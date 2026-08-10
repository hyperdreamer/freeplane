# GraphStream 1.3 Dependency Gate Report

Date: 2026-08-10

## Decision

Technical gate: PASS with implementation constraints.

Policy gate: APPROVED on 2026-08-10. The maintainer selected the LGPLv3 distribution option for the three unchanged GraphStream jars, with the canonical LGPLv3 text, GraphStream attribution, artifact checksums, and source links included in the plugin distribution.

## Artifacts

- gs-core 1.3 SHA-256: `2d6a6f92f86c624fcbf468fc7e9cb9c8e3fb7e14c72ad578edb04cc36b0b66cd`
- pherd 1.0 SHA-256: `9e74f3702d13756faece5987147c937c09b6837a38ed32199f59c26697b94230`
- mbox2 1.0 SHA-256: `3c2db334867211f385a2d62d061818268443f361381f78bbc53f9e897e145983`
- gs-ui and Scala were not used.

## Fast Gate

- Java: probe compiled with `--release 8` (class-file major version 52) and ran on OpenJDK 11.0.32 and 21.0.12. The repository-requested Zulu 21.0.8 installation was absent.
- Determinism: same seed produced bit-identical positions initially and after 100 steps; a different seed differed.
- Pinning: frozen drift was exactly 0 across 500 steps; the node moved after unfreezing.
- Dynamic mutation: 100 cycles added and removed 1,000 nodes total; particle, spring, and neighbor-reference counts returned to baseline after every cycle.
- Worker lifecycle: 25 start/stop cycles on a plugin-owned single-thread executor left zero non-daemon threads and no background failures on Java 11 and 21.
- GraphStream `LayoutRunner` is prohibited. Its `release()` nulls `pumpPipe` before clearing `loop`; a deterministic barrier probe reproduces `NullPointerException` at `LayoutRunner.java:188` on Java 11 and 21.

## OSGi

- Freeplane-style packaging with three unchanged jars in plugin `lib/` and `Bundle-ClassPath` passed `bnd verify`.
- The bundle resolved and became ACTIVE in Freeplane's actual Knopflerfish 8.0.11 framework with the `bootdelegation=*`, app-parent, system-export, and listener settings from `BIN/props.xargs`.
- Ten representative classes loaded: `Graph`, `SingleGraph`, `FileSourceDGS`, `SpringBox`, `LinLog`, `GraphicGraph`, `ViewPanel`, `NTree`, `Receiver`, and `HTTPSource`. A graph operation succeeded, and `SpringBox` instantiated at quality 0.1.
- No duplicate classes and no Scala classes.
- No wrapper bundle, package exports, launcher changes, or `gs-ui` dependency are needed.
- Keep Freeplane's `Import-Package: nothing.*` convention. Knopflerfish rejects `java.*` imports, and a strict `com.sun.net.httpserver` import does not resolve under the default system packages.
- Negative controls confirmed that a bundle importing `java.*` is rejected at install and a mandatory `com.sun.net.httpserver` import fails resolution. The zero-import bundle succeeds under Freeplane's boot-delegation policy.

## Full Pipeline Protocol

This dependency-selection spike constructs and asserts the solver ledger directly. It does not claim to exercise the production map projection, stable-key diff, EDT swap, or Swing paint paths, which do not exist yet. Those paths have separate implementation-phase gates below.

- 20 maps.
- 2,000 projected visible nodes.
- 5,000 unique consolidated relationship edges: 3,500 same-map, 1,500 cross-map.
- 1,200 enclosure anchors (60 per map, binary hierarchy depth up to 5).
- 2,000 containment springs and 1,180 anchor hierarchy springs.
- Total: 3,200 particles and 8,180 springs.
- GraphStream SpringBox quality 0.10, deterministic seed 20260810.
- 400 complete warm-up iterations, then 300 timed complete iterations.
- Every iteration measured force, map-tier correction, position snapshot, bottom-up convex/smoothed hull fitting, and four-step label placement.
- Gate thresholds: force-step p95 <= 50 ms and complete-pipeline p95 <= 100 ms, allowing at least 20 force steps and 10 full geometry publications per second after the 150 ms debounce while pan/zoom remains decoupled on the EDT.

Reference machine: Intel Core Ultra 7 155H, 16 physical cores / 22 logical CPUs, up to 4.8 GHz, 62 GiB RAM, x86_64 Linux 7.1.4.

GraphStream's `layout.weight` changes preferred spring length, not stiffness. A small `SpringBox`/`SpringBoxNodeParticle` subclass therefore supplies typed attraction:

- weak containment attraction;
- weak anchor hierarchy attraction;
- hard aggregate cross-map displacement cap of 0.005 per particle per step;
- normal same-map relationship attraction.

Map-root separation translates all particles of an unpinned map by the root-derived correction. At the end of the root-only control run, 3 map-hull bounding boxes still overlapped; uniform map translation reduced exact hull intersections to zero while preserving internal geometry. A map containing any pinned projected node is treated as rigid and immovable by this correction; one movable side takes the full correction, while two blocked sides are reported with Unpin. The full spike was unpinned, so this pin/correction rule remains an implementation-phase acceptance test.

## Final Measurements

| Runtime | Build | Force p95 | Hull p95 | Label p95 | Complete p95 | Complete max | Heap | Exact map intersections |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Java 11.0.32 | 325.5 ms | 19.312 ms | 3.971 ms | 7.623 ms | 31.362 ms | 43.408 ms | 93.4 MB | 0/190 |
| Java 21.0.12 | 319.3 ms | 22.980 ms | 3.605 ms | 8.132 ms | 34.530 ms | 67.972 ms | 88.2 MB | 0/190 |

At the 2,000-node tier, the final label ladder chose 57 interior labels, 0 arc labels, 201 external/leader labels, and 942 hover-only labels. This is consistent with the specified density-based label suppression; emphatic map labels never use hover-only fallback. Every label still runs the interior and arc candidates before demotion, so hover-heavy output does not under-measure the ladder.

The exploratory settle diagnostic ended at RMS movement 0.014392 layout units and maximum movement 0.044531 after 1,000 total steps. It did not meet the probe's provisional RMS threshold of 0.01 (`consecutive_stable_steps=0`), so production idle thresholds are not yet calibrated. GraphStream's own stabilization value is unusable after external correction because `moveNode()` clears its energy history. Production should stop/pause based on measured perceptual displacement, not `getStabilization()`.

## Constraints For The Plan

1. Embed the three unchanged jars through the plugin's existing `lib`/`Bundle-ClassPath` mechanism.
2. Keep GraphStream behind `LayoutEngine`; do not expose GraphStream types outside its package.
3. Use a plugin-owned serialized worker. Never use `LayoutRunner`.
4. Implement typed springs in a minimal subclass; do not treat `layout.weight` as stiffness.
5. Keep SpringBox at its library default quality of 0.10. A recorded quality-1.0 control measured 134.80 ms/step on the smaller raw 2,000/approximately-5,000 graph and missed the interaction budget.
6. Cap aggregate cross-map displacement per particle at 0.005 and apply map-root correction as rigid uniform translation only to maps with no pinned projected node.
7. Use perceptual displacement for layout-idle detection.
8. Add the exact 3,200/8,180 generated workload as a repeatable diagnostic performance test with generous CI regression limits; keep the 100 ms p95 gate as recorded evidence, not a hardware-independent unit-test assertion.

## Residual Work

- Canvas paint/pan timing is an implementation-phase Swing concern; this gate measured the dependency-owned/off-EDT full geometry publication path required by the design.
- Full projection rebuild, stable-key diff, obsolete-generation discard, and batch-to-first-frame timing require the production projection/adapter and remain implementation-phase performance gates. The implementation target is batch p95 <= 150 ms and p99 <= 300 ms; the EDT immutable-state swap target is p95 <= 2 ms.
- The synthetic workload uses uniform map sizes and random cross-map links. Implementation stress tests must cover skewed map sizes, two- and three-map workspaces, concentrated cross-map clusters, and pinned map pairs.
- Verify exact dependency retrieval/checksums and notice files in the Gradle implementation task.
