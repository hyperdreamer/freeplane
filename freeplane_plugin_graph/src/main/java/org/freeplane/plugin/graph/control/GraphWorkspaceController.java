package org.freeplane.plugin.graph.control;

import java.nio.file.Path;

public interface GraphWorkspaceController {
    GraphWorkspaceHandle open(Path workspaceFile);
}
