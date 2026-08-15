package org.freeplane.plugin.graph.layout.graphstream;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.freeplane.plugin.graph.layout.LayoutCalibration;
import org.graphstream.ui.layout.springbox.NodeParticle;
import org.graphstream.ui.layout.springbox.implementations.SpringBox;

final class TypedSpringBox extends SpringBox {
    private static final double CROSS_MAP_DISPLACEMENT_LIMIT = 0.005;
    private static final double REST_LENGTH = 24.0;
    private static final double ATTRACTION_FACTOR = 0.0001;
    private static final double REPULSION_FACTOR = 0.05;

    private final LayoutCalibration calibration;
    private final Map<String, TypedNodeParticle> typedParticles = new LinkedHashMap<String, TypedNodeParticle>();

    TypedSpringBox(final LayoutCalibration calibration, final Random random) {
        super(false, random);
        this.calibration = calibration;
        setQuality(0.10);
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
        }
    }

    void setParticlePosition(final String id, final double x, final double y) {
        moveNode(id, x, y, 0.0);
        freezeNode(id, true);
    }

    void forgetParticle(final String id) {
        typedParticles.remove(id);
    }

    void applyTypedForces(final LinkedHashMap<String, GraphStreamLayoutEngine.ParticleState> states,
            final List<GraphStreamLayoutEngine.ForceLink> links) {
        final Map<String, Delta> ordinary = zeroDeltas(states);
        final Map<String, Delta> crossMap = zeroDeltas(states);
        final List<GraphStreamLayoutEngine.ParticleState> ordered =
            new ArrayList<GraphStreamLayoutEngine.ParticleState>(states.values());

        for (int first = 0; first < ordered.size(); first++) {
            for (int second = first + 1; second < ordered.size(); second++) {
                final GraphStreamLayoutEngine.ParticleState left = ordered.get(first);
                final GraphStreamLayoutEngine.ParticleState right = ordered.get(second);
                if (left.mapReferenceId.equals(right.mapReferenceId)) {
                    addSeparation(left, right, ordinary);
                }
            }
        }
        for (final GraphStreamLayoutEngine.ForceLink link : links) {
            final GraphStreamLayoutEngine.ParticleState first = states.get(link.firstId);
            final GraphStreamLayoutEngine.ParticleState second = states.get(link.secondId);
            if (first == null || second == null) {
                continue;
            }
            addAttraction(first, second, multiplier(link.kind), link.crossMap,
                link.crossMap ? crossMap : ordinary);
        }
        for (final GraphStreamLayoutEngine.ParticleState state : ordered) {
            if (state.pinned) {
                continue;
            }
            final Delta displacement = ordinary.get(state.id);
            final Delta cappedCrossMap = crossMap.get(state.id);
            clamp(cappedCrossMap, CROSS_MAP_DISPLACEMENT_LIMIT);
            displacement.add(cappedCrossMap.x, cappedCrossMap.y);
            state.x += displacement.x;
            state.y += displacement.y;
        }
    }

    private Map<String, Delta> zeroDeltas(final LinkedHashMap<String, GraphStreamLayoutEngine.ParticleState> states) {
        final Map<String, Delta> result = new LinkedHashMap<String, Delta>();
        for (final String id : states.keySet()) {
            result.put(id, new Delta());
        }
        return result;
    }

    private void addSeparation(final GraphStreamLayoutEngine.ParticleState first,
            final GraphStreamLayoutEngine.ParticleState second, final Map<String, Delta> target) {
        double x = first.x - second.x;
        double y = first.y - second.y;
        double distance = Math.sqrt(x * x + y * y);
        if (distance == 0.0) {
            x = first.id.compareTo(second.id) < 0 ? 1.0 : -1.0;
            y = 0.0;
            distance = 1.0;
        }
        final double radius = radius(first) + radius(second);
        final double magnitude = REPULSION_FACTOR * radius / (distance + 1.0);
        final double xContribution = x / distance * magnitude;
        final double yContribution = y / distance * magnitude;
        add(target, first, xContribution, yContribution);
        add(target, second, -xContribution, -yContribution);
    }

    private void addAttraction(final GraphStreamLayoutEngine.ParticleState first,
            final GraphStreamLayoutEngine.ParticleState second, final double multiplier, final boolean crossMap,
            final Map<String, Delta> target) {
        double x = second.x - first.x;
        double y = second.y - first.y;
        double distance = Math.sqrt(x * x + y * y);
        if (distance == 0.0) {
            x = first.id.compareTo(second.id) < 0 ? 1.0 : -1.0;
            y = 0.0;
            distance = 1.0;
        }
        final double magnitude = (distance - REST_LENGTH) * ATTRACTION_FACTOR * multiplier;
        final double xContribution = x / distance * magnitude;
        final double yContribution = y / distance * magnitude;
        add(target, first, xContribution, yContribution);
        add(target, second, -xContribution, -yContribution);
    }

    private double multiplier(final GraphStreamLayoutEngine.ForceKind kind) {
        if (kind == GraphStreamLayoutEngine.ForceKind.CONTAINMENT) {
            return calibration.containment();
        }
        if (kind == GraphStreamLayoutEngine.ForceKind.HIERARCHY) {
            return calibration.hierarchy();
        }
        return calibration.sameMap();
    }

    private double radius(final GraphStreamLayoutEngine.ParticleState state) {
        final TypedNodeParticle particle = typedParticles.get(state.id);
        return particle == null ? state.radius : particle.separationRadius();
    }

    private static void add(final Map<String, Delta> target, final GraphStreamLayoutEngine.ParticleState state,
            final double x, final double y) {
        if (!state.pinned) {
            target.get(state.id).add(x, y);
        }
    }

    private static void clamp(final Delta value, final double maximum) {
        final double magnitude = Math.sqrt(value.x * value.x + value.y * value.y);
        if (magnitude > maximum) {
            final double scale = maximum / magnitude;
            value.x *= scale;
            value.y *= scale;
        }
    }

    private static final class Delta {
        private double x;
        private double y;

        void add(final double x, final double y) {
            this.x += x;
            this.y += y;
        }
    }
}
