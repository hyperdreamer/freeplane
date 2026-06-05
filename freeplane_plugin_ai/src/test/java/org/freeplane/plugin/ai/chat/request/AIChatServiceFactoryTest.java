package org.freeplane.plugin.ai.chat.request;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import java.util.concurrent.atomic.AtomicReference;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.plugin.ai.chat.memory.ChatTokenUsageTracker;
import org.freeplane.plugin.ai.chat.settings.ToolAvailabilityLevel;
import org.freeplane.plugin.ai.model.AIChatModelFactory;
import org.freeplane.plugin.ai.model.AIProviderConfiguration;
import org.freeplane.plugin.ai.tools.AIToolSet;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;

public class AIChatServiceFactoryTest {

    @Test
    public void createService_usesSelectedModelOverrideWithoutMutatingGlobalSelection() {
        AtomicReference<String> seenSelectionValue = new AtomicReference<String>();

        try (MockedStatic<ResourceController> resourceController = mockStatic(ResourceController.class);
             MockedStatic<AIChatModelFactory> modelFactory = mockStatic(AIChatModelFactory.class, invocation -> {
                 AIProviderConfiguration configuration = invocation.getArgument(0);
                 seenSelectionValue.set(configuration.getSelectedModelValue());
                 return mock(ChatModel.class);
             });
             MockedConstruction<AIChatService> chatServices = mockConstruction(AIChatService.class)) {
            resourceController.when(ResourceController::getResourceController).thenReturn(mock(ResourceController.class));
            AIChatServiceFactory.createService(
                mock(AIToolSet.class),
                mock(ChatMemory.class),
                new ChatTokenUsageTracker(totals -> {
                }),
                null,
                null,
                null,
                () -> ToolAvailabilityLevel.EDITING,
                "openrouter|openai/gpt-4.1-mini"
            );

            assertThat(seenSelectionValue.get()).isEqualTo("openrouter|openai/gpt-4.1-mini");
            assertThat(chatServices.constructed()).hasSize(1);
        }
    }
}
