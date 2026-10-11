package org.freeplane.plugin.graph.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.freeplane.core.undo.IActor;
import org.freeplane.core.undo.IUndoHandler;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.plugin.graph.command.GraphCommands;
import org.freeplane.plugin.graph.control.CanvasState;
import org.freeplane.plugin.graph.control.DefaultGraphWorkspaceController;
import org.freeplane.plugin.graph.control.GraphWorkspaceHandle;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.workspace.GraphCommandResult;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class GraphWorkspaceLiveNodeRefreshShould {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static final String LIVE_REFRESH_FIXTURE = "<map version=\"freeplane 1.12.15\">\n"
        + "  <node TEXT=\"live refresh root\" ID=\"ID_ROOT\">\n"
        + "    <node TEXT=\"alpha\" ID=\"ID_ALPHA\">\n"
        + "      <graph_group version=\"1\"/>\n"
        + "    </node>\n"
        + "    <node TEXT=\"bravo\" ID=\"ID_BRAVO\">\n"
        + "      <graph_group version=\"1\"/>\n"
        + "    </node>\n"
        + "  </node>\n"
        + "</map>\n";

    @Test
    public void nodeTextEditRefreshesTheLiveProjection() throws Exception {
        final GraphWorkspaceIntegrationSupport.FreeplaneScope freeplane =
            new GraphWorkspaceIntegrationSupport.FreeplaneScope();
        DefaultGraphWorkspaceController controller = null;
        GraphWorkspaceHandle handle = null;
        try {
            final Path sourceMapFile = temporaryFolder.getRoot().toPath().resolve("live-refresh.mm");
            Files.write(sourceMapFile, LIVE_REFRESH_FIXTURE.getBytes(StandardCharsets.UTF_8));
            freeplane.installGraphGroups();
            final MapModel sourceMap = freeplane.loadWithView(sourceMapFile);
            final GraphWorkspaceIntegrationSupport.RecordingViewFactory views =
                new GraphWorkspaceIntegrationSupport.RecordingViewFactory();
            controller = new DefaultGraphWorkspaceController(freeplane.modeController(), views);
            final Path workspaceFile = temporaryFolder.getRoot().toPath().resolve("live-refresh.fpg");
            handle = controller.open(workspaceFile);
            final GraphWorkspaceHandle liveHandle = handle;
            final MapReferenceId mapId = MapReferenceId.of(UUID.nameUUIDFromBytes(
                sourceMapFile.toAbsolutePath().toString().getBytes(StandardCharsets.UTF_8)));
            final GraphCommandResult addMap = handle.execute(
                GraphCommands.addMap(mapId, sourceMapFile.toUri()));
            assertThat(addMap.status()).isEqualTo(GraphCommandResult.Status.APPLIED);
            GraphWorkspaceIntegrationSupport.awaitCondition(
                () -> GraphWorkspaceIntegrationSupport.hasNodeLabel(liveHandle.currentProjection(), "alpha"),
                15000L, "initial live label did not appear");

            final NodeModel alpha = sourceMap.getRootNode().getChildAt(0);
            final String oldText = alpha.getText();
            final String newText = "omega";
            freeplane.modeController().execute(
                textEdit(freeplane.modeController(), alpha, oldText, newText), sourceMap);

            GraphWorkspaceIntegrationSupport.awaitCondition(
                () -> GraphWorkspaceIntegrationSupport.hasNodeLabel(liveHandle.currentProjection(), "omega")
                    && !GraphWorkspaceIntegrationSupport.hasNodeLabel(liveHandle.currentProjection(), "alpha"),
                15000L, "edited label did not reach the live projection");
        }
        finally {
            if (handle != null) {
                try {
                    handle.close();
                }
                catch (RuntimeException ignored) {
                    // The controller shutdown below performs deterministic discard cleanup for a failed close.
                }
            }
            if (controller != null) {
                try {
                    controller.shutdown();
                }
                catch (RuntimeException ignored) {
                    // Preserve the original assertion failure while still releasing the headless Freeplane scope.
                }
            }
            freeplane.close();
        }
    }

    @Test
    public void nodeTextEditPreservesUnaffectedNodePositions() throws Exception {
        final GraphWorkspaceIntegrationSupport.FreeplaneScope freeplane =
            new GraphWorkspaceIntegrationSupport.FreeplaneScope();
        DefaultGraphWorkspaceController controller = null;
        GraphWorkspaceHandle handle = null;
        try {
            final Path sourceMapFile = temporaryFolder.getRoot().toPath().resolve("live-refresh-positions.mm");
            Files.write(sourceMapFile, LIVE_REFRESH_FIXTURE.getBytes(StandardCharsets.UTF_8));
            freeplane.installGraphGroups();
            final MapModel sourceMap = freeplane.loadWithView(sourceMapFile);
            final GraphWorkspaceIntegrationSupport.RecordingViewFactory views =
                new GraphWorkspaceIntegrationSupport.RecordingViewFactory();
            controller = new DefaultGraphWorkspaceController(freeplane.modeController(), views);
            final Path workspaceFile = temporaryFolder.getRoot().toPath().resolve("live-refresh-positions.fpg");
            handle = controller.open(workspaceFile);
            final GraphWorkspaceHandle liveHandle = handle;
            final MapReferenceId mapId = MapReferenceId.of(UUID.nameUUIDFromBytes(
                sourceMapFile.toAbsolutePath().toString().getBytes(StandardCharsets.UTF_8)));
            final GraphCommandResult addMap = handle.execute(
                GraphCommands.addMap(mapId, sourceMapFile.toUri()));
            assertThat(addMap.status()).isEqualTo(GraphCommandResult.Status.APPLIED);
            GraphWorkspaceIntegrationSupport.awaitCondition(
                () -> GraphWorkspaceIntegrationSupport.hasNodeLabel(liveHandle.currentProjection(), "alpha")
                    && GraphWorkspaceIntegrationSupport.hasNodeLabel(liveHandle.currentProjection(), "bravo"),
                15000L, "initial live labels did not appear");

            GraphWorkspaceIntegrationSupport.awaitIdle(views.latestBinding(), 15000L);
            final CanvasState baseline = views.latestBinding().currentCanvasState();
            final long baselineGeneration = baseline.generation();
            final ProjectedNodeKey alphaKey =
                GraphWorkspaceIntegrationSupport.nodeKeyByLabel(baseline.projection(), "alpha");
            final ProjectedNodeKey bravoKey =
                GraphWorkspaceIntegrationSupport.nodeKeyByLabel(baseline.projection(), "bravo");
            final LayoutPoint alphaBefore = baseline.layout().positions().nodes().get(alphaKey);
            final LayoutPoint bravoBefore = baseline.layout().positions().nodes().get(bravoKey);
            assertThat(alphaBefore).isNotNull();
            assertThat(bravoBefore).isNotNull();

            final NodeModel alpha = sourceMap.getRootNode().getChildAt(0);
            final String oldText = alpha.getText();
            final String newText = "omega";
            freeplane.modeController().execute(
                textEdit(freeplane.modeController(), alpha, oldText, newText), sourceMap);

            GraphWorkspaceIntegrationSupport.awaitCondition(() -> {
                    final CanvasState state = views.latestBinding().currentCanvasState();
                    return state.generation() > baselineGeneration
                        && GraphWorkspaceIntegrationSupport.hasNodeLabel(state.projection(), "omega")
                        && !GraphWorkspaceIntegrationSupport.hasNodeLabel(state.projection(), "alpha");
                }, 15000L, "same-length text edit did not reach the live canvas state");
            GraphWorkspaceIntegrationSupport.awaitIdle(views.latestBinding(), 15000L);

            final CanvasState edited = views.latestBinding().currentCanvasState();
            assertThat(edited.layout().positions().nodes().get(alphaKey)).isEqualTo(alphaBefore);
            assertThat(edited.layout().positions().nodes().get(bravoKey)).isEqualTo(bravoBefore);
        }
        finally {
            if (handle != null) {
                try {
                    handle.close();
                }
                catch (RuntimeException ignored) {
                    // The controller shutdown below performs deterministic discard cleanup for a failed close.
                }
            }
            if (controller != null) {
                try {
                    controller.shutdown();
                }
                catch (RuntimeException ignored) {
                    // Preserve the original assertion failure while still releasing the headless Freeplane scope.
                }
            }
            freeplane.close();
        }
    }

    @Test
    public void nodeTextEditRefreshesWhenADistinctEditorModelAppears() throws Exception {
        final GraphWorkspaceIntegrationSupport.FreeplaneScope freeplane =
            new GraphWorkspaceIntegrationSupport.FreeplaneScope();
        DefaultGraphWorkspaceController controller = null;
        GraphWorkspaceHandle handle = null;
        try {
            final Path realDirectory = temporaryFolder.newFolder("real").toPath();
            final Path sourceMapFile = realDirectory.resolve("symlinked.mm");
            Files.write(sourceMapFile, LIVE_REFRESH_FIXTURE.getBytes(StandardCharsets.UTF_8));
            final Path aliasDirectory = temporaryFolder.newFolder("alias").toPath();
            final Path aliasMapFile = aliasDirectory.resolve("symlinked.mm");
            Files.createSymbolicLink(aliasMapFile, sourceMapFile);
            freeplane.installGraphGroups();
            final GraphWorkspaceIntegrationSupport.RecordingViewFactory views =
                new GraphWorkspaceIntegrationSupport.RecordingViewFactory();
            controller = new DefaultGraphWorkspaceController(freeplane.modeController(), views);
            final Path workspaceFile = temporaryFolder.getRoot().toPath().resolve("symlinked.fpg");
            handle = controller.open(workspaceFile);
            final GraphWorkspaceHandle liveHandle = handle;
            final MapReferenceId mapId = MapReferenceId.of(UUID.nameUUIDFromBytes(
                sourceMapFile.toAbsolutePath().toString().getBytes(StandardCharsets.UTF_8)));
            // The stored URI uses the alias path; the lease canonicalizes it to the real path.
            final GraphCommandResult addMap = handle.execute(
                GraphCommands.addMap(mapId, aliasMapFile.toUri()));
            assertThat(addMap.status()).isEqualTo(GraphCommandResult.Status.APPLIED);
            GraphWorkspaceIntegrationSupport.awaitCondition(
                () -> GraphWorkspaceIntegrationSupport.hasNodeLabel(liveHandle.currentProjection(), "alpha"),
                15000L, "initial live label did not appear");

            final MapModel editorMap = freeplane.loadWithView(aliasMapFile);
            freeplane.drainAsyncQueues();
            final NodeModel alpha = editorMap.getRootNode().getChildAt(0);
            final String oldText = alpha.getText();
            final String newText = "omega";
            freeplane.modeController().execute(
                textEdit(freeplane.modeController(), alpha, oldText, newText), editorMap);

            GraphWorkspaceIntegrationSupport.awaitCondition(
                () -> GraphWorkspaceIntegrationSupport.hasNodeLabel(liveHandle.currentProjection(), "omega")
                    && !GraphWorkspaceIntegrationSupport.hasNodeLabel(liveHandle.currentProjection(), "alpha"),
                15000L, "edited label did not reach the live projection after the editor model appeared");
        }
        finally {
            if (handle != null) {
                try {
                    handle.close();
                }
                catch (RuntimeException ignored) {
                    // The controller shutdown below performs deterministic discard cleanup for a failed close.
                }
            }
            if (controller != null) {
                try {
                    controller.shutdown();
                }
                catch (RuntimeException ignored) {
                    // Preserve the original assertion failure while still releasing the headless Freeplane scope.
                }
            }
            freeplane.close();
        }
    }

    @Test
    public void undoOfNodeTextEditRefreshesTheLiveProjection() throws Exception {
        final GraphWorkspaceIntegrationSupport.FreeplaneScope freeplane =
            new GraphWorkspaceIntegrationSupport.FreeplaneScope();
        DefaultGraphWorkspaceController controller = null;
        GraphWorkspaceHandle handle = null;
        try {
            final Path sourceMapFile = temporaryFolder.getRoot().toPath().resolve("live-refresh-undo.mm");
            Files.write(sourceMapFile, LIVE_REFRESH_FIXTURE.getBytes(StandardCharsets.UTF_8));
            freeplane.installGraphGroups();
            final MapModel sourceMap = freeplane.loadWithView(sourceMapFile);
            final GraphWorkspaceIntegrationSupport.RecordingViewFactory views =
                new GraphWorkspaceIntegrationSupport.RecordingViewFactory();
            controller = new DefaultGraphWorkspaceController(freeplane.modeController(), views);
            final Path workspaceFile = temporaryFolder.getRoot().toPath().resolve("live-refresh-undo.fpg");
            handle = controller.open(workspaceFile);
            final GraphWorkspaceHandle liveHandle = handle;
            final MapReferenceId mapId = MapReferenceId.of(UUID.nameUUIDFromBytes(
                sourceMapFile.toAbsolutePath().toString().getBytes(StandardCharsets.UTF_8)));
            final GraphCommandResult addMap = handle.execute(
                GraphCommands.addMap(mapId, sourceMapFile.toUri()));
            assertThat(addMap.status()).isEqualTo(GraphCommandResult.Status.APPLIED);
            GraphWorkspaceIntegrationSupport.awaitCondition(
                () -> GraphWorkspaceIntegrationSupport.hasNodeLabel(liveHandle.currentProjection(), "alpha"),
                15000L, "initial live label did not appear");

            final NodeModel alpha = sourceMap.getRootNode().getChildAt(0);
            final String oldText = alpha.getText();
            final String newText = "omega";
            freeplane.modeController().execute(
                textEdit(freeplane.modeController(), alpha, oldText, newText), sourceMap);
            GraphWorkspaceIntegrationSupport.awaitCondition(
                () -> GraphWorkspaceIntegrationSupport.hasNodeLabel(liveHandle.currentProjection(), "omega")
                    && !GraphWorkspaceIntegrationSupport.hasNodeLabel(liveHandle.currentProjection(), "alpha"),
                15000L, "edited label did not reach the live projection");

            sourceMap.getExtension(IUndoHandler.class).undo();

            GraphWorkspaceIntegrationSupport.awaitCondition(
                () -> GraphWorkspaceIntegrationSupport.hasNodeLabel(liveHandle.currentProjection(), "alpha")
                    && !GraphWorkspaceIntegrationSupport.hasNodeLabel(liveHandle.currentProjection(), "omega"),
                15000L, "undone label did not reach the live projection");
        }
        finally {
            if (handle != null) {
                try {
                    handle.close();
                }
                catch (RuntimeException ignored) {
                    // The controller shutdown below performs deterministic discard cleanup for a failed close.
                }
            }
            if (controller != null) {
                try {
                    controller.shutdown();
                }
                catch (RuntimeException ignored) {
                    // Preserve the original assertion failure while still releasing the headless Freeplane scope.
                }
            }
            freeplane.close();
        }
    }

    private static IActor textEdit(final org.freeplane.features.mode.ModeController modeController,
            final NodeModel node, final String oldText, final String newText) {
        return new IActor() {
            @Override
            public void act() {
                node.setText(newText);
                modeController.getMapController().nodeChanged(node, NodeModel.NODE_TEXT, oldText, newText);
            }

            @Override
            public String getDescription() {
                return "graph-live-refresh-text-edit";
            }

            @Override
            public void undo() {
                node.setText(oldText);
                modeController.getMapController().nodeChanged(node, NodeModel.NODE_TEXT, newText, oldText);
            }
        };
    }
}
