package org.freeplane.plugin.ai.mcpserver;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class ModelContextProtocolToolDispatcherTest {

    @Test
    public void registryAndDispatcherUseSameOrderedToolObjectList() {
        List<Object> toolObjects = Arrays.<Object>asList(new FirstToolSet(), new SecondToolSet());
        ModelContextProtocolToolRegistry registry = new ModelContextProtocolToolRegistry(toolObjects, new ObjectMapper());
        ModelContextProtocolToolDispatcher dispatcher = new ModelContextProtocolToolDispatcher(toolObjects, new ObjectMapper());

        List<ModelContextProtocolTool> tools = registry.listTools();
        AtomicReference<ToolExecutionResult> result = new AtomicReference<ToolExecutionResult>();
        try {
            SwingUtilities.invokeAndWait(() -> result.set(dispatcher.dispatch("secondTool", null)));
        } catch (Exception error) {
            throw new AssertionError(error);
        }

        assertThat(tools).extracting(ModelContextProtocolTool::getName).containsExactly("firstTool", "secondTool");
        assertThat(result.get().resultText()).isEqualTo("second");
    }

    @Test
    public void dispatcherConsultsAuthorizerBeforeExecutingTool() {
        AtomicBoolean authorized = new AtomicBoolean(false);
        List<Object> toolObjects = Arrays.<Object>asList(new AuthorizationAwareToolSet(authorized));
        ModelContextProtocolToolCallAuthorizer authorizer = mock(ModelContextProtocolToolCallAuthorizer.class);
        doAnswer(invocation -> {
            authorized.set(true);
            return null;
        }).when(authorizer).assertAuthorized(eq("secondTool"), eq(null));
        ModelContextProtocolToolDispatcher dispatcher = new ModelContextProtocolToolDispatcher(
            toolObjects,
            new ObjectMapper(),
            authorizer);

        AtomicReference<ToolExecutionResult> result = new AtomicReference<ToolExecutionResult>();
        try {
            SwingUtilities.invokeAndWait(() -> result.set(dispatcher.dispatch("secondTool", null)));
        } catch (Exception error) {
            throw new AssertionError(error);
        }

        verify(authorizer).assertAuthorized(eq("secondTool"), eq(null));
        assertThat(result.get().resultText()).isEqualTo("authorized");
    }

    private static class FirstToolSet {
        @Tool("first")
        public String firstTool() {
            return "first";
        }
    }

    private static class SecondToolSet {
        @Tool("second")
        public String secondTool() {
            return "second";
        }
    }

    private static class AuthorizationAwareToolSet {
        private final AtomicBoolean authorized;

        private AuthorizationAwareToolSet(AtomicBoolean authorized) {
            this.authorized = authorized;
        }

        @Tool("second")
        public String secondTool() {
            return authorized.get() ? "authorized" : "not authorized";
        }
    }
}
