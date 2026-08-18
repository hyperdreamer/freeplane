package org.freeplane.plugin.graph.canvas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.freeplane.plugin.graph.control.CanvasState;
import org.freeplane.plugin.graph.control.OperationalStatus;
import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.geometry.NodeGeometry;
import org.freeplane.plugin.graph.layout.LayoutFrame;
import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;

import org.junit.Test;

public class GraphSearchModelShould {
    @Test
    public void searchFullSafeTextAndMapNamesInStableEndpointOrder() {
        final Fixture fixture = Fixture.create();

        final Set<ProjectedEndpointKey> results = GraphSearchModel.search(fixture.state, "  LONG ");
        assertThat(results).containsExactly(fixture.nodeEndpoint);
        assertThat(GraphSearchModel.search(fixture.state, "NORTH MAP"))
            .containsExactly(fixture.nodeEndpoint);
        assertThat(GraphSearchModel.search(fixture.state, "enclosure safe"))
            .containsExactly(fixture.enclosureEndpoint);
        assertThat(GraphSearchModel.search(fixture.state, "second enclosure"))
            .containsExactly(fixture.secondEnclosureEndpoint);

        final List<ProjectedEndpointKey> ordered = new ArrayList<ProjectedEndpointKey>(
            GraphSearchModel.search(fixture.state, "map"));
        final List<ProjectedEndpointKey> sorted = new ArrayList<ProjectedEndpointKey>(ordered);
        Collections.sort(sorted);
        assertThat(ordered).isEqualTo(sorted);
    }

    @Test
    public void excludeAProjectedSourceIdentityWhileFindingProjectedSafeText() {
        final Fixture fixture = Fixture.create();

        assertThat(GraphSearchModel.search(fixture.state, fixture.sourceIdentitySentinel))
            .isEmpty();
        assertThat(GraphSearchModel.search(fixture.state, "A very long safe label"))
            .containsExactly(fixture.nodeEndpoint);
    }

    @Test
    public void returnAnEmptyUnmodifiableSetForBlankOrUnprojectedText() {
        final Fixture fixture = Fixture.create();

        assertThat(GraphSearchModel.search(fixture.state, " \t\n")).isEmpty();
        assertThat(GraphSearchModel.search(fixture.state, fixture.sourceIdentitySentinel))
            .isEmpty();
        assertThat(GraphSearchModel.search(fixture.state, "suppressed label")).isEmpty();
        assertThat(GraphSearchModel.tooltip(fixture.state, fixture.suppressedEndpoint)).isNull();
        assertThatThrownBy(() -> GraphSearchModel.search(fixture.state, "long")
            .add(fixture.nodeEndpoint)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void exposeOnlyFullSafeTooltipTextAndOwningMap() {
        final Fixture fixture = Fixture.create();

        final String tooltip = GraphSearchModel.tooltip(fixture.state, fixture.nodeEndpoint);
        assertThat(tooltip).contains("A very long safe label");
        assertThat(tooltip).contains("North Map");
        assertThat(tooltip).doesNotContain("A very...");
        assertThat(GraphSearchModel.tooltip(fixture.state, fixture.secondEnclosureEndpoint))
            .contains("Second Enclosure Label").contains("South Map")
            .doesNotContain("Enclosure Safe Label");
        assertThat(GraphSearchModel.tooltip(fixture.state,
            ProjectedEndpointKey.ofNode(fixture.otherNodeKey))).isNull();
    }

    private static final class Fixture {
        private final CanvasState state;
        private final ProjectedEndpointKey nodeEndpoint;
        private final ProjectedEndpointKey enclosureEndpoint;
        private final ProjectedEndpointKey secondEnclosureEndpoint;
        private final ProjectedEndpointKey suppressedEndpoint;
        private final ProjectedNodeKey otherNodeKey;
        private final String sourceIdentitySentinel;

        private Fixture(CanvasState state, ProjectedEndpointKey nodeEndpoint,
                ProjectedEndpointKey enclosureEndpoint,
                ProjectedEndpointKey secondEnclosureEndpoint,
                ProjectedEndpointKey suppressedEndpoint, ProjectedNodeKey otherNodeKey,
                String sourceIdentitySentinel) {
            this.state = state;
            this.nodeEndpoint = nodeEndpoint;
            this.enclosureEndpoint = enclosureEndpoint;
            this.secondEnclosureEndpoint = secondEnclosureEndpoint;
            this.suppressedEndpoint = suppressedEndpoint;
            this.otherNodeKey = otherNodeKey;
            this.sourceIdentitySentinel = sourceIdentitySentinel;
        }

        private static Fixture create() {
            final MapReferenceId north = MapReferenceId.of(
                UUID.fromString("00000000-0000-0000-0000-000000000001"));
            final MapReferenceId south = MapReferenceId.of(
                UUID.fromString("00000000-0000-0000-0000-000000000002"));
            final ProjectedNodeKey nodeKey = ProjectedNodeKey.of(
                SourceNodeKey.transientPath(north, Arrays.asList(0)));
            final ProjectedNodeKey otherNodeKey = ProjectedNodeKey.of(
                SourceNodeKey.transientPath(north, Arrays.asList(1)));
            final EnclosureKey enclosureKey = EnclosureKey.of(
                SourceNodeKey.transientPath(south, Arrays.asList(0)));
            final EnclosureKey secondEnclosureKey = EnclosureKey.of(
                SourceNodeKey.transientPath(south, Arrays.asList(1)));
            final EnclosureHullKey hullKey = EnclosureHullKey.of(
                Arrays.asList(enclosureKey, secondEnclosureKey));
            final EnclosureKey suppressedKey = EnclosureKey.of(
                SourceNodeKey.transientPath(south, Arrays.asList(2)));
            final EnclosureHullKey suppressedHullKey = EnclosureHullKey.of(
                Collections.singletonList(suppressedKey));

            final ProjectedNode node = ProjectedNode.of(nodeKey,
                SafeNodeLabel.of("A very long safe label", "A very..."), "North Map", false);
            final String sourceIdentitySentinel = node.source().toString();
            final ProjectedEnclosure enclosure = ProjectedEnclosure.of(hullKey,
                Arrays.asList(enclosureKey, secondEnclosureKey), Arrays.asList(
                    SafeNodeLabel.of("Enclosure Safe Label", "Enclosure"),
                    SafeNodeLabel.of("Second Enclosure Label", "Second Enclosure")),
                "South Map", Optional.<EnclosureHullKey>empty(),
                Collections.<ProjectedNodeKey>emptyList(), Collections.<EnclosureHullKey>emptyList(), false,
                BoundaryTier.EMPHATIC);
            final ProjectedEnclosure suppressed = ProjectedEnclosure.of(suppressedHullKey,
                Collections.singletonList(suppressedKey), Collections.singletonList(
                    SafeNodeLabel.of("Suppressed Label", "Suppressed")), "South Map",
                Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(),
                Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.SUPPRESSED);
            final GraphProjection projection = GraphProjection.structure(1L,
                Collections.singletonList(node), Arrays.asList(enclosure, suppressed));
            final Map<ProjectedNodeKey, NodeGeometry> nodeGeometry =
                new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
            nodeGeometry.put(nodeKey, NodeGeometry.of(LayoutPoint.of(0.0, 0.0), 10.0));
            final Map<EnclosureHullKey, org.freeplane.plugin.graph.geometry.HullGeometry> hullGeometry =
                new LinkedHashMap<EnclosureHullKey, org.freeplane.plugin.graph.geometry.HullGeometry>();
            hullGeometry.put(hullKey, org.freeplane.plugin.graph.geometry.HullGeometry.of(
                Arrays.asList(LayoutPoint.of(-20.0, -20.0), LayoutPoint.of(20.0, -20.0),
                    LayoutPoint.of(20.0, 20.0), LayoutPoint.of(-20.0, 20.0)),
                LayoutPoint.of(0.0, 0.0)));
            final GraphGeometry geometry = GraphGeometry.of(nodeGeometry, hullGeometry);
            final LayoutPositions positions = LayoutPositions.of(
                new LinkedHashMap<ProjectedNodeKey, LayoutPoint>(nodeGeometryAsPoints(nodeGeometry)),
                Collections.singletonMap(hullKey, LayoutPoint.of(0.0, 0.0)));
            final LayoutFrame frame = LayoutFrame.of(1L, positions, false);
            final CanvasState state = CanvasState.of(1L, projection, frame, geometry,
                OperationalStatus.IDLE);
            return new Fixture(state, ProjectedEndpointKey.ofNode(nodeKey),
                ProjectedEndpointKey.ofEnclosure(enclosureKey),
                ProjectedEndpointKey.ofEnclosure(secondEnclosureKey),
                ProjectedEndpointKey.ofEnclosure(suppressedKey), otherNodeKey, sourceIdentitySentinel);
        }

        private static Map<ProjectedNodeKey, LayoutPoint> nodeGeometryAsPoints(
                Map<ProjectedNodeKey, NodeGeometry> geometries) {
            final Map<ProjectedNodeKey, LayoutPoint> points =
                new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
            for (Map.Entry<ProjectedNodeKey, NodeGeometry> entry : geometries.entrySet()) {
                points.put(entry.getKey(), entry.getValue().center());
            }
            return points;
        }
    }

}
