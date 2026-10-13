# Graph Workspace — Initial Cluster Proximity (design)

- Date: 2026-10-13
- PM run: `pm-run-20261013-111126-b33a54c6` (topic `graph-cluster-initial-proximity`)
- Base commit: `9b8259bcd1cb2c8d37121e63bcac0107386fed0d` (`refs/heads/plugin/graph-workspace`)
- Status: v7 — **scope decision (user, 2026-10-13): initialization only.** Reviews 1–5 progressively
  falsified every attempt to *hold* the settled arrangement (force symmetry, convergence, transient idle,
  relationship-spring drift); the user chose scope A: ship the tight initialization, keep the existing
  force model, and report the settled behaviour honestly. All numbers below are measured by the prototype
  in section 12.

## 1. Background

A cluster is a boundary enclosure (hull): the shape that wraps a group of nodes and nested hulls. Two
mechanisms position clusters:

1. Seeding — `GraphStreamLayoutEngine.Seeds` places enclosure anchors: top-level map roots with
   `topRingPosition` (one circle radius from the largest root footprint), nested siblings with
   `BoundarySizes.ringRadius` (the circumradius formula per parent).
2. Settling — `TypedSpringBox`/`TypedNodeParticle` move particles. Anchor separation is a circle push
   whose extent is `BoundarySizes.boundaryRadius` = half-diagonal of the enclosure's estimated footprint;
   each child anchor is pulled to its parent by a hierarchy link with the fixed rest length
   `GROUP_SPACING = 100.0` (depth ≤ 1) or `SUB_GROUP_SPACING = 60.0` (deeper).

`BoundarySizes.sizeOf` builds the footprint from label lengths: `directNodeReach` uses node-box reaches
derived from `CHAR_WIDTH_UPPER_BOUND = 16.0` per character, `ringRadius` derives each child ring from the
maximum child size, and the result is an isotropic square. The painted hull instead comes from
`GraphGeometryEngine`, using node disc radii plus `HULL_CLEARANCE = 16.0`. The overestimate compounds
with nesting, so clusters are seeded far apart and the anchor repulsion keeps them there.

## 2. Problem statement and baseline evidence

The user reports clusters "spaced too far apart" and asks that they be initialized as close together as
possible, at both levels (separate maps and sibling sub-clusters inside one map).

Baseline, production classes (probe in section 12; settled = first idle else 2000 steps, then 200 extra;
painted hulls from `GraphGeometryEngine` + `AwtGeometryTextMetrics`; gap = convex-hull separation):

| Fixture | Settled painted gap (named pair) |
|---|---|
| A `root > zfc > axioms + definitions` | `Axioms`↔`Definitions` **682.8** |
| B two maps, heterogeneous subgroups | `Map a`↔`Map b` **2704.4** |
| C B + 3 cross-map relationships + node pin | **1896.2** (never idle within 2000) — reproduced by the review-6 `BaselineGapProbe`; `ClusterProbe` covers A/B only |
| D B + 3 cross-map relationships | **2204.5** (idle 184) — same provenance |

Measured causes:

- The label-based estimate overstates linear size by ~10× (A: 840 vs 63 painted) and compounds through
  nesting; the top ring radius `(2020.7 + 8)·√2/2 ≈ 1434` puts maps ~2831 apart while their painted
  hulls are ~120 wide.
- Anchors settle where the separation force balances (`boundaryRadius` sum + `SIBLING_GAP` ≈ 719.4 in A),
  not where the painted hulls are. Native repulsion and (for nested children) the fixed 60/100 rest
  lengths keep the seeded spread.

## 3. Goals and non-goals

### Goals

- **Initial layout:** every workspace is seeded with the smallest footprints the layout can justify,
  separated by `SIBLING_GAP = 8.0` plus a measured settle margin, at both levels; deterministic and
  exact for all-new particle sets.
- The existing post-geometry `BoundarySeparationCorrection` keeps painted hulls from crossing at every
  frame; the settled arrangement is measured and reported.
- Minimal change surface: no new force kinds, no displacement caps, no per-pair state, no change to
  native repulsion, pins, relationship semantics, or the label placement pass.

### Non-goals (explicit scope decision)

- **Holding the packed arrangement through settling.** Reviews 1–5 measured: packed rest lengths alone
  stretch under native repulsion; a contact-graph skeleton holds two-item fixtures but not ≥3-item
  arrangements; stiff packing links re-inflate; per-step caps break force symmetry (non-convergence at
  1.5/1.5, motion-bound violation at 2.0/2.0); "idle" is a transient and relationship fixtures keep
  moving. The user chose to ship the initialization and report settled drift rather than keep iterating.
- Reserving label space in the layout (user decision 3).
- Keeping the arrangement compact when nodes are pinned far from their cluster.
- Tight settled bounds: settled numbers are reported with measured ranges, not asserted as guarantees.

## 4. Design

All changes live in `org.freeplane.plugin.graph.layout.graphstream` (`GraphStreamLayoutEngine`,
`TypedSpringBox`, `TypedNodeParticle`) plus `LayoutCalibration`. `ClusterPacking` is a new
package-private type in the same package. No GraphStream type enters a public signature.

### 4.1 Anchor-centred per-axis footprints

The anchor of an enclosure is the centre of its direct-node content (as today: `Seeds.nodePosition` rings
direct nodes around `center(key)`). The content item is always placed at the anchor origin; the assembly
is **not** re-centred, so a footprint may be asymmetric about the anchor and carries an anchor offset.

`BoundarySizes` computes a `Footprint { width, height, anchorX, anchorY }`:

- Direct node content: `ringRadius = contentRingRadius(key)` (disc radii +
  `NodeSeparationProjection.MIN_GAP`, unchanged), `maxDiscRadius = NODE_RADIUS · prominence.scale()`;
  half-extent `ringRadius + maxDiscRadius + NODE_SETTLE_MARGIN`; centred on the anchor
  (`anchorX = anchorY = 0`).
- Children: each direct child contributes its own `Footprint`; 4.2 positions them.
- Footprint = bounding box of (content ∪ packed children) expanded by `FRAME_CLEARANCE = 16.0` per side;
  `anchorX/anchorY` is the anchor position relative to the box centre (`bboxCentre = anchor − anchorX/anchorY`).
- Empty enclosure: the existing label-sized box; the only remaining use of `CHAR_WIDTH_UPPER_BOUND`,
  `CHAR_HEIGHT_UPPER_BOUND`, and `BOUNDARY_PADDING`.

`NODE_SETTLE_MARGIN = 8.0` and `LayoutCalibration.containment() = 1.0` are the calibrated pair (4.6).
The margin is a packing budget; settled content can exceed the footprint (measured per-side overflow
0–14.0 units on the fixture set; 7.3), and containment is reported rather than guaranteed.

### 4.2 Deterministic V1 packing (production algorithm)

`ClusterPacking` is the single placement authority for seeding and footprints (and records contacts as
packing metadata for tests). It is pure; `BoundarySizes` memoizes placements and footprints so the
recursion footprint → placement → child footprint evaluates each enclosure once.

Items: `[content item (if the enclosure has direct nodes)] + direct child footprints`. Top level: all
enclosures with `mapRoot() == true` **or** no parent hull (`Seeds.isTopLevel`).

Algorithm:

1. Order: content first; remaining items by descending footprint area, ties by projection order.
2. First item at the origin. For each next item, candidates are the full Cartesian product `X × Y` of:
   - for every placed item `p`: `p.x ± (p.w + w)/2 + gap`, `p.y ± (p.h + h)/2 + gap`, and the centre lines
     `p.x`, `p.y`;
   - the assembly box: `minX + w/2`, `maxX − w/2`, `minX − w/2 − gap`, `maxX + w/2 + gap` (likewise y).
3. A candidate is clear when separated from every placed item by at least `gap` on at least one axis.
   Selection: minimum resulting assembly bounding-box area, ties by smaller distance from the origin,
   ties by candidate enumeration order (ascending `X` then ascending `Y`, duplicates removed).
4. Contacts: after placing an item, every pair separated by `gap` (tolerance 0.75) on one axis while
   overlapping on the other is recorded for tests (no force uses them).
5. No fallback scan is needed: with a non-empty placed set, `x = minX − w/2 − gap` is always present and
   always clear. `packItems` keeps a defensive `IllegalStateException` only.
6. No re-centring: placement coordinates are the anchor frame.

`gap = SIBLING_GAP = 8.0`. Complexity is `O(n²)` candidates per item with a grid-indexed clearance test
and incremental bounding-box maintenance; measured on the prototype's naive implementation, the 200-child
fixture takes 0.580 s for submit (apply + packing) and 1.252 s for 10 steps; the plan asserts ≤ 3 s.
Acceptance tolerance: contact gaps are `SIBLING_GAP − 1e-9 ≤ gap ≤ SIBLING_GAP + 0.75` (double rounding
reaches 7.999999999999972).

`Seeds` consumes placements lazily per particle: only new anchors are seeded; node seeds stay on the ring
around the parent anchor.

### 4.3 Hierarchy links with packed rest lengths

The existing hierarchy link topology (parent anchor ↔ child anchor) is kept, but each link's rest length
becomes the **packed parent–child anchor distance** instead of `GROUP_SPACING`/`SUB_GROUP_SPACING`:

- For every enclosure with a parent, one link is added from the parent anchor to the enclosure anchor with
  rest `hypot(placement.x + item.anchorX, placement.y + item.anchorY)`.
- Top-level roots have no hierarchy links (as today).
- Rest lengths are recomputed from the deterministic packing on every synchronize (no per-particle state,
  no frozen map); the existing `hierarchy()` multiplier `0.30` is unchanged.
- `GROUP_SPACING` and `SUB_GROUP_SPACING` are deleted; `ForceKind.HIERARCHY` stays.

Effect: nested children no longer settle into the old radius-60/100 hollow rings; they settle near their
packed parent distance (measured A `ZFC`↔`Axioms` 84.6 vs packed 84.7). Top-level maps and sibling sets
are not spring-held against native repulsion, so they drift after settling (7.3) — this is the accepted
scope boundary.

### 4.4 Offset-aware anchor separation (overlap lower bound)

`DesiredParticle.anchor` carries the footprint's per-axis half extents **and its anchor offset**;
`TypedNodeParticle` stores them; `springBox.configureAnchorExtents(id, hw, hh, −anchorX, −anchorY)`.

`TypedSpringBox.addBoundaryRepulsion` tests footprint boxes: with `c₁ = anchor₁ − (anchorX₁, anchorY₁)`
and likewise `c₂`, and `gapX = |c₁.x − c₂.x| − (hw₁ + hw₂ + G)`, `gapY = |c₁.y − c₂.y| − (hh₁ + hh₂ + G)`,
an overlap is both negative and the force `min(−gapX, −gapY) · BOUNDARY_REPULSION_FACTOR` is applied
along the smaller-overlap axis, **away from the other footprint-box centre** (`anchor − offset`), not
the other anchor (equal overlaps resolve along x). Ancestor exclusion and pins are unchanged. At packed
positions the force is zero by construction; at settled positions the soft force plus the idle policy can
leave a marginal overlap ≤ 0.5 units (measured A −0.1, G −0.2/−0.4; B/C/D/F zero), and the post-geometry
`BoundarySeparationCorrection` remains the crossing safety net (all-pairs non-ancestor crossings measured 0).

### 4.5 No holding mechanism

No packing skeleton, contact springs, frozen rest lengths, displacement caps, or new force kinds are
introduced. This is the deliberate scope decision recorded in section 3.

### 4.6 Calibration and legacy removal

`LayoutCalibration.containment()` rises from `0.15` to `1.0` (measured direct-node drift `+7.3…+44.4` at
0.15 versus `+2.2…+13.3` at 1.0; the tight margin depends on it). All other constants (`K2`, `force`,
`hierarchy() = 0.30`, `ATTRACTION_FACTOR`, `REPULSION_FACTOR`, `BASE_SEPARATION_RADIUS`,
`BOUNDARY_REPULSION_FACTOR`, `CROSS_MAP_DISPLACEMENT_LIMIT`) are untouched. Because containment is now
stiffer than hierarchy, the calibration ordering assertion must be rewritten (9.1).

Per the repository's legacy-removal policy: `topRingPosition`, the sibling ring path in `Seeds.center`,
`ringRadius`, `directNodeReach`, `childBoundaryReach`, `reachOf`, `directNodeRingRadius`,
`BoundarySizes.sizeOf`'s isotropic branch, `BoundarySizes.boundaryRadius`,
`BoundarySizes.sizeOf` and its private `Size` holder (no consumer remains), `MAX_RENDERED_NODE_RADIUS`,
`TypedNodeParticle.boundaryRadius`, `hierarchyRestLength`, `GROUP_SPACING`, and `SUB_GROUP_SPACING` are
deleted. `contentRingRadius`, `discRadius`, `SIBLING_GAP`, `FRAME_CLEARANCE`, and the empty-enclosure
label branch stay.

## 5. Data flow

```
projection ──► BoundarySizes (memoized)
                 ├─ ClusterPacking.pack(items) + contacts   → placements (anchor frame), top level
                 │     ├─ footprint(key) = content ∪ packed children + FRAME_CLEARANCE
                 │     └─ Seeds: anchor/node seeds for new particles only
                 ├─ topology(): HIERARCHY links, rest = packed parent–child anchor distance
                 └─ per-axis half extents + anchor offset
                       └─ TypedSpringBox.addBoundaryRepulsion (footprint boxes)
```

Determinism: item order, candidate enumeration, and selection keys are fixed by projection order and
geometric values; no RNG, hashing, or clock input participates.

## 6. Edge cases

1. **Single child**: placed at the anchor origin (offset 0); the hierarchy link rest is 0 and native
   repulsion settles the child 9.3–12.1 units away on the design's fixtures (B 9.3, D 11.1, F 10.1–12.1;
   synthetic single-child probes 5.5–19.8). The child footprint is inside the parent's.
2. **Enclosure without direct nodes**: children pack around the anchor; the hierarchy star links each
   child directly to the parent anchor with its packed distance.
3. **Top level**: membership is exactly `Seeds.isTopLevel`; roots pack against each other with no springs
   (settled drift reported).
4. **Empty enclosure**: label-sized footprint, packed like any item.
5. **Pins**: a pinned particle keeps its pin position; pinned content can exceed the margin (reported).
6. **Cross-map relationships**: unchanged; the cross-map budget keeps its relationship-only meaning.
7. **Topology change / reparenting**: rest lengths follow the new deterministic packing; the existing
   `aTopologyChangeDoesNotTeleportRetainedParticles` fixture keeps its hard < 8.0/step bound (the
   small-change fixture; the large reorder fixture is recorded, not asserted — scope 3).

## 7. Measured evidence (option-A prototype)

Final configuration: `containment = 1.0`, `NODE_SETTLE_MARGIN = 8.0`, `hierarchy()` multiplier 0.30,
packed hierarchy rests, offset-aware separation, production centre-line packing.

### 7.1 Initial frame (asserted)

The seeded frame is exact by construction: every packed pair of footprints is separated by exactly
`SIBLING_GAP` on one axis (or more), the content item sits at the anchor, deterministic order and
tie-breaks, and no seed overlaps. Verified by `ClusterPackingShould` (new) and by the packaged
determinism tests.

### 7.2 Settled measurements (reported)

Documented endpoint = first idle else 2000 steps, then 200 extra steps; "window" = the next 1000 steps.

| Fixture | Idle | Named pair | Packed | Settled | Window max hold deviation | Painted gap | Min separation |
|---|---|---|---|---|---|---|---|
| A | 42 | `Axioms`↔`Definitions` | 84.7 | 84.6 | 4.6 | 36.0 | 36.0 |
| B | 28 | `Map a`↔`Map b` | 137.0 | 140.9 | 14.6 | 31.1 | 31.2 |
| C (pin+rel.) | 785 | `Map a`↔`Map b` | 135.1 | 147.2 | 12.1 | 0.0 | 0.0 |
| D (rel.) | 428 | `Map a`↔`Map b` | 135.1 | 147.2 | 78.7 | 35.7 | 35.7 |
| E | 23 | `child`↔parent | 79.7 | 81.4 | 2.1 | nested | — |
| F | 135 | `m3`↔`m2` / `m3`↔`m1` / `m1`↔`m0` | 138.3 / 135.4 / 127.7 | 151.2 / 151.8 / 127.8 | 27.2 | 37.5 / 19.7 / 25.4 | 19.7 |
| G | 28 | adjacent groups | 94.0 | 93.6–103.9 | 9.9 | 18.6–34.2 | 18.6 |
| H | 22 | child/grandchild pairs | 78.0 | 78.6–79.3 | 2.1 | nested | 83.5 |

All-pairs non-ancestor crossings: **0** on every fixture at the documented endpoint (minimum separations
A 36.0, B 31.2, C 0.0, D 35.7, F 19.7, G 18.6, H 83.5; E has no non-ancestor pair). Post-settle drift is
real and reported (windows up to 78.7 on the relationship fixture D; 27.2 on F; 2.1–14.6 on A/B/C/E/G/H).

### 7.3 Containment budget (reported)

Painted per-side overflow beyond the footprint at the documented endpoint: A `ZFC` 8.1, B `Map b` 5.7,
C `Map a` 14.0, D `Map b` 5.9, E 0, F `Map m3` 3.7, G `Map g` 0.2, H 0. Settled content can exceed the
packing footprint; the margin is a packing budget, and the post-geometry correction keeps hulls apart.

### 7.4 Calibration and timing

- `containment()` 0.15 → 1.0: measured direct-node drift falls from `+7.3…+44.4` to `+2.2…+13.3`.
- 200-child fixture: submit (apply + packing) 0.580 s, 10 steps 1.252 s (naive prototype; production bound
  ≤ 3 s; machine/JVM: Java 21.0.8-zulu, headless).
- Baseline reproduction: `ClusterProbe` against production classes reproduces `probe-baseline.txt`
  (A 682.8, B 2704.4).

## 8. Mockups and accepted user choices

- `cluster-packing-workspace.png` and `cluster-packing-siblings.png` were regenerated with the production
  packer (`cluster_packing_mockups.py`: edge candidates, assembly-box edges, centre lines, minimum-area
  key); the workspace arrangement is a 1144×260 single row (the polar original was 1146×260).
- User decisions: (1) tight packing at both levels; (2) variant V1; (3) no label allowance;
  (4) scope to initialization, no holding mechanism (2026-10-13).

## 9. Test strategy

### 9.1 Impacted existing tests (class → test → disposition)

- `TypedForcesShould`: `hierarchyAnchorsSeedOnTheGroupRing` (rewrite to packed placements),
  `hierarchyLinksPullNestedAnchorsTowardTheHierarchyRestLength` (rewrite: rest = packed distance),
  `twoMapWorkspaceSettlesToIdle` (re-baseline; measured idle **756** with the final configuration),
  `largerWorkspacesSeedWiderThanTheMinimumSpread`, `smallWorkspaceInitialPositionsAreNotCollapsedIntoTheOrigin`,
  `firstStepDoesNotTeleportSeededParticlesOntoTheirNeighbours` (keep the hard bound),
  `aTopologyChangeDoesNotTeleportRetainedParticles` (keep < 8.0/step), the direct-node containment and
  repulsion-coverage tests (re-baseline), `retainHierarchyAnchorsAcrossSteps` (adapt).
- Test-local mirror to remove: `TypedForcesShould.ringRadius` (line ~714).
- `BoundarySeparationShould`: `siblingBoundariesSeedWithoutOverlap`, `nestedGroupRingsDoNotInterleave`,
  `rootFramesSeparateOnTheTopRing`, `directNodesLieInsideTheirParentHullAfterGeometryComputation`,
  `siblingAnchorsRemainSeparatedAfterSettling`, pin tests, `repeatedSettlesAreDeterministic`, the
  `reproducedCase*` family, ancestor-exclusion tests. Seed-geometry assertions are rewritten to the packed
  contract with measured values; the proximity invariant
  `distance ≤ ReferenceRepulsionFixture.boundaryRadius(parent) − 16` is replaced by the per-axis footprint
  containment check with the measured tolerance (reported, not asserted at 4 units).
- Test-local estimator mirrors to remove: `BoundarySeparationShould`'s private
  `Sizes`/`ringRadius`/`maximumReach`/`reachOf` block and `ReferenceRepulsionFixture.Sizes`.
- `GraphStreamBoundaryShould.provideOrderedPositiveCalibrationStrengths`: **rewritten**, not kept: it
  asserts positivity plus `hierarchy() < containment()` and `containment() <= sameMap()` with the recorded
  values (0.30, 1.0, 1.0). The old `containment() < hierarchy()` ordering no longer expresses the intended
  stiffness (containment springs are now as stiff as same-map links); leaving it in place fails with
  "Expecting actual: 1.0 to be less than: 0.3" and criterion 5 could not be met.
- `LayoutWorkerShould`, `BoundarySeparationCorrectionShould`, `GraphWorkspaceModelAcceptanceShould` and
  peers: re-run; idle/position goldens re-baselined with recorded measurements.

### 9.2 New tests

- `ClusterPackingShould`: determinism; exact gutter within the stated tolerance (`SIBLING_GAP − 1e-9` to
  `SIBLING_GAP + 0.75`) and no overlap (`max(gapX, gapY) >= −1e-9`) on every pair; order/tie-breaks;
  centre-line and assembly-box candidates; contact recording (metadata only); anchor-frame placement;
  top-level membership; single-item offset 0; a **packed-position separation-box invariant** case (every
  non-ancestor pair's box gaps are not both negative at the packed positions); 200-child wall-clock ≤ 3 s.
- `BoundaryFootprintShould`: per-axis footprints and anchor offsets for leaf/nested/empty enclosures.
- `ClusterProximityShould` (settled, reported): fixtures A–H — run to first idle (else 2000 steps) plus
  200 steps; record idle, packed/settled anchor distances, painted gaps, all-pairs non-ancestor crossings
  and containment overflows; assert idle within budget and crossings = 0; record the 1000-step window.
- Initial-frame test: seed a fresh projection and assert the placements exactly (no force stepping). The
  assertion targets `LayoutEngine.apply`'s seeded frame **before** `BoundarySeparationCorrection`; through
  `LayoutWorker.submit` the pinned fixture C diverges by up to 380.45 units, so pinned fixtures assert
  packing equality at the engine level and only no-overlap/gutter through the worker.

### 9.3 Acceptance measurement procedure

Settle endpoint: first idle frame; if a fixture does not idle within 2000 steps, the 2000th step, then
always 200 extra steps (the stored prototype loops to 2500; the production test uses 2000). Positions from
`LayoutWorker` frames (`LayoutCalibration.spikeDefaults()`);
painted hulls from `GraphGeometryEngine` with `AwtGeometryTextMetrics` (`Dialog` 12, headless); gap =
convex-hull separation (maximum separating-axis gap, positive when disjoint); crossing = non-ancestor pair
with negative separation (`BoundaryInvariantAssertions` semantics). The 1000-step window follows the
documented endpoint.

### 9.4 Label readability gate

Real-application screenshots at both levels (fit-all and reading zoom) plus a headless measurement from
`ScreenLabelPlacement` (overlapping labels, leader length vs footprint, maximum leader), compared with
the recorded baseline. Criterion is an explicit user-review gate; if crowding materially exceeds the
baseline, decision 3 is re-opened.

## 10. Risks and mitigations

| Risk | Mitigation |
|---|---|
| Settled drift after initialization (measured up to 78.7 over a 1000-step window on relationship fixtures) | Explicit scope decision 4; reported per fixture; the post-geometry correction prevents crossings every frame |
| Labels crowd after their reserved space is removed | 9.4 records a baseline and is a user gate |
| Containment overflow (measured 0–14) | Reported, not asserted; margin is a packing budget |
| Calibration change alters node layout | `containment()` 1.0 is the only calibration change; goldens re-baselined with measurements |
| 200-child cost | Measured 0.52 s submit; production bound ≤ 3 s |
| Fixture set still small | A–H cover leaf/nested/empty/dual/4-map/4-sibling/relationship/pin/asymmetric-sibling shapes; settled values are reported per fixture |

## 11. Acceptance criteria

1. **Initial frame (asserted):** for every fixture and for a set of generated projections, `apply` on a
   fresh workspace seeds placements that are exactly the `ClusterPacking` output at the engine frame
   (before the post-geometry correction): deterministic, no pair overlap
   (`max(gapX, gapY) >= −1e-9`), gutter in `[SIBLING_GAP − 1e-9, SIBLING_GAP + 0.75]` on every contact,
   content at the anchor, correct top-level membership and single/empty cases; pinned fixtures assert
   equality at the engine level only (9.2).
2. **Determinism (asserted):** equal-request frame equality and the packing determinism tests pass.
3. **Settled (asserted where measured-robust):** every fixture reaches idle within the 2000-step budget
   (measured 22–785) and has zero all-pairs non-ancestor painted crossings at the documented endpoint
   (measured 0).
4. **Settled (reported):** packed vs settled anchor distances, painted gaps, 1000-step window drift, and
   containment overflow are recorded for A–H in the test evidence (values in 7.2/7.3); the top-level
   drift on relationship fixtures is documented as an accepted consequence of scope decision 4.
5. The enumerated existing tests are re-baselined with recorded measurements; the full
   `freeplane_plugin_graph` suite and the project suite pass in the integration worktree.
6. Real-application screenshots show compact initial clusters; the 9.4 crowding measurement is recorded
   and reviewed by the user.
7. Only the files and symbols named in 4.1–4.6 change; no other force, geometry, canvas, or label
   behaviour is altered.

## 12. Reproducible prototype and probe artifacts

Stored under
`docs/superpowers/specs/mockups/2026-10-13-graph-cluster-initial-proximity/prototype/`:

- `overlay/` — the option-A overlay sources (`GraphStreamLayoutEngine.java`, `TypedNodeParticle.java`,
  `TypedSpringBox.java`): anchor-centred per-axis footprints, production centre-line packing with contact
  metadata, hierarchy links with packed rest lengths, offset-aware box separation. Prototype switches:
  `-Dproto.containment`, `-Dproto.margin`, `-Dproto.boundary`.
- `ClusterProbe.java` (baseline A/B), `ContactProbe.java` (A–H + settled/window/crossing/containment +
  timing).
- `README.md` — classpath, compile, run commands, switches.
- `probe-baseline.txt`, `probe-final.txt` (option-A output), `probe-cap-sweep.txt` and
  `superseded-v2/` (review-2 lattice prototype, review-3 v4 overlay, interim v5/no-centre-line and v6
  skeleton overlays) for history.
