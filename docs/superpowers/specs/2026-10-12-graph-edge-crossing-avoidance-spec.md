# Graph Workspace Edge-Crossing Avoidance — Technical Specification

- Date: 2026-10-12
- Topic slug: `graph-edge-crossing-avoidance`
- Approved design (immutable input): `docs/superpowers/specs/2026-10-12-graph-edge-crossing-avoidance-design.md` (revision 6, review closed 0 blockers / 0 majors)
- Mockup and measured fixture: `docs/superpowers/specs/mockups/2026-10-12-graph-edge-crossing-avoidance/`
- Module: `freeplane_plugin_graph` only
- Code baseline: this worktree at commit `7c4ef8a2c04413008e9859190f28e8a838a67a96`; every `file:line` reference below is to that commit
- Status: implementation-ready
- Mandates N1–N8 from design §12 are placed in §11; every other section is derived from the design without new requirements.

## 1. Scope, Constraints, Constants

### 1.1 Scope

Implement, exactly as designed:

1. `layout/EdgeConflictCorrection` — a new verified relocation stage applied by `LayoutWorker.accept` immediately after
   `BoundarySeparationCorrection`, with conflict detection, a conflict index, deterministic candidates, A1–A4 acceptance,
   rejection-retry, and the §5.5 rebuilt boundary record.
2. `geometry/EdgeRouter` — a publish-time bounded routing pass over the final positions and hull geometry, invoked in
   `LayoutSettleLoop` between `geometryEngine.computeHulls(...)` (`LayoutSettleLoop.java:547`) and `CanvasState.of(...)`
   (`LayoutSettleLoop.java:551`, failure publish `LayoutSettleLoop.java:725-727`).
3. `projection/CanonicalKeys` — the single public canonical-encoding path replacing `layout/CanonicalLayoutKeys`.
4. `layout/BoundaryInvariants` — the shared document/side-effect-free detection and terminal-classification home used by
   both the boundary stage and the edge stage.
5. Frame and canvas plumbing: `EdgeConflictDiagnostics` on `LayoutFrame`, `EdgePaths` on `CanvasState`,
   `BoundarySeparationResult.geometry()`, the four-argument `LayoutFrame.withDiagnostics`, merged displacement accounting.

Non-goals are exactly design §3; in particular hulls are not router obstacles, enclosures are never relocated, nothing is
persisted, and no feature flag exists (design §11). The unchanged interaction contract (design G7) is selection, hover,
dimming, search, pins, viewport, hit-testing (extended only as §6.8 specifies) and the 2,000-node/5,000-edge commitment.
Relocations and routed paths are view geometry: nothing is routed through `GraphCommand` and nothing new is written to the
`.fpg` file.

### 1.2 Constraints

- Java 8 source/target; production excerpts use Java 8 syntax. All geometry lists are immutable
  (`Collections.unmodifiableList`/`unmodifiableMap`) and every public value type validates constructor inputs the way the
  existing types do (`Objects.requireNonNull`, nonnegative counts, finite coordinates).
- Determinism: no iteration over `HashMap` order participates in any decision. The router and the relocation stage sort by
  the canonical keys of §2.1 before any choice.
- No core-module change and no `freeplane_plugin_graph` public class outside the packages named in §2.
- No `System.nanoTime()` value enters diagnostics; timings live only in `EdgeConflictTimings` and `EdgeRouteTimings`.

### 1.3 Pinned constants (design §13)

All constants are `public static final` on the owning class unless noted. All values are world units unless the table says
otherwise. The "invariant" column states the design constraint satisfied; "rationale" is the specification's justification
for the chosen number.

| Constant | Owner | Value | Unit | Design invariant | Rationale |
| --- | --- | --- | --- | --- | --- |
| `EDGE_NODE_CLEARANCE` | `EdgeRouter` | `3.0` | world units | `> 0` and `≤ MIN_GAP / 2` | `MIN_GAP = 6.0` (`NodeSeparationProjection.MIN_GAP`), so `3.0` is the largest legal value: a routed path may pass exactly between two discs standing at the minimum gap, splitting the 6.0 gap evenly. |
| `EDGE_CLEARANCE` | `EdgeRouter` | `3.0` | world units | `> 0` and `≤ MIN_GAP / 2` | Same bound and same reason. Equal to `EDGE_NODE_CLEARANCE` so the sampled-quadratic fidelity bound (§6.5) is `min(...)/2 = 1.5` on both obstacle classes. |
| `CROSSING_CLEARANCE` | `EdgeConflictCorrection` | `16.0` | world units | `> 0` and `> largest drawn node radius` | Largest drawn radius is `BASE_RADIUS (8.0) × MAX_SCALE (1.75) = 14.0`; `16.0` exceeds it by `2.0`, so a mirrored disc leaves the opposite chord's line with a rim-to-line gap of `16.0 − 14.0 = 2.0 > ROUTE_EPSILON`, and the mirror direction cannot reread as a touch. |
| `MAX_BEND` | `EdgeRouter` | `96.0` | world units | `> 0`; absolute bound on ladder offsets | `96.0 = 12 × BASE_RADIUS`: a bend may reach twelve base radii, enough to clear the largest disc (radius 14 plus clearance 3 needs offset 17) with margin, while any single bow/dogleg excursion stays bounded; rungs whose `fraction × chordLength` exceeds it are skipped (§6.3). |
| `ROUTE_LENGTH_CAP` | `EdgeRouter` | `1.25` | ratio | live iff analytic ratio `≤` cap | Smallest quarter-ratio that keeps the same-side dogleg `0.30` rung live (`1.230242 ≤ 1.25`) while rejecting dogleg `0.50` (`1.535184`): the published path is at most 25 % longer than the anchor chord. Derived live rung list in §1.4. |
| `ROUTE_EPSILON` | `EdgeRouter` | `1e-9` | world units | covers only degenerate touch | Matches the house precision `BoundarySeparationCorrection.SUPPORT_COMPARISON_EPSILON = 1e-9`; `10⁻⁹` is nine orders below the pinned clearances (`3.0`), so it can never mask a real violation while absorbing floating-point touch. Crossing predicates are sign-only (no epsilon on the sign), normalise the four points of a predicate by one common binary exponent and can never produce a non-finite product, so extreme spans cannot silently turn every crossing degenerate. |
| `MAX_RELOCATION_ROUNDS` | `EdgeConflictCorrection` | `4` | count | `≥ 1` | Equals `BoundarySeparationCorrection.MAX_DISPLACEMENT_ROUNDS = 4`; every accepted round strictly shrinks the conflict set by A1, so four rounds bound the stage exactly as the boundary stage bounds its own displacement rounds. |
| `MAX_VERIFICATIONS_PER_FRAME` | `EdgeConflictCorrection` | `64` | count | bounds every A1–A4 attempt including retries | Equals `NodeSeparationProjection.MAX_PASSES = 64`; the per-frame detection count is bounded by `1 + MAX_RELOCATION_ROUNDS + MAX_VERIFICATIONS_PER_FRAME + 1`, and one batch of six candidates plus its six singles never exhausts the budget for a frame with a handful of conflicts. Exhaustion sets `verificationBudgetExhausted`, never an exception. |
| `BOW_SAMPLE_TOLERANCE` | `EdgePath` | `0.75` | world units | max sagitta `≤ min(EDGE_NODE_CLEARANCE, EDGE_CLEARANCE) / 2 = 1.5` | Half the allowed fidelity bound (safety margin for the §6.5 single-sample-list contract): the drawn polyline deviates from the true quadratic by at most `0.75`, i.e. a quarter of the `3.0` clearance. |
| `FAN_HULL_RADIUS` | `EdgeRouter` | `16.0` | world units | `> 0`; hull-endpoint part of `endpointClearanceRadius` | Equals `BoundarySeparationCorrection.HULL_CLEARANCE = 16.0`: a hull endpoint has no drawn disc, and its fan region uses exactly the hull padding the boundary stage reserves, so the two stages agree on hull scale. |
| `CONFLICT_CELL_SIZE` | `EdgeConflictCorrection` | `48.0` | world units | `> 0`; the level-0 floor of the span-adaptive cell size with 64-bit cell-key encoding + outermost-ring fallback | `48.0 > 34.0 = 2 × (14.0 + EDGE_NODE_CLEARANCE)`, the largest inflated-disc diameter: at the root level every inflated disc overlaps at most a 2 × 2 cell block, bounding insertion cost. Larger than `NodeSeparationProjection.SPATIAL_CELL = 34.0` (the house grid) for the same reason. Frames wider than `CONFLICT_GRID_DIMENSION × 48.0` grow the cell instead (next row), so the floor is preserved exactly for the small frames that keep it. |
| `CONFLICT_GRID_DIMENSION` | `EdgeConflictCorrection` | `128` | count | level-0 grid dimension per frame axis | `baseCellSize = max(CONFLICT_CELL_SIZE, min(span / CONFLICT_GRID_DIMENSION, CONFLICT_MAX_CELL_SIZE))`, `span = max(maxX − originX, maxY − originY)` over the inserted primitives (non-positive/non-finite span falls back to `CONFLICT_CELL_SIZE`). A wide frame keeps ~128 cells per axis instead of one cell per 48 units, so a long chord costs O(traversed cells) after adaptation. Proven by the 65-chord fixture (span ≈ 235, base stays `48.0`) and the reference-scale fixture (adaptive base). |
| `CONFLICT_MAX_CELL_SIZE` | `EdgeConflictCorrection` | `1.0e9` | world units | `≥ CONFLICT_CELL_SIZE`; cap on the adaptive level-0 cell size | Keeps the 64-bit cell-key encoding and the outermost-ring sentinel meaningful for spans beyond ~2.1e18: the `(±1e300)` fixture still clamps to the outermost ring and takes the direct outermost pass. Proven by that fixture. |
| `CONFLICT_MAX_CELLS_PER_PRIMITIVE` | `EdgeConflictCorrection` | `4096` | cells | `> 0`; per-level bound on the cells one primitive may occupy | A chord whose traversal `max(\|dX\|, \|dY\|) + 1` (or an inflated disc whose closed-cell bounding-box count) exceeds the cap is recorded in the outermost set and paired directly against every other primitive at the level instead of being rasterised; no pair is dropped, and index memory is bounded for intermediate spans. Proven by the dense wide-span fixture of §8.5. |
| `CONFLICT_CELL_CAP` | `EdgeConflictCorrection` | `64` | inserted items | triggers subdivision, never truncation | A cell at or below the cap (at most `64` items) enumerates at most `64 × 63 / 2 = 2016` candidate pairs; a cell above the cap (at least `65` items) starts at `65 × 64 / 2 = 2080` pairs before subdivision, so §8.5's `2080` is exactly the first over-cap count; `64` matches `MAX_VERIFICATIONS_PER_FRAME` and keeps the cap contract testable. |
| `CONFLICT_MIN_CELL_SIZE` | `EdgeConflictCorrection` | `3.0` | world units | subdivision floor for the pinned base | Equals `EDGE_NODE_CLEARANCE`: a cell smaller than the smallest clearance cannot separate obstacles any better. For `baseCellSize = 48.0` the four halvings reach `3.0`; for an adaptive base the fixed `CONFLICT_MAX_SUBDIVISION_DEPTH` binds first, and an over-cap leaf still enumerates every pair (`indexBudgetExhausted`). |
| `CONFLICT_MAX_SUBDIVISION_DEPTH` | `EdgeConflictCorrection` | `4` | count | fixed halving depth | For the pinned `48.0` base the levels are `48.0 → 24.0 → 12.0 → 6.0 → 3.0 = CONFLICT_MIN_CELL_SIZE`; for an adaptive base larger than `48.0` the depth cap binds first, and an over-cap leaf still enumerates every pair (`indexBudgetExhausted`). |
| `OUTERMOST_CELL_INDEX` | `EdgeConflictCorrection` | `2147483647` (`Integer.MAX_VALUE`) | cell index | outermost-ring fallback | The largest nonnegative `int` representable in the 32-bit half of the 64-bit cell key; coordinates whose index would exceed it are clamped here (§4.2). |
| `DRAWN_BASE_RADIUS` | `EdgeConflictCorrection`, `EdgeRouter` | `8.0` | world units | must equal the engine rule | Same numeric rule as `GraphGeometryEngine` (`BASE_RADIUS = 8.0`) and `NodeSeparationProjection.NODE_RADIUS`; both stages read the actual drawn radius from `GraphGeometry.nodes().get(key).radius()` whenever geometry is available and use this constant only for conflict-index/ordering fallbacks. |

`ROUTE_EPSILON` is additionally the only epsilon used by the router for distance comparisons: a clearance rule fails iff the
measured distance is `< clearance − ROUTE_EPSILON`; a segment shorter than `ROUTE_EPSILON` is treated as a point for distance
measurement. Crossing predicates are sign-only and use no epsilon (§3.2); the common-exponent normalisation makes every
product finite (§5.1, §6.4).

### 1.4 Live rung list derived from `ROUTE_LENGTH_CAP`

Design §6.3's analytic ratios (anchor-to-anchor path over anchor chord) with `ROUTE_LENGTH_CAP = 1.25`:

| rung | offset fraction | analytic ratio | live (`ratio ≤ 1.25`) |
| --- | --- | --- | --- |
| bow | 0.15 | 1.014804 | yes |
| bow | 0.30 | 1.057116 | yes |
| bow | 0.50 | 1.147794 | yes |
| dogleg | 0.15 | 1.064390 | yes |
| dogleg | 0.30 | 1.230242 | yes |
| dogleg | 0.50 | 1.535184 | **no** (excluded, probe 8) |

Live ladder order (fixed by design §6.3): straight, bow `+0.15`, bow `-0.15`, bow `+0.30`, bow `-0.30`, bow `+0.50`,
bow `-0.50`, dogleg `+0.15`, dogleg `-0.15`, dogleg `+0.30`, dogleg `-0.30`. A rung is additionally skipped when
`|offset| > MAX_BEND`. Acceptance is still measured per candidate on the sampled `pathLength ≤ 1.25 × chordLength`, so a
live rung can fail on a specific geometry; the table only bounds which rungs are tried.

### 1.5 Derived values used throughout

- Largest drawn radius: `8.0 × 1.75 = 14.0`.
- Largest inflated-disc diameter: `2 × (14.0 + 3.0) = 34.0`.
- Maximum sagitta tolerance: `min(3.0, 3.0) / 2 = 1.5`; pinned sampling tolerance `0.75 = 1.5 / 2`.
- Bow sample count (§6.5): `n = max(1, ceil(sqrt(d / (2 × BOW_SAMPLE_TOLERANCE))))`, `d` = perpendicular distance of the
  control point from the attachment chord; `n + 1` published points.

## 2. New and Changed Types and Signatures

All new types are Java 8 `final` classes with `private` constructors and static factories unless stated. Accessors are
public; constructors validate like `BoundarySeparationTimings` (`< 0` → `IllegalArgumentException`).

### 2.1 `projection/CanonicalKeys` (new; replaces `layout/CanonicalLayoutKeys`)

`public final class CanonicalKeys` in `org.freeplane.plugin.graph.projection`. Every encoding currently in
`CanonicalLayoutKeys` moves here unchanged in literal format; `layout/CanonicalLayoutKeys.java` is deleted and all callers
(`BoundaryConflict`, `BoundarySeparationCorrection`, `BoundarySeparationDiagnostics`, `BoundaryInvariantAssertions`,
`BoundaryConflictShould`, `BoundarySeparationShould`, `BoundarySeparationCorrectionShould`, `CanonicalLayoutKeysShould`)
delegate to `CanonicalKeys`. No encoding is duplicated.

```java
public static String escape(String value);                       // % -> %25, | -> %7C, : -> %3A, , -> %2C
public static String endpoint(SourceNodeKey source);             // "m:<map>|p:<nodeId>" or "m:<map>|t:<path>"
public static String endpoint(ProjectedEndpointKey endpoint);    // node "n:"+endpoint(source); enclosure "e:"+endpoint(source)
public static String hull(EnclosureHullKey hull);                // sorted comma-joined endpoint(enclosure keys), unchanged
public static String nodeField(ProjectedNodeKey node);           // "n:" + endpoint(node.source()), unchanged
public static String anchorField(EnclosureHullKey hull);         // "a:" + hull(hull), unchanged
public static String pair(EnclosureHullKey first, EnclosureHullKey second);   // sorted hull keys joined by '|', unchanged
public static String node(ProjectedNodeKey node);                // == nodeField(node)
public static String nodePair(ProjectedNodeKey first, ProjectedNodeKey second);  // sorted "node|node"
public static String edge(ProjectedEdgeKey edge);                // "endpoint(first)~endpoint(second)"
public static String edgePair(ProjectedEdgeKey first, ProjectedEdgeKey second);// sorted "edge|edge"  (C1 identity)
public static String edgeNodePair(ProjectedEdgeKey edge, ProjectedNodeKey node); // edge(edge)+"|"+node(node) (C2 identity, N1)
public static String hullPair(EnclosureHullKey first, EnclosureHullKey second);  // == pair(first, second)
public static MapReferenceId mapOfField(String field);           // unchanged parsing of "n:"/"a:" displacement keys
```

Encoding rules pinned for the new keys. `escape` is applied to every node-id and structural-path component and replaces
`%`, `|`, `:` and `,` as `%25`, `%7C`, `%3A`, `%2C`; a map value is a canonical UUID and contains none of them. An
encoded endpoint therefore has exactly one unescaped `|` (its fixed `m:<uuid>|p:`/`|t:` separator), and every escaped
component may contain `~` but no unescaped `|`, `:`, `,` or `%`; `~` is not a composite separator and is not escaped.
`edge` joins its two encoded endpoints with `~`, and that joiner is the unique `~` immediately followed by `n:m:` or
`e:m:`, because a raw colon can occur only in the fixed literal prefixes, never inside an escaped component. `pair`,
`nodePair`, `edgePair`, `edgeNodePair` and `hullPair` join their sorted-or-fixed parts with `|`; each joiner is the
unique `|` immediately followed by the part's leading literal (`m:` for a hull, `n:m:`/`e:m:` for an edge or node), while
every internal `|` is followed by `p:` or `t:`. Every pair is emitted in ascending `String.compareTo` order of its two
encoded elements, so the key is symmetric and the encodings are injective: distinct inputs produce distinct keys.
`edgeNodePair` is the C2 identity mandated by N1, so two distinct passed-over nodes on one edge produce distinct keys; a
C1 key carries five unescaped `|` occurrences (two per edge plus the pair joiner) while a C2 key carries four (two per
edge, the pair joiner and the node's `m:...|`), so a C1 key can never equal a C2 key.

### 2.2 `layout/BoundaryInvariants` (new) and the boundary extraction

`public final class BoundaryInvariants` in `org.freeplane.plugin.graph.layout`. The detection and terminal-classification
code currently private in `BoundarySeparationCorrection` (the `detect` sweep, `Violation`, `PlanOutcome`, `plan`,
`FrameCapCache`, `terminalConflicts`, `changedHulls`, `nodeHulls`, `parents`, `enclosuresByHull`, `related`,
`containsInclusive`, `blockingPins`, the small vector helpers used by them) moves here; `BoundarySeparationCorrection`
keeps its public API and delegates. The extraction is behaviour-preserving and is pinned by the frozen characterization
fixture of `BoundaryInvariantsShould` (§9.1).

```java
public final class BoundaryInvariants {
    public BoundaryInvariants();                   // one instance per correction stage; owns all state below
    public static final class Violation {          // immutable; same semantics as the extracted private type
        public EnclosureHullKey first();
        public EnclosureHullKey second();
        public BoundaryConflict.Kind kind();
        public double penetration();
        public LayoutPoint translation();          // null for ANCESTOR_ESCAPE
        public String pairKey();                   // CanonicalKeys.hullPair(first, second)
    }
    public List<Violation> detect(GraphProjection projection, GraphGeometry geometry);
    public Set<String> detectPairKeys(GraphProjection projection, GraphGeometry geometry);
    public static Map<EnclosureHullKey, EnclosureHullKey> parents(GraphProjection projection);
    public static Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull(GraphProjection projection);
    public static Map<ProjectedNodeKey, List<EnclosureHullKey>> nodeHullsByKey(GraphProjection projection);
    public static Set<EnclosureHullKey> changedHulls(Map<ProjectedNodeKey, List<EnclosureHullKey>> nodeHullsByKey,
            LayoutPositions before, LayoutPositions after);
    public List<BoundaryConflict> terminalConflicts(GraphProjection projection,
            LayoutPositions positions, GeometryTextMetrics metrics, List<PinProjection> pins,
            GraphGeometry geometry, List<Violation> violations, boolean boundExhausted);
    public BoundarySeparationDiagnostics describe(GraphProjection projection,
            LayoutPositions positions, GeometryTextMetrics metrics, List<PinProjection> pins,
            GraphGeometry geometry, List<String> baselineResidualKeys,
            BoundarySeparationDiagnostics stageBaseline);
    long ancestorExactContainmentChecks();         // package-private: delegates preserve the pinned characterization
    long ancestorContainmentMemoHits();
    long capTraversals();
    long capCacheHits();
}
```

- **State.** `BoundaryInvariants` carries the extracted `AncestorContainmentMemo` and the `capTraversals` /
  `capCacheHits` counters as instance fields, so they persist across `apply` calls exactly as they did in
  `BoundarySeparationCorrection`. `BoundarySeparationCorrection` owns one private `final BoundaryInvariants` instance
  and delegates its package-private `ancestorExactContainmentChecks()`, `ancestorContainmentMemoHits()`,
  `capTraversals()` and `capCacheHits()` accessors to it, which is why
  `BoundarySeparationCorrectionShould.ancestorContainmentIsMemoizedAcrossIdenticalFrames` (1231-1244),
  `ancestorContainmentMemoRechecksAHullWhoseGeometryChanged` (1262-1277),
  `capSetsAreReusedWithinAFrame` (1281-1299) and
  `candidateSelectionStopsWhenNoSideHasAMovableContributor` (1310-1325) stay green. `EdgeConflictCorrection` owns its own
  private `final BoundaryInvariants boundaryInvariants` instance, so `detect`, the terminal classification of
  `terminalConflicts` and the `describe` rebuild share one memo and one pair of counters within that stage; the state
  influences work only and never results (G4).
- `detect` uses the instance's `AncestorContainmentMemo` for ancestor containment, returns the same ordered list
  (penetration descending, then pair key ascending) the boundary stage produced before extraction, with the same
  suppressed/ambiguous/related exclusions; `detectPairKeys` is the distinct, ascending key set used by A3. Neither method
  resets the memo.
- `terminalConflicts(..., boundExhausted)` builds one `FrameCapCache` per call from the supplied positions/hulls, runs the
  extracted `plan` classification-only (nothing is applied), adds the traversal counts to the instance's
  `capTraversals`/`capCacheHits`, and maps each violation to a `BoundaryConflict` with the same reason mapping the
  boundary stage uses (`BoundarySeparationCorrection.java:157-163`). The plan's `movedPairs` participate in the
  `ROUND_LIMIT` rule exactly as before: `boundExhausted = false` (the rebuild case) never produces `ROUND_LIMIT`; the
  boundary stage passes `rounds >= MAX_DISPLACEMENT_ROUNDS`, which reproduces the pinned
  `new BoundarySeparationCorrection(1)` characterization (`BoundarySeparationCorrectionShould.java:1000-1013`).
- `describe` is the §5.5 rebuild entry point. `baselineResidualKeys` (N2) is the boundary stage's terminal
  `residualHullPairs()` list for the pre-edge-stage positions, in exact `CanonicalKeys.hullPair` format, not sorted; its
  roles are (1) an inclusion guard — if `detectPairKeys(finalGeometry)` is not a subset,
  `describe` throws `EdgeConflictException` (defence in depth beside A3) — and (2) the source of the terminal
  "pre-existing residual" semantics. `stageBaseline` supplies the preserved stage-level fields. `describe` calls the
  instance's `detect` and then the instance's `terminalConflicts(..., false)` on the same hulls, never resets the memo or
  the counters, and therefore shares both with every other call on the instance. The result copies
  `stageBaseline.hullViolationsDetected()`, `rounds()`, `displacementRms()`, `displacementMax()`,
  `appliedDisplacements()`, `deltaRms()` and `deltaMax()` unchanged and recomputes `conflicts`,
  `residualHullPairs = new ArrayList<>(detectPairKeys(...))` (ascending) and `hullResidualViolations = detect(...).size()`.
- `BoundarySeparationCorrection` keeps `MAX_DISPLACEMENT_ROUNDS`, `HULL_CLEARANCE`, `BASE_RADIUS`,
  `BOUNDARY_PADDING`, `SUPPORT_COMPARISON_EPSILON` and its public `apply`; its `apply`/`separate` bodies call its
  `BoundaryInvariants` instance for detection and terminal classification.

`BoundarySeparationResult` gains exactly one member (design §4.2):

```java
public GraphGeometry geometry();
```

returning the terminal `GraphGeometry` (`terminalHulls`) the boundary stage already computed, stored in the result at
construction. No other member changes.

### 2.3 `layout/EdgeConflictCorrection`, result, diagnostics, timings, exception

```java
public final class EdgeConflictCorrection {
    public EdgeConflictCorrection();                       // maxDisplacementRounds = 4 for tests via package-private ctor
    public EdgeConflictResult apply(GraphProjection projection, LayoutPositions positions,
            GeometryTextMetrics metrics, List<PinProjection> pins,
            Set<ProjectedEndpointKey> visibleEndpoints, BoundarySeparationResult boundaryResult);
}

public final class EdgeConflictResult {
    public LayoutPositions positions();
    public EdgeConflictDiagnostics diagnostics();
    public EdgeConflictTimings timings();
    public BoundarySeparationDiagnostics boundaryDiagnostics();      // N3: rebuilt record, or the boundary record if no attempt accepted
    public int nodeResidualViolations();                             // recomputed on positions() when an attempt was accepted (rounds > 0)
    public Map<String, LayoutPoint> appliedDisplacements();          // edge-stage own map, canonical field keys
}

public final class EdgeConflictException extends RuntimeException {
    public EdgeConflictException(String message);
    public EdgeConflictException(String message, Throwable cause);
    public EdgeConflictException(Throwable cause);
}

public final class EdgeConflictDiagnostics {
    public enum RepairKind { SWAP, MIRROR, TRANSLATION }
    public static final class Repair {
        public RepairKind kind();
        public List<ProjectedNodeKey> nodeKeys();      // ascending CanonicalKeys.node order
        public Map<ProjectedNodeKey, LayoutPoint> fromCentres();
        public Map<ProjectedNodeKey, LayoutPoint> toCentres();
        public int batchIndex();                       // 0-based accepted-attempt index == rounds-1
    }
    public static EdgeConflictDiagnostics empty();
    public int conflictsDetected();                    // distinct C1 + C2 identity keys, first round
    public int crossingsDetected();                    // C1 count, first round
    public int edgeOverNodeDetected();                 // C2 count, first round
    public int crossingsResolved();                    // detected C1 minus residual C1
    public int edgeOverNodeResolved();                 // detected C2 minus residual C2
    public List<String> conflictsResidual();           // ascending distinct identity keys on positions()
    public List<Repair> repairsApplied();              // one per accepted candidate, application order
    public int rounds();
    public int verifications();                        // every batch or single attempt counted
    public boolean verificationBudgetExhausted();      // verification-phase stop; detection complete
    public boolean indexBudgetExhausted();             // direct full enumeration ran; no pair dropped
    public boolean detectionTruncated();               // detection-side stop; residual lower bound
    public long indexInsertedItems();                  // counted work, §4.3
    public long indexPairChecks();                     // counted work, §4.3
    public long verificationWork();                    // A2 tests + A3 comparisons + hull recomputes, §4.3
    public int indexSubdivisions();                    // counted work, §4.3
}

public final class EdgeConflictTimings {
    public EdgeConflictTimings(long detectionNanos, long indexNanos, long verificationNanos, long rebuildNanos);
    public long detectionNanos();
    public long indexNanos();
    public long verificationNanos();
    public long rebuildNanos();
    public long totalNanos();
}
```

`EdgeConflictDiagnostics.empty()` returns all counts zero, empty lists, all flags `false`. `conflictsResidual()` is
computed on the returned `positions()`, not on the pre-batch state. `crossingsResolved = crossingsDetected − residualC1`
and `edgeOverNodeResolved = edgeOverNodeDetected − residualC2` on the first-round detection. On a frame stopped by a
detection-side budget, `conflictsDetected` and `conflictsResidual` are the ascending set of distinct keys accumulated up
to the counted stop — a lower bound — and `crossingsResolved`/`edgeOverNodeResolved` are 0; `detectionTruncated()` is
the only signal of incompleteness. On a verification-phase stop (`verificationBudgetExhausted()`), detection is
complete and the residual is exact.

### 2.4 `geometry/EdgePath`, `EdgePaths`, `EdgeRouter`, route diagnostics and timings

```java
public final class EdgePath {
    public enum Shape { STRAIGHT, BOW, DOGLEG }
    public static EdgePath straight(LayoutPoint start, LayoutPoint end);
    public static EdgePath bow(LayoutPoint start, LayoutPoint control, LayoutPoint end);
    public static EdgePath dogleg(LayoutPoint start, LayoutPoint bend1, LayoutPoint bend2, LayoutPoint end);
    public Shape shape();
    public LayoutPoint start();
    public LayoutPoint end();
    public Optional<LayoutPoint> control();            // present iff BOW
    public List<LayoutPoint> definingPoints();         // straight [a,b]; bow [a,control,b]; dogleg [a,b1,b2,b]
    public List<LayoutPoint> samples();                // the single verified/painted polyline, §6.5
    public double length();                            // sum of consecutive samples distances
    public LayoutPoint arcLengthMidpoint();            // multiplicity cue, §6.7
    public LayoutPoint arrowAnchor(boolean atStart);   // §6.6 terminal-segment rule
    public double terminalSegmentLength(boolean atStart);
    public boolean equals(Object other);               // value equality over definingPoints()
    public int hashCode();
}

public final class EdgePaths {
    public static EdgePaths empty();
    public static EdgePaths of(Map<ProjectedEdgeKey, EdgePath> byEdge,
            EdgeRouteDiagnostics diagnostics, EdgeRouteTimings timings);
    public Map<ProjectedEdgeKey, EdgePath> byEdge();   // insertion order = canonical CanonicalKeys.edge order
    public Optional<EdgePath> path(ProjectedEdgeKey key);
    public EdgeRouteDiagnostics diagnostics();
    public EdgeRouteTimings timings();
}

public final class EdgeRouter {
    public EdgePaths route(GraphProjection projection, LayoutPositions positions, GraphGeometry geometry,
            Set<ProjectedEndpointKey> visibleEndpoints);
    public static boolean properlyCross(EdgePath first, EdgePath second);   // sample-polyline proper crossing, sign-only (§3.2)
}

public final class EdgeRouteDiagnostics {
    public static EdgeRouteDiagnostics empty();
    public int edgesStraight();
    public int edgesBow();
    public int edgesDogleg();
    public int edgesFallback();
    public int pathCrossingsDetected();                // straight-chord crossings before routing; lower bound on a truncated pass
    public List<String> pathCrossingsResidual();       // ascending distinct CanonicalKeys.edgePair keys after routing; lower bound on a truncated pass
    public int pathHullPenetrations();                 // (edge, hull) pairs, §6.9; lower bound on a truncated pass
    public boolean routeBudgetExhausted();             // obstacle/index budget stopped routing; remaining edges fallback
    public boolean detectionTruncated();               // detected-crossing/residual/hull pair budget stopped a pass
    public long routeIndexItems();                     // counted work, §6.2
    public long routeObstacleChecks();                 // counted work, §6.2
    public long routePairChecks();                     // counted work, §6.9
}

public final class EdgeRouteTimings {
    public EdgeRouteTimings(long indexNanos, long selectionNanos, long residualNanos);
    public long indexNanos();
    public long selectionNanos();
    public long residualNanos();
    public long totalNanos();
}
```

`EdgePaths` is the router's complete immutable result: it owns the per-edge paths, the diagnostics and the timings, so the
single `CanvasState` factory of §2.5 publishes all three through one parameter. `EdgePath.equals` compares `shape()`,
`definingPoints()` and the derived `samples()` list; it does not implement `Comparable`.

### 2.5 `CanvasState` EdgePaths-taking factory (N5) and `LayoutFrame.withDiagnostics` (N6)

`control/CanvasState` replaces its five-argument `of(...)` with the single factory and accessor:

```java
public static CanvasState of(long generation, GraphProjection projection, LayoutFrame layout,
        GraphGeometry geometry, EdgePaths edgePaths, OperationalStatus status);
public EdgePaths edgePaths();
CanvasState withStatus(OperationalStatus nextStatus);   // preserves edgePaths
```

The old five-argument `CanvasState.of(...)` is removed. All construction sites move in the same change:

- `LayoutSettleLoop.java:551` (normal publish) and `LayoutSettleLoop.java:725-727` (failure publish): route the frame's
  own positions through one `EdgeRouter.route(projection, frame.positions(), geometry, visibleEndpoints)` call and pass the
  returned `EdgePaths`; `visibleEndpoints` is computed once per frame with
  `ProjectedEndpointVisibility.visibleEndpoints(projection.nodes(), projection.enclosures())`.
- `GraphUpdateCoordinator.java:136` (`initializeState`) passes `EdgePaths.empty()` (empty projection/geometry).
- `GraphUpdateCoordinator.java:561` (`publishFailure`) passes `state.edgePaths()`.
- The remaining baseline test call sites at HEAD `7c4ef8a2c0`: `command/ContributorDeletionPlanShould.java:856`,
  `control/LayoutSettleLoopShould.java:1374`, `integration/GraphWorkspaceCommandAcceptanceShould.java:785,1230`,
  `smoke/GraphWorkspaceUiEvidence.java:281`, `window/GraphWorkspaceWindowModelShould.java:3024,3048`.
- Every further test fixture listed in §9.2.

The union of the bullets above and §9.2 is every `CanvasState.of(...)` call site at HEAD `7c4ef8a2c0`; the list is
exhaustive.

`layout/LayoutFrame` removes the three-argument form and gains the four-argument form (four parameters after the frame):

```java
public static LayoutFrame withDiagnostics(LayoutFrame frame, int residualViolations,
        BoundarySeparationDiagnostics boundaryDiagnostics, EdgeConflictDiagnostics edgeConflictDiagnostics,
        PerceptualIdlePolicy.IdleMeasurement idle);
public EdgeConflictDiagnostics edgeConflictDiagnostics();
```

`LayoutFrame.of(...)` initialises both diagnostics to their `empty()` values. The four-argument `withDiagnostics` replaces
all three-argument call sites: `LayoutWorker.EMPTY_FAILED_FRAME` (`LayoutWorker.java:35`), `LayoutWorker.accept`
(`LayoutWorker.java:297-299`), `LayoutWorker.failedFrame` (`LayoutWorker.java:375`), `LayoutSettleLoop.failedFrame`
(`LayoutSettleLoop.java:761`), `LayoutSettleLoopShould.java:1203,1410`, `GraphUpdateCoordinatorShould.java:1194,1604,1646`.
No three-argument overload and no parallel path survive (repository legacy policy; design §5.6).

### 2.6 `NodeSeparationProjection` predicate extraction

To give A2 pair identities without duplicating the gap predicate:

```java
public static List<String> violatingPairs(GraphProjection projection, LayoutPositions positions);
public static CountedViolatingPairs violatingPairsCounted(GraphProjection projection, LayoutPositions positions,
        int pairBudget);
```

`CountedViolatingPairs` is a new immutable value type with `pairs()`, `pairEvaluations()` and `truncated()`. `violatingPairsCounted`
returns the same ascending `CanonicalKeys.nodePair(a, b)` list for every unordered node pair with
`Math.hypot(ax−bx, ay−by) < radius(a) + radius(b) + MIN_GAP`, using the same `NODE_RADIUS × prominenceScale` rule as
`NodeSeparationProjection.project`, consuming at most `pairBudget` node-pair tests: the loop decrements the remaining
allowance before each test and stops at zero, returning the pairs examined so far with `truncated() == true`. The
`pairEvaluations()` count is the number of node pairs examined, counted as `verificationWork` (§4.3).
`violatingPairs(...)` delegates to the counted variant with an unbounded allowance (`Integer.MAX_VALUE`), so the
predicate has exactly one implementation and unbudgeted callers keep today's behaviour; `project` computes
`residualViolations()` as the size of the list (computed on its final
relaxed positions). `EdgeConflictCorrection`'s A2 uses the counted variant for **both** the current and the candidate
position sets and adds each call's `pairEvaluations()` to `verificationWork`, so the O(n²) predicate is inside the
verification-work budget.

## 3. Canonical Conflict Detection (design §5.1, C1/C2)

### 3.1 Straight drawn chord

For a projected edge `e` with both endpoints in `visibleEndpoints`:

- `anchor(e.first)` is `geometry.nodes().get(key).center()` for a node endpoint, or the published anchor
  `positions.anchors().get(hullKey)` (falling back to `hull.labelAnchor()` exactly as the painter does) for an enclosure
  endpoint.
- `chord.first = geometry.edgeAttachment(e.first(), anchor(e.second()))` and symmetrically for `second` — today's painter
  geometry (`GraphPainter.java:122-155`). If either anchor or either attachment is missing, the edge is not drawn and takes
  part in no conflict.
- A chord is **open** for intersection purposes; touch-only (zero-length or collinear contact) is not a conflict.

### 3.2 C1 edge–edge crossing

Two projected edges `e1`, `e2`, both with both endpoints visible, are a C1 conflict iff their open drawn chords properly
intersect. The predicate is evaluated on coordinates normalised by one common power of two so no product can overflow; it
is sign-only and applies no epsilon:

```
orientationSign(e, p, q, r):
  px = Math.scalb(p.x, -e); py = Math.scalb(p.y, -e)      // and likewise q, r
  cross = (qx - px) * (ry - py) - (qy - py) * (rx - px)   // |cross| < 32, always finite
  return cross == 0.0 ? 0 : Double.compare(cross, 0.0)     // -0.0 maps to 0, zero = touch

properlyCross(a, b, c, d):
  e = max over {|a.x|, |a.y|, |b.x|, |b.y|, |c.x|, |c.y|, |d.x|, |d.y|} of Math.getExponent(v)
  return orientationSign(e, a, b, c) * orientationSign(e, a, b, d) < 0
      && orientationSign(e, c, d, a) * orientationSign(e, c, d, b) < 0
```

All four points share the exponent `e` of the dominant coordinate, so every scaled coordinate has magnitude `< 2`, every
scaled difference has magnitude `< 4`, and every cross product has magnitude `< 32`: a non-finite orientation cannot
occur. A zero computed orientation is a touch and not a conflict. Edges sharing an endpoint are never C1 (design §5.1;
the only possible shared-node contact is at the node, and the convexity argument covers shared hull endpoints). The
conflict identity key is `CanonicalKeys.edgePair(e1.key(), e2.key())`.

### 3.3 C2 edge-over-node

For a projected edge `e` with both endpoints visible and a projected node `n` whose disc exists in `geometry.nodes()` and
is not one of `e`'s own node endpoints, `(e, n)` is a C2 conflict iff

```
segmentDistance(chord(e), center(n)) − radius(n) < EDGE_NODE_CLEARANCE − ROUTE_EPSILON
```

where `segmentDistance` is the exact point-to-segment distance of the closed chord (zero-length chords are point
distance). The visibility filter is part of the predicate: an edge the painter does not draw never relocates a node. The
candidate for a C2 conflict moves `n` and exists whenever `n` is projected and unpinned, independently of whether `e`'s
other endpoint is an enclosure endpoint; a passed-over node that is a descendant of one of `e`'s enclosure endpoints is
still C2 (whole-batch verification and re-detection handle the hull fixed point, design §5.1). The conflict identity key is
`CanonicalKeys.edgeNodePair(e.key(), n)` (N1). A node endpoint that is an enclosure source is never a projected-node C2
target unless it also appears in `projection.nodes()`.

### 3.4 Detection output and ordering

`detectConflicts(projection, positions, geometry, visibleEndpoints)` returns all distinct C1 and C2 identity keys, ascending
by key, deduplicated by key. Counts: `crossingsDetected = |C1|`, `edgeOverNodeDetected = |C2|`,
`conflictsDetected = |C1| + |C2|`. Every decision downstream consumes this ordered list; `HashMap` iteration is never
observed, and every enumeration over chords/discs is ordered by `CanonicalKeys.edge` / `CanonicalKeys.node`.

## 4. Conflict Index (design §9)

### 4.1 Indexed primitives

The index is a uniform grid over both conflict classes. For one detection pass:

- every visible chord is inserted into every cell its **segment actually traverses** (a segment-cell traversal, not its
  bounding box), and
- every projected node disc inflated by `EDGE_NODE_CLEARANCE` is inserted into every cell it **overlaps** (again not its
  bounding box alone).

C1 pairs are enumerated only from chords that share a cell; C2 pairs only from a chord and an inflated disc that share a
cell. Pair keys are accumulated in a `TreeSet<String>`/`LinkedHashSet<String>` and emitted once in ascending identity-key
order. A pair found in several cells is emitted once.

### 4.2 Cell-key encoding, origin, normalisation, outermost ring

- Origin: `originX = min` over every inserted primitive's bounding-box `x` (chord endpoints; inflated-disc
  `center.x − radius(n) − EDGE_NODE_CLEARANCE`); `originY` likewise. With the origin at the frame minimum, every cell
  index is `≥ 0` by construction.
- Normalisation: `cellX(x) = clamp(floor((x − originX) / cellSize))` where `cellSize = baseCellSize / 2^level`,
  `baseCellSize` is the span-adaptive level-0 size of §4.3 and `level ∈ [0, CONFLICT_MAX_SUBDIVISION_DEPTH]`;
  `clamp(v) = min(v, OUTERMOST_CELL_INDEX)` for finite `v ≥ 0`. `floor(−0.0) = 0`.
- 64-bit key: `key = ((long) cellX << 32) | (cellY & 0xffffffffL)`. Because `cellX, cellY ∈ [0, 2^31−1]` the key is
  injective.
- **Outermost-ring fallback:** a primitive whose `cellX` or `cellY` is clamped to `OUTERMOST_CELL_INDEX`, or whose
  traversal/bbox cell count exceeds `CONFLICT_MAX_CELLS_PER_PRIMITIVE`, is recorded in the `outermost` set for its level
  and is **not inserted into any cell**: if either chord endpoint clamps (or either bounding-box corner of an inflated
  disc clamps), cell insertion for that primitive is skipped entirely, so a segment whose real span would cross a
  ~2^31-cell range is never rasterised. At the end of the level's enumeration, every primitive in `outermost` is paired
  **directly** against all other primitives it can conflict with (C1 against the chords, C2 against the inflated discs),
  without relying on cell locality. This guarantees the design's rule that extreme spans cannot silently collapse the
  grid to a single hub cell: coordinates outside the representable index range are still checked, at a work bound of
  `O(|outermost| × (chords + inflated discs))` primitive-pair examinations before canonical-key deduplication, and the
  frame sets `indexBudgetExhausted` when the direct pass runs (the direct pass itself is charged to the index-item and
  pair-check budgets of §4.3).

### 4.3 Span-adaptive grid, subdivision cap and counted work

- **Level-0 cell size (span-adaptive).** `baseCellSize` is chosen once per detection pass:
  `span = max(maxX − originX, maxY − originY)` over the inserted primitives and
  `baseCellSize = max(CONFLICT_CELL_SIZE, min(span / CONFLICT_GRID_DIMENSION, CONFLICT_MAX_CELL_SIZE))`; a
  non-positive or non-finite span falls back to `CONFLICT_CELL_SIZE`. Small frames keep the pinned `48.0` grid; wide
  frames keep `CONFLICT_GRID_DIMENSION` cells per axis instead of one cell per 48 units; the `CONFLICT_MAX_CELL_SIZE`
  cap keeps the outermost-ring sentinel reachable by the 64-bit encoding. The fixtures prove the invariants: the
  65-chord fixture keeps `baseCellSize = 48.0` and its pinned `indexSubdivisions = 4`, and the `(±1e300)` fixture
  still clamps to the outermost ring.
- **Per-primitive cell cap.** A chord whose traversal length `max(|dX|, |dY|) + 1` exceeds
  `CONFLICT_MAX_CELLS_PER_PRIMITIVE`, or an inflated disc whose bounding-box cell count exceeds it, is recorded in the
  level's `outermost` set and checked directly against every other primitive at that level; no pair is dropped.
- Choose the level once per detection pass: start at `level = 0`; while any occupied cell's inserted-item count exceeds
  `CONFLICT_CELL_CAP` and `level < CONFLICT_MAX_SUBDIVISION_DEPTH`, increment the level and recount. Subdivision never
  truncates: a cell that subdivides is replaced by its four children, and every primitive inserted in it is re-inserted in
  every child it traverses/overlaps.
- If a cell at the deepest level still exceeds the cap, the pass **enumerates its pairs in full** (no pair is dropped) and
  sets `indexBudgetExhausted = true` on the frame diagnostics.
- Counted work and budget check points (§13.1):
  - `indexInsertedItems` increments once per primitive per cell written (at every level, including every recount) plus
    once per direct outermost examination. The index-item budget is checked **before every cell write and inside every
    subdivision re-collect**, so a recount cannot insert past the cap unnoticed.
  - `indexPairChecks` increments once per examined primitive pair after canonical-key deduplication. The pair-check
    budget is checked **before every `properlyCross` / `segmentDistance` test, including the outermost direct pass**.
  - `verificationWork` increments once per A2 node-pair test (the counted predicate of §2.6), once per A3 hull-pair
    comparison and once per recomputed hull. The verification-work budget is checked before every counted sub-step.
  - `indexSubdivisions` increments once per **over-cap trigger cell that was split** — a `(cell, level)` whose
    inserted-item count exceeded `CONFLICT_CELL_CAP` and was therefore replaced by its children; a cell at or below the
    cap that is merely superseded by the level change does not increment the counter.
  - All counters are **per-frame totals**: they accumulate over the initial detection, every verification detection,
    the final detection and the §5.4 rebuild and are never reset between passes.
- **Stop semantics.** An index-item, pair-check or detected-key stop on the **initial (published-position) detection**
  truncates that detection: the ascending distinct keys accumulated so far are published as the detected and residual
  sets, the ladder does not start and `detectionTruncated = true`. A detection-side stop inside a **verification
  detection** rejects that attempt (a truncated `C_out` is never accepted) and stops the ladder as a
  verification-phase stop with `verificationBudgetExhausted = true` and `detectionTruncated` **false**: the last
  complete published-position detection describes the unchanged positions, so the published residual is exact. A
  verification-work stop rejects the in-flight attempt and stops the ladder the same way; detection stays complete.
  `indexBudgetExhausted` keeps the different meaning above (direct full enumeration, all pairs examined, no drop).
  The both-flags rule applies at a single stop point: all counters are evaluated there and every flag whose counter
  is at or beyond its cap is set, respecting the phase: a published-position detection stop (initial,
  post-repair or terminal) sets `detectionTruncated`; a verification-detection stop keeps it false.
- Per frame, detection runs once when no conflicts exist and otherwise at most `1 initial + acceptedRounds + rejectedAttempts
  + 1 conditional final detection (only when at least one attempt was accepted) + 1 rebuild` times, bounded by
  `MAX_VERIFICATIONS_PER_FRAME` and the work budgets. The final publication detection and every counted
  rebuild sub-step check the remaining frame allowance before executing. No full hull computation is
  performed by the index.

## 5. Relocation Stage `EdgeConflictCorrection` (design §5)

### 5.1 Inputs, data flow, terminal-geometry reuse

`apply(projection, positions, metrics, pins, visibleEndpoints, boundaryResult)` is a pure function of
`(projection, positions, metrics, pins, visibleEndpoints, boundaryResult.geometry(), boundaryResult.diagnostics())`; it
never touches force state and never re-enters the simulation. `positions` must be `boundaryResult.positions()` (the
published frame uses the stage output, so the argument is explicit for testability).

- The stage starts from `boundaryResult.geometry()` and updates only affected hulls via
  `GraphGeometryEngine.recomputeHulls(projection, candidatePositions, metrics, currentGeometry,
  BoundaryInvariants.changedHulls(nodeHullsByKey, currentPositions, candidatePositions))`; it never calls `computeHulls`.
- `visibleEndpoints` is supplied by `LayoutWorker.accept` once per frame.
- When no attempt is accepted, the result returns `boundaryResult.diagnostics()` unchanged (N3 path still supplies it)
  and no rebuild is performed.

### 5.2 Repair candidates and deterministic order

A repair moves **node centres only**; attachments are re-derived afterwards by the router/painter, not here. Enclosure
anchors (`LayoutPositions.anchors()`) are never modified (N8): moving a descendant changes the recomputed hull geometry
only, and A3 re-checks containment with the unchanged pre-batch anchors. This is deliberately unlike the boundary stage's
subtree averaging (`BoundarySeparationCorrection.java:621-670`).

**All-node C1.** For a C1 conflict whose four endpoints are all projected nodes, order the four endpoint keys
`K0 < K1 < K2 < K3` ascending by `CanonicalKeys.node`. The four centres are in convex position. Compute the centroid `O`
of the four centres; sort the four keys by `atan2(centre.y − O.y, centre.x − O.x)` ascending, breaking exact angle ties by
`CanonicalKeys.node`; rotate the resulting cycle so it starts at `K0`, giving positions `P0..P3`. A proper crossing makes
`(P0,P2)` and `(P1,P3)` the crossing chords and `(P0,P1)`,`(P1,P2)`,`(P2,P3)`,`(P3,P0)` the quad's hull edges.

Candidates, in this fixed order:

1. **Adjacent swap `P0↔P1`**: exchange the two nodes' centre positions (only the two moved nodes change).
2. **Adjacent swap `P2↔P3`**: the other adjacent pair.
3. **Mirror `M(P0)`, `M(P1)`, `M(P2)`, `M(P3)`** in ascending index order. `M(Pi)`:
   - `L` = supporting line of `Pi`'s opposite chord (the crossing chord not incident to `Pi`), i.e. the line through the
     two centres of that chord's endpoints (attachments are irrelevant to the line);
   - `n` = either unit normal of `L`; `s = (centre(Pi) − a) · n` for any `a ∈ L`; reflect `c' = centre(Pi) − 2s·n`;
   - `n_out` = the unit normal of `L` with `n_out · (c' − a) > 0` (the direction of increasing distance from the line):
     `n_out = s < 0 ? n : −n`;
   - final centre `= c' + CROSSING_CLEARANCE · n_out`; the moved set is exactly `{Pi}`.
   - Postcondition: the mirrored centre is at distance `|s| + CROSSING_CLEARANCE ≥ 16.0 > 14.0` from `L`, so the disc rim
     leaves the chord's line with margin `≥ 2.0`.

**Mixed C1** (at least one enclosure endpoint): no candidates; any candidate that would move an enclosure endpoint is
discarded before evaluation. Such conflicts are left to §6 routing and counted as residuals when routing also fails.

**C2.** Candidate: translate the passed-over node `n` along the lexicographically smaller unit normal `n*` of `chord(e)`
(the `(-dy, dx)/len` or its exact negation, whichever `(nx, ny)` sorts first by `Double.compare(nx)` then
`Double.compare(ny)`), never modifying `e`'s endpoints; the computation requires `len > 0` (zero-length chords are handled
below). With `R = radius(n)`, `a` a point on the chord line and `s = (centre(n) − a) · n*`, the candidate sets
`centre(n) = centre(n) + (R + EDGE_NODE_CLEARANCE − s) · n*`; the moved set is exactly `{n}`. Postcondition: the signed
projection on `n*` becomes `R + EDGE_NODE_CLEARANCE`, and because the distance to the closed chord is at least the
distance to the chord's line, the translation **guarantees** the full disc clearance. It is not claimed to be the
minimal clearing translation: the formula targets the chord's supporting line, and when the nearest feature of the closed
chord is an endpoint, a smaller translation along `n*` could already clear the endpoint. The node is moved only if
unpinned; a pinned passed-over node yields no candidate. **Zero-length chord:** if `attachment(first) ==
attachment(second)` then `len == 0`, the normal is undefined, no C2 candidate is generated, and the conflict is left to
§6 routing and reported as a residual if routing also fails.

**Discards before evaluation:** a candidate is discarded iff it moves a pinned node, or a swap's two nodes have different
enclosure membership (the projection's full enclosure path set), or it would move any enclosure endpoint. Discarded
candidates are not counted as verifications.

### 5.3 Batch assembly, A1–A4, rejection-retry, bounds

Candidates are generated from the current conflict list in the order of §5.2 and greedily combined into a batch whose
moved-node sets are pairwise disjoint: scan candidates in order and add each one whose moved-node set does not intersect
the accumulated set; the result is the batch. Verification of an attempt (the batch, or later a single candidate):

- **A1 — pair-set monotonicity.** Let `C_in` be the current distinct conflict-identity set and `C_out` the set after
  applying the attempt and re-running §3 detection on the candidate positions. Accept A1 only if `C_out ⊆ C_in` and
  `|C_out| < |C_in|`. Both C1 (`edgePair`) and C2 (`edgeNodePair`) keys live in the same set; the formats are disjoint
  (N1), so a new crossing and a new edge-over-node both veto, and two C2 conflicts on one edge are distinguished.
- **A2 — disc separation.** Accept only if
  `NodeSeparationProjection.violatingPairsCounted(projection, candidatePositions, remainingWork).pairs() ⊆
  NodeSeparationProjection.violatingPairsCounted(projection, currentPositions, remainingWork').pairs()` compared as key sets; no pair
  may fall below `MIN_GAP` that was not already below it. Disc radii are the same `NODE_RADIUS × prominenceScale` rule
  as the boundary stage. Each call receives the frame's remaining verification-work allowance, decrements it before
  every node-pair test and stops at zero; a `truncated()` result is a `DETECTION_STOP` for the attempt, so `C_out` is
  never accepted partially. Both calls add their `pairEvaluations()` to `verificationWork`, so the O(n²) predicate
  cannot run unbounded.
- **A3 — hull invariants, subset form.** `V_in = boundaryInvariants.detectPairKeys(projection, currentGeometry)` is
  **re-derived from the current positions each round**, never frozen from the boundary stage. `V_out` is computed from
  `candidateGeometry = recomputeHulls(..., changedHulls(nodeHullsByKey, currentPositions, candidatePositions))`. Accept
  only if `V_out ⊆ V_in` compared by pair keys, so pre-existing residuals the boundary stage legally publishes
  (`boundaryCovered()`) never veto a repair, and no new sibling overlap, sibling containment or ancestor escape can pass.
- **A4 — membership (precondition/assertion, not an independent veto).** Every moved node keeps its projection
  enclosure path: the ascending `List<EnclosureHullKey>` of the projection's enclosing hulls is equal before and after,
  and `candidateGeometry.hulls()` contains every key on the path. A4 cannot discriminate: the projection is immutable, so
  the path is unchanged by any candidate, and `recomputeHulls` always emits every projection hull key. A4 is therefore
  evaluated as an assertion that can fail only on an implementation defect; the real protection against an enclosure
  escape is A3's per-round `V_out ⊆ V_in` comparison, ancestor containment included.

**Rejection-retry (pinned).** When the batch is rejected, the stage re-evaluates the rejected batch's candidates
individually in canonical order and accepts the first single candidate that passes A1–A4. Only if none passes does it
discard the whole batch and continue the greedy selection over the remaining unused candidates. Every batch or single
attempt increments `verifications`; the total per frame is bounded by `MAX_VERIFICATIONS_PER_FRAME`. A viable single
repair is never discarded because a sibling in its batch failed. `batch.singlesInCanonicalOrder()` enumerates the
batch's candidates in canonical order; a one-candidate batch needs no separate retry (its batch evaluation **is** that
candidate's individual evaluation), so its retry list is empty.

**Budget accounting (pinned).** The frame runs under four counted budgets whose units, check points and stop
semantics are:

| budget | counted unit | check point | stop |
| --- | --- | --- | --- |
| `EDGE_CONFLICT_MAX_INDEX_ITEMS_PER_FRAME` | one inserted index item (one primitive-cell write) or one direct outermost examination | before every cell write and inside every subdivision re-collect | detection truncation |
| `EDGE_CONFLICT_MAX_PAIR_CHECKS_PER_FRAME` | one `properlyCross` / `segmentDistance` test | before every test, including the outermost direct pass | detection truncation |
| `EDGE_CONFLICT_MAX_DETECTED_PER_FRAME` | one distinct conflict identity key | when a key is added to the accumulating set | detection truncation |
| `EDGE_CONFLICT_MAX_VERIFICATION_WORK_PER_FRAME` | one A2 node-pair test, one A3 hull-pair comparison or one recomputed hull | before every counted verification sub-step | the in-flight attempt is rejected; verification-phase stop |

The counters are **per-frame totals** over the initial detection, every verification detection, A1–A4 (including A2
and every `recomputeHulls`), the final detection and the §5.4 rebuild; a pass never resets them.
`MAX_VERIFICATIONS_PER_FRAME` remains the attempt counter. The flags are distinct:

- a **detection-side stop on the initial detection** sets `detectionTruncated`, publishes the ascending distinct keys
  accumulated up to the counted stop as `conflictsDetected`/`conflictsResidual`, leaves `rounds = 0`,
  `verifications = 0`, `repairsApplied` empty and `crossingsResolved = edgeOverNodeResolved = 0`, and performs no
  relocation and no §5.4 rebuild;
- a **detection-side stop inside a verification detection** (computing `C_out`) rejects that attempt rather than
  accepting a truncated `C_out`; the last complete detection describes the unchanged positions, so the ladder stops with
  `verificationBudgetExhausted` and the published residual is exact;
- a **verification-phase stop** (`MAX_VERIFICATIONS_PER_FRAME` reached or the verification-work budget exhausted)
  rejects the in-flight attempt, sets `verificationBudgetExhausted`, and retains the last complete detection
  on the published positions (including the accepted candidate's complete `C_out` after an earlier repair).
  The flag is set on transition to the attempt cap when conflicts remain. When the accepted cap attempt
  clears every conflict the flag stays `false`;
- `indexBudgetExhausted` is unrelated to stops: it records a direct full-enumeration fallback (subdivision floor/depth
  reached, or the outermost direct pass) with every pair examined and none dropped.

At every stop point all counters are evaluated and every flag whose counter is at or beyond its cap is set (the
both-flags rule), respecting detection-side versus verification-phase flag semantics. The final publication
detection and the §5.4 rebuild consume only the remaining frame allowances. A terminal detection stop publishes
its accumulated lower bound with `detectionTruncated`; a rebuild stop keeps the last complete edge detection
with `verificationBudgetExhausted` and `detectionTruncated == false`.

**Round loop.** Pinned control flow:

```
detectionTruncated = false; verificationStopped = false
round = 0; acceptedAny = false; verificationBudgetExhausted = false
while (round < MAX_RELOCATION_ROUNDS && !detectionTruncated && !verificationStopped
        && verificationCount < MAX_VERIFICATIONS_PER_FRAME) {
    conflicts = detectConflicts(currentPositions, currentGeometry)     // counted; a detection-side
                                                                       // stop sets detectionTruncated
    if (detectionTruncated || conflicts.isEmpty()) break
    pool = generateCandidates(conflicts)                                // canonical order
    acceptedThisRound = false
    while (pool not empty && !verificationStopped
            && verificationCount < MAX_VERIFICATIONS_PER_FRAME) {
        batch = greedyDisjoint(pool); pool -= batch
        if (batch empty) break
        attempts = [batch] + batch.singlesInCanonicalOrder()
        for attempt in attempts {
            verificationCount++                                         // counted before evaluation
            outcome = acceptsA1A2A3A4(attempt)                          // counted; DETECTION_STOP
                                                                        // rejects the attempt
            if (outcome == ACCEPT) {
                apply(attempt); currentPositions = result; currentGeometry = recomputedGeometry
                conflicts = acceptedCompleteC_out
                rounds++; acceptedAny = true; acceptedThisRound = true
                recordRepair(attempt)
                if (verificationCount >= MAX_VERIFICATIONS_PER_FRAME) {
                    verificationStopped = true      // accepted cap attempt; the final detection
                                                    // below sets the flag if conflicts remain
                }
                break
            }
            if (outcome == DETECTION_STOP || verificationWorkAtCap) {
                verificationStopped = true; verificationBudgetExhausted = true  // rejected attempt;
                                                                                // C_out never truncated
                break
            }
        }
        if (acceptedThisRound || verificationStopped) break
    }
    if (!acceptedThisRound) break
}
residual = conflicts
if (rounds > 0 && !detectionTruncated && !verificationStopped) {
    terminal = detectConflicts(currentPositions, currentGeometry, remainingFrameAllowances)
    residual = terminal.accumulatedKeys
    detectionTruncated = terminal.truncated
}
if (verificationStopped && !residual.isEmpty() && !detectionTruncated) {
    verificationBudgetExhausted = true
}
```

Each accepted attempt is one round; A1's strict decrease guarantees termination; `rounds` records accepted attempts and is
also the accepted-attempt index (`repairsApplied` order). The final detection supplies the published-position
residual when it completes. On a verification stop, the retained complete `C_in` (or accepted complete `C_out`)
supplies that exact residual without further detection work. On any published-position detection stop,
`conflictsResidual` and the resolved counts instead come from the accumulated lower bound and
`detectionTruncated` is set. If `rounds` reaches `MAX_RELOCATION_ROUNDS` with conflicts remaining, those are
residuals; an unsatisfiable conflict is never an exception.

### 5.4 Rebuilt boundary record (design §5.5, N3) and rebuild data path

When at least one attempt was accepted (a batch or a retry single; equivalently `rounds > 0`) and
published-position detection is complete, attempt the following rebuild with the remaining verification allowance:

1. `rebuilt = boundaryInvariants.describe(projection, publishedPositions, metrics, pins, publishedGeometry,
   boundaryResult.diagnostics().residualHullPairs(), boundaryResult.diagnostics())`.
   `describe` re-derives detection and terminal classification on the published hulls; the inclusion guard throws
   `EdgeConflictException` if `detectPairKeys` contains a pair not in `baselineResidualKeys` (impossible after A3; defence
   in depth).
2. The node residual is recomputed as
   `new NodeSeparationProjection().project(projection, canonicallySorted(publishedPositions), pinnedNodes).residualViolations()`
   where `canonicallySorted` rebuilds the node map in ascending `CanonicalKeys.node` order (so the diagnostic is
   independent of input map iteration order) and keeps `anchors()` unchanged.
3. Both scans charge their consumed A3/A2 units before the next counted operation; they never use an
   unbounded terminal allowance. Publish `rebuilt` and step 2's count together only if both scans complete.
   On a stop retain this frame's `boundaryResult.diagnostics()` and `nodeResidualViolations()`, set
   `verificationBudgetExhausted`, and keep the complete edge residual with `detectionTruncated == false`.
   The accepted edge displacements remain published. These retained informational boundary fields describe
   the boundary stage before edge repair; they are not a partial rebuild or previous-frame data.
4. The result's `appliedDisplacements()` is the edge stage's own canonical field map (figures in §5.6).
   A detection-truncated frame skips this rebuild and retains the same boundary-stage fields.

When no attempt was accepted, `boundaryDiagnostics()` is `boundaryResult.diagnostics()`,
`nodeResidualViolations()` is `boundaryResult.nodeResidualViolations()`, and no detection/rebuild runs beyond the initial
and final detection passes.

The rebuilt record's field policy is exact:

| field | source |
| --- | --- |
| `rounds`, `hullViolationsDetected`, `displacementRms`, `displacementMax`, `deltaRms`, `deltaMax`, `appliedDisplacements` | copied from `boundaryResult.diagnostics()` unchanged |
| `conflicts` | `BoundaryInvariants.describe` terminal classification on the published hulls |
| `residualHullPairs` | `detectPairKeys(publishedGeometry)` ascending |
| `hullResidualViolations` | `detect(publishedGeometry).size()` |

The publish gate is unchanged and runs at the boundary stage (`BoundarySeparationCorrection.java:169-171`,
`LayoutWorker.java:288-290`) before the edge stage; A3 guarantees no new hull violation can appear afterwards, so there is
no post-relocation re-gating. The rebuilt record is informational for the published frame.

### 5.5 `EdgeConflictCorrection` diagnostics composition

- `crossingsDetected` / `edgeOverNodeDetected`: first detection pass counts.
- `crossingsResolved` / `edgeOverNodeResolved`: subtract the final residual counts.
- `conflictsResidual`: ascending distinct identity keys on the published positions.
- `repairsApplied`: one `Repair` per accepted candidate, in candidate order within each accepted attempt
  (application order across attempts); `kind` from that candidate, `nodeKeys` ascending,
  `fromCentres`/`toCentres` from the pre/post candidate positions, `batchIndex = rounds − 1` (all candidates of one
  accepted attempt share the attempt's index, so a mixed-kind batch reports one `Repair` per kind).
- `rounds`, `verifications`, `verificationWork`, the three conflict-stage flags (`indexBudgetExhausted`,
  `verificationBudgetExhausted`, `detectionTruncated`) and the §4.3 counted work (`indexInsertedItems`,
  `indexPairChecks`, `indexSubdivisions`).
- `EdgeConflictTimings`: `detectionNanos` (all `detectConflicts` passes plus rebuild detection),
  `indexNanos` (index build/query), `verificationNanos` (A1–A4 except hull recompute), `rebuildNanos` (describe + node
  residual). Timings never enter `EdgeConflictDiagnostics`.

### 5.6 `LayoutWorker.accept` composition, merged displacements, failure path

The edge stage is unconditional (design §4.2, §11). `LayoutWorker.accept` becomes exactly:

```java
BoundarySeparationResult boundary = boundaryCorrection.apply(projection, raw.positions(), metrics, pins);
Set<ProjectedEndpointKey> visible =
    ProjectedEndpointVisibility.visibleEndpoints(projection.nodes(), projection.enclosures());
EdgeConflictResult edge = edgeConflictCorrection.apply(projection, boundary.positions(), metrics, pins,
    visible, boundary);
LayoutPositions corrected = edge.positions();
Map<String, LayoutPoint> merged = mergeDisplacements(boundary.appliedDisplacements(),
    edge.appliedDisplacements());
PerceptualIdlePolicy.IdleMeasurement idle = idlePolicy.observe(before, corrected);
double[] deltas = displacementDeltas(previousAppliedDisplacements, merged);
LayoutFrame decorated = LayoutFrame.withDiagnostics(
    LayoutFrame.of(raw.stepIndex(), corrected, false, edge.nodeResidualViolations()),
    edge.nodeResidualViolations(), edge.boundaryDiagnostics().withDeltas(deltas[0], deltas[1]),
    edge.diagnostics(), idle);
previousAppliedDisplacements = merged;
```

`mergeDisplacements` returns a `LinkedHashMap` ascending by field key; duplicate keys sum componentwise
(`LayoutPoint.of(x1 + x2, y1 + y2)`). The merged map is the new `previousAppliedDisplacements` and is the second argument
of `displacementDeltas(previousAppliedDisplacements, merged)`; `idlePolicy.observe(before, corrected)` instead measures the
published `corrected` positions, so a relocation is motion and cannot be published while the frame is labelled idle.

### 5.7 Failure behaviour and state transitions

- Any `RuntimeException` raised inside `EdgeConflictCorrection.apply` (including the `describe` inclusion guard) is
  wrapped in `EdgeConflictException` and propagates, exactly as `BoundarySeparationException` does from the boundary
  stage; `LayoutWorker.runSubmit`/`runStep` catch it, set `failedEngine`, and return `failedFrame(...)`. The canvas
  publishes the previous frame through `LayoutSettleLoop.fail(...)`; the settle loop's failure frame keeps the last valid
  `BoundarySeparationDiagnostics` **and** `EdgeConflictDiagnostics`, mirroring today's boundary retention:
  `LayoutWorker.failedFrame` (`LayoutWorker.java:375`) and `LayoutSettleLoop.failedFrame`
  (`LayoutSettleLoop.java:761`) both build the four-argument `withDiagnostics` from
  `retained.boundaryDiagnostics()` and `retained.edgeConflictDiagnostics()`, and `LayoutFrame.of(...)` initialises both to
  their `empty()` values. `LayoutWorker.EMPTY_FAILED_FRAME` carries `EdgeConflictDiagnostics.empty()`.
- An unsatisfiable conflict is a residual, never an error.
- Publish gate semantics: unchanged boundary gate; `CanvasState` for a failed publish still routes the failed frame's own
  positions through the single factory (design §6.1).

### 5.8 Pins

- The stage uses **active pins only**, filtered exactly as `BoundarySeparationCorrection.pinnedNodes(pins)` filters them
  (`pin.active()` and a present projected node); inactive pins and pins without a projected node are ignored. The pinned
  set is computed once per `apply`.
- Any candidate whose moved-node set intersects the pinned set is discarded before evaluation (§5.2). A conflict whose
  every candidate is discarded is a residual; §6 routing still runs for it (routed if a detour clears, else straight).
- Pinning captures different coordinates depending on the interaction path, and both are intended: the node context menu
  pins the published `geometry.center()` — the post-relocation position, `GraphInteractionController.java:510` — while a
  drag-release pin records the mouse world point `canvas.worldAt(event.getPoint())`
  (`GraphInteractionController.java:474`). The stage never reinterprets a pin's recorded coordinates; it treats every
  active pinned node as immovable.
- Invariant I1 (design §7): *No pinned projected node changes position in the published frame (active pins only, exactly
  as `BoundarySeparationCorrection` filters them).* `EdgeConflictCorrectionShould` pins the pinned-candidate discard, the
  pinned/mixed-conflict residual, and the strict I1 assertion on the published positions.

## 6. Routing Pass `EdgeRouter` (design §6)

### 6.1 Placement and inputs

`route(projection, positions, geometry, visibleEndpoints)` runs on the settle thread inside `LayoutSettleLoop.handleFrame`
after `geometryEngine.computeHulls(...)` (`LayoutSettleLoop.java:547`) and before the single `CanvasState.of(...)`
factory, and on the failure path after `computeHulls(...)` (`LayoutSettleLoop.java:725`). It never runs on the EDT or
inside the geometry cache. Its result is cached in the published `CanvasState` for the frame's lifetime.

### 6.2 Processing order and obstacle model

Edges are processed in ascending `CanonicalKeys.edge(edge.key())` order. For edge `e`, the obstacle set is:

- every other projected edge whose two endpoints are visible, where
  - if already processed, the obstacle is its published `EdgePath`,
  - if not yet processed, the obstacle is its straight chord (the current geometry of §3.1),
- every projected node disc except `e`'s own endpoint discs.

Canonical order decides only which edge pays a detour; it never decides what counts as an obstacle. A straight fallback is
itself an obstacle for later edges. An edge whose endpoints are visible but whose node/hull geometry cannot be resolved is
omitted from `EdgePaths` and is also excluded from every other edge's obstacle set: it contributes neither a published
path nor a straight chord. The painter draws nothing for it, exactly as today's null-geometry skip.

The per-frame router budgets of §13.1 are independent of this model: one **obstacle-check** unit per (candidate rung or
straight chord, obstacle) clearance evaluation, checked before each evaluation; the index build and every path/disc/hull
insertion during selection are charged to the router **index-item** budget; the detected-crossing/residual/hull
sample-pair tests are charged to the router **pair-check** budget. On obstacle-budget exhaustion the remaining
canonical-order edges are straight fallbacks published without further obstacle checks — fallbacks consume no obstacle
budget. On index-item exhaustion the remaining edges are fallbacks and the residual passes use the partial index, with
`routeBudgetExhausted` set; on pair-check exhaustion the running pass stops and publishes lower bounds with router
`detectionTruncated` set (§6.9).

### 6.3 Candidate ladder

For each edge, in order:

1. **Straight chord** — the two attachments of §3.1; the default, accepted whenever it clears §6.4.
2. **Single bow** — quadratic Bézier with the control point at the chord midpoint plus the chord normal times the offset,
   for offsets `+0.15, −0.15, +0.30, −0.30, +0.50, −0.50` times `chordLength` (`chordLength` = anchor-to-anchor distance:
   node centres, or hull anchors).
3. **Two-bend dogleg** — same offsets and sign order, both bends on the same signed side: `bend1` at `(1/3, +s·a)` and
   `bend2` at `(2/3, +s·a)` in chord-normal coordinates, `a` the offset magnitude and `s` the candidate sign. The
   alternating-bend reading is not part of the ladder (probe 12).

A rung is skipped when its analytic ratio exceeds `ROUTE_LENGTH_CAP` (live list §1.4) or when `|offset| > MAX_BEND`.
Acceptance additionally requires `pathLength ≤ ROUTE_LENGTH_CAP × chordLength`, where `pathLength` is the sampled
published path length (`EdgePath.length()`), measured per candidate. If no rung clears, the edge falls back to the
straight chord (`reuse the straight rung's attachments`); fallback is not a candidate and may pass over discs and paths
(I4), reported through `edgesFallback` and `pathCrossingsResidual`.

### 6.4 Clearance rules

For a candidate path `P`:

- **Discs:** every sample point and every sample segment keeps `EDGE_NODE_CLEARANCE` from every projected node disc it is
  not attached to; the rule fails iff a measured distance is `< radius(n) + EDGE_NODE_CLEARANCE − ROUTE_EPSILON`.
  Attached discs are the edge's own node endpoints only; a hull endpoint contributes no disc.
- **Other paths:** `EDGE_CLEARANCE` from every other path (already-published or not-yet-processed chord), with the **fan
  exemption**. For two paths that share an endpoint `X`:
  - each path has its own attachment at `X` (`geometry.edgeAttachment` depends on the other endpoint, so they differ);
  - the exemption region `R` is the closed union of two discs, centered at the two paths' own attachments at `X`, each
    with radius `endpointClearanceRadius(X) + EDGE_CLEARANCE`, where `endpointClearanceRadius` is the node's drawn radius
    for a node endpoint and `FAN_HULL_RADIUS` for an enclosure endpoint;
  - the distance requirement is waived for point pairs both inside `R`. Operatively, `P` is path-distance-clear of `Q`
    iff `minDistance(P ∖ R, Q) ≥ EDGE_CLEARANCE − ROUTE_EPSILON` **and**
    `minDistance(P, Q ∖ R) ≥ EDGE_CLEARANCE − ROUTE_EPSILON` (`P ∖ R` = points strictly outside every region disc; the two
    minima together equal the minimum over all point pairs with at least one point outside `R`).
  - A **proper crossing of the two sample polylines is rejected everywhere**, inside or outside `R`, using the sign-only
    exponent-normalised predicate of §3.2 on every sample segment pair. The fan exemption never waives the crossing ban.
- **Hulls:** not obstacles; `pathHullPenetrations` reports (edge, hull) pairs as defined in §6.9.

### 6.5 Path model, construction order, sampling

`EdgePath` construction (removing the attachment/waypoint circularity):

1. the candidate shape is computed between the endpoint **anchors** (node centres or hull anchors, the coordinates the
   layout publishes);
2. the departure attachment is the rim/hull boundary point facing the first waypoint, the arrival attachment the rim/hull
   point facing the last waypoint (`geometry.edgeAttachment(endpoint, waypoint)`);
3. the published path starts and ends at those attachments.

Resulting shapes:

- **straight:** `[attachmentA, attachmentB]`; `samples()` equals `definingPoints()`.
- **bow:** control computed from anchors; attachments face the control; quadratic from `attachmentA` with the control to
  `attachmentB`; `samples()` is the sampled polyline, computed once at construction.
- **dogleg:** `[attachmentA, bend1, bend2, attachmentB]` with bends at `(1/3, +s·a)` and `(2/3, +s·a)` of the anchor
  chord; `samples()` equals `definingPoints()`; the rounded corner is a `BasicStroke` join property, not geometry.

**Bow sampling (pinned).** Let `d` = perpendicular distance from the control point to the attachment chord. Then
`n = max(1, ceil(sqrt(d / (2 × BOW_SAMPLE_TOLERANCE))))`, segment parameters `t_i = i / n`, and `samples()` has `n + 1`
points `B(t_i)` in ascending `i` (endpoints included, duplicates not removed). The exact same list is used for clearance
checking, hit-testing and painting; this is the fidelity contract whose bound is
`d / (2n²) ≤ BOW_SAMPLE_TOLERANCE = 0.75 ≤ min(3.0, 3.0) / 2`.

### 6.6 Terminal-segment arrowhead rule

The arrival head is oriented along the terminal segment and capped by it:

- `arrowAnchor(atStart)` = the point the head at that end is drawn from: straight → the opposite end; bow → the control
  point; dogleg → the adjacent bend (`bend1` for the start, `bend2` for the end).
- `terminalSegmentLength(atStart) = distance(start/end attachment, arrowAnchor(atStart))`:
  - dogleg: the final leg `bend2 → arrival attachment` (or `departure attachment → bend1`);
  - bow: `control → arrival attachment` (the control gives the curve's end tangent, so the head lies along the curve);
  - straight: the attachment-to-attachment distance (today's value).
- `GraphPainter.availableArrowHeadWorldLength(terminalSegmentLength, arrowAtFirst, arrowAtSecond)` keeps its current
  semantics (halved when both ends carry heads), and
  `effectiveArrowHeadLengthPx(zoom, availableWorldLength) = min(arrowHeadLengthPx(zoom), availableWorldLength × zoom)`
  keeps its floor and growth rules. The painter draws the head from `arrowAnchor(...)` to the attachment using the same
  `paintArrow` math as today.

### 6.7 Multiplicity cue

`paintMultiplicity` is called with `path.arcLengthMidpoint()` instead of the straight midpoint. `arcLengthMidpoint()` walks
`samples()` accumulating segment lengths and returns the point at half `length()`; for a straight path it is the segment
midpoint (today's position). If `length() == 0` (a degenerate path whose samples all coincide), it returns
`samples().get(0)` (equivalently `start()`), so the multiplicity label sits on that point. The `+4/zoom` / `−4/zoom`
label offset is unchanged.

### 6.8 Hit-testing

`GraphHitIndex.EdgeEntry` stores the `EdgePath` for each visible edge instead of two points. `edgeAt(point, tolerance)`
computes, for every stored path, the minimum exact segment distance over all consecutive `samples()` pairs (reusing the
existing `ExactValue` numerator/denominator comparison; a zero-length sample pair uses endpoint distance), keeps the best
`(distance, canonical edge key)` pair with the existing tie rules (smaller distance first, then `ProjectedEdgeKey`
`compareTo`), and returns the key iff the best distance is within tolerance. Straight and dogleg paths therefore test one
and three segments; a bow tests its sample list, the same list that was verified and painted.

### 6.9 Route diagnostics

- `edgesStraight`: accepted straight chords; `edgesBow`: accepted bows; `edgesDogleg`: accepted doglegs;
  `edgesFallback`: straight fallbacks. `edgesStraight + edgesBow + edgesDogleg + edgesFallback` equals the number of
  routed edges (both endpoints visible and resolvable).
- `pathCrossingsDetected`: number of distinct visible edge pairs whose straight chords properly intersect before routing.
- `pathCrossingsResidual`: every proper intersection of the published drawn paths, **no region exclusion**, deduplicated
  as ascending `CanonicalKeys.edgePair` keys. This is the authoritative visible residual (§6.10).
- `pathHullPenetrations`: number of `(edge, hull)` pairs where the published path properly crosses at least one edge of
  the hull's `exactPolygon()` and the hull contains neither endpoint. A hull contains an endpoint iff (a) the endpoint key
  is one of the hull's `endpointKeys`, (b) the endpoint's source node's projection enclosure path contains the hull key,
  or (c) the hull is an ancestor (via `BoundaryInvariants.parents`) of a hull key on that path. Clause (c) keeps an
  ancestor hull of an enclosed endpoint from being counted as penetrated: an edge into an enclosed node must cross its
  ancestor boundaries, and those crossings are not penetrations.
- `routeIndexItems`, `routeObstacleChecks`, `routePairChecks`: the counted work of the three budgets of §6.2/§13.1. The
  index-item counter increments once per primitive-cell insertion (path sample segment, disc box, hull segment); the
  obstacle counter once per (candidate rung or straight chord, obstacle) clearance evaluation; the pair counter once per
  tested sample-segment pair in the detected-crossing pass, the residual pass or the hull-penetration pass. Each counter
  is checked before its counted operation (insertion or test), so a stop can never overshoot the cap by a whole pass.
- `routeBudgetExhausted`: the router index-item or obstacle budget stopped selection; the remaining canonical-order
  edges are fallbacks. `detectionTruncated`: the router pair-check budget stopped the detected-crossing, residual or
  hull-penetration pass; `pathCrossingsDetected`, `pathCrossingsResidual` and `pathHullPenetrations` are lower bounds
  and routing selection itself is unaffected.

### 6.10 Determinism

Identical `(projection, positions, geometry, visibleEndpoints)` produce identical `EdgePaths` and diagnostics across runs
and across permutations of projection list order: canonical edge order, canonical key tie-breaks, fixed ladder order, and
the fixed sampling formula leave no choice to iteration order.

## 7. Performance (design §9)

### 7.1 New stages and thresholds

`PerformanceMeasurements.Stage` gains two enum constants inserted immediately after `HULL` and before `PLACEMENT`:

```java
EDGE_CONFLICT("edge-conflict"),
EDGE_ROUTING("edge-routing"),
```

The pinned wire list in `PerformanceTripwiresShould.exposeTheCanonicalStageOrderAndThresholds` becomes exactly:

```
"snapshot", "projection", "diff", "mutation", "force", "correction", "separation", "hull",
"edge-conflict", "edge-routing", "placement", "full-worker", "edt-swap", "repaint",
"accepted-batch-first-frame"
```

Specification-pinned strict thresholds for the reference `reference-2000-5000` fixture (`strictThresholdNanos`), all other
scenarios `DIAGNOSTIC_ONLY_THRESHOLD`; `normalThresholdNanos` stays `DIAGNOSTIC_ONLY_THRESHOLD` for both new stages:

| stage | strict threshold | hard constraint satisfied |
| --- | --- | --- |
| `edge-conflict` | `25_000_000L` (25 ms) | boundary stage + this stage leave at least 65 ms of the 100 ms `STRICT_FULL_WORKER_NANOS` budget for force/correction/hull and harness overhead on the reference fixture. |
| `edge-routing` | `10_000_000L` (10 ms) | the publish-time pass stays well inside the remaining `STRICT_FIRST_FRAME_P95_NANOS = 150 ms` / `P99 = 300 ms` first-frame gates while the EDT-swap stage is untouched at 2 ms. |

The enum constants, their order and the threshold values are pinned and machine-checked by
`PerformanceTripwiresShould.exposeTheCanonicalStageOrderAndThresholds`; the wall-clock outcome is measured by the tripwire
harness on the reference 2000/5000 fixture. A first measurement above a pinned strict threshold is a hard failure that
forces the design revision stated above (no feature flag); the threshold must not be silently raised, and the only
permitted constant change is a tightening applied to the specification and to the tripwire test in the same change.

The two new stage samples are recorded on the **settled worker frame the application publishes** (§13.4): the diagnostic
re-runs each stage on the worker frame's own positions and times that call, and the direct-probe seed-frame recordings
are removed. A7 is satisfied only when every measured sample has zero budget-exhaustion events for the two stages
(`detectionTruncated`, `verificationBudgetExhausted`, `indexBudgetExhausted`, `routeBudgetExhausted` all `false`);
otherwise the sample is a budget-stop time and the gate would be vacuous. The measured replacement guarantee
and the frontier calibration record are in §13.1: strict gates apply to A7a's calibrated settled fixture;
Revision 7 caps bound counted work on larger frames, whose truncation is designed degraded behaviour.

### 7.2 Budget composition and calibration

Per frame the relocation stage performs at most `1 + MAX_RELOCATION_ROUNDS + MAX_VERIFICATIONS_PER_FRAME + 1` detection
passes and one `BoundaryInvariants.describe` rebuild, reusing `recomputeHulls(..., changedHulls(...))`; the only full
`computeHulls` calls per frame remain the boundary stage's own and the publish-time one in `LayoutSettleLoop`. The
`edge-conflict` stage tier covers detection, the conflict index, A1–A4 verification (including the counted A2 predicate
and every hull recompute) and the §5.5 rebuild. The router tier covers the query-index build, the candidate ladder, the
detected-crossing pass and the residual/hull passes; the router's per-frame result is cached on the published
`CanvasState`.

The budgets of §13.1 are **per-frame totals over every counted event** of the tier: the initial detection, every
verification detection, A1–A4, the final detection and the rebuild for `edge-conflict`; the index build, every obstacle
evaluation and the detected/residual/hull passes for `edge-routing`. A verification-phase stop rejects the in-flight
attempt and sets `verificationBudgetExhausted` with detection complete; a detection-side stop truncates detection and
sets `detectionTruncated` (lower-bound residual). The calibration obligation is to record measured per-unit
costs, fixed overhead and worst-in-budget stage times on the largest complete frame in the declared deterministic
calibration sweep, including a near-cap accepted-repair case for verification and the terminal rebuild. The
replacement guarantee is measured A7a strict gates with zero exhaustion on the calibrated settled fixture, plus
A7b counted-work caps, declared flags and the pinned complete-frame time bound. The caps do not imply the
former half-threshold wall-clock guarantee; §13.1 records the measured limitation. No threshold or Revision 7
cap is raised.

## 8. Test Fixtures and Exact Expected Values

### 8.1 The reported fixture (design §1.1)

Headless fixture built with `ProjectionScenario`-style helpers; node centres are the measured screenshot coordinates in
world units (the screenshot was rendered at zoom 1.32 with drawn radius 18.48 px; using the measured pixel coordinates as
world coordinates does not change the topology and keeps every expected value exact):

| role | id | centre (`LayoutPoint`) | drawn radius | edges |
| --- | --- | --- | --- | --- |
| top (K0) | `ID_A` | `(272.0, 170.0)` | 14.0 | `ID_A–ID_C` |
| right (K1) | `ID_B` | `(307.0, 202.0)` | 14.0 | `ID_B–ID_D` |
| bottom (K2) | `ID_C` | `(275.0, 240.0)` | 14.0 | |
| left (K3) | `ID_D` | `(240.0, 208.0)` | 14.0 | |

All four nodes unpinned, no enclosures, all four endpoints visible. Canonical order `ID_A < ID_B < ID_C < ID_D`; cyclic
order from `K0` is `[top, right, bottom, left]`, so the crossing diagonals are `(P0,P2)` and `(P1,P3)`.

Exact detected state:

- `crossingsDetected = 1` (the pair `ID_A–ID_C` × `ID_B–ID_D`, key `CanonicalKeys.edgePair(...)`);
- `edgeOverNodeDetected = 0` (each chord is ≥ `19.597` from every unrelated disc centre, so no C2);
- straight-chord intersection at `(273.5, 205.0)`, the exact midpoint of both chords.

Exact repair trace: candidates are `swap01 (A,B)`, `swap23 (C,D)`, `M(A)`, `M(B)`, `M(C)`, `M(D)`. Greedy batch =
`{swap01, swap23}` (disjoint moved sets). Applying both swaps leaves `ID_A–ID_C` and `ID_B–ID_D` as the diagonals again,
so `C_out = C_in` and A1's strict decrease fails — the batch is rejected. Retry singles in canonical order: `swap01`
alone gives `C_out = ∅`, A2–A4 hold, so it is accepted. Therefore:

- `rounds = 1`, `verifications = 2` (one rejected batch + one accepted single);
- `repairsApplied = [SWAP {ID_A, ID_B}, batchIndex 0]` with from/to centres `ID_A: (272,170)→(307,202)` and
  `ID_B: (307,202)→(272,170)`; `ID_C`, `ID_D` unchanged;
- `crossingsResolved = 1`, `edgeOverNodeResolved = 0`, `conflictsResidual = []`;
- every painted path is straight and the published `pathCrossingsResidual` is empty.

### 8.2 Later-chord bow (attempt-1 B1 counterexample, probe 6)

Nodes (radius 14, no enclosures, unpinned, visible): `ID_1 = (−90, 0)`, `ID_2 = (90, 0)`, `ID_3 = (0, −25)`,
`ID_4 = (0, 25)`; edges `ID_1–ID_2` (processed first, key order `ID_1 < ID_2 < ID_3 < ID_4`) and `ID_3–ID_4`.

Expected router result:

- `ID_1–ID_2`: the straight chord properly crosses `ID_3–ID_4`'s chord at `(0, 0)` and is rejected; bows `±0.15` and
  `±0.30` fail disc clearance against `ID_4`/`ID_3` (the published bows use their attachments, so their quadratic
  midpoints are `(0, 15.511)` and `(0, 30.601)`, which lie `9.49` and `5.60` from `ID_4`'s centre, below the required
  `14.0 + 3.0 = 17.0`); bow `+0.50` (amplitude `90 ≤ MAX_BEND`) is accepted: control `(0, 90)`, attachment chord
  `y = 9.899495`, `d = 90 − 9.899495 = 80.100505`, sample count `n = ceil(sqrt(80.100505 / 1.5)) = ceil(7.3070) = 8`
  → 9 samples, first/last attachments `(∓80.100505, 9.899495)`, midpoint sample `(0, 49.949747)`;
  `length ≈ 183.58 ≤ 1.25 × 180 = 225`;
- `ID_3–ID_4`: `STRAIGHT` (the bow's midpoint sample `(0, 49.949747)` is `38.95` from the drawn segment's
  endpoint, and the path's minimum distance to the drawn segment `(0, −11)–(0, 11)` is `≈38.65`, far above
  `EDGE_CLEARANCE`; it also clears all discs);
- `edgesBow = 1`, `edgesStraight = 1`, `edgesFallback = 0`, `pathCrossingsDetected = 1`,
  `pathCrossingsResidual = []`.

With probe 6 applied (not-yet-processed chords dropped from the obstacle set) `ID_1–ID_2` stays straight and the residual
becomes the crossing pair, so the fixture falsifies the mutation.

### 8.3 Fan-crossing negative fixture (probe N7 / §6.4 "never accepted anywhere")

Nodes (radius 12 except the obstacle): `X = (0, 0)`, `A = (12.997, 48.089)`, `O = (4.808, 23.714)` (radius 14, unrelated
disc), `B = (38.39, 23.412)`; edges `X–A` (first) and `X–B`; `EDGE_CLEARANCE` fan region radius `rX + 3 = 15`.

Expected: `X–A` cannot accept any non-straight rung. Its widest candidate bow `0.50` on the negative side would properly
cross `X–B`'s not-yet-processed chord inside the fan region (crossing point near `(12.55, 7.65)`, within 15 of both
attachments at `X`), so the crossing ban rejects it; all remaining rungs fail clearance. `X–A` therefore falls back to its
straight chord while `X–B` stays straight:

- `edgesFallback = 1`, `edgesStraight = 1`, `pathCrossingsResidual = []`, `pathHullPenetrations = 0`.

Under a mutation that waives the crossing ban inside `R`, `X–A` accepts the bow and `X–B` accepts the straight, and
`pathCrossingsResidual` contains the fan pair; the test asserts the pinned correct state, so the mutation fails.

### 8.4 A3 hull counterexample (reproducible, sibling overlap)

The earlier parent/child escape fixture is not reproducible: `recomputeHulls` recomputes every ancestor of a changed
hull, and a parent hull always contains its direct child hulls, so a candidate can never produce `ANCESTOR_ESCAPE`. The
replacement is a sibling-overlap fixture in the style of
`BoundarySeparationCorrectionShould.ancestorEscapePairKeysReportEscapingChildren`, with one edge so A1 has a non-empty
conflict set to act on.

Projection: root enclosure `R`, sibling child enclosures `A` and `C` under `R`, nodes `a ∈ A`, `c ∈ C`, `p = (0, 100)`,
`q = (0, −100)` (all four projected, all unpinned, all endpoints visible), and edge `e = p–q`. Anchors may be zero; only
the non-empty sibling hulls matter. Initial positions: `a = (10, 0)`, `c = (−58, 0)`, `p = (0, 100)`, `q = (0, −100)`;
`r` is the engine's drawn radius for the fixture (`BASE_RADIUS × prominenceScale`), and every verdict below holds for
every `r ∈ [8.0, 14.0]`.

- Initial detection: the closed chord of `e` is the segment `(0, 100−r)–(0, −(100−r))`, so `a` at distance
  `10 < r + EDGE_NODE_CLEARANCE` is a C2 conflict and `C_in = {CanonicalKeys.edgeNodePair(e.key(), a)}`;
  `crossingsDetected = 0`, `edgeOverNodeDetected = 1`. The sibling hulls are `68 ≥ 2(r + HULL_CLEARANCE)` apart, so
  `V_in = boundaryInvariants.detectPairKeys(...) = ∅`.
- Candidate: `n* = (−1, 0)`, `s = (a − chordPoint)·n* = −10`, so the C2 translation is `(r + 3 + 10)·n* = (−(r+13), 0)`
  and `a` moves to `(−(r+3), 0)`.
- A1 would pass: `a`'s distance to the chord line becomes exactly `r + EDGE_NODE_CLEARANCE` (the C2 predicate no longer
  fires) and no other chord exists, so `C_out = ∅ ⊂ C_in` with strict decrease. A2 would pass: `a_new` is `55 − r ≥
  2r + MIN_GAP` from `c` for every `r ∈ [8.0, 14.0]`, and all other disc distances only increase.
- A3 vetoes: with `candidateGeometry = recomputeHulls(..., changedHulls(nodeHullsByKey, current, candidate))`, hull `A`
  is recomputed at `−(r+3)` while unrecomputed sibling hull `C` stays at `−58`, so `distance(A, C) = 55 − r <
  2(r + HULL_CLEARANCE)` for every `r ≥ 8`: `detectPairKeys(candidateGeometry) = {CanonicalKeys.hullPair(A, C)}` with
  kind `SIBLING_CROSSING` and a nonzero separating translation. `V_out ⊄ V_in`, so the candidate is rejected and the
  published positions stay at the initial values.

Expected stage outcome: `rounds = 0`, `verifications = 1` (the singleton batch is verified once and §5.3 gives it no
separate retry), `conflictsResidual = [CanonicalKeys.edgeNodePair(e.key(), a)]`, and no repair is recorded.
The companion `BoundaryInvariantsShould` characterization injects the pair keys directly (the
`ancestorEscapePairKeysReportEscapingChildren` technique): `V_in = {pair(A, C)}` with `V_out = V_in` is not vetoed,
while any `V_out` that adds a pair not in `V_in` is vetoed, because A3 compares pair-key sets rather than violation
counts.

### 8.5 Conflict-index negative fixtures (N7)

- **Extreme spans:** four nodes at `(−1e300, −1e300)`, `(1e300, 1e300)`, `(−1e300, 1e300)`, `(1e300, −1e300)` with the
  crossing diagonals as edges. This expectation is part of the §3.2 `properlyCross` contract: the common-exponent
  normalisation keeps all four orientations finite at `±1e300`, so the diagonals properly cross and
  `crossingsDetected = 1`. Both chords clamp to `OUTERMOST_CELL_INDEX`, are skipped by cell insertion, and are paired by
  the outermost direct pass, so `indexBudgetExhausted = true`. With the outermost-ring fallback removed the same fixture
  misses the pair; the §3.2 normalisation keeps the orientations finite at `±1e300` (an un-normalised product predicate
  would only miss the crossing for coordinates whose own differences overflow, e.g. `±1.5e308`).
- **Subdivision cap:** 65 distinct diameters of the circle of radius `100.5` centred at `(0, 0)`: for `k = 0..64` the
  chord joins `(100.5·cos θ_k, 100.5·sin θ_k)` and `(−100.5·cos θ_k, −100.5·sin θ_k)` with `θ_k = 2πk/65`, giving 130
  distinct endpoint nodes of drawn radius `r = 14.0` (pinned; the frame minimum is `(−117.5, −100.5·cos(π/130) − 17.0)
  ≈ (−117.5, −117.470655)` and `(0, 0)` is strictly interior to its cell at every level for these values). Every chord
  passes through the interior point `(0, 0)`, so every pair properly crosses there. Traversal semantics (pinned for this
  fixture): a chord is inserted into every cell whose closed square the segment intersects, an inflated disc into every
  cell whose closed square it overlaps, and a repeated pair key is counted once. Expected: `indexSubdivisions = 4` (the
  single over-cap origin cell is split at levels 0, 1, 2 and 3), `indexBudgetExhausted = true`,
  `indexPairChecks ≥ 65 × 64 / 2 = 2080`, and `crossingsDetected = 2080`. No pair is dropped. With the traversal pinned
  to this radius, no grid boundary at any level passes through `(0, 0)`, so the origin leaf contains all 65 chords at
  every level.
- **Adaptive-grid value proof:** the 65-chord fixture keeps `baseCellSize = 48.0` (span ≈ 235 < 128 × 48) and, with the
  pinned traversal and recount semantics, totals ≈ 39,440 `indexInsertedItems` across its five collection levels and
  2,080 `indexPairChecks`; the item and pair budgets must admit both so the fixture still detects every pair. The
  reference-scale fixture (500 chords of radius 600,000) stays on the adaptive branch (base cell ≈ 9,375; the level-4
  traversal is ≈ 2,048 cells per chord, below the 4,096 cap), and the per-primitive cap is exercised by the dense
  wide-span fixtures below.
- **Item-budget truncation:** a dense wide-span fixture whose counted `indexInsertedItems` alone reaches the cap before
  enumeration completes asserts `detectionTruncated == true`, `indexBudgetExhausted == false`, the pair and volume
  counters below their caps, `rounds == 0`, `verifications == 0`, and a `crossingsDetected` strictly smaller than a
  brute-force baseline of the same frame; the published residual is exactly the ascending keys accumulated up to the
  item stop.
- **Pair-budget truncation:** the same shape with `indexInsertedItems` below the item cap and `indexPairChecks` — not
  the volume counter — the only counter at its cap.
- **Volume-budget truncation:** a hub/star frame with more than 4,096 detectable keys asserts `detectionTruncated == true`,
  `rounds == 0`, `verifications == 0`, empty `repairsApplied`, and the accumulated ascending set.
- **Verification-work truncation:** a frame whose A2/verification work reaches the verification-work cap asserts the
  in-flight attempt was rejected (`rounds` unchanged at the stop point), `verificationBudgetExhausted == true`,
  `detectionTruncated == false`, and a residual equal to the last complete detection on the published positions.
- **Router truncation:** a fixture whose obstacle demand exceeds `EDGE_ROUTING_MAX_OBSTACLE_CHECKS_PER_FRAME` asserts
  `routeBudgetExhausted == true`, the fallback classification identity and every remaining edge straight; a second
  fixture whose residual pass exceeds `EDGE_ROUTING_MAX_PAIR_CHECKS_PER_FRAME` asserts `detectionTruncated == true` and
  a `pathCrossingsResidual` that is a lower bound of the brute-force residual.

### 8.6 Scaffold-then-red sequence (design §10.1)

1. **Scaffold commit (behaviour-preserving):** introduces the `EdgeConflictCorrection`, `BoundaryInvariants`,
   `CanonicalKeys`, `EdgePath`/`EdgePaths`/`EdgeRouter` seams with today's straight rendering and no relocation; the
   router returns straight `EdgePath`s and `EdgeConflictCorrection` returns the boundary result unchanged; all existing
   tests stay green.
2. **Red phase:** the §8.1–§8.5 tests are added and run against the scaffold. The reported-fixture test
   `EdgeConflictCorrectionShould.swapsTheReportedConvexQuadAndRemovesTheCrossing` and the painted-path test
   `GraphCanvasPaintShould.paintsNoCrossingForTheReportedFixture` must fail on the scaffold (straight renderer; no swap,
   crossing present).
3. **Implementation commit:** turns them green. The scaffold is the only scaffold; after step 3 there is one active
   execution path (no parallel old renderer, design §11).

### 8.7 Named mutation probes (design §10.2, all twenty)

Each probe breaks exactly one mechanism and shows the covering test fails; the named test is the first one that must fail.

| # | mutation | covering test |
| --- | --- | --- |
| 1 | drop the strict set-decrease in A1 (accept equal counts) | `EdgeConflictCorrectionShould.rejectsTheTwoAdjacentSwapBatchAndRetriesSingles` (reported fixture, `verifications = 2`) |
| 2 | drop the A1 pair-set inclusion (allow a new conflict pair) | `EdgeConflictCorrectionShould.rejectsACandidateThatIntroducesANewCrossing` |
| 3 | drop the A2 disc-gap veto | `EdgeConflictCorrectionShould.vetoesACandidateThatWouldBreakMinGap` |
| 4 | drop the A3 hull veto | `EdgeConflictCorrectionShould.vetoesACandidateThatAddsASiblingHullViolation` (§8.4) |
| 5 | drop the fan exemption in §6.4 | `EdgeRouterShould.waivesTheDistanceRequirementInsideTheFanRegion` (min distance 2.786 < 3.0 accepted) |
| 6 | drop not-yet-processed-chord obstacles | `EdgeRouterShould.routesTheFirstEdgeAroundALaterChordsCrossing` (§8.2) |
| 7 | invert the canonical candidate order in §5.2 | `EdgeConflictCorrectionShould.appliesCandidatesInCanonicalOrder` |
| 8 | remove the `ROUTE_LENGTH_CAP` fallback | `EdgeRouterShould.rejectsTheDeadDoglegRungAndFallsBack` (dogleg 0.50 ratio 1.535184) |
| 9 | re-introduce the endpoint-to-endpoint arrowhead cap | `GraphCanvasPaintShould.capsTheArrowheadAtTheTerminalSegmentOfARoutedPath` |
| 10 | replace rejection-retry with whole-batch discard | `EdgeConflictCorrectionShould.retriesASingleCandidateAfterARejectedBatch` (reported fixture) |
| 11 | drop the C2 visibility filter | `EdgeConflictCorrectionShould.ignoresAnInvisibleEdgeForC2` |
| 12 | allow an alternating-bend dogleg | `EdgeRouterShould.rejectsAnAlternatingBendDogleg` (both bends same side only) |
| 13 | drop the §5.5 rebuilt-record field preservation | `LayoutWorkerShould.preservesBoundaryStageFieldsAcrossTheRebuild` |
| 14 | replace terminal-geometry reuse with a fresh `computeHulls` | `EdgeConflictCorrectionShould.reusesTheBoundaryTerminalGeometry` + the `edge-conflict` budget tripwire |
| 15 | remove the outermost-ring fallback | `EdgeConflictCorrectionShould.detectsACrossingAtExtremeSpans` (§8.5) |
| 16 | flip the C2 normal direction | `EdgeConflictCorrectionShould.movesThePassedOverNodeOffTheChord` (fixture with `s > 0`: flipped distance `\|2s − (R + EDGE_NODE_CLEARANCE)\| < R + EDGE_NODE_CLEARANCE`) |
| 17 | move the index-item cap check after a whole cell/collect write (check-after-collect) | `EdgeConflictCorrectionShould.truncatesDetectionAtTheItemBudget` |
| 18 | move the pair-check cap check after the predicate (check-after-predicate) | `EdgeConflictCorrectionShould.truncatesDetectionAtThePairBudget` |
| 19 | leave the router residual pass unbounded (no pair-check budget) | `EdgeRouterShould.truncatesTheResidualPassAndPublishesALowerBound` |
| 20 | truncate `C_out` instead of rejecting the attempt when the verification-work cap is reached | `EdgeConflictCorrectionShould.rejectsTheAttemptWhenVerificationWorkExhausts` |

## 9. Test Coverage Delta

### 9.1 New test classes

| class | package | scope (per design §10.3) |
| --- | --- | --- |
| `EdgeConflictCorrectionShould` | `layout` | reported convex quad (batch rejection, single retry, exact `verifications = 2`), both adjacent swaps evaluated, mirrors individually, mixed/hull-endpoint conflicts produce no candidate and no anchor movement (N8), pinned candidate discard (active pins only, `BoundarySeparationCorrection.pinnedNodes` filtering), disjoint later repairs, one `Repair` per candidate of an accepted mixed-kind batch, C2 normal direction, visibility, A4 membership assertion, disc-gap veto, sibling-hull-overlap veto, pair-set subset semantics against an equal-count replacement pair, determinism across runs and permuted input order, residual reporting, round/verification/work bounds, `visibleEndpoints` participation, extreme spans and the 65-chord subdivision cap, the item/pair/volume/verification truncation fixtures and the counted-work accessors bounded by their pins |
| `BoundaryInvariantsShould` | `layout` | frozen characterization of the extracted `detect` **and** terminal classification against `BoundarySeparationCorrection`'s pre-extraction output (injected-hull-map hull-nesting/escape cases included), the bound-exhausted `ROUND_LIMIT` case (`new BoundarySeparationCorrection(1)`, `BoundarySeparationCorrectionShould.java:1000-1013`), the memo/counter characterization (`ancestorContainmentIsMemoizedAcrossIdenticalFrames` 1231-1244, `ancestorContainmentMemoRechecksAHullWhoseGeometryChanged` 1262-1277, `capSetsAreReusedWithinAFrame` 1281-1299, `candidateSelectionStopsWhenNoSideHasAMovableContributor` 1310-1325), `describe` field policy and `baselineResidualKeys` guard |
| `EdgeRouterShould` | `geometry` | straight when clear, bow for a disc on the chord, dogleg when a bow is blocked, later-chord obstacle, fan exemption region, proper crossing rejected inside/outside the fan region, sampled-polyline tolerance bound, `ROUTE_LENGTH_CAP` fallback and rung exclusion, attachment tangency, terminal-segment arrowhead values, canonical order, determinism and order permutation, obstacle-budget fallback and residual-pass truncation with the counted-work bounds |
| `EdgePathShould` | `geometry` | value equality of the three shapes, sample-count formula, `length()`, `arcLengthMidpoint()`, `arrowAnchor`/`terminalSegmentLength` for all three shapes |
| `CanonicalKeysShould` | `projection` | the encoding of §2.1 including `edgePair`, `edgeNodePair` uniqueness, hull keys, `mapOfField`, and `~`-containing node ids staying injective without `~` escaping (replaces `layout/CanonicalLayoutKeysShould`, which is deleted) |

### 9.2 Changed existing tests

- **Arrowhead-cap tests** (`GraphCanvasPaintShould.java:1019-1039`): `arrowHeadRuleCapAndBidirectionalSplitAreSelectedInTheHelpers`
  stays, and new assertions pin the terminal-segment source for bow (control → attachment), dogleg (`bend2 → attachment`)
  and straight, plus the both-heads halving on a routed path.
- **Painter tests** (`GraphCanvasPaintShould`, production `GraphPainter.paintEdges` call site): a routed edge is drawn from
  the published `EdgePath.samples()` list (the same list that was verified and hit-tested); multiplicity is drawn at
  `arcLengthMidpoint()` for a bow; selection, hover and dimming are unchanged.
- **CanvasState fixtures:** every direct `CanvasState.of(...)` call already listed in §2.5 (including the seven baseline
  sites) plus
  `AccessibleGraphCanvasShould.java:660,667,791`, `GraphInteractionControllerShould.java:341,617,761,831,889,1030`,
  `GraphCanvasPaintShould.java:121,159,180,237,440,458,727,734,814,1149,1233,1300,1334,1349,1363,1613,1642,1677,1764`,
  `GraphSearchModelShould.java:185`, `WorkspaceDialogsShould.java:438,481,510`,
  `GraphWorkspaceWindowModelShould.java:2618,2910,2928,2938,2953,2982,2995`, and
  `performance/GraphWorkspacePerformanceDiagnostic.java:843` gain the `EdgePaths` argument. Fixtures with drawn edges call
  `new EdgeRouter().route(...)` so the painted geometry is the routed geometry; fixtures without edges pass
  `EdgePaths.empty()`.
- **BoundarySeparationResult fixtures** (`BoundarySeparationResultShould.java:25,36,43,50,57`): the five constructor calls
  gain the terminal `GraphGeometry` argument required by the new `geometry()` member.
- **LayoutFrame call sites (N6):** `LayoutWorker.EMPTY_FAILED_FRAME`/`accept`/`failedFrame`,
  `LayoutSettleLoop.failedFrame`, `LayoutSettleLoopShould.java:1203,1410`,
  `GraphUpdateCoordinatorShould.java:1194,1604,1646` move to the four-argument form.
- **Hit-test tests** (`GraphInteractionControllerShould.java:66-152,345-355`, `GraphCanvasPaintShould.java:228,248`) are
  extended with nearest-point assertions on routed bows and doglegs; straight behaviour is unchanged.
- **Composition tests** (`LayoutWorkerShould`, `BoundarySeparationShould`): boundary diagnostics on the published frame
  are rebuilt for the published positions with stage-level fields preserved; node-key canonicalisation makes the rebuilt
  node residual order-independent; edge diagnostics appear on the frame and on failure frames; a failing relocation
  produces a failed frame, never a broken publish; idle/delta accounting includes the merged relocation displacements.
- **Performance tests:** `performance/PerformanceMeasurements.java` (two stages, threshold methods) and
  `performance/PerformanceTripwiresShould.java` (pinned wire list, 25 ms / 10 ms strict thresholds, reference fixture
  inside `STRICT_FULL_WORKER_NANOS` and first-frame p95/p99). The hub/star fixture and the geometry-suite extreme
  coordinates of §8.5 are exercised by `PerformanceTripwiresShould` as design §10.3 requires, alongside the
  `EdgeConflictCorrectionShould` assertions; both suites assert the same grid-encoding and subdivision-cap fixtures.
  `GraphWorkspacePerformanceDiagnostic` records the two new stages and routes its CanvasState.
- **Canonical-key references:**
  `BoundarySeparationCorrectionShould.java:262,562-566,674-723,751,753,772,774,860-889,945-947,1072-1076,1108-1110`,
  `BoundaryConflictShould.java:36`, `BoundarySeparationShould.java:650-652`, `BoundaryInvariantAssertions.java:53,69`,
  `BoundaryConflict.java:39-40,88`,
  `BoundarySeparationCorrection.java:203,226,252,273,294,313,321,527,533,575,631,640,931,1101` and
  `BoundarySeparationDiagnostics.java:139` delegate to `CanonicalKeys`; `layout/CanonicalLayoutKeysShould.java` is deleted
  and its expectations move to `CanonicalKeysShould`.

## 10. Acceptance Criteria

| # | criterion | named tests |
| --- | --- | --- |
| A1 | The reported convex-quad crossing is removed by the accepted adjacent swap; the crossing pair batch is rejected and the single retry is applied (`rounds=1`, `verifications=2`) | `EdgeConflictCorrectionShould.swapsTheReportedConvexQuadAndRemovesTheCrossing`; `GraphCanvasPaintShould.paintsNoCrossingForTheReportedFixture` |
| A2 | A later edge's straight chord is an obstacle: the first edge bows around it and the residual is empty | `EdgeRouterShould.routesTheFirstEdgeAroundALaterChordsCrossing` |
| A3 | The fan region waives only the distance requirement; a proper crossing is never accepted, inside or outside the region | `EdgeRouterShould.waivesTheDistanceRequirementInsideTheFanRegion`; `EdgeRouterShould.rejectsAProperCrossingInsideTheFanRegion` |
| A4 | A3 uses pair-key subset semantics with a per-round baseline; a legal pre-existing residual never vetoes | `EdgeConflictCorrectionShould.keepsAPreExistingHullResidualWhenTheCandidateDoesNotAddOne` |
| A5 | The §5.5 rebuild preserves boundary stage-level fields and recomputes residuals on the published positions, order-independently | `LayoutWorkerShould.preservesBoundaryStageFieldsAcrossTheRebuild`; `BoundaryInvariantsShould.describePreservesStageFieldsAndRecomputesResiduals` |
| A6 | On a non-truncated frame the conflict index detects every pair (extreme spans, subdivision at the cap, direct outermost pass) and reports counted work bounded by its pins; an index-item, pair-check or volume stop truncates detection, publishes a lower-bound residual and sets `detectionTruncated` | `EdgeConflictCorrectionShould.detectsACrossingAtExtremeSpans`; `EdgeConflictCorrectionShould.enumeratesEveryPairWhenSubdivisionExhausts`; `EdgeConflictCorrectionShould.truncatesDetectionAtTheItemBudget`; `EdgeConflictCorrectionShould.truncatesDetectionAtThePairBudget` |
| A7 | The two stages have the pinned order and 25 ms / 10 ms strict thresholds; A7a's calibrated settled fixture meets the strict worker/first-frame gates with zero exhaustion; A7b's raw frames obey the counted-work caps, declared flags and complete submit/geometry/routing bound (§13.1, §14) | `PerformanceTripwiresShould.exposeTheCanonicalStageOrderAndThresholds`; `PerformanceTripwiresShould.referenceFixtureStaysInsideTheStrictWorkerAndFirstFrameGates`; `PerformanceTripwiresShould.referenceFixtureStaysBoundedAndFlagVisible`; `PerformanceTripwiresShould.rejectsAFrameWhoseRoutingExceedsTheCompleteFrameBound` |
| A8 | Every §8.7 mutation probe (all twenty, including the check-after-collect, check-after-predicate and unbounded-residual-pass probes) is falsified by its named test | probe table §8.7 |
| A9 | Determinism: identical inputs produce identical positions, paths and diagnostics across runs and permuted input order | `EdgeConflictCorrectionShould.isDeterministicAcrossRunsAndPermutedInputs`; `EdgeRouterShould.isDeterministicAcrossRunsAndPermutedEdgeOrder` |
| A10 | Core gate: no file under `freeplane/` or `freeplane_api/` is modified; `gradle :freeplane_plugin_graph:test` passes with the Java 21 Zulu toolchain | `git diff --stat`; module suite |

## 11. Design Review Mandates N1–N8

| mandate | placement |
| --- | --- |
| N1 — C2 identity key | §2.1 `CanonicalKeys.edgeNodePair(edge, node)`; §3.3, §5.3 A1; §8.7 probe 16/2 |
| N2 — `BoundaryInvariants.describe` `baselineResidualKeys` | §2.2 (role, format, guard, seventh `stageBaseline` parameter); §5.4 |
| N3 — accessor carrying the rebuilt diagnostics | §2.3 `EdgeConflictResult.boundaryDiagnostics()`; §5.4 field policy; §5.6 `LayoutWorker.accept` |
| N4 — exhaustion flag names | `indexBudgetExhausted` (direct full enumeration, no pair dropped), `verificationBudgetExhausted` (verification-phase stop, detection complete), `detectionTruncated` (detection-side truncation, lower-bound residual) and `routeBudgetExhausted` (router obstacle/index budget, remaining edges fallback) in §2.3, §2.4, §4.3, §5.3, §5.5, §6.2, §6.9, §7; no other names appear |
| N5 — remaining `CanvasState` construction sites | §2.5 (`GraphUpdateCoordinator.java:136,561`, `CanvasState.withStatus`, all `LayoutSettleLoop` sites, all test fixtures in §9.2) |
| N6 — three-argument `withDiagnostics` call sites | §2.5 (removed form; `LayoutWorker.EMPTY_FAILED_FRAME`, `LayoutSettleLoopShould.java:1203,1410`, `GraphUpdateCoordinatorShould.java:1194,1604,1646`) |
| N7 — negative tests for the subdivision cap/encoding and the "proper crossing is never accepted anywhere" rule | §8.5, §8.3; `EdgeConflictCorrectionShould`, `EdgeRouterShould` |
| N8 — enclosure anchors stay at pre-batch values | §5.2 (anchors never modified, unlike `BoundarySeparationCorrection` subtree averaging); tested in `EdgeConflictCorrectionShould` and `BoundaryInvariantsShould` |

## 12. Traceability

| design section | spec sections | named tests |
| --- | --- | --- |
| §4.2 relocation stage placement and merged displacements | §2.3, §5.1, §5.6 | `LayoutWorkerShould.decorateConsecutiveFramesWithDisplacementDeltas` (extended) |
| §4.3 publish-time router and `CanvasState` field | §2.4, §2.5, §6.1 | `GraphCanvasPaintShould` routed-path tests |
| §5.1–§5.2 detection and candidates | §3, §5.2 | `EdgeConflictCorrectionShould` |
| §5.3–§5.4 acceptance and determinism | §5.3 | `EdgeConflictCorrectionShould` |
| §5.5 rebuilt record | §2.2, §5.4 | `BoundaryInvariantsShould`, `LayoutWorkerShould` |
| §5.6–§5.7 diagnostics and failure | §2.3, §2.5, §5.5, §5.6, §5.7 | `LayoutWorkerShould`, `BoundarySeparationShould` (extended) |
| §6.1–§6.3 processing, ladder, caps | §6.1–§6.3 | `EdgeRouterShould` |
| §6.4 clearance and fan exemption | §6.4 | `EdgeRouterShould`, `EdgeConflictCorrectionShould` |
| §6.5–§6.8 paths, arrowheads, cue, hit-test | §2.4, §6.5–§6.8 | `EdgePathShould`, `GraphCanvasPaintShould`, `GraphInteractionControllerShould` |
| §6.9–§6.10 diagnostics and authoritative residual | §6.9 | `EdgeRouterShould`, `GraphCanvasPaintShould` |
| §9 performance | §7 | `PerformanceTripwiresShould` |
| §10 test strategy | §8, §9 | all named classes |
| §13 constants | §1.3–§1.5 | `EdgePathShould`, `EdgeRouterShould`, `EdgeConflictCorrectionShould` |
| §14.2 bounded relocation budgets | §4.3, §5.3, §7.2, §13 | `EdgeConflictCorrectionShould` bounded-work fixtures |
| §14.3 bounded router budgets | §6.2, §6.9, §7.2, §13 | `EdgeRouterShould` truncation fixtures |
| §14.4 settled-frame measurement window | §7.1, §13.4 | `PerformanceTripwiresShould` |
| §14.5 bounded-policy fixtures | §8.5, §8.7, §10 | the named truncated fixtures |

## 13. Revision 7 — bounded-effort pins, calibration and the settled-frame measurement window

Added by the PM after the Step 8 design pivot (2026-10-13, user decision A+B).
**Full footprint:** this revision changes §1.3, §2.3–§2.4, §2.6, §4.2–§4.3, §5.3–§5.5, §6.2, §6.9,
§7.1–§7.2, §8.5, §8.7, §9.1, §10 (A6–A8), §11 (N4), §12 and this appendix. No other section changes,
and the §7.1 thresholds are unchanged.

### 13.1 Pinned budgets, units and check points

| pin | value | counted unit | check point | stop |
| --- | --- | --- | --- | --- |
| `EDGE_CONFLICT_MAX_INDEX_ITEMS_PER_FRAME` | `262_144` | one inserted index item (one primitive-cell write) or one direct outermost examination | before every cell write and inside every subdivision re-collect | `detectionTruncated`; ladder does not start; lower-bound residual |
| `EDGE_CONFLICT_MAX_PAIR_CHECKS_PER_FRAME` | `131_072` | one `properlyCross` / `segmentDistance` test | before every test, including the outermost direct pass | `detectionTruncated`; lower-bound residual |
| `EDGE_CONFLICT_MAX_DETECTED_PER_FRAME` | `4_096` | one distinct conflict identity key | when a key is added to the accumulating set | `detectionTruncated`; lower-bound residual |
| `EDGE_CONFLICT_MAX_VERIFICATION_WORK_PER_FRAME` | `65_536` | one verification-phase predicate evaluation: one A2 node-pair test, one A3 hull-pair comparison or one recomputed hull | before every counted verification sub-step | attempt rejected; `verificationBudgetExhausted`; detection complete |
| `MAX_VERIFICATIONS_PER_FRAME` | `64` (§1.3) | one batch or single A1–A4 evaluation | before each attempt | `verificationBudgetExhausted`; detection complete |
| `EDGE_ROUTING_MAX_INDEX_ITEMS_PER_FRAME` | `262_144` | one query-index primitive-cell insertion (path sample segment, disc box, hull segment) | before every insertion | remaining edges fallback; `routeBudgetExhausted` |
| `EDGE_ROUTING_MAX_OBSTACLE_CHECKS_PER_FRAME` | `131_072` | one `(candidate rung or straight chord, obstacle)` clearance evaluation | before every evaluation | remaining edges fallback; `routeBudgetExhausted`; fallbacks consume no obstacle budget |
| `EDGE_ROUTING_MAX_PAIR_CHECKS_PER_FRAME` | `131_072` | one sample-segment pair test in the detected-crossing, residual or hull-penetration pass | before every test | running pass stops; lower-bound diagnostics; router `detectionTruncated` |

All counters are **per-frame totals**: the conflict-stage counters accumulate over the initial detection,
every verification detection, A1–A4 (including the counted A2 predicate and every recomputed hull), the
final detection and the §5.4 rebuild; the router counters accumulate over the index build, every obstacle
evaluation and every detected/residual/hull pass. No pass resets a counter. At every stop point all
counters are evaluated and every flag whose counter is at or beyond its cap is set (the both-flags rule);
the flags are the four distinct names of §11 N4. Detection-side stops publish the ascending distinct keys
accumulated up to the counted stop (“detected so far”) — never a set derived from full enumeration — and
set `detectionTruncated`; `indexBudgetExhausted` is never used for a truncation and keeps its §4.3 meaning
(direct full enumeration, every pair examined, no pair dropped).

**Measured replacement guarantee (Step 9 audit calibration, binding).** Revision 7 caps and all §7.1
thresholds stay unchanged. A7a guarantees its strict stage/worker/first-frame gates on the committed,
SHA-256-pinned settled fixture, with a nontrivial crossing, a bow/dogleg and zero exhaustion. A7b guarantees
the counted-work caps, the phase-specific stop/flag rules and a complete submit/geometry/routing duration
of at most `RAW_REFERENCE_MAX_FRAME_NANOS = 3_000_000_000L` per raw reference frame. Caps alone do not
guarantee 12.5 ms / 5 ms stage times for arbitrary larger non-truncating frames. This replaces the former
`cap(B) × c_B + overhead ≤ 0.5 × threshold` saturation obligation, which the measurements below falsify;
no cap or threshold is raised.

Calibration record (Intel Core Ultra 7 155H, Java 21.0.8-zulu, CPU governor `performance`, headless AWT metrics):

- **Declared frontier search.** Take `reference-2000-5000`, reduce it with
  `SettledReferenceFixtureCompiler.reduce`, retain the compiler's 400 relaxation iterations, rescale its
  compiled positions from 4.1 to 0.35, and pin all nodes and anchors as in A7a. Sweep node limits
  300..600 in increments of 25, plus every integer 400..499 (109 distinct frames). This is a calibration
  family, not the committed A7a ledger. The largest nontrivial frame completing every budget in this
  sweep is **474 nodes / 273 enclosures / 909 edges**. Its counts are 19,038 conflict index items,
  18,946 conflict pair tests, **4,035 detected keys**, zero verification work/attempts, 57,395 route index
  items, 17,270 obstacle checks and 84,540 route pair checks; all five exhaustion/truncation flags are
  false and three bows are accepted. At 475 nodes the 4,096-key cap truncates detection; no larger
  sampled frame completes. Simultaneously saturating every counter is not a realizable precondition
  for this family because the first exhausted counter stops the stage.
- **Measured worst-in-budget times.** After 20 warmups, 30 complete samples of that frontier have
  edge-conflict p50/p95/max **69.956581 / 84.428137 / 102.679241 ms**, and edge-routing
  p50/p95/max **181.621258 / 209.709899 / 213.857198 ms**. These samples exceed the withdrawn
  half-threshold allowances even though none is a budget-stop measurement.
- **Verification and terminal work.** For the accepted-repair wide-node family, 209 nodes is the
  largest complete member: `3 × C(209,2) = 65,208` verification units (99.5% of 65,536), two
  attempts and one accepted round. The three scans include the candidate A2 check, A2 recheck and
  terminal node-residual rebuild. After 20 warmups, 30 samples give edge-conflict p50/p95/max
  **10.678875 / 12.329001 / 13.956123 ms**. At 210 nodes the terminal rebuild stops at exactly
  65,536, sets `verificationBudgetExhausted`, retains the complete edge residual and leaves
  `detectionTruncated` false. Counted A3 terminal hull comparisons are independently checked with
  allowances 0, 1, 2 and 3 by `BoundaryInvariantsShould.checksTheRebuildAllowanceBeforeEveryHullComparison`.
- **Fixed overhead.** An empty-input stage harness (30 warmups, 100 measurements) measures median/max
  overhead of **5,441.5 / 34,634 ns** for edge conflict and **4,195 / 26,341 ns** for routing.
  Size-dependent geometry, candidate generation and other uncounted overhead remain in the inclusive
  costs below; empty-input overhead is not claimed to model those costs independently.

Measured inclusive cost per counted unit, computed as `(largest measured stage time − median empty-input
overhead) / unit count` on the corresponding complete frame:

| stage / unit | complete-frame count | inclusive ns / unit |
| --- | ---: | ---: |
| conflict index item | 19,038 | 5,393.098 |
| conflict pair test | 18,946 | 5,419.286 |
| distinct detected key | 4,035 | 25,445.799 |
| verification work unit (209-node accepted-repair family) | 65,208 | 213.941 |
| verification attempt (same accepted-repair family) | 2 | 6,975,340.750 |
| route index item | 57,395 | 3,725.987 |
| route obstacle check | 17,270 | 12,382.919 |
| route pair check | 84,540 | 2,529.607 |

Each ratio includes the other work in that stage; the rows are not independent marginal costs and must
not be summed or linearly extrapolated to an all-caps frame. They record the measured per-unit cost and
its fixed-overhead basis without implying the withdrawn saturation guarantee.

A fresh full diagnostic with the unchanged A7a ledger measured edge-conflict p95 **2.563023 ms**, routing
p95 **8.104872 ms**, full-worker p95/p99 **3.273580 / 4.869970 ms** and first-frame p95/p99
**21.530890 / 22.950769 ms**, with zero exhaustion in 300 measured samples. A7b's eight raw frames
measured a maximum complete submit/geometry/routing duration of **2,820,913,745 ns** (2.821 s), below
the unchanged 3 s pin. Its saturated counts included 262,144 conflict index items, 262,144 route index
items and 131,072 route pair tests, with the declared truncation/exhaustion flags. The A3 routing-delay
regression test proves that routing time participates in that bound.

### 13.2 Budget-exhausted relocation semantics

- **Initial-detection stop.** When an index-item, pair-check or detected-key budget trips during the
  initial detection, `apply` performs no relocation attempts and no §5.4 rebuild for that frame. It
  publishes `conflictsDetected` and `conflictsResidual` as the ascending distinct keys accumulated up to
  the counted stop, `rounds = 0`, `verifications = 0`, empty `repairsApplied`,
  `crossingsResolved = edgeOverNodeResolved = 0`, and `detectionTruncated = true`. The published residual
  is a **lower bound**; the flag is the only signal of incompleteness.
- **Verification stop.** A detection-side stop inside a verification detection rejects that attempt. A
  verification-work stop likewise rejects the in-flight attempt; `C_out` is never truncated and never
  accepted partially. Both stop the ladder with `verificationBudgetExhausted = true` and leave
  `detectionTruncated = false` (the stop is reported as a verification-phase stop); because the last
  complete detection still describes the published positions, the residual is exact. The accepted candidate's
  complete `C_out` becomes that retained detection immediately. A published-position detection stop after
  an accepted repair (post-round or terminal) publishes its accumulated lower bound and sets
  `detectionTruncated`; it does not become a verification stop. The attempt-cap
  branch keeps its §5.3 semantics: the flag is set on the transition to the cap when conflicts remain
  after the rejected cap attempt and after the final detection when an accepted cap attempt still leaves
  conflicts; an accepted cap attempt that clears every conflict leaves the flag `false`.
- `indexBudgetExhausted` stays `false` for every truncated frame unless the index also completed a direct
  full-enumeration fallback at the same stop point; `indexBudgetExhausted` alone never truncates.
- A detection-truncated frame skips the §5.4 rebuild. After an accepted repair, each terminal A3 hull
  comparison and A2 residual test checks the remaining verification allowance and charges its consumed
  work to this frame. If either rebuild scan stops, publish neither partial record: retain this frame's
  boundary-stage diagnostics and node residual, set `verificationBudgetExhausted`, and retain the complete
  edge residual with `detectionTruncated == false`. The retained boundary record describes the boundary
  stage before the accepted edge repair (§5.4); no previous-frame diagnostics are retained.
- Frames inside every budget keep every §5.3 rule, every counter, and the §5.4 rebuild exactly as
  specified.

### 13.3 Budget-exhausted routing semantics

- On index-item or obstacle-budget exhaustion the router stops the corresponding work, publishes every
  remaining canonical-order edge as a straight fallback, keeps the classification identity
  `edgesStraight + edgesBow + edgesDogleg + edgesFallback == routed visible edges`, and sets
  `routeBudgetExhausted = true`. **Fallback edges consume no obstacle budget.**
- On pair-check exhaustion the running detected-crossing, residual or hull-penetration pass stops and
  sets router `detectionTruncated = true`; `pathCrossingsDetected`, `pathCrossingsResidual` and
  `pathHullPenetrations` are lower bounds over the published paths, and routing selection itself is
  unaffected (it is bounded by the obstacle budget). A fallback's crossings are simply residual.
- No partially drawn geometry is published; the router index-item/obstacle stops and the pair-check stop
  may coexist as flags.

### 13.4 Settled-frame measurement window and process deadline

- **Input.** The §7.1 strict thresholds are measured on the **settled worker frame the application
  publishes**, never on `runDirectProbe`'s fresh-engine seed frame. The diagnostic re-runs the stage on
  `workerFrame.positions()` and times that call, on the same deterministic code path:
  - `edge-conflict`: run `BoundarySeparationCorrection.apply` on the worker frame positions and time
    `EdgeConflictCorrection.apply` on the returned boundary result;
  - `edge-routing`: time `EdgeRouter.route(projection, workerFrame.positions(), workerHulls, visible)`;
  - the `EDGE_CONFLICT`/`EDGE_ROUTING` recordings inside `runDirectProbe` are removed.
- **Zero-exhaustion rule.** A7 is satisfied only when every measured sample reports
  `detectionTruncated == false`, `verificationBudgetExhausted == false`, `indexBudgetExhausted == false`
  and `routeBudgetExhausted == false` for the two stages. A sample that hits a budget or falls back to
  direct full enumeration is a budget-stop time; counting it would make the gate vacuous.
- **Window.** `GeneratedWorkspace.Scenario.REFERENCE_2000_5000` pins 400 warmups / 300 measured (700
  samples). `WARMUP_SAMPLES = 20` and `TIMED_SAMPLES = 100` are the placement-trigger sub-measurement
  counts and are unchanged. Convergence means a stable p95 across the measured window plus the
  zero-exhaustion rule; raising the window remains a measurement-window value, never a threshold change.
- **Per-frame total and deadline.** Revision 7 caps bound counted work and do not imply a 12.5 ms /
  5 ms saturated-frame time allowance. A7a measures the strict stage, worker and first-frame gates on
  the calibrated settled fixture with zero exhaustion; A7b measures complete raw-frame duration through
  worker submit, publish-time geometry and routing against `RAW_REFERENCE_MAX_FRAME_NANOS = 3_000_000_000L`.
  The measured frontier costs and the replacement guarantee are recorded in §13.1. The direct probe and
  the settled-frame stage re-runs are wrapped
  in the harness's bounded-operation wrapper (`invokeBounded`) with the per-operation expected time
  pinned in the implementation change; until that wrapper is in place their bounded per-frame work is the
  practical bound. The reference
  window is budgeted at 2 s per sample (700 × 2 s = 23.3 min), the other five scenarios at 250 × 2 s =
  8.3 min and the placement triggers at ≤ 6 min, so the whole process fits the pinned 40-minute
  `PROCESS_DEADLINE_NANOS`; `checkDeadline`'s message must read “Forty-minute diagnostic process deadline
  exceeded” (the current “Ten-minute” text is the inconsistency to fix). If a measured window exceeds the
  deadline, the deadline is raised as a measurement-window value in the same change.

### 13.5 Bounded-policy fixtures (extends §8.5) and the superseded archived test

- All bounded claims are asserted by counted work, never wall clock: `indexInsertedItems`,
  `indexPairChecks`, `verificationWork`, `routeObstacleChecks` and `routePairChecks` are each bounded by
  their pins on a dense wide-span frame, together with the flag and residual assertions.
- Item-budget fixture: `detectionTruncated` caused only by
  `EDGE_CONFLICT_MAX_INDEX_ITEMS_PER_FRAME`, with the detected set incomplete against a brute-force
  baseline and the published set exactly the ascending keys accumulated up to the stop; pair-budget
  fixture: the same with only `EDGE_CONFLICT_MAX_PAIR_CHECKS_PER_FRAME` at its cap; volume fixture: the
  accumulated-set semantics; verification fixture: rejected attempt, `verificationBudgetExhausted`,
  exact residual; router fixtures: obstacle-budget fallback with `routeBudgetExhausted` and residual-pass
  lower bound with router `detectionTruncated`.
- Verification-detection stop: a fixture whose `C_out` detection hits the pair-check or index-item budget
  asserts `verificationBudgetExhausted = true`, `detectionTruncated = false`, and a residual equal to a
  brute-force detection on the unchanged published positions.
- The `65`-chord and `(±1e300)` fixtures keep their pinned values (§8.5), including the adaptive-grid
  value proof; the item and pair budgets admit the 65-chord fixture's ≈ 39,440 item writes and 2,080 pair
  checks.
- The archived `1b5e22c460` test
  `EdgeConflictCorrectionShould.boundsTheConflictIndexForReferenceScaleSpansAndStillDetectsEveryCrossing`
  (500 chords, 124,750 crossings) is **superseded**: below the budgets every crossing is still detected,
  and above them the accumulated truncated set plus `detectionTruncated` is the pinned behaviour. The
  test is reshaped to assert the pinned semantics and the counted-work bounds instead of the
  all-124,750 assertion.

## 14. Revision 8 — A7 measurement input split (design §15)

Added by the PM after the second Step 8 pivot (2026-10-13, user decision "option 1").
Extends §7.1–§7.2, §8 and §10 A7; no threshold and no Revision 7 budget changes.

### 14.1 A7a — settled-position fixture

- New deterministic fixture `settled-reference`: bounded settled positions for a
  generated projection supplied as committed fixture data (recorded-ledger pattern),
  never produced by stepping the divergent engine. Its size is calibrated as the largest
  deterministic layout that stays inside every Revision 7 budget with zero exhaustion
  while carrying a non-trivial workload; a full-scale 2,000/5,000 settled graph is
  explicitly not required for A7a because its conflict volume cannot stay in-budget.
- The §7.1 strict thresholds (`25 ms`, `10 ms`, `100 ms`, `150 ms`, `300 ms`) and the
  zero-budget-exhaustion rule are asserted on this fixture's measured samples.
- The fixture must be non-trivial and in-budget: at least one detected crossing and at
  least one accepted bow or dogleg rung on the measured frame, and no sample may set
  `detectionTruncated`, `verificationBudgetExhausted`, `routeBudgetExhausted` or
  `indexBudgetExhausted`. A vacuous frame (no crossings routed) is not acceptable
  evidence.
- The measurement runs the production `EdgeConflictCorrection.apply` / `EdgeRouter.route`
  on the fixture's frame (and the worker composition where the worker gates are
  asserted); it must not measure a fresh engine's replay seed frame.

### 14.2 A7b — bounded reference-frame behaviour

- On `reference-2000-5000`'s raw/divergent frames, A7 asserts: no OOM; every measured
  frame completes through worker submit, publish-time geometry and routing within
  `RAW_REFERENCE_MAX_FRAME_NANOS = 3_000_000_000L` (unchanged measurement-window pin); whenever a Revision 7 budget is exceeded
  the corresponding flag is set; and the published counted work stays within the pinned
  budgets. No millisecond threshold applies to these frames.
- The measurement-window values from Revision 7 (§13.4) still govern sample counts and
  the process deadline; they may be re-pinned with the measured cost recorded.

### 14.3 Traceability

- A7 now reads: the two stages are in the pinned order with the pinned thresholds; the
  strict gates and the zero-exhaustion rule hold on `settled-reference-2000-5000`
  (`PerformanceTripwiresShould.referenceFixtureStaysInsideTheStrictWorkerAndFirstFrameGates`),
  and the raw reference frames stay bounded and
  flag-visible (`PerformanceTripwiresShould.referenceFixtureStaysBoundedAndFlagVisible`).
