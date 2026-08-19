package org.freeplane.plugin.graph.canvas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
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

import org.freeplane.plugin.graph.control.CanvasState;
import org.freeplane.plugin.graph.control.OperationalStatus;
import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.HullGeometry;
import org.freeplane.plugin.graph.geometry.LabelPlacement;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.geometry.NodeGeometry;
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
import org.junit.Test;

public class GraphCanvasPaintShould {
    private static final MapReferenceId FIRST_MAP =
        MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    private static final MapReferenceId SECOND_MAP =
        MapReferenceId.of("00000000-0000-0000-0000-000000000007");
    private static final MapReferenceId UNREGISTERED_MAP =
        MapReferenceId.of("00000000-0000-0000-0000-000000000099");
    private static final Dimension SIZE = new Dimension(240, 140);

    @Test
    public void paintOpaqueLayeredProjectionUsingGeometryAndProminence() {
        Fixture fixture = fixture(16.0);
        GraphTheme theme = lightTheme();
        BufferedImage image = paint(fixture.state, GraphPaintState.empty(), theme, RenderingLevel.FULL);

        assertThat(allPixelsAreOpaque(image)).isTrue();
        assertThat(nonBackgroundPixels(image, theme.background())).isGreaterThan(100);
        assertThat(image.getRGB(120, 70)).isEqualTo(theme.edgeColor().getRGB());
        assertThat(image.getRGB(75, 70)).isEqualTo(theme.nodeFill().getRGB());
        assertThat(image.getRGB(55, 70)).isNotEqualTo(theme.background().getRGB());
        assertThat(image.getRGB(185, 70)).isNotEqualTo(theme.background().getRGB());

        LayoutPoint attachment = fixture.state.geometry().edgeAttachment(fixture.edge.first(),
            fixture.state.geometry().nodes().get(fixture.first.key()).center());
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
        assertThat(withEdge.projection().prominence().get(fixture.selected.node().get())
            .visibleOutgoingTargets()).isZero();
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
    public void paintOnlyActivePins() {
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

        assertThat(colorPixelsIn(image, theme.pinColor(), 104, 24, 117, 37)).isGreaterThan(0);
        assertThat(colorPixelsIn(image, theme.pinColor(), 174, 24, 187, 37)).isEqualTo(0);
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
        assertThat(colorPixelsIn(painted, theme.pinColor(), 104, 24, 117, 37)).isZero();
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
        ProjectedNode ordinary = node(FIRST_MAP, "ORDINARY", LayoutPoint.of(-85.0, -25.0));
        ProjectedNode selected = node(FIRST_MAP, "SELECTED", LayoutPoint.of(0.0, -25.0));
        ProjectedNode hovered = node(FIRST_MAP, "HOVERED", LayoutPoint.of(85.0, -25.0));
        ProjectedNode searchMatched = node(FIRST_MAP, "SEARCH", LayoutPoint.of(-85.0, 45.0));
        List<ProjectedNode> nodes = new ArrayList<ProjectedNode>(nodeCount);
        nodes.add(ordinary);
        nodes.add(selected);
        nodes.add(hovered);
        nodes.add(searchMatched);
        for (int index = nodes.size(); index < nodeCount; index++) {
            nodes.add(node(FIRST_MAP, "label-extra-" + index, LayoutPoint.of(500.0 + index, 500.0)));
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
        Map<ProjectedNodeKey, NodeGeometry> geometries = new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
        geometries.put(ordinary.key(), NodeGeometry.of(LayoutPoint.of(-85.0, -25.0), 8.0));
        geometries.put(selected.key(), NodeGeometry.of(LayoutPoint.of(0.0, -25.0), 8.0));
        geometries.put(hovered.key(), NodeGeometry.of(LayoutPoint.of(85.0, -25.0), 8.0));
        geometries.put(searchMatched.key(), NodeGeometry.of(LayoutPoint.of(-85.0, 45.0), 8.0));
        Map<EnclosureHullKey, HullGeometry> hulls = new LinkedHashMap<EnclosureHullKey, HullGeometry>();
        hulls.put(emphaticHull, rectangle(-20.0, 25.0, 20.0, 65.0, LayoutPoint.of(0.0, 45.0)));
        hulls.put(subtleHull, rectangle(65.0, 25.0, 105.0, 65.0, LayoutPoint.of(85.0, 45.0)));
        hulls.put(suppressedHull, rectangle(-90.0, -5.0, -55.0, 15.0, LayoutPoint.of(-72.5, 5.0)));
        Map<EnclosureKey, LabelPlacement> labels = new LinkedHashMap<EnclosureKey, LabelPlacement>();
        labels.put(emphaticKey, LabelPlacement.of("EMPHATIC", LabelPlacement.Mode.INTERIOR,
            LayoutPoint.of(0.0, 45.0), 60.0, 10.0, Optional.<LayoutPoint>empty()));
        labels.put(subtleKey, LabelPlacement.of("SUBTLE", LabelPlacement.Mode.INTERIOR,
            LayoutPoint.of(85.0, 45.0), 45.0, 10.0, Optional.<LayoutPoint>empty()));
        labels.put(suppressedKey, LabelPlacement.of("SUPPRESSED", LabelPlacement.Mode.INTERIOR,
            LayoutPoint.of(-72.5, 5.0), 70.0, 10.0, Optional.<LayoutPoint>empty()));
        Map<ProjectedNodeKey, LayoutPoint> positions = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        positions.put(ordinary.key(), LayoutPoint.of(-85.0, -25.0));
        positions.put(selected.key(), LayoutPoint.of(0.0, -25.0));
        positions.put(hovered.key(), LayoutPoint.of(85.0, -25.0));
        positions.put(searchMatched.key(), LayoutPoint.of(-85.0, 45.0));
        Map<EnclosureHullKey, LayoutPoint> anchors = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        anchors.put(emphaticHull, LayoutPoint.of(0.0, 45.0));
        anchors.put(subtleHull, LayoutPoint.of(85.0, 45.0));
        anchors.put(suppressedHull, LayoutPoint.of(-72.5, 5.0));
        GraphProjection projection = GraphProjection.projected(1L, nodes, Arrays.asList(emphatic, subtle, suppressed),
            Collections.<ProjectedEdge>emptyList(), Collections.<RelationshipResolution>emptyList(),
            Collections.<PinProjection>emptyList());
        CanvasState state = CanvasState.of(1L, projection,
            LayoutFrame.of(1L, LayoutPositions.of(positions, anchors), false),
            GraphGeometry.of(geometries, hulls, labels), OperationalStatus.IDLE);
        return new LabelFixture(state, ProjectedEndpointKey.ofNode(selected.key()),
            ProjectedEndpointKey.ofNode(hovered.key()), ProjectedEndpointKey.ofNode(searchMatched.key()),
            ProjectedEndpointKey.ofEnclosure(suppressedKey));
    }

    @Test
    public void useTheSuppliedNodeRadiusWithoutChangingGeometry() {
        Fixture small = fixture(10.0);
        Fixture large = fixture(24.0);
        GraphTheme theme = lightTheme();

        BufferedImage smallImage = paint(small.state, GraphPaintState.empty(), theme, RenderingLevel.FULL);
        BufferedImage largeImage = paint(large.state, GraphPaintState.empty(), theme, RenderingLevel.FULL);

        assertThat(colorPixelsIn(largeImage, theme.nodeFill(), 60, 40, 90, 100))
            .isGreaterThan(colorPixelsIn(smallImage, theme.nodeFill(), 60, 40, 90, 100));
        assertThat(large.state.geometry().nodes().get(large.first.key()).radius()).isEqualTo(24.0);
    }

    @Test
    public void keepPaintStateImmutableAndLimitSelectionToHighlightLayers() {
        Fixture fixture = fixture(16.0);
        Set<ProjectedEndpointKey> mutableMatches = new LinkedHashSet<ProjectedEndpointKey>();
        mutableMatches.add(fixture.firstEndpoint);
        GraphPaintState state = GraphPaintState.empty()
            .withSelection(fixture.firstEndpoint)
            .withHover(fixture.secondEndpoint)
            .withSearchMatches(mutableMatches);
        mutableMatches.clear();

        assertThat(state.selection()).contains(fixture.firstEndpoint);
        assertThat(state.hover()).contains(fixture.secondEndpoint);
        assertThat(state.searchMatches()).containsExactly(fixture.firstEndpoint);
        assertThatThrownBy(() -> state.searchMatches().add(fixture.secondEndpoint))
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
    public void continuePaintingSelectedContentWhenAboveTarget() {
        Fixture fixture = fixture(16.0);
        List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        nodes.add(fixture.first);
        for (int index = 0; index < 2000; index++) {
            nodes.add(node(FIRST_MAP, "extra-" + index, LayoutPoint.of(500.0 + index, 500.0)));
        }
        GraphProjection projection = GraphProjection.projected(1L, nodes,
            Collections.<ProjectedEnclosure>emptyList(), Collections.<ProjectedEdge>emptyList(),
            Collections.<RelationshipResolution>emptyList(), Collections.<PinProjection>emptyList());
        Map<ProjectedNodeKey, NodeGeometry> geometries = new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
        geometries.put(fixture.first.key(), NodeGeometry.of(fixture.state.geometry().nodes()
            .get(fixture.first.key()).center(), 16.0));
        CanvasState state = CanvasState.of(1L, projection,
            LayoutFrame.of(1L, LayoutPositions.of(Collections.singletonMap(fixture.first.key(),
                fixture.state.layout().positions().nodes().get(fixture.first.key())),
                Collections.emptyMap()), false), GraphGeometry.of(geometries, Collections.emptyMap()),
            OperationalStatus.IDLE);
        GraphCanvas canvas = new GraphCanvas();
        canvas.setSize(SIZE);
        canvas.setTheme(lightTheme());
        canvas.setCanvasState(state);
        canvas.setPaintState(GraphPaintState.empty().withSelection(fixture.firstEndpoint));
        canvas.setViewport(GraphViewport.of(0.0, 0.0, 1.0));
        BufferedImage image = paintCanvas(canvas);

        assertThat(canvas.isEnabled()).isTrue();
        assertThat(nonBackgroundPixels(image, canvas.theme().background())).isGreaterThan(0);
        assertThat(hasInkNear(image, new Point2D.Double(75.0, 70.0), canvas.theme().selectionColor(), 22))
            .isTrue();
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
        return new Fixture(state, first, second, edge, firstEndpoint, secondEndpoint);
    }

    private static ProjectedEnclosure enclosure(final EnclosureHullKey hullKey, final EnclosureKey endpoint,
            final String fullLabel, final String displayLabel, final BoundaryTier tier) {
        return ProjectedEnclosure.of(hullKey, Collections.singletonList(endpoint),
            Collections.singletonList(SafeNodeLabel.of(fullLabel, displayLabel)), "Map",
            Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(),
            Collections.<EnclosureHullKey>emptyList(), true, tier);
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

        private Fixture(final CanvasState state, final ProjectedNode first, final ProjectedNode second,
                final ProjectedEdge edge, final ProjectedEndpointKey firstEndpoint,
                final ProjectedEndpointKey secondEndpoint) {
            this.state = state;
            this.first = first;
            this.second = second;
            this.edge = edge;
            this.firstEndpoint = firstEndpoint;
            this.secondEndpoint = secondEndpoint;
        }
    }
}
