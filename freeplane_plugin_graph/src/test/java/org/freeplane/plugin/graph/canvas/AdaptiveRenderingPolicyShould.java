package org.freeplane.plugin.graph.canvas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.Test;

public class AdaptiveRenderingPolicyShould {
    @Test
    public void useFullRenderingBelowTheNodeTarget() {
        AdaptiveRenderingPolicy policy = new AdaptiveRenderingPolicy();

        assertThat(policy.forCounts(499, 0)).isEqualTo(RenderingLevel.FULL);
    }

    @Test
    public void useDenseRenderingAtTheNodeOrEdgeTarget() {
        AdaptiveRenderingPolicy policy = new AdaptiveRenderingPolicy();

        assertThat(policy.forCounts(500, 0)).isEqualTo(RenderingLevel.DENSE);
        assertThat(policy.forCounts(2000, 5000)).isEqualTo(RenderingLevel.DENSE);
    }

    @Test
    public void useOverTargetRenderingAfterEitherEngineeringTarget() {
        AdaptiveRenderingPolicy policy = new AdaptiveRenderingPolicy();

        assertThat(policy.forCounts(2001, 0)).isEqualTo(RenderingLevel.OVER_TARGET);
        assertThat(policy.forCounts(0, 5001)).isEqualTo(RenderingLevel.OVER_TARGET);
        assertThat(policy.forCounts(2001, 5001)).isEqualTo(RenderingLevel.OVER_TARGET);
    }

    @Test
    public void reportTheEngineeringTargetAtTheExactBoundary() {
        AdaptiveRenderingPolicy policy = new AdaptiveRenderingPolicy();

        assertThat(policy.exceedsEngineeringTarget(2000, 5000)).isFalse();
        assertThat(policy.exceedsEngineeringTarget(2001, 5000)).isTrue();
        assertThat(policy.exceedsEngineeringTarget(2000, 5001)).isTrue();
    }

    @Test
    public void rejectNegativeCounts() {
        AdaptiveRenderingPolicy policy = new AdaptiveRenderingPolicy();

        assertThatThrownBy(() -> policy.forCounts(-1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.forCounts(0, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.exceedsEngineeringTarget(-1, 0))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
