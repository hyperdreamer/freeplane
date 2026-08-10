package org.freeplane.plugin.graph.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.namespace.QName;

import org.freeplane.plugin.graph.workspace.model.DisplaySettings;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.UnknownXml;
import org.freeplane.plugin.graph.workspace.model.Viewport;
import org.freeplane.plugin.graph.workspace.model.WorkspaceCompatibility;
import org.freeplane.plugin.graph.workspace.model.WorkspaceDocument;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;
import org.junit.Test;

public class WorkspaceHistoryShould {
    private static final WorkspaceId ORIGINAL_ID =
        WorkspaceId.of("00000000-0000-0000-0000-000000000100");
    private static final WorkspaceId CURRENT_ID =
        WorkspaceId.of("00000000-0000-0000-0000-000000000200");
    private static final MapReferenceId MAP_ONE =
        MapReferenceId.of("00000000-0000-0000-0000-000000000001");

    @Test
    public void executeAppliedCommandsOnceAndLeaveHistoryUntouchedForNoOpOrRejectedCommands() {
        WorkspaceDocument before = document();
        WorkspaceDocument after = before.toBuilder()
            .displaySettings(DisplaySettings.of(false, DisplaySettings.CanvasTheme.DARK, true, true,
                Collections.<UnknownXml>emptyList()))
            .build();
        AtomicInteger invocations = new AtomicInteger();
        WorkspaceCommand applied = new WorkspaceCommand() {
            @Override
            public WorkspaceTransition apply(WorkspaceDocument current) {
                invocations.incrementAndGet();
                return WorkspaceTransition.applied(after, "test.applied");
            }
        };
        WorkspaceHistory history = new WorkspaceHistory();

        WorkspaceTransition result = history.execute(applied, before);

        assertThat(invocations.get()).isEqualTo(1);
        assertThat(result.status()).isEqualTo(WorkspaceTransition.Status.APPLIED);
        assertThat(history.canUndo()).isTrue();
        assertThat(history.canRedo()).isFalse();

        history.undo(after);
        assertThat(history.canUndo()).isFalse();
        assertThat(history.canRedo()).isTrue();

        WorkspaceTransition noOp = history.execute(new WorkspaceCommand() {
            @Override
            public WorkspaceTransition apply(WorkspaceDocument current) {
                return WorkspaceTransition.noOp(current, "test.no-op");
            }
        }, before);
        WorkspaceTransition rejected = history.execute(new WorkspaceCommand() {
            @Override
            public WorkspaceTransition apply(WorkspaceDocument current) {
                return WorkspaceTransition.rejected(current, "test.rejected");
            }
        }, before);

        assertThat(noOp.status()).isEqualTo(WorkspaceTransition.Status.NO_OP);
        assertThat(rejected.status()).isEqualTo(WorkspaceTransition.Status.REJECTED);
        assertThat(history.canUndo()).isFalse();
        assertThat(history.canRedo()).isTrue();
    }

    @Test
    public void clearRedoWhenANewCommandIsAppliedAfterUndo() {
        WorkspaceDocument before = document();
        WorkspaceHistory history = new WorkspaceHistory();
        WorkspaceTransition first = history.execute(WorkspaceCommands.addMap(MAP_ONE, URI.create("maps/one.mm")),
            before);
        WorkspaceTransition undone = history.undo(first.after());

        WorkspaceTransition second = history.execute(WorkspaceCommands.setDisplaySettings(DisplaySettings.of(false,
            DisplaySettings.CanvasTheme.LIGHT, true, true, Collections.<UnknownXml>emptyList())), undone.after());
        WorkspaceTransition redo = history.redo(second.after());

        assertThat(second.status()).isEqualTo(WorkspaceTransition.Status.APPLIED);
        assertThat(history.canRedo()).isFalse();
        assertThat(redo.status()).isEqualTo(WorkspaceTransition.Status.NO_OP);
        assertThat(redo.messageKey()).isEqualTo("graph_workspace.history.nothing_to_redo");
        assertThat(redo.after()).isEqualTo(second.after());
    }

    @Test
    public void overlayTheCurrentIdentityCompatibilityAndViewportOnUndoAndRedo() {
        UnknownXml beforeUnknown = unknown("before", "before-value");
        UnknownXml afterUnknown = unknown("after", "after-value");
        WorkspaceDocument before = document().toBuilder()
            .unknownXml(Collections.singletonList(beforeUnknown))
            .build();
        WorkspaceDocument after = WorkspaceCommands.addMap(MAP_ONE, URI.create("maps/one.mm")).apply(before).after()
            .toBuilder()
            .displaySettings(DisplaySettings.of(false, DisplaySettings.CanvasTheme.DARK, false, false,
                Collections.<UnknownXml>emptyList()))
            .unknownXml(Collections.singletonList(afterUnknown))
            .build();
        WorkspaceHistory history = new WorkspaceHistory();
        history.execute(new WorkspaceCommand() {
            @Override
            public WorkspaceTransition apply(WorkspaceDocument current) {
                return WorkspaceTransition.applied(after, "test.changed");
            }
        }, before);
        Viewport firstPan = Viewport.of(12, -6, 2, Collections.<UnknownXml>emptyList());
        WorkspaceDocument currentForUndo = after.toBuilder()
            .id(CURRENT_ID)
            .sourceFormatVersion(2)
            .compatibility(WorkspaceCompatibility.READ_ONLY_NEWER)
            .viewport(firstPan)
            .build();

        WorkspaceTransition undone = history.undo(currentForUndo);

        assertThat(undone.status()).isEqualTo(WorkspaceTransition.Status.APPLIED);
        assertThat(undone.messageKey()).isEqualTo("graph_workspace.history.undone");
        assertThat(undone.after().id()).isEqualTo(CURRENT_ID);
        assertThat(undone.after().sourceFormatVersion()).isEqualTo(2);
        assertThat(undone.after().compatibility()).isEqualTo(WorkspaceCompatibility.READ_ONLY_NEWER);
        assertThat(undone.after().viewport()).isEqualTo(firstPan);
        assertThat(undone.after().maps()).isEmpty();
        assertThat(undone.after().displaySettings()).isEqualTo(DisplaySettings.defaults());
        assertThat(undone.after().unknownXml()).containsExactly(beforeUnknown);

        Viewport secondPan = Viewport.of(-9, 5, 0.5, Collections.<UnknownXml>emptyList());
        WorkspaceDocument currentForRedo = undone.after().toBuilder().viewport(secondPan).build();
        WorkspaceTransition redone = history.redo(currentForRedo);

        assertThat(redone.status()).isEqualTo(WorkspaceTransition.Status.APPLIED);
        assertThat(redone.messageKey()).isEqualTo("graph_workspace.history.redone");
        assertThat(redone.after().id()).isEqualTo(CURRENT_ID);
        assertThat(redone.after().sourceFormatVersion()).isEqualTo(2);
        assertThat(redone.after().compatibility()).isEqualTo(WorkspaceCompatibility.READ_ONLY_NEWER);
        assertThat(redone.after().viewport()).isEqualTo(secondPan);
        assertThat(redone.after().maps()).hasSize(1);
        assertThat(redone.after().displaySettings()).isEqualTo(after.displaySettings());
        assertThat(redone.after().unknownXml()).containsExactly(afterUnknown);
    }

    @Test
    public void reportEmptyHistoryAndClearBothStacks() {
        WorkspaceDocument document = document();
        WorkspaceHistory history = new WorkspaceHistory();

        WorkspaceTransition undo = history.undo(document);
        WorkspaceTransition redo = history.redo(document);

        assertThat(undo.status()).isEqualTo(WorkspaceTransition.Status.NO_OP);
        assertThat(undo.messageKey()).isEqualTo("graph_workspace.history.nothing_to_undo");
        assertThat(undo.after()).isEqualTo(document);
        assertThat(redo.status()).isEqualTo(WorkspaceTransition.Status.NO_OP);
        assertThat(redo.messageKey()).isEqualTo("graph_workspace.history.nothing_to_redo");

        WorkspaceTransition applied = history.execute(WorkspaceCommands.addMap(MAP_ONE, URI.create("maps/one.mm")),
            document);
        history.undo(applied.after());
        history.clear();

        assertThat(history.canUndo()).isFalse();
        assertThat(history.canRedo()).isFalse();
    }

    @Test
    public void makeTransitionArgumentsImmutableAndDefensivelyCopied() {
        Object[] arguments = new Object[] { "before" };
        WorkspaceTransition transition = WorkspaceTransition.applied(document(), "test.transition", arguments);
        arguments[0] = "after";

        assertThat(transition.messageArguments()).containsExactly("before");
        assertThatThrownBy(() -> transition.messageArguments().add("later"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    private static WorkspaceDocument document() {
        return WorkspaceDocument.createVersion1(ORIGINAL_ID);
    }

    private static UnknownXml unknown(String name, String value) {
        return UnknownXml.attribute(UnknownXml.Owner.WORKSPACE, new QName("urn:test", name), value);
    }
}
