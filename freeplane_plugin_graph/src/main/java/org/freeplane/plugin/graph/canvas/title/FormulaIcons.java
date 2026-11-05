package org.freeplane.plugin.graph.canvas.title;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.scilab.forge.jlatexmath.TeXFormula;
import org.scilab.forge.jlatexmath.TeXIcon;

/**
 * Bounded icon cache. Failures are memoized so a broken macro costs one attempt per instance, and the least
 * recently used icon is evicted beyond capacity because icons are rebuildable.
 */
public final class FormulaIcons implements IconSource {
    public static final int CAPACITY = 512;

    private static final class Key {
        private final String source;
        private final int px;
        private final int style;

        private Key(final String source, final int px, final int style) {
            this.source = source;
            this.px = px;
            this.style = style;
        }

        @Override
        public boolean equals(final Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key)) {
                return false;
            }
            final Key that = (Key) other;
            return px == that.px && style == that.style && source.equals(that.source);
        }

        @Override
        public int hashCode() {
            return Objects.hash(source, Integer.valueOf(px), Integer.valueOf(style));
        }
    }

    private final int capacity;
    private final Map<Key, IconBox> icons;
    private final Set<Key> failures = new LinkedHashSet<Key>();
    private int builds;
    private int hits;
    private int failureCount;

    public FormulaIcons() {
        this(CAPACITY);
    }

    public FormulaIcons(final int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        this.icons = new LinkedHashMap<Key, IconBox>(16, 0.75f, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(final Map.Entry<Key, IconBox> eldest) {
                return size() > FormulaIcons.this.capacity;
            }
        };
    }

    @Override
    public IconBox icon(final String source, final int px, final int style) {
        if (source == null) {
            throw new NullPointerException("source");
        }
        final Key key = new Key(source, px, style);
        if (failures.contains(key)) {
            return null;
        }
        final IconBox cached = icons.get(key);
        if (cached != null) {
            hits++;
            return cached;
        }
        final IconBox built = build(key);
        if (built == null) {
            failures.add(key);
            failureCount++;
            return null;
        }
        icons.put(key, built);
        builds++;
        return built;
    }

    private IconBox build(final Key key) {
        try {
            final TeXFormula formula = new TeXFormula(key.source);
            final TeXIcon icon = formula.new TeXIconBuilder()
                .setStyle(key.style)
                .setSize(key.px)
                .build();
            final double height = icon.getIconHeight();
            final double ascent = icon.getBaseLine() * height;
            return new IconBox(icon, icon.getIconWidth(), height, ascent, height - ascent);
        }
        catch (final RuntimeException rejected) {
            return null;
        }
    }

    public int buildCount() {
        return builds;
    }

    public int hitCount() {
        return hits;
    }

    public int failureCount() {
        return failureCount;
    }

    public int size() {
        return icons.size();
    }
}
