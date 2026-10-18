# Graph View initial and preferred spacing — technical specification

- Date: 2026-10-16
- Task identifier: `graph-initial-spacing`
- Delivery target: `plugin/graph-workspace`
- Status: specification amended at epoch 2 after Task 1 convergence finding; implementation and outcome verification are pending.
- Approved design: [2026-10-16-graph-initial-spacing-design.md](2026-10-16-graph-initial-spacing-design.md), committed at `7cb4ad04960fca107de6da39fb5cdb671d72b4e7`. Amendment approved 2026-10-17.
- Pinned old production implementation: `0734fb23db6873f4ec64c3e3e29fc8c3e17edc7a`.
- Decision: scope B, initial placement **and** preferred settling spacing; option 1, **24.0 layout units** of preferred node-disc edge gap, applied to enclosure content rings. Relationship rest lengths retain the collision clearance.

> **Epoch 2 amendment.** Task 1 measured that substituting the preferred gap into the
> relationship rest length makes the cross-map fixture in
> `TypedForcesShould.twoMapWorkspaceSettlesToIdle` fail to converge at any gap above
> the old value: the cross-map rest length grows by 18 units while
> `TypedSpringBox.CROSS_MAP_DISPLACEMENT_LIMIT` allows only 0.005 units of cross-map
> travel per engine step, which needs at least 3600 steps against the 2000-step
> observation bound. Measured receipts are in `reports/task-1-convergence-finding.md`.
> The preferred gap is therefore confined to the content ring, which is not subject to
> that cap, and three additional allowances below are authorized. The acceptance
> contract of section 7, the collision clearance, the packing gutter, the cross-map
> cap, and all idle thresholds are unchanged.

## 1. Contract and production change boundary

Fresh layouts should open with wider node spacing, and the force targets should retain that preference during settling. The preferred value is an equilibrium target. It does not guarantee a 24-unit gap between every settled pair.

The production diff is confined to these two files:

`freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java`

1. Declare one package-private internal `static final double PREFERRED_NODE_GAP = 24.0` beside `NODE_RADIUS`.
2. Substitute that constant for `NodeSeparationProjection.MIN_GAP` in `BoundarySizes.contentRingRadius`.
3. Keep `NodeSeparationProjection.MIN_GAP` in the relationship `ForceLink` rest-length expression in `topology`, and therefore keep the `NodeSeparationProjection` import.

`freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/ClusterPacking.java`

4. Apply the existing `CANDIDATE_EPSILON` tolerance to the clearance comparison in `isClear`, so a candidate exactly on the gutter is accepted:

```java
if (clearanceX < gap - CANDIDATE_EPSILON && clearanceY < gap - CANDIDATE_EPSILON) {
```

This corrects a pre-existing one-ulp fragility (`7.999999999999986 < 8.0`) that the changed item sizes newly expose; it does not change the 8.0 gutter. Larger packed items shift the arithmetic, and a strictly `<` comparison rejects a geometrically valid candidate by one unit in the last place, aborting packing with `Cluster packing found no free slot`.

There is one active preferred-gap parameter. Apart from the authorized `ClusterPacking.isClear` tolerance correction described above, no production algorithm, class interface, factory, command path, dependency, setting, persistence field, serialization format, migration, fallback, or public API changes. No other production file may change. Do not restore obsolete `GROUP_SPACING` or `SUB_GROUP_SPACING` constants. Test-only observation and comparison code is permitted during implementation; it must not enter the plugin bundle.

The following retain their existing independent roles and values:

| Mechanism | Required value/behavior |
| --- | --- |
| Collision clearance, `NodeSeparationProjection.MIN_GAP` | 6.0, and the relationship rest-length term |
| Footprint packing gutter, `BoundarySizes.SIBLING_GAP` | 8.0 |
| Rendered base node radius | 8.0, scaled by the existing prominence |
| Footprint node settle margin / frame clearance | 8.0 / 16.0 |
| Existing boundary padding / hull clearance | 8.0 / 16.0 |
| `LayoutCalibration.spikeDefaults()` | containment 1.0; hierarchy 0.30; relationship (`sameMap`) 1.0 |
| Typed attraction / repulsion / boundary repulsion | 0.05 / 16.0 / 0.5 |
| Aggregate cross-map displacement cap | 0.005 per affected particle per engine step |
| Production idle policy | 8 consecutive frames; RMS <= 0.05; maximum displacement <= 0.10 |
| Pins, worker ownership, correction budgets, publication fencing | Existing mechanisms and limits |
| Zoom, node/label rendering, projection semantics | Existing mechanisms |

Unrelated failures do not authorize widening this diff or relaxing a gate. In particular, an outcome failure with the approved parameter change requires design review before claiming scope B is delivered.

## 2. Structures, formulas, and consumers

`GraphProjection` supplies ordered nodes, enclosures and edges, a complete prominence map, and pins. `LayoutRequest` binds the projection and its real `ProjectionDiff` to a workspace and pin list. `BoundarySizes` derives disc radii, content rings, recursive footprints, and packed placements. `Seeds` places new particles. `Topology` creates particles and `ForceLink` entries; `replaceLinks` registers these with the existing `TypedSpringBox`. No new production structure is needed.

For direct node `i`, let `r_i = 8.0 * projection.prominence().get(i).scale()`. For enclosure `h`, let `n` be **its direct-node count**, and `r_max` the maximum direct-node radius. Define:

```text
R(h) = 0                                             if n <= 1
R(h) = (2*r_max + 24.0) / (2*sin(pi/n))                otherwise
```

Keep direct-node list order and the existing angular origin:

```text
seed(i) = seedAnchor(h) + R(h) * (cos(2*pi*i/n), sin(2*pi*i/n))
```

For fresh, unpinned, equal-radius nodes, the gap is exactly 24.0, within floating-point tolerance, between **adjacent indices on that ring**, including the wrap-around pair. Other pairs can be farther apart. With mixed radii the adjacent centre distance is `2*r_max + 24.0`; the corresponding edge gap is `24.0 + 2*r_max - r_i - r_j`, which is conservatively at least 24.0. Incremental additions are not fresh rings.

Use that same `R(h)` through the existing consumers:

- `Seeds.nodePosition` for newly created particles;
- the content item in `BoundarySizes.placements`, whose square half extent is `R(h) + r_max + 8.0`;
- recursive `footprint`, including its unchanged 16.0 frame clearance on each side;
- every containment link from `h` to one of its direct nodes, with rest length `R(h)`;
- the endpoint-radius lookup for relationships attached to an enclosure.

A relationship endpoint's model radius is the rendered disc radius for a node, and **the enclosure's direct-content ring radius `R(h)`** for an enclosure. It is not a painted-hull extent or recursive-footprint radius. Thus a singleton enclosure, or a parent containing only child enclosures, can have a zero relationship endpoint radius despite a nonempty painted hull.

Every resolved relationship link has:

```text
restLength = firstEndpointModelRadius + secondEndpointModelRadius + 6.0
kind       = RELATIONSHIP
crossMap   = firstEndpoint.mapReferenceId != secondEndpoint.mapReferenceId
```

The added term is the existing collision clearance `NodeSeparationProjection.MIN_GAP`, unchanged by this feature. Relationship rest lengths are deliberately **not** widened: they are subject to the cross-map displacement cap of 0.005 units per engine step, and widening them makes the cross-map fixture non-convergent within the observation bound (see the epoch 2 amendment note and `reports/task-1-convergence-finding.md`). The preferred gap therefore governs enclosure content rings, which are not capped.

This applies to node/node, node/enclosure, and enclosure/enclosure endpoints, both same-map and cross-map. Native same-map connectors and cross-map graph relationships both reach this force-kind path. Preserve endpoint resolution, duplicate/parallel-link handling, and rejection of missing/self links.

Hierarchy rests continue to be the distance of the packed child's anchor from the parent anchor: `hypot(placement.x + item.anchorX, placement.y + item.anchorY)`, or zero when the existing rule yields zero. Expanded content can change these derived placements. Do not introduce another spacing term into hierarchy rests.

`TypedSpringBox.addTypedAttraction` must still consume each registered `link.restLength` in `(distance - link.restLength) * 0.05 * multiplier(kind)`. Preserve `TypedNodeParticle` integration, repulsion, and cross-map clamping. `LayoutWorker.accept` must continue to perform boundary correction, edge-conflict correction, and then idle observation on **corrected positions**. Raw engine frames and corrected worker frames have different verification purposes.

## 3. Lifecycle and error contracts

| Event | Coordinates | Targets and required evidence |
| --- | --- | --- |
| First apply on a fresh engine | Deterministic packed anchors and wider rings for unpinned direct nodes | Independent raw-seed assertions; actual registered target inspection |
| Raw engine `reset()` | Existing disposal/reinitialization and full synchronization | A subsequent raw apply snapshot agrees with a fresh engine for the same request; wider targets restored |
| Application **Reset Layout** | `GraphUpdateCoordinator.resetLayout` -> settle-loop reset -> `WorkerStepper.reset` closes/recreates the worker -> current projection submitted | Exercise the actual command path and a real worker/engine, in addition to existing substitute-stepper tests |
| Healthy **Restart Layout** | Retain the engine and its particle coordinates; resume | No fresh-ring guarantee; prove continuity at restart/resubmission and the existing wider targets |
| Failed worker recovery through Restart | Existing failed-engine replacement and projection resubmission | Prove replacement, current-projection submission and wider targets on the replacement; do not confuse this with healthy restart |
| Real topology/prominence change | Retain every surviving unpinned particle's coordinates **at raw engine apply**; seed only additions; remove obsolete particles | Use nonempty `ProjectionDiff`; prove replaced links and radii/rests match the new topology, without reseeding retained particles |
| Active pins | Exact stored coordinates at apply and every returned/publication frame | Preference and collision clearance can be overridden by conflicting pins; report existing residual/conflict diagnostics honestly |

A corrected worker submission can move unpinned coordinates during correction, so raw-apply continuity is the exact retention assertion. Healthy restart tests compare the last retained frame before restart with a barriered resubmission before further force integration; subsequent integration can move unpinned particles normally.

The new constant introduces no input parsing or exception type. Preserve finite-coordinate validation, owner-thread confinement, request/pin validation, failure retention, failed-engine recovery, asynchronous generation fences, pause/close semantics, and diagnostics. A failed worker frame can retain a previous frame's `idle` flag: always test `failed()` **before** interpreting idle. No failed, unverified, missing-coverage, or non-finite frame can establish acceptance.

## 4. Independent geometry and target examples

Use literal expectations, independent of the new production constant or helper under test. Geometry comparisons here use absolute tolerance `1e-9`; the outcome-improvement tolerance in section 7 is separately `1e-6`.

| Fresh direct content | Candidate independent expectation | Pinned old reference |
| --- | --- | --- |
| Empty projection | Empty finite positions; no force link | Unchanged |
| Empty enclosure, label `wide` | Ring 0; label-sized footprint 80 x 40 | Unchanged |
| One radius-8 node | Ring 0; node at its anchor; leaf footprint **64 x 64** | Unchanged |
| Two radius-8 nodes | Ring **20**; centre distance **40**; edge gap **24**; leaf footprint **104 x 104**, anchor offset (0,0) | Ring 11; centre 22; gap 6; footprint 86 x 86 |
| Four radius-8 nodes | Ring 28.284271247461902; adjacent centre distance 40; edge gap 24; footprint 120.5685424949238 square | Reference measurements come from old code |
| Eight radius-8 nodes | Ring 52.26251859505506; adjacent centre distance 40; edge gap 24; footprint 168.52503719011014 square | Ring 28.744385227280283; adjacent centre 22; gap 6 |
| Mixed three-node ring: `m0 -> m1`, `m0 -> m2`, forward arrows only | Outgoing counts (2,0,0); radii (9.6,8,8); ring 24.941531628991836; every adjacent centre distance 43.2; gaps (25.6,27.2,25.6) in direct order | No assertion that every mixed gap equals 24 |

For the mixed case, use three direct persisted nodes `m0,m1,m2`, in that order, in one parentless emphatic map-root enclosure `mixed-root` on map A. Labels equal IDs; same-map native contributors have occurrence 0 and the specified forward arrows. Use the metadata defaults in section 6 and workspace `00000000-0000-0000-0000-000000001604`. Do not inject a synthetic prominence map in place of the real calculator.

For direct rest-length inspection, freeze two enclosure variants with the same four radius-8 nodes `p0,p1,q0,q1`, all relationships undirected so prominence remains zero:

- **Same-map variant:** map A root `target-root`, no direct nodes, children `left,right` in that order. Each child is subtle, has the root as parent, and contains respectively `p0,p1` and `q0,q1`. The root is emphatic. Node order is `p0,p1,q0,q1`; enclosure order is root,left,right.
- **Cross-map variant:** parentless emphatic roots `left` on map A and `right` on map B; direct nodes respectively `p0,p1` and `q0,q1`; enclosure order left,right and the same node order.

For each variant, add edges in this order: `p0--q0`, `p1--right`, `left--right`. Use distinct native contributor keys for the same-map case (occurrence 0 for sources p0,p1,left); cross-map relationship IDs `00000000-0000-0000-0000-000000016041` through `...016043`, sequence 1 through 3, `UNDIRECTED`, empty unknown XML. Labels equal IDs; map names are `Map A`/`Map B`; graph-group nodes are true. Workspaces are `...001605` and `...001606`. Generation is 1 and all pins/resolutions are empty. These are target tests, not additional first-idle outcome cases.

Both two-node enclosures have model radius 20. Actual registered relationship rests must consequently be **22, 34, 46**, respectively, with crossMap flags all false in the same-map variant and all true in the cross-map variant. Containment rests are **20** for all four direct nodes. The root with no direct nodes has ring 0. Add a node/enclosure and enclosure/enclosure assertion using a singleton/children-only enclosure to pin the zero endpoint-radius rule. A parent with one child enclosure and no direct content has the existing valid zero hierarchy rest.

Also verify recursively expanded footprints in a parent containing both direct content and child enclosures, and at least two top-level map roots. Check the actual non-overlapping packed rectangles and their 8-unit gutter, including anchor offsets. Existing no-content fixtures and their literals remain unchanged. A production-helper-derived expected footprint alone cannot establish the selected preference.

## 5. Internal inspection and falsification

Keep `GraphStreamLayoutEngine` package-private. Place raw-engine/target tests in `org.freeplane.plugin.graph.layout.graphstream`, using its existing package-private `BoundarySizes`, `ForceLink` and `ForceKind` structures. A small test-local reflection helper may read `GraphStreamLayoutEngine.springBox`, its `particles`, and `TypedSpringBox.typedLinks`/`typedParticles`. Return a detached snapshot keyed by semantic node/hull keys and link endpoints, kind, crossMap and restLength. Assertions must fail if an expected particle/link is absent. Do not expose getters or mutation hooks in production, widen module exports, or make private topology/seed classes public.

Required distinct proofs:

- Inspect actual links **registered with the live engine's spring box**, including after stepping, a real topology change, raw reset, command Reset, and failed replacement. Computing a rest again from `BoundarySizes` is not registration evidence.
- Connect registration to consumption through a controlled `TypedSpringBox.addTypedAttraction` experiment in the same test package. With one non-cross-map relationship along +x, particles at (0,0) and (30,0), coefficient 1.0, and no other links, rest 22 produces x contribution **+0.4** to the first particle; rest 40 produces **-0.5**. The difference is **-0.9**. Inspect before integration/clamping so the 0.005 cross-map limit cannot conceal it. Separately use a registered containment link with rest 20 at distance 15, giving **-0.25** instead of the old rest-11 contribution **+0.20**. Use a real `MultiGraph` edge and `TypedSpringBox` particle; call the actual consumer, not a copied force equation.
- Check cross-map links' actual rest values and flags directly, then retain the existing integration tests for the aggregate 0.005 cap. Do not enlarge the cap to make an outcome or consumption test visible.
- For a real synchronization test, begin with direct nodes `u0,u1`; apply/step; then generation 2 adds `u2` and forward native edges `u0->u1`, `u0->u2`. Use `ProjectionDiff.between(before,after)` and assert it is nonempty. All retained node and anchor positions are bit-identical at raw apply; `u2` is deterministic; the updated ring/containment is 24.941531628991836; relationship rests remain 23.6 for both edges, unchanged by this feature. Remove `u2`/one edge at generation 3 and verify obsolete links disappear and current rests/radii are rebuilt. This must not be replaced with the empty-diff fast path.
- Exercise pins in fresh, stepped, update, restart, reset and recovery cases. In particular pin radius-8 nodes at (0,0) and (20,0): keep exact positions even though the 4-unit disc gap conflicts with the preference and 6-unit clearance; require the existing residual/conflict representation. Do not force a zero-residual assertion onto that deliberate conflict fixture.

Meaningful negative controls, in disposable test compilation/output only, must demonstrate that each relevant assertion fails: (a) revert the constant to 6; (b) widen seeds/footprints but leave containment rests old; (c) substitute `PREFERRED_NODE_GAP` for `NodeSeparationProjection.MIN_GAP` in the relationship rest-length term while keeping current endpoint radii, so the registered-rest assertions for 22/34/46 and 23.6 must reject it; (d) ignore `restLength` in attraction; (e) reseed retained particles during real topology synchronization; (f) make healthy restart recreate the engine; (g) make Reset preserve the old engine. Preserve the active worktree and record each broken mechanism and the failing test/assertion. The unchanged implementation failing new literal seed/target tests is expected red evidence, not evidence that a frozen outcome fixture may be changed.

## 6. Frozen first-idle input fixtures

These definitions are frozen before implementation. No random generator, search for favorable IDs, fixture-size reduction, edge removal, prominence override, pin, coefficient change, or per-version adjustment is permitted to make the comparison pass.

Shared input contract:

- Map A = `00000000-0000-0000-0000-000000000001`; map B = `00000000-0000-0000-0000-000000000002`.
- Every node/hull source is `SourceNodeKey.persisted(NodeReference.of(map, PersistedNodeId.of(id)))`; no structural-path sources or coalesced hull endpoints. Each root hull contains exactly its one `EnclosureKey`.
- Use `GraphProjection.projected(1L, orderedNodes, orderedEnclosures, orderedEdges, emptyResolutions, emptyPins)`, so real prominence is computed. Nodes have `SafeNodeLabel.of(id,id)`, `graphGroup=true`, and map name `Map A` or `Map B`.
- Roots are parentless, `mapRoot=true`, `BoundaryTier.EMPHATIC`, have no child enclosures, and have label `A_root` or `B_root`. Each root's direct-node list is the declared node order for that map. Every node belongs to exactly one root.
- Submit `LayoutRequest.of(workspace, projection, ProjectionDiff.between(emptyGeneration0Projection,projection), emptyPins)` to a **new** `LayoutWorker(LayoutCalibration.spikeDefaults())`. The generation-0 projection is a projected graph with all lists empty. Neither factory nor idle policy is substituted.
- Calibration is exactly (1.0,0.30,1.0). The worker's existing metrics remain `AwtGeometryTextMetrics(new Font("Dialog", Font.PLAIN, 12), new FontRenderContext(null,true,true))`. Recomputed measurement geometry uses the same metrics. Do not inject constant text dimensions into the worker. Locale en/US, timezone UTC, Java 21.0.8-zulu, headless for both runs; compare actual label-metric receipts.
- Acceptance zoom = 1.0. There is no auto-fit transform in numeric evidence. Every node has scale 1.0 and radius 8.0: assert these actual values rather than assuming them.

| ID | Workspace ID | Ordered node list | Ordered enclosure list | Ordered relationships and actual outgoing counts | Qualifying enclosures |
| --- | --- | --- | --- | --- | --- |
| `NR8` | `00000000-0000-0000-0000-000000001601` | A0,A1,A2,A3,A4,A5,A6,A7 on A | A_root | None; all outgoing counts 0 | A_root, 8 nodes |
| `SM8` | `00000000-0000-0000-0000-000000001602` | A0,A1,A2,A3,A4,A5,A6,A7 on A | A_root | Forward native connectors A0->A1,A1->A2,A2->A3,A3->A4,A4->A5,A5->A6,A6->A7,A7->A0, in that order; each outgoing count 1 | A_root, 8 nodes |
| `XM4x2` | `00000000-0000-0000-0000-000000001603` | A0,A1,A2,A3 on A, then B0,B1,B2,B3 on B | A_root,B_root | One forward graph relationship A2->B0; only A2 has outgoing count 1; the other seven are 0 | A_root and B_root, 4 nodes each |

For each native connector use occurrence 0, `arrowAtSource=false`, `arrowAtTarget=true`, and all three labels empty. For the cross-map relationship use ID `00000000-0000-0000-0000-000000016031`, sequence 1, `FORWARD`, empty unknown XML. Construct a single `EdgeContributor` for each edge and `ProjectedEdgeKey.of(sourceEndpoint,targetEndpoint)`; preserve list insertion order even when the edge key canonicalizes endpoint order. A same-map `GraphRelationshipRecord` would be invalid; use native connectors there.

The exact observer in appendix A materializes these fixtures using existing builders. It emits an input manifest with IDs, ordering, labels, contributors, prominence, calibration, metric dimensions, pins, bounds and idle policy. Node rows end with the explicit map UUID and persisted node ID; enclosure rows end with the map UUID and root label. Direct-membership and edge rows retain canonical keys. The comparator derives exact node/map/enclosure, anchor and edge coverage from that manifest and cross-checks it against the frozen fixture definitions, including on every diagnostic frame. Baseline and candidate manifest bytes must match. Worker outputs and version identifiers belong in separate files and cannot be included in that equality comparison.

## 7. First-idle endpoint, budgets, population, and predicate

For **each version and fixture separately**, inspect the corrected submission result and then sequential corrected `LayoutWorker.step()` results. Accept the **first** non-failed, verified frame with `idle().idle()==true` under the unchanged production idle policy. Never call raw engine idle an application stopping frame, reset idle counters, change thresholds, ignore earlier idle, or continue toward a preferred endpoint.

Submission is worker step count 0. At most **2000 calls to `step()`** follow it; a first idle returned by call 2000 is within the count limit. Record this explicit count and `LayoutFrame.stepIndex()` separately: the raw engine uses index 0 for both initial apply and its first step, so frame index is not an adequate count.

Use one monotonic 30-second deadline per fixture/version, starting immediately before construction of its fresh worker and submission. Request submission, engine initialization, queueing, corrections, future waits, intervening frame inspection/collection, and endpoint validation share that deadline. Before each wait use only the remaining time; do not grant 30 seconds to each future. The Java observer must validate exact coverage, finite all-pairs nearest gaps, minima and medians, relationship endpoint measurements and required safety before recording `validationElapsedNanos`; it must recheck the deadline after writing the receipts. Python independently recomputes these statistics and validates the receipts later. Compiling, dependency resolution, input-manifest preparation, and the separately identified post-idle experiment are outside this measurement. A result delivered/validated after 30 seconds fails even if its idle flag is true. Timeout, failed frame, exception, missing coverage, invalid number, exhausted step count, or absence of idle blocks acceptance; keep the last observed frame as diagnostic only.

For each enclosure with at least two **direct** nodes, and for every direct node `i`, compute:

```text
nearestGap(i) = min over all other direct nodes j in that same enclosure:
                hypot(x_i-x_j, y_i-y_j) - renderedRadius(i) - renderedRadius(j)
```

This is an all-pairs search within each qualifying enclosure, independently of edges, adjacency on the seed ring, edge contributors, or how far nodes moved. Exclude self, ancestors, nodes belonging only to child enclosures, anchors and nodes in other enclosures. Keep duplicate nearest-neighbor values: the median population has exactly one value for each direct node, not one per distinct pair. Do not clip negative gaps.

Use radii from `GraphGeometryEngine.computeHulls(...).nodes()`, cross-checked against the frozen prominence and base radius. Use `Math.hypot`/`math.hypot`, not squared-distance arithmetic that can overflow. Check coordinates, their differences, distances, radii and gaps for finiteness; reject an unrepresentable difference rather than accepting infinity. Sort numeric values ascending; the odd median is the middle value and the even median is `0.5*lowerMiddle + 0.5*upperMiddle`. Do not round before comparison.

For **each of the four qualifying enclosure results** (NR8 A, SM8 A, XM4x2 A, XM4x2 B), require:

```text
delta = candidateMedianAtItsOwnFirstIdle - baselineMedianAtItsOwnFirstIdle
PASS  iff delta >= 6.0 - 1e-6
```

Apply tolerance once to the difference. Do not average across enclosures or cases. Report each version's minimum and median, every per-node nearest gap, delta, explicit worker step count, raw frame index, elapsed nanoseconds, RMS, maximum motion and stable-frame count. Report relationship-endpoint centre distances and rendered disc-edge gaps separately for every relationship in SM8/XM4x2; target tests independently establish that relationship model rests are unchanged at 22 units and that the preferred 24-unit gap governs content rings. Anchor/hull distances cannot replace this population or predicate.

Retain safety checks on first-idle frames: exact node/anchor coverage and finite geometry; verified node residuals; collision pair detection; boundary residual/detection consistency and containment; zero unpinned residuals in these small fixtures; complete nonexhausted edge diagnostics; actual painted-path crossing checks. The observer records independent boundary/node and chord checks plus painted crossings. Existing detailed boundary/pin/edge routing assertions remain required in their regression suites. A larger preference does not waive any existing safety or motion/performance assertion.

After freezing first idle, deliberately request **the next 100 sequential worker frames** from the same worker, without reset/restart/resubmission. Give this diagnostic window its own monotonic 30-second deadline; identify offsets 1..100 and absolute step counts. Record per-node gaps, per-enclosure minima/medians, idle flags, RMS/max/stable count, correction deltas/displacements and residual/budget/crossing diagnostics. Preserve any subsequent movement or loss of idle in the evidence. No improvement threshold is imposed on the hypothetical window; safety assertions remain visible. Incomplete/failed diagnostic collection prevents declaring the evidence package complete but cannot replace or improve a failed first-idle verdict. The application normally stops stepping after first IDLE; these are explicitly **post-idle diagnostic frames**.

Do not use the old `ClusterProximityShould.settle` endpoint: it advances another 200 frames after first idle and then measures a further 1000-frame anchor window. Those existing regression/packing diagnostics retain their own meaning.

## 8. Executable baseline comparison and provenance

Appendices A and B specify a self-contained, test-only observer and launcher/comparator. They use the established pattern of a Java diagnostic main on the module's runtime classpath, Gradle's existing compilation, and temporary output. They introduce no production branch or legacy engine. During implementation these may be materialized as:

```text
freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/InitialSpacingProbe.java
freeplane_plugin_graph/src/test/scripts/compare_initial_spacing.py
```

The appendix blocks can also be extracted to an external evidence directory and run without adding harness files to production. The launcher obtains the actual pinned old tree with `git archive`, compiles that tree in a disposable directory, and compiles the **same observer bytes** against each version's main runtime classpath. It never substitutes a hand-transcribed old formula, copies old production classes into the live candidate, or compares a candidate run with itself. The candidate may be committed or dirty: record HEAD and the exact tracked patch, plus hashes of current main sources and observer bytes. Include the clean source-state receipts, JVM/Gradle versions, dependency checksums, full commands, stdout/stderr, exit status and unrounded TSV files in the acceptance package.

For executable extraction from the repository root (after implementation, not during this drafting task):

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
export JAVA_TOOL_OPTIONS=-Djava.awt.headless=true
python3 - <<'PY'
from pathlib import Path
import re
import tempfile
import subprocess
spec = Path('docs/superpowers/specs/2026-10-16-graph-initial-spacing-spec.md').read_text()
out = Path(tempfile.mkdtemp(prefix='graph-initial-spacing-'))
for marker, name, lang in [('spacing-probe-java', 'InitialSpacingProbe.java', 'java'),
                           ('spacing-compare-python', 'compare_initial_spacing.py', 'python')]:
    match = re.search(r'<!-- ' + marker + r' -->\s*```' + lang + r'\n(.*?)\n```', spec, re.S)
    if not match:
        raise SystemExit('Missing executable appendix: ' + marker)
    (out / name).write_text(match.group(1) + '\n')
print('Evidence directory:', out, flush=True)
subprocess.run(['python3', str(out / 'compare_initial_spacing.py'),
                str(Path.cwd()), str(out / 'InitialSpacingProbe.java'), str(out / 'results')], check=True)
PY
```

Record the printed external evidence location. The comparator exits nonzero on either run failure, input mismatch, missing/duplicate result, deadline/count failure, incomplete diagnostic window, invalid safety receipt, or any enclosure delta below the frozen margin. Preserve failures; no fallback baseline numbers or automatic fixture retry/selection is allowed. A failed comparison is a material acceptance blocker to report to design review. A rerun after an identified environmental correction must retain the original receipt and explain the correction.

The harness has a 65-second external process watchdog per fixture/version to bound failed JVM cleanup plus the two 30-second phases; **this does not extend the first-idle deadline**. A first-idle future timeout exits the probe process without waiting indefinitely in `LayoutWorker.close()`; normal success closes the worker. The watchdog kills the child JVM on a cleanup stall. Build/dependency failures are tooling failures, not spacing measurements.

## 9. Verification and evidence gates

Required tests are JUnit 4/AssertJ, Java 8 source/bytecode target. Extend the existing `BoundaryFootprintShould`, `TypedForcesShould`, `ClusterPackingShould`, `ClusterProximityShould`, raw-engine and lifecycle tests where appropriate; a focused `InitialSpacingShould` can own the independent numeric/registration tests. The observer itself is evidence support, not a replacement for those assertions.

Preserve the existing `<8.0` one-step movement checks, cross-map cap, 200-enclosure **<=3.0-second** submission assertion and its **5-second** future await. Keep existing safety/coverage/pin/determinism and performance thresholds. Only spacing-derived numeric goldens may change, supported by old/new measured receipts and a derivation explaining the spacing dependency. For example, the `BoundaryFootprintShould` literals 11/86 become 20/104; singleton 64 remains 64. Update obsolete comments tying preferred rings to collision MIN_GAP. No blanket golden regeneration or accepting any changed output is allowed.

Three measured regression expectations are additionally authorized, each with receipts:

1. `TypedForcesShould.TWO_MAP_FIRST_IDLE_STEP` becomes the re-measured candidate value (measured 3455), and that test's observation bound becomes **6000** steps in place of 2000, because the widened ring lengthens convergence on the cross-map fixture while remaining convergent. This raises a test observation bound only; the production idle policy and every safety/performance threshold are unchanged, and section 7's 2000-call first-idle budget for the frozen outcome fixtures is unchanged.
2. That test's post-idle hold envelope becomes the measured candidate envelope (measured worst RMS 0.291052 and worst maximum 0.990234 over 100 frames, rounding up to 0.30 / 1.00). It remains a bounded smoke envelope, not a stability guarantee, and the rationale comment must be restated from measured receipts.
3. `ClusterProximityShould.initialFrameThroughWorkerKeepsSeedsWithoutPins` becomes residual-tolerant for fixture D: the widened ring removes the exact `MIN_GAP` tie that the old assertion depended on, so `EdgeConflictCorrection` may leave one residual edge conflict. The frame must still be `verified` with `residualViolations == 0`, `hullResidual == 0`, `boundaryVerified`/`boundaryCovered` true, and no violating node-separation pairs. No tolerance, threshold, or production assertion may be weakened to obtain this.

The obsolete comment at `ClusterProximityShould.java:506-513` referring to the `MIN_GAP tie produced by contentRingRadius` becomes `preferred-gap tie produced by contentRingRadius`.

Run from the verified candidate root, using the installed `gradle`, Java 21.0.8-zulu, and the repository's required build escalation where the execution harness supports it. Every non-UI command below inherits the same headless environment:

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
export JAVA_TOOL_OPTIONS=-Djava.awt.headless=true
java -version
gradle --version
gradle :freeplane_plugin_graph:test --tests '*InitialSpacingShould' --tests '*BoundaryFootprintShould' --tests '*ClusterPackingShould' --tests '*TypedForcesShould' --tests '*ClusterProximityShould' --tests '*AnchorSeparationShould' --tests '*LayoutWorkerShould' --tests '*LayoutSettleLoopShould' --tests '*GraphUpdateCoordinatorShould' --console=plain -PTestLoggingFull
gradle :freeplane_plugin_graph:check --console=plain -PTestLoggingFull
gradle :freeplane_plugin_graph:graphPerformanceDiagnostic -PgraphStrictPerformance --console=plain
gradle :freeplane_plugin_graph:graphOsgiSmoke :freeplane_plugin_graph:freeplaneLaunchSmoke --console=plain
gradle :freeplane:compileJava test --console=plain -PTestLoggingFull
```

Run the baseline-comparison command in section 8 as a separate required gate. Preserve the existing strict performance ledger, operation/process budgets, dependency checksums and Java-8 bundle validation. Do not claim a performance gate measured on a settled worker frame unless that gate's actual fixture preconditions are established. An environmental or unrelated suite failure must be classified and resolved/reviewed honestly; it does not establish a candidate pass.

The recorded pre-change 26/26 headless two-class run is **historical baseline evidence only**: the original 25/26 run and isolated rerun timed out with unauthorized-X11/font initialization symptoms; adding only `JAVA_TOOL_OPTIONS=-Djava.awt.headless=true` passed all 26 and measured the unchanged 200-enclosure submission at 0.565 seconds. Keep the original failed receipts, the successful rerun, and the unchanged 3-second assertion/5-second await distinct. This history supplies neither first-idle baseline numbers for section 6 nor candidate verification.

## 10. Real-application first-IDLE screenshots

Capture the actual Freeplane application with its installed candidate graph plugin, at zoom 1.0, a fixed viewport and matching baseline/candidate input files, font/Look-and-Feel and pan transform. Use a valid X display (a recorded Xvfb display is acceptable); remove forced headless for the UI process. Existing `graphUiEvidence` is a headless synthetic evidence task and alone cannot establish real-application first-IDLE behavior. Use the installed runtime/launch harness and existing canvas listeners/diagnostic scripting; do not add a production instrumentation API.

Required captures are an initial displayed worker publication and its exact first-IDLE publication for the direct-node case, a connected case, and a nested multi-map case, plus pin behavior. Use the section 6 cases where the actual live projection can be asserted to equal their manifest. For additional UI coverage freeze these explicit input shapes in the evidence manifest before running them:

- **Nested/multi-map:** map A root `A_root` has no direct nodes, children `A_left,A_right` in that order; each child is subtle and has two direct nodes `L0,L1` / `R0,R1`. Map B root `B_root` is emphatic with direct nodes `B0,B1,B2,B3`. Root order A_root,B_root and node order L0,L1,R0,R1,B0,B1,B2,B3. One forward cross-map relationship L0->B0. Labels equal IDs, remaining defaults from section 6; workspace `...001607`, relationship `...016071` sequence 1. Record actual source file paths/hashes and live projected inputs.
- **Pins:** the nested/multi-map shape with workspace `...001608`, one active pin L0 at (120,-40). Capture its exact persisted coordinates through initial, first IDLE, healthy Restart and Reset. The deliberate two-pin conflict of section 5 remains a numeric safety/residual test, and can be an additional clearly labeled UI capture.

For each image, record in a sibling machine-readable receipt: image SHA-256; phase (`initial-publication` or `first-idle`, never `post-idle`); source revision and patch hash; installed plugin/bundle hashes; input/workspace files and hashes; projection/input-manifest hash and generation; run/worker identity; explicit step-call count, raw frame index, full idle measurement and failed/verified status; corrected node/anchor positions and geometry hash; viewport dimensions, display scale, zoom and pan; active pin coordinates; image capture time and bounds.

Observe the canvas's first successful publication and first `CanvasState.status()==IDLE` for that run/generation. Establish that its `layout()` is the first qualifying corrected worker frame in that run's trace. Paint that exact live canvas state on the EDT and capture the real application window through the screen-capture/Robot mechanism before another state can replace it; assert state identity/position hash before and after capture. Initial-publication capture may use a test-only listener/EDT barrier so the next publication cannot race the capture. First-IDLE publication stops the normal run naturally. Do not fabricate idle, use a selected late trace frame, replay a different frame onto an offscreen canvas, or press Restart to obtain a prettier stopped state. Reject mismatched/raced captures and preserve the reason.

The initial **displayed** worker publication may already include corrections. Label it accordingly; exact raw seed gaps are proven by raw-engine tests, not inferred from a screen capture. Post-idle experiment images, if supplied, must be separately labeled diagnostics and cannot satisfy this requirement. Schematic mockups remain design material and must not be relabeled application evidence.

## 11. Acceptance record and unresolved outcome risk

The implementation evidence record must map each contract to receipts: production scope diff; independent seed/footprint values; actual registered/consumed targets for all endpoint/map variants; topology/lifecycle/pin checks; negative controls; four first-idle enclosure comparisons; complete separate 100-frame windows; unchanged safety/performance limits; plugin/project commands; installed-runtime real-app images and their traces.

Specification-tooling validation has run the original appendix observer against both the archived baseline and unchanged integration production source. Both observers compiled for Java 8; NR8/SM8/XM4x2 reached first idle in 15/13/24 steps, each with a complete 100-frame diagnostic window. All four unchanged medians had delta 0.0 and were rejected as expected. Those receipts live outside Git at `reports/spec-materialization-1/` under the PM run root; they establish baseline/tooling evidence only.

The revised appendix observer/comparator was separately materialized and run with the same unchanged production inputs (`reports/spec-materialization-2/`). Both Java-8 observer compilations and all six fixture runs passed their collection/coverage/safety checks; the comparator again rejected exactly the four zero-delta outcomes. Twenty-one comparator controls passed, including wrong node IDs, missing B-root diagnostic membership, missing/duplicate anchor and edge rows/files, missing offsets, non-finite values, altered statistics and deadline boundaries. A disposable observer mutant delayed only first-idle endpoint validation by 31 seconds: an idle future arrived at step 15, but validation completed after 31.4 seconds and the probe correctly exited with the first-idle deadline failure before any diagnostic frames. The scripts, complete TSVs and immutable experiment receipts remain in that external evidence directory. These are specification-tooling controls, not candidate feature verification.

Report every implementation gate as PASS, FAIL or NOT RUN with its artifact path. Candidate first-idle values remain NOT RUN. The approved parameter change's ability to achieve every 6-unit improvement remains an empirical outcome question, not a drafting assumption. If any required fixture fails to converge, improve or remain safe, keep its exact inputs and failed receipts and return the finding to design review. Do not weaken the frozen acceptance contract or expand production logic under this specification.

## Appendix A. Reference observer source

This code uses only existing production interfaces/package seams, not the preferred-gap constant. The emitted raw positions permit independent recomputation. The `frames.tsv` first-idle row is immutable evidence; later rows have phase `post-idle`. For diagnosis retain every pre-idle frame too. Correction displacements/deltas are included for the diagnostic window.

<!-- spacing-probe-java -->
```java
package org.freeplane.plugin.graph.layout;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.geom.Dimension2D;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.freeplane.plugin.graph.geometry.*;
import org.freeplane.plugin.graph.projection.*;
import org.freeplane.plugin.graph.projection.input.*;
import org.freeplane.plugin.graph.workspace.model.*;

public final class InitialSpacingProbe {
    private static final long BUDGET = TimeUnit.SECONDS.toNanos(30L);
    private static final MapReferenceId A = MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    private static final MapReferenceId B = MapReferenceId.of("00000000-0000-0000-0000-000000000002");
    private static final GeometryTextMetrics METRICS = new AwtGeometryTextMetrics(
        new Font("Dialog", Font.PLAIN, 12), new FontRenderContext(null, true, true));

    private static NodeReference ref(MapReferenceId map, String id) {
        return NodeReference.of(map, PersistedNodeId.of(id));
    }
    private static ProjectedNodeKey key(MapReferenceId map, String id) {
        return ProjectedNodeKey.of(SourceNodeKey.persisted(ref(map, id)));
    }
    private static EnclosureHullKey hull(MapReferenceId map, String id) {
        return EnclosureHullKey.of(Collections.singletonList(
            EnclosureKey.of(SourceNodeKey.persisted(ref(map, id)))));
    }
    private static String id(ProjectedNodeKey key) {
        return key.source().persistedReference().get().nodeId().value();
    }
    private static void row(PrintWriter out, Object... values) {
        for (int i = 0; i < values.length; i++) {
            if (i != 0) out.print('\t');
            out.print(values[i]);
        }
        out.println();
    }
    private static PrintWriter writer(Path out, String file) throws Exception {
        return new PrintWriter(Files.newBufferedWriter(out.resolve(file), StandardCharsets.UTF_8));
    }
    private static GraphProjection fixture(String name) {
        if (!Arrays.asList("NR8", "SM8", "XM4x2").contains(name))
            throw new IllegalArgumentException("Unknown frozen fixture " + name);
        List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        List<ProjectedEdge> edges = new ArrayList<ProjectedEdge>();
        int maps = name.equals("XM4x2") ? 2 : 1;
        int count = maps == 2 ? 4 : 8;
        for (int m = 0; m < maps; m++) {
            MapReferenceId map = m == 0 ? A : B;
            String prefix = m == 0 ? "A" : "B";
            List<ProjectedNodeKey> direct = new ArrayList<ProjectedNodeKey>();
            for (int n = 0; n < count; n++) {
                String node = prefix + n;
                ProjectedNodeKey k = key(map, node);
                direct.add(k);
                nodes.add(ProjectedNode.of(k, SafeNodeLabel.of(node, node), "Map " + prefix, true));
            }
            String root = prefix + "_root";
            EnclosureHullKey h = hull(map, root);
            enclosures.add(ProjectedEnclosure.of(h, h.endpointKeys(),
                Collections.singletonList(SafeNodeLabel.of(root, root)), "Map " + prefix,
                Optional.<EnclosureHullKey>empty(), direct,
                Collections.<EnclosureHullKey>emptyList(), true, BoundaryTier.EMPHATIC));
        }
        if (name.equals("SM8")) {
            for (int n = 0; n < 8; n++) {
                ProjectedEndpointKey from = ProjectedEndpointKey.ofNode(key(A, "A" + n));
                ProjectedEndpointKey to = ProjectedEndpointKey.ofNode(key(A, "A" + ((n + 1) % 8)));
                ConnectorDescriptor d = ConnectorDescriptor.of(SourceNodeKey.persisted(ref(A, "A" + n)),
                    ref(A, "A" + ((n + 1) % 8)), false, true, "", "", "");
                edges.add(ProjectedEdge.of(ProjectedEdgeKey.of(from, to), Collections.singletonList(
                    EdgeContributor.nativeConnector(ConnectorSnapshot.of(0, d), from, to))));
            }
        }
        if (name.equals("XM4x2")) {
            ProjectedEndpointKey from = ProjectedEndpointKey.ofNode(key(A, "A2"));
            ProjectedEndpointKey to = ProjectedEndpointKey.ofNode(key(B, "B0"));
            GraphRelationshipRecord r = GraphRelationshipRecord.of(RelationshipId.of(
                "00000000-0000-0000-0000-000000016031"), 1L, ref(A, "A2"), ref(B, "B0"),
                RelationshipDirection.FORWARD, Collections.<UnknownXml>emptyList());
            edges.add(ProjectedEdge.of(ProjectedEdgeKey.of(from, to), Collections.singletonList(
                EdgeContributor.graphRelationship(r, from, to))));
        }
        return GraphProjection.projected(1L, nodes, enclosures, edges,
            Collections.<RelationshipResolution>emptyList(), Collections.<PinProjection>emptyList());
    }
    private static WorkspaceId workspace(String name) {
        String suffix = name.equals("NR8") ? "1601" : name.equals("SM8") ? "1602" : "1603";
        return WorkspaceId.of("00000000-0000-0000-0000-00000000" + suffix);
    }
    private static void manifest(Path out, String name, GraphProjection p) throws Exception {
        try (PrintWriter w = writer(out, "inputs.tsv")) {
            row(w, "fixture", name, "generation", 1, "workspace", workspace(name).value());
            row(w, "calibration", 1.0, 0.30, 1.0, "zoom", 1.0, "pins", 0, "resolutions", 0);
            row(w, "metrics", "Dialog", "PLAIN", 12, "identityFRC", true, true, "locale", "en_US", "UTC");
            row(w, "idle", 8, 0.05, 0.10, "stepLimit", 2000, "phaseNanos", BUDGET, "diff", "0->1");
            for (int i = 0; i < p.nodes().size(); i++) {
                ProjectedNode n = p.nodes().get(i);
                NodeProminence v = p.prominence().get(n.key());
                int outgoing = name.equals("SM8") || name.equals("XM4x2") && id(n.key()).equals("A2") ? 1 : 0;
                if (v.scale() != 1.0 || v.visibleOutgoingTargets() != outgoing)
                    throw new AssertionError("Frozen prominence changed");
                row(w, "node", i, CanonicalKeys.node(n.key()), n.label().displayText(), n.mapName(),
                    n.graphGroup(), v.visibleOutgoingTargets(), v.scale(), "radius", 8.0,
                    n.key().mapReferenceId().value(), id(n.key()));
            }
            for (int i = 0; i < p.enclosures().size(); i++) {
                ProjectedEnclosure e = p.enclosures().get(i);
                row(w, "enclosure", i, CanonicalKeys.hull(e.hullKey()), e.mapName(), e.mapRoot(),
                    e.boundaryTier(), "parent=none", "children=none", e.labels().get(0).displayText(),
                    e.mapReferenceId().value(), e.labels().get(0).displayText());
                for (int j = 0; j < e.directNodes().size(); j++)
                    row(w, "direct", i, j, CanonicalKeys.node(e.directNodes().get(j)));
                Dimension2D d = METRICS.measure(e.labels().get(0).displayText(), e.boundaryTier());
                row(w, "text", i, d.getWidth(), d.getHeight());
            }
            for (int i = 0; i < p.edges().size(); i++) {
                ProjectedEdge e = p.edges().get(i);
                EdgeContributor c = e.contributors().get(0);
                row(w, "edge", i, CanonicalKeys.edge(e.key()), c.key(),
                    CanonicalKeys.endpoint(c.projectedSource()), CanonicalKeys.endpoint(c.projectedTarget()),
                    c.arrowAtSource(), c.arrowAtTarget(), "labels=empty", "unknownXml=empty",
                    c.graphRelationship().isPresent() ? "sequence=1" : "occurrence=0");
            }
            if (w.checkError()) throw new IllegalStateException("Input receipt write failed");
        }
    }
    private static LayoutFrame await(CompletionStage<LayoutFrame> stage, long deadline) throws Exception {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0L) throw new TimeoutException("Phase deadline before wait");
        LayoutFrame frame = stage.toCompletableFuture().get(remaining, TimeUnit.NANOSECONDS);
        if (System.nanoTime() - deadline > 0L) throw new TimeoutException("Phase deadline after wait");
        if (frame == null || frame.failed() || !frame.verified())
            throw new AssertionError("Failed or unverified corrected worker frame");
        return frame;
    }
    private static double gap(NodeGeometry a, NodeGeometry b) {
        if (a == null || b == null || !Double.isFinite(a.center().x()) || !Double.isFinite(a.center().y())
                || !Double.isFinite(b.center().x()) || !Double.isFinite(b.center().y())
                || !Double.isFinite(a.radius()) || !Double.isFinite(b.radius())
                || a.radius() <= 0.0 || b.radius() <= 0.0)
            throw new AssertionError("Missing or non-finite node geometry");
        double dx = a.center().x() - b.center().x();
        double dy = a.center().y() - b.center().y();
        double distance = Math.hypot(dx, dy);
        double value = distance - a.radius() - b.radius();
        if (!Double.isFinite(dx) || !Double.isFinite(dy) || !Double.isFinite(distance) || !Double.isFinite(value))
            throw new AssertionError("Non-finite gap");
        return value;
    }
    private static double[] nearestGaps(ProjectedEnclosure e, GraphGeometry g) {
        if (e.directNodes().size() < 2) throw new AssertionError("Changed frozen population");
        double[] nearest = new double[e.directNodes().size()];
        for (int i = 0; i < nearest.length; i++) {
            nearest[i] = Double.POSITIVE_INFINITY;
            for (int j = 0; j < nearest.length; j++) {
                if (i != j) nearest[i] = Math.min(nearest[i],
                    gap(g.nodes().get(e.directNodes().get(i)), g.nodes().get(e.directNodes().get(j))));
            }
            if (!Double.isFinite(nearest[i])) throw new AssertionError("Invalid nearest gap");
        }
        return nearest;
    }
    private static void finiteMotion(double... values) {
        for (double value : values)
            if (!Double.isFinite(value) || value < 0.0) throw new AssertionError("Invalid motion diagnostic");
    }
    private static void emit(GraphProjection p, LayoutFrame f, String phase, int ordinal, int steps,
            long started, PrintWriter frames, PrintWriter nodes, PrintWriter anchors, PrintWriter edges) {
        Set<ProjectedNodeKey> nodeKeys = new LinkedHashSet<ProjectedNodeKey>();
        for (ProjectedNode n : p.nodes()) nodeKeys.add(n.key());
        Set<EnclosureHullKey> hullKeys = new LinkedHashSet<EnclosureHullKey>();
        for (ProjectedEnclosure e : p.enclosures()) hullKeys.add(e.hullKey());
        if (!f.positions().nodes().keySet().equals(nodeKeys) || !f.positions().anchors().keySet().equals(hullKeys))
            throw new AssertionError("Exact position coverage mismatch");
        GraphGeometry g = new GraphGeometryEngine().computeHulls(p, f.positions(), METRICS);
        if (!g.nodes().keySet().equals(nodeKeys) || !g.hulls().keySet().equals(hullKeys))
            throw new AssertionError("Exact geometry coverage mismatch");
        Set<ProjectedEndpointKey> visible = ProjectedEndpointVisibility.visibleEndpoints(p.nodes(), p.enclosures());
        EdgeConflictCorrection.Detection conflicts = EdgeConflictCorrection.detectConflicts(p, f.positions(), g, visible);
        List<EdgePath> paths = new ArrayList<EdgePath>(new EdgeRouter().route(p, f.positions(), g, visible).byEdge().values());
        int painted = 0;
        for (int i = 0; i < paths.size(); i++)
            for (int j = i + 1; j < paths.size(); j++)
                if (EdgeRouter.properlyCross(paths.get(i), paths.get(j))) painted++;
        int boundary = new BoundaryInvariants().detect(p, g).size();
        int collision = NodeSeparationProjection.violatingPairs(p, f.positions()).size();
        for (ProjectedEnclosure e : p.enclosures()) {
            double[] nearest = nearestGaps(e, g);
            double[] sorted = nearest.clone();
            Arrays.sort(sorted);
            int mid = sorted.length / 2;
            double median = sorted.length % 2 == 0 ? 0.5 * sorted[mid - 1] + 0.5 * sorted[mid] : sorted[mid];
            if (!Double.isFinite(median)) throw new AssertionError("Non-finite median");
            for (int i = 0; i < e.directNodes().size(); i++) {
                ProjectedNodeKey k = e.directNodes().get(i);
                NodeGeometry n = g.nodes().get(k);
                if (n == null || n.radius() != 8.0) throw new AssertionError("Missing node or changed radius");
                row(nodes, phase, ordinal, k.mapReferenceId().value(), id(k),
                    e.labels().get(0).displayText(), n.center().x(), n.center().y(), n.radius(),
                    nearest[i], sorted[0], median);
            }
            LayoutPoint a = f.positions().anchors().get(e.hullKey());
            if (a == null || !Double.isFinite(a.x()) || !Double.isFinite(a.y()))
                throw new AssertionError("Missing or non-finite anchor");
            row(anchors, phase, ordinal, e.mapReferenceId().value(), e.labels().get(0).displayText(), a.x(), a.y());
        }
        for (ProjectedEdge e : p.edges()) {
            NodeGeometry a = g.nodes().get(e.first().node().get());
            NodeGeometry b = g.nodes().get(e.second().node().get());
            row(edges, phase, ordinal, CanonicalKeys.edge(e.key()), gap(a, b) + a.radius() + b.radius(), gap(a, b));
        }
        BoundarySeparationDiagnostics b = f.boundaryDiagnostics();
        EdgeConflictDiagnostics e = f.edgeConflictDiagnostics();
        finiteMotion(f.idle().rms(), f.idle().max(), b.displacementRms(), b.displacementMax(),
            b.deltaRms(), b.deltaMax());
        boolean endpointSafe = !f.failed() && f.verified() && b.boundaryVerified() && b.boundaryCovered()
            && f.residualViolations() == 0 && b.hullResidualViolations() == 0 && e.conflictsResidual().isEmpty()
            && !e.verificationBudgetExhausted() && !e.indexBudgetExhausted() && !e.detectionTruncated()
            && boundary == 0 && collision == 0 && conflicts.crossings() == 0
            && conflicts.edgeOverNodes() == 0 && painted == 0;
        if (phase.equals("first-idle")) endpointSafe &= f.idle().idle()
            && f.idle().consecutiveStableFrames() >= 8 && f.idle().rms() <= 0.05 && f.idle().max() <= 0.10;
        long validationElapsed = System.nanoTime() - started;
        row(frames, phase, ordinal, steps, f.stepIndex(), validationElapsed, f.failed(),
            f.verified(), f.idle().idle(), f.idle().consecutiveStableFrames(), f.idle().rms(), f.idle().max(),
            f.residualViolations(), b.hullResidualViolations(), b.boundaryVerified(), b.boundaryCovered(),
            e.conflictsResidual().size(), e.verificationBudgetExhausted(), e.indexBudgetExhausted(),
            e.detectionTruncated(), boundary, collision, conflicts.crossings(), conflicts.edgeOverNodes(), painted,
            b.displacementRms(), b.displacementMax(), b.deltaRms(), b.deltaMax(), endpointSafe);
        frames.flush(); nodes.flush(); anchors.flush(); edges.flush();
        if (frames.checkError() || nodes.checkError() || anchors.checkError() || edges.checkError())
            throw new IllegalStateException("Measurement receipt write failed");
        if (!phase.equals("pre-idle") && !endpointSafe) throw new AssertionError("Unsafe endpoint; see frame receipt");
    }
    private static void run(String name, Path out) throws Exception {
        Files.createDirectories(out);
        GraphProjection p = fixture(name);
        manifest(out, name, p);
        GraphProjection empty = GraphProjection.projected(0L, Collections.<ProjectedNode>emptyList(),
            Collections.<ProjectedEnclosure>emptyList(), Collections.<ProjectedEdge>emptyList(),
            Collections.<RelationshipResolution>emptyList(), Collections.<PinProjection>emptyList());
        try (PrintWriter frames = writer(out, "frames.tsv"); PrintWriter nodes = writer(out, "nodes.tsv");
             PrintWriter anchors = writer(out, "anchors.tsv"); PrintWriter edges = writer(out, "edges.tsv")) {
            row(frames, "phase", "ordinal", "steps", "engineStep", "validationElapsedNanos", "failed", "verified",
                "idle", "stable", "rms", "max", "nodeResidual", "hullResidual", "boundaryVerified", "boundaryCovered",
                "edgeResidual", "verificationExhausted", "indexExhausted", "detectionTruncated", "actualBoundary",
                "actualCollision", "chordCrossings", "edgeOverNodes", "paintedCrossings", "correctionRms",
                "correctionMax", "correctionDeltaRms", "correctionDeltaMax", "endpointSafe");
            row(nodes, "phase", "ordinal", "map", "node", "enclosure", "x", "y", "radius",
                "nearestGap", "enclosureMinimum", "enclosureMedian");
            row(anchors, "phase", "ordinal", "map", "enclosure", "x", "y");
            row(edges, "phase", "ordinal", "edge", "centreDistance", "discGap");
            long started = System.nanoTime();
            LayoutWorker worker = new LayoutWorker(LayoutCalibration.spikeDefaults());
            // On failure main exits the JVM; do not block forever closing a stalled worker.
            LayoutFrame f = await(worker.submit(LayoutRequest.of(workspace(name), p,
                ProjectionDiff.between(empty, p), Collections.<PinProjection>emptyList())), started + BUDGET);
            int steps = 0;
            while (true) {
                emit(p, f, f.idle().idle() ? "first-idle" : "pre-idle", steps, steps, started,
                    frames, nodes, anchors, edges);
                if (System.nanoTime() - started > BUDGET) throw new TimeoutException("First-idle validation deadline");
                if (f.idle().idle()) break;
                if (steps == 2000) throw new AssertionError("No first idle within 2000 worker steps");
                steps++;
                f = await(worker.step(), started + BUDGET);
            }
            long diagnosticStarted = System.nanoTime();
            for (int offset = 1; offset <= 100; offset++) {
                f = await(worker.step(), diagnosticStarted + BUDGET);
                emit(p, f, "post-idle", offset, steps + offset, diagnosticStarted, frames, nodes, anchors, edges);
                if (System.nanoTime() - diagnosticStarted > BUDGET)
                    throw new TimeoutException("Post-idle diagnostic deadline");
            }
            worker.close();
        }
    }
    public static void main(String[] args) {
        try {
            if (args.length != 2) throw new IllegalArgumentException("fixture output-directory");
            run(args[0], Paths.get(args[1]));
        }
        catch (Throwable failure) {
            failure.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
```

## Appendix B. Reference launcher and comparator

Requires Python 3.12+ standard library, `git`, `gradle`, and the selected Java installation. Run with three arguments: candidate root, observer Java source, and a fresh external output directory. It keeps the old snapshot, compiled observer outputs and all receipts for review. The script intentionally reports a first-idle comparison separately from post-idle completeness/safety failures.

<!-- spacing-compare-python -->
```python
import csv
import hashlib
import json
import math
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile

BASE = '0734fb23db6873f4ec64c3e3e29fc8c3e17edc7a'
FIXTURES = ('NR8', 'SM8', 'XM4x2')
root, probe, out = (Path(arg).resolve() for arg in sys.argv[1:])
if out.exists():
    raise SystemExit('Use a fresh output directory; preserve earlier receipts')
out.mkdir(parents=True)
env = dict(os.environ)
env['JAVA_TOOL_OPTIONS'] = '-Djava.awt.headless=true'
java_home = Path(env['JAVA_HOME'])
java, javac = str(java_home / 'bin/java'), str(java_home / 'bin/javac')
errors = []

def digest(data):
    return hashlib.sha256(data).hexdigest()

def command(args, cwd, logfile, timeout=None):
    with logfile.open('w') as log:
        log.write(json.dumps({'argv': list(map(str, args)), 'cwd': str(cwd)}) + '\n')
        log.flush()
        result = subprocess.run(list(map(str, args)), cwd=cwd, env=env,
                                stdout=log, stderr=subprocess.STDOUT, timeout=timeout)
        log.write('\nexit=' + str(result.returncode) + '\n')
    return result.returncode

def git(*args):
    return subprocess.check_output(['git', '-C', str(root), *args])

head = git('rev-parse', 'HEAD').decode().strip()
patch = git('diff', '--binary', 'HEAD')
(out / 'candidate.patch').write_bytes(patch)
(out / 'candidate-status.txt').write_bytes(git('status', '--short'))
source_hashes = {str(p.relative_to(root)): digest(p.read_bytes())
                 for p in sorted(root.glob('*/src/main/**/*')) if p.is_file()}
provenance = {'baseline': BASE, 'candidateHead': head, 'candidatePatchSha256': digest(patch),
              'observerSha256': digest(probe.read_bytes()), 'candidateMainSources': source_hashes,
              'JAVA_HOME': str(java_home), 'JAVA_TOOL_OPTIONS': env['JAVA_TOOL_OPTIONS']}
(out / 'provenance.json').write_text(json.dumps(provenance, indent=2) + '\n')
shutil.copyfile(probe, out / 'InitialSpacingProbe.java')
command([java, '-version'], root, out / 'java-version.log')
command(['gradle', '--version'], root, out / 'gradle-version.log')
baseline = out / 'baseline-source'
baseline.mkdir()
with (out / 'baseline.tar').open('wb') as archive:
    subprocess.run(['git', '-C', str(root), 'archive', '--format=tar', BASE], stdout=archive, check=True)
with tarfile.open(out / 'baseline.tar') as archive:
    archive.extractall(baseline, filter='data')
init = out / 'classpath.init.gradle'
init.write_text('''gradle.projectsEvaluated {
    def p = gradle.rootProject.project(':freeplane_plugin_graph')
    p.tasks.register('writeInitialSpacingClasspath') {
        dependsOn p.tasks.named('classes')
        doLast {
            new File(System.getProperty('spacing.classpath')).text = p.sourceSets.main.runtimeClasspath.asPath
        }
    }
}
''')
for version, tree in (('baseline', baseline), ('candidate', root)):
    run = out / version
    run.mkdir()
    cpfile = run / 'classpath.txt'
    rc = command(['gradle', '--no-daemon', '--console=plain', '-I', init,
                  '-Dspacing.classpath=' + str(cpfile),
                  ':freeplane_plugin_graph:writeInitialSpacingClasspath'], tree, run / 'build.log')
    if rc or not cpfile.exists():
        errors.append(version + ': classpath/build failure')
        continue
    cp = cpfile.read_text()
    jars = {str(Path(p).resolve()): digest(Path(p).read_bytes()) for p in cp.split(os.pathsep)
            if Path(p).is_file() and p.endswith('.jar')}
    (run / 'classpath-jars.json').write_text(json.dumps(jars, indent=2) + '\n')
    classes = run / 'observer-classes'
    classes.mkdir()
    rc = command([javac, '--release', '8', '-encoding', 'UTF-8', '-cp', cp, '-d', classes,
                  out / 'InitialSpacingProbe.java'], tree, run / 'observer-compile.log')
    if rc:
        errors.append(version + ': observer compilation failed')
        continue
    for fixture in FIXTURES:
        target = run / fixture
        target.mkdir()
        try:
            rc = command([java, '-Djava.awt.headless=true', '-Duser.language=en', '-Duser.country=US',
                          '-Duser.timezone=UTC', '-cp', str(classes) + os.pathsep + cp,
                          'org.freeplane.plugin.graph.layout.InitialSpacingProbe', fixture, target],
                         tree, target / 'process.log', timeout=65)
            if rc:
                errors.append(version + '/' + fixture + ': probe exited ' + str(rc))
        except subprocess.TimeoutExpired:
            errors.append(version + '/' + fixture + ': external watchdog expired')

def records(path):
    with path.open(newline='') as f:
        return list(csv.DictReader(f, delimiter='\t'))

def require(condition, message):
    if not condition:
        raise ValueError(message)

def number(value):
    result = float(value)
    require(math.isfinite(result), 'non-finite number')
    return result

def gaps(nodes):
    require(len({(n['map'], n['node']) for n in nodes}) == len(nodes), 'duplicate node')
    groups = {}
    for n in nodes:
        require(number(n['radius']) == 8.0, 'changed rendered radius')
        groups.setdefault(n['map'] + '/' + n['enclosure'], []).append(n)
    result = {}
    for key, population in groups.items():
        require(len(population) >= 2, 'nonqualifying population in frozen fixture')
        nearest = []
        for i, n in enumerate(population):
            distances = []
            for j, m in enumerate(population):
                if i == j:
                    continue
                dx, dy = number(n['x']) - number(m['x']), number(n['y']) - number(m['y'])
                require(math.isfinite(dx) and math.isfinite(dy), 'unrepresentable coordinate difference')
                value = math.hypot(dx, dy) - number(n['radius']) - number(m['radius'])
                require(math.isfinite(value), 'unrepresentable gap')
                distances.append(value)
            nearest.append((n['node'], min(distances)))
        values = sorted(value for _, value in nearest)
        mid = len(values) // 2
        median = values[mid] if len(values) % 2 else 0.5 * values[mid - 1] + 0.5 * values[mid]
        result[key] = {'minimum': values[0], 'median': median, 'perNode': nearest}
    return result

def safety(frame):
    for flag in ('failed', 'verificationExhausted', 'indexExhausted', 'detectionTruncated'):
        require(frame[flag] == 'false', 'bad safety flag: ' + flag)
    for flag in ('verified', 'boundaryVerified', 'boundaryCovered', 'endpointSafe'):
        require(frame[flag] == 'true', 'missing verification: ' + flag)
    for count in ('nodeResidual', 'hullResidual', 'edgeResidual', 'actualBoundary', 'actualCollision',
                  'chordCrossings', 'edgeOverNodes', 'paintedCrossings'):
        require(int(frame[count]) == 0, 'nonzero safety count: ' + count)
    for field in ('rms', 'max', 'correctionRms', 'correctionMax', 'correctionDeltaRms', 'correctionDeltaMax'):
        require(number(frame[field]) >= 0.0, 'negative motion measurement')

def expectations(run, fixture):
    with (run / 'inputs.tsv').open(newline='') as f:
        manifest = list(csv.reader(f, delimiter='\t'))
    declared_nodes = [r for r in manifest if r[0] == 'node']
    declared_hulls = [r for r in manifest if r[0] == 'enclosure']
    declared_edges = [r for r in manifest if r[0] == 'edge']
    maps = ['00000000-0000-0000-0000-000000000001']
    if fixture == 'XM4x2':
        maps.append('00000000-0000-0000-0000-000000000002')
    count = 4 if fixture == 'XM4x2' else 8
    frozen_nodes = [(m, prefix + str(i)) for m, prefix in zip(maps, 'AB') for i in range(count)]
    frozen_hulls = [(m, prefix + '_root') for m, prefix in zip(maps, 'AB')]
    require([int(r[1]) for r in declared_nodes] == list(range(8)), 'manifest node order changed')
    require([(r[10], r[11]) for r in declared_nodes] == frozen_nodes, 'manifest node identity changed')
    require([int(r[1]) for r in declared_hulls] == list(range(len(maps))), 'manifest enclosure order changed')
    require([(r[9], r[10]) for r in declared_hulls] == frozen_hulls, 'manifest enclosure identity changed')
    key_to_node = {r[2]: (r[10], r[11]) for r in declared_nodes}
    require(len(key_to_node) == 8, 'duplicate manifest node key')
    require(len({r[2] for r in declared_hulls}) == len(maps), 'duplicate manifest enclosure key')
    expected_nodes = set()
    for index, (map_id, label) in enumerate(frozen_hulls):
        direct = [r for r in manifest if r[0] == 'direct' and int(r[1]) == index]
        require([int(r[2]) for r in direct] == list(range(count)), 'manifest direct-node order changed')
        members = [key_to_node[r[3]] for r in direct]
        require(members == [n for n in frozen_nodes if n[0] == map_id], 'manifest membership changed')
        expected_nodes.update((m, n, label) for m, n in members)
    require(sum(r[0] == 'direct' for r in manifest) == 8, 'extra/missing manifest memberships')
    endpoints = [(key_to_node[r[4]], key_to_node[r[5]]) for r in declared_edges]
    frozen_edges = []
    if fixture == 'SM8':
        frozen_edges = [((maps[0], 'A' + str(i)), (maps[0], 'A' + str((i + 1) % 8))) for i in range(8)]
    elif fixture == 'XM4x2':
        frozen_edges = [((maps[0], 'A2'), (maps[1], 'B0'))]
    require([int(r[1]) for r in declared_edges] == list(range(len(frozen_edges))), 'manifest edge order changed')
    require(endpoints == frozen_edges, 'manifest relationship endpoints changed')
    expected_edges = {r[2]: ends for r, ends in zip(declared_edges, endpoints)}
    require(len(expected_edges) == len(declared_edges), 'duplicate manifest edge key')
    return expected_nodes, set(frozen_hulls), expected_edges

def read_run(version, fixture):
    run = out / version / fixture
    frames, nodes = records(run / 'frames.tsv'), records(run / 'nodes.tsv')
    anchors, edges = records(run / 'anchors.tsv'), records(run / 'edges.tsv')
    expected_nodes, expected_anchors, expected_edges = expectations(run, fixture)

    def frame_data(frame):
        def sample(rows):
            return [r for r in rows if r['phase'] == frame['phase'] and r['ordinal'] == frame['ordinal']]
        sampled, hulls, relationships = sample(nodes), sample(anchors), sample(edges)
        require(len(sampled) == len(expected_nodes)
                and {(n['map'], n['node'], n['enclosure']) for n in sampled} == expected_nodes,
                'exact node/map/enclosure coverage mismatch')
        require(len(hulls) == len(expected_anchors)
                and {(a['map'], a['enclosure']) for a in hulls} == expected_anchors,
                'exact anchor coverage mismatch')
        require(len(relationships) == len(expected_edges)
                and {e['edge'] for e in relationships} == set(expected_edges), 'exact edge coverage mismatch')
        for a in hulls:
            number(a['x']); number(a['y'])
        measured = gaps(sampled)
        by_node = {(n['map'], n['node']): n for n in sampled}
        for n in sampled:
            group = measured[n['map'] + '/' + n['enclosure']]
            expected = dict(group['perNode'])[n['node']]
            for field, value in (('nearestGap', expected), ('enclosureMinimum', group['minimum']),
                                 ('enclosureMedian', group['median'])):
                require(abs(number(n[field]) - value) <= 1e-9, 'observer statistic mismatch: ' + field)
        for e in relationships:
            a, b = (by_node[k] for k in expected_edges[e['edge']])
            distance = math.hypot(number(a['x']) - number(b['x']), number(a['y']) - number(b['y']))
            require(math.isfinite(distance), 'invalid relationship distance')
            require(abs(number(e['centreDistance']) - distance) <= 1e-9, 'relationship centre distance mismatch')
            require(abs(number(e['discGap']) - (distance - number(a['radius']) - number(b['radius']))) <= 1e-9,
                    'relationship disc gap mismatch')
        return measured

    first = [f for f in frames if f['phase'] == 'first-idle']
    require(len(first) == 1, 'expected exactly one first-idle row')
    first = first[0]
    pre = [f for f in frames if f['phase'] == 'pre-idle']
    require(all(f['idle'] == 'false' for f in pre), 'earlier idle skipped')
    require([int(f['steps']) for f in pre] == list(range(int(first['steps']))), 'missing pre-idle calls')
    require(all(int(f['ordinal']) == int(f['steps']) for f in pre + [first]), 'wrong pre/first ordinal')
    require(frames[:len(pre)] == pre and frames[len(pre)] == first, 'wrong first-idle ordering')
    require(first['idle'] == 'true' and int(first['stable']) >= 8, 'invalid first idle')
    require(number(first['rms']) <= 0.05 and number(first['max']) <= 0.10, 'invalid idle motion')
    require(0 <= int(first['steps']) <= 2000 and 0 <= int(first['validationElapsedNanos']) <= 30_000_000_000,
            'first-idle validation budget failed')
    safety(first)
    for f in pre:
        frame_data(f)
    measurements = frame_data(first)
    result = {'firstIdle': first, 'enclosures': measurements, 'postIdle': [], 'diagnosticErrors': []}
    post = [f for f in frames if f['phase'] == 'post-idle']
    try:
        require([int(f['ordinal']) for f in post] == list(range(1, 101)), 'incomplete post-idle window')
        require(len(frames) == len(pre) + 101, 'unexpected frame rows')
        frame_keys = {(f['phase'], f['ordinal']) for f in frames}
        require(len(frame_keys) == len(frames), 'duplicate frame records')
        for dataset in (nodes, anchors, edges):
            require(all((r['phase'], r['ordinal']) in frame_keys for r in dataset), 'orphan evidence row')
        for f in post:
            offset = int(f['ordinal'])
            require(int(f['steps']) == int(first['steps']) + offset, 'diagnostic call mismatch')
            require(0 <= int(f['validationElapsedNanos']) <= 30_000_000_000, 'diagnostic validation deadline')
            safety(f)
            result['postIdle'].append({'frame': f, 'enclosures': frame_data(f)})
    except (ValueError, KeyError, IndexError) as failure:
        result['diagnosticErrors'].append(str(failure))
        errors.append(version + '/' + fixture + ': post-idle diagnostic: ' + str(failure))
    return result

summary = {'baseline': BASE, 'candidateHead': head, 'fixtures': {}, 'errors': errors}
for fixture in FIXTURES:
    try:
        old_inputs = (out / 'baseline' / fixture / 'inputs.tsv').read_bytes()
        new_inputs = (out / 'candidate' / fixture / 'inputs.tsv').read_bytes()
        require(old_inputs == new_inputs, 'baseline/candidate inputs differ')
        old, new = read_run('baseline', fixture), read_run('candidate', fixture)
        require(old['enclosures'].keys() == new['enclosures'].keys(), 'enclosure population differs')
        comparisons = {}
        for key in old['enclosures']:
            delta = new['enclosures'][key]['median'] - old['enclosures'][key]['median']
            comparisons[key] = {'delta': delta, 'pass': delta >= 6.0 - 1e-6}
            if delta < 6.0 - 1e-6:
                errors.append(fixture + '/' + key + ': first-idle improvement below 6 units')
        summary['fixtures'][fixture] = {'inputsSha256': digest(old_inputs), 'baseline': old, 'candidate': new,
                                        'firstIdleComparisons': comparisons}
    except (OSError, ValueError, KeyError, IndexError) as failure:
        errors.append(fixture + ': ' + str(failure))
require(source_hashes == {str(p.relative_to(root)): digest(p.read_bytes())
                          for p in sorted(root.glob('*/src/main/**/*')) if p.is_file()},
        'candidate main sources changed during measurement')
summary['status'] = 'PASS' if not errors else 'FAIL'
(out / 'comparison.json').write_text(json.dumps(summary, indent=2, allow_nan=False) + '\n')
print(json.dumps({'status': summary['status'], 'errors': errors, 'evidence': str(out)}, indent=2))
raise SystemExit(0 if not errors else 1)
```

---

# Epoch 3 Addendum - final-gate remediation (normative)

- Date: 2026-10-18
- Task identifier: `graph-initial-spacing`
- Epoch: 3
- Authority: approved design [2026-10-18-graph-initial-spacing-epoch-3-design.md](2026-10-18-graph-initial-spacing-epoch-3-design.md), revision 5 (incorporating the D1 and D2 corrections), SHA-256 `2b2ea8ec83bc9eb7144826a09e165ccc373fc45a5cd3226dca8e47cd2690a72f`.
- Epoch-3 integration baseline: commit `92f31fce842f8e5bb1376c13e4135cdd138907db`, generation 1.
- Status: normative draft addendum. Implementation, runner creation, evidence collection, and user sign-off remain pending.

This addendum is appended to, and does not replace or rewrite, any epoch-1 or epoch-2 text above. All parent contracts remain in force. This addendum supplies the approved epoch-3 remediation contract for the four pre-existing final-gate defects. If an epoch-3 final-gate command below differs from a historical command in the preserved parent text, the command below is the epoch-3 final acceptance command; the preserved parent command remains unchanged as historical specification text.

## 12. Epoch-3 scope, authority, and freeze

Epoch 3 repairs environment assumptions, stale test evidence, and the lifecycle suite-order failure without changing the approved ring-only `PREFERRED_NODE_GAP=24.0` behavior, relationship rest lengths, convergence or idle policy, safety limits, or the meaning of any non-spacing performance threshold.

The only source-file changes authorized by this addendum are these four test classes:

1. `AccessibleGraphCanvasShould`, for the two explicitly named headless assumptions in Section 13.
2. `GraphWorkspaceWindowModelShould`, for the window-manager precondition and test-mode handling in Section 14.
3. `GraphWorkspacePerformanceDiagnostic`, for the exact two-pinned-maps conflict golden and structure assertions in Section 15.
4. `GraphWorkspaceLifecycleShould.java`, for the test-only close-boundary barrier in Section 16.

The final command path correction, external receipts, and hash-recorded evidence runners are also in scope. No production source, production API, build configuration, fixture resource, dependency, serialization format, persistence field, threshold, deadline, safety budget, or non-spacing numeric golden may change. All epoch-2 production implementation files must remain byte-identical to the approved integrated tree. No production correction is authorized by this addendum; if evidence indicates that one is needed, implementation must stop for a new explicit user decision.

The candidate/evidence runner and receipts are implementation deliverables. They may be absent while this addendum is drafted and must not be described as existing evidence until they have been created, hash-recorded, and run. Openbox is unavailable in the drafting environment. Its absence is a blocking prerequisite for the positive window-manager evidence, not an optional skip or a pass.

## 13. A - accessibility assumptions are exactly scoped

The only display-dependent methods in `AccessibleGraphCanvasShould` are:

- `receiveTabThroughSwingDispatchAndRestoreFocusTraversalConfiguration`
- `ignoreEnterForSelectionsAbsentFromCurrentVisibleOrderAfterStateReplacement`

Both methods call `withFocusedCanvas`, which creates and shows a `JFrame`. Add the repository's existing JUnit assumption pattern at the beginning of exactly these two methods, before frame construction, to skip when `GraphicsEnvironment.isHeadless()` is true. Keep every existing focus, traversal, and state-replacement assertion unchanged when a display is available.

No other method in the class may be skipped for this reason. Do not replace either real-window test with a mocked focus owner, and do not add a class-level headless guard. The headless class receipt must show exactly these two named methods skipped and the other nine methods executed and passed. In the full module receipt, the pre-existing skipped test identifiers must remain unchanged and the module skip count may increase only from the frozen candidate count of 18 to 20.

A separate display-backed run must execute both named methods and all nine other methods, for 11 executed methods, zero skipped, zero failures, and zero errors. This display-backed run is required evidence that the assumptions are scoped rather than a permanent bypass.

## 14. B - maximized-window capability, EWMH proof, and Openbox evidence

### 14.1 Test-worker mode contract

`GraphWorkspaceWindowModelShould.f11RestoresBoundsAndExtendedStateFromMaximizedWindow` depends on a window manager honoring `MAXIMIZED_BOTH`; a bare Xvfb display is not sufficient. Retain the existing headless guard and all existing fullscreen round-trip assertions.

The test worker reads `FREEPLANE_GRAPH_TEST_WINDOW_MANAGER` from its environment. The only accepted values are:

- `required`, which is the default when the variable is unset;
- `optional`.

Every other value is a hard test failure. Gradle's test worker must inherit this environment variable; no new Gradle property propagation is permitted.

Before requesting maximization, the test must query `_NET_SUPPORTING_WM_CHECK` on the current `DISPLAY` using `xprop`. A query or tool failure is an error and is not evidence that a window manager is absent. In `optional` mode, exactly one JUnit assumption skip is permitted only when this independent pre-maximize query proves `_NET_SUPPORTING_WM_CHECK` absent on that same display. A maximize timeout, malformed property, broken window manager, or missing postcondition is never an optional absence skip.

If a window manager is present, missing `_NET_WORKAREA`, a failed work-area cross-check, failure to maximize, or failure to stabilize is an assertion failure even in `optional` mode. In `required` mode, absence of either required EWMH property or failure to maximize is an assertion failure and can never skip.

### 14.2 Authoritative work area and stable interval

Keep the existing AWT-insets `workArea(graphics)` as the test's authoritative rectangle so the existing `DisplayFixture` contract remains the assertion source. When a window manager is present, read `_NET_CURRENT_DESKTOP` and the four-cardinal rectangle for that desktop from `_NET_WORKAREA` on the same `DISPLAY`. Record both rectangles and require each corresponding edge to agree within `X11_AWT_BOUNDS_TOLERANCE`, exactly 2 px. Missing or malformed `_NET_WORKAREA` fails whenever a window manager is present; it is not converted to a skip.

The visible frame must cover the authoritative AWT work area within the same 2 px tolerance. Use the existing `awaitFrameStable(2100)` mechanism without enlarging its deadline: it polls every 10 ms, requires a full 2100 ms of continuous quiet, and fails at the existing 5000 ms monotonic deadline. Strengthen every poll so that the frame remains visible and displayable, `MAXIMIZED_BOTH` remains set, and its bounds remain unchanged. Require work-area coverage again at the stable endpoint, then run the existing F11 round-trip assertions. The bare no-window-manager path may skip before this interval only after the independent absent-property proof; the required path treats missing `_NET_WORKAREA`, a failed cross-check, or a failed stable interval as failure.

**D1 correction (user-approved 2026-10-18).** Work-area coverage and *screen* containment are different claims. Work-area coverage is required everywhere; screen containment is required only where it is actually achievable. Measured under Openbox 3.6.1: a **decorated** maximized frame is stably reported by AWT as `(-1,-1,1282,1030)` on a `1280x1024` screen, while the X server reports the same window as exactly `(0,0,1280,1024)` (`_NET_FRAME_EXTENTS 0,0,21,0`, client `0,21 1280x1003`). An **undecorated** fullscreen frame carries no decoration, so the same AWT reporting does satisfy screen containment there.

The pre-existing helpers are shared by both paths, which is the actual defect: `hasWorkAreaCoverage()` and `assertWorkAreaCoverageOnEdt()` each assert work-area coverage **and** screen containment, and are reached both from the undecorated fullscreen test (`f11MakesWindowUndecoratedAndFillsCurrentScreen`, via `f.await(f::hasWorkAreaCoverage)` and `f.assertWorkAreaCoverage()`) and from the decorated maximized test (via `assertRoundTrip(true)`). Measured failures attributable to the screen-containment clauses on the untouched task-1 baseline: **6 of 8 runs**, every one of them in the maximized-window method with the decorated `(-1,-1,1282,1030)` frame. (One further baseline run failed in the unrelated normal-window method because the default 640x480 screen cannot host the fixture; that is the separate runner-geometry matter, not this clause. The remaining run passed.)

The correction is therefore **scoped, not global**: both helpers gain an explicit screen-containment parameter, and only the decorated maximized path passes `false`. Concretely:

- `hasWorkAreaCoverage(boolean requireScreenContainment)` keeps all four work-area clauses always, and adds its four screen clauses only when the flag is true. The undecorated fullscreen call site keeps `true`.
- `assertWorkAreaCoverageOnEdt(boolean requireScreenContainment)` and its `assertWorkAreaCoverage(...)` wrapper behave identically. The undecorated fullscreen call site keeps `true`.
- `assertRoundTrip(requireWorkArea = true)`, which is reached only from the decorated maximized test, passes `false` to both.
- No other call site, clause, tolerance, or budget changes. `X11_AWT_BOUNDS_TOLERANCE` remains exactly 2 px.

This corrects an over-broad shared assertion rather than relaxing required behavior: the maximized frame must still be reported by AWT as covering the entire work area at all four edges, on every poll of the stability interval and again at the stable endpoint, and any failure of that coverage remains an assertion failure in both `required` and `optional` modes. The undecorated fullscreen test keeps its full screen-containment coverage, so this correction removes no check from the path where the check is meaningful.

### 14.3 Openbox 3.6.1 prerequisite and identity chain

The positive display-backed run has an explicit external prerequisite: Openbox upstream release 3.6.1. The future runner must preflight and record:

- `command -v openbox`;
- `openbox --version`;
- `pacman -Q openbox`;
- the executable SHA-256.

On Arch, the documented provisioning command is `sudo pacman -S --needed openbox`. Provisioning is an implementation/operator step, not an action of this drafting task. The run must be rejected unless the reported upstream release is exactly 3.6.1. If preflight cannot establish the prerequisite, the runner must emit `WM_PREREQUISITE_MISSING:OPENBOX_3_6_1`; the evidence gate is BLOCKED, not SKIPPED and not PASS.

On an isolated Xvfb display, start `openbox --sm-disable` and wait no more than 5 seconds for root `_NET_SUPPORTING_WM_CHECK`. Verify all of the following identity links:

1. The launched Openbox PID is alive.
2. `/proc/<pid>/exe` resolves to the preflight executable path and its executable SHA-256 equals the preflight SHA-256.
3. `xprop -root _OPENBOX_PID` equals the launched PID.
4. Read the support-window XID from root `_NET_SUPPORTING_WM_CHECK`.
5. `xprop -id <support-XID> _NET_SUPPORTING_WM_CHECK` self-references the same support XID.
6. `xprop -id <support-XID> _NET_WM_NAME` identifies Openbox.

Openbox 3.6.1 publishes `_OPENBOX_PID` on the root, not `_NET_WM_PID` on the support window. `_NET_WM_PID` may be recorded if a package supplies it, but its absence must not reject stock 3.6.1 and the runner must not synthesize it. Record `_NET_CURRENT_DESKTOP` and `_NET_WORKAREA` as well as the full process/property evidence.

The runner must install a cleanup trap, terminate and reap Openbox, allow `xvfb-run` to stop Xvfb, and record each cleanup outcome, including child-process termination and display-server reaping. A stale process or failed cleanup makes the evidence incomplete and must be reported.

The bare run must show the named maximized-window method with exactly one capability skip caused by the same-display proof of absent `_NET_SUPPORTING_WM_CHECK`. The Openbox run must show that method executed with `skipped=0`, `failures=0`, and `errors=0`; the rest of the class must pass with no newly introduced skips so normal-window and other F11 behavior remain covered independently.

## 15. C - exact two-pinned-maps conflict golden and provenance

Change only `GraphWorkspacePerformanceDiagnostic.TWO_PINNED_MAPS_EXPECTED_CONFLICTS` from the stale value 1 to the measured delivered-branch value 11. Keep an exact count assertion. The count is a measured re-derivation authorized by the existing spacing-golden rule, not a tunable threshold or a relaxation.

The conflict assertion must additionally require all of the following:

- exactly 11 conflicts;
- every conflict has reason `IMMOVABLE_SIDES`;
- exactly 2 conflicts have kind `SIBLING_CROSSING`;
- exactly 9 conflicts have kind `SIBLING_CONTAINMENT`;
- every conflict has the exact unordered blocking-pin identity set:
  `{(00000000-0000-0000-0000-000000000001, m00-n0001), (00000000-0000-0000-0000-000000000002, m01-n0001)}`.

Resolve the two identities from the fixture's persisted `PinProjection`/`NodeReference` values. Do not use list order, map order, or merely `blockingPins().size()==2` as the identity proof. The fixture persists `m00-n0001` and `m01-n0001` as roots of two maps pinned at the same `(0.0, 0.0)` center. The wider delivered rings separate some remaining hull pairs, producing baseline 20 and candidate 11 while retaining the `IMMOVABLE_SIDES` classification.

### 15.1 Frozen baseline receipt

The baseline value 20 must be bound to all of these immutable provenance facts:

- source commit `0734fb23db6873f4ec64c3e3e29fc8c3e17edc7a`;
- source tree `0bc8e57b5d26c2d79252a4ea4735cbf52fdbbf3c`;
- strict log `/tmp/graph-initial-spacing-evidence/task-5/diagnostic/baseline-vector2-strict.log`;
- strict-log SHA-256 `67390c50c91aec57d8ff913b6f1c549453eab83555834d53978bf66b6a52c104`;
- a SHA-256 recorded for the baseline receipt itself.

The baseline receipt must preserve the exact 20-count failure from the hash-pinned strict log and bind it to the frozen source commit/tree and the receipt's SHA-256. The historical log does not claim to contain per-conflict records. Full conflict records and four individually recorded first-submission samples are required for the final candidate under Section 15.2. A missing receipt hash, altered source tree, or substituted baseline run invalidates the golden evidence.

### 15.2 Candidate receipt and tree equality

The candidate value 11 must be bound to the final epoch-3 candidate commit and tree, the matching final lane tree, and the matching final integration/delivery tree. Acceptance must reject the receipt unless the candidate, lane, and final delivery `git rev-parse HEAD^{tree}` values are identical. An earlier epoch-2 probe is supporting historical evidence only and cannot serve as the final-tree acceptance receipt.

The candidate receipt must include hashes of `GraphWorkspacePerformanceDiagnostic.java` and `GeneratedWorkspace.java`, the exact `TWO_PINNED_MAPS` fixture identity, request generation, first-submission identity, four individually recorded consecutive samples, every full conflict record, and a SHA-256 for the probe log and all probe artifacts. Each sample must independently show the exact count, reason, kind split, and unordered persisted pin identities. An aggregate count without the four sample records is insufficient.

No other conflict golden, conflict rule, performance threshold, operation timeout, or process deadline may change. The exact 20-to-11 result and all structure guardrails must remain visible in the acceptance record.

## 16. D - lifecycle suite-order remediation is test-only

The frozen baseline's full module suite failed once at `GraphWorkspaceLifecycleShould.doesNotDeliverCallbacksAfterClose` with `stale projection listener after close`, while the focused test passed. Preserve the invariant that no projection, canvas, or status callbacks are delivered after the coordinator's logical close boundary. First localize and record whether suite order exposes leaked fixture resources or a race-prone test assumption; do not infer a production defect from one order-dependent failure.

The only source file authorized for this remediation is `GraphWorkspaceLifecycleShould.java`. The governing [Graph Workspace Lifecycle Close-Boundary Remediation Design](2026-08-24-graph-workspace-lifecycle-close-boundary-remediation-design.md) forbids production-source, API, fixture-resource, build-configuration, timeout, or unrelated lifecycle changes.

Implement the test-only teardown barrier in this exact order:

1. Capture the `ResourceBaseline`.
2. Prove that the workspace map-lifecycle listener count has risen above the captured baseline.
3. Start close off the EDT and await the existing handle-closing rejection boundary.
4. Await the callback-registration rejection boundary: registering a canvas-state listener throws `IllegalStateException` once the coordinator is logically closed.
5. Only after that logical-close signal, release the deliberately blocked first projection listener.
6. *After* release, use the existing bounded condition helper to await the map-lifecycle listener count returning to baseline as a completed-teardown assertion.

**D2 correction (user-approved 2026-10-18).** The earlier ordering — wait for the listener count to return to baseline, then release — is circular and cannot be satisfied by teardown. `GraphUpdateCoordinator.close()` assigns `closed = true` synchronously inside its `synchronized` block and only then tears down; teardown reaches `MapLeaseManager.close()`, which detaches the map-lifecycle listener inside `runOnEdtAndWait`, a blocking EDT round trip with no timeout. The deliberately blocked first projection listener is itself occupying the EDT, so the count cannot return to baseline until that listener releases the EDT — which happens only when its own 5-second latch expires, the same 5000 ms budget as the barrier. The earlier ordering therefore satisfied the wait on the bounded helper's post-deadline re-check rather than on teardown. Measured: satisfied within ±20 ms of its own deadline in six of six instrumented runs, four of six only on the post-deadline re-check; a 20-second observation window placed the coordinator's closed boundary at about 1.94 s with the EDT still blocked but the listener baseline only at about 6.94 s, exactly the latch expiry; and lengthening only the latch to 20 s made the baseline unreachable inside the barrier budget in three of three runs.

So the callback-registration rejection is the **governing** logical-close signal for release, and it is legitimately reachable while the EDT is blocked because `closed` is set before any teardown and `addCanvasStateListener` reads it under the same monitor (measured reachable at about 1.94 s). The listener-count return remains required, but only as a post-release completed-teardown assertion, where it is genuinely achievable because the EDT is free to run the detach.

Both signals are required. Neither may be dropped, retried, slept on, or widened, and both keep the existing 5000 ms budget. The 5-second latch and 10-second close-thread join keep their existing values; no timeout changes.

Preserve the second-listener zero assertion, source-map mutation, resource-baseline checks, callback counters, closed-handle assertions, and existing cleanup. Release the blocking listener in a `finally` path that covers the wait and bounded close-thread join, so a failing probe cannot contaminate later tests.

Add no retries, sleeps, `Assume` guards, broad exclusions, or weakened callback-count assertions. In a disposable test compilation only, remove the production closed-state guard immediately before each projection-listener invocation. The revised test must fail against that mutant. The active production source tree must remain untouched by the mutant probe. If investigation establishes that a production correction is required, stop and obtain a new explicit user decision; this epoch authorizes no production correction.

## 17. Supporting-evidence matrix and runner contract

The following six supporting runs are required in addition to, and never instead of, the four final gates in Section 19. They must run from a pinned, clean `CANDIDATE_ROOT`, recorded as an absolute path that resolves to the final candidate commit/tree.

Before these runs, export `JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu`, prepend `$JAVA_HOME/bin` to `PATH`, and export:

```bash
export CANDIDATE_ROOT=/absolute/path/to/final-candidate
export PM_RUN_ROOT=/home/henry/.local/state/pi/project-manager/runs/0233ff88d3cc54bbd61550c61fa917d6e2a5717f8a03be4e9aa63dcd47dd1cfa/pm-run-20261016-125451-a5048653
export EVIDENCE_ROOT="$PM_RUN_ROOT/reports/epoch-3/supporting-evidence"
```

Each run must have a fresh named evidence directory and `run.json` containing the exact argv, environment, working directory, start/end timestamps, exit status, `git rev-parse HEAD`, `git rev-parse HEAD^{tree}`, clean status, and relevant source hashes. Preserve full stdout/stderr and the JUnit XML emitted under `freeplane_plugin_graph/build/test-results/test/` in that run's directory. Hash the runner scripts, source/probe files, init scripts, classpaths, receipts, logs, and generated artifacts; record every hash in an immutable manifest. Do not overwrite an earlier receipt or silently rerun with changed inputs.

| Run | Exact command | Expected result | Receipt |
| --- | --- | --- | --- |
| 1 - headless accessibility | `JAVA_TOOL_OPTIONS=-Djava.awt.headless=true gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.AccessibleGraphCanvasShould' --console=plain -PTestLoggingFull --rerun-tasks` | Exactly the two named assumption skips and 9 passes; no other skip in the selected class. | `reports/epoch-3/supporting-evidence/accessibility-headless/`, including `gradle.log` and `TEST-org.freeplane.plugin.graph.canvas.AccessibleGraphCanvasShould.xml`. |
| 2 - display accessibility | `env -u JAVA_TOOL_OPTIONS xvfb-run -a gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.canvas.AccessibleGraphCanvasShould' --console=plain -PTestLoggingFull --rerun-tasks` | All 11 methods execute and pass; zero skipped. | `reports/epoch-3/supporting-evidence/accessibility-xvfb/`, including the actual `DISPLAY`, `gradle.log`, the class XML, and Xvfb cleanup. |
| 3 - bare-Xvfb maximized method | `env -u JAVA_TOOL_OPTIONS FREEPLANE_GRAPH_TEST_WINDOW_MANAGER=optional xvfb-run -a gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould.f11RestoresBoundsAndExtendedStateFromMaximizedWindow' --console=plain -PTestLoggingFull --rerun-tasks` | Exactly one skip, caused only by the same-display proof that `_NET_SUPPORTING_WM_CHECK` is absent; record the absent property and `_NET_WORKAREA` on that display. | `reports/epoch-3/supporting-evidence/f11-bare-xvfb/`, including XML, logs, display data, and Xvfb cleanup. |
| 4 - Openbox/Xvfb window-model class | `env -u JAVA_TOOL_OPTIONS PM_RUN_ROOT="$PM_RUN_ROOT" bash "$PM_RUN_ROOT/scripts/epoch-3/run-openbox-window-probe.sh" "$CANDIDATE_ROOT" "$EVIDENCE_ROOT/f11-openbox-xvfb"` | The target method executes with `skipped=0`, `failures=0`, and `errors=0`; the rest of the class passes with no newly introduced skips. A failed prerequisite emits `WM_PREREQUISITE_MISSING:OPENBOX_3_6_1` and blocks acceptance. | `reports/epoch-3/supporting-evidence/f11-openbox-xvfb/`, including runner hash, Openbox/Xvfb process identities, version, EWMH properties, AWT/EWMH rectangles, XML, Gradle log, and cleanup results. |
| 5 - two-pinned-maps golden | Use the exact classpath, compile, and probe sequence in Section 17.1 against a fresh `supporting-evidence/two-pinned-conflicts/` directory. | Four consecutive first submissions of the exact `TWO_PINNED_MAPS` fixture, 5-second await per submission, full conflict records, and the exact 11/`IMMOVABLE_SIDES`/2-crossing/9-containment/pin-identity result in every sample. | `reports/epoch-3/supporting-evidence/two-pinned-conflicts/`, including `ConflictProbe.java`, `classpath.init.gradle`, classpath, source/log/JSONL hashes, and sample receipts. |
| 6 - frozen baseline comparison | `python3 freeplane_plugin_graph/src/test/scripts/compare_initial_spacing.py "$CANDIDATE_ROOT" freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/InitialSpacingProbe.java "$EVIDENCE_ROOT/spacing-comparison"` | Fresh output; all four parent fixtures, unchanged first-idle/post-idle budgets, complete receipts, and each median delta at least `6.0 - 1e-6`. | `reports/epoch-3/supporting-evidence/spacing-comparison/`, preserving the complete output tree. |

The six-run matrix is a supporting contract, not a replacement for the final integration vector. A display assumption is acceptable only because the same method is separately proven executed under the required display/window manager. Every Xvfb invocation must exit and reap its display server. The Openbox runner must terminate and reap Openbox before Xvfb cleanup and record the result. Test workers, probe JVMs, and capture/launch children must terminate normally; cleanup failures make the corresponding evidence incomplete.

### 17.1 Exact two-pinned-maps classpath and probe setup

In the fresh `supporting-evidence/two-pinned-conflicts/` directory, the implementation deliverable must save the probe source as `ConflictProbe.java` and save this exact Gradle init script as `classpath.init.gradle`:

```groovy
allprojects { p ->
  afterEvaluate {
    if (p.path == ':freeplane_plugin_graph') {
      tasks.register('printSpacingTestClasspath') {
        doLast { println('SPACING_TEST_CP=' + p.sourceSets.test.runtimeClasspath.asPath) }
      }
    }
  }
}
```

Run the following exact commands, requiring exit status 0 at every step:

```bash
JAVA_TOOL_OPTIONS=-Djava.awt.headless=true gradle :freeplane_plugin_graph:testClasses :freeplane_plugin_graph:printSpacingTestClasspath -I "$EVIDENCE_ROOT/two-pinned-conflicts/classpath.init.gradle" --console=plain > "$EVIDENCE_ROOT/two-pinned-conflicts/classpath.log" 2>&1
sed -n 's/^SPACING_TEST_CP=//p' "$EVIDENCE_ROOT/two-pinned-conflicts/classpath.log" > "$EVIDENCE_ROOT/two-pinned-conflicts/classpath.txt"
# Require classpath.txt to contain exactly one non-empty line.
mkdir -p "$EVIDENCE_ROOT/two-pinned-conflicts/classes"
javac --release 8 -encoding UTF-8 -cp "$(cat "$EVIDENCE_ROOT/two-pinned-conflicts/classpath.txt")" -d "$EVIDENCE_ROOT/two-pinned-conflicts/classes" "$EVIDENCE_ROOT/two-pinned-conflicts/ConflictProbe.java"
JAVA_TOOL_OPTIONS=-Djava.awt.headless=true "$JAVA_HOME/bin/java" -Duser.language=en -Duser.country=US -cp "$EVIDENCE_ROOT/two-pinned-conflicts/classes:$(cat "$EVIDENCE_ROOT/two-pinned-conflicts/classpath.txt")" org.freeplane.plugin.graph.performance.ConflictProbe "$EVIDENCE_ROOT/two-pinned-conflicts/samples.jsonl" > "$EVIDENCE_ROOT/two-pinned-conflicts/probe.log" 2>&1
```

The probe must make four consecutive first submissions of the exact `TWO_PINNED_MAPS` fixture, use a 5-second await for each submission, and emit one full conflict record per sample. The source, init script, classpath, source files, logs, JSONL receipt, and all generated artifacts must be SHA-256 recorded. The exact probe source and Openbox runner are deliverables and are not pre-existing artifacts merely because these commands are specified here.

## 18. Immutable evidence, provenance, and cleanup

Every final-gate and supporting-run receipt must bind the result to the final candidate source state. At minimum, each receipt records the full command, environment, working directory, candidate HEAD and tree, start/end timestamps, exit status, and content hashes. The spacing comparator must retain its candidate patch/status, observer hash, all main-source hashes, Java/Gradle versions, dependency/JAR checksums, exact inputs, baseline archive, candidate archive/working tree identity, full stdout/stderr, unrounded TSV files, and comparison result. The conflict probe must retain its four sample records and full conflict details. The Openbox runner must be immutable after hashing and its hash must appear in the receipt.

The acceptance package must distinguish PASS, FAIL, NOT RUN, and BLOCKED for every gate and supporting run. A missing runner, missing receipt, missing hash, absent cleanup proof, or fabricated result is not PASS. The current draft does not claim that the absent Openbox runner or receipts exist. A preflight failure must remain `WM_PREREQUISITE_MISSING:OPENBOX_3_6_1` and BLOCKED; it must not be reclassified as the optional bare-Xvfb skip.

The final candidate, lane, and integration tree equality in Section 15 is a hard provenance gate. A receipt generated from an earlier epoch-2 tree, a dirty or changed candidate, a candidate/lane mismatch, or a final-delivery tree mismatch cannot satisfy the conflict golden or any final acceptance criterion. Preserve failed receipts and explain any rerun after an identified environmental correction.

## 19. Four exact final integration gates

Run from the verified candidate root using the installed `gradle` and Java 21.0.8-zulu. Non-UI commands inherit `JAVA_TOOL_OPTIONS=-Djava.awt.headless=true`; the runtime smoke command explicitly removes that variable and uses an actual X display. Each receipt records the full command, environment, working directory, candidate HEAD/tree, timestamps, exit status, and content hashes.

| Gate | Exact command and environment | Frozen contract | Required receipt |
| --- | --- | --- | --- |
| 1 - graph module | `gradle :freeplane_plugin_graph:check --console=plain -PTestLoggingFull` with headless Java 21.0.8-zulu and `JAVA_TOOL_OPTIONS=-Djava.awt.headless=true` | Full module suite. Exactly the two named accessibility methods may be newly skipped; all pre-existing skipped identifiers remain unchanged and the module total may increase only 18 to 20. | `reports/epoch-3/final-gates/gate-1/`, including command/environment, exit code, per-test XML, counts, and skip attribution. |
| 2 - strict performance | `gradle :freeplane_plugin_graph:graphPerformanceDiagnostic -PgraphStrictPerformance --console=plain` with headless Java 21.0.8-zulu and `JAVA_TOOL_OPTIONS=-Djava.awt.headless=true` | `OPERATION_TIMEOUT_NANOS` remains 5 seconds; `PROCESS_DEADLINE_NANOS` remains 40 minutes; 8 raw-reference frames remain, each at most 5 seconds; 200-enclosure submission remains <=3.0 seconds with its 5-second await; cross-map displacement remains 0.005; production idle remains 8 consecutive frames, RMS <=0.05, maximum <=0.10. | `reports/epoch-3/final-gates/gate-2/`, including the strict ledger, command/environment, exit code, and diagnostics. |
| 3 - runtime smoke | `env -u JAVA_TOOL_OPTIONS xvfb-run -a gradle :freeplane_plugin_graph:graphOsgiSmoke :freeplane_plugin_graph:freeplaneLaunchSmoke --console=plain` with Java 21.0.8-zulu | Both project-qualified tasks execute on an actual X display; capture/launch processes terminate normally. The unqualified `:freeplaneLaunchSmoke` is not the epoch-3 command. | `reports/epoch-3/final-gates/gate-3/`, including display, task receipts, OSGi bundle evidence, launch result, and child-process termination. |
| 4 - application compile/tests | `gradle :freeplane:compileJava test --console=plain -PTestLoggingFull` with headless Java 21.0.8-zulu and `JAVA_TOOL_OPTIONS=-Djava.awt.headless=true` | Existing application compile and test assertions remain unchanged. | `reports/epoch-3/final-gates/gate-4/`, including command/environment, exit code, and XML/log outputs. |

All four final gates are independently required and must exit 0. The supporting matrix is additional evidence. The Openbox positive evidence is also required for completion; if its prerequisite is unavailable, the final acceptance status remains BLOCKED even if the bare-Xvfb optional run produces its one capability skip.

## 20. Unchanged spacing, performance, safety, and production budgets

The following parent contracts are explicitly frozen for epoch 3:

- The preferred gap remains ring-only at 24.0 layout units; relationship rest lengths continue to use collision clearance 6.0, not the preferred gap.
- The packing gutter remains 8.0; rendered base node radius remains 8.0; node settle margin/frame clearance remains 8.0/16.0; boundary padding/hull clearance remains 8.0/16.0.
- Calibration remains `(1.0, 0.30, 1.0)`; typed attraction, repulsion, and boundary repulsion remain `0.05 / 16.0 / 0.5`.
- The aggregate cross-map displacement cap remains 0.005 per affected particle per engine step.
- The production idle policy remains 8 consecutive frames, RMS <=0.05, and maximum displacement <=0.10.
- First-idle outcome evidence retains NR8 A, SM8 A, XM4x2 A, and XM4x2 B; each candidate-versus-baseline median delta remains `>= 6.0 - 1e-6`; the first-idle limit remains at most 2000 `step()` calls and one monotonic 30-second deadline per fixture/version.
- The post-idle diagnostic remains the next 100 sequential worker frames from the same worker, with its own monotonic 30-second deadline and no improvement threshold; the external per-fixture/version watchdog remains 65 seconds.
- `OPERATION_TIMEOUT_NANOS` remains 5 seconds; `PROCESS_DEADLINE_NANOS` remains 40 minutes; the strict diagnostic retains 8 raw-reference frames with a maximum of 5 seconds each.
- The 200-enclosure submission assertion remains <=3.0 seconds with its existing 5-second future await.
- Existing `<8.0` one-step movement checks, cross-map cap checks, safety/coverage/pin/determinism assertions, boundary and edge-routing checks, and performance thresholds remain active.
- No idle threshold, correction budget, convergence cap, process deadline, operation timeout, safety budget, non-spacing numeric golden, conflict rule, or unrelated test assertion may be weakened or changed.

The authorized conflict value change is only the measured 20-to-11 `TWO_PINNED_MAPS` golden with the guardrails in Section 15. It is not a general golden regeneration. The lifecycle test may become order-stable only through the Section 16 test-only barrier; no production lifecycle behavior changes.

## 21. Epoch-3 acceptance criteria and status

Epoch 3 is complete only when all of the following are evidenced against the final tree:

1. The two named accessibility methods are the only newly permitted headless skips; the headless class has exactly 2 skips and 9 passes; the display-backed class executes and passes all 11 methods; the full module skip count changes only 18 to 20.
2. The maximized-window method uses the exact `required`/`optional` environment contract, performs the same-display pre-maximize xprop proof, skips only for independently proven WM absence in optional bare Xvfb, fails for missing/broken WM capability when required or present, and passes under Openbox 3.6.1 with the complete PID/support-XID/name identity chain, AWT/EWMH work-area cross-check within 2 px, and the full 2100 ms stable interval under the existing 5000 ms deadline. Work-area coverage keeps all four clauses within the 2 px tolerance on every path; screen containment is relaxed to the undecorated paths only, per the scoped D1 correction in Section 14.2, so the undecorated fullscreen test retains its screen-containment checks.
3. The exact two-pinned-maps conflict golden is 11, with all `IMMOVABLE_SIDES`, exactly 2 `SIBLING_CROSSING`, exactly 9 `SIBLING_CONTAINMENT`, and the exact unordered persisted pin identities in all four samples. Baseline 20 provenance, strict-log hash, receipt hashes, final candidate/lane/integration tree equality, source hashes, and probe hashes are present.
4. The lifecycle suite no longer exhibits the order-dependent post-close callback failure; only `GraphWorkspaceLifecycleShould.java` changed; the callback-registration rejection (the coordinator's synchronously-set logical-close signal) governs release of the blocked listener; the map-listener return-to-baseline is awaited only after that release as a completed-teardown assertion, per the D2 correction in Section 16; existing assertions and cleanup remain; and the disposable closed-guard mutant fails the test without modifying production source.
5. All six supporting runs and all four exact final gates have immutable receipts, source/runner hashes, full logs, and cleanup results. Any missing Openbox prerequisite is recorded as `WM_PREREQUISITE_MISSING:OPENBOX_3_6_1` and BLOCKED, never SKIPPED or PASS.
6. The unchanged spacing, convergence, performance, safety, lifecycle production-file, and Java-8 bundle contracts in the parent specification remain satisfied. No production source, threshold, deadline, safety budget, or non-spacing golden was changed or relaxed.

At drafting time, the epoch-3 runner/evidence deliverables are NOT RUN, and the Openbox prerequisite is unavailable. No candidate pass is claimed by this addendum. A final acceptance record must report every item as PASS, FAIL, NOT RUN, or BLOCKED with its artifact path and must preserve all failures for review.
