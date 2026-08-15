package org.freeplane.plugin.graph.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.HullGeometry;
import org.freeplane.plugin.graph.geometry.HullIntersection;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.freeplane.plugin.graph.workspace.model.PinRecord;
import org.freeplane.plugin.graph.workspace.model.UnknownXml;
import org.junit.Test;

public class MapTierCorrectionShould {
    private static final MapReferenceId MAP_ONE = MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    private static final MapReferenceId MAP_TWO = MapReferenceId.of("00000000-0000-0000-0000-000000000002");
    private static final ProjectedNodeKey NODE_ONE = nodeKey(MAP_ONE, "one");
    private static final ProjectedNodeKey NODE_TWO = nodeKey(MAP_TWO, "two");

    @Test
    public void translateEveryParticleInEachMovableMapByTheSamePairDelta() {
        ProjectedEnclosure first = root(MAP_ONE, "root-one", NODE_ONE, BoundaryTier.SUBTLE);
        ProjectedEnclosure second = root(MAP_TWO, "root-two", NODE_TWO, BoundaryTier.SUBTLE);
        GraphProjection projection = projection(first, second, Collections.<PinProjection>emptyList());
        LayoutPositions positions = positions(first.hullKey(), second.hullKey());
        GraphGeometry geometry = geometry(first.hullKey(), second.hullKey());

        MapTierCorrection.CorrectionResult result = new MapTierCorrection().apply(projection, positions, geometry);

        assertThat(result.conflicts()).isEmpty();
        assertThat(result.positions().nodes().get(NODE_ONE)).isEqualTo(LayoutPoint.of(-0.5, 0.0));
        assertThat(result.positions().nodes().get(NODE_TWO)).isEqualTo(LayoutPoint.of(2.5, 0.0));
        assertThat(result.positions().anchors().get(first.hullKey())).isEqualTo(LayoutPoint.of(-0.5, 0.0));
        assertThat(result.positions().anchors().get(second.hullKey())).isEqualTo(LayoutPoint.of(2.5, 0.0));
    }

    @Test
    public void moveOnlyTheOtherMapWhenTheFirstMapHasAnActivePin() {
        ProjectedEnclosure first = root(MAP_ONE, "root-one", NODE_ONE, BoundaryTier.SUBTLE);
        ProjectedEnclosure second = root(MAP_TWO, "root-two", NODE_TWO, BoundaryTier.SUBTLE);
        PinProjection pin = PinProjection.active(pinRecord(MAP_ONE, "one"), NODE_ONE);
        GraphProjection projection = projection(first, second, Collections.singletonList(pin));
        LayoutPositions positions = positions(first.hullKey(), second.hullKey());
        GraphGeometry geometry = geometry(first.hullKey(), second.hullKey());

        LayoutPositions corrected = new MapTierCorrection().apply(projection, positions, geometry).positions();

        assertThat(corrected.nodes().get(NODE_ONE)).isEqualTo(LayoutPoint.of(0.0, 0.0));
        assertThat(corrected.nodes().get(NODE_TWO)).isEqualTo(LayoutPoint.of(3.0, 0.0));
        assertThat(corrected.anchors().get(first.hullKey())).isEqualTo(LayoutPoint.of(0.0, 0.0));
        assertThat(corrected.anchors().get(second.hullKey())).isEqualTo(LayoutPoint.of(3.0, 0.0));
    }

    @Test
    public void moveOnlyTheFirstMapWhenTheSecondMapHasAnActivePin() {
        ProjectedEnclosure first = root(MAP_ONE, "root-one", NODE_ONE, BoundaryTier.SUBTLE);
        ProjectedEnclosure second = root(MAP_TWO, "root-two", NODE_TWO, BoundaryTier.SUBTLE);
        PinProjection pin = PinProjection.active(pinRecord(MAP_TWO, "two"), NODE_TWO);
        LayoutPositions corrected = new MapTierCorrection().apply(projection(first, second,
            Collections.singletonList(pin)), positions(first.hullKey(), second.hullKey()),
            geometry(first.hullKey(), second.hullKey())).positions();

        assertThat(corrected.nodes().get(NODE_ONE)).isEqualTo(LayoutPoint.of(-1.0, 0.0));
        assertThat(corrected.nodes().get(NODE_TWO)).isEqualTo(LayoutPoint.of(2.0, 0.0));
        assertThat(corrected.anchors().get(first.hullKey())).isEqualTo(LayoutPoint.of(-1.0, 0.0));
        assertThat(corrected.anchors().get(second.hullKey())).isEqualTo(LayoutPoint.of(2.0, 0.0));
    }

    @Test
    public void accumulateThreeMapDeltasFromTheOriginalHullSnapshot() {
        MapReferenceId mapThree = MapReferenceId.of("00000000-0000-0000-0000-000000000003");
        ProjectedNodeKey nodeThree = nodeKey(mapThree, "three");
        ProjectedEnclosure first = root(MAP_ONE, "root-one", NODE_ONE, BoundaryTier.SUBTLE);
        ProjectedEnclosure second = root(MAP_TWO, "root-two", NODE_TWO, BoundaryTier.SUBTLE);
        ProjectedEnclosure third = root(mapThree, "root-three", nodeThree, BoundaryTier.SUBTLE);
        GraphProjection projection = GraphProjection.projected(1L, Arrays.asList(node(NODE_ONE), node(NODE_TWO),
            node(nodeThree)), Arrays.asList(first, second, third), Collections.emptyList(),
            Collections.emptyList(), Collections.emptyList());
        Map<ProjectedNodeKey, LayoutPoint> nodes = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        nodes.put(NODE_ONE, LayoutPoint.of(0.0, 0.0));
        nodes.put(NODE_TWO, LayoutPoint.of(0.5, 0.0));
        nodes.put(nodeThree, LayoutPoint.of(1.0, 0.0));
        Map<EnclosureHullKey, LayoutPoint> anchors = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        anchors.put(first.hullKey(), LayoutPoint.of(0.0, 0.0));
        anchors.put(second.hullKey(), LayoutPoint.of(0.5, 0.0));
        anchors.put(third.hullKey(), LayoutPoint.of(1.0, 0.0));
        LayoutPositions positions = LayoutPositions.of(nodes, anchors);
        Map<EnclosureHullKey, HullGeometry> hulls = new LinkedHashMap<EnclosureHullKey, HullGeometry>();
        HullGeometry firstHull = square(0.0);
        HullGeometry secondHull = square(0.5);
        HullGeometry thirdHull = square(1.0);
        hulls.put(first.hullKey(), firstHull);
        hulls.put(second.hullKey(), secondHull);
        hulls.put(third.hullKey(), thirdHull);

        LayoutPositions corrected = new MapTierCorrection().apply(projection, positions,
            GraphGeometry.of(Collections.emptyMap(), hulls)).positions();

        LayoutPoint firstDelta = add(scale(HullIntersection.minimumSeparatingTranslation(firstHull, secondHull), -0.5),
            scale(HullIntersection.minimumSeparatingTranslation(firstHull, thirdHull), -0.5));
        LayoutPoint secondDelta = add(scale(HullIntersection.minimumSeparatingTranslation(firstHull, secondHull), 0.5),
            scale(HullIntersection.minimumSeparatingTranslation(secondHull, thirdHull), -0.5));
        LayoutPoint thirdDelta = add(scale(HullIntersection.minimumSeparatingTranslation(firstHull, thirdHull), 0.5),
            scale(HullIntersection.minimumSeparatingTranslation(secondHull, thirdHull), 0.5));
        assertThat(corrected.nodes().get(NODE_ONE)).isEqualTo(add(nodes.get(NODE_ONE), firstDelta));
        assertThat(corrected.nodes().get(NODE_TWO)).isEqualTo(add(nodes.get(NODE_TWO), secondDelta));
        assertThat(corrected.nodes().get(nodeThree)).isEqualTo(add(nodes.get(nodeThree), thirdDelta));
        assertThat(corrected.anchors().get(third.hullKey())).isEqualTo(add(anchors.get(third.hullKey()), thirdDelta));
    }

    @Test
    public void ignoreSuppressedMapRoots() {
        ProjectedEnclosure first = root(MAP_ONE, "root-one", NODE_ONE, BoundaryTier.SUBTLE);
        ProjectedEnclosure suppressed = root(MAP_TWO, "root-two", NODE_TWO, BoundaryTier.SUPPRESSED);
        LayoutPositions positions = positions(first.hullKey(), suppressed.hullKey());

        MapTierCorrection.CorrectionResult result = new MapTierCorrection().apply(
            projection(first, suppressed, Collections.<PinProjection>emptyList()), positions,
            geometry(first.hullKey(), suppressed.hullKey()));

        assertThat(result.positions()).isEqualTo(positions);
        assertThat(result.conflicts()).isEmpty();
    }

    @Test
    public void reportOneOrderedConflictWhenBothMapsAreRigid() {
        ProjectedEnclosure first = root(MAP_ONE, "root-one", NODE_ONE, BoundaryTier.SUBTLE);
        ProjectedEnclosure second = root(MAP_TWO, "root-two", NODE_TWO, BoundaryTier.SUBTLE);
        List<PinProjection> pins = Arrays.asList(PinProjection.active(pinRecord(MAP_ONE, "one"), NODE_ONE),
            PinProjection.active(pinRecord(MAP_TWO, "two"), NODE_TWO));
        GraphProjection projection = projection(first, second, pins);
        LayoutPositions positions = positions(first.hullKey(), second.hullKey());

        MapTierCorrection.CorrectionResult result = new MapTierCorrection().apply(projection, positions,
            geometry(first.hullKey(), second.hullKey()));

        assertThat(result.positions()).isEqualTo(positions);
        assertThat(result.conflicts()).hasSize(1);
        assertThat(result.conflicts().get(0).firstMap()).isEqualTo(MAP_ONE);
        assertThat(result.conflicts().get(0).secondMap()).isEqualTo(MAP_TWO);
        assertThat(result.conflicts().get(0).blockingPins()).containsExactlyElementsOf(pins);
    }

    @Test
    public void rejectInvalidConflictMapPairsAndCopyPins() {
        PinProjection pin = PinProjection.active(pinRecord(MAP_ONE, "one"), NODE_ONE);
        assertThatThrownBy(() -> new LayoutConflict(MAP_ONE, MAP_ONE, Collections.singletonList(pin)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LayoutConflict(MAP_ONE, MAP_TWO,
            Collections.singletonList(PinProjection.dormant(pin.record())))).isInstanceOf(IllegalArgumentException.class);
    }

    private static GraphProjection projection(ProjectedEnclosure first, ProjectedEnclosure second,
            List<PinProjection> pins) {
        List<ProjectedNode> nodes = Arrays.asList(node(NODE_ONE), node(NODE_TWO));
        return GraphProjection.projected(1L, nodes, Arrays.asList(first, second),
            Collections.emptyList(), Collections.emptyList(), pins);
    }

    private static LayoutPositions positions(EnclosureHullKey first, EnclosureHullKey second) {
        Map<ProjectedNodeKey, LayoutPoint> nodes = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        nodes.put(NODE_ONE, LayoutPoint.of(0.0, 0.0));
        nodes.put(NODE_TWO, LayoutPoint.of(2.0, 0.0));
        Map<EnclosureHullKey, LayoutPoint> anchors = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        anchors.put(first, LayoutPoint.of(0.0, 0.0));
        anchors.put(second, LayoutPoint.of(2.0, 0.0));
        return LayoutPositions.of(nodes, anchors);
    }

    private static GraphGeometry geometry(EnclosureHullKey first, EnclosureHullKey second) {
        Map<EnclosureHullKey, HullGeometry> hulls = new LinkedHashMap<EnclosureHullKey, HullGeometry>();
        hulls.put(first, square(0.0));
        hulls.put(second, square(1.0));
        return GraphGeometry.of(Collections.emptyMap(), hulls);
    }

    private static HullGeometry square(double offset) {
        return HullGeometry.of(Arrays.asList(LayoutPoint.of(offset - 1.0, -1.0),
            LayoutPoint.of(offset + 1.0, -1.0), LayoutPoint.of(offset + 1.0, 1.0),
            LayoutPoint.of(offset - 1.0, 1.0)), LayoutPoint.of(offset, 0.0));
    }

    private static LayoutPoint add(LayoutPoint first, LayoutPoint second) {
        return LayoutPoint.of(first.x() + second.x(), first.y() + second.y());
    }

    private static LayoutPoint scale(LayoutPoint value, double factor) {
        return LayoutPoint.of(value.x() * factor, value.y() * factor);
    }

    private static ProjectedEnclosure root(MapReferenceId map, String id, ProjectedNodeKey node,
            BoundaryTier tier) {
        EnclosureKey endpoint = EnclosureKey.of(SourceNodeKey.persisted(reference(map, id)));
        EnclosureHullKey hull = EnclosureHullKey.of(Collections.singletonList(endpoint));
        return ProjectedEnclosure.of(hull, Collections.singletonList(endpoint),
            Collections.singletonList(SafeNodeLabel.of(id, id)), "Map " + map,
            Optional.<EnclosureHullKey>empty(), Collections.singletonList(node), Collections.<EnclosureHullKey>emptyList(),
            true, tier);
    }

    private static ProjectedNode node(ProjectedNodeKey key) {
        return ProjectedNode.of(key, SafeNodeLabel.of("node", "node"), "Map", false);
    }

    private static ProjectedNodeKey nodeKey(MapReferenceId map, String id) {
        return ProjectedNodeKey.of(SourceNodeKey.persisted(reference(map, id)));
    }

    private static NodeReference reference(MapReferenceId map, String id) {
        return NodeReference.of(map, PersistedNodeId.of(id));
    }

    private static PinRecord pinRecord(MapReferenceId map, String id) {
        return PinRecord.of(reference(map, id), 0.0, 0.0, Collections.<UnknownXml>emptyList());
    }
}
