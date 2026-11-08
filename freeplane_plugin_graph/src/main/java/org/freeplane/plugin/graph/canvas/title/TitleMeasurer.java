package org.freeplane.plugin.graph.canvas.title;

import java.awt.Font;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.freeplane.plugin.graph.projection.input.TitleText;

/**
 * Measures titles, memoizing the numbers per generation, deferring work beyond a per-pass budget, and draining a
 * queue of deferred keys on later passes. Plain titles are free: they never consume budget and are never deferred.
 */
public final class TitleMeasurer {
    public static final int DEFAULT_MAX_NEW_MEASUREMENTS_PER_PASS = 200;

    enum Form {
        RENDERED, DEGRADED, DEGRADED_TEXT, DEFERRED
    }

    private static final class Key {
        private final TitleText title;
        private final String family;
        private final int style;
        private final double size;
        private final Form form;

        private Key(final TitleText title, final Font font, final Form form) {
            this.title = title;
            this.family = font.getFamily();
            this.style = font.getStyle();
            this.size = font.getSize2D();
            this.form = form;
        }

        @Override
        public boolean equals(final Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key)) {
                return false;
            }
            final Key that = (Key) other;
            return style == that.style && form == that.form && Double.compare(size, that.size) == 0
                && family.equals(that.family) && title.equals(that.title);
        }

        @Override
        public int hashCode() {
            return Objects.hash(title, family, Integer.valueOf(style), Double.valueOf(size), form);
        }
    }

    private final IconSource icons;
    private final int budget;
    private final Map<Key, MeasuredTitle> cache = new LinkedHashMap<Key, MeasuredTitle>();
    private final Set<Key> pending = new LinkedHashSet<Key>();
    private int newMeasurementsInPass;
    private int deferredInPass;
    private long generation;
    private boolean generationKnown;
    private boolean stalled;
    private String stallDiagnostic;

    public TitleMeasurer() {
        this(new FormulaIcons(), DEFAULT_MAX_NEW_MEASUREMENTS_PER_PASS);
    }

    public TitleMeasurer(final IconSource icons, final int maxNewMeasurementsPerPass) {
        this(icons, maxNewMeasurementsPerPass, false);
    }

    /** The three-argument form admits a zero budget for tests inside this package; production never uses it. */
    TitleMeasurer(final IconSource icons, final int maxNewMeasurementsPerPass, final boolean testOnly) {
        this.icons = Objects.requireNonNull(icons, "icons");
        if (maxNewMeasurementsPerPass < 0 || (!testOnly && maxNewMeasurementsPerPass == 0)) {
            throw new IllegalArgumentException("maxNewMeasurementsPerPass must be positive");
        }
        this.budget = maxNewMeasurementsPerPass;
    }

    public void beginPass(final long projectionGeneration) {
        if (!generationKnown || projectionGeneration != generation) {
            cache.clear();
            pending.clear();
            stalled = false;
            stallDiagnostic = null;
            generation = projectionGeneration;
            generationKnown = true;
        }
        newMeasurementsInPass = 0;
        deferredInPass = 0;
    }

    /** Closes a pass and records the stall state when a pass deferred work and measured nothing. */
    public void endPass() {
        if (deferredInPass > 0 && newMeasurementsInPass == 0) {
            stalled = true;
            stallDiagnostic = "FORMULA_MEASUREMENT_STALL: " + pending.size() + " formula titles unmeasured";
        }
    }

    public MeasuredTitle measure(final TitleText title, final Font font) {
        require(title, font);
        final MeasuredTitle hit = cache.get(new Key(title, font, Form.RENDERED));
        if (hit != null) {
            return hit;
        }
        if (!title.hasMath()) {
            final MeasuredTitle plain = plainMeasurement(title, font);
            cache.put(new Key(title, font, Form.RENDERED), plain);
            return plain;
        }
        if (!consumeBudgetSlot()) {
            return defer(title, font);
        }
        final MeasuredTitle computed = computedMeasurement(title, font);
        cache.put(new Key(title, font, Form.RENDERED), computed);
        pending.remove(new Key(title, font, Form.DEFERRED));
        return computed;
    }

    public MeasuredTitle measureDegraded(final TitleText title, final Font font) {
        require(title, font);
        final MeasuredTitle hit = cache.get(new Key(title, font, Form.DEGRADED));
        if (hit != null) {
            return hit;
        }
        if (!title.hasMath()) {
            return measure(title, font);
        }
        if (!consumeBudgetSlot()) {
            return defer(title, font);
        }
        final MeasuredTitle degraded = degradedMeasurement(title, font);
        cache.put(new Key(title, font, Form.DEGRADED), degraded);
        pending.remove(new Key(title, font, Form.DEFERRED));
        return degraded;
    }

    public MeasuredTitle measureDegradedText(final String text, final Font font, final boolean deferredForBudget) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(font, "font");
        final Key key = new Key(TitleText.plain(text), font, Form.DEGRADED_TEXT);
        MeasuredTitle measured = cache.get(key);
        if (measured == null) {
            final Rectangle2D bounds = font.getStringBounds(text, TitleMetrics.SCREEN_FRC);
            final double width = bounds.getWidth();
            final double ascent = -bounds.getY();
            final double descent = bounds.getHeight() + bounds.getY();
            final double height = Math.max(ascent + descent, 1.0e-6);
            final List<MeasuredRun> runs = new ArrayList<MeasuredRun>();
            runs.add(MeasuredRun.plain(text, 0.0, width, ascent, descent));
            measured = MeasuredTitle.degraded(runs, width, height, ascent, descent);
            cache.put(key, measured);
        }
        return deferredForBudget ? measured.deferred() : measured;
    }

    public MeasuredTitle defer(final TitleText title, final Font font) {
        require(title, font);
        final MeasuredTitle deferred = degradedMeasurement(title, font).deferred();
        cache.put(new Key(title, font, Form.DEFERRED), deferred);
        pending.add(new Key(title, font, Form.DEFERRED));
        deferredInPass++;
        return deferred;
    }

    public boolean isKnown(final TitleText title, final Font font) {
        return cache.containsKey(new Key(title, font, Form.RENDERED))
            || cache.containsKey(new Key(title, font, Form.DEGRADED));
    }

    public boolean isPending(final TitleText title, final Font font) {
        return pending.contains(new Key(title, font, Form.DEFERRED));
    }

    public IconSource iconSource() {
        return icons;
    }

    public boolean consumeBudgetSlot() {
        if (newMeasurementsInPass >= budget) {
            return false;
        }
        newMeasurementsInPass++;
        return true;
    }

    public boolean budgetExhaustedInLastPass() {
        return deferredInPass > 0;
    }

    public boolean stalled() {
        return stalled;
    }

    public String stallDiagnostic() {
        return stallDiagnostic;
    }

    public int pendingCount() {
        return pending.size();
    }

    public int cacheSize() {
        return cache.size();
    }

    public int newMeasurementsInLastPass() {
        return newMeasurementsInPass;
    }

    public int deferredInLastPass() {
        return deferredInPass;
    }

    private MeasuredTitle computedMeasurement(final TitleText title, final Font font) {
        final List<MeasuredRun> runs = new ArrayList<MeasuredRun>();
        double x = 0.0;
        double ascent = 0.0;
        double descent = 0.0;
        for (final TitleText.Run run : title.runs()) {
            if (run instanceof TitleText.Plain) {
                final String text = ((TitleText.Plain) run).text();
                final Rectangle2D bounds = font.getStringBounds(text, TitleMetrics.SCREEN_FRC);
                final double runAscent = -bounds.getY();
                final double runDescent = bounds.getHeight() + bounds.getY();
                runs.add(MeasuredRun.plain(text, x, bounds.getWidth(), runAscent, runDescent));
                x += bounds.getWidth();
                ascent = Math.max(ascent, runAscent);
                descent = Math.max(descent, runDescent);
                continue;
            }
            final TitleText.Math math = (TitleText.Math) run;
            final IconBox box = icons.icon(math.source(), TitleMetrics.px(font), TitleMetrics.STYLE);
            if (!isUsable(box, font)) {
                return degradedMeasurement(title, font);
            }
            runs.add(MeasuredRun.math(math.source(), math.rawSource(), x, box.width(), box.ascent(), box.descent()));
            x += box.width();
            ascent = Math.max(ascent, box.ascent());
            descent = Math.max(descent, box.descent());
        }
        if (runs.isEmpty()) {
            return degradedMeasurement(title, font);
        }
        return MeasuredTitle.rendered(runs, x, ascent + descent, ascent, descent);
    }

    private boolean isUsable(final IconBox box, final Font font) {
        if (box == null) {
            return false;
        }
        if (!Double.isFinite(box.width()) || !(box.width() > 0.0)) {
            return false;
        }
        if (!Double.isFinite(box.height()) || !(box.height() > 0.0)) {
            return false;
        }
        if (!Double.isFinite(box.ascent()) || !Double.isFinite(box.descent())) {
            return false;
        }
        if (box.ascent() < 0.0 || box.descent() < 0.0) {
            return false;
        }
        return box.height() <= TitleMetrics.MAX_HEIGHT_FACTOR * TitleMetrics.lineHeight(font);
    }

    private MeasuredTitle degradedMeasurement(final TitleText title, final Font font) {
        final String text = TitleTruncation.degradedPlainText(title);
        final Rectangle2D bounds = font.getStringBounds(text, TitleMetrics.SCREEN_FRC);
        final double ascent = -bounds.getY();
        final double descent = bounds.getHeight() + bounds.getY();
        final List<MeasuredRun> runs = new ArrayList<MeasuredRun>();
        runs.add(MeasuredRun.plain(text, 0.0, bounds.getWidth(), ascent, descent));
        return MeasuredTitle.degraded(runs, bounds.getWidth(), Math.max(ascent + descent, 1.0e-6), ascent, descent);
    }

    private MeasuredTitle plainMeasurement(final TitleText title, final Font font) {
        final String text = title.plainSource();
        final Rectangle2D bounds = font.getStringBounds(text, TitleMetrics.SCREEN_FRC);
        final double ascent = -bounds.getY();
        final double descent = bounds.getHeight() + bounds.getY();
        final List<MeasuredRun> runs = new ArrayList<MeasuredRun>();
        runs.add(MeasuredRun.plain(text, 0.0, Math.max(bounds.getWidth(), 0.0), ascent, descent));
        return MeasuredTitle.rendered(runs, Math.max(bounds.getWidth(), 0.0),
            Math.max(ascent + descent, 1.0e-6), ascent, descent);
    }

    private void require(final TitleText title, final Font font) {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(font, "font");
    }
}
