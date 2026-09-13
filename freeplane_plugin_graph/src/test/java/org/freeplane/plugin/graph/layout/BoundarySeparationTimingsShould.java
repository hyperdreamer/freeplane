package org.freeplane.plugin.graph.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.Test;

public class BoundarySeparationTimingsShould {
    @Test
    public void exposeNonnegativeStageSums() {
        final BoundarySeparationTimings timings = new BoundarySeparationTimings(11L, 22L, 33L, 44L);

        assertThat(timings.separationNanos()).isEqualTo(11L);
        assertThat(timings.hullNanos()).isEqualTo(22L);
        assertThat(timings.planNanos()).isEqualTo(33L);
        assertThat(timings.applyNanos()).isEqualTo(44L);
        assertThatThrownBy(() -> new BoundarySeparationTimings(-1L, 0L, 0L, 0L))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
