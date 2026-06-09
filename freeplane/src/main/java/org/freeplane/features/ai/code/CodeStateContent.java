package org.freeplane.features.ai.code;

import java.util.Objects;

public class CodeStateContent {
    private String sourceText;
    private String inputText;

    public CodeStateContent() {
    }

    public CodeStateContent(String sourceText, String inputText) {
        this.sourceText = sourceText;
        this.inputText = inputText;
    }

    public String getSourceText() {
        return sourceText;
    }

    public void setSourceText(String sourceText) {
        this.sourceText = sourceText;
    }

    public String getInputText() {
        return inputText;
    }

    public void setInputText(String inputText) {
        this.inputText = inputText;
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
            && Objects.equals(inputText, that.inputText);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceText, inputText);
    }

    @Override
    public String toString() {
        return "CodeStateContent{"
            + "sourceText='" + sourceText + '\''
            + ", inputText='" + inputText + '\''
            + '}';
    }
}
