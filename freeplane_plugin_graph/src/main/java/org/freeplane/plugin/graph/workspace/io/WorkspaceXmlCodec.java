package org.freeplane.plugin.graph.workspace.io;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.freeplane.plugin.graph.workspace.model.DisplaySettings;
import org.freeplane.plugin.graph.workspace.model.GraphRelationshipRecord;
import org.freeplane.plugin.graph.workspace.model.MapReference;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.freeplane.plugin.graph.workspace.model.PinRecord;
import org.freeplane.plugin.graph.workspace.model.RelationshipDirection;
import org.freeplane.plugin.graph.workspace.model.RelationshipId;
import org.freeplane.plugin.graph.workspace.model.UnknownXml;
import org.freeplane.plugin.graph.workspace.model.Viewport;
import org.freeplane.plugin.graph.workspace.model.WorkspaceCompatibility;
import org.freeplane.plugin.graph.workspace.model.WorkspaceDocument;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public final class WorkspaceXmlCodec {
    private static final int CURRENT_FORMAT_VERSION = 1;
    private static final String ROOT_ELEMENT = "graph-workspace";
    private static final String DISALLOW_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl";
    private static final String EXTERNAL_GENERAL_ENTITIES = "http://xml.org/sax/features/external-general-entities";
    private static final String EXTERNAL_PARAMETER_ENTITIES = "http://xml.org/sax/features/external-parameter-entities";
    private static final String LOAD_EXTERNAL_DTD =
        "http://apache.org/xml/features/nonvalidating/load-external-dtd";

    private final WorkspaceMigrationRegistry migrations;

    public WorkspaceXmlCodec(final WorkspaceMigrationRegistry migrations) {
        this.migrations = Objects.requireNonNull(migrations, "migrations");
    }

    public WorkspaceDocument read(final Path file) {
        Objects.requireNonNull(file, "file");
        try (InputStream input = Files.newInputStream(file)) {
            final DocumentBuilder builder = secureDocumentBuilder();
            final org.w3c.dom.Document xml = builder.parse(input);
            return parse(xml);
        }
        catch (final WorkspaceFormatException exception) {
            throw exception;
        }
        catch (final IOException exception) {
            throw new WorkspaceFormatException("Unable to read workspace XML", exception);
        }
        catch (final SAXException exception) {
            throw new WorkspaceFormatException("Unable to read workspace XML", exception);
        }
        catch (final ParserConfigurationException exception) {
            throw new WorkspaceFormatException("Unable to configure secure workspace XML parsing", exception);
        }
        catch (final RuntimeException exception) {
            throw new WorkspaceFormatException("Unable to read workspace XML", exception);
        }
    }

    public byte[] write(final WorkspaceDocument document, final Path location) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(location, "location");
        if (document.compatibility() != WorkspaceCompatibility.WRITABLE_VERSION_1
                || document.sourceFormatVersion() != CURRENT_FORMAT_VERSION) {
            throw new WorkspaceFormatException("Read-only workspace documents cannot be written",
                new IllegalStateException("The source format is newer than this codec"));
        }

        try {
            final StringBuilder output = new StringBuilder();
            output.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            final NamespaceScope rootScope = appendKnownStart(output, ROOT_ELEMENT,
                attributes("format-version", Integer.toString(CURRENT_FORMAT_VERSION), "id", document.id().toString()),
                unknownAttributes(document.unknownXml(), UnknownXml.Owner.WORKSPACE), new NamespaceScope());
            appendChildren(output, rootChildren(document, rootScope),
                unknownElements(document.unknownXml(), UnknownXml.Owner.WORKSPACE), rootScope);
            output.append("</").append(ROOT_ELEMENT).append('>');
            return output.toString().getBytes(StandardCharsets.UTF_8);
        }
        catch (final WorkspaceFormatException exception) {
            throw exception;
        }
        catch (final RuntimeException exception) {
            throw new WorkspaceFormatException("Unable to write workspace XML", exception);
        }
    }

    private WorkspaceDocument parse(final org.w3c.dom.Document xml) {
        final Element root = xml.getDocumentElement();
        if (root == null || !hasName(root, ROOT_ELEMENT)) {
            throw malformed("The workspace XML root element must be graph-workspace");
        }

        final int sourceVersion = positiveInt(requiredAttribute(root, "format-version"), "format-version");
        final WorkspaceId id = WorkspaceId.of(requiredAttribute(root, "id"));
        final List<UnknownXml> unknownXml = unknownAttributes(root, UnknownXml.Owner.WORKSPACE,
            new String[] { "format-version", "id" }, Collections.<String>emptyList());

        List<MapReference> maps = null;
        List<GraphRelationshipRecord> relationships = null;
        List<PinRecord> pins = null;
        Viewport viewport = null;
        DisplaySettings displaySettings = null;
        int position = 0;
        final NodeList children = root.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            final Node child = children.item(index);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                requireIgnorable(child, ROOT_ELEMENT);
                continue;
            }

            final Element element = (Element) child;
            if (hasName(element, "maps")) {
                if (maps != null) {
                    throw malformed("Workspace XML contains duplicate maps elements");
                }
                maps = parseMaps(element, unknownXml);
            } else if (hasName(element, "relationships")) {
                if (relationships != null) {
                    throw malformed("Workspace XML contains duplicate relationships elements");
                }
                relationships = parseRelationships(element, unknownXml);
            } else if (hasName(element, "pins")) {
                if (pins != null) {
                    throw malformed("Workspace XML contains duplicate pins elements");
                }
                pins = parsePins(element, unknownXml);
            } else if (hasName(element, "viewport")) {
                if (viewport != null) {
                    throw malformed("Workspace XML contains duplicate viewport elements");
                }
                viewport = parseViewport(element);
            } else if (hasName(element, "display-settings")) {
                if (displaySettings != null) {
                    throw malformed("Workspace XML contains duplicate display-settings elements");
                }
                displaySettings = parseDisplaySettings(element);
            } else {
                unknownXml.add(unknownElement(UnknownXml.Owner.WORKSPACE, position, element));
            }
            position++;
        }

        if (maps == null || relationships == null || pins == null || viewport == null || displaySettings == null) {
            throw malformed("Workspace XML must contain maps, relationships, pins, viewport, and display-settings");
        }

        final WorkspaceDocument.Builder builder = WorkspaceDocument.createVersion1(id).toBuilder()
            .maps(maps)
            .relationships(relationships)
            .pins(pins)
            .viewport(viewport)
            .displaySettings(displaySettings)
            .unknownXml(unknownXml);
        if (sourceVersion > CURRENT_FORMAT_VERSION) {
            return builder.sourceFormatVersion(sourceVersion)
                .compatibility(WorkspaceCompatibility.READ_ONLY_NEWER)
                .build();
        }

        final WorkspaceDocument document = builder.build();
        return sourceVersion == CURRENT_FORMAT_VERSION
            ? document
            : migrations.migrate(document, sourceVersion, CURRENT_FORMAT_VERSION);
    }

    private static List<MapReference> parseMaps(final Element container, final List<UnknownXml> documentUnknownXml) {
        documentUnknownXml.addAll(unknownAttributes(container, UnknownXml.Owner.MAPS,
            Collections.<String>emptyList(), Collections.<String>emptyList()));
        final List<MapReference> maps = new ArrayList<MapReference>();
        int position = 0;
        final NodeList children = container.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            final Node child = children.item(index);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                requireIgnorable(child, "maps");
                continue;
            }
            final Element element = (Element) child;
            if (hasName(element, "map")) {
                maps.add(parseMap(element));
            } else {
                documentUnknownXml.add(unknownElement(UnknownXml.Owner.MAPS, position, element));
            }
            position++;
        }
        return maps;
    }

    private static MapReference parseMap(final Element element) {
        final List<UnknownXml> unknownXml = recordUnknownXml(element,
            new String[] { "id", "sequence", "uri", "active", "color" },
            Collections.singletonList("status"));
        return MapReference.of(
            MapReferenceId.of(requiredAttribute(element, "id")),
            positiveLong(requiredAttribute(element, "sequence"), "map sequence"),
            URI.create(requiredAttribute(element, "uri")),
            booleanValue(requiredAttribute(element, "active"), "map active"),
            requiredAttribute(element, "color"),
            unknownXml);
    }

    private static List<GraphRelationshipRecord> parseRelationships(final Element container,
            final List<UnknownXml> documentUnknownXml) {
        documentUnknownXml.addAll(unknownAttributes(container, UnknownXml.Owner.RELATIONSHIPS,
            Collections.<String>emptyList(), Collections.<String>emptyList()));
        final List<GraphRelationshipRecord> relationships = new ArrayList<GraphRelationshipRecord>();
        int position = 0;
        final NodeList children = container.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            final Node child = children.item(index);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                requireIgnorable(child, "relationships");
                continue;
            }
            final Element element = (Element) child;
            if (hasName(element, "relationship")) {
                relationships.add(parseRelationship(element));
            } else {
                documentUnknownXml.add(unknownElement(UnknownXml.Owner.RELATIONSHIPS, position, element));
            }
            position++;
        }
        return relationships;
    }

    private static GraphRelationshipRecord parseRelationship(final Element element) {
        final List<UnknownXml> unknownXml = recordUnknownXml(element,
            new String[] { "id", "sequence", "source-map", "source-node", "target-map", "target-node", "direction" },
            Collections.<String>emptyList());
        return GraphRelationshipRecord.of(
            RelationshipId.of(requiredAttribute(element, "id")),
            positiveLong(requiredAttribute(element, "sequence"), "relationship sequence"),
            nodeReference(element, "source-map", "source-node"),
            nodeReference(element, "target-map", "target-node"),
            enumValue(RelationshipDirection.class, requiredAttribute(element, "direction"), "relationship direction"),
            unknownXml);
    }

    private static List<PinRecord> parsePins(final Element container, final List<UnknownXml> documentUnknownXml) {
        documentUnknownXml.addAll(unknownAttributes(container, UnknownXml.Owner.PINS,
            Collections.<String>emptyList(), Collections.<String>emptyList()));
        final List<PinRecord> pins = new ArrayList<PinRecord>();
        int position = 0;
        final NodeList children = container.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            final Node child = children.item(index);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                requireIgnorable(child, "pins");
                continue;
            }
            final Element element = (Element) child;
            if (hasName(element, "pin")) {
                pins.add(parsePin(element));
            } else {
                documentUnknownXml.add(unknownElement(UnknownXml.Owner.PINS, position, element));
            }
            position++;
        }
        return pins;
    }

    private static PinRecord parsePin(final Element element) {
        final List<UnknownXml> unknownXml = recordUnknownXml(element,
            new String[] { "map", "node", "x", "y" }, Collections.<String>emptyList());
        return PinRecord.of(
            nodeReference(element, "map", "node"),
            finiteDouble(requiredAttribute(element, "x"), "pin x"),
            finiteDouble(requiredAttribute(element, "y"), "pin y"),
            unknownXml);
    }

    private static Viewport parseViewport(final Element element) {
        final List<UnknownXml> unknownXml = recordUnknownXml(element,
            new String[] { "center-x", "center-y", "zoom" }, Collections.<String>emptyList());
        return Viewport.of(
            finiteDouble(requiredAttribute(element, "center-x"), "viewport center-x"),
            finiteDouble(requiredAttribute(element, "center-y"), "viewport center-y"),
            finiteDouble(requiredAttribute(element, "zoom"), "viewport zoom"),
            unknownXml);
    }

    private static DisplaySettings parseDisplaySettings(final Element element) {
        final List<UnknownXml> unknownXml = recordUnknownXml(element,
            new String[] { "show-arrowheads", "canvas-theme", "remember-viewport", "dim-unrelated-nodes" },
            Collections.<String>emptyList());
        return DisplaySettings.of(
            booleanValue(requiredAttribute(element, "show-arrowheads"), "show-arrowheads"),
            enumValue(DisplaySettings.CanvasTheme.class, requiredAttribute(element, "canvas-theme"), "canvas-theme"),
            booleanValue(requiredAttribute(element, "remember-viewport"), "remember-viewport"),
            booleanValue(requiredAttribute(element, "dim-unrelated-nodes"), "dim-unrelated-nodes"),
            unknownXml);
    }

    private static NodeReference nodeReference(final Element element, final String mapAttribute,
            final String nodeAttribute) {
        return NodeReference.of(
            MapReferenceId.of(requiredAttribute(element, mapAttribute)),
            PersistedNodeId.of(requiredAttribute(element, nodeAttribute)));
    }

    private static List<UnknownXml> recordUnknownXml(final Element element, final String[] knownAttributes,
            final List<String> ignoredAttributes) {
        final List<UnknownXml> unknownXml = unknownAttributes(element, UnknownXml.Owner.RECORD,
            knownAttributes, ignoredAttributes);
        int position = 0;
        final NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            final Node child = children.item(index);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                unknownXml.add(unknownElement(UnknownXml.Owner.RECORD, position, (Element) child));
                position++;
            } else {
                requireIgnorable(child, localName(element));
            }
        }
        return unknownXml;
    }

    private static List<UnknownXml> unknownAttributes(final Element element, final UnknownXml.Owner owner,
            final List<String> knownAttributes, final List<String> ignoredAttributes) {
        return unknownAttributes(element, owner, knownAttributes.toArray(new String[knownAttributes.size()]),
            ignoredAttributes);
    }

    private static List<UnknownXml> unknownAttributes(final Element element, final UnknownXml.Owner owner,
            final String[] knownAttributes, final List<String> ignoredAttributes) {
        final List<UnknownXml> unknownXml = new ArrayList<UnknownXml>();
        final NamedNodeMap attributes = element.getAttributes();
        for (int index = 0; index < attributes.getLength(); index++) {
            final Attr attribute = (Attr) attributes.item(index);
            if (isNamespaceDeclaration(attribute)) {
                unknownXml.add(UnknownXml.attribute(owner, qName(attribute), attribute.getValue()));
                continue;
            }
            final String name = localName(attribute);
            if (hasNoNamespace(attribute) && (contains(knownAttributes, name) || ignoredAttributes.contains(name))) {
                continue;
            }
            unknownXml.add(UnknownXml.attribute(owner, qName(attribute), attribute.getValue()));
        }
        return unknownXml;
    }

    private static UnknownXml unknownElement(final UnknownXml.Owner owner, final int position,
            final Element element) {
        return UnknownXml.element(owner, position, qName(element), unknownElementAttributes(element),
            unknownContent(element));
    }

    private static Map<QName, String> unknownElementAttributes(final Element element) {
        final Map<QName, String> attributes = new TreeMap<QName, String>(new java.util.Comparator<QName>() {
            @Override
            public int compare(final QName first, final QName second) {
                int result = first.getNamespaceURI().compareTo(second.getNamespaceURI());
                if (result != 0) {
                    return result;
                }
                result = first.getLocalPart().compareTo(second.getLocalPart());
                if (result != 0) {
                    return result;
                }
                return first.getPrefix().compareTo(second.getPrefix());
            }
        });
        final NamedNodeMap values = element.getAttributes();
        for (int index = 0; index < values.getLength(); index++) {
            final Attr attribute = (Attr) values.item(index);
            attributes.put(qName(attribute), attribute.getValue());
        }
        return attributes;
    }

    private static List<UnknownXml.Content> unknownContent(final Element element) {
        final List<UnknownXml.Content> content = new ArrayList<UnknownXml.Content>();
        final NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            final Node child = children.item(index);
            switch (child.getNodeType()) {
            case Node.ELEMENT_NODE:
                final Element childElement = (Element) child;
                content.add(UnknownXml.Content.element(qName(childElement), unknownElementAttributes(childElement),
                    unknownContent(childElement)));
                break;
            case Node.TEXT_NODE:
            case Node.CDATA_SECTION_NODE:
                content.add(UnknownXml.Content.text(child.getNodeValue()));
                break;
            case Node.COMMENT_NODE:
            case Node.PROCESSING_INSTRUCTION_NODE:
            case Node.ENTITY_REFERENCE_NODE:
                break;
            default:
                throw malformed("Unsupported content in unknown workspace XML element");
            }
        }
        return content;
    }

    private static DocumentBuilder secureDocumentBuilder() throws ParserConfigurationException {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature(DISALLOW_DOCTYPE, true);
        factory.setFeature(EXTERNAL_GENERAL_ENTITIES, false);
        factory.setFeature(EXTERNAL_PARAMETER_ENTITIES, false);
        factory.setFeature(LOAD_EXTERNAL_DTD, false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        final DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new ErrorHandler() {
            @Override
            public void warning(final SAXParseException exception) throws SAXException {
                throw exception;
            }

            @Override
            public void error(final SAXParseException exception) throws SAXException {
                throw exception;
            }

            @Override
            public void fatalError(final SAXParseException exception) throws SAXException {
                throw exception;
            }
        });
        return builder;
    }

    private static List<String> rootChildren(final WorkspaceDocument document, final NamespaceScope parent) {
        final List<String> children = new ArrayList<String>();
        children.add(mapsXml(document, parent));
        children.add(relationshipsXml(document, parent));
        children.add(pinsXml(document, parent));
        children.add(viewportXml(document.viewport(), parent));
        children.add(displaySettingsXml(document.displaySettings(), parent));
        return children;
    }

    private static String mapsXml(final WorkspaceDocument document, final NamespaceScope parent) {
        final StringBuilder output = new StringBuilder();
        final NamespaceScope scope = appendKnownStart(output, "maps", attributes(),
            unknownAttributes(document.unknownXml(), UnknownXml.Owner.MAPS), parent);
        final List<String> maps = new ArrayList<String>();
        for (final MapReference map : document.maps()) {
            maps.add(mapXml(map, scope));
        }
        appendChildren(output, maps, unknownElements(document.unknownXml(), UnknownXml.Owner.MAPS), scope);
        output.append("</maps>");
        return output.toString();
    }

    private static String mapXml(final MapReference map, final NamespaceScope parent) {
        final StringBuilder output = new StringBuilder();
        final NamespaceScope scope = appendKnownStart(output, "map", attributes(
            "id", map.id().toString(),
            "sequence", Long.toString(map.sequence()),
            "uri", map.storedUri().toString(),
            "active", Boolean.toString(map.active()),
            "color", map.color()), unknownAttributes(map.unknownXml(), UnknownXml.Owner.RECORD), parent);
        appendChildren(output, Collections.<String>emptyList(),
            unknownElements(map.unknownXml(), UnknownXml.Owner.RECORD), scope);
        output.append("</map>");
        return output.toString();
    }

    private static String relationshipsXml(final WorkspaceDocument document, final NamespaceScope parent) {
        final StringBuilder output = new StringBuilder();
        final NamespaceScope scope = appendKnownStart(output, "relationships", attributes(),
            unknownAttributes(document.unknownXml(), UnknownXml.Owner.RELATIONSHIPS), parent);
        final List<String> relationships = new ArrayList<String>();
        for (final GraphRelationshipRecord relationship : document.relationships()) {
            relationships.add(relationshipXml(relationship, scope));
        }
        appendChildren(output, relationships,
            unknownElements(document.unknownXml(), UnknownXml.Owner.RELATIONSHIPS), scope);
        output.append("</relationships>");
        return output.toString();
    }

    private static String relationshipXml(final GraphRelationshipRecord relationship, final NamespaceScope parent) {
        final StringBuilder output = new StringBuilder();
        final NamespaceScope scope = appendKnownStart(output, "relationship", attributes(
            "id", relationship.id().toString(),
            "sequence", Long.toString(relationship.sequence()),
            "source-map", relationship.source().mapReferenceId().toString(),
            "source-node", relationship.source().nodeId().value(),
            "target-map", relationship.target().mapReferenceId().toString(),
            "target-node", relationship.target().nodeId().value(),
            "direction", relationship.direction().name()),
            unknownAttributes(relationship.unknownXml(), UnknownXml.Owner.RECORD), parent);
        appendChildren(output, Collections.<String>emptyList(),
            unknownElements(relationship.unknownXml(), UnknownXml.Owner.RECORD), scope);
        output.append("</relationship>");
        return output.toString();
    }

    private static String pinsXml(final WorkspaceDocument document, final NamespaceScope parent) {
        final StringBuilder output = new StringBuilder();
        final NamespaceScope scope = appendKnownStart(output, "pins", attributes(),
            unknownAttributes(document.unknownXml(), UnknownXml.Owner.PINS), parent);
        final List<String> pins = new ArrayList<String>();
        for (final PinRecord pin : document.pins()) {
            pins.add(pinXml(pin, scope));
        }
        appendChildren(output, pins, unknownElements(document.unknownXml(), UnknownXml.Owner.PINS), scope);
        output.append("</pins>");
        return output.toString();
    }

    private static String pinXml(final PinRecord pin, final NamespaceScope parent) {
        final StringBuilder output = new StringBuilder();
        final NamespaceScope scope = appendKnownStart(output, "pin", attributes(
            "map", pin.node().mapReferenceId().toString(),
            "node", pin.node().nodeId().value(),
            "x", Double.toString(pin.x()),
            "y", Double.toString(pin.y())), unknownAttributes(pin.unknownXml(), UnknownXml.Owner.RECORD), parent);
        appendChildren(output, Collections.<String>emptyList(),
            unknownElements(pin.unknownXml(), UnknownXml.Owner.RECORD), scope);
        output.append("</pin>");
        return output.toString();
    }

    private static String viewportXml(final Viewport viewport, final NamespaceScope parent) {
        final StringBuilder output = new StringBuilder();
        final NamespaceScope scope = appendKnownStart(output, "viewport", attributes(
            "center-x", Double.toString(viewport.centerX()),
            "center-y", Double.toString(viewport.centerY()),
            "zoom", Double.toString(viewport.zoom())),
            unknownAttributes(viewport.unknownXml(), UnknownXml.Owner.RECORD), parent);
        appendChildren(output, Collections.<String>emptyList(),
            unknownElements(viewport.unknownXml(), UnknownXml.Owner.RECORD), scope);
        output.append("</viewport>");
        return output.toString();
    }

    private static String displaySettingsXml(final DisplaySettings settings, final NamespaceScope parent) {
        final StringBuilder output = new StringBuilder();
        final NamespaceScope scope = appendKnownStart(output, "display-settings", attributes(
            "show-arrowheads", Boolean.toString(settings.showArrowheads()),
            "canvas-theme", settings.canvasTheme().name(),
            "remember-viewport", Boolean.toString(settings.rememberViewport()),
            "dim-unrelated-nodes", Boolean.toString(settings.dimUnrelatedNodes())),
            unknownAttributes(settings.unknownXml(), UnknownXml.Owner.RECORD), parent);
        appendChildren(output, Collections.<String>emptyList(),
            unknownElements(settings.unknownXml(), UnknownXml.Owner.RECORD), scope);
        output.append("</display-settings>");
        return output.toString();
    }

    private static void appendChildren(final StringBuilder output, final List<String> knownChildren,
            final List<UnknownXml> unknownChildren, final NamespaceScope parent) {
        int knownIndex = 0;
        int nextPosition = 0;
        for (final UnknownXml child : unknownChildren) {
            while (knownIndex < knownChildren.size() && nextPosition < child.position()) {
                output.append(knownChildren.get(knownIndex++));
                nextPosition++;
            }
            appendUnknownElement(output, child, parent);
            nextPosition = Math.max(nextPosition, child.position() + 1);
        }
        while (knownIndex < knownChildren.size()) {
            output.append(knownChildren.get(knownIndex++));
        }
    }

    private static NamespaceScope appendKnownStart(final StringBuilder output, final String elementName,
            final List<KnownAttribute> knownAttributes, final List<UnknownXml> unknownAttributes,
            final NamespaceScope parent) {
        final NamespaceScope scope = parent.copy();
        final List<WrittenAttribute> attributes = new ArrayList<WrittenAttribute>();
        for (final UnknownXml attribute : unknownAttributes) {
            if (isNamespaceDeclaration(attribute.name())) {
                scope.declareNamespace(namespacePrefix(attribute.name()), attribute.attributeValue().get());
            }
        }
        for (final UnknownXml attribute : unknownAttributes) {
            if (isNamespaceDeclaration(attribute.name())) {
                continue;
            }
            attributes.add(new WrittenAttribute(scope.qualifiedAttributeName(attribute.name()),
                attribute.attributeValue().get()));
        }
        output.append('<').append(scope.knownElementName(elementName));
        appendNamespaceDeclarations(output, scope.additions(parent));
        appendKnownAttributes(output, knownAttributes);
        appendWrittenAttributes(output, attributes);
        output.append('>');
        return scope;
    }

    private static void appendUnknownElement(final StringBuilder output, final UnknownXml element,
            final NamespaceScope parent) {
        appendUnknownElement(output, element.name(), element.attributes(), element.content(), parent);
    }

    private static void appendUnknownElement(final StringBuilder output, final QName name,
            final Map<QName, String> attributes, final List<UnknownXml.Content> content,
            final NamespaceScope parent) {
        final NamespaceScope scope = parent.copy();
        for (final Map.Entry<QName, String> attribute : attributes.entrySet()) {
            if (isNamespaceDeclaration(attribute.getKey())) {
                scope.declareNamespace(namespacePrefix(attribute.getKey()), attribute.getValue());
            }
        }
        final String elementName = scope.qualifiedElementName(name);
        final List<WrittenAttribute> writtenAttributes = new ArrayList<WrittenAttribute>();
        for (final Map.Entry<QName, String> attribute : attributes.entrySet()) {
            if (isNamespaceDeclaration(attribute.getKey())) {
                continue;
            }
            writtenAttributes.add(new WrittenAttribute(scope.qualifiedAttributeName(attribute.getKey()),
                attribute.getValue()));
        }
        output.append('<').append(elementName);
        appendNamespaceDeclarations(output, scope.additions(parent));
        appendWrittenAttributes(output, writtenAttributes);
        output.append('>');
        for (final UnknownXml.Content item : content) {
            if (item.kind() == UnknownXml.Content.Kind.TEXT) {
                appendEscaped(output, item.text().get(), false);
            } else {
                appendUnknownElement(output, item.name().get(), item.attributes(), item.content(), scope);
            }
        }
        output.append("</").append(elementName).append('>');
    }

    private static void appendNamespaceDeclarations(final StringBuilder output,
            final List<NamespaceBinding> bindings) {
        for (final NamespaceBinding binding : bindings) {
            output.append(" xmlns");
            if (!binding.prefix.isEmpty()) {
                output.append(':').append(binding.prefix);
            }
            output.append("=\"");
            appendEscaped(output, binding.namespace, true);
            output.append('"');
        }
    }

    private static void appendKnownAttributes(final StringBuilder output, final List<KnownAttribute> attributes) {
        for (final KnownAttribute attribute : attributes) {
            output.append(' ').append(attribute.name).append("=\"");
            appendEscaped(output, attribute.value, true);
            output.append('"');
        }
    }

    private static void appendWrittenAttributes(final StringBuilder output, final List<WrittenAttribute> attributes) {
        for (final WrittenAttribute attribute : attributes) {
            output.append(' ').append(attribute.name).append("=\"");
            appendEscaped(output, attribute.value, true);
            output.append('"');
        }
    }

    private static void appendEscaped(final StringBuilder output, final String value, final boolean attribute) {
        for (int offset = 0; offset < value.length();) {
            final int codePoint = value.codePointAt(offset);
            if (!isXmlCharacter(codePoint)) {
                throw malformed("Workspace XML contains a character that XML 1.0 cannot represent");
            }
            switch (codePoint) {
            case '&':
                output.append("&amp;");
                break;
            case '<':
                output.append("&lt;");
                break;
            case '>':
                output.append("&gt;");
                break;
            case '"':
                if (attribute) {
                    output.append("&quot;");
                } else {
                    output.append('"');
                }
                break;
            case '\r':
                output.append("&#13;");
                break;
            case '\n':
                if (attribute) {
                    output.append("&#10;");
                } else {
                    output.append('\n');
                }
                break;
            case '\t':
                if (attribute) {
                    output.append("&#9;");
                } else {
                    output.append('\t');
                }
                break;
            default:
                output.appendCodePoint(codePoint);
                break;
            }
            offset += Character.charCount(codePoint);
        }
    }

    private static boolean isXmlCharacter(final int value) {
        return value == 0x9 || value == 0xA || value == 0xD
            || value >= 0x20 && value <= 0xD7FF
            || value >= 0xE000 && value <= 0xFFFD
            || value >= 0x10000 && value <= 0x10FFFF;
    }

    private static List<KnownAttribute> attributes(final String... values) {
        if (values.length % 2 != 0) {
            throw new IllegalArgumentException("Known XML attributes need name and value pairs");
        }
        final List<KnownAttribute> attributes = new ArrayList<KnownAttribute>(values.length / 2);
        for (int index = 0; index < values.length; index += 2) {
            attributes.add(new KnownAttribute(values[index], values[index + 1]));
        }
        return attributes;
    }

    private static List<UnknownXml> unknownAttributes(final List<UnknownXml> values,
            final UnknownXml.Owner owner) {
        return unknownXml(values, owner, UnknownXml.Kind.ATTRIBUTE);
    }

    private static List<UnknownXml> unknownElements(final List<UnknownXml> values,
            final UnknownXml.Owner owner) {
        return unknownXml(values, owner, UnknownXml.Kind.ELEMENT);
    }

    private static List<UnknownXml> unknownXml(final List<UnknownXml> values, final UnknownXml.Owner owner,
            final UnknownXml.Kind kind) {
        final List<UnknownXml> selected = new ArrayList<UnknownXml>();
        for (final UnknownXml value : values) {
            if (value.owner() == owner && value.kind() == kind) {
                selected.add(value);
            }
        }
        return selected;
    }

    private static boolean hasName(final Element element, final String expectedName) {
        return hasNoNamespace(element) && expectedName.equals(localName(element));
    }

    private static boolean hasNoNamespace(final Node node) {
        final String namespace = node.getNamespaceURI();
        return namespace == null || namespace.isEmpty();
    }

    private static String localName(final Node node) {
        final String localName = node.getLocalName();
        return localName == null ? node.getNodeName() : localName;
    }

    private static QName qName(final Node node) {
        final String namespace = node.getNamespaceURI() == null ? "" : node.getNamespaceURI();
        final String prefix = node.getPrefix() == null ? "" : node.getPrefix();
        return new QName(namespace, localName(node), prefix);
    }

    private static boolean isNamespaceDeclaration(final Attr attribute) {
        return XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attribute.getNamespaceURI())
            || "xmlns".equals(attribute.getName());
    }

    private static boolean isNamespaceDeclaration(final QName name) {
        return XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(name.getNamespaceURI());
    }

    private static String namespacePrefix(final QName name) {
        if (!isNamespaceDeclaration(name)) {
            throw malformed("Unknown XML namespace declaration is invalid");
        }
        if ("xmlns".equals(name.getPrefix())) {
            return name.getLocalPart();
        }
        if (name.getPrefix().isEmpty() && "xmlns".equals(name.getLocalPart())) {
            return "";
        }
        throw malformed("Unknown XML namespace declaration is invalid");
    }

    private static String requiredAttribute(final Element element, final String name) {
        if (!element.hasAttribute(name)) {
            throw malformed("Missing required " + name + " attribute on " + localName(element));
        }
        final String value = element.getAttribute(name);
        if (value.isEmpty()) {
            throw malformed("Required " + name + " attribute on " + localName(element) + " must not be empty");
        }
        return value;
    }

    private static int positiveInt(final String value, final String description) {
        try {
            final int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw malformed(description + " must be positive");
            }
            return parsed;
        }
        catch (final NumberFormatException exception) {
            throw new WorkspaceFormatException(description + " must be an integer", exception);
        }
    }

    private static long positiveLong(final String value, final String description) {
        try {
            final long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw malformed(description + " must be positive");
            }
            return parsed;
        }
        catch (final NumberFormatException exception) {
            throw new WorkspaceFormatException(description + " must be an integer", exception);
        }
    }

    private static double finiteDouble(final String value, final String description) {
        try {
            final double parsed = Double.parseDouble(value);
            if (Double.isNaN(parsed) || Double.isInfinite(parsed)) {
                throw malformed(description + " must be finite");
            }
            return parsed;
        }
        catch (final NumberFormatException exception) {
            throw new WorkspaceFormatException(description + " must be numeric", exception);
        }
    }

    private static boolean booleanValue(final String value, final String description) {
        if ("true".equals(value)) {
            return true;
        }
        if ("false".equals(value)) {
            return false;
        }
        throw malformed(description + " must be true or false");
    }

    private static <T extends Enum<T>> T enumValue(final Class<T> type, final String value,
            final String description) {
        try {
            return Enum.valueOf(type, value);
        }
        catch (final IllegalArgumentException exception) {
            throw new WorkspaceFormatException(description + " is invalid", exception);
        }
    }

    private static void requireIgnorable(final Node node, final String parentName) {
        switch (node.getNodeType()) {
        case Node.TEXT_NODE:
        case Node.CDATA_SECTION_NODE:
            if (!isXmlWhitespace(node.getNodeValue())) {
                throw malformed("Unexpected text in " + parentName);
            }
            break;
        case Node.COMMENT_NODE:
        case Node.PROCESSING_INSTRUCTION_NODE:
        case Node.ENTITY_REFERENCE_NODE:
            break;
        default:
            throw malformed("Unsupported XML content in " + parentName);
        }
    }

    private static boolean isXmlWhitespace(final String value) {
        for (int index = 0; index < value.length(); index++) {
            final char character = value.charAt(index);
            if (character != ' ' && character != '\t' && character != '\n' && character != '\r') {
                return false;
            }
        }
        return true;
    }

    private static boolean contains(final String[] values, final String candidate) {
        for (final String value : values) {
            if (value.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static WorkspaceFormatException malformed(final String message) {
        return new WorkspaceFormatException(message, new IllegalArgumentException(message));
    }

    private static final class KnownAttribute {
        private final String name;
        private final String value;

        private KnownAttribute(final String name, final String value) {
            this.name = name;
            this.value = value;
        }
    }

    private static final class WrittenAttribute {
        private final String name;
        private final String value;

        private WrittenAttribute(final String name, final String value) {
            this.name = name;
            this.value = value;
        }
    }

    private static final class NamespaceBinding {
        private final String prefix;
        private final String namespace;

        private NamespaceBinding(final String prefix, final String namespace) {
            this.prefix = prefix;
            this.namespace = namespace;
        }
    }

    private static final class NamespaceScope {
        private final Map<String, String> bindings;
        private int nextGeneratedPrefix;

        private NamespaceScope() {
            this(new TreeMap<String, String>(), 1);
        }

        private NamespaceScope(final Map<String, String> bindings, final int nextGeneratedPrefix) {
            this.bindings = bindings;
            this.nextGeneratedPrefix = nextGeneratedPrefix;
        }

        private NamespaceScope copy() {
            return new NamespaceScope(new TreeMap<String, String>(bindings), nextGeneratedPrefix);
        }

        private void declareNamespace(final String prefix, final String namespace) {
            if ("xmlns".equals(prefix) || XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(namespace)
                    || !prefix.isEmpty() && namespace.isEmpty()
                    || "xml".equals(prefix) && !XMLConstants.XML_NS_URI.equals(namespace)
                    || !"xml".equals(prefix) && XMLConstants.XML_NS_URI.equals(namespace)) {
                throw malformed("Unknown XML namespace declaration is invalid");
            }
            bindings.put(prefix, namespace);
        }

        private String knownElementName(final String name) {
            if (bindings.containsKey("") && !bindings.get("").isEmpty()) {
                bindings.put("", "");
            }
            return name;
        }

        private String qualifiedElementName(final QName name) {
            return qualifiedName(name, true);
        }

        private String qualifiedAttributeName(final QName name) {
            return qualifiedName(name, false);
        }

        private String qualifiedName(final QName name, final boolean element) {
            final String localPart = name.getLocalPart();
            if (localPart == null || localPart.isEmpty()) {
                throw malformed("Unknown XML names must have a local part");
            }
            final String namespace = name.getNamespaceURI();
            if (namespace == null || namespace.isEmpty()) {
                if (element && bindings.containsKey("") && !bindings.get("").isEmpty()) {
                    bindings.put("", "");
                }
                return localPart;
            }
            if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(namespace)) {
                throw malformed("Unknown XML must not use the xmlns namespace as content");
            }
            if (XMLConstants.XML_NS_URI.equals(namespace)) {
                return "xml:" + localPart;
            }

            String prefix = name.getPrefix();
            if (element && (prefix == null || prefix.isEmpty())) {
                bindings.put("", namespace);
                return localPart;
            }
            if (prefix == null || prefix.isEmpty() || "xml".equals(prefix) || "xmlns".equals(prefix)
                    || bindings.containsKey(prefix) && !namespace.equals(bindings.get(prefix))) {
                prefix = generatedPrefix();
            }
            bindings.put(prefix, namespace);
            return prefix + ':' + localPart;
        }

        private String generatedPrefix() {
            String prefix;
            do {
                prefix = "ns" + nextGeneratedPrefix++;
            } while (bindings.containsKey(prefix));
            return prefix;
        }

        private List<NamespaceBinding> additions(final NamespaceScope parent) {
            final List<NamespaceBinding> additions = new ArrayList<NamespaceBinding>();
            for (final Map.Entry<String, String> binding : bindings.entrySet()) {
                if (!binding.getValue().equals(parent.bindings.get(binding.getKey()))) {
                    additions.add(new NamespaceBinding(binding.getKey(), binding.getValue()));
                }
            }
            return additions;
        }
    }
}
