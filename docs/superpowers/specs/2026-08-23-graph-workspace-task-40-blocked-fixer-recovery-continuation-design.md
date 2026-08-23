# Graph Workspace Task 40 Blocked-Fixer Recovery Continuation Design

## Context

The previous Task 40 fixer run is terminal `TASK_BLOCKED` because the capable child stopped after provider `403 insufficient balance` without a report or commit. Its dirty candidate is preserved in the existing registered Task 40 worktree and is the only source change currently present. Earlier blocked attempts and their reports remain immutable evidence only.

The current candidate is the exact dirty path `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceCommandAcceptanceShould.java` at SHA-256 `a72abf81851153d0b27d022a1e51005640e5343c08ff5b596099579b055d4ee8`, relative to current HEAD `a2a4bf59c641730cccc420b8867dd72b3b8ae731`. It already contains a real native fixture for scenarios 08, 09, and 16, but Scenario 11 still uses mocked native collaborators and the Scenario 22 validation transition remains insufficiently falsifiable.

## Recovery

Use the same existing Task 40 worktree and branch. Create a distinct successor SDD run whose first task is a fresh capable correction/audit of the exact dirty candidate against carried findings F-1, F-2, and F-3. Complete only the one allowlisted acceptance source, preserving all unrelated candidate work. Require red evidence for the old mock-only or single-validation paths, real production-shaped native map/link/undo behavior, sequential validation changes for purge and contributor deletion, serial focused/full tests, and an exact one-file commit. A Frontier task review and mandatory Frontier final review must inspect the resulting complete range.

Do not reset, checkout, clean, stash, or discard the candidate. Preserve all predecessor run roots and reports; do not cite absent or blocked child reports as approval evidence. Any source fix remains limited to the acceptance test path. Test verification is serialized with Task 41's lifecycle/resource lane; use separate data-backed temporary roots and do not run Gradle test commands concurrently.

## Dispatch Transport

Persist each complete renderer role envelope and a no-trailing-newline pointer. Build each `spawn_subsession` prompt by reading the exact pointer file bytes. Compare candidate/pointer bytes before dispatch and the raw first child user message with the pointer before admitting the report. Any mismatch is terminal.
