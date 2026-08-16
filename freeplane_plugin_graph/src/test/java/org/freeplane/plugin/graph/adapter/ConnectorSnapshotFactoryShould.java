package org.freeplane.plugin.graph.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.freeplane.core.extension.IExtension;
import org.freeplane.features.filter.hidden.NodeVisibility;
import org.freeplane.features.filter.hidden.NodeVisibilityConfiguration;
import org.freeplane.features.link.ConnectorArrows;
import org.freeplane.features.link.ConnectorModel;
import org.freeplane.features.link.NodeLinks;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.SummaryNodeFlag;
import org.freeplane.plugin.graph.projection.input.ConnectorDescriptor;
import org.freeplane.plugin.graph.projection.input.ConnectorSnapshot;
import org.freeplane.plugin.graph.projection.input.MapSnapshot;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ConnectorSnapshotFactoryShould {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static GraphAdapterTestSupport.HeadlessMapScope headless;

    @org.junit.BeforeClass
    public static void setUpHeadlessResources() throws Exception {
        headless = new GraphAdapterTestSupport.HeadlessMapScope();
    }

    @org.junit.AfterClass
    public static void tearDownHeadlessResources() throws Exception {
        if (headless != null) {
            headless.close();
        }
    }

    private final MapSnapshotFactory factory = new MapSnapshotFactory();
    private final ConnectorSnapshotFactory connectorFactory = new ConnectorSnapshotFactory();

    @Test
    public void snapshotsReachableConnectorsOnTheLeaseEdtAndIntegratesThemIntoMapSnapshots() throws Exception {
        GraphAdapterTestSupport.TestMapModel map = new GraphAdapterTestSupport.TestMapModel("Graph connectors");
        GraphAdapterTestSupport.GuardedNodeModel root = new GraphAdapterTestSupport.GuardedNodeModel("root", map);
        root.setID("ID_ROOT");
        map.setRoot(root);
        GraphAdapterTestSupport.GuardedNodeModel source = new GraphAdapterTestSupport.GuardedNodeModel("source", map);
        source.setID("ID_SOURCE");
        root.insert(source);
        GraphAdapterTestSupport.GuardedNodeModel target = new GraphAdapterTestSupport.GuardedNodeModel("target", map);
        target.setID("ID_TARGET");
        root.insert(target);
        NodeLinks.createLinkExtension(source).addArrowlink(new ConnectorModel(source, "ID_TARGET"));

        try (GraphAdapterTestSupport.LeaseScope scope = GraphAdapterTestSupport.leaseScope(temporaryFolder, map, 7L)) {
            map.enforceEdt(scope.edt());
            int callsBeforeSnapshot = scope.edt().callCount();

            MapSnapshot snapshot = factory.snapshot(scope.lease());

            assertThat(scope.edt().callCount()).isEqualTo(callsBeforeSnapshot + 1);
            assertThat(snapshot.connectors()).hasSize(1);
            ConnectorSnapshot integrated = snapshot.connectors().get(0);
            assertThat(integrated.occurrence()).isZero();
            assertThat(integrated.descriptor().source().persistedReference().get().nodeId().value())
                .isEqualTo("ID_SOURCE");
            assertThat(integrated.descriptor().target().nodeId().value()).isEqualTo("ID_TARGET");

            List<ConnectorSnapshot> standalone = connectorFactory.snapshotReachableConnectors(scope.lease(), snapshot);
            assertThat(scope.edt().callCount()).isEqualTo(callsBeforeSnapshot + 2);
            assertThat(standalone).isEqualTo(snapshot.connectors());
        }
    }

    @Test
    public void preservesDuplicateOccurrencesDirectionsLabelsAndTraversalOrder() throws Exception {
        GraphAdapterTestSupport.TestMapModel map = new GraphAdapterTestSupport.TestMapModel("connector detail");
        NodeModel root = GraphAdapterTestSupport.node(map, "root", "ID_ROOT");
        map.setRoot(root);
        NodeModel firstSource = GraphAdapterTestSupport.node(map, "first", "ID_FIRST");
        root.insert(firstSource);
        NodeModel secondSource = GraphAdapterTestSupport.node(map, "second", "ID_SECOND");
        root.insert(secondSource);

        NodeLinks firstLinks = NodeLinks.createLinkExtension(firstSource);
        ConnectorModel none = new ConnectorModel(firstSource, "ID_SECOND");
        none.setArrows(Optional.of(ConnectorArrows.NONE));
        none.setSourceLabel("src\r\ntext");
        firstLinks.addArrowlink(none);
        ConnectorModel forward = new ConnectorModel(firstSource, "ID_SECOND");
        forward.setArrows(Optional.of(ConnectorArrows.FORWARD));
        forward.setMiddleLabel("mid\rtitle");
        firstLinks.addArrowlink(forward);
        ConnectorModel backward = new ConnectorModel(firstSource, "ID_SECOND");
        backward.setArrows(Optional.of(ConnectorArrows.BACKWARD));
        backward.setTargetLabel("tgt\nlabel");
        firstLinks.addArrowlink(backward);
        ConnectorModel both = new ConnectorModel(firstSource, "ID_SECOND");
        both.setArrows(Optional.of(ConnectorArrows.BOTH));
        both.setMiddleLabel("a\r\n\r\nb");
        firstLinks.addArrowlink(both);
        ConnectorModel defaultArrows = new ConnectorModel(firstSource, "ID_SECOND");
        defaultArrows.setMiddleLabel("x\n\ny");
        firstLinks.addArrowlink(defaultArrows);

        NodeLinks secondLinks = NodeLinks.createLinkExtension(secondSource);
        ConnectorModel duplicate = new ConnectorModel(secondSource, "ID_FIRST");
        duplicate.setMiddleLabel("A");
        secondLinks.addArrowlink(duplicate);
        ConnectorModel equalDuplicate = new ConnectorModel(secondSource, "ID_FIRST");
        equalDuplicate.setMiddleLabel("A");
        secondLinks.addArrowlink(equalDuplicate);

        try (GraphAdapterTestSupport.LeaseScope scope = GraphAdapterTestSupport.leaseScope(temporaryFolder, map, 1L)) {
            MapSnapshot snapshot = factory.snapshot(scope.lease());

            assertThat(snapshot.connectors()).hasSize(7);
            assertThat(snapshot.connectors()).extracting(ConnectorSnapshot::occurrence)
                .containsExactly(0, 1, 2, 3, 4, 0, 1);
            assertThat(snapshot.connectors()).extracting(c -> c.descriptor().target().nodeId().value())
                .containsExactly("ID_SECOND", "ID_SECOND", "ID_SECOND", "ID_SECOND", "ID_SECOND", "ID_FIRST",
                    "ID_FIRST");

            ConnectorSnapshot noneSnapshot = snapshot.connectors().get(0);
            assertThat(noneSnapshot.descriptor().arrowAtSource()).isFalse();
            assertThat(noneSnapshot.descriptor().arrowAtTarget()).isFalse();
            assertThat(noneSnapshot.descriptor().sourceLabel()).isEqualTo("src text");
            assertThat(noneSnapshot.descriptor().middleLabel()).isEmpty();
            assertThat(noneSnapshot.descriptor().targetLabel()).isEmpty();

            ConnectorSnapshot forwardSnapshot = snapshot.connectors().get(1);
            assertThat(forwardSnapshot.descriptor().arrowAtSource()).isFalse();
            assertThat(forwardSnapshot.descriptor().arrowAtTarget()).isTrue();
            assertThat(forwardSnapshot.descriptor().middleLabel()).isEqualTo("mid title");

            ConnectorSnapshot backwardSnapshot = snapshot.connectors().get(2);
            assertThat(backwardSnapshot.descriptor().arrowAtSource()).isTrue();
            assertThat(backwardSnapshot.descriptor().arrowAtTarget()).isFalse();
            assertThat(backwardSnapshot.descriptor().targetLabel()).isEqualTo("tgt label");

            ConnectorSnapshot bothSnapshot = snapshot.connectors().get(3);
            assertThat(bothSnapshot.descriptor().arrowAtSource()).isTrue();
            assertThat(bothSnapshot.descriptor().arrowAtTarget()).isTrue();
            assertThat(bothSnapshot.descriptor().middleLabel()).isEqualTo("a b");

            ConnectorSnapshot defaultSnapshot = snapshot.connectors().get(4);
            assertThat(defaultSnapshot.descriptor().arrowAtSource()).isFalse();
            assertThat(defaultSnapshot.descriptor().arrowAtTarget()).isTrue();
            assertThat(defaultSnapshot.descriptor().middleLabel()).isEqualTo("x y");

            ConnectorSnapshot firstDuplicate = snapshot.connectors().get(5);
            ConnectorSnapshot secondDuplicate = snapshot.connectors().get(6);
            assertThat(firstDuplicate.descriptor().sourceLabel()).isEmpty();
            assertThat(firstDuplicate.descriptor().targetLabel()).isEmpty();
            assertThat(firstDuplicate.descriptor().middleLabel()).isEqualTo("A");
            assertThat(firstDuplicate.descriptor()).isEqualTo(secondDuplicate.descriptor());
            assertThat(firstDuplicate.key()).isNotEqualTo(secondDuplicate.key());
        }
    }

    @Test
    public void keepsOccurrenceGapsWhenAnEarlierConnectorTargetIsUnreachable() throws Exception {
        GraphAdapterTestSupport.TestMapModel map = new GraphAdapterTestSupport.TestMapModel("connector gaps");
        NodeModel root = GraphAdapterTestSupport.node(map, "root", "ID_ROOT");
        map.setRoot(root);
        NodeModel source = GraphAdapterTestSupport.node(map, "source", "ID_SOURCE");
        root.insert(source);
        NodeModel hiddenTarget = GraphAdapterTestSupport.node(map, "hidden", "ID_TGT_HIDDEN");
        hiddenTarget.addExtension(NodeVisibility.HIDDEN);
        root.insert(hiddenTarget);
        NodeModel visibleTarget = GraphAdapterTestSupport.node(map, "visible", "ID_TGT_VISIBLE");
        root.insert(visibleTarget);

        NodeLinks links = NodeLinks.createLinkExtension(source);
        links.setLocalHyperlink(source, "ID_OTHER");
        links.addArrowlink(new ConnectorModel(source, "ID_TGT_HIDDEN"));
        links.addArrowlink(new ConnectorModel(source, "ID_TGT_VISIBLE"));

        try (GraphAdapterTestSupport.LeaseScope scope = GraphAdapterTestSupport.leaseScope(temporaryFolder, map, 1L)) {
            MapSnapshot hidden = factory.snapshot(scope.lease());
            assertThat(hidden.connectors()).hasSize(1);
            ConnectorSnapshot visibleOnly = hidden.connectors().get(0);
            assertThat(visibleOnly.occurrence()).isEqualTo(1);
            assertThat(visibleOnly.descriptor().target().nodeId().value()).isEqualTo("ID_TGT_VISIBLE");

            hiddenTarget.removeExtension(NodeVisibility.class);
            MapSnapshot revealed = factory.snapshot(scope.lease());
            assertThat(revealed.connectors()).extracting(ConnectorSnapshot::occurrence).containsExactly(0, 1);
            assertThat(revealed.connectors()).extracting(c -> c.descriptor().target().nodeId().value())
                .containsExactly("ID_TGT_HIDDEN", "ID_TGT_VISIBLE");
            assertThat(revealed.connectors().get(1).key()).isEqualTo(visibleOnly.key());
        }
    }

    @Test
    public void omitsConnectorsWhoseSourceOrTargetIsExcludedWithoutReadingTheirLabels() throws Exception {
        GraphAdapterTestSupport.TestMapModel map = new GraphAdapterTestSupport.TestMapModel("excluded connectors");
        NodeModel root = GraphAdapterTestSupport.node(map, "root", "ID_ROOT");
        map.setRoot(root);

        NodeModel hiddenSummarySource = new LinkHostileNodeModel("", map);
        hiddenSummarySource.setID("ID_HIDDEN_SUMMARY");
        hiddenSummarySource.addExtension(SummaryNodeFlag.SUMMARY);
        NodeLinks hiddenLinks = new NodeLinks();
        hiddenLinks.addArrowlink(new HostileConnectorModel(hiddenSummarySource, "ID_TARGET"));
        hiddenSummarySource.addExtension(hiddenLinks);
        hiddenSummarySource.insert(GraphAdapterTestSupport.node(map, "summary child", "ID_SUMMARY_CHILD"));
        root.insert(hiddenSummarySource);

        NodeModel visibleSource = GraphAdapterTestSupport.node(map, "visible source", "ID_VISIBLE");
        root.insert(visibleSource);
        NodeLinks.createLinkExtension(visibleSource)
            .addArrowlink(new HostileConnectorModel(visibleSource, "ID_HIDDEN_SUMMARY"));

        NodeModel ordinaryHidden = GraphAdapterTestSupport.node(map, "ordinary hidden", "ID_ORDINARY_HIDDEN");
        ordinaryHidden.addExtension(NodeVisibility.HIDDEN);
        root.insert(ordinaryHidden);
        NodeLinks.createLinkExtension(ordinaryHidden).addArrowlink(new ConnectorModel(ordinaryHidden, "ID_TARGET"));

        NodeModel target = GraphAdapterTestSupport.node(map, "target", "ID_TARGET");
        root.insert(target);

        try (GraphAdapterTestSupport.LeaseScope scope = GraphAdapterTestSupport.leaseScope(temporaryFolder, map, 1L)) {
            MapSnapshot omitted = factory.snapshot(scope.lease());
            assertThat(omitted.connectors()).isEmpty();

            root.addExtension(NodeVisibilityConfiguration.SHOW_HIDDEN_NODES);
            MapSnapshot restored = factory.snapshot(scope.lease());
            assertThat(restored.connectors()).hasSize(1);
            ConnectorSnapshot connector = restored.connectors().get(0);
            assertThat(connector.occurrence()).isZero();
            assertThat(connector.descriptor().source().persistedReference().get().nodeId().value())
                .isEqualTo("ID_ORDINARY_HIDDEN");
            assertThat(connector.descriptor().target().nodeId().value()).isEqualTo("ID_TARGET");
        }
    }

    @Test
    public void omitsPersistentSelfConnectorsWithoutRenumberingLaterContributors() throws Exception {
        GraphAdapterTestSupport.TestMapModel map = new GraphAdapterTestSupport.TestMapModel("self connectors");
        NodeModel root = GraphAdapterTestSupport.node(map, "root", "ID_ROOT");
        map.setRoot(root);
        NodeModel source = GraphAdapterTestSupport.node(map, "source", "ID_SELF_SOURCE");
        root.insert(source);
        NodeModel target = GraphAdapterTestSupport.node(map, "target", "ID_TARGET");
        root.insert(target);

        NodeLinks links = NodeLinks.createLinkExtension(source);
        links.addArrowlink(new ConnectorModel(source, "ID_SELF_SOURCE"));
        links.addArrowlink(new ConnectorModel(source, "ID_TARGET"));

        try (GraphAdapterTestSupport.LeaseScope scope = GraphAdapterTestSupport.leaseScope(temporaryFolder, map, 1L)) {
            MapSnapshot snapshot = factory.snapshot(scope.lease());
            assertThat(snapshot.connectors()).hasSize(1);
            ConnectorSnapshot connector = snapshot.connectors().get(0);
            assertThat(connector.occurrence()).isEqualTo(1);
            assertThat(connector.descriptor().target().nodeId().value()).isEqualTo("ID_TARGET");
        }
    }

    @Test
    public void supportsTransientSourcesWithoutAssigningIds() throws Exception {
        GraphAdapterTestSupport.CountingMapModel counting = new GraphAdapterTestSupport.CountingMapModel(
            "counting connectors");
        NodeModel root = GraphAdapterTestSupport.node(counting, "root", "ID_CC_ROOT");
        counting.setRoot(root);
        NodeModel idless = GraphAdapterTestSupport.node(counting, "idless connector source", null);
        root.insert(idless);
        NodeModel target = GraphAdapterTestSupport.node(counting, "target", "ID_CC_TARGET");
        root.insert(target);
        NodeLinks.createLinkExtension(idless).addArrowlink(new ConnectorModel(idless, "ID_CC_TARGET"));

        try (GraphAdapterTestSupport.LeaseScope scope = GraphAdapterTestSupport.leaseScope(temporaryFolder, counting, 1L)) {
            assertThat(idless.getID()).isNull();
            assertThat(counting.registryCalls()).isZero();

            MapSnapshot snapshot = factory.snapshot(scope.lease());

            assertThat(idless.getID()).isNull();
            assertThat(counting.registryCalls()).isZero();
            assertThat(snapshot.connectors()).hasSize(1);
            ConnectorSnapshot connector = snapshot.connectors().get(0);
            assertThat(connector.occurrence()).isZero();
            assertThat(connector.descriptor().source()).isEqualTo(
                SourceNodeKey.transientPath(scope.lease().mapReferenceId(), Arrays.asList(0)));
            assertThat(connector.descriptor().target().nodeId().value()).isEqualTo("ID_CC_TARGET");
        }
    }

    @Test
    public void rejectsMismatchedOrStaleSafeSnapshots() throws Exception {
        GraphAdapterTestSupport.TestMapModel mapA = new GraphAdapterTestSupport.TestMapModel("connector map A");
        NodeModel rootA = GraphAdapterTestSupport.node(mapA, "root", "ID_ROOT_A");
        mapA.setRoot(rootA);
        GraphAdapterTestSupport.TestMapModel mapB = new GraphAdapterTestSupport.TestMapModel("connector map B");
        NodeModel rootB = GraphAdapterTestSupport.node(mapB, "root", "ID_ROOT_B");
        mapB.setRoot(rootB);
        NodeModel hostileSource = GraphAdapterTestSupport.node(mapB, "hostile", "ID_HOSTILE_B");
        rootB.insert(hostileSource);
        NodeLinks.createLinkExtension(hostileSource)
            .addArrowlink(new HostileConnectorModel(hostileSource, "ID_TARGET_B"));
        rootB.insert(GraphAdapterTestSupport.node(mapB, "target", "ID_TARGET_B"));

        try (GraphAdapterTestSupport.LeaseScope leaseA = GraphAdapterTestSupport.leaseScope(temporaryFolder, mapA, 1L);
                GraphAdapterTestSupport.LeaseScope leaseB = GraphAdapterTestSupport.leaseScope(temporaryFolder, mapB,
                    2L)) {
            MapSnapshot safeNodesA = factory.snapshot(leaseA.lease());
            assertThatThrownBy(() -> connectorFactory.snapshotReachableConnectors(leaseB.lease(), safeNodesA))
                .isInstanceOf(IllegalArgumentException.class);
        }

        GraphAdapterTestSupport.TestMapModel orderMap = new GraphAdapterTestSupport.TestMapModel("connector order");
        NodeModel orderRoot = GraphAdapterTestSupport.node(orderMap, "root", "ID_ORDER_ROOT");
        orderMap.setRoot(orderRoot);
        orderRoot.insert(GraphAdapterTestSupport.node(orderMap, "child", "ID_ORDER_CHILD"));
        try (GraphAdapterTestSupport.LeaseScope first = GraphAdapterTestSupport.leaseScope(temporaryFolder, orderMap,
                5L);
                GraphAdapterTestSupport.LeaseScope second = GraphAdapterTestSupport.leaseScope(temporaryFolder,
                    orderMap, 6L)) {
            orderMap.enforceEdt(first.edt());
            MapSnapshot safeNodes = factory.snapshot(first.lease());
            assertThatThrownBy(() -> connectorFactory.snapshotReachableConnectors(second.lease(), safeNodes))
                .isInstanceOf(IllegalArgumentException.class);
        }

        GraphAdapterTestSupport.CountingMapModel stale = new GraphAdapterTestSupport.CountingMapModel(
            "stale connectors");
        NodeModel staleRoot = GraphAdapterTestSupport.node(stale, "root", "ID_STALE_ROOT");
        stale.setRoot(staleRoot);
        NodeModel staleSource = GraphAdapterTestSupport.node(stale, "source", "ID_STALE_SOURCE");
        staleRoot.insert(staleSource);
        staleRoot.insert(GraphAdapterTestSupport.node(stale, "target", "ID_STALE_TARGET"));

        try (GraphAdapterTestSupport.LeaseScope scope = GraphAdapterTestSupport.leaseScope(temporaryFolder, stale, 1L)) {
            MapSnapshot safeNodes = factory.snapshot(scope.lease());
            NodeLinks.createLinkExtension(staleSource)
                .addArrowlink(new HostileConnectorModel(staleSource, "ID_STALE_TARGET"));

            staleSource.addExtension(NodeVisibility.HIDDEN);
            assertThatThrownBy(() -> connectorFactory.snapshotReachableConnectors(scope.lease(), safeNodes))
                .isInstanceOf(IllegalArgumentException.class);
            assertThat(stale.registryCalls()).isZero();
            staleSource.removeExtension(NodeVisibility.class);

            staleRoot.insert(GraphAdapterTestSupport.node(stale, "late child", "ID_STALE_LATE"));
            assertThatThrownBy(() -> connectorFactory.snapshotReachableConnectors(scope.lease(), safeNodes))
                .isInstanceOf(IllegalArgumentException.class);
            assertThat(stale.registryCalls()).isZero();
        }
    }

    @Test
    public void omitsAConnectorToARelockedStaleIndexedTarget() throws Exception {
        try (GraphAdapterTestSupport.HeadlessMapScope headless = new GraphAdapterTestSupport.HeadlessMapScope()) {
            GraphAdapterTestSupport.RelockedMap relocked = headless.relockedMap();
            NodeModel safeSibling = relocked.map().getNodeForID("ID_LOCK_SAFE_SIBLING");

            assertThat(relocked.map().getNodeForID("ID_LOCKED_SECRET").getUserObject())
                .isEqualTo("RELOCKED_SECRET_SENTINEL");

            try (GraphAdapterTestSupport.LeaseScope lease = headless.leaseScope(temporaryFolder, relocked.map(), 1L)) {
                MapSnapshot safeNodes = factory.snapshot(lease.lease());
                ConnectorModel connector = new ConnectorModel(safeSibling, "ID_LOCKED_SECRET");
                connector.setMiddleLabel("RELOCKED_CONNECTOR_SENTINEL");
                NodeLinks.createLinkExtension(safeSibling).addArrowlink(connector);

                List<ConnectorSnapshot> standalone = connectorFactory.snapshotReachableConnectors(lease.lease(),
                    safeNodes);
                assertThat(standalone).extracting(c -> c.descriptor().middleLabel())
                    .doesNotContain("RELOCKED_CONNECTOR_SENTINEL");
                assertThat(standalone).isEmpty();

                MapSnapshot integrated = factory.snapshot(lease.lease());
                assertThat(integrated.connectors()).isEmpty();
                assertThat(connectorLabels(integrated)).doesNotContain("RELOCKED_CONNECTOR_SENTINEL");

                relocked.encryption().unlock();
                MapSnapshot unlocked = factory.snapshot(lease.lease());
                assertThat(unlocked.connectors()).hasSize(1);
                ConnectorSnapshot restored = unlocked.connectors().get(0);
                assertThat(restored.occurrence()).isZero();
                assertThat(restored.descriptor().source().persistedReference().get().nodeId().value())
                    .isEqualTo("ID_LOCK_SAFE_SIBLING");
                assertThat(restored.descriptor().target().nodeId().value()).isEqualTo("ID_LOCKED_SECRET");
                assertThat(restored.descriptor().middleLabel()).isEqualTo("RELOCKED_CONNECTOR_SENTINEL");
            }
        }
    }

    private static List<String> connectorLabels(MapSnapshot snapshot) {
        List<String> labels = new ArrayList<String>();
        for (ConnectorSnapshot connector : snapshot.connectors()) {
            ConnectorDescriptor descriptor = connector.descriptor();
            labels.add(descriptor.sourceLabel());
            labels.add(descriptor.middleLabel());
            labels.add(descriptor.targetLabel());
        }
        return labels;
    }

    private static final class HostileConnectorModel extends ConnectorModel {
        HostileConnectorModel(NodeModel source, String targetID) {
            super(source, targetID);
        }

        @Override
        public Optional<String> getSourceLabel() {
            throw new AssertionError("Excluded connector labels must not be read");
        }

        @Override
        public Optional<String> getMiddleLabel() {
            throw new AssertionError("Excluded connector labels must not be read");
        }

        @Override
        public Optional<String> getTargetLabel() {
            throw new AssertionError("Excluded connector labels must not be read");
        }

        @Override
        public Optional<ConnectorArrows> getArrows() {
            throw new AssertionError("Excluded connector arrows must not be read");
        }
    }

    private static final class LinkHostileNodeModel extends NodeModel {
        LinkHostileNodeModel(Object userObject, MapModel map) {
            super(userObject, map);
        }

        @Override
        public <T extends IExtension> T getExtension(Class<T> clazz) {
            if (clazz == NodeLinks.class) {
                throw new AssertionError("Excluded source links must not be read");
            }
            return super.getExtension(clazz);
        }
    }
}
