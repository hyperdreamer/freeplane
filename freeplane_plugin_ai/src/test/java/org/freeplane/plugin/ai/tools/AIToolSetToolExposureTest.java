package org.freeplane.plugin.ai.tools;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonReferenceSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import java.lang.reflect.Method;
import java.util.Map;
import org.freeplane.plugin.ai.tools.code.AiCodeToolSet;
import org.freeplane.plugin.ai.tools.code.WriteAndRunCodeToolRequest;
import org.freeplane.plugin.ai.tools.code.WriteCodeToolRequest;
import org.freeplane.plugin.ai.tools.read.ReadNodesWithDescendantsRequest;
import org.freeplane.plugin.ai.tools.search.SearchNodesRequest;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AIToolSetToolExposureTest {
    @Test
    public void readNodesWithDescendants_isExposedAsToolMethod() throws Exception {
        Method method = AIToolSet.class.getMethod(
            "readNodesWithDescendants",
            ReadNodesWithDescendantsRequest.class);

        Tool annotation = method.getAnnotation(Tool.class);

        assertThat(annotation).isNotNull();
    }

    @Test
    public void readNodesWithDescendantsAsPlainText_isExposedAsToolMethod() throws Exception {
        Method method = AIToolSet.class.getMethod(
            "readNodesWithDescendantsAsPlainText",
            ReadNodesWithDescendantsRequest.class);

        Tool annotation = method.getAnnotation(Tool.class);

        assertThat(annotation).isNotNull();
    }

    @Test
    public void contextGatheringRequestSchemasUseToolParameterStructures() throws Exception {
        assertReadRequestSchema(requestSchemaFor(
            AIToolSet.class,
            "readNodesWithDescendants",
            ReadNodesWithDescendantsRequest.class));
        assertReadRequestSchema(requestSchemaFor(
            AIToolSet.class,
            "readNodesWithDescendantsAsPlainText",
            ReadNodesWithDescendantsRequest.class));
        assertSearchRequestSchema(requestSchemaFor(
            AIToolSet.class,
            "searchNodes",
            SearchNodesRequest.class));
    }

    @Test
    public void getApiDocumentation_isExposedAsToolMethod() throws Exception {
        Method method = AIToolSet.class.getMethod("getApiDocumentation");

        Tool annotation = method.getAnnotation(Tool.class);

        assertThat(annotation).isNotNull();
    }

    @Test
    public void writeAndRunCodeToolSpecificationExplainsGroovyArgsSelectionAndTokenFreeFlow() throws Exception {
        ToolSpecification specification = toolSpecificationFor(
            AiCodeToolSet.class,
            "writeAndRunCode",
            WriteAndRunCodeToolRequest.class);

        assertThat(specification.description()).contains("AI-owned Groovy script host");
        assertThat(specification.description()).contains("current Freeplane selection");
        assertThat(specification.description()).contains("exposed to the script as args");
        assertThat(specification.description()).contains("No prior readCode or expectedStateToken is required");
    }

    @Test
    public void writeAndRunCodeSchemaMarksArgumentsJsonTextOptional() throws Exception {
        JsonObjectSchema requestSchema = requestSchemaFor(
            AiCodeToolSet.class,
            "writeAndRunCode",
            WriteAndRunCodeToolRequest.class);
        JsonObjectSchema contentSchema = propertyObjectSchema(requestSchema, "content");

        assertThat(requestSchema.required()).containsExactly("content");
        assertThat(contentSchema.required()).containsExactly("sourceText");
        assertThat(contentSchema.properties()).containsKeys("sourceText", "argumentsJsonText");
    }

    @Test
    public void writeCodeSchemaMarksArgumentsJsonTextOptional() throws Exception {
        JsonObjectSchema requestSchema = requestSchemaFor(
            AiCodeToolSet.class,
            "writeCode",
            WriteCodeToolRequest.class);
        JsonObjectSchema contentSchema = propertyObjectSchema(requestSchema, "content");

        assertThat(requestSchema.required()).containsExactly("host", "content");
        assertThat(contentSchema.required()).containsExactly("sourceText");
        assertThat(contentSchema.properties()).containsKeys("sourceText", "argumentsJsonText");
    }

    @Test
    public void writeCodeSchemaMarksExpectedStateTokenOptional() throws Exception {
        JsonObjectSchema requestSchema = requestSchemaFor(
            AiCodeToolSet.class,
            "writeCode",
            WriteCodeToolRequest.class);

        assertThat(requestSchema.required()).containsExactly("host", "content");
        assertThat(requestSchema.properties()).containsKey("expectedStateToken");
    }

    private ToolSpecification toolSpecificationFor(Class<?> toolSetClass,
                                                   String methodName,
                                                   Class<?> requestType) throws Exception {
        Method method = toolSetClass.getMethod(methodName, requestType);
        return ToolSpecifications.toolSpecificationFrom(method);
    }

    private JsonObjectSchema requestSchemaFor(Class<?> toolSetClass,
                                              String methodName,
                                              Class<?> requestType) throws Exception {
        ToolSpecification specification = toolSpecificationFor(toolSetClass, methodName, requestType);
        Map<String, JsonSchemaElement> properties = specification.parameters().properties();
        assertThat(properties).containsOnlyKeys("request");
        assertThat(specification.parameters().required()).containsExactly("request");
        assertThat(properties.get("request")).isInstanceOf(JsonObjectSchema.class);
        return (JsonObjectSchema) properties.get("request");
    }

    private void assertReadRequestSchema(JsonObjectSchema requestSchema) {
        assertThat(requestSchema.properties()).containsKeys(
            "mapIdentifier",
            "nodeIdentifiers",
            "contextSections",
            "fullContentDepth",
            "additionalSummaryDepth",
            "maxCharacters");
        assertThat(requestSchema.properties()).doesNotContainKeys(
            "summaryDepth",
            "maximumTotalTextCharacters");
        assertThat(requestSchema.required()).containsExactly("mapIdentifier");
        assertNoPresenceHelperFields(requestSchema);
    }

    private void assertSearchRequestSchema(JsonObjectSchema requestSchema) {
        assertThat(requestSchema.properties()).containsKeys(
            "mapIdentifier",
            "queryText",
            "subtreeRootNodeIdentifiers",
            "nodeContentRequestForSearch",
            "matchingMode",
            "caseSensitivity",
            "resultSections",
            "offset",
            "limit",
            "maxCharacters");
        assertThat(requestSchema.properties()).doesNotContainKeys("maximumTotalTextCharacters");
        assertThat(requestSchema.required()).containsExactly("mapIdentifier", "queryText");
        assertNoPresenceHelperFields(requestSchema);
    }

    private JsonObjectSchema propertyObjectSchema(JsonObjectSchema requestSchema, String propertyName) {
        JsonSchemaElement property = requestSchema.properties().get(propertyName);
        assertThat(property).isNotNull();
        if (property instanceof JsonObjectSchema) {
            return (JsonObjectSchema) property;
        }
        assertThat(property).isInstanceOf(JsonReferenceSchema.class);
        JsonSchemaElement definition = requestSchema.definitions().get(((JsonReferenceSchema) property).reference());
        assertThat(definition).isInstanceOf(JsonObjectSchema.class);
        return (JsonObjectSchema) definition;
    }

    private void assertNoPresenceHelperFields(JsonObjectSchema requestSchema) {
        for (String propertyName : requestSchema.properties().keySet()) {
            assertThat(propertyName).doesNotStartWith("has");
        }
    }
}
