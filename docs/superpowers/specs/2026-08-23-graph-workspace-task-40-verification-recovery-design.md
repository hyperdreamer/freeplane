# Graph Workspace Task 40 Verification Recovery Design

## Context

The previous Task 40 continuation child completed the intended source corrections for carried findings F-1/F-2/F-3 but was blocked before verification because Task 41's serialized test lane was active. The corrected source remains dirty in the same registered Task 40 worktree and is the only candidate input for this successor.

Current successor input:

- Worktree: `/data/home/guest/Development/freeplane/.worktrees/graph-workspace-batch-j-task-40`
- Branch: `2026-08-10-graph-workspace-task-40-command-acceptance`
- HEAD: `577961a7b7f86cf032cc68f1d6058f055dc27c07`
- Source: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceCommandAcceptanceShould.java`
- Source SHA-256: `f2c8323918ed48bfc31aebff0478541d0c249426ce5a78da0b8bdba8cf62f164`

## Recovery

Create a fresh one-task SDD run in this existing worktree. A capable implementer independently audits the preserved candidate, runs the required serialized red/green focused and full tests now that Task 41 is clear, executes falsifiable disposable mutant probes for the carried boundaries, fixes only the allowlisted acceptance source if evidence exposes a gap, and commits exactly that source. A Frontier task review and mandatory Frontier final review then inspect the resulting range.

The Task 41 failure is isolated to its separate worktree and is not a reason to run Task 40 concurrently with any other verification. Use the data-backed Task 40 temp root and leave no temporary logs or mutant archives. Predecessor run roots and blocked reports are immutable evidence only.

## Evidence rule

A child report is admissible only after its persisted dispatch pointer matches the exact pre-spawn prompt bytes and the completed child transcript's first user message. Any mismatch is terminal. Full-suite test totals and failures must be recorded verbatim from the result XML/logs.
