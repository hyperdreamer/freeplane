package org.freeplane.plugin.graph.control;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Dimension;
import java.awt.geom.Dimension2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.freeplane.plugin.graph.adapter.EdtExecutor;
import org.freeplane.plugin.graph.geometry.GeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.GraphGeometryEngine;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.layout.LayoutFrame;
import org.freeplane.plugin.graph.layout.PerceptualIdlePolicy;
import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.ProjectionDiff;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;
import org.junit.Test;

public class LayoutSettleLoopShould {
    private static final WorkspaceId WORKSPACE =
        WorkspaceId.of("00000000-0000-0000-0000-000000000101");
    private static final MapReferenceId MAP =
        MapReferenceId.of("00000000-0000-0000-0000-000000000102");

    @Test
    public void publishesEverySettlingFrameOnTheEdtUntilIdle() {
        GraphProjection projection = populatedProjection(4L);
        TestStepper stepper = new TestStepper(frame(projection, 0L, false), frame(projection, 1L, false),
            frame(projection, 2L, true));
        ImmediateEdt edt = new ImmediateEdt();
        LayoutSettleLoop loop = new LayoutSettleLoop(WORKSPACE, stepper, new GraphGeometryEngine(), edt);
        List<CanvasState> states = new ArrayList<CanvasState>();

        CompletionStage<Void> completion = loop.start(batch(4L), projection,
            ProjectionDiff.between(emptyProjection(3L), projection), states::add);

        assertThat(completion.toCompletableFuture().isCompletedExceptionally()).isFalse();
        await(completion);
        assertThat(states).hasSize(3);
        assertThat(states).extracting(CanvasState::generation).containsExactly(4L, 4L, 4L);
        assertThat(states).extracting(CanvasState::status)
            .containsExactly(OperationalStatus.SETTLING, OperationalStatus.SETTLING, OperationalStatus.IDLE);
        assertThat(stepper.submitCount).isEqualTo(1);
        assertThat(stepper.stepCount).isEqualTo(2);
        assertThat(states.get(0).projection()).isSameAs(projection);
        assertThat(states.get(0).geometry().nodes()).hasSize(1);
        assertThat(states.get(0).geometry().hulls()).hasSize(1);
        assertThat(edt.callbackCount).isEqualTo(3);
        assertThat(edt.allCallbacksOnEdt).isTrue();
        loop.close();
    }

    @Test
    public void publishesEmptyAfterAnAcceptedEmptyFrame() {
        TestStepper stepper = new TestStepper(frame(0L, true));
        LayoutSettleLoop loop = new LayoutSettleLoop(WORKSPACE, stepper, new GraphGeometryEngine(), new ImmediateEdt());
        GraphProjection projection = emptyProjection(5L);
        List<CanvasState> states = new ArrayList<CanvasState>();

        await(loop.start(batch(5L), projection, ProjectionDiff.between(emptyProjection(4L), projection), states::add));

        assertThat(states).hasSize(1);
        assertThat(states.get(0).status()).isEqualTo(OperationalStatus.EMPTY);
        loop.close();
    }

    @Test
    public void publishesAFailedCurrentGenerationWhenItsInitialFrameFails() {
        GraphProjection projection = populatedProjection(6L);
        TestStepper stepper = new TestStepper(failedFrame(0L, LayoutPositions.of(
            Collections.<ProjectedNodeKey, LayoutPoint>emptyMap(),
            Collections.<EnclosureHullKey, LayoutPoint>emptyMap())));
        LayoutSettleLoop loop = new LayoutSettleLoop(WORKSPACE, stepper, new GraphGeometryEngine(), new ImmediateEdt());
        List<CanvasState> states = new ArrayList<CanvasState>();

        await(loop.start(batch(6L), projection, ProjectionDiff.between(emptyProjection(5L), projection), states::add));

        assertThat(states).hasSize(1);
        CanvasState state = states.get(0);
        assertThat(state.generation()).isEqualTo(6L);
        assertThat(state.status()).isEqualTo(OperationalStatus.FAILED);
        assertThat(state.layout().failed()).isTrue();
        assertThat(state.layout().positions().nodes().keySet()).containsExactlyElementsOf(nodeKeys(projection));
        assertThat(state.layout().positions().anchors().keySet()).containsExactlyElementsOf(enclosureKeys(projection));
        loop.close();
    }

    @Test
    public void rejectsALowerGenerationAfterAHigherGenerationHasBeenAccepted() {
        GraphProjection higher = populatedProjection(8L);
        GraphProjection lower = populatedProjection(7L);
        TestStepper stepper = new TestStepper(frame(higher, 0L, true), frame(lower, 1L, true));
        LayoutSettleLoop loop = new LayoutSettleLoop(WORKSPACE, stepper, new GraphGeometryEngine(), new ImmediateEdt());
        List<CanvasState> states = new ArrayList<CanvasState>();

        await(loop.start(batch(8L), higher, ProjectionDiff.between(emptyProjection(7L), higher), states::add));
        await(loop.start(batch(7L), lower, ProjectionDiff.between(emptyProjection(6L), lower), states::add));

        assertThat(stepper.submitCount).isEqualTo(1);
        assertThat(states).hasSize(1);
        assertThat(states.get(0).generation()).isEqualTo(8L);
        loop.close();
    }

    @Test
    public void suppressesAnInFlightFrameCompletedAfterPause() {
        GraphProjection projection = populatedProjection(9L);
        ControlledStepper stepper = new ControlledStepper();
        LayoutSettleLoop loop = new LayoutSettleLoop(WORKSPACE, stepper, new GraphGeometryEngine(), new ImmediateEdt());
        List<CanvasState> states = new ArrayList<CanvasState>();

        CompletionStage<Void> completion = loop.start(batch(9L), projection,
            ProjectionDiff.between(emptyProjection(8L), projection), states::add);
        loop.pause();
        stepper.first.complete(frame(projection, 0L, true));
        stepper.allowStep = true;
        loop.restart();
        stepper.second.complete(frame(projection, 1L, true));
        await(completion);

        assertThat(states).hasSize(1);
        assertThat(states.get(0).layout().stepIndex()).isEqualTo(1L);
        assertThat(stepper.stepCount).isEqualTo(1);
        loop.close();
    }

    @Test
    public void suppressesAQueuedPublicationWhenPausePrecedesEdtDelivery() {
        GraphProjection projection = populatedProjection(10L);
        ControlledStepper stepper = new ControlledStepper();
        QueuedEdt edt = new QueuedEdt();
        RecordingMetrics metrics = new RecordingMetrics(edt);
        LayoutSettleLoop loop = new LayoutSettleLoop(WORKSPACE, stepper, new GraphGeometryEngine(), metrics, edt);
        List<CanvasState> states = new ArrayList<CanvasState>();

        CompletionStage<Void> completion = loop.start(batch(10L), projection,
            ProjectionDiff.between(emptyProjection(9L), projection), states::add);
        stepper.first.complete(frame(projection, 0L, false));
        assertThat(await(metrics.measuredOnEdt)).isFalse();
        await(edt.executed);
        loop.pause();
        stepper.allowStep = true;
        loop.restart();
        edt.runQueued();
        await(stepper.stepCalled);
        stepper.second.complete(frame(projection, 1L, true));
        await(edt.secondExecuted);
        edt.runQueued();
        await(completion);

        assertThat(states).hasSize(1);
        assertThat(states.get(0).layout().stepIndex()).isEqualTo(1L);
        assertThat(stepper.stepCount).isEqualTo(1);
        loop.close();
    }

    @Test
    public void resetsAnIdleRunBySubmittingTheCurrentProjectionAgain() {
        GraphProjection projection = populatedProjection(11L);
        TestStepper stepper = new TestStepper(frame(projection, 0L, true), frame(projection, 1L, true));
        LayoutSettleLoop loop = new LayoutSettleLoop(WORKSPACE, stepper, new GraphGeometryEngine(), new ImmediateEdt());
        List<CanvasState> states = new ArrayList<CanvasState>();
        CompletableFuture<CanvasState> resetPublication = new CompletableFuture<CanvasState>();

        await(loop.start(batch(11L), projection, ProjectionDiff.between(emptyProjection(10L), projection), state -> {
            states.add(state);
            if (states.size() == 2) {
                resetPublication.complete(state);
            }
        }));
        reset(loop);
        await(resetPublication);

        assertThat(stepper.restartCount).isEqualTo(1);
        assertThat(stepper.submitCount).isEqualTo(2);
        assertThat(stepper.requests.get(1)).isNotSameAs(stepper.requests.get(0));
        assertThat(stepper.requests.get(1).projection()).isSameAs(projection);
        assertThat(states).extracting(CanvasState::generation).containsExactly(11L, 11L);
        loop.close();
    }

    @Test
    public void continuesSettlingAfterAListenerThrows() {
        GraphProjection projection = populatedProjection(12L);
        TestStepper stepper = new TestStepper(frame(projection, 0L, false), frame(projection, 1L, true));
        LayoutSettleLoop loop = new LayoutSettleLoop(WORKSPACE, stepper, new GraphGeometryEngine(), new ImmediateEdt());
        List<CanvasState> states = new ArrayList<CanvasState>();
        AtomicBoolean first = new AtomicBoolean(true);

        await(loop.start(batch(12L), projection, ProjectionDiff.between(emptyProjection(11L), projection), state -> {
            states.add(state);
            if (first.getAndSet(false)) {
                throw new IllegalStateException("listener failure");
            }
        }));

        assertThat(states).extracting(CanvasState::status)
            .containsExactly(OperationalStatus.SETTLING, OperationalStatus.IDLE);
        assertThat(stepper.stepCount).isEqualTo(1);
        loop.close();
    }

    @Test
    public void handlesAnAlreadyCompletedFrameOffTheEdt() {
        GraphProjection projection = populatedProjection(13L);
        TestStepper stepper = new TestStepper(frame(projection, 0L, true));
        QueuedEdt edt = new QueuedEdt();
        RecordingMetrics metrics = new RecordingMetrics(edt);
        LayoutSettleLoop loop = new LayoutSettleLoop(WORKSPACE, stepper, new GraphGeometryEngine(), metrics, edt);
        final StageHolder completion = new StageHolder();
        AtomicBoolean listenerOnEdt = new AtomicBoolean();

        edt.runOnEdt(() -> completion.value = loop.start(batch(13L), projection,
            ProjectionDiff.between(emptyProjection(12L), projection), state -> listenerOnEdt.set(edt.isEdt())));
        assertThat(await(metrics.measuredOnEdt)).isFalse();
        await(edt.executed);
        edt.runQueued();
        await(completion.value);

        assertThat(listenerOnEdt).isTrue();
        loop.close();
    }

    @Test
    public void suppressesAStaleRunWhenANewerGenerationStarts() {
        ControlledStepper stepper = new ControlledStepper();
        ImmediateEdt edt = new ImmediateEdt();
        LayoutSettleLoop loop = new LayoutSettleLoop(WORKSPACE, stepper, new GraphGeometryEngine(), edt);
        List<CanvasState> states = new ArrayList<CanvasState>();
        GraphProjection first = emptyProjection(7L);
        GraphProjection second = emptyProjection(8L);

        loop.start(batch(7L), first, ProjectionDiff.between(emptyProjection(6L), first), states::add);
        CompletionStage<Void> secondCompletion = loop.start(batch(8L), second,
            ProjectionDiff.between(first, second), states::add);

        stepper.first.complete(frame(0L, true));
        assertThat(states).isEmpty();
        stepper.second.complete(frame(1L, true));
        await(secondCompletion);

        assertThat(states).hasSize(1);
        assertThat(states.get(0).generation()).isEqualTo(8L);
        assertThat(states.get(0).projection()).isSameAs(second);
        loop.close();
    }

    @Test
    public void canvasStateCopiesItsStatusAndReferencesImmutably() {
        GraphProjection projection = emptyProjection(9L);
        CanvasState state = CanvasState.of(9L, projection, frame(0L, true),
            new GraphGeometryEngine().computeHulls(projection, LayoutPositions.of(
                Collections.emptyMap(), Collections.emptyMap())), OperationalStatus.IDLE);

        assertThat(state.generation()).isEqualTo(9L);
        assertThat(state.projection()).isSameAs(projection);
        assertThat(state.layout().positions().nodes()).isEmpty();
        assertThat(state.geometry().nodes()).isEmpty();
        assertThat(state.status()).isEqualTo(OperationalStatus.IDLE);
    }

    private static AcceptedBatch batch(long generation) {
        return new AcceptedBatch(generation, generation, EnumSet.of(ChangeKind.STRUCTURE));
    }

    private static GraphProjection emptyProjection(long generation) {
        return GraphProjection.projected(generation, Collections.emptyList(), Collections.emptyList(),
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    private static LayoutFrame frame(long index, boolean idle) {
        return frame(index, LayoutPositions.of(Collections.<ProjectedNodeKey, LayoutPoint>emptyMap(),
            Collections.<EnclosureHullKey, LayoutPoint>emptyMap()), false, idle);
    }

    private static LayoutFrame frame(GraphProjection projection, long index, boolean idle) {
        return frame(index, positions(projection), false, idle);
    }

    private static LayoutFrame failedFrame(long index, LayoutPositions positions) {
        return frame(index, positions, true, true);
    }

    private static LayoutFrame frame(long index, LayoutPositions positions, boolean failed, boolean idle) {
        return LayoutFrame.withDiagnostics(LayoutFrame.of(index, positions, failed),
            Collections.emptyList(), new PerceptualIdlePolicy.IdleMeasurement(0.0, 0.0,
                idle ? 8 : 1, idle));
    }

    private static LayoutPositions positions(GraphProjection projection) {
        Map<ProjectedNodeKey, LayoutPoint> nodes = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        int nodeIndex = 0;
        for (ProjectedNode node : projection.nodes()) {
            nodes.put(node.key(), LayoutPoint.of(nodeIndex++ * 32.0, 0.0));
        }
        Map<EnclosureHullKey, LayoutPoint> anchors = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        int enclosureIndex = 0;
        for (ProjectedEnclosure enclosure : projection.enclosures()) {
            anchors.put(enclosure.hullKey(), LayoutPoint.of(enclosureIndex++ * 32.0, 0.0));
        }
        return LayoutPositions.of(nodes, anchors);
    }

    private static List<ProjectedNodeKey> nodeKeys(GraphProjection projection) {
        List<ProjectedNodeKey> keys = new ArrayList<ProjectedNodeKey>();
        for (ProjectedNode node : projection.nodes()) {
            keys.add(node.key());
        }
        return keys;
    }

    private static List<EnclosureHullKey> enclosureKeys(GraphProjection projection) {
        List<EnclosureHullKey> keys = new ArrayList<EnclosureHullKey>();
        for (ProjectedEnclosure enclosure : projection.enclosures()) {
            keys.add(enclosure.hullKey());
        }
        return keys;
    }

    private static GraphProjection populatedProjection(long generation) {
        ProjectedNodeKey nodeKey = ProjectedNodeKey.of(SourceNodeKey.persisted(reference("node-" + generation)));
        ProjectedNode node = ProjectedNode.of(nodeKey, SafeNodeLabel.of("Node", "Node"), "Map", false);
        EnclosureKey endpoint = EnclosureKey.of(SourceNodeKey.persisted(reference("hull-" + generation)));
        EnclosureHullKey hullKey = EnclosureHullKey.of(Collections.singletonList(endpoint));
        ProjectedEnclosure enclosure = ProjectedEnclosure.of(hullKey, Collections.singletonList(endpoint),
            Collections.singletonList(SafeNodeLabel.of("Map", "Map")), "Map", Optional.empty(),
            Collections.singletonList(nodeKey), Collections.emptyList(), true, BoundaryTier.SUBTLE);
        return GraphProjection.projected(generation, Collections.singletonList(node),
            Collections.singletonList(enclosure), Collections.emptyList(), Collections.emptyList(),
            Collections.emptyList());
    }

    private static NodeReference reference(String id) {
        return NodeReference.of(MAP, PersistedNodeId.of(id));
    }

    private static void reset(LayoutSettleLoop loop) {
        loop.reset();
    }

    private static <T> T await(CompletionStage<T> stage) {
        try {
            return stage.toCompletableFuture().get(5L, TimeUnit.SECONDS);
        }
        catch (Exception exception) {
            throw new AssertionError("Timed out waiting for layout settlement", exception);
        }
    }

    private static final class TestStepper implements LayoutSettleLoop.FrameStepper {
        private final Deque<LayoutFrame> frames = new ArrayDeque<LayoutFrame>();
        private final List<org.freeplane.plugin.graph.layout.LayoutRequest> requests =
            new ArrayList<org.freeplane.plugin.graph.layout.LayoutRequest>();
        private int submitCount;
        private int stepCount;
        private int restartCount;

        private TestStepper(LayoutFrame... values) {
            Collections.addAll(frames, values);
        }

        @Override
        public CompletionStage<LayoutFrame> submit(org.freeplane.plugin.graph.layout.LayoutRequest request) {
            submitCount++;
            requests.add(request);
            return CompletableFuture.completedFuture(frames.removeFirst());
        }

        @Override
        public CompletionStage<LayoutFrame> step() {
            stepCount++;
            return CompletableFuture.completedFuture(frames.removeFirst());
        }

        @Override
        public void pause() {
        }

        @Override
        public void restart() {
            restartCount++;
        }

        @Override
        public LayoutFrame lastValidFrame() {
            return frame(0L, true);
        }

        @Override
        public void close() {
        }
    }

    private static final class ControlledStepper implements LayoutSettleLoop.FrameStepper {
        private final CompletableFuture<LayoutFrame> first = new CompletableFuture<LayoutFrame>();
        private final CompletableFuture<LayoutFrame> second = new CompletableFuture<LayoutFrame>();
        private final CompletableFuture<Void> stepCalled = new CompletableFuture<Void>();
        private int submissions;
        private int stepCount;
        private boolean allowStep;

        @Override
        public CompletionStage<LayoutFrame> submit(org.freeplane.plugin.graph.layout.LayoutRequest request) {
            submissions++;
            return submissions == 1 ? first : second;
        }

        @Override
        public CompletionStage<LayoutFrame> step() {
            stepCount++;
            stepCalled.complete(null);
            if (!allowStep) {
                throw new AssertionError("stale run must not step");
            }
            return second;
        }

        @Override
        public void pause() {
        }

        @Override
        public void restart() {
        }

        @Override
        public LayoutFrame lastValidFrame() {
            return frame(0L, true);
        }

        @Override
        public void close() {
        }
    }

    private static final class ImmediateEdt implements EdtExecutor {
        private boolean edt;
        private int callbackCount;
        private boolean allCallbacksOnEdt = true;

        @Override
        public <T> T call(java.util.concurrent.Callable<T> task) {
            try {
                return task.call();
            }
            catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public void execute(Runnable task) {
            boolean previous = edt;
            edt = true;
            callbackCount++;
            try {
                task.run();
            }
            catch (RuntimeException failure) {
                allCallbacksOnEdt = false;
                throw failure;
            }
            finally {
                edt = previous;
            }
        }

        @Override
        public boolean isEdt() {
            return edt;
        }
    }

    private static final class QueuedEdt implements EdtExecutor {
        private final Deque<Runnable> queued = new ArrayDeque<Runnable>();
        private final CompletableFuture<Void> executed = new CompletableFuture<Void>();
        private final CompletableFuture<Void> secondExecuted = new CompletableFuture<Void>();
        private int executionCount;
        private boolean edt;

        @Override
        public <T> T call(java.util.concurrent.Callable<T> task) {
            try {
                return task.call();
            }
            catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public void execute(Runnable task) {
            synchronized (this) {
                executionCount++;
                if (executionCount == 1) {
                    executed.complete(null);
                }
                else if (executionCount == 2) {
                    secondExecuted.complete(null);
                }
                if (edt) {
                    task.run();
                }
                else {
                    queued.add(task);
                }
            }
        }

        @Override
        public synchronized boolean isEdt() {
            return edt;
        }

        private void runOnEdt(Runnable task) {
            synchronized (this) {
                boolean previous = edt;
                edt = true;
                try {
                    task.run();
                }
                finally {
                    edt = previous;
                }
            }
        }

        private void runQueued() {
            for (;;) {
                final Runnable task;
                synchronized (this) {
                    task = queued.pollFirst();
                }
                if (task == null) {
                    return;
                }
                runOnEdt(task);
            }
        }
    }

    private static final class RecordingMetrics implements GeometryTextMetrics {
        private final QueuedEdt edt;
        private final CompletableFuture<Boolean> measuredOnEdt = new CompletableFuture<Boolean>();

        private RecordingMetrics(QueuedEdt edt) {
            this.edt = edt;
        }

        @Override
        public Dimension2D measure(String displayText, BoundaryTier tier) {
            measuredOnEdt.complete(edt.isEdt());
            return new Dimension(10, 10);
        }
    }

    private static final class StageHolder {
        private CompletionStage<Void> value;
    }
}
