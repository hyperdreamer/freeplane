# Graph Workspace Batch E V11 Recovery Design

## Recovery Boundary

V10 is terminal at `DISPATCH_MISMATCH_BLOCKED` after its correlated Task 2
Advanced implementer stopped after editing and rerunning verification, but before
returning a contract status, writing its report, or committing. The V10 state,
progress ledger, envelopes, dispatch receipts, and incomplete child output remain
byte-preserved diagnostic history. No V10 Task 2 report or transcript claim is
admitted as evidence.

The source baseline is committed `HEAD`
`5cb7dfd07bf701ae9a5b8e774031ab9338189995`. The active worktree intentionally
contains exactly these six unstaged Task 27 paths:

- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/TraversalDirection.java`
- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphTraversalOrder.java`
- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvas.java`
- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphCanvas.java`
- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvasShould.java`

These are quarantined inherited implementation input, not a completed deliverable.
V11 starts from the dirty state deliberately, finishes and independently validates
it in place, and commits exactly those files. It must not reset, clean, checkout,
stash, discard, or reproduce the inherited changes in another worktree.

## Execution

1. A fresh Advanced implementer receives the exact six-path scope, directly
   inspects the inherited diff and current source, verifies the Task 27 contract,
   corrects only defects it can demonstrate, runs the focused and full canvas
   suites under Zulu 21, commits the six paths, and writes a bounded report.
2. A fresh Frontier task reviewer reviews the committed Task 27 range from
   `5cb7dfd07bf701ae9a5b8e774031ab9338189995` to the new commit without relying
   on V10 child output. Any load-bearing finding follows the normal bounded
   fix/re-review loop.
3. A fresh Frontier final reviewer covers the entire branch from merge base
   `02f02355d9a33851a8a4417c4610d1897f716a50` through final `HEAD`, including
   the accepted Task 25/26 commits, finite-coordinate correction
   `5cb7dfd07bf701ae9a5b8e774031ab9338189995`, and the Task 27 commit. It runs
   the full graph-plugin suite and reconciles all findings.

## Contracts

Task 27 remains limited to deterministic keyboard traversal and lightweight
virtual Swing accessibility over current immutable canvas state. Traversal uses
only rendered geometry: node centers and non-suppressed hull label anchors.
Suppressed or geometry-less endpoints are absent from tab order, directional
selection, hit behavior, and accessible children. Viewport transformation is the
only source for accessible component bounds.

Selected unmodified arrows traverse; no-selection arrows pan; Shift arrows always
accelerate pan; Tab and Shift+Tab cycle visible endpoints; Enter opens the selected
source endpoint; Escape cancels preview before clearing selection. Virtual
accessibility objects retain only the canvas and endpoint key, resolve current
state/paint data per call, contain safe projected labels and map identity, and do
not reveal raw text, exclusions, colors, scale factors, source models, or
suppressed endpoints.

## Verification

The V11 plan is parsed by `sdd-state validate-plan` and the real-parser
body-extraction probe before initialization. Its clean-preflight conflict is
explicitly approved only for the six documented paths. Every child receives a
persisted renderer-produced role envelope through a byte-stable pointer prompt;
its initial user message is checked before a result is admitted. The source commit
uses the exact six-path allowlist and the required
`2026-08-10-graph-workspace:` subject. Completion requires fresh task and final
Frontier approval plus a clean Git status.
