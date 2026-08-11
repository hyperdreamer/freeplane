package org.freeplane.plugin.graph.group;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class GraphGroupPersistenceShould {
    @Test
    public void loadSaveAndReloadKnownMarkerFixtureWithoutChangingUnrelatedContent() throws Exception {
        FixtureCodec codec = new FixtureCodec(true);

        MapModel map = codec.read(readResource("/maps/graph-group-known.mm"));
        NodeModel markedLeaf = map.getRootNode().getChildAt(0);

        assertThat(GraphGroupModel.isMarked(markedLeaf)).isTrue();
        assertThat(markedLeaf.isLeaf()).isTrue();

        String saved = codec.write(map);
        MapModel reloaded = codec.read(saved);

        assertKnownFixtureContent(saved, true);
        assertThat(GraphGroupModel.isMarked(reloaded.getRootNode().getChildAt(0))).isTrue();
        assertKnownFixtureContent(codec.write(reloaded), true);
    }

    @Test
    public void preservesCompleteUnsupportedMarkerAndUnrelatedContentWithPluginEnabled() throws Exception {
        FixtureCodec codec = new FixtureCodec(true);

        MapModel map = codec.read(readResource("/maps/graph-group-unknown-version.mm"));
        NodeModel node = map.getRootNode().getChildAt(0);
        String saved = codec.write(map);
        MapModel reloaded = codec.read(saved);

        assertThat(GraphGroupModel.isMarked(node)).isFalse();
        assertThat(GraphGroupModel.isMarked(reloaded.getRootNode().getChildAt(0))).isFalse();
        assertUnsupportedFixtureContent(saved);
        assertUnsupportedFixtureContent(codec.write(reloaded));
    }

    @Test
    public void preservesCompleteUnsupportedMarkerAndUnrelatedContentThroughDisabledPluginSaveThenEnabledReload()
            throws Exception {
        FixtureCodec disabledCodec = new FixtureCodec(false);
        FixtureCodec enabledCodec = new FixtureCodec(true);

        MapModel map = disabledCodec.read(readResource("/maps/graph-group-unknown-version.mm"));
        String saved = disabledCodec.write(map);
        MapModel reloaded = enabledCodec.read(saved);

        assertUnsupportedFixtureContent(saved);
        assertThat(GraphGroupModel.isMarked(reloaded.getRootNode().getChildAt(0))).isFalse();
        assertUnsupportedFixtureContent(enabledCodec.write(reloaded));
    }

    @Test
    public void removesOnlyTheKnownMarkerAndRetainsExistingFixtureContent() throws Exception {
        FixtureCodec codec = new FixtureCodec(true);
        MapModel map = codec.read(readResource("/maps/graph-group-known.mm"));
        NodeModel markedLeaf = map.getRootNode().getChildAt(0);

        markedLeaf.removeExtension(GraphGroupModel.class);
        String saved = codec.write(map);
        MapModel reloaded = codec.read(saved);

        assertKnownFixtureContent(saved, false);
        assertThat(GraphGroupModel.isMarked(reloaded.getRootNode().getChildAt(0))).isFalse();
        assertKnownFixtureContent(codec.write(reloaded), false);
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

    private static void assertKnownFixtureContent(String xml, boolean markerPresent) throws Exception {
        Element root = assertMapAndRoot(parseXml(xml));
        List<Element> rootChildren = childElements(root);
        assertThat(elementNames(rootChildren)).containsExactly("root_unknown_one", "root_unknown_two", "node", "node");
        assertElement(rootChildren.get(0), "root_unknown_one", "order", "1", "root_extra", "alpha");
        assertElement(rootChildren.get(1), "root_unknown_two", "order", "2", "root_extra", "beta");

        Element markedLeaf = rootChildren.get(2);
        assertNode(markedLeaf, "marked leaf", "ID_MARKED", "marked_attribute", "preserve-marked");
        List<Element> markedChildren = childElements(markedLeaf);
        assertThat(elementNamesExcept(markedChildren, "graph_group"))
            .containsExactly("known_unknown_one", "known_unknown_two");
        assertElement(findChild(markedChildren, "known_unknown_one"), "known_unknown_one", "order", "1", "marker_extra", "before");
        assertElement(findChild(markedChildren, "known_unknown_two"), "known_unknown_two", "order", "2", "marker_extra", "after");
        Element marker = findChild(markedChildren, "graph_group");
        if (markerPresent) {
            assertElement(marker, "graph_group", "version", "1");
            assertThat(childElements(marker)).isEmpty();
        }
        else {
            assertThat(marker).isNull();
        }

        assertPlainLeaf(rootChildren.get(3));
    }

    private static void assertUnsupportedFixtureContent(String xml) throws Exception {
        Element root = assertMapAndRoot(parseXml(xml));
        List<Element> rootChildren = childElements(root);
        assertThat(elementNames(rootChildren)).containsExactly("root_unknown_one", "root_unknown_two", "node", "node");

        Element unsupportedLeaf = rootChildren.get(2);
        assertNode(unsupportedLeaf, "unsupported leaf", "ID_UNSUPPORTED", "unsupported_attribute", "preserve-unsupported");
        List<Element> unsupportedChildren = childElements(unsupportedLeaf);
        assertThat(elementNames(unsupportedChildren)).containsExactly("unknown_before", "graph_group", "unknown_after");
        assertElement(unsupportedChildren.get(0), "unknown_before", "order", "1", "unknown_extra", "before");
        Element unsupportedMarker = unsupportedChildren.get(1);
        assertElement(unsupportedMarker, "graph_group", "version", "99", "extra", "preserve", "raw", "keep");
        List<Element> nestedElements = childElements(unsupportedMarker);
        assertThat(elementNames(nestedElements)).containsExactly("nested", "nested_second");
        assertElement(nestedElements.get(0), "nested", "flag", "yes");
        assertThat(nestedElements.get(0).getTextContent()).isEqualTo("payload");
        assertElement(nestedElements.get(1), "nested_second", "flag", "no");
        assertThat(nestedElements.get(1).getTextContent()).isEqualTo("tail");
        assertElement(unsupportedChildren.get(2), "unknown_after", "order", "2", "unknown_extra", "after");

        assertPlainLeaf(rootChildren.get(3));
    }

    private static Element assertMapAndRoot(Element map) {
        assertThat(map.getTagName()).isEqualTo("map");
        assertAttributes(map, "version", "freeplane 1.12.0", "unrelated_map_attribute", "preserve-map");
        List<Element> mapChildren = childElements(map);
        assertThat(elementNames(mapChildren)).containsExactly("map_unknown_one", "map_unknown_two", "node");
        assertElement(mapChildren.get(0), "map_unknown_one", "order", "1", "map_extra", "alpha");
        assertElement(mapChildren.get(1), "map_unknown_two", "order", "2", "map_extra", "beta");
        Element root = mapChildren.get(2);
        assertNode(root, "fixture root", "ID_ROOT", "root_attribute", "preserve-root");
        return root;
    }

    private static void assertPlainLeaf(Element plainLeaf) {
        assertNode(plainLeaf, "plain leaf", "ID_PLAIN", "plain_attribute", "preserve-plain");
        List<Element> plainChildren = childElements(plainLeaf);
        assertThat(elementNames(plainChildren)).containsExactly("plain_unknown_one", "plain_unknown_two");
        assertElement(plainChildren.get(0), "plain_unknown_one", "order", "1", "plain_extra", "one");
        assertElement(plainChildren.get(1), "plain_unknown_two", "order", "2", "plain_extra", "two");
    }

    private static void assertNode(Element node, String text, String id, String attribute, String value) {
        assertElement(node, "node", "TEXT", text, "ID", id, attribute, value);
    }

    private static void assertElement(Element element, String name, String... attributes) {
        assertThat(element).isNotNull();
        assertThat(element.getTagName()).isEqualTo(name);
        assertAttributes(element, attributes);
    }

    private static void assertAttributes(Element element, String... attributes) {
        for (int index = 0; index < attributes.length; index += 2) {
            assertThat(element.getAttribute(attributes[index])).isEqualTo(attributes[index + 1]);
        }
    }

    private static Element parseXml(String xml) throws Exception {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(new InputSource(new StringReader(xml)));
        return document.getDocumentElement();
    }

    private static List<Element> childElements(Element element) {
        List<Element> elements = new ArrayList<Element>();
        NodeList childNodes = element.getChildNodes();
        for (int index = 0; index < childNodes.getLength(); index++) {
            Node child = childNodes.item(index);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                elements.add((Element) child);
            }
        }
        return elements;
    }

    private static List<String> elementNames(List<Element> elements) {
        List<String> names = new ArrayList<String>(elements.size());
        for (Element element : elements) {
            names.add(element.getTagName());
        }
        return names;
    }

    private static List<String> elementNamesExcept(List<Element> elements, String excludedName) {
        List<String> names = new ArrayList<String>(elements.size());
        for (Element element : elements) {
            if (!excludedName.equals(element.getTagName())) {
                names.add(element.getTagName());
            }
        }
        return names;
    }

    private static Element findChild(List<Element> elements, String name) {
        for (Element element : elements) {
            if (name.equals(element.getTagName())) {
                return element;
            }
        }
        return null;
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
