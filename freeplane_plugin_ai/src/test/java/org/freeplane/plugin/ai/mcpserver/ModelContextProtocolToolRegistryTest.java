package org.freeplane.plugin.ai.mcpserver;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import java.util.Collections;
import org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevel;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ModelContextProtocolToolRegistryTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void listToolsAppendsDisabledFormulaCapabilityNotes() {
        ModelContextProtocolToolRegistry uut = new ModelContextProtocolToolRegistry(
            Collections.singletonList(new DummyToolSet()),
            objectMapper,
            () -> ToolAvailabilityLevel.EDITING,
            () -> Boolean.FALSE);

        java.util.List<ModelContextProtocolTool> tools = uut.listTools();

        assertThat(findTool(tools, "previewFormulaUpdates").getDescription())
            .contains("Current formula authoring capability: disabled");
        assertThat(findTool(tools, "writeCode").getDescription())
            .contains("Current attached formula editing capability: disabled");
    }

    @Test
    public void listToolsAppendsEnabledFormulaCapabilityNotes() {
        ModelContextProtocolToolRegistry uut = new ModelContextProtocolToolRegistry(
            Collections.singletonList(new DummyToolSet()),
            objectMapper,
            () -> ToolAvailabilityLevel.EDITING,
            () -> Boolean.TRUE);

        java.util.List<ModelContextProtocolTool> tools = uut.listTools();

        assertThat(findTool(tools, "previewFormulaUpdates").getDescription())
            .contains("Current formula authoring capability: enabled");
        assertThat(findTool(tools, "compileCode").getDescription())
            .contains("Current attached formula editing capability: enabled");
    }

    private ModelContextProtocolTool findTool(java.util.List<ModelContextProtocolTool> tools, String name) {
        for (ModelContextProtocolTool tool : tools) {
            if (name.equals(tool.getName())) {
                return tool;
            }
        }
        throw new AssertionError("Tool not found: " + name);
    }

    private static class DummyToolSet {
        @Tool("Preview formula updates")
        public void previewFormulaUpdates() {
        }

        @Tool("Write code")
        public void writeCode() {
        }

        @Tool("Compile code")
        public void compileCode() {
        }
    }
}
