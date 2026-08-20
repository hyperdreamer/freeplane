package org.freeplane.plugin.graph.window;

import java.awt.event.ActionEvent;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;

import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.ui.menubuilders.generic.UserRole;
import org.freeplane.plugin.graph.control.GraphWorkspaceController;

public final class OpenGraphWorkspaceAction extends AFreeplaneAction {
    public static final String KEY = "OpenGraphWorkspaceAction";
    private static final long serialVersionUID = 1L;

    private final GraphWorkspaceController applicationController;
    private final Supplier<Path> pathChooser;

    public OpenGraphWorkspaceAction(final GraphWorkspaceController applicationController) {
        this(applicationController, GraphWorkspaceWindow::chooseWorkspacePath);
    }

    OpenGraphWorkspaceAction(final GraphWorkspaceController applicationController,
            final Supplier<Path> pathChooser) {
        super(KEY);
        this.applicationController = Objects.requireNonNull(applicationController, "applicationController");
        this.pathChooser = Objects.requireNonNull(pathChooser, "pathChooser");
    }

    @Override
    public void actionPerformed(final ActionEvent event) {
        final Path path = pathChooser.get();
        if (path != null) {
            applicationController.open(path);
        }
    }

    @Override
    public void afterMapChange(final UserRole userRole) {
    }
}
