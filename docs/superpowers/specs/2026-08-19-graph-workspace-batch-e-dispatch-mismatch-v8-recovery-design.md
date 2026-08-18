# Graph Workspace Batch E V8 Recovery Design

## Recovery Boundary

V7 is terminal at `DISPATCH_MISMATCH_BLOCKED`. Its implementer commit is valid and remains authoritative, but the task-reviewer result cannot be admitted because the child received prompt bytes different from the persisted rendered prompt. Preserve V7 `state.json`, `progress.md`, prompts, reports, transcripts, and mismatch event unchanged; the review report is diagnostic only and is not evidence for V8.

The valid source baseline is commit `e740e9c741f1f2aa6db4c0567e1957bf0416a63d`, `2026-08-10-graph-workspace: Correct finite edge projection clamping`. It changes exactly:

- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphHitIndex.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphInteractionControllerShould.java`

The current recovery continues in the existing worktree `/data/home/guest/Development/freeplane/.worktrees/graph-batch-e-recovery`. Do not create another Git worktree. The worktree must be clean at V8 preflight; ignored SDD artifacts and the pre-existing `.codegraph/` directory remain preserved.

## Task 1 Audit Contract

V8 Task 1 is a no-source-change carry-forward audit of the exact committed Task 26 plus finite-coordinate range. A fresh capable implementer must independently inspect the code and run the required gates, but must not modify, stage, or commit source. A fresh Frontier reviewer then independently reviews the same range. If the reviewer finds a load-bearing defect, only the state-machine-directed fix round may modify the two Task 1 paths, followed by scoped re-review. A clean audit is accepted without a new source commit.

The audit must explicitly test finite ordinary coordinates, mixed magnitudes, near-limit spans, cancellation residuals, positive subnormal offsets, zero-length segments, endpoint projection clamping, zero tolerance, finite tolerance comparison, non-finite rejection, nearest-edge ordering, layout-anchor precedence, suppressed-hull exclusion, node-before-hull ordering, interaction intents, and projection-only safe search. Valid finite geometry may not be rejected or clamped merely because its coordinates are extreme.

## Task 2 Boundary

After Task 1 is independently approved, implement Backlog Task 27 keyboard traversal and accessible virtual children over the existing immutable graph values. Keep the existing Task 25/26 commits and any bounded Task 1 fix commit immutable. Use the existing package APIs and exact allowlists from the V8 plan.

## Evidence Rules

- Preserve every prior terminal SDD root, especially V5, V6, and V7, byte-for-byte. Do not cite blocked-run reports as fresh evidence.
- Validate the V8 plan with `sdd-state validate-plan` and a real `parsePlanText` body-truncation probe before initialization.
- Render every child prompt through `sdd-state render-prompt`, persist it, and pass its bytes verbatim. Record the returned session immediately and compare the completed child's first user message byte-for-byte before admitting a report. Any mismatch terminates that run.
- Use bounded logs, exact source allowlists, Zulu 21, and fresh test evidence. Never clean or reset the worktree.
