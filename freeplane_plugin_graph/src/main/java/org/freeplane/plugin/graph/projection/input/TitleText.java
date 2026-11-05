package org.freeplane.plugin.graph.projection.input;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A node title as a sequence of plain-text and LaTeX-formula runs. Pure value type: no AWT, no engine types, so it
 * can live in {@code projection.input} beside {@link SafeNodeLabel}.
 */
public final class TitleText {
    public interface Run {
    }

    public static final class Plain implements Run {
        private final String text;

        public Plain(final String text) {
            if (text == null) {
                throw new NullPointerException("text");
            }
            this.text = text;
        }

        public String text() {
            return text;
        }

        @Override
        public boolean equals(final Object other) {
            return this == other || other instanceof Plain && text.equals(((Plain) other).text);
        }

        @Override
        public int hashCode() {
            return text.hashCode();
        }

        @Override
        public String toString() {
            return "Plain[" + text + "]";
        }
    }

    public static final class Math implements Run {
        private final String source;
        private final String rawSource;

        public Math(final String source, final String rawSource) {
            if (source == null) {
                throw new NullPointerException("source");
            }
            if (rawSource == null) {
                throw new NullPointerException("rawSource");
            }
            this.source = source;
            this.rawSource = rawSource;
        }

        public String source() {
            return source;
        }

        public String rawSource() {
            return rawSource;
        }

        @Override
        public boolean equals(final Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Math)) {
                return false;
            }
            final Math that = (Math) other;
            return source.equals(that.source) && rawSource.equals(that.rawSource);
        }

        @Override
        public int hashCode() {
            return Objects.hash(source, rawSource);
        }

        @Override
        public String toString() {
            return "Math[" + source + "]";
        }
    }

    private final List<Run> runs;

    private TitleText(final List<Run> runs) {
        this.runs = Collections.unmodifiableList(new ArrayList<Run>(runs));
    }

    public static TitleText plain(final String text) {
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
        return new TitleText(Collections.<Run>singletonList(new Plain(text)));
    }

    public static TitleText of(final List<Run> runs) {
        if (runs == null) {
            throw new NullPointerException("runs");
        }
        if (runs.isEmpty()) {
            throw new IllegalArgumentException("runs must not be empty");
        }
        for (final Run run : runs) {
            if (run == null) {
                throw new NullPointerException("run");
            }
        }
        return new TitleText(runs);
    }

    public List<Run> runs() {
        return runs;
    }

    public boolean hasMath() {
        for (final Run run : runs) {
            if (run instanceof Math) {
                return true;
            }
        }
        return false;
    }

    public String plainSource() {
        final StringBuilder text = new StringBuilder();
        for (final Run run : runs) {
            text.append(run instanceof Math ? ((Math) run).rawSource() : ((Plain) run).text());
        }
        return text.toString();
    }

    @Override
    public boolean equals(final Object other) {
        return this == other || other instanceof TitleText && runs.equals(((TitleText) other).runs);
    }

    @Override
    public int hashCode() {
        return runs.hashCode();
    }

    @Override
    public String toString() {
        return runs.toString();
    }
}
