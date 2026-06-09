package org.freeplane.plugin.script;

import groovy.json.JsonException;
import groovy.json.JsonSlurper;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.features.ai.code.CodeStateDiagnostic;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.ai.code.CodeStateField;
import org.freeplane.features.ai.code.CodeStateToken;

public class ScriptInputJsonSupport {
    public static final String ARGUMENTS_VARIABLE_NAME = "args";
    public static final String SAVED_SCRIPT_INPUT_PREFIX = "args_for_";
    private static final Pattern LINE_NUMBER_PATTERN = Pattern.compile("line number\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern COLUMN_NUMBER_PATTERN = Pattern.compile("column number\\s+(\\d+)", Pattern.CASE_INSENSITIVE);

    public static final class ParseResult {
        private final Object argsValue;
        private final CodeStateDiagnostic diagnostic;

        private ParseResult(Object argsValue, CodeStateDiagnostic diagnostic) {
            this.argsValue = argsValue;
            this.diagnostic = diagnostic;
        }

        public Object getArgsValue() {
            return argsValue;
        }

        public CodeStateDiagnostic getDiagnostic() {
            return diagnostic;
        }

        public boolean isSuccessful() {
            return diagnostic == null;
        }
    }

    private static final ConcurrentCache<String, ParseResult> parsedInputs = new ConcurrentCache<String, ParseResult>(
        ScriptInputJsonSupport::cacheSize);

    public static ParseResult parseInputText(String inputText) {
        return parseInputText(inputText, CodeStateToken.fingerprint(inputText));
    }

    public static ParseResult parseInputText(String inputText, String inputFingerprint) {
        if (isBlankInput(inputText)) {
            return new ParseResult(null, null);
        }
        return parsedInputs.computeIfAbsent(inputFingerprint, () -> parseNonBlank(inputText));
    }

    public static boolean isBlankInput(String inputText) {
        return inputText == null || inputText.trim().isEmpty();
    }

    public static String companionAttributeName(String scriptAttributeName) {
        return SAVED_SCRIPT_INPUT_PREFIX + scriptAttributeName;
    }

    public static boolean isCompanionAttributeName(String attributeName) {
        return attributeName != null && attributeName.startsWith(SAVED_SCRIPT_INPUT_PREFIX);
    }

    public static Map<String, Object> boundVariables(Object argsValue) {
        return Collections.<String, Object>singletonMap(ARGUMENTS_VARIABLE_NAME, argsValue);
    }

    public static ExecuteScriptException toExecuteScriptException(CodeStateDiagnostic diagnostic) {
        return new ExecuteScriptException(primaryMessage(diagnostic));
    }

    public static String primaryMessage(CodeStateDiagnostic diagnostic) {
        if (diagnostic == null || diagnostic.getMessage() == null || diagnostic.getMessage().trim().isEmpty()) {
            return "Invalid input JSON.";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(diagnostic.getMessage().trim());
        if (diagnostic.getLine() != null) {
            builder.append(" at line ").append(diagnostic.getLine());
            if (diagnostic.getColumn() != null) {
                builder.append(", column ").append(diagnostic.getColumn());
            }
        }
        return builder.toString();
    }

    private static ParseResult parseNonBlank(String inputText) {
        try {
            return new ParseResult(new JsonSlurper().parseText(inputText), null);
        } catch (JsonException error) {
            return new ParseResult(null, diagnostic(error));
        } catch (RuntimeException error) {
            return new ParseResult(null, new CodeStateDiagnostic(CodeStateField.INPUT_JSON, error.getMessage(), null, null));
        }
    }

    private static CodeStateDiagnostic diagnostic(JsonException error) {
        String message = error.getMessage();
        return new CodeStateDiagnostic(
            CodeStateField.INPUT_JSON,
            message,
            extractNumber(LINE_NUMBER_PATTERN, message),
            extractNumber(COLUMN_NUMBER_PATTERN, message));
    }

    private static Integer extractNumber(Pattern pattern, String message) {
        if (message == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(message);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private static int cacheSize() {
        Controller controller = Controller.getCurrentController();
        ResourceController resourceController = controller == null ? null : controller.getResourceController();
        return resourceController == null ? 200 : resourceController.getIntProperty("compiled_script_cache_size", 200);
    }
}
