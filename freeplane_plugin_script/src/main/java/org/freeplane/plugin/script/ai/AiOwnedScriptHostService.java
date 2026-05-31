package org.freeplane.plugin.script.ai;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.features.ai.code.AiCodeHostService;
import org.freeplane.features.ai.code.AiCodeRunListener;
import org.freeplane.features.ai.code.CodeLifecycleStatus;
import org.freeplane.features.ai.code.CompileCodeRequest;
import org.freeplane.features.ai.code.CompileCodeResponse;
import org.freeplane.features.ai.code.ReadCodeRequest;
import org.freeplane.features.ai.code.ReadCodeResponse;
import org.freeplane.features.ai.code.RunScriptRequest;
import org.freeplane.features.ai.code.RunScriptResponse;
import org.freeplane.features.ai.code.ScriptHost;
import org.freeplane.features.ai.code.ScriptRunInitiator;
import org.freeplane.features.ai.code.WriteCodeRequest;
import org.freeplane.features.ai.code.WriteCodeResponse;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.plugin.script.ExecuteScriptException;
import org.freeplane.plugin.script.IFreeplaneScriptErrorHandler;
import org.freeplane.plugin.script.ScriptingEngine;
import org.freeplane.plugin.script.ScriptingPermissions;

public class AiOwnedScriptHostService implements AiCodeHostService {
    public static final String HOST_REGISTRATION_PROPERTY = "scriptHost";
    public static final String AI_HOST_REGISTRATION_VALUE = "AI";
    public static final String AI_SCRIPT_CONTENT_TYPE = "text/x-freeplane-script-groovy";
    public static final String AI_SCRIPT_WITHOUT_FILE_RESTRICTION = "ai_script_without_file_restriction";
    public static final String AI_SCRIPT_WITHOUT_WRITE_RESTRICTION = "ai_script_without_write_restriction";
    public static final String AI_SCRIPT_WITHOUT_NETWORK_RESTRICTION = "ai_script_without_network_restriction";
    public static final String AI_SCRIPT_WITHOUT_EXEC_RESTRICTION = "ai_script_without_exec_restriction";

    private static final String AI_SCRIPT_CODE_ID_PREFIX = "ai-script-";

    private final ResourceController resourceController;
    private final Set<AiCodeRunListener> runListeners = Collections.newSetFromMap(
        new IdentityHashMap<AiCodeRunListener, Boolean>());
    private final Map<String, ReadCodeResponse> archivedStates = new HashMap<String, ReadCodeResponse>();
    private long nextCodeId = 1L;
    private CurrentScript currentScript;

    public AiOwnedScriptHostService() {
        this(Controller.getCurrentController() == null ? null : Controller.getCurrentController().getResourceController());
    }

    AiOwnedScriptHostService(ResourceController resourceController) {
        this.resourceController = resourceController;
    }

    @Override
    public synchronized ReadCodeResponse readCode(ReadCodeRequest request) {
        String codeId = normalized(request == null ? null : request.getCodeId());
        if (codeId != null) {
            if (currentScript != null && currentScript.codeId.equals(codeId)) {
                return currentReadCodeResponse(request == null ? null : request.getFingerprint());
            }
            ReadCodeResponse archivedState = archivedStates.get(codeId);
            if (archivedState != null) {
                return archivedState;
            }
            if (codeId.startsWith(AI_SCRIPT_CODE_ID_PREFIX)) {
                return noCodeState(codeId);
            }
            throw new IllegalArgumentException("Unknown codeId: " + codeId);
        }
        ScriptHost host = request == null ? null : request.getHost();
        if (host != ScriptHost.AI) {
            throw new IllegalArgumentException("host AI is required when codeId is absent.");
        }
        if (currentScript == null) {
            return noCodeState(null);
        }
        return currentReadCodeResponse(request == null ? null : request.getFingerprint());
    }

    @Override
    public synchronized WriteCodeResponse writeCode(WriteCodeRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required.");
        }
        if (request.getHost() != null && request.getHost() != ScriptHost.AI) {
            throw new IllegalArgumentException("host AI is required for AI-owned scripts.");
        }
        if (request.getText() == null) {
            throw new IllegalArgumentException("text is required.");
        }
        assertExpectedFingerprint(request.getExpectedFingerprint());
        String newCodeId = nextCodeId();
        if (currentScript != null) {
            archivedStates.put(currentScript.codeId, replacedState(currentScript, newCodeId));
        }
        currentScript = new CurrentScript(newCodeId, request.getText());
        return new WriteCodeResponse(
            currentScript.codeId,
            ScriptHost.AI,
            AI_SCRIPT_CONTENT_TYPE,
            CodeLifecycleStatus.READY,
            currentScript.fingerprint);
    }

    @Override
    public synchronized CompileCodeResponse compileCode(CompileCodeRequest request) {
        requireCurrentScript(request == null ? null : request.getCodeId(), request == null ? null : request.getHost());
        assertExpectedFingerprint(request == null ? null : request.getExpectedFingerprint());
        ScriptingEngine.GroovyCompileResult compileResult = ScriptingEngine.compileGroovyScriptForDiagnostics(
            currentScript.codeText,
            aiStartedPermissions(false));
        CompileCodeResponse response = new CompileCodeResponse(
            currentScript.codeId,
            ScriptHost.AI,
            AI_SCRIPT_CONTENT_TYPE,
            compileResult.isSuccessful() ? CodeLifecycleStatus.READY : CodeLifecycleStatus.FAILED,
            currentScript.fingerprint,
            compileResult.getCompilerDiagnostics(),
            compileResult.getErrorMessage(),
            compileResult.getLineNumber());
        currentScript.latestState = compileResult.isSuccessful()
            ? readyState(currentScript)
            : failedState(currentScript, response.getCompilerDiagnostics(), response.getErrorMessage(),
                response.getLineNumber(), null, null, null);
        return response;
    }

    @Override
    public synchronized RunScriptResponse runScript(RunScriptRequest request) {
        requireCurrentScript(request == null ? null : request.getCodeId(), request == null ? null : request.getHost());
        assertExpectedFingerprint(request == null ? null : request.getExpectedFingerprint());
        ScriptingPermissions permissions = aiStartedPermissions(false);
        ScriptingEngine.GroovyCompileResult compileResult = ScriptingEngine.compileGroovyScriptForDiagnostics(
            currentScript.codeText,
            permissions);
        if (!compileResult.isSuccessful()) {
            RunScriptResponse response = new RunScriptResponse(
                currentScript.codeId,
                ScriptHost.AI,
                AI_SCRIPT_CONTENT_TYPE,
                CodeLifecycleStatus.FAILED,
                ScriptRunInitiator.AI,
                currentScript.fingerprint,
                compileResult.getCompilerDiagnostics(),
                compileResult.getErrorMessage(),
                compileResult.getLineNumber(),
                null,
                null);
            currentScript.latestState = failedState(currentScript, response.getCompilerDiagnostics(), response.getErrorMessage(),
                response.getLineNumber(), null, null, ScriptRunInitiator.AI);
            fireRunFinished(response);
            return response;
        }
        NodeModel selectedNode = currentSelectedNode();
        if (selectedNode == null) {
            throw new IllegalStateException("No node is currently selected.");
        }
        ByteArrayOutputStream outputBuffer = new ByteArrayOutputStream();
        final int[] lineNumber = new int[] { -1 };
        try (PrintStream outStream = new PrintStream(outputBuffer, false, "UTF-8")) {
            Object result = ScriptingEngine.executeScript(
                selectedNode,
                currentScript.codeText,
                new IFreeplaneScriptErrorHandler() {
                    @Override
                    public void gotoLine(int pLineNumber) {
                        lineNumber[0] = pLineNumber;
                    }
                },
                outStream,
                null,
                permissions);
            String stdout = trimStdout(outputBuffer);
            Object structuredResult = toJsonSafeValue(result);
            RunScriptResponse response = new RunScriptResponse(
                currentScript.codeId,
                ScriptHost.AI,
                AI_SCRIPT_CONTENT_TYPE,
                CodeLifecycleStatus.SUCCEEDED,
                ScriptRunInitiator.AI,
                currentScript.fingerprint,
                null,
                null,
                null,
                stdout,
                structuredResult);
            currentScript.latestState = new ReadCodeResponse(
                currentScript.codeId,
                ScriptHost.AI,
                AI_SCRIPT_CONTENT_TYPE,
                CodeLifecycleStatus.SUCCEEDED,
                ScriptRunInitiator.AI,
                currentScript.fingerprint,
                currentScript.codeText,
                null,
                null,
                null,
                null,
                stdout,
                structuredResult);
            fireRunFinished(response);
            return response;
        } catch (ExecuteScriptException error) {
            String stdout = trimStdout(outputBuffer);
            Integer errorLine = lineNumber[0] >= 0 ? Integer.valueOf(lineNumber[0]) : null;
            RunScriptResponse response = new RunScriptResponse(
                currentScript.codeId,
                ScriptHost.AI,
                AI_SCRIPT_CONTENT_TYPE,
                CodeLifecycleStatus.FAILED,
                ScriptRunInitiator.AI,
                currentScript.fingerprint,
                null,
                error.getMessage(),
                errorLine,
                stdout,
                null);
            currentScript.latestState = failedState(currentScript, null, error.getMessage(), errorLine, stdout, null,
                ScriptRunInitiator.AI);
            fireRunFinished(response);
            return response;
        } catch (RuntimeException error) {
            String stdout = trimStdout(outputBuffer);
            Integer errorLine = lineNumber[0] >= 0 ? Integer.valueOf(lineNumber[0]) : null;
            RunScriptResponse response = new RunScriptResponse(
                currentScript.codeId,
                ScriptHost.AI,
                AI_SCRIPT_CONTENT_TYPE,
                CodeLifecycleStatus.FAILED,
                ScriptRunInitiator.AI,
                currentScript.fingerprint,
                null,
                error.getMessage(),
                errorLine,
                stdout,
                null);
            currentScript.latestState = failedState(currentScript, null, error.getMessage(), errorLine, stdout, null,
                ScriptRunInitiator.AI);
            fireRunFinished(response);
            return response;
        } catch (Exception error) {
            throw new IllegalStateException(error.getMessage(), error);
        }
    }

    @Override
    public synchronized void addRunListener(AiCodeRunListener listener) {
        if (listener != null) {
            runListeners.add(listener);
        }
    }

    @Override
    public synchronized void removeRunListener(AiCodeRunListener listener) {
        if (listener != null) {
            runListeners.remove(listener);
        }
    }

    private void requireCurrentScript(String codeId, ScriptHost host) {
        String normalizedCodeId = normalized(codeId);
        if (normalizedCodeId != null) {
            if (currentScript == null || !currentScript.codeId.equals(normalizedCodeId)) {
                throw new IllegalStateException("The requested code is not current.");
            }
            return;
        }
        if (host != ScriptHost.AI) {
            throw new IllegalArgumentException("host AI is required when codeId is absent.");
        }
        if (currentScript == null) {
            throw new IllegalStateException("No AI-owned script exists.");
        }
    }

    private void assertExpectedFingerprint(String expectedFingerprint) {
        String normalizedFingerprint = normalized(expectedFingerprint);
        if (normalizedFingerprint == null || currentScript == null) {
            return;
        }
        if (!normalizedFingerprint.equals(currentScript.fingerprint)) {
            throw new IllegalStateException("Expected fingerprint does not match the current code.");
        }
    }

    private ReadCodeResponse currentReadCodeResponse(String requestedFingerprint) {
        String codeText = currentScript.fingerprint.equals(normalized(requestedFingerprint))
            ? null
            : currentScript.codeText;
        ReadCodeResponse state = currentScript.latestState == null ? readyState(currentScript) : currentScript.latestState;
        return new ReadCodeResponse(
            currentScript.codeId,
            ScriptHost.AI,
            AI_SCRIPT_CONTENT_TYPE,
            state.getStatus(),
            state.getRunInitiator(),
            currentScript.fingerprint,
            codeText,
            state.getReplacementCodeId(),
            state.getCompilerDiagnostics(),
            state.getErrorMessage(),
            state.getLineNumber(),
            state.getStdout(),
            state.getStructuredResult());
    }

    private ReadCodeResponse readyState(CurrentScript script) {
        return new ReadCodeResponse(
            script.codeId,
            ScriptHost.AI,
            AI_SCRIPT_CONTENT_TYPE,
            CodeLifecycleStatus.READY,
            null,
            script.fingerprint,
            script.codeText,
            null,
            null,
            null,
            null,
            null,
            null);
    }

    private ReadCodeResponse failedState(CurrentScript script,
                                         List<String> compilerDiagnostics,
                                         String errorMessage,
                                         Integer lineNumber,
                                         String stdout,
                                         Object structuredResult,
                                         ScriptRunInitiator runInitiator) {
        return new ReadCodeResponse(
            script.codeId,
            ScriptHost.AI,
            AI_SCRIPT_CONTENT_TYPE,
            CodeLifecycleStatus.FAILED,
            runInitiator,
            script.fingerprint,
            script.codeText,
            null,
            compilerDiagnostics,
            errorMessage,
            lineNumber,
            stdout,
            structuredResult);
    }

    private ReadCodeResponse noCodeState(String codeId) {
        return new ReadCodeResponse(
            codeId,
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

    private ReadCodeResponse replacedState(CurrentScript script, String replacementCodeId) {
        ReadCodeResponse state = script.latestState == null ? readyState(script) : script.latestState;
        return new ReadCodeResponse(
            script.codeId,
            ScriptHost.AI,
            AI_SCRIPT_CONTENT_TYPE,
            CodeLifecycleStatus.REPLACED,
            state.getRunInitiator(),
            script.fingerprint,
            null,
            replacementCodeId,
            state.getCompilerDiagnostics(),
            state.getErrorMessage(),
            state.getLineNumber(),
            state.getStdout(),
            state.getStructuredResult());
    }

    private String nextCodeId() {
        return AI_SCRIPT_CODE_ID_PREFIX + nextCodeId++;
    }

    private String normalized(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private NodeModel currentSelectedNode() {
        ModeController modeController = Controller.getCurrentModeController();
        if (modeController == null || modeController.getMapController() == null) {
            return null;
        }
        return modeController.getMapController().getSelectedNode();
    }

    private ScriptingPermissions aiStartedPermissions(boolean allowInScriptAiRequests) {
        Map<String, Boolean> permissions = new HashMap<String, Boolean>();
        permissions.put(ScriptingPermissions.RESOURCES_EXECUTE_SCRIPTS_WITHOUT_ASKING, Boolean.TRUE);
        permissions.put(
            ScriptingPermissions.RESOURCES_EXECUTE_SCRIPTS_WITHOUT_READ_RESTRICTION,
            Boolean.valueOf(booleanProperty(AI_SCRIPT_WITHOUT_FILE_RESTRICTION)));
        permissions.put(
            ScriptingPermissions.RESOURCES_EXECUTE_SCRIPTS_WITHOUT_WRITE_RESTRICTION,
            Boolean.valueOf(booleanProperty(AI_SCRIPT_WITHOUT_WRITE_RESTRICTION)));
        permissions.put(
            ScriptingPermissions.RESOURCES_EXECUTE_SCRIPTS_WITHOUT_NETWORK_RESTRICTION,
            Boolean.valueOf(booleanProperty(AI_SCRIPT_WITHOUT_NETWORK_RESTRICTION)));
        permissions.put(
            ScriptingPermissions.RESOURCES_EXECUTE_SCRIPTS_WITHOUT_EXEC_RESTRICTION,
            Boolean.valueOf(booleanProperty(AI_SCRIPT_WITHOUT_EXEC_RESTRICTION)));
        permissions.put(
            ScriptingPermissions.RESOURCES_EXECUTE_SCRIPTS_WITHOUT_AI_REQUEST_RESTRICTION,
            Boolean.valueOf(allowInScriptAiRequests));
        permissions.put(ScriptingPermissions.RESOURCES_SIGNED_SCRIPT_ARE_TRUSTED, Boolean.FALSE);
        return new ScriptingPermissions(permissions);
    }

    private boolean booleanProperty(String key) {
        return resourceController != null && resourceController.getBooleanProperty(key, false);
    }

    private Object toJsonSafeValue(Object value) {
        if (value == null || value instanceof Boolean || value instanceof Number || value instanceof String) {
            return value;
        }
        if (value instanceof Map<?, ?>) {
            Map<?, ?> source = (Map<?, ?>) value;
            Map<String, Object> converted = new LinkedHashMap<String, Object>();
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                if (!(entry.getKey() instanceof String)) {
                    throw unsupportedValue(value);
                }
                converted.put((String) entry.getKey(), toJsonSafeValue(entry.getValue()));
            }
            return converted;
        }
        if (value instanceof Iterable<?>) {
            List<Object> converted = new ArrayList<Object>();
            for (Object item : (Iterable<?>) value) {
                converted.add(toJsonSafeValue(item));
            }
            return converted;
        }
        if (value.getClass().isArray()) {
            List<Object> converted = new ArrayList<Object>();
            int length = java.lang.reflect.Array.getLength(value);
            for (int index = 0; index < length; index++) {
                converted.add(toJsonSafeValue(java.lang.reflect.Array.get(value, index)));
            }
            return converted;
        }
        if (value instanceof Iterator<?>) {
            List<Object> converted = new ArrayList<Object>();
            Iterator<?> iterator = (Iterator<?>) value;
            while (iterator.hasNext()) {
                converted.add(toJsonSafeValue(iterator.next()));
            }
            return converted;
        }
        throw unsupportedValue(value);
    }

    private IllegalStateException unsupportedValue(Object value) {
        return new IllegalStateException("Unsupported script result type: " + value.getClass().getName());
    }

    private String trimStdout(ByteArrayOutputStream outputBuffer) {
        String stdout = new String(outputBuffer.toByteArray(), StandardCharsets.UTF_8);
        return stdout.isEmpty() ? null : stdout;
    }

    private void fireRunFinished(RunScriptResponse response) {
        List<AiCodeRunListener> listeners;
        synchronized (this) {
            listeners = new ArrayList<AiCodeRunListener>(runListeners);
        }
        for (AiCodeRunListener listener : listeners) {
            listener.runFinished(response);
        }
    }

    private String fingerprint(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            Formatter formatter = new Formatter();
            try {
                for (byte value : hash) {
                    formatter.format("%02x", value);
                }
                return formatter.toString();
            } finally {
                formatter.close();
            }
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is not available.", error);
        }
    }

    private final class CurrentScript {
        private final String codeId;
        private final String codeText;
        private final String fingerprint;
        private ReadCodeResponse latestState;

        private CurrentScript(String codeId, String codeText) {
            this.codeId = codeId;
            this.codeText = codeText == null ? "" : codeText;
            this.fingerprint = fingerprint(this.codeText);
        }
    }
}
