package org.freeplane.plugin.graph.projection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;

public final class ProjectedEnclosure {
    private final EnclosureHullKey hullKey;
    private final List<EnclosureKey> endpointKeys;
    private final List<SafeNodeLabel> labels;
    private final String mapName;
    private final Optional<EnclosureHullKey> parentHull;
    private final List<ProjectedNodeKey> directNodes;
    private final List<EnclosureHullKey> directEnclosures;
    private final boolean mapRoot;
    private final BoundaryTier boundaryTier;

    private ProjectedEnclosure(final EnclosureHullKey hullKey, final List<EnclosureKey> endpointKeys,
            final List<SafeNodeLabel> labels, final String mapName, final Optional<EnclosureHullKey> parentHull,
            final List<ProjectedNodeKey> directNodes, final List<EnclosureHullKey> directEnclosures,
            final boolean mapRoot, final BoundaryTier boundaryTier) {
        this.hullKey = Objects.requireNonNull(hullKey, "hullKey");
        this.endpointKeys = copyValues(endpointKeys, "endpointKeys");
        if (!hullKey.endpointKeys().equals(this.endpointKeys)) {
            throw new IllegalArgumentException("Enclosure endpoint keys must match the hull key");
        }
        this.labels = copyValues(labels, "labels");
        if (this.endpointKeys.size() != this.labels.size()) {
            throw new IllegalArgumentException("Enclosure endpoint keys and labels must have equal sizes");
        }
        this.mapName = requireMapName(mapName);
        this.parentHull = Objects.requireNonNull(parentHull, "parentHull");
        validateParent(this.parentHull, this.hullKey);
        this.directNodes = copyDirectNodes(directNodes, hullKey.mapReferenceId());
        this.directEnclosures = copyDirectEnclosures(directEnclosures, hullKey.mapReferenceId());
        this.mapRoot = mapRoot;
        this.boundaryTier = Objects.requireNonNull(boundaryTier, "boundaryTier");
    }

    public static ProjectedEnclosure of(final EnclosureHullKey hullKey, final List<EnclosureKey> endpointKeys,
            final List<SafeNodeLabel> labels, final String mapName, final Optional<EnclosureHullKey> parentHull,
            final List<ProjectedNodeKey> directNodes, final List<EnclosureHullKey> directEnclosures,
            final boolean mapRoot, final BoundaryTier boundaryTier) {
        return new ProjectedEnclosure(hullKey, endpointKeys, labels, mapName, parentHull, directNodes,
            directEnclosures, mapRoot, boundaryTier);
    }

    public EnclosureHullKey hullKey() {
        return hullKey;
    }

    public List<EnclosureKey> endpointKeys() {
        return endpointKeys;
    }

    public List<SafeNodeLabel> labels() {
        return labels;
    }

    public MapReferenceId mapReferenceId() {
        return hullKey.mapReferenceId();
    }

    public String mapName() {
        return mapName;
    }

    public Optional<EnclosureHullKey> parentHull() {
        return parentHull;
    }

    public List<ProjectedNodeKey> directNodes() {
        return directNodes;
    }

    public List<EnclosureHullKey> directEnclosures() {
        return directEnclosures;
    }

    public boolean mapRoot() {
        return mapRoot;
    }

    public BoundaryTier boundaryTier() {
        return boundaryTier;
    }

    private static String requireMapName(final String value) {
        Objects.requireNonNull(value, "mapName");
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Map name must not be empty");
        }
        return value;
    }

    private static void validateParent(final Optional<EnclosureHullKey> parent,
            final EnclosureHullKey hullKey) {
        if (parent.isPresent()) {
            final EnclosureHullKey parentHull = parent.get();
            if (parentHull.equals(hullKey)) {
                throw new IllegalArgumentException("An enclosure hull cannot be its own parent");
            }
            if (!parentHull.mapReferenceId().equals(hullKey.mapReferenceId())) {
                throw new IllegalArgumentException("Enclosure parent and child must belong to one map");
            }
        }
    }

    private static List<ProjectedNodeKey> copyDirectNodes(final List<ProjectedNodeKey> values,
            final MapReferenceId mapReferenceId) {
        final List<ProjectedNodeKey> copy = copyValues(values, "directNodes");
        final Set<ProjectedNodeKey> unique = new HashSet<ProjectedNodeKey>();
        for (final ProjectedNodeKey value : copy) {
            if (!mapReferenceId.equals(value.mapReferenceId())) {
                throw new IllegalArgumentException("Direct nodes must belong to the enclosure map");
            }
            if (!unique.add(value)) {
                throw new IllegalArgumentException("Direct nodes must be unique");
            }
        }
        return copy;
    }

    private static List<EnclosureHullKey> copyDirectEnclosures(final List<EnclosureHullKey> values,
            final MapReferenceId mapReferenceId) {
        final List<EnclosureHullKey> copy = copyValues(values, "directEnclosures");
        final Set<EnclosureHullKey> unique = new HashSet<EnclosureHullKey>();
        for (final EnclosureHullKey value : copy) {
            if (!mapReferenceId.equals(value.mapReferenceId())) {
                throw new IllegalArgumentException("Direct enclosures must belong to the enclosure map");
            }
            if (!unique.add(value)) {
                throw new IllegalArgumentException("Direct enclosures must be unique");
            }
        }
        return copy;
    }

    private static <T> List<T> copyValues(final List<T> values, final String name) {
        Objects.requireNonNull(values, name);
        final List<T> copy = new ArrayList<T>(values.size());
        for (final T value : values) {
            copy.add(Objects.requireNonNull(value, name + " entry"));
        }
        return Collections.unmodifiableList(copy);
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProjectedEnclosure)) {
            return false;
        }
        final ProjectedEnclosure that = (ProjectedEnclosure) other;
        return mapRoot == that.mapRoot && boundaryTier == that.boundaryTier && hullKey.equals(that.hullKey)
            && endpointKeys.equals(that.endpointKeys)
            && labels.equals(that.labels) && mapName.equals(that.mapName) && parentHull.equals(that.parentHull)
            && directNodes.equals(that.directNodes) && directEnclosures.equals(that.directEnclosures);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hullKey, endpointKeys, labels, mapName, parentHull, directNodes, directEnclosures,
            mapRoot, boundaryTier);
    }

    @Override
    public String toString() {
        return "ProjectedEnclosure{" + "hullKey=" + hullKey + ", parentHull=" + parentHull
            + ", directNodes=" + directNodes + ", directEnclosures=" + directEnclosures
            + ", mapRoot=" + mapRoot + ", boundaryTier=" + boundaryTier + '}';
    }
}
