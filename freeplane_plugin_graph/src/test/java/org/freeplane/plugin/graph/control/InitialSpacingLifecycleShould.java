package org.freeplane.plugin.graph.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.freeplane.plugin.graph.layout.graphstream.SpacingTestSupport.*;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;
import org.freeplane.plugin.graph.adapter.*;
import org.freeplane.plugin.graph.geometry.*;
import org.freeplane.plugin.graph.layout.*;
import org.freeplane.plugin.graph.layout.graphstream.*;
import org.freeplane.plugin.graph.projection.*;
import org.freeplane.plugin.graph.workspace.*;
import org.junit.Test;

public class InitialSpacingLifecycleShould {
    private static final class TestEdt implements EdtExecutor {
        public <T> T call(Callable<T> action) { try { return action.call(); } catch(Exception e) { throw new AssertionError(e); } }
        public void execute(Runnable action) { action.run(); }
        public boolean isEdt() { return true; }
    }
    private static final class RealEngine implements LayoutEngine {
        final LayoutEngine delegate=GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults());
        final AtomicBoolean fail;
        volatile Snapshot last;
        volatile boolean closed;
        RealEngine(AtomicBoolean fail) { this.fail=fail; }
        public LayoutFrame apply(LayoutRequest r) {
            LayoutFrame f=delegate.apply(r); last=inspect(delegate); return f;
        }
        public LayoutFrame step() {
            if(fail.compareAndSet(true,false)) return LayoutFrame.of(0,last.positions,true);
            LayoutFrame f=delegate.step(); last=inspect(delegate); return f;
        }
        public void reset() { delegate.reset(); last=inspect(delegate); }
        public void close() { delegate.close(); closed=true; }
    }
    private static final class Submission {
        final LayoutRequest request;
        final LayoutWorker worker;
        final RealEngine engine;
        final Snapshot raw;
        final LayoutFrame corrected;
        Submission(LayoutRequest r,LayoutWorker w,RealEngine e,LayoutFrame f) {
            request=r;worker=w;engine=e;raw=e.last;corrected=f;
        }
    }
    private static final class Gate {
        final CompletableFuture<Submission> entered=new CompletableFuture<Submission>();
        final CompletableFuture<Void> release=new CompletableFuture<Void>();
    }
    private static final class ObservingStepper implements LayoutSettleLoop.FrameStepper {
        final LayoutSettleLoop.FrameStepper delegate;
        final Set<LayoutWorker> instrumented=Collections.newSetFromMap(new IdentityHashMap<LayoutWorker,Boolean>());
        final AtomicBoolean failNext=new AtomicBoolean();
        final AtomicReference<Gate> nextSubmit=new AtomicReference<Gate>();
        final AtomicReference<Gate> nextStepResult=new AtomicReference<Gate>();
        final AtomicReference<Gate> nextStepBeforeIntegration=new AtomicReference<Gate>();
        volatile LayoutRequest lastRequest;
        volatile RealEngine current;
        ObservingStepper() throws Exception {
            Class<?> type=Class.forName("org.freeplane.plugin.graph.control.LayoutSettleLoop$WorkerStepper");
            Constructor<?> constructor=type.getDeclaredConstructor(LayoutCalibration.class);
            constructor.setAccessible(true);
            delegate=(LayoutSettleLoop.FrameStepper)constructor.newInstance(LayoutCalibration.spikeDefaults());
        }
        LayoutWorker worker() { return (LayoutWorker)read(delegate,"worker"); }
        Gate holdNextSubmit() {
            Gate gate=new Gate(); assertThat(nextSubmit.compareAndSet(null,gate)).isTrue(); return gate;
        }
        Gate holdNextStepResult() {
            Gate gate=new Gate(); assertThat(nextStepResult.compareAndSet(null,gate)).isTrue(); return gate;
        }
        Gate holdNextStepBeforeIntegration() {
            Gate gate=new Gate(); assertThat(nextStepBeforeIntegration.compareAndSet(null,gate)).isTrue(); return gate;
        }
        private Submission observed(LayoutWorker w,LayoutFrame corrected) {
            return new Submission(lastRequest,w,current,corrected);
        }
        public CompletionStage<LayoutFrame> submit(LayoutRequest r) {
            lastRequest=r;
            final LayoutWorker w=worker();
            if(instrumented.add(w)) write(w,"engineFactory",(Supplier<LayoutEngine>)()->{
                RealEngine created=new RealEngine(failNext);current=created;return created;
            });
            Gate gate=nextSubmit.getAndSet(null);
            return delegate.submit(r).thenCompose(f->{
                requireTargets(current.last,20,false,22);
                if(gate==null) return CompletableFuture.completedFuture(f);
                gate.entered.complete(new Submission(r,w,current,f));
                return gate.release.thenApply(ignored->f);
            });
        }
        public CompletionStage<LayoutFrame> step() {
            Gate before=nextStepBeforeIntegration.getAndSet(null);
            if(before!=null) {
                before.entered.complete(observed(worker(),delegate.lastValidFrame()));
                return before.release.thenCompose(ignored->delegate.step());
            }
            Gate after=nextStepResult.getAndSet(null);
            CompletionStage<LayoutFrame> stepping=delegate.step();
            if(after==null) return stepping;
            return stepping.thenCompose(frame->{
                after.entered.complete(observed(worker(),frame));
                return after.release.thenApply(ignored->frame);
            });
        }
        public void pause() { delegate.pause(); }
        public void restart() { delegate.restart(); }
        public void reset() { delegate.reset(); }
        public LayoutFrame lastValidFrame() { return delegate.lastValidFrame(); }
        public void close() {
            for(AtomicReference<Gate> gates:Arrays.asList(nextSubmit,nextStepResult,nextStepBeforeIntegration)) {
                Gate g=gates.getAndSet(null);if(g!=null)g.release.complete(null);
            }
            delegate.close();
        }
    }
    private static final class Fixture implements AutoCloseable {
        final TestEdt edt=new TestEdt();
        final ObservingStepper stepper=new ObservingStepper();
        final BlockingQueue<CanvasState> states=new LinkedBlockingQueue<CanvasState>();
        final ScheduledExecutorService scheduler=Executors.newSingleThreadScheduledExecutor();
        final GraphUpdateCoordinator coordinator;
        Fixture() throws Exception {
            GraphProjection basic=ring(1,2,false);
            List<PinProjection> pins=Collections.singletonList(pin(node(A,"u0"),0,0));
            GraphProjection p=projection(1,basic.nodes(),basic.enclosures(),Collections.singletonList(
                edge(ProjectedEndpointKey.ofNode(node(A,"u0")),ProjectedEndpointKey.ofNode(node(A,"u1")),false,16041)),pins);
            GraphUpdateCoordinator.RebuildPipeline pipeline=(batch,previous)->p;
            ProjectionBatcher batcher=new ProjectionBatcher(edt,scheduler,()->0L,batch->{});
            GraphWorkspaceStore store=mock(GraphWorkspaceStore.class);
            MapLeaseManager leases=mock(MapLeaseManager.class);
            when(store.addListener(any(WorkspaceStoreListener.class))).thenReturn(()->{});
            when(leases.addListener(any(MapAdapterListener.class))).thenReturn(()->{});
            coordinator=new GraphUpdateCoordinator(pipeline,batcher,
                new LayoutSettleLoop(WORKSPACE,stepper,new GraphGeometryEngine(),edt),edt,store,leases);
            coordinator.addCanvasStateListener(state->{
                if(state.layout().positions().nodes().containsKey(node(A,"u0"))) {
                    assertThat(state.layout().positions().nodes().get(node(A,"u0"))).isEqualTo(LayoutPoint.of(0,0));
                }
                states.add(state);
            });
        }
        void start() { coordinator.acceptBatch(new AcceptedBatch(1,1,EnumSet.of(ChangeKind.STRUCTURE))); }
        CanvasState until(OperationalStatus status) throws Exception {
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
            for(;;) {
                long left=end-System.nanoTime(); assertThat(left).as("command publication deadline").isPositive();
                CanvasState state=states.poll(left,TimeUnit.NANOSECONDS);
                assertThat(state).as("command publication").isNotNull();
                if(state.status()==OperationalStatus.FAILED&&status!=OperationalStatus.FAILED)
                    throw new AssertionError("failed frame cannot establish idle");
                if(state.status()==status) {
                    if(status==OperationalStatus.IDLE) {
                        assertThat(state.layout().failed()).isFalse();assertThat(state.layout().verified()).isTrue();
                        assertThat(state.layout().idle().idle()).isTrue();
                    }
                    return state;
                }
            }
        }
        public void close() { coordinator.close();scheduler.shutdownNow(); }
    }
    private static Submission entered(Gate gate) throws Exception { return gate.entered.get(5,TimeUnit.SECONDS); }
    private static void verifyPin(Submission s) {
        assertThat(s.raw.positions.nodes().get(node(A,"u0"))).isEqualTo(LayoutPoint.of(0,0));
        assertThat(s.corrected.positions().nodes().get(node(A,"u0"))).isEqualTo(LayoutPoint.of(0,0));
        requireTargets(s.raw,20,false,22);
        System.out.println("spacing-lifecycle worker="+System.identityHashCode(s.worker)+" engine="+
            System.identityHashCode(s.engine)+" generation="+s.request.projection().generation()+" targets=20/22 pin=0,0");
    }
    @Test public void healthyRestartRetainsRawCoordinatesAndResetReplacesTheRealWorker() throws Exception {
        try(Fixture f=new Fixture()) {
            Gate inFlight=f.stepper.holdNextStepResult();
            f.start();Submission beforeRestart=entered(inFlight);verifyPin(beforeRestart);
            RealEngine retained=beforeRestart.engine;LayoutWorker worker=beforeRestart.worker;
            Snapshot rawBefore=beforeRestart.raw;
            Gate restartBoundary=f.stepper.holdNextStepBeforeIntegration();
            try {
                f.coordinator.pauseLayout();f.coordinator.restartLayout();
                inFlight.release.complete(null);
                Submission resumed=entered(restartBoundary);verifyPin(resumed);
                assertThat(resumed.worker).isSameAs(worker);assertThat(resumed.engine).isSameAs(retained);
                assertThat(retained.closed).isFalse();
                assertThat(resumed.raw.positions).isEqualTo(rawBefore.positions);
                assertThat(resumed.request.projection()).isSameAs(f.coordinator.currentProjection());
            } finally { inFlight.release.complete(null);restartBoundary.release.complete(null); }
            f.until(OperationalStatus.IDLE);f.states.clear();
            Gate reset=f.stepper.holdNextSubmit();
            try {
                f.coordinator.resetLayout();Submission rebuilt=entered(reset);verifyPin(rebuilt);
                assertThat(rebuilt.worker).isNotSameAs(worker);assertThat(rebuilt.engine).isNotSameAs(retained);
                assertThat(retained.closed).isTrue();
                assertThat(rebuilt.request.projection()).isSameAs(f.coordinator.currentProjection());
                try(LayoutEngine fresh=GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
                    assertThat(rebuilt.raw.positions).isEqualTo(fresh.apply(rebuilt.request).positions());
                }
            } finally { reset.release.complete(null); }
            f.until(OperationalStatus.IDLE);
        }
    }
    @Test public void failedRestartReplacesEngineResubmitsCurrentProjectionAndRetainsPin() throws Exception {
        try(Fixture f=new Fixture()) {
            f.stepper.failNext.set(true);f.start();CanvasState failed=f.until(OperationalStatus.FAILED);
            assertThat(failed.layout().failed()).isTrue();
            RealEngine old=f.stepper.current;LayoutWorker worker=f.stepper.worker();f.states.clear();
            Gate recover=f.stepper.holdNextSubmit();
            try {
                f.coordinator.restartLayout();Submission recovered=entered(recover);verifyPin(recovered);
                assertThat(recovered.worker).isSameAs(worker);assertThat(recovered.engine).isNotSameAs(old);
                assertThat(old.closed).isTrue();
                assertThat(recovered.request.projection()).isSameAs(f.coordinator.currentProjection());
                assertThat(recovered.corrected.failed()).isFalse();assertThat(recovered.corrected.verified()).isTrue();
            } finally { recover.release.complete(null); }
            f.until(OperationalStatus.IDLE);
        }
    }
}
