package org.freeplane.plugin.graph.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;

import org.freeplane.plugin.graph.adapter.EdtExecutor;
import org.freeplane.plugin.graph.control.CanvasState;
import org.freeplane.plugin.graph.control.GraphUpdateCoordinator;
import org.freeplane.plugin.graph.control.OperationalStatus;
import org.freeplane.plugin.graph.projection.ContributorKey;
import org.freeplane.plugin.graph.projection.EdgeContributor;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.input.ConnectorDescriptor;
import org.freeplane.plugin.graph.workspace.GraphCommandResult;
import org.freeplane.plugin.graph.workspace.GraphWorkspaceStore;
import org.freeplane.plugin.graph.workspace.WorkspaceCommands;
import org.freeplane.plugin.graph.workspace.WorkspaceTransition;
import org.freeplane.plugin.graph.workspace.model.GraphRelationshipRecord;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.RelationshipId;
import org.freeplane.plugin.graph.workspace.model.WorkspaceDocument;

public final class DefaultContributorDeletionHandler implements ContributorDeletionHandler {
    private static final String CONTRIBUTOR_EMPTY = "graph_workspace.contributor.empty";
    private static final String CONTRIBUTOR_STALE = "graph_workspace.contributor.stale";
    private static final String CONTRIBUTOR_PENDING = "graph_workspace.contributor.pending";
    private static final String CONTRIBUTOR_UNAVAILABLE = "graph_workspace.contributor.unavailable";
    private static final String CONTRIBUTOR_NOT_FOUND = "graph_workspace.contributor.not_found";
    private static final String CONTRIBUTOR_CHANGED = "graph_workspace.contributor.changed";
    private static final String CONTRIBUTOR_DUPLICATE = "graph_workspace.contributor.duplicate";
    private static final String CONTRIBUTOR_UNEXPECTED_DESCRIPTOR =
        "graph_workspace.contributor.unexpected_descriptor";
    private static final String CONTRIBUTOR_WORKSPACE_FAILED = "graph_workspace.contributor.workspace_failed";
    private static final String CONTRIBUTOR_UNDO_PARTIAL = "graph_workspace.contributors.undo_partial";
    private static final String CONTRIBUTOR_UNDO_INCOMPLETE = "graph_workspace.contributor.undo_incomplete";
    private static final String CONTRIBUTOR_NATIVE_COMMIT_FAILED =
        "graph_workspace.contributor.native_commit_failed";
    private static final String RECOVERY_NATIVE = "native";
    private static final String RECOVERY_WORKSPACE = "workspace";
    private static final String RECOVERY_NATIVE_AND_WORKSPACE = "native_and_workspace";

    private final GraphUpdateCoordinator updates;
    private final GraphWorkspaceStore store;
    private final FreeplaneMapCommandExecutor maps;
    private final EdtExecutor edt;
    private PendingRecovery pendingRecovery;

    public DefaultContributorDeletionHandler(final GraphUpdateCoordinator updates,
            final GraphWorkspaceStore store, final FreeplaneMapCommandExecutor maps, final EdtExecutor edt) {
        this.updates = Objects.requireNonNull(updates, "updates");
        this.store = Objects.requireNonNull(store, "store");
        this.maps = Objects.requireNonNull(maps, "maps");
        this.edt = Objects.requireNonNull(edt, "edt");
    }

    @Override
    public GraphCommandResult deleteOne(final GraphCommands.DeleteContributor command) {
        return edt.call(new Callable<GraphCommandResult>() {
            @Override
            public GraphCommandResult call() {
                return deleteOneOnEdt(Objects.requireNonNull(command, "command"));
            }
        });
    }

    @Override
    public GraphCommandResult deleteAll(final GraphCommands.DeleteAllContributors command) {
        return edt.call(new Callable<GraphCommandResult>() {
            @Override
            public GraphCommandResult call() {
                return deleteAllOnEdt(Objects.requireNonNull(command, "command"));
            }
        });
    }

    private GraphCommandResult deleteOneOnEdt(final GraphCommands.DeleteContributor command) {
        final GraphCommandResult recovery = retryPendingRecovery();
        if (recovery != null) {
            return recovery;
        }
        GraphProjection displayed = updates.currentProjection();
        GraphCommandResult rejection = validateDisplayedState(displayed, command.displayedGeneration());
        if (rejection != null) {
            return rejection;
        }
        PlanResult first = buildOnePlan(displayed, command.contributor(), command.expectedConnector());
        if (first.rejection != null) {
            return first.rejection;
        }

        displayed = updates.currentProjection();
        rejection = validateDisplayedState(displayed, command.displayedGeneration());
        if (rejection != null) {
            return rejection;
        }
        final PlanResult second = buildOnePlan(displayed, command.contributor(), command.expectedConnector());
        if (second.rejection != null) {
            return second.rejection;
        }
        return execute(second.plan);
    }

    private GraphCommandResult deleteAllOnEdt(final GraphCommands.DeleteAllContributors command) {
        final GraphCommandResult recovery = retryPendingRecovery();
        if (recovery != null) {
            return recovery;
        }
        if (command.contributors().isEmpty()) {
            return rejected(CONTRIBUTOR_EMPTY);
        }
        GraphProjection displayed = updates.currentProjection();
        GraphCommandResult rejection = validateDisplayedState(displayed, command.displayedGeneration());
        if (rejection != null) {
            return rejection;
        }
        PlanResult first = buildAllPlan(displayed, command);
        if (first.rejection != null) {
            return first.rejection;
        }

        displayed = updates.currentProjection();
        rejection = validateDisplayedState(displayed, command.displayedGeneration());
        if (rejection != null) {
            return rejection;
        }
        final PlanResult second = buildAllPlan(displayed, command);
        if (second.rejection != null) {
            return second.rejection;
        }
        return execute(second.plan);
    }

    private GraphCommandResult validateDisplayedState(final GraphProjection projection,
            final long displayedGeneration) {
        final CanvasState state = updates.currentState();
        if (projection == null || state == null || state.status() == OperationalStatus.FAILED
                || state.status() == OperationalStatus.CLOSED) {
            return rejected(CONTRIBUTOR_UNAVAILABLE);
        }
        if (projection.generation() != displayedGeneration) {
            return rejected(CONTRIBUTOR_STALE, displayedGeneration, projection.generation());
        }
        if (updates.hasPendingChanges()) {
            return rejected(CONTRIBUTOR_PENDING);
        }
        return null;
    }

    private PlanResult buildOnePlan(final GraphProjection projection, final ContributorKey requested,
            final Optional<ConnectorDescriptor> expected) {
        final EdgeContributor contributor = findContributor(projection.edges(), requested);
        if (contributor == null) {
            return rejectedPlan(CONTRIBUTOR_NOT_FOUND, requested);
        }
        final Map<MapReferenceId, List<ContributorDeletionPlan.NativeEdit>> nativeEdits =
            new LinkedHashMap<MapReferenceId, List<ContributorDeletionPlan.NativeEdit>>();
        final Set<RelationshipId> relationships = new LinkedHashSet<RelationshipId>();
        final GraphCommandResult validation = addContributor(contributor, requested, expected, nativeEdits,
            relationships);
        if (validation != null) {
            return new PlanResult(null, validation);
        }
        final GraphCommandResult workspaceValidation = validateWorkspaceIds(relationships);
        if (workspaceValidation != null) {
            return new PlanResult(null, workspaceValidation);
        }
        try {
            return new PlanResult(ContributorDeletionPlan.of(nativeEdits, relationships), null);
        }
        catch (final IllegalArgumentException failure) {
            return rejectedPlan(CONTRIBUTOR_CHANGED, requested);
        }
    }

    private PlanResult buildAllPlan(final GraphProjection projection,
            final GraphCommands.DeleteAllContributors command) {
        final ProjectedEdge edge = findEdge(projection.edges(), command.edge());
        if (edge == null) {
            return rejectedPlan(CONTRIBUTOR_NOT_FOUND, command.edge());
        }
        final Set<ContributorKey> requestedKeys = new LinkedHashSet<ContributorKey>();
        for (final ContributorKey key : command.contributors()) {
            if (!requestedKeys.add(key)) {
                return rejectedPlan(CONTRIBUTOR_DUPLICATE, key);
            }
        }
        final Map<ContributorKey, EdgeContributor> current = new LinkedHashMap<ContributorKey, EdgeContributor>();
        for (final EdgeContributor contributor : edge.contributors()) {
            if (contributor == null) {
                return rejectedPlan(CONTRIBUTOR_UNAVAILABLE, command.edge());
            }
            current.put(contributor.key(), contributor);
        }
        if (!requestedKeys.equals(current.keySet())) {
            return rejectedPlan(CONTRIBUTOR_NOT_FOUND, command.edge());
        }
        for (final ContributorKey key : command.expectedConnectors().keySet()) {
            if (!requestedKeys.contains(key) || !key.isNativeConnector()) {
                return rejectedPlan(CONTRIBUTOR_UNEXPECTED_DESCRIPTOR, key);
            }
        }

        final Map<MapReferenceId, List<ContributorDeletionPlan.NativeEdit>> nativeEdits =
            new LinkedHashMap<MapReferenceId, List<ContributorDeletionPlan.NativeEdit>>();
        final Set<RelationshipId> relationships = new LinkedHashSet<RelationshipId>();
        for (final ContributorKey key : command.contributors()) {
            final EdgeContributor contributor = current.get(key);
            final GraphCommandResult validation = addContributor(contributor, key,
                Optional.ofNullable(command.expectedConnectors().get(key)), nativeEdits, relationships);
            if (validation != null) {
                return new PlanResult(null, validation);
            }
        }
        final GraphCommandResult workspaceValidation = validateWorkspaceIds(relationships);
        if (workspaceValidation != null) {
            return new PlanResult(null, workspaceValidation);
        }
        try {
            return new PlanResult(ContributorDeletionPlan.of(nativeEdits, relationships), null);
        }
        catch (final IllegalArgumentException failure) {
            return rejectedPlan(CONTRIBUTOR_CHANGED, command.edge());
        }
    }

    private GraphCommandResult addContributor(final EdgeContributor contributor, final ContributorKey requested,
            final Optional<ConnectorDescriptor> expected,
            final Map<MapReferenceId, List<ContributorDeletionPlan.NativeEdit>> nativeEdits,
            final Set<RelationshipId> relationships) {
        if (!requested.equals(contributor.key())) {
            return rejected(CONTRIBUTOR_CHANGED, requested);
        }
        if (requested.isNativeConnector()) {
            if (!expected.isPresent() || !contributor.connectorDescriptor().isPresent()
                    || !expected.get().equals(contributor.connectorDescriptor().get())) {
                return rejected(CONTRIBUTOR_CHANGED, requested);
            }
            final MapReferenceId mapId = requested.mapReferenceId().get();
            List<ContributorDeletionPlan.NativeEdit> edits = nativeEdits.get(mapId);
            if (edits == null) {
                edits = new ArrayList<ContributorDeletionPlan.NativeEdit>();
                nativeEdits.put(mapId, edits);
            }
            edits.add(ContributorDeletionPlan.NativeEdit.of(requested, expected.get()));
            return null;
        }
        if (expected.isPresent() || !contributor.graphRelationship().isPresent()) {
            return rejected(CONTRIBUTOR_CHANGED, requested);
        }
        final GraphRelationshipRecord relationship = contributor.graphRelationship().get();
        if (!requested.relationshipId().isPresent()
                || !requested.relationshipId().get().equals(relationship.id())) {
            return rejected(CONTRIBUTOR_CHANGED, requested);
        }
        relationships.add(relationship.id());
        return null;
    }

    private GraphCommandResult validateWorkspaceIds(final Set<RelationshipId> relationships) {
        if (relationships.isEmpty()) {
            return null;
        }
        final WorkspaceDocument document = store.currentDocument();
        if (document == null) {
            return rejected(CONTRIBUTOR_UNAVAILABLE);
        }
        for (final RelationshipId id : relationships) {
            boolean found = false;
            for (final GraphRelationshipRecord relationship : document.relationships()) {
                if (id.equals(relationship.id())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return rejected(CONTRIBUTOR_NOT_FOUND, id);
            }
        }
        return null;
    }

    private GraphCommandResult execute(final ContributorDeletionPlan plan) {
        final FreeplaneMapCommandExecutor.ContributorDeletionTransaction transaction;
        try {
            transaction = maps.beginContributorDeletion(plan);
        }
        catch (final RuntimeException failure) {
            return rejected(CONTRIBUTOR_UNAVAILABLE);
        }
        final GraphCommandResult nativeOutcome = transaction.outcome();
        if (nativeOutcome == null || nativeOutcome.status() == GraphCommandResult.Status.REJECTED) {
            if (isIncompleteRecovery(nativeOutcome)) {
                return retainRecovery(transaction, RECOVERY_NATIVE);
            }
            return nativeOutcome == null ? rejected(CONTRIBUTOR_UNAVAILABLE) : nativeOutcome;
        }
        if (plan.relationshipIds().isEmpty()) {
            try {
                transaction.commit();
            }
            catch (final RuntimeException failure) {
                return recoverNativeCommitFailure(transaction);
            }
            if (plan.nativeEditsByMap().size() > 1) {
                return enrichNativeResult(rejectedApplied(CONTRIBUTOR_UNDO_PARTIAL,
                    plan.nativeEditsByMap().size()), transaction);
            }
            return enrichNativeResult(nativeOutcome, transaction);
        }

        final GraphCommandResult workspaceResult;
        try {
            workspaceResult = store.execute(WorkspaceCommands.purgeRelationships(plan.relationshipIds()));
        }
        catch (final RuntimeException failure) {
            return rollbackAfterWorkspaceFailure(transaction, rejected(CONTRIBUTOR_WORKSPACE_FAILED));
        }
        if (workspaceResult == null || workspaceResult.status() != GraphCommandResult.Status.APPLIED) {
            return rollbackAfterWorkspaceFailure(transaction,
                workspaceResult == null ? rejected(CONTRIBUTOR_WORKSPACE_FAILED) : workspaceResult);
        }
        try {
            transaction.commit();
        }
        catch (final RuntimeException failure) {
            final GraphCommandResult nativeFailure = recoverNativeCommitFailure(transaction);
            if (!compensatePublishedWorkspace()) {
                final String resource = nativeFailure != null
                        && CONTRIBUTOR_UNDO_INCOMPLETE.equals(nativeFailure.messageKey())
                    ? RECOVERY_NATIVE_AND_WORKSPACE : RECOVERY_WORKSPACE;
                return incompleteRecovery(transaction, resource);
            }
            return nativeFailure;
        }
        return enrichWorkspaceResult(workspaceResult, transaction);
    }

    private GraphCommandResult rollbackAfterWorkspaceFailure(
            final FreeplaneMapCommandExecutor.ContributorDeletionTransaction transaction,
            final GraphCommandResult fallback) {
        try {
            transaction.rollback();
        }
        catch (final RuntimeException failure) {
            return incompleteRecovery(transaction, RECOVERY_NATIVE);
        }
        final GraphCommandResult outcome = transaction.outcome();
        if (isIncompleteRecovery(outcome)) {
            return retainRecovery(transaction, RECOVERY_NATIVE);
        }
        return fallback;
    }

    private GraphCommandResult recoverNativeCommitFailure(
            final FreeplaneMapCommandExecutor.ContributorDeletionTransaction transaction) {
        try {
            transaction.rollback();
        }
        catch (final RuntimeException failure) {
            return incompleteRecovery(transaction, RECOVERY_NATIVE);
        }
        final GraphCommandResult outcome = transaction.outcome();
        if (isIncompleteRecovery(outcome)) {
            return retainRecovery(transaction, RECOVERY_NATIVE);
        }
        if (outcome != null && outcome.status() == GraphCommandResult.Status.REJECTED) {
            if (CONTRIBUTOR_NATIVE_COMMIT_FAILED.equals(outcome.messageKey())) {
                return enrichNativeResult(outcome, transaction);
            }
        }
        return incompleteRecovery(transaction, RECOVERY_NATIVE);
    }

    private boolean compensatePublishedWorkspace() {
        try {
            final GraphCommandResult result = store.undo();
            return result != null && result.status() == GraphCommandResult.Status.APPLIED;
        }
        catch (final RuntimeException failure) {
            return false;
        }
    }

    private GraphCommandResult incompleteRecovery(
            final FreeplaneMapCommandExecutor.ContributorDeletionTransaction transaction,
            final String resource) {
        return retainRecovery(transaction, resource);
    }

    private GraphCommandResult retainRecovery(
            final FreeplaneMapCommandExecutor.ContributorDeletionTransaction transaction,
            final String resource) {
        pendingRecovery = new PendingRecovery(transaction, resource);
        return pendingRecovery.result(this);
    }

    private GraphCommandResult retryPendingRecovery() {
        if (pendingRecovery == null) {
            return null;
        }
        final PendingRecovery recovery = pendingRecovery;
        if (recovery.workspaceUnresolved()) {
            if (compensatePublishedWorkspace()) {
                recovery.workspaceRecovered();
            }
        }
        if (recovery.nativeUnresolved()) {
            try {
                recovery.transaction.rollback();
                final GraphCommandResult nativeOutcome = recovery.transaction.outcome();
                if (nativeOutcome != null && !isIncompleteRecovery(nativeOutcome)) {
                    recovery.nativeRecovered();
                }
            }
            catch (final RuntimeException failure) {
                // Keep the native resource pending for the next command.
            }
        }
        if (recovery.complete()) {
            pendingRecovery = null;
            return null;
        }
        return recovery.result(this);
    }

    private boolean isIncompleteRecovery(final GraphCommandResult result) {
        return result != null && CONTRIBUTOR_UNDO_INCOMPLETE.equals(result.messageKey());
    }

    private Set<MapReferenceId> dirtySourceMaps(
            final FreeplaneMapCommandExecutor.ContributorDeletionTransaction transaction) {
        final Set<MapReferenceId> dirty = transaction.dirtySourceMaps();
        return dirty == null ? Collections.<MapReferenceId>emptySet() : dirty;
    }

    private GraphCommandResult enrichNativeResult(final GraphCommandResult result,
            final FreeplaneMapCommandExecutor.ContributorDeletionTransaction transaction) {
        return result.withDirtySourceMaps(dirtySourceMaps(transaction))
            .withEditorViewActivated(transaction.editorViewActivated() || result.editorViewActivated());
    }

    private GraphCommandResult enrichWorkspaceResult(final GraphCommandResult result,
            final FreeplaneMapCommandExecutor.ContributorDeletionTransaction transaction) {
        final Set<org.freeplane.plugin.graph.workspace.model.MapReferenceId> dirty =
            new LinkedHashSet<org.freeplane.plugin.graph.workspace.model.MapReferenceId>(result.dirtySourceMaps());
        dirty.addAll(dirtySourceMaps(transaction));
        return result.withDirtySourceMaps(dirty)
            .withEditorViewActivated(result.editorViewActivated() || transaction.editorViewActivated());
    }

    private static EdgeContributor findContributor(final List<ProjectedEdge> edges, final ContributorKey key) {
        if (edges == null) {
            return null;
        }
        for (final ProjectedEdge edge : edges) {
            if (edge == null || edge.contributors() == null) {
                continue;
            }
            for (final EdgeContributor contributor : edge.contributors()) {
                if (contributor != null && key.equals(contributor.key())) {
                    return contributor;
                }
            }
        }
        return null;
    }

    private static ProjectedEdge findEdge(final List<ProjectedEdge> edges,
            final org.freeplane.plugin.graph.projection.ProjectedEdgeKey key) {
        if (edges == null) {
            return null;
        }
        for (final ProjectedEdge edge : edges) {
            if (edge != null && key.equals(edge.key())) {
                return edge;
            }
        }
        return null;
    }

    private GraphCommandResult rejected(final String messageKey, final Object... arguments) {
        return GraphCommandResult.from(WorkspaceTransition.rejected(store.currentDocument(), messageKey, arguments));
    }

    private PlanResult rejectedPlan(final String messageKey, final Object argument) {
        return new PlanResult(null, rejected(messageKey, argument));
    }

    private GraphCommandResult rejectedApplied(final String messageKey, final Object... arguments) {
        return GraphCommandResult.from(WorkspaceTransition.applied(store.currentDocument(), messageKey, arguments));
    }

    private static final class PendingRecovery {
        private final FreeplaneMapCommandExecutor.ContributorDeletionTransaction transaction;
        private boolean nativeUnresolved;
        private boolean workspaceUnresolved;

        private PendingRecovery(final FreeplaneMapCommandExecutor.ContributorDeletionTransaction transaction,
                final String resource) {
            this.transaction = Objects.requireNonNull(transaction, "transaction");
            if (RECOVERY_NATIVE.equals(resource)) {
                nativeUnresolved = true;
            }
            else if (RECOVERY_WORKSPACE.equals(resource)) {
                workspaceUnresolved = true;
            }
            else if (RECOVERY_NATIVE_AND_WORKSPACE.equals(resource)) {
                nativeUnresolved = true;
                workspaceUnresolved = true;
            }
            else {
                throw new IllegalArgumentException("Unknown recovery resource: " + resource);
            }
        }

        private boolean nativeUnresolved() {
            return nativeUnresolved;
        }

        private boolean workspaceUnresolved() {
            return workspaceUnresolved;
        }

        private void nativeRecovered() {
            nativeUnresolved = false;
        }

        private void workspaceRecovered() {
            workspaceUnresolved = false;
        }

        private boolean complete() {
            return !nativeUnresolved && !workspaceUnresolved;
        }

        private String resource() {
            if (nativeUnresolved && workspaceUnresolved) {
                return RECOVERY_NATIVE_AND_WORKSPACE;
            }
            return nativeUnresolved ? RECOVERY_NATIVE : RECOVERY_WORKSPACE;
        }

        private GraphCommandResult result(final DefaultContributorDeletionHandler handler) {
            GraphCommandResult result = handler.rejected(CONTRIBUTOR_UNDO_INCOMPLETE, resource())
                .withDirtySourceMaps(handler.dirtySourceMaps(transaction));
            return result.withEditorViewActivated(transaction.editorViewActivated());
        }
    }

    private static final class PlanResult {
        private final ContributorDeletionPlan plan;
        private final GraphCommandResult rejection;

        private PlanResult(final ContributorDeletionPlan plan, final GraphCommandResult rejection) {
            this.plan = plan;
            this.rejection = rejection;
        }
    }
}
