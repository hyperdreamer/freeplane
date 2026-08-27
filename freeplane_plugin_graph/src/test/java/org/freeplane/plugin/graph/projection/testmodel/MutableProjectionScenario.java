package org.freeplane.plugin.graph.projection.testmodel;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.freeplane.plugin.graph.workspace.model.PinRecord;
import org.freeplane.plugin.graph.workspace.model.RelationshipDirection;
import org.freeplane.plugin.graph.workspace.model.RelationshipId;
import org.freeplane.plugin.graph.workspace.model.UnknownXml;
import org.freeplane.plugin.graph.workspace.model.WorkspaceDocument;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;

public final class MutableProjectionScenario {
    public static final WorkspaceId WORKSPACE_ID =
        WorkspaceId.of("00000000-0000-0000-0000-000000000100");
    public static final MapReferenceId MAP_ONE =
        MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    public static final MapReferenceId MAP_TWO =
        MapReferenceId.of("00000000-0000-0000-0000-000000000002");
    public static final MapReferenceId MAP_THREE =
        MapReferenceId.of("00000000-0000-0000-0000-000000000003");

    public static final SourceNodeKey A_ROOT = source(MAP_ONE, "a-root");
    public static final SourceNodeKey A_BRANCH = source(MAP_ONE, "a-branch");
    public static final SourceNodeKey A_OTHER_BRANCH = source(MAP_ONE, "a-other-branch");
    public static final SourceNodeKey A_ONE = source(MAP_ONE, "a-one");
    public static final SourceNodeKey A_TWO = source(MAP_ONE, "a-two");
    public static final SourceNodeKey A_THREE = source(MAP_ONE, "a-three");
    public static final SourceNodeKey A_FOUR = source(MAP_ONE, "a-four");
    public static final SourceNodeKey A_NEW = source(MAP_ONE, "a-new");
    public static final SourceNodeKey B_ROOT = source(MAP_TWO, "b-root");
    public static final SourceNodeKey B_ONE = source(MAP_TWO, "b-one");
    public static final SourceNodeKey C_ROOT = source(MAP_THREE, "c-root");
    public static final SourceNodeKey C_ONE = source(MAP_THREE, "c-one");

    public static final RelationshipId RELATIONSHIP_ONE =
        RelationshipId.of("10000000-0000-0000-0000-000000000101");
    public static final RelationshipId RELATIONSHIP_TWO =
        RelationshipId.of("10000000-0000-0000-0000-000000000102");

    public interface Operation {
        String category();

        String description();

        void apply(MutableProjectionScenario target);
    }

    private final Map<MapReferenceId, MapDraft> maps = new HashMap<MapReferenceId, MapDraft>();
    private final Map<RelationshipId, RelationshipDraft> relationships =
        new HashMap<RelationshipId, RelationshipDraft>();
    private final Map<NodeReference, PinDraft> pins = new HashMap<NodeReference, PinDraft>();

    private MutableProjectionScenario() {
        maps.put(MAP_ONE, mapOne());
        maps.put(MAP_TWO, mapTwo());
        maps.put(MAP_THREE, mapThree());
    }

    public static MutableProjectionScenario create() {
        return new MutableProjectionScenario();
    }

    public static List<Operation> commutativeOperations() {
        final List<Operation> operations = new ArrayList<Operation>();
        operations.add(new ScenarioOperation("map", "register map one") {
            @Override
            public void apply(final MutableProjectionScenario target) {
                target.register(MAP_ONE);
            }
        });
        operations.add(new ScenarioOperation("map", "register map two") {
            @Override
            public void apply(final MutableProjectionScenario target) {
                target.register(MAP_TWO);
            }
        });
        operations.add(new ScenarioOperation("map", "register map three") {
            @Override
            public void apply(final MutableProjectionScenario target) {
                target.register(MAP_THREE);
            }
        });
        operations.add(new ScenarioOperation("structural", "add a-new under a-branch at ordinal 1") {
            @Override
            public void apply(final MutableProjectionScenario target) {
                target.addStructuralChild();
            }
        });
        operations.add(new ScenarioOperation("group", "mark c-one as a graph group") {
            @Override
            public void apply(final MutableProjectionScenario target) {
                target.markGroup();
            }
        });
        operations.add(new ScenarioOperation("connector", "add connector occurrence 0") {
            @Override
            public void apply(final MutableProjectionScenario target) {
                target.addConnector(0, false, true, "a-one-source", "connector-zero", "a-two-target");
            }
        });
        operations.add(new ScenarioOperation("connector", "add connector occurrence 1") {
            @Override
            public void apply(final MutableProjectionScenario target) {
                target.addConnector(1, true, false, "a-one-source-two", "connector-one", "a-two-target-two");
            }
        });
        operations.add(new ScenarioOperation("relationship", "add relationship one") {
            @Override
            public void apply(final MutableProjectionScenario target) {
                target.addRelationship(RELATIONSHIP_ONE, 1, A_ONE_REFERENCE, B_ONE_REFERENCE,
                    RelationshipDirection.FORWARD);
            }
        });
        operations.add(new ScenarioOperation("relationship", "add relationship two") {
            @Override
            public void apply(final MutableProjectionScenario target) {
                target.addRelationship(RELATIONSHIP_TWO, 2, A_TWO_REFERENCE, B_ONE_REFERENCE,
                    RelationshipDirection.UNDIRECTED);
            }
        });
        operations.add(new ScenarioOperation("pin", "pin a-one") {
            @Override
            public void apply(final MutableProjectionScenario target) {
                target.addPin(A_ONE_REFERENCE, 1.25, -2.5);
            }
        });
        operations.add(new ScenarioOperation("pin", "pin b-one") {
            @Override
            public void apply(final MutableProjectionScenario target) {
                target.addPin(B_ONE_REFERENCE, -3.75, 4.5);
            }
        });
        operations.add(new ScenarioOperation("text", "set a-one full and display text") {
            @Override
            public void apply(final MutableProjectionScenario target) {
                target.setText(A_ONE, "A one final full text", "A one final display text");
            }
        });
        return Collections.unmodifiableList(operations);
    }

    private static final NodeReference A_ONE_REFERENCE = reference(MAP_ONE, "a-one");
    private static final NodeReference A_TWO_REFERENCE = reference(MAP_ONE, "a-two");
    private static final NodeReference B_ONE_REFERENCE = reference(MAP_TWO, "b-one");

    public void apply(final Operation operation) {
        Objects.requireNonNull(operation, "operation").apply(this);
    }

    public void applyAll(final List<Operation> operations) {
        Objects.requireNonNull(operations, "operations");
        for (final Operation operation : operations) {
            apply(operation);
        }
    }

    public WorkspaceDocument workspace() {
        final List<MapReference> registrations = new ArrayList<MapReference>();
        for (final MapDraft draft : maps.values()) {
            if (draft.registered) {
                registrations.add(MapReference.of(draft.id, draft.sequence, draft.uri, draft.active, draft.color,
                    Collections.<UnknownXml>emptyList()));
            }
        }
        final List<GraphRelationshipRecord> records = new ArrayList<GraphRelationshipRecord>();
        for (final RelationshipDraft draft : relationships.values()) {
            records.add(GraphRelationshipRecord.of(draft.id, draft.sequence, draft.source, draft.target,
                draft.direction, Collections.<UnknownXml>emptyList()));
        }
        final List<PinRecord> pinRecords = new ArrayList<PinRecord>();
        for (final PinDraft draft : pins.values()) {
            pinRecords.add(PinRecord.of(draft.node, draft.x, draft.y, Collections.<UnknownXml>emptyList()));
        }
        return WorkspaceDocument.createVersion1(WORKSPACE_ID).toBuilder()
            .maps(registrations)
            .relationships(records)
            .pins(pinRecords)
            .build();
    }

    public List<MapSnapshot> rebuiltSnapshots(final List<MapReferenceId> requestedOrder) {
        Objects.requireNonNull(requestedOrder, "requestedOrder");
        final List<MapSnapshot> snapshots = new ArrayList<MapSnapshot>(requestedOrder.size());
        for (final MapReferenceId id : requestedOrder) {
            final MapDraft draft = map(id);
            if (!draft.registered) {
                throw new IllegalArgumentException("Requested snapshots must be registered");
            }
            snapshots.add(buildSnapshot(draft));
        }
        return snapshots;
    }

    public ProjectionInput input(final long generation, final List<MapReferenceId> mapInsertionOrder,
            final List<MapReferenceId> availabilityInsertionOrder) {
        final WorkspaceDocument document = workspace();
        return inputForWorkspace(generation, document, rebuiltSnapshots(mapInsertionOrder),
            availabilityInsertionOrder);
    }

    public ProjectionInput inputForWorkspace(final long generation, final WorkspaceDocument workspace,
            final List<MapSnapshot> snapshots, final List<MapReferenceId> availabilityInsertionOrder) {
        Objects.requireNonNull(workspace, "workspace");
        Objects.requireNonNull(snapshots, "snapshots");
        Objects.requireNonNull(availabilityInsertionOrder, "availabilityInsertionOrder");
        final Map<MapReferenceId, MapAvailability> availability =
            new HashMap<MapReferenceId, MapAvailability>();
        for (final MapReferenceId id : availabilityInsertionOrder) {
            MapAvailability state = null;
            for (final MapReference registration : workspace.maps()) {
                if (registration.id().equals(id)) {
                    state = registration.active() ? MapAvailability.AVAILABLE : MapAvailability.INACTIVE;
                    break;
                }
            }
            if (state == null) {
                throw new IllegalArgumentException("Availability must reference a registered map");
            }
            availability.put(id, state);
        }
        return ProjectionInput.of(generation, workspace, snapshots, availability);
    }

    public static SourceNodeKey source(final MapReferenceId map, final String id) {
        return SourceNodeKey.persisted(reference(map, id));
    }

    public static NodeReference reference(final MapReferenceId map, final String id) {
        return NodeReference.of(map, PersistedNodeId.of(id));
    }

    private void register(final MapReferenceId id) {
        final MapDraft draft = map(id);
        draft.registered = true;
        draft.active = true;
    }

    private void addStructuralChild() {
        map(MAP_ONE).addChild("a-branch", "a-new", 1);
    }

    private void markGroup() {
        map(MAP_THREE).node("c-one").graphGroup = true;
    }

    private void addConnector(final int occurrence, final boolean arrowAtSource, final boolean arrowAtTarget,
            final String sourceLabel, final String middleLabel, final String targetLabel) {
        map(MAP_ONE).connectors.put(Integer.valueOf(occurrence), new ConnectorDraft(occurrence, A_ONE, A_TWO_REFERENCE,
            arrowAtSource, arrowAtTarget, sourceLabel, middleLabel, targetLabel));
    }

    private void addRelationship(final RelationshipId id, final long sequence, final NodeReference source,
            final NodeReference target, final RelationshipDirection direction) {
        relationships.put(id, new RelationshipDraft(id, sequence, source, target, direction));
    }

    private void addPin(final NodeReference node, final double x, final double y) {
        pins.put(node, new PinDraft(node, x, y));
    }

    private void setText(final SourceNodeKey key, final String fullText, final String displayText) {
        map(key.mapReferenceId()).node(key.persistedReference().get().nodeId().value())
            .setText(fullText, displayText);
    }

    private MapSnapshot buildSnapshot(final MapDraft draft) {
        final NodeSnapshot root = buildNode(draft.node(draft.rootId));
        final Set<PersistedNodeId> attachedIds = new HashSet<PersistedNodeId>();
        collectAttachedIds(root, attachedIds);
        final MapSnapshot snapshot = MapSnapshot.of(draft.id, draft.snapshotOrder, draft.name, root, attachedIds,
            false);
        final List<ConnectorSnapshot> connectors = new ArrayList<ConnectorSnapshot>();
        for (final ConnectorDraft connector : draft.connectors.values()) {
            final ConnectorDescriptor descriptor = ConnectorDescriptor.of(connector.source, connector.target,
                connector.arrowAtSource, connector.arrowAtTarget, connector.sourceLabel, connector.middleLabel,
                connector.targetLabel);
            connectors.add(ConnectorSnapshot.of(connector.occurrence, descriptor));
        }
        return snapshot.withConnectors(connectors);
    }

    private static NodeSnapshot buildNode(final NodeDraft draft) {
        final List<ChildDraft> orderedChildren = new ArrayList<ChildDraft>(draft.children.values());
        Collections.sort(orderedChildren, new Comparator<ChildDraft>() {
            @Override
            public int compare(final ChildDraft first, final ChildDraft second) {
                int result = Integer.compare(first.ordinal, second.ordinal);
                if (result != 0) {
                    return result;
                }
                return first.node.id.compareTo(second.node.id);
            }
        });
        final List<NodeSnapshot> children = new ArrayList<NodeSnapshot>(orderedChildren.size());
        for (final ChildDraft child : orderedChildren) {
            children.add(buildNode(child.node));
        }
        return NodeSnapshot.of(draft.key, SafeNodeLabel.of(draft.fullText, draft.displayText), draft.structuralLeaf,
            draft.graphGroup, draft.excluded, children);
    }

    private static void collectAttachedIds(final NodeSnapshot node, final Set<PersistedNodeId> attachedIds) {
        attachedIds.add(node.key().persistedReference().get().nodeId());
        for (final NodeSnapshot child : node.children()) {
            collectAttachedIds(child, attachedIds);
        }
    }

    private MapDraft map(final MapReferenceId id) {
        final MapDraft draft = maps.get(Objects.requireNonNull(id, "map"));
        if (draft == null) {
            throw new IllegalArgumentException("Unknown map");
        }
        return draft;
    }

    private static MapDraft mapOne() {
        final MapDraft draft = new MapDraft(MAP_ONE, 1, 30, "Map One", "maps/map-one.mm", "#4E79A7", "a-root");
        draft.addNode("a-root", false);
        draft.addNode("a-branch", false);
        draft.addNode("a-other-branch", false);
        draft.addNode("a-one", true);
        draft.addNode("a-two", true);
        draft.addNode("a-three", true);
        draft.addNode("a-four", true);
        draft.addNode("a-new", true);
        draft.addChild("a-root", "a-branch", 0);
        draft.addChild("a-root", "a-other-branch", 1);
        draft.addChild("a-branch", "a-one", 0);
        draft.addChild("a-branch", "a-two", 2);
        draft.addChild("a-other-branch", "a-three", 0);
        draft.addChild("a-other-branch", "a-four", 1);
        draft.markGroup("a-branch");
        draft.markGroup("a-other-branch");
        draft.markGroup("a-one");
        draft.markGroup("a-two");
        draft.markGroup("a-new");
        return draft;
    }

    private static MapDraft mapTwo() {
        final MapDraft draft = new MapDraft(MAP_TWO, 2, 10, "Map Two", "maps/map-two.mm", "#F28E2B", "b-root");
        draft.addNode("b-root", false);
        draft.addNode("b-one", true);
        draft.addChild("b-root", "b-one", 0);
        draft.markGroup("b-one");
        return draft;
    }

    private static MapDraft mapThree() {
        final MapDraft draft = new MapDraft(MAP_THREE, 3, 20, "Map Three", "maps/map-three.mm", "#59A14F", "c-root");
        draft.addNode("c-root", false);
        draft.addNode("c-one", true);
        draft.addChild("c-root", "c-one", 0);
        return draft;
    }

    private abstract static class ScenarioOperation implements Operation {
        private final String category;
        private final String description;

        ScenarioOperation(final String category, final String description) {
            this.category = category;
            this.description = description;
        }

        @Override
        public String category() {
            return category;
        }

        @Override
        public String description() {
            return description;
        }
    }

    private static final class MapDraft {
        private final MapReferenceId id;
        private final long sequence;
        private final int snapshotOrder;
        private final String name;
        private final URI uri;
        private final String color;
        private final String rootId;
        private final Map<String, NodeDraft> nodes = new HashMap<String, NodeDraft>();
        private final Map<Integer, ConnectorDraft> connectors = new HashMap<Integer, ConnectorDraft>();
        private boolean registered;
        private boolean active;

        MapDraft(final MapReferenceId id, final long sequence, final int snapshotOrder, final String name,
                final String uri, final String color, final String rootId) {
            this.id = id;
            this.sequence = sequence;
            this.snapshotOrder = snapshotOrder;
            this.name = name;
            this.uri = URI.create(uri);
            this.color = color;
            this.rootId = rootId;
        }

        void addNode(final String id, final boolean structuralLeaf) {
            nodes.put(id, new NodeDraft(this.id, id, structuralLeaf));
        }

        void addChild(final String parentId, final String childId, final int ordinal) {
            final NodeDraft parent = node(parentId);
            final NodeDraft child = node(childId);
            if (parent.children.containsKey(childId)) {
                throw new IllegalArgumentException("Child is already attached");
            }
            for (final ChildDraft existing : parent.children.values()) {
                if (existing.ordinal == ordinal) {
                    throw new IllegalArgumentException("Child ordinal is already used");
                }
            }
            parent.children.put(childId, new ChildDraft(ordinal, child));
        }

        void markGroup(final String id) {
            node(id).graphGroup = true;
        }

        NodeDraft node(final String id) {
            final NodeDraft draft = nodes.get(id);
            if (draft == null) {
                throw new IllegalArgumentException("Unknown node " + id);
            }
            return draft;
        }
    }

    private static final class NodeDraft {
        private final SourceNodeKey key;
        private final String id;
        private final boolean structuralLeaf;
        private final Map<String, ChildDraft> children = new HashMap<String, ChildDraft>();
        private String fullText;
        private String displayText;
        private boolean graphGroup;
        private boolean excluded;

        NodeDraft(final MapReferenceId map, final String id, final boolean structuralLeaf) {
            this.key = source(map, id);
            this.id = id;
            this.structuralLeaf = structuralLeaf;
            this.fullText = id;
            this.displayText = id;
        }

        void setText(final String fullText, final String displayText) {
            this.fullText = fullText;
            this.displayText = displayText;
        }
    }

    private static final class ChildDraft {
        private final int ordinal;
        private final NodeDraft node;

        ChildDraft(final int ordinal, final NodeDraft node) {
            this.ordinal = ordinal;
            this.node = node;
        }
    }

    private static final class ConnectorDraft {
        private final int occurrence;
        private final SourceNodeKey source;
        private final NodeReference target;
        private final boolean arrowAtSource;
        private final boolean arrowAtTarget;
        private final String sourceLabel;
        private final String middleLabel;
        private final String targetLabel;

        ConnectorDraft(final int occurrence, final SourceNodeKey source, final NodeReference target,
                final boolean arrowAtSource, final boolean arrowAtTarget, final String sourceLabel,
                final String middleLabel, final String targetLabel) {
            this.occurrence = occurrence;
            this.source = source;
            this.target = target;
            this.arrowAtSource = arrowAtSource;
            this.arrowAtTarget = arrowAtTarget;
            this.sourceLabel = sourceLabel;
            this.middleLabel = middleLabel;
            this.targetLabel = targetLabel;
        }
    }

    private static final class RelationshipDraft {
        private final RelationshipId id;
        private final long sequence;
        private final NodeReference source;
        private final NodeReference target;
        private final RelationshipDirection direction;

        RelationshipDraft(final RelationshipId id, final long sequence, final NodeReference source,
                final NodeReference target, final RelationshipDirection direction) {
            this.id = id;
            this.sequence = sequence;
            this.source = source;
            this.target = target;
            this.direction = direction;
        }
    }

    private static final class PinDraft {
        private final NodeReference node;
        private final double x;
        private final double y;

        PinDraft(final NodeReference node, final double x, final double y) {
            this.node = node;
            this.x = x;
            this.y = y;
        }
    }
}
