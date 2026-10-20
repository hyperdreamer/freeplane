package org.freeplane.plugin.graph.canvas;

import java.util.Objects;

import org.freeplane.plugin.graph.geometry.LayoutPoint;

public final class LeaderLine {
    private final LayoutPoint start;
    private final LayoutPoint end;

    LeaderLine(final LayoutPoint start, final LayoutPoint end) {
        final LayoutPoint checkedStart = Objects.requireNonNull(start, "start");
        final LayoutPoint checkedEnd = Objects.requireNonNull(end, "end");
        if (!Double.isFinite(checkedStart.x()) || !Double.isFinite(checkedStart.y())
                || !Double.isFinite(checkedEnd.x()) || !Double.isFinite(checkedEnd.y())) {
            throw new IllegalArgumentException("Leader line coordinates must be finite");
        }
        if (checkedStart.equals(checkedEnd)) {
            throw new IllegalArgumentException("Leader line endpoints must differ");
        }
        this.start = checkedStart;
        this.end = checkedEnd;
    }

    public LayoutPoint start() {
        return start;
    }

    public LayoutPoint end() {
        return end;
    }
}
