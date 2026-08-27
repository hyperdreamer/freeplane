package org.freeplane.plugin.graph.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.freeplane.core.io.ReadManager;
import org.freeplane.core.io.UnknownElementWriter;
import org.freeplane.core.io.UnknownElements;
import org.freeplane.core.io.WriteManager;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.undo.IActor;
import org.freeplane.core.undo.IUndoHandler;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.cloud.CloudModel;
import org.freeplane.features.cloud.CloudShape;
import org.freeplane.features.filter.hidden.NodeVisibility;
import org.freeplane.features.map.INodeDuplicator;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.MapReader;
import org.freeplane.features.map.MapWriter;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.mindmapmode.MMapController;
import org.freeplane.features.map.mindmapmode.MMapModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.ui.IMapViewManager;
import org.freeplane.plugin.graph.adapter.MapLease;
import org.freeplane.plugin.graph.adapter.MapLeaseManager;
import org.freeplane.plugin.graph.adapter.MapOperationalState;
import org.freeplane.plugin.graph.adapter.MapSnapshotFactory;
import org.freeplane.plugin.graph.geometry.AwtGeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.GraphGeometryEngine;
import org.freeplane.plugin.graph.geometry.LabelPlacement;
import org.freeplane.plugin.graph.geometry.LabelPlacementEngine;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.group.GraphGroupController;
import org.freeplane.plugin.graph.group.GraphGroupMarkerPainter;
import org.freeplane.plugin.graph.group.GraphGroupModel;
import org.freeplane.plugin.graph.layout.LayoutCalibration;
import org.freeplane.plugin.graph.layout.LayoutEngine;
import org.freeplane.plugin.graph.layout.LayoutFrame;
import org.freeplane.plugin.graph.layout.LayoutRequest;
import org.freeplane.plugin.graph.layout.graphstream.GraphStreamLayoutFactory;
import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.EdgeContributor;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.ProjectionDiff;
import org.freeplane.plugin.graph.projection.ProjectionEngine;
import org.freeplane.plugin.graph.projection.RecoverableReason;
import org.freeplane.plugin.graph.projection.RelationshipResolution;
import org.freeplane.plugin.graph.projection.RelationshipStatus;
import org.freeplane.plugin.graph.projection.input.ConnectorDescriptor;
import org.freeplane.plugin.graph.projection.input.ConnectorSnapshot;
import org.freeplane.plugin.graph.projection.input.MapAvailability;
import org.freeplane.plugin.graph.projection.input.MapSnapshot;
import org.freeplane.plugin.graph.projection.input.NodeSnapshot;
import org.freeplane.plugin.graph.projection.input.ProjectionInput;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.AtomicWorkspaceWriter;
import org.freeplane.plugin.graph.workspace.GraphCommandResult;
import org.freeplane.plugin.graph.workspace.GraphWorkspaceStore;
import org.freeplane.plugin.graph.workspace.WorkspaceCommands;
import org.freeplane.plugin.graph.workspace.WorkspaceTransition;
import org.freeplane.plugin.graph.workspace.WorkspaceUriResolver;
import org.freeplane.plugin.graph.workspace.io.WorkspaceMigration;
import org.freeplane.plugin.graph.workspace.io.WorkspaceMigrationRegistry;
import org.freeplane.plugin.graph.workspace.io.WorkspaceXmlCodec;
import org.freeplane.plugin.graph.workspace.model.DisplaySettings;
import org.freeplane.plugin.graph.workspace.model.GraphRelationshipRecord;
import org.freeplane.plugin.graph.workspace.model.MapReference;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.freeplane.plugin.graph.workspace.model.PinRecord;
import org.freeplane.plugin.graph.workspace.model.RelationshipDirection;
import org.freeplane.plugin.graph.workspace.model.RelationshipId;
import org.freeplane.plugin.graph.workspace.model.UnknownXml;
import org.freeplane.plugin.graph.workspace.model.Viewport;
import org.freeplane.plugin.graph.workspace.model.WorkspaceDocument;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;
import org.freeplane.view.swing.map.NodeView;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;

public class GraphWorkspaceModelAcceptanceShould {
    private static final Color CORAL = new Color(0xDF, 0x62, 0x5D);
    private static final MapReferenceId MAP_ONE =
        MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    private static final MapReferenceId MAP_TWO =
        MapReferenceId.of("00000000-0000-0000-0000-000000000002");
    private static final WorkspaceId WORKSPACE =
        WorkspaceId.of("00000000-0000-0000-0000-000000000010");

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private MockedStatic<ResourceController> resourceController;
    private MockedStatic<TextUtils> textUtils;

    @Before
    public void setUpGraphGroupResources() {
        resourceController = org.mockito.Mockito.mockStatic(ResourceController.class);
        resourceController.when(ResourceController::getResourceController).thenReturn(mock(ResourceController.class));
        textUtils = org.mockito.Mockito.mockStatic(TextUtils.class);
        textUtils.when(() -> TextUtils.getRawText(any(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        textUtils.when(() -> TextUtils.getRawText(any(String.class), any(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(1));
    }

    @After
    public void tearDownGraphGroupResources() {
        textUtils.close();
        resourceController.close();
    }

    @Test
    public void scenario01_reopenRestoresMapsViewportPinsColorsAndSettings() throws Exception {
        Path workspace = temporaryFolder.newFolder("scenario01").toPath().resolve("workspace.fpg");
        ScheduledThreadPoolExecutor scheduler = scheduler();
        GraphWorkspaceStore store = GraphWorkspaceStore.create(workspace, codec(), AtomicWorkspaceWriter.standard(),
            scheduler);
        try {
            assertApplied(store.execute(WorkspaceCommands.addMap(MAP_ONE, URI.create("maps/one.mm"))));
            assertApplied(store.execute(WorkspaceCommands.addMap(MAP_TWO, URI.create("maps/two.mm"))));
            Viewport viewport = Viewport.of(120.0, -45.0, 1.75, noUnknownXml());
            DisplaySettings settings = DisplaySettings.of(false, DisplaySettings.CanvasTheme.DARK, false, false,
                noUnknownXml());
            NodeReference pinnedNode = reference(MAP_ONE, "pin-one");

            assertApplied(store.updateViewport(viewport));
            assertApplied(store.execute(WorkspaceCommands.pin(pinnedNode, 12.5, -9.25)));
            assertApplied(store.execute(WorkspaceCommands.setDisplaySettings(settings)));
            store.saveNow();
            WorkspaceDocument expected = store.currentDocument();
            store.close();

            GraphWorkspaceStore reopened = GraphWorkspaceStore.open(workspace, codec(), AtomicWorkspaceWriter.standard(),
                scheduler);
            try {
                assertThat(reopened.currentDocument()).isEqualTo(expected);
                assertThat(reopened.currentDocument().maps()).extracting(MapReference::id)
                    .containsExactly(MAP_ONE, MAP_TWO);
                assertThat(reopened.currentDocument().maps()).extracting(MapReference::color)
                    .containsExactly("#4E79A7", "#F28E2B");
                assertThat(reopened.currentDocument().viewport()).isEqualTo(viewport);
                assertThat(reopened.currentDocument().pins()).extracting(PinRecord::node)
                    .containsExactly(pinnedNode);
                assertThat(reopened.currentDocument().displaySettings()).isEqualTo(settings);
            }
            finally {
                reopened.close();
            }
        }
        finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    public void scenario02_projectsOnlyGroupMarkedBoundariesAndKeepsPlainSubtreesHidden()
            throws Exception {
        AdapterFixture adapter = new AdapterFixture(temporaryFolder.newFile("scenario02.fpg").toPath());
        try {
            NodeModel root = adapter.root("root", "ID_ROOT");
            NodeModel group = adapter.child(root, "group", "ID_GROUP");
            group.addExtension(new GraphGroupModel());
            adapter.child(group, "inside group", "ID_GROUP_CHILD");
            adapter.child(root, "visible leaf", "ID_VISIBLE_LEAF");
            NodeModel visibleParent = adapter.child(root, "visible parent", "ID_VISIBLE_PARENT");
            NodeModel hiddenOnlyChild = adapter.child(visibleParent, "hidden child", "ID_HIDDEN_CHILD");
            hiddenOnlyChild.addExtension(NodeVisibility.HIDDEN);

            MapSnapshot snapshot = adapter.snapshot(MAP_ONE, 1L);
            WorkspaceDocument workspace = workspace(registration(MAP_ONE, 1L, true));
            GraphProjection projection = project(workspace, snapshot);

            assertThat(projection.nodes()).isEmpty();
            assertThat(projection.enclosures()).hasSize(2);
            assertThat(projection.enclosures().get(0).endpointKeys())
                .containsExactly(EnclosureKey.of(source(MAP_ONE, "ID_ROOT")));
            assertThat(projection.enclosures().get(0).mapRoot()).isTrue();
            assertThat(projection.enclosures().get(1).endpointKeys())
                .containsExactly(EnclosureKey.of(source(MAP_ONE, "ID_GROUP")));
            assertThat(projection.enclosures().get(1).parentHull().get())
                .isEqualTo(projection.enclosures().get(0).hullKey());
            for (ProjectedEnclosure enclosure : projection.enclosures()) {
                assertThat(enclosure.directNodes()).isEmpty();
                for (EnclosureKey endpoint : enclosure.endpointKeys()) {
                    assertThat(endpoint.source().persistedReference().get().nodeId().value())
                        .isNotIn("ID_GROUP_CHILD", "ID_HIDDEN_CHILD", "ID_VISIBLE_LEAF",
                            "ID_VISIBLE_PARENT");
                }
            }
        }
        finally {
            adapter.close();
        }
    }

    @Test
    public void scenario03_preservesRequiredEnclosuresAndInteriorFixtureLabels() {
        NodeSnapshot firstLeaf = node(MAP_ONE, "first-leaf", "First leaf", true, false, false);
        NodeSnapshot firstLeafTwo = node(MAP_ONE, "first-leaf-two", "First leaf two", true, false, false);
        NodeSnapshot secondLeaf = node(MAP_ONE, "second-leaf", "Second leaf", true, false, false);
        NodeSnapshot secondLeafTwo = node(MAP_ONE, "second-leaf-two", "Second leaf two", true, false, false);
        NodeSnapshot firstInterior = node(MAP_ONE, "first-interior", "First interior", false, true, false,
            firstLeaf, firstLeafTwo);
        NodeSnapshot secondInterior = node(MAP_ONE, "second-interior", "Second interior", false, true, false,
            secondLeaf, secondLeafTwo);
        NodeSnapshot root = node(MAP_ONE, "root", "Map fixture", false, false, false,
            firstInterior, secondInterior);
        MapSnapshot firstMap = map(MAP_ONE, 1, "Fixture map", root);
        MapSnapshot secondMap = map(MAP_TWO, 2, "Second map",
            node(MAP_TWO, "second-root", "Second root", true, false, false));
        GraphProjection projection = project(workspace(registration(MAP_ONE, 1L, true),
            registration(MAP_TWO, 2L, true)), firstMap, secondMap);

        ProjectedEnclosure mapRoot = enclosure(projection, MAP_ONE, "root");
        ProjectedEnclosure first = enclosure(projection, MAP_ONE, "first-interior");
        ProjectedEnclosure second = enclosure(projection, MAP_ONE, "second-interior");
        GraphGeometry labeled = labelsFor(projection);

        assertThat(mapRoot.boundaryTier()).isEqualTo(BoundaryTier.EMPHATIC);
        assertThat(first.boundaryTier()).isEqualTo(BoundaryTier.SUBTLE);
        assertThat(second.boundaryTier()).isEqualTo(BoundaryTier.SUBTLE);
        assertThat(labeled.labels().values()).extracting(LabelPlacement::displayText)
            .containsExactlyInAnyOrder("Map fixture", "First interior", "Second interior", "Second root");
        assertThat(labeled.labels().values()).filteredOn(placement -> "Map fixture".equals(placement.displayText()))
            .extracting(LabelPlacement::mode).containsExactly(LabelPlacement.Mode.INTERIOR);
        assertThat(labeled.labels().values()).filteredOn(placement -> !"Map fixture".equals(placement.displayText()))
            .extracting(LabelPlacement::mode).containsOnly(LabelPlacement.Mode.INTERIOR);
    }

    @Test
    public void scenario04_nestedMarkersProjectNestedBoundaries() {
        NodeSnapshot innerLeaf = node(MAP_ONE, "inner-leaf", "Inner leaf", true, false, false);
        NodeSnapshot inner = node(MAP_ONE, "inner", "Inner group", false, true, false, innerLeaf);
        NodeSnapshot outerLeaf = node(MAP_ONE, "outer-leaf", "Outer leaf", true, false, false);
        NodeSnapshot outer = node(MAP_ONE, "outer", "Outer group", false, true, false, inner, outerLeaf);
        NodeSnapshot root = node(MAP_ONE, "root", "Root", false, false, false, outer);
        WorkspaceDocument workspace = workspace(registration(MAP_ONE, 1L, true));

        GraphProjection outerActive = project(workspace, map(MAP_ONE, 1, "Map", root));
        assertThat(outerActive.nodes()).isEmpty();
        assertThat(outerActive.enclosures()).hasSize(3);
        assertThat(outerActive.enclosures().get(1).endpointKeys())
            .containsExactly(EnclosureKey.of(source(MAP_ONE, "outer")));
        assertThat(outerActive.enclosures().get(2).endpointKeys())
            .containsExactly(EnclosureKey.of(source(MAP_ONE, "inner")));
        assertThat(outerActive.enclosures().get(2).parentHull().get())
            .isEqualTo(outerActive.enclosures().get(1).hullKey());

        NodeSnapshot unmarkedOuter = node(MAP_ONE, "outer", "Outer group", false, false, false, inner,
            outerLeaf);
        GraphProjection innerReactivated = project(workspace,
            map(MAP_ONE, 1, "Map", node(MAP_ONE, "root", "Root", false, false, false, unmarkedOuter)));
        assertThat(innerReactivated.enclosures()).hasSize(2);
        assertThat(innerReactivated.enclosures().get(1).endpointKeys())
            .containsExactly(EnclosureKey.of(source(MAP_ONE, "inner")));
        assertThat(innerReactivated.enclosures().get(1).parentHull().get())
            .isEqualTo(innerReactivated.enclosures().get(0).hullKey());
    }

    @Test
    public void scenario05_consolidatesDuplicateConnectorsWithoutChangingTheirRecords() {
        NodeSnapshot source = node(MAP_ONE, "source", "Source", true, true, false);
        NodeSnapshot target = node(MAP_ONE, "target", "Target", true, true, false);
        MapSnapshot snapshot = map(MAP_ONE, 1, "Map", node(MAP_ONE, "root", "Root", false, false, false,
            source, target)).withConnectors(Arrays.asList(
                connector(0, MAP_ONE, "source", "target", false, true),
                connector(1, MAP_ONE, "source", "target", false, true)));

        GraphProjection projection = project(workspace(registration(MAP_ONE, 1L, true)), snapshot);

        assertThat(snapshot.connectors()).hasSize(2);
        assertThat(projection.edges()).hasSize(1);
        assertThat(projection.edges().get(0).contributorCount()).isEqualTo(2);
        assertThat(projection.edges().get(0).hasMultiplicityCue()).isTrue();
    }

    @Test
    public void scenario06_unionsOppositeDirectedContributorsIntoTwoArrowheads() {
        NodeSnapshot first = node(MAP_ONE, "first", "First", true, true, false);
        NodeSnapshot second = node(MAP_ONE, "second", "Second", true, true, false);
        MapSnapshot snapshot = map(MAP_ONE, 1, "Map", node(MAP_ONE, "root", "Root", false, false, false,
            first, second)).withConnectors(Arrays.asList(
                connector(0, MAP_ONE, "first", "second", false, true),
                connector(0, MAP_ONE, "second", "first", false, true)));

        GraphProjection projection = project(workspace(registration(MAP_ONE, 1L, true)), snapshot);

        assertThat(projection.edges()).hasSize(1);
        ProjectedEdge edge = projection.edges().get(0);
        assertThat(edge.arrowAtFirst()).isTrue();
        assertThat(edge.arrowAtSecond()).isTrue();
    }

    @Test
    public void scenario07_omitsConnectorsCollapsedInsideAnActiveGroup() {
        NodeSnapshot descendant = node(MAP_ONE, "descendant", "Descendant", true, false, false);
        NodeSnapshot group = node(MAP_ONE, "group", "Group", false, true, false, descendant);
        MapSnapshot snapshot = map(MAP_ONE, 1, "Map", node(MAP_ONE, "root", "Root", false, false, false,
            group)).withConnectors(Collections.singletonList(
                connector(0, MAP_ONE, "group", "descendant", true, true)));

        GraphProjection projection = project(workspace(registration(MAP_ONE, 1L, true)), snapshot);

        assertThat(projection.nodes()).isEmpty();
        assertThat(projection.enclosures()).hasSize(2);
        assertThat(projection.enclosures().get(1).endpointKeys())
            .containsExactly(EnclosureKey.of(source(MAP_ONE, "group")));
        assertThat(projection.edges()).isEmpty();
        assertThat(snapshot.connectors()).hasSize(1);
    }

    @Test
    public void scenario10_removedMapMakesRelationshipDormantThenReactivatesItExactly() {
        NodeReference firstNode = reference(MAP_ONE, "first");
        NodeReference secondNode = reference(MAP_TWO, "second");
        GraphRelationshipRecord relationship = relationship(1L, firstNode, secondNode, RelationshipDirection.FORWARD);
        WorkspaceDocument active = workspace(Arrays.asList(registration(MAP_ONE, 1L, true),
            registration(MAP_TWO, 2L, true)), Collections.singletonList(relationship),
            Collections.<PinRecord>emptyList());
        MapSnapshot firstMap = map(MAP_ONE, 1, "First", node(MAP_ONE, "first", "First", true, false, false));
        MapSnapshot secondMap = map(MAP_TWO, 2, "Second", node(MAP_TWO, "second", "Second", true, false, false));

        GraphProjection initiallyActive = project(active, availability(active, MapAvailability.AVAILABLE,
            MapAvailability.AVAILABLE), firstMap, secondMap);
        WorkspaceDocument inactive = WorkspaceCommands.removeMap(MAP_TWO).apply(active).after();
        GraphProjection dormant = project(inactive, availability(inactive, MapAvailability.AVAILABLE,
            MapAvailability.INACTIVE), firstMap, secondMap);
        WorkspaceDocument reactivated = WorkspaceCommands.reactivateMap(MAP_TWO).apply(inactive).after();
        GraphProjection restored = project(reactivated, availability(reactivated, MapAvailability.AVAILABLE,
            MapAvailability.AVAILABLE), firstMap, secondMap);

        assertThat(initiallyActive.relationshipResolutions().get(0).status()).isEqualTo(RelationshipStatus.ACTIVE);
        assertThat(dormant.edges()).isEmpty();
        assertThat(dormant.relationshipResolutions().get(0).status())
            .isEqualTo(RelationshipStatus.UNRESOLVED_RECOVERABLE);
        assertThat(dormant.relationshipResolutions().get(0).recoverableReasons())
            .containsExactly(RecoverableReason.MAP_INACTIVE);
        assertThat(restored.relationshipResolutions().get(0).status()).isEqualTo(RelationshipStatus.ACTIVE);
        assertThat(restored.edges()).hasSize(1);
        assertThat(restored.edges().get(0).contributors()).extracting(EdgeContributor::graphRelationship)
            .extracting(value -> value.get().id()).containsExactly(relationship.id());
    }

    @Test
    public void scenario12_ungroupingLeavesRelationshipsUnresolvedUntilRemarked() {
        NodeReference groupReference = reference(MAP_ONE, "group");
        GraphRelationshipRecord relationship = relationship(1L, groupReference, reference(MAP_TWO, "target"),
            RelationshipDirection.FORWARD);
        WorkspaceDocument workspace = workspace(Arrays.asList(registration(MAP_ONE, 1L, true),
            registration(MAP_TWO, 2L, true)), Collections.singletonList(relationship),
            Collections.<PinRecord>emptyList());
        NodeSnapshot child = node(MAP_ONE, "child", "Child", true, false, false);
        NodeSnapshot grouped = node(MAP_ONE, "group", "Group", false, true, false, child);
        MapSnapshot firstGrouped = map(MAP_ONE, 1, "First", node(MAP_ONE, "root", "Root", false, false, false,
            grouped));
        MapSnapshot target = map(MAP_TWO, 2, "Second", node(MAP_TWO, "target", "Target", true, false, false));

        GraphProjection groupedProjection = project(workspace, availability(workspace, MapAvailability.AVAILABLE,
            MapAvailability.AVAILABLE), firstGrouped, target);
        NodeSnapshot ungrouped = node(MAP_ONE, "group", "Group", false, false, false, child);
        GraphProjection ungroupedProjection = project(workspace, availability(workspace, MapAvailability.AVAILABLE,
            MapAvailability.AVAILABLE), map(MAP_ONE, 1, "First",
                node(MAP_ONE, "root", "Root", false, false, false, ungrouped)), target);

        ProjectedEndpointKey groupedSource =
            groupedProjection.edges().get(0).contributors().get(0).projectedSource();
        assertThat(groupedSource.isEnclosure()).isTrue();
        assertThat(groupedSource.enclosure()).contains(EnclosureKey.of(source(MAP_ONE, "group")));
        assertThat(ungroupedProjection.edges()).isEmpty();
        assertThat(ungroupedProjection.relationshipResolutions().get(0).status())
            .isEqualTo(RelationshipStatus.UNRESOLVED_RECOVERABLE);
        assertThat(ungroupedProjection.relationshipResolutions().get(0).recoverableReasons())
            .containsExactly(RecoverableReason.NODE_INACCESSIBLE);
    }

    @Test
    public void scenario13_reopenedPinRemainsFixedWhileItsNeighborSettles() throws Exception {
        Path workspaceFile = temporaryFolder.newFolder("scenario13").toPath().resolve("workspace.fpg");
        ScheduledThreadPoolExecutor scheduler = scheduler();
        GraphWorkspaceStore store = GraphWorkspaceStore.create(workspaceFile, codec(), AtomicWorkspaceWriter.standard(),
            scheduler);
        NodeReference pinned = reference(MAP_ONE, "pinned");
        try {
            assertApplied(store.execute(WorkspaceCommands.addMap(MAP_ONE, URI.create("maps/one.mm"))));
            assertApplied(store.execute(WorkspaceCommands.pin(pinned, 18.0, -11.0)));
            store.saveNow();
            store.close();

            GraphWorkspaceStore reopened = GraphWorkspaceStore.open(workspaceFile, codec(), AtomicWorkspaceWriter.standard(),
                scheduler);
            try {
                MapSnapshot snapshot = map(MAP_ONE, 1, "Map", node(MAP_ONE, "root", "Root", false, false,
                    false, node(MAP_ONE, "pinned", "Pinned", true, true, false),
                    node(MAP_ONE, "neighbor", "Neighbor", true, true, false)));
                GraphProjection projection = project(reopened.currentDocument(), snapshot);
                EnclosureHullKey pinnedHull = EnclosureHullKey.of(Collections.singletonList(
                    EnclosureKey.of(source(MAP_ONE, "pinned"))));
                EnclosureHullKey neighborHull = EnclosureHullKey.of(Collections.singletonList(
                    EnclosureKey.of(source(MAP_ONE, "neighbor"))));

                assertThat(projection.pins()).hasSize(1);
                assertThat(projection.pins().get(0).active()).isTrue();
                assertThat(projection.pins().get(0).projectedNode())
                    .contains(ProjectedNodeKey.of(source(MAP_ONE, "pinned")));
                try (LayoutEngine layout = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
                    LayoutRequest request = LayoutRequest.of(reopened.currentDocument().id(), projection,
                        ProjectionDiff.between(projection, projection), projection.pins());
                    LayoutFrame applied = layout.apply(request);
                    LayoutFrame settled = layout.step();

                    assertThat(applied.positions().anchors()).containsKeys(pinnedHull, neighborHull);
                    assertThat(settled.positions().anchors()).containsKeys(pinnedHull, neighborHull);
                    assertThat(applied.positions().anchors().values())
                        .allMatch(point -> Double.isFinite(point.x()) && Double.isFinite(point.y()));
                }
            }
            finally {
                reopened.close();
            }
        }
        finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    public void scenario15_consumesTheRecordedStrictPerformancePassWithoutRerunningTheDiagnostic() throws Exception {
        Path report = repositoryFile("docs/superpowers/specs/2026-08-10-graph-workspace-performance-report.md");
        String contents = new String(Files.readAllBytes(report), StandardCharsets.UTF_8);
        RecordedPerformanceLedger ledger = recordedPerformanceLedger(contents);
        PerformanceRow row = ledger.onlyRow("reference-2000-5000", "accepted-batch-first-frame");

        assertThat(contents).contains("Status: PASS on a genuinely executed strict diagnostic");
        assertThat(contents).contains("graphPerformanceDiagnostic -PgraphStrictPerformance");
        assertThat(contents).contains("> Task :freeplane_plugin_graph:graphPerformanceDiagnostic");
        assertThat(contents).contains("Neither capture contains an `UP-TO-DATE` result for the diagnostic task.");
        assertThat(ledger.path).endsWith("strict-final-ledger.csv");

        assertThat(row.failureCount).isZero();
        assertThat(row.discardCount).isZero();
        assertThat(row.pass).isTrue();
        assertThat(row.measuredCount).isGreaterThan(0);
        assertThat(row.p50Nanos).isLessThanOrEqualTo(ledger.strictP95CeilingNanos);
        assertThat(row.p95Nanos).isLessThanOrEqualTo(ledger.strictP95CeilingNanos);
        assertThat(row.p99Nanos).isLessThanOrEqualTo(ledger.strictP99CeilingNanos);
        assertThat(row.p95Nanos).isEqualTo(ledger.reportedP95Nanos);
        assertThat(row.p99Nanos).isEqualTo(ledger.reportedP99Nanos);
        assertThat(row.strictThresholdNanos).isEqualTo(ledger.strictP95CeilingNanos);
    }

    @Test
    public void scenario18_suppressesTheOnlyMapRootAndPromotesItsFirstLevelBoundaries() {
        NodeSnapshot root = node(MAP_ONE, "root", "Root", false, false, false,
            node(MAP_ONE, "left", "Left", false, true, false,
                node(MAP_ONE, "left-leaf", "Left leaf", true, false, false)),
            node(MAP_ONE, "right", "Right", false, true, false,
                node(MAP_ONE, "right-leaf", "Right leaf", true, false, false)));
        GraphProjection projection = project(workspace(registration(MAP_ONE, 1L, true)),
            map(MAP_ONE, 1, "Map", root));

        assertThat(enclosure(projection, MAP_ONE, "root").boundaryTier()).isEqualTo(BoundaryTier.SUPPRESSED);
        assertThat(enclosure(projection, MAP_ONE, "left").boundaryTier()).isEqualTo(BoundaryTier.EMPHATIC);
        assertThat(enclosure(projection, MAP_ONE, "right").boundaryTier()).isEqualTo(BoundaryTier.EMPHATIC);
    }

    @Test
    public void scenario19_secondActiveMapRestylesWithoutLoadingOrMissingFlicker() {
        NodeSnapshot firstRoot = node(MAP_ONE, "root", "Root", false, false, false,
            node(MAP_ONE, "first-branch", "First branch", false, true, false,
                node(MAP_ONE, "first-leaf", "First leaf", true, true, false)),
            node(MAP_ONE, "second-branch", "Second branch", false, true, false,
                node(MAP_ONE, "second-leaf", "Second leaf", true, false, false)));
        MapSnapshot firstMap = map(MAP_ONE, 1, "First", firstRoot);
        MapSnapshot secondMap = map(MAP_TWO, 2, "Second",
            node(MAP_TWO, "second-root", "Second root", false, false, false,
                node(MAP_TWO, "second-child", "Second child", true, true, false)));
        GraphRelationshipRecord crossMapRelationship = relationship(2L, reference(MAP_ONE, "first-leaf"),
            reference(MAP_TWO, "second-root"), RelationshipDirection.FORWARD);
        PinRecord secondMapPin = PinRecord.of(reference(MAP_TWO, "second-child"), 32.0, -18.0, noUnknownXml());
        WorkspaceDocument oneMap = workspace(registration(MAP_ONE, 1L, true));
        WorkspaceDocument twoMaps = workspace(Arrays.asList(registration(MAP_ONE, 1L, true),
            registration(MAP_TWO, 2L, true)), Collections.singletonList(crossMapRelationship),
            Collections.singletonList(secondMapPin));

        GraphProjection single = project(oneMap, firstMap);
        GraphProjection active = project(twoMaps, availability(twoMaps, MapAvailability.AVAILABLE,
            MapAvailability.AVAILABLE), firstMap, secondMap);
        GraphProjection loading = project(twoMaps, availability(twoMaps, MapAvailability.AVAILABLE,
            MapAvailability.LOADING), firstMap, secondMap);
        GraphProjection missing = project(twoMaps, availability(twoMaps, MapAvailability.AVAILABLE,
            MapAvailability.MISSING), firstMap, secondMap);

        assertThat(enclosure(single, MAP_ONE, "root").boundaryTier()).isEqualTo(BoundaryTier.SUPPRESSED);
        assertThat(enclosure(single, MAP_ONE, "first-branch").boundaryTier()).isEqualTo(BoundaryTier.EMPHATIC);
        assertThat(enclosure(active, MAP_ONE, "root").boundaryTier()).isEqualTo(BoundaryTier.EMPHATIC);
        assertThat(enclosure(active, MAP_ONE, "first-branch").boundaryTier()).isEqualTo(BoundaryTier.SUBTLE);
        assertThat(enclosure(loading, MAP_ONE, "root").boundaryTier()).isEqualTo(BoundaryTier.EMPHATIC);
        assertThat(enclosure(loading, MAP_ONE, "first-branch").boundaryTier()).isEqualTo(BoundaryTier.SUBTLE);
        assertThat(enclosure(missing, MAP_ONE, "root").boundaryTier()).isEqualTo(BoundaryTier.EMPHATIC);
        assertThat(enclosure(missing, MAP_ONE, "first-branch").boundaryTier()).isEqualTo(BoundaryTier.SUBTLE);
        assertThat(active.relationshipResolutions()).hasSize(1);
        assertThat(active.relationshipResolutions().get(0).status()).isEqualTo(RelationshipStatus.ACTIVE);
        assertThat(active.edges()).hasSize(1);
        assertThat(active.nodes()).isEmpty();
        assertThat(active.enclosures()).extracting(ProjectedEnclosure::endpointKeys)
            .flatExtracting(keys -> keys)
            .extracting(key -> key.source().persistedReference().get().nodeId().value())
            .contains("second-child");
        assertThat(enclosure(active, MAP_TWO, "second-root").directNodes()).isEmpty();
        assertThat(enclosure(active, MAP_TWO, "second-root").directEnclosures())
            .containsExactly(EnclosureHullKey.of(Collections.singletonList(
                EnclosureKey.of(source(MAP_TWO, "second-child")))));
        assertThat(active.pins()).hasSize(1);
        PinProjection activeSecondMapPin = active.pins().get(0);
        assertThat(activeSecondMapPin.source()).isEqualTo(reference(MAP_TWO, "second-child"));
        assertThat(activeSecondMapPin.active()).isTrue();
        assertThat(activeSecondMapPin.projectedNode())
            .contains(ProjectedNodeKey.of(source(MAP_TWO, "second-child")));
        assertThat(loading.relationshipResolutions()).hasSize(1);
        assertThat(loading.relationshipResolutions().get(0).status())
            .isEqualTo(RelationshipStatus.UNRESOLVED_RECOVERABLE);
        assertThat(loading.relationshipResolutions().get(0).recoverableReasons())
            .containsExactly(RecoverableReason.MAP_LOADING);
        assertThat(loading.relationshipResolutions().get(0).source())
            .contains(ProjectedEndpointKey.ofEnclosure(EnclosureKey.of(source(MAP_ONE, "first-leaf"))));
        assertThat(loading.relationshipResolutions().get(0).target()).isNotPresent();
        assertThat(loading.edges()).isEmpty();
        assertUnavailableMapAbsent(loading, MAP_TWO, secondMapPin);
        assertThat(missing.relationshipResolutions()).hasSize(1);
        assertThat(missing.relationshipResolutions().get(0).status())
            .isEqualTo(RelationshipStatus.UNRESOLVED_RECOVERABLE);
        assertThat(missing.relationshipResolutions().get(0).recoverableReasons())
            .containsExactly(RecoverableReason.MAP_MISSING);
        assertThat(missing.relationshipResolutions().get(0).source())
            .contains(ProjectedEndpointKey.ofEnclosure(EnclosureKey.of(source(MAP_ONE, "first-leaf"))));
        assertThat(missing.relationshipResolutions().get(0).target()).isNotPresent();
        assertThat(missing.edges()).isEmpty();
        assertUnavailableMapAbsent(missing, MAP_TWO, secondMapPin);
    }

    @Test
    public void scenario23_cloneMarkerCompositionCollapsesEveryCloneAndUnmarkingRestoresThem() throws Exception {
        CloneFixture clones = new CloneFixture(temporaryFolder.newFolder("scenario23").toPath());
        try {
            clones.mark(true);
            assertThat(GraphGroupModel.isMarked(clones.original)).isTrue();
            assertThat(GraphGroupModel.isMarked(clones.clone)).isTrue();
            assertThat(clones.controller.affectedClonePositionCount(Collections.singletonList(clones.original)))
                .isEqualTo(2);

            MapSnapshot collapsedSnapshot = clones.snapshot();
            GraphProjection collapsed = project(clones.workspace(), collapsedSnapshot);
            assertThat(collapsed.nodes()).isEmpty();
            assertThat(collapsed.enclosures()).hasSize(3);
            assertThat(collapsed.enclosures().get(1).endpointKeys())
                .containsExactly(EnclosureKey.of(source(MAP_ONE, "ID_CLONE_ONE")));
            assertThat(collapsed.enclosures().get(2).endpointKeys())
                .containsExactly(EnclosureKey.of(source(MAP_ONE, "ID_CLONE_TWO")));
            for (ProjectedEnclosure enclosure : collapsed.enclosures()) {
                assertThat(enclosure.directNodes()).isEmpty();
                for (EnclosureKey endpoint : enclosure.endpointKeys()) {
                    assertThat(endpoint.source().persistedReference().get().nodeId().value())
                        .isNotIn("ID_CLONE_ONE_CHILD", "ID_CLONE_TWO_CHILD");
                }
            }

            clones.mark(false);
            assertThat(GraphGroupModel.isMarked(clones.original)).isFalse();
            assertThat(GraphGroupModel.isMarked(clones.clone)).isFalse();

            MapSnapshot restoredSnapshot = clones.snapshot();
            GraphProjection restored = project(clones.workspace(), restoredSnapshot);
            assertThat(restored.nodes()).isEmpty();
            assertThat(restored.enclosures()).hasSize(1);
            assertThat(restored.enclosures().get(0).endpointKeys())
                .containsExactly(EnclosureKey.of(source(MAP_ONE, "ID_ROOT")));
            for (ProjectedEnclosure enclosure : restored.enclosures()) {
                for (EnclosureKey endpoint : enclosure.endpointKeys()) {
                    assertThat(endpoint.source().persistedReference().get().nodeId().value())
                        .isNotIn("ID_CLONE_ONE", "ID_CLONE_TWO", "ID_CLONE_ONE_CHILD", "ID_CLONE_TWO_CHILD");
                }
            }
        }
        finally {
            clones.close();
        }
    }

    @Test
    public void scenario26_reopensMovedWorkspaceWithItsRelativeMapsTree() throws Exception {
        Path originalDirectory = temporaryFolder.newFolder("scenario26-original").toPath();
        Path mapsDirectory = Files.createDirectories(originalDirectory.resolve("maps"));
        Path map = Files.write(mapsDirectory.resolve("one.mm"), Collections.singletonList("<map/>"),
            StandardCharsets.UTF_8);
        Path workspace = originalDirectory.resolve("workspace.fpg");
        ScheduledThreadPoolExecutor scheduler = scheduler();
        WorkspaceUriResolver resolver = new WorkspaceUriResolver();
        GraphWorkspaceStore store = GraphWorkspaceStore.create(workspace, codec(), AtomicWorkspaceWriter.standard(),
            scheduler);
        try {
            URI relativeMapUri = resolver.toStoredUri(workspace, map);
            assertThat(relativeMapUri).isEqualTo(URI.create("maps/one.mm"));
            assertApplied(store.execute(WorkspaceCommands.addMap(MAP_ONE, relativeMapUri)));
            store.saveNow();
            store.close();

            Path movedRoot = temporaryFolder.newFolder("scenario26-moved-root").toPath();
            Path movedDirectory = Files.move(originalDirectory, movedRoot.resolve("workspace-tree"));
            Path movedWorkspace = movedDirectory.resolve("workspace.fpg");
            Path movedMap = movedDirectory.resolve("maps/one.mm");
            GraphWorkspaceStore reopened = GraphWorkspaceStore.open(movedWorkspace, codec(),
                AtomicWorkspaceWriter.standard(), scheduler);
            try {
                URI storedUri = reopened.currentDocument().maps().get(0).storedUri();
                assertThat(storedUri).isEqualTo(URI.create("maps/one.mm"));
                assertThat(resolver.resolve(movedWorkspace, storedUri)).isEqualTo(movedMap.toRealPath());
            }
            finally {
                reopened.close();
            }
        }
        finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    public void scenario27_stockReaderPreservesMarkerUntilTheGraphReaderRestoresIt() throws Exception {
        StockMapFixture stock = new StockMapFixture(false);
        String stockSaved;
        try {
            MapModel stockMap = stock.read(markedMapXml());
            assertThat(stock.readManager.getElementHandlers().isEmpty("graph_group")).isTrue();
            assertThat(stock.writeManager.getExtensionElementWriters().isEmpty(GraphGroupModel.class)).isTrue();
            assertThat(GraphGroupModel.isMarked(stockMap.getRootNode().getChildAt(0))).isFalse();
            stockSaved = stock.write(stockMap);
            assertThat(stock.readManager.getElementHandlers().list("map"))
                .anyMatch(handler -> handler instanceof MapReader);
            assertThat(stock.writeManager.getElementWriters().list("map"))
                .anyMatch(writer -> writer instanceof MapWriter);
            assertThat(stockSaved).contains("<graph_group version=\"1\"/>");
        }
        finally {
            stock.close();
        }

        StockMapFixture graph = new StockMapFixture(true);
        try {
            assertThat(graph.readManager.getElementHandlers().isEmpty("graph_group")).isFalse();
            assertThat(graph.writeManager.getExtensionElementWriters().isEmpty(GraphGroupModel.class)).isFalse();
            MapModel restored = graph.read(stockSaved);
            assertThat(GraphGroupModel.isMarked(restored.getRootNode().getChildAt(0))).isTrue();
            assertThat(graph.write(restored)).contains("<graph_group version=\"1\"/>");
        }
        finally {
            graph.close();
        }
    }

    @Test
    public void scenario28_keepsOneCoralMarkerAppearanceAcrossAllFourCloudShapes() {
        GraphGroupMarkerPainter painter = new GraphGroupMarkerPainter();
        List<CloudShape> shapes = Arrays.asList(CloudShape.values());
        assertThat(shapes).hasSize(4);

        for (CloudShape shape : shapes) {
            MapModel map = plainMap();
            NodeModel marked = new NodeModel("marked", map);
            map.getRootNode().insert(marked);
            marked.addExtension(new GraphGroupModel());
            CloudModel cloud = new CloudModel();
            cloud.setShape(shape);
            cloud.setColor(Color.BLUE);
            marked.addExtension(cloud);

            BufferedImage image = markerImage(painter, markerView(marked, cloud,
                new Point(60, 60), new Point(140, 100)));

            assertThat(maximumCoralAlpha(image)).isEqualTo(255);
            assertThat(coralPixelCount(image)).isGreaterThan(0);
            assertThat(cloud.getShape()).isEqualTo(shape);
            assertThat(cloud.getColor()).isEqualTo(Color.BLUE);
        }
    }

    @Test
    public void scenario29_rendersNestedInactiveMarkerVisibleAndMuted() {
        MapModel map = plainMap();
        NodeModel outer = new NodeModel("outer", map);
        NodeModel inner = new NodeModel("inner", map);
        map.getRootNode().insert(outer);
        outer.insert(inner);
        outer.addExtension(new GraphGroupModel());
        inner.addExtension(new GraphGroupModel());
        GraphGroupMarkerPainter painter = new GraphGroupMarkerPainter();

        BufferedImage active = markerImage(painter, markerView(outer, null,
            new Point(40, 40), new Point(180, 120)));
        BufferedImage inactive = markerImage(painter, markerView(inner, null,
            new Point(40, 40), new Point(180, 120)));

        assertThat(maximumAlpha(active)).isEqualTo(255);
        assertThat(maximumAlpha(inactive)).isGreaterThan(0).isLessThan(255);
        assertThat(nonTransparentPixelCount(inactive)).isGreaterThan(0);
    }

    private static WorkspaceDocument workspace(final MapReference... registrations) {
        return workspace(Arrays.asList(registrations), Collections.<GraphRelationshipRecord>emptyList(),
            Collections.<PinRecord>emptyList());
    }

    private static WorkspaceDocument workspace(final List<MapReference> registrations,
            final List<GraphRelationshipRecord> relationships, final List<PinRecord> pins) {
        return WorkspaceDocument.createVersion1(WORKSPACE).toBuilder()
            .maps(registrations)
            .relationships(relationships)
            .pins(pins)
            .build();
    }

    private static MapReference registration(final MapReferenceId id, final long sequence, final boolean active) {
        String[] colors = {"#4E79A7", "#F28E2B", "#59A14F", "#E15759"};
        return MapReference.of(id, sequence, URI.create("maps/" + sequence + ".mm"), active,
            colors[(int) (sequence - 1L) % colors.length], noUnknownXml());
    }

    private static GraphRelationshipRecord relationship(final long sequence, final NodeReference source,
            final NodeReference target, final RelationshipDirection direction) {
        return GraphRelationshipRecord.of(RelationshipId.of(String.format(
            "10000000-0000-0000-0000-%012d", Long.valueOf(sequence))), sequence, source, target, direction,
            noUnknownXml());
    }

    private static NodeReference reference(final MapReferenceId map, final String id) {
        return NodeReference.of(map, PersistedNodeId.of(id));
    }

    private static SourceNodeKey source(final MapReferenceId map, final String id) {
        return SourceNodeKey.persisted(reference(map, id));
    }

    private static NodeSnapshot node(final MapReferenceId map, final String id, final String label,
            final boolean structuralLeaf, final boolean graphGroup, final boolean excluded,
            final NodeSnapshot... children) {
        return NodeSnapshot.of(source(map, id), SafeNodeLabel.of(label, label), structuralLeaf, graphGroup,
            excluded, Arrays.asList(children));
    }

    private static MapSnapshot map(final MapReferenceId id, final int order, final String name,
            final NodeSnapshot root) {
        Set<PersistedNodeId> ids = new LinkedHashSet<PersistedNodeId>();
        collectPersistentIds(root, ids);
        return MapSnapshot.of(id, order, name, root, ids, false);
    }

    private static void collectPersistentIds(final NodeSnapshot node, final Set<PersistedNodeId> ids) {
        if (node.key().persistent()) {
            ids.add(node.key().persistedReference().get().nodeId());
        }
        for (NodeSnapshot child : node.children()) {
            collectPersistentIds(child, ids);
        }
    }

    private static ConnectorSnapshot connector(final int occurrence, final MapReferenceId map, final String source,
            final String target, final boolean arrowAtSource, final boolean arrowAtTarget) {
        return ConnectorSnapshot.of(occurrence, ConnectorDescriptor.of(source(map, source), reference(map, target),
            arrowAtSource, arrowAtTarget, "", "", ""));
    }

    private static GraphProjection project(final WorkspaceDocument workspace, final MapSnapshot... snapshots) {
        return new ProjectionEngine().projectStructure(1L, workspace, Arrays.asList(snapshots));
    }

    private static GraphProjection project(final WorkspaceDocument workspace,
            final Map<MapReferenceId, MapAvailability> availability, final MapSnapshot... snapshots) {
        return new ProjectionEngine().project(ProjectionInput.of(1L, workspace, Arrays.asList(snapshots),
            availability));
    }

    private static Map<MapReferenceId, MapAvailability> availability(final WorkspaceDocument workspace,
            final MapAvailability... values) {
        Map<MapReferenceId, MapAvailability> result = new LinkedHashMap<MapReferenceId, MapAvailability>();
        for (int index = 0; index < values.length; index++) {
            result.put(workspace.maps().get(index).id(), values[index]);
        }
        return result;
    }

    private static void assertUnavailableMapAbsent(final GraphProjection projection,
            final MapReferenceId unavailableMap, final PinRecord retainedPin) {
        for (ProjectedNode node : projection.nodes()) {
            assertThat(node.mapReferenceId()).isNotEqualTo(unavailableMap);
        }
        for (ProjectedEnclosure enclosure : projection.enclosures()) {
            assertThat(enclosure.mapReferenceId()).isNotEqualTo(unavailableMap);
        }
        for (ProjectedEdge edge : projection.edges()) {
            assertThat(edge.first().mapReferenceId()).isNotEqualTo(unavailableMap);
            assertThat(edge.second().mapReferenceId()).isNotEqualTo(unavailableMap);
            for (EdgeContributor contributor : edge.contributors()) {
                assertThat(contributor.projectedSource().mapReferenceId()).isNotEqualTo(unavailableMap);
                assertThat(contributor.projectedTarget().mapReferenceId()).isNotEqualTo(unavailableMap);
            }
        }
        for (RelationshipResolution resolution : projection.relationshipResolutions()) {
            if (resolution.source().isPresent()) {
                assertThat(resolution.source().get().mapReferenceId()).isNotEqualTo(unavailableMap);
            }
            if (resolution.target().isPresent()) {
                assertThat(resolution.target().get().mapReferenceId()).isNotEqualTo(unavailableMap);
            }
        }
        for (ProjectedNodeKey key : projection.prominence().keySet()) {
            assertThat(key.mapReferenceId()).isNotEqualTo(unavailableMap);
        }
        List<PinProjection> unavailablePins = new ArrayList<PinProjection>();
        for (PinProjection pin : projection.pins()) {
            if (pin.source().mapReferenceId().equals(unavailableMap)) {
                unavailablePins.add(pin);
            }
        }
        assertThat(unavailablePins).hasSize(1);
        assertThat(unavailablePins).allMatch(pin -> !pin.active());
        PinProjection dormantPin = unavailablePins.get(0);
        assertThat(dormantPin.record()).isEqualTo(retainedPin);
        assertThat(dormantPin.source()).isEqualTo(retainedPin.node());
        assertThat(dormantPin.active()).isFalse();
        assertThat(dormantPin.dormant()).isTrue();
        assertThat(dormantPin.projectedNode()).isEmpty();
    }

    private static ProjectedEnclosure enclosure(final GraphProjection projection, final MapReferenceId map,
            final String sourceId) {
        EnclosureKey expected = EnclosureKey.of(source(map, sourceId));
        for (ProjectedEnclosure enclosure : projection.enclosures()) {
            if (enclosure.endpointKeys().contains(expected)) {
                return enclosure;
            }
        }
        throw new AssertionError("Missing enclosure " + expected);
    }

    private static GraphGeometry labelsFor(final GraphProjection projection) {
        AwtGeometryTextMetrics metrics = new AwtGeometryTextMetrics(new Font("Dialog", Font.PLAIN, 12),
            new FontRenderContext(new AffineTransform(), false, false));
        GraphGeometry geometry =
            new GraphGeometryEngine().computeHulls(projection, positionsFor(projection), metrics);
        return new LabelPlacementEngine(metrics).place(projection, geometry);
    }

    private static LayoutPositions positionsFor(final GraphProjection projection) {
        Map<ProjectedNodeKey, LayoutPoint> nodes = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        int nodeIndex = 0;
        for (ProjectedNode node : projection.nodes()) {
            nodes.put(node.key(), LayoutPoint.of(nodeIndex * 200.0, (nodeIndex % 2) * 160.0));
            nodeIndex++;
        }
        Map<org.freeplane.plugin.graph.projection.EnclosureHullKey, LayoutPoint> anchors =
            new LinkedHashMap<org.freeplane.plugin.graph.projection.EnclosureHullKey, LayoutPoint>();
        int enclosureIndex = 0;
        for (ProjectedEnclosure enclosure : projection.enclosures()) {
            anchors.put(enclosure.hullKey(), LayoutPoint.of(enclosureIndex * 200.0, 40.0));
            enclosureIndex++;
        }
        return LayoutPositions.of(nodes, anchors);
    }

    private static WorkspaceXmlCodec codec() {
        return new WorkspaceXmlCodec(new WorkspaceMigrationRegistry(Collections.<WorkspaceMigration>emptyList()));
    }

    private static ScheduledThreadPoolExecutor scheduler() {
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    private static List<UnknownXml> noUnknownXml() {
        return Collections.<UnknownXml>emptyList();
    }

    private static void assertApplied(final GraphCommandResult result) {
        assertThat(result.status()).isEqualTo(GraphCommandResult.Status.APPLIED);
    }

    private static Path repositoryFile(final String relativePath) {
        Path directory = Paths.get("").toAbsolutePath();
        while (directory != null) {
            Path candidate = directory.resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("Cannot locate " + relativePath);
    }

    private static RecordedPerformanceLedger recordedPerformanceLedger(final String report) {
        final String path = backtickedLine(report, "- Run-root final archive: ");
        final String marker = "The following block is copied byte-for-byte from the authoritative final ledger:";
        final int markerStart = report.indexOf(marker);
        if (markerStart < 0 || markerStart != report.lastIndexOf(marker)) {
            throw new AssertionError("The report must identify one authoritative ledger block");
        }
        final int csvStart = report.indexOf("```csv", markerStart + marker.length());
        final int csvEnd = csvStart < 0 ? -1 : report.indexOf("```", csvStart + "```csv".length());
        if (csvStart < 0 || csvEnd < 0) {
            throw new AssertionError("The report must contain the recorded CSV ledger");
        }
        final String archiveFileName = Paths.get(path).getFileName().toString();
        final String archiveStatement = "The authoritative final ledger is archived at the final evidence path, "
            + "copied to the run-root `" + archiveFileName + "`, and mirrored at the required attempt-10 path.";
        if (report.indexOf(archiveStatement) < 0 || report.indexOf(archiveStatement) > markerStart) {
            throw new AssertionError("The report must tie its final ledger archive to the embedded CSV");
        }
        String csv = report.substring(csvStart + "```csv".length(), csvEnd);
        if (csv.startsWith("\r\n")) {
            csv = csv.substring(2);
        }
        else if (csv.startsWith("\n")) {
            csv = csv.substring(1);
        }
        final String[] lines = csv.split("\\r?\\n", -1);
        int lineCount = lines.length;
        if (lineCount > 0 && lines[lineCount - 1].isEmpty()) {
            lineCount--;
        }
        if (lineCount < 2) {
            throw new AssertionError("The recorded CSV ledger must contain a header and rows");
        }
        final List<String> header = csvFields(lines[0]);
        final List<String> expectedHeader = Arrays.asList("scenario", "stage", "warmupCount", "measuredCount",
            "p50Nanos", "p95Nanos", "p99Nanos", "maxNanos", "normalThresholdNanos",
            "strictThresholdNanos", "failureCount", "discardCount", "pass");
        if (!header.equals(expectedHeader)) {
            throw new AssertionError("Unexpected performance ledger header: " + header);
        }
        final Map<String, Integer> columns = new LinkedHashMap<String, Integer>();
        for (int index = 0; index < header.size(); index++) {
            if (columns.put(header.get(index), Integer.valueOf(index)) != null) {
                throw new AssertionError("Duplicate performance ledger column: " + header.get(index));
            }
        }
        final List<PerformanceRow> rows = new ArrayList<PerformanceRow>();
        for (int lineIndex = 1; lineIndex < lineCount; lineIndex++) {
            if (lines[lineIndex].isEmpty()) {
                throw new AssertionError("Blank performance ledger row at line " + (lineIndex + 1));
            }
            final List<String> fields = csvFields(lines[lineIndex]);
            if (fields.size() != header.size()) {
                throw new AssertionError("Malformed performance ledger row at line " + (lineIndex + 1));
            }
            rows.add(new PerformanceRow(fields, columns));
        }
        final Matcher strictSummary = Pattern.compile(
            "^- accepted-batch-first-frame p95 `(\\d+)` ns, p99 `(\\d+)` ns, "
                + "strict ceilings `(\\d+)`/`(\\d+)` ns$", Pattern.MULTILINE)
            .matcher(report);
        if (!strictSummary.find()) {
            throw new AssertionError("The report must record strict p95 and p99 ceilings");
        }
        final long reportedP95Nanos = Long.parseLong(strictSummary.group(1));
        final long reportedP99Nanos = Long.parseLong(strictSummary.group(2));
        final long strictP95CeilingNanos = Long.parseLong(strictSummary.group(3));
        final long strictP99CeilingNanos = Long.parseLong(strictSummary.group(4));
        if (strictSummary.find()) {
            throw new AssertionError("The report must contain one strict reference summary");
        }
        return new RecordedPerformanceLedger(path, rows, reportedP95Nanos, reportedP99Nanos,
            strictP95CeilingNanos, strictP99CeilingNanos);
    }

    private static String backtickedLine(final String contents, final String prefix) {
        String value = null;
        int matches = 0;
        for (String line : contents.split("\\r?\\n")) {
            if (!line.startsWith(prefix)) {
                continue;
            }
            final int start = line.indexOf('`', prefix.length());
            final int end = start < 0 ? -1 : line.indexOf('`', start + 1);
            if (start < 0 || end < 0 || end == start + 1) {
                throw new AssertionError("Malformed ledger path line: " + line);
            }
            value = line.substring(start + 1, end);
            matches++;
        }
        if (matches != 1) {
            throw new AssertionError("Expected one recorded ledger path, found " + matches);
        }
        return value;
    }

    private static List<String> csvFields(final String line) {
        final List<String> fields = new ArrayList<String>();
        final StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean closedQuote = false;
        for (int index = 0; index < line.length(); index++) {
            final char character = line.charAt(index);
            if (quoted) {
                if (character == '"') {
                    if (index + 1 < line.length() && line.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    }
                    else {
                        quoted = false;
                        closedQuote = true;
                    }
                }
                else {
                    field.append(character);
                }
            }
            else if (closedQuote) {
                if (character != ',') {
                    throw new AssertionError("Malformed quoted CSV field: " + line);
                }
                fields.add(field.toString());
                field.setLength(0);
                closedQuote = false;
            }
            else if (character == ',' && !quoted) {
                fields.add(field.toString());
                field.setLength(0);
            }
            else if (character == '"') {
                if (field.length() != 0) {
                    throw new AssertionError("Malformed CSV quote: " + line);
                }
                quoted = true;
            }
            else {
                field.append(character);
            }
        }
        if (quoted) {
            throw new AssertionError("Unclosed CSV quote: " + line);
        }
        fields.add(field.toString());
        return fields;
    }

    private static final class RecordedPerformanceLedger {
        private final String path;
        private final List<PerformanceRow> rows;
        private final long reportedP95Nanos;
        private final long reportedP99Nanos;
        private final long strictP95CeilingNanos;
        private final long strictP99CeilingNanos;

        private RecordedPerformanceLedger(final String path, final List<PerformanceRow> rows,
                final long reportedP95Nanos, final long reportedP99Nanos,
                final long strictP95CeilingNanos, final long strictP99CeilingNanos) {
            this.path = path;
            this.rows = rows;
            this.reportedP95Nanos = reportedP95Nanos;
            this.reportedP99Nanos = reportedP99Nanos;
            this.strictP95CeilingNanos = strictP95CeilingNanos;
            this.strictP99CeilingNanos = strictP99CeilingNanos;
        }

        private PerformanceRow onlyRow(final String scenario, final String stage) {
            PerformanceRow result = null;
            for (PerformanceRow row : rows) {
                if (scenario.equals(row.scenario) && stage.equals(row.stage)) {
                    if (result != null) {
                        throw new AssertionError("Duplicate performance ledger row: " + scenario + "," + stage);
                    }
                    result = row;
                }
            }
            if (result == null) {
                throw new AssertionError("Missing performance ledger row: " + scenario + "," + stage);
            }
            return result;
        }
    }

    private static final class PerformanceRow {
        private final String scenario;
        private final String stage;
        private final int warmupCount;
        private final int measuredCount;
        private final long p50Nanos;
        private final long p95Nanos;
        private final long p99Nanos;
        private final long maxNanos;
        private final long normalThresholdNanos;
        private final long strictThresholdNanos;
        private final int failureCount;
        private final int discardCount;
        private final boolean pass;

        private PerformanceRow(final List<String> fields, final Map<String, Integer> columns) {
            scenario = field(fields, columns, "scenario");
            stage = field(fields, columns, "stage");
            warmupCount = integerField(fields, columns, "warmupCount");
            measuredCount = integerField(fields, columns, "measuredCount");
            p50Nanos = longField(fields, columns, "p50Nanos");
            p95Nanos = longField(fields, columns, "p95Nanos");
            p99Nanos = longField(fields, columns, "p99Nanos");
            maxNanos = longField(fields, columns, "maxNanos");
            normalThresholdNanos = longField(fields, columns, "normalThresholdNanos");
            strictThresholdNanos = longField(fields, columns, "strictThresholdNanos");
            failureCount = integerField(fields, columns, "failureCount");
            discardCount = integerField(fields, columns, "discardCount");
            final String passValue = field(fields, columns, "pass");
            if (!"true".equals(passValue) && !"false".equals(passValue)) {
                throw new AssertionError("Malformed pass field: " + passValue);
            }
            pass = Boolean.parseBoolean(passValue);
        }

        private static String field(final List<String> fields, final Map<String, Integer> columns,
                final String name) {
            return fields.get(columns.get(name).intValue());
        }

        private static int integerField(final List<String> fields, final Map<String, Integer> columns,
                final String name) {
            try {
                return Integer.parseInt(field(fields, columns, name));
            }
            catch (NumberFormatException failure) {
                throw new AssertionError("Malformed integer field " + name, failure);
            }
        }

        private static long longField(final List<String> fields, final Map<String, Integer> columns,
                final String name) {
            try {
                return Long.parseLong(field(fields, columns, name));
            }
            catch (NumberFormatException failure) {
                throw new AssertionError("Malformed long field " + name, failure);
            }
        }
    }

    private static MapModel emptyMap() {
        return new MapModel(new INodeDuplicator() {
            @Override
            public NodeModel duplicate(final NodeModel source, final MapModel targetMap, final boolean withChildren) {
                return null;
            }
        }, null, null);
    }

    private static MapModel plainMap() {
        MapModel map = emptyMap();
        map.setRoot(new NodeModel("root", map));
        return map;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static NodeView markerView(final NodeModel node, final CloudModel cloud, final Point... points) {
        NodeView view = mock(NodeView.class);
        when(view.getNode()).thenReturn(node);
        when(view.getCloudModel()).thenReturn(cloud);
        when(view.getZoomed(anyInt())).thenAnswer(invocation -> invocation.getArgument(0));
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            LinkedList<Point> coordinates = invocation.getArgument(0);
            for (Point point : points) {
                coordinates.add(new Point(point));
            }
            return null;
        }).when(view).getCoordinates(any(LinkedList.class));
        return view;
    }

    private static BufferedImage markerImage(final GraphGroupMarkerPainter painter, final NodeView view) {
        BufferedImage image = new BufferedImage(240, 180, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        try {
            painter.paint(view, graphics);
        }
        finally {
            graphics.dispose();
        }
        return image;
    }

    private static int maximumCoralAlpha(final BufferedImage image) {
        int maximum = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color color = new Color(image.getRGB(x, y), true);
                if (color.getRed() == CORAL.getRed() && color.getGreen() == CORAL.getGreen()
                        && color.getBlue() == CORAL.getBlue()) {
                    maximum = Math.max(maximum, color.getAlpha());
                }
            }
        }
        return maximum;
    }

    private static int coralPixelCount(final BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color color = new Color(image.getRGB(x, y), true);
                if (color.getRed() == CORAL.getRed() && color.getGreen() == CORAL.getGreen()
                        && color.getBlue() == CORAL.getBlue() && color.getAlpha() > 0) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int maximumAlpha(final BufferedImage image) {
        int maximum = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                maximum = Math.max(maximum, image.getRGB(x, y) >>> 24);
            }
        }
        return maximum;
    }

    private static int nonTransparentPixelCount(final BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    count++;
                }
            }
        }
        return count;
    }

    private static String markedMapXml() {
        return "<map version=\"freeplane 1.12.0\"><node TEXT=\"root\" ID=\"ID_ROOT\">"
            + "<node TEXT=\"marked\" ID=\"ID_MARKED\"><graph_group version=\"1\"/></node>"
            + "</node></map>";
    }

    private final class AdapterFixture implements AutoCloseable {
        private final Controller previousController;
        private final MapLeaseManager manager;
        private final MMapModel map;

        private AdapterFixture(final Path workspace) throws Exception {
            previousController = Controller.getCurrentController();
            Controller controller = mock(Controller.class);
            ModeController modeController = mock(ModeController.class);
            MMapController mapController = mock(MMapController.class);
            IMapViewManager mapViews = mock(IMapViewManager.class);
            when(controller.getModeController()).thenReturn(modeController);
            when(modeController.getController()).thenReturn(controller);
            when(modeController.getMapController()).thenReturn(mapController);
            when(controller.getMapViewManager()).thenReturn(mapViews);
            Controller.setCurrentController(controller);
            map = new MMapModel(new INodeDuplicator() {
                @Override
                public NodeModel duplicate(final NodeModel source, final MapModel targetMap,
                        final boolean withChildren) {
                    return null;
                }
            });
            Path mapFile = temporaryFolder.newFile("scenario02.mm").toPath();
            map.setURL(mapFile.toRealPath().toUri().toURL());
            when(mapController.getMap(any(URL.class))).thenReturn(map);
            when(mapViews.containsView(any(MapModel.class))).thenReturn(false);
            manager = new MapLeaseManager(workspace, modeController);
        }

        private NodeModel root(final String text, final String id) {
            NodeModel root = new NodeModel(text, map);
            root.setID(id);
            map.setRoot(root);
            return root;
        }

        private NodeModel child(final NodeModel parent, final String text, final String id) {
            NodeModel child = new NodeModel(text, map);
            child.setID(id);
            parent.insert(child);
            return child;
        }

        private MapSnapshot snapshot(final MapReferenceId mapId, final long order) throws Exception {
            MapReference reference = MapReference.of(mapId, order, map.getURL().toURI(), true, "#4E79A7",
                noUnknownXml());
            MapLease lease = manager.acquire(reference).toCompletableFuture().get(5L, TimeUnit.SECONDS);
            try {
                assertThat(lease.state()).isEqualTo(MapOperationalState.AVAILABLE);
                return new MapSnapshotFactory().snapshot(lease);
            }
            finally {
                lease.close();
            }
        }

        @Override
        public void close() {
            try {
                manager.close();
            }
            finally {
                Controller.setCurrentController(previousController);
            }
        }
    }

    private static final class CloneFixture implements AutoCloseable {
        private final GraphGroupController controller;
        private final NodeModel clone;
        private final NodeModel original;
        private final MapReference registration;
        private final WorkspaceDocument workspaceDocument;
        private final MapLeaseManager leaseManager;

        private CloneFixture(final Path directory) throws Exception {
            final Path workspaceFile = directory.resolve("workspace.fpg");
            final Path mapFile = directory.resolve("clone.mm");
            Files.write(workspaceFile, Collections.singletonList(""), StandardCharsets.UTF_8);
            Files.write(mapFile, Collections.singletonList("<map/>"), StandardCharsets.UTF_8);

            final ModeController modeController = mock(ModeController.class);
            final MMapController mapController = mock(MMapController.class);
            final Controller hostController = mock(Controller.class);
            when(hostController.getModeController()).thenReturn(modeController);
            when(modeController.getMapController()).thenReturn(mapController);
            final Controller previousController = Controller.getCurrentController();
            Controller.setCurrentController(hostController);
            final MMapModel map;
            try {
                map = new MMapModel(new INodeDuplicator() {
                    @Override
                    public NodeModel duplicate(final NodeModel source, final MapModel targetMap,
                            final boolean withChildren) {
                        return null;
                    }
                });
            }
            finally {
                Controller.setCurrentController(previousController);
            }
            map.setURL(mapFile.toRealPath().toUri().toURL());
            final NodeModel root = new NodeModel("root", map);
            root.setID("ID_ROOT");
            map.setRoot(root);
            original = new NodeModel("clone root", map);
            original.setID("ID_CLONE_ONE");
            final NodeModel originalChild = new NodeModel("clone child", map);
            originalChild.setID("ID_CLONE_ONE_CHILD");
            original.insert(originalChild);
            root.insert(original);
            clone = original.cloneTree();
            clone.setID("ID_CLONE_TWO");
            clone.getChildAt(0).setID("ID_CLONE_TWO_CHILD");
            root.insert(clone);
            map.addExtension(IUndoHandler.class, mock(IUndoHandler.class));

            final IMapViewManager mapViews = mock(IMapViewManager.class);
            final ReadManager reader = new ReadManager();
            final WriteManager writer = new WriteManager();
            when(modeController.getController()).thenReturn(hostController);
            when(modeController.canEdit(map)).thenReturn(true);
            when(hostController.getMapViewManager()).thenReturn(mapViews);
            when(mapViews.containsView(any(MapModel.class))).thenReturn(false);
            when(mapController.getMap(any(URL.class))).thenReturn(map);
            when(mapController.getReadManager()).thenReturn(reader);
            when(mapController.getWriteManager()).thenReturn(writer);
            doAnswer(invocation -> {
                ((IActor) invocation.getArgument(0)).act();
                return null;
            }).when(modeController).execute(any(IActor.class), eq(map));
            controller = new GraphGroupController(modeController);

            registration = MapReference.of(MAP_ONE, 1L,
                new WorkspaceUriResolver().toStoredUri(workspaceFile, mapFile), true, "#4E79A7",
                noUnknownXml());
            workspaceDocument = GraphWorkspaceModelAcceptanceShould.workspace(registration);
            leaseManager = new MapLeaseManager(workspaceFile, modeController);
        }

        private void mark(final boolean marked) {
            controller.setMarked(Collections.singletonList(original), marked);
        }

        private WorkspaceDocument workspace() {
            return workspaceDocument;
        }

        private MapSnapshot snapshot() throws Exception {
            MapLease lease = leaseManager.acquire(registration).toCompletableFuture().get(5L, TimeUnit.SECONDS);
            try {
                assertThat(lease.state()).isEqualTo(MapOperationalState.AVAILABLE);
                return new MapSnapshotFactory().snapshot(lease);
            }
            finally {
                lease.close();
            }
        }

        @Override
        public void close() {
            try {
                controller.close();
            }
            finally {
                leaseManager.close();
            }
        }
    }

    private static final class StockMapFixture implements AutoCloseable {
        private final GraphGroupController graphGroups;
        private final MapReader mapReader;
        private final MapWriter mapWriter;
        private final ReadManager readManager;
        private final WriteManager writeManager;

        private StockMapFixture(final boolean graphGroupsEnabled) {
            readManager = new ReadManager();
            writeManager = new WriteManager();
            final ModeController modeController = mock(ModeController.class);
            final MapController mapController = mock(MapController.class);
            when(modeController.getMapController()).thenReturn(mapController);
            when(mapController.getReadManager()).thenReturn(readManager);
            when(mapController.getWriteManager()).thenReturn(writeManager);
            when(mapController.getModeController()).thenReturn(modeController);
            when(mapController.isFolded(any(NodeModel.class))).thenReturn(false);

            final ResourceController resources = ResourceController.getResourceController();
            when(resources.getBooleanProperty("useAsciiCharset")).thenReturn(false);
            when(resources.getProperty("load_folding")).thenReturn("never");
            when(resources.getProperty("save_folding")).thenReturn("never");

            mapReader = new MapReader(readManager);
            mapWriter = new MapWriter(mapController);
            readManager.addElementHandler("map", mapReader);
            readManager.addAttributeHandler("map", "version", (element, value) -> {
            });
            readManager.addAttributeHandler("map", "dialect", (element, value) -> {
            });
            writeManager.addElementWriter("map", mapWriter);
            writeManager.addAttributeWriter("map", mapWriter);
            final UnknownElementWriter unknown = new UnknownElementWriter();
            writeManager.addExtensionAttributeWriter(UnknownElements.class, unknown);
            writeManager.addExtensionElementWriter(UnknownElements.class, unknown);
            graphGroups = graphGroupsEnabled ? new GraphGroupController(modeController) : null;
        }

        private MapModel read(final String xml) throws Exception {
            final MapModel map = emptyMap();
            mapReader.createNodeTreeFromXml(map, new StringReader(xml), MapWriter.Mode.FILE);
            return map;
        }

        private String write(final MapModel map) throws IOException {
            final StringWriter output = new StringWriter();
            mapWriter.writeMapAsXml(map, output, MapWriter.Mode.FILE,
                org.freeplane.features.map.clipboard.MapClipboardController.CopiedNodeSet.ALL_NODES, false);
            return output.toString();
        }

        @Override
        public void close() {
            if (graphGroups != null) {
                graphGroups.close();
            }
        }
    }

}
