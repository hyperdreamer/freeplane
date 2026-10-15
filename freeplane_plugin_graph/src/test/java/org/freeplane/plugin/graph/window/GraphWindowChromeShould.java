package org.freeplane.plugin.graph.window;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.plaf.basic.BasicSplitPaneUI;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.TextUtils;
import org.freeplane.plugin.graph.command.GraphCommand;
import org.freeplane.plugin.graph.command.GraphCommands;
import org.freeplane.plugin.graph.control.CanvasState;
import org.freeplane.plugin.graph.control.GraphWorkspaceController;
import org.freeplane.plugin.graph.control.GraphWorkspaceHandle;
import org.freeplane.plugin.graph.control.GraphWorkspacePresentation;
import org.freeplane.plugin.graph.control.GraphWorkspaceViewBinding;
import org.freeplane.plugin.graph.control.OperationalStatus;
import org.freeplane.plugin.graph.control.WorkspaceCloseController;
import org.freeplane.plugin.graph.control.WorkspaceSessionStatus;
import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.layout.LayoutFrame;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.workspace.model.DisplaySettings;
import org.freeplane.plugin.graph.workspace.model.Viewport;
import org.junit.Test;
import org.mockito.MockedStatic;

public class GraphWindowChromeShould {
    private void withFixture(final boolean writable, final Consumer<ChromeFixture> test) {
        GraphWorkspaceWindow.runOnEdt(() -> {
            try (ChromeFixture fixture = new ChromeFixture(writable)) {
                test.accept(fixture);
            }
        });
    }

    @Test
    public void hideMakesAllChromeInvisibleAndRemovesDividerSize() {
        withFixture(true, f -> {
            f.model.capture();
            f.model.hide();
            f.model.hide();
            f.paint();
            assertThat(f.visibility()).containsExactly(false, false, false, false, false);
            assertThat(f.split.getDividerSize()).isZero();
            assertThat(f.scroll.getWidth()).isGreaterThanOrEqualTo(f.split.getWidth() - 4);
            f.assertNoCommands();
        });
    }

    @Test
    public void restoreReturnsVisibilityDividerStateAndCollapsedStateExactly() {
        for (boolean collapsed : new boolean[] {false, true}) {
            withFixture(true, f -> {
                f.effective(collapsed);
                f.model.capture();
                f.model.hide();
                f.paint();
                f.model.restore();
                f.assertEffective(collapsed);
                assertThat(f.visibility()).containsExactly(true, true, true, false, true);
                assertThat(f.model.toolbar().settingsButton().isSelected()).isFalse();
                f.assertNoCommands();
            });
        }
    }

    @Test
    public void captureUsesEffectiveStateWhenSettingsDifferFromDisplaySettings() {
        withFixture(true, f -> {
            assertThat(f.stored.mapSidebarWidth()).isEqualTo(241);
            assertThat(f.stored.mapSidebarHidden()).isFalse();
            f.restoreEffective(true);
            f.assertEffective(true);
            assertThat(f.menu().isSelected()).isFalse();
            f.assertNoCommands();
        });
    }

    @Test
    public void automaticRefreshLayoutAndFocusLossCannotOverwriteSidebarOverride() {
        withFixture(true, f -> {
            f.restoreEffective(true);
            Object override = field(f.model, "sidebarOverride");
            f.model.applyPresentation(f.presentation());
            f.model.acceptCanvasState(f.state);
            f.split.revalidate();
            f.split.setSize(1120, 620);
            f.split.setDividerLocation(181);
            f.paint();
            f.focusLost();
            f.model.applySidebarSettings(f.stored);
            assertThat(field(f.model, "sidebarOverride")).isSameAs(override).isNotNull();
            f.assertEffective(true);
            f.assertNoCommands();
        });
    }

    @Test
    public void expandedCollapseButtonReleasesOverrideAndCommitsOnce() {
        for (boolean writable : new boolean[] {true, false}) {
            withFixture(writable, f -> {
                f.restoreEffective(false);
                f.sidebar.collapseButton().doClick(0);
                f.assertReleased(true, writable);
            });
        }
    }

    @Test
    public void collapsedRailRestoreButtonReleasesOverrideAndCommitsOnce() {
        for (boolean writable : new boolean[] {true, false}) {
            withFixture(writable, f -> {
                f.restoreEffective(true);
                assertThat(f.sidebar.rail().restoreButton().getActionListeners()).hasSize(1);
                f.sidebar.rail().restoreButton().doClick(0);
                f.assertReleased(false, writable);
            });
        }
    }

    @Test
    public void mapsSidebarMenuToggleReleasesOverrideAndCommitsOnce() {
        for (boolean writable : new boolean[] {true, false}) {
            withFixture(writable, f -> {
                f.restoreEffective(false);
                f.menu().doClick(0);
                f.assertReleased(true, writable);
            });
        }
    }

    @Test
    public void dividerPressReleasesOverrideBeforeDrag() {
        for (boolean writable : new boolean[] {true, false}) {
            withFixture(writable, f -> {
                f.restoreEffective(false);
                f.split.setEnabled(true);
                f.mouse(MouseEvent.MOUSE_PRESSED, 1);
                assertThat(field(f.model, "sidebarOverride")).isNull();
                assertThat(field(f.model, "sidebarGestureActive")).isEqualTo(true);
                f.split.setDividerLocation(391);
                f.mouse(MouseEvent.MOUSE_RELEASED, 1);
                assertThat(f.split.getDividerLocation()).isEqualTo(391);
                assertThat(f.commands()).hasSize(writable ? 1 : 0);
                assertThat(f.model.appliedSidebarWidth()).isEqualTo(391);
            });
        }
    }

    @Test
    public void dividerDoubleClickReleasesOverrideBeforeReset() {
        for (boolean writable : new boolean[] {true, false}) {
            withFixture(writable, f -> {
                f.restoreEffective(false);
                f.split.setEnabled(true);
                f.mouse(MouseEvent.MOUSE_PRESSED, 2);
                f.mouse(MouseEvent.MOUSE_RELEASED, 2);
                assertThat(field(f.model, "sidebarOverride")).isNull();
                assertThat(f.model.appliedSidebarWidth()).isEqualTo(264);
                assertThat(f.commands()).hasSize(writable ? 1 : 0);
            });
        }
    }

    @Test
    public void keyboardDividerActionReleasesOverrideBeforeMutation() {
        for (String key : new String[] {"selectMin", "selectMax", "negativeIncrement", "positiveIncrement"}) {
            for (boolean writable : new boolean[] {true, false}) {
                withFixture(writable, f -> {
                    Action wrapper = f.split.getActionMap().get(key);
                    assertThat(wrapper).isNotNull();
                    f.restoreEffective(false);
                    f.split.setEnabled(true);
                    // BasicSplitPaneUI's delegate requires keyboard-resize mode to change width.
                    for (java.awt.event.FocusListener listener : f.split.getFocusListeners()) {
                        listener.focusGained(new FocusEvent(f.split, FocusEvent.FOCUS_GAINED));
                    }
                    final int[] mutations = {0};
                    f.split.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, event -> {
                        assertThat(field(f.model, "sidebarOverride")).isNull();
                        mutations[0]++;
                    });
                    wrapper.actionPerformed(new ActionEvent(f.split, ActionEvent.ACTION_PERFORMED, key));
                    assertThat(mutations[0]).isPositive();
                    assertThat(field(f.model, "sidebarOverride")).isNull();
                    assertThat(f.split.getDividerLocation()).isNotEqualTo(317);
                    assertThat(f.commands()).hasSize(writable ? 1 : 0);
                    assertThat(f.model.appliedSidebarWidth()).isEqualTo(f.split.getDividerLocation());
                    f.focusLost();
                    assertThat(f.commands()).hasSize(writable ? 1 : 0);
                });
            }
        }
    }

    @Test
    public void dividerPropertyChangeDoesNotReleaseOverride() {
        withFixture(true, f -> {
            f.restoreEffective(false);
            Object override = field(f.model, "sidebarOverride");
            final int[] events = {0};
            f.split.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, e -> events[0]++);
            f.split.setDividerLocation(193);
            f.paint();
            assertThat(events[0]).isPositive();
            assertThat(field(f.model, "sidebarOverride")).isSameAs(override).isNotNull();
            f.model.applySidebarSettings(f.stored);
            f.assertEffective(false);
            f.assertNoCommands();
        });
    }

    @Test
    public void noGestureFocusLossDoesNotReleaseOverrideOrCommit() {
        withFixture(true, f -> {
            f.restoreEffective(false);
            List<Object> fields = f.mutationFields();
            f.focusLost();
            assertThat(f.mutationFields()).containsExactlyElementsOf(fields);
            f.assertEffective(false);
            f.assertNoCommands();
        });
    }

    @Test
    public void zeroWidthSplitPaneRestoresOnFirstLayout() {
        withFixture(true, f -> {
            f.effective(false);
            f.model.capture();
            f.model.hide();
            f.split.setSize(0, 620);
            f.model.restore();
            f.model.applyPresentation(f.presentation());
            assertThat(field(f.model, "sidebarOverride")).isNotNull();
            f.split.setSize(1100, 620);
            for (java.awt.event.ComponentListener listener : f.split.getComponentListeners()) {
                listener.componentResized(new java.awt.event.ComponentEvent(f.split,
                    java.awt.event.ComponentEvent.COMPONENT_RESIZED));
            }
            f.paint();
            f.assertEffective(false);
            f.assertNoCommands();
        });
    }

    @Test
    public void changedStoredSidebarSettingsReleaseOverrideButIdenticalRefreshDoesNot() {
        for (boolean hiddenChange : new boolean[] {false, true}) {
            withFixture(true, f -> {
                f.restoreEffective(true);
                Object override = field(f.model, "sidebarOverride");
                f.model.applyPresentation(f.presentation());
                assertThat(field(f.model, "sidebarOverride")).isSameAs(override);
                f.stored = settings(hiddenChange ? 241 : 402, hiddenChange);
                f.model.applyPresentation(f.presentation());
                assertThat(field(f.model, "sidebarOverride")).isNull();
                assertThat(f.sidebar.isCollapsed()).isEqualTo(hiddenChange);
                assertThat(f.model.appliedSidebarWidth()).isEqualTo(hiddenChange ? 26 : 402);
                f.assertNoCommands();
            });
        }
    }

    @Test
    public void restoreContinuesAfterOneComponentFails() {
        withFixture(true, f -> {
            f.effective(false);
            javax.swing.JPanel throwing = org.mockito.Mockito.spy(f.model.statusSlot());
            field(f.model, "statusSlot", throwing);
            f.model.capture();
            f.model.hide();
            org.mockito.Mockito.doThrow(new AssertionError("visibility failure"))
                .doCallRealMethod().when(throwing).setVisible(true);
            f.model.restore();
            assertThat(field(f.model, "presentationHidden")).isEqualTo(false);
            f.assertEffective(false);
            assertThat(f.model.toolbar().isVisible()).isTrue();
            assertThat(f.logs).anyMatch(log -> log.contains("visibility failure"));
            f.assertNoCommands();
        });
    }

    @Test
    public void sidebarMutationGuardsReturnBeforeAnyFieldOrComponentChange() {
        for (boolean hidden : new boolean[] {true, false}) {
            withFixture(true, f -> {
                f.restoreEffective(false);
                if (hidden) {
                    f.model.hide();
                }
                List<Object> fields = f.mutationFields();
                List<Object> components = f.componentState();
                f.model.setSidebarCollapsed(true);
                f.model.commitSidebarWidth(442);
                f.model.resetSidebarWidth();
                assertThat(f.mutationFields()).containsExactlyElementsOf(fields);
                assertThat(f.componentState()).containsExactlyElementsOf(components);
                f.assertNoCommands();
            });
        }
    }

    static DisplaySettings settings(final int width, final boolean hidden) {
        return DisplaySettings.of(true, DisplaySettings.CanvasTheme.FOLLOW_FREEPLANE, true, true,
            width, hidden, Collections.emptyList());
    }

    static Object field(final Object owner, final String name) {
        try {
            Field field = findField(owner.getClass(), name);
            field.setAccessible(true);
            return field.get(owner);
        }
        catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    static void field(final Object owner, final String name, final Object value) {
        try {
            Field field = findField(owner.getClass(), name);
            field.setAccessible(true);
            field.set(owner, value);
        }
        catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static Field findField(final Class<?> type, final String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            }
            catch (NoSuchFieldException missing) {
                // Continue through Swing's implementation hierarchy for the keyboard delegate probe.
            }
        }
        throw new NoSuchFieldException(name);
    }

    static final class ChromeFixture implements AutoCloseable {
        final GraphWorkspaceHandle handle = mock(GraphWorkspaceHandle.class);
        final GraphWorkspaceViewBinding binding = mock(GraphWorkspaceViewBinding.class);
        final List<GraphCommand> executed = new ArrayList<>();
        final List<String> logs = new ArrayList<>();
        final MockedStatic<ResourceController> resources;
        final MockedStatic<TextUtils> texts;
        final GraphWorkspaceWindowModel model;
        final JSplitPane split;
        final MapSidebarPanel sidebar;
        final JScrollPane scroll;
        final CanvasState state;
        DisplaySettings stored = settings(241, false);
        int checkpoint;

        ChromeFixture(final boolean writable) {
            resources = org.mockito.Mockito.mockStatic(ResourceController.class);
            resources.when(ResourceController::getResourceController).thenReturn(mock(ResourceController.class));
            texts = org.mockito.Mockito.mockStatic(TextUtils.class);
            texts.when(() -> TextUtils.getText(any(String.class))).thenAnswer(i -> i.getArgument(0));
            texts.when(() -> TextUtils.getText(any(String.class), any(String.class))).thenAnswer(i -> i.getArgument(0));
            texts.when(() -> TextUtils.getRawText(any(String.class))).thenAnswer(i -> i.getArgument(0));
            texts.when(() -> TextUtils.getRawText(any(String.class), any(String.class))).thenAnswer(i -> i.getArgument(0));
            texts.when(() -> TextUtils.format(any(String.class), any(Object[].class)))
                .thenAnswer(i -> i.<String>getArgument(0));
            state = CanvasState.of(0L, GraphProjection.structure(0L, Collections.emptyList(), Collections.emptyList()),
                LayoutFrame.of(0L, LayoutPositions.of(Collections.emptyMap(), Collections.emptyMap()), false, 0),
                GraphGeometry.of(Collections.emptyMap(), Collections.emptyMap()), OperationalStatus.IDLE);
            when(binding.currentViewport()).thenReturn(Viewport.of(0, 0, 1, Collections.emptyList()));
            when(binding.currentCanvasState()).thenReturn(state);
            when(binding.currentSessionStatus()).thenReturn(WorkspaceSessionStatus.empty());
            when(binding.currentMapRows()).thenReturn(Collections.emptyList());
            when(binding.currentPresentation()).thenAnswer(i -> presentation());
            when(binding.isReadOnly()).thenReturn(!writable);
            when(handle.execute(any(GraphCommand.class))).thenAnswer(i -> {
                GraphCommand command = i.getArgument(0);
                executed.add(command);
                if (command instanceof GraphCommands.Display) {
                    stored = ((GraphCommands.Display) command).settings();
                }
                return null;
            });
            model = new GraphWorkspaceWindowModel(handle, binding, mock(GraphWorkspaceController.class),
                () -> Paths.get("unused"), mock(WorkspaceCloseController.class), () -> { }, () -> { },
                () -> { }, logs::add);
            split = (JSplitPane) field(model, "splitPane");
            sidebar = (MapSidebarPanel) split.getLeftComponent();
            scroll = (JScrollPane) split.getRightComponent();
            split.setSize(1100, 620);
            model.applySidebarSettings(stored);
            paint();
            scroll.doLayout();
            model.completeInitialLayout();
            checkpoint = executed.size();
        }

        GraphWorkspacePresentation presentation() {
            return GraphWorkspacePresentation.of(stored, Collections.emptyList());
        }

        void effective(final boolean collapsed) {
            invokeGuard(() -> {
                sidebar.setCollapsed(collapsed);
                split.setDividerSize(9);
                split.setEnabled(false);
                sidebar.setMinimumSize(new Dimension(collapsed ? 26 : 180, 0));
                split.setDividerLocation(317);
                model.settingsPanel().setVisible(false);
                model.toolbar().setSettingsVisible(false);
            });
        }

        void restoreEffective(final boolean collapsed) {
            effective(collapsed);
            model.capture();
            model.hide();
            paint();
            model.restore();
        }

        void invokeGuard(final Runnable action) {
            try {
                Method method = GraphWorkspaceWindowModel.class.getDeclaredMethod("runWithSidebarGuard", Runnable.class);
                method.setAccessible(true);
                method.invoke(model, action);
            }
            catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
        }

        List<Boolean> visibility() {
            return Arrays.asList(model.menuBar().isVisible(), model.toolbar().isVisible(), sidebar.isVisible(),
                model.settingsPanel().isVisible(), model.statusSlot().isVisible());
        }

        List<Object> mutationFields() {
            List<Object> result = new ArrayList<>();
            for (String name : new String[] {"appliedSidebarWidth", "sidebarGestureActive", "sidebarApplyPending",
                    "sidebarApplying", "sessionSidebarWidth", "sessionSidebarHidden", "sidebarOverride"}) {
                result.add(field(model, name));
            }
            return result;
        }

        List<Object> componentState() {
            return Arrays.asList(sidebar.isCollapsed(), sidebar.getMinimumSize(), split.getDividerLocation(),
                split.getDividerSize(), split.isEnabled(), menu().isSelected(), scroll.getMinimumSize());
        }

        void assertEffective(final boolean collapsed) {
            assertThat(sidebar.isCollapsed()).isEqualTo(collapsed);
            assertThat(split.getDividerLocation()).isEqualTo(317);
            assertThat(split.getDividerSize()).isEqualTo(9);
            assertThat(split.isEnabled()).isFalse();
            assertThat(model.appliedSidebarWidth()).isEqualTo(317);
            assertThat(menu().isSelected()).isEqualTo(!collapsed);
            assertThat(field(model, "sidebarOverride")).isNotNull();
        }

        JCheckBoxMenuItem menu() {
            return (JCheckBoxMenuItem) field(model, "mapsSidebarMenuItem");
        }

        List<GraphCommand> commands() {
            return executed.subList(checkpoint, executed.size());
        }

        void assertNoCommands() {
            assertThat(commands()).isEmpty();
        }

        void assertReleased(final boolean collapsed, final boolean writable) {
            assertThat(field(model, "sidebarOverride")).isNull();
            assertThat(sidebar.isCollapsed()).isEqualTo(collapsed);
            assertThat(menu().isSelected()).isEqualTo(!collapsed);
            assertThat(commands()).hasSize(writable ? 1 : 0);
            if (writable) {
                assertThat(commands().get(0)).isInstanceOf(GraphCommands.Display.class);
            }
        }

        void mouse(final int id, final int count) {
            Component divider = ((BasicSplitPaneUI) split.getUI()).getDivider();
            divider.dispatchEvent(new MouseEvent(divider, id, 1L, 0, 2, 2, count, false, MouseEvent.BUTTON1));
        }

        void focusLost() {
            FocusEvent event = new FocusEvent(split, FocusEvent.FOCUS_LOST);
            for (java.awt.event.FocusListener listener : split.getFocusListeners()) {
                listener.focusLost(event);
            }
        }

        void paint() {
            split.doLayout();
            BufferedImage image = new BufferedImage(Math.max(1, split.getWidth()), Math.max(1, split.getHeight()),
                BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try {
                split.paint(graphics);
            }
            finally {
                graphics.dispose();
            }
        }

        @Override
        public void close() {
            model.close();
            texts.close();
            resources.close();
        }
    }
}
