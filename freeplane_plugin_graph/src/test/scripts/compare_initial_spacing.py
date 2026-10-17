import csv
import hashlib
import json
import math
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile

BASE = '0734fb23db6873f4ec64c3e3e29fc8c3e17edc7a'
FIXTURES = ('NR8', 'SM8', 'XM4x2')
root, probe, out = (Path(arg).resolve() for arg in sys.argv[1:])
if out.exists():
    raise SystemExit('Use a fresh output directory; preserve earlier receipts')
out.mkdir(parents=True)
env = dict(os.environ)
env['JAVA_TOOL_OPTIONS'] = '-Djava.awt.headless=true'
java_home = Path(env['JAVA_HOME'])
java, javac = str(java_home / 'bin/java'), str(java_home / 'bin/javac')
errors = []

def digest(data):
    return hashlib.sha256(data).hexdigest()

def command(args, cwd, logfile, timeout=None):
    with logfile.open('w') as log:
        log.write(json.dumps({'argv': list(map(str, args)), 'cwd': str(cwd)}) + '\n')
        log.flush()
        result = subprocess.run(list(map(str, args)), cwd=cwd, env=env,
                                stdout=log, stderr=subprocess.STDOUT, timeout=timeout)
        log.write('\nexit=' + str(result.returncode) + '\n')
    return result.returncode

def git(*args):
    return subprocess.check_output(['git', '-C', str(root), *args])

head = git('rev-parse', 'HEAD').decode().strip()
patch = git('diff', '--binary', 'HEAD')
(out / 'candidate.patch').write_bytes(patch)
(out / 'candidate-status.txt').write_bytes(git('status', '--short'))
source_hashes = {str(p.relative_to(root)): digest(p.read_bytes())
                 for p in sorted(root.glob('*/src/main/**/*')) if p.is_file()}
provenance = {'baseline': BASE, 'candidateHead': head, 'candidatePatchSha256': digest(patch),
              'observerSha256': digest(probe.read_bytes()), 'candidateMainSources': source_hashes,
              'JAVA_HOME': str(java_home), 'JAVA_TOOL_OPTIONS': env['JAVA_TOOL_OPTIONS']}
(out / 'provenance.json').write_text(json.dumps(provenance, indent=2) + '\n')
shutil.copyfile(probe, out / 'InitialSpacingProbe.java')
command([java, '-version'], root, out / 'java-version.log')
command(['gradle', '--version'], root, out / 'gradle-version.log')
baseline = out / 'baseline-source'
baseline.mkdir()
with (out / 'baseline.tar').open('wb') as archive:
    subprocess.run(['git', '-C', str(root), 'archive', '--format=tar', BASE], stdout=archive, check=True)
with tarfile.open(out / 'baseline.tar') as archive:
    archive.extractall(baseline, filter='data')
init = out / 'classpath.init.gradle'
init.write_text('''gradle.projectsEvaluated {
    def p = gradle.rootProject.project(':freeplane_plugin_graph')
    p.tasks.register('writeInitialSpacingClasspath') {
        dependsOn p.tasks.named('classes')
        doLast {
            new File(System.getProperty('spacing.classpath')).text = p.sourceSets.main.runtimeClasspath.asPath
        }
    }
}
''')
for version, tree in (('baseline', baseline), ('candidate', root)):
    run = out / version
    run.mkdir()
    cpfile = run / 'classpath.txt'
    rc = command(['gradle', '--no-daemon', '--console=plain', '-I', init,
                  '-Dspacing.classpath=' + str(cpfile),
                  ':freeplane_plugin_graph:writeInitialSpacingClasspath'], tree, run / 'build.log')
    if rc or not cpfile.exists():
        errors.append(version + ': classpath/build failure')
        continue
    cp = cpfile.read_text()
    jars = {str(Path(p).resolve()): digest(Path(p).read_bytes()) for p in cp.split(os.pathsep)
            if Path(p).is_file() and p.endswith('.jar')}
    (run / 'classpath-jars.json').write_text(json.dumps(jars, indent=2) + '\n')
    classes = run / 'observer-classes'
    classes.mkdir()
    rc = command([javac, '--release', '8', '-encoding', 'UTF-8', '-cp', cp, '-d', classes,
                  out / 'InitialSpacingProbe.java'], tree, run / 'observer-compile.log')
    if rc:
        errors.append(version + ': observer compilation failed')
        continue
    for fixture in FIXTURES:
        target = run / fixture
        target.mkdir()
        try:
            rc = command([java, '-Djava.awt.headless=true', '-Duser.language=en', '-Duser.country=US',
                          '-Duser.timezone=UTC', '-cp', str(classes) + os.pathsep + cp,
                          'org.freeplane.plugin.graph.layout.InitialSpacingProbe', fixture, target],
                         tree, target / 'process.log', timeout=65)
            if rc:
                errors.append(version + '/' + fixture + ': probe exited ' + str(rc))
        except subprocess.TimeoutExpired:
            errors.append(version + '/' + fixture + ': external watchdog expired')

def records(path):
    with path.open(newline='') as f:
        return list(csv.DictReader(f, delimiter='\t'))

def require(condition, message):
    if not condition:
        raise ValueError(message)

def number(value):
    result = float(value)
    require(math.isfinite(result), 'non-finite number')
    return result

def gaps(nodes):
    require(len({(n['map'], n['node']) for n in nodes}) == len(nodes), 'duplicate node')
    groups = {}
    for n in nodes:
        require(number(n['radius']) == 8.0, 'changed rendered radius')
        groups.setdefault(n['map'] + '/' + n['enclosure'], []).append(n)
    result = {}
    for key, population in groups.items():
        require(len(population) >= 2, 'nonqualifying population in frozen fixture')
        nearest = []
        for i, n in enumerate(population):
            distances = []
            for j, m in enumerate(population):
                if i == j:
                    continue
                dx, dy = number(n['x']) - number(m['x']), number(n['y']) - number(m['y'])
                require(math.isfinite(dx) and math.isfinite(dy), 'unrepresentable coordinate difference')
                value = math.hypot(dx, dy) - number(n['radius']) - number(m['radius'])
                require(math.isfinite(value), 'unrepresentable gap')
                distances.append(value)
            nearest.append((n['node'], min(distances)))
        values = sorted(value for _, value in nearest)
        mid = len(values) // 2
        median = values[mid] if len(values) % 2 else 0.5 * values[mid - 1] + 0.5 * values[mid]
        result[key] = {'minimum': values[0], 'median': median, 'perNode': nearest}
    return result

def safety(frame):
    for flag in ('failed', 'verificationExhausted', 'indexExhausted', 'detectionTruncated'):
        require(frame[flag] == 'false', 'bad safety flag: ' + flag)
    for flag in ('verified', 'boundaryVerified', 'boundaryCovered', 'endpointSafe'):
        require(frame[flag] == 'true', 'missing verification: ' + flag)
    for count in ('nodeResidual', 'hullResidual', 'edgeResidual', 'actualBoundary', 'actualCollision',
                  'chordCrossings', 'edgeOverNodes', 'paintedCrossings'):
        require(int(frame[count]) == 0, 'nonzero safety count: ' + count)
    for field in ('rms', 'max', 'correctionRms', 'correctionMax', 'correctionDeltaRms', 'correctionDeltaMax'):
        require(number(frame[field]) >= 0.0, 'negative motion measurement')

def expectations(run, fixture):
    with (run / 'inputs.tsv').open(newline='') as f:
        manifest = list(csv.reader(f, delimiter='\t'))
    declared_nodes = [r for r in manifest if r[0] == 'node']
    declared_hulls = [r for r in manifest if r[0] == 'enclosure']
    declared_edges = [r for r in manifest if r[0] == 'edge']
    maps = ['00000000-0000-0000-0000-000000000001']
    if fixture == 'XM4x2':
        maps.append('00000000-0000-0000-0000-000000000002')
    count = 4 if fixture == 'XM4x2' else 8
    frozen_nodes = [(m, prefix + str(i)) for m, prefix in zip(maps, 'AB') for i in range(count)]
    frozen_hulls = [(m, prefix + '_root') for m, prefix in zip(maps, 'AB')]
    require([int(r[1]) for r in declared_nodes] == list(range(8)), 'manifest node order changed')
    require([(r[10], r[11]) for r in declared_nodes] == frozen_nodes, 'manifest node identity changed')
    require([int(r[1]) for r in declared_hulls] == list(range(len(maps))), 'manifest enclosure order changed')
    require([(r[9], r[10]) for r in declared_hulls] == frozen_hulls, 'manifest enclosure identity changed')
    key_to_node = {r[2]: (r[10], r[11]) for r in declared_nodes}
    require(len(key_to_node) == 8, 'duplicate manifest node key')
    require(len({r[2] for r in declared_hulls}) == len(maps), 'duplicate manifest enclosure key')
    expected_nodes = set()
    for index, (map_id, label) in enumerate(frozen_hulls):
        direct = [r for r in manifest if r[0] == 'direct' and int(r[1]) == index]
        require([int(r[2]) for r in direct] == list(range(count)), 'manifest direct-node order changed')
        members = [key_to_node[r[3]] for r in direct]
        require(members == [n for n in frozen_nodes if n[0] == map_id], 'manifest membership changed')
        expected_nodes.update((m, n, label) for m, n in members)
    require(sum(r[0] == 'direct' for r in manifest) == 8, 'extra/missing manifest memberships')
    endpoints = [(key_to_node[r[4]], key_to_node[r[5]]) for r in declared_edges]
    frozen_edges = []
    if fixture == 'SM8':
        frozen_edges = [((maps[0], 'A' + str(i)), (maps[0], 'A' + str((i + 1) % 8))) for i in range(8)]
    elif fixture == 'XM4x2':
        frozen_edges = [((maps[0], 'A2'), (maps[1], 'B0'))]
    require([int(r[1]) for r in declared_edges] == list(range(len(frozen_edges))), 'manifest edge order changed')
    require(endpoints == frozen_edges, 'manifest relationship endpoints changed')
    expected_edges = {r[2]: ends for r, ends in zip(declared_edges, endpoints)}
    require(len(expected_edges) == len(declared_edges), 'duplicate manifest edge key')
    return expected_nodes, set(frozen_hulls), expected_edges

def read_run(version, fixture):
    run = out / version / fixture
    frames, nodes = records(run / 'frames.tsv'), records(run / 'nodes.tsv')
    anchors, edges = records(run / 'anchors.tsv'), records(run / 'edges.tsv')
    expected_nodes, expected_anchors, expected_edges = expectations(run, fixture)

    def frame_data(frame):
        def sample(rows):
            return [r for r in rows if r['phase'] == frame['phase'] and r['ordinal'] == frame['ordinal']]
        sampled, hulls, relationships = sample(nodes), sample(anchors), sample(edges)
        require(len(sampled) == len(expected_nodes)
                and {(n['map'], n['node'], n['enclosure']) for n in sampled} == expected_nodes,
                'exact node/map/enclosure coverage mismatch')
        require(len(hulls) == len(expected_anchors)
                and {(a['map'], a['enclosure']) for a in hulls} == expected_anchors,
                'exact anchor coverage mismatch')
        require(len(relationships) == len(expected_edges)
                and {e['edge'] for e in relationships} == set(expected_edges), 'exact edge coverage mismatch')
        for a in hulls:
            number(a['x']); number(a['y'])
        measured = gaps(sampled)
        by_node = {(n['map'], n['node']): n for n in sampled}
        for n in sampled:
            group = measured[n['map'] + '/' + n['enclosure']]
            expected = dict(group['perNode'])[n['node']]
            for field, value in (('nearestGap', expected), ('enclosureMinimum', group['minimum']),
                                 ('enclosureMedian', group['median'])):
                require(abs(number(n[field]) - value) <= 1e-9, 'observer statistic mismatch: ' + field)
        for e in relationships:
            a, b = (by_node[k] for k in expected_edges[e['edge']])
            distance = math.hypot(number(a['x']) - number(b['x']), number(a['y']) - number(b['y']))
            require(math.isfinite(distance), 'invalid relationship distance')
            require(abs(number(e['centreDistance']) - distance) <= 1e-9, 'relationship centre distance mismatch')
            require(abs(number(e['discGap']) - (distance - number(a['radius']) - number(b['radius']))) <= 1e-9,
                    'relationship disc gap mismatch')
        return measured

    first = [f for f in frames if f['phase'] == 'first-idle']
    require(len(first) == 1, 'expected exactly one first-idle row')
    first = first[0]
    pre = [f for f in frames if f['phase'] == 'pre-idle']
    require(all(f['idle'] == 'false' for f in pre), 'earlier idle skipped')
    require([int(f['steps']) for f in pre] == list(range(int(first['steps']))), 'missing pre-idle calls')
    require(all(int(f['ordinal']) == int(f['steps']) for f in pre + [first]), 'wrong pre/first ordinal')
    require(frames[:len(pre)] == pre and frames[len(pre)] == first, 'wrong first-idle ordering')
    require(first['idle'] == 'true' and int(first['stable']) >= 8, 'invalid first idle')
    require(number(first['rms']) <= 0.05 and number(first['max']) <= 0.10, 'invalid idle motion')
    require(0 <= int(first['steps']) <= 2000 and 0 <= int(first['validationElapsedNanos']) <= 30_000_000_000,
            'first-idle validation budget failed')
    safety(first)
    for f in pre:
        frame_data(f)
    measurements = frame_data(first)
    result = {'firstIdle': first, 'enclosures': measurements, 'postIdle': [], 'diagnosticErrors': []}
    post = [f for f in frames if f['phase'] == 'post-idle']
    try:
        require([int(f['ordinal']) for f in post] == list(range(1, 101)), 'incomplete post-idle window')
        require(len(frames) == len(pre) + 101, 'unexpected frame rows')
        frame_keys = {(f['phase'], f['ordinal']) for f in frames}
        require(len(frame_keys) == len(frames), 'duplicate frame records')
        for dataset in (nodes, anchors, edges):
            require(all((r['phase'], r['ordinal']) in frame_keys for r in dataset), 'orphan evidence row')
        for f in post:
            offset = int(f['ordinal'])
            require(int(f['steps']) == int(first['steps']) + offset, 'diagnostic call mismatch')
            require(0 <= int(f['validationElapsedNanos']) <= 30_000_000_000, 'diagnostic validation deadline')
            safety(f)
            result['postIdle'].append({'frame': f, 'enclosures': frame_data(f)})
    except (ValueError, KeyError, IndexError) as failure:
        result['diagnosticErrors'].append(str(failure))
        errors.append(version + '/' + fixture + ': post-idle diagnostic: ' + str(failure))
    return result

summary = {'baseline': BASE, 'candidateHead': head, 'fixtures': {}, 'errors': errors}
for fixture in FIXTURES:
    try:
        old_inputs = (out / 'baseline' / fixture / 'inputs.tsv').read_bytes()
        new_inputs = (out / 'candidate' / fixture / 'inputs.tsv').read_bytes()
        require(old_inputs == new_inputs, 'baseline/candidate inputs differ')
        old, new = read_run('baseline', fixture), read_run('candidate', fixture)
        require(old['enclosures'].keys() == new['enclosures'].keys(), 'enclosure population differs')
        comparisons = {}
        for key in old['enclosures']:
            delta = new['enclosures'][key]['median'] - old['enclosures'][key]['median']
            comparisons[key] = {'delta': delta, 'pass': delta >= 6.0 - 1e-6}
            if delta < 6.0 - 1e-6:
                errors.append(fixture + '/' + key + ': first-idle improvement below 6 units')
        summary['fixtures'][fixture] = {'inputsSha256': digest(old_inputs), 'baseline': old, 'candidate': new,
                                        'firstIdleComparisons': comparisons}
    except (OSError, ValueError, KeyError, IndexError) as failure:
        errors.append(fixture + ': ' + str(failure))
require(source_hashes == {str(p.relative_to(root)): digest(p.read_bytes())
                          for p in sorted(root.glob('*/src/main/**/*')) if p.is_file()},
        'candidate main sources changed during measurement')
summary['status'] = 'PASS' if not errors else 'FAIL'
(out / 'comparison.json').write_text(json.dumps(summary, indent=2, allow_nan=False) + '\n')
print(json.dumps({'status': summary['status'], 'errors': errors, 'evidence': str(out)}, indent=2))
raise SystemExit(0 if not errors else 1)
