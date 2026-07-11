package org.freeplane.plugin.ai.chat.request;

import org.freeplane.api.ai.AiRequestStatus;
import org.freeplane.core.util.TextUtils;
import org.freeplane.plugin.ai.model.AIChatModelFactory;
import org.freeplane.plugin.ai.model.AIModelSelection;
import org.freeplane.plugin.ai.model.AIProviderConfiguration;
import org.junit.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class AiRequestConfigurationResolverTest {

    @Test
    public void reportsMissingSelectedModelAsConfigurationError() {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);

        try (MockedStatic<TextUtils> textUtils = mockStatic(TextUtils.class)) {
            textUtils.when(() -> TextUtils.getText("ai_model_selection_missing"))
                .thenReturn("Select an AI model before running a request.");

            AiRequestConfigurationResolver.Issue issue =
                new AiRequestConfigurationResolver(configuration).resolve(null);

            assertThat(issue).isNotNull();
            assertThat(issue.getStatus()).isEqualTo(AiRequestStatus.CONFIGURATION_ERROR);
            assertThat(issue.getDetail())
                .isEqualTo("Select an AI model before running a request.");
        }
    }

    @Test
    public void reportsMissingProviderCredentialAsConfigurationError() {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        String modelSelection = AIModelSelection.createSelectionValue(AIChatModelFactory.PROVIDER_NAME_OPENROUTER,
            "openai/gpt-4.1-mini");

        AiRequestConfigurationResolver.Issue issue = new AiRequestConfigurationResolver(configuration).resolve(modelSelection);

        assertThat(issue).isNotNull();
        assertThat(issue.getStatus()).isEqualTo(AiRequestStatus.CONFIGURATION_ERROR);
        assertThat(issue.getDetail()).contains("OpenRouter");
    }

    @Test
    public void acceptsConfiguredExplicitModelSelection() {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        when(configuration.getOpenRouterKey()).thenReturn("key");
        String modelSelection = AIModelSelection.createSelectionValue(AIChatModelFactory.PROVIDER_NAME_OPENROUTER,
            "openai/gpt-4.1-mini");

        AiRequestConfigurationResolver.Issue issue = new AiRequestConfigurationResolver(configuration).resolve(modelSelection);

        assertThat(issue).isNull();
    }
}
