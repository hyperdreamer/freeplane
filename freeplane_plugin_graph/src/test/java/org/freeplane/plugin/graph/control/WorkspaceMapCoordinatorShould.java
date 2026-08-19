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
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.freeplane.plugin.graph.workspace.WorkspaceIdentityChange;
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
    public void rebasesLeaseResolutionBeforeInstallingSaveAsReferences() {
        MapReferenceId mapId = id(1);
        MapReference initial = registration(mapId, 1L, true);
        MapReference replacement = MapReference.of(mapId, 1L, URI.create("../maps/replaced.mm"), true,
            MAP_COLOR, Collections.<UnknownXml>emptyList());
        WorkspaceDocument initialDocument = workspace(initial);
        WorkspaceDocument replacementDocument = workspace(replacement);
        FakeLease initialLease = new FakeLease(mapId, MapOperationalState.AVAILABLE);
        FakeLease replacementLease = new FakeLease(mapId, MapOperationalState.AVAILABLE);
        AtomicReference<WorkspaceDocument> currentDocument = new AtomicReference<WorkspaceDocument>(initialDocument);
        AtomicReference<WorkspaceStoreListener> workspaceListener = new AtomicReference<WorkspaceStoreListener>();
        AtomicReference<Boolean> rebased = new AtomicReference<Boolean>(false);
        TestEdt edt = new TestEdt();
        GraphWorkspaceStore store = mock(GraphWorkspaceStore.class);
        MapLeaseManager leaseManager = mock(MapLeaseManager.class);
        MapSnapshotFactory snapshotFactory = mock(MapSnapshotFactory.class);
        when(store.currentDocument()).thenAnswer(invocation -> currentDocument.get());
        when(store.addListener(any(WorkspaceStoreListener.class))).thenAnswer(invocation -> {
            workspaceListener.set(invocation.getArgument(0));
            return new ListenerRegistrationStub();
        });
        when(leaseManager.addListener(any(MapAdapterListener.class))).thenReturn(new ListenerRegistrationStub());
        doAnswer(invocation -> {
            rebased.set(true);
            return null;
        }).when(leaseManager).rebaseWorkspace(any(Path.class));
        Path newWorkspace = java.nio.file.Paths.get("target", "moved.fpg").toAbsolutePath();
        WorkspaceIdentityChange change = mock(WorkspaceIdentityChange.class);
        when(change.newPath()).thenReturn(newWorkspace);

        WorkspaceMapCoordinator coordinator = new WorkspaceMapCoordinator(snapshotFactory, leaseManager, store, edt,
            reference -> {
                if (reference.equals(initial)) {
                    return CompletableFuture.completedFuture(initialLease);
                }
                assertThat(rebased.get()).isTrue();
                return CompletableFuture.completedFuture(replacementLease);
            });
        coordinators.add(coordinator);

        currentDocument.set(replacementDocument);
        WorkspaceStoreEvent event = mock(WorkspaceStoreEvent.class);
        when(event.type()).thenReturn(WorkspaceStoreEvent.Type.IDENTITY_CHANGED);
        when(event.document()).thenReturn(replacementDocument);
        when(event.identityChange()).thenReturn(Optional.of(change));
        workspaceListener.get().onWorkspaceStoreEvent(event);
        edt.runQueued();

        verify(leaseManager).rebaseWorkspace(newWorkspace);
        assertThat(replacementLease.closeCount()).isZero();
    }

    @Test
    public void exposesTheCurrentLeaseWithoutTransferringItsOwnership() {
        MapReferenceId id = id(1);
        MapReference registration = registration(id, 1L, true);
        WorkspaceDocument document = workspace(registration);
        TestEdt edt = new TestEdt();
        GraphWorkspaceStore store = mock(GraphWorkspaceStore.class);
        MapLeaseManager leaseManager = mock(MapLeaseManager.class);
        MapSnapshotFactory snapshots = mock(MapSnapshotFactory.class);
        FakeLease lease = new FakeLease(id, MapOperationalState.AVAILABLE);
        stubStoreAndListeners(store, leaseManager, document, new ListenerRegistrationStub(),
            new ListenerRegistrationStub());
        WorkspaceMapCoordinator coordinator = coordinator(document, edt, store, leaseManager, snapshots,
            mapOf(id, CompletableFuture.completedFuture(lease)));
        coordinators.add(coordinator);

        assertThat(coordinator.find(id)).containsSame(lease);
        assertThat(lease.closeCount()).isZero();
        coordinator.close();
        assertThat(lease.closeCount()).isEqualTo(1);
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

    @Test
    public void captureOrdersMultipleSnapshotsByWorkspaceRegistration() {
        MapReferenceId firstId = id(1);
        MapReferenceId secondId = id(2);
        WorkspaceDocument document = workspace(
            registration(firstId, 1L, true), registration(secondId, 2L, true));
        FakeLease firstLease = new FakeLease(firstId, MapOperationalState.AVAILABLE);
        FakeLease secondLease = new FakeLease(secondId, MapOperationalState.AVAILABLE);
        MapSnapshot firstSnapshot = mock(MapSnapshot.class);
        MapSnapshot secondSnapshot = mock(MapSnapshot.class);
        // Stage the IDs so the factory can return valid snapshots in reverse order while
        // the production identity guard still validates each requested lease.
        when(firstSnapshot.mapReferenceId()).thenReturn(secondId, firstId, firstId);
        when(firstSnapshot.workspaceOrder()).thenReturn(2);
        when(secondSnapshot.mapReferenceId()).thenReturn(firstId, secondId, secondId);
        when(secondSnapshot.workspaceOrder()).thenReturn(1);
        TestEdt edt = new TestEdt();
        GraphWorkspaceStore store = mock(GraphWorkspaceStore.class);
        MapLeaseManager leaseManager = mock(MapLeaseManager.class);
        MapSnapshotFactory snapshotFactory = mock(MapSnapshotFactory.class);
        stubStoreAndListeners(store, leaseManager, document, new ListenerRegistrationStub(),
            new ListenerRegistrationStub());
        when(snapshotFactory.snapshot(same(firstLease))).thenReturn(secondSnapshot);
        when(snapshotFactory.snapshot(same(secondLease))).thenReturn(firstSnapshot);
        WorkspaceMapCoordinator coordinator = coordinator(document, edt, store, leaseManager, snapshotFactory,
            mapOf(firstId, CompletableFuture.completedFuture(firstLease),
                secondId, CompletableFuture.completedFuture(secondLease)));

        ProjectionInput input = coordinator.capture(batch(11L));

        assertThat(input.maps()).containsExactly(firstSnapshot, secondSnapshot);
        assertThat(input.maps()).extracting(MapSnapshot::mapReferenceId).containsExactly(firstId, secondId);
    }

    @Test
    public void captureRetriesSnapshotAfterTransientFailure() {
        MapReferenceId availableId = id(1);
        WorkspaceDocument document = workspace(registration(availableId, 1L, true));
        FakeLease available = new FakeLease(availableId, MapOperationalState.AVAILABLE);
        MapSnapshot recoveredSnapshot = snapshot(availableId, 1);
        AtomicInteger attempts = new AtomicInteger();
        TestEdt edt = new TestEdt();
        GraphWorkspaceStore store = mock(GraphWorkspaceStore.class);
        MapLeaseManager leaseManager = mock(MapLeaseManager.class);
        MapSnapshotFactory snapshotFactory = mock(MapSnapshotFactory.class);
        stubStoreAndListeners(store, leaseManager, document, new ListenerRegistrationStub(),
            new ListenerRegistrationStub());
        when(snapshotFactory.snapshot(same(available))).thenAnswer(invocation -> {
            if (attempts.getAndIncrement() == 0) {
                throw new IllegalStateException("transient snapshot failure");
            }
            return recoveredSnapshot;
        });
        WorkspaceMapCoordinator coordinator = coordinator(document, edt, store, leaseManager, snapshotFactory,
            mapOf(availableId, CompletableFuture.completedFuture(available)));

        ProjectionInput failedCapture = coordinator.capture(batch(12L));
        ProjectionInput recoveredCapture = coordinator.capture(batch(13L));

        assertThat(failedCapture.availability().get(availableId)).isEqualTo(MapAvailability.UNREADABLE);
        assertThat(failedCapture.maps()).isEmpty();
        assertThat(recoveredCapture.availability().get(availableId)).isEqualTo(MapAvailability.AVAILABLE);
        assertThat(recoveredCapture.maps()).containsExactly(recoveredSnapshot);
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    public void adapterEventUpdatesOnlyTheMatchingRegistrationBeforeCapture() {
        MapReferenceId firstId = id(1);
        MapReferenceId secondId = id(2);
        WorkspaceDocument document = workspace(
            registration(firstId, 1L, true), registration(secondId, 2L, true));
        FakeLease firstLease = new FakeLease(firstId, MapOperationalState.AVAILABLE);
        FakeLease secondLease = new FakeLease(secondId, MapOperationalState.AVAILABLE);
        TestEdt edt = new TestEdt();
        GraphWorkspaceStore store = mock(GraphWorkspaceStore.class);
        MapLeaseManager leaseManager = mock(MapLeaseManager.class);
        MapSnapshotFactory snapshotFactory = mock(MapSnapshotFactory.class);
        AtomicReference<MapAdapterListener> adapterListener = new AtomicReference<MapAdapterListener>();
        when(store.currentDocument()).thenReturn(document);
        when(store.addListener(any(WorkspaceStoreListener.class))).thenReturn(new ListenerRegistrationStub());
        when(leaseManager.addListener(any(MapAdapterListener.class))).thenAnswer(invocation -> {
            adapterListener.set(invocation.getArgument(0));
            return new ListenerRegistrationStub();
        });
        when(snapshotFactory.snapshot(same(secondLease))).thenReturn(snapshot(secondId, 2));
        WorkspaceMapCoordinator coordinator = coordinator(document, edt, store, leaseManager, snapshotFactory,
            mapOf(firstId, CompletableFuture.completedFuture(firstLease),
                secondId, CompletableFuture.completedFuture(secondLease)));

        adapterListener.get().onMapAdapterEvent(new MapAdapterEvent(firstId, MapOperationalState.RELOAD_REQUIRED));
        edt.runQueued();
        ProjectionInput input = coordinator.capture(batch(14L));

        assertThat(input.availability().get(firstId)).isEqualTo(MapAvailability.RELOAD_REQUIRED);
        assertThat(input.availability().get(secondId)).isEqualTo(MapAvailability.AVAILABLE);
        assertThat(input.maps()).extracting(MapSnapshot::mapReferenceId).containsExactly(secondId);
        verify(snapshotFactory, never()).snapshot(same(firstLease));
    }

    @Test
    public void documentChangeClosesStaleLeasesIgnoresOldCompletionsAndRetainsUnchangedLease() {
        MapReferenceId retainedId = id(1);
        MapReferenceId changedId = id(2);
        MapReferenceId deactivatedId = id(3);
        MapReference retained = registration(retainedId, 1L, true);
        MapReference initialChanged = registration(changedId, 2L, true);
        MapReference initialDeactivated = registration(deactivatedId, 3L, true);
        WorkspaceDocument initialDocument = workspace(retained, initialChanged, initialDeactivated);
        MapReference replacementChanged = MapReference.of(changedId, 2L, URI.create("maps/replaced.mm"), true,
            MAP_COLOR, Collections.<UnknownXml>emptyList());
        MapReference replacementDeactivated = registration(deactivatedId, 3L, false);
        WorkspaceDocument replacementDocument = workspace(retained, replacementChanged, replacementDeactivated);
        FakeLease retainedLease = new FakeLease(retainedId, MapOperationalState.AVAILABLE);
        FakeLease deactivatedLease = new FakeLease(deactivatedId, MapOperationalState.AVAILABLE);
        FakeLease replacementLease = new FakeLease(changedId, MapOperationalState.AVAILABLE);
        FakeLease staleLease = new FakeLease(changedId, MapOperationalState.AVAILABLE);
        CompletableFuture<MapLease> staleAcquisition = new CompletableFuture<MapLease>();
        AtomicInteger retainedAcquisitions = new AtomicInteger();
        AtomicReference<WorkspaceDocument> currentDocument = new AtomicReference<WorkspaceDocument>(initialDocument);
        AtomicReference<WorkspaceStoreListener> workspaceListener = new AtomicReference<WorkspaceStoreListener>();
        TestEdt edt = new TestEdt();
        GraphWorkspaceStore store = mock(GraphWorkspaceStore.class);
        MapLeaseManager leaseManager = mock(MapLeaseManager.class);
        MapSnapshotFactory snapshotFactory = mock(MapSnapshotFactory.class);
        when(store.currentDocument()).thenAnswer(invocation -> currentDocument.get());
        when(store.addListener(any(WorkspaceStoreListener.class))).thenAnswer(invocation -> {
            workspaceListener.set(invocation.getArgument(0));
            return new ListenerRegistrationStub();
        });
        when(leaseManager.addListener(any(MapAdapterListener.class))).thenReturn(new ListenerRegistrationStub());
        WorkspaceMapCoordinator coordinator = new WorkspaceMapCoordinator(snapshotFactory, leaseManager, store, edt,
            reference -> {
                if (reference.equals(retained)) {
                    retainedAcquisitions.incrementAndGet();
                    return CompletableFuture.completedFuture(retainedLease);
                }
                if (reference.equals(initialChanged)) {
                    return staleAcquisition;
                }
                if (reference.equals(initialDeactivated)) {
                    return CompletableFuture.completedFuture(deactivatedLease);
                }
                if (reference.equals(replacementChanged)) {
                    return CompletableFuture.completedFuture(replacementLease);
                }
                throw new AssertionError("Unexpected registration: " + reference);
            });

        currentDocument.set(replacementDocument);
        workspaceListener.get().onWorkspaceStoreEvent(mockDocumentChangedEvent(replacementDocument));
        edt.runQueued();
        staleAcquisition.complete(staleLease);
        edt.runQueued();

        assertThat(retainedAcquisitions.get()).isEqualTo(1);
        assertThat(retainedLease.closeCount()).isEqualTo(0);
        assertThat(deactivatedLease.closeCount()).isEqualTo(1);
        assertThat(replacementLease.closeCount()).isEqualTo(0);
        assertThat(staleLease.closeCount()).isEqualTo(1);
    }

    @Test
    public void replacementAcquireWaitsForOlderSameIdLoadToSettle() {
        MapReferenceId mapId = id(1);
        MapReference initial = registration(mapId, 1L, true);
        MapReference replacement = MapReference.of(mapId, 1L, URI.create("maps/replaced.mm"), true,
            MAP_COLOR, Collections.<UnknownXml>emptyList());
        WorkspaceDocument initialDocument = workspace(initial);
        WorkspaceDocument replacementDocument = workspace(replacement);
        FakeLease staleLease = new FakeLease(mapId, MapOperationalState.AVAILABLE);
        FakeLease replacementLease = new FakeLease(mapId, MapOperationalState.AVAILABLE);
        CompletableFuture<MapLease> oldAcquisition = new CompletableFuture<MapLease>();
        AtomicReference<WorkspaceDocument> currentDocument = new AtomicReference<WorkspaceDocument>(initialDocument);
        AtomicReference<WorkspaceStoreListener> workspaceListener = new AtomicReference<WorkspaceStoreListener>();
        List<MapReference> acquisitions = new ArrayList<MapReference>();
        TestEdt edt = new TestEdt();
        GraphWorkspaceStore store = mock(GraphWorkspaceStore.class);
        MapLeaseManager leaseManager = mock(MapLeaseManager.class);
        MapSnapshotFactory snapshotFactory = mock(MapSnapshotFactory.class);
        when(store.currentDocument()).thenAnswer(invocation -> currentDocument.get());
        when(store.addListener(any(WorkspaceStoreListener.class))).thenAnswer(invocation -> {
            workspaceListener.set(invocation.getArgument(0));
            return new ListenerRegistrationStub();
        });
        when(leaseManager.addListener(any(MapAdapterListener.class))).thenReturn(new ListenerRegistrationStub());
        when(snapshotFactory.snapshot(same(replacementLease))).thenReturn(snapshot(mapId, 1));
        WorkspaceMapCoordinator coordinator = new WorkspaceMapCoordinator(snapshotFactory, leaseManager, store, edt,
            reference -> {
                acquisitions.add(reference);
                if (reference.equals(initial)) {
                    return oldAcquisition;
                }
                if (reference.equals(replacement)) {
                    if (!oldAcquisition.isDone()) {
                        CompletableFuture<MapLease> rejected = new CompletableFuture<MapLease>();
                        rejected.completeExceptionally(new IllegalArgumentException("same map is still loading"));
                        return rejected;
                    }
                    return CompletableFuture.completedFuture(replacementLease);
                }
                throw new AssertionError("Unexpected registration: " + reference);
            });

        assertThat(acquisitions).containsExactly(initial);
        currentDocument.set(replacementDocument);
        workspaceListener.get().onWorkspaceStoreEvent(mockDocumentChangedEvent(replacementDocument));
        edt.runQueued();
        assertThat(acquisitions).containsExactly(initial);

        oldAcquisition.complete(staleLease);
        edt.runQueued();

        assertThat(acquisitions).containsExactly(initial, replacement);
        ProjectionInput input = coordinator.capture(batch(15L));
        assertThat(input.availability().get(mapId)).isEqualTo(MapAvailability.AVAILABLE);
        assertThat(input.maps()).containsExactly(snapshot(mapId, 1));
        assertThat(staleLease.closeCount()).isEqualTo(1);
        assertThat(replacementLease.closeCount()).isEqualTo(0);
    }

    @Test
    public void retriesPendingAcquireOnceAfterStaleLeaseSettlesAndInstallsReplacement() {
        MapReferenceId mapId = id(1);
        MapReference active = registration(mapId, 1L, true);
        WorkspaceDocument document = workspace(active);
        FakeLease staleLease = new FakeLease(mapId, MapOperationalState.AVAILABLE);
        FakeLease replacementLease = new FakeLease(mapId, MapOperationalState.AVAILABLE);
        CompletableFuture<MapLease> firstAcquire = new CompletableFuture<MapLease>();
        CompletableFuture<MapLease> secondAcquire = new CompletableFuture<MapLease>();
        List<MapReference> acquisitions = new ArrayList<MapReference>();
        MapSnapshot expectedSnapshot = snapshot(mapId, 1);
        TestEdt edt = new TestEdt();
        GraphWorkspaceStore store = mock(GraphWorkspaceStore.class);
        MapLeaseManager leaseManager = mock(MapLeaseManager.class);
        MapSnapshotFactory snapshotFactory = mock(MapSnapshotFactory.class);
        stubStoreAndListeners(store, leaseManager, document, new ListenerRegistrationStub(),
            new ListenerRegistrationStub());
        when(snapshotFactory.snapshot(same(replacementLease))).thenReturn(expectedSnapshot);
        WorkspaceMapCoordinator coordinator = new WorkspaceMapCoordinator(snapshotFactory, leaseManager, store, edt,
            reference -> {
                acquisitions.add(reference);
                if (acquisitions.size() == 1) {
                    return firstAcquire;
                }
                if (acquisitions.size() == 2) {
                    return secondAcquire;
                }
                throw new AssertionError("Unexpected third acquisition");
            });
        coordinators.add(coordinator);

        assertThat(acquisitions).containsExactly(active);
        coordinator.retry(active);
        assertThat(acquisitions).containsExactly(active);
        assertThat(store.currentDocument()).isSameAs(document);

        firstAcquire.complete(staleLease);
        edt.runQueued();

        assertThat(staleLease.closeCount()).isEqualTo(1);
        assertThat(acquisitions).hasSize(2);
        assertThat(acquisitions.get(1)).isSameAs(active);
        assertThat(acquisitions.get(1).id()).isEqualTo(mapId);
        assertThat(acquisitions.get(1).storedUri()).isEqualTo(active.storedUri());
        assertThat(secondAcquire.isDone()).isFalse();

        secondAcquire.complete(replacementLease);
        edt.runQueued();

        ProjectionInput input = coordinator.capture(batch(19L));
        assertThat(input.availability().get(mapId)).isEqualTo(MapAvailability.AVAILABLE);
        assertThat(input.maps()).containsExactly(expectedSnapshot);
        assertThat(replacementLease.closeCount()).isEqualTo(0);
        assertThat(acquisitions).hasSize(2);
    }

    @Test
    public void rejectsStaleOrInactiveRetryReferencesWithoutChangingTheInstalledRegistration() {
        MapReferenceId activeId = id(1);
        MapReferenceId inactiveId = id(2);
        MapReference active = registration(activeId, 1L, true);
        MapReference inactive = registration(inactiveId, 2L, false);
        MapReference stale = MapReference.of(activeId, 1L, URI.create("maps/stale.mm"), true,
            MAP_COLOR, Collections.<UnknownXml>emptyList());
        WorkspaceDocument document = workspace(active, inactive);
        FakeLease activeLease = new FakeLease(activeId, MapOperationalState.AVAILABLE);
        MapSnapshot expectedSnapshot = snapshot(activeId, 1);
        List<MapReference> acquisitions = new ArrayList<MapReference>();
        TestEdt edt = new TestEdt();
        GraphWorkspaceStore store = mock(GraphWorkspaceStore.class);
        MapLeaseManager leaseManager = mock(MapLeaseManager.class);
        MapSnapshotFactory snapshotFactory = mock(MapSnapshotFactory.class);
        stubStoreAndListeners(store, leaseManager, document, new ListenerRegistrationStub(),
            new ListenerRegistrationStub());
        when(snapshotFactory.snapshot(same(activeLease))).thenReturn(expectedSnapshot);
        WorkspaceMapCoordinator coordinator = new WorkspaceMapCoordinator(snapshotFactory, leaseManager, store, edt,
            reference -> {
                acquisitions.add(reference);
                return CompletableFuture.completedFuture(activeLease);
            });
        coordinators.add(coordinator);

        assertThatThrownBy(() -> coordinator.retry(stale)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> coordinator.retry(inactive)).isInstanceOf(IllegalStateException.class);

        ProjectionInput input = coordinator.capture(batch(20L));
        assertThat(acquisitions).containsExactly(active);
        assertThat(input.availability().get(activeId)).isEqualTo(MapAvailability.AVAILABLE);
        assertThat(input.availability().get(inactiveId)).isEqualTo(MapAvailability.INACTIVE);
        assertThat(input.maps()).containsExactly(expectedSnapshot);
        assertThat(activeLease.closeCount()).isEqualTo(0);
        assertThat(store.currentDocument()).isSameAs(document);
    }

    @Test
    public void replacementAfterRemovalWaitsForOlderLoadToSettle() {
        MapReferenceId mapId = id(1);
        MapReference initial = registration(mapId, 1L, true);
        MapReference replacement = MapReference.of(mapId, 1L, URI.create("maps/replaced-after-removal.mm"), true,
            MAP_COLOR, Collections.<UnknownXml>emptyList());
        WorkspaceDocument initialDocument = workspace(initial);
        WorkspaceDocument removedDocument = workspace();
        WorkspaceDocument replacementDocument = workspace(replacement);
        FakeLease staleLease = new FakeLease(mapId, MapOperationalState.AVAILABLE);
        FakeLease replacementLease = new FakeLease(mapId, MapOperationalState.AVAILABLE);
        CompletableFuture<MapLease> oldAcquisition = new CompletableFuture<MapLease>();
        AtomicReference<WorkspaceDocument> currentDocument = new AtomicReference<WorkspaceDocument>(initialDocument);
        AtomicReference<WorkspaceStoreListener> workspaceListener = new AtomicReference<WorkspaceStoreListener>();
        List<MapReference> acquisitions = new ArrayList<MapReference>();
        TestEdt edt = new TestEdt();
        GraphWorkspaceStore store = mock(GraphWorkspaceStore.class);
        MapLeaseManager leaseManager = mock(MapLeaseManager.class);
        MapSnapshotFactory snapshotFactory = mock(MapSnapshotFactory.class);
        when(store.currentDocument()).thenAnswer(invocation -> currentDocument.get());
        when(store.addListener(any(WorkspaceStoreListener.class))).thenAnswer(invocation -> {
            workspaceListener.set(invocation.getArgument(0));
            return new ListenerRegistrationStub();
        });
        when(leaseManager.addListener(any(MapAdapterListener.class))).thenReturn(new ListenerRegistrationStub());
        when(snapshotFactory.snapshot(same(replacementLease))).thenReturn(snapshot(mapId, 1));
        WorkspaceMapCoordinator coordinator = new WorkspaceMapCoordinator(snapshotFactory, leaseManager, store, edt,
            reference -> {
                acquisitions.add(reference);
                if (reference.equals(initial)) {
                    return oldAcquisition;
                }
                if (reference.equals(replacement)) {
                    if (!oldAcquisition.isDone()) {
                        CompletableFuture<MapLease> rejected = new CompletableFuture<MapLease>();
                        rejected.completeExceptionally(new IllegalArgumentException("same map is still loading"));
                        return rejected;
                    }
                    return CompletableFuture.completedFuture(replacementLease);
                }
                throw new AssertionError("Unexpected registration: " + reference);
            });

        assertThat(acquisitions).containsExactly(initial);
        currentDocument.set(removedDocument);
        workspaceListener.get().onWorkspaceStoreEvent(mockDocumentChangedEvent(removedDocument));
        edt.runQueued();
        assertThat(acquisitions).containsExactly(initial);

        currentDocument.set(replacementDocument);
        workspaceListener.get().onWorkspaceStoreEvent(mockDocumentChangedEvent(replacementDocument));
        edt.runQueued();
        assertThat(acquisitions).containsExactly(initial);

        oldAcquisition.complete(staleLease);
        edt.runQueued();

        assertThat(acquisitions).containsExactly(initial, replacement);
        assertThat(staleLease.closeCount()).isEqualTo(1);
        assertThat(replacementLease.closeCount()).isEqualTo(0);
        ProjectionInput input = coordinator.capture(batch(16L));
        assertThat(input.availability().get(mapId)).isEqualTo(MapAvailability.AVAILABLE);
        assertThat(input.maps()).containsExactly(snapshot(mapId, 1));
    }

    @Test
    public void replacementAfterDeactivationWaitsForOlderLoadToSettle() {
        MapReferenceId mapId = id(1);
        MapReference initial = registration(mapId, 1L, true);
        MapReference inactive = registration(mapId, 1L, false);
        MapReference replacement = MapReference.of(mapId, 1L, URI.create("maps/replaced-after-deactivation.mm"), true,
            MAP_COLOR, Collections.<UnknownXml>emptyList());
        WorkspaceDocument initialDocument = workspace(initial);
        WorkspaceDocument inactiveDocument = workspace(inactive);
        WorkspaceDocument replacementDocument = workspace(replacement);
        FakeLease staleLease = new FakeLease(mapId, MapOperationalState.AVAILABLE);
        FakeLease replacementLease = new FakeLease(mapId, MapOperationalState.AVAILABLE);
        CompletableFuture<MapLease> oldAcquisition = new CompletableFuture<MapLease>();
        AtomicReference<WorkspaceDocument> currentDocument = new AtomicReference<WorkspaceDocument>(initialDocument);
        AtomicReference<WorkspaceStoreListener> workspaceListener = new AtomicReference<WorkspaceStoreListener>();
        List<MapReference> acquisitions = new ArrayList<MapReference>();
        TestEdt edt = new TestEdt();
        GraphWorkspaceStore store = mock(GraphWorkspaceStore.class);
        MapLeaseManager leaseManager = mock(MapLeaseManager.class);
        MapSnapshotFactory snapshotFactory = mock(MapSnapshotFactory.class);
        when(store.currentDocument()).thenAnswer(invocation -> currentDocument.get());
        when(store.addListener(any(WorkspaceStoreListener.class))).thenAnswer(invocation -> {
            workspaceListener.set(invocation.getArgument(0));
            return new ListenerRegistrationStub();
        });
        when(leaseManager.addListener(any(MapAdapterListener.class))).thenReturn(new ListenerRegistrationStub());
        when(snapshotFactory.snapshot(same(replacementLease))).thenReturn(snapshot(mapId, 1));
        WorkspaceMapCoordinator coordinator = new WorkspaceMapCoordinator(snapshotFactory, leaseManager, store, edt,
            reference -> {
                acquisitions.add(reference);
                if (reference.equals(initial)) {
                    return oldAcquisition;
                }
                if (reference.equals(replacement)) {
                    if (!oldAcquisition.isDone()) {
                        CompletableFuture<MapLease> rejected = new CompletableFuture<MapLease>();
                        rejected.completeExceptionally(new IllegalArgumentException("same map is still loading"));
                        return rejected;
                    }
                    return CompletableFuture.completedFuture(replacementLease);
                }
                throw new AssertionError("Unexpected registration: " + reference);
            });

        assertThat(acquisitions).containsExactly(initial);
        currentDocument.set(inactiveDocument);
        workspaceListener.get().onWorkspaceStoreEvent(mockDocumentChangedEvent(inactiveDocument));
        edt.runQueued();
        assertThat(acquisitions).containsExactly(initial);

        currentDocument.set(replacementDocument);
        workspaceListener.get().onWorkspaceStoreEvent(mockDocumentChangedEvent(replacementDocument));
        edt.runQueued();
        assertThat(acquisitions).containsExactly(initial);

        oldAcquisition.complete(staleLease);
        edt.runQueued();

        assertThat(acquisitions).containsExactly(initial, replacement);
        assertThat(staleLease.closeCount()).isEqualTo(1);
        assertThat(replacementLease.closeCount()).isEqualTo(0);
        ProjectionInput input = coordinator.capture(batch(17L));
        assertThat(input.availability().get(mapId)).isEqualTo(MapAvailability.AVAILABLE);
        assertThat(input.maps()).containsExactly(snapshot(mapId, 1));
    }

    @Test
    public void replacementAfterRemovalAndInactivityWaitsForOlderLoadToSettle() {
        MapReferenceId mapId = id(1);
        MapReference initial = registration(mapId, 1L, true);
        MapReference inactive = registration(mapId, 1L, false);
        MapReference replacement = MapReference.of(mapId, 1L, URI.create("maps/replaced-after-transitions.mm"), true,
            MAP_COLOR, Collections.<UnknownXml>emptyList());
        WorkspaceDocument initialDocument = workspace(initial);
        WorkspaceDocument removedDocument = workspace();
        WorkspaceDocument inactiveDocument = workspace(inactive);
        WorkspaceDocument replacementDocument = workspace(replacement);
        FakeLease staleLease = new FakeLease(mapId, MapOperationalState.AVAILABLE);
        FakeLease replacementLease = new FakeLease(mapId, MapOperationalState.AVAILABLE);
        CompletableFuture<MapLease> oldAcquisition = new CompletableFuture<MapLease>();
        AtomicReference<WorkspaceDocument> currentDocument = new AtomicReference<WorkspaceDocument>(initialDocument);
        AtomicReference<WorkspaceStoreListener> workspaceListener = new AtomicReference<WorkspaceStoreListener>();
        List<MapReference> acquisitions = new ArrayList<MapReference>();
        TestEdt edt = new TestEdt();
        GraphWorkspaceStore store = mock(GraphWorkspaceStore.class);
        MapLeaseManager leaseManager = mock(MapLeaseManager.class);
        MapSnapshotFactory snapshotFactory = mock(MapSnapshotFactory.class);
        when(store.currentDocument()).thenAnswer(invocation -> currentDocument.get());
        when(store.addListener(any(WorkspaceStoreListener.class))).thenAnswer(invocation -> {
            workspaceListener.set(invocation.getArgument(0));
            return new ListenerRegistrationStub();
        });
        when(leaseManager.addListener(any(MapAdapterListener.class))).thenReturn(new ListenerRegistrationStub());
        when(snapshotFactory.snapshot(same(replacementLease))).thenReturn(snapshot(mapId, 1));
        WorkspaceMapCoordinator coordinator = new WorkspaceMapCoordinator(snapshotFactory, leaseManager, store, edt,
            reference -> {
                acquisitions.add(reference);
                if (reference.equals(initial)) {
                    return oldAcquisition;
                }
                if (reference.equals(replacement)) {
                    if (!oldAcquisition.isDone()) {
                        CompletableFuture<MapLease> rejected = new CompletableFuture<MapLease>();
                        rejected.completeExceptionally(new IllegalArgumentException("same map is still loading"));
                        return rejected;
                    }
                    return CompletableFuture.completedFuture(replacementLease);
                }
                throw new AssertionError("Unexpected registration: " + reference);
            });

        assertThat(acquisitions).containsExactly(initial);
        currentDocument.set(removedDocument);
        workspaceListener.get().onWorkspaceStoreEvent(mockDocumentChangedEvent(removedDocument));
        edt.runQueued();
        assertThat(acquisitions).containsExactly(initial);

        currentDocument.set(inactiveDocument);
        workspaceListener.get().onWorkspaceStoreEvent(mockDocumentChangedEvent(inactiveDocument));
        edt.runQueued();
        assertThat(acquisitions).containsExactly(initial);

        currentDocument.set(replacementDocument);
        workspaceListener.get().onWorkspaceStoreEvent(mockDocumentChangedEvent(replacementDocument));
        edt.runQueued();
        assertThat(acquisitions).containsExactly(initial);

        oldAcquisition.complete(staleLease);
        edt.runQueued();

        assertThat(acquisitions).containsExactly(initial, replacement);
        assertThat(staleLease.closeCount()).isEqualTo(1);
        assertThat(replacementLease.closeCount()).isEqualTo(0);
        ProjectionInput input = coordinator.capture(batch(18L));
        assertThat(input.availability().get(mapId)).isEqualTo(MapAvailability.AVAILABLE);
        assertThat(input.maps()).containsExactly(snapshot(mapId, 1));
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
