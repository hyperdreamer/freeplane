# Graph Workspace Task 41 Dispatch-Mismatch Recovery Design

## Context

The original Task 41 SDD run at `.superpowers/sdd/batch-j-task-41` is terminal in `DISPATCH_MISMATCH_BLOCKED`. Its child received 5,384 bytes rather than the persisted 5,791-byte prompt, so the child report and transcript are inadmissible.

The source commit is preserved as immutable recovery input: `dfcd0f99010ad7dc04c167caecba002115a230f7`, parent `834d381f724c8606034a6bc5c878bb91d105cb63`, changing only `GraphWorkspaceColdReloadShould.java` and `GraphWorkspaceLifecycleShould.java`. Their SHA-256 values are respectively `ebf21666566ed9fbc660582b4e1fdbce3ba7b7f2f78c39775934331dc8d02320` and `e2a1f5ddfa5ab3e2cc1517567b3322e833c6a00646b5e88f168ac0a33df420d5`.

## Recovery

A distinct successor performs a read-only audit of that commit. It independently verifies real Freeplane map creation/save/reopen boundaries, production controller/store/actors/connectors/groups/leases/workers, exact state/projection comparison, 25 lifecycle cycles, close during debounce, Retry/Discard/Cancel, callback suppression, and resource baselines. It reproduces the dirty-close and stale-callback mutants in disposable archives.

Verification is serialized after the Task 39 and Task 40 acceptance audits to avoid timing and EDT contamination. A fresh task reviewer then reviews the exact implementation range. Only an admitted finding may authorize a normal fix round, restricted to the two Task 41 test files.

## Dispatch Transport

The complete renderer-produced role envelope is persisted under the successor run root. The tracked child receives a short two-line ASCII pointer naming that envelope. A candidate copy is compared to the pointer before spawn; the raw child first message is compared byte-for-byte after completion and before report admission. Any mismatch is terminal.

## Completion

Completion requires fresh serialized focused/full tests, independent mutant and behavior evidence, task review, and Frontier final review of `834d381f724c8606034a6bc5c878bb91d105cb63..HEAD`, with no production changes, exact two-file source scope, no residue, and clean Git state.
