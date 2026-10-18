# Graph Initial Spacing Epoch 3 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking; do not edit this pinned plan
> to tick them during execution.

**Goal:** Repair the four approved final-gate defects through test-only changes and produce honest, immutable epoch-3 evidence for the existing ring-only spacing implementation.

**Architecture:** One lane, `spacing`, executes six tasks in order. The first four tasks change exactly one authorized test class each and create external evidence tools; the fifth collects supporting evidence and the four exact integration gates; the sixth writes the acceptance record outside Git. Production behavior remains frozen, and acceptance fails closed if the evidence, candidate, lane and delivery trees differ.

**Tech Stack:** Java 8 source/bytecode target; Java 21.0.8-zulu runtime; installed Gradle; JUnit 4 and AssertJ; Python 3; X11 `xprop`, Xvfb and Openbox upstream release 3.6.1; Git and SHA-256.

**Authority:** `docs/superpowers/specs/2026-10-16-graph-initial-spacing-spec.md`, including Sections 12–21 (SHA-256 `5561e22397b216788403f22709e2e0b37c0e10a110f4a7bd367dc1b292a51e74`), and `docs/superpowers/specs/2026-10-18-graph-initial-spacing-epoch-3-design.md`, revision 5 (SHA-256 `2b2ea8ec83bc9eb7144826a09e165ccc373fc45a5cd3226dca8e47cd2690a72f`). Design review 5, specification review 2 and the D1/D2 amendment review are the approved reviews. The epoch-2 plan is a style reference, not an epoch-3 execution plan.

**Planning status:** This document specifies the authorized implementation and evidence work. It does not claim that implementation, runners, tests, receipts, or acceptance have completed. The earlier execution-contract conflict is **resolved by explicit user decision on 2026-10-18**: the acceptance record is written **outside Git** under `$EPOCH3_ROOT/acceptance/`, so no task adds a repository document after the final-tree measurements. The delivered tree therefore equals the measured candidate tree exactly, and no source-only digest, documentation exclusion, or reordered provenance exception is authorized. A repository copy of the acceptance record, if the user later wants one, is a separate post-acceptance docs-only delivery commit and is never claimed as measured evidence. Tasks 1–6 and an honest acceptance record are concrete and reviewable.

**File structure and responsibility:**

| Task | Repository write scope | External responsibility |
| --- | --- | --- |
| 1 | `AccessibleGraphCanvasShould.java` | Common command recorder/sealer; scoped headless/display XML receipts |
| 2 | `GraphWorkspaceWindowModelShould.java` | Immutable Openbox runner; WM negative controls and positive/optional evidence |
| 3 | `GraphWorkspacePerformanceDiagnostic.java` | Frozen baseline receipt; four-sample conflict probe and exact classpath setup |
| 4 | `GraphWorkspaceLifecycleShould.java` | Bounded teardown-barrier investigation and disposable closed-guard mutant |
| 5 | None | Clean candidate identity; final supporting runs; four final gates; immutable evidence index |
| 6 | None (acceptance record is external, outside Git) | Acceptance audit, requirement mapping and delivery-tree equality receipt |

## Global Constraints

- Task identifier is `graph-initial-spacing`; epoch is 3. There is exactly one lane, `spacing`, with sequence 1–6 and no concurrent execution. Each task consumes its predecessor's reviewed outputs. Do not reinterpret the fixed implementer tiers or add tasks.
- Read-only authorities for every task: `docs/superpowers/specs/2026-10-16-graph-initial-spacing-spec.md` including Sections 12–21, SHA-256 `5561e22397b216788403f22709e2e0b37c0e10a110f4a7bd367dc1b292a51e74`; `docs/superpowers/specs/2026-10-18-graph-initial-spacing-epoch-3-design.md` revision 5, SHA-256 `2b2ea8ec83bc9eb7144826a09e165ccc373fc45a5cd3226dca8e47cd2690a72f`; `$PM_RUN_ROOT/reports/epoch3-design-review-5.md`; and `$PM_RUN_ROOT/reports/epoch3-spec-review-2.md`, plus `$PM_RUN_ROOT/reports/epoch3-amendment-review-{1,2,3}.md`. Any impossible requirement is reported rather than silently repaired.
- Execute from the actual assigned isolated lane/candidate worktree, not an assumed current directory. Record `pwd -P`, `git rev-parse HEAD`, `git rev-parse 'HEAD^{tree}'`, and `git status --porcelain=v1 --untracked-files=all`. `CANDIDATE_ROOT` must be an absolute canonical path to a clean pinned checkout for every final supporting run and final gate. Early red/green iteration receipts are explicitly provisional and must be replayed after all four test changes have been committed.
- `PM_RUN_ROOT=/home/henry/.local/state/pi/project-manager/runs/0233ff88d3cc54bbd61550c61fa917d6e2a5717f8a03be4e9aa63dcd47dd1cfa/pm-run-20261016-125451-a5048653`; `EPOCH3_ROOT=$PM_RUN_ROOT/reports/epoch-3`; final `EVIDENCE_ROOT=$EPOCH3_ROOT/supporting-evidence`. These variable names expand only to the exact roots stated here. Each task's Files block explicitly bounds its external writes. Historical reports, quarantines, archives, and `/tmp/graph-initial-spacing-evidence/task-*/` are read-only inputs.
- Export `JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu` and prepend `$JAVA_HOME/bin` to `PATH`. All non-UI commands use `JAVA_TOOL_OPTIONS=-Djava.awt.headless=true`; display commands explicitly use `env -u JAVA_TOOL_OPTIONS` and an isolated X display. Use installed `gradle`, never `gradlew` or Maven. Keep the Java-8 compilation and bundle checks active. The recorder may use `GRADLE_OPTS=-Dorg.gradle.daemon=false` so run-owned workers have observable lifetimes; record the effective environment without changing project build files.
- The only repository source write targets are `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvasShould.java`, `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java`, `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GraphWorkspacePerformanceDiagnostic.java`, and `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceLifecycleShould.java`. No production source/API, build configuration, fixture resource, dependency, serialization format, persistence field, existing specification/design, epoch-2 plan, observer, comparator, or documentation file may change. Task 6 writes no repository file at all. A task's read-only references never grant write access. `GraphWorkspaceIntegrationSupport` is a package-private class inside the unauthorized `GraphWorkspaceColdReloadShould.java`, not a separately editable helper file. Ignored build/test/BIN outputs produced by the listed commands are permitted generated artifacts, not source-edit permission.
- Freeze all `*/src/main/**` bytes against integrated epoch-2 commit `92f31fce842f8e5bb1376c13e4135cdd138907db`. In particular, `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java` must remain SHA-256 `7840cb0c845cc4c06dbe417aa566959fb9bace2844968a402f13e89d9b1569e7`, and `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/ClusterPacking.java` must remain SHA-256 `29abf48532f7aa3e35b787afd607172669192e00eb39a0e3838873a56f024d68`. Check this before/after every mutation experiment and final gate. If any production correction is indicated, stop for an explicit user decision; do not implement one.
- Preferred content-ring gap remains 24.0; relationship rest lengths continue to use clearance 6.0; packing gutter remains 8.0; base radius remains 8.0; settle margin/frame clearance remains 8.0/16.0; boundary padding/hull clearance remains 8.0/16.0. Calibration remains `(1.0, 0.30, 1.0)` and typed attraction/repulsion/boundary repulsion remain `0.05 / 16.0 / 0.5`.
- Aggregate cross-map displacement cap remains 0.005 per affected particle per engine step. Production idle remains 8 consecutive frames, RMS <=0.05 and maximum displacement <=0.10. Preserve existing `<8.0` one-step checks, coverage, safety, pins, determinism, boundary, and edge-routing assertions.
- Preserve the four outcome cases NR8 A, SM8 A, XM4x2 A and XM4x2 B, each candidate-versus-baseline median delta `>= 6.0 - 1e-6`. First idle uses <=2000 `step()` calls and one monotonic 30-second deadline per fixture/version. Post idle uses the next 100 sequential frames from that same worker with its own 30-second deadline and no improvement threshold. External per-fixture/version watchdog remains 65 seconds.
- `OPERATION_TIMEOUT_NANOS` remains 5 seconds; `PROCESS_DEADLINE_NANOS` remains 40 minutes; retain 8 raw-reference frames, each <=5 seconds; 200-enclosure submission remains <=3.0 seconds with the existing 5-second await. Do not repeat the approximately 35-minute strict diagnostic during Tasks 1–4. No idle threshold, correction budget, convergence cap, operation/process deadline, safety budget, non-spacing golden, or unrelated assertion may change.
- `FREEPLANE_GRAPH_TEST_WINDOW_MANAGER` accepts only `required` (also the unset default) and `optional`; every other value fails. Optional skips require independent same-display pre-maximize absence proof for `_NET_SUPPORTING_WM_CHECK`. Query/tool failure, malformed properties, a present broken WM, missing `_NET_WORKAREA`, a failed 2 px AWT/EWMH cross-check, maximize timeout, or unstable frame must fail, never skip. Keep 10 ms polls, 2100 ms continuous quiet, and the existing 5000 ms monotonic deadline.
- The only changed conflict golden is `TWO_PINNED_MAPS_EXPECTED_CONFLICTS`, from stale 1 to measured 11. Every one of the 11 conflicts has reason `IMMOVABLE_SIDES`; exactly 2 have kind `SIBLING_CROSSING` and exactly 9 have kind `SIBLING_CONTAINMENT`; every conflict's unordered blocking-pin identity set is exactly `{(00000000-0000-0000-0000-000000000001, m00-n0001), (00000000-0000-0000-0000-000000000002, m01-n0001)}`, resolved through the fixture's persisted `PinProjection`/`NodeReference` values. Preserve baseline 20 provenance and four individually complete final-candidate first-submission samples with 5-second awaits.
- The four exact final gates are headless `gradle :freeplane_plugin_graph:check --console=plain -PTestLoggingFull`; headless `gradle :freeplane_plugin_graph:graphPerformanceDiagnostic -PgraphStrictPerformance --console=plain`; display-backed `env -u JAVA_TOOL_OPTIONS xvfb-run -a gradle :freeplane_plugin_graph:graphOsgiSmoke :freeplane_plugin_graph:freeplaneLaunchSmoke --console=plain`; and headless `gradle :freeplane:compileJava test --console=plain -PTestLoggingFull`. All four are independently required with exit 0 and receipts under `$EPOCH3_ROOT/final-gates/gate-N/`; the six supporting runs are additional requirements.
- Openbox positive evidence requires upstream 3.6.1, preflight/version/package/executable hashes, launched-PID/root-`_OPENBOX_PID` agreement, executable identity, root/support-window `_NET_SUPPORTING_WM_CHECK` agreement, support self-reference, and support `_NET_WM_NAME` identifying Openbox. Stock Openbox 3.6.1 does not require support-window `_NET_WM_PID`; never synthesize that property. A missing prerequisite emits `WM_PREREQUISITE_MISSING:OPENBOX_3_6_1` and BLOCKED, not PASS or SKIPPED. Re-preflight at execution time; the specification's drafting-time absence is historical context.
- Evidence states are exactly PASS, FAIL, NOT RUN, BLOCKED. Preserve command failures and XML rather than overwriting them. Every run records exact argv, effective environment, cwd, start/end timestamps, exit status, candidate HEAD/tree/clean status and source hashes. Preserve full stdout/stderr, emitted JUnit XML, diagnostics and relevant artifacts. Hash runners before use, hash their bytes again afterward, and seal each completed receipt/log/probe/classpath/generated-artifact set with an immutable manifest and manifest-hash sidecar. Do not edit a sealed directory.
- A fresh evidence directory must not contain prior run receipts. Stage probe inputs in that fresh directory before execution. For an explained rerun, allocate a new explicitly named attempt under the owning task's evidence root and preserve the earlier attempt; never overwrite primary evidence. Task 5's evidence index must identify which final attempt is authoritative and retain all previous failures. Evidence from a dirty tree or earlier task head is provisional only.
- Record the actual `DISPLAY`, Xvfb/Openbox/process identities and exit/reaping outcomes for UI runs. A stale worker, probe, capture/launch child, Openbox or Xvfb makes evidence incomplete. Do not kill unrelated user processes. Missing process/cleanup proof is BLOCKED, not PASS.
- Final candidate, final lane and final integration/delivery `git rev-parse 'HEAD^{tree}'` values must be identical. This is achievable exactly as written because no task adds, edits or deletes a repository file after Task 5's measurements: the acceptance record is external, and Task 6's checkpoint commit is empty. A receipt generated from an earlier head, a dirty tree, or any tree-changing post-measurement commit cannot satisfy acceptance. No source-only digest, documentation-tree exclusion, or retroactive re-measurement exception is authorized. Any repository doc-only commit recording the acceptance outcome is a separate post-acceptance delivery step, labelled as such, and is never presented as measured evidence.
- Preserve epoch-1 and epoch-2 quarantine/convergence findings. Read `$PM_RUN_ROOT/reports/task-1-convergence-finding.md`, `$PM_RUN_ROOT/archives/epoch-2-spacing-fence/evidence/gate-classification.json`, and `$PM_RUN_ROOT/archives/epoch-2-spacing-fence/evidence/epoch2-reconciliation-validation.json` without modification. Do not reuse a quarantined lane, delete archives, fabricate acceptance, merge to the delivery branch, or push as part of these tasks.
- Commit steps below are future implementation instructions. Stage only each task's repository allowlist. External PM artifacts are not staged in Git; an external-only task uses an explicit empty checkpoint commit. Every commit message contains `graph-initial-spacing`. The planning operation that creates this plan does not execute these commits.

## Task 1: Scope accessibility headless assumptions to the two actual-window methods

**Implementer tier:** Advanced

**Lane:** spacing

**Sequence:** 1

**Depends on:** none

**Files:**

- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvasShould.java:1-76,234-236,327-329` — imports and the first statement of exactly the two named methods.
- Create: `/home/henry/.local/state/pi/project-manager/runs/0233ff88d3cc54bbd61550c61fa917d6e2a5717f8a03be4e9aa63dcd47dd1cfa/pm-run-20261016-125451-a5048653/scripts/epoch-3/record-run.py`.
- Create: `/home/henry/.local/state/pi/project-manager/runs/0233ff88d3cc54bbd61550c61fa917d6e2a5717f8a03be4e9aa63dcd47dd1cfa/pm-run-20261016-125451-a5048653/scripts/epoch-3/seal-evidence.py`.
- Create: `/home/henry/.local/state/pi/project-manager/runs/0233ff88d3cc54bbd61550c61fa917d6e2a5717f8a03be4e9aa63dcd47dd1cfa/pm-run-20261016-125451-a5048653/reports/epoch-3/task-1/` — red, headless/display green, source-freeze, tool-hash and XML-attribution artifacts only.
- Generated only: ignored Gradle compilation/test outputs from the validation commands below. No other repository file is a write target.

**Interfaces:**

- Consumes: existing `withFocusedCanvas(GraphCanvas, Runnable)`; the repository's JUnit-4 pattern `Assume.assumeFalse(GraphicsEnvironment.isHeadless())`.
- Produces: the unchanged 11 test methods with assumptions only in `receiveTabThroughSwingDispatchAndRestoreFocusTraversalConfiguration()` and `ignoreEnterForSelectionsAbsentFromCurrentVisibleOrderAfterStateReplacement()`.
- Produces: `python3 record-run.py [--allow-dirty] RECEIPT_DIR -- COMMAND [ARG ...] -> command exit code`, or exit 78 for prerequisite/provenance/cleanup BLOCKED. It writes `run.json`, `gradle.log`, process evidence and copies command artifacts; it never changes source. `--allow-dirty` is for provisional iterations only.
- Produces: `python3 seal-evidence.py DIR -> 0`, writing one `SHA256SUMS` manifest plus `SHA256SUMS.sha256`, rejecting an already sealed directory. Tasks 2–6 consume these exact CLIs and `run.json` fields.
- Produces: provisional accessibility receipts with explicit source identity. Final supporting runs 1 and 2 are replayed by Task 5 at the common candidate tree.

**Validation commands:**

```bash
export JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"
export JAVA_TOOL_OPTIONS=-Djava.awt.headless=true
export GRADLE_OPTS=-Dorg.gradle.daemon=false
export CANDIDATE_ROOT="$(pwd -P)"
export PM_RUN_ROOT=/home/henry/.local/state/pi/project-manager/runs/0233ff88d3cc54bbd61550c61fa917d6e2a5717f8a03be4e9aa63dcd47dd1cfa/pm-run-20261016-125451-a5048653
python3 "$PM_RUN_ROOT/scripts/epoch-3/record-run.py" --allow-dirty "$PM_RUN_ROOT/reports/epoch-3/task-1/headless-green" -- gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.AccessibleGraphCanvasShould' --console=plain -PTestLoggingFull --rerun-tasks
python3 "$PM_RUN_ROOT/scripts/epoch-3/record-run.py" --allow-dirty "$PM_RUN_ROOT/reports/epoch-3/task-1/display-green" -- env -u JAVA_TOOL_OPTIONS xvfb-run -a gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.AccessibleGraphCanvasShould' --console=plain -PTestLoggingFull --rerun-tasks
git diff --check
git diff --exit-code 92f31fce842f8e5bb1376c13e4135cdd138907db -- '*/src/main/**'
```

- [ ] **Step 1: Verify inputs and establish the uncorrected failure mechanism**

Check the authority hashes and production freeze. Read the two methods and their helper; confirm the other nine methods do not create the real-window fixture. The required red witness is the two existing `HeadlessException` failures at frame construction. Create the evidence tools in Steps 2–3 before recording that unmodified test run; do not add failing tests or change existing assertions to manufacture red.

- [ ] **Step 2: Create the external command recorder**

Save the following as `$PM_RUN_ROOT/scripts/epoch-3/record-run.py`. Its evidence is deliberately separate from semantic acceptance: exit 0 alone never proves expected skips, pin identities, or gate requirements. Snapshot source and runner hashes even for failed commands. Provisional receipts must say `provisional=true`. The recorded Python launch gate waits until its PID/session is observed, then executes the exact requested argv via `os.execvpe`; this preserves identity through `exec` and prevents short controls from escaping observation.

```python
import argparse, datetime, hashlib, json, os, shutil, subprocess, sys, time
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument('--allow-dirty', action='store_true')
p.add_argument('directory')
p.add_argument('command', nargs=argparse.REMAINDER)
a = p.parse_args()
argv = a.command[1:] if a.command[:1] == ['--'] else a.command
if not argv:
    p.error('command is required after --')
root = Path(os.environ['CANDIDATE_ROOT']).resolve(strict=True)
out = Path(a.directory).resolve()
out.mkdir(parents=True, exist_ok=True)
if any((out / n).exists() for n in ('run.json', 'gradle.log', 'SHA256SUMS')):
    raise SystemExit('Refusing to overwrite prior receipt')
def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def git(*args):
    return subprocess.check_output(['git', '-C', str(root), *args], text=True).strip()
def identity():
    return {'head': git('rev-parse', 'HEAD'), 'tree': git('rev-parse', 'HEAD^{tree}'),
            'status': git('status', '--porcelain=v1', '--untracked-files=all')}
def sources():
    names = git('ls-files').splitlines()
    return {n: sha(root / n) for n in names if '/src/main/' in n
            or n.startswith('freeplane_plugin_graph/src/test/')}
def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()
# Construct and record the exact environment actually passed, without carrying tokens.
keys = ('HOME', 'USER', 'LOGNAME', 'PATH', 'LANG', 'LC_ALL', 'TMPDIR', 'JAVA_HOME',
        'JAVA_TOOL_OPTIONS', 'GRADLE_OPTS', 'GRADLE_USER_HOME', 'DISPLAY', 'XAUTHORITY',
        'CANDIDATE_ROOT', 'PM_RUN_ROOT', 'EVIDENCE_ROOT', 'FREEPLANE_GRAPH_TEST_WINDOW_MANAGER')
env = {k: os.environ[k] for k in keys if k in os.environ}
scripts = Path(env['PM_RUN_ROOT']) / 'scripts/epoch-3'
runner_hashes = {str(f): sha(f) for f in sorted(scripts.rglob('*')) if f.is_file()}
before, initial_sources = identity(), sources()
receipt = {'argv': argv, 'environment': env, 'cwd': str(root), 'start': now(),
           'before': before, 'sourceHashes': initial_sources, 'runnerHashes': runner_hashes,
           'provisional': a.allow_dirty, 'status': 'NOT RUN', 'exitStatus': None}
started_ns = time.time_ns()
seen = {}
def processes(group):
    rows = {}
    for proc in Path('/proc').glob('[0-9]*'):
        try:
            stat = (proc / 'stat').read_text()
            tail = stat[stat.rfind(')') + 2:].split()
            pid, started = int(proc.name), tail[19]
            args = (proc / 'cmdline').read_bytes().replace(b'\0', b' ').decode(errors='replace')
            pairs = (proc / 'environ').read_bytes().split(b'\0')
            display_env = {}
            for pair in pairs:
                key, sep, value = pair.partition(b'=')
                if sep and key in (b'DISPLAY', b'JAVA_TOOL_OPTIONS', b'FREEPLANE_GRAPH_TEST_WINDOW_MANAGER'):
                    display_env[key.decode()] = value.decode(errors='replace')
            rows[(pid, started)] = {'pid': pid, 'startTicks': started, 'state': tail[0],
                'ppid': int(tail[1]), 'pgrp': int(tail[2]), 'session': int(tail[3]),
                'argv': args, 'environment': display_env}
        except (OSError, ValueError, IndexError):
            continue
    owned = {key for key, row in rows.items()
             if row['pgrp'] == group or row['session'] == group or key in seen}
    while True:
        parent_ids = {rows[key]['pid'] for key in owned}
        expanded = owned | {key for key, row in rows.items() if row['ppid'] in parent_ids}
        if expanded == owned:
            break
        owned = expanded
    for key in owned:
        row = rows[key]
        row['firstObserved'] = seen.get(key, {}).get('firstObserved', now())
        row['lastObserved'] = now()
        seen[key] = row
    return [rows[key] for key in sorted(owned)]
blocked = []
if before['status'] and not a.allow_dirty:
    blocked.append('DIRTY_CANDIDATE')
rc = 78
try:
    subprocess.run(['git', '-C', str(root), 'diff', '--exit-code',
                    '92f31fce842f8e5bb1376c13e4135cdd138907db', '--', '*/src/main/**'], check=True)
    if not blocked:
        with (out / 'gradle.log').open('x') as log:
            # The launcher waits before exec so even a one-line command has an observed owner.
            launch_code = '''import os, select, sys
fd = int(sys.argv[1])
if not select.select([fd], [], [], 5.0)[0]:
    raise SystemExit(78)
token = os.read(fd, 1)
os.close(fd)
if token != b'1':
    raise SystemExit(78)
os.execvpe(sys.argv[2], sys.argv[2:], os.environ)
'''
            launch_read, launch_write = os.pipe()
            try:
                launch_argv = [sys.executable, '-c', launch_code, str(launch_read), *argv]
                receipt['launcher'] = {'argv': launch_argv,
                    'codeSha256': hashlib.sha256(launch_code.encode()).hexdigest()}
                child = subprocess.Popen(launch_argv, cwd=root, env=env, stdout=log,
                    stderr=subprocess.STDOUT, start_new_session=True, pass_fds=(launch_read,))
                os.close(launch_read)
                launch_read = None
                first = processes(child.pid)
                receipt['launchOwnerObserved'] = any(row['pid'] == child.pid for row in first)
                if receipt['launchOwnerObserved']:
                    os.write(launch_write, b'1')
                else:
                    blocked.append('OWNED_PROCESS_OBSERVATION_MISSING')
            finally:
                if launch_read is not None:
                    os.close(launch_read)
                os.close(launch_write)
            while child.poll() is None:
                processes(child.pid)
                time.sleep(0.02)
            rc = child.wait()
            retirement_deadline = time.monotonic() + 5.0
            survivors = processes(child.pid)
            while survivors and time.monotonic() < retirement_deadline:
                time.sleep(0.02)
                survivors = processes(child.pid)
        if not seen:
            blocked.append('OWNED_PROCESS_OBSERVATION_MISSING')
        if survivors:
            blocked.append('CHILD_PROCESS_NOT_REAPED')
        receipt['processes'] = list(seen.values())
        receipt['cleanup'] = {'directChildExit': rc, 'directChildWaited': True,
                              'survivors': survivors, 'complete': not survivors}
        text = (out / 'gradle.log').read_text(errors='replace')
        if 'WM_PREREQUISITE_MISSING:OPENBOX_3_6_1' in text:
            blocked.append('WM_PREREQUISITE_MISSING:OPENBOX_3_6_1')
    after = identity()
    if after != before or sources() != initial_sources:
        blocked.append('CANDIDATE_CHANGED_DURING_RUN')
    if any(not Path(f).exists() or sha(f) != h for f, h in runner_hashes.items()):
        blocked.append('RUNNER_CHANGED_DURING_RUN')
    receipt['after'] = after
except BaseException as error:
    blocked.append(type(error).__name__ + ': ' + str(error))
finally:
    receipt.update(end=now(), exitStatus=rc, blockers=blocked,
                   status='BLOCKED' if blocked else ('PASS' if rc == 0 else 'FAIL'))
    receipt['freshJUnitXml'] = []
    receipt['staleJUnitXml'] = []
    for source in sorted(root.glob('*/build/test-results/test')):
        target = out / 'artifacts' / source.relative_to(root)
        shutil.copytree(source, target, dirs_exist_ok=True)
        for xml in sorted(source.glob('TEST-*.xml')):
            category = 'freshJUnitXml' if xml.stat().st_mtime_ns >= started_ns else 'staleJUnitXml'
            receipt[category].append(str((target / xml.name).relative_to(out)))
    for relative in ('freeplane_plugin_graph/build/graph-performance',
                     'freeplane_plugin_graph/build/freeplane-launch-smoke'):
        source = root / relative
        if source.exists():
            shutil.copytree(source, out / 'artifacts' / relative, dirs_exist_ok=True)
    (out / 'run.json').write_text(json.dumps(receipt, indent=2, sort_keys=True) + '\n')
raise SystemExit(78 if blocked else rc)
```

Before final use, validate this recorder using an external `python3 -c 'print("receipt-control")'` command and an intentional nonzero command in separate task-1 control directories. Require their exact exit statuses, source identities, and unmodified recorder hash. Validate nested-session observation with `python3 -c 'import subprocess,sys; subprocess.run([sys.executable,"-c","import time; time.sleep(0.3)"],start_new_session=True,check=True)'`; the receipt must contain the nested PID/session and show eventual absence. These are recorder controls, not test retries or production timeout changes. Final semantic receipts must use the just-emitted paths in `freshJUnitXml`; preserved stale XML never proves a run executed. If actual display, owned-process observation or reaping proof is missing, mark the semantic receipt BLOCKED. A surviving owned process is an incomplete receipt; retire only its recorded PID/start-time identity before any next run, preserving that abnormal cleanup and BLOCKED status.

- [ ] **Step 3: Create the immutable evidence sealer**

Save this as `$PM_RUN_ROOT/scripts/epoch-3/seal-evidence.py`. Call it only after counts, display/cleanup observations and any semantic receipt have been added. The manifest lists every other artifact; its own digest lives in the sidecar, avoiding self-hashing recursion.

```python
import hashlib, sys
from pathlib import Path
root = Path(sys.argv[1]).resolve(strict=True)
manifest, sidecar = root / 'SHA256SUMS', root / 'SHA256SUMS.sha256'
if manifest.exists() or sidecar.exists():
    raise SystemExit('Evidence is already sealed')
rows = []
for f in sorted(root.rglob('*')):
    if f.is_file():
        rows.append(hashlib.sha256(f.read_bytes()).hexdigest() + '  ' + str(f.relative_to(root)))
manifest.write_text('\n'.join(rows) + '\n')
sidecar.write_text(hashlib.sha256(manifest.read_bytes()).hexdigest() + '  SHA256SUMS\n')
```

Now record the unmodified headless run before the assumptions are inserted:

```bash
python3 "$PM_RUN_ROOT/scripts/epoch-3/record-run.py" --allow-dirty "$PM_RUN_ROOT/reports/epoch-3/task-1/headless-red" -- gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.AccessibleGraphCanvasShould' --console=plain -PTestLoggingFull --rerun-tasks
```

Require the two named `HeadlessException` failures and preserve their fresh XML/logs; a setup failure is not the required red witness. Seal this red directory after attribution.

- [ ] **Step 4: Add only the two method-entry assumptions**

Add `import java.awt.GraphicsEnvironment;` and `import org.junit.Assume;`. Insert exactly this statement immediately after each of the two named method declarations, before `Fixture.create()` and any `JFrame` construction:

```java
Assume.assumeFalse(GraphicsEnvironment.isHeadless());
```

Do not add a guard to `withFocusedCanvas`, the class, or any other method. Keep every existing focus, traversal, selection/state-replacement and cleanup assertion byte-for-byte.

- [ ] **Step 5: Run the headless and display commands and attribute every method**

Use the validation commands in fresh provisional directories. Preserve the selected class XML immediately after each invocation. Verify the XML rather than relying on Gradle's aggregate summary:

```python
import json, os, xml.etree.ElementTree as ET
from pathlib import Path
base = Path(os.environ['PM_RUN_ROOT']) / 'reports/epoch-3/task-1'
named = {'receiveTabThroughSwingDispatchAndRestoreFocusTraversalConfiguration',
         'ignoreEnterForSelectionsAbsentFromCurrentVisibleOrderAfterStateReplacement'}
for directory, expected_skips in (('headless-green', named), ('display-green', set())):
    d = base / directory
    paths = list(d.rglob('TEST-org.freeplane.plugin.graph.canvas.AccessibleGraphCanvasShould.xml'))
    assert len(paths) == 1, paths
    cases = ET.parse(paths[0]).getroot().findall('testcase')
    assert len(cases) == 11
    assert all(c.find('failure') is None and c.find('error') is None for c in cases)
    skipped = {c.attrib['name'] for c in cases if c.find('skipped') is not None}
    assert skipped == expected_skips, skipped
    (d / 'method-counts.json').write_text(json.dumps({'tests': 11,
        'passed': 11 - len(skipped), 'skipped': sorted(skipped), 'failures': 0, 'errors': 0}, indent=2) + '\n')
```

For the display receipt, require process evidence showing the actual `DISPLAY`, that headless forcing was removed in the child environment, and no remaining run-owned Xvfb/test worker. Seal both green directories after these checks; the red receipt was already sealed in Step 3. Headless expectation is exactly two named skips plus nine passes; display expectation is 11 passes and zero skips/failures/errors.

- [ ] **Step 6: Review the scoped diff and publish the provisional handoff**

Run `git diff --check` and the production-freeze command. Record both production SHA-256 values, the two external script hashes, actual test counts and all receipt paths under `task-1/handoff.json`. Note that full-module skip preservation and final-tree supporting runs 1/2 remain Task 5 obligations. No headless aggregate count alone proves attribution.

- [ ] **Step 7: Commit**

```bash
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvasShould.java
git diff --cached --check
git diff --cached --stat
git commit -m "graph-initial-spacing: scope accessibility display assumptions"
```

## Task 2: Prove window-manager capability and bounded maximized-window stability

**Implementer tier:** Advanced

**Lane:** spacing

**Sequence:** 2

**Depends on:** Task 1

**Files:**

- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java:1-134,1851-1887,2147-2162,2267-2380` — imports; the maximized-window method; the undecorated fullscreen method's two coverage call sites (which keep screen containment, passing `true`); `assertRoundTrip`; the coverage helpers and their screen-containment flag; the private EWMH helpers; and the narrowly gated stable-poll checks. That is the complete set of methods the D1 scoping and the WM precondition require, and nothing outside it may change.
- Create: `/home/henry/.local/state/pi/project-manager/runs/0233ff88d3cc54bbd61550c61fa917d6e2a5717f8a03be4e9aa63dcd47dd1cfa/pm-run-20261016-125451-a5048653/scripts/epoch-3/run-openbox-window-probe.sh`.
- Create: `/home/henry/.local/state/pi/project-manager/runs/0233ff88d3cc54bbd61550c61fa917d6e2a5717f8a03be4e9aa63dcd47dd1cfa/pm-run-20261016-125451-a5048653/reports/epoch-3/task-2/` — bare/required/invalid/tool-failure/malformed/positive iteration receipts, input scripts and handoff only.
- Read only: Task 1's `record-run.py` and `seal-evidence.py`. Generated only: ignored Gradle/display-test outputs from the listed commands. No build-property propagation or other source write is authorized.

**Interfaces:**

- Consumes: `record-run.py [--allow-dirty] RECEIPT_DIR -- COMMAND [ARG ...]`, `seal-evidence.py DIR`, and their `run.json` source/cleanup fields.
- Consumes: `System.getenv("FREEPLANE_GRAPH_TEST_WINDOW_MANAGER")`, `System.getenv("DISPLAY")`, installed `xprop`, `DisplayFixture.workArea(GraphicsConfiguration): Rectangle`, `awaitFrameStable(int): void`, `assertRoundTrip(boolean): void`, and `X11_AWT_BOUNDS_TOLERANCE=2`.
- Produces: test-only `queryRootProperty(String): String`, `requiredWorkArea(): Rectangle`, and `requireWindowManager(String): void`; their query output includes the same `DISPLAY` and raw property bytes in JUnit stdout. No public API is added.
- Produces: `bash run-openbox-window-probe.sh CANDIDATE_ROOT RECEIPT_DIR -> 0/1/78`; inner argument `--inner` is private to the runner. Exit 78 plus `WM_PREREQUISITE_MISSING:OPENBOX_3_6_1` means prerequisite BLOCKED. The outer receipt records the exact public invocation, and Task 5 replays it as supporting run 4.
- Produces: method-level optional absence proof and full-class Openbox evidence; normal-window/F11 methods remain active. Final supporting runs 3/4 are replayed at Task 5's common candidate tree.

**Validation commands:**

```bash
export JAVA_TOOL_OPTIONS=-Djava.awt.headless=true
python3 "$PM_RUN_ROOT/scripts/epoch-3/record-run.py" --allow-dirty "$PM_RUN_ROOT/reports/epoch-3/task-2/bare-optional" -- env -u JAVA_TOOL_OPTIONS FREEPLANE_GRAPH_TEST_WINDOW_MANAGER=optional xvfb-run -a gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould.f11RestoresBoundsAndExtendedStateFromMaximizedWindow' --console=plain -PTestLoggingFull --rerun-tasks
python3 "$PM_RUN_ROOT/scripts/epoch-3/record-run.py" --allow-dirty "$PM_RUN_ROOT/reports/epoch-3/task-2/bare-required" -- env -u JAVA_TOOL_OPTIONS FREEPLANE_GRAPH_TEST_WINDOW_MANAGER=required xvfb-run -a gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould.f11RestoresBoundsAndExtendedStateFromMaximizedWindow' --console=plain -PTestLoggingFull --rerun-tasks
python3 "$PM_RUN_ROOT/scripts/epoch-3/record-run.py" --allow-dirty "$PM_RUN_ROOT/reports/epoch-3/task-2/invalid-mode" -- env -u JAVA_TOOL_OPTIONS FREEPLANE_GRAPH_TEST_WINDOW_MANAGER=invalid xvfb-run -a gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould.f11RestoresBoundsAndExtendedStateFromMaximizedWindow' --console=plain -PTestLoggingFull --rerun-tasks
python3 "$PM_RUN_ROOT/scripts/epoch-3/record-run.py" --allow-dirty "$PM_RUN_ROOT/reports/epoch-3/task-2/openbox-class" -- env -u JAVA_TOOL_OPTIONS PM_RUN_ROOT="$PM_RUN_ROOT" bash "$PM_RUN_ROOT/scripts/epoch-3/run-openbox-window-probe.sh" "$CANDIDATE_ROOT" "$PM_RUN_ROOT/reports/epoch-3/task-2/openbox-class"
git diff --check
```

- [ ] **Step 1: Preserve the bare-Xvfb failure before the change**

Run the named maximized-window method on bare Xvfb before editing. Preserve the maximize timeout as a provisional red receipt. Do not infer WM absence from this timeout. Inspect the current helper: it already polls every 10 ms and has a 5000 ms deadline; its default 200 ms use serves other tests and must retain that behavior.

- [ ] **Step 2: Implement strict mode parsing and independent same-display queries**

Add the following private helpers in this test class. Add the Java-8-compatible imports they use. `queryRootProperty` waits for the query process to terminate before reading its small fixed-property output; process failures propagate as assertions. The display is supplied explicitly to `xprop` and logged. The absence pattern is a narrow match for xprop's absent-property forms, not a catch-all exception path.

```java
private static String queryRootProperty(final String property) throws Exception {
    final String display = System.getenv("DISPLAY");
    assertThat(display).as("current DISPLAY").isNotNull().isNotEmpty();
    final Process process = new ProcessBuilder("xprop", "-display", display, "-root", property)
        .redirectErrorStream(true).start();
    try {
        assertThat(process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS))
            .as("bounded xprop query for %s", property).isTrue();
        final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        final byte[] buffer = new byte[1024];
        int count;
        while ((count = process.getInputStream().read(buffer)) != -1) {
            bytes.write(buffer, 0, count);
        }
        final String text = new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8).trim();
        System.out.println("EWMH DISPLAY=" + display + " property=" + property + " output=" + text);
        assertThat(process.exitValue()).as("xprop exit: %s", text).isZero();
        return text;
    }
    finally {
        if (process.isAlive()) {
            process.destroyForcibly();
            assertThat(process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS))
                .as("xprop cleanup").isTrue();
        }
    }
}

private static void requireWindowManager(final String mode) throws Exception {
    final String supporting = queryRootProperty("_NET_SUPPORTING_WM_CHECK");
    final boolean absent = supporting.matches("_NET_SUPPORTING_WM_CHECK:\\s+"
        + "(?:not found\\.|no such atom on any window\\.)");
    if (absent) {
        queryRootProperty("_NET_WORKAREA"); // Same-display absent-path receipt, before maximization.
        if ("optional".equals(mode)) {
            Assume.assumeTrue("WM_ABSENT_SAME_DISPLAY:" + System.getenv("DISPLAY"), false);
        }
        throw new AssertionError("Required WM absent on " + System.getenv("DISPLAY"));
    }
    assertThat(supporting).matches("_NET_SUPPORTING_WM_CHECK\\(WINDOW\\): window id # 0x[0-9a-fA-F]+");
    final String xid = supporting.substring(supporting.lastIndexOf(' ') + 1);
    assertThat(Long.parseLong(xid.substring(2), 16)).as("nonzero WM support XID").isNotZero();
}

private static java.awt.Rectangle requiredWorkArea() throws Exception {
    final String desktop = queryRootProperty("_NET_CURRENT_DESKTOP");
    assertThat(desktop).matches("_NET_CURRENT_DESKTOP\\(CARDINAL\\) = [0-9]+");
    final int index = Integer.parseInt(desktop.substring(desktop.indexOf("= ") + 2));
    final String work = queryRootProperty("_NET_WORKAREA");
    assertThat(work).startsWith("_NET_WORKAREA(CARDINAL) = ");
    final String[] values = work.substring(work.indexOf("= ") + 2).split(",\\s*");
    assertThat(values.length % 4).isZero();
    assertThat(index).isGreaterThanOrEqualTo(0).isLessThan(values.length / 4);
    final long[] rectangle = new long[4];
    for (int i = 0; i < values.length; i++) {
        assertThat(values[i]).matches("[0-9]+");
        final long value = Long.parseLong(values[i]);
        assertThat(value).isBetween(0L, 4294967295L);
        if (i / 4 == index) {
            rectangle[i % 4] = value;
        }
    }
    assertThat(rectangle[2]).isBetween(1L, (long) Integer.MAX_VALUE);
    assertThat(rectangle[3]).isBetween(1L, (long) Integer.MAX_VALUE);
    return new java.awt.Rectangle((int) rectangle[0], (int) rectangle[1],
        (int) rectangle[2], (int) rectangle[3]);
}

private static void assertWorkAreaAgreement(final java.awt.Rectangle awt,
        final java.awt.Rectangle ewmh) {
    System.out.println("EWMH AWT=" + awt + " NET_WORKAREA=" + ewmh
        + " tolerance=" + DisplayFixture.X11_AWT_BOUNDS_TOLERANCE);
    final long tolerance = DisplayFixture.X11_AWT_BOUNDS_TOLERANCE;
    assertThat(Math.abs((long) awt.x - ewmh.x)).isLessThanOrEqualTo(tolerance);
    assertThat(Math.abs((long) awt.y - ewmh.y)).isLessThanOrEqualTo(tolerance);
    assertThat(Math.abs(((long) awt.x + awt.width) - ((long) ewmh.x + ewmh.width)))
        .isLessThanOrEqualTo(tolerance);
    assertThat(Math.abs(((long) awt.y + awt.height) - ((long) ewmh.y + ewmh.height)))
        .isLessThanOrEqualTo(tolerance);
}
```

Replace the body of the existing named method with the following. Validate mode before the existing headless guard so an invalid configured value never becomes an assumption skip. All actual fullscreen assertions remain in `assertRoundTrip(true)`.

```java
final String configured = System.getenv("FREEPLANE_GRAPH_TEST_WINDOW_MANAGER");
final String mode = configured == null ? "required" : configured;
assertThat(mode).as("FREEPLANE_GRAPH_TEST_WINDOW_MANAGER").isIn("required", "optional");
Assume.assumeFalse(GraphicsEnvironment.isHeadless());
requireWindowManager(mode);
final java.awt.Rectangle ewmh = requiredWorkArea();
try (DisplayFixture f = new DisplayFixture()) {
    final java.awt.Rectangle awt = f.edt(() -> DisplayFixture.workArea(f.window.getGraphicsConfiguration()));
    assertWorkAreaAgreement(awt, ewmh);
    f.onEdt(() -> f.window.setExtendedState(java.awt.Frame.MAXIMIZED_BOTH));
    f.await(() -> f.window.getExtendedState() == java.awt.Frame.MAXIMIZED_BOTH);
    f.requireMaximizedDuringStable = true;
    try {
        f.awaitFrameStable(2100);
    }
    finally {
        f.requireMaximizedDuringStable = false;
    }
    f.assertWorkAreaCoverage(false);
    f.checkpoint = f.edt(() -> f.commands.size());
    f.assertRoundTrip(true);
}
```

**D1 scoping (binding).** Screen containment must be relaxed **only** on the decorated maximized path. The existing helpers are shared with the undecorated fullscreen test, where screen containment is achievable and must keep being asserted. So:

1. `hasWorkAreaCoverage(boolean requireScreenContainment)` keeps all four work-area clauses unconditionally and applies its four screen-containment clauses only when the argument is `true`.
2. `assertWorkAreaCoverageOnEdt(boolean requireScreenContainment)` does the same, and the `assertWorkAreaCoverage(boolean)` wrapper delegates to it.
3. The undecorated fullscreen call sites in `f11MakesWindowUndecoratedAndFillsCurrentScreen` — `f.await(f::hasWorkAreaCoverage)` and `f.assertWorkAreaCoverage()` — keep `true`; this task only updates their call syntax to pass it explicitly.
4. The decorated maximized path passes `false`: the direct call above, and the two internal uses inside `assertRoundTrip(requireWorkArea = true)`.
5. `X11_AWT_BOUNDS_TOLERANCE` stays exactly 2 px, no clause is deleted, and no other call site changes.

Do not delete the screen-containment clauses outright and do not add a new helper that silently drops them for every caller.

- [ ] **Step 3: Strengthen each poll only for the maximized interval**

Add `private boolean requireMaximizedDuringStable;` to `DisplayFixture`. At the beginning of the existing `awaitFrameStable(int)` lambda, insert this block; keep the existing bounds-change timestamp reset and the existing `await(...)` deadline/polling implementation:

```java
if (requireMaximizedDuringStable) {
    assertThat(window.isVisible()).as("maximized frame visible throughout stable interval").isTrue();
    assertThat(window.isDisplayable()).as("maximized frame displayable throughout stable interval").isTrue();
    assertThat(window.getExtendedState() & java.awt.Frame.MAXIMIZED_BOTH)
        .as("MAXIMIZED_BOTH throughout stable interval").isEqualTo(java.awt.Frame.MAXIMIZED_BOTH);
}
```

Bounds changes restart the quiet interval; they cannot count toward the required 2100 continuous milliseconds. Endpoint work-area coverage precedes the original round trip. The extra field is false for all other existing stability calls.

- [ ] **Step 4: Author and hash the Openbox runner**

Create `$PM_RUN_ROOT/scripts/epoch-3/run-openbox-window-probe.sh` with the following content. The public invocation must be wrapped by `record-run.py`; that outer recorder captures command/environment/source/process evidence and copies XML, then semantic verification and sealing complete the receipt. A prerequisite marker always remains BLOCKED. Do not put package installation in this script; the approved Arch operator command is `sudo pacman -S --needed openbox`.

```bash
#!/usr/bin/env bash
set -euo pipefail
if [[ ${1:-} != --inner ]]; then
    candidate=$1
    receipt=$2
    mkdir -p "$receipt"
    if ! command -v openbox > "$receipt/openbox-command.txt"; then
        echo 'WM_PREREQUISITE_MISSING:OPENBOX_3_6_1'
        exit 78
    fi
    wm_exe=$(readlink -f "$(command -v openbox)") || {
        echo 'WM_PREREQUISITE_MISSING:OPENBOX_3_6_1'
        exit 78
    }
    if [[ ! -x $wm_exe ]]; then
        echo 'WM_PREREQUISITE_MISSING:OPENBOX_3_6_1'
        exit 78
    fi
    if ! openbox --version > "$receipt/openbox-version.txt" 2>&1; then
        echo 'WM_PREREQUISITE_MISSING:OPENBOX_3_6_1'
        exit 78
    fi
    if ! pacman -Q openbox > "$receipt/openbox-package.txt" 2>&1; then
        echo 'WM_PREREQUISITE_MISSING:OPENBOX_3_6_1'
        exit 78
    fi
    if ! python3 - "$receipt/openbox-version.txt" <<'PY'
import re, sys
from pathlib import Path
assert re.search(r'(?m)^Openbox 3\.6\.1\s*$', Path(sys.argv[1]).read_text())
PY
    then
        echo 'WM_PREREQUISITE_MISSING:OPENBOX_3_6_1'
        exit 78
    fi
    if ! sha256sum "$wm_exe" > "$receipt/openbox-executable.sha256"; then
        echo 'WM_PREREQUISITE_MISSING:OPENBOX_3_6_1'
        exit 78
    fi
    read -r wm_sha _ < "$receipt/openbox-executable.sha256"
    xvfb-run -a bash "$0" --inner "$candidate" "$receipt" "$wm_exe" "$wm_sha"
    exit $?
fi
candidate=$2
receipt=$3
wm_exe=$4
wm_sha=$5
wm_pid=
cleanup() {
    run_exit=$?
    trap - EXIT INT TERM
    set +e
    cleanup_status=0
    if [[ -n $wm_pid ]]; then
        kill -TERM "$wm_pid"
        term_status=$?
        forced=0
        if ! python3 - "$wm_pid" <<'PY'
import os, sys, time
from pathlib import Path
pid = int(sys.argv[1])
deadline = time.monotonic() + 5.0
while time.monotonic() < deadline:
    try:
        stat = Path('/proc', str(pid), 'stat').read_text()
    except FileNotFoundError:
        raise SystemExit(0)
    if stat[stat.rfind(')') + 2:].split()[0] == 'Z':
        raise SystemExit(0)
    time.sleep(0.02)
raise SystemExit(1)
PY
        then
            kill -KILL "$wm_pid"
            forced=1
        fi
        wait "$wm_pid"
        reap_status=$?
        if kill -0 "$wm_pid" 2>/dev/null; then cleanup_status=1; fi
        if [[ $term_status != 0 || $reap_status != 0 || $forced != 0 ]]; then cleanup_status=1; fi
        python3 - "$receipt" "$wm_pid" "$term_status" "$reap_status" "$cleanup_status" "$forced" <<'PY'
import json, sys
from pathlib import Path
out, pid, term, reap, incomplete, forced = sys.argv[1:]
Path(out, 'openbox-cleanup.json').write_text(json.dumps({
    'pid': int(pid), 'termExit': int(term), 'waitExit': int(reap),
    'forcedKill': bool(int(forced)), 'complete': not bool(int(incomplete)),
    'reaped': not Path('/proc', pid).exists()}, indent=2) + '\n')
PY
    fi
    if [[ $cleanup_status != 0 ]]; then run_exit=78; fi
    exit "$run_exit"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
"$wm_exe" --sm-disable > "$receipt/openbox.log" 2>&1 &
wm_pid=$!
python3 - "$receipt" "$wm_pid" "$wm_exe" "$wm_sha" <<'PY'
import hashlib, json, os, re, subprocess, sys, time
from pathlib import Path
out, pid, executable, expected_sha = sys.argv[1:]
out, pid = Path(out), int(pid)
deadline = time.monotonic() + 5.0
queries = []
def query(*args):
    left = deadline - time.monotonic()
    assert left > 0, 'WM readiness deadline exceeded'
    result = subprocess.run(['xprop', '-display', os.environ['DISPLAY'], *args],
        text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=left)
    queries.append({'argv': result.args, 'exit': result.returncode, 'output': result.stdout})
    assert result.returncode == 0, result.stdout
    return result.stdout.strip()
try:
    while True:
        os.kill(pid, 0)
        root = query('-root', '_NET_SUPPORTING_WM_CHECK')
        match = re.fullmatch(r'_NET_SUPPORTING_WM_CHECK\(WINDOW\): window id # (0x[0-9a-fA-F]+)', root)
        if match and int(match[1], 16):
            break
        assert re.fullmatch(r'_NET_SUPPORTING_WM_CHECK:\s+(not found\.|no such atom on any window\.)', root), root
        assert time.monotonic() < deadline
        time.sleep(min(0.02, max(0.0, deadline - time.monotonic())))
    actual_exe = Path('/proc', str(pid), 'exe').resolve(strict=True)
    assert actual_exe == Path(executable).resolve(strict=True)
    assert hashlib.sha256(actual_exe.read_bytes()).hexdigest() == expected_sha
    root_pid = query('-root', '_OPENBOX_PID')
    found_pid = re.fullmatch(r'_OPENBOX_PID\(CARDINAL\) = ([0-9]+)', root_pid)
    assert found_pid and int(found_pid[1]) == pid, root_pid
    xid = match[1]
    support = query('-id', xid, '_NET_SUPPORTING_WM_CHECK')
    self_check = re.fullmatch(r'_NET_SUPPORTING_WM_CHECK\(WINDOW\): window id # (0x[0-9a-fA-F]+)', support)
    assert self_check and int(self_check[1], 16) == int(xid, 16), support
    name = query('-id', xid, '_NET_WM_NAME')
    assert re.fullmatch(r'_NET_WM_NAME\(UTF8_STRING\) = "Openbox"', name), name
    query('-root', '_NET_CURRENT_DESKTOP')
    query('-root', '_NET_WORKAREA')
    (out / 'wm-identity.json').write_text(json.dumps({'DISPLAY': os.environ['DISPLAY'],
        'pid': pid, 'executable': str(actual_exe), 'sha256': expected_sha,
        'supportXid': xid, 'verified': True}, indent=2) + '\n')
finally:
    (out / 'ewmh-queries.json').write_text(json.dumps(queries, indent=2) + '\n')
PY
export FREEPLANE_GRAPH_TEST_WINDOW_MANAGER=required
unset JAVA_TOOL_OPTIONS
cd "$candidate"
gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould' --console=plain -PTestLoggingFull --rerun-tasks
```

Check `bash -n` before running. Hash this exact script before the first invocation and keep its bytes immutable afterward. Readiness is one 5-second monotonic window, not a separate 5-second budget per query. Retain all failed query output. The enclosing `xvfb-run` owns Xvfb shutdown; the recorder must observe its identity and eventual absence, in addition to the inner Openbox wait receipt.

- [ ] **Step 5: Run capability and negative-control checks**

Run the validation commands into fresh directories. Expected: bare optional has one named skip with `WM_ABSENT_SAME_DISPLAY` and the raw absent-property proof; bare required has one assertion failure and zero skips; invalid mode fails with zero skips before fixture construction. Also run with the variable unset on bare Xvfb and require the same failure as `required`.

Create separate executable `task-2/xprop-controls/query-error/xprop` and `task-2/xprop-controls/malformed/xprop` files. Their exact contents are respectively:

```bash
#!/usr/bin/env bash
exit 1
```

```bash
#!/usr/bin/env bash
case "$*" in
    *'_NET_SUPPORTING_WM_CHECK') echo '_NET_SUPPORTING_WM_CHECK(WINDOW): window id # 0x1234' ;;
    *'_NET_CURRENT_DESKTOP') echo '_NET_CURRENT_DESKTOP(CARDINAL) = 0' ;;
    *'_NET_WORKAREA') echo '_NET_WORKAREA(CARDINAL) = malformed' ;;
    *) exit 2 ;;
esac
```

For each control, prepend only its directory to `PATH` through the recorded command's `env PATH=...` argument, use optional mode and isolated Xvfb, and select the named maximized-window method with the same Gradle flags. Both runs must fail with zero skips: the first for query failure, the second for a present-WM/malformed work area. Save each control script and SHA-256, exact argv and effective PATH in separate provisional receipts. Injected responses never count as final WM evidence; the common recorder stays unchanged.

- [ ] **Step 6: Verify the positive class receipt and cleanup, or record BLOCKED**

On the Openbox run, parse `TEST-org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould.xml`. Require the target method present once, with no skip/failure/error; require every other method no failure/error and no newly introduced skip. Preserve the complete class result, including normal-window and other F11 methods. Require `wm-identity.json`, all property logs, AWT/EWMH rectangles and <=2 px edge agreement, Openbox termination/wait proof, and Xvfb/test-worker reaping. If preflight fails, preserve the marker and BLOCKED without inventing a positive receipt.

Record `task-2/handoff.json` with mode/negative-control statuses, runner SHA, method counts and readiness/stability values. Seal each completed receipt. Review the diff to prove no other method's stability contract or headless guard changed.

- [ ] **Step 7: Commit**

```bash
git diff --exit-code 92f31fce842f8e5bb1376c13e4135cdd138907db -- '*/src/main/**'
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java
git diff --cached --check
git diff --cached --stat
git commit -m "graph-initial-spacing: prove maximized window manager capability"
```

## Task 3: Pin the measured eleven-conflict golden and four-sample provenance

**Implementer tier:** Advanced

**Lane:** spacing

**Sequence:** 3

**Depends on:** Task 2

**Files:**

- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GraphWorkspacePerformanceDiagnostic.java:1-110,584-615` — needed imports, only `TWO_PINNED_MAPS_EXPECTED_CONFLICTS`, and the exact two-pinned conflict guardrails.
- Create: `/home/henry/.local/state/pi/project-manager/runs/0233ff88d3cc54bbd61550c61fa917d6e2a5717f8a03be4e9aa63dcd47dd1cfa/pm-run-20261016-125451-a5048653/scripts/epoch-3/ConflictProbe.java` — immutable external probe template, package `org.freeplane.plugin.graph.performance`.
- Create: `/home/henry/.local/state/pi/project-manager/runs/0233ff88d3cc54bbd61550c61fa917d6e2a5717f8a03be4e9aa63dcd47dd1cfa/pm-run-20261016-125451-a5048653/scripts/epoch-3/run-conflict-probe.sh` — exact Section 17.1 command-sequence driver.
- Create: `/home/henry/.local/state/pi/project-manager/runs/0233ff88d3cc54bbd61550c61fa917d6e2a5717f8a03be4e9aa63dcd47dd1cfa/pm-run-20261016-125451-a5048653/reports/epoch-3/task-3/` — baseline receipt and provisional probe/source/classpath/control receipts only.
- Read only: `/tmp/graph-initial-spacing-evidence/task-5/diagnostic/baseline-vector2-strict.log`, `GeneratedWorkspace.java`, all production classes, common external tools, and the approved investigation report. Generated only: ignored Gradle test classes and external probe classes. No fixture or other golden may change.

**Interfaces:**

- Consumes: `GeneratedWorkspace.forScenario(Scenario.TWO_PINNED_MAPS)`, `document()`, `snapshots()`, `availability()`, `projection().pins(): List<PinProjection>`, `PinProjection.source(): NodeReference`, and persisted `NodeReference` equality/hashCode.
- Consumes: `ProjectionEngine.project(ProjectionInput)`, `ProjectionInput.of(long, WorkspaceDocument, List<MapSnapshot>, Map<MapReferenceId,MapAvailability>)`, `ProjectionDiff.between(GraphProjection,GraphProjection)`, `LayoutRequest.of(WorkspaceId,GraphProjection,ProjectionDiff,List<PinProjection>)`, `LayoutWorker.submit(LayoutRequest): CompletionStage<LayoutFrame>`, and `LayoutWorker.close()`.
- Consumes: `LayoutFrame.conflicts(): List<BoundaryConflict>`; `BoundaryConflict.kind()`, `reason()`, `blockingPins()`, `firstHull()`, `secondHull()`, `pairKey()`; `CanonicalKeys.hull(EnclosureHullKey): String`.
- Produces: exact constant 11 and semantic validation of all 11 reasons, 2/9 kind split and every unordered persisted pin set. No order-dependent pin lookup remains in this branch.
- Consumes: `GraphWorkspacePerformanceDiagnostic(Path, boolean, NanoClock)`, its existing public nested `RelativeNanoClock`, and private `validatePinConflicts(GeneratedWorkspace,LayoutFrame)` through reflection; the probe cleans up the diagnostic's existing `boundedOperations` executor without running the long diagnostic vector.
- Produces: `org.freeplane.plugin.graph.performance.ConflictProbe OUTPUT_JSONL -> 0/1`, four fresh-worker first submissions of the exact fixture, each with a 5-second await, full conflict details and diagnostic guard verification; `bash run-conflict-probe.sh [--allow-dirty] -> sequence exit code`, consuming exported `EVIDENCE_ROOT` plus the staged `two-pinned-conflicts/ConflictProbe.java` and `classpath.init.gradle`. It writes per-command receipts beneath that directory. An enclosing common recorder writes the aggregate `run.json`. Task 5 copies the immutable template into its final fresh probe directory and repeats the exact Section 17.1 sequence.
- Produces: baseline-20 provenance receipt and provisional candidate-11 receipt; neither earlier epoch-2 samples nor this intermediate task tree is final acceptance evidence.

**Validation commands:**

```bash
export EVIDENCE_ROOT="$PM_RUN_ROOT/reports/epoch-3/task-3/probe-green"
JAVA_TOOL_OPTIONS=-Djava.awt.headless=true gradle :freeplane_plugin_graph:testClasses :freeplane_plugin_graph:printSpacingTestClasspath -I "$EVIDENCE_ROOT/two-pinned-conflicts/classpath.init.gradle" --console=plain > "$EVIDENCE_ROOT/two-pinned-conflicts/classpath.log" 2>&1
sed -n 's/^SPACING_TEST_CP=//p' "$EVIDENCE_ROOT/two-pinned-conflicts/classpath.log" > "$EVIDENCE_ROOT/two-pinned-conflicts/classpath.txt"
mkdir -p "$EVIDENCE_ROOT/two-pinned-conflicts/classes"
javac --release 8 -encoding UTF-8 -cp "$(cat "$EVIDENCE_ROOT/two-pinned-conflicts/classpath.txt")" -d "$EVIDENCE_ROOT/two-pinned-conflicts/classes" "$EVIDENCE_ROOT/two-pinned-conflicts/ConflictProbe.java"
JAVA_TOOL_OPTIONS=-Djava.awt.headless=true "$JAVA_HOME/bin/java" -Duser.language=en -Duser.country=US -cp "$EVIDENCE_ROOT/two-pinned-conflicts/classes:$(cat "$EVIDENCE_ROOT/two-pinned-conflicts/classpath.txt")" org.freeplane.plugin.graph.performance.ConflictProbe "$EVIDENCE_ROOT/two-pinned-conflicts/samples.jsonl" > "$EVIDENCE_ROOT/two-pinned-conflicts/probe.log" 2>&1
git diff --check
```

These commands are the specification's exact Section 17.1 sequence; require exit 0 for each green step and exactly one non-empty classpath line before compiling. For evidence, run each command through the common recorder in distinct `build-command/`, `compile-command/`, `probe-command/` subdirectories, retaining the specified `classpath.log`/`probe.log` as well. Record the extraction/mkdir argv and outcomes. The aggregate `two-pinned-conflicts/run.json` must bind the entire sequence to the same before/after candidate identity.

- [ ] **Step 1: Bind the baseline twenty to the immutable historical log**

Use this exact baseline receipt construction in the fresh `task-3/baseline/` directory; hash/seal its output. A missing/changed log is BLOCKED and may not be replaced by a new baseline run.

```python
import hashlib, json, os, subprocess
from pathlib import Path
out = Path(os.environ['PM_RUN_ROOT']) / 'reports/epoch-3/task-3/baseline'
out.mkdir(parents=True, exist_ok=False)
log = Path('/tmp/graph-initial-spacing-evidence/task-5/diagnostic/baseline-vector2-strict.log')
raw = log.read_bytes()
assert hashlib.sha256(raw).hexdigest() == '67390c50c91aec57d8ff913b6f1c549453eab83555834d53978bf66b6a52c104'
commit = '0734fb23db6873f4ec64c3e3e29fc8c3e17edc7a'
tree = subprocess.check_output(['git', 'rev-parse', commit + '^{tree}'], text=True).strip()
assert tree == '0bc8e57b5d26c2d79252a4ea4735cbf52fdbbf3c'
lines = [line for line in raw.decode().splitlines()
         if 'Two-pinned-maps conflict count changed: expected 1 but measured 20' in line]
assert lines, 'Exact historical 20-count failure absent'
(out / 'baseline-receipt.json').write_text(json.dumps({'sourceCommit': commit,
    'sourceTree': tree, 'strictLog': str(log), 'strictLogSha256': hashlib.sha256(raw).hexdigest(),
    'count': 20, 'failureLines': lines, 'hasPerConflictRecords': False}, indent=2) + '\n')
receipt = out / 'baseline-receipt.json'
(out / 'baseline-receipt.sha256').write_text(hashlib.sha256(receipt.read_bytes()).hexdigest() + '  baseline-receipt.json\n')
```

- [ ] **Step 2: Author the complete immutable probe source**

Save this complete external source as `$PM_RUN_ROOT/scripts/epoch-3/ConflictProbe.java`. Its reflection call exercises the actual private diagnostic validator without running the long strict performance vector. Step 3 stages the driver and preserves stale-golden red before Step 4 changes the diagnostic. On green it writes four independently checked full sample records. Each worker is newly constructed and submitted once; no settling or second submission is substituted for a first submission.

```java
package org.freeplane.plugin.graph.performance;

import java.io.BufferedWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.freeplane.plugin.graph.layout.BoundaryConflict;
import org.freeplane.plugin.graph.layout.LayoutCalibration;
import org.freeplane.plugin.graph.layout.LayoutFrame;
import org.freeplane.plugin.graph.layout.LayoutRequest;
import org.freeplane.plugin.graph.layout.LayoutWorker;
import org.freeplane.plugin.graph.projection.CanonicalKeys;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectionDiff;
import org.freeplane.plugin.graph.projection.ProjectionEngine;
import org.freeplane.plugin.graph.projection.input.ProjectionInput;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;

public final class ConflictProbe {
    private static String quote(final String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }
    private static String pin(final PinProjection value) {
        return "{\"map\":" + quote(value.source().mapReferenceId().value().toString())
            + ",\"node\":" + quote(value.source().nodeId().value())
            + ",\"active\":" + value.active() + ",\"x\":" + value.x() + ",\"y\":" + value.y() + "}";
    }
    private static String pins(final List<PinProjection> values) {
        final List<String> rows = new ArrayList<String>();
        for (final PinProjection value : values) { rows.add(pin(value)); }
        return "[" + String.join(",", rows) + "]";
    }
    private static void require(final boolean condition, final String message) {
        if (!condition) { throw new AssertionError(message); }
    }
    private static void closeDiagnostic(final GraphWorkspacePerformanceDiagnostic diagnostic) throws Exception {
        final Field field = GraphWorkspacePerformanceDiagnostic.class.getDeclaredField("boundedOperations");
        field.setAccessible(true);
        final ExecutorService operations = (ExecutorService) field.get(diagnostic);
        operations.shutdownNow();
        require(operations.awaitTermination(5, TimeUnit.SECONDS), "diagnostic executor cleanup");
    }
    public static void main(final String[] args) throws Exception {
        final Path target = Paths.get(args[0]);
        final GeneratedWorkspace generated = GeneratedWorkspace.forScenario(GeneratedWorkspace.Scenario.TWO_PINNED_MAPS);
        final Set<NodeReference> expected = new HashSet<NodeReference>(Arrays.asList(
            NodeReference.of(MapReferenceId.of("00000000-0000-0000-0000-000000000001"), PersistedNodeId.of("m00-n0001")),
            NodeReference.of(MapReferenceId.of("00000000-0000-0000-0000-000000000002"), PersistedNodeId.of("m01-n0001"))));
        final Set<NodeReference> persisted = new HashSet<NodeReference>();
        for (final PinProjection pin : generated.projection().pins()) {
            require(pin.active() && pin.x() == 0.0 && pin.y() == 0.0, "fixture pin premise");
            persisted.add(pin.source());
        }
        require(persisted.equals(expected), "fixture persisted identities");
        final GraphWorkspacePerformanceDiagnostic diagnostic = new GraphWorkspacePerformanceDiagnostic(
            target.getParent(), true, new GraphWorkspacePerformanceDiagnostic.RelativeNanoClock());
        final Method validate = GraphWorkspacePerformanceDiagnostic.class.getDeclaredMethod(
            "validatePinConflicts", GeneratedWorkspace.class, LayoutFrame.class);
        validate.setAccessible(true);
        final ProjectionEngine engine = new ProjectionEngine();
        try (BufferedWriter output = Files.newBufferedWriter(target, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            for (int sample = 1; sample <= 4; sample++) {
                final GraphProjection previous = engine.project(ProjectionInput.of(0L, generated.document(),
                    generated.snapshots(), generated.availability()));
                final GraphProjection current = engine.project(ProjectionInput.of(1L, generated.document(),
                    generated.snapshots(), generated.availability()));
                final LayoutRequest request = LayoutRequest.of(generated.document().id(), current,
                    ProjectionDiff.between(previous, current), current.pins());
                final LayoutWorker worker = new LayoutWorker(LayoutCalibration.spikeDefaults());
                try {
                    final LayoutFrame frame = worker.submit(request).toCompletableFuture().get(5, TimeUnit.SECONDS);
                    require(!frame.failed() && frame.verified(), "usable first-submission frame");
                    require(frame.conflicts().size() == 11, "exact 11 conflicts");
                    int crossing = 0, containment = 0;
                    final List<String> conflicts = new ArrayList<String>();
                    for (final BoundaryConflict conflict : frame.conflicts()) {
                        require(conflict.reason() == BoundaryConflict.Reason.IMMOVABLE_SIDES, "exact reason");
                        final Set<NodeReference> actual = new HashSet<NodeReference>();
                        for (final PinProjection pin : conflict.blockingPins()) { actual.add(pin.source()); }
                        require(actual.equals(persisted) && conflict.blockingPins().size() == 2, "exact unordered pin set");
                        if (conflict.kind() == BoundaryConflict.Kind.SIBLING_CROSSING) { crossing++; }
                        if (conflict.kind() == BoundaryConflict.Kind.SIBLING_CONTAINMENT) { containment++; }
                        conflicts.add("{\"firstHull\":" + quote(CanonicalKeys.hull(conflict.firstHull()))
                            + ",\"secondHull\":" + quote(CanonicalKeys.hull(conflict.secondHull()))
                            + ",\"firstMap\":" + quote(conflict.firstMap().value().toString())
                            + ",\"secondMap\":" + quote(conflict.secondMap().value().toString())
                            + ",\"pairKey\":" + quote(conflict.pairKey())
                            + ",\"kind\":" + quote(conflict.kind().name())
                            + ",\"reason\":" + quote(conflict.reason().name())
                            + ",\"blockingPins\":" + pins(conflict.blockingPins()) + "}");
                    }
                    require(crossing == 2 && containment == 9, "exact 2/9 kind split");
                    output.write("{\"sample\":" + sample + ",\"scenario\":\"TWO_PINNED_MAPS\",\"requestGeneration\":1"
                        + ",\"worker\":" + sample + ",\"submission\":1,\"stepIndex\":" + frame.stepIndex()
                        + ",\"failed\":" + frame.failed() + ",\"verified\":" + frame.verified()
                        + ",\"idle\":" + frame.idle().idle() + ",\"fixturePins\":" + pins(current.pins())
                        + ",\"conflicts\":[" + String.join(",", conflicts) + "]}\n");
                    output.flush();
                    try { validate.invoke(diagnostic, generated, frame); }
                    catch (InvocationTargetException error) { throw new AssertionError("diagnostic validation failed", error.getCause()); }
                    System.out.println("sample=" + sample
                        + " firstSubmission=1 conflicts=11 crossing=2 containment=9 diagnosticValidation=PASS");
                }
                finally { worker.close(); }
            }
        }
        finally { closeDiagnostic(diagnostic); }
    }
}
```

The constructor, nested clock, validator signature and executor field above exist in the current source. Keep their signatures unchanged; reflection is confined to the disposable external probe. Preserve probe JVM termination and executor shutdown; an unclosed worker is incomplete evidence.

- [ ] **Step 3: Stage the exact driver and preserve the stale-golden red**

Create the fresh directory, copy the immutable probe into `ConflictProbe.java`, and write this exact `classpath.init.gradle`:

```groovy
allprojects { p ->
  afterEvaluate {
    if (p.path == ':freeplane_plugin_graph') {
      tasks.register('printSpacingTestClasspath') {
        doLast { println('SPACING_TEST_CP=' + p.sourceSets.test.runtimeClasspath.asPath) }
      }
    }
  }
}
```

Create `$PM_RUN_ROOT/scripts/epoch-3/run-conflict-probe.sh` with this concrete driver. It preserves the exact Section 17.1 commands and their redirects while wrapping each in its own command receipt. Its outer invocation supplies the aggregate before/after identity; only provisional iterations pass `--allow-dirty` to both recorders.

```bash
#!/usr/bin/env bash
set -euo pipefail
mode=()
if [[ ${1:-} == --allow-dirty ]]; then mode=(--allow-dirty); shift; fi
[[ $# == 0 ]]
base="$EVIDENCE_ROOT/two-pinned-conflicts"
[[ -f "$base/ConflictProbe.java" && -f "$base/classpath.init.gradle" ]]
record() {
    local name=$1
    shift
    python3 "$PM_RUN_ROOT/scripts/epoch-3/record-run.py" "${mode[@]}" "$base/$name" -- "$@"
}
record build-command bash -c 'JAVA_TOOL_OPTIONS=-Djava.awt.headless=true gradle :freeplane_plugin_graph:testClasses :freeplane_plugin_graph:printSpacingTestClasspath -I "$EVIDENCE_ROOT/two-pinned-conflicts/classpath.init.gradle" --console=plain > "$EVIDENCE_ROOT/two-pinned-conflicts/classpath.log" 2>&1'
record extraction-command bash -c 'sed -n '\''s/^SPACING_TEST_CP=//p'\'' "$EVIDENCE_ROOT/two-pinned-conflicts/classpath.log" > "$EVIDENCE_ROOT/two-pinned-conflicts/classpath.txt"'
record classpath-check-command python3 -c 'import os,sys; from pathlib import Path; rows=Path(sys.argv[1]).read_text().splitlines(); assert len(rows)==1 and rows[0]; assert all(Path(p).exists() for p in rows[0].split(os.pathsep))' "$base/classpath.txt"
record mkdir-command mkdir -p "$base/classes"
record compile-command javac --release 8 -encoding UTF-8 -cp "$(cat "$EVIDENCE_ROOT/two-pinned-conflicts/classpath.txt")" -d "$EVIDENCE_ROOT/two-pinned-conflicts/classes" "$EVIDENCE_ROOT/two-pinned-conflicts/ConflictProbe.java"
record probe-command bash -c 'JAVA_TOOL_OPTIONS=-Djava.awt.headless=true "$JAVA_HOME/bin/java" -Duser.language=en -Duser.country=US -cp "$EVIDENCE_ROOT/two-pinned-conflicts/classes:$(cat "$EVIDENCE_ROOT/two-pinned-conflicts/classpath.txt")" org.freeplane.plugin.graph.performance.ConflictProbe "$EVIDENCE_ROOT/two-pinned-conflicts/samples.jsonl" > "$EVIDENCE_ROOT/two-pinned-conflicts/probe.log" 2>&1'
record jar-hash-command python3 -c 'import hashlib,json,os,sys; from pathlib import Path; base=Path(sys.argv[1]); paths=base.joinpath("classpath.txt").read_text().strip().split(os.pathsep); hashes={p:hashlib.sha256(Path(p).read_bytes()).hexdigest() for p in paths if Path(p).is_file() and p.endswith(".jar")}; base.joinpath("classpath-jars.json").write_text(json.dumps(hashes,indent=2)+"\n")' "$base"
```

Hash the driver and run `bash -n` on it before use. First stage the source and exact init script under a fresh `EVIDENCE_ROOT=$PM_RUN_ROOT/reports/epoch-3/task-3/probe-red`, then execute:

```bash
python3 "$PM_RUN_ROOT/scripts/epoch-3/record-run.py" --allow-dirty "$EVIDENCE_ROOT/two-pinned-conflicts" -- bash "$PM_RUN_ROOT/scripts/epoch-3/run-conflict-probe.sh" --allow-dirty
```

Require the first submission's reflection call to fail specifically with the unchanged diagnostic message `expected 1 but measured 11`. Preserve its first-submission records/logs, classpath, compiled classes and all per-command outcomes; seal the red directory. A compiler/setup failure is not stale-golden red. Do not run the long strict diagnostic.

- [ ] **Step 4: Replace only the stale golden and prove the four-sample green**

Change `TWO_PINNED_MAPS_EXPECTED_CONFLICTS = 1` to `= 11`. Keep the existing `ONE_PINNED_MAP` branch and exact count failure. Replace the order-dependent loop after that count check with the following; use already present imports where possible and add only `NodeReference`/`PersistedNodeId` imports if absent:

```java
final Set<NodeReference> expectedPins = new HashSet<NodeReference>();
for (final PinProjection pin : generated.projection().pins()) {
    expectedPins.add(pin.source());
}
final Set<NodeReference> declaredPins = new HashSet<NodeReference>(Arrays.asList(
    NodeReference.of(org.freeplane.plugin.graph.workspace.model.MapReferenceId.of(
        "00000000-0000-0000-0000-000000000001"), PersistedNodeId.of("m00-n0001")),
    NodeReference.of(org.freeplane.plugin.graph.workspace.model.MapReferenceId.of(
        "00000000-0000-0000-0000-000000000002"), PersistedNodeId.of("m01-n0001"))));
if (!expectedPins.equals(declaredPins)) {
    throw new DiagnosticFailure(PerformanceMeasurements.Stage.FULL_WORKER,
        "Two-pinned-maps fixture persisted identities changed");
}
int crossing = 0;
int containment = 0;
for (final BoundaryConflict conflict : frame.conflicts()) {
    final Set<NodeReference> actualPins = new HashSet<NodeReference>();
    for (final PinProjection pin : conflict.blockingPins()) {
        actualPins.add(pin.source());
    }
    if (conflict.reason() != BoundaryConflict.Reason.IMMOVABLE_SIDES
            || conflict.blockingPins().size() != 2 || !actualPins.equals(expectedPins)) {
        throw new DiagnosticFailure(PerformanceMeasurements.Stage.FULL_WORKER,
            "Two-pinned-maps conflict reason or unordered persisted pin identities changed");
    }
    if (conflict.kind() == BoundaryConflict.Kind.SIBLING_CROSSING) {
        crossing++;
    }
    else if (conflict.kind() == BoundaryConflict.Kind.SIBLING_CONTAINMENT) {
        containment++;
    }
}
if (crossing != 2 || containment != 9) {
    throw new DiagnosticFailure(PerformanceMeasurements.Stage.FULL_WORKER,
        "Two-pinned-maps conflict kind split changed: crossing=" + crossing + ", containment=" + containment);
}
```

Every conflict must satisfy the set equality; remove the same-map `continue` bypass and order-dependent pin lookups. The exact 11 total plus exact 2/9 split excludes any extra kind. No other diagnostic branch or numeric value changes. Stage the unchanged probe/init inputs under fresh `EVIDENCE_ROOT=$PM_RUN_ROOT/reports/epoch-3/task-3/probe-green` and run the same recorded driver invocation once.

Require one nonempty classpath line, successful Java-8 compilation, four JSONL samples numbered 1–4, each worker/submission identity fresh/first, exact request generation, 11 complete records per sample, all `IMMOVABLE_SIDES`, exactly 2 crossing/9 containment, and the exact unordered persisted identities. Hash source, fixture source, init script, classpath, classpath JARs, compiled probe classes, logs, JSONL and aggregate receipt. All four independently satisfy the contract; an aggregate count is insufficient.

- [ ] **Step 5: Falsify the diagnostic guardrails in external test-only copies**

Compile disposable copies of this test diagnostic into `task-3/guard-controls/` and put their classes first on the probe JVM classpath. Change one mechanism per copy: constant 11 back to 1; expected reason to `ROUND_LIMIT`; expected crossing count from 2 to 3; and one declared persisted map/node identity. Each must make the reflection validation fail with the corresponding diagnostic message. This mutates only copied test code under the external allowlist, never production or active compiled classes. Record each argv, mutant source/class hashes, failure output and restored green result. Do not run the full strict vector for these controls.

- [ ] **Step 6: Explain the twenty-to-eleven derivation and hand off**

Record that the two fixture roots persist at the same `(0.0,0.0)` center, and the wider delivered content rings separate some remaining hull pairs. Baseline count is 20; candidate count is 11; both are the consequence of deliberately immovable co-located roots. Do not describe 1 as the baseline or treat 11 as a tunable performance threshold. Link the baseline receipt/hash, four candidate samples, kind/reason/identity checks, provisional commit/tree and required final-tree replay in `task-3/handoff.json`. Seal all receipts. Check the diff and production freeze.

- [ ] **Step 7: Commit**

```bash
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GraphWorkspacePerformanceDiagnostic.java
git diff --cached --check
git diff --cached --stat
git commit -m "graph-initial-spacing: pin measured two-map conflict structure"
```

## Task 4: Establish the lifecycle teardown barrier and kill the disposable closed-guard mutant

**Implementer tier:** Advanced

**Lane:** spacing

**Sequence:** 4

**Depends on:** Task 3

**Files:**

- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceLifecycleShould.java:234-394` — only `doesNotDeliverCallbacksAfterClose` and its necessary failure-cleanup scope.
- Create: `/home/henry/.local/state/pi/project-manager/runs/0233ff88d3cc54bbd61550c61fa917d6e2a5717f8a03be4e9aa63dcd47dd1cfa/pm-run-20261016-125451-a5048653/scripts/epoch-3/run-lifecycle-mutant.py`.
- Create: `/home/henry/.local/state/pi/project-manager/runs/0233ff88d3cc54bbd61550c61fa917d6e2a5717f8a03be4e9aa63dcd47dd1cfa/pm-run-20261016-125451-a5048653/reports/epoch-3/task-4/` — investigation, focused/fresh class/regression runs, mutation receipts and handoff only.
- Create disposable probe artifacts only: `/tmp/graph-initial-spacing-evidence/epoch-3/task-4-mutant/` — a copied `GraphUpdateCoordinator.java`, mutant/runner classes and logs. The copy is not a repository production write target; do not alter active source or active class output.
- Read only: `GraphWorkspaceColdReloadShould.java` (contains package-private `GraphWorkspaceIntegrationSupport`), `GraphUpdateCoordinator.java`, the linked close-boundary design, and previous task runners. Generated only: ignored Gradle test outputs. No support-helper, fixture, timeout, build or production edit is authorized.

**Interfaces:**

- Consumes: `GraphWorkspaceIntegrationSupport.baseline(FreeplaneScope,Path): ResourceBaseline`, `ResourceBaseline.mapLifecycleListeners`, `FreeplaneScope.mapLifecycleListenerCount(): int`, `awaitCondition(BooleanSupplier,long,String): void`, and the existing `awaitPostCloseCompletion(...)`.
- Consumes: existing `callbackHandle.close()`, handle command rejection, `binding.addCanvasStateListener(...).close()`, blocked first projection listener, callback counters, and bounded `closing.join(10000L)`.
- Produces: order `baseline -> observed listener increase -> off-EDT close -> handle rejection -> callback-registration rejection (governing logical-close signal) -> release blocked listener -> listener baseline restored (post-release teardown completion) -> bounded close join`. Both signals are required; the listener baseline is a post-release assertion, never a pre-release gate.
- Produces: `python3 run-lifecycle-mutant.py CANDIDATE_ROOT CLASSPATH_FILE RECEIPT_DIR -> 0` only when the real method passes with production classes and fails with the one copied per-listener closed check removed. The receipt records green/mutant exit codes, assertion text, source/class hashes and unchanged active source/tree.
- Produces: a reviewed clean lane head containing all four authorized test changes, ready for Task 5's common-tree evidence collection.

**Validation commands:**

```bash
JAVA_TOOL_OPTIONS=-Djava.awt.headless=true gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.integration.GraphWorkspaceLifecycleShould' --console=plain -PTestLoggingFull --rerun-tasks
JAVA_TOOL_OPTIONS=-Djava.awt.headless=true gradle :freeplane_plugin_graph:clean :freeplane_plugin_graph:check :freeplane_plugin_graph:test :freeplane_plugin_graph:build --console=plain -PTestLoggingFull
JAVA_TOOL_OPTIONS=-Djava.awt.headless=true gradle test --console=plain -PTestLoggingFull
python3 "$PM_RUN_ROOT/scripts/epoch-3/run-lifecycle-mutant.py" "$CANDIDATE_ROOT" "$PM_RUN_ROOT/reports/epoch-3/task-4/classpath.txt" "$PM_RUN_ROOT/reports/epoch-3/task-4/mutant"
git diff --check
git diff --exit-code 92f31fce842f8e5bb1376c13e4135cdd138907db -- '*/src/main/**'
```

Capture each command with the common recorder, `--allow-dirty` only before the implementation commit. These are lifecycle compatibility runs, not substitutes for the four exact final gates. The long strict diagnostic remains reserved for Task 5.

- [ ] **Step 1: Localize the inherited race and preserve the baseline observations**

Read the existing focused-pass/full-suite-failure receipts and the close-boundary design. Trace the active production close ordering read-only. The two signals are distinct and neither substitutes for the other:

- **Handle-closing rejection and callback-registration rejection** are the logical-close signals. The handle rejects at session shutdown start, and the coordinator sets `closed = true` and clears its listener lists synchronously in its `synchronized` close block before any teardown work; `addCanvasStateListener` checks that flag under the same monitor, so a rejected registration proves the coordinator has entered its logical closed state. Both are reachable while the EDT is still blocked, which is why registration rejection must be observed **before** the blocked listener is released.
- **Return to the observed map-listener baseline** is a *teardown-completion* signal, and it can only be observed **after** release. `MapLeaseManager.close()` detaches that listener inside `runOnEdtAndWait`, a blocking EDT round trip on the very thread the blocked listener occupies, so a pre-release baseline wait is circular and can only pass on the bounded helper's post-deadline re-check.

Record resource baselines and source hashes. Do not manufacture a flaky failing rerun, widen latches, or conclude a production defect solely from suite order.

- [ ] **Step 2: Add the bounded map-listener teardown wait**

Keep the existing `ResourceBaseline` capture and the non-vacuous above-baseline assertion after projection. Replace only the sequence beginning with the existing `viewport(13,14,1.6)` command through its close-thread `finally` with this exact code. The success assertions immediately after it remain untouched:

```java
final AtomicReference<Throwable> closeFailure = new AtomicReference<Throwable>();
final Thread closing = new Thread(() -> {
    try {
        callbackHandle.close();
    }
    catch (Throwable failure) {
        closeFailure.set(failure);
    }
}, "graph-workspace-stale-callback-close-probe");
boolean closingStarted = false;
try {
    assertThat(callbackHandle.execute(GraphCommands.viewport(Viewport.of(13, 14, 1.6,
        Collections.emptyList()))).status()).isEqualTo(GraphCommandResult.Status.APPLIED);
    assertThat(firstProjectionEntered.await(5, TimeUnit.SECONDS)).isTrue();
    closing.start();
    closingStarted = true;
    GraphWorkspaceIntegrationSupport.awaitCondition(() -> {
        try {
            callbackHandle.execute(GraphCommands.viewport(Viewport.of(15, 16, 1.7,
                Collections.emptyList())));
            return false;
        }
        catch (IllegalStateException expected) {
            return true;
        }
    }, 5000L, "close did not cross the handle closing boundary");
    GraphWorkspaceIntegrationSupport.awaitCondition(() -> {
        try {
            binding.addCanvasStateListener(state -> {
            }).close();
            return false;
        }
        catch (IllegalStateException expected) {
            return true;
        }
    }, 5000L, "close did not reach the graph update coordinator callback boundary");
    releaseFirstProjection.countDown();
    GraphWorkspaceIntegrationSupport.awaitCondition(
        () -> freeplane.mapLifecycleListenerCount() == baseline.mapLifecycleListeners,
        5000L, "close did not detach workspace map lifecycle listeners");
}
finally {
    releaseFirstProjection.countDown();
    if (closingStarted) {
        closing.join(10000L);
    }
}
```

The callback-registration rejection **governs** the logical-close precondition for release, and the listener-count return is a **post-release** completed-teardown assertion. This ordering is mandatory: the reverse order is circular, because `MapLeaseManager.close()` detaches that listener via `runOnEdtAndWait` on the very EDT the blocked listener occupies, so a pre-release listener-baseline wait can only be satisfied by the listener's own 5-second latch expiring. The `finally` still counts down the latch (idempotent, and now usually redundant) and joins the close thread, so failure cleanup is unchanged. Preserve the original 5-second latch and 10-second join values, thread-terminated/`closeFailure` assertions, callback counters, second-listener zero assertion, source-map mutation, baseline checks and closed-handle assertions. Both waits keep the existing `5000L` budget. Add no sleeps, retries or assumptions.

- [ ] **Step 3: Execute fresh focused and class/suite compatibility runs**

Run the focused class with fresh execution three times in separately named receipts. These are three independent stability observations, not retry-until-green; preserve and investigate every failure. Then run the graph `clean/check/test/build` command and full repository `test` command specified above. Require zero failures/errors and honest attribution of the expected headless skips. A production failure stops this task for a user decision rather than expanding scope.

- [ ] **Step 4: Create the copied-class mutation probe**

Obtain the test runtime classpath using this exact read-only init task, saving its source under the task-4 external allowlist:

```bash
python3 - "$PM_RUN_ROOT/reports/epoch-3/task-4/classpath.init.gradle" <<'PY'
import sys
from pathlib import Path
Path(sys.argv[1]).write_text('''allprojects { p ->
  afterEvaluate {
    if (p.path == ':freeplane_plugin_graph') {
      tasks.register('printSpacingTestClasspath') {
        doLast { println('SPACING_TEST_CP=' + p.sourceSets.test.runtimeClasspath.asPath) }
      }
    }
  }
}
''')
PY
JAVA_TOOL_OPTIONS=-Djava.awt.headless=true gradle :freeplane_plugin_graph:testClasses :freeplane_plugin_graph:printSpacingTestClasspath -I "$PM_RUN_ROOT/reports/epoch-3/task-4/classpath.init.gradle" --console=plain > "$PM_RUN_ROOT/reports/epoch-3/task-4/classpath.log" 2>&1
sed -n 's/^SPACING_TEST_CP=//p' "$PM_RUN_ROOT/reports/epoch-3/task-4/classpath.log" > "$PM_RUN_ROOT/reports/epoch-3/task-4/classpath.txt"
```

Require exactly one nonempty line and retain the init/build log and JAR hashes. Save the following external probe as `run-lifecycle-mutant.py`. It changes only the second guard in `publishProjection`, immediately before the individual listener invocation; the initial batch-entry guard and generation/projection checks remain intact. The compiled overlay never enters active `build/classes`. Wrap its public invocation with the common recorder using separate `task-4/mutant-command/` for the enclosing receipt and `task-4/mutant/` for the script's newly created output directory.

```python
import datetime, hashlib, json, os, shutil, subprocess, sys
from pathlib import Path
root, cpfile, out = (Path(x).resolve() for x in sys.argv[1:])
out.mkdir(parents=True, exist_ok=False)
tmp = Path('/tmp/graph-initial-spacing-evidence/epoch-3/task-4-mutant')
tmp.mkdir(parents=True, exist_ok=False)
relative = 'freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/GraphUpdateCoordinator.java'
active = root / relative
original = active.read_bytes()
head = subprocess.check_output(['git', '-C', str(root), 'rev-parse', 'HEAD'], text=True).strip()
cp = cpfile.read_text().splitlines()
assert len(cp) == 1 and cp[0]
java = Path(os.environ['JAVA_HOME']) / 'bin/java'
javac = Path(os.environ['JAVA_HOME']) / 'bin/javac'
runner = tmp / 'MethodProbe.java'
runner.write_text('''import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
public final class MethodProbe {
  public static void main(String[] args) throws Exception {
    Result result = new JUnitCore().run(Request.method(Class.forName(args[0]), args[1]));
    for (Failure failure : result.getFailures()) { System.out.println(failure.getTrace()); }
    System.out.println("run=" + result.getRunCount() + " ignored=" + result.getIgnoreCount()
      + " failures=" + result.getFailureCount());
    System.exit(result.wasSuccessful() && result.getRunCount() == 1 ? 0 : 1);
  }
}
''')
def stamp():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()
commands = []
def run(name, argv):
    args = list(map(str, argv))
    effective_env = dict(os.environ, JAVA_TOOL_OPTIONS='-Djava.awt.headless=true')
    command = {'name': name, 'argv': args, 'cwd': str(root), 'start': stamp(),
        'JAVA_HOME': effective_env['JAVA_HOME'], 'JAVA_TOOL_OPTIONS': effective_env['JAVA_TOOL_OPTIONS']}
    with (out / (name + '.log')).open('x') as log:
        log.write(json.dumps(command) + '\n')
        log.flush()
        result = subprocess.run(args, cwd=root, stdout=log, stderr=subprocess.STDOUT, env=effective_env)
        command.update(end=stamp(), exitStatus=result.returncode)
        log.write('\n' + json.dumps(command) + '\n')
    commands.append(command)
    return result.returncode
receipt = {'activeSourceSha256': hashlib.sha256(original).hexdigest(), 'head': head}
try:
    runner_classes, mutant_classes = tmp / 'runner-classes', tmp / 'mutant-classes'
    runner_classes.mkdir(); mutant_classes.mkdir()
    assert run('compile-runner', [javac, '--release', '8', '-encoding', 'UTF-8', '-cp', cp[0],
        '-d', runner_classes, runner]) == 0
    selected = ['MethodProbe', 'org.freeplane.plugin.graph.integration.GraphWorkspaceLifecycleShould',
                'doesNotDeliverCallbacksAfterClose']
    receipt['greenExit'] = run('green', [java, '-cp', str(runner_classes) + os.pathsep + cp[0], *selected])
    assert receipt['greenExit'] == 0
    text = original.decode('UTF-8')
    anchor = '''for (GraphProjectionListener listener : listeners) {
                        synchronized (monitor) {
                            if (closed || generation != acceptedGeneration || projection != next) {'''
    assert text.count(anchor) == 1
    changed = text.replace(anchor, anchor.replace('closed || ', ''), 1)
    mutant = tmp / 'GraphUpdateCoordinator.java'
    mutant.write_text(changed)
    receipt['mutantSourceSha256'] = hashlib.sha256(mutant.read_bytes()).hexdigest()
    assert run('compile-mutant', [javac, '--release', '8', '-encoding', 'UTF-8', '-cp', cp[0],
        '-d', mutant_classes, mutant]) == 0
    receipt['mutantExit'] = run('mutant', [java, '-cp', str(mutant_classes) + os.pathsep
        + str(runner_classes) + os.pathsep + cp[0], *selected])
    assert receipt['mutantExit'] != 0
    failure = (out / 'mutant.log').read_text()
    assert 'stale projection listener after close' in failure, failure
    assert 'run=1 ignored=0 failures=1' in failure, failure
    receipt['killed'] = True
finally:
    assert active.read_bytes() == original, 'active production source changed'
    assert subprocess.check_output(['git', '-C', str(root), 'rev-parse', 'HEAD'], text=True).strip() == head
    shutil.copytree(tmp, out / 'probe-artifacts')
    receipt['artifactHashes'] = {str(f.relative_to(out)): hashlib.sha256(f.read_bytes()).hexdigest()
        for f in sorted((out / 'probe-artifacts').rglob('*')) if f.is_file()}
    receipt['commands'] = commands
    receipt['activeSourceUnchanged'] = active.read_bytes() == original
    (out / 'mutation.json').write_text(json.dumps(receipt, indent=2) + '\n')
    shutil.rmtree(tmp)
```

Record exact compile/run argv and timestamps in the enclosing command receipt and individual log headers before sealing. If the mutant survives, do not weaken or relabel the assertion; report a falsifiability failure. If a compile/runtime setup error occurs, it is not a killed mutant. Confirm the temporary directory is gone and active production hashes unchanged.

- [ ] **Step 5: Review preservation and publish the clean-head handoff**

Diff only the authorized method. Confirm the success/failure cleanup covers the blocked listener and bounded join, the above-baseline assertion prevents a vacuous wait, and no sleep/retry/assumption or timeout change was introduced. Preserve the closed-handle/source-map/callback/resource assertions. Record focused/suite receipts, mutant kill assertion and hashes, source-freeze proof and handoff head in `task-4/handoff.json`; seal the receipts. The next task remeasures all final evidence after this commit.

- [ ] **Step 6: Commit**

```bash
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceLifecycleShould.java
git diff --cached --check
git diff --cached --stat
git commit -m "graph-initial-spacing: wait for coordinator teardown in lifecycle probe"
```

## Task 5: Collect common-tree supporting evidence and all four final integration gates

**Implementer tier:** Standard

**Lane:** spacing

**Sequence:** 5

**Depends on:** Task 4

**Files:**

- Create: `/home/henry/.local/state/pi/project-manager/runs/0233ff88d3cc54bbd61550c61fa917d6e2a5717f8a03be4e9aa63dcd47dd1cfa/pm-run-20261016-125451-a5048653/reports/epoch-3/supporting-evidence/` — six named final supporting receipts, probe inputs/classes, comparator baseline archive and generated artifacts only.
- Create: `/home/henry/.local/state/pi/project-manager/runs/0233ff88d3cc54bbd61550c61fa917d6e2a5717f8a03be4e9aa63dcd47dd1cfa/pm-run-20261016-125451-a5048653/reports/epoch-3/final-gates/` — `gate-1/` through `gate-4/`, logs, XML, diagnostics and child/cleanup evidence only.
- Create: `/home/henry/.local/state/pi/project-manager/runs/0233ff88d3cc54bbd61550c61fa917d6e2a5717f8a03be4e9aa63dcd47dd1cfa/pm-run-20261016-125451-a5048653/reports/epoch-3/task-5/` — candidate pin, skip comparison, per-command receipts, evidence index and handoff only; explained fresh reruns stay under distinct attempt directories here.
- Read only: all four authorized test changes, production, existing observer/comparator, common record/seal tools, Openbox runner, `ConflictProbe.java`, baseline receipt and epoch-2 archives. Generated only: ignored Gradle compilation/test/BIN/diagnostic outputs of the exact commands below. No repository source/document/build file may be modified in this task.

**Interfaces:**

- Consumes: Task 1's `record-run.py RECEIPT_DIR -- COMMAND [ARG ...]` without `--allow-dirty`, `seal-evidence.py DIR`; Task 2's `run-openbox-window-probe.sh CANDIDATE_ROOT RECEIPT_DIR`; Task 3's immutable `ConflictProbe.java`, `bash run-conflict-probe.sh [--allow-dirty]` (use no flag for final runs, consuming exported `EVIDENCE_ROOT` plus staged source/init inputs) and baseline-20 receipt; Task 4's clean reviewed head and killed-mutant receipt.
- Consumes: unchanged comparator `python3 freeplane_plugin_graph/src/test/scripts/compare_initial_spacing.py CANDIDATE_ROOT OBSERVER_PATH OUTPUT_DIR -> 0/1`, creating a fresh output tree with `comparison.json`, `provenance.json`, archives, logs and unrounded TSV data.
- Produces: `$EPOCH3_ROOT/task-5/candidate.json` with absolute root, candidate commit/tree, source hashes and clean status; six supporting receipts and four gate receipts pinned to that same tree.
- Produces: `$EPOCH3_ROOT/task-5/evidence-index.json` containing six `supportingRuns` and four `finalGates`, each with `id`, `status`, `receiptPath`, `manifestSha256`, `candidateHead`, `candidateTree`, `exitStatus`, `cleanupComplete` and requirement IDs; missing evidence has a reason and no invented digest.
- Produces: honest four-state gate outcomes. All commands succeeding is necessary but insufficient for overall acceptance; Task 6 must still verify final lane/integration/delivery tree equality and the unresolved document-commit conflict.

**Validation commands:**

```bash
export JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"
export JAVA_TOOL_OPTIONS=-Djava.awt.headless=true
export GRADLE_OPTS=-Dorg.gradle.daemon=false
export CANDIDATE_ROOT="$(pwd -P)"
export EVIDENCE_ROOT="$PM_RUN_ROOT/reports/epoch-3/supporting-evidence"
git status --porcelain=v1 --untracked-files=all
git rev-parse HEAD 'HEAD^{tree}'
git diff --exit-code 92f31fce842f8e5bb1376c13e4135cdd138907db -- '*/src/main/**'
```

The command vector below is exact. Prefix each with the common recorder and its named receipt directory; run sequentially, not concurrently. Do not add an unqualified root launch-smoke task or replace the runtime command with forced-headless Java.

```bash
JAVA_TOOL_OPTIONS=-Djava.awt.headless=true gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.AccessibleGraphCanvasShould' --console=plain -PTestLoggingFull --rerun-tasks
env -u JAVA_TOOL_OPTIONS xvfb-run -a gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.AccessibleGraphCanvasShould' --console=plain -PTestLoggingFull --rerun-tasks
env -u JAVA_TOOL_OPTIONS FREEPLANE_GRAPH_TEST_WINDOW_MANAGER=optional xvfb-run -a gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould.f11RestoresBoundsAndExtendedStateFromMaximizedWindow' --console=plain -PTestLoggingFull --rerun-tasks
env -u JAVA_TOOL_OPTIONS PM_RUN_ROOT="$PM_RUN_ROOT" bash "$PM_RUN_ROOT/scripts/epoch-3/run-openbox-window-probe.sh" "$CANDIDATE_ROOT" "$EVIDENCE_ROOT/f11-openbox-xvfb"
python3 freeplane_plugin_graph/src/test/scripts/compare_initial_spacing.py "$CANDIDATE_ROOT" freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/InitialSpacingProbe.java "$EVIDENCE_ROOT/spacing-comparison"
gradle :freeplane_plugin_graph:check --console=plain -PTestLoggingFull
gradle :freeplane_plugin_graph:graphPerformanceDiagnostic -PgraphStrictPerformance --console=plain
env -u JAVA_TOOL_OPTIONS xvfb-run -a gradle :freeplane_plugin_graph:graphOsgiSmoke :freeplane_plugin_graph:freeplaneLaunchSmoke --console=plain
gradle :freeplane:compileJava test --console=plain -PTestLoggingFull
```

Supporting run 5 additionally uses the complete exact Section 17.1 init/classpath/javac/java sequence in Step 3 below. The comparator creates its own output directory; record its invocation outside that directory, then copy the command/process receipt into the output before sealing it.

- [ ] **Step 1: Pin the clean candidate and validate immutable inputs**

Require clean `git status`, record the canonical `CANDIDATE_ROOT`, HEAD/tree, Java/Gradle versions, protected source hashes, spec/design hashes, runner/probe hashes, observer/comparator hashes and dependency/JAR checksums into `task-5/candidate.json`. Verify all four source changes are within the test allowlist. Validate the baseline receipt against its hash-pinned historical log. Do not proceed using a dirty tree, a quarantined worktree, or an earlier task head. If the planning-contract conflict has not been resolved, label all final receipts `deliveryProvenancePending=true`; they can describe this candidate's results but cannot establish post-Task-6 delivery acceptance.

- [ ] **Step 2: Replay final accessibility and WM supporting runs**

Collect supporting runs 1–4 into the exact fresh directories:

| ID | Directory under final `EVIDENCE_ROOT` | Semantic expectation |
| --- | --- | --- |
| 1 | `accessibility-headless/` | Exactly the two named skips and 9 passes, 0 failures/errors |
| 2 | `accessibility-xvfb/` | All 11 methods execute/pass; 0 skips/failures/errors; actual display and cleanup |
| 3 | `f11-bare-xvfb/` | Exactly one selected-method skip, same-display absent-property proof, actual display and cleanup |
| 4 | `f11-openbox-xvfb/` | Openbox 3.6.1/PID/support-XID/name chain, <=2 px work-area cross-check, target executes with 0 skips/failures/errors, full class and cleanup |

Use the exact commands in the vector. The recorder creates their `run.json`; the Openbox runner may add its files to that newly reserved receipt directory. XML inspection must enforce each row before sealing. A missing Openbox prerequisite records the exact marker and BLOCKED; continue collecting independent evidence, retaining overall BLOCKED. Do not substitute optional bare-Xvfb success for positive evidence.

- [ ] **Step 3: Replay the four-sample conflict probe at this candidate**

Create fresh `$EVIDENCE_ROOT/two-pinned-conflicts/`, copy the hash-verified external probe into `ConflictProbe.java`, and write the exact init script:

```groovy
allprojects { p ->
  afterEvaluate {
    if (p.path == ':freeplane_plugin_graph') {
      tasks.register('printSpacingTestClasspath') {
        doLast { println('SPACING_TEST_CP=' + p.sourceSets.test.runtimeClasspath.asPath) }
      }
    }
  }
}
```

Run these exact commands with per-command argv/env/cwd/timestamp/exit receipts and a single aggregate `run.json`. Require each exit 0 and no candidate change between them:

```bash
JAVA_TOOL_OPTIONS=-Djava.awt.headless=true gradle :freeplane_plugin_graph:testClasses :freeplane_plugin_graph:printSpacingTestClasspath -I "$EVIDENCE_ROOT/two-pinned-conflicts/classpath.init.gradle" --console=plain > "$EVIDENCE_ROOT/two-pinned-conflicts/classpath.log" 2>&1
sed -n 's/^SPACING_TEST_CP=//p' "$EVIDENCE_ROOT/two-pinned-conflicts/classpath.log" > "$EVIDENCE_ROOT/two-pinned-conflicts/classpath.txt"
```

Before compilation, verify the classpath without changing it:

```python
import os
from pathlib import Path
lines = (Path(os.environ['EVIDENCE_ROOT']) / 'two-pinned-conflicts/classpath.txt').read_text().splitlines()
assert len(lines) == 1 and lines[0]
assert all(Path(entry).exists() for entry in lines[0].split(os.pathsep))
```

```bash
mkdir -p "$EVIDENCE_ROOT/two-pinned-conflicts/classes"
javac --release 8 -encoding UTF-8 -cp "$(cat "$EVIDENCE_ROOT/two-pinned-conflicts/classpath.txt")" -d "$EVIDENCE_ROOT/two-pinned-conflicts/classes" "$EVIDENCE_ROOT/two-pinned-conflicts/ConflictProbe.java"
JAVA_TOOL_OPTIONS=-Djava.awt.headless=true "$JAVA_HOME/bin/java" -Duser.language=en -Duser.country=US -cp "$EVIDENCE_ROOT/two-pinned-conflicts/classes:$(cat "$EVIDENCE_ROOT/two-pinned-conflicts/classpath.txt")" org.freeplane.plugin.graph.performance.ConflictProbe "$EVIDENCE_ROOT/two-pinned-conflicts/samples.jsonl" > "$EVIDENCE_ROOT/two-pinned-conflicts/probe.log" 2>&1
```

After staging the source/init inputs, hash-check and invoke the immutable Task 3 driver with its exact consumed CLI. The enclosing recorder writes this run's aggregate `run.json`; the driver executes the displayed commands and writes each command's receipt:

```bash
python3 "$PM_RUN_ROOT/scripts/epoch-3/record-run.py" "$EVIDENCE_ROOT/two-pinned-conflicts" -- bash "$PM_RUN_ROOT/scripts/epoch-3/run-conflict-probe.sh"
```

Inspect four distinct numbered samples, each first submission on a fresh worker, the exact fixture/request generation, every complete hull/kind/reason/blocking-pin record and exact 11/2/9/identity result. Require `failed=false`, `verified=true`; retain observed idle/step values without inventing a new idle requirement for this diagnostic fixture. Record diagnostic/fixture source hashes, candidate HEAD/tree and lane tree. The final integration/delivery tree remains an independent Task 6 check, never filled from an earlier epoch-2 probe. Seal all source/init/classpath/JAR/log/JSONL/class artifacts and include the manifest hash in the evidence index.

- [ ] **Step 4: Complete the fresh frozen-baseline spacing comparison**

Run supporting run 6 exactly once into fresh `$EVIDENCE_ROOT/spacing-comparison/`, with its command receipt under `task-5/comparison-command/`. Copy `run.json`, full command log and cleanup/process proof into the comparator output before sealing. Preserve its full output tree: baseline archive/source identity, candidate patch/status, observer hash, all main-source hashes, Java/Gradle versions, dependency/JAR checksums, exact input tables, full stdout/stderr, unrounded TSV files and `comparison.json`.

Require `comparison.json.status == "PASS"`, no comparator errors, all four enclosure cases NR8 A/SM8 A/XM4x2 A/XM4x2 B, each delta `>= 6.0 - 1e-6`, identical inputs and all frozen first-idle/post-idle/watchdog limits. Do not repair the comparator, observer, fixture or thresholds. Preserve the 100 post-idle diagnostic frames without adding a post-idle improvement threshold.

- [ ] **Step 5: Run and preserve all four exact final gates**

Before gates that must execute tests, remove only ignored generated `build/test-results/test/` and matching test-report output directories of the participating modules so the exact commands cannot reuse filtered or stale XML. Do not delete source or historical receipts. Verify every expected task actually executes and archive its just-emitted results; `UP-TO-DATE`, `FROM-CACHE`, or old XML alone is insufficient for a fresh test receipt.

Run sequentially under the global Java environment:

```bash
python3 "$PM_RUN_ROOT/scripts/epoch-3/record-run.py" "$PM_RUN_ROOT/reports/epoch-3/final-gates/gate-1" -- gradle :freeplane_plugin_graph:check --console=plain -PTestLoggingFull
python3 "$PM_RUN_ROOT/scripts/epoch-3/record-run.py" "$PM_RUN_ROOT/reports/epoch-3/final-gates/gate-2" -- gradle :freeplane_plugin_graph:graphPerformanceDiagnostic -PgraphStrictPerformance --console=plain
python3 "$PM_RUN_ROOT/scripts/epoch-3/record-run.py" "$PM_RUN_ROOT/reports/epoch-3/final-gates/gate-3" -- env -u JAVA_TOOL_OPTIONS xvfb-run -a gradle :freeplane_plugin_graph:graphOsgiSmoke :freeplane_plugin_graph:freeplaneLaunchSmoke --console=plain
python3 "$PM_RUN_ROOT/scripts/epoch-3/record-run.py" "$PM_RUN_ROOT/reports/epoch-3/final-gates/gate-4" -- gradle :freeplane:compileJava test --console=plain -PTestLoggingFull
```

Preserve independent FAIL/BLOCKED results and continue independent gates; do not turn a shell short-circuit into four claimed results. Gate 2's complete strict ledger must retain operation 5 s, process 40 min, eight raw frames <=5 s, 200-enclosure <=3.0 s/5 s await, cross-map 0.005 and idle 8/0.05/0.10. Gate 3 requires both project-qualified tasks, OSGi bundle/dependency/Java-8 evidence, the launch result, actual display and normal child termination. Gate 4 includes the application compile plus full root `test`, not only `:freeplane:test`. Copy all relevant output before a later invocation overwrites it, then seal.

- [ ] **Step 6: Prove full-module skip attribution and source isolation**

Use the frozen epoch-2 candidate XML under `/tmp/graph-initial-spacing-evidence/task-5/attempt-1/artifacts/freeplane_plugin_graph/build/test-results/test/` as the historical 18-skip input, after cross-checking its run/log identity with the archived gate-classification receipt. Extract `(classname,name)` for every skipped testcase and record the XML/log hashes. Compare Gate 1's fresh full-module XML: its skipped identity set must equal that historical set plus exactly the two named accessibility identities, cardinality 20. No other pre-existing skip identifier changes and no additional failure/error is permitted. If historical per-method XML cannot be authenticated, mark this requirement BLOCKED; do not infer the set from the number 18.

Run `git diff --check`, verify candidate HEAD/tree/clean status unchanged through all ten runs, verify both frozen production hashes and all `*/src/main/**` bytes, and compare every recorded source/runner hash. Require complete child/display cleanup and validate each manifest's sidecar and artifact hashes.

- [ ] **Step 7: Publish the evidence index and task handoff**

Write `task-5/evidence-index.json` with exactly six supporting and four gate entries, using actual receipts and the four-state vocabulary. Record an authoritative attempt path only after inspecting its semantic results; retain earlier failed attempts in the index. Include baseline receipt/hash, skip-set comparison, frozen budgets, candidate/lane identity and `deliveryProvenancePending` while the Task 6 conflict is unresolved. A missing/not-executed item is NOT RUN or BLOCKED with its reason and absent hash, never PASS. Seal the index/handoff directory only after all entries and annotations are final.

- [ ] **Step 8: Commit**

This task changes only external artifacts. Make an explicit empty checkpoint commit; do not stage generated output or alter a source/document merely to create a diff. This advances HEAD without changing its tree; existing receipts retain their actual measured commit and tree.

```bash
git diff --check
git diff --exit-code
git diff --cached --exit-code
git commit --allow-empty -m "graph-initial-spacing: checkpoint epoch-3 gate receipts"
```

## Task 6: Write the receipt-backed acceptance record and verify delivery-tree equality

**Implementer tier:** Advanced

**Lane:** spacing

**Sequence:** 6

**Depends on:** Task 5

**Files:**

- Create: `/home/henry/.local/state/pi/project-manager/runs/0233ff88d3cc54bbd61550c61fa917d6e2a5717f8a03be4e9aa63dcd47dd1cfa/pm-run-20261016-125451-a5048653/reports/epoch-3/acceptance/` — the acceptance record `2026-10-18-graph-initial-spacing-epoch-3.md`, `acceptance-audit.json`, `delivery-provenance.json`, and the hash/source-isolation ledger only.
- Create: `/home/henry/.local/state/pi/project-manager/runs/0233ff88d3cc54bbd61550c61fa917d6e2a5717f8a03be4e9aa63dcd47dd1cfa/pm-run-20261016-125451-a5048653/reports/epoch-3/task-6/` — acceptance verification notes, historical-finding links and the empty-checkpoint receipt only.
- Read only: all final supporting/gate receipts and manifests, baseline receipt and strict log, approved spec/design/reviews, existing epoch-1/epoch-2 evidence/archives and the Task 5 evidence index. **No repository file of any kind is written, staged, committed or deleted in this task**; no source/test/build/spec/design/plan/doc edit, receipt rewrite, gate rerun, merge or push is authorized.

**Interfaces:**

- Consumes: `task-5/evidence-index.json` with six `supportingRuns`, four `finalGates`, four-state results, receipt paths/hashes, candidate commit/tree/cleanup and requirement IDs.
- Consumes: baseline 20 receipt at frozen source `0734fb23db6873f4ec64c3e3e29fc8c3e17edc7a`/tree `0bc8e57b5d26c2d79252a4ea4735cbf52fdbbf3c`, strict-log hash `67390c50c91aec57d8ff913b6f1c549453eab83555834d53978bf66b6a52c104`, final four sample records and Task 4's killed-mutant receipt.
- Consumes: independently supplied final candidate, lane and integration/delivery checkout identities. Obtain those actual paths from the PM controller and inspect each checkout; missing identity is BLOCKED. Do not assign all three variables the candidate path just to satisfy equality.
- Produces: the acceptance record at `$EPOCH3_ROOT/acceptance/2026-10-18-graph-initial-spacing-epoch-3.md` mapping every requirement to actual artifact paths, hashes, statuses and limitations; it preserves all historical failures and the twenty-to-eleven derivation. It is an external PM artifact and is **not** committed to Git.
- Produces: `acceptance/acceptance-audit.json` and `acceptance/delivery-provenance.json`. Overall PASS requires all ten semantic results, preserved budgets/source bytes, cleanup, and candidate = lane = integration/delivery tree equality. Because this task writes no repository file, equality is achievable exactly; any mismatch is BLOCKED with the measured OIDs recorded, never reconciled by relaxing the rule.

**Validation commands:**

```bash
git diff --check
git diff --exit-code 92f31fce842f8e5bb1376c13e4135cdd138907db -- '*/src/main/**'
git status --porcelain=v1 --untracked-files=all
git rev-parse HEAD 'HEAD^{tree}'
python3 - "$PM_RUN_ROOT/reports/epoch-3/task-5/evidence-index.json" <<'PY'
import json, sys
from pathlib import Path
index = json.loads(Path(sys.argv[1]).read_text())
assert len(index['supportingRuns']) == 6 and len(index['finalGates']) == 4
allowed = {'PASS', 'FAIL', 'NOT RUN', 'BLOCKED'}
for rows in (index['supportingRuns'], index['finalGates']):
    assert len({row['id'] for row in rows}) == len(rows)
    for row in rows:
        assert row['status'] in allowed
        if row['status'] == 'PASS':
            assert row['exitStatus'] == 0 and row['cleanupComplete']
            assert row['manifestSha256'] and Path(row['receiptPath']).exists()
PY
```

The commands above check the index shape; the steps below independently verify its evidence. Do not equate schema validity with acceptance.

- [ ] **Step 1: Audit every immutable receipt and final-tree identity**

Verify manifest sidecars and every artifact digest, runner-before/after hashes, source hashes, actual exit statuses, semantic method/conflict/comparator results and cleanup. Require clean unchanged candidate identity. Read actual lane and integration/delivery `HEAD^{tree}` values; compare them with the final candidate receipt tree, not a prior epoch-2 probe. Record missing/mismatched identities as BLOCKED. Confirm that no repository commit or working-tree change has occurred since Task 5's candidate pin, so the measured tree is still the delivered tree.

- [ ] **Step 2: Derive the honest status ledger and requirement mapping**

Map supporting 1/2 to Section 13; supporting 3/4 to Section 14; supporting 5 plus baseline receipt to Section 15; the focused/suite/mutant receipts to Section 16; all six supporting runs to Sections 17/18; gates 1–4 to Section 19; the comparator/source-freeze/strict ledger to Section 20; and the combined results/tree identity to Section 21. Cite paths and digest values actually present. If any necessary run failed, was not run, or is blocked, retain that state and explain it. Do not relabel the bare-Xvfb assumption skip as positive Openbox evidence.

- [ ] **Step 3: Write the acceptance record with explicit provenance limits**

Write `$EPOCH3_ROOT/acceptance/2026-10-18-graph-initial-spacing-epoch-3.md` (outside Git) using `#`/`##` headings freely — this file is not parsed as plan content, so the plan's `##` restriction does not apply to it. Write these concrete sections, populating all status rows from the verified evidence index rather than estimates:

1. Authority and source identity: spec/design hashes, epoch-3 candidate commit/tree, actual lane/integration/delivery identities, production freeze hashes and accepted tree comparison.
2. Overall status: PASS/FAIL/NOT RUN/BLOCKED with the actual reasons, and the measured candidate, lane and integration/delivery tree OIDs used for the equality decision.
3. Supporting matrix: six rows with exact commands/environments, actual counts/results, artifact paths, manifest hashes, source trees and cleanup. Preserve 2 named headless skips/9 passes versus 11 display executions, the optional absent-WM proof versus required Openbox run, four complete pin-conflict samples and the frozen comparison.
4. Four final gates: four separate rows for exact project-qualified argv/environments, actual exits, XML/diagnostic/bundle/launch evidence and receipt hashes. Do not omit a failed or unavailable gate.
5. Conflict derivation: stale constant 1, frozen measured baseline 20 and final measured candidate 11 are three distinct facts. Explain the co-located persisted roots, wider rings, exact all-`IMMOVABLE_SIDES`/2-crossing/9-containment/identity requirements and four individual samples. Include the exact frozen baseline commit/tree/log hash and receipt hash.
6. Lifecycle falsifiability: resource count increase; handle-closing rejection; callback-registration rejection as the governing logical-close signal observed **before** release; the blocked listener released only afterwards; map-listener return to baseline as a **post-release** teardown-completion assertion; retained zero-callback assertions and copied-class mutant failure. Cite active-production byte isolation and cleanup proof. Never describe the listener baseline as a pre-release gate.
7. Unchanged contracts: enumerate the exact Section 20 values and link the comparison/strict/safety/bundle receipts; no new tolerance or exception.
8. Preserved history: epoch-1 quarantine and convergence findings, epoch-2 integrated ring-only remediation and quarantined final-gate failures, the approved defect investigation, and final epoch-3 remediation evidence. Never claim the quarantined run passed. Preserve the cross-map `+18 / 0.005 >=3600` versus 2000-step finding explaining why preferred gap stays ring-only; link the original report rather than rewriting its observations.
9. Remaining blockers and review: any failed, missing, or blocked receipt; any actual tree mismatch. State that a new explicit decision is required for any production correction or any change to the final-tree provenance contract, and that the acceptance record lives outside Git by the recorded 2026-10-18 user decision.

Link external receipts with their absolute paths so a reviewer can inspect them. Include no invented checksum or result. A BLOCKED record is a valid honest deliverable; it is not completed epoch-3 acceptance.

- [ ] **Step 4: Verify content coverage, source isolation and the delivery-tree equality rule**

Write `$EPOCH3_ROOT/acceptance/acceptance-audit.json` containing each Section 21 criterion, mapped evidence rows, actual status/reason, checked hashes and the document SHA-256. Confirm all six supporting and all four final gate IDs appear exactly once in their respective ledger tables. Verify the baseline receipt SHA, strict-log SHA, four sample records, budgets, production byte equality and preserved history links.

Confirm on the repository that `git status --porcelain=v1 --untracked-files=all` is empty, that no commit has been created since Task 5's candidate pin, and that the delivered `HEAD^{tree}` still equals the measured candidate tree. Because this task writes no repository file, that equality is expected to hold exactly; a mismatch is BLOCKED and must be reported with the measured OIDs, never repaired by relaxing the rule.

- [ ] **Step 5: Empty checkpoint commit**

This task has no repository content to stage. Create an explicit empty checkpoint commit so the lane has a commit boundary after acceptance, and immediately prove the tree is unchanged and still equal to the measured candidate tree.

```bash
git status --porcelain=v1 --untracked-files=all
git commit --allow-empty -m "graph-initial-spacing: checkpoint after external epoch-3 acceptance record"
git status --porcelain=v1 --untracked-files=all
python3 - "$PM_RUN_ROOT/reports/epoch-3/task-5/candidate.json" "$PM_RUN_ROOT/reports/epoch-3/acceptance/delivery-provenance.json" <<'PY'
import json, subprocess, sys
from pathlib import Path
candidate = json.loads(Path(sys.argv[1]).read_text())
head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
tree = subprocess.check_output(['git', 'rev-parse', 'HEAD^{tree}'], text=True).strip()
measured = candidate['tree']
status = 'PASS' if tree == measured else 'BLOCKED'
record = {'deliveryHead': head, 'deliveryTree': tree, 'measuredCandidateTree': measured,
          'acceptanceRecordLocation': 'external:reports/epoch-3/acceptance',
          'status': status, 'reason': None if tree == measured else 'DELIVERY_TREE_MISMATCH'}
target = Path(sys.argv[2])
with target.open('x') as output:
    json.dump(record, output, indent=2)
    output.write('\n')
print(json.dumps(record, indent=2))
# Equality is required; this is not a source-only digest or a documentation exclusion.
raise SystemExit(0 if tree == measured else 78)
PY
```
