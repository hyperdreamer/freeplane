package org.freeplane.plugin.graph.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.freeplane.features.map.INodeDuplicator;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.ModeController;
import org.freeplane.plugin.graph.adapter.EdtExecutor;
import org.freeplane.plugin.graph.adapter.MapLease;
import org.freeplane.plugin.graph.adapter.MapOperationalState;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.GraphCommandResult;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.freeplane.plugin.graph.workspace.model.WorkspaceDocument;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;
import org.junit.Test;

public class SourceNavigationShould {
    @Test
    public void selectsAReachableSourceThroughTheTraversalResolver() {
        // Catches navigation that selects by a fabricated ID rather than the resolver's reachable node.
        Fixture fixture = new Fixture();
        MapNodes nodes = fixture.addMap("reachable");

        GraphCommandResult result = fixture.navigation.open(nodes.key);

        assertThat(result.status()).isEqualTo(GraphCommandResult.Status.APPLIED);
        assertThat(result.messageKey()).isEqualTo("graph_workspace.source.opened");
        assertThat(result.editorViewActivated()).isTrue();
        assertThat(fixture.resolver.lastKey).isEqualTo(nodes.key);
        assertThat(fixture.selected).isSameAs(nodes.node);
        assertThat(nodes.map.registryCalls()).isZero();
        assertThat(fixture.results.saveHookCalls()).isZero();
    }

    @Test
    public void rejectsUnavailableOrUnreachableSourcesWithoutSelectingAnything() {
        // Catches source navigation that selects a stale node when the lease is unavailable or resolution fails.
        Fixture fixture = new Fixture();
        MapNodes nodes = fixture.addMap("rejects");
        fixture.leases.clear();

        GraphCommandResult unavailable = fixture.navigation.open(nodes.key);

        assertRejected(unavailable, "graph_workspace.source_map.unavailable");
        assertThat(fixture.selected).isNull();

        fixture.leases.put(nodes.mapId, new TestLease(nodes.mapId, MapOperationalState.AVAILABLE));
        fixture.resolver.nodes.clear();
        GraphCommandResult notFound = fixture.navigation.open(nodes.key);

        assertRejected(notFound, "graph_workspace.source_node.not_found");
        assertThat(fixture.selected).isNull();
    }

    @Test
    public void navigatesToAnIdlessSourceWithoutAssigningAnId() {
        // Catches navigation that calls createID while resolving a transient structural source.
        Fixture fixture = new Fixture();
        MapNodes nodes = fixture.addMap("idless");
        nodes.node.setID(null);
        SourceNodeKey idless = SourceNodeKey.transientPath(nodes.mapId, Collections.singletonList(Integer.valueOf(0)));
        fixture.resolver.nodes.remove(nodes.key);
        fixture.resolver.nodes.put(idless, nodes.node);
        int registryCallsBeforeOpen = nodes.map.registryCalls();

        GraphCommandResult result = fixture.navigation.open(idless);

        assertThat(result.status()).isEqualTo(GraphCommandResult.Status.APPLIED);
        assertThat(fixture.selected).isSameAs(nodes.node);
        assertThat(nodes.node.getID()).isNull();
        assertThat(nodes.map.registryCalls()).isEqualTo(registryCallsBeforeOpen);
    }

    private static void assertRejected(GraphCommandResult result, String key) {
        assertThat(result.status()).isEqualTo(GraphCommandResult.Status.REJECTED);
        assertThat(result.messageKey()).isEqualTo(key);
    }

    private static final class Fixture {
        private final InlineEdt edt = new InlineEdt();
        private final Map<MapReferenceId, TestLease> leases = new HashMap<MapReferenceId, TestLease>();
        private final Resolver resolver = new Resolver();
        private final ModeController modeController = mock(ModeController.class);
        private final MapController mapController = mock(MapController.class);
        private final ReadOnlyResultEnvelope results = new ReadOnlyResultEnvelope();
        private final SourceNavigation navigation;
        private NodeModel selected;

        private Fixture() {
            when(modeController.getMapController()).thenReturn(mapController);
            doAnswer(invocation -> {
                selected = invocation.getArgument(0);
                return null;
            }).when(mapController).select(any(NodeModel.class));
            navigation = new SourceNavigation(new LeaseLookup(leases), modeController, edt, resolver, results);
        }

        private MapNodes addMap(String title) {
            MapReferenceId mapId = MapReferenceId.of(UUID.randomUUID());
            TrackingMapModel map = new TrackingMapModel(title);
            NodeModel root = new NodeModel("root", map);
            root.setID("ID_ROOT");
            map.setRoot(root);
            NodeModel source = new NodeModel("source", map);
            source.setID("ID_SOURCE");
            root.insert(source);
            SourceNodeKey key = SourceNodeKey.persisted(NodeReference.of(mapId, PersistedNodeId.of("ID_SOURCE")));
            leases.put(mapId, new TestLease(mapId, MapOperationalState.AVAILABLE));
            resolver.nodes.put(key, source);
            return new MapNodes(mapId, map, source, key);
        }
    }

    private static final class MapNodes {
        private final MapReferenceId mapId;
        private final TrackingMapModel map;
        private final NodeModel node;
        private final SourceNodeKey key;

        private MapNodes(MapReferenceId mapId, TrackingMapModel map, NodeModel node, SourceNodeKey key) {
            this.mapId = mapId;
            this.map = map;
            this.node = node;
            this.key = key;
        }
    }

    private static final class LeaseLookup implements FreeplaneMapCommandExecutor.MapLeaseLookup {
        private final Map<MapReferenceId, TestLease> leases;

        private LeaseLookup(Map<MapReferenceId, TestLease> leases) {
            this.leases = leases;
        }

        @Override
        public Optional<MapLease> find(MapReferenceId mapReferenceId) {
            return Optional.<MapLease>ofNullable(leases.get(mapReferenceId));
        }
    }

    private static final class Resolver implements FreeplaneMapCommandExecutor.TraversalResolver {
        private final Map<SourceNodeKey, NodeModel> nodes = new HashMap<SourceNodeKey, NodeModel>();
        private SourceNodeKey lastKey;

        @Override
        public Optional<NodeModel> resolve(MapLease lease, SourceNodeKey key) {
            lastKey = key;
            return Optional.ofNullable(nodes.get(key));
        }
    }

    private static final class ReadOnlyResultEnvelope implements FreeplaneMapCommandExecutor.ResultEnvelope {
        private final WorkspaceDocument document = WorkspaceDocument.createVersion1(WorkspaceId.of(UUID.randomUUID()));
        private int saveHookCalls;

        @Override
        public WorkspaceDocument currentDocument() {
            return document;
        }

        int saveHookCalls() {
            return saveHookCalls;
        }
    }

    private static final class TestLease implements MapLease {
        private final MapReferenceId mapId;
        private final MapOperationalState state;

        private TestLease(MapReferenceId mapId, MapOperationalState state) {
            this.mapId = mapId;
            this.state = state;
        }

        @Override
        public MapReferenceId mapReferenceId() {
            return mapId;
        }

        @Override
        public MapOperationalState state() {
            return state;
        }

        @Override
        public void close() {
        }
    }

    private static final class InlineEdt implements EdtExecutor {
        private boolean onEdt;

        @Override
        public <T> T call(Callable<T> task) {
            boolean previous = onEdt;
            onEdt = true;
            try {
                return task.call();
            }
            catch (RuntimeException failure) {
                throw failure;
            }
            catch (Exception failure) {
                throw new AssertionError("EDT task failed", failure);
            }
            finally {
                onEdt = previous;
            }
        }

        @Override
        public void execute(Runnable task) {
            task.run();
        }

        @Override
        public boolean isEdt() {
            return onEdt;
        }
    }

    private static final class TrackingMapModel extends MapModel {
        private final String title;
        private int registryCalls;

        private TrackingMapModel(String title) {
            super(new INodeDuplicator() {
                @Override
                public NodeModel duplicate(NodeModel source, MapModel targetMap, boolean withChildren) {
                    return null;
                }
            }, null, null);
            this.title = title;
        }

        @Override
        public String registryNode(NodeModel nodeModel) {
            registryCalls++;
            return super.registryNode(nodeModel);
        }

        @Override
        public String getTitle() {
            return title;
        }

        int registryCalls() {
            return registryCalls;
        }
    }
}
