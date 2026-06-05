package org.freeplane.plugin.ai.chat.ui;

import io.github.gitbucket.markedj.Marked;
import io.github.gitbucket.markedj.Options;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.freeplane.core.util.HtmlUtils;
import org.freeplane.core.util.LogUtils;

class ChatMessageRenderer {
    private static final Pattern URL_PATTERN = Pattern.compile("(?i)\\b(?:https?|ftp)://[^\\s\"'<>\\{\\}]+");
    private final Options markdownOptions;

    ChatMessageRenderer() {
        markdownOptions = createMarkdownOptions();
    }

    String renderMessage(String text, boolean renderMarkdown) {
        if (renderMarkdown) {
            return renderMarkdownMessage(text);
        }
        return formatPlainText(text);
    }

    String renderFailureMessage(String text) {
        String normalized = normalizeNewlines(text);
        StringBuilder rendered = new StringBuilder();
        Matcher matcher = URL_PATTERN.matcher(normalized);
        int currentIndex = 0;
        while (matcher.find()) {
            appendEscapedWithLineBreaks(rendered, normalized.substring(currentIndex, matcher.start()));
            String matched = matcher.group();
            String linkText = stripTrailingPunctuation(matched);
            if (linkText.isEmpty()) {
                appendEscapedWithLineBreaks(rendered, matched);
            } else {
                String trailing = matched.substring(linkText.length());
                String escapedLink = HtmlUtils.toXMLEscapedText(linkText);
                rendered.append("<a href=\"")
                    .append(escapedLink)
                    .append("\">")
                    .append(escapedLink)
                    .append("</a>");
                appendEscapedWithLineBreaks(rendered, trailing);
            }
            currentIndex = matcher.end();
        }
        appendEscapedWithLineBreaks(rendered, normalized.substring(currentIndex));
        return rendered.toString();
    }

    private Options createMarkdownOptions() {
        Options options = new Options();
        options.setSafelist(null);
        return options;
    }

    private String renderMarkdownMessage(String text) {
        try {
            String renderedMarkup = Marked.marked(text, markdownOptions);
            if (renderedMarkup == null) {
                return formatPlainText(text);
            }
            return renderedMarkup;
        } catch (RuntimeException exception) {
            LogUtils.severe(exception);
            return formatPlainText(text);
        }
    }

    private String formatPlainText(String text) {
        String normalized = normalizeNewlines(text);
        String escaped = HtmlUtils.toXMLEscapedText(normalized);
        return escaped.replace("\n", "<br>");
    }

    private String normalizeNewlines(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n").replace("\r", "\n");
    }

    private void appendEscapedWithLineBreaks(StringBuilder rendered, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        rendered.append(HtmlUtils.toXMLEscapedText(text).replace("\n", "<br>"));
    }

    private String stripTrailingPunctuation(String text) {
        int end = text.length();
        while (end > 0 && isTrailingPunctuation(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end);
    }

    private boolean isTrailingPunctuation(char character) {
        return character == '.'
            || character == ','
            || character == ';'
            || character == ':'
            || character == '!'
            || character == '?'
            || character == ')'
            || character == ']';
    }
}
