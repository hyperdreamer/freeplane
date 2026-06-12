package org.freeplane.features.ai.code;

import java.util.Objects;

public class CodeStateContent {
    private String sourceText;
    private String argumentsJsonText;

    public CodeStateContent() {
    }

    public CodeStateContent(String sourceText, String argumentsJsonText) {
        this.sourceText = sourceText;
        this.argumentsJsonText = argumentsJsonText;
    }

    public String getSourceText() {
        return sourceText;
    }

    public void setSourceText(String sourceText) {
        this.sourceText = sourceText;
    }

    public String getArgumentsJsonText() {
        return argumentsJsonText;
    }

    public void setArgumentsJsonText(String argumentsJsonText) {
        this.argumentsJsonText = argumentsJsonText;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CodeStateContent)) {
            return false;
        }
        CodeStateContent that = (CodeStateContent) other;
        return Objects.equals(sourceText, that.sourceText)
            && Objects.equals(argumentsJsonText, that.argumentsJsonText);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceText, argumentsJsonText);
    }

    @Override
    public String toString() {
        return "CodeStateContent{" 
            + "sourceText='" + sourceText + '\''
            + ", argumentsJsonText='" + argumentsJsonText + '\''
            + '}';
    }
}
