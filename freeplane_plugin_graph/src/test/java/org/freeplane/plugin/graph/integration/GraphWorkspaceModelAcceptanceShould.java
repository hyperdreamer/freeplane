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

import org.freeplane.core.extension.IExtension;
import org.freeplane.core.io.IAttributeWriter;
import org.freeplane.core.io.IElementDOMHandler;
import org.freeplane.core.io.IElementWriter;
import org.freeplane.core.io.ITreeWriter;
import org.freeplane.core.io.ReadManager;
import org.freeplane.core.io.UnknownElementWriter;
import org.freeplane.core.io.UnknownElements;
import org.freeplane.core.io.WriteManager;
import org.freeplane.core.io.xml.TreeXmlReader;
import org.freeplane.core.io.xml.TreeXmlWriter;
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
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.mindmapmode.MMapController;
import org.freeplane.features.map.mindmapmode.MMapModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.ui.IMapViewManager;
import org.freeplane.n3.nanoxml.XMLElement;
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
    public void scenario02_projectsOnlyStructuralLeavesAndActiveGroupsIncludingHiddenOnlyChildEnclosure()
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

            assertThat(projection.nodes()).extracting(ProjectedNode::source)
                .containsExactly(source(MAP_ONE, "ID_GROUP"), source(MAP_ONE, "ID_VISIBLE_LEAF"));
            assertThat(projection.nodes()).extracting(ProjectedNode::graphGroup).containsExactly(true, false);
            assertThat(projection.nodes()).extracting(ProjectedNode::source)
                .doesNotContain(source(MAP_ONE, "ID_GROUP_CHILD"), source(MAP_ONE, "ID_HIDDEN_CHILD"));
            assertThat(enclosure(projection, MAP_ONE, "ID_VISIBLE_PARENT").directNodes()).isEmpty();
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
        NodeSnapshot firstInterior = node(MAP_ONE, "first-interior", "First interior", false, false, false,
            firstLeaf, firstLeafTwo);
        NodeSnapshot secondInterior = node(MAP_ONE, "second-interior", "Second interior", false, false, false,
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
            .containsExactlyInAnyOrder("Map fixture", "First interior", "Second interior");
        assertThat(labeled.labels().values()).extracting(LabelPlacement::mode)
            .containsOnly(LabelPlacement.Mode.INTERIOR);
    }

    @Test
    public void scenario04_outerMarkerSuppressesInnerMarkerUntilOuterIsRemoved() {
        NodeSnapshot innerLeaf = node(MAP_ONE, "inner-leaf", "Inner leaf", true, false, false);
        NodeSnapshot inner = node(MAP_ONE, "inner", "Inner group", false, true, false, innerLeaf);
        NodeSnapshot outer = node(MAP_ONE, "outer", "Outer group", false, true, false, inner);
        NodeSnapshot root = node(MAP_ONE, "root", "Root", false, false, false, outer);
        WorkspaceDocument workspace = workspace(registration(MAP_ONE, 1L, true));

        GraphProjection outerActive = project(workspace, map(MAP_ONE, 1, "Map", root));
        assertThat(outerActive.nodes()).extracting(ProjectedNode::source).containsExactly(source(MAP_ONE, "outer"));
        assertThat(outerActive.nodes()).extracting(ProjectedNode::graphGroup).containsExactly(true);

        NodeSnapshot unmarkedOuter = node(MAP_ONE, "outer", "Outer group", false, false, false, inner);
        GraphProjection innerReactivated = project(workspace,
            map(MAP_ONE, 1, "Map", node(MAP_ONE, "root", "Root", false, false, false, unmarkedOuter)));
        assertThat(innerReactivated.nodes()).extracting(ProjectedNode::source)
            .containsExactly(source(MAP_ONE, "inner"));
        assertThat(innerReactivated.nodes()).extracting(ProjectedNode::graphGroup).containsExactly(true);
    }

    @Test
    public void scenario05_consolidatesDuplicateConnectorsWithoutChangingTheirRecords() {
        NodeSnapshot source = node(MAP_ONE, "source", "Source", true, false, false);
        NodeSnapshot target = node(MAP_ONE, "target", "Target", true, false, false);
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
        NodeSnapshot first = node(MAP_ONE, "first", "First", true, false, false);
        NodeSnapshot second = node(MAP_ONE, "second", "Second", true, false, false);
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

        assertThat(projection.nodes()).extracting(ProjectedNode::source).containsExactly(source(MAP_ONE, "group"));
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
    public void scenario12_ungroupedRootRelationshipAttachesToItsAncestorEnclosure() {
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

        assertThat(groupedProjection.edges().get(0).contributors().get(0).projectedSource().isNode()).isTrue();
        ProjectedEndpointKey sourceAfterUngrouping =
            ungroupedProjection.edges().get(0).contributors().get(0).projectedSource();
        assertThat(sourceAfterUngrouping.isEnclosure()).isTrue();
        assertThat(sourceAfterUngrouping.enclosure()).contains(EnclosureKey.of(source(MAP_ONE, "group")));
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
                    false, node(MAP_ONE, "pinned", "Pinned", true, false, false),
                    node(MAP_ONE, "neighbor", "Neighbor", true, false, false)));
                GraphProjection projection = project(reopened.currentDocument(), snapshot);
                ProjectedNodeKey pinnedKey = ProjectedNodeKey.of(source(MAP_ONE, "pinned"));
                ProjectedNodeKey neighborKey = ProjectedNodeKey.of(source(MAP_ONE, "neighbor"));

                assertThat(projection.pins()).hasSize(1);
                assertThat(projection.pins().get(0).active()).isTrue();
                try (LayoutEngine layout = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
                    LayoutRequest request = LayoutRequest.of(reopened.currentDocument().id(), projection,
                        ProjectionDiff.between(projection, projection), projection.pins());
                    LayoutFrame applied = layout.apply(request);
                    LayoutFrame settled = layout.step();

                    assertThat(applied.positions().nodes().get(pinnedKey)).isEqualTo(LayoutPoint.of(18.0, -11.0));
                    assertThat(settled.positions().nodes().get(pinnedKey)).isEqualTo(LayoutPoint.of(18.0, -11.0));
                    assertThat(settled.positions().nodes().get(neighborKey))
                        .isNotEqualTo(applied.positions().nodes().get(neighborKey));
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

        assertThat(contents).contains("Status: PASS on a genuinely executed strict diagnostic");
        assertThat(contents).contains("Every row has `failureCount=0`, `discardCount=0`, and `pass=true`.");
        assertThat(contents).contains("reference-2000-5000,accepted-batch-first-frame");
    }

    @Test
    public void scenario18_suppressesTheOnlyMapRootAndPromotesItsFirstLevelEnclosures() {
        NodeSnapshot root = node(MAP_ONE, "root", "Root", false, false, false,
            node(MAP_ONE, "left", "Left", false, false, false,
                node(MAP_ONE, "left-leaf", "Left leaf", true, false, false)),
            node(MAP_ONE, "right", "Right", false, false, false,
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
            node(MAP_ONE, "first-branch", "First branch", false, false, false,
                node(MAP_ONE, "first-leaf", "First leaf", true, false, false)),
            node(MAP_ONE, "second-branch", "Second branch", false, false, false,
                node(MAP_ONE, "second-leaf", "Second leaf", true, false, false)));
        MapSnapshot firstMap = map(MAP_ONE, 1, "First", firstRoot);
        MapSnapshot secondMap = map(MAP_TWO, 2, "Second",
            node(MAP_TWO, "second-root", "Second root", true, false, false));
        WorkspaceDocument oneMap = workspace(registration(MAP_ONE, 1L, true));
        WorkspaceDocument twoMaps = workspace(registration(MAP_ONE, 1L, true), registration(MAP_TWO, 2L, true));

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
    }

    @Test
    public void scenario23_cloneMarkerCompositionCollapsesEveryCloneAndUnmarkingRestoresThem() {
        CloneFixture clones = new CloneFixture();
        try {
            clones.mark(true);
            assertThat(GraphGroupModel.isMarked(clones.original)).isTrue();
            assertThat(GraphGroupModel.isMarked(clones.clone)).isTrue();
            assertThat(clones.controller.affectedClonePositionCount(Collections.singletonList(clones.original)))
                .isEqualTo(2);

            GraphProjection collapsed = cloneProjection(true);
            assertThat(collapsed.nodes()).extracting(ProjectedNode::graphGroup).containsExactly(true, true);
            assertThat(collapsed.nodes()).extracting(ProjectedNode::source)
                .containsExactly(source(MAP_ONE, "clone-one"), source(MAP_ONE, "clone-two"));

            clones.mark(false);
            assertThat(GraphGroupModel.isMarked(clones.original)).isFalse();
            assertThat(GraphGroupModel.isMarked(clones.clone)).isFalse();

            GraphProjection restored = cloneProjection(false);
            assertThat(restored.nodes()).extracting(ProjectedNode::graphGroup).containsOnly(false);
            assertThat(restored.nodes()).extracting(ProjectedNode::source)
                .containsExactly(source(MAP_ONE, "clone-one-child"), source(MAP_ONE, "clone-two-child"));
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
        MarkerFixtureCodec stock = new MarkerFixtureCodec(false);
        String stockSaved;
        try {
            MapModel stockMap = stock.read(markedMapXml());
            assertThat(GraphGroupModel.isMarked(stockMap.getRootNode().getChildAt(0))).isFalse();
            stockSaved = stock.write(stockMap);
            assertThat(stockSaved).contains("<graph_group version=\"1\"/>");
        }
        finally {
            stock.close();
        }

        MarkerFixtureCodec graph = new MarkerFixtureCodec(true);
        try {
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

    private static GraphProjection cloneProjection(final boolean marked) {
        NodeSnapshot first = node(MAP_ONE, "clone-one", "Clone one", false, marked, false,
            node(MAP_ONE, "clone-one-child", "Clone one child", true, false, false));
        NodeSnapshot second = node(MAP_ONE, "clone-two", "Clone two", false, marked, false,
            node(MAP_ONE, "clone-two-child", "Clone two child", true, false, false));
        return project(workspace(registration(MAP_ONE, 1L, true)),
            map(MAP_ONE, 1, "Map", node(MAP_ONE, "root", "Root", false, false, false, first, second)));
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
        GraphGeometry geometry = new GraphGeometryEngine().computeHulls(projection, positionsFor(projection));
        AwtGeometryTextMetrics metrics = new AwtGeometryTextMetrics(new Font("Dialog", Font.PLAIN, 12),
            new FontRenderContext(new AffineTransform(), false, false));
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

    private static MapModel plainMap() {
        MapModel map = new MapModel(new INodeDuplicator() {
            @Override
            public NodeModel duplicate(final NodeModel source, final MapModel targetMap, final boolean withChildren) {
                return null;
            }
        }, null, null);
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

        private CloneFixture() {
            MapModel map = plainMap();
            original = new NodeModel("clone root", map);
            original.setID("ID_CLONE_ROOT");
            original.insert(new NodeModel("clone child", map));
            map.getRootNode().insert(original);
            clone = original.cloneTree();
            map.getRootNode().insert(clone);
            map.addExtension(IUndoHandler.class, mock(IUndoHandler.class));

            ModeController modeController = mock(ModeController.class);
            MapController mapController = mock(MapController.class);
            ReadManager reader = new ReadManager();
            WriteManager writer = new WriteManager();
            when(modeController.getMapController()).thenReturn(mapController);
            when(modeController.canEdit(map)).thenReturn(true);
            when(mapController.getReadManager()).thenReturn(reader);
            when(mapController.getWriteManager()).thenReturn(writer);
            doAnswer(invocation -> {
                ((IActor) invocation.getArgument(0)).act();
                return null;
            }).when(modeController).execute(any(IActor.class), eq(map));
            controller = new GraphGroupController(modeController);
        }

        private void mark(final boolean marked) {
            controller.setMarked(Collections.singletonList(original), marked);
        }

        @Override
        public void close() {
            controller.close();
        }
    }

    private static final class MarkerFixtureCodec implements AutoCloseable {
        private final GraphGroupController graphGroups;
        private final FixtureMapBuilder mapBuilder = new FixtureMapBuilder();
        private final ReadManager readManager = new ReadManager();
        private final WriteManager writeManager = new WriteManager();

        private MarkerFixtureCodec(final boolean graphGroupsEnabled) {
            FixtureNodeBuilder nodeBuilder = new FixtureNodeBuilder(mapBuilder);
            readManager.addElementHandler("map", mapBuilder);
            readManager.addElementHandler("node", nodeBuilder);
            writeManager.addAttributeWriter("map", new FixtureExtensionAttributeWriter());
            writeManager.addAttributeWriter("node", new FixtureExtensionAttributeWriter());
            writeManager.addElementWriter("map", new FixtureMapWriter());
            writeManager.addElementWriter("node", new FixtureNodeWriter());
            UnknownElementWriter unknown = new UnknownElementWriter();
            writeManager.addExtensionAttributeWriter(UnknownElements.class, unknown);
            writeManager.addExtensionElementWriter(UnknownElements.class, unknown);
            if (graphGroupsEnabled) {
                ModeController modeController = mock(ModeController.class);
                MapController mapController = mock(MapController.class);
                when(modeController.getMapController()).thenReturn(mapController);
                when(mapController.getReadManager()).thenReturn(readManager);
                when(mapController.getWriteManager()).thenReturn(writeManager);
                graphGroups = new GraphGroupController(modeController);
            }
            else {
                graphGroups = null;
            }
        }

        private MapModel read(final String xml) throws Exception {
            mapBuilder.clear();
            new TreeXmlReader(readManager).load(null, new StringReader(xml));
            return mapBuilder.map();
        }

        private String write(final MapModel map) throws IOException {
            StringWriter output = new StringWriter();
            TreeXmlWriter writer = new TreeXmlWriter(writeManager, output, false);
            writer.addElement(map, "map");
            writer.flush();
            return output.toString();
        }

        @Override
        public void close() {
            if (graphGroups != null) {
                graphGroups.close();
            }
        }
    }

    private static final class FixtureMapBuilder implements IElementDOMHandler {
        private MapModel map;

        @Override
        public Object createElement(final Object parent, final String tag, final XMLElement attributes) {
            map = plainMap();
            return map;
        }

        @Override
        public void endElement(final Object parent, final String tag, final Object element, final XMLElement dom) {
            if (dom.getAttributeCount() != 0 || dom.hasChildren()) {
                ((MapModel) element).addExtension(new UnknownElements(dom));
            }
        }

        private void clear() {
            map = null;
        }

        private MapModel map() {
            if (map == null) {
                throw new AssertionError("Map reader did not create a map");
            }
            return map;
        }
    }

    private static final class FixtureNodeBuilder implements IElementDOMHandler {
        private final FixtureMapBuilder mapBuilder;

        private FixtureNodeBuilder(final FixtureMapBuilder mapBuilder) {
            this.mapBuilder = mapBuilder;
        }

        @Override
        public Object createElement(final Object parent, final String tag, final XMLElement attributes) {
            return new NodeModel(mapBuilder.map());
        }

        @Override
        public void endElement(final Object parent, final String tag, final Object element, final XMLElement dom) {
            NodeModel node = (NodeModel) element;
            if (dom.getAttributeCount() != 0 || dom.hasChildren()) {
                node.addExtension(new UnknownElements(dom));
            }
            if (parent instanceof MapModel) {
                ((MapModel) parent).setRoot(node);
            }
            else if (parent instanceof NodeModel) {
                ((NodeModel) parent).insert(node);
            }
        }
    }

    private static final class FixtureExtensionAttributeWriter implements IAttributeWriter {
        @Override
        public void writeAttributes(final ITreeWriter writer, final Object element, final String tag) {
            if (element instanceof MapModel) {
                writer.addExtensionAttributes(element, ((MapModel) element).getExtensions().values());
            }
            else if (element instanceof NodeModel) {
                writer.addExtensionAttributes(element, ((NodeModel) element).getSharedExtensions().values());
            }
        }
    }

    private static final class FixtureMapWriter implements IElementWriter {
        @Override
        public void writeContent(final ITreeWriter writer, final Object element, final String tag) throws IOException {
            MapModel map = (MapModel) element;
            writer.addExtensionNodes(map, map.getExtensions().values());
            writer.addElement(map.getRootNode(), "node");
        }
    }

    private static final class FixtureNodeWriter implements IElementWriter {
        @Override
        public void writeContent(final ITreeWriter writer, final Object element, final String tag) throws IOException {
            NodeModel node = (NodeModel) element;
            List<IExtension> extensions = new ArrayList<IExtension>(node.getSharedExtensions().values());
            writer.addExtensionNodes(node, extensions);
            for (NodeModel child : node.getChildren()) {
                writer.addElement(child, "node");
            }
        }
    }
}
