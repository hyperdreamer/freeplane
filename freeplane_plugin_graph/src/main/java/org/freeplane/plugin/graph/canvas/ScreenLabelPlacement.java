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
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.NodeGeometry;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;

public final class ScreenLabelPlacement {
    static final double SLOT_GAP = 6.0;
    static final double DISPLACED_OFFSET = 30.0;
    static final double ARC_GAP = 1.0;
    static final double EXTERNAL_GAP = 4.0;
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
            previous.forcedAtBaseSlot(), false, previous.leaderStart(), previous.slot());
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
                return new PlacedLabel(endpoint, candidateText, font, PlacedLabel.Mode.INTERIOR,
                    rung.rung, anchor[0], anchor[1], size.getWidth(), size.getHeight(),
                    rung.truncating && !candidateText.equals(fullText), forced, false, false,
                    fullTextSlotWasFree, Optional.<LayoutPoint>empty(), slot);
            }
        }
        return null;
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
            false, forced, false, true, false, Optional.<LayoutPoint>empty(), Slot.ABOVE);
    }

    private static PlacedLabel hoverOnly(final Context context, final ProjectedNodeKey key,
            final ProjectedEndpointKey endpoint, final String fullText) {
        final Font font = context.fonts.full();
        final Rectangle2D size = screenBounds(fullText, font);
        final double[] anchor = slotAnchor(Slot.ABOVE, context.screenX(key), context.screenY(key),
            context.radius(key), size.getWidth(), size.getHeight());
        return new PlacedLabel(endpoint, fullText, font, PlacedLabel.Mode.HOVER_ONLY,
            PlacedLabel.Rung.HOVER_ONLY, anchor[0], anchor[1], size.getWidth(), size.getHeight(),
            false, false, false, false, false, Optional.<LayoutPoint>empty(), Slot.ABOVE);
    }

    private static List<PlacedLabel> filterByLevel(final LabelPlacementRequest request,
            final List<PlacedLabel> placed) {
        if (request.renderingLevel() != RenderingLevel.OVER_TARGET) {
            return placed;
        }
        final List<PlacedLabel> filtered = new ArrayList<PlacedLabel>();
        for (final PlacedLabel label : placed) {
            if (label.forced()) {
                filtered.add(label);
            }
        }
        return filtered;
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
