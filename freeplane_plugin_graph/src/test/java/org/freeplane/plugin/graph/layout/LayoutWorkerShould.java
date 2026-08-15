package org.freeplane.plugin.graph.layout;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectionDiff;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;
import org.junit.Test;

public class LayoutWorkerShould {
    private static final WorkspaceId WORKSPACE = WorkspaceId.of("00000000-0000-0000-0000-000000000010");

    @Test
    public void serializeEngineCallsAndDecorateSuccessfulFrames() throws Exception {
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        CountingEngine engine = new CountingEngine(active, maximum);
        LayoutWorker worker = new LayoutWorker(new FixedEngineSupplier(engine),
            new PerceptualIdlePolicy(2, 0.1, 0.1));
        try {
            LayoutRequest request = request();
            LayoutFrame applied = await(worker.submit(request));
            LayoutFrame stepped = await(worker.step());

            assertThat(applied.failed()).isFalse();
            assertThat(stepped.failed()).isFalse();
            assertThat(applied.conflicts()).isEmpty();
            assertThat(applied.idle()).isNotNull();
            assertThat(worker.lastValidFrame()).isEqualTo(stepped);
            assertThat(maximum.get()).isEqualTo(1);
            assertThat(engine.applies).isEqualTo(1);
            assertThat(engine.steps).isEqualTo(1);
        }
        finally {
            worker.close();
        }
    }

    @Test
    public void preserveTheLastValidFrameWhenTheEngineFails() throws Exception {
        FailingEngine engine = new FailingEngine();
        LayoutWorker worker = new LayoutWorker(new FixedEngineSupplier(engine),
            new PerceptualIdlePolicy(2, 0.1, 0.1));
        try {
            LayoutFrame valid = await(worker.submit(request()));
            LayoutFrame failed = await(worker.step());

            assertThat(valid.failed()).isFalse();
            assertThat(failed.failed()).isTrue();
            assertThat(failed.positions()).isEqualTo(valid.positions());
            assertThat(worker.lastValidFrame()).isEqualTo(valid);
        }
        finally {
            worker.close();
        }
    }

    @Test
    public void returnRetainedFramesForPausedAndPreSubmitSteps() throws Exception {
        LayoutWorker worker = new LayoutWorker(new FixedEngineSupplier(new CountingEngine(new AtomicInteger(),
            new AtomicInteger())), PerceptualIdlePolicy.spikeDefaults());
        try {
            assertThat(await(worker.step())).isNull();
            worker.pause();
            assertThat(await(worker.step())).isNull();
            worker.restart();
            LayoutFrame frame = await(worker.submit(request()));
            worker.pause();
            assertThat(await(worker.step())).isEqualTo(frame);
        }
        finally {
            worker.close();
        }
    }

    @Test
    public void replaceAFailedEngineBeforeTheNextSubmittedRequest() throws Exception {
        FailingEngine failed = new FailingEngine();
        CountingEngine replacement = new CountingEngine(new AtomicInteger(), new AtomicInteger());
        LayoutWorker worker = new LayoutWorker(new SequenceEngineSupplier(Arrays.<LayoutEngine>asList(failed,
            replacement)), PerceptualIdlePolicy.spikeDefaults());
        try {
            await(worker.submit(request()));
            assertThat(await(worker.step()).failed()).isTrue();

            worker.restart();
            LayoutFrame recovered = await(worker.submit(request()));

            assertThat(recovered.failed()).isFalse();
            assertThat(failed.closed).isTrue();
            assertThat(replacement.applies).isEqualTo(1);
        }
        finally {
            worker.close();
        }
    }

    @Test
    public void cancelQueuedStagesWhenClosed() throws Exception {
        BlockingEngine engine = new BlockingEngine();
        LayoutWorker worker = new LayoutWorker(new FixedEngineSupplier(engine),
            PerceptualIdlePolicy.spikeDefaults());
        Thread closer = null;
        try {
            CompletionStage<LayoutFrame> active = worker.submit(request());
            assertThat(engine.started.await(5L, TimeUnit.SECONDS)).isTrue();
            CompletionStage<LayoutFrame> queued = worker.submit(request());
            closer = new Thread(new Runnable() {
                @Override
                public void run() {
                    worker.close();
                }
            });
            closer.start();
            long cancellationDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
            while (!queued.toCompletableFuture().isCancelled() && System.nanoTime() < cancellationDeadline) {
                Thread.yield();
            }
            assertThat(queued.toCompletableFuture().isCancelled()).isTrue();
            engine.release.countDown();
            closer.join(5000L);
            assertThat(closer.isAlive()).isFalse();
            assertThat(active.toCompletableFuture().isCancelled()).isTrue();
        }
        finally {
            engine.release.countDown();
            if (closer != null) {
                closer.join(5000L);
            }
            worker.close();
        }
    }

    @Test
    public void terminateTheWorkerThreadAcrossRepeatedCloseCycles() {
        for (int cycle = 0; cycle < 25; cycle++) {
            LayoutWorker worker = new LayoutWorker(new FixedEngineSupplier(new CountingEngine(new AtomicInteger(),
                new AtomicInteger())), PerceptualIdlePolicy.spikeDefaults());
            worker.close();
        }

        Set<Thread> threads = Thread.getAllStackTraces().keySet();
        for (Thread thread : threads) {
            assertThat(thread.getName()).doesNotStartWith("freeplane-graph-layout-worker-");
        }
    }

    private static LayoutRequest request() {
        GraphProjection projection = GraphProjection.projected(1L, Collections.emptyList(), Collections.emptyList(),
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        return LayoutRequest.of(WORKSPACE, projection, ProjectionDiff.between(projection, projection),
            Collections.emptyList());
    }

    private static LayoutFrame await(CompletionStage<LayoutFrame> stage) throws Exception {
        return stage.toCompletableFuture().get(5L, TimeUnit.SECONDS);
    }

    private static final class FixedEngineSupplier implements Supplier<LayoutEngine> {
        private final LayoutEngine engine;

        FixedEngineSupplier(LayoutEngine engine) {
            this.engine = engine;
        }

        @Override
        public LayoutEngine get() {
            return engine;
        }
    }

    private static class CountingEngine implements LayoutEngine {
        private final AtomicInteger active;
        private final AtomicInteger maximum;
        volatile boolean closed;
        int applies;
        int steps;

        CountingEngine(AtomicInteger active, AtomicInteger maximum) {
            this.active = active;
            this.maximum = maximum;
        }

        @Override
        public LayoutFrame apply(LayoutRequest request) {
            enter();
            try {
                applies++;
                return frame(applies);
            }
            finally {
                leave();
            }
        }

        @Override
        public LayoutFrame step() {
            enter();
            try {
                steps++;
                return frame(applies + steps);
            }
            finally {
                leave();
            }
        }

        @Override
        public void reset() {
        }

        @Override
        public void close() {
            closed = true;
        }

        private void enter() {
            int current = active.incrementAndGet();
            maximum.updateAndGet(value -> Math.max(value, current));
        }

        private void leave() {
            active.decrementAndGet();
        }

        private static LayoutFrame frame(long index) {
            return LayoutFrame.of(index, LayoutPositions.of(Collections.emptyMap(), Collections.emptyMap()), false);
        }
    }

    private static final class SequenceEngineSupplier implements Supplier<LayoutEngine> {
        private final List<LayoutEngine> engines;
        private int index;

        SequenceEngineSupplier(List<LayoutEngine> engines) {
            this.engines = engines;
        }

        @Override
        public LayoutEngine get() {
            return engines.get(index++);
        }
    }

    private static final class BlockingEngine extends CountingEngine {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        BlockingEngine() {
            super(new AtomicInteger(), new AtomicInteger());
        }

        @Override
        public LayoutFrame apply(LayoutRequest request) {
            started.countDown();
            try {
                release.await();
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return LayoutFrame.of(1L, LayoutPositions.of(Collections.emptyMap(), Collections.emptyMap()), false);
        }
    }

    private static final class FailingEngine extends CountingEngine {
        FailingEngine() {
            super(new AtomicInteger(), new AtomicInteger());
        }

        @Override
        public LayoutFrame step() {
            throw new IllegalStateException("step failed");
        }
    }
}
