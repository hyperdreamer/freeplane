package org.freeplane.plugin.graph.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.freeplane.plugin.graph.adapter.EdtExecutor;
import org.freeplane.plugin.graph.adapter.MapAdapterEvent;
import org.freeplane.plugin.graph.adapter.MapAdapterListener;
import org.freeplane.plugin.graph.adapter.MapLease;
import org.freeplane.plugin.graph.adapter.MapLeaseManager;
import org.freeplane.plugin.graph.adapter.MapOperationalState;
import org.freeplane.plugin.graph.adapter.MapSnapshotFactory;
import org.freeplane.plugin.graph.projection.input.MapAvailability;
import org.freeplane.plugin.graph.projection.input.MapSnapshot;
import org.freeplane.plugin.graph.projection.input.NodeSnapshot;
import org.freeplane.plugin.graph.projection.input.ProjectionInput;
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
import org.junit.After;
import org.junit.Test;

public class WorkspaceMapCoordinatorShould {
    private static final String MAP_COLOR = "#4E79A7";
    private static final WorkspaceId WORKSPACE_ID =
        WorkspaceId.of("00000000-0000-0000-0000-000000000100");

    private final List<WorkspaceMapCoordinator> coordinators = new ArrayList<WorkspaceMapCoordinator>();

    @After
    public void closeCoordinators() {
        for (WorkspaceMapCoordinator coordinator : coordinators) {
            coordinator.close();
        }
    }

    @Test
    public void captureReadsWorkspaceAndSnapshotsOnlyThroughTheEdt() {
        MapReferenceId id = id(1);
        MapReference registration = registration(id, 1L, true);
        WorkspaceDocument document = workspace(registration);
        TestEdt edt = new TestEdt();
        GraphWorkspaceStore store = mock(GraphWorkspaceStore.class);
        MapLeaseManager leaseManager = mock(MapLeaseManager.class);
        MapSnapshotFactory snapshots = mock(MapSnapshotFactory.class);
        FakeLease lease = new FakeLease(id, MapOperationalState.AVAILABLE);
        MapSnapshot snapshot = snapshot(id, 1);
        stubStoreAndListeners(store, leaseManager, document, new ListenerRegistrationStub(),
            new ListenerRegistrationStub());
        when(snapshots.snapshot(any(MapLease.class))).thenAnswer(invocation -> {
            assertThat(edt.isEdt()).isTrue();
            assertThat((MapLease) invocation.getArgument(0)).isSameAs(lease);
            return snapshot;
        });
        WorkspaceMapCoordinator coordinator = coordinator(document, edt, store, leaseManager, snapshots,
            mapOf(id, CompletableFuture.completedFuture(lease)));

        AcceptedBatch batch = new AcceptedBatch(7L, 11L, EnumSet.of(ChangeKind.TEXT));
        ProjectionInput input = coordinator.capture(batch);

        assertThat(input.generation()).isEqualTo(7L);
        assertThat(input.workspace()).isSameAs(document);
        assertThat(input.maps()).containsExactly(snapshot);
        assertThat(input.availability().get(id)).isEqualTo(MapAvailability.AVAILABLE);
        assertThat(edt.callCount()).isGreaterThan(0);
    }

    @Test
    public void captureIncludesEveryRegistrationAndMapsOperationalStates() {
        List<MapReference> registrations = new ArrayList<MapReference>();
        Map<MapReferenceId, FakeLease> leases = new LinkedHashMap<MapReferenceId, FakeLease>();
        Map<MapReferenceId, CompletionStage<MapLease>> acquisitions =
            new LinkedHashMap<MapReferenceId, CompletionStage<MapLease>>();
        Map<MapReferenceId, MapSnapshot> snapshotsById = new LinkedHashMap<MapReferenceId, MapSnapshot>();
        MapOperationalState[] states = MapOperationalState.values();
        for (int index = 0; index < states.length; index++) {
            MapReferenceId id = id(index + 1);
            registrations.add(registration(id, index + 1L, true));
            FakeLease lease = new FakeLease(id, states[index]);
            leases.put(id, lease);
            acquisitions.put(id, CompletableFuture.completedFuture(lease));
            if (states[index] == MapOperationalState.AVAILABLE) {
                snapshotsById.put(id, snapshot(id, index + 1));
            }
        }
        WorkspaceDocument document = workspace(registrations.toArray(new MapReference[registrations.size()]));
        TestEdt edt = new TestEdt();
        GraphWorkspaceStore store = mock(GraphWorkspaceStore.class);
        MapLeaseManager leaseManager = mock(MapLeaseManager.class);
        MapSnapshotFactory snapshotFactory = mock(MapSnapshotFactory.class);
        stubStoreAndListeners(store, leaseManager, document, new ListenerRegistrationStub(),
            new ListenerRegistrationStub());
        when(snapshotFactory.snapshot(any(MapLease.class))).thenAnswer(invocation -> {
            FakeLease lease = (FakeLease) invocation.getArgument(0);
            return snapshotsById.get(lease.mapReferenceId());
        });
        WorkspaceMapCoordinator coordinator = coordinator(document, edt, store, leaseManager, snapshotFactory,
            acquisitions);

        ProjectionInput input = coordinator.capture(batch(8L));

        assertThat(input.workspace()).isSameAs(document);
        assertThat(input.availability()).hasSize(states.length);
        for (int index = 0; index < states.length; index++) {
            MapReferenceId id = id(index + 1);
            assertThat(input.availability().get(id)).isEqualTo(availability(states[index]));
        }
        assertThat(input.maps()).containsExactly(snapshotsById.get(id(2)));
        assertThat(input.maps().get(0).mapReferenceId()).isEqualTo(id(2));
    }

    @Test
    public void captureDoesNotSnapshotInactiveOrUnavailableMaps() {
        MapReferenceId unavailableId = id(1);
        MapReferenceId inactiveId = id(2);
        MapReferenceId availableId = id(3);
        WorkspaceDocument document = workspace(
            registration(unavailableId, 1L, true),
            registration(inactiveId, 2L, false),
            registration(availableId, 3L, true));
        FakeLease unavailable = new FakeLease(unavailableId, MapOperationalState.UNREADABLE);
        FakeLease available = new FakeLease(availableId, MapOperationalState.AVAILABLE);
        TestEdt edt = new TestEdt();
        GraphWorkspaceStore store = mock(GraphWorkspaceStore.class);
        MapLeaseManager leaseManager = mock(MapLeaseManager.class);
        MapSnapshotFactory snapshotFactory = mock(MapSnapshotFactory.class);
        stubStoreAndListeners(store, leaseManager, document, new ListenerRegistrationStub(),
            new ListenerRegistrationStub());
        when(snapshotFactory.snapshot(same(available))).thenReturn(snapshot(availableId, 3));
        WorkspaceMapCoordinator coordinator = coordinator(document, edt, store, leaseManager, snapshotFactory,
            mapOf(unavailableId, CompletableFuture.completedFuture(unavailable),
                availableId, CompletableFuture.completedFuture(available)));

        ProjectionInput input = coordinator.capture(batch(9L));

        assertThat(input.maps()).extracting(MapSnapshot::mapReferenceId).containsExactly(availableId);
        assertThat(input.availability().get(unavailableId)).isEqualTo(MapAvailability.UNREADABLE);
        assertThat(input.availability().get(inactiveId)).isEqualTo(MapAvailability.INACTIVE);
        assertThat(input.availability().get(availableId)).isEqualTo(MapAvailability.AVAILABLE);
        verify(snapshotFactory, never()).snapshot(same(unavailable));
    }

    @Test
    public void closeReleasesListenersAndLeasesWithoutFurtherCallbacks() {
        MapReferenceId availableId = id(1);
        MapReferenceId pendingId = id(2);
        WorkspaceDocument document = workspace(
            registration(availableId, 1L, true), registration(pendingId, 2L, true));
        FakeLease available = new FakeLease(availableId, MapOperationalState.AVAILABLE);
        FakeLease pendingLease = new FakeLease(pendingId, MapOperationalState.AVAILABLE);
        CompletableFuture<MapLease> pending = new CompletableFuture<MapLease>();
        TestEdt edt = new TestEdt();
        GraphWorkspaceStore store = mock(GraphWorkspaceStore.class);
        MapLeaseManager leaseManager = mock(MapLeaseManager.class);
        MapSnapshotFactory snapshotFactory = mock(MapSnapshotFactory.class);
        ListenerRegistrationStub storeRegistration = new ListenerRegistrationStub();
        ListenerRegistrationStub adapterRegistration = new ListenerRegistrationStub();
        AtomicReference<WorkspaceStoreListener> storeListener = new AtomicReference<WorkspaceStoreListener>();
        AtomicReference<MapAdapterListener> adapterListener = new AtomicReference<MapAdapterListener>();
        when(store.currentDocument()).thenReturn(document);
        when(store.addListener(any(WorkspaceStoreListener.class))).thenAnswer(invocation -> {
            storeListener.set(invocation.getArgument(0));
            return storeRegistration;
        });
        when(leaseManager.addListener(any(MapAdapterListener.class))).thenAnswer(invocation -> {
            adapterListener.set(invocation.getArgument(0));
            return adapterRegistration;
        });
        Map<MapReferenceId, CompletionStage<MapLease>> acquisitions = mapOf(
            availableId, CompletableFuture.completedFuture(available), pendingId, pending);
        WorkspaceMapCoordinator coordinator = coordinator(document, edt, store, leaseManager, snapshotFactory,
            acquisitions);

        coordinator.close();
        assertThat(storeRegistration.closeCount()).isEqualTo(1);
        assertThat(adapterRegistration.closeCount()).isEqualTo(1);
        assertThat(available.closeCount()).isEqualTo(1);

        pending.complete(pendingLease);
        storeListener.get().onWorkspaceStoreEvent(mockDocumentChangedEvent(document));
        adapterListener.get().onMapAdapterEvent(new MapAdapterEvent(availableId, MapOperationalState.RELOAD_REQUIRED));
        edt.runQueued();
        assertThat(pendingLease.closeCount()).isEqualTo(1);
        assertThatThrownBy(() -> coordinator.capture(batch(10L)))
            .isInstanceOf(IllegalStateException.class);
        verify(snapshotFactory, never()).snapshot(any(MapLease.class));
    }

    private static WorkspaceMapCoordinator coordinator(WorkspaceDocument document, TestEdt edt,
            GraphWorkspaceStore store, MapLeaseManager leaseManager, MapSnapshotFactory snapshots,
            Map<MapReferenceId, CompletionStage<MapLease>> acquisitions) {
        WorkspaceMapCoordinator coordinator = new WorkspaceMapCoordinator(snapshots, leaseManager, store, edt,
            reference -> acquisitions.get(reference.id()));
        return coordinator;
    }

    private static void stubStoreAndListeners(GraphWorkspaceStore store, MapLeaseManager leaseManager,
            WorkspaceDocument document, ListenerRegistrationStub storeRegistration,
            ListenerRegistrationStub adapterRegistration) {
        when(store.currentDocument()).thenReturn(document);
        when(store.addListener(any(WorkspaceStoreListener.class))).thenReturn(storeRegistration);
        when(leaseManager.addListener(any(MapAdapterListener.class))).thenReturn(adapterRegistration);
    }

    private static WorkspaceStoreEvent mockDocumentChangedEvent(WorkspaceDocument document) {
        WorkspaceStoreEvent event = mock(WorkspaceStoreEvent.class);
        when(event.type()).thenReturn(WorkspaceStoreEvent.Type.DOCUMENT_CHANGED);
        when(event.document()).thenReturn(document);
        return event;
    }

    private static AcceptedBatch batch(long generation) {
        return new AcceptedBatch(generation, generation, EnumSet.of(ChangeKind.TEXT));
    }

    private static WorkspaceDocument workspace(MapReference... registrations) {
        return WorkspaceDocument.createVersion1(WORKSPACE_ID).toBuilder()
            .maps(Arrays.asList(registrations)).build();
    }

    private static MapReference registration(MapReferenceId id, long sequence, boolean active) {
        return MapReference.of(id, sequence, URI.create("maps/" + id.value() + ".mm"), active, MAP_COLOR,
            Collections.<UnknownXml>emptyList());
    }

    private static MapSnapshot snapshot(MapReferenceId id, int workspaceOrder) {
        PersistedNodeId nodeId = PersistedNodeId.of("root-" + id.value());
        NodeSnapshot root = NodeSnapshot.of(SourceNodeKey.persisted(NodeReference.of(id, nodeId)),
            SafeNodeLabel.of("Map", "Map"), true, false, false, Collections.<NodeSnapshot>emptyList());
        return MapSnapshot.of(id, workspaceOrder, "Map " + id.value(), root,
            Collections.singleton(nodeId), false);
    }

    private static MapReferenceId id(int value) {
        return MapReferenceId.of(String.format("00000000-0000-0000-0000-000000000%03d", value));
    }

    private static MapAvailability availability(MapOperationalState state) {
        switch (state) {
        case LOADING:
            return MapAvailability.LOADING;
        case AVAILABLE:
            return MapAvailability.AVAILABLE;
        case MISSING:
            return MapAvailability.MISSING;
        case UNREADABLE:
            return MapAvailability.UNREADABLE;
        case PASSWORD_REQUIRED:
            return MapAvailability.PASSWORD_REQUIRED;
        case RELOAD_REQUIRED:
            return MapAvailability.RELOAD_REQUIRED;
        default:
            throw new AssertionError(state);
        }
    }

    private static <K, V> Map<K, V> mapOf(K firstKey, V firstValue, Object... rest) {
        Map<K, V> values = new LinkedHashMap<K, V>();
        values.put(firstKey, firstValue);
        for (int index = 0; index < rest.length; index += 2) {
            @SuppressWarnings("unchecked")
            K key = (K) rest[index];
            @SuppressWarnings("unchecked")
            V value = (V) rest[index + 1];
            values.put(key, value);
        }
        return values;
    }

    private static final class FakeLease implements MapLease {
        private final MapReferenceId id;
        private final MapOperationalState state;
        private int closeCount;

        private FakeLease(MapReferenceId id, MapOperationalState state) {
            this.id = id;
            this.state = state;
        }

        @Override
        public MapReferenceId mapReferenceId() {
            return id;
        }

        @Override
        public MapOperationalState state() {
            return state;
        }

        @Override
        public void close() {
            closeCount++;
        }

        private int closeCount() {
            return closeCount;
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

    private static final class TestEdt implements EdtExecutor {
        private final Queue<Runnable> queued = new ArrayDeque<Runnable>();
        private boolean edt;
        private int callCount;

        @Override
        public <T> T call(Callable<T> task) {
            callCount++;
            if (edt) {
                return callNow(task);
            }
            final AtomicReferenceValue<T> result = new AtomicReferenceValue<T>();
            runOnEdt(() -> result.value = callNow(task));
            return result.value;
        }

        @Override
        public void execute(Runnable task) {
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

        private int callCount() {
            return callCount;
        }

        private void runQueued() {
            while (!queued.isEmpty()) {
                runOnEdt(queued.remove());
            }
        }

        private void runOnEdt(Runnable task) {
            boolean previous = edt;
            edt = true;
            try {
                task.run();
            }
            finally {
                edt = previous;
            }
        }

        private static <T> T callNow(Callable<T> task) {
            try {
                return task.call();
            }
            catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        }
    }

    private static final class AtomicReferenceValue<T> {
        private T value;
    }
}
