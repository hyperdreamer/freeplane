package org.freeplane.plugin.graph.control;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicBoolean;

import org.freeplane.plugin.graph.adapter.EdtExecutor;
import org.freeplane.plugin.graph.geometry.GraphGeometryEngine;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.layout.LayoutFrame;
import org.freeplane.plugin.graph.layout.LayoutWorker;
import org.freeplane.plugin.graph.layout.PerceptualIdlePolicy;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectionDiff;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;
import org.junit.Test;

public class LayoutSettleLoopShould {
    private static final WorkspaceId WORKSPACE =
        WorkspaceId.of("00000000-0000-0000-0000-000000000101");

    @Test
    public void publishesEverySettlingFrameOnTheEdtUntilIdle() {
        TestStepper stepper = new TestStepper(frame(0L, false), frame(1L, false), frame(2L, true));
        ImmediateEdt edt = new ImmediateEdt();
        LayoutSettleLoop loop = new LayoutSettleLoop(WORKSPACE, stepper, new GraphGeometryEngine(), edt);
        List<CanvasState> states = new ArrayList<CanvasState>();
        GraphProjection projection = emptyProjection(4L);

        CompletionStage<Void> completion = loop.start(batch(4L), projection,
            ProjectionDiff.between(emptyProjection(3L), projection), states::add);

        assertThat(completion.toCompletableFuture().isCompletedExceptionally()).isFalse();
        assertThat(completion.toCompletableFuture().isDone()).isTrue();
        assertThat(states).hasSize(3);
        assertThat(states).extracting(CanvasState::generation).containsExactly(4L, 4L, 4L);
        assertThat(states).extracting(CanvasState::status)
            .containsExactly(OperationalStatus.SETTLING, OperationalStatus.SETTLING, OperationalStatus.IDLE);
        assertThat(stepper.submitCount).isEqualTo(1);
        assertThat(stepper.stepCount).isEqualTo(2);
        assertThat(states.get(0).projection()).isSameAs(projection);
        assertThat(states.get(0).geometry().nodes()).isEmpty();
        assertThat(states.get(0).geometry().hulls()).isEmpty();
        assertThat(edt.callbackCount).isEqualTo(3);
        assertThat(edt.allCallbacksOnEdt).isTrue();
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
        loop.start(batch(8L), second, ProjectionDiff.between(first, second), states::add);

        stepper.first.complete(frame(0L, true));
        assertThat(states).isEmpty();
        stepper.second.complete(frame(1L, true));

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
        return LayoutFrame.withDiagnostics(LayoutFrame.of(index,
            LayoutPositions.of(Collections.emptyMap(), Collections.emptyMap()), false),
            Collections.emptyList(), new PerceptualIdlePolicy.IdleMeasurement(0.0, 0.0,
                idle ? 8 : 1, idle));
    }

    private static final class TestStepper implements LayoutSettleLoop.FrameStepper {
        private final Deque<LayoutFrame> frames = new ArrayDeque<LayoutFrame>();
        private int submitCount;
        private int stepCount;

        private TestStepper(LayoutFrame... values) {
            Collections.addAll(frames, values);
        }

        @Override
        public CompletionStage<LayoutFrame> submit(org.freeplane.plugin.graph.layout.LayoutRequest request) {
            submitCount++;
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
        private int submissions;

        @Override
        public CompletionStage<LayoutFrame> submit(org.freeplane.plugin.graph.layout.LayoutRequest request) {
            submissions++;
            return submissions == 1 ? first : second;
        }

        @Override
        public CompletionStage<LayoutFrame> step() {
            throw new AssertionError("stale run must not step");
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
}
