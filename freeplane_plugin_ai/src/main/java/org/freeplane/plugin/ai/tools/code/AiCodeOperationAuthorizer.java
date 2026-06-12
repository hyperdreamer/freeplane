package org.freeplane.plugin.ai.tools.code;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;
import org.freeplane.features.ai.code.AiCodeHostService;
import org.freeplane.features.ai.code.CodeState;
import org.freeplane.features.ai.code.ReadCodeRequest;
import org.freeplane.features.ai.code.ReadCodeResponse;
import org.freeplane.features.ai.code.ScriptHost;
import org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevel;
import org.freeplane.plugin.ai.tools.formula.FormulaEditingAccess;
import org.freeplane.plugin.ai.tools.utilities.ToolCaller;

public class AiCodeOperationAuthorizer {
    private static final String SCRIPT_CONTENT_TYPE = "text/x-freeplane-script-groovy";
    private static final String FORMULA_CONTENT_TYPE = "text/x-freeplane-formula-groovy";

    @SuppressWarnings("unused")
    private final ToolCaller toolCaller;
    private final Supplier<ToolAvailabilityLevel> globalAvailabilitySupplier;
    private final Supplier<ToolAvailabilityLevel> sessionOverrideSupplier;
    private final Supplier<Boolean> formulaEditingEnabledSupplier;
    private final AiCodeHostService codeHostService;

    public AiCodeOperationAuthorizer(ToolCaller toolCaller,
                                     Supplier<ToolAvailabilityLevel> globalAvailabilitySupplier,
                                     Supplier<ToolAvailabilityLevel> sessionOverrideSupplier,
                                     Supplier<Boolean> formulaEditingEnabledSupplier,
                                     AiCodeHostService codeHostService) {
        this.toolCaller = toolCaller == null ? ToolCaller.CHAT : toolCaller;
        this.globalAvailabilitySupplier = globalAvailabilitySupplier;
        this.sessionOverrideSupplier = sessionOverrideSupplier;
        this.formulaEditingEnabledSupplier = formulaEditingEnabledSupplier;
        this.codeHostService = codeHostService;
    }

    public Set<String> authorizedToolNames() {
        if (codeHostService == null) {
            return Collections.emptySet();
        }
        LinkedHashSet<String> toolNames = new LinkedHashSet<String>();
        boolean scriptExecutionAvailable = globalAvailability().includesScriptExecution();
        if (canReadAttachedEditor() || canReadAiHost() || scriptExecutionAvailable) {
            toolNames.add("readCode");
        }
        boolean attachedWriteAuthorized = canWriteAttachedEditor();
        if (attachedWriteAuthorized || scriptExecutionAvailable) {
            toolNames.add("writeCode");
            toolNames.add("compileCode");
        }
        if (scriptExecutionAvailable) {
            toolNames.add("runCode");
        }
        return Collections.unmodifiableSet(toolNames);
    }

    public void assertAuthorized(String operation, ScriptHost host) {
        ScriptHost resolvedHost = requireHost(host);
        if ("readCode".equals(operation)) {
            if (resolvedHost == ScriptHost.ATTACHED_EDITOR && canReadAttachedEditor()) {
                return;
            }
            if (resolvedHost == ScriptHost.AI && (canReadAiHost() || globalAvailability().includesScriptExecution())) {
                return;
            }
            throw new IllegalStateException("The requested code host is not readable at the current availability level.");
        }
        if ("writeCode".equals(operation) || "compileCode".equals(operation)) {
            if (resolvedHost == ScriptHost.ATTACHED_EDITOR && canWriteAttachedEditor()) {
                return;
            }
            if (resolvedHost == ScriptHost.AI && globalAvailability().includesScriptExecution()) {
                return;
            }
            throw new IllegalStateException("The requested code host is not writable at the current availability level.");
        }
        if ("runCode".equals(operation)) {
            if (!globalAvailability().includesScriptExecution()) {
                throw new IllegalStateException("Script execution is not available at the current availability level.");
            }
            ReadCodeResponse state = currentState(resolvedHost);
            if (state == null || state.getCodeState() == CodeState.NO_CODE) {
                throw new IllegalStateException("No runnable code is available for the requested host.");
            }
            if (!SCRIPT_CONTENT_TYPE.equals(state.getContentType())) {
                throw new IllegalStateException("Only script content is runnable.");
            }
            return;
        }
        throw new IllegalArgumentException("Unknown code operation: " + operation);
    }

    private ToolAvailabilityLevel globalAvailability() {
        ToolAvailabilityLevel level = globalAvailabilitySupplier == null ? null : globalAvailabilitySupplier.get();
        return level == null ? ToolAvailabilityLevel.EDITING : level;
    }

    private boolean hasSessionOverride() {
        return sessionOverrideSupplier != null && sessionOverrideSupplier.get() != null;
    }

    private boolean canReadAttachedEditor() {
        if (!hasCurrentCode(ScriptHost.ATTACHED_EDITOR)) {
            return false;
        }
        return hasSessionOverride() || globalAvailability().includesTools();
    }

    public ToolAvailabilityLevel currentToolAvailability() {
        ToolAvailabilityLevel sessionOverride = sessionOverrideSupplier == null ? null : sessionOverrideSupplier.get();
        return sessionOverride == null ? globalAvailability() : sessionOverride;
    }

    private boolean canWriteAttachedEditor() {
        ReadCodeResponse state = currentState(ScriptHost.ATTACHED_EDITOR);
        if (state == null || state.getCodeState() == CodeState.NO_CODE) {
            return false;
        }
        if (FORMULA_CONTENT_TYPE.equals(state.getContentType())) {
            return FormulaEditingAccess.isFormulaEditingAllowed(
                currentToolAvailability(),
                formulaEditingEnabledSupplier != null && formulaEditingEnabledSupplier.get().booleanValue());
        }
        return hasSessionOverride() || globalAvailability().includesEditing();
    }

    private boolean canReadAiHost() {
        return hasCurrentCode(ScriptHost.AI);
    }

    private boolean hasCurrentCode(ScriptHost host) {
        ReadCodeResponse state = currentState(host);
        return state != null && state.getCodeState() != CodeState.NO_CODE;
    }

    private ReadCodeResponse currentState(ScriptHost host) {
        try {
            return codeHostService.readCode(new ReadCodeRequest(host));
        } catch (RuntimeException error) {
            return null;
        }
    }

    private ScriptHost requireHost(ScriptHost host) {
        if (host == null) {
            throw new IllegalArgumentException("host is required.");
        }
        return host;
    }
}
