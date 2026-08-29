package org.freeplane.plugin.graph.canvas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.swing.JScrollPane;
import javax.swing.JViewport;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.main.application.ApplicationResourceController;
import org.freeplane.plugin.graph.control.CanvasState;
import org.freeplane.plugin.graph.control.OperationalStatus;
import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.HullGeometry;
import org.freeplane.plugin.graph.geometry.LabelPlacement;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.geometry.NodeGeometry;
import org.freeplane.plugin.graph.group.GraphGroupColors;
import org.freeplane.plugin.graph.layout.LayoutFrame;
import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.EdgeContributor;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEdgeKey;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.RelationshipResolution;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.input.ConnectorDescriptor;
import org.freeplane.plugin.graph.projection.input.ConnectorSnapshot;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.GraphRelationshipRecord;
import org.freeplane.plugin.graph.workspace.model.MapReference;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PinRecord;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.freeplane.plugin.graph.workspace.model.RelationshipDirection;
import org.freeplane.plugin.graph.workspace.model.RelationshipId;
import org.freeplane.plugin.graph.workspace.model.UnknownXml;
import org.freeplane.plugin.graph.workspace.model.DisplaySettings.CanvasTheme;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

public class GraphCanvasPaintShould {
    private static final MapReferenceId FIRST_MAP =
        MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    private static final MapReferenceId SECOND_MAP =
        MapReferenceId.of("00000000-0000-0000-0000-000000000007");
    private static final MapReferenceId UNREGISTERED_MAP =
        MapReferenceId.of("00000000-0000-0000-0000-000000000099");
    private static final Dimension SIZE = new Dimension(240, 140);

    private ApplicationResourceController resources;
    private MockedStatic<ResourceController> resourceController;

    @Before
    public void setUp() {
        resourceController = org.mockito.Mockito.mockStatic(ResourceController.class);
        resources = mock(ApplicationResourceController.class);
        resourceController.when(ResourceController::getResourceController).thenReturn(resources);
    }

    @After
    public void tearDown() {
        resourceController.close();
    }

    @Test
    public void paintBoundaryShapesOnlyAndNeverNodeCircles() {
        EnclosureKey boundaryKey = EnclosureKey.of(source(FIRST_MAP, "boundary-only"));
        EnclosureHullKey boundaryHull = EnclosureHullKey.of(Collections.singletonList(boundaryKey));
        ProjectedEnclosure boundary = ProjectedEnclosure.of(boundaryHull,
            Collections.singletonList(boundaryKey),
            Collections.singletonList(SafeNodeLabel.of("Boundary only", "Boundary only")), "Map",
            Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(),
            Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.SUBTLE);
        ProjectedNode retained = node(FIRST_MAP, "retained", LayoutPoint.of(-45.0, 0.0));
        Map<ProjectedNodeKey, NodeGeometry> nodeGeometry =
            new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
        nodeGeometry.put(retained.key(), NodeGeometry.of(LayoutPoint.of(-45.0, 0.0), 8.0));
        Map<EnclosureHullKey, HullGeometry> hulls = new LinkedHashMap<EnclosureHullKey, HullGeometry>();
        hulls.put(boundaryHull, rectangle(-50.0, -20.0, 50.0, 20.0, LayoutPoint.of(0.0, 0.0)));
        GraphProjection projection = GraphProjection.projected(1L, Collections.singletonList(retained),
            Collections.singletonList(boundary), Collections.<ProjectedEdge>emptyList(),
            Collections.<RelationshipResolution>emptyList(), Collections.<PinProjection>emptyList());
        LayoutFrame layout = LayoutFrame.of(1L, LayoutPositions.of(
            Collections.singletonMap(retained.key(), LayoutPoint.of(-45.0, 0.0)),
            Collections.singletonMap(boundaryHull, LayoutPoint.of(0.0, 0.0))), false);
        CanvasState state = CanvasState.of(1L, projection, layout,
            GraphGeometry.of(nodeGeometry, hulls), OperationalStatus.IDLE);
        GraphTheme theme = lightTheme();
        BufferedImage image = paint(state, GraphPaintState.empty(), theme, RenderingLevel.FULL);

        assertThat(colorPixelsIn(image, theme.nodeFill(), 0, 0, SIZE.width, SIZE.height)).isZero();
        assertThat(nonBackgroundPixels(image, theme.background())).isGreaterThan(100);
        assertThat(image.getRGB(120, 70)).isEqualTo(new Color(0xDF, 0x62, 0x5D).getRGB());
    }

    @Test
    public void paintsGroupBoundariesInTheConfiguredColor() {
        Color configured = new Color(0x22, 0x55, 0xAA);
        when(resources.getColorProperty(GraphGroupColors.COLOR_PROPERTY_KEY)).thenReturn(configured);
        EnclosureKey boundaryKey = EnclosureKey.of(source(FIRST_MAP, "boundary-only"));
        EnclosureHullKey boundaryHull = EnclosureHullKey.of(Collections.singletonList(boundaryKey));
        ProjectedEnclosure boundary = ProjectedEnclosure.of(boundaryHull,
            Collections.singletonList(boundaryKey),
            Collections.singletonList(SafeNodeLabel.of("Boundary only", "Boundary only")), "Map",
            Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(),
            Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.SUBTLE);
        ProjectedNode retained = node(FIRST_MAP, "retained", LayoutPoint.of(-45.0, 0.0));
        Map<ProjectedNodeKey, NodeGeometry> nodeGeometry =
            new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
        nodeGeometry.put(retained.key(), NodeGeometry.of(LayoutPoint.of(-45.0, 0.0), 8.0));
        Map<EnclosureHullKey, HullGeometry> hulls = new LinkedHashMap<EnclosureHullKey, HullGeometry>();
        hulls.put(boundaryHull, rectangle(-50.0, -20.0, 50.0, 20.0, LayoutPoint.of(0.0, 0.0)));
        GraphProjection projection = GraphProjection.projected(1L, Collections.singletonList(retained),
            Collections.singletonList(boundary), Collections.<ProjectedEdge>emptyList(),
            Collections.<RelationshipResolution>emptyList(), Collections.<PinProjection>emptyList());
        LayoutFrame layout = LayoutFrame.of(1L, LayoutPositions.of(
            Collections.singletonMap(retained.key(), LayoutPoint.of(-45.0, 0.0)),
            Collections.singletonMap(boundaryHull, LayoutPoint.of(0.0, 0.0))), false);
        CanvasState state = CanvasState.of(1L, projection, layout,
            GraphGeometry.of(nodeGeometry, hulls), OperationalStatus.IDLE);
        GraphTheme theme = lightTheme();
        BufferedImage image = paint(state, GraphPaintState.empty(), theme, RenderingLevel.FULL);

        assertThat(image.getRGB(120, 70)).isEqualTo(configured.getRGB());
        assertThat(colorPixelsIn(image, new Color(0xDF, 0x62, 0x5D), 0, 0, SIZE.width, SIZE.height))
            .as("default coral must not appear").isZero();
    }

    @Test
    public void paintOpaqueLayeredProjectionUsingGeometryAndProminence() {
        Fixture fixture = fixture(16.0);
        GraphTheme theme = lightTheme();
        BufferedImage image = paint(fixture.state, GraphPaintState.empty(), theme, RenderingLevel.FULL);

        assertThat(allPixelsAreOpaque(image)).isTrue();
        assertThat(nonBackgroundPixels(image, theme.background())).isGreaterThan(100);
        assertThat(image.getRGB(120, 70)).isEqualTo(theme.edgeColor().getRGB());
        assertThat(image.getRGB(75, 70)).isEqualTo(theme.hullFill(FIRST_MAP, BoundaryTier.EMPHATIC).getRGB());
        assertThat(image.getRGB(55, 70)).isNotEqualTo(theme.background().getRGB());
        assertThat(image.getRGB(185, 70)).isNotEqualTo(theme.background().getRGB());
        assertThat(colorPixelsIn(image, theme.nodeFill(), 0, 0, SIZE.width, SIZE.height)).isZero();

        LayoutPoint attachment = fixture.state.geometry().edgeAttachment(fixture.edge.first(),
            fixture.state.layout().positions().anchors().get(fixture.secondHullKey));
        Point2D attachmentScreen = GraphViewport.of(0.0, 0.0, 1.0).toScreen(attachment, SIZE);
        assertThat(hasColorNear(image, attachmentScreen, theme.edgeColor(), 4)).isTrue();
        assertThat(nearColorPixelsIn(image, theme.edgeColor(), 147, 60, 155, 69, 80)).isGreaterThan(0);
        assertThat(nearColorPixelsIn(image, theme.edgeColor(), 92, 60, 100, 69, 80)).isEqualTo(0);
    }

    @Test
    public void doNotPaintOrHitEdgesToSuppressedEndpoints() {
        LabelFixture fixture = labelFixture(499);
        ProjectedEdge edge = directedEdge(fixture.selected, fixture.suppressed, "SELECTED",
            "suppressed-label");
        CanvasState withEdge = stateWithEdges(fixture.state, Collections.singletonList(edge));
        CanvasState withoutEdge = stateWithEdges(fixture.state, Collections.<ProjectedEdge>emptyList());
        GraphTheme theme = lightTheme();

        BufferedImage painted = paint(withEdge, GraphPaintState.empty(), theme, RenderingLevel.FULL);
        BufferedImage unconnected = paint(withoutEdge, GraphPaintState.empty(), theme, RenderingLevel.FULL);

        assertThat(differentPixels(painted, unconnected)).isZero();
        assertThat(GraphHitIndex.from(withEdge).edgeAt(LayoutPoint.of(-36.0, -10.0), 4.0)).isEmpty();
    }

    @Test
    public void skipEdgesWhenANodeHasOnlyALayoutPosition() {
        Fixture fixture = fixture(16.0);
        Map<ProjectedNodeKey, NodeGeometry> currentGeometry =
            new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
        currentGeometry.put(fixture.first.key(), fixture.state.geometry().nodes().get(fixture.first.key()));
        CanvasState state = CanvasState.of(fixture.state.generation(), fixture.state.projection(),
            fixture.state.layout(), GraphGeometry.of(currentGeometry, fixture.state.geometry().hulls(),
                fixture.state.geometry().labels()), fixture.state.status());
        CanvasState withoutEdge = stateWithEdges(state, Collections.<ProjectedEdge>emptyList());
        GraphTheme theme = lightTheme();

        BufferedImage painted = paint(state, GraphPaintState.empty(), theme, RenderingLevel.FULL);
        BufferedImage unconnected = paint(withoutEdge, GraphPaintState.empty(), theme,
            RenderingLevel.FULL);

        assertThat(differentPixels(painted, unconnected)).isZero();
        assertThat(GraphHitIndex.from(state).edgeAt(LayoutPoint.of(0.0, 0.0), 4.0)).isEmpty();
    }

    @Test
    public void drawMultiplicityOnlyForAnEdgeWithMultipleContributors() {
        Fixture fixture = fixture(16.0);
        GraphTheme theme = lightTheme();
        BufferedImage image = paint(fixture.state, GraphPaintState.empty(), theme, RenderingLevel.FULL);

        assertThat(nonBackgroundPixelsIn(image, theme.background(), 112, 53, 128, 66)).isGreaterThan(0);
        assertThat(fixture.edge.hasMultiplicityCue()).isTrue();
        assertThat(fixture.edge.contributorCount()).isEqualTo(2);
    }

    @Test
    public void useDedicatedEmphaticLabelsAndPreserveLevelSpecificVisibility() {
        GraphTheme theme = lightTheme();
        assertThat(theme.emphaticLabelFont().isBold()).isTrue();
        assertThat(theme.emphaticLabelFont().getSize2D()).isGreaterThan(theme.labelFont().getSize2D());

        LabelFixture fullFixture = labelFixture(499);
        LabelFixture denseFixture = labelFixture(2000);
        LabelFixture overTargetFixture = labelFixture(2001);
        BufferedImage full = paintLabelFixture(fullFixture, theme, forcedOrdinaryPaintState(fullFixture));
        BufferedImage dense = paintLabelFixture(denseFixture, theme, forcedOrdinaryPaintState(denseFixture));
        BufferedImage overTarget = paintLabelFixture(overTargetFixture, theme,
            forcedOrdinaryPaintState(overTargetFixture));

        assertThat(labelPixels(full, theme, 0, 0, 75, 36)).isGreaterThan(0);
        assertThat(labelPixels(dense, theme, 0, 0, 75, 36)).isGreaterThan(0);
        assertThat(labelPixels(overTarget, theme, 0, 0, 75, 36)).isEqualTo(0);
        assertThat(labelPixels(full, theme, 160, 95, 240, 136)).isGreaterThan(0);
        assertThat(labelPixels(dense, theme, 160, 95, 240, 136)).isGreaterThan(0);
        assertThat(labelPixels(overTarget, theme, 160, 95, 240, 136)).isEqualTo(0);
        assertForcedOrdinaryLabelsVisible(full, theme);
        assertForcedOrdinaryLabelsVisible(dense, theme);
        assertForcedOrdinaryLabelsVisible(overTarget, theme);
        assertEmphaticGlyphUsesDedicatedFont(full, theme);
        assertEmphaticGlyphUsesDedicatedFont(dense, theme);
        assertEmphaticGlyphUsesDedicatedFont(overTarget, theme);
        assertForcedOrdinaryGlyphsUseFullDetailFont(overTarget, theme);

        LabelFixture suppressedFixture = labelFixture(499);
        BufferedImage suppressed = paintLabelFixture(suppressedFixture, theme,
            forcedSuppressedPaintState(suppressedFixture));
        assertThat(nonBackgroundPixelsIn(suppressed, theme.background(), 0, 55, 100, 95)).isEqualTo(0);
    }

    @Test
    public void doNotPaintNodeKeyedPinMarkers() {
        Fixture fixture = fixture(16.0);
        PinProjection active = PinProjection.active(PinRecord.of(reference(FIRST_MAP, "first"), -10.0, -40.0,
            Collections.<UnknownXml>emptyList()), fixture.first.key());
        PinProjection dormant = PinProjection.dormant(PinRecord.of(reference(SECOND_MAP, "dormant"), 60.0,
            -40.0, Collections.<UnknownXml>emptyList()));
        GraphProjection projection = GraphProjection.projected(1L, fixture.state.projection().nodes(),
            fixture.state.projection().enclosures(), fixture.state.projection().edges(),
            fixture.state.projection().relationshipResolutions(), Arrays.asList(active, dormant));
        CanvasState state = CanvasState.of(1L, projection, fixture.state.layout(), fixture.state.geometry(),
            OperationalStatus.IDLE);
        GraphTheme theme = lightTheme();
        BufferedImage image = paint(state, GraphPaintState.empty(), theme, RenderingLevel.FULL);

        // Node-keyed pins have no current hull: boundary-only painting never draws pin crosses.
        assertThat(colorPixelsIn(image, theme.pinColor(), 0, 0, SIZE.width, SIZE.height)).isZero();
    }

    @Test
    public void skipActivePinsForNodesWithoutCurrentGeometry() {
        Fixture fixture = fixture(16.0);
        PinProjection active = PinProjection.active(PinRecord.of(reference(FIRST_MAP, "first"), -10.0, -40.0,
            Collections.<UnknownXml>emptyList()), fixture.first.key());
        CanvasState withPin = stateWithPins(fixture.state, Collections.singletonList(active));
        Map<ProjectedNodeKey, NodeGeometry> currentGeometry =
            new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
        currentGeometry.put(fixture.second.key(), fixture.state.geometry().nodes().get(fixture.second.key()));
        CanvasState withoutFirstNodeGeometry = CanvasState.of(withPin.generation(), withPin.projection(),
            withPin.layout(), GraphGeometry.of(currentGeometry, withPin.geometry().hulls(),
                withPin.geometry().labels()), withPin.status());
        CanvasState withoutPin = stateWithPins(withoutFirstNodeGeometry,
            Collections.<PinProjection>emptyList());
        GraphTheme theme = lightTheme();

        BufferedImage painted = paint(withoutFirstNodeGeometry, GraphPaintState.empty(), theme,
            RenderingLevel.FULL);
        BufferedImage unpinned = paint(withoutPin, GraphPaintState.empty(), theme, RenderingLevel.FULL);

        assertThat(differentPixels(painted, unpinned)).isZero();
        assertThat(colorPixelsIn(painted, theme.pinColor(), 0, 0, SIZE.width, SIZE.height)).isZero();
        assertThat(colorPixelsIn(unpinned, theme.pinColor(), 0, 0, SIZE.width, SIZE.height)).isZero();
    }

    @Test
    public void doNotPaintConnectionPreviewForUnavailableSources() {
        Fixture fixture = fixture(16.0);
        GraphTheme theme = lightTheme();
        LayoutPoint from = LayoutPoint.of(-35.0, -35.0);
        LayoutPoint to = LayoutPoint.of(35.0, -35.0);

        GraphPaintState currentPreview = GraphPaintState.empty().withConnectionPreview(
            GraphPaintState.ConnectionPreview.of(fixture.firstEndpoint, from, to));
        BufferedImage currentImage = paint(fixture.state, currentPreview, theme, RenderingLevel.FULL);
        assertThat(colorPixelsIn(currentImage, theme.previewColor(), 80, 25, 160, 45))
            .isGreaterThan(0);

        assertPreviewColorAbsent(withoutProjectedNode(fixture.state, fixture.first.key()),
            fixture.firstEndpoint, from, to, theme);
        ProjectedEndpointKey enclosureSource = ProjectedEndpointKey.ofEnclosure(
            fixture.state.projection().enclosures().get(0).endpointKeys().get(0));
        assertPreviewColorAbsent(withSuppressedEnclosure(fixture.state, enclosureSource),
            enclosureSource, from, to, theme);
        assertPreviewColorAbsent(withoutNodeGeometry(fixture.state, fixture.first.key()),
            fixture.firstEndpoint, from, to, theme);
    }

    @Test
    public void skipConnectionPreviewForEnclosureWhoseCurrentHullIsMissing() {
        Fixture fixture = fixture(16.0);
        GraphTheme theme = lightTheme();
        LayoutPoint from = LayoutPoint.of(-35.0, -35.0);
        LayoutPoint to = LayoutPoint.of(35.0, -35.0);
        ProjectedEnclosure previousEnclosure = fixture.state.projection().enclosures().get(0);
        ProjectedEndpointKey source = ProjectedEndpointKey.ofEnclosure(
            previousEnclosure.endpointKeys().get(0));
        GraphPaintState preview = GraphPaintState.empty().withConnectionPreview(
            GraphPaintState.ConnectionPreview.of(source, from, to));

        BufferedImage currentImage = paint(fixture.state, preview, theme, RenderingLevel.FULL);
        assertThat(colorPixelsIn(currentImage, theme.previewColor(), 80, 25, 160, 45))
            .isGreaterThan(0);

        CanvasState replacement = replacementWithMissingCurrentEnclosureHull(fixture.state, previousEnclosure);
        ProjectedEnclosure currentEnclosure = replacement.projection().enclosures().get(0);
        assertThat(currentEnclosure.endpointKeys()).contains(source.enclosure().get());
        assertThat(currentEnclosure.hullKey()).isNotEqualTo(previousEnclosure.hullKey());
        assertThat(replacement.geometry().hulls()).containsKey(previousEnclosure.hullKey());
        assertThat(replacement.geometry().hulls()).doesNotContainKey(currentEnclosure.hullKey());

        BufferedImage staleImage = paint(replacement, preview, theme, RenderingLevel.FULL);
        assertThat(colorPixelsIn(staleImage, theme.previewColor(), 80, 25, 160, 45)).isZero();
    }

    @Test
    public void skipOrdinaryEdgeWhenCurrentEnclosureHullIsMissing() {
        Fixture fixture = fixture(16.0);
        GraphTheme theme = lightTheme();
        ProjectedEnclosure previousEnclosure = fixture.state.projection().enclosures().get(0);
        ProjectedEndpointKey enclosureEndpoint = ProjectedEndpointKey.ofEnclosure(
            previousEnclosure.endpointKeys().get(0));
        ProjectedEdge enclosureEdge = enclosureToNodeEdge(enclosureEndpoint, fixture.secondEndpoint);

        CanvasState currentWithEdge = stateWithEdges(fixture.state, Collections.singletonList(enclosureEdge));
        BufferedImage currentImage = paint(currentWithEdge, GraphPaintState.empty(), theme, RenderingLevel.FULL);
        BufferedImage currentWithoutEdge = paint(stateWithEdges(fixture.state,
            Collections.<ProjectedEdge>emptyList()), GraphPaintState.empty(), theme, RenderingLevel.FULL);
        assertThat(differentPixels(currentImage, currentWithoutEdge)).isGreaterThan(0);

        CanvasState replacement = replacementWithMissingCurrentEnclosureHull(fixture.state, previousEnclosure);
        ProjectedEnclosure currentEnclosure = replacement.projection().enclosures().get(0);
        assertThat(currentEnclosure.endpointKeys()).contains(enclosureEndpoint.enclosure().get());
        assertThat(currentEnclosure.hullKey()).isNotEqualTo(previousEnclosure.hullKey());
        assertThat(replacement.geometry().hulls()).containsKey(previousEnclosure.hullKey());
        assertThat(replacement.geometry().hulls()).doesNotContainKey(currentEnclosure.hullKey());

        CanvasState staleWithEdge = stateWithEdges(replacement, Collections.singletonList(enclosureEdge));
        CanvasState staleWithoutEdge = stateWithEdges(replacement, Collections.<ProjectedEdge>emptyList());
        BufferedImage staleImage = paint(staleWithEdge, GraphPaintState.empty(), theme, RenderingLevel.FULL);
        BufferedImage staleWithoutEdgeImage = paint(staleWithoutEdge, GraphPaintState.empty(), theme,
            RenderingLevel.FULL);
        assertThat(differentPixels(staleImage, staleWithoutEdgeImage)).isZero();
    }

    @Test
    public void skipEnclosureLabelWhenCurrentHullIsMissing() {
        Fixture fixture = fixture(16.0);
        GraphTheme theme = lightTheme();
        ProjectedEnclosure previousEnclosure = fixture.state.projection().enclosures().get(0);
        EnclosureKey enclosureEndpoint = previousEnclosure.endpointKeys().get(0);
        BufferedImage currentImage = paint(fixture.state, GraphPaintState.empty(), theme, RenderingLevel.FULL);
        assertThat(labelPixels(currentImage, theme, 50, 70, 110, 100)).isGreaterThan(0);

        CanvasState replacement = replacementWithMissingCurrentEnclosureHull(fixture.state, previousEnclosure);
        ProjectedEnclosure currentEnclosure = replacement.projection().enclosures().get(0);
        assertThat(currentEnclosure.endpointKeys()).contains(enclosureEndpoint);
        assertThat(currentEnclosure.hullKey()).isNotEqualTo(previousEnclosure.hullKey());
        assertThat(replacement.geometry().hulls()).containsKey(previousEnclosure.hullKey());
        assertThat(replacement.geometry().hulls()).doesNotContainKey(currentEnclosure.hullKey());
        assertThat(replacement.geometry().labels()).containsKey(enclosureEndpoint);

        CanvasState withoutRetainedLabel = withoutEnclosureLabel(replacement, enclosureEndpoint);
        BufferedImage staleImage = paint(replacement, GraphPaintState.empty(), theme, RenderingLevel.FULL);
        BufferedImage withoutRetainedLabelImage = paint(withoutRetainedLabel, GraphPaintState.empty(), theme,
            RenderingLevel.FULL);
        assertThat(differentPixels(staleImage, withoutRetainedLabelImage)).isZero();
    }

    @Test
    public void resolveHullColorsFromRegisteredMapsAndTierTreatment() {
        GraphTheme theme = lightTheme();
        GraphTheme reconstructed = GraphTheme.resolve(CanvasTheme.LIGHT, registeredMaps());
        BufferedImage image = paint(paletteState(FIRST_MAP, SECOND_MAP, FIRST_MAP), GraphPaintState.empty(), theme,
            RenderingLevel.FULL);

        assertThat(Math.floorMod(FIRST_MAP.value().hashCode(), 6))
            .isEqualTo(Math.floorMod(SECOND_MAP.value().hashCode(), 6));
        assertThat(theme.hullFill(FIRST_MAP, BoundaryTier.EMPHATIC)).isEqualTo(new Color(207, 219, 232));
        assertThat(theme.hullFill(SECOND_MAP, BoundaryTier.EMPHATIC)).isEqualTo(new Color(244, 210, 212));
        assertThat(theme.hullStroke(FIRST_MAP, BoundaryTier.EMPHATIC)).isEqualTo(new Color(104, 141, 180));
        assertThat(theme.hullStroke(SECOND_MAP, BoundaryTier.EMPHATIC)).isEqualTo(new Color(229, 112, 114));
        assertThat(theme.hullFill(FIRST_MAP, BoundaryTier.EMPHATIC))
            .isNotEqualTo(theme.hullFill(SECOND_MAP, BoundaryTier.EMPHATIC));
        assertThat(theme.hullStroke(FIRST_MAP, BoundaryTier.EMPHATIC))
            .isNotEqualTo(theme.hullStroke(SECOND_MAP, BoundaryTier.EMPHATIC));
        assertThat(image.getRGB(30, 70)).isEqualTo(new Color(207, 219, 232).getRGB());
        assertThat(image.getRGB(120, 70)).isEqualTo(new Color(244, 210, 212).getRGB());
        assertThat(image.getRGB(210, 70)).isEqualTo(new Color(229, 235, 242).getRGB());
        assertThat(hasColorNear(image, new Point2D.Double(10.0, 70.0),
            new Color(104, 141, 180), 4)).isTrue();
        assertThat(hasColorNear(image, new Point2D.Double(100.0, 70.0),
            new Color(229, 112, 114), 4)).isTrue();
        assertThat(reconstructed.hullFill(FIRST_MAP, BoundaryTier.EMPHATIC))
            .isEqualTo(theme.hullFill(FIRST_MAP, BoundaryTier.EMPHATIC));
        assertThat(reconstructed.hullStroke(SECOND_MAP, BoundaryTier.EMPHATIC))
            .isEqualTo(theme.hullStroke(SECOND_MAP, BoundaryTier.EMPHATIC));
    }

    @Test
    public void paintVisibleEnclosureUsingPersistedPaletteInput() {
        Map<MapReferenceId, String> colors = new LinkedHashMap<MapReferenceId, String>();
        colors.put(FIRST_MAP, "#4E79A7");
        colors.put(SECOND_MAP, "#E15759");
        GraphTheme theme = GraphTheme.resolve(CanvasTheme.LIGHT, colors);

        BufferedImage image = paint(paletteState(FIRST_MAP, SECOND_MAP, FIRST_MAP),
            GraphPaintState.empty(), theme, RenderingLevel.FULL);

        assertThat(nonBackgroundPixels(image, theme.background())).isGreaterThan(100);
    }

    @Test
    public void suppressAndRestoreDirectionalArrowheadsInThePaintedCanvas() {
        Fixture fixture = fixture(16.0);
        GraphTheme theme = lightTheme();
        GraphCanvas canvas = new GraphCanvas();
        canvas.setSize(SIZE);
        canvas.setTheme(theme);
        canvas.setCanvasState(fixture.state);
        canvas.setViewport(GraphViewport.of(0.0, 0.0, 1.0));
        canvas.setShowArrowheads(false);
        BufferedImage withoutArrowheads = paintCanvas(canvas);

        canvas.setShowArrowheads(true);
        BufferedImage withArrowheads = paintCanvas(canvas);
        ProjectedEdge noArrowEdge = ProjectedEdge.of(fixture.edge.key(), Arrays.asList(
            undirectedRelationshipContributor("00000000-0000-0000-0000-000000000021", 1L,
                fixture.firstEndpoint, fixture.secondEndpoint),
            undirectedRelationshipContributor("00000000-0000-0000-0000-000000000022", 2L,
                fixture.firstEndpoint, fixture.secondEndpoint)));
        BufferedImage expectedWithoutArrowheads = paint(stateWithEdges(fixture.state,
            Collections.singletonList(noArrowEdge)), GraphPaintState.empty(), theme, RenderingLevel.FULL);

        assertThat(fixture.edge.arrowAtSecond()).isTrue();
        assertThat(differentPixels(withoutArrowheads, expectedWithoutArrowheads)).isZero();
        assertThat(differentPixels(withArrowheads, expectedWithoutArrowheads)).isGreaterThan(0);
    }

    @Test
    public void keepBoundariesUndimmedUntilATransientDimmingTriggerIsActive() {
        Fixture fixture = fixture(16.0);
        GraphTheme theme = lightTheme();
        BufferedImage baseline = paint(fixture.state, GraphPaintState.empty(), theme, RenderingLevel.FULL);

        GraphCanvas canvas = new GraphCanvas();
        canvas.setSize(SIZE);
        canvas.setTheme(theme);
        canvas.setCanvasState(fixture.state);
        canvas.setViewport(GraphViewport.of(0.0, 0.0, 1.0));
        canvas.setPaintState(GraphPaintState.empty());
        canvas.setDimUnrelated(true);
        BufferedImage image = paintCanvas(canvas);

        assertThat(image.getRGB(75, 70)).isEqualTo(theme.hullFill(FIRST_MAP, BoundaryTier.EMPHATIC).getRGB());
        assertThat(image.getRGB(75, 70)).isEqualTo(baseline.getRGB(75, 70));
        assertThat(image.getRGB(165, 70)).isEqualTo(baseline.getRGB(165, 70));
    }

    @Test
    public void dimUnrelatedBoundariesWhenTransientTriggerAndPreferenceAreEnabled() {
        Fixture fixture = fixture(16.0);
        GraphTheme theme = lightTheme();
        GraphPaintState active = GraphPaintState.empty()
            .withSelection(fixture.firstHullEndpoint).withDimUnrelated(true);

        GraphCanvas canvas = new GraphCanvas();
        canvas.setSize(SIZE);
        canvas.setTheme(theme);
        canvas.setCanvasState(fixture.state);
        canvas.setViewport(GraphViewport.of(0.0, 0.0, 1.0));
        canvas.setPaintState(active);
        canvas.setDimUnrelated(true);
        BufferedImage image = paintCanvas(canvas);

        assertThat(image.getRGB(75, 70)).isEqualTo(theme.hullFill(FIRST_MAP, BoundaryTier.EMPHATIC).getRGB());
        assertThat(image.getRGB(165, 70)).isNotEqualTo(theme.hullFill(SECOND_MAP, BoundaryTier.SUBTLE).getRGB());
    }

    @Test
    public void keepUnrelatedBoundariesUndimmedWhenTransientTriggerMeetsDisabledPreference() {
        Fixture fixture = fixture(16.0);
        GraphTheme theme = lightTheme();
        GraphPaintState active = GraphPaintState.empty()
            .withSelection(fixture.firstHullEndpoint).withDimUnrelated(true);

        GraphCanvas canvas = new GraphCanvas();
        canvas.setSize(SIZE);
        canvas.setTheme(theme);
        canvas.setCanvasState(fixture.state);
        canvas.setViewport(GraphViewport.of(0.0, 0.0, 1.0));
        canvas.setDimUnrelated(false);
        canvas.setPaintState(active);
        BufferedImage image = paintCanvas(canvas);

        assertThat(image.getRGB(75, 70)).isEqualTo(theme.hullFill(FIRST_MAP, BoundaryTier.EMPHATIC).getRGB());
        assertThat(image.getRGB(165, 70)).isEqualTo(theme.hullFill(SECOND_MAP, BoundaryTier.SUBTLE).getRGB());
        assertThat(canvas.paintState().dimUnrelated()).isTrue();
    }

    @Test
    public void rejectPaintingAnUnregisteredMapColor() {
        GraphTheme theme = lightTheme();

        assertThatThrownBy(() -> paint(paletteState(UNREGISTERED_MAP, SECOND_MAP, FIRST_MAP),
            GraphPaintState.empty(), theme, RenderingLevel.FULL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(UNREGISTERED_MAP.value().toString());
    }

    @Test
    public void exposeThemeBindingAsAPublicMethod() throws NoSuchMethodException {
        assertThat(Modifier.isPublic(GraphCanvas.class.getMethod("setTheme", GraphTheme.class).getModifiers()))
            .isTrue();
    }

    private static CanvasState stateWithEdges(final CanvasState base,
            final List<ProjectedEdge> edges) {
        GraphProjection projection = GraphProjection.projected(base.generation(), base.projection().nodes(),
            base.projection().enclosures(), edges, base.projection().relationshipResolutions(),
            base.projection().pins());
        return CanvasState.of(base.generation(), projection, base.layout(), base.geometry(), base.status());
    }

    private static CanvasState stateWithPins(final CanvasState base, final List<PinProjection> pins) {
        GraphProjection projection = GraphProjection.projected(base.generation(), base.projection().nodes(),
            base.projection().enclosures(), base.projection().edges(), base.projection().relationshipResolutions(),
            pins);
        return CanvasState.of(base.generation(), projection, base.layout(), base.geometry(), base.status());
    }

    private static void assertPreviewColorAbsent(final CanvasState state,
            final ProjectedEndpointKey source, final LayoutPoint from, final LayoutPoint to,
            final GraphTheme theme) {
        GraphPaintState preview = GraphPaintState.empty().withConnectionPreview(
            GraphPaintState.ConnectionPreview.of(source, from, to));
        BufferedImage image = paint(state, preview, theme, RenderingLevel.FULL);

        assertThat(colorPixelsIn(image, theme.previewColor(), 80, 25, 160, 45)).isZero();
    }

    private static CanvasState withoutProjectedNode(final CanvasState base,
            final ProjectedNodeKey removed) {
        final List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        for (final ProjectedNode node : base.projection().nodes()) {
            if (!removed.equals(node.key())) {
                nodes.add(node);
            }
        }
        return replacementState(base, nodes, base.projection().enclosures(), base.geometry());
    }

    private static CanvasState withSuppressedEnclosure(final CanvasState base,
            final ProjectedEndpointKey source) {
        final List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        for (final ProjectedEnclosure enclosure : base.projection().enclosures()) {
            if (enclosure.endpointKeys().contains(source.enclosure().get())) {
                enclosures.add(ProjectedEnclosure.of(enclosure.hullKey(), enclosure.endpointKeys(),
                    enclosure.labels(), enclosure.mapName(), enclosure.parentHull(), enclosure.directNodes(),
                    enclosure.directEnclosures(), enclosure.mapRoot(), BoundaryTier.SUPPRESSED));
            }
            else {
                enclosures.add(enclosure);
            }
        }
        return replacementState(base, base.projection().nodes(), enclosures, base.geometry());
    }

    private static CanvasState replacementWithMissingCurrentEnclosureHull(final CanvasState base,
            final ProjectedEnclosure previous) {
        EnclosureKey addedEndpoint = EnclosureKey.of(source(FIRST_MAP, "replacement-hull"));
        List<EnclosureKey> endpointKeys = Arrays.asList(previous.endpointKeys().get(0), addedEndpoint);
        EnclosureHullKey currentHullKey = EnclosureHullKey.of(endpointKeys);
        ProjectedEnclosure current = ProjectedEnclosure.of(currentHullKey, endpointKeys,
            Arrays.asList(previous.labels().get(0), SafeNodeLabel.of("Replacement", "Replacement")),
            previous.mapName(), previous.parentHull(), previous.directNodes(), previous.directEnclosures(),
            previous.mapRoot(), previous.boundaryTier());
        List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        for (final ProjectedEnclosure enclosure : base.projection().enclosures()) {
            enclosures.add(enclosure.equals(previous) ? current : enclosure);
        }
        return replacementState(base, base.projection().nodes(), enclosures, base.geometry());
    }

    private static CanvasState withoutEnclosureLabel(final CanvasState base,
            final EnclosureKey removed) {
        final Map<EnclosureKey, LabelPlacement> labels =
            new LinkedHashMap<EnclosureKey, LabelPlacement>(base.geometry().labels());
        labels.remove(removed);
        return CanvasState.of(base.generation(), base.projection(), base.layout(),
            GraphGeometry.of(base.geometry().nodes(), base.geometry().hulls(), labels), base.status());
    }

    private static ProjectedEdge enclosureToNodeEdge(final ProjectedEndpointKey enclosure,
            final ProjectedEndpointKey node) {
        GraphRelationshipRecord relationship = GraphRelationshipRecord.of(
            RelationshipId.of("00000000-0000-0000-0000-000000000013"), 3L,
            reference(FIRST_MAP, "enclosure-source"), reference(SECOND_MAP, "second"),
            RelationshipDirection.UNDIRECTED, Collections.<UnknownXml>emptyList());
        EdgeContributor contributor = EdgeContributor.graphRelationship(relationship, enclosure, node);
        return ProjectedEdge.of(ProjectedEdgeKey.of(enclosure, node), Collections.singletonList(contributor));
    }

    private static CanvasState withoutNodeGeometry(final CanvasState base,
            final ProjectedNodeKey removed) {
        final Map<ProjectedNodeKey, NodeGeometry> nodes =
            new LinkedHashMap<ProjectedNodeKey, NodeGeometry>(base.geometry().nodes());
        nodes.remove(removed);
        return replacementState(base, base.projection().nodes(), base.projection().enclosures(),
            GraphGeometry.of(nodes, base.geometry().hulls(), base.geometry().labels()));
    }

    private static CanvasState replacementState(final CanvasState base, final List<ProjectedNode> nodes,
            final List<ProjectedEnclosure> enclosures, final GraphGeometry geometry) {
        final GraphProjection projection = GraphProjection.projected(base.generation(), nodes, enclosures,
            Collections.<ProjectedEdge>emptyList(), base.projection().relationshipResolutions(),
            Collections.<PinProjection>emptyList());
        return CanvasState.of(base.generation(), projection, base.layout(), geometry, base.status());
    }

    private static ProjectedEdge directedEdge(final ProjectedEndpointKey source,
            final ProjectedEndpointKey target, final String sourceId, final String targetId) {
        ConnectorDescriptor descriptor = ConnectorDescriptor.of(
            SourceNodeKey.persisted(reference(FIRST_MAP, sourceId)), reference(FIRST_MAP, targetId),
            false, true, "source", "middle", "target");
        EdgeContributor contributor = EdgeContributor.nativeConnector(
            ConnectorSnapshot.of(0, descriptor), source, target);
        return ProjectedEdge.of(ProjectedEdgeKey.of(source, target), Collections.singletonList(contributor));
    }

    private static void assertForcedOrdinaryLabelsVisible(final BufferedImage image, final GraphTheme theme) {
        assertThat(labelPixels(image, theme, 80, 0, 160, 36)).isGreaterThan(0);
        assertThat(labelPixels(image, theme, 165, 0, 240, 36)).isGreaterThan(0);
        assertThat(labelPixels(image, theme, 0, 80, 75, 111)).isGreaterThan(0);
    }

    private static void assertEmphaticGlyphUsesDedicatedFont(final BufferedImage image,
            final GraphTheme theme) {
        final Font emphatic = zoomAdjustedFont(theme.emphaticLabelFont(), 1.0);
        final Font normal = zoomAdjustedFont(theme.labelFont(), 1.0);
        assertThat(stringWidth(emphatic, "EMPHATIC")).isGreaterThan(stringWidth(normal, "EMPHATIC"));
        assertThat(fontHeight(emphatic)).isGreaterThan(fontHeight(normal));

        final Rectangle actual = glyphBounds(image, theme.labelColor(), 75, 95, 165, 136);
        final Rectangle expected = glyphBounds(paintReferenceLabel("EMPHATIC", emphatic, 120.0, 115.0,
            theme.labelColor()), theme.labelColor(), 75, 95, 165, 136);
        final Rectangle normalBounds = glyphBounds(paintReferenceLabel("EMPHATIC", normal, 120.0, 115.0,
            theme.labelColor()), theme.labelColor(), 75, 95, 165, 136);
        assertThat(actual).isEqualTo(expected);
        assertThat(actual.width).isGreaterThan(normalBounds.width);
        assertThat(actual.height).isGreaterThanOrEqualTo(normalBounds.height);
    }

    private static void assertForcedOrdinaryGlyphsUseFullDetailFont(final BufferedImage image,
            final GraphTheme theme) {
        assertGlyphUsesFullDetailFont(image, theme, "SELECTED", 120.0, 29.0, 75, 0, 165, 36);
        assertGlyphUsesFullDetailFont(image, theme, "HOVERED", 205.0, 29.0, 165, 0, 240, 36);
        assertGlyphUsesFullDetailFont(image, theme, "SEARCH", 35.0, 99.0, 0, 80, 75, 104);
    }

    private static void assertGlyphUsesFullDetailFont(final BufferedImage image, final GraphTheme theme,
            final String text, final double x, final double y, final int minX, final int minY,
            final int maxX, final int maxY) {
        final Rectangle actual = glyphBounds(image, theme.labelColor(), minX, minY, maxX, maxY);
        final Rectangle normal = glyphBounds(paintReferenceLabel(text, zoomAdjustedFont(theme.labelFont(), 1.0), x,
            y, theme.labelColor()), theme.labelColor(), minX, minY, maxX, maxY);
        final Rectangle degraded = glyphBounds(paintReferenceLabel(text,
            zoomAdjustedFont(theme.overTargetLabelFont(), 1.0), x, y, theme.labelColor()), theme.labelColor(),
            minX, minY, maxX, maxY);
        assertThat(actual).isEqualTo(normal);
        assertThat(actual).isNotEqualTo(degraded);
    }

    private static GraphPaintState forcedOrdinaryPaintState(final LabelFixture fixture) {
        return GraphPaintState.empty().withSelection(fixture.selected)
            .withHover(fixture.hovered).withSearchMatches(Collections.singleton(fixture.searchMatched));
    }

    private static GraphPaintState forcedSuppressedPaintState(final LabelFixture fixture) {
        return GraphPaintState.empty().withSelection(fixture.suppressed)
            .withHover(fixture.suppressed).withSearchMatches(Collections.singleton(fixture.suppressed));
    }

    private static int labelPixels(final BufferedImage image, final GraphTheme theme, final int minX,
            final int minY, final int maxX, final int maxY) {
        return nearColorPixelsIn(image, theme.labelColor(), minX, minY, maxX, maxY, 100);
    }

    private static Font zoomAdjustedFont(final Font base, final double zoom) {
        return base.deriveFont(Math.max(1.0f, base.getSize2D() / (float) zoom));
    }

    private static int stringWidth(final Font font, final String text) {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            return graphics.getFontMetrics(font).stringWidth(text);
        }
        finally {
            graphics.dispose();
        }
    }

    private static int fontHeight(final Font font) {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            return graphics.getFontMetrics(font).getHeight();
        }
        finally {
            graphics.dispose();
        }
    }

    private static BufferedImage paintReferenceLabel(final String text, final Font font, final double x,
            final double y, final Color color) {
        BufferedImage image = new BufferedImage(SIZE.width, SIZE.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                java.awt.RenderingHints.VALUE_RENDER_SPEED);
            graphics.setColor(new Color(250, 251, 253));
            graphics.fillRect(0, 0, SIZE.width, SIZE.height);
            graphics.translate(SIZE.width * 0.5, SIZE.height * 0.5);
            graphics.setFont(font);
            graphics.setColor(color);
            FontMetrics metrics = graphics.getFontMetrics();
            float width = metrics.stringWidth(text);
            float baseline = (metrics.getAscent() - metrics.getDescent()) * 0.5f;
            graphics.drawString(text, (float) (x - SIZE.width * 0.5 - width * 0.5f),
                (float) (y - SIZE.height * 0.5 + baseline));
        }
        finally {
            graphics.dispose();
        }
        return image;
    }

    private static Rectangle glyphBounds(final BufferedImage image, final Color color, final int minX,
            final int minY, final int maxX, final int maxY) {
        int left = maxX;
        int top = maxY;
        int right = minX - 1;
        int bottom = minY - 1;
        for (int y = Math.max(0, minY); y < Math.min(image.getHeight(), maxY); y++) {
            for (int x = Math.max(0, minX); x < Math.min(image.getWidth(), maxX); x++) {
                final int pixel = image.getRGB(x, y);
                final int red = ((pixel >>> 16) & 0xff) - color.getRed();
                final int green = ((pixel >>> 8) & 0xff) - color.getGreen();
                final int blue = (pixel & 0xff) - color.getBlue();
                if (red * red + green * green + blue * blue <= 10000) {
                    left = Math.min(left, x);
                    top = Math.min(top, y);
                    right = Math.max(right, x);
                    bottom = Math.max(bottom, y);
                }
            }
        }
        return right < left ? new Rectangle() : new Rectangle(left, top, right - left + 1, bottom - top + 1);
    }

    private static BufferedImage paintLabelFixture(final LabelFixture fixture, final GraphTheme theme,
            final GraphPaintState paintState) {
        GraphCanvas canvas = new GraphCanvas();
        canvas.setSize(SIZE);
        canvas.setTheme(theme);
        canvas.setCanvasState(fixture.state);
        canvas.setPaintState(paintState);
        canvas.setViewport(GraphViewport.of(0.0, 0.0, 1.0));
        return paintCanvas(canvas);
    }

    private static GraphTheme lightTheme() {
        return GraphTheme.resolve(CanvasTheme.LIGHT, registeredMaps());
    }

    private static List<MapReference> registeredMaps() {
        return Arrays.asList(mapReference(FIRST_MAP, 1L, "#4E79A7"),
            mapReference(SECOND_MAP, 2L, "#E15759"));
    }

    private static MapReference mapReference(final MapReferenceId id, final long sequence,
            final String color) {
        return MapReference.of(id, sequence, URI.create("maps/" + sequence + ".mm"), true, color,
            Collections.<UnknownXml>emptyList());
    }

    private static CanvasState paletteState(final MapReferenceId firstMap, final MapReferenceId secondMap,
            final MapReferenceId firstChildMap) {
        EnclosureKey firstRootKey = EnclosureKey.of(source(firstMap, "palette-first-root"));
        EnclosureKey secondRootKey = EnclosureKey.of(source(secondMap, "palette-second-root"));
        EnclosureKey firstChildKey = EnclosureKey.of(source(firstChildMap, "palette-first-child"));
        EnclosureHullKey firstRootHull = EnclosureHullKey.of(Collections.singletonList(firstRootKey));
        EnclosureHullKey secondRootHull = EnclosureHullKey.of(Collections.singletonList(secondRootKey));
        EnclosureHullKey firstChildHull = EnclosureHullKey.of(Collections.singletonList(firstChildKey));
        ProjectedEnclosure firstRoot = enclosure(firstRootHull, firstRootKey, "First root", "First root",
            BoundaryTier.EMPHATIC);
        ProjectedEnclosure secondRoot = enclosure(secondRootHull, secondRootKey, "Second root", "Second root",
            BoundaryTier.EMPHATIC);
        ProjectedEnclosure firstChild = enclosure(firstChildHull, firstChildKey, "First child", "First child",
            BoundaryTier.SUBTLE);
        Map<EnclosureHullKey, HullGeometry> hulls = new LinkedHashMap<EnclosureHullKey, HullGeometry>();
        hulls.put(firstRootHull, rectangle(-110.0, -20.0, -70.0, 20.0, LayoutPoint.of(-90.0, 0.0)));
        hulls.put(secondRootHull, rectangle(-20.0, -20.0, 20.0, 20.0, LayoutPoint.of(0.0, 0.0)));
        hulls.put(firstChildHull, rectangle(70.0, -20.0, 110.0, 20.0, LayoutPoint.of(90.0, 0.0)));
        GraphProjection projection = GraphProjection.projected(1L, Collections.<ProjectedNode>emptyList(),
            Arrays.asList(firstRoot, secondRoot, firstChild), Collections.<ProjectedEdge>emptyList(),
            Collections.<RelationshipResolution>emptyList(), Collections.<PinProjection>emptyList());
        LayoutFrame layout = LayoutFrame.of(1L, LayoutPositions.of(Collections.<ProjectedNodeKey, LayoutPoint>emptyMap(),
            Collections.<EnclosureHullKey, LayoutPoint>emptyMap()), false);
        return CanvasState.of(1L, projection, layout,
            GraphGeometry.of(Collections.<ProjectedNodeKey, NodeGeometry>emptyMap(), hulls,
                Collections.<EnclosureKey, LabelPlacement>emptyMap()), OperationalStatus.IDLE);
    }

    private static LabelFixture labelFixture(final int nodeCount) {
        EnclosureKey ordinaryKey = EnclosureKey.of(source(FIRST_MAP, "ORDINARY"));
        EnclosureKey selectedKey = EnclosureKey.of(source(FIRST_MAP, "SELECTED"));
        EnclosureKey hoveredKey = EnclosureKey.of(source(FIRST_MAP, "HOVERED"));
        EnclosureKey searchMatchedKey = EnclosureKey.of(source(FIRST_MAP, "SEARCH"));
        EnclosureHullKey ordinaryHull = EnclosureHullKey.of(Collections.singletonList(ordinaryKey));
        EnclosureHullKey selectedHull = EnclosureHullKey.of(Collections.singletonList(selectedKey));
        EnclosureHullKey hoveredHull = EnclosureHullKey.of(Collections.singletonList(hoveredKey));
        EnclosureHullKey searchMatchedHull = EnclosureHullKey.of(Collections.singletonList(searchMatchedKey));
        ProjectedEnclosure ordinary = enclosure(ordinaryHull, ordinaryKey, "ORDINARY", "ORDINARY",
            BoundaryTier.SUBTLE);
        ProjectedEnclosure selected = enclosure(selectedHull, selectedKey, "SELECTED", "SELECTED",
            BoundaryTier.SUBTLE);
        ProjectedEnclosure hovered = enclosure(hoveredHull, hoveredKey, "HOVERED", "HOVERED",
            BoundaryTier.SUBTLE);
        ProjectedEnclosure searchMatched = enclosure(searchMatchedHull, searchMatchedKey, "SEARCH", "SEARCH",
            BoundaryTier.SUBTLE);
        List<ProjectedEnclosure> extras = new ArrayList<ProjectedEnclosure>(nodeCount);
        for (int index = 0; index < nodeCount; index++) {
            extras.add(groupBoundary("label-extra-" + index));
        }
        EnclosureKey emphaticKey = EnclosureKey.of(source(FIRST_MAP, "emphatic-label"));
        EnclosureKey subtleKey = EnclosureKey.of(source(SECOND_MAP, "subtle-label"));
        EnclosureKey suppressedKey = EnclosureKey.of(source(FIRST_MAP, "suppressed-label"));
        EnclosureHullKey emphaticHull = EnclosureHullKey.of(Collections.singletonList(emphaticKey));
        EnclosureHullKey subtleHull = EnclosureHullKey.of(Collections.singletonList(subtleKey));
        EnclosureHullKey suppressedHull = EnclosureHullKey.of(Collections.singletonList(suppressedKey));
        ProjectedEnclosure emphatic = enclosure(emphaticHull, emphaticKey, "EMPHATIC", "EMPHATIC",
            BoundaryTier.EMPHATIC);
        ProjectedEnclosure subtle = enclosure(subtleHull, subtleKey, "SUBTLE", "SUBTLE",
            BoundaryTier.SUBTLE);
        ProjectedEnclosure suppressed = enclosure(suppressedHull, suppressedKey, "SUPPRESSED", "SUPPRESSED",
            BoundaryTier.SUPPRESSED);
        Map<EnclosureHullKey, HullGeometry> hulls = new LinkedHashMap<EnclosureHullKey, HullGeometry>();
        hulls.put(ordinaryHull, rectangle(-95.0, -51.0, -75.0, -31.0, LayoutPoint.of(-85.0, -41.0)));
        hulls.put(selectedHull, rectangle(-10.0, -51.0, 10.0, -31.0, LayoutPoint.of(0.0, -41.0)));
        hulls.put(hoveredHull, rectangle(75.0, -51.0, 95.0, -31.0, LayoutPoint.of(85.0, -41.0)));
        hulls.put(searchMatchedHull, rectangle(-95.0, 89.0, -75.0, 109.0, LayoutPoint.of(-85.0, 99.0)));
        hulls.put(emphaticHull, rectangle(-20.0, 25.0, 20.0, 65.0, LayoutPoint.of(0.0, 45.0)));
        hulls.put(subtleHull, rectangle(65.0, 25.0, 105.0, 65.0, LayoutPoint.of(85.0, 45.0)));
        hulls.put(suppressedHull, rectangle(-90.0, -5.0, -55.0, 15.0, LayoutPoint.of(-72.5, 5.0)));
        Map<EnclosureKey, LabelPlacement> labels = new LinkedHashMap<EnclosureKey, LabelPlacement>();
        labels.put(ordinaryKey, LabelPlacement.of("ORDINARY", LabelPlacement.Mode.INTERIOR,
            LayoutPoint.of(-85.0, -41.0), 60.0, 10.0, Optional.<LayoutPoint>empty()));
        labels.put(selectedKey, LabelPlacement.of("SELECTED", LabelPlacement.Mode.INTERIOR,
            LayoutPoint.of(0.0, -41.0), 60.0, 10.0, Optional.<LayoutPoint>empty()));
        labels.put(hoveredKey, LabelPlacement.of("HOVERED", LabelPlacement.Mode.INTERIOR,
            LayoutPoint.of(85.0, -41.0), 60.0, 10.0, Optional.<LayoutPoint>empty()));
        labels.put(searchMatchedKey, LabelPlacement.of("SEARCH", LabelPlacement.Mode.INTERIOR,
            LayoutPoint.of(-85.0, 29.0), 45.0, 10.0, Optional.<LayoutPoint>empty()));
        labels.put(emphaticKey, LabelPlacement.of("EMPHATIC", LabelPlacement.Mode.INTERIOR,
            LayoutPoint.of(0.0, 45.0), 60.0, 10.0, Optional.<LayoutPoint>empty()));
        labels.put(subtleKey, LabelPlacement.of("SUBTLE", LabelPlacement.Mode.INTERIOR,
            LayoutPoint.of(85.0, 45.0), 45.0, 10.0, Optional.<LayoutPoint>empty()));
        labels.put(suppressedKey, LabelPlacement.of("SUPPRESSED", LabelPlacement.Mode.INTERIOR,
            LayoutPoint.of(-72.5, 5.0), 70.0, 10.0, Optional.<LayoutPoint>empty()));
        Map<EnclosureHullKey, LayoutPoint> anchors = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        anchors.put(ordinaryHull, LayoutPoint.of(-85.0, -41.0));
        anchors.put(selectedHull, LayoutPoint.of(0.0, -41.0));
        anchors.put(hoveredHull, LayoutPoint.of(85.0, -41.0));
        anchors.put(searchMatchedHull, LayoutPoint.of(-85.0, 29.0));
        anchors.put(emphaticHull, LayoutPoint.of(0.0, 45.0));
        anchors.put(subtleHull, LayoutPoint.of(85.0, 45.0));
        anchors.put(suppressedHull, LayoutPoint.of(-72.5, 5.0));
        List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>(
            Arrays.asList(ordinary, selected, hovered, searchMatched, emphatic, subtle, suppressed));
        enclosures.addAll(extras);
        GraphProjection projection = GraphProjection.projected(1L, Collections.<ProjectedNode>emptyList(),
            enclosures, Collections.<ProjectedEdge>emptyList(), Collections.<RelationshipResolution>emptyList(),
            Collections.<PinProjection>emptyList());
        CanvasState state = CanvasState.of(1L, projection,
            LayoutFrame.of(1L, LayoutPositions.of(Collections.<ProjectedNodeKey, LayoutPoint>emptyMap(), anchors),
                false),
            GraphGeometry.of(Collections.<ProjectedNodeKey, NodeGeometry>emptyMap(), hulls, labels),
            OperationalStatus.IDLE);
        return new LabelFixture(state, ProjectedEndpointKey.ofEnclosure(selectedKey),
            ProjectedEndpointKey.ofEnclosure(hoveredKey),
            ProjectedEndpointKey.ofEnclosure(searchMatchedKey),
            ProjectedEndpointKey.ofEnclosure(suppressedKey));
    }

    @Test
    public void paintBoundaryShapesRegardlessOfTheRetainedNodeRadius() {
        Fixture small = fixture(10.0);
        Fixture large = fixture(24.0);
        GraphTheme theme = lightTheme();

        BufferedImage smallImage = paint(small.state, GraphPaintState.empty(), theme, RenderingLevel.FULL);
        BufferedImage largeImage = paint(large.state, GraphPaintState.empty(), theme, RenderingLevel.FULL);

        assertThat(colorPixelsIn(largeImage, theme.nodeFill(), 0, 0, SIZE.width, SIZE.height)).isZero();
        assertThat(colorPixelsIn(smallImage, theme.nodeFill(), 0, 0, SIZE.width, SIZE.height)).isZero();
        assertThat(colorPixelsIn(largeImage, theme.hullFill(FIRST_MAP, BoundaryTier.EMPHATIC), 60, 40, 90, 100))
            .isGreaterThan(0);
        assertThat(large.state.geometry().nodes().get(large.first.key()).radius()).isEqualTo(24.0);
    }

    @Test
    public void keepPaintStateImmutableAndLimitSelectionToHighlightLayers() {
        Fixture fixture = fixture(16.0);
        Set<ProjectedEndpointKey> mutableMatches = new LinkedHashSet<ProjectedEndpointKey>();
        mutableMatches.add(fixture.firstHullEndpoint);
        GraphPaintState state = GraphPaintState.empty()
            .withSelection(fixture.firstHullEndpoint)
            .withHover(fixture.secondHullEndpoint)
            .withSearchMatches(mutableMatches);
        mutableMatches.clear();

        assertThat(state.selection()).contains(fixture.firstHullEndpoint);
        assertThat(state.hover()).contains(fixture.secondHullEndpoint);
        assertThat(state.searchMatches()).containsExactly(fixture.firstHullEndpoint);
        assertThatThrownBy(() -> state.searchMatches().add(fixture.secondHullEndpoint))
            .isInstanceOf(UnsupportedOperationException.class);

        GraphTheme theme = lightTheme();
        BufferedImage base = paint(fixture.state, GraphPaintState.empty(), theme, RenderingLevel.FULL);
        BufferedImage highlighted = paint(fixture.state, state, theme, RenderingLevel.FULL);
        assertThat(differentPixels(base, highlighted)).isGreaterThan(0);
        assertThat(fixture.state.geometry().nodes().get(fixture.first.key()).radius()).isEqualTo(16.0);
    }

    @Test
    public void resolveReadableDistinctLightAndDarkThemesWithoutChangingSwingDefaults() {
        GraphCanvas lightCanvas = new GraphCanvas();
        lightCanvas.setTheme(lightTheme());
        GraphCanvas darkCanvas = new GraphCanvas();
        darkCanvas.setTheme(GraphTheme.resolve(CanvasTheme.DARK, registeredMaps()));
        Fixture fixture = fixture(16.0);

        BufferedImage light = paint(fixture.state, GraphPaintState.empty(), lightCanvas.theme(),
            RenderingLevel.FULL);
        BufferedImage dark = paint(fixture.state, GraphPaintState.empty(), darkCanvas.theme(),
            RenderingLevel.FULL);

        assertThat(light.getRGB(0, 0)).isNotEqualTo(dark.getRGB(0, 0));
        assertThat(lightCanvas.theme().background()).isNotEqualTo(lightCanvas.theme().labelColor());
        assertThat(darkCanvas.theme().background()).isNotEqualTo(darkCanvas.theme().labelColor());
        assertThat(nonBackgroundPixels(light, lightCanvas.theme().background())).isGreaterThan(100);
        assertThat(nonBackgroundPixels(dark, darkCanvas.theme().background())).isGreaterThan(100);
    }

    @Test
    public void fitFiniteViewportAndResetToUnitOrigin() {
        Fixture fixture = fixture(16.0);
        GraphCanvas canvas = new GraphCanvas();
        canvas.setSize(SIZE);
        canvas.setTheme(lightTheme());
        canvas.setCanvasState(fixture.state);
        canvas.setViewport(GraphViewport.of(100000.0, 100000.0, 1.0));

        assertThat(canvas.viewport().overlaps(-70.0, -25.0, 70.0, 25.0, SIZE)).isFalse();
        canvas.fitGraph();
        assertThat(canvas.viewport().overlaps(-70.0, -25.0, 70.0, 25.0, SIZE)).isTrue();

        canvas.resetZoom();
        assertThat(canvas.viewport().centerX()).isEqualTo(0.0);
        assertThat(canvas.viewport().centerY()).isEqualTo(0.0);
        assertThat(canvas.viewport().zoom()).isEqualTo(1.0);
    }

    @Test
    public void translatePhysicalScrollIntoVisibleWorldViewportWithoutMovingTheRenderingAnchor() {
        Fixture fixture = fixture(16.0);
        GraphCanvas canvas = new GraphCanvas();
        canvas.setCanvasState(fixture.state);
        canvas.setViewport(GraphViewport.of(12.0, -8.0, 2.0));
        canvas.setPreferredSize(new Dimension(1200, 900));
        canvas.setSize(new Dimension(1200, 900));
        JScrollPane scrollPane = new JScrollPane(canvas);
        scrollPane.setSize(new Dimension(320, 220));
        scrollPane.doLayout();
        final javax.swing.JViewport scrollViewport = scrollPane.getViewport();
        scrollViewport.setViewPosition(new Point(100, 100));
        GraphViewport before = canvas.visibleViewport();
        GraphViewport anchor = canvas.viewport();
        final int delta = 40;

        scrollViewport.setViewPosition(new Point(100 + delta, 100));

        GraphViewport after = canvas.visibleViewport();
        assertThat(after.centerX() - before.centerX()).isCloseTo(delta / anchor.zoom(),
            org.assertj.core.data.Offset.offset(0.000001));
        assertThat(after.centerY()).isEqualTo(before.centerY());
        assertThat(canvas.viewport()).isEqualTo(anchor);
    }

    @Test
    public void preserveVisibleWorldCenterWhenTheSurfaceGrows() {
        Fixture fixture = fixture(16.0);
        GraphCanvas canvas = new GraphCanvas();
        canvas.setPreferredSize(new Dimension(800, 560));
        canvas.setSize(new Dimension(800, 560));
        JScrollPane scrollPane = new JScrollPane(canvas);
        scrollPane.setSize(new Dimension(320, 220));
        scrollPane.doLayout();
        final javax.swing.JViewport scrollViewport = scrollPane.getViewport();
        scrollViewport.setViewPosition(new Point(120, 100));
        canvas.setViewport(GraphViewport.of(0.0, 0.0, 1.0));
        GraphViewport before = canvas.visibleViewport();

        canvas.setCanvasState(stateWithWideGeometry(fixture));

        GraphViewport after = canvas.visibleViewport();
        assertThat(after.centerX()).isCloseTo(before.centerX(),
            org.assertj.core.data.Offset.offset(0.000001));
        assertThat(after.centerY()).isCloseTo(before.centerY(),
            org.assertj.core.data.Offset.offset(0.000001));
    }

    @Test
    public void keepBothWorldExtremaScrollableAfterOffCenterZoom() {
        Fixture fixture = fixture(16.0);
        GraphCanvas canvas = new GraphCanvas();
        canvas.setCanvasState(stateWithWideGeometry(fixture));
        canvas.setViewport(GraphViewport.of(0.0, 0.0, 1.0));
        JScrollPane scrollPane = new JScrollPane(canvas);
        scrollPane.setSize(new Dimension(320, 220));
        scrollPane.doLayout();
        javax.swing.JViewport scrollViewport = scrollPane.getViewport();
        int initialMaxX = Math.max(0, canvas.getWidth() - scrollViewport.getExtentSize().width);
        scrollViewport.setViewPosition(new Point(initialMaxX, 0));
        GraphViewport offCenter = canvas.visibleViewport();
        assertThat(offCenter.centerX()).isGreaterThan(0.0);

        canvas.setViewport(GraphViewport.of(offCenter.centerX(), offCenter.centerY(), 2.0));

        Dimension surface = canvas.getSize();
        double marginPixels = 80.0 * canvas.viewport().zoom();
        Point2D left = canvas.viewport().toScreen(LayoutPoint.of(-510.0, 0.0), surface);
        Point2D right = canvas.viewport().toScreen(LayoutPoint.of(510.0, 0.0), surface);
        assertThat(left.getX()).isGreaterThanOrEqualTo(marginPixels);
        assertThat(right.getX()).isLessThanOrEqualTo(surface.getWidth() - marginPixels);

        scrollViewport.setViewPosition(new Point(0, 0));
        assertThat(left.getX()).isLessThan(scrollViewport.getExtentSize().getWidth());
        int finalMaxX = Math.max(0, canvas.getWidth() - scrollViewport.getExtentSize().width);
        scrollViewport.setViewPosition(new Point(finalMaxX, 0));
        assertThat(right.getX() - finalMaxX).isLessThan(scrollViewport.getExtentSize().getWidth());
    }

    @Test
    public void keepBothWorldExtremaReachableAfterOffCenterUserPan() {
        Fixture fixture = fixture(16.0);
        GraphCanvas canvas = new GraphCanvas();
        canvas.setCanvasState(stateWithWideGeometry(fixture));
        canvas.setViewport(GraphViewport.of(0.0, 0.0, 1.0));
        canvas.setSize(canvas.getPreferredSize());
        JScrollPane scrollPane = new JScrollPane(canvas);
        scrollPane.setSize(new Dimension(320, 220));
        scrollPane.doLayout();
        JViewport scrollViewport = scrollPane.getViewport();
        int initialMaximumX = Math.max(0, canvas.getWidth() - scrollViewport.getExtentSize().width);
        scrollViewport.setViewPosition(new Point(initialMaximumX * 3 / 4, 0));
        GraphViewport before = canvas.visibleViewport();

        canvas.panByPixels(-700.0, 0.0);

        GraphViewport after = canvas.visibleViewport();
        assertThat(after.centerX() - before.centerX()).isCloseTo(700.0,
            org.assertj.core.data.Offset.offset(0.000001));
        Dimension surface = canvas.getSize();
        double marginPixels = 80.0 * canvas.viewport().zoom();
        Point2D left = canvas.viewport().toScreen(LayoutPoint.of(-510.0, 0.0), surface);
        Point2D right = canvas.viewport().toScreen(LayoutPoint.of(510.0, 0.0), surface);
        assertThat(left.getX()).isGreaterThanOrEqualTo(marginPixels);
        assertThat(right.getX()).isLessThanOrEqualTo(surface.getWidth() - marginPixels);
        assertHorizontallyReachable(scrollViewport, left.getX());
        assertHorizontallyReachable(scrollViewport, right.getX());
    }

    @Test
    public void deferSameGenerationSurfaceContractionUntilIdle() {
        Fixture fixture = fixture(16.0);
        GraphCanvas canvas = new GraphCanvas();
        CanvasState expandedSettling = stateWithGeometry(fixture, 500.0, 400.0,
            OperationalStatus.SETTLING);
        CanvasState contractedSettling = stateWithGeometry(fixture, 100.0, 100.0,
            OperationalStatus.SETTLING);
        CanvasState contractedIdle = stateWithGeometry(fixture, 100.0, 100.0,
            OperationalStatus.IDLE);

        canvas.setCanvasState(expandedSettling);
        Dimension expandedSurface = canvas.getPreferredSize();
        assertThat(expandedSurface.width).isGreaterThan(800);
        assertThat(expandedSurface.height).isGreaterThan(560);

        canvas.setCanvasState(contractedSettling);
        assertThat(canvas.getPreferredSize()).isEqualTo(expandedSurface);

        canvas.setCanvasState(contractedIdle);
        assertThat(canvas.getPreferredSize().width).isLessThan(expandedSurface.width);
        assertThat(canvas.getPreferredSize().height).isLessThan(expandedSurface.height);
    }

    @Test
    public void fitGraphUsesTheScrollableViewportExtentInsteadOfTheFullSurface() {
        Fixture fixture = fixture(16.0);
        GraphCanvas canvas = new GraphCanvas();
        canvas.setCanvasState(fixture.state);
        canvas.setPreferredSize(new Dimension(1200, 900));
        JScrollPane scrollPane = new JScrollPane(canvas);
        scrollPane.setSize(SIZE);
        scrollPane.doLayout();
        Dimension extent = scrollPane.getViewport().getExtentSize();
        canvas.fitGraph();

        double expected = Math.min(extent.getWidth() * 0.8 / 140.0,
            extent.getHeight() * 0.8 / 50.0);
        assertThat(canvas.viewport().zoom()).isCloseTo(expected,
            org.assertj.core.data.Offset.offset(0.000001));
        assertThat(canvas.viewport().zoom()).isLessThan(1200.0 * 0.8 / 140.0);
    }

    @Test
    public void continuePaintingSelectedContentWhenAboveTarget() {
        Fixture fixture = fixture(16.0);
        List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        nodes.add(fixture.first);
        for (int index = 0; index < 2000; index++) {
            nodes.add(node(FIRST_MAP, "extra-" + index, LayoutPoint.of(500.0 + index, 500.0)));
        }
        GraphProjection projection = GraphProjection.projected(1L, nodes,
            fixture.state.projection().enclosures(), Collections.<ProjectedEdge>emptyList(),
            Collections.<RelationshipResolution>emptyList(), Collections.<PinProjection>emptyList());
        Map<ProjectedNodeKey, NodeGeometry> geometries = new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
        geometries.put(fixture.first.key(), fixture.state.geometry().nodes().get(fixture.first.key()));
        CanvasState state = CanvasState.of(1L, projection,
            LayoutFrame.of(1L, LayoutPositions.of(fixture.state.layout().positions().nodes(),
                fixture.state.layout().positions().anchors()), false),
            GraphGeometry.of(geometries, fixture.state.geometry().hulls(),
                fixture.state.geometry().labels()), OperationalStatus.IDLE);
        GraphCanvas canvas = new GraphCanvas();
        canvas.setSize(SIZE);
        canvas.setTheme(lightTheme());
        canvas.setCanvasState(state);
        canvas.setPaintState(GraphPaintState.empty().withSelection(fixture.firstHullEndpoint));
        canvas.setViewport(GraphViewport.of(0.0, 0.0, 1.0));
        BufferedImage image = paintCanvas(canvas);

        assertThat(canvas.isEnabled()).isTrue();
        assertThat(nonBackgroundPixels(image, canvas.theme().background())).isGreaterThan(0);
        assertThat(hasInkNear(image, new Point2D.Double(100.0, 70.0), canvas.theme().selectionColor(), 6))
            .isTrue();
    }

    private static CanvasState stateWithWideGeometry(final Fixture fixture) {
        return stateWithGeometry(fixture, 500.0, 0.0, fixture.state.status());
    }

    private static CanvasState stateWithGeometry(final Fixture fixture, final double horizontalCoordinate,
            final double verticalCoordinate, final OperationalStatus status) {
        Map<ProjectedNodeKey, NodeGeometry> nodes = new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
        nodes.put(fixture.first.key(), NodeGeometry.of(
            LayoutPoint.of(-horizontalCoordinate, -verticalCoordinate), 10.0));
        nodes.put(fixture.second.key(), NodeGeometry.of(
            LayoutPoint.of(horizontalCoordinate, verticalCoordinate), 10.0));
        return CanvasState.of(fixture.state.generation(), fixture.state.projection(), fixture.state.layout(),
            GraphGeometry.of(nodes, fixture.state.geometry().hulls(), fixture.state.geometry().labels()),
            status);
    }

    private static void assertHorizontallyReachable(final JViewport viewport, final double surfaceX) {
        int extentWidth = viewport.getExtentSize().width;
        int maximumX = Math.max(0, viewport.getView().getWidth() - extentWidth);
        int centeredX = (int) Math.round(surfaceX - extentWidth * 0.5);
        int viewX = Math.max(0, Math.min(maximumX, centeredX));
        viewport.setViewPosition(new Point(viewX, viewport.getViewPosition().y));
        double visibleX = surfaceX - viewport.getViewPosition().x;
        assertThat(visibleX).isBetween(0.0, (double) extentWidth);
    }

    private static BufferedImage paint(final CanvasState state, final GraphPaintState paintState,
            final GraphTheme theme, final RenderingLevel level) {
        GraphCanvas canvas = new GraphCanvas();
        canvas.setSize(SIZE);
        canvas.setTheme(theme);
        canvas.setCanvasState(state);
        canvas.setPaintState(paintState);
        canvas.setViewport(GraphViewport.of(0.0, 0.0, 1.0));
        return paintCanvas(canvas);
    }

    private static BufferedImage paintCanvas(final GraphCanvas canvas) {
        BufferedImage image = new BufferedImage(SIZE.width, SIZE.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            canvas.paint(graphics);
        }
        finally {
            graphics.dispose();
        }
        return image;
    }

    private static Fixture fixture(final double firstRadius) {
        ProjectedNode first = node(FIRST_MAP, "first", LayoutPoint.of(-45.0, 0.0));
        ProjectedNode second = node(SECOND_MAP, "second", LayoutPoint.of(45.0, 0.0));
        ProjectedEndpointKey firstEndpoint = ProjectedEndpointKey.ofNode(first.key());
        ProjectedEndpointKey secondEndpoint = ProjectedEndpointKey.ofNode(second.key());
        ProjectedEdgeKey edgeKey = ProjectedEdgeKey.of(firstEndpoint, secondEndpoint);
        List<EdgeContributor> contributors = Arrays.asList(
            relationshipContributor("00000000-0000-0000-0000-000000000011", 1L,
                firstEndpoint, secondEndpoint),
            relationshipContributor("00000000-0000-0000-0000-000000000012", 2L,
                firstEndpoint, secondEndpoint));
        ProjectedEdge edge = ProjectedEdge.of(edgeKey, contributors);

        EnclosureKey firstEnclosureKey = EnclosureKey.of(source(FIRST_MAP, "first-hull"));
        EnclosureKey secondEnclosureKey = EnclosureKey.of(source(SECOND_MAP, "second-hull"));
        EnclosureHullKey firstHullKey = EnclosureHullKey.of(Collections.singletonList(firstEnclosureKey));
        EnclosureHullKey secondHullKey = EnclosureHullKey.of(Collections.singletonList(secondEnclosureKey));
        ProjectedEnclosure firstEnclosure = enclosure(firstHullKey, firstEnclosureKey,
            "Emphatic hull", "Emphatic", BoundaryTier.EMPHATIC);
        ProjectedEnclosure secondEnclosure = enclosure(secondHullKey, secondEnclosureKey,
            "Subtle hull", "Subtle", BoundaryTier.SUBTLE);

        Map<ProjectedNodeKey, NodeGeometry> nodes = new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
        nodes.put(first.key(), NodeGeometry.of(firstLabelAnchor(first), firstRadius));
        nodes.put(second.key(), NodeGeometry.of(secondLabelAnchor(second), 10.0));
        Map<EnclosureHullKey, HullGeometry> hulls = new LinkedHashMap<EnclosureHullKey, HullGeometry>();
        hulls.put(firstHullKey, rectangle(-70.0, -25.0, -20.0, 25.0, LayoutPoint.of(-45.0, 14.0)));
        hulls.put(secondHullKey, rectangle(20.0, -20.0, 70.0, 20.0, LayoutPoint.of(45.0, 12.0)));
        Map<EnclosureKey, LabelPlacement> labels = new LinkedHashMap<EnclosureKey, LabelPlacement>();
        labels.put(firstEnclosureKey, LabelPlacement.of("Emphatic", LabelPlacement.Mode.INTERIOR,
            LayoutPoint.of(-45.0, 14.0), 34.0, 8.0, Optional.<LayoutPoint>empty()));
        labels.put(secondEnclosureKey, LabelPlacement.of("Subtle", LabelPlacement.Mode.INTERIOR,
            LayoutPoint.of(45.0, 12.0), 26.0, 8.0, Optional.<LayoutPoint>empty()));
        GraphGeometry geometry = GraphGeometry.of(nodes, hulls, labels);
        GraphProjection projection = GraphProjection.projected(1L, Arrays.asList(first, second),
            Arrays.asList(firstEnclosure, secondEnclosure), Collections.singletonList(edge),
            Collections.<RelationshipResolution>emptyList(), Collections.<PinProjection>emptyList());
        Map<ProjectedNodeKey, LayoutPoint> positions = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        positions.put(first.key(), firstLabelAnchor(first));
        positions.put(second.key(), secondLabelAnchor(second));
        Map<EnclosureHullKey, LayoutPoint> anchors = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        anchors.put(firstHullKey, LayoutPoint.of(-45.0, 14.0));
        anchors.put(secondHullKey, LayoutPoint.of(45.0, 12.0));
        LayoutFrame layout = LayoutFrame.of(1L, LayoutPositions.of(positions, anchors), false);
        CanvasState state = CanvasState.of(1L, projection, layout, geometry, OperationalStatus.IDLE);
        return new Fixture(state, first, second, edge, firstEndpoint, secondEndpoint, firstHullKey,
            secondHullKey, ProjectedEndpointKey.ofEnclosure(firstEnclosureKey),
            ProjectedEndpointKey.ofEnclosure(secondEnclosureKey));
    }

    private static ProjectedEnclosure enclosure(final EnclosureHullKey hullKey, final EnclosureKey endpoint,
            final String fullLabel, final String displayLabel, final BoundaryTier tier) {
        return ProjectedEnclosure.of(hullKey, Collections.singletonList(endpoint),
            Collections.singletonList(SafeNodeLabel.of(fullLabel, displayLabel)), "Map",
            Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(),
            Collections.<EnclosureHullKey>emptyList(), true, tier);
    }

    private static ProjectedEnclosure groupBoundary(final String id) {
        EnclosureKey endpoint = EnclosureKey.of(source(FIRST_MAP, id));
        EnclosureHullKey hullKey = EnclosureHullKey.of(Collections.singletonList(endpoint));
        return ProjectedEnclosure.of(hullKey, Collections.singletonList(endpoint),
            Collections.singletonList(SafeNodeLabel.of(id, id)), "Map",
            Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(),
            Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.EMPHATIC);
    }

    private static ProjectedNode node(final MapReferenceId map, final String id, final LayoutPoint center) {
        SourceNodeKey source = source(map, id);
        return ProjectedNode.of(ProjectedNodeKey.of(source), SafeNodeLabel.of(id, id), "Map", false);
    }

    private static EdgeContributor relationshipContributor(final String relationshipId, final long sequence,
            final ProjectedEndpointKey source, final ProjectedEndpointKey target) {
        GraphRelationshipRecord record = GraphRelationshipRecord.of(RelationshipId.of(relationshipId), sequence,
            reference(FIRST_MAP, "first"), reference(SECOND_MAP, "second"), RelationshipDirection.FORWARD,
            Collections.<UnknownXml>emptyList());
        return EdgeContributor.graphRelationship(record, source, target);
    }

    private static EdgeContributor undirectedRelationshipContributor(final String relationshipId,
            final long sequence, final ProjectedEndpointKey source, final ProjectedEndpointKey target) {
        GraphRelationshipRecord record = GraphRelationshipRecord.of(RelationshipId.of(relationshipId), sequence,
            reference(FIRST_MAP, "first"), reference(SECOND_MAP, "second"), RelationshipDirection.UNDIRECTED,
            Collections.<UnknownXml>emptyList());
        return EdgeContributor.graphRelationship(record, source, target);
    }

    private static SourceNodeKey source(final MapReferenceId map, final String id) {
        return SourceNodeKey.persisted(reference(map, id));
    }

    private static NodeReference reference(final MapReferenceId map, final String id) {
        return NodeReference.of(map, PersistedNodeId.of(id));
    }

    private static LayoutPoint firstLabelAnchor(final ProjectedNode node) {
        return LayoutPoint.of(-45.0, 0.0);
    }

    private static LayoutPoint secondLabelAnchor(final ProjectedNode node) {
        return LayoutPoint.of(45.0, 0.0);
    }

    private static HullGeometry rectangle(final double minX, final double minY, final double maxX,
            final double maxY, final LayoutPoint labelAnchor) {
        return HullGeometry.of(Arrays.asList(LayoutPoint.of(minX, minY), LayoutPoint.of(maxX, minY),
            LayoutPoint.of(maxX, maxY), LayoutPoint.of(minX, maxY)), labelAnchor);
    }

    private static boolean allPixelsAreOpaque(final BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0xff) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int nonBackgroundPixels(final BufferedImage image, final Color background) {
        return nonBackgroundPixelsIn(image, background, 0, 0, image.getWidth(), image.getHeight());
    }

    private static int nonBackgroundPixelsIn(final BufferedImage image, final Color background,
            final int minX, final int minY, final int maxX, final int maxY) {
        int count = 0;
        for (int y = Math.max(0, minY); y < Math.min(image.getHeight(), maxY); y++) {
            for (int x = Math.max(0, minX); x < Math.min(image.getWidth(), maxX); x++) {
                if (image.getRGB(x, y) != background.getRGB()) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int colorPixelsIn(final BufferedImage image, final Color color,
            final int minX, final int minY, final int maxX, final int maxY) {
        int count = 0;
        for (int y = Math.max(0, minY); y < Math.min(image.getHeight(), maxY); y++) {
            for (int x = Math.max(0, minX); x < Math.min(image.getWidth(), maxX); x++) {
                if (image.getRGB(x, y) == color.getRGB()) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int nearColorPixelsIn(final BufferedImage image, final Color color,
            final int minX, final int minY, final int maxX, final int maxY, final int maximumDistance) {
        int count = 0;
        final int maximumDistanceSquared = maximumDistance * maximumDistance;
        for (int y = Math.max(0, minY); y < Math.min(image.getHeight(), maxY); y++) {
            for (int x = Math.max(0, minX); x < Math.min(image.getWidth(), maxX); x++) {
                final int pixel = image.getRGB(x, y);
                final int red = ((pixel >>> 16) & 0xff) - color.getRed();
                final int green = ((pixel >>> 8) & 0xff) - color.getGreen();
                final int blue = (pixel & 0xff) - color.getBlue();
                if (red * red + green * green + blue * blue <= maximumDistanceSquared) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int differentPixels(final BufferedImage first, final BufferedImage second) {
        int count = 0;
        for (int y = 0; y < first.getHeight(); y++) {
            for (int x = 0; x < first.getWidth(); x++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean hasColorNear(final BufferedImage image, final Point2D point, final Color color,
            final int radius) {
        for (int y = (int) point.getY() - radius; y <= (int) point.getY() + radius; y++) {
            for (int x = (int) point.getX() - radius; x <= (int) point.getX() + radius; x++) {
                if (x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight()
                        && image.getRGB(x, y) == color.getRGB()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasInkNear(final BufferedImage image, final Point2D point, final Color background,
            final int radius) {
        return nonBackgroundPixelsIn(image, background, (int) point.getX() - radius,
            (int) point.getY() - radius, (int) point.getX() + radius + 1,
            (int) point.getY() + radius + 1) > 0;
    }

    private static final class LabelFixture {
        private final CanvasState state;
        private final ProjectedEndpointKey selected;
        private final ProjectedEndpointKey hovered;
        private final ProjectedEndpointKey searchMatched;
        private final ProjectedEndpointKey suppressed;

        private LabelFixture(final CanvasState state, final ProjectedEndpointKey selected,
                final ProjectedEndpointKey hovered, final ProjectedEndpointKey searchMatched,
                final ProjectedEndpointKey suppressed) {
            this.state = state;
            this.selected = selected;
            this.hovered = hovered;
            this.searchMatched = searchMatched;
            this.suppressed = suppressed;
        }
    }

    private static final class Fixture {
        private final CanvasState state;
        private final ProjectedNode first;
        private final ProjectedNode second;
        private final ProjectedEdge edge;
        private final ProjectedEndpointKey firstEndpoint;
        private final ProjectedEndpointKey secondEndpoint;
        private final EnclosureHullKey firstHullKey;
        private final EnclosureHullKey secondHullKey;
        private final ProjectedEndpointKey firstHullEndpoint;
        private final ProjectedEndpointKey secondHullEndpoint;

        private Fixture(final CanvasState state, final ProjectedNode first, final ProjectedNode second,
                final ProjectedEdge edge, final ProjectedEndpointKey firstEndpoint,
                final ProjectedEndpointKey secondEndpoint, final EnclosureHullKey firstHullKey,
                final EnclosureHullKey secondHullKey, final ProjectedEndpointKey firstHullEndpoint,
                final ProjectedEndpointKey secondHullEndpoint) {
            this.state = state;
            this.first = first;
            this.second = second;
            this.edge = edge;
            this.firstEndpoint = firstEndpoint;
            this.secondEndpoint = secondEndpoint;
            this.firstHullKey = firstHullKey;
            this.secondHullKey = secondHullKey;
            this.firstHullEndpoint = firstHullEndpoint;
            this.secondHullEndpoint = secondHullEndpoint;
        }
    }
}
