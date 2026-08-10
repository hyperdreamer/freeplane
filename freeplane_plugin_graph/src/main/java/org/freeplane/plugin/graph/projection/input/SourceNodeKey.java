package org.freeplane.plugin.graph.projection.input;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;

public final class SourceNodeKey {
    private final MapReferenceId mapReferenceId;
    private final NodeReference persistedReference;
    private final List<Integer> structuralPath;

    private SourceNodeKey(final MapReferenceId mapReferenceId, final NodeReference persistedReference,
            final List<Integer> structuralPath) {
        this.mapReferenceId = Objects.requireNonNull(mapReferenceId, "mapReferenceId");
        this.persistedReference = persistedReference;
        this.structuralPath = structuralPath;
    }

    public static SourceNodeKey persisted(final NodeReference reference) {
        final NodeReference persistedReference = Objects.requireNonNull(reference, "reference");
        return new SourceNodeKey(persistedReference.mapReferenceId(), persistedReference,
            Collections.<Integer>emptyList());
    }

    public static SourceNodeKey transientPath(final MapReferenceId map, final List<Integer> structuralPath) {
        final MapReferenceId mapReferenceId = Objects.requireNonNull(map, "map");
        Objects.requireNonNull(structuralPath, "structuralPath");
        final List<Integer> copy = new ArrayList<Integer>(structuralPath.size());
        for (final Integer index : structuralPath) {
            final Integer childIndex = Objects.requireNonNull(index, "structuralPath entry");
            if (childIndex.intValue() < 0) {
                throw new IllegalArgumentException("Structural path indexes must be nonnegative");
            }
            copy.add(childIndex);
        }
        return new SourceNodeKey(mapReferenceId, null, Collections.unmodifiableList(copy));
    }

    public MapReferenceId mapReferenceId() {
        return mapReferenceId;
    }

    public boolean persistent() {
        return persistedReference != null;
    }

    public Optional<NodeReference> persistedReference() {
        return Optional.ofNullable(persistedReference);
    }

    public List<Integer> structuralPath() {
        return structuralPath;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SourceNodeKey)) {
            return false;
        }
        final SourceNodeKey that = (SourceNodeKey) other;
        return mapReferenceId.equals(that.mapReferenceId)
            && Objects.equals(persistedReference, that.persistedReference)
            && structuralPath.equals(that.structuralPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mapReferenceId, persistedReference, structuralPath);
    }

    @Override
    public String toString() {
        if (persistent()) {
            return "SourceNodeKey{" + "mapReferenceId=" + mapReferenceId + ", persistent=true}";
        }
        return "SourceNodeKey{" + "mapReferenceId=" + mapReferenceId + ", persistent=false"
            + ", structuralPath=" + structuralPath + '}';
    }
}
