package org.freeplane.plugin.graph.window;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.util.ConfigurationUtils;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.map.MapController;
import org.freeplane.core.ui.menubuilders.generic.UserRole;
import org.freeplane.features.mode.ModeController;
import org.freeplane.main.application.ApplicationResourceController;
import org.freeplane.plugin.graph.GraphModeExtension;
import org.freeplane.plugin.graph.control.DefaultGraphWorkspaceController;
import org.freeplane.plugin.graph.control.GraphWorkspaceController;
import org.freeplane.plugin.graph.control.GraphWorkspaceHandle;
import org.freeplane.plugin.graph.control.GraphWorkspaceOpenException;
import org.freeplane.plugin.graph.control.GraphWorkspaceView;
import org.freeplane.plugin.graph.control.GraphWorkspaceViewBinding;
import org.freeplane.plugin.graph.control.WorkspaceCloseController;
import org.freeplane.plugin.graph.control.WorkspaceSessionStatus;
import org.freeplane.plugin.graph.group.GraphGroupController;
import org.freeplane.plugin.graph.workspace.RecentWorkspaceList;
import org.freeplane.plugin.graph.workspace.model.Viewport;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

public class GraphPluginIntegrationShould {
    private MockedStatic<ResourceController> resourceController;
    private MockedStatic<TextUtils> textUtils;

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setUp() {
        resourceController = org.mockito.Mockito.mockStatic(ResourceController.class);
        resourceController.when(ResourceController::getResourceController).thenReturn(mock(ResourceController.class));
        textUtils = org.mockito.Mockito.mockStatic(TextUtils.class, invocation -> {
            String method = invocation.getMethod().getName();
            if ("getText".equals(method) || "getRawText".equals(method)) {
                return invocation.getArguments()[0];
            }
            return Answers.RETURNS_DEFAULTS.answer(invocation);
        });
    }

    @After
    public void tearDown() {
        textUtils.close();
        resourceController.close();
    }

    @Test
    public void opensOnlyPathsSelectedByTheInjectedWorkspaceChooser() {
        GraphWorkspaceController controller = mock(GraphWorkspaceController.class);
        Path existingWorkspace = Paths.get("/tmp/existing.fpg");
        Path newWorkspace = Paths.get("/tmp/new.fpg");
        final Path[] selectedPath = new Path[] { null };
        OpenGraphWorkspaceAction action = new OpenGraphWorkspaceAction(controller, () -> selectedPath[0]);

        action.afterMapChange(UserRole.NO_MAP);
        assertThat(action.isEnabled()).isTrue();
        action.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "cancelled"));
        verifyNoInteractions(controller);
        selectedPath[0] = existingWorkspace;
        action.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "existing"));
        selectedPath[0] = newWorkspace;
        action.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "new"));

        verify(controller).open(existingWorkspace);
        verify(controller).open(newWorkspace);
        assertThat(action.getKey()).isEqualTo(OpenGraphWorkspaceAction.KEY);
    }

    @Test
    public void installsAndRemovesTheApplicationScopedWorkspaceActionWithTheExistingGraphExtension() {
        ApplicationResourceController applicationResources = mock(ApplicationResourceController.class);
        resourceController.when(ResourceController::getResourceController).thenReturn(applicationResources);
        ModeController modeController = mock(ModeController.class);
        MapController mapController = mock(MapController.class);
        when(modeController.getMapController()).thenReturn(mapController);
        when(mapController.getReadManager()).thenReturn(new org.freeplane.core.io.ReadManager());
        when(mapController.getWriteManager()).thenReturn(new org.freeplane.core.io.WriteManager());
        GraphModeExtension extension = new GraphModeExtension();

        extension.installExtension(modeController, null);
        extension.close();

        ArgumentCaptor<AFreeplaneAction> actions = ArgumentCaptor.forClass(AFreeplaneAction.class);
        verify(modeController, org.mockito.Mockito.times(3)).addAction(actions.capture());
        assertThat(actions.getAllValues()).anyMatch(action -> action instanceof OpenGraphWorkspaceAction);
        verify(modeController).removeAction(OpenGraphWorkspaceAction.KEY);
        verify(applicationResources).registerResourceLoader(GraphModeExtension.class.getClassLoader());
    }

    @Test
    public void shutsDownWorkspaceSessionsBeforeRemovingGraphActionsAndExtensions() {
        ApplicationResourceController applicationResources = mock(ApplicationResourceController.class);
        resourceController.when(ResourceController::getResourceController).thenReturn(applicationResources);
        ModeController modeController = configuredModeController();
        GraphModeExtension extension = new GraphModeExtension();

        try (MockedConstruction<DefaultGraphWorkspaceController> constructions =
                mockConstruction(DefaultGraphWorkspaceController.class)) {
            extension.installExtension(modeController, null);
            DefaultGraphWorkspaceController controller = constructions.constructed().get(0);

            extension.close();

            InOrder order = inOrder(controller, modeController);
            order.verify(controller).shutdown();
            order.verify(modeController).removeAction(OpenGraphWorkspaceAction.KEY);
            order.verify(modeController).removeExtension(GraphGroupController.class);
        }
    }

    @Test
    public void closesSafelyAfterPartialGraphGroupInstallation() {
        ApplicationResourceController applicationResources = mock(ApplicationResourceController.class);
        resourceController.when(ResourceController::getResourceController).thenReturn(applicationResources);
        ModeController modeController = configuredModeController();
        doThrow(new IllegalStateException("graph group installation failed")).when(modeController)
            .addExtension(org.mockito.ArgumentMatchers.eq(GraphGroupController.class), any(GraphGroupController.class));
        GraphModeExtension extension = new GraphModeExtension();

        assertThatThrownBy(() -> extension.installExtension(modeController, null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("graph group installation failed");

        extension.close();
        extension.close();

        verify(modeController).removeExtension(GraphGroupController.class);
        verify(modeController, org.mockito.Mockito.never()).removeAction(OpenGraphWorkspaceAction.KEY);
    }

    @Test
    public void placesAndDescribesBothGraphActionsWithTheirOwnIcons() throws IOException {
        String menu = read("freeplane/src/external/resources/xml/mindmapmodemenu.xml");
        String viewerProperties = read("freeplane/src/viewer/resources/freeplane.properties");
        Properties translations = properties("freeplane/src/viewer/resources/translations/Resources_en.properties");

        int cloud = menu.indexOf("<Entry name=\"CloudAction\" />");
        int graphGroup = menu.indexOf("<Entry name=\"GraphGroupAction\" />");
        int colorAction = menu.indexOf("<Entry name=\"GraphGroupColorAction\" />");
        int view = menu.indexOf("<Entry name=\"view\">");
        int format = menu.indexOf("<Entry name=\"format\"");
        int workspaceAction = menu.indexOf("<Entry name=\"OpenGraphWorkspaceAction\" />", view);
        assertThat(graphGroup).isGreaterThan(cloud);
        assertThat(menu.substring(cloud, graphGroup)).isEqualTo("<Entry name=\"CloudAction\" />\n\t\t\t");
        assertThat(colorAction).isGreaterThan(graphGroup);
        assertThat(menu.substring(graphGroup, colorAction))
            .isEqualTo("<Entry name=\"GraphGroupAction\" />\n\t\t\t");
        assertThat(workspaceAction).isGreaterThan(view);
        assertThat(workspaceAction).isLessThan(format);
        assertThat(viewerProperties).contains("GraphGroupAction.icon=/images/GraphGroup.svg");
        assertThat(viewerProperties).contains("OpenGraphWorkspaceAction.icon=/images/GraphWorkspace.svg");
        assertThat(translations.getProperty("GraphGroupAction.text")).isEqualTo("Include in Graph");
        assertThat(translations.getProperty("GraphGroupAction.tooltip"))
            .isEqualTo("Toggle inclusion in Graph Workspace for the selected nodes");
        assertThat(translations.getProperty("GraphGroupColorAction.text")).isEqualTo("Graph marker color");
        assertThat(translations.getProperty("GraphGroupColorAction.tooltip"))
            .isEqualTo("Change the color of graph inclusion markers");
        assertThat(translations.getProperty("choose_graph_group_color")).isEqualTo("Choose Graph Marker Color:");
        assertThat(viewerProperties)
            .contains("GraphGroupColorAction.icon=/images/Colors24.svg?useAccentColor\\=true");
        assertThat(translations.getProperty("OpenGraphWorkspaceAction.text")).isNotBlank();
        assertThat(translations.getProperty("OpenGraphWorkspaceAction.tooltip")).isNotBlank();
        assertThat(read("freeplane_plugin_graph/src/main/resources/images/GraphGroup.svg"))
            .contains("<svg", "#DF625D", "aria-label=\"Graph inclusion marker\"");
        assertThat(read("freeplane_plugin_graph/src/main/resources/images/GraphWorkspace.svg")).contains("<svg");
    }

    @Test
    public void passesTheSharedListIntoTheCreatedHeadlessView() throws Exception {
        Assume.assumeTrue(GraphicsEnvironment.isHeadless());
        Path workspace = temporaryFolder.newFile("factory-headless.fpg").toPath().toRealPath();
        GraphWorkspaceController controller = mock(GraphWorkspaceController.class);
        GraphWorkspaceHandle handle = mock(GraphWorkspaceHandle.class);
        GraphWorkspaceViewBinding binding = modelBinding();
        WorkspaceCloseController close = mock(WorkspaceCloseController.class);
        RecentWorkspaceList list = new RecentWorkspaceList("", value -> { });
        list.record(workspace);

        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                try (MockedStatic<TextUtils> edtTextUtils = edtTextUtils();
                        MockedStatic<ResourceController> edtResourceController = edtResourceController()) {
                    GraphWorkspaceView view = new SwingGraphWorkspaceViewFactory(controller, list)
                        .create(handle, binding, close);
                    assertThat(view).isInstanceOf(HeadlessGraphWorkspaceView.class);
                    HeadlessGraphWorkspaceView headless = (HeadlessGraphWorkspaceView) view;
                    headless.model().rebuildRecentWorkspacesMenu();
                    JMenu recents = recentsMenu(headless.model().menuBar());
                    assertThat(recents.getMenuComponentCount()).isEqualTo(3);
                    assertThat(((JMenuItem) recents.getMenuComponent(0)).getName())
                        .isEqualTo("graph-workspace-recent-workspace-0");
                    view.close();
                }
            }
        });

        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                try (MockedStatic<TextUtils> edtTextUtils = edtTextUtils();
                        MockedStatic<ResourceController> edtResourceController = edtResourceController()) {
                    GraphWorkspaceView view = new SwingGraphWorkspaceViewFactory(controller)
                        .create(handle, binding, close);
                    HeadlessGraphWorkspaceView headless = (HeadlessGraphWorkspaceView) view;
                    headless.model().rebuildRecentWorkspacesMenu();
                    JMenu recents = recentsMenu(headless.model().menuBar());
                    assertThat(recents.getMenuComponentCount()).isEqualTo(1);
                    assertThat(((JMenuItem) recents.getMenuComponent(0)).getName())
                        .isEqualTo("graph-workspace-recent-workspaces-empty");
                    view.close();
                }
            }
        });
    }

    @Test
    public void passesTheSharedListIntoTheCreatedSwingWindow() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        Path workspace = temporaryFolder.newFile("factory-window.fpg").toPath().toRealPath();
        GraphWorkspaceController controller = mock(GraphWorkspaceController.class);
        GraphWorkspaceHandle handle = mock(GraphWorkspaceHandle.class);
        GraphWorkspaceViewBinding binding = modelBinding();
        WorkspaceCloseController close = mock(WorkspaceCloseController.class);
        RecentWorkspaceList list = new RecentWorkspaceList("", value -> { });
        list.record(workspace);

        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                try (MockedStatic<TextUtils> edtTextUtils = edtTextUtils();
                        MockedStatic<ResourceController> edtResourceController = edtResourceController()) {
                    GraphWorkspaceView view = new SwingGraphWorkspaceViewFactory(controller, list)
                        .create(handle, binding, close);
                    assertThat(view).isInstanceOf(GraphWorkspaceWindow.class);
                    GraphWorkspaceWindow window = (GraphWorkspaceWindow) view;
                    JMenu recents = recentsMenu(window.getJMenuBar());
                    recentsPopupListener(recents).popupMenuWillBecomeVisible(
                        new PopupMenuEvent(recents.getPopupMenu()));
                    assertThat(recents.getMenuComponentCount()).isEqualTo(3);
                    assertThat(((JMenuItem) recents.getMenuComponent(0)).getName())
                        .isEqualTo("graph-workspace-recent-workspace-0");
                    view.close();
                }
            }
        });
    }

    private static GraphWorkspaceViewBinding modelBinding() {
        GraphWorkspaceViewBinding binding = mock(GraphWorkspaceViewBinding.class);
        when(binding.currentViewport()).thenReturn(Viewport.of(0.0, 0.0, 1.0, Collections.emptyList()));
        when(binding.currentCanvasState()).thenReturn(null);
        when(binding.currentSessionStatus()).thenReturn(WorkspaceSessionStatus.empty());
        when(binding.currentMapRows()).thenReturn(Collections.emptyList());
        return binding;
    }

    private static JMenu recentsMenu(final JMenuBar menuBar) {
        for (int index = 0; index < menuBar.getMenuCount(); index++) {
            JMenu menu = menuBar.getMenu(index);
            for (Component component : menu.getMenuComponents()) {
                if (component instanceof JMenu
                        && "graph-workspace-recent-workspaces-menu".equals(component.getName())) {
                    return (JMenu) component;
                }
            }
        }
        throw new AssertionError("Missing recent workspaces menu");
    }

    private static PopupMenuListener recentsPopupListener(final JMenu menu) {
        PopupMenuListener[] listeners = menu.getPopupMenu().getPopupMenuListeners();
        for (PopupMenuListener listener : listeners) {
            if (!listener.getClass().getName().startsWith("javax.swing.")) {
                return listener;
            }
        }
        throw new AssertionError("Missing recent-workspaces popup listener");
    }

    private static MockedStatic<TextUtils> edtTextUtils() {
        MockedStatic<TextUtils> textUtils = org.mockito.Mockito.mockStatic(TextUtils.class);
        textUtils.when(() -> TextUtils.getText(any(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        textUtils.when(() -> TextUtils.getText(any(String.class), any(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        textUtils.when(() -> TextUtils.getRawText(any(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        textUtils.when(() -> TextUtils.getRawText(any(String.class), any(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        return textUtils;
    }

    private static MockedStatic<ResourceController> edtResourceController() {
        MockedStatic<ResourceController> resources = org.mockito.Mockito.mockStatic(ResourceController.class);
        resources.when(ResourceController::getResourceController).thenReturn(mock(ResourceController.class));
        return resources;
    }

    @Test
    public void wiresApplicationWideRecentWorkspacesThroughUserProperties() throws Exception {
        ApplicationResourceController applicationResources = mock(ApplicationResourceController.class);
        resourceController.when(ResourceController::getResourceController).thenReturn(applicationResources);
        Path first = temporaryFolder.newFile("seeded-workspace.fpg").toPath().toRealPath();
        Path second = temporaryFolder.newFile("recorded-workspace.fpg").toPath().toRealPath();
        when(applicationResources.getProperty(RecentWorkspaceList.PROPERTY_KEY, ""))
            .thenReturn(ConfigurationUtils.encodeListValue(Arrays.asList(first.toString()), true));
        AtomicReference<RecentWorkspaceList> controllerList = new AtomicReference<RecentWorkspaceList>();
        AtomicReference<RecentWorkspaceList> factoryList = new AtomicReference<RecentWorkspaceList>();
        AtomicReference<RecentWorkspaceList> actionList = new AtomicReference<RecentWorkspaceList>();
        ModeController modeController = configuredModeController();
        GraphModeExtension extension = new GraphModeExtension();

        try (MockedConstruction<DefaultGraphWorkspaceController> controllerConstruction =
                mockConstruction(DefaultGraphWorkspaceController.class, (mock, context) ->
                    controllerList.set((RecentWorkspaceList) context.arguments().get(2)));
             MockedConstruction<SwingGraphWorkspaceViewFactory> factoryConstruction =
                mockConstruction(SwingGraphWorkspaceViewFactory.class, (mock, context) ->
                    factoryList.set((RecentWorkspaceList) context.arguments().get(1)));
             MockedConstruction<OpenGraphWorkspaceAction> actionConstruction =
                mockConstruction(OpenGraphWorkspaceAction.class, (mock, context) ->
                    actionList.set((RecentWorkspaceList) context.arguments().get(1)))) {
            extension.installExtension(modeController, null);

            assertThat(controllerConstruction.constructed()).hasSize(1);
            assertThat(controllerList.get()).isNotNull();
            assertThat(controllerList.get().mostRecentExisting()).contains(first);
            assertThat(factoryList.get()).isSameAs(controllerList.get());
            assertThat(actionList.get()).isSameAs(controllerList.get());

            controllerList.get().record(second);
            controllerList.get().clear();

            ArgumentCaptor<String> persisted = ArgumentCaptor.forClass(String.class);
            verify(applicationResources, times(2)).setProperty(eq(RecentWorkspaceList.PROPERTY_KEY),
                persisted.capture());
            assertThat(persisted.getAllValues().get(0)).isEqualTo(
                ConfigurationUtils.encodeListValue(Arrays.asList(second.toString(), first.toString()), true));
            assertThat(persisted.getAllValues().get(1)).isEqualTo("");

            extension.close();
        }
    }

    @Test
    public void delegatesOpenExistingThroughTheForwardingController() throws Exception {
        ApplicationResourceController applicationResources = mock(ApplicationResourceController.class);
        resourceController.when(ResourceController::getResourceController).thenReturn(applicationResources);
        Path recent = temporaryFolder.newFile("forwarded.fpg").toPath().toRealPath();
        when(applicationResources.getProperty(RecentWorkspaceList.PROPERTY_KEY, ""))
            .thenReturn(ConfigurationUtils.encodeListValue(Arrays.asList(recent.toString()), true));
        ModeController modeController = configuredModeController();
        GraphModeExtension extension = new GraphModeExtension();

        try (MockedConstruction<DefaultGraphWorkspaceController> constructions =
                mockConstruction(DefaultGraphWorkspaceController.class)) {
            extension.installExtension(modeController, null);
            DefaultGraphWorkspaceController constructed = constructions.constructed().get(0);
            ArgumentCaptor<AFreeplaneAction> actions = ArgumentCaptor.forClass(AFreeplaneAction.class);
            verify(modeController, times(3)).addAction(actions.capture());
            OpenGraphWorkspaceAction action = null;
            for (AFreeplaneAction candidate : actions.getAllValues()) {
                if (candidate instanceof OpenGraphWorkspaceAction) {
                    action = (OpenGraphWorkspaceAction) candidate;
                }
            }
            assertThat(action).isNotNull();

            action.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "recent"));

            verify(constructed).openExisting(recent);
            verify(constructed, never()).open(any(java.nio.file.Path.class));
            extension.close();
        }

        Class<?> forwardingType = Class.forName(
            "org.freeplane.plugin.graph.GraphModeExtension$ForwardingGraphWorkspaceController");
        Constructor<?> constructor = forwardingType.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object forwarding = constructor.newInstance();
        Method openExisting = forwardingType.getDeclaredMethod("openExisting", java.nio.file.Path.class);
        openExisting.setAccessible(true);
        try {
            openExisting.invoke(forwarding, recent);
            throw new AssertionError("Expected GraphWorkspaceOpenException");
        }
        catch (java.lang.reflect.InvocationTargetException failure) {
            assertThat(failure.getCause()).isInstanceOf(GraphWorkspaceOpenException.class);
            assertThat(failure.getCause()).hasCauseInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    public void shipsTheFourRecentWorkspaceResourceKeys() throws IOException {
        Properties translations = properties("freeplane/src/viewer/resources/translations/Resources_en.properties");

        assertThat(translations.getProperty("graph_workspace.action.clear_recent_workspaces"))
            .isEqualTo("Clear Recent Workspaces");
        assertThat(translations.getProperty("graph_workspace.menu.recent_workspaces"))
            .isEqualTo("Recent Workspaces");
        assertThat(translations.getProperty("graph_workspace.recent_workspaces.empty"))
            .isEqualTo("No recent workspaces");
        assertThat(translations.getProperty("graph_workspace.recent_workspaces.open_failed"))
            .isEqualTo("Could not open the recent workspace: {0}");
    }

    @Test
    public void shipsTheGraphMapSidebarResourceKeys() throws IOException {
        Properties translations = properties("freeplane/src/viewer/resources/translations/Resources_en.properties");

        assertThat(translations.getProperty("graph_workspace.action.maps_sidebar")).isEqualTo("Maps sidebar");
        assertThat(translations.getProperty("graph_workspace.map_list.collapse")).isEqualTo("Hide maps sidebar");
        assertThat(translations.getProperty("graph_workspace.map_list.expand")).isEqualTo("Show maps sidebar");
        assertThat(translations.getProperty("graph_workspace.map_list.rail_label")).isEqualTo("MAPS");
        assertThat(translations.getProperty("graph_workspace.map_list.rail_count")).isEqualTo("{0} active maps");
    }

    @Test
    public void shipsTheGraphToolbarAffordanceResourceKeys() throws IOException {
        Properties translations = properties(
            "freeplane/src/viewer/resources/translations/Resources_en.properties");

        assertThat(translations.getProperty("graph_workspace.tooltip.connect"))
            .isEqualTo("Connect \u2014 create a cross-map relationship");
        assertThat(translations.getProperty("graph_workspace.tooltip.settings")).isEqualTo("Graph settings");
        assertThat(translations.getProperty("graph_workspace.tooltip.search")).isEqualTo("Search nodes and maps");
        assertThat(translations.getProperty("graph_workspace.tool.select")).isEqualTo("Select");
        assertThat(translations.getProperty("graph_workspace.tool.connect")).isEqualTo("Connect");
        assertThat(translations.getProperty("graph_workspace.action.settings")).isEqualTo("Settings");
    }

    @Test
    public void keepsTheToolbarSwitchGroupOwnedByToolSwitch() throws IOException {
        String toolbar = read(
            "freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceToolbar.java");

        assertThat(toolbar).doesNotContain("new ButtonGroup");
    }

    @Test
    public void keepsTheToolbarSegmentStylingDependencyFree() throws IOException {
        String toolbar = read(
            "freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceToolbar.java");

        assertThat(toolbar).contains("putClientProperty(\"JButton.buttonType\", \"toolBarButton\")");
        assertThat(toolbar).doesNotContain("com.formdev.flatlaf");
    }

    private static ModeController configuredModeController() {
        ModeController modeController = mock(ModeController.class);
        MapController mapController = mock(MapController.class);
        when(modeController.getMapController()).thenReturn(mapController);
        when(mapController.getReadManager()).thenReturn(new org.freeplane.core.io.ReadManager());
        when(mapController.getWriteManager()).thenReturn(new org.freeplane.core.io.WriteManager());
        return modeController;
    }

    private static Properties properties(final String relativePath) throws IOException {
        Properties result = new Properties();
        try (InputStream input = Files.newInputStream(repositoryFile(relativePath))) {
            result.load(input);
        }
        return result;
    }

    private static String read(final String relativePath) throws IOException {
        return new String(Files.readAllBytes(repositoryFile(relativePath)), StandardCharsets.UTF_8);
    }

    private static Path repositoryFile(final String relativePath) {
        Path directory = Paths.get("").toAbsolutePath();
        while (directory != null) {
            Path candidate = directory.resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("Cannot locate " + relativePath);
    }
}
