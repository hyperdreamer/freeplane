# Headless Resource Scope Clean-Suite Remediation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox syntax for readability; `state.json` is the canonical tracker.

**Goal:** Make the graph-plugin test suite reproducible from a clean checkout by creating the headless map-version resource parent before copying and by preserving primary setup failures during connector-fixture teardown.

**Architecture:** Keep `GraphAdapterTestSupport.HeadlessResourceScope` private and its existing resource snapshot/restore protocol unchanged. Establish the destination-directory invariant directly beside `testMapVersions`, and make the connector class's static teardown conditional on successful class setup. Verify the real fixture lifecycle in fresh detached worktrees rather than precreating build output.

**Tech Stack:** Java 8 source compatibility, Zulu JDK 21.0.8, Gradle, JUnit 4, AssertJ, Java NIO `Files`, Git worktrees, and Node.js for deterministic source-mutant byte restoration.

## Global Constraints

- Work only in `/data/home/guest/Development/freeplane/.worktrees/graph-batch-d-lifecycle-recovery` on branch `graph-batch-d-lifecycle-recovery`; preserve `/data/home/guest/Development/freeplane` and `.worktrees/graph-batch-d-remediation` unchanged.
- The predecessor SDD run at `.superpowers/sdd/asynchronous-reset-observability-recovery-v1` is terminal at revision 21 `FINAL_BLOCKED`; never modify, reopen, or dispatch into its `state.json`, `progress.md`, prompts, reports, or event files.
- The predecessor recovery implementation HEAD is `5692562573a26f39ef94bb0e97d6c23dd7ab3a0f`; the original Batch D merge base is `9248c6e227bb82fab8e6139f46db37b62174309f`; the committed successor design baseline is `2428b2aa1a99fa605bfbf0c3bfe07caeec0e8d58`.
- Follow `docs/superpowers/specs/2026-08-17-headless-resource-scope-clean-suite-remediation-design.md`; do not modify that specification.
- Modify only `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/MapSnapshotFactoryShould.java` and `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/ConnectorSnapshotFactoryShould.java`. Do not modify production code, build files, generated resources, `GraphAdapterTestSupport` APIs, lifecycle files, the old recovery plan, or the terminal-run evidence.
- Use `/home/henry/.sdkman/candidates/java/21.0.8-zulu` and `gradle -p /data/home/guest/Development/freeplane/.worktrees/graph-batch-d-lifecycle-recovery`; use `gradle`, never Maven or the wrapper.
- Preserve `HeadlessResourceScope` resource bytes/restore behavior, `HeadlessMapScope` ownership and close behavior, and the original exception from any failed `@BeforeClass` setup. A successful `ConnectorSnapshotFactoryShould` setup must still close its static `HeadlessMapScope` exactly once.
- Create `testMapVersions.getParent()` explicitly before inspecting or copying `xml/mapVersions.xml`; do not rely on the preference-resource copy to create the shared parent incidentally.
- Do not add a shared fixture abstraction, public/package-private API, sleeps, precreated build-output workaround, compatibility fallback, or unrelated cleanup.
- The clean detached worktree full-module failure is the required RED evidence because this task corrects test bootstrap itself: before edits, `gradle :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks` must fail with the missing `xml/mapVersions.xml` destination, the secondary null-pointer teardown failure, and the known 428-test/2-failure/2-skip aggregate. Compilation failure, missing headless prerequisites, or a different test failure is invalid RED evidence.
- After green, mutate only the new `Files.createDirectories(testMapVersions.getParent())` line while retaining the null teardown guard. The focused connector test must then fail from `NoSuchFileException` for `mapVersions.xml` and must not report any `NullPointerException`; restore exact source bytes before the full suite.
- The existing untracked `docs/superpowers/plans/2026-08-17-asynchronous-reset-observability-recovery.md` and ignored `.superpowers/sdd/**` artifacts are preserved execution evidence. They are the only non-deliverable untracked evidence permitted at preflight; do not stage them.
- Before commit, compare unstaged/staged paths and the implementation commit itself with the exact two-file allowlist, run `git diff --check`, and commit only the source/test fixtures using `2026-08-10-graph-workspace: Fix headless test resource setup`.
- The successor final review must review the original merge base `9248c6e227bb82fab8e6139f46db37b62174309f` through final HEAD and reconcile carried predecessor findings: F-1 settling lifecycle behavior is fixed, and F-2 clean-suite reproducibility is resolved by this task.

## Task 1: Repair Headless Resource Fixture Bootstrap

**Implementer tier:** Advanced

**Files:**

- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/MapSnapshotFactoryShould.java:838-913`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/ConnectorSnapshotFactoryShould.java:33-43`

**Interfaces:**

- Consumes: private `GraphAdapterTestSupport.HeadlessResourceScope()` setup, its `Path testMapVersions` destination, `Files.createDirectories(Path)`, and static `ConnectorSnapshotFactoryShould.headless` assigned by `setUpHeadlessResources()`.
- Produces: no public API. A clean generated test-resource tree can initialize `HeadlessResourceScope`; failed connector class setup retains its original exception without a secondary null-pointer teardown failure; successful setup still closes the assigned scope.

- [ ] **Step 1: Record valid clean-checkout full-suite RED evidence before edits**

Create a fresh detached diagnostic worktree at the current successor baseline. Do not create any `build/resources/test/xml` directory manually. Run the exact full graph-plugin command required by the approved design, not a filtered class invocation.

```bash
set -euo pipefail
source=/data/home/guest/Development/freeplane/.worktrees/graph-batch-d-lifecycle-recovery
head=$(git -C "$source" rev-parse HEAD)
tmp=
cleanup() {
  local status=$?
  if [ -n "$tmp" ]; then
    if ! git -C "$source" worktree remove --force "$tmp"; then
      status=1
    fi
    if [ -e "$tmp" ]; then
      status=1
    fi
    local registered
    if ! registered=$(git -C "$source" worktree list --porcelain); then
      status=1
    elif printf '%s\n' "$registered" | rg -Fqx "worktree $tmp"; then
      status=1
    fi
  fi
  exit "$status"
}
remove_worktree() {
  path=$1
  git -C "$source" worktree remove --force "$path"
  test ! -e "$path"
  if git -C "$source" worktree list --porcelain | rg -Fqx "worktree $path"; then
    echo "temporary worktree remains registered: $path" >&2
    return 1
  fi
}
trap cleanup EXIT
tmp=$(mktemp -d /tmp/freeplane-headless-resource-red.XXXXXX)
rmdir "$tmp"
git -C "$source" worktree add --detach "$tmp" "$head"
set +e
(
  cd "$tmp"
  export JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu
  export PATH="$JAVA_HOME/bin:$PATH"
  gradle :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks
) > /tmp/freeplane-headless-resource-red.log 2>&1
status=$?
set -e
if [ "$status" -eq 0 ]; then
  echo 'expected clean full graph-plugin suite to fail before the repair' >&2
  exit 1
fi
rg -q 'NoSuchFileException: .*mapVersions\.xml' /tmp/freeplane-headless-resource-red.log
rg -q 'ConnectorSnapshotFactoryShould' /tmp/freeplane-headless-resource-red.log
rg -q 'NullPointerException' /tmp/freeplane-headless-resource-red.log
rg -q '428 tests completed, 2 failed, 2 skipped' /tmp/freeplane-headless-resource-red.log
remove_worktree "$tmp"
tmp=
```

Record the exact command, nonzero exit, and both expected failure mechanisms in the implementer report. The checked `remove_worktree` call is required on the valid RED path; the `EXIT` trap preserves the original gate status and also fails the gate if fallback cleanup cannot remove or unregister its worktree. Do not edit either allowlisted file until this result is observed.

- [ ] **Step 2: Make the minimal two-fixture repair**

In `HeadlessResourceScope`, insert the explicit destination-parent creation immediately after the existing `testMapVersions` assignment and before it reads prior contents or copies the editor resource:

```java
testMapVersions = testResourceDirectory.resolve("xml/mapVersions.xml");
Files.createDirectories(testMapVersions.getParent());
previousMapVersions = Files.exists(testMapVersions) ? Files.readAllBytes(testMapVersions) : null;
Files.copy(editorMapVersions, testMapVersions, StandardCopyOption.REPLACE_EXISTING);
```

Do not move preference setup, change the restore helper, or change paths.

In `ConnectorSnapshotFactoryShould.tearDownHeadlessResources()`, retain the original `close()` call but invoke it only when setup assigned the static field:

```java
@org.junit.AfterClass
public static void tearDownHeadlessResources() throws Exception {
    if (headless != null) {
        headless.close();
    }
}
```

Do not catch or suppress an exception thrown by a non-null scope's `close()`.

- [ ] **Step 3: Run focused green fixture coverage in the active worktree**

Run the connector fixture after the two source changes:

```bash
export JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"
gradle -p /data/home/guest/Development/freeplane/.worktrees/graph-batch-d-lifecycle-recovery \
  :freeplane_plugin_graph:test \
  --tests '*ConnectorSnapshotFactoryShould' \
  -PTestLoggingFull --rerun-tasks
```

Require a zero exit. Inspect `freeplane_plugin_graph/build/test-results/test/TEST-org.freeplane.plugin.graph.adapter.ConnectorSnapshotFactoryShould.xml` and require zero failures and zero errors.

- [ ] **Step 4: Prove the teardown guard with a one-mechanism mutant, then verify in a separate clean checkout**

Run this entire block as one shell invocation. It uses one fresh detached patched worktree only for the mutant, removes it after exact byte restoration, then creates a second fresh detached patched worktree for the full suite. The full-suite proof must never reuse generated resources written before the mutant failure.

```bash
set -euo pipefail
source=/data/home/guest/Development/freeplane/.worktrees/graph-batch-d-lifecycle-recovery
base=$(git -C "$source" rev-parse HEAD)
patch=$(mktemp)
backup=$(mktemp)
mutant_tmp=
verify_tmp=
cleanup() {
  local status=$?
  if ! rm -f "$patch" "$backup"; then
    status=1
  fi
  for path in "$mutant_tmp" "$verify_tmp"; do
    if [ -n "$path" ]; then
      if ! git -C "$source" worktree remove --force "$path"; then
        status=1
      fi
      if [ -e "$path" ]; then
        status=1
      fi
      local registered
      if ! registered=$(git -C "$source" worktree list --porcelain); then
        status=1
      elif printf '%s\n' "$registered" | rg -Fqx "worktree $path"; then
        status=1
      fi
    fi
  done
  exit "$status"
}
trap cleanup EXIT
remove_worktree() {
  path=$1
  git -C "$source" worktree remove --force "$path"
  test ! -e "$path"
  if git -C "$source" worktree list --porcelain | rg -Fqx "worktree $path"; then
    echo "temporary worktree remains registered: $path" >&2
    return 1
  fi
}
apply_patch_tree() {
  path=$1
  git -C "$source" worktree add --detach "$path" "$base"
  git -C "$path" apply --check "$patch"
  git -C "$path" apply "$patch"
  cmp "$source/freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/MapSnapshotFactoryShould.java" \
    "$path/freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/MapSnapshotFactoryShould.java"
  cmp "$source/freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/ConnectorSnapshotFactoryShould.java" \
    "$path/freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/ConnectorSnapshotFactoryShould.java"
}
git -C "$source" diff -- \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/MapSnapshotFactoryShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/ConnectorSnapshotFactoryShould.java > "$patch"
test -s "$patch"

mutant_tmp=$(mktemp -d /tmp/freeplane-headless-resource-mutant.XXXXXX)
rmdir "$mutant_tmp"
apply_patch_tree "$mutant_tmp"
source_file=freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/MapSnapshotFactoryShould.java
cp "$mutant_tmp/$source_file" "$backup"
clean_sha=$(sha256sum "$mutant_tmp/$source_file" | cut -d' ' -f1)
node --input-type=module - "$mutant_tmp/$source_file" <<'NODE'
import { readFileSync, writeFileSync } from 'node:fs';
const path = process.argv[2];
const source = readFileSync(path, 'utf8');
const target = '            Files.createDirectories(testMapVersions.getParent());\n';
if (source.split(target).length - 1 !== 1) {
  throw new Error('expected exactly one map-version parent-creation statement');
}
writeFileSync(path, source.replace(target, '            // MUTANT: leave the map-version parent absent.\n'));
NODE
mutant_sha=$(sha256sum "$mutant_tmp/$source_file" | cut -d' ' -f1)
test "$clean_sha" != "$mutant_sha"
diff -u "$backup" "$mutant_tmp/$source_file" > /tmp/freeplane-headless-resource-mutant.diff || true
hunks=$(rg -c '^@@' /tmp/freeplane-headless-resource-mutant.diff || true)
test "$hunks" -eq 1
set +e
(
  cd "$mutant_tmp"
  export JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu
  export PATH="$JAVA_HOME/bin:$PATH"
  gradle :freeplane_plugin_graph:test \
    --tests '*ConnectorSnapshotFactoryShould' \
    -PTestLoggingFull --rerun-tasks
) > /tmp/freeplane-headless-resource-mutant.log 2>&1
status=$?
set -e
test "$status" -ne 0
rg -q 'NoSuchFileException: .*mapVersions\.xml' /tmp/freeplane-headless-resource-mutant.log
if rg -q 'NullPointerException' /tmp/freeplane-headless-resource-mutant.log; then
  echo 'teardown guard did not preserve the primary setup failure' >&2
  exit 1
fi
node --input-type=module - \
  "$mutant_tmp/freeplane_plugin_graph/build/test-results/test/TEST-org.freeplane.plugin.graph.adapter.ConnectorSnapshotFactoryShould.xml" <<'NODE'
import { readFileSync } from 'node:fs';
const path = process.argv[2];
const xml = readFileSync(path, 'utf8');
const suite = xml.match(/<testsuite\b[^>]*>/)?.[0];
if (suite === undefined) throw new Error('missing ConnectorSnapshotFactoryShould testsuite');
const attributes = (name) => Number(suite.match(new RegExp(`${name}="(\\d+)"`))?.[1]);
for (const [name, expected] of Object.entries({ tests: 1, failures: 1, errors: 0, skipped: 0 })) {
  if (attributes(name) !== expected) throw new Error(`expected ${name}=${expected}, found ${attributes(name)}`);
}
const cases = [...xml.matchAll(/<testcase\b([^>]*)>/g)];
if (cases.length !== 1 || !/name="classMethod"/.test(cases[0][1])) {
  throw new Error('expected exactly one classMethod testcase');
}
const failures = [...xml.matchAll(/<failure\b([^>]*)>([\s\S]*?)<\/failure>/g)];
if (failures.length !== 1 || !/type="java\.nio\.file\.NoSuchFileException"/.test(failures[0][1])) {
  throw new Error('expected exactly one NoSuchFileException failure');
}
if (!/NoSuchFileException: .*mapVersions\.xml/.test(failures[0][2])) {
  throw new Error('sole failure is not rooted at mapVersions.xml');
}
if (/<error\b|NullPointerException/.test(xml)) {
  throw new Error('mutant reported a secondary error');
}
NODE
cp "$backup" "$mutant_tmp/$source_file"
cmp "$backup" "$mutant_tmp/$source_file"
test "$clean_sha" = "$(sha256sum "$mutant_tmp/$source_file" | cut -d' ' -f1)"
git -C "$mutant_tmp" diff --check
remove_worktree "$mutant_tmp"
mutant_tmp=

verify_tmp=$(mktemp -d /tmp/freeplane-headless-resource-verify.XXXXXX)
rmdir "$verify_tmp"
apply_patch_tree "$verify_tmp"
test ! -e "$verify_tmp/freeplane_plugin_graph/build/resources/test/xml/mapVersions.xml"
(
  cd "$verify_tmp"
  export JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu
  export PATH="$JAVA_HOME/bin:$PATH"
  gradle :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks
)
node --input-type=module - "$verify_tmp/freeplane_plugin_graph/build/test-results/test" <<'NODE'
import { readFileSync, readdirSync } from 'node:fs';
const directory = process.argv[2];
const totals = { tests: 0, failures: 0, errors: 0, skipped: 0 };
for (const name of readdirSync(directory).filter((file) => /^TEST-.*\.xml$/.test(file))) {
  const text = readFileSync(`${directory}/${name}`, 'utf8');
  const suite = text.match(/<testsuite\b[^>]*>/);
  if (suite === null) continue;
  for (const key of Object.keys(totals)) {
    const value = suite[0].match(new RegExp(`${key}="(\\d+)"`));
    totals[key] += value === null ? 0 : Number(value[1]);
  }
}
console.log(JSON.stringify(totals));
if (totals.tests !== 434 || totals.failures !== 0 || totals.errors !== 0 || totals.skipped !== 2) {
  process.exit(1);
}
NODE
git -C "$verify_tmp" diff --check
git -C "$source" diff --check
remove_worktree "$verify_tmp"
verify_tmp=
```

The mutant must retain only the primary `NoSuchFileException`; any null-pointer error, missing one-hunk proof, source-byte drift, temporary-worktree cleanup failure, or manually created resource path invalidates the evidence. The second worktree's full suite must report exactly 434 tests, 0 failures, 0 errors, and 2 skips.

- [ ] **Step 5: Carry predecessor evidence through supported task and final-review packages**

This step is in the dispatched task body because the installed `task-brief` helper may omit preceding Global Constraints. It records controller-only review protocol rather than an implementer action: the implementer must read the predecessor artifacts and report the F-1/F-2 status described below, but must not create review packages, render reviewer prompts, or alter ignored SDD evidence. Although the protocol is stated here so it reaches the task brief, the controller executes its post-commit package blocks only after Step 6 has created the implementation commit.

**Controller-only complete task-brief protocol:** before recording the task base or any implementer dispatch intent, run the installed extractor into a run-root task-only artifact, then prepend the parsed Global Constraints block and validate the resulting complete brief. The raw extractor output must never be dispatched.

```bash
set -euo pipefail
source=/data/home/guest/Development/freeplane/.worktrees/graph-batch-d-lifecycle-recovery
plan=$source/docs/superpowers/plans/2026-08-17-headless-resource-scope-clean-suite-remediation.md
run=$source/.superpowers/sdd/headless-resource-scope-clean-suite-remediation-v1
skill=/home/henry/.pi/agent/skills/subagent-driven-development/scripts
task_only=$run/task-1-task-only.md
brief=$run/task-1-brief.md
"$skill/task-brief" "$plan" 1 "$task_only" >/dev/null
node --input-type=module - "$plan" "$task_only" "$brief" <<'NODE'
import { readFileSync, writeFileSync } from 'node:fs';
import { parsePlanText } from '/home/henry/.pi/agent/skills/subagent-driven-development/scripts/lib/plan-policy.mjs';
const [planPath, taskOnlyPath, outputPath] = process.argv.slice(2);
const plan = readFileSync(planPath, 'utf8');
const taskOnly = readFileSync(taskOnlyPath, 'utf8');
const parsed = parsePlanText(plan, planPath);
if (parsed.tasks.length !== 1 || parsed.tasks[0].number !== 1 || parsed.globalConstraints === null) {
  throw new Error('expected one Task 1 and a Global Constraints section');
}
const globalStart = plan.indexOf('## Global Constraints\n');
const taskStart = plan.indexOf('## Task 1:');
if (globalStart < 0 || taskStart < 0 || taskStart <= globalStart) {
  throw new Error('could not locate Global Constraints and Task 1 boundaries');
}
if (taskOnly !== plan.slice(taskStart)) {
  throw new Error('installed task-brief bytes differ from the parsed Task 1 suffix');
}
const brief = plan.slice(globalStart, taskStart) + taskOnly;
if ((brief.match(/^## Global Constraints$/gm) ?? []).length !== 1 ||
    !brief.includes(parsed.globalConstraints) || !brief.includes('## Task 1:')) {
  throw new Error('rebuilt brief does not contain the complete parsed Global Constraints block');
}
if (Buffer.byteLength(brief, 'utf8') > 256 * 1024) {
  throw new Error('complete task brief exceeds the 256 KiB artifact bound');
}
writeFileSync(outputPath, brief);
NODE
test -s "$brief"
rg -q '^## Global Constraints$' "$brief"
rg -q '^## Task 1: Repair Headless Resource Fixture Bootstrap$' "$brief"
rg -q -F -- '- Modify only `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/MapSnapshotFactoryShould.java`' "$brief"
printf 'complete-task-brief-sha256='; sha256sum "$brief" | cut -d' ' -f1
```

All implementer, task-reviewer, fixer/re-reviewer, and final-reviewer contexts must use this complete `$run/task-1-brief.md`; no role may use the raw `$run/task-1-task-only.md`. The controller records the brief digest and the exact command outcome in its run-root audit artifacts before dispatch.

Before the implementer dispatch, the controller records the Task 1 Git base in `<new-run-root>/task-1-base.sha` using the active HEAD. After the two-file implementation commit, the controller uses that recorded base and the implementation commit SHA as the task-review range. It must not derive the task base from `HEAD^` or expand the task reviewer beyond that one completed task.

```bash
set -euo pipefail
source=/data/home/guest/Development/freeplane/.worktrees/graph-batch-d-lifecycle-recovery
run=$source/.superpowers/sdd/headless-resource-scope-clean-suite-remediation-v1
task_base=$(git -C "$source" rev-parse HEAD)
printf '%s\n' "$task_base" > "$run/task-1-base.sha"
test "$task_base" = "$(< "$run/task-1-base.sha")"
```

The predecessor sources are immutable and must be read without modification:

- `.superpowers/sdd/asynchronous-reset-observability-recovery-v1/state.json`
- `.superpowers/sdd/asynchronous-reset-observability-recovery-v1/final-reviewer-report.md`
- `.superpowers/sdd/asynchronous-reset-observability-recovery-v1/task-1-rereview-round-1-report.md`
- `.superpowers/sdd/asynchronous-reset-observability-recovery-v1/final-f2-reproduction.md`

The implementer report must state that predecessor F-1, the settling lifecycle restart defect, was already fixed and this task does not change lifecycle sources. It must state that predecessor F-2 is resolved only when this task's fresh detached full-suite proof succeeds.

**Controller-only task-review package protocol:** after the implementation commit and before task-review dispatch, create the normal task-only diff with `review-package`, then create a bounded bundle beneath the new run root. The bundle must embed the normal task diff, the current implementer report, a generated `predecessor-ledger.md`, and byte-for-byte text of all four predecessor sources, each named and SHA-256-pinned. It is not sufficient to name paths that a reviewer may or may not read.

```bash
set -euo pipefail
source=/data/home/guest/Development/freeplane/.worktrees/graph-batch-d-lifecycle-recovery
plan=$source/docs/superpowers/plans/2026-08-17-headless-resource-scope-clean-suite-remediation.md
run=$source/.superpowers/sdd/headless-resource-scope-clean-suite-remediation-v1
predecessor=$source/.superpowers/sdd/asynchronous-reset-observability-recovery-v1
skill=/home/henry/.pi/agent/skills/subagent-driven-development/scripts
task_base=$(< "$run/task-1-base.sha")
task_head=$(git -C "$source" rev-parse HEAD)
test "$task_base" = "$(git -C "$source" rev-parse "$task_base")"
git -C "$source" merge-base --is-ancestor "$task_base" "$task_head"
test "$(git -C "$source" rev-list --count "$task_base..$task_head")" -eq 1
task_diff=$run/task-1-review.diff
task_report=$run/task-1-implementer-report.md
task_bundle=$run/task-1-review-package.md
predecessor_ledger=$run/predecessor-ledger.md
(
  cd "$source"
  "$skill/review-package" "$plan" "$task_base" "$task_head" "$task_diff"
)
node --input-type=module - "$task_bundle" "$predecessor_ledger" "$task_diff" "$task_report" \
  "$predecessor/state.json" \
  "$predecessor/final-reviewer-report.md" \
  "$predecessor/task-1-rereview-round-1-report.md" \
  "$predecessor/final-f2-reproduction.md" <<'NODE'
import { createHash } from 'node:crypto';
import { readFileSync, writeFileSync } from 'node:fs';
const [bundlePath, ledgerPath, diffPath, implementerReportPath, ...sourcePaths] = process.argv.slice(2);
const digest = (bytes) => createHash('sha256').update(bytes).digest('hex');
const implementerReport = readFileSync(implementerReportPath, 'utf8');
const implementerReportSha256 = digest(readFileSync(implementerReportPath));
const sources = sourcePaths.map((path) => ({
  path,
  text: readFileSync(path, 'utf8'),
  sha256: digest(readFileSync(path)),
}));
const ledger = [
  '# Inherited predecessor findings',
  '',
  '- F-1 | recordedDisposition: fixed | evidence: predecessor re-review confirms the lifecycle repair; Task 1 must not alter lifecycle sources.',
  '- F-2 | recordedDisposition: open-before-task | evidence: predecessor final review and reproduction show the clean-suite fixture failure; Task 1 resolves it only with the fresh full-suite proof.',
  '',
  '## Immutable sources',
  ...sources.map(({ path, sha256 }) => `- ${path} | sha256: ${sha256}`),
  '',
].join('\n');
const sections = [
  '# Task 1 Review Package',
  '',
  'Scope: review only the task range supplied in Dispatch Context. Do not expand into the original Batch D range.',
  '',
  '## Task diff',
  readFileSync(diffPath, 'utf8'),
  '## Current implementer report',
  `SHA-256: ${implementerReportSha256}`,
  '```text',
  implementerReport,
  '```',
  '## Predecessor ledger',
  ledger,
  ...sources.flatMap(({ path, text, sha256 }) => [
    `## Immutable predecessor source: ${path}`,
    `SHA-256: ${sha256}`,
    '```text',
    text,
    '```',
  ]),
];
const bundle = `${sections.join('\n')}\n`;
if (Buffer.byteLength(bundle, 'utf8') > 128 * 1024) throw new Error('task-review bundle exceeds 128 KiB');
writeFileSync(ledgerPath, ledger);
writeFileSync(bundlePath, bundle);
if (!bundle.includes(implementerReport) || !bundle.includes(implementerReportSha256)) {
  throw new Error('bundle did not preserve the implementer report');
}
for (const source of sources) {
  if (!bundle.includes(source.text) || !bundle.includes(source.sha256)) {
    throw new Error(`bundle did not preserve ${source.path}`);
  }
}
NODE
test -s "$task_diff"
test -s "$task_report"
test -s "$task_bundle"
test -s "$predecessor_ledger"
test "$(wc -c < "$task_bundle")" -le $((128 * 1024))

task_context=$run/task-1-reviewer-context.json
task_prompt=$run/task-1-reviewer-prompt.md
reviewer_tier=$("$skill/sdd-state" role-tier --implementer advanced --role task-reviewer | jq -r '.tier')
node --input-type=module - "$task_context" "$source" "$run" "$task_base" "$task_head" "$task_bundle" <<'NODE'
import { writeFileSync } from 'node:fs';
const [path, worktree, runRoot, baseSha, headSha, reviewPackagePath] = process.argv.slice(2);
writeFileSync(path, `${JSON.stringify({
  worktree,
  runRoot,
  task: 1,
  baseSha,
  headSha,
  briefPath: `${runRoot}/task-1-brief.md`,
  reportPath: `${runRoot}/task-1-task-reviewer-report.md`,
  reviewPackagePath,
}, null, 2)}\n`);
NODE
"$skill/sdd-state" render-prompt --tier "$reviewer_tier" --role task-reviewer \
  --context "$task_context" --output "$task_prompt"
node --input-type=module - "$task_context" "$task_base" "$task_head" "$task_bundle" <<'NODE'
import { readFileSync } from 'node:fs';
const [path, baseSha, headSha, reviewPackagePath] = process.argv.slice(2);
const context = JSON.parse(readFileSync(path, 'utf8'));
if (context.baseSha !== baseSha || context.headSha !== headSha || context.reviewPackagePath !== reviewPackagePath) {
  throw new Error('task-reviewer context does not pin the intended range and package');
}
NODE
rg -F -- "$task_bundle" "$task_prompt"
```

The task-reviewer prompt therefore uses the supported `reviewPackagePath` field with `baseSha` = `task_base` and `headSha` = `task_head`; it never supplies an unsupported task-reviewer `contextPath` or `ledgerPath`. The task reviewer reviews only `task_base..task_head`, verifies F-2 from the bundle, the current task diff, and the current implementer report, confirms its scope does not modify F-1 lifecycle files, and adds this non-finding section after its normal report schema:

```text
PREDECESSOR RECONCILIATION:
- F-1: inherited fixed; no lifecycle source is in the Task 1 range.
- F-2: resolved | cannot-verify, with the clean detached full-suite evidence path.
```

The reviewer must not re-report F-1 or F-2 as new native task findings solely because they originated in the terminal predecessor run. New defects found in Task 1 use ordinary current-run finding IDs and enter the native SDD state ledger normally.

**Controller-only final-review package protocol:** after Task 1 is approved, create a fresh full-range diff from `9248c6e227bb82fab8e6139f46db37b62174309f` to final HEAD. The final package embeds that full diff, the checked task-review bundle, the task-reviewer report, and a combined final ledger holding both the current native state and the inherited predecessor ledger. Build and verify it as follows before recording the final-review intent:

```bash
set -euo pipefail
source=/data/home/guest/Development/freeplane/.worktrees/graph-batch-d-lifecycle-recovery
plan=$source/docs/superpowers/plans/2026-08-17-headless-resource-scope-clean-suite-remediation.md
run=$source/.superpowers/sdd/headless-resource-scope-clean-suite-remediation-v1
skill=/home/henry/.pi/agent/skills/subagent-driven-development/scripts
final_base=9248c6e227bb82fab8e6139f46db37b62174309f
final_head=$(git -C "$source" rev-parse HEAD)
final_diff=$run/final-review.diff
final_bundle=$run/final-review-package.md
final_ledger=$run/final-review-ledger.md
task_bundle=$run/task-1-review-package.md
task_review_report=$run/task-1-task-reviewer-report.md
native_state=$run/state.json
predecessor_ledger=$run/predecessor-ledger.md
(
  cd "$source"
  "$skill/review-package" "$plan" "$final_base" "$final_head" "$final_diff"
)
node --input-type=module - "$final_bundle" "$final_ledger" "$final_diff" "$task_bundle" \
  "$task_review_report" "$native_state" "$predecessor_ledger" <<'NODE'
import { createHash } from 'node:crypto';
import { readFileSync, writeFileSync } from 'node:fs';
const [bundlePath, ledgerPath, diffPath, taskBundlePath, taskReviewReportPath, nativeStatePath, predecessorLedgerPath] = process.argv.slice(2);
const digest = (bytes) => createHash('sha256').update(bytes).digest('hex');
const read = (path) => {
  const bytes = readFileSync(path);
  return { path, text: bytes.toString('utf8'), sha256: digest(bytes) };
};
const finalDiff = read(diffPath);
const taskBundle = read(taskBundlePath);
const taskReviewReport = read(taskReviewReportPath);
const nativeState = read(nativeStatePath);
const predecessorLedger = read(predecessorLedgerPath);
const ledger = [
  '# Final Review Ledger',
  '',
  '## Current native SDD state',
  `SHA-256: ${nativeState.sha256}`,
  '```json',
  nativeState.text,
  '```',
  '## Inherited predecessor ledger',
  `SHA-256: ${predecessorLedger.sha256}`,
  '```text',
  predecessorLedger.text,
  '```',
].join('\n');
writeFileSync(ledgerPath, `${ledger}\n`);
const sections = [
  '# Final Review Package',
  '',
  'Scope: review the full original Batch D range supplied in Dispatch Context.',
  '',
  '## Full-range diff',
  `SHA-256: ${finalDiff.sha256}`,
  finalDiff.text,
  '## Task review bundle',
  `SHA-256: ${taskBundle.sha256}`,
  taskBundle.text,
  '## Task reviewer report',
  `SHA-256: ${taskReviewReport.sha256}`,
  '```text',
  taskReviewReport.text,
  '```',
  '## Combined final ledger',
  ledger,
];
const bundle = `${sections.join('\n')}\n`;
if (Buffer.byteLength(bundle, 'utf8') > 512 * 1024) throw new Error('final-review bundle exceeds 512 KiB');
writeFileSync(bundlePath, bundle);
for (const document of [finalDiff, taskBundle, taskReviewReport, nativeState, predecessorLedger]) {
  if (!bundle.includes(document.text) || !bundle.includes(document.sha256)) {
    throw new Error(`final bundle did not preserve ${document.path}`);
  }
}
NODE
test -s "$final_diff"
test -s "$final_bundle"
test -s "$final_ledger"
test "$(wc -c < "$final_bundle")" -le $((512 * 1024))

final_context=$run/final-reviewer-context.json
final_prompt=$run/final-reviewer-prompt.md
final_tier=$("$skill/sdd-state" role-tier --implementer advanced --role final | jq -r '.tier')
node --input-type=module - "$final_context" "$source" "$run" "$final_base" "$final_head" "$final_bundle" "$final_ledger" <<'NODE'
import { writeFileSync } from 'node:fs';
const [path, worktree, runRoot, baseSha, headSha, reviewPackagePath, ledgerPath] = process.argv.slice(2);
writeFileSync(path, `${JSON.stringify({
  worktree,
  runRoot,
  task: 1,
  baseSha,
  headSha,
  briefPath: `${runRoot}/task-1-brief.md`,
  reportPath: `${runRoot}/final-reviewer-report.md`,
  reviewPackagePath,
  ledgerPath,
}, null, 2)}\n`);
NODE
"$skill/sdd-state" render-prompt --tier "$final_tier" --role final-reviewer \
  --context "$final_context" --output "$final_prompt"
node --input-type=module - "$final_context" "$final_base" "$final_head" "$final_bundle" "$final_ledger" <<'NODE'
import { readFileSync } from 'node:fs';
const [path, baseSha, headSha, reviewPackagePath, ledgerPath] = process.argv.slice(2);
const context = JSON.parse(readFileSync(path, 'utf8'));
if (context.baseSha !== baseSha || context.headSha !== headSha ||
    context.reviewPackagePath !== reviewPackagePath || context.ledgerPath !== ledgerPath) {
  throw new Error('final-reviewer context does not pin the intended range and evidence');
}
NODE
rg -F -- "$final_bundle" "$final_prompt"
rg -F -- "$final_ledger" "$final_prompt"
```

The final reviewer, and only the final reviewer, inspects the full original Batch D range. Its required `LEDGER RECONCILIATION` must cover F-1 and F-2 from the combined ledger as well as all native current-run findings: it confirms F-1 is fixed in final HEAD and was not reintroduced by a later change in the range, and determines F-2 from the fresh full-suite proof. A residual is reported as an ordinary load-bearing final finding; a reconciliation note does not fabricate or mutate inherited entries in the new run's native state ledger.

- [ ] **Step 6: Audit exact scope and commit the repair**

After the detached-worktree trap has removed its temporary checkout, verify the active worktree changes only the two allowlisted files. Do not stage the old plan, any SDD artifact, the committed design, generated build output, or temporary logs.

```bash
set -euo pipefail
cd /data/home/guest/Development/freeplane/.worktrees/graph-batch-d-lifecycle-recovery
allowed=$(mktemp)
actual=$(mktemp)
trap 'rm -f "$allowed" "$actual"' EXIT
printf '%s\n' \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/ConnectorSnapshotFactoryShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/MapSnapshotFactoryShould.java \
  | sort > "$allowed"
git diff --name-only | sort -u > "$actual"
diff -u "$allowed" "$actual"
git diff --cached --name-only | sort -u > "$actual"
test ! -s "$actual"
git diff --check
git add -- \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/ConnectorSnapshotFactoryShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/MapSnapshotFactoryShould.java
git diff --cached --name-only | sort -u > "$actual"
diff -u "$allowed" "$actual"
git diff --cached --check
git commit -m "2026-08-10-graph-workspace: Fix headless test resource setup"
git show --format='' --name-only HEAD | sort -u > "$actual"
diff -u "$allowed" "$actual"
git show --stat --oneline HEAD
```

The implementer report must identify the baseline red failures, focused green result, expected mutant failure without a null-pointer teardown failure, clean detached full-suite XML totals, exact two-file scope checks, and the commit SHA.
