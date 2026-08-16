package org.freeplane.plugin.graph.control;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.freeplane.plugin.graph.adapter.EdtExecutor;
import org.freeplane.plugin.graph.geometry.GraphGeometryEngine;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.layout.LayoutFrame;
import org.freeplane.plugin.graph.layout.LayoutRequest;
import org.freeplane.plugin.graph.layout.PerceptualIdlePolicy;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectionDiff;
import org.freeplane.plugin.graph.workspace.ListenerRegistration;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;
import org.junit.Test;

public class GraphUpdateCoordinatorShould {
    private static final WorkspaceId WORKSPACE =
        WorkspaceId.of("00000000-0000-0000-0000-000000000201");

    @Test
    public void publishesLoadingThenProjectionAndCanvasInOrder() {
        TestEdt edt = new TestEdt();
        TestScheduler scheduler = new TestScheduler();
        final GraphUpdateCoordinator[] holder = new GraphUpdateCoordinator[1];
        ProjectionBatcher batcher = new ProjectionBatcher(edt, scheduler, () -> 10L, batch -> {
            holder[0].acceptBatch(batch);
        });
        TestPipeline pipeline = new TestPipeline();
        LayoutSettleLoop loop = new LayoutSettleLoop(WORKSPACE, new ImmediateStepper(),
            new GraphGeometryEngine(), edt);
        GraphUpdateCoordinator coordinator = new GraphUpdateCoordinator(pipeline, batcher, loop, edt);
        holder[0] = coordinator;
        List<String> events = new ArrayList<String>();
        coordinator.addProjectionListener(projection -> events.add("projection-" + projection.generation()));
        coordinator.addCanvasStateListener(state -> events.add("canvas-" + state.generation()));

        coordinator.start();
        assertThat(coordinator.currentState().status()).isEqualTo(OperationalStatus.LOADING);
        edt.runQueued();
        scheduler.runAllIncludingCancelled();
        edt.runQueued();

        assertThat(events).containsExactly("canvas-0", "projection-1", "canvas-1");
        assertThat(coordinator.currentProjection().generation()).isEqualTo(1L);
        assertThat(coordinator.currentState().generation()).isEqualTo(1L);
        coordinator.close();
    }

    @Test
    public void queuedChangeIsVisibleBeforeDebounceAcceptance() {
        TestEdt edt = new TestEdt();
        TestScheduler scheduler = new TestScheduler();
        final GraphUpdateCoordinator[] holder = new GraphUpdateCoordinator[1];
        ProjectionBatcher batcher = new ProjectionBatcher(edt, scheduler, () -> 20L, batch -> {
            holder[0].acceptBatch(batch);
        });
        GraphUpdateCoordinator coordinator = new GraphUpdateCoordinator(new TestPipeline(), batcher,
            new LayoutSettleLoop(WORKSPACE, new ImmediateStepper(), new GraphGeometryEngine(), edt), edt);
        holder[0] = coordinator;

        edt.runOnEdt(() -> coordinator.requestRebuild(ChangeKind.TEXT));
        assertThat(coordinator.hasPendingChanges()).isTrue();
        scheduler.runAllIncludingCancelled();
        assertThat(coordinator.hasPendingChanges()).isFalse();
        coordinator.close();
    }

    @Test
    public void ordersListenersAndSuppressesCallbacksAfterClose() {
        TestEdt edt = new TestEdt();
        TestScheduler scheduler = new TestScheduler();
        final GraphUpdateCoordinator[] holder = new GraphUpdateCoordinator[1];
        ProjectionBatcher batcher = new ProjectionBatcher(edt, scheduler, () -> 30L, batch -> {
            holder[0].acceptBatch(batch);
        });
        GraphUpdateCoordinator coordinator = new GraphUpdateCoordinator(new TestPipeline(), batcher,
            new LayoutSettleLoop(WORKSPACE, new ImmediateStepper(), new GraphGeometryEngine(), edt), edt);
        holder[0] = coordinator;
        List<String> callbacks = new ArrayList<String>();
        ListenerRegistration first = coordinator.addCanvasStateListener(state -> callbacks.add("first"));
        coordinator.addCanvasStateListener(state -> callbacks.add("second"));
        coordinator.start();
        edt.runQueued();
        assertThat(callbacks).containsExactly("first", "second");

        first.close();
        coordinator.close();
        scheduler.runAllIncludingCancelled();
        edt.runQueued();

        assertThat(callbacks).containsExactly("first", "second");
    }

    private static final class TestPipeline implements GraphUpdateCoordinator.RebuildPipeline {
        @Override
        public GraphProjection rebuild(final AcceptedBatch batch, final GraphProjection previous) {
            return GraphProjection.projected(batch.generation(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        }
    }

    private static final class ImmediateStepper implements LayoutSettleLoop.FrameStepper {
        @Override
        public CompletionStage<LayoutFrame> submit(final LayoutRequest request) {
            return CompletableFuture.completedFuture(frame(request.projection().generation()));
        }

        @Override
        public CompletionStage<LayoutFrame> step() {
            return CompletableFuture.completedFuture(frame(0L));
        }

        @Override
        public void pause() {
        }

        @Override
        public void restart() {
        }

        @Override
        public LayoutFrame lastValidFrame() {
            return frame(0L);
        }

        @Override
        public void close() {
        }

        private static LayoutFrame frame(final long generation) {
            return LayoutFrame.withDiagnostics(LayoutFrame.of(generation,
                LayoutPositions.of(Collections.emptyMap(), Collections.emptyMap()), false),
                Collections.emptyList(), new PerceptualIdlePolicy.IdleMeasurement(0.0, 0.0, 8, true));
        }
    }

    private static final class TestEdt implements EdtExecutor {
        private final Deque<Runnable> queued = new ArrayDeque<Runnable>();
        private boolean edt;

        @Override
        public <T> T call(final Callable<T> task) {
            if (edt) {
                return callNow(task);
            }
            final Holder<T> result = new Holder<T>();
            runOnEdt(() -> result.value = callNow(task));
            return result.value;
        }

        @Override
        public void execute(final Runnable task) {
            if (edt) {
                task.run();
            }
            else {
                queued.add(task);
            }
        }

        @Override
        public boolean isEdt() {
            return edt;
        }

        private void runOnEdt(final Runnable task) {
            boolean previous = edt;
            edt = true;
            try {
                task.run();
            }
            finally {
                edt = previous;
            }
        }

        private void runQueued() {
            while (!queued.isEmpty()) {
                runOnEdt(queued.remove());
            }
        }

        private static <T> T callNow(final Callable<T> task) {
            try {
                return task.call();
            }
            catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        }
    }

    private static final class Holder<T> {
        private T value;
    }

    private static final class TestScheduler extends AbstractExecutorService implements ScheduledExecutorService {
        private final List<TestScheduledFuture> tasks = new ArrayList<TestScheduledFuture>();
        private boolean shutdown;

        @Override
        public ScheduledFuture<?> schedule(final Runnable command, final long delay, final TimeUnit unit) {
            TestScheduledFuture task = new TestScheduledFuture(command);
            tasks.add(task);
            return task;
        }

        @Override
        public <V> ScheduledFuture<V> schedule(final Callable<V> callable, final long delay, final TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(final Runnable command, final long initialDelay,
                final long period, final TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(final Runnable command, final long initialDelay,
                final long delay, final TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void execute(final Runnable command) {
            command.run();
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(final long timeout, final TimeUnit unit) {
            return shutdown;
        }

        private void runAllIncludingCancelled() {
            for (TestScheduledFuture task : new ArrayList<TestScheduledFuture>(tasks)) {
                task.command.run();
            }
        }
    }

    private static final class TestScheduledFuture implements ScheduledFuture<Object> {
        private final Runnable command;

        private TestScheduledFuture(final Runnable command) {
            this.command = command;
        }

        @Override
        public long getDelay(final TimeUnit unit) {
            return 0L;
        }

        @Override
        public int compareTo(final Delayed other) {
            return 0;
        }

        @Override
        public boolean cancel(final boolean mayInterruptIfRunning) {
            return true;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public Object get() {
            return null;
        }

        @Override
        public Object get(final long timeout, final TimeUnit unit) {
            return null;
        }
    }
}
