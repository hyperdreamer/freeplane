package org.freeplane.plugin.graph.canvas.title;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;

import java.awt.Font;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.Map;

import org.freeplane.plugin.graph.projection.input.TitleText;
import org.freeplane.plugin.graph.projection.input.TitleTextParser;
import org.junit.Test;

public class TitleMeasurerShould {
    private static final Font FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    private static final Font BOLD = new Font(Font.SANS_SERIF, Font.BOLD, 12);

    private static final class StubIcons implements IconSource {
        final Map<String, IconBox> boxes = new HashMap<String, IconBox>();
        int calls;

        @Override
        public IconBox icon(final String source, final int px, final int style) {
            calls++;
            return boxes.get(source);
        }
    }

    private static IconBox box(final double width, final double height, final double ascent) {
        return new IconBox(null, width, height, ascent, height - ascent);
    }

    private static TitleText parsed(final String source) {
        return TitleTextParser.parse(source, false);
    }

    @Test
    public void degradedTextIsMeasuredFromItsOwnString() {
        final String text = "literal $a$";
        final Rectangle2D bounds = FONT.getStringBounds(text, TitleMetrics.SCREEN_FRC);
        final TitleMeasurer measurer = new TitleMeasurer(new StubIcons(), 10);
        measurer.beginPass(1L);

        final MeasuredTitle deferred = measurer.measureDegradedText(text, FONT, true);
        final MeasuredTitle immediate = measurer.measureDegradedText(text, FONT, false);

        assertLiteralMeasurement(deferred, text, bounds);
        assertLiteralMeasurement(immediate, text, bounds);
        assertThat(deferred.deferredForBudget()).isTrue();
        assertThat(immediate.deferredForBudget()).isFalse();
        assertThat(measurer.newMeasurementsInLastPass()).isZero();
        assertThat(measurer.deferredInLastPass()).isZero();
        assertThat(measurer.pendingCount()).isZero();
        assertThat(measurer.budgetExhaustedInLastPass()).isFalse();

        final MeasuredTitle plainTitle = measurer.measure(TitleText.plain(text), FONT);
        assertThat(plainTitle.availability()).isEqualTo(MeasuredTitle.Availability.RENDERED);
    }

    private static void assertLiteralMeasurement(final MeasuredTitle measured, final String text,
            final Rectangle2D bounds) {
        assertThat(measured.availability()).isEqualTo(MeasuredTitle.Availability.DEGRADED);
        assertThat(measured.runs()).hasSize(1);
        final MeasuredRun run = measured.runs().get(0);
        assertThat(run.kind()).isEqualTo(MeasuredRun.Kind.PLAIN);
        assertThat(run.source()).isEqualTo(text);
        assertThat(run.width()).isEqualTo(bounds.getWidth());
        assertThat(run.ascent()).isEqualTo(-bounds.getY());
        assertThat(run.descent()).isEqualTo(bounds.getHeight() + bounds.getY());
        assertThat(measured.width()).isEqualTo(bounds.getWidth());
        assertThat(measured.height()).isEqualTo(Math.max(measured.ascent() + measured.descent(), 1.0e-6));
    }

    @Test
    public void plainTitlesMeasureAsThePlacementBaseline() {
        final TitleMeasurer measurer = new TitleMeasurer(new StubIcons(), 10);
        measurer.beginPass(1L);
        final MeasuredTitle measured = measurer.measure(parsed("Theorem: initial segment"), FONT);
        final Rectangle2D bounds = FONT.getStringBounds("Theorem: initial segment", TitleMetrics.SCREEN_FRC);
        assertThat(measured.width()).isCloseTo(bounds.getWidth(), offset(1.0e-9));
        assertThat(measured.height()).isCloseTo(bounds.getHeight(), offset(1.0e-9));
        assertThat(measured.availability()).isEqualTo(MeasuredTitle.Availability.RENDERED);
    }

    @Test
    public void plainTitlesNeverConsumeTheBudget() {
        final StubIcons icons = new StubIcons();
        icons.boxes.put("\\alpha", box(9.0, 12.0, 9.0));
        final TitleMeasurer measurer = new TitleMeasurer(icons, 1);
        measurer.beginPass(1L);
        measurer.measure(parsed("plain"), FONT);
        final MeasuredTitle formula = measurer.measure(parsed("$\\alpha$"), FONT);
        assertThat(formula.availability()).isEqualTo(MeasuredTitle.Availability.RENDERED);
        assertThat(measurer.newMeasurementsInLastPass()).isEqualTo(1);
    }

    @Test
    public void aFormulaBeyondTheBudgetIsDeferred() {
        final StubIcons icons = new StubIcons();
        icons.boxes.put("\\alpha", box(9.0, 12.0, 9.0));
        icons.boxes.put("\\beta", box(9.0, 12.0, 9.0));
        final TitleMeasurer measurer = new TitleMeasurer(icons, 1);
        measurer.beginPass(1L);
        assertThat(measurer.measure(parsed("$\\alpha$"), FONT).availability())
            .isEqualTo(MeasuredTitle.Availability.RENDERED);
        final MeasuredTitle deferred = measurer.measure(parsed("$\\beta$"), FONT);
        assertThat(deferred.deferredForBudget()).isTrue();
        assertThat(measurer.pendingCount()).isEqualTo(1);
        measurer.endPass();
        assertThat(measurer.stalled()).isFalse();
    }

    @Test
    public void rejectsNonFiniteAndNonPositiveIconMetrics() {
        final StubIcons icons = new StubIcons();
        icons.boxes.put("\\alpha", box(0.0, 12.0, 9.0));
        final TitleMeasurer measurer = new TitleMeasurer(icons, 10);
        measurer.beginPass(1L);
        assertThat(measurer.measure(parsed("$\\alpha$"), FONT).availability())
            .isEqualTo(MeasuredTitle.Availability.DEGRADED);
    }

    @Test
    public void rejectsFormulaTallerThanTwiceTheLineHeight() {
        final StubIcons icons = new StubIcons();
        final double tooTall = 3.0 * TitleMetrics.lineHeight(FONT);
        icons.boxes.put("\\alpha", box(9.0, tooTall, tooTall - 2.0));
        final TitleMeasurer measurer = new TitleMeasurer(icons, 10);
        measurer.beginPass(1L);
        assertThat(measurer.measure(parsed("$\\alpha$"), FONT).availability())
            .isEqualTo(MeasuredTitle.Availability.DEGRADED);
    }

    @Test
    public void rejectsANonFiniteBaseline() {
        assertThatThrownBy(() -> new IconBox(null, 5.0, 10.0, Double.NaN, 0.0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void multipleFormulasDegradeWholesale() {
        final StubIcons icons = new StubIcons();
        icons.boxes.put("\\alpha", box(9.0, 12.0, 9.0));
        final TitleMeasurer measurer = new TitleMeasurer(icons, 10);
        measurer.beginPass(1L);
        final MeasuredTitle measured = measurer.measure(parsed("a $\\leslant$ b $\\alpha$"), FONT);
        assertThat(measured.availability()).isEqualTo(MeasuredTitle.Availability.DEGRADED);
        assertThat(measured.runs()).hasSize(1);
        assertThat(measured.runs().get(0).source()).isEqualTo("a $\\leslant$ b $\\alpha$");
    }

    @Test
    public void aFailedMeasurementIsReusedWithoutASecondLookup() {
        final StubIcons icons = new StubIcons();
        final TitleMeasurer measurer = new TitleMeasurer(icons, 10);
        measurer.beginPass(1L);
        measurer.measure(parsed("$\\leslant$"), FONT);
        measurer.measure(parsed("$\\leslant$"), FONT);
        assertThat(icons.calls).isEqualTo(1);
    }

    @Test
    public void mixedRunsComposeInOrder() {
        final StubIcons icons = new StubIcons();
        icons.boxes.put("\\alpha", box(10.0, 12.0, 9.0));
        final TitleMeasurer measurer = new TitleMeasurer(icons, 10);
        measurer.beginPass(1L);
        final MeasuredTitle measured = measurer.measure(parsed("ab $\\alpha$ cd"), FONT);
        assertThat(measured.availability()).isEqualTo(MeasuredTitle.Availability.RENDERED);
        assertThat(measured.runs()).hasSize(3);
        assertThat(measured.runs().get(1).isMath()).isTrue();
        assertThat(measured.runs().get(2).x())
            .isCloseTo(measured.runs().get(1).x() + 10.0, offset(1.0e-9));
    }

    @Test
    public void sameIconPixelsDifferentFontKeepsSeparateMeasurements() {
        final StubIcons icons = new StubIcons();
        icons.boxes.put("\\alpha", box(9.0, 12.0, 9.0));
        final TitleMeasurer measurer = new TitleMeasurer(icons, 10);
        measurer.beginPass(1L);
        measurer.measure(parsed("a $\\alpha$"), FONT);
        measurer.measure(parsed("a $\\alpha$"), BOLD);
        assertThat(measurer.cacheSize()).isEqualTo(2);
    }

    @Test
    public void emptyTitleIsMeasuredWithoutThrowing() {
        final TitleMeasurer measurer = new TitleMeasurer(new StubIcons(), 10);
        measurer.beginPass(1L);
        final MeasuredTitle measured = measurer.measure(TitleText.plain(""), FONT);
        assertThat(measured.width()).isZero();
        assertThat(measured.height()).isPositive();
    }

    @Test
    public void aGenerationChangeClearsTheCacheAndTheQueue() {
        final StubIcons icons = new StubIcons();
        icons.boxes.put("\\alpha", box(9.0, 12.0, 9.0));
        final TitleMeasurer measurer = new TitleMeasurer(icons, 1);
        measurer.beginPass(1L);
        measurer.measure(parsed("$\\alpha$"), FONT);
        measurer.measure(parsed("$\\beta$"), FONT);
        assertThat(measurer.cacheSize()).isPositive();
        measurer.beginPass(2L);
        assertThat(measurer.cacheSize()).isZero();
        assertThat(measurer.pendingCount()).isZero();
    }

    @Test
    public void rejectsANonPositivePublicBudget() {
        assertThatThrownBy(() -> new TitleMeasurer(new StubIcons(), 0))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
