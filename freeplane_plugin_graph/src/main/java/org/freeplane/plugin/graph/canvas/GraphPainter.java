package org.freeplane.plugin.graph.canvas;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.freeplane.plugin.graph.control.CanvasState;
import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.HullGeometry;
import org.freeplane.plugin.graph.geometry.LabelPlacement;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.geometry.NodeGeometry;
import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedEndpointVisibility;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;

final class GraphPainter {
    private static final Color GROUP_BOUNDARY_COLOR = new Color(0xDF, 0x62, 0x5D);
    void paint(final Graphics2D graphics, final CanvasState state, final GraphPaintState paintState,
            final GraphViewport viewport, final java.awt.Dimension size, final GraphTheme theme,
            final RenderingLevel level) {
        paint(graphics, state, paintState, viewport, size, theme, level, true, true);
    }

    void paint(final Graphics2D graphics, final CanvasState state, final GraphPaintState paintState,
            final GraphViewport viewport, final java.awt.Dimension size, final GraphTheme theme,
            final RenderingLevel level, final boolean showArrowheads, final boolean dimUnrelatedEnabled) {
        Objects.requireNonNull(graphics, "graphics");
        Objects.requireNonNull(paintState, "paintState");
        final GraphViewport currentViewport = Objects.requireNonNull(viewport, "viewport");
        final java.awt.Dimension componentSize = Objects.requireNonNull(size, "size");
        final GraphTheme currentTheme = Objects.requireNonNull(theme, "theme");
        final RenderingLevel renderingLevel = Objects.requireNonNull(level, "level");
        if (componentSize.width < 0 || componentSize.height < 0) {
            throw new IllegalArgumentException("Component dimensions must not be negative");
        }
        final Graphics2D copy = (Graphics2D) graphics.create();
        try {
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            copy.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
            copy.setColor(currentTheme.background());
            copy.fillRect(0, 0, componentSize.width, componentSize.height);
            if (state == null || componentSize.width == 0 || componentSize.height == 0) {
                return;
            }
            copy.transform(worldTransform(currentViewport, componentSize));
            final boolean dimUnrelated = paintState.dimUnrelated() && dimUnrelatedEnabled;
            final Set<ProjectedEndpointKey> visibleEndpoints =
                ProjectedEndpointVisibility.visibleEndpoints(state.projection().nodes(),
                    state.projection().enclosures());
            paintHulls(copy, state, paintState, currentTheme, dimUnrelated);
            paintEdges(copy, state, paintState, currentViewport, currentTheme, dimUnrelated,
                showArrowheads, visibleEndpoints);
            paintPins(copy, state, paintState, currentTheme, dimUnrelated, visibleEndpoints);
            paintLabels(copy, state, paintState, currentTheme, renderingLevel, currentViewport, dimUnrelated);
            paintHighlights(copy, state, paintState, currentTheme);
            paintConnectionPreview(copy, state, paintState, currentTheme, visibleEndpoints);
        }
        finally {
            copy.dispose();
        }
    }

    private static AffineTransform worldTransform(final GraphViewport viewport,
            final java.awt.Dimension size) {
        final AffineTransform transform = new AffineTransform();
        transform.translate(size.getWidth() * 0.5, size.getHeight() * 0.5);
        transform.scale(viewport.zoom(), viewport.zoom());
        transform.translate(-viewport.centerX(), -viewport.centerY());
        return transform;
    }

    private static void paintHulls(final Graphics2D graphics, final CanvasState state,
            final GraphPaintState paintState, final GraphTheme theme, final boolean dimUnrelated) {
        final GraphGeometry geometry = state.geometry();
        for (final ProjectedEnclosure enclosure : state.projection().enclosures()) {
            if (enclosure.boundaryTier() == BoundaryTier.SUPPRESSED) {
                continue;
            }
            final HullGeometry hull = geometry.hulls().get(enclosure.hullKey());
            if (hull == null) {
                continue;
            }
            final Shape path = hull.smoothPath();
            final boolean dim = dimUnrelated && !isEnclosureRelated(enclosure, paintState);
            final AlphaComposite oldComposite = setOpacity(graphics, dim);
            if (enclosure.mapRoot()) {
                graphics.setColor(theme.hullFill(enclosure.mapReferenceId(), enclosure.boundaryTier()));
            }
            else {
                // Group boundaries use the fixed coral marker color.
                graphics.setColor(GROUP_BOUNDARY_COLOR);
            }
            graphics.fill(path);
            if (enclosure.mapRoot()) {
                graphics.setColor(theme.hullStroke(enclosure.mapReferenceId(), enclosure.boundaryTier()));
            }
            else {
                graphics.setColor(GROUP_BOUNDARY_COLOR);
            }
            graphics.setStroke(theme.hullStrokeStyle(enclosure.boundaryTier()));
            graphics.draw(path);
            graphics.setComposite(oldComposite);
        }
    }

    private static void paintEdges(final Graphics2D graphics, final CanvasState state,
            final GraphPaintState paintState, final GraphViewport viewport, final GraphTheme theme,
            final boolean dimUnrelated, final boolean showArrowheads,
            final Set<ProjectedEndpointKey> visibleEndpoints) {
        final GraphGeometry geometry = state.geometry();
        for (final ProjectedEdge edge : state.projection().edges()) {
            if (!visibleEndpoints.contains(edge.first()) || !visibleEndpoints.contains(edge.second())) {
                continue;
            }
            final LayoutPoint firstTarget = endpointAnchor(edge.second(), state);
            final LayoutPoint secondTarget = endpointAnchor(edge.first(), state);
            if (firstTarget == null || secondTarget == null) {
                continue;
            }
            final LayoutPoint first = geometry.edgeAttachment(edge.first(), firstTarget);
            final LayoutPoint second = geometry.edgeAttachment(edge.second(), secondTarget);
            final boolean related = isRelated(edge.first(), paintState) || isRelated(edge.second(), paintState);
            final AlphaComposite oldComposite = setOpacity(graphics, dimUnrelated && !related);
            graphics.setColor(theme.edgeColor());
            graphics.setStroke(theme.edgeStroke());
            graphics.draw(new Line2D.Double(first.x(), first.y(), second.x(), second.y()));
            if (showArrowheads && edge.arrowAtFirst()) {
                paintArrow(graphics, first, second, theme.edgeColor(), viewport.zoom());
            }
            if (showArrowheads && edge.arrowAtSecond()) {
                paintArrow(graphics, second, first, theme.edgeColor(), viewport.zoom());
            }
            if (edge.hasMultiplicityCue()) {
                paintMultiplicity(graphics, first, second, edge.contributorCount(), theme, viewport.zoom());
            }
            graphics.setComposite(oldComposite);
        }
    }

    private static void paintArrow(final Graphics2D graphics, final LayoutPoint tip, final LayoutPoint from,
            final Color color, final double zoom) {
        final double dx = tip.x() - from.x();
        final double dy = tip.y() - from.y();
        final double length = Math.sqrt(dx * dx + dy * dy);
        if (!(length > 0.0) || !Double.isFinite(length)) {
            return;
        }
        final double unitX = dx / length;
        final double unitY = dy / length;
        final double perpendicularX = -unitY;
        final double perpendicularY = unitX;
        final double arrowLength = 8.0 / zoom;
        final double arrowWidth = 3.5 / zoom;
        final Path2D.Double arrow = new Path2D.Double();
        arrow.moveTo(tip.x(), tip.y());
        arrow.lineTo(tip.x() - unitX * arrowLength + perpendicularX * arrowWidth,
            tip.y() - unitY * arrowLength + perpendicularY * arrowWidth);
        arrow.lineTo(tip.x() - unitX * arrowLength - perpendicularX * arrowWidth,
            tip.y() - unitY * arrowLength - perpendicularY * arrowWidth);
        arrow.closePath();
        graphics.setColor(color);
        graphics.fill(arrow);
    }

    private static void paintMultiplicity(final Graphics2D graphics, final LayoutPoint first,
            final LayoutPoint second, final int count, final GraphTheme theme, final double zoom) {
        final double x = (first.x() + second.x()) * 0.5;
        final double y = (first.y() + second.y()) * 0.5;
        final Font font = theme.denseLabelFont().deriveFont(Math.max(1.0f, 9.0f / (float) zoom));
        graphics.setFont(font);
        graphics.setColor(theme.labelColor());
        graphics.drawString("x" + count, (float) (x + 4.0 / zoom), (float) (y - 4.0 / zoom));
    }

    private static void paintPins(final Graphics2D graphics, final CanvasState state,
            final GraphPaintState paintState, final GraphTheme theme, final boolean dimUnrelated,
            final Set<ProjectedEndpointKey> visibleEndpoints) {
        for (final PinProjection pin : state.projection().pins()) {
            if (!pin.active() || !pin.projectedNode().isPresent()) {
                continue;
            }
            final ProjectedEndpointKey endpoint =
                ProjectedEndpointKey.ofNode(pin.projectedNode().get());
            if (!visibleEndpoints.contains(endpoint) || hullOf(state, endpoint) == null) {
                continue;
            }
            final boolean dim = dimUnrelated && !isRelated(endpoint, paintState);
            final AlphaComposite oldComposite = setOpacity(graphics, dim);
            graphics.setColor(theme.pinColor());
            graphics.setStroke(theme.edgeStroke());
            final double radius = 4.0;
            graphics.draw(new Line2D.Double(pin.x() - radius, pin.y(), pin.x() + radius, pin.y()));
            graphics.draw(new Line2D.Double(pin.x(), pin.y() - radius, pin.x(), pin.y() + radius));
            graphics.setComposite(oldComposite);
        }
    }

    private static HullGeometry hullOf(final CanvasState state, final ProjectedEndpointKey endpoint) {
        if (endpoint.isNode()) {
            return null;
        }
        for (final ProjectedEnclosure enclosure : state.projection().enclosures()) {
            if (enclosure.endpointKeys().contains(endpoint.enclosure().get())) {
                return state.geometry().hulls().get(enclosure.hullKey());
            }
        }
        return null;
    }

    private static void paintLabels(final Graphics2D graphics, final CanvasState state,
            final GraphPaintState paintState, final GraphTheme theme, final RenderingLevel level,
            final GraphViewport viewport, final boolean dimUnrelated) {
        final GraphGeometry geometry = state.geometry();
        for (final ProjectedEnclosure enclosure : state.projection().enclosures()) {
            if (enclosure.boundaryTier() == BoundaryTier.SUPPRESSED) {
                continue;
            }
            if (geometry.hulls().get(enclosure.hullKey()) == null) {
                continue;
            }
            for (final EnclosureKey endpointKey : enclosure.endpointKeys()) {
                final LabelPlacement placement = geometry.labels().get(endpointKey);
                if (placement == null) {
                    continue;
                }
                final ProjectedEndpointKey endpoint = ProjectedEndpointKey.ofEnclosure(endpointKey);
                final boolean forced = isRelated(endpoint, paintState);
                final boolean emphatic = enclosure.boundaryTier() == BoundaryTier.EMPHATIC;
                final boolean required = emphatic;
                if (!shouldPaintLabel(placement.mode(), forced, level, required)) {
                    continue;
                }
                final boolean dim = dimUnrelated && !forced;
                final AlphaComposite oldComposite = setOpacity(graphics, dim);
                final Font font = labelFont(theme, level, forced, emphatic, viewport.zoom());
                graphics.setFont(font);
                graphics.setColor(theme.labelColor());
                if (placement.leaderStart().isPresent()) {
                    graphics.setStroke(theme.edgeStroke());
                    graphics.draw(new Line2D.Double(placement.leaderStart().get().x(),
                        placement.leaderStart().get().y(), placement.anchor().x(), placement.anchor().y()));
                }
                drawCentered(graphics, placement.displayText(), placement.anchor().x(), placement.anchor().y());
                graphics.setComposite(oldComposite);
            }
        }
    }

    private static Font labelFont(final GraphTheme theme, final RenderingLevel level, final boolean forced,
            final boolean emphatic, final double zoom) {
        final Font base;
        if (emphatic) {
            base = theme.emphaticLabelFont();
        }
        else {
            base = forced ? theme.labelFont() : theme.labelFont(level);
        }
        return base.deriveFont(Math.max(1.0f, base.getSize2D() / (float) zoom));
    }

    private static boolean shouldPaintLabel(final LabelPlacement.Mode mode, final boolean forced,
            final RenderingLevel level, final boolean required) {
        return forced || required || (level != RenderingLevel.OVER_TARGET
            && mode != LabelPlacement.Mode.HOVER_ONLY);
    }

    private static void drawCentered(final Graphics2D graphics, final String text, final double x,
            final double y) {
        final java.awt.FontMetrics metrics = graphics.getFontMetrics();
        final float width = metrics.stringWidth(text);
        final float baseline = (metrics.getAscent() - metrics.getDescent()) * 0.5f;
        graphics.drawString(text, (float) (x - width * 0.5f), (float) (y + baseline));
    }

    private static void paintHighlights(final Graphics2D graphics, final CanvasState state,
            final GraphPaintState paintState, final GraphTheme theme) {
        final GraphGeometry geometry = state.geometry();
        for (final ProjectedEnclosure enclosure : state.projection().enclosures()) {
            if (enclosure.boundaryTier() == BoundaryTier.SUPPRESSED) {
                continue;
            }
            final HullGeometry hull = geometry.hulls().get(enclosure.hullKey());
            if (hull == null) {
                continue;
            }
            for (final EnclosureKey endpointKey : enclosure.endpointKeys()) {
                paintEndpointOutline(graphics, hull, ProjectedEndpointKey.ofEnclosure(endpointKey),
                    paintState, theme);
            }
        }
    }

    private static void paintEndpointOutline(final Graphics2D graphics, final HullGeometry geometry,
            final ProjectedEndpointKey endpoint, final GraphPaintState paintState, final GraphTheme theme) {
        paintShapeOutlines(graphics, geometry.smoothPath(), endpoint, paintState, theme);
    }

    private static void paintShapeOutlines(final Graphics2D graphics, final Shape shape,
            final ProjectedEndpointKey endpoint, final GraphPaintState paintState, final GraphTheme theme) {
        if (paintState.selection().isPresent() && paintState.selection().get().equals(endpoint)) {
            graphics.setColor(theme.selectionColor());
            graphics.setStroke(theme.selectionStroke());
            graphics.draw(shape);
        }
        if (paintState.hover().isPresent() && paintState.hover().get().equals(endpoint)) {
            graphics.setColor(theme.hoverColor());
            graphics.setStroke(theme.hoverStroke());
            graphics.draw(shape);
        }
        if (paintState.searchMatches().contains(endpoint)) {
            graphics.setColor(theme.searchColor());
            graphics.setStroke(theme.searchStroke());
            graphics.draw(shape);
        }
    }

    private static void paintConnectionPreview(final Graphics2D graphics, final CanvasState state,
            final GraphPaintState paintState, final GraphTheme theme,
            final Set<ProjectedEndpointKey> visibleEndpoints) {
        final Optional<GraphPaintState.ConnectionPreview> preview = paintState.connectionPreview();
        if (!preview.isPresent()) {
            return;
        }
        final GraphPaintState.ConnectionPreview value = preview.get();
        if (!visibleEndpoints.contains(value.source())
                || previewEndpointAnchor(state, value.source()) == null) {
            return;
        }
        graphics.setColor(theme.previewColor());
        graphics.setStroke(theme.hoverStroke());
        graphics.draw(new Line2D.Double(value.from().x(), value.from().y(), value.to().x(), value.to().y()));
    }

    private static LayoutPoint previewEndpointAnchor(final CanvasState state,
            final ProjectedEndpointKey endpoint) {
        if (endpoint.isNode()) {
            final NodeGeometry node = state.geometry().nodes().get(endpoint.node().get());
            return node == null ? null : node.center();
        }
        final EnclosureKey enclosureKey = endpoint.enclosure().get();
        for (final ProjectedEnclosure enclosure : state.projection().enclosures()) {
            if (!enclosure.endpointKeys().contains(enclosureKey)) {
                continue;
            }
            final EnclosureHullKey hullKey = enclosure.hullKey();
            final HullGeometry hull = state.geometry().hulls().get(hullKey);
            if (hull == null) {
                return null;
            }
            final LayoutPoint position = state.layout().positions().anchors().get(hullKey);
            return position == null ? hull.labelAnchor() : position;
        }
        return null;
    }

    private static LayoutPoint endpointAnchor(final ProjectedEndpointKey endpoint, final CanvasState state) {
        final GraphGeometry geometry = state.geometry();
        final LayoutPositions positions = state.layout().positions();
        if (endpoint.isNode()) {
            final NodeGeometry node = geometry.nodes().get(endpoint.node().get());
            if (node != null) {
                return node.center();
            }
            return null;
        }
        final EnclosureKey enclosureKey = endpoint.enclosure().get();
        for (final ProjectedEnclosure enclosure : state.projection().enclosures()) {
            if (!enclosure.endpointKeys().contains(enclosureKey)) {
                continue;
            }
            final HullGeometry hull = geometry.hulls().get(enclosure.hullKey());
            if (hull == null) {
                return null;
            }
            final LayoutPoint position = positions.anchors().get(enclosure.hullKey());
            return position == null ? hull.labelAnchor() : position;
        }
        return null;
    }

    private static boolean isEnclosureRelated(final ProjectedEnclosure enclosure,
            final GraphPaintState paintState) {
        for (final EnclosureKey endpoint : enclosure.endpointKeys()) {
            if (isRelated(ProjectedEndpointKey.ofEnclosure(endpoint), paintState)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRelated(final ProjectedEndpointKey endpoint, final GraphPaintState paintState) {
        return paintState.selection().isPresent() && paintState.selection().get().equals(endpoint)
            || paintState.hover().isPresent() && paintState.hover().get().equals(endpoint)
            || paintState.searchMatches().contains(endpoint);
    }

    private static AlphaComposite setOpacity(final Graphics2D graphics, final boolean dim) {
        final AlphaComposite old = (AlphaComposite) graphics.getComposite();
        if (dim) {
            graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.28f));
        }
        return old;
    }
}
