package org.freeplane.plugin.graph.adapter;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import javax.swing.SwingUtilities;

import org.freeplane.features.map.EncryptionModel;
import org.freeplane.features.map.IMapChangeListener;
import org.freeplane.features.map.IMapLifeCycleListener;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.mindmapmode.MMapController;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.ui.IMapViewManager;
import org.freeplane.features.url.mindmapmode.MapLoader;
import org.freeplane.plugin.graph.workspace.ListenerRegistration;
import org.freeplane.plugin.graph.workspace.WorkspaceUriResolver;
import org.freeplane.plugin.graph.workspace.model.MapReference;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;

public final class MapLeaseManager implements AutoCloseable {
    private static final long EXTERNAL_CHECK_PERIOD_MILLIS = 500L;

    interface MapLoaderOperation {
        MapModel load(URL canonicalUrl) throws Exception;
    }

    interface MapViewLookup {
        boolean containsView(MapModel map);
    }

    interface MapLookup {
        MapModel get(URL canonicalUrl);
    }

    interface LeaseCompletionInterceptor {
        void beforeComplete(CompletableFuture<MapLease> future);
    }

    interface SettlementTestHook {
        void afterFailedSettlementTryLock();
    }

    private static final SettlementTestHook NO_OP_SETTLEMENT_TEST_HOOK = new SettlementTestHook() {
        @Override
        public void afterFailedSettlementTryLock() {
        }
    };

    private final Object monitor = new Object();
    // Serializes future delivery with every mutation that can invalidate a reserved request.
    private final ReentrantLock settlementLock = new ReentrantLock();
    private final Path workspaceFile;
    private final WorkspaceUriResolver uriResolver;
    private final ModeController modeController;
    private final MMapController mapController;
    private final EdtExecutor edt;
    private final MapLoaderOperation loader;
    private final MapViewLookup viewLookup;
    private final MapLookup mapLookup;
    private final LeaseCompletionInterceptor completionInterceptor;
    private final SettlementTestHook settlementTestHook;
    private final ScheduledExecutorService scheduler;
    private final boolean ownsScheduler;
    private final Map<MapReferenceId, Entry> entries = new HashMap<MapReferenceId, Entry>();
    private final List<MapAdapterListener> listeners = new ArrayList<MapAdapterListener>();
    private final Set<MapModel> suppressedRemovals = Collections.newSetFromMap(
        new IdentityHashMap<MapModel, Boolean>());
    private final List<DeferredOperation> deferredOperations = new ArrayList<DeferredOperation>();
    private final IMapLifeCycleListener lifecycleListener = new LifecycleListener();
    private ScheduledFuture<?> externalCheck;
    private boolean lifecycleListenerRegistered;
    private boolean deferredDrainScheduled;
    private boolean deferredDrainRunning;
    private boolean closed;

    private static final LeaseCompletionInterceptor NO_OP_COMPLETION_INTERCEPTOR = new LeaseCompletionInterceptor() {
        @Override
        public void beforeComplete(final CompletableFuture<MapLease> future) {
        }
    };

    public MapLeaseManager(final Path workspaceFile, final ModeController modeController) {
        this(workspaceFile, modeController, new SwingEdtExecutor(), newDefaultScheduler(), null, null, null, null,
            NO_OP_SETTLEMENT_TEST_HOOK, true);
    }

    public MapLeaseManager(final Path workspaceFile, final ModeController modeController,
            final EdtExecutor edt) {
        this(workspaceFile, modeController, edt, newDefaultScheduler(), null, null, null, null,
            NO_OP_SETTLEMENT_TEST_HOOK, true);
    }

    MapLeaseManager(final Path workspaceFile, final ModeController modeController,
            final EdtExecutor edt, final ScheduledExecutorService scheduler,
            final MapLoaderOperation loader, final MapViewLookup viewLookup) {
        this(workspaceFile, modeController, edt, scheduler, loader, viewLookup, null, null,
            NO_OP_SETTLEMENT_TEST_HOOK, false);
    }

    MapLeaseManager(final Path workspaceFile, final ModeController modeController,
            final EdtExecutor edt, final ScheduledExecutorService scheduler,
            final MapLoaderOperation loader, final MapViewLookup viewLookup, final MapLookup mapLookup) {
        this(workspaceFile, modeController, edt, scheduler, loader, viewLookup, mapLookup, null,
            NO_OP_SETTLEMENT_TEST_HOOK, false);
    }

    MapLeaseManager(final Path workspaceFile, final ModeController modeController,
            final EdtExecutor edt, final ScheduledExecutorService scheduler,
            final MapLoaderOperation loader, final MapViewLookup viewLookup, final MapLookup mapLookup,
            final LeaseCompletionInterceptor completionInterceptor) {
        this(workspaceFile, modeController, edt, scheduler, loader, viewLookup, mapLookup, completionInterceptor,
            NO_OP_SETTLEMENT_TEST_HOOK, false);
    }

    MapLeaseManager(final Path workspaceFile, final ModeController modeController,
            final EdtExecutor edt, final ScheduledExecutorService scheduler,
            final MapLoaderOperation loader, final MapViewLookup viewLookup, final MapLookup mapLookup,
            final LeaseCompletionInterceptor completionInterceptor, final SettlementTestHook settlementTestHook) {
        this(workspaceFile, modeController, edt, scheduler, loader, viewLookup, mapLookup, completionInterceptor,
            settlementTestHook, false);
    }

    private MapLeaseManager(final Path workspaceFile, final ModeController modeController,
            final EdtExecutor edt, final ScheduledExecutorService scheduler,
            final MapLoaderOperation loader, final MapViewLookup viewLookup, final MapLookup mapLookup,
            final LeaseCompletionInterceptor completionInterceptor, final SettlementTestHook settlementTestHook,
            final boolean ownsScheduler) {
        this.uriResolver = new WorkspaceUriResolver();
        this.workspaceFile = uriResolver.canonical(Objects.requireNonNull(workspaceFile, "workspaceFile"));
        this.modeController = Objects.requireNonNull(modeController, "modeController");
        this.edt = Objects.requireNonNull(edt, "edt");
        this.scheduler = scheduler;
        this.ownsScheduler = ownsScheduler;
        this.completionInterceptor = completionInterceptor != null ? completionInterceptor
            : NO_OP_COMPLETION_INTERCEPTOR;
        this.settlementTestHook = settlementTestHook != null ? settlementTestHook : NO_OP_SETTLEMENT_TEST_HOOK;
        this.loader = loader != null ? loader : new MapLoaderOperation() {
            @Override
            public MapModel load(final URL canonicalUrl) {
                return new MapLoader(MapLeaseManager.this.modeController).load(canonicalUrl).getMap();
            }
        };
        this.viewLookup = viewLookup != null ? viewLookup : new MapViewLookup() {
            @Override
            public boolean containsView(final MapModel map) {
                final IMapViewManager mapViewManager = MapLeaseManager.this.modeController
                    .getController().getMapViewManager();
                return mapViewManager != null && mapViewManager.containsView(map);
            }
        };

        final MapController controller = edt.call(new Callable<MapController>() {
            @Override
            public MapController call() {
                return MapLeaseManager.this.modeController.getMapController();
            }
        });
        if (!(controller instanceof MMapController)) {
            shutdownOwnedScheduler();
            throw new IllegalArgumentException("Graph map leases require the MindMap map controller");
        }
        this.mapController = (MMapController) controller;
        this.mapLookup = mapLookup != null ? mapLookup : new MapLookup() {
            @Override
            public MapModel get(final URL canonicalUrl) {
                return MapLeaseManager.this.mapController.getMap(canonicalUrl);
            }
        };
        try {
            runOnEdtAndWait(new Runnable() {
                @Override
                public void run() {
                    mapController.addMapLifeCycleListener(lifecycleListener);
                    synchronized (monitor) {
                        lifecycleListenerRegistered = true;
                    }
                }
            });
            if (scheduler != null) {
                externalCheck = scheduler.scheduleWithFixedDelay(new Runnable() {
                    @Override
                    public void run() {
                        scheduleExternalCheckOnEdt();
                    }
                }, EXTERNAL_CHECK_PERIOD_MILLIS, EXTERNAL_CHECK_PERIOD_MILLIS, TimeUnit.MILLISECONDS);
            }
        }
        catch (RuntimeException failure) {
            shutdownOwnedScheduler();
            throw failure;
        }
    }

    public CompletionStage<MapLease> acquire(final MapReference reference) {
        Objects.requireNonNull(reference, "reference");
        final CompletableFuture<MapLease> future = new CompletableFuture<MapLease>();
        Entry entry = null;
        long generation = 0L;
        MapAdapterEvent loadingEvent = null;
        PendingAcquire readyRequest = null;
        Throwable immediateFailure = null;
        boolean scheduleLoad = false;
        try {
            final Path mapPath = uriResolver.resolve(workspaceFile, reference.storedUri());
            final URL canonicalUrl = mapPath.toUri().toURL();
            boolean retryDecision;
            do {
                retryDecision = false;
                entry = null;
                generation = 0L;
                loadingEvent = null;
                readyRequest = null;
                scheduleLoad = false;
                Entry modelNullEntry = null;
                synchronized (monitor) {
                    if (closed) {
                        immediateFailure = new IllegalStateException("Map lease manager is closed");
                    }
                    else {
                        entry = entries.get(reference.id());
                    }
                    if (immediateFailure == null && entry != null) {
                        if (!entry.mapPath.equals(mapPath)) {
                            immediateFailure = new IllegalArgumentException(
                                "A map reference cannot be rebound without Locate");
                        }
                        else if (entry.loading) {
                            addPendingAcquire(entry, future);
                            return future;
                        }
                        else if (entry.model == null) {
                            modelNullEntry = entry;
                        }
                        else {
                            readyRequest = addPendingAcquire(entry, future);
                            reservePendingAcquire(entry, readyRequest, entry.generation);
                        }
                    }
                    else if (immediateFailure == null) {
                        final Entry created = new Entry(reference, mapPath, canonicalUrl);
                        created.loading = true;
                        created.generation = 1L;
                        addPendingAcquire(created, future);
                        entries.put(created.id, created);
                        entry = created;
                        generation = created.generation;
                        loadingEvent = new MapAdapterEvent(created.id, MapOperationalState.LOADING);
                        scheduleLoad = true;
                    }
                }
                if (modelNullEntry != null) {
                    settlementLock.lock();
                    try {
                        synchronized (monitor) {
                            if (closed) {
                                immediateFailure = new IllegalStateException("Map lease manager is closed");
                            }
                            else if (entries.get(modelNullEntry.id) != modelNullEntry
                                    || modelNullEntry.loading || modelNullEntry.model != null) {
                                retryDecision = true;
                            }
                            else {
                                entry = modelNullEntry;
                                entry.loading = true;
                                entry.generation++;
                                generation = entry.generation;
                                addPendingAcquire(entry, future);
                                loadingEvent = new MapAdapterEvent(entry.id, MapOperationalState.LOADING);
                                scheduleLoad = true;
                            }
                        }
                    }
                    finally {
                        releaseSettlementLock();
                    }
                }
            }
            while (retryDecision && immediateFailure == null);
        }
        catch (Exception failure) {
            immediateFailure = failure;
        }

        if (immediateFailure != null) {
            future.completeExceptionally(immediateFailure);
            return future;
        }
        if (readyRequest != null) {
            completeReservedRequest(entry, readyRequest);
            return future;
        }

        if (loadingEvent != null) {
            publish(loadingEvent);
        }
        if (scheduleLoad) {
            scheduleLoad(entry, generation, null);
        }
        return future;
    }

    public void release(final MapReferenceId id) {
        if (id == null) {
            return;
        }
        Detachment detachment = null;
        synchronized (monitor) {
            final Entry entry = entries.get(id);
            if (entry == null || entry.leaseCount == 0) {
                return;
            }
            entry.leaseCount--;
            detachment = removeEntryIfUnused(entry);
        }
        detach(detachment);
    }

    public ListenerRegistration addListener(final MapAdapterListener listener) {
        Objects.requireNonNull(listener, "listener");
        synchronized (monitor) {
            if (closed) {
                return NoOpRegistration.INSTANCE;
            }
            listeners.add(listener);
            return new ListenerRegistrationImpl(this, listener);
        }
    }

    @Override
    public void close() {
        final List<Detachment> detachments = new ArrayList<Detachment>();
        final List<PendingAcquire> pending = new ArrayList<PendingAcquire>();
        ScheduledFuture<?> scheduledCheck;
        boolean removeLifecycle;
        settlementLock.lock();
        try {
            synchronized (monitor) {
                if (closed) {
                    return;
                }
                closed = true;
                scheduledCheck = externalCheck;
                externalCheck = null;
                removeLifecycle = lifecycleListenerRegistered;
                lifecycleListenerRegistered = false;
                for (Entry entry : entries.values()) {
                    entry.generation++;
                    entry.loading = false;
                    for (PendingAcquire request : entry.pending) {
                        request.leaseReserved = false;
                        pending.add(request);
                    }
                    for (PendingAcquire request : entry.settling) {
                        request.leaseReserved = false;
                        pending.add(request);
                    }
                    entry.pending.clear();
                    entry.settling.clear();
                    entry.leaseCount = 0;
                    final Detachment detachment = detachmentFor(entry);
                    if (detachment != null) {
                        detachments.add(detachment);
                    }
                }
                entries.clear();
                listeners.clear();
                suppressedRemovals.clear();
                deferredOperations.clear();
                deferredDrainScheduled = false;
            }
        }
        finally {
            releaseSettlementLock();
        }
        if (scheduledCheck != null) {
            scheduledCheck.cancel(false);
        }
        if (removeLifecycle || !detachments.isEmpty()) {
            runOnEdtAndWait(new Runnable() {
                @Override
                public void run() {
                    if (removeLifecycle) {
                        mapController.removeMapLifeCycleListener(lifecycleListener);
                    }
                    for (Detachment detachment : detachments) {
                        detachOnEdt(detachment);
                    }
                }
            });
        }
        completeExceptionally(pending, new IllegalStateException("Map lease manager is closed"));
        shutdownOwnedScheduler();
    }

    void checkExternalChanges() {
        if (!edt.isEdt()) {
            scheduleExternalCheckOnEdt();
            return;
        }
        final List<Entry> snapshot;
        synchronized (monitor) {
            if (closed) {
                return;
            }
            snapshot = new ArrayList<Entry>(entries.values());
        }
        for (Entry entry : snapshot) {
            checkExternalChangeOnEdt(entry);
        }
    }

    private void checkExternalChangeOnEdt(final Entry entry) {
        final MapModel model;
        synchronized (monitor) {
            if (closed || entries.get(entry.id) != entry || entry.leaseCount == 0
                    || entry.loading || entry.model == null) {
                return;
            }
            model = entry.model;
        }
        final boolean changed;
        try {
            changed = model.hasExternalFileChanged();
        }
        catch (RuntimeException failure) {
            markReloadRequired(entry, model);
            return;
        }
        if (!changed) {
            return;
        }
        final boolean replace;
        try {
            replace = entryManagerOwned(entry, model) && !viewLookup.containsView(model) && model.isSaved();
        }
        catch (RuntimeException failure) {
            markReloadRequired(entry, model);
            return;
        }
        if (replace) {
            reloadManagerOwnedModelOnEdt(entry, model);
        }
        else {
            markReloadRequired(entry, model);
        }
    }

    private boolean entryManagerOwned(final Entry entry, final MapModel model) {
        synchronized (monitor) {
            return !closed && entries.get(entry.id) == entry && entry.model == model
                && entry.managerOwned && entry.leaseCount > 0 && !entry.loading;
        }
    }

    private void reloadManagerOwnedModelOnEdt(final Entry entry, final MapModel oldModel) {
        tryOrDeferSettlementOnEdt(new ExternalReloadOperation(entry, oldModel));
    }

    private ReloadPlan beginManagerOwnedReloadOnEdtLocked(final Entry entry, final MapModel oldModel) {
        synchronized (monitor) {
            if (closed || entries.get(entry.id) != entry || entry.model != oldModel
                    || entry.leaseCount == 0 || entry.loading || !entry.managerOwned) {
                return null;
            }
            final boolean canReload;
            try {
                canReload = !viewLookup.containsView(oldModel) && oldModel.isSaved();
            }
            catch (RuntimeException failure) {
                entry.state = MapOperationalState.RELOAD_REQUIRED;
                return ReloadPlan.reloadRequired(entry);
            }
            if (!canReload) {
                entry.state = MapOperationalState.RELOAD_REQUIRED;
                return ReloadPlan.reloadRequired(entry);
            }
            entry.generation++;
            entry.loading = true;
            entry.state = MapOperationalState.LOADING;
            return new ReloadPlan(entry, oldModel, entry.modelListener, entry.generation);
        }
    }

    private void continueManagerOwnedReloadOnEdt(final ReloadPlan plan) {
        final Entry entry = plan.entry;
        if (plan.reloadRequired) {
            publish(new MapAdapterEvent(entry.id, MapOperationalState.RELOAD_REQUIRED));
            return;
        }
        final MapModel oldModel = plan.oldModel;
        final IMapChangeListener oldListener = plan.oldListener;
        final long generation = plan.generation;
        publish(new MapAdapterEvent(entry.id, MapOperationalState.LOADING));

        final MapAdapterEvent reloadRequiredEvent = revalidateReloadAfterLoading(entry, oldModel, oldListener,
            generation);
        if (reloadRequiredEvent != null) {
            publish(reloadRequiredEvent);
            completePending(entry, generation, null);
            return;
        }
        if (!isCurrentReload(entry, oldModel, oldListener, generation)) {
            synchronized (monitor) {
                suppressedRemovals.remove(oldModel);
            }
            return;
        }
        detachOnEdt(new Detachment(oldModel, oldListener));
        try {
            mapController.closeWithoutSaving(oldModel);
        }
        catch (RuntimeException failure) {
            synchronized (monitor) {
                suppressedRemovals.remove(oldModel);
            }
            restoreAfterFailedClose(entry, oldModel, oldListener, generation);
            return;
        }
        finally {
            synchronized (monitor) {
                suppressedRemovals.remove(oldModel);
            }
        }
        synchronized (monitor) {
            if (!isCurrentReloadLocked(entry, oldModel, oldListener, generation)) {
                return;
            }
            entry.model = null;
            entry.modelListener = null;
            entry.managerOwned = false;
        }
        loadOnEdt(entry, generation, oldModel);
    }


    private MapAdapterEvent revalidateReloadAfterLoading(final Entry entry, final MapModel oldModel,
            final IMapChangeListener oldListener, final long generation) {
        synchronized (monitor) {
            if (!isCurrentReloadEntryLocked(entry, oldModel, oldListener, generation)) {
                return null;
            }
            if (entry.leaseCount == 0) {
                if (!entry.pending.isEmpty()) {
                    entry.loading = false;
                    entry.state = MapOperationalState.RELOAD_REQUIRED;
                    return new MapAdapterEvent(entry.id, MapOperationalState.RELOAD_REQUIRED);
                }
                return null;
            }
            final boolean canReload;
            try {
                canReload = !viewLookup.containsView(oldModel) && oldModel.isSaved();
            }
            catch (RuntimeException failure) {
                return markReloadRequiredLocked(entry, oldModel, oldListener, generation);
            }
            if (!isCurrentReloadLocked(entry, oldModel, oldListener, generation)) {
                return null;
            }
            if (canReload) {
                suppressedRemovals.add(oldModel);
                return null;
            }
            return markReloadRequiredLocked(entry, oldModel, oldListener, generation);
        }
    }

    private MapAdapterEvent markReloadRequiredLocked(final Entry entry, final MapModel oldModel,
            final IMapChangeListener oldListener, final long generation) {
        if (!isCurrentReloadLocked(entry, oldModel, oldListener, generation)) {
            return null;
        }
        entry.loading = false;
        entry.state = MapOperationalState.RELOAD_REQUIRED;
        return new MapAdapterEvent(entry.id, MapOperationalState.RELOAD_REQUIRED);
    }

    private boolean isCurrentReload(final Entry entry, final MapModel oldModel,
            final IMapChangeListener oldListener, final long generation) {
        synchronized (monitor) {
            return isCurrentReloadLocked(entry, oldModel, oldListener, generation);
        }
    }

    private boolean isCurrentReloadLocked(final Entry entry, final MapModel oldModel,
            final IMapChangeListener oldListener, final long generation) {
        return isCurrentReloadEntryLocked(entry, oldModel, oldListener, generation) && entry.leaseCount > 0;
    }

    private boolean isCurrentReloadEntryLocked(final Entry entry, final MapModel oldModel,
            final IMapChangeListener oldListener, final long generation) {
        return !closed && entries.get(entry.id) == entry && entry.generation == generation
            && entry.model == oldModel && entry.modelListener == oldListener && entry.managerOwned
            && entry.loading;
    }

    private void restoreAfterFailedClose(final Entry entry, final MapModel oldModel,
            final IMapChangeListener oldListener, final long generation) {
        final MapAdapterEvent event;
        synchronized (monitor) {
            if (!isCurrentReloadLocked(entry, oldModel, oldListener, generation)) {
                return;
            }
            if (oldListener != null) {
                oldModel.addMapChangeListener(oldListener);
            }
            if (!isCurrentReloadLocked(entry, oldModel, oldListener, generation)) {
                if (oldListener != null) {
                    oldModel.removeMapChangeListener(oldListener);
                }
                return;
            }
            entry.loading = false;
            entry.state = MapOperationalState.RELOAD_REQUIRED;
            event = new MapAdapterEvent(entry.id, MapOperationalState.RELOAD_REQUIRED);
        }
        publish(event);
    }

    private void handleMapRemovedOnEdt(final MapModel map) {
        tryOrDeferSettlementOnEdt(new LifecycleRemovalOperation(map));
    }


    private boolean tryOrDeferSettlementOnEdt(final DeferredOperation operation) {
        if (settlementLock.tryLock()) {
            final Runnable[] continuation = new Runnable[1];
            try {
                continuation[0] = operation.applyOnSettlement();
            }
            finally {
                releaseSettlementLock(continuation[0]);
            }
            return true;
        }
        settlementTestHook.afterFailedSettlementTryLock();
        synchronized (monitor) {
            if (closed) {
                return true;
            }
            deferredOperations.add(operation);
        }
        if (settlementLock.tryLock()) {
            releaseSettlementLock();
        }
        return false;
    }

    private void releaseSettlementLock() {
        releaseSettlementLock(null);
    }

    private void releaseSettlementLock(final Runnable afterRelease) {
        final boolean outermost = settlementLock.getHoldCount() == 1;
        settlementLock.unlock();
        try {
            if (afterRelease != null) {
                afterRelease.run();
            }
        }
        finally {
            if (outermost && !settlementLock.isHeldByCurrentThread()) {
                scheduleDeferredDrainIfNeeded();
            }
        }
    }

    private void scheduleDeferredDrainIfNeeded() {
        synchronized (monitor) {
            if (closed || deferredOperations.isEmpty() || deferredDrainScheduled || deferredDrainRunning) {
                return;
            }
            deferredDrainScheduled = true;
        }
        try {
            edt.execute(new Runnable() {
                @Override
                public void run() {
                    drainDeferredOperationsOnEdt();
                }
            });
        }
        catch (RuntimeException failure) {
            synchronized (monitor) {
                deferredDrainScheduled = false;
                if (closed) {
                    deferredOperations.clear();
                }
            }
        }
    }

    private void drainDeferredOperationsOnEdt() {
        if (!edt.isEdt()) {
            return;
        }
        synchronized (monitor) {
            deferredDrainScheduled = false;
            if (closed) {
                deferredOperations.clear();
                return;
            }
            deferredDrainRunning = true;
        }
        try {
            while (true) {
                final List<DeferredOperation> operations;
                synchronized (monitor) {
                    if (deferredOperations.isEmpty()) {
                        break;
                    }
                    operations = new ArrayList<DeferredOperation>(deferredOperations);
                    deferredOperations.clear();
                }
                for (DeferredOperation operation : operations) {
                    if (!tryOrDeferSettlementOnEdt(operation)) {
                        return;
                    }
                }
            }
        }
        finally {
            synchronized (monitor) {
                deferredDrainRunning = false;
            }
            if (!settlementLock.isLocked()) {
                scheduleDeferredDrainIfNeeded();
            }
        }
    }

    private void handleMapChanged(final Entry entry, final MapModel map, final long generation) {
        final Runnable callback = new Runnable() {
            @Override
            public void run() {
                if (!edt.isEdt()) {
                    edt.execute(callbackRunnable(entry, map, generation));
                    return;
                }
                final MapAdapterEvent event;
                synchronized (monitor) {
                    if (closed || entries.get(entry.id) != entry || entry.generation != generation
                            || entry.model != map || entry.leaseCount == 0 || entry.loading) {
                        return;
                    }
                    final MapOperationalState nextState = entry.state == MapOperationalState.RELOAD_REQUIRED
                        ? MapOperationalState.RELOAD_REQUIRED : stateForModel(map);
                    entry.state = nextState;
                    event = new MapAdapterEvent(entry.id, nextState);
                }
                publish(event);
            }
        };
        if (edt.isEdt()) {
            callback.run();
        }
        else {
            edt.execute(callback);
        }
    }

    private Runnable callbackRunnable(final Entry entry, final MapModel map, final long generation) {
        return new Runnable() {
            @Override
            public void run() {
                handleMapChanged(entry, map, generation);
            }
        };
    }

    private void loadOnEdt(final Entry entry, final long generation, final MapModel replacedModel) {
        if (!edt.isEdt()) {
            edt.execute(new Runnable() {
                @Override
                public void run() {
                    loadOnEdt(entry, generation, replacedModel);
                }
            });
            return;
        }
        synchronized (monitor) {
            if (closed || entries.get(entry.id) != entry || entry.generation != generation || !entry.loading) {
                return;
            }
        }
        MapModel model = null;
        IMapChangeListener listener = null;
        boolean managerOwned = false;
        MapOperationalState state;
        Throwable failure = null;
        try {
            final MapModel alreadyLoaded = mapLookup.get(entry.canonicalUrl);
            if (alreadyLoaded != null) {
                model = alreadyLoaded;
                managerOwned = false;
            }
            else {
                model = loader.load(entry.canonicalUrl);
                managerOwned = model != null;
            }
            if (model == null) {
                state = stateForLoadFailure(entry.mapPath, null);
            }
            else if (model == replacedModel) {
                failure = new IllegalStateException("Map loader returned the released model");
                state = MapOperationalState.UNREADABLE;
            }
            else {
                listener = listenerFor(entry, model, generation);
                state = stateForModel(model);
                if (!attachListenerForCurrentLoad(entry, generation, model, listener)) {
                    return;
                }
            }
        }
        catch (RuntimeException exception) {
            failure = exception;
            state = stateForLoadFailure(entry.mapPath, exception);
        }
        catch (Exception exception) {
            failure = exception;
            state = stateForLoadFailure(entry.mapPath, exception);
        }
        finishLoad(entry, generation, model, listener, managerOwned, state, failure);
    }

    private boolean attachListenerForCurrentLoad(final Entry entry, final long generation, final MapModel model,
            final IMapChangeListener listener) {
        synchronized (monitor) {
            if (closed || entries.get(entry.id) != entry || entry.generation != generation || !entry.loading) {
                return false;
            }
            model.addMapChangeListener(listener);
            return true;
        }
    }

    private void finishLoad(final Entry entry, final long generation, final MapModel model,
            final IMapChangeListener listener, final boolean managerOwned,
            final MapOperationalState state, final Throwable failure) {
        boolean stale = false;
        synchronized (monitor) {
            if (closed || entries.get(entry.id) != entry || entry.generation != generation || !entry.loading) {
                stale = true;
            }
            else {
                entry.model = model;
                entry.modelListener = listener;
                entry.managerOwned = managerOwned && model != null;
                entry.state = state;
                entry.loading = false;
            }
        }
        if (stale) {
            if (model != null && listener != null) {
                model.removeMapChangeListener(listener);
            }
            return;
        }
        publish(new MapAdapterEvent(entry.id, state));
        completePending(entry, generation, failure);
    }

    private void completePending(final Entry entry, final long generation, final Throwable failure) {
        final List<PendingAcquire> requests;
        Detachment detachment = null;
        synchronized (monitor) {
            if (closed || entries.get(entry.id) != entry || entry.generation != generation) {
                return;
            }
            requests = new ArrayList<PendingAcquire>(entry.pending);
            for (PendingAcquire request : requests) {
                if (!request.future.isCancelled()) {
                    reservePendingAcquire(entry, request, generation);
                }
                else {
                    entry.pending.remove(request);
                }
            }
            detachment = removeEntryIfUnused(entry);
        }
        detach(detachment);
        for (PendingAcquire request : requests) {
            if (!request.leaseReserved) {
                continue;
            }
            completeReservedRequest(entry, request);
        }
    }

    private void completeReservedRequest(final Entry entry, final PendingAcquire request) {
        if (!isCurrentSettlement(entry, request)) {
            invalidateReservedRequestWhenOpen(entry, request);
            return;
        }
        completionInterceptor.beforeComplete(request.future);
        final boolean attempted;
        final boolean delivered;
        settlementLock.lock();
        try {
            attempted = isCurrentSettlement(entry, request);
            delivered = attempted && request.future.complete(new LeaseImpl(this, entry));
        }
        finally {
            releaseSettlementLock();
        }
        if (attempted) {
            settleReservedRequest(entry, request, delivered);
        }
        else {
            invalidateReservedRequestWhenOpen(entry, request);
        }
    }

    private void invalidateReservedRequestWhenOpen(final Entry entry, final PendingAcquire request) {
        synchronized (monitor) {
            if (closed) {
                return;
            }
        }
        invalidateReservedRequest(entry, request);
    }

    private boolean isCurrentSettlement(final Entry entry, final PendingAcquire request) {
        synchronized (monitor) {
            return !closed && entries.get(entry.id) == entry && entry.generation == request.generation
                && request.leaseReserved && entry.settling.contains(request);
        }
    }

    private void invalidateReservedRequest(final Entry entry, final PendingAcquire request) {
        final boolean delivered;
        settlementLock.lock();
        try {
            request.future.completeExceptionally(new IllegalStateException("Map lease acquisition was invalidated"));
            delivered = !request.future.isCompletedExceptionally();
        }
        finally {
            releaseSettlementLock();
        }
        settleReservedRequest(entry, request, delivered);
    }

    private void completeExceptionally(final List<PendingAcquire> requests, final Throwable failure) {
        settlementLock.lock();
        try {
            for (PendingAcquire request : requests) {
                request.future.completeExceptionally(failure);
            }
        }
        finally {
            releaseSettlementLock();
        }
    }

    private PendingAcquire addPendingAcquire(final Entry entry, final CompletableFuture<MapLease> future) {
        final PendingAcquire request = new PendingAcquire(future);
        entry.pending.add(request);
        future.whenComplete((value, failure) -> {
            if (future.isCancelled()) {
                cancelPending(entry, request);
            }
        });
        return request;
    }

    private void reservePendingAcquire(final Entry entry, final PendingAcquire request,
            final long generation) {
        if (!entry.pending.remove(request)) {
            throw new IllegalStateException("Map lease request is not pending");
        }
        entry.leaseCount++;
        request.generation = generation;
        request.leaseReserved = true;
        entry.settling.add(request);
    }

    private void cancelPending(final Entry entry, final PendingAcquire request) {
        Detachment detachment = null;
        synchronized (monitor) {
            if (entry.pending.remove(request) && entry.leaseCount == 0 && entries.get(entry.id) == entry) {
                detachment = removeEntryIfUnused(entry);
            }
        }
        detach(detachment);
    }

    private void settleReservedRequest(final Entry entry, final PendingAcquire request,
            final boolean delivered) {
        Detachment detachment = null;
        synchronized (monitor) {
            if (!request.leaseReserved) {
                return;
            }
            request.leaseReserved = false;
            entry.settling.remove(request);
            if (!delivered) {
                entry.leaseCount--;
            }
            detachment = removeEntryIfUnused(entry);
        }
        detach(detachment);
    }

    private void scheduleLoad(final Entry entry, final long generation, final MapModel replacedModel) {
        try {
            edt.execute(new Runnable() {
                @Override
                public void run() {
                    loadOnEdt(entry, generation, replacedModel);
                }
            });
        }
        catch (RuntimeException failure) {
            finishLoad(entry, generation, null, null, false,
                stateForLoadFailure(entry.mapPath, failure), failure);
        }
    }

    private void scheduleExternalCheckOnEdt() {
        try {
            edt.execute(new Runnable() {
                @Override
                public void run() {
                    checkExternalChanges();
                }
            });
        }
        catch (RuntimeException ignored) {
            // A closing manager deliberately rejects queued work.
        }
    }

    private void markReloadRequired(final Entry entry, final MapModel model) {
        final MapAdapterEvent event;
        synchronized (monitor) {
            if (closed || entries.get(entry.id) != entry || entry.model != model || entry.leaseCount == 0) {
                return;
            }
            entry.state = MapOperationalState.RELOAD_REQUIRED;
            event = new MapAdapterEvent(entry.id, MapOperationalState.RELOAD_REQUIRED);
        }
        publish(event);
    }

    private IMapChangeListener listenerFor(final Entry entry, final MapModel model, final long generation) {
        return new IMapChangeListener() {
            @Override
            public void mapChanged(final org.freeplane.features.map.MapChangeEvent event) {
                handleMapChanged(entry, model, generation);
            }

            @Override
            public void onNodeDeleted(final org.freeplane.features.map.NodeDeletionEvent event) {
                handleMapChanged(entry, model, generation);
            }

            @Override
            public void onNodeInserted(final NodeModel parent, final NodeModel child, final int index) {
                handleMapChanged(entry, model, generation);
            }

            @Override
            public void onNodeMoved(final org.freeplane.features.map.NodeMoveEvent event) {
                handleMapChanged(entry, model, generation);
            }

            @Override
            public void onPreNodeMoved(final org.freeplane.features.map.NodeMoveEvent event) {
                handleMapChanged(entry, model, generation);
            }

            @Override
            public void onPreNodeDelete(final org.freeplane.features.map.NodeDeletionEvent event) {
                handleMapChanged(entry, model, generation);
            }
        };
    }

    private MapOperationalState stateForModel(final MapModel model) {
        final NodeModel root = model.getRootNode();
        if (root == null) {
            return MapOperationalState.UNREADABLE;
        }
        final EncryptionModel encryption = EncryptionModel.getModel(root);
        return encryption != null && encryption.isLocked()
            ? MapOperationalState.PASSWORD_REQUIRED : MapOperationalState.AVAILABLE;
    }

    private MapOperationalState stateForLoadFailure(final Path mapPath, final Throwable failure) {
        if (!Files.exists(mapPath)) {
            return MapOperationalState.MISSING;
        }
        if (failure instanceof FileNotFoundException && !Files.isReadable(mapPath)) {
            return MapOperationalState.UNREADABLE;
        }
        if (hasMissingCause(failure) && !Files.exists(mapPath)) {
            return MapOperationalState.MISSING;
        }
        return MapOperationalState.UNREADABLE;
    }

    private boolean hasMissingCause(final Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof java.nio.file.NoSuchFileException || current instanceof FileNotFoundException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void publish(final MapAdapterEvent event) {
        final List<MapAdapterListener> snapshot;
        synchronized (monitor) {
            if (closed) {
                return;
            }
            snapshot = new ArrayList<MapAdapterListener>(listeners);
        }
        for (MapAdapterListener listener : snapshot) {
            synchronized (monitor) {
                if (closed) {
                    return;
                }
            }
            try {
                listener.onMapAdapterEvent(event);
            }
            catch (RuntimeException ignored) {
                // Listener failures must not prevent later listeners or state transitions.
            }
        }
    }

    private Detachment detachmentFor(final Entry entry) {
        final Detachment detachment = entry.model != null && entry.modelListener != null
            ? new Detachment(entry.model, entry.modelListener) : null;
        entry.model = null;
        entry.modelListener = null;
        entry.managerOwned = false;
        return detachment;
    }

    private Detachment removeEntryIfUnused(final Entry entry) {
        // No request can be invalidated here because both request collections must already be empty.
        if (entry.leaseCount != 0 || !entry.pending.isEmpty() || !entry.settling.isEmpty()
                || entries.get(entry.id) != entry) {
            return null;
        }
        entries.remove(entry.id);
        entry.generation++;
        entry.loading = false;
        return detachmentFor(entry);
    }

    private void detach(final Detachment detachment) {
        if (detachment == null) {
            return;
        }
        runOnEdtAndWait(new Runnable() {
            @Override
            public void run() {
                detachOnEdt(detachment);
            }
        });
    }

    private void detachOnEdt(final Detachment detachment) {
        if (detachment.model != null && detachment.listener != null) {
            detachment.model.removeMapChangeListener(detachment.listener);
        }
    }

    private void removeListener(final MapAdapterListener listener) {
        synchronized (monitor) {
            listeners.remove(listener);
        }
    }

    private void runOnEdtAndWait(final Runnable task) {
        edt.call(new Callable<Void>() {
            @Override
            public Void call() {
                task.run();
                return null;
            }
        });
    }

    private void shutdownOwnedScheduler() {
        if (ownsScheduler && scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private static ScheduledExecutorService newDefaultScheduler() {
        return Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(final Runnable runnable) {
                final Thread thread = new Thread(runnable, "freeplane-graph-map-lease-check");
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    private final class LifecycleListener implements IMapLifeCycleListener {
        @Override
        public void onRemove(final MapModel map) {
            if (edt.isEdt()) {
                handleMapRemovedOnEdt(map);
            }
            else {
                try {
                    edt.execute(new Runnable() {
                        @Override
                        public void run() {
                            handleMapRemovedOnEdt(map);
                        }
                    });
                }
                catch (RuntimeException ignored) {
                    // Manager close invalidates lifecycle callbacks.
                }
            }
        }
    }

    private interface DeferredOperation {
        Runnable applyOnSettlement();
    }

    private final class LifecycleRemovalOperation implements DeferredOperation {
        private final MapModel map;

        private LifecycleRemovalOperation(final MapModel map) {
            this.map = map;
        }

        @Override
        public Runnable applyOnSettlement() {
            final List<Reacquisition> reacquisitions = new ArrayList<Reacquisition>();
            synchronized (monitor) {
                if (closed || suppressedRemovals.remove(map)) {
                    return null;
                }
                for (Entry entry : entries.values()) {
                    if (entry.model == map && entry.leaseCount > 0 && !entry.loading) {
                        final IMapChangeListener listener = entry.modelListener;
                        entry.model = null;
                        entry.modelListener = null;
                        entry.managerOwned = false;
                        entry.loading = true;
                        entry.generation++;
                        entry.state = MapOperationalState.LOADING;
                        reacquisitions.add(new Reacquisition(entry, entry.generation, map, listener));
                    }
                }
            }
            return new Runnable() {
                @Override
                public void run() {
                    for (Reacquisition reacquisition : reacquisitions) {
                        detachOnEdt(new Detachment(map, reacquisition.listener));
                        publish(new MapAdapterEvent(reacquisition.entry.id, MapOperationalState.LOADING));
                        loadOnEdt(reacquisition.entry, reacquisition.generation, map);
                    }
                }
            };
        }
    }

    private final class ExternalReloadOperation implements DeferredOperation {
        private final Entry entry;
        private final MapModel oldModel;

        private ExternalReloadOperation(final Entry entry, final MapModel oldModel) {
            this.entry = entry;
            this.oldModel = oldModel;
        }

        @Override
        public Runnable applyOnSettlement() {
            final ReloadPlan plan = beginManagerOwnedReloadOnEdtLocked(entry, oldModel);
            return plan == null ? null : new Runnable() {
                @Override
                public void run() {
                    continueManagerOwnedReloadOnEdt(plan);
                }
            };
        }
    }

    private static final class ReloadPlan {
        private final Entry entry;
        private final MapModel oldModel;
        private final IMapChangeListener oldListener;
        private final long generation;
        private final boolean reloadRequired;

        private ReloadPlan(final Entry entry, final MapModel oldModel,
                final IMapChangeListener oldListener, final long generation) {
            this.entry = entry;
            this.oldModel = oldModel;
            this.oldListener = oldListener;
            this.generation = generation;
            this.reloadRequired = false;
        }

        private static ReloadPlan reloadRequired(final Entry entry) {
            return new ReloadPlan(entry, true);
        }

        private ReloadPlan(final Entry entry, final boolean reloadRequired) {
            this.entry = entry;
            this.oldModel = null;
            this.oldListener = null;
            this.generation = 0L;
            this.reloadRequired = reloadRequired;
        }
    }
    private static final class Entry {
        private final MapReferenceId id;
        private final MapReference reference;
        private final Path mapPath;
        private final URL canonicalUrl;
        private final List<PendingAcquire> pending = new ArrayList<PendingAcquire>();
        private final List<PendingAcquire> settling = new ArrayList<PendingAcquire>();
        private MapOperationalState state = MapOperationalState.LOADING;
        private MapModel model;
        private IMapChangeListener modelListener;
        private boolean managerOwned;
        private boolean loading;
        private int leaseCount;
        private long generation;

        private Entry(final MapReference reference, final Path mapPath, final URL canonicalUrl) {
            this.id = reference.id();
            this.reference = reference;
            this.mapPath = mapPath;
            this.canonicalUrl = canonicalUrl;
        }
    }

    private static final class PendingAcquire {
        private final CompletableFuture<MapLease> future;
        private boolean leaseReserved;
        private long generation;

        private PendingAcquire(final CompletableFuture<MapLease> future) {
            this.future = future;
        }
    }

    private static final class Detachment {
        private final MapModel model;
        private final IMapChangeListener listener;

        private Detachment(final MapModel model, final IMapChangeListener listener) {
            this.model = model;
            this.listener = listener;
        }
    }

    private static final class Reacquisition {
        private final Entry entry;
        private final long generation;
        @SuppressWarnings("unused")
        private final MapModel oldModel;
        private final IMapChangeListener listener;

        private Reacquisition(final Entry entry, final long generation, final MapModel oldModel,
                final IMapChangeListener listener) {
            this.entry = entry;
            this.generation = generation;
            this.oldModel = oldModel;
            this.listener = listener;
        }
    }

    private static final class LeaseImpl implements MapLease {
        private final MapLeaseManager manager;
        private final Entry entry;
        private final AtomicBoolean closed = new AtomicBoolean();

        private LeaseImpl(final MapLeaseManager manager, final Entry entry) {
            this.manager = manager;
            this.entry = entry;
        }

        @Override
        public MapReferenceId mapReferenceId() {
            return entry.id;
        }

        @Override
        public MapOperationalState state() {
            synchronized (manager.monitor) {
                return entry.state;
            }
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                manager.release(entry.id);
            }
        }
    }

    private static final class ListenerRegistrationImpl implements ListenerRegistration {
        private final MapLeaseManager manager;
        private final MapAdapterListener listener;
        private final AtomicBoolean closed = new AtomicBoolean();

        private ListenerRegistrationImpl(final MapLeaseManager manager, final MapAdapterListener listener) {
            this.manager = manager;
            this.listener = listener;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                manager.removeListener(listener);
            }
        }
    }

    private static final class NoOpRegistration implements ListenerRegistration {
        private static final NoOpRegistration INSTANCE = new NoOpRegistration();

        @Override
        public void close() {
        }
    }

    private static final class SwingEdtExecutor implements EdtExecutor {
        @Override
        public <T> T call(final Callable<T> task) {
            Objects.requireNonNull(task, "task");
            if (isEdt()) {
                return callOnCurrentThread(task);
            }
            final CompletableFuture<T> result = new CompletableFuture<T>();
            try {
                SwingUtilities.invokeAndWait(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            result.complete(task.call());
                        }
                        catch (Exception failure) {
                            result.completeExceptionally(failure);
                        }
                    }
                });
            }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while executing on the EDT", interrupted);
            }
            catch (Exception failure) {
                throw new IllegalStateException("Unable to execute on the EDT", failure);
            }
            try {
                return result.get();
            }
            catch (Exception failure) {
                throw new IllegalStateException("EDT task failed", unwrap(failure));
            }
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

        @Override
        public boolean isEdt() {
            return SwingUtilities.isEventDispatchThread();
        }

        private static <T> T callOnCurrentThread(final Callable<T> task) {
            try {
                return task.call();
            }
            catch (Exception failure) {
                throw new IllegalStateException("EDT task failed", failure);
            }
        }

        private static Throwable unwrap(final Exception failure) {
            if (failure instanceof java.util.concurrent.ExecutionException && failure.getCause() != null) {
                return failure.getCause();
            }
            return failure;
        }
    }
}
