package org.freeplane.plugin.ai.mcpserver;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import org.freeplane.features.ai.code.ScriptHost;
import org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevel;
import org.freeplane.plugin.ai.tools.code.AiCodeOperationAuthorizer;
import org.freeplane.plugin.ai.tools.formula.FormulaEditingAccess;
import org.freeplane.plugin.ai.tools.documentation.GetApiDocumentationResponse;
import org.freeplane.plugin.ai.tools.documentation.GetApiDocumentationTool;

public class ModelContextProtocolToolCallAuthorizer {
    private static final Set<String> CODE_TOOL_NAMES = Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(
        "readCode",
        "writeCode",
        "compileCode",
        "runScript")));
    private static final Set<String> DISABLED_API_MAP_READ_TOOL_NAMES = Collections.unmodifiableSet(
        new LinkedHashSet<String>(Arrays.asList(
            "readNodesWithDescendants",
            "readNodesWithDescendantsAsPlainText",
            "searchNodes")));

    private final Supplier<ToolAvailabilityLevel> toolAvailabilitySupplier;
    private final Supplier<Boolean> formulaEditingEnabledSupplier;
    private final AiCodeOperationAuthorizer aiCodeOperationAuthorizer;
    private final GetApiDocumentationTool getApiDocumentationTool;

    public ModelContextProtocolToolCallAuthorizer(Supplier<ToolAvailabilityLevel> toolAvailabilitySupplier,
                                                  Supplier<Boolean> formulaEditingEnabledSupplier,
                                                  AiCodeOperationAuthorizer aiCodeOperationAuthorizer,
                                                  GetApiDocumentationTool getApiDocumentationTool) {
        this.toolAvailabilitySupplier = Objects.requireNonNull(toolAvailabilitySupplier, "toolAvailabilitySupplier");
        this.formulaEditingEnabledSupplier = Objects.requireNonNull(
            formulaEditingEnabledSupplier,
            "formulaEditingEnabledSupplier");
        this.aiCodeOperationAuthorizer = Objects.requireNonNull(aiCodeOperationAuthorizer,
            "aiCodeOperationAuthorizer");
        this.getApiDocumentationTool = Objects.requireNonNull(getApiDocumentationTool, "getApiDocumentationTool");
    }

    public void assertAuthorized(String toolName, JsonNode argumentsNode) {
        String normalizedToolName = normalizeToolName(toolName);
        if (CODE_TOOL_NAMES.contains(normalizedToolName)) {
            aiCodeOperationAuthorizer.assertAuthorized(
                normalizedToolName,
                textValue(argumentsNode, "codeId"),
                hostValue(argumentsNode));
            return;
        }
        ToolAvailabilityLevel toolAvailability = currentToolAvailability();
        if (toolAvailability == ToolAvailabilityLevel.DISABLED) {
            assertDisabledAuthorized(normalizedToolName, argumentsNode);
            return;
        }
        if (FormulaEditingAccess.isFormulaTool(normalizedToolName)) {
            if (!FormulaEditingAccess.isFormulaEditingAllowed(
                toolAvailability,
                formulaEditingEnabledSupplier.get().booleanValue())) {
                throw new IllegalStateException("Formula authoring is not available at the current availability level or formula-editing permission.");
            }
            return;
        }
        if (!toolAvailability.allowsTool(normalizedToolName)) {
            throw new IllegalStateException("The requested tool is not available at the current availability level.");
        }
    }

    private ToolAvailabilityLevel currentToolAvailability() {
        ToolAvailabilityLevel toolAvailability = toolAvailabilitySupplier.get();
        return toolAvailability == null ? ToolAvailabilityLevel.EDITING : toolAvailability;
    }

    private void assertDisabledAuthorized(String toolName, JsonNode argumentsNode) {
        if ("getApiDocumentation".equals(toolName)) {
            return;
        }
        if (DISABLED_API_MAP_READ_TOOL_NAMES.contains(toolName) && targetsInternalApiMap(argumentsNode)) {
            return;
        }
        throw new IllegalStateException("The requested tool is not available at the current availability level.");
    }

    private boolean targetsInternalApiMap(JsonNode argumentsNode) {
        String requestMapIdentifier = textValue(argumentsNode, "mapIdentifier");
        if (requestMapIdentifier == null) {
            return false;
        }
        return requestMapIdentifier.equals(internalApiMapIdentifier());
    }

    private String internalApiMapIdentifier() {
        GetApiDocumentationResponse response = getApiDocumentationTool.getApiDocumentation();
        if (response == null || response.getMapIdentifier() == null || response.getMapIdentifier().trim().isEmpty()) {
            throw new IllegalStateException("The internal API map identifier is not available.");
        }
        return response.getMapIdentifier().trim();
    }

    private ScriptHost hostValue(JsonNode argumentsNode) {
        String hostText = textValue(argumentsNode, "host");
        if (hostText == null) {
            return null;
        }
        try {
            return ScriptHost.valueOf(hostText);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid host: " + hostText, error);
        }
    }

    private String textValue(JsonNode argumentsNode, String fieldName) {
        JsonNode effectiveArgumentsNode = effectiveArgumentsNode(argumentsNode);
        if (effectiveArgumentsNode == null || effectiveArgumentsNode.isNull()) {
            return null;
        }
        JsonNode valueNode = effectiveArgumentsNode.get(fieldName);
        if (valueNode == null || valueNode.isNull()) {
            return null;
        }
        String value = valueNode.asText();
        if (value == null) {
            return null;
        }
        String trimmedValue = value.trim();
        return trimmedValue.isEmpty() ? null : trimmedValue;
    }

    private JsonNode effectiveArgumentsNode(JsonNode argumentsNode) {
        if (argumentsNode == null || argumentsNode.isNull()) {
            return argumentsNode;
        }
        JsonNode requestNode = argumentsNode.get("request");
        if (requestNode != null && requestNode.isObject()) {
            return requestNode;
        }
        return argumentsNode;
    }

    private String normalizeToolName(String toolName) {
        if (toolName == null || toolName.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing tool name");
        }
        return toolName.trim();
    }
}
