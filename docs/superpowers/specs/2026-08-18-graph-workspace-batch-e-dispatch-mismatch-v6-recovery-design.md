# Graph Workspace Batch E V6 Recovery Design

## Recovery Boundary

The V5 run is terminal at `TASK_BLOCKED`: its fix-round child stopped without a status token or required report after leaving uncommitted changes in the two Task 1 allowlisted files. Those partial source edits are preserved as intentional preflight input for V6; no predecessor artifact is treated as a completed fix.

V6 starts from committed `HEAD` `4597825bf14cd36f3bad82fab929cdbda79323e7` with the exact dirty paths:

- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphHitIndex.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphInteractionControllerShould.java`

The V6 Task 1 child must inspect and either retain or replace those partial edits in place. It must not reset, checkout, clean, or discard them. Only a completed, independently reviewed commit becomes admitted work.

## Numerical Contract

`GraphHitIndex.edgeAt` must support every finite `LayoutPoint`, including mixed-magnitude coordinates, near-`Double.MAX_VALUE` spans, subnormal offsets, zero-length segments, endpoint clamping, and ordinary coordinates. Distance arithmetic must preserve independently meaningful coordinate differences and tolerance values without common-scale underflow, overflow, or `NaN` acceptance. Non-finite intermediate/final distances are misses. Nearest-edge ordering and key tie-breaking remain unchanged.

The regression set includes:

- symmetric near-limit span with on-segment and off-segment queries;
- mixed-magnitude horizontal segment `(10.0, 1e308)` to `(1e100, 1e308)` with an on-segment query;
- tiny perpendicular offset `1e-20` against an `8e307` span with tolerance `1e-100` that must miss.

## Task Boundary

Task 1 owns only the two dirty allowlisted source/test files and must commit them after red/green evidence. Task 2 begins only after Task 1’s fresh review resolves F-1. Task 2 implements deterministic traversal and lightweight virtual Swing accessibility over immutable graph state, geometry, and viewport values.

## Evidence Rules

- Preserve V5 and every earlier terminal SDD root byte-for-byte.
- Do not cite the V5 incomplete child transcript or blocked report as implementation evidence; use fresh tests and the new commit.
- Persist each rendered prompt and pass its bytes verbatim to the typed child dispatch. Compare completed child initial messages before admitting reports.
- Use bounded logs, disposable probes, exact staged allowlists, fresh task reviews, scoped re-review, and a final Frontier review.
