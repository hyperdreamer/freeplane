package org.freeplane.plugin.graph.workspace.model;

import java.util.List;
import java.util.Objects;

public final class DisplaySettings {
    public enum CanvasTheme {
        FOLLOW_FREEPLANE,
        LIGHT,
        DARK
    }

    private final boolean showArrowheads;
    private final CanvasTheme canvasTheme;
    private final boolean rememberViewport;
    private final boolean dimUnrelatedNodes;
    private final List<UnknownXml> unknownXml;

    private DisplaySettings(final boolean showArrowheads, final CanvasTheme canvasTheme,
            final boolean rememberViewport, final boolean dimUnrelatedNodes,
            final List<UnknownXml> unknownXml) {
        this.showArrowheads = showArrowheads;
        this.canvasTheme = Objects.requireNonNull(canvasTheme, "canvasTheme");
        this.rememberViewport = rememberViewport;
        this.dimUnrelatedNodes = dimUnrelatedNodes;
        this.unknownXml = UnknownXml.forRecord(unknownXml);
    }

    public static DisplaySettings defaults() {
        return new DisplaySettings(true, CanvasTheme.FOLLOW_FREEPLANE, true, true,
            java.util.Collections.<UnknownXml>emptyList());
    }

    public static DisplaySettings of(final boolean showArrowheads, final CanvasTheme canvasTheme,
            final boolean rememberViewport, final boolean dimUnrelatedNodes,
            final List<UnknownXml> unknownXml) {
        return new DisplaySettings(showArrowheads, canvasTheme, rememberViewport, dimUnrelatedNodes, unknownXml);
    }

    public boolean showArrowheads() {
        return showArrowheads;
    }

    public CanvasTheme canvasTheme() {
        return canvasTheme;
    }

    public boolean rememberViewport() {
        return rememberViewport;
    }

    public boolean dimUnrelatedNodes() {
        return dimUnrelatedNodes;
    }

    public List<UnknownXml> unknownXml() {
        return unknownXml;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DisplaySettings)) {
            return false;
        }
        final DisplaySettings that = (DisplaySettings) other;
        return showArrowheads == that.showArrowheads && rememberViewport == that.rememberViewport
            && dimUnrelatedNodes == that.dimUnrelatedNodes && canvasTheme == that.canvasTheme
            && unknownXml.equals(that.unknownXml);
    }

    @Override
    public int hashCode() {
        return Objects.hash(showArrowheads, canvasTheme, rememberViewport, dimUnrelatedNodes, unknownXml);
    }

    @Override
    public String toString() {
        return "DisplaySettings{" + "showArrowheads=" + showArrowheads + ", canvasTheme=" + canvasTheme
            + ", rememberViewport=" + rememberViewport + ", dimUnrelatedNodes=" + dimUnrelatedNodes
            + ", unknownXml=" + unknownXml + '}';
    }
}
