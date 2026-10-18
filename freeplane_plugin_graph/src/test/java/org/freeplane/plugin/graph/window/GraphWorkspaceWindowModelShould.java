package org.freeplane.plugin.graph.window;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GraphicsEnvironment;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.xml.namespace.QName;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.JViewport;
import javax.swing.Icon;
import javax.swing.UIManager;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.plaf.basic.BasicSplitPaneUI;

import org.freeplane.plugin.graph.canvas.GraphCanvas;
import org.freeplane.plugin.graph.canvas.GraphIntent;
import org.freeplane.plugin.graph.canvas.GraphTheme;
import org.freeplane.plugin.graph.canvas.GraphViewport;
import org.freeplane.plugin.graph.canvas.InteractionTool;
import org.freeplane.plugin.graph.command.GraphCommand;
import org.freeplane.plugin.graph.command.GraphCommands;
import org.freeplane.plugin.graph.control.CanvasState;
import org.freeplane.plugin.graph.control.GraphWorkspaceController;
import org.freeplane.plugin.graph.control.GraphWorkspaceHandle;
import org.freeplane.plugin.graph.control.GraphWorkspaceOpenException;
import org.freeplane.plugin.graph.control.GraphWorkspacePresentation;
import org.freeplane.plugin.graph.control.GraphWorkspaceView;
import org.freeplane.plugin.graph.control.GraphWorkspaceViewBinding;
import org.freeplane.plugin.graph.control.OperationalStatus;
import org.freeplane.plugin.graph.control.WorkspaceCloseController;
import org.freeplane.plugin.graph.control.WorkspaceSessionStatus;
import org.freeplane.plugin.graph.control.WorkspaceSessionStatusListener;
import org.freeplane.plugin.graph.geometry.EdgePaths;
import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.HullGeometry;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.geometry.NodeGeometry;
import org.freeplane.plugin.graph.layout.LayoutFrame;
import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.input.MapAvailability;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.GraphCommandResult;
import org.freeplane.plugin.graph.workspace.ListenerRegistration;
import org.freeplane.plugin.graph.workspace.RecentWorkspaceList;
import org.freeplane.plugin.graph.workspace.WorkspaceTransition;
import org.freeplane.plugin.graph.workspace.model.DisplaySettings;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.freeplane.plugin.graph.workspace.model.PinRecord;
import org.freeplane.plugin.graph.workspace.model.RelationshipDirection;
import org.freeplane.plugin.graph.workspace.model.UnknownXml;
import org.freeplane.plugin.graph.workspace.model.Viewport;
import org.freeplane.plugin.graph.workspace.model.DisplaySettings.CanvasTheme;
import org.freeplane.plugin.graph.workspace.model.WorkspaceDocument;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;
import org.freeplane.core.util.TextUtils;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.components.FrameResynchronizer;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

public class GraphWorkspaceWindowModelShould {
    @Test
    public void fullScreenMenuItemHasExpectedNameAndTranslationKey() {
        GraphWorkspaceWindow.runOnEdt(() -> {
            try (GraphWindowChromeShould.ChromeFixture f = new GraphWindowChromeShould.ChromeFixture(true)) {
                JMenu view = menu(f.model, "graph-workspace-view-menu");
                assertThat(view.getMenuComponents()).extracting(Component::getName).containsExactly(
                    "graph-workspace-menu-item-fit-graph", "graph-workspace-menu-item-reset-zoom",
                    "graph-workspace-menu-item-zoom-in", "graph-workspace-menu-item-zoom-out",
                    "graph-workspace-menu-item-settings", "graph-workspace-full-screen-menu-item",
                    "graph-workspace-maps-sidebar-menu-item");
                JCheckBoxMenuItem item = (JCheckBoxMenuItem) view.getMenuComponent(5);
                assertThat(item.getText()).isEqualTo("graph_workspace.action.full_screen");
                assertThat(item.getAccelerator()).isNull();
            }
        });
    }

    @Test
    public void fullScreenMenuItemIsDisabledAndUncheckedWhenUnavailable() {
        GraphWorkspaceWindow.runOnEdt(() -> {
            try (GraphWindowChromeShould.ChromeFixture f = new GraphWindowChromeShould.ChromeFixture(true)) {
                JCheckBoxMenuItem item = (JCheckBoxMenuItem) GraphWindowChromeShould.field(f.model, "fullScreenMenuItem");
                ScreenFillSurface surface = mock(ScreenFillSurface.class);
                GraphPresentationMode mode = presentationMode(f, surface, item, false);
                assertThat(item.isEnabled()).isFalse();
                assertThat(item.isSelected()).isFalse();
                mode.toggle();
                mode.requestExit();
                org.mockito.Mockito.verifyNoInteractions(surface);
                f.assertNoCommands();
                mode.windowClosed();
            }
        });
    }

    @Test
    public void f11IsBoundOnceOnFocusedWindowAndNoMenuAcceleratorExists() {
        GraphWorkspaceWindow.runOnEdt(() -> {
            try (GraphWindowChromeShould.ChromeFixture f = new GraphWindowChromeShould.ChromeFixture(true)) {
                JCheckBoxMenuItem item = (JCheckBoxMenuItem) GraphWindowChromeShould.field(f.model, "fullScreenMenuItem");
                ScreenFillSurface surface = mock(ScreenFillSurface.class);
                when(surface.captureFrameState()).thenReturn(new ScreenFillSurface.FrameState(new java.awt.Rectangle(1, 2, 800, 600), 0));
                when(surface.applyScreenFill(any())).thenReturn(ScreenFillSurface.Survival.REENTERED_FULLSCREEN);
                GraphPresentationMode mode = presentationMode(f, surface, item, true);
                JRootPane root = new JRootPane();
                GraphWorkspaceWindow.installPresentationKeys(root, mode);
                KeyStroke f11 = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F11, 0);
                Object key = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(f11);
                assertThat(key).isEqualTo("graph-workspace-toggle-full-screen");
                assertThat(root.getInputMap(JComponent.WHEN_FOCUSED).get(f11)).isNull();
                assertThat(root.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(f11)).isNull();
                assertThat(root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke("ESCAPE"))).isNull();
                assertThat(item.getAccelerator()).isNull();
                root.getActionMap().get(key).actionPerformed(new java.awt.event.ActionEvent(root, 0, "F11"));
                verify(surface, org.mockito.Mockito.times(1)).applyScreenFill(any());
                assertThat(mode.isActive()).isTrue();
                f.assertNoCommands();
                mode.windowClosed();
            }
        });
    }

    @Test
    public void f11UnavailableActionDoesNothing() {
        GraphWorkspaceWindow.runOnEdt(() -> {
            try (GraphWindowChromeShould.ChromeFixture f = new GraphWindowChromeShould.ChromeFixture(true)) {
                JCheckBoxMenuItem item = new JCheckBoxMenuItem();
                ScreenFillSurface surface = mock(ScreenFillSurface.class);
                GraphPresentationMode mode = presentationMode(f, surface, item, false);
                JRootPane root = new JRootPane();
                GraphWorkspaceWindow.installPresentationKeys(root, mode);
                Object key = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .get(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F11, 0));
                assertThat(key).isNotNull();
                root.getActionMap().get(key).actionPerformed(new java.awt.event.ActionEvent(root, 0, "F11"));
                org.mockito.Mockito.verifyNoInteractions(surface);
                assertThat(GraphWindowChromeShould.field(f.model.presentationViewport(), "armed")).isEqualTo(false);
                f.assertNoCommands();
                mode.windowClosed();
            }
        });
    }

    private GraphPresentationMode presentationMode(final GraphWindowChromeShould.ChromeFixture f,
            final ScreenFillSurface surface, final JCheckBoxMenuItem item, final boolean supported) {
        return new GraphPresentationMode(surface, f.model, f.model.presentationViewport(),
            new PresentationHint(new javax.swing.JLayeredPane(), "exit"), item, () -> { }, f.logs::add, supported);
    }

    @Test
    public void modelFrameRecoveryReportsObservableSurvivalAndBoundedCalls() {
        for (boolean entering : new boolean[] {true, false}) {
            for (int failures = 0; failures <= 2; failures++) {
                final int failureCount = failures;
                List<String> calls = new ArrayList<>();
                List<String> logs = new ArrayList<>();
                final boolean[] decorated = {!entering};
                final boolean[] visible = {true};
                java.awt.Rectangle original = new java.awt.Rectangle(30, 40, 800, 600);
                java.awt.Rectangle screen = new java.awt.Rectangle(0, 0, 1920, 1080);
                final java.awt.Rectangle[] bounds = {entering ? original : screen};
                final int[] extendedState = {entering ? 0 : java.awt.Frame.MAXIMIZED_BOTH};
                ScreenFillSurface.FrameState previous = new ScreenFillSurface.FrameState(original, 0);
                Runnable enter = () -> {
                    calls.add("enter");
                    decorated[0] = false;
                    visible[0] = false;
                    if (calls.size() <= failureCount) { throw new AssertionError("enter-failure"); }
                    bounds[0] = screen;
                    extendedState[0] = java.awt.Frame.MAXIMIZED_BOTH;
                    visible[0] = true;
                };
                java.util.function.Consumer<ScreenFillSurface.FrameState> restore = saved -> {
                    calls.add("restore");
                    decorated[0] = true;
                    visible[0] = false;
                    if (calls.size() <= failureCount) { throw new IllegalStateException("restore-failure"); }
                    bounds[0] = saved.bounds();
                    extendedState[0] = saved.extendedState();
                    visible[0] = true;
                };
                GraphWorkspaceWindow.FrameTransitions transitions = new GraphWorkspaceWindow.FrameTransitions(enter, restore, logs::add);
                ScreenFillSurface.Survival survival = entering ? transitions.applyScreenFill(previous)
                    : transitions.restoreFrameState(previous);
                assertThat(calls).containsExactlyElementsOf(failures == 0
                    ? Collections.singletonList(entering ? "enter" : "restore")
                    : Arrays.asList(entering ? "enter" : "restore", entering ? "restore" : "enter"));
                assertThat(logs).hasSize(failures);
                if (failures == 2) {
                    assertThat(survival).isEqualTo(ScreenFillSurface.Survival.UNKNOWN_FRAME);
                    assertThat(visible[0]).isFalse();
                }
                else {
                    boolean fullscreen = entering == (failures == 0);
                    assertThat(survival).isEqualTo(fullscreen ? ScreenFillSurface.Survival.REENTERED_FULLSCREEN
                        : ScreenFillSurface.Survival.RESTORED_WINDOWED);
                    assertThat(decorated[0]).isEqualTo(!fullscreen);
                    assertThat(visible[0]).isTrue();
                    assertThat(bounds[0]).isEqualTo(fullscreen ? screen : original);
                    assertThat(extendedState[0]).isEqualTo(fullscreen ? java.awt.Frame.MAXIMIZED_BOTH : 0);
                }
                // A broken logger also stays inside this recovery boundary.
                new GraphWorkspaceWindow.FrameTransitions(() -> { throw new AssertionError("raw"); },
                    saved -> { throw new IllegalStateException("inverse"); }, message -> { throw new AssertionError("logger"); })
                    .applyScreenFill(previous);
            }
        }
    }

    private static final Path OPEN_PATH = Paths.get("/tmp/opened.graph-workspace");
    private static final Color SEARCH_STUB_COLOR = new Color(0xFFFF00FF);
    private static final Color DISABLED_PROBE_COLOR = new Color(0xFF4080C0);
    private static final MapReferenceId ACTIVE_ID = id(1L);
    private static final java.util.List<EdtResources> RESOURCES =
        new java.util.ArrayList<EdtResources>();
    private MockedStatic<TextUtils> textUtils;
    private MockedStatic<ResourceController> resourceController;

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

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
            .thenAnswer(invocation -> formattedText(invocation));
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

    private static String formattedText(final org.mockito.invocation.InvocationOnMock invocation) {
        final Object[] invocationArguments = invocation.getArguments();
        final Object[] formatArguments;
        if (invocationArguments.length == 2 && invocationArguments[1] instanceof Object[]) {
            formatArguments = (Object[]) invocationArguments[1];
        }
        else {
            formatArguments = Arrays.copyOfRange(invocationArguments, 1, invocationArguments.length);
        }
        return invocationArguments[0] + Arrays.toString(formatArguments);
    }

    @Test
    public void resolvesFullScreenControlsFromTheEnglishResource() throws Exception {
        final java.util.Properties english = new java.util.Properties();
        try (java.io.InputStream input = Files.newInputStream(
                Paths.get("../freeplane/src/viewer/resources/translations/Resources_en.properties"))) {
            english.load(input);
        }
        assertThat(english.getProperty("graph_workspace.action.full_screen")).isEqualTo("Full Screen");
        assertThat(english.getProperty("graph_workspace.hint.full_screen_exit"))
            .isEqualTo("Esc or F11 exits full screen");
        final Fixture fixture = fixture(RecentWorkspaceList.empty());
        GraphWorkspaceWindow.runOnEdt(() -> {
            EdtResources resources = new EdtResources();
            RESOURCES.add(resources);
            resources.textUtils.when(() -> TextUtils.getText(any(String.class)))
                .thenAnswer(invocation -> english.getProperty((String) invocation.getArgument(0)));
            GraphWorkspaceWindowModel model = new GraphWorkspaceWindowModel(fixture.handle, fixture.binding,
                fixture.applicationController, () -> OPEN_PATH, fixture.closeController,
                () -> { }, () -> { }, () -> { }, message -> { });
            try {
                assertThat(((JCheckBoxMenuItem) GraphWindowChromeShould.field(model,
                    "fullScreenMenuItem")).getText()).isEqualTo("Full Screen");
            }
            finally {
                model.close();
            }
        });
    }

    @Test
    public void rendersVisibleShellControlsFromGraphWorkspaceResourceKeys() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();

        assertThat(model.toolbar().openButton().getText()).isEqualTo("graph_workspace.action.open");
        assertThat(model.toolbar().saveButton().getText()).isEqualTo("graph_workspace.action.save");
        assertThat(model.settingsPanel().getComponent(0).getName()).isEqualTo("graph-workspace-settings-heading");
        JPanel headingRow = (JPanel) componentByName(model.mapList(), "graph-workspace-map-list-heading-row");
        assertThat(componentByName(headingRow, "graph-workspace-map-list-heading").getName())
            .isEqualTo("graph-workspace-map-list-heading");
        model.close();
    }

    @Test
    public void keepsDisplaySettingsControlsPackedAtTheTopOfATallPanel() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();

        GraphWorkspaceWindow.runOnEdt(() -> {
            model.settingsPanel().setSize(244, 1000);
            layoutRecursively(model.settingsPanel());
        });

        AbstractButton firstControl = model.settingsPanel().showArrowheads();
        AbstractButton lastControl = model.settingsPanel().dimUnrelated();
        assertThat(lastControl.getY() - firstControl.getY()).isLessThan(200);
        model.close();
    }

    private static void layoutRecursively(final Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container) {
                layoutRecursively((Container) child);
            }
        }
    }

    @Test
    public void showsTheFullCanvasThemeSelectorInsideTheSettingsPanel() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();

        GraphWorkspaceWindow.runOnEdt(() -> {
            model.settingsPanel().setSize(244, 1000);
            layoutRecursively(model.settingsPanel());
        });

        JComboBox<?> canvasTheme = model.settingsPanel().canvasTheme();
        Container themeRow = canvasTheme.getParent();
        assertThat(canvasTheme.getY() + canvasTheme.getHeight())
            .isLessThanOrEqualTo(themeRow.getHeight());
        assertThat(canvasTheme.getHeight()).isGreaterThanOrEqualTo(canvasTheme.getPreferredSize().height);
        model.close();
    }

    @Test
    public void composesAHeadlessModelessWorkspaceWithStablePanelsAndApprovedControls() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();

        assertThat(model.menuBar()).isNotNull();
        assertThat(model.toolbar()).isNotNull();
        assertThat(model.mapList()).isNotNull();
        assertThat(model.canvas()).isInstanceOf(GraphCanvas.class);
        assertThat(model.settingsPanel()).isNotNull();
        assertThat(model.statusSlot()).isNotNull();
        assertThat(model.statusSlot().getName()).isEqualTo("graph-workspace-status-slot");
        assertThat(model.mapList().getPreferredSize().width).isGreaterThan(0);
        assertThat(model.settingsPanel().getPreferredSize().width).isGreaterThan(0);
        assertThat(model.toolbar().getPreferredSize().height).isGreaterThan(0);
        assertThat(model.canvas().getPreferredSize()).isEqualTo(new Dimension(800, 560));
        assertThat(model.toolbar().approvedControlNames()).contains(
            "open", "save", "add-map", "remove-map", "select", "connect", "direction", "search",
            "settings", "zoom-in", "zoom-out", "fit-graph", "reset-zoom", "pin").doesNotContain("unpin");
        assertThat(model.settingsPanel().approvedSettingNames()).contains(
            "show-arrowheads", "canvas-theme", "remember-viewport", "dim-unrelated");
        assertThat(model.mapList().rowHeight()).isEqualTo(MapListPanel.ROW_HEIGHT);
        assertThat(model.toolbar().getComponentCount()).isGreaterThan(0);
        assertThat(model.settingsPanel().getComponentCount()).isGreaterThan(0);
        assertThat(UIManager.getLookAndFeel()).isNotNull();

        assertThat(model.content().getComponentCount()).isEqualTo(3);
        JPanel graphArea = graphArea(model);
        assertThat(graphArea.getComponentCount()).isEqualTo(2);
        assertThat(componentByName(graphArea, "graph-workspace-settings")).isSameAs(model.settingsPanel());
        JSplitPane splitPane = splitPane(model);
        assertThat(sidebarPanel(model)).isNotSameAs(graphArea.getComponent(0));
        assertThat(sidebarPanel(model).mapList()).isSameAs(model.mapList());
        assertThat(splitPane.getRightComponent()).isSameAs(canvasScrollPane(model));
        assertThat(canvasScrollPane(model).getName()).isEqualTo("graph-workspace-scroll-pane");
        assertThat(canvasScrollPane(model).getViewport().getView()).isSameAs(model.canvas());
        model.close();
    }

    @Test
    public void preservesSidebarFieldsWhenADisplayCheckboxIsToggled() {
        DisplaySettings settings = DisplaySettings.of(true, CanvasTheme.FOLLOW_FREEPLANE, true, true, 400, true,
            emptyUnknownXml());
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)),
            false, WorkspaceSessionStatus.empty(), presentation(settings, ACTIVE_ID)).answerSidebarRoundTrip();
        GraphWorkspaceWindowModel model = fixture.model();

        model.settingsPanel().showArrowheads().doClick();

        assertThat(displayCommandCount(fixture)).isEqualTo(1);
        DisplaySettings committed = lastDisplaySettings(fixture);
        assertThat(committed.showArrowheads()).isFalse();
        assertThat(committed.mapSidebarWidth()).isEqualTo(400);
        assertThat(committed.mapSidebarHidden()).isTrue();
        model.close();
    }

    @Test
    public void appliesStoredSidebarStateWithoutEmittingACommand() {
        DisplaySettings initial = DisplaySettings.of(true, CanvasTheme.FOLLOW_FREEPLANE, true, true, 264, false,
            emptyUnknownXml());
        DisplaySettings changed = DisplaySettings.of(true, CanvasTheme.FOLLOW_FREEPLANE, true, true, 500, false,
            emptyUnknownXml());
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)),
            false, WorkspaceSessionStatus.empty(), presentation(initial, ACTIVE_ID)).answerSidebarRoundTrip();
        GraphWorkspaceWindowModel model = fixture.model();
        GraphWorkspaceWindow.runOnEdt(() -> {
            splitPane(model).setSize(1000, 300);
            splitPane(model).doLayout();
        });
        GraphWorkspaceWindow.runOnEdt(() -> { });

        model.execute(GraphCommands.display(changed));
        int afterCommand = displayCommandCount(fixture);
        model.acceptCanvasState(emptyState());

        assertThat(afterCommand).isEqualTo(1);
        assertThat(splitPane(model).getDividerLocation()).isEqualTo(500);
        assertThat(mapsSidebarMenuItem(model).isSelected()).isTrue();
        assertThat(displayCommandCount(fixture)).isEqualTo(1);
        model.close();
    }

    @Test
    public void showsTheStoredSidebarWidthOnOpenInsteadOfTheMinimum() {
        DisplaySettings settings = DisplaySettings.of(true, CanvasTheme.FOLLOW_FREEPLANE, true, true, 400, false,
            emptyUnknownXml());
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)),
            false, WorkspaceSessionStatus.empty(), presentation(settings, ACTIVE_ID)).answerSidebarRoundTrip();
        GraphWorkspaceWindowModel model = fixture.model();

        assertThat(splitPane(model).getWidth()).isZero();
        GraphWorkspaceWindow.runOnEdt(() -> {
            splitPane(model).setSize(1000, 300);
            splitPane(model).doLayout();
        });
        GraphWorkspaceWindow.runOnEdt(() -> { });

        assertThat(splitPane(model).getDividerLocation()).isEqualTo(400);
        model.close();
    }

    @Test
    public void hostsTheMapSidebarInASplitPaneInsideTheGraphArea() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();

        assertThat(model.content().getComponentCount()).isEqualTo(3);
        JPanel graphArea = graphArea(model);
        assertThat(graphArea.getComponentCount()).isEqualTo(2);
        assertThat(componentByName(graphArea, "graph-workspace-settings")).isSameAs(model.settingsPanel());
        assertThat(sidebarPanel(model).getName()).isEqualTo("graph-workspace-map-sidebar");
        assertThat(sidebarPanel(model).mapList()).isSameAs(model.mapList());
        assertThat(sidebarPanel(model)).isNotSameAs(graphArea.getComponent(0));
        assertThat(canvasScrollPane(model).getName()).isEqualTo("graph-workspace-scroll-pane");
        assertThat(canvasScrollPane(model).getViewport().getView()).isSameAs(model.canvas());
        model.close();
    }

    @Test
    public void rendersFourIconOnlyControlsThroughTheScalableIconLookup() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        Icon undoIcon = icon(24, 16);
        Icon redoIcon = icon(16, 16);
        Icon zoomInIcon = icon(20, 16);
        Icon zoomOutIcon = icon(28, 16);
        fixture.stubIcon("/images/undo.svg?useAccentColor=true", undoIcon);
        fixture.stubIcon("/images/redo.svg?useAccentColor=true", redoIcon);
        fixture.stubIcon("/images/ZoomIn24.svg?useAccentColor=true", zoomInIcon);
        fixture.stubIcon("/images/ZoomOut24.svg?useAccentColor=true", zoomOutIcon);
        GraphWorkspaceWindowModel model = fixture.model();

        assertThat(model.toolbar().undoButton().getIcon()).isSameAs(undoIcon);
        assertThat(model.toolbar().undoButton().getText()).isNull();
        assertThat(model.toolbar().undoButton().getToolTipText())
            .isEqualTo("graph_workspace.action.undo_workspace");
        assertThat(model.toolbar().undoButton().getAccessibleContext().getAccessibleName())
            .isEqualTo("graph_workspace.action.undo_workspace");
        assertThat(model.toolbar().undoButton().getName()).isEqualTo("graph-workspace-undo");

        assertThat(model.toolbar().redoButton().getIcon()).isSameAs(redoIcon);
        assertThat(model.toolbar().redoButton().getText()).isNull();
        assertThat(model.toolbar().redoButton().getToolTipText())
            .isEqualTo("graph_workspace.action.redo_workspace");
        assertThat(model.toolbar().redoButton().getAccessibleContext().getAccessibleName())
            .isEqualTo("graph_workspace.action.redo_workspace");
        assertThat(model.toolbar().redoButton().getName()).isEqualTo("graph-workspace-redo");

        assertThat(model.toolbar().zoomInButton().getIcon()).isSameAs(zoomInIcon);
        assertThat(model.toolbar().zoomInButton().getText()).isNull();
        assertThat(model.toolbar().zoomInButton().getToolTipText())
            .isEqualTo("graph_workspace.action.zoom_in");
        assertThat(model.toolbar().zoomInButton().getAccessibleContext().getAccessibleName())
            .isEqualTo("graph_workspace.action.zoom_in");
        assertThat(model.toolbar().zoomInButton().getName()).isEqualTo("graph-workspace-zoom-in");

        assertThat(model.toolbar().zoomOutButton().getIcon()).isSameAs(zoomOutIcon);
        assertThat(model.toolbar().zoomOutButton().getText()).isNull();
        assertThat(model.toolbar().zoomOutButton().getToolTipText())
            .isEqualTo("graph_workspace.action.zoom_out");
        assertThat(model.toolbar().zoomOutButton().getAccessibleContext().getAccessibleName())
            .isEqualTo("graph_workspace.action.zoom_out");
        assertThat(model.toolbar().zoomOutButton().getName()).isEqualTo("graph-workspace-zoom-out");

        assertThat(model.toolbar().undoButton().getPreferredSize().width
            - model.toolbar().redoButton().getPreferredSize().width).isEqualTo(8);
        assertThat(model.toolbar().zoomOutButton().getPreferredSize().width
            - model.toolbar().zoomInButton().getPreferredSize().width).isEqualTo(8);

        verify(fixture.resourceController()).getOptionalIcon("/images/undo.svg?useAccentColor=true");
        verify(fixture.resourceController()).getOptionalIcon("/images/redo.svg?useAccentColor=true");
        verify(fixture.resourceController()).getOptionalIcon("/images/ZoomIn24.svg?useAccentColor=true");
        verify(fixture.resourceController()).getOptionalIcon("/images/ZoomOut24.svg?useAccentColor=true");
        model.close();
    }

    @Test
    public void keepsTextFallbackWhenTheIconLookupReturnsNoIcon() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();

        assertThat(model.toolbar().undoButton().getIcon()).isNull();
        assertThat(model.toolbar().undoButton().getText())
            .isEqualTo("graph_workspace.action.undo_workspace");
        assertThat(model.toolbar().undoButton().getToolTipText()).isNull();
        assertThat(model.toolbar().undoButton().getName()).isEqualTo("graph-workspace-undo");
        assertThat(model.toolbar().redoButton().getIcon()).isNull();
        assertThat(model.toolbar().redoButton().getText())
            .isEqualTo("graph_workspace.action.redo_workspace");
        assertThat(model.toolbar().redoButton().getToolTipText()).isNull();
        assertThat(model.toolbar().redoButton().getName()).isEqualTo("graph-workspace-redo");
        assertThat(model.toolbar().zoomInButton().getIcon()).isNull();
        assertThat(model.toolbar().zoomInButton().getText())
            .isEqualTo("graph_workspace.action.zoom_in");
        assertThat(model.toolbar().zoomInButton().getToolTipText()).isNull();
        assertThat(model.toolbar().zoomInButton().getName()).isEqualTo("graph-workspace-zoom-in");
        assertThat(model.toolbar().zoomOutButton().getIcon()).isNull();
        assertThat(model.toolbar().zoomOutButton().getText())
            .isEqualTo("graph_workspace.action.zoom_out");
        assertThat(model.toolbar().zoomOutButton().getToolTipText()).isNull();
        assertThat(model.toolbar().zoomOutButton().getName()).isEqualTo("graph-workspace-zoom-out");

        org.mockito.Mockito.clearInvocations(fixture.handle);
        model.toolbar().zoomInButton().doClick();
        verify(fixture.handle, org.mockito.Mockito.times(1)).execute(any(GraphCommand.class));
        model.close();
    }

    @Test
    public void keepsUndoRedoEnablementRulesAndZoomButtonsEnabledWithoutHistoryAndInReadOnlySessions() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false,
            WorkspaceSessionStatus.empty());
        GraphWorkspaceWindowModel model = fixture.model();

        assertThat(model.toolbar().undoButton().isEnabled()).isFalse();
        assertThat(model.toolbar().redoButton().isEnabled()).isFalse();
        assertThat(model.toolbar().zoomInButton().isEnabled()).isTrue();
        assertThat(model.toolbar().zoomOutButton().isEnabled()).isTrue();
        org.mockito.Mockito.clearInvocations(fixture.handle);
        model.toolbar().zoomInButton().doClick();
        verify(fixture.handle, org.mockito.Mockito.times(1)).execute(any(GraphCommand.class));
        model.close();

        Fixture readOnlyFixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), true,
            WorkspaceSessionStatus.empty());
        GraphWorkspaceWindowModel readOnlyModel = readOnlyFixture.model();

        assertThat(readOnlyModel.toolbar().undoButton().isEnabled()).isFalse();
        assertThat(readOnlyModel.toolbar().redoButton().isEnabled()).isFalse();
        assertThat(readOnlyModel.toolbar().zoomInButton().isEnabled()).isTrue();
        assertThat(readOnlyModel.toolbar().zoomOutButton().isEnabled()).isTrue();
        org.mockito.Mockito.clearInvocations(readOnlyFixture.handle);
        readOnlyModel.toolbar().zoomInButton().doClick();
        verify(readOnlyFixture.handle, org.mockito.Mockito.never()).execute(any(GraphCommand.class));
        readOnlyModel.close();
    }

    @Test
    public void groupsSelectAndConnectInOneToolSwitch() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        WorkspaceToolbar toolbar = model.toolbar();
        JToggleButton select = toolbar.selectButton();
        JToggleButton connect = toolbar.connectButton();

        assertThat(select.getParent()).isInstanceOf(ToolSwitch.class);
        assertThat(connect.getParent()).isSameAs(select.getParent());
        ToolSwitch toolSwitch = (ToolSwitch) select.getParent();
        GridLayout layout = (GridLayout) toolSwitch.getLayout();
        assertThat(layout.getRows()).isEqualTo(1);
        assertThat(layout.getColumns()).isEqualTo(2);
        assertThat(layout.getHgap()).isZero();
        assertThat(layout.getVgap()).isZero();
        assertThat(toolSwitch.getName()).isEqualTo("graph-workspace-tool-switch");
        assertThat(toolSwitch.isOpaque()).isFalse();
        assertThat(toolSwitch.getComponentCount()).isEqualTo(2);
        assertThat(toolSwitch.getComponent(0)).isSameAs(select);
        assertThat(toolSwitch.getComponent(1)).isSameAs(connect);
        ButtonGroup group = select.getModel().getGroup();
        assertThat(group).isNotNull();
        assertThat(connect.getModel().getGroup()).isSameAs(group);
        assertThat(Collections.list(group.getElements()).stream()
            .map(segment -> segment.getModel())
            .collect(Collectors.toList()))
            .containsExactly(select.getModel(), connect.getModel());
        assertThat(toolbar.approvedControlNames()).containsExactlyInAnyOrder(
            "open", "save", "add-map", "remove-map", "select", "connect", "direction", "search",
            "settings", "zoom-in", "zoom-out", "fit-graph", "reset-zoom", "pin");
        int toolSwitchCount = 0;
        for (Component component : toolbar.getComponents()) {
            if (component instanceof ToolSwitch) {
                toolSwitchCount++;
            }
        }
        assertThat(toolbar.getComponentCount()).isEqualTo(13);
        assertThat(toolSwitchCount).isEqualTo(1);
        model.close();
    }

    @Test
    public void switchesToolsThroughTheSharedButtonGroup() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        List<InteractionTool> tools = new ArrayList<InteractionTool>();
        model.toolbar().setToolListener(tools::add);

        model.toolbar().connectButton().doClick();
        assertThat(model.toolbar().connectButton().isSelected()).isTrue();
        assertThat(model.toolbar().selectButton().isSelected()).isFalse();
        model.toolbar().selectButton().doClick();
        assertThat(model.toolbar().selectButton().isSelected()).isTrue();
        assertThat(model.toolbar().connectButton().isSelected()).isFalse();
        assertThat(tools).containsExactly(InteractionTool.CONNECT, InteractionTool.SELECT);
        model.close();
    }

    @Test
    public void keepsTheSearchPromptOutOfTheDocument() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        List<String> queries = new ArrayList<String>();
        model.toolbar().setSearchListener(queries::add);

        assertThat(model.toolbar().searchField().getText()).isEmpty();
        assertThat(queries).isEmpty();
        model.toolbar().searchField().setText("Alpha");
        assertThat(model.toolbar().searchField().getText()).isEqualTo("Alpha");
        assertThat(queries).containsExactly("Alpha");
        model.toolbar().searchField().setText("");
        assertThat(model.toolbar().searchField().getText()).isEmpty();
        assertThat(queries).containsExactly("Alpha", "");
        model.close();
    }

    @Test
    public void paintsSearchPromptAndMagnifierFromLiveState() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        RecordingPaintingIcon stub = new RecordingPaintingIcon(16, 16, SEARCH_STUB_COLOR);
        fixture.stubIcon("/images/GraphSearch.svg?useAccentColor=true", stub);
        GraphWorkspaceWindowModel model = fixture.model();
        JTextField field = model.toolbar().searchField();

        assertThat(field.getText()).isEmpty();
        assertThat(field.getPreferredSize().height).isEqualTo(26);
        assertThat(field.getPreferredSize().width).isEqualTo(Math.max(160, field.getInsets().left
            + field.getFontMetrics(field.getFont()).stringWidth("graph_workspace.tooltip.search")
            + field.getInsets().right));

        BufferedImage emptyImage = paintField(field);
        assertThat(pixelsMatching(emptyImage, SEARCH_STUB_COLOR)).isGreaterThan(0);
        assertThat(pixelsMatching(emptyImage, field.getDisabledTextColor())).isGreaterThan(0);
        assertThat(stub.lastX).isEqualTo(field.getInsets().left - field.getMargin().left);
        assertThat(stub.lastY).isEqualTo((field.getHeight() - stub.getIconHeight()) / 2);

        GraphSearchField tallField = new GraphSearchField();
        tallField.setPrompt("graph_workspace.tooltip.search");
        tallField.setPromptIcon(new RecordingPaintingIcon(40, 40, SEARCH_STUB_COLOR));
        Insets tallMargin = tallField.getMargin();
        assertThat(tallField.getPreferredSize().height)
            .isEqualTo(40 + tallMargin.top + tallMargin.bottom)
            .isGreaterThan(26);

        field.setText("Results");
        BufferedImage textImage = paintField(field);
        assertThat(pixelsMatching(textImage, SEARCH_STUB_COLOR)).isGreaterThan(0);
        assertThat(pixelsMatching(textImage, field.getDisabledTextColor())).isZero();
        int firstTextPixel = -1;
        int bandTop = field.getInsets().top;
        int bandBottom = bandTop + field.getFontMetrics(field.getFont()).getHeight();
        for (int y = bandTop; y < bandBottom && firstTextPixel < 0; y++) {
            for (int x = field.getInsets().left; x < field.getWidth() - field.getInsets().right; x++) {
                int rgb = textImage.getRGB(x, y);
                if (rgb != field.getBackground().getRGB() && rgb != SEARCH_STUB_COLOR.getRGB()) {
                    firstTextPixel = x;
                    break;
                }
            }
        }
        assertThat(firstTextPixel).isGreaterThanOrEqualTo(stub.lastX + stub.getIconWidth());
        model.close();
    }

    @Test
    public void hidesSearchPromptWhileFocused() {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        GraphSearchField field = new GraphSearchField();
        field.setPrompt("graph_workspace.tooltip.search");
        RecordingPaintingIcon stub = new RecordingPaintingIcon(16, 16, SEARCH_STUB_COLOR);
        field.setPromptIcon(stub);
        BufferedImage[] focusedImage = new BufferedImage[1];
        int[] recordedX = new int[1];
        int[] recordedY = new int[1];
        int[] paintedX = new int[1];
        int[] paintedY = new int[1];
        withFocusedField(field, new Runnable() {
            @Override
            public void run() {
                focusedImage[0] = paintField(field);
                recordedX[0] = field.getInsets().left - field.getMargin().left;
                recordedY[0] = (field.getHeight() - stub.getIconHeight()) / 2;
                // The shown frame keeps repainting after this action, so snapshot
                // the icon position of the focused paint before it can be overwritten.
                paintedX[0] = stub.lastX;
                paintedY[0] = stub.lastY;
            }
        });

        assertThat(focusedImage[0]).isNotNull();
        assertThat(pixelsMatching(focusedImage[0], SEARCH_STUB_COLOR)).isGreaterThan(0);
        assertThat(pixelsMatching(focusedImage[0], field.getDisabledTextColor())).isZero();
        assertThat(paintedX[0]).isEqualTo(recordedX[0]);
        assertThat(paintedY[0]).isEqualTo(recordedY[0]);
    }

    @Test
    public void paintsTheSearchPromptWithoutAMagnifier() {
        final GraphSearchField[] field = new GraphSearchField[1];
        final BufferedImage[] image = new BufferedImage[1];
        final EdtResources[] edtResources = new EdtResources[1];
        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                edtResources[0] = new EdtResources();
                GraphSearchField searchField = new GraphSearchField();
                searchField.setPrompt("Search nodes and maps");
                searchField.setPromptIcon(null);
                searchField.setSize(searchField.getPreferredSize());
                field[0] = searchField;
                image[0] = paintField(searchField);
            }
        });
        RESOURCES.add(edtResources[0]);

        assertThat(field[0].getText()).isEmpty();
        assertThat(field[0].getPreferredSize().height).isEqualTo(26);
        assertThat(field[0].getPreferredSize().width).isEqualTo(Math.max(160,
            field[0].getInsets().left
                + field[0].getFontMetrics(field[0].getFont()).stringWidth("Search nodes and maps")
                + field[0].getInsets().right));
        assertThat(pixelsMatching(image[0], SEARCH_STUB_COLOR)).isZero();
        assertThat(pixelsMatchingAtOrRightOf(image[0], field[0].getDisabledTextColor(),
            field[0].getInsets().left)).isGreaterThan(0);
    }

    @Test
    public void resolvesTheToolbarAffordanceIconsThroughTheSharedRoutine() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        Icon selectIcon = icon(16, 16);
        Icon connectIcon = icon(16, 16);
        Icon settingsIcon = icon(16, 16);
        Icon searchIcon = icon(16, 16);
        fixture.stubIcon("/images/GraphSelect.svg?useAccentColor=true", selectIcon);
        fixture.stubIcon("/images/GraphConnect.svg?useAccentColor=true", connectIcon);
        fixture.stubIcon("/images/GraphSettings.svg?useAccentColor=true", settingsIcon);
        fixture.stubIcon("/images/GraphSearch.svg?useAccentColor=true", searchIcon);
        GraphWorkspaceWindowModel model = fixture.model();

        assertThat(model.toolbar().selectButton().getIcon()).isSameAs(selectIcon);
        assertThat(model.toolbar().selectButton().getText()).isNull();
        assertThat(model.toolbar().selectButton().getToolTipText()).isEqualTo("graph_workspace.tool.select");
        assertThat(model.toolbar().selectButton().getAccessibleContext().getAccessibleName())
            .isEqualTo("graph_workspace.tool.select");
        assertThat(model.toolbar().selectButton().getName()).isEqualTo("graph-workspace-select");
        assertThat(model.toolbar().connectButton().getIcon()).isSameAs(connectIcon);
        assertThat(model.toolbar().connectButton().getText()).isNull();
        assertThat(model.toolbar().connectButton().getToolTipText())
            .isEqualTo("graph_workspace.tooltip.connect");
        assertThat(model.toolbar().connectButton().getAccessibleContext().getAccessibleName())
            .isEqualTo("graph_workspace.tooltip.connect");
        assertThat(model.toolbar().connectButton().getName()).isEqualTo("graph-workspace-connect");
        assertThat(model.toolbar().settingsButton().getIcon()).isSameAs(settingsIcon);
        assertThat(model.toolbar().settingsButton().getText()).isNull();
        assertThat(model.toolbar().settingsButton().getToolTipText())
            .isEqualTo("graph_workspace.tooltip.settings");
        assertThat(model.toolbar().settingsButton().getAccessibleContext().getAccessibleName())
            .isEqualTo("graph_workspace.tooltip.settings");
        assertThat(model.toolbar().settingsButton().getName()).isEqualTo("graph-workspace-settings");
        assertThat(model.toolbar().searchField().getToolTipText()).isEqualTo("graph_workspace.tooltip.search");
        assertThat(model.toolbar().searchField().getAccessibleContext().getAccessibleName())
            .isEqualTo("graph_workspace.tooltip.search");

        verify(fixture.resourceController()).getOptionalIcon("/images/GraphSelect.svg?useAccentColor=true");
        verify(fixture.resourceController()).getOptionalIcon("/images/GraphConnect.svg?useAccentColor=true");
        verify(fixture.resourceController()).getOptionalIcon("/images/GraphSettings.svg?useAccentColor=true");
        verify(fixture.resourceController()).getOptionalIcon("/images/GraphSearch.svg?useAccentColor=true");
        model.close();
    }

    @Test
    public void keepsToolbarAffordanceTextFallbackWhenIconsDoNotResolve() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        List<InteractionTool> tools = new ArrayList<InteractionTool>();
        model.toolbar().setToolListener(tools::add);
        int[] settingsClicks = new int[1];
        model.toolbar().setSettingsAction(() -> settingsClicks[0]++);

        assertThat(model.toolbar().selectButton().getText()).isEqualTo("graph_workspace.tool.select");
        assertThat(model.toolbar().selectButton().getIcon()).isNull();
        assertThat(model.toolbar().selectButton().getToolTipText()).isNull();
        assertThat(model.toolbar().connectButton().getText()).isEqualTo("graph_workspace.tool.connect");
        assertThat(model.toolbar().connectButton().getIcon()).isNull();
        assertThat(model.toolbar().connectButton().getToolTipText()).isNull();
        assertThat(model.toolbar().settingsButton().getText()).isEqualTo("graph_workspace.action.settings");
        assertThat(model.toolbar().settingsButton().getIcon()).isNull();
        assertThat(model.toolbar().settingsButton().getToolTipText()).isNull();

        model.toolbar().selectButton().doClick();
        model.toolbar().connectButton().doClick();
        model.toolbar().settingsButton().doClick();
        assertThat(tools).containsExactly(InteractionTool.SELECT, InteractionTool.CONNECT);
        assertThat(settingsClicks[0]).isEqualTo(1);
        model.close();
    }

    @Test
    public void dimsDisabledToolbarGlyphs() {
        final Icon stub = paintingIcon(DISABLED_PROBE_COLOR, 16, 16);
        final Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        fixture.stubIcon("/images/GraphSelect.svg?useAccentColor=true", stub);
        fixture.stubIcon("/images/GraphConnect.svg?useAccentColor=true", stub);
        final GraphWorkspaceWindowModel model = fixture.model();
        final WorkspaceToolbar toolbar = model.toolbar();
        for (final AbstractButton button : new AbstractButton[] {
                toolbar.selectButton(), toolbar.connectButton() }) {
            assertThat(button.getIcon()).isNotNull();
            assertThat(button.getDisabledIcon()).as("%s disabled icon", button.getName()).isNotNull();
            assertThat(button.getDisabledIcon()).isNotSameAs(button.getIcon());
            assertThat(averageLuminance(button.getDisabledIcon())).as("%s dimmed", button.getName())
                .isLessThan(averageLuminance(button.getIcon()));
        }
        model.close();
    }

    @Test
    public void preservesToolbarEnablementInReadOnlySessions() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false,
            WorkspaceSessionStatus.empty());
        GraphWorkspaceWindowModel model = fixture.model();

        assertThat(model.toolbar().selectButton().isEnabled()).isTrue();
        assertThat(model.toolbar().connectButton().isEnabled()).isTrue();
        assertThat(model.toolbar().settingsButton().isEnabled()).isTrue();
        assertThat(model.toolbar().searchField().isEnabled()).isTrue();
        assertThat(model.toolbar().directionComboBox().isEnabled()).isTrue();
        assertThat(model.toolbar().undoButton().isEnabled()).isFalse();
        assertThat(model.toolbar().redoButton().isEnabled()).isFalse();
        assertThat(model.toolbar().zoomInButton().isEnabled()).isTrue();
        assertThat(model.toolbar().zoomOutButton().isEnabled()).isTrue();
        assertThat(model.toolbar().pinButton().isEnabled()).isFalse();
        model.close();

        Fixture readOnlyFixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), true,
            WorkspaceSessionStatus.empty());
        GraphWorkspaceWindowModel readOnlyModel = readOnlyFixture.model();

        assertThat(readOnlyModel.toolbar().selectButton().isEnabled()).isTrue();
        assertThat(readOnlyModel.toolbar().connectButton().isEnabled()).isFalse();
        assertThat(readOnlyModel.toolbar().settingsButton().isEnabled()).isTrue();
        assertThat(readOnlyModel.toolbar().searchField().isEnabled()).isTrue();
        assertThat(readOnlyModel.toolbar().directionComboBox().isEnabled()).isFalse();
        assertThat(readOnlyModel.toolbar().undoButton().isEnabled()).isFalse();
        assertThat(readOnlyModel.toolbar().redoButton().isEnabled()).isFalse();
        assertThat(readOnlyModel.toolbar().zoomInButton().isEnabled()).isTrue();
        assertThat(readOnlyModel.toolbar().zoomOutButton().isEnabled()).isTrue();
        assertThat(readOnlyModel.toolbar().pinButton().isEnabled()).isFalse();
        readOnlyModel.close();
    }

    @Test
    public void keepsTheSettingsGearSynchronizedWithThePanel() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();

        assertThat(model.settingsPanel().isVisible()).isTrue();
        assertThat(model.toolbar().settingsButton().isSelected()).isTrue();

        model.toolbar().settingsButton().doClick();
        assertThat(model.settingsPanel().isVisible()).isFalse();
        assertThat(model.toolbar().settingsButton().isSelected()).isFalse();

        menuItem(model, "settings").doClick();
        assertThat(model.settingsPanel().isVisible()).isTrue();
        assertThat(model.toolbar().settingsButton().isSelected()).isTrue();

        model.settingsPanel().setVisible(false);
        assertThat(model.toolbar().settingsButton().isSelected()).isTrue();
        model.toolbar().settingsButton().doClick();
        assertThat(model.settingsPanel().isVisible()).isTrue();
        assertThat(model.toolbar().settingsButton().isSelected()).isTrue();

        model.setReadOnly(true);
        assertThat(model.toolbar().settingsButton().isEnabled()).isTrue();
        assertThat(model.toolbar().settingsButton().isSelected())
            .isEqualTo(model.settingsPanel().isVisible());
        model.close();
    }

    @Test
    public void stylesOnlyTheSwitchSegmentsWhenIconsResolve() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        fixture.stubIcon("/images/GraphSelect.svg?useAccentColor=true", icon(16, 16));
        fixture.stubIcon("/images/GraphConnect.svg?useAccentColor=true", icon(16, 16));
        fixture.stubIcon("/images/GraphSettings.svg?useAccentColor=true", icon(16, 16));
        fixture.stubIcon("/images/GraphSearch.svg?useAccentColor=true", icon(16, 16));
        fixture.stubIcon("/images/undo.svg?useAccentColor=true", icon(16, 16));
        fixture.stubIcon("/images/redo.svg?useAccentColor=true", icon(16, 16));
        fixture.stubIcon("/images/ZoomIn24.svg?useAccentColor=true", icon(16, 16));
        fixture.stubIcon("/images/ZoomOut24.svg?useAccentColor=true", icon(16, 16));
        GraphWorkspaceWindowModel model = fixture.model();

        assertThat(model.toolbar().selectButton().getClientProperty("JButton.buttonType"))
            .isEqualTo("toolBarButton");
        assertThat(model.toolbar().connectButton().getClientProperty("JButton.buttonType"))
            .isEqualTo("toolBarButton");
        assertThat(model.toolbar().settingsButton().getIcon()).isNotNull();
        assertThat(model.toolbar().undoButton().getIcon()).isNotNull();
        assertThat(model.toolbar().redoButton().getIcon()).isNotNull();
        assertThat(model.toolbar().zoomInButton().getIcon()).isNotNull();
        assertThat(model.toolbar().zoomOutButton().getIcon()).isNotNull();
        assertThat(model.toolbar().settingsButton().getClientProperty("JButton.buttonType")).isNull();
        assertThat(model.toolbar().undoButton().getClientProperty("JButton.buttonType")).isNull();
        assertThat(model.toolbar().redoButton().getClientProperty("JButton.buttonType")).isNull();
        assertThat(model.toolbar().zoomInButton().getClientProperty("JButton.buttonType")).isNull();
        assertThat(model.toolbar().zoomOutButton().getClientProperty("JButton.buttonType")).isNull();
        model.close();

        Fixture fallbackFixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel fallbackModel = fallbackFixture.model();

        assertThat(fallbackModel.toolbar().selectButton().getClientProperty("JButton.buttonType")).isNull();
        assertThat(fallbackModel.toolbar().connectButton().getClientProperty("JButton.buttonType")).isNull();
        fallbackModel.close();
    }

    @Test
    public void growsTheScrollableSurfaceForVisibleWorldGeometry() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        JScrollPane graphScrollPane = canvasScrollPane(model);

        model.acceptCanvasState(wideNodeState(ACTIVE_ID));

        assertThat(model.canvas().getPreferredSize().width).isGreaterThan(800);
        assertThat(graphScrollPane.getViewport().getView()).isSameAs(model.canvas());
        model.close();
    }

    @Test
    public void persistsExternalScrollAsTheVisibleViewportOnce() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            wideNodeState(ACTIVE_ID),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        JScrollPane graphScrollPane = canvasScrollPane(model);
        graphScrollPane.setSize(new Dimension(420, 300));
        graphScrollPane.doLayout();
        javax.swing.JViewport viewport = graphScrollPane.getViewport();
        viewport.setViewPosition(new Point(80, 60));
        org.mockito.Mockito.clearInvocations(fixture.handle);

        viewport.setViewPosition(new Point(120, 60));

        ArgumentCaptor<GraphCommand> commands = ArgumentCaptor.forClass(GraphCommand.class);
        verify(fixture.handle, org.mockito.Mockito.times(1)).execute(commands.capture());
        assertThat(commands.getValue()).isInstanceOf(GraphCommands.Viewport.class);
        GraphCommands.Viewport command = (GraphCommands.Viewport) commands.getValue();
        GraphViewport visible = model.canvas().visibleViewport();
        GraphViewport anchor = model.canvas().viewport();
        assertThat(command.viewport().centerX()).isEqualTo(visible.centerX());
        assertThat(command.viewport().centerY()).isEqualTo(visible.centerY());
        assertThat(command.viewport().zoom()).isEqualTo(visible.zoom());
        assertThat(visible.centerX()).isNotEqualTo(anchor.centerX());
        assertThat(visible.centerY()).isNotEqualTo(anchor.centerY());
        model.close();
    }

    @Test
    public void persistsCanvasDragAsTheVisibleViewportOnce() {
        assertCanvasGesturePersistsVisibleViewportOnce(canvas -> {
            int centerX = canvas.getWidth() / 2;
            int centerY = canvas.getHeight() / 2;
            canvas.dispatchEvent(mouse(canvas, MouseEvent.MOUSE_PRESSED, centerX, centerY,
                InputEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1));
            canvas.dispatchEvent(mouse(canvas, MouseEvent.MOUSE_DRAGGED, centerX + 15, centerY,
                InputEvent.BUTTON1_DOWN_MASK, MouseEvent.NOBUTTON));
            canvas.dispatchEvent(mouse(canvas, MouseEvent.MOUSE_DRAGGED, centerX + 30, centerY,
                InputEvent.BUTTON1_DOWN_MASK, MouseEvent.NOBUTTON));
            canvas.dispatchEvent(mouse(canvas, MouseEvent.MOUSE_RELEASED, centerX + 30, centerY,
                0, MouseEvent.BUTTON1));
        });
    }

    @Test
    public void persistsCanvasArrowPanAsTheVisibleViewportOnce() {
        assertCanvasGesturePersistsVisibleViewportOnce(canvas -> {
            KeyEvent event = new KeyEvent(canvas, KeyEvent.KEY_PRESSED, System.currentTimeMillis(),
                0, KeyEvent.VK_RIGHT, KeyEvent.CHAR_UNDEFINED);
            for (KeyListener listener : canvas.getKeyListeners()) {
                listener.keyPressed(event);
            }
        });
    }

    @Test
    public void persistsCanvasWheelZoomAsTheVisibleViewportOnce() {
        assertCanvasGesturePersistsVisibleViewportOnce(canvas -> canvas.dispatchEvent(
            new MouseWheelEvent(canvas, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0,
                canvas.getWidth() / 2, canvas.getHeight() / 2, 0, false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, -1)));
    }

    @Test
    public void preservesLoadedViewportUnknownXmlThroughToolbarZoomFitAndReset() {
        UnknownXml retained = UnknownXml.attribute(UnknownXml.Owner.RECORD,
            new QName("urn:freeplane:test", "retained"), "viewport-value");
        List<UnknownXml> unknownXml = Collections.singletonList(retained);
        Fixture fixture = fixture(Viewport.of(125.0, -75.0, 2.0, unknownXml),
            wideNodeState(ACTIVE_ID),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        scrollViewport(model, new Dimension(420, 300));
        awaitDeferredViewportClamp();
        org.mockito.Mockito.clearInvocations(fixture.handle);

        model.toolbar().zoomInButton().doClick();
        model.toolbar().fitGraphButton().doClick();
        model.toolbar().resetZoomButton().doClick();
        awaitDeferredViewportClamp();

        ArgumentCaptor<GraphCommand> commands = ArgumentCaptor.forClass(GraphCommand.class);
        verify(fixture.handle, org.mockito.Mockito.times(3)).execute(commands.capture());
        for (GraphCommand command : commands.getAllValues()) {
            assertThat(command).isInstanceOf(GraphCommands.Viewport.class);
            GraphCommands.Viewport viewportCommand = (GraphCommands.Viewport) command;
            assertThat(viewportCommand.viewport().unknownXml()).containsExactly(retained);
        }
        model.close();
    }

    @Test
    public void suppressesDeferredSurfaceResizeViewportPersistenceAfterExternalScroll() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            wideNodeState(ACTIVE_ID, 500.0, 500.0),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        JViewport viewport = scrollViewport(model, new Dimension(1000, 600));
        int widthBeforeResize = model.canvas().getWidth();
        Point externalPosition = scrollOffCenter(model, viewport);
        awaitDeferredViewportClamp();
        List<Point> viewportPositions = new ArrayList<Point>();
        final boolean[] deferredSetupQueued = new boolean[] {false};
        final int[] maximumX = new int[] {-1};
        viewport.addChangeListener(event -> {
            viewportPositions.add(viewport.getViewPosition());
            if (!deferredSetupQueued[0]) {
                deferredSetupQueued[0] = true;
                // Run after resize compensation but before the canvas's deferred clamp.
                javax.swing.SwingUtilities.invokeLater(() -> {
                    Dimension viewSize = viewport.getView().getSize();
                    Dimension extent = viewport.getExtentSize();
                    maximumX[0] = Math.max(0, viewSize.width - extent.width);
                    viewport.setViewPosition(new Point(maximumX[0] + 1,
                        viewport.getViewPosition().y));
                });
            }
        });
        org.mockito.Mockito.clearInvocations(fixture.handle);

        model.acceptCanvasState(wideNodeState(ACTIVE_ID, 460.0, 460.0));
        awaitDeferredViewportClamp();

        assertThat(deferredSetupQueued[0]).isTrue();
        assertThat(model.canvas().getWidth()).isLessThan(widthBeforeResize);
        assertThat(maximumX[0]).isGreaterThan(0);
        assertThat(viewport.getViewPosition().x).isEqualTo(maximumX[0]);
        assertThat(viewport.getViewPosition()).isNotEqualTo(externalPosition);
        assertThat(viewportPositions).anyMatch(position -> position.x == maximumX[0] + 1);
        assertThat(viewportPositions).anyMatch(position -> position.x == maximumX[0]);
        assertViewportCommandCount(fixture, model, 0);
        model.close();
    }

    @Test
    public void suppressesZoomViewportPersistenceAfterExternalScroll() {
        assertViewportCommandCountAfterExternalScroll(1, wideNodeState(ACTIVE_ID),
            model -> model.toolbar().zoomInButton().doClick());
    }

    @Test
    public void suppressesFitViewportPersistenceAfterExternalScroll() {
        assertViewportCommandCountAfterExternalScroll(1, wideNodeState(ACTIVE_ID),
            model -> model.toolbar().fitGraphButton().doClick());
    }

    @Test
    public void suppressesResetViewportPersistenceAfterExternalScroll() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 2.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        JViewport viewport = scrollViewport(model, new Dimension(420, 300));
        assertThat(model.canvas().viewport().zoom()).isEqualTo(2.0);
        int widthBeforeReset = model.canvas().getWidth();
        Point externalPosition = scrollOffCenter(model, viewport);
        awaitDeferredViewportClamp();
        final boolean[] callbackMovedViewport = new boolean[] {false};
        org.mockito.Mockito.clearInvocations(fixture.handle);
        // Command handling can synchronously refresh the scroll position during reset publication.
        when(fixture.handle.execute(any(GraphCommand.class))).thenAnswer(invocation -> {
            if (!callbackMovedViewport[0]) {
                callbackMovedViewport[0] = true;
                Point finalPosition = viewport.getViewPosition();
                int maximumX = Math.max(0, model.canvas().getWidth() - viewport.getExtentSize().width);
                int alternateX = finalPosition.x < maximumX ? finalPosition.x + 1 : finalPosition.x - 1;
                viewport.setViewPosition(new Point(alternateX, finalPosition.y));
                viewport.setViewPosition(finalPosition);
            }
            return null;
        });

        model.toolbar().resetZoomButton().doClick();
        awaitDeferredViewportClamp();

        assertThat(model.canvas().viewport().centerX()).isEqualTo(0.0);
        assertThat(model.canvas().viewport().centerY()).isEqualTo(0.0);
        assertThat(model.canvas().viewport().zoom()).isEqualTo(1.0);
        assertThat(model.canvas().getWidth()).isEqualTo(widthBeforeReset);
        assertThat(viewport.getViewPosition()).isNotEqualTo(externalPosition);
        assertThat(callbackMovedViewport[0]).isTrue();
        assertViewportCommandCount(fixture, model, 1);
        model.close();
    }
    @Test
    public void routesApplicationOpenToTheApplicationControllerAndSessionActionsToTheHandle() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false,
            WorkspaceSessionStatus.of(false, true, true, false, Collections.<MapReferenceId>emptySet(),
                java.util.Optional.<org.freeplane.plugin.graph.command.MapUndoTarget>empty()));
        GraphWorkspaceWindowModel model = fixture.model();

        model.toolbar().openButton().doClick();
        model.toolbar().saveButton().doClick();
        model.toolbar().undoButton().doClick();
        model.toolbar().redoButton().doClick();
        model.toolbar().zoomInButton().doClick();
        model.toolbar().resetZoomButton().doClick();
        model.settingsPanel().showArrowheads().doClick();

        verify(fixture.applicationController).open(OPEN_PATH);
        ArgumentCaptor<GraphCommand> commands = ArgumentCaptor.forClass(GraphCommand.class);
        verify(fixture.handle, org.mockito.Mockito.atLeast(6)).execute(commands.capture());
        assertThat(commands.getAllValues()).extracting("class").contains(
            GraphCommands.Save.class, GraphCommands.UndoWorkspace.class, GraphCommands.RedoWorkspace.class,
            GraphCommands.Viewport.class, GraphCommands.Display.class);
        model.close();
    }

    @Test
    public void publishesOneLocalizedMessageForRejectedCommand() {
        final List<String> messages = new ArrayList<String>();
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        when(fixture.handle.execute(any(GraphCommand.class))).thenReturn(commandResult(
            WorkspaceTransition.rejected(emptyDocument(), "graph_workspace.test.rejected", "argument")));
        GraphWorkspaceWindowModel model = fixture.model(messages::add);

        model.execute(GraphCommands.save());

        assertThat(messages).containsExactly("graph_workspace.test.rejected[argument]");
        model.close();
    }

    @Test
    public void publishesOneLocalizedMessageForNoOpCommand() {
        final List<String> messages = new ArrayList<String>();
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        when(fixture.handle.execute(any(GraphCommand.class))).thenReturn(commandResult(
            WorkspaceTransition.noOp(emptyDocument(), "graph_workspace.test.no_op", "argument")));
        GraphWorkspaceWindowModel model = fixture.model(messages::add);

        model.execute(GraphCommands.save());

        assertThat(messages).containsExactly("graph_workspace.test.no_op[argument]");
        model.close();
    }

    @Test
    public void doesNotPublishMessageForAppliedCommand() {
        final List<String> messages = new ArrayList<String>();
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        when(fixture.handle.execute(any(GraphCommand.class))).thenReturn(commandResult(
            WorkspaceTransition.applied(emptyDocument(), "graph_workspace.test.applied", "argument")));
        GraphWorkspaceWindowModel model = fixture.model(messages::add);

        model.execute(GraphCommands.save());

        assertThat(messages).isEmpty();
        model.close();
    }

    @Test
    public void revealsSourceNodeSilentlyOnSingleClickIntentWhileDoubleClickOpenStillReportsFailures() {
        final List<String> messages = new ArrayList<String>();
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            enclosureState(ACTIVE_ID), Collections.singletonList(registration(ACTIVE_ID, "Active",
                MapAvailability.AVAILABLE)), false);
        when(fixture.handle.execute(any(GraphCommand.class))).thenReturn(commandResult(
            WorkspaceTransition.rejected(emptyDocument(), "graph_workspace.test.rejected")));
        GraphWorkspaceWindowModel model = fixture.model(messages::add);
        final SourceNodeKey source = SourceNodeKey.transientPath(ACTIVE_ID, Collections.emptyList());
        final ProjectedEndpointKey endpoint = ProjectedEndpointKey.ofEnclosure(EnclosureKey.of(source));

        model.acceptIntent(new GraphIntent.RevealSourceNode(endpoint));

        ArgumentCaptor<GraphCommand> commands = ArgumentCaptor.forClass(GraphCommand.class);
        verify(fixture.handle).execute(commands.capture());
        assertThat(commands.getValue()).isInstanceOf(GraphCommands.OpenSource.class);
        assertThat(((GraphCommands.OpenSource) commands.getValue()).source()).isEqualTo(source);
        assertThat(messages).isEmpty();

        model.acceptIntent(new GraphIntent.OpenSourceNode(endpoint));

        assertThat(messages).containsExactly("graph_workspace.test.rejected[]");
        model.close();
    }

    @Test
    public void exposesAllMapRowStatesWithProjectedCountsAndEmitsSessionCommandsOnCanvasUpdates() {
        MapReferenceId loadingId = id(2L);
        MapReferenceId missingId = id(3L);
        MapReferenceId retryId = id(4L);
        MapReferenceId inactiveId = id(5L);
        List<GraphWorkspaceViewBinding.MapRegistration> registrations = Arrays.asList(
            registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE),
            registration(loadingId, "Loading", MapAvailability.LOADING),
            registration(missingId, "Missing", MapAvailability.MISSING),
            registration(retryId, "Retry", MapAvailability.UNREADABLE),
            registration(inactiveId, "Inactive", MapAvailability.INACTIVE));
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), registrations, false);
        GraphWorkspaceWindowModel model = fixture.model();

        model.acceptCanvasState(nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)));

        assertThat(model.mapList().rows()).extracting(MapListPanel.MapRow::state).containsExactly(
            MapListPanel.RowState.ACTIVE, MapListPanel.RowState.LOADING, MapListPanel.RowState.MISSING,
            MapListPanel.RowState.RETRYABLE, MapListPanel.RowState.INACTIVE);
        assertThat(model.mapList().rows().get(0).projectedNodeCount()).isEqualTo(1);
        assertThat(model.mapList().rows().get(3).projectedNodeCount()).isEqualTo(0);

        model.mapList().selectMap(missingId);
        model.mapList().locateButton().doClick();
        model.mapList().selectMap(retryId);
        assertThat(model.mapList().selectedRow().mapReferenceId()).isEqualTo(retryId);
        assertThat(model.mapList().rows().get(3).selected()).isTrue();
        model.mapList().retryButton().doClick();

        ArgumentCaptor<GraphCommand> commands = ArgumentCaptor.forClass(GraphCommand.class);
        verify(fixture.handle, org.mockito.Mockito.times(2)).execute(commands.capture());
        assertThat(commands.getAllValues()).extracting("class").containsExactly(
            GraphCommands.LocateMap.class, GraphCommands.RetryMap.class);
        model.close();
    }

    @Test
    public void routesMapActionsToTheWorkspaceHandle() {
        MapReferenceId activeId = id(21L);
        MapReferenceId missingId = id(22L);
        MapReferenceId retryId = id(23L);
        GraphWorkspaceHandle handle = mock(GraphWorkspaceHandle.class);
        MapListPanel panel = new MapListPanel(handle, () -> Paths.get("/tmp/map.mm"));
        panel.setRows(Arrays.asList(
            MapListPanel.MapRow.of(activeId, "Active", MapListPanel.RowState.ACTIVE, 2, false),
            MapListPanel.MapRow.of(missingId, "Missing", MapListPanel.RowState.MISSING, 0, false),
            MapListPanel.MapRow.of(retryId, "Retry", MapListPanel.RowState.RETRYABLE, 1, true)));

        panel.addButton().doClick();
        panel.selectMap(missingId);
        panel.locateButton().doClick();
        panel.selectMap(retryId);
        panel.retryButton().doClick();
        panel.selectMap(activeId);
        panel.removeButton().doClick();

        ArgumentCaptor<GraphCommand> commands = ArgumentCaptor.forClass(GraphCommand.class);
        verify(handle, org.mockito.Mockito.times(4)).execute(commands.capture());
        assertThat(commands.getAllValues()).extracting("class").containsExactly(
            GraphCommands.AddMap.class, GraphCommands.LocateMap.class,
            GraphCommands.RetryMap.class, GraphCommands.RemoveMap.class);
    }

    @Test
    public void mapRowRecordsAndExposesMapPartition() {
        MapReferenceId mapId = id(31L);
        MapListPanel.MapRow row = MapListPanel.MapRow.of(
            mapId, "My Map", MapListPanel.RowState.ACTIVE, MapPartition.ACTIVE, 5, true);

        assertThat(row.mapReferenceId()).isEqualTo(mapId);
        assertThat(row.displayName()).isEqualTo("My Map");
        assertThat(row.state()).isEqualTo(MapListPanel.RowState.ACTIVE);
        assertThat(row.partition()).isEqualTo(MapPartition.ACTIVE);
        assertThat(row.projectedNodeCount()).isEqualTo(5);
        assertThat(row.selected()).isTrue();
    }

    @Test
    public void mapRowOfWithPartitionSetsPartitionProperly() {
        MapReferenceId mapId = id(32L);
        MapListPanel.MapRow row = MapListPanel.MapRow.of(
            mapId, "Inactive Map", MapListPanel.RowState.INACTIVE, MapPartition.INACTIVE, 0, false);

        assertThat(row.partition()).isEqualTo(MapPartition.INACTIVE);
        assertThat(row.state()).isEqualTo(MapListPanel.RowState.INACTIVE);
    }

    @Test
    public void backwardCompatibleMapRowOfDerivesPartitionFromState() {
        MapReferenceId mapId = id(33L);
        MapListPanel.MapRow activeRow = MapListPanel.MapRow.of(
            mapId, "Active Map", MapListPanel.RowState.ACTIVE, 3, false);
        MapListPanel.MapRow inactiveRow = MapListPanel.MapRow.of(
            mapId, "Inactive Map", MapListPanel.RowState.INACTIVE, 0, false);
        MapListPanel.MapRow missingRow = MapListPanel.MapRow.of(
            mapId, "Missing Map", MapListPanel.RowState.MISSING, 0, false);

        assertThat(activeRow.partition()).isEqualTo(MapPartition.ACTIVE);
        assertThat(inactiveRow.partition()).isEqualTo(MapPartition.INACTIVE);
        assertThat(missingRow.partition()).isEqualTo(MapPartition.ACTIVE);
    }

    @Test
    public void mapRowWithSelectedPreservesPartition() {
        MapReferenceId mapId = id(34L);
        MapListPanel.MapRow row = MapListPanel.MapRow.of(
            mapId, "My Map", MapListPanel.RowState.READ_ONLY, MapPartition.INACTIVE, 0, false);
        MapListPanel.MapRow updated = row.withSelected(true);

        assertThat(updated.selected()).isTrue();
        assertThat(updated.partition()).isEqualTo(MapPartition.INACTIVE);
        assertThat(updated.state()).isEqualTo(MapListPanel.RowState.READ_ONLY);
    }

    @Test
    public void disablesMutatingControlsForReadOnlySessions() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), true);
        GraphWorkspaceWindowModel model = fixture.model();

        assertThat(model.toolbar().saveButton().isEnabled()).isFalse();
        assertThat(model.toolbar().pinButton().isEnabled()).isFalse();
        assertThat(model.toolbar().pinButton().getText()).isEqualTo("graph_workspace.action.pin");
        assertThat(model.mapList().removeButton().isEnabled()).isFalse();
        assertThat(model.mapList().addButton().isEnabled()).isFalse();
        assertThat(model.settingsPanel().isReadOnly()).isTrue();
        assertThat(model.mapList().rows().get(0).state()).isEqualTo(MapListPanel.RowState.READ_ONLY);
        model.close();
    }

    @Test
    public void startsWithPersistedPresentationValuesAndAppliesThePersistedThemeBeforePainting() {
        DisplaySettings settings = DisplaySettings.of(false, CanvasTheme.DARK, false, false,
            DisplaySettings.DEFAULT_MAP_SIDEBAR_WIDTH, false, emptyUnknownXml());
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false,
            WorkspaceSessionStatus.empty(), presentation(settings, ACTIVE_ID));
        GraphWorkspaceWindowModel model = fixture.model();

        assertThat(model.settingsPanel().showArrowheads().isSelected()).isFalse();
        assertThat(model.settingsPanel().canvasTheme().getSelectedItem()).isEqualTo(CanvasTheme.DARK);
        assertThat(model.settingsPanel().rememberViewport().isSelected()).isFalse();
        assertThat(model.settingsPanel().dimUnrelated().isSelected()).isFalse();
        BufferedImage image = paintCanvas(model.canvas());
        assertThat(image.getRGB(0, 0)).isEqualTo(GraphTheme.resolve(CanvasTheme.DARK,
            palette(ACTIVE_ID)).background().getRGB());
        model.close();
    }

    @Test
    public void refreshesCanvasPresentationAfterADisplayCommandWithoutResettingOtherSettings() {
        DisplaySettings initial = DisplaySettings.of(true, CanvasTheme.LIGHT, true, true,
            DisplaySettings.DEFAULT_MAP_SIDEBAR_WIDTH, false, emptyUnknownXml());
        DisplaySettings changed = DisplaySettings.of(false, CanvasTheme.DARK, true, false,
            DisplaySettings.DEFAULT_MAP_SIDEBAR_WIDTH, false, emptyUnknownXml());
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false,
            WorkspaceSessionStatus.empty(), presentation(initial, ACTIVE_ID));
        GraphWorkspaceWindowModel model = fixture.model();
        when(fixture.binding.currentPresentation()).thenReturn(presentation(changed, ACTIVE_ID));

        model.execute(GraphCommands.display(changed));

        assertThat(model.settingsPanel().showArrowheads().isSelected()).isFalse();
        assertThat(model.settingsPanel().canvasTheme().getSelectedItem()).isEqualTo(CanvasTheme.DARK);
        assertThat(model.settingsPanel().rememberViewport().isSelected()).isTrue();
        assertThat(model.settingsPanel().dimUnrelated().isSelected()).isFalse();
        assertThat(paintCanvas(model.canvas()).getRGB(0, 0)).isEqualTo(GraphTheme.resolve(CanvasTheme.DARK,
            palette(ACTIVE_ID)).background().getRGB());
        verify(fixture.handle).execute(any(GraphCommand.class));
        model.close();
    }

    @Test
    public void paintsAVisibleEnclosureThroughTheWindowModel() {
        DisplaySettings settings = DisplaySettings.defaults();
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            enclosureState(ACTIVE_ID),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false,
            WorkspaceSessionStatus.empty(), presentation(settings, ACTIVE_ID));
        GraphWorkspaceWindowModel model = fixture.model();

        BufferedImage image = paintCanvas(model.canvas());

        assertThat(nonBackgroundPixels(image, image.getRGB(0, 0))).isGreaterThan(0);
        model.close();
    }

    @Test
    public void fitsWhenRememberViewportIsDisabledEvenIfThePersistedViewportOverlaps() {
        DisplaySettings settings = DisplaySettings.of(true, CanvasTheme.LIGHT, false, true,
            DisplaySettings.DEFAULT_MAP_SIDEBAR_WIDTH, false, emptyUnknownXml());
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false,
            WorkspaceSessionStatus.empty(), presentation(settings, ACTIVE_ID));
        GraphWorkspaceWindowModel model = fixture.model();

        assertThat(model.canvas().viewport().zoom()).isGreaterThan(1.0);
        model.close();
    }

    @Test
    public void updatesMapsMenuEnablementForActiveAndInactivePartitions() {
        MapReferenceId activeId = id(101L);
        MapReferenceId inactiveId = id(102L);
        List<GraphWorkspaceViewBinding.MapRegistration> registrations = Arrays.asList(
            registration(activeId, "Active", MapAvailability.AVAILABLE),
            registration(inactiveId, "Inactive", MapAvailability.INACTIVE));
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), registrations, false);
        GraphWorkspaceWindowModel model = fixture.model();

        // Select Active map
        model.mapList().selectMap(activeId);
        assertThat(menuItem(model, "add-map").isEnabled()).isTrue();
        assertThat(menuItem(model, "deactivate-map").isEnabled()).isTrue();
        assertThat(menuItem(model, "reactivate-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "delete-map").isEnabled()).isFalse();

        // Select Inactive map
        model.mapList().selectMap(inactiveId);
        assertThat(menuItem(model, "add-map").isEnabled()).isTrue();
        assertThat(menuItem(model, "deactivate-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "reactivate-map").isEnabled()).isTrue();
        assertThat(menuItem(model, "delete-map").isEnabled()).isTrue();

        model.close();
    }

    @Test
    public void preservesInactivePartitionInReadOnlyMode() {
        MapReferenceId inactiveId = id(103L);
        List<GraphWorkspaceViewBinding.MapRegistration> registrations = Collections.singletonList(
            registration(inactiveId, "Inactive", MapAvailability.INACTIVE));
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), registrations, true);
        GraphWorkspaceWindowModel model = fixture.model();

        MapListPanel.MapRow row = model.mapList().rows().get(0);
        assertThat(row.partition()).isEqualTo(MapPartition.INACTIVE);
        assertThat(row.state()).isEqualTo(MapListPanel.RowState.READ_ONLY);
        assertThat(model.mapList().addButton().isEnabled()).isFalse();
        assertThat(model.mapList().removeButton().isEnabled()).isFalse();

        model.close();
    }

    @Test
    public void keepsMenuEnablementAlignedWithReadOnlyAndIndependentHistoryRules() {
        WorkspaceSessionStatus status = WorkspaceSessionStatus.of(true, true, true, false,
            Collections.<MapReferenceId>emptySet(),
            java.util.Optional.of(new org.freeplane.plugin.graph.command.MapUndoTarget(ACTIVE_ID,
                "Active", true)));
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false,
            status, presentation(DisplaySettings.defaults(), ACTIVE_ID));
        GraphWorkspaceWindowModel model = fixture.model();
        model.mapList().selectMap(ACTIVE_ID);

        assertThat(menuItem(model, "save").isEnabled()).isTrue();
        assertThat(menuItem(model, "save-as").isEnabled()).isTrue();
        assertThat(menuItem(model, "add-map").isEnabled()).isTrue();
        assertThat(menuItem(model, "deactivate-map").isEnabled()).isTrue();
        assertThat(menuItem(model, "fit-graph").isEnabled()).isTrue();
        assertThat(menuItem(model, "reset-zoom").isEnabled()).isTrue();
        assertThat(menuItem(model, "settings").isEnabled()).isTrue();
        assertThat(menuItem(model, "undo").isEnabled()).isTrue();
        assertThat(menuItem(model, "redo").isEnabled()).isTrue();
        assertThat(menuItem(model, "undo-source-map").isEnabled()).isTrue();

        model.setReadOnly(true);

        assertThat(menuItem(model, "save").isEnabled()).isFalse();
        assertThat(menuItem(model, "save-as").isEnabled()).isFalse();
        assertThat(menuItem(model, "add-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "deactivate-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "reactivate-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "delete-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "retry-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "fit-graph").isEnabled()).isTrue();
        assertThat(menuItem(model, "reset-zoom").isEnabled()).isTrue();
        assertThat(menuItem(model, "settings").isEnabled()).isTrue();
        assertThat(model.toolbar().settingsButton().isEnabled()).isTrue();
        assertThat(menuItem(model, "undo").isEnabled()).isFalse();
        assertThat(menuItem(model, "redo").isEnabled()).isFalse();
        assertThat(menuItem(model, "undo-source-map").isEnabled()).isFalse();
        model.close();
    }

    @Test
    public void fitsAnOutOfRangeStateDeliveredBeforeInitialLayoutExactlyOnce() {
        Viewport persisted = Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml());
        Fixture fixture = fixture(persisted, emptyState(),
            Collections.<GraphWorkspaceViewBinding.MapRegistration>emptyList(), false);
        GraphWorkspaceWindowModel model = fixture.modelWithoutLayout();

        model.acceptCanvasState(nodeState(ACTIVE_ID, LayoutPoint.of(10_000.0, 10_000.0)));
        assertThat(model.canvas().viewport().centerX()).isEqualTo(0.0);
        model.completeInitialLayout();
        assertThat(model.canvas().viewport().centerX()).isEqualTo(10_000.0);
        assertThat(model.canvas().viewport().centerY()).isEqualTo(10_000.0);
        assertThat(model.canvas().viewport().zoom()).isGreaterThan(1.0);

        model.acceptCanvasState(nodeState(ACTIVE_ID, LayoutPoint.of(20_000.0, 20_000.0)));
        assertThat(model.canvas().viewport().centerX()).isEqualTo(10_000.0);
        assertThat(model.canvas().viewport().centerY()).isEqualTo(10_000.0);
        model.close();
    }

    @Test
    public void appliesThePersistedViewportAndFitsWhenTheFirstNonEmptyCanvasStateDoesNotOverlap() {
        Viewport persisted = Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml());
        Fixture fixture = fixture(persisted, emptyState(), Collections.<GraphWorkspaceViewBinding.MapRegistration>emptyList(),
            false);
        GraphWorkspaceWindowModel model = fixture.model();

        assertThat(model.canvas().viewport().centerX()).isEqualTo(0.0);
        model.acceptCanvasState(nodeState(ACTIVE_ID, LayoutPoint.of(10_000.0, 10_000.0)));

        assertThat(model.canvas().viewport().centerX()).isEqualTo(10_000.0);
        assertThat(model.canvas().viewport().centerY()).isEqualTo(10_000.0);
        assertThat(model.canvas().viewport().zoom()).isGreaterThan(1.0);
        model.acceptCanvasState(nodeState(ACTIVE_ID, LayoutPoint.of(20_000.0, 20_000.0)));
        assertThat(model.canvas().viewport().centerX()).isEqualTo(10_000.0);
        assertThat(model.canvas().viewport().centerY()).isEqualTo(10_000.0);
        model.close();
    }

    @Test
    public void preservesThePersistedViewportWhenTheFirstNonEmptyCanvasStateOverlaps() {
        Viewport persisted = Viewport.of(10_000.0, 10_000.0, 1.0, emptyUnknownXml());
        Fixture fixture = fixture(persisted, emptyState(), Collections.<GraphWorkspaceViewBinding.MapRegistration>emptyList(),
            false);
        GraphWorkspaceWindowModel model = fixture.model();

        model.acceptCanvasState(nodeState(ACTIVE_ID, LayoutPoint.of(10_000.0, 10_000.0)));

        assertThat(model.canvas().viewport().centerX()).isEqualTo(10_000.0);
        assertThat(model.canvas().viewport().centerY()).isEqualTo(10_000.0);
        assertThat(model.canvas().viewport().zoom()).isEqualTo(1.0);
        model.close();
    }

    @Test
    public void closesTheHeadlessShellWithoutShowingIt() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();

        model.close();
        model.close();

        verify(fixture.registration).close();
        verify(fixture.sessionRegistration).close();
    }

    @Test
    public void factoryDoesNotPublishOrShowAViewBeforeConstructionCompletes() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel resourceScope = fixture.model();
        SwingGraphWorkspaceViewFactory factory = new SwingGraphWorkspaceViewFactory(
            fixture.applicationController, () -> OPEN_PATH);

        GraphWorkspaceView view = factory.create(fixture.handle, fixture.binding, fixture.closeController);

        if (view instanceof GraphWorkspaceWindow) {
            assertThat(((GraphWorkspaceWindow) view).isVisible()).isFalse();
        }
        else {
            assertThat(view).isInstanceOf(HeadlessGraphWorkspaceView.class);
            assertThat(((HeadlessGraphWorkspaceView) view).isVisible()).isFalse();
        }
        view.close();
        resourceScope.close();
    }

    @Test
    public void showsAWorkspaceWindowAfterOpeningIt() {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel resourceScope = fixture.model();
        GraphWorkspaceView view = new SwingGraphWorkspaceViewFactory(fixture.applicationController, () -> OPEN_PATH)
            .create(fixture.handle, fixture.binding, fixture.closeController);
        assertThat(view).isInstanceOf(GraphWorkspaceWindow.class);
        GraphWorkspaceWindow window = (GraphWorkspaceWindow) view;

        try {
            window.show();

            assertThat(window.isVisible()).isTrue();
        }
        finally {
            window.close();
            resourceScope.close();
        }
    }

    @Test
    public void showsAHiddenWorkspaceWindowWhenFocusingIt() {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel resourceScope = fixture.model();
        GraphWorkspaceView view = new SwingGraphWorkspaceViewFactory(fixture.applicationController, () -> OPEN_PATH)
            .create(fixture.handle, fixture.binding, fixture.closeController);
        assertThat(view).isInstanceOf(GraphWorkspaceWindow.class);
        GraphWorkspaceWindow window = (GraphWorkspaceWindow) view;

        try {
            assertThat(window.isVisible()).isFalse();
            window.focus();

            assertThat(window.isVisible()).isTrue();
        }
        finally {
            window.close();
            resourceScope.close();
        }
    }

    @Test
    public void installsPopupFrameResynchronizationOnTheWorkspaceWindow() {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel resourceScope = fixture.model();
        GraphWorkspaceView view = new SwingGraphWorkspaceViewFactory(fixture.applicationController, () -> OPEN_PATH)
            .create(fixture.handle, fixture.binding, fixture.closeController);
        assertThat(view).isInstanceOf(GraphWorkspaceWindow.class);
        GraphWorkspaceWindow window = (GraphWorkspaceWindow) view;

        try {
            // The X11/KWin coordinate resynchronizer must be installed before the
            // window can be shown so that popup menus stay selectable after
            // maximize, unmaximize, or arbitrary window manager moves.
            assertThat(window.getWindowListeners()).anyMatch(listener -> listener instanceof FrameResynchronizer);
            assertThat(window.getWindowStateListeners()).anyMatch(listener -> listener instanceof FrameResynchronizer);
            assertThat(window.getComponentListeners()).anyMatch(listener -> listener instanceof FrameResynchronizer);
        }
        finally {
            window.close();
            resourceScope.close();
        }
    }

    @Test
    public void f11MakesWindowUndecoratedAndFillsCurrentScreen() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        try (DisplayFixture f = new DisplayFixture()) {
            final GraphViewport before = f.view();
            f.pressF11();
            f.awaitActive(true);
            f.await(f::hasWorkAreaCoverage);
            f.onEdt(() -> {
                assertThat(f.window.isUndecorated()).isTrue();
                assertThat(f.menu.isSelected()).isTrue();
                assertThat(f.chrome()).containsOnly(false);
            });
            f.assertWorkAreaCoverage();
            f.assertView(before);
            f.assertNoCommands();
        }
    }

    @Test
    public void f11RestoresBoundsAndExtendedStateFromNormalWindow() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        try (DisplayFixture f = new DisplayFixture()) {
            f.assertRoundTrip();
        }
    }

    @Test
    public void f11RestoresBoundsAndExtendedStateFromMaximizedWindow() throws Exception {
        final String configured = System.getenv("FREEPLANE_GRAPH_TEST_WINDOW_MANAGER");
        final String mode = configured == null ? "required" : configured;
        assertThat(mode).as("FREEPLANE_GRAPH_TEST_WINDOW_MANAGER").isIn("required", "optional");
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        requireWindowManager(mode);
        final java.awt.Rectangle ewmh = requiredWorkArea();
        try (DisplayFixture f = new DisplayFixture()) {
            final java.awt.Rectangle awt = f.edt(() -> DisplayFixture.workArea(f.window.getGraphicsConfiguration()));
            assertWorkAreaAgreement(awt, ewmh);
            f.onEdt(() -> f.window.setExtendedState(java.awt.Frame.MAXIMIZED_BOTH));
            f.await(() -> f.window.getExtendedState() == java.awt.Frame.MAXIMIZED_BOTH);
            f.requireMaximizedDuringStable = true;
            try {
                f.awaitFrameStable(2100);
            }
            finally {
                f.requireMaximizedDuringStable = false;
            }
            f.assertMaximizedWorkAreaCoverage();
            f.checkpoint = f.edt(() -> f.commands.size());
            f.assertRoundTrip(true);
        }
    }

    private static String queryRootProperty(final String property) throws Exception {
        final String display = System.getenv("DISPLAY");
        assertThat(display).as("current DISPLAY").isNotNull().isNotEmpty();
        final Process process = new ProcessBuilder("xprop", "-display", display, "-root", property)
            .redirectErrorStream(true).start();
        try {
            assertThat(process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS))
                .as("bounded xprop query for %s", property).isTrue();
            final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            final byte[] buffer = new byte[1024];
            int count;
            while ((count = process.getInputStream().read(buffer)) != -1) {
                bytes.write(buffer, 0, count);
            }
            final String text = new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8).trim();
            System.out.println("EWMH DISPLAY=" + display + " property=" + property + " output=" + text);
            assertThat(process.exitValue()).as("xprop exit: %s", text).isZero();
            return text;
        }
        finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                assertThat(process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS))
                    .as("xprop cleanup").isTrue();
            }
        }
    }

    private static void requireWindowManager(final String mode) throws Exception {
        final String supporting = queryRootProperty("_NET_SUPPORTING_WM_CHECK");
        final boolean absent = supporting.matches("_NET_SUPPORTING_WM_CHECK:\\s+"
            + "(?:not found\\.|no such atom on any window\\.)");
        if (absent) {
            queryRootProperty("_NET_WORKAREA"); // Same-display absent-path receipt, before maximization.
            if ("optional".equals(mode)) {
                Assume.assumeTrue("WM_ABSENT_SAME_DISPLAY:" + System.getenv("DISPLAY"), false);
            }
            throw new AssertionError("Required WM absent on " + System.getenv("DISPLAY"));
        }
        assertThat(supporting).matches("_NET_SUPPORTING_WM_CHECK\\(WINDOW\\): window id # 0x[0-9a-fA-F]+");
        final String xid = supporting.substring(supporting.lastIndexOf(' ') + 1);
        assertThat(Long.parseLong(xid.substring(2), 16)).as("nonzero WM support XID").isNotZero();
    }

    private static java.awt.Rectangle requiredWorkArea() throws Exception {
        final String desktop = queryRootProperty("_NET_CURRENT_DESKTOP");
        assertThat(desktop).matches("_NET_CURRENT_DESKTOP\\(CARDINAL\\) = [0-9]+");
        final int index = Integer.parseInt(desktop.substring(desktop.indexOf("= ") + 2));
        final String work = queryRootProperty("_NET_WORKAREA");
        assertThat(work).startsWith("_NET_WORKAREA(CARDINAL) = ");
        final String[] values = work.substring(work.indexOf("= ") + 2).split(",\\s*");
        assertThat(values.length % 4).isZero();
        assertThat(index).isGreaterThanOrEqualTo(0).isLessThan(values.length / 4);
        final long[] rectangle = new long[4];
        for (int i = 0; i < values.length; i++) {
            assertThat(values[i]).matches("[0-9]+");
            final long value = Long.parseLong(values[i]);
            assertThat(value).isBetween(0L, 4294967295L);
            if (i / 4 == index) {
                rectangle[i % 4] = value;
            }
        }
        assertThat(rectangle[2]).isBetween(1L, (long) Integer.MAX_VALUE);
        assertThat(rectangle[3]).isBetween(1L, (long) Integer.MAX_VALUE);
        return new java.awt.Rectangle((int) rectangle[0], (int) rectangle[1],
            (int) rectangle[2], (int) rectangle[3]);
    }

    private static void assertWorkAreaAgreement(final java.awt.Rectangle awt,
            final java.awt.Rectangle ewmh) {
        System.out.println("EWMH AWT=" + awt + " NET_WORKAREA=" + ewmh
            + " tolerance=" + DisplayFixture.X11_AWT_BOUNDS_TOLERANCE);
        final long tolerance = DisplayFixture.X11_AWT_BOUNDS_TOLERANCE;
        assertThat(Math.abs((long) awt.x - ewmh.x)).isLessThanOrEqualTo(tolerance);
        assertThat(Math.abs((long) awt.y - ewmh.y)).isLessThanOrEqualTo(tolerance);
        assertThat(Math.abs(((long) awt.x + awt.width) - ((long) ewmh.x + ewmh.width)))
            .isLessThanOrEqualTo(tolerance);
        assertThat(Math.abs(((long) awt.y + awt.height) - ((long) ewmh.y + ewmh.height)))
            .isLessThanOrEqualTo(tolerance);
    }

    @Test
    public void escapeExitsWhenCanvasHasNoPreviewOrSelection() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        try (DisplayFixture f = new DisplayFixture()) {
            final AtomicReference<KeyEvent> escape = new AtomicReference<KeyEvent>();
            f.onEdt(() -> f.window.canvas().addKeyListener(new java.awt.event.KeyAdapter() {
                @Override
                public void keyPressed(final KeyEvent event) {
                    if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
                        escape.set(event);
                    }
                }
            }));
            f.pressF11();
            f.awaitActive(true);
            f.onEdt(() -> {
                org.freeplane.plugin.graph.canvas.GraphPaintState paint =
                    (org.freeplane.plugin.graph.canvas.GraphPaintState) GraphWindowChromeShould.field(
                        f.window.canvas(), "paintState");
                assertThat(paint.selection()).isEmpty();
                assertThat((Optional<?>) GraphWindowChromeShould.field(paint, "connectionPreview")).isEmpty();
            });
            f.pressEscape();
            f.awaitActive(false);
            f.onEdt(() -> {
                assertThat(escape.get()).isNotNull();
                assertThat(escape.get().isConsumed()).isTrue();
                assertThat(f.window.isUndecorated()).isFalse();
            });
            f.assertNoCommands();
        }
    }

    @Test
    public void panDuringPresentationIsPreservedThroughExit() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        try (DisplayFixture f = new DisplayFixture()) {
            f.pressF11();
            f.awaitActive(true);
            final GraphViewport before = f.view();
            final Point pointer = f.edt(() -> {
                JViewport viewport = (JViewport) f.window.canvas().getParent();
                Point point = viewport.getLocationOnScreen();
                point.translate(viewport.getWidth() / 2, viewport.getHeight() / 2);
                return point;
            });
            f.robot.mouseMove(pointer.x, pointer.y);
            f.robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            f.robot.mouseMove(pointer.x + 60, pointer.y + 40);
            f.robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            f.robot.mouseWheel(-2);
            f.await(() -> f.window.canvas().visibleViewport().zoom() != before.zoom());
            f.awaitEdt();
            final GraphViewport changed = f.view();
            assertThat(changed.centerX()).isNotEqualTo(before.centerX());
            assertThat(changed.centerY()).isNotEqualTo(before.centerY());
            assertThat(f.edt(() -> f.commands.size())).isGreaterThan(f.checkpoint);
            f.checkpoint = f.edt(() -> f.commands.size());
            f.pressF11();
            f.awaitActive(false);
            f.await(() -> !(Boolean) GraphWindowChromeShould.field(f.model.presentationViewport(), "armed"));
            f.assertView(changed);
            f.assertNoCommands();
        }
    }

    @Test
    public void canvasHasFocusAfterEntryAndExit() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        try (DisplayFixture f = new DisplayFixture()) {
            f.pressF11();
            f.awaitActive(true);
            f.awaitCanvasFocus();
            f.pressF11();
            f.awaitActive(false);
            f.awaitCanvasFocus();
            f.assertNoCommands();
        }
    }

    @Test
    public void oneF11PressPerformsExactlyOneToggle() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        try (DisplayFixture f = new DisplayFixture()) {
            final int[] operations = { 0, 0 };
            f.onEdt(() -> f.replaceTransitions(new GraphWorkspaceWindow.FrameTransitions(() -> {
                operations[0]++;
                f.window.rawEnterFullScreen();
            }, previous -> {
                operations[1]++;
                f.window.rawRestoreWindowed(previous);
            }, f.messages::add)));
            for (int cycle = 0; cycle < 2; cycle++) {
                if (cycle == 1) {
                    f.onEdt(() -> f.model.menuBar().setVisible(false));
                }
                final int expected = cycle + 1;
                f.pressF11();
                f.awaitActive(true);
                f.onEdt(() -> assertThat(operations).containsExactly(expected, expected - 1));
                f.pressF11();
                f.awaitActive(false);
                f.onEdt(() -> assertThat(operations).containsExactly(expected, expected));
            }
            f.assertNoCommands();
        }
    }

    @Test
    public void temporaryDisposeDoesNotClosePresentationMode() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        try (DisplayFixture f = new DisplayFixture()) {
            for (int cycle = 0; cycle < 2; cycle++) {
                f.pressF11();
                f.awaitActive(true);
                f.onEdt(() -> {
                    assertThat(f.menu.isEnabled()).isTrue();
                    assertThat(f.hintPanel.getParent()).isSameAs(f.window.getLayeredPane());
                    assertThat(GraphWindowChromeShould.field(f.model.presentationViewport(), "closed"))
                        .isEqualTo(false);
                    assertThat(f.chrome()).containsOnly(false);
                });
                f.pressF11();
                f.awaitActive(false);
                f.onEdt(() -> {
                    assertThat(f.window.isDisplayable()).isTrue();
                    assertThat(f.model.menuBar().isVisible()).isTrue();
                    assertThat(f.menu.isEnabled()).isTrue();
                });
            }
            f.assertNoCommands();
        }
    }

    @Test
    public void closeDuringTransitionLeavesDisposedWindowWithoutError() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        try (DisplayFixture f = new DisplayFixture()) {
            final AtomicReference<Runnable> late = new AtomicReference<Runnable>();
            f.onEdt(() -> f.replaceTransitions(new GraphWorkspaceWindow.FrameTransitions(() -> {
                late.set((Runnable) GraphWindowChromeShould.field(f.window.canvas(),
                    "pendingViewportSettledCallback"));
                f.window.close();
                f.window.rawEnterFullScreen();
            }, f.window::rawRestoreWindowed, f.messages::add)));
            f.pressF11();
            f.await(() -> !f.window.isDisplayable());
            f.onEdt(() -> {
                assertThat(GraphWindowChromeShould.field(f.mode, "state").toString()).isEqualTo("CLOSED");
                assertThat(f.menu.isSelected()).isFalse();
                assertThat(f.menu.isEnabled()).isFalse();
                assertThat(f.hintTimer.isRunning()).isFalse();
                assertThat(f.hintPanel.getParent()).isNull();
                if (late.get() != null) {
                    late.get().run();
                }
                f.hintTimer.getActionListeners()[0].actionPerformed(null);
                f.window.show();
                f.window.focus();
                f.mode.toggle();
                f.mode.requestExit();
                assertThat(f.window.isDisplayable()).isFalse();
                assertThat(GraphWindowChromeShould.field(f.model.presentationViewport(), "armed"))
                    .isEqualTo(false);
            });
            f.awaitEdt();
            f.assertNoCommands();
        }
    }

    @Test
    public void hintShowsOnEntryAndIsRemovedAfterInjectedDelay() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        try (DisplayFixture f = new DisplayFixture()) {
            final java.util.concurrent.atomic.AtomicBoolean shown = new java.util.concurrent.atomic.AtomicBoolean();
            f.onEdt(() -> {
                f.hintTimer.setInitialDelay(25);
                f.hintPanel.addComponentListener(new java.awt.event.ComponentAdapter() {
                    @Override
                    public void componentShown(final java.awt.event.ComponentEvent event) {
                        shown.set(f.hintPanel.isVisible());
                    }
                });
            });
            f.pressF11();
            f.awaitActive(true);
            f.await(shown::get);
            f.await(() -> !f.hintPanel.isVisible() && !f.hintTimer.isRunning());
            f.pressF11();
            f.awaitActive(false);
            f.onEdt(() -> {
                assertThat(f.hintPanel.isVisible()).isFalse();
                f.window.close();
                f.hintTimer.getActionListeners()[0].actionPerformed(null);
                assertThat(f.hintPanel.isVisible()).isFalse();
                assertThat(f.hintPanel.getParent()).isNull();
            });
        }
    }

    @Test
    public void rawFrameOperationsFollowSpecifiedOrder() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        try (DisplayFixture f = new DisplayFixture()) {
            f.onEdt(() -> {
                final GraphWorkspaceWindow spy = org.mockito.Mockito.spy(f.window);
                final List<String> operations = new ArrayList<String>();
                final int[] nesting = { 0 };
                final org.mockito.stubbing.Answer<Object> record = invocation -> {
                    boolean top = nesting[0]++ == 0;
                    try {
                        if (top) {
                            operations.add(invocation.getMethod().getName()
                                + Arrays.toString(invocation.getArguments()));
                        }
                        return invocation.callRealMethod();
                    }
                    finally {
                        nesting[0]--;
                    }
                };
                org.mockito.Mockito.doAnswer(record).when(spy).dispose();
                org.mockito.Mockito.doAnswer(record).when(spy).setUndecorated(any(Boolean.class));
                org.mockito.Mockito.doAnswer(record).when(spy).setExtendedState(any(Integer.class));
                org.mockito.Mockito.doAnswer(record).when(spy).setBounds(any(java.awt.Rectangle.class));
                org.mockito.Mockito.doAnswer(record).when(spy).setResizable(any(Boolean.class));
                org.mockito.Mockito.doAnswer(record).when(spy).setVisible(any(Boolean.class));
                org.mockito.Mockito.doAnswer(record).when(spy).validate();
                final java.awt.Rectangle screen = spy.getGraphicsConfiguration().getBounds();
                final ScreenFillSurface.FrameState previous = spy.captureFrameState();
                try {
                    operations.clear();
                    spy.rawEnterFullScreen();
                    List<String> enter = new ArrayList<String>(Arrays.asList("dispose[]", "setUndecorated[true]",
                        "setExtendedState[6]", "setBounds[" + screen + "]"));
                    if (org.freeplane.core.util.Compat.isWindowsOS()) {
                        enter.add("setResizable[false]");
                    }
                    enter.add("setVisible[true]");
                    enter.add("validate[]");
                    assertThat(operations).containsExactlyElementsOf(enter);
                    operations.clear();
                    spy.rawRestoreWindowed(previous);
                    List<String> restore = new ArrayList<String>(Arrays.asList("dispose[]", "setUndecorated[false]",
                        "setBounds[" + previous.bounds() + "]", "setExtendedState[" + previous.extendedState() + "]"));
                    if (org.freeplane.core.util.Compat.isWindowsOS()) {
                        restore.add("setResizable[false]");
                    }
                    restore.addAll(Arrays.asList("setResizable[true]", "setVisible[true]", "validate[]"));
                    assertThat(operations).containsExactlyElementsOf(restore);
                }
                finally {
                    spy.dispose();
                }
            });
        }
    }

    private static final class DisplayFixture implements AutoCloseable {
        // X11 AWT geometry can lag the window manager by a couple of pixels.
        private static final int X11_AWT_BOUNDS_TOLERANCE = 2;
        private final java.awt.Robot robot;
        private final List<GraphCommand> commands = new ArrayList<GraphCommand>();
        private final List<String> messages = new ArrayList<String>();
        private final AtomicReference<Throwable> uncaught = new AtomicReference<Throwable>();
        private GraphWorkspaceWindow window;
        private GraphWorkspaceWindowModel model;
        private GraphPresentationMode mode;
        private JCheckBoxMenuItem menu;
        private JPanel hintPanel;
        private javax.swing.Timer hintTimer;
        private Thread.UncaughtExceptionHandler previousHandler;
        private int checkpoint;
        private boolean requireMaximizedDuringStable;

        private DisplayFixture() throws Exception {
            robot = new java.awt.Robot();
            robot.setAutoDelay(15);
            final Fixture fixture = fixture(Viewport.of(37.25, -28.75, 1.25, emptyUnknownXml()),
                displayState(), Collections.singletonList(registration(ACTIVE_ID, "Active",
                    MapAvailability.AVAILABLE)), false);
            when(fixture.handle.execute(any(GraphCommand.class))).thenAnswer(invocation -> {
                commands.add(invocation.getArgument(0));
                return null;
            });
            onEdt(() -> {
                final EdtResources resources = new EdtResources();
                RESOURCES.add(resources);
                previousHandler = Thread.currentThread().getUncaughtExceptionHandler();
                Thread.currentThread().setUncaughtExceptionHandler((thread, failure) -> uncaught.compareAndSet(null, failure));
                window = new GraphWorkspaceWindow(fixture.handle, fixture.binding, fixture.closeController,
                    fixture.applicationController, () -> OPEN_PATH);
                model = (GraphWorkspaceWindowModel) GraphWindowChromeShould.field(window, "model");
                mode = (GraphPresentationMode) GraphWindowChromeShould.field(window, "presentationMode");
                menu = (JCheckBoxMenuItem) GraphWindowChromeShould.field(model, "fullScreenMenuItem");
                final Object hint = GraphWindowChromeShould.field(mode, "hint");
                hintPanel = (JPanel) GraphWindowChromeShould.field(hint, "panel");
                hintTimer = (javax.swing.Timer) GraphWindowChromeShould.field(hint, "timer");
                window.setBounds(120, 100, 1000, 700);
                window.show();
                window.toFront();
                window.canvas().requestFocusInWindow();
            });
            awaitCanvasFocus();
            awaitFrameStable();
            checkpoint = edt(() -> commands.size());
        }

        private static CanvasState displayState() {
            List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
            Map<ProjectedNodeKey, NodeGeometry> geometry = new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
            Map<ProjectedNodeKey, LayoutPoint> positions = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
            for (int index = 0; index < 2; index++) {
                ProjectedNodeKey key = ProjectedNodeKey.of(SourceNodeKey.transientPath(ACTIVE_ID,
                    Collections.singletonList(Integer.valueOf(index))));
                LayoutPoint point = LayoutPoint.of(index == 0 ? -4000.0 : 4000.0,
                    index == 0 ? -3000.0 : 3000.0);
                nodes.add(ProjectedNode.of(key, SafeNodeLabel.of("Node", "Node"), "Map", true));
                geometry.put(key, NodeGeometry.of(point, 10.0));
                positions.put(key, point);
            }
            return CanvasState.of(0L, GraphProjection.structure(0L, nodes, Collections.emptyList()),
                LayoutFrame.of(0L, LayoutPositions.of(positions, Collections.emptyMap()), false, 0),
                GraphGeometry.of(geometry, Collections.emptyMap()), EdgePaths.empty(), OperationalStatus.IDLE);
        }

        private void onEdt(final Runnable operation) {
            GraphWorkspaceWindow.runOnEdt(operation);
        }

        private <T> T edt(final Supplier<T> operation) {
            AtomicReference<T> result = new AtomicReference<T>();
            onEdt(() -> result.set(operation.get()));
            return result.get();
        }

        private void awaitEdt() {
            assertThat(javax.swing.SwingUtilities.isEventDispatchThread()).isFalse();
            onEdt(() -> { });
            onEdt(() -> { });
            assertThat(uncaught.get()).as("uncaught EDT error").isNull();
        }

        private void await(final java.util.function.BooleanSupplier condition) throws Exception {
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(5000);
            while (!edt(() -> condition.getAsBoolean())) {
                assertThat(uncaught.get()).as("uncaught EDT error").isNull();
                if (System.nanoTime() >= deadline) {
                    throw new AssertionError("Display condition did not settle within 5000 ms; bounds="
                        + edt(() -> window.getBounds()) + "; messages=" + messages);
                }
                Thread.sleep(10);
            }
            awaitEdt();
        }

        private void awaitCanvasFocus() throws Exception {
            final long deadline = System.nanoTime()
                + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(5000);
            while (!edt(() -> window.canvas().isFocusOwner())) {
                onEdt(() -> {
                    if (window.isDisplayable() && window.isVisible()) {
                        window.toFront();
                        window.canvas().requestFocusInWindow();
                    }
                });
                assertThat(uncaught.get()).as("uncaught EDT error").isNull();
                if (System.nanoTime() >= deadline) {
                    throw new AssertionError("Canvas did not regain focus within 5000 ms");
                }
                Thread.sleep(10);
            }
            awaitEdt();
        }

        private void awaitFrameStable() throws Exception {
            awaitFrameStable(200);
        }

        private void awaitFrameStable(final int quietMillis) throws Exception {
            final AtomicReference<java.awt.Rectangle> last = new AtomicReference<java.awt.Rectangle>();
            final long[] changed = { System.nanoTime() };
            await(() -> {
                if (requireMaximizedDuringStable) {
                    assertThat(window.isVisible()).as("maximized frame visible throughout stable interval").isTrue();
                    assertThat(window.isDisplayable()).as("maximized frame displayable throughout stable interval").isTrue();
                    assertThat(window.getExtendedState() & java.awt.Frame.MAXIMIZED_BOTH)
                        .as("MAXIMIZED_BOTH throughout stable interval").isEqualTo(java.awt.Frame.MAXIMIZED_BOTH);
                }
                java.awt.Rectangle current = window.getBounds();
                if (!current.equals(last.get())) {
                    last.set(current);
                    changed[0] = System.nanoTime();
                }
                return System.nanoTime() - changed[0]
                    >= java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(quietMillis);
            });
        }

        private void pressF11() throws Exception {
            press(KeyEvent.VK_F11);
        }

        private void pressEscape() throws Exception {
            press(KeyEvent.VK_ESCAPE);
        }

        private void press(final int key) throws Exception {
            awaitCanvasFocus();
            robot.keyPress(key);
            robot.keyRelease(key);
            awaitEdt();
        }

        private void awaitActive(final boolean active) throws Exception {
            await(() -> mode.isActive() == active);
            awaitFrameStable();
        }

        private GraphViewport view() {
            return edt(() -> window.canvas().visibleViewport());
        }

        private void assertView(final GraphViewport expected) {
            final GraphViewport actual = view();
            assertThat(actual.zoom()).isEqualTo(expected.zoom());
            // Pixel rounding allows half a pixel; the epsilon covers the world/pixel double round trip.
            assertThat(Math.abs(actual.centerX() - expected.centerX()) * actual.zoom())
                .as("horizontal center residual in pixels").isLessThanOrEqualTo(0.500000001);
            assertThat(Math.abs(actual.centerY() - expected.centerY()) * actual.zoom())
                .as("vertical center residual in pixels").isLessThanOrEqualTo(0.500000001);
        }

        private List<Boolean> chrome() {
            return Arrays.asList(model.menuBar().isVisible(), model.toolbar().isVisible(),
                ((Component) GraphWindowChromeShould.field(model, "sidebar")).isVisible(),
                model.settingsPanel().isVisible(), model.statusSlot().isVisible());
        }

        private void assertRoundTrip() throws Exception {
            assertRoundTrip(edt(() -> window.getBounds()), false);
        }

        private void assertRoundTrip(final boolean requireWorkArea) throws Exception {
            assertRoundTrip(requireWorkArea ? null : edt(() -> window.getBounds()), requireWorkArea);
        }

        private void assertRoundTrip(final java.awt.Rectangle expectedBounds, final boolean requireWorkArea)
                throws Exception {
            final int extended = edt(() -> window.getExtendedState());
            final List<Boolean> chrome = edt(this::chrome);
            final int divider = edt(() -> splitPane(model).getDividerLocation());
            final GraphViewport before = view();
            System.out.println("DISPLAY before bounds=" + expectedBounds + " state=" + extended + " view=" + before);
            pressF11();
            awaitActive(true);
            assertView(before);
            final GraphViewport exitBaseline = view();
            pressF11();
            awaitActive(false);
            System.out.println("DISPLAY restored bounds=" + edt(() -> window.getBounds())
                + " state=" + edt(() -> window.getExtendedState()) + " view=" + view());
            await(() -> window.getExtendedState() == extended
                && (requireWorkArea ? hasWorkAreaCoverage() : window.getBounds().equals(expectedBounds)));
            onEdt(() -> {
                assertThat(window.isUndecorated()).isFalse();
                if (requireWorkArea) {
                    assertWorkAreaCoverageOnEdt();
                }
                else {
                    assertThat(window.getBounds()).isEqualTo(expectedBounds);
                }
                assertThat(chrome()).isEqualTo(chrome);
                assertThat(splitPane(model).getDividerLocation()).isEqualTo(divider);
            });
            assertView(exitBaseline);
            assertNoCommands();
        }

        private boolean hasWorkAreaCoverage() {
            final java.awt.GraphicsConfiguration graphics = window.getGraphicsConfiguration();
            final java.awt.Rectangle screen = graphics.getBounds();
            final java.awt.Rectangle workArea = workArea(graphics);
            final java.awt.Rectangle actual = window.getBounds();
            return actual.x <= workArea.x + X11_AWT_BOUNDS_TOLERANCE
                && actual.y <= workArea.y + X11_AWT_BOUNDS_TOLERANCE
                && actual.x + actual.width >= workArea.x + workArea.width - X11_AWT_BOUNDS_TOLERANCE
                && actual.y + actual.height >= workArea.y + workArea.height - X11_AWT_BOUNDS_TOLERANCE
                && actual.x >= screen.x - X11_AWT_BOUNDS_TOLERANCE
                && actual.y >= screen.y - X11_AWT_BOUNDS_TOLERANCE
                && actual.x + actual.width <= screen.x + screen.width + X11_AWT_BOUNDS_TOLERANCE
                && actual.y + actual.height <= screen.y + screen.height + X11_AWT_BOUNDS_TOLERANCE;
        }

        private void assertWorkAreaCoverage() {
            onEdt(this::assertWorkAreaCoverageOnEdt);
        }

        // The maximized stable endpoint requires work-area coverage only. The stricter
        // screen-containment assertions of assertWorkAreaCoverageOnEdt belong to the
        // undecorated full-screen contract and do not hold for a decorated maximized frame.
        private void assertMaximizedWorkAreaCoverage() {
            onEdt(() -> {
                final java.awt.GraphicsConfiguration graphics = window.getGraphicsConfiguration();
                final java.awt.Rectangle workArea = workArea(graphics);
                final java.awt.Rectangle actual = window.getBounds();
                System.out.println("DISPLAY maximized endpoint bounds=" + actual
                    + " workArea=" + workArea);
                assertThat(actual.x).as("maximized frame covers work-area left edge")
                    .isLessThanOrEqualTo(workArea.x + X11_AWT_BOUNDS_TOLERANCE);
                assertThat(actual.y).as("maximized frame covers work-area top edge")
                    .isLessThanOrEqualTo(workArea.y + X11_AWT_BOUNDS_TOLERANCE);
                assertThat(actual.x + actual.width).as("maximized frame covers work-area right edge")
                    .isGreaterThanOrEqualTo(workArea.x + workArea.width - X11_AWT_BOUNDS_TOLERANCE);
                assertThat(actual.y + actual.height).as("maximized frame covers work-area bottom edge")
                    .isGreaterThanOrEqualTo(workArea.y + workArea.height - X11_AWT_BOUNDS_TOLERANCE);
            });
        }

        private void assertWorkAreaCoverageOnEdt() {
            final java.awt.GraphicsConfiguration graphics = window.getGraphicsConfiguration();
            final java.awt.Rectangle screen = graphics.getBounds();
            final java.awt.Rectangle workArea = workArea(graphics);
            final java.awt.Rectangle actual = window.getBounds();
            // X11 AWT can briefly report a cached outer bound while KWin has already
            // applied the current work-area placement; permit two pixels at each edge.
            assertThat(actual.x).as("full-screen frame left edge within screen")
                .isGreaterThanOrEqualTo(screen.x - X11_AWT_BOUNDS_TOLERANCE);
            assertThat(actual.y).as("full-screen frame top edge within screen")
                .isGreaterThanOrEqualTo(screen.y - X11_AWT_BOUNDS_TOLERANCE);
            assertThat(actual.x + actual.width).as("full-screen frame right edge within screen")
                .isLessThanOrEqualTo(screen.x + screen.width + X11_AWT_BOUNDS_TOLERANCE);
            assertThat(actual.y + actual.height).as("full-screen frame bottom edge within screen")
                .isLessThanOrEqualTo(screen.y + screen.height + X11_AWT_BOUNDS_TOLERANCE);
            assertThat(actual.x).as("full-screen frame covers work-area left edge")
                .isLessThanOrEqualTo(workArea.x + X11_AWT_BOUNDS_TOLERANCE);
            assertThat(actual.y).as("full-screen frame covers work-area top edge")
                .isLessThanOrEqualTo(workArea.y + X11_AWT_BOUNDS_TOLERANCE);
            assertThat(actual.x + actual.width).as("full-screen frame covers work-area right edge")
                .isGreaterThanOrEqualTo(workArea.x + workArea.width - X11_AWT_BOUNDS_TOLERANCE);
            assertThat(actual.y + actual.height).as("full-screen frame covers work-area bottom edge")
                .isGreaterThanOrEqualTo(workArea.y + workArea.height - X11_AWT_BOUNDS_TOLERANCE);
        }

        private static java.awt.Rectangle workArea(final java.awt.GraphicsConfiguration graphics) {
            final java.awt.Rectangle bounds = new java.awt.Rectangle(graphics.getBounds());
            final Insets insets = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(graphics);
            bounds.x += insets.left;
            bounds.y += insets.top;
            bounds.width -= insets.left + insets.right;
            bounds.height -= insets.top + insets.bottom;
            return bounds;
        }

        private void replaceTransitions(final GraphWorkspaceWindow.FrameTransitions replacement) {
            try {
                java.lang.reflect.Field field = GraphWorkspaceWindow.class.getDeclaredField("frameTransitions");
                field.setAccessible(true);
                field.set(window, replacement);
            }
            catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
        }

        private void assertNoCommands() {
            onEdt(() -> assertThat(commands).hasSize(checkpoint));
        }

        @Override
        public void close() {
            onEdt(() -> {
                if (window != null) {
                    window.close();
                }
                Thread.currentThread().setUncaughtExceptionHandler(previousHandler);
            });
            awaitEdt();
        }
    }

    private static void layoutSidebarAt(final GraphWorkspaceWindowModel model, final int width) {
        GraphWorkspaceWindow.runOnEdt(() -> {
            splitPane(model).setSize(width, 300);
            splitPane(model).doLayout();
        });
        GraphWorkspaceWindow.runOnEdt(() -> { });
    }

    private static Component divider(final GraphWorkspaceWindowModel model) {
        return ((BasicSplitPaneUI) splitPane(model).getUI()).getDivider();
    }

    private static MouseEvent dividerEvent(final GraphWorkspaceWindowModel model, final int id,
            final int clickCount) {
        final Component divider = divider(model);
        return new MouseEvent(divider, id, System.currentTimeMillis(), 0, 0, 0, clickCount, false,
            MouseEvent.BUTTON1);
    }

    private static void dispatchFocus(final GraphWorkspaceWindowModel model, final int id) {
        final JSplitPane splitPane = splitPane(model);
        // KeyboardFocusManager consumes directly dispatched focus events, so the
        // event only reaches the component's focus listeners through redispatchEvent.
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
            .redispatchEvent(splitPane, new FocusEvent(splitPane, id));
    }

    private static void dragSidebarTo(final GraphWorkspaceWindowModel model, final int rawLocation) {
        GraphWorkspaceWindow.runOnEdt(() -> {
            divider(model).dispatchEvent(dividerEvent(model, MouseEvent.MOUSE_PRESSED, 1));
            splitPane(model).setDividerLocation(rawLocation);
            divider(model).dispatchEvent(dividerEvent(model, MouseEvent.MOUSE_RELEASED, 1));
        });
        GraphWorkspaceWindow.runOnEdt(() -> { });
    }

    @Test
    public void commitsOneClampedSidebarWidthPerDividerDrag() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)),
            false).answerSidebarRoundTrip();
        GraphWorkspaceWindowModel model = fixture.model();
        layoutSidebarAt(model, 1000);

        dragSidebarTo(model, 1000 - splitPane(model).getInsets().right);

        assertThat(displayCommandCount(fixture)).isEqualTo(1);
        assertThat(lastDisplaySettings(fixture).mapSidebarWidth()).isEqualTo(500);

        Fixture moved = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)),
            false).answerSidebarRoundTrip();
        GraphWorkspaceWindowModel movedModel = moved.model();
        layoutSidebarAt(movedModel, 1000);

        dragSidebarTo(movedModel, 250);

        assertThat(displayCommandCount(moved)).isEqualTo(1);
        assertThat(lastDisplaySettings(moved).mapSidebarWidth()).isEqualTo(250);
        model.close();
        movedModel.close();
    }

    @Test
    public void commitsNothingForAPressAndReleaseWithoutMovement() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)),
            false).answerSidebarRoundTrip();
        GraphWorkspaceWindowModel model = fixture.model();
        layoutSidebarAt(model, 1000);
        int location = splitPane(model).getDividerLocation();

        GraphWorkspaceWindow.runOnEdt(() -> {
            divider(model).dispatchEvent(dividerEvent(model, MouseEvent.MOUSE_PRESSED, 1));
            divider(model).dispatchEvent(dividerEvent(model, MouseEvent.MOUSE_RELEASED, 1));
        });

        assertThat(displayCommandCount(fixture)).isZero();
        assertThat(model.appliedSidebarWidth()).isEqualTo(location);
        model.close();
    }

    @Test
    public void resetsTheSidebarWidthOnDividerDoubleClick() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)),
            false).answerSidebarRoundTrip();
        GraphWorkspaceWindowModel model = fixture.model();
        layoutSidebarAt(model, 1000);
        dragSidebarTo(model, 400);
        assertThat(lastDisplaySettings(fixture).mapSidebarWidth()).isEqualTo(400);

        GraphWorkspaceWindow.runOnEdt(() -> {
            divider(model).dispatchEvent(dividerEvent(model, MouseEvent.MOUSE_PRESSED, 2));
            divider(model).dispatchEvent(dividerEvent(model, MouseEvent.MOUSE_RELEASED, 2));
        });

        assertThat(displayCommandCount(fixture)).isEqualTo(2);
        assertThat(lastDisplaySettings(fixture).mapSidebarWidth()).isEqualTo(264);

        Fixture narrow = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)),
            false).answerSidebarRoundTrip();
        GraphWorkspaceWindowModel narrowModel = narrow.model();
        layoutSidebarAt(narrowModel, 400);
        dragSidebarTo(narrowModel, 190);

        GraphWorkspaceWindow.runOnEdt(() -> {
            divider(narrowModel).dispatchEvent(dividerEvent(narrowModel, MouseEvent.MOUSE_PRESSED, 2));
            divider(narrowModel).dispatchEvent(dividerEvent(narrowModel, MouseEvent.MOUSE_RELEASED, 2));
        });

        assertThat(displayCommandCount(narrow)).isEqualTo(2);
        assertThat(lastDisplaySettings(narrow).mapSidebarWidth()).isEqualTo(200);
        model.close();
        narrowModel.close();
    }

    @Test
    public void defersSidebarApplicationWhileTheDividerGestureIsActive() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)),
            false).answerSidebarRoundTrip();
        GraphWorkspaceWindowModel model = fixture.model();
        layoutSidebarAt(model, 1000);

        GraphWorkspaceWindow.runOnEdt(() ->
            divider(model).dispatchEvent(dividerEvent(model, MouseEvent.MOUSE_PRESSED, 1)));
        int before = splitPane(model).getDividerLocation();

        GraphWorkspaceWindow.runOnEdt(() -> model.acceptCanvasState(emptyState()));

        assertThat(splitPane(model).getDividerLocation()).isEqualTo(before);
        assertThat(displayCommandCount(fixture)).isZero();

        GraphWorkspaceWindow.runOnEdt(() -> {
            splitPane(model).setDividerLocation(400);
            divider(model).dispatchEvent(dividerEvent(model, MouseEvent.MOUSE_RELEASED, 1));
        });
        GraphWorkspaceWindow.runOnEdt(() -> { });

        assertThat(displayCommandCount(fixture)).isEqualTo(1);
        assertThat(lastDisplaySettings(fixture).mapSidebarWidth()).isEqualTo(400);
        model.close();
    }

    @Test
    public void preservesAnOutOfRangeStoredSidebarWidthAcrossNoOpGestures() {
        DisplaySettings wide = DisplaySettings.of(true, CanvasTheme.FOLLOW_FREEPLANE, true, true, 1000, false,
            emptyUnknownXml());
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)),
            false, WorkspaceSessionStatus.empty(), presentation(wide, ACTIVE_ID)).answerSidebarRoundTrip();
        GraphWorkspaceWindowModel model = fixture.model();
        layoutSidebarAt(model, 600);

        assertThat(splitPane(model).getDividerLocation()).isEqualTo(300);
        assertThat(model.appliedSidebarWidth()).isEqualTo(300);

        GraphWorkspaceWindow.runOnEdt(() -> {
            divider(model).dispatchEvent(dividerEvent(model, MouseEvent.MOUSE_PRESSED, 1));
            divider(model).dispatchEvent(dividerEvent(model, MouseEvent.MOUSE_RELEASED, 1));
            dispatchFocus(model, FocusEvent.FOCUS_LOST);
        });

        assertThat(displayCommandCount(fixture)).isZero();
        assertThat(model.appliedSidebarWidth()).isEqualTo(300);

        Fixture squeezed = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)),
            false).answerSidebarRoundTrip();
        GraphWorkspaceWindowModel squeezedModel = squeezed.model();
        layoutSidebarAt(squeezedModel, 150);

        GraphWorkspaceWindow.runOnEdt(() -> {
            divider(squeezedModel).dispatchEvent(dividerEvent(squeezedModel, MouseEvent.MOUSE_PRESSED, 1));
            divider(squeezedModel).dispatchEvent(dividerEvent(squeezedModel, MouseEvent.MOUSE_RELEASED, 1));
            dispatchFocus(squeezedModel, FocusEvent.FOCUS_LOST);
        });

        assertThat(displayCommandCount(squeezed)).isZero();
        assertThat(lastDisplaySettings(squeezed)).isNull();
        model.close();
        squeezedModel.close();
    }

    @Test
    public void suppressesSqueezedSidebarCommitsWhenTheDividerLocationChanged() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)),
            false).answerSidebarRoundTrip();
        GraphWorkspaceWindowModel model = fixture.model();
        layoutSidebarAt(model, 150);
        final int storedWidth = fixture.binding.currentPresentation().displaySettings().mapSidebarWidth();
        assertThat(model.appliedSidebarWidth()).isEqualTo(MapSidebarLayout.MIN_WIDTH);

        GraphWorkspaceWindow.runOnEdt(() -> {
            dispatchFocus(model, FocusEvent.FOCUS_GAINED);
            splitPane(model).getActionMap().get("selectMin").actionPerformed(
                new java.awt.event.ActionEvent(splitPane(model), java.awt.event.ActionEvent.ACTION_PERFORMED,
                    "selectMin"));
        });

        assertThat(splitPane(model).getDividerLocation()).isNotEqualTo(model.appliedSidebarWidth());

        GraphWorkspaceWindow.runOnEdt(() -> dispatchFocus(model, FocusEvent.FOCUS_LOST));

        assertThat(displayCommandCount(fixture)).isZero();
        assertThat(fixture.binding.currentPresentation().displaySettings().mapSidebarWidth())
            .isEqualTo(storedWidth);
        model.close();
    }

    @Test
    public void commitsTheKeyboardAdjustedSidebarWidthOnceOnFocusLoss() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)),
            false).answerSidebarRoundTrip();
        GraphWorkspaceWindowModel model = fixture.model();
        layoutSidebarAt(model, 1000);

        GraphWorkspaceWindow.runOnEdt(() -> {
            dispatchFocus(model, FocusEvent.FOCUS_GAINED);
            splitPane(model).getActionMap().get("selectMax").actionPerformed(
                new java.awt.event.ActionEvent(splitPane(model), java.awt.event.ActionEvent.ACTION_PERFORMED,
                    "selectMax"));
        });

        assertThat(splitPane(model).getDividerLocation()).isEqualTo(500);

        GraphWorkspaceWindow.runOnEdt(() ->
            dispatchFocus(model, FocusEvent.FOCUS_LOST));

        assertThat(displayCommandCount(fixture)).isEqualTo(1);
        assertThat(lastDisplaySettings(fixture).mapSidebarWidth()).isEqualTo(500);
        model.close();
    }

    @Test
    public void keepsTheViewMenuSidebarItemInSyncWithEveryToggle() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)),
            false).answerSidebarRoundTrip();
        GraphWorkspaceWindowModel model = fixture.model();
        layoutSidebarAt(model, 1000);

        assertThat(mapsSidebarMenuItem(model).isSelected()).isTrue();

        GraphWorkspaceWindow.runOnEdt(() -> sidebarPanel(model).collapseButton().doClick());
        assertThat(sidebarPanel(model).isCollapsed()).isTrue();
        assertThat(mapsSidebarMenuItem(model).isSelected()).isFalse();
        assertThat(displayCommandCount(fixture)).isEqualTo(1);
        assertThat(lastDisplaySettings(fixture).mapSidebarHidden()).isTrue();

        GraphWorkspaceWindow.runOnEdt(() -> sidebarPanel(model).rail().restoreButton().doClick());
        assertThat(sidebarPanel(model).isCollapsed()).isFalse();
        assertThat(mapsSidebarMenuItem(model).isSelected()).isTrue();
        assertThat(displayCommandCount(fixture)).isEqualTo(2);
        assertThat(lastDisplaySettings(fixture).mapSidebarHidden()).isFalse();

        GraphWorkspaceWindow.runOnEdt(() -> mapsSidebarMenuItem(model).doClick());
        assertThat(sidebarPanel(model).isCollapsed()).isTrue();
        assertThat(mapsSidebarMenuItem(model).isSelected()).isFalse();
        assertThat(displayCommandCount(fixture)).isEqualTo(3);

        Fixture stored = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)),
            false, WorkspaceSessionStatus.empty(),
            presentation(DisplaySettings.of(true, CanvasTheme.FOLLOW_FREEPLANE, true, true, 264, true,
                emptyUnknownXml()), ACTIVE_ID)).answerSidebarRoundTrip();
        GraphWorkspaceWindowModel storedModel = stored.model();
        layoutSidebarAt(storedModel, 1000);

        assertThat(sidebarPanel(storedModel).isCollapsed()).isTrue();
        assertThat(mapsSidebarMenuItem(storedModel).isSelected()).isFalse();
        model.close();
        storedModel.close();
    }

    @Test
    public void mapsTheActivePartitionCountToTheRailBadge() {
        MapReferenceId activeId = id(201L);
        MapReferenceId inactiveId = id(202L);
        List<GraphWorkspaceViewBinding.MapRegistration> registrations = Arrays.asList(
            registration(activeId, "Active", MapAvailability.AVAILABLE),
            registration(inactiveId, "Inactive", MapAvailability.INACTIVE));
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), registrations, false).answerSidebarRoundTrip();
        GraphWorkspaceWindowModel model = fixture.model();

        assertThat(sidebarPanel(model).rail().countBadge().getText()).isEqualTo("1");
        assertThat(sidebarPanel(model).rail().countBadge().getToolTipText())
            .isEqualTo("graph_workspace.map_list.rail_count[1]");

        List<GraphWorkspaceViewBinding.MapRegistration> allInactive = Collections.singletonList(
            registration(inactiveId, "Inactive", MapAvailability.INACTIVE));
        Fixture empty = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), allInactive, false).answerSidebarRoundTrip();
        GraphWorkspaceWindowModel emptyModel = empty.model();

        assertThat(sidebarPanel(emptyModel).rail().countBadge().getText()).isEmpty();
        assertThat(sidebarPanel(emptyModel).rail().countBadge().getToolTipText())
            .isEqualTo("graph_workspace.map_list.rail_count[0]");

        GraphWorkspaceWindow.runOnEdt(() -> sidebarPanel(emptyModel).collapseButton().doClick());
        GraphWorkspaceWindow.runOnEdt(() -> emptyModel.acceptCanvasState(emptyState()));

        assertThat(sidebarPanel(emptyModel).rail().countBadge().getText()).isEmpty();
        assertThat(displayCommandCount(empty)).isEqualTo(1);
        model.close();
        emptyModel.close();
    }

    @Test
    public void keepsTheMapsMenuActionsEnabledWhileCollapsed() {
        MapReferenceId activeId = id(301L);
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), Collections.singletonList(registration(activeId, "Active", MapAvailability.AVAILABLE)),
            false).answerSidebarRoundTrip();
        GraphWorkspaceWindowModel model = fixture.model();
        layoutSidebarAt(model, 1000);
        model.mapList().selectMap(activeId);
        boolean addBefore = menuItem(model, "add-map").isEnabled();
        boolean deactivateBefore = menuItem(model, "deactivate-map").isEnabled();

        GraphWorkspaceWindow.runOnEdt(() -> mapsSidebarMenuItem(model).doClick());

        assertThat(sidebarPanel(model).isCollapsed()).isTrue();
        assertThat(menuItem(model, "add-map").isEnabled()).isEqualTo(addBefore);
        assertThat(menuItem(model, "deactivate-map").isEnabled()).isEqualTo(deactivateBefore);
        assertThat(mapsSidebarMenuItem(model).isEnabled()).isTrue();
        assertThat(mapsSidebarMenuItem(model).isSelected()).isFalse();
        model.close();
    }

    @Test
    public void commitsNothingFromAGestureThatEndsWhileCollapsed() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)),
            false).answerSidebarRoundTrip();
        GraphWorkspaceWindowModel model = fixture.model();
        layoutSidebarAt(model, 1000);

        GraphWorkspaceWindow.runOnEdt(() -> {
            divider(model).dispatchEvent(dividerEvent(model, MouseEvent.MOUSE_PRESSED, 1));
            mapsSidebarMenuItem(model).doClick();
        });

        assertThat(displayCommandCount(fixture)).isEqualTo(1);
        assertThat(lastDisplaySettings(fixture).mapSidebarHidden()).isTrue();
        assertThat(sidebarPanel(model).isCollapsed()).isTrue();
        assertThat(splitPane(model).getDividerSize()).isZero();
        assertThat(splitPane(model).isEnabled()).isFalse();

        GraphWorkspaceWindow.runOnEdt(() -> {
            splitPane(model).setDividerLocation(400);
            divider(model).dispatchEvent(dividerEvent(model, MouseEvent.MOUSE_RELEASED, 1));
        });

        assertThat(displayCommandCount(fixture)).isEqualTo(1);
        assertThat(splitPane(model).getDividerLocation()).isEqualTo(26);
        model.close();
    }

    @Test
    public void keepsReadOnlySidebarInteractionsSessionOnly() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)),
            true).answerSidebarRoundTrip();
        GraphWorkspaceWindowModel model = fixture.model();
        layoutSidebarAt(model, 1000);

        dragSidebarTo(model, 400);
        GraphWorkspaceWindow.runOnEdt(() -> mapsSidebarMenuItem(model).doClick());

        assertThat(displayCommandCount(fixture)).isZero();
        assertThat(model.appliedSidebarWidth()).isEqualTo(26);
        assertThat(mapsSidebarMenuItem(model).isEnabled()).isTrue();
        assertThat(sidebarPanel(model).collapseButton().isEnabled()).isTrue();
        assertThat(sidebarPanel(model).rail().restoreButton().isEnabled()).isTrue();

        GraphWorkspaceWindow.runOnEdt(() -> mapsSidebarMenuItem(model).doClick());
        assertThat(sidebarPanel(model).isCollapsed()).isFalse();

        layoutSidebarAt(model, 800);
        assertThat(splitPane(model).getDividerLocation()).isEqualTo(400);
        assertThat(displayCommandCount(fixture)).isZero();

        model.setReadOnly(false);
        layoutSidebarAt(model, 900);

        assertThat(sidebarPanel(model).isCollapsed()).isFalse();
        assertThat(splitPane(model).getDividerLocation()).isEqualTo(264);
        assertThat(displayCommandCount(fixture)).isZero();
        model.close();
    }

    @Test
    public void restoresTheLastExpandedWidthOnExpand() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)),
            false).answerSidebarRoundTrip();
        GraphWorkspaceWindowModel model = fixture.model();
        layoutSidebarAt(model, 1000);
        dragSidebarTo(model, 400);
        assertThat(displayCommandCount(fixture)).isEqualTo(1);

        GraphWorkspaceWindow.runOnEdt(() -> sidebarPanel(model).collapseButton().doClick());

        assertThat(sidebarPanel(model).isCollapsed()).isTrue();
        assertThat(model.appliedSidebarWidth()).isEqualTo(26);
        assertThat(splitPane(model).getDividerSize()).isZero();
        assertThat(splitPane(model).isEnabled()).isFalse();
        assertThat(displayCommandCount(fixture)).isEqualTo(2);

        GraphWorkspaceWindow.runOnEdt(() -> sidebarPanel(model).rail().restoreButton().doClick());

        assertThat(sidebarPanel(model).isCollapsed()).isFalse();
        assertThat(splitPane(model).getDividerLocation()).isEqualTo(400);
        assertThat(splitPane(model).getDividerSize()).isEqualTo(6);
        assertThat(splitPane(model).isEnabled()).isTrue();
        assertThat(displayCommandCount(fixture)).isEqualTo(3);
        model.close();
    }

    @Test
    public void expandsFromCollapsedWithTheRestoredDividerWidthInOneReadOnlyApply() {
        DisplaySettings settings = DisplaySettings.of(true, CanvasTheme.FOLLOW_FREEPLANE, true, true, 400, true,
            emptyUnknownXml());
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)),
            true, WorkspaceSessionStatus.empty(), presentation(settings, ACTIVE_ID)).answerSidebarRoundTrip();
        GraphWorkspaceWindowModel model = fixture.model();
        layoutSidebarAt(model, 400);

        assertThat(sidebarPanel(model).isCollapsed()).isTrue();

        GraphWorkspaceWindow.runOnEdt(() -> mapsSidebarMenuItem(model).doClick());

        assertThat(displayCommandCount(fixture)).isZero();
        assertThat(sidebarPanel(model).isCollapsed()).isFalse();
        assertThat(splitPane(model).getDividerLocation()).isEqualTo(200);
        assertThat(sidebarPanel(model).getMinimumSize().width).isEqualTo(180);
        assertThat(canvasScrollPane(model).getMinimumSize().width).isEqualTo(193);
        assertThat(((BasicSplitPaneUI) splitPane(model).getUI()).getMaximumDividerLocation(splitPane(model)))
            .isEqualTo(200);
        model.close();
    }

    private static void assertViewportCommandCountAfterExternalScroll(final int expectedCount,
            final CanvasState initialState, final Consumer<GraphWorkspaceWindowModel> operation) {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()), initialState,
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        JViewport viewport = scrollViewport(model, new Dimension(420, 300));
        scrollOffCenter(model, viewport);
        GraphViewport external = model.canvas().visibleViewport();
        assertThat(external.centerX()).isNotEqualTo(model.canvas().viewport().centerX());
        org.mockito.Mockito.clearInvocations(fixture.handle);

        operation.accept(model);
        awaitDeferredViewportClamp();

        assertViewportCommandCount(fixture, model, expectedCount);
        model.close();
    }

    private static void assertCanvasGesturePersistsVisibleViewportOnce(final Consumer<GraphCanvas> gesture) {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            wideNodeState(ACTIVE_ID),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        scrollViewport(model, new Dimension(420, 300));
        awaitDeferredViewportClamp();
        org.mockito.Mockito.clearInvocations(fixture.handle);

        gesture.accept(model.canvas());
        awaitDeferredViewportClamp();

        assertViewportCommandCount(fixture, model, 1);
        model.close();
    }

    private static MouseEvent mouse(final GraphCanvas canvas, final int id, final int x, final int y,
            final int modifiers, final int button) {
        return new MouseEvent(canvas, id, System.currentTimeMillis(), modifiers, x, y, 1, false, button);
    }

    private static JViewport scrollViewport(final GraphWorkspaceWindowModel model, final Dimension size) {
        JScrollPane graphScrollPane = canvasScrollPane(model);
        graphScrollPane.setSize(size);
        graphScrollPane.doLayout();
        return graphScrollPane.getViewport();
    }

    private static Point scrollOffCenter(final GraphWorkspaceWindowModel model, final JViewport viewport) {
        int maxX = Math.max(0, model.canvas().getWidth() - viewport.getExtentSize().width);
        int maxY = Math.max(0, model.canvas().getHeight() - viewport.getExtentSize().height);
        viewport.setViewPosition(new Point(Math.max(1, maxX * 3 / 4), Math.max(1, maxY * 3 / 4)));
        Point position = viewport.getViewPosition();
        assertThat(position.x).isGreaterThan(0);
        return position;
    }

    private static void awaitDeferredViewportClamp() {
        GraphWorkspaceWindow.runOnEdt(() -> { });
    }

    private static void assertViewportCommandCount(final Fixture fixture, final GraphWorkspaceWindowModel model,
            final int expectedCount) {
        verify(fixture.handle, org.mockito.Mockito.times(expectedCount)).execute(any(GraphCommand.class));
        if (expectedCount == 1) {
            ArgumentCaptor<GraphCommand> commands = ArgumentCaptor.forClass(GraphCommand.class);
            verify(fixture.handle, org.mockito.Mockito.times(1)).execute(commands.capture());
            assertThat(commands.getValue()).isInstanceOf(GraphCommands.Viewport.class);
            GraphCommands.Viewport command = (GraphCommands.Viewport) commands.getValue();
            GraphViewport visible = model.canvas().visibleViewport();
            assertThat(command.viewport().centerX()).isEqualTo(visible.centerX());
            assertThat(command.viewport().centerY()).isEqualTo(visible.centerY());
            assertThat(command.viewport().zoom()).isEqualTo(visible.zoom());
        }
    }

    private static JPanel graphArea(final GraphWorkspaceWindowModel model) {
        return (JPanel) componentByName(model.content(), "graph-workspace-graph-area");
    }

    private static JSplitPane splitPane(final GraphWorkspaceWindowModel model) {
        return (JSplitPane) componentByName(graphArea(model), "graph-workspace-split");
    }

    private static MapSidebarPanel sidebarPanel(final GraphWorkspaceWindowModel model) {
        return (MapSidebarPanel) splitPane(model).getLeftComponent();
    }

    private static JScrollPane canvasScrollPane(final GraphWorkspaceWindowModel model) {
        return (JScrollPane) splitPane(model).getRightComponent();
    }

    private static JCheckBoxMenuItem mapsSidebarMenuItem(final GraphWorkspaceWindowModel model) {
        return (JCheckBoxMenuItem) menuItemByName(model, "graph-workspace-maps-sidebar-menu-item");
    }

    private static Component componentByName(final Container parent, final String name) {
        for (final Component component : parent.getComponents()) {
            if (name.equals(component.getName())) {
                return component;
            }
        }
        throw new AssertionError("Missing component " + name);
    }

    private static JMenuItem menuItemByName(final GraphWorkspaceWindowModel model, final String name) {
        for (int menuIndex = 0; menuIndex < model.menuBar().getMenuCount(); menuIndex++) {
            final JMenu menu = model.menuBar().getMenu(menuIndex);
            for (final Component component : menu.getMenuComponents()) {
                if (component instanceof JMenuItem && name.equals(component.getName())) {
                    return (JMenuItem) component;
                }
            }
        }
        throw new AssertionError("Missing menu item " + name);
    }

    private static int displayCommandCount(final Fixture fixture) {
        final ArgumentCaptor<GraphCommand> commands = ArgumentCaptor.forClass(GraphCommand.class);
        verify(fixture.handle, org.mockito.Mockito.atLeast(0)).execute(commands.capture());
        int count = 0;
        for (final GraphCommand command : commands.getAllValues()) {
            if (command instanceof GraphCommands.Display) {
                count++;
            }
        }
        return count;
    }

    private static DisplaySettings lastDisplaySettings(final Fixture fixture) {
        final ArgumentCaptor<GraphCommand> commands = ArgumentCaptor.forClass(GraphCommand.class);
        verify(fixture.handle, org.mockito.Mockito.atLeast(0)).execute(commands.capture());
        DisplaySettings result = null;
        for (final GraphCommand command : commands.getAllValues()) {
            if (command instanceof GraphCommands.Display) {
                result = ((GraphCommands.Display) command).settings();
            }
        }
        return result;
    }
    @Test
    public void placesRecentWorkspacesMenuBetweenSaveAsAndClose() {
        Fixture fixture = fixture(RecentWorkspaceList.empty());
        GraphWorkspaceWindowModel model = fixture.model();

        JMenu file = menu(model, "graph-workspace-file-menu");

        assertThat(file.getMenuComponentCount()).isEqualTo(6);
        assertThat(file.getMenuComponent(2).getName()).isEqualTo("graph-workspace-menu-item-save-as");
        assertThat(file.getMenuComponent(3)).isInstanceOf(JMenu.class);
        JMenu recents = (JMenu) file.getMenuComponent(3);
        assertThat(recents.getName()).isEqualTo("graph-workspace-recent-workspaces-menu");
        assertThat(recents.getText()).isEqualTo("graph_workspace.menu.recent_workspaces");
        assertThat(file.getMenuComponent(4)).isInstanceOf(JSeparator.class);
        assertThat(file.getMenuComponent(5).getName()).isEqualTo("graph-workspace-menu-item-close");
        model.close();
    }

    @Test
    public void rebuildsRecentEntriesInOrderWithLabels() throws Exception {
        Path first = temporaryFolder.newFile("menu-first.fpg").toPath().toRealPath();
        Path second = temporaryFolder.newFile("menu-second.fpg").toPath().toRealPath();
        RecentWorkspaceList list = recentList(first, second);
        Fixture fixture = fixture(list);
        GraphWorkspaceWindowModel model = fixture.model();
        List<RecentWorkspaceList.Entry> entries = list.displayEntries();

        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                model.rebuildRecentWorkspacesMenu();
                JMenu recents = menu(model, "graph-workspace-recent-workspaces-menu");
                assertThat(recents.isEnabled()).isTrue();
                assertThat(recents.getMenuComponentCount()).isEqualTo(4);
                assertThat(((JMenuItem) recents.getMenuComponent(0)).getName())
                    .isEqualTo("graph-workspace-recent-workspace-0");
                assertThat(((JMenuItem) recents.getMenuComponent(0)).getText())
                    .isEqualTo(entries.get(0).label());
                assertThat(((JMenuItem) recents.getMenuComponent(1)).getName())
                    .isEqualTo("graph-workspace-recent-workspace-1");
                assertThat(((JMenuItem) recents.getMenuComponent(1)).getText())
                    .isEqualTo(entries.get(1).label());
                assertThat(recents.getMenuComponent(2)).isInstanceOf(JSeparator.class);
                JMenuItem clear = (JMenuItem) recents.getMenuComponent(3);
                assertThat(clear.getName()).isEqualTo("graph-workspace-recent-workspaces-clear");
                assertThat(clear.isEnabled()).isTrue();
            }
        });
        model.close();
    }

    @Test
    public void limitsRebuiltEntriesToTheDisplayCap() throws Exception {
        RecentWorkspaceList list = new RecentWorkspaceList("", value -> { });
        for (int index = 0; index < 10; index++) {
            list.record(temporaryFolder.newFile("menu-cap-" + index + ".fpg").toPath().toRealPath());
        }
        Fixture fixture = fixture(list);
        GraphWorkspaceWindowModel model = fixture.model();

        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                model.rebuildRecentWorkspacesMenu();
                JMenu recents = menu(model, "graph-workspace-recent-workspaces-menu");
                assertThat(recents.getMenuComponentCount()).isEqualTo(10);
                for (int index = 0; index < RecentWorkspaceList.DISPLAY_CAPACITY; index++) {
                    assertThat(((JMenuItem) recents.getMenuComponent(index)).getName())
                        .isEqualTo("graph-workspace-recent-workspace-" + index);
                }
                assertThat(recents.getMenuComponent(8)).isInstanceOf(JSeparator.class);
                assertThat(((JMenuItem) recents.getMenuComponent(9)).getName())
                    .isEqualTo("graph-workspace-recent-workspaces-clear");
            }
        });
        model.close();
    }

    @Test
    public void hidesMissingEntriesFromTheRebuiltMenu() throws Exception {
        Path existing = temporaryFolder.newFile("menu-existing.fpg").toPath().toRealPath();
        Path missing = temporaryFolder.newFile("menu-missing.fpg").toPath().toRealPath();
        RecentWorkspaceList list = recentList(existing, missing);
        Files.delete(missing);
        Fixture fixture = fixture(list);
        GraphWorkspaceWindowModel model = fixture.model();
        String label = list.displayEntries().get(0).label();

        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                model.rebuildRecentWorkspacesMenu();
                JMenu recents = menu(model, "graph-workspace-recent-workspaces-menu");
                assertThat(recents.getMenuComponentCount()).isEqualTo(3);
                JMenuItem entry = (JMenuItem) recents.getMenuComponent(0);
                assertThat(entry.getName()).isEqualTo("graph-workspace-recent-workspace-0");
                assertThat(entry.getText()).isEqualTo(label);
                assertThat(recents.getMenuComponent(1)).isInstanceOf(JSeparator.class);
                assertThat(((JMenuItem) recents.getMenuComponent(2)).getName())
                    .isEqualTo("graph-workspace-recent-workspaces-clear");
            }
        });
        model.close();
    }

    @Test
    public void showsDisabledEmptyRowWithoutClearWhenNothingIsStored() {
        Fixture fixture = fixture(RecentWorkspaceList.empty());
        GraphWorkspaceWindowModel model = fixture.model();

        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                JMenu recents = menu(model, "graph-workspace-recent-workspaces-menu");
                recents.setEnabled(false);

                model.rebuildRecentWorkspacesMenu();

                assertThat(recents.isEnabled()).isTrue();
                assertThat(recents.getMenuComponentCount()).isEqualTo(1);
                JMenuItem empty = (JMenuItem) recents.getMenuComponent(0);
                assertThat(empty.getName()).isEqualTo("graph-workspace-recent-workspaces-empty");
                assertThat(empty.getText()).isEqualTo("graph_workspace.recent_workspaces.empty");
                assertThat(empty.isEnabled()).isFalse();
            }
        });
        model.close();
    }

    @Test
    public void keepsClearAndTheMenuEnabledWhenOnlyHiddenEntriesAreStored() throws Exception {
        Path missing = temporaryFolder.newFile("menu-hidden.fpg").toPath().toRealPath();
        RecentWorkspaceList list = recentList(missing);
        Files.delete(missing);
        Fixture fixture = fixture(list);
        GraphWorkspaceWindowModel model = fixture.model();

        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                JMenu recents = menu(model, "graph-workspace-recent-workspaces-menu");
                recents.setEnabled(false);

                model.rebuildRecentWorkspacesMenu();

                assertThat(recents.isEnabled()).isTrue();
                assertThat(recents.getMenuComponentCount()).isEqualTo(3);
                JMenuItem empty = (JMenuItem) recents.getMenuComponent(0);
                assertThat(empty.getName()).isEqualTo("graph-workspace-recent-workspaces-empty");
                assertThat(empty.isEnabled()).isFalse();
                assertThat(recents.getMenuComponent(1)).isInstanceOf(JSeparator.class);
                JMenuItem clear = (JMenuItem) recents.getMenuComponent(2);
                assertThat(clear.getName()).isEqualTo("graph-workspace-recent-workspaces-clear");
                assertThat(clear.isEnabled()).isTrue();
            }
        });
        model.close();
    }

    @Test
    public void clearsTheListAndRebuildsWhenClearIsClicked() throws Exception {
        Path existing = temporaryFolder.newFile("menu-clear.fpg").toPath().toRealPath();
        RecentWorkspaceList list = recentList(existing);
        Fixture fixture = fixture(list);
        GraphWorkspaceWindowModel model = fixture.model();

        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                model.rebuildRecentWorkspacesMenu();
                JMenu recents = menu(model, "graph-workspace-recent-workspaces-menu");
                JMenuItem clear = (JMenuItem) recents.getMenuComponent(2);
                assertThat(clear.getName()).isEqualTo("graph-workspace-recent-workspaces-clear");

                clear.doClick();

                assertThat(list.hasStoredEntries()).isFalse();
                assertThat(recents.getMenuComponentCount()).isEqualTo(1);
                assertThat(((JMenuItem) recents.getMenuComponent(0)).getName())
                    .isEqualTo("graph-workspace-recent-workspaces-empty");
            }
        });
        model.close();
    }

    @Test
    public void reportsAFailedOpenFromTheMenuAndNeverCreatesAWorkspace() throws Exception {
        Path existing = temporaryFolder.newFile("menu-failing.fpg").toPath().toRealPath();
        RecentWorkspaceList list = recentList(existing);
        Fixture fixture = fixture(list);
        List<String> messages = new ArrayList<String>();
        GraphWorkspaceWindowModel model = fixture.model(messages::add);
        when(fixture.applicationController.openExisting(any(Path.class)))
            .thenThrow(new GraphWorkspaceOpenException(existing, new IllegalStateException("missing")));
        AtomicReference<JMenuItem> entry = new AtomicReference<JMenuItem>();

        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                model.rebuildRecentWorkspacesMenu();
                JMenu recents = menu(model, "graph-workspace-recent-workspaces-menu");
                assertThat(recents.getMenuComponentCount()).isEqualTo(3);
                entry.set((JMenuItem) recents.getMenuComponent(0));
            }
        });

        Files.delete(existing);

        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                entry.get().doClick();

                assertThat(messages).containsExactly(
                    "graph_workspace.recent_workspaces.open_failed[" + existing + "]");
                JMenu recents = menu(model, "graph-workspace-recent-workspaces-menu");
                assertThat(recents.getMenuComponentCount()).isEqualTo(3);
                assertThat(((JMenuItem) recents.getMenuComponent(0)).getName())
                    .isEqualTo("graph-workspace-recent-workspaces-empty");
                assertThat(((JMenuItem) recents.getMenuComponent(2)).getName())
                    .isEqualTo("graph-workspace-recent-workspaces-clear");
            }
        });
        verify(fixture.applicationController, never()).open(any());
        model.close();
    }

    @Test
    public void rebuildsTheMenuFromThePopupListener() throws Exception {
        Path existing = temporaryFolder.newFile("menu-popup.fpg").toPath().toRealPath();
        RecentWorkspaceList list = recentList(existing);
        Fixture fixture = fixture(list);
        GraphWorkspaceWindowModel model = fixture.model();

        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                JMenu recents = menu(model, "graph-workspace-recent-workspaces-menu");

                recentsPopupListener(recents).popupMenuWillBecomeVisible(
                    new PopupMenuEvent(recents.getPopupMenu()));

                assertThat(recents.getMenuComponentCount()).isEqualTo(3);
                assertThat(((JMenuItem) recents.getMenuComponent(0)).getName())
                    .isEqualTo("graph-workspace-recent-workspace-0");
            }
        });
        model.close();
    }

    @Test
    public void keepsTheNineArgumentConstructorForTheUiEvidenceHarness() {
        Fixture fixture = fixture(RecentWorkspaceList.empty());
        final GraphWorkspaceWindowModel[] result = new GraphWorkspaceWindowModel[1];
        final EdtResources[] edtResources = new EdtResources[1];
        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                edtResources[0] = new EdtResources();
                try {
                    Constructor<GraphWorkspaceWindowModel> constructor =
                        GraphWorkspaceWindowModel.class.getDeclaredConstructor(
                            GraphWorkspaceHandle.class, GraphWorkspaceViewBinding.class,
                            GraphWorkspaceController.class, Supplier.class, WorkspaceCloseController.class,
                            Runnable.class, Runnable.class, Runnable.class, Consumer.class);
                    result[0] = constructor.newInstance(fixture.handle, fixture.binding,
                        fixture.applicationController, (Supplier<Path>) () -> OPEN_PATH,
                        fixture.closeController, (Runnable) () -> { }, (Runnable) () -> { },
                        (Runnable) () -> { }, (Consumer<String>) message -> { });
                }
                catch (ReflectiveOperationException failure) {
                    throw new AssertionError(failure);
                }
            }
        });
        RESOURCES.add(edtResources[0]);
        GraphWorkspaceWindowModel model = result[0];

        assertThat(model).isNotNull();
        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                model.rebuildRecentWorkspacesMenu();
                JMenu recents = menu(model, "graph-workspace-recent-workspaces-menu");
                assertThat(recents.getMenuComponentCount()).isEqualTo(1);
                assertThat(((JMenuItem) recents.getMenuComponent(0)).getName())
                    .isEqualTo("graph-workspace-recent-workspaces-empty");
            }
        });
        model.close();
    }

    private static GraphCommandResult commandResult(final WorkspaceTransition transition) {
        return GraphCommandResult.from(transition);
    }

    private static WorkspaceDocument emptyDocument() {
        return WorkspaceDocument.createVersion1(WorkspaceId.of(
            "00000000-0000-0000-0000-000000000001"));
    }
    private static Fixture fixture(Viewport viewport, CanvasState state,
            List<GraphWorkspaceViewBinding.MapRegistration> registrations, boolean readOnly) {
        return fixture(viewport, state, registrations, readOnly, WorkspaceSessionStatus.empty(),
            presentation(DisplaySettings.defaults(), ACTIVE_ID));
    }

    private static Fixture fixture(Viewport viewport, CanvasState state,
            List<GraphWorkspaceViewBinding.MapRegistration> registrations, boolean readOnly,
            WorkspaceSessionStatus sessionStatus) {
        return fixture(viewport, state, registrations, readOnly, sessionStatus,
            presentation(DisplaySettings.defaults(), ACTIVE_ID));
    }

    private static Fixture fixture(Viewport viewport, CanvasState state,
            List<GraphWorkspaceViewBinding.MapRegistration> registrations, boolean readOnly,
            WorkspaceSessionStatus sessionStatus, GraphWorkspacePresentation presentation) {
        return fixture(viewport, state, registrations, readOnly, sessionStatus, presentation,
            RecentWorkspaceList.empty());
    }

    private static Fixture fixture(Viewport viewport, CanvasState state,
            List<GraphWorkspaceViewBinding.MapRegistration> registrations, boolean readOnly,
            WorkspaceSessionStatus sessionStatus, GraphWorkspacePresentation presentation,
            RecentWorkspaceList recentWorkspaces) {
        GraphWorkspaceController applicationController = mock(GraphWorkspaceController.class);
        GraphWorkspaceHandle handle = mock(GraphWorkspaceHandle.class);
        WorkspaceCloseController closeController = mock(WorkspaceCloseController.class);
        GraphWorkspaceViewBinding binding = mock(GraphWorkspaceViewBinding.class);
        ListenerRegistration registration = mock(ListenerRegistration.class);
        ListenerRegistration sessionRegistration = mock(ListenerRegistration.class);
        when(binding.currentViewport()).thenReturn(viewport);
        when(binding.currentCanvasState()).thenReturn(state);
        when(binding.currentMapRows()).thenReturn(registrations);
        when(binding.isReadOnly()).thenReturn(readOnly);
        when(binding.currentPresentation()).thenReturn(presentation);
        when(binding.currentSessionStatus()).thenReturn(sessionStatus);
        when(binding.addCanvasStateListener(any())).thenReturn(registration);
        when(binding.addSessionStatusListener(any())).thenReturn(sessionRegistration);
        return new Fixture(applicationController, handle, closeController, binding, registration,
            sessionRegistration, recentWorkspaces);
    }

    private static Fixture fixture(final RecentWorkspaceList recentWorkspaces) {
        return fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()), emptyState(),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false,
            WorkspaceSessionStatus.empty(), presentation(DisplaySettings.defaults(), ACTIVE_ID),
            recentWorkspaces);
    }

    private static GraphWorkspaceViewBinding.MapRegistration registration(MapReferenceId id, String name,
            MapAvailability availability) {
        return GraphWorkspaceViewBinding.MapRegistration.of(id, name, availability);
    }

    private static MapReferenceId id(long value) {
        return MapReferenceId.of(UUID.fromString(String.format("00000000-0000-0000-0000-%012d", value)));
    }

    private static List<UnknownXml> emptyUnknownXml() {
        return Collections.emptyList();
    }

    private static CanvasState emptyState() {
        return CanvasState.of(0L, GraphProjection.structure(0L, Collections.emptyList(), Collections.emptyList()),
            LayoutFrame.of(0L, LayoutPositions.of(Collections.emptyMap(), Collections.emptyMap()), false, 0),
            GraphGeometry.of(Collections.emptyMap(), Collections.emptyMap()), EdgePaths.empty(),
            OperationalStatus.LOADING);
    }

    @Test
    public void selectsTheSelectedNodeMapRowAndPinsTheSelectedNode() throws Exception {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            persistedNodeState(ACTIVE_ID, LayoutPoint.of(2.0, -3.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        ProjectedNodeKey nodeKey = ProjectedNodeKey.of(SourceNodeKey.persisted(
            NodeReference.of(ACTIVE_ID, PersistedNodeId.of("selected"))));
        ProjectedEndpointKey nodeEndpoint = ProjectedEndpointKey.ofNode(nodeKey);

        model.acceptIntent(new GraphIntent.ChangeSelection(Optional.of(nodeEndpoint)));

        assertThat(model.mapList().rows().get(0).selected()).isTrue();
        model.toolbar().pinButton().doClick();

        ArgumentCaptor<GraphCommand> commands = ArgumentCaptor.forClass(GraphCommand.class);
        verify(fixture.handle).execute(commands.capture());
        assertThat(commands.getValue()).isInstanceOf(GraphCommands.Pin.class);
        GraphCommands.Pin pin = (GraphCommands.Pin) commands.getValue();
        assertThat(pin.node()).isEqualTo(NodeReference.of(ACTIVE_ID, PersistedNodeId.of("selected")));
        assertThat(pin.x()).isEqualTo(2.0);
        assertThat(pin.y()).isEqualTo(-3.0);

        model.acceptIntent(new GraphIntent.ChangeSelection(Optional.<ProjectedEndpointKey>empty()));
        assertThat(model.mapList().rows().get(0).selected()).isFalse();
        Method togglePin = GraphWorkspaceWindowModel.class.getDeclaredMethod("togglePinSelectedNode");
        togglePin.setAccessible(true);
        togglePin.invoke(model);
        verify(fixture.handle, org.mockito.Mockito.times(1)).execute(any(GraphCommand.class));
        model.close();
    }

    @Test
    public void boundarySelectionDoesNotPinOrSelectAMapRow() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            boundaryWithNodeState(),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        EnclosureKey boundary = EnclosureKey.of(SourceNodeKey.transientPath(ACTIVE_ID,
            Collections.singletonList(Integer.valueOf(2))));
        ProjectedEndpointKey boundaryEndpoint = ProjectedEndpointKey.ofEnclosure(boundary);

        model.acceptIntent(new GraphIntent.ChangeSelection(Optional.of(boundaryEndpoint)));

        assertThat(model.mapList().rows().get(0).selected()).isFalse();
        model.toolbar().pinButton().doClick();
        verify(fixture.handle, org.mockito.Mockito.never()).execute(any(GraphCommand.class));
        model.close();
    }

    @Test
    public void graphSelectionOverridesAClickedMapRow() {
        MapReferenceId activeA = id(201L);
        MapReferenceId activeB = id(202L);
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(activeA, LayoutPoint.of(0.0, 0.0)),
            Arrays.asList(registration(activeA, "A", MapAvailability.AVAILABLE),
                registration(activeB, "B", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        model.mapList().selectMap(activeB);

        model.acceptIntent(new GraphIntent.ChangeSelection(Optional.of(transientNodeEndpoint(activeA))));

        assertThat(model.mapList().selectedRow()).isNotNull();
        assertThat(model.mapList().selectedRow().mapReferenceId()).isEqualTo(activeA);
        assertThat(model.mapList().activeList().getSelectedIndex()).isEqualTo(0);
        assertThat(model.mapList().inactiveList().getSelectedIndex()).isEqualTo(-1);
        assertThat(model.mapList().rows().get(0).selected()).isTrue();
        assertThat(model.mapList().rows().get(1).selected()).isFalse();
        assertThat(menuItem(model, "deactivate-map").isEnabled()).isTrue();
        assertThat(menuItem(model, "reactivate-map").isEnabled()).isFalse();
        model.close();
    }

    @Test
    public void emptyGraphSelectionOverridesAClickedMapRow() {
        MapReferenceId activeA = id(203L);
        MapReferenceId activeB = id(204L);
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(activeA, LayoutPoint.of(0.0, 0.0)),
            Arrays.asList(registration(activeA, "A", MapAvailability.AVAILABLE),
                registration(activeB, "B", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        model.mapList().selectMap(activeB);

        model.acceptIntent(new GraphIntent.ChangeSelection(Optional.<ProjectedEndpointKey>empty()));

        assertThat(model.mapList().selectedRow()).isNull();
        assertThat(model.mapList().activeList().getSelectedIndex()).isEqualTo(-1);
        assertThat(model.mapList().inactiveList().getSelectedIndex()).isEqualTo(-1);
        assertThat(model.mapList().rows().get(0).selected()).isFalse();
        assertThat(model.mapList().rows().get(1).selected()).isFalse();
        assertThat(menuItem(model, "deactivate-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "reactivate-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "delete-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "retry-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "locate-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "add-map").isEnabled()).isTrue();
        model.close();
    }

    @Test
    public void enclosureGraphSelectionOverridesAClickedMapRow() {
        MapReferenceId activeA = id(205L);
        MapReferenceId activeB = id(206L);
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(activeA, LayoutPoint.of(0.0, 0.0)),
            Arrays.asList(registration(activeA, "A", MapAvailability.AVAILABLE),
                registration(activeB, "B", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        model.mapList().selectMap(activeB);
        EnclosureKey boundary = EnclosureKey.of(SourceNodeKey.transientPath(activeA,
            Collections.singletonList(Integer.valueOf(2))));

        model.acceptIntent(new GraphIntent.ChangeSelection(
            Optional.of(ProjectedEndpointKey.ofEnclosure(boundary))));

        assertThat(model.mapList().selectedRow()).isNull();
        assertThat(model.mapList().activeList().getSelectedIndex()).isEqualTo(-1);
        assertThat(model.mapList().inactiveList().getSelectedIndex()).isEqualTo(-1);
        assertThat(model.mapList().rows().get(0).selected()).isFalse();
        assertThat(model.mapList().rows().get(1).selected()).isFalse();
        model.close();
    }

    @Test
    public void neutralCanvasUpdatePreservesAClickedRowAndItsActions() {
        MapReferenceId activeA = id(207L);
        MapReferenceId activeB = id(208L);
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(activeA, LayoutPoint.of(0.0, 0.0)),
            Arrays.asList(registration(activeA, "A", MapAvailability.AVAILABLE),
                registration(activeB, "B", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        model.mapList().selectMap(activeB);

        model.acceptCanvasState(nodeState(activeA, LayoutPoint.of(5.0, 5.0)));

        assertThat(model.mapList().selectedRow()).isNotNull();
        assertThat(model.mapList().selectedRow().mapReferenceId()).isEqualTo(activeB);
        assertThat(model.mapList().activeList().getSelectedIndex()).isEqualTo(1);
        assertThat(model.mapList().inactiveList().getSelectedIndex()).isEqualTo(-1);
        assertThat(model.mapList().rows().get(0).selected()).isFalse();
        assertThat(model.mapList().rows().get(1).selected()).isTrue();
        assertThat(model.mapList().removeButton().getText()).isEqualTo("graph_workspace.action.deactivate_map");
        assertThat(model.mapList().removeButton().isEnabled()).isTrue();
        assertThat(menuItem(model, "deactivate-map").isEnabled()).isTrue();
        assertThat(menuItem(model, "reactivate-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "delete-map").isEnabled()).isFalse();
        model.close();
    }

    @Test
    public void readOnlyTogglePreservesTheClickedRowAndOnlyDisablesItsActions() {
        MapReferenceId activeA = id(209L);
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(activeA, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(activeA, "A", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        model.mapList().selectMap(activeA);

        model.setReadOnly(true);

        assertThat(model.mapList().selectedRow()).isNotNull();
        assertThat(model.mapList().selectedRow().mapReferenceId()).isEqualTo(activeA);
        assertThat(model.mapList().rows().get(0).state()).isEqualTo(MapListPanel.RowState.READ_ONLY);
        assertThat(model.mapList().rows().get(0).selected()).isTrue();
        assertThat(model.mapList().addButton().isEnabled()).isFalse();
        assertThat(model.mapList().removeButton().isEnabled()).isFalse();
        assertThat(model.mapList().retryButton().isEnabled()).isFalse();
        assertThat(model.mapList().locateButton().isEnabled()).isFalse();
        assertThat(menuItem(model, "add-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "deactivate-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "reactivate-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "delete-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "retry-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "locate-map").isEnabled()).isFalse();
        model.close();
    }

    @Test
    public void clearingTheGraphSelectionDisablesTheMapMenuItems() {
        MapReferenceId activeA = id(210L);
        MapReferenceId inactiveB = id(211L);
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(activeA, LayoutPoint.of(0.0, 0.0)),
            Arrays.asList(registration(activeA, "A", MapAvailability.AVAILABLE),
                registration(inactiveB, "B", MapAvailability.INACTIVE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        model.mapList().selectMap(activeA);

        model.mapList().clearSelection();

        assertThat(model.mapList().selectedRow()).isNull();
        assertThat(model.mapList().activeList().getSelectedIndex()).isEqualTo(-1);
        assertThat(model.mapList().inactiveList().getSelectedIndex()).isEqualTo(-1);
        for (MapListPanel.MapRow row : model.mapList().rows()) {
            assertThat(row.selected()).isFalse();
        }
        assertThat(menuItem(model, "deactivate-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "reactivate-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "delete-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "retry-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "locate-map").isEnabled()).isFalse();
        assertThat(menuItem(model, "add-map").isEnabled()).isTrue();
        model.close();
    }

    @Test
    public void deletionFrameFallsThroughToTheFlaggedGraphSelectedRow() {
        MapReferenceId activeA = id(212L);
        MapReferenceId inactiveB = id(213L);
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(activeA, LayoutPoint.of(0.0, 0.0)),
            Arrays.asList(registration(activeA, "A", MapAvailability.AVAILABLE),
                registration(inactiveB, "B", MapAvailability.INACTIVE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        model.acceptIntent(new GraphIntent.ChangeSelection(Optional.of(transientNodeEndpoint(activeA))));
        model.mapList().selectMap(inactiveB);
        assertThat(model.mapList().selectedRow().mapReferenceId()).isEqualTo(inactiveB);

        // The model test owns no DeleteConfirmationPrompt; execute the same command the
        // panel action dispatches and deliver the post-delete registration set.
        model.execute(GraphCommands.deleteMap(inactiveB));
        when(fixture.binding.currentMapRows()).thenReturn(Collections.singletonList(
            registration(activeA, "A", MapAvailability.AVAILABLE)));

        model.acceptCanvasState(nodeState(activeA, LayoutPoint.of(0.0, 0.0)));

        assertThat(model.mapList().selectedRow()).isNotNull();
        assertThat(model.mapList().selectedRow().mapReferenceId()).isEqualTo(activeA);
        assertThat(model.mapList().activeList().getSelectedIndex()).isEqualTo(0);
        assertThat(model.mapList().rows().size()).isEqualTo(1);
        assertThat(model.mapList().rows().get(0).selected()).isTrue();
        assertThat(menuItem(model, "deactivate-map").isEnabled()).isTrue();
        model.close();
    }

    @Test
    public void tracksPinToggleLabelAndEnablementAcrossSelectionAndPinnedStates() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            persistedNodeState(ACTIVE_ID, LayoutPoint.of(2.0, -3.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        ProjectedNodeKey nodeKey = ProjectedNodeKey.of(SourceNodeKey.persisted(
            NodeReference.of(ACTIVE_ID, PersistedNodeId.of("selected"))));
        ProjectedEndpointKey nodeEndpoint = ProjectedEndpointKey.ofNode(nodeKey);

        assertThat(model.toolbar().pinButton().getText()).isEqualTo("graph_workspace.action.pin");
        assertThat(model.toolbar().pinButton().isEnabled()).isFalse();
        model.acceptIntent(new GraphIntent.ChangeSelection(Optional.of(nodeEndpoint)));
        assertThat(model.toolbar().pinButton().getText()).isEqualTo("graph_workspace.action.pin");
        assertThat(model.toolbar().pinButton().isEnabled()).isTrue();
        model.acceptCanvasState(persistedPinnedNodeState(ACTIVE_ID, LayoutPoint.of(2.0, -3.0), 2.0, -3.0));
        assertThat(model.toolbar().pinButton().getText()).isEqualTo("graph_workspace.action.unpin");
        assertThat(model.toolbar().pinButton().isEnabled()).isTrue();
        model.acceptIntent(new GraphIntent.ChangeSelection(Optional.<ProjectedEndpointKey>empty()));
        assertThat(model.toolbar().pinButton().getText()).isEqualTo("graph_workspace.action.pin");
        assertThat(model.toolbar().pinButton().isEnabled()).isFalse();
        model.close();

        Fixture boundaryFixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            boundaryWithNodeState(),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel boundaryModel = boundaryFixture.model();
        EnclosureKey boundary = EnclosureKey.of(SourceNodeKey.transientPath(ACTIVE_ID,
            Collections.singletonList(Integer.valueOf(2))));
        ProjectedEndpointKey boundaryEndpoint = ProjectedEndpointKey.ofEnclosure(boundary);

        boundaryModel.acceptIntent(new GraphIntent.ChangeSelection(Optional.of(boundaryEndpoint)));
        assertThat(boundaryModel.toolbar().pinButton().getText()).isEqualTo("graph_workspace.action.pin");
        assertThat(boundaryModel.toolbar().pinButton().isEnabled()).isFalse();
        boundaryModel.close();
    }

    @Test
    public void keepsPinLabelTruthfulWhenReadOnlyDisablesTheToggle() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            persistedPinnedNodeState(ACTIVE_ID, LayoutPoint.of(2.0, -3.0), 2.0, -3.0),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), true);
        GraphWorkspaceWindowModel model = fixture.model();
        ProjectedNodeKey nodeKey = ProjectedNodeKey.of(SourceNodeKey.persisted(
            NodeReference.of(ACTIVE_ID, PersistedNodeId.of("selected"))));
        ProjectedEndpointKey nodeEndpoint = ProjectedEndpointKey.ofNode(nodeKey);

        model.acceptIntent(new GraphIntent.ChangeSelection(Optional.of(nodeEndpoint)));
        assertThat(model.toolbar().pinButton().getText()).isEqualTo("graph_workspace.action.unpin");
        assertThat(model.toolbar().pinButton().isEnabled()).isFalse();
        model.acceptCanvasState(persistedNodeState(ACTIVE_ID, LayoutPoint.of(2.0, -3.0)));
        assertThat(model.toolbar().pinButton().getText()).isEqualTo("graph_workspace.action.pin");
        assertThat(model.toolbar().pinButton().isEnabled()).isFalse();
        model.close();
    }

    @Test
    public void removesTheStandaloneUnpinControlFromToolbarAndApprovedNames() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();

        assertThat(model.toolbar().approvedControlNames()).doesNotContain("unpin").contains("pin");
        for (Component component : model.toolbar().getComponents()) {
            assertThat(component.getName()).isNotEqualTo("graph-workspace-unpin");
        }
        model.close();
    }

    @Test
    public void pinsTheSelectedNodeAtItsGeometryCentreAndUnpinsWhenPinned() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            persistedNodeState(ACTIVE_ID, LayoutPoint.of(2.0, -3.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        ProjectedNodeKey nodeKey = ProjectedNodeKey.of(SourceNodeKey.persisted(
            NodeReference.of(ACTIVE_ID, PersistedNodeId.of("selected"))));
        ProjectedEndpointKey nodeEndpoint = ProjectedEndpointKey.ofNode(nodeKey);
        model.acceptIntent(new GraphIntent.ChangeSelection(Optional.of(nodeEndpoint)));

        model.toolbar().pinButton().doClick();
        ArgumentCaptor<GraphCommand> commands = ArgumentCaptor.forClass(GraphCommand.class);
        verify(fixture.handle).execute(commands.capture());
        assertThat(commands.getValue()).isInstanceOf(GraphCommands.Pin.class);
        GraphCommands.Pin pin = (GraphCommands.Pin) commands.getValue();
        assertThat(pin.node()).isEqualTo(NodeReference.of(ACTIVE_ID, PersistedNodeId.of("selected")));
        assertThat(pin.x()).isEqualTo(2.0);
        assertThat(pin.y()).isEqualTo(-3.0);

        org.mockito.Mockito.clearInvocations(fixture.handle);
        model.acceptCanvasState(persistedPinnedNodeState(ACTIVE_ID, LayoutPoint.of(2.0, -3.0), 2.0, -3.0));
        assertThat(model.toolbar().pinButton().getText()).isEqualTo("graph_workspace.action.unpin");
        model.toolbar().pinButton().doClick();
        ArgumentCaptor<GraphCommand> unpinCommands = ArgumentCaptor.forClass(GraphCommand.class);
        verify(fixture.handle).execute(unpinCommands.capture());
        assertThat(unpinCommands.getValue()).isInstanceOf(GraphCommands.Unpin.class);
        assertThat(((GraphCommands.Unpin) unpinCommands.getValue()).node())
            .isEqualTo(NodeReference.of(ACTIVE_ID, PersistedNodeId.of("selected")));
        model.close();
    }

    @Test
    public void doesNotCommandPinWithoutGeometryOrSelection() throws Exception {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            persistedNodeStateWithoutGeometry(ACTIVE_ID),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        ProjectedNodeKey nodeKey = ProjectedNodeKey.of(SourceNodeKey.persisted(
            NodeReference.of(ACTIVE_ID, PersistedNodeId.of("selected"))));
        ProjectedEndpointKey nodeEndpoint = ProjectedEndpointKey.ofNode(nodeKey);
        model.acceptIntent(new GraphIntent.ChangeSelection(Optional.of(nodeEndpoint)));

        assertThat(model.toolbar().pinButton().isEnabled()).isTrue();
        model.toolbar().pinButton().doClick();
        verify(fixture.handle, never()).execute(any(GraphCommand.class));

        model.acceptCanvasState(persistedPinnedNodeStateWithoutGeometry(ACTIVE_ID));
        assertThat(model.toolbar().pinButton().getText()).isEqualTo("graph_workspace.action.unpin");
        model.toolbar().pinButton().doClick();
        ArgumentCaptor<GraphCommand> commands = ArgumentCaptor.forClass(GraphCommand.class);
        verify(fixture.handle).execute(commands.capture());
        assertThat(commands.getValue()).isInstanceOf(GraphCommands.Unpin.class);
        org.mockito.Mockito.clearInvocations(fixture.handle);

        model.acceptIntent(new GraphIntent.ChangeSelection(Optional.<ProjectedEndpointKey>empty()));
        Method method = GraphWorkspaceWindowModel.class.getDeclaredMethod("togglePinSelectedNode");
        method.setAccessible(true);
        method.invoke(model);
        verify(fixture.handle, never()).execute(any(GraphCommand.class));
        model.close();
    }

    @Test
    public void refreshesPinToggleAfterPostCommandCanvasStatePublication() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            persistedNodeState(ACTIVE_ID, LayoutPoint.of(2.0, -3.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        ProjectedNodeKey nodeKey = ProjectedNodeKey.of(SourceNodeKey.persisted(
            NodeReference.of(ACTIVE_ID, PersistedNodeId.of("selected"))));
        ProjectedEndpointKey nodeEndpoint = ProjectedEndpointKey.ofNode(nodeKey);
        model.acceptIntent(new GraphIntent.ChangeSelection(Optional.of(nodeEndpoint)));
        assertThat(model.toolbar().pinButton().getText()).isEqualTo("graph_workspace.action.pin");

        model.acceptCanvasState(persistedPinnedNodeState(ACTIVE_ID, LayoutPoint.of(2.0, -3.0), 2.0, -3.0));
        assertThat(model.toolbar().pinButton().getText()).isEqualTo("graph_workspace.action.unpin");

        org.mockito.Mockito.clearInvocations(fixture.handle);
        ArgumentCaptor<GraphCommand> commands = ArgumentCaptor.forClass(GraphCommand.class);
        model.acceptIntent(new GraphIntent.Unpin(nodeKey));
        verify(fixture.handle, org.mockito.Mockito.times(1)).execute(commands.capture());
        assertThat(commands.getValue()).isInstanceOf(GraphCommands.Unpin.class);
        model.acceptCanvasState(persistedNodeState(ACTIVE_ID, LayoutPoint.of(2.0, -3.0)));
        assertThat(model.toolbar().pinButton().getText()).isEqualTo("graph_workspace.action.pin");

        model.acceptCanvasState(persistedPinnedNodeState(ACTIVE_ID, LayoutPoint.of(2.0, -3.0), 2.0, -3.0));
        assertThat(model.toolbar().pinButton().getText()).isEqualTo("graph_workspace.action.unpin");
        org.mockito.Mockito.clearInvocations(fixture.handle);
        ArgumentCaptor<GraphCommand> allCommands = ArgumentCaptor.forClass(GraphCommand.class);
        model.acceptIntent(new GraphIntent.UnpinAll());
        verify(fixture.handle, org.mockito.Mockito.times(1)).execute(allCommands.capture());
        assertThat(allCommands.getValue()).isInstanceOf(GraphCommands.UnpinAll.class);
        model.acceptCanvasState(persistedNodeState(ACTIVE_ID, LayoutPoint.of(2.0, -3.0)));
        assertThat(model.toolbar().pinButton().getText()).isEqualTo("graph_workspace.action.pin");

        model.acceptCanvasState(persistedPinnedNodeState(ACTIVE_ID, LayoutPoint.of(2.0, -3.0), 2.0, -3.0));
        assertThat(model.toolbar().pinButton().getText()).isEqualTo("graph_workspace.action.unpin");
        ArgumentCaptor<WorkspaceSessionStatusListener> statusListener =
            ArgumentCaptor.forClass(WorkspaceSessionStatusListener.class);
        verify(fixture.binding).addSessionStatusListener(statusListener.capture());
        statusListener.getValue().onWorkspaceSessionStatus(
            WorkspaceSessionStatus.of(true, true, false, false, Collections.<MapReferenceId>emptySet(),
                Optional.<org.freeplane.plugin.graph.command.MapUndoTarget>empty()));
        assertThat(model.toolbar().pinButton().getText()).isEqualTo("graph_workspace.action.unpin");
        assertThat(model.toolbar().pinButton().isEnabled()).isTrue();
        model.close();
    }

    @Test
    public void rejectsPinAndUnpinIntentsInReadOnlySessions() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            persistedNodeState(ACTIVE_ID, LayoutPoint.of(2.0, -3.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), true);
        GraphWorkspaceWindowModel model = fixture.model();
        ProjectedNodeKey nodeKey = ProjectedNodeKey.of(SourceNodeKey.persisted(
            NodeReference.of(ACTIVE_ID, PersistedNodeId.of("selected"))));

        model.acceptIntent(new GraphIntent.Pin(nodeKey, 5.0, 6.0));
        model.acceptIntent(new GraphIntent.Unpin(nodeKey));
        verify(fixture.handle, never()).execute(any(GraphCommand.class));
        assertThat(model.toolbar().pinButton().isEnabled()).isFalse();
        model.close();
    }

    @Test
    public void connectIntentRequiresBothNodeEndpoints() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            wideNodeState(ACTIVE_ID),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        SourceNodeKey left = SourceNodeKey.transientPath(ACTIVE_ID,
            Collections.singletonList(Integer.valueOf(0)));
        SourceNodeKey right = SourceNodeKey.transientPath(ACTIVE_ID,
            Collections.singletonList(Integer.valueOf(1)));
        ProjectedEndpointKey leftEndpoint = ProjectedEndpointKey.ofNode(ProjectedNodeKey.of(left));
        ProjectedEndpointKey rightEndpoint = ProjectedEndpointKey.ofNode(ProjectedNodeKey.of(right));
        ProjectedEndpointKey boundaryEndpoint = ProjectedEndpointKey.ofEnclosure(EnclosureKey.of(
            SourceNodeKey.transientPath(ACTIVE_ID, Collections.singletonList(Integer.valueOf(2)))));

        model.acceptIntent(new GraphIntent.Connect(leftEndpoint, boundaryEndpoint,
            RelationshipDirection.FORWARD));
        verify(fixture.handle, org.mockito.Mockito.never()).execute(any(GraphCommand.class));

        model.acceptIntent(new GraphIntent.Connect(leftEndpoint, rightEndpoint,
            RelationshipDirection.FORWARD));
        ArgumentCaptor<GraphCommand> commands = ArgumentCaptor.forClass(GraphCommand.class);
        verify(fixture.handle).execute(commands.capture());
        assertThat(commands.getValue()).isInstanceOf(GraphCommands.Connect.class);
        GraphCommands.Connect connect = (GraphCommands.Connect) commands.getValue();
        assertThat(connect.source()).isEqualTo(left);
        assertThat(connect.target()).isEqualTo(right);
        assertThat(connect.direction()).isEqualTo(RelationshipDirection.FORWARD);
        model.close();
    }

    private static CanvasState persistedNodeState(MapReferenceId mapId, LayoutPoint center) {
        SourceNodeKey source = SourceNodeKey.persisted(
            NodeReference.of(mapId, PersistedNodeId.of("selected")));
        ProjectedNodeKey key = ProjectedNodeKey.of(source);
        ProjectedNode node = ProjectedNode.of(key, SafeNodeLabel.of("Selected", "Selected"), "Map", true);
        GraphProjection projection = GraphProjection.structure(0L, Collections.singletonList(node),
            Collections.emptyList());
        GraphGeometry geometry = GraphGeometry.of(Collections.singletonMap(key, NodeGeometry.of(center, 10.0)),
            Collections.emptyMap());
        LayoutFrame layout = LayoutFrame.of(0L, LayoutPositions.of(
            Collections.singletonMap(key, center), Collections.emptyMap()), false, 0);
        return CanvasState.of(0L, projection, layout, geometry, EdgePaths.empty(), OperationalStatus.IDLE);
    }

    private static CanvasState persistedPinnedNodeState(MapReferenceId mapId, LayoutPoint center,
            double pinX, double pinY) {
        SourceNodeKey source = SourceNodeKey.persisted(
            NodeReference.of(mapId, PersistedNodeId.of("selected")));
        ProjectedNodeKey key = ProjectedNodeKey.of(source);
        ProjectedNode node = ProjectedNode.of(key, SafeNodeLabel.of("Selected", "Selected"), "Map", true);
        GraphProjection projection = GraphProjection.projected(0L, Collections.singletonList(node),
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
            Collections.singletonList(PinProjection.active(
                PinRecord.of(NodeReference.of(mapId, PersistedNodeId.of("selected")), pinX, pinY,
                    Collections.emptyList()), key)));
        GraphGeometry geometry = GraphGeometry.of(Collections.singletonMap(key, NodeGeometry.of(center, 10.0)),
            Collections.emptyMap());
        LayoutFrame layout = LayoutFrame.of(0L, LayoutPositions.of(
            Collections.singletonMap(key, center), Collections.emptyMap()), false, 0);
        return CanvasState.of(0L, projection, layout, geometry, EdgePaths.empty(), OperationalStatus.IDLE);
    }

    private static CanvasState persistedNodeStateWithoutGeometry(MapReferenceId mapId) {
        SourceNodeKey source = SourceNodeKey.persisted(
            NodeReference.of(mapId, PersistedNodeId.of("selected")));
        ProjectedNodeKey key = ProjectedNodeKey.of(source);
        ProjectedNode node = ProjectedNode.of(key, SafeNodeLabel.of("Selected", "Selected"), "Map", true);
        GraphProjection projection = GraphProjection.structure(0L, Collections.singletonList(node),
            Collections.emptyList());
        return CanvasState.of(0L, projection,
            LayoutFrame.of(0L, LayoutPositions.of(Collections.emptyMap(), Collections.emptyMap()), false, 0),
            GraphGeometry.of(Collections.emptyMap(), Collections.emptyMap()), EdgePaths.empty(),
            OperationalStatus.IDLE);
    }

    private static CanvasState persistedPinnedNodeStateWithoutGeometry(MapReferenceId mapId) {
        SourceNodeKey source = SourceNodeKey.persisted(
            NodeReference.of(mapId, PersistedNodeId.of("selected")));
        ProjectedNodeKey key = ProjectedNodeKey.of(source);
        ProjectedNode node = ProjectedNode.of(key, SafeNodeLabel.of("Selected", "Selected"), "Map", true);
        GraphProjection projection = GraphProjection.projected(0L, Collections.singletonList(node),
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
            Collections.singletonList(PinProjection.active(
                PinRecord.of(NodeReference.of(mapId, PersistedNodeId.of("selected")), 2.0, -3.0,
                    Collections.emptyList()), key)));
        return CanvasState.of(0L, projection,
            LayoutFrame.of(0L, LayoutPositions.of(Collections.emptyMap(), Collections.emptyMap()), false, 0),
            GraphGeometry.of(Collections.emptyMap(), Collections.emptyMap()), EdgePaths.empty(),
            OperationalStatus.IDLE);
    }

    private static CanvasState boundaryWithNodeState() {
        SourceNodeKey nodeSource = SourceNodeKey.transientPath(ACTIVE_ID,
            Collections.singletonList(Integer.valueOf(0)));
        ProjectedNodeKey key = ProjectedNodeKey.of(nodeSource);
        ProjectedNode node = ProjectedNode.of(key, SafeNodeLabel.of("Node", "Node"), "Map", true);
        EnclosureKey boundary = EnclosureKey.of(SourceNodeKey.transientPath(ACTIVE_ID,
            Collections.singletonList(Integer.valueOf(2))));
        EnclosureHullKey hullKey = EnclosureHullKey.of(Collections.singletonList(boundary));
        ProjectedEnclosure enclosure = ProjectedEnclosure.of(hullKey,
            Collections.singletonList(boundary),
            Collections.singletonList(SafeNodeLabel.of("Boundary", "Boundary")), "Map",
            java.util.Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(),
            Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.EMPHATIC);
        LayoutPoint anchor = LayoutPoint.of(-40.0, 0.0);
        HullGeometry hull = HullGeometry.of(Arrays.asList(LayoutPoint.of(-60.0, -20.0),
            LayoutPoint.of(-20.0, -20.0), LayoutPoint.of(-20.0, 20.0), LayoutPoint.of(-60.0, 20.0)), anchor);
        GraphProjection projection = GraphProjection.projected(0L, Collections.singletonList(node),
            Collections.singletonList(enclosure), Collections.emptyList(), Collections.emptyList(),
            Collections.emptyList());
        GraphGeometry geometry = GraphGeometry.of(Collections.singletonMap(key,
            NodeGeometry.of(LayoutPoint.of(0.0, 0.0), 10.0)), Collections.singletonMap(hullKey, hull));
        LayoutFrame layout = LayoutFrame.of(0L, LayoutPositions.of(
            Collections.singletonMap(key, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonMap(hullKey, anchor)), false, 0);
        return CanvasState.of(0L, projection, layout, geometry, EdgePaths.empty(), OperationalStatus.IDLE);
    }

    private static CanvasState nodeState(MapReferenceId mapId, LayoutPoint center) {
        SourceNodeKey source = SourceNodeKey.transientPath(mapId, Collections.emptyList());
        ProjectedNodeKey key = ProjectedNodeKey.of(source);
        ProjectedNode node = ProjectedNode.of(key, SafeNodeLabel.of("Node", "Node"), "Map", false);
        GraphProjection projection = GraphProjection.structure(0L, Collections.singletonList(node),
            Collections.emptyList());
        GraphGeometry geometry = GraphGeometry.of(Collections.singletonMap(key, NodeGeometry.of(center, 10.0)),
            Collections.emptyMap());
        LayoutFrame layout = LayoutFrame.of(0L, LayoutPositions.of(
            Collections.singletonMap(key, center), Collections.emptyMap()), false, 0);
        return CanvasState.of(0L, projection, layout, geometry, EdgePaths.empty(), OperationalStatus.IDLE);
    }

    private static ProjectedEndpointKey transientNodeEndpoint(final MapReferenceId mapId) {
        return ProjectedEndpointKey.ofNode(ProjectedNodeKey.of(
            SourceNodeKey.transientPath(mapId, Collections.emptyList())));
    }

    private static CanvasState wideNodeState(MapReferenceId mapId) {
        return wideNodeState(mapId, 500.0);
    }

    private static CanvasState wideNodeState(MapReferenceId mapId, double coordinate) {
        return wideNodeState(mapId, coordinate, 0.0);
    }

    private static CanvasState wideNodeState(MapReferenceId mapId, double coordinate,
            double verticalCoordinate) {
        SourceNodeKey leftSource = SourceNodeKey.transientPath(mapId, Collections.singletonList(Integer.valueOf(0)));
        SourceNodeKey rightSource = SourceNodeKey.transientPath(mapId, Collections.singletonList(Integer.valueOf(1)));
        ProjectedNodeKey leftKey = ProjectedNodeKey.of(leftSource);
        ProjectedNodeKey rightKey = ProjectedNodeKey.of(rightSource);
        ProjectedNode left = ProjectedNode.of(leftKey, SafeNodeLabel.of("Left", "Left"), "Map", false);
        ProjectedNode right = ProjectedNode.of(rightKey, SafeNodeLabel.of("Right", "Right"), "Map", false);
        List<ProjectedNode> nodes = Arrays.asList(left, right);
        GraphProjection projection = GraphProjection.structure(0L, nodes, Collections.emptyList());
        LayoutPoint leftCenter = LayoutPoint.of(-coordinate, -verticalCoordinate);
        LayoutPoint rightCenter = LayoutPoint.of(coordinate, verticalCoordinate);
        Map<ProjectedNodeKey, NodeGeometry> geometry = new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
        geometry.put(leftKey, NodeGeometry.of(leftCenter, 10.0));
        geometry.put(rightKey, NodeGeometry.of(rightCenter, 10.0));
        Map<ProjectedNodeKey, LayoutPoint> positions = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        positions.put(leftKey, leftCenter);
        positions.put(rightKey, rightCenter);
        return CanvasState.of(0L, projection,
            LayoutFrame.of(0L, LayoutPositions.of(positions, Collections.emptyMap()), false, 0),
            GraphGeometry.of(geometry, Collections.emptyMap()), EdgePaths.empty(), OperationalStatus.IDLE);
    }

    private static CanvasState enclosureState(MapReferenceId mapId) {
        SourceNodeKey source = SourceNodeKey.transientPath(mapId, Collections.emptyList());
        EnclosureKey endpoint = EnclosureKey.of(source);
        EnclosureHullKey hullKey = EnclosureHullKey.of(Collections.singletonList(endpoint));
        ProjectedEnclosure enclosure = ProjectedEnclosure.of(hullKey,
            Collections.singletonList(endpoint),
            Collections.singletonList(SafeNodeLabel.of("Enclosure", "Enclosure")), "Map",
            java.util.Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(),
            Collections.<EnclosureHullKey>emptyList(), true, BoundaryTier.EMPHATIC);
        LayoutPoint anchor = LayoutPoint.of(0.0, 0.0);
        HullGeometry hull = HullGeometry.of(Arrays.asList(LayoutPoint.of(-30.0, -20.0),
            LayoutPoint.of(30.0, -20.0), LayoutPoint.of(30.0, 20.0), LayoutPoint.of(-30.0, 20.0)), anchor);
        GraphProjection projection = GraphProjection.projected(0L,
            Collections.<ProjectedNode>emptyList(), Collections.singletonList(enclosure),
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        GraphGeometry geometry = GraphGeometry.of(Collections.<ProjectedNodeKey, NodeGeometry>emptyMap(),
            Collections.singletonMap(hullKey, hull));
        LayoutFrame layout = LayoutFrame.of(0L, LayoutPositions.of(Collections.emptyMap(),
            Collections.singletonMap(hullKey, anchor)), false, 0);
        return CanvasState.of(0L, projection, layout, geometry, EdgePaths.empty(), OperationalStatus.IDLE);
    }


    private static GraphWorkspacePresentation presentation(final DisplaySettings settings,
            final MapReferenceId... ids) {
        List<GraphWorkspacePresentation.MapColor> colors = new java.util.ArrayList<GraphWorkspacePresentation.MapColor>();
        String[] palette = new String[] {"#4E79A7", "#E15759", "#59A14F", "#F28E2B"};
        for (int index = 0; index < ids.length; index++) {
            colors.add(GraphWorkspacePresentation.MapColor.of(ids[index], palette[index % palette.length]));
        }
        return GraphWorkspacePresentation.of(settings, colors);
    }

    private static Map<MapReferenceId, String> palette(final MapReferenceId... ids) {
        Map<MapReferenceId, String> result = new LinkedHashMap<MapReferenceId, String>();
        String[] values = new String[] {"#4E79A7", "#E15759", "#59A14F", "#F28E2B"};
        for (int index = 0; index < ids.length; index++) {
            result.put(ids[index], values[index % values.length]);
        }
        return result;
    }

    private static BufferedImage paintCanvas(final GraphCanvas canvas) {
        int width = Math.max(1, canvas.getWidth());
        int height = Math.max(1, canvas.getHeight());
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            canvas.paint(graphics);
        }
        finally {
            graphics.dispose();
        }
        return image;
    }

    private static int nonBackgroundPixels(final BufferedImage image, final int background) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) != background) {
                    count++;
                }
            }
        }
        return count;
    }

    private static BufferedImage paintField(final JTextField field) {
        field.setSize(field.getPreferredSize());
        BufferedImage image = new BufferedImage(Math.max(1, field.getWidth()),
            Math.max(1, field.getHeight()), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            field.paint(graphics);
        }
        finally {
            graphics.dispose();
        }
        return image;
    }

    private static int pixelsMatching(final BufferedImage image, final Color color) {
        int expected = color.getRGB();
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) == expected) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int pixelsMatchingAtOrRightOf(final BufferedImage image, final Color color,
            final int minimumX) {
        int expected = color.getRGB();
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = Math.max(0, minimumX); x < image.getWidth(); x++) {
                if (image.getRGB(x, y) == expected) {
                    count++;
                }
            }
        }
        return count;
    }

    private static void withFocusedField(final GraphSearchField field, final Runnable action) {
        final JFrame[] frame = new JFrame[1];
        try {
            GraphWorkspaceWindow.runOnEdt(new Runnable() {
                @Override
                public void run() {
                    JFrame value = new JFrame();
                    value.setLayout(new BorderLayout());
                    value.add(field, BorderLayout.CENTER);
                    value.add(new JButton("Next"), BorderLayout.SOUTH);
                    value.setSize(500, 400);
                    value.setVisible(true);
                    field.requestFocusInWindow();
                    frame[0] = value;
                }
            });
            waitForFieldFocus(field);
            GraphWorkspaceWindow.runOnEdt(action);
        }
        finally {
            if (frame[0] != null) {
                GraphWorkspaceWindow.runOnEdt(new Runnable() {
                    @Override
                    public void run() {
                        frame[0].dispose();
                    }
                });
            }
        }
    }

    private static void waitForFieldFocus(final GraphSearchField field) {
        for (int attempt = 0; attempt < 100; attempt++) {
            final boolean[] focused = new boolean[1];
            GraphWorkspaceWindow.runOnEdt(new Runnable() {
                @Override
                public void run() {
                    field.requestFocusInWindow();
                    focused[0] = field.isFocusOwner();
                }
            });
            if (focused[0]) {
                return;
            }
            try {
                Thread.sleep(10L);
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
        }
        throw new AssertionError("Search field did not gain focus");
    }

    private static final class RecordingPaintingIcon implements Icon {
        private final int width;
        private final int height;
        private final Color color;
        private int lastX = -1;
        private int lastY = -1;

        private RecordingPaintingIcon(final int width, final int height, final Color color) {
            this.width = width;
            this.height = height;
            this.color = color;
        }

        @Override
        public void paintIcon(final Component component, final Graphics graphics, final int x, final int y) {
            lastX = x;
            lastY = y;
            graphics.setColor(color);
            graphics.fillRect(x, y, width, height);
        }

        @Override
        public int getIconWidth() {
            return width;
        }

        @Override
        public int getIconHeight() {
            return height;
        }
    }

    private static JMenuItem menuItem(final GraphWorkspaceWindowModel model, final String name) {
        final String expected = "graph-workspace-menu-item-" + name;
        for (int menuIndex = 0; menuIndex < model.menuBar().getMenuCount(); menuIndex++) {
            JMenu menu = model.menuBar().getMenu(menuIndex);
            for (Component component : menu.getMenuComponents()) {
                if (component instanceof JMenuItem && expected.equals(component.getName())) {
                    return (JMenuItem) component;
                }
            }
        }
        throw new AssertionError("Missing menu item " + expected);
    }

    private static RecentWorkspaceList recentList(final Path... paths) {
        RecentWorkspaceList list = new RecentWorkspaceList("", value -> { });
        for (Path path : paths) {
            list.record(path);
        }
        return list;
    }

    private static JMenu menu(final GraphWorkspaceWindowModel model, final String name) {
        for (int menuIndex = 0; menuIndex < model.menuBar().getMenuCount(); menuIndex++) {
            JMenu found = findMenu(model.menuBar().getMenu(menuIndex), name);
            if (found != null) {
                return found;
            }
        }
        throw new AssertionError("Missing menu " + name);
    }

    private static JMenu findMenu(final JMenu menu, final String name) {
        if (name.equals(menu.getName())) {
            return menu;
        }
        for (Component component : menu.getMenuComponents()) {
            if (component instanceof JMenu) {
                JMenu found = findMenu((JMenu) component, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
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

    private static Icon icon(final int width, final int height) {
        final Icon icon = mock(Icon.class);
        when(icon.getIconWidth()).thenReturn(Integer.valueOf(width));
        when(icon.getIconHeight()).thenReturn(Integer.valueOf(height));
        return icon;
    }

    private static Icon paintingIcon(final Color color, final int width, final int height) {
        return new RecordingPaintingIcon(width, height, color);
    }

    private static double averageLuminance(final Icon icon) {
        final int width = icon.getIconWidth();
        final int height = icon.getIconHeight();
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = image.createGraphics();
        try {
            icon.paintIcon(null, graphics, 0, 0);
        }
        finally {
            graphics.dispose();
        }
        double total = 0.0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int argb = image.getRGB(x, y);
                final double alpha = ((argb >>> 24) & 0xFF) / 255.0;
                final double luminance = (0.2126 * ((argb >> 16) & 0xFF)
                    + 0.7152 * ((argb >> 8) & 0xFF) + 0.0722 * (argb & 0xFF)) / 255.0;
                total += alpha * luminance;
            }
        }
        return total / (width * height);
    }

    private static final class Fixture {
        private final GraphWorkspaceController applicationController;
        private final GraphWorkspaceHandle handle;
        private final WorkspaceCloseController closeController;
        private final GraphWorkspaceViewBinding binding;
        private final ListenerRegistration registration;
        private final ListenerRegistration sessionRegistration;
        private final RecentWorkspaceList recentWorkspaces;
        private final Map<String, Icon> iconStubs = new LinkedHashMap<String, Icon>();
        private EdtResources resources;

        private Fixture(GraphWorkspaceController applicationController, GraphWorkspaceHandle handle,
                WorkspaceCloseController closeController, GraphWorkspaceViewBinding binding,
                ListenerRegistration registration, ListenerRegistration sessionRegistration,
                RecentWorkspaceList recentWorkspaces) {
            this.applicationController = applicationController;
            this.handle = handle;
            this.closeController = closeController;
            this.binding = binding;
            this.registration = registration;
            this.sessionRegistration = sessionRegistration;
            this.recentWorkspaces = recentWorkspaces;
        }

        private Fixture stubIcon(final String path, final Icon icon) {
            iconStubs.put(path, icon);
            return this;
        }

        private Fixture answerSidebarRoundTrip() {
            final DisplaySettings[] committed = { binding.currentPresentation().displaySettings() };
            when(handle.execute(any(GraphCommand.class))).thenAnswer(invocation -> {
                final GraphCommand command = invocation.getArgument(0);
                if (command instanceof GraphCommands.Display) {
                    committed[0] = ((GraphCommands.Display) command).settings();
                }
                return null;
            });
            when(binding.currentPresentation())
                .thenAnswer(invocation -> presentation(committed[0], ACTIVE_ID));
            return this;
        }

        private ResourceController resourceController() {
            return resources.controller();
        }

        private GraphWorkspaceWindowModel model() {
            final GraphWorkspaceWindowModel result = modelWithoutLayout();
            result.completeInitialLayout();
            return result;
        }

        private GraphWorkspaceWindowModel modelWithoutLayout() {
            return modelWithoutLayout(message -> { });
        }

        private GraphWorkspaceWindowModel model(final Consumer<String> commandMessageSink) {
            final GraphWorkspaceWindowModel result = modelWithoutLayout(commandMessageSink);
            result.completeInitialLayout();
            return result;
        }

        private GraphWorkspaceWindowModel modelWithoutLayout(final Consumer<String> commandMessageSink) {
            final GraphWorkspaceWindowModel[] result = new GraphWorkspaceWindowModel[1];
            final EdtResources[] edtResources = new EdtResources[1];
            GraphWorkspaceWindow.runOnEdt(new Runnable() {
                @Override
                public void run() {
                    edtResources[0] = new EdtResources(iconStubs);
                    result[0] = new GraphWorkspaceWindowModel(handle, binding, applicationController,
                        () -> OPEN_PATH, closeController, () -> { }, () -> { }, () -> { }, commandMessageSink,
                        recentWorkspaces);
                }
            });
            resources = edtResources[0];
            RESOURCES.add(edtResources[0]);
            return result[0];
        }
    }

    private static final class EdtResources {
        private final MockedStatic<TextUtils> textUtils;
        private final MockedStatic<ResourceController> resourceController;
        private final ResourceController controller;
        private boolean closed;

        private EdtResources() {
            this(Collections.<String, Icon>emptyMap());
        }

        private EdtResources(final Map<String, Icon> iconStubs) {
            closePreviouslyRegisteredResources();
            controller = mock(ResourceController.class);
            resourceController = org.mockito.Mockito.mockStatic(ResourceController.class);
            resourceController.when(ResourceController::getResourceController).thenReturn(controller);
            when(controller.getOptionalIcon(any(String.class)))
                .thenAnswer(invocation -> iconStubs.get(invocation.getArgument(0)));
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
                .thenAnswer(invocation -> formattedText(invocation));
        }

        private ResourceController controller() {
            return controller;
        }

        private void closePreviouslyRegisteredResources() {
            final java.util.Iterator<EdtResources> registered = RESOURCES.iterator();
            while (registered.hasNext()) {
                final EdtResources resource = registered.next();
                if (resource != this) {
                    resource.closeOnEdt();
                }
                registered.remove();
            }
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
