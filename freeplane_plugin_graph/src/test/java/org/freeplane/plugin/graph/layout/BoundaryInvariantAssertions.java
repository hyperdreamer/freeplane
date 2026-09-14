package org.freeplane.plugin.graph.layout;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.freeplane.plugin.graph.geometry.GeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.HullGeometry;
import org.freeplane.plugin.graph.geometry.HullIntersection;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;

final class BoundaryInvariantAssertions {
    private BoundaryInvariantAssertions() {
    }

    static void assertVerifiedFrame(final GraphProjection projection, final LayoutFrame frame,
            final BoundarySeparationDiagnostics diagnostics, final GeometryTextMetrics metrics,
            final List<PinProjection> pins) {
        assertThat(frame.failed()).as("verified frames must not be failed").isFalse();
        assertThat(diagnostics.boundaryVerified()).as("helper is scoped to boundary-verified frames").isTrue();
        final GraphGeometry geometry = new org.freeplane.plugin.graph.geometry.GraphGeometryEngine()
            .computeHulls(projection, frame.positions(), metrics);
        final Map<EnclosureHullKey, EnclosureHullKey> parents = parents(projection);
        final List<EnclosureHullKey> enforced = enforced(projection, geometry);
        for (int first = 0; first < enforced.size(); first++) {
            for (int second = first + 1; second < enforced.size(); second++) {
                final EnclosureHullKey firstHull = enforced.get(first);
                final EnclosureHullKey secondHull = enforced.get(second);
                if (related(parents, firstHull, secondHull)) {
                    continue;
                }
                final HullGeometry firstGeometry = geometry.hulls().get(firstHull);
                final HullGeometry secondGeometry = geometry.hulls().get(secondHull);
                final LayoutPoint translation = HullIntersection.minimumSeparatingTranslation(firstGeometry,
                    secondGeometry);
                final boolean overlap = HullIntersection.siblingOverlap(firstGeometry, secondGeometry)
                    || containsInclusive(firstGeometry, secondGeometry)
                    || containsInclusive(secondGeometry, firstGeometry);
                assertThat(overlap && !(translation.x() == 0.0 && translation.y() == 0.0))
                    .as("non-nested hull pair must not cross: %s <-> %s",
                        CanonicalLayoutKeys.hull(firstHull), CanonicalLayoutKeys.hull(secondHull))
                    .isFalse();
            }
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
            for (final LayoutPoint vertex : geometry.hulls().get(child).exactPolygon()) {
                assertThat(geometry.hulls().get(parent).contains(vertex))
                    .as("child vertex must lie inside its parent hull: %s in %s",
                        CanonicalLayoutKeys.hull(child), CanonicalLayoutKeys.hull(parent))
                    .isTrue();
            }
        }
        for (final PinProjection pin : pins) {
            if (pin.active()) {
                final ProjectedNodeKey node = pin.projectedNode().get();
                assertThat(frame.positions().nodes().get(node))
                    .as("pin %s must keep its stored coordinates", pin.source().nodeId().value())
                    .isEqualTo(LayoutPoint.of(pin.x(), pin.y()));
            }
        }
        final Set<ProjectedNodeKey> pinned = new LinkedHashSet<ProjectedNodeKey>();
        for (final PinProjection pin : pins) {
            if (pin.active()) {
                pinned.add(pin.projectedNode().get());
            }
        }
        final NodeSeparationResult separation = new NodeSeparationProjection().project(projection,
            frame.positions(), pinned);
        assertThat(frame.residualViolations()).as("frame node residual must match the frame geometry")
            .isEqualTo(separation.residualViolations());
        assertBijection(diagnostics);
    }

    static void assertBijection(final BoundarySeparationDiagnostics diagnostics) {
        final List<String> conflictPairs = new ArrayList<String>();
        for (final BoundaryConflict conflict : diagnostics.conflicts()) {
            conflictPairs.add(conflict.pairKey());
        }
        assertThat(conflictPairs).as("conflict pair keys must be unique").doesNotHaveDuplicates();
        assertThat(diagnostics.residualHullPairs()).as("residual pair keys must be unique")
            .doesNotHaveDuplicates();
        assertThat(new LinkedHashSet<String>(conflictPairs))
            .as("every residual pair has exactly one conflict and no conflict is outside the residual set")
            .isEqualTo(new LinkedHashSet<String>(diagnostics.residualHullPairs()));
        assertThat(diagnostics.boundaryCovered()).isTrue();
    }

    private static List<EnclosureHullKey> enforced(final GraphProjection projection,
            final GraphGeometry geometry) {
        final List<EnclosureHullKey> result = new ArrayList<EnclosureHullKey>();
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            if (enclosure.boundaryTier() != BoundaryTier.SUPPRESSED
                    && geometry.hulls().get(enclosure.hullKey()) != null) {
                result.add(enclosure.hullKey());
            }
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
}
