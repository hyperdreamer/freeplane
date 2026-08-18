package org.freeplane.plugin.graph.canvas;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.swing.UIManager;

import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.DisplaySettings.CanvasTheme;

public final class GraphTheme {
    private static final List<Color> APPROVED_PALETTE = Collections.unmodifiableList(Arrays.asList(
        new Color(48, 104, 189),
        new Color(20, 135, 122),
        new Color(210, 122, 44),
        new Color(174, 76, 104),
        new Color(103, 83, 170),
        new Color(82, 139, 72)));

    private final Color background;
    private final Color labelColor;
    private final Color edgeColor;
    private final Color nodeFill;
    private final Color nodeStroke;
    private final Color selectionColor;
    private final Color hoverColor;
    private final Color searchColor;
    private final Color warningColor;
    private final Color pinColor;
    private final Color mutedColor;
    private final Color previewColor;
    private final BasicStroke edgeStroke;
    private final BasicStroke emphaticHullStrokeStyle;
    private final BasicStroke subtleHullStrokeStyle;
    private final BasicStroke selectionStroke;
    private final BasicStroke hoverStroke;
    private final BasicStroke searchStroke;
    private final Font labelFont;
    private final Font denseLabelFont;
    private final Font overTargetLabelFont;
    private final List<Color> mapPalette;

    private GraphTheme(final Color background, final Color labelColor, final Color edgeColor,
            final Color nodeFill, final Color nodeStroke, final Color selectionColor, final Color hoverColor,
            final Color searchColor, final Color warningColor, final Color pinColor, final Color mutedColor,
            final Color previewColor, final List<Color> mapPalette) {
        this.background = requireColor(background, "background");
        this.labelColor = requireColor(labelColor, "labelColor");
        this.edgeColor = requireColor(edgeColor, "edgeColor");
        this.nodeFill = requireColor(nodeFill, "nodeFill");
        this.nodeStroke = requireColor(nodeStroke, "nodeStroke");
        this.selectionColor = requireColor(selectionColor, "selectionColor");
        this.hoverColor = requireColor(hoverColor, "hoverColor");
        this.searchColor = requireColor(searchColor, "searchColor");
        this.warningColor = requireColor(warningColor, "warningColor");
        this.pinColor = requireColor(pinColor, "pinColor");
        this.mutedColor = requireColor(mutedColor, "mutedColor");
        this.previewColor = requireColor(previewColor, "previewColor");
        this.edgeStroke = new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        this.emphaticHullStrokeStyle = new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        this.subtleHullStrokeStyle = new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        this.selectionStroke = new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        this.hoverStroke = new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        this.searchStroke = new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
            10.0f, new float[] { 5.0f, 4.0f }, 0.0f);
        this.labelFont = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        this.denseLabelFont = new Font(Font.SANS_SERIF, Font.PLAIN, 9);
        this.overTargetLabelFont = new Font(Font.SANS_SERIF, Font.PLAIN, 7);
        this.mapPalette = copyPalette(mapPalette);
    }

    public static GraphTheme resolve(final CanvasTheme requested) {
        return resolve(requested, APPROVED_PALETTE);
    }

    public static GraphTheme resolve(final CanvasTheme requested, final List<Color> callerPalette) {
        final CanvasTheme theme = Objects.requireNonNull(requested, "requested");
        final List<Color> palette = callerPalette == null || callerPalette.isEmpty()
            ? APPROVED_PALETTE : callerPalette;
        final Color uiBackground = UIManager.getColor("Panel.background");
        final Color uiForeground = UIManager.getColor("Label.foreground");
        final boolean dark;
        if (theme == CanvasTheme.DARK) {
            dark = true;
        }
        else if (theme == CanvasTheme.LIGHT) {
            dark = false;
        }
        else {
            dark = isDark(uiBackground);
        }
        if (dark) {
            Color background = theme == CanvasTheme.FOLLOW_FREEPLANE && uiBackground != null
                ? uiBackground : new Color(31, 35, 42);
            Color foreground = theme == CanvasTheme.FOLLOW_FREEPLANE && uiForeground != null
                ? uiForeground : new Color(240, 244, 248);
            return darkTheme(background, foreground, palette);
        }
        Color background = theme == CanvasTheme.FOLLOW_FREEPLANE && uiBackground != null
            ? uiBackground : new Color(250, 251, 253);
        Color foreground = theme == CanvasTheme.FOLLOW_FREEPLANE && uiForeground != null
            ? uiForeground : new Color(31, 38, 48);
        return lightTheme(background, foreground, palette);
    }

    public static GraphTheme from(final CanvasTheme requested) {
        return resolve(requested);
    }

    public static GraphTheme of(final CanvasTheme requested) {
        return resolve(requested);
    }

    private static GraphTheme lightTheme(final Color background, final Color foreground,
            final List<Color> palette) {
        return new GraphTheme(background, foreground, new Color(73, 84, 100),
            new Color(229, 239, 249), new Color(38, 91, 137),
            new Color(37, 91, 205), new Color(202, 112, 18), new Color(24, 135, 76),
            new Color(180, 48, 46), new Color(98, 88, 190), new Color(120, 130, 142),
            new Color(64, 113, 184), palette);
    }

    private static GraphTheme darkTheme(final Color background, final Color foreground,
            final List<Color> palette) {
        return new GraphTheme(background, foreground, new Color(190, 202, 217),
            new Color(57, 76, 98), new Color(142, 194, 239),
            new Color(112, 164, 255), new Color(255, 190, 93), new Color(103, 211, 146),
            new Color(255, 117, 117), new Color(190, 177, 255), new Color(151, 163, 178),
            new Color(135, 181, 255), palette);
    }

    public Color background() {
        return background;
    }

    public Color canvasColor() {
        return background;
    }

    public Color canvasBackground() {
        return background;
    }

    public Color labelColor() {
        return labelColor;
    }

    public Color foreground() {
        return labelColor;
    }

    public Color edgeColor() {
        return edgeColor;
    }

    public Color nodeFill() {
        return nodeFill;
    }

    public Color nodeStroke() {
        return nodeStroke;
    }

    public Color selectionColor() {
        return selectionColor;
    }

    public Color hoverColor() {
        return hoverColor;
    }

    public Color searchColor() {
        return searchColor;
    }

    public Color warningColor() {
        return warningColor;
    }

    public Color pinColor() {
        return pinColor;
    }

    public Color mutedColor() {
        return mutedColor;
    }

    public Color previewColor() {
        return previewColor;
    }

    public Color hullFill(final MapReferenceId mapReferenceId, final BoundaryTier tier) {
        final BoundaryTier value = Objects.requireNonNull(tier, "tier");
        if (value == BoundaryTier.EMPHATIC) {
            return treatment(mapReferenceId, 0.25);
        }
        if (value == BoundaryTier.SUBTLE) {
            return treatment(mapReferenceId, 0.125);
        }
        return mutedColor;
    }

    public Color hullStroke(final MapReferenceId mapReferenceId, final BoundaryTier tier) {
        final BoundaryTier value = Objects.requireNonNull(tier, "tier");
        if (value == BoundaryTier.EMPHATIC) {
            return treatment(mapReferenceId, 0.85);
        }
        if (value == BoundaryTier.SUBTLE) {
            return treatment(mapReferenceId, 0.55);
        }
        return mutedColor;
    }

    public BasicStroke hullStrokeStyle(final BoundaryTier tier) {
        final BoundaryTier value = Objects.requireNonNull(tier, "tier");
        return value == BoundaryTier.EMPHATIC ? emphaticHullStrokeStyle : subtleHullStrokeStyle;
    }

    public BasicStroke edgeStroke() {
        return edgeStroke;
    }

    public BasicStroke selectionStroke() {
        return selectionStroke;
    }

    public BasicStroke hoverStroke() {
        return hoverStroke;
    }

    public BasicStroke searchStroke() {
        return searchStroke;
    }

    public Font labelFont() {
        return labelFont;
    }

    public Font denseLabelFont() {
        return denseLabelFont;
    }

    public Font overTargetLabelFont() {
        return overTargetLabelFont;
    }

    public Font labelFont(final RenderingLevel level) {
        final RenderingLevel value = Objects.requireNonNull(level, "level");
        if (value == RenderingLevel.DENSE) {
            return denseLabelFont;
        }
        if (value == RenderingLevel.OVER_TARGET) {
            return overTargetLabelFont;
        }
        return labelFont;
    }

    public List<Color> mapPalette() {
        return mapPalette;
    }

    private Color treatment(final MapReferenceId mapReferenceId, final double baseWeight) {
        final Color base = mapColor(mapReferenceId);
        return new Color(blend(background.getRed(), base.getRed(), baseWeight),
            blend(background.getGreen(), base.getGreen(), baseWeight),
            blend(background.getBlue(), base.getBlue(), baseWeight));
    }

    private Color mapColor(final MapReferenceId mapReferenceId) {
        final MapReferenceId value = Objects.requireNonNull(mapReferenceId, "mapReferenceId");
        final List<Color> palette = mapPalette();
        return palette.get(Math.floorMod(value.value().hashCode(), palette.size()));
    }

    private static int blend(final int backgroundChannel, final int baseChannel, final double baseWeight) {
        return (int) Math.round(backgroundChannel * (1.0 - baseWeight) + baseChannel * baseWeight);
    }

    private static List<Color> copyPalette(final List<Color> values) {
        Objects.requireNonNull(values, "mapPalette");
        if (values.isEmpty()) {
            throw new IllegalArgumentException("mapPalette must not be empty");
        }
        final List<Color> copy = new ArrayList<Color>(values.size());
        for (final Color value : values) {
            copy.add(requireColor(value, "mapPalette entry"));
        }
        return Collections.unmodifiableList(copy);
    }

    private static Color requireColor(final Color value, final String name) {
        return Objects.requireNonNull(value, name);
    }

    private static boolean isDark(final Color color) {
        if (color == null) {
            return false;
        }
        final double luminance = (0.2126 * color.getRed() + 0.7152 * color.getGreen()
            + 0.0722 * color.getBlue()) / 255.0;
        return luminance < 0.5;
    }
}
