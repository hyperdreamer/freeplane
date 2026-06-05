package org.freeplane.plugin.ai.tools.tagcategories;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;
import java.util.List;
import java.util.Objects;
import org.freeplane.features.icon.TagCategoryInstruction;
import org.freeplane.features.icon.TagCategoryInstructionType;
import org.freeplane.features.icon.TagTargetLocation;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TagCategoryInstructionPayload {
    @Description("Operation kind. ADD_TAG creates a tag, MOVE_TAG changes its placement, RENAME_TAG changes its "
        + "name, DELETE_TAG removes it.")
    private final TagCategoryInstructionType type;
    @JsonProperty(required = false)
    @Description("Target path. For ADD_TAG with targetLocation CATEGORIZED, this is the full target path to create "
        + "and any missing parent categorized tags are created automatically. For ADD_TAG with targetLocation "
        + "UNCATEGORIZED, this must contain exactly one segment. Examples: [\"context\"], [\"context\", "
        + "\"work\"], [\"urgent\"].")
    private final List<String> path;
    @JsonProperty(required = false)
    @Description("Replacement name for RENAME_TAG only.")
    private final String newName;
    @JsonProperty(required = false)
    @Description("Target parent path for MOVE_TAG only when targetLocation is CATEGORIZED. Use [] to move to the "
        + "top level. Missing parent categorized tags on this path are created automatically. Omit this field for "
        + "UNCATEGORIZED moves.")
    private final List<String> newParentPath;
    @JsonProperty(required = false)
    @Description("Target placement for ADD_TAG and MOVE_TAG: CATEGORIZED or UNCATEGORIZED.")
    private final TagTargetLocation targetLocation;
    @JsonProperty(required = false)
    @Description("Optional insertion index for MOVE_TAG.")
    private final Integer index;
    @JsonProperty(required = false)
    @Description("Optional color for ADD_TAG and SET_COLOR operations.")
    private final String color;
    @JsonProperty(required = false)
    @Description("Replacement category separator for SET_CATEGORY_SEPARATOR only.")
    private final String newSeparator;

    @JsonCreator
    public TagCategoryInstructionPayload(@JsonProperty("type") TagCategoryInstructionType type,
                                         @JsonProperty(value = "path", required = false) List<String> path,
                                         @JsonProperty(value = "newName", required = false) String newName,
                                         @JsonProperty(value = "newParentPath", required = false)
                                         List<String> newParentPath,
                                         @JsonProperty(value = "targetLocation", required = false)
                                         TagTargetLocation targetLocation,
                                         @JsonProperty(value = "index", required = false) Integer index,
                                         @JsonProperty(value = "color", required = false) String color,
                                         @JsonProperty(value = "newSeparator", required = false)
                                         String newSeparator) {
        this.type = type;
        this.path = path;
        this.newName = newName;
        this.newParentPath = newParentPath;
        this.targetLocation = targetLocation;
        this.index = index;
        this.color = color;
        this.newSeparator = newSeparator;
    }

    static TagCategoryInstructionPayload fromInstruction(TagCategoryInstruction instruction) {
        return new TagCategoryInstructionPayload(
            instruction.getType(),
            instruction.getPath(),
            instruction.getNewName(),
            instruction.getNewParentPath(),
            instruction.getTargetLocation(),
            instruction.getIndex(),
            instruction.getColor(),
            instruction.getNewSeparator());
    }

    TagCategoryInstruction toInstruction() {
        return new TagCategoryInstruction(type, path, newName, newParentPath, targetLocation, index, color,
            newSeparator);
    }

    public TagCategoryInstructionType getType() {
        return type;
    }

    public List<String> getPath() {
        return path;
    }

    public String getNewName() {
        return newName;
    }

    public List<String> getNewParentPath() {
        return newParentPath;
    }

    public TagTargetLocation getTargetLocation() {
        return targetLocation;
    }

    public Integer getIndex() {
        return index;
    }

    public String getColor() {
        return color;
    }

    public String getNewSeparator() {
        return newSeparator;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, path, newName, newParentPath, targetLocation, index, color, newSeparator);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TagCategoryInstructionPayload)) {
            return false;
        }
        TagCategoryInstructionPayload other = (TagCategoryInstructionPayload) obj;
        return type == other.type
            && Objects.equals(path, other.path)
            && Objects.equals(newName, other.newName)
            && Objects.equals(newParentPath, other.newParentPath)
            && targetLocation == other.targetLocation
            && Objects.equals(index, other.index)
            && Objects.equals(color, other.color)
            && Objects.equals(newSeparator, other.newSeparator);
    }
}
