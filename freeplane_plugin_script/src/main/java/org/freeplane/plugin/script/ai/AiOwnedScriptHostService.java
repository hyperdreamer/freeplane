package org.freeplane.plugin.script.ai;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Iterator;
import java.util.concurrent.Callable;
import javax.swing.SwingUtilities;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.features.ai.code.AiChatCodeOperationResult;
import org.freeplane.features.ai.code.AiCodeHostService;
import org.freeplane.features.ai.code.AiCodeRunListener;
import org.freeplane.features.ai.code.CodeLifecycleStatus;
import org.freeplane.features.ai.code.CompileCodeRequest;
import org.freeplane.features.ai.code.CompileCodeResponse;
import org.freeplane.features.ai.code.EvaluateFormulaRequest;
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
import org.freeplane.plugin.script.FormulaValidationSupport;
import org.freeplane.plugin.script.IFreeplaneScriptErrorHandler;
import org.freeplane.plugin.script.ScriptingEngine;
import org.freeplane.plugin.script.ScriptingPermissions;

public class AiOwnedScriptHostService implements AiCodeHostService {
    public static final String HOST_REGISTRATION_PROPERTY = "scriptHost";
    public static final String AI_HOST_REGISTRATION_VALUE = "AI";
    public static final String AI_SCRIPT_CONTENT_TYPE = "text/x-freeplane-script-groovy";
    public static final String AI_SCRIPT_EXECUTION_POLICY = "ai_script_execution_policy";
    public static final String AI_SCRIPT_USER_RUN_PERMISSION_MODE = "ai_script_user_run_permission_mode";
    public static final String AI_SCRIPT_WITHOUT_FILE_RESTRICTION = "ai_script_without_file_restriction";
    public static final String AI_SCRIPT_WITHOUT_WRITE_RESTRICTION = "ai_script_without_write_restriction";
    public static final String AI_SCRIPT_WITHOUT_NETWORK_RESTRICTION = "ai_script_without_network_restriction";
    public static final String AI_SCRIPT_WITHOUT_EXEC_RESTRICTION = "ai_script_without_exec_restriction";
    public static final String AI_TOOL_AVAILABILITY_PROPERTY = "ai_tool_availability";

    private static final String AI_SCRIPT_CODE_ID_PREFIX = "ai-script-";

    interface DialogHandle {
        void showCode(String codeId);
        void showAndFocus();
        String currentCodeText();
        boolean showsCode(String codeId);
        void hideDialog();
    }

    interface DialogFactory {
        DialogHandle create(CodeStateProvider codeStateProvider, DialogCallbacks callbacks);
    }

    interface CodeStateProvider {
        ReadCodeResponse readCurrentState(String codeId);
    }

    interface DialogCallbacks {
        RunScriptResponse runFromDialog(String codeText);
        void dialogCancelled();
    }

    private final ResourceController resourceController;
    private final DialogFactory dialogFactory;
    private FormulaValidationSupport formulaValidationSupport;
    private final Set<AiCodeRunListener> runListeners = new LinkedHashSet<AiCodeRunListener>();
    private final Map<String, ReadCodeResponse> archivedStates = new HashMap<String, ReadCodeResponse>();
    private long nextCodeId = 1L;
    private CurrentScript currentScript;
    private DialogHandle dialog;
    private boolean loadingDialogCode;

    public AiOwnedScriptHostService() {
        this(Controller.getCurrentController() == null ? null : Controller.getCurrentController().getResourceController());
    }

    AiOwnedScriptHostService(ResourceController resourceController) {
        this(resourceController,
            (codeStateProvider, callbacks) -> new AiOwnedScriptDialog(codeStateProvider, callbacks),
            null);
    }

    AiOwnedScriptHostService(ResourceController resourceController, DialogFactory dialogFactory) {
        this(resourceController, dialogFactory, null);
    }

    AiOwnedScriptHostService(ResourceController resourceController,
                             DialogFactory dialogFactory,
                             FormulaValidationSupport formulaValidationSupport) {
        this.resourceController = resourceController;
        this.dialogFactory = dialogFactory;
        this.formulaValidationSupport = formulaValidationSupport;
    }

    @Override
    public ReadCodeResponse readCode(ReadCodeRequest request) {
        return onEdt(() -> doReadCode(request));
    }

    @Override
    public WriteCodeResponse writeCode(WriteCodeRequest request) {
        return onEdt(() -> doWriteCode(request));
    }

    @Override
    public CompileCodeResponse compileCode(CompileCodeRequest request) {
        return onEdt(() -> doCompileCode(request));
    }

    @Override
    public RunScriptResponse runScript(RunScriptRequest request) {
        return onEdt(() -> doRunScript(request));
    }

    @Override
    public AiChatCodeOperationResult evaluateFormula(EvaluateFormulaRequest request) {
        return onEdt(() -> doEvaluateFormula(request));
    }

    @Override
    public void addRunListener(AiCodeRunListener listener) {
        onEdt(() -> {
            if (listener != null) {
                runListeners.add(listener);
            }
            return null;
        });
    }

    @Override
    public void removeRunListener(AiCodeRunListener listener) {
        onEdt(() -> {
            if (listener != null) {
                runListeners.remove(listener);
            }
            return null;
        });
    }

    public void showCode(String codeId) {
        onEdt(() -> {
            if (currentScript == null || codeId == null || !codeId.equals(currentScript.codeId)) {
                return null;
            }
            showCodeInDialog(codeId);
            dialog().showAndFocus();
            return null;
        });
    }

    AiChatCodeOperationResult doEvaluateFormula(EvaluateFormulaRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required.");
        }
        NodeModel targetNode = request.getTargetNode();
        if (targetNode == null) {
            throw new IllegalArgumentException("targetNode is required.");
        }
        String formulaText = request.getFormulaText();
        if (formulaText == null || formulaText.trim().isEmpty()) {
            throw new IllegalArgumentException("formulaText is required.");
        }
        if (!formulaText.startsWith("=")) {
            throw new IllegalArgumentException("formulaText must start with '='.");
        }
        return formulaValidationSupport().validateFormula(targetNode, formulaText);
    }

    private FormulaValidationSupport formulaValidationSupport() {
        if (formulaValidationSupport == null) {
            formulaValidationSupport = new FormulaValidationSupport();
        }
        return formulaValidationSupport;
    }

    ReadCodeResponse doReadCode(ReadCodeRequest request) {
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

    WriteCodeResponse doWriteCode(WriteCodeRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required.");
        }
        if (request.getHost() != null && request.getHost() != ScriptHost.AI) {
            throw new IllegalArgumentException("host AI is required for AI-owned scripts.");
        }
        if (request.getText() == null) {
            throw new IllegalArgumentException("text is required.");
        }
        assertNotRunning();
        assertExpectedFingerprint(request.getExpectedFingerprint());
        String newCodeId = nextCodeId();
        if (currentScript != null) {
            archivedStates.put(currentScript.codeId, replacedState(currentScript, newCodeId));
        }
        currentScript = new CurrentScript(newCodeId, request.getText());
        currentScript.fingerprint = fingerprint(currentScript.storedText);
        currentScript.latestState = readyState(currentScript, currentScript.storedText, currentScript.fingerprint);
        if (dialog != null) {
            showCodeInDialog(newCodeId);
        }
        return new WriteCodeResponse(
            currentScript.codeId,
            ScriptHost.AI,
            AI_SCRIPT_CONTENT_TYPE,
            CodeLifecycleStatus.READY,
            currentScript.fingerprint);
    }

    CompileCodeResponse doCompileCode(CompileCodeRequest request) {
        requireCurrentScript(request == null ? null : request.getCodeId(), request == null ? null : request.getHost());
        assertExpectedFingerprint(request == null ? null : request.getExpectedFingerprint());
        synchronizeCurrentTextFromDialog();
        String codeText = currentText();
        ScriptingEngine.GroovyCompileResult compileResult = ScriptingEngine.compileGroovyScriptForDiagnostics(
            codeText,
            aiStartedPermissions());
        String currentFingerprint = fingerprint(codeText);
        CompileCodeResponse response = new CompileCodeResponse(
            currentScript.codeId,
            ScriptHost.AI,
            AI_SCRIPT_CONTENT_TYPE,
            compileResult.isSuccessful() ? CodeLifecycleStatus.READY : CodeLifecycleStatus.FAILED,
            currentFingerprint,
            compileResult.getCompilerDiagnostics(),
            compileResult.getErrorMessage(),
            compileResult.getLineNumber());
        currentScript.latestState = compileResult.isSuccessful()
            ? readyState(currentScript, codeText, currentFingerprint)
            : failedState(currentScript, currentFingerprint, compileResult.getCompilerDiagnostics(),
                compileResult.getErrorMessage(), compileResult.getLineNumber(), null, null, null);
        if (dialog != null && dialog.showsCode(currentScript.codeId)) {
            showCodeInDialog(currentScript.codeId);
        }
        return response;
    }

    RunScriptResponse doRunScript(RunScriptRequest request) {
        requireCurrentScript(request == null ? null : request.getCodeId(), request == null ? null : request.getHost());
        assertExpectedFingerprint(request == null ? null : request.getExpectedFingerprint());
        assertNotRunning();
        synchronizeCurrentTextFromDialog();
        AiScriptExecutionPolicy policy = executionPolicy();
        if (policy == AiScriptExecutionPolicy.SHOWN_USER_RUN) {
            showCodeInDialog(currentScript.codeId);
            dialog().showAndFocus();
            RunScriptResponse response = waitingResponse(ScriptRunInitiator.AI);
            currentScript.latestState = waitingState(currentScript, response.getFingerprint(), ScriptRunInitiator.AI);
            return response;
        }
        return executeCurrentScript(ScriptRunInitiator.AI, aiStartedPermissions());
    }

    RunScriptResponse runFromDialog(String codeText) {
        if (currentScript == null) {
            throw new IllegalStateException("No AI-owned script exists.");
        }
        if (!isScriptExecutionAvailable()) {
            throw new IllegalStateException("AI-owned script execution is currently disabled.");
        }
        currentScript.storedText = codeText == null ? "" : codeText;
        currentScript.fingerprint = fingerprint(currentScript.storedText);
        return executeCurrentScript(ScriptRunInitiator.USER, userStartedPermissions());
    }

    void dialogCancelled() {
        synchronizeCurrentTextFromDialog();
    }

    ScriptingPermissions aiStartedPermissions() {
        return restrictedExecutionPermissions(
            booleanProperty(AI_SCRIPT_WITHOUT_FILE_RESTRICTION),
            booleanProperty(AI_SCRIPT_WITHOUT_WRITE_RESTRICTION),
            booleanProperty(AI_SCRIPT_WITHOUT_NETWORK_RESTRICTION),
            booleanProperty(AI_SCRIPT_WITHOUT_EXEC_RESTRICTION));
    }

    ScriptingPermissions userStartedPermissions() {
        if (userRunPermissionMode() == AiScriptUserRunPermissionMode.UNRESTRICTED) {
            return restrictedExecutionPermissions(true, true, true, true);
        }
        return restrictedExecutionPermissions(
            booleanProperty(AI_SCRIPT_WITHOUT_FILE_RESTRICTION),
            booleanProperty(AI_SCRIPT_WITHOUT_WRITE_RESTRICTION),
            booleanProperty(AI_SCRIPT_WITHOUT_NETWORK_RESTRICTION),
            booleanProperty(AI_SCRIPT_WITHOUT_EXEC_RESTRICTION));
    }

    private RunScriptResponse executeCurrentScript(ScriptRunInitiator runInitiator,
                                                   ScriptingPermissions permissions) {
        assertNotRunning();
        synchronizeCurrentTextFromDialog();
        currentScript.running = true;
        try {
            String codeText = currentText();
            String currentFingerprint = fingerprint(codeText);
            ScriptingEngine.GroovyCompileResult compileResult = ScriptingEngine.compileGroovyScriptForDiagnostics(
                codeText,
                permissions);
            if (!compileResult.isSuccessful()) {
                RunScriptResponse response = new RunScriptResponse(
                    currentScript.codeId,
                    ScriptHost.AI,
                    AI_SCRIPT_CONTENT_TYPE,
                    CodeLifecycleStatus.FAILED,
                    runInitiator,
                    currentFingerprint,
                    compileResult.getCompilerDiagnostics(),
                    compileResult.getErrorMessage(),
                    compileResult.getLineNumber(),
                    null,
                    null);
                currentScript.latestState = failedState(currentScript, currentFingerprint,
                    response.getCompilerDiagnostics(), response.getErrorMessage(), response.getLineNumber(), null,
                    null, runInitiator);
                refreshDialogAfterRun(response);
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
                    codeText,
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
                    runInitiator,
                    currentFingerprint,
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
                    runInitiator,
                    currentFingerprint,
                    codeText,
                    null,
                    null,
                    null,
                    null,
                    stdout,
                    structuredResult);
                refreshDialogAfterRun(response);
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
                    runInitiator,
                    currentFingerprint,
                    null,
                    error.getMessage(),
                    errorLine,
                    stdout,
                    null);
                currentScript.latestState = failedState(currentScript, currentFingerprint, null,
                    error.getMessage(), errorLine, stdout, null, runInitiator);
                refreshDialogAfterRun(response);
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
                    runInitiator,
                    currentFingerprint,
                    null,
                    error.getMessage(),
                    errorLine,
                    stdout,
                    null);
                currentScript.latestState = failedState(currentScript, currentFingerprint, null,
                    error.getMessage(), errorLine, stdout, null, runInitiator);
                refreshDialogAfterRun(response);
                fireRunFinished(response);
                return response;
            } catch (Exception error) {
                throw new IllegalStateException(error.getMessage(), error);
            }
        } finally {
            currentScript.running = false;
        }
    }

    private void refreshDialogAfterRun(RunScriptResponse response) {
        if (dialog == null || !dialog.showsCode(currentScript.codeId)) {
            return;
        }
        showCodeInDialog(currentScript.codeId);
        if (response.getStatus() == CodeLifecycleStatus.SUCCEEDED) {
            dialog.hideDialog();
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
        String currentFingerprint = fingerprint(currentText());
        if (!normalizedFingerprint.equals(currentFingerprint)) {
            throw new IllegalStateException("Expected fingerprint does not match the current code.");
        }
    }

    private void assertNotRunning() {
        if (currentScript != null && currentScript.running) {
            throw new IllegalStateException("The current AI-owned script is already running.");
        }
    }

    private ReadCodeResponse currentReadCodeResponse(String requestedFingerprint) {
        String codeText = currentText();
        String currentFingerprint = fingerprint(codeText);
        ReadCodeResponse state = currentScript.latestState == null
            ? readyState(currentScript, codeText, currentFingerprint)
            : currentScript.latestState;
        String responseText = currentFingerprint.equals(normalized(requestedFingerprint)) ? null : codeText;
        return new ReadCodeResponse(
            currentScript.codeId,
            ScriptHost.AI,
            AI_SCRIPT_CONTENT_TYPE,
            state.getStatus(),
            state.getRunInitiator(),
            currentFingerprint,
            responseText,
            state.getReplacementCodeId(),
            state.getCompilerDiagnostics(),
            state.getErrorMessage(),
            state.getLineNumber(),
            state.getStdout(),
            state.getStructuredResult());
    }

    private ReadCodeResponse readyState(CurrentScript script, String codeText, String currentFingerprint) {
        return new ReadCodeResponse(
            script.codeId,
            ScriptHost.AI,
            AI_SCRIPT_CONTENT_TYPE,
            CodeLifecycleStatus.READY,
            null,
            currentFingerprint,
            codeText,
            null,
            null,
            null,
            null,
            null,
            null);
    }

    private ReadCodeResponse waitingState(CurrentScript script, String currentFingerprint,
                                          ScriptRunInitiator runInitiator) {
        return new ReadCodeResponse(
            script.codeId,
            ScriptHost.AI,
            AI_SCRIPT_CONTENT_TYPE,
            CodeLifecycleStatus.WAITING_FOR_USER_RUN,
            runInitiator,
            currentFingerprint,
            currentText(),
            null,
            null,
            null,
            null,
            null,
            null);
    }

    private ReadCodeResponse failedState(CurrentScript script,
                                         String currentFingerprint,
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
            currentFingerprint,
            currentText(),
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
        String codeText = currentText();
        String currentFingerprint = fingerprint(codeText);
        ReadCodeResponse state = script.latestState == null
            ? readyState(script, codeText, currentFingerprint)
            : script.latestState;
        return new ReadCodeResponse(
            script.codeId,
            ScriptHost.AI,
            AI_SCRIPT_CONTENT_TYPE,
            CodeLifecycleStatus.REPLACED,
            state.getRunInitiator(),
            currentFingerprint,
            null,
            replacementCodeId,
            state.getCompilerDiagnostics(),
            state.getErrorMessage(),
            state.getLineNumber(),
            state.getStdout(),
            state.getStructuredResult());
    }

    private RunScriptResponse waitingResponse(ScriptRunInitiator runInitiator) {
        String codeText = currentText();
        String currentFingerprint = fingerprint(codeText);
        return new RunScriptResponse(
            currentScript.codeId,
            ScriptHost.AI,
            AI_SCRIPT_CONTENT_TYPE,
            CodeLifecycleStatus.WAITING_FOR_USER_RUN,
            runInitiator,
            currentFingerprint,
            null,
            null,
            null,
            null,
            null);
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

    private DialogHandle dialog() {
        if (dialog == null) {
            dialog = dialogFactory.create(this::readCurrentStateForDialog, new DialogCallbacks() {
                @Override
                public RunScriptResponse runFromDialog(String codeText) {
                    return AiOwnedScriptHostService.this.runFromDialog(codeText);
                }

                @Override
                public void dialogCancelled() {
                    AiOwnedScriptHostService.this.dialogCancelled();
                }
            });
        }
        return dialog;
    }

    private ReadCodeResponse readCurrentStateForDialog(String codeId) {
        return doReadCode(new ReadCodeRequest(codeId, null, null));
    }

    private void showCodeInDialog(String codeId) {
        if (codeId == null) {
            return;
        }
        loadingDialogCode = true;
        try {
            dialog().showCode(codeId);
        } finally {
            loadingDialogCode = false;
        }
    }

    private void synchronizeCurrentTextFromDialog() {
        if (currentScript == null || dialog == null || !dialog.showsCode(currentScript.codeId)) {
            return;
        }
        currentScript.storedText = dialog.currentCodeText() == null ? "" : dialog.currentCodeText();
        currentScript.fingerprint = fingerprint(currentScript.storedText);
    }

    private String currentText() {
        if (currentScript == null) {
            return "";
        }
        if (loadingDialogCode) {
            return currentScript.storedText == null ? "" : currentScript.storedText;
        }
        if (dialog != null && dialog.showsCode(currentScript.codeId)) {
            return dialog.currentCodeText() == null ? "" : dialog.currentCodeText();
        }
        return currentScript.storedText == null ? "" : currentScript.storedText;
    }

    private AiScriptExecutionPolicy executionPolicy() {
        return resourceController == null
            ? AiScriptExecutionPolicy.SHOWN_USER_RUN
            : resourceController.getEnumProperty(AI_SCRIPT_EXECUTION_POLICY, AiScriptExecutionPolicy.SHOWN_USER_RUN);
    }

    private AiScriptUserRunPermissionMode userRunPermissionMode() {
        return resourceController == null
            ? AiScriptUserRunPermissionMode.AI_SPECIFIC_PERMISSIONS
            : resourceController.getEnumProperty(
                AI_SCRIPT_USER_RUN_PERMISSION_MODE,
                AiScriptUserRunPermissionMode.AI_SPECIFIC_PERMISSIONS);
    }

    private boolean isScriptExecutionAvailable() {
        if (resourceController == null) {
            return true;
        }
        String availability = resourceController.getProperty(AI_TOOL_AVAILABILITY_PROPERTY, null);
        return availability == null || availability.trim().isEmpty() || "SCRIPT_EXECUTION".equals(availability.trim());
    }

    private ScriptingPermissions restrictedExecutionPermissions(boolean file, boolean write, boolean network, boolean exec) {
        Map<String, Boolean> permissions = new HashMap<String, Boolean>();
        permissions.put(ScriptingPermissions.RESOURCES_EXECUTE_SCRIPTS_WITHOUT_ASKING, Boolean.TRUE);
        permissions.put(ScriptingPermissions.RESOURCES_EXECUTE_SCRIPTS_WITHOUT_READ_RESTRICTION, Boolean.valueOf(file));
        permissions.put(ScriptingPermissions.RESOURCES_EXECUTE_SCRIPTS_WITHOUT_WRITE_RESTRICTION, Boolean.valueOf(write));
        permissions.put(ScriptingPermissions.RESOURCES_EXECUTE_SCRIPTS_WITHOUT_NETWORK_RESTRICTION, Boolean.valueOf(network));
        permissions.put(ScriptingPermissions.RESOURCES_EXECUTE_SCRIPTS_WITHOUT_EXEC_RESTRICTION, Boolean.valueOf(exec));
        permissions.put(ScriptingPermissions.RESOURCES_EXECUTE_SCRIPTS_WITHOUT_AI_REQUEST_RESTRICTION, Boolean.FALSE);
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
        List<AiCodeRunListener> listeners = new ArrayList<AiCodeRunListener>(runListeners);
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

    private <T> T onEdt(Callable<T> callable) {
        if (SwingUtilities.isEventDispatchThread()) {
            return call(callable);
        }
        final Object[] value = new Object[1];
        final Throwable[] failure = new Throwable[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    value[0] = call(callable);
                } catch (Throwable error) {
                    failure[0] = error;
                }
            });
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error.getMessage(), error);
        } catch (InvocationTargetException error) {
            throw new IllegalStateException(error.getMessage(), error);
        }
        if (failure[0] != null) {
            rethrow(failure[0]);
        }
        @SuppressWarnings("unchecked")
        T cast = (T) value[0];
        return cast;
    }

    private <T> T call(Callable<T> callable) {
        try {
            return callable.call();
        } catch (RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException(error.getMessage(), error);
        }
    }

    private void rethrow(Throwable failure) {
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw new IllegalStateException(failure.getMessage(), failure);
    }

    private static final class CurrentScript {
        private final String codeId;
        private String storedText;
        private String fingerprint;
        private ReadCodeResponse latestState;
        private boolean running;

        private CurrentScript(String codeId, String codeText) {
            this.codeId = codeId;
            this.storedText = codeText == null ? "" : codeText;
            this.fingerprint = null;
        }
    }
}
