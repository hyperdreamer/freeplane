package org.freeplane.plugin.ai.tools.tagcategories;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import java.util.List;
import java.util.Map;
import org.freeplane.plugin.ai.mcpserver.ModelContextProtocolTool;
import org.freeplane.plugin.ai.mcpserver.ModelContextProtocolToolRegistry;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TagCategoryToolSchemaTest {
    @Test
    public void editTagCategoriesSchemaKeepsConditionalInstructionFieldsOptional() {
        ModelContextProtocolToolRegistry uut = new ModelContextProtocolToolRegistry(new DummyToolSet(), new ObjectMapper());

        ModelContextProtocolTool tool = uut.listTools().get(0);
        Map<String, Object> instructionSchema = findObjectSchemaWithProperty(tool.getInputSchema(), "newParentPath");

        assertThat(instructionSchema).isNotNull();
        @SuppressWarnings("unchecked")
        List<String> requiredProperties = (List<String>) instructionSchema.get("required");
        assertThat(requiredProperties).containsExactly("type");
    }

    private Map<String, Object> findObjectSchemaWithProperty(Map<String, Object> schema, String propertyName) {
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        if (properties != null && properties.containsKey(propertyName)) {
            return schema;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> definitions = (Map<String, Object>) schema.get("$defs");
        if (definitions != null) {
            for (Object definition : definitions.values()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> definitionMap = (Map<String, Object>) definition;
                Map<String, Object> nestedMatch = findObjectSchemaWithProperty(definitionMap, propertyName);
                if (nestedMatch != null) {
                    return nestedMatch;
                }
            }
        }
        if (properties != null) {
            for (Object propertySchema : properties.values()) {
                if (!(propertySchema instanceof Map)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> propertyMap = (Map<String, Object>) propertySchema;
                Map<String, Object> nestedMatch = findObjectSchemaWithProperty(propertyMap, propertyName);
                if (nestedMatch != null) {
                    return nestedMatch;
                }
            }
        }
        Object items = schema.get("items");
        if (items instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> itemMap = (Map<String, Object>) items;
            Map<String, Object> nestedMatch = findObjectSchemaWithProperty(itemMap, propertyName);
            if (nestedMatch != null) {
                return nestedMatch;
            }
        }
        Object anyOf = schema.get("anyOf");
        if (anyOf instanceof List) {
            for (Object option : (List<?>) anyOf) {
                if (!(option instanceof Map)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> optionMap = (Map<String, Object>) option;
                Map<String, Object> nestedMatch = findObjectSchemaWithProperty(optionMap, propertyName);
                if (nestedMatch != null) {
                    return nestedMatch;
                }
            }
        }
        return null;
    }

    private static class DummyToolSet {
        @Tool("Edit tag categories.")
        public void editTagCategories(TagCategoryInstructionRequestPayload request) {
        }
    }
}
