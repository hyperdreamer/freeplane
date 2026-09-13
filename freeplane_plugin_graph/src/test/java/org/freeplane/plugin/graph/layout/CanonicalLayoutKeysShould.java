package org.freeplane.plugin.graph.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Collections;

import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.junit.Test;

public class CanonicalLayoutKeysShould {
    private static final MapReferenceId MAP = MapReferenceId.of("8055d8c8-d71e-40f3-a8ed-5bf2502f2cad");
    private static final MapReferenceId OTHER_MAP = MapReferenceId.of("76650fda-9b84-4f8b-858f-27b18d08c535");

    @Test
    public void escapeReservedCharactersInFixedOrder() {
        assertThat(CanonicalLayoutKeys.escape("a%b|c:d,e")).isEqualTo("a%25b%7Cc%3Ad%2Ce");
        assertThat(CanonicalLayoutKeys.escape("plain")).isEqualTo("plain");
    }

    @Test
    public void encodePersistedAndTransientEndpointsDistinctly() {
        SourceNodeKey persisted = source(MAP, "ID_1");
        SourceNodeKey transientKey = SourceNodeKey.transientPath(MAP,
            Arrays.asList(Integer.valueOf(1), Integer.valueOf(2)));

        assertThat(CanonicalLayoutKeys.endpoint(persisted)).isEqualTo("m:" + MAP.value() + "|p:ID_1");
        assertThat(CanonicalLayoutKeys.endpoint(transientKey)).isEqualTo("m:" + MAP.value() + "|t:1.2");
        assertThat(CanonicalLayoutKeys.endpoint(SourceNodeKey.transientPath(MAP,
            Collections.<Integer>emptyList()))).isEqualTo("m:" + MAP.value() + "|t:");
        assertThat(CanonicalLayoutKeys.endpoint(persisted))
            .isNotEqualTo(CanonicalLayoutKeys.endpoint(source(MAP, "p:ID_1")));
        assertThat(CanonicalLayoutKeys.endpoint(source(MAP, "x|t:1.2")))
            .isNotEqualTo(CanonicalLayoutKeys.endpoint(transientKey));
    }

    @Test
    public void buildHullKeysIndependentOfEndpointOrder() {
        EnclosureHullKey forward = EnclosureHullKey.of(Arrays.asList(
            EnclosureKey.of(source(MAP, "a")), EnclosureKey.of(source(MAP, "b"))));
        EnclosureHullKey reversed = EnclosureHullKey.of(Arrays.asList(
            EnclosureKey.of(source(MAP, "b")), EnclosureKey.of(source(MAP, "a"))));

        assertThat(CanonicalLayoutKeys.hull(forward)).isEqualTo(CanonicalLayoutKeys.hull(reversed));
        assertThat(CanonicalLayoutKeys.hull(forward)).isEqualTo(
            "m:" + MAP.value() + "|p:a,m:" + MAP.value() + "|p:b");
    }

    @Test
    public void orderPairsCanonicallyAndSymmetrically() {
        EnclosureHullKey first = hull("a");
        EnclosureHullKey second = hull("b");

        assertThat(CanonicalLayoutKeys.pair(first, second))
            .isEqualTo(CanonicalLayoutKeys.pair(second, first));
        assertThat(CanonicalLayoutKeys.pair(first, second))
            .isEqualTo(CanonicalLayoutKeys.hull(first) + "|" + CanonicalLayoutKeys.hull(second));
    }

    @Test
    public void prefixNodeAndAnchorFields() {
        ProjectedNodeKey node = ProjectedNodeKey.of(source(MAP, "node"));

        assertThat(CanonicalLayoutKeys.nodeField(node)).isEqualTo("n:m:" + MAP.value() + "|p:node");
        assertThat(CanonicalLayoutKeys.anchorField(hull("hull"))).isEqualTo("a:m:" + MAP.value() + "|p:hull");
    }

    @Test
    public void recoverTheMapFromADisplacementField() {
        ProjectedNodeKey node = ProjectedNodeKey.of(source(MAP, "node"));

        assertThat(CanonicalLayoutKeys.mapOfField(CanonicalLayoutKeys.nodeField(node))).isEqualTo(MAP);
        assertThat(CanonicalLayoutKeys.mapOfField(CanonicalLayoutKeys.anchorField(hull("hull")))).isEqualTo(MAP);
        assertThat(CanonicalLayoutKeys.mapOfField(CanonicalLayoutKeys.nodeField(
            ProjectedNodeKey.of(source(OTHER_MAP, "node"))))).isEqualTo(OTHER_MAP);
        assertThatThrownBy(() -> CanonicalLayoutKeys.mapOfField("x:y")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CanonicalLayoutKeys.mapOfField("n:garbage"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static EnclosureHullKey hull(String id) {
        return EnclosureHullKey.of(Collections.singletonList(EnclosureKey.of(source(MAP, id))));
    }

    private static SourceNodeKey source(MapReferenceId map, String id) {
        return SourceNodeKey.persisted(NodeReference.of(map, PersistedNodeId.of(id)));
    }
}
