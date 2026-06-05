package org.freeplane.plugin.ai.tools.code;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;
import org.freeplane.features.ai.code.AiCodeHostService;
import org.freeplane.features.ai.code.CodeLifecycleStatus;
import org.freeplane.features.ai.code.ReadCodeRequest;
import org.freeplane.features.ai.code.ReadCodeResponse;
import org.freeplane.features.ai.code.ScriptHost;
import org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevel;
import org.freeplane.plugin.ai.tools.utilities.ToolCaller;

public class AiCodeOperationAuthorizer {
    private static final String ATTACHED_EDITOR_CODE_ID_PREFIX = "attached-editor-";
    private static final String AI_SCRIPT_CODE_ID_PREFIX = "ai-script-";
    private static final String SCRIPT_CONTENT_TYPE = "text/x-freeplane-script-groovy";

    private final ToolCaller toolCaller;
    private final Supplier<ToolAvailabilityLevel> globalAvailabilitySupplier;
    private final Supplier<ToolAvailabilityLevel> sessionOverrideSupplier;
    private final AiCodeHostService codeHostService;

    public AiCodeOperationAuthorizer(ToolCaller toolCaller,
                                     Supplier<ToolAvailabilityLevel> globalAvailabilitySupplier,
                                     Supplier<ToolAvailabilityLevel> sessionOverrideSupplier,
                                     AiCodeHostService codeHostService) {
        this.toolCaller = toolCaller == null ? ToolCaller.CHAT : toolCaller;
        this.globalAvailabilitySupplier = globalAvailabilitySupplier;
        this.sessionOverrideSupplier = sessionOverrideSupplier;
        this.codeHostService = codeHostService;
    }

    public Set<String> authorizedToolNames() {
        if (codeHostService == null) {
            return Collections.emptySet();
        }
        LinkedHashSet<String> toolNames = new LinkedHashSet<String>();
        if (canReadAttachedEditor() || canReadAiHost()) {
            toolNames.add("readCode");
        }
        if (canWriteOrCompileAttachedEditor() || globalAvailability().includesScriptExecution()) {
            toolNames.add("writeCode");
            if (hasCurrentCode(ScriptHost.ATTACHED_EDITOR) || hasCurrentCode(ScriptHost.AI)) {
                toolNames.add("compileCode");
            }
        }
        if (canRunCurrentScript()) {
            toolNames.add("runScript");
        }
        return Collections.unmodifiableSet(toolNames);
    }

    public void assertAuthorized(String operation, String codeId, ScriptHost host) {
        ScriptHost resolvedHost = resolveHost(codeId, host);
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
            if (resolvedHost == ScriptHost.ATTACHED_EDITOR && canWriteOrCompileAttachedEditor()) {
                return;
            }
            if (resolvedHost == ScriptHost.AI && globalAvailability().includesScriptExecution()) {
                return;
            }
            throw new IllegalStateException("The requested code host is not writable at the current availability level.");
        }
        if ("runScript".equals(operation)) {
            if (!globalAvailability().includesScriptExecution()) {
                throw new IllegalStateException("Script execution is not available at the current availability level.");
            }
            ReadCodeResponse state = currentState(resolvedHost);
            if (state == null || state.getStatus() == CodeLifecycleStatus.NO_CODE) {
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

    private boolean canWriteOrCompileAttachedEditor() {
        if (!hasCurrentCode(ScriptHost.ATTACHED_EDITOR)) {
            return false;
        }
        return hasSessionOverride() || globalAvailability().includesEditing();
    }

    private boolean canReadAiHost() {
        return hasCurrentCode(ScriptHost.AI);
    }

    private boolean canRunCurrentScript() {
        if (!globalAvailability().includesScriptExecution()) {
            return false;
        }
        ReadCodeResponse attachedState = currentState(ScriptHost.ATTACHED_EDITOR);
        if (attachedState != null
            && attachedState.getStatus() != CodeLifecycleStatus.NO_CODE
            && SCRIPT_CONTENT_TYPE.equals(attachedState.getContentType())) {
            return true;
        }
        ReadCodeResponse aiState = currentState(ScriptHost.AI);
        return aiState != null
            && aiState.getStatus() != CodeLifecycleStatus.NO_CODE
            && SCRIPT_CONTENT_TYPE.equals(aiState.getContentType());
    }

    private boolean hasCurrentCode(ScriptHost host) {
        ReadCodeResponse state = currentState(host);
        return state != null && state.getStatus() != CodeLifecycleStatus.NO_CODE;
    }

    private ReadCodeResponse currentState(ScriptHost host) {
        try {
            return codeHostService.readCode(new ReadCodeRequest(null, host, null));
        } catch (RuntimeException error) {
            return null;
        }
    }

    private ScriptHost resolveHost(String codeId, ScriptHost host) {
        if (codeId != null && !codeId.trim().isEmpty()) {
            String normalizedCodeId = codeId.trim();
            if (normalizedCodeId.startsWith(ATTACHED_EDITOR_CODE_ID_PREFIX)) {
                return ScriptHost.ATTACHED_EDITOR;
            }
            if (normalizedCodeId.startsWith(AI_SCRIPT_CODE_ID_PREFIX)) {
                return ScriptHost.AI;
            }
            throw new IllegalArgumentException("Unknown codeId: " + normalizedCodeId);
        }
        if (host == null) {
            throw new IllegalArgumentException("host is required when codeId is absent.");
        }
        return host;
    }
}
