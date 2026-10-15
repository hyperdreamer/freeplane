package org.freeplane.plugin.graph.window;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.KeyboardFocusManager;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JScrollBar;
import javax.swing.KeyStroke;
import javax.swing.JViewport;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
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
import org.freeplane.plugin.graph.canvas.GraphCanvas;
import org.freeplane.plugin.graph.canvas.GraphViewport;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.NodeGeometry;
import org.freeplane.plugin.graph.geometry.EdgePaths;
import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.layout.LayoutFrame;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
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

    @Test public void preservesCenterForEvenCanvasAndEvenExtentGeometry() { assertParity(0, 0); }
    @Test public void preservesCenterForOddCanvasAndEvenExtentGeometry() { assertParity(1, 0); }
    @Test public void preservesCenterForEvenCanvasAndOddExtentGeometry() { assertParity(0, 1); }
    @Test public void preservesCenterForOddCanvasAndOddExtentGeometry() { assertParity(1, 1); }

    private void assertParity(final int canvasParity, final int extentParity) {
        withViewport(p -> {
            p.configure(8000 + canvasParity, 6000 + canvasParity, 600 + extentParity, 400 + extentParity, 713, 419);
            assertThat(p.canvas.getWidth() % 2).isEqualTo(canvasParity);
            assertThat(p.canvas.getHeight() % 2).isEqualTo(canvasParity);
            assertThat(p.viewport.getExtentSize().width % 2).isEqualTo(extentParity);
            assertThat(p.viewport.getExtentSize().height % 2).isEqualTo(extentParity);
            p.capture();
            p.f.model.capture();
            p.f.model.hide();
            p.f.model.restore();
            p.geometry(p.w0.width - 117, p.w0.height + 53, p.e0.width + 201, p.e0.height - 39);
            p.assertTarget();
            GraphViewport visible = p.canvas.visibleViewport();
            assertThat(Math.abs(visible.centerX() - p.center.centerX()) * p.anchor.zoom()).isLessThanOrEqualTo(0.5);
            assertThat(Math.abs(visible.centerY() - p.center.centerY()) * p.anchor.zoom()).isLessThanOrEqualTo(0.5);
            p.f.assertNoCommands();
        });
    }

    @Test public void preservesScrolledCenterWhenTransitionExhaustsScrollRange() {
        for (int x : new int[] {118, 199}) {
            for (int y : new int[] {71, 148}) {
                withViewport(p -> {
                    // The live window has W0=989, E0=672 and P0=118: its
                    // visible center is 40.5 pixels left of the canvas anchor.
                    p.canvas.setPreferredSize(new Dimension(989, 631));
                    p.configure(989, 631, 672, 412, x, y);
                    p.capture();
                    p.hold.compensate();
                    p.viewport.setExtentSize(new Dimension(1914, 1074));
                    p.viewport.doLayout();
                    p.assertVisibleCenter();
                    p.assertTarget();
                    assertThat(p.canvas.viewport()).isEqualTo(p.anchor);
                    p.f.assertNoCommands();
                    // A late publication shrinks the surface back to the extent
                    // and queues the real canvas clamp before its settle callback.
                    p.canvas.setCanvasState(largeState(0, 0));
                    assertThat(field(p.canvas, "pendingViewportClamps")).isEqualTo(1);
                }, p -> {
                    assertThat(field(p.canvas, "pendingViewportClamps")).isEqualTo(0);
                    p.assertVisibleCenter();
                    p.assertTarget();
                    p.clock.advanceTo(2500);
                    p.assertArmed(false);
                    p.viewport.doLayout();
                    p.assertVisibleCenter();
                    // Exit captures the current view and restores the small surface.
                    p.capture();
                    p.canvas.setPreferredSize(new Dimension(989, 631));
                    p.geometry(989, 631, 672, 412);
                    p.hold.compensate();
                    p.assertVisibleCenter();
                    p.assertTarget();
                    p.clock.advanceTo(5000);
                    p.assertArmed(false);
                    p.viewport.doLayout();
                    p.assertVisibleCenter();
                    assertThat(p.canvas.viewport()).isEqualTo(p.anchor);
                    p.f.assertNoCommands();
                });
            }
        }
    }

    @Test public void compensatesLateExtentChangeAfterTransitionReturns() {
        withViewport(p -> {
            p.capture();
            p.hold.compensate();
            p.viewport.setExtentSize(new Dimension(p.e0.width + 200, p.e0.height + 200));
            assertThat(p.events).isPositive();
            p.assertTarget();
            p.assertArmed(true);
            p.f.assertNoCommands();
        });
    }

    @Test public void recomputesAbsolutelyAfterOddShrinkWithoutCompounding() {
        withViewport(p -> {
            Dimension natural = p.canvas.getPreferredSize();
            p.configure(natural.width, natural.height, 600, 400, 37, 37);
            p.capture();
            p.viewport.setExtentSize(new Dimension(601, 401));
            assertThat(p.viewport.getViewPosition()).isEqualTo(new Point(37, 37));
            p.canvas.setCanvasState(largeState(2048 - 1.0 / 3.0, 1536 - 1.0 / 3.0));
            assertThat(p.canvas.getWidth()).isEqualTo(p.w0.width - 1);
            assertThat(p.canvas.getHeight()).isEqualTo(p.w0.height - 1);
        }, p -> {
            assertThat(p.viewport.getViewPosition()).isEqualTo(new Point(36, 36));
            p.assertTarget();
            p.f.assertNoCommands();
        });
    }

    @Test public void logsClampResidualAndPinsContentEdge() {
        withViewport(p -> {
            p.configure(8000, 6000, 600, 400, 1, 1);
            p.capture();
            p.geometry(7000, 5000, 1600, 1400);
            assertThat(p.viewport.getViewPosition()).isEqualTo(new Point());
            assertThat(p.f.logs).anyMatch(s -> s.contains("requested=") && s.contains("applied=0") && s.contains("residual="));
            p.hold.disarm();
            p.f.model.acceptCanvasState(p.f.state);
            p.configure(100, 100, 600, 400, 0, 0);
            p.capture();
            p.geometry(90, 90, 700, 500);
            p.assertTarget();
            assertThat(p.canvas.viewport()).isEqualTo(p.anchor);
            p.f.assertNoCommands();
        });
    }

    @Test public void unchangedCanvasDeliveryDoesNotPublishOrMoveCenter() {
        withViewport(p -> {
            // Use the canvas's naturally sized surface for an unchanged delivery.
            p.f.model.acceptCanvasState(p.largeState);
            p.canvas.setViewport(p.anchor);
        }, p -> {
            p.checkpoint();
            p.capture();
            p.f.model.acceptCanvasState(p.largeState);
            p.assertTarget();
            assertThat(p.canvas.visibleViewport()).isEqualTo(p.center);
            p.f.assertNoCommands();
        });
    }

    @Test public void classifiesAnchorChangeAsUserBeforePositioningMarker() {
        withViewport(p -> {
            p.capture();
            field(p.canvas, "viewportPositioningDepth", 1);
            p.canvas.resetZoom();
            assertThat(p.canvas.viewport()).isNotEqualTo(p.anchor);
            p.assertOneViewportCommand();
            p.assertArmed(false);
            field(p.canvas, "viewportPositioningDepth", 0);
            Point user = p.viewport.getViewPosition();
            p.hold.compensate();
            assertThat(p.viewport.getViewPosition()).isEqualTo(user);
        });
    }

    @Test public void compensatesPositionOnlyLayoutEventWithoutPublishingOrDisarming() {
        withViewport(p -> {
            p.capture();
            long generation = (Long) field(p.hold, "generation");
            p.viewport.setViewPosition(new Point(p.p0.x + 31, p.p0.y + 19));
            assertThat(p.events).isPositive();
            assertThat(p.viewport.getExtentSize()).isEqualTo(p.e0);
            assertThat(p.canvas.getSize()).isEqualTo(p.w0);
            p.assertTarget();
            p.assertArmed(true);
            assertThat(field(p.hold, "generation")).isEqualTo(generation);
            p.f.assertNoCommands();
        });
    }

    @Test public void classifiesScrollbarDragAsUserWhenExtentAndSurfaceAreUnchanged() {
        withViewport(p -> {
            p.capture();
            Point next = new Point(p.p0.x + 31, p.p0.y + 19);
            p.userScrollTo(next);
            assertThat(p.events).isPositive();
            p.assertOneViewportCommand();
            p.assertArmed(false);
            p.hold.compensate();
            assertThat(p.viewport.getViewPosition()).isEqualTo(next);
        });
    }

    @Test public void classifiesKeyboardScrollAsUserWhenPendingClampExistsWithoutActivePositioning() {
        withViewport(p -> {
            p.capture();
            invoke(p.canvas, "scheduleViewportClamp", JViewport.class, p.viewport);
            assertThat(field(p.canvas, "pendingViewportClamps")).isEqualTo(1);
            assertThat(p.canvas.isViewportPositioningActive()).isFalse();
            Action scroll = p.f.scroll.getActionMap().get("unitScrollRight");
            assertThat(scroll).isNotNull();
            p.paneKey(KeyEvent.VK_RIGHT, true);
            assertThat(p.viewport.getViewPosition()).isNotEqualTo(p.p0);
            p.assertOneViewportCommand();
            p.assertArmed(false);
        }, p -> {
            assertThat(field(p.canvas, "pendingViewportClamps")).isEqualTo(0);
            p.assertOneViewportCommand();
        });
    }

    @Test public void scrollbarPressPublishesOnceBeforeUiAdjustmentOnEitherAxis() {
        for (boolean horizontal : new boolean[] {false, true}) {
            withViewport(p -> {
                JScrollBar bar = horizontal ? p.f.scroll.getHorizontalScrollBar()
                    : p.f.scroll.getVerticalScrollBar();
                Component arrow = bar.getComponent(0);
                p.capture();
                arrow.dispatchEvent(new MouseEvent(arrow, MouseEvent.MOUSE_PRESSED,
                    1L, 0, 2, 2, 1, false, MouseEvent.BUTTON1));
                arrow.dispatchEvent(new MouseEvent(arrow, MouseEvent.MOUSE_RELEASED,
                    2L, 0, 2, 2, 1, false, MouseEvent.BUTTON1));
                assertThat(p.viewport.getViewPosition()).isNotEqualTo(p.p0);
                p.assertOneViewportCommand();
                p.assertArmed(false);
                assertThat(field(p.f.model, "userScrollDepth")).isEqualTo(0);
                assertThat(bar.getValueIsAdjusting()).isFalse();
                p.checkpoint();
                p.capture();
                p.viewport.setViewPosition(new Point(p.p0.x + 31, p.p0.y + 19));
                p.assertTarget();
                p.assertArmed(true);
                p.f.assertNoCommands();
            });
        }
    }

    @Test public void adjustingScrollbarPublishesOnceOnEitherAxisAndClearsMarker() {
        for (boolean horizontal : new boolean[] {false, true}) {
            withViewport(p -> {
                JScrollBar bar = horizontal ? p.f.scroll.getHorizontalScrollBar()
                    : p.f.scroll.getVerticalScrollBar();
                p.capture();
                bar.setValueIsAdjusting(true);
                assertThat(bar.getValueIsAdjusting()).isTrue();
                bar.setValue(bar.getValue() + 31);
                bar.setValueIsAdjusting(false);
                assertThat(p.viewport.getViewPosition()).isNotEqualTo(p.p0);
                p.assertOneViewportCommand();
                p.assertArmed(false);
                assertThat(field(p.f.model, "userScrollDepth")).isEqualTo(0);
                p.checkpoint();
                p.capture();
                p.viewport.setViewPosition(new Point(p.p0.x + 31, p.p0.y + 19));
                p.assertTarget();
                p.assertArmed(true);
                p.f.assertNoCommands();
            });
        }
    }

    @Test public void classifiesPaneWheelScrollAsUserAndClearsTransientMarker() {
        withViewport(p -> {
            p.capture();
            p.f.scroll.dispatchEvent(new MouseWheelEvent(p.f.scroll, MouseEvent.MOUSE_WHEEL,
                1L, 0, 20, 20, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, 1));
            assertThat(p.viewport.getViewPosition()).isNotEqualTo(p.p0);
            p.assertOneViewportCommand();
            p.assertArmed(false);
            assertThat(field(p.f.model, "userScrollDepth")).isEqualTo(0);
            p.checkpoint();
            p.capture();
            p.viewport.setViewPosition(new Point(p.p0.x + 31, p.p0.y + 19));
            p.assertTarget();
            p.assertArmed(true);
            p.f.assertNoCommands();
        });
    }

    @Test public void keyboardPaneNavigationPublishesOnceOnlyWhenPaneHasFocus() {
        for (int key : new int[] {KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_UP, KeyEvent.VK_DOWN,
                KeyEvent.VK_PAGE_UP, KeyEvent.VK_PAGE_DOWN, KeyEvent.VK_HOME, KeyEvent.VK_END}) {
            for (boolean paneFocus : new boolean[] {true, false}) {
                withViewport(p -> {
                    p.capture();
                    p.paneKey(key, paneFocus);
                    if (paneFocus) {
                        assertThat(p.viewport.getViewPosition()).isNotEqualTo(p.p0);
                        p.assertOneViewportCommand();
                        p.assertArmed(false);
                    }
                    else {
                        p.assertTarget();
                        p.assertArmed(true);
                        p.f.assertNoCommands();
                    }
                    assertThat(field(p.f.model, "userScrollDepth")).isEqualTo(0);
                    p.checkpoint();
                    p.capture();
                    p.viewport.setViewPosition(new Point(p.p0.x + 31, p.p0.y + 19));
                    p.assertTarget();
                    p.assertArmed(true);
                    p.f.assertNoCommands();
                });
            }
        }
    }

    @Test public void ignoresActiveProgrammaticPositioningAndKeepsHoldArmed() {
        withViewport(p -> {
            p.capture();
            field(p.canvas, "viewportPositioningDepth", 1);
            p.viewport.setViewPosition(new Point(714, 420));
            assertThat(p.events).isPositive();
            p.assertArmed(true);
            assertThat(field(p.canvas, "pendingViewportSettledCallback")).isNotNull();
            field(p.canvas, "viewportPositioningDepth", 0);
            p.f.assertNoCommands();
        });
    }

    @Test public void toolbarPublicationPublishesBeforeDisarm() {
        withViewport(p -> {
            p.capture();
            final int[] observed = {0};
            org.mockito.Mockito.doAnswer(i -> {
                p.assertArmed(true);
                assertThat(field(p.f.model, "lastPublishedViewport")).isNotEqualTo(p.center);
                p.f.executed.add(i.getArgument(0));
                observed[0]++;
                return null;
            }).when(p.f.handle).execute(any(GraphCommand.class));
            javax.swing.event.ChangeListener listener = p.listener();
            p.viewport.removeChangeListener(listener);
            p.f.model.toolbar().zoomInButton().doClick(0);
            p.viewport.addChangeListener(listener);
            assertThat(observed[0]).isEqualTo(1);
            p.assertOneViewportCommand();
            p.assertArmed(false);
            Point user = p.viewport.getViewPosition();
            p.hold.compensate();
            assertThat(p.viewport.getViewPosition()).isEqualTo(user);
        });
    }

    @Test public void canvasPublicationPublishesBeforeDedupeReconcileAndDisarm() {
        withViewport(p -> {
            p.capture();
            GraphViewport changed = GraphViewport.of(231, -142, 1.75);
            // Direct callback has no obligation to deliver a JViewport event first.
            field(p.canvas, "viewport", changed);
            final int[] observed = {0};
            org.mockito.Mockito.doAnswer(i -> {
                p.assertArmed(true);
                p.f.executed.add(i.getArgument(0));
                observed[0]++;
                return null;
            }).when(p.f.handle).execute(any(GraphCommand.class));
            ((Runnable) field(p.canvas, "userViewportChangeListener")).run();
            assertThat(observed[0]).isEqualTo(1);
            p.assertOneViewportCommand();
            p.assertArmed(false);
            assertThat(p.canvas.viewport()).isEqualTo(changed);
        });
    }

    @Test public void settleCallbackCorrectsAfterClampWithoutFinalViewportEvent() {
        withViewport(p -> {
            Dimension natural = p.canvas.getPreferredSize();
            p.configure(natural.width, natural.height, 600, 400, 37, 37);
            p.capture();
            p.viewport.setExtentSize(new Dimension(599, 399));
            assertThat(p.viewport.getViewPosition()).isEqualTo(new Point(38, 38));
            // Isolate the silent-clamp case: the queued component-size notification is
            // deliberately absent, while resizeSurface and its real clamp queue run.
            for (java.awt.event.ComponentListener listener : p.canvas.getComponentListeners()) {
                p.canvas.removeComponentListener(listener);
            }
            p.canvas.addPropertyChangeListener("preferredSize", event -> {
                assertThat(p.canvas.isViewportPositioningActive()).isTrue();
                p.viewport.setViewPosition(new Point(39, 39));
                p.viewport.setViewPosition(new Point(38, 38));
            });
            p.canvas.setCanvasState(largeState(2048 - 1.0 / 3.0, 1536 - 1.0 / 3.0));
            assertThat(p.canvas.getWidth()).isEqualTo(p.w0.width - 1);
            assertThat(p.canvas.getHeight()).isEqualTo(p.w0.height - 1);
            assertThat(p.viewport.getViewPosition()).isEqualTo(new Point(38, 38));
            assertThat(field(p.canvas, "pendingViewportClamps")).isEqualTo(1);
            assertThat(p.savedCallback()).isNotNull();
            p.events = 0;
        }, p -> {
            // Clamp at 38 emits no event. Only completion correction changes 38 to 37.
            assertThat(field(p.canvas, "pendingViewportClamps")).isEqualTo(0);
            assertThat(p.events).isEqualTo(1);
            assertThat(p.viewport.getViewPosition()).isEqualTo(new Point(37, 37));
            p.assertTarget();
            p.f.assertNoCommands();
        });
    }

    @Test public void settleCallbackRegistrationIsIdempotentPerGeneration() {
        withViewport(p -> {
            p.capture();
            field(p.canvas, "viewportPositioningDepth", 1);
            p.viewport.setViewPosition(new Point(714, 420));
            Runnable first = p.savedCallback();
            p.viewport.setViewPosition(new Point(715, 421));
            assertThat(p.savedCallback()).isSameAs(first);
            field(p.canvas, "viewportPositioningDepth", 0);
            first.run();
            p.assertTarget();
            p.f.assertNoCommands();
        });
    }

    @Test public void settleCallbackRunsImmediatelyWhenAlreadySettled() {
        withViewport(p -> {
            p.capture();
            // The registration seam itself must tolerate inline canvas completion.
            p.viewport.removeChangeListener(p.listener());
            p.viewport.setViewSize(new Dimension(7999, 5999));
            assertThat(p.canvas.isProgrammaticViewportChange()).isFalse();
            invoke(p.hold, "registerSettleCorrection");
            assertThat(SwingUtilities.isEventDispatchThread()).isTrue();
            p.assertTarget();
            assertThat(field(p.canvas, "pendingViewportSettledCallback")).isNull();
            p.f.assertNoCommands();
        });
    }

    @Test public void userDisarmCancelsSettleCallback() {
        withViewport(p -> {
            p.capture();
            p.beginPositioning();
            Runnable old = p.savedCallback();
            field(p.canvas, "viewportPositioningDepth", 0);
            Point user = new Point(933, 627);
            p.userScrollTo(user);
            p.assertArmed(false);
            old.run();
            assertThat(p.viewport.getViewPosition()).isEqualTo(user);
            p.assertOneViewportCommand();
        });
    }

    @Test public void closeCancelsSettleCallbackAndIgnoresLateGeneration() {
        withViewport(p -> {
            p.capture();
            p.beginPositioning();
            Runnable old = p.savedCallback();
            field(p.canvas, "viewportPositioningDepth", 0);
            p.capture();
            javax.swing.event.ChangeListener listener = p.listener();
            p.viewport.removeChangeListener(listener);
            Point newer = new Point(919, 717);
            p.viewport.setViewPosition(newer);
            old.run();
            assertThat(p.viewport.getViewPosition()).isEqualTo(newer);
            p.viewport.addChangeListener(listener);
            p.assertArmed(true);
            p.beginPositioning();
            Runnable closing = p.savedCallback();
            field(p.canvas, "viewportPositioningDepth", 0);
            p.f.model.close();
            Point closed = p.viewport.getViewPosition();
            closing.run();
            old.run();
            p.hold.capture();
            p.hold.compensate();
            p.assertArmed(false);
            assertThat(p.viewport.getViewPosition()).isEqualTo(closed);
            p.f.assertNoCommands();
        });
    }

    @Test public void modelAppliedViewportDoesNotPublishOrDisarm() {
        withViewport(p -> {
            p.capture();
            long generation = (Long) field(p.hold, "generation");
            p.clock.advanceTo(1000);
            GraphViewport different = GraphViewport.of(-711, 221, 2.0);
            invoke(p.f.model, "applyModelViewport", Runnable.class, (Runnable) () -> p.canvas.setViewport(different));
            assertThat(p.canvas.viewport()).isEqualTo(different);
            assertThat(field(p.hold, "generation")).isEqualTo(generation + 1);
            assertThat(field(p.hold, "baselineAnchor")).isEqualTo(different);
            assertThat(field(p.hold, "lastTransitionGeometryAt")).isEqualTo(0L);
            assertThat(p.events).isPositive();
            p.assertArmed(true);
            p.f.assertNoCommands();
        }, p -> {
            p.assertArmed(true);
            p.f.assertNoCommands();
            p.clock.advanceTo(2500);
            p.assertArmed(false);
            p.f.assertNoCommands();
        });
    }

    @Test public void exitRecapturesCurrentViewAfterEntryHoldDisarmed() {
        withViewport(p -> {
            p.capture();
            p.f.model.toolbar().zoomInButton().doClick(0);
            p.assertArmed(false);
        }, p -> {
            p.checkpoint();
            p.capture();
            GraphViewport exiting = p.center;
            p.viewport.setExtentSize(new Dimension(p.e0.width + 200, p.e0.height + 100));
            p.assertTarget();
            assertThat(p.canvas.visibleViewport().zoom()).isEqualTo(exiting.zoom());
            assertThat(Math.abs(p.canvas.visibleViewport().centerX() - exiting.centerX()) * exiting.zoom())
                .isLessThanOrEqualTo(0.5);
            p.f.assertNoCommands();
        });
    }

    @Test public void publishedViewportIsNotSwallowedByReconcileBeforeDisarm() {
        withViewport(p -> {
            p.capture();
            GraphViewport before = (GraphViewport) field(p.f.model, "lastPublishedViewport");
            p.userScrollTo(new Point(1044, 733));
            p.assertOneViewportCommand();
            GraphCommands.Viewport command = (GraphCommands.Viewport) p.f.commands().get(0);
            assertThat(command.viewport()).isEqualTo(p.canvas.visibleViewport().toPersisted());
            assertThat(p.canvas.visibleViewport()).isNotEqualTo(before);
            p.assertArmed(false);
        });
    }

    @Test public void holdNeverDisarmsBeforeTwoThousandFiveHundredMilliseconds() {
        withViewport(p -> {
            p.capture();
            p.hold.compensate();
            p.clock.advanceTo(2499);
            p.assertArmed(true);
            p.f.assertNoCommands();
        });
    }

    @Test public void holdDisarmsOnFirstQuietWindowAtOrAfterPlatformHorizon() {
        withViewport(p -> {
            p.capture();
            p.clock.advanceTo(2450);
            p.f.model.presentationFrameGeometryChanged(new Rectangle(10, 10, 1280, 800));
            p.clock.advanceTo(2500);
            p.assertArmed(true);
            p.clock.advanceTo(2649);
            p.assertArmed(true);
            p.clock.advanceTo(2650);
            p.assertArmed(false);
            p.f.assertNoCommands();
        });
    }

    @Test public void holdReleasesAtFiveSecondCapAndLogsSettlementFailure() {
        withViewport(p -> {
            p.capture();
            for (int time = 150; time < 5000; time += 150) {
                p.clock.advanceTo(time);
                p.f.model.presentationFrameGeometryChanged(new Rectangle(time, 10, 1280, 800));
            }
            p.clock.advanceTo(4999);
            p.assertArmed(true);
            p.clock.advanceTo(5000);
            p.assertArmed(false);
            assertThat(p.f.logs).anyMatch(s -> s.contains("settlement failure") && s.contains("generation=")
                && s.contains("frame=") && s.contains("viewport="));
            p.f.assertNoCommands();
        });
    }

    @Test public void quietWindowFloorRestartsAfterLastTransitionGeometry() {
        withViewport(p -> {
            p.capture();
            FakePresentationScheduler.Work entry = p.clock.last();
            p.clock.advanceTo(1000);
            p.hold.compensate();
            assertThat(entry.cancelled).isTrue();
            p.clock.advanceTo(3499);
            p.assertArmed(true);
            assertThat(field(p.f.model, "suppressViewportEvents")).isEqualTo(true);
            p.clock.advanceTo(3500);
            p.assertArmed(false);
            assertThat(field(p.f.model, "suppressViewportEvents")).isEqualTo(false);
            assertThat(p.f.logs).noneMatch(s -> s.contains("settlement failure"));
            p.f.assertNoCommands();
        });
    }

    @Test public void lateViewportEventsIndependentlyDelayQuietWindowAndHitCap() {
        withViewport(p -> {
            p.capture();
            long generation = (Long) field(p.hold, "generation");
            for (int time = 150; time < 5000; time += 150) {
                p.clock.advanceTo(time);
                p.viewport.setExtentSize(new Dimension(600 + (time / 150) % 2, 400));
                p.assertArmed(true);
            }
            p.clock.advanceTo(4999);
            p.assertArmed(true);
            assertThat(p.f.logs).noneMatch(s -> s.contains("settlement failure"));
            p.clock.advanceTo(5000);
            p.assertArmed(false);
            assertThat(p.f.logs.stream().filter(s -> s.contains("settlement failure")))
                .containsExactly("Graph presentation settlement failure generation=" + generation
                    + " transition=0 frame=0 viewport=4950 now=5000");
            assertThat(field(p.f.model, "suppressViewportEvents")).isEqualTo(false);
            p.clock.advanceTo(10000);
            assertThat(p.f.logs.stream().filter(s -> s.contains("settlement failure")).count()).isEqualTo(1);
            p.f.assertNoCommands();
        });
    }

    @Test public void newGenerationReplacesPendingSettleCallbackAndRunsCorrectionOnce() {
        final int[] callbacks = {0};
        withViewport(p -> {
            p.capture();
            p.beginPositioning();
            Runnable stale = p.savedCallback();
            long previous = (Long) field(p.hold, "generation");
            FakePresentationScheduler.Work previousQuiet = p.clock.last();
            p.capture();
            assertThat(previousQuiet.cancelled).isTrue();
            assertThat(field(p.hold, "generation")).isEqualTo(previous + 1);
            p.beginPositioning();
            Runnable current = p.savedCallback();
            assertThat(current).isNotSameAs(stale);
            assertThat(field(p.hold, "registeredSettleGeneration")).isEqualTo(previous + 1);
            p.viewport.setViewPosition(new Point(p.p0.x + 17, p.p0.y + 13));
            assertThat(p.savedCallback()).isSameAs(current);
            Point pending = p.viewport.getViewPosition();
            stale.run();
            assertThat(p.viewport.getViewPosition()).isEqualTo(pending);
            invoke(p.canvas, "scheduleViewportClamp", JViewport.class, p.viewport);
            assertThat(field(p.canvas, "pendingViewportClamps")).isEqualTo(1);
            field(p.canvas, "viewportPositioningDepth", 0);
            invoke(p.canvas, "drainViewportSettledCallback");
            assertThat(p.savedCallback()).isSameAs(current);
            assertThat(p.viewport.getViewPosition()).isEqualTo(pending);
            field(p.canvas, "pendingViewportSettledCallback", (Runnable) () -> {
                callbacks[0]++;
                current.run();
            });
            assertThat(callbacks[0]).isZero();
            p.events = 0;
        }, p -> {
            assertThat(field(p.canvas, "pendingViewportClamps")).isEqualTo(0);
            assertThat(field(p.canvas, "pendingViewportSettledCallback")).isNull();
            assertThat(callbacks[0]).isEqualTo(1);
            p.assertTarget();
            int eventsAfterCorrection = p.events;
            assertThat(eventsAfterCorrection).isPositive();
            invoke(p.canvas, "drainViewportSettledCallback");
            assertThat(callbacks[0]).isEqualTo(1);
            assertThat(p.events).isEqualTo(eventsAfterCorrection);
            p.assertArmed(true);
            p.f.assertNoCommands();
        });
    }

    @Test public void closeWithPendingClampCancelsSettleCorrectionAndQuietWork() {
        withViewport(p -> {
            p.capture();
            p.beginPositioning();
            Runnable stale = p.savedCallback();
            FakePresentationScheduler.Work quiet = p.clock.last();
            long generation = (Long) field(p.hold, "generation");
            invoke(p.canvas, "scheduleViewportClamp", JViewport.class, p.viewport);
            assertThat(field(p.canvas, "pendingViewportClamps")).isEqualTo(1);
            field(p.canvas, "viewportPositioningDepth", 0);
            p.f.model.close();
            assertThat(quiet.cancelled).isTrue();
            assertThat(field(p.hold, "generation")).isEqualTo(generation + 1);
            assertThat(field(p.hold, "registeredSettleGeneration")).isEqualTo(-1L);
            assertThat(p.savedCallback()).isNotSameAs(stale);
            p.p0 = p.viewport.getViewPosition();
            stale.run();
            quiet.callback.run();
            p.clock.advanceTo(10000);
            p.assertArmed(false);
            assertThat(p.viewport.getViewPosition()).isEqualTo(p.p0);
            p.f.assertNoCommands();
        }, p -> {
            assertThat(field(p.canvas, "pendingViewportClamps")).isEqualTo(0);
            assertThat(field(p.canvas, "pendingViewportSettledCallback")).isNull();
            assertThat(p.viewport.getViewPosition()).isEqualTo(p.p0);
            p.assertArmed(false);
            assertThat(p.f.logs).noneMatch(s -> s.contains("settlement failure"));
            p.f.assertNoCommands();
        });
    }

    @Test public void newTransitionCancelsPreviousQuietWindowGeneration() {
        withViewport(p -> {
            p.capture();
            Runnable old = p.clock.last().callback;
            long generation = (Long) field(p.hold, "generation");
            p.clock.advanceTo(1000);
            p.capture();
            assertThat((Long) field(p.hold, "generation")).isGreaterThan(generation);
            p.clock.advanceTo(2500);
            int observations = p.clock.work.size();
            old.run();
            assertThat(p.clock.work).hasSize(observations);
            p.assertArmed(true);
            p.clock.advanceTo(3500);
            p.assertArmed(false);
            assertThat(field(p.f.model, "suppressViewportEvents")).isEqualTo(false);
            p.f.assertNoCommands();
        });
    }

    @Test public void closeCancelsQuietWindowGeneration() {
        withViewport(p -> {
            p.capture();
            Runnable old = p.clock.last().callback;
            p.f.model.close();
            assertThat(p.clock.last().cancelled).isTrue();
            p.clock.advanceTo(6000);
            old.run();
            p.assertArmed(false);
            p.f.assertNoCommands();
        });
    }

    @Test public void disarmReconcilesWithoutPublishing() {
        withViewport(p -> {
            p.capture();
            p.geometry(8020, 6020, 700, 500);
            p.hold.disarm();
            assertThat(field(p.f.model, "lastPublishedViewport")).isEqualTo(p.canvas.visibleViewport());
            assertThat(field(p.f.model, "suppressViewportEvents")).isEqualTo(false);
            p.assertArmed(false);
            p.f.assertNoCommands();
        });
    }

    @Test public void subsequentIndependentResizeUsesBaselineAfterHoldDisarms() {
        withViewport(p -> {
            p.capture();
            p.clock.advanceTo(2500);
            p.assertArmed(false);
            p.viewport.setExtentSize(new Dimension(800, 500));
            assertThat(p.events).isPositive();
            p.assertOneViewportCommand();
        });
    }

    @Test public void presentationEnterAndExitEmitNoWorkspaceCommandInWritableMode() { assertModeNoCommands(true); }
    @Test public void presentationEnterAndExitEmitNoWorkspaceCommandInReadOnlyMode() { assertModeNoCommands(false); }

    private void assertModeNoCommands(final boolean writable) {
        withViewport(writable, p -> {
            ScreenFillSurface surface = new ScreenFillSurface() {
                @Override public FrameState captureFrameState() { return new FrameState(new Rectangle(1, 2, 800, 600), 0); }
                @Override public Survival applyScreenFill(FrameState previous) {
                    p.geometry(8111, 6073, 1001, 701);
                    return Survival.REENTERED_FULLSCREEN;
                }
                @Override public Survival restoreFrameState(FrameState previous) {
                    p.geometry(8000, 6000, 600, 400);
                    return Survival.RESTORED_WINDOWED;
                }
            };
            GraphPresentationMode mode = new GraphPresentationMode(surface, p.f.model, p.hold,
                new PresentationHint(new JLayeredPane(), "exit"), new JCheckBoxMenuItem(), () -> { }, p.f.logs::add, true);
            mode.toggle();
            assertThat(mode.isActive()).isTrue();
            p.viewport.setExtentSize(new Dimension(1100, 800));
            mode.requestExit();
            assertThat(mode.isActive()).isFalse();
            p.viewport.setExtentSize(new Dimension(611, 411));
            p.f.assertNoCommands();
            mode.windowClosed();
        }, p -> p.f.assertNoCommands());
    }

    @Test public void displaySettingsAndViewportCommandMutantsFailTheNoCommandAssertion() {
        withViewport(p -> {
            p.f.assertNoCommands();
            p.f.handle.execute(GraphCommands.display(p.f.stored));
            org.assertj.core.api.Assertions.assertThatThrownBy(p.f::assertNoCommands).isInstanceOf(AssertionError.class);
            p.checkpoint();
            p.f.handle.execute(GraphCommands.viewport(p.canvas.visibleViewport().toPersisted()));
            org.assertj.core.api.Assertions.assertThatThrownBy(p.f::assertNoCommands).isInstanceOf(AssertionError.class);
        });
    }

    private void withViewport(final Consumer<ViewportProbe> test) { withViewport(test, p -> { }); }
    private void withViewport(final Consumer<ViewportProbe> test, final Consumer<ViewportProbe> afterDrain) {
        withViewport(true, test, afterDrain);
    }
    private void withViewport(final boolean writable, final Consumer<ViewportProbe> test,
            final Consumer<ViewportProbe> afterDrain) {
        final ViewportProbe[] probe = {null};
        try {
            GraphWorkspaceWindow.runOnEdt(() -> probe[0] = new ViewportProbe(writable));
            drainEdt();
            GraphWorkspaceWindow.runOnEdt(() -> { probe[0].initialize(); test.accept(probe[0]); });
            drainEdt();
            GraphWorkspaceWindow.runOnEdt(() -> afterDrain.accept(probe[0]));
        }
        finally {
            if (probe[0] != null) {
                GraphWorkspaceWindow.runOnEdt(() -> probe[0].f.close());
            }
            drainEdt();
        }
    }

    private static void drainEdt() {
        for (int turn = 0; turn < 4; turn++) { GraphWorkspaceWindow.runOnEdt(() -> { }); }
    }

    static void invoke(final Object target, final String name) { invoke(target, name, null, null); }
    static void invoke(final Object target, final String name, final Class<?> parameter, final Object value) {
        try {
            Method method = parameter == null ? target.getClass().getDeclaredMethod(name)
                : target.getClass().getDeclaredMethod(name, parameter);
            method.setAccessible(true);
            if (parameter == null) { method.invoke(target); }
            else { method.invoke(target, value); }
        }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static final class ViewportProbe {
        final ChromeFixture f;
        final GraphCanvas canvas;
        final JViewport viewport;
        final PresentationViewport hold;
        final FakePresentationScheduler clock = new FakePresentationScheduler();
        final GraphViewport anchor = GraphViewport.of(123.5, -87.25, 1.5);
        final CanvasState largeState = largeState();
        Point p0;
        Dimension w0;
        Dimension e0;
        GraphViewport center;
        int events;

        ViewportProbe(final boolean writable) {
            f = new ChromeFixture(writable);
            canvas = f.model.canvas();
            viewport = f.scroll.getViewport();
            hold = f.model.presentationViewport();
            f.model.setPresentationScheduler(clock);
            f.model.acceptCanvasState(largeState);
            canvas.setViewport(anchor);
            viewport.addChangeListener(event -> events++);
        }

        void initialize() { configure(8000, 6000, 600, 400, 713, 419); checkpoint(); }
        void checkpoint() { f.checkpoint = f.executed.size(); field(f.model, "lastPublishedViewport", canvas.visibleViewport()); }
        void configure(final int width, final int height, final int extentX, final int extentY, final int x, final int y) {
            viewport.setExtentSize(new Dimension(extentX, extentY));
            viewport.setViewSize(new Dimension(width, height));
            viewport.setViewPosition(new Point(x, y));
            checkpoint();
        }
        void capture() {
            p0 = viewport.getViewPosition();
            w0 = canvas.getSize();
            e0 = viewport.getExtentSize();
            center = canvas.visibleViewport();
            events = 0;
            hold.capture();
            assertArmed(true);
        }
        void geometry(final int width, final int height, final int extentX, final int extentY) {
            viewport.setExtentSize(new Dimension(extentX, extentY));
            viewport.setViewSize(new Dimension(width, height));
            assertThat(events).isPositive();
        }
        void assertArmed(final boolean armed) { assertThat(field(hold, "armed")).isEqualTo(armed); }
        void assertVisibleCenter() {
            GraphViewport visible = canvas.visibleViewport();
            assertThat(Math.abs(visible.centerX() - center.centerX()) * center.zoom()).isLessThanOrEqualTo(0.5);
            assertThat(Math.abs(visible.centerY() - center.centerY()) * center.zoom()).isLessThanOrEqualTo(0.5);
            assertThat(visible.zoom()).isEqualTo(center.zoom());
        }
        void assertTarget() {
            Point expected = new Point(axis(p0.x, w0.width, e0.width, canvas.getWidth(), viewport.getExtentSize().width),
                axis(p0.y, w0.height, e0.height, canvas.getHeight(), viewport.getExtentSize().height));
            assertThat(viewport.getViewPosition()).isEqualTo(expected);
            assertThat(canvas.viewport().zoom()).isEqualTo(center.zoom());
        }
        int axis(final int p, final int w, final int e, final int nextW, final int nextE) {
            return (int) Math.max(0L, Math.min((long) Math.max(0, nextW - nextE),
                Math.round(p + (((double) nextW - w) - ((double) nextE - e)) / 2.0)));
        }
        void assertOneViewportCommand() {
            assertThat(f.commands()).hasSize(1);
            assertThat(f.commands().get(0)).isInstanceOf(GraphCommands.Viewport.class);
        }
        void userScrollTo(final Point position) {
            JScrollBar bar = f.scroll.getHorizontalScrollBar();
            bar.setValueIsAdjusting(true);
            try {
                viewport.setViewPosition(position);
            }
            finally {
                bar.setValueIsAdjusting(false);
            }
        }
        void paneKey(final int key, final boolean paneFocus) {
            KeyboardFocusManager manager = mock(KeyboardFocusManager.class);
            when(manager.getFocusOwner()).thenReturn(paneFocus ? f.scroll : canvas);
            try (MockedStatic<KeyboardFocusManager> focus = org.mockito.Mockito.mockStatic(
                    KeyboardFocusManager.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
                focus.when(KeyboardFocusManager::getCurrentKeyboardFocusManager).thenReturn(manager);
                Method method = f.scroll.getClass().getDeclaredMethod("processKeyBinding", KeyStroke.class,
                    KeyEvent.class, int.class, boolean.class);
                method.setAccessible(true);
                int modifiers = key == KeyEvent.VK_HOME || key == KeyEvent.VK_END
                    ? java.awt.event.InputEvent.CTRL_DOWN_MASK : 0;
                KeyEvent event = new KeyEvent(f.scroll, KeyEvent.KEY_PRESSED, 1L, modifiers,
                    key, KeyEvent.CHAR_UNDEFINED);
                assertThat(method.invoke(f.scroll, KeyStroke.getKeyStrokeForEvent(event), event,
                    JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, true)).isEqualTo(true);
            }
            catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
        }
        void beginPositioning() {
            field(canvas, "viewportPositioningDepth", 1);
            viewport.setViewPosition(new Point(viewport.getViewPosition().x + 1, viewport.getViewPosition().y + 1));
        }
        Runnable savedCallback() {
            Runnable callback = (Runnable) field(canvas, "pendingViewportSettledCallback");
            assertThat(callback).isNotNull();
            return callback;
        }
        javax.swing.event.ChangeListener listener() {
            return Arrays.stream(viewport.getChangeListeners())
                .filter(listener -> listener.getClass().getName().contains("GraphWorkspaceWindowModel")).findFirst().get();
        }
    }

    private static final class FakePresentationScheduler implements GraphWorkspaceWindowModel.PresentationScheduler {
        long now;
        long sequence;
        final List<Work> work = new ArrayList<>();
        @Override public long nowMillis() { return now; }
        @Override public GraphWorkspaceWindowModel.ScheduledWork schedule(final int delay, final Runnable callback) {
            Work next = new Work(now + delay, sequence++, callback);
            work.add(next);
            return () -> next.cancelled = true;
        }
        Work last() { assertThat(work).isNotEmpty(); return work.get(work.size() - 1); }
        void advanceTo(final long millis) {
            assertThat(SwingUtilities.isEventDispatchThread()).isTrue();
            while (true) {
                Work next = work.stream().filter(w -> !w.cancelled && !w.ran && w.deadline <= millis)
                    .min(Comparator.comparingLong((Work w) -> w.deadline).thenComparingLong(w -> w.sequence)).orElse(null);
                if (next == null) { break; }
                now = next.deadline;
                next.ran = true;
                next.callback.run();
            }
            now = millis;
        }
        private static final class Work {
            final long deadline;
            final long sequence;
            final Runnable callback;
            boolean cancelled;
            boolean ran;
            Work(final long deadline, final long sequence, final Runnable callback) {
                this.deadline = deadline; this.sequence = sequence; this.callback = callback;
            }
        }
    }

    private static CanvasState largeState() {
        return largeState(2048, 1536);
    }

    private static CanvasState largeState(final double x, final double y) {
        MapReferenceId id = MapReferenceId.of("00000000-0000-0000-0000-000000000001");
        Map<ProjectedNodeKey, LayoutPoint> positions = new LinkedHashMap<>();
        Map<ProjectedNodeKey, NodeGeometry> geometry = new LinkedHashMap<>();
        List<ProjectedNode> nodes = new ArrayList<>();
        for (int sign : new int[] {-1, 1}) {
            ProjectedNodeKey key = ProjectedNodeKey.of(SourceNodeKey.transientPath(id, Collections.singletonList(sign + 1)));
            LayoutPoint point = LayoutPoint.of(sign * x, sign * y);
            positions.put(key, point);
            geometry.put(key, NodeGeometry.of(point, 10));
            nodes.add(ProjectedNode.of(key, SafeNodeLabel.of("Node", "Node"), "Map", false));
        }
        return CanvasState.of(0, GraphProjection.structure(0, nodes, Collections.emptyList()),
            LayoutFrame.of(0, LayoutPositions.of(positions, Collections.emptyMap()), false, 0),
            GraphGeometry.of(geometry, Collections.emptyMap()), EdgePaths.empty(), OperationalStatus.IDLE);
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
                GraphGeometry.of(Collections.emptyMap(), Collections.emptyMap()), EdgePaths.empty(), OperationalStatus.IDLE);
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
