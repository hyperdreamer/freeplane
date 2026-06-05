package org.freeplane.plugin.ai.mcpserver;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonAnyOfSchema;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNullSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.request.json.JsonReferenceSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.freeplane.plugin.ai.tools.utilities.ToolExecutorFactory;
import org.freeplane.plugin.ai.tools.utilities.ToolExecutorRegistry;

public class ModelContextProtocolToolRegistry {
    private static final Map<String, Object> EMPTY_SCHEMA = createEmptySchema();

    private final Collection<?> toolSets;
    private final ObjectMapper objectMapper;

    public ModelContextProtocolToolRegistry(Object toolSet, ObjectMapper objectMapper) {
        this(Collections.singletonList(Objects.requireNonNull(toolSet, "toolSet")), objectMapper);
    }

    public ModelContextProtocolToolRegistry(Collection<?> toolSets, ObjectMapper objectMapper) {
        this.toolSets = Objects.requireNonNull(toolSets, "toolSets");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public List<ModelContextProtocolTool> listTools() {
        ToolExecutorRegistry toolExecutorRegistry = new ToolExecutorFactory(false, false).createRegistry(toolSets);
        List<ModelContextProtocolTool> tools = new ArrayList<ModelContextProtocolTool>(
            toolExecutorRegistry.getExecutorsBySpecification().size());
        for (ToolSpecification specification : toolExecutorRegistry.getExecutorsBySpecification().keySet()) {
            Map<String, Object> inputSchema = EMPTY_SCHEMA;
            JsonObjectSchema parameters = specification.parameters();
            if (parameters != null) {
                inputSchema = jsonSchemaElementToMap(parameters);
            }
            tools.add(new ModelContextProtocolTool(
                specification.name(),
                specification.description(),
                inputSchema
            ));
        }
        return tools;
    }

    private Map<String, Object> jsonSchemaElementToMap(JsonSchemaElement schemaElement) {
        if (schemaElement instanceof JsonObjectSchema) {
            JsonObjectSchema jsonObjectSchema = (JsonObjectSchema) schemaElement;
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            map.put("type", "object");
            if (jsonObjectSchema.description() != null) {
                map.put("description", jsonObjectSchema.description());
            }
            Map<String, Object> properties = new LinkedHashMap<String, Object>();
            for (Map.Entry<String, JsonSchemaElement> entry : jsonObjectSchema.properties().entrySet()) {
                properties.put(entry.getKey(), jsonSchemaElementToMap(entry.getValue()));
            }
            map.put("properties", properties);
            if (jsonObjectSchema.required() != null) {
                map.put("required", jsonObjectSchema.required());
            }
            if (jsonObjectSchema.additionalProperties() != null) {
                map.put("additionalProperties", jsonObjectSchema.additionalProperties());
            }
            Map<String, JsonSchemaElement> definitions = jsonObjectSchema.definitions();
            if (definitions != null && !definitions.isEmpty()) {
                Map<String, Object> defs = new LinkedHashMap<String, Object>();
                for (Map.Entry<String, JsonSchemaElement> entry : definitions.entrySet()) {
                    defs.put(entry.getKey(), jsonSchemaElementToMap(entry.getValue()));
                }
                map.put("$defs", defs);
            }
            return map;
        }
        if (schemaElement instanceof JsonArraySchema) {
            JsonArraySchema jsonArraySchema = (JsonArraySchema) schemaElement;
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            map.put("type", "array");
            if (jsonArraySchema.description() != null) {
                map.put("description", jsonArraySchema.description());
            }
            JsonSchemaElement items = jsonArraySchema.items();
            if (items != null) {
                map.put("items", jsonSchemaElementToMap(items));
            }
            return map;
        }
        if (schemaElement instanceof JsonEnumSchema) {
            JsonEnumSchema jsonEnumSchema = (JsonEnumSchema) schemaElement;
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            map.put("type", "string");
            map.put("enum", jsonEnumSchema.enumValues());
            if (jsonEnumSchema.description() != null) {
                map.put("description", jsonEnumSchema.description());
            }
            return map;
        }
        if (schemaElement instanceof JsonStringSchema) {
            JsonStringSchema jsonStringSchema = (JsonStringSchema) schemaElement;
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            map.put("type", "string");
            if (jsonStringSchema.description() != null) {
                map.put("description", jsonStringSchema.description());
            }
            return map;
        }
        if (schemaElement instanceof JsonIntegerSchema) {
            JsonIntegerSchema jsonIntegerSchema = (JsonIntegerSchema) schemaElement;
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            map.put("type", "integer");
            if (jsonIntegerSchema.description() != null) {
                map.put("description", jsonIntegerSchema.description());
            }
            return map;
        }
        if (schemaElement instanceof JsonNumberSchema) {
            JsonNumberSchema jsonNumberSchema = (JsonNumberSchema) schemaElement;
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            map.put("type", "number");
            if (jsonNumberSchema.description() != null) {
                map.put("description", jsonNumberSchema.description());
            }
            return map;
        }
        if (schemaElement instanceof JsonBooleanSchema) {
            JsonBooleanSchema jsonBooleanSchema = (JsonBooleanSchema) schemaElement;
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            map.put("type", "boolean");
            if (jsonBooleanSchema.description() != null) {
                map.put("description", jsonBooleanSchema.description());
            }
            return map;
        }
        if (schemaElement instanceof JsonReferenceSchema) {
            JsonReferenceSchema jsonReferenceSchema = (JsonReferenceSchema) schemaElement;
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            map.put("$ref", "#/$defs/" + jsonReferenceSchema.reference());
            return map;
        }
        if (schemaElement instanceof JsonAnyOfSchema) {
            JsonAnyOfSchema jsonAnyOfSchema = (JsonAnyOfSchema) schemaElement;
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            if (jsonAnyOfSchema.description() != null) {
                map.put("description", jsonAnyOfSchema.description());
            }
            List<Map<String, Object>> anyOf = new ArrayList<Map<String, Object>>(jsonAnyOfSchema.anyOf().size());
            for (JsonSchemaElement element : jsonAnyOfSchema.anyOf()) {
                anyOf.add(jsonSchemaElementToMap(element));
            }
            map.put("anyOf", anyOf);
            return map;
        }
        if (schemaElement instanceof JsonNullSchema) {
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            map.put("type", "null");
            return map;
        }
        if (schemaElement instanceof JsonRawSchema) {
            JsonRawSchema jsonRawSchema = (JsonRawSchema) schemaElement;
            return parseRawSchema(jsonRawSchema.schema());
        }
        throw new IllegalArgumentException("Unsupported schema element: " + schemaElement.getClass());
    }

    private Map<String, Object> parseRawSchema(String schema) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(schema, Map.class);
            return map;
        } catch (IOException error) {
            throw new IllegalArgumentException("Invalid raw schema JSON.", error);
        }
    }

    private static Map<String, Object> createEmptySchema() {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("type", "object");
        map.put("properties", Collections.emptyMap());
        map.put("required", Collections.emptyList());
        return Collections.unmodifiableMap(map);
    }
}
