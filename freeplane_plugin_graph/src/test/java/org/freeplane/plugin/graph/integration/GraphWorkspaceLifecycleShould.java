package org.freeplane.plugin.graph.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.freeplane.core.undo.IActor;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.ModeController;
import org.freeplane.plugin.graph.command.GraphCommands;
import org.freeplane.plugin.graph.control.DefaultGraphWorkspaceController;
import org.freeplane.plugin.graph.control.GraphWorkspaceHandle;
import org.freeplane.plugin.graph.control.GraphWorkspaceViewBinding;
import org.freeplane.plugin.graph.control.WorkspaceCloseController;
import org.freeplane.plugin.graph.workspace.GraphCommandResult;
import org.freeplane.plugin.graph.workspace.ListenerRegistration;
import org.freeplane.plugin.graph.workspace.io.WorkspaceMigrationRegistry;
import org.freeplane.plugin.graph.workspace.io.WorkspaceXmlCodec;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.Viewport;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class GraphWorkspaceLifecycleShould {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void returnsProductionResourcesToBaselineAcrossTwentyFiveOpenCloseRestartCycles() throws Exception {
        final GraphWorkspaceIntegrationSupport.FreeplaneScope freeplane =
            new GraphWorkspaceIntegrationSupport.FreeplaneScope();
        DefaultGraphWorkspaceController controller = null;
        GraphWorkspaceHandle openHandle = null;
        try {
            freeplane.observeMaps();
            final Path root = temporaryFolder.getRoot().toPath();
            final GraphWorkspaceIntegrationSupport.ResourceBaseline baseline =
                GraphWorkspaceIntegrationSupport.baseline(freeplane, root);
            final Path sourceMap = root.resolve("cycle-source.mm");
            GraphWorkspaceIntegrationSupport.copyFixture(sourceMap);
            final Path workspace = root.resolve("cycle.fpg");
            final GraphWorkspaceIntegrationSupport.RecordingViewFactory views =
                new GraphWorkspaceIntegrationSupport.RecordingViewFactory();
            controller = new DefaultGraphWorkspaceController(freeplane.modeController(), views);
            final MapReferenceId mapId = MapReferenceId.of(UUID.nameUUIDFromBytes(
                sourceMap.toAbsolutePath().toString().getBytes("UTF-8")));

            for (int cycle = 0; cycle < 25; cycle++) {
                openHandle = controller.open(workspace);
                if (cycle == 0) {
                    final GraphCommandResult addMap = openHandle.execute(GraphCommands.addMap(mapId,
                        sourceMap.toUri()));
                    assertThat(addMap.status()).isEqualTo(GraphCommandResult.Status.APPLIED);
                }
                GraphWorkspaceIntegrationSupport.awaitProjection(openHandle, 1);
                assertThat(views.activeViews()).isEqualTo(1);
                assertThat(freeplane.mapLifecycleListenerCount())
                    .as("lease lifecycle listener during cycle %s", cycle)
                    .isGreaterThan(baseline.mapLifecycleListeners);
                openHandle.close();
                openHandle = null;
                assertThat(views.activeViews()).isZero();

                // Check lease detachment while the viewless source model is still observable.
                GraphWorkspaceIntegrationSupport.awaitBaseline(freeplane, root, baseline);
                freeplane.closeMapAt(sourceMap);
                GraphWorkspaceIntegrationSupport.awaitBaseline(freeplane, root, baseline);
            }

            assertThat(views.showCalls()).isEqualTo(25);
            assertThat(views.closeCalls()).isEqualTo(25);
        }
        finally {
            if (openHandle != null) {
                try {
                    openHandle.close();
                }
                catch (RuntimeException ignored) {
                    // The controller shutdown below remains responsible for final cleanup.
                }
            }
            if (controller != null) {
                try {
                    controller.shutdown();
                }
                catch (RuntimeException ignored) {
                    // Preserve the first failure from the cycle assertions.
                }
            }
            freeplane.close();
        }
    }

    @Test
    public void savesDirtyWorkspaceSynchronouslyWhenClosedDuringDebounce() throws Exception {
        final GraphWorkspaceIntegrationSupport.FreeplaneScope freeplane =
            new GraphWorkspaceIntegrationSupport.FreeplaneScope();
        DefaultGraphWorkspaceController controller = null;
        GraphWorkspaceHandle handle = null;
        try {
            final Path root = temporaryFolder.getRoot().toPath();
            freeplane.observeMaps();
            final GraphWorkspaceIntegrationSupport.ResourceBaseline baseline =
                GraphWorkspaceIntegrationSupport.baseline(freeplane, root);
            final Path workspace = root.resolve("debounce.fpg");
            final GraphWorkspaceIntegrationSupport.RecordingViewFactory views =
                new GraphWorkspaceIntegrationSupport.RecordingViewFactory();
            controller = new DefaultGraphWorkspaceController(freeplane.modeController(), views);
            handle = controller.open(workspace);
            final Viewport changed = Viewport.of(31.0, -17.0, 1.75, Collections.emptyList());
            assertThat(handle.execute(GraphCommands.viewport(changed)).status())
                .isEqualTo(GraphCommandResult.Status.APPLIED);
            handle.close();
            handle = null;

            final WorkspaceXmlCodec codec = productionCodec();
            assertThat(codec.read(workspace).viewport()).isEqualTo(changed);
            assertThat(GraphWorkspaceIntegrationSupport.temporaryArtifacts(root)).isEmpty();
            GraphWorkspaceIntegrationSupport.awaitBaseline(freeplane, root, baseline);
        }
        finally {
            if (handle != null) {
                try {
                    handle.close();
                }
                catch (RuntimeException ignored) {
                }
            }
            if (controller != null) {
                try {
                    controller.shutdown();
                }
                catch (RuntimeException ignored) {
                }
            }
            freeplane.close();
        }
    }

    @Test
    public void preservesFailedCloseRetryDiscardAndCancelSemantics() throws Exception {
        final GraphWorkspaceIntegrationSupport.FreeplaneScope freeplane =
            new GraphWorkspaceIntegrationSupport.FreeplaneScope();
        DefaultGraphWorkspaceController controller = null;
        try {
            final Path root = temporaryFolder.getRoot().toPath();
            freeplane.observeMaps();
            final GraphWorkspaceIntegrationSupport.ResourceBaseline baseline =
                GraphWorkspaceIntegrationSupport.baseline(freeplane, root);
            final GraphWorkspaceIntegrationSupport.RecordingViewFactory views =
                new GraphWorkspaceIntegrationSupport.RecordingViewFactory();
            controller = new DefaultGraphWorkspaceController(freeplane.modeController(), views);
            final WorkspaceXmlCodec codec = productionCodec();

            final Path retryWorkspace = root.resolve("retry.fpg");
            GraphWorkspaceHandle retryHandle = controller.open(retryWorkspace);
            final Viewport retryViewport = Viewport.of(4.0, 5.0, 1.2, Collections.emptyList());
            retryHandle.execute(GraphCommands.viewport(retryViewport));
            final Path retryPreserved = GraphWorkspaceIntegrationSupport.blockWorkspaceTarget(retryWorkspace);
            try {
                assertThatThrownBy(retryHandle::close)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Unable to save graph workspace while closing");
            }
            finally {
                GraphWorkspaceIntegrationSupport.restoreWorkspaceTarget(retryWorkspace, retryPreserved);
            }
            final WorkspaceCloseController retryClose = views.latestCloseController();
            assertThat(retryClose.retrySaveAndClose()).isTrue();
            assertThat(codec.read(retryWorkspace).viewport()).isEqualTo(retryViewport);
            GraphWorkspaceIntegrationSupport.awaitBaseline(freeplane, root, baseline);

            final Path cancelWorkspace = root.resolve("cancel.fpg");
            GraphWorkspaceHandle cancelHandle = controller.open(cancelWorkspace);
            final Viewport firstCancelViewport = Viewport.of(7.0, 8.0, 1.3, Collections.emptyList());
            final Viewport secondCancelViewport = Viewport.of(9.0, 10.0, 1.4, Collections.emptyList());
            cancelHandle.execute(GraphCommands.viewport(firstCancelViewport));
            final Path cancelPreserved = GraphWorkspaceIntegrationSupport.blockWorkspaceTarget(cancelWorkspace);
            try {
                assertThatThrownBy(cancelHandle::close)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Unable to save graph workspace while closing");
            }
            finally {
                GraphWorkspaceIntegrationSupport.restoreWorkspaceTarget(cancelWorkspace, cancelPreserved);
            }
            final WorkspaceCloseController cancelClose = views.latestCloseController();
            cancelClose.cancelClose();
            assertThat(cancelHandle.execute(GraphCommands.viewport(secondCancelViewport)).status())
                .isEqualTo(GraphCommandResult.Status.APPLIED);
            assertThat(cancelClose.retrySaveAndClose()).isTrue();
            assertThat(codec.read(cancelWorkspace).viewport()).isEqualTo(secondCancelViewport);
            GraphWorkspaceIntegrationSupport.awaitBaseline(freeplane, root, baseline);

            final Path discardWorkspace = root.resolve("discard.fpg");
            GraphWorkspaceHandle discardHandle = controller.open(discardWorkspace);
            final Viewport discardViewport = Viewport.of(11.0, 12.0, 1.5, Collections.emptyList());
            discardHandle.execute(GraphCommands.viewport(discardViewport));
            final Path discardPreserved = GraphWorkspaceIntegrationSupport.blockWorkspaceTarget(discardWorkspace);
            Files.deleteIfExists(discardPreserved);
            final WorkspaceCloseController discardClose = views.latestCloseController();
            assertThat(discardClose.discardAndClose()).isTrue();
            assertThat(Files.isDirectory(discardWorkspace)).as("discard leaves the failed target untouched").isTrue();
            GraphWorkspaceIntegrationSupport.deleteRecursively(discardWorkspace);
            assertThatThrownBy(() -> discardHandle.execute(GraphCommands.viewport(discardViewport)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Graph workspace handle is not open");
            GraphWorkspaceIntegrationSupport.awaitBaseline(freeplane, root, baseline);
        }
        finally {
            if (controller != null) {
                try {
                    controller.shutdown();
                }
                catch (RuntimeException ignored) {
                }
            }
            freeplane.close();
        }
    }

    @Test
    public void doesNotDeliverCallbacksAfterClose() throws Exception {
        final GraphWorkspaceIntegrationSupport.FreeplaneScope freeplane =
            new GraphWorkspaceIntegrationSupport.FreeplaneScope();
        DefaultGraphWorkspaceController controller = null;
        GraphWorkspaceHandle handle = null;
        ListenerRegistration projectionRegistration = null;
        ListenerRegistration secondProjectionRegistration = null;
        ListenerRegistration canvasRegistration = null;
        ListenerRegistration statusRegistration = null;
        try {
            final Path root = temporaryFolder.getRoot().toPath();
            freeplane.observeMaps();
            final GraphWorkspaceIntegrationSupport.ResourceBaseline baseline =
                GraphWorkspaceIntegrationSupport.baseline(freeplane, root);
            final Path sourceMap = root.resolve("callback-source.mm");
            GraphWorkspaceIntegrationSupport.copyFixture(sourceMap);
            final Path workspace = root.resolve("callbacks.fpg");
            final GraphWorkspaceIntegrationSupport.RecordingViewFactory views =
                new GraphWorkspaceIntegrationSupport.RecordingViewFactory();
            controller = new DefaultGraphWorkspaceController(freeplane.modeController(), views);
            handle = controller.open(workspace);
            final MapReferenceId mapId = MapReferenceId.of(UUID.nameUUIDFromBytes(
                sourceMap.toAbsolutePath().toString().getBytes("UTF-8")));
            assertThat(handle.execute(GraphCommands.addMap(mapId, sourceMap.toUri())).status())
                .isEqualTo(GraphCommandResult.Status.APPLIED);
            GraphWorkspaceIntegrationSupport.awaitProjection(handle, 1);
            assertThat(freeplane.mapLifecycleListenerCount())
                .as("workspace map lifecycle listener before close")
                .isGreaterThan(baseline.mapLifecycleListeners);
            final GraphWorkspaceViewBinding binding = views.latestBinding();
            final AtomicInteger projectionCallbacks = new AtomicInteger();
            final AtomicInteger secondProjectionCallbacks = new AtomicInteger();
            final AtomicInteger canvasCallbacks = new AtomicInteger();
            final AtomicInteger statusCallbacks = new AtomicInteger();
            final CountDownLatch firstProjectionEntered = new CountDownLatch(1);
            final CountDownLatch releaseFirstProjection = new CountDownLatch(1);
            final GraphWorkspaceHandle callbackHandle = handle;
            projectionRegistration = callbackHandle.addProjectionListener(projection -> {
                projectionCallbacks.incrementAndGet();
                firstProjectionEntered.countDown();
                try {
                    releaseFirstProjection.await(5, TimeUnit.SECONDS);
                }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            });
            secondProjectionRegistration = callbackHandle.addProjectionListener(
                projection -> secondProjectionCallbacks.incrementAndGet());
            canvasRegistration = binding.addCanvasStateListener(state -> canvasCallbacks.incrementAndGet());
            statusRegistration = binding.addSessionStatusListener(status -> statusCallbacks.incrementAndGet());

            assertThat(callbackHandle.execute(GraphCommands.viewport(Viewport.of(13, 14, 1.6,
                Collections.emptyList()))).status()).isEqualTo(GraphCommandResult.Status.APPLIED);
            assertThat(firstProjectionEntered.await(5, TimeUnit.SECONDS)).isTrue();
            final AtomicReference<Throwable> closeFailure = new AtomicReference<Throwable>();
            final Thread closing = new Thread(() -> {
                try {
                    callbackHandle.close();
                }
                catch (Throwable failure) {
                    closeFailure.set(failure);
                }
            }, "graph-workspace-stale-callback-close-probe");
            closing.start();
            try {
                GraphWorkspaceIntegrationSupport.awaitCondition(() -> {
                    try {
                        callbackHandle.execute(GraphCommands.viewport(Viewport.of(15, 16, 1.7,
                            Collections.emptyList())));
                        return false;
                    }
                    catch (IllegalStateException expected) {
                        return true;
                    }
                }, 5000L, "close did not cross the handle closing boundary");
                GraphWorkspaceIntegrationSupport.awaitCondition(
                    () -> freeplane.mapLifecycleListenerCount() == baseline.mapLifecycleListeners,
                    5000L, "close did not reach the graph update coordinator callback boundary");
            }
            finally {
                releaseFirstProjection.countDown();
                closing.join(10000L);
            }
            assertThat(closing.isAlive()).as("close thread completed").isFalse();
            assertThat(closeFailure.get()).isNull();
            handle = null;
            final int projectionAfterClose = projectionCallbacks.get();
            final int canvasAfterClose = canvasCallbacks.get();
            final int statusAfterClose = statusCallbacks.get();
            assertThat(secondProjectionCallbacks).as("stale projection listener after close").hasValue(0);
            final MapModel loadedMap = freeplane.mapAt(sourceMap);
            if (loadedMap != null) {
                final NodeModel rootNode = loadedMap.getRootNode();
                final ModeController modeController = freeplane.modeController();
                final MapController mapController = modeController.getMapController();
                modeController.execute(new IActor() {
                    @Override
                    public void act() {
                        rootNode.setText(rootNode.getText() + "-after-close");
                        mapController.nodeChanged(rootNode);
                    }

                    @Override
                    public String getDescription() {
                        return "graph-workspace-callback-after-close-probe";
                    }

                    @Override
                    public void undo() {
                        rootNode.setText(rootNode.getText().replace("-after-close", ""));
                        mapController.nodeChanged(rootNode);
                    }
                }, loadedMap);
                freeplane.closeMap(loadedMap);
            }
            GraphWorkspaceIntegrationSupport.awaitPostCloseCompletion(freeplane, root, baseline);
            assertThat(projectionCallbacks).hasValue(projectionAfterClose);
            assertThat(canvasCallbacks).hasValue(canvasAfterClose);
            assertThat(statusCallbacks).hasValue(statusAfterClose);
            assertThatThrownBy(() -> callbackHandle.execute(GraphCommands.viewport(Viewport.of(0, 0, 1,
                Collections.emptyList()))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Graph workspace handle is not open");
        }
        finally {
            closeRegistration(projectionRegistration);
            closeRegistration(secondProjectionRegistration);
            closeRegistration(canvasRegistration);
            closeRegistration(statusRegistration);
            if (handle != null) {
                try {
                    handle.close();
                }
                catch (RuntimeException ignored) {
                }
            }
            if (controller != null) {
                try {
                    controller.shutdown();
                }
                catch (RuntimeException ignored) {
                }
            }
            freeplane.close();
        }
    }

    private static WorkspaceXmlCodec productionCodec() {
        return new WorkspaceXmlCodec(new WorkspaceMigrationRegistry(Collections.emptyList()));
    }

    private static void closeRegistration(final ListenerRegistration registration) {
        if (registration != null) {
            registration.close();
        }
    }
}
