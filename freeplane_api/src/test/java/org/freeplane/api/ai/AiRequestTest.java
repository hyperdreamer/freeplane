package org.freeplane.api.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import org.freeplane.api.MindMap;
import org.junit.Test;

public class AiRequestTest {

    @Test
    public void rejectsNonPositiveTimeout() {
        assertThatThrownBy(() -> new AiRequest(
            "Prompt",
            AiModelSelection.current(),
            AiToolAvailability.CURRENT,
            AiRequestMode.HIDDEN,
            Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("timeout");
    }

    @Test
    public void acceptsSelectionOverrideWithOrderedNodeIds() {
        MindMap mindMap = mock(MindMap.class);

        AiSelectionOverride override = new AiSelectionOverride(mindMap, Arrays.asList("ID_1", "ID_2"));

        assertThat(override.getMindMap()).isSameAs(mindMap);
        assertThat(override.getSelectedNodeIds()).containsExactly("ID_1", "ID_2");
    }

    @Test
    public void rejectsDuplicateSelectionOverrideNodeIds() {
        MindMap mindMap = mock(MindMap.class);

        assertThatThrownBy(() -> new AiSelectionOverride(mindMap, Arrays.asList("ID_1", "ID_1")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("duplicates");
    }

    @Test
    public void explicitModelSelectionNormalizesProviderAndModel() {
        AiModelSelection selection = AiModelSelection.explicit(" openrouter ", " openai/gpt-4.1-mini ");

        assertThat(selection.isCurrent()).isFalse();
        assertThat(selection.getProviderName()).isEqualTo("openrouter");
        assertThat(selection.getModelName()).isEqualTo("openai/gpt-4.1-mini");
    }

    @Test
    public void requestExposesOptionalSelectionOverride() {
        MindMap mindMap = mock(MindMap.class);
        AiSelectionOverride override = new AiSelectionOverride(mindMap, Collections.singletonList("ID_1"));

        AiRequest request = new AiRequest(
            "Prompt",
            AiModelSelection.current(),
            AiToolAvailability.READING,
            AiRequestMode.HIDDEN,
            Duration.ofSeconds(10),
            override);

        assertThat(request.getSelectionOverride()).isSameAs(override);
    }

    @Test
    public void exposesStablePublicEnumValues() {
        assertThat(AiRequestMode.values()).extracting(Enum::name)
            .containsExactly("SHOW_IN_CHAT", "ADD_TO_CHAT", "HIDDEN_WITH_CANCEL_DIALOG", "HIDDEN");
        assertThat(AiToolAvailability.values()).extracting(Enum::name)
            .containsExactly("CURRENT", "DISABLED", "READING", "EDITING");
        assertThat(AiRequestStatus.values()).extracting(Enum::name)
            .containsExactly(
                "SUCCEEDED",
                "REJECTED_BUSY",
                "PERMISSION_DENIED",
                "AI_UNAVAILABLE",
                "CONFIGURATION_ERROR",
                "AUTHENTICATION_ERROR",
                "MODEL_UNAVAILABLE",
                "PROVIDER_ERROR",
                "FAILED",
                "CANCELLED",
                "TIMED_OUT");
    }

    @Test
    public void keepsPublicAiTypesInAiPackage() {
        assertThat(AiRequest.class.getPackage().getName()).isEqualTo("org.freeplane.api.ai");
        assertThat(AiRequestResult.class.getPackage().getName()).isEqualTo("org.freeplane.api.ai");
        assertThat(AiModelSelection.class.getPackage().getName()).isEqualTo("org.freeplane.api.ai");
        assertThat(AiSelectionOverride.class.getPackage().getName()).isEqualTo("org.freeplane.api.ai");
        assertThat(AiRequestRejectedException.class.getPackage().getName()).isEqualTo("org.freeplane.api.ai");
        assertThat(AiRequestCallback.class.getPackage().getName()).isEqualTo("org.freeplane.api.ai");
        assertThat(AiRequestHandle.class.getPackage().getName()).isEqualTo("org.freeplane.api.ai");
        assertThat(AiRequestMode.class.getPackage().getName()).isEqualTo("org.freeplane.api.ai");
        assertThat(AiToolAvailability.class.getPackage().getName()).isEqualTo("org.freeplane.api.ai");
        assertThat(AiRequestStatus.class.getPackage().getName()).isEqualTo("org.freeplane.api.ai");
        assertThat(AiRequestService.class.getPackage().getName()).isEqualTo("org.freeplane.api.ai");
    }

    @Test
    public void rejectedExceptionExposesStatus() {
        AiRequestRejectedException rejected =
            new AiRequestRejectedException(AiRequestStatus.PERMISSION_DENIED, "denied");

        assertThat(rejected.getStatus()).isEqualTo(AiRequestStatus.PERMISSION_DENIED);
        assertThat(rejected).hasMessage("denied");
    }
}
