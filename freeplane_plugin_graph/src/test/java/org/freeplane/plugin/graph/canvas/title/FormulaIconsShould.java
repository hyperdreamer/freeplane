package org.freeplane.plugin.graph.canvas.title;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.Test;

public class FormulaIconsShould {
    @Test
    public void buildsEachKeyOnce() {
        final FormulaIcons icons = new FormulaIcons();
        final IconBox first = icons.icon("\\alpha", 12, TitleMetrics.STYLE);
        final IconBox second = icons.icon("\\alpha", 12, TitleMetrics.STYLE);
        assertThat(first).isNotNull();
        assertThat(second).isSameAs(first);
        assertThat(icons.buildCount()).isEqualTo(1);
        assertThat(icons.hitCount()).isEqualTo(1);
    }

    @Test
    public void cachesFailuresWithoutRebuilding() {
        final FormulaIcons icons = new FormulaIcons();
        assertThat(icons.icon("\\leslant", 12, TitleMetrics.STYLE)).isNull();
        assertThat(icons.icon("\\leslant", 12, TitleMetrics.STYLE)).isNull();
        assertThat(icons.failureCount()).isEqualTo(1);
        assertThat(icons.buildCount()).isZero();
    }

    @Test
    public void evictsLeastRecentlyUsedBeyondCapacity() {
        final FormulaIcons icons = new FormulaIcons(2);
        icons.icon("\\alpha", 12, TitleMetrics.STYLE);
        icons.icon("\\beta", 12, TitleMetrics.STYLE);
        icons.icon("\\gamma", 12, TitleMetrics.STYLE);
        assertThat(icons.size()).isEqualTo(2);
        final int buildsBefore = icons.buildCount();
        icons.icon("\\alpha", 12, TitleMetrics.STYLE);
        assertThat(icons.buildCount()).isEqualTo(buildsBefore + 1);
    }

    @Test
    public void capacityMustBePositive() {
        assertThatThrownBy(() -> new FormulaIcons(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void baselineRatioIsAppliedInPixels() {
        final FormulaIcons icons = new FormulaIcons();
        final IconBox box = icons.icon("\\mathrm{x}", 20, TitleMetrics.STYLE);
        assertThat(box).isNotNull();
        assertThat(box.height()).isGreaterThan(1.0);
        assertThat(box.ascent()).isGreaterThan(5.0);
        assertThat(box.ascent() + box.descent()).isCloseTo(box.height(), within(1.0e-9));
    }
}
