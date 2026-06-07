package org.freeplane.plugin.ai.tools.utilities;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CancellationToolExecutorTest {
    @Test
    public void execute_throwsWhenCancelled() {
        AtomicBoolean cancelled = new AtomicBoolean(true);
        ToolExecutor delegate = new StubToolExecutor("ok");
        CancellationToolExecutor uut = new CancellationToolExecutor(delegate, cancelled::get);

        assertThatThrownBy(() -> uut.execute(null, "memory"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("cancelled");
    }

    @Test
    public void execute_delegatesWhenNotCancelled() {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        StubToolExecutor delegate = new StubToolExecutor("ok");
        CancellationToolExecutor uut = new CancellationToolExecutor(delegate, cancelled::get);

        String result = uut.execute(null, "memory");

        assertThat(result).isEqualTo("ok");
        assertThat(delegate.wasCalled()).isTrue();
    }

    private static class StubToolExecutor implements ToolExecutor {
        private final String resultText;
        private boolean called;

        private StubToolExecutor(String resultText) {
            this.resultText = resultText;
        }

        @Override
        public String execute(ToolExecutionRequest request, Object memoryId) {
            called = true;
            return resultText;
        }

        @Override
        public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext invocationContext) {
            called = true;
            return null;
        }

        private boolean wasCalled() {
            return called;
        }
    }
}
