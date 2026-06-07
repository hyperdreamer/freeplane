package org.freeplane.plugin.ai.tools.tagcategories;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.freeplane.features.icon.TagCategoryNode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TagCategoryNodePayload {
    private final List<String> path;
    private final String name;
    private final String qualifiedName;
    private final String color;
    private final List<TagCategoryNodePayload> children;

    @JsonCreator
    public TagCategoryNodePayload(@JsonProperty("path") List<String> path,
                                  @JsonProperty("name") String name,
                                  @JsonProperty("qualifiedName") String qualifiedName,
                                  @JsonProperty("color") String color,
                                  @JsonProperty("children") List<TagCategoryNodePayload> children) {
        this.path = path;
        this.name = name;
        this.qualifiedName = qualifiedName;
        this.color = color;
        this.children = children;
    }

    static TagCategoryNodePayload fromNode(TagCategoryNode node) {
        ArrayList<TagCategoryNodePayload> childPayloads = new ArrayList<>();
        for (TagCategoryNode child : node.getChildren()) {
            childPayloads.add(fromNode(child));
        }
        return new TagCategoryNodePayload(
            node.getPath(),
            node.getName(),
            node.getQualifiedName(),
            node.getColor(),
            childPayloads);
    }

    TagCategoryNode toNode() {
        ArrayList<TagCategoryNode> childNodes = new ArrayList<>();
        if (children != null) {
            for (TagCategoryNodePayload child : children) {
                childNodes.add(child.toNode());
            }
        }
        return new TagCategoryNode(path, name, qualifiedName, color, childNodes);
    }

    public List<String> getPath() {
        return path;
    }

    public String getName() {
        return name;
    }

    public String getQualifiedName() {
        return qualifiedName;
    }

    public String getColor() {
        return color;
    }

    public List<TagCategoryNodePayload> getChildren() {
        return children;
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, name, qualifiedName, color, children);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TagCategoryNodePayload)) {
            return false;
        }
        TagCategoryNodePayload other = (TagCategoryNodePayload) obj;
        return Objects.equals(path, other.path)
            && Objects.equals(name, other.name)
            && Objects.equals(qualifiedName, other.qualifiedName)
            && Objects.equals(color, other.color)
            && Objects.equals(children, other.children);
    }
}
