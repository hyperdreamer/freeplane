package org.freeplane.plugin.ai.tools.utilities;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import org.freeplane.plugin.ai.tools.read.ContextSection;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ToolCallSummaryFormatterTest {
    @Test
    public void sanitizeValue_replacesLineBreaksAndTrims() {
        String input = "  line1\r\nline2\nline3\r  ";

        String result = ToolCallSummaryFormatter.sanitizeValue(input);

        assertThat(result).isEqualTo("line1 line2 line3");
    }

    @Test
    public void sanitizeValue_truncatesLongText() {
        StringBuilder longTextBuilder = new StringBuilder();
        for (int index = 0; index < 200; index += 1) {
            longTextBuilder.append('a');
        }

        String result = ToolCallSummaryFormatter.sanitizeValue(longTextBuilder.toString());

        assertThat(result).endsWith("...");
        assertThat(result.length()).isEqualTo(160);
    }

    @Test
    public void joinEnumValues_returnsCommaSeparatedNames() {
        String result = ToolCallSummaryFormatter.joinEnumValues(EnumSet.of(
            ContextSection.BREADCRUMB_PATH, ContextSection.PARENT_SUMMARY));

        assertThat(result).isEqualTo("BREADCRUMB_PATH,PARENT_SUMMARY");
    }

    @Test
    public void joinEnumValues_returnsEmptyForNullOrEmpty() {
        assertThat(ToolCallSummaryFormatter.joinEnumValues(null)).isEmpty();
        assertThat(ToolCallSummaryFormatter.joinEnumValues(Collections.emptySet())).isEmpty();
    }

    @Test
    public void joinTextValues_skipsEmptyValues() {
        String result = ToolCallSummaryFormatter.joinTextValues(Arrays.asList("Alpha", "", null, "Beta"), "; ");

        assertThat(result).isEqualTo("Alpha; Beta");
    }
}
