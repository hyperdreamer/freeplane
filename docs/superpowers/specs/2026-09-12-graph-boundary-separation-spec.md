# Graph Workspace Boundary Separation — Technical Specification

- **Date:** 2026-09-12
- **Status:** implementation-ready specification, derived strictly from the approved design
  `docs/superpowers/specs/2026-09-12-graph-boundary-separation-design.md` (revision 8,
  zero blockers at design-review attempt 8). No scope is added or removed; every
  implementation choice the design leaves open is resolved and recorded in §8.
- **Task:** enforce non-overlap of non-nested boundary hulls (and verify ancestor containment)
  on every published non-failed layout frame, with pin-aware exact correction, explicit
  conflicts, diagnostics, and evidence-gated force escalation.
- **Evidence:** `/tmp/pm-probe/RealPipelineOverlapProbe.java`, `/tmp/pm-probe/real-run.log`,
  and `$STATE_ROOT/reports/boundary-overlap-diagnosis.md` (probe reproduction of the real
  `math.fpg` crossing: Axioms ↔ Basic Definitions and Theorems, `siblingOverlap=true`,
  `mst=(-13.5, 0.0)`).
- **Provenance markers** used below: **[D]** derived from the design; **[M]** measured from
  the probe log/report or verified in the repository; **[P]** pinned by this specification
  where the design leaves a choice; **[N]** not pinnable without running the probe/engine
  (deferred to an explicit implementation step, never an unresolved blocker).

---

## 0. Normative conventions

- **Enforced enclosure.** A `ProjectedEnclosure` whose `boundaryTier() != BoundaryTier.SUPPRESSED`
  and for which the frame's computed `GraphGeometry` contains a hull for `hullKey()`. Suppressed
  enclosures are not painted, not hit-tested, and are excluded from both invariants and correction
  (design §4.2, matching `GraphPainter`, `GraphCanvas`, `GraphSearchModel`).
- **Hull.** The `HullGeometry` for an enforced enclosure, computed by
  `org.freeplane.plugin.graph.geometry.GraphGeometryEngine.computeHulls(GraphProjection,
  LayoutPositions, GeometryTextMetrics)`. Hull shape and generation do not change.
- **Pair.** An unordered pair of distinct enforced hulls. Its **canonical order** is the
  ascending lexicographic order of the two canonical hull keys (§3.3); "first"/"second" always
  refer to that order. Ancestor/descendant pairs cannot occur across maps
  (`ProjectedEnclosure.validateParent` requires one map).
- **Penetration.** For a non-nested violation pair, `|t| = Math.hypot(t.x(), t.y())` of the
  `HullIntersection.minimumSeparatingTranslation(first, second)` result. For ancestor findings
  the penetration is `0.0` (§8 R4).
- **Violation pair / residual pair.** A detected violating pair, deduplicated by canonical pair
  key. A residual pair is a violation pair present in the terminal detection pass.
- **Displacement round.** One application of the per-round accumulated displacement field. The
  per-iteration node-separation pass is not a displacement round; a round is counted only when
  the plan contains at least one displacement (design §4.5).
- **Published frame.** A `LayoutFrame` returned by `LayoutWorker` (or copied by
  `LayoutSettleLoop.failedFrame`) that reaches `CanvasState`. Engine-internal frames are out of
  scope.
- **Determinism (design I5).** Identical inputs must produce identical positions, conflict
  lists, round counts and diagnostic values. Every collection iterated by the component is an
  immutable list/map from `GraphProjection`, `LayoutPositions` or the computed `GraphGeometry`,
  whose iteration order is stable for identical inputs; the component does not sort except where
  this specification says it sorts (violations, canonical keys, blocking pins, applied
  displacements).
- **Java level.** Java 8 source level, 4-space indentation, no new dependencies, no
  `freeplane_api`, OSGi export/import, persistence or schema change. All new production types
  live in `org.freeplane.plugin.graph.layout`.
- The design is normative. This specification only pins values, names, orders, encodings,
  fixtures and seams. Where the two could be read against each other, the design text wins; §8
  records every resolved reading and no unresolved conflict.

---

## 1. Pinned constants

| # | Constant | Value | Kind | Placement / justification |
|---|---|---|---|---|
| C1 | `BoundarySeparationCorrection.MAX_DISPLACEMENT_ROUNDS` | `4` | **[P]** design §4.5 | The design's `MAX_ITERATIONS = 4`; the counter counts displacement-applying rounds only. |
| C2 | `HULL_CLEARANCE` | `16.0` world units | **[M]** | `GraphGeometryEngine.HULL_CLEARANCE` (`GraphGeometryEngine.java:23`). Re-declared as `BoundarySeparationCorrection.HULL_CLEARANCE`; a test asserts cap-derived supports equal `computeHull` supports on the frozen fixture. |
| C3 | `BASE_RADIUS` | `8.0` world units | **[M]** | `GraphGeometryEngine.BASE_RADIUS` (`GraphGeometryEngine.java:22`); node radius is `BASE_RADIUS · prominence.scale()`. |
| C4 | `BOUNDARY_PADDING` | `8.0` world units | **[M]** | `GraphGeometryEngine.BOUNDARY_PADDING` (`GraphGeometryEngine.java:26`); used for empty-enclosure label octagons. |
| C5 | `SIBLING_GAP` | `8.0` world units | **[M]** | `GraphStreamLayoutEngine.BoundarySizes.SIBLING_GAP` (`GraphStreamLayoutEngine.java:512`). **Not used by the correction** (design §3 I1); named only to keep force-side constants out of the correction contract. |
| C6 | `SUPPORT_COMPARISON_EPSILON` | `1e-9` relative | **[P]** design §4.3b.2 | Cap-band inclusion `ε = 1e-9 · max(|contribution|, |S|, |S − band|)`; scale-relative, never an absolute world-unit window. |
| C7 | MST epsilon | absolute `1e-9` world units after unscaling | **[M]** design §4.2 | `HullIntersection.EPSILON = 1e-9`; `t == (0,0)` exactly (both components `+0.0`) means "contact by tolerance" for a strictly overlapping pair. |
| C8 | Support-width floor | non-empty hulls `≥ 48`, empty hulls `≥ ~11.3` in every one of the 8 normals | **[D]** design §4.2 | `2·(r + HULL_CLEARANCE)` for a node with `r = 8`, `2·(BOUNDARY_PADDING …)` for an empty label octagon. Bounds the tolerance caveat to `computeHull`-produced hulls. |
| C9 | Canonical key escapes | order-sensitive: `%`→`%25`, `|`→`%7C`, `:`→`%3A`, `,`→`%2C` | **[P]** design §4.2 | Applied `%` first; see §3.3. |
| C10 | Timing aggregation | `System.nanoTime()` sums per `apply` call | **[P]** design §4.7 | `separation` = every node-separation pass; `hull` = every `computeHulls`; `plan` = detection + candidate evaluation + conflict construction; `apply` = every displacement application. |

---

## 2. Data structures and exact types

All new types are `final`, validate constructor inputs (`Objects.requireNonNull`, finite
coordinates, nonnegative counts), copy collections defensively, and expose unmodifiable views.
No new type is `Serializable`. The result is a final class, not a Java `record` (Java 8 target);
its accessor shape is the design's "result record shape".

### 2.1 `org.freeplane.plugin.graph.layout.BoundarySeparationCorrection` (new, public)

```java
public final class BoundarySeparationCorrection {
    public static final int MAX_DISPLACEMENT_ROUNDS = 4;   // C1
    static final double HULL_CLEARANCE = 16.0;             // C2
    static final double SUPPORT_COMPARISON_EPSILON = 1e-9; // C6

    /** Production bound (C1). Stateless apart from a private GraphGeometryEngine cache. */
    public BoundarySeparationCorrection();

    /** Package-private test seam (R8): the same algorithm with a smaller bound; rejects
     *  `maxDisplacementRounds < 1` with `IllegalArgumentException`. */
    BoundarySeparationCorrection(int maxDisplacementRounds);

    /**
     * Owns the complete correction for one raw engine frame: every node-separation pass,
     * every hull computation, violation detection, displacement planning and application,
     * the bounded loop, and the terminal detection. Pure with respect to its arguments.
     */
    public BoundarySeparationResult apply(GraphProjection projection, LayoutPositions positions,
            GeometryTextMetrics metrics, List<PinProjection> pins);

    /** Package-private test seam (R8): the single guarded MST entry point. */
    static LayoutPoint guardedMinimumSeparatingTranslation(HullGeometry first, HullGeometry second);

    /** Package-private verification seam (R8): canonical pair keys of ANCESTOR_ESCAPE pairs. */
    static List<String> ancestorEscapePairKeys(GraphProjection projection,
            Map<EnclosureHullKey, HullGeometry> hulls);
}
```

- `apply` throws `NullPointerException` for null arguments (existing `MapTierCorrection` style),
  `BoundarySeparationException` for every guarded MST failure and for any internal invariant
  violation named in §4, and never returns a result for which neither
  `diagnostics().boundaryVerified()` nor `diagnostics().boundaryCovered()` holds.
- The component may hold a private `GraphGeometryEngine` for its cache; like the current
  `LayoutWorker`, a component instance is used by one worker thread at a time. The
  `GraphGeometryEngine` in `LayoutSettleLoop` is untouched and remains the painting-side engine.
- `MAX_DISPLACEMENT_ROUNDS` is public so tests and diagnostics can assert the bound; the
  package-private bounded constructor exists only so the bound-path tests can force
  `ROUND_LIMIT` without changing production behavior (R8).

### 2.2 `org.freeplane.plugin.graph.layout.BoundarySeparationResult` (new, public)

```java
public final class BoundarySeparationResult {
    public LayoutPositions positions();          // post-separation, exactly what was detected
    public BoundarySeparationDiagnostics diagnostics();
    public int nodeResidualViolations();         // final node-separation residual (I4)
    public Map<String, LayoutPoint> appliedDisplacements(); // same content as diagnostics()
    public BoundarySeparationTimings timings();
}
```

- `positions()` are always post-node-separation and are exactly the positions on which
  `diagnostics().hullResidualViolations()` and `diagnostics().residualHullPairs()` were measured.
- `appliedDisplacements()` is the per-frame accumulated correction field: canonical-key string
  (`n:<canonical node key>` for nodes, `a:<canonical hull key>` for anchors) → correction
  displacement vector. It contains only non-zero vectors, in ascending lexicographic key order
  (R12). Node-separation nudges are not part of this field.
- The constructor is package-private (only the component creates results); accessors are public.

### 2.3 `org.freeplane.plugin.graph.layout.BoundarySeparationDiagnostics` (new, public)

```java
public final class BoundarySeparationDiagnostics {
    public BoundarySeparationDiagnostics(List<BoundaryConflict> conflicts, List<String> residualHullPairs,
            int hullViolationsDetected, int hullResidualViolations, int rounds,
            double displacementRms, double displacementMax, Map<String, LayoutPoint> appliedDisplacements,
            double deltaRms, double deltaMax);

    public static BoundarySeparationDiagnostics empty();

    public List<BoundaryConflict> conflicts();        // one per residual pair, in violation order
    public List<String> residualHullPairs();          // canonical pair keys, same order
    public int hullViolationsDetected();              // first detection pass of the frame
    public int hullResidualViolations();              // terminal detection pass, all kinds
    public int rounds();                              // displacement-applying rounds (0..C1)
    public double displacementRms();                  // over moved keys of appliedDisplacements
    public double displacementMax();
    public Map<String, LayoutPoint> appliedDisplacements();
    public double deltaRms();                         // worker-side decoration (0 for component results)
    public double deltaMax();
    public boolean boundaryVerified();                // hullResidualViolations == 0
    public boolean boundaryCovered();                 // I6 set equality (§3.10)
    public double worstMapDisplacement();             // §7.2 item 5 compactness metric, derived (R14)
    public BoundarySeparationDiagnostics withDeltas(double deltaRms, double deltaMax); // worker decoration
}
```

- `conflicts` and `residualHullPairs` are ordered by the §3.3 violation order; both are
  deduplicated by pair key by construction.
- `displacementRms()`/`displacementMax()`: let `M` be the entries of `appliedDisplacements()`
  with non-zero vector; `n = |M|`; `displacementRms = sqrt(Σ_{k∈M}|v_k|² / n)`,
  `displacementMax = max_{k∈M}|v_k|`; both `0.0` when `n == 0`.
- `deltaRms()`/`deltaMax()`: default `0.0` for component results; the worker replaces them via
  `withDeltas` (§5.1).
- `worstMapDisplacement()`: for each `MapReferenceId` that owns at least one displaced key of
  this frame's `appliedDisplacements()` (`n:` node or `a:` anchor, same-map and cross-map
  components alike), the maximum `|v|` among that map's displaced keys; the maximum of those
  map values; `0.0` when no key is displaced. This maximum-over-all-displaced-keys value
  (including same-map components) is the number recorded for §7.2 item 5; it upper-bounds the rigid
  cross-map map-delta component.
- `boundaryCovered()`: §3.10. `boundaryVerified()` implies `boundaryCovered()` (the empty
  residual set is vacuously covered).
- `empty()` = empty conflicts/residual lists, all counts `0`, all metrics `0.0`, empty
  displacement map; used by `LayoutFrame.of(...)` and `EMPTY_FAILED_FRAME`.

### 2.4 `org.freeplane.plugin.graph.layout.BoundaryConflict` (new, public)

```java
public final class BoundaryConflict {
    public BoundaryConflict(EnclosureHullKey firstHull, EnclosureHullKey secondHull, Kind kind,
            Reason reason, List<PinProjection> blockingPins);

    public EnclosureHullKey firstHull();     // canonical (smaller key) side
    public EnclosureHullKey secondHull();    // canonical (larger key) side
    public MapReferenceId firstMap();        // derived: firstHull().mapReferenceId()
    public MapReferenceId secondMap();       // derived: secondHull().mapReferenceId()
    public String pairKey();                 // canonicalPair(firstHull, secondHull) §3.3
    public Kind kind();
    public Reason reason();
    public List<PinProjection> blockingPins(); // active pins only, R10 order

    public enum Kind { SIBLING_CROSSING, SIBLING_CONTAINMENT, ANCESTOR_ESCAPE }
    public enum Reason { STRUCTURAL_ESCAPE, IMMOVABLE_SIDES, ROUND_LIMIT }
}
```

- The constructor normalizes the pair into canonical order (R18), so a conflict can never name a
  non-canonical pair. Same-map conflicts are legal: `firstMap()` may equal `secondMap()`
  (this replaces `LayoutConflict`'s distinct-map requirement, `LayoutConflict.java:20-22`).
- `blockingPins` are copied and sorted by `(mapReferenceId().value().toString(),
  source().nodeId().value())` ascending (R10); dormant pins are rejected with
  `IllegalArgumentException` (the deterministic component never produces them).
- `Kind` mapping: `SIBLING_CROSSING` iff `HullIntersection.siblingOverlap(first, second)`;
  otherwise `SIBLING_CONTAINMENT` for an inclusive-containment violation; `ANCESTOR_ESCAPE`
  for ancestor findings. The kinds are mutually exclusive by the predicate (§3.2).
- `Reason` precedence per residual pair is in §3.10.

### 2.5 `org.freeplane.plugin.graph.layout.BoundarySeparationException` (new, public)

```java
public final class BoundarySeparationException extends RuntimeException {
    public BoundarySeparationException(String message);
    public BoundarySeparationException(String message, Throwable cause);
    public BoundarySeparationException(Throwable cause);
}
```

- It is a `RuntimeException` so it travels the existing `LayoutWorker` fail-closed channel
  (`LayoutWorker.java:254` and `:273`). Every wrapped MST failure chains its original
  `RuntimeException` (typically `IllegalArgumentException` from `LayoutPoint.of`) as cause.

### 2.6 `org.freeplane.plugin.graph.layout.BoundarySeparationTimings` (new, public)

```java
public final class BoundarySeparationTimings {
    public BoundarySeparationTimings(long separationNanos, long hullNanos, long planNanos, long applyNanos);
    public long separationNanos();
    public long hullNanos();
    public long planNanos();
    public long applyNanos();
}
```

- Values are nonnegative `System.nanoTime()` differences aggregated over the whole `apply` call
  (C10). The performance diagnostic maps them as `SEPARATION = separationNanos`,
  `HULL = hullNanos`, `CORRECTION = planNanos + applyNanos` (§5.6).

### 2.7 `org.freeplane.plugin.graph.layout.CanonicalLayoutKeys` (new, package-private)

```java
final class CanonicalLayoutKeys {
    static String endpoint(SourceNodeKey source);
    static String hull(EnclosureHullKey hull);
    static String nodeField(ProjectedNodeKey node);      // "n:" + endpoint(node.source())
    static String anchorField(EnclosureHullKey hull);    // "a:" + hull(hull)
    static String pair(EnclosureHullKey first, EnclosureHullKey second);
    static String escape(String value);
}
```

- Exact encoding and ordering: §3.3. This type is package-private, is not added to
  `publicLayoutTypes()` and is covered by the determinism/collision tests.

### 2.8 `LayoutFrame` — diagnostics field (existing, public, modified)

`LayoutFrame` keeps `stepIndex`, `positions`, `failed`, `residualViolations`, `idle`,
`verified()`, `residualViolations()`, `stepIndex()`, `positions()`, `failed()`, `idle()` and
the two `of(...)` factories unchanged. Changes (R2):

```java
public BoundarySeparationDiagnostics boundaryDiagnostics();      // replaces the conflict list field
public List<BoundaryConflict> conflicts();                        // delegate to boundaryDiagnostics().conflicts()
public static LayoutFrame withDiagnostics(LayoutFrame raw, BoundarySeparationDiagnostics diagnostics,
        PerceptualIdlePolicy.IdleMeasurement idle);               // replaces the List<LayoutConflict> overload
```

- `LayoutFrame.of(...)` constructs with `BoundarySeparationDiagnostics.empty()` exactly as it
  today constructs an empty conflict list; therefore `verified()` and the node residual keep
  their existing meaning (design §4.6, §8.2).
- `conflicts()` is kept so existing consumers (`LayoutSettleLoop.failedFrame`, the performance
  diagnostic, tests) migrate by type only.
- Frame construction still validates finite positions; no new validation is added to frames.

### 2.9 Deleted types

- `org.freeplane.plugin.graph.layout.MapTierCorrection` is **deleted** (`MapTierCorrection.java`
  removed; no delegating wrapper, design §4.7).
- `org.freeplane.plugin.graph.layout.LayoutConflict` is **deleted** (`LayoutConflict.java`
  removed; replaced by `BoundaryConflict`).
- `MapTierCorrectionShould` is **renamed/migrated** to `BoundarySeparationCorrectionShould`;
  no test may keep the old class name.

---

## 3. Contracts

### 3.1 Enforcement set and hull availability

1. Enforced enclosures are `projection.enclosures()` filtered by
   `boundaryTier() != BoundaryTier.SUPPRESSED`, in projection order.
2. For an enforced enclosure whose `hullKey()` is absent from the computed hull map, the
   enclosure is skipped defensively (never throws on a valid projection, design §4.2/§5).
   A duplicate canonical hull key inside the computed map cannot occur
   (`GraphGeometryEngine.computeHulls` throws on duplicate projected enclosure hull keys);
   if two enforced enclosures nevertheless resolve to one canonical hull key, both are skipped
   for that key (defensive).
3. The enclosure parent map (`EnclosureHullKey` → `Optional<EnclosureHullKey>`) is built once
   per `apply` call from the projection's `ProjectedEnclosure.parentHull()` values.
4. Suppressed enclosures, and any pair with a missing hull, contribute no violation, no
   displacement and no conflict.

### 3.2 Violation predicate and tolerance split

For two enforced hulls `A`, `B` with `A != B`:

```
strictlyOverlapping(A, B) =
      HullIntersection.siblingOverlap(A, B)
   || containsInclusive(A, B)         // every vertex of B inside A (HullGeometry.contains)
   || containsInclusive(B, A)         // every vertex of A inside B
```

`HullIntersection.siblingOverlap` is exact (no epsilon); it is false when one hull contains the
other or when they merely touch. The extra containment clauses therefore add inner containment,
coincident and equal hulls to the violation set. Bare contact without containment is not
`strictlyOverlapping`.

Detection splits strictly overlapping pairs three ways (design §4.2):

1. `t = HullIntersection.minimumSeparatingTranslation(A, B)`; `t.x() == 0.0 && t.y() == 0.0`
   (bit-exact positive zero) → **contact by tolerance**: not a violation, not a residual, no
   conflict. This covers shallow crossings whose overlap is within `HullIntersection`'s
   `1e-9`-after-unscaling epsilon. Containment can never be hidden by this rule because the
   `computeHull`-produced support widths of C8 are far above the epsilon (design §4.2).
2. `t` non-zero (hence both components finite, because `LayoutPoint.of` rejects non-finite
   values) → **violation**. Penetration is `Math.hypot(t.x(), t.y())`.
3. An MST call that throws a `RuntimeException` (reachable on extreme coordinates through
   `LayoutPoint.of` rejecting non-finite products) is wrapped by
   `guardedMinimumSeparatingTranslation` into `BoundarySeparationException` chaining the
   original exception. It is never treated as tolerance and never silently dropped. Every MST
   call in the component goes through this guard.

Violation kind: `SIBLING_CROSSING` iff `siblingOverlap(A, B)`; otherwise
`SIBLING_CONTAINMENT` (inclusive containment, including equal/coincident hulls).

**Ancestor containment (I2, verification only).** For every enforced child hull `A` whose
parent hull `P` is also enforced (both non-suppressed and both present in the hull map; R5),
test every vertex of `hull(A).exactPolygon()` with `hull(P).contains(vertex)`. The test is not
gated on bounding-box overlap. If any vertex fails, the pair `(A, P)` is one `ANCESTOR_ESCAPE`
violation counted per pair (not per escaping vertex; R3). Ancestor findings are never displaced
(design §4.3b.9).

### 3.3 Canonical keys, pair order and violation order

Encoding (design §4.2, C9):

```
escape(s):
    s = s.replace("%", "%25")     // must run first
    s = s.replace("|", "%7C")
    s = s.replace(":", "%3A")
    s = s.replace(",", "%2C")

endpoint(persisted):  "m:" + mapReferenceId() + "|p:" + escape(persistedReference().get().nodeId().value())
endpoint(transient):  "m:" + mapReferenceId() + "|t:" + escape(join(".", structuralPath()))
hull(k):              join(",", sorted_ascending( endpoint(e.source()) for e in k.endpointKeys() ))
pair(a, b):           min(hull(a), hull(b)) + "|" + max(hull(a), hull(b))   // String.compareTo order
nodeField(n):         "n:" + endpoint(n.source())
anchorField(k):       "a:" + hull(k)
```

- `join(".", path)` uses the nonnegative integers of `SourceNodeKey.structuralPath()` in list
  order; `.` needs no escape and no escaped payload can contain a raw `.` ambiguity because the
  path alphabet is digits and `.` only.
- A transient key may carry an empty `structuralPath` (`SourceNodeKey.transientPath` accepts
  one); if it does, the `t:`/`p:` prefixes still separate it from a persisted endpoint, so
  collision-freedom is unaffected. `SourceNodeKey.persisted(...)` never carries a path.
- Sorting the endpoint encodings makes the hull key independent of `endpointKeys()` order.
- Collision-freedom: map ids are canonical UUID strings containing none of the escaped
  characters; escaped payloads cannot contain raw `|`, `:`, `%` or `,`; the `p:`/`t:` prefixes
  separate persisted from transient endpoints; sorting plus `,`-joining is injective on
  endpoint multisets. The component never concatenates raw ids.
- **Violation order** (used for planning, conflict lists, `residualHullPairs` and applied-
  displacement accumulation order): descending penetration (`Double.compare` on
  `Math.hypot(t.x(), t.y())`), then ascending `pair(a, b)`. Ancestor findings have penetration
  `0.0` and therefore follow all MST-bearing findings, ordered by ascending pair key (R4).
- Violations are deduplicated by `pair(a, b)` before planning, so each pair appears once per
  detection pass.

### 3.4 Detection sweep

1. Collect the enforced hulls (with their geometry) in projection order.
2. Find non-nested candidate pairs with a bounding-box sweep (design §4.2): sort hulls by
   `(minX, minY, canonical hull key)` ascending; sweep in that order maintaining an active list
   of hulls with `maxX >= current.minX`; for each active hull test vertical bbox overlap
   (`active.minY <= current.maxY && current.minY <= active.maxY`) before applying
   `strictlyOverlapping`. Remove hulls whose `maxX < current.minX` from the active list. The
   result is the same pair set as an O(H²) scan; the sweep is O(H log H + K).
3. Skip pairs in the ancestor relation in both directions (they are not non-nested and are
   handled by I2).
4. Every remaining candidate pair goes through §3.2 in the canonical order of its keys.

### 3.5 Cross-map pairs — rigid map translation (policy (a))

Two enforced hulls with different `mapReferenceId()` are a cross-map non-nested pair
(enforcement is generalized from map roots only to every enforced cross-map pair; design
§4.3a). For each such violation pair, in violation order, with `t` applied to the second hull:

- A map is **rigid** iff it contains at least one active pin: an entry of `pins` with
  `active() == true` whose `projectedNode()` is present and whose projected node's
  `mapReferenceId()` equals the map. Dormant pins do not make a map rigid.
- both maps rigid → per-pair conflict `IMMOVABLE_SIDES`; positions unchanged.
- first rigid, second not → second map delta `+= t`.
- second rigid, first not → first map delta `+= −t`.
- neither rigid → first map delta `+= −t/2`, second map delta `+= +t/2`.

Map deltas accumulate across pairs per `MapReferenceId` (double component addition in violation
order) and are applied once per round to **every node and every anchor** of the moved map,
preserving map-unit semantics (the existing `MapTierCorrection.applyDeltas` behavior).
A rigid map never moves, so no pinned node is ever displaced by this policy (I3).

### 3.6 Same-map pairs — support and cap-set recursion (policy (b))

For a same-map non-nested violation pair `(A, B)` in canonical order, `t` finite non-zero,
`m = |t|`, `d = t/m` (unit), `u_A = d`, `u_B = −d`. The first hull separates by `−t`, the
second by `+t` (signs bound to canonical order; `HullIntersectionShould
.returnsTheExactMinimumTranslationAppliedToTheSecondHull`).

**Support.** For hull `H` and unit direction `u`:

```
empty(H):   S(H,u) = anchor·u + max(|u.x|·(labelWidth/2 + BOUNDARY_PADDING),
                                     |u.y|·(labelHeight/2 + BOUNDARY_PADDING))
non-empty:  S(H,u) = HULL_CLEARANCE + max(
                  max over direct nodes n of (center(n)·u + radius(n)),
                  max over direct child hulls C of polySupport(C,u))
polySupport(C,u) = max over vertices v of C.exactPolygon() of v·u
```

`labelWidth`/`labelHeight` are the largest-by-area label of the enclosure measured by the
frame's `GeometryTextMetrics`, exactly as `GraphGeometryEngine.labelSize` (no clearance for
empty hulls; `BOUNDARY_PADDING` only). `radius(n) = BASE_RADIUS · prominence.scale()`.
This is `computeHull`'s own support formula (design §4.3b.2); on the 8 constant normals the
formula equals the half-plane limits. The frozen-fixture test additionally asserts that the
formula equals the computed polygon support along the MST axis (R11).

**Depth.** For a contributor `c` of hull `H` along `u`:
`contribution_H(c)` and `depth_H(c) = S(H,u) − contribution_H(c)`:

- direct node `n`: `contribution = center(n)·u + radius(n) + HULL_CLEARANCE`;
- direct child hull `C`: `contribution = polySupport(C,u) + HULL_CLEARANCE` (the child's depth
  is only used for the recursion decision; its own level has its own `S(C,u)`);
- empty hull: the anchor is its only contributor at depth `0` (its own contribution equals
  `S`, computed with label bounds and no clearance).

**Cap set.** `capSet(H, u, band)` (`band > 0`) is the recursive contributor tree:

1. if `H` is empty → `{ anchor(H) }`;
2. otherwise the set contains every direct node `n` with
   `contribution_H(n) ≥ S(H,u) − band − ε`;
3. the set contains every direct child hull `C` with
   `polySupport(C,u) + HULL_CLEARANCE ≥ S(H,u) − band − ε`, and for each such `C` also the
   recursive `capSet(C, u, band)`;
4. the maximum-support contributor of `H` is always in the set (a direct node with
   `contribution_H = S`, or a child hull with `polySupport + HULL_CLEARANCE = S`);
5. `ε` per comparison is `SUPPORT_COMPARISON_EPSILON · max(|contribution|, |S(H,u)|,
   |S(H,u) − band|)` (C6).

`moves(H, u, a)` is the effective displacement set: the recursive `capSet(H, u, a)` filtered to
(a) unpinned direct nodes and (b) anchors of empty contributing hulls. Anchors are never
pinned. `pins(H, u, a)` is the set of `(pinned direct node p, enclosing hull H')` pairs for
every hull `H'` visited by the recursion (design §4.3b.2/§4.3b.5).

### 3.7 Per-candidate capability and selection

**Capability for an applied magnitude `a` on a side.** `valid(H, u, a)` holds iff

1. `moves(H, u, a)` is non-empty (an unpinned contributor/anchor can take the displacement), and
2. every `(p, H')` in `pins(H, u, a)` satisfies `depth_{H'}(p) >= a` — the depth-strict tie
   rule: `depth < a` invalidates the candidate, `depth == a` is a legal tie (the pin ends
   exactly at the new edge, contact allowed), and the tied pin is never displaced because
   `moves` excludes pinned nodes.

Depth is local to each recursion level (R6). Pinned contributors are never displaced in any
candidate (design I3, §4.3b.3/§4.3b.5).

**Candidate distributions** in this deterministic preference order; the first valid candidate
is selected:

| # | Name | First side | Second side | Validity |
|---|---|---|---|---|
| 1 | both-sides half | `−t/2`, `a = m/2` | `+t/2`, `a = m/2` | `valid(A,u_A,m/2) && valid(B,u_B,m/2)` |
| 2 | first-only full | `−t`, `a = m` | no move | `valid(A,u_A,m)` |
| 3 | second-only full | no move | `+t`, `a = m` | `valid(B,u_B,m)` |
| 4 | first-side complementary split | `−t·(d_A/m)`, `a_A = d_A` | `+t·(m−d_A)/m`, `a_B = m−d_A` | `0 < d_A < m`, `valid(A,u_A,d_A) && valid(B,u_B,m−d_A)` |
| 5 | second-side complementary split | `−t·(m−d_B)/m`, `a_A = m−d_B` | `+t·(d_B/m)`, `a_B = d_B` | `0 < d_B < m`, `valid(A,u_A,m−d_B) && valid(B,u_B,d_B)` |
| 6 | conflict | positions unchanged | | no candidate above is valid → `IMMOVABLE_SIDES` |

`d_A`/`d_B` is the smallest local depth strictly greater than `0` and strictly less than `m`
among pinned direct nodes in the recursive full-band (`band = m`) closure of that side's cap
set; when no such pin exists the candidate is inapplicable (R7). Because `valid(A, d_A)`
requires every pin in the `band = d_A` closure to have depth `>= d_A`, a depth-`0` pinned
contributor invalidates the complementary candidate as well.

Worked falsification cases (design §10.7, §6.1):

- attempt-5 (`m = 10`, unpinned max contributor plus a pinned contributor at depth `6` on both
  sides) resolves by candidate 1 (`a = 5 <= 6` on both sides).
- mixed (`m = 10`, pinned depth `3` on the first side, pinned depth `9` on the second side):
  candidates 1–3 are invalid; candidate 4 gives `d_A = 3`, `valid(A,3)` (tie) and
  `valid(B,7)` (`9 >= 7`) → first moves `3`, second moves `7`.
- a pinned max contributor (depth `0`) on one side forces candidate 3 or 2 on the other side.
- pinned contributors at depth `0` on both sides make every candidate invalid →
  `IMMOVABLE_SIDES`.

**Selection.** For the chosen candidate, each moving side's displacement set is
`moves(H, u, a_side)` with that side's applied magnitude. If a selected candidate's set were
empty (impossible by construction because `valid` requires it non-empty), the component throws
`BoundarySeparationException` (fail closed, design §5).

### 3.8 Application, accumulation and anchor consistency

1. **Per-round snapshot.** All cap sets, depths and candidate decisions for a round are
   computed against the hulls of the round's first detection pass (raw geometry of the round;
   design §4.3b.6). Ordering of pairs affects conflict recording only, never the accumulated
   result.
2. **Accumulation per key.** Node displacements accumulate per canonical node field
   (`n:<key>`) and anchor displacements per canonical anchor field (`a:<key>`) by double
   component addition in violation order; cross-map map deltas accumulate per
   `MapReferenceId`. Accumulation is across all pairs of the round; a node displaced for one
   pair can receive additional displacement from another pair.
3. **Application.** After planning the whole round, apply all accumulated deltas exactly once:
   every node field adds its vector; every anchor field adds its vector; every node and anchor
   of a translated map adds the map's delta. No pinned node receives a delta (guaranteed by the
   policies themselves).
4. **Anchor consistency (same-map only).** After the node and anchor fields of the round are
   known, for each enclosure `e` of the projection:
   - if `e` is empty and its anchor was assigned a displacement as an empty contributing hull
     in this round, `delta(anchor(e))` is that accumulated anchor field;
   - else if at least one moved node (non-zero accumulated node field of this round) lies in
     the transitive subtree of `e` (all descendant enclosures and their direct nodes),
     `delta(anchor(e))` is the component-wise mean of those moved nodes' accumulated vectors;
   - else `delta(anchor(e))` is zero (the anchor keeps its position).

   The two rules compose additively with cross-map map deltas when a map takes both kinds of
   correction in one round. The subsequent node-separation pass moves nodes only and copies
   anchors through unchanged; anchors therefore follow correction displacements exactly
   (design §4.3b.8).
5. **Per-frame field.** Result `appliedDisplacements()` is the accumulation of the round fields
   over all rounds of the frame (correction only), keys with zero vector omitted, ascending
   lexicographic key order (R12).

### 3.9 Loop structure and terminal coverage

```
rounds = 0
for iteration in 1..maxDisplacementRounds:            # C1 = 4 in production
    positions  = nodeSeparation(positions)            # existing NodeSeparationProjection, pins excluded
    hulls      = computeHulls(projection, positions, metrics)
    violations = detectViolations(hulls)              # §3.2–§3.4
    if violations is empty:
        return verified(positions, rounds)            # residual 0, conflicts empty
    plan = planDisplacements(violations, pins)        # §3.5–§3.8
    if plan.displacements is empty:
        return covered(positions, conflictsFor(violations, boundExhausted=false))
    positions = apply(positions, plan.displacements)  # accumulated once
    rounds++
# bound reached: one final separated + measured state
positions  = nodeSeparation(positions)
hulls      = computeHulls(projection, positions, metrics)
violations = detectViolations(hulls)
if violations is empty:
    return verified(positions, rounds)
return covered(positions, conflictsFor(violations, boundExhausted=true))
```

- `rounds` counts only the `apply` steps: empty projection → `0`; the frozen real case → `1`
  (design §4.3b.7, §6.1); the bound → `4` (or the seam value, R8).
- **Convergence (design §4.3b.7).** For a pair planned in isolation, any valid candidate
  resolves it in one displacement round: the relative displacement is the full `t`, and the
  chosen candidate's band guarantees no pinned contributor caps it. The two interruption
  sources are (a) per-key accumulation mixing another pair's vector into the same contributor
  and (b) the mandatory node-separation pass before the next detection; both are handled by
  the loop. Published-frame cleanliness therefore comes from the bounded loop's terminal
  coverage (§3.10), not from the one-round claim. The reproduced case has no such interaction
  and publishes at `rounds == 1` (§6.1); the general gate is `rounds <= 2` (§7.2 item 1).
- The returned positions are always post-node-separation and are exactly the positions on
  which the terminal residual was detected.
- `verified` requires `hullResidualViolations == 0`; `covered` requires the I6 bijection on the
  final residual pair set (§3.10). The component never returns `!verified && !covered`; it
  throws `BoundarySeparationException` instead.
- `hullViolationsDetected` is recorded on the first detection pass of the frame (iteration 1,
  after the first node-separation pass), not on the untouched raw engine frame (design §4.6).
- `nodeResidualViolations` is the `residualViolations` of the last executed
  `NodeSeparationProjection.project` call (the pass immediately preceding the returned
  detection); it preserves I4 and the existing `LayoutFrame` contract.

### 3.10 Conflicts and the I6 bijection

`conflictsFor(violations, boundExhausted)` builds the conflict list **solely from the final
detected violation set** (design §4.4), deduplicated by canonical pair, one record per residual
pair and no record outside it. Reasons are recomputed on that final set:

1. `ANCESTOR_ESCAPE` → `kind = ANCESTOR_ESCAPE`, `reason = STRUCTURAL_ESCAPE`.
2. Non-nested pair:
   - cross-map and both maps rigid → `IMMOVABLE_SIDES`;
   - same-map and `valid(first, u_first, a)`/candidate evaluation finds no valid candidate on
     the final geometry → `IMMOVABLE_SIDES` (this is necessarily the case on the early
     `plan.displacements is empty` exit);
   - any remaining pair with an available displacement (same-map with a valid candidate, or
     cross-map with at most one rigid map) and `boundExhausted == true` → `ROUND_LIMIT`;
   - otherwise (defensive; cannot occur on the early exit) → `IMMOVABLE_SIDES`.
3. Blocking pins (all active, §2.4):
   - same-map pair: every active pin whose projected node lies in the transitive subtree of
     either hull (union, deduplicated);
   - cross-map pair: every active pin of either involved map (matching today's
     `MapTierCorrection.blockingPins`).

`residualHullPairs` is the list of `pairKey()` values of the final residual pairs in violation
order; `conflicts` is the list of the corresponding `BoundaryConflict`s in the same order.
`boundaryCovered()` is

```
distinct(conflicts.pairKey()) == distinct(residualHullPairs)
```

as sets, with both lists duplicate-free by construction. This is the I6 bijection in both
directions (design §3 I6): every residual pair has exactly one conflict naming that pair, and
no conflict names a non-residual pair. A coverage failure is not publishable: the component
throws, and `LayoutWorker` fails closed (§4).

### 3.11 Diagnostics measurement semantics

- `hullViolationsDetected` and `hullResidualViolations` count violation **pairs** of all kinds
  (`1` per residual pair); multiple escaping vertices of one parent/child pair collapse into one
  `ANCESTOR_ESCAPE` pair (R3).
- `displacementRms`/`displacementMax` are defined in §2.3 over the per-frame field; the union is
  exactly `appliedDisplacements()`'s keys.
- `deltaRms`/`deltaMax` are worker-side: for consecutive accepted frames `f-1`, `f` with fields
  `D_{f-1}`, `D_f`, let `U = keys(D_{f-1}) ∪ keys(D_f)`; missing keys count as the zero vector;
  `deltaRms = sqrt(Σ_{k∈U}|D_f(k) − D_{f-1}(k)|² / |U|)`, `deltaMax = max_{k∈U}|…|`; both `0.0`
  when `U` is empty. Node fields (`n:`) and anchor fields (`a:`) never collide. The field is
  reset to empty on worker construction, on `LayoutWorker.restart()` and by
  `LayoutSettleLoop.WorkerStepper.reset()` (which constructs a fresh worker).
- `worstMapDisplacement` is the recorded §7.2 item 5 compactness metric (§2.3).
- Diagnostics are test-visible and drive §7; nothing new is painted (design §4.6).

---

## 4. Error behavior and state transitions

### 4.1 Component behavior table

| Case | Behavior |
|---|---|
| Empty projection / no enforced enclosures | Hull no-op: `rounds = 0`, `hullResidualViolations = 0`, `boundaryVerified()`; node separation still runs and its residual is reported. |
| Suppressed enclosure | Excluded from I1/I2 (`§3.1`); never corrected; no conflict. |
| Missing or duplicate hull key | Pair skipped defensively; never throws on a valid projection. |
| Coincident/equal non-nested hulls | `strictlyOverlapping` via inclusive containment; kind `SIBLING_CONTAINMENT`; separated by the finite non-zero MST (candidate/rigid policy as usual). |
| Strict overlap with zero MST | Contact by tolerance: not a violation, not a residual, no conflict. |
| MST call failure on extreme coordinates | `guardedMinimumSeparatingTranslation` wraps the `RuntimeException` (e.g. `IllegalArgumentException` from `LayoutPoint.of`) in `BoundarySeparationException` with cause; fail-closed path (§4.2). |
| Effective displacement set empty for a selected candidate | Impossible by `valid`; if observed, `BoundarySeparationException` (fail closed). |
| No valid candidate distribution | Per-pair conflict `IMMOVABLE_SIDES`; positions unchanged; exactly one conflict for that pair; never silent. |
| Pinned contributor strictly inside a candidate's band (`depth < a`) | Candidate invalid; a less aggressive candidate may still be valid; `depth == a` is a tie and valid, and the pinned node is never displaced. |
| Cross-map both-rigid maps | Per-pair conflict `IMMOVABLE_SIDES`; positions unchanged. |
| Round bound reached with a valid candidate | Final-state residual pairs receive `ROUND_LIMIT` conflicts, with reasons recomputed on the final set. |
| `verified || covered` false | Component throws `BoundarySeparationException`; worker fails closed. |
| Ancestor escape detected | `ANCESTOR_ESCAPE` / `STRUCTURAL_ESCAPE` conflict only; no separating displacement. |
| Empty-enclosure overlap | Anchor-level displacement per §3.6/§3.8. |
| Failed engine / fallback frame | Out of invariant scope; retained `BoundarySeparationDiagnostics` copied unchanged. |

### 4.2 Worker state machine (`LayoutWorker`)

Observable lifecycle flags per worker instance (the code has no `{ready, running, failed}`
state enum): `closed`, `paused`, `hasRequest`, `pendingSubmits` (all under `lifecycleLock`),
plus `failedEngine` and `lastValidFrame`/`previousAppliedDisplacements`.

- `submit`/`step` catch **all** `RuntimeException`s from `accept` (`LayoutWorker.java:254`,
  `:273`) and set `failedEngine = true`, returning `failedFrame(...)`. `BoundarySeparationException`
  deliberately rides this one channel (design §4.1); there is no second fail-closed path.
- `accept` additionally re-asserts `result.diagnostics().boundaryVerified() ||
  result.diagnostics().boundaryCovered()` and throws `BoundarySeparationException` when it is
  false; the throw is caught by the same channel.
- `failedFrame(requestedIndex)` keeps the retained frame's positions, residual, diagnostics
  (including `appliedDisplacements`, `deltaRms`, `deltaMax`), and idle measurement unchanged;
  it changes only `stepIndex` and `failed = true`.
- `restart()` closes the old engine, clears `currentRequest`/`hasRequest`,
  `failedEngine = false`, clears `previousAppliedDisplacements`, and builds a new engine.
  `LayoutSettleLoop.WorkerStepper.reset()` closes the worker and constructs a fresh
  `LayoutWorker` (fresh empty displacement field). `LayoutSettleLoop.failedFrame` copies the
  retained `boundaryDiagnostics()` unchanged (design §4.1, §4.6).
- Recovery rebuilds only the engine: `restart()` keeps the same `BoundarySeparationCorrection`
  instance. The component is stateless across frames apart from its private
  `GraphGeometryEngine` cache, so no partial component state needs rebuilding; `restart()`
  clears the correction's accumulation state by clearing `previousAppliedDisplacements` (R19).
  The component's cache is per component instance and is not recreated with the worker.
- The now-dead `LayoutWorker.geometryEngine` field and the `pinnedNodes(...)` pin-derivation
  scaffolding are removed (repository legacy-removal policy; the component owns hull
  computation and node-separation pin exclusion).

---

## 5. Interfaces and call-site changes

### 5.1 `LayoutWorker` (production)

- Field `MapTierCorrection mapCorrection` (`LayoutWorker.java:49`) → `BoundarySeparationCorrection
  boundaryCorrection = new BoundarySeparationCorrection()`.
- New field `private Map<String, LayoutPoint> previousAppliedDisplacements;` (nullable/empty).
- `accept(request, raw)` (`:278`) becomes:

  1. null/`raw.failed()` handling unchanged (lines `:279-285`);
  2. `validateCoverage(...)` unchanged;
  3. `BoundarySeparationResult result = boundaryCorrection.apply(request.projection(),
     raw.positions(), defaultMetrics(), request.pins());`
  4. coverage assertion (§4.2);
  5. `LayoutPositions corrected = result.positions();`
  6. compute `deltaRms`/`deltaMax` from `result.appliedDisplacements()` and
     `previousAppliedDisplacements` (§3.11);
  7. `PerceptualIdlePolicy.IdleMeasurement idle = idlePolicy.observe(before, corrected);`
     unchanged;
  8. `LayoutFrame.withDiagnostics(LayoutFrame.of(raw.stepIndex(), corrected, false,
     result.nodeResidualViolations()), result.diagnostics().withDeltas(deltaRms, deltaMax), idle)`;
  9. `previousAppliedDisplacements = result.appliedDisplacements();` and the existing
     `currentRequest`/`hasRequest`/`previousCorrectedPositions`/`lastValidFrame` updates.
- `LayoutWorker` no longer computes hulls and no longer calls `NodeSeparationProjection`
  directly: the `geometryEngine` field and the `pinnedNodes(...)` pin-derivation scaffolding
  become dead and are removed (repository legacy-removal policy; the component owns hull
  computation and pin exclusion).
- `failedFrame` (`:355-364`, call at `:363`) passes `retained.boundaryDiagnostics()` instead
  of `retained.conflicts()`.
- `runRestart` (`:339-353`) additionally clears `previousAppliedDisplacements`; it does not
  recreate the correction component.

### 5.2 `LayoutSettleLoop` (production)

- Import/type change at the conflict consumption point (`LayoutSettleLoop.java:760`):
  `final BoundarySeparationDiagnostics diagnostics = retained.boundaryDiagnostics();` and
  `LayoutFrame.withDiagnostics(LayoutFrame.of(index, positions, true, residual), diagnostics,
  retained.idle())`.
- No other change: `LayoutSettleLoop` continues to compute its own painting hulls from
  `frame.positions()`; the publication path inherits the invariant with no second correction
  path (design §4.1).

### 5.3 `LayoutFrame` (production)

§2.8. Engine-internal frames (`LayoutFrame.of`) keep empty diagnostics; `verified()` and the
node residual keep their meaning.

### 5.4 Deletions and renames

- Delete `MapTierCorrection.java` and `LayoutConflict.java`; no wrapper, no compatibility
  shim (repository legacy-removal policy).
- Rename `MapTierCorrectionShould.java` to `BoundarySeparationCorrectionShould.java` and
  migrate its cases (§6); update every `import ...LayoutConflict` to `BoundaryConflict`.
- Update `LayoutWorkerShould`, `LayoutSettleLoopShould` (including the
  `LayoutConflict.of(MAP, otherMap, ...)` construction at `:1194`), `GraphUpdateCoordinatorShould`,
  `GraphWorkspaceCommandAcceptanceShould` (the direct `LayoutConflict` construction and
  `blockingPins` assertion at `:420-424`; cross-map blocking-pin content is unchanged for that
  pair, so the pin list assertion survives type migration).
- `GraphWorkspacePerformanceDiagnostic`: §5.6.

### 5.5 Public layout signature gate

Add to `GraphStreamBoundaryShould.publicLayoutTypes()` (`GraphStreamBoundaryShould.java:84-88`):
`BoundarySeparationCorrection.class`, `BoundarySeparationResult.class`,
`BoundarySeparationDiagnostics.class`, `BoundarySeparationTimings.class`,
`BoundaryConflict.class`, `BoundarySeparationException.class`. `CanonicalLayoutKeys` stays
package-private and is not added. All listed types must expose no `org.graphstream` type in any
public constructor/method parameter, return type, exception type or public field (the existing
`exposeOnlyGraphStreamFreePublicLayoutSignatures` test).

### 5.6 Performance diagnostic stage mapping (`GraphWorkspacePerformanceDiagnostic`)

- Field `MapTierCorrection correctionEngine` (`:100`) → `BoundarySeparationCorrection`.
- Direct probe (`runDirectProbe`, `:341-400`): replace the separate `mapCorrection.apply` +
  `NodeSeparationProjection` + post-correction `computeHulls` block with one call
  `BoundarySeparationResult result = correctionEngine.apply(projection, applied.positions(),
  textMetrics, request.pins());`, then record:
  - `SEPARATION` ← `result.timings().separationNanos()` (`recordWarmup`/`recordMeasured`),
  - `HULL` ← `result.timings().hullNanos()`,
  - `CORRECTION` ← `result.timings().planNanos() + result.timings().applyNanos()`.
  Each stage is still recorded exactly once per sample, preserving
  `validateScenarioLifecycle`'s warmup/measured counts for every `PerformanceMeasurements.Stage`.
  The `MUTATION`, `FORCE` and `PLACEMENT` probes are unchanged. The pre-correction root-overlap
  check (`assertPinnedRootOverlap`, `:554-579`) still computes a raw `computeHulls` on
  `applied.positions()` before the correction call; that pre-correction computation is a probe,
  not a recorded stage.
- The direct probe and the worker probe read the new diagnostics: `rounds()`,
  `hullViolationsDetected()`, `hullResidualViolations()`, `residualHullPairs()`,
  `displacementRms()/displacementMax()`, `worstMapDisplacement()`.
- `validatePinConflicts` (`:531-552`) migrates to `BoundaryConflict`; the `TWO_PINNED_MAPS`
  expected conflict count is re-derived from the first measured run and recorded as a golden
  constant, together with the requirement that every cross-map conflict between the two pinned
  maps lists exactly both `m00-n0001` and `m01-n0001` (R17), while a same-map conflict inside
  one of the pinned maps lists only that map's subtree pins (§3.10). `ONE_PINNED_MAP` keeps
  asserting the absence of rigid (two-pinned-map) conflicts; any same-map conflict that the generalized
  enforcement produces is recorded in the same re-derivation.
- Strict gate unchanged: `PerformanceMeasurements.STRICT_FULL_WORKER_NANOS = 100_000_000L`;
  the correction overhead is recorded from the plan/apply split (design §6.4, §7).

---

## 6. Fixtures and tests

All tests run under `gradle :freeplane_plugin_graph:test` with Java from
`~/.sdkman/candidates/java/21.0.8-zulu`, Java 8 source level. New tests are JUnit 4 + AssertJ,
in the same package as the production types where package-private seams are used.

### 6.1 Frozen real-case fixture (`BoundarySeparationCorrectionShould`)

**Source data** (probe log `/tmp/pm-probe/real-run.log` and the diagnosis report). The
implementation step re-runs `RealPipelineOverlapProbe` to capture **full-precision doubles**
for settled node positions, prominence counts and the pre-correction MST; the fixture embeds
those captured values and must not read Dropbox paths.

- Map: one `MapReferenceId` = `8055d8c8-d71e-40f3-a8ed-5bf2502f2cad` (the real active map).
- Nodes (persisted `SourceNodeKey`, real labels):

| id | label | prominence reach | radius | pin |
|---|---|---|---|---|
| `ID_1133378501` | `Fundation / Regularity` | `2` | `8.0 · scale(2) = 9.6` | — |
| `ID_822182441` | `Replacement Scheme` | `0` | `8.0` | — |
| `ID_130337169` | `Axiom of Choice` | `0` | `8.0` | `(-24.832420395427746, -34.92046985440441)` |
| `ID_1901523076` | `Theorem` | `0` | `8.0` | `(-209.31397564145126, 9.820904009249132)` |
| `ID_1387156674` | `Theorem` | `0` | `8.0` | — |

- Enclosures (single-endpoint hull keys; real ids and labels; tiers and tree as projected):

| hull id | label | tier | parent | direct nodes | direct enclosures |
|---|---|---|---|---|---|
| `ID_435635462` | `Axiomatic Set Theory` | `SUPPRESSED` | — | — | `ID_1675547143` (ZFC) |
| `ID_1675547143` | `ZFC` | `EMPHATIC` | root | — | `ID_1912952190`, `ID_978732953` |
| `ID_1912952190` | `Axioms` | `SUBTLE` | ZFC | Regularity, Replacement, Choice | — |
| `ID_978732953` | `Basic Definitions and Theorems` | `SUBTLE` | ZFC | pinned Theorem, free Theorem | — |

- Raw settled positions (recorded; replace with full precision from the re-run): Regularity
  `(-173.3, 1.8)`, Replacement `(248.6, -59.3)`, Choice `(-24.8, -34.9)`, pinned Theorem
  `(-209.3, 9.8)`, free Theorem `(-241.8, 14.4)`. Pinned node input positions equal their pin
  records. Anchors: all four hulls are non-empty, so `GraphGeometryEngine` ignores
  `positions.anchors()` for them; the fixture supplies finite anchor values (the recorded node
  positions or zeros) and asserts anchor deltas only as consistency (§3.8).
- Prominence: the fixture builds the projection with `GraphProjection.projected(...)` and the
  two real outgoing edges `Regularity → free Theorem` and `Regularity → pinned Theorem`
  (`arrowAtSecond`), reproducing reach `2` for Regularity and `0` for the others; the test
  asserts the resulting `NodeGeometry.radius()` values (`9.6` and `8.0`) before correction
  (design §6.1). If the re-run prints different real counts, the fixture embeds the printed
  counts and the radius assertion follows `8.0 · NodeProminence.of(count).scale()`.
- Metrics: `new AwtGeometryTextMetrics(new Font("Dialog", Font.PLAIN, 12),
  new FontRenderContext(null, true, true))` (no hull in this fixture is empty, so hull geometry
  is font-independent; the metrics are still the production ones).

**Assertions, in order:**

1. **Pre-correction violation.** Compute hulls with `GraphGeometryEngine` on the raw positions;
   assert `siblingOverlap(Axioms, Definitions) == true`, the inclusive-containment checks are
   both false, and `minimumSeparatingTranslation(Axioms, Definitions)` equals the captured
   full-precision vector (recorded as `(-13.5…, 0.0)`; `t.y() == 0.0`, `t.x() < 0`). The
   fixture has exactly four projected enclosures (suppressed root `ID_435635462`, `ZFC`,
   `Axioms`, `Basic Definitions`): one sibling pair (Axioms ↔ Basic Definitions) and, under
   R5, two enforced ancestor pairs — `ZFC ↔ Axioms` and `ZFC ↔ Definitions` (the suppressed
   root's ZFC link is out of scope). Assert exactly those two ancestor pairs are contained
   (I2) before correction directly, using the same inclusive-containment predicate as §3.2
   (every vertex of the child hull inside the parent hull via `hull(P).contains`); the shared
   `assertBoundaryInvariants` helper (§7.1) is scoped to `boundaryVerified()` frames and is
   used only for the post-correction assertions.
2. **Correction.** `apply(...)` returns `rounds() == 1`, `hullViolationsDetected() >= 1`,
   `hullResidualViolations() == 0`, `boundaryVerified() == true`, `conflicts().isEmpty()`,
   `residualHullPairs().isEmpty()`.
3. **Pins (I3).** Both pinned nodes are bit-identical to their pin records.
4. **Separation (I1).** Recompute hulls on the corrected positions with the same metrics;
   `strictlyOverlapping` is false for Axioms ↔ Definitions, and every non-nested enforced pair
   satisfies the §3.2 predicate with either no overlap or zero MST. The corrected facing
   support is the recorded contact (the Axioms side moved by `−t`; the pinned Theorem side did
   not move), and `appliedDisplacements()` contains the Regularity node field but no pinned
   node field.
5. **Ancestor containment (I2).** Every enforced child vertex is inside its parent hull after
   correction.
6. **Anchor consistency.** `Axioms`, `ZFC` and root anchors shifted by the mean of the moved
   nodes in their subtrees (here exactly the Regularity delta); no anchor was moved for the
   Basic Definitions subtree.
7. **Determinism.** A second `apply` on the same inputs returns equal positions, `conflicts()`,
   `rounds()` and diagnostics numbers.

### 6.2 Predicate completeness (`BoundarySeparationCorrectionShould`)

- **Sibling crossing**: §6.1 covers the real crossing; the synthetic named `SIBLING_CROSSING`
  case uses two sibling hulls overlapping strictly with an edge axis; assert kind and that the
  pair is removed.
- **Sibling containment**: one non-nested hull wholly inside the other; the fixture places two
  same-map siblings with overlapping node clusters such that one computed hull contains the
  other. Assert kind `SIBLING_CONTAINMENT`, non-zero MST and resolution.
- **Coincident/equal hulls**: two identical hulls at the same coordinates (e.g., two enclosures
  whose direct nodes overlap exactly); `siblingOverlap` false, both containment checks true,
  violation kind `SIBLING_CONTAINMENT`, MST non-zero, resolved.
- **Bare contact**: two hulls whose facing edges touch exactly; `strictlyOverlapping` false;
  no violation, no residual, no conflict; positions unchanged; `rounds == 0`.
- **Sub-epsilon shallow crossing**: two hulls overlapping by less than the MST epsilon; the
  exact predicate sees them as strictly overlapping but `minimumSeparatingTranslation` returns
  `(0.0, 0.0)`; assert "contact by tolerance": no violation, no residual, no conflict, no
  `BoundarySeparationException`. (Construct at the geometry level through a projection whose
  overlap is ~1e-12; if the fixture granularity cannot produce it through computed hulls, the
  split is covered by a hand-built `HullGeometry` pair asserted directly against
  `HullIntersection.siblingOverlap` (true) and `minimumSeparatingTranslation` (`(0.0, 0.0)`).)
- **MST-call failure**: call `guardedMinimumSeparatingTranslation` with a hand-built extreme
  hull pair at coordinates around `±1.7e308` whose chosen-axis overlap is a sizeable fraction
  of their extent, so the unscaled magnitude `Math.scalb(magnitude, -scaleExponent)`
  (`HullIntersection.java:122-124`) overflows and `LayoutPoint.of` rejects the non-finite
  product (`LayoutPoint.java:9-12`); assert `BoundarySeparationException` with an
  `IllegalArgumentException` cause. Extreme coordinates with only a small overlap do **not**
  throw — they produce a finite, astronomically large translation. Extents below `2^-523` are
  **not** a throw fixture: the `scalb`-derived epsilon becomes `+Infinity`, so the
  `overlap <= epsilon` branch returns `(0.0, 0.0)` at `HullIntersection.java:105-107` before
  any `LayoutPoint.of` sees a non-finite product — that is the §3.2 zero-MST contact case.
  Additionally, a fake `LayoutEngine` that throws `BoundarySeparationException` from
  `apply` is driven through `LayoutWorker` to assert the fail-closed state transition (§4.2):
  failed frame, retained diagnostics, and recovery via `restart()` (R19/R20).
- **Ancestor escape**: `ancestorEscapePairKeys(projection, hulls)` with a hand-built projection
  and a hull map whose child hull has one vertex outside the parent hull returns exactly that
  child/parent pair key; a `BoundaryConflict` with `Kind.ANCESTOR_ESCAPE` /
  `Reason.STRUCTURAL_ESCAPE` is constructible and reports the expected pair; no displacement is
  applied for ancestor findings (a `rounds == 0` public-API call on a clean projection proves
  the absence path). Across every fixture, no `ANCESTOR_ESCAPE` residual may occur (I2
  structural guarantee).

### 6.3 Per-candidate capability (`BoundarySeparationCorrectionShould`)

Common fixture recipe (R7/R8): one map, a root enclosure with two same-map sibling children
`A` (id `a`) and `B` (id `b`), one or two direct nodes each, all prominence scale `1.0`
(radius `8.0`), so a node at `(x, y)` contributes `x + 24` to the `+x` support. The canonical
order `a < b` makes `A` the first hull and `t` its translation applied to `B`; placing `B` to
the right gives `t = (+m, 0)`, `u_A = (+1,0)`, `u_B = (−1,0)`. Pinned nodes are recorded with
pin coordinates equal to their positions and a `y` offset (`30`) large enough that the
node-separation pass never moves an unpinned node toward a pin. The tests compute hulls with
the production engine and assert `m == |t|` and the depths before running `apply`.

Concrete cases (all coordinates `(x, y)`; `|t| = 10`):

| Case | `A` nodes | `B` nodes | expected candidate | expected result |
|---|---|---|---|---|
| attempt-5 | unpinned `(0, 0)`; pinned `(−6, 30)` | unpinned `(38, 0)`; pinned `(44, 30)` | 1 (both-sides half, `a = 5`) | `A`'s unpinned node `−5x`, `B`'s unpinned node `+5x`, both pins unchanged, `rounds == 1`, residual `0` |
| mixed complementary | unpinned `(0, 0)`; pinned `(−3, 30)` | unpinned `(38, 0)`; pinned `(47, 30)` | 4 (`3 + 7`) | `A`'s unpinned node `−3x` (tie with its pin), `B`'s unpinned node `+7x`, both pins unchanged, residual `0` |
| pinned max contributor | pinned `(0, 30)` | unpinned `(38, 0)` | 3 (second full) | `B`'s node `+10x`, `A` unchanged, both pins unchanged, residual `0` |
| no valid candidate | pinned `(0, 30)` | pinned `(38, 30)` | 6 (conflict) | positions unchanged, `rounds == 0`, residual `1`, one `IMMOVABLE_SIDES` conflict whose pair key matches `residualHullPairs` |
| tie | pinned `(−3, 30)`, unpinned `(0, 0)` | unpinned `(38, 0)`; pinned `(47, 30)` | 4 (`3 + 7`) | the tied pinned node (depth `3 == a_A`) is in the band but absent from `appliedDisplacements`; only unpinned keys appear |
| applied-band selection | any candidate 4/5 fixture | | | filter `appliedDisplacements` keys by the fixture's pinned node keys: none present |

Also required:

- **Nested child-hull cap recursion**: `A` has no direct nodes but a child enclosure `A1`
  whose single unpinned node defines `A`'s facing edge; a candidate move of `A` recurses into
  `A1` and moves that node; `appliedDisplacements` contains it and `rounds == 1`. A second
  variant places a pinned node inside `A1` at local depth `3` (recursive capability) and pins
  `B` at depth `9` as in the mixed complementary fixture; candidates 1 and 2 are invalid on
  `A` and candidate 3 is invalid on `B`, so the complementary candidate 4 (`d_A = 3`,
  `a_B = 7`) is selected.
- **Empty-enclosure anchors**: `B` is an empty enclosure with a label (label metrics via the
  production `AwtGeometryTextMetrics`); its hull is the label octagon including
  `BOUNDARY_PADDING`; the overlap resolves by an `a:<canonical hull key>` entry in
  `appliedDisplacements` and the anchor moves by the candidate vector.
- **Both-side halves preserve internal separation**: two unpinned nodes on one side 10 apart
  both receive the same vector (rigid cap-set translation).

### 6.4 Pin policy

- `pinnedNodesKeepTheirExactCoordinates`-style assertion across every candidate path and both
  policies: every active pinned node's corrected coordinate equals its pin record exactly
  (`isEqualTo` on `LayoutPoint`), including the tie path (a pinned contributor at depth equal
  to the applied magnitude).
- A map containing a pin anywhere is rigid (cross-map), and the pin of the rigid map is
  unchanged; a dormant pin does not create rigidity and does not appear in
  `blockingPins`.

### 6.5 Conflict identity, bijection and reasons

- On the `IMMOVABLE_SIDES` fixture: `residualHullPairs()` has exactly the conflict pair keys in
  both directions (`containsExactlyElementsOf` both ways, no duplicates), `boundaryCovered()`
  true, `boundaryVerified()` false.
- Round-limit path (R8): the three-hull sibling fixture — three same-map sibling hulls `A`,
  `B`, `C` under one common parent, arranged in a line on one axis, with all-unpinned max
  contributors at depth `0` (e.g. `A` at `x=0`, `B` at `x=38`, `C` at `x=76`, `|t|=10` per
  adjacent pair; round 1 moves `A −5`, `B +5−5 = 0`, `C +5`, so both adjacent pairs
  retain overlap `5`, and candidate 1 stays valid because there are no pins). The fixture is
  exercised **only through the package-private bounded seam with bound
  `1`**: assert residual `2`, `hullResidualViolations() == 2`, exactly two conflicts with
  `Reason.ROUND_LIMIT`, the same set-equality bijection in both directions, and
  `boundaryCovered() == true` while `boundaryVerified() == false`. This exercises the reason
  recomputation and the `boundExhausted` path without touching production constants; this
  three-hull sibling line converges only geometrically and is not used to assert the production
  bound.
  The production `rounds <= 2` gate is measured on the §6.10 settle-sequence corpus, not on
  this deliberately seam-bounded fixture.
- Blocking pins: same-map fixtures list exactly the active pins in either hull's subtree;
  cross-map fixtures list all active pins of both maps; `blockingPins()` order is the R10
  sort; dormant pins never appear.
- A `BoundaryConflict` constructor test asserts canonical normalization (passing the pair in
  reverse order yields the same `pairKey`, `firstHull`/`secondHull`), derived
  `firstMap()`/`secondMap()` (equal for same-map), and the pin sort.

### 6.6 Suppressed roots, empty enclosures, fast path

- **Suppressed roots**: two overlapping enclosures where one is `SUPPRESSED`; assert
  `rounds == 0`, positions unchanged except any node-separation no-op, no conflicts, no
  residual; the suppressed enclosure is not moved even when the enforced one is.
- **Empty enclosure**: §6.3's anchor case.
- **Fast path**: a projection with no strictly overlapping enforced pair returns positions
  identical to the input (after a no-op node-separation pass on non-overlapping nodes) with
  `rounds == 0`, `hullViolationsDetected == 0`, `hullResidualViolations == 0`.

### 6.7 Cross-map rigidity (`BoundarySeparationCorrectionShould`)

- Two maps, one map-root enclosure each, nodes at `(0,0)` and `(38,0)` (`|t| = 10`), no pins:
  first map's nodes and anchors move `−5x`, second map's `+5x`; assert every node and anchor of
  a map shares the same delta (whole-map rigid translation) and `rounds == 1`.
- Pin in the first map: second map moves `+10x`, first map unchanged, pin exact.
- Pins in both maps: one `IMMOVABLE_SIDES` conflict with both pin identities, positions
  unchanged, `rounds == 0`.
- Generalized non-root cross-map pair: two maps each with a non-root enclosure that overlaps;
  assert the whole free map is translated, not just the overlapping enclosure's nodes.
- Node-based re-derivation of the old
  `MapTierCorrectionShould.accumulateThreeMapDeltasFromTheOriginalHullSnapshot`: three maps,
  one root enclosure and one direct node each, placed pairwise-overlapping and non-collinearly
  (e.g. at the vertices of an equilateral triangle with pairwise distance `38`), so all three
  cross-map pairs are planned in the same round and every map receives deltas from two
  cross-map pairs. The expected per-node and per-anchor delta is the component-wise sum of
  that map's two pair deltas computed against the round's first-detection hull snapshot (the
  old `firstDelta`/`secondDelta`/`thirdDelta` construction), never against post-first-pair
  geometry. Assert exact positions, `rounds == 1` (the accumulated deltas fully separate all
  three pairs), and unchanged pins where present.
- Node-based re-derivation of the old
  `MapTierCorrectionShould.reportOrderedConflictsForEveryRigidMapPair`: three maps, one root
  enclosure and one active pin each, all pairwise overlapping; assert exactly 3
  `IMMOVABLE_SIDES` conflicts in canonical pair order with the corresponding two-pin
  `blockingPins()` sets, positions unchanged, `rounds == 0`, and the I6 bijection.

### 6.8 Determinism and canonical keys

- Repeated `apply` runs (fresh components) on the full fixture set produce equal positions,
  `conflicts()` (pair keys, kinds, reasons, pins), `rounds()` and every diagnostics number.
- `CanonicalLayoutKeys` collision tests: persisted endpoints whose ids contain `%`, `|`, `:`
  and `,`; transient endpoints with multi-component paths; a persisted vs transient pair whose
  naive concatenations would collide; assert distinct keys, hull-key order independence
  (`endpointKeys` permutations map to one key), canonical pair order symmetry, and the exact
  escape outputs (`%25`, `%7C`, `%3A`, `%2C`).
- `BoundaryConflict.pairKey()` equals `CanonicalLayoutKeys.pair(firstHull, secondHull)`;
  `diagnostics.residualHullPairs()` entries equal conflict pair keys exactly.

### 6.9 Pipeline adversarial fixture with red-before/green-after (`BoundarySeparationShould`)

A real `GraphStreamLayoutFactory` / `LayoutWorker` fixture, deterministic and self-contained
(no Dropbox paths), constructed so that:

1. a direct engine settle (`GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())`,
   `apply` + 1500 `step()`s) produces raw positions whose two same-map sibling hulls are
   strictly overlapping, with at least one facing edge defined by an **unpinned** node, and
2. the pair admits a specific valid candidate: on **both** moving sides every pinned
   contributor in the half-band (`band = |t|/2`) closure has depth `>= |t|/2` (the half-band
   is pin-free below `|t|/2`), on each moving side at least one pinned contributor has depth strictly between
   `|t|/2` and `|t|` (the attempt-5 depth pattern), and at least one unpinned contributor is
   in the half-band. Candidate 1 resolves the pair: it is valid first, and the pinned depths
   below `|t|` invalidate the full-magnitude candidates 2 and 3 (candidate 2 on the first
   side, candidate 3 on the second side). An unpinned facing edge alone is not sufficient (a
   deeper pin can invalidate the band; design §6.2).

Test structure:

- **Part 1 (raw evidence).** Run the direct engine settle; compute hulls with the production
  engine and metrics; assert `siblingOverlap == true` for the pair and assert the depth
  pattern from the raw geometry (on **both** moving sides every pinned contributor in the
  half-band closure has depth `>= |t|/2`, so candidate 1 is valid, and the pinned contributor
  depth `δ` satisfies `|t|/2 < δ < |t|`; because `δ < |t|`, candidates 2 and 3 are invalid on
  the side carrying the pin, so candidate 1 is the selected distribution). This is the
  falsifiability guard for the per-candidate capability.
- **Part 2 (published frame).** Submit the identical request through `LayoutWorker`, step to
  idle, and assert `rounds() <= 2` on every accepted frame and `hullViolationsDetected() >= 1`
  on at least one accepted frame (the raw crossing was observed and repaired; the crossing may
  emerge during settling rather than on the first accepted frame). For every published
  non-failed frame that is `boundaryVerified()`, assert I1/I2/I3 via the shared
  `assertBoundaryInvariants` helper (§7.1); `assertBoundaryInvariants` is scoped to verified
  frames, so a covered frame (none expected in this fixture) asserts the I6 bijection from
  `residualHullPairs()` and `conflicts()` instead of I1. On the pre-fix commit, part 2 fails
  because same-map hulls are never corrected; the implementation step records that red run
  before the fix and the green run after (design §6.2).

### 6.10 Settle-sequence measurement test (`BoundarySeparationShould`)

Drive `LayoutWorker` from `submit` to idle over the frozen pipeline fixture, recording §4.6
diagnostics per frame (step index, `rounds`, `hullViolationsDetected`,
`hullResidualViolations`, `residualHullPairs`, `displacementRms/Max`, `deltaRms/Max`,
`worstMapDisplacement`, idle measurement). Assert:

- every frame satisfies `boundaryVerified() || boundaryCovered()`; on the normal fixture every
  frame has `hullResidualViolations() == 0` and the final idle frame has zero residual;
- `rounds() <= 2` on every frame (design §7.1); `ROUND_LIMIT` never appears on this fixture;
- the sequence reaches idle under `PerceptualIdlePolicy.spikeDefaults()` within the existing
  step budget; after idle is reported, `idle().rms() <= 0.0505` and `idle().max() <= 0.10`
  (the slack pinned by `BoundarySeparationShould.pinnedFixtureSettlesWithPinPositionUnchanged`),
  and `deltaRms() <= 0.05`, `deltaMax() <= 0.10` after the 8-frame idle window (design §7.3);
  a stable recurring correction has `deltaRms() == 0.0` and `deltaMax() == 0.0`;
- no `BoundarySeparationException` occurs; `worstMapDisplacement()` is recorded for the
  compactness metric (§7.2 item 5).

The existing `BoundarySeparationShould` numeric equilibrium expectations and the
`GraphStreamLayoutFactory` force fixtures keep their values (design §6.3); this test adds the
boundary diagnostics assertions without re-baselining them.

### 6.11 Performance measurements

- `GraphWorkspacePerformanceDiagnostic` records `SEPARATION`, `HULL`, `CORRECTION` from the
  component timings with full warmup/measured counts for every scenario, and additionally
  reads the new diagnostics fields (§5.6).
- The strict gate `PerformanceMeasurements.STRICT_FULL_WORKER_NANOS = 100_000_000L` must pass
  on `REFERENCE_2000_5000` (2000 nodes / 1200 enclosures). The measured correction overhead
  (`CORRECTION` p50/p95/p99/max and its plan/apply split) is recorded in the plan evidence.
- Candidate-computation characterization (design §6.4/§10.6 M-3): at most four distinct
  hull/band cap-set computations per pair per round — two half-band sets for candidate 1 and
  two full-band sets for candidates 2–3, reused by the complementary splits; a per-hull
  full-band cache filtered by band reduces this to two full-band runs plus filtering. The five
  candidate distributions are evaluated in the deterministic preference order of §3.7.
- The default implementation recomputes all hulls each detection pass through the component's
  private `GraphGeometryEngine`; if the strict gate is exceeded, the fallback is affected-only
  hull recomputation with an explicit interface (recompute moved hulls and their ancestors),
  and the plan must state which interface was chosen and show the re-measurement. The gate
  itself is not relaxed (R15).
- The `TWO_PINNED_MAPS` and `ONE_PINNED_MAP` expectations are re-derived from a measured run
  as golden values (R17); the run also confirms that the pre-correction pinned-root overlap is
  still observed (`assertPinnedRootOverlap`).

---

## 7. Acceptance criteria and measurement gates

Each item below is objectively checkable; the design's §8 acceptance criteria and §7
measurement/escalation criteria are restated, not extended.

### 7.1 Acceptance criteria (design §8)

1. **Reproduced case.** The §6.1 fixture publishes the corrected frame with zero non-ancestor
   violations in one displacement round and both pins at their stored coordinates; the Axioms
   and Basic Definitions hulls no longer cross. Manual acceptance: after a full build, open
   `math.fpg` in `BIN/freeplane.sh`, inspect the `ZFC → Basic Definitions and Theorems` region,
   and confirm the sibling hulls no longer cross and the layout reaches idle.
2. **I6 on every published frame.** `boundaryVerified() || boundaryCovered()` holds on every
   non-failed published frame; `residualHullPairs()` and `conflicts()` are exact bijections;
   `verified()` and the node residual keep their existing meaning; a coverage failure fails the
   frame and the engine closed (failed frame copies the retained diagnostics; `restart()`
   recovers).
3. **Ancestor containment.** I2 verified across the existing suite (`directNodesLieInside…`,
   the reference fixtures) and the new fixtures via the shared `assertBoundaryInvariants`
   helper, which recomputes all enforced pairs with the §3.2 predicate and tolerance and asserts
   I1, I2, I3 (pins) and I4 (frame node residual), plus the I6 bijection both ways. The helper
   is scoped to `boundaryVerified()` frames; fixtures whose residual pairs are legitimately
   conflict-covered assert the I6 bijection and I3 instead of I1.
4. **Test suite.** `gradle :freeplane_plugin_graph:test` passes, including
   `BoundarySeparationCorrectionShould`, the migrated
   `LayoutWorkerShould`/`LayoutSettleLoopShould`/`GraphUpdateCoordinatorShould`/
   `GraphWorkspaceCommandAcceptanceShould`/`GraphWorkspacePerformanceDiagnostic` expectations,
   the `publicLayoutTypes()` addition, and the determinism tests.
5. **Performance and stability.** The strict `FULL_WORKER` gate passes on
   `REFERENCE_2000_5000` with recorded correction overhead; the settle sequence reaches idle
   with stable correction deltas (§7.2); the cross-map compactness metric is recorded.
6. **Manual acceptance on `math.fpg`** as in item 1.
7. **One component.** `MapTierCorrection` and its cross-map-only special casing no longer
   exist; a single `BoundarySeparationCorrection` handles cross-map rigid map translation and
   same-map facing-edge displacement; `LayoutConflict` no longer exists.

### 7.2 Measurement and escalation gates (design §7)

All quantities are read from the frame stream through `BoundarySeparationDiagnostics` (§4.6):

1. **Rounds.** `rounds <= 2` on every corpus frame, except frames covered by `ROUND_LIMIT`
   conflicts, which must not occur in the corpus. The reproduced case is `rounds == 1`.
2. **Terminal cleanliness.** On the idle terminal frame of every settle sequence,
   `hullResidualViolations == 0`, or every residual pair is conflict-covered with reason
   `IMMOVABLE_SIDES` in the deliberate pin fixtures. An `ANCESTOR_ESCAPE`/`STRUCTURAL_ESCAPE`
   residual is a hard verification defect against I2 (it means `computeHull`'s containment
   guarantee was violated), not a force-clearance escalation trigger. Zero-MST overlaps are
   not violations and do not appear. `hullViolationsDetected > 0` on the first detection pass
   of raw-equilibrium frames is expected and is not an escalation trigger by itself.
3. **Correction stability, not correction size.** The settle sequence reaches idle under
   `PerceptualIdlePolicy` within the existing step budgets; the published-frame idle
   measurement satisfies `rms <= 0.0505` and `max <= 0.10`, and `deltaRms`/`deltaMax` do not
   exceed `SPIKE_RMS = 0.05` / `SPIKE_MAX = 0.10` for longer than the idle policy's
   8-frame consecutive window. A stable recurring correction has zero delta and is acceptable.
4. **Failure-free corpus.** No `ROUND_LIMIT` conflict occurs on the reproduced case or any
   existing fixture; `verified || covered` never fails; `BoundarySeparationException` never
   occurs on the corpus.
5. **Cross-map compactness (recorded, not gated).** The §2.3 `worstMapDisplacement` value —
   the maximum applied key vector per map over the frame's displacement field, including
   same-map components — is recorded from the settle sequences, so overshoot from generalizing
   whole-map MST deltas to all cross-map pairs is visible.

**Escalation.** Escalate to targeted force-side clearance only if any of 1–4 fails. The
follow-up adds `HULL_CLEARANCE` only to the non-ancestor boundary repulsion effective radius
and re-runs the same measurements. Until then, no force-model change is made. Measurements and
the decision are recorded in the plan evidence.

---

## 8. Open decisions (all resolved — no unresolved blockers)

The design (revision 8, zero blockers) leaves the following implementation choices open. Each
is resolved below in a way consistent with the design; none changes scope, public behavior
beyond the design, the invariants, or §7.

- **R1 — Result/timing type names and placement.** `BoundarySeparationResult`,
  `BoundarySeparationDiagnostics`, `BoundarySeparationTimings`, `BoundaryConflict` and
  `BoundarySeparationException` are top-level public types in
  `org.freeplane.plugin.graph.layout`; `Kind`/`Reason` are nested enums of `BoundaryConflict`;
  `BoundarySeparationResult`'s constructor is package-private. All are added to
  `publicLayoutTypes()` per design §4.7.
- **R2 — `LayoutFrame` API shape.** A `BoundarySeparationDiagnostics` field plus
  `boundaryDiagnostics()` accessor replaces the conflict-list field; `conflicts()` remains as a
  delegating accessor; `withDiagnostics(raw, diagnostics, idle)` replaces the list overload.
  `LayoutFrame.of(...)` still builds empty diagnostics, preserving `verified()`/residual
  semantics and minimizing call-site churn (design §4.6/§4.7).
- **R3 — Violation counting granularity.** Violations are pairs, deduplicated by canonical pair
  key; `hullViolationsDetected`/`hullResidualViolations` count violating pairs; an ancestor
  escape with multiple escaping child vertices is one pair. This is the only reading under
  which "exactly one conflict per residual pair" is well defined.
- **R4 — Ancestor ordering.** Ancestor findings have penetration `0.0` and sort after all
  MST-bearing findings, then by ascending canonical pair key; this keeps the design's
  "descending penetration, then ascending key" order total and deterministic.
- **R5 — Ancestor pair scope.** A child/parent pair is verified only when both enclosures are
  enforced (non-suppressed and present in the hull map); a suppressed parent or child contributes
  no `ANCESTOR_ESCAPE`. This matches the design's scoping of the invariants to non-suppressed
  enclosures ("enforced child with parent P").
- **R6 — Capability is recursive with local depths.** `valid` is evaluated level-by-level
  through the band-`a` cap recursion; each pinned contributor's depth is local to its enclosing
  hull's support at that level. This is the reading under which each recursion level's edge
  provably advances by `a`; it is conservative at the top level only when a non-edge-determining
  child caps, which the design's conservative-validity wording accepts.
- **R7 — Complementary-split pin depths.** `d_A`/`d_B` is the minimum local depth in
  `(0, m)` among pinned direct nodes in the recursive full-band (`band = m`) closure of the
  side; no such pin makes the candidate inapplicable. This realizes the design's "smallest
  pinned depth with `0 < d_A < |t|`" and the mixed `3 + 7` example.
- **R8 — Falsifiability seams.** Three package-private seams are added (no public surface
  change): the bounded constructor `BoundarySeparationCorrection(int maxDisplacementRounds)`
  (production default `4`, with `maxDisplacementRounds >= 1` enforced),
  `guardedMinimumSeparatingTranslation` (the single MST guard), and
  `ancestorEscapePairKeys` (pure ancestor verification over a supplied hull map). They make the
  `ROUND_LIMIT` path, the MST-failure contract and the otherwise unreachable ancestor-escape
  branch unit-testable. The `STRUCTURAL_ESCAPE` reason itself is additionally covered by a
  direct `BoundaryConflict` construction test (the branch is structurally unreachable through
  `GraphGeometryEngine` hulls, as the design states).
- **R9 — Support epsilon formula.** Per cap-band comparison
  `ε = SUPPORT_COMPARISON_EPSILON · max(|contribution|, |S(H,u)|, |S(H,u) − band|)`; exact
  double comparison for the depth-strict tie rule (`depth < a` invalid, `depth == a` valid).
  Ties are constructed exactly in the fixtures.
- **R10 — Blocking-pin order.** Ascending `(mapReferenceId().value().toString(),
  source().nodeId().value())` string order (the design's deterministic `(mapId, nodeId)`;
  neither `MapReferenceId` nor `PersistedNodeId` is `Comparable`).
- **R11 — Cap support formula.** `S(H,u)` is the design's formula (direct node contributions,
  child polygon supports, one `HULL_CLEARANCE`, anchor+label for empty hulls), which is exactly
  `computeHull`'s support on the 8 constant normals. The frozen-fixture test asserts the
  formula equals the computed polygon support along the MST axis on the real case.
- **R12 — Displacement field content and order.** `appliedDisplacements()` contains only
  correction displacement vectors (never node-separation nudges), accumulated over the frame's
  rounds, zero vectors omitted, ascending lexicographic canonical-key order; this is the field
  the worker diffs for `deltaRms`/`deltaMax`.
- **R13 — Timing attribution.** `plan` covers detection, candidate evaluation and conflict
  construction; `hull` covers every `computeHulls` (loop and terminal); `separation` covers
  every node-separation pass; `apply` covers every displacement application. Together they sum
  to the whole correction, and `CORRECTION = plan + apply` per design §4.7.
- **R14 — Compactness metric exposure.** `worstMapDisplacement()` is a derived
  `BoundarySeparationDiagnostics` accessor: the maximum applied key vector per map over the
  frame's displacement field, including same-map node/anchor components, maximum across maps.
  This §2.3 value is the number recorded for §7.2 item 5 (it upper-bounds the rigid cross-map
  map-delta component), satisfying §7's "all quantities are exposed by the diagnostics"
  without adding a stored field beyond §4.6's list.
- **R15 — Performance default/fallback.** The default implementation recomputes all hulls per
  detection pass through the component's private `GraphGeometryEngine`; affected-only hull
  recomputation is a contingency to be implemented only if the measured strict `FULL_WORKER`
  gate (100 ms) is exceeded, with the plan recording the chosen interface and measurement. The
  gate is unchanged.
- **R16 — Pipeline fixture home and red protocol.** The adversarial-pin pipeline fixture and
  the settle-sequence test live in `BoundarySeparationShould` (which already drives
  `GraphStreamLayoutFactory`/`LayoutWorker`); the red run is executed on the pre-fix commit
  before implementation and recorded in the plan evidence, then the same test is run green
  after the change.
- **R17 — Performance scenario conflict expectations.** `TWO_PINNED_MAPS` and
  `ONE_PINNED_MAP` expectations are re-derived from a measured golden run under the generalized
  enforcement (count and pin identities of every conflict, including same-map conflicts), rather
  than hard-coded from the old roots-only behavior; every cross-map conflict between the two
  pinned maps must still list both pin identities, while a same-map conflict lists only that
  map's subtree pins. `ONE_PINNED_MAP` keeps asserting no rigid (two-pinned) conflict.
- **R18 — Conflict canonicalization and derived maps.** The `BoundaryConflict` constructor
  normalizes the pair to canonical key order; `firstMap()`/`secondMap()` are derived from the
  hull keys and may be equal; blocking pins are copied/sorted and must be active.
- **R19 — Displacement-field reset points.** The worker's previous-field is reset at worker
  construction, in `LayoutWorker.restart()`, and implicitly by
  `LayoutSettleLoop.WorkerStepper.reset()` (fresh worker). Failed frames do not clear it, so
  the next accepted frame is still diffed against the last valid frame's field.
- **R20 — Failed-frame diagnostics.** `LayoutWorker.failedFrame` and
  `LayoutSettleLoop.failedFrame` copy the retained `BoundarySeparationDiagnostics` unchanged
  (conflicts, residual pairs, counts, displacement field, deltas), changing only `stepIndex`
  and `failed`; `LayoutSettleLoop`'s retained-frame path keeps its positions/residual/idle
  handling.

No unresolved blocker, contradiction or deferred decision remains. The only **[N]** items are
measurement captures that require running the code: the frozen fixture's full-precision doubles
and prominence map (§6.1) and the performance golden conflict counts (§5.6/R17). Both have a
defined capture procedure and defined assertion targets; neither gates specification
implementation.

---

## Appendix A — Verified code facts

All line references and values cited above were verified against this worktree
(`integration` at `5806a338bd`).

- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/MapTierCorrection.java`
  (`apply`, `mapRoots`, `rigidMaps`, `blockingPins`, `applyDeltas`) is the policy being replaced.
- `LayoutWorker.java:49` (correction field), `:278-304` (`accept`), `:254`/`:273`
  (`failedEngine = true`), `:355-364` (`failedFrame`), `:339-353` (`runRestart`).
- `LayoutSettleLoop.java:760` (`retained.conflicts()` consumption in `failedFrame`),
  `:1084-1128` (`WorkerStepper`, worker replacement on `reset()`).
- `LayoutFrame.java:15-20` (fields), `:41-48` (`of`), `:52` (`withDiagnostics`).
- `LayoutConflict.java:20-22` (distinct-map rejection).
- `GraphStreamBoundaryShould.java:84-88` (`publicLayoutTypes`).
- `HullIntersection.java:10-11` (`EPSILON = 1e-9`, `TARGET_EXPONENT = 500`), `:86-128`
  (`minimumSeparatingTranslation`, zero return at `:105-107` and unscaling at `:122-124`),
  `:16-26` (`siblingOverlap`).
- `GraphGeometryEngine.java:22-37` (`BASE_RADIUS`, `HULL_CLEARANCE`, `BOUNDARY_PADDING`,
  normals), `:88-100` (node radius from prominence), `:159-221` (`computeHull` support formula
  and empty-hull label octagon).
- `NodeSeparationProjection.java` (`MIN_GAP = 6.0`, `MAX_PASSES = 64`, pinned exclusion,
  anchors copied through).
- `PerceptualIdlePolicy.java:10-12` (`SPIKE_CONSECUTIVE = 8`, `SPIKE_RMS = 0.05`,
  `SPIKE_MAX = 0.10`), `BoundarySeparationShould.java` pinned-slack assertions
  (0.0505 / 0.10).
- `PerformanceMeasurements.java:17-19` (`STRICT_FULL_WORKER_NANOS = 100_000_000L` and strict
  neighbours), `:27-41` (`Stage` enum).
- `GraphWorkspacePerformanceDiagnostic.java:100` (correction field), `:341-400` (direct probe),
  `:531-552` (pin-conflict validation), `:554-579` (`assertPinnedRootOverlap`).
- `SourceNodeKey.java` (persisted/transient forms, structural path),
  `PersistedNodeId`/`NodeReference`/`MapReferenceId` string forms; `EnclosureHullKey.endpointKeys()`.
- `ProjectedEnclosure.validateParent` (same-map parent), `BoundaryTier` values.

## Appendix B — Real-case fixture data (probe capture, to be re-run for full precision)

From `/tmp/pm-probe/real-run.log` (`RealPipelineOverlapProbe`):

```
Workspace 1df8f60e-2643-4661-b126-ab13064f4091; active map 8055d8c8-d71e-40f3-a8ed-5bf2502f2cad
Pins: ID_130337169 @ (-24.832420395427746, -34.92046985440441)
      ID_1901523076 @ (-209.31397564145126, 9.820904009249132)
Nodes: ID_1133378501 "Fundation / Regularity" (-173.3, 1.8)
       ID_822182441  "Replacement Scheme"     (248.6, -59.3)
       ID_130337169  "Axiom of Choice"        (-24.8, -34.9)
       ID_1901523076 "Theorem"                (-209.3, 9.8)   pinned
       ID_1387156674 "Theorem"                (-241.8, 14.4)
Enclosures: ID_435635462 "Axiomatic Set Theory" SUPPRESSED root
            ID_1675547143 "ZFC" EMPHATIC
            ID_1912952190 "Axioms" SUBTLE [ID_1133378501, ID_822182441, ID_130337169]
            ID_978732953  "Basic Definitions and Theorems" SUBTLE [ID_1901523076, ID_1387156674]
Pre-correction pair: Axioms <-> Basic Definitions and Theorems
  siblingOverlap=true aInB=false bInA=false mst=(-13.5, 0.0)
Hull bounds: Axioms x [-198.9 .. 272.6], y [-83.3 .. 27.4]
             Basic Definitions x [-265.8 .. -185.3], y [-14.2 .. 38.4]
```

The implementation step re-runs the probe with full-precision printing (`Double.toString`/
`%.17g`) for the five node positions, the five prominence counts, both pin values (already
full precision), and the MST vector, and embeds those values in the frozen fixture. The probe
must not be part of the test suite and tests must not read the Dropbox paths (design §6.3).
