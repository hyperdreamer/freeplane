package org.freeplane.plugin.graph.window;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import java.util.Objects;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JPanel;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.TextUtils;

final class MapSidebarPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    @FunctionalInterface
    interface CollapsedListener {
        void collapsedChanged(boolean collapsed);
    }

    private final MapListPanel mapList;
    private final MapSidebarRail rail;
    private final CollapsedListener listener;
    private boolean collapsed;
    private boolean readOnly;

    MapSidebarPanel(final MapListPanel mapList, final CollapsedListener listener) {
        this.mapList = Objects.requireNonNull(mapList, "mapList");
        this.listener = Objects.requireNonNull(listener, "listener");
        setName("graph-workspace-map-sidebar");
        setLayout(new BorderLayout());
        rail = new MapSidebarRail();
        rail.restoreButton().addActionListener(event -> setCollapsed(false));
        add(rail, BorderLayout.WEST);
        add(mapList, BorderLayout.CENTER);
        applyCollapsedState(false);
    }

    void setCollapsed(final boolean collapsed) {
        if (this.collapsed == collapsed) {
            return;
        }
        this.collapsed = collapsed;
        applyCollapsedState(collapsed);
        listener.collapsedChanged(collapsed);
    }

    boolean isCollapsed() {
        return collapsed;
    }

    void setActiveMapCount(final int count) {
        rail.setActiveMapCount(count);
    }

    void setReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        mapList.setReadOnly(readOnly);
    }

    MapListPanel mapList() {
        return mapList;
    }

    MapSidebarRail rail() {
        return rail;
    }

    JButton collapseButton() {
        return mapList.collapseButton();
    }

    static JButton chevronButton(final String textKey, final String name, final String iconPath) {
        final JButton button = new JButton(TextUtils.getText(textKey));
        final Icon icon = ResourceController.getResourceController().getOptionalIcon(iconPath);
        if (icon != null) {
            button.setIcon(icon);
            button.setText(null);
            button.setToolTipText(TextUtils.getText(textKey));
            button.getAccessibleContext().setAccessibleName(TextUtils.getText(textKey));
        }
        button.setName(name);
        button.setMargin(new Insets(2, 7, 2, 7));
        button.setFocusable(false);
        return button;
    }

    private void applyCollapsedState(final boolean collapsed) {
        rail.setVisible(collapsed);
        mapList.setVisible(!collapsed);
        final int width = collapsed ? MapSidebarLayout.RAIL_WIDTH : MapSidebarLayout.DEFAULT_WIDTH;
        setPreferredSize(new Dimension(width, 0));
        setMinimumSize(new Dimension(collapsed ? MapSidebarLayout.RAIL_WIDTH : MapSidebarLayout.MIN_WIDTH, 0));
    }
}
