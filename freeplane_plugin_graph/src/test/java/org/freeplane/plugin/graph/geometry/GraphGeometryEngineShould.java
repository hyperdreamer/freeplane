package org.freeplane.plugin.graph.geometry;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.geom.Dimension2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
}
