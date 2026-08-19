package org.freeplane.plugin.graph.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.freeplane.plugin.graph.projection.ContributorKey;
import org.freeplane.plugin.graph.projection.input.ConnectorDescriptor;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.RelationshipId;

final class ContributorDeletionPlan {
    private static final Comparator<MapReferenceId> MAP_ID_ORDER = new Comparator<MapReferenceId>() {
        @Override
        public int compare(final MapReferenceId first, final MapReferenceId second) {
            return first.value().toString().compareTo(second.value().toString());
        }
    };

    static final class NativeEdit {
        private final ContributorKey key;
        private final ConnectorDescriptor descriptor;

        private NativeEdit(final ContributorKey key, final ConnectorDescriptor descriptor) {
            this.key = Objects.requireNonNull(key, "key");
            this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        }

        static NativeEdit of(final ContributorKey key, final ConnectorDescriptor descriptor) {
            return new NativeEdit(key, descriptor);
        }

        ContributorKey key() {
            return key;
        }

        ConnectorDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public boolean equals(final Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof NativeEdit)) {
                return false;
            }
            final NativeEdit that = (NativeEdit) other;
            return key.equals(that.key) && descriptor.equals(that.descriptor);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, descriptor);
        }

        @Override
        public String toString() {
            return "NativeEdit{" + "key=" + key + ", descriptor=" + descriptor + '}';
        }
    }

    private final Map<MapReferenceId, List<NativeEdit>> nativeEditsByMap;
    private final Set<RelationshipId> relationshipIds;

    private ContributorDeletionPlan(final Map<MapReferenceId, List<NativeEdit>> nativeEditsByMap,
            final Set<RelationshipId> relationshipIds) {
        this.nativeEditsByMap = copyNativeEdits(nativeEditsByMap);
        this.relationshipIds = copyRelationshipIds(relationshipIds);
    }

    static ContributorDeletionPlan of(final Map<MapReferenceId, List<NativeEdit>> nativeEditsByMap,
            final Set<RelationshipId> relationshipIds) {
        return new ContributorDeletionPlan(nativeEditsByMap, relationshipIds);
    }

    static ContributorDeletionPlan of(final List<NativeEdit> nativeEdits,
            final Set<RelationshipId> relationshipIds) {
        Objects.requireNonNull(nativeEdits, "nativeEdits");
        final Map<MapReferenceId, List<NativeEdit>> grouped =
            new LinkedHashMap<MapReferenceId, List<NativeEdit>>();
        for (final NativeEdit edit : nativeEdits) {
            final NativeEdit value = Objects.requireNonNull(edit, "nativeEdits entry");
            final ContributorKey key = value.key();
            if (!key.isNativeConnector() || !key.mapReferenceId().isPresent()) {
                throw new IllegalArgumentException("Native edits require native connector keys");
            }
            List<NativeEdit> group = grouped.get(key.mapReferenceId().get());
            if (group == null) {
                group = new ArrayList<NativeEdit>();
                grouped.put(key.mapReferenceId().get(), group);
            }
            group.add(value);
        }
        return new ContributorDeletionPlan(grouped, relationshipIds);
    }

    Map<MapReferenceId, List<NativeEdit>> nativeEditsByMap() {
        return nativeEditsByMap;
    }

    List<MapReferenceId> nativeMapIds() {
        final List<MapReferenceId> mapIds = new ArrayList<MapReferenceId>(nativeEditsByMap.keySet());
        Collections.sort(mapIds, MAP_ID_ORDER);
        return Collections.unmodifiableList(mapIds);
    }

    Map<MapReferenceId, List<NativeEdit>> nativeEdits() {
        return nativeEditsByMap;
    }

    List<NativeEdit> nativeEditsFor(final MapReferenceId mapReferenceId) {
        final List<NativeEdit> edits = nativeEditsByMap.get(Objects.requireNonNull(mapReferenceId, "mapReferenceId"));
        return edits == null ? Collections.<NativeEdit>emptyList() : edits;
    }

    Set<RelationshipId> relationshipIds() {
        return relationshipIds;
    }

    Set<RelationshipId> workspaceRelationshipIds() {
        return relationshipIds;
    }

    boolean hasNativeEdits() {
        return !nativeEditsByMap.isEmpty();
    }

    boolean hasWorkspaceEdits() {
        return !relationshipIds.isEmpty();
    }

    boolean isEmpty() {
        return !hasNativeEdits() && !hasWorkspaceEdits();
    }

    private static Map<MapReferenceId, List<NativeEdit>> copyNativeEdits(
            final Map<MapReferenceId, List<NativeEdit>> values) {
        Objects.requireNonNull(values, "nativeEditsByMap");
        final Map<MapReferenceId, List<NativeEdit>> copy =
            new LinkedHashMap<MapReferenceId, List<NativeEdit>>();
        final Set<ContributorKey> keys = new HashSet<ContributorKey>();
        for (final Map.Entry<MapReferenceId, List<NativeEdit>> entry : values.entrySet()) {
            final MapReferenceId mapId = Objects.requireNonNull(entry.getKey(), "native edit map ID");
            final List<NativeEdit> edits = Objects.requireNonNull(entry.getValue(), "native edits");
            final List<NativeEdit> group = new ArrayList<NativeEdit>(edits.size());
            if (edits.isEmpty()) {
                throw new IllegalArgumentException("Native edit groups must not be empty");
            }
            for (final NativeEdit edit : edits) {
                final NativeEdit value = Objects.requireNonNull(edit, "native edits entry");
                final ContributorKey key = value.key();
                final ConnectorDescriptor descriptor = value.descriptor();
                if (!key.isNativeConnector() || !key.mapReferenceId().isPresent()
                        || !key.source().isPresent()) {
                    throw new IllegalArgumentException("Native edits require native connector keys");
                }
                if (!mapId.equals(key.mapReferenceId().get())
                        || !mapId.equals(descriptor.source().mapReferenceId())
                        || !key.source().get().equals(descriptor.source())) {
                    throw new IllegalArgumentException("Native edit map and source descriptors must agree");
                }
                if (!keys.add(key)) {
                    throw new IllegalArgumentException("Native edit keys must be unique");
                }
                group.add(value);
            }
            copy.put(mapId, Collections.unmodifiableList(group));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Set<RelationshipId> copyRelationshipIds(final Set<RelationshipId> values) {
        Objects.requireNonNull(values, "relationshipIds");
        final Set<RelationshipId> copy = new LinkedHashSet<RelationshipId>();
        for (final RelationshipId value : values) {
            copy.add(Objects.requireNonNull(value, "relationship ID"));
        }
        return Collections.unmodifiableSet(copy);
    }
}
