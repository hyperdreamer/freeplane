# Edge-node Crossing Residual Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Repair the approved residual cases by appending off-midpoint routing candidates and expanding relocation only after an unsuccessful ring-0 round.

**Architecture:** Keep the existing midpoint bow and dogleg implementations, then append a separate control-position bow group. Retain relocation's attempt loop, publication rules and counted checks, recording selected conflict families so an unsuccessful round can expand their C2 targets through four additional rings. Routing must pass its preservation gate before relocation work starts; frozen-fixture and reported-workspace acceptance finish the sequence.

**Tech Stack:** Java 8 source/target, Gradle running on Zulu Java 21.0.8, JUnit 4, AssertJ, existing Freeplane graph projection and geometry helpers; no new dependencies.

**Task identifier:** `2026-10-26-edge-node-crossing-residual`.

**Binding inputs:** `docs/superpowers/specs/2026-10-26-edge-node-crossing-residual-spec.md` at `f823834243`, and `docs/superpowers/specs/2026-10-26-edge-node-crossing-residual-design.md` at `c6bfd3a0c6`. The delivery base is `bfaed5611d170b5b0b3dc93919685bd6c54035c5`. Read the specified clauses before each task. Line ranges below locate the pre-implementation source; use the named symbols after earlier tasks shift lines.

**Controller compatibility:** Lowercase tier values below are intentional, as requested. The installed controller's `TIER_FIELD` accepts only TitleCase, so its unmodified `validate-plan` rejects this document. Do not silently change the tiers, patch the installed controller, initialize execution state, or claim that dispatch validation passed. A separate controller-contract decision is required before deterministic execution. Heading, task-body and interface checks can still be performed by normalizing tiers in memory for inspection; that does not make the saved document dispatchable.

### File responsibilities and execution order

| Tasks | Lane | Category | Owned implementation and evidence |
|---|---|---|---|
| 1–3 | routing | geometry / fixtures / preservation | `EdgeRouter.java`, `EdgeRouterShould.java`, `EdgeRouterFixture.java`, new expected-path resource |
| 4–6 | relocation | round-loop / fixtures / stop boundaries | `EdgeConflictCorrection.java`, `EdgeConflictCorrectionShould.java`, new search evidence resource |
| 7 | acceptance | frozen evidence / performance | `PerformanceTripwiresShould.java`, new frozen acceptance record |
| 8 | acceptance | reported workspace | new module-local probe copy and acceptance test, new workspace acceptance record |

Dependency order is exactly `1 -> 2 -> 3 -> 4 -> 5 -> 6 -> 7 -> 8`. In particular, all routing fixtures must be green at Task 3 before Task 4 starts. There is no parallel execution on these overlapping files. Tasks 7 and 8 are the final tasks. The table is planning information; do not derive a manifest or DOT graph while authoring this plan.

## Global Constraints

### Verbatim normative constraints from the binding specification

This document specifies the only implementation permitted for the residual edge-node crossing defect. It changes the internal relocation search in `EdgeConflictCorrection` and the internal routing ladder in `EdgeRouter`. It does not change the public API, layout-force policy, pins, enclosure membership, prominence, label placement, settlement policy, strict clearance predicate, or the existing counted-budget framework. [Design §§5, 6.1-6.3, 8]

All distances below are world units. All keys are the canonical strings produced by the existing `CanonicalKeys` helpers. Every fixture uses the existing production projection, geometry, positions, pin, enclosure, and edge-construction helpers; no parallel fixture format is permitted. [Design §9; 2026-10-16 spec §§2-3]

The approved design is binding. A clause in this document is normative only when it is marked by a design citation or explicitly identified as retained text from one of the two amended specifications. The cited design section controls if this document and an earlier specification differ. [Design §§1, 11]

The implementation must remain Java 8 source compatible: no Java 9 APIs, private interface methods, local-variable type inference, records, lambdas where the surrounding source does not use them, or newer language constructs may be introduced. [Design §5; repository guidelines]

The implementation must not add a legality pre-screen, reserve candidate slots, change the strict clearance margin, change the force relaxation, generalize the midpoint bow implementation, add an asymmetric dogleg, or promise completeness. [Design §5]

> **Relocation preserves the legacy accepted sequence.** For an input whose legacy search finishes without ever reaching a *failed* round — that is, without a round in which no candidate is accepted — the new search produces the identical accepted repairs, the identical final positions, and the identical `rounds`, `verifications`, `verificationWork` and residual. For an input that does reach a failed round, the accepted prefix is identical and everything after it may differ; such an input is allowed to gain a repair, and its counters and final positions may then change. [Design §4, Goal 1]

> **Routing preserves every per-edge decision under identical inputs.** For a given edge, given the same obstacle grid published by earlier edges and the same remaining routing allowance, the new ladder selects the identical path, classification and sample list when the current ladder accepts. Named fixtures must additionally be preserved exactly, end to end, including every later edge. Frame-wide preservation is **not** claimed: extending any edge's rung set can change the obstacle grid seen by later edges, and review round 4 produced a concrete two-edge counterexample that does exactly that. [Design §4, Goal 2]

A rejected relocation attempt changes no position, geometry, conflict set, or repair record. An accepted attempt installs its verified positions, geometry, and complete detection together and records one `Repair` per candidate with the exact repair kind and moved-node map. All candidates in an accepted attempt share its zero-based `batchIndex`. C2 candidates use `RepairKind.TRANSLATION`. [2026-10-16 spec §2; Design §§6.2, 8]

The C2 family retains the 2026-10-16 basis and eligibility rules. For chord endpoints `a` and `b`, `d = b - a`, `L = hypot(d.x, d.y)`, `rawNormal = (-d.y / L, d.x / L)`, and `n` is the lexicographically smaller of `rawNormal` and `-rawNormal`, using `Double.compare` after signed-zero normalization. `u = (-n.y, n.x)`. A horizontal chord always has `n = (0,-1)` and `u = (1,0)`, independent of traversal direction. [2026-10-16 spec §3.1; Design §6.2]

For a disc centre `p`, radius `r`, signed projection `s = (p-a) dot n`, and ring radius `rho`, a target is:

```text
p + (sigma * rho - s) * n + q * u
```

where `sigma` is `+1` or `-1` and `q` is `0`, `-h`, or `+h`. Ring 0 uses `rho = r + EDGE_NODE_CLEARANCE`; `h = 2*r + MIN_GAP + EDGE_NODE_CLEARANCE`. No candidate is generated for a pinned disc, a non-finite input, a non-positive radius, a zero-length chord, a non-finite intermediate, zero movement, or movement greater than `MAX_EDGE_NODE_EXCURSION`. No value is clamped and no direction or radius is invented. [2026-10-16 spec §§3.1-3.2; Design §§3, 6.2, 8]

A candidate that would move an enclosure endpoint or swap nodes with different enclosure membership remains ineligible. Such a discarded candidate is not a verification. [Design §5.2; 2026-10-16 spec §3.2]

A1-A4 remain the complete verification contract. A1 requires the output edge-conflict key set to be a strict subset of the current set. A2 requires `gapPairs_out` to be a subset of `gapPairs_in`, using counted checks for both current and candidate positions. A3 requires `hullPairs_out` to be a subset of `hullPairs_in`. A4 retains the existing enclosure and membership invariants. [Design §§3, 6.2, 11; 2026-10-16 §§3.2, 5.1]

The retained constants are `MAX_BEND = 96.0`, `ROUTE_LENGTH_CAP = 1.25`, `EDGE_NODE_CLEARANCE = 3.0`, `ROUTE_EPSILON = 1e-9`, and `EdgePath.BOW_SAMPLE_TOLERANCE = 0.75`. A bow has three defining points and a dogleg has four. Samples remain immutable and contain at least two points. [Design §§2, 6.1, 8; 2026-10-12 spec §§1.4-1.5; `EdgePath` contract]

The final gate must be `clears(...)`, not `clearsDiscs(...)`: it must check both other node discs and other paths. A route budget stop returns no candidate and is reconciled through the existing stop flags. [Design §6.1; `EdgeRouter.bestRung` contract]

For all routing candidates, the clearance assertion is surface clearance `centreDistance - radius >= EDGE_NODE_CLEARANCE - ROUTE_EPSILON`. The expression `radius + clearance` is not a surface-clearance assertion and must not be used. [Design §9 test 1; Design §2 Fact 2]

Within each ring, deduplicate candidates by the existing `CandidateIdentity` only. Do not deduplicate candidates across different rings. A ring rejected by `verify` does not prevent later rings from being tried. [Design §6.2; 2026-10-16 spec §3.3]

Discard non-finite, zero-movement, and over-excursion proposals before verification. `MAX_EDGE_NODE_EXCURSION = 64.0` is inclusive: movement exactly `64.0` is eligible; movement greater than `64.0` is rejected without clamping. [Design §§6.2, 8; 2026-10-16 spec §3.2]

The stage-1 proposal ceiling is `4 rings * 6 slots * selected families`. With at most eight selected families this is `4 * 6 * 8 = 192` proposals, not `24`; `24` is the per-family bound. The unchanged `MAX_VERIFICATIONS_PER_FRAME = 64`, `EDGE_CONFLICT_MAX_VERIFICATION_WORK_PER_FRAME = 65_536`, `MAX_RELOCATION_ROUNDS = 4`, and `MAX_CANDIDATES_PER_BATCH = 8` still bound actual attempts. [Design §6.2]

A verification is counted only after a candidate or candidate batch survives generation and is actually submitted to `verify`. Candidate generation, deduplication, pinned-node filtering, zero-movement filtering, non-finite filtering, and max-excursion filtering do not increment `verifications`. [Design §6.2; 2026-10-16 spec §§3.2, 4.1]

If any allowance is exhausted, use the existing `verificationBudgetExhausted` contract and publish the complete stage-0 residual. Do not throw. Do not retry a singleton after a work-stop rejected its batch. [Design §8; 2026-10-16 spec §5]

A partial scan never supplies a conflict set to candidate selection, never determines the canonical order of a later attempt, never installs positions or geometry, and never replaces a complete boundary or node-residual record. A partial detection may appear only through the existing explicitly named residual lower-bound diagnostic path; that diagnostic publication is not a partial accepted state. This rule applies equally to stage 0 and stage 1. [Design §§6.2, 8; 2026-10-16 spec §§4.1-5.3]

Acceptance is atomic: positions, geometry, complete conflict detection, repair record, and counters for the accepted attempt are installed together. A rejected attempt changes none of them. [Design §§6.2, 8; 2026-10-16 spec §2]

For identical `(projection, positions, geometry, visibleEndpoints)`, and for projection-list permutations that preserve the same logical items, correction and routing must produce identical canonical paths and diagnostics. Canonical edge order, canonical conflict-key order, fixed candidate order, fixed ladder order, and the existing sampling formula are all part of the deterministic contract. [2026-10-12 spec §6.10; Design §§4, 6.1-6.2]

### Repository and execution constraints

- Keep Java 8 source and bytecode compatibility, UTF-8 Java files and 4-space indentation. Tests use JUnit 4 and AssertJ. No new runtime dependencies; use the existing geometry/projection fixture helpers.
- Run commands from the isolated implementation worktree root, with `export JAVA_HOME=~/.sdkman/candidates/java/21.0.8-zulu` and `export PATH="$JAVA_HOME/bin:$PATH"`. Use `gradle`, never `gradlew` or Maven.
- Modify only the exact files listed in the current task. No changes to `freeplane_api`, core modules, the approved specification/design, force calibration, existing fixture CSVs or performance thresholds. New test output pins are evidence, not a parallel input-fixture format.
- Preserve one active runtime path. No compatibility flags, alternate legacy implementation, pre-screen, candidate reservation or ring-reservation rule. Do not change `Candidate`, `CandidateIdentity`, the C1 geometry, `verify`, the greedy batching rule, or terminal publication to accommodate a test.
- Do not set `GRAPH_REGENERATE_SETTLED_FIXTURE=1`. Preserve `settled-reference.csv` SHA-256 `e9e988b7967ea6bb4f503dfb4c5af7d3e74a3328e28e0987e71ff0f0b8ac0578`, `TWO_MAP_FIRST_IDLE_STEP = 3103`, and `INITIAL_SPACING_FIRST_IDLE_LIMIT = 6000`.
- Preserve strict settled force `50_000_000L`, full worker `100_000_000L`, first-frame p95 `150_000_000L`, edge-conflict p95 `25_000_000L`, edge-routing p95 `10_000_000L`; raw complete-frame limit is `5_000_000_000L`. Keep the 300 measured settled samples and zero discards. Raw exhausted samples cannot replace settled samples.
- Every task includes a red/implementation/green/commit cycle. For a preservation assertion already green on its parent, obtain red evidence in a disposable copy by breaking only the asserted mechanism; report that distinction. Do not insert a knowingly false assertion or leave a failing test in a commit. Delete disposable probes; retain their commands, killing test and failure excerpt in the task's controller evidence.
- No unverified fixture claims. Measure selected candidates, path points, samples, rejection identities and counters before recording them. A2/A3 uses the exact 59-value construct-or-declare search in Task 5. No edits to the binding spec are authorized by its instruction to pin a discovered witness: place discovered data in the test and evidence resource.
- Commit subjects include `2026-10-26-edge-node-crossing-residual`; inspect staged paths and whitespace before each commit. Leave plan checkboxes unticked during controller execution because the plan digest is pinned.
- Never execute `reboot`, `shutdown`, `poweroff`, `halt`, `kexec`, any `systemctl` power or sleep verb, `init 0|6`, or `telinit 0|6`; never restart or stop the display manager or `graphical.target`; never terminate your session or signal the process tree that hosts the PM. If you conclude a host transition is required, write `reboot-required.json` with `{schema: 1, trigger, evidence: [{path, sha256}], provenance: {run_id, lane, attempt, dispatch_key, observed_boot_id}}`, then report it and stop.
- The preceding host-transition clause is mandatory in each generated task brief. Write the fallback at the worktree root; do not execute its suggested transition. This exception reports a blocked environment and does not authorize unrelated file changes.

## Task 1: Pin the control-position ratios and capture pre-extension paths

**Implementer tier:** Standard

**Lane:** routing. **Category:** numerical helper and mechanical evidence capture. **Depends on:** none.

**Goal:** Add the separate ratio helper without changing route selection, and capture the exact existing paths before Task 2 changes routing.

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/EdgeRouter.java:273-317` (add the new helper after `analyticRatio`; leave that existing method unchanged).
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/EdgeRouterShould.java:1-429`.
- Create: `freeplane_plugin_graph/src/test/resources/geometry/edge-node-crossing-residual-legacy-paths.txt` (expected output, generated before ladder extension).
- Read only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/EdgeRouterFixture.java:77-305` and `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/SettledReferenceFixture.java:99-127`.

**Interfaces:**

- Consumes: `public EdgePaths EdgeRouter.route(GraphProjection projection, LayoutPositions positions, GraphGeometry geometry, Set<ProjectedEndpointKey> visibleEndpoints)`; `public static SettledReferenceFixture SettledReferenceFixture.load()`; its `public GraphProjection projection()` and `public LayoutPositions positions()`.
- Consumes: package-local `static EdgeRouterFixture laterChordBow()`, `doglegAccepting()`, `tightFan()`, `reportedConvexQuad()`, `cellCornerFan()`, `closeParallelPaths()`; fields `GraphProjection projection`, `LayoutPositions positions`, `GraphGeometry geometry`, `Set<ProjectedEndpointKey> visibleEndpoints`.
- Produces: `private static double EdgeRouter.controlPositionRatio(double controlFraction, double offsetFraction)` and `private static double EdgeRouter.controlRatioPrimitive(double t, double a, double b, double c)`; these are internal helpers, not public API.
- Produces: test-local `private static String residualRoutePins()` and `private static void appendPathPins(StringBuilder output, String name, EdgePaths paths)`. Resource bytes are UTF-8, LF, canonical fixture/edge order, shape, hexadecimal defining/sample coordinates and hexadecimal length. Elapsed timings are excluded.

- [ ] **Step 1: Write the failing ratio test and the baseline serializer.**

Add exactly one JUnit method named `computesTheControlPositionRatioTable`. Test all four controls and six signed offsets, tolerance `1e-9`, against the spec §2.3 table. Use reflection so the absent helper produces a meaningful `NoSuchMethodException` before implementation, rather than adding a stub.

```java
@Test
public void computesTheControlPositionRatioTable() throws Exception {
    final java.lang.reflect.Method method = EdgeRouter.class.getDeclaredMethod(
        "controlPositionRatio", double.class, double.class);
    method.setAccessible(true);
    final double[] controls = {0.25, 0.75, 1.0 / 3.0, 2.0 / 3.0};
    final double[] offsets = {0.50, -0.50, 0.30, -0.30, 0.15, -0.15};
    final double[][] expected = {
        {1.159854311690731, 1.064241004799091, 1.017221360974041},
        {1.159854311690841, 1.064241004799023, 1.017221360974069},
        {1.153020461743610, 1.060106539650193, 1.015781166936747},
        {1.153020461743631, 1.060106539650204, 1.015781166936771}
    };
    for (int row = 0; row < controls.length; row++) {
        for (int column = 0; column < offsets.length; column++) {
            final double actual = ((Double) method.invoke(null,
                controls[row], offsets[column])).doubleValue();
            assertThat(actual).isCloseTo(expected[row][column / 2],
                org.assertj.core.data.Offset.offset(1e-9));
            assertThat(actual).isLessThan(1.25);
        }
    }
}
```

Implement `appendPathPins` using the existing immutable output values:

```java
private static void appendPathPins(final StringBuilder output, final String name,
        final EdgePaths paths) {
    output.append(name).append('\n');
    for (final java.util.Map.Entry<ProjectedEdgeKey, EdgePath> entry
            : paths.byEdge().entrySet()) {
        final EdgePath path = entry.getValue();
        output.append(CanonicalKeys.edge(entry.getKey())).append('|')
            .append(path.shape()).append('|').append(Double.toHexString(path.length()));
        for (final List<LayoutPoint> points : Arrays.asList(
                path.definingPoints(), path.samples())) {
            output.append('|');
            for (final LayoutPoint point : points) {
                output.append(Double.toHexString(point.x())).append(',')
                    .append(Double.toHexString(point.y())).append(';');
            }
        }
        output.append('\n');
    }
    output.append("classes=").append(paths.diagnostics().edgesStraight()).append(',')
        .append(paths.diagnostics().edgesBow()).append(',')
        .append(paths.diagnostics().edgesDogleg()).append(',')
        .append(paths.diagnostics().edgesFallback()).append('\n');
}
```

`residualRoutePins()` appends the six existing fixtures in the exact order listed in Interfaces, using their production `route(...)` calls. Then append `settled-reference`: load the committed fixture, compute its hulls with `new GraphGeometryEngine().computeHulls(projection, positions, metrics)`, obtain visible endpoints through `ProjectedEndpointVisibility.visibleEndpoints(projection.nodes(), projection.enclosures())`, and route. Construct `metrics` with `new AwtGeometryTextMetrics(new java.awt.Font("Dialog", java.awt.Font.PLAIN, 12), new java.awt.font.FontRenderContext(new java.awt.geom.AffineTransform(), false, false))`, matching the existing performance diagnostic exactly. Do not relax or regenerate the ledger.

Before the ratio test's reflection lookup, temporarily print:

```java
System.out.println("RESIDUAL_ROUTE_PINS=" + java.util.Base64.getEncoder().encodeToString(
    residualRoutePins().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
```

- [ ] **Step 2: Run red and extract the baseline output before changing routing.**

```bash
export JAVA_HOME=~/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"
gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.geometry.EdgeRouterShould.computesTheControlPositionRatioTable'
```

Expected: the missing helper fails the named test. Extract the printed resource from the JUnit XML; do not copy rounded console coordinates:

```bash
python3 - <<'PY'
import base64
from pathlib import Path
import xml.etree.ElementTree as ET
root = Path('freeplane_plugin_graph/build/test-results/test')
rows = []
for path in root.glob('TEST-*.xml'):
    doc = ET.parse(path)
    for element in doc.iter('system-out'):
        rows.extend(line.split('=', 1)[1] for line in (element.text or '').splitlines()
                    if line.startswith('RESIDUAL_ROUTE_PINS='))
assert len(rows) == 1, rows
output = Path('freeplane_plugin_graph/src/test/resources/geometry/edge-node-crossing-residual-legacy-paths.txt')
output.parent.mkdir(parents=True, exist_ok=True)
output.write_bytes(base64.b64decode(rows[0], validate=True))
assert b'classes=21,1,0,0\n' in output.read_bytes()
PY
```

Remove the temporary print after extraction. Record the resource SHA-256 and the source commit in controller evidence. Do not capture it again after Task 2.

- [ ] **Step 3: Implement the closed-form ratio integral independently of midpoint routing.**

For the finite, nonzero signed offsets in the new group, expand the integrand to `2*sqrt(a*t*t+b*t+c)` and use this primitive. No changes to `analyticRatio`, `bowPath`, `doglegPath`, `EdgePath`, route order or budget handling are needed in this task.

```java
private static double controlPositionRatio(final double controlFraction,
        final double offsetFraction) {
    final double slope = 1.0 - 2.0 * controlFraction;
    final double squaredOffset = offsetFraction * offsetFraction;
    final double a = slope * slope + 4.0 * squaredOffset;
    final double b = 2.0 * controlFraction * slope - 4.0 * squaredOffset;
    final double c = controlFraction * controlFraction + squaredOffset;
    return 2.0 * (controlRatioPrimitive(1.0, a, b, c)
        - controlRatioPrimitive(0.0, a, b, c));
}

private static double controlRatioPrimitive(final double t, final double a,
        final double b, final double c) {
    final double rootA = Math.sqrt(a);
    final double root = Math.sqrt(a * t * t + b * t + c);
    final double linear = 2.0 * a * t + b;
    return linear * root / (4.0 * a)
        + (4.0 * a * c - b * b) / (8.0 * a * rootA)
            * Math.log(2.0 * rootA * root + linear);
}
```

- [ ] **Step 4: Run green and confirm the baseline resource is stable.**

```bash
gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.geometry.EdgeRouterShould'
sha256sum freeplane_plugin_graph/src/test/resources/geometry/edge-node-crossing-residual-legacy-paths.txt
```

Expected: ratio table passes for all 24 signed pairs; existing router tests remain green; no route behavior changed. Preserve the captured resource and serializer for Task 3.

- [ ] **Step 5: Commit.**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/EdgeRouter.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/EdgeRouterShould.java freeplane_plugin_graph/src/test/resources/geometry/edge-node-crossing-residual-legacy-paths.txt
git diff --cached --check
git commit -m "test(graph): pin residual routing ratios and paths [2026-10-26-edge-node-crossing-residual]"
```

## Task 2: Append the control-position routing group and its geometry guards

**Implementer tier:** Frontier

**Lane:** routing. **Category:** geometry invariants. **Depends on:** Task 1.

**Goal:** Make the measured off-midpoint case routable while preserving group order, full obstacle checks, trimming, sampling and budgets.

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/EdgeRouter.java:42-191` and `:273-371` (constants, the branch after dogleg failure, new helpers).
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/EdgeRouterFixture.java:77-305` (new fixtures only; retain existing radii).
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/EdgeRouterShould.java:1-429`.
- Read only: Task 1's expected-path resource.

**Interfaces:**

- Consumes: `private static double EdgeRouter.controlPositionRatio(double controlFraction, double offsetFraction)` from Task 1.
- Consumes: `public EdgePaths EdgeRouter.route(GraphProjection, LayoutPositions, GraphGeometry, Set<ProjectedEndpointKey>)`; `public static EdgePath EdgePath.bow(LayoutPoint start, LayoutPoint control, LayoutPoint end)`; existing private `attachment(ProjectedEndpointKey endpoint, LayoutPoint toward, GraphGeometry geometry): LayoutPoint` and `clears(EdgePath candidate, ProjectedEdge edge, QueryGrid grid, GraphGeometry geometry, RouterBudget budget): boolean`.
- Produces: `private static EdgePath bestControlPositionRung(Resolved current, QueryGrid grid, GraphGeometry geometry, RouterBudget budget)` and `private static EdgePath controlPositionPath(Resolved current, LayoutPoint normal, double controlFraction, double offset, GraphGeometry geometry)`.
- Produces: package-local fixture factories `static EdgeRouterFixture offMidpointObstacle()`, `midpointOrdering()`, `maxBendObstacle()`, `controlPositionBarrier()`; test helper `private static double minimumCentreDistance(EdgePath path, LayoutPoint centre)`.

- [ ] **Step 1: Add the exact red tests from spec §§2.4 and 9.**

Use generation `1L`, no pins/enclosures and radius `8.0` for every node in the new fixtures. Within `EdgeRouterFixture`, build the new cases with existing `node`, `edge`, `projection`, `points` and the constructor's explicit radii map; do not inherit the class default radius `14.0` accidentally. For example:

```java
static EdgeRouterFixture offMidpointObstacle() {
    final ProjectedNodeKey a = node("A");
    final ProjectedNodeKey b = node("B");
    final ProjectedNodeKey n = node("N");
    final Map<ProjectedNodeKey, Double> radii = new LinkedHashMap<ProjectedNodeKey, Double>();
    for (final ProjectedNodeKey key : Arrays.asList(a, b, n)) {
        radii.put(key, Double.valueOf(8.0));
    }
    return new EdgeRouterFixture(projection(Arrays.asList(a, b, n),
        Collections.singletonList(edge(a, b))), LayoutPositions.of(
            points(a, -18.091, 0.0, b, 18.091, 0.0, n, -5.0455, 0.0),
            Collections.<EnclosureHullKey, LayoutPoint>emptyMap()), radii);
}
```

| Exact test name | Fixture and executable assertions |
|---|---|
| `routesTheMeasuredOffMidpointObstacleWithAControlPositionBow` | `offMidpointObstacle`; path `BOW`, control `(-9.0455,18.091)` within `1e-12`; selected `A=.25,f=+.50`; minimum sample-segment centre distance `11.374205532928446` within `1e-9`; surface clearance `>=3-1e-9`; one bow, zero fallback, complete route. |
| `rejectsMovingControlPositionGroupAheadOfMidpointBows` | `midpointOrdering`: `A=(-50,0),B=(50,0),N=(0,0)`; midpoint control `(0,30)` wins, with its exact defining/sample lists; compare against separately constructed legal control bow `(-25,50)` and require different samples. |
| `keepsTheDoglegLadderAheadOfTheControlPositionGroup` | Existing `doglegAccepting()` with endpoint radii `14`, obstacles `12` at `(60.44,±13.77)`; one `DOGLEG`, zero other classifications; exact pre-existing positive `.30` defining/sample lists; a legal `.75,+.50` control alternative cannot replace it. |
| `skipsControlPositionCandidatesThatExceedMaxBend` | `maxBendObstacle`: `A=(-100,0),B=(100,0),N=(-84.5,0)`; require `.25,+.30` control `(-50,60)` and centre clearance at least `11.163`; `abs(.50)*200=100>96`, whereas both analytic ratios are below `1.25`. |
| `usesTheFullClearPredicateForControlPositionCandidates` | `controlPositionBarrier`: nodes `A=(-18.091,0),B=(18.091,0),N=(-5.0455,0),Z0=(0,-100),Z1=(0,100)`, edges `A-B` and `Z0-Z1`, all radius `8`. Assert canonical `A-B` is processed first. A disc-only control bow is legal but crosses the later vertical chord; require the first published path to remain a straight fallback. Use the unbarred fixture as the positive control. |

The barrier's endpoints are far from every first-edge candidate, while any path connecting its left and right attachments crosses the vertical chord. Measure this via `EdgeRouter.properlyCross(...)`; do not assume it from a drawing. No exact whole-frame preservation is claimed for the barrier's later edge.

The independent distance helper evaluates each closed segment of `path.samples()` using the obstacle's centre, clamps the projection parameter to `[0,1]`, and takes the minimum. Its core is:

```java
final double dx = second.x() - first.x();
final double dy = second.y() - first.y();
final double squared = dx * dx + dy * dy;
final double t = squared == 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0,
    ((centre.x() - first.x()) * dx + (centre.y() - first.y()) * dy) / squared));
final double distance = Math.hypot(centre.x() - (first.x() + t * dx),
    centre.y() - (first.y() + t * dy));
```

Do not reuse the old `RouterClearanceProbe.distance` numbers as an oracle: its origin-based measurement was rejected in design review. Assert radius `8.0` from the actual fixture geometry. Write the off-midpoint assertion in this form:

```java
assertThat(path.shape()).isEqualTo(EdgePath.Shape.BOW);
assertThat(minimumCentreDistance(path, LayoutPoint.of(-5.0455, 0.0)) - 8.0)
    .isGreaterThanOrEqualTo(EdgeRouter.EDGE_NODE_CLEARANCE - 1e-9);
assertThat(paths.diagnostics().edgesFallback()).isZero();
```

- [ ] **Step 2: Run red.**

```bash
gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.geometry.EdgeRouterShould'
```

Expected: off-midpoint and max-bend witnesses still publish straight fallbacks; preserve these failure excerpts. The group-order and full-predicate controls may already pass; their targeted mutants in Step 4 must fail.

- [ ] **Step 3: Implement only the appended group.**

Add private constants in the exact order:

```java
private static final double[] CONTROL_FRACTIONS = {0.25, 0.75, 1.0 / 3.0, 2.0 / 3.0};
private static final double[] OFFSET_FRACTIONS = {0.50, -0.50, 0.30, -0.30, 0.15, -0.15};
```

After the existing dogleg success branch and before its straight fallback, add a budget guard and call the new helper. A successful member increments `edgesBow` and publishes through the existing grid/map, just as the existing bow branch does. If the budget was exhausted by dogleg search, publish the existing straight fallback without probing the new group.

```java
if (!budget.routeBudgetExhausted()) {
    final EdgePath controlPosition = bestControlPositionRung(current, grid, geometry, budget);
    if (controlPosition != null) {
        grid.publish(current.edge.key(), controlPosition);
        byEdge.put(current.edge.key(), controlPosition);
        edgesBow++;
        continue;
    }
}
```

Implement the new helper with one `A` loop and one signed-offset loop:

```java
private static EdgePath bestControlPositionRung(final Resolved current, final QueryGrid grid,
        final GraphGeometry geometry, final RouterBudget budget) {
    final double chordLength = distance(current.firstAnchor, current.secondAnchor);
    if (chordLength == 0.0) {
        return null;
    }
    final LayoutPoint normal = chordNormal(current.firstAnchor, current.secondAnchor);
    for (final double controlFraction : CONTROL_FRACTIONS) {
        for (final double offsetFraction : OFFSET_FRACTIONS) {
            final double offset = offsetFraction * chordLength;
            if (Math.abs(offset) > MAX_BEND) {
                continue;
            }
            if (controlPositionRatio(controlFraction, offsetFraction) > ROUTE_LENGTH_CAP) {
                continue;
            }
            final EdgePath candidate = controlPositionPath(current, normal,
                controlFraction, offset, geometry);
            if (candidate == null || candidate.length() > ROUTE_LENGTH_CAP * chordLength) {
                continue;
            }
            if (clears(candidate, current.edge, grid, geometry, budget)) {
                return candidate;
            }
            if (budget.routeBudgetExhausted()) {
                return null;
            }
        }
    }
    return null;
}

private static EdgePath controlPositionPath(final Resolved current, final LayoutPoint normal,
        final double controlFraction, final double offset, final GraphGeometry geometry) {
    final LayoutPoint control = LayoutPoint.of(
        current.firstAnchor.x() + controlFraction * (current.secondAnchor.x() - current.firstAnchor.x())
            + offset * normal.x(),
        current.firstAnchor.y() + controlFraction * (current.secondAnchor.y() - current.firstAnchor.y())
            + offset * normal.y());
    final LayoutPoint start = attachment(current.edge.first(), control, geometry);
    final LayoutPoint end = attachment(current.edge.second(), control, geometry);
    return start == null || end == null ? null : EdgePath.bow(start, control, end);
}
```

Leave the existing `bestRung`, midpoint `bowPath`, dogleg implementation, `analyticRatio`, sampling formula, obstacle model and strict predicate unchanged. Unavailable attachments use the existing `null` path; add no new rejection policy.

- [ ] **Step 4: Run green, then falsify the geometry guards in disposable copies.**

```bash
gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.geometry.EdgeRouterShould'
```

Expected: all router tests pass. In separate disposable copies, move the group before midpoint bows, insert it before doglegs, replace only the new group's `clears` call with `clearsDiscs`, and remove only its max-bend check. The corresponding exact named tests must fail. If the max-bend mutant is still green, strengthen the candidate-observation assertion with reflection on the selected control; do not loosen its clearance or ratio expectations. Capture each selected defining/sample list and the actual clearance in controller evidence. Recheck the Task 1 resource digest without rewriting it.

- [ ] **Step 5: Commit.**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/EdgeRouter.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/EdgeRouterFixture.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/EdgeRouterShould.java
git diff --cached --check
git commit -m "fix(graph): append control-position routing bows [2026-10-26-edge-node-crossing-residual]"
```

## Task 3: Close the routing preservation gate before relocation

**Implementer tier:** Standard

**Lane:** routing. **Category:** fixture preservation. **Depends on:** Tasks 1 and 2.

**Goal:** Compare every named legacy path and the settled workload against the pre-extension output, then close the routing lane with a green module suite.

**Files:**

- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/EdgeRouterShould.java:1-429`.
- Read only: `freeplane_plugin_graph/src/test/resources/geometry/edge-node-crossing-residual-legacy-paths.txt` and `freeplane_plugin_graph/src/test/resources/performance/settled-reference.csv`.

**Interfaces:**

- Consumes: test-local `private static String residualRoutePins()` and UTF-8 expected-path resource from Task 1; `public EdgePaths EdgeRouter.route(GraphProjection, LayoutPositions, GraphGeometry, Set<ProjectedEndpointKey>)` from Task 2, including appended `BOW` results.
- Consumes: `public List<LayoutPoint> EdgePath.definingPoints()`, `samples()`, `public double length()`, and `public EdgePath.Shape shape()`; equality is over captured output values, not elapsed timings.
- Produces: `@Test public void preservesTheSettledReferenceAndNamedLadderFixtures() throws Exception`, and the prerequisite routing-green gate for Task 4. No runtime interface changes.

- [ ] **Step 1: Write the exact preservation test.**

Read the resource with a Java 8 stream loop, then compare its UTF-8 string with `residualRoutePins()`. A byte-for-byte comparison of the hexadecimal serialization pins every defining/sample value and classification for all seven workloads. The test must reject a missing or empty resource.

```java
@Test
public void preservesTheSettledReferenceAndNamedLadderFixtures() throws Exception {
    final String resource = "/geometry/edge-node-crossing-residual-legacy-paths.txt";
    final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
    try (java.io.InputStream input = getClass().getResourceAsStream(resource)) {
        assertThat(input).as(resource).isNotNull();
        final byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) >= 0) {
            bytes.write(buffer, 0, count);
        }
    }
    final String expected = new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    assertThat(expected).isNotEmpty();
    assertThat(residualRoutePins()).isEqualTo(expected);
}
```

Retain these exact tests, each once: `routesTheFirstEdgeAroundALaterChordsCrossing`, `acceptsTheSameSideDoglegWhenStraightAndBowsAreBlocked`, `waivesTheDistanceRequirementForATightFan`, `exposeStraightFallbackForAStraightOnlyScaffold`, `rejectsTheDeadDoglegRungAndFallsBack`, `keepsClearOfAParallelPathInTheNeighbouringCell`, and `isDeterministicAcrossRunsAndPermutedEdgeOrder`. Spec §9 spells the parallel-path test `keepsClearOfAParallelPathInTheNeighbouringCell`; the delivered method is `keepClearOfAParallelPathInTheNeighbouringCell`. Rename that existing asserting method to the exact spec spelling, preserving its body, so the required-name inventory contains one real assertion rather than a duplicate alias. Preserve the convex-quad `0/0/0/2` and one residual crossing; `cellCornerFan` retains 15 crossings and `closeParallelPaths` retains its bow at 2.9-unit separation. Add no claim of universal frame equivalence.

- [ ] **Step 2: Obtain red preservation evidence.**

The parent is expected to pass this assertion. In a disposable copy, change the midpoint bow order only, or perturb one midpoint control coordinate. Run the exact new preservation test and require a mismatch against the immutable captured resource. Do not rewrite the pin resource or leave the mutant installed.

```bash
gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.geometry.EdgeRouterShould.preservesTheSettledReferenceAndNamedLadderFixtures'
```

- [ ] **Step 3: Complete the assertion coverage.**

Wire the seven named workloads into the serializer if any was omitted, using the already captured original data; never substitute post-extension results for missing baseline evidence. Compare reversed edge/node/geometry input order with canonical outputs using the existing permutation test. On identical fixture inputs, verify classification, defining points and sample lists; diagnostic budget counters can increase on failed ladders and are governed by the existing budget tests rather than a blanket counter-equality assertion.

- [ ] **Step 4: Run the routing gate.**

```bash
gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.geometry.EdgeRouterShould'
gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.performance.SettledReferenceFixtureShould'
gradle :freeplane_plugin_graph:test
```

Expected: all pass, ledger digest unchanged, settled routes exactly `21/1/0/0`. Stop on a failing named fixture and repair routing within its approved scope. Record the green task commit and command receipts as prerequisites; relocation starts only after this gate passes.

- [ ] **Step 5: Commit.**

```bash
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/EdgeRouterShould.java
git diff --cached --check
git commit -m "test(graph): gate residual routing preservation [2026-10-26-edge-node-crossing-residual]"
```

## Task 4: Expand selected relocation families after a failed round

**Implementer tier:** Frontier

**Lane:** relocation. **Category:** round-loop diff and geometry invariants. **Depends on:** Task 3's green routing gate.

**Goal:** Implement exactly spec §3.2 edits E1–E4, repair the A1 fourth-ring witness and preserve all successful ring-0 prefixes.

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrection.java:45-85`, `:109-219`, `:355-390`, `:527-629`, and `:1361-1460` (constants, minimal loop wrapper, provenance aggregate, ring-local generator).
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrectionShould.java:1-3893` (new A1 test, reflection assertions, successful-prefix assertions and only the authorized blocked-control counter re-pins).

**Interfaces:**

- Consumes: `public EdgeConflictResult EdgeConflictCorrection.apply(GraphProjection projection, LayoutPositions positions, GeometryTextMetrics metrics, List<PinProjection> pins, Set<ProjectedEndpointKey> visibleEndpoints, BoundarySeparationResult boundaryResult)`; `static Detection detectConflicts(GraphProjection projection, LayoutPositions positions, GraphGeometry geometry, Set<ProjectedEndpointKey> visibleEndpoints)`; unchanged `Candidate`, `CandidateIdentity`, `RankedCandidate.ORDER` and `greedyDisjoint(List<Candidate> pool): List<Candidate>`.
- Consumes: unchanged `private Evaluation verify(List<Candidate> attempt, GraphProjection projection, LayoutPositions currentPositions, GraphGeometry currentGeometry, GeometryTextMetrics metrics, Set<ProjectedEndpointKey> visibleEndpoints, Detection currentConflicts, BoundaryInvariants boundaryInvariants, Map<ProjectedNodeKey,List<EnclosureHullKey>> nodeHullsByKey, Map<ProjectedNodeKey,List<EnclosureHullKey>> enclosurePaths)`.
- Produces: `private static PoolGeneration generateCandidates(Detection conflicts, LayoutPositions positions, Set<ProjectedNodeKey> pinned, Map<ProjectedNodeKey,List<EnclosureHullKey>> enclosurePaths)`; `PoolGeneration` carries `List<Candidate> pool` and `List<SelectedFamily> selectedFamilies`.
- Produces: `static final class SelectedFamily` with `final Conflict conflict` and `final List<Candidate> candidates`; `private static List<Candidate> expandSelectedFamiliesByRing(List<SelectedFamily> selectedFamilies, LayoutPositions positions, Set<ProjectedNodeKey> pinned)`.
- Produces: `private static void addEdgeOverNodeCandidatesAtRing(List<Candidate> candidates, Conflict conflict, LayoutPositions positions, Set<ProjectedNodeKey> pinned, int ring)`; retain existing four-argument `addEdgeOverNodeCandidates(...)` as the ring-0 entry into this one generator. This keeps the current reflection test's method lookup unambiguous.

- [ ] **Step 1: Write the exact A1 regression and record the old outcomes.**

Add `expandsTheA1RejectedFamilyToTheFourthRing`, using the existing radius-selecting `fixture(...)` helper, not its default `14.0` radius. Construct:

```java
final Fixture fixture = fixture(1L,
    new String[] {"A", "B", "N", "U", "V", "D", "E"},
    new double[] {-100, 0, 100, 0, 0, 0, -140, 11, 140, 11, -140, -11, 140, -11},
    new int[][] {{0, 1}, {3, 4}, {5, 6}}, 8.0);
```

Create active pin records at their actual coordinates for indices `0,1,3,4,5,6`; leave only `N` unpinned. Initial detection is exactly `edgeNodePair(A-B,N)`; initial node-gap and hull sets are empty. Reflect the six ring-0 targets and scan candidate positions using production detection; record each introduced upper/lower edge-node identity and assert A1 fails. The final assertions include:

```java
assertThat(result.positions().nodes().get(fixture.key(2)))
    .isEqualTo(LayoutPoint.of(0.0, -23.0));
assertThat(result.diagnostics().verifications()).isEqualTo(25);
assertThat(result.diagnostics().rounds()).isEqualTo(1);
assertThat(result.diagnostics().conflictsResidual()).isEmpty();
assertThat(result.diagnostics().repairsApplied()).hasSize(1);
assertThat(EdgeConflictCorrection.detectConflicts(fixture.projection,
    result.positions(), publishedGeometry(fixture, result), fixture.visible).keys()).isEmpty();
```

Also assert repair kind `TRANSLATION`, `from=(0,0)`, `to=(0,-23)`, moved-node set `{N}`, `batchIndex=0`; central straight samples `(-92,0),(92,0)`, length `184`; no node/hull residuals and all stop flags false. Record six rejections for every earlier ring, not merely the final position.

Before modifying production, run and retain the unchanged successful-prefix tests and the blocked controls. Extend their assertions from spec §4.3 where needed; do not change a successful input's expected outcome:

| Exact test / fixture | Required preserved accepted result |
|---|---|
| `repairsNineIndependentObstructionsInBoundedBatches` / E1 | nine translations; first eight batch 0, ninth batch 1; `rounds=2, verifications=2, verificationWork=1755`; empty residual; nine straight routes |
| `repairsOnTheOppositeSideWhenTheNearestSideBreaksTheGap` / E2 | `(0,5)->(0,-11)`; `1/2/30` for rounds/verifications/work |
| `repairsTheBlockedColumnWithTheFirstFeasibleDiagonal` / E3 | `(0,0)->(-25,-11)`; `1/3/252`; empty residual |
| `keepsThePinnedColumnObstructionAsAnExactResidual` | zero rounds/verifications/repairs; exact original edge-node key; unchanged anchors/positions |
| `repairsTheColumnWithADormantPin` | E3's exact installing result and counters, dormant record retained |
| `clearsTheActualProminentNodeDisc` | radius `9.6`; `(0,5)->(0,12.6)`; one verification; three straight paths |
| `stopsAtFourAcceptedRoundsWithoutClaimingExhaustion` | `crowdedRowsFixture(33)`; four rounds/verifications, 32 repairs, exact `N_9` residual, no exhaustion |
| `acceptsTheC2TargetAtExactlyTheMaximumExcursion` | movement `64.0` accepted to `(0,65)` with radius `62`; one verification |
| `rejectsEveryC2TargetBeyondTheMaximumExcursionWithoutClamping` | radius `62` at `(0,0)`; no verification or repair; exact residual |
| `rejectsTheZeroMovementTargetButAcceptsTheOppositeSide` | supplied geometry centre `(0,0)`, position `(0,-11)`; zero target discarded; opposite accepted at verification 1 |

- [ ] **Step 2: Run red.**

```bash
gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.layout.EdgeConflictCorrectionShould.expandsTheA1RejectedFamilyToTheFourthRing'
```

Expected: the old search leaves `N=(0,0)` with the exact original residual after its six ring-0 rejections. Retain this evidence and the existing loop text before editing.

- [ ] **Step 3: Apply E1, keeping selection and identities unchanged.**

Return `PoolGeneration` instead of the bare pool. Preserve conflict-key order, eight nonempty retained-family bound and the existing `retained.add(new CandidateIdentity(candidate))` filtering. For each family that contributes at least one previously unretained candidate, append one `SelectedFamily` carrying that exact conflict and its retained candidates in generated order. Do not select a previously duplicate-only or empty family.

```java
static final class SelectedFamily {
    final Conflict conflict;
    final List<Candidate> candidates;

    SelectedFamily(final Conflict conflict, final List<Candidate> candidates) {
        this.conflict = conflict;
        this.candidates = Collections.unmodifiableList(new ArrayList<Candidate>(candidates));
    }
}

private static final class PoolGeneration {
    final List<Candidate> pool;
    final List<SelectedFamily> selectedFamilies;

    PoolGeneration(final List<Candidate> pool, final List<SelectedFamily> selectedFamilies) {
        this.pool = pool;
        this.selectedFamilies = Collections.unmodifiableList(
            new ArrayList<SelectedFamily>(selectedFamilies));
    }
}
```

These are the two aggregate types explicitly specified by E1; introduce no further scheduling types and do not change `Candidate` or `CandidateIdentity`.

- [ ] **Step 4: Apply E3 with the same basis, finite checks and rank tuple.**

```java
private static final int MAX_CLEARANCE_STEPS = 5;
private static final double MAX_CLEARANCE_STEP = 3.0;
```

Move the existing C2 generator body into `addEdgeOverNodeCandidatesAtRing`; its old four-argument entry calls it with `ring=0`. Change only the `rho` expression inside that body:

```java
final double rho = ring == 0 ? disc.radius + EDGE_NODE_CLEARANCE
    : disc.radius + EDGE_NODE_CLEARANCE + ring * MAX_CLEARANCE_STEP;
```

All other arithmetic and tests, including signed-zero normalization, `h`, six-slot generation and `RankedCandidate.ORDER`, stay identical. Use `family.conflict` directly; do not recompute chord/disc objects from positions. Implement expansion:

```java
private static List<Candidate> expandSelectedFamiliesByRing(
        final List<SelectedFamily> selectedFamilies, final LayoutPositions positions,
        final Set<ProjectedNodeKey> pinned) {
    final List<Candidate> expanded = new ArrayList<Candidate>();
    for (int ring = 1; ring < MAX_CLEARANCE_STEPS; ring++) {
        final Set<CandidateIdentity> retained = new HashSet<CandidateIdentity>();
        for (final SelectedFamily family : selectedFamilies) {
            if (family.conflict.isCrossing()) {
                continue;
            }
            final List<Candidate> candidates = new ArrayList<Candidate>(6);
            addEdgeOverNodeCandidatesAtRing(candidates, family.conflict, positions, pinned, ring);
            for (final Candidate candidate : candidates) {
                if (retained.add(new CandidateIdentity(candidate))) {
                    expanded.add(candidate);
                }
            }
        }
    }
    return expanded;
}
```

This is ring-major, then original family order, then original within-family rank. Never globally re-sort the flattened pool, reselect families, regenerate C1, share the dedup set across rings, reserve slots or add a ring-reservation rule. The ceiling is 192 generated proposals, independent of the 64 actual-verification cap.

- [ ] **Step 5: Apply E2/E4 by reusing the existing inner pool loop once.**

Replace the generation assignment with `PoolGeneration generated = generateCandidates(...)`, then use `final List<Candidate> pool = generated.pool`. Keep `acceptedThisRound` in its current scope. Wrap the existing inner `while` in a two-pass stage loop; its entire body, including every `verifications++`, batch/singleton attempt, `ACCEPT`, `DETECTION_STOP`, `verificationWorkAtCap`, attempt-cap and `reconcileStopFlags` branch, remains character-for-character unchanged:

```java
for (int searchStage = 0; searchStage < 2 && !acceptedThisRound && !verificationStopped
        && rounds < MAX_RELOCATION_ROUNDS && verifications < MAX_VERIFICATIONS_PER_FRAME;
        searchStage++) {
    if (searchStage == 1) {
        pool.addAll(expandSelectedFamiliesByRing(generated.selectedFamilies,
            currentPositions, pinned));
    }
    // Insert the existing inner while statement and its complete body here verbatim.
}
```

The comment identifies a literal move of existing source, not a new implementation of the attempt loop. There is only one copy of that loop in the delivered method. Do not introduce a `runExistingPoolLoop` helper or a simplified second copy. The guard prevents stage 1 after acceptance or any stop. Keep the existing `if (!acceptedThisRound) break`, post-acceptance complete detection, terminal detection and coupled hull/node rebuild below this wrapper unchanged. `generated` is local to one round; the next accepted round starts with fresh stage-0 detection and selection.

Use a textual extraction of the before/after inner loop to prove the only changes to `relocate` are E1's assignment and E2's wrapper. Do not refactor `verify`, flag reconciliation, publication or terminal rebuild.

- [ ] **Step 6: Update only the authorized failed-round control counters and run green.**

Re-pin all assertions of `fullyBlockedFixture()` from `6/432` to `30/2160`, and `duplicateChordFixture()` from `6/660` to `30/3300`, including `deduplicatesIdenticalAlternativesFromOverlappingChords` and repeated assertions in `keepsSelectionLimitsOrdinaryAndDirectEnumerationEligible`. Residual keys, positions, anchors and flags remain exact. Preserve the no-candidate control's dogleg minimum centre distance `23.590001215504564`, and both duplicate routes' publication with no route stop. Describe the increase as **measured expansion cost**, never an improvement.

Extend `keepsTheFiniteC2FamilyWithinItsStatedBounds` to inspect each ring independently: at most six candidates per family, no zero targets, at most eight selected families and at most 192 expanded proposals. Keep the existing ring-0 reflection helper working by retaining its four-argument method name. Assert selected `Conflict` object identity, skip crossing provenance, and ensure selection never exceeds its original bound.

```bash
gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.layout.EdgeConflictCorrectionShould'
gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.geometry.EdgeRouterShould'
```

Expected: all pass; successful-prefix table unchanged. In disposable copies, suppress stage 1, permit crossing-family expansion, or retain selected families across rounds; the A1/provenance/round-prefix assertions must fail. A test of provenance must inspect real generated records; do not fabricate a nonempty C1 translation family just to reach the filter.

- [ ] **Step 7: Commit.**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrection.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrectionShould.java
git diff --cached --check
git commit -m "fix(graph): expand failed relocation rounds [2026-10-26-edge-node-crossing-residual]"
```

## Task 5: Execute the A2/A3 construct-or-declare search

**Implementer tier:** Standard

**Lane:** relocation. **Category:** deterministic fixture search and subset assertions. **Depends on:** Task 4.

**Goal:** Implement spec §4.2 literally, producing a falsifiable witness or the explicit search-limit assertion without claiming an unchecked geometry.

**Files:**

- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrectionShould.java:1461-1576`, `:2648-2697`, and fixture/helper region `:3044-3893`.
- Create: `freeplane_plugin_graph/src/test/resources/geometry/edge-node-crossing-residual-a2-a3-search.txt` (the measured 59-case rejection table and conditional outcome).

**Interfaces:**

- Consumes: unchanged `public EdgeConflictResult EdgeConflictCorrection.apply(GraphProjection projection, LayoutPositions positions, GeometryTextMetrics metrics, List<PinProjection> pins, Set<ProjectedEndpointKey> visibleEndpoints, BoundarySeparationResult boundaryResult)`; Task 4's private `static PoolGeneration generateCandidates(Detection detection, LayoutPositions positions, Set<ProjectedNodeKey> pinned)`, `private static List<Candidate> expandSelectedFamiliesByRing(List<SelectedFamily> selectedFamilies, LayoutPositions positions, Set<ProjectedNodeKey> pinned)`, and `private static void addEdgeOverNodeCandidatesAtRing(List<Candidate> candidates, Conflict conflict, LayoutPositions positions, Set<ProjectedNodeKey> pinned, int ring)` through test reflection.
- Consumes: `public GraphGeometry GraphGeometryEngine.computeHulls(GraphProjection projection, LayoutPositions positions, GeometryTextMetrics metrics)`; `public static List<String> NodeSeparationProjection.violatingPairs(GraphProjection projection, LayoutPositions positions)`; `public Set<String> BoundaryInvariants.detectPairKeys(GraphProjection projection, GraphGeometry geometry)`; `public static Map<ProjectedNodeKey, List<EnclosureHullKey>> BoundaryInvariants.nodeHullsByKey(GraphProjection projection)`; `private Evaluation EdgeConflictCorrection.verify(List<Candidate> attempt, GraphProjection projection, LayoutPositions currentPositions, GraphGeometry currentGeometry, GeometryTextMetrics metrics, Set<ProjectedEndpointKey> visibleEndpoints, Detection currentConflicts, BoundaryInvariants boundaryInvariants, Map<ProjectedNodeKey, List<EnclosureHullKey>> nodeHullsByKey, Map<ProjectedNodeKey, List<EnclosureHullKey>> enclosurePaths)`. Copy this exact parameter list into the reflection lookup; do not select an overload by argument count alone.
- Produces: `@Test public void expandsTheA2RejectedFamilyWithoutReselectingIt() throws Exception`; test-only `private static String a2A3SearchEvidence() throws Exception` and `private static Set<String> rejectedClauses(Fixture fixture, LayoutPositions candidatePositions)`; immutable expected evidence bytes.
- Produces: exact-name source-backed assertion `@Test public void vetoesTheUnsafeSiblingTargetWithA3Subset()` over the existing sibling fixture. Keep the distinct existing `vetoesTheNearestDirectTargetThatAddsASiblingHullViolation` test; do not rename or delete an independently useful assertion.

- [ ] **Step 1: Add the mandatory named tests and conditional assertions.**

Construct each search input with production helpers: `A=(-100,0)`, `B=(100,0)`, passed-over node `n=(0,0)`, `b=(0,y_b)`, `h2=(0,y_b+6)`, generation `1L`, all radii `8`, one `A-B` edge, no pins and no enclosures. The family is fixed; add no blocker discs, active pins or hidden enclosure geometry. The added `h2` can create a pre-existing gap with `b`; A2 is a subset comparison against that actual initial set, not an assertion that every initial pair is legal.

Use integer enumeration to avoid accumulation drift:

```java
for (int sample = 0; sample < 59; sample++) {
    final double blockerY = 22.0 + 0.5 * sample;
    final Fixture fixture = clearanceFixture(new String[] {"A", "B", "n", "b", "h2"},
        new double[] {-100, 0, 100, 0, 0, 0, 0, blockerY, 0, blockerY + 6.0},
        new int[][] {{0, 1}});
    // Reflect the production n family at rings 0,1,2,3,4 and inspect all six slots per ring.
}
```

For every value, record all 30 targets and independent rejecting subsets A1/A2/A3. Compute A1 with production complete conflict scans and strict-subset identity comparison; A2 with the original and target node-gap key sets; A3 with original and fully recomputed hull-pair sets. Empty enclosure sets make A3 empty in this particular family; record that fact rather than inventing a hull veto. A4 is also vacuous here. Run the production full verification on every generated target using a fresh correction instance and the original current state; budget counters must not accumulate across the 1770 independent observations. Record its `ACCEPT/REJECT/STOP` outcome and reject any unexpected `STOP` in this bounded fixture.

Maintain the existing private reflection pattern to obtain real `Candidate` objects; do not duplicate target-generation arithmetic in the oracle. For the full verifier's hull map use `BoundaryInvariants.nodeHullsByKey(projection)`; enclosure paths for this input are each projected node mapped to an empty list. Invoke the exact existing private method with these original-state values, not candidate-mutated state. Use its `outcome` field as the full-verification observation. Keep diagnostic observations out of production counters.

The top-level test must make this exhaustive conditional assertion:

```java
assertThat(sampleCount).isEqualTo(59);
assertThat(targetObservations).isEqualTo(59 * 5 * 6);
if (witnessCount > 0) {
    assertThat(witnessBlockerY).isBetween(22.0, 51.0);
    assertThat(allSixRingZeroTargetsRejectedByA2OrA3).isTrue();
    assertThat(firstAcceptedRing).isBetween(1, 4);
    assertThat(fullVerificationAcceptedWitness).isTrue();
    assertThat(actualRun.positions()).isEqualTo(expectedPositionsPinnedFromTheWitness);
    assertThat(actualRun.diagnostics().verifications()).isEqualTo(expectedVerifications);
    assertThat(actualRun.diagnostics().verificationWork()).isEqualTo(expectedVerificationWork);
}
else {
    assertThat(valuesAdmittingFullVerificationAcceptanceAtRingZero).isEqualTo(59);
    assertThat(searchOutcome).isEqualTo(
        "A2/A3 ring-0-only veto is a search limit, not a defect. Test 7 (A1) carries Goal 4 on its own.");
}
```

The names in this assertion denote local measured values populated by the enumerator; the committed expected positions/counters come from the discovered evidence resource and must not be assigned from `actualRun` in the assertion. If there is a witness, select the smallest `y_b`, pin the entire rejection table, exact accepted coordinates, first ring, `verifications` and `verificationWork`, and compare subsequent runs against those constants. Also inspect Task 4's selected-family provenance to prove only the stage-0-selected `Conflict` is expanded, with no C1 regeneration or reselection. If there is no witness, assert all 59 ring-0 acceptances and the exact search-limit statement; an empty witness list alone is insufficient.

- [ ] **Step 2: Obtain red evidence for the new search contract.**

First run the test with its expected evidence resource absent and require the explicit missing-resource assertion to fail; preserve that output. Then implement the enumerator and review every row before writing the resource. Also mutate the subset classifier in a disposable copy (invert A2 containment or skip `y_b=51`) and require the exhaustive assertion to fail. This proves the search obligation independently of whether the approved finite family contains a later-ring witness.

```bash
gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.layout.EdgeConflictCorrectionShould.expandsTheA2RejectedFamilyWithoutReselectingIt'
```

- [ ] **Step 3: Pin the measured search outcome and the two source-backed veto controls.**

Serialize evidence deterministically as `y_b | ring | slot | target x,y | A1/A2/A3 keys | full outcome`, using hexadecimal coordinates and canonical key sorting. Add a final conditional-outcome line. Generate once into a temporary file, review it, then create the declared resource; subsequent tests only read and compare it. Do not alter the spec or manufacture a witness if none exists.

Keep `vetoesTheUnsafeDirectTargetThatAddsANodeGapAndAcceptsTheOtherSide`: `n=(0,5), b=(0,30)`, nearest `(0,11)` adds `nodePair(n,b)` at distance `19`, opposite `(0,-11)` accepted, rounds `1`, verifications `2`, work `30`. Add the exact-name sibling test over `a=(0,0), c=(-53,0), p=(5,100), q=(5,-100)`, sibling `A,C` under `R`, all radius `8`; `(-6,0)` introduces `hullPair(HA,HC)`, `(16,0)` is safe. Compare incremental and full hull recomputations and retain its measured legacy counters. These are ring-0 Goal-1 controls and must not be described as later-ring witnesses.

- [ ] **Step 4: Run green.**

```bash
gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.layout.EdgeConflictCorrectionShould'
```

Expected: all 59 cases and 1770 target observations are checked, the resource equals measured output, and both mandatory subset witnesses retain their original accepted outcomes. Carry the exact conditional result into the final acceptance records.

- [ ] **Step 5: Commit.**

```bash
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrectionShould.java freeplane_plugin_graph/src/test/resources/geometry/edge-node-crossing-residual-a2-a3-search.txt
git diff --cached --check
git commit -m "test(graph): resolve the A2 A3 ring search obligation [2026-10-26-edge-node-crossing-residual]"
```

## Task 6: Pin stage-1 allowance stops and complete-state publication

**Implementer tier:** Standard

**Lane:** relocation. **Category:** counted-budget and publication fixtures. **Depends on:** Tasks 4 and 5.

**Goal:** Exercise the unchanged stop branches through stage 1 and prove no partial detection or terminal rebuild is installed.

**Files:**

- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrectionShould.java:482-1387` and reflection helper region `:2648-2697`.
- Read only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrection.java`.

**Interfaces:**

- Consumes: unchanged `public EdgeConflictResult EdgeConflictCorrection.apply(GraphProjection, LayoutPositions, GeometryTextMetrics, List<PinProjection>, Set<ProjectedEndpointKey>, BoundarySeparationResult)`; `EdgeConflictResult.positions()`, `diagnostics()`, `boundaryDiagnostics()`, `nodeResidualViolations()` and the existing fixture `preExistingSiblingResidualFixture(double aX, double cX, double dX): A3SubsetFixture`.
- Consumes: existing `private static Set<ProjectedEndpointKey> seedVerificationsAtDetection(EdgeConflictCorrection correction, Fixture fixture, int verifications)`, which delegates to `private static Set<ProjectedEndpointKey> seedVerificationsAtDetection(EdgeConflictCorrection correction, GraphProjection projection, Set<ProjectedEndpointKey> visible, int verifications)`. Its `AbstractSet.contains(Object)` callback seeds query 1 after counter reset. Retain `seedCountersAtDetection(...)` for the separate verification-work controls below.
- Produces: `@Test public void stopsStage1AtTheRemainingVerificationAllowance()` and strengthened `publishesNeitherPartialRebuildRecordWhenTheTerminalScanStops`; no production API or logic changes.

- [ ] **Step 1: Add the stage-1 exhaustion witness exactly as spec §4.5.**

Use `fullyBlockedFixture()` from spec §4.4, not a smaller substitute. Reuse the existing detection-time seeding helper without adding another visibility seam:

```java
final Fixture fixture = fullyBlockedFixture();
final EdgeConflictCorrection correction = new EdgeConflictCorrection();
final EdgeConflictResult result = correction.apply(fixture.projection,
    fixture.boundary.positions(), METRICS, Collections.<PinProjection>emptyList(),
    seedVerificationsAtDetection(correction, fixture, 57), fixture.boundary);
```

The helper seeds the counter at query 1 so `resetFrameCounters()` does not erase it. Do not seed before `apply`. Six stage-0 rejections reach `63`; the first stage-1 rejected attempt reaches `64`. Assert:

```java
assertThat(result.diagnostics().verifications()).isEqualTo(64);
assertThat(result.diagnostics().verificationBudgetExhausted()).isTrue();
assertThat(result.diagnostics().detectionTruncated()).isFalse();
assertThat(result.diagnostics().rounds()).isZero();
assertThat(result.diagnostics().repairsApplied()).isEmpty();
assertThat(result.positions()).isEqualTo(fixture.boundary.positions());
```

Also assert exact complete one-key residual, unchanged anchors, no singleton retry after the stopping attempt and the original initial conflict classification. The sentinel count is intentional and is not reported as 64 actual new attempts.

- [ ] **Step 2: Strengthen partial-publication and numerical controls.**

For `publishesNeitherPartialRebuildRecordWhenTheTerminalScanStops`, use `preExistingSiblingResidualFixture(0.0,30.0,100.0)`, generation `10L`, the three exact sibling enclosures and `p-q` edge from spec §4.5. Seed work at detection 3 to `65_536`, `65_535`, `65_525`, then `65_524`. The first three runs retain the original boundary diagnostics object and hull residual despite accepted node movement; edge residual is complete and empty with one C2 resolved. The final control completes at total work `65_535` and publishes a new coupled boundary/node record. Do not replace identity assertions with equal residual counts.

Retain these exact tests and their existing checks:

- `stopsOnTheVerificationWorkCapWithoutADetectionStop`: seed work `65_536`, exactly one verification; no singleton retry.
- `rejectsTheSecondRoundCandidateWithoutLosingTheLastCompleteResidual` and `publishesTheLowerBoundResidualFromThePostRoundAndTerminalDetections`: partial candidate scans retain last complete state; explicit lower-bound diagnostic publication remains distinct.
- `boundsTheCountedRelocationWorkOnADenseWideSpanFrame`: `referenceScaleFan(500)`, all four item/pair/work/attempt bounds.
- `rejectsIneligibleC2TargetsReachedByHypotScalarAndTargetOverflow`, `usesTheCanonicalHorizontalNormalForEitherEndpointOrder`, zero-length/zero-movement controls and `resetsEveryCounterBetweenSeparateApplyCallsOnOneCorrectionInstance`.
- Extreme crossing with coordinates `±1e300`: one accepted round, two verifications, one crossing resolved, empty residual, index-budget flag true but verification-budget flag false.

Extend private-ring inspection to confirm zero/pinned/non-finite/over-excursion proposals cause no submitted verification, C1 is skipped and eight-family selection is unchanged. No generation-side A2/A3 check is allowed.

- [ ] **Step 3: Run red against isolated stop-branch mutants.**

The Task 4 runtime should already satisfy the new assertions. In disposable copies raise only `MAX_VERIFICATIONS_PER_FRAME` from `64` to `65` so an extra stage-1 rejection violates the exact-count assertion, permit singleton retry after work stop, or publish the hull rebuild before the node rebuild completes. Run only the corresponding named test, require its assertion to fail, and remove the copy. A compilation error is not a killing result for these mutations. Record exact mutation, failing assertion and parent passing command. Do not mutate the active worktree or rewrite loop branches in this fixture task.

- [ ] **Step 4: Complete the assertions and run green.**

```bash
gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.layout.EdgeConflictCorrectionShould'
gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.geometry.EdgeRouterShould'
gradle :freeplane_plugin_graph:test
```

Expected: all pass, including the authorized `30/2160` and `30/3300` controls and the unchanged successful-prefix counters. The new tests must not require production edits. If they expose a mismatch, report the exact violated clause and return to the Task 4 review/fix process within its file ownership; do not weaken the acceptance test.

- [ ] **Step 5: Commit.**

```bash
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrectionShould.java
git diff --cached --check
git commit -m "test(graph): pin expanded-search stop boundaries [2026-10-26-edge-node-crossing-residual]"
```

## Task 7: Measure frozen-fixture preservation and the two performance modes

**Implementer tier:** Standard

**Lane:** acceptance. **Category:** frozen evidence and performance. **Depends on:** Tasks 1–6; this is the penultimate task.

**Goal:** Produce measured evidence for unchanged frozen paths, first-idle pins, accepted-round counts and distinct settled/raw performance contracts.

**Files:**

- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/PerformanceTripwiresShould.java:73-223`.
- Create: `freeplane_plugin_graph/doc/edge-node-crossing-residual/frozen-acceptance.md`.
- Read only: `freeplane_plugin_graph/src/test/resources/performance/settled-reference.csv`, `SettledReferenceFixtureShould.java`, `GraphWorkspacePerformanceDiagnostic.java`, `layout/TypedForcesShould.java` and `layout/EdgeConflictCorrectionShould.java` under the module's test tree. The exact full test paths are `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/SettledReferenceFixtureShould.java`, `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GraphWorkspacePerformanceDiagnostic.java`, `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java`, and `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrectionShould.java`.

**Interfaces:**

- Consumes: Task 3's `preservesTheSettledReferenceAndNamedLadderFixtures()` and Task 4's unchanged accepted-prefix diagnostics; Task 5's expected search-evidence resource.
- Consumes: package-local `PerformanceMeasurements GraphWorkspacePerformanceDiagnostic.runSettledReferenceFixture()`, `void runRawReferenceFixture()`, `List<GraphWorkspacePerformanceDiagnostic.SettledReferenceSample> settledReferenceSamples()`, `List<GraphWorkspacePerformanceDiagnostic.RawReferenceFrame> rawReferenceFrames()` and `long rawReferenceMaxFrameNanos()`; `public static final long RAW_REFERENCE_MAX_FRAME_NANOS = 5_000_000_000L`; `public int PerformanceMeasurements.measuredCount(PerformanceMeasurements.Stage stage)` and `public int discardCount(PerformanceMeasurements.Stage stage)`; the existing `@BeforeClass public static void PerformanceTripwiresShould.runReferenceFixturesOnce() throws Exception` and its `referenceDiagnostic`/`referenceMeasurements` fields.
- Produces: `@Test public void preservesSettledAndRawPerformanceModesSeparately() throws Exception` and a committed acceptance record of actual commands, hashes, measured values and results. No threshold, calibration or ledger changes.

- [ ] **Step 1: Write the named mode-separation assertion.**

Reuse the existing `@BeforeClass runReferenceFixturesOnce()` and its `referenceDiagnostic`/`referenceMeasurements` fields: it already runs both benchmark modes once. The new assertion consumes those observations and calls the two existing assertion methods directly; their shared class setup is not rerun:

```java
@Test
public void preservesSettledAndRawPerformanceModesSeparately() throws Exception {
    assertThat(referenceDiagnostic.settledReferenceSamples()).hasSize(300);
    for (final GraphWorkspacePerformanceDiagnostic.SettledReferenceSample sample
            : referenceDiagnostic.settledReferenceSamples()) {
        assertThat(sample.routeDiagnostics().edgesStraight()).isEqualTo(21);
        assertThat(sample.routeDiagnostics().edgesBow()).isEqualTo(1);
        assertThat(sample.routeDiagnostics().edgesDogleg()).isZero();
        assertThat(sample.routeDiagnostics().edgesFallback()).isZero();
    }
    for (final PerformanceMeasurements.Stage stage : Arrays.asList(
            PerformanceMeasurements.Stage.EDGE_CONFLICT,
            PerformanceMeasurements.Stage.EDGE_ROUTING)) {
        assertThat(referenceMeasurements.measuredCount(stage)).isEqualTo(300);
        assertThat(referenceMeasurements.discardCount(stage)).isZero();
    }
    referenceFixtureStaysInsideTheStrictWorkerAndFirstFrameGates();
    referenceFixtureStaysBoundedAndFlagVisible();
}
```

These delegates retain the complete per-sample and per-frame checks: zero settled exhaustion/truncation, complete settled publication, pinned `21/1/0/0` route totals, unchanged strict p95/p99 limits, bounded raw counters/stop flags/keys, and `rawReferenceMaxFrameNanos() <= RAW_REFERENCE_MAX_FRAME_NANOS`. Do not substitute only a nonempty check, rerun the diagnostic methods from this test, or add another sampling pass to the ordinary suite.

Keep `exposeTheCanonicalStageOrderAndThresholds`, `referenceFixtureStaysInsideTheStrictWorkerAndFirstFrameGates` and `rejectsAFrameWhoseRoutingExceedsTheCompleteFrameBound` exactly named. The delayed-routing test must call the real `new EdgeRouter().route(...)` before advancing its injected clock beyond five seconds. Preserve the exact stage wire order:

```text
snapshot, projection, diff, mutation, force, correction, separation, hull,
edge-conflict, edge-routing, placement, full-worker, edt-swap, repaint,
accepted-batch-first-frame
```

- [ ] **Step 2: Demonstrate red for the acceptance contract in disposable copies.**

Run the unchanged ledger digest test against a copy with one coordinate byte altered; run the mode test with a deliberately exhausted raw frame substituted into a settled observation; run the delayed-routing test with its bound check bypassed. Each corresponding test must fail for the intended assertion, not an environment startup error. Do not edit the committed CSV, threshold constants or force policy in the active worktree. Existing first-idle and four-round tests must run unchanged; do not fabricate failures by changing their expected values.

- [ ] **Step 3: Run the frozen/performance command vector once after the assertions are ready.**

```bash
export JAVA_HOME=~/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"
sha256sum freeplane_plugin_graph/src/test/resources/performance/settled-reference.csv
gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.geometry.EdgeRouterShould'
gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.layout.EdgeConflictCorrectionShould'
gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.performance.SettledReferenceFixtureShould'
gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.layout.TypedForcesShould'
gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.performance.PerformanceTripwiresShould'
gradle :freeplane_plugin_graph:verifyGraphBundle
```

Expected: ledger digest exactly `e9e988b7967ea6bb4f503dfb4c5af7d3e74a3328e28e0987e71ff0f0b8ac0578`; generated fixture bytes unchanged under the existing compiler assertion; two-map first-idle exactly `3103` and initial-spacing acceptance within its independent `6000`-step limit; four rounds/four verifications/32 repairs without exhaustion; successful E1/E2/E3 counters unchanged. Existing performance samples retain their strict thresholds, 300 measured samples and zero discards; raw frames retain the five-second bound and bounded counted work. `verifyGraphBundle` checks Java 8 bytecode and OSGi packaging.

- [ ] **Step 4: Write the measured acceptance record and verify it.**

Record source commit, Java/Gradle versions, exact command exit statuses, immutable input hashes, actual first-idle step, actual four-round counters, settled sample count/discards, measured p95/p99 values and thresholds, raw maximum frame duration, exhausted-frame counts and flags, and route classifications/sample-pin equality. Include the Task 5 conditional outcome verbatim, plus **measured expansion cost** `30/2160` and `30/3300` with unchanged residuals. Cite JUnit XML and diagnostic output paths and their SHA-256s; distinguish observed values from limits. A passing command alone is not the requested measured record. Missing measurements or an environmental failure leaves acceptance pending, never passed.

If existing tests do not expose a required measured value, add a test-only diagnostic print inside this task's allowed test file and capture it from JUnit XML; remove noisy temporary prints after recording. Do not change performance instrumentation or production timing. Run the amended named test after its final edit. Check the record against the XML/CSV values and confirm the ledger digest again.

- [ ] **Step 5: Commit.**

```bash
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/PerformanceTripwiresShould.java freeplane_plugin_graph/doc/edge-node-crossing-residual/frozen-acceptance.md
git diff --cached --check
git commit -m "test(graph): record frozen residual-fix acceptance [2026-10-26-edge-node-crossing-residual]"
```

## Task 8: Replay the reported workspace and audit every published path

**Implementer tier:** Standard

**Lane:** acceptance. **Category:** reported-workspace replay. **Depends on:** Task 7; this is the final task.

**Goal:** Measure the real replay against spec §5.3 with full-precision repair/path checks and an independent same-geometry clearance audit, then finish with the module suite green.

**Files:**

- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/probe/RealWorkspaceCrossingProbe.java` (module-local test copy of the read-only probe, with only test-observation access added).
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/probe/RealWorkspaceCrossingShould.java`.
- Create: `freeplane_plugin_graph/doc/edge-node-crossing-residual/workspace-acceptance.md`.
- Read only: `doc/edge-node-crossing/probe/RealWorkspaceCrossingProbe.java`, `doc/edge-node-crossing/probe/RUN.md`, `doc/edge-node-crossing/probe/real-workspace-probe.txt`, and the three original map files. Do not update historical recorded probe output.

**Interfaces:**

- Consumes: `public EdgePaths EdgeRouter.route(GraphProjection projection, LayoutPositions positions, GraphGeometry geometry, Set<ProjectedEndpointKey> visibleEndpoints)`; `public EdgeConflictDiagnostics LayoutFrame.edgeConflictDiagnostics()`; `public LayoutPositions LayoutFrame.positions()`; `public boolean LayoutFrame.failed()`; `public int LayoutFrame.residualViolations()`; `public PerceptualIdlePolicy.IdleMeasurement LayoutFrame.idle()`. Correction already occurs within worker acceptance; retain the diagnostics from that accepted frame.
- Consumes: `public static void RealWorkspaceCrossingProbe.main(String[] args)` from the documented probe; default argument root `/home/henry/Documents/999.notebooks/01.math`; the original production snapshot/projection/worker construction remains authoritative for this replay.
- Produces: module-local test observation `static ReplayEvidence RealWorkspaceCrossingProbe.replay(java.nio.file.Path mathRoot) throws Exception` and `private static LayoutFrame settle(LayoutRequest request, ReplayEvidence evidence) throws Exception`. A nested test-only `static final class ReplayEvidence` holds `GraphProjection projection`, `LayoutFrame settledFrame`, `LayoutPositions settledPositions`, `GraphGeometry settledGeometry`, `EdgeConflictDiagnostics correction`, `GraphGeometry routedGeometry`, `EdgePaths paths`, `int steps`, `boolean idle`, `int residualViolations`, `List<String> clearanceViolations`, `int auditedEdges`, `int auditedDiscPairs`, `int[][] sourceCounts`, `int availableMaps`, `WorkspaceId workspaceId`, and `int relationshipCount`. Collect the three source-count rows during the existing map loop; collect the workspace/map metadata from the actual constructed document. Keep values at production precision; do not parse rounded console movements back into coordinates.
- Produces: `@Test public void replaysTheReportedWorkspaceWithoutClearanceViolations() throws Exception` and a measured workspace acceptance record. No public runtime APIs, map writes or application UI changes.

- [ ] **Step 1: Write the replay acceptance test before adding the module-local probe copy.**

Load the class reflectively so absence of the new harness yields `ClassNotFoundException` as red. Invoke `replay(Path)` and inspect the evidence fields reflectively, casting only to already-existing production types so the red test compiles before the new nested evidence type exists. Obtain the map root from the test environment `GRAPH_REPORTED_MAP_ROOT`, falling back to the documented root. The following three inputs must exist for the explicit acceptance run:

```text
00.foundation.of.math/02.set_theory/mindmap/Axiomatic_Set_Theory.mm
04.abstract/01.topology/01.general_topology/mindmap/Point-set_Topology.mm
01.analysis/03.real_analysis/mindmap/Real_Anaylsis.mm
```

The reusable ordinary suite can skip the external-data test when `GRAPH_REPORTED_MAP_ROOT` is absent, using JUnit `Assume`; the explicit acceptance command sets it and must fail on any missing file. A skipped test is never acceptance evidence. The replay reads the original `.mm` files and constructs the documented workspace; do not inject recorded settled coordinates into this live replay.

Assert source/projection metadata: three available maps with counts `7455/171/1445/10`, `9025/247/1229/11`, `9834/150/1474/7`; projected `28 nodes / 12 edges / 7 enclosures / 0 pins`; generation `1L`; workspace id `UUID.nameUUIDFromBytes("real-workspace-probe".getBytes(UTF_8))`; no relationships. All 28 nodes remain graph-group discs. Assert `steps=178`, `idle=true`, one original residual violation when the same hashed inputs are supplied.

Assert correction results exactly: one `TRANSLATION` of `ID_1732260180`, batch 0; `conflictsDetected=1`, `crossingsDetected=0`, `edgeOverNodeDetected=1`, `crossingsResolved=0`, `edgeOverNodeResolved=1`, empty residual, rounds/verifications/repairs all `1`, work `1207`, index items `300`, pair checks `84`, subdivisions `0`, all stop flags false. Treat `(1.177,-3.016)` and magnitude `3.238` as rounded display checks only. Preserve and record the actual `fromCentres`/`toCentres` values in hexadecimal form.

Route assertions are `12/0/0/0`, no stop/truncation, obstacle checks `8`, pair checks `0`, index items `2156`. Assert every spec §5.3 edge identity is present, not only a count. Audit every path against every non-attached disc of `routedGeometry`, using the minimum over its published sample segments; require `centreDistance - actualRadius >= 3-1e-9`. Zero violations means all 12 edges were checked; assert both the check count and the empty violation list.

- [ ] **Step 2: Run red using the explicit external-data mode.**

```bash
GRAPH_REPORTED_MAP_ROOT=/home/henry/Documents/999.notebooks/01.math gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.probe.RealWorkspaceCrossingShould'
```

Expected before the harness copy: missing module-local probe class. If the external files are absent, record their exact paths and leave this acceptance pending; do not fabricate a map, use a skipped test as green, or substitute the historical text file for live evidence.

- [ ] **Step 3: Add the test-only harness, retaining the documented replay.**

Copy the existing diagnostic source into the module's test package and factor its current `main` body into `replay(Path)`, collecting the typed observation. `main` delegates to the same replay so there is one active diagnostic computation. Retain its production `ProjectionEngine`, `LayoutRequest`, `LayoutWorker`, `LayoutCalibration.spikeDefaults()`, router call, text metrics (`Dialog`, plain 12, `new FontRenderContext(null, true, true)`) and `finally { worker.close(); }`. Add `ReplayEvidence evidence` to the existing private `settle` helper and assign `evidence.steps = steps` immediately after its existing loop. At the call site, retain the original accepted frame:

```java
final ReplayEvidence evidence = new ReplayEvidence();
final LayoutFrame settled = settle(request, evidence);
if (settled == null || settled.failed()) {
    throw new AssertionError("Reported workspace did not publish a successful frame");
}
evidence.projection = projection;
evidence.settledFrame = settled;
evidence.settledPositions = settled.positions();
evidence.correction = settled.edgeConflictDiagnostics();
evidence.idle = settled.idle().idle();
evidence.residualViolations = settled.residualViolations();
```

Set `settledGeometry`/`routedGeometry` from the probe's existing `computeHulls(projection, settled.positions(), metrics)` call, and `paths`/`clearanceViolations` from its existing route/audit. Inspect repairs through `evidence.correction.repairsApplied()`. Do not invoke correction a second time or invent an `EdgeConflictResult` for a worker frame. Capture the actual observations rather than inferring them from logs. Add no worker sleeps or force changes.

The independent path-distance audit already uses the obstacle centre and closed sample segments in `RealWorkspaceCrossingProbe`; preserve that algorithm and expose its results. Increment `auditedDiscPairs` inside the actual non-attached-disc distance loop, and `auditedEdges` only after completing an edge's audit; assert `auditedEdges=12` and a positive `auditedDiscPairs` count. Record every path's canonical key, shape, defining points, samples and minimum surface clearance. Do not use the separate router probe's origin-based dense-quadratic distance as an oracle.

To prove full-precision Goal-1/Goal-2 preservation for this workspace, capture the same typed observations from the pre-change implementation in a disposable worktree at the delivery base, using the same map hashes, Java runtime and probe. Compare actual repair maps and every routed defining/sample list exactly. The original rounded text is a reference for counters, not a replacement for this comparison. Keep the disposable baseline code out of the delivered runtime and remove the disposable worktree after capturing evidence.

- [ ] **Step 4: Run green, falsify the audit, and write measured evidence.**

Run the explicit replay test. In a disposable copy, replace one audited path with a chord through a non-attached disc and require the clearance assertion to fail; retaining a zero count after suppressing all audits must also fail the checked-edge count. Then run the unmutated replay again only if the harness changed. Record current input SHA-256s, baseline/current commits, full-precision positions and repair maps, all 12 path signatures, their measured minimum surface clearances, counters, stop flags and every command's exit status in `workspace-acceptance.md`.

The recorded pre-settle disc table and edge list in spec §5.3 are comparison values. If map hashes or measured input metadata differ, report the difference with actual results and leave exact recorded-input acceptance pending; do not re-pin expected values or claim the user's original arrangement was reproduced. The original investigation did not establish that the screenshot's exact arrangement is this fresh settle.

- [ ] **Step 5: Run the final implementation validation vector.**

All commands run from the implementation worktree. Reuse the preceding Task 7 evidence unless code or environment changed; do not rerun long performance sampling without a new reason. The full module suite is mandatory after the new test harness files are added.

```bash
export JAVA_HOME=~/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"
GRAPH_REPORTED_MAP_ROOT=/home/henry/Documents/999.notebooks/01.math gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.probe.RealWorkspaceCrossingShould'
gradle :freeplane_plugin_graph:test
gradle :freeplane_plugin_graph:verifyGraphBundle
sha256sum freeplane_plugin_graph/src/test/resources/performance/settled-reference.csv
git diff --check
```

Expected: explicit replay passes without skips; complete module suite and bundle check pass; frozen ledger hash unchanged. Keep evidence records within the three declared new files. No spec/design edits or new manifest/DOT files belong to this task.

- [ ] **Step 6: Commit and report the final measured acceptance.**

```bash
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/probe/RealWorkspaceCrossingProbe.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/probe/RealWorkspaceCrossingShould.java freeplane_plugin_graph/doc/edge-node-crossing-residual/workspace-acceptance.md
git diff --cached --check
git commit -m "test(graph): verify the reported workspace residual fix [2026-10-26-edge-node-crossing-residual]"
```

Report the final commit, passing command receipts, frozen evidence record, workspace record, Task 5's construct-or-declare result and any pending external-input acceptance. Do not call the implementation fully accepted if a required measured gate is missing.

### Required implementation validation command set

The exact union of the class/module gates in this plan is below; method filters in red/mutation steps additionally target the exact methods named in their tasks. This is a future implementation command set, not a statement that builds were run while authoring the plan.

```bash
export JAVA_HOME=~/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"
gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.geometry.EdgeRouterShould'
gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.layout.EdgeConflictCorrectionShould'
gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.performance.SettledReferenceFixtureShould'
gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.layout.TypedForcesShould'
gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.performance.PerformanceTripwiresShould'
GRAPH_REPORTED_MAP_ROOT=/home/henry/Documents/999.notebooks/01.math gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.probe.RealWorkspaceCrossingShould'
gradle :freeplane_plugin_graph:test
gradle :freeplane_plugin_graph:verifyGraphBundle
sha256sum freeplane_plugin_graph/src/test/resources/performance/settled-reference.csv
git diff --check
```
