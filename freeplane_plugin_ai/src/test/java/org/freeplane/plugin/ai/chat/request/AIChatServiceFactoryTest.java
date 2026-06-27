package org.freeplane.plugin.ai.chat.request;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import java.util.concurrent.atomic.AtomicReference;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.plugin.ai.chat.memory.ChatTokenUsageTracker;
import org.freeplane.plugin.ai.model.AIModelConfiguration;
import org.freeplane.plugin.ai.model.AIModelSelection;
import org.freeplane.plugin.ai.tools.AIToolSet;
import org.freeplane.plugin.ai.model.AIChatModelFactory;
import org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevel;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;

public class AIChatServiceFactoryTest {

    @Test
    public void createService_passesRequestModelConfigurationWithoutMutatingGlobalSelection() {
        AtomicReference<AIModelConfiguration> seenRequestConfiguration = new AtomicReference<AIModelConfiguration>();

        try (MockedStatic<ResourceController> resourceController = mockStatic(ResourceController.class);
             MockedStatic<AIChatModelFactory> modelFactory = mockStatic(AIChatModelFactory.class, invocation -> {
                 seenRequestConfiguration.set(invocation.getArgument(1));
                 return mock(ChatModel.class);
             });
             MockedConstruction<AIChatService> chatServices = mockConstruction(AIChatService.class)) {
            resourceController.when(ResourceController::getResourceController).thenReturn(mock(ResourceController.class));
            AIModelConfiguration modelConfiguration = AIModelConfiguration.fromSelectionValue(
                "openrouter|openai/gpt-4.1-mini");
            AIChatServiceFactory.createService(
                mock(AIToolSet.class),
                mock(ChatMemory.class),
                new ChatTokenUsageTracker(totals -> {
                }),
                null,
                null,
                null,
                () -> ToolAvailabilityLevel.EDITING,
                modelConfiguration
            );

            AIModelSelection selection = seenRequestConfiguration.get().getModelSelection();
            assertThat(selection).isEqualTo(AIModelSelection.fromSelectionValue("openrouter|openai/gpt-4.1-mini"));
            assertThat(chatServices.constructed()).hasSize(1);
        }
    }
}
