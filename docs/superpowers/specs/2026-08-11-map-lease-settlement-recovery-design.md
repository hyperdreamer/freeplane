# Map Lease Settlement Recovery Design

- Date: 2026-08-11
- Parent task: Graph Workspace Task 13
- Recovery scope: Task 13 only
- Status: Design approved in conversation; written specification awaiting review

## Summary

Task 13's fifth fix round closed the stale lease-delivery window by making normal `CompletableFuture` completion and every generation-changing invalidation contend on one `settlementLock`. That correction introduced a lock cycle: normal delivery holds `settlementLock` while dependent completion callbacks run, an EDT lifecycle operation waits for that lock, and a dependent callback calling `MapLeaseManager.close()` waits synchronously for EDT teardown.

The recovery keeps one delivery/invalidation winner boundary, but makes EDT invalidation nonblocking. Lifecycle removal and manager-owned external reload attempt the settlement boundary with `tryLock`. If delivery currently owns it, the EDT records the exact invalidation and returns to the event loop. The outermost settlement unlock then schedules the deferred operation back onto the EDT. Non-EDT operations may continue to block at the settlement boundary.

This preserves the existing Task 13 ownership, state, reference-count, and callback semantics while removing the EDT-to-settlement wait that makes the deadlock possible.

## Scope

The recovery changes only:

- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/MapLeaseManager.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/MapLeaseManagerShould.java`

It adds no production file and changes no public signature. `EdtExecutor`, `MapLease`, `MapOperationalState`, and the public `MapLeaseManager` API remain unchanged.

The recovery carries all Task 13 findings:

- T13-F-1 through T13-F-5 remain fixed.
- T13-F-7 remains fixed through one tracked `PendingAcquire` reservation lifecycle.
- T13-F-6 must be resolved without stale normal completion after an invalidation winner.
- T13-F-8 must be resolved without an EDT/settlement deadlock.

## Non-Goals

This recovery does not:

- advance Graph Workspace Task 14 or any later task;
- redesign map loading, operational states, editor ownership, or external-change detection;
- add a settlement executor or expose asynchronous implementation details publicly;
- move arbitrary completion callbacks onto the EDT;
- redefine a pre-completion reservation as an already-delivered lease;
- retain a compatibility path beside the corrected settlement protocol.

## Required Invariants

### One Winner

A reserved request and an operation that can invalidate its currency have one ordering boundary.

- If invalidation acquires the settlement boundary first, it advances or destroys the relevant currency before delivery revalidates. Normal completion must not succeed, the exact reservation rolls back once, and ordinary zero-count teardown runs.
- If delivery acquires the boundary first, its `CompletableFuture` completes normally before the boundary is released. A contending EDT invalidation is deferred and cannot mutate the entry during that completion.
- Manager close, lifecycle removal, external reload, model-null retry, cancellation, rollback, entry removal, and teardown must not create a second completion path.

### Lock Order

The global order remains:

1. `settlementLock`
2. `monitor`

No path may hold `monitor` while acquiring `settlementLock`. The EDT may inspect or enqueue deferred work under `monitor`, but it must release `monitor` before retrying or scheduling settlement work.

### Callback Placement

The following never run while holding `monitor`:

- `CompletableFuture.complete` or `completeExceptionally`;
- dependent future callbacks;
- map adapter listeners;
- the injected completion interceptor;
- EDT executor calls;
- model-listener callbacks.

Future completion remains within the settlement boundary but outside `monitor`. EDT invalidation never waits for that boundary.

### EDT Authority

Freeplane model loading, lifecycle reconciliation, listener attachment/removal, and manager-owned model close remain EDT-only. Deferral records an intent; it does not move Freeplane model work to a delivery thread.

## Settlement Protocol

### Shared Release Point

Every successful `settlementLock.lock()`, reentrant acquisition, and successful `tryLock()` is released through one private helper. Direct unlocks outside that helper are prohibited.

The release helper:

1. releases one reentrant hold;
2. detects whether the current thread still owns an outer settlement hold;
3. schedules deferred EDT invalidations only after the outermost hold is gone.

A nested `close()` invoked by a dependent completion callback therefore does not dispatch deferred invalidation while the outer delivery still owns the boundary. Dispatch occurs when normal completion has returned and the outer delivery releases it.

### EDT Try-Or-Defer

Lifecycle removal and manager-owned external reload use one private try-or-defer mechanism.

1. On the EDT, attempt `settlementLock.tryLock()`.
2. If it succeeds, revalidate and apply the exact operation under `settlementLock -> monitor`, then release through the shared helper.
3. If it fails, enqueue an immutable operation descriptor under `monitor` and return without waiting.
4. After enqueueing, immediately perform a second `tryLock()` handshake without holding `monitor`.
5. If the second attempt succeeds, release through the shared helper so the newly queued work is scheduled. If it fails, the current or next lock owner is responsible for scheduling on its outermost release.

The second attempt prevents this lost-wakeup sequence: the first attempt observes an owner, that owner releases and observes an empty deferred queue, and the EDT enqueues after the owner has already checked.

### Deferred Operation Queue

Deferred operations are exact, typed invalidation intents rather than arbitrary off-EDT state changes. At minimum they distinguish:

- lifecycle removal for one exact `MapModel` identity;
- manager-owned external reload for one exact `Entry` and model identity.

The queue is guarded by `monitor`. Taking a batch, calling `EdtExecutor.execute`, and executing a batch happen outside `monitor` and outside `settlementLock`.

A scheduled drain runs on the EDT. Each operation goes through the same try-or-defer entry point and repeats all normal currency and ownership checks. Stale, duplicate, or post-close operations become no-ops. No operation is applied merely because it was once eligible.

The implementation may coalesce identity-equal deferred operations if and only if ordering and eventual processing stay equivalent. Coalescing is not required.

### Scheduling Failure

If EDT scheduling rejects work after manager close, the deferred operations are obsolete and may be dropped. If scheduling fails while the manager is still open, the operations must remain queued or be requeued; an open manager must not silently lose lifecycle or reload invalidation.

## Operation Flows

### Delivery Wins Before Lifecycle Removal

1. Delivery acquires `settlementLock` and revalidates the exact entry, generation, reservation, and settling membership under `monitor`.
2. Delivery invokes `future.complete` outside `monitor` while retaining `settlementLock`.
3. Lifecycle removal enters on the EDT, cannot acquire the lock, records the exact map removal, and returns.
4. Dependent callbacks finish. Delivery reconciles the exact reservation once.
5. The outermost unlock schedules the deferred lifecycle operation.
6. The EDT revalidates it, advances generation, detaches the old listener, publishes `LOADING`, and reacquires viewlessly.

The returned lease remains one counted lease on the entry and observes the entry's subsequent operational-state changes.

### Lifecycle Removal Wins Before Delivery

1. Lifecycle removal acquires `settlementLock` on the EDT.
2. It advances the exact entry generation and records reacquisition state under `monitor`.
3. Delivery later acquires the lock and fails its exact-currency check.
4. The request completes exceptionally outside `monitor`, its reservation rolls back exactly once, and zero-count teardown runs when applicable.

### Dependent Callback Closes During Contention

1. Delivery owns `settlementLock` and `future.complete` begins dependent callbacks.
2. An EDT lifecycle or reload operation fails `tryLock`, enqueues, and returns. The EDT remains available.
3. A dependent callback calls `manager.close()` on the delivery thread. Reentrant settlement acquisition is legal.
4. Close invalidates manager state under `settlementLock -> monitor`, releases its nested hold, and synchronously requests EDT teardown.
5. Because the EDT never waited for `settlementLock`, it performs teardown and close returns.
6. The dependent callback and `future.complete` return. The outermost release schedules any deferred operations, which observe `closed` and do nothing.

No callback waits for a thread that is waiting for its outer settlement hold.

### External Reload

External-change detection may inspect and consume the model's change indication before settlement. The deferred operation therefore retains the exact entry/model reload intent. When retried, it must revalidate manager openness, entry identity, model identity, positive lease count, loading state, manager ownership, viewlessness, and cleanliness before advancing generation or closing anything.

### Model-Null Retry And Close

Model-null retry remains a two-phase decision that never acquires `settlementLock` under `monitor`. It is not an EDT lifecycle operation and may wait for delivery's settlement boundary.

A non-reentrant close from another non-EDT thread may also wait for delivery. Close invoked by a completion callback is reentrant and follows the dependent-callback flow above.

## Reservation And Teardown Rules

- Immediate reuse and load completion both use `addPendingAcquire`, `reservePendingAcquire`, `completeReservedRequest`, and `settleReservedRequest`.
- `entry.pending` owns unreserved requests; `entry.settling` owns reserved but unsettled requests.
- One request increments `leaseCount` once and reconciles that count once.
- Failed normal completion, cancellation, exceptional invalidation, and manager close cannot double-decrement or retain a phantom count.
- `removeEntryIfUnused` may advance generation outside the settlement boundary only when `leaseCount == 0`, `pending` is empty, and `settling` is empty, so no request currency exists to invalidate.
- Listener detachment and model references follow the existing zero-count teardown path.

## Error Handling

- A stale deferred operation is ignored after full revalidation; it does not complete an unrelated request.
- Listener and completion callback failures retain the existing isolation behavior.
- Interruption or rejection must not leave a deferred-operation scheduling flag permanently set.
- Manager close remains idempotent.
- Tests and production code use bounded waits only as guards. Ordering is controlled by latches, barriers, atomics, executor queues, and explicit thread-state milestones, never sleeps.

## Test Design

### Required Red-to-Green Regressions

1. **Dependent close with lifecycle contention.** On a dedicated EDT, immediate delivery enters a dependent callback. The EDT starts exact lifecycle removal while delivery owns settlement. The callback calls `manager.close()`. The test requires callback, close, future settlement, and EDT task to finish within bounded guards, with listener and manager teardown exact. On `3d39e6c2`, a detecting EDT reports the settlement/EDT inversion.
2. **Dependent close with external-reload contention.** Repeat the cycle with manager-owned external reload. Assert the model is not closed while delivery owns settlement, the EDT remains available for close teardown, and no stale listener or model reference survives.
3. **Deferred lifecycle delivery.** While a delivery callback holds the boundary, lifecycle invalidation enters and returns without applying generation/model mutation. After delivery exits, the deferred operation runs exactly once and viewless reacquisition completes.
4. **Deferred external reload.** While delivery owns the boundary, external reload records one exact intent and returns. After release, reload revalidates and either safely replaces the exact clean viewless manager-owned model or publishes the established fallback state.
5. **No lost wakeup.** Deterministically place owner release between the EDT's failed first attempt and deferred registration. The second-attempt handshake must cause eventual EDT processing without another unrelated settlement operation.

### Existing Coverage Retained

The complete `MapLeaseManagerShould` suite remains green, including:

- canonical reuse and one-load coalescing;
- release/reference count and cancellation;
- reentrant listener acquire/release/close;
- close authority and exceptional completion;
- lifecycle reacquisition and stale callback silence;
- external reload ownership, view, and dirty-map rules;
- model-null retry serialization;
- exact listener removal and zero-count teardown;
- real fixture loading without a view.

### Mutation Requirements

Each mutant is applied alone, must make its named probe fail for the intended reason, and is immediately reversed with SHA-256 restoration before the green rerun.

New recovery mutants:

1. Replace lifecycle `tryLock` with blocking acquisition.
2. Replace external-reload `tryLock` with blocking acquisition.
3. Drop lifecycle deferral registration.
4. Drop external-reload deferral registration.
5. Remove the post-enqueue handshake.
6. Schedule deferred work before the outermost reentrant settlement release.

The recovery also reruns isolated mutants for every previously protected T13-F-1 through T13-F-7 mechanism: reload eligibility revalidation, strict release semantics, canceled-load teardown, reservation rollback, zero-returned-lease recovery, close authority, normal and exceptional completion placement, immediate tracked reservation, lifecycle/reload/model-null serialization, and old-listener removal.

## Verification Gates

The one-task remediation run is complete only when all of the following pass:

1. New RED probes fail against unmodified `3d39e6c2` for the claimed mechanism.
2. Focused new regressions pass after implementation.
3. Full `*MapLeaseManagerShould` passes with `--rerun-tasks`.
4. `gradle :freeplane_plugin_graph:check -PTestLoggingFull --rerun-tasks` passes, including `verifyGraphBundle`.
5. `MapLeaseManager.class` remains Java 8 bytecode, major version 52.
6. The commit range changes exactly the two allowed files and no public signature.
7. Every required mutant fails, is inversely restored, matches the recorded production SHA-256, and returns green before the next mutant.
8. A fresh Frontier task reviewer verifies T13-F-6 and T13-F-8, confirms T13-F-1 through T13-F-7 remain fixed, and returns `SPEC: PASS` plus `QUALITY: APPROVED`.
9. A fresh Frontier final reviewer covers the original Graph Workspace merge base through the remediation HEAD and reconciles the carried Task 13 finding ledger.

## Recovery Workflow

The terminal Task 13 run at revision 224 remains unchanged. This design is implemented through a fresh deterministic one-task SDD plan on the same feature branch. The new run carries the prior report paths, commit range, finding IDs, failed approaches, and mutation evidence as bounded context. It does not reopen or hand-edit the old state.
