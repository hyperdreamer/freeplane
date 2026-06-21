package org.freeplane.plugin.script;

import groovy.json.JsonException;
import groovy.json.JsonSlurper;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.ai.code.CodeStateDiagnostic;
import org.freeplane.features.ai.code.CodeStateField;
import org.freeplane.features.ai.code.CodeStateToken;
import org.freeplane.features.mode.Controller;

@SuppressWarnings("removal")
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

    public static ParseResult parseInputText(String argumentsJsonText) {
        return parseInputText(argumentsJsonText, CodeStateToken.fingerprint(argumentsJsonText));
    }

    public static ParseResult parseInputText(String argumentsJsonText, String argumentsFingerprint) {
        if (isBlankInput(argumentsJsonText)) {
            return new ParseResult(null, null);
        }
        return parsedInputs.computeIfAbsent(argumentsFingerprint, () -> parseNonBlank(argumentsJsonText));
    }

    public static boolean isBlankInput(String argumentsJsonText) {
        return argumentsJsonText == null || argumentsJsonText.trim().isEmpty();
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
        if (diagnostic == null) {
            return "Invalid Arguments JSON.";
        }
        String diagnosticMessage = diagnostic.getMessage() == null ? "" : diagnostic.getMessage().trim();
        StringBuilder builder = new StringBuilder();
        String fieldPrefix = fieldPrefix(diagnostic.getField());
        if (fieldPrefix != null) {
            builder.append(fieldPrefix);
        }
        if (!diagnosticMessage.isEmpty()) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(diagnosticMessage);
        }
        if (diagnostic.getLine() != null) {
            builder.append(" at line ").append(diagnostic.getLine());
            if (diagnostic.getColumn() != null) {
                builder.append(", column ").append(diagnostic.getColumn());
            }
        }
        return builder.length() == 0 ? "Invalid Arguments JSON." : builder.toString();
    }

    private static String fieldPrefix(CodeStateField field) {
        if (field == CodeStateField.ARGUMENTS_JSON) {
            return fieldDisplayName(field) + " is invalid.";
        }
        return null;
    }

    private static String fieldDisplayName(CodeStateField field) {
        Controller controller = Controller.getCurrentController();
        if (field == CodeStateField.ARGUMENTS_JSON) {
            return controller == null
                ? "Arguments JSON"
                : TextUtils.getText("ai_owned_script_dialog_input_json", "Arguments JSON");
        }
        if (field == CodeStateField.SOURCE_TEXT) {
            return controller == null ? "Code" : TextUtils.getText("ai_owned_script_dialog_code", "Code");
        }
        return "Input";
    }

    private static ParseResult parseNonBlank(String argumentsJsonText) {
        ClassLoader oldContextClassLoader = getContextClassLoader();
        try {
            setContextClassLoader(JsonSlurper.class.getClassLoader());
            return new ParseResult(new JsonSlurper().parseText(argumentsJsonText), null);
        } catch (JsonException error) {
            return new ParseResult(null, diagnostic(error));
        } catch (RuntimeException error) {
            return new ParseResult(null, new CodeStateDiagnostic(CodeStateField.ARGUMENTS_JSON, error.getMessage(), null, null));
        }
        finally {
            setContextClassLoader(oldContextClassLoader);
        }
    }

    private static ClassLoader getContextClassLoader() {
        return AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
            @Override
            public ClassLoader run() {
                return Thread.currentThread().getContextClassLoader();
            }
        });
    }

    private static void setContextClassLoader(final ClassLoader classLoader) {
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            @Override
            public Void run() {
                Thread.currentThread().setContextClassLoader(classLoader);
                return null;
            }
        });
    }

    private static CodeStateDiagnostic diagnostic(JsonException error) {
        String message = error.getMessage();
        return new CodeStateDiagnostic(
            CodeStateField.ARGUMENTS_JSON,
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
