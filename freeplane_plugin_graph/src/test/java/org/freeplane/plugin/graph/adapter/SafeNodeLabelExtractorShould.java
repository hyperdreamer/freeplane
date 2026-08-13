package org.freeplane.plugin.graph.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Component;
import java.lang.reflect.Field;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.Icon;

import org.freeplane.api.LengthUnit;
import org.freeplane.api.Quantity;
import org.freeplane.core.util.Compat;
import org.freeplane.core.util.Hyperlink;
import org.freeplane.features.icon.IconDescription;
import org.freeplane.features.icon.NamedIcon;
import org.freeplane.features.map.EncryptionModel;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.features.map.mindmapmode.MMapController;
import org.freeplane.features.format.FormattedFormula;
import org.freeplane.features.format.IFormattedObject;
import org.freeplane.features.link.NodeLinks;
import org.freeplane.features.nodestyle.NodeStyleModel;
import org.freeplane.features.text.AbstractContentTransformer;
import org.freeplane.features.text.IContentTransformer.Mode;
import org.freeplane.features.text.TextController;
import org.freeplane.features.text.TransformationException;
import org.freeplane.features.url.MapVersionInterpreter;
import org.freeplane.features.url.mindmapmode.MapLoader;
import org.freeplane.main.application.ApplicationResourceController;
import org.freeplane.main.application.CommandLineParser;
import org.freeplane.main.headlessmode.FreeplaneHeadlessStarter;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class SafeNodeLabelExtractorShould {
    private static final String HIDDEN_LOCKED_SENTINEL = "HIDDEN_LOCKED_SENTINEL";
    private static final String ID_HIDDEN = "ID_HIDDEN";

    private static FixtureScope fixture;
    private final SafeNodeLabelExtractor extractor = new SafeNodeLabelExtractor();

    @BeforeClass
    public static void loadFixture() throws Exception {
        fixture = new FixtureScope();
    }

    @AfterClass
    public static void closeFixture() throws Exception {
        if (fixture != null) {
            fixture.close();
            fixture = null;
        }
    }

    @Test
    public void extractsDirectPlainAndHtmlTextFromTheRealFixture() throws Exception {
        assertFixtureRepresentations(fixture);

        assertThat(extractor.extract(fixture.node("ID_PLAIN")))
            .isEqualTo(SafeNodeLabel.of("Plain label", "Plain label"));
        assertThat(extractor.extract(fixture.node("ID_HTML")))
            .isEqualTo(SafeNodeLabel.of("HTML label second line", "HTML label second line"));
    }

    @Test
    public void keepsFormulaLatexAndMarkdownAsNormalizedUnevaluatedSource() throws Exception {
        assertFixtureRepresentations(fixture);

        assertThat(extractor.extract(fixture.node("ID_FORMULA")).fullText())
            .isEqualTo("=node['ID_HIDDEN'].text");
        assertThat(extractor.extract(fixture.node("ID_LATEX_PREFIX")).fullText()).isEqualTo("$x_2 = 3$");
        assertThat(extractor.extract(fixture.node("ID_LATEX_FORMAT")).fullText()).isEqualTo("A_{m,n} = B");
        assertThat(extractor.extract(fixture.node("ID_MARKDOWN")).fullText())
            .isEqualTo("**Markdown** [hidden](#ID_HIDDEN)");

        NodeModel unparsedLatex = new NodeModel("\\unparsedlatex \t source", null);
        assertThat(extractor.extract(unparsedLatex).fullText()).isEqualTo("source");

        NodeModel formattedFormula = new NodeModel(new FormattedFormula("=1 + 1", "ignored"), null);
        assertThat(extractor.extract(formattedFormula).fullText()).isEqualTo("=1 + 1");

        NodeModel htmlLookingFormula = new NodeModel(
            new FormattedFormula("<html><body>literal formula source</body></html>", "ignored"), null);
        assertThat(extractor.extract(htmlLookingFormula).fullText())
            .isEqualTo("<html><body>literal formula source</body></html>");

        NodeModel formattedUri = new NodeModel(new IFormattedObject() {
            @Override
            public String getPattern() {
                return "literal";
            }

            @Override
            public Object getObject() {
                return URI.create("file:/private/formatted.pdf");
            }
        }, null);
        assertThat(extractor.extract(formattedUri).fullText()).isEqualTo("file:/private/formatted.pdf");
    }

    @Test
    public void usesLiteralLinkWithoutDereferencingItsHiddenTarget() throws Exception {
        assertFixtureRepresentations(fixture);
        SafeNodeLabel localLabel = extractor.extract(fixture.node("ID_LOCAL_LINK"));
        assertThat(localLabel).isEqualTo(SafeNodeLabel.of("#ID_HIDDEN", "#ID_HIDDEN"));
        assertThat(fixture.node(ID_HIDDEN).getUserObject()).isEqualTo(HIDDEN_LOCKED_SENTINEL);

        MapModel map = mapWithRoot();
        NodeModel source = new NodeModel("", map);
        map.getRootNode().insert(source);
        NodeLinks.createLinkExtension(source).setLocalHyperlink(source, ID_HIDDEN);
        NodeModel detachedLockedTarget = new NodeModel(HIDDEN_LOCKED_SENTINEL, map);
        detachedLockedTarget.setID(ID_HIDDEN);
        detachedLockedTarget.addExtension(new EncryptionModel(detachedLockedTarget, "t/NS/HPSppU= VbQIDGWIdFE="));

        assertThat(extractor.extract(source)).isEqualTo(SafeNodeLabel.of("#ID_HIDDEN", "#ID_HIDDEN"));

        NodeModel rawHyperlink = new NodeModel(new Hyperlink("literal:#not-a-target", URI.create("file:/ignored")), null);
        assertThat(extractor.extract(rawHyperlink).fullText()).isEqualTo("literal:#not-a-target");
        NodeModel rawUri = new NodeModel(URI.create("file:/private/raw.pdf"), null);
        assertThat(extractor.extract(rawUri).fullText()).isEqualTo("file:/private/raw.pdf");
    }

    @Test
    public void fallsBackToDirectIconAndAttachmentDescriptions() throws Exception {
        assertFixtureRepresentations(fixture);
        assertThat(extractor.extract(fixture.node("ID_ICON")))
            .isEqualTo(SafeNodeLabel.of("Idea", "Idea"));
        assertThat(extractor.extract(fixture.node("ID_ATTACHMENT")))
            .isEqualTo(SafeNodeLabel.of("file:/private/report.pdf", "file:/private/report.pdf"));

        TestIcon descriptionlessIcon = new TestIcon("fallback-name", "");
        NodeModel iconOnly = new NodeModel("", null);
        iconOnly.addIcon(descriptionlessIcon);
        assertThat(extractor.extract(iconOnly)).isEqualTo(SafeNodeLabel.of("fallback-name", "fallback-name"));
        assertThat(descriptionlessIcon.iconCalls).isZero();

        NodeModel empty = new NodeModel("", null);
        assertThat(extractor.extract(empty)).isEqualTo(SafeNodeLabel.of("Node", "Node"));
    }

    @Test
    public void collapsesWhitespaceAndSplitsFullFromCodePointBoundedDisplayText() throws Exception {
        assertFixtureRepresentations(fixture);
        assertThat(extractor.extract(fixture.node("ID_WHITESPACE")))
            .isEqualTo(SafeNodeLabel.of("first second third", "first second third"));

        String fullText = repeatedEmoji(78) + "TAIL";
        SafeNodeLabel label = extractor.extract(new NodeModel(fullText, null));
        String expectedDisplay = fullText.substring(0, fullText.offsetByCodePoints(0, 77)) + "...";

        assertThat(label.fullText()).isEqualTo(fullText);
        assertThat(label.displayText()).isEqualTo(expectedDisplay);
        assertThat(label.displayText().codePointCount(0, label.displayText().length())).isEqualTo(80);
        assertThat(hasUnpairedSurrogate(label.displayText())).isFalse();
    }

    @Test
    public void doesNotEnterTransformersForAFormulaReferencingALockedSentinel() throws Exception {
        assertFixtureRepresentations(fixture);
        HostileTransformer hostileTransformer = new HostileTransformer();
        TextController textController = TextController.getController(fixture.modeController);
        textController.addTextTransformer(hostileTransformer);
        try {
            SafeNodeLabel label = extractor.extract(fixture.node("ID_FORMULA"));
            assertThat(hostileTransformer.invocations).hasValue(0);
            assertThat(label.fullText()).isEqualTo("=node['ID_HIDDEN'].text");
            assertThat(label.fullText()).doesNotContain(HIDDEN_LOCKED_SENTINEL);
            assertThat(label.displayText()).doesNotContain(HIDDEN_LOCKED_SENTINEL);
        }
        finally {
            textController.removeTextTransformer(hostileTransformer);
        }
    }

    @Test
    public void doesNotEnterTransformersForALocalLinkReferencingALockedSentinel() throws Exception {
        assertFixtureRepresentations(fixture);
        HostileTransformer hostileTransformer = new HostileTransformer();
        TextController textController = TextController.getController(fixture.modeController);
        textController.addTextTransformer(hostileTransformer);
        try {
            SafeNodeLabel label = extractor.extract(fixture.node("ID_LOCAL_LINK"));
            assertThat(hostileTransformer.invocations).hasValue(0);
            assertThat(label.fullText()).isEqualTo("#ID_HIDDEN");
            assertThat(label.fullText()).doesNotContain(HIDDEN_LOCKED_SENTINEL);
            assertThat(label.displayText()).doesNotContain(HIDDEN_LOCKED_SENTINEL);
        }
        finally {
            textController.removeTextTransformer(hostileTransformer);
        }
    }

    @Test
    public void doesNotAssignAnIdToANumberedIdlessNode() throws Exception {
        CountingMapModel map = new CountingMapModel();
        NodeModel root = new NodeModel("root", map);
        map.setRoot(root);
        NodeModel idless = new NodeModel("idless numbered node", map);
        root.insert(idless);
        NodeStyleModel.setNodeNumbering(idless, Boolean.TRUE);

        assertThat(idless.getID()).isNull();
        assertThat(map.registryCalls).isZero();
        assertThat(extractor.extract(idless)).isEqualTo(SafeNodeLabel.of("idless numbered node", "idless numbered node"));
        assertThat(idless.getID()).isNull();
        assertThat(map.registryCalls).isZero();
    }

    @Test
    public void productionSourceContainsNoTransformationResolutionOrIdentityCalls() throws Exception {
        Path source = Paths.get("src/main/java/org/freeplane/plugin/graph/adapter/SafeNodeLabelExtractor.java");
        assertThat(Files.isRegularFile(source)).isTrue();
        String productionSource = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        String[] forbiddenTokens = {
            "TextController", "getTransformed", "getPlainTransformedText", "getNodeForID", "getNodeFromID_",
            "createID", "getValidLink", "getChildren", "getParent", "toUrl", "Files.", "Paths."
        };

        for (String forbiddenToken : forbiddenTokens) {
            assertThat(productionSource).doesNotContain(forbiddenToken);
        }
    }

    private void assertFixtureRepresentations(FixtureScope fixture) {
        assertThat(fixture.node("ID_PLAIN").getUserObject()).isEqualTo("Plain label");
        assertThat(fixture.node("ID_HTML").getUserObject()).isEqualTo("<html>\n"
            + "  <head>\n"
            + "    \n"
            + "  </head>\n"
            + "  <body>\n"
            + "    <p>\n"
            + "      HTML <b>label</b>\n"
            + "    </p>\n"
            + "    <p>\n"
            + "      second line\n"
            + "    </p>\n"
            + "  </body>\n"
            + "</html>\n");
        assertThat(fixture.node("ID_FORMULA").getUserObject()).isEqualTo("=node['ID_HIDDEN'].text");
        assertThat(fixture.node("ID_LATEX_PREFIX").getUserObject()).isEqualTo("\\latex   $x_2 = 3$");
        assertThat(NodeStyleModel.getNodeFormat(fixture.node("ID_LATEX_FORMAT"))).isEqualTo("latexPatternFormat");
        assertThat(NodeStyleModel.getNodeFormat(fixture.node("ID_MARKDOWN"))).isEqualTo("markdownPatternFormat");
        assertThat(NodeLinks.getLink(fixture.node("ID_LOCAL_LINK")).toString()).isEqualTo("#ID_HIDDEN");
        assertThat(NodeLinks.getLink(fixture.node("ID_ATTACHMENT")).toString())
            .isEqualTo("file:/private/report.pdf");
        assertThat(fixture.node("ID_ICON").getIcons()).hasSize(1);
        assertThat(fixture.node("ID_ICON").getIcons().get(0).getName()).isEqualTo("idea");
        assertThat(idlessFixtureNode(fixture).getID()).isNull();
        assertThat(EncryptionModel.getModel(fixture.node("ID_LOCKED"))).isNotNull();
        assertThat(EncryptionModel.getModel(fixture.node("ID_LOCKED")).isLocked()).isTrue();
    }

    private static NodeModel idlessFixtureNode(FixtureScope fixture) {
        List<NodeModel> children = fixture.map.getRootNode().getChildren();
        for (NodeModel child : children) {
            if ("idless numbered node".equals(child.getUserObject())) {
                return child;
            }
        }
        throw new AssertionError("Missing idless fixture node");
    }

    private static MapModel mapWithRoot() {
        MapModel map = new MapModel((source, targetMap, withChildren) -> null, null, null);
        map.setRoot(new NodeModel("root", map));
        return map;
    }

    private static String repeatedEmoji(int count) {
        String emoji = new String(Character.toChars(0x1f600));
        StringBuilder text = new StringBuilder(count * emoji.length());
        for (int index = 0; index < count; index++) {
            text.append(emoji);
        }
        return text.toString();
    }

    private static boolean hasUnpairedSurrogate(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isHighSurrogate(character)) {
                if (index + 1 == value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    return true;
                }
                index++;
            }
            else if (Character.isLowSurrogate(character)) {
                return true;
            }
        }
        return false;
    }

    private static final class HostileTransformer extends AbstractContentTransformer {
        private final AtomicInteger invocations = new AtomicInteger();

        private HostileTransformer() {
            super(-100);
        }

        @Override
        public Object transformContent(NodeModel node, Object nodeProperty, Object content, TextController textController,
                Mode mode, Component component) throws TransformationException {
            invocations.incrementAndGet();
            return HIDDEN_LOCKED_SENTINEL;
        }
    }

    private static final class TestIcon implements NamedIcon, IconDescription {
        private final String name;
        private final String translatedDescription;
        private int iconCalls;

        private TestIcon(String name, String translatedDescription) {
            this.name = name;
            this.translatedDescription = translatedDescription;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getFile() {
            return "";
        }

        @Override
        public Icon getIcon() {
            iconCalls++;
            return null;
        }

        @Override
        public Icon getIcon(Quantity<LengthUnit> iconHeight) {
            iconCalls++;
            return null;
        }

        @Override
        public NamedIcon zoom(float zoom) {
            return this;
        }

        @Override
        public int getOrder() {
            return 0;
        }

        @Override
        public boolean hasStandardSize() {
            return true;
        }

        @Override
        public String getDescriptionTranslationKey() {
            return "";
        }

        @Override
        public String getTranslatedDescription() {
            return translatedDescription;
        }

        @Override
        public String getShortcutKey() {
            return "";
        }
    }

    private static final class CountingMapModel extends MapModel {
        private int registryCalls;

        private CountingMapModel() {
            super((source, targetMap, withChildren) -> null, null, null);
        }

        @Override
        public String registryNode(NodeModel nodeModel) {
            registryCalls++;
            return super.registryNode(nodeModel);
        }
    }

    private static final class FixtureScope implements AutoCloseable {
        private final HeadlessResourceScope resources;
        private final FreeplaneHeadlessStarter starter;
        private final boolean ownsHeadlessStarter;
        private final Controller previousController;
        private final MapVersionInterpreter[] previousMapVersionInterpreters;
        private final ModeController modeController;
        private final MapModel map;
        private final Path mapFile;

        private FixtureScope() throws Exception {
            previousController = Controller.getCurrentController();
            previousMapVersionInterpreters = mapVersionInterpreters();
            final Controller currentController = previousController;
            final ModeController existingModeController = currentController == null ? null
                : currentController.getModeController(MModeController.MODENAME);
            if (existingModeController != null && existingModeController.getMapController() instanceof MMapController) {
                resources = null;
                starter = null;
                ownsHeadlessStarter = false;
                modeController = existingModeController;
            }
            else {
                resources = new HeadlessResourceScope();
                starter = new FreeplaneHeadlessStarter(CommandLineParser.parse());
                Controller controller = starter.createController();
                starter.createModeControllers(controller);
                starter.createFrame();
                ownsHeadlessStarter = true;
                modeController = controller.getModeController(MModeController.MODENAME);
            }
            mapFile = Files.createTempFile("graph-safe-labels", ".mm");
            try (InputStream input = SafeNodeLabelExtractorShould.class.getResourceAsStream("/maps/graph-safe-labels.mm")) {
                if (input == null) {
                    throw new IOException("Missing graph safe labels fixture");
                }
                Files.copy(input, mapFile, StandardCopyOption.REPLACE_EXISTING);
            }
            MapVersionInterpreter.addMapVersionInterpreter(new MapVersionInterpreter("SAFE_LABEL_FIXTURE", 19,
                "freeplane 1.12.0", false, false, "Freeplane", "https://www.freeplane.org", null, null));
            map = new MapLoader(modeController).load(mapFile.toUri().toURL()).getMap();
            if (map == null) {
                throw new IOException("MapLoader did not load graph safe labels fixture");
            }
        }

        private static MapVersionInterpreter[] mapVersionInterpreters() throws ReflectiveOperationException {
            Field values = MapVersionInterpreter.class.getDeclaredField("values");
            values.setAccessible(true);
            return (MapVersionInterpreter[]) values.get(null);
        }

        private static void restoreMapVersionInterpreters(MapVersionInterpreter[] values)
                throws ReflectiveOperationException {
            Field field = MapVersionInterpreter.class.getDeclaredField("values");
            field.setAccessible(true);
            field.set(null, values);
        }

        private static void clearMapIoSingleton() throws ReflectiveOperationException {
            Field instance = Class.forName("org.freeplane.features.mapio.mindmapmode.MMapIO")
                .getDeclaredField("INSTANCE");
            instance.setAccessible(true);
            instance.set(null, null);
        }

        private NodeModel node(String id) {
            NodeModel node = map.getNodeForID(id);
            if (node == null) {
                throw new AssertionError("Missing fixture node " + id);
            }
            return node;
        }

        @Override
        public void close() throws Exception {
            try {
                ((MMapController) modeController.getMapController()).closeWithoutSaving(map);
            }
            finally {
                try {
                    if (ownsHeadlessStarter) {
                        starter.stop();
                        clearMapIoSingleton();
                    }
                }
                finally {
                    try {
                        Files.deleteIfExists(mapFile);
                    }
                    finally {
                        try {
                            if (resources != null) {
                                resources.close();
                            }
                        }
                        finally {
                            try {
                                restoreMapVersionInterpreters(previousMapVersionInterpreters);
                            }
                            finally {
                                Controller.setCurrentController(previousController);
                            }
                        }
                    }
                }
            }
        }
    }

    private static final class HeadlessResourceScope implements AutoCloseable {
        private final String previousGlobalResourceDirectory;
        private final String previousResourceBaseDirectory;
        private final String previousInstallationBaseDirectory;
        private final Path testProperties;
        private final byte[] previousProperties;
        private final Path testVersionProperties;
        private final byte[] previousVersionProperties;
        private final Path testMapVersions;
        private final byte[] previousMapVersions;
        private final Path testPreferences;
        private final byte[] previousPreferences;

        private HeadlessResourceScope() throws Exception {
            URL fixtureUrl = SafeNodeLabelExtractorShould.class.getResource("/maps/graph-safe-labels.mm");
            if (fixtureUrl == null) {
                throw new IOException("Missing graph safe labels fixture");
            }
            Path testResourceDirectory = Paths.get(fixtureUrl.toURI()).getParent().getParent();
            Path projectDirectory = testResourceDirectory.getParent().getParent().getParent().getParent();
            Path viewerResources = projectDirectory.resolve("freeplane/build/resources/viewer");
            Path viewerProperties = viewerResources.resolve("freeplane.properties");
            Path viewerVersionProperties = viewerResources.resolve("version.properties");
            Path externalPreferences = projectDirectory.resolve("freeplane/src/external/resources/xml/preferences.xml");
            Path editorMapVersions = projectDirectory.resolve("freeplane/src/editor/resources/xml/mapVersions.xml");
            if (!Files.isRegularFile(viewerProperties) || !Files.isRegularFile(viewerVersionProperties)
                    || !Files.isRegularFile(externalPreferences) || !Files.isRegularFile(editorMapVersions)) {
                throw new IOException("Missing Freeplane headless test resources");
            }
            testProperties = testResourceDirectory.resolve("freeplane.properties");
            previousProperties = Files.exists(testProperties) ? Files.readAllBytes(testProperties) : null;
            Files.copy(viewerProperties, testProperties, StandardCopyOption.REPLACE_EXISTING);
            testVersionProperties = testResourceDirectory.resolve("version.properties");
            previousVersionProperties = Files.exists(testVersionProperties) ? Files.readAllBytes(testVersionProperties) : null;
            Files.copy(viewerVersionProperties, testVersionProperties, StandardCopyOption.REPLACE_EXISTING);
            testPreferences = testResourceDirectory.resolve("xml/preferences.xml");
            Files.createDirectories(testPreferences.getParent());
            previousPreferences = Files.exists(testPreferences) ? Files.readAllBytes(testPreferences) : null;
            Files.copy(externalPreferences, testPreferences, StandardCopyOption.REPLACE_EXISTING);
            testMapVersions = testResourceDirectory.resolve("xml/mapVersions.xml");
            previousMapVersions = Files.exists(testMapVersions) ? Files.readAllBytes(testMapVersions) : null;
            Files.copy(editorMapVersions, testMapVersions, StandardCopyOption.REPLACE_EXISTING);

            previousGlobalResourceDirectory = System.getProperty("org.freeplane.globalresourcedir");
            System.setProperty("org.freeplane.globalresourcedir", viewerResources.toString());
            previousResourceBaseDirectory = ApplicationResourceController.RESOURCE_BASE_DIRECTORY;
            previousInstallationBaseDirectory = ApplicationResourceController.INSTALLATION_BASE_DIRECTORY;
            ApplicationResourceController.RESOURCE_BASE_DIRECTORY = viewerResources.toString();
            ApplicationResourceController.INSTALLATION_BASE_DIRECTORY = viewerResources.getParent().toString();
            Compat.setIsApplet(false);
        }

        @Override
        public void close() throws IOException {
            if (previousGlobalResourceDirectory == null) {
                System.clearProperty("org.freeplane.globalresourcedir");
            }
            else {
                System.setProperty("org.freeplane.globalresourcedir", previousGlobalResourceDirectory);
            }
            ApplicationResourceController.RESOURCE_BASE_DIRECTORY = previousResourceBaseDirectory;
            ApplicationResourceController.INSTALLATION_BASE_DIRECTORY = previousInstallationBaseDirectory;
            restore(testProperties, previousProperties);
            restore(testVersionProperties, previousVersionProperties);
            restore(testPreferences, previousPreferences);
            restore(testMapVersions, previousMapVersions);
        }

        private static void restore(Path path, byte[] previousContents) throws IOException {
            if (previousContents == null) {
                Files.deleteIfExists(path);
            }
            else {
                Files.write(path, previousContents);
            }
        }
    }
}
