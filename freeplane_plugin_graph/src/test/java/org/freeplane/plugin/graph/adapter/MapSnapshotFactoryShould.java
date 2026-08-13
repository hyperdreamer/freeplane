package org.freeplane.plugin.graph.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.freeplane.core.extension.IExtension;
import org.freeplane.core.util.Compat;
import org.freeplane.features.filter.hidden.NodeVisibility;
import org.freeplane.features.filter.hidden.NodeVisibilityConfiguration;
import org.freeplane.features.map.EncryptionModel;
import org.freeplane.features.map.FreeNode;
import org.freeplane.features.map.IEncrypter;
import org.freeplane.features.map.INodeDuplicator;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.MapWriter;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.SummaryNodeFlag;
import org.freeplane.features.map.mindmapmode.MMapController;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.features.nodestyle.NodeStyleModel;
import org.freeplane.features.url.MapVersionInterpreter;
import org.freeplane.features.url.mindmapmode.MapLoader;
import org.freeplane.main.application.ApplicationResourceController;
import org.freeplane.main.application.CommandLineParser;
import org.freeplane.main.headlessmode.FreeplaneHeadlessStarter;
import org.freeplane.plugin.graph.group.GraphGroupModel;
import org.freeplane.plugin.graph.projection.input.MapSnapshot;
import org.freeplane.plugin.graph.projection.input.NodeSnapshot;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReference;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MapSnapshotFactoryShould {
    private static final SafeNodeLabel EXCLUDED_LABEL = SafeNodeLabel.of("Node", "Node");

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final MapSnapshotFactory factory = new MapSnapshotFactory();

    @Test
    public void snapshotsThroughTheLeaseEdtWithMapIdentityOrderAndTitle() throws Exception {
        GraphAdapterTestSupport.TestMapModel map = new GraphAdapterTestSupport.TestMapModel("Graph fixture");
        GraphAdapterTestSupport.GuardedNodeModel root = new GraphAdapterTestSupport.GuardedNodeModel("root", map);
        root.setID("ID_ROOT");
        map.setRoot(root);
        GraphAdapterTestSupport.GuardedNodeModel child = new GraphAdapterTestSupport.GuardedNodeModel("child", map);
        child.setID("ID_CHILD");
        root.insert(child);

        try (GraphAdapterTestSupport.LeaseScope scope = GraphAdapterTestSupport.leaseScope(temporaryFolder, map, 7L)) {
            map.enforceEdt(scope.edt());
            int callsBeforeSnapshot = scope.edt().callCount();

            MapSnapshot snapshot = factory.snapshot(scope.lease());

            assertThat(scope.edt().callCount()).isEqualTo(callsBeforeSnapshot + 1);
            assertThat(snapshot.mapReferenceId()).isEqualTo(scope.reference().id());
            assertThat(snapshot.workspaceOrder()).isEqualTo(7);
            assertThat(snapshot.mapName()).isEqualTo("Graph fixture");
            assertThat(snapshot.root().label()).isEqualTo(SafeNodeLabel.of("root", "root"));
            assertThat(snapshot.root().children()).extracting(NodeSnapshot::label)
                .containsExactly(SafeNodeLabel.of("child", "child"));
        }
    }

    @Test
    public void rejectsWorkspaceSequenceAboveTheSnapshotIntRange() throws Exception {
        GraphAdapterTestSupport.TestMapModel maximumMap = GraphAdapterTestSupport.mapWithRoot("maximum");
        try (GraphAdapterTestSupport.LeaseScope maximum = GraphAdapterTestSupport.leaseScope(temporaryFolder,
                maximumMap, Integer.MAX_VALUE)) {
            assertThat(factory.snapshot(maximum.lease()).workspaceOrder()).isEqualTo(Integer.MAX_VALUE);
        }

        GraphAdapterTestSupport.TestMapModel tooLargeMap = GraphAdapterTestSupport.mapWithRoot("too large");
        try (GraphAdapterTestSupport.LeaseScope tooLarge = GraphAdapterTestSupport.leaseScope(temporaryFolder,
                tooLargeMap, ((long) Integer.MAX_VALUE) + 1L)) {
            int rootReadsBeforeSnapshot = tooLargeMap.rootReads();

            assertThatThrownBy(() -> factory.snapshot(tooLarge.lease()))
                .isInstanceOf(IllegalArgumentException.class);

            assertThat(tooLargeMap.rootReads()).isEqualTo(rootReadsBeforeSnapshot);
        }

        assertThat(MapLease.class.getDeclaredMethods()).extracting(Method::getName)
            .containsExactlyInAnyOrder("mapReferenceId", "state", "close");
        for (Method method : MapLease.class.getDeclaredMethods()) {
            assertThat(method.getReturnType()).isNotEqualTo(MapModel.class);
            assertThat(method.getParameterTypes()).doesNotContain(MapModel.class);
        }
    }

    @Test
    public void computesStructuralLeafBeforeExcludedChildrenAreClassified() throws Exception {
        GraphAdapterTestSupport.TestMapModel map = new GraphAdapterTestSupport.TestMapModel("structural");
        NodeModel root = GraphAdapterTestSupport.node(map, "root", "ID_ROOT");
        map.setRoot(root);
        NodeModel parent = GraphAdapterTestSupport.node(map, "parent", "ID_PARENT");
        root.insert(parent);
        NodeModel hidden = GraphAdapterTestSupport.node(map, new HostileValue(), "ID_HIDDEN");
        hidden.addExtension(NodeVisibility.HIDDEN);
        parent.insert(hidden);
        NodeModel descendant = GraphAdapterTestSupport.node(map, "hidden descendant", "ID_HIDDEN_DESCENDANT");
        hidden.insert(descendant);

        try (GraphAdapterTestSupport.LeaseScope scope = GraphAdapterTestSupport.leaseScope(temporaryFolder, map, 1L)) {
            MapSnapshot snapshot = factory.snapshot(scope.lease());
            NodeSnapshot parentSnapshot = GraphAdapterTestSupport.snapshot(snapshot.root(), "ID_PARENT");
            NodeSnapshot hiddenSnapshot = GraphAdapterTestSupport.snapshot(snapshot.root(), "ID_HIDDEN");
            NodeSnapshot descendantSnapshot = GraphAdapterTestSupport.snapshot(snapshot.root(), "ID_HIDDEN_DESCENDANT");

            assertThat(parentSnapshot.structuralLeaf()).isFalse();
            assertThat(hiddenSnapshot.excluded()).isTrue();
            assertThat(hiddenSnapshot.graphGroup()).isFalse();
            assertThat(hiddenSnapshot.label()).isEqualTo(EXCLUDED_LABEL);
            assertThat(descendantSnapshot.excluded()).isTrue();
            assertThat(descendantSnapshot.graphGroup()).isFalse();
            assertThat(descendantSnapshot.label()).isEqualTo(EXCLUDED_LABEL);
        }
    }

    @Test
    public void keepsHiddenSubtreesAsOpaqueIdentityOnlySnapshots() throws Exception {
        GraphAdapterTestSupport.TestMapModel map = new GraphAdapterTestSupport.TestMapModel("opaque hidden");
        NodeModel root = GraphAdapterTestSupport.node(map, "root", "ID_ROOT");
        map.setRoot(root);
        NodeModel hidden = GraphAdapterTestSupport.node(map, "hidden", "ID_HIDDEN");
        hidden.addExtension(NodeVisibility.HIDDEN);
        hidden.addExtension(new GraphGroupModel());
        NodeModel descendant = GraphAdapterTestSupport.node(map, "hidden descendant", "ID_HIDDEN_DESCENDANT");
        descendant.addExtension(new GraphGroupModel());
        hidden.insert(descendant);
        root.insert(hidden);

        try (GraphAdapterTestSupport.LeaseScope scope = GraphAdapterTestSupport.leaseScope(temporaryFolder, map, 1L)) {
            MapSnapshot snapshot = factory.snapshot(scope.lease());
            NodeSnapshot hiddenSnapshot = GraphAdapterTestSupport.snapshot(snapshot.root(), "ID_HIDDEN");
            NodeSnapshot descendantSnapshot = GraphAdapterTestSupport.snapshot(snapshot.root(), "ID_HIDDEN_DESCENDANT");

            assertThat(hiddenSnapshot.excluded()).isTrue();
            assertThat(hiddenSnapshot.graphGroup()).isFalse();
            assertThat(hiddenSnapshot.label()).isEqualTo(EXCLUDED_LABEL);
            assertThat(hiddenSnapshot.children()).hasSize(1);
            assertThat(descendantSnapshot.excluded()).isTrue();
            assertThat(descendantSnapshot.graphGroup()).isFalse();
            assertThat(descendantSnapshot.label()).isEqualTo(EXCLUDED_LABEL);
        }
    }

    @Test
    public void showHiddenRestoresOrdinaryLabelsWithoutOverridingHiddenSummaries() throws Exception {
        GraphAdapterTestSupport.TestMapModel map = new GraphAdapterTestSupport.TestMapModel("show hidden");
        NodeModel root = GraphAdapterTestSupport.node(map, "root", "ID_ROOT");
        root.addExtension(NodeVisibilityConfiguration.SHOW_HIDDEN_NODES);
        map.setRoot(root);
        NodeModel explicitlyHidden = GraphAdapterTestSupport.node(map, "ordinary after show", "ID_EXPLICIT");
        explicitlyHidden.addExtension(NodeVisibility.HIDDEN);
        root.insert(explicitlyHidden);
        NodeModel hiddenSummary = GraphAdapterTestSupport.node(map, "", "ID_HIDDEN_SUMMARY");
        hiddenSummary.addExtension(SummaryNodeFlag.SUMMARY);
        hiddenSummary.insert(GraphAdapterTestSupport.node(map, "summary child", "ID_SUMMARY_CHILD"));
        root.insert(hiddenSummary);

        try (GraphAdapterTestSupport.LeaseScope scope = GraphAdapterTestSupport.leaseScope(temporaryFolder, map, 1L)) {
            MapSnapshot snapshot = factory.snapshot(scope.lease());
            NodeSnapshot explicitlyHiddenSnapshot = GraphAdapterTestSupport.snapshot(snapshot.root(), "ID_EXPLICIT");
            NodeSnapshot hiddenSummarySnapshot = GraphAdapterTestSupport.snapshot(snapshot.root(), "ID_HIDDEN_SUMMARY");
            NodeSnapshot hiddenSummaryChild = GraphAdapterTestSupport.snapshot(snapshot.root(), "ID_SUMMARY_CHILD");

            assertThat(explicitlyHiddenSnapshot.excluded()).isFalse();
            assertThat(explicitlyHiddenSnapshot.label()).isEqualTo(SafeNodeLabel.of("ordinary after show",
                "ordinary after show"));
            assertThat(hiddenSummarySnapshot.excluded()).isTrue();
            assertThat(hiddenSummarySnapshot.label()).isEqualTo(EXCLUDED_LABEL);
            assertThat(hiddenSummaryChild.excluded()).isTrue();
            assertThat(hiddenSummaryChild.label()).isEqualTo(EXCLUDED_LABEL);
        }
    }

    @Test
    public void keepsVisibleSummariesFreeNodesAndNestedGraphGroupsOrdinary() throws Exception {
        try (GraphAdapterTestSupport.HeadlessMapScope headless = new GraphAdapterTestSupport.HeadlessMapScope()) {
            MapModel map = headless.load("/maps/graph-legacy-idless.mm");
            NodeModel root = map.getRootNode();
            NodeModel visibleSummary = GraphAdapterTestSupport.node(map, "visible summary", "ID_VISIBLE_SUMMARY");
            visibleSummary.addExtension(SummaryNodeFlag.SUMMARY);
            root.insert(visibleSummary);
            NodeModel free = GraphAdapterTestSupport.node(map, "free node", "ID_FREE");
            FreeNode freeNode = headless.modeController().getExtension(FreeNode.class);
            assertThat(freeNode).isNotNull();
            free.addExtension(freeNode);
            root.insert(free);
            NodeModel outerGroup = GraphAdapterTestSupport.node(map, "outer", "ID_OUTER_GROUP");
            outerGroup.addExtension(new GraphGroupModel());
            NodeModel nestedGroup = GraphAdapterTestSupport.node(map, "nested", "ID_NESTED_GROUP");
            nestedGroup.addExtension(new GraphGroupModel());
            NodeModel nestedChild = GraphAdapterTestSupport.node(map, "nested child", "ID_NESTED_CHILD");
            nestedGroup.insert(nestedChild);
            outerGroup.insert(nestedGroup);
            root.insert(outerGroup);

            try (GraphAdapterTestSupport.LeaseScope lease = headless.leaseScope(temporaryFolder, map, 1L)) {
                MapSnapshot snapshot = factory.snapshot(lease.lease());
                NodeSnapshot visibleSummarySnapshot = GraphAdapterTestSupport.snapshot(snapshot.root(), "ID_VISIBLE_SUMMARY");
                NodeSnapshot freeSnapshot = GraphAdapterTestSupport.snapshot(snapshot.root(), "ID_FREE");
                NodeSnapshot outerSnapshot = GraphAdapterTestSupport.snapshot(snapshot.root(), "ID_OUTER_GROUP");
                NodeSnapshot nestedSnapshot = GraphAdapterTestSupport.snapshot(snapshot.root(), "ID_NESTED_GROUP");

                assertThat(visibleSummarySnapshot.excluded()).isFalse();
                assertThat(visibleSummarySnapshot.label()).isEqualTo(SafeNodeLabel.of("visible summary", "visible summary"));
                assertThat(freeSnapshot.excluded()).isFalse();
                assertThat(freeSnapshot.label()).isEqualTo(SafeNodeLabel.of("free node", "free node"));
                assertThat(outerSnapshot.graphGroup()).isTrue();
                assertThat(nestedSnapshot.graphGroup()).isTrue();
                assertThat(GraphAdapterTestSupport.snapshot(snapshot.root(), "ID_NESTED_CHILD").excluded()).isFalse();
            }
        }
    }

    @Test
    public void usesRawTransientPathsWithoutAssigningIds() throws Exception {
        try (GraphAdapterTestSupport.HeadlessMapScope headless = new GraphAdapterTestSupport.HeadlessMapScope()) {
            MapModel loaded = headless.load("/maps/graph-legacy-idless.mm");
            NodeModel root = loaded.getRootNode();
            NodeModel before = root.getChildAt(0);
            NodeModel idless = root.getChildAt(1);
            before.addExtension(NodeVisibility.HIDDEN);

            assertThat(NodeStyleModel.getNodeNumbering(idless)).isEqualTo(Boolean.TRUE);
            assertThat(idless.getID()).isNull();

            try (GraphAdapterTestSupport.LeaseScope lease = headless.leaseScope(temporaryFolder, loaded, 1L)) {
                MapSnapshot snapshot = factory.snapshot(lease.lease());
                NodeSnapshot idlessSnapshot = GraphAdapterTestSupport.snapshot(snapshot.root(),
                    SourceNodeKey.transientPath(lease.lease().mapReferenceId(), Arrays.asList(1)));

                assertThat(idlessSnapshot.key().structuralPath()).containsExactly(1);
                assertThat(idless.getID()).isNull();
            }
        }

        GraphAdapterTestSupport.CountingMapModel counting = new GraphAdapterTestSupport.CountingMapModel("counting");
        NodeModel root = GraphAdapterTestSupport.node(counting, "root", "ID_COUNT_ROOT");
        counting.setRoot(root);
        NodeModel before = GraphAdapterTestSupport.node(counting, "before", "ID_COUNT_BEFORE");
        before.addExtension(NodeVisibility.HIDDEN);
        root.insert(before);
        NodeModel idless = GraphAdapterTestSupport.node(counting, "idless numbered node", null);
        NodeStyleModel.setNodeNumbering(idless, Boolean.TRUE);
        root.insert(idless);
        root.insert(GraphAdapterTestSupport.node(counting, "after", "ID_COUNT_AFTER"));

        try (GraphAdapterTestSupport.LeaseScope lease = GraphAdapterTestSupport.leaseScope(temporaryFolder, counting, 1L)) {
            assertThat(idless.getID()).isNull();
            assertThat(counting.registryCalls()).isZero();

            factory.snapshot(lease.lease());

            assertThat(idless.getID()).isNull();
            assertThat(counting.registryCalls()).isZero();
        }
    }

    @Test
    public void recordsOnlySafelyTraversedPersistentIdsInSortedImmutableOrder() throws Exception {
        GraphAdapterTestSupport.TestMapModel map = new GraphAdapterTestSupport.TestMapModel("ids");
        NodeModel root = GraphAdapterTestSupport.node(map, "root", "ID_Z_ROOT");
        map.setRoot(root);
        NodeModel hidden = GraphAdapterTestSupport.node(map, "hidden", "ID_B_HIDDEN");
        hidden.addExtension(NodeVisibility.HIDDEN);
        root.insert(hidden);
        root.insert(GraphAdapterTestSupport.node(map, "visible", "ID_A_VISIBLE"));

        try (GraphAdapterTestSupport.LeaseScope lease = GraphAdapterTestSupport.leaseScope(temporaryFolder, map, 1L)) {
            MapSnapshot snapshot = factory.snapshot(lease.lease());

            assertThat(snapshot.attachedPersistentIds()).extracting(PersistedNodeId::value)
                .containsExactly("ID_A_VISIBLE", "ID_B_HIDDEN", "ID_Z_ROOT");
            assertThatThrownBy(() -> snapshot.attachedPersistentIds().add(PersistedNodeId.of("ID_NEW")))
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Test
    public void marksARelockedBranchInaccessibleWithoutSnapshottingItsSecret() throws Exception {
        try (GraphAdapterTestSupport.HeadlessMapScope headless = new GraphAdapterTestSupport.HeadlessMapScope()) {
            GraphAdapterTestSupport.RelockedMap relocked = headless.relockedMap();
            assertThat(relocked.map().getNodeForID("ID_LOCKED_SECRET").getUserObject())
                .isEqualTo("RELOCKED_SECRET_SENTINEL");
            assertThat(relocked.container().getChildren()).isEmpty();

            try (GraphAdapterTestSupport.LeaseScope lease = headless.leaseScope(temporaryFolder, relocked.map(), 1L)) {
                MapSnapshot lockedSnapshot = factory.snapshot(lease.lease());
                NodeSnapshot lockedContainer = GraphAdapterTestSupport.snapshot(lockedSnapshot.root(), "ID_LOCKED_CONTAINER");

                assertThat(lockedSnapshot.hasInaccessibleBranch()).isTrue();
                assertThat(lockedContainer.structuralLeaf()).isTrue();
                assertThat(lockedContainer.label()).isEqualTo(SafeNodeLabel.of("locked container", "locked container"));
                assertThat(lockedSnapshot.attachedPersistentIds()).extracting(PersistedNodeId::value)
                    .doesNotContain("ID_LOCKED_SECRET");
                assertThat(GraphAdapterTestSupport.snapshotLabels(lockedSnapshot.root()))
                    .doesNotContain("RELOCKED_SECRET_SENTINEL");

                relocked.encryption().unlock();
                MapSnapshot unlockedSnapshot = factory.snapshot(lease.lease());

                assertThat(unlockedSnapshot.hasInaccessibleBranch()).isFalse();
                assertThat(GraphAdapterTestSupport.snapshot(unlockedSnapshot.root(), "ID_LOCKED_SECRET").label())
                    .isEqualTo(SafeNodeLabel.of("RELOCKED_SECRET_SENTINEL", "RELOCKED_SECRET_SENTINEL"));
            }
        }
    }

    @Test
    public void productionSourcesForbidFlatLookupAndIdentityCreation() throws Exception {
        for (String sourceName : Arrays.asList("MapSnapshotFactory.java", "TraversalNodeResolver.java")) {
            Path source = Paths.get("src/main/java/org/freeplane/plugin/graph/adapter", sourceName);
            assertThat(Files.isRegularFile(source)).isTrue();
            String productionSource = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
            for (String forbiddenToken : Arrays.asList("getNodeForID", "getNodeFromID_", "createID", "registryNode",
                    "getDeclaredField", "getDeclaredMethod", "getTransformed", "getPlainTransformedText")) {
                assertThat(productionSource).doesNotContain(forbiddenToken);
            }
        }
    }

    private static final class HostileValue {
        @Override
        public String toString() {
            throw new AssertionError("Excluded node labels must not read raw content");
        }
    }
}

final class GraphAdapterTestSupport {
    private static final String MAP_COLOR = "#4E79A7";

    private GraphAdapterTestSupport() {
    }

    static TestMapModel mapWithRoot(String title) {
        TestMapModel map = new TestMapModel(title);
        NodeModel root = node(map, "root", "ID_ROOT");
        map.setRoot(root);
        return map;
    }

    static NodeModel node(MapModel map, Object text, String id) {
        NodeModel node = new NodeModel(text, map);
        if (id != null) {
            node.setID(id);
        }
        return node;
    }

    static LeaseScope leaseScope(TemporaryFolder folder, TestMapModel map, long sequence) throws Exception {
        Path workspace = folder.newFile("workspace-" + UUID.randomUUID() + ".fpg").toPath();
        Path mapFile = folder.newFile("map-" + UUID.randomUUID() + ".mm").toPath();
        map.setURL(mapFile.toRealPath().toUri().toURL());
        return leaseScope(workspace, mapFile, map, sequence, mockedModeController());
    }

    private static ModeController mockedModeController() {
        ModeController modeController = mock(ModeController.class);
        when(modeController.getMapController()).thenReturn(mock(MMapController.class));
        return modeController;
    }

    static LeaseScope leaseScope(Path workspace, Path mapFile, MapModel map, long sequence,
            ModeController modeController) throws Exception {
        return leaseScope(workspace, mapFile, map, sequence, modeController, new InlineEdt());
    }

    static LeaseScope leaseScope(Path workspace, Path mapFile, MapModel map, long sequence,
            ModeController modeController, InlineEdt edt) throws Exception {
        MapReference reference = MapReference.of(MapReferenceId.of(UUID.randomUUID()), sequence,
            mapFile.toRealPath().toUri(), true, MAP_COLOR, Collections.emptyList());
        MapLeaseManager manager = new MapLeaseManager(workspace, modeController, edt, null, url -> map,
            model -> false, url -> null);
        MapLease lease = manager.acquire(reference).toCompletableFuture().get(1L, TimeUnit.SECONDS);
        return new LeaseScope(manager, lease, reference, edt);
    }

    static NodeSnapshot snapshot(NodeSnapshot root, String persistedId) {
        for (NodeSnapshot candidate : depthFirst(root)) {
            if (candidate.key().persistent()
                    && persistedId.equals(candidate.key().persistedReference().get().nodeId().value())) {
                return candidate;
            }
        }
        throw new AssertionError("Missing snapshot node " + persistedId);
    }

    static NodeSnapshot snapshot(NodeSnapshot root, SourceNodeKey key) {
        for (NodeSnapshot candidate : depthFirst(root)) {
            if (key.equals(candidate.key())) {
                return candidate;
            }
        }
        throw new AssertionError("Missing snapshot node " + key);
    }

    static List<String> snapshotLabels(NodeSnapshot root) {
        List<String> labels = new ArrayList<String>();
        for (NodeSnapshot snapshot : depthFirst(root)) {
            labels.add(snapshot.label().fullText());
            labels.add(snapshot.label().displayText());
        }
        return labels;
    }

    private static List<NodeSnapshot> depthFirst(NodeSnapshot root) {
        List<NodeSnapshot> result = new ArrayList<NodeSnapshot>();
        addDepthFirst(root, result);
        return result;
    }

    private static void addDepthFirst(NodeSnapshot node, List<NodeSnapshot> result) {
        result.add(node);
        for (NodeSnapshot child : node.children()) {
            addDepthFirst(child, result);
        }
    }

    static class TestMapModel extends MapModel {
        private final String title;
        private InlineEdt edt;
        private boolean enforceEdt;
        private int rootReads;

        TestMapModel(String title) {
            super(new INodeDuplicator() {
                @Override
                public NodeModel duplicate(NodeModel source, MapModel targetMap, boolean withChildren) {
                    return null;
                }
            }, null, null);
            this.title = title;
        }

        @Override
        public NodeModel getRootNode() {
            assertEdt();
            rootReads++;
            return super.getRootNode();
        }

        @Override
        public String getTitle() {
            assertEdt();
            return title;
        }

        void enforceEdt(InlineEdt value) {
            edt = value;
            enforceEdt = true;
        }

        void assertEdt() {
            if (enforceEdt && !edt.isEdt()) {
                throw new AssertionError("Model access escaped the lease EDT callback");
            }
        }

        int rootReads() {
            return rootReads;
        }
    }

    static final class CountingMapModel extends TestMapModel {
        private int registryCalls;

        CountingMapModel(String title) {
            super(title);
        }

        @Override
        public String registryNode(NodeModel nodeModel) {
            registryCalls++;
            return super.registryNode(nodeModel);
        }

        int registryCalls() {
            return registryCalls;
        }
    }

    static final class GuardedNodeModel extends NodeModel {
        GuardedNodeModel(Object userObject, TestMapModel map) {
            super(userObject, map);
        }

        @Override
        public List<NodeModel> getChildren() {
            guardedMap().assertEdt();
            return super.getChildren();
        }

        @Override
        public String getID() {
            guardedMap().assertEdt();
            return super.getID();
        }

        @Override
        public Object getUserObject() {
            guardedMap().assertEdt();
            return super.getUserObject();
        }

        @Override
        public <T extends IExtension> T getExtension(Class<T> clazz) {
            guardedMap().assertEdt();
            return super.getExtension(clazz);
        }

        @Override
        public boolean isFolded() {
            guardedMap().assertEdt();
            return super.isFolded();
        }

        private TestMapModel guardedMap() {
            return (TestMapModel) getMap();
        }
    }

    static final class OpaqueExcludedNodeModel extends NodeModel {
        OpaqueExcludedNodeModel(Object userObject, MapModel map) {
            super(userObject, map);
        }

        @Override
        public List<NodeModel> getChildren() {
            throw new AssertionError("Resolver must prune excluded branches before reading their children");
        }
    }

    static final class TerminalNodeModel extends NodeModel {
        TerminalNodeModel(Object userObject, MapModel map) {
            super(userObject, map);
        }

        @Override
        public List<NodeModel> getChildren() {
            throw new AssertionError("Resolver must compare a reachable persistent node before reading its children");
        }
    }

    static final class LeaseScope implements AutoCloseable {
        private final MapLeaseManager manager;
        private final MapLease lease;
        private final MapReference reference;
        private final InlineEdt edt;

        LeaseScope(MapLeaseManager manager, MapLease lease, MapReference reference, InlineEdt edt) {
            this.manager = manager;
            this.lease = lease;
            this.reference = reference;
            this.edt = edt;
        }

        MapLease lease() {
            return lease;
        }

        MapReference reference() {
            return reference;
        }

        InlineEdt edt() {
            return edt;
        }

        @Override
        public void close() {
            lease.close();
            manager.close();
        }
    }

    static final class InlineEdt implements EdtExecutor {
        private final ThreadLocal<Boolean> onEdt = new ThreadLocal<Boolean>();
        private int calls;

        @Override
        public <T> T call(Callable<T> task) {
            Boolean previous = onEdt.get();
            onEdt.set(Boolean.TRUE);
            calls++;
            try {
                return task.call();
            }
            catch (RuntimeException failure) {
                throw failure;
            }
            catch (Exception failure) {
                throw new IllegalStateException("EDT callback failed", failure);
            }
            finally {
                if (previous == null) {
                    onEdt.remove();
                }
                else {
                    onEdt.set(previous);
                }
            }
        }

        @Override
        public void execute(Runnable task) {
            call(new Callable<Void>() {
                @Override
                public Void call() {
                    task.run();
                    return null;
                }
            });
        }

        @Override
        public boolean isEdt() {
            return Boolean.TRUE.equals(onEdt.get());
        }

        int callCount() {
            return calls;
        }
    }

    static final class HeadlessMapScope implements AutoCloseable {
        private final HeadlessResourceScope resources;
        private final FreeplaneHeadlessStarter starter;
        private final boolean ownsStarter;
        private final Controller previousController;
        private final MapVersionInterpreter[] previousMapVersionInterpreters;
        private final ModeController modeController;
        private final List<MapModel> maps = new ArrayList<MapModel>();
        private final List<Path> mapFiles = new ArrayList<Path>();

        HeadlessMapScope() throws Exception {
            previousController = Controller.getCurrentController();
            previousMapVersionInterpreters = mapVersionInterpreters();
            ModeController existing = previousController == null ? null
                : previousController.getModeController(MModeController.MODENAME);
            if (existing != null && existing.getMapController() instanceof MMapController) {
                resources = null;
                starter = null;
                ownsStarter = false;
                modeController = existing;
            }
            else {
                resources = new HeadlessResourceScope();
                starter = new FreeplaneHeadlessStarter(CommandLineParser.parse());
                Controller controller = starter.createController();
                starter.createModeControllers(controller);
                starter.createFrame();
                ownsStarter = true;
                modeController = controller.getModeController(MModeController.MODENAME);
            }
            MapVersionInterpreter.addMapVersionInterpreter(new MapVersionInterpreter("GRAPH_ADAPTER_FIXTURE", 19,
                "freeplane 1.12.0", false, false, "Freeplane", "https://www.freeplane.org", null, null));
        }

        ModeController modeController() {
            return modeController;
        }

        MapModel load(String resourceName) throws Exception {
            Path mapFile = Files.createTempFile("graph-adapter", ".mm");
            try (InputStream input = MapSnapshotFactoryShould.class.getResourceAsStream(resourceName)) {
                if (input == null) {
                    throw new IOException("Missing fixture " + resourceName);
                }
                Files.copy(input, mapFile, StandardCopyOption.REPLACE_EXISTING);
            }
            MapModel map = new MapLoader(modeController).load(mapFile.toUri().toURL()).getMap();
            if (map == null) {
                throw new IOException("MapLoader did not load " + resourceName);
            }
            maps.add(map);
            mapFiles.add(mapFile);
            return map;
        }

        LeaseScope leaseScope(TemporaryFolder folder, MapModel map, long sequence) throws Exception {
            int index = maps.indexOf(map);
            if (index < 0) {
                throw new IllegalArgumentException("Map was not loaded by this scope");
            }
            Path workspace = folder.newFile("workspace-" + UUID.randomUUID() + ".fpg").toPath();
            return GraphAdapterTestSupport.leaseScope(workspace, mapFiles.get(index), map, sequence, modeController);
        }

        RelockedMap relockedMap() throws Exception {
            MapModel map = load("/maps/graph-locked-branch.mm");
            NodeModel container = map.getNodeForID("ID_LOCKED_CONTAINER");
            if (container == null) {
                throw new AssertionError("Missing locked container fixture node");
            }
            EncryptionModel encryption = new EncryptionModel(container, new IdentityEncrypter());
            container.addExtension(encryption);
            MapWriter writer = ((MMapController) modeController.getMapController()).getMapWriter();
            encryption.lock(writer);
            encryption.unlock();
            encryption.lock(writer);
            return new RelockedMap(map, container, encryption);
        }

        @Override
        public void close() throws Exception {
            try {
                for (MapModel map : maps) {
                    ((MMapController) modeController.getMapController()).closeWithoutSaving(map);
                }
            }
            finally {
                try {
                    if (ownsStarter) {
                        starter.stop();
                        clearMapIoSingleton();
                    }
                }
                finally {
                    try {
                        for (Path mapFile : mapFiles) {
                            Files.deleteIfExists(mapFile);
                        }
                    }
                    finally {
                        try {
                            if (resources != null) {
                                resources.close();
                            }
                        }
                        finally {
                            try {
                                restoreMapVersionInterpreters(previousMapVersionInterpreters);
                            }
                            finally {
                                Controller.setCurrentController(previousController);
                            }
                        }
                    }
                }
            }
        }

        private static MapVersionInterpreter[] mapVersionInterpreters() throws ReflectiveOperationException {
            Field values = MapVersionInterpreter.class.getDeclaredField("values");
            values.setAccessible(true);
            return (MapVersionInterpreter[]) values.get(null);
        }

        private static void restoreMapVersionInterpreters(MapVersionInterpreter[] values)
                throws ReflectiveOperationException {
            Field field = MapVersionInterpreter.class.getDeclaredField("values");
            field.setAccessible(true);
            field.set(null, values);
        }

        private static void clearMapIoSingleton() throws ReflectiveOperationException {
            Field instance = Class.forName("org.freeplane.features.mapio.mindmapmode.MMapIO")
                .getDeclaredField("INSTANCE");
            instance.setAccessible(true);
            instance.set(null, null);
        }
    }

    static final class RelockedMap {
        private final MapModel map;
        private final NodeModel container;
        private final EncryptionModel encryption;

        RelockedMap(MapModel map, NodeModel container, EncryptionModel encryption) {
            this.map = map;
            this.container = container;
            this.encryption = encryption;
        }

        MapModel map() {
            return map;
        }

        NodeModel container() {
            return container;
        }

        EncryptionModel encryption() {
            return encryption;
        }
    }

    private static final class IdentityEncrypter implements IEncrypter {
        @Override
        public String decrypt(String value) {
            return value;
        }

        @Override
        public String encrypt(String value) {
            return value;
        }

        @Override
        public void destroy() {
        }
    }

    private static final class HeadlessResourceScope implements AutoCloseable {
        private final String previousGlobalResourceDirectory;
        private final String previousResourceBaseDirectory;
        private final String previousInstallationBaseDirectory;
        private final Path testProperties;
        private final byte[] previousProperties;
        private final Path testVersionProperties;
        private final byte[] previousVersionProperties;
        private final Path testMapVersions;
        private final byte[] previousMapVersions;
        private final Path testPreferences;
        private final byte[] previousPreferences;

        HeadlessResourceScope() throws Exception {
            URL fixtureUrl = MapSnapshotFactoryShould.class.getResource("/maps/graph-locked-branch.mm");
            if (fixtureUrl == null) {
                throw new IOException("Missing graph adapter fixture");
            }
            Path testResourceDirectory = Paths.get(fixtureUrl.toURI()).getParent().getParent();
            Path projectDirectory = testResourceDirectory.getParent().getParent().getParent().getParent();
            Path viewerResources = projectDirectory.resolve("freeplane/build/resources/viewer");
            Path viewerProperties = viewerResources.resolve("freeplane.properties");
            Path viewerVersionProperties = viewerResources.resolve("version.properties");
            Path externalPreferences = projectDirectory.resolve("freeplane/src/external/resources/xml/preferences.xml");
            Path editorMapVersions = projectDirectory.resolve("freeplane/src/editor/resources/xml/mapVersions.xml");
            if (!Files.isRegularFile(viewerProperties) || !Files.isRegularFile(viewerVersionProperties)
                    || !Files.isRegularFile(externalPreferences) || !Files.isRegularFile(editorMapVersions)) {
                throw new IOException("Missing Freeplane headless test resources");
            }
            testProperties = testResourceDirectory.resolve("freeplane.properties");
            previousProperties = Files.exists(testProperties) ? Files.readAllBytes(testProperties) : null;
            Files.copy(viewerProperties, testProperties, StandardCopyOption.REPLACE_EXISTING);
            testVersionProperties = testResourceDirectory.resolve("version.properties");
            previousVersionProperties = Files.exists(testVersionProperties) ? Files.readAllBytes(testVersionProperties) : null;
            Files.copy(viewerVersionProperties, testVersionProperties, StandardCopyOption.REPLACE_EXISTING);
            testMapVersions = testResourceDirectory.resolve("xml/mapVersions.xml");
            previousMapVersions = Files.exists(testMapVersions) ? Files.readAllBytes(testMapVersions) : null;
            Files.copy(editorMapVersions, testMapVersions, StandardCopyOption.REPLACE_EXISTING);
            testPreferences = testResourceDirectory.resolve("xml/preferences.xml");
            Files.createDirectories(testPreferences.getParent());
            previousPreferences = Files.exists(testPreferences) ? Files.readAllBytes(testPreferences) : null;
            Files.copy(externalPreferences, testPreferences, StandardCopyOption.REPLACE_EXISTING);

            previousGlobalResourceDirectory = System.getProperty("org.freeplane.globalresourcedir");
            System.setProperty("org.freeplane.globalresourcedir", viewerResources.toString());
            previousResourceBaseDirectory = ApplicationResourceController.RESOURCE_BASE_DIRECTORY;
            previousInstallationBaseDirectory = ApplicationResourceController.INSTALLATION_BASE_DIRECTORY;
            ApplicationResourceController.RESOURCE_BASE_DIRECTORY = viewerResources.toString();
            ApplicationResourceController.INSTALLATION_BASE_DIRECTORY = viewerResources.getParent().toString();
            Compat.setIsApplet(false);
        }

        @Override
        public void close() throws IOException {
            if (previousGlobalResourceDirectory == null) {
                System.clearProperty("org.freeplane.globalresourcedir");
            }
            else {
                System.setProperty("org.freeplane.globalresourcedir", previousGlobalResourceDirectory);
            }
            ApplicationResourceController.RESOURCE_BASE_DIRECTORY = previousResourceBaseDirectory;
            ApplicationResourceController.INSTALLATION_BASE_DIRECTORY = previousInstallationBaseDirectory;
            restore(testProperties, previousProperties);
            restore(testVersionProperties, previousVersionProperties);
            restore(testMapVersions, previousMapVersions);
            restore(testPreferences, previousPreferences);
        }

        private static void restore(Path path, byte[] previousContents) throws IOException {
            if (previousContents == null) {
                Files.deleteIfExists(path);
            }
            else {
                Files.write(path, previousContents);
            }
        }
    }
}
