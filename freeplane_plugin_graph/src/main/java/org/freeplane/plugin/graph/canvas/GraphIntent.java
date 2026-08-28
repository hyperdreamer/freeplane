package org.freeplane.plugin.graph.canvas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.freeplane.plugin.graph.projection.ContributorKey;
import org.freeplane.plugin.graph.projection.ProjectedEdgeKey;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.workspace.model.RelationshipDirection;

public abstract class GraphIntent {
    protected GraphIntent() {
    }

    private static <T> T require(final T value, final String name) {
        return Objects.requireNonNull(value, name);
    }

    private static double requireFinite(final double value, final String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        return value;
    }

    public static final class OpenSourceNode extends GraphIntent {
        private final ProjectedEndpointKey endpoint;

        public OpenSourceNode(final ProjectedEndpointKey endpoint) {
            this.endpoint = require(endpoint, "endpoint");
        }

        public static OpenSourceNode of(final ProjectedEndpointKey endpoint) {
            return new OpenSourceNode(endpoint);
        }

        public ProjectedEndpointKey endpoint() {
            return endpoint;
        }

        public ProjectedEndpointKey key() {
            return endpoint;
        }

        @Override
        public boolean equals(final Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof OpenSourceNode)) {
                return false;
            }
            final OpenSourceNode other = (OpenSourceNode) object;
            return endpoint.equals(other.endpoint);
        }

        @Override
        public int hashCode() {
            return endpoint.hashCode();
        }
    }

    public static final class RevealSourceNode extends GraphIntent {
        private final ProjectedEndpointKey endpoint;

        public RevealSourceNode(final ProjectedEndpointKey endpoint) {
            this.endpoint = require(endpoint, "endpoint");
        }

        public static RevealSourceNode of(final ProjectedEndpointKey endpoint) {
            return new RevealSourceNode(endpoint);
        }

        public ProjectedEndpointKey endpoint() {
            return endpoint;
        }

        public ProjectedEndpointKey key() {
            return endpoint;
        }

        @Override
        public boolean equals(final Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof RevealSourceNode)) {
                return false;
            }
            final RevealSourceNode other = (RevealSourceNode) object;
            return endpoint.equals(other.endpoint);
        }

        @Override
        public int hashCode() {
            return endpoint.hashCode();
        }
    }

    public static final class Pin extends GraphIntent {
        private final ProjectedNodeKey node;
        private final double worldX;
        private final double worldY;

        public Pin(final ProjectedNodeKey node, final double worldX, final double worldY) {
            this.node = require(node, "node");
            this.worldX = requireFinite(worldX, "worldX");
            this.worldY = requireFinite(worldY, "worldY");
        }

        public static Pin of(final ProjectedNodeKey node, final double worldX, final double worldY) {
            return new Pin(node, worldX, worldY);
        }

        public ProjectedNodeKey node() {
            return node;
        }

        public ProjectedNodeKey nodeKey() {
            return node;
        }

        public double worldX() {
            return worldX;
        }

        public double worldY() {
            return worldY;
        }

        @Override
        public boolean equals(final Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof Pin)) {
                return false;
            }
            final Pin other = (Pin) object;
            return node.equals(other.node) && Double.compare(worldX, other.worldX) == 0
                && Double.compare(worldY, other.worldY) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(node, worldX, worldY);
        }
    }

    public static final class Unpin extends GraphIntent {
        private final ProjectedNodeKey node;

        public Unpin(final ProjectedNodeKey node) {
            this.node = require(node, "node");
        }

        public static Unpin of(final ProjectedNodeKey node) {
            return new Unpin(node);
        }

        public ProjectedNodeKey node() {
            return node;
        }

        public ProjectedNodeKey nodeKey() {
            return node;
        }

        @Override
        public boolean equals(final Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof Unpin)) {
                return false;
            }
            return node.equals(((Unpin) object).node);
        }

        @Override
        public int hashCode() {
            return node.hashCode();
        }
    }

    public static final class UnpinAll extends GraphIntent {
        public UnpinAll() {
        }

        public static UnpinAll of() {
            return new UnpinAll();
        }

        @Override
        public boolean equals(final Object object) {
            return object instanceof UnpinAll;
        }

        @Override
        public int hashCode() {
            return UnpinAll.class.hashCode();
        }
    }

    public static final class Connect extends GraphIntent {
        private final ProjectedEndpointKey source;
        private final ProjectedEndpointKey target;
        private final RelationshipDirection direction;

        public Connect(final ProjectedEndpointKey source, final ProjectedEndpointKey target,
                final RelationshipDirection direction) {
            this.source = require(source, "source");
            this.target = require(target, "target");
            this.direction = require(direction, "direction");
            if (source.equals(target)) {
                throw new IllegalArgumentException("source and target must differ");
            }
        }

        public static Connect of(final ProjectedEndpointKey source, final ProjectedEndpointKey target,
                final RelationshipDirection direction) {
            return new Connect(source, target, direction);
        }

        public ProjectedEndpointKey source() {
            return source;
        }

        public ProjectedEndpointKey target() {
            return target;
        }

        public RelationshipDirection direction() {
            return direction;
        }

        @Override
        public boolean equals(final Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof Connect)) {
                return false;
            }
            final Connect other = (Connect) object;
            return source.equals(other.source) && target.equals(other.target)
                && direction == other.direction;
        }

        @Override
        public int hashCode() {
            return Objects.hash(source, target, direction);
        }
    }

    public static final class InspectEdge extends GraphIntent {
        private final ProjectedEdgeKey edge;

        public InspectEdge(final ProjectedEdgeKey edge) {
            this.edge = require(edge, "edge");
        }

        public static InspectEdge of(final ProjectedEdgeKey edge) {
            return new InspectEdge(edge);
        }

        public ProjectedEdgeKey edge() {
            return edge;
        }

        public ProjectedEdgeKey edgeKey() {
            return edge;
        }

        @Override
        public boolean equals(final Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof InspectEdge)) {
                return false;
            }
            return edge.equals(((InspectEdge) object).edge);
        }

        @Override
        public int hashCode() {
            return edge.hashCode();
        }
    }

    public static final class DeleteContributor extends GraphIntent {
        private final ContributorKey contributor;

        public DeleteContributor(final ContributorKey contributor) {
            this.contributor = require(contributor, "contributor");
        }

        public static DeleteContributor of(final ContributorKey contributor) {
            return new DeleteContributor(contributor);
        }

        public ContributorKey contributor() {
            return contributor;
        }

        public ContributorKey key() {
            return contributor;
        }

        @Override
        public boolean equals(final Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof DeleteContributor)) {
                return false;
            }
            return contributor.equals(((DeleteContributor) object).contributor);
        }

        @Override
        public int hashCode() {
            return contributor.hashCode();
        }
    }

    public static final class DeleteAllContributors extends GraphIntent {
        private final ProjectedEdgeKey edge;
        private final List<ContributorKey> contributors;

        public DeleteAllContributors(final ProjectedEdgeKey edge,
                final List<ContributorKey> contributors) {
            this.edge = require(edge, "edge");
            Objects.requireNonNull(contributors, "contributors");
            final List<ContributorKey> copy = new ArrayList<ContributorKey>(contributors.size());
            for (final ContributorKey contributor : contributors) {
                copy.add(require(contributor, "contributors entry"));
            }
            this.contributors = Collections.unmodifiableList(copy);
        }

        public static DeleteAllContributors of(final ProjectedEdgeKey edge,
                final List<ContributorKey> contributors) {
            return new DeleteAllContributors(edge, contributors);
        }

        public ProjectedEdgeKey edge() {
            return edge;
        }

        public ProjectedEdgeKey edgeKey() {
            return edge;
        }

        public List<ContributorKey> contributors() {
            return contributors;
        }

        @Override
        public boolean equals(final Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof DeleteAllContributors)) {
                return false;
            }
            final DeleteAllContributors other = (DeleteAllContributors) object;
            return edge.equals(other.edge) && contributors.equals(other.contributors);
        }

        @Override
        public int hashCode() {
            return Objects.hash(edge, contributors);
        }
    }

    public static final class ChangeSelection extends GraphIntent {
        private final Optional<ProjectedEndpointKey> selection;

        public ChangeSelection(final Optional<ProjectedEndpointKey> selection) {
            this.selection = require(selection, "selection");
            if (selection.isPresent()) {
                require(selection.get(), "selection value");
            }
        }

        public static ChangeSelection of(final Optional<ProjectedEndpointKey> selection) {
            return new ChangeSelection(selection);
        }

        public Optional<ProjectedEndpointKey> selection() {
            return selection;
        }

        @Override
        public boolean equals(final Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof ChangeSelection)) {
                return false;
            }
            return selection.equals(((ChangeSelection) object).selection);
        }

        @Override
        public int hashCode() {
            return selection.hashCode();
        }
    }
}
