package org.freeplane.plugin.script.doclet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

final class FreeplaneMindMapWriter {
    static final String TEMPLATE_RESOURCE = "/org/freeplane/plugin/script/doclet/freeplane-api-template.mm";

    public void write(ApiMapNode rootNode, File outputFile) throws IOException {
        if (outputFile.getParentFile() != null) {
            outputFile.getParentFile().mkdirs();
        }
        try {
            Document document = loadTemplateDocument();
            Element mapElement = requireMapElement(document);
            Element rootElement = requireSingleRootNode(mapElement);
            assertTemplateHasNoChildContentNodes(rootElement);
            for (ApiMapNode child : rootNode.getChildren()) {
                appendGeneratedNode(document, rootElement, child);
            }
            writeDocument(document, outputFile);
        }
        catch (ParserConfigurationException error) {
            throw new IOException("Failed to configure Freeplane API map XML template parser.", error);
        }
        catch (SAXException error) {
            throw new IOException("Failed to parse Freeplane API map template.", error);
        }
        catch (TransformerException error) {
            throw new IOException("Failed to write Freeplane API map XML.", error);
        }
    }

    private Document loadTemplateDocument() throws IOException, ParserConfigurationException, SAXException {
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setNamespaceAware(false);
        try (java.io.InputStream inputStream = DocletResourceLoader.openRequiredResource(TEMPLATE_RESOURCE)) {
            return documentBuilderFactory.newDocumentBuilder().parse(inputStream);
        }
    }

    private Element requireMapElement(Document document) {
        Element mapElement = document.getDocumentElement();
        if (mapElement == null || !"map".equals(mapElement.getTagName())) {
            throw new IllegalStateException("Freeplane API map template must have a <map> root element.");
        }
        return mapElement;
    }

    private Element requireSingleRootNode(Element mapElement) {
        Element rootNode = null;
        for (Node child = mapElement.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (!(child instanceof Element)) {
                continue;
            }
            Element childElement = (Element) child;
            if (!"node".equals(childElement.getTagName())) {
                continue;
            }
            if (rootNode != null) {
                throw new IllegalStateException("Freeplane API map template must contain exactly one root <node> element.");
            }
            rootNode = childElement;
        }
        if (rootNode == null) {
            throw new IllegalStateException("Freeplane API map template must contain a root <node> element.");
        }
        return rootNode;
    }

    private void assertTemplateHasNoChildContentNodes(Element rootElement) {
        for (Node child = rootElement.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element && "node".equals(((Element) child).getTagName())) {
                throw new IllegalStateException(
                    "Freeplane API map template root must not contain preexisting child <node> elements.");
            }
        }
    }

    private void appendGeneratedNode(Document document, Element parent, ApiMapNode node) {
        Element nodeElement = document.createElement("node");
        if (!node.isContentClone()) {
            nodeElement.setAttribute("TEXT", node.getText());
        }
        nodeElement.setAttribute("ID", NodeIdFactory.createId(node.getLogicalKey()));
        nodeElement.setAttribute("FOLDED", Boolean.toString(node.isFolded()));
        if (node.isContentClone()) {
            nodeElement.setAttribute("CONTENT_ID", NodeIdFactory.createId(node.getContentCloneOfLogicalKey()));
        }
        if (node.getPosition() != null && !node.getPosition().isEmpty()) {
            nodeElement.setAttribute("POSITION", node.getPosition());
        }
        if (node.getLink() != null && !node.getLink().isEmpty()) {
            nodeElement.setAttribute("LINK", node.getLink());
        }
        parent.appendChild(nodeElement);
        for (ApiMapNode child : node.getChildren()) {
            appendGeneratedNode(document, nodeElement, child);
        }
    }

    private void writeDocument(Document document, File outputFile) throws IOException, TransformerException {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8)) {
            transformer.transform(new DOMSource(document), new StreamResult(writer));
        }
    }
}
