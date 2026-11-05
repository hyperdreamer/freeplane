package org.freeplane.plugin.graph.canvas.title;

import java.awt.Font;
import java.awt.font.LineMetrics;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.freeplane.plugin.graph.projection.input.TitleText;

/**
 * A paint-ready row: world/paint-unit geometry, the derived font per plain run, and the icon per formula run. The
 * painter consumes this and measures nothing.
 */
public final class TitleRow {
    public enum Form {
        RENDERED, DEGRADED
    }

    private final Form form;
    private final List<RunRow> runs;
    private final double width;
    private final double height;
    private final double ascent;
    private final double descent;
    private final boolean deferredForBudget;

    private TitleRow(final Form form, final List<RunRow> runs, final double width, final double height,
            final double ascent, final double descent, final boolean deferredForBudget) {
        this.form = form;
        this.runs = Collections.unmodifiableList(new ArrayList<RunRow>(runs));
        this.width = width;
        this.height = height;
        this.ascent = ascent;
        this.descent = descent;
        this.deferredForBudget = deferredForBudget;
    }

    public static TitleRow resolve(final MeasuredTitle measured, final IconSource icons, final Font logicalFont,
            final double zoom) {
        if (measured == null) {
            throw new NullPointerException("measured");
        }
        if (icons == null) {
            throw new NullPointerException("icons");
        }
        if (logicalFont == null) {
            throw new NullPointerException("logicalFont");
        }
        if (!Double.isFinite(zoom) || !(zoom > 0.0)) {
            throw new IllegalArgumentException("zoom must be finite and positive");
        }
        final Font worldFont = TitleMetrics.worldFont(logicalFont, zoom);
        final double scale = 1.0 / zoom;
        final int px = TitleMetrics.px(logicalFont);
        final List<RunRow> runs = new ArrayList<RunRow>();
        double x = 0.0;
        double ascent = 0.0;
        double descent = 0.0;
        for (final MeasuredRun run : measured.runs()) {
            if (!run.isMath()) {
                final Rectangle2D bounds = worldFont.getStringBounds(run.source(), TitleMetrics.SCREEN_FRC);
                final LineMetrics line = worldFont.getLineMetrics(run.source(), TitleMetrics.SCREEN_FRC);
                runs.add(new PlainRunRow(run.source(), worldFont, x, bounds.getWidth(), line.getAscent(),
                    line.getDescent()));
                x += bounds.getWidth();
                ascent = Math.max(ascent, line.getAscent());
                descent = Math.max(descent, line.getDescent());
                continue;
            }
            final IconBox box = icons.icon(run.source(), px, TitleMetrics.STYLE);
            if (box == null || box.icon() == null) {
                throw new IllegalStateException("rendered measurement lost its icon");
            }
            final double runWidth = run.width() * scale;
            runs.add(new MathRunRow(run.source(), box.icon(), x, runWidth, run.ascent() * scale,
                run.descent() * scale));
            x += runWidth;
            ascent = Math.max(ascent, run.ascent() * scale);
            descent = Math.max(descent, run.descent() * scale);
        }
        final Form form = measured.availability() == MeasuredTitle.Availability.RENDERED ? Form.RENDERED
            : Form.DEGRADED;
        return new TitleRow(form, runs, x, Math.max(ascent + descent, 1.0e-6), ascent, descent,
            measured.deferredForBudget());
    }

    /** A plain-only row, used for enclosure labels that are never formula-parsed. */
    public static TitleRow plain(final String text, final Font logicalFont, final double zoom) {
        if (text == null) {
            throw new NullPointerException("text");
        }
        final Font worldFont = TitleMetrics.worldFont(logicalFont, zoom);
        final Rectangle2D bounds = worldFont.getStringBounds(text, TitleMetrics.SCREEN_FRC);
        final LineMetrics line = worldFont.getLineMetrics(text, TitleMetrics.SCREEN_FRC);
        final List<RunRow> runs = new ArrayList<RunRow>();
        runs.add(new PlainRunRow(text, worldFont, 0.0, bounds.getWidth(), line.getAscent(), line.getDescent()));
        return new TitleRow(Form.RENDERED, runs, bounds.getWidth(),
            Math.max(line.getAscent() + line.getDescent(), 1.0e-6), line.getAscent(), line.getDescent(), false);
    }

    public Form form() {
        return form;
    }

    public List<RunRow> runs() {
        return runs;
    }

    public double width() {
        return width;
    }

    public double height() {
        return height;
    }

    public double ascent() {
        return ascent;
    }

    public double descent() {
        return descent;
    }

    public boolean deferredForBudget() {
        return deferredForBudget;
    }
}
