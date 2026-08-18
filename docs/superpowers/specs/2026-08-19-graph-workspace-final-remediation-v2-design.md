# Graph Workspace Final Remediation V2 Design

- Date: 2026-08-19
- Status: Continuation after terminal dispatch mismatches
- Preserved implementation commit: `091e46581950fffeb42087e48d696d43d2158848`
- Preserved implementation parent: `6c242168eba15725aa27988e39b2027f93949ec3`
- New baseline commit: `1290cfdbbee7c32b2c4edc761e91e7fa0cd38a17`
- Original merge base: `9248c6e227bb82fab8e6139f46db37b62174309f`

## Context

Two earlier SDD runs are terminal and immutable. The first successor reached
`DISPATCH_MISMATCH_BLOCKED` after an implementer received a stale worktree path.
The recovery successor reached `DISPATCH_MISMATCH_BLOCKED` after its correlated
Task 1 reviewer received prompt bytes that differed from its persisted rendered
prompt and then stopped without writing its required report. Neither blocked
child report or verdict is admissible.

The mismatched first implementer nevertheless created `091e465819`, which changes
exactly six Task 1 files. A prior audit independently exercised the implementation,
but this continuation redoes the audit and task review without using any blocked-run
artifact as evidence. The audited source boundary is the direct commit delta
`091e465819^..091e465819`, not the historical branch range that also contains
plan and design commits.

## Carried Findings

- `FINAL-F2`: mixed contributor deletion must compensate exactly the published
  workspace history entry. It must not use generic `store.undo()` because an
  interposed workspace command could otherwise be consumed.
- `FINAL-F4`: a failed `LayoutSettleLoop` publication must remain restartable.
  `GraphCommandRouter` currently reports Restart Layout as applied even when the
  current failed projection is not resubmitted.

## Task 1: Fresh Exact-Commit Audit

The continuation's first task has no source deliverable. It independently verifies
only the six-file delta in `091e465819` against `FINAL-F2`:

- history token identity, revision, redo content, current document identity, and
  command-undo-redo ABA rejection;
- persistence-aware workspace mutation compensation, including file identity,
  generation, dirty/debounce state, byte restoration, save-as, and retry behavior;
- handler-owned workspace-first then native recovery, with no generic undo;
- original Task 32 native transaction and owner-local undo compatibility;
- fresh focused test evidence and a disposable archive mutation proving the ABA
  regression is falsifiable.

The audit writes one report below its new run root, leaves source, index, and HEAD
unchanged, and receives a fresh independent task review. The direct commit delta
is the sole source allowlist; continuation documentation is not part of the audit
range.

## Task 2: Failed Layout Restart

A `Run` whose frame or geometry path fails currently publishes an `OperationalStatus.FAILED`
canvas state as idle. `finishPublication()` then terminalizes that run, so a later
`restart()` has no live request to submit. The correction stays inside
`LayoutSettleLoop` and tracks enough failed-run state to distinguish a normal
idle settlement from a failed publication.

The corrected lifecycle must satisfy these rules:

1. A failed current run stays attached to the loop and is restartable after its
   failed canvas publication completes.
2. Restart advances the control revision and has exactly one recovery claim. If
   a failed publication is in flight, the recovery is deferred rather than
   claiming or stepping concurrently.
3. Once the newest recovery claim is valid, the lifecycle dispatcher calls
   `FrameStepper.restart()` and resubmits the retained immutable `LayoutRequest`.
   It never calls `step()` as the first operation for a failed run.
4. A reentrant restart from a failed-state listener may supersede the earlier
   revision; only the newest revision may restart and submit.
5. Reset, newer start, pause, and close invalidate deferred recovery so they
   cannot submit a stale request. A restart or submit failure republishes the
   failed state and leaves the current run retryable.
6. The live `GraphUpdateCoordinator` and `GraphCommandRouter` chain must produce
   the recovered IDLE canvas state after `RestartLayout`, while retaining their
   existing public interfaces and command result contract.

## Completion

This branch can complete only after the fresh Task 1 audit and review, Task 2
TDD and review, and a Frontier whole-branch final review from the original merge
base through the final V2 head. The final ledger must reconcile `FINAL-F2` and
`FINAL-F4`, and every terminal parent run remains preserved without reopening or
admitting its child results.
