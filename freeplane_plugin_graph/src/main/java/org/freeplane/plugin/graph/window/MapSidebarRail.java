package org.freeplane.plugin.graph.window;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.freeplane.core.util.TextUtils;

final class MapSidebarRail extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final Dimension RESTORE_BUTTON_SIZE = new Dimension(18, 18);

    private final JButton restoreButton = MapSidebarPanel.chevronButton(
        "graph_workspace.map_list.expand", "graph-workspace-map-sidebar-expand",
        "/images/MapSidebarExpand.svg?useAccentColor=true");
    private final JLabel countBadge = new JLabel();
    private final JLabel label = new VerticalLabel();
    private int activeMapCount = -1;

    MapSidebarRail() {
        setName("graph-workspace-map-sidebar-rail");
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        restoreButton.setPreferredSize(RESTORE_BUTTON_SIZE);
        restoreButton.setMaximumSize(RESTORE_BUTTON_SIZE);
        restoreButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        countBadge.setName("graph-workspace-map-sidebar-count");
        countBadge.setAlignmentX(Component.CENTER_ALIGNMENT);
        label.setName("graph-workspace-map-sidebar-label");
        label.setFont(label.getFont().deriveFont(java.awt.Font.BOLD, 10f));
        label.setForeground(java.awt.Color.GRAY);
        label.setOpaque(false);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        add(restoreButton);
        add(countBadge);
        add(label);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(MapSidebarLayout.RAIL_WIDTH, super.getPreferredSize().height);
    }

    void setActiveMapCount(final int count) {
        if (activeMapCount == count) {
            return;
        }
        activeMapCount = count;
        countBadge.setText(count == 0 ? "" : Integer.toString(count));
        countBadge.setToolTipText(TextUtils.format("graph_workspace.map_list.rail_count", Integer.valueOf(count)));
    }

    JButton restoreButton() {
        return restoreButton;
    }

    JLabel countBadge() {
        return countBadge;
    }

    private static final class VerticalLabel extends JLabel {
        private static final long serialVersionUID = 1L;

        private VerticalLabel() {
            super(TextUtils.getText("graph_workspace.map_list.rail_label"));
        }

        @Override
        public Dimension getPreferredSize() {
            final FontMetrics metrics = getFontMetrics(getFont());
            return new Dimension(metrics.getAscent() + metrics.getDescent(), metrics.stringWidth(getText()));
        }

        @Override
        protected void paintComponent(final Graphics graphics) {
            final Graphics2D copy = (Graphics2D) graphics.create();
            try {
                copy.rotate(Math.toRadians(90), getWidth() / 2.0, getHeight() / 2.0);
                super.paintComponent(copy);
            }
            finally {
                copy.dispose();
            }
        }
    }
}
