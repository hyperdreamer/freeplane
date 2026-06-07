package org.freeplane.plugin.ai.tools.tagcategories;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.freeplane.features.icon.TagCategoryNode;
import org.freeplane.features.icon.TagCategoryState;
import org.freeplane.features.icon.TagItem;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TagCategoryStatePayload {
    @Description("Map ID.")
    private final String mapIdentifier;
    @Description("Revision of the returned category structure.")
    private final String revision;
    @Description("Category separator.")
    private final String categorySeparator;
    @Description("Tag category structure.")
    private final List<TagCategoryNodePayload> categories;
    @Description("Tags that are not assigned to any category.")
    private final List<TagItemPayload> uncategorizedTags;

    @JsonCreator
    public TagCategoryStatePayload(@JsonProperty("mapIdentifier") String mapIdentifier,
                                   @JsonProperty("revision") String revision,
                                   @JsonProperty("categorySeparator") String categorySeparator,
                                   @JsonProperty("categories") List<TagCategoryNodePayload> categories,
                                   @JsonProperty("uncategorizedTags") List<TagItemPayload> uncategorizedTags) {
        this.mapIdentifier = mapIdentifier;
        this.revision = revision;
        this.categorySeparator = categorySeparator;
        this.categories = categories;
        this.uncategorizedTags = uncategorizedTags;
    }

    public static TagCategoryStatePayload fromState(String mapIdentifier, TagCategoryState categoryState) {
        ArrayList<TagCategoryNodePayload> categoryPayloads = new ArrayList<>();
        for (TagCategoryNode category : categoryState.getCategories()) {
            categoryPayloads.add(TagCategoryNodePayload.fromNode(category));
        }
        ArrayList<TagItemPayload> uncategorizedPayloads = new ArrayList<>();
        for (TagItem uncategorizedTag : categoryState.getUncategorizedTags()) {
            uncategorizedPayloads.add(TagItemPayload.fromItem(uncategorizedTag));
        }
        return new TagCategoryStatePayload(
            mapIdentifier,
            categoryState.getRevision(),
            categoryState.getCategorySeparator(),
            categoryPayloads,
            uncategorizedPayloads);
    }

    public TagCategoryState toState() {
        ArrayList<TagCategoryNode> categoryNodes = new ArrayList<>();
        if (categories != null) {
            for (TagCategoryNodePayload category : categories) {
                categoryNodes.add(category.toNode());
            }
        }
        ArrayList<TagItem> uncategorizedItems = new ArrayList<>();
        if (uncategorizedTags != null) {
            for (TagItemPayload uncategorizedTag : uncategorizedTags) {
                uncategorizedItems.add(uncategorizedTag.toItem());
            }
        }
        return new TagCategoryState(revision, categorySeparator, categoryNodes, uncategorizedItems);
    }

    public String getMapIdentifier() {
        return mapIdentifier;
    }

    public String getRevision() {
        return revision;
    }

    public String getCategorySeparator() {
        return categorySeparator;
    }

    public List<TagCategoryNodePayload> getCategories() {
        return categories;
    }

    public List<TagItemPayload> getUncategorizedTags() {
        return uncategorizedTags;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mapIdentifier, revision, categorySeparator, categories, uncategorizedTags);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TagCategoryStatePayload)) {
            return false;
        }
        TagCategoryStatePayload other = (TagCategoryStatePayload) obj;
        return Objects.equals(mapIdentifier, other.mapIdentifier)
            && Objects.equals(revision, other.revision)
            && Objects.equals(categorySeparator, other.categorySeparator)
            && Objects.equals(categories, other.categories)
            && Objects.equals(uncategorizedTags, other.uncategorizedTags);
    }
}
