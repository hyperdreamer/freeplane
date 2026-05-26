package org.freeplane.plugin.script.doclet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

final class FreeplaneMindMapWriter {
    private static final String MAP_VERSION = "freeplane 1.9.8";

    public void write(ApiMapNode rootNode, File outputFile) throws IOException {
        if (outputFile.getParentFile() != null) {
            outputFile.getParentFile().mkdirs();
        }
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8)) {
            XMLStreamWriter xmlWriter = XMLOutputFactory.newFactory().createXMLStreamWriter(writer);
            writeDocument(rootNode, xmlWriter);
            xmlWriter.flush();
            xmlWriter.close();
        }
        catch (XMLStreamException error) {
            throw new IOException("Failed to write Freeplane API map XML.", error);
        }
    }

    private void writeDocument(ApiMapNode rootNode, XMLStreamWriter xmlWriter) throws XMLStreamException {
        xmlWriter.writeStartDocument(StandardCharsets.UTF_8.name(), "1.0");
        xmlWriter.writeCharacters("\n");
        xmlWriter.writeStartElement("map");
        xmlWriter.writeAttribute("version", MAP_VERSION);
        xmlWriter.writeCharacters("\n");
        xmlWriter.writeComment("To view this file, open it in Freeplane.");
        xmlWriter.writeCharacters("\n");
        writeNode(xmlWriter, rootNode, 0, true);
        xmlWriter.writeEndElement();
        xmlWriter.writeCharacters("\n");
        xmlWriter.writeEndDocument();
    }

    private void writeNode(XMLStreamWriter xmlWriter, ApiMapNode node, int depth, boolean root)
        throws XMLStreamException {
        indent(xmlWriter, depth);
        xmlWriter.writeStartElement("node");
        if (!node.isContentClone()) {
            xmlWriter.writeAttribute("TEXT", node.getText());
        }
        xmlWriter.writeAttribute("ID", NodeIdFactory.createId(node.getLogicalKey()));
        xmlWriter.writeAttribute("FOLDED", Boolean.toString(node.isFolded()));
        if (node.isContentClone()) {
            xmlWriter.writeAttribute("CONTENT_ID", NodeIdFactory.createId(node.getContentCloneOfLogicalKey()));
        }
        if (node.getPosition() != null && !node.getPosition().isEmpty()) {
            xmlWriter.writeAttribute("POSITION", node.getPosition());
        }
        if (node.getLink() != null && !node.getLink().isEmpty()) {
            xmlWriter.writeAttribute("LINK", node.getLink());
        }
        if (root) {
            xmlWriter.writeAttribute("STYLE", "oval");
        }
        if (root) {
            xmlWriter.writeCharacters("\n");
            indent(xmlWriter, depth + 1);
            xmlWriter.writeEmptyElement("font");
            xmlWriter.writeAttribute("SIZE", "18");
        }
        for (ApiMapNode child : node.getChildren()) {
            xmlWriter.writeCharacters("\n");
            writeNode(xmlWriter, child, depth + 1, false);
        }
        if (!node.getChildren().isEmpty() || root) {
            xmlWriter.writeCharacters("\n");
            indent(xmlWriter, depth);
        }
        xmlWriter.writeEndElement();
    }

    private void indent(XMLStreamWriter xmlWriter, int depth) throws XMLStreamException {
        for (int index = 0; index < depth; index += 1) {
            xmlWriter.writeCharacters("  ");
        }
    }
}
