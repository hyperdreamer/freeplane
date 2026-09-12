package org.freeplane.plugin.graph.canvas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.HullGeometry;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.geometry.NodeGeometry;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.DisplaySettings.CanvasTheme;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.junit.Test;

public class ScreenLabelPlacementShould {
    private static final MapReferenceId MAP = MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    private static final double ELLIPSIS_TOLERANCE = 1e-4;
    private static final String[] DENSE_NAMES = { "Theorem", "Axiom of Choice",
        "Replacement Scheme", "Extensionality", "Pairing", "Union", "Power Set", "Infinity",
        "Separation", "Foundation / Regularity", "Comprehension", "Well-Ordering" };
    private static final String[] LONG_NAMES = {
        "Well-Ordering Theorem of Choice and Regularity",
        "Axiom Schema of Replacement and Comprehension",
        "Transfinite Induction over Ordinal Numbers",
        "Cardinal Arithmetic under the Continuum Hypothesis",
        "Ultrafilter Lemma and Boolean Prime Ideal Theorem",
        "Kuratowski Zorn Lemma for Partially Ordered Sets" };

    @Test
    public void placesTheDenseSceneAtThePinnedViewport() {
        List<SceneNode> scene = denseScene();
        Rectangle2D area = area(1128.0, 364.0);

        List<PlacedLabel> placed = place(scene, 1.0, area, standIn(scene, 1.0), forced("Axiom of Choice"),
            RenderingLevel.FULL, null);

        assertThat(placed).hasSize(12);
        assertRow(placed, area, "Axiom of Choice", "ABOVE", 12, "Axiom of Choice",
            -17.0, -56.172057, 91.104675, 16.344114);
        assertRow(placed, area, "Theorem", "LEFT", 12, "Theorem", -96.530182, -34.0,
            51.060364, 16.344114);
        assertRow(placed, area, "Extensionality", "RIGHT", 12, "Extensionality", 110.216270, -34.0,
            78.432541, 16.344114);
        assertRow(placed, area, "Power Set", "RIGHT_FAR", 12, "Power Set", 89.242210, 0.0,
            56.484421, 16.344114);
        assertRow(placed, area, "Foundation / Regularity", "BELOW", 12,
            "Foundation / Regularity", -17.0, 62.172057, 132.600922, 16.344114);
        assertRow(placed, area, "Replacement Scheme", "ABOVE_RIGHT", 12, "Replacement Scheme",
            91.672424, -56.172057, 121.344849, 16.344114);
        assertRow(placed, area, "Pairing", "LEFT", 12, "Pairing", -84.968140, 0.0,
            39.936279, 16.344114);
        assertRow(placed, area, "Infinity", "BELOW_RIGHT", 12, "Infinity", 84.836136, 22.172057,
            39.672272, 16.344114);
        assertRow(placed, area, "Separation", "LEFT", 12, "Separation", -95.630211, 34.0,
            61.260422, 16.344114);
        assertRow(placed, area, "Comprehension", "BELOW_FAR", 12, "Comprehension", 17.0, 80.172057,
            90.288651, 16.344114);
        assertRow(placed, area, "Well-Ordering", "BELOW_RIGHT", 12, "Well-Ordering", 104.654289,
            56.172057, 79.308578, 16.344114);
        PlacedLabel union = find(placed, "Union");
        assertThat(union.mode()).isEqualTo(PlacedLabel.Mode.HOVER_ONLY);
        assertThat(histogram(placed, "full")).isEqualTo(11);
        assertThat(histogram(placed, "dense")).isZero();
        assertThat(histogram(placed, "truncated")).isZero();
        assertThat(histogram(placed, "hover-only")).isEqualTo(1);
        assertThat(labelLabelCollisions(placed)).isZero();
        assertThat(labelDiscCollisions(placed, scene, 1.0)).isZero();
        assertThat(meanLeader(placed, scene, 1.0)).isCloseTo(48.046026, within(1e-4));
        assertThat(maxLeader(placed, scene, 1.0)).isCloseTo(77.894615, within(1e-4));
    }

    @Test
    public void keepsThePinnedDenseHistogramsAtSmallerViewports() {
        List<SceneNode> scene = denseScene();

        List<PlacedLabel> wide = place(scene, 1.0, area(420.0, 240.0),
            standIn(scene, 1.0, area(420.0, 240.0)),
            forced("Axiom of Choice"), RenderingLevel.FULL, null);
        assertThat(histogram(wide, "full")).isEqualTo(11);
        assertThat(histogram(wide, "hover-only")).isEqualTo(1);

        List<PlacedLabel> medium = place(scene, 1.0, area(280.0, 170.0),
            standIn(scene, 1.0, area(280.0, 170.0)),
            forced("Axiom of Choice"), RenderingLevel.FULL, null);
        assertThat(histogram(medium, "full")).isEqualTo(7);
        assertThat(histogram(medium, "dense")).isEqualTo(4);
        assertThat(histogram(medium, "hover-only")).isEqualTo(1);

        List<PlacedLabel> cramped = place(scene, 1.0, area(200.0, 130.0),
            standIn(scene, 1.0, area(200.0, 130.0)),
            forced("Axiom of Choice"), RenderingLevel.FULL, null);
        assertThat(histogram(cramped, "full")).isEqualTo(2);
        assertThat(histogram(cramped, "dense")).isEqualTo(4);
        assertThat(histogram(cramped, "hover-only")).isEqualTo(6);
    }

    @Test
    public void placesTheLongSceneThroughTheO4BaseSlotWithoutTheStandIn() {
        List<SceneNode> scene = longScene();
        Rectangle2D area = area(1128.0, 364.0);

        List<PlacedLabel> placed = place(scene, 1.0, area, null, forced(LONG_NAMES[0]),
            RenderingLevel.FULL, null);

        PlacedLabel forced = find(placed, LONG_NAMES[0]);
        assertThat(forced.forcedAtBaseSlot()).isTrue();
        assertRow(placed, area, LONG_NAMES[0], "ABOVE", 12, LONG_NAMES[0], -22.0, -22.172057,
            274.130005, 16.344114);
        assertRow(placed, area, LONG_NAMES[1], "ABOVE_FAR", 12,
            "Axiom Schema of Replacement a\u2026", 0.0, -46.172057, 193.873367, 16.344114);
        assertRow(placed, area, LONG_NAMES[2], "BELOW_FAR", 9, LONG_NAMES[2], 22.0, 44.129043,
            185.041336, 12.258085);
        assertRow(placed, area, LONG_NAMES[3], "LEFT", 12, "Cardinal Arithmetic u\u2026",
            -100.392456, 22.0, 128.784912, 16.344114);
        assertRow(placed, area, LONG_NAMES[4], "BELOW_FAR", 12,
            "Ultrafilter Lemma and Boolean Pr\u2026", 0.0, 68.172057, 198.541412, 16.344114);
        assertRow(placed, area, LONG_NAMES[5], "RIGHT", 12, "Kuratowski Zorn Lem\u2026",
            100.656464, 22.0, 129.312927, 16.344114);
        assertThat(histogram(placed, "full")).isEqualTo(1);
        assertThat(histogram(placed, "dense")).isEqualTo(1);
        assertThat(histogram(placed, "truncated")).isEqualTo(4);
        assertThat(histogram(placed, "hover-only")).isZero();
    }

    @Test
    public void placesTheLongSceneThroughTheO4BaseSlotWithTheStandIn() {
        List<SceneNode> scene = longScene();
        Rectangle2D area = area(1128.0, 364.0);

        List<PlacedLabel> placed = place(scene, 1.0, area, standIn(scene, 1.0), forced(LONG_NAMES[0]),
            RenderingLevel.FULL, null);

        assertThat(find(placed, LONG_NAMES[0]).forcedAtBaseSlot()).isTrue();
        assertRow(placed, area, LONG_NAMES[0], "ABOVE", 12, LONG_NAMES[0], -22.0, -22.172057,
            274.130005, 16.344114);
        assertRow(placed, area, LONG_NAMES[1], "BELOW_FAR", 12,
            "Axiom Schema of Replacement a\u2026", 0.0, 46.172057, 193.873367, 16.344114);
        assertRow(placed, area, LONG_NAMES[2], "RIGHT", 12, "Transfinite Induction\u2026",
            99.558441, 0.0, 127.116882, 16.344114);
        assertRow(placed, area, LONG_NAMES[3], "LEFT", 12, "Cardinal Arithmetic u\u2026",
            -100.392456, 22.0, 128.784912, 16.344114);
        assertRow(placed, area, LONG_NAMES[4], "BELOW_FAR", 12,
            "Ultrafilter Lemma and Boolean Pr\u2026", 0.0, 68.172057, 198.541412, 16.344114);
        assertRow(placed, area, LONG_NAMES[5], "RIGHT", 12, "Kuratowski Zorn Lem\u2026",
            100.656464, 22.0, 129.312927, 16.344114);
        assertThat(histogram(placed, "full")).isEqualTo(1);
        assertThat(histogram(placed, "dense")).isZero();
        assertThat(histogram(placed, "truncated")).isEqualTo(5);
        assertThat(histogram(placed, "hover-only")).isZero();
    }

    @Test
    public void keepsThePinnedLongHistogramsAtTheSmallerViewports() {
        List<SceneNode> scene = longScene();

        List<PlacedLabel> wide = place(scene, 1.0, area(500.0, 300.0), null,
            forced(LONG_NAMES[0]), RenderingLevel.FULL, null);
        assertThat(histogram(wide, "full")).isEqualTo(1);
        assertThat(histogram(wide, "dense")).isEqualTo(1);
        assertThat(histogram(wide, "truncated")).isEqualTo(4);
        assertThat(histogram(wide, "hover-only")).isZero();
        assertThat(maxLeader(wide, scene, 1.0, area(500.0, 300.0))).isCloseTo(78.6565, within(1e-3));

        List<PlacedLabel> cramped = place(scene, 1.0, area(200.0, 130.0), null,
            forced(LONG_NAMES[0]), RenderingLevel.FULL, null);
        assertThat(histogram(cramped, "full")).isEqualTo(1);
        assertThat(histogram(cramped, "truncated")).isEqualTo(2);
        assertThat(histogram(cramped, "hover-only")).isEqualTo(3);

        List<PlacedLabel> crampedWithStandIn = place(scene, 1.0, area(200.0, 130.0),
            standIn(scene, 1.0, area(200.0, 130.0)), forced(LONG_NAMES[0]), RenderingLevel.FULL, null);
        assertThat(histogram(crampedWithStandIn, "full")).isEqualTo(1);
        assertThat(histogram(crampedWithStandIn, "truncated")).isEqualTo(1);
        assertThat(histogram(crampedWithStandIn, "hover-only")).isEqualTo(4);
    }

    @Test
    public void followsThePinnedZoomAndRenderingLevelMatrix() {
        List<SceneNode> scene = denseScene();
        Rectangle2D area = area(1128.0, 364.0);

        assertZoomCell(scene, area, 0.25, 8, 7, 1, "ABOVE", -4.25, -24.672057,
            Arrays.asList("Pairing", "Union", "Infinity", "Well-Ordering"));
        assertZoomCell(scene, area, 0.5, 10, 10, 0, "ABOVE", -8.5, -35.172057,
            Arrays.asList("Union", "Well-Ordering"));
        assertZoomCell(scene, area, 1.0, 11, 11, 0, "ABOVE", -17.0, -56.172057,
            Collections.singletonList("Union"));
        assertZoomCell(scene, area, 2.0, 11, 11, 0, "ABOVE_FAR", -34.0, -122.172057,
            Collections.singletonList("Replacement Scheme"));

        for (double zoom : new double[] { 0.25, 0.5, 1.0, 2.0 }) {
            List<PlacedLabel> overTarget = place(scene, zoom, area, null, forced("Axiom of Choice"),
                RenderingLevel.OVER_TARGET, null);
            assertThat(overTarget).hasSize(1);
            assertThat(overTarget.get(0).endpoint())
                .isEqualTo(ProjectedEndpointKey.ofNode(key("Axiom of Choice")));
            assertThat(overTarget.get(0).forced()).isTrue();
        }
    }

    @Test
    public void neverTruncatesWhileAFullTextSlotWasFree() {
        List<SceneNode> scene = longScene();
        for (double width : new double[] { 1128.0, 500.0 }) {
            List<PlacedLabel> withoutStandIn = place(scene, 1.0, area(width, 364.0), null,
                forced(LONG_NAMES[0]), RenderingLevel.FULL, null);
            for (PlacedLabel label : withoutStandIn) {
                if (label.truncated()) {
                    assertThat(label.fullTextSlotWasFree()).as(label.text()).isFalse();
                }
            }
            List<PlacedLabel> withStandIn = place(scene, 1.0, area(width, 364.0),
                standIn(scene, 1.0, area(width, 364.0)), forced(LONG_NAMES[0]),
                RenderingLevel.FULL, null);
            for (PlacedLabel label : withStandIn) {
                if (label.truncated()) {
                    assertThat(label.fullTextSlotWasFree()).as(label.text()).isFalse();
                }
            }
        }
    }

    private static void assertZoomCell(List<SceneNode> scene, Rectangle2D area, double zoom,
            int placedCount, int fullCount, int denseCount, String forcedSlot, double forcedX,
            double forcedY, List<String> hidden) {
        List<PlacedLabel> placed = place(scene, zoom, area, null, forced("Axiom of Choice"),
            RenderingLevel.FULL, null);

        assertThat(countVisible(placed)).as("zoom " + zoom + " placed").isEqualTo(placedCount);
        assertThat(histogram(placed, "full")).as("zoom " + zoom + " full").isEqualTo(fullCount);
        assertThat(histogram(placed, "dense")).as("zoom " + zoom + " dense").isEqualTo(denseCount);
        for (String name : hidden) {
            assertThat(find(placed, name).mode()).as(name).isEqualTo(PlacedLabel.Mode.HOVER_ONLY);
        }
        PlacedLabel forced = find(placed, "Axiom of Choice");
        assertThat(forced.mode()).isNotEqualTo(PlacedLabel.Mode.HOVER_ONLY);
        assertThat(slotOf(forced, scene, zoom)).isEqualTo(forcedSlot);
        assertThat(forced.anchorX() - area.getWidth() * 0.5).isCloseTo(forcedX, within(ELLIPSIS_TOLERANCE));
        assertThat(forced.anchorY() - area.getHeight() * 0.5).isCloseTo(forcedY, within(ELLIPSIS_TOLERANCE));
    }

    static List<SceneNode> denseScene() {
        List<SceneNode> scene = new ArrayList<SceneNode>();
        for (int index = 0; index < DENSE_NAMES.length; index++) {
            scene.add(new SceneNode(DENSE_NAMES[index], index % 3 == 0 ? 14.0 : 8.0, index == 1,
                (index % 4 - 1.5) * 34.0, (index / 4 - 1) * 34.0));
        }
        return scene;
    }

    static List<SceneNode> longScene() {
        List<SceneNode> scene = new ArrayList<SceneNode>();
        for (int index = 0; index < LONG_NAMES.length; index++) {
            scene.add(new SceneNode(LONG_NAMES[index], 8.0, index == 0,
                (index % 3 - 1) * 22.0, (index / 3) * 22.0));
        }
        return scene;
    }

    static List<PlacedLabel> place(List<SceneNode> scene, double zoom, Rectangle2D area,
            Rectangle2D standIn, Set<ProjectedEndpointKey> forced, RenderingLevel level,
            List<PlacedLabel> previous) {
        List<Rectangle2D> obstacles = standIn == null
            ? Collections.<Rectangle2D>emptyList() : Collections.singletonList(standIn);
        return new ScreenLabelPlacement().place(request(scene, zoom, area, forced, level),
            previous, LabelFonts.from(GraphTheme.resolve(CanvasTheme.LIGHT)), obstacles);
    }

    static LabelPlacementRequest request(List<SceneNode> scene, double zoom, Rectangle2D area,
            Set<ProjectedEndpointKey> forced, RenderingLevel level) {
        List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        Map<ProjectedNodeKey, NodeGeometry> geometry = new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
        Map<ProjectedNodeKey, LayoutPoint> positions = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        for (SceneNode node : scene) {
            ProjectedNodeKey nodeKey = key(node.name);
            nodes.add(ProjectedNode.of(nodeKey, SafeNodeLabel.of(node.name, node.name), "Map", false));
            geometry.put(nodeKey, NodeGeometry.of(LayoutPoint.of(node.x, node.y), node.radius));
            positions.put(nodeKey, LayoutPoint.of(node.x, node.y));
        }
        GraphProjection projection = GraphProjection.structure(1L, nodes,
            Collections.<org.freeplane.plugin.graph.projection.ProjectedEnclosure>emptyList());
        GraphGeometry graphGeometry = GraphGeometry.of(geometry,
            Collections.<EnclosureHullKey, HullGeometry>emptyMap());
        LayoutPositions layoutPositions = LayoutPositions.of(positions,
            Collections.<EnclosureHullKey, LayoutPoint>emptyMap());
        return LabelPlacementRequest.of(projection, graphGeometry, layoutPositions, zoom, 0.0, 0.0,
            area, forced, level);
    }

    static Rectangle2D area(double width, double height) {
        return new Rectangle2D.Double(0.0, 0.0, width, height);
    }

    static Rectangle2D standIn(List<SceneNode> scene, double zoom) {
        return standIn(scene, zoom, area(1128.0, 364.0));
    }

    static Rectangle2D standIn(List<SceneNode> scene, double zoom, Rectangle2D area) {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        for (SceneNode node : scene) {
            minX = Math.min(minX, node.x * zoom - node.radius * zoom);
            minY = Math.min(minY, node.y * zoom - node.radius * zoom);
        }
        double width = ScreenLabelPlacement.textWidth("Basic Definitions and Theorems",
            LabelFonts.from(GraphTheme.resolve(CanvasTheme.LIGHT)).emphatic()
                .deriveFont(java.awt.Font.PLAIN, 10.0f));
        double height = ScreenLabelPlacement.screenBounds("Basic Definitions and Theorems",
            LabelFonts.from(GraphTheme.resolve(CanvasTheme.LIGHT)).emphatic()
                .deriveFont(java.awt.Font.PLAIN, 10.0f)).getHeight();
        return new Rectangle2D.Double(minX - 11.0 + area.getWidth() * 0.5,
            minY - 18.0 - height + area.getHeight() * 0.5, width, height);
    }

    static Set<ProjectedEndpointKey> forced(String... names) {
        Set<ProjectedEndpointKey> forced = new LinkedHashSet<ProjectedEndpointKey>();
        for (String name : names) {
            forced.add(ProjectedEndpointKey.ofNode(key(name)));
        }
        return forced;
    }

    static PlacedLabel find(List<PlacedLabel> placed, String name) {
        for (PlacedLabel label : placed) {
            if (label.endpoint().isNode() && name.equals(nodeName(label.endpoint()))) {
                return label;
            }
        }
        return null;
    }

    static String nodeName(ProjectedEndpointKey endpoint) {
        return endpoint.node().get().source().persistedReference().get().nodeId().value();
    }

    static void assertRow(List<PlacedLabel> placed, Rectangle2D area, String name, String slotName,
            int fontSize, String text, double fixtureX, double fixtureY, double fixtureWidth,
            double fixtureHeight) {
        PlacedLabel label = find(placed, name);
        assertThat(label).as("label " + name).isNotNull();
        assertThat(label.font().getSize()).as(name + " font").isEqualTo(fontSize);
        assertThat(label.text()).as(name + " text").isEqualTo(text);
        assertThat(label.anchorX() - area.getWidth() * 0.5).as(name + " x")
            .isCloseTo(fixtureX, within(ELLIPSIS_TOLERANCE));
        assertThat(label.anchorY() - area.getHeight() * 0.5).as(name + " y")
            .isCloseTo(fixtureY, within(ELLIPSIS_TOLERANCE));
        assertThat(label.width()).as(name + " w").isCloseTo(fixtureWidth, within(ELLIPSIS_TOLERANCE));
        assertThat(label.height()).as(name + " h").isCloseTo(fixtureHeight, within(ELLIPSIS_TOLERANCE));
    }

    static String slotOf(PlacedLabel label, List<SceneNode> scene, double zoom) {
        SceneNode node = sceneNode(scene, nodeName(label.endpoint()));
        double centerX = node.x * zoom + 564.0;
        double centerY = node.y * zoom + 182.0;
        double radius = Math.max(2.0, node.radius * zoom);
        for (ScreenLabelPlacement.Slot slot : ScreenLabelPlacement.Slot.values()) {
            double[] anchor = ScreenLabelPlacement.slotAnchor(slot, centerX, centerY, radius,
                label.width(), label.height());
            if (Math.abs(anchor[0] - label.anchorX()) <= 1e-6
                    && Math.abs(anchor[1] - label.anchorY()) <= 1e-6) {
                return slot.name();
            }
        }
        return null;
    }

    private static SceneNode sceneNode(List<SceneNode> scene, String name) {
        for (SceneNode node : scene) {
            if (node.name.equals(name)) {
                return node;
            }
        }
        throw new IllegalArgumentException("Unknown scene node " + name);
    }

    static int countVisible(List<PlacedLabel> placed) {
        int count = 0;
        for (PlacedLabel label : placed) {
            if (label.mode() != PlacedLabel.Mode.HOVER_ONLY) {
                count++;
            }
        }
        return count;
    }

    static int histogram(List<PlacedLabel> placed, String kind) {
        int count = 0;
        for (PlacedLabel label : placed) {
            if ("hover-only".equals(kind)) {
                if (label.mode() == PlacedLabel.Mode.HOVER_ONLY) {
                    count++;
                }
            }
            else if (label.mode() != PlacedLabel.Mode.HOVER_ONLY) {
                if (label.truncated()) {
                    if ("truncated".equals(kind)) {
                        count++;
                    }
                }
                else if (label.font().getSize() == 12 && "full".equals(kind)) {
                    count++;
                }
                else if (label.font().getSize() == 9 && "dense".equals(kind)) {
                    count++;
                }
            }
        }
        return count;
    }

    static int labelLabelCollisions(List<PlacedLabel> placed) {
        int collisions = 0;
        for (int first = 0; first < placed.size(); first++) {
            for (int second = first + 1; second < placed.size(); second++) {
                if (placed.get(first).mode() == PlacedLabel.Mode.HOVER_ONLY
                        || placed.get(second).mode() == PlacedLabel.Mode.HOVER_ONLY) {
                    continue;
                }
                if (placed.get(first).bounds().intersects(placed.get(second).bounds())) {
                    collisions++;
                }
            }
        }
        return collisions;
    }

    static int labelDiscCollisions(List<PlacedLabel> placed, List<SceneNode> scene, double zoom) {
        int collisions = 0;
        for (PlacedLabel label : placed) {
            if (label.mode() == PlacedLabel.Mode.HOVER_ONLY) {
                continue;
            }
            for (SceneNode node : scene) {
                double radius = Math.max(2.0, node.radius * zoom);
                Rectangle2D disc = new Rectangle2D.Double(node.x * zoom - radius + 564.0,
                    node.y * zoom - radius + 182.0, 2.0 * radius, 2.0 * radius);
                if (disc.intersects(label.bounds())) {
                    collisions++;
                    break;
                }
            }
        }
        return collisions;
    }

    static double leader(PlacedLabel label, List<SceneNode> scene, double zoom) {
        return leader(label, scene, zoom, area(1128.0, 364.0));
    }

    static double leader(PlacedLabel label, List<SceneNode> scene, double zoom, Rectangle2D area) {
        SceneNode node = sceneNode(scene, nodeName(label.endpoint()));
        double centerX = node.x * zoom + area.getWidth() * 0.5;
        double centerY = node.y * zoom + area.getHeight() * 0.5;
        return Math.hypot(label.anchorX() - centerX, label.anchorY() - centerY);
    }

    static double meanLeader(List<PlacedLabel> placed, List<SceneNode> scene, double zoom) {
        double sum = 0.0;
        int count = 0;
        for (PlacedLabel label : placed) {
            if (label.mode() == PlacedLabel.Mode.HOVER_ONLY) {
                continue;
            }
            sum += leader(label, scene, zoom);
            count++;
        }
        return sum / count;
    }

    static double maxLeader(List<PlacedLabel> placed, List<SceneNode> scene, double zoom) {
        return maxLeader(placed, scene, zoom, area(1128.0, 364.0));
    }

    static double maxLeader(List<PlacedLabel> placed, List<SceneNode> scene, double zoom,
            Rectangle2D area) {
        double max = 0.0;
        for (PlacedLabel label : placed) {
            if (label.mode() == PlacedLabel.Mode.HOVER_ONLY) {
                continue;
            }
            max = Math.max(max, leader(label, scene, zoom, area));
        }
        return max;
    }

    static ProjectedNodeKey key(String name) {
        return ProjectedNodeKey.of(SourceNodeKey.persisted(
            NodeReference.of(MAP, PersistedNodeId.of(name))));
    }

    static final class SceneNode {
        final String name;
        final double radius;
        final boolean selected;
        final double x;
        final double y;

        SceneNode(String name, double radius, boolean selected, double x, double y) {
            this.name = name;
            this.radius = radius;
            this.selected = selected;
            this.x = x;
            this.y = y;
        }
    }
}
