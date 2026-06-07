package org.freeplane.plugin.ai.code;

import java.util.concurrent.atomic.AtomicReference;
import org.freeplane.features.ai.code.AiCodeHostService;
import org.freeplane.features.ai.code.AiCodeRunListener;
import org.freeplane.features.ai.code.CodeLifecycleStatus;
import org.freeplane.features.ai.code.CompileCodeRequest;
import org.freeplane.features.ai.code.ReadCodeRequest;
import org.freeplane.features.ai.code.ReadCodeResponse;
import org.freeplane.features.ai.code.RunCodeResponse;
import org.freeplane.features.ai.code.ScriptHost;
import org.freeplane.features.ai.code.WriteCodeRequest;
import org.freeplane.features.ai.code.WriteCodeResponse;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RoutingAiCodeHostServiceTest {
    @Test
    public void readCodeReturnsNoCodeWhenAiHostIsUnavailable() {
        AiCodeHostService attachedEditorHost = mock(AiCodeHostService.class);
        RoutingAiCodeHostService uut = new RoutingAiCodeHostService(attachedEditorHost, () -> null);

        ReadCodeResponse response = uut.readCode(new ReadCodeRequest(ScriptHost.AI, null));

        assertThat(response.getHost()).isEqualTo(ScriptHost.AI);
        assertThat(response.getContentType()).isEqualTo("text/x-freeplane-script-groovy");
        assertThat(response.getStatus()).isEqualTo(CodeLifecycleStatus.NO_CODE);
    }

    @Test
    public void writeCodeRoutesAiRequestsToAiHost() {
        AiCodeHostService attachedEditorHost = mock(AiCodeHostService.class);
        AiCodeHostService aiHost = mock(AiCodeHostService.class);
        WriteCodeResponse expectedResponse = new WriteCodeResponse(
            ScriptHost.AI,
            "text/x-freeplane-script-groovy",
            CodeLifecycleStatus.READY,
            "fingerprint");
        when(aiHost.writeCode(org.mockito.ArgumentMatchers.any(WriteCodeRequest.class))).thenReturn(expectedResponse);
        RoutingAiCodeHostService uut = new RoutingAiCodeHostService(attachedEditorHost, () -> aiHost);

        WriteCodeResponse response = uut.writeCode(new WriteCodeRequest(ScriptHost.AI, "println 1", null));

        assertThat(response).isSameAs(expectedResponse);
        verify(aiHost).writeCode(org.mockito.ArgumentMatchers.any(WriteCodeRequest.class));
    }

    @Test
    public void writeCodeFailsWhenAiHostIsUnavailable() {
        AiCodeHostService attachedEditorHost = mock(AiCodeHostService.class);
        RoutingAiCodeHostService uut = new RoutingAiCodeHostService(attachedEditorHost, () -> null);

        assertThatThrownBy(() -> uut.writeCode(new WriteCodeRequest(ScriptHost.AI, "println 1", null)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("AI code host is not available.");
    }

    @Test
    public void addRunListenerRegistersWithBothHosts() {
        AiCodeHostService attachedEditorHost = mock(AiCodeHostService.class);
        AiCodeHostService aiHost = mock(AiCodeHostService.class);
        RoutingAiCodeHostService uut = new RoutingAiCodeHostService(attachedEditorHost, () -> aiHost);
        AiCodeRunListener listener = response -> {
        };

        uut.addRunListener(listener);

        verify(attachedEditorHost).addRunListener(listener);
        verify(aiHost).addRunListener(listener);
    }
}
