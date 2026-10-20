# FRONTIER specification: graph-initial-spacing

Status: implementation contract for `graph-initial-spacing`, dated 2026-10-20. This document authorizes only the production edits in A and the test/evidence changes needed to verify them. It does not implement those edits.

## A — Authority, scope and exact production diff

The authority is [the approved design and its complete epoch-2 amendment](2026-10-16-graph-initial-spacing-design.md), especially E2-A1 through E2-A8. The amendment controls whenever the preserved approved body disagrees with it. In particular, E2-A2 replaces the universal settled-spacing gate with a **seed-frame** gate; E2-A3 makes settled spacing descriptive; E2-A4 replaces the conflicting convergence limits; E2-A5 requires native connectors for the same-map case; E2-A6 replaces the worker-frame roundoff explanation and completes the consumer list. E2-A8 lists the superseded body statements. The round-1 review is an input to this specification, not a second set of conflicting acceptance rules.

Production paths below are relative to `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/`. Test paths are relative to `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/`. Layout units are model coordinates.

| Item | Exact contract | Authority / source receipt |
| --- | --- | --- |
| New constant | Add `static final double PREFERRED_NODE_GAP = 24.0;` at class scope of package-private `layout.graphstream.GraphStreamLayoutEngine`, adjacent to `NODE_RADIUS`. Package-private visibility; layout-owned; no public API or configuration exposure. | E2-A1/A7; R5 overlay below |
| Only numeric consumer of the new constant | `GraphStreamLayoutEngine.BoundarySizes.contentRingRadius(EnclosureHullKey)`: replace the additive `NodeSeparationProjection.MIN_GAP` with `PREFERRED_NODE_GAP`. | C0 source `GraphStreamLayoutEngine.java:503–512`; R5 overlay |
| Formula | For direct-node count `n > 1`, `R = (2.0 * maxRadius + PREFERRED_NODE_GAP) / (2.0 * Math.sin(Math.PI / n))`; retain the maximum of the production rendered radii. Retain `n <= 1 -> 0.0`. No label radius or average radius substitution. | E2-A1/A2; same source |
| Packing correction | In `layout.graphstream.ClusterPacking.isClear`, change only the rejection condition to `clearanceX < gap - CANDIDATE_EPSILON && clearanceY < gap - CANDIDATE_EPSILON`. `CANDIDATE_EPSILON` already exists as `1e-9`; do not add a second constant. | E2-A6(1); `ClusterPacking.java:141–151`; R5 overlay |
| Collision clearance | `NodeSeparationProjection.MIN_GAP` remains public `6.0`, including separation and relationship-rest uses. | E2-A7; `layout/NodeSeparationProjection.java:22`; H |
| Packing gutter | `BoundarySizes.SIBLING_GAP` remains `8.0`, at sibling and top-level packing. | E2-A7; `GraphStreamLayoutEngine.java:483` |
| Protected behavior | Preserve the force model, calibration, component-level cross-map clamp, node radii, prominence, text metrics, label placement, zoom policy, projection semantics, persistence, pins, idle thresholds and production safety limits. | E2-A7; H–J |

Do not parameterize production APIs, widen relationship additive clearance, add a user setting, introduce a compatibility path, modify core modules, or refactor to deliver this change. Test-only helpers may inspect package-private geometry or use reflection to observe existing private topology; this must not require new production accessors. The new constant has one direct production use; the consumers in H inherit its result through existing calls.

The intended production diff is exactly the two changes above. `TWO_MAP_FIRST_IDLE_STEP` and observation limits in F are **test** changes, separately visible in review. Remove obsolete test comments/assertions about last-ulp-only worker changes when replacing their contract in G.

**Receipt vocabulary.** All receipt paths beginning `R/` expand to the following run directory, outside the repository:

```text
/data/home/henry-arch/.local/state/pi/project-manager/runs/0233ff88d3cc54bbd61550c61fa917d6e2a5717f8a03be4e9aa63dcd47dd1cfa/pm-run-20261020-041612-a868d395/reports
```

`C0 source` means `git show cb8782fb3742f378b2907198fd2343292415da95:<repository path>`. Source line references identify the delivery base, not post-edit line numbers. Constants, formulas and prescribed test limits are requirements/source facts, not claimed measurements. All observed values below name a receipt and extraction procedure. Rounded display values must never replace the full precision receipts in assertions.

| Receipt ID | Files and how the evidence was produced |
| --- | --- |
| R1 | `R/design-review-1.md` and `R/frontier-review-1/`: isolated delivery-base compilation, baseline and minimal ring/epsilon overlays, real engine and corrected worker. Read findings 1–7; the exact-step conflict is finding 2 / appendix D-1 of the design discussion. |
| RP | `R/design-pivot-evidence.md`: synthesis of the sweeps, including corrected first-idle versus post-100 distinctions. Raw receipts below take precedence over rounded synthesis figures. |
| R0 | `R/sweep-1/README.md`, `manifest.json`, `verification.json`, `results.json`, `probe-src/SweepProbe.java`, and `raw/C0-reference.json`, `raw/C0-cross.json`: archived C0, ring 6.0, collision gap 6.0, epsilon OFF. Each JSON records runtime configuration, coordinates, first idle, and the following 100 frames. |
| R24 | Same sweep, `raw/A1-reference.json` and `raw/A1-cross.json`: ring 24.0, collision gap 6.0, packing epsilon ON, otherwise C0. Extract `first_idle_step`, `first_idle_elapsed_s`, `enclosures`, `post100.max_rms`, `post100.max`, and `post100.enclosures`. |
| RS | `R/sweep-3/summary.md`, `results.json`, `SpacingProbe.java` where archived, and S1/S1-A1 frame logs identified by the results manifest: minimal native-connector fixture, configs C0 and A1. Extract RS runs with `run_id == "S1"` and `"S1-A1"`, especially `post_first_idle_follow_through`; each run's top-level `enclosures` field is at first-idle-plus-100, not first idle. The frozen generator is the byte-pinned `R/frontier-review-5/SpacingProbe.java`. |
| RX | `R/sweep-2/README.md`, `CORRECTION.md`, `summary-samemap.md`, `results-samemap.json`, and RP: the corrected native-connector construction of the rejected large same-map shape. The earlier structurally-unrepresentable claim was withdrawn; failure to settle, not construction, excludes it. |
| R5 | `R/frontier-review-5/{provenance.json,commands.json,measurement-only-overlay.diff,SpacingProbe.java,ReviewSeedProbe.java,verify_seed.py,verified.json,logs/}`. `ReviewSeedProbe` applies the real raw engine and submits independently to the real worker. It prints per-node radii and positions, `METRIC raw/seed/idle/post100`, repair diagnostics and per-enclosure shifts. `verify_seed.py` independently recomputes per-node nearest-neighbour gaps from `POINT` records. Use `logs/C0-*` and `logs/ring24-*` by fixture name. |
| RF | Fresh specification-time candidate rerun: `/tmp/graph-initial-spacing-spec-evidence/{command.json,run.py,result.json,ring24-cross-rerun.log}`. Executed R5's `ring24-cross` Java argv with final argument `6000`, `JAVA_TOOL_OPTIONS=-Djava.awt.headless=true`; return code `0`, whole-process elapsed `202.71040298003936 s`, `FIRST_IDLE 3455`. The timing includes seed instrumentation and 100 follow-up frames; it is **not** a first-idle timing. The results reproduced R24's cross-map first-idle and post-100 geometry. |
| RD | Fresh candidate seed diagnostic: `/tmp/graph-initial-spacing-spec-evidence/{SpecFrameProbe.java,compile-frame-receipt.json,frame-receipt.json,frame.log}`. A package-local read-only probe uses R5's candidate-first classpath, the frozen factories in C and `ClusterProximityShould.fixture('D')`, fresh engine `apply` and independent worker `submit`. It prints `BoundarySizes.placements`, raw anchors, per-node raw/corrected differences, residuals and repair counters. Both compilation and run returned `0`. Relevant output is transcribed in B/G; reproduce it by the package-local observation procedure there. |
| RM | Supplemental mixed-prominence seed measurement: `/tmp/graph-initial-spacing-spec-evidence/{SpecMixedProbe.java,mixed-commands.json,mixed-C0.log,mixed-candidate.log,mixed-verified.json}`. Reconstructed C0 and candidate with K, copied S1 geometry and replaced its edges exactly as D specifies; applied real raw engine and independent real worker, printed own production radii/raw/corrected positions and flags. Python independently enumerated all `i != j` distances from `NODE ... corrected=` to obtain the transcribed minima/medians. All compile/run exit codes `0`. Probe SHA-256 `3d917b8f8a1b9b236fb45aa8bb7eeb7c52100ca88a09540b225b252ff0b0ac98`; verified JSON SHA-256 `1729350801664295f042257fa86c8f17a65d7c2f639618c1e3956e4a174cb5a7`. |
| RT | Supplemental S1 timing: `/tmp/graph-initial-spacing-spec-evidence/{SpecS1TimingProbe.java,timing-commands.json,timing-S1-C0.log,timing-S1-candidate.log}`. Using K's rebuilt C0/candidate, each configuration ran in its own JVM; reflectively copied S1 input before the clock, started `System.nanoTime()` immediately before worker construction, submitted and stepped against a shared 600-second deadline, stopped on receipt of first idle. No layout warmup or post-idle work inside the clock. Both configurations reproduced 84/58 steps and returned `0`. Probe SHA-256 `58b239eb0130bef4136d4d572d601ba595f822beb16a6aff56de36deb9ddef56`; C0 log SHA-256 `71ffe608879cc41158742e297da7323e962374d8466a7511c4954facbbbd083a`; candidate log SHA-256 `cb870b80f2e17038fe87eafcc1e7a0a4970248c39c3c561eac6d55f88418bf37`. |

Fresh `/tmp` files are supplemental receipts, not dependencies of acceptance. Their measured output, procedure and hashes are included here so losing a temporary build does not lose the contract. K reconstructs the archived baseline/candidate probe independently of the old `/tmp` build.

## B — Seed-frame metric, comparison order and ring eligibility

Implement the gate in test-only `layout.graphstream.InitialSpacingShould` using the frozen factories in C. Construct identical `GraphProjection` and pins for C0 and candidate. Use separate fresh engines/workers; an existing engine with retained particles is not a fresh seed measurement.

Define `rawSeed = freshEngine.apply(request)`, before any `step()` or worker correction. Define `seed = freshWorker.submit(request)` after the worker's complete boundary and edge-conflict correction pipeline, before any `step()`. **The primary gate measures `seed`, not `rawSeed`**. Store both sets of coordinates and diagnostics. `submit` is sample 0; later numbering is the count of completed calls to `worker.step()`, since the engine's first step can also carry frame index 0.

A qualifying enclosure has at least two **direct** nodes, all unpinned for this gate, all unique, all present in the projection and corrected frame. Ancestor membership is not direct membership. Required C fixtures have no pins. A missing/nonfinite point or duplicate membership fails fixture validation; do not drop it and obtain a smaller passing sample. Do not filter relationship or native-connector endpoints or select the widest enclosure.

For enclosure direct nodes `v[0..n-1]`, preserve their stored ring order for adjacency. For each `i` compute:

```text
r_i = GraphStreamLayoutEngine.NODE_RADIUS * prominenceScale(v[i])
      = 8.0 * (projection.prominence().get(v[i]).scale(), or 1.0 if absent)
g_i = min over j in the same direct-node list, j != i:
      hypot(x_i - x_j, y_i - y_j) - r_i - r_j
```

This is the same rendered-disc source used in `BoundarySizes` and the engine topology; labels, hit targets, hull padding and zoom are excluded. Use each node's own radius for mixed prominence. Sort a copy of the `n` values: minimum is the first; odd median is element `n/2`; even median is `(sorted[n/2-1] + sorted[n/2]) / 2.0`. Reject nonfinite gaps and medians. Do not round or clamp negative gaps, replace them with `MIN_GAP`, deduplicate equal gap values, or average enclosures.

Execute checks in this order:

1. Validate provenance/configuration, fixture topology/order/radii, projection coverage and frame success. Verify the C0 S1 idle qualification in C before promoting S1 to an acceptance fixture.
2. Validate raw seeding and independently record packing and repair eligibility below. Preserve this classification even if a later number happens to be exactly 24.
3. Recompute C0 seed metrics and compare each to the frozen C0 entry in D with absolute tolerance `1e-6`. A drifted control blocks comparison.
4. Compute candidate seed metrics. Match **each** qualifying enclosure to the same canonical hull key in C0, then assert `candidateMedian - baselineMedian >= 6.0 - 1e-6`. Every row must pass, including displaced, repaired and mixed-prominence rows. This is the primary E2-A2 gate; ring eligibility never exempts a row from it.
5. Compare candidate minima/medians to D's measured regression values within `1e-6`, without replacing the increase assertion. For eligible uniform unmodified rings additionally assert every cyclic adjacent pair's disc-edge gap is `24.0 ± 1e-6`. For two nodes check the one unordered pair; do not count the same point as its own neighbour.
6. Only then proceed to convergence and descriptive settled measurements. A raw ring passing while the corrected seed fails is a failure.

**Eligibility must be observed, not inferred from the median or anchor stability.** For each direct-node enclosure record all of the following from a real engine/worker run:

- All rendered radii; uniform means identical input radii. Record the analytic `R`, angle `2*pi*i/n`, raw anchor and each raw node. Independently reconstruct the composed anchor from each parent placement and the top-level placement, including `item.anchorX/Y`. Reconstruct the analytic node coordinate. Assert raw positions agree within the metric tolerance; never use corrected positions to infer the raw ring.
- Inspect the actual content `ClusterPlacement` (`item.anchorKey == null`) in `new BoundarySizes(projection).placements(hull)`. Record its `x/y`; no local content translation requires `(0.0, 0.0)`. Also record the composed packing translation from the top-level/ancestor path. For the conservative E2-A2 **no packing translation** classification here, that full translation must be zero as well. A globally translated ring can preserve all its gaps and still be classified as displaced; D retains its measured gap checks independently.
- Match every direct node in raw and corrected maps and compute `max hypot(seed_i - raw_i)`. Record anchors separately. No worker movement requires no node-coordinate change (except a recomputed machine-rounding discrepancy bounded by `1e-9`) **and** no boundary/edge repair displacement record touching that node. Inspect both correction-stage positions when a nonzero or ambiguous record occurs. A whole-frame repair elsewhere does not by itself prove this enclosure was repaired; identify affected keys. Finite but ambiguous eligibility is a failed evidence check, not automatic eligibility or an exemption from the median gate.
- For eligible rings additionally assert `abs(seedAdjacentGap - 24.0) <= 1e-6`. A measured movement around 11 units is a repair and cannot be absorbed by either tolerance. Record reason flags for `MIXED_RADII`, `PACKED_TRANSLATION`, and `WORKER_REPAIR` independently.

R5 reports raw/corrected differences; RD independently observes actual content placements and anchors. All required leaf content items have local placement `(0,0)` at candidate. The following freezes the conservative full-placement classification (RD `CONTENT`/`ANCHOR`, R5 `SHIFT`, RF for cross-map):

| Qualifying enclosure | Candidate raw composed anchor `(x,y)` | Largest raw-to-worker node shift | Classification / exact-gap eligibility |
| --- | --- | --- | --- |
| Relationship-free / Axioms | `(36.0, 0.0)` | `0.0` | uniform, assembly translated; ineligible; expected seed gap still 24 in D |
| S1 / Sub | `(0.0, 0.0)` | `0.0` | uniform unmodified ring; **eligible** |
| Cross / A-sub 0 | `(0.0, 0.0)` | `11.0` | worker repair; ineligible despite unchanged anchor |
| Cross / A-sub 1 | `(-128.56854249492380, 0.0)` | `11.0` | packed translation and worker repair; ineligible |
| Cross / A-sub 2 | `(128.56854249492380, 0.0)` | `0.0` | packed translation; ineligible; expected seed gap still 24 |
| Cross / B-sub 0 | `(-417.70562748477140, 0.0)` | `0.0` | packed translation; ineligible; expected seed gap still 24 |
| Cross / B-sub 1 | `(-546.27416997969520, 0.0)` | `0.0` | packed translation; ineligible; expected seed gap still 24 |
| Cross / B-sub 2 | `(-289.13708498984760, 0.0)` | `11.0` | packed translation and worker repair; ineligible |

The shift values of 11.0 are displayed rounded from the full log (some are `10.999999999999996`). R5's C0 cross seed has two edge repairs; candidate has four. In C0 `A-sub 1` and `B-sub 2` move by about 11; in candidate `A-sub 0`, `A-sub 1` and `B-sub 2` do. Never infer which nodes moved from a count of repairs alone.

For mixed prominence the **raw analytic** adjacent gap is `24.0 + 2*maxRadius - r_i - r_j`. A uniform radius above 8 still has a 24-unit analytic gap; a mixed ring does not. After packing displacement or worker repair the analytic equality is not a corrected-frame promise. Mixed fixtures still require the +6 median gate when compared under this protocol; they cannot be included by merely substituting an average radius.

## C — Frozen fixtures, C0 provenance and reproducible construction

All primary fixtures use workspace `00000000-0000-0000-0000-0000000000aa`; map A `00000000-0000-0000-0000-000000000001`, map B `00000000-0000-0000-0000-000000000002`; generation `1L`; empty pin list, empty relationship-resolution list, and `ProjectionDiff.between(p,p)` on initial submission. IDs are persisted `NodeReference` keys, never transient random keys. Preserve the constructor list order below; canonical edge normalization remains production behavior. Every enclosure's endpoint-key list contains its one persisted hull source unless explicitly stated otherwise. `SafeNodeLabel.of(label,label)` preserves the exact displayed text, including the existing `Fundation` spelling.

Use `LayoutCalibration.spikeDefaults()` unchanged: containment `1.0`, hierarchy `0.30`, same-map `1.0` (`layout/LayoutCalibration.java`). Keep workspace-derived SHA-256/lower-64-bit deterministic random seeding (`GraphStreamLayoutEngine.initializeGraph`), not a test-selected random seed. `GraphProjection.projected` supplies the production prominence map; assert all primary fixture scales are `1.0`, rendered radii `8.0`. Both relationship directions and connector attributes below are inputs to that calculation; do not suppress prominence after constructing a projection to force this result.

Worker geometry metrics are exactly `new AwtGeometryTextMetrics(new Font("Dialog", Font.PLAIN, 12), new FontRenderContext(null, true, true))` (`LayoutWorker.defaultMetrics`). EMPHATIC metrics use that implementation's derived bold font at `1.2f`. Do not substitute test monospace metrics or screenshot font widths. `BoundarySizes` uses the existing analytic content footprints and, only for empty enclosures, its character upper-bound box: width per character `16.0`, height `24.0`, padding `8.0`; content settle margin `8.0`, frame clearance `16.0`. These are C0 source constants, not values to retune. Record JDK/font configuration and hash actual measured label dimensions in regenerated receipts so a different installed Dialog font cannot silently change the experiment.

| Fixture | Frozen generator and counts | Qualifying enclosures / edge regime | Measurement receipts |
| --- | --- | --- | --- |
| Relationship-free | `ReferenceRepulsionFixture.referenceProjection(1L)`; one map, four nodes, four enclosures, zero edges | Axioms only, three direct nodes; no connector or relationship endpoints | R0/R24 `*-reference.json`; R5 `*-reference.log` |
| Same-map native connector S1 | Copy `SpacingProbe.singleMapFixture("S1")` exactly into a test-only factory; one map, four nodes, two enclosures, two native connectors | Sub only, four direct nodes, four endpoint incidences; no cross-map links | RS runs S1 and S1-A1; R5 `*-S1.log` |
| Cross-map | `TypedForcesShould.twoMapProjection(1L)`; two maps, 24 nodes, eight enclosures, four graph-relationship edges | Six sub-enclosures with four direct nodes each; four cross-map links, eight endpoint incidences, 32 particles including anchors | R0/R24 `*-cross.json`; R5 `*-cross.log`; RF |

**Relationship-free construction.** Node order is `n1`, `n2`, `n3`, `n4`, labels respectively `Fundation / Regularity`, `Replacement Scheme`, `Axiom of Choice`, `Theorem`; node map label `M`, final node factory boolean `true`. Enclosure order and membership are:

| Hull ID / label | Parent | Ordered direct nodes | Ordered child hulls | Root / tier |
| --- | --- | --- | --- | --- |
| `root` / `Axiomatic Set Theory` | absent | empty | `zfc` | true / SUPPRESSED |
| `zfc` / `ZFC` | `root` | empty | `axioms`, `defs` | false / EMPHATIC |
| `axioms` / `Axioms` | `zfc` | `n1`, `n2`, `n3` | empty | false / SUBTLE |
| `defs` / `Basic Definitions and Theorems` | `zfc` | `n4` | empty | false / SUBTLE |

Enclosure map labels are `M`. The singleton in `defs` is deliberately excluded from nearest-neighbour aggregation; it remains in coverage/geometry checks. Receipt: C0 `ReferenceRepulsionFixture.java` and R5's matching reference factory.

**S1 construction.** Node order `n0`, `n1`, `n2`, `n3`; labels `Node 0` through `Node 3`; node map label `Map`, final node boolean `true`. Enclosure order is **sub then root**: hull `sub`, label `Sub`, parent `root`, all four direct nodes in that order, no children, false/SUBTLE; then hull `root`, label `Root`, no parent, no direct nodes, one child `sub`, true/EMPHATIC. Enclosure map labels are `Map`. Edge insertion order is `n0 -> n1` then `n2 -> n3`, each node–node, via:

```java
ConnectorDescriptor.of(source.source(), target.source().persistedReference().get(),
    false, false, "source", "middle", "target");
EdgeContributor.nativeConnector(ConnectorSnapshot.of(0, descriptor), sourceEndpoint, targetEndpoint);
```

Occurrence `0` is used on each distinct source, exactly as the archived generator; do not replace these with graph relationships. `ProjectedEdgeKey.of(sourceEndpoint,targetEndpoint)` and a singleton contributor list construct each edge. Same-map edges have `crossMap == false` and bypass the cross-map-specific component clamp. Receipt: byte-pinned R5 `SpacingProbe.java`, `singleMapFixture`, `enclosuresForMap`, `addNative`.

**C0 qualification is a prerequisite:** RS S1 reaches first idle at step **84**, completes step **184**, and all **100/100** following frames remain idle; max RMS `0.03903402780948504`, maximum displacement `0.06011499052181499`. R5 `C0-S1.log` independently reports `FIRST_IDLE 84` and first-idle min/median `6.0/6.0`. Gate S1 only after reconstructing this C0 success under K. The **24-node single-map shape** (two roots, three subs per root, four direct nodes per sub) is forbidden for primary gating: RX/RP records no C0 idle within 6000 steps, with final-100 max displacement approximately `142.8`. It is constructible via native connectors; the earlier claim that it was structurally blocked is withdrawn. Do not transplant RS S1's qualification to that shape, to S3, or to X1. S3 is optional corroboration, not a substitution for S1.

**Cross-map construction.** Loop maps A then B, prefix `a` then `b`. For each map, loop subs `0,1,2`, each with node indices `0,1,2,3`. Node IDs are `a0_0` … `a2_3`, then `b0_0` … `b2_3`; labels `Boundary <sub> node <index>`, map label `Map`, final boolean `true`. Sub hull IDs `a-sub-0` … `a-sub-2` and `b-sub-0` … `b-sub-2`; labels `A-sub 0` … `B-sub 2`; map label `Map`, false/SUBTLE, parent `a-root` or `b-root`, no children. Direct-node order is ascending index. Enclosure order per map is sub 0, sub 1, sub 2, then root. Root A label `Axiomatic Set Theory`, tier SUPPRESSED; root B label `Topology`, tier EMPHATIC. Both have no parent/direct nodes, ordered sub children 0,1,2, map label `Map`, root flag true.

Append edges in ascending `i=0..3`, from node `a0_i` to `b0_i`. Each is `EdgeContributor.graphRelationship` wrapping `GraphRelationshipRecord` with sequence `i+1`, ID `20000000-0000-0000-0000-%012d` formatted with that sequence, direction FORWARD, empty unknown XML, node–node projected endpoints, singleton contributor list. Thus A-sub 0 and B-sub 0 each contain four endpoint incidences; the other qualifying enclosures contain zero. Roots contain zero **direct** endpoint incidences. The separate endpoint-type matrix in H uses additional fixtures; primary cross-map edges must not be replaced by enclosure endpoints. `GraphRelationshipRecord` forbids same-map endpoints, so it cannot construct S1.

**Baseline identity.** C0 is exactly delivery base `cb8782fb3742f378b2907198fd2343292415da95`, ring `6.0`, `MIN_GAP 6.0`, no epsilon subtraction in `isClear`. It is not the older approved-design commit or a candidate with only a disabled ring constant. R5 provenance was recorded at integration head `a002c536a9e37c342e2900fc4321911bbb369015`; all **74** source hashes in `provenance.source_sha256` were freshly compared against `git show cb8782fb:<path>` during this specification and matched. Thus the source provenance is C0 even though documentation commits advanced integration HEAD. Never overlay candidate classes onto a baseline classpath first by accident; record code-source locations and runtime constants.

| Receipt file | SHA-256 |
| --- | --- |
| `R/sweep-1/manifest.json` | `9d0da4b01a1a7bb73f50d2812514f9416422d2091cf6707c222e2087b1a7713b` |
| `R/sweep-1/raw/C0-reference.json` | `d0cd92c8bcff2211c6efb73b2cd923e86789fb89098075b30affded4273d97a2` |
| `R/sweep-1/raw/C0-cross.json` | `044c3210f50f3a59cb1c886eef500d5421481905a4df56500ceca01ebe861e61` |
| `R/sweep-1/raw/A1-reference.json` | `27299dfccc994b2bb42a3ae23d4048413c944538553a68d559a1585c2de38622` |
| `R/sweep-1/raw/A1-cross.json` | `d696e56cfe36864325792f489546c6a044e637ba970c0a99b57e8de078e19e04` |
| `R/sweep-3/results.json` | `00f64e3539492c03ba860b3450d9b4396c8ae6d81cfb4327b68cb76f87c97fb3` |
| `R/frontier-review-5/SpacingProbe.java` | `8aff8e74e8a4062b06f50de9e161c8d3e915742983bf2623cbdb67c861c1a0a7` |
| `R/frontier-review-5/ReviewSeedProbe.java` | `21a819f579557d960678dbd0db445b84f0e56c9335284ac7f585b1d75f505351` |
| `R/frontier-review-5/verified.json` | `3cfa95614bcf35ad6f1c20e0b4f0d42c969f6ab5c73cb6f1f6ea8695648c25a3` |

## D — Primary acceptance table and analytic oracles

The following values are **corrected worker seed** metrics, not first-idle metrics. Receipt: R5 `verified.json` rows, recomputed from `POINT seed` by `verify_seed.py`; C0 and candidate fixture-specific logs. RF independently repeats candidate cross-map values. C0 rounding near 6 is retained here; compare the unrounded doubles before formatting. All rows must pass B's +6 gate.

| Fixture / qualifying enclosure | C0 median | Candidate minimum | Candidate median | Candidate minus C0 median |
| --- | ---: | ---: | ---: | ---: |
| Relationship-free / Axioms | 6.0 | 24.0 | 24.0 | 18.0 |
| Same-map S1 / Sub | 6.0 | 24.0 | 24.0 | 18.0 |
| Cross / A-sub 0 | 6.0 | 17.14733824239645 | 20.573669121198225 | 14.573669121198225 |
| Cross / A-sub 1 | 6.0 | 17.14733824239645 | 17.14733824239645 | 11.14733824239645 |
| Cross / A-sub 2 | 6.0 | 24.0 | 24.0 | 18.0 |
| Cross / B-sub 0 | 6.000000000000009 | 24.0 | 24.0 | 17.999999999999993 |
| Cross / B-sub 1 | 6.000000000000009 | 24.0 | 24.0 | 17.999999999999993 |
| Cross / B-sub 2 | 6.000000000000011 | 17.14733824239645 | 20.573669121198225 | 14.573669121198215 |

The smallest observed increase is `11.14733824239645` (R5 `minimum_increase`); it is evidence for the prescribed 6.0 threshold, not a new threshold. Do not substitute the `17.147338...` minimum for the `20.573669...` median in A-sub 0 or B-sub 2.

Independent formula tests must calculate expected `R` without calling `contentRingRadius` to construct their expected result. For count 2 and radius 8, candidate `R=20.0` and pair distance `40.0`, so disc-edge gap `24.0`; C0 `R=11.0` and gap `6.0` (algebra from A, not a measured settle claim). For count 4, candidate `R=28.284271247461902` and C0 `R=15.556349186104047` (R5 raw/rest logs); every raw adjacent gap is 24 and 6 respectively before any repair. Ring order, not nearest-neighbour sort order, defines adjacency for that oracle.

Mixed-prominence oracle: copy S1's nodes/enclosures/order/IDs/labels/metrics and replace its two edges with native connectors `n2 -> n0` (occurrence 0) and `n2 -> n1` (occurrence 1), each `ConnectorDescriptor.of(source,target,false,true,"source","middle","target")`. The target arrow is required. The production `ProminenceCalculator` yields outgoing-target counts `[0,0,2,0]`, scales `[1.0,1.0,1.2,1.0]` and rendered radii `[8.0,8.0,9.6,8.0]` in ring order; assert these before geometry. Use the common workspace/generation and empty pins. This is a supplementary fixture, not a replacement for S1.

Maximum radius is 9.6; each adjacent radius-8 pair has analytic gap `24 + 19.2 - 8 - 8 = 27.2`. This formula is corroborated by E2-A8 and fresh RM's observed `27.200000000000003` for pair `n0/n1`; **27.2 is not required for pairs touching the larger disc**. For every adjacent pair use its own `r_i/r_j`. RM measured raw and corrected positions identical, no edge repairs, zero node/hull residual, verified/covered boundaries and all exhaustion/truncation flags false in both configurations. C0 ring radius is `17.819090885900998`, candidate radius `30.547012947258857`. Independent corrected-gap enumeration gives C0 per-node nearest gaps `[9.2,7.6,7.6,7.6]`, minimum/median `7.6/7.6`; candidate `[27.200000000000003,25.6,25.6,25.6]`, minimum/median `25.6/25.6`; median increase `18.0`. Receipt both configurations during implementation and retain the +6 comparison and mixed-pair analytic checks. No expected settled value is specified.

## E — First-idle and post-idle reporting

E2-A3 controls: **there is no +6.0 settled-spacing threshold**. Use the same qualifying lists/radii/metric as B at the first returned `LayoutFrame` whose `idle().idle()` is true. The unchanged production policy is eight consecutive qualifying frames, RMS `<= 0.05`, max `<= 0.10` (`PerceptualIdlePolicy.spikeDefaults`); report the actual returned flag rather than predicting idle from a single small movement. Assert convergence as F specifies, then report spacing.

For each fixture/configuration record the first-idle call number, per-enclosure minimum and median, all 100 following step samples, the maximum of their RMS values, and maximum of their maximum-displacement values. Also record the number of those frames still marked idle and spacing at first-idle-plus-100. A maximum RMS is `max(frame.idle().rms())` over that window; it is not RMS of the maxima or an aggregate over all nodes from all frames. Exclude the first-idle frame from the 100 follow-up frames. Correction can continue after the first idle observation; persistent idle is not a production guarantee.

Measured first-idle spacing (full precision receipts R0/R24 `enclosures`, R5 `C0-S1.log` and `ring24-S1.log` `METRIC idle`; displayed values below are rounded to six decimals):

| Enclosure | C0 min | C0 median | Ring-24 min | Ring-24 median | Direct connector / relationship endpoint incidences |
| --- | ---: | ---: | ---: | ---: | --- |
| Reference / Axioms | 6.000000 | 6.000000 | 26.225796 | 26.225796 | 0 / 0 |
| S1 / Sub | 6.000000 | 6.000000 | 7.021058 | 7.085864 | 4 / 0 |
| Cross / A-sub 0 | 6.000000 | 6.000000 | 7.571295 | 8.708413 | 0 / 4 |
| Cross / A-sub 1 | 6.000000 | 6.000000 | 10.969350 | 17.780094 | 0 / 0 |
| Cross / A-sub 2 | 6.000000 | 7.240551 | 6.000000 | 11.364345 | 0 / 0 |
| Cross / B-sub 0 | 6.000000 | 6.000000 | 6.000000 | 6.000000 | 0 / 4 |
| Cross / B-sub 1 | 6.000000 | 6.328832 | 6.000000 | 14.517296 | 0 / 0 |
| Cross / B-sub 2 | 6.000000 | 7.937570 | 12.278312 | 13.138935 | 0 / 0 |

Label an enclosure **median at collision floor** iff `abs(median - 6.0) <= 1e-9`, evaluated before rounding. At C0 this labels Reference/Axioms, S1/Sub, cross A-sub 0, A-sub 1 and B-sub 0; at ring 24 it labels cross B-sub 0. Report these as expected not to improve by any promised settled threshold. This is descriptive: the relationship-free C0 row also sits at the floor, so the label cannot establish that a connector is the binding cause. Record topology counts separately as above. Minimum at the floor does not imply median at the floor.

| Fixture/config | First-idle step | First-idle elapsed seconds where timed | Following 100 frames: maximum RMS | Following 100 frames: maximum displacement | Receipt / exact field |
| --- | ---: | ---: | ---: | ---: | --- |
| Reference C0 | 42 | 0.836386998 | 0.03166628586497854 | 0.06703720037109218 | R0 `C0-reference.json`, `first_idle_*`, `post100` |
| Reference ring24 | 37 | 0.748857183 | 0.04192325080465495 | 0.05729486451474486 | R24 `A1-reference.json`, same fields |
| S1 C0 | 84 | 0.897959881 | 0.03903402780948504 | 0.06011499052181499 | RS run S1 `post_first_idle_follow_through`; R5 step; RT first-idle stopwatch |
| S1 ring24 | 58 | 0.412135498 | 0.036061908543609966 | 0.05264809280985304 | RS run S1-A1, same fields; R5 step; RT first-idle stopwatch |
| Cross C0 | 756 | 161.521471602 | 6.911901186336649 | 29.693160379681416 | R0 `C0-cross.json` |
| Cross ring24 | 3455 | 272.630600943 | 0.2910542126686765 | 0.9902335696885642 | R24 `A1-cross.json`; first-idle geometry reproduced by RF |

S1's elapsed-to-first-idle column comes from the fresh RT stopwatch receipts (`897959881 ns` C0, `412135498 ns` candidate), while its post-100 maxima come from RS. The original RS `elapsed_seconds` includes follow-up and must not be used as first-idle timing. Both S1 follow-up windows remain idle `100/100` (RS). Do not claim that for cross-map merely because first idle was reached.

Post-100 spacing provides a second observation, never a replacement for first idle. Receipt R0/R24 `post100.enclosures` and R5 `METRIC post100`, displayed rounded:

| Enclosure | C0 post-100 min / median | Ring24 post-100 min / median |
| --- | --- | --- |
| Reference / Axioms | 6.000000 / 6.000000 | 25.141430 / 25.141430 |
| S1 / Sub | 6.000000 / 6.000000 | 6.532678 / 6.552148 |
| Cross / A-sub 0 | 6.000000 / 6.000000 | 7.215499 / 8.359547 |
| Cross / A-sub 1 | 6.000000 / 6.000000 | 10.996905 / 18.017710 |
| Cross / A-sub 2 | 6.000000 / 7.273484 | 6.000000 / 11.119722 |
| Cross / B-sub 0 | 6.000000 / 6.000000 | 6.000000 / 6.000000 |
| Cross / B-sub 1 | 6.000000 / 6.823511 | 6.000000 / 15.388346 |
| Cross / B-sub 2 | 6.000000 / 9.056570 | 10.090275 / 11.741611 |

Do not turn the synthesis's S1 post-100 `6.552` into a first-idle value; first idle is `7.085863751184222`. Describe settled shape as an equilibrium among containment, relationships, repulsion, hull/edge correction and pins, with the ring one contributor.

### E.4 Relationship-endpoint gaps (separate, descriptive, required)

E2-A8 preserves the obligation to *measure relationship-endpoint gaps separately*. That is a third, distinct quantity: it is neither the within-enclosure nearest-neighbour statistic above, nor the relationship spring rest length in H, nor the per-enclosure endpoint-incidence counts. Reporting incidences and rest targets does not discharge it. For every required fixture and every configuration, report the **realized** gap between the two laid-out endpoints of each projected edge.

Definition, applied to corrected published positions at first idle and at first-idle-plus-100:

```
realizedGap(edge) = distance(pos(first), pos(second))
                    - renderedRadius(first) - renderedRadius(second)
renderedRadius(node)      = NODE_RADIUS * prominenceScale(node)   // 8.0 * scale
renderedRadius(enclosure) = contentRingRadius(hull)               // the value in radiusById
```

For every edge record: fixture, edge identity (both endpoint keys), contributor kind (`nativeConnector` or `graphRelationship`), the `crossMap` flag, both endpoint types (node or enclosure), and the C0 and candidate values at both observation stages. Aggregate per endpoint-type pair (node–node, node–enclosure, enclosure–enclosure) as minimum and median.

Coverage requirements: at least one same-map node–node edge and one cross-map node–node edge in every configuration, plus node–enclosure and enclosure–enclosure edges wherever a fixture provides them. The cross-map fixture provides four node–node relationship edges; S1 provides exactly two same-map node–node native connectors and is the frozen same-map fixture. S3 (four connectors) is optional corroboration only: it must never be substituted for S1, and no acceptance number may be taken from it.

These values are **descriptive only**. No +6.0 threshold, no improvement requirement, and no claim that a realized gap tracks its rest length applies: a realized gap is the equilibrium outcome of all forces, while the rest length is a target. State explicitly, alongside the numbers, that the additive `MIN_GAP` term is unchanged at 6.0 while a total rest involving an enclosure endpoint does change. Receipts for every reported value are required; an unmeasured cell is written `NOT_MEASURED`, never inferred from a rest length.

## F — Convergence limits and explicit resolution of D-1

This is the sole acceptance observation-limit table, per E2-A4. It supersedes the preserved design's 2000-step and 30-second limits, including its acceptance-body repetitions. It changes tests, not production idle or safety policies.

| Primary fixture | First-idle call bound | Elapsed bound through first idle | Measured candidate step receipt |
| --- | ---: | ---: | --- |
| Relationship-free | 6000 | 600 seconds | 37, R24/R5 |
| Same-map native connector S1 | 6000 | 600 seconds | 58, RS/R5 |
| Cross-map | 6000 | 600 seconds | 3455, R24 and fresh RF |

Timing protocol for regenerated acceptance: use a single fixture/configuration per JVM, no fixture-level parallelism, no untimed worker/engine warmup, no repeated-trial selection. Construct immutable fixture inputs before starting; record JVM startup separately if desired. Start `System.nanoTime()` immediately before worker construction/submission; stop the first-idle timer immediately when its completed step frame is received. Include submission, queueing and correction; exclude compilation and the post-idle window. Use a shared deadline, not a new 600-second timeout for each future. After first idle, collect exactly 100 additional steps; apply the same bounded runner protocol to that collection and report its duration separately. Failure, exception, timeout, missing coverage, no idle by the bound, or exhausted/truncated verification blocks acceptance. A partial/failed run is diagnostic only; do not relabel its last frame as settled.

Historical sweep timing used its own recorded JVM allowances (R0 had an 1800-second per-run allowance). That larger measurement allowance is not the new acceptance bound. R24's measured cross first-idle `272.630600943 s` is below 600. E2-A4's rounded `213 s` and independent `216.6 s` figures are historical environment-dependent observations; they are not substituted for a missing clock receipt. RF's complete instrumented run was `202.71040298003936 s`, including 100 post-idle calls, so it supplies a fresh successful bounded run but does not invent a first-idle stopwatch number.

**D-1 decision: re-pin the exact step; do not introduce a window.** C0 pins 756 via `TypedForcesShould.TWO_MAP_FIRST_IDLE_STEP` and `.isEqualTo`, independently of the old 2000 loop limit. The minimal candidate repeatedly reaches 3455: R1, R24 and RF. RF candidate source hashes are `7840cb0c845cc4c06dbe417aa566959fb9bace2844968a402f13e89d9b1569e7` (`GraphStreamLayoutEngine.java`) and `29abf48532f7aa3e35b787afd607172669192e00eb39a0e3838873a56f024d68` (`ClusterPacking.java`), with only A's overlay. Preserve deterministic regression sensitivity under the pinned runner; a bounded window would hide a changed trajectory for which these receipts provide no need.

Implement this separately reviewable test change in `TypedForcesShould`:

```java
private static final int TWO_MAP_FIRST_IDLE_STEP = 3455;
private static final int INITIAL_SPACING_FIRST_IDLE_LIMIT = 6000;
// Observe the existing production idle flag, with F's shared wall deadline.
for (int step = 1; step <= INITIAL_SPACING_FIRST_IDLE_LIMIT; step++) {
    LayoutFrame frame = awaitWithinDeadline(worker.step(), firstIdleDeadlineNanos);
    assertThat(frame.failed()).isFalse();
    // Validate coverage, finite geometry and non-exhausted diagnostics as in G/J.
    if (frame.idle().idle()) {
        firstIdle = frame;
        firstIdleStep = step;
        break;
    }
}
assertThat(firstIdle).as("cross-map first idle within 6000 steps and 600 seconds").isNotNull();
assertThat(firstIdleStep).as("measured ring-24 first-idle step").isEqualTo(TWO_MAP_FIRST_IDLE_STEP);
```

`awaitWithinDeadline` is test-only and must fail when the shared remaining nanoseconds are nonpositive; `get(remaining, NANOSECONDS)` must not reset that deadline. The first-idle stopwatch must also be asserted `<= 600 seconds` after completion. Use the same 6000/600 observation bounds for the reference settle test and the new S1 test. Do not globally raise limits of unrelated proximity fixtures without a measured requirement.

Keep the existing cross-map 100-frame smoke envelope assertions `frame.idle().rms() <= 9.0` and `frame.idle().max() <= 36.0` intact (`TypedForcesShould.twoMapWorkspaceSettlesToIdle`, C0). They are existing bounded checks, not a promise of persistent idle or a new settled-spacing threshold; R0/R24 movement receipts above satisfy them. Update the obsolete baseline-only explanatory comment to identify both measured configurations. Do not tighten or delete these assertions to make the ring edit appear more stable. Any failure of the exact 3455 pin in the pinned environment requires a new receipt and explicit spec/review decision, not an implementation-time `isBetween` or `<= 6000` substitution.

## G — Corrected worker frame and proximity regression contract

Replace the obsolete premise in `ClusterProximityShould.initialFrameThroughWorkerKeepsSeedsWithoutPins`. The current pipeline is `raw engine -> BoundarySeparationCorrection (including node separation) -> EdgeConflictCorrection -> published corrected frame` (`LayoutWorker.accept`). Equality to just `new NodeSeparationProjection().project(raw)` is not the worker's contract. A larger floating-point tolerance is not the repair.

Retain `initialFrameEqualsTheDeterministicPacking` as a **raw engine** contract: exact deterministic packing/composed anchors, ring seed formula, coverage, nonoverlap/contact gutters. For worker assertions, independently reconstruct the full correction-stage result using the actual raw input, exact projection, visible endpoint set, production metrics and pins; record each stage and compare the worker output to that complete result. This stage comparison supplements, and cannot replace, the independent geometric checks below. In particular, copying a worker output into its own expected value or comparing two identical calls alone is insufficient.

Frozen fixture D is `ClusterProximityShould.relationshipProjection(1L)` / `fixture('D')`: maps A then B; four nodes `a-n0..3`, `b-n0..3`, sub then root per map, three node–node cross-map relationships for indices 0,1,2. Keep its existing long labels, tiers, ordering and source factory byte-identical. C shares that geometry with the existing pin `b-n0=(60.0,20.0)`; D has no pins. Do not confuse this eight-node/three-edge fixture with C's primary 24-node/four-edge cross-map fixture.

**Independent candidate fixture-D expectation, measured by RD** (same repair seen in R1 `ring-epsilon-initialFrameThroughWorkerKeepsSeedsWithoutPins.log`):

| Property | Required value / oracle |
| --- | --- |
| Node `a-n2` | raw `(-28.284271247461902, 3.4638242249419733e-15)`; corrected `(-28.284271247461902, 11.0)` |
| Node `b-n0` | raw `(-132.28427124746190, 0.0)`; corrected `(-132.28427124746190, -10.999999999999996)` |
| Other six nodes | No raw-to-worker coordinate change in RD; compare to their independently reconstructed raw coordinates, not a loose enclosure-wide bound |
| Anchors | Preserve raw anchor map; sub anchors are `(0,0)` and `(-160.56854249492380,0)` (RD); independently check the full map |
| Published node residual | `0`, recomputed independently on the final geometry |
| Boundary | `boundaryVerified=true`, `boundaryCovered=true`, `hullResidualViolations=0`; independently recomputed hulls satisfy nonoverlap and nesting |
| Repairs | Exactly two `repairsApplied`, one relocation round, one verification; no residual edge conflicts for D |
| Repair budgets | `indexBudgetExhausted=false`, `verificationBudgetExhausted=false`, `detectionTruncated=false`; measured inserted items `112`, pair checks `40`, verification work `98` |

Use per-coordinate absolute `1e-6` for the measured node targets, while retaining exact unchanged/pin comparisons where production returns exact values. Assert the affected node keys and signed displacements, not merely a maximum shift; also require a nonzero raw-to-worker shift on both affected nodes so removing edge correction fails this regression. The measured shifts around 11 are substantive. `ClusterPacking.CANDIDATE_EPSILON=1e-9` addresses candidate rectangle feasibility only; it does not explain, permit or hide these worker repairs.

On every worker fixture under test, including pinned C:

- Assert not failed, verified node residual, exact projection coverage, and finite node/anchor coordinates and motion diagnostics. For unpinned primary seeds and D, require node residual zero. Independently enumerate final-position disc pairs with the production collision predicate/radii and compare the count to `frame.residualViolations()`; do not first repair the final positions and count only the repaired result. Preserve existing impossible-pin behavior: explicitly reported residual/conflict is required rather than moving pins.
- Recompute hulls from corrected positions and the pinned production metrics. Assert non-nested enforced hull pairs do not overlap and enforced child vertices lie in their parent hull. Reuse/extend the test-only `BoundaryInvariantAssertions` logic without widening a production epsilon; SUPPRESSED hulls keep their existing enforcement semantics. Require unique residual/conflict pair-key bijection, not just a boolean from the producer.
- Assert active pin coordinates exactly. In particular C keeps `b-n0=(60.0,20.0)`. No pin coordinate may be snapped to the ring or adjusted by edge repair.
- Assert production budget counters do not exceed the unchanged limits and all exhaustion/truncation flags are false for passing primary measurements. Source `EdgeConflictCorrection` limits are rounds `4`, verifications `64`, inserted index items `262144`, pair checks `131072`, verification work `65536`, detected conflicts `4096`; source `BoundarySeparationCorrection.MAX_DISPLACEMENT_ROUNDS=4`. D's measured counters above are well within them. Never raise budgets to deliver this spacing change.
- Do not impose zero residual edge conflicts on every graph: RD's primary cross-map seed legitimately has four repairs over four rounds/eight verifications and **eight residual edge-conflict keys**, with zero node/hull residual and no exhausted flags (index items `1173`, pair checks `546`, work `2553`). Preserve/report that bounded result. Requiring zero for D remains a specific measured regression oracle. Preserve contact-gutter checks on packing output; apply final-frame hull invariants to repaired geometry rather than assuming repaired nodes still equal raw ring positions.

RD `frame.log` SHA-256 is `16f54b57d0e0685533197eab960e503279c00a81204a5f34430258371c2e29cd`; probe source SHA-256 is `969b3850549f55678cbebc3b12884b29e0cc9ac2090bfd7d2d155043bacaa752`. Reproduce by a package-local test/probe that obtains `fixture('D')`, calls fresh `GraphStreamLayoutEngine.apply` and independent `LayoutWorker.submit`, iterates nodes by source persisted ID, inspects `BoundarySizes.placements`/raw anchors, and prints the diagnostic accessors named above on the R5 candidate-first classpath. Acceptance must assert these expectations, not rely on the continuing existence of the temporary probe.

## H — Complete consumer coverage and relationship endpoint matrix

Tests must observe production consumer outputs; a test of the constant alone cannot prove propagation. Use package `layout.graphstream` for `BoundarySizes`/raw engine observation and reflection only in tests for existing private topology/particle state. Calculate expected geometry independently from inputs. No additional production seam is authorized.

| Consumer | Production location / mechanism | Required test oracle |
| --- | --- | --- |
| Fresh node seeds | `Seeds.nodePosition`, `BoundarySizes.contentRingRadius` | Independent formula in A, stored direct-node order and composed anchor; check raw node coordinates for all fixtures, plus count/mixed cases in I. |
| Content footprint | `BoundarySizes.placements`, content item with null anchor | Independently calculate `half = R + maxDisc + NODE_SETTLE_MARGIN`, content box `2*half` in both dimensions; empty enclosures retain label-sized boxes; compare actual item dimensions and local placement. |
| Sibling packing | `BoundarySizes.placements` / `ClusterPacking.packItems` | Compose child footprints and actual offsets, unchanged gutter 8.0. All packed boxes clear on at least one axis by `gap - CANDIDATE_EPSILON`; contact gaps stay between `SIBLING_GAP-CANDIDATE_EPSILON` and `SIBLING_GAP+CONTACT_GAP_TOLERANCE`, the existing `0.75` upper slack. Keep deterministic ordering and existing generated-projection regression. |
| Parent footprint | `BoundarySizes.footprint` | Independent min/max box union plus twice `FRAME_CLEARANCE`; carry `anchorX/Y=-(min+max)/2`, without recentering the assembly. Nested and asymmetric fixtures must expose a nonzero offset. |
| Top-level packing | `topLevelPlacements` and `Seeds.center` | Actual placement membership for roots/orphans, exactly once each; compose anchor offsets; retain 8.0 gutter and deterministic packing across maps. Do not infer correct top-level output solely from sibling tests. |
| Containment rests | `topology` CONTAINMENT links | Every direct node–anchor link rest is that enclosure's `R`; zero for singleton/empty content as applicable. Check actual `ForceLink.restLength`, not inferred settled distance. |
| Relationship endpoint radius | `topology` `radiusById`, `radiusOf`, RELATIONSHIP links | Nodes use their disc radius; enclosure endpoints use **content ring radius**, not footprint half-width, label extent or anchor particle radius (anchor particles themselves have radius 0). Exercise every row of the matrix below. |
| Hierarchy rests | `topology` HIERARCHY links | Independently compute `hypot(placement.x+item.anchorX, placement.y+item.anchorY)`; exactly one unique parent/child link, correct parent identity, no rest inferred from old baseline packing. |
| Anchor extents | `synchronize`, `configureAnchorExtents` | Inspect configured half-width/half-height and separation offsets: footprint dimensions / 2 and `-anchorX/-anchorY`. Wider content must propagate even when anchor disc radius remains 0. |
| Anchor repulsion | `TypedSpringBox.addBoundaryRepulsion` | Existing overlap-axis calculation uses both configured half extents plus `SIBLING_GAP`. Paired sibling anchors: independently compute overlap and selected push axis (x on tie), including asymmetric offsets; ancestor pairs remain excluded. Observe actual component, not a different long-run settle outcome. |
| Published worker geometry | `LayoutWorker.accept`, boundary then edge correction | Recompute final hull geometry, coverage, residuals, pins and budgets as G; record raw versus corrected seeds. D's signed 11-unit repairs make omission of the edge stage detectable. |

Relationship rest **formula** stays `firstEndpointModelRadius + secondEndpointModelRadius + MIN_GAP`. Only the additive `MIN_GAP=6.0` is unchanged unconditionally. The total changes when an enclosure endpoint radius changes. The following analytically derived rest targets use uniform radius-8 nodes and four-node leaf rings, with C0 `R=15.556349186104047` and candidate `R=28.284271247461902` (R5 `C0-rest.log`/`ring24-rest.log`). Each row is a separate test, not a loop that can skip an endpoint category:

| Link scope | Projected endpoints | Contributor construction | C0 rest | Candidate rest | Additional assertion |
| --- | --- | --- | ---: | ---: | --- |
| Same-map | node–node | native connector | 22.0 | 22.0 | `crossMap=false`; additive term 6.0 |
| Same-map | node–enclosure | native connector | 29.556349186104047 | 42.28427124746190 | `crossMap=false`; enclosure radius is R |
| Same-map | enclosure–enclosure | native connector | 37.112698372208094 | 62.568542494923804 | `crossMap=false`; both radii are R |
| Cross-map | node–node | graph relationship | 22.0 | 22.0 | `crossMap=true`; additive term 6.0 |
| Cross-map | node–enclosure | graph relationship | 29.556349186104047 | 42.28427124746190 | `crossMap=true`; enclosure radius is R |
| Cross-map | enclosure–enclosure | graph relationship | 37.112698372208094 | 62.568542494923804 | `crossMap=true`; both radii are R |

Use persisted enclosure endpoint sources belonging to the leaf hulls, not a fabricated same-map `GraphRelationshipRecord`. Mirror `EdgeRouterFixture`/`TypedForcesShould.connectorEdge` for `ConnectorDescriptor` and endpoint-source conversion. Provide two four-node leaf enclosures with distinct hulls in one map for same-map enclosure links, and map A/map B counterparts for cross-map links; no pins, generation 1, the common workspace/metrics/calibration. Keep node–enclosure nodes outside the target enclosure to avoid a degenerate self-link. Build one relationship under test per projection so it can be identified unambiguously. Reverse mixed endpoint order in an additional assertion to exercise endpoint lookup on both sides. Assert actual endpoint resolution yields one RELATIONSHIP link; an absent or collapsed link must fail, not leave an empty passing stream assertion.

The cross-map enclosure–enclosure totals `37.112698372208094 -> 62.568542494923804`, with measured additive remainder `6.0`, have a production-topology receipt in R5 `C0-rest.log`/`ring24-rest.log`. Other targets in the matrix are formula-derived test requirements; their production-observed values must be receipted when implementing the matrix. Never describe all relationship preferred distances as unchanged.

The force-cap argument remains empirical, per E2-A6(2): `TypedNodeParticle.move` clamps the combined native-repulsion/cross-map-attraction component at `CROSS_MAP_DISPLACEMENT_LIMIT=0.005`, then restores ordinary displacement. Containment, hierarchy, anchor repulsion and worker correction are not all governed by that clamp; pair separation need not be supplied by one endpoint. No `18/0.005` convergence lower bound follows. RP/R0's sweep records ring24 with `MIN_GAP24` failing to idle within 6000, while ring24 with `MIN_GAP6` idles at 3455; ring40 with `MIN_GAP24` idles at 698. These support keeping the additive clearance unchanged for this approved combination, not a theorem that raising it always diverges.

### H.1 `CANDIDATE_EPSILON` must be shown to be load-bearing

Independent review established that removing `CANDIDATE_EPSILON` from `ClusterPacking.isClear` still passes all eight fixed raw-packing fixtures. Those fixtures therefore do **not** demonstrate that the tolerance is required, and a passing suite must not be presented as evidence for it. Two outcomes are acceptable, and one of them must be recorded explicitly:

1. **Sensitivity test created.** Add a focused packing test that fails with the pre-change comparison and passes with the epsilon: an input whose clearance on the binding axis equals `gap` within at most one ULP, produced by larger packed items of the kind that newly expose the rejection. The test must assert the placement is accepted under the epsilon and that the same input is rejected by a deliberately restored `clearanceX < gap` comparison (a mutation switch confined to the test build, never to production). Record the exact item dimensions, offsets and the computed clearance so the one-ULP condition is verifiable rather than asserted.
2. **Necessity recorded as unestablished.** If such an input cannot be constructed, the specification and the delivery report must state `EPSILON_NECESSITY = NOT_ESTABLISHED` with the reason, and the epsilon must be described as an authorised but unfalsified tolerance correction. It may not be claimed as fixing an observed failure.

In both cases the epsilon stays scoped to `ClusterPacking.isClear` and is never extended to any other comparison.

## I — Edge cases and transition oracles

Use test-only factories with explicit stable IDs, the C environment and actual production prominence. Unless stated otherwise, no relationships/pins and a fresh engine. Values labeled derived below follow A and the preserved source, not unperformed settled measurements. Run final-frame coverage/finiteness/pin/hull/budget checks from G/J after worker submission.

| Case | Executable construction / transition | Required oracle |
| --- | --- | --- |
| Empty projection / empty enclosure | `GraphProjection.projected(1L, empty...)`; separately a root with no nodes/children | Empty projection yields empty coordinate maps, no NaN and no division by zero. Empty enclosure `R=0.0`; existing label-size footprint remains; no nearest-neighbour statistic is fabricated. |
| Singleton | One root with one direct unpinned node | `R=0.0`; raw node equals its composed anchor; footprint includes disc and existing margins. No qualifying enclosure, so report seed metric as inapplicable rather than zero/Infinity. |
| Two nodes | One leaf enclosure, two radius-8 direct nodes | Independent raw `R=20.0`, pair gap 24.0 (C0 R 11.0, gap 6.0), source formula; raw count 2 retained. Corrected-seed +6 gate and conditional exact gap apply after B's eligibility check. |
| Mixed prominence | D's frozen S1-derived native-connector fixture, radii `8,8,9.6,8`, C0/candidate matched | Assert production scales, max-radius ring formula, per-pair formula from D, nearest-neighbour median using own radii. No unconditional 24 pair assertion. Match RM's corrected minima/medians `7.6 -> 25.6` and assert the +6 gate. |
| Nested / mixed content | Reference plus `ReferenceRepulsionFixture.reparentedProjection(2L)`; additionally existing `ClusterProximityShould` dual/asymmetric fixtures | Independently compose content, child and ancestor placements; verify offsets, nesting and gutters; distinguish raw hierarchy changes from worker repair. No child repacking presumed to preserve ancestor coordinates. |
| Multiple maps | Primary cross-map and existing four-map/top-level proximity fixtures | All roots packed once; nonoverlap/gutters, complete canonical coverage, reproducible positions; primary cross-map seed medians D and convergence F. No new every-enclosure settled widening claim. |
| Active pins | Existing proximity fixture C pin `b-n0=(60,20)`; add a direct-ring-node pin case | Exact pinned coordinates across apply/step/reset; no fresh-ring placement overrides a pin. Pinned enclosures are excluded only from the unpinned primary spacing gate; pin/conflict checks remain mandatory. Conflicting pins retain explicit residual/conflict semantics. |
| Fresh engine and explicit reset | Apply S1, take one step, `engine.reset()`, then read via same request; compare against independent fresh engine | Raw reset positions equal fresh raw positions for the same projection/workspace/pins; fresh 24 ring is restored. Do not use worker `restart()` to pretend this is reset. |
| Healthy worker restart | Submit S1; take one step; record worker state; call `restart()` while healthy; step in FIFO order | Source `LayoutWorker.runRestart` is a no-op unless `failedEngine`; compare next frame/diagnostics with a control worker advanced by the same sequence without restart. No re-seed, loss of pins or invented new 24-gap state. Preserve existing failed-engine restart tests. |
| Retained particles after topology change | Apply reference generation 1, take one step; apply its reparented generation 2 with the real `ProjectionDiff` | Raw engine positions of every surviving node/anchor stay at the recorded pre-change raw state; current topology, extents and rests update. Compare by stable keys before worker correction; do not require the old particles to jump to the new ring. Corrected output must satisfy G rather than raw equality. |
| Incremental addition | S1 generation 1, take one step; generation 2 adds persisted node `n4` to Sub at the end, label `Node 4`, `Map`, boolean true, same two native connectors | Surviving raw particles retain coordinates. The added particle alone uses the new five-node ring at direct index 4, with `R=(2*maxRadius+24)/(2*sin(pi/5))`, centered at freshly composed seed anchor as existing `Seeds` defines it, not necessarily the retained live anchor. Assert radius/topology/coverage update. Worker correction may move unpinned nodes; no whole-enclosure exact 24 promise on this retained/addition frame. |

For reset/retention/addition assertions observe the raw engine before applying worker corrections. Never feed corrected coordinates back as a pre-change raw reference unless production actually does so. Tests must distinguish healthy restart, explicit raw-engine reset, failed-engine restart and new-workspace initialization; these are different existing lifecycle operations, not alternative names for a new spacing reset feature.

## J — Error handling, determinism and finite geometry

The change introduces no new recovery or production error policy. Preserve existing validation and failure behavior in `LayoutFrame`, `LayoutWorker`, `BoundarySeparationCorrection`, `EdgeConflictCorrection` and the engine. For every acceptance run:

- Validate all requested nodes/anchors are present exactly once and there are no obsolete keys. Reject nonfinite coordinates/radii/rest lengths/footprint dimensions, nonpositive measured label dimensions, nonfinite/negative motion summaries, and missing metric values. Do not convert exceptions, NaN or a timeout into an empty success row.
- Retain the formula's early count guard. Exercise zero/one/two counts so division by `sin(pi/n)` cannot be used for n 0 or 1. For extreme but finite fixture inputs, require either an existing explicit failure or finite output; adding clamping, arbitrary fallback geometry or global clearance changes is outside scope.
- A failed frame must never be counted as seed/idle acceptance. Observe failure frames and retained last-valid-state semantics in existing `LayoutWorkerShould`; `BoundarySeparationException("Boundary separation did not cover every residual pair")` remains a failure. Report residual covered pin conflicts explicitly. Do not silently promote `boundaryCovered` to `boundaryVerified`.
- Preserve all correction round/work limits and report flags/counters. Budget-exhausted or truncated samples may be saved diagnostically; they do not demonstrate a successful geometry/spacing gate. A per-fixture deadline expiry fails once and closes the worker; no retry until one trajectory happens to pass.
- Repeat fresh raw and worker seed construction with the exact same workspace/order/environment; compare coordinate maps, coverage, repair signatures and metrics. Keep existing canonical reorder regression for reorderings that preserve direct-node ring order. A permutation of direct-node order changes ring index inputs and is not automatically expected to keep the same coordinates. Retained-particle or different-workspace runs cannot be substituted for fresh repeatability.

Use the unchanged finite positive source radii and default-scale fallback; no value from a label's width or a zoom factor may replace them. Keep both packing's `1e-9` feasibility epsilon and B's `1e-6` measurement tolerance in their stated domains. No eleven-unit coordinate allowance is authorized.

## K — Environment, exact verification commands and evidence gates

Use JDK `/home/henry/.sdkman/candidates/java/21.0.8-zulu`, headless via `JAVA_TOOL_OPTIONS=-Djava.awt.headless=true`, installed `gradle` (not `gradlew`/Maven), Java 8 target. Run from the implementation worktree root with the repository-required Gradle execution privileges. The commands below are the implementation verification contract; this specification-only delivery does not claim those future tests exist or have run.

```bash
export JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"
export JAVA_TOOL_OPTIONS=-Djava.awt.headless=true
java -version
gradle --version
gradle --no-daemon --max-workers=1 :freeplane_plugin_graph:compileTestJava
gradle --no-daemon --max-workers=1 :freeplane_plugin_graph:test --rerun-tasks -PTestLoggingFull --tests 'org.freeplane.plugin.graph.layout.graphstream.InitialSpacingShould'
gradle --no-daemon --max-workers=1 :freeplane_plugin_graph:test --rerun-tasks -PTestLoggingFull --tests 'org.freeplane.plugin.graph.layout.TypedForcesShould' --tests 'org.freeplane.plugin.graph.layout.graphstream.ClusterProximityShould' --tests 'org.freeplane.plugin.graph.layout.graphstream.ClusterPackingShould' --tests 'org.freeplane.plugin.graph.layout.BoundarySeparationShould' --tests 'org.freeplane.plugin.graph.layout.LayoutWorkerShould'
gradle --no-daemon --max-workers=1 :freeplane_plugin_graph:test -PTestLoggingFull
gradle --no-daemon --max-workers=1 :freeplane_plugin_graph:verifyGraphBundle
git diff --check
git diff -- freeplane_plugin_graph/src/main/java
git diff -- freeplane_plugin_graph/src/test/java
```

Configure the acceptance harness not to run fixtures concurrently; `--max-workers=1` does not by itself guarantee a test class has not created parallel workers. Keep each fixture/configuration serial with no selection of a fastest or best run. Preserve complete stdout/stderr, XML test results, command argv, exit status, Java/Gradle version, timestamps, source and jar hashes, environment, workspace/generation, metric definitions, topology signature, raw/corrected coordinates, packing classification, correction diagnostics, clock boundaries and all idle/follow-up samples. A copied prior report cannot stand in for current candidate verification.

**Replaying the archived raw/worker measurements without relying on the old `/tmp` tree.** The following exact Python invocation reconstructs C0 sources from the pinned commit, verifies all R5 source hashes, compiles R5's two-file overlay and probes, then runs reference/S1/cross/rest on both configurations. It writes only to a fresh temporary directory. Cached dependency paths are taken from R5's recorded javac classpath; if unavailable, resolve the same versions/checksums from Gradle and record the replacement locations before running. Do not replace a dependency version. This receipt replay complements, rather than replaces, the Gradle implementation suite.

```bash
python3 - <<'PY'
import hashlib, json, os, pathlib, subprocess, tempfile, time
repo = pathlib.Path.cwd()
reports = pathlib.Path('/data/home/henry-arch/.local/state/pi/project-manager/runs/0233ff88d3cc54bbd61550c61fa917d6e2a5717f8a03be4e9aa63dcd47dd1cfa/pm-run-20261020-041612-a868d395/reports')
r5 = reports / 'frontier-review-5'
base = 'cb8782fb3742f378b2907198fd2343292415da95'
prov = json.loads((r5 / 'provenance.json').read_text())
cmds = {r['label']: r['command'] for r in json.loads((r5 / 'commands.json').read_text())}
root = pathlib.Path(tempfile.mkdtemp(prefix='initial-spacing-replay-'))
for d in ('src', 'empty', 'C0', 'candidate', 'probes'):
    (root / d).mkdir()
sources = []
for relative, expected in prov['source_sha256'].items():
    data = subprocess.check_output(['git', 'show', base + ':' + relative], cwd=repo)
    assert hashlib.sha256(data).hexdigest() == expected, relative
    p = root / 'src' / relative
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_bytes(data)
    sources.append(str(p))
java = '/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin/java'
javac = java + 'c'
recorded = cmds['compile-C0']
jars = recorded[recorded.index('-classpath') + 1]
assert all(pathlib.Path(p).is_file() for p in jars.split(':')), 'resolve pinned jars first'
env = dict(os.environ, JAVA_TOOL_OPTIONS='-Djava.awt.headless=true')
receipts = []
def run(label, argv, timeout=600):
    start = time.monotonic()
    with (root / (label + '.log')).open('w') as out:
        result = subprocess.run(argv, env=env, stdout=out, stderr=subprocess.STDOUT, timeout=timeout)
    receipts.append(dict(label=label, command=argv, returncode=result.returncode,
                         elapsed_s=time.monotonic()-start))
    (root / 'commands.json').write_text(json.dumps(receipts, indent=2) + '\n')
    assert result.returncode == 0, (label, root)
def compile_to(label, out, cp, files):
    run(label, [javac, '--release', '8', '-encoding', 'UTF-8', '-implicit:none',
        '-sourcepath', str(root/'empty'), '-classpath', cp, '-d', str(out)] + files)
compile_to('compile-C0', root/'C0', jars, sources)
engine = root/'src/freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java'
packing = engine.with_name('ClusterPacking.java')
# Edit only scratch copies, with unique old-text checks.
e = engine.read_text()
old = 'static final double NODE_RADIUS = 8.0;'
assert e.count(old) == 1
e = e.replace(old, old + '\n    static final double PREFERRED_NODE_GAP = 24.0;')
old = '(2.0 * maxRadius + NodeSeparationProjection.MIN_GAP)'
assert e.count(old) == 1
e = e.replace(old, '(2.0 * maxRadius + PREFERRED_NODE_GAP)')
engine.write_text(e)
s = packing.read_text()
old = 'clearanceX < gap && clearanceY < gap'
assert s.count(old) == 1
packing.write_text(s.replace(old, 'clearanceX < gap - CANDIDATE_EPSILON && clearanceY < gap - CANDIDATE_EPSILON'))
compile_to('compile-candidate', root/'candidate', str(root/'C0')+':'+jars,
           [str(engine), str(packing)])
compile_to('compile-probes', root/'probes', str(root/'C0')+':'+jars,
           [str(r5/'SpacingProbe.java'), str(r5/'ReviewSeedProbe.java')])
for config in ('C0', 'candidate'):
    cp = ':'.join([str(root/config), str(root/'probes'), str(root/'C0'), jars])
    for fixture in ('reference', 'S1', 'cross', 'rest'):
        args = [java, '-cp', cp, 'org.freeplane.plugin.graph.layout.graphstream.ReviewSeedProbe', fixture]
        if fixture != 'rest':
            args.append('6000')
        run(config+'-'+fixture, args)
print(root)
PY
```

The replay's whole-process timeout is an additional bound on the diagnostic executable; it is not a claim that it measures F's exact first-idle clock boundary. The executable fails if no idle is seen in 6000 calls and performs 100 subsequent calls; use the implementing tests for the explicit first-idle and follow-up timers and all G budget flags. Confirm runtime code-source location and constants in regenerated evidence.

**Required verification gates before final implementation audit:**

1. Freeze/verify C0 and fixture signatures, including S1 C0 first-idle qualification and source hashes. Reject fixture substitutions, especially the nonsettling 24-node same-map shape.
2. Review A's two production edits and F's exact-step re-pin as separate visible diffs. No core-logic change beyond A is authorized.
3. Pass B/D for every enclosure with raw/corrected classification and separately validated radii. Complete H's endpoint/consumer matrix, I's transitions and G's D repair/pin invariants. Reproduce D's supplemental mixed-prominence and E's timed S1 receipts.
4. Pass F's bound/exact pin and existing hold envelope; publish E's full first-idle and 100-frame reports. Zero failed/timeout/exhausted/truncated primary runs.
5. Pass the focused and full graph-plugin test suite and bundle verification commands above. Preserve XML/log receipts and inspect the production/test diff and whitespace check. No generated performance or UI evidence may be asserted from an unrelated build.
6. Perform the final specification-fidelity audit against A–M and the protected source diff. Audit findings cannot be resolved by weakening the primary metric, skipping enclosures, changing C0 or silently relaxing the exact pin.
7. Publish E.4's realized relationship-endpoint gaps for every required fixture and configuration at both observation stages, with receipts. Missing endpoint gaps block delivery, exactly as missing per-enclosure settled spacing does.
8. Record the H.1 outcome: either the sensitivity test passes with the mutation switch proving the pre-epsilon comparison fails the same input, or `EPSILON_NECESSITY = NOT_ESTABLISHED` is stated with its reason.

**Unconditional real-application evidence gate.** Paired captures from the *same build* are required before final delivery. This is not conditional on making a physical-pixel claim: design verification strategy item 6 requires it, and E2-A8 keeps that obligation operative.

Required capture set, all at zoom 1.0, each consisting of an initial view and the frame identified as the **actual first IDLE** state (worker call number and the returned `idle()` flag, never a screenshot guessed to be settled):

1. a direct-node enclosure case that exercises the wider ring;
2. a nested and/or multi-map case;
3. a case with at least two active pins, to show pin coordinates retained.

Each capture must record build provenance (commit OID and the same artifact set used for the model-space gates), zoom factor, the logical-pixel measurement of at least one named gap, the display scale factor and the device transform separately. Logical pixels are the required unit. Physical-pixel statements are permitted only when display scale is recorded, and must be labelled as derived. A capture from an unrelated build is not acceptable evidence. Until these exist, the run remains `NOT_MEASURED` for UI evidence and the delivery gate is open; the model-space seed gate is unaffected either way.

## L — Traceability and supersession ledger

| Input | Resolved requirement / evidence | Spec sections |
| --- | --- | --- |
| Round-1 finding 1, BLOCKER: ring24 fails settled +6 | Use amended corrected-seed +6 for every qualifying enclosure; keep settled floor/topology report, no universal settled increase | B, D, E, M |
| Round-1 finding 2, BLOCKER / D-1: conflicting limits and exact pin | One 6000-step/600-second table; measured 3455 exact re-pin, equality retained and separately reviewed | F, K |
| Round-1 finding 3, MAJOR: ~11-unit repair is not last-ulp drift | Full worker pipeline, independently pinned signed fixture-D repairs, residual/hull/pin/finiteness/budget checks; packing epsilon scoped separately | A, B, G |
| Round-1 finding 4, MAJOR: invalid displacement lower bound | Component-level clamp description and empirical combination-specific evidence; no claimed 3600-step theorem | H, M |
| Round-1 finding 5, MINOR: incomplete consumers / changing rest totals | Complete consumer list plus six endpoint/scope cases; additive 6 unchanged, enclosure totals change | A, H |
| Round-1 finding 6, MINOR: reproducibility / transitions | Exact factories, IDs, labels, order, metrics, calibration/prominence, C0 pin, measurement clocks and transition oracles | B, C, F, I, K |
| Round-1 finding 7, MINOR: metric and pixel conventions | Direct unpinned qualifier, own radii, i != j, median convention, tolerance ordering, logical/physical scale separation | B, D, K |
| E2-A1 | Preferred initial ring gap 24; collision 6; sole numeric ring consumer | A, D |
| E2-A2 | Corrected seed median +6, frozen C0, precise metric and uniform/unmodified eligibility; mixed formula | B–D |
| E2-A3 | First-idle and post-100 report, floor label, separate topology; no settled increase gate | E, F |
| E2-A4 | Single convergence table; no-idle/time/failure blocks; D-1 explicitly re-pinned with receipts | F, K |
| E2-A5 | Native-connector S1, C0 step 84 qualification; large same-map shape forbidden | C, H |
| E2-A6(1) | Full corrected worker contract with independent substantive repair oracle | G |
| E2-A6(2) | Withdraw cap lower bound; empirical relationship-clearance exclusion | H |
| E2-A6(3) | Rest formula/additive versus total distinction and complete consumer coverage | A, H |
| E2-A6(4) | Fixture/metric/timing/scale conventions | B, C, F, I, K |
| E2-A7 | All protected behavior unchanged; no settings/schema/migration | A, J, M |
| Spec review 2, MAJOR 1: relationship-endpoint gaps omitted | E.4 defines the realized endpoint gap separately from nearest-neighbour spacing, endpoint-incidence counts and spring rest lengths, requires per-edge C0/candidate reporting at both stages with receipts, and is a delivery gate (K item 7) | E.4, H, K item 7, M |
| Spec review 2, MAJOR 2: application evidence was conditional | Unconditional paired same-build initial + actual-first-IDLE capture gate for direct-node, nested/multi-map and pinned cases, with provenance, zoom, logical pixels, display scale and device transform | K, M |
| Spec review 2 (contract reviewer): epsilon not falsified | H.1 requires a one-ULP sensitivity test with a test-only mutation switch, or an explicit `EPSILON_NECESSITY = NOT_ESTABLISHED`; the eight fixed raw-packing fixtures are declared non-discriminating | H.1, K item 8, M |
| E2-A8 | Preserved body is historical where contradicted: settled gate, exact gap without eligibility, same-map relationship wording, constant total rests, ulp rationale, cap lower bound and old limits must not re-enter implementation/tests | A, B, E–H, M |

The RP report's floor/connector explanation is empirical context, not permission to infer a cause from a median or to exclude endpoint nodes. Its earlier globally-unsatisfiable-at-any-ring conclusion was withdrawn. Use the amended authority and the exact receipts above when a historical phrase or rounded figure conflicts.

## M — Explicit non-promises, open measurements and delivery boundary

This change promises an initial layout ring parameter and the amended corrected-seed gate under frozen inputs. It does **not** promise universal settled-spacing improvement, a +6 settled threshold, exact ring gaps after packing displacement or worker repair, an exact 24 gap for mixed radii, wider relationship additive clearance, unchanged total rests for enclosure endpoints, persistent idle after the first observation, a theoretical 3600-step lower bound, pixel sizes independent of scaling, reseeding retained particles on every topology update, or any new user setting/menu/persistence field/migration.

The authoritative acceptance remains 24.0 preferred ring spacing, collision clearance 6.0, packing gutter 8.0, B's every-enclosure +6 seed comparison and conditional exact ring requirement, plus F/G/J's convergence and invariant checks. Do not widen parameters to chase descriptive settled results.

The following measurements are explicitly missing; none is filled with a guessed value:

| Missing observation | Status | Required completion / effect |
| --- | --- | --- |
| Paired same-build initial and actual-first-IDLE application captures at zoom 1.0 (direct-node, nested/multi-map, pinned), with logical-pixel measurements, display scale and device transform | NOT_MEASURED | **Mandatory delivery gate**, not an optional observation. The unconditional real-application evidence gate in K requires these before final delivery regardless of whether any physical-pixel claim is made. |
| Realized relationship-endpoint gaps per E.4 | NOT_MEASURED | Required report with receipts at both observation stages; missing gaps block delivery. |
| `CANDIDATE_EPSILON` sensitivity | NOT_ESTABLISHED | Resolve per H.1: provide the one-ULP sensitivity test, or state this status explicitly in the delivery report. The eight fixed raw-packing fixtures do not establish it. |

Remaining matrix outputs in H are analytically specified test oracles; implementation must record their actual consumer values and passing tests, not claim them as measurements already performed. Full-suite/bundle results belong to the eventual implementation evidence; this specification makes no claim that a ring24 implementation has passed those checks.

Specification delivery consists of exactly this new file, `docs/superpowers/specs/2026-10-20-graph-initial-spacing-spec.md`. Production sources, tests, archived reports, approved design and its amendment remain untouched by this delivery. Any implementation that needs additional production behavior must return for a separately reviewed scope/spec change instead of silently changing these gates.
