# Graph Workspace Batch H Provider-Stop Successor

## Decision

The remediation run `.superpowers/sdd/2026-08-20-graph-workspace-batch-h-remediation` is preserved at terminal `DISPATCH_MISMATCH_BLOCKED` after its Task 2 implementer stopped in the provider before producing a report or changing source. Its state, progress ledger, rendered pointer, role envelope, and child transcript remain sealed and are not implementation or review evidence.

## Carry-Forward

The branch contains the independently committed Task 1 presentation correction at `7e381428707cb1259b5eb163451d2bf2a535fc14`, changing exactly the ten Task 1 paths from base `1ff2afd3b11ed8981c17f79af5d9931a878c9251`. The successor must audit that immutable range afresh without rewriting or reverting it before it attempts Task 2.

Task 2 has no source changes or admissible result in the blocked run. The successor implements only the four Task 2 paths from the current branch head, then runs the normal independent task review and whole-branch Frontier final review. The existing predecessor finding IDs F-2, F-3, F-4, and F-5 remain carried obligations for final reconciliation.

## Invariants

- Stay in `/data/home/guest/Development/freeplane/.worktrees/graph-workspace-batch-h-ui-shell`; do not create a worktree.
- Preserve Java 8 source/target compatibility; use Zulu Java 21 and `gradle`.
- Do not modify `freeplane_api`, `MapView`, editor translations, or unrelated graph behavior.
- Keep graph window tests headless and do not call `setVisible`.
- Do not admit the blocked run's Task 2 report because it does not exist; do not reissue its prompt in place.
- Every successor dispatch stores an exact rendered envelope and pointer, records the returned session immediately, and admits only a byte-matching transcript with a valid report.
