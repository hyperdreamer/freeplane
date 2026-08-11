package org.freeplane.plugin.graph.group;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Stroke;
import java.awt.geom.RoundRectangle2D;
import java.util.LinkedList;

import org.freeplane.features.cloud.CloudModel;
import org.freeplane.features.cloud.CloudShape;
import org.freeplane.features.map.NodeModel;
import org.freeplane.view.swing.map.NodeView;
import org.freeplane.view.swing.map.NodeViewDecorationPainter;

public final class GraphGroupMarkerPainter implements NodeViewDecorationPainter {
    private static final Color CORAL = new Color(0xDF, 0x62, 0x5D);
    private static final int ACTIVE_FILL_ALPHA = 40;
    private static final int ACTIVE_STROKE_WIDTH = 2;
    private static final int ARC_CLOUD_ALLOWANCE = 48;
    private static final int CORNER_RADIUS = 14;
    private static final int INACTIVE_FILL_ALPHA = 18;
    private static final int INACTIVE_STROKE_ALPHA = 120;
    private static final int MINIMUM_ENVELOPE_SIZE = 4;
    private static final int OUTER_GAP = 6;
    private static final int RECTANGLE_CLOUD_ALLOWANCE = 14;
    private static final int STAR_CLOUD_ALLOWANCE = 64;

    public GraphGroupMarkerPainter() {
    }

    @Override
    public void paint(final NodeView nodeView, final Graphics2D graphics) {
        final NodeModel node = nodeView.getNode();
        if (!GraphGroupModel.isMarked(node)) {
            return;
        }
        final LinkedList<Point> coordinates = new LinkedList<Point>();
        nodeView.getCoordinates(coordinates);
        if (coordinates.isEmpty()) {
            return;
        }
        final boolean inactive = hasMarkedAncestor(node);
        final RoundRectangle2D envelope = envelope(nodeView, coordinates);
        graphics.setColor(coral(inactive ? INACTIVE_FILL_ALPHA : ACTIVE_FILL_ALPHA));
        graphics.fill(envelope);
        graphics.setColor(inactive ? coral(INACTIVE_STROKE_ALPHA) : CORAL);
        graphics.setStroke(stroke(nodeView, inactive));
        graphics.draw(envelope);
    }

    private RoundRectangle2D envelope(final NodeView nodeView, final LinkedList<Point> coordinates) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Point point : coordinates) {
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
        }
        final int clearance = zoomed(nodeView, OUTER_GAP + cloudAllowance(nodeView));
        final int minimumSize = zoomed(nodeView, MINIMUM_ENVELOPE_SIZE);
        final int width = Math.max(minimumSize, maxX - minX) + 2 * clearance;
        final int height = Math.max(minimumSize, maxY - minY) + 2 * clearance;
        final int cornerRadius = Math.min(Math.min(width, height), zoomed(nodeView, CORNER_RADIUS));
        return new RoundRectangle2D.Double(minX - clearance, minY - clearance, width, height, cornerRadius,
            cornerRadius);
    }

    private int cloudAllowance(final NodeView nodeView) {
        final CloudModel cloud = nodeView.getCloudModel();
        if (cloud == null) {
            return 0;
        }
        final CloudShape shape = cloud.getShape();
        if (shape == CloudShape.STAR) {
            return STAR_CLOUD_ALLOWANCE;
        }
        if (shape == CloudShape.RECT || shape == CloudShape.ROUND_RECT) {
            return RECTANGLE_CLOUD_ALLOWANCE;
        }
        return ARC_CLOUD_ALLOWANCE;
    }

    private Color coral(final int alpha) {
        return new Color(CORAL.getRed(), CORAL.getGreen(), CORAL.getBlue(), alpha);
    }

    private boolean hasMarkedAncestor(final NodeModel node) {
        for (NodeModel ancestor = node.getParentNode(); ancestor != null; ancestor = ancestor.getParentNode()) {
            if (GraphGroupModel.isMarked(ancestor)) {
                return true;
            }
        }
        return false;
    }

    private Stroke stroke(final NodeView nodeView, final boolean inactive) {
        final float width = zoomed(nodeView, ACTIVE_STROKE_WIDTH);
        if (!inactive) {
            return new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        }
        final float dash = zoomed(nodeView, 5);
        return new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10.0f,
            new float[] {dash, dash}, 0.0f);
    }

    private int zoomed(final NodeView nodeView, final int value) {
        return Math.max(1, nodeView.getZoomed(value));
    }
}
