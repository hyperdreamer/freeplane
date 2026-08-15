package org.freeplane.plugin.ai.mcpserver;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.function.Supplier;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.tools.MapTargetToolCallAuthorizer;
import org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevel;
import org.freeplane.plugin.ai.tools.code.AiCodeOperationAuthorizer;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class ModelContextProtocolToolCallAuthorizerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void codeToolsDelegateToAiCodeOperationAuthorizer() throws Exception {
        AiCodeOperationAuthorizer aiCodeOperationAuthorizer = mock(AiCodeOperationAuthorizer.class);
        ModelContextProtocolToolCallAuthorizer uut = authorizer(
            ToolAvailabilityLevel.SCRIPT_EXECUTION,
            false,
            aiCodeOperationAuthorizer,
            mock(MapTargetToolCallAuthorizer.class));

        uut.assertAuthorized("runCode", objectMapper.readTree("{\"request\":{\"host\":\"AI\"}}"));

        verify(aiCodeOperationAuthorizer).assertAuthorized(eq("runCode"), eq(org.freeplane.features.ai.code.ScriptHost.AI));
    }

    @Test
    public void writeAndRunCodeDelegatesToAiCodeOperationAuthorizerForAiHost() throws Exception {
        AiCodeOperationAuthorizer aiCodeOperationAuthorizer = mock(AiCodeOperationAuthorizer.class);
        ModelContextProtocolToolCallAuthorizer uut = authorizer(
            ToolAvailabilityLevel.SCRIPT_EXECUTION,
            false,
            aiCodeOperationAuthorizer,
            mock(MapTargetToolCallAuthorizer.class));

        uut.assertAuthorized(
            "writeAndRunCode",
            objectMapper.readTree("{\"request\":{\"content\":{\"sourceText\":\"println 1\"}}}"));

        verify(aiCodeOperationAuthorizer).assertAuthorized(
            eq("writeAndRunCode"),
            eq(org.freeplane.features.ai.code.ScriptHost.AI));
    }

    @Test
    public void delegatesDocumentationMapTargetRestrictionToMapTargetToolCallAuthorizer() throws Exception {
        MapTargetToolCallAuthorizer mapTargetToolCallAuthorizer = mock(MapTargetToolCallAuthorizer.class);
        ModelContextProtocolToolCallAuthorizer uut = authorizer(
            ToolAvailabilityLevel.EDITING,
            true,
            mock(AiCodeOperationAuthorizer.class),
            mapTargetToolCallAuthorizer);

        uut.assertAuthorized(
            "createNodes",
            objectMapper.readTree("{\"request\":{\"mapIdentifier\":\"map-id\"}}"));

        verify(mapTargetToolCallAuthorizer).assertAuthorized("createNodes", "map-id");
    }

    @Test
    public void disabledAllowsApiDocumentation() {
        ModelContextProtocolToolCallAuthorizer uut = authorizer(
            ToolAvailabilityLevel.DISABLED,
            false,
            mock(AiCodeOperationAuthorizer.class),
            mock(MapTargetToolCallAuthorizer.class));

        assertThatCode(() -> uut.assertAuthorized("getApiDocumentation", null))
            .doesNotThrowAnyException();
    }

    @Test
    public void editingToolRejectsDocumentationMapTarget() throws Exception {
        ModelContextProtocolToolCallAuthorizer uut = authorizer(
            ToolAvailabilityLevel.EDITING,
            true,
            mock(AiCodeOperationAuthorizer.class),
            new MapTargetToolCallAuthorizer());

        assertThatThrownBy(() -> uut.assertAuthorized(
            "createNodes",
            objectMapper.readTree("{\"request\":{\"mapIdentifier\":\""
                + AvailableMaps.INTERNAL_API_DOCUMENTATION_MAP_IDENTIFIER
                + "\"}}")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("The internal API documentation map cannot be edited.");
    }

    @Test
    public void formulaToolRejectsDocumentationMapTarget() throws Exception {
        ModelContextProtocolToolCallAuthorizer uut = authorizer(
            ToolAvailabilityLevel.EDITING,
            true,
            mock(AiCodeOperationAuthorizer.class),
            new MapTargetToolCallAuthorizer());

        assertThatThrownBy(() -> uut.assertAuthorized(
            "previewFormulaUpdates",
            objectMapper.readTree("{\"request\":{\"mapIdentifier\":\""
                + AvailableMaps.INTERNAL_API_DOCUMENTATION_MAP_IDENTIFIER
                + "\"}}")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("The internal API documentation map cannot be used as a scripting or formula target.");
    }

    @Test
    public void disabledAllowsApiMapScopedReadOnlyForInternalApiMap() throws Exception {
        ModelContextProtocolToolCallAuthorizer uut = authorizer(
            ToolAvailabilityLevel.DISABLED,
            false,
            mock(AiCodeOperationAuthorizer.class),
            mock(MapTargetToolCallAuthorizer.class));

        assertThatCode(() -> uut.assertAuthorized(
            "readNodesWithDescendants",
            objectMapper.readTree("{\"request\":{\"mapIdentifier\":\""
                + AvailableMaps.INTERNAL_API_DOCUMENTATION_MAP_IDENTIFIER
                + "\"}}")))
            .doesNotThrowAnyException();

        assertThatThrownBy(() -> uut.assertAuthorized(
            "searchNodes",
            objectMapper.readTree("{\"request\":{\"mapIdentifier\":\"other-map\"}}")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("The requested tool is not available at the current availability level.");
    }

    @Test
    public void readingAllowsReadingToolsWithoutApiMapRestriction() throws Exception {
        ModelContextProtocolToolCallAuthorizer uut = authorizer(
            ToolAvailabilityLevel.READING,
            false,
            mock(AiCodeOperationAuthorizer.class),
            mock(MapTargetToolCallAuthorizer.class));

        assertThatCode(() -> uut.assertAuthorized(
            "searchNodes",
            objectMapper.readTree("{\"request\":{\"mapIdentifier\":\"any-map\"}}")))
            .doesNotThrowAnyException();
    }

    @Test
    public void disabledRejectsEditingTool() {
        ModelContextProtocolToolCallAuthorizer uut = authorizer(
            ToolAvailabilityLevel.DISABLED,
            false,
            mock(AiCodeOperationAuthorizer.class),
            mock(MapTargetToolCallAuthorizer.class));

        assertThatThrownBy(() -> uut.assertAuthorized("createNodes", null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("The requested tool is not available at the current availability level.");
    }

    @Test
    public void codeToolsReadNestedHostValue() throws Exception {
        AiCodeOperationAuthorizer aiCodeOperationAuthorizer = mock(AiCodeOperationAuthorizer.class);
        ModelContextProtocolToolCallAuthorizer uut = authorizer(
            ToolAvailabilityLevel.SCRIPT_EXECUTION,
            false,
            aiCodeOperationAuthorizer,
            mock(MapTargetToolCallAuthorizer.class));

        uut.assertAuthorized("writeCode", objectMapper.readTree("{\"request\":{\"host\":\"AI\"}}"));

        verify(aiCodeOperationAuthorizer).assertAuthorized(eq("writeCode"), eq(org.freeplane.features.ai.code.ScriptHost.AI));
    }

    @Test
    public void formulaToolsRequireFormulaEditingPermission() {
        ModelContextProtocolToolCallAuthorizer uut = authorizer(
            ToolAvailabilityLevel.EDITING,
            false,
            mock(AiCodeOperationAuthorizer.class),
            mock(MapTargetToolCallAuthorizer.class));

        assertThatThrownBy(() -> uut.assertAuthorized("previewFormulaUpdates", objectMapper.createObjectNode()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Formula authoring is not available at the current availability level or formula-editing permission.");
    }

    @Test
    public void formulaToolsAreAllowedAtEditingWhenFormulaEditingIsEnabled() {
        ModelContextProtocolToolCallAuthorizer uut = authorizer(
            ToolAvailabilityLevel.EDITING,
            true,
            mock(AiCodeOperationAuthorizer.class),
            mock(MapTargetToolCallAuthorizer.class));

        assertThatCode(() -> uut.assertAuthorized("previewFormulaUpdates", objectMapper.createObjectNode()))
            .doesNotThrowAnyException();
    }

    private ModelContextProtocolToolCallAuthorizer authorizer(ToolAvailabilityLevel toolAvailability,
                                                              boolean formulaEditingEnabled,
                                                              AiCodeOperationAuthorizer aiCodeOperationAuthorizer,
                                                              MapTargetToolCallAuthorizer mapTargetToolCallAuthorizer) {
        return new ModelContextProtocolToolCallAuthorizer(
            availability(toolAvailability),
            () -> Boolean.valueOf(formulaEditingEnabled),
            aiCodeOperationAuthorizer,
            mapTargetToolCallAuthorizer);
    }

    private Supplier<ToolAvailabilityLevel> availability(ToolAvailabilityLevel toolAvailability) {
        return () -> toolAvailability;
    }
}
