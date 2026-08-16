# Graph Workspace Task 22 Acquisition Barrier Remediation Plan v2

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox syntax for readability; `state.json` is the canonical tracker.

**Goal:** Preserve same-ID map acquisition barriers across document removal and reactivation so the latest active registration is retried only after the old loading entry settles.

**Architecture:** Keep the existing `WorkspaceMapCoordinator` public API and EDT ownership. Replace registration-local deferred state with one coordinator-owned barrier map keyed by `MapReferenceId`; each barrier identifies its source acquisition and the latest registration waiting behind it. Source completion closes stale ownership, clears the barrier, and retries only the current latest active registration.

**Tech Stack:** Java 8 source and bytecode, Zulu JDK 21.0.8, Gradle, JUnit 4, AssertJ, Mockito, `CompletableFuture`, and the existing Freeplane graph workspace adapter seams.

## Global Constraints

- Work only in `/data/home/guest/Development/freeplane/.worktrees/task-22-remediation-v2` on branch `task-22-remediation-v2`; preserve `/data/home/guest/Development/freeplane`, `.worktrees/task-20-21-layout`, `.worktrees/task-22`, and `.worktrees/task-22-remediation` unchanged.
- The original Task 22 run is terminal at `.worktrees/task-22/.superpowers/sdd/freeplane-graph-task-22-plan`, revision 35 `FINAL_BLOCKED`. The first successor run is terminal at `.worktrees/task-22-remediation/.superpowers/sdd/graph-workspace-task-22-remediation`, revision 7 `TASK_BLOCKED` because one redundant red test passed. Never edit, reopen, or dispatch into either state or progress ledger.
- The predecessor implementation HEAD is `369077d8bf049ad0bf528a9fc9a30433ad0be68e`; the original Graph Workspace merge base is `afba8436462753a38aef73f91bf21ba6715e8460`.
- The approved design is `docs/superpowers/specs/2026-08-16-graph-workspace-task-22-remediation-design.md`. The committed red fixtures are in `d49979173e`; the new implementation changes after this plan are allowlisted to exactly `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/WorkspaceMapCoordinator.java`.
- Plan and design documents, plus the committed red fixtures, exist before the new SDD run is initialized. Do not modify any build file, adapter, lease manager, projection value, public API, or unrelated test file. The existing `WorkspaceMapCoordinatorShould.java` red fixtures are part of the inherited baseline and must remain unchanged during implementation.
- Use `/home/henry/.sdkman/candidates/java/21.0.8-zulu` and `gradle -p /data/home/guest/Development/freeplane/.worktrees/task-22-remediation-v2`; use `gradle`, never Maven or the wrapper.
- Preserve the existing Task 22 contracts: capture remains EDT-scoped and immutable, snapshot failures remain capture-local, snapshots remain registration ordered, unchanged leases remain reusable, stale leases close once, queued callbacks after close are ignored, and `ProjectionBatcher` close/callback behavior remains unchanged.
- The coordinator must never ask `MapLeaseManager.acquire` for a changed path while an earlier same-ID acquisition is still loading. At most one coordinator acquisition barrier may exist for one `MapReferenceId`.
- Only the latest active registration may be retried after the old completion. Removal, inactivity, close, stale completion, exceptional completion, invalid lease identity, and repeated replacement updates must not resurrect an obsolete registration.
- Keep all state changes, lease-acquirer calls, completion handling, and document reconciliation on the supplied EDT seam. Do not add sleeps, elapsed-time synchronization, a public API, a second parallel lifecycle path, or a compatibility fallback.
- The committed red fixtures are strict TDD evidence. Run them against the inherited production implementation before editing production code and require all three to fail for early replacement acquisition. A compile failure, fixture failure, or unrelated failure is not valid RED evidence.
- Carry the prior ledger into the new run's review package and final review: F-1 snapshot retry is fixed, F-2 lifecycle/order coverage is fixed, F-3 is the open residual resolved by this plan, and F-4 batcher close synchronization is fixed. Also carry the first successor's blocked RED evidence and explain why its repeated-active-replacement test was removed from the new gate as already satisfied by the inherited implementation.
- Before completion, require focused tests, the full `:freeplane_plugin_graph:test` suite, XML counts with zero failures/errors, `git diff --check`, exact post-baseline implementation scope, and clean source status. The implementation commit must use `2026-08-10-graph-workspace: Preserve map acquisition barriers`.

## Task 1: Preserve Same-ID Acquisition Barriers

**Implementer tier:** Capable

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/WorkspaceMapCoordinator.java:1-end`
- Verify inherited tests: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/WorkspaceMapCoordinatorShould.java:442-680`

**Interfaces:**

- Consumes the existing package-private `WorkspaceMapCoordinator` constructor with injected `MapSnapshotFactory`, `MapLeaseManager`, `GraphWorkspaceStore`, `EdtExecutor`, and `LeaseAcquirer`.
- Consumes the existing `WorkspaceDocument`, `MapReference`, `MapReferenceId`, `MapLease`, `MapAvailability`, `ProjectionInput`, and listener-registration contracts without changing signatures.
- Produces a private coordinator-level same-ID acquisition barrier. The inherited test file already contains red regressions named `replacementAfterRemovalWaitsForOlderLoadToSettle`, `replacementAfterDeactivationWaitsForOlderLoadToSettle`, and `replacementAfterRemovalAndInactivityWaitsForOlderLoadToSettle`.

The fresh child must read the approved design and the predecessor artifacts before editing:

- `/data/home/guest/Development/freeplane/.worktrees/task-22-remediation-v2/docs/superpowers/specs/2026-08-16-graph-workspace-task-22-remediation-design.md`
- `/data/home/guest/Development/freeplane/.worktrees/task-22-remediation/.superpowers/sdd/graph-workspace-task-22-remediation/task-1-implementer-report.md`
- `/data/home/guest/Development/freeplane/.worktrees/task-22-remediation/.superpowers/sdd/graph-workspace-task-22-remediation/state.json`
- `/data/home/guest/Development/freeplane/.worktrees/task-22/.superpowers/sdd/freeplane-graph-task-22-plan/task-1-final-rereview-report.md`
- `/data/home/guest/Development/freeplane/.worktrees/task-22/.superpowers/sdd/freeplane-graph-task-22-plan/state.json`
- `/data/home/guest/Development/freeplane/.worktrees/task-22/.superpowers/sdd/freeplane-graph-task-22-plan/task-1-final-fix-report.md`
- `/data/home/guest/Development/freeplane/.worktrees/task-22/.superpowers/sdd/freeplane-graph-task-22-plan/task-1-final-review-report.md`

The first successor child demonstrated that the removal and direct deactivation regressions fail against inherited production, while the attempted repeated active replacement test passed because the inherited implementation already coalesces replacements while its current `Registration` remains present. The committed replacement `replacementAfterRemovalAndInactivityWaitsForOlderLoadToSettle` covers the missing composite lifecycle path and fails at inherited production. Do not weaken or remove the committed red fixtures.

### Step 1: Verify the committed RED fixtures before production edits

Run exactly these three tests against the inherited production at `369077d8` plus the committed red fixtures. Do not change production code or the test file. Require three tests completed, zero errors, and three failures whose assertion shows the replacement reference was acquired before the old future settled:

```bash
env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/task-22-remediation-v2 \
  :freeplane_plugin_graph:test \
  --tests '*WorkspaceMapCoordinatorShould.replacementAfterRemovalWaitsForOlderLoadToSettle' \
  --tests '*WorkspaceMapCoordinatorShould.replacementAfterDeactivationWaitsForOlderLoadToSettle' \
  --tests '*WorkspaceMapCoordinatorShould.replacementAfterRemovalAndInactivityWaitsForOlderLoadToSettle' \
  -PTestLoggingFull --rerun-tasks
```

If any named test passes, fails to compile, or fails for a different reason, stop with `BLOCKED` and do not edit production code. Record the XML totals and exact failure mechanism in the implementer report.

### Step 2: Introduce one coordinator-level barrier representation

Replace the registration-local lifetime of `acquisitionInFlight` and `deferredAcquisition` with one private coordinator-owned map keyed by `MapReferenceId`. The barrier representation must retain:

- the source `Registration` and its acquisition generation, so only the matching old completion can settle it;
- the latest current `Registration` to retry, which may be absent while the document is removed or inactive;
- no mutable document or lease ownership beyond the existing coordinator fields.

Create the barrier atomically before calling `leaseAcquirer.acquire`. `beginAcquireOnEdt` must refuse to begin a new acquisition when a barrier already exists for that ID. Keep `pendingCompletions` as the ownership set for non-null completion leases.

### Step 3: Update document reconciliation to preserve the barrier across state changes

Modify `installDocumentOnEdt` so every document transition consults the coordinator-level barrier, not only the immediately previous registration:

- unchanged active registration: retain its lease and make it the barrier's latest registration if a barrier exists;
- changed active registration: detach any old lease, create the new loading registration, and assign it as the barrier's latest registration when a barrier exists; otherwise queue its normal acquisition;
- inactive registration: detach any current lease, retain the barrier with no active retry target, and do not acquire;
- removed registration: detach any current lease, retain the barrier with no active retry target, and do not acquire;
- later reactivation or re-addition before settlement: assign the new active registration as the barrier's latest target and do not acquire early;
- repeated replacements: overwrite only the latest target; never preserve a queue of obsolete registrations.

Preserve the current stale-lease close list and registration insertion order. A barrier must survive removal/deactivation but must not survive its source completion or coordinator close.

### Step 4: Settle the source completion and retry only the current latest target

Update `finishAcquireOnEdt` so the matching source barrier is consumed regardless of whether the source registration is still current. Keep the existing lease identity and failure checks for a current completion. For a stale, invalid, or exceptional source completion:

- close a non-null stale lease exactly once outside `monitor`;
- remove the matching barrier before selecting a retry;
- inspect the current registration for the same ID after removal;
- retry only when the barrier's latest target is the current registration, it is active, the coordinator is open, and its reference is still the latest document identity;
- leave inactive, removed, closed, or obsolete targets without a retry.

For a completion that is still current and valid, preserve the current lease and availability behavior. For queued completion callbacks after close, preserve the existing claim/close guard. Ensure a synchronous acquirer failure still settles and clears the barrier so a later document event can acquire normally.

### Step 5: Run focused green and retained Task 22 coverage

Run the three lifecycle regressions, then the focused coordinator/batcher classes:

```bash
env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/task-22-remediation-v2 \
  :freeplane_plugin_graph:test \
  --tests '*WorkspaceMapCoordinatorShould' \
  --tests '*ProjectionBatcherShould' \
  -PTestLoggingFull --rerun-tasks
```

Require the new remove/deactivate/reactivate tests to pass, plus the inherited direct replacement, stale completion, close, ordering, adapter, and transient snapshot retry tests. Read the JUnit XML under `freeplane_plugin_graph/build/test-results/test` and record test, skipped, failure, and error totals.

### Step 6: Exercise the barrier regression and run the full graph suite

Temporarily mutate only the coordinator-level barrier guard/update so a later active registration is acquired immediately even while the source barrier exists. Run `replacementAfterRemovalWaitsForOlderLoadToSettle` and require it to fail with an early replacement acquisition or unreadable result. Restore the exact source immediately and verify the focused suite is green again. Do not leave mutant or diagnostic residue.

Then run the full graph-module suite:

```bash
env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/task-22-remediation-v2 \
  :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks
```

Require zero failures and zero errors, retain existing skips only, and inspect the complete XML aggregation. Run `git diff --check` after restoration.

### Step 7: Audit exact scope and commit the implementation

Before committing, verify the inherited source commits and committed red fixture remain untouched by the implementation:

```bash
git diff --check d49979173e..HEAD
git status --short --untracked-files=all
git diff --name-only d49979173e..HEAD | sort
```

Stage only the production file:

```bash
git add -- freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/WorkspaceMapCoordinator.java
git diff --cached --check
git commit -m "2026-08-10-graph-workspace: Preserve map acquisition barriers"
```

The implementation commit must be a new direct child, must not amend or rewrite inherited commits, and must contain only `WorkspaceMapCoordinator.java`. The implementer report must include the RED failure, focused and full test totals, mutant failure/restoration, final commit SHA, and any concerns.

### Step 8: Carry the predecessor ledger through review

The task-review package must include the original final-review and final-re-review reports, the first successor's blocked report and state summary, the approved design, the committed red-fixture commit, the full original merge-base-to-current-HEAD diff scope, and explicit ledger evidence for F-1 through F-4. The task reviewer must verify that F-1, F-2, and F-4 remain absent and that the new regressions resolve F-3. The controller must retain all four IDs in the new run's finding ledger and mark the three inherited fixed findings and F-3 fixed only with persisted evidence.

The new final-review package must require a fresh Frontier review of `afba8436462753a38aef73f91bf21ba6715e8460..HEAD`, not only the remediation commit, and must reconcile every carried ID. Completion is legal only after `SPEC: PASS`, `QUALITY: APPROVED`, no open load-bearing findings, full graph tests green, exact scope checks, and a clean worktree.
