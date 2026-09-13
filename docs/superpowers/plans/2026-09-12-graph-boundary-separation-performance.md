# Graph Boundary Separation Performance Evidence

## Profile (Task 1)

Command: gradle :freeplane_plugin_graph:boundaryCorrectionProfile --rerun-tasks -PTestLoggingFull

Workload: reference-2000-5000, 2000 nodes, 1200 enclosures.

| stage | p50 (ns) | p95 (ns) | max (ns) |
| --- | ---: | ---: | ---: |
| workerTotal | 112811117 | 159970300 | 205370085 |
| directTotal | 110119429 | 130733887 | 156159504 |
| separation | 19252895 | 27414490 | 46747940 |
| hull | 750491 | 1197511 | 1927981 |
| plan | 89312644 | 108350332 | 137078346 |
| apply | 0 | 0 | 0 |

JFR command: ~/.sdkman/candidates/java/21.0.8-zulu/bin/jfr view hot-methods freeplane_plugin_graph/build/boundary-correction-profile/correction-profile.jfr

| rank | hot method | samples | share |
| --- | --- | ---: | ---: |
| 1 | org.freeplane.plugin.graph.geometry.HullGeometry.signOfSum(double[], int[], int, double[], int[]) | 586 | 13.97% |
| 2 | java.util.Objects.equals(Object, Object) | 572 | 13.63% |
| 3 | org.graphstream.ui.layout.springbox.Energies.clearEnergies() | 462 | 11.01% |
| 4 | java.lang.Double.isFinite(double) | 373 | 8.89% |
| 5 | org.freeplane.plugin.graph.geometry.HullGeometry.mergeComponent(double[], int[], int, double, int) | 363 | 8.65% |
| 6 | org.freeplane.plugin.graph.workspace.model.MapReferenceId.equals(Object) | 273 | 6.51% |
| 7 | java.util.UUID.equals(Object) | 135 | 3.22% |
| 8 | java.lang.Math.scalb(double, int) | 117 | 2.79% |
| 9 | java.util.Collections$UnmodifiableCollection$1.next() | 110 | 2.62% |
| 10 | java.util.HashMap.getNode(Object) | 100 | 2.38% |

Hotspot list:
- `BoundarySeparationCorrection.detect` child/parent containment scan: 1,180 enforced child/parent pairs with 8
  polygon vertices each, i.e. 9,440 `HullGeometry.contains` invocations per correction call. Its exact-arithmetic
  point-in-polygon leaf work is the top JFR cost (`HullGeometry.signOfSum` 13.97%, `HullGeometry.mergeComponent`
  8.65%, `Math.scalb` 2.79%). The scan lands in the `plan` stage (p50 89.3 ms, p95 108.4 ms), which meters
  `computeHulls` + `detect`; the separate `hull` stage itself is only p50 0.75 ms.
- `BoundarySeparationCorrection.detect` enforced-set membership: `enforced.contains(child)` and
  `enforced.contains(parent)` scan a 1,180-element `ArrayList<EnclosureHullKey>` with 2 lookups per each of the
  1,200 enclosures. `EnclosureHullKey.equals` compares unmodifiable `SourceNodeKey` lists element-wise, measured
  as `java.util.Objects.equals` 13.63% (462 samples under `ArrayList.contains` -> `EnclosureHullKey.equals` ->
  `UnmodifiableList.equals`) plus `EnclosureKey.equals` 1.76%.
- Enclosure-key equality reaches `MapReferenceId.equals` 6.51% and `java.util.UUID.equals` 3.22% through
  `SourceNodeKey.equals` during enclosure-key hash lookups (hull lookup maps and GraphStream `BoundarySizes`).
- `NodeSeparationProjection.residualViolations` all-pairs distance scan: 2000*1999/2 = 1,999,000 `Math.hypot`
  calls per correction call, measured as `Double.isFinite` 8.89% plus `StrictMath.hypot` 2.34% (372 of 373
  `Double.isFinite` samples attributed to this loop). This is the `separation` stage, p50 19.3 ms, p95 27.4 ms.
- `NodeSeparationProjection.validateFinite` walks the 3,200 node + anchor positions through unmodifiable
  collection wrappers: `Collections$UnmodifiableCollection$1.next` 2.62% (85 of 110 samples).
- GraphStream spring-box layout `Energies.clearEnergies` 11.01%: 462 samples under `BarnesHutLayout.moveNode` ->
  `TypedSpringBox.setParticlePosition`, the per-move energy reset over the 3,200-particle graph. This cost sits in
  the layout engine (`LayoutEngine.apply` and worker submit), outside the measured correction stages.
