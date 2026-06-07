package org.freeplane.plugin.script.doclet;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

public class FreeplaneMindMapWriterTest {
    @Test
    public void write_producesDeterministicFreeplaneXml() throws Exception {
        ApiMapNode root = new ApiMapNode("root", "IGNORED GENERATED ROOT", "ignored.html", false);
        ApiMapNode howToUse = new ApiMapNode("section:how", "How to use this map", false);
        howToUse.addChild(new ApiMapNode("section:how:guide", "Guide line 1.\nGuide line 2.", false));
        ApiMapNode exactType = new ApiMapNode("exact:type:node-ro", "NodeRO [interface]", false);
        ApiMapNode exactTypeClone = ApiMapNode.contentClone("clone:type:node-ro", "exact:type:node-ro", false);
        root.addChild(howToUse);
        root.addChild(exactType);
        root.addChild(exactTypeClone);

        File firstOutput = Files.createTempFile("freeplane-api-map-first", ".mm").toFile();
        File secondOutput = Files.createTempFile("freeplane-api-map-second", ".mm").toFile();
        FreeplaneMindMapWriter writer = new FreeplaneMindMapWriter();

        writer.write(root, firstOutput);
        writer.write(root, secondOutput);

        String firstXml = new String(Files.readAllBytes(firstOutput.toPath()), StandardCharsets.UTF_8);
        String secondXml = new String(Files.readAllBytes(secondOutput.toPath()), StandardCharsets.UTF_8);
        assertThat(firstXml).isEqualTo(secondXml);
        assertThat(firstXml).startsWith("<map version=\"freeplane 1.9.8\">");
        assertThat(firstXml).doesNotStartWith("<?xml");
        assertThat(firstXml).contains("TEXT=\"Freeplane scripting API\"");
        assertThat(firstXml).contains("ID=\"ID_TEMPLATE_ROOT\"");
        assertThat(firstXml).contains("LINK=\"index.html\"");
        assertThat(firstXml).contains("STYLE=\"oval\"");
        assertThat(firstXml).contains("<font SIZE=\"18\"/>");
        assertThat(firstXml).contains("<hook NAME=\"MapStyle\">");
        assertThat(firstXml.indexOf("<hook NAME=\"MapStyle\">"))
            .isLessThan(firstXml.indexOf("TEXT=\"How to use this map\""));
        assertThat(firstXml).doesNotContain("IGNORED GENERATED ROOT");
        assertThat(firstXml).doesNotContain("ignored.html");
        assertThat(firstXml).contains("TEXT=\"How to use this map\"");
        assertThat(firstXml).contains("Guide line 1.");
        assertThat(firstXml).contains("Guide line 2.");
        assertThat(firstXml).contains("TEXT=\"NodeRO [interface]\"");
        assertThat(firstXml).contains("CONTENT_ID=\"" + NodeIdFactory.createId("exact:type:node-ro") + "\"");
    }
}
