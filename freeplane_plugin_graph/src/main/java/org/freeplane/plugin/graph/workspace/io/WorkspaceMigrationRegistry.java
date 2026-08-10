package org.freeplane.plugin.graph.workspace.io;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.freeplane.plugin.graph.workspace.model.WorkspaceDocument;

public final class WorkspaceMigrationRegistry {
    private final Map<Integer, WorkspaceMigration> migrationsBySourceVersion;

    public WorkspaceMigrationRegistry(final List<WorkspaceMigration> migrations) {
        Objects.requireNonNull(migrations, "migrations");
        final Map<Integer, WorkspaceMigration> values = new TreeMap<Integer, WorkspaceMigration>();
        for (final WorkspaceMigration migration : migrations) {
            final WorkspaceMigration nonNullMigration = Objects.requireNonNull(migration, "migration");
            final int fromVersion = nonNullMigration.fromVersion();
            final int toVersion = nonNullMigration.toVersion();
            if (fromVersion <= 0 || toVersion <= fromVersion) {
                throw new IllegalArgumentException("Migrations must advance between positive format versions");
            }
            if (values.put(fromVersion, nonNullMigration) != null) {
                throw new IllegalArgumentException("Only one migration may start from a format version");
            }
        }
        migrationsBySourceVersion = Collections.unmodifiableMap(values);
    }

    WorkspaceDocument migrate(final WorkspaceDocument source, final int sourceVersion, final int targetVersion) {
        Objects.requireNonNull(source, "source");
        if (sourceVersion <= 0 || targetVersion <= 0 || sourceVersion > targetVersion) {
            throw new IllegalArgumentException("Migration versions must advance from source to target");
        }

        WorkspaceDocument migrated = source;
        int currentVersion = sourceVersion;
        while (currentVersion < targetVersion) {
            final WorkspaceMigration migration = migrationsBySourceVersion.get(currentVersion);
            if (migration == null) {
                throw new WorkspaceFormatException(
                    "No complete workspace migration chain from version " + sourceVersion + " to " + targetVersion,
                    new IllegalArgumentException("Missing migration from version " + currentVersion));
            }
            if (migration.toVersion() > targetVersion) {
                throw new WorkspaceFormatException(
                    "No complete workspace migration chain from version " + sourceVersion + " to " + targetVersion,
                    new IllegalArgumentException("Migration from version " + currentVersion
                        + " overshoots version " + targetVersion));
            }
            migrated = Objects.requireNonNull(migration.migrate(migrated), "migrated document");
            currentVersion = migration.toVersion();
        }
        return migrated;
    }
}
