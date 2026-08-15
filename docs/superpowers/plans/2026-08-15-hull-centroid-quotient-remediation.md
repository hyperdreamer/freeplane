# Hull Centroid Quotient Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for readability; controller state is canonical.

- **Task Identifier:** 2026-08-10-graph-workspace
- **Original backlog task:** Task 18, Compute deterministic hull and attachment geometry
- **Design:** `docs/superpowers/specs/2026-08-14-hull-geometry-final-remediation-design.md`
- **Terminal predecessor run:** `.superpowers/r5`, revision 39 `TASK_BLOCKED`
- **Starting production implementation:** `1bac8e23a517f7c2301e60a33425a91300b869a8`
- **Original merge base:** `04d39279c4eed35254b0f234c8ec0c27c79a04bf`

**Goal:** Resolve open load-bearing F-14, the public `computeHulls` one-ULP centroid residual, while preserving approved Tasks 1-3 geometry and already-reconciled F-1, F-12, and parked F-1.

**Architecture:** Keep the correction private to `GraphGeometryEngine`. Replace the four-pass quotient cap with an exponent-aware remainder/rounding completion that distinguishes adjacent binary64 centroids, including ties. Do not reopen Tasks 1 or 2, do not change `NodeGeometry` or `HullGeometry`, and do not introduce a shared numerical utility.

**Tech Stack:** Java 8 source and bytecode, JUnit 4, AssertJ, Java2D `Shape`/`Path2D`, Gradle, immutable projection values.

## Global Constraints

- Follow `AGENTS.md`: Java source and target compatibility are 8, UTF-8 encoding, four-space indentation, and tests use JUnit 4/AssertJ/Mockito.
- Use Java at `/home/henry/.sdkman/candidates/java/21.0.8-zulu`; every Gradle and JDK command sets this exact `JAVA_HOME` and prepends its `bin` directory to `PATH`. Use `gradle`, never Maven or the wrapper.
- Work only in `/data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-18-hull-geometry` on branch `2026-08-10-graph-workspace-task-18-hull-geometry`. Do not merge, rebase, amend, reset, or rewrite inherited commits.
- Terminal predecessor run `.superpowers/r5` is immutable at revision 39 `TASK_BLOCKED`. Never reopen or modify its canonical state or audit ledger. Also leave `.superpowers/r4` and every `.superpowers/sdd/*` predecessor/recovery ledger untouched.
- The implementation allowlist is exactly two existing paths: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/GraphGeometryEngine.java` and `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/HullGeometryShould.java`. Do not modify any third implementation or test path.
- Production geometry code may use only Java 8 primitives, `java.lang`, existing `java.util`, `java.awt.Shape`, `java.awt.geom.Path2D`, and immutable `org.freeplane.plugin.graph.projection` values already used by `GraphGeometryEngine`. Do not import `java.math`, third-party numerical libraries, Freeplane feature/view packages, Swing, GraphStream, adapters, mutable map models, or text metrics.
- Do not use `BigDecimal`, `Math.fma`, decimal strings, a new utility class, public helper, overload, fixture-specific branch, exception fallback, clamp, alternate centroid, old whole-coordinate path, or parallel legacy execution path. High-precision oracles remain external under `/tmp` and are never checked in.
- Preserve all public signatures and approved behavior: canonical polygon order, the absolute `1e-9` orientation/SAT policy, positive-zero separation, positive-X deterministic ties, exact immutable collections, defensive `Path2D` copies, fixed four-world-unit smoothing cap, and Java 8 bytecode.
- Treat every finite `LayoutPoint` accepted by a public geometry API as valid numerical input. Intermediate overflow, underflow, NaN, cancellation, or lost minor terms must not change a representable finite answer.
- Carry parked F-1 and fixed F-12 unchanged into the final ledger. Canonical F-13 remains the already-fixed Task 1 ray finding. This plan's only open load-bearing finding is F-14.
- This task is strict TDD. Add only its named public `computeHulls` regression before production edits, run exactly that regression at inherited HEAD `1bac8e23a517f7c2301e60a33425a91300b869a8`, and prove the specified public one-ULP RED. If the named test passes, fails to compile, or fails for a different mechanism, return `BLOCKED` before editing production code.
- Create exactly one new, non-amended commit with the specified subject. Inspect `git diff --check`, the staged diff, commit parent, and worktree status before reporting.
- After the correction, run this exact fresh gate as two invocations because Gradle's `--tests` option belongs only to the `Test` task and is rejected by `verifyGraphBundle`:

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
- The final Frontier gate reviews the whole branch from `04d39279c4eed35254b0f234c8ec0c27c79a04bf` through the new HEAD and reconciles parked F-1, fixed F-12, already-fixed canonical F-13, and F-14. Completion requires `SPEC: PASS`, `QUALITY: APPROVED`, those findings absent or already parked/fixed, the full gate green, and a clean worktree.

## Task 1: Refine the public hull-centroid quotient

**Implementer tier:** Capable

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/GraphGeometryEngine.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/HullGeometryShould.java`

**Interfaces:**

- Consumes `GraphGeometryEngine.computeHulls(GraphProjection, LayoutPositions)` and the already-committed local-origin tagged centroid at `1bac8e23a517f7c2301e60a33425a91300b869a8`.
- Produces no new public API. The published nonempty-enclosure `labelAnchor()` must be the correctly rounded binary64 area centroid of the public `computeHulls` polygon, including after radii and the 16-world-unit clearance expand the node rectangle.
- Resolves open F-14. Preserve `computesRepresentableCentroidForLargeOffsetHull`, enclosure recursion/order, empty-enclosure anchors, prominence radii, polygon canonicalization, and exact map/list immutability.

- [ ] **Step 1: Add the public expanded-hull quotient regression before production edits**

Add this test next to `computesRepresentableCentroidForLargeOffsetHull`. Do not use reflection, private-method invocation, or the raw `clipHalfPlanes` rectangle from r5. The public surface applies node radii and 16-world-unit clearance, so the fixture must exercise `computeHulls`.

```java
@Test
public void roundsPublicExpandedHullCentroidQuotientToNearestEven() {
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
        .isEqualTo(0x40325e018e582688L);
}
```

The expected raw bits are the exact-binary64 rounding of the public expanded-hull y centroid. Inherited `1bac8e23a517f7c2301e60a33425a91300b869a8` publishes `0x40325e018e582689`.

- [ ] **Step 2: Prove the RED mechanism at the inherited HEAD**

```bash
env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-18-hull-geometry \
  :freeplane_plugin_graph:test \
  --tests '*HullGeometryShould.roundsPublicExpandedHullCentroidQuotientToNearestEven' \
  -PTestLoggingFull --rerun-tasks
```

Expected behavioral RED: the public `labelAnchor().y()` raw bits are `0x40325e018e582689`, one ULP above expected `0x40325e018e582688`. A compile error, fixture-validation failure, overflow exception, or a different assertion failure is invalid RED evidence.

- [ ] **Step 3: Complete the represented quotient without a fixed iteration cap**

Correct only private `GraphGeometryEngine` centroid/tagged-arithmetic helpers:

- Keep `polygon.get(0)` as the deterministic local origin and retain the already-committed origin-relative tagged area and first-moment construction.
- Replace the arbitrary four-pass `TaggedSum.divide` cutoff with a bounded exponent-aware remainder/rounding completion. The completion must retain enough represented remainder to distinguish adjacent binary64 results and exact ties.
- A correct approach may certify the final origin-plus-local quotient by comparing represented numerator/denominator residuals against the appropriate binary64 half-gap. It must not rely on a new fixed iteration cap, reconstruct the semantic centroid from rounded endpoint coordinates, or use `Math.hypot`.
- Preserve the actual polygon area centroid. Do not replace it with a vertex average, bounding-box midpoint, input anchor, clamp, exception fallback, or legacy absolute-shoelace path.
- Keep helpers private to `GraphGeometryEngine`; add no public API or utility class and no new imports outside the positive whitelist.

Failed prior attempt that must not be repeated: r5 Task 3 fixer `01a0032c` left an uncommitted rewrite that repaired the private raw rectangle but still published public y bits `0x40325e018e582689`. Do not treat the raw `clipHalfPlanes` rectangle as the public RED fixture.

An external `/tmp` oracle may compare origin-relative exact binary shoelace results for this public expanded hull, both orientations, translations, and extreme spans. Checked-in code must use only the literal public regression above plus already-committed tests.

- [ ] **Step 4: Run focused and full green gates**

Run the named regression alone, then the exact fresh gate in Global Constraints. Require `computesRepresentableCentroidForLargeOffsetHull`, inherited parent/child ordering, containment, empty enclosure, prominence, immutability, and duplicate-key tests; both geometry classes; bundle verification; XML aggregation; bytecode/API/import checks; and `git diff --check` to pass.

- [ ] **Step 5: Commit Task 1**

```bash
cd /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-18-hull-geometry
git add -- \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/GraphGeometryEngine.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/HullGeometryShould.java
git diff --cached --check
git commit -m "2026-08-10-graph-workspace: Refine hull centroid quotient"
git status --porcelain
git diff --check
git log --format='%H %P %s' --reverse HEAD~2..HEAD
```

Expected: one new direct child of `1bac8e23a517f7c2301e60a33425a91300b869a8`, no inherited commit amended, and a clean worktree. The committed tree changes exactly the two allowlisted paths.
