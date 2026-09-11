package org.freeplane.plugin.graph.window;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.event.ActionEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.mode.Controller;
import org.freeplane.plugin.graph.control.GraphWorkspaceController;
import org.freeplane.plugin.graph.control.GraphWorkspaceOpenException;
import org.freeplane.plugin.graph.workspace.RecentWorkspaceList;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;

public class OpenGraphWorkspaceActionShould {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();
    private MockedStatic<TextUtils> textUtils;
    private MockedStatic<ResourceController> resourceController;

    @Before
    public void setUp() {
        resourceController = org.mockito.Mockito.mockStatic(ResourceController.class);
        resourceController.when(ResourceController::getResourceController)
            .thenReturn(mock(ResourceController.class));
        textUtils = org.mockito.Mockito.mockStatic(TextUtils.class);
        textUtils.when(() -> TextUtils.getText(any(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        textUtils.when(() -> TextUtils.getText(any(String.class), any(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        textUtils.when(() -> TextUtils.getRawText(any(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        textUtils.when(() -> TextUtils.getRawText(any(String.class), any(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        textUtils.when(() -> TextUtils.format(any(String.class), any(Object[].class)))
            .thenAnswer(invocation -> formattedText(invocation));
    }

    @After
    public void tearDown() {
        textUtils.close();
        resourceController.close();
    }

    @Test
    public void opensTheMostRecentExistingEntryWithOpenExisting() throws Exception {
        Path existing = temporaryFolder.newFile("recent-open.fpg").toPath().toRealPath();
        GraphWorkspaceController controller = mock(GraphWorkspaceController.class);
        RecentWorkspaceList list = recentList(new AtomicReference<String>(), existing);
        AtomicInteger chooserCalls = new AtomicInteger();
        List<String> messages = new ArrayList<String>();
        OpenGraphWorkspaceAction action = new OpenGraphWorkspaceAction(controller, list,
            () -> {
                chooserCalls.incrementAndGet();
                return null;
            }, messages::add);

        action.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "open"));

        verify(controller).openExisting(existing);
        verify(controller, never()).open(any());
        assertThat(chooserCalls).hasValue(0);
        assertThat(messages).isEmpty();
    }

    @Test
    public void fallsThroughToTheNextExistingEntryWhenTheNewestIsMissing() throws Exception {
        Path existing = temporaryFolder.newFile("older-existing.fpg").toPath().toRealPath();
        Path missing = temporaryFolder.getRoot().toPath().resolve("missing-newest.fpg");
        GraphWorkspaceController controller = mock(GraphWorkspaceController.class);
        RecentWorkspaceList list = recentList(new AtomicReference<String>(), existing, missing);
        OpenGraphWorkspaceAction action = new OpenGraphWorkspaceAction(controller, list,
            () -> null, message -> { });

        action.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "open"));

        verify(controller).openExisting(existing);
    }

    @Test
    public void fallsBackToTheChooserWhenNoStoredEntryIsUsable() throws Exception {
        Path chosen = temporaryFolder.newFile("chooser-chosen.fpg").toPath().toRealPath();
        GraphWorkspaceController controller = mock(GraphWorkspaceController.class);
        OpenGraphWorkspaceAction action = new OpenGraphWorkspaceAction(controller,
            RecentWorkspaceList.empty(), () -> chosen, message -> { });

        action.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "open"));

        verify(controller).open(chosen);
        verify(controller, never()).openExisting(any());
    }

    @Test
    public void reportsAndFallsBackWhenTheRecentOpenThrowsGraphWorkspaceOpenException() throws Exception {
        Path recent = temporaryFolder.newFile("failing-recent.fpg").toPath().toRealPath();
        Path chosen = temporaryFolder.newFile("fallback.fpg").toPath().toRealPath();
        GraphWorkspaceController controller = mock(GraphWorkspaceController.class);
        RecentWorkspaceList list = recentList(new AtomicReference<String>(), recent);
        when(controller.openExisting(recent))
            .thenThrow(new GraphWorkspaceOpenException(recent, new IllegalStateException("gone")));
        List<String> messages = new ArrayList<String>();
        OpenGraphWorkspaceAction action = new OpenGraphWorkspaceAction(controller, list, () -> chosen, messages::add);

        action.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "open"));

        assertThat(messages).containsExactly(
            "graph_workspace.recent_workspaces.open_failed[" + recent + "]");
        verify(controller).open(chosen);
    }

    @Test
    public void reportsAndFallsBackWhenTheRecentOpenThrowsIllegalArgumentException() throws Exception {
        Path recent = temporaryFolder.newFile("illegal-recent.fpg").toPath().toRealPath();
        Path chosen = temporaryFolder.newFile("illegal-fallback.fpg").toPath().toRealPath();
        GraphWorkspaceController controller = mock(GraphWorkspaceController.class);
        RecentWorkspaceList list = recentList(new AtomicReference<String>(), recent);
        when(controller.openExisting(recent)).thenThrow(new IllegalArgumentException("bad path"));
        List<String> messages = new ArrayList<String>();
        OpenGraphWorkspaceAction action = new OpenGraphWorkspaceAction(controller, list, () -> chosen, messages::add);

        action.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "open"));

        assertThat(messages).containsExactly(
            "graph_workspace.recent_workspaces.open_failed[" + recent + "]");
        verify(controller).open(chosen);
    }

    @Test
    public void reportsAndFallsBackWhenTheRecentOpenThrowsIllegalStateException() throws Exception {
        Path recent = temporaryFolder.newFile("state-recent.fpg").toPath().toRealPath();
        Path chosen = temporaryFolder.newFile("state-fallback.fpg").toPath().toRealPath();
        GraphWorkspaceController controller = mock(GraphWorkspaceController.class);
        RecentWorkspaceList list = recentList(new AtomicReference<String>(), recent);
        when(controller.openExisting(recent)).thenThrow(new IllegalStateException("shut down"));
        List<String> messages = new ArrayList<String>();
        OpenGraphWorkspaceAction action = new OpenGraphWorkspaceAction(controller, list, () -> chosen, messages::add);

        action.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "open"));

        assertThat(messages).containsExactly(
            "graph_workspace.recent_workspaces.open_failed[" + recent + "]");
        verify(controller).open(chosen);
    }

    @Test
    public void constructsWithTheLazyDefaultMessageSink() {
        try (MockedStatic<Controller> controller = org.mockito.Mockito.mockStatic(Controller.class)) {
            controller.when(Controller::getCurrentController)
                .thenThrow(new AssertionError("Controller touched during construction"));

            OpenGraphWorkspaceAction first = new OpenGraphWorkspaceAction(mock(GraphWorkspaceController.class));
            OpenGraphWorkspaceAction second = new OpenGraphWorkspaceAction(mock(GraphWorkspaceController.class),
                RecentWorkspaceList.empty());

            assertThat(first.getKey()).isEqualTo(OpenGraphWorkspaceAction.KEY);
            assertThat(second.getKey()).isEqualTo(OpenGraphWorkspaceAction.KEY);
        }
    }

    private static RecentWorkspaceList recentList(final AtomicReference<String> persisted, final Path... paths) {
        RecentWorkspaceList list = new RecentWorkspaceList("", persisted::set);
        for (Path path : paths) {
            list.record(path);
        }
        return list;
    }

    private static String formattedText(final org.mockito.invocation.InvocationOnMock invocation) {
        final Object[] invocationArguments = invocation.getRawArguments();
        final Object[] formatArguments = invocationArguments[1] instanceof Object[]
            ? (Object[]) invocationArguments[1]
            : new Object[0];
        return invocationArguments[0] + java.util.Arrays.toString(formatArguments);
    }
}
