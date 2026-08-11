package org.freeplane.plugin.graph.projection;

import java.util.Objects;

public final class ProjectedEdgeKey implements Comparable<ProjectedEdgeKey> {
    private final ProjectedEndpointKey first;
    private final ProjectedEndpointKey second;

    private ProjectedEdgeKey(final ProjectedEndpointKey first, final ProjectedEndpointKey second) {
        this.first = Objects.requireNonNull(first, "first");
        this.second = Objects.requireNonNull(second, "second");
        if (this.first.equals(this.second)) {
            throw new IllegalArgumentException("Projected edge endpoints must be distinct");
        }
    }

    public static ProjectedEdgeKey of(final ProjectedEndpointKey first, final ProjectedEndpointKey second) {
        final ProjectedEndpointKey left = Objects.requireNonNull(first, "first");
        final ProjectedEndpointKey right = Objects.requireNonNull(second, "second");
        if (left.compareTo(right) <= 0) {
            return new ProjectedEdgeKey(left, right);
        }
        return new ProjectedEdgeKey(right, left);
    }

    public ProjectedEndpointKey first() {
        return first;
    }

    public ProjectedEndpointKey second() {
        return second;
    }

    @Override
    public int compareTo(final ProjectedEdgeKey other) {
        Objects.requireNonNull(other, "other");
        int result = first.compareTo(other.first);
        if (result != 0) {
            return result;
        }
        return second.compareTo(other.second);
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProjectedEdgeKey)) {
            return false;
        }
        final ProjectedEdgeKey that = (ProjectedEdgeKey) other;
        return first.equals(that.first) && second.equals(that.second);
    }

    @Override
    public int hashCode() {
        return 31 * first.hashCode() + second.hashCode();
    }

    @Override
    public String toString() {
        return "ProjectedEdgeKey{" + "first=" + first + ", second=" + second + '}';
    }
}
