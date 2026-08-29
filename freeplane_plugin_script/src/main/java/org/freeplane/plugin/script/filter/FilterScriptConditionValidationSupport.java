package org.freeplane.plugin.script.filter;

import java.io.PrintStream;

import org.freeplane.features.ai.code.AiChatCodeOperationResult;
import org.freeplane.features.ai.code.CodeStateToken;
import org.freeplane.features.ai.code.CompileCodeResponse;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.text.TextController;
import org.freeplane.plugin.script.FormulaUtils;
import org.freeplane.plugin.script.GroovyScriptValidation;
import org.freeplane.plugin.script.IFreeplaneScriptErrorHandler;

public class FilterScriptConditionValidationSupport {
    public static final String FORMULA_CONDITION_CONTENT_TYPE = "text/x-freeplane-formula-condition-groovy";

    interface ScriptValidator {
        Object validate(NodeModel node, String script, PrintStream outStream, IFreeplaneScriptErrorHandler errorHandler);
    }

    private static final GroovyScriptValidation.ResultPolicy CONDITION_RESULT_POLICY =
        new GroovyScriptValidation.ResultPolicy() {
            @Override
            public boolean accepts(Object result) {
                return isConditionResult(result);
            }

            @Override
            public String resultText(Object result) {
                return String.valueOf(result);
            }

            @Override
            public String invalidResultMessage(Object result) {
                return FilterScriptConditionValidationSupport.invalidResultMessage(result);
            }
        };

    private final GroovyScriptValidation groovyScriptValidation;

    public FilterScriptConditionValidationSupport() {
        this(TextController.getController(), new ScriptValidator() {
            @Override
            public Object validate(NodeModel node, String script, PrintStream outStream,
                                   IFreeplaneScriptErrorHandler errorHandler) {
                return FormulaUtils.validateScript(node, script, outStream, errorHandler);
            }
        });
    }

    FilterScriptConditionValidationSupport(TextController textController, ScriptValidator scriptValidator) {
        this.groovyScriptValidation = new GroovyScriptValidation(
            textController,
            scriptValidator::validate,
            CONDITION_RESULT_POLICY,
            true,
            CodeStateToken::fingerprint);
    }

    public CompileCodeResponse compile(String scriptText) {
        return groovyScriptValidation.compile(normalizeSourceText(scriptText), FORMULA_CONDITION_CONTENT_TYPE);
    }

    public AiChatCodeOperationResult validate(NodeModel node, String scriptText) {
        String sourceText = normalizeSourceText(scriptText);
        return groovyScriptValidation.validate(
            node,
            sourceText,
            sourceText);
    }

    public static boolean isConditionResult(Object result) {
        return result instanceof Boolean || result instanceof Number;
    }

    public static boolean conditionResultAsBoolean(Object result) {
        if (result instanceof Boolean) {
            return ((Boolean) result).booleanValue();
        }
        if (result instanceof Number) {
            return ((Number) result).doubleValue() != 0;
        }
        throw new IllegalArgumentException(invalidResultMessage(result));
    }

    public static String invalidResultMessage(Object result) {
        return "Filter condition must return Boolean or Number, but returned " + String.valueOf(result) + ".";
    }

    private String normalizeSourceText(String scriptText) {
        return scriptText == null ? "" : scriptText;
    }
}
