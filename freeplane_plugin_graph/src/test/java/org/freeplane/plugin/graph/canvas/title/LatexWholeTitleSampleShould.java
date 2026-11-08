package org.freeplane.plugin.graph.canvas.title;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Font;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.freeplane.features.map.NodeModel;
import org.freeplane.plugin.graph.adapter.SafeNodeLabelExtractor;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.TitleText;
import org.junit.Test;

public class LatexWholeTitleSampleShould {
    @Test
    public void productionTotalsMatchTheRecordedSample() throws IOException {
        final Path sample = Paths.get("doc/latex-whole-title/titles-sample.json");
        final List<String> bodies = readStringArray(
            new String(Files.readAllBytes(sample), StandardCharsets.UTF_8));
        assertThat(bodies).hasSize(250);

        final String dumpPath = System.getenv("LATEX_PROBE_DUMP");
        final Path dumpTarget = dumpPath == null ? null : Paths.get(dumpPath);
        if (dumpTarget != null) {
            assertThat(dumpTarget.isAbsolute()).isTrue();
        }
        final List<String> dumpRows = dumpTarget == null ? null : new ArrayList<String>(bodies.size());
        final SafeNodeLabelExtractor extractor = new SafeNodeLabelExtractor();
        final TitleMeasurer measurer = new TitleMeasurer(new FormulaIcons(), 1000);
        final Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        measurer.beginPass(1L);

        for (final String rawBody : bodies) {
            final SafeNodeLabel label = extractor.extract(new NodeModel("\\latex " + rawBody, null));
            assertThat(label.title().runs()).hasSize(1);
            assertThat(label.title().runs().get(0)).isInstanceOf(TitleText.Math.class);
            final TitleText.Math math = (TitleText.Math) label.title().runs().get(0);
            final String normalizedBody = normalizeWhitespace(rawBody);
            final int codePointCount = normalizedBody.codePointCount(0, normalizedBody.length());
            final String expectedDisplayText = codePointCount <= SafeNodeLabelExtractor.MAX_DISPLAY_CODE_POINTS
                ? normalizedBody
                : normalizedBody.substring(0, normalizedBody.offsetByCodePoints(0,
                    SafeNodeLabelExtractor.MAX_DISPLAY_CODE_POINTS - 3)) + "...";
            assertThat(label.displayText()).isEqualTo(expectedDisplayText);
            final String displayText = label.displayText();
            final String expectedSource = displayText.indexOf('$') < 0
                ? "\\text{" + displayText + "}" : displayText;
            assertThat(math.source()).isEqualTo(expectedSource);

            final MeasuredTitle measured = measurer.measure(label.title(), font);
            if (dumpRows != null) {
                dumpRows.add(displayText + "\t" + measured.availability() + "\t" + measured.width() + "\t"
                    + measured.runs().size());
            }
        }
        assertThat(measurer.deferredInLastPass()).isZero();

        if (dumpTarget != null) {
            final Path parent = dumpTarget.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(dumpTarget, dumpRows, StandardCharsets.UTF_8);
        }
    }

    private static String normalizeWhitespace(final String value) {
        final StringBuilder normalized = new StringBuilder(value.length());
        boolean pendingSpace = false;
        for (int index = 0; index < value.length();) {
            final int codePoint = value.codePointAt(index);
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                pendingSpace = normalized.length() > 0;
            }
            else {
                if (pendingSpace) {
                    normalized.append(' ');
                    pendingSpace = false;
                }
                normalized.appendCodePoint(codePoint);
            }
            index += Character.charCount(codePoint);
        }
        return normalized.toString();
    }

    private static List<String> readStringArray(final String json) {
        return new JsonStringArrayReader(json).read();
    }

    private static final class JsonStringArrayReader {
        private final String source;
        private int index;

        private JsonStringArrayReader(final String source) {
            if (source == null) {
                throw new NullPointerException("source");
            }
            this.source = source;
        }

        private List<String> read() {
            skipWhitespace();
            expect('[');
            skipWhitespace();
            final List<String> values = new ArrayList<String>();
            if (consume(']')) {
                finish();
                return values;
            }
            while (true) {
                skipWhitespace();
                if (index >= source.length() || source.charAt(index) != '"') {
                    throw malformed("Expected a string array entry");
                }
                values.add(readString());
                skipWhitespace();
                if (consume(']')) {
                    break;
                }
                expect(',');
            }
            finish();
            return values;
        }

        private String readString() {
            expect('"');
            final StringBuilder value = new StringBuilder();
            while (index < source.length()) {
                final char current = source.charAt(index++);
                if (current == '"') {
                    return value.toString();
                }
                if (current == '\\') {
                    if (index >= source.length()) {
                        throw malformed("Unterminated escape sequence");
                    }
                    appendEscape(value, source.charAt(index++));
                }
                else {
                    if (current < 0x20) {
                        throw malformed("Unescaped control character");
                    }
                    value.append(current);
                }
            }
            throw malformed("Unterminated string");
        }

        private void appendEscape(final StringBuilder value, final char escaped) {
            switch (escaped) {
            case '"':
                value.append('"');
                break;
            case '\\':
                value.append('\\');
                break;
            case '/':
                value.append('/');
                break;
            case 'b':
                value.append('\b');
                break;
            case 'f':
                value.append('\f');
                break;
            case 'n':
                value.append('\n');
                break;
            case 'r':
                value.append('\r');
                break;
            case 't':
                value.append('\t');
                break;
            case 'u':
                value.append(readUnicodeEscape());
                break;
            default:
                throw malformed("Invalid escape sequence");
            }
        }

        private char readUnicodeEscape() {
            int value = 0;
            for (int digitIndex = 0; digitIndex < 4; digitIndex++) {
                if (index >= source.length()) {
                    throw malformed("Incomplete unicode escape");
                }
                final int digit = Character.digit(source.charAt(index++), 16);
                if (digit < 0) {
                    throw malformed("Invalid unicode escape");
                }
                value = (value << 4) | digit;
            }
            return (char) value;
        }

        private void skipWhitespace() {
            while (index < source.length()) {
                final char current = source.charAt(index);
                if (current != ' ' && current != '\t' && current != '\n' && current != '\r') {
                    return;
                }
                index++;
            }
        }

        private boolean consume(final char expected) {
            if (index < source.length() && source.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void expect(final char expected) {
            if (!consume(expected)) {
                throw malformed("Expected '" + expected + "'");
            }
        }

        private void finish() {
            skipWhitespace();
            if (index != source.length()) {
                throw malformed("Trailing data");
            }
        }

        private IllegalArgumentException malformed(final String reason) {
            return new IllegalArgumentException(reason + " at index " + index);
        }
    }
}
