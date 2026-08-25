package org.freeplane.plugin.graph.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.freeplane.plugin.graph.adapter.EdtExecutor;
import org.freeplane.plugin.graph.adapter.MapAdapterEvent;
import org.freeplane.plugin.graph.adapter.MapAdapterListener;
import org.freeplane.plugin.graph.adapter.MapLease;
import org.freeplane.plugin.graph.adapter.MapLeaseManager;
import org.freeplane.plugin.graph.adapter.MapOperationalState;
import org.freeplane.plugin.graph.adapter.MapSnapshotFactory;
import org.freeplane.plugin.graph.projection.input.MapAvailability;
import org.freeplane.plugin.graph.projection.input.MapSnapshot;
import org.freeplane.plugin.graph.projection.input.ProjectionInput;
import org.freeplane.plugin.graph.workspace.GraphWorkspaceStore;
import org.freeplane.plugin.graph.workspace.ListenerRegistration;
import org.freeplane.plugin.graph.workspace.WorkspaceIdentityChange;
import org.freeplane.plugin.graph.workspace.WorkspaceStoreEvent;
import org.freeplane.plugin.graph.workspace.WorkspaceStoreListener;
import org.freeplane.plugin.graph.workspace.model.MapReference;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.WorkspaceDocument;

public final class WorkspaceMapCoordinator implements AutoCloseable {
    private final Object monitor = new Object();
    private final MapSnapshotFactory snapshotFactory;
    private final MapLeaseManager leaseManager;
    private final GraphWorkspaceStore store;
    private final EdtExecutor edt;
    private final LeaseAcquirer leaseAcquirer;
    private final LinkedHashMap<MapReferenceId, Registration> registrations =
        new LinkedHashMap<MapReferenceId, Registration>();
    private final LinkedHashMap<MapReferenceId, AcquisitionBarrier> acquisitionBarriers =
        new LinkedHashMap<MapReferenceId, AcquisitionBarrier>();
    private final Set<MapLease> pendingCompletions = Collections.newSetFromMap(
        new IdentityHashMap<MapLease, Boolean>());
    private final List<LeaseAttachmentListener> leaseAttachmentListeners =
        new ArrayList<LeaseAttachmentListener>();
    private final WorkspaceStoreListener workspaceListener;
    private final MapAdapterListener adapterListener;

    private ListenerRegistration workspaceListenerRegistration;
    private ListenerRegistration adapterListenerRegistration;
    private boolean closed;

    public WorkspaceMapCoordinator(final GraphWorkspaceStore store, final MapLeaseManager leaseManager) {
        this(new MapSnapshotFactory(), leaseManager, store, new SwingEdtExecutor(), new LeaseAcquirer() {
            @Override
            public CompletionStage<MapLease> acquire(final MapReference reference) {
                return leaseManager.acquire(reference);
            }
        });
    }

    WorkspaceMapCoordinator(final MapSnapshotFactory snapshotFactory, final MapLeaseManager leaseManager,
            final GraphWorkspaceStore store, final EdtExecutor edt, final LeaseAcquirer leaseAcquirer) {
        this.snapshotFactory = Objects.requireNonNull(snapshotFactory, "snapshotFactory");
        this.leaseManager = Objects.requireNonNull(leaseManager, "leaseManager");
        this.store = Objects.requireNonNull(store, "store");
        this.edt = Objects.requireNonNull(edt, "edt");
        this.leaseAcquirer = Objects.requireNonNull(leaseAcquirer, "leaseAcquirer");
        this.workspaceListener = new WorkspaceStoreListener() {
            @Override
            public void onWorkspaceStoreEvent(final WorkspaceStoreEvent event) {
                handleWorkspaceStoreEvent(event);
            }
        };
        this.adapterListener = new MapAdapterListener() {
            @Override
            public void onMapAdapterEvent(final MapAdapterEvent event) {
                handleMapAdapterEvent(event);
            }
        };

        ListenerRegistration storeRegistration = null;
        ListenerRegistration mapRegistration = null;
        try {
            storeRegistration = store.addListener(workspaceListener);
            mapRegistration = leaseManager.addListener(adapterListener);
            workspaceListenerRegistration = Objects.requireNonNull(storeRegistration, "workspace listener registration");
            adapterListenerRegistration = Objects.requireNonNull(mapRegistration, "map listener registration");
            edt.call(new Callable<Void>() {
                @Override
                public Void call() {
                    installDocumentOnEdt(store.currentDocument());
                    return null;
                }
            });
        }
        catch (RuntimeException failure) {
            closeRegistration(mapRegistration);
            closeRegistration(storeRegistration);
            throw failure;
        }
    }

    public ProjectionInput capture(final AcceptedBatch batch) {
        final AcceptedBatch value = Objects.requireNonNull(batch, "batch");
        synchronized (monitor) {
            requireOpenLocked();
        }
        return edt.call(new Callable<ProjectionInput>() {
            @Override
            public ProjectionInput call() {
                return captureOnEdt(value);
            }
        });
    }

    /**
     * Returns the coordinator-owned lease without transferring ownership to the caller.
     */
    public Optional<MapLease> find(final MapReferenceId id) {
        final MapReferenceId value = Objects.requireNonNull(id, "id");
        synchronized (monitor) {
            if (closed) {
                return Optional.empty();
            }
            final Registration registration = registrations.get(value);
            return registration == null || registration.lease == null
                ? Optional.<MapLease>empty() : Optional.of(registration.lease);
        }
    }

    public void retry(final MapReference reference) {
        final MapReference value = Objects.requireNonNull(reference, "reference");
        edt.call(new Callable<Void>() {
            @Override
            public Void call() {
                retryOnEdt(value);
                return null;
            }
        });
    }

    ListenerRegistration addLeaseAttachmentListener(final LeaseAttachmentListener listener) {
        final LeaseAttachmentListener value = Objects.requireNonNull(listener, "listener");
        synchronized (monitor) {
            requireOpenLocked();
            leaseAttachmentListeners.add(value);
            return new ListenerRegistration() {
                @Override
                public void close() {
                    removeLeaseAttachmentListener(value);
                }
            };
        }
    }

    private void removeLeaseAttachmentListener(final LeaseAttachmentListener listener) {
        synchronized (monitor) {
            leaseAttachmentListeners.remove(listener);
        }
    }

    @Override
    public void close() {
        final ListenerRegistration storeRegistration;
        final ListenerRegistration mapRegistration;
        final List<MapLease> leases = new ArrayList<MapLease>();
        final Set<MapLease> leasesToClose = Collections.newSetFromMap(
            new IdentityHashMap<MapLease, Boolean>());
        synchronized (monitor) {
            if (closed) {
                return;
            }
            closed = true;
            storeRegistration = workspaceListenerRegistration;
            mapRegistration = adapterListenerRegistration;
            workspaceListenerRegistration = null;
            adapterListenerRegistration = null;
            for (MapLease lease : pendingCompletions) {
                if (leasesToClose.add(lease)) {
                    leases.add(lease);
                }
            }
            pendingCompletions.clear();
            acquisitionBarriers.clear();
            leaseAttachmentListeners.clear();
            for (Registration registration : registrations.values()) {
                final MapLease lease = detachLeaseLocked(registration);
                if (lease != null && leasesToClose.add(lease)) {
                    leases.add(lease);
                }
            }
            registrations.clear();
        }
        closeRegistration(storeRegistration);
        closeRegistration(mapRegistration);
        for (MapLease lease : leases) {
            closeLease(lease);
        }
    }

    private void handleWorkspaceStoreEvent(final WorkspaceStoreEvent event) {
        if (event == null) {
            return;
        }
        final WorkspaceStoreEvent.Type type = event.type();
        if (type != WorkspaceStoreEvent.Type.DOCUMENT_CHANGED && type != WorkspaceStoreEvent.Type.IDENTITY_CHANGED) {
            return;
        }
        if (type == WorkspaceStoreEvent.Type.IDENTITY_CHANGED) {
            final WorkspaceIdentityChange change = event.identityChange().orElseThrow(
                () -> new IllegalStateException("Identity change event has no identity"));
            leaseManager.rebaseWorkspace(change.newPath());
        }
        final WorkspaceDocument document = event.document();
        executeOnEdt(new Runnable() {
            @Override
            public void run() {
                installDocumentOnEdt(document);
            }
        });
    }

    private void handleMapAdapterEvent(final MapAdapterEvent event) {
        if (event == null) {
            return;
        }
        executeOnEdt(new Runnable() {
            @Override
            public void run() {
                synchronized (monitor) {
                    if (closed) {
                        return;
                    }
                    final Registration registration = registrations.get(event.mapReferenceId());
                    if (registration == null || !registration.reference.active()) {
                        return;
                    }
                    registration.availability = availabilityFor(event.state());
                }
            }
        });
    }

    private void installDocumentOnEdt(final WorkspaceDocument document) {
        final WorkspaceDocument value = Objects.requireNonNull(document, "document");
        final List<MapLease> staleLeases = new ArrayList<MapLease>();
        final List<Registration> acquisitions = new ArrayList<Registration>();
        synchronized (monitor) {
            if (closed) {
                return;
            }
            final LinkedHashMap<MapReferenceId, Registration> remaining =
                new LinkedHashMap<MapReferenceId, Registration>(registrations);
            final LinkedHashMap<MapReferenceId, Registration> next =
                new LinkedHashMap<MapReferenceId, Registration>();
            for (MapReference reference : value.maps()) {
                final MapReferenceId id = reference.id();
                final Registration previous = remaining.remove(id);
                final AcquisitionBarrier barrier = acquisitionBarriers.get(id);
                if (!reference.active()) {
                    if (previous != null) {
                        final MapLease lease = detachLeaseLocked(previous);
                        if (lease != null) {
                            staleLeases.add(lease);
                        }
                    }
                    if (barrier != null) {
                        barrier.latestRegistration = null;
                    }
                    next.put(id, new Registration(reference, MapAvailability.INACTIVE));
                }
                else if (previous != null && previous.reference.active() && previous.reference.equals(reference)) {
                    next.put(id, previous);
                    if (barrier != null) {
                        barrier.latestRegistration = previous;
                    }
                }
                else {
                    if (previous != null) {
                        final MapLease lease = detachLeaseLocked(previous);
                        if (lease != null) {
                            staleLeases.add(lease);
                        }
                    }
                    final Registration registration = new Registration(reference, MapAvailability.LOADING);
                    next.put(id, registration);
                    if (barrier != null) {
                        barrier.latestRegistration = registration;
                    }
                    else {
                        acquisitions.add(registration);
                    }
                }
            }
            for (Registration removed : remaining.values()) {
                final MapLease lease = detachLeaseLocked(removed);
                if (lease != null) {
                    staleLeases.add(lease);
                }
                final AcquisitionBarrier barrier = acquisitionBarriers.get(removed.reference.id());
                if (barrier != null) {
                    barrier.latestRegistration = null;
                }
            }
            registrations.clear();
            registrations.putAll(next);
        }
        for (MapLease lease : staleLeases) {
            closeLease(lease);
        }
        for (Registration registration : acquisitions) {
            beginAcquireOnEdt(registration);
        }
    }

    private void retryOnEdt(final MapReference reference) {
        Registration acquire = null;
        MapLease staleLease = null;
        synchronized (monitor) {
            requireOpenLocked();
            final MapReferenceId id = reference.id();
            final Registration registration = registrations.get(id);
            if (registration == null || !id.equals(registration.reference.id())
                    || !registration.reference.equals(reference) || !reference.active()
                    || !registration.reference.active()) {
                throw new IllegalArgumentException("Map retry reference is stale or inactive");
            }
            staleLease = detachLeaseLocked(registration);
            registration.availability = MapAvailability.LOADING;
            final AcquisitionBarrier barrier = acquisitionBarriers.get(id);
            if (barrier == null) {
                acquire = registration;
            }
            else {
                barrier.latestRegistration = registration;
            }
        }
        closeLease(staleLease);
        if (acquire != null) {
            beginAcquireOnEdt(acquire);
        }
    }

    private void beginAcquireOnEdt(final Registration registration) {
        final long acquisitionGeneration;
        synchronized (monitor) {
            final MapReferenceId id = registration.reference.id();
            if (closed || registrations.get(id) != registration
                    || !registration.reference.active() || acquisitionBarriers.containsKey(id)) {
                return;
            }
            registration.availability = MapAvailability.LOADING;
            acquisitionGeneration = ++registration.acquisitionGeneration;
            acquisitionBarriers.put(id, new AcquisitionBarrier(registration, acquisitionGeneration, registration));
        }
        final CompletionStage<MapLease> acquisition;
        try {
            acquisition = leaseAcquirer.acquire(registration.reference);
        }
        catch (RuntimeException failure) {
            finishAcquireOnEdt(registration, acquisitionGeneration, null, failure);
            return;
        }
        if (acquisition == null) {
            finishAcquireOnEdt(registration, acquisitionGeneration, null,
                new IllegalStateException("Map lease acquisition returned no completion stage"));
            return;
        }
        try {
            acquisition.whenComplete((lease, failure) -> receiveAcquireCompletion(registration, acquisitionGeneration,
                lease, failure));
        }
        catch (RuntimeException failure) {
            finishAcquireOnEdt(registration, acquisitionGeneration, null, failure);
        }
    }

    private void receiveAcquireCompletion(final Registration registration, final long acquisitionGeneration,
            final MapLease lease, final Throwable failure) {
        final boolean closeImmediately;
        synchronized (monitor) {
            closeImmediately = closed;
            if (!closeImmediately && lease != null) {
                pendingCompletions.add(lease);
            }
        }
        if (closeImmediately) {
            closeLease(lease);
            return;
        }
        try {
            handoffAcquireCompletionOnEdt(new Runnable() {
                @Override
                public void run() {
                    if (!claimPendingCompletion(lease)) {
                        return;
                    }
                    finishAcquireOnEdt(registration, acquisitionGeneration, lease, failure);
                }
            });
        }
        catch (RuntimeException ignored) {
            if (claimPendingCompletion(lease)) {
                closeLease(lease);
            }
        }
    }

    private void handoffAcquireCompletionOnEdt(final Runnable task) {
        // MapLeaseManager settles a request after completing its future, so this must be a later EDT turn.
        if (edt instanceof SwingEdtExecutor) {
            ((SwingEdtExecutor) edt).executeLater(task);
        }
        else {
            edt.execute(task);
        }
    }

    private boolean claimPendingCompletion(final MapLease lease) {
        if (lease == null) {
            return true;
        }
        synchronized (monitor) {
            return pendingCompletions.remove(lease);
        }
    }

    private void finishAcquireOnEdt(final Registration registration, final long acquisitionGeneration,
            final MapLease lease, final Throwable failure) {
        boolean validLease = false;
        try {
            validLease = lease != null && registration.reference.id().equals(lease.mapReferenceId());
        }
        catch (RuntimeException stateFailure) {
            validLease = false;
        }
        MapLease staleLease = null;
        Registration retry = null;
        MapReferenceId attachedId = null;
        synchronized (monitor) {
            final MapReferenceId id = registration.reference.id();
            final AcquisitionBarrier barrier = acquisitionBarriers.get(id);
            if (barrier == null || barrier.source != registration
                    || barrier.sourceGeneration != acquisitionGeneration) {
                staleLease = lease;
            }
            else {
                acquisitionBarriers.remove(id);
                final Registration current = registrations.get(id);
                final boolean currentSource = !closed && current == registration
                    && registration.acquisitionGeneration == acquisitionGeneration
                    && registration.reference.active();
                if (currentSource && failure == null && validLease) {
                    registration.lease = lease;
                    registration.availability = availabilityFor(lease);
                    attachedId = id;
                }
                else {
                    staleLease = lease;
                    if (currentSource) {
                        registration.availability = MapAvailability.UNREADABLE;
                    }
                    else if (!closed && current != null && current.reference.active()
                            && barrier.latestRegistration == current
                            && current.reference.equals(barrier.latestRegistration.reference)) {
                        retry = current;
                    }
                }
            }
        }
        if (attachedId != null) {
            notifyLeaseAttachmentListeners(attachedId);
        }
        closeLease(staleLease);
        if (retry != null) {
            beginAcquireOnEdt(retry);
        }
    }

    private void notifyLeaseAttachmentListeners(final MapReferenceId id) {
        final List<LeaseAttachmentListener> listeners;
        synchronized (monitor) {
            if (closed) {
                return;
            }
            listeners = new ArrayList<LeaseAttachmentListener>(leaseAttachmentListeners);
        }
        for (LeaseAttachmentListener listener : listeners) {
            synchronized (monitor) {
                if (closed) {
                    return;
                }
                if (!leaseAttachmentListeners.contains(listener)) {
                    continue;
                }
            }
            try {
                listener.onLeaseAttached(id);
            }
            catch (RuntimeException ignored) {
                // A rebuild observer must not interfere with lease ownership settlement.
            }
        }
    }

    private ProjectionInput captureOnEdt(final AcceptedBatch batch) {
        final WorkspaceDocument document;
        final List<MapReference> maps;
        synchronized (monitor) {
            requireOpenLocked();
        }
        document = Objects.requireNonNull(store.currentDocument(), "workspace document");
        maps = new ArrayList<MapReference>(document.maps());
        final LinkedHashMap<MapReferenceId, MapAvailability> availability =
            new LinkedHashMap<MapReferenceId, MapAvailability>();
        final List<SnapshotRequest> snapshotRequests = new ArrayList<SnapshotRequest>();
        final LinkedHashMap<MapReferenceId, Integer> order = new LinkedHashMap<MapReferenceId, Integer>();
        synchronized (monitor) {
            requireOpenLocked();
            for (int index = 0; index < maps.size(); index++) {
                final MapReference reference = maps.get(index);
                order.put(reference.id(), Integer.valueOf(index));
                if (!reference.active()) {
                    availability.put(reference.id(), MapAvailability.INACTIVE);
                    continue;
                }
                final Registration registration = registrations.get(reference.id());
                final boolean current = registration != null && registration.reference.equals(reference);
                MapAvailability state = current ? registration.availability : MapAvailability.LOADING;
                final MapLease lease = current ? registration.lease : null;
                if (state == MapAvailability.AVAILABLE && lease == null) {
                    state = MapAvailability.UNREADABLE;
                }
                availability.put(reference.id(), state);
                if (state == MapAvailability.AVAILABLE) {
                    snapshotRequests.add(new SnapshotRequest(reference, lease));
                }
            }
        }

        final List<MapSnapshot> snapshots = new ArrayList<MapSnapshot>();
        for (SnapshotRequest request : snapshotRequests) {
            try {
                final MapSnapshot snapshot = snapshotFactory.snapshot(request.lease);
                if (snapshot == null || !request.reference.id().equals(snapshot.mapReferenceId())) {
                    throw new IllegalStateException("Map snapshot does not match its registration");
                }
                snapshots.add(snapshot);
            }
            catch (RuntimeException failure) {
                availability.put(request.reference.id(), MapAvailability.UNREADABLE);
            }
        }
        Collections.sort(snapshots, new Comparator<MapSnapshot>() {
            @Override
            public int compare(final MapSnapshot first, final MapSnapshot second) {
                return order.get(first.mapReferenceId()).compareTo(order.get(second.mapReferenceId()));
            }
        });
        synchronized (monitor) {
            requireOpenLocked();
        }
        return ProjectionInput.of(batch.generation(), document, snapshots, availability);
    }

    private void executeOnEdt(final Runnable task) {
        synchronized (monitor) {
            if (closed) {
                return;
            }
        }
        try {
            edt.execute(new Runnable() {
                @Override
                public void run() {
                    task.run();
                }
            });
        }
        catch (RuntimeException ignored) {
            // Close may invalidate callbacks while the executor is shutting down.
        }
    }

    private MapLease detachLeaseLocked(final Registration registration) {
        registration.acquisitionGeneration++;
        final MapLease lease = registration.lease;
        registration.lease = null;
        return lease;
    }

    private void requireOpenLocked() {
        if (closed) {
            throw new IllegalStateException("Workspace map coordinator is closed");
        }
    }

    private static MapAvailability availabilityFor(final MapLease lease) {
        try {
            return availabilityFor(lease.state());
        }
        catch (RuntimeException failure) {
            return MapAvailability.UNREADABLE;
        }
    }

    private static MapAvailability availabilityFor(final MapOperationalState state) {
        if (state == null) {
            return MapAvailability.UNREADABLE;
        }
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
            return MapAvailability.UNREADABLE;
        }
    }

    private static void closeLease(final MapLease lease) {
        if (lease == null) {
            return;
        }
        try {
            lease.close();
        }
        catch (RuntimeException ignored) {
            // Closing another lease must still release the remaining coordinator-owned leases.
        }
    }

    private static void closeRegistration(final ListenerRegistration registration) {
        if (registration == null) {
            return;
        }
        try {
            registration.close();
        }
        catch (RuntimeException ignored) {
            // A listener registration is no longer used after coordinator shutdown.
        }
    }

    interface LeaseAttachmentListener {
        void onLeaseAttached(MapReferenceId id);
    }

    interface LeaseAcquirer {
        CompletionStage<MapLease> acquire(MapReference reference);
    }

    private static final class AcquisitionBarrier {
        private final Registration source;
        private final long sourceGeneration;
        private Registration latestRegistration;

        private AcquisitionBarrier(final Registration source, final long sourceGeneration,
                final Registration latestRegistration) {
            this.source = source;
            this.sourceGeneration = sourceGeneration;
            this.latestRegistration = latestRegistration;
        }
    }

    private static final class Registration {
        private final MapReference reference;
        private MapLease lease;
        private MapAvailability availability;
        private long acquisitionGeneration;

        private Registration(final MapReference reference, final MapAvailability availability) {
            this.reference = reference;
            this.availability = availability;
        }
    }

    private static final class SnapshotRequest {
        private final MapReference reference;
        private final MapLease lease;

        private SnapshotRequest(final MapReference reference, final MapLease lease) {
            this.reference = reference;
            this.lease = lease;
        }
    }

    private static final class SwingEdtExecutor implements EdtExecutor {
        @Override
        public <T> T call(final Callable<T> task) {
            Objects.requireNonNull(task, "task");
            if (isEdt()) {
                return callNow(task);
            }
            final AtomicReference<T> result = new AtomicReference<T>();
            final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
            try {
                SwingUtilities.invokeAndWait(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            result.set(task.call());
                        }
                        catch (Throwable exception) {
                            failure.set(exception);
                        }
                    }
                });
            }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while executing on the EDT", interrupted);
            }
            catch (Exception exception) {
                throw new IllegalStateException("Unable to execute on the EDT", exception);
            }
            if (failure.get() != null) {
                throw new IllegalStateException("EDT task failed", failure.get());
            }
            return result.get();
        }

        @Override
        public void execute(final Runnable task) {
            Objects.requireNonNull(task, "task");
            if (isEdt()) {
                task.run();
            }
            else {
                SwingUtilities.invokeLater(task);
            }
        }

        private void executeLater(final Runnable task) {
            SwingUtilities.invokeLater(Objects.requireNonNull(task, "task"));
        }

        @Override
        public boolean isEdt() {
            return SwingUtilities.isEventDispatchThread();
        }

        private static <T> T callNow(final Callable<T> task) {
            try {
                return task.call();
            }
            catch (Exception failure) {
                throw new IllegalStateException("EDT task failed", failure);
            }
        }
    }
}
