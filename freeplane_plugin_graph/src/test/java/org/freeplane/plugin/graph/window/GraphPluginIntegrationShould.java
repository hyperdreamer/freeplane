package org.freeplane.plugin.graph.window;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.awt.event.ActionEvent;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.map.MapController;
import org.freeplane.core.ui.menubuilders.generic.UserRole;
import org.freeplane.features.mode.ModeController;
import org.freeplane.main.application.ApplicationResourceController;
import org.freeplane.plugin.graph.GraphModeExtension;
import org.freeplane.plugin.graph.control.DefaultGraphWorkspaceController;
import org.freeplane.plugin.graph.control.GraphWorkspaceController;
import org.freeplane.plugin.graph.group.GraphGroupController;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

public class GraphPluginIntegrationShould {
    private MockedStatic<ResourceController> resourceController;
    private MockedStatic<TextUtils> textUtils;

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
        assertThat(read("freeplane_plugin_graph/src/main/resources/images/GraphGroup.svg")).contains("<svg", "#DF625D");
        assertThat(read("freeplane_plugin_graph/src/main/resources/images/GraphWorkspace.svg")).contains("<svg");
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
