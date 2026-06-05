package org.freeplane.plugin.ai.tools.utilities;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ToolExecutorRegistryTest {

    @Test
    public void createRegistryMergesMultipleToolObjectsInSuppliedOrder() {
        ToolExecutorRegistry registry = new ToolExecutorFactory(true, true).createRegistry(
            Arrays.<Object>asList(new FirstToolSet(), new SecondToolSet()));

        assertThat(new ArrayList<String>(registry.getExecutorsByName().keySet())).containsExactly(
            "beta",
            "gamma",
            "alpha");
        assertThat(toolSpecificationNames(registry)).containsExactly("beta", "gamma", "alpha");
    }

    @Test
    public void createRegistryRejectsDuplicateToolNamesAcrossToolObjects() {
        ToolExecutorFactory factory = new ToolExecutorFactory(true, true);

        assertThatThrownBy(() -> factory.createRegistry(Arrays.<Object>asList(
            new FirstToolSet(),
            new DuplicateToolSet())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Duplicate tool name: beta");
    }

    @Test
    public void filteredKeepsSubsetInOriginalRegistryOrderAndAlignsSpecifications() {
        ToolExecutorRegistry registry = new ToolExecutorFactory(true, true).createRegistry(
            Arrays.<Object>asList(new FirstToolSet(), new SecondToolSet()));

        ToolExecutorRegistry filteredRegistry = registry.filtered(Arrays.asList("alpha", "gamma"));

        List<String> expectedToolNames = filterToolNames(registry.getExecutorsByName(), "gamma", "alpha");
        assertThat(new ArrayList<String>(filteredRegistry.getExecutorsByName().keySet())).isEqualTo(expectedToolNames);
        assertThat(toolSpecificationNames(filteredRegistry)).isEqualTo(expectedToolNames);
    }

    private List<String> filterToolNames(Map<String, ?> valuesByName, String... allowedNames) {
        List<String> expected = new ArrayList<String>();
        List<String> allowed = Arrays.asList(allowedNames);
        for (String toolName : valuesByName.keySet()) {
            if (allowed.contains(toolName)) {
                expected.add(toolName);
            }
        }
        return expected;
    }

    private List<String> toolSpecificationNames(ToolExecutorRegistry registry) {
        List<String> toolNames = new ArrayList<String>();
        for (ToolSpecification specification : registry.getExecutorsBySpecification().keySet()) {
            toolNames.add(specification.name());
        }
        return toolNames;
    }

    private static class FirstToolSet {
        @Tool("beta")
        public String beta() {
            return "beta";
        }

        @Tool("gamma")
        public String gamma() {
            return "gamma";
        }
    }

    private static class SecondToolSet {
        @Tool("alpha")
        public String alpha() {
            return "alpha";
        }
    }

    private static class DuplicateToolSet {
        @Tool("beta")
        public String beta() {
            return "beta";
        }
    }
}
