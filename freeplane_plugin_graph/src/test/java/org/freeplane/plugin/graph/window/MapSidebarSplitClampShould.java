package org.freeplane.plugin.graph.window;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;

import org.junit.Test;

public class MapSidebarSplitClampShould {
    private static final int DIVIDER_SIZE = 6;

    private static final class ClampHarness {
        private final JPanel leftPanel = new JPanel();
        private final JPanel rightPanel = new JPanel();
        private final JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        private int splitWidth;
        private int storedWidth = 264;
        private int appliedWidth;
        private int commits;
        private int reclampResets;
        private boolean gestureActive;
        private boolean applying;

        private ClampHarness() {
            splitPane.setContinuousLayout(true);
            splitPane.setDividerSize(DIVIDER_SIZE);
            splitPane.setResizeWeight(0.0);
            splitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, event -> {
                if (applying) {
                    return;
                }
                final int location = splitPane.getDividerLocation();
                if (location < 0 || effectiveMinimum() < MapSidebarLayout.MIN_WIDTH) {
                    return;
                }
                final int clamped = MapSidebarLayout.clampWidth(location, splitWidth, effectiveDividerWidth(),
                    insets());
                if (clamped != location) {
                    reclampResets++;
                    runGuarded(() -> splitPane.setDividerLocation(clamped));
                }
            });
            divider().addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(final MouseEvent event) {
                    if (event.getButton() != MouseEvent.BUTTON1) {
                        return;
                    }
                    gestureActive = event.getClickCount() != 2;
                }

                @Override
                public void mouseReleased(final MouseEvent event) {
                    if (event.getButton() != MouseEvent.BUTTON1 || !gestureActive) {
                        return;
                    }
                    gestureActive = false;
                    commit(event.getX());
                }
            });
        }

        private void commit(final int location) {
            if (effectiveMinimum() < MapSidebarLayout.MIN_WIDTH) {
                return;
            }
            final int clamped = MapSidebarLayout.clampWidth(location, splitWidth, effectiveDividerWidth(), insets());
            if (clamped == appliedWidth) {
                return;
            }
            commits++;
            storedWidth = clamped;
        }

        private void runGuarded(final Runnable action) {
            final boolean previous = applying;
            applying = true;
            try {
                action.run();
            }
            finally {
                applying = previous;
            }
        }

        private int effectiveDividerWidth() {
            final BasicSplitPaneDivider divider = (BasicSplitPaneDivider) divider();
            return divider == null ? 0 : divider.getDividerSize();
        }

        private Insets insets() {
            final Insets value = splitPane.getInsets();
            return value == null ? new Insets(0, 0, 0, 0) : value;
        }

        private int effectiveMinimum() {
            return MapSidebarLayout.effectiveMinimum(splitWidth, effectiveDividerWidth(), insets());
        }

        private ClampHarness layoutAt(final int width) {
            splitWidth = width;
            leftPanel.setMinimumSize(new Dimension(
                MapSidebarLayout.effectiveMinimum(width, effectiveDividerWidth(), insets()), 0));
            rightPanel.setMinimumSize(new Dimension(
                MapSidebarLayout.canvasMinimumWidth(width, effectiveDividerWidth(), insets()), 0));
            splitPane.setSize(width, 300);
            splitPane.doLayout();
            appliedWidth = MapSidebarLayout.clampWidth(storedWidth, width, effectiveDividerWidth(), insets());
            runGuarded(() -> splitPane.setDividerLocation(appliedWidth));
            return this;
        }

        private Component divider() {
            return ((BasicSplitPaneUI) splitPane.getUI()).getDivider();
        }

        private void press(final int x) {
            divider().dispatchEvent(mouseEvent(MouseEvent.MOUSE_PRESSED, x));
        }

        private void drag(final int x) {
            divider().dispatchEvent(mouseEvent(MouseEvent.MOUSE_DRAGGED, x));
        }

        private void release(final int x) {
            divider().dispatchEvent(mouseEvent(MouseEvent.MOUSE_RELEASED, x));
        }

        private MouseEvent mouseEvent(final int id, final int x) {
            return new MouseEvent(divider(), id, System.currentTimeMillis(), 0, x, 0, 1, false,
                MouseEvent.BUTTON1);
        }
    }

    @Test
    public void reportsMinimumDividerLocationAtOrAboveTheSidebarMinimum() {
        for (final int splitWidth : new int[] { 400, 800, 1000 }) {
            ClampHarness harness = new ClampHarness().layoutAt(splitWidth);

            assertThat(((BasicSplitPaneUI) harness.splitPane.getUI()).getMinimumDividerLocation(harness.splitPane))
                .isGreaterThanOrEqualTo(MapSidebarLayout.MIN_WIDTH);
        }
    }

    @Test
    public void reportsTheExactInteractiveCeilingAsMaximumWidth() {
        for (final int splitWidth : new int[] { 400, 800, 1000 }) {
            ClampHarness harness = new ClampHarness().layoutAt(splitWidth);

            assertThat(((BasicSplitPaneUI) harness.splitPane.getUI()).getMaximumDividerLocation(harness.splitPane))
                .isEqualTo(MapSidebarLayout.maximumWidth(splitWidth));
        }
        ClampHarness narrow = new ClampHarness().layoutAt(362);
        assertThat(((BasicSplitPaneUI) narrow.splitPane.getUI()).getMaximumDividerLocation(narrow.splitPane))
            .isEqualTo(181);
    }

    @Test
    public void reclampsAProgrammaticEndMoveToTheAllowedMaximum() {
        ClampHarness harness = new ClampHarness().layoutAt(1000);

        harness.splitPane.setDividerLocation(1000 - 1);

        assertThat(harness.splitPane.getDividerLocation()).isEqualTo(MapSidebarLayout.maximumWidth(1000));
        assertThat(harness.commits).isZero();
    }

    @Test
    public void commitsOneClampedWidthForOneDividerDrag() {
        ClampHarness harness = new ClampHarness().layoutAt(1000);
        harness.press(harness.splitPane.getDividerLocation());
        harness.drag(999);
        harness.release(999);

        assertThat(harness.commits).isEqualTo(1);
        assertThat(harness.storedWidth).isEqualTo(500);

        ClampHarness moved = new ClampHarness().layoutAt(1000);
        moved.press(moved.splitPane.getDividerLocation());
        moved.drag(250);
        moved.release(250);

        assertThat(moved.commits).isEqualTo(1);
        assertThat(moved.storedWidth).isEqualTo(250);
    }

    @Test
    public void commitsNothingForAPressAndReleaseWithoutMovement() {
        ClampHarness harness = new ClampHarness().layoutAt(1000);
        int location = harness.splitPane.getDividerLocation();

        harness.press(location);
        harness.release(location);

        assertThat(harness.commits).isZero();
        assertThat(harness.storedWidth).isEqualTo(264);
    }

    @Test
    public void holdsTheSqueezedRegimeWithoutCommits() {
        for (final int splitWidth : new int[] { 100, 150 }) {
            ClampHarness harness = new ClampHarness().layoutAt(splitWidth);
            harness.press(harness.splitPane.getDividerLocation());
            harness.drag(splitWidth - 1);
            harness.release(splitWidth - 1);

            assertThat(harness.commits).isZero();
            assertThat(harness.storedWidth).isEqualTo(264);

            int resetsBefore = harness.reclampResets;
            harness.layoutAt(splitWidth);
            assertThat(harness.reclampResets).isEqualTo(resetsBefore);
        }
    }
}
