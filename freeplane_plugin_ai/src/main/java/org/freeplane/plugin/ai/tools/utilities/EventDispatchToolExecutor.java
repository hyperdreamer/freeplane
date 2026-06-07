package org.freeplane.plugin.ai.tools.utilities;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.ui.ViewController;

public class EventDispatchToolExecutor implements ToolExecutor {
    private final ToolExecutor delegate;

    public EventDispatchToolExecutor(ToolExecutor delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public String execute(ToolExecutionRequest request, Object memoryId) {
        InvocationContext invocationContext = InvocationContext.builder()
            .chatMemoryId(memoryId)
            .build();
        ToolExecutionResult result = executeWithContext(request, invocationContext);
        return result == null ? null : result.resultText();
    }

    @Override
    public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext invocationContext) {
        if (SwingUtilities.isEventDispatchThread()) {
            return delegate.executeWithContext(request, invocationContext);
        }
        ViewController viewController = requireViewController();
        AtomicReference<ToolExecutionResult> resultReference = new AtomicReference<>();
        AtomicReference<Throwable> errorReference = new AtomicReference<>();
        Runnable runnable = () -> {
            try {
                resultReference.set(delegate.executeWithContext(request, invocationContext));
            } catch (Throwable throwable) {
                errorReference.set(throwable);
            }
        };
        try {
            viewController.invokeAndWait(runnable);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Tool execution was interrupted.", interrupted);
        } catch (InvocationTargetException invocationTarget) {
            throw toRuntimeException(invocationTarget.getCause(), "Tool execution failed during invokeAndWait.");
        }
        Throwable error = errorReference.get();
        if (error != null) {
            throw toRuntimeException(error, "Tool execution failed.");
        }
        ToolExecutionResult result = resultReference.get();
        if (result == null) {
            throw new IllegalStateException("Tool execution did not return a result.");
        }
        return result;
    }

    private ViewController requireViewController() {
        Controller controller = Controller.getCurrentController();
        if (controller == null) {
            throw new IllegalStateException("No current controller is available.");
        }
        ViewController viewController = controller.getViewController();
        if (viewController == null) {
            throw new IllegalStateException("No view controller is available.");
        }
        return viewController;
    }

    private RuntimeException toRuntimeException(Throwable error, String message) {
        if (error instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException(message, error);
    }
}
