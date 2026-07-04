package org.freeplane.plugin.ai.tools.read;

import java.util.Arrays;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReadNodesWithDescendantsRequestTest {
    @Test
    public void defaultsAndPresenceUseNullableRequestFields() {
        ReadNodesWithDescendantsRequest request = new ReadNodesWithDescendantsRequest(
            "map-identifier",
            null,
            Arrays.asList((ContextSection) null),
            null,
            null,
            null);

        assertThat(request.getFullContentDepth()).isEqualTo(0);
        assertThat(request.getAdditionalSummaryDepth()).isEqualTo(1);
        assertThat(request.getMaxCharacters()).isEqualTo(65536);
        assertThat(request.hasFullContentDepth()).isFalse();
        assertThat(request.hasAdditionalSummaryDepth()).isFalse();
        assertThat(request.getContextSections()).isEmpty();
    }

    @Test
    public void suppliedOptionalValuesArePresent() {
        ReadNodesWithDescendantsRequest request = new ReadNodesWithDescendantsRequest(
            "map-identifier",
            null,
            Arrays.asList(ContextSection.QUALIFIERS, null),
            2,
            3,
            1000);

        assertThat(request.getFullContentDepth()).isEqualTo(2);
        assertThat(request.getAdditionalSummaryDepth()).isEqualTo(3);
        assertThat(request.getMaxCharacters()).isEqualTo(1000);
        assertThat(request.hasFullContentDepth()).isTrue();
        assertThat(request.hasAdditionalSummaryDepth()).isTrue();
        assertThat(request.getContextSections()).containsExactly(ContextSection.QUALIFIERS);
    }
}
