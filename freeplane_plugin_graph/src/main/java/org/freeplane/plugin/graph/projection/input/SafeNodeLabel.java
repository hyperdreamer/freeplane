package org.freeplane.plugin.graph.projection.input;

import java.util.Objects;

public final class SafeNodeLabel {
    private final String fullText;
    private final String displayText;
    private final TitleText title;

    private SafeNodeLabel(final String fullText, final String displayText, final TitleText title) {
        this.fullText = requireText(fullText, "full");
        this.displayText = requireText(displayText, "display");
        this.title = Objects.requireNonNull(title, "title");
    }

    public static SafeNodeLabel of(final String full, final String display) {
        return new SafeNodeLabel(full, display, TitleText.plain(display));
    }

    public static SafeNodeLabel of(final String full, final String display, final TitleText title) {
        return new SafeNodeLabel(full, display, title);
    }

    public TitleText title() {
        return title;
    }

    public String fullText() {
        return fullText;
    }

    public String displayText() {
        return displayText;
    }

    private static String requireText(final String value, final String name) {
        Objects.requireNonNull(value, name);
        if (value.isEmpty()) {
            throw new IllegalArgumentException(name + " text must not be empty");
        }
        return value;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SafeNodeLabel)) {
            return false;
        }
        final SafeNodeLabel that = (SafeNodeLabel) other;
        return fullText.equals(that.fullText) && displayText.equals(that.displayText);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fullText, displayText);
    }

    @Override
    public String toString() {
        return "SafeNodeLabel{}";
    }
}
