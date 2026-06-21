package org.freeplane.api.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import org.freeplane.api.Controller;
import org.freeplane.api.MindMap;
import org.junit.Test;

public class AiRequestOptionsTest {

    @Test
    public void rejectsMissingTimeout() {
        assertThatThrownBy(() -> AiRequestOptions.builder().build())
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("timeout");
    }

    @Test
    public void rejectsNonPositiveTimeout() {
        assertThatThrownBy(() -> AiRequestOptions.builder().timeout(Duration.ZERO).build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("timeout");
    }

    @Test
    public void builderAllowsOptionalFieldsToRemainNull() {
        AiRequestOptions options = AiRequestOptions.builder()
            .timeout(Duration.ofSeconds(10))
            .build();

        assertThat(options.getTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(options.getMode()).isNull();
        assertThat(options.getModelSelection()).isNull();
        assertThat(options.getToolAvailability()).isNull();
        assertThat(options.getSelectionOverride()).isNull();
        assertThat(options.getSystemMessage()).isNull();
        assertThat(options.isSystemMessageExact()).isFalse();
        assertThat(options.getProfileName()).isNull();
        assertThat(options.getProfileMessage()).isNull();
    }

    @Test
    public void builderExposesExplicitFieldValues() {
        MindMap mindMap = mock(MindMap.class);
        AiSelectionOverride override = new AiSelectionOverride(mindMap, Collections.singletonList("ID_1"));

        AiRequestOptions options = AiRequestOptions.builder()
            .timeout(Duration.ofSeconds(10))
            .mode(AiRequestMode.ADD_TO_CHAT)
            .modelSelection(AiModelSelection.explicit("openrouter", "openai/gpt-4.1-mini"))
            .toolAvailability(AiToolAvailability.READING)
            .selectionOverride(override)
            .systemMessage(" system ")
            .profile(" reviewer ", " check strictly ")
            .build();

        assertThat(options.getTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(options.getMode()).isEqualTo(AiRequestMode.ADD_TO_CHAT);
        assertThat(options.getModelSelection())
            .isEqualTo(AiModelSelection.explicit("openrouter", "openai/gpt-4.1-mini"));
        assertThat(options.getToolAvailability()).isEqualTo(AiToolAvailability.READING);
        assertThat(options.getSelectionOverride()).isSameAs(override);
        assertThat(options.getSystemMessage()).isEqualTo("system");
        assertThat(options.isSystemMessageExact()).isFalse();
        assertThat(options.getProfileName()).isEqualTo("reviewer");
        assertThat(options.getProfileMessage()).isEqualTo("check strictly");
    }

    @Test
    public void preservesExplicitEmptySystemMessage() {
        AiRequestOptions options = AiRequestOptions.builder()
            .timeout(Duration.ofSeconds(10))
            .systemMessage("   ")
            .build();

        assertThat(options.getSystemMessage()).isEqualTo("");
        assertThat(options.isSystemMessageExact()).isFalse();
    }

    @Test
    public void exactSystemMessageStoresTrimmedExactText() {
        AiRequestOptions options = AiRequestOptions.builder()
            .timeout(Duration.ofSeconds(10))
            .exactSystemMessage(" exact ")
            .build();

        assertThat(options.getSystemMessage()).isEqualTo("exact");
        assertThat(options.isSystemMessageExact()).isTrue();
    }

    @Test
    public void exactSystemMessagePreservesExplicitEmptyText() {
        AiRequestOptions options = AiRequestOptions.builder()
            .timeout(Duration.ofSeconds(10))
            .exactSystemMessage("   ")
            .build();

        assertThat(options.getSystemMessage()).isEqualTo("");
        assertThat(options.isSystemMessageExact()).isTrue();
    }

    @Test
    public void exactSystemMessageNullClearsExactness() {
        AiRequestOptions options = AiRequestOptions.builder()
            .timeout(Duration.ofSeconds(10))
            .exactSystemMessage("exact")
            .exactSystemMessage(null)
            .build();

        assertThat(options.getSystemMessage()).isNull();
        assertThat(options.isSystemMessageExact()).isFalse();
    }

    @Test
    public void lastSystemMessageBuilderCallWinsExactness() {
        AiRequestOptions normal = AiRequestOptions.builder()
            .timeout(Duration.ofSeconds(10))
            .exactSystemMessage("exact")
            .systemMessage("normal")
            .build();
        AiRequestOptions exact = AiRequestOptions.builder()
            .timeout(Duration.ofSeconds(10))
            .systemMessage("normal")
            .exactSystemMessage("exact")
            .build();

        assertThat(normal.getSystemMessage()).isEqualTo("normal");
        assertThat(normal.isSystemMessageExact()).isFalse();
        assertThat(exact.getSystemMessage()).isEqualTo("exact");
        assertThat(exact.isSystemMessageExact()).isTrue();
    }

    @Test
    public void configuredProfileLookupStoresTrimmedNameOnly() {
        AiRequestOptions options = AiRequestOptions.builder()
            .timeout(Duration.ofSeconds(10))
            .profile(" reviewer ")
            .build();

        assertThat(options.getProfileName()).isEqualTo("reviewer");
        assertThat(options.getProfileMessage()).isNull();
    }

    @Test
    public void explicitProfileRejectsNullMessage() {
        assertThatThrownBy(() -> AiRequestOptions.builder()
            .timeout(Duration.ofSeconds(10))
            .profile("reviewer", null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("message");
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
    public void exposesStablePublicEnumValues() {
        assertThat(AiRequestMode.values()).extracting(Enum::name)
            .containsExactly("SHOW_IN_NEW_CHAT", "ADD_TO_CHAT", "HIDDEN_WITH_CANCEL_DIALOG", "HIDDEN");
        assertThat(AiToolAvailability.values()).extracting(Enum::name)
            .containsExactly("CURRENT", "DISABLED", "READING", "EDITING", "SCRIPT_EXECUTION");
        assertThat(AiRequestStatus.values()).extracting(Enum::name)
            .containsExactly(
                "SUCCEEDED",
                "REJECTED_BUSY",
                "PERMISSION_DENIED",
                "AI_UNAVAILABLE",
                "PROMPT_NOT_FOUND",
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
        assertThat(AiRequestOptions.class.getPackage().getName()).isEqualTo("org.freeplane.api.ai");
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
    public void controllerExposesOnlyCurrentAiMethodSignatures() {
        assertThat(Controller.class.getMethods())
            .extracting(method -> method.getName() + "(" + Arrays.toString(method.getParameterTypes()) + ")")
            .anyMatch(signature -> signature.contains("askAi") && signature.contains("AiRequestOptions"))
            .anyMatch(signature -> signature.contains("runAiPrompt") && signature.contains("Duration"))
            .anyMatch(signature -> signature.contains("runAiPrompt") && signature.contains("AiRequestOptions"));
    }

    @Test
    public void rejectedExceptionExposesStatus() {
        AiRequestRejectedException rejected =
            new AiRequestRejectedException(AiRequestStatus.PERMISSION_DENIED, "denied");

        assertThat(rejected.getStatus()).isEqualTo(AiRequestStatus.PERMISSION_DENIED);
        assertThat(rejected).hasMessage("denied");
    }
}
