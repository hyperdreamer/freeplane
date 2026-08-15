# Correct Hull Centroid Rounding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.

- **Task Identifier:** 2026-08-10-graph-workspace
- **Original backlog task:** Task 18, Compute deterministic hull and attachment geometry
- **Design:** `docs/superpowers/specs/2026-08-15-correct-hull-centroid-rounding-design.md`
- **Terminal predecessor run:** `.superpowers/r6`, revision 17 `TASK_BLOCKED`
- **Starting numerical candidate:** `e5c1fdb35e5ba1d84cbe885b1dba4283fce82610`
- **Design commit:** `c19effd9ac07bd77da01943c45a4fd4e7c7c61f2`
- **Original merge base:** `04d39279c4eed35254b0f234c8ec0c27c79a04bf`

**Goal:** Publish the correctly rounded binary64 public hull area centroid by removing the leading-term tie override and correcting the invalid base-fixture oracle value.

**Architecture:** Keep the existing origin-relative tagged-expansion centroid and full represented-expansion residual comparison. A candidate is retained only when the represented residual is below the half-gap, or exactly equal with an even candidate; every strictly greater residual advances to the adjacent binary64 value.

**Tech Stack:** Java 8 source/bytecode, JUnit 4, AssertJ, Java2D geometry, Gradle, external `/tmp` exact-binary oracles.

## Global Constraints

- Follow `AGENTS.md`: Java source and target compatibility are 8, UTF-8 encoding, four-space indentation, and tests use JUnit 4/AssertJ/Mockito.
- Use Java at `/home/henry/.sdkman/candidates/java/21.0.8-zulu`; every Gradle and JDK command sets this exact `JAVA_HOME` and prepends its `bin` directory to `PATH`. Use `gradle`, never Maven or the wrapper.
- Work only in `/data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-18-hull-geometry` on branch `2026-08-10-graph-workspace-task-18-hull-geometry`. Do not merge, rebase, amend, reset, or rewrite inherited commits.
- Terminal predecessor `.superpowers/r6` is immutable at revision 17 `TASK_BLOCKED`. Leave `.superpowers/r4`, `.superpowers/r5`, every `.superpowers/sdd/*` predecessor/recovery ledger, and all prior reports untouched.
- The implementation allowlist is exactly two existing paths: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/GraphGeometryEngine.java` and `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/HullGeometryShould.java`. Do not modify any third implementation or test path.
- Production code may use only Java 8 primitives, `java.lang`, existing `java.util`, `java.awt.Shape`, `java.awt.geom.Path2D`, and immutable `org.freeplane.plugin.graph.projection` values already used by `GraphGeometryEngine`. Do not import `java.math`, third-party numerical libraries, Freeplane feature/view packages, Swing, GraphStream, adapters, mutable map models, or text metrics.
- Do not use `BigDecimal`, `BigInteger`, `Math.fma`, decimal strings, a new utility class, public helper, overload, fixture-specific branch, exception fallback, clamp, alternate centroid, old absolute-coordinate shoelace path, fixed quotient-iteration cap, or parallel legacy execution path in checked-in production code. Arbitrary-precision oracles remain external under `/tmp`.
- Preserve all public signatures and approved behavior: canonical polygon order, absolute `1e-9` orientation/SAT policy, positive-zero separation, positive-X deterministic ties, exact immutable collections, defensive `Path2D` copies, fixed four-world-unit smoothing cap, and Java 8 bytecode.
- The corrected public base-fixture y centroid is raw bits `0x40325e018e582689`. Independent 1000/3000-digit decimal and exact integer-rational public-polygon oracles agree. The former `0x40325e018e582688` value is invalid and must not be preserved as a tie or compatibility result.
- Retain the nearby public ULP regression at raw bits `0x40325e018e58267d`, the large-offset representable centroid, and all inherited Task 18 tests.
- This task is strict TDD. Change only the base public regression name/expected bits before production edits, run that exact regression against inherited production `e5c1fdb3`, and prove the expected one-ULP RED: actual `0x40325e018e582688`, expected `0x40325e018e582689`. A compile error, fixture error, or different failure is invalid RED evidence.
- Create exactly one new non-amended implementation commit after the committed design/plan documents, with the specified subject. Inspect `git diff --check`, the staged diff, commit parent, and worktree status before reporting.
- Run this exact fresh focused gate after the correction:

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
  :freeplane_plugin_graph:check -PTestLoggingFull --rerun-tasks

env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-18-hull-geometry \
  :freeplane_plugin_graph:verifyGraphBundle --rerun-tasks
```

- Aggregate every XML suite under `freeplane_plugin_graph/build/test-results/test`; require zero failures and zero errors and record suites, tests, skips, failures, and errors.
- Inspect the built plugin JAR: require `GraphGeometry`, `GraphGeometryEngine`, `HullGeometry`, `HullIntersection`, `LayoutPoint`, `LayoutPositions`, and `NodeGeometry` plus private inners; `javap -verbose` must report major version 52 for every public geometry class.
- Run `javap -public` for all seven classes; require no new public geometry API and no `GraphGeometry.labels()` method. Scan all seven production geometry files against the existing positive import whitelist and specifically require no `java.math` import.
- The final Frontier gate reviews the whole Task 18 branch from `04d39279c4eed35254b0f234c8ec0c27c79a04bf` through the new HEAD. It must reconcile parked F-1, fixed F-12, fixed canonical F-13, and corrected F-14. Completion requires `SPEC: PASS`, `QUALITY: APPROVED`, no open load-bearing finding, the full gate green, and a clean worktree.

## Task 1: Correct the public centroid midpoint decision

**Implementer tier:** Capable

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/GraphGeometryEngine.java:327-355`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/HullGeometryShould.java:237-293`

**Interfaces:**

- Consumes `GraphGeometryEngine.computeHulls(GraphProjection, LayoutPositions)` and private `TaggedSum.compareMagnitude(TaggedSum)` from the inherited numerical candidate.
- Produces no new public API. For every finite public hull polygon, `HullGeometry.labelAnchor()` is the correctly rounded binary64 area centroid; exact half-gap equality uses ties-to-even.
- Preserves the nearby public ULP fixture at `0x40325e018e58267d`, large-offset centroid behavior, enclosure order/recursion, empty anchors, prominence radii, polygon canonicalization, and exact collection immutability.

- [ ] **Step 1: Correct the public base-fixture oracle before production edits**

Rename only the existing test method and change only its expected raw bits. Keep its public `computeHulls` fixture unchanged:

```java
@Test
public void roundsPublicExpandedHullCentroidToCorrectAdjacentValue() {
    List<ProjectedNode> nodes = Arrays.asList(node("n1"), node("n2"), node("n3"), node("n4"));
    EnclosureHullKey hullKey = hullKey("public-expanded-quotient-hull");
    List<ProjectedNodeKey> directNodes = Arrays.asList(
        nodes.get(0).key(), nodes.get(1).key(), nodes.get(2).key(), nodes.get(3).key());
    ProjectedEnclosure enclosure = enclosure(hullKey, Optional.<EnclosureHullKey>empty(),
        directNodes, Collections.<EnclosureHullKey>emptyList());
    Map<ProjectedNodeKey, LayoutPoint> nodePositions = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
    nodePositions.put(nodes.get(0).key(), LayoutPoint.of(-0x1.57c5db1deabccp999, 0x1.81dcbefed8782p3));
    nodePositions.put(nodes.get(1).key(), LayoutPoint.of(0x1.307029465492p3, 0x1.81dcbefed8782p3));
    nodePositions.put(nodes.get(2).key(), LayoutPoint.of(0x1.307029465492p3, 0x1.8ad1d24b9894fp4));
    nodePositions.put(nodes.get(3).key(), LayoutPoint.of(-0x1.57c5db1deabccp999, 0x1.8ad1d24b9894fp4));

    GraphGeometry geometry = compute(
        projection(nodes, Collections.singletonList(enclosure), Collections.<ProjectedEdge>emptyList()),
        LayoutPositions.of(nodePositions, Collections.singletonMap(hullKey, LayoutPoint.of(0.0, 0.0))));

    assertThat(Double.doubleToRawLongBits(geometry.hulls().get(hullKey).labelAnchor().y()))
        .isEqualTo(0x40325e018e582689L);
}
```

Do not change `roundsNearbyPublicExpandedHullCentroidWithFullRemainder` or production code in this step.

- [ ] **Step 2: Prove the corrected RED at inherited production**

```bash
env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-18-hull-geometry \
  :freeplane_plugin_graph:test \
  --tests '*HullGeometryShould.roundsPublicExpandedHullCentroidToCorrectAdjacentValue' \
  -PTestLoggingFull --rerun-tasks
```

Required behavioral RED: expected decimal long `4625863128039040649` (`0x40325e018e582689`) but inherited production publishes `4625863128039040648` (`0x40325e018e582688`). Stop as `BLOCKED` if this exact mechanism is not observed.

- [ ] **Step 3: Remove the leading-term override and retain exact ties-to-even**

In private `TaggedSum.roundingDirection`, delete the `sameLeadingMagnitude` comment, index locals, comparison, and override branch. Keep the full represented-expansion comparison and use exactly this decision:

```java
final boolean evenCandidate = (Double.doubleToRawLongBits(candidate) & 1L) == 0L;
if (comparison < 0 || (comparison == 0 && evenCandidate)) {
    return 0;
}
return direction;
```

Do not change `TaggedSum.compareMagnitude`, quotient refinement, origin-relative area/moment construction, or any other production helper. A strictly positive comparison must advance even when the candidate is even; exact equality alone invokes ties-to-even.

- [ ] **Step 4: Run exact-oracle and repository green gates**

Run the corrected named base test, `roundsNearbyPublicExpandedHullCentroidWithFullRemainder`, and `computesRepresentableCentroidForLargeOffsetHull` separately. Then run the exact focused/full/bundle gates in Global Constraints.

Compile external `/tmp` oracles against freshly built production classes. Require both 1000- and 3000-digit decimal arithmetic and an independent exact integer-rational midpoint comparison to agree on:

- base public fixture: `0x40325e018e582689`;
- disclosed `-4/-4/-16/-16` ULP fixture: `0x40325e018e58267d`;
- adjacent `-8/-8/0/0` ULP fixture: `0x40325e018e582689`;
- reversed orientation, translated fixtures, and large/extreme spans: exact match with published raw bits.

Complete XML aggregation, JAR inventory, Java 8 bytecode, public API/import scans, `git diff --check`, and committed-range path inspection.

- [ ] **Step 5: Commit Task 1**

```bash
cd /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-18-hull-geometry
git add -- \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/GraphGeometryEngine.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/HullGeometryShould.java
git diff --cached --check
git commit -m "2026-08-10-graph-workspace: Correct public centroid rounding"
git status --porcelain
git diff --check
git log --format='%H %P %s' --reverse HEAD~2..HEAD
```

Expected: one new non-amended direct child of the committed plan HEAD, exactly the two allowlisted implementation paths changed, inherited commits preserved, and a clean worktree.
