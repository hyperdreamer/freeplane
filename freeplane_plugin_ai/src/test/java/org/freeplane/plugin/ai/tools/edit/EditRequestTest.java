package org.freeplane.plugin.ai.tools.edit;

import java.util.Collections;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EditRequestTest {
    @Test
    public void getResolvedCompatibilityPolicy_defaultsToSkipIncompatibleFields() {
        EditRequest request = new EditRequest("map-id", "summary", null, Collections.<NodeContentEditItem>emptyList());

        assertThat(request.getResolvedCompatibilityPolicy()).isEqualTo(EditCompatibilityPolicy.SKIP_INCOMPATIBLE_FIELDS);
    }

    @Test
    public void getResolvedCompatibilityPolicy_returnsExplicitPolicy() {
        EditRequest request = new EditRequest("map-id", "summary", EditCompatibilityPolicy.REJECT_ON_ANY_INCOMPATIBLE,
            Collections.<NodeContentEditItem>emptyList());

        assertThat(request.getResolvedCompatibilityPolicy()).isEqualTo(EditCompatibilityPolicy.REJECT_ON_ANY_INCOMPATIBLE);
    }
}
