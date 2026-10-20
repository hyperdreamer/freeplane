package org.freeplane.plugin.graph.layout.graphstream;

import static org.assertj.core.api.Assertions.*;
import static org.freeplane.plugin.graph.layout.graphstream.InitialSpacingMetrics.*;
import java.awt.geom.Dimension2D;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.freeplane.plugin.graph.geometry.*;
import org.freeplane.plugin.graph.layout.*;
import org.freeplane.plugin.graph.projection.*;

public final class InitialSpacingProbe {
    private static final String[] CONTROL_HEADER = {"fixture", "stage", "step_index", "exhausted_control",
        "index_budget_exhausted", "verification_budget_exhausted", "detection_truncated", "verifications",
        "verification_cap", "edge_rounds", "round_cap", "index_inserted_items", "index_items_cap", "index_pair_checks",
        "pair_checks_cap", "verification_work", "work_cap", "conflicts_detected", "conflicts_cap",
        "residual_conflict_keys", "repairs_applied", "boundary_rounds", "raw_node_residual_pairs",
        "frame_node_residual", "node_residual_diagnostic_matches", "gated_frames",
        "authorised_verification_budget_exhausted_frames", "forbidden_index_budget_exhausted_frames",
        "forbidden_detection_truncated_frames"};
    private static String fixture;
    private static String configuration;
    private static double gap;
    private static int gatedFrames;
    private static int authorisedVerificationBudgetExhaustedFrames;
    private static int forbiddenIndexBudgetExhaustedFrames;
    private static int forbiddenDetectionTruncatedFrames;
    private static int maxVerifications;
    private static int maxResidualConflictKeys;
    private static int maxRepairs;
    private static int coveredFrames;
    private static int hullInvariantFrames;
    private static int residualMismatchFrames;
    private static boolean controlHeaderWritten;
    private static long firstIdleStep = -1L;

    public static void main(String[] args) throws Exception {
        assertThat(System.getProperty("java.awt.headless")).isEqualTo("true");
        configuration = args[0]; fixture = args[1];
        assertThat(configuration).isIn("C0", "candidate");
        gap = configuration.equals("C0") ? 6.0 : 24.0;
        GraphProjection p = InitialSpacingFixtures.named(fixture);
        try {
            java.lang.reflect.Field field = GraphStreamLayoutEngine.class.getDeclaredField("PREFERRED_NODE_GAP");
            field.setAccessible(true);
            assertThat(configuration).isEqualTo("candidate"); close(field.getDouble(null), 24.0);
        }
        catch (NoSuchFieldException absent) { assertThat(configuration).isEqualTo("C0"); }
        inputs(p);
        assertThat(p.generation()).isEqualTo(1L); assertThat(p.pins()).isEmpty();
        if (!fixture.equals("mixed")) for (ProjectedNode node : p.nodes()) close(radius(p, node.key()), 8.0);
        if (fixture.equals("mixed")) {
            double[] scales = {1.0, 1.0, 1.2, 1.0};
            for (int i = 0; i < 4; i++) {
                close(radius(p, p.nodes().get(i).key()), 8.0 * scales[i]);
                assertThat(p.prominence().get(p.nodes().get(i).key()).visibleOutgoingTargets()).isEqualTo(i == 2 ? 2 : 0);
            }
        }
        row("CONFIG", configuration, fixture, gap, NodeSeparationProjection.MIN_GAP,
            GraphStreamLayoutEngine.BoundarySizes.SIBLING_GAP, InitialSpacingFixtures.WORKSPACE,
            System.getProperty("java.version"), System.getProperty("java.home"), "Dialog/PLAIN/12;FRC(null,true,true)");
        for (Class<?> type : Arrays.asList(GraphStreamLayoutEngine.class, ClusterPacking.class,
                LayoutWorker.class, InitialSpacingFixtures.class)) row("CODE_SOURCE", type.getName(),
                    type.getProtectionDomain().getCodeSource().getLocation());
        LayoutFrame seed, raw, first = null, post = null;
        int firstStep = -1, stillIdle = 0;
        double maxRms = 0.0, maxDisplacement = 0.0;
        long start = System.nanoTime();
        long deadline = start + TimeUnit.SECONDS.toNanos(600);
        long firstReceived = -1L, followStart;
        try (LayoutWorker worker = new LayoutWorker(LayoutCalibration.spikeDefaults())) {
            seed = InitialSpacingFrameAssertions.awaitWithinDeadline(worker.submit(InitialSpacingFixtures.request(p, p)), deadline);
            gate(p, seed, p.pins(), true, "seed");
            try (GraphStreamLayoutEngine engine = new GraphStreamLayoutEngine(LayoutCalibration.spikeDefaults())) {
                raw = engine.apply(InitialSpacingFixtures.request(p, p));
                InitialSpacingConsumers.raw(p, raw, gap);
                InitialSpacingConsumers.topology(p, engine, gap);
            }
            Set<EnclosureHullKey> eligible = classify(p, raw, seed);
            print(p, "raw", raw); print(p, "seed", seed);
            seedGate(p, seed, args[2], eligible);
            if (fixture.startsWith("matrix-")) {
                labels(p);
                row("CONSUMER_ONLY", fixture, "Raw/seed topology matrix; no settling claim");
                summary(); note();
                return;
            }
            // submit is sample 0; the first raw step can also have frame.stepIndex()==0.
            for (int step = 1; step <= 6000; step++) {
                LayoutFrame frame = InitialSpacingFrameAssertions.awaitWithinDeadline(worker.step(), deadline);
                long received = System.nanoTime();
                gate(p, frame, p.pins(), false, "sample");
                row("SAMPLE", fixture, step, frame.stepIndex(), frame.idle().idle(), frame.idle().rms(), frame.idle().max());
                if (frame.idle().idle()) {
                    first = frame; firstStep = step; firstReceived = received; firstIdleStep = step; break;
                }
            }
            assertThat(first).as("first idle within 6000 calls and 600 seconds").isNotNull();
            assertThat(firstReceived - start).isBetween(0L, TimeUnit.SECONDS.toNanos(600));
            row("FIRST_IDLE", fixture, firstStep, firstReceived - start);
            followStart = System.nanoTime();
            long followDeadline = followStart + TimeUnit.SECONDS.toNanos(600);
            for (int i = 1; i <= 100; i++) {
                post = InitialSpacingFrameAssertions.awaitWithinDeadline(worker.step(), followDeadline);
                gate(p, post, p.pins(), false, "follow");
                maxRms = Math.max(maxRms, post.idle().rms()); maxDisplacement = Math.max(maxDisplacement, post.idle().max());
                if (post.idle().idle()) stillIdle++;
                row("FOLLOW", fixture, i, firstStep + i, post.idle().idle(), post.idle().rms(), post.idle().max());
            }
            long followElapsed = System.nanoTime() - followStart;
            assertThat(followElapsed).isBetween(0L, TimeUnit.SECONDS.toNanos(600));
            row("POST", fixture, firstStep + 100, stillIdle, maxRms, maxDisplacement, followElapsed);
        }
        labels(p);
        LayoutFrame repeatSeed;
        try (LayoutWorker worker = new LayoutWorker(LayoutCalibration.spikeDefaults())) {
            repeatSeed = InitialSpacingFrameAssertions.awaitWithinDeadline(worker.submit(InitialSpacingFixtures.request(p, p)),
                System.nanoTime() + TimeUnit.SECONDS.toNanos(600));
            gate(p, repeatSeed, p.pins(), true, "repeat-seed");
        }
        assertThat(repeatSeed.positions()).isEqualTo(seed.positions());
        assertThat(repeatSeed.boundaryDiagnostics()).usingRecursiveComparison().isEqualTo(seed.boundaryDiagnostics());
        assertThat(repeatSeed.edgeConflictDiagnostics()).isEqualTo(seed.edgeConflictDiagnostics());
        try (GraphStreamLayoutEngine engine = new GraphStreamLayoutEngine(LayoutCalibration.spikeDefaults())) {
            assertThat(engine.apply(InitialSpacingFixtures.request(p, p)).positions()).isEqualTo(raw.positions());
        }
        print(p, "idle", first); print(p, "post100", post);
        endpoints(p, "idle", first); endpoints(p, "post100", post);
        row("NOTE", "Descriptive endpoint gaps; additive MIN_GAP remains 6.0; total rests with enclosure endpoints change.");
        summary(); note();
    }

    /**
     * Candidate frames keep the strict shared gate unchanged. C0 is a baseline observation of the
     * unmodified pre-change engine, so four pre-existing conditions are recorded in the receipt
     * instead of being rejected: verification-budget exhaustion, residual edge-conflict keys, repair
     * counts, and the frame node-residual diagnostic disagreeing with the raw geometric scan. Index-budget
     * exhaustion and truncated detection remain forbidden and fail the C0 run. The fourth recorded
     * condition is authorised by {@code ruling-A-amendment-1.md} condition 4; it is not
     * inferred from the ruling's principle. Every other property the shared gate asserts is
     * re-asserted for C0, including {@code BoundaryInvariantAssertions.assertVerifiedFrame}
     * (sibling-hull non-crossing and child-vertex containment), which is invoked reflectively because
     * the helper is package-private in the parent package.
     */
    private static void gate(GraphProjection p, LayoutFrame frame, List<PinProjection> pins,
            boolean requireZero, String stage) {
        gatedFrames++;
        if (!configuration.equals("C0")) {
            InitialSpacingFrameAssertions.check(p, frame, pins, requireZero);
            if (pins.isEmpty() || frame.boundaryDiagnostics().boundaryVerified()) hullInvariantFrames++;
            record(frame, frame.edgeConflictDiagnostics());
            return;
        }
        control(p, frame, pins, requireZero, stage);
    }

    private static void control(GraphProjection p, LayoutFrame frame, List<PinProjection> pins,
            boolean requireZero, String stage) {
        assertThat(frame.failed()).as("C0 control frame must not be failed").isFalse();
        assertThat(frame.verified()).as("C0 control frame must be verified").isTrue();
        Set<ProjectedNodeKey> nodes = new LinkedHashSet<ProjectedNodeKey>();
        for (ProjectedNode node : p.nodes()) assertThat(nodes.add(node.key())).isTrue();
        Set<EnclosureHullKey> anchors = new LinkedHashSet<EnclosureHullKey>();
        for (ProjectedEnclosure e : p.enclosures()) assertThat(anchors.add(e.hullKey())).isTrue();
        assertThat(frame.positions().nodes().keySet()).isEqualTo(nodes);
        assertThat(frame.positions().anchors().keySet()).isEqualTo(anchors);
        for (LayoutPoint point : frame.positions().nodes().values()) finitePoint(point);
        for (LayoutPoint point : frame.positions().anchors().values()) finitePoint(point);
        List<String> residuals = InitialSpacingFrameAssertions.residualPairs(p, frame.positions());
        assertThat(residuals).doesNotHaveDuplicates();
        if (requireZero) assertThat(residuals).isEmpty();
        // The frame's node-residual field is a diagnostic of the unmodified engine's relaxing
        // projection; the raw geometric scan is the geometry truth. C0 records their disagreement
        // under ruling-A-amendment-1.md condition 4 instead of rejecting the baseline.
        boolean residualDiagnosticMatches = frame.residualViolations() == residuals.size();
        if (!residualDiagnosticMatches) residualMismatchFrames++;
        Set<ProjectedNodeKey> pinned = new LinkedHashSet<ProjectedNodeKey>();
        for (PinProjection pin : pins) if (pin.active()) pinned.add(pin.projectedNode().get());
        assertThat(frame.residualViolations()).isEqualTo(
            new NodeSeparationProjection().project(p, frame.positions(), pinned).residualViolations());
        BoundarySeparationDiagnostics boundary = frame.boundaryDiagnostics();
        List<String> conflictPairs = new ArrayList<String>();
        for (BoundaryConflict conflict : boundary.conflicts()) conflictPairs.add(conflict.pairKey());
        assertThat(conflictPairs).doesNotHaveDuplicates();
        assertThat(boundary.residualHullPairs()).doesNotHaveDuplicates();
        assertThat(new LinkedHashSet<String>(conflictPairs))
            .isEqualTo(new LinkedHashSet<String>(boundary.residualHullPairs()));
        assertThat(boundary.boundaryCovered()).isTrue();
        if (pins.isEmpty() || boundary.boundaryVerified()) {
            assertThat(boundary.boundaryVerified()).isTrue();
            assertThat(boundary.hullResidualViolations()).isZero();
            verifiedHullInvariants(p, frame, boundary, pins);
        }
        for (PinProjection pin : pins) if (pin.active())
            assertThat(frame.positions().nodes().get(pin.projectedNode().get()))
                .isEqualTo(LayoutPoint.of(pin.x(), pin.y()));
        assertThat(boundary.rounds()).isBetween(0, 4);
        nonnegative(boundary.displacementRms());
        nonnegative(boundary.displacementMax());
        nonnegative(boundary.deltaRms());
        nonnegative(boundary.deltaMax());
        for (LayoutPoint point : boundary.appliedDisplacements().values()) finitePoint(point);
        EdgeConflictDiagnostics edge = frame.edgeConflictDiagnostics();
        if (edge.indexBudgetExhausted()) forbiddenIndexBudgetExhaustedFrames++;
        assertThat(edge.indexBudgetExhausted()).as("C0 index-budget exhaustion is forbidden").isFalse();
        if (edge.detectionTruncated()) forbiddenDetectionTruncatedFrames++;
        assertThat(edge.detectionTruncated()).as("C0 truncated edge detection is forbidden").isFalse();
        assertThat(edge.rounds()).isBetween(0, 4);
        assertThat(edge.verifications()).isBetween(0, 64);
        assertThat(edge.indexInsertedItems()).isBetween(0L, 262144L);
        assertThat(edge.indexPairChecks()).isBetween(0L, 131072L);
        assertThat(edge.verificationWork()).isBetween(0L, 65536L);
        assertThat(edge.conflictsDetected()).isBetween(0, 4096);
        assertThat(edge.conflictsResidual()).doesNotHaveDuplicates();
        nonnegative(frame.idle().rms());
        nonnegative(frame.idle().max());
        record(frame, edge);
        if (!controlHeaderWritten) {
            controlHeaderWritten = true;
            Object[] header = new Object[CONTROL_HEADER.length];
            System.arraycopy(CONTROL_HEADER, 0, header, 0, CONTROL_HEADER.length);
            row("CONTROL_HEADER", header);
        }
        row("CONTROL", fixture, stage, frame.stepIndex(), true, edge.indexBudgetExhausted(),
            edge.verificationBudgetExhausted(), edge.detectionTruncated(), edge.verifications(), 64, edge.rounds(), 4,
            edge.indexInsertedItems(), 262144L, edge.indexPairChecks(), 131072L, edge.verificationWork(), 65536L,
            edge.conflictsDetected(), 4096, edge.conflictsResidual().size(), edge.repairsApplied().size(),
            boundary.rounds(), residuals.size(), frame.residualViolations(), residualDiagnosticMatches,
            gatedFrames, authorisedVerificationBudgetExhaustedFrames, forbiddenIndexBudgetExhaustedFrames,
            forbiddenDetectionTruncatedFrames);
    }

    /**
     * Re-asserts the shared gate's hull invariants for a C0 frame: non-crossing sibling hulls and
     * child-vertex containment inside the parent hull. The helper is package-private in
     * {@code org.freeplane.plugin.graph.layout}, hence reflection; a missing or inaccessible helper
     * fails the run instead of silently narrowing the C0 gate.
     */
    private static void verifiedHullInvariants(GraphProjection p, LayoutFrame frame,
            BoundarySeparationDiagnostics boundary, List<PinProjection> pins) {
        java.lang.reflect.Method method;
        try {
            Class<?> type = Class.forName("org.freeplane.plugin.graph.layout.BoundaryInvariantAssertions");
            method = type.getDeclaredMethod("assertVerifiedFrame", GraphProjection.class, LayoutFrame.class,
                BoundarySeparationDiagnostics.class, GeometryTextMetrics.class, List.class);
            method.setAccessible(true);
        }
        catch (ClassNotFoundException | NoSuchMethodException | RuntimeException absent) {
            throw new AssertionError("shared hull invariants are unavailable to the C0 control gate", absent);
        }
        try {
            method.invoke(null, p, frame, boundary, InitialSpacingFrameAssertions.metrics(), pins);
        }
        catch (java.lang.reflect.InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Error) throw (Error) cause;
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new AssertionError("shared hull invariants failed for a C0 frame", cause);
        }
        catch (IllegalAccessException denied) {
            throw new AssertionError("shared hull invariants are inaccessible to the C0 control gate", denied);
        }
        hullInvariantFrames++;
    }

    private static void record(LayoutFrame frame, EdgeConflictDiagnostics edge) {
        if (edge.verificationBudgetExhausted()) authorisedVerificationBudgetExhaustedFrames++;
        maxVerifications = Math.max(maxVerifications, edge.verifications());
        maxResidualConflictKeys = Math.max(maxResidualConflictKeys, edge.conflictsResidual().size());
        maxRepairs = Math.max(maxRepairs, edge.repairsApplied().size());
        if (frame.boundaryDiagnostics().boundaryCovered()) coveredFrames++;
    }

    private static void summary() {
        row("CONTROL_SUMMARY", fixture, configuration, "exhausted_control", configuration.equals("C0"),
            "gated_frames", gatedFrames,
            "authorised_verification_budget_exhausted_frames", authorisedVerificationBudgetExhaustedFrames,
            "forbidden_index_budget_exhausted_frames", forbiddenIndexBudgetExhaustedFrames,
            "forbidden_detection_truncated_frames", forbiddenDetectionTruncatedFrames,
            "verification_cap", 64, "max_verifications_observed", maxVerifications, "round_cap", 4,
            "max_residual_conflict_keys", maxResidualConflictKeys, "max_repairs_observed", maxRepairs,
            "boundary_covered_frames", coveredFrames, "hull_invariant_frames", hullInvariantFrames,
            "node_residual_mismatch_frames", residualMismatchFrames);
    }

    private static void note() {
        if (configuration.equals("C0")) {
            Object observed = firstIdleStep < 0 ? "not-applicable" : Long.valueOf(firstIdleStep);
            Object baseline = fixture.equals("cross") ? Integer.valueOf(756) : "not-applicable";
            Object match = fixture.equals("cross") ? Boolean.valueOf(firstIdleStep == 756L) : "not-applicable";
            row("CONTROL_NOTE", fixture, configuration, "exhausted_control", true,
                "disposition", "recorded_not_rejected",
                "cause", "pre-existing behaviour of the unmodified pre-change engine",
                "observed_first_idle_step", observed, "shipped_pre_change_baseline", baseline, "match", match,
                "node_residual_mismatch_frames", residualMismatchFrames,
                "recorded_not_rejected", "verification-budget exhaustion; residual edge-conflict keys; repair counts; frame node-residual diagnostic versus the raw geometric scan",
                "forbidden_not_recorded", "index-budget exhaustion and truncated detection fail the C0 run",
                "forbidden_index_budget_exhausted_frames", forbiddenIndexBudgetExhaustedFrames,
                "forbidden_detection_truncated_frames", forbiddenDetectionTruncatedFrames,
                "authorisation", "ruling-A-amendment-1.md condition 4 covers the frame node-residual diagnostic disagreement: C0 records it, never rejects it",
                "scope", "baseline observation, not a candidate defect, regression or waived gate");
        }
        else row("CONTROL_NOTE", fixture, configuration, "exhausted_control", false,
            "candidate_authorised_verification_budget_exhausted_frames", authorisedVerificationBudgetExhaustedFrames,
            "forbidden_index_budget_exhausted_frames", forbiddenIndexBudgetExhaustedFrames,
            "forbidden_detection_truncated_frames", forbiddenDetectionTruncatedFrames,
            "node_residual_mismatch_frames", residualMismatchFrames,
            "gate", "shared InitialSpacingFrameAssertions.check on every frame",
            "pre_existing_condition", "removed by the candidate rather than introduced by it");
    }

    private static void finitePoint(LayoutPoint point) {
        assertThat(point).isNotNull();
        assertThat(Double.isFinite(point.x())).isTrue();
        assertThat(Double.isFinite(point.y())).isTrue();
    }

    private static void nonnegative(double value) {
        assertThat(Double.isFinite(value)).isTrue();
        assertThat(value).isGreaterThanOrEqualTo(0.0);
    }

    private static void seedGate(GraphProjection p, LayoutFrame seed, String baselineLog, Set<EnclosureHullKey> eligible) throws Exception {
        Map<String, double[]> expected = new LinkedHashMap<String, double[]>();
        if (fixture.equals("reference")) expected.put("Axioms", new double[] {6.0, 24.0, 24.0});
        if (fixture.equals("S1")) expected.put("Sub", new double[] {6.0, 24.0, 24.0});
        if (fixture.equals("two")) expected.put("Root", new double[] {6.0, 24.0, 24.0});
        if (fixture.equals("mixed")) expected.put("Sub", new double[] {7.6, 25.6, 25.6});
        if (fixture.equals("cross")) {
            expected.put("A-sub 0", new double[] {6.0, 17.14733824239645, 20.573669121198225});
            expected.put("A-sub 1", new double[] {6.0, 17.14733824239645, 17.14733824239645});
            expected.put("A-sub 2", new double[] {6.0, 24.0, 24.0});
            expected.put("B-sub 0", new double[] {6.000000000000009, 24.0, 24.0});
            expected.put("B-sub 1", new double[] {6.000000000000009, 24.0, 24.0});
            expected.put("B-sub 2", new double[] {6.000000000000011, 17.14733824239645, 20.573669121198225});
        }
        if (expected.isEmpty()) return;
        Map<EnclosureHullKey, Metric> actual = measure(p, seed.positions());
        assertThat(actual).hasSize(expected.size());
        Map<String, String[]> control = new LinkedHashMap<String, String[]>();
        if (configuration.equals("candidate")) {
            for (String line : java.nio.file.Files.readAllLines(java.nio.file.Paths.get(baselineLog), StandardCharsets.UTF_8)) {
                String[] row = line.split("\t");
                if (row.length > 6 && row[0].equals("METRIC") && row[2].equals("seed")) {
                    assertThat(control.put(row[3], row)).isNull();
                    close(Double.parseDouble(row[6]), expected.get(row[4])[0]);
                }
            }
            assertThat(control).hasSize(expected.size());
        }
        for (ProjectedEnclosure e : p.enclosures()) if (actual.containsKey(e.hullKey())) {
            Metric m = actual.get(e.hullKey()); double[] target = expected.get(e.labels().get(0).displayText());
            assertThat(target).isNotNull();
            if (configuration.equals("C0")) close(m.median, target[0]);
            else {
                String[] baseline = control.get(CanonicalKeys.hull(e.hullKey())); assertThat(baseline).isNotNull();
                assertThat(m.median - Double.parseDouble(baseline[6])).isGreaterThanOrEqualTo(6.0 - 1e-6);
                close(m.minimum, target[1]); close(m.median, target[2]);
                if (eligible.contains(e.hullKey())) {
                    for (int i = 0; i < e.directNodes().size(); i++) {
                        ProjectedNodeKey a = e.directNodes().get(i), b = e.directNodes().get((i + 1) % e.directNodes().size());
                        close(distance(seed.positions().nodes().get(a), seed.positions().nodes().get(b)) - radius(p, a) - radius(p, b), 24.0);
                        if (e.directNodes().size() == 2) break;
                    }
                }
            }
        }
    }

    private static void inputs(GraphProjection p) {
        for (ProjectedNode node : p.nodes()) row("INPUT_NODE", CanonicalKeys.node(node.key()), node.label().displayText(),
            node.mapName(), node.graphGroup(), radius(p, node.key()));
        for (ProjectedEnclosure e : p.enclosures()) {
            List<String> direct = new ArrayList<String>(), children = new ArrayList<String>(), labels = new ArrayList<String>();
            for (ProjectedNodeKey key : e.directNodes()) direct.add(CanonicalKeys.node(key));
            for (EnclosureHullKey key : e.directEnclosures()) children.add(CanonicalKeys.hull(key));
            for (org.freeplane.plugin.graph.projection.input.SafeNodeLabel label : e.labels()) labels.add(label.displayText());
            row("INPUT_HULL", CanonicalKeys.hull(e.hullKey()), labels, e.mapName(), e.mapRoot(), e.boundaryTier(),
                e.parentHull().isPresent() ? CanonicalKeys.hull(e.parentHull().get()) : "absent", direct, children);
        }
        for (ProjectedEdge edge : p.edges()) for (EdgeContributor c : edge.contributors()) row("INPUT_EDGE",
            CanonicalKeys.endpoint(edge.first()), CanonicalKeys.endpoint(edge.second()), c.connectorDescriptor().isPresent(),
            CanonicalKeys.endpoint(c.projectedSource()), CanonicalKeys.endpoint(c.projectedTarget()), c.arrowAtSource(), c.arrowAtTarget(),
            c.sourceLabel(), c.middleLabel(), c.targetLabel());
    }

    private static void labels(GraphProjection p) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        for (ProjectedEnclosure e : p.enclosures()) if (e.boundaryTier() != BoundaryTier.SUPPRESSED) {
            for (org.freeplane.plugin.graph.projection.input.SafeNodeLabel label : e.labels()) {
                Dimension2D dimension = InitialSpacingFrameAssertions.metrics().measure(label.displayText(), e.boundaryTier());
                finite(dimension.getWidth()); finite(dimension.getHeight());
                assertThat(dimension.getWidth()).isPositive(); assertThat(dimension.getHeight()).isPositive();
                String value = CanonicalKeys.hull(e.hullKey()) + "\t" + label.displayText() + "\t"
                    + e.boundaryTier() + "\t" + dimension.getWidth() + "\t" + dimension.getHeight();
                sha.update((value + "\n").getBytes(StandardCharsets.UTF_8)); row("LABEL", value);
            }
        }
        StringBuilder hex = new StringBuilder(); for (byte b : sha.digest()) hex.append(String.format("%02x", b & 255));
        row("LABEL_SHA256", hex);
    }

    private static Set<EnclosureHullKey> classify(GraphProjection p, LayoutFrame raw, LayoutFrame seed) {
        Set<EnclosureHullKey> eligibleHulls = new LinkedHashSet<EnclosureHullKey>();
        GeometryTextMetrics metrics = InitialSpacingFrameAssertions.metrics();
        BoundarySeparationResult boundary = new BoundarySeparationCorrection().apply(p, raw.positions(), metrics, p.pins());
        EdgeConflictResult edge = new EdgeConflictCorrection().apply(p, boundary.positions(), metrics, p.pins(),
            ProjectedEndpointVisibility.visibleEndpoints(p.nodes(), p.enclosures()), boundary);
        assertThat(edge.positions()).isEqualTo(seed.positions());
        GraphStreamLayoutEngine.BoundarySizes sizes = new GraphStreamLayoutEngine.BoundarySizes(p);
        for (ProjectedEnclosure e : p.enclosures()) if (e.directNodes().size() >= 2) {
            double firstRadius = radius(p, e.directNodes().get(0)), maximumShift = 0.0;
            boolean mixed = false, repair = false;
            ClusterPacking.ClusterPlacement content = sizes.placements(e.hullKey()).get(0);
            assertThat(content.item.anchorKey).isNull();
            LayoutPoint composed = InitialSpacingConsumers.composed(sizes, e.hullKey());
            boolean translated = content.x != 0.0 || content.y != 0.0 || composed.x() != 0.0 || composed.y() != 0.0;
            for (int i = 0; i < e.directNodes().size(); i++) {
                ProjectedNodeKey key = e.directNodes().get(i);
                mixed |= radius(p, key) != firstRadius;
                LayoutPoint a = raw.positions().nodes().get(key), b = boundary.positions().nodes().get(key), c = seed.positions().nodes().get(key);
                double shift = distance(a, c); maximumShift = Math.max(maximumShift, shift);
                boolean record = boundary.appliedDisplacements().containsKey(CanonicalKeys.node(key));
                for (EdgeConflictDiagnostics.Repair r : edge.diagnostics().repairsApplied()) record |= r.nodeKeys().contains(key);
                boolean stageMoved = distance(a, b) > 1e-9 || distance(b, c) > 1e-9;
                repair |= record || stageMoved || shift > 1e-9;
                row("ELIGIBILITY_NODE", fixture, CanonicalKeys.hull(e.hullKey()), CanonicalKeys.node(key), radius(p, key),
                    analyticRadius(p, e, gap), 2.0 * Math.PI * i / e.directNodes().size(), a.x(), a.y(), b.x(), b.y(), c.x(), c.y(), record);
            }
            boolean eligible = !mixed && !translated && !repair;
            row("ELIGIBILITY", fixture, CanonicalKeys.hull(e.hullKey()), e.labels().get(0).displayText(), content.x, content.y,
                composed.x(), composed.y(), maximumShift, mixed, translated, repair, eligible);
            if (eligible) eligibleHulls.add(e.hullKey());
        }
        return eligibleHulls;
    }

    private static void print(GraphProjection p, String stage, LayoutFrame frame) {
        for (ProjectedNode node : p.nodes()) {
            LayoutPoint v = frame.positions().nodes().get(node.key());
            row("POINT", fixture, stage, "node", CanonicalKeys.node(node.key()), v.x(), v.y(), radius(p, node.key()));
        }
        for (ProjectedEnclosure e : p.enclosures()) {
            LayoutPoint v = frame.positions().anchors().get(e.hullKey());
            row("POINT", fixture, stage, "enclosure", CanonicalKeys.hull(e.hullKey()), v.x(), v.y(), analyticRadius(p, e, gap));
        }
        for (Map.Entry<EnclosureHullKey, Metric> entry : measure(p, frame.positions()).entrySet()) {
            ProjectedEnclosure enclosure = null;
            for (ProjectedEnclosure e : p.enclosures()) if (e.hullKey().equals(entry.getKey())) enclosure = e;
            int nativeCount = 0, relationshipCount = 0;
            for (ProjectedEdge edge : p.edges()) for (EdgeContributor c : edge.contributors()) {
                for (ProjectedEndpointKey endpoint : Arrays.asList(edge.first(), edge.second())) if (endpoint.isNode()
                        && enclosure.directNodes().contains(endpoint.node().get())) {
                    if (c.connectorDescriptor().isPresent()) nativeCount++; else relationshipCount++;
                }
            }
            Metric m = entry.getValue();
            row("METRIC", fixture, stage, CanonicalKeys.hull(entry.getKey()), enclosure.labels().get(0).displayText(),
                m.minimum, m.median, m.nearest, Math.abs(m.median - 6.0) <= 1e-9, nativeCount, relationshipCount);
        }
        BoundarySeparationDiagnostics b = frame.boundaryDiagnostics(); EdgeConflictDiagnostics e = frame.edgeConflictDiagnostics();
        row("DIAGNOSTICS", fixture, stage, frame.failed(), frame.residualViolations(), b.boundaryVerified(), b.boundaryCovered(),
            b.hullResidualViolations(), b.rounds(), e.repairsApplied().size(), e.rounds(), e.verifications(), e.conflictsResidual(),
            e.indexInsertedItems(), e.indexPairChecks(), e.verificationWork(), e.indexBudgetExhausted(), e.verificationBudgetExhausted(), e.detectionTruncated());
        for (EdgeConflictDiagnostics.Repair r : e.repairsApplied()) row("REPAIR", fixture, stage, r.kind(), r.nodeKeys(), r.fromCentres(), r.toCentres(), r.batchIndex());
    }

    private static void endpoints(GraphProjection p, String stage, LayoutFrame frame) {
        Map<String, List<Double>> byType = new TreeMap<String, List<Double>>();
        for (ProjectedEdge edge : p.edges()) {
            double value = distance(endpointPosition(p, frame, edge.first()), endpointPosition(p, frame, edge.second()))
                - InitialSpacingConsumers.endpointRadius(p, edge.first(), gap) - InitialSpacingConsumers.endpointRadius(p, edge.second(), gap);
            finite(value);
            String first = edge.first().isNode() ? "node" : "enclosure", second = edge.second().isNode() ? "node" : "enclosure";
            String pair = first.equals(second) ? first + "-" + second : "node-enclosure";
            if (!byType.containsKey(pair)) byType.put(pair, new ArrayList<Double>());
            byType.get(pair).add(value);
            for (EdgeContributor contributor : edge.contributors()) row("ENDPOINT", fixture, stage, CanonicalKeys.endpoint(edge.first()), CanonicalKeys.endpoint(edge.second()),
                contributor.connectorDescriptor().isPresent() ? "nativeConnector" : "graphRelationship",
                !edge.first().mapReferenceId().equals(edge.second().mapReferenceId()), first, second, value, pair);
        }
        for (String pair : Arrays.asList("node-node", "node-enclosure", "enclosure-enclosure")) {
            List<Double> values = byType.get(pair);
            if (values == null) { row("ENDPOINT_AGGREGATE", fixture, stage, pair, "NOT_APPLICABLE", "NOT_APPLICABLE"); continue; }
            Collections.sort(values); int n = values.size();
            double median = n % 2 == 1 ? values.get(n / 2) : (values.get(n / 2 - 1) + values.get(n / 2)) / 2.0;
            row("ENDPOINT_AGGREGATE", fixture, stage, pair, values.get(0), median);
        }
    }

    private static LayoutPoint endpointPosition(GraphProjection p, LayoutFrame frame, ProjectedEndpointKey endpoint) {
        return endpoint.isNode() ? frame.positions().nodes().get(endpoint.node().get())
            : frame.positions().anchors().get(InitialSpacingConsumers.endpointHull(p, endpoint));
    }

    private static void row(String tag, Object... values) {
        StringBuilder line = new StringBuilder(tag);
        for (Object value : values) line.append('\t').append(value);
        System.out.println(line);
    }
}
