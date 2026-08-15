package org.freeplane.plugin.graph.layout.graphstream;

import org.graphstream.ui.geom.Vector3;
import org.graphstream.ui.layout.springbox.implementations.SpringBoxNodeParticle;

final class TypedNodeParticle extends SpringBoxNodeParticle {
    private final TypedSpringBox typedBox;
    private final Vector3 rawCrossMapAttraction = new Vector3();
    private final Vector3 rawBudgetedRepulsion = new Vector3();
    private final Vector3 preScaleDisplacement = new Vector3();
    private double separationRadius = 8.0;
    private boolean activelyPinned;

    TypedNodeParticle(final TypedSpringBox box, final String id) {
        super(box, id);
        typedBox = box;
    }

    void configure(final double radius, final boolean pinned) {
        separationRadius = radius;
        activelyPinned = pinned;
    }

    double separationRadius() {
        return separationRadius;
    }

    @Override
    protected void repulsionN2(final Vector3 displacement) {
        final Vector3 before = new Vector3(disp);
        super.repulsionN2(displacement);
        scaleRepulsion(before);
    }

    @Override
    protected void repulsionNLogN(final Vector3 displacement) {
        final Vector3 before = new Vector3(disp);
        super.repulsionNLogN(displacement);
        scaleRepulsion(before);
    }

    @Override
    protected void attraction(final Vector3 displacement) {
        displacement.fill(0.0);
        rawCrossMapAttraction.fill(0.0);
        if (!activelyPinned) {
            typedBox.addTypedAttraction(this, displacement, rawCrossMapAttraction);
            disp.add(displacement);
            preScaleDisplacement.copy(disp);
        }
    }

    @Override
    public void move(final int time) {
        rawCrossMapAttraction.fill(0.0);
        rawBudgetedRepulsion.fill(0.0);
        preScaleDisplacement.fill(0.0);
        super.move(time);
        if (!frozen) {
            final Vector3 budgetedDisplacement = new Vector3(rawBudgetedRepulsion);
            budgetedDisplacement.add(rawCrossMapAttraction);
            final double force = typedBox.getForce();
            final double unboundedLength = preScaleDisplacement.length() * Math.abs(force);
            final double globalScale = unboundedLength == 0.0 ? 0.0 : len / unboundedLength;
            budgetedDisplacement.scalarMult(force * globalScale);
            if (!budgetedDisplacement.isZero()) {
                // Keep native repulsion and cross-map springs in one per-particle budget.
                final Vector3 ordinary = new Vector3(disp);
                ordinary.sub(budgetedDisplacement);
                clamp(budgetedDisplacement, TypedSpringBox.CROSS_MAP_DISPLACEMENT_LIMIT);
                disp.copy(ordinary);
                disp.add(budgetedDisplacement);
            }
            len = disp.length();
        }
    }

    private void scaleRepulsion(final Vector3 before) {
        final Vector3 repulsion = new Vector3(disp);
        repulsion.sub(before);
        if (separationRadius != typedBox.baseSeparationRadius()) {
            repulsion.scalarMult(separationRadius / typedBox.baseSeparationRadius());
            disp.copy(before);
            disp.add(repulsion);
        }
        if (typedBox.hasCrossMapLink(this)) {
            rawBudgetedRepulsion.add(repulsion);
        }
    }

    private static void clamp(final Vector3 value, final double maximum) {
        final double magnitude = value.length();
        if (magnitude > maximum) {
            value.scalarMult(maximum / magnitude);
        }
    }
}
