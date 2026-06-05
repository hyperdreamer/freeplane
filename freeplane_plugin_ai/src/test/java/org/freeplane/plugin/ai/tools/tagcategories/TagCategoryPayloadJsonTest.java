package org.freeplane.plugin.ai.tools.tagcategories;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Collections;
import org.freeplane.features.icon.TagCategoryInstruction;
import org.freeplane.features.icon.TagCategoryInstructionRequest;
import org.freeplane.features.icon.TagCategoryInstructionType;
import org.freeplane.features.icon.TagCategoryNode;
import org.freeplane.features.icon.TagCategoryState;
import org.freeplane.features.icon.TagItem;
import org.freeplane.features.icon.TagTargetLocation;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TagCategoryPayloadJsonTest {
    @Test
    public void categoryStatePayloadRoundtripIsLossless() throws Exception {
        TagCategoryState categoryState = new TagCategoryState(
            "sha256:abc",
            "::",
            Collections.singletonList(new TagCategoryNode(
                Collections.singletonList("Project"),
                "Project",
                "Project",
                "#01020304",
                Collections.singletonList(new TagCategoryNode(
                    Arrays.asList("Project", "Status"),
                    "Status",
                    "Project::Status",
                    "#05060708",
                    Collections.emptyList())))),
            Collections.singletonList(new TagItem(
                Collections.singletonList("urgent"),
                "urgent",
                "urgent",
                "#ff0000ff")));

        TagCategoryStatePayload payload = TagCategoryStatePayload.fromState("map-1", categoryState);
        ObjectMapper uut = new ObjectMapper();

        String json = uut.writeValueAsString(payload);
        TagCategoryStatePayload restoredPayload = uut.readValue(json, TagCategoryStatePayload.class);

        assertThat(restoredPayload).isEqualTo(payload);
        assertThat(restoredPayload.toState()).isEqualTo(categoryState);
    }

    @Test
    public void instructionRequestPayloadRoundtripIsLossless() throws Exception {
        TagCategoryInstructionRequest instructionRequest = new TagCategoryInstructionRequest(
            "sha256:abc",
            Arrays.asList(
                TagCategoryInstruction.addTag(Collections.singletonList("urgent"), TagTargetLocation.UNCATEGORIZED),
                TagCategoryInstruction.renameTag(Arrays.asList("Project", "Status"), "State"),
                TagCategoryInstruction.setCategorySeparator("/")));
        TagCategoryInstructionRequestPayload payload = TagCategoryInstructionRequestPayload.fromInstructionRequest(
            "map-1",
            instructionRequest);
        ObjectMapper uut = new ObjectMapper();

        String json = uut.writeValueAsString(payload);
        TagCategoryInstructionRequestPayload restoredPayload = uut.readValue(json, TagCategoryInstructionRequestPayload.class);

        assertThat(restoredPayload).isEqualTo(payload);
        assertThat(restoredPayload.toInstructionRequest()).isEqualTo(instructionRequest);
    }
}
