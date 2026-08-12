# Map Lease Settlement Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Repair Graph Workspace Task 13 so reserved map leases serialize with lifecycle invalidation without blocking the EDT on a lock held across dependent future callbacks.

**Architecture:** Preserve the existing `settlementLock -> monitor` winner boundary and the single `PendingAcquire` reservation lifecycle. Lifecycle removal and manager-owned external reload will use a shared EDT try-or-defer protocol: contention records an exact typed invalidation and returns, while the outermost settlement unlock schedules deferred work for EDT revalidation. All future callbacks remain outside `monitor`, and no public API changes.

**Tech Stack:** Java 8 source/target, Zulu JDK 21.0.8, Gradle, JUnit 4, AssertJ, Mockito, `CompletableFuture`, `ReentrantLock`, deterministic latches/atomics/executor queues, Freeplane EDT/model APIs.

## Global Constraints

- Work only in `/data/home/guest/Development/freeplane/.worktrees/graph-workspace` on branch `2026-08-10-graph-workspace`.
- The prior SDD run is terminal at revision 224 `TASK_BLOCKED`; never edit or reopen its `state.json` or `progress.md`.
- This remediation is Task 13 only; do not implement Task 14 or later tasks.
- The implementation allowlist is exactly `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/MapLeaseManager.java` and `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/MapLeaseManagerShould.java`.
- Do not add production files, change public signatures, modify `freeplane_api`, use `MapView`, change launchers, or add `Import-Package: nothing.*`.
- Use `/home/henry/.sdkman/candidates/java/21.0.8-zulu` and `gradle -p /data/home/guest/Development/freeplane/.worktrees/graph-workspace`; preserve Java 8 bytecode with class major version 52.
- Keep Freeplane model access, lifecycle reconciliation, listener attachment/removal, and manager-owned model close on the EDT.
- Keep the single settlement winner mechanism and strict global lock order `settlementLock -> monitor`; no path may hold `monitor` while acquiring `settlementLock`.
- Never invoke `future.complete`, `future.completeExceptionally`, dependent completion callbacks, adapter listeners, the completion interceptor, or `EdtExecutor` while holding `monitor`.
- EDT invalidation must never block waiting for `settlementLock`; non-EDT model-null retry and close may wait, and callback reentrancy must remain safe.
- Preserve T13-F-1 through T13-F-5 and T13-F-7, exact ownership/state/event ordering, viewless loading, listener identity, reference counts, rollback, and zero-count teardown.
- Do not define a pre-completion reservation as delivered; invalidation that wins before `future.complete` must reject normal completion and roll back exactly once.
- Use deterministic latches, barriers, atomics, executor queues, and thread-state checks only; no sleeps or elapsed time for synchronization. Bounded waits are guards only.
- Every security/correctness mutant is isolated, expected to fail for its named mechanism, immediately reversed, SHA-256 verified, and followed by a green rerun before the next mutant.
- The implementation commit message must be `2026-08-10-graph-workspace: Fix map lease EDT settlement`.
- Before claiming completion, run focused tests, full `:freeplane_plugin_graph:check -PTestLoggingFull --rerun-tasks`, `verifyGraphBundle`, Java 8 bytecode, exact allowlist, and clean-status checks.

## Task 1: Repair Task 13 EDT settlement and invalidation

**Implementer tier:** Capable

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/MapLeaseManager.java:1-end`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/MapLeaseManagerShould.java:1-end`

**Interfaces:**

- Consumes the existing package-private `MapLeaseManager` test constructors, `LeaseCompletionInterceptor`, `EdtExecutor`, `MapLease`, `MapOperationalState`, and `MapAdapterEvent` contracts. The public signatures of `MapLeaseManager`, `EdtExecutor`, and `MapLease` must remain byte-for-byte compatible.
- Consumes the existing `PendingAcquire` fields and methods `addPendingAcquire`, `reservePendingAcquire`, `completeReservedRequest`, `settleReservedRequest`, `handleMapRemovedOnEdt`, `reloadManagerOwnedModelOnEdt`, `acquire`, `close`, and `removeEntryIfUnused` as the current reservation/lifecycle boundaries.
- Produces one shared settlement protocol in `MapLeaseManager` in which an invalidation that wins before `future.complete` rejects the request and rolls back its exact reservation, while an EDT invalidation that encounters an in-flight delivery records an exact deferred intent and returns without waiting.
- Produces deterministic regression coverage in `MapLeaseManagerShould` for T13-F-6, T13-F-8, lifecycle deferral, external-reload deferral, lost-wakeup protection, and all previously fixed Task 13 mechanisms.

The fresh remediation starts from production commit `3d39e6c2881bbd2bb687e8bc352eda6295ffae45` (`MapLeaseManager.java` SHA-256 `671bce059b8ca630342b18faca9ed6f9556cc86d9de5801fe11f1871514f5ae2`) and carries the following failed correction history. Read the corresponding reports under `/data/home/guest/Development/freeplane/.worktrees/graph-workspace/.superpowers/sdd/006-implement-graph-workspace/` before editing:

- `50f81fd96ec204eb623a8a58a3c579888cc82634`: initial viewless lease lifecycle; later review found reload reentrancy, release/cancellation, and reservation-race defects T13-F-1 through T13-F-4.
- `5110cb18a0e08e67590f9a5266b71983d60aef74`: fixed T13-F-1 through T13-F-4; retain those guards.
- `a12eb0c409c48709cb50db96342400ec8bfdd70e`: fixed reentrant acquisition, but introduced the zero-returned-lease/reload interaction later recorded as T13-F-5.
- `68447aa69c546eef9b3362de1314332fce843114`: fixed T13-F-5 and close-visible reservation tracking, but left T13-F-6's check-to-complete window.
- `8bdce1ae3d768c8c89bbdce24146c0ddcd4584f1`: unified immediate reuse with `PendingAcquire`, but lifecycle invalidation still raced delivery; T13-F-6 and T13-F-7 remained open.
- `3d39e6c2881bbd2bb687e8bc352eda6295ffae45`: serialized lifecycle/reload/model-null invalidation on `settlementLock`, closing the stale window and resolving T13-F-7, but introduced T13-F-8: delivery holds the lock across dependent callbacks while EDT invalidation waits on it. A dedicated disposable probe passes at `8bdce1ae` and fails at `3d39e6c2` with the settlement/EDT inversion. Do not repeat this blocking-EDT correction.

- [ ] **Step 1: Read the current source and pin the baseline behavior**

Read the current `MapLeaseManager.java`, the complete `MapLeaseManagerShould.java`, the approved design at `docs/superpowers/specs/2026-08-11-map-lease-settlement-recovery-design.md`, and the prior reports named above. Confirm the current implementation has one `settlementLock`, delivery holds it through `future.complete`, lifecycle removal/reload acquire it with blocking `lock()`, and `completeImmediateAcquire` is absent. Confirm the worktree starts clean and record the two baseline source hashes in the report.

- [ ] **Step 2: Add the falsifiable RED probe for the head-only deadlock**

Add a deterministic test using the existing completion interceptor and a dedicated detecting EDT. During immediate lease delivery, attach a dependent completion callback that starts a second thread to enter lifecycle invalidation on the exact old model, then calls `manager.close()` from the completion callback. The detecting EDT must report whether its invalidation task is `WAITING` at the settlement boundary; the callback must not join that operation thread before `future.complete` returns. Use latches/atomics and bounded joins, never sleeps. Assert that the callback, close teardown, EDT task, and future settlement all finish, exact model listeners are removed, and no inversion is observed. Run only this test against unmodified `3d39e6c2` and record the expected deterministic failure before changing production code.

Add the analogous external-reload callback-close RED probe. It must mark the exact manager-owned model externally changed, start the EDT check, assert `closeCount == 0` while delivery is in progress, and detect the same inversion without hanging. Keep the test controls condition-based and ensure operation threads are joined only after the completion boundary is released.

- [ ] **Step 3: Add RED coverage for deferred invalidation and the lost-wakeup handshake**

Add deterministic lifecycle-removal and external-reload probes that hold delivery inside a dependent callback, let the EDT invalidation attempt its boundary, and prove the EDT operation returns without applying the generation/model mutation while the callback is still active. After the callback releases the boundary, drain the EDT and assert exactly-one reacquisition/reload, no stale callback, exact listener counts, usable returned leases, and ordinary zero-count teardown.

Add a deterministic no-lost-wakeup control for the two-attempt handshake. Use a package-private test seam only if required, without changing any public signature, to place the settlement owner release between the first failed EDT `tryLock` and deferred registration. The test must prove the deferred operation is eventually scheduled and processed without an unrelated later settlement operation. Run the new probes against the unchanged baseline and record each intended RED result.

- [ ] **Step 4: Implement one shared try-or-defer settlement mechanism**

Implement the smallest production change matching the approved design:

- Represent deferred lifecycle removal and manager-owned reload as exact typed operation descriptors carrying the required model/entry identity.
- Add a monitor-guarded deferred queue and any scheduling flag needed to prevent duplicate drains.
- Add one private EDT try-or-defer helper. It must attempt `settlementLock.tryLock`, apply the operation only after exact revalidation under `settlementLock -> monitor`, and on contention enqueue then perform the second `tryLock` handshake without holding `monitor`.
- Add one private outermost settlement-release helper and replace every direct `settlementLock.unlock()` with it. It must schedule deferred work only after the current thread releases its final reentrant hold, and must preserve/requeue work on an open-manager scheduling failure.
- Route `handleMapRemovedOnEdt` and `reloadManagerOwnedModelOnEdt` through the helper. Do not let either EDT path call blocking `settlementLock.lock()`.
- Keep all model operations and listener callbacks on the EDT, all future completion under the settlement boundary but outside `monitor`, and all exact reservation reconciliation through the existing tracked lifecycle.
- Ensure manager close from a dependent callback can synchronously reach EDT teardown, and ensure deferred operations become no-ops after close.

Do not add a second lock, a parallel completion path, a resurrected immediate completion method, a public API, or a pre-completion delivery commit that changes the finding's winner semantics.

- [ ] **Step 5: Run the focused RED-to-GREEN suite**

Run the new lifecycle deadlock, external-reload deadlock, deferred lifecycle, deferred reload, and lost-wakeup tests with `--rerun-tasks`. Then run the complete `*MapLeaseManagerShould` class with `-PTestLoggingFull --rerun-tasks`. Expected result: all new probes pass, the manager suite has zero failures/errors, and no test uses sleep or elapsed time as synchronization. Inspect the XML counts and retain fresh evidence paths in the implementer report.

- [ ] **Step 6: Execute the isolated mutation matrix**

Apply one named mutant at a time and immediately restore the inverse. At minimum verify:

- blocking lifecycle acquisition exposes the dedicated-EDT callback-close deadlock;
- blocking external reload exposes the external-reload callback-close deadlock;
- dropped lifecycle or reload deferral leaves the required reacquisition/reload behavior unperformed;
- removed second handshake fails the deterministic lost-wakeup probe;
- scheduling deferred work before the outermost reentrant release recreates the callback-close inversion or premature mutation;
- each prior T13-F-1 through T13-F-7 protection still fails under its named one-mechanism mutant.

After each restoration verify the exact production SHA-256 returns to the current implementation hash and rerun the relevant green probe before proceeding. Never stack mutants or leave diagnostic instrumentation in the final files.

- [ ] **Step 7: Run all verification gates and audit the exact scope**

Run:

```bash
env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/graph-workspace \
  :freeplane_plugin_graph:test --tests '*MapLeaseManagerShould' \
  -PTestLoggingFull --rerun-tasks

env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/graph-workspace \
  :freeplane_plugin_graph:check -PTestLoggingFull --rerun-tasks
```

Confirm `verifyGraphBundle` passes, the manager XML has zero failures/errors, `MapLeaseManager.class` has major version 52, no public signatures changed, `git diff --name-only` contains only the two allowlisted files relative to the implementation base, and the worktree is clean except for the intended commit. Record final source hashes and test XML paths.

- [ ] **Step 8: Commit the implementation**

Create exactly one implementation commit after all tests and mutants are green:

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/MapLeaseManager.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/MapLeaseManagerShould.java
git commit -m "2026-08-10-graph-workspace: Fix map lease EDT settlement"
```

The implementer report must include `STATUS: DONE`, the commit SHA, exact two-file scope, baseline/head hashes, RED and GREEN evidence, mutation restoration evidence, and any concerns. Do not modify the approved design, plan, old run state, or progress files.
