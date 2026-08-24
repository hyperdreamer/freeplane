# Graph Workspace Clean-Suite Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.

**Goal:** Make the complete Graph Workspace plugin test suite reproducible and
green by isolating its dialog-resource mocks and by supplying the production
map-version XSLT through the controlled headless resource base.

**Architecture:** The repair is test-only and has two bounded fixtures.
`WorkspaceDialogsShould` will own thread-local `ResourceController` and
`TextUtils` mocks on both its JUnit and AWT threads. `HeadlessResourceFiles`
will snapshot, overlay, and restore the exact production XSLT inside the viewer
resource base already selected by its headless starter. No application resource
lookup behavior or graph runtime lifecycle is changed.

**Tech Stack:** Java 8 source compatibility, Zulu JDK 21.0.8, Gradle 9,
JUnit 4, Mockito static mocks, Java NIO, AssertJ, and Node.js for test-result
and disposable-worktree verification.

## Global Constraints

- Work only in `/data/home/guest/Development/freeplane/.worktrees/graph-workspace-clean-suite-remediation` on branch `graph-workspace-clean-suite-remediation`; preserve `/data/home/guest/Development/freeplane` and every other worktree.
- This run begins from design-correction commit `1453779e7d3eab4fc95ea12b36aea738e6bb0729`; Task 42 correction commit `a093868c24b1b68f8e6986ad5e646572f2006ab1` remains the predecessor verification baseline.
- Follow `docs/superpowers/specs/2026-08-24-graph-workspace-clean-suite-remediation-design.md`; do not modify that specification, this plan after initialization, Task 42 artifacts, or any predecessor `.superpowers/sdd/006-implement-graph-workspace` artifact.
- Modify only `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/WorkspaceDialogsShould.java` and `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceColdReloadShould.java`; do not modify production sources, Gradle files, source resources, generated resources, test ordering, test exclusions, or ignore annotations.
- Use `/home/henry/.sdkman/candidates/java/21.0.8-zulu` and `gradle`; source and target compatibility remains Java 8. Do not use `gradlew`, Maven, Java APIs newer than Java 8, or non-ASCII source text.
- The focused dialog baseline is a valid RED gate only when all nine `WorkspaceDialogsShould` tests fail through `ResourceController.getResourceController()` because `Controller.getCurrentController()` is null. Do not change expected assertions or use an ambient controller to mask this failure.
- The cold-reload baseline is a valid RED gate only when the full graph-plugin suite reaches `GraphWorkspaceColdReloadShould`, logs `Can't find /xslt/freeplane_version_updater.xslt as resource`, and fails its saved-map reopening projection assertion. A standalone cold-reload pass is not evidence that this suite-order defect is absent.
- `WorkspaceDialogsShould` static Mockito mocks are thread-local. Create and close each mock on the same thread; retain EDT mocks until per-test teardown and close them through `GraphWorkspaceWindow.runOnEdt`.
- Preserve the six existing contributor-label assertions by formatting these exact templates: `Source: {0}`, `Middle: {0}`, `Target: {0}`, `Owner: {0}`, `Key: {0}`, and `Native connector: {0}`. Other formatting must stay deterministic and preserve the existing source-map undo assertion shape `graph_workspace.action.undo_source_map:Roadmap`.
- The XSLT overlay source is exactly `freeplane/src/editor/resources/xslt/freeplane_version_updater.xslt`; the controlled target is exactly `freeplane/build/resources/viewer/xslt/freeplane_version_updater.xslt`. Snapshot and restore target bytes exactly, deleting it on teardown only when it was absent before setup.
- Do not add a resource-lookup fallback, a class loader, a test-resource copy of the XSLT, shared public test support, sleeps, retries, compatibility paths, or unrelated cleanup.
- Do not represent the task as complete until the full plugin gate, repository `gradle test`, Task 42 smoke command, focused acceptance gate, and strict performance diagnostic have fresh passing evidence. Use explicit wall-clock bounds; do not exclude `WorkspaceDialogsShould` or `GraphWorkspaceColdReloadShould` from any full gate.
- The successor task review and final review must carry predecessor Task 42 finding `F-4`: full graph-plugin and repository test gates were blocked by the dialog fixture failures and the cold-reload XSLT visibility failure. Resolve F-4 only when the fresh complete gates pass without exclusions.
- Before the implementation commit, compare working-tree and staged paths with the exact two-file allowlist, run `git diff --check`, and commit only those two test files using `2026-08-10-graph-workspace: Fix clean graph test fixtures`.

## Task 1: Isolate Dialog and Headless Resource Fixtures

**Implementer tier:** Capable

**Files:**

- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/WorkspaceDialogsShould.java:1-535`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceColdReloadShould.java:1365-1441`

**Interfaces:**

- Consumes: `GraphWorkspaceWindow.runOnEdt(Runnable)`, Mockito `MockedStatic<T>`, `TextUtils.format(String, Object...)`, the existing nine `WorkspaceDialogsShould` assertions, and `HeadlessResourceFiles.copyWithBackup(Path, Path)` / `restore(Path, byte[])`.
- Produces: no public API. Every dialog test can construct real Swing graph-window components without an ambient Freeplane controller; the cold-reload fixture makes the exact production XSLT visible through its selected viewer resource directory and restores any prior target bytes after closing.

- [ ] **Step 1: Capture both unmodified RED gates**

Run the focused dialog class first. Require nonzero exit, exactly nine failed
tests, and the null-current-controller resource lookup stack. This is the
falsifiable red phase for the dialog fixture; do not edit either source file
before observing it.

```bash
set -euo pipefail
export JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"
cd /data/home/guest/Development/freeplane/.worktrees/graph-workspace-clean-suite-remediation
set +e
timeout --signal=TERM --kill-after=20s 180s \
  gradle --no-daemon --no-parallel :freeplane_plugin_graph:test \
  --tests 'org.freeplane.plugin.graph.window.WorkspaceDialogsShould' \
  -PTestLoggingFull --rerun-tasks > /tmp/graph-dialog-red.log 2>&1
status=$?
set -e
test "$status" -ne 0
rg -q '9 tests completed, 9 failed' /tmp/graph-dialog-red.log
rg -q 'ResourceController\.getResourceController' /tmp/graph-dialog-red.log
rg -q 'Controller\.getCurrentController\(\).* is null' /tmp/graph-dialog-red.log
```

Then run the entire module in a fresh Gradle test execution. Require a
nonzero exit, the cold-reload class failure, and the exact missing-XSLT log.
The observed baseline at plan time completed 708 tests with one failure and
two skips; use the error signature rather than a hard-coded aggregate count.

```bash
set -euo pipefail
export JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"
cd /data/home/guest/Development/freeplane/.worktrees/graph-workspace-clean-suite-remediation
set +e
timeout --signal=TERM --kill-after=20s 420s \
  gradle --no-daemon --no-parallel :freeplane_plugin_graph:test \
  -PTestLoggingFull --rerun-tasks > /tmp/graph-clean-suite-red.log 2>&1
status=$?
set -e
test "$status" -ne 0
rg -q 'GraphWorkspaceColdReloadShould > coldReloadsProductionWorkspaceStateAndProjection FAILED' \
  /tmp/graph-clean-suite-red.log
rg -q 'Can.t find /xslt/freeplane_version_updater\.xslt as resource' /tmp/graph-clean-suite-red.log
rg -q 'production graph projection did not publish the expected native edges' /tmp/graph-clean-suite-red.log
```

- [ ] **Step 2: Add per-test dialog resource fixtures on both execution threads**

Add the needed imports to `WorkspaceDialogsShould`: `java.text.MessageFormat`,
`org.freeplane.core.resources.ResourceController`,
`org.freeplane.core.util.TextUtils`, `org.junit.Before`, `org.junit.After`,
`org.mockito.MockedStatic`, and `org.mockito.invocation.InvocationOnMock`.

Introduce a private `ResourceMocks` holder that opens the two static mocks in
its constructor and closes them in `close()`. The holder is instantiated on
the thread that owns it. Configure it with this behavior:

```java
resourceController = org.mockito.Mockito.mockStatic(ResourceController.class);
resourceController.when(ResourceController::getResourceController)
    .thenReturn(mock(ResourceController.class));
textUtils = org.mockito.Mockito.mockStatic(TextUtils.class);
textUtils.when(() -> TextUtils.getText(any(String.class)))
    .thenAnswer(invocation -> invocation.getArgument(0));
textUtils.when(() -> TextUtils.getText(any(String.class), any(String.class)))
    .thenAnswer(invocation -> invocation.getArgument(0));
textUtils.when(() -> TextUtils.getRawText(any(String.class)))
    .thenAnswer(invocation -> invocation.getArgument(0));
textUtils.when(() -> TextUtils.getRawText(any(String.class), any(String.class)))
    .thenAnswer(invocation -> invocation.getArgument(0));
textUtils.when(() -> TextUtils.format(any(String.class), any(Object[].class)))
    .thenAnswer(invocation -> formattedText(invocation));
```

Use `@Before` to create one `ResourceMocks` instance on the JUnit thread, and
use `@After` to close it before finishing teardown. Store each EDT-created
holder in a private static `List<ResourceMocks>`. In teardown, close every
such holder inside a single `GraphWorkspaceWindow.runOnEdt` callback, then
clear the list. Do not close an EDT static mock from the JUnit thread.

Add a private `runOnEdtWithResources(Runnable)` helper. It must construct a
new `ResourceMocks` inside `GraphWorkspaceWindow.runOnEdt`, run the supplied
action, and retain that holder in the EDT list for teardown. Replace both
current direct `GraphWorkspaceWindow.runOnEdt` construction calls in this
class with the helper: the `focusesTheGraphOnceOnlyForAnEditorActivatingCommandResultAfterRouting`
fixture and `modelFixture`.

Implement `formattedText(InvocationOnMock)` so it unpacks both a varargs
`Object[]` and individually supplied format arguments. For the six
contributor keys, call `MessageFormat.format` with the exact templates below:

```java
graph_workspace.contributor.source           -> "Source: {0}"
graph_workspace.contributor.middle           -> "Middle: {0}"
graph_workspace.contributor.target           -> "Target: {0}"
graph_workspace.contributor.owner            -> "Owner: {0}"
graph_workspace.contributor.key              -> "Key: {0}"
graph_workspace.contributor.native_connector -> "Native connector: {0}"
```

For every other key, build the same deterministic fallback used by
`UndoRoutingShould`: start with the key, flatten nested object arrays, and
append each format argument prefixed with `:`. This preserves existing
observable assertions such as `graph_workspace.action.undo_source_map:Roadmap`.
Do not replace the existing contributor-label assertions or assert mock calls.

- [ ] **Step 3: Overlay and restore the production XSLT in the cold-reload fixture**

In `HeadlessResourceFiles`, add two fields beside the existing test-resource
snapshot fields:

```java
private final Path xslt;
private final byte[] previousXslt;
```

Inside the constructor, derive the source and target after `viewerResources`
is available:

```java
final Path editorXslt = projectDirectory.resolve(
    "freeplane/src/editor/resources/xslt/freeplane_version_updater.xslt");
xslt = viewerResources.resolve("xslt/freeplane_version_updater.xslt");
```

Extend the existing prerequisite check to require `editorXslt` to be a regular
file. Before changing global resource-directory fields, copy the production
source into the selected viewer base using the existing exact-byte helper:

```java
previousXslt = copyWithBackup(editorXslt, xslt);
```

In `close()`, call `restore(xslt, previousXslt)` with the other resource
restorations before restoring global resource properties. Do not leave an XSLT
under `freeplane/build/resources/viewer` after a fixture that found no prior
file, and do not alter `copyWithBackup` or `restore` semantics.

- [ ] **Step 4: Run focused green gates and inspect their XML results**

Run both named classes independently after the source changes:

```bash
set -euo pipefail
export JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"
cd /data/home/guest/Development/freeplane/.worktrees/graph-workspace-clean-suite-remediation
gradle --no-daemon --no-parallel :freeplane_plugin_graph:test \
  --tests 'org.freeplane.plugin.graph.window.WorkspaceDialogsShould' \
  --tests 'org.freeplane.plugin.graph.integration.GraphWorkspaceColdReloadShould' \
  -PTestLoggingFull --rerun-tasks
node --input-type=module - freeplane_plugin_graph/build/test-results/test <<'NODE'
import { readFileSync } from 'node:fs';
const directory = process.argv[2];
const expected = {
  'TEST-org.freeplane.plugin.graph.window.WorkspaceDialogsShould.xml': 9,
  'TEST-org.freeplane.plugin.graph.integration.GraphWorkspaceColdReloadShould.xml': 1,
};
for (const [name, tests] of Object.entries(expected)) {
  const xml = readFileSync(`${directory}/${name}`, 'utf8');
  const suite = xml.match(/<testsuite\b[^>]*>/)?.[0];
  if (suite === undefined) throw new Error(`missing testsuite in ${name}`);
  const attribute = (key) => Number(suite.match(new RegExp(`${key}="(\\d+)"`))?.[1]);
  if (attribute('tests') !== tests || attribute('failures') !== 0 || attribute('errors') !== 0) {
    throw new Error(`${name} did not pass: ${suite}`);
  }
}
NODE
```

- [ ] **Step 5: Commit the two test-fixture changes**

Before committing, verify the source diff contains only the exact two allowed
paths and no generated resource. Then make one implementation commit.

```bash
set -euo pipefail
cd /data/home/guest/Development/freeplane/.worktrees/graph-workspace-clean-suite-remediation
git diff --check
test "$(git diff --name-only | sort)" = "$(printf '%s\n%s\n' \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceColdReloadShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/WorkspaceDialogsShould.java | sort)"
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/WorkspaceDialogsShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceColdReloadShould.java
git diff --cached --check
test "$(git diff --cached --name-only | sort)" = "$(printf '%s\n%s\n' \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceColdReloadShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/WorkspaceDialogsShould.java | sort)"
git commit -m '2026-08-10-graph-workspace: Fix clean graph test fixtures'
```

- [ ] **Step 6: Prove the XSLT red phase against only its added mechanism**

After the implementation commit, create a disposable detached worktree from
that commit. In the disposable worktree, replace exactly one occurrence of:

```java
previousXslt = copyWithBackup(editorXslt, xslt);
```

with:

```java
previousXslt = Files.exists(xslt) ? Files.readAllBytes(xslt) : null;
```

This mutant retains the snapshot but removes only the production-XSLT overlay.
Run the complete graph-plugin suite with `--rerun-tasks`; it must fail and log
`Can't find /xslt/freeplane_version_updater.xslt as resource` from
`GraphWorkspaceColdReloadShould`. Remove the temporary worktree and verify the
active worktree's two source files remain unchanged. Use this cleanup-safe
shell structure:

```bash
set -euo pipefail
source=/data/home/guest/Development/freeplane/.worktrees/graph-workspace-clean-suite-remediation
head=$(git -C "$source" rev-parse HEAD)
tmp=$(mktemp -d /tmp/freeplane-graph-xslt-mutant.XXXXXX)
rmdir "$tmp"
cleanup() {
  status=$?
  git -C "$source" worktree remove --force "$tmp" 2>/dev/null || status=1
  test ! -e "$tmp" || status=1
  exit "$status"
}
trap cleanup EXIT
git -C "$source" worktree add --detach "$tmp" "$head"
node --input-type=module - "$tmp/freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceColdReloadShould.java" <<'NODE'
import { readFileSync, writeFileSync } from 'node:fs';
const path = process.argv[2];
const source = readFileSync(path, 'utf8');
const target = '            previousXslt = copyWithBackup(editorXslt, xslt);\n';
if (source.split(target).length !== 2) throw new Error('expected one XSLT overlay statement');
writeFileSync(path, source.replace(target,
  '            previousXslt = Files.exists(xslt) ? Files.readAllBytes(xslt) : null;\n'));
NODE
export JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"
set +e
( cd "$tmp" && timeout --signal=TERM --kill-after=20s 420s \
  gradle --no-daemon --no-parallel :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks \
  > /tmp/graph-xslt-mutant.log 2>&1 )
status=$?
set -e
test "$status" -ne 0
rg -q 'Can.t find /xslt/freeplane_version_updater\.xslt as resource' /tmp/graph-xslt-mutant.log
```

- [ ] **Step 7: Run the complete verification matrix from the committed source**

Use the committed source, not stale test output. The first command is the
Task 42 module gate that previously blocked; it must complete before its bound
with no test failures or errors. The repository gate must run every module;
do not filter it. The smoke command must execute all three task bodies rather
than report either evidence or launch task as up-to-date.

```bash
set -euo pipefail
export JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"
cd /data/home/guest/Development/freeplane/.worktrees/graph-workspace-clean-suite-remediation
timeout --signal=TERM --kill-after=20s 600s \
  gradle --no-daemon --no-parallel :freeplane_plugin_graph:clean :freeplane_plugin_graph:check \
  :freeplane_plugin_graph:test :freeplane_plugin_graph:build -PTestLoggingFull
timeout --signal=TERM --kill-after=20s 900s \
  gradle --no-daemon --no-parallel test -PTestLoggingFull
gradle :freeplane_plugin_graph:graphOsgiSmoke :freeplane_plugin_graph:graphUiEvidence \
  :freeplane_plugin_graph:freeplaneLaunchSmoke -PTestLoggingFull
gradle --no-daemon --no-parallel :freeplane_plugin_graph:test \
  --tests 'org.freeplane.plugin.graph.integration.GraphWorkspaceModelAcceptanceShould' \
  --tests 'org.freeplane.plugin.graph.integration.GraphWorkspaceCommandAcceptanceShould' \
  -PTestLoggingFull --rerun-tasks
gradle --no-daemon --no-parallel :freeplane_plugin_graph:graphPerformanceDiagnostic \
  -PgraphStrictPerformance -PTestLoggingFull
```

After the module command, inspect every generated plugin test XML to prove the
suite executed and has no failures or errors. Do not hard-code the aggregate
test count, but require the two repaired classes to be present and green.

```bash
node --input-type=module - freeplane_plugin_graph/build/test-results/test <<'NODE'
import { readFileSync, readdirSync } from 'node:fs';
const directory = process.argv[2];
const totals = { tests: 0, failures: 0, errors: 0 };
const files = readdirSync(directory).filter((name) => /^TEST-.*\.xml$/.test(name));
for (const name of files) {
  const suite = readFileSync(`${directory}/${name}`, 'utf8').match(/<testsuite\b[^>]*>/)?.[0];
  if (suite === undefined) continue;
  for (const key of Object.keys(totals)) {
    totals[key] += Number(suite.match(new RegExp(`${key}="(\\d+)"`))?.[1] || 0);
  }
}
if (totals.tests === 0 || totals.failures !== 0 || totals.errors !== 0) {
  throw new Error(`unexpected graph-plugin totals: ${JSON.stringify(totals)}`);
}
for (const name of [
  'TEST-org.freeplane.plugin.graph.window.WorkspaceDialogsShould.xml',
  'TEST-org.freeplane.plugin.graph.integration.GraphWorkspaceColdReloadShould.xml',
]) {
  const suite = readFileSync(`${directory}/${name}`, 'utf8').match(/<testsuite\b[^>]*>/)?.[0];
  if (suite === undefined || !/failures="0"/.test(suite) || !/errors="0"/.test(suite)) {
    throw new Error(`repaired class did not pass: ${name}`);
  }
}
console.log(JSON.stringify(totals));
NODE
git status --short
```

In the implementer report, record both baseline red signatures, focused test
results, the mutant result, the module and repository-wide gate outcomes, the
three smoke-task output, the strict performance outcome, the implementation
commit SHA, and whether predecessor finding F-4 is resolved by the full gates.
