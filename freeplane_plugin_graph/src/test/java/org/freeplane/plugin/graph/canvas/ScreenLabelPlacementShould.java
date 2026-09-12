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
import java.util.Optional;
import java.util.Set;

import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.HullGeometry;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.geometry.NodeGeometry;
import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
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
    public void resetsTheBaseSlotFlagWhenRetainingAnO4Label() {
        List<SceneNode> scene = longScene();
        Rectangle2D area = area(1128.0, 364.0);
        List<PlacedLabel> baseline = place(scene, 1.0, area, null, forced(LONG_NAMES[0]),
            RenderingLevel.FULL, null);
        PlacedLabel before = find(baseline, LONG_NAMES[0]);
        assertThat(before.forcedAtBaseSlot()).isTrue();

        List<PlacedLabel> retained = place(scene, 1.0, area, null, forced(LONG_NAMES[0]),
            RenderingLevel.FULL, baseline);

        PlacedLabel after = find(retained, LONG_NAMES[0]);
        assertThat(after.forcedAtBaseSlot()).isFalse();
        assertThat(after.mode()).isEqualTo(before.mode());
        assertThat(after.rung()).isEqualTo(before.rung());
        assertThat(after.anchorX()).isEqualTo(before.anchorX());
        assertThat(after.anchorY()).isEqualTo(before.anchorY());
        assertThat(after.width()).isEqualTo(before.width());
        assertThat(after.height()).isEqualTo(before.height());
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
    public void drawsNodeLeadersForEverySlotExceptAboveAndBelow() {
        List<SceneNode> scene = denseScene();
        Rectangle2D area = area(1128.0, 364.0);
        List<PlacedLabel> placed = place(scene, 1.0, area, standIn(scene, 1.0),
            forced("Axiom of Choice"), RenderingLevel.FULL, null);

        assertThat(meanLeader(placed, scene, 1.0)).isCloseTo(48.046026, within(1e-4));
        assertThat(maxLeader(placed, scene, 1.0)).isCloseTo(77.894615, within(1e-4));
        assertThat(leaderCrossings(placed)).isZero();
        assertNodeLeadersAtTheRim(placed, scene, 1.0, area);

        PlacedLabel theorem = find(placed, "Theorem");
        assertThat(slotOf(theorem, scene, 1.0)).isEqualTo("LEFT");
        assertThat(theorem.leaderStart()).isPresent();
        assertThat(theorem.leaderStart().get().x()).isCloseTo(513.0 - 14.0, within(1e-9));
        assertThat(theorem.leaderStart().get().y()).isCloseTo(148.0, within(1e-9));
        PlacedLabel powerSet = find(placed, "Power Set");
        assertThat(slotOf(powerSet, scene, 1.0)).isEqualTo("RIGHT_FAR");
        assertThat(powerSet.leaderStart()).isPresent();
        assertThat(powerSet.leaderStart().get().x()).isCloseTo(581.0 + 14.0, within(1e-9));
        assertThat(powerSet.leaderStart().get().y()).isCloseTo(182.0, within(1e-9));
        PlacedLabel replacement = find(placed, "Replacement Scheme");
        assertThat(slotOf(replacement, scene, 1.0)).isEqualTo("ABOVE_RIGHT");
        assertThat(replacement.leaderStart()).isPresent();

        List<PlacedLabel> longPlaced = place(longScene(), 1.0, area, standIn(longScene(), 1.0),
            forced(LONG_NAMES[0]), RenderingLevel.FULL, null);
        PlacedLabel longForced = find(longPlaced, LONG_NAMES[0]);
        assertThat(longForced.leaderStart()).isEmpty();
        assertThat(maxLeader(longPlaced, longScene(), 1.0)).isCloseTo(78.656464, within(1e-4));
        assertThat(leaderCrossings(longPlaced)).isZero();
        assertNodeLeadersAtTheRim(longPlaced, longScene(), 1.0, area);
    }

    private static void assertNodeLeadersAtTheRim(List<PlacedLabel> placed, List<SceneNode> scene,
            double zoom, Rectangle2D area) {
        for (PlacedLabel label : placed) {
            if (label.mode() == PlacedLabel.Mode.HOVER_ONLY || label.endpoint().isEnclosure()) {
                continue;
            }
            String slot = slotOf(label, scene, zoom, area);
            assertThat(slot).as(label.text()).isNotNull();
            if ("ABOVE".equals(slot) || "BELOW".equals(slot)) {
                assertThat(label.leaderStart()).as(slot + " " + label.text()).isEmpty();
                continue;
            }
            assertThat(label.leaderStart()).as(slot + " " + label.text()).isPresent();
            SceneNode node = sceneNode(scene, nodeName(label.endpoint()));
            double centerX = node.x * zoom + area.getWidth() * 0.5;
            double centerY = node.y * zoom + area.getHeight() * 0.5;
            double radius = Math.max(2.0, node.radius * zoom);
            LayoutPoint start = label.leaderStart().get();
            double dx = start.x() - centerX;
            double dy = start.y() - centerY;
            double ax = label.anchorX() - centerX;
            double ay = label.anchorY() - centerY;
            assertThat(Math.hypot(dx, dy)).as(slot + " rim " + label.text())
                .isCloseTo(radius, within(1e-9));
            assertThat(dx * ay - dy * ax).as(slot + " collinear " + label.text())
                .isCloseTo(0.0, within(1e-9));
            assertThat(dx * ax + dy * ay).as(slot + " forward " + label.text())
                .isGreaterThan(0.0);
        }
    }

    private static void assertRetainedNodeLeadersAtTheRim(List<PlacedLabel> placed,
            List<SceneNode> scene, double zoom, Rectangle2D area, double viewportCenterX,
            double viewportCenterY) {
        for (PlacedLabel label : placed) {
            if (label.mode() == PlacedLabel.Mode.HOVER_ONLY || label.endpoint().isEnclosure()) {
                continue;
            }
            SceneNode node = sceneNode(scene, nodeName(label.endpoint()));
            double centerX = node.x * zoom + area.getWidth() * 0.5 - viewportCenterX * zoom;
            double centerY = node.y * zoom + area.getHeight() * 0.5 - viewportCenterY * zoom;
            double radius = Math.max(2.0, node.radius * zoom);
            String slot = slotOfAt(label, centerX, centerY, radius);
            assertThat(slot).as("retained slot " + label.text()).isNotNull();
            if ("ABOVE".equals(slot) || "BELOW".equals(slot)) {
                assertThat(label.leaderStart()).as(slot + " " + label.text()).isEmpty();
                continue;
            }
            assertThat(label.leaderStart()).as(slot + " " + label.text()).isPresent();
            LayoutPoint start = label.leaderStart().get();
            double dx = start.x() - centerX;
            double dy = start.y() - centerY;
            double ax = label.anchorX() - centerX;
            double ay = label.anchorY() - centerY;
            assertThat(Math.hypot(dx, dy)).as(slot + " retained rim " + label.text())
                .isCloseTo(radius, within(1e-9));
            assertThat(dx * ay - dy * ax).as(slot + " retained collinear " + label.text())
                .isCloseTo(0.0, within(1e-9));
            assertThat(dx * ax + dy * ay).as(slot + " retained forward " + label.text())
                .isGreaterThan(0.0);
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

    @Test
    public void recomputesTheFullTextSlotPredicateIndependently() {
        List<SceneNode> scene = longScene();
        for (double width : new double[] { 1128.0, 500.0 }) {
            Rectangle2D area = area(width, 364.0);
            for (Rectangle2D standIn : new Rectangle2D[] { null, standIn(scene, 1.0, area) }) {
                List<Rectangle2D> seeds = standIn == null
                    ? Collections.<Rectangle2D>emptyList() : Collections.singletonList(standIn);
                List<PlacedLabel> placed = new ScreenLabelPlacement().place(
                    request(scene, 1.0, area, forced(LONG_NAMES[0]), RenderingLevel.FULL), null,
                    fonts(), seeds);
                List<Rectangle2D> obstacles = new ArrayList<Rectangle2D>();
                for (SceneNode node : scene) {
                    double radius = Math.max(2.0, node.radius);
                    obstacles.add(new Rectangle2D.Double(node.x - radius + area.getWidth() * 0.5,
                        node.y - radius + area.getHeight() * 0.5, 2.0 * radius, 2.0 * radius));
                }
                obstacles.addAll(seeds);
                for (PlacedLabel label : placed) {
                    boolean recomputed = independentlyFullTextSlotWasFree(label, scene, area, obstacles);
                    assertThat(label.fullTextSlotWasFree()).as(label.text()).isEqualTo(recomputed);
                    if (label.truncated()) {
                        assertThat(label.fullTextSlotWasFree()).as(label.text()).isFalse();
                    }
                    if (label.mode() != PlacedLabel.Mode.HOVER_ONLY) {
                        obstacles.add(label.bounds());
                    }
                }
            }
        }
    }

    private static boolean independentlyFullTextSlotWasFree(PlacedLabel label, List<SceneNode> scene,
            Rectangle2D area, List<Rectangle2D> obstacles) {
        SceneNode node = sceneNode(scene, nodeName(label.endpoint()));
        double centerX = node.x + area.getWidth() * 0.5;
        double centerY = node.y + area.getHeight() * 0.5;
        double radius = Math.max(2.0, node.radius);
        LabelFonts fonts = fonts();
        for (int pass = 0; pass < 4; pass++) {
            boolean dense = pass >= 2;
            if (dense && label.forced()) {
                continue;
            }
            boolean far = pass % 2 == 1;
            java.awt.Font font = dense ? fonts.dense() : fonts.full();
            ScreenLabelPlacement.Slot[] slots = far
                ? ScreenLabelPlacement.FAR_SLOTS : ScreenLabelPlacement.NEAR_SLOTS;
            for (ScreenLabelPlacement.Slot slot : slots) {
                if (ScreenLabelPlacement.textWidth(node.name, font)
                        > ScreenLabelPlacement.slotMaxWidth(slot)) {
                    continue;
                }
                Rectangle2D size = ScreenLabelPlacement.screenBounds(node.name, font);
                double[] anchor = ScreenLabelPlacement.slotAnchor(slot, centerX, centerY, radius,
                    size.getWidth(), size.getHeight());
                Rectangle2D candidate = ScreenLabelPlacement.rectangle(anchor[0], anchor[1],
                    size.getWidth(), size.getHeight());
                if (area.contains(candidate) && !intersectsAnyObstacle(obstacles, candidate)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean intersectsAnyObstacle(List<Rectangle2D> obstacles, Rectangle2D candidate) {
        for (Rectangle2D obstacle : obstacles) {
            if (obstacle.intersects(candidate)) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void keepsPlacementsStickyAcrossAPan() {
        List<SceneNode> scene = denseScene();
        Rectangle2D area = area(1128.0, 364.0);
        Rectangle2D standIn = standIn(scene, 1.0);
        List<PlacedLabel> baseline = place(scene, 1.0, area, standIn, forced("Axiom of Choice"),
            RenderingLevel.FULL, null);
        Rectangle2D shiftedStandIn = new Rectangle2D.Double(standIn.getX() - 1.0, standIn.getY(),
            standIn.getWidth(), standIn.getHeight());

        List<PlacedLabel> panned = placeShifted(scene, 1.0, area, shiftedStandIn,
            forced("Axiom of Choice"), RenderingLevel.FULL, baseline, 1.0, 0.0);

        assertThat(countVisible(panned)).isEqualTo(11);
        for (PlacedLabel label : baseline) {
            if (label.mode() == PlacedLabel.Mode.HOVER_ONLY) {
                continue;
            }
            PlacedLabel after = find(panned, nodeName(label.endpoint()));
            assertThat(after).as(nodeName(label.endpoint())).isNotNull();
            assertThat(after.rung()).isEqualTo(label.rung());
            assertThat(after.text()).isEqualTo(label.text());
            assertThat(after.font()).isEqualTo(label.font());
        }
        assertRetainedNodeLeadersAtTheRim(panned, scene, 1.0, area, 1.0, 0.0);

        List<PlacedLabel> reapplied = placeShifted(scene, 1.0, area, shiftedStandIn,
            forced("Axiom of Choice"), RenderingLevel.FULL, panned, 1.0, 0.0);
        assertSamePlacement(panned, reapplied);
    }

    @Test
    public void recomputesTheLeaderStartOfRetainedLabelsAfterALargePan() {
        List<SceneNode> scene = denseScene();
        Rectangle2D area = area(1128.0, 364.0);
        Rectangle2D standIn = standIn(scene, 1.0);
        List<PlacedLabel> baseline = place(scene, 1.0, area, standIn, forced("Axiom of Choice"),
            RenderingLevel.FULL, null);
        double pan = 100.0;
        Rectangle2D shiftedStandIn = new Rectangle2D.Double(standIn.getX() - pan, standIn.getY(),
            standIn.getWidth(), standIn.getHeight());

        List<PlacedLabel> panned = placeShifted(scene, 1.0, area, shiftedStandIn,
            forced("Axiom of Choice"), RenderingLevel.FULL, baseline, pan, 0.0);

        assertThat(countVisible(panned)).isEqualTo(11);
        PlacedLabel theorem = find(panned, "Theorem");
        PlacedLabel beforePan = find(baseline, "Theorem");
        assertThat(theorem.leaderStart()).isPresent();
        assertThat(theorem.leaderStart().get().x())
            .as("retained leader x after pan")
            .isCloseTo(beforePan.leaderStart().get().x() - pan, within(1e-9));
        assertThat(theorem.leaderStart().get().y()).as("retained leader y after pan")
            .isCloseTo(beforePan.leaderStart().get().y(), within(1e-9));
        assertRetainedNodeLeadersAtTheRim(panned, scene, 1.0, area, pan, 0.0);
    }

    @Test
    public void recomputesTheLeaderStartOfRetainedLabelsAfterAZoomChange() {
        List<SceneNode> scene = denseScene();
        Rectangle2D area = area(1128.0, 364.0);
        List<PlacedLabel> baseline = place(scene, 1.0, area, standIn(scene, 1.0),
            forced("Axiom of Choice"), RenderingLevel.FULL, null);

        List<PlacedLabel> zoomed = placeShifted(scene, 2.0, area, standIn(scene, 2.0),
            forced("Axiom of Choice"), RenderingLevel.FULL, baseline, 0.0, 0.0);

        int leaders = 0;
        for (PlacedLabel label : zoomed) {
            if (label.mode() != PlacedLabel.Mode.HOVER_ONLY && label.leaderStart().isPresent()) {
                leaders++;
            }
        }
        assertThat(leaders).as("leader-carrying labels after zoom").isGreaterThan(0);
        assertRetainedNodeLeadersAtTheRim(zoomed, scene, 2.0, area, 0.0, 0.0);
    }

    @Test
    public void retainsASlotForAOnePixelMove() {
        List<SceneNode> scene = denseScene();
        Rectangle2D area = area(200.0, 130.0);
        List<PlacedLabel> baseline = place(scene, 1.0, area, standInIn(scene, 1.0, area),
            forced("Axiom of Choice"), RenderingLevel.FULL, null);

        assertRow(placedIn(baseline, area, 200.0, 130.0), area,
            "Axiom of Choice", "ABOVE", 12, "Axiom of Choice", -17.0, -56.172057,
            91.104675, 16.344114);
        assertRow(placedIn(baseline, area, 200.0, 130.0), area, "Theorem", "BELOW_FAR", 9,
            "Theorem", -51.0, 16.129043, 38.295258, 12.258085);
        assertRow(placedIn(baseline, area, 200.0, 130.0), area, "Pairing", "LEFT", 9,
            "Pairing", -79.976112, 0.0, 29.952225, 12.258085);
        assertRow(placedIn(baseline, area, 200.0, 130.0), area, "Infinity", "RIGHT", 9,
            "Infinity", 79.877113, 0.0, 29.754227, 12.258085);
        assertRow(placedIn(baseline, area, 200.0, 130.0), area, "Separation", "BELOW", 12,
            "Separation", -51.0, 56.172057, 61.260422, 16.344114);
        assertRow(placedIn(baseline, area, 200.0, 130.0), area, "Comprehension", "BELOW", 9,
            "Comprehension", 17.0, 54.129043, 67.716476, 12.258085);

        List<SceneNode> movedScene = moved(scene, "Infinity", 51.0, -1.0);
        List<PlacedLabel> again = place(movedScene, 1.0, area, standInIn(scene, 1.0, area),
            forced("Axiom of Choice"), RenderingLevel.FULL, baseline);

        PlacedLabel infinity = find(again, "Infinity");
        assertThat(slotOfAt(infinity, 51.0 + 100.0, -1.0 + 65.0, 8.0)).isEqualTo("RIGHT");
        assertThat(infinity.font().getSize()).isEqualTo(9);
        assertThat(infinity.anchorX() - 100.0).isCloseTo(79.877113, within(ELLIPSIS_TOLERANCE));
        assertThat(infinity.anchorY() - 65.0).isCloseTo(-1.0, within(ELLIPSIS_TOLERANCE));
        assertThat(infinity.width()).isCloseTo(29.754227, within(ELLIPSIS_TOLERANCE));
        assertThat(infinity.height()).isCloseTo(12.258085, within(ELLIPSIS_TOLERANCE));
        for (PlacedLabel label : baseline) {
            if ("Infinity".equals(nodeName(label.endpoint()))
                    || label.mode() == PlacedLabel.Mode.HOVER_ONLY) {
                continue;
            }
            PlacedLabel after = find(again, nodeName(label.endpoint()));
            assertThat(after.anchorX()).isEqualTo(label.anchorX());
            assertThat(after.anchorY()).isEqualTo(label.anchorY());
            assertThat(after.text()).isEqualTo(label.text());
            assertThat(after.font()).isEqualTo(label.font());
        }
        assertThat(labelLabelCollisions(again)).isZero();
        assertThat(discCollisionsIn(again, movedScene, 1.0, area)).isZero();
        assertSamePlacement(again, place(movedScene, 1.0, area, standInIn(scene, 1.0, area),
            forced("Axiom of Choice"), RenderingLevel.FULL, again));
    }

    @Test
    public void invalidatesAStaleSlotAndReLadders() {
        List<SceneNode> scene = new ArrayList<SceneNode>();
        scene.add(new SceneNode("Alpha", 8.0, true, 0.0, 0.0));
        scene.add(new SceneNode("Beta", 8.0, false, -40.0, 0.0));
        Rectangle2D area = area(400.0, 300.0);
        List<PlacedLabel> baseline = place(scene, 1.0, area, null, forced("Alpha"),
            RenderingLevel.FULL, null);

        assertRow(baseline, area, "Alpha", "ABOVE", 12, "Alpha", 0.0, -22.172057,
            32.292221, 16.344114);
        assertRow(baseline, area, "Beta", "ABOVE", 12, "Beta", -40.0, -22.172057,
            25.632172, 16.344114);

        List<SceneNode> movedScene = moved(scene, "Beta", 0.0, -35.0);
        List<PlacedLabel> again = place(movedScene, 1.0, area, null, forced("Alpha"),
            RenderingLevel.FULL, baseline);

        assertThat(slotOfAt(find(again, "Alpha"), 200.0, 150.0, 8.0)).isEqualTo("BELOW");
        assertThat(find(again, "Alpha").anchorY() - 150.0).isCloseTo(22.172057, within(ELLIPSIS_TOLERANCE));
        assertThat(find(again, "Alpha").width()).isCloseTo(32.292221, within(ELLIPSIS_TOLERANCE));
        assertThat(slotOfAt(find(again, "Beta"), 200.0, 115.0, 8.0)).isEqualTo("ABOVE");
        assertThat(find(again, "Beta").anchorY() - 150.0).isCloseTo(-57.172057, within(ELLIPSIS_TOLERANCE));
        assertThat(find(again, "Beta").width()).isCloseTo(25.632172, within(ELLIPSIS_TOLERANCE));
        assertThat(labelLabelCollisions(again)).isZero();
        assertThat(discCollisionsIn(again, movedScene, 1.0, area)).isZero();
        assertSamePlacement(again, place(movedScene, 1.0, area, null, forced("Alpha"),
            RenderingLevel.FULL, again));
    }

    @Test
    public void keepsPaintedInkSeparatedAcrossTheZoomMatrix() {
        assertInk("dense z=0.25", denseScene(), 0.25, standIn(denseScene(), 0.25), 6,
            "Axiom of Choice", "BELOW_FAR", -4.25, 31.672057, false, 0.4958, 2.155886);
        assertInk("dense z=1.00", denseScene(), 1.0, standIn(denseScene(), 1.0), 11,
            "Axiom of Choice", "ABOVE", -17.0, -56.172057, false, 0.5156, 1.655886);
        assertInk("dense z=2.00", denseScene(), 2.0, standIn(denseScene(), 2.0), 11,
            "Axiom of Choice", "ABOVE", -34.0, -98.172057, true, 0.7578, 19.921654);
        assertInk("long z=1 none", longScene(), 1.0, null, 6,
            "Well-Ordering Theorem of Choice and Regularity", "ABOVE", -22.0, -22.172057, true,
            0.4793, 7.655886);
        assertInk("long z=1 stand-in", longScene(), 1.0, standIn(longScene(), 1.0), 6,
            "Well-Ordering Theorem of Choice and Regularity", "ABOVE", -22.0, -22.172057, true,
            0.2151, 5.655886);
    }

    private static void assertInk(String tag, List<SceneNode> scene, double zoom, Rectangle2D standIn,
            int expectedPlaced, String forcedName, String forcedSlot, double forcedX, double forcedY,
            boolean forcedBaseSlot, double expectedOverhang, double expectedMinGap) {
        Rectangle2D area = area(1128.0, 364.0);
        List<PlacedLabel> placed = place(scene, zoom, area, standIn, forced(forcedName),
            RenderingLevel.FULL, null);
        assertThat(countVisible(placed)).as(tag + " placed").isEqualTo(expectedPlaced);
        PlacedLabel forcedLabel = find(placed, forcedName);
        assertThat(slotOf(forcedLabel, scene, zoom)).as(tag + " forced slot").isEqualTo(forcedSlot);
        assertThat(forcedLabel.anchorX() - area.getWidth() * 0.5).as(tag + " forced x")
            .isCloseTo(forcedX, within(1e-4));
        assertThat(forcedLabel.anchorY() - area.getHeight() * 0.5).as(tag + " forced y")
            .isCloseTo(forcedY, within(1e-4));
        assertThat(forcedLabel.forcedAtBaseSlot()).as(tag + " forced base").isEqualTo(forcedBaseSlot);

        int width = (int) area.getWidth();
        int height = (int) area.getHeight();
        List<boolean[]> masks = new ArrayList<boolean[]>();
        double maxOverhang = 0.0;
        for (PlacedLabel label : placed) {
            if (label.mode() == PlacedLabel.Mode.HOVER_ONLY) {
                continue;
            }
            boolean[] mask = inkMask(label, width, height);
            masks.add(mask);
            double[] overhang = overhang(label, mask, width, height);
            maxOverhang = Math.max(maxOverhang,
                Math.max(Math.max(overhang[0], overhang[1]), Math.max(overhang[2], overhang[3])));
        }
        assertThat(maxOverhang).as(tag + " max overhang").isCloseTo(expectedOverhang, within(1e-4));
        assertThat(minRectGap(placed)).as(tag + " min gap").isCloseTo(expectedMinGap, within(1e-6));

        for (int first = 0; first < masks.size(); first++) {
            for (int second = first + 1; second < masks.size(); second++) {
                assertThat(overlaps(masks.get(first), masks.get(second)))
                    .as(tag + " ink pair " + first + "/" + second).isFalse();
            }
        }
        List<boolean[]> discs = new ArrayList<boolean[]>();
        for (SceneNode node : scene) {
            discs.add(discMask(node.x * zoom + width * 0.5, node.y * zoom + height * 0.5,
                Math.max(2.0, node.radius * zoom), width, height));
        }
        for (boolean[] mask : masks) {
            for (boolean[] disc : discs) {
                assertThat(overlaps(mask, disc)).as(tag + " ink/disc").isFalse();
            }
        }
    }

    @Test
    public void boundsEveryPlacedLabelByItsSlotForms() {
        assertBounds(denseScene(), 1.0, standIn(denseScene(), 1.0), 11, 77.894615, 138.704698,
            "Axiom of Choice");
        assertBounds(denseScene(), 2.0, standIn(denseScene(), 2.0), 11, 73.611911, 118.655013,
            "Axiom of Choice");
        assertBounds(longScene(), 1.0, standIn(longScene(), 1.0), 6, 78.656464, 143.545734,
            "Well-Ordering Theorem of Choice and Regularity");
        assertBounds(longScene(), 2.0, standIn(longScene(), 2.0), 5, 86.656464, 151.533443,
            "Well-Ordering Theorem of Choice and Regularity");
    }

    private static void assertBounds(List<SceneNode> scene, double zoom, Rectangle2D standIn,
            int expectedVisible, double expectedMaxLeader, double expectedMaxSupport,
            String forcedName) {
        Rectangle2D area = area(1128.0, 364.0);
        List<PlacedLabel> placed = place(scene, zoom, area, standIn, forced(forcedName),
            RenderingLevel.FULL, null);
        assertThat(countVisible(placed)).isEqualTo(expectedVisible);
        for (PlacedLabel label : placed) {
            if (label.mode() == PlacedLabel.Mode.HOVER_ONLY) {
                continue;
            }
            String slot = slotOf(label, scene, zoom);
            SceneNode node = sceneNode(scene, nodeName(label.endpoint()));
            double radius = Math.max(2.0, node.radius * zoom);
            assertThat(leader(label, scene, zoom)).as("leader " + slot)
                .isLessThanOrEqualTo(leaderForm(slot, radius, label.width(), label.height()) + 1e-6);
            assertThat(support(label, scene, zoom)).as("support " + slot)
                .isLessThanOrEqualTo(supportForm(slot, radius, label.width(), label.height()) + 1e-6);
        }
        assertThat(maxLeader(placed, scene, zoom)).isCloseTo(expectedMaxLeader, within(1e-4));
        assertThat(maxSupport(placed, scene, zoom)).isCloseTo(expectedMaxSupport, within(1e-4));
    }

    @Test
    public void promotesAHoverOnlyLabelToForcedWithoutCollisions() {
        List<SceneNode> scene = denseScene();
        Rectangle2D area = area(200.0, 130.0);
        Rectangle2D standIn = standIn(scene, 1.0);
        List<PlacedLabel> baseline = place(scene, 1.0, area, standIn, forced("Axiom of Choice"),
            RenderingLevel.FULL, null);
        assertThat(find(baseline, "Well-Ordering").mode()).isEqualTo(PlacedLabel.Mode.HOVER_ONLY);
        assertThat(find(baseline, "Comprehension").mode()).isNotEqualTo(PlacedLabel.Mode.HOVER_ONLY);

        Set<ProjectedEndpointKey> promoted = new LinkedHashSet<ProjectedEndpointKey>();
        promoted.add(ProjectedEndpointKey.ofNode(key("Axiom of Choice")));
        promoted.add(ProjectedEndpointKey.ofNode(key("Well-Ordering")));
        List<PlacedLabel> forcedResult = place(scene, 1.0, area, standIn, promoted,
            RenderingLevel.FULL, baseline);

        assertThat(slotOfAt(find(forcedResult, "Well-Ordering"), 51.0 + 100.0, 34.0 + 65.0, 8.0))
            .isEqualTo("BELOW");
        assertThat(find(forcedResult, "Well-Ordering").font().getSize()).isEqualTo(12);
        assertThat(find(forcedResult, "Well-Ordering").anchorY() - 65.0)
            .isCloseTo(56.172057, within(ELLIPSIS_TOLERANCE));
        assertThat(find(forcedResult, "Comprehension").mode()).isEqualTo(PlacedLabel.Mode.HOVER_ONLY);
        for (String name : Arrays.asList("Axiom of Choice", "Theorem", "Pairing", "Infinity",
                "Separation")) {
            assertThat(find(forcedResult, name).mode()).as(name).isNotEqualTo(PlacedLabel.Mode.HOVER_ONLY);
        }
        assertThat(labelLabelCollisions(forcedResult)).isZero();
        assertThat(discCollisionsIn(forcedResult, scene, 1.0, area)).isZero();
        assertSamePlacement(forcedResult, place(scene, 1.0, area, standIn, promoted,
            RenderingLevel.FULL, forcedResult));
    }

    @Test
    public void reusesTheCachedPlacementUntilTheKeyChanges() {
        List<SceneNode> scene = denseScene();
        Rectangle2D area = area(1128.0, 364.0);
        LabelFonts fonts = LabelFonts.from(GraphTheme.resolve(CanvasTheme.LIGHT));
        ScreenLabelPlacementCache cache = new ScreenLabelPlacementCache();
        LabelPlacementRequest request = request(scene, 1.0, area, forced("Axiom of Choice"),
            RenderingLevel.FULL);

        List<PlacedLabel> first = cache.place(request, fonts);
        List<PlacedLabel> second = cache.place(request, fonts);
        assertThat(second).isSameAs(first);

        LabelPlacementRequest rebuilt = request(scene, 1.0, area, forced("Axiom of Choice"),
            RenderingLevel.FULL);
        List<PlacedLabel> third = cache.place(rebuilt, fonts);
        assertThat(third).isNotSameAs(first);
        assertSamePlacement(first, third);

        LabelPlacementRequest forcedChanged = request(scene, 1.0, area, forced("Axiom of Choice",
            "Well-Ordering"), RenderingLevel.FULL);
        List<PlacedLabel> fourth = cache.place(forcedChanged, fonts);
        assertThat(fourth).isNotSameAs(first);
        assertThat(find(fourth, "Well-Ordering").forced()).isTrue();
        assertThat(labelLabelCollisions(fourth)).isZero();
    }

    @Test
    public void placesEmphaticEnclosureLabelsInTheHullInterior() {
        Rectangle2D area = area(1128.0, 364.0);
        PlacedLabel label = placeEnclosure("Axioms", true, area, Collections.<SceneNode>emptyList(),
            RenderingLevel.FULL).get(0);

        assertThat(label.mode()).isEqualTo(PlacedLabel.Mode.INTERIOR);
        assertThat(label.font().getSize()).isEqualTo(15);
        assertThat(label.anchorX() - area.getWidth() * 0.5).isCloseTo(0.0, within(1e-6));
        assertThat(label.anchorY() - area.getHeight() * 0.5).isCloseTo(0.0, within(1e-6));
        assertThat(label.width()).isCloseTo(55.065384, within(1e-6));
        assertThat(label.height()).isCloseTo(20.430143, within(1e-6));
        assertThat(label.emphaticAtAnchor()).isFalse();
        assertThat(label.leaderStart()).isEmpty();
    }

    @Test
    public void placesEmphaticEnclosureLabelsOnTheLeastPopulatedArc() {
        Rectangle2D area = area(1128.0, 364.0);
        List<SceneNode> obstacle = Collections.singletonList(
            new SceneNode("Theorem", 8.0, false, 0.0, 0.0));
        PlacedLabel label = placeEnclosure("Axioms", true, area, obstacle, RenderingLevel.FULL).get(0);

        assertThat(label.mode()).isEqualTo(PlacedLabel.Mode.ARC);
        assertThat(label.anchorX() - area.getWidth() * 0.5).isCloseTo(0.0, within(1e-6));
        assertThat(label.anchorY() - area.getHeight() * 0.5).isCloseTo(-38.784928, within(1e-6));
        assertThat(label.width()).isCloseTo(55.065384, within(1e-6));
        assertThat(label.height()).isCloseTo(20.430143, within(1e-6));
        assertThat(label.leaderStart()).isEmpty();
    }

    @Test
    public void placesEmphaticEnclosureLabelsExternallyWithALeader() {
        Rectangle2D area = area(320.0, 320.0);
        PlacedLabel label = placeEnclosure("Basic Definitions and Theorems", true, area,
            Collections.<SceneNode>emptyList(), RenderingLevel.FULL).get(0);

        assertThat(label.mode()).isEqualTo(PlacedLabel.Mode.EXTERNAL);
        assertThat(label.anchorX() - area.getWidth() * 0.5).isCloseTo(0.0, within(1e-6));
        assertThat(label.anchorY() - area.getHeight() * 0.5).isCloseTo(-64.215072, within(1e-6));
        assertThat(label.leaderStart()).isPresent();
        assertThat(label.leaderStart().get().x() - area.getWidth() * 0.5).isCloseTo(0.0, within(1e-6));
        assertThat(label.leaderStart().get().y() - area.getHeight() * 0.5)
            .isCloseTo(-50.0, within(1e-6));
    }

    @Test(timeout = 5000)
    public void forcesAnEmphaticEnclosureLabelAtTheAnchorWhenNoSlotIsAccepted() {
        Rectangle2D area = area(120.0, 120.0);
        PlacedLabel label = placeEnclosure("Basic Definitions and Theorems", true, area,
            Collections.<SceneNode>emptyList(), RenderingLevel.FULL).get(0);

        assertThat(label.mode()).isEqualTo(PlacedLabel.Mode.INTERIOR);
        assertThat(label.emphaticAtAnchor()).isTrue();
        assertThat(label.anchorX() - area.getWidth() * 0.5).isCloseTo(0.0, within(1e-6));
        assertThat(label.anchorY() - area.getHeight() * 0.5).isCloseTo(0.0, within(1e-6));
        assertThat(label.leaderStart()).isEmpty();
    }

    @Test(timeout = 5000)
    public void hidesASubtleEnclosureLabelWhenNoExternalSlotIsAccepted() {
        Rectangle2D area = area(120.0, 120.0);
        PlacedLabel label = placeEnclosure("Basic Definitions and Theorems", false, area,
            Collections.<SceneNode>emptyList(), RenderingLevel.FULL).get(0);

        assertThat(label.mode()).isEqualTo(PlacedLabel.Mode.HOVER_ONLY);
        assertThat(label.anchorX() - area.getWidth() * 0.5).isCloseTo(0.0, within(1e-6));
        assertThat(label.anchorY() - area.getHeight() * 0.5).isCloseTo(0.0, within(1e-6));
        assertThat(label.leaderStart()).isEmpty();
    }

    @Test
    public void keepsHullGeometryUnchangedAcrossEnclosurePlacement() {
        Rectangle2D area = area(320.0, 320.0);
        LabelPlacementRequest request = enclosureRequest("Basic Definitions and Theorems", true, area,
            Collections.<SceneNode>emptyList(), RenderingLevel.FULL);
        Map<EnclosureHullKey, HullGeometry> before =
            new LinkedHashMap<EnclosureHullKey, HullGeometry>(request.geometry().hulls());

        new ScreenLabelPlacement().place(request, null, fonts(), Collections.<Rectangle2D>emptyList());

        assertThat(request.geometry().hulls()).isEqualTo(before);
        assertThat(request.geometry().hulls().get(firstHullKey())).isEqualTo(before.get(firstHullKey()));
    }

    @Test
    public void placesEmphaticAndSubtleEnclosureLabelsThroughTheLevelFilter() {
        Rectangle2D area = area(1128.0, 364.0);
        List<SceneNode> nodes = Collections.singletonList(
            new SceneNode("Theorem", 8.0, false, 0.0, -80.0));

        List<PlacedLabel> emphatic = placeEnclosure("Axioms", true, area, nodes, RenderingLevel.FULL);
        assertThat(emphatic).hasSize(2);
        PlacedLabel enclosure = findEnclosure(emphatic);
        assertThat(enclosure.mode()).isEqualTo(PlacedLabel.Mode.INTERIOR);
        assertThat(enclosure.font().getSize()).isEqualTo(15);
        assertThat(enclosure.width()).isCloseTo(55.065384, within(1e-6));
        assertThat(enclosure.height()).isCloseTo(20.430143, within(1e-6));
        assertThat(enclosure.emphaticAtAnchor()).isFalse();
        assertThat(enclosure.leaderStart()).isEmpty();
        PlacedLabel node = find(emphatic, "Theorem");
        assertThat(node.font().getSize()).isEqualTo(12);
        assertThat(node.width()).isCloseTo(51.060364, within(1e-6));
        assertThat(node.height()).isCloseTo(16.344114, within(1e-6));
        assertThat(node.anchorX() - area.getWidth() * 0.5).isCloseTo(0.0, within(1e-6));
        assertThat(node.anchorY() - area.getHeight() * 0.5).isCloseTo(-102.172057, within(1e-6));
        assertThat(enclosure.bounds().intersects(node.bounds())).isFalse();

        List<PlacedLabel> overTarget = placeEnclosure("Axioms", true, area, nodes,
            RenderingLevel.OVER_TARGET);
        assertThat(overTarget).hasSize(1);
        assertThat(overTarget.get(0).endpoint().isEnclosure()).isTrue();

        List<PlacedLabel> subtle = placeEnclosure("Axioms", false, area, nodes, RenderingLevel.FULL);
        assertThat(subtle).hasSize(2);
        assertThat(findEnclosure(subtle).font().getSize()).isEqualTo(12);
        assertThat(findEnclosure(subtle).width()).isCloseTo(41.340302, within(1e-6));
        assertThat(findEnclosure(subtle).height()).isCloseTo(16.344114, within(1e-6));
        assertThat(placeEnclosure("Axioms", false, area, nodes, RenderingLevel.OVER_TARGET)).isEmpty();
    }

    @Test
    public void keepsTheEmphaticEnclosureInteriorWhenASubtleEnclosureComesFirst() {
        Rectangle2D area = area(1128.0, 364.0);
        List<PlacedLabel> placed = new ScreenLabelPlacement().place(twoEnclosureRequest(area), null,
            fonts(), Collections.<Rectangle2D>emptyList());

        PlacedLabel emphatic = findEnclosureLabel(placed, "Axioms");
        assertThat(emphatic.mode()).isEqualTo(PlacedLabel.Mode.INTERIOR);
        assertThat(emphatic.font().getSize()).isEqualTo(15);
        assertThat(emphatic.anchorX() - area.getWidth() * 0.5).isCloseTo(0.0, within(1e-6));
        assertThat(emphatic.anchorY() - area.getHeight() * 0.5).isCloseTo(0.0, within(1e-6));
        assertThat(emphatic.width()).isCloseTo(55.065384, within(1e-6));
        assertThat(emphatic.height()).isCloseTo(20.430143, within(1e-6));
        assertThat(emphatic.leaderStart()).isEmpty();

        PlacedLabel subtle = findEnclosureLabel(placed, "Subtle");
        assertThat(subtle.mode()).isEqualTo(PlacedLabel.Mode.ARC);
        assertThat(subtle.font().getSize()).isEqualTo(12);
        assertThat(subtle.anchorX() - area.getWidth() * 0.5).isCloseTo(0.0, within(1e-6));
        assertThat(subtle.anchorY() - area.getHeight() * 0.5).isCloseTo(-40.827943, within(1e-6));
        assertThat(subtle.leaderStart()).isEmpty();
    }

    @Test
    public void skipsSuppressedEnclosureLabels() {
        Rectangle2D area = area(1128.0, 364.0);
        LabelPlacementRequest request = enclosureRequest("Axioms", BoundaryTier.SUPPRESSED, area,
            Collections.<SceneNode>emptyList(), RenderingLevel.FULL);

        List<PlacedLabel> placed = new ScreenLabelPlacement().place(request, null, fonts(),
            Collections.<Rectangle2D>emptyList());

        assertThat(placed).isEmpty();
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
        return request(scene, zoom, area, forced, level, 0.0, 0.0);
    }

    static LabelPlacementRequest request(List<SceneNode> scene, double zoom, Rectangle2D area,
            Set<ProjectedEndpointKey> forced, RenderingLevel level, double centerX, double centerY) {
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
        return LabelPlacementRequest.of(projection, graphGeometry, layoutPositions, zoom, centerX, centerY,
            area, forced, level);
    }

    static LabelFonts fonts() {
        return LabelFonts.from(GraphTheme.resolve(CanvasTheme.LIGHT));
    }

    static List<PlacedLabel> placeEnclosure(String text, boolean emphatic, Rectangle2D area,
            List<SceneNode> nodes, RenderingLevel level) {
        return new ScreenLabelPlacement().place(
            enclosureRequest(text, emphatic, area, nodes, level), null, fonts(),
            Collections.<Rectangle2D>emptyList());
    }

    static LabelPlacementRequest enclosureRequest(String text, boolean emphatic, Rectangle2D area,
            List<SceneNode> nodes, RenderingLevel level) {
        return enclosureRequest(text, emphatic ? BoundaryTier.EMPHATIC : BoundaryTier.SUBTLE, area, nodes,
            level);
    }

    static LabelPlacementRequest enclosureRequest(String text, BoundaryTier tier, Rectangle2D area,
            List<SceneNode> nodes, RenderingLevel level) {
        EnclosureKey endpointKey = EnclosureKey.of(SourceNodeKey.persisted(
            NodeReference.of(MAP, PersistedNodeId.of("axioms"))));
        EnclosureHullKey hullKey = EnclosureHullKey.of(Collections.singletonList(endpointKey));
        List<LayoutPoint> polygon = Arrays.asList(LayoutPoint.of(-50.0, -50.0),
            LayoutPoint.of(50.0, -50.0), LayoutPoint.of(50.0, 50.0), LayoutPoint.of(-50.0, 50.0));
        List<ProjectedNodeKey> directNodes = new ArrayList<ProjectedNodeKey>();
        List<ProjectedNode> projected = new ArrayList<ProjectedNode>();
        Map<ProjectedNodeKey, NodeGeometry> geometry = new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
        Map<ProjectedNodeKey, LayoutPoint> positions = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        for (SceneNode node : nodes) {
            ProjectedNodeKey nodeKey = key(node.name);
            projected.add(ProjectedNode.of(nodeKey, SafeNodeLabel.of(node.name, node.name), "Map", false));
            geometry.put(nodeKey, NodeGeometry.of(LayoutPoint.of(node.x, node.y), node.radius));
            positions.put(nodeKey, LayoutPoint.of(node.x, node.y));
            directNodes.add(nodeKey);
        }
        ProjectedEnclosure enclosure = ProjectedEnclosure.of(hullKey,
            Collections.singletonList(endpointKey), Collections.singletonList(SafeNodeLabel.of(text, text)),
            "Map", Optional.<EnclosureHullKey>empty(), directNodes,
            Collections.<EnclosureHullKey>emptyList(), true, tier);
        GraphProjection projection = GraphProjection.structure(1L, projected,
            Collections.singletonList(enclosure));
        Map<EnclosureHullKey, HullGeometry> hulls = new LinkedHashMap<EnclosureHullKey, HullGeometry>();
        hulls.put(hullKey, HullGeometry.of(polygon, LayoutPoint.of(0.0, 0.0)));
        GraphGeometry graphGeometry = GraphGeometry.of(geometry, hulls);
        Map<EnclosureHullKey, LayoutPoint> anchors = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        anchors.put(hullKey, LayoutPoint.of(0.0, 0.0));
        return LabelPlacementRequest.of(projection, graphGeometry, LayoutPositions.of(positions, anchors),
            1.0, 0.0, 0.0, area, Collections.<ProjectedEndpointKey>emptySet(), level);
    }

    static PlacedLabel findEnclosure(List<PlacedLabel> placed) {
        for (PlacedLabel label : placed) {
            if (label.endpoint().isEnclosure()) {
                return label;
            }
        }
        return null;
    }

    static EnclosureHullKey firstHullKey() {
        EnclosureKey endpointKey = EnclosureKey.of(SourceNodeKey.persisted(
            NodeReference.of(MAP, PersistedNodeId.of("axioms"))));
        return EnclosureHullKey.of(Collections.singletonList(endpointKey));
    }

    static LabelPlacementRequest twoEnclosureRequest(Rectangle2D area) {
        EnclosureKey subtleKey = EnclosureKey.of(SourceNodeKey.persisted(
            NodeReference.of(MAP, PersistedNodeId.of("subtle"))));
        EnclosureKey emphaticKey = EnclosureKey.of(SourceNodeKey.persisted(
            NodeReference.of(MAP, PersistedNodeId.of("emphatic"))));
        EnclosureHullKey subtleHullKey = EnclosureHullKey.of(Collections.singletonList(subtleKey));
        EnclosureHullKey emphaticHullKey = EnclosureHullKey.of(Collections.singletonList(emphaticKey));
        List<LayoutPoint> polygon = Arrays.asList(LayoutPoint.of(-50.0, -50.0),
            LayoutPoint.of(50.0, -50.0), LayoutPoint.of(50.0, 50.0), LayoutPoint.of(-50.0, 50.0));
        ProjectedEnclosure subtle = ProjectedEnclosure.of(subtleHullKey,
            Collections.singletonList(subtleKey),
            Collections.singletonList(SafeNodeLabel.of("Subtle", "Subtle")), "Map",
            Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(),
            Collections.<EnclosureHullKey>emptyList(), true, BoundaryTier.SUBTLE);
        ProjectedEnclosure emphatic = ProjectedEnclosure.of(emphaticHullKey,
            Collections.singletonList(emphaticKey),
            Collections.singletonList(SafeNodeLabel.of("Axioms", "Axioms")), "Map",
            Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(),
            Collections.<EnclosureHullKey>emptyList(), true, BoundaryTier.EMPHATIC);
        GraphProjection projection = GraphProjection.structure(1L,
            Collections.<ProjectedNode>emptyList(), Arrays.asList(subtle, emphatic));
        Map<EnclosureHullKey, HullGeometry> hulls = new LinkedHashMap<EnclosureHullKey, HullGeometry>();
        hulls.put(subtleHullKey, HullGeometry.of(polygon, LayoutPoint.of(0.0, 0.0)));
        hulls.put(emphaticHullKey, HullGeometry.of(polygon, LayoutPoint.of(0.0, 0.0)));
        GraphGeometry graphGeometry = GraphGeometry.of(
            Collections.<ProjectedNodeKey, NodeGeometry>emptyMap(), hulls);
        Map<EnclosureHullKey, LayoutPoint> anchors = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        anchors.put(subtleHullKey, LayoutPoint.of(0.0, 0.0));
        anchors.put(emphaticHullKey, LayoutPoint.of(0.0, 0.0));
        return LabelPlacementRequest.of(projection, graphGeometry,
            LayoutPositions.of(Collections.<ProjectedNodeKey, LayoutPoint>emptyMap(), anchors),
            1.0, 0.0, 0.0, area, Collections.<ProjectedEndpointKey>emptySet(), RenderingLevel.FULL);
    }

    static PlacedLabel findEnclosureLabel(List<PlacedLabel> placed, String text) {
        for (PlacedLabel label : placed) {
            if (label.endpoint().isEnclosure() && text.equals(label.text())) {
                return label;
            }
        }
        return null;
    }

    static List<PlacedLabel> placeShifted(List<SceneNode> scene, double zoom, Rectangle2D area,
            Rectangle2D standIn, Set<ProjectedEndpointKey> forced, RenderingLevel level,
            List<PlacedLabel> previous, double centerX, double centerY) {
        List<Rectangle2D> obstacles = standIn == null
            ? Collections.<Rectangle2D>emptyList() : Collections.singletonList(standIn);
        return new ScreenLabelPlacement().place(
            request(scene, zoom, area, forced, level, centerX, centerY), previous,
            LabelFonts.from(GraphTheme.resolve(CanvasTheme.LIGHT)), obstacles);
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

    static String slotOf(PlacedLabel label, List<SceneNode> scene, double zoom, Rectangle2D area) {
        SceneNode node = sceneNode(scene, nodeName(label.endpoint()));
        double centerX = node.x * zoom + area.getWidth() * 0.5;
        double centerY = node.y * zoom + area.getHeight() * 0.5;
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

    static int leaderCrossings(List<PlacedLabel> placed) {
        List<double[]> segments = new ArrayList<double[]>();
        for (PlacedLabel label : placed) {
            if (label.mode() == PlacedLabel.Mode.HOVER_ONLY || !label.leaderStart().isPresent()) {
                continue;
            }
            LayoutPoint start = label.leaderStart().get();
            segments.add(new double[] { start.x(), start.y(), label.anchorX(), label.anchorY() });
        }
        int crossings = 0;
        for (int first = 0; first < segments.size(); first++) {
            for (int second = first + 1; second < segments.size(); second++) {
                if (properCross(segments.get(first), segments.get(second))) {
                    crossings++;
                }
            }
        }
        return crossings;
    }

    private static boolean properCross(double[] first, double[] second) {
        return orient(first[0], first[1], first[2], first[3], second[0], second[1])
                * orient(first[0], first[1], first[2], first[3], second[2], second[3]) < 0
            && orient(second[0], second[1], second[2], second[3], first[0], first[1])
                * orient(second[0], second[1], second[2], second[3], first[2], first[3]) < 0;
    }

    private static double orient(double ax, double ay, double bx, double by, double cx, double cy) {
        return (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
    }

    static SceneNode sceneNode(List<SceneNode> scene, String name) {
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

    static List<SceneNode> moved(List<SceneNode> scene, String name, double x, double y) {
        List<SceneNode> moved = new ArrayList<SceneNode>();
        for (SceneNode node : scene) {
            moved.add(name.equals(node.name) ? new SceneNode(node.name, node.radius, node.selected, x, y) : node);
        }
        return moved;
    }

    static Rectangle2D standInIn(List<SceneNode> scene, double zoom, Rectangle2D area) {
        Rectangle2D worldCentred = standIn(scene, zoom);
        return new Rectangle2D.Double(worldCentred.getX() - 564.0 + area.getWidth() * 0.5,
            worldCentred.getY() - 182.0 + area.getHeight() * 0.5,
            worldCentred.getWidth(), worldCentred.getHeight());
    }

    static int discCollisionsIn(List<PlacedLabel> placed, List<SceneNode> scene, double zoom,
            Rectangle2D area) {
        int collisions = 0;
        for (PlacedLabel label : placed) {
            if (label.mode() == PlacedLabel.Mode.HOVER_ONLY) {
                continue;
            }
            for (SceneNode node : scene) {
                double radius = Math.max(2.0, node.radius * zoom);
                Rectangle2D disc = new Rectangle2D.Double(node.x * zoom - radius + area.getWidth() * 0.5,
                    node.y * zoom - radius + area.getHeight() * 0.5, 2.0 * radius, 2.0 * radius);
                if (disc.intersects(label.bounds())) {
                    collisions++;
                    break;
                }
            }
        }
        return collisions;
    }

    static String slotOfAt(PlacedLabel label, double centerX, double centerY, double radius) {
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

    static void assertSamePlacement(List<PlacedLabel> expected, List<PlacedLabel> actual) {
        assertThat(actual).hasSameSizeAs(expected);
        for (PlacedLabel label : expected) {
            PlacedLabel other = find(actual, nodeName(label.endpoint()));
            assertThat(other).isNotNull();
            assertThat(other.rung()).isEqualTo(label.rung());
            assertThat(other.text()).isEqualTo(label.text());
            assertThat(other.font()).isEqualTo(label.font());
            assertThat(other.anchorX()).isEqualTo(label.anchorX());
            assertThat(other.anchorY()).isEqualTo(label.anchorY());
            assertThat(other.width()).isEqualTo(label.width());
            assertThat(other.height()).isEqualTo(label.height());
        }
    }

    static List<PlacedLabel> placedIn(List<PlacedLabel> placed, Rectangle2D area, double width,
            double height) {
        return placed;
    }

    static boolean[] inkMask(PlacedLabel label, int width, int height) {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(width, height,
            java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setFont(label.font());
            graphics.setColor(java.awt.Color.BLACK);
            Rectangle2D bounds = label.font().getStringBounds(label.text(), ScreenLabelPlacement.SCREEN_FRC);
            java.awt.FontMetrics metrics = graphics.getFontMetrics(label.font());
            double baseline = label.anchorY() + (metrics.getAscent() - metrics.getDescent()) / 2.0;
            graphics.drawString(label.text(), (float) (label.anchorX() - bounds.getWidth() / 2.0),
                (float) baseline);
        }
        finally {
            graphics.dispose();
        }
        boolean[] mask = new boolean[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    mask[y * width + x] = true;
                }
            }
        }
        return mask;
    }

    static boolean[] discMask(double centerX, double centerY, double radius, int width, int height) {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(width, height,
            java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.fill(new java.awt.geom.Ellipse2D.Double(centerX - radius, centerY - radius,
                2.0 * radius, 2.0 * radius));
        }
        finally {
            graphics.dispose();
        }
        boolean[] mask = new boolean[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    mask[y * width + x] = true;
                }
            }
        }
        return mask;
    }

    static boolean overlaps(boolean[] first, boolean[] second) {
        for (int index = 0; index < first.length; index++) {
            if (first[index] && second[index]) {
                return true;
            }
        }
        return false;
    }

    static double[] overhang(PlacedLabel label, boolean[] mask, int width, int height) {
        int minX = Integer.MAX_VALUE;
        int maxX = -1;
        int minY = Integer.MAX_VALUE;
        int maxY = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (mask[y * width + x]) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        Rectangle2D bounds = label.bounds();
        return new double[] { bounds.getMinX() - minX, maxX + 1 - bounds.getMaxX(),
            bounds.getMinY() - minY, maxY + 1 - bounds.getMaxY() };
    }

    static double minRectGap(List<PlacedLabel> placed) {
        double best = Double.MAX_VALUE;
        for (int first = 0; first < placed.size(); first++) {
            if (placed.get(first).mode() == PlacedLabel.Mode.HOVER_ONLY) {
                continue;
            }
            for (int second = first + 1; second < placed.size(); second++) {
                if (placed.get(second).mode() == PlacedLabel.Mode.HOVER_ONLY) {
                    continue;
                }
                Rectangle2D a = placed.get(first).bounds();
                Rectangle2D b = placed.get(second).bounds();
                double dx = Math.max(0.0, Math.max(a.getMinX() - b.getMaxX(), b.getMinX() - a.getMaxX()));
                double dy = Math.max(0.0, Math.max(a.getMinY() - b.getMaxY(), b.getMinY() - a.getMaxY()));
                best = Math.min(best, Math.hypot(dx, dy));
            }
        }
        return best;
    }

    static double leaderForm(String slot, double radius, double width, double height) {
        if ("ABOVE".equals(slot) || "BELOW".equals(slot)) {
            return radius + 6.0 + height / 2.0;
        }
        if ("ABOVE_FAR".equals(slot) || "BELOW_FAR".equals(slot)) {
            return radius + 30.0 + height / 2.0;
        }
        if ("RIGHT".equals(slot) || "LEFT".equals(slot)) {
            return radius + 6.0 + width / 2.0;
        }
        if ("RIGHT_FAR".equals(slot) || "LEFT_FAR".equals(slot)) {
            return radius + 30.0 + width / 2.0;
        }
        return Math.hypot(radius + 6.0 + width / 2.0, radius + 6.0 + height / 2.0);
    }

    static double supportForm(String slot, double radius, double width, double height) {
        if ("ABOVE".equals(slot) || "BELOW".equals(slot)) {
            return Math.hypot(width / 2.0, radius + 6.0 + height);
        }
        if ("ABOVE_FAR".equals(slot) || "BELOW_FAR".equals(slot)) {
            return Math.hypot(width / 2.0, radius + 30.0 + height);
        }
        if ("RIGHT".equals(slot) || "LEFT".equals(slot)) {
            return Math.hypot(radius + 6.0 + width, height / 2.0);
        }
        if ("RIGHT_FAR".equals(slot) || "LEFT_FAR".equals(slot)) {
            return Math.hypot(radius + 30.0 + width, height / 2.0);
        }
        return Math.hypot(radius + 6.0 + width, radius + 6.0 + height);
    }

    static double support(PlacedLabel label, List<SceneNode> scene, double zoom) {
        SceneNode node = sceneNode(scene, nodeName(label.endpoint()));
        double centerX = node.x * zoom + 564.0;
        double centerY = node.y * zoom + 182.0;
        return Math.hypot(Math.abs(label.anchorX() - centerX) + label.width() / 2.0,
            Math.abs(label.anchorY() - centerY) + label.height() / 2.0);
    }

    static double maxSupport(List<PlacedLabel> placed, List<SceneNode> scene, double zoom) {
        double max = 0.0;
        for (PlacedLabel label : placed) {
            if (label.mode() != PlacedLabel.Mode.HOVER_ONLY) {
                max = Math.max(max, support(label, scene, zoom));
            }
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
