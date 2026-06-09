package org.freeplane.features.ai.code;

public class CodeStateDiagnostic {
    private final CodeStateField field;
    private final String message;
    private final Integer line;
    private final Integer column;

    public CodeStateDiagnostic(CodeStateField field, String message, Integer line, Integer column) {
        this.field = field;
        this.message = message;
        this.line = line;
        this.column = column;
    }

    public CodeStateField getField() {
        return field;
    }

    public String getMessage() {
        return message;
    }

    public Integer getLine() {
        return line;
    }

    public Integer getColumn() {
        return column;
    }
}
