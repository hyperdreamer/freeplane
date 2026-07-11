package org.freeplane.plugin.ai.model;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RequestyModelMetadataInterpreterTest {
    @Test
    public void requiresChatApiAndToolCalling() {
        RequestyModelMetadataInterpreter interpreter = new RequestyModelMetadataInterpreter();

        AIModelCapabilities accepted = interpreter.interpret(
            OpenAIModelItem.create("accepted", null, null, "chat", Boolean.TRUE));
        AIModelCapabilities nonChat = interpreter.interpret(
            OpenAIModelItem.create("image", null, null, "image", Boolean.TRUE));
        AIModelCapabilities noTools = interpreter.interpret(
            OpenAIModelItem.create("no-tools", null, null, "chat", Boolean.FALSE));

        assertThat(accepted.isToolCapableTextModel()).isTrue();
        assertThat(nonChat.getTextOutput()).isEqualTo(CapabilitySupport.UNSUPPORTED);
        assertThat(noTools.getToolCalling()).isEqualTo(CapabilitySupport.UNSUPPORTED);
    }
}
