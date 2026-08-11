package org.freeplane.plugin.graph.group;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

import org.freeplane.core.extension.IExtension;
import org.freeplane.core.io.IAttributeWriter;
import org.freeplane.core.io.IElementDOMHandler;
import org.freeplane.core.io.IElementWriter;
import org.freeplane.core.io.ITreeWriter;
import org.freeplane.core.io.ReadManager;
import org.freeplane.core.io.UnknownElementWriter;
import org.freeplane.core.io.UnknownElements;
import org.freeplane.core.io.WriteManager;
import org.freeplane.core.io.xml.TreeXmlReader;
import org.freeplane.core.io.xml.TreeXmlWriter;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.n3.nanoxml.XMLElement;
import org.junit.Test;

public class GraphGroupPersistenceShould {
    @Test
    public void loadSaveAndReloadKnownMarkerFixture() throws Exception {
        FixtureCodec codec = new FixtureCodec(true);

        MapModel map = codec.read(readResource("/maps/graph-group-known.mm"));
        NodeModel markedLeaf = map.getRootNode().getChildAt(0);

        assertThat(GraphGroupModel.isMarked(markedLeaf)).isTrue();
        assertThat(markedLeaf.isLeaf()).isTrue();

        String saved = codec.write(map);
        MapModel reloaded = codec.read(saved);

        assertThat(saved).contains("<graph_group version=\"1\"/>");
        assertThat(GraphGroupModel.isMarked(reloaded.getRootNode().getChildAt(0))).isTrue();
    }

    @Test
    public void preservesUnsupportedMarkerWithPluginEnabledAndKeepsItInactive() throws Exception {
        FixtureCodec codec = new FixtureCodec(true);

        MapModel map = codec.read(readResource("/maps/graph-group-unknown-version.mm"));
        NodeModel node = map.getRootNode().getChildAt(0);
        String saved = codec.write(map);
        MapModel reloaded = codec.read(saved);

        assertThat(GraphGroupModel.isMarked(node)).isFalse();
        assertThat(GraphGroupModel.isMarked(reloaded.getRootNode().getChildAt(0))).isFalse();
        assertThat(saved).contains("<graph_group version=\"99\" extra=\"preserve\">");
        assertThat(saved).contains("<nested flag=\"yes\">payload</nested>");
        assertThat(saved.indexOf("<unknown_before"))
            .isLessThan(saved.indexOf("<graph_group version=\"99\""));
        assertThat(saved.indexOf("<graph_group version=\"99\""))
            .isLessThan(saved.indexOf("<unknown_after"));
    }

    @Test
    public void preservesUnsupportedMarkerThroughDisabledPluginSaveThenEnabledReload() throws Exception {
        FixtureCodec disabledCodec = new FixtureCodec(false);
        FixtureCodec enabledCodec = new FixtureCodec(true);

        MapModel map = disabledCodec.read(readResource("/maps/graph-group-unknown-version.mm"));
        String saved = disabledCodec.write(map);
        MapModel reloaded = enabledCodec.read(saved);

        assertThat(saved).contains("<graph_group version=\"99\" extra=\"preserve\">");
        assertThat(GraphGroupModel.isMarked(reloaded.getRootNode().getChildAt(0))).isFalse();
    }

    @Test
    public void writesKnownMarkerForLeafWithoutCreatingAnIdentityOrStamp() throws Exception {
        FixtureCodec codec = new FixtureCodec(true);
        MapModel map = mapWithLeaf();
        NodeModel leaf = map.getRootNode().getChildAt(0);

        leaf.addExtension(new GraphGroupModel());
        String saved = codec.write(map);

        assertThat(leaf.getID()).isNull();
        assertThat(saved).contains("<graph_group version=\"1\"/>");
        assertThat(saved).doesNotContain("graph_group_id").doesNotContain("graph_group_stamp");
    }

    @Test
    public void persistsMarkerAcrossAnIndependentXmlCopy() throws Exception {
        FixtureCodec codec = new FixtureCodec(true);
        MapModel source = mapWithLeaf();
        NodeModel sourceLeaf = source.getRootNode().getChildAt(0);
        sourceLeaf.addExtension(new GraphGroupModel());

        MapModel copy = codec.read(codec.write(source));
        NodeModel copiedLeaf = copy.getRootNode().getChildAt(0);

        assertThat(GraphGroupModel.isMarked(copiedLeaf)).isTrue();
        assertThat(copiedLeaf.getSharedData()).isNotSameAs(sourceLeaf.getSharedData());
    }

    @Test
    public void unregistersBothVersionDispatchHandlersAndWriter() {
        ReadManager reader = new ReadManager();
        WriteManager writer = new WriteManager();
        GraphGroupBuilder builder = new GraphGroupBuilder();

        builder.registerBy(reader, writer);

        assertThat(reader.getElementHandlers().list("graph_group")).hasSize(2);
        assertThat(writer.getExtensionElementWriters().list(GraphGroupModel.class)).containsExactly(builder);

        builder.unregisterFrom(reader, writer);

        assertThat(reader.getElementHandlers().list("graph_group")).isEmpty();
        assertThat(writer.getExtensionElementWriters().list(GraphGroupModel.class)).isEmpty();
    }

    private static MapModel mapWithLeaf() {
        MapModel map = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel root = new NodeModel("root", map);
        NodeModel leaf = new NodeModel("leaf", map);
        map.setRoot(root);
        root.insert(leaf);
        return map;
    }

    private String readResource(String resource) throws IOException {
        InputStream input = getClass().getResourceAsStream(resource);
        assertThat(input).as("resource %s", resource).isNotNull();
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            for (int read = input.read(buffer); read != -1; read = input.read(buffer)) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
        finally {
            input.close();
        }
    }

    private static final class FixtureCodec {
        private final GraphGroupBuilder graphGroupBuilder;
        private final MapBuilder mapBuilder;
        private final NodeBuilder nodeBuilder;
        private final ReadManager readManager;
        private final WriteManager writeManager;

        private FixtureCodec(boolean graphGroupEnabled) {
            readManager = new ReadManager();
            writeManager = new WriteManager();
            mapBuilder = new MapBuilder();
            nodeBuilder = new NodeBuilder(mapBuilder);
            readManager.addElementHandler("map", mapBuilder);
            readManager.addElementHandler("node", nodeBuilder);
            writeManager.addAttributeWriter("map", new ExtensionAttributeWriter());
            writeManager.addAttributeWriter("node", new ExtensionAttributeWriter());
            writeManager.addElementWriter("map", new MapWriter());
            writeManager.addElementWriter("node", new NodeWriter());
            UnknownElementWriter unknownElementWriter = new UnknownElementWriter();
            writeManager.addExtensionAttributeWriter(UnknownElements.class, unknownElementWriter);
            writeManager.addExtensionElementWriter(UnknownElements.class, unknownElementWriter);
            if (graphGroupEnabled) {
                graphGroupBuilder = new GraphGroupBuilder();
                graphGroupBuilder.registerBy(readManager, writeManager);
            }
            else {
                graphGroupBuilder = null;
            }
        }

        private MapModel read(String xml) throws Exception {
            new TreeXmlReader(readManager).load(null, new StringReader(xml));
            return mapBuilder.map;
        }

        private String write(MapModel map) throws IOException {
            StringWriter output = new StringWriter();
            TreeXmlWriter writer = new TreeXmlWriter(writeManager, output, false);
            writer.addElement(map, "map");
            writer.flush();
            return output.toString();
        }

        private static final class ExtensionAttributeWriter implements IAttributeWriter {
            @Override
            public void writeAttributes(ITreeWriter writer, Object element, String tag) {
                if (element instanceof MapModel) {
                    writer.addExtensionAttributes(element, ((MapModel) element).getExtensions().values());
                }
                else if (element instanceof NodeModel) {
                    writer.addExtensionAttributes(element, ((NodeModel) element).getSharedExtensions().values());
                }
            }
        }

        private static final class MapWriter implements IElementWriter {
            @Override
            public void writeContent(ITreeWriter writer, Object element, String tag) throws IOException {
                MapModel map = (MapModel) element;
                writer.addExtensionNodes(map, map.getExtensions().values());
                writer.addElement(map.getRootNode(), "node");
            }
        }

        private static final class NodeWriter implements IElementWriter {
            @Override
            public void writeContent(ITreeWriter writer, Object element, String tag) throws IOException {
                NodeModel node = (NodeModel) element;
                Collection<IExtension> extensions = node.getSharedExtensions().values();
                writer.addExtensionNodes(node, extensions);
                for (NodeModel child : node.getChildren()) {
                    writer.addElement(child, "node");
                }
            }
        }

        private static final class MapBuilder implements IElementDOMHandler {
            private MapModel map;

            @Override
            public Object createElement(Object parent, String tag, XMLElement attributes) {
                map = new MapModel((source, targetMap, withChildren) -> null, null, null);
                return map;
            }

            @Override
            public void endElement(Object parent, String tag, Object element, XMLElement dom) {
                if (dom.getAttributeCount() != 0 || dom.hasChildren()) {
                    ((MapModel) element).addExtension(new UnknownElements(dom));
                }
            }
        }

        private static final class NodeBuilder implements IElementDOMHandler {
            private final MapBuilder mapBuilder;

            private NodeBuilder(MapBuilder mapBuilder) {
                this.mapBuilder = mapBuilder;
            }

            @Override
            public Object createElement(Object parent, String tag, XMLElement attributes) {
                return new NodeModel(mapBuilder.map);
            }

            @Override
            public void endElement(Object parent, String tag, Object element, XMLElement dom) {
                NodeModel node = (NodeModel) element;
                if (dom.getAttributeCount() != 0 || dom.hasChildren()) {
                    node.addExtension(new UnknownElements(dom));
                }
                if (parent instanceof MapModel) {
                    ((MapModel) parent).setRoot(node);
                }
                else if (parent instanceof NodeModel) {
                    ((NodeModel) parent).insert(node);
                }
            }
        }
    }
}
