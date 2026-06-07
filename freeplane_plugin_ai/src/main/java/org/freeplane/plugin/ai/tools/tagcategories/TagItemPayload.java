package org.freeplane.plugin.ai.tools.tagcategories;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;
import org.freeplane.features.icon.TagItem;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TagItemPayload {
    private final List<String> path;
    private final String name;
    private final String qualifiedName;
    private final String color;

    @JsonCreator
    public TagItemPayload(@JsonProperty("path") List<String> path,
                          @JsonProperty("name") String name,
                          @JsonProperty("qualifiedName") String qualifiedName,
                          @JsonProperty("color") String color) {
        this.path = path;
        this.name = name;
        this.qualifiedName = qualifiedName;
        this.color = color;
    }

    static TagItemPayload fromItem(TagItem item) {
        return new TagItemPayload(
            item.getPath(),
            item.getName(),
            item.getQualifiedName(),
            item.getColor());
    }

    TagItem toItem() {
        return new TagItem(path, name, qualifiedName, color);
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

    @Override
    public int hashCode() {
        return Objects.hash(path, name, qualifiedName, color);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TagItemPayload)) {
            return false;
        }
        TagItemPayload other = (TagItemPayload) obj;
        return Objects.equals(path, other.path)
            && Objects.equals(name, other.name)
            && Objects.equals(qualifiedName, other.qualifiedName)
            && Objects.equals(color, other.color);
    }
}
