package org.freeplane.plugin.script;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.freeplane.features.ai.code.CodeStateField;
import org.junit.Test;

public class ScriptInputJsonSupportTest {

    @Test
    public void blankInputBindsArgsToNull() {
        ScriptInputJsonSupport.ParseResult result = ScriptInputJsonSupport.parseInputText("   ");

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getArgsValue()).isNull();
    }

    @Test
    public void objectInputParsesToMap() {
        ScriptInputJsonSupport.ParseResult result = ScriptInputJsonSupport.parseInputText("{\"message\":\"hello\"}");

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getArgsValue()).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) result.getArgsValue()).get("message")).isEqualTo("hello");
    }

    @Test
    public void invalidInputReportsInputJsonDiagnostic() {
        ScriptInputJsonSupport.ParseResult result = ScriptInputJsonSupport.parseInputText("{");

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getDiagnostic().getField()).isEqualTo(CodeStateField.INPUT_JSON);
        assertThat(result.getDiagnostic().getMessage()).isNotBlank();
    }
}
