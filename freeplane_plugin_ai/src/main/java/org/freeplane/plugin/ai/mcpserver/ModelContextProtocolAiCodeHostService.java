package org.freeplane.plugin.ai.mcpserver;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
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
        clearPendingAiOwnedUserRunFollowup(request == null ? null : request.getHost());
        RunCodeResponse response = delegate.runCode(request);
        if (!shouldWaitForFinalUserRunResponse(response)) {
            return response;
        }
        return waitForFinalUserRunResponse(response);
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

    private boolean shouldWaitForFinalUserRunResponse(RunCodeResponse response) {
        return response != null
            && response.getHost() == ScriptHost.AI
            && response.getCodeState() == CodeState.WAITING_FOR_USER_RUN
            && !SwingUtilities.isEventDispatchThread();
    }

    private RunCodeResponse waitForFinalUserRunResponse(RunCodeResponse waitingResponse) {
        long waitTimeoutMillis = normalizedWaitTimeoutMillis(waitTimeoutMillisSupplier.get());
        if (waitTimeoutMillis <= 0L) {
            return waitingResponse;
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<RunCodeResponse> finalResponse = new AtomicReference<RunCodeResponse>();
        AiCodeRunListener listener = response -> {
            if (response == null || response.getHost() != ScriptHost.AI
                || response.getCodeState() == CodeState.WAITING_FOR_USER_RUN) {
                return;
            }
            finalResponse.compareAndSet(null, response);
            latch.countDown();
        };
        delegate.addRunListener(listener);
        try {
            if (latch.await(waitTimeoutMillis, TimeUnit.MILLISECONDS)) {
                RunCodeResponse response = finalResponse.get();
                if (response != null) {
                    return response;
                }
            }
            return waitingResponse;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return waitingResponse;
        } finally {
            delegate.removeRunListener(listener);
        }
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
