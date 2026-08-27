# Graph Workspace Layout Calibration Remediation Design

- Date: 2026-08-27
- Status: Approved for implementation
- Scope: Recalibrate the Graph Workspace force layout so real maps settle into a readable, separated layout instead of a frozen cramped seed scatter.

## Context

The layout-cramming remediation (`2026-08-27-graph-workspace-layout-cramming-remediation`, commit `cd3f611360`) removed GraphStream's edge-insertion teleport by overriding `chooseNodePosition` as a no-op. Restarting Freeplane with that fix confirmed the teleport is gone, but the graph is still cramped. A headless probe that replicates the production pipeline (real `GraphStreamLayoutEngine`, the `PerceptualIdlePolicy.spikeDefaults()` idle rule, and a realistic single-map tree of 25 nodes, 11 enclosures, 6 edges matching the reported screenshot structure) measured four independent defects with the fixed code:

1. **The settle loop freezes at the seeds.** Per-step solver movement is ~0.001-0.006 world units because the typed attraction factor is `0.0001`. The idle policy declares idle after 8 frames (movement below max 0.05), so the engine stops at step 8 having rearranged nothing.
2. **The uniform 50.0-unit seed square is far too small.** 25 nodes seeded in a 47x49 unit region produce min pairwise distance 2.1 and 61 of 300 node pairs overlapping (nodes have radius 8, so they need >= 16). For the 2000-node engineering target the mean seed spacing is ~1.1 units.
3. **Enclosure anchors are seeded at independent random points, so hulls balloon.** The root hull spans 156x159 while the node content spans 47x49; every hull must contain its scattered anchor. This is the reported "large empty container with content crammed in the center".
4. **The rest-24 hierarchy springs collapse the group structure.** During settling, all 11 anchors drift into a 45x60 unit ball around the root anchor; sibling group boundaries overlap into a mush regardless of where the seeds started.

## Design

Four production changes across two files, plus test updates in the existing force suite.

### 1. Count-scaled seed spread (`GraphStreamLayoutEngine`)

Replace the flat `INITIAL_POSITION_SPREAD = 50.0` with a floor plus a per-node term:

```java
private static final double MIN_INITIAL_POSITION_SPREAD = 50.0;
private static final double INITIAL_POSITION_SPREAD_PER_NODE = 20.0;

private static double initialPositionSpread(final GraphProjection projection) {
    return Math.max(MIN_INITIAL_POSITION_SPREAD,
        INITIAL_POSITION_SPREAD_PER_NODE * Math.sqrt(Math.max(1, projection.projectedNodeCount())));
}
```

25 nodes seed in a ~100-unit square, 200 nodes in ~283 units, 2000 in ~894.

### 2. Structured deterministic seeds (`GraphStreamLayoutEngine`, new package-private `Seeds` class)

Replace the per-particle independent random seed with a structure-aware deterministic placement:

- **Map-root anchors**: identity-derived random position inside the count-scaled spread (unchanged mechanism).
- **Non-root enclosure anchors**: the parent anchor's center plus a deterministic ring position. The ring angle is `2 * PI * siblingIndex / siblingCount` from the parent's ordered `directEnclosures()` list; the radius is `100.0` for depth-1 groups and `60.0` for deeper groups (`GROUP_SPACING`, `SUB_GROUP_SPACING`). An enclosure without a parent hull falls back to the identity-derived random seed.
- **Nodes**: the innermost containing enclosure's center plus a ring position of radius `24.0` with `+/- 2.0` jitter (`NODE_RING_RADIUS`, `NODE_RING_JITTER`), angle derived from the node's identity hash. Nodes without a containing enclosure use the identity-derived random seed.

All seeds remain deterministic per workspace/identity; different workspaces still produce different positions because map-root anchors and node ring angles derive from workspace-scoped hashes.

### 3. Per-link rest lengths (`GraphStreamLayoutEngine` `ForceLink`, `TypedSpringBox`)

`ForceLink` gains a `restLength` field. Relationship and containment links use the existing `REST_LENGTH = 24.0` (made package-private); hierarchy links use `100.0` for a child of depth <= 1 and `60.0` otherwise, matching the seed rings so the springs hold the seeded group structure instead of collapsing it. `TypedSpringBox.addTypedAttraction` computes `(distance - link.restLength) * ATTRACTION_FACTOR * multiplier(link.kind)`.

### 4. Force calibration (`TypedSpringBox`)

- `ATTRACTION_FACTOR`: `0.0001` -> `0.05` (convergent springs; the settle loop now rearranges instead of freezing).
- Native repulsion: the protected `SpringBox.K2` field is set to `REPULSION_FACTOR = 16.0` in the constructor (was `0.024`). At the idle movement bound (max 0.05) this keeps node pairs separating until they reach ~17+ units, i.e. no overlapping radius-8 nodes.
- `LayoutCalibration` multipliers (0.15 / 0.30 / 1.0), the cross-map aggregate cap, pin handling, solver quality, and the `chooseNodePosition` no-op are unchanged.

### Measured effect (headless probe, same 25-node tree, settle to production idle rule)

| Metric | Before calibration | After calibration |
| --- | ---: | ---: |
| Settle loop idle at step | 8 | ~1000 (animated settle) |
| Node pairs closer than 16.0 | 61 / 300 | 0 / 300 |
| Min pairwise node distance at idle | 2.1 | ~17 |
| Root hull span vs node content span | 156x159 vs 47x49 | ~345x346 vs ~250x255 |
| Group anchors at idle | 45x60 ball | separated ring ~100 from root |

The longer settle is intentional: the layout now animates into a readable state and stops only when per-frame movement is perceptually negligible.

### Test updates (`TypedForcesShould`)

1. The two movement regressions (`firstStepDoesNotTeleportSeededParticlesOntoTheirNeighbours`, `aTopologyChangeDoesNotTeleportRetainedParticles`) raise their bound from `1.0` to `8.0`: with the stronger springs a legitimate one-step movement can reach ~1.15 units, while the teleport defect measures 36.7-42.1 units, so 8.0 still discriminates with margin.
2. New `largerWorkspacesSeedWiderThanTheMinimumSpread`: a 200-node projection must seed with greatest pairwise distance > 150 (measured 367; the flat-50 mutant measures ~100).
3. New `hierarchyAnchorsSeedOnTheGroupRing`: in `baseline(1)` the child anchor must sit within 10 units of distance 100 from the root anchor (measured exactly 100; independent random anchor seeds can never reach 90-110 with the 50-unit floor).
4. Replace `reduceAnchorDistanceChangeWhenEnclosuresHaveAHierarchyLink` (whose premise — hierarchy attraction at 24-unit anchor separation — is superseded by the 100-unit hierarchy rest) with `hierarchyLinksPullNestedAnchorsTowardTheHierarchyRestLength`: with parent/child pinned at 0 and 400, after 300 steps the nested anchor distance is 150 (measured) vs 309 for the unlinked pair; asserting `nestedDistance > 100` and `nestedDistance < peerDistance` pins the rest-length behavior (the rest-24 mutant measures ~70 and fails).

## Deferred work

The cross-map aggregate budget still routes a cross-map-linked particle's entire native repulsion vector through the 0.005-unit cap (`TypedNodeParticle.scaleRepulsion` gates on the particle-level `hasCrossMapLink`). Per-pair map classification in the Barnes-Hut path would break the 2000-node performance budget, so this stays a documented limitation with a follow-up task; the fan-out cap test is unchanged.

## Testing

1. Add the new/updated regressions and run the focused `TypedForcesShould` suite; each must fail against the pre-calibration implementation for the intended mechanism (movement thresholds, spread formula, anchor ring, hierarchy rest).
2. Apply the production changes and rerun the focused suite green (17 tests).
3. Falsifiability probes in disposable copies: (a) remove the `chooseNodePosition` no-op -> both movement regressions fail; (b) revert `hierarchyRestLength` to 24 -> the new hierarchy test fails; (c) revert the spread formula to the flat floor -> `largerWorkspacesSeedWiderThanTheMinimumSpread` fails; (d) revert anchor seeding to independent random seeds -> `hierarchyAnchorsSeedOnTheGroupRing` fails. Restore exact bytes, verify SHA-256, rerun green.
4. Run the complete `:freeplane_plugin_graph:test` suite with `JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu`.
5. Run `git diff --check` and verify that only the three allowlisted paths changed.

## Scope

Only these three paths may change:

- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java`
- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/TypedSpringBox.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java`

No changes to `TypedNodeParticle`, `LayoutCalibration`, public layout interfaces, projection code, canvas code, persistence, or dependencies. The final result retains all prior Graph Workspace commits and adds one focused calibration commit.
