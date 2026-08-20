package org.freeplane.plugin.graph.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.freeplane.plugin.graph.command.GraphCommandRouter;
import org.freeplane.plugin.graph.command.MapUndoTarget;
import org.freeplane.plugin.graph.workspace.GraphCommandResult;
import org.freeplane.plugin.graph.workspace.GraphWorkspaceStore;
import org.freeplane.plugin.graph.workspace.ListenerRegistration;
import org.freeplane.plugin.graph.workspace.WorkspaceStoreEvent;
import org.freeplane.plugin.graph.workspace.WorkspaceStoreListener;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.junit.Test;

public class WorkspaceSessionStatusShould {
    private static final MapReferenceId MAP_ONE =
        MapReferenceId.of("00000000-0000-0000-0000-000000000101");
    private static final MapReferenceId MAP_TWO =
        MapReferenceId.of("00000000-0000-0000-0000-000000000102");

    @Test
    public void createsAnEmptySnapshot() {
        WorkspaceSessionStatus status = WorkspaceSessionStatus.empty();

        assertThat(status.workspaceDirty()).isFalse();
        assertThat(status.workspaceUndoAvailable()).isFalse();
        assertThat(status.workspaceRedoAvailable()).isFalse();
        assertThat(status.saveFailed()).isFalse();
        assertThat(status.dirtySourceMaps()).isEmpty();
        assertThat(status.sourceMapUndoTarget()).isEmpty();
    }

    @Test
    public void exposesEveryFieldWithDeterministicDefensiveDirtyMapCopying() {
        Set<MapReferenceId> dirtyMaps = new LinkedHashSet<MapReferenceId>(Arrays.asList(MAP_TWO, MAP_ONE));
        MapUndoTarget target = new MapUndoTarget(MAP_ONE, "Map one", true);

        WorkspaceSessionStatus status = WorkspaceSessionStatus.of(true, true, false, true, dirtyMaps,
            Optional.of(target));
        dirtyMaps.add(MapReferenceId.of("00000000-0000-0000-0000-000000000103"));

        assertThat(status.workspaceDirty()).isTrue();
        assertThat(status.workspaceUndoAvailable()).isTrue();
        assertThat(status.workspaceRedoAvailable()).isFalse();
        assertThat(status.saveFailed()).isTrue();
        assertThat(status.dirtySourceMaps()).containsExactly(MAP_ONE, MAP_TWO);
        assertThat(status.sourceMapUndoTarget()).contains(target);
        assertThatThrownBy(() -> status.dirtySourceMaps().add(MAP_ONE))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void refreshesStoreLifecycleAndUnionsCommandStatus() {
        GraphWorkspaceStore store = mock(GraphWorkspaceStore.class);
        GraphCommandRouter router = mock(GraphCommandRouter.class);
        ListenerRegistration storeRegistration = mock(ListenerRegistration.class);
        AtomicReference<WorkspaceStoreListener> storeListener = new AtomicReference<WorkspaceStoreListener>();
        when(store.addListener(any(WorkspaceStoreListener.class))).thenAnswer(invocation -> {
            storeListener.set(invocation.getArgument(0));
            return storeRegistration;
        });
        when(store.isDirty()).thenReturn(false, true, true, false);
        when(store.canUndo()).thenReturn(false, true, true, false);
        when(store.canRedo()).thenReturn(false, true, true, true);

        WorkspaceSessionStatusPublisher publisher = new WorkspaceSessionStatusPublisher(store, router);
        List<WorkspaceSessionStatus> received = new ArrayList<WorkspaceSessionStatus>();
        publisher.addListener(status -> {
            throw new IllegalStateException("listener failed");
        });
        publisher.addListener(received::add);

        MapUndoTarget target = new MapUndoTarget(MAP_ONE, "Map one", true);
        GraphCommandResult firstCommandResult = mock(GraphCommandResult.class);
        when(firstCommandResult.dirtySourceMaps()).thenReturn(Collections.singleton(MAP_ONE));
        GraphCommandResult commandResult = mock(GraphCommandResult.class);
        when(commandResult.dirtySourceMaps()).thenReturn(
            new LinkedHashSet<MapReferenceId>(Arrays.asList(MAP_TWO, MAP_ONE)));
        when(router.currentMapUndoTarget()).thenReturn(Optional.<MapUndoTarget>empty());
        publisher.recordCommandResult(firstCommandResult);
        when(router.currentMapUndoTarget()).thenReturn(Optional.of(target));
        publisher.recordCommandResult(commandResult);

        assertThat(received).hasSize(2);
        assertThat(received.get(0).dirtySourceMaps()).containsExactly(MAP_ONE);
        assertThat(received.get(1).dirtySourceMaps()).containsExactly(MAP_ONE, MAP_TWO);
        assertThat(received.get(1).sourceMapUndoTarget()).contains(target);

        storeListener.get().onWorkspaceStoreEvent(event(WorkspaceStoreEvent.Type.DOCUMENT_CHANGED));
        assertThat(received.get(2).workspaceDirty()).isTrue();
        assertThat(received.get(2).workspaceUndoAvailable()).isTrue();
        assertThat(received.get(2).workspaceRedoAvailable()).isTrue();
        assertThat(received.get(2).saveFailed()).isFalse();

        storeListener.get().onWorkspaceStoreEvent(event(WorkspaceStoreEvent.Type.SAVE_FAILED));
        assertThat(received.get(3).saveFailed()).isTrue();
        assertThat(received.get(3).dirtySourceMaps()).containsExactly(MAP_ONE, MAP_TWO);

        storeListener.get().onWorkspaceStoreEvent(event(WorkspaceStoreEvent.Type.SAVED));
        assertThat(received.get(4).saveFailed()).isFalse();
        assertThat(received.get(4).workspaceDirty()).isFalse();
        assertThat(received.get(4).workspaceUndoAvailable()).isFalse();
        assertThat(received.get(4).workspaceRedoAvailable()).isTrue();

        publisher.close();
        publisher.close();
        verify(storeRegistration).close();
    }

    private static WorkspaceStoreEvent event(final WorkspaceStoreEvent.Type type) {
        WorkspaceStoreEvent event = mock(WorkspaceStoreEvent.class);
        when(event.type()).thenReturn(type);
        return event;
    }
}
