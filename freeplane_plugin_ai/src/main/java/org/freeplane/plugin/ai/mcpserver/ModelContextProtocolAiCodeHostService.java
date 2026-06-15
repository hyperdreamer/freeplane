package org.freeplane.plugin.ai.mcpserver;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.swing.Timer;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.features.ai.code.AiChatCodeOperationResult;
import org.freeplane.features.ai.code.AiCodeHostService;
import org.freeplane.features.ai.code.AiCodeRunListener;
import org.freeplane.features.ai.code.CompileCodeRequest;
import org.freeplane.features.ai.code.CompileCodeResponse;
import org.freeplane.features.ai.code.CodeState;
import org.freeplane.features.ai.code.EvaluateFormulaRequest;
import org.freeplane.features.ai.code.ReadCodeRequest;
import org.freeplane.features.ai.code.ReadCodeResponse;
import org.freeplane.features.ai.code.RunCodeRequest;
import org.freeplane.features.ai.code.RunCodeResponse;
import org.freeplane.features.ai.code.ScriptHost;
import org.freeplane.features.ai.code.WriteCodeRequest;
import org.freeplane.features.ai.code.WriteCodeResponse;

public class ModelContextProtocolAiCodeHostService implements AiCodeHostService {
    static final String MCP_USER_RUN_WAIT_TIMEOUT_SECONDS_PROPERTY = "ai_mcp_user_run_wait_timeout_seconds";
    static final long DEFAULT_WAIT_TIMEOUT_MILLIS = 30000L;

    private final AiCodeHostService delegate;
    private final Runnable pendingAiOwnedUserRunFollowupResetter;
    private final Supplier<Long> waitTimeoutMillisSupplier;
    private final AtomicReference<PendingRunCompletion> pendingRunCompletion =
        new AtomicReference<PendingRunCompletion>();

    public ModelContextProtocolAiCodeHostService(AiCodeHostService delegate,
                                                  Runnable pendingAiOwnedUserRunFollowupResetter) {
        this(delegate,
            pendingAiOwnedUserRunFollowupResetter,
            () -> waitTimeoutMillis(ResourceController.getResourceController()));
    }

    ModelContextProtocolAiCodeHostService(AiCodeHostService delegate,
                                          Runnable pendingAiOwnedUserRunFollowupResetter,
                                          Supplier<Long> waitTimeoutMillisSupplier) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.pendingAiOwnedUserRunFollowupResetter = Objects.requireNonNull(
            pendingAiOwnedUserRunFollowupResetter,
            "pendingAiOwnedUserRunFollowupResetter");
        this.waitTimeoutMillisSupplier = Objects.requireNonNull(waitTimeoutMillisSupplier, "waitTimeoutMillisSupplier");
    }

    @Override
    public ReadCodeResponse readCode(ReadCodeRequest request) {
        return delegate.readCode(request);
    }

    @Override
    public WriteCodeResponse writeCode(WriteCodeRequest request) {
        clearPendingAiOwnedUserRunFollowup(request == null ? null : request.getHost());
        return delegate.writeCode(request);
    }

    @Override
    public CompileCodeResponse compileCode(CompileCodeRequest request) {
        return delegate.compileCode(request);
    }

    @Override
    public RunCodeResponse runCode(RunCodeRequest request) {
        ScriptHost host = request == null ? null : request.getHost();
        clearPendingAiOwnedUserRunFollowup(host);
        PendingRunCompletion completion = host == ScriptHost.AI ? startPendingRunCompletion() : null;
        try {
            RunCodeResponse response = delegate.runCode(request);
            if (completion == null) {
                return response;
            }
            if (shouldWaitForFinalUserRunResponse(response)) {
                completion.waitingResponse(response);
                return response;
            }
            cancelPendingRunCompletion(completion);
            return response;
        } catch (RuntimeException error) {
            if (completion != null) {
                cancelPendingRunCompletion(completion);
            }
            throw error;
        }
    }

    @Override
    public AiChatCodeOperationResult evaluateFormula(EvaluateFormulaRequest request) {
        return delegate.evaluateFormula(request);
    }

    @Override
    public void addRunListener(AiCodeRunListener listener) {
        delegate.addRunListener(listener);
    }

    @Override
    public void removeRunListener(AiCodeRunListener listener) {
        delegate.removeRunListener(listener);
    }

    private void clearPendingAiOwnedUserRunFollowup(ScriptHost host) {
        if (host == ScriptHost.AI) {
            pendingAiOwnedUserRunFollowupResetter.run();
        }
    }

    RunCodeResponse awaitFinalRunResponse(RunCodeResponse waitingResponse) {
        PendingRunCompletion completion = pendingRunCompletion.get();
        if (completion == null || !completion.matches(waitingResponse)) {
            return waitingResponse;
        }
        try {
            return completion.await(waitingResponse);
        } finally {
            if (completion.isCompleted()) {
                pendingRunCompletion.compareAndSet(completion, null);
            }
        }
    }

    private PendingRunCompletion startPendingRunCompletion() {
        PendingRunCompletion completion = new PendingRunCompletion();
        PendingRunCompletion previousCompletion = pendingRunCompletion.getAndSet(completion);
        if (previousCompletion != null) {
            previousCompletion.cancel();
        }
        completion.start();
        return completion;
    }

    private void cancelPendingRunCompletion(PendingRunCompletion completion) {
        completion.cancel();
        pendingRunCompletion.compareAndSet(completion, null);
    }

    private static boolean shouldWaitForFinalUserRunResponse(RunCodeResponse response) {
        return response != null
            && response.getHost() == ScriptHost.AI
            && response.getCodeState() == CodeState.WAITING_FOR_USER_RUN;
    }

    private static boolean isFinalAiRunResponse(RunCodeResponse response) {
        return response != null
            && response.getHost() == ScriptHost.AI
            && response.getCodeState() != CodeState.WAITING_FOR_USER_RUN;
    }

    private final class PendingRunCompletion {
        private final CountDownLatch completion = new CountDownLatch(1);
        private final AtomicBoolean completed = new AtomicBoolean(false);
        private final AtomicReference<RunCodeResponse> terminalResponse = new AtomicReference<RunCodeResponse>();
        private final AtomicReference<RunCodeResponse> waitingResponse = new AtomicReference<RunCodeResponse>();
        private final Timer timeoutTimer;
        private final AiCodeRunListener listener;

        private PendingRunCompletion() {
            timeoutTimer = new Timer(timeoutMillisAsInt(), event -> complete(null));
            timeoutTimer.setRepeats(false);
            listener = response -> {
                if (isFinalAiRunResponse(response)) {
                    complete(response);
                }
            };
        }

        private void start() {
            delegate.addRunListener(listener);
            timeoutTimer.start();
        }

        private void waitingResponse(RunCodeResponse response) {
            waitingResponse.compareAndSet(null, response);
        }

        private boolean matches(RunCodeResponse response) {
            if (!shouldWaitForFinalUserRunResponse(response)) {
                return false;
            }
            RunCodeResponse storedWaitingResponse = waitingResponse.get();
            if (storedWaitingResponse == null || storedWaitingResponse == response) {
                return true;
            }
            return storedWaitingResponse.getStateToken() != null
                && storedWaitingResponse.getStateToken().matches(response.getStateToken());
        }

        private RunCodeResponse await(RunCodeResponse waitingResponse) {
            try {
                completion.await();
                RunCodeResponse response = terminalResponse.get();
                return response != null ? response : waitingResponse;
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return waitingResponse;
            }
        }

        private void cancel() {
            complete(null);
        }

        private boolean isCompleted() {
            return completed.get();
        }

        private void complete(RunCodeResponse response) {
            if (!completed.compareAndSet(false, true)) {
                return;
            }
            if (response != null) {
                terminalResponse.set(response);
            }
            timeoutTimer.stop();
            delegate.removeRunListener(listener);
            completion.countDown();
        }
    }

    private int timeoutMillisAsInt() {
        return (int) Math.min(normalizedWaitTimeoutMillis(waitTimeoutMillisSupplier.get()), Integer.MAX_VALUE);
    }

    private long normalizedWaitTimeoutMillis(Long waitTimeoutMillis) {
        if (waitTimeoutMillis == null || waitTimeoutMillis.longValue() <= 0L) {
            return DEFAULT_WAIT_TIMEOUT_MILLIS;
        }
        return waitTimeoutMillis.longValue();
    }

    private static long waitTimeoutMillis(ResourceController resourceController) {
        if (resourceController == null) {
            return DEFAULT_WAIT_TIMEOUT_MILLIS;
        }
        int waitTimeoutSeconds = resourceController.getIntProperty(
            MCP_USER_RUN_WAIT_TIMEOUT_SECONDS_PROPERTY,
            (int) (DEFAULT_WAIT_TIMEOUT_MILLIS / 1000L));
        if (waitTimeoutSeconds <= 0) {
            return DEFAULT_WAIT_TIMEOUT_MILLIS;
        }
        return TimeUnit.SECONDS.toMillis(waitTimeoutSeconds);
    }
}
