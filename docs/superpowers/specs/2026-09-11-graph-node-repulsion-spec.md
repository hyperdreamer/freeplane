# Graph Workspace Node Repulsion & Layout Settling — Implementation Specification

- Date: 2026-09-11
- Task Identifier: `2026-09-11-graph-node-repulsion` (no Ticket ID)
- Status: Specified for implementation planning; approved design is review round 5 at commit `0eeefcfca7`
- Scope: `freeplane_plugin_graph` (branch `feature/graph-workspace`)
- Binding design: `docs/superpowers/specs/2026-09-11-graph-node-repulsion-design.md`
- Diagnosis evidence: `reports/repulsion-diagnosis.md` (PM run `0233ff88d3cc54bbd61550c61fa917d6e2a5717f8a03be4e9aa63dcd47dd1cfa`, task `pm-run-20260911-085410-438034e0`)
- Repository conventions: Java 8 target, 4-space indent, UTF-8, JUnit 4 `*Should` tests, AssertJ assertions, test-local copies of production size formulas (production `GraphStreamLayoutEngine.BoundarySizes` is not visible from the test package).

This specification is derived strictly from the approved design. It defines three production changes and ten new tests. It adds no features, no APIs, and no runtime flags beyond the approved design.

## 1. Purpose and Reading Guide

The reported defect ("repulsion between nodes doesn't work properly; the nodes keep moving and won't stabilize when unpinned") is caused by `TypedNodeParticle.scaleRepulsion` scaling a particle's total native repulsion by its own `separationRadius / 8.0`; hull anchors have separation radii of order 10^3, so anchor–node pairs lose Newton's-third-law symmetry and the layout drifts rigidly forever. A secondary defect lets boundary repulsion push nested parent/child/grandchild hulls apart. The three changes below remove the asymmetric scaling for anchors and exclude ancestor/descendant anchor pairs from boundary repulsion.

Requirement identifiers:

- `R1`–`R3`: change 1 (anchor repulsion scaling).
- `R4`–`R7`: change 2 (explicit anchor ancestry in the engine).
- `R8`–`R13`: change 3 (ancestor-excluded boundary repulsion and hygiene).
- `R14`: explicitly unchanged behavior.
- `R15`–`R19`: error and edge behavior.
- `R20`–`R22`: test assertion contracts.
- `R23`: existing-suite compatibility.
- `T1`–`T10`: new tests (same numbering as design §9).
- `V1`–`V4`: verification requirements.

Normative keywords: **must**, **must not**, **shall** are binding.

## 2. Production Changes

All production changes are confined to the package `org.freeplane.plugin.graph.layout.graphstream`. No other main source file changes.

### 2.1 Change 1 — Anchor particles do not scale native repulsion

#### 2.1.1 New predicate in `TypedSpringBox`

File: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/TypedSpringBox.java`.

Add exactly this package-private method (place it near `configureParticle`/`baseSeparationRadius`):

```java
boolean isAnchorParticle(final String id) {
    final Boolean flag = anchorFlags.get(id);
    return flag != null && flag.booleanValue();
}
```

Semantics (`R1`): reads the existing `anchorFlags` map written by `configureParticle`; returns `true` only for a known id flagged as an anchor particle, `false` for node particles and unknown ids. No other behavior changes.

#### 2.1.2 `TypedNodeParticle.scaleRepulsion`

File: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/TypedNodeParticle.java`.

Current method (must be replaced):

```java
private void scaleRepulsion(final Vector3 before) {
    final Vector3 repulsion = new Vector3(disp);
    repulsion.sub(before);
    if (separationRadius != typedBox.baseSeparationRadius()) {
        repulsion.scalarMult(separationRadius / typedBox.baseSeparationRadius());
        disp.copy(before);
        disp.add(repulsion);
    }
    if (typedBox.hasCrossMapLink(this)) {
        rawBudgetedRepulsion.add(repulsion);
    }
}
```

New method (`R2`):

```java
private void scaleRepulsion(final Vector3 before) {
    final Vector3 repulsion = new Vector3(disp);
    repulsion.sub(before);
    if (!typedBox.isAnchorParticle(getId().toString())
            && separationRadius != typedBox.baseSeparationRadius()) {
        repulsion.scalarMult(separationRadius / typedBox.baseSeparationRadius());
        disp.copy(before);
        disp.add(repulsion);
    }
    if (typedBox.hasCrossMapLink(this)) {
        rawBudgetedRepulsion.add(repulsion);
    }
}
```

Semantics (`R2`):

- `repulsion` is still the native repulsion delta of the current pass (the difference `disp − before` produced by `super.repulsionN2` / `super.repulsionNLogN`).
- The scaling branch executes only when the particle is **not** an anchor particle and `separationRadius != baseSeparationRadius()`. Node particles keep the prominence-driven scaling (`NodeProminence.MAX_SCALE = 1.75`); anchor particles (hull centers) now keep the raw native delta.
- The cross-map capture is unchanged in position: `rawBudgetedRepulsion` accumulates exactly the `repulsion` vector that the particle ends up applying — scaled for scaled node particles, raw for anchors and unscaled nodes (`R3`).
- No other method of `TypedNodeParticle` changes.

Ordering (`R3`): `isAnchorParticle` is read from `anchorFlags`, which is written by `configureParticle` during `synchronize()`. `scaleRepulsion` only runs later, inside `step()`, so the flags are current. No invalidation logic is added.

Coupled effect (`R3`): for a cross-map-linked anchor, `rawBudgetedRepulsion` previously carried the ≈ 180× scaled delta; it now carries the anchor's true native repulsion magnitude. The `≤ 0.005` budget cap and the `capAggregateCrossMapFanOutDisplacementOncePerParticle` contract are preserved; only the composition of the capped sum changes (cross-map attraction dominates in practice).

### 2.2 Change 2 — Explicit anchor ancestry in `GraphStreamLayoutEngine`

File: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java`.

#### 2.2.1 `DesiredParticle.parentAnchorId`

Add a nullable, non-final field after `radius`:

```java
private String parentAnchorId;
```

It defaults to `null`. The constructor signature and the `node(...)` / `anchor(...)` factory signatures must not change (`R4`). Node particles never receive a non-null value; anchor particles receive it in the topology's second phase.

#### 2.2.2 Two-phase anchor construction in `topology(...)`

The current anchor block (phase 1) stays as is:

```java
final Map<EnclosureHullKey, String> anchorIds = new LinkedHashMap<EnclosureHullKey, String>();
final Map<EnclosureKey, String> enclosureEndpoints = new LinkedHashMap<EnclosureKey, String>();
for (final ProjectedEnclosure enclosure : projection.enclosures()) {
    final DesiredParticle particle = DesiredParticle.anchor(enclosure.hullKey(),
        sizes.boundaryRadius(enclosure.hullKey()));
    desired.put(particle.id, particle);
    anchorIds.put(enclosure.hullKey(), particle.id);
    for (final EnclosureKey endpoint : enclosure.endpointKeys()) {
        enclosureEndpoints.put(endpoint, particle.id);
    }
}
```

Add a second loop immediately after it and before the link-construction code (`R5`):

```java
for (final ProjectedEnclosure enclosure : projection.enclosures()) {
    final DesiredParticle particle = desired.get(anchorIds.get(enclosure.hullKey()));
    particle.parentAnchorId = anchorIds.get(enclosure.parentHull().orElse(null));
}
```

Ordering constraints (`R5`):

- Phase 2 strictly follows phase 1, so `anchorIds` already contains every hull of the projection.
- `anchorIds.get(null)` returns `null`: map roots and any enclosure with an empty `parentHull()` receive `null`.
- A `parentHull()` that is absent from the projection's enclosure list also yields `null` (see §3.1).
- The second loop only mutates `DesiredParticle` fields; it must not insert, remove, or reorder entries of `desired`.
- Link construction, `enclosureDepths`, `hierarchyRestLength`, and `addHierarchyLink` are untouched.

#### 2.2.3 `configureParticle` call site in `synchronize()`

Current call:

```java
springBox.configureParticle(desired.id, state.radius, state.pinned, desired.nodeKey == null);
```

New call (`R6`):

```java
springBox.configureParticle(desired.id, state.radius, state.pinned, desired.nodeKey == null,
    desired.parentAnchorId);
```

`configureParticle` is invoked for every particle on every accepted request (the `synchronize()` loop iterates all `topology.particles`), so a reparented boundary refreshes its parent id without extra invalidation logic (`R7`).

### 2.3 Change 3 — Ancestor-excluded boundary repulsion in `TypedSpringBox`

File: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/TypedSpringBox.java`.

#### 2.3.1 `parentOf` field

Add directly after `anchorFlags` (`R8`):

```java
private final Map<String, String> parentOf = new LinkedHashMap<String, String>();
```

Null values are permitted; an absent key and a null value are equivalent to "no parent".

#### 2.3.2 `configureParticle` signature

Current signature:

```java
void configureParticle(final String id, final double radius, final boolean pinned, final boolean anchor)
```

New signature (`R9`):

```java
void configureParticle(final String id, final double radius, final boolean pinned, final boolean anchor,
        final String parentAnchorId)
```

Add `parentOf.put(id, parentAnchorId);` as the first statement of the body. Existing statements (`anchorFlags.put`, particle lookup, `particle.configure`, `freezeNode`) remain unchanged. The method is called for every particle on every `synchronize()`, so `parentOf` is refreshed (including overwritten with `null` for nodes and map roots) on each accepted request.

#### 2.3.3 `forgetParticle`

Current method:

```java
void forgetParticle(final String id) {
    anchorFlags.remove(id);
    typedParticles.remove(id);
}
```

New method (`R10`):

```java
void forgetParticle(final String id) {
    anchorFlags.remove(id);
    parentOf.remove(id);
    typedParticles.remove(id);
}
```

This keeps `parentOf` bounded by the live particle set.

`clearLinks()` must **not** clear `parentOf` (`R11`). In `synchronize()` the per-particle `configureParticle` loop runs before `replaceLinks(...)` (which calls `clearLinks()`), so clearing `parentOf` in `clearLinks()` would discard the ancestry written moments earlier. `parentOf` is owned exclusively by `configureParticle` and `forgetParticle`.

#### 2.3.4 New predicate `isAncestorPair`

Add this private method (`R12`):

```java
private boolean isAncestorPair(final String first, final String second) {
    for (String current = parentOf.get(first); current != null; current = parentOf.get(current)) {
        if (current.equals(second)) {
            return true;
        }
    }
    for (String current = parentOf.get(second); current != null; current = parentOf.get(current)) {
        if (current.equals(first)) {
            return true;
        }
    }
    return false;
}
```

Semantics: returns `true` iff `first` is an ancestor of `second` or `second` is an ancestor of `first`. Each walk starts at the direct parent and ascends one level per iteration; a null value or an absent key terminates that walk. The predicate covers parent–child **and** grandparent–grandchild (all ancestor depths). No recursion, no exceptions.

#### 2.3.5 `addBoundaryRepulsion` skip

In `addBoundaryRepulsion`, after the existing self-skip

```java
if (other == particle) {
    continue;
}
```

and before `final Point3 own = particle.getPosition();`, insert (`R13`):

```java
if (isAncestorPair(particle.getId().toString(), other.getId().toString())) {
    continue;
}
```

Ordering constraint (`R13`): the ancestor skip is applied **before** the zero-distance fallback (`if (distance == 0.0) { ... }`) and before the penetration computation. A parent/child pair seeded at the same point (single-child boundary, `Seeds.center` with `ringRadius = 0`) is therefore excluded instead of being resolved by the fallback direction. Nothing else in `addBoundaryRepulsion` changes: the `extent` formula (`boundaryRadius + boundaryRadius + SIBLING_GAP`), the `penetration <= 0.0` early-out, the direction vector, and `BOUNDARY_REPULSION_FACTOR = 0.5` are untouched. Sibling boundaries, different-map roots, and cross-map anchors still repel symmetrically.

### 2.4 Signature Change Summary

| Symbol | Before | After |
| --- | --- | --- |
| `TypedSpringBox.isAnchorParticle` | — | `boolean isAnchorParticle(final String id)` |
| `TypedSpringBox.configureParticle` | `void configureParticle(String, double, boolean, boolean)` | `void configureParticle(String, double, boolean, boolean, String)` |
| `TypedSpringBox.forgetParticle` | removes `anchorFlags`, `typedParticles` | also removes `parentOf` |
| `TypedSpringBox.isAncestorPair` | — | `private boolean isAncestorPair(String, String)` |
| `TypedNodeParticle.scaleRepulsion` | scales for every particle with `radius != 8.0` | scales only when `!isAnchorParticle(id) && radius != 8.0` |
| `GraphStreamLayoutEngine.DesiredParticle` | fields `id, mapReferenceId, nodeKey, anchorKey, radius` | adds nullable non-final `parentAnchorId` |
| `GraphStreamLayoutEngine.topology` | one anchor phase | adds a second ancestry-assignment phase |
| `GraphStreamLayoutEngine.synchronize` | 4-argument `configureParticle` | passes `desired.parentAnchorId` (5 arguments) |

### 2.5 Explicitly Unchanged Behavior

The following must remain byte-for-byte equivalent in behavior (no retuning, no refactor) (`R14`):

- `K2 = 16.0` (`REPULSION_FACTOR`), `force = 1`, `ATTRACTION_FACTOR = 0.05`,
  `BOUNDARY_REPULSION_FACTOR = 0.5`, `REST_LENGTH = 24.0`,
  `BASE_SEPARATION_RADIUS = 8.0`, `CROSS_MAP_DISPLACEMENT_LIMIT = 0.005`.
- `PerceptualIdlePolicy.spikeDefaults()` (`8` consecutive frames, RMS `0.05`, max `0.10`)
  and `LayoutCalibration` values (`containment 0.15`, `hierarchy 0.30`, `sameMap 1.0`).
- Node-prominence repulsion weighting for node particles (scaling stays for `radius != 8.0`).
- Typed attraction, pin freezing, `Seeds` placement, `BoundarySizes`, `enclosureDepths`,
  `hierarchyRestLength`, `MapTierCorrection`, `GraphGeometry`, hull/geometry code,
  projection code, and the `GraphStreamLayoutEngine.apply` empty-request fast path.
- Barnes–Hut configuration: `setQuality(0.10)` (view zone 2) and `nodesPerCell = 10`.
- No system-property switches or runtime flags are added; the three changes are unconditional.

## 3. Error and Edge Behavior

### 3.1 Missing or unresolvable parent hull

`R15`: An enclosure with an empty `parentHull()`, or a `parentHull()` absent from the projection's enclosure list, yields `parentAnchorId == null` in `topology()` (`anchorIds.get(...)` returns `null`). `parentOf` then records a null value, `isAncestorPair` finds no ancestry, and no exclusion is applied. This is the only "missing parent" handling introduced; it must not throw. Malformed projections that reference an absent parent hull already fail earlier in `enclosureDepths`; the new code adds no new failure mode.

### 3.2 Ancestry walk termination

`R16`: The `isAncestorPair` walks terminate when `parentOf.get(current)` returns `null` (map root or null value) or when the key is absent. No cycle detection is added: `ProjectedEnclosure` enforces a non-self, same-map parent but not acyclicity, and a cyclic parent chain already fails earlier in `GraphStreamLayoutEngine.enclosureDepths` (unbounded recursion) before any `isAncestorPair` call during stepping, so the walk adds no new failure mode and is otherwise bounded by the enclosure tree depth.

### 3.3 `forgetParticle` hygiene

`R17`: Removing an obsolete particle removes its `anchorFlags`, `parentOf`, and `typedParticles` entries in the same call. A particle re-added later receives a fresh parent id through `configureParticle`.

### 3.4 Reparenting refresh and the empty-diff early return

`R18`: A reparented boundary refreshes `parentOf` only when `GraphStreamLayoutEngine.apply` calls `synchronize()`. The empty-diff fast path in `apply` is taken only when `accepted.diff().isEmpty()` **and** `accepted.diff().beforeGeneration() == lastSynchronizedProjectionGeneration` (plus matching workspace and pins); otherwise `synchronize()` runs. Under the generation-identifies-content protocol any request that actually reparents a boundary also advances the generation, so it cannot carry a matching-generation empty diff (`LayoutRequest` itself only forces `afterGeneration == projection.generation()`) and therefore re-synchronizes. Test T7 builds its request with `ProjectionDiff.between(originalProjection, reparentedProjection)` (a non-empty diff) and a distinct generation as the honest request description; the assertion fails when `configureParticle` does not overwrite `parentOf`, which is the contract under test.

### 3.5 Guards and recovery untouched

`R19`: The non-finite position guards, `LayoutWorker` coverage validation, and failed-frame recovery in `LayoutWorker`/`LayoutSettleLoop` are untouched.

## 4. Test Plan

### 4.1 Conventions

- JUnit 4 (`org.junit.Test`), public `void` test methods, `*Should` class names, AssertJ `assertThat`/`within`.
- All new tests live in `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/`.
- Raw-engine tests use `GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())` in try-with-resources.
- `LayoutWorker` tests use `new LayoutWorker(LayoutCalibration.spikeDefaults())` and `try { ... } finally { worker.close(); }`.
- Idle step counting convention: submit the request once, then count `worker.step()` calls starting at 1. A "cap of N" means the loop performs at most N `step()` calls after the submit, stopping at the first frame with `frame.idle().idle() == true`. Measured step numbers below use this convention.
- 1500-frame settle convention (mirrors the existing private `settle(...)` helper in `BoundarySeparationShould`): `engine.apply(request)`, then exactly `1500` × `engine.step()`, then a trailing `engine.apply(request)` whose returned frame is asserted on. For the empty-diff settle requests (T3/T4/T8/T9) the trailing `apply` takes the empty-diff fast path and returns the current positions; T7's reparent settle uses the non-empty reparent request, so its trailing `apply` re-synchronizes instead, which is position-idempotent (no re-seeding, no pins).
- No existing test method or assertion may be modified; new tests are appended.

### 4.2 Fixtures

#### 4.2.1 Reference fixture (used by T1–T9)

Fixed workspace `WORKSPACE = 00000000-0000-0000-0000-0000000000aa`, single map `MAP = 00000000-0000-0000-0000-000000000001`, map name `"M"`, `ProjectedNode.graphGroup == true`. Projection generation `1L`, no edges, no pins in the projection.

Nodes (list order `[n1, n2, n3, n4]`; persisted node ids `n1`..`n4`):

| key | label | parent enclosure | direct-node index |
| --- | --- | --- | --- |
| `n1` | `Fundation / Regularity` | `Axioms` | 0 |
| `n2` | `Replacement Scheme` | `Axioms` | 1 |
| `n3` | `Axiom of Choice` | `Axioms` | 2 |
| `n4` | `Theorem` | `Basic Definitions and Theorems` | 0 |

Enclosures (list order `[root, zfc, axioms, definitions]`; persisted endpoint node ids `root`, `zfc`, `axioms`, `defs`):

| hull key | label | tier | mapRoot | parentHull | directNodes | directEnclosures |
| --- | --- | --- | --- | --- | --- | --- |
| `root` | `Axiomatic Set Theory` | `SUPPRESSED` | `true` | empty | `[]` | `[zfc]` |
| `zfc` | `ZFC` | `EMPHATIC` | `false` | `root` | `[]` | `[axioms, definitions]` |
| `axioms` | `Axioms` | `SUBTLE` | `false` | `zfc` | `[n1, n2, n3]` | `[]` |
| `definitions` | `Basic Definitions and Theorems` | `SUBTLE` | `false` | `zfc` | `[n4]` | `[]` |

Every hull is `EnclosureHullKey.of(Collections.singletonList(EnclosureKey.of(SourceNodeKey.persisted(NodeReference.of(MAP, PersistedNodeId.of(id))))))`. Projection is built with `GraphProjection.projected(1L, nodes, enclosures, Collections.<ProjectedEdge>emptyList(), Collections.<RelationshipResolution>emptyList(), Collections.<PinProjection>emptyList())`. This fixture has 8 particles and all node prominence scales are exactly `1.0`.

#### 4.2.2 Reparented reference fixture (used by T7)

Same nodes, same enclosure list order `[root, zfc, axioms, definitions]`, generation `2L`, no edges, no pins. Differences from §4.2.1:

- `zfc.directEnclosures = [axioms]` (definitions removed),
- `axioms.directEnclosures = [definitions]` (definitions added),
- `definitions.parentHull = Optional.of(axioms)` (was `zfc`).

`ProjectionDiff.between(reference, reparented)` is therefore non-empty (three changed enclosures: `zfc`, `axioms`, `definitions`).

#### 4.2.3 Two-map fixture (used by T10)

Fixed workspace `WORKSPACE` (same id as §4.2.1). Map A `MAP_A = 00000000-0000-0000-0000-000000000001`, map B `MAP_B = 00000000-0000-0000-0000-000000000002`. Generation `1L`. All `ProjectedNode`s use map name `"Map"` and `graphGroup == true`.

Build per map prefix `p` in `{a, b}`:

- root hull endpoint node id `p + "-root"`; map A root label `Axiomatic Set Theory` tier `SUPPRESSED`; map B root label `Topology` tier `EMPHATIC`; `mapRoot = true`; empty `parentHull`; empty `directNodes`; `directEnclosures = [p-sub-0, p-sub-1, p-sub-2]`.
- sub-boundary `s` in `0..2`: hull endpoint node id `p + "-sub-" + s`; label `("A-sub " | "B-sub ") + s`; tier `SUBTLE`; `parentHull = Optional.of(root hull)`; `directEnclosures = []`; `directNodes = [p + s + "_0" .. p + s + "_3"]`.
- node `p + s + "_" + n` for `n` in `0..3`; label `"Boundary " + s + " node " + n`.

Node list order: map A first (s ascending, n ascending), then map B. Enclosure list order: `[a-sub-0, a-sub-1, a-sub-2, a-root, b-sub-0, b-sub-1, b-sub-2, b-root]` (sub-boundaries are appended before their root, exactly as validated).

Edges, list order `i = 0..3`, for `i`, using the existing private `key(MapReferenceId, String)` helper of `TypedForcesShould` (which builds `ProjectedNodeKey.of(SourceNodeKey.persisted(NodeReference.of(map, PersistedNodeId.of(id))))`):

```java
long sequence = i + 1L;
GraphRelationshipRecord relationship = GraphRelationshipRecord.of(
    RelationshipId.of(String.format("20000000-0000-0000-0000-%012d", Long.valueOf(sequence))),
    sequence,
    key(MAP_A, "a0_" + i).source().persistedReference().get(),
    key(MAP_B, "b0_" + i).source().persistedReference().get(),
    RelationshipDirection.FORWARD,
    Collections.<UnknownXml>emptyList());
ProjectedEndpointKey source = ProjectedEndpointKey.ofNode(key(MAP_A, "a0_" + i));
ProjectedEndpointKey target = ProjectedEndpointKey.ofNode(key(MAP_B, "b0_" + i));
edges.add(ProjectedEdge.of(ProjectedEdgeKey.of(source, target),
    Collections.singletonList(EdgeContributor.graphRelationship(relationship, source, target))));
```

Edges connect all four nodes of A-sub 0 to the corresponding nodes of B-sub 0. The fixture has 24 nodes and 8 anchors (32 particles), no pins.

### 4.3 Shared Test Helper

New file: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/ReferenceRepulsionFixture.java`.

Package-private `final class ReferenceRepulsionFixture` (no JUnit dependency). It builds and exposes §4.2.1 and §4.2.2 plus the test-local size formulas. Required API:

```java
static final WorkspaceId WORKSPACE;                 // 00000000-0000-0000-0000-0000000000aa
static final MapReferenceId MAP;                    // 00000000-0000-0000-0000-000000000001
static final double CHAR_WIDTH_UPPER_BOUND = 16.0;
static final double CHAR_HEIGHT_UPPER_BOUND = 24.0;
static final double BOUNDARY_PADDING = 8.0;
static final double SIBLING_GAP = 8.0;
static final double FRAME_CLEARANCE = 16.0;
static final double MAX_RENDERED_NODE_RADIUS = 14.0;

static ProjectedNodeKey regularityNode();           // n1
static ProjectedNodeKey replacementNode();          // n2
static ProjectedNodeKey choiceNode();               // n3
static ProjectedNodeKey theoremNode();              // n4
static EnclosureHullKey rootHull();                 // root
static EnclosureHullKey zfcHull();                  // zfc
static EnclosureHullKey axiomsHull();               // axioms
static EnclosureHullKey definitionsHull();          // defs
static GraphProjection referenceProjection(long generation);   // §4.2.1
static GraphProjection reparentedProjection(long generation);  // §4.2.2
static double boundaryRadius(GraphProjection projection, EnclosureHullKey hull);
```

`boundaryRadius` is a test-local copy of the production `BoundarySizes` formulas and must not filter any tier:

1. Per direct node: `width = Math.max(2.0 * MAX_RENDERED_NODE_RADIUS, label.length() * CHAR_WIDTH_UPPER_BOUND + 2.0 * BOUNDARY_PADDING)`, `height = Math.max(2.0 * MAX_RENDERED_NODE_RADIUS, CHAR_HEIGHT_UPPER_BOUND + 2.0 * BOUNDARY_PADDING)`.
2. `directNodeRingRadius(hull)`: `0.0` when the enclosure has ≤ 1 direct node; else `Math.hypot(maxWidth + SIBLING_GAP, maxHeight + SIBLING_GAP) / (2.0 * Math.sin(Math.PI / count))` over the direct nodes.
3. `directNodeReach(hull)`: `0.0` when there are no direct nodes; else `directNodeRingRadius + max(0.5 * Math.hypot(width, height))`.
4. `ringRadius(hull)`: `0.0` when the enclosure has ≤ 1 direct child enclosure; else the same hypot/sin formula over the children's `sizeOf` squares.
5. `childBoundaryReach(hull)`: `0.0` when there are no children; else `ringRadius(hull) + max(reachOf(child))`.
6. `reachOf(hull)`: when the enclosure has no direct nodes and no direct children, `0.5 * Math.hypot(sizeOf(hull).width, sizeOf(hull).height)`; else `max(directNodeReach, childBoundaryReach)`.
7. `sizeOf(hull)`: when `directNodeReach == 0.0 && childBoundaryReach == 0.0 && no direct nodes && no children`, the label box `width = max(2.0 * BOUNDARY_PADDING, max(label.length() * CHAR_WIDTH_UPPER_BOUND + 2.0 * BOUNDARY_PADDING))`, `height = CHAR_HEIGHT_UPPER_BOUND + 2.0 * BOUNDARY_PADDING`; else the square `side = 2.0 * (max(directNodeReach, childBoundaryReach) + FRAME_CLEARANCE)`.
8. `boundaryRadius(hull) = 0.5 * Math.hypot(size.width, size.height)` (memoization is allowed).

Expected values for the reference fixture (design's quoted figures; the helper computes the exact doubles): `boundaryRadius(root) ≈ boundaryRadius(zfc) ≈ 1441.73`, `boundaryRadius(axioms) ≈ 593.87`, `boundaryRadius(definitions) ≈ 117.45`.

In the test descriptions below, unqualified names `WORKSPACE`, `MAP`, `referenceProjection(...)`, `reparentedProjection(...)`, `rootHull()`, `zfcHull()`, `axiomsHull()`, `definitionsHull()`, `replacementNode()`, and `boundaryRadius(...)` refer to the corresponding static members of `ReferenceRepulsionFixture`.

### 4.4 New Tests

#### T1 — `TypedForcesShould.settleRelationshipFreeNestedBoundaryProjectionToIdle`

- File: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java`.
- Fixture: `ReferenceRepulsionFixture.referenceProjection(1L)`; pins empty.
- Drive: `LayoutWorker` with `spikeDefaults()`. Submit `LayoutRequest.of(WORKSPACE, projection, ProjectionDiff.between(projection, projection), Collections.<PinProjection>emptyList())`; loop at most 2000 `worker.step()` calls, stopping at the first idle frame.
- Assert: `firstIdleStep <= 2000` and the idle frame's `idle().idle() == true`.
- Measured: first idle at step 457 (rms 0.0472, max 0.0939).
- Falsifiability: the unfixed implementation never reports idle within 2000 steps (steady-state rms ≈ 0.184), so the assertion fails.

#### T2 — `TypedForcesShould.nativeRepulsionDisplacementsSumToZeroWithoutDrift`

- File: `TypedForcesShould.java`.
- Fixture: `ReferenceRepulsionFixture.referenceProjection(1L)`; pins empty.
- Drive: raw engine (`GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())`), not `LayoutWorker` (which applies `MapTierCorrection`):

```java
engine.apply(request);
LayoutFrame before = engine.step();
LayoutFrame after = engine.step();
```

- Assert (`R20`): summing `after.x − before.x` and `after.y − before.y` over every entry of `before.positions().nodes()` and `before.positions().anchors()`, `Math.hypot(sumDx, sumDy) <= 1.0e-9`.
- Comment requirement: record in the test that pins, cross-map budgeting, and Barnes–Hut aggregation are deliberately outside this assertion; the fixture has ≤ 10 particles (single leaf cell → exact pairwise pass), no pins, no relationship edges, no cross-map links, and all pairwise springs are symmetric.
- Measured: second interval (`step1 → step2`) fixed `9.9e-14`, unfixed `6.55`; third interval fixed `1.0e-13`, unfixed `0.54`.
- The first interval (`apply() → step1`) must not be used: `NodeParticle.move` clamps each particle independently to `box.area / 2` (≈ 1.414 during seeding), so its sum is 0.1725 both before and after the fix.
- Falsifiability: unfixed momentum 6.55 exceeds 1e-9, so the assertion fails.

#### T3 — `BoundarySeparationShould.directParentChildAnchorsSatisfyTheProximityInvariantAfterSettling`

- File: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationShould.java`.
- Fixture: `ReferenceRepulsionFixture.referenceProjection(1L)`; pins empty.
- Drive: 1500-frame settle convention (§4.1).
- Assert: for every enclosure whose `parentHull()` is present — pairs `root→zfc`, `zfc→axioms`, `zfc→definitions` — `distance(childAnchor, parentAnchor) <= ReferenceRepulsionFixture.boundaryRadius(projection, parentHull) - 16.0` (no extra tolerance).
- Measured after exactly 1500 steps (fixed): root–zfc `100.23`, zfc–axioms `355.37`, zfc–definitions `355.11`; bound for root/zfc ≈ `1441.73 − 16 = 1425.73`; unfixed root–zfc `2810.58`, zfc–axioms `1984.77`, zfc–definitions `1522.81`.
- Falsifiability: unfixed root–zfc 2810.58 > 1425.7 (and zfc–axioms 1984.77 > 1425.7) fails.
- Full containment `d + r_child <= r_parent` is deliberately not asserted: root and zfc radii are equal by construction for wrapper hulls, and only direct pairs are asserted.

#### T4 — `BoundarySeparationShould.siblingAnchorsRemainSeparatedAfterSettling`

- File: `BoundarySeparationShould.java`.
- Fixture: `referenceProjection(1L)`; pins empty.
- Drive: 1500-frame settle convention.
- Assert (`R21`):
  (a) the existing label-box non-overlap contract via `assertNoSiblingOverlap(frame, Arrays.asList(axiomsHull(), definitionsHull()), Arrays.asList(SafeNodeLabel.of("Axioms", "Axioms"), SafeNodeLabel.of("Basic Definitions and Theorems", "Basic Definitions and Theorems")))`; and
  (b) the maximum pairwise anchor distance over all four hulls is `<= 1000.0`.
- Measured after exactly 1500 steps: axioms–definitions `710.48`; contact ≈ `593.87 + 117.45 + 8 = 719.32`; unfixed axioms–definitions `3485.41` and unfixed maximum pairwise anchor distance `3814.17`.
- Falsifiability: the unfixed maximum pairwise anchor distance `3814.17 > 1000` fails (the label-box guard is implied by the spread because the label boxes are far smaller than the 1000-unit bound).

#### T5 — `BoundarySeparationShould.pinnedFixtureSettlesWithPinPositionUnchanged`

- File: `BoundarySeparationShould.java`.
- Fixture: `ReferenceRepulsionFixture.referenceProjection(1L)`; pin `replacementNode()` (n2) at `(1059, -145)`:

```java
ProjectedNodeKey pinned = replacementNode();
PinRecord record = PinRecord.of(pinned.source().persistedReference().get(),
    1059.0, -145.0, Collections.<UnknownXml>emptyList());
List<PinProjection> pins = Collections.singletonList(PinProjection.active(record, pinned));
```

- Drive: `LayoutWorker`; submit the request with `pins`; loop at most 10000 `step()` calls, capturing `firstIdleStep`; then run exactly 100 further `step()` calls and track `worstRms = max(idle().rms())`, `worstMax = max(idle().max())` over those frames.
- Assert:
  - `firstIdleStep <= 10000`;
  - the pinned node's coordinates on the first idle frame and on the last guard frame are exactly `LayoutPoint.of(1059.0, -145.0)`;
  - `worstRms <= 0.0505` and `worstMax <= 0.10` (RMS guard relaxed from `0.05` to accommodate the measured `0.049654` margin).
- Measured: first idle at step 2471 with rms `0.049654`; post-idle 100-frame guard stays under both bounds.
- Falsifiability: the unfixed pinned run does not idle in the recorded 1500 frames, and its constant-amplitude drift mechanism (Section 7) does not decay, so `firstIdleStep <= 10000` fails.

#### T6 — `BoundarySeparationShould.unpinTransitionSettlesAfterFormerPinReleased`

- File: `BoundarySeparationShould.java`.
- Fixture: reference fixture with the T5 pin.
- Drive: submit the pinned request; step exactly 1500 frames; submit `LayoutRequest.of(WORKSPACE, projection, ProjectionDiff.between(projection, projection), Collections.<PinProjection>emptyList())` (empty pins only; same projection); loop at most 2000 `step()` calls, capturing the first idle frame.
- Assert (`R22`): `firstIdleStep <= 2000`, and on the first idle frame `distance(position(replacementNode), LayoutPoint.of(1059.0, -145.0)) > 10.0`.
- Measured: first idle `292` steps after the unpin submit; formerly pinned node `108.97` units from the pin at that frame (design's converged value `122.04` after ≈ 2100 further frames; see §5.1).
- Falsifiability: unfixed never idles within 2000 steps after unpin, so the assertion fails.

#### T7 — `BoundarySeparationShould.reparentedBoundaryRefreshesAncestorExclusion`

- File: `BoundarySeparationShould.java`.
- Fixture: `original = ReferenceRepulsionFixture.referenceProjection(1L)`, `reparented = ReferenceRepulsionFixture.reparentedProjection(2L)`.
- Drive: apply a request for `original`; step exactly 1500 frames; then

```java
LayoutRequest reparentRequest = LayoutRequest.of(WORKSPACE, reparented,
    ProjectionDiff.between(original, reparented), Collections.<PinProjection>emptyList());
engine.apply(reparentRequest);
for (int step = 0; step < 1500; step++) {
    engine.step();
}
LayoutFrame frame = engine.apply(reparentRequest);
```

- Assert: `distance(frame.positions().anchors().get(axiomsHull), frame.positions().anchors().get(definitionsHull))`
  `<= ReferenceRepulsionFixture.boundaryRadius(reparented, axiomsHull) - 16.0` (≈ `593.87 − 16 = 577.87`).
- Measured after exactly 1500 steps after the reparent: refreshed exclusion `61.84`; stale sibling exclusion `699.88`.
- Falsifiability: the assertion fails on unfixed code (measured `699.88 > 577.87`) and fails if `configureParticle` does not overwrite `parentOf` on reparent. (Direct-parent-only exclusion also excludes this pair, because `axioms` is `definitions`' direct parent; that insufficiency is discriminated by T8 instead.) `ProjectionDiff.between(original, reparented)` is the honest request description and guarantees `synchronize()`; an empty diff at a *different* generation also re-synchronizes, and the fast path is only taken for an empty diff whose `beforeGeneration()` equals the last synchronized generation (design §6.3, this spec §3.4).

#### T8 — `BoundarySeparationShould.grandchildAnchorsAreExcludedFromBoundaryRepulsion`

- File: `BoundarySeparationShould.java`.
- Fixture: `ReferenceRepulsionFixture.referenceProjection(1L)`; pins empty.
- Drive: 1500-frame settle convention.
- Assert: `distance(frame.positions().anchors().get(rootHull()), frame.positions().anchors().get(axiomsHull())) < 1000.0`.
- Measured after exactly 1500 steps: fixed `455.60`; with the ancestor exclusion removed `2043.08`; with direct-parent-only exclusion ≈ `2025`.
- Falsifiability: without the full ancestry walk the pair settles at 2043.08 (or ≈ 2025 direct-parent-only), both `>= 1000`.

#### T9 — `BoundarySeparationShould.coincidentParentChildAnchorsStayExcluded`

- File: `BoundarySeparationShould.java`.
- Fixture: `ReferenceRepulsionFixture.referenceProjection(1L)`; pins empty. The root has a single direct enclosure, so `Seeds.center` gives `ringRadius = 0` and the root–zfc anchors seed at the same point.
- Drive: 1500-frame settle convention.
- Assert: the same proximity invariant as T3 for the root–zfc pair: `distance(frame.positions().anchors().get(rootHull()), frame.positions().anchors().get(zfcHull())) <= boundaryRadius(projection, rootHull()) - 16.0`.
- Measured after exactly 1500 steps: fixed `100.23`; unfixed `2810.58`.
- Falsifiability: without the exclusion the zero-distance fallback pushes the coincident pair to 2810.58 > 1425.7. The exclusion-before-fallback ordering itself is a code-impact note, not an independent assertion.

#### T10 — `TypedForcesShould.twoMapWorkspaceSettlesToIdle`

- File: `TypedForcesShould.java`.
- Fixture: the §4.2.3 two-map fixture, built with private static helpers in `TypedForcesShould`; 24 nodes, 8 anchors, 4 cross-map edges; pins empty.
- Drive: `LayoutWorker` with `spikeDefaults()`; submit `LayoutRequest.of(WORKSPACE, projection, ProjectionDiff.between(projection, projection), Collections.<PinProjection>emptyList())`; loop at most 2000 `step()` calls capturing the first idle frame; then run exactly 100 further `step()` calls.
- Assert: `firstIdleStep <= 2000`; for each of the 100 frames after the first idle frame, `idle().rms() <= 0.05` and `idle().max() <= 0.10`.
- Measured: first idle at step 419 (rms `0.0468`, max `0.0648`); converged rms `0.0018` at step 4000. Baseline never idles (rms ≈ `0.24`).
- Falsifiability: the unfixed implementation never idles within 2000 steps (rms ≈ 0.24), so `firstIdleStep <= 2000` fails.

### 4.5 Existing Suites

`R23`: All existing tests must pass unchanged:

- `TypedForcesShould` (13 tests)
- `BoundarySeparationShould` (7 tests)
- `GraphStreamBoundaryShould` (5 tests)
- `LayoutWorkerShould` (13 tests)
- `MapTierCorrectionShould` (7 tests)
- `PerceptualIdlePolicyShould` (5 tests)
- `LayoutSettleLoopShould` (38 tests)

That is the existing 88 layout/settle-loop tests (50 layout-package + 38 settle-loop). Tests 2, 3, 4, and 9 are only valid on the relationship-free reference fixture (all node prominence scales exactly `1.0`) and must not be reused on fixtures with relationship edges.

## 5. Non-Blocking Review Notes

### 5.1 Test 6 measurement point

T6's `> 10.0` assertion is evaluated **at the first idle frame after the unpin submit** (measured step 292 after unpin), where the formerly pinned node is `108.97` units from `(1059, -145)`. The design's quoted `122.04` is the converged value measured after ≈ 2100 further frames (≈ step 2392); it is not the assertion point. Both values are far above the 10-unit bound, so the assertion is insensitive to the measurement point.

### 5.2 Retained node-prominence asymmetry

Node particles still scale native repulsion by `separationRadius / 8.0` (factor `1.0`–`1.75`, `NodeProminence.MAX_SCALE = 1.75`). This one-sided weighting is a documented residual of the approved design and remains out of scope; it does not prevent idle on the reference fixture or the two-map fixture.

### 5.3 Barnes–Hut aggregation residual

`setQuality(0.10)` sets `viewZone = 2`, so `repulsionNLogN` always runs; only `nodesPerCell = 10` decides whether the n-tree root stays a single leaf. On the 17-particle crowded same-map relationship fixture (hub with 14 outgoing targets, prominence scale 1.75, single boundary): baseline never idles (rms 0.31); with this fix it idles once at step 183 but later exceeds the max threshold (max ≈ 0.156); with Barnes–Hut disabled (`viewZone = -1`) the same fix stays under both thresholds from step 162 at a persistent rms ≈ 0.0110, and only also removing the retained prominence asymmetry reaches rms 0.0000. This is a combined residual of n-tree aggregation discontinuities and the retained prominence weighting, not the anchor asymmetry this specification removes, and it is out of scope. The reference fixture and the 24-node two-map fixture are unaffected (both stay idle).

## 6. Verification

`V1`: Run `gradle :freeplane_plugin_graph:test` from the repository root (use the repository `gradle`, not `gradlew`; add `-PTestLoggingFull` for verbose failures). It must exit successfully.

`V2`: The run must include all existing 88 layout/settle-loop tests (list in §4.5) green, plus the 10 new tests (`TypedForcesShould` grows 13 → 16, `BoundarySeparationShould` grows 7 → 14; layout package 50 → 60, total 88 → 98).

`V3`: No production class outside the three files named in §2 may be modified, and no test method in §4.5 may be modified.

`V4` (manual acceptance, design §11): after a full build, run `BIN/freeplane.sh`, open the user's `math.fpg`, unpin a node, and observe the layout freeze with CPU returning to idle; confirm sibling boundaries and multiple map roots still appear visually separated. These manual checks are not part of the automated gate.

## 7. Out of Scope / Known Residuals

- Changing the cross-map displacement budget (`≤ 0.005`) that replaces repulsion for cross-map-linked particles.
- Retuning `K2`, `ATTRACTION_FACTOR`, rest lengths, or idle thresholds.
- Adding damping/inertia to the position-only integrator.
- Removing the node-prominence repulsion weighting.
- Addressing Barnes–Hut cell-transition re-excitation or changing the quality/view-zone settings.
- Any change to `frog`/`Freeplane` core, projection, geometry, or control-loop code.

## 8. Traceability

| Design reference | Spec requirement |
| --- | --- |
| §6.1 anchor scaling | `R1`, `R2`, `R3` |
| §6.2 explicit ancestry | `R4`, `R5`, `R6`, `R7` |
| §6.3 ancestor exclusion | `R8`–`R13` |
| §8 error handling | `R15`–`R19` |
| §9 tests 1–10 | `T1`–`T10` |
| §9 test 11 existing suites | `R23`, `V2` |
| §10 validation evidence | measured values in `T1`–`T10` |
| §11 rollout | `V1`–`V4` |
| §12 residuals | §5.2, §5.3, §7 |
