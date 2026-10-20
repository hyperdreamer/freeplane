package org.freeplane.plugin.graph.layout.graphstream;

import static org.assertj.core.api.Assertions.*;
import static org.freeplane.plugin.graph.layout.graphstream.InitialSpacingMetrics.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.freeplane.plugin.graph.geometry.*;
import org.freeplane.plugin.graph.layout.*;
import org.freeplane.plugin.graph.projection.*;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;

public class InitialSpacingLifecycleShould {
    @Test public void retainExistingParticlesOnReparentingAndSeedOnlyAnAddedParticle() throws Exception {
        GraphProjection before = InitialSpacingFixtures.reference(1L), after = InitialSpacingFixtures.reparented(2L);
        retained(before, after, null);
        before = InitialSpacingFixtures.single(4, 1L, false, Collections.<PinProjection>emptyList());
        after = InitialSpacingFixtures.single(5, 2L, false, Collections.<PinProjection>emptyList());
        retained(before, after, InitialSpacingFixtures.key(InitialSpacingFixtures.A, "n4"));
    }

    private static void retained(GraphProjection before, GraphProjection after, ProjectedNodeKey added) throws Exception {
        try (GraphStreamLayoutEngine engine = new GraphStreamLayoutEngine(LayoutCalibration.spikeDefaults())) {
            engine.apply(InitialSpacingFixtures.request(before, before));
            LayoutFrame previous = engine.step(); assertThat(previous.failed()).isFalse();
            LayoutFrame updated = engine.apply(InitialSpacingFixtures.request(before, after));
            assertThat(updated.failed()).isFalse();
            for (Map.Entry<ProjectedNodeKey, LayoutPoint> entry : previous.positions().nodes().entrySet())
                assertThat(updated.positions().nodes().get(entry.getKey())).isEqualTo(entry.getValue());
            for (Map.Entry<EnclosureHullKey, LayoutPoint> entry : previous.positions().anchors().entrySet())
                assertThat(updated.positions().anchors().get(entry.getKey())).isEqualTo(entry.getValue());
            InitialSpacingConsumers.topology(after, engine, 24.0);
            if (added != null) {
                ProjectedEnclosure e = after.enclosures().get(0);
                assertThat(e.directNodes()).hasSize(5); assertThat(e.directNodes().get(4)).isEqualTo(added);
                LayoutPoint centre = InitialSpacingConsumers.composed(new GraphStreamLayoutEngine.BoundarySizes(after), e.hullKey());
                double r = (16.0 + 24.0) / (2.0 * Math.sin(Math.PI / 5.0)), angle = 8.0 * Math.PI / 5.0;
                close(updated.positions().nodes().get(added).x(), centre.x() + r * Math.cos(angle));
                close(updated.positions().nodes().get(added).y(), centre.y() + r * Math.sin(angle));
            }
        }
        try (LayoutWorker worker = new LayoutWorker(LayoutCalibration.spikeDefaults())) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(600);
            InitialSpacingFrameAssertions.awaitWithinDeadline(worker.submit(InitialSpacingFixtures.request(before, before)), deadline);
            InitialSpacingFrameAssertions.awaitWithinDeadline(worker.step(), deadline);
            LayoutFrame frame = InitialSpacingFrameAssertions.awaitWithinDeadline(worker.submit(InitialSpacingFixtures.request(before, after)), deadline);
            InitialSpacingFrameAssertions.check(after, frame, after.pins(), false);
        }
    }

    @Test public void explicitRawResetRestoresFreshSeedsAndKeepsPinsExactly() throws Exception {
        for (List<PinProjection> pins : Arrays.asList(Collections.<PinProjection>emptyList(), Collections.singletonList(
                InitialSpacingFixtures.pin(InitialSpacingFixtures.key(InitialSpacingFixtures.A, "n0"), 25.0, 35.0)))) {
            GraphProjection p = InitialSpacingFixtures.single(4, 1L, false, pins);
            LayoutRequest request = InitialSpacingFixtures.request(p, p);
            try (GraphStreamLayoutEngine changed = new GraphStreamLayoutEngine(LayoutCalibration.spikeDefaults());
                    GraphStreamLayoutEngine fresh = new GraphStreamLayoutEngine(LayoutCalibration.spikeDefaults())) {
                changed.apply(request); LayoutFrame stepped = changed.step();
                assertThat(stepped.failed()).isFalse();
                for (PinProjection pin : pins) assertThat(stepped.positions().nodes().get(pin.projectedNode().get()))
                    .isEqualTo(LayoutPoint.of(pin.x(), pin.y()));
                changed.reset(); LayoutFrame reset = changed.apply(request), independent = fresh.apply(request);
                assertThat(reset.positions()).isEqualTo(independent.positions()); InitialSpacingConsumers.raw(p, reset, 24.0);
            }
        }
    }

    @Test public void healthyRestartPreservesTheTrajectoryInsteadOfReseeding() throws Exception {
        GraphProjection p = InitialSpacingFixtures.single(4, 1L, false, Collections.singletonList(
            InitialSpacingFixtures.pin(InitialSpacingFixtures.key(InitialSpacingFixtures.A, "n0"), 25.0, 35.0)));
        try (LayoutWorker restarted = new LayoutWorker(LayoutCalibration.spikeDefaults());
                LayoutWorker control = new LayoutWorker(LayoutCalibration.spikeDefaults())) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(600);
            InitialSpacingFrameAssertions.awaitWithinDeadline(restarted.submit(InitialSpacingFixtures.request(p, p)), deadline);
            InitialSpacingFrameAssertions.awaitWithinDeadline(control.submit(InitialSpacingFixtures.request(p, p)), deadline);
            LayoutFrame before = InitialSpacingFrameAssertions.awaitWithinDeadline(restarted.step(), deadline);
            LayoutFrame controlBefore = InitialSpacingFrameAssertions.awaitWithinDeadline(control.step(), deadline);
            equalFrames(before, controlBefore);
            restarted.restart();
            assertThat(restarted.lastValidFrame().positions()).isEqualTo(before.positions());
            LayoutFrame actual = InitialSpacingFrameAssertions.awaitWithinDeadline(restarted.step(), deadline);
            LayoutFrame expected = InitialSpacingFrameAssertions.awaitWithinDeadline(control.step(), deadline);
            equalFrames(actual, expected); InitialSpacingFrameAssertions.check(p, actual, p.pins(), false);
        }
    }

    private static void equalFrames(LayoutFrame a, LayoutFrame b) {
        assertThat(a.positions()).isEqualTo(b.positions()); assertThat(a.failed()).isEqualTo(b.failed());
        assertThat(a.stepIndex()).isEqualTo(b.stepIndex()); assertThat(a.residualViolations()).isEqualTo(b.residualViolations());
        assertThat(a.boundaryDiagnostics()).usingRecursiveComparison().isEqualTo(b.boundaryDiagnostics());
        assertThat(a.edgeConflictDiagnostics()).isEqualTo(b.edgeConflictDiagnostics());
        assertThat(a.idle().idle()).isEqualTo(b.idle().idle());
        assertThat(a.idle().rms()).isEqualTo(b.idle().rms()); assertThat(a.idle().max()).isEqualTo(b.idle().max());
    }

    @Test public void initialiseANewWorkspaceAsAFreshEngine() throws Exception {
        GraphProjection p = InitialSpacingFixtures.single(4, 1L, false, Collections.<PinProjection>emptyList());
        LayoutRequest other = LayoutRequest.of(WorkspaceId.of("00000000-0000-0000-0000-0000000000ab"), p,
            ProjectionDiff.between(p, p), p.pins());
        try (GraphStreamLayoutEngine changed = new GraphStreamLayoutEngine(LayoutCalibration.spikeDefaults());
                GraphStreamLayoutEngine fresh = new GraphStreamLayoutEngine(LayoutCalibration.spikeDefaults())) {
            changed.apply(InitialSpacingFixtures.request(p, p)); changed.step();
            assertThat(changed.apply(other).positions()).isEqualTo(fresh.apply(other).positions());
        }
    }

    @Test public void handleEmptyProjectionEmptyEnclosureSingletonTwoNodesAndFiniteExtremes() throws Exception {
        List<GraphProjection> fixtures = new ArrayList<GraphProjection>();
        fixtures.add(InitialSpacingFixtures.projection(1L, Collections.<ProjectedNode>emptyList(),
            Collections.<ProjectedEnclosure>emptyList(), Collections.<ProjectedEdge>emptyList(), Collections.<PinProjection>emptyList()));
        for (int n : new int[] {0, 1, 2}) fixtures.add(InitialSpacingFixtures.root(n, 1L));
        fixtures.add(InitialSpacingFixtures.single(4, 1L, false, Collections.singletonList(
            InitialSpacingFixtures.pin(InitialSpacingFixtures.key(InitialSpacingFixtures.A, "n0"), Double.MAX_VALUE, -Double.MAX_VALUE))));
        for (int i = 0; i < fixtures.size(); i++) {
            GraphProjection p = fixtures.get(i); boolean extreme = i == fixtures.size() - 1;
            try (LayoutWorker worker = new LayoutWorker(LayoutCalibration.spikeDefaults())) {
                LayoutFrame frame = InitialSpacingFrameAssertions.awaitWithinDeadline(worker.submit(InitialSpacingFixtures.request(p, p)),
                    System.nanoTime() + TimeUnit.SECONDS.toNanos(600));
                if (extreme) {
                    if (frame.failed()) assertThat(frame.positions()).isEqualTo(worker.lastValidFrame().positions());
                    else {
                        for (LayoutPoint point : frame.positions().nodes().values()) {
                            finite(point.x()); finite(point.y());
                        }
                        for (LayoutPoint point : frame.positions().anchors().values()) {
                            finite(point.x()); finite(point.y());
                        }
                        assertThat(frame.positions().nodes().get(p.pins().get(0).projectedNode().get()))
                            .isEqualTo(LayoutPoint.of(Double.MAX_VALUE, -Double.MAX_VALUE));
                        assertThat(frame.residualViolations()).isEqualTo(InitialSpacingFrameAssertions.residualPairs(p, frame.positions()).size());
                        assertThat(frame.edgeConflictDiagnostics().indexBudgetExhausted()
                            || frame.edgeConflictDiagnostics().verificationBudgetExhausted()
                            || frame.edgeConflictDiagnostics().detectionTruncated()).isTrue();
                    }
                }
                else InitialSpacingFrameAssertions.check(p, frame, p.pins(), true);
                if (!extreme && i <= 2) assertThat(measure(p, frame.positions())).isEmpty();
                if (i == 3) close(measure(p, frame.positions()).values().iterator().next().median, 24.0);
            }
        }
    }
}
