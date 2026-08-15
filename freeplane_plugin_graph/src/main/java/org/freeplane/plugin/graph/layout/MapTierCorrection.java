package org.freeplane.plugin.graph.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.freeplane.plugin.graph.geometry.GraphGeometry;
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

public final class MapTierCorrection {
    public CorrectionResult apply(final GraphProjection projection, final LayoutPositions positions,
            final GraphGeometry geometry) {
        Objects.requireNonNull(projection, "projection");
        return apply(projection, positions, geometry, projection.pins());
    }

    CorrectionResult apply(final GraphProjection projection, final LayoutPositions positions,
            final GraphGeometry geometry, final List<PinProjection> pins) {
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(positions, "positions");
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(pins, "pins");

        final List<ProjectedEnclosure> roots = mapRoots(projection);
        final Set<MapReferenceId> rigidMaps = rigidMaps(pins);
        final LinkedHashMap<MapReferenceId, LayoutPoint> deltas =
            new LinkedHashMap<MapReferenceId, LayoutPoint>();
        final List<LayoutConflict> conflicts = new ArrayList<LayoutConflict>();

        for (int firstIndex = 0; firstIndex < roots.size(); firstIndex++) {
            final ProjectedEnclosure first = roots.get(firstIndex);
            final HullGeometry firstHull = geometry.hulls().get(first.hullKey());
            if (firstHull == null) {
                continue;
            }
            for (int secondIndex = firstIndex + 1; secondIndex < roots.size(); secondIndex++) {
                final ProjectedEnclosure second = roots.get(secondIndex);
                if (first.mapReferenceId().equals(second.mapReferenceId())) {
                    continue;
                }
                final HullGeometry secondHull = geometry.hulls().get(second.hullKey());
                if (secondHull == null) {
                    continue;
                }
                final LayoutPoint translation =
                    HullIntersection.minimumSeparatingTranslation(firstHull, secondHull);
                if (isZero(translation)) {
                    continue;
                }
                final boolean firstRigid = rigidMaps.contains(first.mapReferenceId());
                final boolean secondRigid = rigidMaps.contains(second.mapReferenceId());
                if (firstRigid && secondRigid) {
                    conflicts.add(new LayoutConflict(first.mapReferenceId(), second.mapReferenceId(),
                        blockingPins(pins, first.mapReferenceId(), second.mapReferenceId())));
                }
                else if (firstRigid) {
                    addDelta(deltas, second.mapReferenceId(), translation);
                }
                else if (secondRigid) {
                    addDelta(deltas, first.mapReferenceId(), negate(translation));
                }
                else {
                    addDelta(deltas, first.mapReferenceId(), scale(translation, -0.5));
                    addDelta(deltas, second.mapReferenceId(), scale(translation, 0.5));
                }
            }
        }

        return new CorrectionResult(applyDeltas(positions, deltas), conflicts);
    }

    private static List<ProjectedEnclosure> mapRoots(final GraphProjection projection) {
        final List<ProjectedEnclosure> roots = new ArrayList<ProjectedEnclosure>();
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            if (enclosure.mapRoot() && enclosure.boundaryTier() != BoundaryTier.SUPPRESSED) {
                roots.add(enclosure);
            }
        }
        return roots;
    }

    private static Set<MapReferenceId> rigidMaps(final List<PinProjection> pins) {
        final Set<MapReferenceId> result = new LinkedHashSet<MapReferenceId>();
        for (final PinProjection pin : pins) {
            final PinProjection value = Objects.requireNonNull(pin, "pins entry");
            if (value.active()) {
                result.add(value.projectedNode().get().mapReferenceId());
            }
        }
        return result;
    }

    private static List<PinProjection> blockingPins(final List<PinProjection> pins,
            final MapReferenceId firstMap, final MapReferenceId secondMap) {
        final List<PinProjection> result = new ArrayList<PinProjection>();
        for (final PinProjection pin : pins) {
            final PinProjection value = Objects.requireNonNull(pin, "pins entry");
            if (value.active()) {
                final MapReferenceId map = value.projectedNode().get().mapReferenceId();
                if (firstMap.equals(map) || secondMap.equals(map)) {
                    result.add(value);
                }
            }
        }
        return result;
    }

    private static void addDelta(final Map<MapReferenceId, LayoutPoint> deltas,
            final MapReferenceId map, final LayoutPoint delta) {
        final LayoutPoint previous = deltas.get(map);
        deltas.put(map, previous == null ? delta : add(previous, delta));
    }

    private static LayoutPositions applyDeltas(final LayoutPositions positions,
            final Map<MapReferenceId, LayoutPoint> deltas) {
        final Map<ProjectedNodeKey, LayoutPoint> nodes =
            new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        for (final Map.Entry<ProjectedNodeKey, LayoutPoint> entry : positions.nodes().entrySet()) {
            nodes.put(entry.getKey(), translated(entry.getValue(), deltas.get(entry.getKey().mapReferenceId())));
        }
        final Map<EnclosureHullKey, LayoutPoint> anchors =
            new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        for (final Map.Entry<EnclosureHullKey, LayoutPoint> entry : positions.anchors().entrySet()) {
            anchors.put(entry.getKey(), translated(entry.getValue(), deltas.get(entry.getKey().mapReferenceId())));
        }
        return LayoutPositions.of(nodes, anchors);
    }

    private static LayoutPoint translated(final LayoutPoint point, final LayoutPoint delta) {
        return delta == null ? point : add(point, delta);
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

    private static boolean isZero(final LayoutPoint value) {
        return value.x() == 0.0 && value.y() == 0.0;
    }

    public static final class CorrectionResult {
        private final LayoutPositions positions;
        private final List<LayoutConflict> conflicts;

        private CorrectionResult(final LayoutPositions positions, final List<LayoutConflict> conflicts) {
            this.positions = Objects.requireNonNull(positions, "positions");
            Objects.requireNonNull(conflicts, "conflicts");
            final List<LayoutConflict> copy = new ArrayList<LayoutConflict>(conflicts.size());
            for (final LayoutConflict conflict : conflicts) {
                copy.add(Objects.requireNonNull(conflict, "conflicts entry"));
            }
            this.conflicts = Collections.unmodifiableList(copy);
        }

        public LayoutPositions positions() {
            return positions;
        }

        public List<LayoutConflict> conflicts() {
            return conflicts;
        }
    }
}
