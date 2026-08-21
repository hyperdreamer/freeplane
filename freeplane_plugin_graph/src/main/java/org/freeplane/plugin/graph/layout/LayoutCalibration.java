package org.freeplane.plugin.graph.layout;

public final class LayoutCalibration {
    private static final double CONTAINMENT = 0.15;
    private static final double HIERARCHY = 0.30;
    private static final double SAME_MAP = 1.0;
    private static final LayoutCalibration SPIKE_DEFAULTS =
        new LayoutCalibration(CONTAINMENT, HIERARCHY, SAME_MAP);

    private final double containment;
    private final double hierarchy;
    private final double sameMap;

    private LayoutCalibration(final double containment, final double hierarchy, final double sameMap) {
        this.containment = containment;
        this.hierarchy = hierarchy;
        this.sameMap = sameMap;
    }

    public static LayoutCalibration spikeDefaults() {
        return SPIKE_DEFAULTS;
    }

    public double containment() {
        return containment;
    }

    public double hierarchy() {
        return hierarchy;
    }

    public double sameMap() {
        return sameMap;
    }
}
