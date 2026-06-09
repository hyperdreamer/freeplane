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
        ScriptHost host = requireHost(request == null ? null : request.getHost());
        if (host == ScriptHost.ATTACHED_EDITOR) {
            return attachedEditorCodeHostService.readCode(request);
        }
        AiCodeHostService aiHostService = currentAiCodeHostService();
        if (aiHostService == null) {
            return new ReadCodeResponse(
                ScriptHost.AI,
                AI_SCRIPT_CONTENT_TYPE,
                CodeLifecycleStatus.NO_CODE,
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
        ScriptHost host = requireHost(request == null ? null : request.getHost());
        if (host == ScriptHost.ATTACHED_EDITOR) {
            return attachedEditorCodeHostService.writeCode(request);
        }
        return requireAiCodeHostService().writeCode(request);
    }

    @Override
    public synchronized CompileCodeResponse compileCode(CompileCodeRequest request) {
        ScriptHost host = requireHost(request == null ? null : request.getHost());
        if (host == ScriptHost.ATTACHED_EDITOR) {
            return attachedEditorCodeHostService.compileCode(request);
        }
        return requireAiCodeHostService().compileCode(request);
    }

    @Override
    public synchronized RunCodeResponse runCode(RunCodeRequest request) {
        ScriptHost host = requireHost(request == null ? null : request.getHost());
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

    private ScriptHost requireHost(ScriptHost host) {
        if (host == null) {
            throw new IllegalArgumentException("host is required.");
        }
        return host;
    }

    private AiCodeHostService requireAiCodeHostService() {
        AiCodeHostService aiHostService = currentAiCodeHostService();
        if (aiHostService == null) {
            throw new IllegalStateException("AI code host is not available.");
        }
        return aiHostService;
    }

    private AiCodeHostService currentAiCodeHostService() {
        AiCodeHostService aiHostService = aiCodeHostServiceSupplier == null ? null : aiCodeHostServiceSupplier.get();
        if (aiHostService == null) {
            return null;
        }
        syncRunListeners(aiHostService);
        return aiHostService;
    }

    private void syncRunListeners(AiCodeHostService aiHostService) {
        if (aiHostService == null) {
            return;
        }
        for (AiCodeRunListener listener : runListeners) {
            aiHostService.addRunListener(listener);
        }
    }

    public synchronized boolean showCurrentAiOwnedCode() {
        AiCodeHostService aiHostService = currentAiCodeHostService();
        if (aiHostService == null) {
            return false;
        }
        ReadCodeResponse response = aiHostService.readCode(new ReadCodeRequest(ScriptHost.AI, null));
        if (response == null || response.getStatus() == CodeLifecycleStatus.NO_CODE) {
            return false;
        }
        try {
            Method method = aiHostService.getClass().getMethod("showCurrentCode");
            method.invoke(aiHostService);
            return true;
        } catch (Exception error) {
            return false;
        }
    }
}
