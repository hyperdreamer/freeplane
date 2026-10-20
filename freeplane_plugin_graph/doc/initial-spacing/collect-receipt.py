#!/usr/bin/env python3
import hashlib
import json
import pathlib
import re
import shutil
import subprocess

repo = pathlib.Path.cwd().resolve()
assert (repo / '.git').exists()
assert (repo / 'freeplane_plugin_graph').is_dir()
output = repo / 'freeplane_plugin_graph/doc/initial-spacing'
output.mkdir(parents=True, exist_ok=True)
latest = pathlib.Path((repo / 'freeplane_plugin_graph/build/initial-spacing-current.txt').read_text().strip()).resolve()
assert latest.parent == (repo / 'freeplane_plugin_graph/build').resolve()
manifest = json.loads((latest / 'manifest.json').read_text())
assert manifest['status'] == 'MODEL_GATES_PASSED; UI_NOT_MEASURED_DELIVERY_GATE_OPEN'
assert (latest / 'acceptance.md').is_file() and (latest / 'comparison.json').is_file()
runs = output / 'runs'
runs.mkdir(exist_ok=True)
for attempt in sorted((repo / 'freeplane_plugin_graph/build').glob('initial-spacing-*')):
    if attempt.is_dir():
        shutil.copytree(str(attempt), str(runs / attempt.name), dirs_exist_ok=True)
head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=str(repo), text=True).strip()
replays = set(re.findall(r'/tmp/initial-spacing-replay-[A-Za-z0-9_-]+', (output / 'validation.log').read_text(errors='replace')))
assert len(replays) == 1, 'expected one exact spec K replay directory in validation.log'
replay = pathlib.Path(next(iter(replays)))
assert (replay / 'commands.json').is_file()
shutil.copytree(str(replay), str(output / 'replay'), dirs_exist_ok=True)
results = repo / 'freeplane_plugin_graph/build/test-results'
assert list(results.rglob('*.xml')), 'missing Gradle XML test receipts'
shutil.copytree(str(results), str(output / 'test-results'), dirs_exist_ok=True)
assert manifest['head'] == head, 'latest model receipt was measured at a different implementation HEAD'
planning_head = '8c36da1b7a0f0794c75bccbe29dabec43c770a3f'
allowed_outside = {'docs/superpowers/plans/2026-10-20-graph-initial-spacing.md',
    # Plan control-plane artifacts derived by the planning step (6b5fd17117) before this lane's first
    # implementation commit (ef4cbb907a): plan deliverables, not implementation edits.
    'docs/superpowers/plans/2026-10-20-graph-initial-spacing.execution-graph.yaml',
    'docs/superpowers/plans/2026-10-20-graph-initial-spacing.execution-graph.dot'}
for args in [['git', 'diff', '--name-only', planning_head, 'HEAD', '--', '.', ':!freeplane_plugin_graph/**'],
             ['git', 'diff', '--name-only', 'HEAD', '--', '.', ':!freeplane_plugin_graph/**']]:
    outside = subprocess.check_output(args, cwd=str(repo), text=True).splitlines()
    assert set(outside) <= allowed_outside, 'out-of-scope tracked edits: ' + repr(outside)
changed_main = subprocess.check_output(['git', 'diff', '--name-only', manifest['base'], 'HEAD', '--',
    'freeplane_plugin_graph/src/main/java'], cwd=str(repo), text=True).splitlines()
assert set(changed_main) == {
    'freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java',
    'freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/ClusterPacking.java'}
files = sorted(list((repo / 'freeplane_plugin_graph/src/main').rglob('*')) +
               list((repo / 'freeplane_plugin_graph/src/test').rglob('*')))
hashes = {p.relative_to(repo).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in files if p.is_file()}
(output / 'implementation.sha256').write_text(''.join(v + '  ' + k + '\n' for k, v in hashes.items()))
artifacts = {}
for base in [repo / 'freeplane_plugin_graph/build/libs', repo / 'BIN']:
    if base.is_dir():
        for p in sorted(base.rglob('*')):
            if p.is_file() and (p.suffix.lower() in {'.jar', '.zip'} or p.name.startswith('freeplane.')):
                artifacts[p.relative_to(repo).as_posix()] = hashlib.sha256(p.read_bytes()).hexdigest()
assert artifacts, 'no built artifacts found for same-build capture provenance'
receipt = dict(implementation_head=head, delivery_base=manifest['base'], latest_run=latest.name,
    latest_run_manifest_sha256=hashlib.sha256((latest / 'manifest.json').read_bytes()).hexdigest(),
    source_sha256=hashes, built_artifact_sha256=artifacts, fixture_and_dependency_manifest='runs/' + latest.name + '/manifest.json',
    validation_log='validation.log', application_capture_status='NOT_MEASURED',
    delivery_gate='OPEN: three paired same-build real-application captures remain required outside the SDD lane')
(output / 'collection.json').write_text(json.dumps(receipt, indent=2) + '\n')
print(json.dumps(receipt, indent=2))
