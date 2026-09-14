package org.freeplane.plugin.graph.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.Test;

public class BoundarySeparationExceptionShould {
    @Test
    public void carryMessageAndCause() {
        final IllegalArgumentException cause = new IllegalArgumentException("non-finite product");
        final BoundarySeparationException exception = new BoundarySeparationException("mst failed", cause);

        assertThat(exception).hasMessage("mst failed");
        assertThat(exception).hasCause(cause);
        assertThat(new BoundarySeparationException(cause)).hasCause(cause);
        assertThat(new BoundarySeparationException("plain")).hasMessage("plain");
        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> new BoundarySeparationException((String) null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BoundarySeparationException((String) null, cause))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BoundarySeparationException("mst failed", null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BoundarySeparationException((Throwable) null))
            .isInstanceOf(NullPointerException.class);
    }
}
