package org.freeplane.plugin.ai.tools.tagcategories;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import javax.swing.tree.DefaultMutableTreeNode;
import org.freeplane.features.icon.IconRegistry;
import org.freeplane.features.icon.TagCategories;
import org.freeplane.features.icon.TagCategoryAccess;
import org.freeplane.features.icon.TagCategoryConflictException;
import org.freeplane.features.icon.TagCategoryInstruction;
import org.freeplane.features.icon.TagCategoryInstructionRequest;
import org.freeplane.features.icon.TagCategoryInstructionType;
import org.freeplane.features.icon.TagCategoryNode;
import org.freeplane.features.icon.TagCategoryState;
import org.freeplane.features.icon.TagCategoryStateBuilder;
import org.freeplane.features.icon.TagTargetLocation;
import org.freeplane.features.icon.mindmapmode.FreeplaneTagCategoryAccess;
import org.freeplane.features.icon.mindmapmode.MIconController;
import org.freeplane.features.map.MapModel;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EditTagCategoriesToolTest {
    @Test
    public void editTagCategoriesConvertsOrderedInstructionsAndReturnsState() {
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        TagCategoryAccess tagCategoryAccess = mock(TagCategoryAccess.class);
        MapModel mapModel = mock(MapModel.class);
        String mapIdentifier = UUID.randomUUID().toString();
        TagCategoryState appliedState = new TagCategoryState(
            "sha256:applied",
            "/",
            Collections.singletonList(new TagCategoryNode(
                Collections.singletonList("Project"),
                "Project",
                "Project",
                "#11223344",
                Collections.emptyList())),
            Collections.emptyList());
        when(availableMaps.findMapModel(UUID.fromString(mapIdentifier), null)).thenReturn(mapModel);
        when(tagCategoryAccess.applyInstructionRequest(eq(mapModel), any())).thenReturn(appliedState);
        EditTagCategoriesTool uut = new EditTagCategoriesTool(availableMaps, null, tagCategoryAccess);
        TagCategoryInstructionPayload addInstruction = new TagCategoryInstructionPayload(
            TagCategoryInstructionType.ADD_TAG,
            Arrays.asList("Project", "Owner"),
            null,
            null,
            TagTargetLocation.CATEGORIZED,
            null,
            null,
            null);
        TagCategoryInstructionPayload separatorInstruction = new TagCategoryInstructionPayload(
            TagCategoryInstructionType.SET_CATEGORY_SEPARATOR,
            null,
            null,
            null,
            null,
            null,
            null,
            "/");
        TagCategoryInstructionRequestPayload request = new TagCategoryInstructionRequestPayload(
            mapIdentifier,
            "sha256:expected",
            Arrays.asList(addInstruction, separatorInstruction));

        TagCategoryStatePayload result = uut.editTagCategories(request);

        ArgumentCaptor<TagCategoryInstructionRequest> requestCaptor = ArgumentCaptor.forClass(TagCategoryInstructionRequest.class);
        verify(tagCategoryAccess).applyInstructionRequest(eq(mapModel), requestCaptor.capture());
        TagCategoryInstructionRequest submittedRequest = requestCaptor.getValue();
        assertThat(submittedRequest.getBaseRevision()).isEqualTo("sha256:expected");
        assertThat(submittedRequest.getInstructions()).extracting(TagCategoryInstruction::getType)
            .containsExactly(TagCategoryInstructionType.ADD_TAG, TagCategoryInstructionType.SET_CATEGORY_SEPARATOR);
        assertThat(submittedRequest.getInstructions().get(0).getPath()).containsExactly("Project", "Owner");
        assertThat(submittedRequest.getInstructions().get(0).getTargetLocation()).isEqualTo(TagTargetLocation.CATEGORIZED);
        assertThat(submittedRequest.getInstructions().get(1).getNewSeparator()).isEqualTo("/");
        assertThat(result.getRevision()).isEqualTo("sha256:applied");
        assertThat(result.getCategorySeparator()).isEqualTo("/");
    }

    @Test
    public void editTagCategoriesPropagatesStaleRevisionConflict() {
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        TagCategoryAccess tagCategoryAccess = mock(TagCategoryAccess.class);
        MapModel mapModel = mock(MapModel.class);
        String mapIdentifier = UUID.randomUUID().toString();
        when(availableMaps.findMapModel(UUID.fromString(mapIdentifier), null)).thenReturn(mapModel);
        when(tagCategoryAccess.applyInstructionRequest(eq(mapModel), any()))
            .thenThrow(new TagCategoryConflictException("stale revision"));
        EditTagCategoriesTool uut = new EditTagCategoriesTool(availableMaps, null, tagCategoryAccess);
        TagCategoryInstructionPayload separatorInstruction = new TagCategoryInstructionPayload(
            TagCategoryInstructionType.SET_CATEGORY_SEPARATOR,
            null,
            null,
            null,
            null,
            null,
            null,
            "/");
        TagCategoryInstructionRequestPayload request = new TagCategoryInstructionRequestPayload(
            mapIdentifier,
            "sha256:expected",
            Collections.singletonList(separatorInstruction));

        assertThatThrownBy(() -> uut.editTagCategories(request))
            .isInstanceOf(TagCategoryConflictException.class)
            .hasMessageContaining("stale revision");
    }

    @Test
    public void editTagCategoriesMatchesCoreAccessOutcomeForEquivalentPayload() {
        TagCategories tagCategories = new TagCategories(
            new DefaultMutableTreeNode("tags"),
            new DefaultMutableTreeNode("uncategorized_tags"),
            "::");
        tagCategories.load("Project\n"
            + " Status\n");
        MapModel mapModelForTool = new MapModel(
            (source, targetMap, withChildren) -> null,
            new IconRegistry(tagCategories),
            null);
        MapModel mapModelForDirectApply = new MapModel(
            (source, targetMap, withChildren) -> null,
            new IconRegistry(tagCategories.copy()),
            null);
        AvailableMaps availableMaps = mock(AvailableMaps.class);
        String mapIdentifier = UUID.randomUUID().toString();
        when(availableMaps.findMapModel(UUID.fromString(mapIdentifier), null)).thenReturn(mapModelForTool);
        MIconController iconController = mock(MIconController.class);
        FreeplaneTagCategoryAccess access = new FreeplaneTagCategoryAccess(iconController);
        EditTagCategoriesTool uut = new EditTagCategoriesTool(availableMaps, null, access);
        String expectedRevision = TagCategoryStateBuilder
            .from(tagCategories)
            .getRevision();
        TagCategoryInstructionPayload renameInstruction = new TagCategoryInstructionPayload(
            TagCategoryInstructionType.RENAME_TAG,
            Arrays.asList("Project", "Status"),
            "State",
            null,
            null,
            null,
            null,
            null);
        TagCategoryInstructionPayload separatorInstruction = new TagCategoryInstructionPayload(
            TagCategoryInstructionType.SET_CATEGORY_SEPARATOR,
            null,
            null,
            null,
            null,
            null,
            null,
            "/");
        TagCategoryInstructionRequestPayload request = new TagCategoryInstructionRequestPayload(
            mapIdentifier,
            expectedRevision,
            Arrays.asList(renameInstruction, separatorInstruction));
        TagCategoryInstructionRequest directRequest = new TagCategoryInstructionRequest(
            expectedRevision,
            Arrays.asList(
                TagCategoryInstruction.renameTag(Arrays.asList("Project", "Status"), "State"),
                TagCategoryInstruction.setCategorySeparator("/")));
        TagCategoryState expectedState = access.applyInstructionRequest(mapModelForDirectApply, directRequest);

        TagCategoryStatePayload actualPayload = uut.editTagCategories(request);

        assertThat(actualPayload.getRevision()).isEqualTo(expectedState.getRevision());
        assertThat(actualPayload.getCategorySeparator()).isEqualTo(expectedState.getCategorySeparator());
        assertThat(actualPayload.getCategories()).hasSize(expectedState.getCategories().size());
    }
}
