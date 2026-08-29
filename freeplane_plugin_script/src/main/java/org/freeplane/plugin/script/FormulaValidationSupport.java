package org.freeplane.plugin.script;

import java.io.PrintStream;

import org.freeplane.features.ai.code.AiChatCodeOperationResult;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.text.TextController;

public class FormulaValidationSupport {
    public interface FormulaValidator {
        Object validate(NodeModel node, String formulaText, PrintStream outStream, IFreeplaneScriptErrorHandler errorHandler);
    }

    private static final GroovyScriptValidation.ResultPolicy FORMULA_RESULT_POLICY =
        new GroovyScriptValidation.ResultPolicy() {
            @Override
            public boolean accepts(Object result) {
                return true;
            }

            @Override
            public String resultText(Object result) {
                return result == null ? null : String.valueOf(result);
            }

            @Override
            public String invalidResultMessage(Object result) {
                return null;
            }
        };

    private final GroovyScriptValidation groovyScriptValidation;

    public FormulaValidationSupport() {
        this(TextController.getController(), FormulaUtils::validateFormula);
    }

    public FormulaValidationSupport(TextController textController, FormulaValidator formulaValidator) {
        this.groovyScriptValidation = new GroovyScriptValidation(
            textController,
            formulaValidator::validate,
            FORMULA_RESULT_POLICY,
            false,
            GroovyScriptValidation::sha256Fingerprint);
    }

    public AiChatCodeOperationResult validateFormula(NodeModel node, String formulaText) {
        String compilationSource = formulaText != null && formulaText.startsWith("=")
            ? FormulaUtils.scriptOf(formulaText)
            : null;
        return groovyScriptValidation.validate(
            node,
            formulaText,
            compilationSource);
    }
}
