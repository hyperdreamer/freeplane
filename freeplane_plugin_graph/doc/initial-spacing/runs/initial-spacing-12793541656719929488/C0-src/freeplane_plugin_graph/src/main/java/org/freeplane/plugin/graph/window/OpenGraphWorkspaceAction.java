package org.freeplane.plugin.graph.window;

import java.awt.event.ActionEvent;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.ui.menubuilders.generic.UserRole;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.mode.Controller;
import org.freeplane.plugin.graph.control.GraphWorkspaceController;
import org.freeplane.plugin.graph.workspace.RecentWorkspaceList;

public final class OpenGraphWorkspaceAction extends AFreeplaneAction {
    public static final String KEY = "OpenGraphWorkspaceAction";
    private static final long serialVersionUID = 1L;

    private final GraphWorkspaceController applicationController;
    private final RecentWorkspaceList recentWorkspaces;
    private final Supplier<Path> pathChooser;
    private final Consumer<String> messageSink;

    public OpenGraphWorkspaceAction(final GraphWorkspaceController applicationController) {
        this(applicationController, RecentWorkspaceList.empty());
    }

    public OpenGraphWorkspaceAction(final GraphWorkspaceController applicationController,
            final RecentWorkspaceList recentWorkspaces) {
        this(applicationController, recentWorkspaces, defaultMessageSink());
    }

    public OpenGraphWorkspaceAction(final GraphWorkspaceController applicationController,
            final RecentWorkspaceList recentWorkspaces, final Consumer<String> messageSink) {
        this(applicationController, recentWorkspaces, GraphWorkspaceWindow::chooseWorkspacePath, messageSink);
    }

    OpenGraphWorkspaceAction(final GraphWorkspaceController applicationController,
            final RecentWorkspaceList recentWorkspaces, final Supplier<Path> pathChooser,
            final Consumer<String> messageSink) {
        super(KEY);
        this.applicationController = Objects.requireNonNull(applicationController, "applicationController");
        this.recentWorkspaces = Objects.requireNonNull(recentWorkspaces, "recentWorkspaces");
        this.pathChooser = Objects.requireNonNull(pathChooser, "pathChooser");
        this.messageSink = Objects.requireNonNull(messageSink, "messageSink");
    }

    OpenGraphWorkspaceAction(final GraphWorkspaceController applicationController,
            final Supplier<Path> pathChooser) {
        this(applicationController, RecentWorkspaceList.empty(), pathChooser, defaultMessageSink());
    }

    private static Consumer<String> defaultMessageSink() {
        return message -> Controller.getCurrentController().getViewController().out(message);
    }

    @Override
    public void actionPerformed(final ActionEvent event) {
        final Optional<Path> recent = recentWorkspaces.mostRecentExisting();
        if (recent.isPresent()) {
            try {
                applicationController.openExisting(recent.get());
                return;
            }
            catch (RuntimeException failure) {
                report("graph_workspace.recent_workspaces.open_failed", recent.get());
            }
        }
        final Path chosen = pathChooser.get();
        if (chosen != null) {
            applicationController.open(chosen);
        }
    }

    private void report(final String messageKey, final Object... arguments) {
        messageSink.accept(TextUtils.format(messageKey, arguments));
    }

    @Override
    public void afterMapChange(final UserRole userRole) {
    }
}
