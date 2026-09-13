package org.freeplane.plugin.graph.performance;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.freeplane.plugin.graph.geometry.AwtGeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.GeometryTextMetrics;
import org.freeplane.plugin.graph.layout.BoundarySeparationCorrection;
import org.freeplane.plugin.graph.layout.BoundarySeparationResult;
import org.freeplane.plugin.graph.layout.LayoutCalibration;
import org.freeplane.plugin.graph.layout.LayoutEngine;
import org.freeplane.plugin.graph.layout.LayoutFrame;
import org.freeplane.plugin.graph.layout.LayoutRequest;
import org.freeplane.plugin.graph.layout.LayoutWorker;
import org.freeplane.plugin.graph.layout.graphstream.GraphStreamLayoutFactory;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectionDiff;
import org.freeplane.plugin.graph.projection.ProjectionEngine;
import org.freeplane.plugin.graph.projection.input.ProjectionInput;

/**
 * Focused, deterministic profile of the boundary-separation correction cost per published frame.
 * Writes a per-sample CSV and prints per-stage p50/p95/max; the Gradle task also records a JFR
 * profile so hot methods can be attributed with {@code jfr view hot-methods}.
 */
public final class BoundaryCorrectionProfile {
    private static final int WARMUP_SAMPLES = 40;
    private static final int MEASURED_SAMPLES = 120;
    private static final List<String> STAGES = Collections.unmodifiableList(Arrays.asList(
        "workerTotal", "directTotal", "separation", "hull", "plan", "apply"));

    private BoundaryCorrectionProfile() {
    }

    public static void main(final String[] args) throws Exception {
        final String scenario = args.length > 0 ? args[0] : "reference-2000-5000";
        final Path output = args.length > 1 ? Paths.get(args[1])
            : Paths.get("build", "boundary-correction-profile");
        Files.createDirectories(output);
        final GeneratedWorkspace generated = GeneratedWorkspace.forScenario(scenario);
        final ProjectionEngine projectionEngine = new ProjectionEngine();
        final GeometryTextMetrics metrics = new AwtGeometryTextMetrics(new Font("Dialog", Font.PLAIN, 12),
            new FontRenderContext(null, true, true));
        final BoundarySeparationCorrection corrector = new BoundarySeparationCorrection();
        final LayoutWorker worker = new LayoutWorker(LayoutCalibration.spikeDefaults());
        final List<String> csv = new ArrayList<String>();
        csv.add("sample,stage,totalNanos,separationNanos,hullNanos,planNanos,applyNanos,rounds,detected,residual,conflicts");
        final Map<String, List<Long>> samples = new LinkedHashMap<String, List<Long>>();
        for (final String stage : STAGES) {
            samples.put(stage, new ArrayList<Long>());
        }
        try {
            GraphProjection previous = projectionEngine.project(ProjectionInput.of(0L, generated.document(),
                generated.snapshots(), generated.availability()));
            final int total = WARMUP_SAMPLES + MEASURED_SAMPLES;
            for (int index = 0; index < total; index++) {
                final long generation = index + 1L;
                final GraphProjection current = projectionEngine.project(ProjectionInput.of(generation,
                    generated.document(), generated.snapshots(), generated.availability()));
                final ProjectionDiff diff = ProjectionDiff.between(previous, current);
                final LayoutRequest request = LayoutRequest.of(generated.document().id(), current, diff,
                    current.pins());

                final long workerStart = System.nanoTime();
                final LayoutFrame frame = worker.submit(request).toCompletableFuture().get();
                final long workerTotal = System.nanoTime() - workerStart;

                final LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults());
                try {
                    final LayoutFrame applied = engine.apply(request);
                    final long directStart = System.nanoTime();
                    final BoundarySeparationResult result = corrector.apply(current, applied.positions(),
                        metrics, request.pins());
                    final long directTotal = System.nanoTime() - directStart;
                    if (index >= WARMUP_SAMPLES) {
                        record(samples, "workerTotal", workerTotal);
                        record(samples, "directTotal", directTotal);
                        record(samples, "separation", result.timings().separationNanos());
                        record(samples, "hull", result.timings().hullNanos());
                        record(samples, "plan", result.timings().planNanos());
                        record(samples, "apply", result.timings().applyNanos());
                        csv.add(String.join(",", String.valueOf(index), "direct",
                            String.valueOf(directTotal),
                            String.valueOf(result.timings().separationNanos()),
                            String.valueOf(result.timings().hullNanos()),
                            String.valueOf(result.timings().planNanos()),
                            String.valueOf(result.timings().applyNanos()),
                            String.valueOf(result.diagnostics().rounds()),
                            String.valueOf(result.diagnostics().hullViolationsDetected()),
                            String.valueOf(result.diagnostics().hullResidualViolations()),
                            String.valueOf(result.diagnostics().conflicts().size())));
                        csv.add(String.join(",", String.valueOf(index), "worker",
                            String.valueOf(workerTotal), "-1", "-1", "-1", "-1",
                            String.valueOf(frame.boundaryDiagnostics().rounds()),
                            String.valueOf(frame.boundaryDiagnostics().hullViolationsDetected()),
                            String.valueOf(frame.boundaryDiagnostics().hullResidualViolations()),
                            String.valueOf(frame.boundaryDiagnostics().conflicts().size())));
                    }
                }
                finally {
                    engine.close();
                }
                previous = current;
            }
        }
        finally {
            worker.close();
        }
        Files.write(output.resolve("correction-profile.csv"),
            String.join("\n", csv).concat("\n").getBytes(StandardCharsets.UTF_8));
        printSummary(samples);
    }

    private static void record(final Map<String, List<Long>> samples, final String stage, final long nanos) {
        samples.get(stage).add(Long.valueOf(nanos));
    }

    private static void printSummary(final Map<String, List<Long>> samples) {
        for (final Map.Entry<String, List<Long>> entry : samples.entrySet()) {
            final List<Long> values = new ArrayList<Long>(entry.getValue());
            if (values.isEmpty()) {
                continue;
            }
            Collections.sort(values);
            System.out.println(String.format("stage=%-12s p50=%10d ns p95=%10d ns max=%10d ns samples=%d",
                entry.getKey(), NearestRankPercentile.of(values, 0.50),
                NearestRankPercentile.of(values, 0.95), values.get(values.size() - 1).longValue(),
                values.size()));
        }
    }
}
