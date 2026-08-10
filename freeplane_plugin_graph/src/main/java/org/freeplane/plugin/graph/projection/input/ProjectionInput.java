package org.freeplane.plugin.graph.projection.input;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.freeplane.plugin.graph.workspace.model.MapReference;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.WorkspaceDocument;

public final class ProjectionInput {
    private final long generation;
    private final WorkspaceDocument workspace;
    private final List<MapSnapshot> maps;
    private final Map<MapReferenceId, MapAvailability> availability;

    private ProjectionInput(final long generation, final WorkspaceDocument workspace, final List<MapSnapshot> maps,
            final Map<MapReferenceId, MapAvailability> availability) {
        if (generation < 0) {
            throw new IllegalArgumentException("Generation must be nonnegative");
        }
        this.generation = generation;
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.maps = copyMaps(maps);
        this.availability = copyAvailability(this.workspace, availability);
        validateSnapshots(this.workspace, this.maps, this.availability);
    }

    public static ProjectionInput of(final long generation, final WorkspaceDocument workspace,
            final List<MapSnapshot> maps, final Map<MapReferenceId, MapAvailability> availability) {
        return new ProjectionInput(generation, workspace, maps, availability);
    }

    public long generation() {
        return generation;
    }

    public WorkspaceDocument workspace() {
        return workspace;
    }

    public List<MapSnapshot> maps() {
        return maps;
    }

    public Map<MapReferenceId, MapAvailability> availability() {
        return availability;
    }

    private static List<MapSnapshot> copyMaps(final List<MapSnapshot> values) {
        Objects.requireNonNull(values, "maps");
        final List<MapSnapshot> copy = new ArrayList<MapSnapshot>(values.size());
        for (final MapSnapshot value : values) {
            copy.add(Objects.requireNonNull(value, "maps entry"));
        }
        return Collections.unmodifiableList(copy);
    }

    private static Map<MapReferenceId, MapAvailability> copyAvailability(final WorkspaceDocument workspace,
            final Map<MapReferenceId, MapAvailability> values) {
        Objects.requireNonNull(values, "availability");
        final Set<MapReferenceId> registeredIds = new HashSet<MapReferenceId>();
        for (final MapReference registration : workspace.maps()) {
            registeredIds.add(registration.id());
        }
        if (values.size() != registeredIds.size()) {
            throw new IllegalArgumentException("Availability must contain exactly the registered maps");
        }
        for (final Map.Entry<MapReferenceId, MapAvailability> entry : values.entrySet()) {
            final MapReferenceId mapReferenceId = Objects.requireNonNull(entry.getKey(), "availability map ID");
            Objects.requireNonNull(entry.getValue(), "availability value");
            if (!registeredIds.contains(mapReferenceId)) {
                throw new IllegalArgumentException("Availability must not contain unregistered maps");
            }
        }

        final Map<MapReferenceId, MapAvailability> ordered =
            new LinkedHashMap<MapReferenceId, MapAvailability>();
        for (final MapReference registration : workspace.maps()) {
            final MapAvailability state = values.get(registration.id());
            if (state == null) {
                throw new IllegalArgumentException("Availability must contain every registered map");
            }
            if (!registration.active() && state != MapAvailability.INACTIVE) {
                throw new IllegalArgumentException("Inactive registrations must be inactive");
            }
            if (registration.active() && state == MapAvailability.INACTIVE) {
                throw new IllegalArgumentException("Active registrations must not be inactive");
            }
            ordered.put(registration.id(), state);
        }
        return Collections.unmodifiableMap(ordered);
    }

    private static void validateSnapshots(final WorkspaceDocument workspace, final List<MapSnapshot> snapshots,
            final Map<MapReferenceId, MapAvailability> availability) {
        final Set<MapReferenceId> registeredIds = new HashSet<MapReferenceId>();
        for (final MapReference registration : workspace.maps()) {
            registeredIds.add(registration.id());
        }
        final Set<MapReferenceId> snapshotIds = new HashSet<MapReferenceId>();
        final Set<Integer> workspaceOrders = new HashSet<Integer>();
        for (final MapSnapshot snapshot : snapshots) {
            if (!registeredIds.contains(snapshot.mapReferenceId())) {
                throw new IllegalArgumentException("Snapshots must reference registered maps");
            }
            if (!snapshotIds.add(snapshot.mapReferenceId())) {
                throw new IllegalArgumentException("Snapshot map IDs must be unique");
            }
            if (snapshot.workspaceOrder() <= 0 || !workspaceOrders.add(Integer.valueOf(snapshot.workspaceOrder()))) {
                throw new IllegalArgumentException("Snapshot workspace orders must be positive and unique");
            }
        }
        for (final MapReference registration : workspace.maps()) {
            if (availability.get(registration.id()) == MapAvailability.AVAILABLE
                    && !snapshotIds.contains(registration.id())) {
                throw new IllegalArgumentException("Available registrations must have one snapshot");
            }
        }
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProjectionInput)) {
            return false;
        }
        final ProjectionInput that = (ProjectionInput) other;
        return generation == that.generation && workspace.equals(that.workspace) && maps.equals(that.maps)
            && availability.equals(that.availability);
    }

    @Override
    public int hashCode() {
        return Objects.hash(generation, workspace, maps, availability);
    }

    @Override
    public String toString() {
        return "ProjectionInput{" + "generation=" + generation + ", mapCount=" + maps.size()
            + ", availability=" + availability + '}';
    }
}
