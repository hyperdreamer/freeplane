# Graph Workspace Batch I Performance Design

**Status:** Approved by the user on 2026-08-21.

## Goal

Implement Graph Workspace Tasks 37 and 38 sequentially. Task 37 adds a deterministic, production-composed performance diagnostic and exact generated workspace fixtures. Task 38 consumes those diagnostics, calibrates measured bottlenecks, and records a strict-target performance report.

## Scope and sequencing

Task 37 is completed and committed before any Task 38 source is changed. Task 37 owns the Gradle diagnostic task, generated workload builders, timing ledger, percentile helper, and performance tripwires. Task 38 first runs the strict Task 37 diagnostic as a baseline, then changes only the six listed calibration/runtime files when measurements demonstrate a bottleneck, and finally writes the report.

The implementation runs in the isolated Batch I worktree on Java 21 while compiling the project for its Java 8 compatibility target. Existing Graph Workspace production types remain the source of truth; the diagnostic does not create a parallel graph implementation or expose a new production API.

## Task 37 architecture

`GeneratedWorkspace` deterministically constructs the reference workload and the smaller stress variants from a fixed seed. It produces valid `.fpg` documents and the exact output names `two-map.fpg`, `three-map.fpg`, and `reference-2000-5000.fpg` under `build/graph-performance/`. The reference workload contains 20 maps, 2,000 nodes, 5,000 edges split into 3,500 same-map and 1,500 cross-map relationships, 1,200 anchors, 2,000 containment links, 1,180 hierarchy links, 3,200 particles, and 8,180 springs.

`GraphWorkspacePerformanceDiagnostic` runs the generated scenarios through the existing projection, diff, layout, geometry, label, canvas-state, EDT-publication, and repaint composition. Each sample has an `AcceptedBatch.acceptedAtNanos` start and ends after the EDT CanvasState reference assignment. A `NanoClock` is injectable so tests can assert timing boundaries without relying on wall-clock values. `PerformanceMeasurements` stores named stage samples and computes sorted nearest-rank percentiles through `NearestRankPercentile.of(sortedNanos, p)`, whose index is `ceil(p * N) - 1`.

The diagnostic reports snapshot, projection, diff, mutation, force, correction, hull, label, full-worker, EDT-swap, repaint, and accepted-batch-first-frame measurements. It runs 400 warm-up iterations and 300 measured samples for the reference scenario. Smaller deterministic variants cover two maps, three maps with concentrated cross-map clusters, one map holding at least 80 percent of projected nodes, and one- and two-pinned-map cases. Normal CI uses exact structural invariants plus generous tripwires; strict mode enables the production ceilings.

## Task 38 calibration

The strict baseline targets are force p95 <= 50 ms, full-worker p95 <= 100 ms, accepted-batch-first-frame p95 <= 150 ms and p99 <= 300 ms, and EDT swap p95 <= 2 ms. Calibration preserves O(N+E) rebuild behavior unless the Task 37 ledger proves it is insufficient. It preserves fixed SpringBox quality, the aggregate cross-map displacement cap, rigid pinned-map correction, deterministic seeds, immutable publication, and the existing GraphStream boundary.

Only measured bottlenecks may justify changes in `LayoutCalibration`, `PerceptualIdlePolicy`, `GraphUpdateCoordinator`, `GraphStreamLayoutEngine`, `GraphGeometryEngine`, or `GraphPainter`. Calibration values and observed environment, sample counts, percentiles, and residual hardware risk are recorded in `docs/superpowers/specs/2026-08-10-graph-workspace-performance-report.md`.

## Error handling and determinism

Malformed generated documents, non-finite measurements, missing stage samples, failed worker frames, stale generations, and failed EDT publication are diagnostic failures rather than silently omitted samples. Fixture generation and scenario ordering are deterministic for the fixed seed. The diagnostic closes workers, schedulers, and temporary resources after every scenario.

## Verification

Task 37 uses the prescribed red task invocation, focused performance tests, the diagnostic task, exact fixture-name checks, structural invariants, and the full graph-plugin test suite. Task 38 runs the strict baseline before edits, reruns focused tests and strict diagnostics after calibration, validates the report against the ledger, and verifies the exact seven-file Task 38 allowlist before commit. The two task commits use the required `2026-08-10-graph-workspace:` prefix.
