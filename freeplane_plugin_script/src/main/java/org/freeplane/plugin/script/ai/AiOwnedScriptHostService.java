package org.freeplane.plugin.script.ai;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import javax.swing.SwingUtilities;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.CapturedPrintStream;
import org.freeplane.features.ai.code.AiChatCodeOperationResult;
import org.freeplane.features.ai.code.AiCodeHostService;
import org.freeplane.features.ai.code.AiCodeRunListener;
import org.freeplane.features.ai.code.CodeState;
import org.freeplane.features.ai.code.CodeStateContent;
import org.freeplane.features.ai.code.CodeStateDiagnostic;
import org.freeplane.features.ai.code.CodeStateDiagnostics;
import org.freeplane.features.ai.code.CodeStateField;
import org.freeplane.features.ai.code.CodeStateToken;
import org.freeplane.features.ai.code.CompileCodeRequest;
import org.freeplane.features.ai.code.CompileCodeResponse;
import org.freeplane.features.ai.code.EvaluateFormulaRequest;
import org.freeplane.features.ai.code.ReadCodeRequest;
import org.freeplane.features.ai.code.ReadCodeResponse;
import org.freeplane.features.ai.code.RunCodeRequest;
import org.freeplane.features.ai.code.RunCodeResponse;
import org.freeplane.features.ai.code.ScriptHost;
import org.freeplane.features.ai.code.ScriptRunInitiator;
import org.freeplane.features.ai.code.WriteAndRunCodeRequest;
import org.freeplane.features.ai.code.WriteCodeRequest;
import org.freeplane.features.ai.code.WriteCodeResponse;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.plugin.script.ExecuteScriptException;
import org.freeplane.plugin.script.FormulaValidationSupport;
import org.freeplane.plugin.script.GroovyCompilerDiagnosticsMapper;
import org.freeplane.plugin.script.IFreeplaneScriptErrorHandler;
import org.freeplane.plugin.script.ScriptContext;
import org.freeplane.plugin.script.ScriptInputJsonSupport;
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

    interface DialogHandle {
        void showCode();
        void showAndFocus();
        CodeStateContent currentContent();
        boolean hasCode();
        void hideDialog();
    }

    interface DialogFactory {
        DialogHandle create(CodeStateProvider codeStateProvider, DialogCallbacks callbacks);
    }

    interface CodeStateProvider {
        ReadCodeResponse readCodeState();
    }

    interface DialogCallbacks {
        RunCodeResponse runFromDialog(CodeStateContent content);
        void dialogCancelled();
    }

    private static final class ValidationOutcome {
        private final CodeState codeState;
        private final List<CodeStateDiagnostic> diagnostics;
        private final String errorMessage;
        private final Object argsValue;

        private ValidationOutcome(CodeState codeState,
                                  List<CodeStateDiagnostic> diagnostics,
                                  String errorMessage,
                                  Object argsValue) {
            this.codeState = codeState;
            this.diagnostics = diagnostics;
            this.errorMessage = errorMessage;
            this.argsValue = argsValue;
        }

        private boolean isSuccessful() {
            return codeState == null;
        }
    }

    private final ResourceController resourceController;
    private final DialogFactory dialogFactory;
    private FormulaValidationSupport formulaValidationSupport;
    private final Set<AiCodeRunListener> runListeners = new LinkedHashSet<AiCodeRunListener>();
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
    public RunCodeResponse runCode(RunCodeRequest request) {
        return onEdt(() -> doRunCode(request));
    }

    @Override
    public RunCodeResponse writeAndRunCode(WriteAndRunCodeRequest request) {
        return onEdt(() -> doWriteAndRunCode(request));
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

    public void showCurrentCode() {
        onEdt(() -> {
            if (currentScript == null) {
                return null;
            }
            showCodeInDialog();
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
        ScriptHost host = request == null ? null : request.getHost();
        if (host != ScriptHost.AI) {
            throw new IllegalArgumentException("host AI is required.");
        }
        if (currentScript == null) {
            return noCodeState();
        }
        return currentReadCodeResponse();
    }

    WriteCodeResponse doWriteCode(WriteCodeRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required.");
        }
        if (request.getHost() != ScriptHost.AI) {
            throw new IllegalArgumentException("host AI is required for AI-owned scripts.");
        }
        if (request.getContent() == null) {
            throw new IllegalArgumentException("content is required.");
        }
        assertNotRunning();
        if (currentScript != null) {
            requireExpectedStateToken(request.getExpectedStateToken());
        }
        CodeStateToken stateToken = storeCurrentScriptContent(request.getContent());
        return new WriteCodeResponse(
            ScriptHost.AI,
            AI_SCRIPT_CONTENT_TYPE,
            CodeState.EDITED,
            stateToken);
    }

    RunCodeResponse doWriteAndRunCode(WriteAndRunCodeRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required.");
        }
        if (request.getContent() == null) {
            throw new IllegalArgumentException("content is required.");
        }
        assertNotRunning();
        CodeStateToken stateToken = storeCurrentScriptContent(request.getContent());
        return doRunCode(new RunCodeRequest(ScriptHost.AI, stateToken));
    }

    CompileCodeResponse doCompileCode(CompileCodeRequest request) {
        requireCurrentScript(request == null ? null : request.getHost());
        requireExpectedStateToken(request == null ? null : request.getExpectedStateToken());
        synchronizeCurrentContentFromDialog();
        CodeStateContent content = currentContent();
        CodeStateToken stateToken = CodeStateToken.fromContent(content);
        ValidationOutcome validation = validate(content, stateToken, aiStartedPermissions());
        CompileCodeResponse response = new CompileCodeResponse(
            ScriptHost.AI,
            AI_SCRIPT_CONTENT_TYPE,
            validation.isSuccessful() ? CodeState.RUNNABLE : validation.codeState,
            stateToken,
            validation.diagnostics,
            validation.errorMessage);
        currentScript.latestState = validation.isSuccessful()
            ? runnableState(stateToken, content)
            : stateOf(content, stateToken, validation.codeState, validation.diagnostics, validation.errorMessage, null, null, null);
        if (dialog != null && dialog.hasCode()) {
            showCodeInDialog();
        }
        return response;
    }

    RunCodeResponse doRunCode(RunCodeRequest request) {
        requireCurrentScript(request == null ? null : request.getHost());
        requireExpectedStateToken(request == null ? null : request.getExpectedStateToken());
        assertNotRunning();
        synchronizeCurrentContentFromDialog();
        CodeStateContent content = currentContent();
        CodeStateToken stateToken = CodeStateToken.fromContent(content);
        ValidationOutcome argumentsValidation = validateArguments(content, stateToken);
        if (!argumentsValidation.isSuccessful()) {
            RunCodeResponse response = new RunCodeResponse(
                ScriptHost.AI,
                AI_SCRIPT_CONTENT_TYPE,
                argumentsValidation.codeState,
                ScriptRunInitiator.AI,
                stateToken,
                argumentsValidation.diagnostics,
                argumentsValidation.errorMessage,
                null,
                null);
            currentScript.latestState = stateOf(
                content,
                stateToken,
                argumentsValidation.codeState,
                argumentsValidation.diagnostics,
                argumentsValidation.errorMessage,
                null,
                null,
                ScriptRunInitiator.AI);
            refreshDialogAfterRun(response);
            fireRunFinished(response);
            return response;
        }
        AiScriptExecutionPolicy policy = executionPolicy();
        if (policy == AiScriptExecutionPolicy.SHOWN_USER_RUN) {
            showCodeInDialog();
            dialog().showAndFocus();
            RunCodeResponse response = waitingResponse(stateToken, ScriptRunInitiator.AI);
            currentScript.latestState = waitingState(content, stateToken, ScriptRunInitiator.AI);
            return response;
        }
        ValidationOutcome validation = validate(content, stateToken, aiStartedPermissions());
        if (!validation.isSuccessful()) {
            RunCodeResponse response = new RunCodeResponse(
                ScriptHost.AI,
                AI_SCRIPT_CONTENT_TYPE,
                validation.codeState,
                ScriptRunInitiator.AI,
                stateToken,
                validation.diagnostics,
                validation.errorMessage,
                null,
                null);
            currentScript.latestState = stateOf(
                content,
                stateToken,
                validation.codeState,
                validation.diagnostics,
                validation.errorMessage,
                null,
                null,
                ScriptRunInitiator.AI);
            refreshDialogAfterRun(response);
            fireRunFinished(response);
            return response;
        }
        return executeCurrentScript(content, stateToken, ScriptRunInitiator.AI, aiStartedPermissions(), validation.argsValue);
    }

    RunCodeResponse runFromDialog(CodeStateContent content) {
        if (currentScript == null) {
            throw new IllegalStateException("No AI-owned script exists.");
        }
        if (!isScriptExecutionAvailable()) {
            throw new IllegalStateException("AI-owned script execution is currently disabled.");
        }
        assertNotRunning();
        currentScript.storedContent = sanitizeContent(content);
        CodeStateContent currentContent = currentContent();
        CodeStateToken stateToken = CodeStateToken.fromContent(currentContent);
        ValidationOutcome validation = validate(currentContent, stateToken, userStartedPermissions());
        if (!validation.isSuccessful()) {
            RunCodeResponse response = new RunCodeResponse(
                ScriptHost.AI,
                AI_SCRIPT_CONTENT_TYPE,
                validation.codeState,
                ScriptRunInitiator.USER,
                stateToken,
                validation.diagnostics,
                validation.errorMessage,
                null,
                null);
            currentScript.latestState = stateOf(
                currentContent,
                stateToken,
                validation.codeState,
                validation.diagnostics,
                validation.errorMessage,
                null,
                null,
                ScriptRunInitiator.USER);
            refreshDialogAfterRun(response);
            fireRunFinished(response);
            return response;
        }
        return executeCurrentScript(currentContent, stateToken, ScriptRunInitiator.USER, userStartedPermissions(), validation.argsValue);
    }

    void dialogCancelled() {
        synchronizeCurrentContentFromDialog();
        if (currentScript == null || currentScript.latestState == null
            || currentScript.latestState.getCodeState() != CodeState.WAITING_FOR_USER_RUN) {
            return;
        }
        CodeStateContent content = currentContent();
        CodeStateToken stateToken = CodeStateToken.fromContent(content);
        RunCodeResponse response = new RunCodeResponse(
            ScriptHost.AI,
            AI_SCRIPT_CONTENT_TYPE,
            CodeState.USER_RUN_CANCELLED,
            ScriptRunInitiator.USER,
            stateToken,
            null,
            null,
            null,
            null);
        currentScript.latestState = stateOf(
            content,
            stateToken,
            CodeState.USER_RUN_CANCELLED,
            null,
            null,
            null,
            null,
            ScriptRunInitiator.USER);
        fireRunFinished(response);
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

    private RunCodeResponse executeCurrentScript(CodeStateContent content,
                                                 CodeStateToken stateToken,
                                                 ScriptRunInitiator runInitiator,
                                                 ScriptingPermissions permissions,
                                                 Object argsValue) {
        assertNotRunning();
        synchronizeCurrentContentFromDialog();
        currentScript.running = true;
        try {
            NodeModel selectedNode = currentSelectedNode();
            if (selectedNode == null) {
                throw new IllegalStateException("No node is currently selected.");
            }
            final int[] lineNumber = new int[] { -1 };
            CapturedPrintStream outputCapture = CapturedPrintStream.tee(System.out);
            try {
                ScriptContext scriptContext = new ScriptContext(null)
                    .withBoundVariables(ScriptInputJsonSupport.boundVariables(argsValue))
                    .withCallbackOutputStream(System.out);
                Object result = ScriptingEngine.executeScript(
                    selectedNode,
                    content.getSourceText(),
                    new IFreeplaneScriptErrorHandler() {
                        @Override
                        public void gotoLine(int pLineNumber) {
                            lineNumber[0] = pLineNumber;
                        }
                    },
                    outputCapture.printStream(),
                    scriptContext,
                    permissions);
                String stdout = outputCapture.text();
                Object structuredResult = toJsonSafeValue(result);
                RunCodeResponse response = new RunCodeResponse(
                    ScriptHost.AI,
                    AI_SCRIPT_CONTENT_TYPE,
                    CodeState.RUN_SUCCEEDED,
                    runInitiator,
                    stateToken,
                    null,
                    null,
                    stdout,
                    structuredResult);
                currentScript.latestState = stateOf(
                    content,
                    stateToken,
                    CodeState.RUN_SUCCEEDED,
                    null,
                    null,
                    stdout,
                    structuredResult,
                    runInitiator);
                refreshDialogAfterRun(response);
                fireRunFinished(response);
                return response;
            } catch (ExecuteScriptException error) {
                String stdout = outputCapture.text();
                List<CodeStateDiagnostic> diagnostics = CodeStateDiagnostics.singleton(
                    CodeStateField.SOURCE_TEXT,
                    error.getMessage(),
                    lineNumber[0] >= 0 ? Integer.valueOf(lineNumber[0]) : null,
                    null);
                RunCodeResponse response = new RunCodeResponse(
                    ScriptHost.AI,
                    AI_SCRIPT_CONTENT_TYPE,
                    CodeState.RUN_FAILED,
                    runInitiator,
                    stateToken,
                    diagnostics,
                    error.getMessage(),
                    stdout,
                    null);
                currentScript.latestState = stateOf(content, stateToken, CodeState.RUN_FAILED, diagnostics,
                    error.getMessage(), stdout, null, runInitiator);
                refreshDialogAfterRun(response);
                fireRunFinished(response);
                return response;
            } catch (RuntimeException error) {
                String stdout = outputCapture.text();
                List<CodeStateDiagnostic> diagnostics = CodeStateDiagnostics.singleton(
                    CodeStateField.SOURCE_TEXT,
                    error.getMessage(),
                    lineNumber[0] >= 0 ? Integer.valueOf(lineNumber[0]) : null,
                    null);
                RunCodeResponse response = new RunCodeResponse(
                    ScriptHost.AI,
                    AI_SCRIPT_CONTENT_TYPE,
                    CodeState.RUN_FAILED,
                    runInitiator,
                    stateToken,
                    diagnostics,
                    error.getMessage(),
                    stdout,
                    null);
                currentScript.latestState = stateOf(content, stateToken, CodeState.RUN_FAILED, diagnostics,
                    error.getMessage(), stdout, null, runInitiator);
                refreshDialogAfterRun(response);
                fireRunFinished(response);
                return response;
            } catch (Exception error) {
                throw new IllegalStateException(error.getMessage(), error);
            } finally {
                outputCapture.close();
            }
        } finally {
            currentScript.running = false;
        }
    }

    private ValidationOutcome validate(CodeStateContent content,
                                       CodeStateToken stateToken,
                                       ScriptingPermissions permissions) {
        ValidationOutcome argumentsValidation = validateArguments(content, stateToken);
        if (!argumentsValidation.isSuccessful()) {
            return argumentsValidation;
        }
        ScriptingEngine.GroovyCompileResult compileResult = ScriptingEngine.compileGroovyScriptForDiagnostics(
            content.getSourceText(),
            permissions);
        if (!compileResult.isSuccessful()) {
            List<CodeStateDiagnostic> diagnostics = GroovyCompilerDiagnosticsMapper.toSourceDiagnostics(
                compileResult.getCompilerDiagnostics());
            return new ValidationOutcome(CodeState.INVALID_SCRIPT, diagnostics, compileResult.getErrorMessage(), null);
        }
        return new ValidationOutcome(null, null, null, argumentsValidation.argsValue);
    }

    private ValidationOutcome validateArguments(CodeStateContent content, CodeStateToken stateToken) {
        ScriptInputJsonSupport.ParseResult parseResult = ScriptInputJsonSupport.parseInputText(
            content.getArgumentsJsonText(),
            stateToken.getArgumentsFingerprint());
        if (!parseResult.isSuccessful()) {
            List<CodeStateDiagnostic> diagnostics = Collections.singletonList(parseResult.getDiagnostic());
            return new ValidationOutcome(
                CodeState.INVALID_ARGUMENTS_JSON,
                diagnostics,
                ScriptInputJsonSupport.primaryMessage(parseResult.getDiagnostic()),
                null);
        }
        return new ValidationOutcome(null, null, null, parseResult.getArgsValue());
    }

    private void refreshDialogAfterRun(RunCodeResponse response) {
        if (dialog == null || !dialog.hasCode()) {
            return;
        }
        showCodeInDialog();
        if (response.getCodeState() == CodeState.RUN_SUCCEEDED) {
            dialog.hideDialog();
        }
    }

    private void requireCurrentScript(ScriptHost host) {
        if (host != ScriptHost.AI) {
            throw new IllegalArgumentException("host AI is required.");
        }
        if (currentScript == null) {
            throw new IllegalStateException("No AI-owned script exists.");
        }
    }

    private void requireExpectedStateToken(CodeStateToken expectedStateToken) {
        if (expectedStateToken == null) {
            throw new IllegalArgumentException("expectedStateToken is required.");
        }
        if (currentScript == null) {
            throw new IllegalStateException("No AI-owned script exists.");
        }
        CodeStateToken currentStateToken = currentStateToken();
        if (!currentStateToken.matches(expectedStateToken)) {
            throw new IllegalStateException("Expected state token does not match the current code state.");
        }
    }

    private void assertNotRunning() {
        if (currentScript != null && currentScript.running) {
            throw new IllegalStateException("The current AI-owned script is already running.");
        }
    }

    private ReadCodeResponse currentReadCodeResponse() {
        CodeStateContent content = currentContent();
        CodeStateToken stateToken = CodeStateToken.fromContent(content);
        ReadCodeResponse state = currentScript.latestState;
        if (state == null || state.getStateToken() == null || !stateToken.matches(state.getStateToken())) {
            state = editedState(stateToken, content);
        }
        return new ReadCodeResponse(
            ScriptHost.AI,
            AI_SCRIPT_CONTENT_TYPE,
            state.getCodeState(),
            state.getRunInitiator(),
            stateToken,
            content,
            state.getDiagnostics(),
            state.getErrorMessage(),
            state.getStdout(),
            state.getStructuredResult());
    }

    private ReadCodeResponse editedState(CodeStateToken stateToken, CodeStateContent content) {
        return stateOf(content, stateToken, CodeState.EDITED, null, null, null, null, null);
    }

    private ReadCodeResponse runnableState(CodeStateToken stateToken, CodeStateContent content) {
        return stateOf(content, stateToken, CodeState.RUNNABLE, null, null, null, null, null);
    }

    private ReadCodeResponse waitingState(CodeStateContent content,
                                          CodeStateToken stateToken,
                                          ScriptRunInitiator runInitiator) {
        return stateOf(content, stateToken, CodeState.WAITING_FOR_USER_RUN, null, null, null, null, runInitiator);
    }

    private ReadCodeResponse stateOf(CodeStateContent content,
                                     CodeStateToken stateToken,
                                     CodeState codeState,
                                     List<CodeStateDiagnostic> diagnostics,
                                     String errorMessage,
                                     String stdout,
                                     Object structuredResult,
                                     ScriptRunInitiator runInitiator) {
        return new ReadCodeResponse(
            ScriptHost.AI,
            AI_SCRIPT_CONTENT_TYPE,
            codeState,
            runInitiator,
            stateToken,
            content,
            diagnostics,
            errorMessage,
            stdout,
            structuredResult);
    }

    private ReadCodeResponse noCodeState() {
        return new ReadCodeResponse(
            ScriptHost.AI,
            AI_SCRIPT_CONTENT_TYPE,
            CodeState.NO_CODE,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    }

    private RunCodeResponse waitingResponse(CodeStateToken stateToken, ScriptRunInitiator runInitiator) {
        return new RunCodeResponse(
            ScriptHost.AI,
            AI_SCRIPT_CONTENT_TYPE,
            CodeState.WAITING_FOR_USER_RUN,
            runInitiator,
            stateToken,
            null,
            null,
            null,
            null);
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
            dialog = dialogFactory.create(this::readCodeStateForDialog, new DialogCallbacks() {
                @Override
                public RunCodeResponse runFromDialog(CodeStateContent content) {
                    return AiOwnedScriptHostService.this.runFromDialog(content);
                }

                @Override
                public void dialogCancelled() {
                    AiOwnedScriptHostService.this.dialogCancelled();
                }
            });
        }
        return dialog;
    }

    private ReadCodeResponse readCodeStateForDialog() {
        if (currentScript == null) {
            return noCodeState();
        }
        return currentReadCodeResponse();
    }

    private void showCodeInDialog() {
        if (currentScript == null) {
            return;
        }
        loadingDialogCode = true;
        try {
            dialog().showCode();
        } finally {
            loadingDialogCode = false;
        }
    }

    private CodeStateToken storeCurrentScriptContent(CodeStateContent content) {
        CodeStateContent sanitizedContent = sanitizeContent(content);
        if (currentScript == null) {
            currentScript = new CurrentScript(sanitizedContent);
        }
        else {
            currentScript.storedContent = sanitizedContent;
        }
        if (dialog != null) {
            showCodeInDialog();
        }
        CodeStateToken stateToken = currentStateToken();
        currentScript.latestState = editedState(stateToken, currentScript.storedContent);
        return stateToken;
    }

    private void synchronizeCurrentContentFromDialog() {
        if (currentScript == null || dialog == null || !dialog.hasCode()) {
            return;
        }
        currentScript.storedContent = sanitizeContent(dialog.currentContent());
    }

    private CodeStateContent currentContent() {
        if (currentScript == null) {
            return new CodeStateContent("", null);
        }
        if (loadingDialogCode) {
            return currentScript.storedContent;
        }
        if (dialog != null && dialog.hasCode()) {
            return sanitizeContent(dialog.currentContent());
        }
        return currentScript.storedContent;
    }

    private CodeStateToken currentStateToken() {
        return CodeStateToken.fromContent(currentContent());
    }

    private CodeStateContent sanitizeContent(CodeStateContent content) {
        if (content == null) {
            throw new IllegalArgumentException("content is required.");
        }
        return new CodeStateContent(content.getSourceText() == null ? "" : content.getSourceText(), content.getArgumentsJsonText());
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


    private void fireRunFinished(RunCodeResponse response) {
        List<AiCodeRunListener> listeners = new ArrayList<AiCodeRunListener>(runListeners);
        for (AiCodeRunListener listener : listeners) {
            listener.runFinished(response);
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
        private CodeStateContent storedContent;
        private ReadCodeResponse latestState;
        private boolean running;

        private CurrentScript(CodeStateContent content) {
            this.storedContent = content;
        }
    }
}
