package org.freeplane.view.swing.ui.mindmapmode;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Point;

import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;

import org.freeplane.core.ui.DelayedMouseListener;
import org.freeplane.core.ui.IMouseListener;

public class CenterPaneNodeSelectionOverlay {
    private final JRootPane rootPane;
    private final IMouseListener mouseListener;
    private final JComponent overlayPane;
    private Component previousGlassPane;

    public CenterPaneNodeSelectionOverlay(JRootPane rootPane, INodeSelector nodeSelector) {
        this.rootPane = rootPane;
        this.mouseListener = new DelayedMouseListener(new GlassPaneNodeSelector(nodeSelector), 2, 1);
        this.overlayPane = new JComponent() {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean contains(int x, int y) {
                Component centerComponent = findCenterComponent();
                if (centerComponent == null || centerComponent.getParent() == null) {
                    return false;
                }
                Point point = SwingUtilities.convertPoint(this, x, y, centerComponent.getParent());
                return centerComponent.getBounds().contains(point);
            }
        };
        this.overlayPane.setOpaque(false);
    }

    public void activate() {
        if (rootPane.getGlassPane() == overlayPane) {
            overlayPane.setVisible(true);
            return;
        }
        previousGlassPane = rootPane.getGlassPane();
        rootPane.setGlassPane(overlayPane);
        overlayPane.addMouseListener(mouseListener);
        overlayPane.addMouseMotionListener(mouseListener);
        overlayPane.setVisible(true);
    }

    public void deactivate() {
        overlayPane.removeMouseListener(mouseListener);
        overlayPane.removeMouseMotionListener(mouseListener);
        overlayPane.setVisible(false);
        if (rootPane.getGlassPane() == overlayPane && previousGlassPane != null) {
            rootPane.setGlassPane(previousGlassPane);
        }
    }

    private Component findCenterComponent() {
        Container contentPane = rootPane.getContentPane();
        if (!(contentPane.getLayout() instanceof BorderLayout)) {
            return null;
        }
        BorderLayout borderLayout = (BorderLayout) contentPane.getLayout();
        return borderLayout.getLayoutComponent(BorderLayout.CENTER);
    }
}
