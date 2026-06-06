package org.freeplane.plugin.ai.code;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;
import org.freeplane.features.ai.code.AiChatCodeOperationResult;
import org.freeplane.features.ai.code.AiCodeHostService;
import org.freeplane.features.ai.code.AiCodeRunListener;
import org.freeplane.features.ai.code.CodeLifecycleStatus;
import org.freeplane.features.ai.code.CompileCodeRequest;
import org.freeplane.features.ai.code.CompileCodeResponse;
import org.freeplane.features.ai.code.EvaluateFormulaRequest;
import org.freeplane.features.ai.code.ReadCodeRequest;
import org.freeplane.features.ai.code.ReadCodeResponse;
import org.freeplane.features.ai.code.RunCodeRequest;
import org.freeplane.features.ai.code.RunCodeResponse;
import org.freeplane.features.ai.code.ScriptHost;
import org.freeplane.features.ai.code.WriteCodeRequest;
import org.freeplane.features.ai.code.WriteCodeResponse;

public class RoutingAiCodeHostService implements AiCodeHostService {
    private static final String AI_SCRIPT_CODE_ID_PREFIX = "ai-script-";
    private static final String AI_SCRIPT_CONTENT_TYPE = "text/x-freeplane-script-groovy";

    private final AiCodeHostService attachedEditorCodeHostService;
    private final Supplier<AiCodeHostService> aiCodeHostServiceSupplier;
    private final Set<AiCodeRunListener> runListeners = new LinkedHashSet<AiCodeRunListener>();

    public RoutingAiCodeHostService(AiCodeHostService attachedEditorCodeHostService,
                                    Supplier<AiCodeHostService> aiCodeHostServiceSupplier) {
        this.attachedEditorCodeHostService = attachedEditorCodeHostService;
        this.aiCodeHostServiceSupplier = aiCodeHostServiceSupplier;
    }

    @Override
    public synchronized ReadCodeResponse readCode(ReadCodeRequest request) {
        ScriptHost host = resolveHost(request == null ? null : request.getCodeId(), request == null ? null : request.getHost());
        if (host == ScriptHost.ATTACHED_EDITOR) {
            return attachedEditorCodeHostService.readCode(request);
        }
        AiCodeHostService aiHostService = currentAiCodeHostService();
        if (aiHostService == null) {
            return new ReadCodeResponse(
                request == null ? null : request.getCodeId(),
                ScriptHost.AI,
                AI_SCRIPT_CONTENT_TYPE,
                CodeLifecycleStatus.NO_CODE,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        }
        return aiHostService.readCode(request);
    }

    @Override
    public synchronized WriteCodeResponse writeCode(WriteCodeRequest request) {
        ScriptHost host = resolveHost(request == null ? null : request.getCodeId(), request == null ? null : request.getHost());
        if (host == ScriptHost.ATTACHED_EDITOR) {
            return attachedEditorCodeHostService.writeCode(request);
        }
        return requireAiCodeHostService().writeCode(request);
    }

    @Override
    public synchronized CompileCodeResponse compileCode(CompileCodeRequest request) {
        ScriptHost host = resolveHost(request == null ? null : request.getCodeId(), request == null ? null : request.getHost());
        if (host == ScriptHost.ATTACHED_EDITOR) {
            return attachedEditorCodeHostService.compileCode(request);
        }
        return requireAiCodeHostService().compileCode(request);
    }

    @Override
    public synchronized RunCodeResponse runCode(RunCodeRequest request) {
        ScriptHost host = resolveHost(request == null ? null : request.getCodeId(), request == null ? null : request.getHost());
        if (host == ScriptHost.ATTACHED_EDITOR) {
            return attachedEditorCodeHostService.runCode(request);
        }
        return requireAiCodeHostService().runCode(request);
    }

    @Override
    public synchronized AiChatCodeOperationResult evaluateFormula(EvaluateFormulaRequest request) {
        return requireAiCodeHostService().evaluateFormula(request);
    }

    @Override
    public synchronized void addRunListener(AiCodeRunListener listener) {
        if (listener == null || !runListeners.add(listener)) {
            return;
        }
        attachedEditorCodeHostService.addRunListener(listener);
        currentAiCodeHostService();
    }

    @Override
    public synchronized void removeRunListener(AiCodeRunListener listener) {
        if (listener == null || !runListeners.remove(listener)) {
            return;
        }
        attachedEditorCodeHostService.removeRunListener(listener);
        AiCodeHostService aiHostService = currentAiCodeHostService();
        if (aiHostService != null) {
            aiHostService.removeRunListener(listener);
        }
    }

    private ScriptHost resolveHost(String codeId, ScriptHost host) {
        if (codeId != null && !codeId.trim().isEmpty()) {
            return codeId.trim().startsWith(AI_SCRIPT_CODE_ID_PREFIX)
                ? ScriptHost.AI
                : ScriptHost.ATTACHED_EDITOR;
        }
        if (host == null) {
            throw new IllegalArgumentException("host is required when codeId is absent.");
        }
        return host;
    }

    private AiCodeHostService requireAiCodeHostService() {
        AiCodeHostService aiHostService = currentAiCodeHostService();
        if (aiHostService == null) {
            throw new IllegalStateException("AI-owned script host is unavailable.");
        }
        return aiHostService;
    }

    public synchronized boolean showCurrentAiOwnedCode() {
        AiCodeHostService aiHostService = currentAiCodeHostService();
        if (aiHostService == null) {
            return false;
        }
        ReadCodeResponse response = aiHostService.readCode(new ReadCodeRequest(null, ScriptHost.AI, null));
        if (response == null || response.getStatus() == CodeLifecycleStatus.NO_CODE || response.getCodeId() == null) {
            return false;
        }
        try {
            Method method = aiHostService.getClass().getMethod("showCode", String.class);
            method.invoke(aiHostService, response.getCodeId());
            return true;
        } catch (Exception error) {
            return false;
        }
    }

    private AiCodeHostService currentAiCodeHostService() {
        AiCodeHostService aiHostService = aiCodeHostServiceSupplier == null ? null : aiCodeHostServiceSupplier.get();
        if (aiHostService == null) {
            return null;
        }
        for (AiCodeRunListener listener : runListeners) {
            aiHostService.addRunListener(listener);
        }
        return aiHostService;
    }
}
