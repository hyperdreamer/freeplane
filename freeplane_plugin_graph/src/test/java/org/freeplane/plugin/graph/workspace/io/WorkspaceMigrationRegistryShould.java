package org.freeplane.plugin.graph.workspace.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.freeplane.plugin.graph.workspace.model.WorkspaceDocument;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;
import org.junit.Test;

public class WorkspaceMigrationRegistryShould {
    @Test
    public void applyEveryMigrationInACompleteChainInVersionOrder() {
        List<Integer> appliedVersions = new ArrayList<Integer>();
        WorkspaceMigrationRegistry registry = new WorkspaceMigrationRegistry(Arrays.asList(
            migration(1, 2, appliedVersions), migration(2, 3, appliedVersions)));
        WorkspaceDocument source = WorkspaceDocument.createVersion1(
            WorkspaceId.of("00000000-0000-0000-0000-000000000100"));

        WorkspaceDocument result = registry.migrate(source, 1, 3);

        assertThat(result).isSameAs(source);
        assertThat(appliedVersions).containsExactly(1, 2);
    }

    @Test
    public void rejectAnIncompleteMigrationChain() {
        WorkspaceMigrationRegistry registry = new WorkspaceMigrationRegistry(Arrays.asList(
            migration(1, 2, new ArrayList<Integer>())));
        WorkspaceDocument source = WorkspaceDocument.createVersion1(
            WorkspaceId.of("00000000-0000-0000-0000-000000000100"));

        assertThatThrownBy(() -> registry.migrate(source, 1, 3))
            .isInstanceOf(WorkspaceFormatException.class);
    }

    @Test
    public void rejectAMigrationThatOvershootsTheRequestedTargetVersion() {
        WorkspaceMigrationRegistry registry = new WorkspaceMigrationRegistry(Arrays.asList(
            migration(1, 3, new ArrayList<Integer>())));
        WorkspaceDocument source = WorkspaceDocument.createVersion1(
            WorkspaceId.of("00000000-0000-0000-0000-000000000100"));

        assertThatThrownBy(() -> registry.migrate(source, 1, 2))
            .isInstanceOf(WorkspaceFormatException.class);
    }

    private static WorkspaceMigration migration(final int fromVersion, final int toVersion,
            final List<Integer> appliedVersions) {
        return new WorkspaceMigration() {
            @Override
            public int fromVersion() {
                return fromVersion;
            }

            @Override
            public int toVersion() {
                return toVersion;
            }

            @Override
            public WorkspaceDocument migrate(WorkspaceDocument source) {
                appliedVersions.add(fromVersion);
                return source;
            }
        };
    }
}
