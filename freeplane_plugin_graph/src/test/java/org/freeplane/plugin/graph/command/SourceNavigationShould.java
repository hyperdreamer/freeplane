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
        assertThat(fixture.edt.callCount()).isEqualTo(1);
        assertThat(fixture.results.saveHookCalls()).isZero();
    }

    @Test
    public void rejectsUnavailableOrUnreachableSourcesWithoutSelectingAnything() {
        // Catches source navigation that selects a stale node when the lease is unavailable or resolution fails.
        Fixture fixture = new Fixture();
        MapNodes nodes = fixture.addMap("rejects");
        fixture.leases.put(nodes.mapId, new TestLease(nodes.mapId, MapOperationalState.LOADING, fixture.edt));

        GraphCommandResult unavailable = fixture.navigation.open(nodes.key);

        assertRejected(unavailable, "graph_workspace.source_map.unavailable");
        assertThat(fixture.resolver.resolveCalls).isZero();
        assertThat(fixture.selected).isNull();
        assertThat(fixture.edt.callCount()).isEqualTo(1);

        fixture.leases.put(nodes.mapId, new TestLease(nodes.mapId, MapOperationalState.AVAILABLE, fixture.edt));
        fixture.resolver.nodes.clear();
        GraphCommandResult notFound = fixture.navigation.open(nodes.key);

        assertRejected(notFound, "graph_workspace.source_node.not_found");
        assertThat(fixture.resolver.resolveCalls).isEqualTo(1);
        assertThat(fixture.selected).isNull();
        assertThat(fixture.edt.callCount()).isEqualTo(2);
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
        assertThat(fixture.edt.callCount()).isEqualTo(1);
    }

    private static void assertRejected(GraphCommandResult result, String key) {
        assertThat(result.status()).isEqualTo(GraphCommandResult.Status.REJECTED);
        assertThat(result.messageKey()).isEqualTo(key);
    }

    private static final class Fixture {
        private final InlineEdt edt = new InlineEdt();
        private final Map<MapReferenceId, TestLease> leases = new HashMap<MapReferenceId, TestLease>();
        private final Resolver resolver;
        private final ModeController modeController = mock(ModeController.class);
        private final MapController mapController = mock(MapController.class);
        private final ReadOnlyResultEnvelope results;
        private final SourceNavigation navigation;
        private NodeModel selected;

        private Fixture() {
            resolver = new Resolver(edt);
            results = new ReadOnlyResultEnvelope(edt);
            when(modeController.getMapController()).thenAnswer(invocation -> {
                edt.requireOnEdt("mode controller map access");
                return mapController;
            });
            doAnswer(invocation -> {
                edt.requireOnEdt("source selection");
                selected = invocation.getArgument(0);
                return null;
            }).when(mapController).select(any(NodeModel.class));
            navigation = new SourceNavigation(new LeaseLookup(leases, edt), modeController, edt, resolver, results);
        }

        private MapNodes addMap(String title) {
            MapReferenceId mapId = MapReferenceId.of(UUID.randomUUID());
            TrackingMapModel map = new TrackingMapModel(title, edt);
            NodeModel root = new NodeModel("root", map);
            root.setID("ID_ROOT");
            map.setRoot(root);
            NodeModel source = new NodeModel("source", map);
            source.setID("ID_SOURCE");
            root.insert(source);
            SourceNodeKey key = SourceNodeKey.persisted(NodeReference.of(mapId, PersistedNodeId.of("ID_SOURCE")));
            leases.put(mapId, new TestLease(mapId, MapOperationalState.AVAILABLE, edt));
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
        private final InlineEdt edt;

        private LeaseLookup(Map<MapReferenceId, TestLease> leases, InlineEdt edt) {
            this.leases = leases;
            this.edt = edt;
        }

        @Override
        public Optional<MapLease> find(MapReferenceId mapReferenceId) {
            edt.requireOnEdt("lease lookup");
            return Optional.<MapLease>ofNullable(leases.get(mapReferenceId));
        }
    }

    private static final class Resolver implements FreeplaneMapCommandExecutor.TraversalResolver {
        private final Map<SourceNodeKey, NodeModel> nodes = new HashMap<SourceNodeKey, NodeModel>();
        private final InlineEdt edt;
        private SourceNodeKey lastKey;
        private int resolveCalls;

        private Resolver(InlineEdt edt) {
            this.edt = edt;
        }

        @Override
        public Optional<NodeModel> resolve(MapLease lease, SourceNodeKey key) {
            edt.requireOnEdt("traversal resolution");
            resolveCalls++;
            lastKey = key;
            return Optional.ofNullable(nodes.get(key));
        }
    }

    private static final class ReadOnlyResultEnvelope implements FreeplaneMapCommandExecutor.ResultEnvelope {
        private final WorkspaceDocument document = WorkspaceDocument.createVersion1(WorkspaceId.of(UUID.randomUUID()));
        private final InlineEdt edt;
        private int saveHookCalls;

        private ReadOnlyResultEnvelope(InlineEdt edt) {
            this.edt = edt;
        }

        @Override
        public WorkspaceDocument currentDocument() {
            edt.requireOnEdt("result envelope access");
            return document;
        }

        int saveHookCalls() {
            return saveHookCalls;
        }
    }

    private static final class TestLease implements MapLease {
        private final MapReferenceId mapId;
        private final MapOperationalState state;
        private final InlineEdt edt;

        private TestLease(MapReferenceId mapId, MapOperationalState state, InlineEdt edt) {
            this.mapId = mapId;
            this.state = state;
            this.edt = edt;
        }

        @Override
        public MapReferenceId mapReferenceId() {
            edt.requireOnEdt("lease map identity");
            return mapId;
        }

        @Override
        public MapOperationalState state() {
            edt.requireOnEdt("lease state");
            return state;
        }

        @Override
        public void close() {
        }
    }

    private static final class InlineEdt implements EdtExecutor {
        private boolean onEdt;
        private int callCount;

        @Override
        public <T> T call(Callable<T> task) {
            callCount++;
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

        private void requireOnEdt(String operation) {
            assertThat(onEdt).as(operation + " must run on the EDT").isTrue();
        }

        private int callCount() {
            return callCount;
        }
    }

    private static final class TrackingMapModel extends MapModel {
        private final String title;
        private final InlineEdt edt;
        private int registryCalls;

        private TrackingMapModel(String title, InlineEdt edt) {
            super(new INodeDuplicator() {
                @Override
                public NodeModel duplicate(NodeModel source, MapModel targetMap, boolean withChildren) {
                    return null;
                }
            }, null, null);
            this.title = title;
            this.edt = edt;
        }

        @Override
        public <T extends org.freeplane.core.extension.IExtension> T getExtension(Class<T> clazz) {
            edt.requireOnEdt("map undo-extension access");
            return super.getExtension(clazz);
        }

        @Override
        public NodeModel getRootNode() {
            edt.requireOnEdt("map root access");
            return super.getRootNode();
        }

        @Override
        public String registryNode(NodeModel nodeModel) {
            registryCalls++;
            return super.registryNode(nodeModel);
        }

        @Override
        public String getTitle() {
            edt.requireOnEdt("map title access");
            return title;
        }

        int registryCalls() {
            return registryCalls;
        }
    }
}
