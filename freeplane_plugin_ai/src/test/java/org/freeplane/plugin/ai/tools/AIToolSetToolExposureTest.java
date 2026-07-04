package org.freeplane.plugin.ai.tools;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import java.lang.reflect.Method;
import java.util.Map;
import org.freeplane.plugin.ai.tools.read.ReadNodesWithDescendantsRequest;
import org.freeplane.plugin.ai.tools.search.SearchNodesRequest;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AIToolSetToolExposureTest {
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
            "readNodesWithDescendants",
            ReadNodesWithDescendantsRequest.class));
        assertReadRequestSchema(requestSchemaFor(
            "readNodesWithDescendantsAsPlainText",
            ReadNodesWithDescendantsRequest.class));
        assertSearchRequestSchema(requestSchemaFor(
            "searchNodes",
            SearchNodesRequest.class));
    }

    @Test
    public void getApiDocumentation_isExposedAsToolMethod() throws Exception {
        Method method = AIToolSet.class.getMethod("getApiDocumentation");

        Tool annotation = method.getAnnotation(Tool.class);

        assertThat(annotation).isNotNull();
    }

    private JsonObjectSchema requestSchemaFor(String methodName, Class<?> requestType) throws Exception {
        Method method = AIToolSet.class.getMethod(methodName, requestType);
        ToolSpecification specification = ToolSpecifications.toolSpecificationFrom(method);
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

    private void assertNoPresenceHelperFields(JsonObjectSchema requestSchema) {
        for (String propertyName : requestSchema.properties().keySet()) {
            assertThat(propertyName).doesNotStartWith("has");
        }
    }
}
