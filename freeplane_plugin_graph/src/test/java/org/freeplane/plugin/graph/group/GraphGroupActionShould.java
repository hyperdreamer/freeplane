package org.freeplane.plugin.graph.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.event.ActionEvent;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.undo.IActor;
import org.freeplane.core.undo.IUndoHandler;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.ModeController;
import org.freeplane.main.application.ApplicationResourceController;
import org.freeplane.plugin.graph.GraphModeExtension;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

public class GraphGroupActionShould {
    private MapController mapController;
    private ModeController modeController;
    private org.freeplane.core.io.ReadManager reader;
    private MockedStatic<ResourceController> resourceController;
    private MockedStatic<TextUtils> textUtils;
    private org.freeplane.core.io.WriteManager writer;

    @Before
    public void setUp() {
        ResourceController resources = mock(ResourceController.class);
        resourceController = org.mockito.Mockito.mockStatic(ResourceController.class);
        resourceController.when(ResourceController::getResourceController).thenReturn(resources);
        textUtils = org.mockito.Mockito.mockStatic(TextUtils.class);
        textUtils.when(() -> TextUtils.getRawText(any(String.class))).thenAnswer(invocation -> invocation.getArgument(0));
        textUtils.when(() -> TextUtils.getRawText(any(String.class), any(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(1));
        modeController = mock(ModeController.class);
        mapController = mock(MapController.class);
        reader = new org.freeplane.core.io.ReadManager();
        writer = new org.freeplane.core.io.WriteManager();
        when(modeController.getMapController()).thenReturn(mapController);
        when(mapController.getReadManager()).thenReturn(reader);
        when(mapController.getWriteManager()).thenReturn(writer);
    }

    @After
    public void tearDown() {
        textUtils.close();
        resourceController.close();
    }

    @Test
    public void togglesThePrimarySelectionStateAcrossTheWholeOrderedSelectionInOneActor() {
        MapModel map = mapWithTwoLeaves();
        NodeModel primary = map.getRootNode().getChildAt(0);
        NodeModel secondary = map.getRootNode().getChildAt(1);
        prepareEditableMap(map);
        primary.addExtension(new GraphGroupModel());
        when(mapController.getSelectedNode()).thenReturn(primary);
        when(mapController.getSelectedNodes()).thenReturn(Arrays.asList(primary, secondary));
        GraphGroupController controller = new GraphGroupController(modeController);
        GraphGroupAction action = new GraphGroupAction(modeController, controller);

        action.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "toggle"));

        assertThat(GraphGroupModel.isMarked(primary)).isFalse();
        assertThat(GraphGroupModel.isMarked(secondary)).isFalse();
        verify(modeController).execute(any(IActor.class), eq(map));
    }

    @Test
    public void selectedStateFollowsThePrimarySelectedNode() {
        MapModel map = mapWithTwoLeaves();
        NodeModel primary = map.getRootNode().getChildAt(0);
        prepareEditableMap(map);
        when(mapController.getSelectedNode()).thenReturn(primary);
        GraphGroupController controller = new GraphGroupController(modeController);
        GraphGroupAction action = new GraphGroupAction(modeController, controller);

        primary.addExtension(new GraphGroupModel());
        action.setSelected();

        assertThat(action.isSelected()).isTrue();

        primary.removeExtension(GraphGroupModel.class);
        action.setSelected();

        assertThat(action.isSelected()).isFalse();
    }

    @Test
    public void recordsOneActorAndRestoresExactPriorStatesOnUndoAndRedo() {
        MapModel map = mapWithTwoLeaves();
        NodeModel first = map.getRootNode().getChildAt(0);
        NodeModel second = map.getRootNode().getChildAt(1);
        AtomicReference<IActor> actor = prepareEditableMap(map);
        first.addExtension(new GraphGroupModel());
        GraphGroupController controller = new GraphGroupController(modeController);

        controller.setMarked(Arrays.asList(first, second), false);

        assertThat(GraphGroupModel.isMarked(first)).isFalse();
        assertThat(GraphGroupModel.isMarked(second)).isFalse();
        verify(modeController, times(1)).execute(any(IActor.class), eq(map));
        verify(mapController).nodeChanged(first);

        actor.get().undo();

        assertThat(GraphGroupModel.isMarked(first)).isTrue();
        assertThat(GraphGroupModel.isMarked(second)).isFalse();

        actor.get().act();

        assertThat(GraphGroupModel.isMarked(first)).isFalse();
        assertThat(GraphGroupModel.isMarked(second)).isFalse();
    }

    @Test
    public void deduplicatesSharedClonePositionsForOneActorAndCountsAllAffectedPositions() {
        MapModel map = mapWithTwoLeaves();
        NodeModel original = map.getRootNode().getChildAt(0);
        NodeModel clone = original.cloneContent();
        map.getRootNode().insert(clone);
        prepareEditableMap(map);
        GraphGroupController controller = new GraphGroupController(modeController);

        controller.setMarked(Arrays.asList(original, clone), true);

        assertThat(GraphGroupModel.isMarked(original)).isTrue();
        assertThat(GraphGroupModel.isMarked(clone)).isTrue();
        assertThat(controller.affectedClonePositionCount(Arrays.asList(original, clone))).isEqualTo(2);
        verify(modeController, times(1)).execute(any(IActor.class), eq(map));
        verify(mapController, times(1)).nodeChanged(original);
        verify(mapController, never()).nodeChanged(clone);
    }

    @Test
    public void doesNotCreateAnActorOrPublishChangesForAnAlreadyHeldTargetState() {
        MapModel map = mapWithTwoLeaves();
        NodeModel node = map.getRootNode().getChildAt(0);
        prepareEditableMap(map);
        node.addExtension(new GraphGroupModel());
        GraphGroupController controller = new GraphGroupController(modeController);

        controller.setMarked(Collections.singletonList(node), true);

        verify(modeController, never()).execute(any(IActor.class), any(MapModel.class));
        verify(mapController, never()).nodeChanged(any(NodeModel.class));
    }

    @Test
    public void rejectsMissingUndoReadOnlyMixedAndDetachedNodesBeforeMutation() {
        MapModel noUndoMap = mapWithTwoLeaves();
        NodeModel noUndoNode = noUndoMap.getRootNode().getChildAt(0);
        when(modeController.canEdit(noUndoMap)).thenReturn(true);
        GraphGroupController controller = new GraphGroupController(modeController);

        assertThatIllegalStateException().isThrownBy(() -> controller.setMarked(
            Collections.singletonList(noUndoNode), true));
        assertThat(GraphGroupModel.isMarked(noUndoNode)).isFalse();

        MapModel readOnlyMap = mapWithTwoLeaves();
        NodeModel readOnlyNode = readOnlyMap.getRootNode().getChildAt(0);
        readOnlyMap.addExtension(IUndoHandler.class, mock(IUndoHandler.class));
        readOnlyMap.setReadOnly(true);

        assertThatIllegalStateException().isThrownBy(() -> controller.setMarked(
            Collections.singletonList(readOnlyNode), true));
        assertThat(GraphGroupModel.isMarked(readOnlyNode)).isFalse();

        MapModel otherMap = mapWithTwoLeaves();
        NodeModel otherNode = otherMap.getRootNode().getChildAt(0);
        noUndoMap.addExtension(IUndoHandler.class, mock(IUndoHandler.class));
        otherMap.addExtension(IUndoHandler.class, mock(IUndoHandler.class));

        assertThatIllegalArgumentException().isThrownBy(() -> controller.setMarked(
            Arrays.asList(noUndoNode, otherNode), true));
        assertThat(GraphGroupModel.isMarked(noUndoNode)).isFalse();
        assertThat(GraphGroupModel.isMarked(otherNode)).isFalse();

        NodeModel detached = new NodeModel("detached", noUndoMap);

        assertThatIllegalArgumentException().isThrownBy(() -> controller.setMarked(
            Collections.singletonList(detached), true));
        assertThat(GraphGroupModel.isMarked(detached)).isFalse();
    }

    @Test
    public void treatsNullAndEmptySelectionsAsNoOps() {
        GraphGroupController controller = new GraphGroupController(modeController);

        controller.setMarked(null, true);
        controller.setMarked(Collections.<NodeModel>emptyList(), false);

        verify(modeController, never()).execute(any(IActor.class), any(MapModel.class));
        verify(mapController, never()).nodeChanged(any(NodeModel.class));
    }

    @Test
    public void installsAndClosesAllGraphGroupRegistrationsSymmetrically() {
        ApplicationResourceController applicationResources = mock(ApplicationResourceController.class);
        resourceController.when(ResourceController::getResourceController).thenReturn(applicationResources);
        GraphModeExtension extension = new GraphModeExtension();

        extension.installExtension(modeController, null);
        extension.close();
        extension.close();

        assertThat(reader.getElementHandlers().list("graph_group")).isEmpty();
        assertThat(writer.getExtensionElementWriters().list(GraphGroupModel.class)).isEmpty();
        verify(modeController).addExtension(eq(GraphGroupController.class), any(GraphGroupController.class));
        verify(modeController).addAction(any(GraphGroupAction.class));
        verify(modeController, times(1)).removeAction("GraphGroupAction");
        verify(modeController, times(1)).removeExtension(GraphGroupController.class);
    }

    private AtomicReference<IActor> prepareEditableMap(MapModel map) {
        AtomicReference<IActor> actor = new AtomicReference<IActor>();
        map.addExtension(IUndoHandler.class, mock(IUndoHandler.class));
        when(modeController.canEdit(map)).thenReturn(true);
        doAnswer(invocation -> {
            IActor captured = invocation.getArgument(0);
            actor.set(captured);
            captured.act();
            return null;
        }).when(modeController).execute(any(IActor.class), eq(map));
        return actor;
    }

    private MapModel mapWithTwoLeaves() {
        MapModel map = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel root = new NodeModel("root", map);
        map.setRoot(root);
        root.insert(new NodeModel("first", map));
        root.insert(new NodeModel("second", map));
        return map;
    }
}
