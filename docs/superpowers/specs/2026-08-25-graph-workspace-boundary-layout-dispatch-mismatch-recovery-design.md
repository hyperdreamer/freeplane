# Graph Workspace Boundary Layout Dispatch-Mismatch Recovery Design

## Context

The SDD run at `.superpowers/sdd/2026-08-25-graph-workspace-boundary-layout` is terminal in `DISPATCH_MISMATCH_BLOCKED`. Its round-2 fixer received a prompt missing the stored `BLOCKED` report-table row. The child ran at the intended `capable` tier, but the transcript's first user message is not byte-identical to the recorded intent. The terminal run, including its state, prompt, reports, and audit projection, must remain unchanged and cannot certify any later work.

The child did create source commit `106c6374dd10ada5f3e9f1f88593e45f22cf0558`, directly following `dd9dcf4bc8d88a88dddd090c1827c5dea7db6d6d`. The candidate Task 2 range begins at reviewed Task 1 head `9a551a937d4643f41db0b93f71123f209f1f5b38` and contains this linear chain:

- `d570bfbe33d235303a824e36a0057fcd7db23229` - dynamic scrollable surface
- `dd9dcf4bc8d88a88dddd090c1827c5dea7db6d6d` - viewport persistence and anchor correction
- `106c6374dd10ada5f3e9f1f88593e45f22cf0558` - falsifiable viewport persistence regressions

The immutable source range changes exactly these five paths:

- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphCanvas.java` - SHA-256 `1ef6fd920dcbe61dd4de8c8bd236fe5cd003666c087a3a2dfd6700fb452a8bbd`
- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java` - SHA-256 `076f5a3e4c751f99146e850038547351197faac963f9cdfd8b41b59cb8f7d4d2`
- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceToolbar.java` - SHA-256 `a27a49e6743bae54b139b1fd094239eac03c8f1908cbf88e0309acaf6642a0bb`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java` - SHA-256 `9eb5310f0bfa83190c6c2da4144d19785fb58163754fc267404fee684a31a6f2`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java` - SHA-256 `bde726889d61d6022fd2c9e17af2ae2434ec2f0061cd64829e2b1a22fced92bb`

The original boundary and layout design remains the approved behavioral direction. This recovery adds no user-facing behavior; it replaces invalid process evidence with a fresh audit before the remaining layout work continues.

## Recovery Approach

A distinct SDD run first performs a source-read-only audit of the immutable Task 2 range. It independently verifies the scroll-surface integration, anchor-relative extent calculation, visible-world viewport persistence, programmatic event suppression, and falsifiability of the reset and deferred-clamp regressions. It may inspect the terminal predecessor state only to confirm it is preserved; it must not use predecessor reports or transcripts as correctness evidence.

A normal independent task review follows that audit. A reviewer-authorized fix may change only the five paths in the audited range. This keeps any real correction adjacent to the behavior it verifies and avoids broad recovery refactoring.

The second task performs the still-pending small-workspace layout change. It replaces the `0.002` seed envelope with one `50.0` world-unit seed spread, with a red regression against the current code. It preserves topology, identity-derived random ordering, pins, force calibration, prominence, and map correction.

## Dispatch Transport

The successor uses a separate ignored run root. For every child, the controller persists the complete `sdd-state render-prompt` output as a role envelope, then persists a short ASCII pointer without a trailing newline. The pointer is the exact `spawn_subsession` prompt and tells the child to read and obey the envelope.

Before spawn, a candidate pointer must match the stored pointer by `cmp` and SHA-256. The returned session ID is recorded immediately. Before admitting a child report, the raw first user message from that child is compared byte-for-byte with the stored pointer. A mismatch is terminal for the successor run. This transport avoids reconstructing a long prompt in an API call.

## Completion

Completion requires the read-only audit, independent task review, the small-workspace spread implementation and review, and a Frontier final review from `7cbbf60cab81ed4189327a374f44ddecd420a51d` through final `HEAD`. There must be no unresolved load-bearing findings, no source changes outside the allowed paths, a clean `git diff --check`, and a clean worktree apart from the pre-existing untracked original plan `docs/superpowers/plans/2026-08-25-graph-workspace-boundary-layout.md`.
