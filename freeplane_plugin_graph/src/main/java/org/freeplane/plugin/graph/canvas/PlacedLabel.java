package org.freeplane.plugin.graph.canvas;

import java.awt.Font;
import java.awt.geom.Rectangle2D;
import java.util.Objects;
import java.util.Optional;

import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;

public final class PlacedLabel {
    public enum Mode {
        INTERIOR, ARC, EXTERNAL, HOVER_ONLY
    }

    public enum Rung {
        FULL_NEAR, FULL_DISPLACED, DENSE_NEAR, DENSE_DISPLACED,
        TRUNCATED_NEAR, TRUNCATED_DISPLACED, TRUNCATED_DENSE_NEAR, TRUNCATED_DENSE_DISPLACED, HOVER_ONLY
    }

    private final ProjectedEndpointKey endpoint;
    private final String text;
    private final Font font;
    private final Mode mode;
    private final Rung rung;
    private final double anchorX;
    private final double anchorY;
    private final double width;
    private final double height;
    private final boolean truncated;
    private final boolean forced;
    private final boolean emphaticAtAnchor;
    private final boolean forcedAtBaseSlot;
    private final boolean fullTextSlotWasFree;
    private final Optional<LeaderLine> leader;
    private final ScreenLabelPlacement.Slot slot;

    PlacedLabel(final ProjectedEndpointKey endpoint, final String text, final Font font, final Mode mode,
            final Rung rung, final double anchorX, final double anchorY, final double width,
            final double height, final boolean truncated, final boolean forced,
            final boolean emphaticAtAnchor, final boolean forcedAtBaseSlot,
            final boolean fullTextSlotWasFree, final Optional<LeaderLine> leader,
            final ScreenLabelPlacement.Slot slot) {
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.text = Objects.requireNonNull(text, "text");
        this.font = Objects.requireNonNull(font, "font");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.rung = Objects.requireNonNull(rung, "rung");
        if (!Double.isFinite(anchorX) || !Double.isFinite(anchorY) || !Double.isFinite(width)
                || !Double.isFinite(height) || !(width > 0.0) || !(height > 0.0)) {
            throw new IllegalArgumentException("Placed label geometry must be finite and positive");
        }
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.width = width;
        this.height = height;
        this.truncated = truncated;
        this.forced = forced;
        this.emphaticAtAnchor = emphaticAtAnchor;
        this.forcedAtBaseSlot = forcedAtBaseSlot;
        this.fullTextSlotWasFree = fullTextSlotWasFree;
        this.leader = Objects.requireNonNull(leader, "leader");
        this.slot = slot;
    }

    public ProjectedEndpointKey endpoint() {
        return endpoint;
    }

    public String text() {
        return text;
    }

    public Font font() {
        return font;
    }

    public Mode mode() {
        return mode;
    }

    public Rung rung() {
        return rung;
    }

    public double anchorX() {
        return anchorX;
    }

    public double anchorY() {
        return anchorY;
    }

    public double width() {
        return width;
    }

    public double height() {
        return height;
    }

    public Rectangle2D bounds() {
        return new Rectangle2D.Double(anchorX - width * 0.5, anchorY - height * 0.5, width, height);
    }

    public boolean truncated() {
        return truncated;
    }

    public boolean forced() {
        return forced;
    }

    public boolean emphaticAtAnchor() {
        return emphaticAtAnchor;
    }

    public boolean forcedAtBaseSlot() {
        return forcedAtBaseSlot;
    }

    public boolean fullTextSlotWasFree() {
        return fullTextSlotWasFree;
    }

    public Optional<LeaderLine> leader() {
        return leader;
    }

    ScreenLabelPlacement.Slot slot() {
        return slot;
    }
}
