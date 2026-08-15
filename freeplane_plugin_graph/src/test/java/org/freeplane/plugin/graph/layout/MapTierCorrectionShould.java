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
    private static final MapReferenceId MAP_THREE = MapReferenceId.of("00000000-0000-0000-0000-000000000003");
    private static final ProjectedNodeKey NODE_ONE = nodeKey(MAP_ONE, "one");
    private static final ProjectedNodeKey NODE_TWO = nodeKey(MAP_TWO, "two");
    private static final ProjectedNodeKey NODE_THREE = nodeKey(MAP_THREE, "three");

    @Test
    public void translateEveryParticleInEachMovableMapByTheSamePairDelta() {
        ProjectedNodeKey firstOther = nodeKey(MAP_ONE, "one-other");
        ProjectedNodeKey secondOther = nodeKey(MAP_TWO, "two-other");
        ProjectedEnclosure first = root(MAP_ONE, "root-one", NODE_ONE, BoundaryTier.SUBTLE,
            Collections.<EnclosureHullKey>singletonList(childHull(MAP_ONE, "child-one")));
        ProjectedEnclosure firstChild = child(MAP_ONE, "child-one", first.hullKey(), firstOther);
        ProjectedEnclosure second = root(MAP_TWO, "root-two", NODE_TWO, BoundaryTier.SUBTLE,
            Collections.<EnclosureHullKey>singletonList(childHull(MAP_TWO, "child-two")));
        ProjectedEnclosure secondChild = child(MAP_TWO, "child-two", second.hullKey(), secondOther);
        GraphProjection projection = GraphProjection.projected(1L,
            Arrays.asList(node(NODE_ONE), node(firstOther), node(NODE_TWO), node(secondOther)),
            Arrays.asList(first, firstChild, second, secondChild), Collections.emptyList(),
            Collections.emptyList(), Collections.<PinProjection>emptyList());
        Map<ProjectedNodeKey, LayoutPoint> nodes = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        nodes.put(NODE_ONE, LayoutPoint.of(0.0, 0.0));
        nodes.put(firstOther, LayoutPoint.of(0.0, 3.0));
        nodes.put(NODE_TWO, LayoutPoint.of(2.0, 0.0));
        nodes.put(secondOther, LayoutPoint.of(2.0, 3.0));
        Map<EnclosureHullKey, LayoutPoint> anchors = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        anchors.put(first.hullKey(), LayoutPoint.of(0.0, 0.0));
        anchors.put(firstChild.hullKey(), LayoutPoint.of(0.0, 3.0));
        anchors.put(second.hullKey(), LayoutPoint.of(2.0, 0.0));
        anchors.put(secondChild.hullKey(), LayoutPoint.of(2.0, 3.0));
        LayoutPositions positions = LayoutPositions.of(nodes, anchors);
        Map<EnclosureHullKey, HullGeometry> hulls = new LinkedHashMap<EnclosureHullKey, HullGeometry>();
        hulls.put(first.hullKey(), square(0.0));
        hulls.put(firstChild.hullKey(), square(0.0));
        hulls.put(second.hullKey(), square(1.0));
        hulls.put(secondChild.hullKey(), square(1.0));

        MapTierCorrection.CorrectionResult result = new MapTierCorrection().apply(projection, positions,
            GraphGeometry.of(Collections.emptyMap(), hulls));

        assertThat(result.conflicts()).isEmpty();
        assertThat(result.positions().nodes().get(NODE_ONE)).isEqualTo(LayoutPoint.of(-0.5, 0.0));
        assertThat(result.positions().nodes().get(firstOther)).isEqualTo(LayoutPoint.of(-0.5, 3.0));
        assertThat(result.positions().nodes().get(NODE_TWO)).isEqualTo(LayoutPoint.of(2.5, 0.0));
        assertThat(result.positions().nodes().get(secondOther)).isEqualTo(LayoutPoint.of(2.5, 3.0));
        assertThat(result.positions().anchors().get(first.hullKey())).isEqualTo(LayoutPoint.of(-0.5, 0.0));
        assertThat(result.positions().anchors().get(firstChild.hullKey())).isEqualTo(LayoutPoint.of(-0.5, 3.0));
        assertThat(result.positions().anchors().get(second.hullKey())).isEqualTo(LayoutPoint.of(2.5, 0.0));
        assertThat(result.positions().anchors().get(secondChild.hullKey())).isEqualTo(LayoutPoint.of(2.5, 3.0));
        assertThatThrownBy(() -> result.positions().nodes().put(NODE_ONE, LayoutPoint.of(9.0, 9.0)))
            .isInstanceOf(UnsupportedOperationException.class);
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
        ProjectedNodeKey nodeThree = NODE_THREE;
        ProjectedEnclosure first = root(MAP_ONE, "root-one", NODE_ONE, BoundaryTier.SUBTLE);
        ProjectedEnclosure second = root(MAP_TWO, "root-two", NODE_TWO, BoundaryTier.SUBTLE);
        ProjectedEnclosure third = root(MAP_THREE, "root-three", NODE_THREE, BoundaryTier.SUBTLE);
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
    public void reportOrderedConflictsForEveryRigidMapPair() {
        ProjectedEnclosure first = root(MAP_ONE, "root-one", NODE_ONE, BoundaryTier.SUBTLE);
        ProjectedEnclosure second = root(MAP_TWO, "root-two", NODE_TWO, BoundaryTier.SUBTLE);
        ProjectedEnclosure third = root(MAP_THREE, "root-three", NODE_THREE, BoundaryTier.SUBTLE);
        List<PinProjection> pins = Arrays.asList(PinProjection.active(pinRecord(MAP_ONE, "one"), NODE_ONE),
            PinProjection.active(pinRecord(MAP_TWO, "two"), NODE_TWO),
            PinProjection.active(pinRecord(MAP_THREE, "three"), NODE_THREE));
        GraphProjection projection = GraphProjection.projected(1L,
            Arrays.asList(node(NODE_ONE), node(NODE_TWO), node(NODE_THREE)),
            Arrays.asList(first, second, third), Collections.emptyList(), Collections.emptyList(), pins);
        LayoutPositions positions = positions(first.hullKey(), second.hullKey(), third.hullKey());

        MapTierCorrection.CorrectionResult result = new MapTierCorrection().apply(projection, positions,
            geometry(first.hullKey(), second.hullKey(), third.hullKey()));

        assertThat(result.positions()).isEqualTo(positions);
        assertThat(result.conflicts()).hasSize(3);
        assertConflict(result.conflicts().get(0), MAP_ONE, MAP_TWO, pins.subList(0, 2));
        assertConflict(result.conflicts().get(1), MAP_ONE, MAP_THREE,
            Arrays.asList(pins.get(0), pins.get(2)));
        assertConflict(result.conflicts().get(2), MAP_TWO, MAP_THREE, pins.subList(1, 3));
        assertThatThrownBy(() -> result.conflicts().clear()).isInstanceOf(UnsupportedOperationException.class);
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

    private static void assertConflict(LayoutConflict conflict, MapReferenceId first, MapReferenceId second,
            List<PinProjection> pins) {
        assertThat(conflict.firstMap()).isEqualTo(first);
        assertThat(conflict.secondMap()).isEqualTo(second);
        assertThat(conflict.blockingPins()).containsExactlyElementsOf(pins);
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
    private static LayoutPositions positions(EnclosureHullKey first, EnclosureHullKey second,
            EnclosureHullKey third) {
        Map<ProjectedNodeKey, LayoutPoint> nodes = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        nodes.put(NODE_ONE, LayoutPoint.of(0.0, 0.0));
        nodes.put(NODE_TWO, LayoutPoint.of(0.5, 0.0));
        nodes.put(NODE_THREE, LayoutPoint.of(1.0, 0.0));
        Map<EnclosureHullKey, LayoutPoint> anchors = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        anchors.put(first, LayoutPoint.of(0.0, 0.0));
        anchors.put(second, LayoutPoint.of(0.5, 0.0));
        anchors.put(third, LayoutPoint.of(1.0, 0.0));
        return LayoutPositions.of(nodes, anchors);
    }


    private static GraphGeometry geometry(EnclosureHullKey first, EnclosureHullKey second,
            EnclosureHullKey third) {
        Map<EnclosureHullKey, HullGeometry> hulls = new LinkedHashMap<EnclosureHullKey, HullGeometry>();
        hulls.put(first, square(0.0));
        hulls.put(second, square(0.5));
        hulls.put(third, square(1.0));
        return GraphGeometry.of(Collections.emptyMap(), hulls);
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
        return root(map, id, node, tier, Collections.<EnclosureHullKey>emptyList());
    }

    private static ProjectedEnclosure root(MapReferenceId map, String id, ProjectedNodeKey node,
            BoundaryTier tier, List<EnclosureHullKey> directEnclosures) {
        EnclosureKey endpoint = EnclosureKey.of(SourceNodeKey.persisted(reference(map, id)));
        EnclosureHullKey hull = EnclosureHullKey.of(Collections.singletonList(endpoint));
        return ProjectedEnclosure.of(hull, Collections.singletonList(endpoint),
            Collections.singletonList(SafeNodeLabel.of(id, id)), "Map " + map,
            Optional.<EnclosureHullKey>empty(), Collections.singletonList(node), directEnclosures,
            true, tier);
    }

    private static ProjectedEnclosure child(MapReferenceId map, String id, EnclosureHullKey parent,
            ProjectedNodeKey node) {
        EnclosureKey endpoint = EnclosureKey.of(SourceNodeKey.persisted(reference(map, id)));
        EnclosureHullKey hull = EnclosureHullKey.of(Collections.singletonList(endpoint));
        return ProjectedEnclosure.of(hull, Collections.singletonList(endpoint),
            Collections.singletonList(SafeNodeLabel.of(id, id)), "Map " + map,
            Optional.of(parent), Collections.singletonList(node), Collections.<EnclosureHullKey>emptyList(),
            false, BoundaryTier.SUBTLE);
    }

    private static EnclosureHullKey childHull(MapReferenceId map, String id) {
        return EnclosureHullKey.of(Collections.singletonList(
            EnclosureKey.of(SourceNodeKey.persisted(reference(map, id)))));
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
