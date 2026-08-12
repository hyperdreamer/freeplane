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
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Delayed;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

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
    public void completesAReentrantAcquireWhenTheLastLeaseIsReleasedDuringExternalReload() throws Exception {
        TestEdt edt = new TestEdt();
        TestEnvironment environment = environment(edt);
        MapLeaseManager manager = manager(environment, edt, environment::newMap);
        List<MapAdapterEvent> events = eventsFrom(manager);
        MapLease initialLease = acquire(manager, environment, edt);
        MapReference reference = environment.reference();
        FakeMapModel oldModel = environment.model;
        IMapChangeListener oldListener = oldModel.listeners.get(0);
        AtomicReference<CompletionStage<MapLease>> reentrantAcquire = new AtomicReference<CompletionStage<MapLease>>();
        manager.addListener(event -> {
            if (event.state() == MapOperationalState.LOADING) {
                reentrantAcquire.set(manager.acquire(reference));
                initialLease.close();
            }
        });
        oldModel.markExternalChange();

        manager.checkExternalChanges();
        edt.runAll();

        CompletionStage<MapLease> reacquisition = reentrantAcquire.get();
        assertThat(reacquisition).isNotNull();
        assertThat(reacquisition.toCompletableFuture().isDone()).isTrue();
        MapLease reacquiredLease = reacquisition.toCompletableFuture().get(1, TimeUnit.SECONDS);
        assertThat(environment.closeCount).hasValue(0);
        assertThat(environment.loadCount).hasValue(1);
        assertThat(reacquiredLease.state()).isEqualTo(MapOperationalState.RELOAD_REQUIRED);
        assertThat(environment.model).isSameAs(oldModel);
        assertThat(oldModel.listeners).containsExactly(oldListener);
        assertThat(events).extracting(MapAdapterEvent::state)
            .containsExactly(MapOperationalState.LOADING, MapOperationalState.AVAILABLE,
                MapOperationalState.LOADING, MapOperationalState.RELOAD_REQUIRED);

        reacquiredLease.close();

        assertThat(oldModel.listenerCount()).isZero();
        int eventsAfterTeardown = events.size();
        oldModel.fireChange();
        edt.runAll();
        assertThat(events).hasSize(eventsAfterTeardown);
    }

    @Test
    public void rejectsAReentrantAcquireWhenManagerCloseWinsDuringSettlement() throws Exception {
        TestEdt edt = new TestEdt();
        TestEnvironment environment = environment(edt);
        AtomicBoolean closeDuringSettlement = new AtomicBoolean();
        AtomicReference<MapLeaseManager> managerReference = new AtomicReference<MapLeaseManager>();
        MapLeaseManager manager = manager(environment, edt, environment::newMap, future -> {
            if (closeDuringSettlement.get()) {
                managerReference.get().close();
            }
        });
        managerReference.set(manager);
        List<MapAdapterEvent> events = eventsFrom(manager);
        MapLease initialLease = acquire(manager, environment, edt);
        MapReference reference = environment.reference();
        FakeMapModel oldModel = environment.model;
        AtomicReference<CompletionStage<MapLease>> reentrantAcquire = new AtomicReference<CompletionStage<MapLease>>();
        manager.addListener(event -> {
            if (event.state() == MapOperationalState.LOADING) {
                reentrantAcquire.set(manager.acquire(reference));
                initialLease.close();
                closeDuringSettlement.set(true);
            }
        });
        oldModel.markExternalChange();

        manager.checkExternalChanges();
        edt.runAll();

        CompletableFuture<MapLease> reentrantFuture = reentrantAcquire.get().toCompletableFuture();
        assertThat(manager.acquire(reference).toCompletableFuture().isCompletedExceptionally()).isTrue();
        assertThat(reentrantFuture.isDone()).isTrue();
        assertThat(reentrantFuture.isCompletedExceptionally()).isTrue();
        assertThat(environment.closeCount).hasValue(0);
        assertThat(environment.loadCount).hasValue(1);
        assertThat(environment.lifecycleListeners).isEmpty();
        assertThat(oldModel.listenerCount()).isZero();
        int eventCountAfterClose = events.size();
        oldModel.fireChange();
        edt.runAll();
        assertThat(events).hasSize(eventCountAfterClose);
    }

    @Test
    public void rollsBackAnImmediateAcquireInvalidatedWhileWaitingForSettlement() throws Exception {
        TestEdt edt = new TestEdt();
        TestEnvironment environment = environment(edt);
        MapLeaseManager manager = manager(environment, edt, environment::newMap);
        List<MapAdapterEvent> events = eventsFrom(manager);
        MapReference firstReference = environment.reference();
        MapLease firstLease = acquire(manager, firstReference, edt);
        FakeMapModel oldModel = environment.model;
        Path secondMap = environment.workspace.resolveSibling("map-" + UUID.randomUUID() + ".mm");
        Files.write(secondMap, Collections.singletonList("fixture"));
        MapReference secondReference = reference(environment.workspace, secondMap);
        AtomicReference<CompletableFuture<MapLease>> immediateAcquire =
            new AtomicReference<CompletableFuture<MapLease>>();
        AtomicReference<Thread> acquireThread = new AtomicReference<Thread>();
        AtomicReference<Throwable> acquireFailure = new AtomicReference<Throwable>();
        AtomicBoolean acquireReturned = new AtomicBoolean();
        CountDownLatch acquireStarted = new CountDownLatch(1);

        CompletableFuture<MapLease> secondAcquire = manager.acquire(secondReference).toCompletableFuture();
        CompletableFuture<MapLease> settlementProbe = secondAcquire.whenComplete((lease, failure) -> {
            Thread operation = new Thread(() -> {
                acquireStarted.countDown();
                try {
                    immediateAcquire.set(manager.acquire(firstReference).toCompletableFuture());
                }
                catch (Throwable exception) {
                    acquireFailure.set(exception);
                }
                finally {
                    acquireReturned.set(true);
                }
            }, "map-lease-immediate-settlement-probe");
            acquireThread.set(operation);
            operation.start();
            try {
                assertThat(acquireStarted.await(1, TimeUnit.SECONDS)).isTrue();
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while starting the immediate acquire probe", exception);
            }
            awaitOperationReturnOrLockWait(operation, acquireReturned);
            assertThat(acquireReturned).isFalse();
            environment.removeExternally(oldModel);
        });

        edt.runAll();

        MapLease secondLease = secondAcquire.get(1, TimeUnit.SECONDS);
        settlementProbe.get(1, TimeUnit.SECONDS);
        Thread operation = acquireThread.get();
        assertThat(operation).isNotNull();
        operation.join(1_000L);
        assertThat(operation.isAlive()).isFalse();
        assertThat(acquireFailure.get()).isNull();
        CompletableFuture<MapLease> invalidated = immediateAcquire.get();
        assertThat(invalidated).isNotNull();
        assertThat(invalidated.isCompletedExceptionally()).isTrue();
        FakeMapModel replacement = environment.model;
        assertThat(replacement).isNotSameAs(oldModel);

        firstLease.close();
        secondLease.close();

        assertThat(oldModel.listenerCount()).isZero();
        assertThat(replacement.listenerCount()).isZero();
        int eventCountAfterTeardown = events.size();
        oldModel.fireChange();
        replacement.fireChange();
        edt.runAll();
        assertThat(events).hasSize(eventCountAfterTeardown);
    }

    @Test
    public void serializesLifecycleInvalidationWithImmediateLeaseDelivery() throws Exception {
        TestEdt edt = new TestEdt();
        TestEnvironment environment = environment(edt);
        DeliveryRaceProbe probe = new DeliveryRaceProbe();
        MapLeaseManager manager = manager(environment, edt, environment::newMap, probe);
        List<MapAdapterEvent> events = eventsFrom(manager);
        MapReference reference = environment.reference();
        MapLease firstLease = acquire(manager, reference, edt);
        FakeMapModel oldModel = environment.model;

        probe.arm(() -> {
            environment.removeExternally(oldModel);
            edt.runAll();
        }, "Lifecycle invalidation", () -> {
            assertThat(environment.model).isSameAs(oldModel);
            assertThat(oldModel.listenerCount()).isEqualTo(1);
        }, true);
        CompletionStage<MapLease> immediateAcquire = manager.acquire(reference);

        assertThat(probe.observedFuture()).isSameAs(immediateAcquire.toCompletableFuture());
        probe.awaitCompletionAndOperation();
        edt.runAll();
        MapLease immediateLease = immediateAcquire.toCompletableFuture().get(1, TimeUnit.SECONDS);
        FakeMapModel replacement = environment.model;
        assertThat(replacement).isNotSameAs(oldModel);
        assertThat(environment.loadCount).hasValue(2);
        assertThat(firstLease.state()).isEqualTo(MapOperationalState.AVAILABLE);
        assertThat(immediateLease.state()).isEqualTo(MapOperationalState.AVAILABLE);
        assertThat(immediateLease.mapReferenceId()).isEqualTo(reference.id());
        assertThat(oldModel.listenerCount()).isZero();
        assertThat(replacement.listenerCount()).isEqualTo(1);
        assertThat(events).extracting(MapAdapterEvent::state)
            .containsExactly(MapOperationalState.LOADING, MapOperationalState.AVAILABLE,
                MapOperationalState.LOADING, MapOperationalState.AVAILABLE);

        int eventCount = events.size();
        oldModel.fireChange();
        edt.runAll();
        assertThat(events).hasSize(eventCount);

        firstLease.close();
        assertThat(replacement.listenerCount()).isEqualTo(1);
        immediateLease.close();
        assertThat(replacement.listenerCount()).isZero();
        replacement.fireChange();
        edt.runAll();
        assertThat(events).hasSize(eventCount);
    }

    @Test
    public void serializesExternalReloadWithImmediateLeaseDelivery() throws Exception {
        TestEdt edt = new TestEdt();
        TestEnvironment environment = environment(edt);
        DeliveryRaceProbe probe = new DeliveryRaceProbe();
        MapLeaseManager manager = manager(environment, edt, environment::newMap, probe);
        List<MapAdapterEvent> events = eventsFrom(manager);
        MapReference reference = environment.reference();
        MapLease firstLease = acquire(manager, reference, edt);
        FakeMapModel oldModel = environment.model;

        probe.arm(() -> {
            oldModel.markExternalChange();
            manager.checkExternalChanges();
            edt.runAll();
        }, "External reload", () -> {
            assertThat(environment.closeCount)
                .as("External reload reached close before lease delivery left the settlement boundary")
                .hasValue(0);
            assertThat(environment.model).isSameAs(oldModel);
            assertThat(oldModel.listenerCount()).isEqualTo(1);
        }, true);
        CompletionStage<MapLease> immediateAcquire = manager.acquire(reference);

        assertThat(probe.observedFuture()).isSameAs(immediateAcquire.toCompletableFuture());
        probe.awaitCompletionAndOperation();
        edt.runAll();
        MapLease immediateLease = immediateAcquire.toCompletableFuture().get(1, TimeUnit.SECONDS);
        FakeMapModel replacement = environment.model;
        assertThat(replacement).isNotSameAs(oldModel);
        assertThat(environment.loadCount).hasValue(2);
        assertThat(firstLease.state()).isEqualTo(MapOperationalState.AVAILABLE);
        assertThat(immediateLease.state()).isEqualTo(MapOperationalState.AVAILABLE);
        assertThat(environment.closeCount).hasValue(1);
        assertThat(environment.loadCount).hasValue(2);
        assertThat(oldModel.listenerCount()).isZero();
        assertThat(replacement.listenerCount()).isEqualTo(1);

        int eventCount = events.size();
        oldModel.fireChange();
        edt.runAll();
        assertThat(events).hasSize(eventCount);
        replacement.fireChange();
        edt.runAll();
        assertThat(events).hasSize(eventCount + 1);

        firstLease.close();
        assertThat(replacement.listenerCount()).isEqualTo(1);
        immediateLease.close();
        assertThat(replacement.listenerCount()).isZero();
        int eventCountAfterTeardown = events.size();
        replacement.fireChange();
        edt.runAll();
        assertThat(events).hasSize(eventCountAfterTeardown);
    }

    @Test
    public void completesLifecycleCallbackCloseWithoutBlockingTheDetectingEdt() throws Exception {
        DetectingEdt edt = new DetectingEdt();
        MapLeaseManager manager = null;
        try {
            TestEnvironment environment = environment(edt);
            AtomicReference<MapLeaseManager> managerReference = new AtomicReference<MapLeaseManager>();
            CallbackCloseProbe probe = new CallbackCloseProbe(managerReference);
            manager = manager(environment, edt, environment::newMap, probe);
            managerReference.set(manager);
            MapLease initialLease = acquire(manager, environment, edt);
            FakeMapModel oldModel = environment.model;
            DetectingEdt.TaskObservation observation = edt.expectNextTask();
            probe.arm(() -> environment.removeExternally(oldModel), observation);

            CompletableFuture<MapLease> immediate = manager.acquire(environment.reference()).toCompletableFuture();

            probe.awaitCallbackAndOperation();
            assertThat(probe.callbackFailure()).isNull();
            assertThat(probe.waitingAtBoundary()).isFalse();
            assertThat(probe.operationReturned()).isTrue();
            assertThat(immediate.isDone()).isTrue();
            assertThat(immediate.isCompletedExceptionally()).isFalse();
            assertThat(environment.lifecycleListeners).isEmpty();
            assertThat(oldModel.listenerCount()).isZero();
            initialLease.close();
            immediate.get(1, TimeUnit.SECONDS).close();
        }
        finally {
            if (manager != null) {
                manager.close();
            }
            edt.close();
        }
    }

    @Test
    public void completesExternalReloadCallbackCloseWithoutBlockingTheDetectingEdt() throws Exception {
        DetectingEdt edt = new DetectingEdt();
        MapLeaseManager manager = null;
        try {
            TestEnvironment environment = environment(edt);
            AtomicReference<MapLeaseManager> managerReference = new AtomicReference<MapLeaseManager>();
            CallbackCloseProbe probe = new CallbackCloseProbe(managerReference);
            manager = manager(environment, edt, environment::newMap, probe);
            managerReference.set(manager);
            MapLease initialLease = acquire(manager, environment, edt);
            FakeMapModel oldModel = environment.model;
            DetectingEdt.TaskObservation observation = edt.expectNextTask();
            probe.arm(() -> {
                oldModel.markExternalChange();
                managerReference.get().checkExternalChanges();
            }, observation, () -> assertThat(environment.closeCount).hasValue(0));

            CompletableFuture<MapLease> immediate = manager.acquire(environment.reference()).toCompletableFuture();

            probe.awaitCallbackAndOperation();
            assertThat(probe.callbackFailure()).isNull();
            assertThat(probe.waitingAtBoundary()).isFalse();
            assertThat(probe.operationReturned()).isTrue();
            assertThat(environment.closeCount).hasValue(0);
            assertThat(immediate.isDone()).isTrue();
            assertThat(immediate.isCompletedExceptionally()).isFalse();
            assertThat(environment.lifecycleListeners).isEmpty();
            assertThat(oldModel.listenerCount()).isZero();
            initialLease.close();
            immediate.get(1, TimeUnit.SECONDS).close();
        }
        finally {
            if (manager != null) {
                manager.close();
            }
            edt.close();
        }
    }


    @Test
    public void schedulesDeferredLifecycleAfterTheSettlementOwnerReleasesBeforeRegistration() throws Exception {
        DetectingEdt edt = new DetectingEdt();
        MapLeaseManager manager = null;
        try {
            TestEnvironment environment = environment(edt);
            LostWakeupProbe probe = new LostWakeupProbe(edt, environment);
            manager = new MapLeaseManager(environment.workspace, environment.modeController, edt,
                (ScheduledExecutorService) null, environment::newMap, environment::containsView, environment::lookup,
                probe, probe);
            MapLease initialLease = acquire(manager, environment, edt);
            FakeMapModel oldModel = environment.model;
            probe.arm(manager, oldModel);

            CompletableFuture<MapLease> immediate = manager.acquire(environment.reference()).toCompletableFuture();

            probe.await();
            MapLease immediateLease = immediate.get(1, TimeUnit.SECONDS);
            assertThat(probe.ownerReleasedBeforeRegistration()).isTrue();
            assertThat(environment.model).isNotSameAs(oldModel);
            assertThat(oldModel.listenerCount()).isZero();
            assertThat(environment.model.listenerCount()).isEqualTo(1);
            initialLease.close();
            immediateLease.close();
            assertThat(environment.model.listenerCount()).isZero();
        }
        finally {
            if (manager != null) {
                manager.close();
            }
            edt.close();
        }
    }

    @Test
    public void schedulesDeferredLifecycleOnlyAfterTheOutermostSettlementRelease() throws Exception {
        InlineEdt edt = new InlineEdt();
        TestEnvironment environment = environment(edt);
        DeliveryRaceProbe probe = new DeliveryRaceProbe();
        MapLeaseManager manager = manager(environment, edt, environment::newMap, probe);
        MapLease initialLease = acquire(manager, environment, edt);
        FakeMapModel oldModel = environment.model;
        ReentrantLock settlementLock = (ReentrantLock) privateField(manager, "settlementLock");

        probe.arm(() -> environment.removeExternally(oldModel), "Deferred lifecycle scheduling",
            () -> edt.observeNextExecute(settlementLock), true);
        CompletableFuture<MapLease> immediate = manager.acquire(environment.reference()).toCompletableFuture();

        probe.awaitCompletionAndOperation();
        assertThat(edt.settlementLockHeldAtObservedExecute()).isFalse();
        assertThat(environment.model).isNotSameAs(oldModel);
        assertThat(oldModel.listenerCount()).isZero();
        assertThat(environment.model.listenerCount()).isEqualTo(1);

        initialLease.close();
        immediate.get(1, TimeUnit.SECONDS).close();
        assertThat(environment.model.listenerCount()).isZero();
    }

    @Test
    public void revalidatesDeferredExternalReloadBeforeAdvancingGeneration() throws Exception {
        TestEdt edt = new TestEdt();
        TestEnvironment environment = environment(edt);
        DeliveryRaceProbe probe = new DeliveryRaceProbe();
        MapLeaseManager manager = manager(environment, edt, environment::newMap, probe);
        List<MapAdapterEvent> events = eventsFrom(manager);
        MapReference reference = environment.reference();
        MapLease firstLease = acquire(manager, reference, edt);
        FakeMapModel oldModel = environment.model;

        probe.arm(() -> {
            oldModel.markExternalChange();
            manager.checkExternalChanges();
            edt.runAll();
            environment.visibleModels.add(oldModel);
        }, "Deferred external reload", () -> {
            assertThat(environment.closeCount).hasValue(0);
            assertThat(environment.model).isSameAs(oldModel);
            assertThat(oldModel.listenerCount()).isEqualTo(1);
        }, true);
        CompletionStage<MapLease> immediateAcquire = manager.acquire(reference);

        probe.awaitCompletionAndOperation();
        edt.runAll();
        MapLease immediateLease = immediateAcquire.toCompletableFuture().get(1, TimeUnit.SECONDS);
        assertThat(immediateLease.state()).isEqualTo(MapOperationalState.RELOAD_REQUIRED);
        assertThat(environment.closeCount).hasValue(0);
        assertThat(environment.loadCount).hasValue(1);
        assertThat(environment.model).isSameAs(oldModel);
        assertThat(oldModel.listenerCount()).isEqualTo(1);
        assertThat(events).extracting(MapAdapterEvent::state)
            .containsExactly(MapOperationalState.LOADING, MapOperationalState.AVAILABLE,
                MapOperationalState.RELOAD_REQUIRED);

        firstLease.close();
        immediateLease.close();
        assertThat(oldModel.listenerCount()).isZero();
    }

    @Test
    public void retriesDeferredLifecycleRemovalAfterATransientEdtDrainRejection() throws Exception {
        TestEdt edt = new TestEdt();
        ControlledRetryScheduler retryScheduler = new ControlledRetryScheduler();
        TestEnvironment environment = environment(edt);
        DeliveryRaceProbe probe = new DeliveryRaceProbe();
        MapLeaseManager manager = manager(environment, edt, retryScheduler, environment::newMap, probe);
        MapReference reference = environment.reference();
        MapLease initialLease = acquire(manager, reference, edt);
        FakeMapModel oldModel = environment.model;

        probe.arm(() -> {
            environment.removeExternally(oldModel);
            edt.runAll();
        }, "Deferred lifecycle removal", () -> edt.rejectNextExecute(), true);
        CompletableFuture<MapLease> immediate = manager.acquire(reference).toCompletableFuture();

        probe.awaitCompletionAndOperation();
        assertThat(edt.rejectedSubmissionCount()).isEqualTo(1);
        assertThat(environment.model).isSameAs(oldModel);
        assertThat(oldModel.listenerCount()).isEqualTo(1);
        retryScheduler.runNextRetry();
        edt.runAll();

        MapLease immediateLease = immediate.get(1, TimeUnit.SECONDS);
        FakeMapModel replacement = environment.model;
        assertThat(replacement).isNotSameAs(oldModel);
        assertThat(environment.loadCount).hasValue(2);
        assertThat(oldModel.listenerCount()).isZero();
        assertThat(replacement.listenerCount()).isEqualTo(1);
        assertThat(immediateLease.state()).isEqualTo(MapOperationalState.AVAILABLE);

        initialLease.close();
        immediateLease.close();
        assertThat(replacement.listenerCount()).isZero();
    }

    @Test
    public void retriesDeferredExternalReloadAfterATransientEdtDrainRejection() throws Exception {
        TestEdt edt = new TestEdt();
        ControlledRetryScheduler retryScheduler = new ControlledRetryScheduler();
        TestEnvironment environment = environment(edt);
        DeliveryRaceProbe probe = new DeliveryRaceProbe();
        MapLeaseManager manager = manager(environment, edt, retryScheduler, environment::newMap, probe);
        MapReference reference = environment.reference();
        MapLease initialLease = acquire(manager, reference, edt);
        FakeMapModel oldModel = environment.model;

        probe.arm(() -> {
            oldModel.markExternalChange();
            manager.checkExternalChanges();
            edt.runAll();
        }, "Deferred external reload", () -> edt.rejectNextExecute(), true);
        CompletableFuture<MapLease> immediate = manager.acquire(reference).toCompletableFuture();

        probe.awaitCompletionAndOperation();
        assertThat(edt.rejectedSubmissionCount()).isEqualTo(1);
        assertThat(environment.closeCount).hasValue(0);
        assertThat(environment.model).isSameAs(oldModel);
        assertThat(oldModel.listenerCount()).isEqualTo(1);
        retryScheduler.runNextRetry();
        edt.runAll();

        MapLease immediateLease = immediate.get(1, TimeUnit.SECONDS);
        FakeMapModel replacement = environment.model;
        assertThat(replacement).isNotSameAs(oldModel);
        assertThat(environment.closeCount).hasValue(1);
        assertThat(environment.loadCount).hasValue(2);
        assertThat(oldModel.listenerCount()).isZero();
        assertThat(replacement.listenerCount()).isEqualTo(1);
        assertThat(immediateLease.state()).isEqualTo(MapOperationalState.AVAILABLE);

        initialLease.close();
        immediateLease.close();
        assertThat(replacement.listenerCount()).isZero();
    }

    @Test
    public void ignoresDeferredExternalReloadWhenCloseFollowsARejectedDrainSubmission() throws Exception {
        TestEdt edt = new TestEdt();
        ControlledRetryScheduler retryScheduler = new ControlledRetryScheduler();
        TestEnvironment environment = environment(edt);
        DeliveryRaceProbe probe = new DeliveryRaceProbe();
        MapLeaseManager manager = manager(environment, edt, retryScheduler, environment::newMap, probe);
        MapReference reference = environment.reference();
        MapLease initialLease = acquire(manager, reference, edt);
        FakeMapModel oldModel = environment.model;

        probe.arm(() -> {
            oldModel.markExternalChange();
            manager.checkExternalChanges();
            edt.runAll();
        }, "Deferred external reload", () -> edt.rejectNextExecute(), true);
        CompletableFuture<MapLease> immediate = manager.acquire(reference).toCompletableFuture();

        probe.awaitCompletionAndOperation();
        assertThat(edt.rejectedSubmissionCount()).isEqualTo(1);
        assertThat(environment.closeCount).hasValue(0);
        manager.close();
        assertThat((List<?>) privateField(manager, "deferredOperations")).isEmpty();
        assertThat(privateField(manager, "deferredDrainRetry")).isNull();
        assertThat(privateField(manager, "deferredDrainRetryFuture")).isNull();
        assertThat(privateField(manager, "deferredDrainScheduled")).isEqualTo(false);
        assertThat(privateField(manager, "deferredDrainRunning")).isEqualTo(false);
        retryScheduler.runNextRetry();
        edt.runAll();

        assertThat(immediate.isDone()).isTrue();
        assertThat(immediate.isCompletedExceptionally()).isFalse();
        assertThat(environment.closeCount).hasValue(0);
        assertThat(environment.loadCount).hasValue(1);
        assertThat(oldModel.listenerCount()).isZero();
        assertThat(environment.lifecycleListeners).isEmpty();
        assertThat(manager.acquire(reference).toCompletableFuture().isCompletedExceptionally()).isTrue();
        initialLease.close();
    }

    @Test
    public void serializesModelNullRetryWithLeaseDelivery() throws Exception {
        TestEdt edt = new TestEdt();
        TestEnvironment environment = environment(edt);
        AtomicInteger loadAttempts = new AtomicInteger();
        MapLeaseManager manager = manager(environment, edt, url -> {
            if (loadAttempts.incrementAndGet() == 1) {
                return null;
            }
            return environment.newMap(url);
        });
        List<MapAdapterEvent> events = eventsFrom(manager);
        MapReference reference = environment.reference();
        CompletableFuture<MapLease> firstAcquire = manager.acquire(reference).toCompletableFuture();
        CompletableFuture<MapLease> secondAcquire = manager.acquire(reference).toCompletableFuture();
        AtomicReference<CompletableFuture<MapLease>> retryAcquire =
            new AtomicReference<CompletableFuture<MapLease>>();
        DeliveryRaceProbe probe = new DeliveryRaceProbe();
        probe.arm(() -> retryAcquire.set(manager.acquire(reference).toCompletableFuture()), "Model-null retry");
        probe.attachTo(firstAcquire);

        edt.runAll();

        probe.awaitCompletionAndOperation();
        edt.runAll();
        MapLease firstLease = firstAcquire.get(1, TimeUnit.SECONDS);
        CompletableFuture<MapLease> retryFuture = retryAcquire.get();
        assertThat(retryFuture).isNotNull();
        MapLease retryLease = retryFuture.get(1, TimeUnit.SECONDS);
        assertThat(secondAcquire.isDone()).isTrue();
        MapLease secondLease = secondAcquire.isCompletedExceptionally()
            ? null : secondAcquire.get(1, TimeUnit.SECONDS);
        assertThat(loadAttempts).hasValue(2);
        assertThat(firstLease.state()).isEqualTo(MapOperationalState.AVAILABLE);
        assertThat(retryLease.state()).isEqualTo(MapOperationalState.AVAILABLE);
        if (secondLease != null) {
            assertThat(secondLease.state()).isEqualTo(MapOperationalState.AVAILABLE);
        }
        assertThat(environment.model.listenerCount()).isEqualTo(1);
        assertThat(events).extracting(MapAdapterEvent::state)
            .containsExactly(MapOperationalState.LOADING, MapOperationalState.UNREADABLE,
                MapOperationalState.LOADING, MapOperationalState.AVAILABLE);

        firstLease.close();
        if (secondLease != null) {
            secondLease.close();
        }
        assertThat(environment.model.listenerCount()).isEqualTo(1);
        retryLease.close();
        assertThat(environment.model.listenerCount()).isZero();
        int eventCountAfterTeardown = events.size();
        environment.model.fireChange();
        edt.runAll();
        assertThat(events).hasSize(eventCountAfterTeardown);
    }

    @Test
    public void completesPendingFutureCallbacksOutsideTheManagerMonitor() throws Exception {
        TestEdt edt = new TestEdt();
        TestEnvironment environment = environment(edt);
        MapLeaseManager manager = manager(environment, edt, environment::newMap);
        CompletableFuture<MapLease> pending = manager.acquire(environment.reference()).toCompletableFuture();
        CountDownLatch monitorOperationCompleted = new CountDownLatch(1);
        AtomicReference<Thread> monitorOperation = new AtomicReference<Thread>();
        AtomicReference<Throwable> monitorOperationFailure = new AtomicReference<Throwable>();
        CompletableFuture<Void> callback = pending.handle((lease, failure) -> {
            Thread operation = new Thread(() -> {
                try {
                    ListenerRegistration registration = manager.addListener(event -> {
                    });
                    registration.close();
                }
                catch (Throwable exception) {
                    monitorOperationFailure.set(exception);
                }
                finally {
                    monitorOperationCompleted.countDown();
                }
            }, "map-lease-monitor-probe");
            monitorOperation.set(operation);
            operation.start();
            try {
                if (!monitorOperationCompleted.await(1, TimeUnit.SECONDS)) {
                    throw new AssertionError("Future completion callback ran while holding the manager monitor");
                }
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for the manager monitor probe", exception);
            }
            return null;
        });

        edt.runAll();

        callback.get(1, TimeUnit.SECONDS);
        Thread operation = monitorOperation.get();
        assertThat(operation).isNotNull();
        operation.join(1_000L);
        assertThat(operation.isAlive()).isFalse();
        assertThat(monitorOperationFailure.get()).isNull();
        MapLease lease = pending.get(1, TimeUnit.SECONDS);
        lease.close();
    }

    @Test
    public void completesExceptionalPendingFutureCallbacksOutsideTheManagerMonitor() throws Exception {
        TestEdt edt = new TestEdt();
        TestEnvironment environment = environment(edt);
        MapLeaseManager manager = manager(environment, edt, environment::newMap);
        CompletableFuture<MapLease> pending = manager.acquire(environment.reference()).toCompletableFuture();
        CountDownLatch monitorOperationCompleted = new CountDownLatch(1);
        AtomicReference<Thread> monitorOperation = new AtomicReference<Thread>();
        AtomicReference<Throwable> monitorOperationFailure = new AtomicReference<Throwable>();
        CompletableFuture<Void> callback = pending.handle((lease, failure) -> {
            Thread operation = new Thread(() -> {
                try {
                    ListenerRegistration registration = manager.addListener(event -> {
                    });
                    registration.close();
                }
                catch (Throwable exception) {
                    monitorOperationFailure.set(exception);
                }
                finally {
                    monitorOperationCompleted.countDown();
                }
            }, "map-lease-exceptional-monitor-probe");
            monitorOperation.set(operation);
            operation.start();
            try {
                if (!monitorOperationCompleted.await(1, TimeUnit.SECONDS)) {
                    throw new AssertionError("Future completion callback ran while holding the manager monitor");
                }
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for the manager monitor probe", exception);
            }
            return null;
        });

        manager.close();

        callback.get(1, TimeUnit.SECONDS);
        Thread operation = monitorOperation.get();
        assertThat(operation).isNotNull();
        operation.join(1_000L);
        assertThat(operation.isAlive()).isFalse();
        assertThat(monitorOperationFailure.get()).isNull();
        assertThat(pending.isCompletedExceptionally()).isTrue();
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

    private static final class ControlledRetryScheduler extends AbstractExecutorService
            implements ScheduledExecutorService {
        private final Deque<ControlledScheduledFuture<?>> retries =
            new ConcurrentLinkedDeque<ControlledScheduledFuture<?>>();
        private final AtomicBoolean shutdown = new AtomicBoolean();

        @Override
        public ScheduledFuture<?> schedule(final Runnable command, final long delay, final TimeUnit unit) {
            return schedule(new Callable<Object>() {
                @Override
                public Object call() {
                    command.run();
                    return null;
                }
            }, delay, unit);
        }

        @Override
        public <V> ScheduledFuture<V> schedule(final Callable<V> command, final long delay, final TimeUnit unit) {
            if (shutdown.get()) {
                throw new RejectedExecutionException("Controlled retry scheduler is closed");
            }
            ControlledScheduledFuture<V> retry = new ControlledScheduledFuture<V>(command);
            retries.add(retry);
            return retry;
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(final Runnable command, final long initialDelay,
                final long period, final TimeUnit unit) {
            return periodic(command);
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(final Runnable command, final long initialDelay,
                final long delay, final TimeUnit unit) {
            return periodic(command);
        }

        @Override
        public void execute(final Runnable command) {
            schedule(command, 0L, TimeUnit.MILLISECONDS);
        }

        @Override
        public void shutdown() {
            shutdown.set(true);
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown();
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return shutdown.get();
        }

        @Override
        public boolean isTerminated() {
            return shutdown.get();
        }

        @Override
        public boolean awaitTermination(final long timeout, final TimeUnit unit) {
            return shutdown.get();
        }

        private ScheduledFuture<?> periodic(final Runnable command) {
            return new ControlledScheduledFuture<Object>(new Callable<Object>() {
                @Override
                public Object call() {
                    command.run();
                    return null;
                }
            });
        }

        private void runNextRetry() {
            ControlledScheduledFuture<?> retry = retries.pollFirst();
            if (retry == null) {
                throw new AssertionError("No autonomous deferred-drain retry was scheduled");
            }
            retry.runEvenIfCancelled();
        }
    }

    private static final class ControlledScheduledFuture<V> extends FutureTask<V> implements ScheduledFuture<V> {
        private final Callable<V> callable;

        private ControlledScheduledFuture(final Callable<V> callable) {
            super(callable);
            this.callable = callable;
        }

        @Override
        public long getDelay(final TimeUnit unit) {
            return 0L;
        }

        @Override
        public int compareTo(final Delayed other) {
            return 0;
        }

        private void runEvenIfCancelled() {
            try {
                callable.call();
            }
            catch (Exception failure) {
                throw new AssertionError("Controlled retry failed", failure);
            }
        }
    }

    private static final class LostWakeupProbe implements MapLeaseManager.LeaseCompletionInterceptor,
            MapLeaseManager.SettlementTestHook {
        private final DetectingEdt edt;
        private final TestEnvironment environment;
        private final CountDownLatch firstTryFailed = new CountDownLatch(1);
        private final CountDownLatch callbackReturning = new CountDownLatch(1);
        private final CountDownLatch ownerReleased = new CountDownLatch(1);
        private final CountDownLatch invalidationSubmitted = new CountDownLatch(1);
        private final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        private final AtomicReference<MapModel> oldModel = new AtomicReference<MapModel>();
        private final AtomicReference<ReentrantLock> settlementLock = new AtomicReference<ReentrantLock>();
        private final AtomicBoolean armed = new AtomicBoolean();
        private final AtomicBoolean ownerReleasedBeforeRegistration = new AtomicBoolean();

        private LostWakeupProbe(final DetectingEdt edt, final TestEnvironment environment) {
            this.edt = edt;
            this.environment = environment;
        }

        private void arm(final MapLeaseManager manager, final MapModel oldModel) {
            this.oldModel.set(oldModel);
            armed.set(true);
            try {
                settlementLock.set((ReentrantLock) privateField(manager, "settlementLock"));
            }
            catch (Exception reflectionFailure) {
                failure.set(reflectionFailure);
            }
        }

        @Override
        public void beforeComplete(final CompletableFuture<MapLease> future) {
            if (!armed.compareAndSet(true, false)) {
                return;
            }
            future.whenComplete((lease, failureValue) -> {
                try {
                    final ReentrantLock lock = settlementLock.get();
                    if (lock == null) {
                        throw new AssertionError("Settlement lock was not installed for the lost-wakeup probe");
                    }
                    final Thread ownerReleaseWatcher = new Thread(() -> {
                        lock.lock();
                        try {
                        }
                        finally {
                            lock.unlock();
                            ownerReleased.countDown();
                        }
                    }, "map-lease-lost-wakeup-owner-watcher");
                    final Thread invalidation = new Thread(() -> {
                        try {
                            environment.removeExternally(oldModel.get());
                        }
                        finally {
                            invalidationSubmitted.countDown();
                        }
                    }, "map-lease-lost-wakeup-invalidation");
                    ownerReleaseWatcher.start();
                    invalidation.start();
                    if (!firstTryFailed.await(1, TimeUnit.SECONDS)) {
                        throw new AssertionError("EDT did not report the first failed settlement attempt");
                    }
                }
                catch (Throwable probeFailure) {
                    failure.compareAndSet(null, probeFailure);
                }
                finally {
                    callbackReturning.countDown();
                }
            });
        }

        @Override
        public void afterFailedSettlementTryLock() {
            firstTryFailed.countDown();
            try {
                if (!callbackReturning.await(1, TimeUnit.SECONDS)) {
                    throw new AssertionError("Completion callback did not return for the lost-wakeup probe");
                }
                if (!ownerReleased.await(1, TimeUnit.SECONDS)) {
                    throw new AssertionError("Settlement owner did not release before deferred registration");
                }
                ownerReleasedBeforeRegistration.set(true);
            }
            catch (Throwable probeFailure) {
                failure.compareAndSet(null, probeFailure);
            }
        }

        private void await() throws Exception {
            if (!invalidationSubmitted.await(2, TimeUnit.SECONDS)) {
                throw new AssertionError("Lifecycle invalidation was not submitted");
            }
            edt.call(() -> null);
            edt.call(() -> null);
            assertThat(failure.get()).isNull();
        }

        private boolean ownerReleasedBeforeRegistration() {
            return ownerReleasedBeforeRegistration.get();
        }
    }
    private static final class CallbackCloseProbe implements MapLeaseManager.LeaseCompletionInterceptor {
        private final AtomicReference<MapLeaseManager> manager;
        private final AtomicBoolean armed = new AtomicBoolean();
        private final AtomicReference<CompletableFuture<MapLease>> observedFuture =
            new AtomicReference<CompletableFuture<MapLease>>();
        private final AtomicReference<Thread> operationThread = new AtomicReference<Thread>();
        private final AtomicReference<Throwable> callbackFailure = new AtomicReference<Throwable>();
        private final AtomicReference<Throwable> operationFailure = new AtomicReference<Throwable>();
        private final AtomicBoolean operationReturned = new AtomicBoolean();
        private final AtomicBoolean waitingAtBoundary = new AtomicBoolean();
        private final CountDownLatch operationStarted = new CountDownLatch(1);
        private final CountDownLatch callbackFinished = new CountDownLatch(1);
        private Runnable operation;
        private Runnable boundaryAssertion;
        private DetectingEdt.TaskObservation observation;

        private CallbackCloseProbe(final AtomicReference<MapLeaseManager> manager) {
            this.manager = manager;
        }

        private void arm(final Runnable operation, final DetectingEdt.TaskObservation observation) {
            arm(operation, observation, new Runnable() {
                @Override
                public void run() {
                }
            });
        }

        private void arm(final Runnable operation, final DetectingEdt.TaskObservation observation,
                final Runnable boundaryAssertion) {
            this.operation = operation;
            this.observation = observation;
            this.boundaryAssertion = boundaryAssertion;
            callbackFailure.set(null);
            operationFailure.set(null);
            operationReturned.set(false);
            operationThread.set(null);
            observedFuture.set(null);
            armed.set(true);
        }

        @Override
        public void beforeComplete(final CompletableFuture<MapLease> future) {
            if (!armed.compareAndSet(true, false)) {
                return;
            }
            observedFuture.set(future);
            final Runnable operationToRun = operation;
            final DetectingEdt.TaskObservation observationToUse = observation;
            future.whenComplete((lease, failure) -> {
                final Thread operationRunner = new Thread(() -> {
                    operationStarted.countDown();
                    try {
                        operationToRun.run();
                    }
                    catch (Throwable exception) {
                        operationFailure.set(exception);
                    }
                    finally {
                        operationReturned.set(true);
                    }
                }, "map-lease-callback-close-operation");
                operationThread.set(operationRunner);
                operationRunner.start();
                try {
                    if (!operationStarted.await(1, TimeUnit.SECONDS)) {
                        throw new AssertionError("Callback-close operation did not start");
                    }
                    if (!observationToUse.awaitStarted()) {
                        throw new AssertionError("EDT invalidation task did not start");
                    }
                    waitingAtBoundary.set(observationToUse.awaitWaitingOrFinished());
                    if (waitingAtBoundary.get()) {
                        callbackFailure.compareAndSet(null,
                            new AssertionError("EDT invalidation waited for lease settlement"));
                        return;
                    }
                    try {
                        boundaryAssertion.run();
                    }
                    catch (Throwable assertionFailure) {
                        callbackFailure.compareAndSet(null, assertionFailure);
                    }
                    try {
                        manager.get().close();
                    }
                    catch (Throwable closeFailure) {
                        callbackFailure.compareAndSet(null, closeFailure);
                    }
                }
                catch (Throwable failureDuringProbe) {
                    callbackFailure.compareAndSet(null, failureDuringProbe);
                }
                finally {
                    callbackFinished.countDown();
                }
            });
        }

        private void awaitCallbackAndOperation() throws Exception {
            if (!callbackFinished.await(2, TimeUnit.SECONDS)) {
                throw new AssertionError("Completion callback did not finish");
            }
            final Thread operationRunner = operationThread.get();
            if (operationRunner == null) {
                throw new AssertionError("Callback-close operation thread was not created");
            }
            operationRunner.join(2_000L);
            if (operationRunner.isAlive()) {
                throw new AssertionError("Callback-close operation did not finish");
            }
            assertThat(operationFailure.get()).isNull();
        }

        private Throwable callbackFailure() {
            return callbackFailure.get();
        }

        private boolean waitingAtBoundary() {
            return waitingAtBoundary.get();
        }

        private boolean operationReturned() {
            return operationReturned.get();
        }
    }

    private static final class DetectingEdt implements EdtExecutor {
        private static final Runnable STOP = new Runnable() {
            @Override
            public void run() {
            }
        };

        private final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<Runnable>();
        private final AtomicBoolean running = new AtomicBoolean(true);
        private final AtomicReference<TaskObservation> nextObservation =
            new AtomicReference<TaskObservation>();
        private final AtomicReference<TaskObservation> currentObservation =
            new AtomicReference<TaskObservation>();
        private final CountDownLatch ready = new CountDownLatch(1);
        private final Thread thread;
        private final Thread observer;

        private DetectingEdt() throws Exception {
            thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    runLoop();
                }
            }, "map-lease-detecting-edt");
            thread.setDaemon(true);
            observer = new Thread(new Runnable() {
                @Override
                public void run() {
                    observeWaits();
                }
            }, "map-lease-detecting-edt-observer");
            observer.setDaemon(true);
            thread.start();
            observer.start();
            if (!ready.await(1, TimeUnit.SECONDS)) {
                throw new AssertionError("Detecting EDT did not start");
            }
        }

        @Override
        public <T> T call(final Callable<T> task) {
            if (isEdt()) {
                try {
                    return task.call();
                }
                catch (Exception failure) {
                    throw new IllegalStateException("EDT task failed", failure);
                }
            }
            final CompletableFuture<T> result = new CompletableFuture<T>();
            execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        result.complete(task.call());
                    }
                    catch (Throwable failure) {
                        result.completeExceptionally(failure);
                    }
                }
            });
            try {
                return result.get(1, TimeUnit.SECONDS);
            }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while executing on the detecting EDT", interrupted);
            }
            catch (java.util.concurrent.TimeoutException timeout) {
                throw new AssertionError("EDT call did not finish", timeout);
            }
            catch (java.util.concurrent.ExecutionException failure) {
                throw new IllegalStateException("EDT task failed", failure.getCause());
            }
        }

        @Override
        public void execute(final Runnable task) {
            if (isEdt()) {
                task.run();
                return;
            }
            if (!running.get()) {
                throw new IllegalStateException("Detecting EDT is closed");
            }
            queue.add(task);
        }

        @Override
        public boolean isEdt() {
            return Thread.currentThread() == thread;
        }

        private TaskObservation expectNextTask() {
            final TaskObservation observation = new TaskObservation(thread);
            nextObservation.set(observation);
            return observation;
        }

        private void runLoop() {
            ready.countDown();
            try {
                while (running.get()) {
                    final Runnable task = queue.take();
                    if (task == STOP) {
                        break;
                    }
                    final TaskObservation observation = nextObservation.getAndSet(null);
                    currentObservation.set(observation);
                    if (observation != null) {
                        observation.activate();
                    }
                    try {
                        task.run();
                    }
                    finally {
                        if (observation != null) {
                            observation.finish();
                        }
                        currentObservation.compareAndSet(observation, null);
                    }
                }
            }
            catch (InterruptedException interrupted) {
                if (running.get()) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        private void observeWaits() {
            while (running.get()) {
                final TaskObservation observation = currentObservation.get();
                if (observation != null) {
                    observation.observeWaiting();
                }
                Thread.yield();
            }
        }

        private void close() {
            running.set(false);
            observer.interrupt();
            queue.offer(STOP);
            thread.interrupt();
            try {
                thread.join(2_000L);
                observer.join(2_000L);
            }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        private static final class TaskObservation {
            private final Thread edtThread;
            private final CountDownLatch started = new CountDownLatch(1);
            private final CountDownLatch waitingOrFinished = new CountDownLatch(1);
            private final AtomicBoolean active = new AtomicBoolean();
            private final AtomicBoolean watching = new AtomicBoolean();
            private final AtomicBoolean waiting = new AtomicBoolean();

            private TaskObservation(final Thread edtThread) {
                this.edtThread = edtThread;
            }

            private void activate() {
                active.set(true);
                started.countDown();
            }

            private void finish() {
                active.set(false);
                waitingOrFinished.countDown();
            }

            private boolean awaitWaitingOrFinished() throws Exception {
                watching.set(true);
                if (!active.get()) {
                    waitingOrFinished.countDown();
                }
                else {
                    observeWaiting();
                }
                if (!waitingOrFinished.await(1, TimeUnit.SECONDS)) {
                    throw new AssertionError("EDT invalidation did not reach a boundary");
                }
                watching.set(false);
                return waiting.get();
            }

            private boolean awaitStarted() throws Exception {
                return started.await(1, TimeUnit.SECONDS);
            }

            private void observeWaiting() {
                if (watching.get() && active.get() && edtThread.getState() == Thread.State.WAITING) {
                    waiting.set(true);
                    waitingOrFinished.countDown();
                }
            }
        }
    }

    private static final class DeliveryRaceProbe implements MapLeaseManager.LeaseCompletionInterceptor {
        private final AtomicBoolean armed = new AtomicBoolean();
        private final AtomicReference<CompletableFuture<MapLease>> observedFuture =
            new AtomicReference<CompletableFuture<MapLease>>();
        private final AtomicReference<CompletableFuture<MapLease>> completion =
            new AtomicReference<CompletableFuture<MapLease>>();
        private final AtomicReference<Thread> operationThread = new AtomicReference<Thread>();
        private final AtomicReference<Throwable> operationFailure = new AtomicReference<Throwable>();
        private final AtomicBoolean operationReturned = new AtomicBoolean();
        private Runnable concurrentOperation;
        private Runnable boundaryAssertion;
        private String operationName;
        private CountDownLatch operationStarted;
        private boolean expectOperationReturn;

        private void arm(final Runnable operation, final String name) {
            arm(operation, name, new Runnable() {
                @Override
                public void run() {
                }
            }, false);
        }

        private void arm(final Runnable operation, final String name, final Runnable assertion) {
            arm(operation, name, assertion, false);
        }

        private void arm(final Runnable operation, final String name, final Runnable assertion,
                final boolean expectOperationReturn) {
            concurrentOperation = operation;
            boundaryAssertion = assertion;
            operationName = name;
            this.expectOperationReturn = expectOperationReturn;
            operationStarted = new CountDownLatch(1);
            operationReturned.set(false);
            operationFailure.set(null);
            operationThread.set(null);
            observedFuture.set(null);
            completion.set(null);
            armed.set(true);
        }

        @Override
        public void beforeComplete(final CompletableFuture<MapLease> future) {
            attachTo(future);
        }

        private void attachTo(final CompletableFuture<MapLease> future) {
            if (!armed.compareAndSet(true, false)) {
                return;
            }
            observedFuture.set(future);
            completion.set(future.whenComplete((lease, failure) -> {
                Thread operation = new Thread(() -> {
                    operationStarted.countDown();
                    try {
                        concurrentOperation.run();
                    }
                    catch (Throwable exception) {
                        operationFailure.set(exception);
                    }
                    finally {
                        operationReturned.set(true);
                    }
                }, "map-lease-" + operationName.toLowerCase().replace(' ', '-') + "-probe");
                operationThread.set(operation);
                operation.start();
                try {
                    assertThat(operationStarted.await(1, TimeUnit.SECONDS)).isTrue();
                }
                catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("Interrupted while starting " + operationName, exception);
                }
                awaitOperationReturnOrLockWait(operation, operationReturned);
                assertThat(operationReturned.get())
                    .as(operationName + " did not return while lease delivery was active")
                    .isEqualTo(expectOperationReturn);
                boundaryAssertion.run();
            }));
        }

        private CompletableFuture<MapLease> observedFuture() {
            return observedFuture.get();
        }

        private void awaitCompletionAndOperation() throws Exception {
            CompletableFuture<MapLease> completionFuture = completion.get();
            assertThat(completionFuture).isNotNull();
            completionFuture.get(1, TimeUnit.SECONDS);
            Thread operation = operationThread.get();
            assertThat(operation).isNotNull();
            operation.join(1_000L);
            assertThat(operation.isAlive()).isFalse();
            assertThat(operationFailure.get()).isNull();
        }
    }

    private static Object privateField(final MapLeaseManager manager, final String name) throws Exception {
        final Field field = MapLeaseManager.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(manager);
    }

    private static void awaitOperationReturnOrLockWait(final Thread operation,
            final AtomicBoolean operationReturned) {
        final AtomicBoolean observing = new AtomicBoolean(true);
        final AtomicBoolean waitingForSettlement = new AtomicBoolean();
        final CountDownLatch observed = new CountDownLatch(1);
        final Thread observer = new Thread(new Runnable() {
            @Override
            public void run() {
                while (observing.get()) {
                    if (operationReturned.get()) {
                        observed.countDown();
                        return;
                    }
                    if (operation.getState() == Thread.State.WAITING) {
                        waitingForSettlement.set(true);
                        observed.countDown();
                        return;
                    }
                    Thread.yield();
                }
            }
        }, "map-lease-settlement-observer");
        observer.setDaemon(true);
        observer.start();
        try {
            if (!observed.await(1, TimeUnit.SECONDS)) {
                throw new AssertionError("Operation did not return or wait for lease settlement");
            }
            if (!operationReturned.get() && !waitingForSettlement.get()) {
                throw new AssertionError("Operation left settlement observation without returning or waiting");
            }
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while observing lease settlement", interrupted);
        }
        finally {
            observing.set(false);
        }
    }

    private MapLease acquire(MapLeaseManager manager, TestEnvironment environment, EdtExecutor edt) throws Exception {
        return acquire(manager, environment.reference(), edt);
    }

    private MapLease acquire(MapLeaseManager manager, MapReference reference, EdtExecutor edt) throws Exception {
        CompletionStage<MapLease> stage = manager.acquire(reference);
        if (edt instanceof TestEdt) {
            ((TestEdt) edt).runAll();
        }
        return stage.toCompletableFuture().get(1, TimeUnit.SECONDS);
    }

    private MapLeaseManager manager(TestEnvironment environment, EdtExecutor edt,
            MapLeaseManager.MapLoaderOperation loader) {
        MapLeaseManager manager = new MapLeaseManager(environment.workspace, environment.modeController, edt,
            (ScheduledExecutorService) null, loader, environment::containsView, environment::lookup);
        managers.add(manager);
        return manager;
    }

    private MapLeaseManager manager(TestEnvironment environment, EdtExecutor edt,
            MapLeaseManager.MapLoaderOperation loader, MapLeaseManager.LeaseCompletionInterceptor completionInterceptor) {
        return manager(environment, edt, null, loader, completionInterceptor);
    }

    private MapLeaseManager manager(TestEnvironment environment, EdtExecutor edt,
            ScheduledExecutorService scheduler, MapLeaseManager.MapLoaderOperation loader,
            MapLeaseManager.LeaseCompletionInterceptor completionInterceptor) {
        MapLeaseManager manager = new MapLeaseManager(environment.workspace, environment.modeController, edt,
            scheduler, loader, environment::containsView, environment::lookup, completionInterceptor);
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

    private TestEnvironment environment(EdtExecutor edt) throws Exception {
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

        private TestEnvironment(Path workspace, Path mapPath, EdtExecutor edt) throws Exception {
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

    private static final class InlineEdt implements EdtExecutor {
        private final ThreadLocal<Boolean> onEdt = new ThreadLocal<Boolean>();
        private final AtomicBoolean observeNextExecute = new AtomicBoolean();
        private final AtomicReference<ReentrantLock> observedLock = new AtomicReference<ReentrantLock>();
        private final AtomicReference<Boolean> settlementLockHeld = new AtomicReference<Boolean>();

        @Override
        public <T> T call(Callable<T> task) {
            Boolean wasOnEdt = onEdt.get();
            onEdt.set(Boolean.TRUE);
            try {
                return task.call();
            }
            catch (Exception exception) {
                throw new RuntimeException(exception);
            }
            finally {
                if (wasOnEdt == null) {
                    onEdt.remove();
                }
                else {
                    onEdt.set(wasOnEdt);
                }
            }
        }

        @Override
        public void execute(Runnable task) {
            if (observeNextExecute.compareAndSet(true, false)) {
                ReentrantLock lock = observedLock.get();
                if (lock == null) {
                    throw new AssertionError("Settlement lock was not installed for the scheduling probe");
                }
                settlementLockHeld.set(lock.isLocked());
            }
            call(() -> {
                task.run();
                return null;
            });
        }

        @Override
        public boolean isEdt() {
            return Boolean.TRUE.equals(onEdt.get());
        }

        private void observeNextExecute(ReentrantLock lock) {
            observedLock.set(lock);
            settlementLockHeld.set(null);
            observeNextExecute.set(true);
        }

        private boolean settlementLockHeldAtObservedExecute() {
            Boolean held = settlementLockHeld.get();
            if (held == null) {
                throw new AssertionError("Deferred lifecycle work was not scheduled");
            }
            return held;
        }
    }

    private static final class TestEdt implements EdtExecutor {
        private final Deque<Runnable> queued = new ConcurrentLinkedDeque<Runnable>();
        private final AtomicBoolean rejectNextExecute = new AtomicBoolean();
        private final AtomicInteger rejectedSubmissions = new AtomicInteger();
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
            if (rejectNextExecute.compareAndSet(true, false)) {
                rejectedSubmissions.incrementAndGet();
                throw new RejectedExecutionException("Controlled EDT rejected a deferred drain");
            }
            queued.add(task);
        }

        @Override
        public boolean isEdt() {
            return onEdt;
        }

        private void rejectNextExecute() {
            if (!rejectNextExecute.compareAndSet(false, true)) {
                throw new AssertionError("A controlled EDT rejection is already armed");
            }
        }

        private int rejectedSubmissionCount() {
            return rejectedSubmissions.get();
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
