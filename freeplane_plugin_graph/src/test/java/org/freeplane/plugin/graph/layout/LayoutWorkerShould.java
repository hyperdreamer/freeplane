package org.freeplane.plugin.graph.layout;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.ProjectionDiff;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.freeplane.plugin.graph.workspace.model.PinRecord;
import org.freeplane.plugin.graph.workspace.model.UnknownXml;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;
import org.junit.Test;

public class LayoutWorkerShould {
    private static final WorkspaceId WORKSPACE = WorkspaceId.of("00000000-0000-0000-0000-000000000010");
    private static final MapReferenceId MAP_ONE = MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    private static final MapReferenceId MAP_TWO = MapReferenceId.of("00000000-0000-0000-0000-000000000002");
    private static final ProjectedNodeKey NODE_ONE = nodeKey(MAP_ONE, "one");
    private static final ProjectedNodeKey NODE_ONE_OTHER = nodeKey(MAP_ONE, "one-other");
    private static final ProjectedNodeKey NODE_TWO = nodeKey(MAP_TWO, "two");
    private static final ProjectedNodeKey NODE_TWO_OTHER = nodeKey(MAP_TWO, "two-other");
    private static final EnclosureHullKey HULL_ONE = hullKey(MAP_ONE, "root-one");
    private static final EnclosureHullKey HULL_TWO = hullKey(MAP_TWO, "root-two");

    @Test
    public void queueStepBehindAnOutstandingSubmit() throws Exception {
        BlockingEngine engine = new BlockingEngine();
        LayoutWorker worker = new LayoutWorker(new FixedEngineSupplier(engine),
            new PerceptualIdlePolicy(2, 0.1, 0.1));
        try {
            CompletionStage<LayoutFrame> submitted = worker.submit(request());
            assertThat(engine.started.await(5L, TimeUnit.SECONDS)).isTrue();

            CompletionStage<LayoutFrame> stepped = worker.step();

            assertThat(stepped.toCompletableFuture().isDone()).isFalse();
            assertThat(engine.steps).isZero();
            engine.release.countDown();

            assertThat(await(submitted)).isNotNull();
            assertThat(await(stepped)).isNotNull();
            assertThat(engine.events).containsExactly("apply", "step");
        }
        finally {
            engine.release.countDown();
            worker.close();
        }
    }

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
    public void decorateNonEmptyFramesWithTemporaryGeometryAndCorrection() throws Exception {
        CountingEngine engine = new CountingEngine(new AtomicInteger(), new AtomicInteger());
        LayoutWorker worker = new LayoutWorker(new FixedEngineSupplier(engine),
            new PerceptualIdlePolicy(2, 0.1, 0.1));
        try {
            LayoutFrame frame = await(worker.submit(request()));

            assertThat(frame.failed()).isFalse();
            assertThat(frame.positions().nodes()).hasSize(4);
            assertThat(frame.positions().anchors()).hasSize(2);
            assertThat(frame.conflicts()).isEmpty();
            assertUniformDelta(engine.lastRaw, frame.positions(), NODE_ONE, NODE_ONE_OTHER, HULL_ONE);
            assertUniformDelta(engine.lastRaw, frame.positions(), NODE_TWO, NODE_TWO_OTHER, HULL_TWO);
            assertThat(frame.positions()).isNotEqualTo(engine.lastRaw);
        }
        finally {
            worker.close();
        }
    }

    @Test
    public void decorateRigidConflictsAndIdleMeasurements() throws Exception {
        PinProjection firstPin = PinProjection.active(pinRecord(MAP_ONE, "one"), NODE_ONE);
        PinProjection secondPin = PinProjection.active(pinRecord(MAP_TWO, "two"), NODE_TWO);
        CountingEngine engine = new CountingEngine(new AtomicInteger(), new AtomicInteger());
        LayoutWorker worker = new LayoutWorker(new FixedEngineSupplier(engine),
            new PerceptualIdlePolicy(2, 0.1, 0.1));
        try {
            LayoutFrame first = await(worker.submit(request(Arrays.asList(firstPin, secondPin))));
            assertThat(first.conflicts()).hasSize(1);
            assertThat(first.conflicts().get(0).blockingPins()).containsExactly(firstPin, secondPin);
            assertThat(first.idle().rms()).isZero();
            assertThat(first.idle().max()).isZero();
            assertThat(first.idle().consecutiveStableFrames()).isEqualTo(1);
            assertThat(first.idle().idle()).isFalse();

            LayoutFrame second = await(worker.step());
            assertThat(second.conflicts()).hasSize(1);
            assertThat(second.conflicts().get(0).firstMap()).isEqualTo(MAP_ONE);
            assertThat(second.conflicts().get(0).secondMap()).isEqualTo(MAP_TWO);
            assertThat(second.conflicts().get(0).blockingPins()).containsExactly(firstPin, secondPin);
            assertThat(second.idle().rms()).isZero();
            assertThat(second.idle().max()).isZero();
            assertThat(second.idle().consecutiveStableFrames()).isEqualTo(2);
            assertThat(second.idle().idle()).isTrue();
        }
        finally {
            worker.close();
        }
    }

    @Test
    public void applyActiveDormantAndUnpinnedTransitions() throws Exception {
        PinProjection active = PinProjection.active(pinRecord(MAP_ONE, "one"), NODE_ONE);
        PinProjection dormant = PinProjection.dormant(active.record());
        CountingEngine engine = new CountingEngine(new AtomicInteger(), new AtomicInteger());
        LayoutWorker worker = new LayoutWorker(new FixedEngineSupplier(engine),
            new PerceptualIdlePolicy(2, 0.1, 0.1));
        try {
            LayoutFrame activeFrame = await(worker.submit(request(Collections.singletonList(active))));
            assertThat(activeFrame.positions().nodes().get(NODE_ONE))
                .isEqualTo(engine.lastRaw.nodes().get(NODE_ONE));
            assertThat(activeFrame.positions().anchors().get(HULL_ONE))
                .isEqualTo(engine.lastRaw.anchors().get(HULL_ONE));

            LayoutFrame dormantFrame = await(worker.submit(request(Collections.singletonList(dormant))));
            assertThat(dormantFrame.positions().nodes().get(NODE_ONE))
                .isNotEqualTo(engine.lastRaw.nodes().get(NODE_ONE));
            assertThat(dormantFrame.positions().anchors().get(HULL_ONE))
                .isNotEqualTo(engine.lastRaw.anchors().get(HULL_ONE));

            LayoutFrame unpinnedFrame = await(worker.submit(request(Collections.<PinProjection>emptyList())));
            assertThat(unpinnedFrame.positions()).isEqualTo(dormantFrame.positions());
        }
        finally {
            worker.close();
        }
    }

    @Test
    public void retainTheLastValidFrameAfterGeometryFailure() throws Exception {
        CountingEngine engine = new CountingEngine(new AtomicInteger(), new AtomicInteger());
        LayoutWorker worker = new LayoutWorker(new FixedEngineSupplier(engine),
            new PerceptualIdlePolicy(2, 0.1, 0.1));
        try {
            LayoutFrame valid = await(worker.submit(request()));
            LayoutFrame failed = await(worker.submit(brokenGeometryRequest()));

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
    public void rejectWorkAfterClose() {
        LayoutWorker worker = new LayoutWorker(new FixedEngineSupplier(new CountingEngine(new AtomicInteger(),
            new AtomicInteger())), PerceptualIdlePolicy.spikeDefaults());
        worker.close();

        assertThat(worker.step().toCompletableFuture().isCompletedExceptionally()).isTrue();
        assertThat(worker.submit(request()).toCompletableFuture().isCompletedExceptionally()).isTrue();
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
        return request(Collections.<PinProjection>emptyList());
    }

    private static LayoutRequest request(List<PinProjection> pins) {
        GraphProjection projection = projection(pins);
        return LayoutRequest.of(WORKSPACE, projection, ProjectionDiff.between(projection, projection), pins);
    }

    private static GraphProjection projection(List<PinProjection> pins) {
        ProjectedEnclosure first = root(MAP_ONE, "root-one", Arrays.asList(NODE_ONE, NODE_ONE_OTHER));
        ProjectedEnclosure second = root(MAP_TWO, "root-two", Arrays.asList(NODE_TWO, NODE_TWO_OTHER));
        return GraphProjection.projected(1L,
            Arrays.asList(node(NODE_ONE), node(NODE_ONE_OTHER), node(NODE_TWO), node(NODE_TWO_OTHER)),
            Arrays.asList(first, second), Collections.emptyList(), Collections.emptyList(), pins);
    }

    private static LayoutRequest brokenGeometryRequest() {
        ProjectedNodeKey missing = nodeKey(MAP_ONE, "missing");
        ProjectedEnclosure broken = root(MAP_ONE, "broken-root", Collections.singletonList(missing));
        GraphProjection projection = GraphProjection.projected(2L, Collections.singletonList(node(NODE_ONE)),
            Collections.singletonList(broken), Collections.emptyList(), Collections.emptyList(),
            Collections.<PinProjection>emptyList());
        return LayoutRequest.of(WORKSPACE, projection, ProjectionDiff.between(projection, projection),
            Collections.<PinProjection>emptyList());
    }

    private static LayoutPositions rawPositions(GraphProjection projection) {
        Map<ProjectedNodeKey, LayoutPoint> nodes = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        for (ProjectedNode node : projection.nodes()) {
            final MapReferenceId map = node.mapReferenceId();
            final double x = MAP_ONE.equals(map) ? 0.0 : 1.0;
            final double y = node.key().equals(NODE_ONE_OTHER) || node.key().equals(NODE_TWO_OTHER) ? 1.0 : 0.0;
            nodes.put(node.key(), LayoutPoint.of(x, y));
        }
        Map<EnclosureHullKey, LayoutPoint> anchors = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        for (ProjectedEnclosure enclosure : projection.enclosures()) {
            anchors.put(enclosure.hullKey(), LayoutPoint.of(
                MAP_ONE.equals(enclosure.mapReferenceId()) ? 0.0 : 1.0, 0.0));
        }
        return LayoutPositions.of(nodes, anchors);
    }

    private static LayoutPoint difference(LayoutPoint after, LayoutPoint before) {
        return LayoutPoint.of(after.x() - before.x(), after.y() - before.y());
    }

    private static ProjectedEnclosure root(MapReferenceId map, String id, List<ProjectedNodeKey> directNodes) {
        EnclosureKey endpoint = EnclosureKey.of(SourceNodeKey.persisted(reference(map, id)));
        return ProjectedEnclosure.of(EnclosureHullKey.of(Collections.singletonList(endpoint)),
            Collections.singletonList(endpoint), Collections.singletonList(SafeNodeLabel.of(id, id)),
            "Map " + map, Optional.<EnclosureHullKey>empty(), directNodes,
            Collections.<EnclosureHullKey>emptyList(), true, BoundaryTier.SUBTLE);
    }

    private static ProjectedNode node(ProjectedNodeKey key) {
        return ProjectedNode.of(key, SafeNodeLabel.of("node", "node"), "Map", false);
    }

    private static ProjectedNodeKey nodeKey(MapReferenceId map, String id) {
        return ProjectedNodeKey.of(SourceNodeKey.persisted(reference(map, id)));
    }

    private static EnclosureHullKey hullKey(MapReferenceId map, String id) {
        return EnclosureHullKey.of(Collections.singletonList(
            EnclosureKey.of(SourceNodeKey.persisted(reference(map, id)))));
    }

    private static NodeReference reference(MapReferenceId map, String id) {
        return NodeReference.of(map, PersistedNodeId.of(id));
    }

    private static PinRecord pinRecord(MapReferenceId map, String id) {
        return PinRecord.of(reference(map, id), 0.0, 0.0, Collections.<UnknownXml>emptyList());
    }

    private static void assertUniformDelta(LayoutPositions before, LayoutPositions after,
            ProjectedNodeKey firstNode, ProjectedNodeKey secondNode, EnclosureHullKey anchor) {
        LayoutPoint delta = difference(after.nodes().get(firstNode), before.nodes().get(firstNode));
        assertThat(delta).isNotEqualTo(LayoutPoint.of(0.0, 0.0));
        assertThat(difference(after.nodes().get(secondNode), before.nodes().get(secondNode))).isEqualTo(delta);
        assertThat(difference(after.anchors().get(anchor), before.anchors().get(anchor))).isEqualTo(delta);
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
        final List<String> events = new ArrayList<String>();
        private LayoutRequest currentRequest;
        LayoutPositions lastRaw;

        CountingEngine(AtomicInteger active, AtomicInteger maximum) {
            this.active = active;
            this.maximum = maximum;
        }

        @Override
        public LayoutFrame apply(LayoutRequest request) {
            enter();
            try {
                applies++;
                currentRequest = request;
                events.add("apply");
                lastRaw = rawPositions(request.projection());
                return LayoutFrame.of(applies, lastRaw, false);
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
                events.add("step");
                lastRaw = rawPositions(currentRequest.projection());
                return LayoutFrame.of(applies + steps, lastRaw, false);
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
            return super.apply(request);
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
