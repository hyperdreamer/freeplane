package org.freeplane.plugin.graph.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.freeplane.plugin.graph.geometry.GeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.GraphGeometryEngine;
import org.freeplane.plugin.graph.geometry.HullGeometry;
import org.freeplane.plugin.graph.geometry.HullIntersection;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;

public final class BoundarySeparationCorrection {
    public static final int MAX_DISPLACEMENT_ROUNDS = 4;
    static final double HULL_CLEARANCE = 16.0;
    static final double BASE_RADIUS = 8.0;
    static final double BOUNDARY_PADDING = 8.0;
    static final double SUPPORT_COMPARISON_EPSILON = 1e-9;

    private final GraphGeometryEngine geometryEngine = new GraphGeometryEngine();
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
        final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull = enclosuresByHull(projection);
        final Map<EnclosureHullKey, EnclosureHullKey> parents = parents(projection);
        final Set<ProjectedNodeKey> pinnedNodes = pinnedNodes(pins);

        long separationNanos = 0L;
        long hullNanos = 0L;
        long planNanos = 0L;
        long applyNanos = 0L;

        LayoutPositions current = positions;
        final Map<String, LayoutPoint> field = new LinkedHashMap<String, LayoutPoint>();
        int rounds = 0;
        int detected = 0;
        int terminalNodeResidual = 0;
        List<Violation> terminalViolations = Collections.emptyList();
        Map<EnclosureHullKey, HullGeometry> terminalHulls = Collections.emptyMap();

        while (true) {
            final long separationStart = System.nanoTime();
            final NodeSeparationResult separation = new NodeSeparationProjection().project(projection, current,
                pinnedNodes);
            separationNanos += System.nanoTime() - separationStart;
            current = separation.positions();
            terminalNodeResidual = separation.residualViolations();

            final long planStart = System.nanoTime();
            final long hullStart = System.nanoTime();
            final Map<EnclosureHullKey, HullGeometry> hulls = geometryEngine
                .computeHulls(projection, current, metrics).hulls();
            hullNanos += System.nanoTime() - hullStart;
            final List<Violation> violations = detect(projection, enclosuresByHull, parents, hulls);
            if (rounds == 0) {
                detected = violations.size();
            }
            if (violations.isEmpty() || rounds >= maxDisplacementRounds) {
                terminalViolations = violations;
                terminalHulls = hulls;
                planNanos += System.nanoTime() - planStart;
                break;
            }
            final PlanOutcome plan = plan(projection, enclosuresByHull, hulls, current, metrics, pins, pinnedNodes,
                violations);
            planNanos += System.nanoTime() - planStart;
            if (plan.isEmpty()) {
                terminalViolations = violations;
                terminalHulls = hulls;
                break;
            }
            final long applyStart = System.nanoTime();
            current = applyRound(projection, enclosuresByHull, current, plan, field);
            applyNanos += System.nanoTime() - applyStart;
            rounds++;
        }

        List<BoundaryConflict> conflicts = Collections.emptyList();
        if (!terminalViolations.isEmpty()) {
            final long planStart = System.nanoTime();
            final PlanOutcome terminalPlan = plan(projection, enclosuresByHull, terminalHulls, current, metrics, pins,
                pinnedNodes, terminalViolations);
            conflicts = terminalConflicts(terminalViolations, terminalPlan, projection, enclosuresByHull, pins);
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
        try {
            return HullIntersection.minimumSeparatingTranslation(first, second);
        }
        catch (final RuntimeException exception) {
            throw new BoundarySeparationException("Minimum separating translation failed", exception);
        }
    }

    static List<String> ancestorEscapePairKeys(final GraphProjection projection,
            final Map<EnclosureHullKey, HullGeometry> hulls) {
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(hulls, "hulls");
        final List<String> result = new ArrayList<String>();
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            if (enclosure.boundaryTier() == BoundaryTier.SUPPRESSED || !enclosure.parentHull().isPresent()) {
                continue;
            }
            final EnclosureHullKey child = enclosure.hullKey();
            final EnclosureHullKey parent = enclosure.parentHull().get();
            final HullGeometry childHull = hulls.get(child);
            final HullGeometry parentHull = hulls.get(parent);
            if (childHull == null || parentHull == null) {
                continue;
            }
            for (final LayoutPoint vertex : childHull.exactPolygon()) {
                if (!parentHull.contains(vertex)) {
                    result.add(CanonicalLayoutKeys.pair(child, parent));
                    break;
                }
            }
        }
        Collections.sort(result);
        return Collections.unmodifiableList(result);
    }

    private List<Violation> detect(final GraphProjection projection,
            final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull,
            final Map<EnclosureHullKey, EnclosureHullKey> parents,
            final Map<EnclosureHullKey, HullGeometry> hulls) {
        final Map<String, EnclosureHullKey> enforcedByCanonical = new LinkedHashMap<String, EnclosureHullKey>();
        final Set<String> ambiguous = new LinkedHashSet<String>();
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            if (enclosure.boundaryTier() == BoundaryTier.SUPPRESSED) {
                continue;
            }
            final EnclosureHullKey hull = enclosure.hullKey();
            if (!hulls.containsKey(hull)) {
                continue;
            }
            final String canonical = CanonicalLayoutKeys.hull(hull);
            if (enforcedByCanonical.put(canonical, hull) != null) {
                ambiguous.add(canonical);
            }
        }
        final List<EnclosureHullKey> enforced = new ArrayList<EnclosureHullKey>();
        for (final Map.Entry<String, EnclosureHullKey> entry : enforcedByCanonical.entrySet()) {
            if (!ambiguous.contains(entry.getKey())) {
                enforced.add(entry.getValue());
            }
        }
        final Map<String, Violation> violations = new LinkedHashMap<String, Violation>();
        final List<EnclosureHullKey> sorted = new ArrayList<EnclosureHullKey>(enforced);
        Collections.sort(sorted, new Comparator<EnclosureHullKey>() {
            @Override
            public int compare(final EnclosureHullKey left, final EnclosureHullKey right) {
                final HullGeometry leftHull = hulls.get(left);
                final HullGeometry rightHull = hulls.get(right);
                int result = Double.compare(leftHull.minX(), rightHull.minX());
                if (result != 0) {
                    return result;
                }
                result = Double.compare(leftHull.minY(), rightHull.minY());
                if (result != 0) {
                    return result;
                }
                return CanonicalLayoutKeys.hull(left).compareTo(CanonicalLayoutKeys.hull(right));
            }
        });
        final List<EnclosureHullKey> active = new ArrayList<EnclosureHullKey>();
        for (final EnclosureHullKey current : sorted) {
            final HullGeometry currentHull = hulls.get(current);
            for (final Iterator<EnclosureHullKey> iterator = active.iterator(); iterator.hasNext();) {
                if (hulls.get(iterator.next()).maxX() < currentHull.minX()) {
                    iterator.remove();
                }
            }
            for (final EnclosureHullKey other : active) {
                final HullGeometry otherHull = hulls.get(other);
                if (!(otherHull.minY() <= currentHull.maxY() && currentHull.minY() <= otherHull.maxY())) {
                    continue;
                }
                if (related(parents, other, current)) {
                    continue;
                }
                final EnclosureHullKey first;
                final EnclosureHullKey second;
                if (CanonicalLayoutKeys.hull(other).compareTo(CanonicalLayoutKeys.hull(current)) <= 0) {
                    first = other;
                    second = current;
                }
                else {
                    first = current;
                    second = other;
                }
                final HullGeometry firstHull = hulls.get(first);
                final HullGeometry secondHull = hulls.get(second);
                final boolean crossing = HullIntersection.siblingOverlap(firstHull, secondHull);
                if (!crossing && !containsInclusive(firstHull, secondHull)
                        && !containsInclusive(secondHull, firstHull)) {
                    continue;
                }
                final LayoutPoint translation = guardedMinimumSeparatingTranslation(firstHull, secondHull);
                if (isZero(translation)) {
                    continue;
                }
                final BoundaryConflict.Kind kind = crossing ? BoundaryConflict.Kind.SIBLING_CROSSING
                    : BoundaryConflict.Kind.SIBLING_CONTAINMENT;
                violations.put(CanonicalLayoutKeys.pair(first, second), new Violation(first, second, kind,
                    Math.hypot(translation.x(), translation.y()), translation));
            }
            active.add(current);
        }
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            if (enclosure.boundaryTier() == BoundaryTier.SUPPRESSED || !enclosure.parentHull().isPresent()) {
                continue;
            }
            final EnclosureHullKey child = enclosure.hullKey();
            final EnclosureHullKey parent = enclosure.parentHull().get();
            if (!enforced.contains(child) || !enforced.contains(parent)) {
                continue;
            }
            final HullGeometry childHull = hulls.get(child);
            final HullGeometry parentHull = hulls.get(parent);
            for (final LayoutPoint vertex : childHull.exactPolygon()) {
                if (!parentHull.contains(vertex)) {
                    final EnclosureHullKey first;
                    final EnclosureHullKey second;
                    if (CanonicalLayoutKeys.hull(child).compareTo(CanonicalLayoutKeys.hull(parent)) <= 0) {
                        first = child;
                        second = parent;
                    }
                    else {
                        first = parent;
                        second = child;
                    }
                    violations.put(CanonicalLayoutKeys.pair(child, parent), new Violation(first, second,
                        BoundaryConflict.Kind.ANCESTOR_ESCAPE, 0.0, null));
                    break;
                }
            }
        }
        final List<Violation> ordered = new ArrayList<Violation>(violations.values());
        Collections.sort(ordered, new Comparator<Violation>() {
            @Override
            public int compare(final Violation left, final Violation right) {
                final int byPenetration = Double.compare(right.penetration, left.penetration);
                if (byPenetration != 0) {
                    return byPenetration;
                }
                return left.pairKey.compareTo(right.pairKey);
            }
        });
        return Collections.unmodifiableList(ordered);
    }

    private PlanOutcome plan(final GraphProjection projection,
            final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull,
            final Map<EnclosureHullKey, HullGeometry> hulls, final LayoutPositions positions,
            final GeometryTextMetrics metrics, final List<PinProjection> pins,
            final Set<ProjectedNodeKey> pinnedNodes, final List<Violation> violations) {
        final PlanOutcome outcome = new PlanOutcome();
        final Set<MapReferenceId> rigidMaps = rigidMaps(pins);
        for (final Violation violation : violations) {
            if (violation.kind == BoundaryConflict.Kind.ANCESTOR_ESCAPE) {
                outcome.conflicts.put(violation.pairKey, conflict(violation,
                    BoundaryConflict.Reason.STRUCTURAL_ESCAPE,
                    blockingPins(violation, enclosuresByHull, pins)));
                continue;
            }
            if (!violation.first.mapReferenceId().equals(violation.second.mapReferenceId())) {
                planCrossMap(violation, rigidMaps, enclosuresByHull, pins, outcome);
            }
            else {
                planSameMap(violation, projection, enclosuresByHull, hulls, positions, metrics, pins,
                    pinnedNodes, outcome);
            }
        }
        return outcome;
    }

    private static void planCrossMap(final Violation violation, final Set<MapReferenceId> rigidMaps,
            final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull, final List<PinProjection> pins,
            final PlanOutcome outcome) {
        final boolean firstRigid = rigidMaps.contains(violation.first.mapReferenceId());
        final boolean secondRigid = rigidMaps.contains(violation.second.mapReferenceId());
        if (firstRigid && secondRigid) {
            outcome.conflicts.put(violation.pairKey, conflict(violation,
                BoundaryConflict.Reason.IMMOVABLE_SIDES, blockingPins(violation, enclosuresByHull, pins)));
            return;
        }
        if (firstRigid) {
            addMapDelta(outcome.mapDeltas, violation.second.mapReferenceId(), violation.translation);
        }
        else if (secondRigid) {
            addMapDelta(outcome.mapDeltas, violation.first.mapReferenceId(), negate(violation.translation));
        }
        else {
            addMapDelta(outcome.mapDeltas, violation.first.mapReferenceId(),
                scale(negate(violation.translation), 0.5));
            addMapDelta(outcome.mapDeltas, violation.second.mapReferenceId(),
                scale(violation.translation, 0.5));
        }
        outcome.movedPairs.add(violation.pairKey);
    }

    private static Set<MapReferenceId> rigidMaps(final List<PinProjection> pins) {
        final Set<MapReferenceId> result = new LinkedHashSet<MapReferenceId>();
        for (final PinProjection pin : pins) {
            if (pin.active()) {
                result.add(pin.projectedNode().get().mapReferenceId());
            }
        }
        return result;
    }

    private static void addMapDelta(final Map<MapReferenceId, LayoutPoint> deltas, final MapReferenceId map,
            final LayoutPoint delta) {
        final LayoutPoint previous = deltas.get(map);
        deltas.put(map, previous == null ? delta : add(previous, delta));
    }

    private static void planSameMap(final Violation violation, final GraphProjection projection,
            final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull,
            final Map<EnclosureHullKey, HullGeometry> hulls, final LayoutPositions positions,
            final GeometryTextMetrics metrics, final List<PinProjection> pins,
            final Set<ProjectedNodeKey> pinnedNodes, final PlanOutcome outcome) {
        final Candidate candidate = selectCandidate(violation, projection, enclosuresByHull, hulls, positions,
            metrics, pinnedNodes);
        if (candidate == null) {
            outcome.conflicts.put(violation.pairKey, conflict(violation,
                BoundaryConflict.Reason.IMMOVABLE_SIDES, blockingPins(violation, enclosuresByHull, pins)));
            return;
        }
        if (candidate.firstMagnitude > 0.0) {
            final Traversal moves = traverse(violation.first, candidate.firstUnit, candidate.firstMagnitude,
                projection, enclosuresByHull, hulls, positions, metrics, pinnedNodes);
            addMoves(outcome, moves, candidate.firstVector);
        }
        if (candidate.secondMagnitude > 0.0) {
            final Traversal moves = traverse(violation.second, candidate.secondUnit, candidate.secondMagnitude,
                projection, enclosuresByHull, hulls, positions, metrics, pinnedNodes);
            addMoves(outcome, moves, candidate.secondVector);
        }
        outcome.movedPairs.add(violation.pairKey);
    }

    private static void addMoves(final PlanOutcome outcome, final Traversal moves, final LayoutPoint vector) {
        if (moves.movedNodes.isEmpty() && moves.movedAnchors.isEmpty()) {
            throw new BoundarySeparationException("A valid candidate must have a non-empty displacement set");
        }
        for (final ProjectedNodeKey node : moves.movedNodes) {
            addVector(outcome.nodeFields, node, vector);
        }
        for (final EnclosureHullKey anchor : moves.movedAnchors) {
            addVector(outcome.anchorFields, anchor, vector);
        }
    }

    private static Candidate selectCandidate(final Violation violation, final GraphProjection projection,
            final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull,
            final Map<EnclosureHullKey, HullGeometry> hulls, final LayoutPositions positions,
            final GeometryTextMetrics metrics, final Set<ProjectedNodeKey> pinnedNodes) {
        final LayoutPoint translation = violation.translation;
        final double magnitude = Math.hypot(translation.x(), translation.y());
        final LayoutPoint firstUnit = LayoutPoint.of(translation.x() / magnitude, translation.y() / magnitude);
        final LayoutPoint secondUnit = negate(firstUnit);
        final double half = magnitude / 2.0;
        if (valid(violation.first, firstUnit, half, projection, enclosuresByHull, hulls, positions, metrics,
                pinnedNodes)
                && valid(violation.second, secondUnit, half, projection, enclosuresByHull, hulls, positions,
                    metrics, pinnedNodes)) {
            return new Candidate(firstUnit, half, scale(translation, -0.5), secondUnit, half,
                scale(translation, 0.5));
        }
        if (valid(violation.first, firstUnit, magnitude, projection, enclosuresByHull, hulls, positions, metrics,
            pinnedNodes)) {
            return new Candidate(firstUnit, magnitude, negate(translation), secondUnit, 0.0,
                LayoutPoint.of(0.0, 0.0));
        }
        if (valid(violation.second, secondUnit, magnitude, projection, enclosuresByHull, hulls, positions, metrics,
            pinnedNodes)) {
            return new Candidate(firstUnit, 0.0, LayoutPoint.of(0.0, 0.0), secondUnit, magnitude, translation);
        }
        return null;
    }

    private static boolean valid(final EnclosureHullKey hull, final LayoutPoint unit, final double magnitude,
            final GraphProjection projection,
            final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull,
            final Map<EnclosureHullKey, HullGeometry> hulls, final LayoutPositions positions,
            final GeometryTextMetrics metrics, final Set<ProjectedNodeKey> pinnedNodes) {
        final Traversal traversal = traverse(hull, unit, magnitude, projection, enclosuresByHull, hulls,
            positions, metrics, pinnedNodes);
        if (traversal.movedNodes.isEmpty() && traversal.movedAnchors.isEmpty()) {
            return false;
        }
        for (final PinDepth pin : traversal.pins) {
            if (pin.depth < magnitude) {
                return false;
            }
        }
        return true;
    }

    private static Traversal traverse(final EnclosureHullKey hull, final LayoutPoint unit, final double band,
            final GraphProjection projection,
            final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull,
            final Map<EnclosureHullKey, HullGeometry> hulls, final LayoutPositions positions,
            final GeometryTextMetrics metrics, final Set<ProjectedNodeKey> pinnedNodes) {
        final Traversal traversal = new Traversal();
        collectCap(hull, unit, band, projection, enclosuresByHull, hulls, positions, metrics, pinnedNodes,
            traversal);
        return traversal;
    }

    private static void collectCap(final EnclosureHullKey hull, final LayoutPoint unit, final double band,
            final GraphProjection projection,
            final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull,
            final Map<EnclosureHullKey, HullGeometry> hulls, final LayoutPositions positions,
            final GeometryTextMetrics metrics, final Set<ProjectedNodeKey> pinnedNodes,
            final Traversal traversal) {
        final ProjectedEnclosure enclosure = enclosuresByHull.get(hull);
        if (enclosure == null) {
            throw new BoundarySeparationException("Missing enclosure for hull " + CanonicalLayoutKeys.hull(hull));
        }
        final boolean empty = enclosure.directNodes().isEmpty() && enclosure.directEnclosures().isEmpty();
        final double support = support(hull, unit, projection, enclosuresByHull, hulls, positions, metrics);
        if (empty) {
            traversal.movedAnchors.add(hull);
            return;
        }
        for (final ProjectedNodeKey node : enclosure.directNodes()) {
            final double contribution = nodeContribution(node, unit, positions, projection);
            if (inBand(contribution, support, band)) {
                if (pinnedNodes.contains(node)) {
                    traversal.pins.add(new PinDepth(node, support - contribution));
                }
                else {
                    traversal.movedNodes.add(node);
                }
            }
        }
        for (final EnclosureHullKey child : enclosure.directEnclosures()) {
            final double contribution = polySupport(child, unit, hulls) + HULL_CLEARANCE;
            if (inBand(contribution, support, band)) {
                collectCap(child, unit, band, projection, enclosuresByHull, hulls, positions, metrics,
                    pinnedNodes, traversal);
            }
        }
    }

    private static boolean inBand(final double contribution, final double support, final double band) {
        final double epsilon = SUPPORT_COMPARISON_EPSILON * Math.max(Math.abs(contribution),
            Math.max(Math.abs(support), Math.abs(support - band)));
        return contribution >= support - band - epsilon;
    }

    private static double support(final EnclosureHullKey hull, final LayoutPoint unit,
            final GraphProjection projection,
            final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull,
            final Map<EnclosureHullKey, HullGeometry> hulls, final LayoutPositions positions,
            final GeometryTextMetrics metrics) {
        final ProjectedEnclosure enclosure = enclosuresByHull.get(hull);
        if (enclosure == null) {
            throw new BoundarySeparationException("Missing enclosure for hull " + CanonicalLayoutKeys.hull(hull));
        }
        final boolean empty = enclosure.directNodes().isEmpty() && enclosure.directEnclosures().isEmpty();
        if (empty) {
            final LayoutPoint anchor = positions.anchors().get(hull);
            if (anchor == null) {
                throw new BoundarySeparationException("Missing anchor for hull " + CanonicalLayoutKeys.hull(hull));
            }
            final java.awt.geom.Dimension2D label = labelSize(enclosure, metrics);
            final double halfWidth = label.getWidth() * 0.5 + BOUNDARY_PADDING;
            final double halfHeight = label.getHeight() * 0.5 + BOUNDARY_PADDING;
            return unit.x() * anchor.x() + unit.y() * anchor.y()
                + Math.max(Math.abs(unit.x()) * halfWidth, Math.abs(unit.y()) * halfHeight);
        }
        double best = Double.NEGATIVE_INFINITY;
        for (final ProjectedNodeKey node : enclosure.directNodes()) {
            final LayoutPoint center = positions.nodes().get(node);
            if (center == null) {
                throw new BoundarySeparationException("Missing node position for " + node);
            }
            best = Math.max(best, unit.x() * center.x() + unit.y() * center.y() + nodeRadius(node, projection));
        }
        for (final EnclosureHullKey child : enclosure.directEnclosures()) {
            best = Math.max(best, polySupport(child, unit, hulls));
        }
        return best + HULL_CLEARANCE;
    }

    private static double nodeContribution(final ProjectedNodeKey node, final LayoutPoint unit,
            final LayoutPositions positions, final GraphProjection projection) {
        final LayoutPoint center = positions.nodes().get(node);
        if (center == null) {
            throw new BoundarySeparationException("Missing node position for " + node);
        }
        return unit.x() * center.x() + unit.y() * center.y() + nodeRadius(node, projection) + HULL_CLEARANCE;
    }

    private static double nodeRadius(final ProjectedNodeKey node, final GraphProjection projection) {
        final org.freeplane.plugin.graph.projection.NodeProminence prominence =
            projection.prominence().get(node);
        final double scale = prominence == null ? 1.0 : prominence.scale();
        return BASE_RADIUS * scale;
    }

    private static double polySupport(final EnclosureHullKey hull, final LayoutPoint unit,
            final Map<EnclosureHullKey, HullGeometry> hulls) {
        final HullGeometry geometry = hulls.get(hull);
        if (geometry == null) {
            throw new BoundarySeparationException("Missing hull " + CanonicalLayoutKeys.hull(hull));
        }
        double best = Double.NEGATIVE_INFINITY;
        for (final LayoutPoint vertex : geometry.exactPolygon()) {
            best = Math.max(best, unit.x() * vertex.x() + unit.y() * vertex.y());
        }
        return best;
    }

    private static java.awt.geom.Dimension2D labelSize(final ProjectedEnclosure enclosure,
            final GeometryTextMetrics metrics) {
        if (enclosure.boundaryTier() == BoundaryTier.SUPPRESSED) {
            return ZERO_SIZE;
        }
        java.awt.geom.Dimension2D largest = null;
        for (final org.freeplane.plugin.graph.projection.input.SafeNodeLabel label : enclosure.labels()) {
            final java.awt.geom.Dimension2D measured = metrics.measure(label.displayText(),
                enclosure.boundaryTier());
            if (largest == null || measured.getWidth() * measured.getHeight() > largest.getWidth()
                    * largest.getHeight()) {
                largest = measured;
            }
        }
        if (largest == null) {
            throw new BoundarySeparationException("Enclosures must carry at least one label");
        }
        return largest;
    }

    private static final java.awt.geom.Dimension2D ZERO_SIZE = new java.awt.geom.Dimension2D() {
        @Override
        public double getWidth() {
            return 0.0;
        }

        @Override
        public double getHeight() {
            return 0.0;
        }

        @Override
        public void setSize(final double width, final double height) {
            throw new UnsupportedOperationException("Geometry sizes are immutable");
        }
    };

    private LayoutPositions applyRound(final GraphProjection projection,
            final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull, final LayoutPositions positions,
            final PlanOutcome plan, final Map<String, LayoutPoint> field) {
        final Map<EnclosureHullKey, LayoutPoint> anchorDeltas = anchorDeltas(projection, enclosuresByHull, plan);
        final Map<ProjectedNodeKey, LayoutPoint> nodes = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        for (final Map.Entry<ProjectedNodeKey, LayoutPoint> entry : positions.nodes().entrySet()) {
            final ProjectedNodeKey key = entry.getKey();
            final LayoutPoint delta = sum(plan.nodeFields.get(key), plan.mapDeltas.get(key.mapReferenceId()));
            nodes.put(key, translated(entry.getValue(), delta));
            if (!isZero(delta)) {
                addField(field, CanonicalLayoutKeys.nodeField(key), delta);
            }
        }
        final Map<EnclosureHullKey, LayoutPoint> anchors = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        for (final Map.Entry<EnclosureHullKey, LayoutPoint> entry : positions.anchors().entrySet()) {
            final EnclosureHullKey key = entry.getKey();
            final LayoutPoint delta = sum(anchorDeltas.get(key), plan.mapDeltas.get(key.mapReferenceId()));
            anchors.put(key, translated(entry.getValue(), delta));
            if (!isZero(delta)) {
                addField(field, CanonicalLayoutKeys.anchorField(key), delta);
            }
        }
        return LayoutPositions.of(nodes, anchors);
    }

    private static Map<EnclosureHullKey, LayoutPoint> anchorDeltas(final GraphProjection projection,
            final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull, final PlanOutcome plan) {
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

    private static <K> void addVector(final Map<K, LayoutPoint> field, final K key, final LayoutPoint delta) {
        final LayoutPoint previous = field.get(key);
        field.put(key, previous == null ? delta : add(previous, delta));
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

    private static LayoutPoint negate(final LayoutPoint value) {
        return LayoutPoint.of(-value.x(), -value.y());
    }

    private static LayoutPoint scale(final LayoutPoint value, final double factor) {
        return LayoutPoint.of(value.x() * factor, value.y() * factor);
    }

    private static List<BoundaryConflict> terminalConflicts(final List<Violation> violations,
            final PlanOutcome plan, final GraphProjection projection,
            final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull, final List<PinProjection> pins) {
        final List<BoundaryConflict> result = new ArrayList<BoundaryConflict>();
        for (final Violation violation : violations) {
            final BoundaryConflict existing = plan.conflicts.get(violation.pairKey);
            if (existing != null) {
                result.add(existing);
                continue;
            }
            final BoundaryConflict.Reason reason = violation.kind == BoundaryConflict.Kind.ANCESTOR_ESCAPE
                ? BoundaryConflict.Reason.STRUCTURAL_ESCAPE : BoundaryConflict.Reason.IMMOVABLE_SIDES;
            result.add(conflict(violation, reason, blockingPins(violation, enclosuresByHull, pins)));
        }
        return Collections.unmodifiableList(result);
    }

    private static List<PinProjection> blockingPins(final Violation violation,
            final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull, final List<PinProjection> pins) {
        final boolean sameMap = violation.first.mapReferenceId().equals(violation.second.mapReferenceId());
        final Set<ProjectedNodeKey> subtree = new LinkedHashSet<ProjectedNodeKey>();
        if (sameMap) {
            subtree.addAll(subtreeNodes(enclosuresByHull.get(violation.first), enclosuresByHull));
            subtree.addAll(subtreeNodes(enclosuresByHull.get(violation.second), enclosuresByHull));
        }
        final Set<PinProjection> unique = new LinkedHashSet<PinProjection>();
        for (final PinProjection pin : pins) {
            if (!pin.active()) {
                continue;
            }
            final ProjectedNodeKey node = pin.projectedNode().get();
            if (sameMap) {
                if (subtree.contains(node)) {
                    unique.add(pin);
                }
            }
            else {
                final MapReferenceId map = node.mapReferenceId();
                if (map.equals(violation.first.mapReferenceId())
                        || map.equals(violation.second.mapReferenceId())) {
                    unique.add(pin);
                }
            }
        }
        return new ArrayList<PinProjection>(unique);
    }

    private static Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull(final GraphProjection projection) {
        final Map<EnclosureHullKey, ProjectedEnclosure> result =
            new LinkedHashMap<EnclosureHullKey, ProjectedEnclosure>();
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            result.put(enclosure.hullKey(), enclosure);
        }
        return result;
    }

    private static Map<EnclosureHullKey, EnclosureHullKey> parents(final GraphProjection projection) {
        final Map<EnclosureHullKey, EnclosureHullKey> result =
            new LinkedHashMap<EnclosureHullKey, EnclosureHullKey>();
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            if (enclosure.parentHull().isPresent()) {
                result.put(enclosure.hullKey(), enclosure.parentHull().get());
            }
        }
        return result;
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

    private static boolean related(final Map<EnclosureHullKey, EnclosureHullKey> parents,
            final EnclosureHullKey first, final EnclosureHullKey second) {
        for (EnclosureHullKey current = parents.get(first); current != null; current = parents.get(current)) {
            if (current.equals(second)) {
                return true;
            }
        }
        for (EnclosureHullKey current = parents.get(second); current != null; current = parents.get(current)) {
            if (current.equals(first)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsInclusive(final HullGeometry outer, final HullGeometry inner) {
        for (final LayoutPoint vertex : inner.exactPolygon()) {
            if (!outer.contains(vertex)) {
                return false;
            }
        }
        return true;
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

    private static BoundaryConflict conflict(final Violation violation, final BoundaryConflict.Reason reason,
            final List<PinProjection> pins) {
        return new BoundaryConflict(violation.first, violation.second, violation.kind, reason, pins);
    }

    private static boolean isZero(final LayoutPoint value) {
        return value == null || value.x() == 0.0 && value.y() == 0.0;
    }

    private static List<String> pairKeys(final List<Violation> violations) {
        final List<String> result = new ArrayList<String>(violations.size());
        for (final Violation violation : violations) {
            result.add(violation.pairKey);
        }
        return Collections.unmodifiableList(result);
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

    private static final class Violation {
        private final EnclosureHullKey first;
        private final EnclosureHullKey second;
        private final BoundaryConflict.Kind kind;
        private final double penetration;
        private final LayoutPoint translation;
        private final String pairKey;

        private Violation(final EnclosureHullKey first, final EnclosureHullKey second,
                final BoundaryConflict.Kind kind, final double penetration, final LayoutPoint translation) {
            this.first = first;
            this.second = second;
            this.kind = kind;
            this.penetration = penetration;
            this.translation = translation;
            this.pairKey = CanonicalLayoutKeys.pair(first, second);
        }
    }

    private static final class PlanOutcome {
        private final Map<MapReferenceId, LayoutPoint> mapDeltas =
            new LinkedHashMap<MapReferenceId, LayoutPoint>();
        private final Map<ProjectedNodeKey, LayoutPoint> nodeFields =
            new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        private final Map<EnclosureHullKey, LayoutPoint> anchorFields =
            new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        private final Map<String, BoundaryConflict> conflicts = new LinkedHashMap<String, BoundaryConflict>();
        private final Set<String> movedPairs = new LinkedHashSet<String>();

        private boolean isEmpty() {
            return mapDeltas.isEmpty() && nodeFields.isEmpty() && anchorFields.isEmpty();
        }
    }

    private static final class Traversal {
        private final List<ProjectedNodeKey> movedNodes = new ArrayList<ProjectedNodeKey>();
        private final List<EnclosureHullKey> movedAnchors = new ArrayList<EnclosureHullKey>();
        private final List<PinDepth> pins = new ArrayList<PinDepth>();
    }

    private static final class PinDepth {
        private final ProjectedNodeKey node;
        private final double depth;

        private PinDepth(final ProjectedNodeKey node, final double depth) {
            this.node = node;
            this.depth = depth;
        }
    }

    private static final class Candidate {
        private final LayoutPoint firstUnit;
        private final double firstMagnitude;
        private final LayoutPoint firstVector;
        private final LayoutPoint secondUnit;
        private final double secondMagnitude;
        private final LayoutPoint secondVector;

        private Candidate(final LayoutPoint firstUnit, final double firstMagnitude,
                final LayoutPoint firstVector, final LayoutPoint secondUnit, final double secondMagnitude,
                final LayoutPoint secondVector) {
            this.firstUnit = firstUnit;
            this.firstMagnitude = firstMagnitude;
            this.firstVector = firstVector;
            this.secondUnit = secondUnit;
            this.secondMagnitude = secondMagnitude;
            this.secondVector = secondVector;
        }
    }
}
