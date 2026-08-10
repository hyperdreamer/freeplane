package org.freeplane.plugin.graph.projection.input;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class NodeSnapshot {
    private final SourceNodeKey key;
    private final SafeNodeLabel label;
    private final boolean structuralLeaf;
    private final boolean graphGroup;
    private final boolean excluded;
    private final List<NodeSnapshot> children;

    private NodeSnapshot(final SourceNodeKey key, final SafeNodeLabel label, final boolean structuralLeaf,
            final boolean graphGroup, final boolean excluded, final List<NodeSnapshot> children) {
        this.key = Objects.requireNonNull(key, "key");
        this.label = Objects.requireNonNull(label, "label");
        this.structuralLeaf = structuralLeaf;
        this.graphGroup = graphGroup;
        this.excluded = excluded;
        this.children = copyChildren(children, key);
        if (structuralLeaf && !this.children.isEmpty()) {
            throw new IllegalArgumentException("Structural leaves must not have snapshot children");
        }
    }

    public static NodeSnapshot of(final SourceNodeKey key, final SafeNodeLabel label, final boolean structuralLeaf,
            final boolean graphGroup, final boolean excluded, final List<NodeSnapshot> children) {
        return new NodeSnapshot(key, label, structuralLeaf, graphGroup, excluded, children);
    }

    public SourceNodeKey key() {
        return key;
    }

    public SafeNodeLabel label() {
        return label;
    }

    public boolean structuralLeaf() {
        return structuralLeaf;
    }

    public boolean graphGroup() {
        return graphGroup;
    }

    public boolean excluded() {
        return excluded;
    }

    public List<NodeSnapshot> children() {
        return children;
    }

    private static List<NodeSnapshot> copyChildren(final List<NodeSnapshot> values, final SourceNodeKey parentKey) {
        Objects.requireNonNull(values, "children");
        final List<NodeSnapshot> copy = new ArrayList<NodeSnapshot>(values.size());
        for (final NodeSnapshot value : values) {
            final NodeSnapshot child = Objects.requireNonNull(value, "children entry");
            if (!parentKey.mapReferenceId().equals(child.key().mapReferenceId())) {
                throw new IllegalArgumentException("Snapshot child keys must belong to the parent map");
            }
            copy.add(child);
        }
        return Collections.unmodifiableList(copy);
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof NodeSnapshot)) {
            return false;
        }
        final NodeSnapshot that = (NodeSnapshot) other;
        return structuralLeaf == that.structuralLeaf && graphGroup == that.graphGroup && excluded == that.excluded
            && key.equals(that.key) && label.equals(that.label) && children.equals(that.children);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, label, structuralLeaf, graphGroup, excluded, children);
    }

    @Override
    public String toString() {
        return "NodeSnapshot{" + "key=" + key + ", structuralLeaf=" + structuralLeaf
            + ", graphGroup=" + graphGroup + ", excluded=" + excluded
            + ", childCount=" + children.size() + '}';
    }
}
