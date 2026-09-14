package org.freeplane.plugin.graph.geometry;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.geom.Dimension2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.RelationshipResolution;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.junit.Test;

public class GraphGeometryEngineShould {
    private static final MapReferenceId MAP =
        MapReferenceId.of("00000000-0000-0000-0000-000000000001");

    // Mutation: measuring a suppressed label (the metrics reject it by design) previously aborted the
    // hull computation for the root-only scene after unmarking the last group marker, leaving the
    // canvas stale forever.
    @Test
    public void computesAHullForAnEmptySuppressedEnclosureWithoutMeasuringItsLabel() {
        EnclosureHullKey rootKey = hullKey("root");
        ProjectedEnclosure root = ProjectedEnclosure.of(rootKey, rootKey.endpointKeys(),
            Arrays.asList(SafeNodeLabel.of("Map full", "Map")), "Map",
            Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(),
            Collections.<EnclosureHullKey>emptyList(), true, BoundaryTier.SUPPRESSED);
        GraphProjection projection = projection(root);
        LayoutPositions positions = LayoutPositions.of(
            Collections.<ProjectedNodeKey, LayoutPoint>emptyMap(),
            Collections.singletonMap(rootKey, LayoutPoint.of(0.0, 0.0)));
        RecordingMetrics metrics = new RecordingMetrics();

        GraphGeometry geometry = new GraphGeometryEngine().computeHulls(projection, positions, metrics);

        assertThat(geometry.hulls()).containsKey(rootKey);
        assertThat(metrics.measuredTexts()).as("suppressed labels must never be measured").isEmpty();
    }

    private static GraphProjection projection(final ProjectedEnclosure enclosure) {
        return GraphProjection.projected(1, Collections.<ProjectedNode>emptyList(),
            Collections.singletonList(enclosure), Collections.<ProjectedEdge>emptyList(),
            Collections.<RelationshipResolution>emptyList(), Collections.<PinProjection>emptyList());
    }

    private static EnclosureHullKey hullKey(final String id) {
        return EnclosureHullKey.of(Collections.singletonList(EnclosureKey.of(SourceNodeKey.persisted(
            NodeReference.of(MAP, PersistedNodeId.of(id))))));
    }

    private static final class RecordingMetrics implements GeometryTextMetrics {
        private final List<String> measuredTexts = new ArrayList<String>();

        @Override
        public Dimension2D measure(final String displayText, final BoundaryTier tier) {
            measuredTexts.add(displayText);
            return new Dimension2D() {
                @Override
                public double getWidth() {
                    return 10.0;
                }

                @Override
                public double getHeight() {
                    return 5.0;
                }

                @Override
                public void setSize(final double width, final double height) {
                    throw new UnsupportedOperationException("Recording metrics are immutable");
                }
            };
        }

        private List<String> measuredTexts() {
            return measuredTexts;
        }
    }

    @Test
    public void recomputeHullsReusesUnaffectedHullsAndMatchesTheFullComputation() {
        EnclosureHullKey rootKey = hullKey("root");
        EnclosureHullKey firstKey = hullKey("first");
        EnclosureHullKey leafKey = hullKey("leaf");
        EnclosureHullKey otherKey = hullKey("other");
        ProjectedNodeKey leafNode = nodeKey("leaf-node");
        ProjectedNodeKey otherNode = nodeKey("other-node");
        ProjectedEnclosure root = enclosure(rootKey, "root", Optional.<EnclosureHullKey>empty(),
            Collections.<ProjectedNodeKey>emptyList(), Arrays.asList(firstKey, otherKey), BoundaryTier.SUBTLE);
        ProjectedEnclosure first = enclosure(firstKey, "first", Optional.of(rootKey),
            Collections.<ProjectedNodeKey>emptyList(), Collections.singletonList(leafKey), BoundaryTier.SUBTLE);
        ProjectedEnclosure leaf = enclosure(leafKey, "leaf", Optional.of(firstKey),
            Collections.singletonList(leafNode), Collections.<EnclosureHullKey>emptyList(), BoundaryTier.SUBTLE);
        ProjectedEnclosure other = enclosure(otherKey, "other", Optional.of(rootKey),
            Collections.singletonList(otherNode), Collections.<EnclosureHullKey>emptyList(), BoundaryTier.SUBTLE);
        GraphProjection projection = GraphProjection.projected(1L,
            Arrays.asList(ProjectedNode.of(leafNode, SafeNodeLabel.of("leaf", "leaf"), "map", false),
                ProjectedNode.of(otherNode, SafeNodeLabel.of("other", "other"), "map", false)),
            Arrays.asList(root, first, leaf, other), Collections.<ProjectedEdge>emptyList(),
            Collections.<RelationshipResolution>emptyList(), Collections.<PinProjection>emptyList());
        GeometryTextMetrics metrics = new RecordingMetrics();
        GraphGeometryEngine engine = new GraphGeometryEngine();
        GraphGeometry full = engine.computeHulls(projection,
            geometryLayout(leafNode, 0.0, otherNode, 100.0, rootKey, firstKey, leafKey, otherKey), metrics);

        GraphGeometry recomputed = engine.recomputeHulls(projection,
            geometryLayout(leafNode, 10.0, otherNode, 100.0, rootKey, firstKey, leafKey, otherKey), metrics,
            full, Collections.singleton(leafKey));

        assertThat(recomputed).isEqualTo(new GraphGeometryEngine().computeHulls(projection,
            geometryLayout(leafNode, 10.0, otherNode, 100.0, rootKey, firstKey, leafKey, otherKey), metrics));
        assertThat(recomputed.hulls().get(otherKey)).isSameAs(full.hulls().get(otherKey));
        assertThat(recomputed.hulls().get(leafKey)).isNotSameAs(full.hulls().get(leafKey));
        assertThat(recomputed.hulls().get(firstKey)).isNotSameAs(full.hulls().get(firstKey));
        assertThat(recomputed.hulls().get(rootKey)).isNotSameAs(full.hulls().get(rootKey));
        assertThat(new GraphGeometryEngine().recomputeHulls(projection,
            geometryLayout(leafNode, 0.0, otherNode, 100.0, rootKey, firstKey, leafKey, otherKey), metrics, null,
            Collections.<EnclosureHullKey>emptySet())).isEqualTo(full);
    }

    private static ProjectedNodeKey nodeKey(final String id) {
        return ProjectedNodeKey.of(SourceNodeKey.persisted(NodeReference.of(MAP, PersistedNodeId.of(id))));
    }

    private static ProjectedEnclosure enclosure(final EnclosureHullKey hull, final String label,
            final Optional<EnclosureHullKey> parent, final List<ProjectedNodeKey> nodes,
            final List<EnclosureHullKey> children, final BoundaryTier tier) {
        return ProjectedEnclosure.of(hull, hull.endpointKeys(),
            Collections.singletonList(SafeNodeLabel.of(label, label)), "map", parent, nodes, children, false,
            tier);
    }

    private static LayoutPositions geometryLayout(final ProjectedNodeKey leafNode, final double leafX,
            final ProjectedNodeKey otherNode, final double otherX, final EnclosureHullKey rootKey,
            final EnclosureHullKey firstKey, final EnclosureHullKey leafKey,
            final EnclosureHullKey otherKey) {
        final Map<ProjectedNodeKey, LayoutPoint> nodes = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        nodes.put(leafNode, LayoutPoint.of(leafX, 0.0));
        nodes.put(otherNode, LayoutPoint.of(otherX, 0.0));
        final Map<EnclosureHullKey, LayoutPoint> anchors = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        anchors.put(rootKey, LayoutPoint.of(0.0, 0.0));
        anchors.put(firstKey, LayoutPoint.of(0.0, 0.0));
        anchors.put(leafKey, LayoutPoint.of(0.0, 0.0));
        anchors.put(otherKey, LayoutPoint.of(otherX, 0.0));
        return LayoutPositions.of(nodes, anchors);
    }
}
