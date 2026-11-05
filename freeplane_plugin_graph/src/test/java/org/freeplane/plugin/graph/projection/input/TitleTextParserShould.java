package org.freeplane.plugin.graph.projection.input;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.Test;

public class TitleTextParserShould {
    private static List<TitleText.Run> runs(final String source) {
        return TitleTextParser.parse(source, false).runs();
    }

    private static TitleText.Math math(final TitleText.Run run) {
        assertThat(run).isInstanceOf(TitleText.Math.class);
        return (TitleText.Math) run;
    }

    private static void assertPlain(final TitleText title, final String expected) {
        assertThat(title.runs()).hasSize(1);
        assertThat(title.runs().get(0)).isInstanceOf(TitleText.Plain.class);
        assertThat(((TitleText.Plain) title.runs().get(0)).text()).isEqualTo(expected);
        assertThat(title.hasMath()).isFalse();
    }

    @Test
    public void plainTextIsOneRun() {
        assertPlain(TitleTextParser.parse("Theorem: initial segment", false), "Theorem: initial segment");
    }

    @Test
    public void singleDelimitersSplitIntoThreeRuns() {
        final List<TitleText.Run> parsed = runs("text $a$ text");
        assertThat(parsed).hasSize(3);
        assertThat(((TitleText.Plain) parsed.get(0)).text()).isEqualTo("text ");
        assertThat(math(parsed.get(1)).source()).isEqualTo("a");
        assertThat(math(parsed.get(1)).rawSource()).isEqualTo("$a$");
        assertThat(((TitleText.Plain) parsed.get(2)).text()).isEqualTo(" text");
    }

    @Test
    public void doubleDollarIsOneMathRun() {
        final List<TitleText.Run> parsed = runs("$$a$$");
        assertThat(parsed).hasSize(1);
        assertThat(math(parsed.get(0)).source()).isEqualTo("a");
        assertThat(math(parsed.get(0)).rawSource()).isEqualTo("$$a$$");
    }

    @Test
    public void escapedDollarIsLiteralAndUnescaped() {
        final List<TitleText.Run> parsed = runs("costs \\$5 and $\\alpha$");
        assertThat(parsed).hasSize(2);
        assertThat(((TitleText.Plain) parsed.get(0)).text()).isEqualTo("costs $5 and ");
        assertThat(math(parsed.get(1)).source()).isEqualTo("\\alpha");
        assertThat(math(parsed.get(1)).rawSource()).isEqualTo("$\\alpha$");
    }

    @Test
    public void unmatchedDollarRollsBackTheWholeTitle() {
        assertPlain(TitleTextParser.parse("unmatched $a", false), "unmatched $a");
    }

    @Test
    public void laterValidFormulaDoesNotSurviveAnUnmatchedDelimiter() {
        assertPlain(TitleTextParser.parse("$a$ and $b", false), "$a$ and $b");
    }

    @Test
    public void twoCompleteFormulasBothSurvive() {
        final List<TitleText.Run> parsed = runs("$a$ and $b$");
        assertThat(parsed).hasSize(3);
        assertThat(math(parsed.get(0)).source()).isEqualTo("a");
        assertThat(((TitleText.Plain) parsed.get(1)).text()).isEqualTo(" and ");
        assertThat(math(parsed.get(2)).source()).isEqualTo("b");
    }

    @Test
    public void unbalancedDoubleDollarRollsBack() {
        assertPlain(TitleTextParser.parse("$$a$", false), "$$a$");
    }

    @Test
    public void wholeTextIsMathProducesOneRun() {
        final TitleText title = TitleTextParser.parse("\\bigcup\\alpha\\leqslant\\alpha", true);
        assertThat(title.runs()).hasSize(1);
        assertThat(math(title.runs().get(0)).source()).isEqualTo("\\bigcup\\alpha\\leqslant\\alpha");
        assertThat(math(title.runs().get(0)).rawSource()).isEqualTo("\\bigcup\\alpha\\leqslant\\alpha");
    }

    @Test
    public void wholeTextWithDelimitersKeepsThemInTheRawSource() {
        final TitleText title = TitleTextParser.parse("$a$", true);
        assertThat(title.runs()).hasSize(1);
        assertThat(math(title.runs().get(0)).source()).isEqualTo("a");
        assertThat(math(title.runs().get(0)).rawSource()).isEqualTo("$a$");
    }

    @Test
    public void textCommandDollarDoesNotCloseEarly() {
        final List<TitleText.Run> parsed = runs("$\\text{$x$}$");
        assertThat(parsed).hasSize(1);
        assertThat(math(parsed.get(0)).source()).isEqualTo("\\text{$x$}");
    }

    @Test
    public void ordinaryBackslashesSurvive() {
        final List<TitleText.Run> parsed = runs("C:\\tmp and $\\alpha$");
        assertThat(parsed).hasSize(2);
        assertThat(((TitleText.Plain) parsed.get(0)).text()).isEqualTo("C:\\tmp and ");
        assertThat(math(parsed.get(1)).source()).isEqualTo("\\alpha");
    }

    @Test
    public void doubleBackslashCollapsesToALiteralOne() {
        assertPlain(TitleTextParser.parse("a \\\\ b", false), "a \\ b");
    }

    @Test
    public void escapedBracesDoNotAffectDepth() {
        final List<TitleText.Run> parsed = runs("$\\{x$");
        assertThat(parsed).hasSize(1);
        assertThat(math(parsed.get(0)).source()).isEqualTo("\\{x");
    }

    @Test
    public void trailingBackslashIsLiteral() {
        assertPlain(TitleTextParser.parse("tail \\", false), "tail \\");
    }

    @Test
    public void emptyRunsAreDropped() {
        final List<TitleText.Run> parsed = runs("$a$$b$");
        assertThat(parsed).hasSize(2);
        assertThat(math(parsed.get(0)).source()).isEqualTo("a");
        assertThat(math(parsed.get(1)).source()).isEqualTo("b");
    }

    @Test
    public void emptySourceYieldsOneEmptyPlainRun() {
        assertPlain(TitleTextParser.parse("", false), "");
    }

    @Test
    public void emptySourceWinsOverWholeTextMath() {
        assertPlain(TitleTextParser.parse("", true), "");
    }

    @Test
    public void plainSourceReconstructsTheOriginalDelimiters() {
        final TitleText title = TitleTextParser.parse("x $a$ y", false);
        assertThat(title.plainSource()).isEqualTo("x $a$ y");
    }
}
