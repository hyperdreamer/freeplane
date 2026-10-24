package org.freeplane.plugin.graph.layout.graphstream;

import static org.assertj.core.api.Assertions.*;
import static org.freeplane.plugin.graph.layout.graphstream.InitialSpacingMetrics.*;
import java.lang.reflect.Field;
import java.util.*;
import org.freeplane.plugin.graph.geometry.*;
import org.freeplane.plugin.graph.layout.*;
import org.freeplane.plugin.graph.projection.*;
import org.graphstream.ui.geom.Vector3;

final class InitialSpacingConsumers {
    static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    static LayoutPoint composed(GraphStreamLayoutEngine.BoundarySizes sizes, EnclosureHullKey key) {
        ProjectedEnclosure enclosure = sizes.enclosure(key);
        if (enclosure.mapRoot() || !enclosure.parentHull().isPresent()) {
            ClusterPacking.ClusterPlacement p = sizes.topLevelPlacementOf(key);
            assertThat(p).isNotNull();
            return LayoutPoint.of(p.x + p.item.anchorX, p.y + p.item.anchorY);
        }
        LayoutPoint parent = composed(sizes, enclosure.parentHull().get());
        ClusterPacking.ClusterPlacement p = sizes.placementOf(enclosure.parentHull().get(), key);
        assertThat(p).isNotNull();
        return LayoutPoint.of(parent.x() + p.x + p.item.anchorX, parent.y() + p.y + p.item.anchorY);
    }

    static void raw(GraphProjection p, LayoutFrame raw, double gap) {
        assertThat(raw.failed()).isFalse();
        GraphStreamLayoutEngine.BoundarySizes sizes = new GraphStreamLayoutEngine.BoundarySizes(p);
        Set<EnclosureHullKey> top = new LinkedHashSet<EnclosureHullKey>();
        for (ProjectedEnclosure e : p.enclosures()) {
            if (e.mapRoot() || !e.parentHull().isPresent()) top.add(e.hullKey());
            double r = analyticRadius(p, e, gap);
            close(sizes.contentRingRadius(e.hullKey()), r);
            LayoutPoint anchor = composed(sizes, e.hullKey());
            close(distance(raw.positions().anchors().get(e.hullKey()), anchor), 0.0);
            List<ClusterPacking.ClusterPlacement> placed = sizes.placements(e.hullKey());
            if (!e.directNodes().isEmpty()) {
                ClusterPacking.ClusterPlacement content = placed.get(0);
                assertThat(content.item.anchorKey).isNull();
                double maxRadius = 0.0;
                for (ProjectedNodeKey key : e.directNodes()) maxRadius = Math.max(maxRadius, radius(p, key));
                close(content.item.width, 2.0 * (r + maxRadius + 8.0));
                close(content.item.height, 2.0 * (r + maxRadius + 8.0));
            }
            int n = e.directNodes().size();
            for (int i = 0; i < n; i++) {
                ProjectedNodeKey key = e.directNodes().get(i);
                PinProjection pin = null;
                for (PinProjection candidate : p.pins()) if (candidate.active()
                        && candidate.projectedNode().get().equals(key)) pin = candidate;
                double angle = 2.0 * Math.PI * i / n;
                LayoutPoint expected = pin == null
                    ? LayoutPoint.of(anchor.x() + r * Math.cos(angle), anchor.y() + r * Math.sin(angle))
                    : LayoutPoint.of(pin.x(), pin.y());
                close(distance(raw.positions().nodes().get(key), expected), 0.0);
                if (n > 1 && pin == null) {
                    ProjectedNodeKey next = e.directNodes().get((i + 1) % n);
                    double maximum = 0.0;
                    for (ProjectedNodeKey k : e.directNodes()) maximum = Math.max(maximum, radius(p, k));
                    boolean anyPin = false;
                    for (PinProjection q : p.pins()) if (q.active()
                            && e.directNodes().contains(q.projectedNode().get())) anyPin = true;
                    if (!anyPin) close(distance(raw.positions().nodes().get(key), raw.positions().nodes().get(next))
                        - radius(p, key) - radius(p, next), gap + 2.0 * maximum - radius(p, key) - radius(p, next));
                }
            }
            footprint(sizes, e);
            packing(placed);
        }
        Set<EnclosureHullKey> actualTop = new LinkedHashSet<EnclosureHullKey>();
        for (ClusterPacking.ClusterPlacement placement : sizes.topLevelPlacements()) {
            assertThat(actualTop.add(placement.item.anchorKey)).isTrue();
        }
        assertThat(actualTop).isEqualTo(top);
        assertThat(raw.positions().anchors().keySet()).hasSize(p.enclosures().size());
        assertThat(raw.positions().nodes().keySet()).hasSize(p.nodes().size());
        packing(sizes.topLevelPlacements());
    }

    private static void footprint(GraphStreamLayoutEngine.BoundarySizes sizes, ProjectedEnclosure e) {
        GraphStreamLayoutEngine.BoundarySizes.Footprint f = sizes.footprint(e.hullKey());
        finite(f.width); finite(f.height); finite(f.anchorX); finite(f.anchorY);
        if (e.directNodes().isEmpty() && e.directEnclosures().isEmpty()) {
            double width = 16.0;
            for (org.freeplane.plugin.graph.projection.input.SafeNodeLabel label : e.labels())
                width = Math.max(width, label.displayText().length() * 16.0 + 16.0);
            close(f.width, width); close(f.height, 40.0);
            close(f.anchorX, 0.0); close(f.anchorY, 0.0);
            return;
        }
        double minX = Double.POSITIVE_INFINITY, minY = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX;
        for (ClusterPacking.ClusterPlacement item : sizes.placements(e.hullKey())) {
            minX = Math.min(minX, item.x - item.item.width / 2.0);
            maxX = Math.max(maxX, item.x + item.item.width / 2.0);
            minY = Math.min(minY, item.y - item.item.height / 2.0);
            maxY = Math.max(maxY, item.y + item.item.height / 2.0);
        }
        close(f.width, maxX - minX + 32.0); close(f.height, maxY - minY + 32.0);
        close(f.anchorX, -(minX + maxX) / 2.0); close(f.anchorY, -(minY + maxY) / 2.0);
    }

    private static void packing(List<ClusterPacking.ClusterPlacement> placements) {
        for (int i = 0; i < placements.size(); i++) {
            ClusterPacking.ClusterPlacement a = placements.get(i);
            for (int j = i + 1; j < placements.size(); j++) {
                ClusterPacking.ClusterPlacement b = placements.get(j);
                double x = Math.abs(a.x - b.x) - (a.item.width + b.item.width) / 2.0;
                double y = Math.abs(a.y - b.y) - (a.item.height + b.item.height) / 2.0;
                assertThat(Math.max(x, y)).isGreaterThanOrEqualTo(8.0 - 1e-9);
            }
            for (ClusterPacking.ClusterItem contact : a.contacts) {
                ClusterPacking.ClusterPlacement b = null;
                for (ClusterPacking.ClusterPlacement candidate : placements) if (candidate.item == contact) b = candidate;
                assertThat(b).isNotNull();
                double x = Math.abs(a.x - b.x) - (a.item.width + b.item.width) / 2.0;
                double y = Math.abs(a.y - b.y) - (a.item.height + b.item.height) / 2.0;
                assertThat(Math.max(x, y)).isBetween(8.0 - 1e-9, 8.75);
            }
        }
    }

    @SuppressWarnings("unchecked")
    static void topology(GraphProjection p, GraphStreamLayoutEngine engine, double ringGap, double edgeGap) throws Exception {
        GraphStreamLayoutEngine.BoundarySizes sizes = new GraphStreamLayoutEngine.BoundarySizes(p);
        Map<String, GraphStreamLayoutEngine.ParticleState> particles =
            (Map<String, GraphStreamLayoutEngine.ParticleState>) field(engine, "particles");
        TypedSpringBox box = (TypedSpringBox) field(engine, "springBox");
        Map<String, TypedNodeParticle> configured = (Map<String, TypedNodeParticle>) field(box, "typedParticles");
        Map<String, GraphStreamLayoutEngine.ForceLink> links =
            (Map<String, GraphStreamLayoutEngine.ForceLink>) field(box, "typedLinks");
        Map<ProjectedNodeKey, String> nodeIds = new LinkedHashMap<ProjectedNodeKey, String>();
        Map<EnclosureHullKey, String> anchorIds = new LinkedHashMap<EnclosureHullKey, String>();
        for (GraphStreamLayoutEngine.ParticleState state : particles.values()) {
            if (state.nodeKey != null) {
                nodeIds.put(state.nodeKey, state.id);
                close(state.radius, radius(p, state.nodeKey));
            }
            else {
                anchorIds.put(state.anchorKey, state.id);
                close(state.radius, 0.0);
                TypedNodeParticle actual = configured.get(state.id);
                GraphStreamLayoutEngine.BoundarySizes.Footprint f = sizes.footprint(state.anchorKey);
                close(actual.separationHalfWidth(), f.width / 2.0);
                close(actual.separationHalfHeight(), f.height / 2.0);
                close(actual.separationOffsetX(), -f.anchorX);
                close(actual.separationOffsetY(), -f.anchorY);
                close(actual.separationRadius(), 0.0);
            }
        }
        assertThat(nodeIds).hasSize(p.nodes().size()); assertThat(anchorIds).hasSize(p.enclosures().size());
        Set<String> expected = new LinkedHashSet<String>();
        for (ProjectedEnclosure e : p.enclosures()) {
            for (ProjectedNodeKey node : e.directNodes())
                expected.add(signature(GraphStreamLayoutEngine.ForceKind.CONTAINMENT, anchorIds.get(e.hullKey()), nodeIds.get(node)));
            if (e.parentHull().isPresent())
                expected.add(signature(GraphStreamLayoutEngine.ForceKind.HIERARCHY, anchorIds.get(e.parentHull().get()), anchorIds.get(e.hullKey())));
        }
        Set<String> actual = new LinkedHashSet<String>();
        int relationships = 0;
        for (GraphStreamLayoutEngine.ForceLink link : links.values()) {
            finite(link.restLength);
            if (link.kind == GraphStreamLayoutEngine.ForceKind.RELATIONSHIP) {
                relationships++;
                List<ProjectedEdge> matched = new ArrayList<ProjectedEdge>();
                for (ProjectedEdge e : p.edges()) {
                    String a = id(p, e.first(), nodeIds, anchorIds), b = id(p, e.second(), nodeIds, anchorIds);
                    if (signature(link.kind, a, b).equals(signature(link.kind, link.firstId, link.secondId))) matched.add(e);
                }
                assertThat(matched).hasSize(1);
                ProjectedEdge edge = matched.get(0);
                double first = endpointRadius(p, edge.first(), ringGap), second = endpointRadius(p, edge.second(), ringGap);
                double relationshipGap = edge.first().isNode() && edge.second().isNode() ? edgeGap : 6.0;
                close(link.restLength, first + second + relationshipGap);
                close(link.restLength - first - second, relationshipGap);
                assertThat(link.crossMap).isEqualTo(!edge.first().mapReferenceId().equals(edge.second().mapReferenceId()));
                System.out.printf(java.util.Locale.ROOT, "REST\t%s\t%s\t%s\t%.17g\t%.17g\t%.17g%n",
                    CanonicalKeys.endpoint(edge.first()), CanonicalKeys.endpoint(edge.second()), link.crossMap, first, second, link.restLength);
                continue;
            }
            assertThat(actual.add(signature(link.kind, link.firstId, link.secondId))).isTrue();
            if (link.kind == GraphStreamLayoutEngine.ForceKind.CONTAINMENT) {
                ProjectedEnclosure enclosure = sizes.enclosure(particles.get(link.firstId).anchorKey);
                assertThat(link.secondId).isEqualTo(nodeIds.get(particles.get(link.secondId).nodeKey));
                assertThat(enclosure.directNodes()).contains(particles.get(link.secondId).nodeKey);
                close(link.restLength, analyticRadius(p, enclosure, ringGap));
            }
            else {
                EnclosureHullKey parent = particles.get(link.firstId).anchorKey;
                EnclosureHullKey child = particles.get(link.secondId).anchorKey;
                assertThat(sizes.enclosure(child).parentHull().get()).isEqualTo(parent);
                ClusterPacking.ClusterPlacement placement = sizes.placementOf(parent, child);
                close(link.restLength, Math.hypot(placement.x + placement.item.anchorX, placement.y + placement.item.anchorY));
            }
        }
        assertThat(actual).isEqualTo(expected);
        assertThat(relationships).isEqualTo(p.edges().size());
    }

    private static String signature(GraphStreamLayoutEngine.ForceKind kind, String a, String b) {
        assertThat(a).isNotNull(); assertThat(b).isNotNull();
        return kind + ":" + (a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a);
    }

    private static String id(GraphProjection p, ProjectedEndpointKey endpoint,
            Map<ProjectedNodeKey, String> nodes, Map<EnclosureHullKey, String> anchors) {
        return endpoint.isNode() ? nodes.get(endpoint.node().get()) : anchors.get(endpointHull(p, endpoint));
    }

    static EnclosureHullKey endpointHull(GraphProjection p, ProjectedEndpointKey endpoint) {
        List<EnclosureHullKey> found = new ArrayList<EnclosureHullKey>();
        for (ProjectedEnclosure enclosure : p.enclosures())
            if (enclosure.endpointKeys().contains(endpoint.enclosure().get())) found.add(enclosure.hullKey());
        assertThat(found).hasSize(1);
        return found.get(0);
    }

    static double endpointRadius(GraphProjection p, ProjectedEndpointKey endpoint, double gap) {
        if (endpoint.isNode()) return radius(p, endpoint.node().get());
        EnclosureHullKey key = endpointHull(p, endpoint);
        for (ProjectedEnclosure e : p.enclosures()) if (e.hullKey().equals(key)) return analyticRadius(p, e, gap);
        throw new AssertionError("Unresolved enclosure endpoint " + endpoint);
    }

    static void repulsion() throws Exception {
        for (boolean ancestor : new boolean[] {false, true}) {
            try (GraphStreamLayoutEngine engine = new GraphStreamLayoutEngine(LayoutCalibration.spikeDefaults())) {
                GraphProjection p = InitialSpacingFixtures.matrix(false, "ee", false);
                engine.apply(InitialSpacingFixtures.request(p, p));
                TypedSpringBox box = (TypedSpringBox) field(engine, "springBox");
                @SuppressWarnings("unchecked") Map<String, TypedNodeParticle> particles =
                    (Map<String, TypedNodeParticle>) field(box, "typedParticles");
                @SuppressWarnings("unchecked") Map<String, GraphStreamLayoutEngine.ParticleState> states =
                    (Map<String, GraphStreamLayoutEngine.ParticleState>) field(engine, "particles");
                List<String> anchors = new ArrayList<String>();
                for (GraphStreamLayoutEngine.ParticleState state : states.values()) if (state.anchorKey != null) anchors.add(state.id);
                assertThat(anchors).hasSize(2);
                String a = anchors.get(0), b = anchors.get(1);
                if (ancestor) box.configureParticle(b, 0.0, false, true, a);
                box.configureAnchorExtents(a, 20.0, 10.0, 3.0, -2.0);
                box.configureAnchorExtents(b, 30.0, 40.0, -7.0, 6.0);
                // Box centres coincide: both overlap magnitudes are 58; x wins the tie.
                box.setParticlePosition(a, -3.0, 2.0);
                box.setParticlePosition(b, 7.0, -6.0);
                Vector3 displacement = new Vector3();
                box.addBoundaryRepulsion(particles.get(a), displacement);
                close(displacement.at(0), ancestor ? 0.0 : 29.0);
                close(displacement.at(1), 0.0);
                // Make x overlap 58 and y overlap 18: the independently smaller y push is 9.
                box.setParticlePosition(a, -3.0, 42.0);
                displacement = new Vector3();
                box.addBoundaryRepulsion(particles.get(a), displacement);
                close(displacement.at(0), 0.0);
                close(displacement.at(1), ancestor ? 0.0 : 9.0);
            }
        }
    }
}
