package org.freeplane.plugin.graph.adapter;

import java.net.URI;
import java.util.List;
import java.util.Objects;

import org.freeplane.core.util.HtmlUtils;
import org.freeplane.core.util.Hyperlink;
import org.freeplane.features.format.FormattedFormula;
import org.freeplane.features.format.IFormattedObject;
import org.freeplane.features.icon.IconDescription;
import org.freeplane.features.icon.NamedIcon;
import org.freeplane.features.link.NodeLinks;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.nodestyle.NodeStyleModel;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;

public final class SafeNodeLabelExtractor {
    public static final int MAX_DISPLAY_CODE_POINTS = 80;

    public SafeNodeLabel extract(final NodeModel reachableNode) {
        Objects.requireNonNull(reachableNode, "reachableNode");
        final String full = firstNonEmpty(
            normalizedRawContent(reachableNode),
            normalizedDirectLink(reachableNode),
            normalizedDirectIcon(reachableNode),
            "Node");
        return SafeNodeLabel.of(full, displayText(full));
    }

    private String normalizedRawContent(final NodeModel node) {
        final Object rawContent = node.getUserObject();
        final boolean directText = rawContent instanceof CharSequence;
        final String source = sourceText(rawContent);
        if (source == null) {
            return "";
        }
        final String localFormat = NodeStyleModel.getNodeFormat(node);
        final String converted = directText ? convertHtml(source) : source;
        return normalizeWhitespace(removeLatexPrefix(converted, localFormat));
    }

    private String sourceText(final Object rawContent) {
        if (rawContent == null) {
            return null;
        }
        if (rawContent instanceof FormattedFormula) {
            return ((FormattedFormula) rawContent).getObject();
        }
        if (rawContent instanceof IFormattedObject) {
            final Object object = ((IFormattedObject) rawContent).getObject();
            if (isScalar(object)) {
                return object.toString();
            }
            return rawContent.toString();
        }
        if (isScalar(rawContent)) {
            return rawContent.toString();
        }
        return rawContent.toString();
    }

    private boolean isScalar(final Object value) {
        return value instanceof CharSequence || value instanceof Number || value instanceof Boolean
            || value instanceof Character || value instanceof URI || value instanceof Hyperlink;
    }

    private String convertHtml(final String source) {
        return HtmlUtils.isHtml(source) ? HtmlUtils.htmlToPlain(source) : source;
    }

    private String removeLatexPrefix(final String source, final String localFormat) {
        if ("latexPatternFormat".equals(localFormat) || "unparsedLatexPatternFormat".equals(localFormat)
                || "markdownPatternFormat".equals(localFormat)) {
            return source;
        }
        if (source.startsWith("\\latex") && hasWhitespaceAfter(source, "\\latex".length())) {
            return source.substring("\\latex".length());
        }
        if (source.startsWith("\\unparsedlatex") && hasWhitespaceAfter(source, "\\unparsedlatex".length())) {
            return source.substring("\\unparsedlatex".length());
        }
        return source;
    }

    private boolean hasWhitespaceAfter(final String source, final int prefixLength) {
        return source.length() > prefixLength && isWhitespace(source.codePointAt(prefixLength));
    }

    private String normalizedDirectLink(final NodeModel node) {
        final Hyperlink link = NodeLinks.getLink(node);
        return link == null ? "" : normalizeWhitespace(link.toString());
    }

    private String normalizedDirectIcon(final NodeModel node) {
        final List<NamedIcon> icons = node.getIcons();
        if (icons.isEmpty()) {
            return "";
        }
        final NamedIcon icon = icons.get(0);
        if (icon instanceof IconDescription) {
            final String description = ((IconDescription) icon).getTranslatedDescription();
            final String normalizedDescription = normalizeWhitespace(description);
            if (!normalizedDescription.isEmpty()) {
                return normalizedDescription;
            }
        }
        return normalizeWhitespace(icon.getName());
    }

    private String firstNonEmpty(final String first, final String second, final String third, final String fallback) {
        if (!first.isEmpty()) {
            return first;
        }
        if (!second.isEmpty()) {
            return second;
        }
        if (!third.isEmpty()) {
            return third;
        }
        return fallback;
    }

    private String normalizeWhitespace(final String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        final StringBuilder normalized = new StringBuilder(value.length());
        boolean pendingSpace = false;
        for (int index = 0; index < value.length();) {
            final int codePoint = value.codePointAt(index);
            if (isWhitespace(codePoint)) {
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

    private boolean isWhitespace(final int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    private String displayText(final String fullText) {
        final int codePointCount = fullText.codePointCount(0, fullText.length());
        if (codePointCount <= MAX_DISPLAY_CODE_POINTS) {
            return fullText;
        }
        final int prefixEnd = fullText.offsetByCodePoints(0, MAX_DISPLAY_CODE_POINTS - 3);
        return fullText.substring(0, prefixEnd) + "...";
    }
}
