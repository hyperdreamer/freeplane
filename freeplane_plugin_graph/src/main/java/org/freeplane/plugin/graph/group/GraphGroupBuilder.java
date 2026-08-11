package org.freeplane.plugin.graph.group;

import java.io.IOException;

import org.freeplane.core.extension.IExtension;
import org.freeplane.core.io.IElementDOMHandler;
import org.freeplane.core.io.IElementHandler;
import org.freeplane.core.io.IExtensionElementWriter;
import org.freeplane.core.io.ITreeWriter;
import org.freeplane.core.io.ReadManager;
import org.freeplane.core.io.WriteManager;
import org.freeplane.features.map.NodeModel;
import org.freeplane.n3.nanoxml.XMLElement;

final class GraphGroupBuilder implements IElementDOMHandler, IExtensionElementWriter {
    private static final String XML_GRAPH_GROUP = "graph_group";
    private static final String XML_VERSION = "version";

    private final IElementHandler unsupportedVersionHandler = new IElementHandler() {
        @Override
        public Object createElement(final Object parent, final String tag, final XMLElement attributes) {
            return null;
        }
    };

    @Override
    public Object createElement(final Object parent, final String tag, final XMLElement attributes) {
        if (!(parent instanceof NodeModel) || attributes == null) {
            return null;
        }
        final String version = attributes.getAttribute(XML_VERSION, null);
        return Integer.toString(GraphGroupModel.FORMAT_VERSION).equals(version) ? new GraphGroupModel() : null;
    }

    @Override
    public void endElement(final Object parent, final String tag, final Object element, final XMLElement dom) {
        if (parent instanceof NodeModel && element instanceof GraphGroupModel) {
            ((NodeModel) parent).putExtension((GraphGroupModel) element);
        }
    }

    void registerBy(final ReadManager reader, final WriteManager writer) {
        // A second declining handler defers TreeXmlReader dispatch until version is available.
        reader.addElementHandler(XML_GRAPH_GROUP, this);
        reader.addElementHandler(XML_GRAPH_GROUP, unsupportedVersionHandler);
        writer.addExtensionElementWriter(GraphGroupModel.class, this);
    }

    void unregisterFrom(final ReadManager reader, final WriteManager writer) {
        reader.removeElementHandler(XML_GRAPH_GROUP, this);
        reader.removeElementHandler(XML_GRAPH_GROUP, unsupportedVersionHandler);
        writer.removeExtensionElementWriter(GraphGroupModel.class, this);
    }

    @Override
    public void writeContent(final ITreeWriter writer, final Object element, final IExtension extension)
            throws IOException {
        XMLElement graphGroup = new XMLElement(XML_GRAPH_GROUP);
        graphGroup.setAttribute(XML_VERSION, Integer.toString(GraphGroupModel.FORMAT_VERSION));
        writer.addElement(extension, graphGroup);
    }
}
