package org.freeplane.plugin.graph.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.freeplane.plugin.graph.geometry.GeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.GraphGeometryEngine;
import org.freeplane.plugin.graph.geometry.HullGeometry;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.projection.CanonicalKeys;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;

public final class BoundarySeparationCorrection {
    public static final int MAX_DISPLACEMENT_ROUNDS = 4;
    static final double HULL_CLEARANCE = 16.0;
    static final double BASE_RADIUS = 8.0;
    static final double BOUNDARY_PADDING = 8.0;
    static final double SUPPORT_COMPARISON_EPSILON = 1e-9;

    private final GraphGeometryEngine geometryEngine = new GraphGeometryEngine();
    private final BoundaryInvariants boundaryInvariants = new BoundaryInvariants();
    private final int maxDisplacementRounds;

    public BoundarySeparationCorrection() {
        this(MAX_DISPLACEMENT_ROUNDS);
    }

    BoundarySeparationCorrection(final int maxDisplacementRounds) {
        if (maxDisplacementRounds < 1) {
            throw new IllegalArgumentException("The displacement round bound must be positive");
        }
        this.maxDisplacementRounds = maxDisplacementRounds;
    }

    long ancestorExactContainmentChecks() {
        return boundaryInvariants.ancestorExactContainmentChecks();
    }

    long ancestorContainmentMemoHits() {
        return boundaryInvariants.ancestorContainmentMemoHits();
    }

    long capTraversals() {
        return boundaryInvariants.capTraversals();
    }

    long capCacheHits() {
        return boundaryInvariants.capCacheHits();
    }

    public BoundarySeparationResult apply(final GraphProjection projection, final LayoutPositions positions,
            final GeometryTextMetrics metrics, final List<PinProjection> pins) {
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(positions, "positions");
        Objects.requireNonNull(metrics, "metrics");
        Objects.requireNonNull(pins, "pins");
        try {
            return separate(projection, positions, metrics, pins);
        }
        catch (final BoundarySeparationException exception) {
            throw exception;
        }
        catch (final RuntimeException exception) {
            throw new BoundarySeparationException("Boundary separation failed", exception);
        }
    }

    private BoundarySeparationResult separate(final GraphProjection projection, final LayoutPositions positions,
            final GeometryTextMetrics metrics, final List<PinProjection> pins) {
        final Set<ProjectedNodeKey> pinnedNodes = pinnedNodes(pins);
        final Map<ProjectedNodeKey, List<EnclosureHullKey>> nodeHullsByKey =
            BoundaryInvariants.nodeHullsByKey(projection);

        long separationNanos = 0L;
        long hullNanos = 0L;
        long planNanos = 0L;
        long applyNanos = 0L;

        LayoutPositions current = positions;
        final Map<String, LayoutPoint> field = new LinkedHashMap<String, LayoutPoint>();
        GraphGeometry geometry = null;
        LayoutPositions hullPositions = null;
        int rounds = 0;
        int detected = 0;
        int terminalNodeResidual = 0;
        List<BoundaryInvariants.Violation> terminalViolations = Collections.emptyList();
        Map<EnclosureHullKey, HullGeometry> terminalHulls = Collections.emptyMap();

        while (true) {
            final long separationStart = System.nanoTime();
            final NodeSeparationResult separation = new NodeSeparationProjection().project(projection, current,
                pinnedNodes);
            separationNanos += System.nanoTime() - separationStart;
            current = separation.positions();
            terminalNodeResidual = separation.residualViolations();

            final long hullStart = System.nanoTime();
            if (geometry == null) {
                geometry = geometryEngine.computeHulls(projection, current, metrics);
            }
            else {
                geometry = geometryEngine.recomputeHulls(projection, current, metrics, geometry,
                    BoundaryInvariants.changedHulls(nodeHullsByKey, hullPositions, current));
            }
            final Map<EnclosureHullKey, HullGeometry> hulls = geometry.hulls();
            hullPositions = current;
            hullNanos += System.nanoTime() - hullStart;
            // R13: plan covers detection, candidate evaluation and conflict construction only;
            // hull measurement is attributed to the hull stage above.
            final long planStart = System.nanoTime();
            final List<BoundaryInvariants.Violation> violations = boundaryInvariants.detect(projection, geometry);
            if (rounds == 0) {
                detected = violations.size();
            }
            if (violations.isEmpty() || rounds >= maxDisplacementRounds) {
                terminalViolations = violations;
                terminalHulls = hulls;
                planNanos += System.nanoTime() - planStart;
                break;
            }
            final BoundaryInvariants.PlanOutcome plan = boundaryInvariants.plan(projection,
                BoundaryInvariants.enclosuresByHull(projection), hulls, current, metrics, pins, pinnedNodes,
                violations);
            planNanos += System.nanoTime() - planStart;
            if (plan.isEmpty()) {
                terminalViolations = violations;
                terminalHulls = hulls;
                break;
            }
            final long applyStart = System.nanoTime();
            current = applyRound(projection, BoundaryInvariants.enclosuresByHull(projection), current, plan, field);
            applyNanos += System.nanoTime() - applyStart;
            rounds++;
        }

        List<BoundaryConflict> conflicts = Collections.emptyList();
        if (!terminalViolations.isEmpty()) {
            final long planStart = System.nanoTime();
            final GraphGeometry terminalGeometry =
                GraphGeometry.of(geometry.nodes(), terminalHulls);
            conflicts = boundaryInvariants.terminalConflicts(projection, current, metrics, pins,
                terminalGeometry, terminalViolations, rounds >= maxDisplacementRounds);
            planNanos += System.nanoTime() - planStart;
        }
        final Map<String, LayoutPoint> applied = sortedNonZero(field);
        final double[] displacementMetrics = displacementMetrics(applied);
        final BoundarySeparationDiagnostics diagnostics = new BoundarySeparationDiagnostics(conflicts,
            pairKeys(terminalViolations), detected, terminalViolations.size(), rounds, displacementMetrics[0],
            displacementMetrics[1], applied, 0.0, 0.0);
        if (!diagnostics.boundaryVerified() && !diagnostics.boundaryCovered()) {
            throw new BoundarySeparationException("Boundary separation did not cover every residual pair");
        }
        return new BoundarySeparationResult(current, diagnostics, terminalNodeResidual,
            new BoundarySeparationTimings(separationNanos, hullNanos, planNanos, applyNanos));
    }

    static LayoutPoint guardedMinimumSeparatingTranslation(final HullGeometry first, final HullGeometry second) {
        return BoundaryInvariants.guardedMinimumSeparatingTranslation(first, second);
    }

    static List<String> ancestorEscapePairKeys(final GraphProjection projection,
            final Map<EnclosureHullKey, HullGeometry> hulls) {
        return BoundaryInvariants.ancestorEscapePairKeys(projection, hulls);
    }

    private LayoutPositions applyRound(final GraphProjection projection,
            final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull, final LayoutPositions positions,
            final BoundaryInvariants.PlanOutcome plan, final Map<String, LayoutPoint> field) {
        final Map<EnclosureHullKey, LayoutPoint> anchorDeltas = anchorDeltas(projection, enclosuresByHull, plan);
        final Map<ProjectedNodeKey, LayoutPoint> nodes = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        for (final Map.Entry<ProjectedNodeKey, LayoutPoint> entry : positions.nodes().entrySet()) {
            final ProjectedNodeKey key = entry.getKey();
            final LayoutPoint delta = sum(plan.nodeFields.get(key), plan.mapDeltas.get(key.mapReferenceId()));
            nodes.put(key, translated(entry.getValue(), delta));
            if (!isZero(delta)) {
                addField(field, CanonicalKeys.nodeField(key), delta);
            }
        }
        final Map<EnclosureHullKey, LayoutPoint> anchors = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        for (final Map.Entry<EnclosureHullKey, LayoutPoint> entry : positions.anchors().entrySet()) {
            final EnclosureHullKey key = entry.getKey();
            final LayoutPoint delta = sum(anchorDeltas.get(key), plan.mapDeltas.get(key.mapReferenceId()));
            anchors.put(key, translated(entry.getValue(), delta));
            if (!isZero(delta)) {
                addField(field, CanonicalKeys.anchorField(key), delta);
            }
        }
        return LayoutPositions.of(nodes, anchors);
    }

    private static Map<EnclosureHullKey, LayoutPoint> anchorDeltas(final GraphProjection projection,
            final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull,
            final BoundaryInvariants.PlanOutcome plan) {
        final Map<EnclosureHullKey, LayoutPoint> result = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        for (final Map.Entry<EnclosureHullKey, LayoutPoint> entry : plan.anchorFields.entrySet()) {
            result.put(entry.getKey(), entry.getValue());
        }
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            final EnclosureHullKey hull = enclosure.hullKey();
            if (result.containsKey(hull)) {
                continue;
            }
            double sumX = 0.0;
            double sumY = 0.0;
            int count = 0;
            for (final ProjectedNodeKey node : subtreeNodes(enclosure, enclosuresByHull)) {
                final LayoutPoint delta = plan.nodeFields.get(node);
                if (delta != null && !isZero(delta)) {
                    sumX += delta.x();
                    sumY += delta.y();
                    count++;
                }
            }
            if (count > 0) {
                result.put(hull, LayoutPoint.of(sumX / count, sumY / count));
            }
        }
        return result;
    }

    private static void addField(final Map<String, LayoutPoint> field, final String key,
            final LayoutPoint delta) {
        final LayoutPoint previous = field.get(key);
        field.put(key, previous == null ? delta : add(previous, delta));
    }

    private static LayoutPoint sum(final LayoutPoint first, final LayoutPoint second) {
        if (first == null) {
            return second == null ? LayoutPoint.of(0.0, 0.0) : second;
        }
        return second == null ? first : add(first, second);
    }

    private static LayoutPoint translated(final LayoutPoint point, final LayoutPoint delta) {
        return isZero(delta) ? point : add(point, delta);
    }

    private static LayoutPoint add(final LayoutPoint first, final LayoutPoint second) {
        return LayoutPoint.of(first.x() + second.x(), first.y() + second.y());
    }

    private static boolean isZero(final LayoutPoint value) {
        return value == null || value.x() == 0.0 && value.y() == 0.0;
    }

    private static List<String> pairKeys(final List<BoundaryInvariants.Violation> violations) {
        final List<String> result = new ArrayList<String>(violations.size());
        for (final BoundaryInvariants.Violation violation : violations) {
            result.add(violation.pairKey());
        }
        return Collections.unmodifiableList(result);
    }

    private static Set<ProjectedNodeKey> pinnedNodes(final List<PinProjection> pins) {
        final Set<ProjectedNodeKey> result = new LinkedHashSet<ProjectedNodeKey>();
        for (final PinProjection pin : pins) {
            if (pin.active()) {
                result.add(pin.projectedNode().get());
            }
        }
        return result;
    }

    private static List<ProjectedNodeKey> subtreeNodes(final ProjectedEnclosure enclosure,
            final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull) {
        final List<ProjectedNodeKey> result = new ArrayList<ProjectedNodeKey>();
        collectSubtreeNodes(enclosure, enclosuresByHull, new LinkedHashSet<EnclosureHullKey>(), result);
        return result;
    }

    private static void collectSubtreeNodes(final ProjectedEnclosure enclosure,
            final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull,
            final Set<EnclosureHullKey> visited, final List<ProjectedNodeKey> result) {
        if (enclosure == null || !visited.add(enclosure.hullKey())) {
            return;
        }
        result.addAll(enclosure.directNodes());
        for (final EnclosureHullKey child : enclosure.directEnclosures()) {
            collectSubtreeNodes(enclosuresByHull.get(child), enclosuresByHull, visited, result);
        }
    }

    private static Map<String, LayoutPoint> sortedNonZero(final Map<String, LayoutPoint> field) {
        final List<String> keys = new ArrayList<String>(field.keySet());
        Collections.sort(keys);
        final Map<String, LayoutPoint> result = new LinkedHashMap<String, LayoutPoint>();
        for (final String key : keys) {
            final LayoutPoint value = field.get(key);
            if (!isZero(value)) {
                result.put(key, value);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static double[] displacementMetrics(final Map<String, LayoutPoint> field) {
        if (field.isEmpty()) {
            return new double[] {0.0, 0.0};
        }
        double sumSquares = 0.0;
        double maximum = 0.0;
        for (final LayoutPoint value : field.values()) {
            final double squared = value.x() * value.x() + value.y() * value.y();
            sumSquares += squared;
            maximum = Math.max(maximum, Math.sqrt(squared));
        }
        return new double[] {Math.sqrt(sumSquares / field.size()), maximum};
    }
}
