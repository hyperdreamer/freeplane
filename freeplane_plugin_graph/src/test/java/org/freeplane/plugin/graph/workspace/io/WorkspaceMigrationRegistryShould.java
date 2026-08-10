package org.freeplane.plugin.graph.workspace.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.freeplane.plugin.graph.workspace.model.Viewport;
import org.freeplane.plugin.graph.workspace.model.WorkspaceDocument;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;
import org.junit.Test;

public class WorkspaceMigrationRegistryShould {
    @Test
    public void composeDistinctMigrationResultsInACompleteChain() {
        List<Integer> appliedVersions = new ArrayList<Integer>();
        List<WorkspaceDocument> migrationSources = new ArrayList<WorkspaceDocument>();
        WorkspaceDocument source = WorkspaceDocument.createVersion1(
            WorkspaceId.of("00000000-0000-0000-0000-000000000100"));
        WorkspaceDocument firstResult = source.toBuilder()
            .viewport(Viewport.of(1, 0, 1, Collections.emptyList()))
            .build();
        WorkspaceDocument finalResult = firstResult.toBuilder()
            .viewport(Viewport.of(2, 0, 1, Collections.emptyList()))
            .build();
        WorkspaceMigrationRegistry registry = new WorkspaceMigrationRegistry(Arrays.asList(
            migration(1, 2, appliedVersions, migrationSources, firstResult),
            migration(2, 3, appliedVersions, migrationSources, finalResult)));

        WorkspaceDocument result = registry.migrate(source, 1, 3);

        assertThat(firstResult).isNotSameAs(source);
        assertThat(finalResult).isNotSameAs(firstResult);
        assertThat(result).isSameAs(finalResult);
        assertThat(appliedVersions).containsExactly(1, 2);
        assertThat(migrationSources).hasSize(2);
        assertThat(migrationSources.get(0)).isSameAs(source);
        assertThat(migrationSources.get(1)).isSameAs(firstResult);
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

    private static WorkspaceMigration migration(final int fromVersion, final int toVersion,
            final List<Integer> appliedVersions, final List<WorkspaceDocument> migrationSources,
            final WorkspaceDocument result) {
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
                migrationSources.add(source);
                return result;
            }
        };
    }
}
