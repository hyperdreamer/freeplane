# Graph Workspace Final Remediation Recovery Design

- Date: 2026-08-18
- Status: Successor recovery design
- Parent terminal run: `graph-batch-f-successor`, `DISPATCH_MISMATCH_BLOCKED`
- Preserved code commit to audit: `091e46581950fffeb42087e48d696d43d2158848`
- Clean recovery baseline: `af034e5d9bd1c6a58be81ec245835fbff35e1ec8`

## Context

The first final-remediation successor run terminated at `DISPATCH_MISMATCH_BLOCKED` because its Task 1 implementer prompt contained a stale worktree path. That child nevertheless produced commit `091e465819`, but its report and result are inadmissible. The original terminal run, its state, prompts, report, and worktree remain immutable evidence.

The preserved commit is a valid six-path implementation candidate for `FINAL-F2` exact workspace compensation. It must receive a fresh no-source-change audit and independent task review in a clean recovery worktree. The remaining `FINAL-F4` layout restart defect is independent and will be implemented only after the audited Task 1 is complete.

## Recovery Goals

1. Independently establish whether `091e465819` correctly provides ABA-safe, persistence-aware, exact workspace purge compensation and preserves the original Task 32 behavior.
2. If the audit or review finds defects in the preserved Task 1 commit, remediate only the approved Task 1 six-path scope through fresh TDD/fix/re-review gates.
3. Implement and review the failed-layout restart recovery with the exact four-path control/test scope from the corrected successor plan.
4. Run a fresh whole-branch Frontier final review from the original merge base through the recovery HEAD, carrying `FINAL-F2` and `FINAL-F4`.

## Task 1 Recovery Design: Audit Preserved Compensation Commit

The first task is intentionally read-only with respect to deliverables. It inspects the exact range `af034e5d9bd1c6a58be81ec245835fbff35e1ec8..091e46581950fffeb42087e48d696d43d2158848` and verifies the preserved implementation against the approved compensation contract:

- history entry identity, monotonic revision, redo identity/content, and ABA rejection;
- GraphWorkspaceStore file identity, persistence generation, dirty/debounce envelope, save/autosave/save-as behavior, and persisted-byte restore/rejection;
- exact handler-owned mutation recovery and workspace-first/native-second ordering;
- original Task 32 native transaction, descriptor, rollback, and compatibility behavior;
- exact six-path scope and fresh test/mutation evidence.

The audit task may not edit, stage, commit, or repair source. It produces a bounded audit report. A fresh Frontier task reviewer then independently reviews the same range. If both pass, the task is complete and its commit is admitted as reviewed work. If not, a subsequent explicitly scoped fixer task may modify only the six Task 1 paths.

## Task 2 Recovery Design: Restart Failed Layout Runs

After Task 1 is complete, implement `FINAL-F4` using the corrected design:

- keep a failed current `LayoutSettleLoop` run restartable rather than terminalizing it;
- defer reentrant restart requests while failed publication holds its claim;
- transfer only the newest control revision to a single recovery claim after publication releases;
- call worker restart before resubmitting the retained immutable request;
- preserve reset/close/superseding-start cancellation and lifecycle serialization;
- prove the live coordinator command chain and public router result.

Task 2 changes only:

- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/LayoutSettleLoop.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/LayoutSettleLoopShould.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/GraphUpdateCoordinatorShould.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/command/GraphCommandRouterShould.java`

## Acceptance

The recovery is complete only after the preserved Task 1 commit has a fresh independent audit/review, Task 2 has TDD RED/GREEN and independent review, carried `FINAL-F2` and `FINAL-F4` are reconciled, the final Frontier review passes over the original merge base through the recovery HEAD, and the tracked/index state is clean. The blocked parent runs are never reopened.
