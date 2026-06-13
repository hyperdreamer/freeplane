package org.freeplane.plugin.script.doclet;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class FreeplaneApiMapDocletGenerationTest {
    @Test
    public void doclet_generatesApiGroupsPackagesAndCloneBasedTypeProvenance() throws Exception {
        File sourceRoot = Files.createTempDirectory("freeplane-api-map-doclet-src").toFile();
        File outputFile = new File(Files.createTempDirectory("freeplane-api-map-doclet-out").toFile(), "freeplane-api.mm");
        writeSampleSources(sourceRoot);

        Process process = new ProcessBuilder(buildCommand(sourceRoot, outputFile))
            .redirectErrorStream(true)
            .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();

        assertThat(exitCode)
            .withFailMessage(output)
            .isZero();
        String xml = new String(Files.readAllBytes(outputFile.toPath()), StandardCharsets.UTF_8);
        Document document = parseXml(xml);
        Element rootNode = document.getDocumentElement();
        assertThat(xml).startsWith("<map version=\"freeplane 1.9.8\">");
        assertThat(xml).doesNotStartWith("<?xml");
        assertThat(xml).contains("TEXT=\"Freeplane scripting API\"");
        assertThat(xml).contains("ID=\"ID_1\"");
        assertThat(xml).contains("LINK=\"index.html\"");
        assertThat(xml).contains("<hook NAME=\"MapStyle\">");
        assertThat(xml.indexOf("<hook NAME=\"MapStyle\">"))
            .isLessThan(xml.indexOf("TEXT=\"How to use this map\""));
        assertThat(xml).contains("TEXT=\"How to use this map\"");
        assertThat(xml).contains("TEXT=\"Packages\"");
        assertThat(xml).contains("POSITION=\"left\"");
        assertThat(xml).contains("TEXT=\"API groups\"");
        assertThat(xml).contains("POSITION=\"right\"");
        assertThat(xml).contains("TEXT=\"text: String [read-write]\"");
        assertThat(xml).contains("TEXT=\"contains(text: String): boolean [read]\"");
        assertThat(xml).contains("TEXT=\"addChild(text: String): void [write]\"");
        assertThat(xml).contains("TEXT=\"Getter available on\"");
        assertThat(xml).contains("TEXT=\"Setter available on\"");
        assertThat(xml).contains("TEXT=\"Available on\"");
        assertThat(xml).doesNotContain("TEXT=\"Parameters\"");
        assertThat(xml).doesNotContain("TEXT=\"Returns\"");
        assertThat(xml).contains("TEXT=\"Proxy.NodeRO [interface]\"");
        assertThat(xml).contains("TEXT=\"NodeRO [interface]\"");
        assertThat(xml).contains("TEXT=\"SampleUtility [class]\"");
        Element howToUseNode = findNodeByTextPrefix(rootNode, "How to use this map");
        assertThat(howToUseNode).isNotNull();
        assertThat(immediateChildTexts(howToUseNode)).containsExactly(readGuideText());
        assertThat(xml).contains("CONTENT_ID=\"");
        assertThat(xml).doesNotContain("TEXT=\"contains(text: String): boolean [read-write]\"");
        assertThat(xml).doesNotContain("TEXT=\"Concepts\"");

        Element apiConvertibleGroup = findNodeByTextPrefix(rootNode,
            "Convertible (org.freeplane.api) — API convertible summary.");
        assertThat(apiConvertibleGroup).isNotNull();
        assertThat(immediateChildTexts(apiConvertibleGroup)).contains("Type", "Properties", "Methods");
        assertThat(descendantTexts(apiConvertibleGroup))
            .doesNotContain("Getter available on", "Setter available on", "Available on");

        Element proxyConvertibleGroup = findNodeByTextPrefix(rootNode,
            "Convertible (org.freeplane.plugin.script.proxy) — Proxy convertible summary.");
        assertThat(proxyConvertibleGroup).isNotNull();
        assertThat(immediateChildTexts(proxyConvertibleGroup)).contains("Type", "Properties", "Methods");

        Element nodeGroup = findNodeByTextPrefix(rootNode, "Node — Node summary.");
        assertThat(nodeGroup).isNotNull();
        assertThat(immediateChildTexts(nodeGroup)).contains("Types", "Properties", "Methods");
    }

    private List<String> buildCommand(File sourceRoot, File outputFile) {
        List<String> command = new ArrayList<String>();
        command.add(findJavadocExecutable().getAbsolutePath());
        command.add("-quiet");
        command.add("-doclet");
        command.add(FreeplaneApiMapDoclet.class.getName());
        command.add("-docletpath");
        command.add(docletClasspath());
        command.add("-freeplaneApiMapOutput");
        command.add(outputFile.getAbsolutePath());
        command.add("-sourcepath");
        command.add(sourceRoot.getAbsolutePath());
        command.add(new File(sourceRoot, "org/freeplane/plugin/script/proxy/Proxy.java").getAbsolutePath());
        command.add(new File(sourceRoot, "org/freeplane/plugin/script/proxy/Convertible.java").getAbsolutePath());
        command.add(new File(sourceRoot, "org/freeplane/api/NodeRO.java").getAbsolutePath());
        command.add(new File(sourceRoot, "org/freeplane/api/Node.java").getAbsolutePath());
        command.add(new File(sourceRoot, "org/freeplane/api/Convertible.java").getAbsolutePath());
        command.add(new File(sourceRoot, "org/freeplane/plugin/script/SampleUtility.java").getAbsolutePath());
        return command;
    }

    private void writeSampleSources(File sourceRoot) throws Exception {
        writeSource(sourceRoot, "org/freeplane/plugin/script/proxy/Proxy.java",
            "package org.freeplane.plugin.script.proxy;\n"
                + "/** Proxy summary. */\n"
                + "public interface Proxy {\n"
                + "    /** Node summary. */\n"
                + "    interface NodeRO extends org.freeplane.api.NodeRO {\n"
                + "        /** Text summary. */\n"
                + "        String getText();\n"
                + "        /** Query helper. */\n"
                + "        boolean contains(String text);\n"
                + "    }\n"
                + "    /** Writable node summary. */\n"
                + "    interface Node extends NodeRO, org.freeplane.api.Node {\n"
                + "        /** Sets text.\n"
                + "         * @param text new text\n"
                + "         */\n"
                + "        void setText(String text);\n"
                + "        /** Adds a child.\n"
                + "         * @param text child text\n"
                + "         */\n"
                + "        void addChild(String text);\n"
                + "    }\n"
                + "}\n");
        writeSource(sourceRoot, "org/freeplane/plugin/script/proxy/Convertible.java",
            "package org.freeplane.plugin.script.proxy;\n"
                + "/** Proxy convertible summary. */\n"
                + "public class Convertible implements org.freeplane.api.Convertible {\n"
                + "    /** No conversion. */\n"
                + "    public String getText() {\n"
                + "        return \"\";\n"
                + "    }\n"
                + "    /** Allow chained conversion. */\n"
                + "    public Convertible getTo() {\n"
                + "        return this;\n"
                + "    }\n"
                + "    /** For implicit conversion to boolean. */\n"
                + "    public boolean asBoolean() {\n"
                + "        return false;\n"
                + "    }\n"
                + "}\n");
        writeSource(sourceRoot, "org/freeplane/api/NodeRO.java",
            "package org.freeplane.api;\n"
                + "import java.util.List;\n"
                + "/** API node summary. */\n"
                + "public interface NodeRO {\n"
                + "    /** Reads text.\n"
                + "     * <pre>{@code\n"
                + "     * println node.text\n"
                + "     * }</pre>\n"
                + "     * @return node text\n"
                + "     */\n"
                + "    String getText();\n"
                + "    /** Checks for text.\n"
                + "     * @param text candidate text\n"
                + "     * @return true if it matches\n"
                + "     */\n"
                + "    boolean contains(String text);\n"
                + "    /** Reads tags.\n"
                + "     * @return tag names\n"
                + "     */\n"
                + "    List<String> getTags();\n"
                + "}\n");
        writeSource(sourceRoot, "org/freeplane/api/Node.java",
            "package org.freeplane.api;\n"
                + "/** Writable API node summary. */\n"
                + "public interface Node extends NodeRO {\n"
                + "    /** Sets text.\n"
                + "     * @param text replacement text\n"
                + "     */\n"
                + "    void setText(String text);\n"
                + "    /** Adds a child.\n"
                + "     * @param text child text\n"
                + "     */\n"
                + "    void addChild(String text);\n"
                + "}\n");
        writeSource(sourceRoot, "org/freeplane/api/Convertible.java",
            "package org.freeplane.api;\n"
                + "/** API convertible summary. */\n"
                + "public interface Convertible {\n"
                + "    /** No conversion. */\n"
                + "    String getText();\n"
                + "    /** For implicit conversion to boolean. */\n"
                + "    boolean asBoolean();\n"
                + "}\n");
        writeSource(sourceRoot, "org/freeplane/plugin/script/SampleUtility.java",
            "package org.freeplane.plugin.script;\n"
                + "/** Utility summary. */\n"
                + "public class SampleUtility {\n"
                + "    /** Formats a value.\n"
                + "     * @param value input value\n"
                + "     * @return formatted value\n"
                + "     */\n"
                + "    public static String format(String value) {\n"
                + "        return value;\n"
                + "    }\n"
                + "}\n");
    }

    private void writeSource(File sourceRoot, String relativePath, String content) throws Exception {
        File sourceFile = new File(sourceRoot, relativePath);
        sourceFile.getParentFile().mkdirs();
        Files.write(sourceFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private File findJavadocExecutable() {
        return new File(System.getProperty("java.home"), "bin/javadoc");
    }

    private String docletClasspath() {
        File classesDir = new File(FreeplaneApiMapDoclet.class.getProtectionDomain().getCodeSource().getLocation().getPath())
            .getAbsoluteFile();
        File buildDir = classesDir.getParentFile().getParentFile().getParentFile();
        File resourcesDir = new File(buildDir, "resources/doclet");
        if (resourcesDir.isDirectory()) {
            return classesDir.getAbsolutePath() + File.pathSeparator + resourcesDir.getAbsolutePath();
        }
        return classesDir.getAbsolutePath();
    }

    private String readGuideText() {
        return DocletResourceLoader.readUtf8Resource(
            "/org/freeplane/plugin/script/doclet/api-map-how-to-use.txt");
    }

    private Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private Element findNodeByTextPrefix(Element element, String textPrefix) {
        if ("node".equals(element.getTagName()) && element.hasAttribute("TEXT")
            && element.getAttribute("TEXT").startsWith(textPrefix)) {
            return element;
        }
        NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index += 1) {
            Node child = children.item(index);
            if (child instanceof Element) {
                Element match = findNodeByTextPrefix((Element) child, textPrefix);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private List<String> immediateChildTexts(Element element) {
        List<String> texts = new ArrayList<String>();
        NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index += 1) {
            Node child = children.item(index);
            if (child instanceof Element && "node".equals(((Element) child).getTagName())) {
                Element childElement = (Element) child;
                if (childElement.hasAttribute("TEXT")) {
                    texts.add(childElement.getAttribute("TEXT"));
                }
            }
        }
        return texts;
    }

    private List<String> descendantTexts(Element element) {
        List<String> texts = new ArrayList<String>();
        collectDescendantTexts(element, texts);
        return texts;
    }

    private void collectDescendantTexts(Element element, List<String> texts) {
        NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index += 1) {
            Node child = children.item(index);
            if (child instanceof Element) {
                Element childElement = (Element) child;
                if ("node".equals(childElement.getTagName()) && childElement.hasAttribute("TEXT")) {
                    texts.add(childElement.getAttribute("TEXT"));
                }
                collectDescendantTexts(childElement, texts);
            }
        }
    }
}
