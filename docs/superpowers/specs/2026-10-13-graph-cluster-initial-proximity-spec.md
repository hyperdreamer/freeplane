# Graph Workspace — Initial Cluster Proximity (specification)

- Date: 2026-10-13
- PM run: `pm-run-20261013-111126-b33a54c6` (topic `graph-cluster-initial-proximity`)
- Base commit: `9b8259bcd1cb2c8d37121e63bcac0107386fed0d` (`plugin/graph-workspace`) plus the design commit
  `5610934d62` (design finalized); integration worktree HEAD checked out at
  `5610934d6252f261fa4f029f5b18622fa4e98f72`
- Approved design: `docs/superpowers/specs/2026-10-13-graph-cluster-initial-proximity-design.md` (v7,
  scope decision A: initialization only)
- Review reports: `runs/451bd60bc7bd6deb9869dd24d741edab7e4dafff987e562ab4cc006e7c0e8fcf/pm-run-20261013-111126-b33a54c6/reports/design-review-{1,2,3,5,6}.md`
- Prototype evidence (stored; numbers quoted below come from these files):
  `docs/superpowers/specs/mockups/2026-10-13-graph-cluster-initial-proximity/prototype/`
  (`README.md`, `overlay/`, `ContactProbe.java`, `ClusterProbe.java`, `probe-final.txt`,
  `probe-baseline.txt`; `probe-cap-sweep.txt`, `probe-containment-sweep.txt` and
  `superseded-v2/` are historical and are **not** cited for any load-bearing number)
- Status: implementation contract derived from design v7; resolves review findings listed in §9.2.

This spec is the single implementation and acceptance contract for the feature. Where the design prose
and the stored `probe-final.txt`/`probe-baseline.txt` disagree on a number, the stored probe value
govers (the known residual discrepancy is the `containment() = 1.0` drift lower bound `2.2` vs
`2.3`, §8.3.4; all other review-6 R6#2 values — E, H, B overlaps, drift maximum, timing, single
child — are already reconciled in the current design). Values that are measured but not stored in
those two artifacts are marked with their provenance and, where they are needed as assertions, marked
**to-be-measured** in the implementation.

---

## 1. Scope and goals

### 1.1 In scope

1. **Tight deterministic initialization at both levels.** Every workspace frame produced by
   `LayoutEngine.apply` on a fresh particle set seeds enclosures at the smallest footprints the layout
   can justify, separated by `SIBLING_GAP = 8.0` plus the packing margin, deterministically and exactly.
   Both levels are covered:
   - top level: separate maps / parentless enclosures;
   - nested: sibling sub-clusters inside one map.
2. Replace the label-based isotropic enclosure estimate with anchor-centred per-axis footprints
   (§3.1) and a deterministic rectangle packer (§3.2).
3. Replace the fixed hierarchy rest lengths `GROUP_SPACING`/`SUB_GROUP_SPACING` with the packed
   parent–child anchor distances (§3.5).
4. Make anchor separation offset-aware so the separation force is inert at packed positions (§3.6).
5. Raise `LayoutCalibration.containment()` to `1.0` as the calibrated companion of
   `NODE_SETTLE_MARGIN = 8.0` (§2.1, §4).
6. Honest reporting of the settled arrangement: the post-geometry `BoundarySeparationCorrection`
   remains the crossing safety net, and settled drift/containment is measured and recorded, not
   guaranteed (§8).

The scope decision (user, 2026-10-13) is **A — initialization only**: ship the tight deterministic
initialization, keep the existing force model, and report the settled behaviour honestly.

### 1.2 Non-goals (explicit scope boundary)

- **No holding mechanism.** Do not add a packing skeleton, contact springs, top-level packing links,
  frozen rest lengths, per-step displacement caps, or any new `ForceKind`. The prototype overlay's
  `rawPackingDisplacement`/`rawBoundaryDisplacement` vectors, its third `addBoundaryRepulsion`
  parameter, and the `PACKING_DISPLACEMENT_LIMIT`/`BOUNDARY_DISPLACEMENT_LIMIT` experiments are
  scaffolding for the superseded cap mechanism and must **not** be ported to production. Reviews 5 and 6
  measured that caps at 1.5/1.5 never converge after a reorder; the design deleted them.
- Do not reserve label space in the layout (user decision 3). Labels remain owned by the screen-space
  label placement pass (§8.5).
- Do not guarantee compactness after settling, containment inside the footprint, or bounded top-level
  drift. These are reported per fixture with measured values (§8.3); the 1000-step window drift reduces
  the value of any hold band, so no settled hold band is asserted.
- Do not alter native repulsion, pins, relationship semantics, the cross-map displacement budget, the
  `BoundarySeparationCorrection`, or label placement.
- Do not change `freeplane_api` or any other module; all changes live in `freeplane_plugin_graph`
  (§2.8).

### 1.3 Change surface (files)

Production:

- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutCalibration.java`
  (`CONTAINMENT` only)
- `.../layout/graphstream/GraphStreamLayoutEngine.java` (`BoundarySizes`, `Seeds`, `topology()`,
  `synchronize()`; `ClusterItem`/`ClusterPlacement`/`packItems` move out to the new class)
- `.../layout/graphstream/ClusterPacking.java` (**new**)
- `.../layout/graphstream/TypedSpringBox.java` (offset-aware separation; `configureAnchorExtents`)
- `.../layout/graphstream/TypedNodeParticle.java` (extent/offset accessors; delete `boundaryRadius()`)

Tests (new test package `org.freeplane.plugin.graph.layout.graphstream`, see §7.4):

- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/ClusterPackingShould.java` (**new**)
- `.../layout/graphstream/BoundaryFootprintShould.java` (**new**)
- `.../layout/graphstream/ClusterProximityShould.java` (**new**)

Tests (existing, modified):

- `.../layout/TypedForcesShould.java`
- `.../layout/BoundarySeparationShould.java`
- `.../layout/GraphStreamBoundaryShould.java`
- `.../layout/ReferenceRepulsionFixture.java`

No Gradle/build file changes are needed (Gradle compiles every source file under `src/test/java`).
No other production or test file may change (acceptance criterion 7 of the design).

---

## 2. Data structures and exact signatures

All types in this section are in package `org.freeplane.plugin.graph.layout.graphstream` unless stated
otherwise. They are package-private (no public modifier), so no GraphStream type enters a public
signature.

### 2.1 `LayoutCalibration` (`org.freeplane.plugin.graph.layout`)

Change exactly one constant:

```java
private static final double CONTAINMENT = 1.0;   // was 0.15
private static final double HIERARCHY   = 0.30;  // unchanged
private static final double SAME_MAP    = 1.0;   // unchanged
```

The public surface (`spikeDefaults()`, `containment()`, `hierarchy()`, `sameMap()`) is unchanged. No
`packing()` accessor is added (that idea from design review 2 was dropped in v7; hierarchy remains the
only anchor-spring stiffness).

### 2.2 `GraphStreamLayoutEngine.BoundarySizes.Footprint`

```java
static final class Footprint {
    final double width;
    final double height;
    final double anchorX;   // anchor position relative to the box centre: anchor = boxCentre + (anchorX, anchorY)
    final double anchorY;

    Footprint(double width, double height, double anchorX, double anchorY);
}
```

Immutable. Local-frame convention: placements are expressed in the **parent anchor frame**
(anchor at the origin). For an item placed at box centre `c`, the item's anchor sits at
`c + (item.anchorX, item.anchorY)`; equivalently `boxCentre = anchor − (anchorX, anchorY)`.

### 2.3 `ClusterPacking` and its item/placement payloads (new file)

```java
final class ClusterPacking {
    static final double CANDIDATE_EPSILON = 1e-9;    // dedupe + selection-key tolerance
    static final double CONTACT_GAP_TOLERANCE = 0.75; // contact metadata predicate (packing budget, metadata only)

    private ClusterPacking() {
    }

    static List<ClusterPlacement> packItems(List<ClusterItem> items, double gap);

    static final class ClusterItem {
        final EnclosureHullKey anchorKey; // null ⟺ the content item
        final double width;
        final double height;
        final double anchorX;
        final double anchorY;
        final int order;                  // projection-derived tie-break key

        ClusterItem(EnclosureHullKey anchorKey, double width, double height,
                    double anchorX, double anchorY, int order);
    }

    static final class ClusterPlacement {
        final ClusterItem item;
        final double x;   // packed box centre in the parent anchor frame
        final double y;
        final List<ClusterItem> contacts = new ArrayList<ClusterItem>(); // metadata only; no force reads this

        ClusterPlacement(ClusterItem item, double x, double y);
    }
}
```

Ownership: `ClusterPacking` is the **single placement authority** for seeding and footprints. It is
pure and stateless: `packItems` does not mutate `items` (it copies before sorting), allocates new
`ClusterPlacement`s, and is deterministic. `BoundarySizes` builds the `ClusterItem` list, calls
`packItems(items, SIBLING_GAP)`, and memoizes placements and footprints (recursion
footprint → placement → child footprint evaluates each enclosure once). `contacts` are read only by
tests; no force code path reads them.

### 2.4 `BoundarySizes` surface after the change

`BoundarySizes` remains a nested `static final class` of `GraphStreamLayoutEngine` (unchanged nesting).

```java
static final class BoundarySizes {
    static final double CHAR_WIDTH_UPPER_BOUND  = 16.0;  // empty-enclosure label box only
    static final double CHAR_HEIGHT_UPPER_BOUND = 24.0;  // empty-enclosure label box only
    static final double BOUNDARY_PADDING        =  8.0;  // empty-enclosure label box only
    static final double SIBLING_GAP             =  8.0;
    static final double NODE_SETTLE_MARGIN      =  8.0;  // new
    static final double FRAME_CLEARANCE         = 16.0;

    BoundarySizes(GraphProjection projection);

    double discRadius(ProjectedNodeKey key);
    double contentRingRadius(EnclosureHullKey key);

    Footprint footprint(EnclosureHullKey key);
    List<ClusterPacking.ClusterPlacement> placements(EnclosureHullKey key);
    ClusterPacking.ClusterPlacement placementOf(EnclosureHullKey parentKey, EnclosureHullKey childKey);
    List<ClusterPacking.ClusterPlacement> topLevelPlacements();
    ClusterPacking.ClusterPlacement topLevelPlacementOf(EnclosureHullKey key);

    ProjectedEnclosure enclosure(EnclosureHullKey key);
    List<ProjectedEnclosure> enclosures();

    private final Map<EnclosureHullKey, Footprint> footprints = new LinkedHashMap<>();
    private final Map<EnclosureHullKey, List<ClusterPacking.ClusterPlacement>> packedPlacements = new LinkedHashMap<>();
    private List<ClusterPacking.ClusterPlacement> topLevelPlacements; // null until first use
}
```

Deleted surface (see §6): `boundaryRadius`, `directNodeRingRadius`, `directNodeReach`,
`childBoundaryReach`, `ringRadius`, `sizeOf`, the private `Size` holder, `reachOf`,
`MAX_RENDERED_NODE_RADIUS`; the private field `sizes` (`Map<…, Size>`) is renamed to `footprints`
(`Map<…, Footprint>`). `discRadius`, `contentRingRadius`, `enclosure`, `enclosures` and the
constructor, including the `discRadii` population in the constructor, are unchanged.

### 2.5 `Seeds`

`Seeds` remains a nested `private static final class` with the same constructor and public-in-package
`Position positionFor(DesiredParticle particle)`. Deleted members: `topRingPosition`, and the
sibling-ring branch of `center(key)`. `isTopLevel(key)` is unchanged. The concrete behavior is in §3.4.

### 2.6 Topology / `DesiredParticle`

- `DesiredParticle.anchor(EnclosureHullKey key, double radius)` is **kept**; `topology()` passes
  `0.0` as the anchor's scalar radius. Rationale: after the change the anchor's scalar radius is not
  consumed anywhere: native repulsion is position-based, anchor separation uses the per-axis extents
  from `configureAnchorExtents`, and hierarchy/containment/relationship springs use rest lengths.
  `TypedNodeParticle.scaleRepulsion` explicitly exempts anchor particles, so the value is dead for
  anchors. Node particles keep `NODE_RADIUS * prominence.scale()`.
- `ForceKind` is unchanged: `RELATIONSHIP`, `CONTAINMENT`, `HIERARCHY`.
- `DesiredParticle.parentAnchorId` and `TypedSpringBox.configureParticle(id, radius, pinned, anchor, parentAnchorId)`
  are unchanged (they feed the ancestor-exclusion set).
- Relationship rest lengths are unchanged: `radiusOf(first) + radiusOf(second) + NodeSeparationProjection.MIN_GAP`,
  where an anchor's `radiusOf` is `contentRingRadius` (see current production `topology()`).

### 2.7 `TypedSpringBox` / `TypedNodeParticle`

`TypedSpringBox`:

```java
void configureAnchorExtents(String id, double halfWidth, double halfHeight,
                            double offsetX, double offsetY);   // new; delegates to the particle
void addBoundaryRepulsion(TypedNodeParticle particle, Vector3 displacement); // signature unchanged (2 args)
```

`TypedNodeParticle`:

```java
private double separationHalfWidth  = -1.0;
private double separationHalfHeight = -1.0;
private double separationOffsetX = 0.0;   // = −footprint.anchorX
private double separationOffsetY = 0.0;   // = −footprint.anchorY

void configureExtents(double halfWidth, double halfHeight, double offsetX, double offsetY);
double separationHalfWidth();
double separationHalfHeight();
double separationOffsetX();
double separationOffsetY();
// deleted: double boundaryRadius()
```

`separationRadius()`, `configure(radius, pinned)`, `scaleRepulsion(before)`, `move(int)`,
`attraction`, `repulsionN2`, `repulsionNLogN` remain. `configureAnchorExtents` is called from
`GraphStreamLayoutEngine.synchronize()` (see §3.6). `baseSeparationRadius()`,
`BOUNDARY_REPULSION_FACTOR`, `CROSS_MAP_DISPLACEMENT_LIMIT` are unchanged.

### 2.8 No module/public-API changes

`freeplane_api` is untouched. `GraphStreamLayoutFactory`, `LayoutEngine`, `LayoutRequest`,
`LayoutFrame`, `LayoutWorker`, `LayoutCalibration` and the diagnostics types keep their public
signatures. `GraphStreamBoundaryShould.exposeOnlyGraphStreamFreePublicLayoutSignatures` must keep
passing after the change.

---

## 3. Algorithms

### 3.1 Footprint recursion (per-axis, anchor-centred, no re-centring)

`BoundarySizes.footprint(key)` is memoized; it computes exactly:

```
footprint(key):
    if cached: return cached
    enclosure = enclosuresByHull.get(key)

    if enclosure.directNodes().isEmpty() and enclosure.directEnclosures().isEmpty():
        # empty enclosure: the existing label-sized box (the ONLY remaining use of the
        # character/padding constants)
        width  = 2.0 * BOUNDARY_PADDING
        height = CHAR_HEIGHT_UPPER_BOUND + 2.0 * BOUNDARY_PADDING
        for label in enclosure.labels():
            width = max(width, label.displayText().length() * CHAR_WIDTH_UPPER_BOUND + 2.0 * BOUNDARY_PADDING)
        result = Footprint(width, height, 0.0, 0.0)
    else:
        placements = placements(key)              # recursion through child footprints
        minX = min(p.x − p.item.width  * 0.5), maxX = max(p.x + p.item.width  * 0.5)
        minY = min(p.y − p.item.height * 0.5), maxY = max(p.y + p.item.height * 0.5)
        centreX = (minX + maxX) * 0.5
        centreY = (minY + maxY) * 0.5
        result = Footprint(maxX − minX + 2.0 * FRAME_CLEARANCE,
                           maxY − minY + 2.0 * FRAME_CLEARANCE,
                           −centreX, −centreY)
    cache and return result
```

`placements(key)` is also memoized; it builds the item list and delegates to the packer:

```
placements(key):
    if cached: return cached
    enclosure = enclosuresByHull.get(key)
    items = []

    if not enclosure.directNodes().isEmpty():
        ring    = contentRingRadius(key)                       # unchanged formula
        maxDisc = max over directNodes of discRadius(nodeKey)  # = NODE_RADIUS * prominence.scale()
        half    = ring + maxDisc + NODE_SETTLE_MARGIN
        items.add(ClusterItem(null, 2.0*half, 2.0*half, 0.0, 0.0, 0))   # content item, anchor at its centre

    order = 0
    for child in enclosure.directEnclosures():                 # projection list order
        fp = footprint(child)                                  # recursion, memoized
        items.add(ClusterItem(child, fp.width, fp.height, fp.anchorX, fp.anchorY, order++))
        # NOTE: a child item's anchor offset equals its Footprint anchor offset (it is not re-centred)

    result = items.isEmpty() ? [] : ClusterPacking.packItems(items, SIBLING_GAP)
    cache and return result
```

`topLevelPlacements()` is memoized once per `BoundarySizes`:

```
topLevelPlacements():
    if cached: return cached
    items = []
    order = 0
    for enclosure in enclosuresByHull.values():                # projection order
        if enclosure.mapRoot() or not enclosure.parentHull().isPresent():
            fp = footprint(enclosure.hullKey())
            items.add(ClusterItem(enclosure.hullKey(), fp.width, fp.height, fp.anchorX, fp.anchorY, order++))
    cached = ClusterPacking.packItems(items, SIBLING_GAP)
    return cached
```

Key properties (asserted by `BoundaryFootprintShould`, §7.1.2):

- The content item is always placed first at the parent anchor origin `(0, 0)`; the parent anchor is
  the centre of its direct-node content, and the direct-node ring in `Seeds` is centred on that anchor.
- The assembly is **not re-centred**: a footprint may be asymmetric about the anchor and carries the
  offset `(anchorX, anchorY) = (−boxCentreX, −boxCentreY)` in the local anchor frame.
- `FRAME_CLEARANCE` is added per side at every nesting level, so a nested footprint is inset by 16 per
  level inside its parent box; this is what makes cross-frame packed footprint boxes disjoint (§3.2).
- A leaf enclosure with direct nodes (no children) has `anchorX = anchorY = 0` because its only item
  (the content item) is symmetric and centred at the origin; an enclosure with children can be
  asymmetric about the anchor.

### 3.2 Deterministic V1 packing (production algorithm)

`ClusterPacking.packItems(items, gap)` is pure; `gap = BoundarySizes.SIBLING_GAP = 8.0` in production.
It is a total, deterministic function of `(items, gap)` with no RNG, hashing, clock or iteration-order
input beyond the item `order` fields and geometric values.

**Step 1 — order.** Copy the list, then stable-sort with the total order:

```
compare(a, b):
    if (a.anchorKey == null) != (b.anchorKey == null):      # content item first
        return a.anchorKey == null ? -1 : +1
    byArea = Double.compare(b.width * b.height, a.width * a.height)   # descending area
    if byArea != 0: return byArea
    return Integer.compare(a.order, b.order)                # ascending projection order
```

`order` is unique among children of one enclosure and the content item is separated by the first key,
so the order is total; input list order does not matter.

**Step 2 — candidate set.** For each item in order:

- If no item is placed yet: `xs = [0.0]`, `ys = [0.0]` (first item at the origin).
- Otherwise let `P` be the placed placements. For each `p ∈ P` add:
  - `p.x − (p.item.width + item.width) * 0.5 − gap`
  - `p.x + (p.item.width + item.width) * 0.5 + gap`
  - `p.y − (p.item.height + item.height) * 0.5 − gap`
  - `p.y + (p.item.height + item.height) * 0.5 + gap`
  - the centre lines `p.x` (to X) and `p.y` (to Y).
  Then let `minX = min(p.x − p.item.width * 0.5)`, `maxX = max(p.x + p.item.width * 0.5)` (likewise y)
  over `P`, and add the assembly-box edges:
  - to X: `minX − item.width * 0.5 − gap`, `maxX + item.width * 0.5 + gap`,
    `minX + item.width * 0.5`, `maxX − item.width * 0.5`
  - to Y: the four analogous values with `height`.
- Sort `xs` ascending (`Double.compareTo`); sort `ys` ascending; remove duplicates within
  `CANDIDATE_EPSILON = 1e-9` keeping the lowest representative (the entry with the smaller value).
  The candidate enumeration order is the nested loop **ascending X then ascending Y** with a
  one-based running index over the deduped sorted lists.

**Step 3 — clearance.** A candidate `(x, y)` is clear iff for every placed `p`:

```
clearanceX = |x − p.x| − (item.width + p.item.width) * 0.5
clearanceY = |y − p.y| − (item.height + p.item.height) * 0.5
clear(p) ⟺ clearanceX >= gap ∨ clearanceY >= gap
```

(Exact equality counts as clear; the negated form `clearanceX < gap ∧ clearanceY < gap` is an
overlap.) All non-clear candidates are skipped.

**Step 4 — selection key.** Over all clear candidates, choose the first in this lexicographic order:

1. smallest resulting **assembly bounding-box area**, computed over the candidate item box plus all
   placed item boxes:
   `area = (maxX − minX) * (maxY − minY)` where the bounds include `x ± item.width/2` and
   `y ± item.height/2` (the first item's area is `item.width * item.height` by the same formula);
2. then smallest distance from the origin `radius = hypot(x, y)` (the origin is the first item's
   placement, so this is well defined before any re-centring — and there is no re-centring);
3. then smallest candidate enumeration index (ascending X then ascending Y).

Comparisons use `CANDIDATE_EPSILON = 1e-9`:

```
better ⟺ !found
      ∨ area < bestArea − CANDIDATE_EPSILON
      ∨ (|area − bestArea| <= CANDIDATE_EPSILON ∧ ( radius < bestRadius − CANDIDATE_EPSILON
          ∨ (|radius − bestRadius| <= CANDIDATE_EPSILON ∧ index < bestIndex) ))
```

**Step 5 — fallback and defensive throw.** No fallback scan exists and none is needed: with a
non-empty placed set, `x = minX − item.width * 0.5 − gap` is always in the X candidate list. For
every placed `p`, `minX <= p.x − p.item.width * 0.5`, hence
`|x − p.x| >= (item.width + p.width) * 0.5 + gap`, so the candidate is clear on the X axis.
`packItems` therefore keeps only a defensive
`throw new IllegalStateException("Cluster packing found no free slot")`, which is unreachable for
non-empty inputs. Do not add a polar/lattice fallback and do not add a "fallback path" test.

**Step 6 — contacts (metadata).** Immediately after a placement is chosen, for every placed `p`:

```
boxGapX = |placement.x − p.x| − (item.width + p.item.width) * 0.5
boxGapY = |placement.y − p.y| − (item.height + p.item.height) * 0.5
if (boxGapX <= gap + CONTACT_GAP_TOLERANCE ∧ boxGapY < −CONTACT_GAP_TOLERANCE)
        ∨ (boxGapY <= gap + CONTACT_GAP_TOLERANCE ∧ boxGapX < −CONTACT_GAP_TOLERANCE):
    placement.contacts.add(p.item); p.contacts.add(item)        # bidirectional
```

Contacts record face-adjacent neighbours (separated within `gap + CONTACT_GAP_TOLERANCE` on one axis,
overlapping by more than `CONTACT_GAP_TOLERANCE` on the other). No force reads them; they exist for
test assertions and diagnostics.

**Determinism.** Item order, candidate enumeration, dedupe representative and all three selection keys
are fixed. Equal inputs produce equal placements (bit-identical doubles) across repeated calls, list
permutations, and fresh `BoundarySizes` instances.

**Performance.** The naive implementation is `O(n²)` candidates per item (`(3k + 4)²` for k placed
items, after adding the three X and three Y values per placed item plus the four assembly-box X/Y
edges) and `O(n³)` overall. The acceptance bound is wall clock, not asymptotic: the 200-child fixture
via `LayoutWorker.submit` (construction + packing + seeding + frame) must complete within **3.0 s**
(measured 0.580 s submit and 1.252 s for 10 steps on the stored prototype; review-6 re-measured
0.37–0.58 s submit). Optimizations (grid-indexed clearance, incremental bounding boxes) are permitted
only if they preserve the exact candidate set, enumeration order and selection key above; they are not
required.

### 3.3 Initial gutter and its acceptance tolerance

For every pair of items in the same assembly, at packed positions:

- non-overlap: `max(boxGapX, boxGapY) >= −CANDIDATE_EPSILON`, where
  `boxGapX = |x₁ − x₂| − (w₁ + w₂)/2` and `boxGapY = |y₁ − y₂| − (h₁ + h₂)/2`;
- for every recorded contact: the separating-axis `boxGapX`/`boxGapY` lies in
  `[SIBLING_GAP − CANDIDATE_EPSILON, SIBLING_GAP + CONTACT_GAP_TOLERANCE]`.

The lower bound catches double rounding (the measured minimum contact gap is 7.999999999999972); the
upper bound is the contact predicate tolerance. An assertion of the form `gap == 8.0` or `gap >= 8.0`
must not be written.

### 3.4 Seed consumption (`Seeds`)

`Seeds` consumes the memoized placements lazily per particle; `GraphStreamLayoutEngine.synchronize()`
calls `seeds.positionFor(desired)` **only when no `ParticleState` exists** (retained particles keep
their positions). For a new particle:

```
positionFor(particle):
    if particle.nodeKey != null: return nodePosition(particle.nodeKey)
    else:                        return center(particle.anchorKey)

nodePosition(nodeKey):
    parentKey = parentEnclosureOfNode.get(nodeKey)
    center = parentKey != null ? center(parentKey) : (0, 0)
    if parentKey == null: return center                    # unchanged fallback
    parent  = sizes.enclosure(parentKey)
    index   = parent.directNodes().indexOf(nodeKey)        # first occurrence
    count   = parent.directNodes().size()
    radius  = sizes.contentRingRadius(parentKey)
    angle   = 2π * max(0, index) / max(1, count)
    return (center.x + radius*cos(angle), center.y + radius*sin(angle))   # ring unchanged

center(key):
    if cached: return cached
    if isTopLevel(key):
        p = sizes.topLevelPlacementOf(key)
        result = p == null ? (0, 0) : (p.x + p.item.anchorX, p.y + p.item.anchorY)
    else:
        parentKey    = enclosure.parentHull().get()
        parentCenter = center(parentKey)
        p = sizes.placementOf(parentKey, key)
        result = p == null ? parentCenter
                           : (parentCenter.x + p.x + p.item.anchorX,
                              parentCenter.y + p.y + p.item.anchorY)
    cache and return result

isTopLevel(key): enclosure == null ∨ enclosure.mapRoot() ∨ ¬enclosure.parentHull().isPresent()
```

Top-level membership is exactly the `isTopLevel`/`topLevelPlacements` predicate: `mapRoot() == true`
**or** no parent hull. A map root without a parent hull has no hierarchy link. A theoretical
`mapRoot()` enclosure that also has a `parentHull()` is both top-level and placed in its parent's
assembly (existing predicate behavior; no fixture exercises it — §10.7).

### 3.5 Hierarchy links with packed rest lengths

The hierarchy link topology is unchanged (one parent-anchor ↔ child-anchor link per direct child,
deduplicated by unordered id pair, `ForceKind.HIERARCHY`, `crossMap = false`). The rest length becomes
the packed parent–child anchor distance:

```
addHierarchyLinks(links, pairs, sizes, anchorIds, parentKey):
    parentId = anchorIds.get(parentKey)
    for placement in sizes.placements(parentKey):
        if placement.item.anchorKey == null: continue        # skip the content item
        childId = anchorIds.get(placement.item.anchorKey)
        if childId == null: continue
        childAnchor = (placement.x + placement.item.anchorX,
                       placement.y + placement.item.anchorY)
        addHierarchyLink(links, pairs, parentId, childId, hypot(childAnchor.x, childAnchor.y))
```

`topology()` calls `addHierarchyLinks(...)` once for every enclosure with
`enclosure.parentHull().isPresent()`. Consequences:

- Rest lengths are recomputed from the deterministic packing on **every** `synchronize()`; there is no
  per-particle rest state, no frozen map, and no parent-change detection.
- A single-item child assembly places the child item's box centre at `(0, 0)`, so its rest is
  `hypot(item.anchorX, item.anchorY)`: `0` when the child footprint is centred on its anchor (the B/D/F
  single-child fixtures, measured settled distance 9.3–12.1; see §8.3.4), and equal to the child's
  anchor offset otherwise.
- The `hierarchy()` multiplier stays `0.30`; `GROUP_SPACING` and `SUB_GROUP_SPACING` are deleted.
- No top-level packing/contact links are added: top-level maps are not spring-held against native
  repulsion. This is the accepted scope boundary.
- Rests are recomputed across reorders; the large-reorder retained motion is recorded, not asserted
  (§8.3.5).

### 3.6 Offset-aware anchor separation

`GraphStreamLayoutEngine.synchronize()` configures every anchor particle's separation box after
`springBox.configureParticle(...)`:

```
if desired.anchorKey != null:
    Footprint f = sizes.footprint(desired.anchorKey)
    springBox.configureAnchorExtents(desired.id, f.width * 0.5, f.height * 0.5,
                                     −f.anchorX, −f.anchorY)
```

`TypedSpringBox.addBoundaryRepulsion(particle, displacement)` keeps its 2-argument production
signature and current guard structure (only anchors; skip self; skip ancestor pairs via `parentOf`),
but the circular branch is replaced entirely by the box branch (`other` is the loop's other anchor
particle):

```
ownCentreX   = particle.getPosition().x + particle.separationOffsetX()
ownCentreY   = particle.getPosition().y + particle.separationOffsetY()
otherCentreX = other.getPosition().x    + other.separationOffsetX()
otherCentreY = other.getPosition().y    + other.separationOffsetY()

forceGapX = |ownCentreX − otherCentreX| − (particle.separationHalfWidth()  + other.separationHalfWidth()
                                            + GraphStreamLayoutEngine.BoundarySizes.SIBLING_GAP)
forceGapY = |ownCentreY − otherCentreY| − (particle.separationHalfHeight() + other.separationHalfHeight()
                                            + GraphStreamLayoutEngine.BoundarySizes.SIBLING_GAP)

if forceGapX >= 0.0 ∨ forceGapY >= 0.0: continue                     # no force

if (−forceGapX) <= (−forceGapY):                                     # equal overlaps resolve along x
    push = (ownCentreX >= otherCentreX ? +1.0 : −1.0) * (−forceGapX) * BOUNDARY_REPULSION_FACTOR
    displacement.set(0, displacement.at(0) + push)
else:
    push = (ownCentreY >= otherCentreY ? +1.0 : −1.0) * (−forceGapY) * BOUNDARY_REPULSION_FACTOR
    displacement.set(1, displacement.at(1) + push)
```

The push direction is **away from the other footprint-box centre** (`anchor − anchor offset`), not
away from the other anchor. This matters whenever the two anchor offsets differ; a literal "away from
the other anchor" implementation can push the boxes further into each other. Ancestor exclusion, pins
and `BOUNDARY_REPULSION_FACTOR = 0.5` are unchanged. At packed positions the two box centres are the
placed item centres and the packing guarantee (`max(forceGapX, forceGapY) >= 0`) makes the force exactly zero; for cross-frame pairs, each footprint is inset by `FRAME_CLEARANCE` per nesting level
inside disjoint parent boxes, so cross-frame gaps grow with nesting depth. This is the "packed-position
separation-box invariant" asserted in §7.1.2.

Settled marginal overlaps (soft force at equilibrium) are permitted and reported: measured A −0.1,
G −0.2/−0.4, B/C/D/F zero (§8.3.3). The post-geometry `BoundarySeparationCorrection` remains the
crossing safety net and is not changed.

### 3.7 Data flow (unchanged shape)

```
projection ──► BoundarySizes (memoized)
                 ├─ ClusterPacking.packItems + contacts → placements (anchor frame), top level
                 │     ├─ footprint(key) = content ∪ packed children + FRAME_CLEARANCE
                 │     └─ Seeds: anchor/node seeds for new particles only
                 ├─ topology(): HIERARCHY links, rest = packed parent–child anchor distance
                 └─ per-axis half extents + anchor offset
                       └─ TypedSpringBox.addBoundaryRepulsion (footprint boxes)
```

Determinism: item order, candidate enumeration, selection keys and seed composition are fixed by
projection order and geometric values; no RNG, hashing or clock input participates.

---

## 4. Constants and tolerances

| Constant | Value | Owner | Change |
|---|---|---|---|
| `SIBLING_GAP` | `8.0` | `BoundarySizes` | unchanged |
| `NODE_SETTLE_MARGIN` | `8.0` | `BoundarySizes` | **new** (the overlay's `proto.margin` default of 12.0 is prototype-only; production is hardcoded 8.0) |
| `FRAME_CLEARANCE` | `16.0` | `BoundarySizes` | unchanged |
| `CHAR_WIDTH_UPPER_BOUND` | `16.0` | `BoundarySizes` | unchanged; empty-enclosure box only |
| `CHAR_HEIGHT_UPPER_BOUND` | `24.0` | `BoundarySizes` | unchanged; empty-enclosure box only |
| `BOUNDARY_PADDING` | `8.0` | `BoundarySizes` | unchanged; empty-enclosure box only |
| `CANDIDATE_EPSILON` | `1e-9` | `ClusterPacking` | **new** |
| `CONTACT_GAP_TOLERANCE` | `0.75` | `ClusterPacking` | **new** (contact predicate upper tolerance) |
| `CONTAINMENT` | `1.0` | `LayoutCalibration` | **was `0.15`** |
| `HIERARCHY` | `0.30` | `LayoutCalibration` | unchanged |
| `SAME_MAP` | `1.0` | `LayoutCalibration` | unchanged |
| `BOUNDARY_REPULSION_FACTOR` | `0.5` | `TypedSpringBox` | unchanged |
| `K2` / `REPULSION_FACTOR` | `16.0` | `TypedSpringBox` | unchanged |
| `ATTRACTION_FACTOR` | `0.05` | `TypedSpringBox` | unchanged |
| `BASE_SEPARATION_RADIUS` | `8.0` | `TypedSpringBox` | unchanged |
| `CROSS_MAP_DISPLACEMENT_LIMIT` | `0.005` | `TypedSpringBox` | unchanged |
| `NODE_RADIUS` | `8.0` | `GraphStreamLayoutEngine` | unchanged |
| `NodeSeparationProjection.MIN_GAP` | `6.0` | `NodeSeparationProjection` | unchanged |

Acceptance tolerances:

| Quantity | Tolerance |
|---|---|
| contact gutter (`boxGap` on the separating axis of a recorded contact) | `SIBLING_GAP − CANDIDATE_EPSILON ≤ gap ≤ SIBLING_GAP + CONTACT_GAP_TOLERANCE` |
| non-overlap of any packed pair | `max(boxGapX, boxGapY) >= −CANDIDATE_EPSILON` |
| packed-position separation-box invariant (non-ancestor pairs) | `forceGapX >= −CANDIDATE_EPSILON ∨ forceGapY >= −CANDIDATE_EPSILON` |
| footprint / placement equality against formulas | exact for engine-composed values; `1e-9` for recomputation comparisons |
| reported containment overflow measurement tolerance | `0.01` (probe convention; the value itself is reported, not asserted) |
| settle budget | first idle else 2000 steps, then always **200** extra steps |
| stability window | **1000** steps after the documented endpoint |
| 200-child wall clock | `≤ 3.0 s` (measured `0.580 s` submit / `1.252 s` for 10 steps) |

---

## 5. Error behaviour, edge cases and pins

**Defensive input validation in `ClusterPacking.packItems`** (unreachable for projection-derived
inputs; fail fast rather than silently mis-pack):

1. `Objects.requireNonNull(items, "items")`; each element `Objects.requireNonNull(item, "item")`.
2. `requirePositiveFinite(item.width/height)` → `IllegalArgumentException` otherwise
   (`> 0.0` and `Double.isFinite`).
3. `requireFiniteNonNegative(gap)` → `IllegalArgumentException` otherwise.
4. Duplicate non-null `anchorKey` in one item list → `IllegalArgumentException` (the projection's
   `directEnclosures()` is expected to be duplicate-free).
5. `packItems(Collections.emptyList(), gap)` returns an empty list; no item is placed.
6. No free slot → `IllegalStateException("Cluster packing found no free slot")` (provably unreachable
   for non-empty placed sets; §3.2 step 5).

**Empty and single-item enclosures:**

- Empty enclosure (no direct nodes, no direct enclosures): label-sized `Footprint` (§3.1), packed like
  any item. `placements(key)` is empty if queried directly; `Seeds` positions it from its parent's (or
  the top-level) placement.
- Single-item enclosure: `packItems` places the item at `(0, 0)` and records no contacts; the
  hierarchy rest is `hypot(item.anchorX, item.anchorY)` (0 for a centred child footprint, e.g. B/D/F).
  The item's footprint lies inside the parent's footprint by construction.
- Single direct node: `contentRingRadius` is `0.0`, so the node seeds at the parent anchor and the
  content half-extent is `maxDisc + NODE_SETTLE_MARGIN`.

**Pins:**

- Pins apply to node particles only (`pinFor` returns a pin only for `nodeKey != null`). Anchor
  placement, packing and anchor extents are unaffected. `synchronize()` overrides the seeded node
  position with the pin position and freezes it, exactly as today (`PinProjection.active` +
  `springBox.configureParticle(..., pinned=true, ...)`).
- Pinned content can exceed the packing margin; this is reported, not asserted (§8.2, §8.3).
- Fixture C (pin at `(60,20)`) is the pinned fixture. At the engine frame the anchors still equal the
  packing output exactly; through `LayoutWorker.submit` the post-geometry correction moves anchors by
  up to 380.45 vs the packing (review-6 `InitProbe`), so C is exempt from worker-path packing equality
  (§7.1.3, §8.2).

**Topology change / reparenting:** rest lengths follow the new deterministic packing because they are
recomputed on every synchronize. The `aTopologyChangeDoesNotTeleportRetainedParticles` small-change
fixture keeps its hard `< 8.0` units/step bound (measured passing); the large-reorder fixture is
recorded, not asserted (§8.3.5).

**Cross-map relationships:** unchanged; the cross-map budget keeps its relationship-only meaning (no
packing/hierarchy link is marked `crossMap`).

---

## 6. Legacy removal

The repository's legacy-removal policy applies: delete, do not retain. The exact removal list (design
§4.6 plus review-6 finding 8 plus symbols that become transitively dead):

`GraphStreamLayoutEngine` / `BoundarySizes` / `Seeds` / topology:

- `GROUP_SPACING`, `SUB_GROUP_SPACING`
- `hierarchyRestLength(int)`, and the `Map<EnclosureHullKey, Integer> depths` local plus
  `enclosureDepths(GraphProjection)` and `depthOf(...)` (transitively dead once rests come from the
  packing)
- `BoundarySizes.MAX_RENDERED_NODE_RADIUS`
- `BoundarySizes.sizeOf(EnclosureHullKey)` and its private `Size` holder (no consumer remains; the
  empty-enclosure label branch moves into `footprint`)
- `BoundarySizes.boundaryRadius(EnclosureHullKey)` (see §2.6 for the anchor radius replacement)
- `BoundarySizes.directNodeRingRadius`, `directNodeReach`, `childBoundaryReach`, `ringRadius`,
  `reachOf`
- `Seeds.topRingPosition` and the sibling-ring branch of `Seeds.center`
- `TypedNodeParticle.boundaryRadius()` (the `separationRadius()` field and accessor stay, used by node
  repulsion scaling)

Test-local mirrors to remove (design §9.1, review-6 finding 8):

- `TypedForcesShould.ringRadius(List<SafeNodeLabel>)` (line ~714) and the label
  `CHAR_WIDTH/HEIGHT`/`BOUNDARY_PADDING`/`SIBLING_GAP`/`FRAME_CLEARANCE` constants if they become
  unused in that class
- the static `BoundarySeparationShould.ringRadius`/`maximumReach`/`reachOf` helpers (and
  `widthOf`/`heightOf` if they become unused after the rewrite, plus any constants left unused)
- `ReferenceRepulsionFixture.Sizes`, `boundaryRadius(GraphProjection, EnclosureHullKey)`,
  `MAX_RENDERED_NODE_RADIUS`, and its now-unused character/padding constants

Retained deliberately: `contentRingRadius`, `discRadius`, `SIBLING_GAP`, `FRAME_CLEARANCE`,
`CHAR_WIDTH_UPPER_BOUND`/`CHAR_HEIGHT_UPPER_BOUND`/`BOUNDARY_PADDING` (empty-enclosure label branch
only), `ForceKind.HIERARCHY`, `LayoutCalibration.hierarchy()` (it is the anchor-spring multiplier, not
a rest-length table).

The prototype overlay's `rawPackingDisplacement`/`rawBoundaryDisplacement` fields, its 3-argument
`addBoundaryRepulsion`, and the cap-limit constants are prototype scaffolding for deleted
experiments; they are not part of the production change and must not be introduced.

---

## 7. Tests

### 7.1 New tests

All new tests live in the production package (see §7.4) so they can use `BoundarySizes`,
`ClusterPacking` and their nested types directly, while using the public engine/worker API
(`GraphStreamLayoutFactory`, `LayoutEngine`, `LayoutRequest`, `LayoutFrame`, `LayoutWorker`,
`GraphGeometryEngine`, `AwtGeometryTextMetrics`) for frame and painted-hull measurement.

#### 7.1.1 `ClusterPackingShould`

Synthetic `ClusterItem`/`ClusterPlacement` unit cases plus the 200-child timing:

1. `emptyInputProducesNoPlacements` — empty list → empty result.
2. `singleItemIsPlacedAtTheOrigin` — one item → `(0.0, 0.0)`, no contacts.
3. `twoItemsAreSeparatedByExactlyTheGap` — two items → separated on one axis in
   `[SIBLING_GAP − CANDIDATE_EPSILON, SIBLING_GAP + CONTACT_GAP_TOLERANCE]`,
   `max(boxGapX, boxGapY) >= −CANDIDATE_EPSILON`, contact recorded bidirectionally.
4. `contentItemIsOrderedFirstAndPlacedAtTheOrigin` — content item with the largest area still sorts
   first and is placed at `(0, 0)`.
5. `remainingItemsAreOrderedByDescendingAreaThenProjectionOrder` — equal-area items keep ascending
   `order`; larger areas are placed earlier.
6. `selectionMinimisesTheAssemblyBoundingBoxArea` — construct a case where the minimum-area placement
   is not the nearest/origin-adjacent one; assert the chosen coordinates.
7. `equalAreaCandidatesTieBreakByDistanceFromTheOrigin`.
8. `equalAreaAndDistanceCandidatesTieBreakByEnumerationOrder` — symmetric layout; assert ascending
   X-then-Y wins.
9. `centreLineCandidateIsUsed` — third item placed with one coordinate equal to a placed item's centre
   line.
10. `assemblyBoxEdgeCandidateIsUsed` — a placement on an assembly-box edge (inside or outside the
    current box).
11. `inputListOrderDoesNotAffectPlacements` — shuffle the input list (item `order` fields fixed) and
    assert placement equality field-wise (`x`/`y` on the placement, `width`/`height`/`anchorKey`/
    `order` on the item) plus equal `contacts` list contents.
12. `placementsAreDeterministicAcrossRepeatedCalls` — repeated calls (and fresh item lists) give
    placements and contacts that are equal field-wise (`x`/`y`/`width`/`height`/`anchorKey`/`order`,
    since `ClusterPlacement`/`ClusterItem` have no `equals`) and by `contacts` list contents; not
    necessarily the same instance.
13. `everyPairIsSeparatedOnAtLeastOneAxis` — `max(boxGapX, boxGapY) >= −CANDIDATE_EPSILON` over all
    pairs for a multi-item random-but-fixed set.
14. `recordedContactsSatisfyTheContactPredicate` — for every recorded contact the separating-axis gap
    is in `[SIBLING_GAP − CANDIDATE_EPSILON, SIBLING_GAP + CONTACT_GAP_TOLERANCE]`; for every
    non-recorded pair the predicate is false. Compare recorded `contacts` lists by contents (the item
    pairs they contain), not list identity.
15. `duplicateAnchorKeysAreRejected` — `IllegalArgumentException`.
16. `invalidDimensionsAndGapAreRejected` — null/non-positive/non-finite width/height and negative gap
    → `NullPointerException`/`IllegalArgumentException`.
17. `twoHundredChildrenPackWithinThreeSeconds` — `wideProjection` (200 empty children, §7.2
    supplementary fixtures);
    `LayoutWorker.submit` wall clock `<= 3.0 s`; print the measured value. (Measured `0.580 s` submit /
    `1.252 s` for 10 steps.)

#### 7.1.2 `BoundaryFootprintShould`

1. `leafFootprintIsSquareAroundTheAnchor` — enclosure with k ≥ 2 direct nodes:
   `width == height == 2.0 * (contentRingRadius + maxDisc + NODE_SETTLE_MARGIN + FRAME_CLEARANCE)`,
   `anchorX == anchorY == 0.0`.
2. `singleDirectNodeFootprint` — ring 0:
   `width == height == 2.0 * (discRadius + NODE_SETTLE_MARGIN + FRAME_CLEARANCE)`.
3. `nestedFootprintIsThePackedChildBoxPlusFrameClearance` — parent with ≥ 1 child: width/height and
   `anchorX/anchorY` equal the memoized placement-bbox formula (§3.1), and the anchor offset equals
   `−bboxCentre` in the anchor frame.
4. `emptyEnclosureUsesTheLabelSizedBox` — width `max(16, len*16 + 16)`, height `40`, anchor `(0, 0)`.
5. `singleChildIsPlacedAtTheAnchorOrigin` — one child: child placement `(0, 0)`;
   parent footprint = child footprint + 32 per axis; parent `anchorX == anchorY == 0`.
6. `topLevelMembershipMatchesSeedsPredicate` — `topLevelPlacements()` contains exactly the enclosures
   with `mapRoot() || !parentHull().isPresent()`, and `topLevelPlacementOf` is null for nested ones.
7. `contentItemIsPlacedAtTheAnchor` — the first placement of an enclosure with direct nodes is the
   content item (`anchorKey == null`) at `(0, 0)`, for every fixture that has direct nodes (A, B, C,
   D, E, F, G, H).
8. `footprintsAreMemoisedAndRecursionTerminates` — repeated `footprint`/`placements` access returns
   the same memoized instance (`isSameAs`) and completes on the widest fixture.
9. **`packedFootprintBoxesDoNotOverlapAcrossTheFixtureSet`** (the packed-position separation-box
   invariant). Build the packed tree for fixtures A–H plus `wideProjection`; compose each enclosure's
   absolute box centre at packed positions (`boxCentre = packedAnchor − anchorOffset`; top-level from
   `topLevelPlacements`, nested from parent centre + placement + item anchor offset), then for every
   **non-ancestor** pair assert `forceGapX >= −CANDIDATE_EPSILON ∨ forceGapY >= −CANDIDATE_EPSILON`,
   with `halfW = footprint.width/2`, `halfH = footprint.height/2` and `SIBLING_GAP`. Review-6 verified
   0 violations on A–H and 20 075 pairs from 400 random nested projections; this test pins the safety
   argument for criterion 1.

#### 7.1.3 `ClusterProximityShould`

Fixtures A–H (§7.2). The initial-frame assertion uses the **engine frame**:
`GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults()).apply(request)` returns the seeded
frame **before** `BoundarySeparationCorrection` (the correction runs in `LayoutWorker.submit/step`).
Settled measurements use `LayoutWorker` frames, exactly like `ContactProbe`.

Initial frame:

1. `initialFrameEqualsTheDeterministicPacking` — for each of A–H, at the engine frame:
   - every anchor equals the composed `BoundarySizes` packing output exactly (`isEqualTo`, measured
     max delta 0.0);
   - every direct node equals `parentAnchor + contentRingRadius*(cos, sin)` of its index angle exactly;
   - content at the anchor for enclosures with direct nodes;
   - correct top-level membership;
   - every pair is non-overlapping (`max(boxGapX, boxGapY) >= −CANDIDATE_EPSILON`) and every contact
     gutter is in `[SIBLING_GAP − CANDIDATE_EPSILON, SIBLING_GAP + CONTACT_GAP_TOLERANCE]`;
   - two fresh engines produce equal frames (determinism).
   For pinned fixture C, anchors still equal the packing exactly; the pinned node `b-n0` equals the pin
   `(60, 20)`.
2. `generatedProjectionsComposeIdenticallyAndDoNotOverlap` — the committed form of review-6's
   400-projection fuzz: a fixed-seed generator (seed recorded in the test; projections mixing 1–6
   maps, 0–6 direct nodes and 0–5 child enclosures to depth 3, empty enclosures and single-node
   groups) produces, for each generated projection, an engine frame whose anchors and node seeds equal
   a **fresh** `BoundarySizes` composition over the same projection exactly, with
   `max(boxGapX, boxGapY) >= −CANDIDATE_EPSILON` for every pair (review-6 measured 0 packed-box
   violations over 20 075 non-ancestor pairs).
3. `initialFrameThroughWorkerKeepsSeedsWithoutPins` — for A, B, D, E, F, G, H: the
   `LayoutWorker.submit` frame equals the engine frame (the correction does not move an unpinned fresh
   seed), plus the non-overlap/gutter checks.
4. `initialFrameThroughWorkerWithPinOnlyChecksNonOverlap` — for C: **do not** assert packing equality
   through the worker (measured divergence up to 380.45); assert the same footprint non-overlap/gutter
   check as test 1 (`max(boxGapX, boxGapY) >= −CANDIDATE_EPSILON`, contact gutters in the §3.3 band)
   plus the pin position `b-n0 = (60, 20)`. Review-6's dedicated probe measured 0 footprint overlaps,
   0 both-axes-below-8 violations and 0 painted crossings at that frame.
5. `settledFixturesReachIdleWithinBudgetAndDoNotCross` — for each of A–H, run to first idle else 2000
   steps, then +200 steps, then a 1000-step window; assert `idle` was found within the 2000-step budget
   (measured 22–785) and that all-pairs non-ancestor painted crossings are 0 at the documented endpoint
   (measured 0). No settled hold band is asserted.
6. `settledEvidenceIsRecorded` — print one stable evidence line per fixture and per named pair /
   enclosure: `idle`, endpoint step, packed vs settled anchor distance, painted gap, window maximum
   hold deviation, containment overflow, marginal overlaps. The implementation task captures this
   output into its evidence file (§8.4).

### 7.2 Fixtures A–H (exact definitions)

Shared ids: workspace `00000000-0000-0000-0000-0000000000aa`; maps
`00000000-0000-0000-0000-000000000001` (MAP_ONE), `...0002` (MAP_TWO), `...0003` (MAP_THREE), `...0004`
(MAP_FOUR). A hull key is
`EnclosureHullKey.of(singletonList(EnclosureKey.of(SourceNodeKey.persisted(NodeReference.of(map, PersistedNodeId.of(id))))))`.
A projected node is `ProjectedNode.of(key, SafeNodeLabel.of(label, label), mapName, graphGroup)`; an
enclosure is `ProjectedEnclosure.of(hull, [EnclosureKey], [SafeNodeLabel], mapName, parent,
directNodes, directEnclosures, mapRoot, BoundaryTier)`. Fixture A uses `mapName = "M"` and
`graphGroup = true` on every node and enclosure (matching `ReferenceRepulsionFixture.referenceProjection(1L)`);
fixtures B–H use `mapName = "Map"` and `graphGroup = false` (matching `ContactProbe`); the flag does
not affect the layout under test. The tables in this section define the fixtures; they are not
re-created from the private probe builders.

Tier strings: the probe's `mapName` values (`"M"`, `"Map"`, `"map"`) do not affect the layout
under test; use the `BoundaryTier` values given in each fixture's table.

**A — reference fixture (root > zfc > axioms + definitions)** — `ReferenceRepulsionFixture.referenceProjection(1L)`,
WORKSPACE = `...00aa`, MAP = MAP_ONE:

| hull id | label | parent | direct nodes | children | mapRoot | tier |
|---|---|---|---|---|---|---|
| `root` | `Axiomatic Set Theory` | — | — | `zfc` | true | SUPPRESSED |
| `zfc` | `ZFC` | `root` | — | `axioms`, `defs` | false | EMPHATIC |
| `axioms` | `Axioms` | `zfc` | `n1`,`n2`,`n3` | — | false | SUBTLE |
| `defs` | `Basic Definitions and Theorems` | `zfc` | `n4` | — | false | SUBTLE |

Nodes: `n1` `Fundation / Regularity` (**sic**; keep the label verbatim), `n2` `Replacement Scheme`,
`n3` `Axiom of Choice`, `n4` `Theorem`.

**B — two maps** — `twoMapProjection(1L)`:

| map | hull id | label | parent | direct nodes | children | mapRoot | tier |
|---|---|---|---|---|---|---|---|
| MAP_ONE | `a-root` | `Map a` | — | — | `a-sub` | true | EMPHATIC |
| MAP_ONE | `a-sub` | `a fundamentals` | `a-root` | `a-n0`…`a-n5` (6) | — | false | SUBTLE |
| MAP_TWO | `b-root` | `Map b` | — | — | `b-sub` | true | EMPHATIC |
| MAP_TWO | `b-sub` | `b fundamentals` | `b-root` | `b-n0`,`b-n1` (2) | — | false | SUBTLE |

Node labels: `Theorem: a statement i with a long label` / `Theorem: b statement i with a long label`.

**C — two maps + 3 cross-map relationships + pin** — `relationshipProjection(1L)` plus
`PinProjection.active(PinRecord.of(<b-n0 ref>, 60.0, 20.0, []), b-n0)`:

| map | hull id | label | parent | direct nodes | children | mapRoot | tier |
|---|---|---|---|---|---|---|---|
| MAP_ONE | `a-root` | `Map a` | — | — | `a-sub` | true | EMPHATIC |
| MAP_ONE | `a-sub` | `a fundamentals` | `a-root` | `a-n0`…`a-n3` (4) | — | false | SUBTLE |
| MAP_TWO | `b-root` | `Map b` | — | — | `b-sub` | true | EMPHATIC |
| MAP_TWO | `b-sub` | `b fundamentals` | `b-root` | `b-n0`…`b-n3` (4) | — | false | SUBTLE |

Relationships: 3 `ProjectedEdge`s between `a-ni` and `b-ni` for `i = 0,1,2`, direction `FORWARD`,
`GraphRelationshipRecord.of(RelationshipId.of("20000000-0000-0000-0000-%012d" % (i+1)), i+1, a-ni, b-ni, FORWARD, [])`,
each edge built with `ProjectedEdgeKey.of(sourceEndpoint, targetEndpoint)` and one
`EdgeContributor.graphRelationship(relationship, sourceEndpoint, targetEndpoint)`.

**D — two maps + 3 cross-map relationships** — `relationshipProjection(1L)`, no pins.

**E — dual enclosure (root 2 nodes + child 3 nodes)** — `dualProjection(1L)`, map MAP_ONE:

| hull id | label | parent | direct nodes | children | mapRoot | tier |
|---|---|---|---|---|---|---|
| `root` | `Root map` | — | `r0`,`r1` | `child` | true | SUPPRESSED |
| `child` | `child group` | `root` | `c0`,`c1`,`c2` | — | false | SUBTLE |

Node labels: `Theorem: root statement zero/one with a long label`, `Theorem: child statement i with a
long label`.

**F — four maps heterogeneous** — `fourMapProjection(1L)`: for `m = 0..3`, map `MAP_{m+1}`, prefix
`m<m>` (`m0`, `m1`, `m2`, `m3`), node count `2 + m` (2, 3, 4, 5):

| hull id | label | parent | direct nodes | children | mapRoot | tier |
|---|---|---|---|---|---|---|
| `mM-root` | `Map mM` | — | — | `mM-sub` | true | EMPHATIC |
| `mM-sub` | `mM fundamentals` | `mM-root` | `mM-n0`…`mM-n{count-1}` | — | false | SUBTLE |

Node labels: `Theorem: mM statement i with a long label`.

**G — one map, four sibling subgroups** — `fourSiblingProjection(1L)`, map MAP_ONE:

| hull id | label | parent | direct nodes | children | mapRoot | tier |
|---|---|---|---|---|---|---|
| `g-root` | `Map g` | — | — | `g0`,`g1`,`g2`,`g3` | true | EMPHATIC |
| `gK` (K=0..3) | `group K` | `g-root` | `gK-n0`,`gK-n1` | — | false | SUBTLE |

Node labels: `Theorem: gK statement i with a long label`.

**H — root with direct nodes + two nested sibling sub-clusters** — `asymmetricSiblingProjection(1L)`,
map MAP_ONE:

| hull id | label | parent | direct nodes | children | mapRoot | tier |
|---|---|---|---|---|---|---|
| `h-root` | `Root map` | — | `r0`,`r1` | `h-c1`,`h-c2` | true | SUPPRESSED |
| `h-c1` | `child one group` | `h-root` | `c1-0`,`c1-1` | `h-g1` | false | EMPHATIC |
| `h-g1` | `grandchild group` | `h-c1` | `g1-0`,`g1-1` | — | false | SUBTLE |
| `h-c2` | `child two group` | `h-root` | `c2-0`,`c2-1` | — | false | SUBTLE |

Node label prefixes: `root`, `child one`, `grandchild`, `child two`; each label
`Theorem: <prefix> statement i with a long label`.

**Supplementary fixtures (not part of A–H):**

- `wideProjection` (200-child timing): MAP_ONE, root hull `wide-root` label `Root`, mapRoot true,
  EMPHATIC, no direct nodes, 200 empty children `wide-0`…`wide-199` (label `wide-i`, SUBTLE, parent
  `wide-root`), no nodes, no edges.
- Reorder fixture (`probe-final.txt` tail): `mapProjection(1L, 4)` then `mapProjection(2L, 5)`.
  For map index `m` (0-based), map id `MAP_{m+1}` for `m < 4`, else
  `00000000-0000-0000-0000-%012d` with `100 + m`; prefix `mM`; child hull `mM-sub` (`mM fundamentals`)
  with node count `(m == mapCount−1 && mapCount > 4) ? 8 : 2 + m` and root hull `mM-root`
  (`Map mM`, mapRoot true, EMPHATIC, child `[mM-sub]`, no direct nodes). So before: 4 maps with
  sub-node counts 2,3,4,5; after: the same 4 plus a new larger 5th map with 8 nodes (the reorder
  trigger).
- `twoMapWorkspaceSettlesToIdle` fixture (`TypedForcesShould`): 2 maps; each root has 3 subtrees with
  4 nodes each (labels `Boundary s node i`, sub labels `A-sub s`/`B-sub s`, root labels
  `Axiomatic Set Theory` (SUPPRESSED) / `Topology` (EMPHATIC)); 4 cross-map relationships between
  `a0_i` and `b0_i`.

### 7.3 Impacted existing tests (class → test → disposition)

`GraphStreamBoundaryShould`:

| test | disposition |
|---|---|
| `provideOrderedPositiveCalibrationStrengths` | **rewrite**: assert positivity of `containment()`, `hierarchy()`, `sameMap()` plus `hierarchy() < containment()` and `containment() <= sameMap()` with the recorded values (0.30, 1.0, 1.0). The old `containment() < hierarchy()` no longer expresses the intended stiffness (containment springs are now as stiff as same-map links) and fails with "Expecting actual: 1.0 to be less than: 0.3". |
| all other tests | unchanged; re-run. |

`TypedForcesShould`:

| test | disposition |
|---|---|
| `hierarchyAnchorsSeedOnTheGroupRing` | **rewrite to packed placements** (engine frame equals the `BoundarySizes` placement composition; pairwise separation ≥ packed box extents + gap). Delete the test-local `ringRadius`. |
| `hierarchyLinksPullNestedAnchorsTowardTheHierarchyRestLength` | **rewrite**: the HIERARCHY link rest equals the packed parent–child anchor distance (measured max rest error 0.0), and the settled nested distance tracks the packed distance; keep `nestedDistance < peerDistance`. Exact settled tolerance **to-be-measured** in the implementation. |
| `twoMapWorkspaceSettlesToIdle` | **re-baseline**: measured first-idle **756** with the final configuration (review-6 faithful full-suite run; not in the stored probe). Re-confirm in the implementation environment; keep the following 100-frame `rms <= 0.05` / `max <= 0.10` shape with re-measured thresholds. |
| `largerWorkspacesSeedWiderThanTheMinimumSpread` | adapt; the 200-child fixture spread must still exceed its packed minimum; wall clock ≤ 3 s asserted separately. |
| `smallWorkspaceInitialPositionsAreNotCollapsedIntoTheOrigin` | keep shape; re-run. |
| `firstStepDoesNotTeleportSeededParticlesOntoTheirNeighbours` | **keep the hard `< 8.0` per-step bound** (passes under the final configuration). |
| `aTopologyChangeDoesNotTeleportRetainedParticles` | **keep the hard `< 8.0`/step bound** (passes; measured in the faithful full-suite run). The large-reorder fixture is recorded separately (§8.3.5), not asserted. |
| `capAggregateCrossMapFanOutDisplacementOncePerParticle` | unchanged (cross-map budget semantics untouched); re-run. |
| `produceIdenticalFramesForEqualRequests` | **keep**; equal-request determinism must hold; re-run. |
| `acceptRelationshipAndHierarchyEdgesBetweenTheSameAnchors`, `exposeEveryEnclosureAnchor`, `retainHierarchyAnchorsAcrossSteps`, `returnToBaselineCoverageAfterOneHundredAddRemoveCycles`, `observeDirectNodeContainmentInfluenceOnEnclosureAnchorCoordinates`, `nativeRepulsionDisplacementsSumToZeroWithoutDrift`, `settleRelationshipFreeNestedBoundaryProjectionToIdle` | adapt/re-run; re-baseline coordinates/idle only where a recorded measurement changes. Delete `ringRadius` (line ~714) and any constants left unused. |

`BoundarySeparationShould`:

| test | disposition |
|---|---|
| `siblingBoundariesSeedWithoutOverlap` | **rewrite**: at the engine frame, sibling boxes (label-sized footprints for the empty-enclosure fixture) satisfy `max(boxGapX, boxGapY) >= SIBLING_GAP − CANDIDATE_EPSILON`; drop the ring-radius span math. |
| `nestedGroupRingsDoNotInterleave` | **rewrite** to the packed contract: packed sibling child boxes separate on at least one axis by ≥ gap; drop `ringRadius` + `maximumReach`. |
| `rootFramesSeparateOnTheTopRing` | **rewrite**: top-level root footprints separate on at least one axis by ≥ gap; drop the ring/`reachOf` computation. |
| `settledSiblingBoundariesRemainSeparated` | re-run with the settled contract; non-overlap after 1500 steps; report settled gaps. |
| `pinnedNodesKeepTheirForcedPositions`, `pinnedFixtureSettlesWithPinPositionUnchanged` (pin `(1059, −145)`), `unpinTransitionSettlesAfterFormerPinReleased` | re-baseline idle/position goldens with recorded measurements; pin coordinates must stay exact. |
| `repeatedSettlesAreDeterministic` | re-run; determinism must hold. |
| `directNodesLieInsideTheirParentHullAfterGeometryComputation` | re-baseline with recorded measurements. |
| `directParentChildAnchorsSatisfyTheProximityInvariantAfterSettling` | **replace** `distance <= boundaryRadius(parent) − 16` with the per-axis footprint containment check using the measured tolerance (reported, not asserted at 4 units). |
| `siblingAnchorsRemainSeparatedAfterSettling`, `grandchildAnchorsAreExcludedFromBoundaryRepulsion`, `coincidentParentChildAnchorsStayExcluded`, `reparentedBoundaryRefreshesAncestorExclusion` | rewrite the `ReferenceRepulsionFixture.boundaryRadius`-based limits to footprint/extent-based equivalents with measured bounds; re-run. |
| `reproducedCase*` family | re-run; re-baseline idle/RMS/delta goldens with recorded measurements. |
| test-local `ringRadius`/`maximumReach`/`reachOf` static helpers (and `widthOf`/`heightOf` if unused after the rewrite) | **delete** (see §6). |

`ReferenceRepulsionFixture`: delete `Sizes`, `boundaryRadius(...)`, `MAX_RENDERED_NODE_RADIUS` and the
constants that only they use; keep the projection builders (`referenceProjection`,
`reparentedProjection`, node/hull accessors). Re-run all consumers.

Full-suite blast radius (review-6, faithful configuration = overlay + `CONTAINMENT=1.0`, 1074 tests):
the change-induced failures are exactly four —
`BoundarySeparationShould.siblingAnchorsRemainSeparatedAfterSettling`,
`TypedForcesShould.twoMapWorkspaceSettlesToIdle` (expected 45, actual **756**),
`TypedForcesShould.hierarchyAnchorsSeedOnTheGroupRing`, and
`TypedForcesShould.hierarchyLinksPullNestedAnchorsTowardTheHierarchyRestLength` — all four of them
rewrite/re-baseline targets enumerated above. One further change-induced failure is the
calibration-ordering rewrite `GraphStreamBoundaryShould.provideOrderedPositiveCalibrationStrengths`
(1.0 < 0.3); four environmental failures (`ActivatorShould` ×2, `AccessibleGraphCanvasShould` ×2) fail
identically on production. `LayoutWorkerShould`, `BoundarySeparationCorrectionShould`,
`GraphWorkspaceModelAcceptanceShould`, `GraphWorkspaceColdReloadShould`,
`GraphWorkspaceLiveNodeRefreshShould`, `PerformanceTripwiresShould` and the remaining 80 `*Should`
classes pass unchanged. The implementation must re-confirm this exact set in the implementation
environment and record it in its evidence; the post-change suite must pass.

### 7.4 Test package placement

The three new tests live in
`freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/` with
`package org.freeplane.plugin.graph.layout.graphstream;`. This gives direct (non-reflective) access to
the package-private `BoundarySizes`, `ClusterPacking` and nested types while still using the public
engine/worker/geometry API. Fixtures A–H are defined in `ClusterProximityShould` (fixture A is
re-created identically to `ReferenceRepulsionFixture.referenceProjection(1L)`; the local fixture
definition of §7.2 is authoritative for the test). No existing test moves package.

---

## 8. Acceptance procedure and evidence recording

### 8.1 Documented endpoint and instruments

- **Initial frame**: `GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())` +
  `LayoutEngine.apply(request)` — the seeded engine frame before `BoundarySeparationCorrection`.
- **Settle endpoint**: run `LayoutWorker` (`LayoutCalibration.spikeDefaults()`) to the first idle
  frame; if no idle within **2000** steps, the 2000th step; then always **200** extra steps. (The
  stored prototype loops to 2500 only because it predates the budget; the production test uses 2000.
  All A–H idle ≤ 785, so no stored number changes.)
- **Stability window**: the **1000** steps after the documented endpoint.
- **Positions**: `LayoutWorker` frames.
- **Painted hulls**: `GraphGeometryEngine.computeHulls(projection, positions, metrics)` with
  `AwtGeometryTextMetrics(new Font("Dialog", Font.PLAIN, 12), new FontRenderContext(null, true, true))`,
  headless.
- **Gap between two convex hulls**: the maximum separating-axis gap (positive when disjoint) —
  the `ContactProbe.hullDistance` / `BoundaryInvariantAssertions` convention.
- **Crossing**: a non-ancestor enclosure pair with negative separation. Ancestor/descendant pairs are
  nested by construction and are excluded (`BoundaryInvariantAssertions.related` semantics).
- **Containment overflow**: the maximum per-side painted-hull bbox excursion beyond the footprint
  interval `anchor ± (halfExtent, halfExtent) − anchored offsets`, measured relative to the settled
  anchor.
- **Marginal overlap**: a non-ancestor pair whose settled footprint-box force gaps are both
  negative (`forceGapX < 0 ∧ forceGapY < 0`), i.e. the settled analogue of §3.6's box force.

### 8.2 Asserted vs reported

| Criterion | Status | Evidence |
|---|---|---|
| Initial frame equals `ClusterPacking` output exactly at the engine frame for A–H **and generated projections**; deterministic; content at anchor; correct top-level membership; single/empty cases | **asserted** | `ClusterProximityShould.initialFrame*`, `ClusterProximityShould.generatedProjectionsComposeIdenticallyAndDoNotOverlap`, `ClusterPackingShould`, `BoundaryFootprintShould` |
| No pair overlap `max(boxGapX, boxGapY) >= −CANDIDATE_EPSILON`; contact gutter `[SIBLING_GAP − CANDIDATE_EPSILON, SIBLING_GAP + CONTACT_GAP_TOLERANCE]` | **asserted** | `ClusterPackingShould`, `ClusterProximityShould` |
| Pinned fixture C: engine-level packing equality; worker-path same footprint non-overlap/gutter check as the unpinned fixtures plus pin preserved (no worker-path packing equality) | **asserted** | `ClusterProximityShould.initialFrameThroughWorkerWithPinOnlyChecksNonOverlap` |
| Packed-position separation-box invariant for non-ancestor pairs | **asserted** | `BoundaryFootprintShould.packedFootprintBoxesDoNotOverlapAcrossTheFixtureSet` |
| Every fixture reaches idle within the 2000-step budget (measured 22–785) | **asserted** | `ClusterProximityShould.settledFixturesReachIdleWithinBudgetAndDoNotCross` |
| Zero all-pairs non-ancestor painted crossings at the documented endpoint (measured 0) | **asserted** | same |
| 200-child wall clock ≤ 3 s | **asserted** | `ClusterPackingShould.twoHundredChildrenPackWithinThreeSeconds` |
| Enumerated existing tests re-baselined; full `freeplane_plugin_graph` suite and project suite pass | **asserted** | §7.3 and the implementation evidence |
| Settled held deviation from packed, painted gaps, 1000-step window maxima, containment overflow, settled marginal overlaps, large-reorder motion | **reported** | `ClusterProximityShould` printed evidence block; §8.3 |
| Label crowding | **user-review gate** | §8.5 |

No settled hold band is asserted: the 1000-step window max hold deviation reaches 78.7 on fixture D.
The top-level drift on relationship fixtures is an accepted consequence of scope decision A and is
documented in the evidence.

### 8.3 Recorded measurements (canonical; provenance stated)

**8.3.1 Stored artifact provenance**

| Artifact | Producer | Configuration | Covers |
|---|---|---|---|
| `probe-baseline.txt` | `ClusterProbe` against production classes | production | baseline A idle 439 / gap 682.8; B idle 343 / gap 2704.4 |
| `probe-final.txt` | `ContactProbe` against the stored option-A overlay | `-Dproto.boundary=box -Dproto.packingMultiplier=0.3 -Dproto.containment=1.0 -Dproto.margin=8` (README command) | fixtures A–H, reorder, 200-child timing; reproduces byte-identically except the timing line (review-6) |
| `probe-containment-sweep.txt`, `probe-cap-sweep.txt`, `superseded-v2/` | historical | superseded | not cited for any load-bearing number |

**8.3.2 Fixture measurements at the documented endpoint (`probe-final.txt`)**

| Fixture | Idle | Named pair(s) | Packed anchor distance | Settled anchor distance | Window max hold deviation | Painted gap | Min separation |
|---|---|---|---|---|---|---|---|
| A `root > zfc > axioms + definitions` | 42 | `Axioms`↔`Basic Definitions and Theorems` | 84.7 | 84.6 | 4.6 | 36.0 | 36.0 |
| B two maps | 28 | `Map a`↔`Map b` | 137.0 | 140.9 | 14.6 | 31.1 | 31.2 |
| C two maps + relationships + pin `(60,20)` | 785 | `Map a`↔`Map b` | 135.1 | 147.2 | 12.1 | 0.0 | 0.0 |
| D two maps + relationships | 428 | `Map a`↔`Map b` | 135.1 | 147.2 | 78.7 | 35.7 | 35.7 |
| E dual enclosure | 23 | `child group`↔parent anchor | 79.7 | 81.4 | 2.1 | nested | — |
| F four maps | 135 | `m3`↔`m2` / `m3`↔`m1` / `m1`↔`m0` | 138.3 / 135.4 / 127.7 | 151.2 / 151.8 / 127.8 | 27.2 | 37.5 / 19.7 / 25.4 | 19.7 |
| G one map four sibling subgroups | 28 | adjacent groups | 94.0 | 93.6–103.9 | 9.9 | 18.6–34.2 | 18.6 |
| H root + direct nodes + two nested sub-clusters | 22 | child/grandchild pairs | 78.0 | 78.6–79.3 | 2.1 | nested | 83.5 |

All-pairs non-ancestor crossings: **0** on every fixture at the endpoint. Window max hold deviation
over A/B/C/E/G/H is **2.1–14.6** (E 2.1, H 2.1); D 78.7 and F 27.2 are the drift outliers. This
matches the current design prose ("2.1–14.6 on A/B/C/E/G/H"); the earlier "4.6–14.6" range that
excluded E/H was corrected before the design was finalized (review-6 finding 6 is resolved in the
current design).

**8.3.3 Containment overflow and settled marginal overlaps (`probe-final.txt`)**

| Fixture | Painted per-side overflow beyond the footprint | Enclosure |
|---|---|---|
| A | 8.1 | `ZFC` |
| B | 5.7 | `Map b` |
| C (pinned) | 14.0 | `Map a` (the pin's displacement propagates into the neighbouring unpinned map) |
| D (relationships) | 5.9 | `Map b` |
| E | 0.0 | — |
| F | 3.7 | `Map m3` |
| G | 0.2 | `Map g` |
| H | 0.0 | — |

Settled marginal overlaps (box gaps both negative): A −0.1 (x), G −0.4 and −0.2 (x), B/C/D/F 0;
all within the ≤ 0.5 expectation. The margin is a packing budget, not a containment guarantee.

**8.3.4 Values derived from the stored rows**

- Direct-node drift at `containment() = 1.0`: **2.3–13.3** (minimum H +2.3, maximum C `a fundamentals`
  +13.3), derived from the `probe-final.txt` drift column. The design's lower bound "2.2" is not
  reproducible from the stored artifact → **to-be-measured** if it is to be cited.
- Single-child settled distances, derived from the stored anchor rows:
  B `Map a`↔`a fundamentals` **9.3**, D **11.1**, F **10.1–12.1** (derived from the printed
  one-decimal anchor rows; review-6 computed the same range from the unrounded positions). Design
  §6.1 and review-6 agree on 9.3–12.1.
- `containment() = 0.15` direct-node drift (the calibration baseline): **+7.3…+44.4**; measured by
  review-6 with the v7 overlay (`-Dproto.containment=0.15`), **not stored** in the artifacts →
  provenance = review-6 finding 2.

**8.3.5 Reorder and timing (`probe-final.txt`)**

Large reorder (settle 4 maps, then submit a 5th larger map):

| Retained anchor | Apply-frame move | Next-step move |
|---|---|---|
| `m0 fundamentals` / `Map m0` | 0.0 / 0.0 | 0.1 / 0.7 |
| `m1 fundamentals` / `Map m1` | 24.1 / 24.1 | 0.0 / 0.0 |
| `m2 fundamentals` / `Map m2` | 17.1 / 17.1 | 0.1 / 0.2 |
| `m3 fundamentals` / `Map m3` | 245.9 / 245.9 | 122.3 / 142.1 |
| totals | `applyMax 245.9`, `applySum 574.2` | `stepMax 142.1` |

The large reorder is **recorded, not asserted**. Review-6 additionally measured post-reorder idle 384
and eventual retained drift 353.8 for this fixture (not stored in `probe-final.txt`); these belong to
the report, not to a bound. The asserted small-change fixture
(`aTopologyChangeDoesNotTeleportRetainedParticles`) keeps `< 8.0` units/step and passes.

200-child timing: **submit (apply + packing) 0.580 s, 10 steps 1.252 s, anchors 201**; review-6
re-measured 0.37–0.58 s submit. Machine/JVM: Java 21.0.8-zulu, headless. The acceptance assertion is
≤ 3 s.

**8.3.6 Baseline (`probe-baseline.txt` plus review-6 supplement)**

| Fixture | Baseline painted gap (named pair) | Baseline idle |
|---|---|---|
| A | `Axioms`↔`Basic Definitions and Theorems` **682.8** | 439 |
| B | `Map a`↔`Map b` **2704.4** | 343 |
| C (pin + relationships) | **1896.2** | never idle within 2000 |
| D (relationships) | **2204.5** | 184 |

Rows A/B are in `probe-baseline.txt`. Rows C/D were reproduced by review-6's `BaselineGapProbe`
against production classes (**not stored** in the artifact; provenance = review-6 finding 7). The
baseline root cause (label-based estimate ≈ 10× the painted size and top-ring radius ≈ 1434 putting
maps ≈ 2831 apart) is recorded in `probe-baseline.txt` (`estW`/`estRad` columns).

**8.3.7 Review-measured values not stored in `probe-final.txt`/`probe-baseline.txt`**

These are cited only as provenance-labelled context; any that become assertions are to-be-measured:

| Value | Provenance |
|---|---|
| C/D baseline gaps/idles above | review-6 `BaselineGapProbe` |
| `containment=0.15` drift +7.3…+44.4 | review-6 v7 overlay run |
| Engine-frame exactness for A–H (max anchor delta 0.0; single/empty synthetics) | review-6 `InitProbe` |
| HIERARCHY rest equals packed distance (max error 0.0) | review-6 `InitProbe` |
| Packed-position separation-box invariant: 0 violations on A–H and 20 075 non-ancestor pairs | review-6 `PackProbe`/`InitProbe` |
| Packer fuzz: deterministic, no overlap, contacts in [8, 8.75], min contact gap 7.999999999999972, no fallback | review-6 `PackProbe` (2 000 rounds, 12 294 contacts) |
| Reorder idle 384; retained drift 353.8 | review-6 `ReorderIdleProbe` |
| `twoMapWorkspaceSettlesToIdle` idle 756 | review-6 faithful full-suite run |
| Synthetic single-child settle 5.5–19.8 | review-6 `SingleChildProbe` |
| Full-suite blast radius (1074 tests; 4 change-induced + 1 calibration + 4 environmental failures) | review-6 |

### 8.4 Evidence recording

The implementation task records, in its task evidence file:

1. the exact commands and configuration used (Gradle test task; `LayoutCalibration.spikeDefaults()`);
2. the full stdout of `ClusterProximityShould` (the §7.1.3 evidence block) and
   `ClusterPackingShould.twoHundredChildrenPackWithinThreeSeconds`;
3. the full-suite result (module suite and project suite), including the enumerated §7.3 tests;
4. a short reported-vs-asserted summary following §8.2, quoting the values from §8.3.2–8.3.5 (no value
   from a superseded overlay);
5. the label-gate measurements of §8.5.

The committed prototype artifacts (`probe-final.txt`, `probe-baseline.txt`) must not be edited; the
new evidence is written by the implementation task, not by this spec.

### 8.5 Label readability gate

- Capture real-application screenshots at both levels (fit-all and reading zoom) and a headless
  measurement from `ScreenLabelPlacement` (overlapping label pairs, leader length vs footprint,
  maximum leader length), on the A–H fixture set.
- Before implementation, record the same measurements under production (baseline); after, record the
  post-change set; report both and the delta.
- The criterion is an explicit **user-review gate** with no hard numeric pass/fail threshold: if
  crowding materially exceeds the recorded baseline, decision 3 (no world-space label allowance) is
  re-opened with the user. Any mitigation must be screen-space, never a world-space label reservation.

### 8.6 Mockups and accepted user choices

- `cluster-packing-workspace.png` and `cluster-packing-siblings.png` in
  `docs/superpowers/specs/mockups/2026-10-13-graph-cluster-initial-proximity/` were regenerated with
  the production packer (`cluster_packing_mockups.py`: edge candidates, assembly-box edges, centre
  lines, minimum resulting bounding-box area, no re-centring). The committed script's packer yields a
  1144×260 workspace arrangement (the superseded polar original was 1146×260) and a 508×130 sibling
  arrangement.
- User decisions: (1) tight packing at both levels; (2) variant V1; (3) no label allowance;
  (4) initialization only, no holding mechanism (2026-10-13).

---

## 9. Traceability

### 9.1 Design sections

| Design section | Spec section |
|---|---|
| §1 Background | §1.1, §2.4–2.5 |
| §2 Problem + baseline evidence | §1.1, §8.3.6 |
| §3 Goals + non-goals | §1.1, §1.2 |
| §4.1 Anchor-centred per-axis footprints | §2.2, §3.1 |
| §4.2 Deterministic V1 packing | §2.3, §2.4, §3.2, §3.3, §3.4 |
| §4.3 Hierarchy links with packed rests | §3.5 |
| §4.4 Offset-aware anchor separation | §2.6, §2.7, §3.6 |
| §4.5 No holding mechanism | §1.2 |
| §4.6 Calibration + legacy removal | §2.1, §2.4, §4, §6 |
| §5 Data flow | §3.7 |
| §6 Edge cases | §5, §7.2 |
| §7 Measured evidence | §8.3 |
| §8 Mockups + user choices | §1.2, §8.5, §8.6 |
| §9 Test strategy | §7 |
| §10 Risks + mitigations | distributed risk text: §1.2, §3.5, §4, §7.3, §8.2–§8.3, §8.5 |
| §11 Acceptance criteria | §1.3, §8.2 |
| §12 Prototype artifacts | §8.3.1, §8.4 |

### 9.2 Load-bearing review findings

| Review | Finding (gist) | Resolution in this spec |
|---|---|---|
| R1#1 | footprint does not bound the settled hull | Scope A: initialization asserted, settled containment reported (§1.2, §8.2, §8.3.3) |
| R1#2 | packed rests are not held by the force model | Scope A: no holding mechanism; settled drift reported (§1.2, §3.5, §8.3.2) |
| R1#3 | packing under-specified / differs from the approved mockup | Exact candidate set + centre lines + tie-breaks + regenerated mockups (§3.2, §8.6) |
| R1#4 | content item vs re-centring / symmetric extents | No re-centring; anchor offsets; offset-aware box separation (§3.1, §3.6) |
| R1#5 | rest-length lifecycle not implementable | Rests recomputed from deterministic packing each synchronize; no per-particle state (§3.5) |
| R1#6 | single-child contradiction; top-level membership | Single-item/origin/rest rules; exact `isTopLevel` predicate (§3.1, §3.4, §5) |
| R1#7 | acceptance not measurable; retained-particle claim | Documented endpoint, instruments, pair sets, asserted-vs-reported (§8.1–8.2) |
| R1#8 | evidence did not exercise the mechanism; probe not reproducible | A–H + reorder + timing in `probe-final.txt`; provenance table (§8.3.1) |
| R1#9 | impacted tests under-enumerated | Full class→test→disposition table (§7.3) |
| R1#10 | label-length entry misattributed | Removals name `directNodeReach`/`ringRadius`/`sizeOf`; `contentRingRadius` untouched (§2.4, §6) |
| R1#11 | constant ownership/memoization/complexity | `ClusterPacking` ownership, memoization contract, candidate count, ≤3 s bound (§2.3–2.4, §3.2, §4) |
| R1#12 | label risk / fallback contradicts decision 3 | Screen-space screenshots + headless metrics; user gate; no world-space allowance (§8.5) |
| R2#1 | specified packing ≠ evidence/mockups | Production edge + assembly-box + centre-line candidates; all numbers from `probe-final.txt` (§3.2, §8.3) |
| R2#2 | margin does not bound the settled hull | Reported containment overflow per fixture; not asserted (§8.2, §8.3.3) |
| R2#3 | content-offset rest + containment springs | Rest = `hypot(placement + item.anchor)`; node ring stays around the parent anchor (§3.4, §3.5) |
| R2#4 | ≥4-item workspaces fail/never idle | Fixtures F/G/H cover ≥4 items; no hold band; idle asserted within budget (§7.2, §8.2) |
| R2#5 | 200-child timing unmeasured | Measured 0.580 s / 1.252 s; asserted ≤ 3 s (§4, §7.1.1, §8.3.5) |
| R2#6 | impacted tests + weakened invariants | Enumerated tests, hard movement bounds kept, child-containment replacement (§7.3) |
| R2#7 | uncapped cross-map packing links / re-pack unmeasured | No packing links; reorder recorded, not asserted; small-change bound kept (§3.5, §7.3, §8.3.5) |
| R2#8 | label crowding criterion unmeasurable | Metrics + baseline recording + explicit user gate (§8.5) |
| R2#9 | §7.3 not reproducible / size-only | Canonical table from `probe-final.txt`; per-side offsets (§8.3.2–8.3.3) |
| R2#10 | packing precision gaps (`G`, first item, origin, duplicates, fallback) | `gap` fixed to 8.0; origin frame; dedupe; defensive throw only (§3.2, §5) |
| R2#11 | `hierarchy()` fate / public-type claim | `hierarchy()` retained; `GraphStreamBoundaryShould` rewrite; no public API change (§2.1, §2.8, §7.3) |
| R3#1 | box extents anchor-symmetric while footprints are asymmetric | Offset-aware centres + push away from the box centre (§3.6) |
| R3#2 | zero-contact top-level roots lose their hold | Scope A: no top-level packing links at all (§1.2) |
| R3#3 | re-pack yanks retained clusters; bound undefined | No caps/skeleton; small-change `< 8.0` kept; large reorder recorded (§7.3, §8.3.5) |
| R3#4 | acceptance not executable (pairs, E, exemptions, crossing) | Enumerated pair sets, E hold-only/nested, C exemption, non-ancestor crossing (§8.1–8.2, §7.2) |
| R3#5 | impacted-test enumeration incomplete | §7.3 names `BoundarySeparationShould` tests, `reproducedCase*`, `BoundaryInvariantAssertions`, mirrors |
| R3#6 | design claims contradicted the raw probe output | **carried forward, resolved at R6#2**: canonical `probe-final.txt` table (§8.3) |
| R3#7 | flush candidates missing; dead fallback kept | Centre lines included; defensive throw only; no fallback test (§3.2, §5) |
| R3#8 | 200-child "recorded" measurement absent | Stored 0.580 s/1.252 s + review-6 confirmation (§8.3.5) |
| R3#9 | approved mockup was the polar arrangement | Mockups regenerated with the production packer (1144×260 / 508×130) (§8.6) |
| R3#10, R3#12 | C overflow misattributed; single-child range stale | `Map a` named; 9.3/11.1/10.1–12.1 derived from `probe-final.txt` (§8.3.4) |
| R3#11 | probe settle loop 2500 vs the 2000-step budget | **carried forward, resolved at R6#9**: production test uses 2000 (§8.1) |
| R5#1 | caps 1.5/1.5 never converge after reorder | Caps deleted (scope A) (§1.2) |
| R5#2 | first idle + 200 samples a transient; hold later exceeds 4.0 | No hold band; window drift reported; idle + crossings only asserted (§8.1–8.2) |
| R5#3 | stored command did not reproduce `probe-final.txt` | Final configuration pinned; provenance table (§8.3.1) |
| R5#4 | frozen-rest rationale unsupported | No frozen rests; rests recomputed; retained motion reported (§3.5, §8.3.5) |
| R5#5 | numbers from superseded overlays | Canonical values from `probe-final.txt`; superseded artifacts not cited (§8.3) |
| R5#6 | acceptance enumeration gaps / probe–procedure mismatches | **carried forward, resolved at R3#4**: pair sets, C exemption, non-ancestor crossing definition, all-pairs assertion (§7.2, §8.1–8.2) |
| R5#7 | reparenting left frozen rests inconsistent | No frozen rests (§3.5) |
| R5#8 | mockup script documentation stale | **carried forward, resolved at R6#10**: `cluster_packing_mockups.py` docstrings already updated at the design commit (§9.2 R6#10) |
| R5#9, R6#12 | packed-position invariant not stored / not asserted | `BoundaryFootprintShould.packedFootprintBoxesDoNotOverlapAcrossTheFixtureSet` (§7.1.2) |
| R6#1 | `GraphStreamBoundaryShould` disposition wrong; criterion 5 unachievable | Explicit rewrite `hierarchy() < containment() <= sameMap()` = (0.30, 1.0, 1.0); idle 756 recorded (§7.3) |
| R6#2 | superseded v6/v4 numbers quoted | Canonical table: E 81.4, H 78.6–79.3, B 0 overlaps, drift max 13.3, timing 0.580/1.252 (§8.3) |
| R6#3 | criterion 1 engine-vs-worker ambiguity (pin 380.45) | Engine-frame assertion path + pinned-fixture exemption (§7.1.3, §8.2) |
| R6#4 | push direction described as "away from the other anchor" | Push away from the other footprint-box centre (§3.6) |
| R6#5 | "exact gutter" has no tolerance | `[SIBLING_GAP − CANDIDATE_EPSILON, SIBLING_GAP + CONTACT_GAP_TOLERANCE]` and `max(boxGapX, boxGapY) >= −CANDIDATE_EPSILON` (§3.3, §4) |
| R6#6 | drift prose excluded E/H (earlier revision) | Current design and `probe-final.txt` agree: window range 2.1–14.6 (§8.3.2) |
| R6#7 | C/D baseline not reproducible from the stored probe | Marked review-6 `BaselineGapProbe` provenance (§8.3.6) |
| R6#8 | newly dead members left in the removal list | Added `sizeOf`, `Size`, `MAX_RENDERED_NODE_RADIUS`, `TypedForcesShould.ringRadius` (+ transitively dead depth helpers) (§6) |
| R6#9 | probe loop 2500 vs the 2000 budget | Production test uses 2000 (§8.1) |
| R6#10 | mockup docstring describes the superseded algorithm | **resolved at the design commit**: `cluster_packing_mockups.py` already documents the production v7 rule (edge-aligned candidates, assembly-box edges, centre lines, minimum area; "assembly is not re-centred") |
| R6#11 | "0–14 units, 7.3" mixed two metrics | Containment range 0–14.0 only; direct-node drift quoted separately (§8.3.3–8.3.4) |

---

## 10. Open items / to-be-measured

1. **`twoMapWorkspaceSettlesToIdle` idle = 756** is review-6-measured, not stored. Re-measure in the
   implementation environment and set the constant accordingly; keep the 100-frame idle-metric shape
   with re-measured thresholds.
2. **`hierarchyLinksPullNestedAnchorsTowardTheHierarchyRestLength`** settled tolerance is not in the
   stored artifacts; the rest-equals-packed-distance identity (max error 0.0) is the primary assertion;
   the settled tolerance is to-be-measured.
3. **`containment() = 0.15` drift max +44.4** and **synthetic single-child 5.5–19.8** are
   review-6-measured only; quote with provenance or re-measure, do not present as stored.
4. **Baseline C/D rows** (1896.2 / 2204.5, idle −1 / 184) are review-6-measured only; if the baseline
   is to be committed to an artifact, extend `ClusterProbe`/regenerate `probe-baseline.txt` first.
5. **Full-suite failure set re-confirmation:** §7.3 names the four change-induced failures plus the
   calibration-ordering failure and the four environmental ones (review-6's faithful full-suite run).
   The implementation must re-confirm this exact set in the implementation environment and record it
   in its evidence; the post-change full suite must pass.
6. **Label gate:** no numeric threshold is defined by the design; the baseline must be recorded before
   the change and the delta is reviewed by the user (§8.5).
7. **`mapRoot()`-with-`parentHull()` enclosures** are top-level for seeding but also placed in their
   parent's assembly and linked by hierarchy rest; this preserves the existing `Seeds.isTopLevel`
   predicate and is not exercised by A–H. Confirm with the user if such projections can occur.
8. **`BoundarySeparationShould` pin-test idle/position goldens** (`(1059, −145)` fixture) are not in
   the stored artifacts; re-baseline with recorded measurements.
9. **`directNodesLieInsideTheirParentHullAfterGeometryComputation`** and the `reproducedCase*` family
   need their re-baselined values recorded in the implementation evidence; the reviews did not publish
   them.
