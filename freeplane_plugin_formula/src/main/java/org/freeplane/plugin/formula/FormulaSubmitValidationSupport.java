package org.freeplane.plugin.formula;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Formatter;
import java.util.function.Supplier;
import org.freeplane.features.ai.code.AiChatCodeOperationResult;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.text.TextController;
import org.freeplane.plugin.script.FormulaUtils;
import org.freeplane.plugin.script.IFreeplaneScriptErrorHandler;

class FormulaSubmitValidationSupport {
    interface FormulaValidator {
        Object validate(NodeModel node, String formulaText, PrintStream outStream, IFreeplaneScriptErrorHandler errorHandler);
    }

    private final TextController textController;
    private final FormulaValidator formulaValidator;

    FormulaSubmitValidationSupport() {
        this(TextController.getController(), FormulaUtils::validateFormula);
    }

    FormulaSubmitValidationSupport(TextController textController, FormulaValidator formulaValidator) {
        this.textController = textController;
        this.formulaValidator = formulaValidator;
    }

    public AiChatCodeOperationResult validateSubmittedFormula(NodeModel node, String formulaText) {
        final int[] lineNumber = new int[] { -1 };
        ByteArrayOutputStream outputBuffer = new ByteArrayOutputStream();
        try (PrintStream outStream = new PrintStream(outputBuffer, false, "UTF-8")) {
            Supplier<Object> validation = () -> formulaValidator.validate(node, formulaText, outStream,
                new IFreeplaneScriptErrorHandler() {
                    @Override
                    public void gotoLine(int pLineNumber) {
                        lineNumber[0] = pLineNumber;
                    }
                });
            Object result = textController.withNodeNumbering(true, validation);
            String standardOutput = new String(outputBuffer.toByteArray(), StandardCharsets.UTF_8);
            return new AiChatCodeOperationResult(
                "SUBMIT_VALIDATION",
                "USER",
                true,
                Collections.<String>emptyList(),
                standardOutput.isEmpty() ? null : standardOutput,
                result == null ? null : String.valueOf(result),
                null,
                null,
                null,
                fingerprint(formulaText));
        } catch (Exception error) {
            String standardOutput = new String(outputBuffer.toByteArray(), StandardCharsets.UTF_8).trim();
            String errorMessage = error.getMessage();
            return new AiChatCodeOperationResult(
                "SUBMIT_VALIDATION",
                "USER",
                false,
                standardOutput.isEmpty() && errorMessage != null
                    ? Collections.singletonList(errorMessage)
                    : standardOutput.isEmpty() ? Collections.<String>emptyList() : Collections.singletonList(standardOutput),
                standardOutput.isEmpty() ? null : standardOutput,
                null,
                "validation",
                errorMessage,
                lineNumber[0] >= 0 ? Integer.valueOf(lineNumber[0]) : null,
                fingerprint(formulaText));
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
}
