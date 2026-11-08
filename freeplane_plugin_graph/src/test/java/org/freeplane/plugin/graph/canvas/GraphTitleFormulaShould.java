package org.freeplane.plugin.graph.canvas;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.plugin.graph.canvas.title.MathRunRow;
import org.freeplane.plugin.graph.canvas.title.PlainRunRow;
import org.freeplane.plugin.graph.canvas.title.TitleRow;
import org.freeplane.plugin.graph.adapter.SafeNodeLabelExtractor;
import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.HullGeometry;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.geometry.NodeGeometry;
import org.freeplane.plugin.graph.group.GraphGroupModel;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.projection.input.TitleText;
import org.freeplane.plugin.graph.projection.input.TitleTextParser;
import org.freeplane.plugin.graph.workspace.model.DisplaySettings.CanvasTheme;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.junit.Test;

/** End-to-end placement of a formula title: the label must carry a rendered or degraded row, never raw-only. */
public class GraphTitleFormulaShould {
    private static final MapReferenceId MAP = MapReferenceId.of("00000000-0000-0000-0000-000000000001");

    private static PlacedLabel place(final String displayText) {
        final ProjectedNodeKey key = ProjectedNodeKey.of(SourceNodeKey.persisted(
            NodeReference.of(MAP, PersistedNodeId.of("n1"))));
        final TitleText title = TitleTextParser.parse(displayText, false);
        final ProjectedNode node = ProjectedNode.of(key, SafeNodeLabel.of(displayText, displayText, title), "Map",
            false);
        final LayoutPoint center = LayoutPoint.of(0.0, 0.0);
        final Map<ProjectedNodeKey, NodeGeometry> geometry = new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
        geometry.put(key, NodeGeometry.of(center, 6.0));
        final Map<ProjectedNodeKey, LayoutPoint> positions = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        positions.put(key, center);
        final GraphProjection projection = GraphProjection.structure(1L, Arrays.asList(node),
            Collections.<ProjectedEnclosure>emptyList());
        final Set<ProjectedEndpointKey> forced = new LinkedHashSet<ProjectedEndpointKey>();
        final LabelPlacementRequest request = LabelPlacementRequest.of(projection,
            GraphGeometry.of(geometry, Collections.<EnclosureHullKey, HullGeometry>emptyMap()),
            LayoutPositions.of(positions, Collections.<EnclosureHullKey, LayoutPoint>emptyMap()),
            1.0, 0.0, 0.0, new Rectangle2D.Double(0.0, 0.0, 1128.0, 364.0), forced, RenderingLevel.FULL);
        final List<PlacedLabel> placed = new ScreenLabelPlacement().place(request, null,
            LabelFonts.from(GraphTheme.from(CanvasTheme.LIGHT)), Collections.<Rectangle2D>emptyList());
        assertThat(placed).hasSize(1);
        return placed.get(0);
    }

    private static PlacedLabel placeExtracted(final NodeModel node, final boolean forced) {
        final SafeNodeLabel label = new SafeNodeLabelExtractor().extract(node);
        final ProjectedNodeKey key = ProjectedNodeKey.of(SourceNodeKey.persisted(
            NodeReference.of(MAP, PersistedNodeId.of("n1"))));
        final ProjectedNode projected = ProjectedNode.of(key, label, "Map", true);
        final LayoutPoint center = LayoutPoint.of(0.0, 0.0);
        final Map<ProjectedNodeKey, NodeGeometry> geometry = new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
        geometry.put(key, NodeGeometry.of(center, 6.0));
        final Map<ProjectedNodeKey, LayoutPoint> positions = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        positions.put(key, center);
        final GraphProjection projection = GraphProjection.structure(1L, Arrays.asList(projected),
            Collections.<ProjectedEnclosure>emptyList());
        final Set<ProjectedEndpointKey> forcedEndpoints = new LinkedHashSet<ProjectedEndpointKey>();
        if (forced) {
            forcedEndpoints.add(ProjectedEndpointKey.ofNode(key));
        }
        final LabelPlacementRequest request = LabelPlacementRequest.of(projection,
            GraphGeometry.of(geometry, Collections.<EnclosureHullKey, HullGeometry>emptyMap()),
            LayoutPositions.of(positions, Collections.<EnclosureHullKey, LayoutPoint>emptyMap()),
            1.0, 0.0, 0.0, new Rectangle2D.Double(0.0, 0.0, 1128.0, 364.0), forcedEndpoints,
            RenderingLevel.FULL);
        final List<PlacedLabel> placed = new ScreenLabelPlacement().place(request, null,
            LabelFonts.from(GraphTheme.from(CanvasTheme.LIGHT)), Collections.<Rectangle2D>emptyList());
        assertThat(placed).hasSize(1);
        return placed.get(0);
    }

    @Test
    public void formulaTitlePlacesARenderedMathRun() {
        final PlacedLabel label = place("Theorem: $\\alpha$");
        assertThat(label.titleRow().form()).isEqualTo(TitleRow.Form.RENDERED);
        assertThat(label.titleRow().runs()).anyMatch(run -> run instanceof MathRunRow);
        assertThat(label.titleRow().runs()).anyMatch(run -> run instanceof PlainRunRow);
    }

    @Test
    public void unrenderableFormulaPlacesADegradedRow() {
        final PlacedLabel label = place("Theorem: $\\leslant$");
        assertThat(label.titleRow().form()).isEqualTo(TitleRow.Form.DEGRADED);
        assertThat(label.titleRow().runs()).noneMatch(run -> run instanceof MathRunRow);
        assertThat(label.titleRow().runs()).anyMatch(run -> run instanceof PlainRunRow
            && ((PlainRunRow) run).paintText().contains("\\leslant"));
    }

    @Test
    public void aFormulaTitleTooWideForTheSlotDegrades() {
        final PlacedLabel label = place("$\\bigcup\\alpha\\leqslant\\alpha\\quad xxxxxxxxxxxxxxxxxxxxxxxxx$");
        assertThat(label.width()).isLessThanOrEqualTo(200.0);
        assertThat(label.titleRow().runs()).noneMatch(run -> run instanceof MathRunRow);
    }

    @Test
    public void paintedRowMatchesThePlacedText() {
        final MapModel map = new MapModel((source, targetMap, withChildren) -> null, null, null);
        map.setRoot(new NodeModel("root", map));
        final NodeModel formula = new NodeModel("\\latex " + new String(new char[100]).replace('\0', 'W'), map);
        map.getRootNode().insert(formula);
        formula.putExtension(new GraphGroupModel());

        final PlacedLabel label = placeExtracted(formula, true);
        assertThat(label.truncated()).isTrue();
        assertThat(label.titleRow().form()).isEqualTo(TitleRow.Form.DEGRADED);
        assertThat(label.titleRow().runs()).hasSize(1);
        assertThat(label.titleRow().runs().get(0)).isInstanceOf(PlainRunRow.class);
        assertThat(((PlainRunRow) label.titleRow().runs().get(0)).paintText()).isEqualTo(label.text());
    }

    @Test
    public void renderedTitleUsesOneMathRun() {
        final MapModel map = new MapModel((source, targetMap, withChildren) -> null, null, null);
        map.setRoot(new NodeModel("root", map));
        final NodeModel formula = new NodeModel("\\latex Theorem: $ON$ is well-ordered by $\\in$", map);
        map.getRootNode().insert(formula);
        formula.putExtension(new GraphGroupModel());

        final PlacedLabel label = placeExtracted(formula, false);
        assertThat(label.titleRow().form()).isEqualTo(TitleRow.Form.RENDERED);
        assertThat(label.titleRow().runs()).hasSize(1);
        assertThat(label.titleRow().runs().get(0)).isInstanceOf(MathRunRow.class);
        final MathRunRow run = (MathRunRow) label.titleRow().runs().get(0);
        assertThat(run.body()).isEqualTo("Theorem: $ON$ is well-ordered by $\\in$");
        assertThat(run.icon()).isNotNull();
        assertThat(label.width()).isEqualTo(run.width());
    }

    @Test
    public void reportedTitleFitsTheVerticalSlot() {
        final MapModel map = new MapModel((source, targetMap, withChildren) -> null, null, null);
        map.setRoot(new NodeModel("root", map));
        final NodeModel formula = new NodeModel("\\latex Theorem: $ON$ is well-ordered by $\\in$", map);
        map.getRootNode().insert(formula);
        formula.putExtension(new GraphGroupModel());

        final PlacedLabel label = placeExtracted(formula, false);
        assertThat(label.width()).isBetween(193.49, 194.51);
        assertThat(label.slot()).isEqualTo(ScreenLabelPlacement.Slot.ABOVE);
        assertThat(label.rung()).isEqualTo(PlacedLabel.Rung.FULL_NEAR);
        assertThat(label.truncated()).isFalse();
        assertThat(label.width()).isLessThanOrEqualTo(200.0);
    }
}
