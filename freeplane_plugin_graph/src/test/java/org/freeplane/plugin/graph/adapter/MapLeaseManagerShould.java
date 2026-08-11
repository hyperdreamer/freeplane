package org.freeplane.plugin.graph.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.freeplane.core.util.Compat;
import org.freeplane.features.map.EncryptionModel;
import org.freeplane.features.map.IMapChangeListener;
import org.freeplane.features.map.IMapLifeCycleListener;
import org.freeplane.features.map.INodeDuplicator;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.mindmapmode.MMapController;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.features.ui.IMapViewManager;
import org.freeplane.features.url.mindmapmode.MapLoader;
import org.freeplane.main.application.ApplicationResourceController;
import org.freeplane.main.application.CommandLineParser;
import org.freeplane.main.headlessmode.FreeplaneHeadlessStarter;
import org.freeplane.plugin.graph.workspace.ListenerRegistration;
import org.freeplane.plugin.graph.workspace.model.MapReference;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MapLeaseManagerShould {
    private static final String MAP_COLOR = "#4E79A7";

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final List<MapLeaseManager> managers = new ArrayList<MapLeaseManager>();

    @After
    public void closeManagers() {
        for (MapLeaseManager manager : managers) {
            manager.close();
        }
    }

    @Test
    public void coalescesConcurrentLoadsAndReusesTheCanonicalViewlessModel() throws Exception {
        TestEdt edt = new TestEdt();
        TestEnvironment environment = environment(edt);
        AtomicInteger loadCount = new AtomicInteger();
        MapLeaseManager manager = manager(environment, edt, url -> {
            assertThat(edt.isEdt()).isTrue();
            loadCount.incrementAndGet();
            return environment.newMap(url);
        });
        List<MapAdapterEvent> events = eventsFrom(manager);

        CompletionStage<MapLease> first = manager.acquire(environment.reference());
        CompletionStage<MapLease> second = manager.acquire(environment.reference());

        assertThat(loadCount).hasValue(0);
        edt.runAll();

        MapLease firstLease = first.toCompletableFuture().get(1, TimeUnit.SECONDS);
        MapLease secondLease = second.toCompletableFuture().get(1, TimeUnit.SECONDS);
        assertThat(loadCount).hasValue(1);
        assertThat(firstLease.mapReferenceId()).isEqualTo(environment.reference().id());
        assertThat(secondLease.mapReferenceId()).isEqualTo(firstLease.mapReferenceId());
        assertThat(firstLease.state()).isEqualTo(MapOperationalState.AVAILABLE);
        assertThat(secondLease.state()).isEqualTo(MapOperationalState.AVAILABLE);
        assertThat(environment.lookup(environment.url())).isSameAs(environment.model);
        assertThat(environment.visibleModels).isEmpty();
        assertThat(events).extracting(MapAdapterEvent::state)
            .containsExactly(MapOperationalState.LOADING, MapOperationalState.AVAILABLE);

        CompletionStage<MapLease> reused = manager.acquire(environment.reference());
        MapLease reusedLease = reused.toCompletableFuture().get(1, TimeUnit.SECONDS);
        assertThat(loadCount).hasValue(1);
        assertThat(reusedLease.state()).isEqualTo(MapOperationalState.AVAILABLE);
    }

    @Test
    public void releasesEachIndependentLeaseAndDetachesTheFinalModelListenerWithoutClosingTheMap() throws Exception {
        TestEdt edt = new TestEdt();
        TestEnvironment environment = environment(edt);
        MapLeaseManager manager = manager(environment, edt, environment::newMap);
        MapLease first = acquire(manager, environment, edt);
        MapLease second = acquire(manager, environment, edt);

        assertThat(environment.model.listenerCount()).isEqualTo(1);
        first.close();
        first.close();
        assertThat(environment.model.listenerCount()).isEqualTo(1);
        manager.release(environment.reference().id());
        assertThat(environment.model.listenerCount()).isZero();
        assertThat(environment.closeCount).hasValue(0);

        int eventCount = environment.events.size();
        environment.model.fireChange();
        edt.runAll();
        assertThat(environment.events).hasSize(eventCount);
        second.close();
    }

    @Test
    public void publishesLoadingThenMissingAndStillReturnsARecoverableLease() throws Exception {
        TestEdt edt = new TestEdt();
        TestEnvironment environment = environment(edt);
        Files.delete(environment.mapPath);
        MapLeaseManager manager = manager(environment, edt, url -> {
            throw new FileNotFoundException(environment.mapPath.toString());
        });
        List<MapAdapterEvent> events = eventsFrom(manager);

        MapLease lease = acquire(manager, environment, edt);

        assertThat(lease.state()).isEqualTo(MapOperationalState.MISSING);
        assertThat(events).extracting(MapAdapterEvent::state)
            .containsExactly(MapOperationalState.LOADING, MapOperationalState.MISSING);
    }

    @Test
    public void publishesLoadingThenUnreadableForAReadableFileThatCannotBeParsed() throws Exception {
        TestEdt edt = new TestEdt();
        TestEnvironment environment = environment(edt);
        MapLeaseManager manager = manager(environment, edt, url -> {
            throw new IOException("parse failure");
        });
        List<MapAdapterEvent> events = eventsFrom(manager);

        MapLease lease = acquire(manager, environment, edt);

        assertThat(lease.state()).isEqualTo(MapOperationalState.UNREADABLE);
        assertThat(events).extracting(MapAdapterEvent::state)
            .containsExactly(MapOperationalState.LOADING, MapOperationalState.UNREADABLE);
    }

    @Test
    public void publishesPasswordRequiredWithoutPromptingWhenTheLoadedRootIsLocked() throws Exception {
        TestEdt edt = new TestEdt();
        TestEnvironment environment = environment(edt);
        MapLeaseManager manager = manager(environment, edt, url -> {
            FakeMapModel locked = environment.newMap(url);
            locked.root.addExtension(new EncryptionModel(locked.root, "ciphertext"));
            return locked;
        });
        List<MapAdapterEvent> events = eventsFrom(manager);

        MapLease lease = acquire(manager, environment, edt);

        assertThat(lease.state()).isEqualTo(MapOperationalState.PASSWORD_REQUIRED);
        assertThat(events).extracting(MapAdapterEvent::state)
            .containsExactly(MapOperationalState.LOADING, MapOperationalState.PASSWORD_REQUIRED);
    }

    @Test
    public void reusesAVisibleCleanEditorModelAsAuthoritativeAndNeverReplacesIt() throws Exception {
        TestEdt edt = new TestEdt();
        TestEnvironment environment = environment(edt);
        environment.installExisting(environment.newMap(environment.url()));
        environment.visibleModels.add(environment.model);
        AtomicInteger loadCount = new AtomicInteger();
        MapLeaseManager manager = manager(environment, edt, url -> {
            loadCount.incrementAndGet();
            throw new AssertionError("editor-owned model must not be loaded");
        });
        List<MapAdapterEvent> events = eventsFrom(manager);

        MapLease lease = acquire(manager, environment, edt);
        environment.model.markExternalChange();
        manager.checkExternalChanges();
        edt.runAll();

        assertThat(lease.state()).isEqualTo(MapOperationalState.RELOAD_REQUIRED);
        assertThat(loadCount).hasValue(0);
        assertThat(environment.closeCount).hasValue(0);
        assertThat(environment.model).isSameAs(environment.lookup(environment.url()));
        assertThat(events).extracting(MapAdapterEvent::state)
            .containsExactly(MapOperationalState.LOADING, MapOperationalState.AVAILABLE,
                MapOperationalState.RELOAD_REQUIRED);
    }

    @Test
    public void keepsAnUnsavedEditorModelAsAuthoritativeAndNeverReplacesIt() throws Exception {
        TestEdt edt = new TestEdt();
        TestEnvironment environment = environment(edt);
        FakeMapModel editorModel = environment.newMap(environment.url());
        environment.installExisting(editorModel);
        environment.visibleModels.add(editorModel);
        editorModel.markDirty();
        AtomicInteger loadCount = new AtomicInteger();
        MapLeaseManager manager = manager(environment, edt, url -> {
            loadCount.incrementAndGet();
            throw new AssertionError("unsaved editor model must not be loaded");
        });

        MapLease lease = acquire(manager, environment, edt);
        editorModel.markExternalChange();
        manager.checkExternalChanges();
        edt.runAll();

        assertThat(lease.state()).isEqualTo(MapOperationalState.RELOAD_REQUIRED);
        assertThat(loadCount).hasValue(0);
        assertThat(environment.closeCount).hasValue(0);
        assertThat(environment.lookup(environment.url())).isSameAs(editorModel);
    }

    @Test
    public void reacquiresViewlesslyAfterTheLastEditorViewCloses() throws Exception {
        TestEdt edt = new TestEdt();
        TestEnvironment environment = environment(edt);
        FakeMapModel editorModel = environment.newMap(environment.url());
        environment.installExisting(editorModel);
        environment.visibleModels.add(editorModel);
        AtomicInteger loadCount = new AtomicInteger();
        MapLeaseManager manager = manager(environment, edt, url -> {
            loadCount.incrementAndGet();
            return environment.newMap(url);
        });
        List<MapAdapterEvent> events = eventsFrom(manager);
        MapLease lease = acquire(manager, environment, edt);

        environment.visibleModels.remove(editorModel);
        environment.removeExternally(editorModel);
        edt.runAll();

        assertThat(lease.state()).isEqualTo(MapOperationalState.AVAILABLE);
        assertThat(loadCount).hasValue(1);
        assertThat(environment.closeCount).hasValue(0);
        assertThat(environment.model).isNotSameAs(editorModel);
        assertThat(environment.model.listenerCount()).isEqualTo(1);
        assertThat(events).extracting(MapAdapterEvent::state)
            .containsExactly(MapOperationalState.LOADING, MapOperationalState.AVAILABLE,
                MapOperationalState.LOADING, MapOperationalState.AVAILABLE);
    }

    @Test
    public void reloadsOneCleanManagerOwnedViewlessModelAfterAnExternalChange() throws Exception {
        TestEdt edt = new TestEdt();
        TestEnvironment environment = environment(edt);
        MapLeaseManager manager = manager(environment, edt, environment::newMap);
        List<MapAdapterEvent> events = eventsFrom(manager);
        MapLease lease = acquire(manager, environment, edt);
        FakeMapModel oldModel = environment.model;
        oldModel.markExternalChange();

        manager.checkExternalChanges();
        edt.runAll();

        assertThat(lease.state()).isEqualTo(MapOperationalState.AVAILABLE);
        assertThat(environment.closeCount).hasValue(1);
        assertThat(environment.loadCount).hasValue(2);
        assertThat(environment.model).isNotSameAs(oldModel);
        assertThat(environment.model.listenerCount()).isEqualTo(1);
        int afterReload = events.size();
        int oldListenerNotifications = oldModel.listenerNotificationCount();
        oldModel.fireChange();
        edt.runAll();
        assertThat(events).hasSize(afterReload);
        assertThat(oldModel.listenerNotificationCount()).isEqualTo(oldListenerNotifications);
        assertThat(oldModel.listenerCount()).isZero();
        environment.model.fireChange();
        edt.runAll();
        assertThat(events).hasSize(afterReload + 1);
        assertThat(events.get(events.size() - 1).state()).isEqualTo(MapOperationalState.AVAILABLE);
        assertThat(events).extracting(MapAdapterEvent::state)
            .containsExactly(MapOperationalState.LOADING, MapOperationalState.AVAILABLE,
                MapOperationalState.LOADING, MapOperationalState.AVAILABLE, MapOperationalState.AVAILABLE);
    }

    @Test
    public void keepsTheDirtyModelWhenALoadingListenerDirtiesItDuringExternalReload() throws Exception {
        TestEdt edt = new TestEdt();
        TestEnvironment environment = environment(edt);
        MapLeaseManager manager = manager(environment, edt, environment::newMap);
        List<MapAdapterEvent> events = eventsFrom(manager);
        MapLease lease = acquire(manager, environment, edt);
        FakeMapModel oldModel = environment.model;
        manager.addListener(event -> {
            if (event.state() == MapOperationalState.LOADING) {
                oldModel.markDirty();
            }
        });
        oldModel.markExternalChange();

        manager.checkExternalChanges();
        edt.runAll();

        assertThat(lease.state()).isEqualTo(MapOperationalState.RELOAD_REQUIRED);
        assertThat(environment.closeCount).hasValue(0);
        assertThat(environment.loadCount).hasValue(1);
        assertThat(environment.model).isSameAs(oldModel);
        assertThat(oldModel.listenerCount()).isEqualTo(1);
        assertThat(events).extracting(MapAdapterEvent::state)
            .containsExactly(MapOperationalState.LOADING, MapOperationalState.AVAILABLE,
                MapOperationalState.LOADING, MapOperationalState.RELOAD_REQUIRED);
    }

    @Test
    public void keepsTheEditorVisibleModelWhenALoadingListenerOpensItDuringExternalReload() throws Exception {
        TestEdt edt = new TestEdt();
        TestEnvironment environment = environment(edt);
        MapLeaseManager manager = manager(environment, edt, environment::newMap);
        List<MapAdapterEvent> events = eventsFrom(manager);
        MapLease lease = acquire(manager, environment, edt);
        FakeMapModel oldModel = environment.model;
        manager.addListener(event -> {
            if (event.state() == MapOperationalState.LOADING) {
                environment.visibleModels.add(oldModel);
            }
        });
        oldModel.markExternalChange();

        manager.checkExternalChanges();
        edt.runAll();

        assertThat(lease.state()).isEqualTo(MapOperationalState.RELOAD_REQUIRED);
        assertThat(environment.closeCount).hasValue(0);
        assertThat(environment.loadCount).hasValue(1);
        assertThat(environment.model).isSameAs(oldModel);
        assertThat(oldModel.listenerCount()).isEqualTo(1);
        assertThat(events).extracting(MapAdapterEvent::state)
            .containsExactly(MapOperationalState.LOADING, MapOperationalState.AVAILABLE,
                MapOperationalState.LOADING, MapOperationalState.RELOAD_REQUIRED);
    }

    @Test
    public void doesNotCloseTheModelWhenALoadingListenerReleasesTheLastLeaseDuringExternalReload()
            throws Exception {
        TestEdt edt = new TestEdt();
        TestEnvironment environment = environment(edt);
        MapLeaseManager manager = manager(environment, edt, environment::newMap);
        List<MapAdapterEvent> events = eventsFrom(manager);
        MapLease lease = acquire(manager, environment, edt);
        FakeMapModel oldModel = environment.model;
        manager.addListener(event -> {
            if (event.state() == MapOperationalState.LOADING) {
                lease.close();
            }
        });
        oldModel.markExternalChange();

        manager.checkExternalChanges();
        edt.runAll();

        assertThat(environment.closeCount).hasValue(0);
        assertThat(environment.loadCount).hasValue(1);
        assertThat(oldModel.listenerCount()).isZero();
        assertThat(events).extracting(MapAdapterEvent::state)
            .containsExactly(MapOperationalState.LOADING, MapOperationalState.AVAILABLE,
                MapOperationalState.LOADING);
    }

    @Test
    public void doesNotCloseTheModelWhenALoadingListenerClosesTheManagerDuringExternalReload()
            throws Exception {
        TestEdt edt = new TestEdt();
        TestEnvironment environment = environment(edt);
        MapLeaseManager manager = manager(environment, edt, environment::newMap);
        List<MapAdapterEvent> events = eventsFrom(manager);
        MapLease lease = acquire(manager, environment, edt);
        FakeMapModel oldModel = environment.model;
        manager.addListener(event -> {
            if (event.state() == MapOperationalState.LOADING) {
                manager.close();
            }
        });
        oldModel.markExternalChange();

        manager.checkExternalChanges();
        edt.runAll();

        assertThat(environment.closeCount).hasValue(0);
        assertThat(environment.loadCount).hasValue(1);
        assertThat(oldModel.listenerCount()).isZero();
        assertThat(environment.lifecycleListeners).isEmpty();
        assertThat(events).extracting(MapAdapterEvent::state)
            .containsExactly(MapOperationalState.LOADING, MapOperationalState.AVAILABLE,
                MapOperationalState.LOADING);
        assertThat(manager.acquire(environment.reference()).toCompletableFuture().isCompletedExceptionally()).isTrue();
        lease.close();
    }

    @Test
    public void checksExternalChangesThroughTheScheduledPath() throws Exception {
        TestEdt edt = new TestEdt();
        TestEnvironment environment = environment(edt);
        AtomicReference<Runnable> externalCheckTask = new AtomicReference<Runnable>();
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> scheduledCheck = mock(ScheduledFuture.class);
        when(scheduler.scheduleWithFixedDelay(any(Runnable.class), org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong(), any(TimeUnit.class))).thenAnswer(invocation -> {
                externalCheckTask.set(invocation.getArgument(0));
                return scheduledCheck;
            });
        MapLeaseManager manager = new MapLeaseManager(environment.workspace, environment.modeController, edt,
            scheduler, environment::newMap, environment::containsView, environment::lookup);
        managers.add(manager);
        MapLease lease = acquire(manager, environment, edt);
        FakeMapModel oldModel = environment.model;
        oldModel.markExternalChange();

        assertThat(externalCheckTask.get()).isNotNull();
        externalCheckTask.get().run();
        edt.runAll();

        assertThat(lease.state()).isEqualTo(MapOperationalState.AVAILABLE);
        assertThat(environment.closeCount).hasValue(1);
        assertThat(environment.model).isNotSameAs(oldModel);
        manager.close();
        verify(scheduledCheck).cancel(false);
    }

    @Test
    public void keepsDirtyManagerOwnedModelAndReportsReloadRequiredInsteadOfClosingIt() throws Exception {
        TestEdt edt = new TestEdt();
        TestEnvironment environment = environment(edt);
        MapLeaseManager manager = manager(environment, edt, environment::newMap);
        MapLease lease = acquire(manager, environment, edt);
        environment.model.markDirty();
        environment.model.markExternalChange();

        manager.checkExternalChanges();
        edt.runAll();

        assertThat(lease.state()).isEqualTo(MapOperationalState.RELOAD_REQUIRED);
        assertThat(environment.closeCount).hasValue(0);
        assertThat(environment.loadCount).hasValue(1);
        assertThat(environment.model.listenerCount()).isEqualTo(1);
    }

    @Test
    public void keepsAnEditorVisibleManagerOwnedModelAndReportsReloadRequired() throws Exception {
        TestEdt edt = new TestEdt();
        TestEnvironment environment = environment(edt);
        MapLeaseManager manager = manager(environment, edt, environment::newMap);
        MapLease lease = acquire(manager, environment, edt);
        environment.visibleModels.add(environment.model);
        environment.model.markExternalChange();

        manager.checkExternalChanges();
        edt.runAll();

        assertThat(lease.state()).isEqualTo(MapOperationalState.RELOAD_REQUIRED);
        assertThat(environment.closeCount).hasValue(0);
        assertThat(environment.loadCount).hasValue(1);
    }

    @Test
    public void ordersListenerEventsAndAllowsSelfRemovalWithoutBlockingOtherListeners() throws Exception {
        TestEdt edt = new TestEdt();
        TestEnvironment environment = environment(edt);
        MapLeaseManager manager = manager(environment, edt, environment::newMap);
        List<String> calls = new ArrayList<String>();
        final ListenerRegistration[] firstRegistration = new ListenerRegistration[1];
        firstRegistration[0] = manager.addListener(event -> {
            calls.add("first:" + event.state());
            firstRegistration[0].close();
        });
        manager.addListener(event -> calls.add("second:" + event.state()));
        acquire(manager, environment, edt);

        environment.model.fireChange();
        edt.runAll();

        assertThat(calls).containsExactly("first:LOADING", "second:LOADING",
            "second:AVAILABLE", "second:AVAILABLE");
    }

    @Test
    public void removesAllListenersAndPendingChecksOnCloseAndNeverPublishesAfterward() throws Exception {
        TestEdt edt = new TestEdt();
        TestEnvironment environment = environment(edt);
        MapLeaseManager manager = manager(environment, edt, environment::newMap);
        List<MapAdapterEvent> events = eventsFrom(manager);
        MapLease lease = acquire(manager, environment, edt);
        ListenerRegistration registration = manager.addListener(event -> events.add(event));
        manager.close();
        manager.close();

        assertThat(environment.model.listenerCount()).isZero();
        assertThat(environment.lifecycleListeners).isEmpty();
        registration.close();
        lease.close();
        environment.model.fireChange();
        environment.removeExternally(environment.model);
        edt.runAll();
        assertThat(events).extracting(MapAdapterEvent::state)
            .containsExactly(MapOperationalState.LOADING, MapOperationalState.AVAILABLE);
    }

    @Test
    public void cancelsAnInFlightAcquireWhenTheManagerCloses() throws Exception {
        TestEdt edt = new TestEdt();
        TestEnvironment environment = environment(edt);
        AtomicInteger loadCount = new AtomicInteger();
        MapLeaseManager manager = manager(environment, edt, url -> {
            loadCount.incrementAndGet();
            return environment.newMap(url);
        });

        CompletionStage<MapLease> pending = manager.acquire(environment.reference());
        manager.close();
        edt.runAll();

        assertThat(pending.toCompletableFuture().isCompletedExceptionally()).isTrue();
        assertThat(loadCount).hasValue(0);
        assertThat(environment.lifecycleListeners).isEmpty();
    }

    @Test
    public void doesNotCancelAPendingAcquireWhenReleaseIsCalledBeforeItsLeaseIsReturned() throws Exception {
        TestEdt edt = new TestEdt();
        TestEnvironment environment = environment(edt);
        MapLeaseManager manager = manager(environment, edt, environment::newMap);

        CompletionStage<MapLease> pending = manager.acquire(environment.reference());
        manager.release(environment.reference().id());

        assertThat(pending.toCompletableFuture().isCancelled()).isFalse();
        edt.runAll();

        MapLease lease = pending.toCompletableFuture().get(1, TimeUnit.SECONDS);
        assertThat(lease.state()).isEqualTo(MapOperationalState.AVAILABLE);
        assertThat(environment.model.listenerCount()).isEqualTo(1);
        lease.close();
        assertThat(environment.model.listenerCount()).isZero();
    }

    @Test
    public void discardsTheQueuedLoadWhenItsOnlyPendingAcquireIsCancelled() throws Exception {
        TestEdt edt = new TestEdt();
        TestEnvironment environment = environment(edt);
        MapLeaseManager manager = manager(environment, edt, environment::newMap);
        List<MapAdapterEvent> events = eventsFrom(manager);

        CompletionStage<MapLease> pending = manager.acquire(environment.reference());
        assertThat(pending.toCompletableFuture().cancel(false)).isTrue();
        int eventsAtCancellation = events.size();
        edt.runAll();

        assertThat(pending.toCompletableFuture().isCancelled()).isTrue();
        assertThat(environment.loadCount).hasValue(0);
        assertThat(environment.totalModelListenerCount()).isZero();
        assertThat(events).hasSize(eventsAtCancellation);
    }

    @Test
    public void keepsTheOtherCoalescedAcquireWhenOnePendingAcquireIsCancelled() throws Exception {
        TestEdt edt = new TestEdt();
        TestEnvironment environment = environment(edt);
        MapLeaseManager manager = manager(environment, edt, environment::newMap);

        CompletionStage<MapLease> cancelled = manager.acquire(environment.reference());
        CompletionStage<MapLease> surviving = manager.acquire(environment.reference());
        assertThat(cancelled.toCompletableFuture().cancel(false)).isTrue();
        edt.runAll();

        assertThat(cancelled.toCompletableFuture().isCancelled()).isTrue();
        MapLease lease = surviving.toCompletableFuture().get(1, TimeUnit.SECONDS);
        assertThat(lease.state()).isEqualTo(MapOperationalState.AVAILABLE);
        assertThat(environment.loadCount).hasValue(1);
        lease.close();
        assertThat(environment.model.listenerCount()).isZero();
    }

    @Test
    public void rollsBackTheReservedLeaseCountWhenCompletionLosesToCancellation() throws Exception {
        TestEdt edt = new TestEdt();
        TestEnvironment environment = environment(edt);
        AtomicBoolean cancelFirstCompletion = new AtomicBoolean(true);
        MapLeaseManager manager = manager(environment, edt, environment::newMap, future -> {
            if (cancelFirstCompletion.compareAndSet(true, false)) {
                future.cancel(false);
            }
        });
        List<MapAdapterEvent> events = eventsFrom(manager);

        CompletionStage<MapLease> cancelled = manager.acquire(environment.reference());
        edt.runAll();

        assertThat(cancelled.toCompletableFuture().isCancelled()).isTrue();
        assertThat(environment.model.listenerCount()).isZero();

        CompletionStage<MapLease> fresh = manager.acquire(environment.reference());
        assertThat(fresh.toCompletableFuture().isDone()).isFalse();
        edt.runAll();

        MapLease lease = fresh.toCompletableFuture().get(1, TimeUnit.SECONDS);
        assertThat(lease.state()).isEqualTo(MapOperationalState.AVAILABLE);
        assertThat(environment.model.listenerCount()).isEqualTo(1);
        assertThat(events).extracting(MapAdapterEvent::state)
            .containsExactly(MapOperationalState.LOADING, MapOperationalState.AVAILABLE,
                MapOperationalState.LOADING, MapOperationalState.AVAILABLE);
        lease.close();
        assertThat(environment.model.listenerCount()).isZero();
    }

    @Test
    public void loadsTheRealFixtureThroughMapLoaderOnTheSuppliedEdtWithoutCreatingAView() throws Exception {
        try (HeadlessResourceScope resources = new HeadlessResourceScope()) {
            FreeplaneHeadlessStarter starter = new FreeplaneHeadlessStarter(CommandLineParser.parse());
            MapLeaseManager manager = null;
            try {
                Controller controller = starter.createController();
                starter.createModeControllers(controller);
                starter.createFrame();
                ModeController modeController = controller.getModeController(MModeController.MODENAME);
                TestEdt edt = new TestEdt();
                Path workspace = temporaryFolder.newFile("integration.fpg").toPath();
                Path map = workspace.resolveSibling("graph-projection.mm");
                try (InputStream input = getClass().getResourceAsStream("/maps/graph-projection.mm")) {
                    if (input == null) {
                        throw new IOException("Missing graph projection fixture");
                    }
                    Files.copy(input, map, StandardCopyOption.REPLACE_EXISTING);
                }
                MapReference reference = reference(workspace, map);
                AtomicInteger loaderCalls = new AtomicInteger();
                manager = new MapLeaseManager(workspace, modeController, edt, null, url -> {
                    assertThat(edt.isEdt()).isTrue();
                    loaderCalls.incrementAndGet();
                    return new MapLoader(modeController).load(url).getMap();
                }, model -> controller.getMapViewManager().containsView(model));
                managers.add(manager);

                MapLease first = acquire(manager, reference, edt);
                MapLease second = acquire(manager, reference, edt);
                URL canonicalUrl = map.toRealPath().toUri().toURL();
                MapModel loaded = edt.call(() -> ((MMapController) modeController.getMapController()).getMap(canonicalUrl));

                assertThat(loaderCalls).hasValue(1);
                assertThat(first.state()).isEqualTo(MapOperationalState.AVAILABLE);
                assertThat(second.state()).isEqualTo(MapOperationalState.AVAILABLE);
                assertThat(loaded).isNotNull();
                assertThat(controller.getMapViewManager().containsView(loaded)).isFalse();
                assertThat(edt.boundaryCalls).isGreaterThan(0);
                first.close();
                second.close();
                edt.call(() -> {
                    ((MMapController) modeController.getMapController()).closeWithoutSaving(loaded);
                    return null;
                });
            }
            finally {
                if (manager != null) {
                    manager.close();
                }
                starter.stop();
            }
        }
    }

    private MapLease acquire(MapLeaseManager manager, TestEnvironment environment, TestEdt edt) throws Exception {
        return acquire(manager, environment.reference(), edt);
    }

    private MapLease acquire(MapLeaseManager manager, MapReference reference, TestEdt edt) throws Exception {
        CompletionStage<MapLease> stage = manager.acquire(reference);
        edt.runAll();
        return stage.toCompletableFuture().get(1, TimeUnit.SECONDS);
    }

    private MapLeaseManager manager(TestEnvironment environment, TestEdt edt,
            MapLeaseManager.MapLoaderOperation loader) {
        MapLeaseManager manager = new MapLeaseManager(environment.workspace, environment.modeController, edt,
            (ScheduledExecutorService) null, loader, environment::containsView, environment::lookup);
        managers.add(manager);
        return manager;
    }

    private MapLeaseManager manager(TestEnvironment environment, TestEdt edt,
            MapLeaseManager.MapLoaderOperation loader, MapLeaseManager.LeaseCompletionInterceptor completionInterceptor) {
        MapLeaseManager manager = new MapLeaseManager(environment.workspace, environment.modeController, edt,
            (ScheduledExecutorService) null, loader, environment::containsView, environment::lookup,
            completionInterceptor);
        managers.add(manager);
        return manager;
    }

    private static List<MapAdapterEvent> eventsFrom(MapLeaseManager manager) {
        List<MapAdapterEvent> events = new ArrayList<MapAdapterEvent>();
        manager.addListener(events::add);
        return events;
    }

    private MapReference reference(Path workspace, Path map) {
        return MapReference.of(MapReferenceId.of(UUID.randomUUID()), 1, new org.freeplane.plugin.graph.workspace.WorkspaceUriResolver()
            .toStoredUri(workspace, map), true, MAP_COLOR, Collections.emptyList());
    }

    private TestEnvironment environment(TestEdt edt) throws Exception {
        Path workspace = temporaryFolder.newFile("workspace-" + UUID.randomUUID() + ".fpg").toPath();
        Path map = workspace.resolveSibling("map-" + UUID.randomUUID() + ".mm");
        Files.write(map, Collections.singletonList("fixture"));
        return new TestEnvironment(workspace, map, edt);
    }

    private static final class TestEnvironment {
        private final Path workspace;
        private final Path mapPath;
        private final ModeController modeController = mock(ModeController.class);
        private final MMapController mapController = mock(MMapController.class);
        private final Controller controller = mock(Controller.class);
        private final IMapViewManager viewManager = mock(IMapViewManager.class);
        private final Map<URL, MapModel> loaded = new java.util.HashMap<URL, MapModel>();
        private final List<IMapLifeCycleListener> lifecycleListeners = new ArrayList<IMapLifeCycleListener>();
        private final List<FakeMapModel> createdModels = new ArrayList<FakeMapModel>();
        private final Set<MapModel> visibleModels = Collections.newSetFromMap(new IdentityHashMap<MapModel, Boolean>());
        private final AtomicInteger loadCount = new AtomicInteger();
        private final AtomicInteger closeCount = new AtomicInteger();
        private FakeMapModel model;

        private TestEnvironment(Path workspace, Path mapPath, TestEdt edt) throws Exception {
            this.workspace = workspace;
            this.mapPath = mapPath;
            when(modeController.getMapController()).thenReturn(mapController);
            when(modeController.getController()).thenReturn(controller);
            when(controller.getMapViewManager()).thenReturn(viewManager);
            when(viewManager.containsView(any(MapModel.class))).thenAnswer(invocation ->
                visibleModels.contains(invocation.getArgument(0)));
            doAnswer(invocation -> {
                lifecycleListeners.add(invocation.getArgument(0));
                return null;
            }).when(mapController).addMapLifeCycleListener(any(IMapLifeCycleListener.class));
            doAnswer(invocation -> {
                lifecycleListeners.remove(invocation.getArgument(0));
                return null;
            }).when(mapController).removeMapLifeCycleListener(any(IMapLifeCycleListener.class));
            doAnswer(invocation -> {
                closeCount.incrementAndGet();
                MapModel closing = invocation.getArgument(0);
                loaded.remove(closing.getURL());
                notifyRemoved(closing);
                return null;
            }).when(mapController).closeWithoutSaving(any(MapModel.class));
        }

        private URL url() throws Exception {
            return mapPath.toRealPath().toUri().toURL();
        }

        private MapReference reference() throws Exception {
            return MapReference.of(MapReferenceId.of(UUID.nameUUIDFromBytes(mapPath.toString().getBytes("UTF-8"))),
                1, new org.freeplane.plugin.graph.workspace.WorkspaceUriResolver().toStoredUri(workspace, mapPath),
                true, MAP_COLOR, Collections.emptyList());
        }

        private FakeMapModel newMap(URL url) {
            loadCount.incrementAndGet();
            FakeMapModel result = new FakeMapModel(url);
            createdModels.add(result);
            model = result;
            loaded.put(url, result);
            return result;
        }

        private void installExisting(FakeMapModel existing) {
            model = existing;
            loaded.put(existing.getURL(), existing);
        }

        private boolean containsView(MapModel map) {
            return visibleModels.contains(map);
        }

        private MapModel lookup(URL url) {
            return loaded.get(url);
        }

        private void removeExternally(MapModel map) {
            loaded.remove(map.getURL());
            notifyRemoved(map);
        }

        private void notifyRemoved(MapModel map) {
            for (IMapLifeCycleListener listener : new ArrayList<IMapLifeCycleListener>(lifecycleListeners)) {
                listener.onRemove(map);
            }
        }

        private int totalModelListenerCount() {
            int count = 0;
            for (FakeMapModel model : createdModels) {
                count += model.listenerCount();
            }
            return count;
        }

        private final List<MapAdapterEvent> events = new ArrayList<MapAdapterEvent>();
    }

    private static final class FakeMapModel extends MapModel {
        private final URL url;
        private final NodeModel root;
        private final List<IMapChangeListener> listeners = new ArrayList<IMapChangeListener>();
        private boolean externalChange;
        private int listenerNotifications;

        private FakeMapModel(URL url) {
            super(new INodeDuplicator() {
                @Override
                public NodeModel duplicate(NodeModel source, MapModel targetMap, boolean withChildren) {
                    return null;
                }
            }, null, null);
            this.url = url;
            root = new NodeModel("root", this);
            setRoot(root);
            setURL(url);
            setSaved(true);
        }

        @Override
        public URL getURL() {
            return url;
        }

        @Override
        public void addMapChangeListener(IMapChangeListener listener) {
            listeners.add(listener);
        }

        @Override
        public void removeMapChangeListener(IMapChangeListener listener) {
            listeners.remove(listener);
        }

        @Override
        public boolean hasExternalFileChanged() {
            boolean result = externalChange;
            externalChange = false;
            return result;
        }

        private void markExternalChange() {
            externalChange = true;
        }

        private void markDirty() {
            setSaved(false);
        }

        private int listenerCount() {
            return listeners.size();
        }

        private int listenerNotificationCount() {
            return listenerNotifications;
        }

        private void fireChange() {
            for (IMapChangeListener listener : new ArrayList<IMapChangeListener>(listeners)) {
                listenerNotifications++;
                listener.mapChanged(null);
            }
        }
    }

    private static final class HeadlessResourceScope implements AutoCloseable {
        private final String previousGlobalResourceDirectory;
        private final String previousResourceBaseDirectory;
        private final String previousInstallationBaseDirectory;
        private final Path testProperties;
        private final byte[] previousProperties;
        private final Path testVersionProperties;
        private final byte[] previousVersionProperties;
        private final Path testPreferences;
        private final byte[] previousPreferences;

        private HeadlessResourceScope() throws Exception {
            URL fixtureUrl = MapLeaseManagerShould.class.getResource("/maps/graph-projection.mm");
            if (fixtureUrl == null) {
                throw new IOException("Missing graph projection fixture");
            }
            Path testResourceDirectory = Paths.get(fixtureUrl.toURI()).getParent().getParent();
            Path projectDirectory = testResourceDirectory.getParent().getParent().getParent().getParent();
            Path viewerResources = projectDirectory.resolve("freeplane/build/resources/viewer");
            Path viewerProperties = viewerResources.resolve("freeplane.properties");
            Path viewerVersionProperties = viewerResources.resolve("version.properties");
            Path externalPreferences = projectDirectory.resolve("freeplane/src/external/resources/xml/preferences.xml");
            if (!Files.isRegularFile(viewerProperties) || !Files.isRegularFile(viewerVersionProperties)
                    || !Files.isRegularFile(externalPreferences)) {
                throw new IOException("Missing Freeplane viewer resources");
            }
            testProperties = testResourceDirectory.resolve("freeplane.properties");
            previousProperties = Files.exists(testProperties) ? Files.readAllBytes(testProperties) : null;
            Files.copy(viewerProperties, testProperties, StandardCopyOption.REPLACE_EXISTING);
            testVersionProperties = testResourceDirectory.resolve("version.properties");
            previousVersionProperties = Files.exists(testVersionProperties)
                ? Files.readAllBytes(testVersionProperties) : null;
            Files.copy(viewerVersionProperties, testVersionProperties, StandardCopyOption.REPLACE_EXISTING);
            testPreferences = testResourceDirectory.resolve("xml/preferences.xml");
            Files.createDirectories(testPreferences.getParent());
            previousPreferences = Files.exists(testPreferences) ? Files.readAllBytes(testPreferences) : null;
            Files.copy(externalPreferences, testPreferences, StandardCopyOption.REPLACE_EXISTING);

            previousGlobalResourceDirectory = System.getProperty("org.freeplane.globalresourcedir");
            System.setProperty("org.freeplane.globalresourcedir", viewerResources.toString());
            previousResourceBaseDirectory = ApplicationResourceController.RESOURCE_BASE_DIRECTORY;
            previousInstallationBaseDirectory = ApplicationResourceController.INSTALLATION_BASE_DIRECTORY;
            ApplicationResourceController.RESOURCE_BASE_DIRECTORY = viewerResources.toString();
            ApplicationResourceController.INSTALLATION_BASE_DIRECTORY = viewerResources.getParent().toString();
            Compat.setIsApplet(false);
        }

        @Override
        public void close() throws IOException {
            if (previousGlobalResourceDirectory == null) {
                System.clearProperty("org.freeplane.globalresourcedir");
            }
            else {
                System.setProperty("org.freeplane.globalresourcedir", previousGlobalResourceDirectory);
            }
            ApplicationResourceController.RESOURCE_BASE_DIRECTORY = previousResourceBaseDirectory;
            ApplicationResourceController.INSTALLATION_BASE_DIRECTORY = previousInstallationBaseDirectory;
            if (previousProperties == null) {
                Files.deleteIfExists(testProperties);
            }
            else {
                Files.write(testProperties, previousProperties);
            }
            if (previousVersionProperties == null) {
                Files.deleteIfExists(testVersionProperties);
            }
            else {
                Files.write(testVersionProperties, previousVersionProperties);
            }
            if (previousPreferences == null) {
                Files.deleteIfExists(testPreferences);
            }
            else {
                Files.write(testPreferences, previousPreferences);
            }
        }
    }

    private static final class TestEdt implements EdtExecutor {
        private final Deque<Runnable> queued = new ArrayDeque<Runnable>();
        private boolean onEdt;
        private int boundaryCalls;

        @Override
        public <T> T call(Callable<T> task) {
            boolean wasOnEdt = onEdt;
            onEdt = true;
            boundaryCalls++;
            try {
                return task.call();
            }
            catch (Exception exception) {
                throw new RuntimeException(exception);
            }
            finally {
                onEdt = wasOnEdt;
            }
        }

        @Override
        public void execute(Runnable task) {
            queued.add(task);
        }

        @Override
        public boolean isEdt() {
            return onEdt;
        }

        private void runAll() {
            while (!queued.isEmpty()) {
                Runnable task = queued.removeFirst();
                call(() -> {
                    task.run();
                    return null;
                });
            }
        }
    }
}
