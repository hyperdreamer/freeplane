package org.freeplane.plugin.ai.mcpserver;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;
import org.freeplane.features.ai.code.AiChatCodeOperationResult;
import org.freeplane.features.ai.code.AiCodeHostService;
import org.freeplane.features.ai.code.AiCodeRunListener;
import org.freeplane.features.ai.code.CodeState;
import org.freeplane.features.ai.code.CodeStateToken;
import org.freeplane.features.ai.code.CompileCodeRequest;
import org.freeplane.features.ai.code.CompileCodeResponse;
import org.freeplane.features.ai.code.EvaluateFormulaRequest;
import org.freeplane.features.ai.code.ReadCodeRequest;
import org.freeplane.features.ai.code.ReadCodeResponse;
import org.freeplane.features.ai.code.RunCodeRequest;
import org.freeplane.features.ai.code.RunCodeResponse;
import org.freeplane.features.ai.code.ScriptHost;
import org.freeplane.features.ai.code.ScriptRunInitiator;
import org.freeplane.features.ai.code.WriteCodeRequest;
import org.freeplane.features.ai.code.WriteCodeResponse;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ModelContextProtocolAiCodeHostServiceTest {

    @Test
    public void runCodeWaitsForFinalUserRunResponseWithinTimeout() {
        WaitingCodeHostService delegate = new WaitingCodeHostService();
        AtomicBoolean cleared = new AtomicBoolean(false);
        ModelContextProtocolAiCodeHostService uut = new ModelContextProtocolAiCodeHostService(
            delegate,
            () -> cleared.set(true),
            () -> Long.valueOf(1000L));
        delegate.setFinalResponse(new RunCodeResponse(
            ScriptHost.AI,
            "text/x-freeplane-script-groovy",
            CodeState.RUN_SUCCEEDED,
            ScriptRunInitiator.USER,
            new CodeStateToken("code", "args"),
            null,
            null,
            "done",
            null),
            50L);

        RunCodeResponse response = uut.runCode(new RunCodeRequest(ScriptHost.AI, new CodeStateToken("code", "args")));

        assertThat(cleared.get()).isTrue();
        assertThat(response.getCodeState()).isEqualTo(CodeState.RUN_SUCCEEDED);
        assertThat(response.getStdout()).isEqualTo("done");
    }

    @Test
    public void runCodeReturnsWaitingWhenTimeoutExpiresFirst() {
        WaitingCodeHostService delegate = new WaitingCodeHostService();
        AtomicBoolean cleared = new AtomicBoolean(false);
        ModelContextProtocolAiCodeHostService uut = new ModelContextProtocolAiCodeHostService(
            delegate,
            () -> cleared.set(true),
            () -> Long.valueOf(25L));
        delegate.setFinalResponse(new RunCodeResponse(
            ScriptHost.AI,
            "text/x-freeplane-script-groovy",
            CodeState.USER_RUN_CANCELLED,
            ScriptRunInitiator.USER,
            new CodeStateToken("code", "args"),
            null,
            null,
            null,
            null),
            200L);

        RunCodeResponse response = uut.runCode(new RunCodeRequest(ScriptHost.AI, new CodeStateToken("code", "args")));

        assertThat(cleared.get()).isTrue();
        assertThat(response.getCodeState()).isEqualTo(CodeState.WAITING_FOR_USER_RUN);
    }

    @Test
    public void writeCodeClearsPendingAiOwnedUserRunFollowupForAiHost() {
        WaitingCodeHostService delegate = new WaitingCodeHostService();
        AtomicBoolean cleared = new AtomicBoolean(false);
        ModelContextProtocolAiCodeHostService uut = new ModelContextProtocolAiCodeHostService(
            delegate,
            () -> cleared.set(true),
            () -> Long.valueOf(1000L));

        uut.writeCode(new WriteCodeRequest(ScriptHost.AI, null, null));

        assertThat(cleared.get()).isTrue();
    }

    private static class WaitingCodeHostService implements AiCodeHostService {
        private final Set<AiCodeRunListener> listeners = new CopyOnWriteArraySet<AiCodeRunListener>();
        private volatile RunCodeResponse finalResponse;
        private volatile long finalResponseDelayMillis;

        private void setFinalResponse(RunCodeResponse finalResponse, long finalResponseDelayMillis) {
            this.finalResponse = finalResponse;
            this.finalResponseDelayMillis = finalResponseDelayMillis;
        }

        @Override
        public ReadCodeResponse readCode(ReadCodeRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public WriteCodeResponse writeCode(WriteCodeRequest request) {
            return new WriteCodeResponse(
                request.getHost(),
                "text/x-freeplane-script-groovy",
                CodeState.EDITED,
                null);
        }

        @Override
        public CompileCodeResponse compileCode(CompileCodeRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RunCodeResponse runCode(RunCodeRequest request) {
            RunCodeResponse response = new RunCodeResponse(
                ScriptHost.AI,
                "text/x-freeplane-script-groovy",
                CodeState.WAITING_FOR_USER_RUN,
                ScriptRunInitiator.AI,
                new CodeStateToken("code", "args"),
                null,
                null,
                null,
                null);
            if (finalResponse != null) {
                new Thread(() -> {
                    try {
                        Thread.sleep(finalResponseDelayMillis);
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    for (AiCodeRunListener listener : new LinkedHashSet<AiCodeRunListener>(listeners)) {
                        listener.runFinished(finalResponse);
                    }
                }).start();
            }
            return response;
        }

        @Override
        public AiChatCodeOperationResult evaluateFormula(EvaluateFormulaRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addRunListener(AiCodeRunListener listener) {
            if (listener != null) {
                listeners.add(listener);
            }
        }

        @Override
        public void removeRunListener(AiCodeRunListener listener) {
            if (listener != null) {
                listeners.remove(listener);
            }
        }
    }
}
