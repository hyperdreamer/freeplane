package org.freeplane.plugin.graph.canvas.title;

/** Supplies measured icons for formula sources. Implementations may cache; callers may stub. */
public interface IconSource {
    /** Returns the measured icon for a formula body, or null when the engine rejects the source. */
    IconBox icon(String source, int px, int style);
}
