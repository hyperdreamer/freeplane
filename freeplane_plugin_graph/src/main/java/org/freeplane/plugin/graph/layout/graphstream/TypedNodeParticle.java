package org.freeplane.plugin.graph.layout.graphstream;

import org.graphstream.ui.geom.Vector3;
import org.graphstream.ui.layout.springbox.implementations.SpringBoxNodeParticle;

final class TypedNodeParticle extends SpringBoxNodeParticle {
    private double separationRadius = 8.0;
    private boolean activelyPinned;

    TypedNodeParticle(final TypedSpringBox box, final String id) {
        super(box, id);
    }

    void configure(final double radius, final boolean pinned) {
        separationRadius = radius;
        activelyPinned = pinned;
    }

    double separationRadius() {
        return separationRadius;
    }

    @Override
    protected void attraction(final Vector3 displacement) {
        if (!activelyPinned) {
            super.attraction(displacement);
        }
    }
}
