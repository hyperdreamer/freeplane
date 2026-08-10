package org.freeplane.plugin.graph.workspace.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.xml.namespace.QName;

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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class WorkspaceXmlCodecShould {
    private static final String FUTURE_NAMESPACE = "urn:freeplane:graph:future";
    private static final WorkspaceId WORKSPACE_ID =
        WorkspaceId.of("00000000-0000-0000-0000-000000000100");
    private static final MapReferenceId MAP_ONE =
        MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    private static final MapReferenceId MAP_TWO =
        MapReferenceId.of("00000000-0000-0000-0000-000000000002");

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void roundTripAllKnownAndUnknownFieldsWithoutWritingDuringRead() throws Exception {
        WorkspaceXmlCodec codec = codec();
        Path source = resource("format-1-full.fpg");
        byte[] sourceBeforeRead = Files.readAllBytes(source);

        WorkspaceDocument document = codec.read(source);

        assertThat(document).isEqualTo(fullDocument());
        assertThat(Files.readAllBytes(source)).isEqualTo(sourceBeforeRead);

        Path location = temporaryFolder.newFolder("written").toPath().resolve("workspace.fpg");
        byte[] firstWrite = codec.write(document, location);
        byte[] secondWrite = codec.write(document, location);

        assertThat(secondWrite).isEqualTo(firstWrite);
        assertThat(new String(firstWrite, StandardCharsets.UTF_8))
            .contains("future:root-attribute=\"root-value\"")
            .doesNotContain("status=");

        Files.write(location, firstWrite);
        assertThat(codec.read(location)).isEqualTo(document);
    }

    @Test
    public void retainNewerStructuredFieldsInReadOnlyDocuments() throws Exception {
        WorkspaceDocument document = codec().read(resource("format-newer-lossless.fpg"));

        assertThat(document.sourceFormatVersion()).isEqualTo(2);
        assertThat(document.compatibility()).isEqualTo(WorkspaceCompatibility.READ_ONLY_NEWER);
        assertThat(document.unknownXml()).contains(
            unknownAttribute(UnknownXml.Owner.WORKSPACE, "root-attribute", "newer-root"),
            unknownElement(UnknownXml.Owner.WORKSPACE, 2, "future-section", "future"));
        assertThat(document.maps().get(0).unknownXml()).contains(
            unknownAttribute(UnknownXml.Owner.RECORD, "map-attribute", "newer-map"));
    }

    @Test
    public void rejectMalformedKnownFieldsInNewerDocuments() throws Exception {
        assertThatThrownBy(() -> codec().read(resource("format-newer-invalid.fpg")))
            .isInstanceOf(WorkspaceFormatException.class);
    }

    @Test
    public void rejectWritingReadOnlyNewerDocuments() throws Exception {
        WorkspaceDocument document = codec().read(resource("format-newer-lossless.fpg"));

        assertThatThrownBy(() -> codec().write(document, temporaryFolder.getRoot().toPath().resolve("newer.fpg")))
            .isInstanceOf(WorkspaceFormatException.class);
    }

    @Test
    public void rejectExternalEntityWithoutReadingSentinel() throws Exception {
        try (ServerSocket sentinel = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            sentinel.setSoTimeout(250);
            AtomicBoolean requested = new AtomicBoolean();
            Thread listener = sentinelListener(sentinel, requested);
            listener.start();

            Path malicious = temporaryFolder.newFile("external-entity.fpg").toPath();
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<!DOCTYPE graph-workspace [<!ENTITY external SYSTEM \"http://127.0.0.1:"
                + sentinel.getLocalPort() + "/sentinel\">]>"
                + "<graph-workspace format-version=\"1\" id=\"" + WORKSPACE_ID + "\">"
                + "<maps></maps><relationships></relationships><pins></pins>"
                + "<viewport center-x=\"0\" center-y=\"0\" zoom=\"1\"/>"
                + "<display-settings show-arrowheads=\"true\" canvas-theme=\"FOLLOW_FREEPLANE\" "
                + "remember-viewport=\"true\" dim-unrelated-nodes=\"true\"/>"
                + "<extension>&external;</extension></graph-workspace>";
            Files.write(malicious, xml.getBytes(StandardCharsets.UTF_8));

            assertThatThrownBy(() -> codec().read(malicious))
                .isInstanceOf(WorkspaceFormatException.class);
            listener.join(1000);
            assertThat(requested).isFalse();
        }
    }

    private static Thread sentinelListener(final ServerSocket sentinel, final AtomicBoolean requested) {
        Thread listener = new Thread(new Runnable() {
            @Override
            public void run() {
                try (Socket ignored = sentinel.accept()) {
                    requested.set(true);
                }
                catch (SocketTimeoutException ignored) {
                }
                catch (IOException exception) {
                    throw new AssertionError(exception);
                }
            }
        }, "workspace-xxe-sentinel");
        listener.setDaemon(true);
        return listener;
    }

    private WorkspaceXmlCodec codec() {
        return new WorkspaceXmlCodec(new WorkspaceMigrationRegistry(Collections.<WorkspaceMigration>emptyList()));
    }

    private Path resource(String name) throws IOException {
        Path destination = temporaryFolder.newFile(name).toPath();
        InputStream stream = getClass().getResourceAsStream("/workspace/" + name);
        assertThat(stream).as("test resource %s", name).isNotNull();
        try (InputStream input = stream) {
            Files.copy(input, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return destination;
    }

    private static WorkspaceDocument fullDocument() {
        MapReference firstMap = MapReference.of(MAP_ONE, 1, URI.create("maps/one.mm"), true, "#4E79A7",
            Arrays.asList(
                unknownAttribute(UnknownXml.Owner.RECORD, "map-attribute", "map-one"),
                unknownElement(UnknownXml.Owner.RECORD, 0, "map-one-element", "map-one")));
        MapReference secondMap = MapReference.of(MAP_TWO, 2, URI.create("file:///tmp/two.mm"), false, "#F28E2B",
            Arrays.asList(
                unknownAttribute(UnknownXml.Owner.RECORD, "map-attribute", "map-two"),
                unknownElement(UnknownXml.Owner.RECORD, 0, "map-two-element", "map-two")));
        NodeReference source = NodeReference.of(MAP_ONE, PersistedNodeId.of("source node"));
        NodeReference target = NodeReference.of(MAP_TWO, PersistedNodeId.of("target-node"));
        GraphRelationshipRecord relationship = GraphRelationshipRecord.of(
            RelationshipId.of("00000000-0000-0000-0000-000000000010"), 1,
            source, target, RelationshipDirection.FORWARD,
            Arrays.asList(
                unknownAttribute(UnknownXml.Owner.RECORD, "relationship-attribute", "relationship"),
                unknownElement(UnknownXml.Owner.RECORD, 0, "relationship-element", "relationship")));
        PinRecord pin = PinRecord.of(source, 12.5, -4.25,
            Arrays.asList(
                unknownAttribute(UnknownXml.Owner.RECORD, "pin-attribute", "pin"),
                unknownElement(UnknownXml.Owner.RECORD, 0, "pin-element", "pin")));
        Viewport viewport = Viewport.of(1.5, -2.5, 0.75,
            Arrays.asList(
                unknownAttribute(UnknownXml.Owner.RECORD, "viewport-attribute", "viewport"),
                unknownElement(UnknownXml.Owner.RECORD, 0, "viewport-element", "viewport")));
        DisplaySettings settings = DisplaySettings.of(false, DisplaySettings.CanvasTheme.DARK, false, true,
            Arrays.asList(
                unknownAttribute(UnknownXml.Owner.RECORD, "display-attribute", "display"),
                unknownElement(UnknownXml.Owner.RECORD, 0, "display-element", "display")));

        return WorkspaceDocument.createVersion1(WORKSPACE_ID).toBuilder()
            .maps(Arrays.asList(secondMap, firstMap))
            .relationships(Collections.singletonList(relationship))
            .pins(Collections.singletonList(pin))
            .viewport(viewport)
            .displaySettings(settings)
            .unknownXml(Arrays.asList(
                unknownAttribute(UnknownXml.Owner.WORKSPACE, "root-attribute", "root"),
                unknownElement(UnknownXml.Owner.WORKSPACE, 2, "root-element", "root"),
                unknownAttribute(UnknownXml.Owner.MAPS, "maps-attribute", "maps"),
                unknownElement(UnknownXml.Owner.MAPS, 1, "maps-element", "maps"),
                unknownAttribute(UnknownXml.Owner.RELATIONSHIPS, "relationships-attribute", "relationships"),
                unknownElement(UnknownXml.Owner.RELATIONSHIPS, 1, "relationships-element", "relationships"),
                unknownAttribute(UnknownXml.Owner.PINS, "pins-attribute", "pins"),
                unknownElement(UnknownXml.Owner.PINS, 1, "pins-element", "pins")))
            .build();
    }

    private static UnknownXml unknownAttribute(UnknownXml.Owner owner, String name, String value) {
        return UnknownXml.attribute(owner, future(name), value + "-value");
    }

    private static UnknownXml unknownElement(UnknownXml.Owner owner, int position, String name, String value) {
        return UnknownXml.element(owner, position, future(name), attributes(future("attribute"), value + "-value"),
            Arrays.asList(
                UnknownXml.Content.text(value + "-text"),
                UnknownXml.Content.element(future("child"), attributes(future("child-attribute"), "child-value"),
                    Collections.singletonList(UnknownXml.Content.text("child-text")))));
    }

    private static QName future(String name) {
        return new QName(FUTURE_NAMESPACE, name, "future");
    }

    private static Map<QName, String> attributes(QName name, String value) {
        Map<QName, String> attributes = new LinkedHashMap<QName, String>();
        attributes.put(name, value);
        return attributes;
    }
}
