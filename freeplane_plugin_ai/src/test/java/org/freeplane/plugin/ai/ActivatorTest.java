package org.freeplane.plugin.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.util.Dictionary;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.main.osgi.IModeControllerExtensionProvider;
import org.freeplane.plugin.ai.chat.AIChatPanel;
import org.freeplane.plugin.ai.mcpserver.ModelContextProtocolServer;
import org.freeplane.plugin.ai.prompt.AiPromptActionRegistry;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.osgi.framework.BundleContext;

public class ActivatorTest {

    @Test
    public void stop_persistsChatPromptStateAndStopsMcpServer() throws Exception {
        Activator activator = new Activator();
        AIChatPanel aiChatPanel = mock(AIChatPanel.class);
        AiPromptActionRegistry promptActionRegistry = mock(AiPromptActionRegistry.class);
        ModelContextProtocolServer modelContextProtocolServer = mock(ModelContextProtocolServer.class);
        setField(activator, "aiChatPanel", aiChatPanel);
        setField(activator, "promptActionRegistry", promptActionRegistry);
        setField(activator, "modelContextProtocolServer", modelContextProtocolServer);

        activator.stop(null);

        verify(aiChatPanel).persistCurrentChatIfNeeded();
        verify(promptActionRegistry).persistStateIfChanged();
        verify(modelContextProtocolServer).stop();
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = Activator.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    public void start_registersMindMapModeExtensionProvider() throws Exception {
        BundleContext context = mock(BundleContext.class);
        Activator activator = new Activator();

        activator.start(context);

        ArgumentCaptor<IModeControllerExtensionProvider> providerCaptor =
            ArgumentCaptor.forClass(IModeControllerExtensionProvider.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Dictionary<String, String[]>> propertiesCaptor =
            (ArgumentCaptor<Dictionary<String, String[]>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Dictionary.class);
        verify(context).registerService(
            eq(IModeControllerExtensionProvider.class.getName()),
            providerCaptor.capture(),
            propertiesCaptor.capture());
        assertThat(providerCaptor.getValue()).isNotNull();
        assertThat(propertiesCaptor.getValue().get("mode")).containsExactly(MModeController.MODENAME);
    }
}
