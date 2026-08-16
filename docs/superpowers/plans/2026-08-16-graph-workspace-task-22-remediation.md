# Graph Workspace Task 22 Acquisition Barrier Remediation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox syntax for readability; `state.json` is the canonical tracker.

**Goal:** Preserve same-ID map acquisition barriers across document removal and reactivation so the latest active registration is retried only after the old loading entry settles.

**Architecture:** Keep the existing `WorkspaceMapCoordinator` public API and EDT ownership. Replace registration-local deferred state with one coordinator-owned barrier map keyed by `MapReferenceId`; each barrier identifies its source acquisition and the latest registration waiting behind it. Source completion closes stale ownership, clears the barrier, and retries only the current latest active registration.

**Tech Stack:** Java 8 source and bytecode, Zulu JDK 21.0.8, Gradle, JUnit 4, AssertJ, Mockito, `CompletableFuture`, and the existing Freeplane graph workspace adapter seams.

## Global Constraints

- Work only in `/data/home/guest/Development/freeplane/.worktrees/task-22-remediation` on branch `task-22-remediation`; preserve `/data/home/guest/Development/freeplane`, `.worktrees/task-20-21-layout`, and the predecessor `.worktrees/task-22` unchanged.
- The predecessor run at `.worktrees/task-22/.superpowers/sdd/freeplane-graph-task-22-plan` is terminal at revision 35 `FINAL_BLOCKED`; never edit, reopen, or dispatch into its `state.json`, `progress.md`, prompts, reports, or event files.
- The predecessor implementation HEAD is `369077d8bf049ad0bf528a9fc9a30433ad0be68e`; the original Graph Workspace merge base is `afba8436462753a38aef73f91bf21ba6715e8460`.
- The approved design is `docs/superpowers/specs/2026-08-16-graph-workspace-task-22-remediation-design.md`. The new implementation changes are allowlisted to exactly `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/WorkspaceMapCoordinator.java` and `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/WorkspaceMapCoordinatorShould.java`.
- Plan and design documents are committed before the new SDD run is initialized. Do not modify any build file, adapter, lease manager, projection value, public API, or unrelated test file.
- Use `/home/henry/.sdkman/candidates/java/21.0.8-zulu` and `gradle -p /data/home/guest/Development/freeplane/.worktrees/task-22-remediation`; use `gradle`, never Maven or the wrapper.
- Preserve the existing Task 22 contracts: capture remains EDT-scoped and immutable, snapshot failures remain capture-local, snapshots remain registration ordered, unchanged leases remain reusable, stale leases close once, queued callbacks after close are ignored, and `ProjectionBatcher` close/callback behavior remains unchanged.
- The coordinator must never ask `MapLeaseManager.acquire` for a changed path while an earlier same-ID acquisition is still loading. At most one coordinator acquisition barrier may exist for one `MapReferenceId`.
- Only the latest active registration may be retried after the old completion. Removal, inactivity, close, stale completion, exceptional completion, invalid lease identity, and repeated replacement updates must not resurrect an obsolete registration.
- Keep all state changes, lease-acquirer calls, completion handling, and document reconciliation on the supplied EDT seam. Do not add sleeps, elapsed-time synchronization, a public API, a second parallel lifecycle path, or a compatibility fallback.
- Follow strict TDD: add the named regression tests before production edits, run them against the inherited implementation, and record the expected behavioral failure. A compile failure, fixture failure, or unrelated failure is not valid RED evidence.
- Carry the predecessor ledger into the new run's review package and final review: F-1 snapshot retry is fixed, F-2 lifecycle/order coverage is fixed, F-3 is the open residual resolved by this plan, and F-4 batcher close synchronization is fixed. The new task reviewer and final reviewer must reconcile all four IDs against the original merge-base-to-new-HEAD range.
- Before completion, require focused tests, the full `:freeplane_plugin_graph:test` suite, XML counts with zero failures/errors, `git diff --check`, exact implementation scope, and clean source status. The implementation commit must use `2026-08-10-graph-workspace: Preserve map acquisition barriers`.

## Task 1: Preserve Same-ID Acquisition Barriers

**Implementer tier:** Capable

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/WorkspaceMapCoordinator.java:1-end`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/WorkspaceMapCoordinatorShould.java:1-end`

**Interfaces:**

- Consumes the existing package-private `WorkspaceMapCoordinator` constructor with injected `MapSnapshotFactory`, `MapLeaseManager`, `GraphWorkspaceStore`, `EdtExecutor`, and `LeaseAcquirer`.
- Consumes the existing `WorkspaceDocument`, `MapReference`, `MapReferenceId`, `MapLease`, `MapAvailability`, `ProjectionInput`, and listener-registration contracts without changing signatures.
- Produces a private coordinator-level same-ID acquisition barrier and deterministic regression coverage for removal, deactivation, reactivation, repeated replacement, stale completion, close, and capture recovery.

The fresh child must read the approved design and the predecessor artifacts before editing:

- `.worktrees/task-22/.superpowers/sdd/freeplane-graph-task-22-plan/task-1-final-rereview-report.md`
- `.worktrees/task-22/.superpowers/sdd/freeplane-graph-task-22-plan/state.json`
- `.worktrees/task-22/.superpowers/sdd/freeplane-graph-task-22-plan/task-1-final-fix-report.md`
- `.worktrees/task-22/.superpowers/sdd/freeplane-graph-task-22-plan/task-1-final-review-report.md`
- `.worktrees/task-22/.superpowers/sdd/freeplane-graph-task-22-plan/task-1-brief.md`

The predecessor final re-review found F-3 at the current registration branch: a deferred marker is lost when an in-flight registration is removed or deactivated, so a later active same-ID changed-path registration reaches `beginAcquireOnEdt` before the original manager load settles. The predecessor direct replacement test does not cover this lifecycle sequence. Do not repeat the registration-local marker design as the only barrier.

### Step 1: Add failing lifecycle regressions before production edits

Add three focused tests to `WorkspaceMapCoordinatorShould.java` using the existing `TestEdt`, workspace listener capture, immutable document fixtures, and `FakeLease` helpers. Do not change `WorkspaceMapCoordinator.java` before running these tests.

1. `replacementAfterRemovalWaitsForOlderLoadToSettle`: start an initial active registration with a pending `CompletableFuture<MapLease>`. Publish a document with the map ID removed, drain the EDT, then publish a document containing the same ID active at a changed URI, drain the EDT, and assert the lease-acquirer call list still contains only the initial reference. Complete the original future with a stale lease, drain the EDT, and assert the call list is exactly initial then replacement, the stale lease closes once, and the replacement lease is not closed. Capture the current document and assert the replacement availability is `AVAILABLE` and its snapshot is present.

2. `replacementAfterDeactivationWaitsForOlderLoadToSettle`: repeat the same sequence through an inactive `MapReference` with the same ID before reactivating a changed URI. Assert no acquisition occurs while inactive or before the old completion, then assert exactly one latest replacement acquisition and a usable available capture after settlement.

3. `repeatedReplacementBeforeOlderLoadSettlesAcquiresOnlyLatestReference`: publish two different active changed-URI replacements before completing the initial future. Assert neither replacement is acquired early. Complete the initial future, drain the EDT, and assert only the final replacement is acquired and available; the intermediate replacement must never be passed to the acquirer.

Use the existing `mockDocumentChangedEvent`, `workspace`, `registration`, `batch`, and snapshot helpers where possible. Ensure the lease acquirer rejects a changed reference if the old future is not complete, modeling `MapLeaseManager.acquire`'s same-ID loading-entry guard. The test must fail against the inherited `369077d8` implementation because the later active replacement is attempted early or remains unreadable. Record the exact focused RED command and failure mechanism before editing production code:

```bash
env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/task-22-remediation \
  :freeplane_plugin_graph:test \
  --tests '*WorkspaceMapCoordinatorShould.replacementAfterRemovalWaitsForOlderLoadToSettle' \
  --tests '*WorkspaceMapCoordinatorShould.replacementAfterDeactivationWaitsForOlderLoadToSettle' \
  --tests '*WorkspaceMapCoordinatorShould.repeatedReplacementBeforeOlderLoadSettlesAcquiresOnlyLatestReference' \
  -PTestLoggingFull --rerun-tasks
```

If any named test passes at the inherited implementation, fails to compile, or fails for a different reason, stop with `BLOCKED` and do not edit production code.

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

Run the three new tests, then the focused coordinator/batcher classes:

```bash
env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/task-22-remediation \
  :freeplane_plugin_graph:test \
  --tests '*WorkspaceMapCoordinatorShould' \
  --tests '*ProjectionBatcherShould' \
  -PTestLoggingFull --rerun-tasks
```

Require the new remove/deactivate/reactivate and repeated-replacement tests to pass, plus the inherited direct replacement, stale completion, close, ordering, adapter, and transient snapshot retry tests. Read the JUnit XML under `freeplane_plugin_graph/build/test-results/test` and record test, skipped, failure, and error totals.

### Step 6: Exercise the barrier regression and run the full graph suite

Temporarily mutate only the coordinator-level barrier guard/update so a later active registration is acquired immediately even while the source barrier exists. Run `replacementAfterRemovalWaitsForOlderLoadToSettle` and require it to fail with an early replacement acquisition or unreadable result. Restore the exact source immediately and verify the focused suite is green again. Do not leave mutant or diagnostic residue.

Then run the full graph-module suite:

```bash
env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/task-22-remediation \
  :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks
```

Require zero failures and zero errors, retain existing skips only, and inspect the complete XML aggregation. Run `git diff --check` after restoration.

### Step 7: Audit exact scope and commit the implementation

Before committing, verify the inherited source commits remain untouched and only the two implementation allowlist files differ after the plan/design commits:

```bash
git diff --check 369077d8bf049ad0bf528a9fc9a30433ad0be68e..HEAD
git status --short --untracked-files=all
git diff --name-only 369077d8bf049ad0bf528a9fc9a30433ad0be68e..HEAD | sort
```

Stage only:

```bash
git add -- \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/WorkspaceMapCoordinator.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/WorkspaceMapCoordinatorShould.java
git diff --cached --check
git commit -m "2026-08-10-graph-workspace: Preserve map acquisition barriers"
```

The implementation commit must be a new direct child, must not amend or rewrite `369077d8`, and must contain exactly the two allowlisted implementation/test paths. The implementer report must include the RED failure, focused and full test totals, mutant failure/restoration, final commit SHA, and any concerns.

### Step 8: Carry the predecessor ledger through review

The task-review package must include the predecessor final-review and final-re-review reports, the predecessor state summary, the approved design, the full original merge-base-to-current-HEAD diff scope, and explicit ledger evidence for F-1 through F-4. The task reviewer must verify that F-1, F-2, and F-4 remain absent and that the new regressions resolve F-3. The controller must retain all four IDs in the new run's finding ledger and mark the three inherited fixed findings and F-3 fixed only with persisted evidence.

The new final-review package must require a fresh Frontier review of `afba8436462753a38aef73f91bf21ba6715e8460..HEAD`, not only the remediation commit, and must reconcile every carried ID. Completion is legal only after `SPEC: PASS`, `QUALITY: APPROVED`, no open load-bearing findings, full graph tests green, exact scope checks, and a clean worktree.
