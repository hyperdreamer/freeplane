package org.freeplane.plugin.graph.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.freeplane.plugin.graph.adapter.EdtExecutor;
import org.freeplane.plugin.graph.adapter.MapAdapterEvent;
import org.freeplane.plugin.graph.adapter.MapAdapterListener;
import org.freeplane.plugin.graph.adapter.MapLease;
import org.freeplane.plugin.graph.adapter.MapLeaseManager;
import org.freeplane.plugin.graph.adapter.MapOperationalState;
import org.freeplane.plugin.graph.adapter.MapSnapshotFactory;
import org.freeplane.plugin.graph.geometry.GraphGeometryEngine;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.layout.LayoutFrame;
import org.freeplane.plugin.graph.layout.LayoutRequest;
import org.freeplane.plugin.graph.layout.PerceptualIdlePolicy;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectionEngine;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.input.MapSnapshot;
import org.freeplane.plugin.graph.projection.input.NodeSnapshot;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.GraphWorkspaceStore;
import org.freeplane.plugin.graph.workspace.ListenerRegistration;
import org.freeplane.plugin.graph.workspace.WorkspaceStoreEvent;
import org.freeplane.plugin.graph.workspace.WorkspaceStoreListener;
import org.freeplane.plugin.graph.workspace.model.MapReference;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.freeplane.plugin.graph.workspace.model.UnknownXml;
import org.freeplane.plugin.graph.workspace.model.WorkspaceDocument;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;
import org.junit.Test;

public class GraphUpdateCoordinatorShould {
    private static final WorkspaceId WORKSPACE =
        WorkspaceId.of("00000000-0000-0000-0000-000000000201");
    private static final MapReferenceId MAP =
        MapReferenceId.of("00000000-0000-0000-0000-000000000202");

    @Test
    public void publishesLoadingThenProjectionAndCanvasInOrder() {
        ImmediateEdt edt = new ImmediateEdt();
        TestScheduler scheduler = new TestScheduler();
        final GraphUpdateCoordinator[] holder = new GraphUpdateCoordinator[1];
        ProjectionBatcher batcher = new ProjectionBatcher(edt, scheduler, () -> 10L, batch -> {
            holder[0].acceptBatch(batch);
        });
        TestPipeline pipeline = new TestPipeline();
        LayoutSettleLoop loop = new LayoutSettleLoop(WORKSPACE, new ImmediateStepper(),
            new GraphGeometryEngine(), edt);
        GraphUpdateCoordinator coordinator = coordinator(pipeline, batcher, loop, edt);
        holder[0] = coordinator;
        List<String> events = new ArrayList<String>();
        CompletableFuture<CanvasState> acceptedCanvas = new CompletableFuture<CanvasState>();
        coordinator.addProjectionListener(projection -> events.add("projection-" + projection.generation()));
        coordinator.addCanvasStateListener(state -> {
            events.add("canvas-" + state.generation());
            if (state.generation() == 1L) {
                acceptedCanvas.complete(state);
            }
        });

        coordinator.start();
        assertThat(coordinator.currentState().status()).isEqualTo(OperationalStatus.LOADING);
        scheduler.runAllIncludingCancelled();
        await(acceptedCanvas);

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
        GraphUpdateCoordinator coordinator = coordinator(new TestPipeline(), batcher,
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
        GraphUpdateCoordinator coordinator = coordinator(new TestPipeline(), batcher,
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

    @Test
    public void queuesRebuildForDocumentStoreEvent() {
        SourceFixture source = sourceCoordinator();
        try {
            source.storeListener.get().onWorkspaceStoreEvent(
                workspaceEvent(WorkspaceStoreEvent.Type.DOCUMENT_CHANGED));

            assertThat(source.coordinator.hasPendingChanges()).isTrue();
            source.edt.runQueued();
            assertThat(source.batcher.pendingKinds()).isNotEmpty();
        }
        finally {
            source.coordinator.close();
        }
    }

    @Test
    public void queuesRebuildForIdentityStoreEvent() {
        SourceFixture source = sourceCoordinator();
        try {
            source.storeListener.get().onWorkspaceStoreEvent(
                workspaceEvent(WorkspaceStoreEvent.Type.IDENTITY_CHANGED));

            assertThat(source.coordinator.hasPendingChanges()).isTrue();
            source.edt.runQueued();
            assertThat(source.batcher.pendingKinds()).isNotEmpty();
        }
        finally {
            source.coordinator.close();
        }
    }

    @Test
    public void rebuildsAfterADeferredLeaseAttachment() throws Exception {
        TestEdt mapEdt = new TestEdt();
        GraphWorkspaceStore store = mock(GraphWorkspaceStore.class);
        MapLeaseManager leaseManager = mock(MapLeaseManager.class);
        MapSnapshotFactory snapshots = mock(MapSnapshotFactory.class);
        MapReference reference = MapReference.of(MAP, 1L, URI.create("maps/map.mm"), true,
            "#4E79A7", Collections.<UnknownXml>emptyList());
        WorkspaceDocument document = WorkspaceDocument.createVersion1(WORKSPACE).toBuilder()
            .maps(Collections.singletonList(reference)).build();
        when(store.currentDocument()).thenReturn(document);
        when(store.addListener(any(WorkspaceStoreListener.class))).thenReturn(new ListenerRegistrationStub());
        List<MapAdapterListener> adapterListeners = new ArrayList<MapAdapterListener>();
        when(leaseManager.addListener(any(MapAdapterListener.class))).thenAnswer(invocation -> {
            adapterListeners.add(invocation.getArgument(0));
            return new ListenerRegistrationStub();
        });

        CompletableFuture<MapLease> acquisition = new CompletableFuture<MapLease>();
        MapLease lease = mock(MapLease.class);
        when(lease.mapReferenceId()).thenReturn(MAP);
        when(lease.state()).thenReturn(MapOperationalState.AVAILABLE);
        MapSnapshot snapshot = snapshot(MAP);
        when(snapshots.snapshot(same(lease))).thenReturn(snapshot);
        WorkspaceMapCoordinator maps = new WorkspaceMapCoordinator(snapshots, leaseManager, store, mapEdt,
            ignored -> acquisition);
        LayoutSettleLoop settleLoop = mock(LayoutSettleLoop.class);
        GraphUpdateCoordinator coordinator = new GraphUpdateCoordinator(maps, store, leaseManager,
            new ProjectionEngine(), settleLoop);
        CountDownLatch firstProjection = new CountDownLatch(1);
        CountDownLatch emptyAvailabilityProjection = new CountDownLatch(1);
        CountDownLatch projectedAfterAttachment = new CountDownLatch(1);
        coordinator.addProjectionListener(projection -> {
            if (projection.generation() == 1L) {
                firstProjection.countDown();
            }
            if (projection.generation() >= 2L && projection.nodes().isEmpty()) {
                emptyAvailabilityProjection.countDown();
            }
            if (!projection.nodes().isEmpty()) {
                projectedAfterAttachment.countDown();
            }
        });

        try {
            coordinator.start();
            assertThat(firstProjection.await(5L, TimeUnit.SECONDS)).isTrue();
            assertThat(coordinator.currentProjection().nodes()).isEmpty();

            MapAdapterEvent available = new MapAdapterEvent(MAP, MapOperationalState.AVAILABLE);
            for (MapAdapterListener listener : adapterListeners) {
                listener.onMapAdapterEvent(available);
            }
            mapEdt.runQueued();
            assertThat(emptyAvailabilityProjection.await(5L, TimeUnit.SECONDS)).isTrue();

            acquisition.complete(lease);
            mapEdt.runQueued();

            assertThat(projectedAfterAttachment.await(5L, TimeUnit.SECONDS)).isTrue();
            assertThat(coordinator.currentProjection().nodes()).isNotEmpty();
        }
        finally {
            coordinator.close();
        }
    }

    @Test
    public void rebuildsAfterADeferredLeaseAttachmentWithoutSourceListeners() throws Exception {
        TestEdt mapEdt = new TestEdt();
        GraphWorkspaceStore store = mock(GraphWorkspaceStore.class);
        MapLeaseManager leaseManager = mock(MapLeaseManager.class);
        MapSnapshotFactory snapshots = mock(MapSnapshotFactory.class);
        MapReference reference = MapReference.of(MAP, 1L, URI.create("maps/map.mm"), true,
            "#4E79A7", Collections.<UnknownXml>emptyList());
        WorkspaceDocument document = WorkspaceDocument.createVersion1(WORKSPACE).toBuilder()
            .maps(Collections.singletonList(reference)).build();
        when(store.currentDocument()).thenReturn(document);
        when(store.addListener(any(WorkspaceStoreListener.class))).thenReturn(new ListenerRegistrationStub());
        when(leaseManager.addListener(any(MapAdapterListener.class))).thenReturn(new ListenerRegistrationStub());

        CompletableFuture<MapLease> acquisition = new CompletableFuture<MapLease>();
        MapLease lease = mock(MapLease.class);
        when(lease.mapReferenceId()).thenReturn(MAP);
        when(lease.state()).thenReturn(MapOperationalState.AVAILABLE);
        when(snapshots.snapshot(same(lease))).thenReturn(snapshot(MAP));
        WorkspaceMapCoordinator maps = new WorkspaceMapCoordinator(snapshots, leaseManager, store, mapEdt,
            ignored -> acquisition);
        GraphUpdateCoordinator coordinator = new GraphUpdateCoordinator(maps, new ProjectionEngine(),
            mock(LayoutSettleLoop.class));
        CountDownLatch initialProjection = new CountDownLatch(1);
        CountDownLatch projectedAfterAttachment = new CountDownLatch(1);
        coordinator.addProjectionListener(projection -> {
            if (projection.generation() == 1L) {
                initialProjection.countDown();
            }
            if (!projection.nodes().isEmpty()) {
                projectedAfterAttachment.countDown();
            }
        });

        try {
            coordinator.start();
            assertThat(initialProjection.await(5L, TimeUnit.SECONDS)).isTrue();
            assertThat(coordinator.currentProjection().nodes()).isEmpty();

            acquisition.complete(lease);
            mapEdt.runQueued();

            assertThat(projectedAfterAttachment.await(5L, TimeUnit.SECONDS)).isTrue();
            assertThat(coordinator.currentProjection().nodes()).isNotEmpty();
        }
        finally {
            coordinator.close();
        }
    }

    @Test
    public void queuesMapStateRebuildForAdapterEvent() {
        SourceFixture source = sourceCoordinator();
        try {
            source.adapterListener.get().onMapAdapterEvent(
                new MapAdapterEvent(MAP, MapOperationalState.RELOAD_REQUIRED));

            assertThat(source.coordinator.hasPendingChanges()).isTrue();
            source.edt.runQueued();
            assertThat(source.batcher.pendingKinds()).containsExactly(ChangeKind.MAP_STATE);
        }
        finally {
            source.coordinator.close();
        }
    }

    @Test
    public void releasesCoordinatorOwnedSourceRegistrationsOnClose() {
        SourceFixture source = sourceCoordinator();

        source.coordinator.close();

        assertThat(source.storeRegistration.closeCount()).isEqualTo(1);
        assertThat(source.adapterRegistration.closeCount()).isEqualTo(1);
        source.storeListener.get().onWorkspaceStoreEvent(workspaceEvent(WorkspaceStoreEvent.Type.DOCUMENT_CHANGED));
        source.adapterListener.get().onMapAdapterEvent(new MapAdapterEvent(MAP, MapOperationalState.AVAILABLE));
        assertThat(source.coordinator.hasPendingChanges()).isFalse();
    }

    @Test
    public void closesOwnedResourcesWhenSourceRegistrationFailsDuringConstruction() {
        ProjectionBatcher batcher = mock(ProjectionBatcher.class);
        LayoutSettleLoop loop = mock(LayoutSettleLoop.class);
        EdtExecutor edt = mock(EdtExecutor.class);
        GraphWorkspaceStore store = mock(GraphWorkspaceStore.class);
        MapLeaseManager leaseManager = mock(MapLeaseManager.class);
        when(store.addListener(any())).thenThrow(new IllegalStateException("registration failed"));

        try {
            new GraphUpdateCoordinator(new TestPipeline(), batcher, loop, edt, store, leaseManager);
            throw new AssertionError("construction should fail");
        }
        catch (IllegalStateException expected) {
            assertThat(expected).hasMessage("registration failed");
        }

        verify(batcher).close();
        verify(loop).close();
    }

    @Test
    public void closesCoordinatorOwnedMapsWhenPublicConstructionFails() {
        WorkspaceMapCoordinator maps = mock(WorkspaceMapCoordinator.class);
        LayoutSettleLoop loop = mock(LayoutSettleLoop.class);
        GraphWorkspaceStore store = mock(GraphWorkspaceStore.class);
        MapLeaseManager leaseManager = mock(MapLeaseManager.class);
        when(store.addListener(any())).thenThrow(new IllegalStateException("registration failed"));

        try {
            new GraphUpdateCoordinator(maps, store, leaseManager, new ProjectionEngine(), loop);
            throw new AssertionError("construction should fail");
        }
        catch (IllegalStateException expected) {
            assertThat(expected).hasMessage("registration failed");
        }

        verify(maps).close();
        verify(loop).close();
    }

    @Test
    public void closesSourceRegistrationsWhenLeaseAttachmentRegistrationFails() {
        WorkspaceMapCoordinator maps = mock(WorkspaceMapCoordinator.class);
        LayoutSettleLoop loop = mock(LayoutSettleLoop.class);
        GraphWorkspaceStore store = mock(GraphWorkspaceStore.class);
        MapLeaseManager leaseManager = mock(MapLeaseManager.class);
        ListenerRegistrationStub storeRegistration = new ListenerRegistrationStub();
        ListenerRegistrationStub adapterRegistration = new ListenerRegistrationStub();
        when(store.addListener(any())).thenReturn(storeRegistration);
        when(leaseManager.addListener(any())).thenReturn(adapterRegistration);
        when(maps.addLeaseAttachmentListener(any(WorkspaceMapCoordinator.LeaseAttachmentListener.class)))
            .thenThrow(new IllegalStateException("attachment registration failed"));

        try {
            new GraphUpdateCoordinator(maps, store, leaseManager, new ProjectionEngine(), loop);
            throw new AssertionError("construction should fail");
        }
        catch (IllegalStateException expected) {
            assertThat(expected).hasMessage("attachment registration failed");
        }

        assertThat(storeRegistration.closeCount()).isEqualTo(1);
        assertThat(adapterRegistration.closeCount()).isEqualTo(1);
        verify(maps).close();
        verify(loop).close();
    }

    @Test
    public void closesOwnedResourcesWhenPublicConstructionSourceIsMissing() {
        WorkspaceMapCoordinator maps = mock(WorkspaceMapCoordinator.class);
        LayoutSettleLoop loop = mock(LayoutSettleLoop.class);
        GraphWorkspaceStore store = mock(GraphWorkspaceStore.class);

        try {
            new GraphUpdateCoordinator(maps, store, null, new ProjectionEngine(), loop);
            throw new AssertionError("construction should fail");
        }
        catch (NullPointerException expected) {
            assertThat(expected).hasMessage("leaseManager");
        }

        verify(maps).close();
        verify(loop).close();
    }

    @Test
    public void closesSuppliedResourcesWhenAConstructionSourceIsMissing() {
        ProjectionBatcher batcher = mock(ProjectionBatcher.class);
        LayoutSettleLoop loop = mock(LayoutSettleLoop.class);
        EdtExecutor edt = mock(EdtExecutor.class);
        MapLeaseManager leaseManager = mock(MapLeaseManager.class);

        try {
            new GraphUpdateCoordinator(new TestPipeline(), batcher, loop, edt, null, leaseManager);
            throw new AssertionError("construction should fail");
        }
        catch (NullPointerException expected) {
            assertThat(expected).hasMessage("store");
        }

        verify(batcher).close();
        verify(loop).close();
    }

    @Test
    public void recoversFailedLayoutThroughTheCoordinatorRestartCommand() {
        ImmediateEdt edt = new ImmediateEdt();
        LayoutSettleLoopShould.ManualLifecycleDispatcher dispatcher =
            new LayoutSettleLoopShould.ManualLifecycleDispatcher();
        LayoutSettleLoopShould.FailedThenIdleStepper stepper =
            new LayoutSettleLoopShould.FailedThenIdleStepper();
        LayoutSettleLoop loop = new LayoutSettleLoop(WORKSPACE, stepper, new GraphGeometryEngine(), edt, dispatcher);
        GraphUpdateCoordinator coordinator = coordinator(new GraphUpdateCoordinator.RebuildPipeline() {
            @Override
            public GraphProjection rebuild(final AcceptedBatch batch, final GraphProjection previous) {
                return nonEmptyProjection(batch.generation());
            }
        }, unusedBatcher(edt), loop, edt);
        List<CanvasState> states = new ArrayList<CanvasState>();
        try {
            coordinator.addCanvasStateListener(states::add);
            coordinator.acceptBatch(batch(12L));
            dispatcher.runAll();

            assertThat(coordinator.currentState().status()).isEqualTo(OperationalStatus.FAILED);
            coordinator.restartLayout();
            dispatcher.runAll();

            assertThat(coordinator.currentState().status()).isEqualTo(OperationalStatus.IDLE);
            assertThat(states).extracting(CanvasState::status)
                .containsExactly(OperationalStatus.FAILED, OperationalStatus.IDLE);
            assertThat(stepper.restartCount()).isEqualTo(2);
            assertThat(stepper.submitCount()).isEqualTo(2);
            assertThat(stepper.stepCount()).isZero();
        }
        finally {
            edt.execute(coordinator::close);
            dispatcher.runAll();
        }
    }

    @Test
    public void retainsTheCurrentProjectionGenerationWhenRebuildFails() {
        ImmediateEdt edt = new ImmediateEdt();
        BlockingStepper stepper = new BlockingStepper();
        GraphUpdateCoordinator coordinator = coordinator(new GraphUpdateCoordinator.RebuildPipeline() {
            @Override
            public GraphProjection rebuild(final AcceptedBatch batch, final GraphProjection previous) {
                if (batch.generation() == 2L) {
                    throw new IllegalStateException("rebuild failure");
                }
                return emptyProjection(batch.generation());
            }
        }, unusedBatcher(edt), new LayoutSettleLoop(WORKSPACE, stepper, new GraphGeometryEngine(), edt), edt);
        try {
            coordinator.acceptBatch(batch(1L));
            coordinator.acceptBatch(batch(2L));

            assertThat(coordinator.currentProjection().generation()).isEqualTo(1L);
            assertThat(coordinator.currentState().generation()).isEqualTo(1L);
            assertThat(coordinator.currentState().status()).isEqualTo(OperationalStatus.FAILED);
        }
        finally {
            coordinator.close();
        }
    }

    @Test
    public void publishesEmptyForAnAcceptedEmptyProjection() {
        ImmediateEdt edt = new ImmediateEdt();
        CompletableFuture<CanvasState> published = new CompletableFuture<CanvasState>();
        GraphUpdateCoordinator coordinator = coordinator(new TestPipeline(), unusedBatcher(edt),
            new LayoutSettleLoop(WORKSPACE, new ImmediateStepper(), new GraphGeometryEngine(), edt), edt);
        try {
            coordinator.addCanvasStateListener(published::complete);
            coordinator.acceptBatch(batch(3L));

            CanvasState state = await(published);
            assertThat(state.generation()).isEqualTo(3L);
            assertThat(state.status()).isEqualTo(OperationalStatus.EMPTY);
        }
        finally {
            coordinator.close();
        }
    }

    @Test
    public void retainsFailedStateAndSuppressesDelayedCanvasFromAnOlderAcceptedGeneration() {
        QueuedEdt edt = new QueuedEdt();
        HeldStepper stepper = new HeldStepper();
        List<CanvasState> callbacks = new ArrayList<CanvasState>();
        GraphUpdateCoordinator coordinator = coordinator(new GraphUpdateCoordinator.RebuildPipeline() {
            @Override
            public GraphProjection rebuild(final AcceptedBatch batch, final GraphProjection previous) {
                if (batch.generation() == 2L) {
                    throw new IllegalStateException("newer rebuild failure");
                }
                return emptyProjection(batch.generation());
            }
        }, unusedBatcher(edt), new LayoutSettleLoop(WORKSPACE, stepper, new GraphGeometryEngine(), edt), edt);
        try {
            coordinator.addCanvasStateListener(callbacks::add);
            coordinator.acceptBatch(batch(1L));
            stepper.awaitSubmission();

            coordinator.acceptBatch(batch(2L));
            edt.runQueued();
            CanvasState failed = coordinator.currentState();
            assertThat(failed.status()).isEqualTo(OperationalStatus.FAILED);
            assertThat(failed.generation()).isEqualTo(1L);
            assertThat(failed.projection()).isSameAs(coordinator.currentProjection());
            callbacks.clear();

            stepper.release(1L);
            edt.awaitQueuedTask();
            edt.runQueued();

            assertThat(coordinator.currentState()).isSameAs(failed);
            assertThat(callbacks).isEmpty();
        }
        finally {
            stepper.release(1L);
            coordinator.close();
        }
    }

    @Test
    public void stopsRemainingProjectionObserversWhenANewerGenerationIsAcceptedDuringDelivery() {
        ImmediateEdt edt = new ImmediateEdt();
        HeldStepper stepper = new HeldStepper();
        List<String> callbacks = new ArrayList<String>();
        GraphUpdateCoordinator coordinator = coordinator(new GraphUpdateCoordinator.RebuildPipeline() {
            @Override
            public GraphProjection rebuild(final AcceptedBatch batch, final GraphProjection previous) {
                if (batch.generation() == 2L) {
                    throw new IllegalStateException("newer rebuild failure");
                }
                return emptyProjection(batch.generation());
            }
        }, unusedBatcher(edt), new LayoutSettleLoop(WORKSPACE, stepper, new GraphGeometryEngine(), edt), edt);
        try {
            coordinator.addProjectionListener(projection -> {
                callbacks.add("first-" + projection.generation());
                coordinator.acceptBatch(batch(2L));
            });
            coordinator.addProjectionListener(projection -> callbacks.add("second-" + projection.generation()));

            coordinator.acceptBatch(batch(1L));

            assertThat(callbacks).containsExactly("first-1");
        }
        finally {
            coordinator.close();
        }
    }

    @Test
    public void stopsRemainingCanvasObserversWhenANewerGenerationIsAcceptedDuringDelivery() throws Exception {
        AtomicBoolean firstCanvasListenerStarted = new AtomicBoolean();
        CountDownLatch firstCanvasPublicationFinished = new CountDownLatch(1);
        ImmediateEdt edt = new ImmediateEdt(() -> {
            if (firstCanvasListenerStarted.get()) {
                firstCanvasPublicationFinished.countDown();
            }
        });
        CountDownLatch firstCanvasListenerEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstCanvasListener = new CountDownLatch(1);
        CountDownLatch secondGenerationRebuildEntered = new CountDownLatch(1);
        CountDownLatch releaseSecondGenerationRebuild = new CountDownLatch(1);
        AtomicReference<Throwable> firstBatchFailure = new AtomicReference<Throwable>();
        AtomicReference<Throwable> secondBatchFailure = new AtomicReference<Throwable>();
        List<String> callbacks = Collections.synchronizedList(new ArrayList<String>());
        GraphUpdateCoordinator coordinator = coordinator(new GraphUpdateCoordinator.RebuildPipeline() {
            @Override
            public GraphProjection rebuild(final AcceptedBatch batch, final GraphProjection previous) {
                if (batch.generation() == 2L) {
                    secondGenerationRebuildEntered.countDown();
                    awaitLatch(releaseSecondGenerationRebuild);
                }
                return emptyProjection(batch.generation());
            }
        }, unusedBatcher(edt), new LayoutSettleLoop(WORKSPACE, new ImmediateStepper(), new GraphGeometryEngine(), edt), edt);
        Thread firstBatch = new Thread(() -> {
            try {
                coordinator.acceptBatch(batch(1L));
            }
            catch (Throwable failure) {
                firstBatchFailure.set(failure);
            }
        }, "graph-update-coordinator-first-canvas-batch");
        Thread secondBatch = new Thread(() -> {
            try {
                coordinator.acceptBatch(batch(2L));
            }
            catch (Throwable failure) {
                secondBatchFailure.set(failure);
            }
        }, "graph-update-coordinator-second-canvas-batch");
        try {
            coordinator.addCanvasStateListener(state -> {
                callbacks.add("first-" + state.generation());
                if (state.generation() == 1L) {
                    firstCanvasListenerStarted.set(true);
                    firstCanvasListenerEntered.countDown();
                    awaitLatch(releaseFirstCanvasListener);
                }
            });
            coordinator.addCanvasStateListener(state -> callbacks.add("second-" + state.generation()));

            firstBatch.start();
            assertThat(firstCanvasListenerEntered.await(5L, TimeUnit.SECONDS)).isTrue();
            GraphProjection firstProjection = coordinator.currentProjection();
            CanvasState firstState = coordinator.currentState();

            secondBatch.start();
            assertThat(secondGenerationRebuildEntered.await(5L, TimeUnit.SECONDS)).isTrue();
            assertThat(secondBatch.isAlive()).isTrue();
            assertThat(coordinator.currentProjection()).isSameAs(firstProjection);
            assertThat(coordinator.currentState()).isSameAs(firstState);

            releaseFirstCanvasListener.countDown();
            assertThat(firstCanvasPublicationFinished.await(5L, TimeUnit.SECONDS)).isTrue();
            firstBatch.join(5_000L);
            assertThat(firstBatch.isAlive()).isFalse();
            assertThat(firstBatchFailure.get()).isNull();
            assertThat(callbacks).containsExactly("first-1");
        }
        finally {
            releaseFirstCanvasListener.countDown();
            releaseSecondGenerationRebuild.countDown();
            firstBatch.join(5_000L);
            secondBatch.join(5_000L);
            coordinator.close();
        }
        assertThat(secondBatchFailure.get()).isNull();
    }

    @Test
    public void suppressesQueuedProjectionObserversAfterNewerGenerationIsAccepted() {
        QueuedEdt edt = new QueuedEdt();
        HeldStepper stepper = new HeldStepper();
        List<Long> callbacks = new ArrayList<Long>();
        GraphUpdateCoordinator coordinator = coordinator(new GraphUpdateCoordinator.RebuildPipeline() {
            @Override
            public GraphProjection rebuild(final AcceptedBatch batch, final GraphProjection previous) {
                if (batch.generation() == 2L) {
                    throw new IllegalStateException("newer rebuild failure");
                }
                return emptyProjection(batch.generation());
            }
        }, unusedBatcher(edt), new LayoutSettleLoop(WORKSPACE, stepper, new GraphGeometryEngine(), edt), edt);
        try {
            coordinator.addProjectionListener(projection -> callbacks.add(projection.generation()));
            coordinator.acceptBatch(batch(1L));
            stepper.awaitSubmission();

            coordinator.acceptBatch(batch(2L));
            edt.runQueued();

            assertThat(callbacks).isEmpty();
        }
        finally {
            coordinator.close();
        }
    }

    @Test
    public void ignoresLowerAcceptedGenerationsForProjectionAndCanvasState() {
        ImmediateEdt edt = new ImmediateEdt();
        CompletableFuture<CanvasState> published = new CompletableFuture<CanvasState>();
        GraphUpdateCoordinator coordinator = coordinator(new TestPipeline(), unusedBatcher(edt),
            new LayoutSettleLoop(WORKSPACE, new ImmediateStepper(), new GraphGeometryEngine(), edt), edt);
        try {
            coordinator.addCanvasStateListener(published::complete);
            coordinator.acceptBatch(batch(9L));
            CanvasState higher = await(published);

            coordinator.acceptBatch(batch(8L));

            assertThat(coordinator.currentProjection().generation()).isEqualTo(9L);
            assertThat(coordinator.currentState()).isSameAs(higher);
            assertThat(coordinator.currentState().generation()).isEqualTo(9L);
        }
        finally {
            coordinator.close();
        }
    }

    @Test
    public void resetsAnIdleRunWithANewCurrentProjectionSubmission() {
        ImmediateEdt edt = new ImmediateEdt();
        RecordingStepper stepper = new RecordingStepper();
        CompletableFuture<CanvasState> firstPublication = new CompletableFuture<CanvasState>();
        CompletableFuture<CanvasState> resetPublication = new CompletableFuture<CanvasState>();
        GraphUpdateCoordinator coordinator = coordinator(new TestPipeline(), unusedBatcher(edt),
            new LayoutSettleLoop(WORKSPACE, stepper, new GraphGeometryEngine(), edt), edt);
        try {
            coordinator.addCanvasStateListener(state -> {
                if (firstPublication.complete(state)) {
                    return;
                }
                resetPublication.complete(state);
            });
            coordinator.acceptBatch(batch(10L));
            await(firstPublication);

            coordinator.resetLayout();
            CanvasState resetState = await(resetPublication);

            assertThat(resetState.generation()).isEqualTo(10L);
            assertThat(resetState.projection()).isSameAs(coordinator.currentProjection());
            assertThat(stepper.resetCount).isEqualTo(1);
            assertThat(stepper.submitCount).isEqualTo(2);
            assertThat(stepper.submissions.get(1).projection()).isSameAs(coordinator.currentProjection());
        }
        finally {
            coordinator.close();
        }
    }

    @Test
    public void doesNotBlockTheCoordinatorEdtOnPhysicalReset() {
        ThreadAwareEdt edt = new ThreadAwareEdt();
        LayoutSettleLoopShould.ManualLifecycleDispatcher dispatcher =
            new LayoutSettleLoopShould.ManualLifecycleDispatcher();
        LayoutSettleLoopShould.RecordingStepper stepper =
            new LayoutSettleLoopShould.RecordingStepper(dispatcher);
        CompletableFuture<CanvasState> firstPublication = new CompletableFuture<CanvasState>();
        CompletableFuture<CanvasState> resetPublication = new CompletableFuture<CanvasState>();
        GraphUpdateCoordinator coordinator = coordinator(new TestPipeline(), unusedBatcher(edt),
            new LayoutSettleLoop(WORKSPACE, stepper, new GraphGeometryEngine(), edt, dispatcher), edt);
        CompletableFuture<Void> physicalReset = null;

        try {
            coordinator.addCanvasStateListener(state -> {
                if (firstPublication.complete(state)) {
                    return;
                }
                resetPublication.complete(state);
            });
            coordinator.acceptBatch(batch(10L));
            dispatcher.runAll();
            await(firstPublication);

            stepper.blockReset();
            CompletableFuture<Void> edtReturned = new CompletableFuture<Void>();
            CompletableFuture<Void> resetCall = CompletableFuture.runAsync(() -> edt.runOnEdt(() -> {
                coordinator.resetLayout();
                edtReturned.complete(null);
            }));
            await(edtReturned);
            assertThat(stepper.resetEntered().isDone()).isFalse();

            physicalReset = LayoutSettleLoopShould.runBlockedNext(dispatcher);
            await(stepper.resetEntered());
            assertThat(edtReturned.isDone()).isTrue();
            stepper.releaseReset();
            await(physicalReset);
            await(resetCall);
            dispatcher.runAll();

            CanvasState resetState = await(resetPublication);
            assertThat(resetState.generation()).isEqualTo(10L);
            assertThat(resetState.projection()).isSameAs(coordinator.currentProjection());
            assertThat(stepper.resetCount()).isEqualTo(1);
            assertThat(stepper.submitCount()).isEqualTo(2);
        }
        finally {
            stepper.releaseReset();
            if (physicalReset != null) {
                await(physicalReset);
            }
            stepper.blockClose();
            int closeCommand = dispatcher.enqueueCount() + 1;
            CompletableFuture<Void> closeCall = CompletableFuture.runAsync(coordinator::close);
            await(dispatcher.enqueuedAt(closeCommand));
            dispatcher.runUntilExecuted(closeCommand - 1);
            CompletableFuture<Void> physicalClose = LayoutSettleLoopShould.runBlockedNext(dispatcher);
            await(stepper.closeEntered());
            stepper.releaseClose();
            await(physicalClose);
            await(closeCall);
        }
    }

    @Test
    public void continuesOrderedObservationAfterTheFirstObserverThrows() {
        ImmediateEdt edt = new ImmediateEdt();
        List<String> projectionCallbacks = new ArrayList<String>();
        List<String> canvasCallbacks = Collections.synchronizedList(new ArrayList<String>());
        CompletableFuture<Void> secondCanvasCallback = new CompletableFuture<Void>();
        GraphUpdateCoordinator coordinator = coordinator(new TestPipeline(), unusedBatcher(edt),
            new LayoutSettleLoop(WORKSPACE, new ImmediateStepper(), new GraphGeometryEngine(), edt), edt);
        try {
            coordinator.addProjectionListener(projection -> {
                projectionCallbacks.add("first");
                throw new IllegalStateException("first projection observer");
            });
            coordinator.addProjectionListener(projection -> projectionCallbacks.add("second"));
            coordinator.addCanvasStateListener(state -> {
                canvasCallbacks.add("first");
                throw new IllegalStateException("first canvas observer");
            });
            coordinator.addCanvasStateListener(state -> {
                canvasCallbacks.add("second");
                secondCanvasCallback.complete(null);
            });

            coordinator.acceptBatch(batch(11L));

            assertThat(projectionCallbacks).containsExactly("first", "second");
            await(secondCanvasCallback);
            assertThat(canvasCallbacks).containsExactly("first", "second");
        }
        finally {
            coordinator.close();
        }
    }

    @Test
    public void closesFromTheEdtWithoutWaitingForABlockedAcceptedBatch() throws Exception {
        ThreadAwareEdt edt = new ThreadAwareEdt();
        TestScheduler scheduler = new TestScheduler();
        CountDownLatch rebuildStarted = new CountDownLatch(1);
        CountDownLatch releaseRebuild = new CountDownLatch(1);
        CountDownLatch closeReturned = new CountDownLatch(1);
        AtomicReference<Throwable> closeFailure = new AtomicReference<Throwable>();
        final GraphUpdateCoordinator[] holder = new GraphUpdateCoordinator[1];
        ProjectionBatcher batcher = new ProjectionBatcher(edt, scheduler, () -> 40L, batch -> {
            holder[0].acceptBatch(batch);
        });
        GraphUpdateCoordinator coordinator = coordinator(new GraphUpdateCoordinator.RebuildPipeline() {
            @Override
            public GraphProjection rebuild(final AcceptedBatch batch, final GraphProjection previous) {
                rebuildStarted.countDown();
                try {
                    releaseRebuild.await();
                }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(interrupted);
                }
                return emptyProjection(batch.generation());
            }
        }, batcher, new LayoutSettleLoop(WORKSPACE, new ImmediateStepper(), new GraphGeometryEngine(), edt), edt);
        holder[0] = coordinator;
        List<String> callbacks = Collections.synchronizedList(new ArrayList<String>());
        coordinator.addProjectionListener(projection -> callbacks.add("projection"));
        coordinator.addCanvasStateListener(state -> callbacks.add("canvas"));
        coordinator.requestRebuild(ChangeKind.TEXT);

        Thread callbackThread = new Thread(() -> scheduler.runAllIncludingCancelled(),
            "graph-update-coordinator-accepted-batch");
        Thread closeThread = new Thread(() -> edt.runOnEdt(() -> {
            try {
                coordinator.close();
            }
            catch (Throwable failure) {
                closeFailure.set(failure);
            }
            finally {
                closeReturned.countDown();
            }
        }), "graph-update-coordinator-edt-close");
        try {
            callbackThread.start();
            assertThat(rebuildStarted.await(5L, TimeUnit.SECONDS)).isTrue();
            closeThread.start();
            assertThat(closeReturned.await(1L, TimeUnit.SECONDS)).isTrue();

            releaseRebuild.countDown();
            callbackThread.join(5_000L);
            closeThread.join(5_000L);
            assertThat(callbackThread.isAlive()).isFalse();
            assertThat(closeThread.isAlive()).isFalse();
            assertThat(closeFailure.get()).isNull();
            assertThat(callbacks).isEmpty();
        }
        finally {
            releaseRebuild.countDown();
            callbackThread.join(5_000L);
            closeThread.join(5_000L);
            coordinator.close();
        }
    }

    private static GraphUpdateCoordinator coordinator(final GraphUpdateCoordinator.RebuildPipeline pipeline,
            final ProjectionBatcher batcher, final LayoutSettleLoop loop, final EdtExecutor edt) {
        GraphWorkspaceStore store = mock(GraphWorkspaceStore.class);
        MapLeaseManager leaseManager = mock(MapLeaseManager.class);
        when(store.addListener(any(WorkspaceStoreListener.class))).thenReturn(new ListenerRegistrationStub());
        when(leaseManager.addListener(any(MapAdapterListener.class))).thenReturn(new ListenerRegistrationStub());
        return new GraphUpdateCoordinator(pipeline, batcher, loop, edt, store, leaseManager);
    }

    private static SourceFixture sourceCoordinator() {
        TestEdt edt = new TestEdt();
        TestScheduler scheduler = new TestScheduler();
        final GraphUpdateCoordinator[] holder = new GraphUpdateCoordinator[1];
        ProjectionBatcher batcher = new ProjectionBatcher(edt, scheduler, () -> 50L, batch -> {
            holder[0].acceptBatch(batch);
        });
        GraphWorkspaceStore store = mock(GraphWorkspaceStore.class);
        MapLeaseManager leaseManager = mock(MapLeaseManager.class);
        ListenerRegistrationStub storeRegistration = new ListenerRegistrationStub();
        ListenerRegistrationStub adapterRegistration = new ListenerRegistrationStub();
        AtomicReference<WorkspaceStoreListener> storeListener = new AtomicReference<WorkspaceStoreListener>();
        AtomicReference<MapAdapterListener> adapterListener = new AtomicReference<MapAdapterListener>();
        when(store.addListener(any(WorkspaceStoreListener.class))).thenAnswer(invocation -> {
            storeListener.set(invocation.getArgument(0));
            return storeRegistration;
        });
        when(leaseManager.addListener(any(MapAdapterListener.class))).thenAnswer(invocation -> {
            adapterListener.set(invocation.getArgument(0));
            return adapterRegistration;
        });
        GraphUpdateCoordinator coordinator = sourceCoordinator(new TestPipeline(), batcher,
            new LayoutSettleLoop(WORKSPACE, new ImmediateStepper(), new GraphGeometryEngine(), edt), edt,
            store, leaseManager);
        holder[0] = coordinator;
        return new SourceFixture(coordinator, batcher, edt, storeListener, adapterListener, storeRegistration,
            adapterRegistration);
    }

    private static GraphUpdateCoordinator sourceCoordinator(final GraphUpdateCoordinator.RebuildPipeline pipeline,
            final ProjectionBatcher batcher, final LayoutSettleLoop loop, final EdtExecutor edt,
            final GraphWorkspaceStore store, final MapLeaseManager leaseManager) {
        return new GraphUpdateCoordinator(pipeline, batcher, loop, edt, store, leaseManager);
    }

    private static ProjectionBatcher unusedBatcher(final EdtExecutor edt) {
        return new ProjectionBatcher(edt, new TestScheduler(), () -> 0L, batch -> { });
    }

    private static AcceptedBatch batch(final long generation) {
        return new AcceptedBatch(generation, generation, EnumSet.of(ChangeKind.STRUCTURE));
    }

    private static GraphProjection emptyProjection(final long generation) {
        return GraphProjection.projected(generation, Collections.emptyList(), Collections.emptyList(),
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    private static GraphProjection nonEmptyProjection(final long generation) {
        ProjectedNodeKey key = ProjectedNodeKey.of(SourceNodeKey.persisted(
            NodeReference.of(MAP, PersistedNodeId.of("node-" + generation))));
        ProjectedNode node = ProjectedNode.of(key, SafeNodeLabel.of("Node", "Node"), "Map", false);
        return GraphProjection.projected(generation, Collections.singletonList(node), Collections.emptyList(),
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    private static MapSnapshot snapshot(final MapReferenceId id) {
        PersistedNodeId nodeId = PersistedNodeId.of("root-" + id.value());
        NodeSnapshot root = NodeSnapshot.of(SourceNodeKey.persisted(NodeReference.of(id, nodeId)),
            SafeNodeLabel.of("Node", "Node"), true, false, false, Collections.<NodeSnapshot>emptyList());
        return MapSnapshot.of(id, 1, "Map", root, Collections.singleton(nodeId), false);
    }
    private static WorkspaceStoreEvent workspaceEvent(final WorkspaceStoreEvent.Type type) {
        WorkspaceStoreEvent event = mock(WorkspaceStoreEvent.class);
        when(event.type()).thenReturn(type);
        return event;
    }

    private static <T> T await(final CompletableFuture<T> result) {
        try {
            return result.get(5L, TimeUnit.SECONDS);
        }
        catch (Exception failure) {
            throw new AssertionError("Timed out waiting for coordinator publication", failure);
        }
    }

    private static void awaitLatch(final CountDownLatch latch) {
        try {
            latch.await();
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for synchronization checkpoint", interrupted);
        }
    }

    private static LayoutFrame idleFrame(final long index) {
        return LayoutFrame.withDiagnostics(LayoutFrame.of(index,
            LayoutPositions.of(Collections.emptyMap(), Collections.emptyMap()), false),
            Collections.emptyList(), new PerceptualIdlePolicy.IdleMeasurement(0.0, 0.0, 8, true));
    }

    private static final class SourceFixture {
        private final GraphUpdateCoordinator coordinator;
        private final ProjectionBatcher batcher;
        private final TestEdt edt;
        private final AtomicReference<WorkspaceStoreListener> storeListener;
        private final AtomicReference<MapAdapterListener> adapterListener;
        private final ListenerRegistrationStub storeRegistration;
        private final ListenerRegistrationStub adapterRegistration;

        private SourceFixture(final GraphUpdateCoordinator coordinator, final ProjectionBatcher batcher,
                final TestEdt edt, final AtomicReference<WorkspaceStoreListener> storeListener,
                final AtomicReference<MapAdapterListener> adapterListener,
                final ListenerRegistrationStub storeRegistration,
                final ListenerRegistrationStub adapterRegistration) {
            this.coordinator = coordinator;
            this.batcher = batcher;
            this.edt = edt;
            this.storeListener = storeListener;
            this.adapterListener = adapterListener;
            this.storeRegistration = storeRegistration;
            this.adapterRegistration = adapterRegistration;
        }
    }

    private static final class ListenerRegistrationStub implements ListenerRegistration {
        private int closeCount;

        @Override
        public void close() {
            closeCount++;
        }

        private int closeCount() {
            return closeCount;
        }
    }

    private static final class HeldStepper implements LayoutSettleLoop.FrameStepper {
        private final CountDownLatch submission = new CountDownLatch(1);
        private final CompletableFuture<LayoutFrame> submitted = new CompletableFuture<LayoutFrame>();

        @Override
        public CompletionStage<LayoutFrame> submit(final LayoutRequest request) {
            submission.countDown();
            return submitted;
        }

        @Override
        public CompletionStage<LayoutFrame> step() {
            return CompletableFuture.completedFuture(idleFrame(0L));
        }

        @Override
        public void pause() {
        }

        @Override
        public void restart() {
        }

        @Override
        public LayoutFrame lastValidFrame() {
            return idleFrame(0L);
        }

        @Override
        public void close() {
        }

        private void awaitSubmission() {
            try {
                assertThat(submission.await(5L, TimeUnit.SECONDS)).isTrue();
            }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for layout submission", interrupted);
            }
        }

        private void release(final long generation) {
            submitted.complete(idleFrame(generation));
        }
    }

    private static final class QueuedEdt implements EdtExecutor {
        private final Object monitor = new Object();
        private final Deque<Runnable> queued = new ArrayDeque<Runnable>();
        private final ThreadLocal<Boolean> active = new ThreadLocal<Boolean>();

        @Override
        public <T> T call(final Callable<T> task) {
            if (isEdt()) {
                return callNow(task);
            }
            final Holder<T> result = new Holder<T>();
            runOnEdt(() -> result.value = callNow(task));
            return result.value;
        }

        @Override
        public void execute(final Runnable task) {
            if (isEdt()) {
                task.run();
                return;
            }
            synchronized (monitor) {
                queued.add(task);
                monitor.notifyAll();
            }
        }

        @Override
        public boolean isEdt() {
            return Boolean.TRUE.equals(active.get());
        }

        private void awaitQueuedTask() {
            final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
            synchronized (monitor) {
                while (queued.isEmpty()) {
                    final long remaining = deadline - System.nanoTime();
                    if (remaining <= 0L) {
                        throw new AssertionError("Timed out waiting for queued EDT task");
                    }
                    try {
                        monitor.wait(Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remaining)));
                    }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError("Interrupted while waiting for queued EDT task", interrupted);
                    }
                }
            }
        }

        private void runQueued() {
            while (true) {
                final Runnable task;
                synchronized (monitor) {
                    task = queued.poll();
                }
                if (task == null) {
                    return;
                }
                runOnEdt(task);
            }
        }

        private void runOnEdt(final Runnable task) {
            final Boolean previous = active.get();
            active.set(Boolean.TRUE);
            try {
                task.run();
            }
            finally {
                restore(previous);
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

        private void restore(final Boolean previous) {
            if (previous == null) {
                active.remove();
            }
            else {
                active.set(previous);
            }
        }
    }

    private static final class BlockingStepper implements LayoutSettleLoop.FrameStepper {
        private final CompletableFuture<LayoutFrame> submitted = new CompletableFuture<LayoutFrame>();

        @Override
        public CompletionStage<LayoutFrame> submit(final LayoutRequest request) {
            return submitted;
        }

        @Override
        public CompletionStage<LayoutFrame> step() {
            return CompletableFuture.completedFuture(idleFrame(0L));
        }

        @Override
        public void pause() {
        }

        @Override
        public void restart() {
        }

        @Override
        public LayoutFrame lastValidFrame() {
            return idleFrame(0L);
        }

        @Override
        public void close() {
        }
    }

    private static final class RecordingStepper implements LayoutSettleLoop.FrameStepper {
        private final List<LayoutRequest> submissions = new ArrayList<LayoutRequest>();
        private int submitCount;
        private int resetCount;

        @Override
        public CompletionStage<LayoutFrame> submit(final LayoutRequest request) {
            submitCount++;
            submissions.add(request);
            return CompletableFuture.completedFuture(idleFrame(request.projection().generation()));
        }

        @Override
        public CompletionStage<LayoutFrame> step() {
            return CompletableFuture.completedFuture(idleFrame(0L));
        }

        @Override
        public void pause() {
        }

        @Override
        public void restart() {
        }

        @Override
        public void reset() {
            resetCount++;
        }

        @Override
        public LayoutFrame lastValidFrame() {
            return idleFrame(0L);
        }

        @Override
        public void close() {
        }
    }

    private static final class ImmediateEdt implements EdtExecutor {
        private final ThreadLocal<Boolean> active = new ThreadLocal<Boolean>();
        private final Runnable afterExecution;

        private ImmediateEdt() {
            this(null);
        }

        private ImmediateEdt(final Runnable afterExecution) {
            this.afterExecution = afterExecution;
        }

        @Override
        public <T> T call(final Callable<T> task) {
            final Boolean previous = active.get();
            active.set(Boolean.TRUE);
            try {
                return task.call();
            }
            catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
            finally {
                restore(previous);
                if (afterExecution != null) {
                    afterExecution.run();
                }
            }
        }

        @Override
        public void execute(final Runnable task) {
            final Boolean previous = active.get();
            active.set(Boolean.TRUE);
            try {
                task.run();
            }
            finally {
                restore(previous);
                if (afterExecution != null) {
                    afterExecution.run();
                }
            }
        }

        @Override
        public boolean isEdt() {
            return Boolean.TRUE.equals(active.get());
        }

        private void restore(final Boolean previous) {
            if (previous == null) {
                active.remove();
            }
            else {
                active.set(previous);
            }
        }
    }

    private static final class ThreadAwareEdt implements EdtExecutor {
        private final ThreadLocal<Boolean> active = new ThreadLocal<Boolean>();

        @Override
        public <T> T call(final Callable<T> task) {
            final Boolean previous = active.get();
            active.set(Boolean.TRUE);
            try {
                return task.call();
            }
            catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
            finally {
                restore(previous);
            }
        }

        @Override
        public void execute(final Runnable task) {
            runOnEdt(task);
        }

        @Override
        public boolean isEdt() {
            return Boolean.TRUE.equals(active.get());
        }

        private void runOnEdt(final Runnable task) {
            final Boolean previous = active.get();
            active.set(Boolean.TRUE);
            try {
                task.run();
            }
            finally {
                restore(previous);
            }
        }

        private void restore(final Boolean previous) {
            if (previous == null) {
                active.remove();
            }
            else {
                active.set(previous);
            }
        }
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
