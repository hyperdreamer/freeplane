# Graph Workspace Final Remediation V3 Design

- Date: 2026-08-19
- Status: Approved continuation after terminal dispatch mismatch
- Baseline: `1e5c710517e9f4c438e96795dd49a253370ae2c4`
- Preserved blocked run: `graph-batch-f-successor-v2`, `DISPATCH_MISMATCH_BLOCKED`
- Original merge base: `9248c6e227bb82fab8e6139f46db37b62174309f`

## Context

The V2 run has a clean source tree and a passing Task 1 audit, but its
independent reviewer received prompt bytes differing from the persisted prompt.
The reviewer report is therefore sealed diagnostic evidence, not an admitted
verdict. Its concrete observation is independently reproducible in the current
`GraphWorkspaceStore` code and must be tested from source in this continuation.

A second terminal parent run and all prior run roots remain immutable. This V3 run
starts from the clean V2 HEAD and does not reopen or admit any predecessor report.

## Workstream 1: Exact Workspace Audit

Task 1 performs a fresh no-source-change audit of the direct implementation commit
`091e46581950fffeb42087e48d696d43d2158848^..091e46581950fffeb42087e48d696d43d2158848`.
The audit scope is exactly the six files changed by that commit, excluding all
predecessor plan/spec commits. It independently verifies the `FINAL-F2`
exact-history compensation contract and explicitly probes the write-before-throw
retry scenario in the current store tests. Its own report and independent review
are the only admissible Task 1 evidence.

## Workstream 2: Persisted-Byte Retry Recovery

Task 2 fixes the verified retry gap in `GraphWorkspaceStore` and its focused test.
The mutation must remember when a failed restore attempt has nevertheless been
confirmed on disk as `beforeBytes`. A later compensation attempt may then use that
verified persisted-byte progress while still requiring current file identity,
current document identity, and the original `WorkspaceHistory.HistoryMutation`
token to match. It must not blindly trust an exception, decrement monotonic save
generations, or bypass interposition/save-as checks.

The regression uses a writer that writes the requested bytes and throws afterward:

1. execute a mutation and allow autosave to persist `afterBytes`;
2. compensate, with the restore writer persisting `beforeBytes` and then throwing;
3. assert the first result is `compensation_incomplete`, the file is actually
   `beforeBytes`, and the current document/history remain post-mutation;
4. retry the same mutation and assert exact history compensation succeeds, the
   document is restored, the file remains `beforeBytes`, and the dirty envelope is
   restored without a save-generation decrement;
5. retain rejection for later document/file/history interposition.

The implementation should use one active recovery path and no compatibility fallback.

## Workstream 3: Failed Layout Restart

Task 3 carries the existing `FINAL-F4` remediation. A failed current
`LayoutSettleLoop` run remains attached and restartable after failed canvas
publication. Restart claims the newest control revision once, defers while
publication owns the claim, calls worker restart, then resubmits the retained
immutable request. Reentrant restart, reset, newer start, pause, and close must
invalidate stale recovery. The live `GraphUpdateCoordinator` and
`GraphCommandRouter` tests prove the public command chain reaches recovered IDLE.

## Completion

The V3 branch completes only after all three tasks receive independent review,
all carried findings are reconciled, a Frontier whole-branch review covers the
original merge base through the final V3 HEAD, and fresh graph-plugin verification
passes with a clean tracked/index state.
