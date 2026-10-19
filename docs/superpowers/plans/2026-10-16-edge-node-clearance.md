# Edge–Node Clearance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax; leave this digest-pinned plan unchanged during execution.

**Goal:** Move locally repairable, unpinned obstructing nodes so the approved E1–E3 cases publish straight edges with three world units of clearance, while preserving global invariants, bounded work, performance and installed-application evidence.

**Architecture:** Amend only the private candidate generation and bounded selection in `EdgeConflictCorrection`; keep its existing verifier, shared counters and correction → worker → settle loop → router → immutable canvas publication sequence. Add production-geometry regressions and an independent sampled-path oracle, then exercise the same real-worker replay through both a unit publication witness and the installed application's existing window. A test-only agent defines the replay helper in the installed control package; no startup hook or production API is added.

**Tech Stack:** Java 8 source/class target, Java 21.0.8-zulu execution, Gradle, JUnit 4, AssertJ, Swing/AWT, existing GraphStream 1.3 dependency and Knopflerfish OSGi installation; JDK-only evidence launcher/agent/helper runtime.

## Global Constraints

- Authority: approved design `docs/superpowers/specs/2026-10-16-edge-node-clearance-design.md`, SHA-256 `34f4b2e9f798673e633d284318ebf765e99fe9a2ceb6a2679238d1baea21bb56`; approved specification `docs/superpowers/specs/2026-10-16-edge-node-clearance-spec.md`, SHA-256 `a18d4a88c8bdbebc41c70d57f9702a08d0e3307dd9f636b49b4cda6c62f56f5e`. The approval is supplied by the task brief; do not rewrite the spec's historical draft-status text. Read the unchanged approved spec for exact geometry and evidence acceptance requirements. Task identifier for all commits: `2026-10-16-edge-node-clearance`.
- Work from the supplied integration worktree. Apply `AGENTS.md` and `/data/home/guest/Development/freeplane/AGENTS.md`. The user has approved this focused implementation through the design/specification; do not widen it to core modules or a publication refactor. No new production public API, core-module modification, runtime dependency, graph-bundle export, production factory or normal-startup change. Do not edit the approved design/specification or this pinned plan.
- Java target remains 8 (class major 52), UTF-8, four-space Java indentation; use JUnit 4/AssertJ and existing helpers. For builds set `JAVA_HOME=/data/home/henry-arch/.sdkman/candidates/java/21.0.8-zulu` and prepend `$JAVA_HOME/bin` to `PATH`; use `gradle`, never the wrapper or Maven, with the environment's normal escalation when necessary.
- For all execution commands set `EDGE_CLEARANCE_REPORTS=/data/home/henry-arch/.local/state/pi/project-manager/runs/0233ff88d3cc54bbd61550c61fa917d6e2a5717f8a03be4e9aa63dcd47dd1cfa/pm-run-20261016-045155-9d674096/reports`. Use its existing `performance-headless.init.gradle` for Gradle `Test` JVMs. Keep logs, source OID/working-diff SHA-256, actual cwd, Java version, exact command/exit status and JUnit XML in that external reports directory. Environmental failures and missing test selection are not behavioral red evidence.
- Retain `EdgeConflictCorrection.apply(GraphProjection, LayoutPositions, GeometryTextMetrics, List<PinProjection>, Set<ProjectedEndpointKey>, BoundarySeparationResult): EdgeConflictResult`. Initial detection uses the supplied `boundaryResult.geometry()` and its actual radii; never synthesize radius-eight initial geometry or recompute it before detection. The general existing fixture defaults to synthetic radius 14; new production fixtures must call `GraphGeometryEngine.computeHulls` and assert actual radii.
- Retain complete A1–A4 verification and its order: affected-hull recomputation charged through `BoundaryInvariants.changedHulls`; complete conflict identity strict subset; counted current/candidate node-gap identity subset; counted current/candidate hull-pair identity subset including ancestor escape; existing enclosure-path condition. No candidate eligibility pre-scan may bypass the counted scans. Pins with `active()==true` exclude every move of that node; dormant pins do not. Node gap stays `r_i + r_j + NodeSeparationProjection.MIN_GAP`, with `MIN_GAP = 6.0`. Anchors, enclosure membership, visibility, identities and prominence stay unchanged.
- Preserve C1's canonical corner cycle, two adjacent swaps then four mirrors, enclosure-path eligibility and pin filtering. C2 remains `RepairKind.TRANSLATION`. Remove `EDGE_CONFLICT_MAX_REPAIRABLE_CONFLICTS_PER_FRAME` and the greater-than-eight suppression branch and its obsolete test/interpretation completely; no compatibility path. Replace `addEdgeOverNodeCandidate` with the finite family, rather than keeping both algorithms.
- Exact new package-private pins: `MAX_CANDIDATE_FAMILIES_PER_ROUND = 8`, `MAX_CANDIDATES_PER_BATCH = 8`, `MAX_EDGE_NODE_EXCURSION = 64.0`. `EDGE_NODE_CLEARANCE = 3.0`. Each family has at most six candidates; at most eight nonempty distinct-conflict families and 48 unique entries per current-state pool. Normalize signed zeros before comparisons/identity/point creation. Use actual finite positive radius, finite arithmetic and no invented direction or fallback radius. Reject zero or over-64 movement; no clamp. Four accepted C2 rounds imply at most 256 world units of cumulative path length per node; no new C1 displacement restriction.
- C2 basis/targets: `d=b-a; L=hypot(dx,dy); n=minLex((-dy/L,dx/L), its negation); u=(-n.y,n.x); s=(p-a)·n; rho=r+3; h=2*r+9; q=p+(sigma*rho-s)*n+t*u`, for `sigma=+1,-1` and `t=0,-h,+h`. Sort by `(stage, movement, sideRank, alongRank, candidateIdentity)` with both direct targets before every diagonal and exact `Double.compare`. Candidate identity is kind plus canonical sorted moved-node keys and normalized from/to `Double.doubleToLongBits`; compare kind name, node strings and each bit field using `Long.compareUnsigned`, then sequence length. Exclude generating conflict key from identity.
- Visit complete conflicts in ascending canonical identity order; keep C1 order and sorted C2 order; deduplicate against earlier retained entries; skip empty families; select the first eight nonempty families, then stop generation. A complete direct-enumeration fallback is eligible; incomplete initial detection is not. Do not flag bounded selection as truncation/exhaustion or claim global fairness.
- Retry schedule: first-fit disjoint batch of at most eight; remove only selected entries; verify the batch, then its candidates singly in that order only when batch size >1. Skipped conflicting alternatives stay in the pool. Ordinary rejection consumes only the attempted entry, then traversal advances; acceptance installs positions/geometry/complete detection atomically, records one repair per candidate with the same zero-based batch index, discards old pool/attempts and regenerates next round. No bisection, singleton duplication, counter reset, pool regeneration after all ordinary rejections or extra repairability scan.
- Exact retained correction caps per `apply`: index `262_144`, pair `131_072`, detected-key `4_096`, verification work `65_536`, actual verifications `64`, accepted rounds `4`. Router caps remain index/obstacle/pair `262_144 / 131_072 / 131_072`. Counters reset only once on entry, not per attempt/round, including instance reuse; initial/candidate/post-round/terminal detections and terminal rebuild share allowances and existing units. Preserve post-round and terminal scans.
- Preserve the spec §5.2 stop table: initial/published-position detection truncation publishes that detection's lower-bound keys and skips terminal rebuilding; candidate detection/work STOP rejects it and retains the last complete accepted residual with `verificationBudgetExhausted=true` and published `detectionTruncated=false`; no retry follows STOP. Incomplete terminal boundary/node rebuild publishes neither partial rebuilt record and keeps boundary-stage diagnostics/node residual. `indexBudgetExhausted` retains direct-enumeration meaning. Attempt 64 that clears all conflicts alone and round four alone do not imply exhaustion; attempt-cap residuals do. Ordinary vetoes/family limits imply no exhaustion.
- Routing/publication stay observable: unresolved paths remain present and classified, including fallback intersections; do not erase correction residuals because routing bows around them. E1–E3 and prominence require exact expected edge coverage, finite geometry/samples, successful `Shape.STRAIGHT` two-sample rim attachments, no fallback and exhaustive segment/disc clearance `distance - actualRadius >= 3.0 - 1e-9`, excluding only the edge's two attached node endpoints. Use an independent clamped closed-segment Euclidean calculation, including zero-length segments, rather than the production conflict predicate alone.
- Common acceptance fixture identities: map `00000000-0000-0000-0000-000000000001`, generation `1`, persisted IDs and labels as specified, native connector per listed edge with both arrows off unless prominence overrides it; no pins/enclosures and empty anchors unless explicitly overridden. Metrics: Dialog/plain/12, antialiased fractional `FontRenderContext`. Validate initial conflicts/gaps/hull pairs so progress/clearance assertions cannot pass on an unobstructed fixture.
- Performance fixture `freeplane_plugin_graph/src/test/resources/performance/settled-reference.csv` stays byte-identical, SHA-256 `e9e988b7967ea6bb4f503dfb4c5af7d3e74a3328e28e0987e71ff0f0b8ac0578`; its loader/compiler inputs and 25-node/22-edge pinned workload stay unchanged (one initial crossing; one accepted bow; 21 straights; no dogleg/fallback/routed residual). The existing `conflictsDetected <= 8` assertion remains a fixture property, never repair eligibility.
- Keep 400 warmups / 300 measurements; conflict p95 ≤25 ms, routing p95 ≤10 ms, worker p95/p99 ≤100 ms, accepted-first-frame p95 ≤150 ms and p99 ≤300 ms; every measured sample retains zero-exhaustion/no-direct-fallback and zero-discard requirements. A7b is **5_000_000_000 ns**, inclusive worker submission + geometry + routing, for each of eight raw reference frames; older three-second prose is superseded. Preserve the `RAW_REFERENCE_MAX_FRAME_NANOS + 1` rejection and every counted cap; never relax a threshold to obtain green results.
- Installed E3 evidence is mandatory after normal checks: only three harness sources, `smoke/EdgeNodeClearanceApplicationEvidence.java`, `smoke/EdgeNodeClearanceEvidenceAgent.java`, `control/EdgeNodeClearanceReplay.java`, under this module's `src/test/java/org/freeplane/plugin/graph/`, plus test-only Gradle tasks. Only agent/helper and nested class bytes enter the agent Jar; no production classes/frameworks/resources/shadows, no agent in `BIN`/bundle/distribution. The JavaExec outer launcher may use the test runtime classpath; the child app and agent never resolve production/helper references from unpackaged test/main class directories.
- Evidence uses Java 21 and actual `BIN/freeplane.sh`, installed bundles, existing showing `GraphWorkspaceWindow` named `graph-workspace-window`, existing model/canvas/painter/presentation and real installed worker/loop/router. No mock/controller/window, pre-corrected frame, manual node move, standalone `graphUiEvidence` image, bytecode transformation or final-field replacement. `run(Object window, Path attemptDirectory)` is a test-helper entry point, not a production public API.
- Evidence gets a fresh external attempt/profile; exact E3 raw centers and stable identities, nine leaf `<graph_group version="1"/>` markers and one A–B workspace relationship, no pins/enclosures. Seed `graph_workspace_recent_workspaces` with the absolute `.fpg` path; set `ui_elements_follow_selected_map=false`, `selected_map_follows_active_window=false`, `create_new_map_if_no_maps_are_loaded=false`, `always_load_last_maps=false`, `load_last_maps=false`, `load_last_map=false`. Child-only `JAVA_OPTS`: `-Dorg.freeplane.nosplash=true`, `-Dorg.freeplane.main.application.FreeplaneSecurityManager.disable=true`, `-javaagent:<absolute-jar>=<absolute-attempt-directory>`. Strip inherited headless options, preserve controlled `DISPLAY`, isolate `HOME`/`XDG_CONFIG_HOME` via the child environment, use `BIN` cwd, and do not use `-XQuitAction` or change the user's shell HOME/profile/display.
- Agent starts a coordinator and returns from premain immediately. Find already-loaded Controller through `Instrumentation.getAllLoadedClasses`, poll action readiness without repeated action invocation, require effective disable-property `true` and absent SecurityManager at action readiness and again after window discovery immediately before helper definition. Invoke `OpenGraphWorkspaceAction` exactly once on the EDT. Define helper plus nested dependencies with reflectively invoked Java 9+ `privateLookupIn`/`defineClass` in the installed control package/loader; fail wrong loaders/access/classes rather than switching execution paths.
- Evidence unregisters original canvas registration on EDT and fences pre-existing deliveries; it pauses replay loop synchronously in its first real listener callback, forwards that exact state to real model `acceptCanvasState`, and captures callback failure in an exceptional future observed off EDT. The retained model and canvas state references, generation, sorted exact-bit digest, screen bounds and viewport must agree before/after a real Robot window screenshot. Fit/settle/paint actual UI, verify complete drawing visibility, independently recheck clearance, inspect PNG, and record installed window/control/worker/router bundle/code-source identities.
- Evidence deadlines from child launch: overall 180 seconds excluding Gradle build; readiness ≤60 s; window discovery ≤30 s; first publication ≤5 s; all other waits bounded by remaining deadline; reserve separate 10 s cleanup. Use posted timed EDT requests, bounded Robot thread and cleanup executor; never block EDT on futures/worker/Robot. Off EDT, bounded cleanup waits for actual loop `close()` return, closes real window on EDT and invokes unwrapped real `DefaultGraphWorkspaceController.shutdown()` off EDT. Require disposal, preserve task failures, track unique attempt marker and owned PIDs/descendants; only those processes may be terminated. Any timeout, async exception, stale/missing/malformed/hash-mismatched artifact, wrong loader, lost state or surviving owned child fails.
- Evidence artifacts per fresh attempt: `app.log`, raw `.mm`/`.fpg`, `e3-window.png`, `e3-state.json`, `result.json`, plus provenance/control files. State includes all spec §8.4.5 fields and E3 exact `rounds=1`, `verifications=3`, `verificationWork=252`, no fallback/exhaustion and minimum clearance ≥`3.0-1e-9`. Stage result and hashes atomically; agent may exit zero only after helper cleanup/result staging; outer launcher finalizes PASS atomically only after validation and actual clean child exit. Do not reuse an earlier PASS.
- Execution records are external evidence, not new product APIs or versioned fixtures. Supplied baseline results in spec §8.1 are historical, not new runs. No claim about the user's original screenshot map is authorized by E1–E3 evidence. Commit only each task's reviewed, passing tracked files with the task identifier; do not commit external logs, generated installations, profiles or screenshots.

### File responsibility map and execution lanes

| Repository-relative file | Responsibility / owning tasks |
| --- | --- |
| `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrection.java` | Private C2 family and bounded pool selection; Tasks 1–2 only |
| `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrectionShould.java` | Production-geometry fixtures, independent path oracle and correction/control/accounting tests; Tasks 1–4 |
| `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundaryInvariantsShould.java` | Explicit ancestor escape identity/type; Task 3 |
| `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrectionShould.java` | Preserve/extend explicit ancestor escape control; Task 3 |
| `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/LayoutSettleLoopShould.java` | First real-worker publication plus helper deadline/digest/failure tests; Tasks 5–6 |
| `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/EdgeNodeClearanceReplay.java` | JDK-only typed E3 replay/worker adapter/oracle, then installed-window binding/capture/cleanup; Tasks 5–6 |
| `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/smoke/EdgeNodeClearanceEvidenceAgent.java` | JDK-only action/bootstrap/installed-loader/helper transport; Task 7 |
| `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/smoke/EdgeNodeClearanceApplicationEvidence.java` | JDK-only fresh profile/fixtures/process/provenance/artifact validation; Task 7 |
| `freeplane_plugin_graph/build.gradle` | Two test-only evidence tasks and restricted agent Jar; Task 7 |

All tasks use lane `clearance` sequentially: 1 → 2 → 3 → 4 → 5 → 6 → 7. This avoids concurrent edits to the same correction/test/helper files. Categories appear per task. Read-only dependencies such as `EdgeRouterShould`, `LayoutWorkerShould`, `GraphWorkspacePerformanceDiagnostic`, `PerformanceTripwiresShould`, `GraphCanvas`, `GraphWorkspaceWindow`, `FreeplaneLaunchSmoke`, and existing smoke fixtures are not edit permissions. Line ranges below refer to the pre-implementation checkout and are navigation anchors; use named methods after earlier tasks shift lines.

### Requirement-to-task self-review map

| Approved specification requirement | Implementing task / falsifiable check |
| --- | --- |
| §§1–3 C2 basis, actual radius, finite bounds/order and obsolete-code removal | Task 1 E2/E3, nearest target, existing expectation reconciliation; Task 3 numerical/prominence tests |
| §4 first eight nonempty families, ≤48 identities, ≤8 disjoint batch, dedup and retry | Task 2 E1, six-attempt blocked/dedup controls; Task 3 quad/mixed ordering |
| §§2,5.3 immutable acceptance, pins, A1–A4, boundary/node diagnostics | Tasks 1–4; Task 3 sibling/equal-count/ancestor controls |
| §§5.1–5.2 complete versus partial residual, counted stops/reset and unchanged scans | Task 4 257-node and retained exhaustion probes, plus Task 2 E1 shared `1_755` |
| §§6.1–6.4 exact fixtures and exhaustive same-geometry routed path oracle | Tasks 1–3; Task 5 real-worker first CanvasState witness |
| §7.1 all required controls and §7.2 affected legacy assertions | Tasks 1–4, with matrices and concrete method names below |
| §8.2 module, performance, OSGi and normal launch verification | Tasks 4 and 7, final complete command vector in Task 7 |
| §8.3 unchanged workload/timings, five-second A7b | Task 4 byte pin and test verification; Task 7 strict diagnostic/final gate |
| §8.4.1 three test sources, two Gradle tasks, actual launch | Tasks 5–7, isolated installed evidence in Task 7 |
| §8.4.2 readiness/security checks and installed class definition | Task 7 agent bootstrap, Jar/loader/negative checks |
| §8.4.3 raw real-worker replay, real model binding and first-frame pause | Task 5 unit witness; Task 6 installed-helper flow; Task 7 installed gate |
| §8.4.4 real painting/Robot capture/reference + digest/view stability | Task 6 snapshots/capture, Task 7 visual inspection and bound artifact validation |
| §8.4.5 schema/provenance/deadlines/cleanup/atomic PASS | Tasks 6–7 timed futures, error transport, owned-process cleanup and result validation |

## Task 1: Replace the single C2 translation with the finite ordered local family

**Implementer tier:** Advanced

**Lane:** `clearance`
**Category:** production-geometry-and-regression
**Depends on:** none

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrection.java:34-72,362-378,501-553,1308-1330` — constants, C2 generation/ranking and private candidate metadata; leave suppression/selection for Task 2.
- Modify/Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrectionShould.java:120-188,652-831,874-1104` — new production fixture/oracle, E2/E3, actual-radius expectations affected by expanded search.
- Read only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/EdgeRouter.java`, `GraphGeometryEngine.java`, `NodeGeometry.java` in that same geometry package.

**Interfaces:**

- Consumes existing `apply(...)` above; `new GraphGeometryEngine().computeHulls(GraphProjection, LayoutPositions, GeometryTextMetrics): GraphGeometry`; `new EdgeRouter().route(GraphProjection, LayoutPositions, GraphGeometry, Set<ProjectedEndpointKey>): EdgePaths`; `EdgePaths.byEdge(): Map<ProjectedEdgeKey,EdgePath>`; `EdgePath.shape(): EdgePath.Shape`, `samples(): List<LayoutPoint>`; existing private `Fixture`, `apply(Fixture): EdgeConflictResult` and native-connector construction in this test class.
- Produces private static `addEdgeOverNodeCandidates(List<Candidate>, Conflict, LayoutPositions, Set<ProjectedNodeKey>): void`, replacing the singular method; private `CandidateIdentity implements Comparable<CandidateIdentity>` with constructor `CandidateIdentity(Candidate)`, value equality/hash; private `RankedCandidate(Candidate candidate, int stage, double movement, int sideRank, int alongRank, CandidateIdentity identity)` with static `Comparator<RankedCandidate> ORDER` and its candidate field. C1 `Candidate` observable fields/kinds are unchanged.
- Produces package-private `static final double MAX_EDGE_NODE_EXCURSION = 64.0`.
- Produces test-private `clearanceFixture(String[] ids, double[] xy, int[][] edges): Fixture`, `oppositeSideFixture(): Fixture`, `blockedColumnFixture(): Fixture`, `routed(Fixture, EdgeConflictResult): EdgePaths`, `assertClearPaths(GraphProjection, GraphGeometry, EdgePaths, Set<ProjectedEdgeKey>): void`, `assertSuccessfulStraight(Fixture, EdgeConflictResult, EdgePaths, int): void`. Build final geometry from result positions, not the fixture's initial geometry. No test helper leaks into main sources.

- [ ] **Step 1: Add real-geometry E2/E3 tests and their independent path oracle.**

`clearanceFixture` must duplicate the existing native-contributor construction but replace its geometry scaffold with `GraphGeometryEngine.computeHulls`; use map UUID from Global Constraints, generation 1 and truthful empty boundary diagnostics/node residual. Do not call the existing synthetic-radius helper and merely assume it is radius eight. Construct the fixtures exactly:

```java
private static Fixture oppositeSideFixture() {
    return clearanceFixture(new String[] {"A", "B", "N", "O"},
        new double[] {-100, 0, 100, 0, 0, 5, 0, 30}, new int[][] {{0, 1}});
}

private static Fixture blockedColumnFixture() {
    return clearanceFixture(new String[] {"A", "B", "N", "O1", "O2", "O3", "O4", "O5", "O6"},
        new double[] {-100, 0, 100, 0, 0, 0, 0, -22, 0, -44, 0, -66,
            0, 22, 0, 44, 0, 66}, new int[][] {{0, 1}});
}

@Test
public void repairsOnTheOppositeSideWhenTheNearestSideBreaksTheGap() {
    final Fixture fixture = oppositeSideFixture();
    final EdgeConflictResult result = apply(fixture);
    assertThat(result.positions().nodes().get(fixture.key(2))).isEqualTo(LayoutPoint.of(0, -11));
    assertThat(result.diagnostics().rounds()).isEqualTo(1);
    assertThat(result.diagnostics().verifications()).isEqualTo(2);
    assertThat(result.diagnostics().verificationWork()).isEqualTo(30);
    assertSuccessfulStraight(fixture, result, routed(fixture, result), 1);
}

@Test
public void repairsTheBlockedColumnWithTheFirstFeasibleDiagonal() {
    final Fixture fixture = blockedColumnFixture();
    final EdgeConflictResult result = apply(fixture);
    assertThat(result.positions().nodes().get(fixture.key(2))).isEqualTo(LayoutPoint.of(-25, -11));
    assertThat(result.diagnostics().rounds()).isEqualTo(1);
    assertThat(result.diagnostics().verifications()).isEqualTo(3);
    assertThat(result.diagnostics().verificationWork()).isEqualTo(252);
    assertSuccessfulStraight(fixture, result, routed(fixture, result), 1);
}

private static double segmentDistance(final LayoutPoint p, final LayoutPoint a, final LayoutPoint b) {
    final double dx = b.x() - a.x();
    final double dy = b.y() - a.y();
    final double lengthSquared = dx * dx + dy * dy;
    final double t = lengthSquared == 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0,
        ((p.x() - a.x()) * dx + (p.y() - a.y()) * dy) / lengthSquared));
    return Math.hypot(p.x() - (a.x() + t * dx), p.y() - (a.y() + t * dy));
}
```

`assertClearPaths` first compares exact expected key set to `paths.byEdge().keySet()`, checks every state position/sample finite and ≥2 samples, then loops every consecutive sample segment against every geometry node except that edge's two attached node keys. Call `segmentDistance` and assert `distance - disc.radius() >= 3.0 - 1e-9`; include fallback shapes. `assertSuccessfulStraight` additionally asserts radii 8, initial exact sole C2 and no gaps/hull pairs, one translation repair with exact from/to and batch 0, C2 detected/resolved 1, zero crossings, empty residual, untouched other nodes/anchors, no new gap/hull identities, every correction/router flag false and counters in Global Constraints, exact route coverage/counts (`straight=1`, other counts 0), two rim samples `(-92,0),(92,0)` and length 184. These requirements are part of these tests, not optional helper behavior. Add unit checks of `segmentDistance` for an interior perpendicular projection, projection outside both ends, and a zero-length segment.

- [ ] **Step 2: Observe the behavioral red phase.**

```bash
gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.layout.EdgeConflictCorrectionShould.repairsOnTheOppositeSideWhenTheNearestSideBreaksTheGap' --tests 'org.freeplane.plugin.graph.layout.EdgeConflictCorrectionShould.repairsTheBlockedColumnWithTheFirstFeasibleDiagonal' -I "$EDGE_CLEARANCE_REPORTS/performance-headless.init.gradle" -PTestLoggingFull --console=plain --rerun-tasks
```

Expected on unchanged production: both movement/straight-result assertions fail because the original preferred move is vetoed; E3 remains blocked. Save exact output/XML externally before editing production. A compile/missing-method/environment failure is not this red phase.

- [ ] **Step 3: Implement numerical eligibility, ranked six-target construction and identity.**

Replace the singular call in `generateCandidates` with the plural method. Keep its unbounded family loop until Task 2. Normalize zeros via `value == 0.0 ? 0.0 : value`. Normalize both normal vectors before lexicographic `Double.compare`; a horizontal chord always chooses `(0,-1)` and `u=(1,0)`. Explicitly reject missing center, nonpositive/nonfinite actual disc radius, nonfinite endpoints/subtractions/basis/scalars, and `L<=0`. Check finite `p-a` components, `s`, `rho`, `h`, normal shift, both products/sums for target coordinates and final displacement before constructing a point. The generation core is:

```java
final ProjectedNodeKey node = conflict.disc().key;
final List<RankedCandidate> ranked = new ArrayList<RankedCandidate>(6);
for (int side = 0; side < 2; side++) {
    final double sigma = side == 0 ? 1.0 : -1.0;
    for (int along = 0; along < 3; along++) {
        final double t = along == 0 ? 0.0 : along == 1 ? -h : h;
        final double normalShift = sigma * rho - s;
        final double qx = p.x() + normalShift * nx + t * ux;
        final double qy = p.y() + normalShift * ny + t * uy;
        final double movement = Math.hypot(qx - p.x(), qy - p.y());
        if (!Double.isFinite(normalShift) || !Double.isFinite(qx) || !Double.isFinite(qy)
                || !Double.isFinite(movement) || movement == 0.0
                || movement > MAX_EDGE_NODE_EXCURSION) {
            continue;
        }
        final LayoutPoint target = LayoutPoint.of(qx == 0.0 ? 0.0 : qx, qy == 0.0 ? 0.0 : qy);
        final Candidate candidate = new Candidate(EdgeConflictDiagnostics.RepairKind.TRANSLATION,
            Collections.singletonList(node), Collections.singletonMap(node, p),
            Collections.singletonMap(node, target));
        ranked.add(new RankedCandidate(candidate, along == 0 ? 0 : 1, movement,
            side, along == 2 ? 1 : 0, new CandidateIdentity(candidate)));
    }
}
Collections.sort(ranked, RankedCandidate.ORDER);
for (final RankedCandidate value : ranked) {
    candidates.add(value.candidate);
}
```

Here `p`, `nx`, `ny`, `ux`, `uy`, `s`, `rho` and `h` are the validated current center/basis/scalars described above. Define the immutable ranked wrapper with the stated constructor and comparator fields; `RankedCandidate.ORDER` compares stage, movement, sideRank, alongRank and identity field by field. Both direct targets precede diagonals even if a diagonal is shorter. `CandidateIdentity` sorts keys by `CanonicalKeys.node`, stores zero-normalized from/to bits, ignores generating conflict, and implements equality/hash with exactly the same fields as ordering. Compare each bit using `Long.compareUnsigned`, not signed longs or decimal strings. Sort C2 wrappers and append candidates in that order; no epsilon or uncounted collision scan. Do not modify C1 mirror calculations, `verify`, detection or counter scopes.

- [ ] **Step 4: Reconcile existing single-target expectations and verify.**

Rename normal-only descriptions to nearest-direct behavior. Update `vetoesACandidateThatWouldBreakMinGap`, sibling-hull and equal-count replacement assertions so an unsafe preferred move remains explicitly proven unsafe and never accepted, while a legal opposite/diagonal may now succeed. For the equal-count fixture `(aX,cX,dX)=(0,45,-53)`, production radius eight yields forbidden preferred a `(-6,0)` replacing A–C with A–D; require accepted a `(16,0)` retaining A–C. For the original synthetic-radius-14 mixed fixture, calculate from its actual initial disc: N beside x=1000 moves from x=1005 to x=1017, not x=983; retain its SWAP/TRANSLATION assertions. Preserve poisoned-boundary-geometry tests and pre-existing-same-pair acceptance. Task 3 adds the full independent A3 controls; do not defer getting the current correction class green.

```bash
gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.layout.EdgeConflictCorrectionShould' --tests 'org.freeplane.plugin.graph.geometry.EdgeRouterShould' -I "$EDGE_CLEARANCE_REPORTS/performance-headless.init.gradle" -PTestLoggingFull --console=plain --rerun-tasks
```

Expected: PASS without failure/error/skip; the old suppression test still passes until Task 2. Inspect diff for no initial-geometry rebuild, fallback radius, public production member, verifier change or compatibility C2 path.

- [ ] **Step 5: Commit.**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrection.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrectionShould.java
git commit -m "Fix finite C2 clearance alternatives [2026-10-16-edge-node-clearance]"
```

## Task 2: Bound family selection and consume rejected alternatives without suppressing crowded frames

**Implementer tier:** Advanced

**Lane:** `clearance`
**Category:** bounded-selection-and-regression
**Depends on:** Task 1

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrection.java:34-54,112-265,362-378,554-575` — suppression removal, ordered family pool and bounded disjoint batching, using Task 1 identities.
- Modify/Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrectionShould.java:189-222,874-1104` — replace suppression test and add E1, blocked and duplicate-family controls plus construction helpers.

**Interfaces:**

- Consumes Task 1 private `CandidateIdentity implements Comparable<CandidateIdentity>` and `addEdgeOverNodeCandidates(List<Candidate>, Conflict, LayoutPositions, Set<ProjectedNodeKey>): void`; existing `addCrossingCandidates(List<Candidate>, Conflict, LayoutPositions, Set<ProjectedNodeKey>, Map<ProjectedNodeKey,List<EnclosureHullKey>>): void`, `greedyDisjoint(List<Candidate>): List<Candidate>` and `verify(...): Evaluation` with ACCEPT/REJECT/DETECTION_STOP.
- Consumes test-private `clearanceFixture(String[], double[], int[][]): Fixture`, `apply(Fixture): EdgeConflictResult`, `routed(Fixture, EdgeConflictResult): EdgePaths`, `assertClearPaths(GraphProjection, GraphGeometry, EdgePaths, Set<ProjectedEdgeKey>): void` from Task 1. The `Fixture` retains projection, boundary, visible and canonical node keys.
- Produces package-private `static final int MAX_CANDIDATE_FAMILIES_PER_ROUND=8`, `MAX_CANDIDATES_PER_BATCH=8`; replaces the private `generateCandidates` body with a deduplicated bounded family traversal. No signature/public diagnostic changes.
- Produces private `nineObstructionsFixture(): Fixture`, `fullyBlockedFixture(): Fixture`, `duplicateChordFixture(): Fixture` and tests named below.

- [ ] **Step 1: Add E1 and fully blocked/deduplicated observable-work tests.**

E1 uses arrays for i=0..8: IDs `Ai,Bi,Ni`, centers `(-100,150*i),(100,150*i),(0,150*i+5)`, edges `(3*i,3*i+1)`. Assert the complete initial set is exactly the nine C2 `CanonicalKeys.edgeNodePair` keys and no C1/gap/hull identity. Production radii must all be eight. The core assertions are:

```java
@Test
public void repairsNineIndependentObstructionsInBoundedBatches() {
    final Fixture fixture = nineObstructionsFixture();
    final EdgeConflictResult result = apply(fixture);
    for (int i = 0; i < 9; i++) {
        assertThat(result.positions().nodes().get(fixture.key(3 * i + 2)))
            .isEqualTo(LayoutPoint.of(0, 150 * i + 11));
    }
    assertThat(result.diagnostics().rounds()).isEqualTo(2);
    assertThat(result.diagnostics().verifications()).isEqualTo(2);
    assertThat(result.diagnostics().verificationWork()).isEqualTo(1_755);
    assertThat(result.diagnostics().repairsApplied()).hasSize(9);
    assertThat(result.diagnostics().conflictsResidual()).isEmpty();
    final EdgePaths paths = routed(fixture, result);
    assertThat(paths.diagnostics().edgesStraight()).isEqualTo(9);
    assertThat(paths.diagnostics().edgesFallback()).isZero();
}
```

Extend this body with all Task 1 oracle/success checks, exact C2 detected/resolved 9, crossing zero, nine translation maps and batch indices 0 for canonical indices 0–7 and 1 for index 8, immutable endpoints/anchors and each straight's rim samples `(-92,150*i),(92,150*i)`. `1_755=5*C(27,2)` proves shared frame accounting, not a per-round counter.

`keepsAResidualWhenEveryLocalAlternativeBreaksTheGap` uses IDs A/B/N/U/D/UL/UR/DL/DR, centers `(-100,0),(100,0),(0,0),(0,22),(0,-22),(-25,25),(25,25),(-25,-25),(25,-25)`, edge A–B. Prove initially zero gaps and one C2. Require exactly six verifications, zero rounds/repairs/resolved, work 432, unchanged positions/anchors, exact residual, all correction budget flags false. Route and record exact actual classification and minimum segment/disc clearance while requiring edge coverage; do not demand impossible repair clearance.

`deduplicatesIdenticalAlternativesFromOverlappingChords` replaces endpoints with A `(-120,0)`, B `(120,0)`, C `(-80,0)`, E `(80,0)`, keeps the same seven unpinned blockers and adds chords A–B/C–E. Pin A/B/C/E actively. Assert zero initial gaps, exact four C2 identities (A–B with C/E/N; C–E with N), six verifications, work 660, zero movement/rounds/repairs/resolution and no budget flags. Twelve attempts fails the cross-conflict dedup contract.

- [ ] **Step 2: Run the selected red tests before selection changes.**

```bash
gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.layout.EdgeConflictCorrectionShould.repairsNineIndependentObstructionsInBoundedBatches' --tests 'org.freeplane.plugin.graph.layout.EdgeConflictCorrectionShould.deduplicatesIdenticalAlternativesFromOverlappingChords' --tests 'org.freeplane.plugin.graph.layout.EdgeConflictCorrectionShould.keepsAResidualWhenEveryLocalAlternativeBreaksTheGap' -I "$EDGE_CLEARANCE_REPORTS/performance-headless.init.gradle" -PTestLoggingFull --console=plain --rerun-tasks
```

E1 must fail because suppression is still present; duplicate-chord must expose duplicate attempts. The fully blocked test can already pass after Task 1 and serves as the pool traversal baseline, not a claimed red result.

- [ ] **Step 3: Replace suppression with bounded family selection and preserve the exact retry state machine.**

Delete the old suppression constant/early return and replace its test. Build one family at a time from complete conflict-key order. Use Task 1 C2 ordering and unchanged C1 generation order. Each candidate surviving cross-family identity dedup is appended; count a family only if something survives; stop after eight nonempty families. Do not retain identities from a family beyond the selection boundary or generate every family eagerly. The implementation shape is:

```java
final List<Candidate> pool = new ArrayList<Candidate>();
final Set<CandidateIdentity> retained = new HashSet<CandidateIdentity>();
int families = 0;
for (final Conflict conflict : conflicts.conflicts()) {
    final List<Candidate> family = new ArrayList<Candidate>(6);
    if (conflict.isCrossing()) {
        addCrossingCandidates(family, conflict, positions, pinned, enclosurePaths);
    }
    else {
        addEdgeOverNodeCandidates(family, conflict, positions, pinned);
    }
    boolean nonempty = false;
    for (final Candidate candidate : family) {
        if (retained.add(new CandidateIdentity(candidate))) {
            pool.add(candidate);
            nonempty = true;
        }
    }
    if (nonempty && ++families == MAX_CANDIDATE_FAMILIES_PER_ROUND) {
        break;
    }
}
return pool;
```

Ensure `conflicts.conflicts()` really exposes ascending canonical detection identities; preserve/build an explicit sorted view if necessary without extra geometry predicates. Change `greedyDisjoint` to stop at eight selected entries, keeping first-fit traversal and leaving skipped entries untouched. Preserve `pool.removeAll(batch)` semantics for precisely selected object identities; do not accidentally remove all alternatives sharing moved nodes. Preserve existing batch-then-singles, one-entry evaluated once, immediate STOP and acceptance pool discard. Keep accepted-round/counter/post-round/terminal logic otherwise unchanged. Complete direct-enumeration detection must use this same policy; no `conflictsDetected>8` exception or generic exhaustion flag.

- [ ] **Step 4: Run correction/router suites and inspect the bounded implementation.**

```bash
gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.layout.EdgeConflictCorrectionShould' --tests 'org.freeplane.plugin.graph.geometry.EdgeRouterShould' -I "$EDGE_CLEARANCE_REPORTS/performance-headless.init.gradle" -PTestLoggingFull --console=plain --rerun-tasks
rg -n 'EDGE_CONFLICT_MAX_REPAIRABLE_CONFLICTS_PER_FRAME|suppressesAFrameWithMoreThanTheRepairableConflictBound|addEdgeOverNodeCandidate\(' freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrection.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrectionShould.java
```

Expected tests PASS; the literal obsolete-symbol scan has no matches (rg exit 1 is expected for that scan). Review ≤8 families, ≤48 unique entries, ≤8 disjoint candidates, no widening when selected families all fail, and no detection restricted to selected families. Keep existing reported-quad two-verification trace passing.

- [ ] **Step 5: Commit.**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrection.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrectionShould.java
git commit -m "Bound clearance family selection and retries [2026-10-16-edge-node-clearance]"
```

## Task 3: Pin global geometry invariants, numerical eligibility, prominence and deterministic retries

**Implementer tier:** Advanced

**Lane:** `clearance`
**Category:** invariant-and-determinism-regression
**Depends on:** Task 2

**Files:**

- Modify/Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrectionShould.java:120-188,325-378,652-873,874-1409` — pin, prominence, sibling/equal-count/ancestor, numerical/permutation and mixed retry controls.
- Modify/Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundaryInvariantsShould.java:1-281` — explicit ancestor escape identity/type fixture.
- Modify/Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrectionShould.java:246-263` — extend existing `ancestorEscapePairKeysReportEscapingChildren` with contained/escaped controls.

**Interfaces:**

- Consumes unchanged `new EdgeConflictCorrection().apply(...)`; `PinProjection.active(PinRecord, ProjectedNodeKey): PinProjection`, `PinProjection.dormant(PinRecord): PinProjection`; `new BoundaryInvariants().detectPairKeys(GraphProjection, GraphGeometry): Set<String>` and `.detect(GraphProjection, GraphGeometry): List<BoundaryInvariants.Violation>`; `GraphGeometryEngine.computeHulls(GraphProjection, LayoutPositions, GeometryTextMetrics): GraphGeometry`; private Fixture and `clearanceFixture(String[],double[],int[][]): Fixture`, `blockedColumnFixture(): Fixture`, `fullyBlockedFixture(): Fixture`, `nineObstructionsFixture(): Fixture`, `routed(Fixture,EdgeConflictResult): EdgePaths`, `assertClearPaths(GraphProjection,GraphGeometry,EdgePaths,Set<ProjectedEdgeKey>): void` from Tasks 1–2.
- Produces only tests/local construction/assertion helpers. No production or fixture-ledger edits. `BoundaryInvariants.Violation.kind(): BoundaryConflict.Kind` must equal `BoundaryConflict.Kind.ANCESTOR_ESCAPE`; no new diagnostic enum/API is introduced.

- [ ] **Step 1: Add explicit active/dormant and prominence controls.**

Add `keepsThePinnedColumnObstructionAsAnExactResidual`: active pin N `(0,0)` in E3, unchanged all positions/anchors, exact sole C2, zero rounds/verifications/repairs/resolved, no correction exhaustion. Require the routed A–B path present; record its fallback/intersection rather than call success-only clearance.

Add `repairsTheColumnWithADormantPin`: same PinRecord dormant, E3's exact N `(-25,-11)`, 1 round/3 verifications/252 work and success oracle. Keep existing C1 active-pin tests.

Add `clearsTheActualProminentNodeDisc` with A/B `(-100,0),(100,0)`, N `(0,5)`, T1 `(-100,100)`, T2 `(100,100)`; edges A–B undirected, N→T1/N→T2 arrow at target only. Construct production contributors so N has two outgoing targets and radius 9.6. Core assertions:

```java
assertThat(fixture.projection.prominence().get(n).visibleOutgoingTargets()).isEqualTo(2);
assertThat(fixture.boundary.geometry().nodes().get(n).radius()).isCloseTo(9.6, within(1e-9));
assertThat(result.positions().nodes().get(n).x()).isEqualTo(0.0);
assertThat(result.positions().nodes().get(n).y()).isCloseTo(12.6, within(1e-9));
assertThat(result.diagnostics().rounds()).isEqualTo(1);
assertThat(result.diagnostics().verifications()).isEqualTo(1);
assertThat(result.diagnostics().conflictsResidual()).isEmpty();
```

Require unchanged other centers, all three path keys, successful straight A–B, no exhaustion/fallback, full oracle using actual radius 9.6. Do not route with radius-eight test discs.

- [ ] **Step 2: Make sibling/equal-count and ancestor controls independently falsifiable.**

Replace the old zero-round sibling expectation with production nodes p `(5,100)`, q `(5,-100)`, a `(0,0)`, c `(-53,0)`, edge p–q; SUBTLE HA/HC contain a/c, EMPHATIC HR contains HA/HC, all anchors `(0,0)`, no suppressed hull. Compute initial and a `(-6,0)` candidate geometry using production recomputation; prove initial sibling keys empty and candidate introduces `CanonicalKeys.hullPair(HA,HC)`. Require actual repair a `(16,0)` in two verifications, no new sibling/ancestor identities and unchanged anchors. Keep the equal-count `(0,45,-53)` fixture's A–C→A–D unsafe identity replacement assertion and accepted `(16,0)`/A–C result. Do not compare only hull counts. Retain the existing legal pre-existing-same-pair test.

Add `BoundaryInvariantsShould.detectsAncestorEscapeInTheCandidatePairSet` with enforced HR/HC: HR square `(-10,-10),(10,-10),(10,10),(-10,10)`; contained HC square `(-1,-1),(1,-1),(1,1),(-1,1)`; candidate HC square `(19,-1),(21,-1),(21,1),(19,1)`. Keep HR unchanged and use the detector over explicit `GraphGeometry.of` hull maps. Assert baseline empty; candidate exactly `CanonicalKeys.pair(HR,HC)`; violation classifies `ANCESTOR_ESCAPE`. Extend the existing `ancestorEscapePairKeysReportEscapingChildren` test to confirm contained false/escaped true. These explicit inconsistent candidate hulls test the detector; they are not claimed production-generated parent geometry.

Add `rebuildsAncestorsAfterTheColumnDiagonal`: E3 with N sole direct node of HC, HC sole child of HR, fixed `(0,0)` anchors, production geometry and inherited tiers. Require N `(-25,-11)`, both hulls recomputed consistently around moved child, containment and no new ancestor keys, exact node/anchor preservation. This is a correction/recomputation check; do not assert E3's unenclosed routing result for this enclosed variant.

- [ ] **Step 3: Extend order, degeneracy and mixed retry coverage using observable outcomes.**

Run E1/E2/E3/fully-blocked fixtures with reversed node/edge lists, node/hull position and geometry maps and visible-endpoint insertion order, plus `new Random(20261016L)` fixed-seed shuffles. Preserve contributor directions/keys. Compare positions, repairs including from/to/batch index, all counters/flags and route classes/sample coordinates; exclude elapsed timings. Preserve `isDeterministicAcrossRunsAndPermutedInputs`, `detectConflictsIsAscendingDeduplicatedAndDeterministic`, quad and mixed permutations.

Add equivalent reversed-endpoint and signed-zero fixtures demonstrating horizontal normal `(0,-1)` independent of traversal. For numerical controls use existing extreme-span/poisoned-geometry construction, never construct forbidden nonfinite `LayoutPoint`s. Probe finite endpoints whose subtraction/hypot/scalar/target overflows, a degenerate zero-length chord, over-64 radius-induced movement, and zero movement; assert no accepted ineligible target, no new exception, retained residual where detection reports one, finite unchanged published positions and no synthetic budget exhaustion. Preserve existing malformed-input exceptions. Use finite huge radii for over-limit targets and the actual supplied boundary geometry to avoid silently recomputing it. Private reflection may inspect candidate construction for an otherwise unreachable numerical branch, but public outcome/counter controls remain the primary regression evidence.

Retain `rejectsTheTwoAdjacentSwapBatchAndRetriesSingles`, `retriesASingleCandidateAfterARejectedBatch` and `recordsOneRepairPerCandidateForAnAcceptedMixedKindBatch`. Add `retriesTheOppositeC2TargetAfterARejectedMixedBatch`: union of the existing reported quad `(272,170),(307,202),(275,240),(240,208)`, edges A–C/B–D and quad C/D actively pinned, with E2 translated by `(1000,1000)` and distinct canonical IDs. Use production radius eight consistently. Prove the first mixed batch rejected, a legal quad singleton accepted, and the new-state C2 preferred veto followed by opposite move to translated N `(1000,989)`; only allowed quad movement plus that N changes. Assert each accepted prefix has complete global conflict strict subset, gap/hull identity subsets and individual RepairKind/batch records. Reconstruct accepted-prefix geometry from the repair from/to maps in test code; do not add a production trace API or pin private array layouts.

- [ ] **Step 4: Run the regression matrix and verify tests can reject the unsafe mechanisms.**

```bash
gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.layout.EdgeConflictCorrectionShould' --tests 'org.freeplane.plugin.graph.layout.BoundaryInvariantsShould' --tests 'org.freeplane.plugin.graph.layout.BoundarySeparationCorrectionShould.ancestorEscapePairKeysReportEscapingChildren' --tests 'org.freeplane.plugin.graph.geometry.EdgeRouterShould' -I "$EDGE_CLEARANCE_REPORTS/performance-headless.init.gradle" -PTestLoggingFull --console=plain --rerun-tasks
```

Expected: PASS with no failure/error/skip. Tests may already pass after Tasks 1–2; report them as preservation coverage. For red evidence, run test-only mutants from an external temporary class directory/worktree copy: force C2 radius 8 (prominence must fail); remove A3 identity subset veto (sibling/equal-count must fail); reverse tied C2 side order (E3 must fail). Keep active worktree and frozen files unchanged during probes and save exact mutant/source/classpath commands/results externally. No new production fix is in this task's allowlist: a discovered implementation failure must return to the controller as a concrete unmet earlier interface, not be hidden by loosening the test.

- [ ] **Step 5: Commit.**

```bash
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrectionShould.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundaryInvariantsShould.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrectionShould.java
git commit -m "Cover clearance geometry and deterministic controls [2026-10-16-edge-node-clearance]"
```

## Task 4: Verify complete crowded residuals, shared stops and unchanged performance pins

**Implementer tier:** Standard

**Lane:** `clearance`
**Category:** accounting-and-performance-regression
**Depends on:** Task 3

**Files:**

- Modify/Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrectionShould.java:189-651,832-861,1380-1409` — 257-node global verification stop and strengthened existing stop/reset assertions.
- Read only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/PerformanceTripwiresShould.java`, `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GraphWorkspacePerformanceDiagnostic.java`, `freeplane_plugin_graph/src/test/resources/performance/settled-reference.csv` and its loader/compiler inputs.

**Interfaces:**

- Consumes Tasks 1–2 `clearanceFixture(String[],double[],int[][]): Fixture`, `nineObstructionsFixture(): Fixture`, `apply(EdgeConflictCorrection, Fixture): EdgeConflictResult` and package-private static `EdgeConflictCorrection.detectConflicts(GraphProjection, LayoutPositions, GraphGeometry, Set<ProjectedEndpointKey>): EdgeConflictCorrection.Detection`, whose `.keys(): List<String>`, `.crossings(): int`, and `.edgeOverNodes(): int` expose unbounded test-only detection. Use these existing signatures rather than adding an overload.
- Consumes unchanged `EdgeConflictDiagnostics` getters for residual, rounds/verifications, flags and counted work; `BoundarySeparationResult`/diagnostics and existing counted boundary/node test fixtures. Produces only additional/strengthened regression methods and external verification logs.

- [ ] **Step 1: Add the global-A2-stop regression with exact residual/work.**

`keepsTheCompleteCrowdedResidualWhenGlobalVerificationCannotFinish` uses the E1 27-node/9-edge fixture plus 230 isolated nodes `Xi=(10000+100*i,10000)`, i=0..229, production geometry, no additional pins/edges/hulls. Assert 257 positioned nodes, zero initial gaps and exact nine initial C2 identities. Then:

```java
final EdgeConflictResult result = apply(fixture);
assertThat(result.positions()).isEqualTo(fixture.boundary.positions());
assertThat(result.diagnostics().rounds()).isZero();
assertThat(result.diagnostics().verifications()).isEqualTo(1);
assertThat(result.diagnostics().repairsApplied()).isEmpty();
assertThat(result.diagnostics().verificationWork()).isEqualTo(65_536);
assertThat(result.diagnostics().verificationBudgetExhausted()).isTrue();
assertThat(result.diagnostics().detectionTruncated()).isFalse();
assertThat(result.diagnostics().conflictsResidual()).containsExactlyElementsOf(initialCompleteKeys);
```

Define `initialCompleteKeys` by the existing unbounded test diagnostic and compare it again with detection on **published** unchanged positions. Require detected 9/resolved 0, no retry or counter reset. `2*C(257,2)=65_792` exceeds retained work even though selected batch has only eight moves. This test must fail the original suppression implementation's zero verifications/no verification exhaustion. Verify the new test against an external baseline class copy if Tasks 1–2 already make it green.

- [ ] **Step 2: Strengthen the phase-specific stop/reset contract without changing units.**

For existing item/pair/detected-volume probes require cap reconciliation, ascending deduplicated lower-bound keys, no repair/resolution and no terminal rebuild after initial detection STOP. Keep complete direct enumeration distinct from detection truncation and confirm it still enters selection. For candidate detection/work STOP, require one rejected current attempt, last accepted positions and **last complete** residual, published `detectionTruncated=false`, verification exhaustion true and no later retry/detection. Keep post-round/terminal detection STOP's lower-bound residual from the actual published geometry and skip rebuild. Preserve terminal boundary/node rebuild partial-record nonpublication and all consumed work. Retain both attempt-64 cases (clears all vs leaves residual), four-round no-exhaustion case, and reset between separate calls reusing one correction object. Assert E1's exact work 1_755 and fully blocked/dedup totals from Task 2 alongside caps. Do not delete post-round/terminal scans to fund alternatives.

- [ ] **Step 3: Run accounting tests and the exact performance tripwires.**

```bash
sha256sum freeplane_plugin_graph/src/test/resources/performance/settled-reference.csv
gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.layout.EdgeConflictCorrectionShould' -I "$EDGE_CLEARANCE_REPORTS/performance-headless.init.gradle" -PTestLoggingFull --console=plain --rerun-tasks
gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.performance.PerformanceTripwiresShould.referenceFixtureStaysInsideTheStrictWorkerAndFirstFrameGates' --tests 'org.freeplane.plugin.graph.performance.PerformanceTripwiresShould.referenceFixtureStaysBoundedAndFlagVisible' --tests 'org.freeplane.plugin.graph.performance.PerformanceTripwiresShould.rejectsAFrameWhoseRoutingExceedsTheCompleteFrameBound' -I "$EDGE_CLEARANCE_REPORTS/performance-headless.init.gradle" -PTestLoggingFull --console=plain --rerun-tasks
```

Require the pinned CSV digest from Global Constraints and PASS for the three A7 tests. Check `RAW_REFERENCE_MAX_FRAME_NANOS` remains `5_000_000_000L`, measured complete frames include routing, injected delay is constant+1, workload/counts/sample counts/zero-exhaustion flags are unchanged. Save logs/XML externally with actual Java/cwd/OID. If a performance regression occurs, report measurements and the exact gate; do not change this task's read-only files or weaken a threshold. The strict diagnostic and whole-module vector run in Task 7 after harness compilation.

- [ ] **Step 4: Commit.**

```bash
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrectionShould.java
git commit -m "Pin clearance shared-work stop outcomes [2026-10-16-edge-node-clearance]"
```

## Task 5: Prove the first published E3 paths come from a real corrected worker frame

**Implementer tier:** Advanced

**Lane:** `clearance`
**Category:** publication-and-test-replay
**Depends on:** Task 4

**Files:**

- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/EdgeNodeClearanceReplay.java` — JDK-only test helper's E3 fixture, raw engine, real-worker FrameStepper, assertions/oracle and exact-bit state digest; Task 6 adds installed-window run/capture.
- Modify/Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/LayoutSettleLoopShould.java:1-175,1400-1750,1990-2401` — first-publication witness, replay reset/failure checks, use existing completion/close helpers.
- Read only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/LayoutSettleLoop.java`, `CanvasState.java` in that control package; `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutWorker.java`, `LayoutEngine.java`, `LayoutFrame.java`, `PerceptualIdlePolicy.java` in the layout package.

**Interfaces:**

- Consumes unchanged package-private `LayoutSettleLoop(WorkspaceId, FrameStepper, GraphGeometryEngine, GeometryTextMetrics, EdtExecutor)` and its four-argument overload omitting metrics, `start(AcceptedBatch,GraphProjection,ProjectionDiff,CanvasStateListener): CompletionStage<Void>`, `pause(): void`, `close(): void`, `CanvasState.withStatus(OperationalStatus): CanvasState`, `new AcceptedBatch(long generation,long acceptedAtNanos,Set<ChangeKind>)`, and existing `ImmediateEdt`, `batch(long): AcceptedBatch`, `emptyProjection(long): GraphProjection`, `await(CompletionStage<T>): T` test helpers. AcceptedBatch has no workspace field; the loop constructor supplies the workspace.
- Consumes existing `FrameStepper` methods `submit(LayoutRequest): CompletionStage<LayoutFrame>`, `step(): CompletionStage<LayoutFrame>`, `pause(): void`, `restart(): void`, `reset(): void`, `lastValidFrame(): LayoutFrame`, `close(): void`; `LayoutEngine.apply(LayoutRequest): LayoutFrame`, `step(): LayoutFrame`, `reset(): void`, `close(): void`; `new LayoutWorker(Supplier<LayoutEngine>, PerceptualIdlePolicy)`, `.submit`, `.step`, `.pause`, `.restart`, `.lastValidFrame`, `.close` with the corresponding types.
- Produces test helper `public final class EdgeNodeClearanceReplay` in the control package with package-private `static GraphProjection projection()`, `static LayoutPositions rawPositions()`, `static LayoutSettleLoop.FrameStepper newStepper()`, `static void assertE3(CanvasState state)`, `static double minimumClearance(GraphProjection, GraphGeometry, EdgePaths)`, `static String stateDigest(CanvasState)`. All dependencies/nested helper types live in this one source; runtime assertions use JDK `AssertionError`, no JUnit/AssertJ or other test utility reference. Stable test workspace UUID: `00000000-0000-0000-0000-000000000100`; map UUID/IDs/generation from Global Constraints. Task 7 must generate `.fpg`/`.mm` matching these values.

- [ ] **Step 1: Add the publication regression against unchanged production or an external baseline class copy.**

Use the helper fixture with no pins/hulls, exact E3 raw positions, normal metrics/idle policy and real `LayoutWorker`. The adapter must not return a frame constructed with repaired positions. Add exactly `publishesAStraightClearPathAfterRepairingTheBlockedColumn`. Its control flow is:

```java
final GraphProjection projection = EdgeNodeClearanceReplay.projection();
final WorkspaceId e3Workspace = WorkspaceId.of("00000000-0000-0000-0000-000000000100");
final CompletableFuture<CanvasState> first = new CompletableFuture<CanvasState>();
final LayoutSettleLoop loop = new LayoutSettleLoop(e3Workspace,
    EdgeNodeClearanceReplay.newStepper(), new GraphGeometryEngine(), new ImmediateEdt());
try {
    loop.start(batch(1L), projection, ProjectionDiff.between(emptyProjection(0L), projection), state -> {
        try {
            loop.pause();
            first.complete(state);
        }
        catch (Throwable failure) {
            first.completeExceptionally(failure);
        }
    });
    final CanvasState state = first.get(5, TimeUnit.SECONDS);
    EdgeNodeClearanceReplay.assertE3(state);
    final CanvasState changedStatus = state.withStatus(state.status());
    assertThat(changedStatus.edgePaths()).isSameAs(state.edgePaths());
    assertThat(changedStatus.layout()).isSameAs(state.layout());
    assertThat(changedStatus.geometry()).isSameAs(state.geometry());
}
finally {
    await(CompletableFuture.runAsync(loop::close));
}
```

Keep the suite's existing `WORKSPACE=...0101` unchanged; this witness uses its local E3 workspace `...0100` and existing `batch(1L)` for matching generation. The existing generic `await` bounds the off-thread close future at five seconds and waits for `loop.close()` to physically return, including real-worker close. The manual-dispatcher close overloads elsewhere in this suite do not accept this real-worker setup and must not be called here. Assertions occur on the test thread, not only inside the swallowed listener. Pause before completing the future so a later replay frame cannot replace first-frame diagnostics.

- [ ] **Step 2: Implement the helper's raw engine, real adapter and independent acceptance assertions.**

`projection()` contains the exact E3 nine nodes and native A–B connector from spec §6.4. `rawPositions()` is N `(0,0)` and all original column centers, never `(-25,-11)`. Use this raw engine; `apply` itself returns the first uncorrected frame because `LayoutWorker.runSubmit` immediately accepts that return value:

```java
private static final class RawReplayEngine implements LayoutEngine {
    private long index;
    private boolean initialized;

    @Override public LayoutFrame apply(final LayoutRequest request) {
        final LayoutPositions raw = rawPositions();
        final Set<ProjectedNodeKey> expected = new HashSet<ProjectedNodeKey>();
        for (final ProjectedNode node : request.projection().nodes()) {
            expected.add(node.key());
        }
        if (!expected.equals(raw.nodes().keySet()) || !request.projection().enclosures().isEmpty()) {
            throw new IllegalArgumentException("E3 replay request coverage mismatch");
        }
        index = 0L;
        initialized = true;
        return LayoutFrame.of(index, raw, false);
    }

    @Override public LayoutFrame step() {
        if (!initialized) {
            throw new IllegalStateException("E3 replay has no request");
        }
        return LayoutFrame.of(++index, rawPositions(), false);
    }

    @Override public void reset() { index = 0L; initialized = false; }
    @Override public void close() { reset(); }
}

private static LayoutWorker freshWorker() {
    return new LayoutWorker(RawReplayEngine::new, PerceptualIdlePolicy.spikeDefaults());
}
```

The engine performs no separation/correction/routing and its frames retain unverified engine diagnostics. The adapter delegates to the real worker:

```java
private static final class ReplayStepper implements LayoutSettleLoop.FrameStepper {
    private LayoutWorker worker = freshWorker();
    @Override public synchronized CompletionStage<LayoutFrame> submit(LayoutRequest request) {
        return worker.submit(request);
    }
    @Override public synchronized CompletionStage<LayoutFrame> step() { return worker.step(); }
    @Override public synchronized void pause() { worker.pause(); }
    @Override public synchronized void restart() { worker.restart(); }
    @Override public synchronized LayoutFrame lastValidFrame() { return worker.lastValidFrame(); }
    @Override public synchronized void reset() { worker.close(); worker = freshWorker(); }
    @Override public synchronized void close() { worker.close(); }
}
```

`freshWorker(): LayoutWorker` supplies a new raw replay engine every time. Reset runs on the loop lifecycle path, never on EDT. Keep helper/nested classes Java 8 compatible. `assertE3` requires non-FAILED status and nonfailed layout, exact generation/projection identities, N `(-25,-11)`, all other centers/anchors unchanged, actual radius eight, geometry centers equal layout positions, sole expected edge present, successful two-sample straight rim attachments, correction detected/resolved 1, crossings zero, one TRANSLATION repair from `(0,0)` with batch 0, 1 round/3 verifications/252 work, empty residual, all flags false and caps retained. Independent `minimumClearance` repeats the clamped segment/disc oracle, checks coverage/finite data, excludes only attached endpoints and returns the minimum `distance-radius`; `assertE3` requires ≥`3-1e-9`. Digest sorts canonical keys and encodes exact normalized double bits for all centers/radii, edge keys, classifications and samples; encode explicit counts/string lengths to prevent ambiguous concatenations; SHA-256 via JDK.

- [ ] **Step 3: Run publication and preservation tests, and prove they reject baseline geometry.**

```bash
gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.control.LayoutSettleLoopShould.publishesAStraightClearPathAfterRepairingTheBlockedColumn' -I "$EDGE_CLEARANCE_REPORTS/performance-headless.init.gradle" -PTestLoggingFull --console=plain --rerun-tasks
gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.control.LayoutSettleLoopShould' --tests 'org.freeplane.plugin.graph.layout.LayoutWorkerShould' --tests 'org.freeplane.plugin.graph.geometry.EdgeRouterShould' -I "$EDGE_CLEARANCE_REPORTS/performance-headless.init.gradle" -PTestLoggingFull --console=plain --rerun-tasks
```

Expected: PASS on amended production; baseline/mutant with original C2 family must fail N movement/path/first-frame diagnostics, not timeout due an invalid test setup. Preserve `publishTheFramesOwnRoutedEdgePaths`, `canvasStateCopiesItsStatusAndReferencesImmutably`, stale generation/discard behavior and all close-completion tests. Verify helper source references only JDK and installed production classes, no regression-test helper or AssertJ/JUnit dependency.

- [ ] **Step 4: Commit.**

```bash
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/LayoutSettleLoopShould.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/EdgeNodeClearanceReplay.java
git commit -m "Verify repaired E3 canvas publication [2026-10-16-edge-node-clearance]"
```

## Task 6: Bind the replay to the installed window and capture one retained painted state

**Implementer tier:** Capable

**Lane:** `clearance`
**Category:** installed-view-evidence-helper
**Depends on:** Task 5

**Files:**

- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/EdgeNodeClearanceReplay.java:1-400` — extend Task 5 helper with reflective window binding, deadlines, exact retained snapshots, painting/Robot capture, state artifact and bounded cleanup; use named helper methods after Task 5 establishes actual line count.
- Modify/Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/LayoutSettleLoopShould.java:1-175,1990-2401` — helper digest/oracle/error/deadline checks in the existing test source.
- Read only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java:110-120,459-495,1200-1220,1923-1984`; `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphCanvas.java`; `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceController.java`; `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/GraphModeExtension.java:135-180`.

**Interfaces:**

- Consumes Task 5 package-private `projection(): GraphProjection`, `rawPositions(): LayoutPositions`, `newStepper(): LayoutSettleLoop.FrameStepper`, `assertE3(CanvasState): void`, `minimumClearance(GraphProjection,GraphGeometry,EdgePaths): double`, `stateDigest(CanvasState): String`; existing package-private `LayoutSettleLoop(WorkspaceId,FrameStepper,GraphGeometryEngine,GeometryTextMetrics,EdtExecutor)`, `start(AcceptedBatch,GraphProjection,ProjectionDiff,CanvasStateListener): CompletionStage<Void>`, `pause(): void`, `close(): void`; `EdtExecutor.call(Callable<T>): T`, `.isEdt(): boolean`, `.execute(Runnable): void`; `new AcceptedBatch(long generation,long acceptedAtNanos,Set<ChangeKind>)`.
- Consumes real window private `model`; model fields `canvasRegistration`, `canvas`, `binding`, `currentState`, `applicationController`; model method `acceptCanvasState(CanvasState): void`; existing registration `.close()`, real `GraphCanvas.fitGraph(): void`, `.runWhenViewportSettled(Runnable): void`, `.viewport(): GraphViewport`, reflectively read package-private `.canvasState(): CanvasState`; real window `.close(): void`; forwarding controller private `delegate` and real `DefaultGraphWorkspaceController.shutdown(): void`. Reflection reads fields/invokes existing methods; no field writes.
- Produces test-helper `public static void run(Object window, Path attemptDirectory) throws Exception`; package-private `static <T> T onEdt(Callable<T> operation, long deadlineNanos) throws Exception`, `static void requireSameSnapshot(Snapshot before, Snapshot after)`, nested immutable `Snapshot`; JDK-only JSON/bit encoding/digest and cleanup helpers, all nested in this source.
- Cross-process contract for Task 7: outer writes UTF-8 `launch.properties` containing `attemptId`, `launchEpochMillis`, `deadlineEpochMillis`, repository root, source/diff/Java/command/agent/bundle hashes and fixture identities. Agent writes `agent-readiness.properties` containing checks at action/helper readiness and loader identities before invoking `run`. Helper reads these, writes `e3-state.json`, `e3-window.png` and atomic `helper-result.json` (stage/result/error/hashes/cleanup), then returns. Agent converts successful helper result into atomic `agent-result.json` and may request clean app exit; **only outer writes final `result.json` PASS after checking actual child exit and ownership cleanup**. These extra staging files do not replace required artifacts.

- [ ] **Step 1: Add falsifiable helper data/deadline/failure-transport tests.**

Use the first real published state from Task 5. Assert `stateDigest` remains stable after map/list reordering but changes for a changed node center/radius, edge coverage/classification or any path sample; construct test states with existing immutable factories and correct generation, not a production hook. Have the oracle reject a deliberately omitted expected edge, a straight through raw N, and a prominent-radius violation. Do not use a mocked application window as evidence.

Test `onEdt` propagates a thrown `IllegalStateException("evidence-edt-failure")` and never relies on assertions swallowed by Swing or the loop. Queue a deliberately gated EDT task; call `onEdt` with a short bounded deadline from another test thread; require timeout and release the gate in `finally` so no test-thread leak remains. For `requireSameSnapshot` construct immutable snapshots differing individually in state object reference (same generation/digest), generation, digest, bounds and viewport; each difference must reject. These tests validate the exact mechanisms the installed gate uses; a real installed-window pass remains Task 7's requirement.

- [ ] **Step 2: Implement timed EDT access, unregister/fence and first-publication binding.**

Timed requests post `FutureTask<T>` with `SwingUtilities.invokeLater`, then call `get(remaining, NANOSECONDS)` off EDT; if already EDT, run immediately without a blocking wait. Deadline calculation checks positive remaining time and preserves causes from `ExecutionException`; timeout is FAIL, not a retry on a different seam. Cancel/mark expired tasks so a late task cannot bind/capture after attempt failure. Real Swing-backed `EdtExecutor` dispatches through `SwingUtilities`; helper coordinator never uses unbounded `invokeAndWait`.

In `run`, require exact showing window class/name, installed helper/window/control/worker/router class identity/loader and installed bundle provenance, then reflectively read model/canvas/binding/current state. Close original canvas registration **on EDT**, then issue a separate EDT fence before adding replay listener. Keep original window/model/canvas/presentation/painter unchanged. Construct real installed worker adapter/loop with Task 5 fixture/normal collaborators and workspace UUID `...0100`; create `new AcceptedBatch(1L, 1L, EnumSet.of(ChangeKind.STRUCTURE))` for generation 1. First listener must:

```java
try {
    loop.pause();
    acceptCanvasStateMethod.invoke(model, state);
    first.complete(state);
}
catch (Throwable failure) {
    first.completeExceptionally(unwrap(failure));
}
```

Use an atomic first-callback guard; additional callbacks or an observed ordinary-controller overwrite fail. Resolve `first.get(min(5 seconds,remaining),NANOSECONDS)` off EDT, call `assertE3` and preserve the exact state reference. Do not await the paused run's normal settle completion before capture. Record pre-replay/raw and accepted diagnostics to demonstrate that the worker actually corrected N.

- [ ] **Step 3: Fit/settle, snapshot, paint and capture the real visible window.**

On timed EDT requests validate/layout the real showing window, invoke canvas `fitGraph`, and register a `runWhenViewportSettled` future; wait off EDT within remaining deadline, then fence. Obtain visible canvas/viewport screen bounds through the actual viewport component's visible rectangle and `getLocationOnScreen`, not the offscreen full scrollable canvas. Convert world node-disc extents, labels/hulls and all actual path samples with actual viewport/zoom/component offsets; require the whole drawing within the visible graph region. Record window/visible-canvas/drawing screen bounds and numeric viewport.

Before capture, require `model.currentState == retained` and reflected `canvas.canvasState() == retained`. Record generation, exact-bit `stateDigest`, bounds and viewport as `Snapshot`. On EDT call actual root pane/canvas `paintImmediately`, drain queued repaint work and `Toolkit.sync`. From a separate bounded capture executor call `new Robot().waitForIdle()` then `createScreenCapture(windowBounds)`; never paint a synthetic BufferedImage as screenshot source. Await capture with remaining attempt deadline; retain/propagate failures. Re-snapshot on timed EDT immediately afterward and require same object references/generation/digest/bounds/viewport; recompute `assertE3`/oracle from the retained state. On mismatch delete the attempted success PNG and write failure details. Write PNG with ImageIO only after valid capture; hash exact saved bytes.

- [ ] **Step 4: Emit the numeric state artifact and perform bounded physical cleanup.**

State JSON must have these explicit top-level records; no field may be omitted on PASS:

```json
{
  "schemaVersion": 1,
  "attemptId": "value read from launch.properties",
  "fixture": {"seed": 20261016, "workspace": "00000000-0000-0000-0000-000000000100", "map": "00000000-0000-0000-0000-000000000001", "generation": 1, "rawCenters": {}, "pins": []},
  "securityChecks": {"actionReady": {}, "helperReady": {}},
  "installedClasses": {},
  "retainedState": {"before": {}, "after": {}, "digest": "exact-bit SHA-256"},
  "correctedNodes": [],
  "edgeCoverage": {"expected": [], "actual": []},
  "paths": [],
  "correction": {},
  "routing": {},
  "clearance": {"tolerance": 1e-9, "required": 3.0, "minimum": 3.0, "segmentDiscChecks": []},
  "screen": {"window": {}, "visibleCanvas": {}, "drawing": {}, "viewport": {}},
  "screenshotSha256": "SHA-256 of e3-window.png"
}
```

The empty arrays/objects above identify schema containers, **not implementation values**: populate them with all nine raw/corrected finite centers/radii, every actual path sample and classification, exact keys, each security/loader check, all diagnostics/counters/flags, both state-reference comparisons, each segment/unrelated-disc measurement and real screen data. Include whole-launch provenance from Task 7; encode strings/controls correctly using JDK-only JSON routines, no new JSON library or hand-concatenated unescaped paths. Assert E3 1/3/252/no fallback/exhaustion before staging success. Write temp files then atomic move in the attempt directory; a failed atomic publication is failure, not an unbound PASS.

In `finally`, reserve/consume the separate 10-second cleanup allowance. Submit `loop.close()` to a cleanup executor off EDT/lifecycle/worker, then await actual task return within cleanup deadline, preserving its exception. Close real graph window on timed EDT. Read `model.applicationController`, unwrap only installed `GraphModeExtension.ForwardingGraphWorkspaceController.delegate`, require `DefaultGraphWorkspaceController`, and submit/await its existing `shutdown()` off EDT. Verify window disposal. Combine operation and cleanup errors with suppressed causes instead of overwriting either. Boundedly shut down capture/cleanup executors. Return successful `helper-result.json` only if capture checks and cleanup all passed; otherwise stage FAIL with stage/error/cleanup and throw. The outer process remains responsible for killing a hung child/descendants; no unbounded wait is allowed here and no new close-future API is added.

- [ ] **Step 5: Run unit/compilation checks for the helper and publication suite.**

```bash
gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.control.LayoutSettleLoopShould' -I "$EDGE_CLEARANCE_REPORTS/performance-headless.init.gradle" -PTestLoggingFull --console=plain --rerun-tasks
gradle :freeplane_plugin_graph:testClasses --console=plain
```

Expected: PASS, helper remains JDK/production-only at runtime and Java 8 target. This task establishes the helper and negative-control mechanisms; do not claim installed-window evidence before Task 7 supplies bootstrap/launch and the real screenshot. Inspect that helper defines no additional source files, writes no production field, and has no fallback renderer/duplicate classloader path.

- [ ] **Step 6: Commit.**

```bash
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/EdgeNodeClearanceReplay.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/LayoutSettleLoopShould.java
git commit -m "Capture retained installed E3 replay state [2026-10-16-edge-node-clearance]"
```

## Task 7: Launch and validate the installed-application E3 gate and run final acceptance

**Implementer tier:** Capable

**Lane:** `clearance`
**Category:** evidence-bootstrap-and-integration
**Depends on:** Task 6

**Files:**

- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/smoke/EdgeNodeClearanceEvidenceAgent.java` — JDK-only premain/coordinator/readiness/loader/class definition and error/result transport, with nested helper classes only.
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/smoke/EdgeNodeClearanceApplicationEvidence.java` — JDK-only launcher/fixtures/provenance/deadlines/process ownership/result validation, including `--self-test` of result validation.
- Modify: `freeplane_plugin_graph/build.gradle:145-197` — add exactly the two evidence tasks beside existing installation/smoke tasks; preserve existing tasks/dependencies/settings.
- Read only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/smoke/FreeplaneLaunchSmoke.java`, `GraphPluginOsgiSmoke.java` in the same smoke package; `freeplane_plugin_graph/src/test/resources/workspace/format-1-full.fpg`, `freeplane_plugin_graph/src/test/resources/maps/graph-group-known.mm`; `freeplane_framework/script/freeplane.sh`; production Controller/action/window/controller/loop/worker/router classes.

**Interfaces:**

- Consumes Task 6 helper `public static void run(Object window, Path attemptDirectory) throws Exception` after definition into the installed control loader, Task 5 helper exact workspace/map/IDs/raw E3 fixture, and the `launch.properties` → `agent-readiness.properties` → `helper-result.json` → `agent-result.json` → outer-only `result.json` protocol described in Task 6. Every process/staging document carries the same unique attemptId.
- Consumes existing `graphSmokeInstallation` Gradle task, actual `BIN/freeplane.sh`, existing core and graph installed bundle directories; use launcher repository-root argument, not guessed cwd or `BIN/gitinfo.txt`, for source provenance. JavaExec command arguments are `[absoluteRepositoryRoot, absoluteAgentJar, absoluteExternalEvidenceDirectory]`.
- Produces `EdgeNodeClearanceEvidenceAgent.premain(String attemptDirectory, Instrumentation): void`; `EdgeNodeClearanceApplicationEvidence.main(String[] args): void` (three path arguments; or exactly `--self-test` for isolated JDK-only validator checks); nonzero process exit on every failed launch/artifact/cleanup gate.
- Produces `edgeNodeClearanceEvidenceAgent` Jar and `edgeNodeClearanceApplicationEvidence` JavaExec, required `-PedgeClearanceEvidenceDir`, plus fresh external per-attempt artifacts. Final `result.json` schema: `schemaVersion`, `attemptId`, `stage`, `status` (`PASS`/`FAIL`), `error` (structured cause/stack), `provenance` (cwd/source/diff/java/command/fixture/installedBundle/agent hashes), `artifacts` (paths/state/image/log hashes), `deadlines`, `cleanup` (helper close/controller shutdown/window disposed/child exit/owned PIDs and survivors).

- [ ] **Step 1: Build the test-only launcher contract and observe its negative acceptance before live bootstrap.**

Implement JDK-only `--self-test` in the outer launcher as a local temporary-directory validator exercise, separate from the live gate. It constructs complete minimal schema fixtures with real file hashes and unique attempt ID, then requires validation failure for missing/stale result, malformed JSON, wrong attempt ID, FAIL status, state/image hash mismatch, missing edge coverage/clearance/1–3–252 checks, security/loader mismatch, changed reference/digest/viewport, failed cleanup/nonzero child exit and surviving owned process. A synthetic validator self-test result is never accepted as installed evidence and never written to the live evidence directory.

Before implementing positive validation, have each invalid case assert that the precise rejection occurred; no `catch(Exception)` that treats arbitrary parser/IO failure as the expected rejection. The valid synthetic case checks only the result validator in self-test mode. Invoke with Java 21 and test classpath after `testClasses`; do not add a third Gradle task or another harness source for this purpose.

- [ ] **Step 2: Implement isolated profile/fixture creation and exact installed launch.**

Require Java 21, existing readable agent Jar and absolute writable external evidence directory outside the repository. Create fresh `attempt-<timestamp>-<UUID>` with home/config/fixtures; never select an earlier result. Generate UTF-8 `.mm` with root plus nine leaf IDs A/B/N/O1..O6, all leaves `<graph_group version="1"/>`. Generate compatible version-1 `.fpg` from existing format: workspace UUID `...0100`, map UUID `...0001`, one active map pointing to absolute .mm URI, one stable relationship UUID `00000000-0000-0000-0000-000000000010` A→B with `direction="UNDIRECTED"` and display arrowheads disabled, empty pins, viewport covering full fixture and display settings. Use existing XML format/enum values, not a new parser schema. Replay's native connector may differ in contributor kind from the opening workspace relationship but canonical endpoints/IDs must match and its directions remain arrows-off.

Encode the single absolute workspace entry in profile `graph_workspace_recent_workspaces` as the literal absolute path (the one-entry result of `ConfigurationUtils.encodeListValue(paths,true)`; multiple entries would be separated by `File.pathSeparator + File.pathSeparator`). Escape it through JDK `Properties.store(OutputStream,...)` when writing ISO-8859-1 `auto.properties` under the same version subdirectory as the existing `FreeplaneLaunchSmoke` (`config/freeplane/1.12.x` in this checkout). The outer remains JDK-only and does not link core. Seed all six settings in Global Constraints. Record files/full raw E3 coordinates and SHA-256. Record actual `git rev-parse HEAD`, working `git diff --binary HEAD` digest plus untracked-file provenance when present, Java version, exact structured command/environment, installed graph bundle file tree digests and agent hash. Require core/graph bundles actually populated by `graphSmokeInstallation`; if a never-built checkout lacks BIN, create directory through the build's existing install setup before running install, never accept a stale directory as installation evidence.

Build `ProcessBuilder` with absolute `BIN/freeplane.sh`, working directory BIN, isolated child HOME/XDG_CONFIG_HOME, controlled DISPLAY/Java path and unique `EDGE_CLEARANCE_ATTEMPT_ID`. Strip inherited `-Djava.awt.headless=true` from child JVM option sources (`JAVA_OPTS`, `JAVA_TOOL_OPTIONS`, `JDK_JAVA_OPTIONS`, `_JAVA_OPTIONS`) without dropping required controlled Java/agent flags; do not mutate the parent shell environment. The existing shell launcher expands `$JAVA_OPTS` unquoted: quote characters inside an environment value cannot protect spaces. For this supplied workspace/report root, require that the absolute Jar path and fresh attempt-directory path contain no whitespace or shell glob characters before building the literal single `-javaagent:<jar>=<attempt>` token; fail with the exact offending path before launch if this contract is violated. Pass the executable as a separate ProcessBuilder argument; do not shell-interpolate JSON or insert decorative quotes into JAVA_OPTS. No `-XQuitAction`. Redirect combined output to `app.log`. Start the monotonic 180-second outer evidence clock at process launch; write launch/deadline metadata before agent consumes it. Track exact owned application PID/descendants and marker. Java 9+ process APIs must be accessed reflectively to preserve Java 8 compilation target; do not import ProcessHandle in Java 8 source.

- [ ] **Step 3: Implement bounded premain bootstrap and installed-loader helper definition.**

`premain` validates attempt directory/metadata, captures its Instrumentation/own Jar path, starts one bounded coordinator, installs uncaught-async error transport scoped to this disposable attempt, and returns immediately. Coordinator polls loaded classes for exact `org.freeplane.features.mode.Controller` and posts bounded EDT requests to call `getCurrentController()`, `getModeController()`, `getAction("OpenGraphWorkspaceAction")`. Once action nonnull, require effective disable property true and `System.getSecurityManager()==null`, record both, and invoke `actionPerformed(new ActionEvent(...))` once. Readiness stops at 60 seconds or remaining overall deadline. Do not call `Class.forName` on core via system loader and do not repeatedly invoke action.

Find showing exact named window on EDT within another ≤30 seconds and overall deadline. Its classloader is authoritative; obtain installed `org.freeplane.plugin.graph.control.LayoutSettleLoop` through that loader, require same loader, and recheck/record disable property/null manager immediately before class definition. Resolve installed LayoutWorker/EdgeRouter and record classloader/bundle symbolic-name/version/location/code-source identities; a bundle `BundleReference`/framework identity may be read reflectively, with a real bundle identity required even when code source is absent.

Load helper and every `$` nested-class byte from the agent's own Jar without system-loading any helper. Define top-level helper then all named/nested dependencies in deterministic dependency-aware order before invoking run; class definition must not trigger helper execution until dependencies exist. Reflective Java 9+ calls follow:

```java
final Class<?> handles = Class.forName("java.lang.invoke.MethodHandles");
final Class<?> lookupType = Class.forName("java.lang.invoke.MethodHandles$Lookup");
final Object baseLookup = handles.getMethod("lookup").invoke(null);
final Object lookup = handles.getMethod("privateLookupIn", Class.class, lookupType)
    .invoke(null, installedControlClass, baseLookup);
final Method defineClass = lookupType.getMethod("defineClass", byte[].class);
final Class<?> helper = (Class<?>) defineClass.invoke(lookup, new Object[] {helperBytes});
```

Check helper and all references resolve to installed classes/loader, write readiness metadata, then invoke `helper.getMethod("run", Object.class, Path.class).invoke(null, window, attemptDirectory)` on the coordinator. Wrong loader, duplicate/inaccessible class, security prerequisite failure or helper exception stages FAIL with original underlying cause. No production field replacement, class transformation, app-classpath injection or fallback window. On successful helper cleanup and artifact staging write atomic `agent-result.json`; only then may agent request `System.exit(0)`. Failure exit is nonzero. The outer still bounds a blocked agent/Swing/native thread and validates actual process termination.

- [ ] **Step 4: Add exactly the restricted Jar and always-fresh JavaExec tasks.**

Use current Gradle task conventions; the required shape is:

```groovy
tasks.register('edgeNodeClearanceEvidenceAgent', Jar) {
    dependsOn tasks.named('testClasses')
    archiveClassifier = 'edge-node-clearance-evidence-agent'
    manifest { attributes('Premain-Class': 'org.freeplane.plugin.graph.smoke.EdgeNodeClearanceEvidenceAgent') }
    from(sourceSets.test.output.classesDirs) {
        include 'org/freeplane/plugin/graph/smoke/EdgeNodeClearanceEvidenceAgent.class'
        include 'org/freeplane/plugin/graph/smoke/EdgeNodeClearanceEvidenceAgent$*.class'
        include 'org/freeplane/plugin/graph/control/EdgeNodeClearanceReplay.class'
        include 'org/freeplane/plugin/graph/control/EdgeNodeClearanceReplay$*.class'
    }
}

tasks.register('edgeNodeClearanceApplicationEvidence', JavaExec) {
    dependsOn tasks.named('edgeNodeClearanceEvidenceAgent')
    dependsOn tasks.named('graphSmokeInstallation')
    outputs.upToDateWhen { false }
    classpath = sourceSets.test.runtimeClasspath
    mainClass = 'org.freeplane.plugin.graph.smoke.EdgeNodeClearanceApplicationEvidence'
    workingDir rootDir
    systemProperty 'user.language', 'en'
    systemProperty 'user.country', 'US'
    doFirst {
        final String directory = providers.gradleProperty('edgeClearanceEvidenceDir').orNull
        if (directory == null || directory.trim().isEmpty()) {
            throw new GradleException('Required -PedgeClearanceEvidenceDir=<absolute external directory>')
        }
        setArgs([rootDir.absolutePath,
            tasks.named('edgeNodeClearanceEvidenceAgent').get().archiveFile.get().asFile.absolutePath,
            file(directory).absolutePath])
    }
}
```

Validate required absolute/external path in launcher (and task before launch), do not mark a live run up-to-date, do not set child headless true, and leave JavaExec `ignoreExitValue` false. Jar content inspection must reject any entry other than manifest/directories and the exact agent/helper class patterns; check helper/agent class major 52 and no harness class in production Jar/BIN. Preserve existing `graphOsgiSmoke`, `freeplaneLaunchSmoke` and `graphUiEvidence` tasks unchanged.

- [ ] **Step 5: Finalize result validation, deadlines and owned-process cleanup.**

Outer launcher watches bounded child completion/artifacts within monotonic 180 seconds, preserves logs continuously and treats missing agent/helper result as failure. Parse complete JSON with a strict JDK-only parser local to the outer source (strings/escapes/numbers/arrays/objects/booleans/null, reject malformed/trailing content/duplicate required keys) or use an existing JDK-only parser only if it is included in the same permitted source; do not use test runtime JSON libraries from the agent/helper. Validate schema/attempt identity, launch provenance and exact fixture/bundle/agent hashes, expected keys/paths, state/image hashes, raw/corrected centers/radii, 1/3/252 counters/flags, ≥`3-1e-9` independent numeric measurements, state references/digests/screens/viewport, SecurityManager checks and installed class identities. Compare recorded numbers with parsed path/node data by independently recalculating minima in the outer as an additional check. Verify PNG decodes and dimensions equal recorded window bounds, no stale path traversal/outside-attempt artifact, all required cleanup outcomes, actual zero child exit and no owned survivors.

Finally use the separate 10-second cleanup reserve. Helper close failure, child hang or nonzero exit stages FAIL even if a PNG exists. Track owned descendants while alive, use attempt marker/PID ancestry to terminate only those application processes, boundedly destroy/force-destroy as necessary and record outcomes/survivors. Do not kill Xvfb or user's Freeplane; wrapper owns display cleanup. Never swallow helper/controller shutdown exceptions. Finalize PASS `result.json` by atomic move **after** successful actual child exit/artifact/hash checks and verified cleanup. Always emit FAIL result with stage/error/cleanup when a failure can be recorded, propagate JavaExec nonzero, and retain diagnostic artifacts. Run `--self-test` negative matrix and inspect each precise rejection result before the live gate.

- [ ] **Step 6: Run the entire approved acceptance vector and archive evidence.**

Set Global Constraints' Java/report variables. Commands run sequentially from the integration worktree; record each actual command/exit/XML independently. This task's final gate cannot rely on earlier cached green commands.

```bash
gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.layout.EdgeConflictCorrectionShould' --tests 'org.freeplane.plugin.graph.layout.BoundaryInvariantsShould' --tests 'org.freeplane.plugin.graph.control.LayoutSettleLoopShould' --tests 'org.freeplane.plugin.graph.geometry.EdgeRouterShould' -I "$EDGE_CLEARANCE_REPORTS/performance-headless.init.gradle" -PTestLoggingFull --console=plain --rerun-tasks

gradle :freeplane_plugin_graph:test -I "$EDGE_CLEARANCE_REPORTS/performance-headless.init.gradle" -PTestLoggingFull --console=plain --rerun-tasks

gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.performance.PerformanceTripwiresShould.referenceFixtureStaysInsideTheStrictWorkerAndFirstFrameGates' --tests 'org.freeplane.plugin.graph.performance.PerformanceTripwiresShould.referenceFixtureStaysBoundedAndFlagVisible' --tests 'org.freeplane.plugin.graph.performance.PerformanceTripwiresShould.rejectsAFrameWhoseRoutingExceedsTheCompleteFrameBound' -I "$EDGE_CLEARANCE_REPORTS/performance-headless.init.gradle" -PTestLoggingFull --console=plain --rerun-tasks

gradle :freeplane_plugin_graph:graphPerformanceDiagnostic -PgraphStrictPerformance --console=plain --rerun-tasks

gradle :freeplane_plugin_graph:verifyGraphBundle :freeplane_plugin_graph:graphOsgiSmoke --console=plain

xvfb-run -a -s '-screen 0 1600x1000x24' gradle :freeplane_plugin_graph:freeplaneLaunchSmoke --console=plain

xvfb-run -a -s '-screen 0 1600x1000x24' gradle :freeplane_plugin_graph:edgeNodeClearanceApplicationEvidence -PedgeClearanceEvidenceDir="$EDGE_CLEARANCE_REPORTS/installed-e3" --console=plain

sha256sum freeplane_plugin_graph/src/test/resources/performance/settled-reference.csv docs/superpowers/specs/2026-10-16-edge-node-clearance-design.md docs/superpowers/specs/2026-10-16-edge-node-clearance-spec.md
git diff --check
```

Require all commands exit 0 except none in this final vector are expected failures; pinned inputs/digests remain exact. Strict diagnostic must retain 400/300 measurements, 25 nodes/22 edges, 1 bow/21 straights, no fallbacks/exhaustion/discards and all thresholds in Global Constraints including five-second complete-frame A7b. Retain normal native-connector/arrowhead/pin/enclosure smoke coverage. Inspect the **actual saved** `e3-window.png` visually to confirm complete horizontal straight edge and displaced N are visible/unobscured in the installed window; compare numeric state/image hashes and bounds. Record a separate external `installed-e3/visual-review.json` binding the inspected image SHA-256 and observations; this review is not fabricated by the launcher. If the image fails visual acceptance, the gate fails even if numeric automation passed.

Archive final external `edge-node-clearance-acceptance.json` with spec/design/CSV/plan digests, actual source OID/diff, command/Java/cwd and all exit codes, JUnit XML, strict performance outputs, agent/bundle/Jar-content checks, fresh installed attempt path/hashes and visual review. Do not describe supplied baseline runs as new evidence or claim the original screenshot map was replayed. Resolve environment errors without gate changes; report a concrete blocker if the installed class seam or verified absent manager is unavailable, rather than weakening §8.4.

- [ ] **Step 7: Commit.**

Review staged/unstaged diffs for no main-source/core/startup/export/install-profile change, no test dependency in agent/helper, no extra harness sources or agent in distribution, and no modified performance fixture/spec/design. Commit only reviewed source/build files; external reports stay outside Git.

```bash
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/smoke/EdgeNodeClearanceEvidenceAgent.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/smoke/EdgeNodeClearanceApplicationEvidence.java freeplane_plugin_graph/build.gradle
git diff --cached --check
git commit -m "Add installed E3 clearance evidence gate [2026-10-16-edge-node-clearance]"
```
