package org.freeplane.plugin.graph.projection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import org.freeplane.plugin.graph.projection.input.ConnectorDescriptor;
import org.freeplane.plugin.graph.projection.input.ConnectorSnapshot;
import org.freeplane.plugin.graph.projection.input.MapAvailability;
import org.freeplane.plugin.graph.projection.input.MapSnapshot;
import org.freeplane.plugin.graph.projection.input.NodeSnapshot;
import org.freeplane.plugin.graph.projection.input.ProjectionInput;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.GraphRelationshipRecord;
import org.freeplane.plugin.graph.workspace.model.MapReference;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PinRecord;
import org.freeplane.plugin.graph.workspace.model.WorkspaceDocument;

public final class ProjectionEngine {
    private static final Comparator<MapSnapshot> MAP_ORDER = new Comparator<MapSnapshot>() {
        @Override
        public int compare(final MapSnapshot first, final MapSnapshot second) {
            int result = Integer.compare(first.workspaceOrder(), second.workspaceOrder());
            if (result != 0) {
                return result;
            }
            return first.mapReferenceId().value().toString().compareTo(second.mapReferenceId().value().toString());
        }
    };

    public GraphProjection projectStructure(final long generation, final WorkspaceDocument workspace,
            final List<MapSnapshot> maps) {
        final WorkspaceDocument document = Objects.requireNonNull(workspace, "workspace");
        final List<MapSnapshot> snapshots = Objects.requireNonNull(maps, "maps");
        return project(ProjectionInput.of(generation, document, snapshots,
            availabilityForStructure(document, snapshots)));
    }

    public GraphProjection project(final ProjectionInput input) {
        final ProjectionInput value = Objects.requireNonNull(input, "input");
        final List<MapSnapshot> selectedMaps = selectedAvailableMaps(value);
        final int activeRegistrationCount = activeRegistrationCount(value.workspace());
        final List<ProjectedNode> projectedNodes = new ArrayList<ProjectedNode>();
        final List<ProjectedEnclosure> projectedEnclosures = new ArrayList<ProjectedEnclosure>();
        final Map<SourceNodeKey, ProjectedEndpointKey> exactEndpoints =
            new HashMap<SourceNodeKey, ProjectedEndpointKey>();

        for (final MapSnapshot map : selectedMaps) {
            validateSafeIdentityTraversal(map);
            final NodeSnapshot root = map.root();
            if (root.excluded()) {
                continue;
            }
            projectMap(map, activeRegistrationCount, projectedNodes, projectedEnclosures, exactEndpoints);
        }

        final Map<MapReferenceId, EndpointTraversal> endpointTraversals =
            indexEndpointTraversals(selectedMaps);
        final List<RelationshipResolution> resolutions = resolveRelationships(value, endpointTraversals);
        final List<PinProjection> pins = projectPins(value.workspace(), projectedNodes);
        final List<ProjectedEdge> edges = projectEdges(selectedMaps, endpointTraversals, resolutions);
        return GraphProjection.projected(value.generation(), projectedNodes,
            projectedEnclosures, edges, resolutions, pins);
    }

    private static List<ProjectedEdge> projectEdges(final List<MapSnapshot> maps,
            final Map<MapReferenceId, EndpointTraversal> endpointTraversals,
            final List<RelationshipResolution> resolutions) {
        final Map<ProjectedEdgeKey, List<EdgeContributor>> grouped =
            new TreeMap<ProjectedEdgeKey, List<EdgeContributor>>();
        for (final MapSnapshot map : maps) {
            final EndpointTraversal traversal = endpointTraversals.get(map.mapReferenceId());
            if (traversal == null) {
                throw new IllegalArgumentException("Available maps must have endpoint traversals");
            }
            for (final ConnectorSnapshot connector : map.connectors()) {
                final ConnectorDescriptor descriptor = connector.descriptor();
                final EndpointOutcome source = traversal.outcomeFor(descriptor.source());
                final EndpointOutcome target = traversal.outcomeFor(SourceNodeKey.persisted(descriptor.target()));
                if (source == null || target == null || source.endpoint == null || target.endpoint == null
                        || source.endpoint.equals(target.endpoint)) {
                    continue;
                }
                final EdgeContributor contributor = EdgeContributor.nativeConnector(connector, source.endpoint,
                    target.endpoint);
                addContributor(grouped, contributor);
            }
        }
        for (final RelationshipResolution resolution : resolutions) {
            if (resolution.status() != RelationshipStatus.ACTIVE || !resolution.source().isPresent()
                    || !resolution.target().isPresent()
                    || resolution.source().get().equals(resolution.target().get())) {
                continue;
            }
            final EdgeContributor contributor = EdgeContributor.graphRelationship(resolution.relationship(),
                resolution.source().get(), resolution.target().get());
            addContributor(grouped, contributor);
        }
        final List<ProjectedEdge> edges = new ArrayList<ProjectedEdge>(grouped.size());
        for (final Map.Entry<ProjectedEdgeKey, List<EdgeContributor>> entry : grouped.entrySet()) {
            edges.add(ProjectedEdge.of(entry.getKey(), entry.getValue()));
        }
        return edges;
    }

    private static void addContributor(final Map<ProjectedEdgeKey, List<EdgeContributor>> grouped,
            final EdgeContributor contributor) {
        final ProjectedEdgeKey key = ProjectedEdgeKey.of(contributor.projectedSource(),
            contributor.projectedTarget());
        List<EdgeContributor> contributors = grouped.get(key);
        if (contributors == null) {
            contributors = new ArrayList<EdgeContributor>();
            grouped.put(key, contributors);
        }
        contributors.add(contributor);
    }

    private static Map<MapReferenceId, MapAvailability> availabilityForStructure(final WorkspaceDocument workspace,
            final List<MapSnapshot> snapshots) {
        final Set<MapReferenceId> snapshotIds = new HashSet<MapReferenceId>();
        for (final MapSnapshot value : snapshots) {
            snapshotIds.add(Objects.requireNonNull(value, "maps entry").mapReferenceId());
        }
        final Map<MapReferenceId, MapAvailability> availability =
            new HashMap<MapReferenceId, MapAvailability>();
        for (final MapReference registration : workspace.maps()) {
            if (!registration.active()) {
                availability.put(registration.id(), MapAvailability.INACTIVE);
            }
            else if (snapshotIds.contains(registration.id())) {
                availability.put(registration.id(), MapAvailability.AVAILABLE);
            }
            else {
                availability.put(registration.id(), MapAvailability.LOADING);
            }
        }
        return availability;
    }

    private static List<MapSnapshot> selectedAvailableMaps(final ProjectionInput input) {
        final List<MapSnapshot> selected = new ArrayList<MapSnapshot>();
        for (final MapSnapshot snapshot : input.maps()) {
            if (input.availability().get(snapshot.mapReferenceId()) == MapAvailability.AVAILABLE) {
                selected.add(snapshot);
            }
        }
        Collections.sort(selected, MAP_ORDER);
        return selected;
    }

    private static int activeRegistrationCount(final WorkspaceDocument workspace) {
        int count = 0;
        for (final MapReference registration : workspace.maps()) {
            if (registration.active()) {
                count++;
            }
        }
        return count;
    }

    private static void validateSafeIdentityTraversal(final MapSnapshot map) {
        validateSafeIdentityTraversal(map.root(), map.mapReferenceId(), new HashSet<SourceNodeKey>());
    }

    private static void validateSafeIdentityTraversal(final NodeSnapshot snapshot, final MapReferenceId mapReferenceId,
            final Set<SourceNodeKey> sourceKeys) {
        final NodeSnapshot node = Objects.requireNonNull(snapshot, "snapshot node");
        final SourceNodeKey source = Objects.requireNonNull(node.key(), "snapshot node key");
        if (!mapReferenceId.equals(source.mapReferenceId())) {
            throw new IllegalArgumentException("Snapshot keys must belong to the map");
        }
        if (!sourceKeys.add(source)) {
            throw new IllegalArgumentException("Snapshot source keys must be unique");
        }
        if (node.structuralLeaf() && !node.children().isEmpty()) {
            throw new IllegalArgumentException("Structural leaves must not have snapshot children");
        }
        for (final NodeSnapshot child : node.children()) {
            validateSafeIdentityTraversal(child, mapReferenceId, sourceKeys);
        }
    }

    private static void projectMap(final MapSnapshot map, final int activeRegistrationCount,
            final List<ProjectedNode> projectedNodes, final List<ProjectedEnclosure> projectedEnclosures,
            final Map<SourceNodeKey, ProjectedEndpointKey> exactEndpoints) {
        final NodeSnapshot root = map.root();
        final EnclosureKey rootKey = EnclosureKey.of(root.key());
        final BoundaryTier rootTier = boundaryTier(0, activeRegistrationCount);
        final HullBuilder rootBuilder = new HullBuilder(rootKey, root.label(), map.mapName(), rootTier, true);

        for (final NodeSnapshot child : root.children()) {
            projectSourceNode(child, map.mapName(), 1, activeRegistrationCount, rootBuilder,
                projectedNodes, exactEndpoints);
        }

        flattenHullTree(rootBuilder, Optional.<EnclosureHullKey>empty(), projectedEnclosures, exactEndpoints);
    }

    private static void projectSourceNode(final NodeSnapshot node, final String mapName,
            final int rawDepth, final int activeRegistrationCount, final HullBuilder nearestRetainedAncestor,
            final List<ProjectedNode> projectedNodes, final Map<SourceNodeKey, ProjectedEndpointKey> exactEndpoints) {
        if (node.excluded()) {
            return;
        }
        if (node.graphGroup()) {
            final ProjectedNodeKey nodeKey = ProjectedNodeKey.of(node.key());
            final ProjectedNode projected = ProjectedNode.of(nodeKey, node.label(), mapName, true);
            projectedNodes.add(projected);
            nearestRetainedAncestor.directNodes.add(nodeKey);
            exactEndpoints.put(node.key(), ProjectedEndpointKey.ofNode(nodeKey));
            return;
        }

        final BoundaryTier tier = boundaryTier(rawDepth, activeRegistrationCount);
        if (tier != BoundaryTier.SUPPRESSED) {
            final EnclosureKey enclosureKey = EnclosureKey.of(node.key());
            final HullBuilder currentHull = new HullBuilder(enclosureKey, node.label(), mapName, tier, false);
            for (final NodeSnapshot child : node.children()) {
                projectSourceNode(child, mapName, rawDepth + 1, activeRegistrationCount, currentHull,
                    projectedNodes, exactEndpoints);
            }
            if (currentHull.hasReachableContent()) {
                nearestRetainedAncestor.directEnclosures.add(currentHull);
            }
        }
        else {
            for (final NodeSnapshot child : node.children()) {
                projectSourceNode(child, mapName, rawDepth + 1, activeRegistrationCount, nearestRetainedAncestor,
                    projectedNodes, exactEndpoints);
            }
        }
    }

    private static void flattenHullTree(final HullBuilder builder, final Optional<EnclosureHullKey> parentHull,
            final List<ProjectedEnclosure> enclosures, final Map<SourceNodeKey, ProjectedEndpointKey> exactEndpoints) {
        final List<EnclosureKey> endpointKeys = Collections.singletonList(builder.key);
        final EnclosureHullKey hullKey = EnclosureHullKey.of(endpointKeys);
        exactEndpoints.put(builder.key.source(), ProjectedEndpointKey.ofEnclosure(builder.key));

        final List<EnclosureHullKey> childHullKeys = new ArrayList<EnclosureHullKey>(builder.directEnclosures.size());
        for (final HullBuilder child : builder.directEnclosures) {
            childHullKeys.add(EnclosureHullKey.of(Collections.singletonList(child.key)));
        }

        final ProjectedEnclosure enclosure = ProjectedEnclosure.of(hullKey, endpointKeys,
            Collections.singletonList(builder.label), builder.mapName, parentHull,
            builder.directNodes, childHullKeys, builder.mapRoot, builder.boundaryTier);
        enclosures.add(enclosure);

        for (final HullBuilder child : builder.directEnclosures) {
            flattenHullTree(child, Optional.of(hullKey), enclosures, exactEndpoints);
        }
    }

    private static final class HullBuilder {
        private final EnclosureKey key;
        private final SafeNodeLabel label;
        private final String mapName;
        private final BoundaryTier boundaryTier;
        private final boolean mapRoot;
        private final List<ProjectedNodeKey> directNodes = new ArrayList<ProjectedNodeKey>();
        private final List<HullBuilder> directEnclosures = new ArrayList<HullBuilder>();

        private HullBuilder(final EnclosureKey key, final SafeNodeLabel label, final String mapName,
                final BoundaryTier boundaryTier, final boolean mapRoot) {
            this.key = key;
            this.label = label;
            this.mapName = mapName;
            this.boundaryTier = boundaryTier;
            this.mapRoot = mapRoot;
        }

        private boolean hasReachableContent() {
            return !directNodes.isEmpty() || !directEnclosures.isEmpty();
        }
    }

    private static BoundaryTier boundaryTier(final int boundaryDepth,
            final int activeRegistrationCount) {
        if (activeRegistrationCount <= 1) {
            if (boundaryDepth == 1) {
                return BoundaryTier.EMPHATIC;
            }
            if (boundaryDepth == 2) {
                return BoundaryTier.SUBTLE;
            }
            return BoundaryTier.SUPPRESSED;
        }
        if (boundaryDepth == 0) {
            return BoundaryTier.EMPHATIC;
        }
        if (boundaryDepth == 1) {
            return BoundaryTier.SUBTLE;
        }
        return BoundaryTier.SUPPRESSED;
    }

    private static Map<MapReferenceId, EndpointTraversal> indexEndpointTraversals(final List<MapSnapshot> maps) {
        final Map<MapReferenceId, EndpointTraversal> result =
            new HashMap<MapReferenceId, EndpointTraversal>();
        for (final MapSnapshot map : maps) {
            final EndpointTraversal traversal = new EndpointTraversal(map);
            traverseEndpoints(map.root(), traversal, null);
            if (result.put(map.mapReferenceId(), traversal) != null) {
                throw new IllegalArgumentException("Available map snapshots must be unique");
            }
        }
        return result;
    }

    private static void traverseEndpoints(final NodeSnapshot node, final EndpointTraversal traversal,
            final ProjectedEndpointKey activeGroupMarker) {
        if (node.excluded()) {
            recordExcludedSubtree(node, traversal);
            return;
        }
        if (node.graphGroup()) {
            final ProjectedEndpointKey groupEndpoint =
                ProjectedEndpointKey.ofNode(ProjectedNodeKey.of(node.key()));
            traversal.recordEndpoint(node.key(), groupEndpoint);
            for (final NodeSnapshot child : node.children()) {
                traverseEndpoints(child, traversal, groupEndpoint);
            }
            return;
        }
        if (activeGroupMarker != null) {
            traversal.recordEndpoint(node.key(), activeGroupMarker);
            for (final NodeSnapshot child : node.children()) {
                traverseEndpoints(child, traversal, activeGroupMarker);
            }
            return;
        }
        for (final NodeSnapshot child : node.children()) {
            traverseEndpoints(child, traversal, null);
        }
    }

    private static void recordExcludedSubtree(final NodeSnapshot node, final EndpointTraversal traversal) {
        traversal.recordExcluded(node.key());
        for (final NodeSnapshot child : node.children()) {
            recordExcludedSubtree(child, traversal);
        }
    }

    private static List<RelationshipResolution> resolveRelationships(final ProjectionInput input,
            final Map<MapReferenceId, EndpointTraversal> endpointTraversals) {
        final List<RelationshipResolution> resolutions = new ArrayList<RelationshipResolution>();
        for (final GraphRelationshipRecord relationship : input.workspace().relationships()) {
            final EndpointResult source = resolveEndpoint(input, relationship.source(), endpointTraversals);
            final EndpointResult target = resolveEndpoint(input, relationship.target(), endpointTraversals);
            final Set<RecoverableReason> reasons = EnumSet.noneOf(RecoverableReason.class);
            if (source.recoverableReason != null) {
                reasons.add(source.recoverableReason);
            }
            if (target.recoverableReason != null) {
                reasons.add(target.recoverableReason);
            }
            final RelationshipStatus status;
            if (source.endpoint != null && target.endpoint != null) {
                status = RelationshipStatus.ACTIVE;
            }
            else if (!reasons.isEmpty()) {
                status = RelationshipStatus.UNRESOLVED_RECOVERABLE;
            }
            else {
                status = RelationshipStatus.UNRESOLVED_MISSING_NODE;
            }
            resolutions.add(RelationshipResolution.of(relationship, status, Optional.ofNullable(source.endpoint),
                Optional.ofNullable(target.endpoint), reasons));
        }
        return resolutions;
    }

    private static EndpointResult resolveEndpoint(final ProjectionInput input, final NodeReference source,
            final Map<MapReferenceId, EndpointTraversal> endpointTraversals) {
        final MapAvailability availability = input.availability().get(source.mapReferenceId());
        if (availability == MapAvailability.INACTIVE) {
            return EndpointResult.recoverable(RecoverableReason.MAP_INACTIVE);
        }
        if (availability == MapAvailability.LOADING) {
            return EndpointResult.recoverable(RecoverableReason.MAP_LOADING);
        }
        if (availability == MapAvailability.MISSING) {
            return EndpointResult.recoverable(RecoverableReason.MAP_MISSING);
        }
        if (availability == MapAvailability.UNREADABLE) {
            return EndpointResult.recoverable(RecoverableReason.MAP_UNREADABLE);
        }
        if (availability == MapAvailability.PASSWORD_REQUIRED) {
            return EndpointResult.recoverable(RecoverableReason.MAP_PASSWORD_REQUIRED);
        }
        if (availability == MapAvailability.RELOAD_REQUIRED) {
            return EndpointResult.recoverable(RecoverableReason.MAP_RELOAD_REQUIRED);
        }
        if (availability != MapAvailability.AVAILABLE) {
            throw new IllegalArgumentException("Registered map availability is required");
        }
        final EndpointTraversal traversal = endpointTraversals.get(source.mapReferenceId());
        if (traversal == null) {
            throw new IllegalArgumentException("Available maps must have endpoint traversals");
        }
        final EndpointOutcome outcome = traversal.outcomeFor(source);
        if (outcome != null) {
            return outcome.endpoint != null ? EndpointResult.resolved(outcome.endpoint)
                : EndpointResult.recoverable(outcome.recoverableReason);
        }
        if (traversal.snapshot.attachedPersistentIds().contains(source.nodeId())
                || traversal.snapshot.hasInaccessibleBranch()) {
            return EndpointResult.recoverable(RecoverableReason.NODE_INACCESSIBLE);
        }
        return EndpointResult.missing();
    }

    private static List<PinProjection> projectPins(final WorkspaceDocument workspace,
            final List<ProjectedNode> nodes) {
        final Map<SourceNodeKey, ProjectedNodeKey> nodeKeys = new HashMap<SourceNodeKey, ProjectedNodeKey>();
        for (final ProjectedNode node : nodes) {
            nodeKeys.put(node.source(), node.key());
        }
        final List<PinProjection> pins = new ArrayList<PinProjection>();
        for (final PinRecord pin : workspace.pins()) {
            final ProjectedNodeKey nodeKey = nodeKeys.get(SourceNodeKey.persisted(pin.node()));
            pins.add(nodeKey == null ? PinProjection.dormant(pin) : PinProjection.active(pin, nodeKey));
        }
        return pins;
    }

    private static final class EndpointTraversal {
        private final MapSnapshot snapshot;
        private final Map<SourceNodeKey, EndpointOutcome> outcomes =
            new HashMap<SourceNodeKey, EndpointOutcome>();

        private EndpointTraversal(final MapSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        private void recordEndpoint(final SourceNodeKey source, final ProjectedEndpointKey endpoint) {
            put(source, EndpointOutcome.resolved(endpoint));
        }

        private void recordExcluded(final SourceNodeKey source) {
            put(source, EndpointOutcome.recoverable(RecoverableReason.NODE_EXCLUDED));
        }

        private EndpointOutcome outcomeFor(final NodeReference source) {
            return outcomeFor(SourceNodeKey.persisted(source));
        }

        private EndpointOutcome outcomeFor(final SourceNodeKey source) {
            return outcomes.get(Objects.requireNonNull(source, "source"));
        }

        private void put(final SourceNodeKey source, final EndpointOutcome outcome) {
            if (outcomes.put(source, outcome) != null) {
                throw new IllegalArgumentException("Snapshot source keys must be unique");
            }
        }
    }

    private static final class EndpointOutcome {
        private final ProjectedEndpointKey endpoint;
        private final RecoverableReason recoverableReason;

        private EndpointOutcome(final ProjectedEndpointKey endpoint, final RecoverableReason recoverableReason) {
            this.endpoint = endpoint;
            this.recoverableReason = recoverableReason;
        }

        private static EndpointOutcome resolved(final ProjectedEndpointKey endpoint) {
            return new EndpointOutcome(Objects.requireNonNull(endpoint, "endpoint"), null);
        }

        private static EndpointOutcome recoverable(final RecoverableReason reason) {
            return new EndpointOutcome(null, Objects.requireNonNull(reason, "reason"));
        }
    }

    private static final class EndpointResult {
        private final ProjectedEndpointKey endpoint;
        private final RecoverableReason recoverableReason;

        private EndpointResult(final ProjectedEndpointKey endpoint, final RecoverableReason recoverableReason) {
            this.endpoint = endpoint;
            this.recoverableReason = recoverableReason;
        }

        private static EndpointResult resolved(final ProjectedEndpointKey endpoint) {
            return new EndpointResult(Objects.requireNonNull(endpoint, "endpoint"), null);
        }

        private static EndpointResult recoverable(final RecoverableReason reason) {
            return new EndpointResult(null, Objects.requireNonNull(reason, "reason"));
        }

        private static EndpointResult missing() {
            return new EndpointResult(null, null);
        }
    }
}
