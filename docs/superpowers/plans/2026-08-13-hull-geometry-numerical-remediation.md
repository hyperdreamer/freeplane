# Hull Geometry Numerical Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for readability; controller state is canonical.

- **Task Identifier:** 2026-08-10-graph-workspace
- **Original backlog task:** Task 18, Compute deterministic hull and attachment geometry
- **Recovery source:** terminal run `.superpowers/sdd/2026-08-13-hull-geometry` at revision 25 `FINAL_BLOCKED`
- **Original merge base:** `04d39279c4eed35254b0f234c8ec0c27c79a04bf`
- **Recovery starting implementation:** `d1f0e46e1f08904702c2df2f6c849965b62c4e31`

**Goal:** Resolve the six load-bearing Task 18 final-rereview residuals for robust finite `double` polygon predicates, circle rays, smoothing, boundary projection, SAT, and package dependency purity.

**Architecture:** Preserve the public geometry API and the original canonical polygon/SAT design. Keep robust arithmetic private to the class that owns each operation: `HullGeometry` owns orientation, smoothing, projection, and distance comparison; `NodeGeometry` owns exponent-aware ray normalization; `HullIntersection` owns origin-relative SAT projections. Use only Java 8 `double`/`long` primitives, `java.lang`, and existing `java.util` collections; do not introduce a utility class or an additional production path.

**Tech Stack:** Java 8 source and bytecode, JUnit 4, AssertJ, Java2D `Shape`/`Path2D`, Gradle, immutable projection values.

## Global Constraints

- Follow `AGENTS.md`: Java source and target compatibility are 8, UTF-8 encoding, four-space indentation, JUnit 4/AssertJ/Mockito tests, and builds use escalated `gradle`, not Maven or the Gradle wrapper.
- Use Java at `/home/henry/.sdkman/candidates/java/21.0.8-zulu`; every Gradle and JDK command sets that exact `JAVA_HOME` and prepends its `bin` directory to `PATH`.
- Work only in `/data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-18-hull-geometry` on branch `2026-08-10-graph-workspace-task-18-hull-geometry`; do not merge, rebase, amend, or rewrite commits from the terminal source run.
- The recovery implementation allowlist is exactly five existing paths: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/HullGeometry.java`, `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/NodeGeometry.java`, `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/HullIntersection.java`, `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/HullGeometryShould.java`, and `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/HullIntersectionShould.java`. Do not modify any sixth implementation path.
- Keep the `geometry` package pure. Its positive dependency whitelist is exactly `java.lang`, `java.util`, `java.awt.Shape`, `java.awt.geom.Path2D`, and immutable `org.freeplane.plugin.graph.projection` values. In particular, do not import `java.math`, third-party numerical libraries, Freeplane feature/view packages, Swing, GraphStream, adapters, mutable map models, or text metrics.
- Do not add a geometry utility class, public helper, API overload, compatibility path, fallback to the defective arithmetic, or parallel legacy execution path. Private nested value helpers are permitted only inside the class that owns the operation.
- Preserve all public signatures and all behavior already approved by the original Task 18 run, including canonical polygon order, absolute `1e-9` tie behavior, positive-zero separation, positive-X deterministic ties, defensive `Path2D` copies, fixed 4-world-unit smoothing cap, and exact map/list immutability.
- Treat every finite `LayoutPoint` accepted by the public API as valid numerical input. An intermediate overflow, underflow, `NaN`, cancellation, or lost minor component must not silently turn an invalid polygon valid or change a finite geometric answer when the answer is representable.
- Each task is strict TDD. Add only its named regression first; run exactly that test at the inherited green implementation and prove it fails for the stated mechanism before production edits. If the named test passes before production edits, return `BLOCKED` because the RED gate is not falsifiable.
- After each task, run its named regression and both full geometry test classes with `--rerun-tasks`, inspect the scoped diff, stage only that task's listed files, and create the exact new commit named by the task. Do not amend an earlier commit.
- The final gate covers the whole branch from `04d39279c4eed35254b0f234c8ec0c27c79a04bf` through the recovery HEAD and must reconcile prior-run F-1 through F-8. F-2 and F-4 were already absent; this plan remediates residual F-1, F-3, F-5, F-6, F-7, and F-8.

## Task 1: Make polygon predicates robust for finite coordinates

**Implementer tier:** Capable

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/HullGeometry.java:68-319`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/HullGeometryShould.java:497-640`

**Interfaces:**

- Consumes the existing `HullGeometry.of(List<LayoutPoint>, LayoutPoint)`, `HullGeometry.contains(LayoutPoint)`, canonicalization, collinear cleanup, simplicity validation, and immutable `LayoutPoint` values.
- Produces no new public API. Private predicate helpers must robustly classify each finite determinant as negative (`< -1e-9`), collinear (`[-1e-9, +1e-9]`), or positive (`> +1e-9`) and must be used consistently by cleanup, winding/strict-convexity validation, segment intersection, and containment.
- Resolves carried final-rereview finding F-1. It must preserve the ordinary `rejectsAdjacentCollinearBacktrackingBeforeCanonicalization` behavior and reject the scaled self-intersecting star rather than treating an indeterminate cross product as zero.

- [ ] **Step 1: Add the two focused regressions before production edits**

Add these tests, retaining the literal finite coordinates from the Frontier report:

```java
@Test
public void rejectsAScaledSelfIntersectingStarPolygon() {
    List<LayoutPoint> scaledStar = Arrays.asList(
        LayoutPoint.of(0.0, 1.0e308),
        LayoutPoint.of(5.877852522924732e307, -8.090169943749475e307),
        LayoutPoint.of(-9.510565162951535e307, 3.090169943749474e307),
        LayoutPoint.of(9.510565162951535e307, 3.090169943749474e307),
        LayoutPoint.of(-5.877852522924732e307, -8.090169943749475e307));

    assertThatThrownBy(() -> HullGeometry.of(scaledStar, LayoutPoint.of(0.0, 0.0)))
        .isInstanceOf(IllegalArgumentException.class);
}

@Test
public void containsVerticesOfANearLimitConvexHull() {
    HullGeometry diamond = HullGeometry.of(Arrays.asList(
        LayoutPoint.of(0.0, 8.0e307),
        LayoutPoint.of(8.0e307, 0.0),
        LayoutPoint.of(0.0, -8.0e307),
        LayoutPoint.of(-8.0e307, 0.0)), LayoutPoint.of(0.0, 0.0));

    assertThat(diamond.contains(LayoutPoint.of(0.0, 0.0))).isTrue();
    for (LayoutPoint vertex : diamond.exactPolygon()) {
        assertThat(diamond.contains(vertex)).isTrue();
    }
    assertThat(diamond.contains(LayoutPoint.of(9.0e307, 0.0))).isFalse();
}
```

- [ ] **Step 2: Prove the RED mechanism at inherited HEAD**

```bash
env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-18-hull-geometry \
  :freeplane_plugin_graph:test \
  --tests '*HullGeometryShould.rejectsAScaledSelfIntersectingStarPolygon' \
  --tests '*HullGeometryShould.containsVerticesOfANearLimitConvexHull' \
  -PTestLoggingFull --rerun-tasks
```

Expected: FAIL because `HullGeometry.of` accepts the finite scaled star after raw products become `NaN`; the containment test may also expose raw-subtraction/product failure. A compile failure, fixture failure, or failure unrelated to predicate arithmetic is not valid RED evidence.

- [ ] **Step 3: Replace raw cross-product decisions with one robust sign predicate**

Implement one private orientation-classification operation for three finite `LayoutPoint` values. It must never decide from a non-finite raw determinant. Use Java 8-compatible power-of-two normalization and error-free `double` transforms (`twoSum`/`twoDiff`, split-product or equivalent expansion terms) so overflow is removed before multiplication and cancellation falls through to lower-order terms. Compare the represented determinant robustly against `-EPSILON` and `+EPSILON`; return collinear only when its magnitude is at most the existing absolute `1e-9` threshold. Do not round or quantize published coordinates and do not use `BigDecimal` or `Math.fma`.

Route all orientation decisions through that operation:

- `contains` rejects only the robust negative classification and includes robust collinear;
- collinear cleanup removes a point only on the robust collinear classification and still calls `isBetween` to reject backtracking;
- winding and strict-convexity validation use the robust classification, never a raw `cross > EPSILON` on a possibly non-finite result;
- segment intersection compares the four robust classifications directly rather than multiplying two determinants;
- endpoint-on-segment handling requires the robust collinear classification plus the existing inclusive coordinate bounds.

The inherited absolute `EPSILON` policy is part of the contract. The correction makes its determinant comparison overflow- and cancellation-safe; it must not replace the threshold with exact-zero classification or scale the threshold relative to coordinate magnitude.

- [ ] **Step 4: Run focused and geometry green gates**

```bash
env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-18-hull-geometry \
  :freeplane_plugin_graph:test \
  --tests '*HullGeometryShould.rejectsAScaledSelfIntersectingStarPolygon' \
  --tests '*HullGeometryShould.containsVerticesOfANearLimitConvexHull' \
  --tests '*HullGeometryShould' \
  --tests '*HullIntersectionShould' \
  -PTestLoggingFull --rerun-tasks
```

Expected: both new regressions and both geometry classes pass with zero failures/errors. Confirm the ordinary star, adjacent backtracking, collinear canonicalization, containment, and intersection suites remain green.

- [ ] **Step 5: Inspect and commit Task 1 only**

```bash
cd /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-18-hull-geometry
git diff --check
git diff -- \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/HullGeometry.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/HullGeometryShould.java
git add -- \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/HullGeometry.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/HullGeometryShould.java
git diff --cached --check
git commit -m "2026-08-10-graph-workspace: Make hull predicates robust"
```

## Task 2: Preserve representable minor components in circle attachments

**Implementer tier:** Advanced

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/NodeGeometry.java:61-86`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/HullGeometryShould.java:570-620`

**Interfaces:**

- Consumes `NodeGeometry.boundaryToward(LayoutPoint)` and immutable finite `center`, `toward`, and positive finite `radius` values.
- Produces no new public API. The returned point must be the representable point on the mathematical center-to-target ray at the node radius; equal center/target still returns the positive-X boundary.
- Resolves carried final-rereview finding F-3 while preserving the existing axis-aligned opposite-sign and ordinary diagonal cases.

- [ ] **Step 1: Add the skew near-limit regression before production edits**

```java
@Test
public void preservesRepresentableMinorBoundaryDisplacementOnANearLimitRay() {
    NodeGeometry node = NodeGeometry.of(LayoutPoint.of(-1.0e308, 0.0), 1.0e307);

    LayoutPoint boundary = node.boundaryToward(LayoutPoint.of(1.0e308, 1.0e-100));

    assertThat(boundary.x()).isEqualTo(-9.0e307);
    assertThat(boundary.y()).isCloseTo(5.0e-102, within(1.0e-116));
}
```

- [ ] **Step 2: Prove the RED mechanism at the Task 1 HEAD**

```bash
env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-18-hull-geometry \
  :freeplane_plugin_graph:test \
  --tests '*HullGeometryShould.preservesRepresentableMinorBoundaryDisplacementOnANearLimitRay' \
  -PTestLoggingFull --rerun-tasks
```

Expected: FAIL because the inherited whole-coordinate scaling underflows the Y direction and returns exactly `0.0` for the boundary Y coordinate.

- [ ] **Step 3: Compute each boundary displacement with exponent-aware ratios**

Replace whole-vector division by one world-coordinate scale. Represent each coordinate difference as a normalized finite significand plus a base-two exponent, scaling before subtraction only when direct subtraction would overflow. Determine the dominant exponent, compute the normalized vector length without discarding smaller components, and compute each final displacement as `radius * component / length` by combining exponents with `Math.getExponent` and `Math.scalb` before the final rounding.

The operation must not first materialize a unit component that can underflow even though multiplication by `radius` would make the final displacement representable. Preserve exact positive-X fallback only when both mathematical differences are zero. Do not add imports outside `java.lang`/`java.util` and do not alter `contains` or public bounds behavior.

- [ ] **Step 4: Run focused and geometry green gates**

```bash
env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-18-hull-geometry \
  :freeplane_plugin_graph:test \
  --tests '*HullGeometryShould.preservesRepresentableMinorBoundaryDisplacementOnANearLimitRay' \
  --tests '*HullGeometryShould' \
  --tests '*HullIntersectionShould' \
  -PTestLoggingFull --rerun-tasks
```

Expected: the skew ray, both inherited opposite-sign axis rays, ordinary/huge diagonal rays, and both geometry classes pass.

- [ ] **Step 5: Inspect and commit Task 2 only**

```bash
cd /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-18-hull-geometry
git diff --check
git diff -- \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/NodeGeometry.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/HullGeometryShould.java
git add -- \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/NodeGeometry.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/HullGeometryShould.java
git diff --cached --check
git commit -m "2026-08-10-graph-workspace: Preserve skew node attachments"
```

## Task 3: Enforce the smoothing cap after coordinate rounding

**Implementer tier:** Standard

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/HullGeometry.java:321-365`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/HullGeometryShould.java:595-680`

**Interfaces:**

- Consumes the existing private smooth-path construction and public `HullGeometry.smoothPath()` defensive `Shape` result.
- Produces no new public API. Every emitted incoming and outgoing quadratic tangent endpoint must be no farther than `4.0` Euclidean world units from its corner after `double` coordinate rounding.
- Resolves carried final-rereview finding F-5. If no distinct representable point exists on an edge within four units of a corner, that endpoint is exactly the corner, yielding a zero-length tangent on that side.

- [ ] **Step 1: Add the large-offset ULP regression before production edits**

```java
@Test
public void keepsSmoothTangentsWithinFourWhenOneUlpExceedsFour() {
    double offset = Math.nextUp(Math.scalb(1.0, 55));
    assertThat(Math.ulp(offset)).isEqualTo(8.0);
    HullGeometry rectangle = HullGeometry.of(Arrays.asList(
        LayoutPoint.of(offset, 0.0),
        LayoutPoint.of(offset + 80.0, 0.0),
        LayoutPoint.of(offset + 80.0, 20.0),
        LayoutPoint.of(offset, 20.0)), LayoutPoint.of(offset, 10.0));

    assertTangentsWithinFourWorldUnits(rectangle);
}
```

- [ ] **Step 2: Prove the RED mechanism at the Task 2 HEAD**

```bash
env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-18-hull-geometry \
  :freeplane_plugin_graph:test \
  --tests '*HullGeometryShould.keepsSmoothTangentsWithinFourWhenOneUlpExceedsFour' \
  -PTestLoggingFull --rerun-tasks
```

Expected: FAIL because adding a requested four-unit horizontal cut at this offset rounds to an eight-unit tangent.

- [ ] **Step 3: Validate each representable endpoint against its corner**

Keep the intended cut calculation unchanged. After constructing a candidate endpoint, compute its actual finite Euclidean displacement from the corner with `Math.hypot`. Return the candidate only when that represented displacement is at most `4.0`; otherwise return the corner itself. Apply this check independently to incoming and outgoing endpoints, so a coarse X coordinate can collapse while a representable Y cut remains four units.

Do not use `Math.nextAfter` to step eight units, increase the cap by an ULP tolerance, move the control corner, or alter the exact polygon. The contract is an absolute world-space cap, not an approximate cap.

- [ ] **Step 4: Run focused and geometry green gates**

```bash
env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-18-hull-geometry \
  :freeplane_plugin_graph:test \
  --tests '*HullGeometryShould.keepsSmoothTangentsWithinFourWhenOneUlpExceedsFour' \
  --tests '*HullGeometryShould' \
  --tests '*HullIntersectionShould' \
  -PTestLoggingFull --rerun-tasks
```

Expected: all smooth-path tests and both geometry classes pass; repeated paths remain deterministic and defensive.

- [ ] **Step 5: Inspect and commit Task 3 only**

```bash
cd /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-18-hull-geometry
git diff --check
git diff -- \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/HullGeometry.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/HullGeometryShould.java
git add -- \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/HullGeometry.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/HullGeometryShould.java
git diff --cached --check
git commit -m "2026-08-10-graph-workspace: Bound representable hull smoothing"
```

## Task 4: Make nearest-boundary projection cancellation-safe without java.math

**Implementer tier:** Capable

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/HullGeometry.java:3-175`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/HullGeometryShould.java:630-720`

**Interfaces:**

- Consumes `HullGeometry.nearestBoundaryPoint(LayoutPoint)`, canonical edge order, the absolute `1e-9` squared-distance improvement rule, and finite immutable polygon/target points.
- Produces no new public API. Segment projection must remain Euclidean for finite inputs when the projected point is representable, including cancellation and far-target cases; distance ties retain the earlier canonical edge.
- Resolves carried final-rereview F-6 and dependency F-8 by deleting all `java.math` imports and code. Private nested scaled/expansion values inside `HullGeometry` are allowed; an additional source file is not.

- [ ] **Step 1: Add the cancellation regression before production edits**

```java
@Test
public void findsRepresentableInteriorProjectionAfterLargeProductCancellation() {
    double a = 3.6519210675856295e120;
    double targetY = Math.nextUp(-a);
    HullGeometry triangle = HullGeometry.of(Arrays.asList(
        LayoutPoint.of(0.0, 0.0),
        LayoutPoint.of(a, a),
        LayoutPoint.of(-a, a)), LayoutPoint.of(0.0, 0.0));
    double expectedCoordinate = (a + targetY) / 2.0;

    assertThat(triangle.nearestBoundaryPoint(LayoutPoint.of(a, targetY)))
        .isEqualTo(LayoutPoint.of(expectedCoordinate, expectedCoordinate));
}
```

- [ ] **Step 2: Prove the RED mechanism at the Task 3 HEAD**

```bash
env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-18-hull-geometry \
  :freeplane_plugin_graph:test \
  --tests '*HullGeometryShould.findsRepresentableInteriorProjectionAfterLargeProductCancellation' \
  -PTestLoggingFull --rerun-tasks
```

Expected: FAIL because the inherited fast-path dot products cancel to `0.0`, selecting `(0.0,0.0)` instead of the finite interior projection.

- [ ] **Step 3: Replace both projection arithmetic paths with allowed scaled arithmetic**

Delete `BigDecimal`, `MathContext`, `decimal`, and `decimalSquaredDistance`. Use one private Java 8-compatible segment-projection path:

- represent edge and target-minus-start components with power-of-two exponents so subtraction overflow is avoided without underflowing a component whose result matters;
- normalize products before multiplying and use compensated product/sum terms when opposite-sign terms can cancel;
- compare the projection numerator to zero and edge length squared before division;
- compute an interior `t` from normalized numerator/denominator terms, then construct `start + t * edge` with finite coordinate checks;
- compare candidate distances with a private scaled magnitude representation, so `hypot`/squared-distance overflow cannot make the first edge win incorrectly;
- retain the existing absolute `1e-9` squared-distance improvement rule when both squared distances are in a directly comparable finite scale, and retain canonical edge order otherwise unless the candidate is demonstrably better by more than that threshold.

The inherited `findsNearestBoundaryForFarFiniteTargets` and `resolvesNearestBoundaryNearTiesToTheEarlierCanonicalEdge` tests are mandatory regression guards. No precise-library import, utility class, decimal string conversion, or exception-based fallback is permitted.

- [ ] **Step 4: Run focused, purity, and geometry green gates**

```bash
env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-18-hull-geometry \
  :freeplane_plugin_graph:test \
  --tests '*HullGeometryShould.findsRepresentableInteriorProjectionAfterLargeProductCancellation' \
  --tests '*HullGeometryShould' \
  --tests '*HullIntersectionShould' \
  -PTestLoggingFull --rerun-tasks

! rg -n '^import java\.math\.' \
  /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-18-hull-geometry/freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry
```

Expected: cancellation, far-target, canonical-tie, and both geometry suites pass; the positive whitelist scan prints no `java.math` import.

- [ ] **Step 5: Inspect and commit Task 4 only**

```bash
cd /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-18-hull-geometry
git diff --check
git diff -- \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/HullGeometry.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/HullGeometryShould.java
git add -- \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/HullGeometry.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/HullGeometryShould.java
git diff --cached --check
git commit -m "2026-08-10-graph-workspace: Fix compensated hull projection"
```

## Task 5: Make SAT translation invariant at large world offsets

**Implementer tier:** Advanced

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/HullIntersection.java:13-120`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/HullIntersectionShould.java:50-130`

**Interfaces:**

- Consumes `HullIntersection.minimumSeparatingTranslation(HullGeometry, HullGeometry)`, exact canonical polygons, axes from both hulls, horizontal-first sorted ties, positive canonical direction on equality, and positive-zero for disjoint/touching hulls.
- Produces no new public API. SAT interval projections and minimum-vector ranking must be invariant under a common finite translation within the representability of the supplied vertices and answer.
- Resolves carried final-rereview finding F-7 while preserving the inherited origin-centered `1e160` overlap, axis-from-both-polygons, touching, tie, and reverse-order tests.

- [ ] **Step 1: Add the common-large-offset regression before production edits**

```java
@Test
public void keepsMinimumTranslationInvariantUnderACommonLargeOffset() {
    double offset = 1.3e308;
    double radius = 1.0e300;
    double shift = 5.0e299;
    HullGeometry first = hull(Arrays.asList(
        point(offset - radius, offset), point(offset, offset - radius),
        point(offset + radius, offset), point(offset, offset + radius)));
    HullGeometry second = hull(Arrays.asList(
        point(offset - radius + shift, offset + shift),
        point(offset + shift, offset - radius + shift),
        point(offset + radius + shift, offset + shift),
        point(offset + shift, offset + radius + shift)));
    HullGeometry firstRelative = translate(first, point(-offset, -offset));
    HullGeometry secondRelative = translate(second, point(-offset, -offset));

    LayoutPoint offsetTranslation = HullIntersection.minimumSeparatingTranslation(first, second);
    LayoutPoint relativeTranslation = HullIntersection.minimumSeparatingTranslation(firstRelative, secondRelative);

    assertThat(offsetTranslation.x()).isCloseTo(relativeTranslation.x(), within(1.0e285));
    assertThat(offsetTranslation.y()).isCloseTo(relativeTranslation.y(), within(1.0e285));
    assertThat(offsetTranslation.x()).isCloseTo(4.9999999001856e299, within(1.0e285));
    assertThat(offsetTranslation.y()).isCloseTo(4.9999999001856e299, within(1.0e285));
    assertPositiveZero(HullIntersection.minimumSeparatingTranslation(first,
        translate(second, offsetTranslation)));
}
```

If negating `offset` through `LayoutPoint` makes a fixture coordinate non-finite after addition, build the relative polygons explicitly by subtracting `offset` from each represented source vertex; do not change the expected geometry or tolerance.

- [ ] **Step 2: Prove the RED mechanism at the Task 4 HEAD**

```bash
env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-18-hull-geometry \
  :freeplane_plugin_graph:test \
  --tests '*HullIntersectionShould.keepsMinimumTranslationInvariantUnderACommonLargeOffset' \
  -PTestLoggingFull --rerun-tasks
```

Expected: FAIL because raw absolute-coordinate projections return approximately `(1.0000000014e300,-1.0000000014e300)` instead of the smaller positive diagonal vector.

- [ ] **Step 3: Project every axis relative to one shared safe origin**

For each SAT invocation, choose one deterministic shared origin from the canonical first polygon and project every point from both polygons relative to that same origin. Compute coordinate differences with power-of-two scaling when direct subtraction would overflow, and compute the two-term axis dot product with normalized/compensated `double` arithmetic when terms can overflow or cancel. Store interval values in a common scale for that axis so overlap, positive translation, negative translation, and tie comparisons never mix differently scaled numbers.

Keep axis canonicalization, deduplication, sorting, positive-direction tie selection, and unsquared magnitude ranking unchanged in meaning. Do not project each polygon from a different origin: that destroys interval comparability. Normalize positive zero on the returned zero vector and ensure applying a returned vector leaves no positive-area overlap.

- [ ] **Step 4: Run focused and full recovery verification**

```bash
env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-18-hull-geometry \
  :freeplane_plugin_graph:test :freeplane_plugin_graph:verifyGraphBundle \
  -PTestLoggingFull --rerun-tasks
```

Required evidence:

- The common-offset regression, inherited huge overlap, touching/disjoint, axis-completeness, deterministic tie, application, and reverse-order tests all pass.
- Aggregate every JUnit XML suite under `freeplane_plugin_graph/build/test-results/test`; report suites, tests, skips, failures, and errors, requiring zero failures/errors.
- `verifyGraphBundle` passes; the plugin JAR contains all seven geometry classes and `javap -verbose` reports major version exactly `52` for each.
- `javap -public` shows no new public geometry API and `GraphGeometry` still has no `labels()` method.
- Scan all seven production geometry files against the exact positive import whitelist. Every import is either `java.util.*`, `java.awt.Shape`, `java.awt.geom.Path2D`, or an immutable `org.freeplane.plugin.graph.projection` value; specifically require no `java.math` import.
- `git diff --check` passes and `git status --short` names only `HullIntersection.java` and `HullIntersectionShould.java` for this task.

- [ ] **Step 5: Commit Task 5 and verify the recovery topology**

```bash
cd /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-18-hull-geometry
git add -- \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/HullIntersection.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/HullIntersectionShould.java
git diff --cached --check
git commit -m "2026-08-10-graph-workspace: Make hull separation translation invariant"

git status --porcelain
git diff --check
git log --format='%H %s' --reverse d1f0e46e1f08904702c2df2f6c849965b62c4e31..HEAD
```

Expected: the worktree and index are clean; exactly five new implementation commits follow the committed recovery plan; no inherited commit was amended; and the union of implementation changes is exactly the five-path recovery allowlist.
