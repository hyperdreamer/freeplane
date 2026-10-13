package org.freeplane.plugin.graph.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.freeplane.plugin.graph.geometry.GeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.GraphGeometryEngine;
import org.freeplane.plugin.graph.geometry.HullGeometry;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.geometry.NodeGeometry;
import org.freeplane.plugin.graph.projection.CanonicalKeys;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEdgeKey;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;

public final class EdgeConflictCorrection {
    static final double EDGE_NODE_CLEARANCE = 3.0;
    static final double DRAWN_BASE_RADIUS = 8.0;
    static final double CONFLICT_CELL_SIZE = 48.0;
    static final double CONFLICT_MIN_CELL_SIZE = 3.0;
    static final int CONFLICT_CELL_CAP = 64;
    static final int CONFLICT_MAX_SUBDIVISION_DEPTH = 4;
    static final int OUTERMOST_CELL_INDEX = Integer.MAX_VALUE;
    static final int CONFLICT_GRID_DIMENSION = 128;
    static final double CONFLICT_MAX_CELL_SIZE = 1.0e9;
    static final int CONFLICT_MAX_CELLS_PER_PRIMITIVE = 4096;
    static final double ROUTE_EPSILON = 1e-9;
    static final double CROSSING_CLEARANCE = 16.0;
    static final int MAX_RELOCATION_ROUNDS = 4;
    static final int MAX_VERIFICATIONS_PER_FRAME = 64;
    static final long EDGE_CONFLICT_MAX_INDEX_ITEMS_PER_FRAME = 262_144L;
    static final long EDGE_CONFLICT_MAX_PAIR_CHECKS_PER_FRAME = 131_072L;
    static final int EDGE_CONFLICT_MAX_DETECTED_PER_FRAME = 4_096;
    static final long EDGE_CONFLICT_MAX_VERIFICATION_WORK_PER_FRAME = 65_536L;

    private static final double OUTERMOST_CELL_INDEX_DOUBLE = OUTERMOST_CELL_INDEX;
    private static final Comparator<ProjectedNodeKey> NODE_ORDER =
        new Comparator<ProjectedNodeKey>() {
            @Override
            public int compare(final ProjectedNodeKey first, final ProjectedNodeKey second) {
                return CanonicalKeys.node(first).compareTo(CanonicalKeys.node(second));
            }
        };
    private static final Comparator<EnclosureHullKey> HULL_ORDER =
        new Comparator<EnclosureHullKey>() {
            @Override
            public int compare(final EnclosureHullKey first, final EnclosureHullKey second) {
                return CanonicalKeys.hull(first).compareTo(CanonicalKeys.hull(second));
            }
        };

    private final GraphGeometryEngine geometryEngine = new GraphGeometryEngine();
    private long indexInsertedItems;
    private long indexPairChecks;
    private int indexSubdivisions;
    private boolean indexBudgetExhausted;
    private int detectedKeys;
    private long verificationWork;

    public EdgeConflictCorrection() {
    }

    public EdgeConflictResult apply(final GraphProjection projection, final LayoutPositions positions,
            final GeometryTextMetrics metrics, final List<PinProjection> pins,
            final Set<ProjectedEndpointKey> visibleEndpoints,
            final BoundarySeparationResult boundaryResult) {
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(positions, "positions");
        Objects.requireNonNull(metrics, "metrics");
        Objects.requireNonNull(pins, "pins");
        Objects.requireNonNull(visibleEndpoints, "visibleEndpoints");
        Objects.requireNonNull(boundaryResult, "boundaryResult");
        try {
            return relocate(projection, positions, metrics, pins, visibleEndpoints, boundaryResult);
        }
        catch (final EdgeConflictException exception) {
            throw exception;
        }
        catch (final RuntimeException exception) {
            throw new EdgeConflictException("Edge conflict correction failed", exception);
        }
    }

    private EdgeConflictResult relocate(final GraphProjection projection, final LayoutPositions positions,
            final GeometryTextMetrics metrics, final List<PinProjection> pins,
            final Set<ProjectedEndpointKey> visibleEndpoints,
            final BoundarySeparationResult boundaryResult) {
        resetFrameCounters();
        final Set<ProjectedNodeKey> pinned = pinnedNodes(pins);
        final Map<ProjectedNodeKey, List<EnclosureHullKey>> nodeHullsByKey =
            BoundaryInvariants.nodeHullsByKey(projection);
        final Map<ProjectedNodeKey, List<EnclosureHullKey>> enclosurePaths = enclosurePaths(projection);
        final BoundaryInvariants boundaryInvariants = new BoundaryInvariants();

        LayoutPositions currentPositions = positions;
        GraphGeometry currentGeometry = boundaryResult.geometry();
        final IndexedDetection initial = detect(projection, currentPositions, currentGeometry,
            visibleEndpoints);
        Detection conflicts = initial.detection();
        final int conflictsDetected = conflicts.keys().size();
        final int crossingsDetected = conflicts.crossings();
        final int edgeOverNodeDetected = conflicts.edgeOverNodes();
        final List<EdgeConflictDiagnostics.Repair> repairs =
            new ArrayList<EdgeConflictDiagnostics.Repair>();
        int rounds = 0;
        int verifications = 0;
        boolean detectionTruncated = initial.truncated();
        boolean verificationStopped = false;
        boolean verificationBudgetExhausted = false;

        while (!detectionTruncated && !verificationStopped && rounds < MAX_RELOCATION_ROUNDS
                && verifications < MAX_VERIFICATIONS_PER_FRAME) {
            if (conflicts.keys().isEmpty()) {
                break;
            }
            final List<Candidate> pool = generateCandidates(conflicts, currentPositions, pinned,
                enclosurePaths);
            boolean acceptedThisRound = false;
            while (!pool.isEmpty() && !verificationStopped
                    && verifications < MAX_VERIFICATIONS_PER_FRAME) {
                final List<Candidate> batch = greedyDisjoint(pool);
                if (batch.isEmpty()) {
                    break;
                }
                pool.removeAll(batch);
                final List<List<Candidate>> attempts = new ArrayList<List<Candidate>>();
                attempts.add(batch);
                if (batch.size() > 1) {
                    for (final Candidate candidate : batch) {
                        attempts.add(Collections.singletonList(candidate));
                    }
                }
                for (final List<Candidate> attempt : attempts) {
                    verifications++;
                    final Evaluation evaluation = verify(attempt, projection, currentPositions,
                        currentGeometry, metrics, visibleEndpoints, conflicts, boundaryInvariants,
                        nodeHullsByKey, enclosurePaths);
                    if (evaluation.outcome == Evaluation.Outcome.ACCEPT) {
                        recordRepair(repairs, attempt, rounds);
                        currentPositions = evaluation.positions;
                        currentGeometry = evaluation.geometry;
                        rounds++;
                        acceptedThisRound = true;
                        if (verifications >= MAX_VERIFICATIONS_PER_FRAME) {
                            verificationStopped = true;
                        }
                        break;
                    }
                    if (evaluation.outcome == Evaluation.Outcome.DETECTION_STOP
                            || verificationWorkAtCap()) {
                        verificationStopped = true;
                        verificationBudgetExhausted = true;
                        break;
                    }
                    if (verifications >= MAX_VERIFICATIONS_PER_FRAME) {
                        // Transition to the attempt cap: the rejected cap attempt leaves the
                        // frame with conflicts and the final check below sets the flag.
                        verificationStopped = true;
                        break;
                    }
                }
                if (acceptedThisRound || verificationStopped) {
                    break;
                }
            }
            if (!acceptedThisRound) {
                break;
            }
            if (rounds >= MAX_RELOCATION_ROUNDS || verificationStopped
                    || verifications >= MAX_VERIFICATIONS_PER_FRAME) {
                break;
            }
            final IndexedDetection next = detect(projection, currentPositions, currentGeometry,
                visibleEndpoints);
            conflicts = next.detection();
            if (next.truncated()) {
                verificationStopped = true;
                verificationBudgetExhausted = true;
            }
        }

        final Detection residual;
        if (detectionTruncated) {
            residual = conflicts;
        }
        else if (rounds > 0) {
            residual = detectConflictsInternal(projection, currentPositions, currentGeometry,
                visibleEndpoints, false).detection();
        }
        else {
            residual = conflicts;
        }
        if (verificationStopped && !residual.keys().isEmpty() && !detectionTruncated) {
            verificationBudgetExhausted = true;
        }
        BoundarySeparationDiagnostics boundaryDiagnostics = boundaryResult.diagnostics();
        int nodeResidualViolations = boundaryResult.nodeResidualViolations();
        long rebuildNanos = 0L;
        if (rounds > 0) {
            final LayoutPositions published = canonicallySorted(currentPositions);
            final long rebuildStart = System.nanoTime();
            boundaryDiagnostics = boundaryInvariants.describe(projection, published, metrics, pins,
                currentGeometry, boundaryResult.diagnostics().residualHullPairs(),
                boundaryResult.diagnostics());
            nodeResidualViolations = new NodeSeparationProjection().project(projection, published,
                pinned).residualViolations();
            verificationWork += (long) boundaryDiagnostics.residualHullPairs().size()
                + (long) boundaryDiagnostics.hullResidualViolations()
                + (long) NodeSeparationProjection.violatingPairs(projection, published).size();
            rebuildNanos = System.nanoTime() - rebuildStart;
        }
        // The §5.4 rebuild's counted units are part of the frame total, so the diagnostics are
        // constructed only after the rebuild has added them (spec §13.1).
        final EdgeConflictDiagnostics diagnostics = new EdgeConflictDiagnostics(
            conflictsDetected, crossingsDetected, edgeOverNodeDetected,
            crossingsDetected - residual.crossings(), edgeOverNodeDetected - residual.edgeOverNodes(),
            residual.keys(), repairs, rounds, verifications, verificationBudgetExhausted,
            indexBudgetExhausted, detectionTruncated, indexInsertedItems, indexPairChecks,
            verificationWork, indexSubdivisions);
        return new EdgeConflictResult(currentPositions, diagnostics,
            new EdgeConflictTimings(0L, 0L, 0L, rebuildNanos), boundaryDiagnostics,
            nodeResidualViolations, appliedDisplacements(repairs));
    }

    private static LayoutPositions canonicallySorted(final LayoutPositions positions) {
        final List<ProjectedNodeKey> keys = new ArrayList<ProjectedNodeKey>(positions.nodes().keySet());
        Collections.sort(keys, NODE_ORDER);
        final Map<ProjectedNodeKey, LayoutPoint> nodes =
            new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        for (final ProjectedNodeKey key : keys) {
            nodes.put(key, positions.nodes().get(key));
        }
        return LayoutPositions.of(nodes, positions.anchors());
    }

    private static Map<String, LayoutPoint> appliedDisplacements(
            final List<EdgeConflictDiagnostics.Repair> repairs) {
        final Map<String, LayoutPoint> field = new TreeMap<String, LayoutPoint>();
        for (final EdgeConflictDiagnostics.Repair repair : repairs) {
            for (final ProjectedNodeKey key : repair.nodeKeys()) {
                final LayoutPoint from = repair.fromCentres().get(key);
                final LayoutPoint to = repair.toCentres().get(key);
                final String fieldKey = CanonicalKeys.nodeField(key);
                final LayoutPoint delta = LayoutPoint.of(to.x() - from.x(), to.y() - from.y());
                final LayoutPoint previous = field.get(fieldKey);
                field.put(fieldKey, previous == null ? delta
                    : LayoutPoint.of(previous.x() + delta.x(), previous.y() + delta.y()));
            }
        }
        final Map<String, LayoutPoint> result = new LinkedHashMap<String, LayoutPoint>();
        for (final Map.Entry<String, LayoutPoint> entry : field.entrySet()) {
            if (!isZero(entry.getValue())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static boolean isZero(final LayoutPoint value) {
        return value.x() == 0.0 && value.y() == 0.0;
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

    private static Map<ProjectedNodeKey, List<EnclosureHullKey>> enclosurePaths(
            final GraphProjection projection) {
        final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull =
            BoundaryInvariants.enclosuresByHull(projection);
        final Map<ProjectedNodeKey, List<EnclosureHullKey>> paths =
            new LinkedHashMap<ProjectedNodeKey, List<EnclosureHullKey>>();
        for (final ProjectedNode node : projection.nodes()) {
            paths.put(node.key(), new ArrayList<EnclosureHullKey>());
        }
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            for (final ProjectedNodeKey node : subtreeNodes(enclosure, enclosuresByHull)) {
                final List<EnclosureHullKey> path = paths.get(node);
                if (path != null) {
                    path.add(enclosure.hullKey());
                }
            }
        }
        for (final List<EnclosureHullKey> path : paths.values()) {
            Collections.sort(path, HULL_ORDER);
        }
        return paths;
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

    private static List<Candidate> generateCandidates(final Detection conflicts,
            final LayoutPositions positions, final Set<ProjectedNodeKey> pinned,
            final Map<ProjectedNodeKey, List<EnclosureHullKey>> enclosurePaths) {
        final List<Candidate> candidates = new ArrayList<Candidate>();
        for (final Conflict conflict : conflicts.conflicts()) {
            if (conflict.isCrossing()) {
                addCrossingCandidates(candidates, conflict, positions, pinned, enclosurePaths);
            }
            else {
                addEdgeOverNodeCandidate(candidates, conflict, positions, pinned);
            }
        }
        return candidates;
    }

    private static void addCrossingCandidates(final List<Candidate> candidates, final Conflict conflict,
            final LayoutPositions positions, final Set<ProjectedNodeKey> pinned,
            final Map<ProjectedNodeKey, List<EnclosureHullKey>> enclosurePaths) {
        final Chord first = conflict.firstChord();
        final Chord second = conflict.secondChord();
        if (first.firstNode == null || first.secondNode == null || second.firstNode == null
                || second.secondNode == null) {
            return;
        }
        final TreeSet<ProjectedNodeKey> sorted = new TreeSet<ProjectedNodeKey>(NODE_ORDER);
        sorted.add(first.firstNode);
        sorted.add(first.secondNode);
        sorted.add(second.firstNode);
        sorted.add(second.secondNode);
        if (sorted.size() != 4) {
            return;
        }
        final List<ProjectedNodeKey> keys = new ArrayList<ProjectedNodeKey>(sorted);
        final LayoutPoint[] centres = new LayoutPoint[keys.size()];
        double centroidX = 0.0;
        double centroidY = 0.0;
        for (int index = 0; index < keys.size(); index++) {
            centres[index] = positions.nodes().get(keys.get(index));
            if (centres[index] == null) {
                return;
            }
            centroidX += centres[index].x() / 4.0;
            centroidY += centres[index].y() / 4.0;
        }
        final Map<ProjectedNodeKey, Double> angles = new LinkedHashMap<ProjectedNodeKey, Double>();
        for (int index = 0; index < keys.size(); index++) {
            angles.put(keys.get(index), Double.valueOf(Math.atan2(centres[index].y() - centroidY,
                centres[index].x() - centroidX)));
        }
        final List<ProjectedNodeKey> cycle = new ArrayList<ProjectedNodeKey>(keys);
        Collections.sort(cycle, new Comparator<ProjectedNodeKey>() {
            @Override
            public int compare(final ProjectedNodeKey left, final ProjectedNodeKey right) {
                final int byAngle = Double.compare(angles.get(left).doubleValue(),
                    angles.get(right).doubleValue());
                if (byAngle != 0) {
                    return byAngle;
                }
                return NODE_ORDER.compare(left, right);
            }
        });
        final int start = cycle.indexOf(keys.get(0));
        final List<ProjectedNodeKey> corners = new ArrayList<ProjectedNodeKey>(4);
        for (int index = 0; index < 4; index++) {
            corners.add(cycle.get((start + index) % 4));
        }
        addSwapCandidate(candidates, corners.get(0), corners.get(1), positions, pinned, enclosurePaths);
        addSwapCandidate(candidates, corners.get(2), corners.get(3), positions, pinned, enclosurePaths);
        for (int index = 0; index < 4; index++) {
            addMirrorCandidate(candidates, corners.get(index), corners.get((index + 1) % 4),
                corners.get((index + 3) % 4), positions, pinned);
        }
    }

    private static void addSwapCandidate(final List<Candidate> candidates, final ProjectedNodeKey first,
            final ProjectedNodeKey second, final LayoutPositions positions,
            final Set<ProjectedNodeKey> pinned,
            final Map<ProjectedNodeKey, List<EnclosureHullKey>> enclosurePaths) {
        if (pinned.contains(first) || pinned.contains(second)) {
            return;
        }
        final List<EnclosureHullKey> firstPath = enclosurePaths.get(first);
        final List<EnclosureHullKey> secondPath = enclosurePaths.get(second);
        if (firstPath == null || secondPath == null || !firstPath.equals(secondPath)) {
            return;
        }
        final LayoutPoint firstCentre = positions.nodes().get(first);
        final LayoutPoint secondCentre = positions.nodes().get(second);
        if (firstCentre == null || secondCentre == null) {
            return;
        }
        final Map<ProjectedNodeKey, LayoutPoint> from =
            new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        from.put(first, firstCentre);
        from.put(second, secondCentre);
        final Map<ProjectedNodeKey, LayoutPoint> to =
            new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        to.put(first, secondCentre);
        to.put(second, firstCentre);
        final List<ProjectedNodeKey> moved = new ArrayList<ProjectedNodeKey>(2);
        moved.add(first);
        moved.add(second);
        candidates.add(new Candidate(EdgeConflictDiagnostics.RepairKind.SWAP, moved, from, to));
    }

    private static void addMirrorCandidate(final List<Candidate> candidates, final ProjectedNodeKey node,
            final ProjectedNodeKey lineFirst, final ProjectedNodeKey lineSecond,
            final LayoutPositions positions, final Set<ProjectedNodeKey> pinned) {
        if (pinned.contains(node)) {
            return;
        }
        final LayoutPoint centre = positions.nodes().get(node);
        final LayoutPoint lineA = positions.nodes().get(lineFirst);
        final LayoutPoint lineB = positions.nodes().get(lineSecond);
        if (centre == null || lineA == null || lineB == null) {
            return;
        }
        final double deltaX = lineB.x() - lineA.x();
        final double deltaY = lineB.y() - lineA.y();
        final double length = Math.hypot(deltaX, deltaY);
        if (!(length > 0.0) || !Double.isFinite(length)) {
            return;
        }
        double normalX = -deltaY / length;
        double normalY = deltaX / length;
        if (Double.compare(normalX, -normalX) > 0 || Double.compare(normalX, -normalX) == 0
                && Double.compare(normalY, -normalY) > 0) {
            normalX = -normalX;
            normalY = -normalY;
        }
        final double projection = (centre.x() - lineA.x()) * normalX
            + (centre.y() - lineA.y()) * normalY;
        final double reflectedX = centre.x() - 2.0 * projection * normalX;
        final double reflectedY = centre.y() - 2.0 * projection * normalY;
        final double outward = projection < 0.0 ? 1.0 : -1.0;
        final LayoutPoint target = LayoutPoint.of(
            reflectedX + outward * CROSSING_CLEARANCE * normalX,
            reflectedY + outward * CROSSING_CLEARANCE * normalY);
        final List<ProjectedNodeKey> moved = new ArrayList<ProjectedNodeKey>(1);
        moved.add(node);
        final Map<ProjectedNodeKey, LayoutPoint> from =
            new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        from.put(node, centre);
        final Map<ProjectedNodeKey, LayoutPoint> to =
            new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        to.put(node, target);
        candidates.add(new Candidate(EdgeConflictDiagnostics.RepairKind.MIRROR, moved, from, to));
    }

    private static void addEdgeOverNodeCandidate(final List<Candidate> candidates,
            final Conflict conflict, final LayoutPositions positions,
            final Set<ProjectedNodeKey> pinned) {
        final Chord chord = conflict.firstChord();
        final Disc disc = conflict.disc();
        if (pinned.contains(disc.key)) {
            return;
        }
        final double deltaX = chord.second.x() - chord.first.x();
        final double deltaY = chord.second.y() - chord.first.y();
        final double length = Math.hypot(deltaX, deltaY);
        if (!(length > 0.0) || !Double.isFinite(length)) {
            return;
        }
        double normalX = -deltaY / length;
        double normalY = deltaX / length;
        if (Double.compare(normalX, -normalX) > 0 || Double.compare(normalX, -normalX) == 0
                && Double.compare(normalY, -normalY) > 0) {
            normalX = -normalX;
            normalY = -normalY;
        }
        final LayoutPoint known = positions.nodes().get(disc.key);
        final LayoutPoint centre = known == null ? disc.center : known;
        final double projection = (centre.x() - chord.first.x()) * normalX
            + (centre.y() - chord.first.y()) * normalY;
        final double shift = disc.radius + EDGE_NODE_CLEARANCE - projection;
        final LayoutPoint target = LayoutPoint.of(centre.x() + shift * normalX,
            centre.y() + shift * normalY);
        final List<ProjectedNodeKey> moved = new ArrayList<ProjectedNodeKey>(1);
        moved.add(disc.key);
        final Map<ProjectedNodeKey, LayoutPoint> from =
            new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        from.put(disc.key, centre);
        final Map<ProjectedNodeKey, LayoutPoint> to =
            new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        to.put(disc.key, target);
        candidates.add(new Candidate(EdgeConflictDiagnostics.RepairKind.TRANSLATION, moved, from, to));
    }

    private static List<Candidate> greedyDisjoint(final List<Candidate> pool) {
        final List<Candidate> batch = new ArrayList<Candidate>();
        final Set<ProjectedNodeKey> moved = new LinkedHashSet<ProjectedNodeKey>();
        for (final Candidate candidate : pool) {
            boolean disjoint = true;
            for (final ProjectedNodeKey node : candidate.movedNodes) {
                if (moved.contains(node)) {
                    disjoint = false;
                    break;
                }
            }
            if (disjoint) {
                batch.add(candidate);
                moved.addAll(candidate.movedNodes);
            }
        }
        return batch;
    }

    private Evaluation verify(final List<Candidate> attempt, final GraphProjection projection,
            final LayoutPositions currentPositions, final GraphGeometry currentGeometry,
            final GeometryTextMetrics metrics, final Set<ProjectedEndpointKey> visibleEndpoints,
            final Detection currentConflicts, final BoundaryInvariants boundaryInvariants,
            final Map<ProjectedNodeKey, List<EnclosureHullKey>> nodeHullsByKey,
            final Map<ProjectedNodeKey, List<EnclosureHullKey>> enclosurePaths) {
        final Map<ProjectedNodeKey, LayoutPoint> toCentres =
            new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        for (final Candidate candidate : attempt) {
            toCentres.putAll(candidate.toCentres);
        }
        final LayoutPositions candidatePositions = withCentres(currentPositions, toCentres);
        final Set<EnclosureHullKey> changed = BoundaryInvariants.changedHulls(nodeHullsByKey,
            currentPositions, candidatePositions);
        if (!consumeVerificationWork(changed.size())) {
            return Evaluation.stopped();
        }
        final GraphGeometry candidateGeometry = geometryEngine.recomputeHulls(projection, candidatePositions,
            metrics, currentGeometry, changed);
        final IndexedDetection candidateDetection = detect(projection, candidatePositions,
            candidateGeometry, visibleEndpoints);
        if (candidateDetection.truncated()) {
            return Evaluation.stopped();
        }
        final Detection candidateConflicts = candidateDetection.detection();
        if (candidateConflicts.keys().size() >= currentConflicts.keys().size()
                || !new LinkedHashSet<String>(currentConflicts.keys())
                    .containsAll(candidateConflicts.keys())) {
            return Evaluation.rejected();
        }
        final CountedViolatingPairs currentPairs = NodeSeparationProjection.violatingPairsCounted(
            projection, currentPositions, verificationAllowance());
        verificationWork += currentPairs.pairEvaluations();
        if (currentPairs.truncated()) {
            return Evaluation.stopped();
        }
        final CountedViolatingPairs candidatePairs = NodeSeparationProjection.violatingPairsCounted(
            projection, candidatePositions, verificationAllowance());
        verificationWork += candidatePairs.pairEvaluations();
        if (candidatePairs.truncated()) {
            return Evaluation.stopped();
        }
        if (!new LinkedHashSet<String>(currentPairs.pairs()).containsAll(candidatePairs.pairs())) {
            return Evaluation.rejected();
        }
        final BoundaryInvariants.CountedPairKeys currentHullPairs = boundaryInvariants.detectPairKeys(
            projection, currentGeometry, verificationAllowance());
        verificationWork += currentHullPairs.work();
        if (currentHullPairs.truncated()) {
            return Evaluation.stopped();
        }
        final BoundaryInvariants.CountedPairKeys candidateHullPairs =
            boundaryInvariants.detectPairKeys(projection, candidateGeometry, verificationAllowance());
        verificationWork += candidateHullPairs.work();
        if (candidateHullPairs.truncated()) {
            return Evaluation.stopped();
        }
        if (!currentHullPairs.pairKeys().containsAll(candidateHullPairs.pairKeys())) {
            return Evaluation.rejected();
        }
        for (final Candidate candidate : attempt) {
            for (final ProjectedNodeKey node : candidate.movedNodes) {
                final List<EnclosureHullKey> path = enclosurePaths.get(node);
                if (path == null) {
                    return Evaluation.rejected();
                }
                for (final EnclosureHullKey hull : path) {
                    if (!candidateGeometry.hulls().containsKey(hull)) {
                        return Evaluation.rejected();
                    }
                }
            }
        }
        return new Evaluation(candidatePositions, candidateGeometry);
    }

    private int verificationAllowance() {
        final long remaining = EDGE_CONFLICT_MAX_VERIFICATION_WORK_PER_FRAME - verificationWork;
        if (remaining <= 0L) {
            return 0;
        }
        return (int) Math.min(remaining, (long) Integer.MAX_VALUE);
    }

    private boolean consumeVerificationWork(final long units) {
        if (verificationWork + units > EDGE_CONFLICT_MAX_VERIFICATION_WORK_PER_FRAME) {
            return false;
        }
        verificationWork += units;
        return true;
    }

    private boolean verificationWorkAtCap() {
        return verificationWork >= EDGE_CONFLICT_MAX_VERIFICATION_WORK_PER_FRAME;
    }

    private static LayoutPositions withCentres(final LayoutPositions positions,
            final Map<ProjectedNodeKey, LayoutPoint> centres) {
        final Map<ProjectedNodeKey, LayoutPoint> nodes =
            new LinkedHashMap<ProjectedNodeKey, LayoutPoint>(positions.nodes());
        for (final Map.Entry<ProjectedNodeKey, LayoutPoint> entry : centres.entrySet()) {
            nodes.put(entry.getKey(), entry.getValue());
        }
        return LayoutPositions.of(nodes, positions.anchors());
    }

    private static void recordRepair(final List<EdgeConflictDiagnostics.Repair> repairs,
            final List<Candidate> attempt, final int roundIndex) {
        for (final Candidate candidate : attempt) {
            // One Repair per candidate keeps `kind` exact even when a greedy batch combines
            // candidates of different repair kinds.
            repairs.add(new EdgeConflictDiagnostics.Repair(candidate.kind,
                new ArrayList<ProjectedNodeKey>(candidate.toCentres.keySet()), candidate.fromCentres,
                candidate.toCentres, roundIndex));
        }
    }

    private void resetFrameCounters() {
        indexInsertedItems = 0L;
        indexPairChecks = 0L;
        indexSubdivisions = 0;
        indexBudgetExhausted = false;
        detectedKeys = 0;
        verificationWork = 0L;
    }

    static Detection detectConflicts(final GraphProjection projection, final LayoutPositions positions,
            final GraphGeometry geometry, final Set<ProjectedEndpointKey> visibleEndpoints) {
        return new EdgeConflictCorrection()
            .detectConflictsInternal(projection, positions, geometry, visibleEndpoints, false)
            .detection();
    }

    IndexedDetection detect(final GraphProjection projection, final LayoutPositions positions,
            final GraphGeometry geometry, final Set<ProjectedEndpointKey> visibleEndpoints) {
        final DetectionRun run =
            detectConflictsInternal(projection, positions, geometry, visibleEndpoints, true);
        return new IndexedDetection(run.detection(), run.truncated());
    }

    private DetectionRun detectConflictsInternal(final GraphProjection projection,
            final LayoutPositions positions, final GraphGeometry geometry,
            final Set<ProjectedEndpointKey> visibleEndpoints, final boolean bounded) {
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(positions, "positions");
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(visibleEndpoints, "visibleEndpoints");
        final List<Chord> chords = chords(projection, positions, geometry, visibleEndpoints);
        if (chords.isEmpty()) {
            return new DetectionRun(Detection.empty(), false);
        }
        final List<Disc> discs = discs(projection, geometry);
        final double originX = originX(chords, discs);
        final double originY = originY(chords, discs);
        final double baseCellSize = baseCellSize(chords, discs, originX, originY);
        final DetectionAccumulator accumulator = new DetectionAccumulator(bounded);
        try {
            int level = 0;
            IndexPass pass = collect(level, chords, discs, originX, originY, baseCellSize, bounded);
            while (hasOverCap(pass) && level < CONFLICT_MAX_SUBDIVISION_DEPTH) {
                indexSubdivisions += overCapCount(pass);
                level++;
                pass = collect(level, chords, discs, originX, originY, baseCellSize, bounded);
            }
            if (hasOverCap(pass)) {
                indexBudgetExhausted = true;
            }
            if (!pass.outermost.isEmpty()) {
                indexBudgetExhausted = true;
            }
            for (final List<Primitive> bucket : pass.cells.values()) {
                for (int first = 0; first < bucket.size(); first++) {
                    for (int second = first + 1; second < bucket.size(); second++) {
                        accumulator.examine(bucket.get(first), bucket.get(second));
                    }
                }
            }
            for (final Primitive primitive : pass.outermost) {
                if (primitive.chord != null) {
                    for (final Chord other : chords) {
                        if (other != primitive.chord) {
                            countDirectExamination(bounded);
                            accumulator.examineChords(primitive.chord, other);
                        }
                    }
                    for (final Disc disc : discs) {
                        countDirectExamination(bounded);
                        accumulator.examineChordDisc(primitive.chord, disc);
                    }
                }
                else {
                    for (final Chord chord : chords) {
                        countDirectExamination(bounded);
                        accumulator.examineChordDisc(chord, primitive.disc);
                    }
                }
            }
            return new DetectionRun(accumulator.snapshot(), false);
        }
        catch (final DetectionStop stop) {
            return new DetectionRun(accumulator.snapshot(), true);
        }
    }

    private void countDirectExamination(final boolean bounded) {
        if (bounded && indexInsertedItems >= EDGE_CONFLICT_MAX_INDEX_ITEMS_PER_FRAME) {
            throw new DetectionStop();
        }
        indexInsertedItems++;
    }

    private static List<Chord> chords(final GraphProjection projection, final LayoutPositions positions,
            final GraphGeometry geometry, final Set<ProjectedEndpointKey> visibleEndpoints) {
        final Map<EnclosureKey, EnclosureHullKey> hullKeys = enclosureHullKeys(projection, geometry);
        final List<ProjectedEdge> ordered = new ArrayList<ProjectedEdge>(projection.edges());
        Collections.sort(ordered, new Comparator<ProjectedEdge>() {
            @Override
            public int compare(final ProjectedEdge first, final ProjectedEdge second) {
                return CanonicalKeys.edge(first.key()).compareTo(CanonicalKeys.edge(second.key()));
            }
        });
        final List<Chord> result = new ArrayList<Chord>();
        for (final ProjectedEdge edge : ordered) {
            if (!visibleEndpoints.contains(edge.first()) || !visibleEndpoints.contains(edge.second())) {
                continue;
            }
            final LayoutPoint firstAnchor = anchor(edge.first(), hullKeys, positions, geometry);
            final LayoutPoint secondAnchor = anchor(edge.second(), hullKeys, positions, geometry);
            if (firstAnchor == null || secondAnchor == null) {
                continue;
            }
            try {
                final LayoutPoint first = geometry.edgeAttachment(edge.first(), secondAnchor);
                final LayoutPoint second = geometry.edgeAttachment(edge.second(), firstAnchor);
                if (isFinite(first) && isFinite(second)) {
                    result.add(new Chord(edge.key(), first, second, nodeKey(edge.first()),
                        nodeKey(edge.second())));
                }
            }
            catch (final IllegalArgumentException exception) {
                // A partially resolved endpoint has no drawable attachment.
            }
        }
        return result;
    }

    private static List<Disc> discs(final GraphProjection projection, final GraphGeometry geometry) {
        final List<ProjectedNode> ordered = new ArrayList<ProjectedNode>(projection.nodes());
        Collections.sort(ordered, new Comparator<ProjectedNode>() {
            @Override
            public int compare(final ProjectedNode first, final ProjectedNode second) {
                return CanonicalKeys.node(first.key()).compareTo(CanonicalKeys.node(second.key()));
            }
        });
        final List<Disc> result = new ArrayList<Disc>();
        for (final ProjectedNode node : ordered) {
            final NodeGeometry nodeGeometry = geometry.nodes().get(node.key());
            if (nodeGeometry != null) {
                result.add(new Disc(node.key(), nodeGeometry.center(), nodeGeometry.radius()));
            }
        }
        return result;
    }

    private static LayoutPoint anchor(final ProjectedEndpointKey endpoint,
            final Map<EnclosureKey, EnclosureHullKey> hullKeys, final LayoutPositions positions,
            final GraphGeometry geometry) {
        if (endpoint.isNode()) {
            final NodeGeometry node = geometry.nodes().get(endpoint.node().get());
            return node == null ? null : node.center();
        }
        final EnclosureHullKey hullKey = hullKeys.get(endpoint.enclosure().get());
        if (hullKey == null) {
            return null;
        }
        final LayoutPoint layoutAnchor = positions.anchors().get(hullKey);
        if (isFinite(layoutAnchor)) {
            return layoutAnchor;
        }
        final HullGeometry hull = geometry.hulls().get(hullKey);
        final LayoutPoint labelAnchor = hull == null ? null : hull.labelAnchor();
        return isFinite(labelAnchor) ? labelAnchor : null;
    }

    private static Map<EnclosureKey, EnclosureHullKey> enclosureHullKeys(final GraphProjection projection,
            final GraphGeometry geometry) {
        final Map<EnclosureKey, EnclosureHullKey> hullKeys =
            new LinkedHashMap<EnclosureKey, EnclosureHullKey>();
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            final EnclosureHullKey hullKey = enclosure.hullKey();
            if (geometry.hulls().get(hullKey) == null) {
                continue;
            }
            for (final EnclosureKey endpoint : enclosure.endpointKeys()) {
                hullKeys.put(endpoint, hullKey);
            }
        }
        return hullKeys;
    }

    private static ProjectedNodeKey nodeKey(final ProjectedEndpointKey endpoint) {
        return endpoint.isNode() ? endpoint.node().get() : null;
    }

    private static double originX(final List<Chord> chords, final List<Disc> discs) {
        double minimum = Double.POSITIVE_INFINITY;
        for (final Chord chord : chords) {
            minimum = Math.min(minimum, Math.min(chord.first.x(), chord.second.x()));
        }
        for (final Disc disc : discs) {
            minimum = Math.min(minimum, disc.center.x() - disc.radius - EDGE_NODE_CLEARANCE);
        }
        return minimum;
    }

    private static double originY(final List<Chord> chords, final List<Disc> discs) {
        double minimum = Double.POSITIVE_INFINITY;
        for (final Chord chord : chords) {
            minimum = Math.min(minimum, Math.min(chord.first.y(), chord.second.y()));
        }
        for (final Disc disc : discs) {
            minimum = Math.min(minimum, disc.center.y() - disc.radius - EDGE_NODE_CLEARANCE);
        }
        return minimum;
    }

    private static double baseCellSize(final List<Chord> chords, final List<Disc> discs,
            final double originX, final double originY) {
        double maximumX = Double.NEGATIVE_INFINITY;
        double maximumY = Double.NEGATIVE_INFINITY;
        for (final Chord chord : chords) {
            maximumX = Math.max(maximumX, Math.max(chord.first.x(), chord.second.x()));
            maximumY = Math.max(maximumY, Math.max(chord.first.y(), chord.second.y()));
        }
        for (final Disc disc : discs) {
            final double inflation = disc.radius + EDGE_NODE_CLEARANCE;
            maximumX = Math.max(maximumX, disc.center.x() + inflation);
            maximumY = Math.max(maximumY, disc.center.y() + inflation);
        }
        final double span = Math.max(maximumX - originX, maximumY - originY);
        if (!(span > 0.0) || !Double.isFinite(span)) {
            return CONFLICT_CELL_SIZE;
        }
        return Math.max(CONFLICT_CELL_SIZE,
            Math.min(span / (double) CONFLICT_GRID_DIMENSION, CONFLICT_MAX_CELL_SIZE));
    }

    private IndexPass collect(final int level, final List<Chord> chords, final List<Disc> discs,
            final double originX, final double originY, final double baseCellSize,
            final boolean bounded) {
        final double cellSize = baseCellSize / Math.scalb(1.0, level);
        final IndexPass pass = new IndexPass();
        for (final Chord chord : chords) {
            insertChord(pass, chord, originX, originY, cellSize, bounded);
        }
        for (final Disc disc : discs) {
            insertDisc(pass, disc, originX, originY, cellSize, bounded);
        }
        return pass;
    }

    private void insertChord(final IndexPass pass, final Chord chord, final double originX,
            final double originY, final double cellSize, final boolean bounded) {
        final int firstX = cellIndex(chord.first.x(), originX, cellSize);
        final int firstY = cellIndex(chord.first.y(), originY, cellSize);
        final int secondX = cellIndex(chord.second.x(), originX, cellSize);
        final int secondY = cellIndex(chord.second.y(), originY, cellSize);
        if (firstX == OUTERMOST_CELL_INDEX || firstY == OUTERMOST_CELL_INDEX
                || secondX == OUTERMOST_CELL_INDEX || secondY == OUTERMOST_CELL_INDEX
                || Math.max(Math.abs((long) secondX - firstX), Math.abs((long) secondY - firstY)) + 1L
                    > CONFLICT_MAX_CELLS_PER_PRIMITIVE) {
            pass.outermost.add(new Primitive(chord));
            return;
        }
        final Primitive primitive = new Primitive(chord);
        final double deltaX = chord.second.x() - chord.first.x();
        final double deltaY = chord.second.y() - chord.first.y();
        // The step direction follows the cell indices, not the raw deltas: a chord whose
        // endpoints share one cell index but differ by a sub-cell delta must not step that axis
        // (the axis interval is convex, so the segment cannot leave the column). Following the
        // sign of a tiny delta made the traversal step away from `secondX` and never terminate.
        final int stepX = Integer.compare(secondX, firstX);
        final int stepY = Integer.compare(secondY, firstY);
        double tMaxX = stepX == 0 ? Double.POSITIVE_INFINITY
            : boundaryTime(chord.first.x(), originX, firstX, stepX, cellSize, deltaX);
        double tMaxY = stepY == 0 ? Double.POSITIVE_INFINITY
            : boundaryTime(chord.first.y(), originY, firstY, stepY, cellSize, deltaY);
        final double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : cellSize / Math.abs(deltaX);
        final double tDeltaY = stepY == 0 ? Double.POSITIVE_INFINITY : cellSize / Math.abs(deltaY);
        int cellX = firstX;
        int cellY = firstY;
        while (true) {
            addCell(pass, primitive, cellX, cellY, bounded);
            if (cellX == secondX && cellY == secondY) {
                return;
            }
            if (tMaxX < tMaxY) {
                cellX += stepX;
                tMaxX += tDeltaX;
            }
            else if (tMaxY < tMaxX) {
                cellY += stepY;
                tMaxY += tDeltaY;
            }
            else {
                // The segment passes through the corner shared by four cells; the closed
                // squares of the two adjacent cells contain that corner too, so they must be
                // inserted as well or a pair crossing exactly at a grid corner is lost.
                if (stepX != 0) {
                    addCell(pass, primitive, cellX + stepX, cellY, bounded);
                }
                if (stepY != 0) {
                    addCell(pass, primitive, cellX, cellY + stepY, bounded);
                }
                cellX += stepX;
                cellY += stepY;
                tMaxX += tDeltaX;
                tMaxY += tDeltaY;
            }
        }
    }

    private static double boundaryTime(final double start, final double origin, final int cell,
            final int step, final double cellSize, final double delta) {
        final double boundary = origin + (double) (step > 0 ? cell + 1 : cell) * cellSize;
        final double distance = step > 0 ? boundary - start : start - boundary;
        return distance / Math.abs(delta);
    }

    private void insertDisc(final IndexPass pass, final Disc disc, final double originX,
            final double originY, final double cellSize, final boolean bounded) {
        final double inflation = disc.radius + EDGE_NODE_CLEARANCE;
        final double minX = disc.center.x() - inflation;
        final double maxX = disc.center.x() + inflation;
        final double minY = disc.center.y() - inflation;
        final double maxY = disc.center.y() + inflation;
        final int firstX = cellIndex(minX, originX, cellSize);
        final int firstY = cellIndex(minY, originY, cellSize);
        final int lastX = cellIndex(maxX, originX, cellSize);
        final int lastY = cellIndex(maxY, originY, cellSize);
        if (firstX == OUTERMOST_CELL_INDEX || firstY == OUTERMOST_CELL_INDEX
                || lastX == OUTERMOST_CELL_INDEX || lastY == OUTERMOST_CELL_INDEX
                || ((long) (lastX - firstX) + 1L) * ((long) (lastY - firstY) + 1L)
                    > CONFLICT_MAX_CELLS_PER_PRIMITIVE) {
            pass.outermost.add(new Primitive(disc));
            return;
        }
        final Primitive primitive = new Primitive(disc);
        for (int cellX = firstX; cellX <= lastX; cellX++) {
            final double cellMinX = originX + (double) cellX * cellSize;
            final double cellMaxX = cellMinX + cellSize;
            for (int cellY = firstY; cellY <= lastY; cellY++) {
                final double cellMinY = originY + (double) cellY * cellSize;
                final double cellMaxY = cellMinY + cellSize;
                if (squareDistance(disc.center, cellMinX, cellMinY, cellMaxX, cellMaxY) <= inflation) {
                    addCell(pass, primitive, cellX, cellY, bounded);
                }
            }
        }
    }

    private static double squareDistance(final LayoutPoint center, final double minX, final double minY,
            final double maxX, final double maxY) {
        final double dx = Math.max(0.0, Math.max(minX - center.x(), center.x() - maxX));
        final double dy = Math.max(0.0, Math.max(minY - center.y(), center.y() - maxY));
        return Math.hypot(dx, dy);
    }

    private void addCell(final IndexPass pass, final Primitive primitive, final int cellX,
            final int cellY, final boolean bounded) {
        if (bounded && indexInsertedItems >= EDGE_CONFLICT_MAX_INDEX_ITEMS_PER_FRAME) {
            throw new DetectionStop();
        }
        final Long key = Long.valueOf(cellKey(cellX, cellY));
        List<Primitive> bucket = pass.cells.get(key);
        if (bucket == null) {
            bucket = new ArrayList<Primitive>();
            pass.cells.put(key, bucket);
        }
        bucket.add(primitive);
        pass.insertedItems++;
        indexInsertedItems++;
    }

    private static long cellKey(final int cellX, final int cellY) {
        return ((long) cellX << 32) | (cellY & 0xffffffffL);
    }

    private static int cellIndex(final double value, final double origin, final double cellSize) {
        final double quotient = (value - origin) / cellSize;
        if (!(quotient > 0.0)) {
            return 0;
        }
        if (quotient >= OUTERMOST_CELL_INDEX_DOUBLE) {
            return OUTERMOST_CELL_INDEX;
        }
        return (int) Math.floor(quotient);
    }

    private static boolean hasOverCap(final IndexPass pass) {
        for (final List<Primitive> bucket : pass.cells.values()) {
            if (bucket.size() > CONFLICT_CELL_CAP) {
                return true;
            }
        }
        return false;
    }

    private static int overCapCount(final IndexPass pass) {
        int count = 0;
        for (final List<Primitive> bucket : pass.cells.values()) {
            if (bucket.size() > CONFLICT_CELL_CAP) {
                count++;
            }
        }
        return count;
    }

    private static boolean properlyCross(final LayoutPoint a, final LayoutPoint b, final LayoutPoint c,
            final LayoutPoint d) {
        final int exponent = commonExponent(a, b, c, d);
        return orientationSign(exponent, a, b, c) * orientationSign(exponent, a, b, d) < 0
            && orientationSign(exponent, c, d, a) * orientationSign(exponent, c, d, b) < 0;
    }

    private static int orientationSign(final int exponent, final LayoutPoint p, final LayoutPoint q,
            final LayoutPoint r) {
        final double px = Math.scalb(p.x(), -exponent);
        final double py = Math.scalb(p.y(), -exponent);
        final double qx = Math.scalb(q.x(), -exponent);
        final double qy = Math.scalb(q.y(), -exponent);
        final double rx = Math.scalb(r.x(), -exponent);
        final double ry = Math.scalb(r.y(), -exponent);
        final double cross = (qx - px) * (ry - py) - (qy - py) * (rx - px);
        return cross == 0.0 ? 0 : Double.compare(cross, 0.0);
    }

    private static int commonExponent(final LayoutPoint... points) {
        int exponent = Integer.MIN_VALUE;
        for (final LayoutPoint point : points) {
            exponent = Math.max(exponent, coordinateExponent(point.x()));
            exponent = Math.max(exponent, coordinateExponent(point.y()));
        }
        return exponent == Integer.MIN_VALUE ? 0 : exponent;
    }

    private static int coordinateExponent(final double value) {
        return value == 0.0 ? Integer.MIN_VALUE : Math.getExponent(value);
    }

    private static double segmentDistance(final LayoutPoint first, final LayoutPoint second,
            final LayoutPoint point) {
        final double edgeX = second.x() - first.x();
        final double edgeY = second.y() - first.y();
        if (edgeX == 0.0 && edgeY == 0.0) {
            return Math.hypot(point.x() - first.x(), point.y() - first.y());
        }
        final int exponent = Math.max(coordinateExponent(edgeX), coordinateExponent(edgeY));
        final double scaledX = Math.scalb(edgeX, -exponent);
        final double scaledY = Math.scalb(edgeY, -exponent);
        final double towardX = Math.scalb(point.x() - first.x(), -exponent);
        final double towardY = Math.scalb(point.y() - first.y(), -exponent);
        final double lengthSquared = scaledX * scaledX + scaledY * scaledY;
        if (!(lengthSquared > 0.0) || !Double.isFinite(lengthSquared)) {
            return Math.hypot(point.x() - first.x(), point.y() - first.y());
        }
        final double projection = (towardX * scaledX + towardY * scaledY) / lengthSquared;
        if (projection <= 0.0) {
            return Math.hypot(point.x() - first.x(), point.y() - first.y());
        }
        if (projection >= 1.0) {
            return Math.hypot(point.x() - second.x(), point.y() - second.y());
        }
        final double cross = scaledX * towardY - scaledY * towardX;
        return Math.scalb(Math.abs(cross) / Math.hypot(scaledX, scaledY), exponent);
    }

    private static boolean isFinite(final LayoutPoint point) {
        return point != null && Double.isFinite(point.x()) && Double.isFinite(point.y());
    }

    static final class Detection {
        private static final Detection EMPTY = new Detection(Collections.<String>emptyList(), 0, 0,
            Collections.<Conflict>emptyList());

        private final List<String> keys;
        private final int crossings;
        private final int edgeOverNodes;
        private final List<Conflict> conflicts;

        private Detection(final List<String> keys, final int crossings, final int edgeOverNodes,
                final List<Conflict> conflicts) {
            this.keys = Collections.unmodifiableList(new ArrayList<String>(keys));
            this.crossings = crossings;
            this.edgeOverNodes = edgeOverNodes;
            this.conflicts = Collections.unmodifiableList(new ArrayList<Conflict>(conflicts));
        }

        static Detection empty() {
            return EMPTY;
        }

        List<String> keys() {
            return keys;
        }

        int crossings() {
            return crossings;
        }

        int edgeOverNodes() {
            return edgeOverNodes;
        }

        List<Conflict> conflicts() {
            return conflicts;
        }
    }

    static final class IndexedDetection {
        private final Detection detection;
        private final boolean truncated;

        private IndexedDetection(final Detection detection, final boolean truncated) {
            this.detection = detection;
            this.truncated = truncated;
        }

        Detection detection() {
            return detection;
        }

        boolean truncated() {
            return truncated;
        }
    }

    private static final class DetectionRun {
        private final Detection detection;
        private final boolean truncated;

        private DetectionRun(final Detection detection, final boolean truncated) {
            this.detection = detection;
            this.truncated = truncated;
        }

        private Detection detection() {
            return detection;
        }

        private boolean truncated() {
            return truncated;
        }
    }

    private static final class DetectionStop extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private DetectionStop() {
            super(null, null, false, false);
        }
    }

    private static final class Chord {
        private final ProjectedEdgeKey key;
        private final LayoutPoint first;
        private final LayoutPoint second;
        private final ProjectedNodeKey firstNode;
        private final ProjectedNodeKey secondNode;

        private Chord(final ProjectedEdgeKey key, final LayoutPoint first, final LayoutPoint second,
                final ProjectedNodeKey firstNode, final ProjectedNodeKey secondNode) {
            this.key = key;
            this.first = first;
            this.second = second;
            this.firstNode = firstNode;
            this.secondNode = secondNode;
        }
    }

    private static final class Disc {
        private final ProjectedNodeKey key;
        private final LayoutPoint center;
        private final double radius;

        private Disc(final ProjectedNodeKey key, final LayoutPoint center, final double radius) {
            this.key = key;
            this.center = center;
            this.radius = radius;
        }
    }

    private static final class Conflict {
        private final Chord firstChord;
        private final Chord secondChord;
        private final Disc disc;

        private Conflict(final Chord firstChord, final Chord secondChord, final Disc disc) {
            this.firstChord = firstChord;
            this.secondChord = secondChord;
            this.disc = disc;
        }

        static Conflict crossing(final Chord first, final Chord second) {
            return new Conflict(first, second, null);
        }

        static Conflict edgeOverNode(final Chord chord, final Disc disc) {
            return new Conflict(chord, null, disc);
        }

        boolean isCrossing() {
            return secondChord != null;
        }

        Chord firstChord() {
            return firstChord;
        }

        Chord secondChord() {
            return secondChord;
        }

        Disc disc() {
            return disc;
        }
    }

    private static final class Candidate {
        private final EdgeConflictDiagnostics.RepairKind kind;
        private final List<ProjectedNodeKey> movedNodes;
        private final Map<ProjectedNodeKey, LayoutPoint> fromCentres;
        private final Map<ProjectedNodeKey, LayoutPoint> toCentres;

        private Candidate(final EdgeConflictDiagnostics.RepairKind kind,
                final List<ProjectedNodeKey> movedNodes,
                final Map<ProjectedNodeKey, LayoutPoint> fromCentres,
                final Map<ProjectedNodeKey, LayoutPoint> toCentres) {
            this.kind = kind;
            this.movedNodes = Collections.unmodifiableList(
                new ArrayList<ProjectedNodeKey>(movedNodes));
            this.fromCentres = Collections.unmodifiableMap(
                new LinkedHashMap<ProjectedNodeKey, LayoutPoint>(fromCentres));
            this.toCentres = Collections.unmodifiableMap(
                new LinkedHashMap<ProjectedNodeKey, LayoutPoint>(toCentres));
        }
    }

    private static final class Evaluation {
        private enum Outcome {
            ACCEPT,
            REJECT,
            DETECTION_STOP
        }

        private final Outcome outcome;
        private final LayoutPositions positions;
        private final GraphGeometry geometry;

        private Evaluation(final Outcome outcome, final LayoutPositions positions,
                final GraphGeometry geometry) {
            this.outcome = outcome;
            this.positions = positions;
            this.geometry = geometry;
        }

        static Evaluation rejected() {
            return new Evaluation(Outcome.REJECT, null, null);
        }

        static Evaluation stopped() {
            return new Evaluation(Outcome.DETECTION_STOP, null, null);
        }

        private Evaluation(final LayoutPositions positions, final GraphGeometry geometry) {
            this(Outcome.ACCEPT, positions, geometry);
        }
    }

    private static final class Primitive {
        private final Chord chord;
        private final Disc disc;

        private Primitive(final Chord chord) {
            this.chord = chord;
            this.disc = null;
        }

        private Primitive(final Disc disc) {
            this.chord = null;
            this.disc = disc;
        }
    }

    private static final class IndexPass {
        private final Map<Long, List<Primitive>> cells = new TreeMap<Long, List<Primitive>>();
        private final List<Primitive> outermost = new ArrayList<Primitive>();
        private long insertedItems;
    }

    private final class DetectionAccumulator {
        private final boolean bounded;
        private final Set<String> examined = new HashSet<String>();
        private final TreeSet<String> keys = new TreeSet<String>();
        private final Map<String, Conflict> conflicts = new TreeMap<String, Conflict>();
        private int crossings;
        private int edgeOverNodes;

        private DetectionAccumulator(final boolean bounded) {
            this.bounded = bounded;
        }

        private void examine(final Primitive first, final Primitive second) {
            if (first.chord != null && second.chord != null) {
                examineChords(first.chord, second.chord);
            }
            else if (first.chord != null && second.disc != null) {
                examineChordDisc(first.chord, second.disc);
            }
            else if (first.disc != null && second.chord != null) {
                examineChordDisc(second.chord, first.disc);
            }
        }

        private void examineChords(final Chord first, final Chord second) {
            final String key = CanonicalKeys.edgePair(first.key, second.key);
            if (!examined.add(key)) {
                return;
            }
            if (bounded && indexPairChecks >= EDGE_CONFLICT_MAX_PAIR_CHECKS_PER_FRAME) {
                throw new DetectionStop();
            }
            indexPairChecks++;
            if (properlyCross(first.first, first.second, second.first, second.second)) {
                recordConflict(key, Conflict.crossing(first, second), true);
            }
        }

        private void examineChordDisc(final Chord chord, final Disc disc) {
            if (disc.key.equals(chord.firstNode) || disc.key.equals(chord.secondNode)) {
                return;
            }
            final String key = CanonicalKeys.edgeNodePair(chord.key, disc.key);
            if (!examined.add(key)) {
                return;
            }
            if (bounded && indexPairChecks >= EDGE_CONFLICT_MAX_PAIR_CHECKS_PER_FRAME) {
                throw new DetectionStop();
            }
            indexPairChecks++;
            if (segmentDistance(chord.first, chord.second, disc.center) - disc.radius
                    < EDGE_NODE_CLEARANCE - ROUTE_EPSILON) {
                recordConflict(key, Conflict.edgeOverNode(chord, disc), false);
            }
        }

        private void recordConflict(final String key, final Conflict conflict, final boolean crossing) {
            if (bounded && detectedKeys >= EDGE_CONFLICT_MAX_DETECTED_PER_FRAME) {
                throw new DetectionStop();
            }
            if (keys.add(key)) {
                detectedKeys++;
                if (crossing) {
                    crossings++;
                }
                else {
                    edgeOverNodes++;
                }
                conflicts.put(key, conflict);
            }
        }

        private Detection snapshot() {
            return new Detection(new ArrayList<String>(keys), crossings, edgeOverNodes,
                new ArrayList<Conflict>(conflicts.values()));
        }
    }
}
