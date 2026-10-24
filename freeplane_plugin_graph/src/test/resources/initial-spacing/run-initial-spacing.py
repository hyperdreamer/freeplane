import argparse
import hashlib
import json
import math
import os
import pathlib
import shutil
import statistics
import subprocess
import tempfile
import time

BASE = 'cb8782fb3742f378b2907198fd2343292415da95'
DELIVERY_BASE = '448c0d679d2b1d46563eaf079352e1eefb254305'
# ruling-A-amendment-1.md condition 4: the C0 cross baseline records these figures, never rejects them.
C0_CROSS_GATED_FRAMES = 858
C0_CROSS_NODE_RESIDUAL_MISMATCHES = 23
C0_NODE_RESIDUAL_AUTHORISATION = 'ruling-A-amendment-1.md condition 4'
JAVA = '/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin/java'
PREFIX = 'freeplane_plugin_graph/src/main/java/'
ENGINE = PREFIX + 'org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java'
PACKING = PREFIX + 'org/freeplane/plugin/graph/layout/graphstream/ClusterPacking.java'
PAINTER = PREFIX + 'org/freeplane/plugin/graph/canvas/GraphPainter.java'
FROZEN = ['freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/ReferenceRepulsionFixture.java',
          'freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java',
          'freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/ClusterProximityShould.java']
FIXTURES = ['S1', 'reference', 'cross', 'mixed', 'two', 'endpoints-cross-ne', 'endpoints-cross-ee'] + [
    'matrix-' + scope + '-' + pair for scope in ('same', 'cross') for pair in ('nn', 'ne', 'ee')]


def digest(data):
    return hashlib.sha256(data).hexdigest()


def control_pairs(row):
    return dict(zip(row[3::2], row[4::2]))


def run_acceptance(repo, candidate_cp, output):
    output.mkdir(parents=True, exist_ok=True)
    env = dict(os.environ, JAVA_TOOL_OPTIONS='-Djava.awt.headless=true')
    manifest = dict(base=BASE, head=subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=repo, text=True).strip(),
                    environment={k: env.get(k) for k in ('JAVA_HOME', 'JAVA_TOOL_OPTIONS', 'LANG', 'LC_ALL')},
                    commands=[], source_sha256={}, classpath=candidate_cp, jar_sha256={})
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')

    def command(label, argv, timeout):
        start = time.monotonic()
        receipt = dict(label=label, argv=argv, utc=time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()))
        manifest['commands'].append(receipt)
        try:
            with (output / (label + '.log')).open('w') as log:
                result = subprocess.run(argv, cwd=repo, env=env, stdout=log, stderr=subprocess.STDOUT, timeout=timeout)
            receipt.update(returncode=result.returncode, elapsed_s=time.monotonic() - start)
            assert result.returncode == 0, (label, output)
        except BaseException as error:
            receipt.update(error=repr(error), elapsed_s=time.monotonic() - start)
            raise
        finally:
            (output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')

    command('java-version', [JAVA, '-version'], 60)
    command('gradle-version', ['gradle', '--version'], 60)
    for item in candidate_cp.split(os.pathsep):
        path = pathlib.Path(item)
        if path.suffix == '.jar' and path.is_file(): manifest['jar_sha256'][str(path)] = digest(path.read_bytes())
    sources = subprocess.check_output(['git', 'ls-tree', '-r', '--name-only', BASE, '--', PREFIX], cwd=repo, text=True).splitlines()
    baseline_files = []
    expected_overlay = {}
    changed_sources = set()
    for relative in sources + FROZEN:
        if not relative.endswith('.java'): continue
        data = subprocess.check_output(['git', 'show', BASE + ':' + relative], cwd=repo)
        manifest['source_sha256'][relative] = dict(C0=digest(data))
        if relative.startswith(PREFIX):
            current = (repo / relative).read_bytes()
            manifest['source_sha256'][relative]['candidate'] = digest(current)
            expected = data.decode('utf-8')
            if relative == ENGINE:
                old = 'static final double NODE_RADIUS = 8.0;'
                assert expected.count(old) == 1
                expected = expected.replace(old, old + '\n    static final double PREFERRED_NODE_GAP = 24.0;'
                    + '\n    static final double PREFERRED_EDGE_GAP = 20.0;')
                old = '(2.0 * maxRadius + NodeSeparationProjection.MIN_GAP)'
                assert expected.count(old) == 1
                expected = expected.replace(old, '(2.0 * maxRadius + PREFERRED_NODE_GAP)')
                old = '''                    radiusOf(first, radiusById) + radiusOf(second, radiusById)
                        + NodeSeparationProjection.MIN_GAP));'''
                assert expected.count(old) == 1
                expected = expected.replace(old, '''                    radiusOf(first, radiusById) + radiusOf(second, radiusById)
                        + (edge.first().isNode() && edge.second().isNode()
                            ? PREFERRED_EDGE_GAP : NodeSeparationProjection.MIN_GAP)));''')
            if relative == PACKING:
                old = 'clearanceX < gap && clearanceY < gap'
                assert expected.count(old) == 1
                expected = expected.replace(old, 'clearanceX < gap - CANDIDATE_EPSILON && clearanceY < gap - CANDIDATE_EPSILON')
            if relative == PAINTER:
                replacements = [
                    ('''    // World units. 12.5 / 14.0 ~ 0.8929 of the maximum node radius:
    // GraphGeometryEngine.BASE_RADIUS 8.0 x NodeProminence.MAX_SCALE 1.75 = 14.0 world.
    private static final double ARROW_HEAD_WORLD_LENGTH = 12.5;
    // Head base is 0.84 x length; the aspect is preserved when the cap shrinks a head.
    private static final double ARROW_HEAD_HALF_BASE_RATIO = 0.42;''',
                     '''    // World units.
    private static final double ARROW_HEAD_RATIO = 0.30;
    private static final double MIN_ARROW_HEAD_WORLD_LENGTH = 4.0;
    private static final double MAX_ARROW_HEAD_WORLD_LENGTH = 12.5;
    private static final double MIN_ARROW_HEAD_AVAILABILITY = 8.0;
    // Head base is 0.84 x length; the aspect is preserved when the cap shrinks a head.
    private static final double ARROW_HEAD_HALF_BASE_RATIO = 0.42;
    private static final double MIN_STROKE_WORLD = 1e-6;'''),
                    ('''    // Screen px.
    static double arrowHeadLengthPx(final double zoom) {
        return ARROW_HEAD_WORLD_LENGTH * zoom;
    }

    // World units in, world units out.
    static double availableArrowHeadWorldLength(final double attachmentDistanceWorld,
            final boolean arrowAtFirst, final boolean arrowAtSecond) {
        return arrowAtFirst && arrowAtSecond ? attachmentDistanceWorld / 2.0 : attachmentDistanceWorld;
    }

    // Screen px.
    static double effectiveArrowHeadLengthPx(final double zoom, final double availableWorldLength) {
        return Math.min(arrowHeadLengthPx(zoom), availableWorldLength * zoom);
    }

    // Screen px.
    static double arrowHeadHalfBasePx(final double lengthPx) {
        return ARROW_HEAD_HALF_BASE_RATIO * lengthPx;
    }''',
                     '''    // World units in, world units out.
    static double availableArrowHeadWorldLength(final double attachmentDistanceWorld,
            final boolean arrowAtFirst, final boolean arrowAtSecond) {
        return arrowAtFirst && arrowAtSecond ? attachmentDistanceWorld / 2.0 : attachmentDistanceWorld;
    }

    // Screen px.
    static double effectiveArrowHeadLengthPx(final double zoom, final double availableWorldLength) {
        return Math.max(MIN_ARROW_HEAD_WORLD_LENGTH,
            Math.min(MAX_ARROW_HEAD_WORLD_LENGTH, ARROW_HEAD_RATIO * availableWorldLength)) * zoom;
    }

    // Screen px.
    static double arrowHeadHalfBasePx(final double lengthPx) {
        return ARROW_HEAD_HALF_BASE_RATIO * lengthPx;
    }'''),
                    ('''        final double availableWorldLength =
            availableArrowHeadWorldLength(terminalSegmentLength, arrowAtFirst, arrowAtSecond);
        final double arrowLengthPx = effectiveArrowHeadLengthPx(zoom, availableWorldLength);''',
                     '''        final double availableWorldLength =
            availableArrowHeadWorldLength(terminalSegmentLength, arrowAtFirst, arrowAtSecond);
        if (availableWorldLength < MIN_ARROW_HEAD_AVAILABILITY) {
            return;
        }
        final double arrowLengthPx = effectiveArrowHeadLengthPx(zoom, availableWorldLength);'''),
                    ('        final BasicStroke leaderStroke = leaderStroke(theme, viewport.zoom());',
                     '        final BasicStroke leaderStroke = leaderStroke(viewport.zoom());'),
                    ('''            graphics.setColor(theme.labelColor());
            final double anchorX = worldX(label.anchorX(), viewport, size);
            final double anchorY = worldY(label.anchorY(), viewport, size);
            if (label.leader().isPresent()) {
                final LeaderLine line = label.leader().get();
                graphics.setStroke(leaderStroke);
                graphics.draw(new Line2D.Double(
                    worldX(line.start().x(), viewport, size), worldY(line.start().y(), viewport, size),
                    worldX(line.end().x(), viewport, size), worldY(line.end().y(), viewport, size)));
            }
            drawCentered(graphics, font, label.text(), anchorX, anchorY);''',
                     '''            graphics.setColor(theme.labelColor());
            final double anchorX = worldX(label.anchorX(), viewport, size);
            final double anchorY = worldY(label.anchorY(), viewport, size);
            if (label.leader().isPresent()) {
                final LeaderLine line = label.leader().get();
                graphics.setColor(theme.mutedColor());
                graphics.setStroke(leaderStroke);
                graphics.draw(new Line2D.Double(
                    worldX(line.start().x(), viewport, size), worldY(line.start().y(), viewport, size),
                    worldX(line.end().x(), viewport, size), worldY(line.end().y(), viewport, size)));
            }
            graphics.setColor(theme.labelColor());
            drawCentered(graphics, font, label.text(), anchorX, anchorY);'''),
                    ('''    static BasicStroke leaderStroke(final GraphTheme theme, final double zoom) {
        return new BasicStroke((float) (theme.edgeStroke().getLineWidth() / zoom),
            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    }''',
                     '''    static BasicStroke leaderStroke(final double zoom) {
        final float width = (float) Math.max(1.0 / zoom, MIN_STROKE_WORLD);
        final float dashOn = (float) Math.max(1.2 / zoom, MIN_STROKE_WORLD);
        final float dashOff = (float) Math.max(2.8 / zoom, MIN_STROKE_WORLD);
        return new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10.0f,
            new float[] { dashOn, dashOff }, 0.0f);
    }''')]
                for old, new in replacements:
                    assert expected.count(old) == 1
                    expected = expected.replace(old, new)
            assert current == expected.encode('utf-8'), 'Unexpected production difference: ' + relative
            if current != data:
                changed_sources.add(relative)
            expected_overlay[relative] = digest(current)
        target = output / 'C0-src' / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        baseline_files.append(str(target))
    current_sources = {str(p.relative_to(repo)) for p in (repo / PREFIX).rglob('*.java')}
    assert current_sources == {x for x in sources if x.endswith('.java')}, 'Production source added or removed'
    assert changed_sources == {ENGINE, PACKING, PAINTER}, ('Unexpected cumulative C0 overlay set', changed_sources)
    delivery_changes = set(subprocess.check_output(
        ['git', 'diff', '--name-only', DELIVERY_BASE, '--', PREFIX], cwd=repo, text=True).splitlines())
    assert delivery_changes == {ENGINE, PAINTER}, ('Unexpected delivery-base production diff', delivery_changes)
    manifest['cumulative_c0_overlays'] = sorted(changed_sources)
    manifest['delivery_base'] = DELIVERY_BASE
    manifest['delivery_base_production_diff'] = sorted(delivery_changes)
    baseline = output / 'C0-classes'
    baseline.mkdir()
    empty = output / 'empty-sourcepath'
    empty.mkdir()
    command('compile-C0', [JAVA + 'c', '--release', '8', '-encoding', 'UTF-8', '-implicit:none', '-sourcepath',
            str(empty), '-classpath', candidate_cp, '-d', str(baseline)] + baseline_files, 600)
    reports = {}
    # Serial JVMs; exactly one fixture/configuration in each JVM; C0 S1 qualifies first.
    for configuration in ('C0', 'candidate'):
        for fixture in FIXTURES:
            cp = str(baseline) + os.pathsep + candidate_cp if configuration == 'C0' else candidate_cp
            label = configuration + '-' + fixture
            command(label, [JAVA, '-ea', '-cp', cp,
                    'org.freeplane.plugin.graph.layout.graphstream.InitialSpacingProbe', configuration, fixture,
                    str(output / ('C0-' + fixture + '.log'))], 1900)
            rows = [line.split('\t') for line in (output / (label + '.log')).read_text().splitlines() if '\t' in line]
            reports[(configuration, fixture)] = rows
            assert [r for r in rows if r[0] == 'CONFIG'][0][3:6] == [
                '6.0' if configuration == 'C0' else '24.0', '6.0', '8.0']
            locations = [r for r in rows if r[0] == 'CODE_SOURCE']
            assert len(locations) == 4
            for r in locations[:3]:
                assert (str(baseline) in r[2]) == (configuration == 'C0'), r
            label_sha = [r for r in rows if r[0] == 'LABEL_SHA256']
            assert len(label_sha) == 1
            if configuration == 'candidate':
                assert label_sha[0][1] == [r for r in reports[('C0', fixture)] if r[0] == 'LABEL_SHA256'][0][1]
                assert [r for r in rows if r[0].startswith('INPUT_')] == [
                    r for r in reports[('C0', fixture)] if r[0].startswith('INPUT_')]
                frozen = {'reference': {'Axioms': (36.0, 0.0, 0.0, False, True, False, False)},
                    'S1': {'Sub': (0.0, 0.0, 0.0, False, False, False, True)},
                    'cross': {'A-sub 0': (0.0, 0.0, 11.0, False, False, True, False),
                        'A-sub 1': (-128.56854249492380, 0.0, 11.0, False, True, True, False),
                        'A-sub 2': (128.56854249492380, 0.0, 0.0, False, True, False, False),
                        'B-sub 0': (-417.70562748477140, 0.0, 0.0, False, True, False, False),
                        'B-sub 1': (-546.27416997969520, 0.0, 0.0, False, True, False, False),
                        'B-sub 2': (-289.13708498984760, 0.0, 11.0, False, True, True, False)}}
                if fixture in frozen:
                    eligibility = [r for r in rows if r[0] == 'ELIGIBILITY']
                    assert len(eligibility) == len(frozen[fixture])
                    for r in eligibility:
                        target = frozen[fixture][r[3]]
                        assert all(abs(float(v)) <= 1e-9 for v in r[4:6])
                        assert all(abs(float(v) - expected) <= 1e-6 for v, expected in zip(r[6:9], target[:3]))
                        assert [v == 'true' for v in r[9:13]] == list(target[3:])
            first = [r for r in rows if r[0] == 'FIRST_IDLE']
            post = [r for r in rows if r[0] == 'POST']
            follow = [r for r in rows if r[0] == 'FOLLOW']
            if fixture.startswith('matrix-'):
                assert not first and not post and not follow
                assert len([r for r in rows if r[0] == 'CONSUMER_ONLY']) == 1
            else:
                assert len(first) == len(post) == 1 and len(follow) == 100
                assert int(first[0][3]) <= 600_000_000_000
                assert int(post[0][6]) <= 600_000_000_000
            if fixture in ('S1', 'reference', 'cross'):
                expected_step = {'S1': (84, 58), 'reference': (42, 37), 'cross': (756, 3455)}[fixture][configuration == 'candidate']
                assert int(first[0][2]) == expected_step
            if configuration == 'C0' and fixture == 'S1':
                assert int(post[0][2]) == 184 and int(post[0][3]) == 100
                assert math.isclose(float(post[0][4]), 0.03903402780948504, abs_tol=1e-6)
                assert math.isclose(float(post[0][5]), 0.06011499052181499, abs_tol=1e-6)
            if fixture == 'S1': assert int(post[0][3]) == 100
            # C0 records pre-existing baseline exhaustion; the candidate must record none.
            summaries = [r for r in rows if r[0] == 'CONTROL_SUMMARY']
            notes = [r for r in rows if r[0] == 'CONTROL_NOTE']
            assert len(summaries) == 1 and len(notes) == 1, label
            summary, note = control_pairs(summaries[0]), control_pairs(notes[0])
            assert (summary['exhausted_control'] == 'true') == (configuration == 'C0'), (label, summary)
            assert (note['exhausted_control'] == 'true') == (configuration == 'C0'), (label, note)
            gated = int(summary['gated_frames'])
            assert gated == (1 if fixture.startswith('matrix-') else 102 + int(first[0][2])), (label, gated)
            if configuration == 'candidate':
                assert int(summary['authorised_verification_budget_exhausted_frames']) == 0, label
                assert note['candidate_authorised_verification_budget_exhausted_frames'] == '0', label
                assert int(summary['forbidden_index_budget_exhausted_frames']) == 0, label
                assert int(summary['forbidden_detection_truncated_frames']) == 0, label
                assert note['forbidden_index_budget_exhausted_frames'] == '0', label
                assert note['forbidden_detection_truncated_frames'] == '0', label
                assert int(summary['node_residual_mismatch_frames']) == 0, label
                assert note['node_residual_mismatch_frames'] == '0', label
                assert int(summary['hull_invariant_frames']) == gated, label
            else:
                header = [r for r in rows if r[0] == 'CONTROL_HEADER'][0][1:]
                controls = [dict(zip(header, r[1:])) for r in rows if r[0] == 'CONTROL']
                assert len(controls) == gated
                assert all(r['exhausted_control'] == 'true' for r in controls)
                assert all(r['verification_cap'] == '64' and r['round_cap'] == '4' for r in controls)
                assert all(int(r['verifications']) <= 64 for r in controls)
                assert int(summary['max_verifications_observed']) <= 64
                assert sum(1 for r in controls if r['verification_budget_exhausted'] == 'true') == int(summary['authorised_verification_budget_exhausted_frames'])
                assert sum(1 for r in controls if r['index_budget_exhausted'] == 'true') == int(summary['forbidden_index_budget_exhausted_frames'])
                assert sum(1 for r in controls if r['detection_truncated'] == 'true') == int(summary['forbidden_detection_truncated_frames'])
                assert int(summary['forbidden_index_budget_exhausted_frames']) == 0, label
                assert int(summary['forbidden_detection_truncated_frames']) == 0, label
                assert sum(1 for r in controls if r['node_residual_diagnostic_matches'] == 'false') == int(summary['node_residual_mismatch_frames'])
                if fixture == 'cross':
                    assert int(summary['authorised_verification_budget_exhausted_frames']) > 0 and int(summary['max_verifications_observed']) == 64
                    assert note['observed_first_idle_step'] == '756' and note['shipped_pre_change_baseline'] == '756'
                    assert note['match'] == 'true', note
                    # ruling-A-amendment-1.md condition 4: recorded per frame, never rejected.
                    assert note['authorisation'].startswith(C0_NODE_RESIDUAL_AUTHORISATION), note
                    assert int(summary['hull_invariant_frames']) == gated
                    assert gated == C0_CROSS_GATED_FRAMES, (label, gated)
                    assert int(summary['node_residual_mismatch_frames']) == C0_CROSS_NODE_RESIDUAL_MISMATCHES, (label, summary)
                    disagreed = [r for r in controls if r['node_residual_diagnostic_matches'] == 'false']
                    assert len(disagreed) == C0_CROSS_NODE_RESIDUAL_MISMATCHES, (label, len(disagreed))
                    assert all(r['frame_node_residual'] == '0' and r['raw_node_residual_pairs'] == '1' for r in disagreed), label
                else:
                    assert int(summary['hull_invariant_frames']) == gated, label
                    # The recorded disagreement is one-directional: the relaxing projection's field under-reports.
                    assert all(int(r['frame_node_residual']) < int(r['raw_node_residual_pairs'])
                        for r in controls if r['node_residual_diagnostic_matches'] == 'false'), label
            # Independent enumeration from printed coordinates catches metric implementation mutations.
            for stage in (('raw', 'seed') if fixture.startswith('matrix-') else ('raw', 'seed', 'idle', 'post100')):
                points = {r[4]: tuple(map(float, r[5:8])) for r in rows if r[0] == 'POINT' and r[2] == stage and r[3] == 'node'}
                members = {}
                for r in rows:
                    if r[0] == 'ELIGIBILITY_NODE': members.setdefault(r[2], []).append(r[3])
                for r in [r for r in rows if r[0] == 'METRIC' and r[2] == stage]:
                    keys = members[r[3]]
                    gaps = [min(math.hypot(points[a][0] - points[b][0], points[a][1] - points[b][1])
                        - points[a][2] - points[b][2] for b in keys if b != a) for a in keys]
                    assert abs(min(gaps) - float(r[5])) <= 1e-6
                    assert abs(statistics.median(gaps) - float(r[6])) <= 1e-6
            # Persist a machine-readable full record set even before the final comparison.
            (output / (label + '.json')).write_text(json.dumps(rows, indent=2) + '\n')
            print('PASS', label, 'first-idle', first[0][2] if first else 'CONSUMER_ONLY', flush=True)
    compare(reports, output)
    write_report(reports, output)
    manifest['epsilon_necessity'] = 'NOT_ESTABLISHED'
    manifest['epsilon_reason'] = 'Predicate ULP sensitivity does not reproduce a larger-item C0 packItems slot failure.'
    manifest['c0_exhaustion_control'] = ('C0 authorised verification-budget exhaustion and node-residual disagreement are recorded; '
        'index-budget exhaustion and truncated detection are forbidden; candidate records zero of all three flags')
    manifest['c0_node_residual_control'] = (C0_NODE_RESIDUAL_AUTHORISATION + ': C0 cross records '
        + str(C0_CROSS_NODE_RESIDUAL_MISMATCHES) + ' of ' + str(C0_CROSS_GATED_FRAMES)
        + ' gated frames where the frame field reports 0 while the raw scan reports 1; the candidate asserts'
        + ' the shared equality on every frame with zero mismatches')
    manifest['status'] = 'MODEL_GATES_PASSED; UI_NOT_MEASURED_DELIVERY_GATE_OPEN'
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')


def compare(reports, output):
    expected = {'reference': {'Axioms': (6.0, 24.0, 24.0)}, 'S1': {'Sub': (6.0, 24.0, 24.0)},
        'cross': {'A-sub 0': (6.0, 17.14733824239645, 20.573669121198225),
                  'A-sub 1': (6.0, 17.14733824239645, 17.14733824239645), 'A-sub 2': (6.0, 24.0, 24.0),
                  'B-sub 0': (6.000000000000009, 24.0, 24.0), 'B-sub 1': (6.000000000000009, 24.0, 24.0),
                  'B-sub 2': (6.000000000000011, 17.14733824239645, 20.573669121198225)},
        'mixed': {'Sub': (7.6, 25.6, 25.6)}, 'two': {'Root': (6.0, 24.0, 24.0)}}
    comparisons = []
    endpoint_rows = []
    for fixture, hulls in expected.items():
        old = {r[3]: r for r in reports[('C0', fixture)] if r[0] == 'METRIC' and r[2] == 'seed'}
        new = {r[3]: r for r in reports[('candidate', fixture)] if r[0] == 'METRIC' and r[2] == 'seed'}
        assert set(old) == set(new) and len(old) == len(hulls)
        assert {r[4] for r in old.values()} == set(hulls)
        # Control drift is checked for all rows before any candidate increase comparison.
        for key, row in old.items(): assert abs(float(row[6]) - hulls[row[4]][0]) <= 1e-6
        for key, row in new.items():
            baseline, minimum, median = hulls[row[4]]
            increase = float(row[6]) - float(old[key][6])
            assert increase >= 6.0 - 1e-6, (fixture, row[4], increase)
            assert abs(float(row[5]) - minimum) <= 1e-6 and abs(float(row[6]) - median) <= 1e-6
            comparisons.append(dict(fixture=fixture, hull=key, label=row[4], C0_median=float(old[key][6]),
                                    candidate_minimum=float(row[5]), candidate_median=float(row[6]), increase=increase))
    for fixture in FIXTURES:
        for stage in ('idle', 'post100'):
            old = [r for r in reports[('C0', fixture)] if r[0] == 'ENDPOINT' and r[2] == stage]
            new = [r for r in reports[('candidate', fixture)] if r[0] == 'ENDPOINT' and r[2] == stage]
            assert len(old) == len(new)
            for a, b in zip(old, new):
                assert a[:9] == b[:9] and a[10] == b[10]
                endpoint_rows.append(dict(fixture=fixture, stage=stage, first=a[3], second=a[4], contributor=a[5],
                    crossMap=a[6] == 'true', first_type=a[7], second_type=a[8], pair=a[10], C0=float(a[9]), candidate=float(b[9])))
    for stage in ('idle', 'post100'):
        assert any(r['stage'] == stage and not r['crossMap'] and r['pair'] == 'node-node' for r in endpoint_rows)
        assert any(r['stage'] == stage and r['crossMap'] and r['pair'] == 'node-node' for r in endpoint_rows)
        for pair in ('node-enclosure', 'enclosure-enclosure'): assert any(r['stage'] == stage and r['pair'] == pair for r in endpoint_rows)
    aggregates = []
    for stage in ('idle', 'post100'):
        for pair in ('node-node', 'node-enclosure', 'enclosure-enclosure'):
            group = [r for r in endpoint_rows if r['stage'] == stage and r['pair'] == pair]
            for config in ('C0', 'candidate'):
                values = [r[config] for r in group]
                assert values and all(math.isfinite(x) for x in values)
                aggregates.append(dict(stage=stage, pair=pair, configuration=config, minimum=min(values), median=statistics.median(values)))
    rests = []
    for config in ('C0', 'candidate'):
        for fixture in FIXTURES:
            if not fixture.startswith('matrix-'): continue
            rows = [r for r in reports[(config, fixture)] if r[0] == 'REST']
            assert len(rows) == 1
            row = rows[0]
            pair = fixture.rsplit('-', 1)[1]
            target = {'nn': (22.0, 36.0), 'ne': (29.556349186104047, 42.28427124746190),
                      'ee': (37.112698372208094, 62.568542494923804)}[pair][config == 'candidate']
            assert abs(float(row[6]) - target) <= 1e-6
            assert row[3] == ('true' if '-cross-' in fixture else 'false')
            rests.append(dict(configuration=config, fixture=fixture, first=row[1], second=row[2], crossMap=row[3],
                              first_radius=float(row[4]), second_radius=float(row[5]), rest=float(row[6])))
    exhaustion = []
    for config in ('C0', 'candidate'):
        for fixture in FIXTURES:
            summary = control_pairs([r for r in reports[(config, fixture)] if r[0] == 'CONTROL_SUMMARY'][0])
            exhaustion.append(dict(configuration=config, fixture=fixture, **{k: summary[k] for k in (
                'exhausted_control', 'gated_frames', 'authorised_verification_budget_exhausted_frames',
                'forbidden_index_budget_exhausted_frames', 'forbidden_detection_truncated_frames',
                'max_verifications_observed', 'max_residual_conflict_keys', 'max_repairs_observed',
                'node_residual_mismatch_frames')}))
    assert all(r['authorised_verification_budget_exhausted_frames'] == '0'
        and r['forbidden_index_budget_exhausted_frames'] == '0'
        and r['forbidden_detection_truncated_frames'] == '0'
        for r in exhaustion if r['configuration'] == 'candidate')
    cross = [r for r in exhaustion if r['configuration'] == 'C0' and r['fixture'] == 'cross'][0]
    assert int(cross['gated_frames']) == C0_CROSS_GATED_FRAMES, cross
    assert int(cross['node_residual_mismatch_frames']) == C0_CROSS_NODE_RESIDUAL_MISMATCHES, cross
    node_residual_control = dict(authorisation=C0_NODE_RESIDUAL_AUTHORISATION, fixture='cross',
        gated_frames=int(cross['gated_frames']), mismatched_frames=int(cross['node_residual_mismatch_frames']),
        frame_field=0, raw_scan=1)
    result = dict(seed=comparisons, endpoints=endpoint_rows, endpoint_aggregates=aggregates, rest_matrix=rests,
        exhaustion_control=exhaustion, node_residual_control=node_residual_control,
        note='Endpoint gaps are descriptive. Additive MIN_GAP remains 6.0; total rests involving enclosures change.')
    (output / 'comparison.json').write_text(json.dumps(result, indent=2, allow_nan=False) + '\n')


def write_report(reports, output):
    result = json.loads((output / 'comparison.json').read_text())
    control4 = result['node_residual_control']
    lines = ['# Initial-spacing acceptance receipts', '',
        'EPSILON_NECESSITY = NOT_ESTABLISHED: predicate ULP sensitivity does not reproduce a larger-item C0 packItems slot failure.', '',
        'UI evidence: NOT_MEASURED; mandatory same-build application capture gate remains open.', '',
        'Seed median increases apply to every qualifying primary enclosure. Settled and realized endpoint gaps are descriptive.', '',
        '| Fixture | Hull | C0 median | Candidate min | Candidate median | Increase |',
        '| --- | --- | ---: | ---: | ---: | ---: |']
    for r in result['seed']:
        lines.append('| {fixture} | {label} | {C0_median:.17g} | {candidate_minimum:.17g} | {candidate_median:.17g} | {increase:.17g} |'.format(**r))
    lines += ['', '| Config / fixture | First-idle calls | First-idle ns | Post-100 idle count | Max RMS | Max displacement | Follow-up ns |',
              '| --- | ---: | ---: | ---: | ---: | ---: | ---: |']
    for (config, fixture), rows in reports.items():
        first = next((r for r in rows if r[0] == 'FIRST_IDLE'), None)
        post = next((r for r in rows if r[0] == 'POST'), None)
        if first is None:
            lines.append('| ' + config + '/' + fixture + ' | NOT_APPLICABLE | NOT_APPLICABLE | NOT_APPLICABLE | NOT_APPLICABLE | NOT_APPLICABLE | NOT_APPLICABLE |')
        else:
            lines.append('| ' + config + '/' + fixture + ' | ' + ' | '.join(first[2:4] + post[3:7]) + ' |')
    lines += ['', '| Config / fixture | Stage | Hull | Minimum | Median | Median at collision floor | Native endpoints | Relationship endpoints |',
              '| --- | --- | --- | ---: | ---: | --- | ---: | ---: |']
    for (config, fixture), rows in reports.items():
        for r in rows:
            if r[0] == 'METRIC' and r[2] in ('idle', 'post100'):
                lines.append('| ' + config + '/' + fixture + ' | ' + ' | '.join([r[2], r[4], r[5], r[6], r[8], r[9], r[10]]) + ' |')
    lines += ['', result['note'], '',
        '| Fixture | Stage | First endpoint | Second endpoint | Contributor | Cross map | Pair | C0 realized gap | Candidate realized gap |',
        '| --- | --- | --- | --- | --- | --- | --- | ---: | ---: |']
    for r in result['endpoints']:
        lines.append('| ' + ' | '.join(str(r[k]).replace('|', '&#124;') for k in (
            'fixture', 'stage', 'first', 'second', 'contributor', 'crossMap', 'pair', 'C0', 'candidate')) + ' |')
    lines += ['', '| Stage | Pair | Config | Minimum | Median |', '| --- | --- | --- | ---: | ---: |']
    for r in result['endpoint_aggregates']:
        lines.append('| ' + ' | '.join(str(r[k]) for k in ('stage', 'pair', 'configuration', 'minimum', 'median')) + ' |')
    lines += ['', '| Config | Fixture | First radius | Second radius | Observed rest |',
        '| --- | --- | ---: | ---: | ---: |']
    for r in result['rest_matrix']:
        lines.append('| ' + ' | '.join(str(r[k]) for k in ('configuration', 'fixture', 'first_radius', 'second_radius', 'rest')) + ' |')
    lines += ['', '## Baseline exhaustion control', '',
        'C0 is the unmodified pre-change engine from delivery base ' + BASE + '. Its verification-budget exhaustion is',
        'recorded as exhausted_control: true and never rejected: it is pre-existing behaviour of the unmodified engine,',
        'evidenced by the C0 cross-map first-idle step of 756 matching the shipped pre-change baseline.',
        'This authorised verification-budget exhaustion is a baseline observation, not a candidate defect, regression',
        'or waived gate. Index-budget exhaustion and truncated detection are forbidden and fail the C0 run.',
        'The candidate applies the strict shared gate to every frame and records zero authorised verification-budget',
        'exhaustion, zero index-budget exhaustion, zero detection truncation, and zero node-residual mismatches.',
        'The candidate removes the pre-existing verification-budget exhaustion rather than introducing it.',
        'The C0 control gate also records, without rejecting, disagreement between the frame node-residual field and',
        'the raw geometric pair scan under ' + control4['authorisation'] + '; the measured figures are in',
        '"Authorised C0 node-residual control" below.', '',
        '| Config | Fixture | exhausted_control | Gated frames | Authorised verification exhaustion | Forbidden index exhaustion | Forbidden detection truncation | Node-residual mismatches | Max verifications | Max residual conflict keys | Max repairs |',
        '| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |']
    for r in result['exhaustion_control']:
        lines.append('| ' + ' | '.join(str(r[k]) for k in ('configuration', 'fixture', 'exhausted_control', 'gated_frames',
            'authorised_verification_budget_exhausted_frames', 'forbidden_index_budget_exhausted_frames',
            'forbidden_detection_truncated_frames', 'node_residual_mismatch_frames', 'max_verifications_observed',
            'max_residual_conflict_keys', 'max_repairs_observed')) + ' |')
    lines += ['', '## Authorised C0 node-residual control', '',
        'The fourth C0 recorded condition is authorised explicitly by ' + control4['authorisation'] + ', not inferred',
        'from the ruling principle: disagreement between the frame node-residual field and the raw geometric pair scan',
        'is recorded, never rejected, for the C0 baseline.', '',
        'Measured on the C0 cross baseline: ' + str(control4['mismatched_frames']) + ' of ' + str(control4['gated_frames'])
        + ' gated frames disagree, and in every one of them the frame field reports ' + str(control4['frame_field'])
        + ' while the raw scan reports ' + str(control4['raw_scan']) + '.', '',
        'The seeded C0 frames still require a zero raw node residual, so the raw geometry remains gated. The candidate',
        'applies the strict shared gate to every frame, keeps the shared equality assertion, and records zero',
        'node-residual mismatches; no candidate-side requirement is relaxed.', '']
    lines += ['', 'Matrix cases are raw/seed consumer checks only; endpoints-cross-ne/ee are qualified settled supplemental cases.',
        'Full precision coordinates, input order, classification, repairs, clocks and all 100 follow-up samples are in the adjacent per-run .log/.json files.',
        'C0 S1 qualification is measured before candidate runs. Missing/failed/timeout/truncated runs fail once and cannot be promoted as settled.',
        'A floor label describes a median; topology incidence counts do not identify its binding cause.', '']
    (output / 'acceptance.md').write_text('\n'.join(lines))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--repo', type=pathlib.Path, required=True)
    parser.add_argument('--classpath', required=True)
    parser.add_argument('--output', type=pathlib.Path, required=True)
    args = parser.parse_args()
    run_acceptance(args.repo.resolve(), args.classpath, args.output.resolve())
