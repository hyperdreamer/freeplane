package org.freeplane.plugin.graph.workspace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.freeplane.plugin.graph.workspace.model.MapReferenceId;

public final class GraphCommandResult {
    public enum Status {
        APPLIED,
        NO_OP,
        REJECTED
    }

    private static final Comparator<MapReferenceId> MAP_ID_ORDER = new Comparator<MapReferenceId>() {
        @Override
        public int compare(final MapReferenceId first, final MapReferenceId second) {
            return first.value().toString().compareTo(second.value().toString());
        }
    };

    private final Status status;
    private final String messageKey;
    private final List<Object> messageArguments;
    private final Set<MapReferenceId> dirtySourceMaps;
    private final boolean editorViewActivated;
    private final WorkspaceIdentityChange identityChange;

    private GraphCommandResult(final Status status, final String messageKey, final List<Object> messageArguments,
            final Set<MapReferenceId> dirtySourceMaps, final boolean editorViewActivated,
            final WorkspaceIdentityChange identityChange) {
        this.status = Objects.requireNonNull(status, "status");
        this.messageKey = Objects.requireNonNull(messageKey, "messageKey");
        this.messageArguments = copyArguments(messageArguments);
        this.dirtySourceMaps = copyMapIds(dirtySourceMaps);
        this.editorViewActivated = editorViewActivated;
        this.identityChange = identityChange;
    }

    public static GraphCommandResult from(final WorkspaceTransition transition) {
        final WorkspaceTransition value = Objects.requireNonNull(transition, "transition");
        return new GraphCommandResult(Status.valueOf(value.status().name()), value.messageKey(),
            value.messageArguments(), Collections.<MapReferenceId>emptySet(), false, null);
    }

    public GraphCommandResult withDirtySourceMaps(final Set<MapReferenceId> maps) {
        return new GraphCommandResult(status, messageKey, messageArguments, maps, editorViewActivated, identityChange);
    }

    public GraphCommandResult withEditorViewActivated(final boolean value) {
        return new GraphCommandResult(status, messageKey, messageArguments, dirtySourceMaps, value, identityChange);
    }

    public GraphCommandResult withIdentityChange(final WorkspaceIdentityChange change) {
        return new GraphCommandResult(status, messageKey, messageArguments, dirtySourceMaps, editorViewActivated,
            Objects.requireNonNull(change, "change"));
    }

    public Status status() {
        return status;
    }

    public String messageKey() {
        return messageKey;
    }

    public List<Object> messageArguments() {
        return messageArguments;
    }

    public Set<MapReferenceId> dirtySourceMaps() {
        return dirtySourceMaps;
    }

    public boolean editorViewActivated() {
        return editorViewActivated;
    }

    public Optional<WorkspaceIdentityChange> identityChange() {
        return Optional.ofNullable(identityChange);
    }

    private static List<Object> copyArguments(final List<Object> arguments) {
        final List<Object> copy = new ArrayList<Object>(Objects.requireNonNull(arguments, "messageArguments"));
        return Collections.unmodifiableList(copy);
    }

    private static Set<MapReferenceId> copyMapIds(final Set<MapReferenceId> maps) {
        final List<MapReferenceId> sorted = new ArrayList<MapReferenceId>(Objects.requireNonNull(maps, "maps"));
        for (final MapReferenceId map : sorted) {
            Objects.requireNonNull(map, "map");
        }
        Collections.sort(sorted, MAP_ID_ORDER);
        return Collections.unmodifiableSet(new LinkedHashSet<MapReferenceId>(sorted));
    }
}
