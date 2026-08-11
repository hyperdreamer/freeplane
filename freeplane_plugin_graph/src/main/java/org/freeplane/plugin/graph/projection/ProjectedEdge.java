package org.freeplane.plugin.graph.projection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ProjectedEdge {
    private final ProjectedEdgeKey key;
    private final List<EdgeContributor> contributors;
    private final boolean arrowAtFirst;
    private final boolean arrowAtSecond;

    private ProjectedEdge(final ProjectedEdgeKey key, final List<EdgeContributor> contributors) {
        this.key = Objects.requireNonNull(key, "key");
        this.contributors = copyContributors(contributors, key);
        boolean firstArrow = false;
        boolean secondArrow = false;
        for (final EdgeContributor contributor : this.contributors) {
            if (contributor.projectedSource().equals(key.first())) {
                firstArrow = firstArrow || contributor.arrowAtSource();
                secondArrow = secondArrow || contributor.arrowAtTarget();
            }
            else {
                firstArrow = firstArrow || contributor.arrowAtTarget();
                secondArrow = secondArrow || contributor.arrowAtSource();
            }
        }
        this.arrowAtFirst = firstArrow;
        this.arrowAtSecond = secondArrow;
    }

    public static ProjectedEdge of(final ProjectedEdgeKey key, final List<EdgeContributor> contributors) {
        return new ProjectedEdge(key, contributors);
    }

    public ProjectedEdgeKey key() {
        return key;
    }

    public ProjectedEndpointKey first() {
        return key.first();
    }

    public ProjectedEndpointKey second() {
        return key.second();
    }

    public List<EdgeContributor> contributors() {
        return contributors;
    }

    public boolean arrowAtFirst() {
        return arrowAtFirst;
    }

    public boolean arrowAtSecond() {
        return arrowAtSecond;
    }

    public int contributorCount() {
        return contributors.size();
    }

    public boolean hasMultiplicityCue() {
        return contributorCount() > 1;
    }

    private static List<EdgeContributor> copyContributors(final List<EdgeContributor> values,
            final ProjectedEdgeKey key) {
        Objects.requireNonNull(values, "contributors");
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Projected edges must have a contributor");
        }
        final List<EdgeContributor> copy = new ArrayList<EdgeContributor>(values.size());
        final Set<ContributorKey> keys = new HashSet<ContributorKey>();
        for (final EdgeContributor value : values) {
            final EdgeContributor contributor = Objects.requireNonNull(value, "contributors entry");
            if (!keys.add(contributor.key())) {
                throw new IllegalArgumentException("Projected edge contributor keys must be unique");
            }
            final boolean matches = contributor.projectedSource().equals(key.first())
                && contributor.projectedTarget().equals(key.second())
                || contributor.projectedSource().equals(key.second())
                && contributor.projectedTarget().equals(key.first());
            if (!matches) {
                throw new IllegalArgumentException("Projected edge contributor endpoints must match its key");
            }
            copy.add(contributor);
        }
        return Collections.unmodifiableList(copy);
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProjectedEdge)) {
            return false;
        }
        final ProjectedEdge that = (ProjectedEdge) other;
        return arrowAtFirst == that.arrowAtFirst && arrowAtSecond == that.arrowAtSecond
            && key.equals(that.key) && contributors.equals(that.contributors);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, contributors, arrowAtFirst, arrowAtSecond);
    }

    @Override
    public String toString() {
        return "ProjectedEdge{" + "key=" + key + ", contributorCount=" + contributors.size()
            + ", arrowAtFirst=" + arrowAtFirst + ", arrowAtSecond=" + arrowAtSecond + '}';
    }
}
