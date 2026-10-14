package org.freeplane.plugin.graph.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Collections;

import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.junit.Test;

public class CanonicalKeysShould {
    private static final MapReferenceId MAP = MapReferenceId.of("8055d8c8-d71e-40f3-a8ed-5bf2502f2cad");
    private static final MapReferenceId OTHER_MAP = MapReferenceId.of("76650fda-9b84-4f8b-858f-27b18d08c535");

    @Test
    public void escapeReservedCharactersInFixedOrder() {
        assertThat(CanonicalKeys.escape("a%b|c:d,e")).isEqualTo("a%25b%7Cc%3Ad%2Ce");
        assertThat(CanonicalKeys.escape("plain")).isEqualTo("plain");
    }

    @Test
    public void encodePersistedAndTransientEndpointsDistinctly() {
        SourceNodeKey persisted = source(MAP, "ID_1");
        SourceNodeKey transientKey = SourceNodeKey.transientPath(MAP,
            Arrays.asList(Integer.valueOf(1), Integer.valueOf(2)));

        assertThat(CanonicalKeys.endpoint(persisted)).isEqualTo("m:" + MAP.value() + "|p:ID_1");
        assertThat(CanonicalKeys.endpoint(transientKey)).isEqualTo("m:" + MAP.value() + "|t:1.2");
        assertThat(CanonicalKeys.endpoint(SourceNodeKey.transientPath(MAP,
            Collections.<Integer>emptyList()))).isEqualTo("m:" + MAP.value() + "|t:");
        assertThat(CanonicalKeys.endpoint(persisted))
            .isNotEqualTo(CanonicalKeys.endpoint(source(MAP, "p:ID_1")));
        assertThat(CanonicalKeys.endpoint(source(MAP, "x|t:1.2")))
            .isNotEqualTo(CanonicalKeys.endpoint(transientKey));
    }

    @Test
    public void buildHullKeysIndependentOfEndpointOrder() {
        EnclosureHullKey forward = EnclosureHullKey.of(Arrays.asList(
            EnclosureKey.of(source(MAP, "a")), EnclosureKey.of(source(MAP, "b"))));
        EnclosureHullKey reversed = EnclosureHullKey.of(Arrays.asList(
            EnclosureKey.of(source(MAP, "b")), EnclosureKey.of(source(MAP, "a"))));

        assertThat(CanonicalKeys.hull(forward)).isEqualTo(CanonicalKeys.hull(reversed));
        assertThat(CanonicalKeys.hull(forward)).isEqualTo(
            "m:" + MAP.value() + "|p:a,m:" + MAP.value() + "|p:b");
    }

    @Test
    public void orderPairsCanonicallyAndSymmetrically() {
        EnclosureHullKey first = hull("a");
        EnclosureHullKey second = hull("b");

        assertThat(CanonicalKeys.pair(first, second)).isEqualTo(CanonicalKeys.pair(second, first));
        assertThat(CanonicalKeys.pair(first, second))
            .isEqualTo(CanonicalKeys.hull(first) + "|" + CanonicalKeys.hull(second));
        assertThat(CanonicalKeys.hullPair(first, second)).isEqualTo(CanonicalKeys.pair(first, second));
    }

    @Test
    public void prefixNodeAndAnchorFields() {
        ProjectedNodeKey node = ProjectedNodeKey.of(source(MAP, "node"));

        assertThat(CanonicalKeys.nodeField(node)).isEqualTo("n:m:" + MAP.value() + "|p:node");
        assertThat(CanonicalKeys.node(node)).isEqualTo(CanonicalKeys.nodeField(node));
        assertThat(CanonicalKeys.anchorField(hull("hull"))).isEqualTo("a:m:" + MAP.value() + "|p:hull");
    }

    @Test
    public void recoverTheMapFromADisplacementField() {
        ProjectedNodeKey node = ProjectedNodeKey.of(source(MAP, "node"));

        assertThat(CanonicalKeys.mapOfField(CanonicalKeys.nodeField(node))).isEqualTo(MAP);
        assertThat(CanonicalKeys.mapOfField(CanonicalKeys.anchorField(hull("hull")))).isEqualTo(MAP);
        assertThat(CanonicalKeys.mapOfField(CanonicalKeys.nodeField(
            ProjectedNodeKey.of(source(OTHER_MAP, "node"))))).isEqualTo(OTHER_MAP);
        assertThatThrownBy(() -> CanonicalKeys.mapOfField("x:y"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CanonicalKeys.mapOfField("n:garbage"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void encodeEndpointOfEnclosureWithTheEnclosurePrefix() {
        EnclosureKey enclosure = EnclosureKey.of(source(MAP, "e"));

        assertThat(CanonicalKeys.endpoint(ProjectedEndpointKey.ofEnclosure(enclosure)))
            .isEqualTo("e:m:" + MAP.value() + "|p:e");
    }

    @Test
    public void orderNodePairsCanonicallyAndSymmetrically() {
        ProjectedNodeKey first = ProjectedNodeKey.of(source(MAP, "a"));
        ProjectedNodeKey second = ProjectedNodeKey.of(source(MAP, "b"));

        assertThat(CanonicalKeys.nodePair(first, second)).isEqualTo(CanonicalKeys.nodePair(second, first));
        assertThat(CanonicalKeys.nodePair(first, second))
            .isEqualTo(CanonicalKeys.node(first) + "|" + CanonicalKeys.node(second));
    }

    @Test
    public void encodeEdgesAndEdgePairsSymmetrically() {
        ProjectedEdgeKey edge = ProjectedEdgeKey.of(
            ProjectedEndpointKey.ofNode(ProjectedNodeKey.of(source(MAP, "a"))),
            ProjectedEndpointKey.ofNode(ProjectedNodeKey.of(source(MAP, "b"))));
        ProjectedEdgeKey other = ProjectedEdgeKey.of(
            ProjectedEndpointKey.ofNode(ProjectedNodeKey.of(source(MAP, "b"))),
            ProjectedEndpointKey.ofNode(ProjectedNodeKey.of(source(MAP, "c"))));

        assertThat(CanonicalKeys.edge(edge)).isEqualTo(CanonicalKeys.node(
            ProjectedNodeKey.of(source(MAP, "a"))) + "~" + CanonicalKeys.node(
                ProjectedNodeKey.of(source(MAP, "b"))));
        assertThat(CanonicalKeys.edgePair(edge, other)).isEqualTo(CanonicalKeys.edgePair(other, edge));
    }

    @Test
    public void distinguishTwoPassedOverNodesOnOneEdge() {
        ProjectedEdgeKey edge = ProjectedEdgeKey.of(
            ProjectedEndpointKey.ofNode(ProjectedNodeKey.of(source(MAP, "a"))),
            ProjectedEndpointKey.ofNode(ProjectedNodeKey.of(source(MAP, "b"))));
        ProjectedNodeKey first = ProjectedNodeKey.of(source(MAP, "n1"));
        ProjectedNodeKey second = ProjectedNodeKey.of(source(MAP, "n2"));

        assertThat(CanonicalKeys.edgeNodePair(edge, first))
            .isEqualTo(CanonicalKeys.edge(edge) + "|" + CanonicalKeys.node(first));
        assertThat(CanonicalKeys.edgeNodePair(edge, first))
            .isNotEqualTo(CanonicalKeys.edgeNodePair(edge, second));
    }

    @Test
    public void keepTildeContainingIdsInjectiveWithoutEscapingTilde() {
        assertThat(CanonicalKeys.endpoint(source(MAP, "a~b"))).isEqualTo("m:" + MAP.value() + "|p:a~b");
        ProjectedEdgeKey first = ProjectedEdgeKey.of(
            ProjectedEndpointKey.ofNode(ProjectedNodeKey.of(source(MAP, "a~b"))),
            ProjectedEndpointKey.ofNode(ProjectedNodeKey.of(source(MAP, "c"))));
        ProjectedEdgeKey second = ProjectedEdgeKey.of(
            ProjectedEndpointKey.ofNode(ProjectedNodeKey.of(source(MAP, "a"))),
            ProjectedEndpointKey.ofNode(ProjectedNodeKey.of(source(MAP, "b~c"))));

        assertThat(CanonicalKeys.edge(first)).isNotEqualTo(CanonicalKeys.edge(second));
    }

    private static EnclosureHullKey hull(String id) {
        return EnclosureHullKey.of(Collections.singletonList(EnclosureKey.of(source(MAP, id))));
    }

    private static SourceNodeKey source(MapReferenceId map, String id) {
        return SourceNodeKey.persisted(NodeReference.of(map, PersistedNodeId.of(id)));
    }
}
