package org.freeplane.plugin.ai.tools.content;

import java.util.Locale;
import org.freeplane.core.util.HtmlUtils;
import org.freeplane.features.text.TextController;

public class ContentTypeConverter {
    private static final String LATEX_PREFIX = "\\latex";
    private static final String UNPARSED_LATEX_PREFIX = "\\unparsedlatex";

    public ContentType toContentType(String freeplaneContentType, boolean isFormula, String rawValue) {
        if (isFormula) {
            return ContentType.FORMULA;
        }
        if (freeplaneContentType == null) {
            if (rawValue != null && HtmlUtils.isHtml(rawValue)) {
                return ContentType.HTML;
            }
            return ContentType.PLAIN_TEXT;
        }
        String normalized = freeplaneContentType.trim().toLowerCase(Locale.ROOT);
        if (TextController.CONTENT_TYPE_AUTO.equals(normalized)
            || TextController.CONTENT_TYPE_HTML.equals(normalized)) {
            return ContentType.HTML;
        }
        if ("markdown".equals(normalized)) {
            return ContentType.MARKDOWN;
        }
        if ("latex".equals(normalized)) {
            return ContentType.LATEX;
        }
        return ContentType.PLAIN_TEXT;
    }

    public ContentType toTextContentTypeForNode(String nodeFormat, String rawValue) {
        if (findLatexPrefix(rawValue) != null) {
            return ContentType.LATEX;
        }
        if (isMarkdownFormat(nodeFormat)) {
            return ContentType.MARKDOWN;
        }
        if (isLatexFormat(nodeFormat)) {
            return ContentType.LATEX;
        }
        if (rawValue != null && HtmlUtils.isHtml(rawValue)) {
            return ContentType.HTML;
        }
        return ContentType.PLAIN_TEXT;
    }

    public String findLatexPrefix(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String plainValue = HtmlUtils.htmlToPlain(rawValue);
        if (startsWithPrefix(plainValue, LATEX_PREFIX)) {
            return LATEX_PREFIX;
        }
        if (startsWithPrefix(plainValue, UNPARSED_LATEX_PREFIX)) {
            return UNPARSED_LATEX_PREFIX;
        }
        return null;
    }

    public String stripLatexPrefix(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String plainValue = HtmlUtils.htmlToPlain(rawValue);
        if (startsWithPrefix(plainValue, LATEX_PREFIX)) {
            return plainValue.substring(LATEX_PREFIX.length() + 1);
        }
        if (startsWithPrefix(plainValue, UNPARSED_LATEX_PREFIX)) {
            return plainValue.substring(UNPARSED_LATEX_PREFIX.length() + 1);
        }
        return plainValue;
    }

    private boolean isMarkdownFormat(String nodeFormat) {
        return "markdown".equals(normalizeContentType(nodeFormat));
    }

    private boolean isLatexFormat(String nodeFormat) {
        return "latex".equals(normalizeContentType(nodeFormat));
    }

    private String normalizeContentType(String contentType) {
        return contentType == null ? null : contentType.trim().toLowerCase(Locale.ROOT);
    }

    private boolean startsWithPrefix(String text, String prefix) {
        if (text == null) {
            return false;
        }
        int startLength = prefix.length() + 1;
        return text.length() > startLength
            && text.startsWith(prefix)
            && Character.isWhitespace(text.charAt(startLength - 1));
    }
}
