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
import org.freeplane.features.ai.code.WriteAndRunCodeRequest;
import org.freeplane.features.ai.code.WriteCodeRequest;
import org.freeplane.features.ai.code.WriteCodeResponse;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ModelContextProtocolAiCodeHostServiceTest {

    @Test
    public void runCodeReturnsWaitingAndAwaitReturnsFinalUserRunResponseWithinTimeout() {
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
            token(),
            null,
            null,
            "done",
            null),
            50L);

        RunCodeResponse waitingResponse = uut.runCode(new RunCodeRequest(ScriptHost.AI, token()));
        RunCodeResponse finalResponse = uut.awaitFinalRunResponse(waitingResponse);

        assertThat(cleared.get()).isTrue();
        assertThat(delegate.listenerCountWhenRunCodeStarted).isEqualTo(1);
        assertThat(waitingResponse.getCodeState()).isEqualTo(CodeState.WAITING_FOR_USER_RUN);
        assertThat(finalResponse.getCodeState()).isEqualTo(CodeState.RUN_SUCCEEDED);
        assertThat(finalResponse.getStdout()).isEqualTo("done");
        assertThat(delegate.listenerCount()).isEqualTo(0);
    }

    @Test
    public void writeAndRunCodeReturnsWaitingAndAwaitReturnsFinalUserRunResponseWithinTimeout() {
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
            token(),
            null,
            null,
            "done",
            null),
            50L);

        RunCodeResponse waitingResponse = uut.writeAndRunCode(new WriteAndRunCodeRequest(
            new org.freeplane.features.ai.code.CodeStateContent("println 1", null)));
        RunCodeResponse finalResponse = uut.awaitFinalRunResponse(waitingResponse);

        assertThat(cleared.get()).isTrue();
        assertThat(waitingResponse.getCodeState()).isEqualTo(CodeState.WAITING_FOR_USER_RUN);
        assertThat(finalResponse.getCodeState()).isEqualTo(CodeState.RUN_SUCCEEDED);
        assertThat(finalResponse.getStdout()).isEqualTo("done");
        assertThat(delegate.listenerCount()).isEqualTo(0);
    }

    @Test
    public void awaitFinalRunResponseReturnsWaitingWhenTimeoutExpiresFirst() {
        WaitingCodeHostService delegate = new WaitingCodeHostService();
        AtomicBoolean cleared = new AtomicBoolean(false);
        ModelContextProtocolAiCodeHostService uut = new ModelContextProtocolAiCodeHostService(
            delegate,
            () -> cleared.set(true),
            () -> Long.valueOf(25L));

        RunCodeResponse waitingResponse = uut.runCode(new RunCodeRequest(ScriptHost.AI, token()));
        RunCodeResponse finalResponse = uut.awaitFinalRunResponse(waitingResponse);

        assertThat(cleared.get()).isTrue();
        assertThat(waitingResponse.getCodeState()).isEqualTo(CodeState.WAITING_FOR_USER_RUN);
        assertThat(finalResponse).isSameAs(waitingResponse);
        assertThat(delegate.listenerCount()).isEqualTo(0);
    }

    @Test
    public void runCodeCleansUpWhenImmediateResponseIsNotWaiting() {
        WaitingCodeHostService delegate = new WaitingCodeHostService();
        delegate.setInitialResponse(new RunCodeResponse(
            ScriptHost.AI,
            "text/x-freeplane-script-groovy",
            CodeState.RUN_SUCCEEDED,
            ScriptRunInitiator.AI,
            token(),
            null,
            null,
            "done",
            null));
        ModelContextProtocolAiCodeHostService uut = new ModelContextProtocolAiCodeHostService(
            delegate,
            () -> {},
            () -> Long.valueOf(1000L));

        RunCodeResponse response = uut.runCode(new RunCodeRequest(ScriptHost.AI, token()));

        assertThat(response.getCodeState()).isEqualTo(CodeState.RUN_SUCCEEDED);
        assertThat(delegate.listenerCountWhenRunCodeStarted).isEqualTo(1);
        assertThat(delegate.listenerCount()).isEqualTo(0);
    }

    @Test
    public void runCodeCleansUpWhenDelegateFails() {
        WaitingCodeHostService delegate = new WaitingCodeHostService();
        delegate.failWith(new IllegalStateException("failed"));
        ModelContextProtocolAiCodeHostService uut = new ModelContextProtocolAiCodeHostService(
            delegate,
            () -> {},
            () -> Long.valueOf(1000L));

        assertThatThrownBy(() -> uut.runCode(new RunCodeRequest(ScriptHost.AI, token())))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("failed");
        assertThat(delegate.listenerCountWhenRunCodeStarted).isEqualTo(1);
        assertThat(delegate.listenerCount()).isEqualTo(0);
    }

    @Test
    public void awaitFinalRunResponseReturnsWaitingWhenNoMatchingPendingRunExists() {
        WaitingCodeHostService delegate = new WaitingCodeHostService();
        ModelContextProtocolAiCodeHostService uut = new ModelContextProtocolAiCodeHostService(
            delegate,
            () -> {},
            () -> Long.valueOf(1000L));
        RunCodeResponse waitingResponse = new RunCodeResponse(
            ScriptHost.AI,
            "text/x-freeplane-script-groovy",
            CodeState.WAITING_FOR_USER_RUN,
            ScriptRunInitiator.AI,
            token(),
            null,
            null,
            null,
            null);

        RunCodeResponse finalResponse = uut.awaitFinalRunResponse(waitingResponse);

        assertThat(finalResponse).isSameAs(waitingResponse);
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

    private static CodeStateToken token() {
        return new CodeStateToken("code", "args");
    }

    private static class WaitingCodeHostService implements AiCodeHostService {
        private final Set<AiCodeRunListener> listeners = new CopyOnWriteArraySet<AiCodeRunListener>();
        private volatile RunCodeResponse initialResponse;
        private volatile RunCodeResponse finalResponse;
        private volatile long finalResponseDelayMillis;
        private volatile int listenerCountWhenRunCodeStarted;
        private volatile RuntimeException runCodeFailure;

        private void setInitialResponse(RunCodeResponse initialResponse) {
            this.initialResponse = initialResponse;
        }

        private void setFinalResponse(RunCodeResponse finalResponse, long finalResponseDelayMillis) {
            this.finalResponse = finalResponse;
            this.finalResponseDelayMillis = finalResponseDelayMillis;
        }

        private void failWith(RuntimeException runCodeFailure) {
            this.runCodeFailure = runCodeFailure;
        }

        private int listenerCount() {
            return listeners.size();
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
            listenerCountWhenRunCodeStarted = listeners.size();
            if (runCodeFailure != null) {
                throw runCodeFailure;
            }
            RunCodeResponse response = initialResponse != null ? initialResponse : new RunCodeResponse(
                ScriptHost.AI,
                "text/x-freeplane-script-groovy",
                CodeState.WAITING_FOR_USER_RUN,
                ScriptRunInitiator.AI,
                token(),
                null,
                null,
                null,
                null);
            if (response.getCodeState() == CodeState.WAITING_FOR_USER_RUN && finalResponse != null) {
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
        public RunCodeResponse writeAndRunCode(WriteAndRunCodeRequest request) {
            return runCode(new RunCodeRequest(ScriptHost.AI, token()));
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
