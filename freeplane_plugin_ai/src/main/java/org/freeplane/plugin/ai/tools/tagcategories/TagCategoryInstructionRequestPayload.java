package org.freeplane.plugin.ai.tools.tagcategories;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.freeplane.features.icon.TagCategoryInstruction;
import org.freeplane.features.icon.TagCategoryInstructionRequest;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TagCategoryInstructionRequestPayload {
    @Description("Target map ID.")
    private final String mapIdentifier;
    @Description("Revision from the last getTagCategories call.")
    private final String baseRevision;
    private final List<TagCategoryInstructionPayload> instructions;

    @JsonCreator
    public TagCategoryInstructionRequestPayload(@JsonProperty("mapIdentifier") String mapIdentifier,
                                                @JsonProperty("baseRevision") String baseRevision,
                                                @JsonProperty("instructions") List<TagCategoryInstructionPayload> instructions) {
        this.mapIdentifier = mapIdentifier;
        this.baseRevision = baseRevision;
        this.instructions = instructions;
    }

    public static TagCategoryInstructionRequestPayload fromInstructionRequest(String mapIdentifier,
                                                                              TagCategoryInstructionRequest instructionRequest) {
        ArrayList<TagCategoryInstructionPayload> instructionPayloads = new ArrayList<>();
        for (TagCategoryInstruction instruction : instructionRequest.getInstructions()) {
            instructionPayloads.add(TagCategoryInstructionPayload.fromInstruction(instruction));
        }
        return new TagCategoryInstructionRequestPayload(
            mapIdentifier,
            instructionRequest.getBaseRevision(),
            instructionPayloads);
    }

    public TagCategoryInstructionRequest toInstructionRequest() {
        ArrayList<TagCategoryInstruction> categoryInstructions = new ArrayList<>();
        if (instructions != null) {
            for (TagCategoryInstructionPayload instruction : instructions) {
                categoryInstructions.add(instruction.toInstruction());
            }
        }
        return new TagCategoryInstructionRequest(baseRevision, categoryInstructions);
    }

    public String getMapIdentifier() {
        return mapIdentifier;
    }

    public String getBaseRevision() {
        return baseRevision;
    }

    public List<TagCategoryInstructionPayload> getInstructions() {
        return instructions;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mapIdentifier, baseRevision, instructions);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TagCategoryInstructionRequestPayload)) {
            return false;
        }
        TagCategoryInstructionRequestPayload other = (TagCategoryInstructionRequestPayload) obj;
        return Objects.equals(mapIdentifier, other.mapIdentifier)
            && Objects.equals(baseRevision, other.baseRevision)
            && Objects.equals(instructions, other.instructions);
    }
}
