package org.freeplane.plugin.graph.canvas;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.IllegalComponentStateException;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.FocusListener;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleComponent;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleState;
import javax.accessibility.AccessibleStateSet;

import org.freeplane.plugin.graph.control.CanvasState;
import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.HullGeometry;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.NodeGeometry;
import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.NodeProminence;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedNode;

final class AccessibleGraphCanvas extends AccessibleContext implements AccessibleComponent {
    private final GraphCanvas canvas;

    AccessibleGraphCanvas(final GraphCanvas canvas) {
        this.canvas = Objects.requireNonNull(canvas, "canvas");
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.CANVAS;
    }

    @Override
    public AccessibleStateSet getAccessibleStateSet() {
        final AccessibleStateSet states = new AccessibleStateSet();
        if (canvas.isEnabled()) {
            states.add(AccessibleState.ENABLED);
        }
        if (canvas.isVisible()) {
            states.add(AccessibleState.VISIBLE);
        }
        if (canvas.isShowing()) {
            states.add(AccessibleState.SHOWING);
        }
        if (canvas.isFocusable()) {
            states.add(AccessibleState.FOCUSABLE);
        }
        return states;
    }

    @Override
    public int getAccessibleIndexInParent() {
        return -1;
    }

    @Override
    public int getAccessibleChildrenCount() {
        return currentOrder().size();
    }

    @Override
    public Accessible getAccessibleChild(final int index) {
        final List<ProjectedEndpointKey> order = currentOrder();
        if (index < 0 || index >= order.size()) {
            return null;
        }
        return new EndpointAccessible(canvas, order.get(index));
    }

    @Override
    public Locale getLocale() throws IllegalComponentStateException {
        return canvas.getLocale();
    }

    @Override
    public AccessibleComponent getAccessibleComponent() {
        return this;
    }

    @Override
    public Color getBackground() {
        return canvas.getBackground();
    }

    @Override
    public void setBackground(final Color color) {
        canvas.setBackground(color);
    }

    @Override
    public Color getForeground() {
        return canvas.getForeground();
    }

    @Override
    public void setForeground(final Color color) {
        canvas.setForeground(color);
    }

    @Override
    public Cursor getCursor() {
        return canvas.getCursor();
    }

    @Override
    public void setCursor(final Cursor cursor) {
        canvas.setCursor(cursor);
    }

    @Override
    public Font getFont() {
        return canvas.getFont();
    }

    @Override
    public void setFont(final Font font) {
        canvas.setFont(font);
    }

    @Override
    public FontMetrics getFontMetrics(final Font font) {
        return canvas.getFontMetrics(font);
    }

    @Override
    public boolean isEnabled() {
        return canvas.isEnabled();
    }

    @Override
    public void setEnabled(final boolean enabled) {
        canvas.setEnabled(enabled);
    }

    @Override
    public boolean isVisible() {
        return canvas.isVisible();
    }

    @Override
    public void setVisible(final boolean visible) {
        canvas.setVisible(visible);
    }

    @Override
    public boolean isShowing() {
        return canvas.isShowing();
    }

    @Override
    public boolean contains(final Point point) {
        return canvas.contains(Objects.requireNonNull(point, "point"));
    }

    @Override
    public Point getLocationOnScreen() throws IllegalComponentStateException {
        return canvas.getLocationOnScreen();
    }

    @Override
    public Point getLocation() {
        return canvas.getLocation();
    }

    @Override
    public void setLocation(final Point point) {
        canvas.setLocation(Objects.requireNonNull(point, "point"));
    }

    @Override
    public Rectangle getBounds() {
        return canvas.getBounds();
    }

    @Override
    public void setBounds(final Rectangle bounds) {
        canvas.setBounds(Objects.requireNonNull(bounds, "bounds"));
    }

    @Override
    public Dimension getSize() {
        return canvas.getSize();
    }

    @Override
    public void setSize(final Dimension size) {
        canvas.setSize(Objects.requireNonNull(size, "size"));
    }

    @Override
    public Accessible getAccessibleAt(final Point point) {
        final Point value = Objects.requireNonNull(point, "point");
        final List<ProjectedEndpointKey> order = currentOrder();
        for (final ProjectedEndpointKey endpoint : order) {
            final Accessible child = new EndpointAccessible(canvas, endpoint);
            final AccessibleComponent component = child.getAccessibleContext().getAccessibleComponent();
            if (component != null && component.contains(value)) {
                return child;
            }
        }
        return null;
    }

    @Override
    public boolean isFocusTraversable() {
        return canvas.isFocusable();
    }

    @Override
    public void requestFocus() {
        canvas.requestFocus();
    }

    @Override
    public void addFocusListener(final FocusListener listener) {
        canvas.addFocusListener(Objects.requireNonNull(listener, "listener"));
    }

    @Override
    public void removeFocusListener(final FocusListener listener) {
        canvas.removeFocusListener(Objects.requireNonNull(listener, "listener"));
    }

    private List<ProjectedEndpointKey> currentOrder() {
        final CanvasState state = canvas.canvasState();
        return state == null ? java.util.Collections.<ProjectedEndpointKey>emptyList()
            : GraphTraversalOrder.tabOrder(state);
    }

    static final class EndpointAccessible implements Accessible, AccessibleAction, AccessibleComponent {
        private final GraphCanvas canvas;
        private final ProjectedEndpointKey endpoint;
        private final EndpointAccessibleContext context;

        EndpointAccessible(final GraphCanvas canvas, final ProjectedEndpointKey endpoint) {
            this.canvas = Objects.requireNonNull(canvas, "canvas");
            this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
            context = new EndpointAccessibleContext(this);
            context.setAccessibleParent(canvas);
        }

        ProjectedEndpointKey endpoint() {
            return endpoint;
        }

        @Override
        public AccessibleContext getAccessibleContext() {
            return context;
        }

        @Override
        public int getAccessibleActionCount() {
            return current().available ? 2 : 0;
        }

        @Override
        public String getAccessibleActionDescription(final int index) {
            if (!current().available || index < 0 || index >= 2) {
                return null;
            }
            return index == 0 ? "Select endpoint" : "Open source node";
        }

        @Override
        public boolean doAccessibleAction(final int index) {
            if (!current().available || index < 0 || index >= 2) {
                return false;
            }
            return canvas.activateAccessible(endpoint, index == 1);
        }

        @Override
        public Color getBackground() {
            return canvas.getBackground();
        }

        @Override
        public void setBackground(final Color color) {
            canvas.setBackground(color);
        }

        @Override
        public Color getForeground() {
            return canvas.getForeground();
        }

        @Override
        public void setForeground(final Color color) {
            canvas.setForeground(color);
        }

        @Override
        public Cursor getCursor() {
            return canvas.getCursor();
        }

        @Override
        public void setCursor(final Cursor cursor) {
            canvas.setCursor(cursor);
        }

        @Override
        public Font getFont() {
            return canvas.getFont();
        }

        @Override
        public void setFont(final Font font) {
            canvas.setFont(font);
        }

        @Override
        public FontMetrics getFontMetrics(final Font font) {
            return canvas.getFontMetrics(font);
        }

        @Override
        public boolean isEnabled() {
            return current().available && canvas.isEnabled();
        }

        @Override
        public void setEnabled(final boolean enabled) {
            canvas.setEnabled(enabled);
        }

        @Override
        public boolean isVisible() {
            return current().available && canvas.isVisible();
        }

        @Override
        public void setVisible(final boolean visible) {
            // Virtual children have no independent mutable visibility.
        }

        @Override
        public boolean isShowing() {
            return isVisible() && canvas.isShowing();
        }

        @Override
        public boolean contains(final Point point) {
            return current().bounds.contains(Objects.requireNonNull(point, "point"));
        }

        @Override
        public Point getLocationOnScreen() throws IllegalComponentStateException {
            final Point location = canvas.getLocationOnScreen();
            final Rectangle bounds = current().bounds;
            location.translate(bounds.x, bounds.y);
            return location;
        }

        @Override
        public Point getLocation() {
            return current().bounds.getLocation();
        }

        @Override
        public void setLocation(final Point point) {
            // Virtual children derive location from current graph geometry.
        }

        @Override
        public Rectangle getBounds() {
            return current().bounds;
        }

        @Override
        public void setBounds(final Rectangle bounds) {
            // Virtual children derive bounds from current graph geometry.
        }

        @Override
        public Dimension getSize() {
            return current().bounds.getSize();
        }

        @Override
        public void setSize(final Dimension size) {
            // Virtual children derive size from current graph geometry.
        }

        @Override
        public Accessible getAccessibleAt(final Point point) {
            return null;
        }

        @Override
        public boolean isFocusTraversable() {
            return canvas.isFocusable();
        }

        @Override
        public void requestFocus() {
            canvas.requestFocus();
        }

        @Override
        public void addFocusListener(final FocusListener listener) {
            canvas.addFocusListener(Objects.requireNonNull(listener, "listener"));
        }

        @Override
        public void removeFocusListener(final FocusListener listener) {
            canvas.removeFocusListener(Objects.requireNonNull(listener, "listener"));
        }

        private EndpointInfo current() {
            final CanvasState state = canvas.canvasState();
            if (state == null) {
                return EndpointInfo.unavailable();
            }
            final GraphGeometry geometry = state.geometry();
            if (endpoint.isNode()) {
                for (ProjectedNode node : state.projection().nodes()) {
                    if (!endpoint.node().get().equals(node.key())) {
                        continue;
                    }
                    final NodeGeometry nodeGeometry = geometry.nodes().get(node.key());
                    if (nodeGeometry == null) {
                        return EndpointInfo.unavailable();
                    }
                    final NodeProminence prominence = state.projection().prominence().get(node.key());
                    final int visibleOutgoingTargets = prominence == null ? 0
                        : prominence.visibleOutgoingTargets();
                    return EndpointInfo.node(node.label().fullText(), node.mapName(),
                        nodeGeometry.minX(), nodeGeometry.minY(), nodeGeometry.maxX(), nodeGeometry.maxY(),
                        isSelected(state), isPinned(state, node.key()), visibleOutgoingTargets,
                        screenBounds(nodeGeometry.minX(), nodeGeometry.minY(), nodeGeometry.maxX(),
                            nodeGeometry.maxY()));
                }
                return EndpointInfo.unavailable();
            }
            for (ProjectedEnclosure enclosure : state.projection().enclosures()) {
                if (enclosure.boundaryTier() == BoundaryTier.SUPPRESSED) {
                    continue;
                }
                final int index = enclosure.endpointKeys().indexOf(endpoint.enclosure().get());
                if (index < 0) {
                    continue;
                }
                final HullGeometry hull = geometry.hulls().get(enclosure.hullKey());
                if (hull == null) {
                    return EndpointInfo.unavailable();
                }
                return EndpointInfo.enclosure(enclosure.labels().get(index).fullText(),
                    enclosure.mapName(), hull.minX(), hull.minY(), hull.maxX(), hull.maxY(),
                    isSelected(state), screenBounds(hull.minX(), hull.minY(), hull.maxX(), hull.maxY()));
            }
            return EndpointInfo.unavailable();
        }

        private Rectangle screenBounds(final double minX, final double minY, final double maxX,
                final double maxY) {
            final Dimension size = new Dimension(canvas.getWidth(), canvas.getHeight());
            final Point2D.Double first = canvas.viewport().toScreen(minX, minY, size);
            final Point2D.Double second = canvas.viewport().toScreen(maxX, maxY, size);
            final int left = toInt(Math.min(first.x, second.x));
            final int top = toInt(Math.min(first.y, second.y));
            final int right = toInt(Math.max(first.x, second.x));
            final int bottom = toInt(Math.max(first.y, second.y));
            return new Rectangle(left, top, Math.max(1, right - left), Math.max(1, bottom - top));
        }

        private boolean isSelected(final CanvasState state) {
            return canvas.paintState().selection().isPresent()
                && endpoint.equals(canvas.paintState().selection().get());
        }

        private static boolean isPinned(final CanvasState state,
                final org.freeplane.plugin.graph.projection.ProjectedNodeKey node) {
            for (PinProjection pin : state.projection().pins()) {
                if (pin.active() && pin.projectedNode().isPresent()
                        && node.equals(pin.projectedNode().get())) {
                    return true;
                }
            }
            return false;
        }

        private static int toInt(final double value) {
            if (value <= Integer.MIN_VALUE) {
                return Integer.MIN_VALUE;
            }
            if (value >= Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
            return (int) Math.round(value);
        }
    }

    private static final class EndpointAccessibleContext extends AccessibleContext {
        private final EndpointAccessible endpoint;

        private EndpointAccessibleContext(final EndpointAccessible endpoint) {
            this.endpoint = endpoint;
        }

        @Override
        public String getAccessibleName() {
            final EndpointInfo info = endpoint.current();
            return info.available ? info.label + " - " + info.mapName : "Unavailable graph endpoint";
        }

        @Override
        public String getAccessibleDescription() {
            final EndpointInfo info = endpoint.current();
            if (!info.available) {
                return "Unavailable graph endpoint";
            }
            final StringBuilder description = new StringBuilder(info.label)
                .append(" on ").append(info.mapName).append(". ")
                .append(info.node ? "Node endpoint." : "Enclosure endpoint.")
                .append(" Select action available. Open source action available.");
            if (info.selected) {
                description.append(" Selected.");
            }
            if (info.pinned) {
                description.append(" Pinned.");
            }
            if (info.visibleOutgoingTargets > 0) {
                description.append(" Visible outgoing targets: ")
                    .append(info.visibleOutgoingTargets).append('.');
            }
            return description.toString();
        }

        @Override
        public AccessibleRole getAccessibleRole() {
            return AccessibleRole.PUSH_BUTTON;
        }

        @Override
        public AccessibleStateSet getAccessibleStateSet() {
            final EndpointInfo info = endpoint.current();
            final AccessibleStateSet states = new AccessibleStateSet();
            if (info.available && endpoint.canvas.isEnabled()) {
                states.add(AccessibleState.ENABLED);
                states.add(AccessibleState.VISIBLE);
            }
            if (endpoint.isShowing()) {
                states.add(AccessibleState.SHOWING);
            }
            if (endpoint.canvas.isFocusable()) {
                states.add(AccessibleState.FOCUSABLE);
            }
            if (info.selected) {
                states.add(AccessibleState.SELECTED);
            }
            return states;
        }

        @Override
        public int getAccessibleIndexInParent() {
            final CanvasState state = endpoint.canvas.canvasState();
            return state == null ? -1 : GraphTraversalOrder.tabOrder(state).indexOf(endpoint.endpoint);
        }

        @Override
        public int getAccessibleChildrenCount() {
            return 0;
        }

        @Override
        public Accessible getAccessibleChild(final int index) {
            return null;
        }

        @Override
        public Locale getLocale() throws IllegalComponentStateException {
            return endpoint.canvas.getLocale();
        }

        @Override
        public AccessibleAction getAccessibleAction() {
            return endpoint;
        }

        @Override
        public AccessibleComponent getAccessibleComponent() {
            return endpoint;
        }
    }

    private static final class EndpointInfo {
        private final boolean available;
        private final boolean node;
        private final String label;
        private final String mapName;
        private final boolean selected;
        private final boolean pinned;
        private final int visibleOutgoingTargets;
        private final Rectangle bounds;

        private EndpointInfo(final boolean available, final boolean node, final String label,
                final String mapName, final boolean selected, final boolean pinned,
                final int visibleOutgoingTargets, final Rectangle bounds) {
            this.available = available;
            this.node = node;
            this.label = label;
            this.mapName = mapName;
            this.selected = selected;
            this.pinned = pinned;
            this.visibleOutgoingTargets = visibleOutgoingTargets;
            this.bounds = bounds;
        }

        private static EndpointInfo node(final String label, final String mapName, final double minX,
                final double minY, final double maxX, final double maxY, final boolean selected,
                final boolean pinned, final int visibleOutgoingTargets, final Rectangle bounds) {
            return new EndpointInfo(true, true, label, mapName, selected, pinned,
                visibleOutgoingTargets, bounds);
        }

        private static EndpointInfo enclosure(final String label, final String mapName,
                final double minX, final double minY, final double maxX, final double maxY,
                final boolean selected, final Rectangle bounds) {
            return new EndpointInfo(true, false, label, mapName, selected, false, 0, bounds);
        }

        private static EndpointInfo unavailable() {
            return new EndpointInfo(false, false, "", "", false, false, 0,
                new Rectangle());
        }
    }
}
