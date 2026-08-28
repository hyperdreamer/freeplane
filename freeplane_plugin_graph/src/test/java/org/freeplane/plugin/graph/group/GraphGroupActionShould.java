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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.undo.IActor;
import org.freeplane.core.undo.IUndoHandler;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.map.IMapChangeListener;
import org.freeplane.features.map.MapChangeEvent;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.INodeChangeListener;
import org.freeplane.features.map.NodeChangeEvent;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.clipboard.MapClipboardController;
import org.freeplane.features.mode.Controller;
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
        AtomicReference<IActor> actor = prepareEditableMap(map);
        primary.addExtension(new GraphGroupModel());
        secondary.addExtension(new GraphGroupModel());
        when(mapController.getSelectedNode()).thenReturn(primary);
        when(mapController.getSelectedNodes()).thenReturn(Arrays.asList(primary, secondary));
        GraphGroupController controller = new GraphGroupController(modeController);
        GraphGroupAction action = new GraphGroupAction(modeController, controller);

        action.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "toggle"));

        assertThat(GraphGroupModel.isMarked(primary)).isFalse();
        assertThat(GraphGroupModel.isMarked(secondary)).isFalse();
        assertThat(actor.get()).isNotNull();
        verify(modeController, times(1)).execute(any(IActor.class), eq(map));
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
        NodeModel alreadyUnmarked = new NodeModel("already unmarked", map);
        map.getRootNode().insert(alreadyUnmarked);
        AtomicReference<IActor> actor = prepareEditableMap(map);
        first.addExtension(new GraphGroupModel());
        second.addExtension(new GraphGroupModel());
        GraphGroupController controller = new GraphGroupController(modeController);

        controller.setMarked(Arrays.asList(first, second, alreadyUnmarked), false);

        assertThat(GraphGroupModel.isMarked(first)).isFalse();
        assertThat(GraphGroupModel.isMarked(second)).isFalse();
        assertThat(GraphGroupModel.isMarked(alreadyUnmarked)).isFalse();
        verify(modeController, times(1)).execute(any(IActor.class), eq(map));
        verify(mapController).nodeChanged(first);
        verify(mapController).nodeChanged(second);
        verify(mapController, never()).nodeChanged(alreadyUnmarked);

        actor.get().undo();

        assertThat(GraphGroupModel.isMarked(first)).isTrue();
        assertThat(GraphGroupModel.isMarked(second)).isTrue();
        assertThat(GraphGroupModel.isMarked(alreadyUnmarked)).isFalse();

        actor.get().act();

        assertThat(GraphGroupModel.isMarked(first)).isFalse();
        assertThat(GraphGroupModel.isMarked(second)).isFalse();
        assertThat(GraphGroupModel.isMarked(alreadyUnmarked)).isFalse();
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
    public void publishesEachAttachedClonePositionThroughMapControllerOnActUndoAndRedo() {
        Controller previousController = Controller.getCurrentController();
        Controller.setCurrentController(mock(Controller.class));
        try {
            MapModel map = mapWithTwoLeaves();
            NodeModel original = map.getRootNode().getChildAt(0);
            NodeModel clone = original.cloneContent();
            map.getRootNode().insert(clone);
            original.setHistoryInformation(null);
            ModeController eventModeController = mock(ModeController.class);
            EventPublishingMapController eventMapController = new EventPublishingMapController(eventModeController);
            when(eventModeController.getMapController()).thenReturn(eventMapController);
            AtomicReference<IActor> actor = prepareEditableMap(eventModeController, map);
            List<NodeModel> deliveredEvents = new ArrayList<NodeModel>();
            eventMapController.addNodeChangeListener(new INodeChangeListener() {
                @Override
                public void nodeChanged(NodeChangeEvent event) {
                    deliveredEvents.add(event.getNode());
                }
            });
            GraphGroupController controller = new GraphGroupController(eventModeController);

            controller.setMarked(Arrays.asList(original, clone), true);

            assertThat(GraphGroupModel.isMarked(original)).isTrue();
            assertThat(GraphGroupModel.isMarked(clone)).isTrue();
            assertThat(eventMapController.publishedNodes()).containsExactly(original);
            assertThat(deliveredEvents).containsExactlyInAnyOrder(original, clone);
            verify(eventModeController, times(1)).execute(any(IActor.class), eq(map));

            deliveredEvents.clear();
            actor.get().undo();

            assertThat(GraphGroupModel.isMarked(original)).isFalse();
            assertThat(GraphGroupModel.isMarked(clone)).isFalse();
            assertThat(deliveredEvents).containsExactlyInAnyOrder(original, clone);

            deliveredEvents.clear();
            actor.get().act();

            assertThat(GraphGroupModel.isMarked(original)).isTrue();
            assertThat(GraphGroupModel.isMarked(clone)).isTrue();
            assertThat(eventMapController.publishedNodes()).containsExactly(original, original, original);
            assertThat(deliveredEvents).containsExactlyInAnyOrder(original, clone);
        }
        finally {
            Controller.setCurrentController(previousController);
        }
    }

    @Test
    public void broadcastsMarkerTogglesAsMapLevelChangeEventsOnActUndoAndRedo() {
        Controller previousController = Controller.getCurrentController();
        Controller.setCurrentController(mock(Controller.class));
        try {
            MapModel map = mapWithTwoLeaves();
            NodeModel marked = map.getRootNode().getChildAt(0);
            marked.addExtension(new GraphGroupModel());
            marked.setHistoryInformation(null);
            final List<MapChangeEvent> modelEvents = new ArrayList<MapChangeEvent>();
            map.addMapChangeListener(new IMapChangeListener() {
                @Override
                public void mapChanged(final MapChangeEvent event) {
                    modelEvents.add(event);
                }

                @Override
                public void onNodeDeleted(final org.freeplane.features.map.NodeDeletionEvent event) {
                }

                @Override
                public void onNodeInserted(final NodeModel parent, final NodeModel child, final int index) {
                }

                @Override
                public void onNodeMoved(final org.freeplane.features.map.NodeMoveEvent event) {
                }

                @Override
                public void onPreNodeMoved(final org.freeplane.features.map.NodeMoveEvent event) {
                }

                @Override
                public void onPreNodeDelete(final org.freeplane.features.map.NodeDeletionEvent event) {
                }
            });
            ModeController eventModeController = mock(ModeController.class);
            EventPublishingMapController eventMapController = new EventPublishingMapController(eventModeController);
            when(eventModeController.getMapController()).thenReturn(eventMapController);
            AtomicReference<IActor> actor = prepareEditableMap(eventModeController, map);
            GraphGroupController controller = new GraphGroupController(eventModeController);

            controller.setMarked(Collections.singletonList(marked), false);

            assertThat(eventMapController.publishedMapChanges()).hasSize(1);
            assertThat(modelEvents).hasSize(1);
            MapChangeEvent unmark = eventMapController.publishedMapChanges().get(0);
            assertThat(unmark.getMap()).isSameAs(map);
            assertThat(unmark.getProperty()).isEqualTo(GraphGroupModel.class);
            assertThat(unmark.getOldValue()).isEqualTo(Boolean.TRUE);
            assertThat(unmark.getNewValue()).isEqualTo(Boolean.FALSE);
            assertThat(unmark.setsDirtyFlag()).isFalse();

            eventMapController.clearPublishedMapChanges();
            modelEvents.clear();
            actor.get().undo();

            assertThat(eventMapController.publishedMapChanges()).hasSize(1);
            assertThat(modelEvents).hasSize(1);
            assertThat(eventMapController.publishedMapChanges().get(0).getOldValue()).isEqualTo(Boolean.FALSE);
            assertThat(eventMapController.publishedMapChanges().get(0).getNewValue()).isEqualTo(Boolean.TRUE);

            eventMapController.clearPublishedMapChanges();
            modelEvents.clear();
            actor.get().act();

            assertThat(eventMapController.publishedMapChanges()).hasSize(1);
            assertThat(modelEvents).hasSize(1);
            assertThat(eventMapController.publishedMapChanges().get(0).getOldValue()).isEqualTo(Boolean.TRUE);
            assertThat(eventMapController.publishedMapChanges().get(0).getNewValue()).isEqualTo(Boolean.FALSE);
        }
        finally {
            Controller.setCurrentController(previousController);
        }
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
        return prepareEditableMap(modeController, map);
    }

    private AtomicReference<IActor> prepareEditableMap(ModeController controller, MapModel map) {
        AtomicReference<IActor> actor = new AtomicReference<IActor>();
        map.addExtension(IUndoHandler.class, mock(IUndoHandler.class));
        when(controller.canEdit(map)).thenReturn(true);
        doAnswer(invocation -> {
            IActor captured = invocation.getArgument(0);
            actor.set(captured);
            captured.act();
            return null;
        }).when(controller).execute(any(IActor.class), eq(map));
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

    private static final class EventPublishingMapController extends MapController {
        private final List<NodeModel> publishedNodes = new ArrayList<NodeModel>();
        private final List<MapChangeEvent> publishedMapChanges = new ArrayList<MapChangeEvent>();

        private EventPublishingMapController(ModeController modeController) {
            super(modeController);
        }

        @Override
        protected MapClipboardController createMapClipboardController() {
            return null;
        }

        @Override
        public void nodeChanged(NodeModel node) {
            publishedNodes.add(node);
            super.nodeChanged(node);
        }

        @Override
        public void fireMapChanged(final MapChangeEvent event) {
            publishedMapChanges.add(event);
            super.fireMapChanged(event);
        }

        private List<NodeModel> publishedNodes() {
            return publishedNodes;
        }

        private List<MapChangeEvent> publishedMapChanges() {
            return publishedMapChanges;
        }

        private void clearPublishedMapChanges() {
            publishedMapChanges.clear();
        }
    }
}
