package org.freeplane.plugin.ai.model;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OpenRouterModelMetadataInterpreterTest {
    @Test
    public void requiresToolsAndTextOutput() {
        OpenRouterModelMetadataInterpreter interpreter = new OpenRouterModelMetadataInterpreter();

        AIModelCapabilities accepted = interpreter.interpret(OpenAIModelItem.create(
            "accepted", Collections.singletonList("tools"), Arrays.asList("text", "image"), null, null));
        AIModelCapabilities noTools = interpreter.interpret(OpenAIModelItem.create(
            "no-tools", Collections.singletonList("temperature"), Collections.singletonList("text"), null, null));
        AIModelCapabilities noText = interpreter.interpret(OpenAIModelItem.create(
            "no-text", Collections.singletonList("tools"), Collections.singletonList("image"), null, null));

        assertThat(accepted.isToolCapableTextModel()).isTrue();
        assertThat(noTools.getToolCalling()).isEqualTo(CapabilitySupport.UNSUPPORTED);
        assertThat(noText.getTextOutput()).isEqualTo(CapabilitySupport.UNSUPPORTED);
    }
}
