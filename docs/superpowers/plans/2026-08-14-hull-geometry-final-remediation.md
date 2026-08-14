# Hull Geometry Final Numerical Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for readability; controller state is canonical.

- **Task Identifier:** 2026-08-10-graph-workspace
- **Original backlog task:** Task 18, Compute deterministic hull and attachment geometry
- **Design:** `docs/superpowers/specs/2026-08-14-hull-geometry-final-remediation-design.md`
- **Terminal predecessor run:** `.superpowers/sdd/2026-08-13-hull-geometry-numerical-remediation`, revision 89 `FINAL_BLOCKED`
- **Starting production implementation:** `2fee7e4562be73888b31f90fd1bdb0b1d34ac8f9`
- **Original merge base:** `04d39279c4eed35254b0f234c8ec0c27c79a04bf`

**Goal:** Resolve canonical open findings F-11, F-12, and F-13 while preserving fixed findings F-1, F-9, and F-10 and every approved Task 18 geometry contract.

**Architecture:** Keep each correction private to its owning class. `NodeGeometry` owns exponent-tagged ray arithmetic, `HullGeometry` owns smoothing vectors, and `GraphGeometryEngine` owns origin-relative area centroid arithmetic. Add no shared numerical utility, public API, compatibility fallback, or parallel legacy path.

**Tech Stack:** Java 8 source and bytecode, JUnit 4, AssertJ, Java2D `Shape`/`Path2D`, Gradle, immutable projection values.

## Global Constraints

- Follow `AGENTS.md`: Java source and target compatibility are 8, UTF-8 encoding, four-space indentation, and tests use JUnit 4/AssertJ/Mockito.
- Use Java at `/home/henry/.sdkman/candidates/java/21.0.8-zulu`; every Gradle and JDK command sets this exact `JAVA_HOME` and prepends its `bin` directory to `PATH`. Use `gradle`, never Maven or the wrapper.
- Work only in `/data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-18-hull-geometry` on branch `2026-08-10-graph-workspace-task-18-hull-geometry`. Do not merge, rebase, amend, reset, or rewrite inherited commits.
- The predecessor run `.superpowers/sdd/2026-08-13-hull-geometry-numerical-remediation` is terminal at revision 89 `FINAL_BLOCKED`; never reopen or modify its canonical state or audit ledger.
- The successor implementation allowlist is exactly four existing paths: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/NodeGeometry.java`, `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/HullGeometry.java`, `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/GraphGeometryEngine.java`, and `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/HullGeometryShould.java`. Do not modify any fifth implementation or test path.
- Production geometry code may use only Java 8 primitives, `java.lang`, existing `java.util`, `java.awt.Shape`, `java.awt.geom.Path2D`, and immutable `org.freeplane.plugin.graph.projection` values already used by `GraphGeometryEngine`. Do not import `java.math`, third-party numerical libraries, Freeplane feature/view packages, Swing, GraphStream, adapters, mutable map models, or text metrics.
- Do not use `BigDecimal`, `Math.fma`, decimal strings, a new utility class, public helper, overload, fixture-specific branch, exception fallback, clamp, alternate centroid, old whole-coordinate path, or parallel legacy execution path. High-precision oracles remain external under `/tmp` and are never checked in.
- Preserve all public signatures and approved behavior: canonical polygon order, the absolute `1e-9` orientation/SAT policy, positive-zero separation, positive-X deterministic ties, exact immutable collections, defensive `Path2D` copies, fixed four-world-unit smoothing cap, and Java 8 bytecode.
- Treat every finite `LayoutPoint` accepted by a public geometry API as valid numerical input. Intermediate overflow, underflow, NaN, cancellation, or lost minor terms must not change a representable finite answer.
- Carry fixed canonical findings F-1, F-9, and F-10 unchanged into the final ledger. The final report's report-local F-10 was remapped to canonical F-13; do not overwrite or reopen canonical F-10.
- Each task is strict TDD. Add only its named regression before production edits, run exactly that regression at the inherited HEAD, and prove the specified behavioral RED. If the named test passes, fails to compile, or fails for a different mechanism, return `BLOCKED` before editing production code.
- Each task modifies only its two listed files and creates exactly one new, non-amended commit with the specified subject. Inspect `git diff --check`, the staged diff, commit parent, and worktree status before reporting.
- After each correction, run this exact fresh gate as two invocations because Gradle's `--tests` option belongs only to the `Test` task and is rejected by `verifyGraphBundle`:

```bash
env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-18-hull-geometry \
  :freeplane_plugin_graph:test \
  --tests '*HullGeometryShould' \
  --tests '*HullIntersectionShould' \
  -PTestLoggingFull --rerun-tasks

env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-18-hull-geometry \
  :freeplane_plugin_graph:verifyGraphBundle --rerun-tasks
```

- Aggregate every XML suite under `freeplane_plugin_graph/build/test-results/test`; require zero failures and zero errors. Record suites, tests, skips, failures, and errors rather than trusting Gradle's summary alone.
- Inspect the built plugin JAR: require `GraphGeometry`, `GraphGeometryEngine`, `HullGeometry`, `HullIntersection`, `LayoutPoint`, `LayoutPositions`, and `NodeGeometry` plus private inners; `javap -verbose` must report major version 52 for every public geometry class.
- Run `javap -public` for all seven classes; require no new public geometry API and no `GraphGeometry.labels()` method. Scan all seven production geometry files against the exact positive import whitelist and specifically require no `java.math` import.
- The final Frontier gate reviews the whole branch from `04d39279c4eed35254b0f234c8ec0c27c79a04bf` through the new HEAD and reconciles F-1, F-9, F-10, F-11, F-12, and F-13. Completion requires `SPEC: PASS`, `QUALITY: APPROVED`, all six findings absent, the full gate green, and a clean worktree.

## Task 1: Finish adjacent-double ray boundary rounding

**Implementer tier:** Capable

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/NodeGeometry.java:67-285`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/HullGeometryShould.java:690-770`

**Interfaces:**

- Consumes `NodeGeometry.of(LayoutPoint, double)` and `NodeGeometry.boundaryToward(LayoutPoint)` with finite center, target, and positive finite radius.
- Produces no new public API. The dominant-coordinate path must carry enough exponent-tagged norm/quotient remainder through `finalSum` to publish the correctly rounded binary64 coordinate, including ties-to-even at adjacent normal or subnormal values.
- Resolves canonical F-13. Preserve the fixed F-10 subtraction-residual, floor, R60886, skew-ray, axis, diagonal, and signed-zero behavior already tested in `HullGeometryShould`.

- [ ] **Step 1: Add the exact raw-bit regression before production edits**

Add this test next to the inherited boundary-rounding tests:

```java
@Test
public void roundsFiniteBoundaryRayToNearestEvenCoordinate() {
    NodeGeometry node = NodeGeometry.of(
        LayoutPoint.of(-2.30665597377219E56, -2.2117294275241294E-19),
        1.0283265339240514E57);

    LayoutPoint boundary = node.boundaryToward(
        LayoutPoint.of(7.09268585234678E-75, -2.3275574432766924E12));

    assertThat(Double.doubleToRawLongBits(boundary.x())).isEqualTo(0x4bc043fc003baf8bL);
    assertThat(Double.doubleToRawLongBits(boundary.y())).isEqualTo(0xc2a2dfe8bc8ed4feL);
}
```

Do not compute the expectation with checked-in decimal arithmetic. The literals were independently rounded from exact binary inputs at 1,000, 3,000, and 6,000 decimal digits.

- [ ] **Step 2: Prove the RED mechanism at the inherited HEAD**

```bash
env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-18-hull-geometry \
  :freeplane_plugin_graph:test \
  --tests '*HullGeometryShould.roundsFiniteBoundaryRayToNearestEvenCoordinate' \
  -PTestLoggingFull --rerun-tasks
```

Expected behavioral RED: actual X bits are `0x4bc043fc003baf8c`, one ULP above expected `0x4bc043fc003baf8b`; the Y assertion is already equal. A compile error or different failure is invalid RED evidence.

- [ ] **Step 3: Carry the full dominant quotient correction to final rounding**

Correct only the private arithmetic used by `dominantCoordinate` and `finalSum`:

- Represent the norm product, scaled-other square, quotient, and nonzero remainder with exponent-tagged components rather than collapsing the quotient correction to `quotientScaled`, `quotientTailScaled`, and one summed tail before the final addition.
- Continue quotient correction from the represented remainder until the remainder is zero or its represented contribution is provably below the final coordinate's rounding decision; a fixed component bound is permitted only when remaining components are still retained for the final sign/tie decision.
- Preserve the exact sign and exponent of every correction through `centerCoordinate + sign * radius`; do not materialize a correction at an exponent where it underflows before `finalSum` sees it.
- Route normal and subnormal publication through one explicit ties-to-even operation. Preserve `roundWithTail` neighbor-gap behavior at signed powers of two and the `-Double.MIN_VALUE` floor fixture.
- Keep the implementation private to `NodeGeometry`; do not copy `HullGeometry` helpers across classes or add a general utility.

An external `/tmp` oracle may use exact `BigDecimal(double)` values at multiple precisions to audit random finite rays, but production and checked-in test code must remain dependency-pure.

- [ ] **Step 4: Run focused and full green gates**

Run the named regression alone, then the exact fresh gate in Global Constraints. Require the new raw-bit fixture, every inherited NodeGeometry fixture, both geometry test classes, bundle verification, XML aggregation, Java 8 bytecode, API inventory, import whitelist, and `git diff --check` to pass.

- [ ] **Step 5: Commit Task 1**

```bash
cd /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-18-hull-geometry
git add -- \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/NodeGeometry.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/HullGeometryShould.java
git diff --cached --check
git commit -m "2026-08-10-graph-workspace: Finish hull ray rounding"
git status --porcelain
git diff --check
```

Expected: one new direct-child commit, no amendment, and a clean worktree.

## Task 2: Preserve representable smoothing minor components

**Implementer tier:** Capable

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/HullGeometry.java:520-580`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/HullGeometryShould.java:750-815`

**Interfaces:**

- Consumes canonical finite polygons through `HullGeometry.of(List<LayoutPoint>, LayoutPoint)` and publishes a defensive `Shape` through `smoothPath()`.
- Produces no new public API. `pointAlong` must preserve each exponent-tagged edge component through direction normalization and multiplication by the requested cut distance; only the final endpoint coordinates may round to binary64.
- Resolves canonical F-12. Preserve fixed four-world-unit smoothing, ordinary and huge rectangles, one-ULP-over-four fallback behavior, canonical path order, and defensive path copies.

- [ ] **Step 1: Add the exact PathIterator regression before production edits**

Add this test next to `limitsEverySmoothPathTangentToFourWorldUnits`:

```java
@Test
public void preservesSubnormalSmoothingTangentAtExtremeCoordinates() {
    HullGeometry hull = HullGeometry.of(Arrays.asList(
        LayoutPoint.of(-1.0e308, 0.0),
        LayoutPoint.of(1.0e308, Math.scalb(1.0, -52)),
        LayoutPoint.of(1.0e308, 1.0e100),
        LayoutPoint.of(-1.0e308, 1.0e100)), LayoutPoint.of(0.0, 0.0));
    PathIterator iterator = hull.smoothPath().getPathIterator(null);
    double[] coordinates = new double[6];

    assertThat(iterator.currentSegment(coordinates)).isEqualTo(PathIterator.SEG_MOVETO);
    iterator.next();
    assertThat(iterator.currentSegment(coordinates)).isEqualTo(PathIterator.SEG_QUADTO);
    assertThat(coordinates[2]).isEqualTo(-1.0e308);
    assertThat(Double.doubleToRawLongBits(coordinates[3])).isEqualTo(1L);
}
```

The second segment is the first corner's quadratic; `coordinates[2]` and `[3]` are its outgoing endpoint. The exact mathematical endpoint rounds to `(-1.0e308, Double.MIN_VALUE)`.

- [ ] **Step 2: Prove the RED mechanism at the inherited HEAD**

```bash
env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-18-hull-geometry \
  :freeplane_plugin_graph:test \
  --tests '*HullGeometryShould.preservesSubnormalSmoothingTangentAtExtremeCoordinates' \
  -PTestLoggingFull --rerun-tasks
```

Expected behavioral RED: `coordinates[3]` has raw bits `0` instead of `1` because the overflow fallback divides `2^-52` by the `1e308` world-coordinate scale before applying the four-unit cut. The polygon must construct successfully; a fixture-validation or compile failure is invalid RED evidence.

- [ ] **Step 3: Keep edge components tagged through the cut-distance product**

Correct only `HullGeometry` private smoothing arithmetic:

- Compute each edge difference without overflowing and retain its leading and residual terms with explicit exponents. Reuse or extend the class-private `ScaledExpansion` representation; do not introduce a shared helper class.
- Compute the represented squared length and normalized cut displacement without materializing the minor component at the dominant edge exponent. Multiply the component by `distance` before final binary64 rounding so the `2^-1075` mathematical displacement ties upward to `Double.MIN_VALUE` under ties-to-even.
- Add each represented displacement to the corner with compensated, exponent-aware final rounding. Preserve `+0.0` normalization where the exact rounded displacement is zero.
- Keep the final four-world-unit guard, but compare the represented displacement magnitude rather than re-subtracting rounded endpoints in a way that rejects a valid tangent. Never return `from` merely because an intermediate representation overflowed.
- Do not change canonicalization, smoothing cut selection, `Path2D` segment ordering, orientation, nearest-boundary projection, or the public defensive clone.

Use an external exact-binary oracle under `/tmp` to verify the endpoint at multiple precisions and audit mixed-exponent edges; do not check the oracle into the repository.

- [ ] **Step 4: Run focused and full green gates**

Run the named regression alone, then the exact fresh gate in Global Constraints. Require all inherited smoothing/path tests, both geometry classes, bundle verification, XML aggregation, bytecode/API/import checks, and `git diff --check` to pass.

- [ ] **Step 5: Commit Task 2**

```bash
cd /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-18-hull-geometry
git add -- \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/HullGeometry.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/HullGeometryShould.java
git diff --cached --check
git commit -m "2026-08-10-graph-workspace: Preserve subnormal hull tangents"
git status --porcelain
git diff --check
```

Expected: one new direct-child commit, no amendment, and a clean worktree.

## Task 3: Stabilize large-offset enclosure centroids

**Implementer tier:** Capable

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/GraphGeometryEngine.java:185-205`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/HullGeometryShould.java:170-270`

**Interfaces:**

- Consumes `GraphGeometryEngine.computeHulls(GraphProjection, LayoutPositions)`, canonical polygons produced by `clipHalfPlanes`, and immutable finite `LayoutPoint` values.
- Produces no new public API. For nonempty enclosures, `centroid(List<LayoutPoint>)` must retain the polygon area centroid while evaluating area and first moments relative to one deterministic local origin with safe exponent-tagged arithmetic.
- Resolves canonical F-11. Preserve enclosure recursion/order, fixed support normals and clearance, empty-enclosure anchors, node prominence radii, polygon canonicalization, and exact map/list immutability.

- [ ] **Step 1: Add the public large-offset centroid regression before production edits**

Add this test near the existing public `GraphGeometryEngine` hull tests:

```java
@Test
public void computesRepresentableCentroidForLargeOffsetHull() {
    List<ProjectedNode> nodes = Arrays.asList(node("n1"), node("n2"), node("n3"), node("n4"));
    EnclosureHullKey hullKey = hullKey("large-offset-hull");
    List<ProjectedNodeKey> directNodes = Arrays.asList(
        nodes.get(0).key(), nodes.get(1).key(), nodes.get(2).key(), nodes.get(3).key());
    ProjectedEnclosure enclosure = enclosure(hullKey, Optional.<EnclosureHullKey>empty(),
        directNodes, Collections.<EnclosureHullKey>emptyList());
    double base = 1.0e200;
    double delta = Math.scalb(1.0, 620);
    Map<ProjectedNodeKey, LayoutPoint> nodePositions = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
    nodePositions.put(nodes.get(0).key(), LayoutPoint.of(base, base));
    nodePositions.put(nodes.get(1).key(), LayoutPoint.of(base + delta, base));
    nodePositions.put(nodes.get(2).key(), LayoutPoint.of(base + delta, base + delta));
    nodePositions.put(nodes.get(3).key(), LayoutPoint.of(base, base + delta));

    GraphGeometry geometry = compute(
        projection(nodes, Collections.singletonList(enclosure), Collections.<ProjectedEdge>emptyList()),
        LayoutPositions.of(nodePositions, Collections.singletonMap(hullKey, LayoutPoint.of(base, base))));

    assertThat(geometry.hulls().get(hullKey).labelAnchor())
        .isEqualTo(LayoutPoint.of(base + delta * 0.5, base + delta * 0.5));
}
```

The node radius and 16-world-unit clearance are below the ULP at `base`, so the generated hull remains symmetric about the asserted representable centroid.

- [ ] **Step 2: Prove the RED mechanism at the inherited HEAD**

```bash
env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-18-hull-geometry \
  :freeplane_plugin_graph:test \
  --tests '*HullGeometryShould.computesRepresentableCentroidForLargeOffsetHull' \
  -PTestLoggingFull --rerun-tasks
```

Expected behavioral RED: `computeHulls` throws `IllegalArgumentException: Layout coordinates must be finite` from `GraphGeometryEngine.centroid` after absolute shoelace products become `Infinity`/`NaN`. Projection or fixture validation failure is invalid RED evidence.

- [ ] **Step 3: Evaluate area and first moments in a deterministic local scale**

Correct only `GraphGeometryEngine.centroid` and private helpers nested in the same class:

- Choose `polygon.get(0)` as the deterministic local origin. Represent every `vertex - origin` coordinate difference with leading/residual terms and explicit power-of-two exponents so opposite-sign subtraction cannot overflow and minor terms do not underflow.
- Evaluate each local shoelace cross product with split products or equivalent error-free transforms. Accumulate twice-area in an exponent-tagged expansion; never decide from an infinite or NaN raw sum.
- Accumulate both local first moments from the same represented cross terms. Scale coordinate, cross, and moment products by powers of two before multiplication so all intermediates remain finite; preserve the scale tags until division.
- Divide each represented first moment by `3 * representedTwiceArea` to obtain the local centroid. Undo only the coordinate scale, then add the local centroid to the origin with compensated ties-to-even rounding.
- Retain the polygon area centroid for every canonical convex polygon. Do not replace it with a vertex average, bounding-box midpoint, input anchor, clamp, exception fallback, or legacy absolute-shoelace path.
- Keep helpers private to `GraphGeometryEngine`; add no public API or utility class and no new imports outside the positive whitelist.

An external `/tmp` oracle may compare origin-relative exact binary shoelace results across translations and exponent ranges. Checked-in code must use only the literal public regression.

- [ ] **Step 4: Run focused and full green gates**

Run the named regression alone, then the exact fresh gate in Global Constraints. Require inherited parent/child ordering, containment, empty enclosure, prominence, immutability, and duplicate-key tests; both geometry classes; bundle verification; XML aggregation; bytecode/API/import checks; and `git diff --check` to pass.

- [ ] **Step 5: Commit Task 3 and verify successor topology**

```bash
cd /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-18-hull-geometry
git add -- \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/GraphGeometryEngine.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/HullGeometryShould.java
git diff --cached --check
git commit -m "2026-08-10-graph-workspace: Stabilize large-offset hull centroids"
git status --porcelain
git diff --check
git log --format='%H %P %s' --reverse HEAD~3..HEAD
```

Expected: exactly three new implementation commits after this committed plan, each a direct child of its predecessor; no inherited commit amended; the union of successor implementation changes is exactly the four-path allowlist.
