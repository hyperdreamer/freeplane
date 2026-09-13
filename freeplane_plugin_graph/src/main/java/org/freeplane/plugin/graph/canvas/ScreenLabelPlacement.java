package org.freeplane.plugin.graph.canvas;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.HullGeometry;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.NodeGeometry;
import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;

public final class ScreenLabelPlacement {
    static final double SLOT_GAP = 6.0;
    static final double DISPLACED_OFFSET = 30.0;
    static final double ARC_GAP = 1.0;
    static final double EXTERNAL_GAP = 4.0;
    static final double LEADER_CLEARANCE = 3.0;
    static final double MIN_VISIBLE_LEADER = 2.0;
    static final int SUBTLE_EXTERNAL_CANDIDATE_BUDGET = 8;
    private static final double MIN_DISC_RADIUS = 2.0;
    private static final double VERTICAL_MAX_WIDTH = 200.0;
    private static final double HORIZONTAL_MAX_WIDTH = 130.0;
    private static final double DIAGONAL_MAX_WIDTH = 150.0;
    private static final String ELLIPSIS = "\u2026";
    static final FontRenderContext SCREEN_FRC = new FontRenderContext(null, true, true);

    enum Slot {
        ABOVE, BELOW, RIGHT, LEFT, ABOVE_RIGHT, ABOVE_LEFT, BELOW_RIGHT, BELOW_LEFT,
        ABOVE_FAR, BELOW_FAR, RIGHT_FAR, LEFT_FAR
    }

    static final Slot[] NEAR_SLOTS = { Slot.ABOVE, Slot.BELOW, Slot.RIGHT, Slot.LEFT,
        Slot.ABOVE_RIGHT, Slot.ABOVE_LEFT, Slot.BELOW_RIGHT, Slot.BELOW_LEFT };
    static final Slot[] FAR_SLOTS = { Slot.ABOVE_FAR, Slot.BELOW_FAR, Slot.RIGHT_FAR, Slot.LEFT_FAR };

    public List<PlacedLabel> place(final LabelPlacementRequest request, final List<PlacedLabel> previous,
            final LabelFonts fonts) {
        return place(request, previous, fonts, Collections.<Rectangle2D>emptyList());
    }

    List<PlacedLabel> place(final LabelPlacementRequest request, final List<PlacedLabel> previous,
            final LabelFonts fonts, final List<Rectangle2D> seedObstacles) {
        final Context context = new Context(request, fonts, seedObstacles);
        final Map<ProjectedNodeKey, PlacedLabel> previousByNode = indexPrevious(previous);
        final List<PlacedLabel> placed = new ArrayList<PlacedLabel>();
        placeForced(context, previousByNode, placed);
        placeEnclosures(context, placed);
        placeNodes(context, previousByNode, placed);
        return filterByLevel(request, placed);
    }

    private static final class Context {
        final LabelPlacementRequest request;
        final LabelFonts fonts;
        final Map<ProjectedNodeKey, ProjectedNode> nodes =
            new LinkedHashMap<ProjectedNodeKey, ProjectedNode>();
        final Map<ProjectedNodeKey, Double> radii = new LinkedHashMap<ProjectedNodeKey, Double>();
        final List<Rectangle2D> obstacles = new ArrayList<Rectangle2D>();
        final List<ProjectedNodeKey> order = new ArrayList<ProjectedNodeKey>();

        Context(final LabelPlacementRequest request, final LabelFonts fonts,
                final List<Rectangle2D> seedObstacles) {
            this.request = request;
            this.fonts = fonts;
            for (final ProjectedNode node : request.projection().nodes()) {
                nodes.put(node.key(), node);
            }
            for (final ProjectedNodeKey key : request.positions().nodes().keySet()) {
                if (!nodes.containsKey(key)) {
                    continue;
                }
                final NodeGeometry geometry = request.geometry().nodes().get(key);
                if (geometry == null) {
                    continue;
                }
                final double radius = Math.max(MIN_DISC_RADIUS, geometry.radius() * request.zoom());
                radii.put(key, Double.valueOf(radius));
                final double centerX = request.screenX(geometry.center().x());
                final double centerY = request.screenY(geometry.center().y());
                obstacles.add(new Rectangle2D.Double(centerX - radius, centerY - radius,
                    2.0 * radius, 2.0 * radius));
            }
            obstacles.addAll(seedObstacles);
            order.addAll(radii.keySet());
            Collections.sort(order, new Comparator<ProjectedNodeKey>() {
                @Override
                public int compare(final ProjectedNodeKey first, final ProjectedNodeKey second) {
                    return Double.compare(radii.get(second).doubleValue(), radii.get(first).doubleValue());
                }
            });
        }

        double radius(final ProjectedNodeKey key) {
            return radii.get(key).doubleValue();
        }

        double screenX(final ProjectedNodeKey key) {
            return request.screenX(request.geometry().nodes().get(key).center().x());
        }

        double screenY(final ProjectedNodeKey key) {
            return request.screenY(request.geometry().nodes().get(key).center().y());
        }
    }

    private static Map<ProjectedNodeKey, PlacedLabel> indexPrevious(final List<PlacedLabel> previous) {
        final Map<ProjectedNodeKey, PlacedLabel> result = new LinkedHashMap<ProjectedNodeKey, PlacedLabel>();
        if (previous == null) {
            return result;
        }
        for (final PlacedLabel label : previous) {
            if (label.endpoint().isNode()) {
                result.put(label.endpoint().node().get(), label);
            }
        }
        return result;
    }

    private static void placeForced(final Context context,
            final Map<ProjectedNodeKey, PlacedLabel> previousByNode, final List<PlacedLabel> placed) {
        for (final ProjectedNodeKey key : context.order) {
            if (!isForced(context, key)) {
                continue;
            }
            placed.add(placeNode(context, key, true, previousByNode.get(key)));
        }
    }

    private static void placeNodes(final Context context,
            final Map<ProjectedNodeKey, PlacedLabel> previousByNode, final List<PlacedLabel> placed) {
        for (final ProjectedNodeKey key : context.order) {
            if (isForced(context, key)) {
                continue;
            }
            placed.add(placeNode(context, key, false, previousByNode.get(key)));
        }
    }

    private static boolean isForced(final Context context, final ProjectedNodeKey key) {
        return context.request.forced().contains(ProjectedEndpointKey.ofNode(key));
    }

    private static void placeEnclosures(final Context context, final List<PlacedLabel> placed) {
        placeEnclosures(context, placed, BoundaryTier.EMPHATIC);
        placeEnclosures(context, placed, BoundaryTier.SUBTLE);
    }

    private static void placeEnclosures(final Context context, final List<PlacedLabel> placed,
            final BoundaryTier tier) {
        for (final ProjectedEnclosure enclosure : context.request.projection().enclosures()) {
            if (enclosure.boundaryTier() != tier) {
                continue;
            }
            final HullGeometry hull = context.request.geometry().hulls().get(enclosure.hullKey());
            if (hull == null) {
                continue;
            }
            final boolean emphatic = enclosure.boundaryTier() == BoundaryTier.EMPHATIC;
            final Font font = emphatic ? context.fonts.emphatic() : context.fonts.full();
            final List<EnclosureKey> endpoints = enclosure.endpointKeys();
            for (int index = 0; index < endpoints.size(); index++) {
                final EnclosureKey endpointKey = endpoints.get(index);
                final ProjectedEndpointKey endpoint = ProjectedEndpointKey.ofEnclosure(endpointKey);
                final String text = enclosure.labels().get(index).displayText();
                final boolean forced = context.request.forced().contains(endpoint);
                placed.add(placeEnclosureLabel(context, endpoint, text, font, emphatic, hull, forced));
            }
        }
    }

    private static PlacedLabel placeEnclosureLabel(final Context context,
            final ProjectedEndpointKey endpoint, final String text, final Font font, final boolean emphatic,
            final HullGeometry hull, final boolean forced) {
        final Rectangle2D size = screenBounds(text, font);
        final List<LayoutPoint> polygon = screenPolygon(context, hull);
        final PlacedLabel interior = interiorEnclosureLabel(context, endpoint, text, font, hull, size, forced);
        if (interior != null) {
            return interior;
        }
        final PlacedLabel arc = arcEnclosureLabel(context, endpoint, text, font, hull, size, forced, polygon);
        if (arc != null) {
            return arc;
        }
        final PlacedLabel external = externalEnclosureLabel(context, endpoint, text, font, emphatic, hull,
            size, forced, polygon);
        if (external != null) {
            return external;
        }
        return anchorTerminal(context, endpoint, text, font, emphatic, hull, size, forced);
    }

    private static PlacedLabel interiorEnclosureLabel(final Context context,
            final ProjectedEndpointKey endpoint, final String text, final Font font, final HullGeometry hull,
            final Rectangle2D size, final boolean forced) {
        final double anchorX = context.request.screenX(hull.labelAnchor().x());
        final double anchorY = context.request.screenY(hull.labelAnchor().y());
        final Rectangle2D candidate = rectangle(anchorX, anchorY, size.getWidth(), size.getHeight());
        if (!context.request.placementArea().contains(candidate)
                || !rectInHull(context, hull, candidate)
                || intersectsAny(context.obstacles, candidate)) {
            return null;
        }
        context.obstacles.add(candidate);
        return enclosureLabel(endpoint, text, font, PlacedLabel.Mode.INTERIOR, PlacedLabel.Rung.FULL_NEAR,
            anchorX, anchorY, size, forced, false, Optional.<LeaderLine>empty());
    }

    private static PlacedLabel arcEnclosureLabel(final Context context,
            final ProjectedEndpointKey endpoint, final String text, final Font font, final HullGeometry hull,
            final Rectangle2D size, final boolean forced, final List<LayoutPoint> polygon) {
        final int edgeCount = polygon.size();
        final int[] population = new int[edgeCount];
        final double[] lengths = new double[edgeCount];
        for (int index = 0; index < edgeCount; index++) {
            final LayoutPoint start = polygon.get(index);
            final LayoutPoint end = polygon.get((index + 1) % edgeCount);
            population[index] = edgePopulation(polygon, start, end, outwardNormal(polygon, index),
                context.obstacles, size.getHeight() + ARC_GAP);
            lengths[index] = Math.hypot(end.x() - start.x(), end.y() - start.y());
        }
        final List<Integer> edgeOrder = new ArrayList<Integer>();
        for (int index = 0; index < edgeCount; index++) {
            edgeOrder.add(Integer.valueOf(index));
        }
        Collections.sort(edgeOrder, new Comparator<Integer>() {
            @Override
            public int compare(final Integer first, final Integer second) {
                final int firstIndex = first.intValue();
                final int secondIndex = second.intValue();
                if (population[firstIndex] != population[secondIndex]) {
                    return population[firstIndex] - population[secondIndex];
                }
                if (lengths[firstIndex] != lengths[secondIndex]) {
                    return Double.compare(lengths[secondIndex], lengths[firstIndex]);
                }
                return firstIndex - secondIndex;
            }
        });
        for (final Integer edge : edgeOrder) {
            final int index = edge.intValue();
            final LayoutPoint start = polygon.get(index);
            final LayoutPoint end = polygon.get((index + 1) % edgeCount);
            final LayoutPoint outward = outwardNormal(polygon, index);
            final double anchorX = (start.x() + end.x()) * 0.5
                - outward.x() * (size.getHeight() * 0.5 + ARC_GAP);
            final double anchorY = (start.y() + end.y()) * 0.5
                - outward.y() * (size.getHeight() * 0.5 + ARC_GAP);
            final Rectangle2D candidate = rectangle(anchorX, anchorY, size.getWidth(), size.getHeight());
            if (context.request.placementArea().contains(candidate)
                    && rectInHull(context, hull, candidate)
                    && !intersectsAny(context.obstacles, candidate)) {
                context.obstacles.add(candidate);
                return enclosureLabel(endpoint, text, font, PlacedLabel.Mode.ARC, PlacedLabel.Rung.FULL_NEAR,
                    anchorX, anchorY, size, forced, false, Optional.<LeaderLine>empty());
            }
        }
        return null;
    }

    private static PlacedLabel externalEnclosureLabel(final Context context,
            final ProjectedEndpointKey endpoint, final String text, final Font font, final boolean emphatic,
            final HullGeometry hull, final Rectangle2D size, final boolean forced,
            final List<LayoutPoint> polygon) {
        final int edgeCount = polygon.size();
        int total = 0;
        for (int index = 0; index < edgeCount; index++) {
            final LayoutPoint start = polygon.get(index);
            final LayoutPoint end = polygon.get((index + 1) % edgeCount);
            final LayoutPoint outward = outwardNormal(polygon, index);
            final double areaSupport = areaSupport(context.request.placementArea(), outward);
            final double halfNormal = outward.x() != 0.0
                ? size.getWidth() * 0.5 : size.getHeight() * 0.5;
            final double midpointX = (start.x() + end.x()) * 0.5;
            final double midpointY = (start.y() + end.y()) * 0.5;
            for (int lane = 0; ; lane++) {
                total++;
                final double distance = size.getHeight() * 0.5 + EXTERNAL_GAP
                    + lane * (size.getHeight() + EXTERNAL_GAP);
                final double anchorX = midpointX + outward.x() * distance;
                final double anchorY = midpointY + outward.y() * distance;
                final Rectangle2D candidate = rectangle(anchorX, anchorY, size.getWidth(), size.getHeight());
                if (!hull.contains(worldPoint(context, anchorX, anchorY))
                        && context.request.placementArea().contains(candidate)
                        && !intersectsAny(context.obstacles, candidate)) {
                    final LayoutPoint leaderWorld = hull.nearestBoundaryPoint(
                        worldPoint(context, anchorX, anchorY));
                    final LayoutPoint boundaryStart = LayoutPoint.of(
                        context.request.screenX(leaderWorld.x()),
                        context.request.screenY(leaderWorld.y()));
                    context.obstacles.add(candidate);
                    return enclosureLabel(endpoint, text, font, PlacedLabel.Mode.EXTERNAL,
                        PlacedLabel.Rung.FULL_NEAR, anchorX, anchorY, size, forced, false,
                        leader(boundaryStart, anchorX, anchorY, size.getWidth(), size.getHeight()));
                }
                if (!emphatic && total >= SUBTLE_EXTERNAL_CANDIDATE_BUDGET) {
                    return null;
                }
                final double innerEdge = outward.x() * anchorX + outward.y() * anchorY - halfNormal;
                if (innerEdge > areaSupport) {
                    break;
                }
            }
        }
        return null;
    }

    private static PlacedLabel anchorTerminal(final Context context, final ProjectedEndpointKey endpoint,
            final String text, final Font font, final boolean emphatic, final HullGeometry hull,
            final Rectangle2D size, final boolean forced) {
        final double anchorX = context.request.screenX(hull.labelAnchor().x());
        final double anchorY = context.request.screenY(hull.labelAnchor().y());
        if (emphatic) {
            return enclosureLabel(endpoint, text, font, PlacedLabel.Mode.INTERIOR,
                PlacedLabel.Rung.FULL_NEAR, anchorX, anchorY, size, forced, true,
                Optional.<LeaderLine>empty());
        }
        return enclosureLabel(endpoint, text, font, PlacedLabel.Mode.HOVER_ONLY,
            PlacedLabel.Rung.HOVER_ONLY, anchorX, anchorY, size, forced, false,
            Optional.<LeaderLine>empty());
    }

    private static PlacedLabel enclosureLabel(final ProjectedEndpointKey endpoint, final String text,
            final Font font, final PlacedLabel.Mode mode, final PlacedLabel.Rung rung, final double anchorX,
            final double anchorY, final Rectangle2D size, final boolean forced,
            final boolean emphaticAtAnchor, final Optional<LeaderLine> leader) {
        return new PlacedLabel(endpoint, text, font, mode, rung, anchorX, anchorY, size.getWidth(),
            size.getHeight(), false, forced, emphaticAtAnchor, false, false, leader, Slot.ABOVE);
    }

    private static List<LayoutPoint> screenPolygon(final Context context, final HullGeometry hull) {
        final List<LayoutPoint> polygon = new ArrayList<LayoutPoint>();
        for (final LayoutPoint point : hull.exactPolygon()) {
            polygon.add(LayoutPoint.of(context.request.screenX(point.x()),
                context.request.screenY(point.y())));
        }
        return polygon;
    }

    private static LayoutPoint outwardNormal(final List<LayoutPoint> polygon, final int index) {
        final LayoutPoint start = polygon.get(index);
        final LayoutPoint end = polygon.get((index + 1) % polygon.size());
        final double dx = end.x() - start.x();
        final double dy = end.y() - start.y();
        final double length = Math.hypot(dx, dy);
        final double sign = signedArea(polygon) >= 0.0 ? 1.0 : -1.0;
        return LayoutPoint.of(sign * dy / length, -sign * dx / length);
    }

    private static double signedArea(final List<LayoutPoint> polygon) {
        double area = 0.0;
        for (int index = 0; index < polygon.size(); index++) {
            final LayoutPoint first = polygon.get(index);
            final LayoutPoint second = polygon.get((index + 1) % polygon.size());
            area += first.x() * second.y() - second.x() * first.y();
        }
        return area * 0.5;
    }

    private static boolean rectInHull(final Context context, final HullGeometry hull,
            final Rectangle2D rectangle) {
        return hull.contains(worldPoint(context, rectangle.getMinX(), rectangle.getMinY()))
            && hull.contains(worldPoint(context, rectangle.getMinX(), rectangle.getMaxY()))
            && hull.contains(worldPoint(context, rectangle.getMaxX(), rectangle.getMinY()))
            && hull.contains(worldPoint(context, rectangle.getMaxX(), rectangle.getMaxY()));
    }

    private static LayoutPoint worldPoint(final Context context, final double screenX,
            final double screenY) {
        return LayoutPoint.of(context.request.worldX(screenX), context.request.worldY(screenY));
    }

    private static int edgePopulation(final List<LayoutPoint> polygon, final LayoutPoint start,
            final LayoutPoint end, final LayoutPoint outward, final List<Rectangle2D> obstacles,
            final double depth) {
        final double dx = end.x() - start.x();
        final double dy = end.y() - start.y();
        final double length = Math.hypot(dx, dy);
        final double tangentX = dx / length;
        final double tangentY = dy / length;
        final double minT = Math.min(tangentX * start.x() + tangentY * start.y(),
            tangentX * end.x() + tangentY * end.y());
        final double maxT = Math.max(tangentX * start.x() + tangentY * start.y(),
            tangentX * end.x() + tangentY * end.y());
        double support = Double.NEGATIVE_INFINITY;
        for (final LayoutPoint point : polygon) {
            support = Math.max(support, outward.x() * point.x() + outward.y() * point.y());
        }
        int count = 0;
        for (final Rectangle2D obstacle : obstacles) {
            final double[] onTangent = projection(obstacle, tangentX, tangentY);
            final double[] onNormal = projection(obstacle, outward.x(), outward.y());
            if (onTangent[0] <= maxT && onTangent[1] >= minT
                    && onNormal[0] <= support + depth && onNormal[1] >= support - depth) {
                count++;
            }
        }
        return count;
    }

    private static double[] projection(final Rectangle2D rectangle, final double normalX,
            final double normalY) {
        final double first = normalX * rectangle.getMinX() + normalY * rectangle.getMinY();
        final double second = normalX * rectangle.getMinX() + normalY * rectangle.getMaxY();
        final double third = normalX * rectangle.getMaxX() + normalY * rectangle.getMinY();
        final double fourth = normalX * rectangle.getMaxX() + normalY * rectangle.getMaxY();
        return new double[] { Math.min(Math.min(first, second), Math.min(third, fourth)),
            Math.max(Math.max(first, second), Math.max(third, fourth)) };
    }

    private static double areaSupport(final Rectangle2D area, final LayoutPoint normal) {
        final double first = normal.x() * area.getMinX() + normal.y() * area.getMinY();
        final double second = normal.x() * area.getMinX() + normal.y() * area.getMaxY();
        final double third = normal.x() * area.getMaxX() + normal.y() * area.getMinY();
        final double fourth = normal.x() * area.getMaxX() + normal.y() * area.getMaxY();
        return Math.max(Math.max(first, second), Math.max(third, fourth));
    }

    private static PlacedLabel placeNode(final Context context, final ProjectedNodeKey key,
            final boolean forced, final PlacedLabel previous) {
        final ProjectedNode node = context.nodes.get(key);
        final String fullText = node.label().displayText();
        final PlacedLabel retained = retained(context, key, previous);
        if (retained != null) {
            return retained;
        }
        final PlacedLabel laddered = ladder(context, key, ProjectedEndpointKey.ofNode(key), fullText, forced);
        if (laddered != null) {
            return laddered;
        }
        if (forced) {
            return baseSlot(context, key, ProjectedEndpointKey.ofNode(key), fullText, true);
        }
        return hoverOnly(context, key, ProjectedEndpointKey.ofNode(key), fullText);
    }

    private static PlacedLabel retained(final Context context, final ProjectedNodeKey key,
            final PlacedLabel previous) {
        if (previous == null || previous.mode() == PlacedLabel.Mode.HOVER_ONLY) {
            return null;
        }
        final double centerX = context.screenX(key);
        final double centerY = context.screenY(key);
        final double radius = context.radius(key);
        final double[] anchor = slotAnchor(previous.slot(), centerX, centerY, radius,
            previous.width(), previous.height());
        final Rectangle2D candidate = rectangle(anchor[0], anchor[1], previous.width(), previous.height());
        if (!context.request.placementArea().contains(candidate)
                || intersectsAny(context.obstacles, candidate)) {
            return null;
        }
        context.obstacles.add(candidate);
        return new PlacedLabel(previous.endpoint(), previous.text(), previous.font(), previous.mode(),
            previous.rung(), anchor[0], anchor[1], previous.width(), previous.height(),
            previous.truncated(), isForced(context, key), previous.emphaticAtAnchor(),
            false, false, nodeLeader(previous.slot(), centerX, centerY, radius, anchor[0], anchor[1],
                previous.width(), previous.height()),
            previous.slot());
    }

    private static PlacedLabel ladder(final Context context, final ProjectedNodeKey key,
            final ProjectedEndpointKey endpoint, final String fullText, final boolean forced) {
        final double centerX = context.screenX(key);
        final double centerY = context.screenY(key);
        final double radius = context.radius(key);
        boolean fullTextSlotWasFree = false;
        for (final LadderRung rung : LADDER) {
            if (forced && rung.rung != PlacedLabel.Rung.FULL_NEAR
                    && rung.rung != PlacedLabel.Rung.FULL_DISPLACED) {
                continue;
            }
            for (final Slot slot : rung.far ? FAR_SLOTS : NEAR_SLOTS) {
                final Font font = rung.font(context.fonts);
                final double limit = slotMaxWidth(slot);
                if (!rung.truncating && textWidth(fullText, font) > limit) {
                    continue;
                }
                final String candidateText = rung.truncating ? truncateTo(fullText, font, limit) : fullText;
                final Rectangle2D size = screenBounds(candidateText, font);
                final double[] anchor = slotAnchor(slot, centerX, centerY, radius,
                    size.getWidth(), size.getHeight());
                final Rectangle2D candidate = rectangle(anchor[0], anchor[1], size.getWidth(),
                    size.getHeight());
                if (!context.request.placementArea().contains(candidate)) {
                    continue;
                }
                final boolean clash = intersectsAny(context.obstacles, candidate);
                if (!rung.truncating && !clash) {
                    fullTextSlotWasFree = true;
                }
                if (clash) {
                    continue;
                }
                context.obstacles.add(candidate);
                final boolean truncated = rung.truncating && !candidateText.equals(fullText);
                final boolean fullTextWasFree = rung.truncating
                    ? fullTextCandidateWasFree(context, fullText, forced, centerX, centerY, radius)
                    : fullTextSlotWasFree;
                return new PlacedLabel(endpoint, candidateText, font, PlacedLabel.Mode.INTERIOR,
                    rung.rung, anchor[0], anchor[1], size.getWidth(), size.getHeight(),
                    truncated, forced, false, false,
                    fullTextWasFree, nodeLeader(slot, centerX, centerY, radius, anchor[0], anchor[1],
                        size.getWidth(), size.getHeight()), slot);
            }
        }
        return null;
    }

    /**
     * Design §8.3.3 predicate: whether, at the moment this label is processed, some full-text
     * rung candidate the label is allowed to try would have been accepted (cap, area, obstacles).
     */
    private static boolean fullTextCandidateWasFree(final Context context, final String fullText,
            final boolean forced, final double centerX, final double centerY, final double radius) {
        for (final LadderRung rung : LADDER) {
            if (rung.truncating) {
                continue;
            }
            if (forced && rung.rung != PlacedLabel.Rung.FULL_NEAR
                    && rung.rung != PlacedLabel.Rung.FULL_DISPLACED) {
                continue;
            }
            for (final Slot slot : rung.far ? FAR_SLOTS : NEAR_SLOTS) {
                final Font font = rung.font(context.fonts);
                if (textWidth(fullText, font) > slotMaxWidth(slot)) {
                    continue;
                }
                final Rectangle2D size = screenBounds(fullText, font);
                final double[] anchor = slotAnchor(slot, centerX, centerY, radius,
                    size.getWidth(), size.getHeight());
                final Rectangle2D candidate = rectangle(anchor[0], anchor[1], size.getWidth(),
                    size.getHeight());
                if (context.request.placementArea().contains(candidate)
                        && !intersectsAny(context.obstacles, candidate)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Core trim: any start (disc rim, hull boundary). End = first entry of [start -> anchor] into
     * inflate(R, LEADER_CLEARANCE), when that entry is strictly between start and the centre and the
     * surviving segment is at least MIN_VISIBLE_LEADER long.
     *
     * Scaffold: returns today's geometry (end == anchor); the trim lands in the clearance commit.
     */
    private static Optional<LeaderLine> leader(final LayoutPoint start, final double anchorX,
            final double anchorY, final double width, final double height) {
        return Optional.of(new LeaderLine(start, LayoutPoint.of(anchorX, anchorY)));
    }

    /** Node labels: computes the rim start, then delegates to the core. */
    private static Optional<LeaderLine> nodeLeader(final Slot slot, final double centerX,
            final double centerY, final double radius, final double anchorX, final double anchorY,
            final double width, final double height) {
        if (slot == Slot.ABOVE || slot == Slot.BELOW) {
            return Optional.empty();
        }
        final double dx = anchorX - centerX;
        final double dy = anchorY - centerY;
        final double distance = Math.max(1e-6, Math.hypot(dx, dy));
        final LayoutPoint start = LayoutPoint.of(centerX + dx / distance * radius,
            centerY + dy / distance * radius);
        return leader(start, anchorX, anchorY, width, height);
    }

    private static PlacedLabel baseSlot(final Context context, final ProjectedNodeKey key,
            final ProjectedEndpointKey endpoint, final String fullText, final boolean forced) {
        final Font font = context.fonts.full();
        final Rectangle2D size = screenBounds(fullText, font);
        final double[] anchor = slotAnchor(Slot.ABOVE, context.screenX(key), context.screenY(key),
            context.radius(key), size.getWidth(), size.getHeight());
        final Rectangle2D candidate = rectangle(anchor[0], anchor[1], size.getWidth(), size.getHeight());
        context.obstacles.add(candidate);
        return new PlacedLabel(endpoint, fullText, font, PlacedLabel.Mode.INTERIOR,
            PlacedLabel.Rung.FULL_NEAR, anchor[0], anchor[1], size.getWidth(), size.getHeight(),
            false, forced, false, true, false, Optional.<LeaderLine>empty(), Slot.ABOVE);
    }

    private static PlacedLabel hoverOnly(final Context context, final ProjectedNodeKey key,
            final ProjectedEndpointKey endpoint, final String fullText) {
        final Font font = context.fonts.full();
        final Rectangle2D size = screenBounds(fullText, font);
        final double[] anchor = slotAnchor(Slot.ABOVE, context.screenX(key), context.screenY(key),
            context.radius(key), size.getWidth(), size.getHeight());
        return new PlacedLabel(endpoint, fullText, font, PlacedLabel.Mode.HOVER_ONLY,
            PlacedLabel.Rung.HOVER_ONLY, anchor[0], anchor[1], size.getWidth(), size.getHeight(),
            false, false, false, false, false, Optional.<LeaderLine>empty(), Slot.ABOVE);
    }

    private static List<PlacedLabel> filterByLevel(final LabelPlacementRequest request,
            final List<PlacedLabel> placed) {
        if (request.renderingLevel() != RenderingLevel.OVER_TARGET) {
            return placed;
        }
        final List<PlacedLabel> filtered = new ArrayList<PlacedLabel>();
        for (final PlacedLabel label : placed) {
            if (label.forced() || isRequiredEmphaticEnclosure(request, label)) {
                filtered.add(label);
            }
        }
        return filtered;
    }

    private static boolean isRequiredEmphaticEnclosure(final LabelPlacementRequest request,
            final PlacedLabel label) {
        if (!label.endpoint().isEnclosure()) {
            return false;
        }
        final EnclosureKey target = label.endpoint().enclosure().get();
        for (final ProjectedEnclosure enclosure : request.projection().enclosures()) {
            if (enclosure.endpointKeys().contains(target)) {
                return enclosure.boundaryTier() == BoundaryTier.EMPHATIC;
            }
        }
        return false;
    }

    private enum FontSelector {
        FULL, DENSE
    }

    private static final class LadderRung {
        final PlacedLabel.Rung rung;
        final FontSelector fontSelector;
        final boolean truncating;
        final boolean far;

        LadderRung(final PlacedLabel.Rung rung, final FontSelector fontSelector, final boolean truncating,
                final boolean far) {
            this.rung = rung;
            this.fontSelector = fontSelector;
            this.truncating = truncating;
            this.far = far;
        }

        Font font(final LabelFonts fonts) {
            return fontSelector == FontSelector.DENSE ? fonts.dense() : fonts.full();
        }
    }

    private static final LadderRung[] LADDER = {
        new LadderRung(PlacedLabel.Rung.FULL_NEAR, FontSelector.FULL, false, false),
        new LadderRung(PlacedLabel.Rung.FULL_DISPLACED, FontSelector.FULL, false, true),
        new LadderRung(PlacedLabel.Rung.DENSE_NEAR, FontSelector.DENSE, false, false),
        new LadderRung(PlacedLabel.Rung.DENSE_DISPLACED, FontSelector.DENSE, false, true),
        new LadderRung(PlacedLabel.Rung.TRUNCATED_NEAR, FontSelector.FULL, true, false),
        new LadderRung(PlacedLabel.Rung.TRUNCATED_DISPLACED, FontSelector.FULL, true, true),
        new LadderRung(PlacedLabel.Rung.TRUNCATED_DENSE_NEAR, FontSelector.DENSE, true, false),
        new LadderRung(PlacedLabel.Rung.TRUNCATED_DENSE_DISPLACED, FontSelector.DENSE, true, true)
    };

    static double[] slotAnchor(final Slot slot, final double centerX, final double centerY,
            final double radius, final double width, final double height) {
        switch (slot) {
            case ABOVE:
                return new double[] { centerX, centerY - radius - SLOT_GAP - height / 2 };
            case BELOW:
                return new double[] { centerX, centerY + radius + SLOT_GAP + height / 2 };
            case RIGHT:
                return new double[] { centerX + radius + SLOT_GAP + width / 2, centerY };
            case LEFT:
                return new double[] { centerX - radius - SLOT_GAP - width / 2, centerY };
            case ABOVE_FAR:
                return new double[] { centerX, centerY - radius - DISPLACED_OFFSET - height / 2 };
            case BELOW_FAR:
                return new double[] { centerX, centerY + radius + DISPLACED_OFFSET + height / 2 };
            case RIGHT_FAR:
                return new double[] { centerX + radius + DISPLACED_OFFSET + width / 2, centerY };
            case LEFT_FAR:
                return new double[] { centerX - radius - DISPLACED_OFFSET - width / 2, centerY };
            case ABOVE_RIGHT:
                return new double[] { centerX + radius + SLOT_GAP + width / 2,
                    centerY - radius - SLOT_GAP - height / 2 };
            case ABOVE_LEFT:
                return new double[] { centerX - radius - SLOT_GAP - width / 2,
                    centerY - radius - SLOT_GAP - height / 2 };
            case BELOW_RIGHT:
                return new double[] { centerX + radius + SLOT_GAP + width / 2,
                    centerY + radius + SLOT_GAP + height / 2 };
            default:
                return new double[] { centerX - radius - SLOT_GAP - width / 2,
                    centerY + radius + SLOT_GAP + height / 2 };
        }
    }

    static double slotMaxWidth(final Slot slot) {
        switch (slot) {
            case ABOVE:
            case BELOW:
            case ABOVE_FAR:
            case BELOW_FAR:
                return VERTICAL_MAX_WIDTH;
            case RIGHT:
            case LEFT:
            case RIGHT_FAR:
            case LEFT_FAR:
                return HORIZONTAL_MAX_WIDTH;
            default:
                return DIAGONAL_MAX_WIDTH;
        }
    }

    static Rectangle2D screenBounds(final String text, final Font font) {
        return font.getStringBounds(text, SCREEN_FRC);
    }

    static double textWidth(final String text, final Font font) {
        return screenBounds(text, font).getWidth();
    }

    static String truncateTo(final String text, final Font font, final double limit) {
        if (textWidth(text, font) <= limit) {
            return text;
        }
        for (int cut = text.length() - 1; cut > 2; cut--) {
            final String candidate = stripTrailing(text.substring(0, cut)) + ELLIPSIS;
            if (textWidth(candidate, font) <= limit) {
                return candidate;
            }
        }
        return text.substring(0, Math.min(3, text.length())) + ELLIPSIS;
    }

    private static String stripTrailing(final String value) {
        int end = value.length();
        while (end > 0 && Character.isWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }

    static Rectangle2D rectangle(final double x, final double y, final double width, final double height) {
        return new Rectangle2D.Double(x - width * 0.5, y - height * 0.5, width, height);
    }

    private static boolean intersectsAny(final List<Rectangle2D> obstacles, final Rectangle2D candidate) {
        for (final Rectangle2D obstacle : obstacles) {
            if (obstacle.intersects(candidate)) {
                return true;
            }
        }
        return false;
    }
}
