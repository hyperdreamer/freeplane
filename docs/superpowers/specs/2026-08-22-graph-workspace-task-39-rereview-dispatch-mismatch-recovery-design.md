# Graph Workspace Task 39 Re-review Dispatch-Mismatch Recovery Design

## Context

The successor Task 39 run at `.superpowers/sdd/batch-j-task-39-recovery` reached terminal `DISPATCH_MISMATCH_BLOCKED` while its Frontier re-review child was reviewing fixer commit `935650a75f1ec093d3a90884dccfaffd1772606b`. The persisted re-review pointer was 230 bytes, but the child received 229 bytes because the transport omitted the final newline. The child's `APPROVED` report is therefore inadmissible evidence under the exact-byte dispatch contract.

The fixer commit is preserved and changes exactly the Task 39 acceptance test path. Its focused and full graph-plugin tests were green, but those results and the blocked re-review report are not approval evidence for this successor.

## Recovery

Create a distinct successor SDD run whose sole task is a fresh read-only audit of the current fixed Task 39 acceptance test at `935650a75f1ec093d3a90884dccfaffd1772606b`. The audit independently checks the four previously admitted findings (strict ledger parsing, unavailable-map exclusion, native clone snapshots, and stock Freeplane persistence), all named model scenarios, test counts, identity/confidentiality boundaries, and exact one-file scope. A fresh Frontier task reviewer then reviews the current implementation range and audit claims. Only a fresh admitted finding may open a fix round; any fix remains limited to the one acceptance test path.

No source reset, rollback, or production change is authorized. The predecessor run and the terminal blocked run remain immutable historical records.

## Dispatch Transport

Persist each complete renderer envelope under the new run root. Persist a short pointer with no trailing newline and dispatch that exact byte sequence. Compare pointer and candidate before spawn, then compare the raw first user message with the pointer before admitting any report. A mismatch is terminal and must not be normalized away.

## Completion

Completion requires a fresh read-only audit, independent Frontier task review, and Frontier final review of `834d381f724c8606034a6bc5c878bb91d105cb63..HEAD`, with no unresolved load-bearing findings, clean Git state, and all predecessor artifacts preserved.
