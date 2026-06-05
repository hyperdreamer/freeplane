package org.freeplane.plugin.ai.tools.utilities;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import java.util.Objects;
import java.util.function.Supplier;

class CancellationToolExecutor implements ToolExecutor {
    private final ToolExecutor delegate;
    private final Supplier<Boolean> cancellationSupplier;

    CancellationToolExecutor(ToolExecutor delegate, Supplier<Boolean> cancellationSupplier) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.cancellationSupplier = Objects.requireNonNull(cancellationSupplier, "cancellationSupplier");
    }

    @Override
    public String execute(ToolExecutionRequest request, Object memoryId) {
        if (isCancelled()) {
            throw new IllegalStateException("Chat request was cancelled.");
        }
        return delegate.execute(request, memoryId);
    }

    @Override
    public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext invocationContext) {
        if (isCancelled()) {
            throw new IllegalStateException("Chat request was cancelled.");
        }
        return delegate.executeWithContext(request, invocationContext);
    }

    private boolean isCancelled() {
        return Boolean.TRUE.equals(cancellationSupplier.get());
    }
}
