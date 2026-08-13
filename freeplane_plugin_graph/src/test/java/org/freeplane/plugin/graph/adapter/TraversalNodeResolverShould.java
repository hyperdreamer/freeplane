package org.freeplane.plugin.graph.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import org.freeplane.features.filter.hidden.NodeVisibility;
import org.freeplane.features.filter.hidden.NodeVisibilityConfiguration;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.SummaryNodeFlag;
import org.freeplane.plugin.graph.group.GraphGroupModel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TraversalNodeResolverShould {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final TraversalNodeResolver resolver = new TraversalNodeResolver();

    @Test
    public void resolvesPersistentNodesByRootTraversalOnTheLeaseEdt() throws Exception {
        GraphAdapterTestSupport.TestMapModel map = new GraphAdapterTestSupport.TestMapModel("resolver");
        GraphAdapterTestSupport.GuardedNodeModel root = new GraphAdapterTestSupport.GuardedNodeModel("root", map);
        root.setID("ID_ROOT");
        map.setRoot(root);
        GraphAdapterTestSupport.TerminalNodeModel child = new GraphAdapterTestSupport.TerminalNodeModel("child", map);
        child.setID("ID_CHILD");
        root.insert(child);

        try (GraphAdapterTestSupport.LeaseScope scope = GraphAdapterTestSupport.leaseScope(temporaryFolder, map, 1L)) {
            map.enforceEdt(scope.edt());
            int callsBeforeResolve = scope.edt().callCount();

            Optional<NodeModel> resolved = resolver.resolve(scope.lease(), persistent(scope.lease(), "ID_CHILD"));

            assertThat(resolved).containsSame(child);
            assertThat(scope.edt().callCount()).isEqualTo(callsBeforeResolve + 1);
        }
    }

    @Test
    public void resolvesDescendantsWithoutStoppingAtGraphGroups() throws Exception {
        GraphAdapterTestSupport.TestMapModel map = GraphAdapterTestSupport.mapWithRoot("groups");
        NodeModel root = map.getRootNode();
        NodeModel outer = GraphAdapterTestSupport.node(map, "outer", "ID_OUTER");
        outer.addExtension(new GraphGroupModel());
        NodeModel nested = GraphAdapterTestSupport.node(map, "nested", "ID_NESTED");
        nested.addExtension(new GraphGroupModel());
        NodeModel descendant = GraphAdapterTestSupport.node(map, "descendant", "ID_DESCENDANT");
        nested.insert(descendant);
        outer.insert(nested);
        root.insert(outer);

        try (GraphAdapterTestSupport.LeaseScope scope = GraphAdapterTestSupport.leaseScope(temporaryFolder, map, 1L)) {
            assertThat(resolver.resolve(scope.lease(), persistent(scope.lease(), "ID_DESCENDANT")))
                .containsSame(descendant);
        }
    }

    @Test
    public void rejectsMismatchedMapsUnknownIdsAndUnknownTransientPaths() throws Exception {
        GraphAdapterTestSupport.TestMapModel map = GraphAdapterTestSupport.mapWithRoot("rejects");
        NodeModel child = GraphAdapterTestSupport.node(map, "child", "ID_CHILD");
        map.getRootNode().insert(child);

        try (GraphAdapterTestSupport.LeaseScope scope = GraphAdapterTestSupport.leaseScope(temporaryFolder, map, 1L)) {
            SourceNodeKey wrongMap = SourceNodeKey.persisted(NodeReference.of(MapReferenceId.of(UUID.randomUUID()),
                PersistedNodeId.of("ID_CHILD")));
            SourceNodeKey unknownId = persistent(scope.lease(), "ID_UNKNOWN");
            SourceNodeKey impossiblePath = SourceNodeKey.transientPath(scope.lease().mapReferenceId(),
                Arrays.asList(9));

            assertThat(resolver.resolve(scope.lease(), wrongMap)).isEmpty();
            assertThat(resolver.resolve(scope.lease(), unknownId)).isEmpty();
            assertThat(resolver.resolve(scope.lease(), impossiblePath)).isEmpty();
        }
    }

    @Test
    public void rejectsHiddenAndHiddenSummarySubtreesUntilTheyBecomeVisible() throws Exception {
        GraphAdapterTestSupport.TestMapModel map = GraphAdapterTestSupport.mapWithRoot("visibility");
        NodeModel root = map.getRootNode();
        NodeModel hidden = GraphAdapterTestSupport.node(map, "hidden", "ID_HIDDEN");
        hidden.addExtension(NodeVisibility.HIDDEN);
        NodeModel hiddenChild = GraphAdapterTestSupport.node(map, "hidden child", "ID_HIDDEN_CHILD");
        hidden.insert(hiddenChild);
        root.insert(hidden);
        NodeModel summary = GraphAdapterTestSupport.node(map, "", "ID_SUMMARY");
        summary.addExtension(SummaryNodeFlag.SUMMARY);
        NodeModel summaryChild = GraphAdapterTestSupport.node(map, "summary child", "ID_SUMMARY_CHILD");
        summary.insert(summaryChild);
        root.insert(summary);
        GraphAdapterTestSupport.OpaqueExcludedNodeModel opaque =
            new GraphAdapterTestSupport.OpaqueExcludedNodeModel("opaque", map);
        opaque.setID("ID_OPAQUE");
        opaque.addExtension(NodeVisibility.HIDDEN);
        NodeModel opaqueChild = GraphAdapterTestSupport.node(map, "opaque child", "ID_OPAQUE_CHILD");
        opaque.insert(opaqueChild);
        root.insert(opaque);

        try (GraphAdapterTestSupport.LeaseScope scope = GraphAdapterTestSupport.leaseScope(temporaryFolder, map, 1L)) {
            SourceNodeKey hiddenPath = SourceNodeKey.transientPath(scope.lease().mapReferenceId(), Arrays.asList(0, 0));
            SourceNodeKey summaryPath = SourceNodeKey.transientPath(scope.lease().mapReferenceId(), Arrays.asList(1, 0));

            assertThat(resolver.resolve(scope.lease(), hiddenPath)).isEmpty();
            assertThat(resolver.resolve(scope.lease(), summaryPath)).isEmpty();
            assertThat(resolver.resolve(scope.lease(), persistent(scope.lease(), "ID_OPAQUE_CHILD"))).isEmpty();

            scope.edt().call(() -> {
                root.addExtension(NodeVisibilityConfiguration.SHOW_HIDDEN_NODES);
                return null;
            });
            assertThat(resolver.resolve(scope.lease(), hiddenPath)).containsSame(hiddenChild);
            assertThat(resolver.resolve(scope.lease(), summaryPath)).isEmpty();

            scope.edt().call(() -> {
                summary.setFolded(true);
                return null;
            });
            assertThat(resolver.resolve(scope.lease(), summaryPath)).containsSame(summaryChild);
        }
    }

    @Test
    public void resolvesIdlessNodesByRawStructuralPathWithoutAssigningIds() throws Exception {
        try (GraphAdapterTestSupport.HeadlessMapScope headless = new GraphAdapterTestSupport.HeadlessMapScope()) {
            org.freeplane.features.map.MapModel map = headless.load("/maps/graph-legacy-idless.mm");
            NodeModel root = map.getRootNode();
            NodeModel before = root.getChildAt(0);
            NodeModel idless = root.getChildAt(1);
            before.addExtension(NodeVisibility.HIDDEN);

            try (GraphAdapterTestSupport.LeaseScope scope = headless.leaseScope(temporaryFolder, map, 1L)) {
                SourceNodeKey path = SourceNodeKey.transientPath(scope.lease().mapReferenceId(), Collections.singletonList(1));

                assertThat(idless.getID()).isNull();
                assertThat(resolver.resolve(scope.lease(), path)).containsSame(idless);
                assertThat(idless.getID()).isNull();
            }
        }
    }

    @Test
    public void refusesAStaleIndexedSecretAfterUnlockAndRelock() throws Exception {
        try (GraphAdapterTestSupport.HeadlessMapScope headless = new GraphAdapterTestSupport.HeadlessMapScope()) {
            GraphAdapterTestSupport.RelockedMap relocked = headless.relockedMap();
            NodeModel indexedSecret = relocked.map().getNodeForID("ID_LOCKED_SECRET");
            assertThat(indexedSecret).isNotNull();
            assertThat(indexedSecret.getUserObject()).isEqualTo("RELOCKED_SECRET_SENTINEL");

            try (GraphAdapterTestSupport.LeaseScope scope = headless.leaseScope(temporaryFolder, relocked.map(), 1L)) {
                assertThat(resolver.resolve(scope.lease(), persistent(scope.lease(), "ID_LOCKED_SECRET"))).isEmpty();
            }
        }
    }

    @Test
    public void unlockRestoresThePreviouslyInaccessibleSecret() throws Exception {
        try (GraphAdapterTestSupport.HeadlessMapScope headless = new GraphAdapterTestSupport.HeadlessMapScope()) {
            GraphAdapterTestSupport.RelockedMap relocked = headless.relockedMap();
            try (GraphAdapterTestSupport.LeaseScope scope = headless.leaseScope(temporaryFolder, relocked.map(), 1L)) {
                SourceNodeKey key = persistent(scope.lease(), "ID_LOCKED_SECRET");

                assertThat(resolver.resolve(scope.lease(), key)).isEmpty();

                relocked.encryption().unlock();
                assertThat(resolver.resolve(scope.lease(), key)).containsSame(relocked.map().getNodeForID("ID_LOCKED_SECRET"));

                relocked.encryption().lock(((org.freeplane.features.map.mindmapmode.MMapController) headless
                    .modeController().getMapController()).getMapWriter());
                assertThat(resolver.resolve(scope.lease(), key)).isEmpty();
            }
        }
    }

    private static SourceNodeKey persistent(MapLease lease, String id) {
        return SourceNodeKey.persisted(NodeReference.of(lease.mapReferenceId(), PersistedNodeId.of(id)));
    }
}
