package org.freeplane.plugin.graph.window;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.TextUtils;
import org.freeplane.plugin.graph.command.GraphCommand;
import org.freeplane.plugin.graph.command.GraphCommands;
import org.freeplane.plugin.graph.command.MapUndoTarget;
import org.freeplane.plugin.graph.control.GraphWorkspaceController;
import org.freeplane.plugin.graph.control.GraphWorkspaceHandle;
import org.freeplane.plugin.graph.control.GraphWorkspaceViewBinding;
import org.freeplane.plugin.graph.control.WorkspaceCloseController;
import org.freeplane.plugin.graph.control.WorkspaceSessionStatus;
import org.freeplane.plugin.graph.control.WorkspaceSessionStatusListener;
import org.freeplane.plugin.graph.workspace.ListenerRegistration;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.UnknownXml;
import org.freeplane.plugin.graph.workspace.model.Viewport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

public class UndoRoutingShould {
    private static final MapReferenceId MAP_ID = MapReferenceId.of(UUID.fromString(
        "00000000-0000-0000-0000-000000000001"));

    private static final List<EdtResources> RESOURCES = new ArrayList<EdtResources>();
    private MockedStatic<TextUtils> textUtils;
    private MockedStatic<ResourceController> resourceController;

    @Before
    public void setUp() {
        resourceController = org.mockito.Mockito.mockStatic(ResourceController.class);
        resourceController.when(ResourceController::getResourceController)
            .thenReturn(mock(ResourceController.class));
        textUtils = org.mockito.Mockito.mockStatic(TextUtils.class);
        textUtils.when(() -> TextUtils.getText(any(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        textUtils.when(() -> TextUtils.getText(any(String.class), any(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        textUtils.when(() -> TextUtils.getRawText(any(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        textUtils.when(() -> TextUtils.getRawText(any(String.class), any(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        textUtils.when(() -> TextUtils.format(any(String.class), any(Object[].class)))
            .thenAnswer(invocation -> format(invocation.getArguments()));
    }

    @After
    public void tearDown() {
        textUtils.close();
        resourceController.close();
        if (!RESOURCES.isEmpty()) {
            GraphWorkspaceWindow.runOnEdt(new Runnable() {
                @Override
                public void run() {
                    for (EdtResources resource : RESOURCES) {
                        resource.closeOnEdt();
                    }
                }
            });
            RESOURCES.clear();
        }
    }

    @Test
    public void routesCtrlZAndCtrlYOnlyToWorkspaceHistory() {
        Fixture fixture = fixture(status(true, true, Optional.of(new MapUndoTarget(MAP_ID, "Roadmap", true))), false);
        JRootPane root = new JRootPane();

        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                fixture.model.installWorkspaceHistoryKeys(root);
                Action undo = root.getActionMap().get("graph-workspace-undo");
                Action redo = root.getActionMap().get("graph-workspace-redo");
                undo.actionPerformed(new ActionEvent(root, ActionEvent.ACTION_PERFORMED, "undo"));
                redo.actionPerformed(new ActionEvent(root, ActionEvent.ACTION_PERFORMED, "redo"));
            }
        });

        assertThat(fixture.commands).extracting("class").containsExactly(
            GraphCommands.UndoWorkspace.class, GraphCommands.RedoWorkspace.class);
        assertThat(root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .get(KeyStroke.getKeyStroke("ctrl Z"))).isEqualTo("graph-workspace-undo");
        assertThat(root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .get(KeyStroke.getKeyStroke("ctrl Y"))).isEqualTo("graph-workspace-redo");
        assertThat(root.getActionMap().get("graph-workspace-undo-source-map")).isNull();
        fixture.close();
    }

    @Test
    public void keepsWorkspaceAndSourceMapHistoryActionsIndependentAndLocalized() {
        Fixture fixture = fixture(WorkspaceSessionStatus.empty(), false);
        JMenuItem undo = menuItem(fixture.model, "graph-workspace-menu-item-undo");
        JMenuItem redo = menuItem(fixture.model, "graph-workspace-menu-item-redo");
        JMenuItem sourceMapUndo = menuItem(fixture.model, "graph-workspace-menu-item-undo-source-map");

        assertThat(fixture.model.toolbar().undoButton().getText()).isEqualTo("graph_workspace.action.undo_workspace");
        assertThat(fixture.model.toolbar().redoButton().getText()).isEqualTo("graph_workspace.action.redo_workspace");
        assertThat(undo.getText()).isEqualTo("graph_workspace.action.undo_workspace");
        assertThat(redo.getText()).isEqualTo("graph_workspace.action.redo_workspace");
        assertThat(fixture.model.toolbar().undoButton().isEnabled()).isFalse();
        assertThat(fixture.model.toolbar().redoButton().isEnabled()).isFalse();
        assertThat(undo.isEnabled()).isFalse();
        assertThat(redo.isEnabled()).isFalse();
        assertThat(sourceMapUndo.isEnabled()).isFalse();
        assertThat(sourceMapUndo.getAccelerator()).isNull();

        fixture.publish(status(true, false, Optional.<MapUndoTarget>empty()));

        assertThat(fixture.model.toolbar().undoButton().isEnabled()).isTrue();
        assertThat(fixture.model.toolbar().redoButton().isEnabled()).isFalse();
        assertThat(undo.isEnabled()).isTrue();
        assertThat(redo.isEnabled()).isFalse();
        undo.doClick();
        assertThat(fixture.commands).extracting("class").containsExactly(GraphCommands.UndoWorkspace.class);

        fixture.commands.clear();
        fixture.publish(status(false, false, Optional.of(new MapUndoTarget(MAP_ID, "Roadmap", false))));

        assertThat(sourceMapUndo.getText()).isEqualTo("graph_workspace.action.undo_source_map:Roadmap");
        assertThat(sourceMapUndo.isEnabled()).isFalse();

        fixture.publish(status(false, false, Optional.of(new MapUndoTarget(MAP_ID, "Roadmap", true))));

        assertThat(sourceMapUndo.isEnabled()).isTrue();
        sourceMapUndo.doClick();
        assertThat(fixture.commands).extracting("class").containsExactly(GraphCommands.UndoSourceMap.class);

        fixture.model.setReadOnly(true);

        assertThat(fixture.model.toolbar().undoButton().isEnabled()).isFalse();
        assertThat(fixture.model.toolbar().redoButton().isEnabled()).isFalse();
        assertThat(undo.isEnabled()).isFalse();
        assertThat(redo.isEnabled()).isFalse();
        assertThat(sourceMapUndo.isEnabled()).isFalse();
        fixture.close();
    }

    @Test
    public void routesStatusSessionControlsThroughTheSessionHandleAndRespectsReadOnlyMode() {
        Fixture fixture = fixture(status(false, false, Optional.<MapUndoTarget>empty()), false);

        fixture.model.statusBar().retrySaveButton().doClick();
        fixture.model.statusBar().restartLayoutButton().doClick();
        fixture.model.statusBar().unpinAllButton().doClick();

        assertThat(fixture.commands).extracting("class").containsExactly(
            GraphCommands.RetrySave.class, GraphCommands.RestartLayout.class, GraphCommands.UnpinAll.class);

        fixture.commands.clear();
        fixture.model.setReadOnly(true);
        fixture.model.statusBar().retrySaveButton().doClick();
        fixture.model.statusBar().unpinAllButton().doClick();

        assertThat(fixture.model.statusBar().retrySaveButton().isEnabled()).isFalse();
        assertThat(fixture.model.statusBar().unpinAllButton().isEnabled()).isFalse();
        assertThat(fixture.commands).isEmpty();
        fixture.close();
    }

    private static WorkspaceSessionStatus status(final boolean undo, final boolean redo,
            final Optional<MapUndoTarget> sourceMapUndoTarget) {
        return WorkspaceSessionStatus.of(false, undo, redo, false, Collections.<MapReferenceId>emptySet(),
            sourceMapUndoTarget);
    }

    private static JMenuItem menuItem(final GraphWorkspaceWindowModel model, final String name) {
        for (int menuIndex = 0; menuIndex < model.menuBar().getMenuCount(); menuIndex++) {
            JMenu menu = model.menuBar().getMenu(menuIndex);
            for (int itemIndex = 0; itemIndex < menu.getItemCount(); itemIndex++) {
                JMenuItem item = menu.getItem(itemIndex);
                if (item != null && name.equals(item.getName())) {
                    return item;
                }
            }
        }
        return null;
    }

    private static String format(final Object[] arguments) {
        StringBuilder value = new StringBuilder(String.valueOf(arguments[0]));
        for (int index = 1; index < arguments.length; index++) {
            appendFormatArgument(value, arguments[index]);
        }
        return value.toString();
    }

    private static void appendFormatArgument(final StringBuilder result, final Object argument) {
        if (argument instanceof Object[]) {
            for (Object value : (Object[]) argument) {
                appendFormatArgument(result, value);
            }
            return;
        }
        result.append(':').append(argument);
    }

    private static Fixture fixture(final WorkspaceSessionStatus status, final boolean readOnly) {
        GraphWorkspaceHandle handle = mock(GraphWorkspaceHandle.class);
        GraphWorkspaceController applicationController = mock(GraphWorkspaceController.class);
        GraphWorkspaceViewBinding binding = mock(GraphWorkspaceViewBinding.class);
        WorkspaceCloseController closeController = mock(WorkspaceCloseController.class);
        ListenerRegistration canvasRegistration = mock(ListenerRegistration.class);
        ListenerRegistration statusRegistration = mock(ListenerRegistration.class);
        List<GraphCommand> commands = new ArrayList<GraphCommand>();
        when(handle.execute(any(GraphCommand.class))).thenAnswer(invocation -> {
            commands.add(invocation.getArgument(0));
            return null;
        });
        when(binding.currentCanvasState()).thenReturn(null);
        when(binding.currentViewport()).thenReturn(Viewport.of(0.0, 0.0, 1.0,
            Collections.<UnknownXml>emptyList()));
        when(binding.currentMapRows()).thenReturn(Collections.<GraphWorkspaceViewBinding.MapRegistration>emptyList());
        when(binding.currentSessionStatus()).thenReturn(status);
        when(binding.isReadOnly()).thenReturn(readOnly);
        when(binding.addCanvasStateListener(any())).thenReturn(canvasRegistration);
        when(binding.addSessionStatusListener(any())).thenReturn(statusRegistration);

        final GraphWorkspaceWindowModel[] model = new GraphWorkspaceWindowModel[1];
        final EdtResources[] edtResources = new EdtResources[1];
        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                edtResources[0] = new EdtResources();
                model[0] = new GraphWorkspaceWindowModel(handle, binding, applicationController, () -> null,
                    closeController, () -> { }, () -> { }, () -> { });
            }
        });
        RESOURCES.add(edtResources[0]);
        ArgumentCaptor<WorkspaceSessionStatusListener> listener =
            ArgumentCaptor.forClass(WorkspaceSessionStatusListener.class);
        verify(binding).addSessionStatusListener(listener.capture());
        return new Fixture(model[0], commands, listener.getValue(), edtResources[0]);
    }

    private static final class Fixture {
        private final GraphWorkspaceWindowModel model;
        private final List<GraphCommand> commands;
        private final WorkspaceSessionStatusListener statusListener;
        private final EdtResources resources;

        private Fixture(final GraphWorkspaceWindowModel model, final List<GraphCommand> commands,
                final WorkspaceSessionStatusListener statusListener, final EdtResources resources) {
            this.model = model;
            this.commands = commands;
            this.statusListener = statusListener;
            this.resources = resources;
        }

        private void publish(final WorkspaceSessionStatus status) {
            statusListener.onWorkspaceSessionStatus(status);
        }

        private void close() {
            model.close();
        }
    }

    private static final class EdtResources {
        private final MockedStatic<TextUtils> textUtils;
        private final MockedStatic<ResourceController> resourceController;
        private boolean closed;

        private EdtResources() {
            resourceController = org.mockito.Mockito.mockStatic(ResourceController.class);
            resourceController.when(ResourceController::getResourceController)
                .thenReturn(mock(ResourceController.class));
            textUtils = org.mockito.Mockito.mockStatic(TextUtils.class);
            textUtils.when(() -> TextUtils.getText(any(String.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
            textUtils.when(() -> TextUtils.getText(any(String.class), any(String.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
            textUtils.when(() -> TextUtils.getRawText(any(String.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
            textUtils.when(() -> TextUtils.getRawText(any(String.class), any(String.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
            textUtils.when(() -> TextUtils.format(any(String.class), any(Object[].class)))
                .thenAnswer(invocation -> format(invocation.getArguments()));
        }

        private void closeOnEdt() {
            if (!closed) {
                closed = true;
                textUtils.close();
                resourceController.close();
            }
        }
    }
}
