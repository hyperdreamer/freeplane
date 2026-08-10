package org.freeplane.plugin.graph.projection.input;

import java.util.Objects;

public final class SafeNodeLabel {
    private final String fullText;
    private final String displayText;

    private SafeNodeLabel(final String fullText, final String displayText) {
        this.fullText = requireText(fullText, "full");
        this.displayText = requireText(displayText, "display");
    }

    public static SafeNodeLabel of(final String full, final String display) {
        return new SafeNodeLabel(full, display);
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
