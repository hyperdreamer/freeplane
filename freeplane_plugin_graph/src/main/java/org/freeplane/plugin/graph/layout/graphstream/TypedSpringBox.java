package org.freeplane.plugin.graph.layout.graphstream;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.layout.LayoutCalibration;
import org.graphstream.ui.geom.Vector3;
import org.graphstream.ui.layout.springbox.EdgeSpring;
import org.graphstream.ui.layout.springbox.NodeParticle;
import org.graphstream.ui.layout.springbox.implementations.SpringBox;
import org.miv.pherd.geom.Point3;

final class TypedSpringBox extends SpringBox {
    static final double CROSS_MAP_DISPLACEMENT_LIMIT = 0.005;
    static final double REST_LENGTH = 24.0;
    private static final double ATTRACTION_FACTOR = 0.05;
    private static final double REPULSION_FACTOR = 16.0;
    private static final double BASE_SEPARATION_RADIUS = 8.0;
    private static final double BOUNDARY_REPULSION_FACTOR = 0.5;

    private final LayoutCalibration calibration;
    private final Map<String, TypedNodeParticle> typedParticles = new LinkedHashMap<String, TypedNodeParticle>();
    private final Map<String, GraphStreamLayoutEngine.ForceLink> typedLinks =
        new LinkedHashMap<String, GraphStreamLayoutEngine.ForceLink>();

    TypedSpringBox(final LayoutCalibration calibration, final Random random) {
        super(false, random);
        this.calibration = calibration;
        setQuality(0.10);
        K2 = REPULSION_FACTOR;
    }

    @Override
    protected void chooseNodePosition(final NodeParticle first, final NodeParticle second) {
        // Keep the engine's deterministic seeded positions. The default
        // implementation teleports a degree-1 endpoint onto its already-connected
        // neighbour at edge insertion, collapsing freshly seeded particles into a pile.
    }

    @Override
    public TypedNodeParticle newNodeParticle(final String id) {
        final TypedNodeParticle particle = new TypedNodeParticle(this, id);
        typedParticles.put(id, particle);
        return particle;
    }

    void configureParticle(final String id, final double radius, final boolean pinned) {
        final TypedNodeParticle particle = typedParticles.get(id);
        if (particle != null) {
            particle.configure(radius, pinned);
            freezeNode(id, pinned);
        }
    }

    void setParticlePosition(final String id, final double x, final double y) {
        moveNode(id, x, y, 0.0);
    }

    void registerLink(final String edgeId, final GraphStreamLayoutEngine.ForceLink link) {
        typedLinks.put(edgeId, link);
    }

    void clearLinks() {
        typedLinks.clear();
    }

    void forgetParticle(final String id) {
        typedParticles.remove(id);
    }

    LayoutPoint positionOf(final String id) {
        final TypedNodeParticle particle = typedParticles.get(id);
        if (particle == null) {
            throw new IllegalStateException("Unknown layout particle " + id);
        }
        final Point3 position = particle.getPosition();
        return LayoutPoint.of(position.x, position.y);
    }

    void addTypedAttraction(final TypedNodeParticle particle, final Vector3 displacement,
            final Vector3 crossMapDisplacement) {
        for (final EdgeSpring edge : particle.getEdges()) {
            if (edge.ignored) {
                continue;
            }
            final GraphStreamLayoutEngine.ForceLink link = typedLinks.get(edge.id);
            if (link == null) {
                continue;
            }
            final NodeParticle opposite = edge.getOpposite(particle);
            final Point3 position = opposite.getPosition();
            final Point3 ownPosition = particle.getPosition();
            double x = position.x - ownPosition.x;
            double y = position.y - ownPosition.y;
            double distance = Math.sqrt(x * x + y * y);
            if (distance == 0.0) {
                x = particle.getId().toString().compareTo(opposite.getId().toString()) < 0 ? 1.0 : -1.0;
                y = 0.0;
                distance = 1.0;
            }
            final double magnitude = (distance - link.restLength) * ATTRACTION_FACTOR * multiplier(link.kind);
            final double xContribution = x / distance * magnitude;
            final double yContribution = y / distance * magnitude;
            displacement.set(0, displacement.at(0) + xContribution);
            displacement.set(1, displacement.at(1) + yContribution);
            if (link.crossMap) {
                crossMapDisplacement.set(0, crossMapDisplacement.at(0) + xContribution);
                crossMapDisplacement.set(1, crossMapDisplacement.at(1) + yContribution);
            }
        }
    }

    boolean hasCrossMapLink(final TypedNodeParticle particle) {
        for (final EdgeSpring edge : particle.getEdges()) {
            final GraphStreamLayoutEngine.ForceLink link = typedLinks.get(edge.id);
            if (link != null && link.crossMap) {
                return true;
            }
        }
        return false;
    }

    double multiplier(final GraphStreamLayoutEngine.ForceKind kind) {
        if (kind == GraphStreamLayoutEngine.ForceKind.CONTAINMENT) {
            return calibration.containment();
        }
        if (kind == GraphStreamLayoutEngine.ForceKind.HIERARCHY) {
            return calibration.hierarchy();
        }
        return calibration.sameMap();
    }

    double baseSeparationRadius() {
        return BASE_SEPARATION_RADIUS;
    }

    void addBoundaryRepulsion(final TypedNodeParticle particle, final Vector3 displacement) {
        for (final Map.Entry<String, TypedNodeParticle> entry : typedParticles.entrySet()) {
            final TypedNodeParticle other = entry.getValue();
            if (other == particle) {
                continue;
            }
            final Point3 own = particle.getPosition();
            final Point3 position = other.getPosition();
            double dx = own.x - position.x;
            double dy = own.y - position.y;
            double distance = Math.sqrt(dx * dx + dy * dy);
            if (distance == 0.0) {
                dx = particle.getId().toString().compareTo(other.getId().toString()) < 0 ? 1.0 : -1.0;
                dy = 0.0;
                distance = 1.0;
            }
            final double extent = particle.boundaryRadius() + other.boundaryRadius()
                + GraphStreamLayoutEngine.BoundarySizes.SIBLING_GAP;
            final double penetration = extent - distance;
            if (penetration <= 0.0) {
                continue;
            }
            final double force = penetration * BOUNDARY_REPULSION_FACTOR;
            displacement.set(0, displacement.at(0) + dx / distance * force);
            displacement.set(1, displacement.at(1) + dy / distance * force);
        }
    }
}
