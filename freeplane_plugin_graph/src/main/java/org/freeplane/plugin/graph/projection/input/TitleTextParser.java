package org.freeplane.plugin.graph.projection.input;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Splits a node title into plain-text and formula runs. Total: never throws for non-null input.
 *
 * <p>Rules (see the technical specification, section 5.1): empty input is one empty plain run; a whole-title formula
 * keeps the caller's text verbatim as its raw fallback; {@code \$} is a literal dollar, {@code \\} a literal
 * backslash, and every other backslash pair is preserved as typed; escaped braces never change delimiter depth; and
 * a single unmatched delimiter rolls the whole title back to plain text so no partial formula can survive.
 */
public final class TitleTextParser {
    private TitleTextParser() {
    }

    public static TitleText parse(final String source, final boolean wholeTextIsMath) {
        if (source == null) {
            throw new NullPointerException("source");
        }
        if (source.isEmpty()) {
            return TitleText.plain("");
        }
        if (wholeTextIsMath) {
            return wholeTitle(source);
        }
        return scan(source);
    }

    private static TitleText wholeTitle(final String source) {
        final String body = stripSingleDollarPair(source);
        return TitleText.of(Collections.<TitleText.Run>singletonList(new TitleText.Math(body, source)));
    }

    private static String stripSingleDollarPair(final String source) {
        if (source.length() < 2 || source.charAt(0) != '$' || source.charAt(source.length() - 1) != '$') {
            return source;
        }
        if (source.startsWith("$$") || source.endsWith("$$")) {
            return source;
        }
        return source.substring(1, source.length() - 1);
    }

    private static TitleText scan(final String source) {
        final StringBuilder plain = new StringBuilder();
        final List<TitleText.Run> runs = new ArrayList<TitleText.Run>();
        int index = 0;
        while (index < source.length()) {
            final char current = source.charAt(index);
            if (current == '\\') {
                index = appendEscaped(source, index, plain);
                continue;
            }
            if (current != '$') {
                plain.append(current);
                index++;
                continue;
            }
            if (source.startsWith("$$", index)) {
                final int close = findDoubleDelimiter(source, index + 2);
                if (close < 0) {
                    return TitleText.plain(source);
                }
                flush(plain, runs);
                runs.add(new TitleText.Math(source.substring(index + 2, close), source.substring(index, close + 2)));
                index = close + 2;
                continue;
            }
            final int close = findSingleDelimiter(source, index + 1);
            if (close < 0) {
                return TitleText.plain(source);
            }
            flush(plain, runs);
            runs.add(new TitleText.Math(source.substring(index + 1, close), source.substring(index, close + 1)));
            index = close + 1;
        }
        flush(plain, runs);
        if (runs.isEmpty()) {
            return TitleText.plain(source);
        }
        return TitleText.of(runs);
    }

    private static int appendEscaped(final String source, final int backslash, final StringBuilder plain) {
        if (backslash + 1 >= source.length()) {
            plain.append('\\');
            return backslash + 1;
        }
        final char escaped = source.charAt(backslash + 1);
        if (escaped == '$' || escaped == '\\') {
            plain.append(escaped);
        }
        else {
            plain.append('\\').append(escaped);
        }
        return backslash + 2;
    }

    private static int findSingleDelimiter(final String source, final int from) {
        int depth = 0;
        int index = from;
        while (index < source.length()) {
            final char current = source.charAt(index);
            if (current == '\\') {
                index += 2;
                continue;
            }
            if (current == '{') {
                depth++;
            }
            else if (current == '}') {
                if (depth > 0) {
                    depth--;
                }
            }
            else if (current == '$' && depth == 0) {
                return index;
            }
            index++;
        }
        return -1;
    }

    private static int findDoubleDelimiter(final String source, final int from) {
        int index = from;
        while (index < source.length()) {
            if (source.charAt(index) == '\\') {
                index += 2;
                continue;
            }
            if (source.startsWith("$$", index)) {
                return index;
            }
            index++;
        }
        return -1;
    }

    private static void flush(final StringBuilder plain, final List<TitleText.Run> runs) {
        if (plain.length() > 0) {
            runs.add(new TitleText.Plain(plain.toString()));
            plain.setLength(0);
        }
    }
}
