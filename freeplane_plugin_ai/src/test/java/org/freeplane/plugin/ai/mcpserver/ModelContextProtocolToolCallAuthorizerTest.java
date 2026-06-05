package org.freeplane.plugin.ai.mcpserver;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.function.Supplier;
import org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevel;
import org.freeplane.plugin.ai.tools.code.AiCodeOperationAuthorizer;
import org.freeplane.plugin.ai.tools.documentation.GetApiDocumentationResponse;
import org.freeplane.plugin.ai.tools.documentation.GetApiDocumentationTool;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ModelContextProtocolToolCallAuthorizerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void codeToolsDelegateToAiCodeOperationAuthorizer() throws Exception {
        AiCodeOperationAuthorizer aiCodeOperationAuthorizer = mock(AiCodeOperationAuthorizer.class);
        GetApiDocumentationTool getApiDocumentationTool = mock(GetApiDocumentationTool.class);
        ModelContextProtocolToolCallAuthorizer uut = new ModelContextProtocolToolCallAuthorizer(
            availability(ToolAvailabilityLevel.SCRIPT_EXECUTION),
            aiCodeOperationAuthorizer,
            getApiDocumentationTool);

        uut.assertAuthorized("runScript", objectMapper.readTree("{\"codeId\":\"ai-script-1\"}"));

        verify(aiCodeOperationAuthorizer).assertAuthorized(eq("runScript"), eq("ai-script-1"), eq(null));
    }

    @Test
    public void disabledAllowsApiDocumentation() {
        ModelContextProtocolToolCallAuthorizer uut = new ModelContextProtocolToolCallAuthorizer(
            availability(ToolAvailabilityLevel.DISABLED),
            mock(AiCodeOperationAuthorizer.class),
            mock(GetApiDocumentationTool.class));

        assertThatCode(() -> uut.assertAuthorized("getApiDocumentation", null))
            .doesNotThrowAnyException();
    }

    @Test
    public void disabledAllowsApiMapScopedReadOnlyForInternalApiMap() throws Exception {
        GetApiDocumentationTool getApiDocumentationTool = mock(GetApiDocumentationTool.class);
        when(getApiDocumentationTool.getApiDocumentation()).thenReturn(new GetApiDocumentationResponse(
            "api-map",
            "root",
            "packages",
            "groups",
            null));
        ModelContextProtocolToolCallAuthorizer uut = new ModelContextProtocolToolCallAuthorizer(
            availability(ToolAvailabilityLevel.DISABLED),
            mock(AiCodeOperationAuthorizer.class),
            getApiDocumentationTool);

        assertThatCode(() -> uut.assertAuthorized(
            "readNodesWithDescendants",
            objectMapper.readTree("{\"mapIdentifier\":\"api-map\"}")))
            .doesNotThrowAnyException();

        assertThatThrownBy(() -> uut.assertAuthorized(
            "searchNodes",
            objectMapper.readTree("{\"mapIdentifier\":\"other-map\"}")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("The requested tool is not available at the current availability level.");
    }

    @Test
    public void readingAllowsReadingToolsWithoutApiMapRestriction() throws Exception {
        ModelContextProtocolToolCallAuthorizer uut = new ModelContextProtocolToolCallAuthorizer(
            availability(ToolAvailabilityLevel.READING),
            mock(AiCodeOperationAuthorizer.class),
            mock(GetApiDocumentationTool.class));

        assertThatCode(() -> uut.assertAuthorized(
            "searchNodes",
            objectMapper.readTree("{\"mapIdentifier\":\"any-map\"}")))
            .doesNotThrowAnyException();
    }

    @Test
    public void disabledRejectsEditingTool() {
        ModelContextProtocolToolCallAuthorizer uut = new ModelContextProtocolToolCallAuthorizer(
            availability(ToolAvailabilityLevel.DISABLED),
            mock(AiCodeOperationAuthorizer.class),
            mock(GetApiDocumentationTool.class));

        assertThatThrownBy(() -> uut.assertAuthorized("createNodes", null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("The requested tool is not available at the current availability level.");
    }

    private Supplier<ToolAvailabilityLevel> availability(ToolAvailabilityLevel toolAvailability) {
        return () -> toolAvailability;
    }
}
