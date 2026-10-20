package org.freeplane.plugin.graph.canvas;

import java.awt.Font;
import java.util.Objects;

public final class LabelFonts {
    private final Font full;
    private final Font dense;
    private final Font emphatic;

    private LabelFonts(final Font full, final Font dense, final Font emphatic) {
        this.full = Objects.requireNonNull(full, "full");
        this.dense = Objects.requireNonNull(dense, "dense");
        this.emphatic = Objects.requireNonNull(emphatic, "emphatic");
    }

    public static LabelFonts from(final GraphTheme theme) {
        final GraphTheme value = Objects.requireNonNull(theme, "theme");
        return new LabelFonts(value.labelFont(), value.denseLabelFont(), value.emphaticLabelFont());
    }

    public Font full() {
        return full;
    }

    public Font dense() {
        return dense;
    }

    public Font emphatic() {
        return emphatic;
    }
}
