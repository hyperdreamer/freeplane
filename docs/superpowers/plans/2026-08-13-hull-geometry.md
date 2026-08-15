# Deterministic Hull And Attachment Geometry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for readability; controller state is canonical.

- **Task Identifier:** 2026-08-10-graph-workspace
- **Original backlog task:** Task 18, Compute deterministic hull and attachment geometry
- **Base commit:** `04d39279c4eed35254b0f234c8ec0c27c79a04bf`

**Goal:** Publish immutable world-space node and enclosure geometry with prominence-scaled circular nodes, bounded deterministic convex hulls, exact endpoint attachment, and exact minimum separating translations.

**Architecture:** `GraphGeometryEngine` is the one deep geometry module: it consumes only an immutable `GraphProjection` and immutable `LayoutPositions`, computes nodes once, recursively computes direct enclosure children before parents, and publishes ordered immutable `GraphGeometry`. `HullGeometry` keeps a canonical convex polygon for containment, hit testing, attachment, and SAT while deriving a defensive smooth paint path from that polygon. No geometry type reads Freeplane models, text metrics, layout-engine internals, or canvas state.

**Tech Stack:** Java 8 source/bytecode, JUnit 4, AssertJ, Java2D `Shape`/`Path2D` for defensive paint paths, pure projection values, Gradle.

## Global Constraints

- Follow `AGENTS.md`: Java source and target compatibility are 8, UTF-8 encoding, four-space indentation, JUnit 4/AssertJ/Mockito tests, and builds use escalated `gradle`, not Maven or the Gradle wrapper.
- Use Java at `/home/henry/.sdkman/candidates/java/21.0.8-zulu`; every Gradle and JDK command sets that exact `JAVA_HOME` and prepends its `bin` directory to `PATH`.
- Work only in `/data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-18-hull-geometry` on branch `2026-08-10-graph-workspace-task-18-hull-geometry`, based on `04d39279c4eed35254b0f234c8ec0c27c79a04bf`.
- The implementation allowlist is exactly nine paths: `LayoutPoint.java`, `LayoutPositions.java`, `NodeGeometry.java`, `HullGeometry.java`, `HullIntersection.java`, `GraphGeometry.java`, `GraphGeometryEngine.java`, `HullGeometryShould.java`, and `HullIntersectionShould.java`, at the paths listed in Task 1. Do not modify projection classes, adapter classes, build files, specs, backlog text, translations, resources, or any tenth implementation path.
- Keep the `geometry` package pure. It may depend on `java.lang`, `java.util`, `java.awt.Shape`, `java.awt.geom.Path2D`, and immutable `org.freeplane.plugin.graph.projection` values only. It must not import `org.freeplane.features..`, `org.freeplane.view..`, Swing, GraphStream, adapter classes, mutable map types, or text metrics.
- Every public geometry value is immutable, deeply comparable, and deterministic. Reject nulls, non-finite coordinates, nonpositive radii, missing keys, duplicate keys, concave/degenerate polygons, and mismatched projection/position key sets. Normalize negative zero to positive zero.
- Preserve published order. `LayoutPositions` preserves caller insertion order; `GraphGeometry.nodes()` and `hulls()` iterate in `GraphProjection.nodes()` and `GraphProjection.enclosures()` order even though hulls are computed bottom-up. Return unmodifiable maps/lists, and return a new defensive `Path2D.Double` as `Shape` on every `smoothPath()` call.
- Projected nodes are circles, matching the approved window mockup. The world-space base radius is exactly `8.0`; effective radius is `8.0 * projection.prominence().get(nodeKey).scale()`. Do not infer prominence from edges, labels, degree, layout, or raw model links in this task.
- Enclosure clearance is exactly `16.0` world units and corner smoothing tangent length is at most `4.0` world units. Prominence changes only the enclosed node radius; it never scales the `16.0` hull clearance or enlarges an enclosure from its own relationship count.
- Build each exact enclosure polygon from eight fixed outward support normals at 45-degree intervals. A node contributes analytic circle support `dot(center, normal) + radius`; a child enclosure contributes the maximum dot product of its exact polygon. Add `16.0` to each maximum support and intersect the eight half-planes in fixed normal order. This produces a bounded convex polygon that contains every direct child without accumulating unbounded vertices through nesting.
- An enclosure with no direct node or child enclosure uses its `LayoutPositions.anchors()` point as a point-shaped child before adding the same `16.0` clearance. Its `labelAnchor()` is exactly that supplied anchor. A nonempty hull uses the area centroid of its exact polygon as `labelAnchor()`.
- Derive the smooth closed paint path from the exact polygon in canonical order: on each incident edge stop at most `4.0` units before the vertex, use one quadratic segment through that vertex, continue after it, and close the path. The `4.0` corner cut remains inside the fixed `16.0` clearance and must not intersect any direct child shape.
- `GraphGeometry.edgeAttachment(endpoint, toward)` uses the prominence-scaled circle boundary for node endpoints. It maps every addressable `EnclosureKey` in an `EnclosureHullKey.endpointKeys()` list to that visible hull and returns the Euclidean-nearest point on the exact hull boundary to `toward`, with canonical polygon-edge order breaking equal-distance ties. If `toward` equals a node center, return the circle's positive-X boundary. Reject an endpoint not present in the geometry.
- `HullIntersection.minimumSeparatingTranslation(a, b)` returns the minimum vector to add to hull `b` so the two exact convex polygons have no positive-area overlap. Use SAT axes from both polygons, canonicalize each axis to positive X or, for zero X, positive Y, deduplicate and sort horizontal-first for deterministic ties. Disjoint or merely touching hulls return exactly `(0.0, 0.0)`; equal-distance choices use sorted-axis order, then the positive canonical direction.
- Task 18 does not create `LabelPlacement`, which belongs to Task 19 and is outside this task's exact allowlist. Therefore `GraphGeometry` in this task intentionally has no `labels()` method. Task 19 must add the typed ordered label map and `labels()` when it creates `LabelPlacement`; do not add an `Object`, wildcard, nested placeholder, compatibility overload, or temporary label type.
- Use test-driven development: create only the two test files first, run both focused classes with `--rerun-tasks`, and observe failure because the geometry package does not exist before creating any production file.
- After green, run the named prominence mutant: record SHA-256 for all nine implementation files, temporarily construct every node with the unscaled `8.0` radius, prove `scalesNodeExtentFromPublishedProminenceWithoutScalingHullClearance` fails for the radius/attachment/hull extent assertions, immediately restore exact hashes, verify no mutant diff remains, and rerun both focused classes green.
- Before staging, assert the index is empty. Stage exactly the nine allowlist paths and compare `git diff --cached --name-only` byte-for-byte to the sorted allowlist. Commit with exactly `2026-08-10-graph-workspace: Add graph hull geometry`.

## Task 1: Compute deterministic hull and attachment geometry

**Implementer tier:** Advanced

**Files:**

- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/LayoutPoint.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/LayoutPositions.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/NodeGeometry.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/HullGeometry.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/HullIntersection.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/GraphGeometry.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/GraphGeometryEngine.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/HullGeometryShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/HullIntersectionShould.java`

**Interfaces:**

```java
package org.freeplane.plugin.graph.geometry;

public final class LayoutPoint {
    public static LayoutPoint of(double x, double y);
    public double x();
    public double y();
}
```

`LayoutPoint.of` rejects non-finite coordinates and normalizes either signed zero to positive zero. Equality and hash code compare the normalized coordinate values; `toString()` includes coordinates only.

```java
public final class LayoutPositions {
    public static LayoutPositions of(
        Map<ProjectedNodeKey, LayoutPoint> nodes,
        Map<EnclosureHullKey, LayoutPoint> anchors);
    public Map<ProjectedNodeKey, LayoutPoint> nodes();
    public Map<EnclosureHullKey, LayoutPoint> anchors();
}
```

`LayoutPositions` makes ordered unmodifiable copies and has deep value equality. `GraphGeometryEngine.computeHulls` requires these key sets to equal the projection node and enclosure key sets exactly; extra keys are stale input, not ignored compatibility state.

```java
public final class NodeGeometry {
    public static NodeGeometry of(LayoutPoint center, double radius);
    public LayoutPoint center();
    public double radius();
    public double minX();
    public double minY();
    public double maxX();
    public double maxY();
    public boolean contains(LayoutPoint point);
    public LayoutPoint boundaryToward(LayoutPoint toward);
}
```

`NodeGeometry` is a circle with a finite positive radius. `contains` includes the boundary. `boundaryToward` follows the center-to-target ray and returns positive X when the ray has zero length.

```java
public final class HullGeometry {
    public static HullGeometry of(List<LayoutPoint> exactPolygon, LayoutPoint labelAnchor);
    public List<LayoutPoint> exactPolygon();
    public LayoutPoint labelAnchor();
    public Shape smoothPath();
    public double minX();
    public double minY();
    public double maxX();
    public double maxY();
    public boolean contains(LayoutPoint point);
    public LayoutPoint nearestBoundaryPoint(LayoutPoint toward);
}
```

`HullGeometry.of` accepts one simple strictly convex polygon with at least three unique vertices. It removes a repeated closing point if present, removes collinear intermediate vertices, canonicalizes counter-clockwise winding, rotates the list to start at the smallest `(x, then y)` point, and rejects degenerate or concave input. `contains` includes the boundary. `nearestBoundaryPoint` projects onto every closed segment, clamps to segment endpoints, and uses canonical edge order for equal squared distances. `smoothPath()` returns an independent closed Java2D path containing quadratic corner segments; callers cannot mutate stored state.

```java
public final class GraphGeometry {
    public static GraphGeometry of(
        Map<ProjectedNodeKey, NodeGeometry> nodes,
        Map<EnclosureHullKey, HullGeometry> hulls);
    public Map<ProjectedNodeKey, NodeGeometry> nodes();
    public Map<EnclosureHullKey, HullGeometry> hulls();
    public LayoutPoint edgeAttachment(ProjectedEndpointKey endpoint, LayoutPoint toward);
}

public final class GraphGeometryEngine {
    public GraphGeometry computeHulls(GraphProjection projection, LayoutPositions positions);
}

public final class HullIntersection {
    public static LayoutPoint minimumSeparatingTranslation(HullGeometry a, HullGeometry b);
}
```

`GraphGeometry.of` derives its private addressable-enclosure lookup exclusively from each hull key's ordered `endpointKeys()`, rejects an exact `EnclosureKey` assigned to two hulls, and publishes no mutable collection. All six value classes implement deep `equals`/`hashCode` and non-content-bearing `toString()` output suitable for diagnostics.

- [ ] **Step 1: Write failing immutable geometry and engine tests**

Create `HullGeometryShould` and name the production break each test catches. Use hand-derived literal coordinates and real projection values; do not mirror production geometry helpers in expected-value builders. Include these named tests:

```java
@Test public void rejectsNonFinitePointsInvalidRadiiAndMutableOrMismatchedPositionState();
@Test public void computesChildHullsBeforeParentsButPublishesProjectionOrder();
@Test public void containsEveryDirectNodeAndChildHullWithFixedClearance();
@Test public void createsASmoothDeterministicClosedPathWithoutCuttingDirectChildren();
@Test public void anchorsAnEmptyEnclosureAtItsSuppliedLayoutAnchor();
@Test public void canonicalizesEquivalentConvexPolygonsAndPublishesDeepImmutableValues();
@Test public void scalesNodeExtentFromPublishedProminenceWithoutScalingHullClearance();
@Test public void containsACappedProminenceNodeInItsDirectHull();
@Test public void attachesToTheScaledNodeBoundaryAndNearestExactHullBoundary();
@Test public void mapsEveryAddressableAncestorInAUnaryHullToOneVisibleBoundary();
```

Required assertions:

- `LayoutPoint` rejects NaN/infinities and normalizes `-0.0`; `NodeGeometry` rejects zero/negative/non-finite radius; all map/list accessors reject null entries and cannot be mutated.
- A projection list ordered parent then child produces a hull map in that same order, while the parent's exact polygon contains every child exact-polygon vertex. Reversing the projection enclosure input while preserving the same parent/direct-child keys produces geometrically equal hull values for each key, proving computation is dependency-driven rather than list-direction-driven.
- Sample every node circle at least every 5 degrees and assert each sample is inside its directly containing exact hull. Assert all vertices of a direct child hull are inside its parent. For a single unscaled node at `(0, 0)`, cardinal hull support is node radius `8.0` plus clearance `16.0`, exactly `24.0`.
- Inspect `smoothPath().getPathIterator(null)`: it starts with one move, contains at least one quadratic segment, ends with close, and has identical segments across repeated computations. Use Java2D containment or dense path sampling to prove it never enters a direct node circle or child exact polygon.
- An empty enclosure centered at anchor `(30, -20)` has that exact `labelAnchor`, a finite closed exact polygon, and contains the anchor. It uses the same `16.0` support clearance as a nonempty enclosure.
- Equivalent polygons passed with another cyclic start, opposite winding, a repeated closing point, and collinear intermediate points canonicalize to equal values/hash codes. A concave or zero-area polygon is rejected. Mutating source collections after construction changes no value.
- Build a real `GraphProjection.projected` with one source reaching fourteen distinct targets so `NodeProminence.scale()` is capped at `1.75`. Assert source radius `14.0`, an unconnected node radius `8.0`, source attachment toward positive X at center X plus `14.0`, and direct hull support at source radius plus exactly `16.0`. This named test is the required prominence mutant detector.
- For a unary collapsed enclosure whose `EnclosureHullKey` holds two addressable `EnclosureKey` values, attachments for both exact endpoints are equal and lie on the one visible exact boundary. A missing node or enclosure endpoint throws `IllegalArgumentException` rather than returning an anchor or center fallback.

- [ ] **Step 2: Write failing exact intersection tests**

Create `HullIntersectionShould` with literal rectangle, diamond, and triangle polygons. Include these named tests:

```java
@Test public void returnsZeroForDisjointOrMerelyTouchingHulls();
@Test public void returnsTheExactMinimumTranslationAppliedToTheSecondHull();
@Test public void usesAxesFromBothConvexPolygons();
@Test public void resolvesCoincidentAndEqualOverlapTiesDeterministically();
@Test public void translatedHullHasNoPositiveAreaIntersectionAndReverseOrderNegatesTheVector();
```

Required assertions:

- Axis-aligned `[0,10] x [0,10]` and `[8,18] x [0,10]` return exactly `(2, 0)` for translation of the second hull. A gap or boundary touch returns exact positive-zero components.
- A rotated diamond/triangle case fails if SAT uses axes from only one polygon. Apply the returned vector to every second-polygon point, rebuild the hull, and assert a second SAT call returns zero.
- Coincident equal squares choose positive X under the horizontal-first canonical-axis rule. Repeated calls and cyclic/reversed polygon forms return the same vector.
- For noncoincident overlaps without an exact tie, swapping arguments negates the vector within `1e-9`. Do not require antisymmetry for the intentionally positive-direction coincident tie.

- [ ] **Step 3: Run focused red and verify the failure reason**

```bash
env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-18-hull-geometry \
  :freeplane_plugin_graph:test \
  --tests '*HullGeometryShould' \
  --tests '*HullIntersectionShould' \
  -PTestLoggingFull --rerun-tasks
```

Expected: FAIL because the `org.freeplane.plugin.graph.geometry` production package and its public interfaces do not exist. Confirm the failure is missing production behavior/API, not malformed projection fixtures, an unrelated baseline regression, or a test compilation error outside the missing geometry types.

- [ ] **Step 4: Implement immutable primitives and canonical hull values**

Implement `LayoutPoint`, `LayoutPositions`, `NodeGeometry`, and `HullGeometry` with the interfaces and validation above. Keep vector, cross-product, segment projection, canonicalization, smoothing, and point-in-convex-polygon helpers private to the classes that own them; do not add a public utility class or a tenth file.

For polygon canonicalization, use a `1e-9` geometry epsilon only for orientation/collinearity and equal-distance tie decisions. Never round or quantize published coordinates. Reject a genuinely clockwise-to-counter-clockwise sign change after collinear removal. Derive bounds and the smooth path once in the constructor, store only immutable scalar/list data, and return a new path copy from `smoothPath()`.

- [ ] **Step 5: Implement bottom-up hull fitting and endpoint geometry**

In `GraphGeometryEngine`, first build ordered indexes for projected nodes and enclosures and validate exact key coverage in positions and prominence. Create every `NodeGeometry` in projected-node order. Compute hulls recursively by `directEnclosures()` with `visiting` and `complete` sets so missing children and cycles fail explicitly; then republish completed values in projection-enclosure order.

Use the eight fixed unit normals `(1,0)`, `(sqrt(1/2),sqrt(1/2))`, `(0,1)`, and their remaining 45-degree rotations in that exact counter-clockwise order. For each normal, take the maximum analytic support across direct node circles and exact child polygons, or the anchor point for an empty enclosure, then add `16.0`. Form a finite starting rectangle from the positive/negative cardinal constraints and clip it in fixed order against all eight half-planes with Sutherland-Hodgman clipping. Pass the resulting polygon and the specified anchor/centroid to `HullGeometry.of`.

Implement `GraphGeometry` as ordered defensive maps plus one private `EnclosureKey -> EnclosureHullKey` lookup. Resolve node attachments with `NodeGeometry.boundaryToward` and enclosure attachments with `HullGeometry.nearestBoundaryPoint`; never fall back across endpoint kinds or inspect projection labels.

- [ ] **Step 6: Implement exact deterministic SAT translation**

In `HullIntersection`, enumerate each closed polygon edge from both hulls, skip zero-length edges, derive its perpendicular unit axis, canonicalize sign, deduplicate axes within `1e-9`, and sort by descending X then descending Y so positive X wins equal-overlap ties before positive Y and diagonals remain deterministic.

Project both polygons on each axis. If any interval has overlap less than or equal to `1e-9`, return zero. Otherwise compute both exact translations for moving `b`: positive distance `aMax - bMin` and negative distance `bMax - aMin`; choose the smaller magnitude, with positive canonical direction on equality. Return the candidate with globally smallest squared magnitude, retaining sorted-axis order on equality.

- [ ] **Step 7: Run focused green and inspect implementation scope**

```bash
env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-18-hull-geometry \
  :freeplane_plugin_graph:test \
  --tests '*HullGeometryShould' \
  --tests '*HullIntersectionShould' \
  -PTestLoggingFull --rerun-tasks

git -C /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-18-hull-geometry status --short
git -C /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-18-hull-geometry diff --check
```

Expected: both focused classes pass with zero failures/errors, status names exactly the nine implementation paths because this plan is already committed, and `git diff --check` is clean.

- [ ] **Step 8: Prove prominence propagation with the isolated mutant**

Record SHA-256 for all nine implementation files. Temporarily replace only the effective node-radius expression `8.0 * prominence.scale()` with constant `8.0`. Run only:

```bash
env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-18-hull-geometry \
  :freeplane_plugin_graph:test \
  --tests '*HullGeometryShould.scalesNodeExtentFromPublishedProminenceWithoutScalingHullClearance' \
  -PTestLoggingFull --rerun-tasks
```

Expected: FAIL because the capped source radius, scaled attachment, and hull extent remain unscaled while the fixed `16.0` clearance assertion still identifies the intended decomposition. Immediately apply the inverse patch, verify all nine files exactly match their recorded SHA-256 values, confirm no mutant diff remains, and rerun both focused classes green. A failure caused only by compilation or changed test setup is not valid mutant evidence.

- [ ] **Step 9: Run module, bundle, purity, bytecode, and API gates**

```bash
env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-18-hull-geometry \
  :freeplane_plugin_graph:test :freeplane_plugin_graph:verifyGraphBundle \
  -PTestLoggingFull --rerun-tasks
```

Required evidence:

- Aggregate every JUnit XML suite under `freeplane_plugin_graph/build/test-results/test`; require zero failures/errors and explicitly report suites, tests, and skips.
- `verifyGraphBundle` passes and the built plugin JAR contains all seven new production classes.
- `javap -verbose` reports class-file major version exactly `52` for all seven production classes.
- `javap -public` for each class matches the interfaces above and exposes no Freeplane mutable type, GraphStream type, Swing type, mutable collection implementation, or label placeholder. `GraphGeometry` has no `labels()` method in this task.
- Search the seven production files for imports from `org.freeplane.features`, `org.freeplane.view`, `org.graphstream`, `javax.swing`, and `org.freeplane.plugin.graph.adapter`; require no matches.
- `git diff --check` passes; HEAD remains the pinned plan commit; the index is empty; the worktree has exactly the nine allowlisted implementation paths.

- [ ] **Step 10: Commit the exact implementation allowlist**

```bash
cd /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-18-hull-geometry
test -z "$(git diff --cached --name-only)"
git add -- \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/LayoutPoint.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/LayoutPositions.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/NodeGeometry.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/HullGeometry.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/HullIntersection.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/GraphGeometry.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/GraphGeometryEngine.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/HullGeometryShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/HullIntersectionShould.java
printf '%s\n' \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/GraphGeometry.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/GraphGeometryEngine.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/HullGeometry.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/HullIntersection.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/LayoutPoint.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/LayoutPositions.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/NodeGeometry.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/HullGeometryShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/HullIntersectionShould.java \
  | sort > /tmp/task18-expected.txt
git diff --cached --name-only | sort > /tmp/task18-actual.txt
cmp /tmp/task18-expected.txt /tmp/task18-actual.txt
git diff --cached --check
git commit -m "2026-08-10-graph-workspace: Add graph hull geometry"
```

Expected: one implementation commit above the plan commit, exactly nine staged implementation paths, and a clean index/worktree after commit.
