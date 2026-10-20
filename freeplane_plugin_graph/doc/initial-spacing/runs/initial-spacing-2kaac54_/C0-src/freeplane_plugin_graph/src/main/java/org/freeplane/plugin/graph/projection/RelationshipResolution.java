package org.freeplane.plugin.graph.projection;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.freeplane.plugin.graph.workspace.model.GraphRelationshipRecord;
import org.freeplane.plugin.graph.workspace.model.RelationshipId;

public final class RelationshipResolution {
    private final GraphRelationshipRecord relationship;
    private final RelationshipStatus status;
    private final Optional<ProjectedEndpointKey> source;
    private final Optional<ProjectedEndpointKey> target;
    private final Set<RecoverableReason> recoverableReasons;

    private RelationshipResolution(final GraphRelationshipRecord relationship, final RelationshipStatus status,
            final Optional<ProjectedEndpointKey> source, final Optional<ProjectedEndpointKey> target,
            final Set<RecoverableReason> recoverableReasons) {
        this.relationship = Objects.requireNonNull(relationship, "relationship");
        this.status = Objects.requireNonNull(status, "status");
        this.source = copyOptional(source, "source");
        this.target = copyOptional(target, "target");
        this.recoverableReasons = copyReasons(recoverableReasons);
        validate();
    }

    public static RelationshipResolution of(final GraphRelationshipRecord relationship,
            final RelationshipStatus status, final Optional<ProjectedEndpointKey> source,
            final Optional<ProjectedEndpointKey> target, final Set<RecoverableReason> recoverableReasons) {
        return new RelationshipResolution(relationship, status, source, target, recoverableReasons);
    }

    public GraphRelationshipRecord relationship() {
        return relationship;
    }

    public RelationshipId relationshipId() {
        return relationship.id();
    }

    public RelationshipStatus status() {
        return status;
    }

    public Optional<ProjectedEndpointKey> source() {
        return source;
    }

    public Optional<ProjectedEndpointKey> target() {
        return target;
    }

    public Set<RecoverableReason> recoverableReasons() {
        return recoverableReasons;
    }

    private static Optional<ProjectedEndpointKey> copyOptional(final Optional<ProjectedEndpointKey> value,
            final String name) {
        return Objects.requireNonNull(value, name);
    }

    private static Set<RecoverableReason> copyReasons(final Set<RecoverableReason> values) {
        Objects.requireNonNull(values, "recoverableReasons");
        final EnumSet<RecoverableReason> copy = EnumSet.noneOf(RecoverableReason.class);
        for (final RecoverableReason value : values) {
            copy.add(Objects.requireNonNull(value, "recoverableReasons entry"));
        }
        return Collections.unmodifiableSet(copy);
    }

    private void validate() {
        if (source.isPresent() && !relationship.source().mapReferenceId().equals(source.get().mapReferenceId())) {
            throw new IllegalArgumentException("Resolved source must belong to the relationship source map");
        }
        if (target.isPresent() && !relationship.target().mapReferenceId().equals(target.get().mapReferenceId())) {
            throw new IllegalArgumentException("Resolved target must belong to the relationship target map");
        }
        if (status == RelationshipStatus.ACTIVE) {
            if (!source.isPresent() || !target.isPresent() || !recoverableReasons.isEmpty()) {
                throw new IllegalArgumentException("Active relationships require both endpoints and no reasons");
            }
            return;
        }
        if (status == RelationshipStatus.UNRESOLVED_RECOVERABLE) {
            if (source.isPresent() && target.isPresent() || recoverableReasons.isEmpty()) {
                throw new IllegalArgumentException("Recoverable relationships require an absent endpoint and a reason");
            }
            return;
        }
        if (status == RelationshipStatus.UNRESOLVED_MISSING_NODE) {
            if (source.isPresent() && target.isPresent() || !recoverableReasons.isEmpty()) {
                throw new IllegalArgumentException("Missing relationships require an absent endpoint and no reasons");
            }
            return;
        }
        throw new IllegalArgumentException("Unknown relationship status");
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RelationshipResolution)) {
            return false;
        }
        final RelationshipResolution that = (RelationshipResolution) other;
        return relationship.equals(that.relationship) && status == that.status && source.equals(that.source)
            && target.equals(that.target) && recoverableReasons.equals(that.recoverableReasons);
    }

    @Override
    public int hashCode() {
        return Objects.hash(relationship, status, source, target, recoverableReasons);
    }

    @Override
    public String toString() {
        return "RelationshipResolution{" + "relationshipId=" + relationship.id() + ", status=" + status
            + ", source=" + source + ", target=" + target + ", recoverableReasons=" + recoverableReasons
            + '}';
    }
}
