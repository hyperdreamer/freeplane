package org.freeplane.plugin.graph.workspace.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.xml.namespace.QName;

import org.junit.Test;

public class WorkspaceDomainShould {
    private static final UUID WORKSPACE_UUID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final MapReferenceId MAP_ONE = MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    private static final MapReferenceId MAP_TWO = MapReferenceId.of("00000000-0000-0000-0000-000000000002");

    @Test
    public void preserveValueIdentityWithoutNormalizingPersistedNodeIds() {
        WorkspaceId lower = WorkspaceId.of("123e4567-e89b-12d3-a456-426614174000");
        WorkspaceId upper = WorkspaceId.of("123E4567-E89B-12D3-A456-426614174000");
        PersistedNodeId node = PersistedNodeId.of(" ID_1 ");

        assertThat(lower).isEqualTo(upper);
        assertThat(lower.value()).isEqualTo(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        assertThat(lower.toString()).isEqualTo("123e4567-e89b-12d3-a456-426614174000");
        assertThat(node.value()).isEqualTo(" ID_1 ");
        assertThat(node.toString()).isEqualTo(" ID_1 ");
        assertThat(NodeReference.of(MAP_ONE, node))
            .isEqualTo(NodeReference.of(MapReferenceId.of(MAP_ONE.value()), PersistedNodeId.of(" ID_1 ")));
        assertThatThrownBy(() -> WorkspaceId.of("1-1-1-1-1"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PersistedNodeId.of(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void defensivelyCopyUnknownXmlAndExposeCanonicalOrder() {
        Map<QName, String> attributes = new LinkedHashMap<QName, String>();
        attributes.put(new QName("urn:test", "z"), "z");
        attributes.put(new QName("urn:test", "a"), "a");
        List<UnknownXml.Content> content = new ArrayList<UnknownXml.Content>();
        content.add(UnknownXml.Content.text("before"));
        content.add(UnknownXml.Content.element(
            new QName("urn:test", "child"), Collections.<QName, String>emptyMap(),
            Collections.singletonList(UnknownXml.Content.text("inside"))));

        UnknownXml unknown = UnknownXml.element(
            UnknownXml.Owner.RECORD, 3, new QName("urn:test", "item"), attributes, content);
        attributes.clear();
        content.clear();

        assertThat(unknown.attributes().keySet()).containsExactly(
            new QName("urn:test", "a"), new QName("urn:test", "z"));
        assertThat(unknown.content()).hasSize(2);
        assertThat(unknown.content().get(0).text()).contains("before");
        assertThat(unknown.content().get(1).name()).contains(new QName("urn:test", "child"));
        assertThatThrownBy(() -> unknown.attributes().put(new QName("urn:test", "x"), "x"))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> unknown.content().add(UnknownXml.Content.text("after")))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void normalizeDocumentRecordsAndCollectionsDeterministically() {
        MapReference first = map(MAP_ONE, 9, "maps/one.mm");
        MapReference second = map(MAP_TWO, 2, "maps/two.mm");
        NodeReference source = NodeReference.of(MAP_ONE, PersistedNodeId.of("source"));
        NodeReference target = NodeReference.of(MAP_TWO, PersistedNodeId.of("target"));
        GraphRelationshipRecord late = GraphRelationshipRecord.of(
            RelationshipId.of("00000000-0000-0000-0000-000000000009"), 9,
            source, target, RelationshipDirection.FORWARD, Collections.<UnknownXml>emptyList());
        GraphRelationshipRecord early = GraphRelationshipRecord.of(
            RelationshipId.of("00000000-0000-0000-0000-000000000002"), 2,
            target, source, RelationshipDirection.BIDIRECTIONAL, Collections.<UnknownXml>emptyList());
        PinRecord laterMap = PinRecord.of(
            NodeReference.of(MAP_TWO, PersistedNodeId.of("a")), 2, 3,
            Collections.<UnknownXml>emptyList());
        PinRecord firstMap = PinRecord.of(
            NodeReference.of(MAP_ONE, PersistedNodeId.of("z")), 4, 5,
            Collections.<UnknownXml>emptyList());
        UnknownXml pinUnknown = UnknownXml.attribute(
            UnknownXml.Owner.PINS, new QName("urn:test", "pin"), "value");
        UnknownXml workspaceUnknown = UnknownXml.attribute(
            UnknownXml.Owner.WORKSPACE, new QName("urn:test", "workspace"), "value");

        WorkspaceDocument document = WorkspaceDocument.createVersion1(WorkspaceId.of(WORKSPACE_UUID))
            .toBuilder()
            .maps(Arrays.asList(first, second))
            .relationships(Arrays.asList(late, early))
            .pins(Arrays.asList(laterMap, firstMap))
            .unknownXml(Arrays.asList(pinUnknown, workspaceUnknown))
            .build();

        assertThat(document.maps()).extracting(MapReference::sequence).containsExactly(2L, 9L);
        assertThat(document.relationships()).extracting(GraphRelationshipRecord::sequence).containsExactly(2L, 9L);
        assertThat(document.pins()).extracting(PinRecord::node).extracting(NodeReference::mapReferenceId)
            .containsExactly(MAP_ONE, MAP_TWO);
        assertThat(document.unknownXml()).containsExactly(workspaceUnknown, pinUnknown);
        assertThat(document.toBuilder().build()).isEqualTo(document);
    }

    @Test
    public void copyBuilderInputsBeforeBuildingAnImmutableDocument() {
        List<MapReference> maps = new ArrayList<MapReference>();
        maps.add(map(MAP_ONE, 1, "one.mm"));
        List<PinRecord> pins = new ArrayList<PinRecord>();
        pins.add(PinRecord.of(
            NodeReference.of(MAP_ONE, PersistedNodeId.of("node")), 1, 2,
            Collections.<UnknownXml>emptyList()));

        WorkspaceDocument document = WorkspaceDocument.createVersion1(WorkspaceId.of(WORKSPACE_UUID))
            .toBuilder().maps(maps).pins(pins).build();
        maps.clear();
        pins.clear();

        assertThat(document.maps()).hasSize(1);
        assertThat(document.pins()).hasSize(1);
        assertThatThrownBy(() -> document.maps().clear())
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> document.pins().clear())
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void rejectInvalidReferencesSequencesAndCompatibility() {
        MapReference first = map(MAP_ONE, 1, "one.mm");
        MapReference duplicateId = map(MAP_ONE, 2, "other.mm");
        MapReference duplicateSequence = map(MAP_TWO, 1, "two.mm");
        WorkspaceDocument.Builder builder = WorkspaceDocument.createVersion1(WorkspaceId.of(WORKSPACE_UUID)).toBuilder();

        assertThatThrownBy(() -> builder.maps(Arrays.asList(first, duplicateId)).build())
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder.maps(Arrays.asList(first, duplicateSequence)).build())
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder.relationships(Collections.singletonList(GraphRelationshipRecord.of(
            RelationshipId.of(UUID.randomUUID()), 1,
            NodeReference.of(MAP_ONE, PersistedNodeId.of("a")),
            NodeReference.of(MAP_ONE, PersistedNodeId.of("b")),
            RelationshipDirection.FORWARD, Collections.<UnknownXml>emptyList()))).build())
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder.pins(Collections.singletonList(PinRecord.of(
            NodeReference.of(MAP_TWO, PersistedNodeId.of("missing")), 0, 0,
            Collections.<UnknownXml>emptyList()))).build())
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WorkspaceDocument.createVersion1(WorkspaceId.of(WORKSPACE_UUID)).toBuilder()
            .sourceFormatVersion(2).compatibility(WorkspaceCompatibility.WRITABLE_VERSION_1).build())
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WorkspaceDocument.createVersion1(WorkspaceId.of(WORKSPACE_UUID)).toBuilder()
            .sourceFormatVersion(1).compatibility(WorkspaceCompatibility.READ_ONLY_NEWER).build())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void rejectInvalidNumericUriColorAndUnknownXmlValues() {
        assertThatThrownBy(() -> PinRecord.of(
            NodeReference.of(MAP_ONE, PersistedNodeId.of("node")), Double.NaN, 0,
            Collections.<UnknownXml>emptyList())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Viewport.of(0, 0, 0, Collections.<UnknownXml>emptyList()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Viewport.of(Double.POSITIVE_INFINITY, 0, 1,
            Collections.<UnknownXml>emptyList())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> map(MAP_ONE, 1, "https://example.test/map.mm"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> map(MAP_ONE, 1, "map.mm?query=value"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MapReference.of(MAP_ONE, 1, URI.create("one.mm"), true,
            "#abcdef", Collections.<UnknownXml>emptyList())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UnknownXml.element(UnknownXml.Owner.RECORD, -1,
            new QName("urn:test", "item"), Collections.<QName, String>emptyMap(),
            Collections.<UnknownXml.Content>emptyList())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WorkspaceDocument.createVersion1(WorkspaceId.of(WORKSPACE_UUID)).toBuilder()
            .unknownXml(Arrays.asList(
                UnknownXml.attribute(UnknownXml.Owner.RECORD, new QName("urn:test", "record"), "bad")))
            .build()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void provideVersionOneDefaults() {
        WorkspaceDocument document = WorkspaceDocument.createVersion1(WorkspaceId.of(WORKSPACE_UUID));

        assertThat(document.formatVersion()).isEqualTo(1);
        assertThat(document.sourceFormatVersion()).isEqualTo(1);
        assertThat(document.compatibility()).isEqualTo(WorkspaceCompatibility.WRITABLE_VERSION_1);
        assertThat(document.maps()).isEmpty();
        assertThat(document.relationships()).isEmpty();
        assertThat(document.pins()).isEmpty();
        assertThat(document.viewport()).isEqualTo(Viewport.of(0, 0, 1,
            Collections.<UnknownXml>emptyList()));
        assertThat(document.displaySettings()).isEqualTo(DisplaySettings.defaults());
        assertThat(document.unknownXml()).isEmpty();
        assertThat(DisplaySettings.defaults().showArrowheads()).isTrue();
        assertThat(DisplaySettings.defaults().canvasTheme())
            .isEqualTo(DisplaySettings.CanvasTheme.FOLLOW_FREEPLANE);
        assertThat(DisplaySettings.defaults().rememberViewport()).isTrue();
        assertThat(DisplaySettings.defaults().dimUnrelatedNodes()).isTrue();
    }

    @Test
    public void rejectDuplicateUnknownXmlKeysAndWrongRecordOwners() {
        QName name = new QName("urn:test", "same");
        UnknownXml firstAttribute = UnknownXml.attribute(UnknownXml.Owner.RECORD, name, "one");
        UnknownXml secondAttribute = UnknownXml.attribute(UnknownXml.Owner.RECORD, name, "two");
        assertThatThrownBy(() -> MapReference.of(MAP_ONE, 1, URI.create("one.mm"), true,
            "#DF625D", Arrays.asList(firstAttribute, secondAttribute)))
            .isInstanceOf(IllegalArgumentException.class);

        UnknownXml firstElement = UnknownXml.element(UnknownXml.Owner.RECORD, 1,
            new QName("urn:test", "one"), Collections.<QName, String>emptyMap(),
            Collections.<UnknownXml.Content>emptyList());
        UnknownXml secondElement = UnknownXml.element(UnknownXml.Owner.RECORD, 1,
            new QName("urn:test", "two"), Collections.<QName, String>emptyMap(),
            Collections.<UnknownXml.Content>emptyList());
        assertThatThrownBy(() -> PinRecord.of(
            NodeReference.of(MAP_ONE, PersistedNodeId.of("node")), 0, 0,
            Arrays.asList(firstElement, secondElement))).isInstanceOf(IllegalArgumentException.class);
    }

    private static MapReference map(MapReferenceId id, long sequence, String uri) {
        return MapReference.of(id, sequence, URI.create(uri), true, "#DF625D",
            Collections.<UnknownXml>emptyList());
    }
}
