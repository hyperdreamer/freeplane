# Graph Workspace Batch E V7 Recovery Design

## Recovery Boundary

The V6 run is terminal at `DISPATCH_MISMATCH_BLOCKED`: its implementer transcript ended after partial verification without a status token or report. No V6 child result is admitted. The active worktree intentionally preserves the V6 two-file edits as the next recovery input.

V7 starts from committed `HEAD` `6291ad82fa807d4c45da04b2ecff57c935d1d50d` and the only dirty source paths are:

- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphHitIndex.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphInteractionControllerShould.java`

The current partial implementation already contains an exponent-tagged `ScaledValue` distance path and finite-coordinate regression tests. V7 Task 1 must finish, validate, and commit that work in place, correcting it only where fresh tests or direct arithmetic inspection demonstrate a defect. It must not reset, checkout, clean, or discard the dirty files.

## Numerical Contract

Edge hit testing must support every finite `LayoutPoint`, including ordinary coordinates, mixed-magnitude coordinates, near-`Double.MAX_VALUE` spans, positive subnormal offsets, zero-length segments, endpoint clamping, and zero tolerance. Preserve independently meaningful differences and tolerance comparisons; no common-scale underflow, squared-distance overflow, `NaN` acceptance, or conversion of a positive subnormal distance to zero may alter hit results. Non-finite final distances are misses. Preserve nearest-edge ordering and key tie-breaking.

## Scope And Completion

Task 1 owns only the two dirty source/test paths and must produce one bounded commit. The child should use the existing partial regressions as its red/green starting point, run the full named suites, inspect the actual diff, stage exactly the two paths, commit, and write its report. It should avoid exploratory searches once the required evidence is obtained.

Task 2 remains the deterministic keyboard traversal and lightweight Swing virtual accessibility work over immutable graph state, geometry, and viewport values. It begins only after a fresh Task 1 review approves the committed correction.

## Evidence Rules

- Preserve V6 and every earlier terminal SDD root byte-for-byte; they are diagnostic history only.
- Persist rendered prompts and pass bytes verbatim to children; compare completed-child initial messages before admission.
- Use bounded logs and exact allowlists. Run fresh task reviews, scoped re-reviews, and final Frontier review.
