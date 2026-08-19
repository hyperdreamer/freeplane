package org.freeplane.plugin.graph.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.freeplane.features.link.mindmapmode.MLinkController;
import org.freeplane.features.map.mindmapmode.MMapController;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.ui.IMapViewManager;
import org.freeplane.plugin.graph.adapter.MapLeaseManager;
import org.freeplane.plugin.graph.command.GraphCommand;
import org.freeplane.plugin.graph.command.GraphCommandRouter;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.workspace.GraphCommandResult;
import org.freeplane.plugin.graph.workspace.GraphWorkspaceStore;
import org.freeplane.plugin.graph.workspace.ListenerRegistration;
import org.mockito.InOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DefaultGraphWorkspaceControllerShould {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void createsAndShowsANewWorkspaceOnlyAfterTheSessionResourcesExist() throws Exception {
        Path workspace = temporaryFolder.getRoot().toPath().resolve("new.fpg");
        WorkspaceSessionRegistry sessions = new WorkspaceSessionRegistry();
        DefaultGraphWorkspaceController.SessionResources resources = resources(true);
        AtomicReference<Boolean> createFlag = new AtomicReference<Boolean>();
        AtomicReference<GraphWorkspaceViewBinding> binding = new AtomicReference<GraphWorkspaceViewBinding>();
        AtomicReference<WorkspaceCloseController> close = new AtomicReference<WorkspaceCloseController>();
        RecordingView view = new RecordingView();
        GraphProjection projection = mock(GraphProjection.class);
        CanvasState state = mock(CanvasState.class);
        CanvasStateListener canvasListener = mock(CanvasStateListener.class);
        ListenerRegistration canvasRegistration = mock(ListenerRegistration.class);
        when(resources.updates.currentProjection()).thenReturn(projection);
        when(resources.updates.currentState()).thenReturn(state);
        when(resources.updates.addCanvasStateListener(canvasListener)).thenReturn(canvasRegistration);

        DefaultGraphWorkspaceController controller = new DefaultGraphWorkspaceController(sessions,
            (path, id, create) -> {
                createFlag.set(create);
                return resources;
            }, (handle, canvas, closeController) -> {
                binding.set(canvas);
                close.set(closeController);
                return view;
            });

        GraphWorkspaceHandle handle = controller.open(workspace);

        assertThat(createFlag).hasValue(true);
        assertThat(view.showCount).hasValue(1);
        assertThat(binding.get().currentCanvasState()).isSameAs(state);
        assertThat(binding.get().addCanvasStateListener(canvasListener)).isSameAs(canvasRegistration);
        assertThat(handle.currentProjection()).isSameAs(projection);
        assertThat(sessions.owner(workspace)).isPresent();
        assertThat(close).isNotNull();
        verify(resources.updates).addCanvasStateListener(canvasListener);
    }

    @Test
    public void focusesAnAlreadyOpenWorkspaceWithoutCreatingASecondSession() throws Exception {
        Path workspace = temporaryFolder.getRoot().toPath().resolve("existing.fpg");
        Files.createFile(workspace);
        WorkspaceSessionRegistry sessions = new WorkspaceSessionRegistry();
        DefaultGraphWorkspaceController.SessionResources resources = resources(false);
        AtomicInteger factoryCalls = new AtomicInteger();
        RecordingView view = new RecordingView();
        DefaultGraphWorkspaceController controller = new DefaultGraphWorkspaceController(sessions,
            (path, id, create) -> {
                factoryCalls.incrementAndGet();
                assertThat(create).isFalse();
                return resources;
            }, (handle, binding, close) -> view);

        GraphWorkspaceHandle first = controller.open(workspace);
        GraphWorkspaceHandle second = controller.open(workspace);

        assertThat(second).isSameAs(first);
        assertThat(factoryCalls).hasValue(1);
        assertThat(view.showCount).hasValue(1);
        assertThat(view.focusCount).hasValue(1);
    }

    @Test
    public void leavesRegistryAndViewUntouchedWhenLoadingFails() throws Exception {
        Path workspace = temporaryFolder.getRoot().toPath().resolve("malformed.fpg");
        Files.createFile(workspace);
        WorkspaceSessionRegistry sessions = new WorkspaceSessionRegistry();
        AtomicInteger viewCalls = new AtomicInteger();
        DefaultGraphWorkspaceController controller = new DefaultGraphWorkspaceController(sessions,
            (path, id, create) -> {
                throw new IllegalArgumentException("malformed");
            }, (handle, binding, close) -> {
                viewCalls.incrementAndGet();
                return new RecordingView();
            });

        assertThatThrownBy(() -> controller.open(workspace))
            .isInstanceOf(GraphWorkspaceOpenException.class)
            .hasCauseInstanceOf(IllegalArgumentException.class);
        assertThat(sessions.owner(workspace)).isEmpty();
        assertThat(viewCalls).hasValue(0);
    }

    @Test
    public void forwardsHandleObservationCommandsAndListenerRegistration() {
        GraphCommandRouter router = mock(GraphCommandRouter.class);
        GraphUpdateCoordinator updates = mock(GraphUpdateCoordinator.class);
        WorkspaceCloseController close = mock(WorkspaceCloseController.class);
        GraphProjection projection = mock(GraphProjection.class);
        GraphCommand command = mock(GraphCommand.class);
        GraphCommandResult result = mock(GraphCommandResult.class);
        GraphProjectionListener listener = mock(GraphProjectionListener.class);
        ListenerRegistration registration = mock(ListenerRegistration.class);
        when(updates.currentProjection()).thenReturn(projection);
        when(router.execute(command)).thenReturn(result);
        when(updates.addProjectionListener(listener)).thenReturn(registration);
        when(close.saveAndClose()).thenReturn(true);
        DefaultGraphWorkspaceHandle handle = new DefaultGraphWorkspaceHandle(router, updates, close);

        assertThat(handle.currentProjection()).isSameAs(projection);
        assertThat(handle.execute(command)).isSameAs(result);
        assertThat(handle.addProjectionListener(listener)).isSameAs(registration);
        handle.close();

        verify(router).execute(command);
        verify(updates).addProjectionListener(listener);
        verify(close).saveAndClose();
    }

    @Test
    public void reportsSaveFailureWithoutMarkingTheHandleClosed() {
        GraphCommandRouter router = mock(GraphCommandRouter.class);
        GraphUpdateCoordinator updates = mock(GraphUpdateCoordinator.class);
        WorkspaceCloseController close = mock(WorkspaceCloseController.class);
        GraphCommand command = mock(GraphCommand.class);
        GraphCommandResult result = mock(GraphCommandResult.class);
        when(close.saveAndClose()).thenReturn(false, true);
        when(router.execute(command)).thenReturn(result);
        DefaultGraphWorkspaceHandle handle = new DefaultGraphWorkspaceHandle(router, updates, close);

        assertThatThrownBy(handle::close).isInstanceOf(IllegalStateException.class);
        assertThat(handle.execute(command)).isSameAs(result);
        handle.close();
        handle.close();

        verify(router).execute(command);
        verify(close, org.mockito.Mockito.times(2)).saveAndClose();
    }

    @Test
    public void keepsTheSessionOpenWhenSaveFailsAndReleasesEverythingOnlyAfterRetrySucceeds() throws Exception {
        Path workspace = temporaryFolder.getRoot().toPath().resolve("retry.fpg");
        Files.createFile(workspace);
        WorkspaceSessionRegistry sessions = new WorkspaceSessionRegistry();
        DefaultGraphWorkspaceController.SessionResources resources = resources(false);
        RecordingView view = new RecordingView();
        AtomicReference<WorkspaceCloseController> close = new AtomicReference<WorkspaceCloseController>();
        DefaultGraphWorkspaceController controller = new DefaultGraphWorkspaceController(sessions,
            (path, id, create) -> resources, (handle, binding, closeController) -> {
                close.set(closeController);
                return view;
            });
        GraphWorkspaceHandle handle = controller.open(workspace);
        RuntimeException saveFailure = new IllegalStateException("save failed");
        doThrow(saveFailure).when(resources.store).close();

        assertThat(close.get().saveAndClose()).isFalse();
        assertThat(sessions.owner(workspace)).isPresent();
        assertThat(view.closeCount).hasValue(0);
        verify(resources.updates, never()).close();
        verify(resources.leaseManager, never()).close();
        verify(resources.scheduler, never()).shutdownNow();

        org.mockito.Mockito.doNothing().when(resources.store).close();
        assertThat(close.get().retrySaveAndClose()).isTrue();
        assertThat(sessions.owner(workspace)).isEmpty();
        assertThat(view.closeCount).hasValue(1);
        verify(resources.updates).close();
        verify(resources.leaseManager).close();
        verify(resources.scheduler).shutdownNow();
        handle.close();
    }

    @Test
    public void createsDistinctSessionsForDistinctWorkspacePaths() throws Exception {
        Path firstPath = temporaryFolder.getRoot().toPath().resolve("first.fpg");
        Path secondPath = temporaryFolder.getRoot().toPath().resolve("second.fpg");
        WorkspaceSessionRegistry sessions = new WorkspaceSessionRegistry();
        AtomicInteger factoryCalls = new AtomicInteger();
        DefaultGraphWorkspaceController.SessionResources resources = resources(false);
        DefaultGraphWorkspaceController controller = new DefaultGraphWorkspaceController(sessions,
            (path, id, create) -> {
                factoryCalls.incrementAndGet();
                return resources;
            }, (handle, binding, close) -> new RecordingView());

        GraphWorkspaceHandle first = controller.open(firstPath);
        GraphWorkspaceHandle second = controller.open(secondPath);

        assertThat(first).isNotSameAs(second);
        assertThat(factoryCalls).hasValue(2);
        assertThat(sessions.owner(firstPath)).isPresent();
        assertThat(sessions.owner(secondPath)).isPresent();
    }

    @Test
    public void discardClosesWithoutSavingAndCancelLeavesTheSessionUntouched() throws Exception {
        Path discardPath = temporaryFolder.getRoot().toPath().resolve("discard.fpg");
        Files.createFile(discardPath);
        WorkspaceSessionRegistry discardSessions = new WorkspaceSessionRegistry();
        DefaultGraphWorkspaceController.SessionResources discardResources = resources(false);
        AtomicReference<WorkspaceCloseController> discardClose = new AtomicReference<WorkspaceCloseController>();
        DefaultGraphWorkspaceController discardController = new DefaultGraphWorkspaceController(discardSessions,
            (path, id, create) -> discardResources, (handle, binding, close) -> {
                discardClose.set(close);
                return new RecordingView();
            });
        discardController.open(discardPath);

        assertThat(discardClose.get().discardAndClose()).isTrue();
        verify(discardResources.store).discardAndClose();
        verify(discardResources.store, never()).close();
        assertThat(discardSessions.owner(discardPath)).isEmpty();

        Path cancelPath = temporaryFolder.getRoot().toPath().resolve("cancel.fpg");
        Files.createFile(cancelPath);
        WorkspaceSessionRegistry cancelSessions = new WorkspaceSessionRegistry();
        DefaultGraphWorkspaceController.SessionResources cancelResources = resources(false);
        AtomicReference<WorkspaceCloseController> cancelClose = new AtomicReference<WorkspaceCloseController>();
        DefaultGraphWorkspaceController cancelController = new DefaultGraphWorkspaceController(cancelSessions,
            (path, id, create) -> cancelResources, (handle, binding, close) -> {
                cancelClose.set(close);
                return new RecordingView();
            });
        cancelController.open(cancelPath);

        cancelClose.get().cancelClose();
        verify(cancelResources.store, never()).close();
        verify(cancelResources.store, never()).discardAndClose();
        assertThat(cancelSessions.owner(cancelPath)).isPresent();
    }

    @Test
    public void serializesHandleCommandsWithAViewInitiatedClose() throws Exception {
        Path workspace = temporaryFolder.getRoot().toPath().resolve("serialized-close.fpg");
        Files.createFile(workspace);
        WorkspaceSessionRegistry sessions = new WorkspaceSessionRegistry();
        DefaultGraphWorkspaceController.SessionResources resources = resources(false);
        CountDownLatch saveEntered = new CountDownLatch(1);
        CountDownLatch allowSave = new CountDownLatch(1);
        CountDownLatch commandEntered = new CountDownLatch(1);
        doAnswer(invocation -> {
            saveEntered.countDown();
            allowSave.await(1, TimeUnit.SECONDS);
            return null;
        }).when(resources.store).close();
        AtomicReference<WorkspaceCloseController> close = new AtomicReference<WorkspaceCloseController>();
        DefaultGraphWorkspaceController controller = new DefaultGraphWorkspaceController(sessions,
            (path, id, create) -> resources, (handle, binding, closeController) -> {
                close.set(closeController);
                return new RecordingView();
            });
        GraphWorkspaceHandle handle = controller.open(workspace);
        GraphCommand command = mock(GraphCommand.class);
        AtomicReference<Throwable> commandFailure = new AtomicReference<Throwable>();
        Thread closing = new Thread(() -> close.get().saveAndClose());
        closing.start();
        assertThat(saveEntered.await(1, TimeUnit.SECONDS)).isTrue();

        Thread executing = new Thread(() -> {
            try {
                commandEntered.countDown();
                handle.execute(command);
            }
            catch (Throwable failure) {
                commandFailure.set(failure);
            }
        });
        executing.start();
        assertThat(commandEntered.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(commandFailure.get()).isNull();
        allowSave.countDown();
        closing.join(1000);
        executing.join(1000);

        assertThat(commandFailure.get()).isInstanceOf(IllegalStateException.class);
        verify(resources.router, never()).execute(command);
    }

    @Test
    public void removesANewlyCreatedWorkspaceWhenViewAssemblyFails() throws Exception {
        Path workspace = temporaryFolder.getRoot().toPath().resolve("assembly-failure.fpg");
        WorkspaceSessionRegistry sessions = new WorkspaceSessionRegistry();
        DefaultGraphWorkspaceController.SessionResources resources = resources(true);
        DefaultGraphWorkspaceController controller = new DefaultGraphWorkspaceController(sessions,
            (path, id, create) -> {
                assertThat(create).isTrue();
                try {
                    Files.createFile(path);
                }
                catch (java.io.IOException failure) {
                    throw new IllegalStateException(failure);
                }
                return resources;
            }, (handle, binding, close) -> {
                throw new IllegalStateException("view construction failed");
            });

        assertThatThrownBy(() -> controller.open(workspace))
            .isInstanceOf(GraphWorkspaceOpenException.class);
        assertThat(Files.exists(workspace)).isFalse();
        assertThat(sessions.owner(workspace)).isEmpty();
        verify(resources.store).discardAndClose();
        verify(resources.updates).close();
        verify(resources.leaseManager).close();
        verify(resources.scheduler).shutdownNow();
    }

    @Test
    public void waitsForAClosingSessionBeforeReopeningItsPath() throws Exception {
        Path workspace = temporaryFolder.getRoot().toPath().resolve("waiting-reopen.fpg");
        Files.createFile(workspace);
        WorkspaceSessionRegistry sessions = new WorkspaceSessionRegistry();
        DefaultGraphWorkspaceController.SessionResources resources = resources(false);
        CountDownLatch saveEntered = new CountDownLatch(1);
        CountDownLatch allowSave = new CountDownLatch(1);
        CountDownLatch openFinished = new CountDownLatch(1);
        AtomicInteger factoryCalls = new AtomicInteger();
        AtomicReference<WorkspaceCloseController> close = new AtomicReference<WorkspaceCloseController>();
        AtomicReference<GraphWorkspaceHandle> reopened = new AtomicReference<GraphWorkspaceHandle>();
        doAnswer(invocation -> {
            saveEntered.countDown();
            allowSave.await(1, TimeUnit.SECONDS);
            return null;
        }).when(resources.store).close();
        DefaultGraphWorkspaceController controller = new DefaultGraphWorkspaceController(sessions,
            (path, id, create) -> {
                factoryCalls.incrementAndGet();
                return resources;
            }, (handle, binding, closeController) -> {
                close.set(closeController);
                return new RecordingView();
            });
        GraphWorkspaceHandle first = controller.open(workspace);
        Thread closing = new Thread(() -> close.get().saveAndClose());
        closing.start();
        assertThat(saveEntered.await(1, TimeUnit.SECONDS)).isTrue();
        Thread opening = new Thread(() -> {
            reopened.set(controller.open(workspace));
            openFinished.countDown();
        });
        opening.start();
        assertThat(openFinished.await(100, TimeUnit.MILLISECONDS)).isFalse();
        allowSave.countDown();
        assertThat(openFinished.await(1, TimeUnit.SECONDS)).isTrue();
        closing.join(1000);
        opening.join(1000);

        assertThat(reopened.get()).isNotSameAs(first);
        assertThat(factoryCalls).hasValue(2);
    }

    @Test
    public void allowsImmediateReopenAfterACompleteClose() throws Exception {
        Path workspace = temporaryFolder.getRoot().toPath().resolve("reopen.fpg");
        Files.createFile(workspace);
        WorkspaceSessionRegistry sessions = new WorkspaceSessionRegistry();
        AtomicInteger factoryCalls = new AtomicInteger();
        AtomicReference<WorkspaceCloseController> close = new AtomicReference<WorkspaceCloseController>();
        DefaultGraphWorkspaceController.SessionResources resources = resources(false);
        DefaultGraphWorkspaceController controller = new DefaultGraphWorkspaceController(sessions,
            (path, id, create) -> {
                factoryCalls.incrementAndGet();
                return resources;
            }, (handle, binding, closeController) -> {
                close.set(closeController);
                return new RecordingView();
            });

        controller.open(workspace);
        assertThat(close.get().saveAndClose()).isTrue();
        GraphWorkspaceHandle reopened = controller.open(workspace);

        assertThat(reopened).isNotNull();
        assertThat(factoryCalls).hasValue(2);
        assertThat(sessions.owner(workspace)).isPresent();
    }
    @Test
    public void closesStoreUpdatesLeasesSchedulerAndViewInDependencyOrder() throws Exception {
        Path workspace = temporaryFolder.getRoot().toPath().resolve("close-order.fpg");
        Files.createFile(workspace);
        WorkspaceSessionRegistry sessions = new WorkspaceSessionRegistry();
        DefaultGraphWorkspaceController.SessionResources resources = resources(false);
        GraphWorkspaceView view = mock(GraphWorkspaceView.class);
        AtomicReference<WorkspaceCloseController> close = new AtomicReference<WorkspaceCloseController>();
        DefaultGraphWorkspaceController controller = new DefaultGraphWorkspaceController(sessions,
            (path, id, create) -> resources, (handle, binding, closeController) -> {
                close.set(closeController);
                return view;
            });
        controller.open(workspace);

        assertThat(close.get().saveAndClose()).isTrue();
        InOrder order = inOrder(view, resources.store, resources.updates, resources.leaseManager,
            resources.scheduler);
        order.verify(view).show();
        order.verify(resources.store).close();
        order.verify(resources.updates).close();
        order.verify(resources.leaseManager).close();
        order.verify(resources.scheduler).shutdownNow();
        order.verify(view).close();
    }

    @Test
    public void reservesTheWorkspacePathBeforeCreatingItsStore() throws Exception {
        Path workspace = temporaryFolder.getRoot().toPath().resolve("reserved-before-open.fpg");
        WorkspaceSessionRegistry sessions = new WorkspaceSessionRegistry();
        DefaultGraphWorkspaceController.SessionResources resources = resources(false);
        CountDownLatch factoryEntered = new CountDownLatch(1);
        CountDownLatch allowFactory = new CountDownLatch(1);
        AtomicReference<GraphWorkspaceHandle> opened = new AtomicReference<GraphWorkspaceHandle>();
        AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        DefaultGraphWorkspaceController controller = new DefaultGraphWorkspaceController(sessions,
            (path, id, create) -> {
                assertThat(sessions.owner(path)).contains(id);
                factoryEntered.countDown();
                try {
                    allowFactory.await(1, TimeUnit.SECONDS);
                }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
                return resources;
            }, (handle, binding, close) -> new RecordingView());
        Thread opening = new Thread(() -> {
            try {
                opened.set(controller.open(workspace));
            }
            catch (Throwable exception) {
                failure.set(exception);
            }
        }, "graph-workspace-opening-reservation-test");
        opening.start();

        assertThat(factoryEntered.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(sessions.owner(workspace)).isPresent();
        allowFactory.countDown();
        opening.join(1000);

        assertThat(failure.get()).isNull();
        assertThat(opened.get()).isNotNull();
    }

    @Test
    public void doesNotBlockTheEdtWhileCoordinatorShutdownNeedsTheEdt() throws Exception {
        Path workspace = temporaryFolder.getRoot().toPath().resolve("edt-close.fpg");
        Files.createFile(workspace);
        WorkspaceSessionRegistry sessions = new WorkspaceSessionRegistry();
        DefaultGraphWorkspaceController.SessionResources resources = resources(false);
        GraphWorkspaceView view = mock(GraphWorkspaceView.class);
        CountDownLatch viewClosed = new CountDownLatch(1);
        AtomicReference<WorkspaceCloseController> close = new AtomicReference<WorkspaceCloseController>();
        AtomicReference<Boolean> result = new AtomicReference<Boolean>();
        CountDownLatch closeReturned = new CountDownLatch(1);
        doAnswer(invocation -> {
            SwingUtilities.invokeAndWait(() -> {
            });
            return null;
        }).when(resources.updates).close();
        doAnswer(invocation -> {
            assertThat(SwingUtilities.isEventDispatchThread()).isTrue();
            viewClosed.countDown();
            return null;
        }).when(view).close();
        DefaultGraphWorkspaceController controller = new DefaultGraphWorkspaceController(sessions,
            (path, id, create) -> resources, (handle, binding, closeController) -> {
                close.set(closeController);
                return view;
            });
        controller.open(workspace);

        Thread caller = new Thread(() -> {
            try {
                SwingUtilities.invokeAndWait(() -> result.set(close.get().saveAndClose()));
            }
            catch (Exception failure) {
                throw new AssertionError(failure);
            }
            finally {
                closeReturned.countDown();
            }
        }, "graph-workspace-edt-close-test");
        caller.setDaemon(true);
        caller.start();

        assertThat(closeReturned.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(result.get()).isTrue();
        assertThat(viewClosed.await(1, TimeUnit.SECONDS)).isTrue();
        caller.join(1000);
        awaitOwnerAbsent(sessions, workspace);
        verify(resources.updates).close();
        verify(resources.leaseManager).close();
        verify(resources.scheduler).shutdownNow();
        verify(view).close();
    }
    @Test
    public void reportsAsyncTeardownFailureAfterTheSessionIsReleased() throws Exception {
        Path workspace = temporaryFolder.getRoot().toPath().resolve("async-close-failure.fpg");
        Files.createFile(workspace);
        WorkspaceSessionRegistry sessions = new WorkspaceSessionRegistry();
        DefaultGraphWorkspaceController.SessionResources resources = resources(false);
        GraphWorkspaceView view = mock(GraphWorkspaceView.class);
        AtomicReference<WorkspaceCloseController> close = new AtomicReference<WorkspaceCloseController>();
        AtomicReference<Boolean> result = new AtomicReference<Boolean>();
        CountDownLatch reported = new CountDownLatch(1);
        AtomicReference<Throwable> reportedFailure = new AtomicReference<Throwable>();
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, failure) -> {
            reportedFailure.set(failure);
            reported.countDown();
        });
        doThrow(new IllegalStateException("lease teardown failed")).when(resources.leaseManager).close();
        try {
            DefaultGraphWorkspaceController controller = new DefaultGraphWorkspaceController(sessions,
                (path, id, create) -> resources, (handle, binding, closeController) -> {
                    close.set(closeController);
                    return view;
                });
            controller.open(workspace);
            SwingUtilities.invokeAndWait(() -> result.set(close.get().saveAndClose()));

            assertThat(result).hasValue(true);
            assertThat(reported.await(1, TimeUnit.SECONDS)).isTrue();
            awaitOwnerAbsent(sessions, workspace);
            assertThat(reportedFailure.get()).isInstanceOf(IllegalStateException.class)
                .hasMessage("lease teardown failed");
            verify(view).close();
        }
        finally {
            Thread.setDefaultUncaughtExceptionHandler(previous);
        }
    }

    @Test
    public void reportsAsyncRollbackFailureAfterTheSessionIsReleased() throws Exception {
        Path workspace = temporaryFolder.getRoot().toPath().resolve("async-rollback-failure.fpg");
        Files.createFile(workspace);
        WorkspaceSessionRegistry sessions = new WorkspaceSessionRegistry();
        DefaultGraphWorkspaceController.SessionResources resources = resources(false);
        CountDownLatch reported = new CountDownLatch(1);
        AtomicReference<Throwable> reportedFailure = new AtomicReference<Throwable>();
        AtomicReference<Boolean> ownerPresentAtReport = new AtomicReference<Boolean>();
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, failure) -> {
            reportedFailure.set(failure);
            ownerPresentAtReport.set(sessions.owner(workspace).isPresent());
            reported.countDown();
        });
        doThrow(new IllegalStateException("lease teardown failed")).when(resources.leaseManager).close();
        try {
            DefaultGraphWorkspaceController controller = new DefaultGraphWorkspaceController(sessions,
                (path, id, create) -> resources, (handle, binding, closeController) -> {
                    throw new IllegalStateException("view assembly failed");
                });
            AtomicReference<Throwable> openFailure = new AtomicReference<Throwable>();
            SwingUtilities.invokeAndWait(() -> {
                try {
                    controller.open(workspace);
                }
                catch (Throwable failure) {
                    openFailure.set(failure);
                }
            });

            assertThat(openFailure.get()).isInstanceOf(GraphWorkspaceOpenException.class);
            assertThat(reported.await(1, TimeUnit.SECONDS)).isTrue();
            awaitOwnerAbsent(sessions, workspace);
            assertThat(ownerPresentAtReport).hasValue(false);
            assertThat(reportedFailure.get()).isInstanceOf(IllegalStateException.class)
                .hasMessage("lease teardown failed");
        }
        finally {
            Thread.setDefaultUncaughtExceptionHandler(previous);
        }
    }

    @Test
    public void doesNotDeleteAReplacementFileDuringDelayedRollback() throws Exception {
        Path workspace = temporaryFolder.getRoot().toPath().resolve("replacement-during-rollback.fpg");
        WorkspaceSessionRegistry sessions = new WorkspaceSessionRegistry();
        DefaultGraphWorkspaceController.SessionResources resources = resources(true);
        CountDownLatch cleanupEntered = new CountDownLatch(1);
        CountDownLatch allowCleanup = new CountDownLatch(1);
        doAnswer(invocation -> {
            cleanupEntered.countDown();
            allowCleanup.await(1, TimeUnit.SECONDS);
            return null;
        }).when(resources.updates).close();
        DefaultGraphWorkspaceController controller = new DefaultGraphWorkspaceController(sessions,
            (path, id, create) -> {
                try {
                    Files.write(path, new byte[] { 1, 2, 3 });
                }
                catch (java.io.IOException failure) {
                    throw new IllegalStateException(failure);
                }
                return resources;
            }, (handle, binding, close) -> {
                throw new IllegalStateException("view assembly failed");
            });
        AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                controller.open(workspace);
            }
            catch (Throwable exception) {
                failure.set(exception);
            }
        });

        assertThat(failure.get()).isInstanceOf(GraphWorkspaceOpenException.class);
        assertThat(cleanupEntered.await(1, TimeUnit.SECONDS)).isTrue();
        BasicFileAttributes originalAttributes = Files.readAttributes(workspace, BasicFileAttributes.class);
        Path replacement = workspace.resolveSibling("replacement-during-rollback.replacement");
        Files.write(replacement, new byte[] { 9, 8, 7 });
        BasicFileAttributes replacementAttributes = Files.readAttributes(replacement, BasicFileAttributes.class);
        assertThat(originalAttributes.fileKey()).isNotNull();
        assertThat(replacementAttributes.fileKey()).isNotEqualTo(originalAttributes.fileKey());

        Files.move(replacement, workspace, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        allowCleanup.countDown();
        awaitPathPresent(workspace);
        awaitOwnerAbsent(sessions, workspace);
        assertThat(Files.readAllBytes(workspace)).containsExactly(9, 8, 7);
    }

    @Test
    public void keepsAProductionCreatedFileUntilEdtAssemblyRollbackFinishes() throws Exception {
        Path workspace = temporaryFolder.getRoot().toPath().resolve("production-rollback.fpg");
        ModeController modeController = mock(ModeController.class);
        MMapController mapController = mock(MMapController.class);
        Controller applicationController = mock(Controller.class);
        IMapViewManager viewManager = mock(IMapViewManager.class);
        MLinkController linkController = mock(MLinkController.class);
        CountDownLatch listenerRemovalEntered = new CountDownLatch(1);
        CountDownLatch allowListenerRemoval = new CountDownLatch(1);
        when(modeController.getMapController()).thenReturn(mapController);
        when(modeController.getController()).thenReturn(applicationController);
        when(applicationController.getMapViewManager()).thenReturn(viewManager);
        when(modeController.getExtension(org.freeplane.features.link.LinkController.class))
            .thenReturn(linkController);
        org.mockito.Mockito.doAnswer(invocation -> {
            listenerRemovalEntered.countDown();
            allowListenerRemoval.await(1, TimeUnit.SECONDS);
            return null;
        }).when(mapController).removeMapLifeCycleListener(any());

        DefaultGraphWorkspaceController controller = new DefaultGraphWorkspaceController(modeController,
            (handle, binding, close) -> {
                throw new IllegalStateException("view assembly failed");
            });
        AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                controller.open(workspace);
            }
            catch (Throwable exception) {
                failure.set(exception);
            }
        });

        assertThat(failure.get()).isInstanceOf(GraphWorkspaceOpenException.class);
        assertThat(listenerRemovalEntered.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(Files.exists(workspace)).isTrue();
        allowListenerRemoval.countDown();
        awaitPathAbsent(workspace);
    }

    @Test
    public void opensAndClosesThroughTheProductionResourceComposition() throws Exception {
        Path workspace = temporaryFolder.getRoot().toPath().resolve("production-composition.fpg");
        ModeController modeController = mock(ModeController.class);
        MMapController mapController = mock(MMapController.class);
        Controller applicationController = mock(Controller.class);
        IMapViewManager viewManager = mock(IMapViewManager.class);
        MLinkController linkController = mock(MLinkController.class);
        when(modeController.getMapController()).thenReturn(mapController);
        when(modeController.getController()).thenReturn(applicationController);
        when(applicationController.getMapViewManager()).thenReturn(viewManager);
        when(modeController.getExtension(org.freeplane.features.link.LinkController.class))
            .thenReturn(linkController);

        GraphWorkspaceView view = mock(GraphWorkspaceView.class);
        DefaultGraphWorkspaceController controller = new DefaultGraphWorkspaceController(modeController,
            (handle, binding, close) -> view);

        GraphWorkspaceHandle handle = controller.open(workspace);

        assertThat(Files.exists(workspace)).isTrue();
        handle.execute(org.freeplane.plugin.graph.command.GraphCommands.save());
        handle.close();
        verify(mapController).addMapLifeCycleListener(any());
        verify(mapController).removeMapLifeCycleListener(any());
        verify(view).show();
        verify(view).close();
    }

    private static void awaitPathPresent(final Path workspace) throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (!Files.exists(workspace) && System.nanoTime() < deadline) {
            Thread.sleep(5L);
        }
        assertThat(Files.exists(workspace)).isTrue();
    }

    private static void awaitPathAbsent(final Path workspace) throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (Files.exists(workspace) && System.nanoTime() < deadline) {
            Thread.sleep(5L);
        }
        assertThat(Files.exists(workspace)).isFalse();
    }

    private static void awaitOwnerAbsent(final WorkspaceSessionRegistry sessions, final Path workspace)
            throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (sessions.owner(workspace).isPresent() && System.nanoTime() < deadline) {
            Thread.sleep(5L);
        }
        assertThat(sessions.owner(workspace)).isEmpty();
    }

    private DefaultGraphWorkspaceController.SessionResources resources(boolean newlyCreated) {
        return new DefaultGraphWorkspaceController.SessionResources(mock(GraphWorkspaceStore.class),
            mock(GraphUpdateCoordinator.class), mock(MapLeaseManager.class), mock(GraphCommandRouter.class),
            mock(ScheduledExecutorService.class), newlyCreated);
    }

    private static final class RecordingView implements GraphWorkspaceView {
        private final AtomicInteger showCount = new AtomicInteger();
        private final AtomicInteger focusCount = new AtomicInteger();
        private final AtomicInteger closeCount = new AtomicInteger();

        @Override
        public void show() {
            showCount.incrementAndGet();
        }

        @Override
        public void focus() {
            focusCount.incrementAndGet();
        }

        @Override
        public void close() {
            closeCount.incrementAndGet();
        }
    }
}
