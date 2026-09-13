# Graph Boundary Separation Performance Evidence

## Profile (Task 1)

Command: gradle :freeplane_plugin_graph:boundaryCorrectionProfile --rerun-tasks -PTestLoggingFull

Workload: reference-2000-5000, 2000 nodes, 1200 enclosures.

| stage | p50 (ns) | p95 (ns) | max (ns) |
| --- | ---: | ---: | ---: |
| workerTotal | 117554631 | 175256982 | 248790004 |
| directTotal | 112112466 | 160243257 | 189848521 |
| separation | 19356054 | 34452048 | 59581100 |
| hull | 791680 | 1744186 | 2702654 |
| plan | 91294643 | 123866731 | 155878250 |
| apply | 0 | 0 | 0 |

JFR command: ~/.sdkman/candidates/java/21.0.8-zulu/bin/jfr view hot-methods freeplane_plugin_graph/build/boundary-correction-profile/correction-profile.jfr

| rank | hot method | samples | share |
| --- | --- | ---: | ---: |
| 1 | org.freeplane.plugin.graph.geometry.HullGeometry.signOfSum(double[], int[], int, double[], int[]) | 637 | 14.78% |
| 2 | java.util.Objects.equals(Object, Object) | 565 | 13.11% |
| 3 | org.graphstream.ui.layout.springbox.Energies.clearEnergies() | 484 | 11.23% |
| 4 | java.lang.Double.isFinite(double) | 413 | 9.58% |
| 5 | org.freeplane.plugin.graph.geometry.HullGeometry.mergeComponent(double[], int[], int, double, int) | 332 | 7.70% |
| 6 | org.freeplane.plugin.graph.workspace.model.MapReferenceId.equals(Object) | 264 | 6.12% |
| 7 | java.util.HashMap.getNode(Object) | 141 | 3.27% |
| 8 | java.util.UUID.equals(Object) | 128 | 2.97% |
| 9 | java.util.Collections$UnmodifiableCollection$1.next() | 120 | 2.78% |
| 10 | java.lang.Math.scalb(double, int) | 117 | 2.71% |

Hotspot list:
- `BoundarySeparationCorrection.detect` child/parent containment scan: 1,180 enforced child/parent pairs with 8
  polygon vertices each, i.e. 9,440 `HullGeometry.contains` invocations per correction call. Its exact-arithmetic
  point-in-polygon leaf work is the top JFR cost (`HullGeometry.signOfSum` 14.78%, `HullGeometry.mergeComponent`
  7.70%, `Math.scalb` 2.71%). The scan lands in the `plan` stage (p50 91.3 ms, p95 123.9 ms), which meters
  `computeHulls` + `detect`; the separate `hull` stage itself is only p50 0.79 ms.
- `BoundarySeparationCorrection.detect` enforced-set membership: `enforced.contains(child)` and
  `enforced.contains(parent)` scan a 1,180-element `ArrayList<EnclosureHullKey>` with 2 lookups per each of the
  1,200 enclosures. `EnclosureHullKey.equals` compares unmodifiable `SourceNodeKey` lists element-wise, measured
  as `java.util.Objects.equals` 13.11% (455 samples under `ArrayList.contains` -> `EnclosureHullKey.equals` ->
  `UnmodifiableList.equals`) plus `EnclosureKey.equals` 1.58%.
- The `MapReferenceId.equals` 6.12% and `java.util.UUID.equals` 2.97% rows belong to that same
  `ArrayList.contains` membership scan rather than to hash lookups: 252 of the 264 `MapReferenceId.equals` samples
  and all 128 `UUID.equals` samples sit under `BoundarySeparationCorrection.detect` -> `ArrayList.contains` ->
  `EnclosureHullKey.equals` -> `EnclosureKey.equals` -> `SourceNodeKey.equals`. The remaining `MapReferenceId.equals`
  samples are 6 in `ProjectionEngine.project`, 4 in the GraphStream `Seeds.center` list `indexOf`, and 2 in
  `ProjectionDiff.between`. The separate `HashMap.getNode` row (3.27%) holds the hash-lookup cost (e.g.
  `BoundarySizes.sizeOf`, `BoundarySeparationCorrection.separate`/`detect`, `GraphStreamLayoutEngine.replaceLinks`).
- `NodeSeparationProjection.residualViolations` all-pairs distance scan: 2000*1999/2 = 1,999,000 `Math.hypot`
  calls per correction call, measured as `Double.isFinite` 9.58% plus `StrictMath.hypot` 1.69% (410 of 413
  `Double.isFinite` samples attributed to this loop). This is the `separation` stage, p50 19.4 ms, p95 34.5 ms.
- `NodeSeparationProjection.validateFinite` walks the 3,200 node + anchor positions through unmodifiable
  collection wrappers: `Collections$UnmodifiableCollection$1.next` 2.78% (92 of 120 samples).
- GraphStream spring-box layout `Energies.clearEnergies` 11.23%: 484 samples under `BarnesHutLayout.moveNode` ->
  `TypedSpringBox.setParticlePosition`, the per-move energy reset over the 3,200-particle graph. This cost sits in
  the layout engine (`LayoutEngine.apply` and worker submit), outside the measured correction stages.
