package org.freeplane.plugin.ai.tools.tagcategories;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import org.freeplane.features.icon.TagCategoryAccess;
import org.freeplane.features.icon.TagCategoryNode;
import org.freeplane.features.icon.TagCategoryState;
import org.freeplane.features.icon.TagItem;
import org.freeplane.features.map.MapModel;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GetTagCategoriesToolTest {
    @Test
    public void getTagCategoriesReturnsStatePayloadWithRevisionAndTree() {
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        TagCategoryAccess tagCategoryAccess = mock(TagCategoryAccess.class);
        MapModel mapModel = mock(MapModel.class);
        String mapIdentifier = UUID.randomUUID().toString();
        TagCategoryState categoryState = new TagCategoryState(
            "sha256:test",
            "::",
            Collections.singletonList(new TagCategoryNode(
                Collections.singletonList("Project"),
                "Project",
                "Project",
                "#11223344",
                Collections.singletonList(new TagCategoryNode(
                    Arrays.asList("Project", "Status"),
                    "Status",
                    "Project::Status",
                    "#22334455",
                    Collections.emptyList())))),
            Collections.singletonList(new TagItem(
                Collections.singletonList("urgent"),
                "urgent",
                "urgent",
                "#ff0000ff")));
        when(availableMaps.findMapModel(UUID.fromString(mapIdentifier), null)).thenReturn(mapModel);
        when(tagCategoryAccess.readCurrentCategoryState(mapModel)).thenReturn(categoryState);
        GetTagCategoriesTool uut = new GetTagCategoriesTool(availableMaps, null, tagCategoryAccess);

        TagCategoryStatePayload result = uut.getTagCategories(new GetTagCategoriesRequest(mapIdentifier));

        assertThat(result.getMapIdentifier()).isEqualTo(mapIdentifier);
        assertThat(result.getRevision()).isEqualTo("sha256:test");
        assertThat(result.getCategorySeparator()).isEqualTo("::");
        assertThat(result.getCategories()).hasSize(1);
        assertThat(result.getCategories().get(0).getChildren()).hasSize(1);
        assertThat(result.getUncategorizedTags()).hasSize(1);
        verify(tagCategoryAccess).readCurrentCategoryState(mapModel);
    }

    @Test
    public void getTagCategoriesRejectsUnknownMapIdentifier() {
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        TagCategoryAccess tagCategoryAccess = mock(TagCategoryAccess.class);
        String mapIdentifier = UUID.randomUUID().toString();
        GetTagCategoriesTool uut = new GetTagCategoriesTool(availableMaps, null, tagCategoryAccess);

        assertThatThrownBy(() -> uut.getTagCategories(new GetTagCategoriesRequest(mapIdentifier)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown map identifier");
    }

    @Test
    public void getTagCategoriesRejectsInvalidMapIdentifier() {
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        TagCategoryAccess tagCategoryAccess = mock(TagCategoryAccess.class);
        GetTagCategoriesTool uut = new GetTagCategoriesTool(availableMaps, null, tagCategoryAccess);

        assertThatThrownBy(() -> uut.getTagCategories(new GetTagCategoriesRequest("invalid")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid map identifier");
    }
}
