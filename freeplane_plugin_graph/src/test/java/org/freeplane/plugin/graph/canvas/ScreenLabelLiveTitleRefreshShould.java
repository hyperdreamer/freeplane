package org.freeplane.plugin.graph.canvas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.freeplane.plugin.graph.canvas.ScreenLabelPlacementShould.fonts;

import java.awt.Font;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.HullGeometry;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.geometry.NodeGeometry;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.junit.Test;

public class ScreenLabelLiveTitleRefreshShould {
    private static final MapReferenceId MAP = MapReferenceId.of(
        "00000000-0000-0000-0000-000000000001");

    private static ProjectedNodeKey stableKey(final String id) {
        return ProjectedNodeKey.of(SourceNodeKey.persisted(
            NodeReference.of(MAP, PersistedNodeId.of(id))));
    }

    private static ProjectedNode node(final String id, final String fullText,
            final String displayText) {
        return ProjectedNode.of(stableKey(id), SafeNodeLabel.of(fullText, displayText), "Map", false);
    }

    private static LabelPlacementRequest request(final long generation,
            final Rectangle2D area, final Set<ProjectedEndpointKey> forced,
            final ProjectedNode[] nodes, final double[][] screenDiscs) {
        assertThat(screenDiscs.length).as("one disc per node").isEqualTo(nodes.length);
        final Map<ProjectedNodeKey, NodeGeometry> geometry =
            new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
        final Map<ProjectedNodeKey, LayoutPoint> positions =
            new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        for (int i = 0; i < nodes.length; i++) {
            assertThat(screenDiscs[i]).hasSize(3);
            final LayoutPoint center = LayoutPoint.of(
                screenDiscs[i][0] - area.getWidth() / 2.0,
                screenDiscs[i][1] - area.getHeight() / 2.0);
            geometry.put(nodes[i].key(), NodeGeometry.of(center, screenDiscs[i][2]));
            positions.put(nodes[i].key(), center);
        }
        final GraphProjection projection = GraphProjection.structure(generation, Arrays.asList(nodes),
            Collections.<ProjectedEnclosure>emptyList());
        return LabelPlacementRequest.of(projection,
            GraphGeometry.of(geometry, Collections.<EnclosureHullKey, HullGeometry>emptyMap()),
            LayoutPositions.of(positions, Collections.<EnclosureHullKey, LayoutPoint>emptyMap()),
            1.0, 0.0, 0.0, area, forced, RenderingLevel.FULL);
    }

    private static PlacedLabel findNode(final List<PlacedLabel> labels, final String id) {
        final ProjectedEndpointKey endpoint = ProjectedEndpointKey.ofNode(stableKey(id));
        for (final PlacedLabel label : labels) {
            if (label.endpoint().equals(endpoint)) {
                return label;
            }
        }
        throw new AssertionError("Missing stable endpoint " + id);
    }

    private static PlacedLabel remembered(final LabelPlacementRequest request,
            final String id, final PlacedLabel.Rung rung, final Font font,
            final boolean truncating, final ScreenLabelPlacement.Slot slot,
            final boolean forcedAtBaseSlot) {
        final ProjectedNodeKey key = stableKey(id);
        ProjectedNode found = null;
        for (final ProjectedNode candidate : request.projection().nodes()) {
            if (candidate.key().equals(key)) {
                found = candidate;
                break;
            }
        }
        assertThat(found).as("source node %s", id).isNotNull();
        final String source = found.label().displayText();
        final String text = truncating
            ? ScreenLabelPlacement.truncateTo(source, font, ScreenLabelPlacement.slotMaxWidth(slot))
            : source;
        final Rectangle2D size = ScreenLabelPlacement.screenBounds(text, font);
        final NodeGeometry geometry = request.geometry().nodes().get(key);
        final double[] anchor = ScreenLabelPlacement.slotAnchor(slot,
            request.screenX(geometry.center().x()), request.screenY(geometry.center().y()),
            Math.max(2.0, geometry.radius() * request.zoom()), size.getWidth(), size.getHeight());
        final ProjectedEndpointKey endpoint = ProjectedEndpointKey.ofNode(key);
        return new PlacedLabel(endpoint, text, font, PlacedLabel.Mode.INTERIOR, rung,
            anchor[0], anchor[1], size.getWidth(), size.getHeight(), !text.equals(source), source,
            request.forced().contains(endpoint), false, forcedAtBaseSlot, false,
            Optional.<LeaderLine>empty(), slot);
    }

    private static void assertSamePlacement(final PlacedLabel actual, final PlacedLabel expected) {
        assertThat(actual.endpoint()).isEqualTo(expected.endpoint());
        assertThat(actual.text()).isEqualTo(expected.text());
        assertThat(actual.sourceText()).isEqualTo(expected.sourceText());
        assertThat(actual.font()).isEqualTo(expected.font());
        assertThat(actual.mode()).isEqualTo(expected.mode());
        assertThat(actual.rung()).isEqualTo(expected.rung());
        assertThat(actual.slot()).isEqualTo(expected.slot());
        assertThat(actual.truncated()).isEqualTo(expected.truncated());
        assertThat(actual.width()).isEqualTo(expected.width());
        assertThat(actual.height()).isEqualTo(expected.height());
        assertThat(actual.anchorX()).isEqualTo(expected.anchorX());
        assertThat(actual.anchorY()).isEqualTo(expected.anchorY());
        assertThat(actual.forced()).isEqualTo(expected.forced());
        assertThat(actual.emphaticAtAnchor()).isEqualTo(expected.emphaticAtAnchor());
        assertThat(actual.forcedAtBaseSlot()).isEqualTo(expected.forcedAtBaseSlot());
        assertThat(actual.fullTextSlotWasFree()).isEqualTo(expected.fullTextSlotWasFree());
        assertThat(actual.leader().isPresent()).isEqualTo(expected.leader().isPresent());
        if (actual.leader().isPresent()) {
            assertThat(actual.leader().get().start().x()).isEqualTo(expected.leader().get().start().x());
            assertThat(actual.leader().get().start().y()).isEqualTo(expected.leader().get().start().y());
            assertThat(actual.leader().get().end().x()).isEqualTo(expected.leader().get().end().x());
            assertThat(actual.leader().get().end().y()).isEqualTo(expected.leader().get().end().y());
        }
    }

    private static String repeat(final char character, final int count) {
        final char[] characters = new char[count];
        Arrays.fill(characters, character);
        return new String(characters);
    }

    private static LabelPlacementRequest openRequest(final long generation, final String title) {
        return request(generation, new Rectangle2D.Double(0, 0, 1280, 800),
            Collections.<ProjectedEndpointKey>emptySet(),
            new ProjectedNode[] {node("edited", title, title), node("bravo", "bravo", "bravo"),
                node("charlie", "charlie", "charlie"), node("delta", "delta", "delta")},
            new double[][] {{600, 400, 8}, {680, 400, 8}, {640, 360, 8}, {640, 440, 8}});
    }

    private static LabelPlacementRequest strip(final long generation, final String title,
            final boolean forced) {
        return request(generation, new Rectangle2D.Double(0, 0, 200, 50),
            forced ? Collections.singleton(ProjectedEndpointKey.ofNode(stableKey("edited")))
                : Collections.<ProjectedEndpointKey>emptySet(),
            new ProjectedNode[] {node("edited", title, title)}, new double[][] {{100, 32, 8}});
    }

    private static LabelPlacementRequest overCap(final long generation, final String title,
            final boolean forced) {
        return request(generation, new Rectangle2D.Double(0, 0, 1128, 364),
            forced ? Collections.singleton(ProjectedEndpointKey.ofNode(stableKey("edited")))
                : Collections.<ProjectedEndpointKey>emptySet(),
            new ProjectedNode[] {node("edited", title, title)}, new double[][] {{542, 182, 8}});
    }

    private static List<Rectangle2D> band() {
        return Collections.<Rectangle2D>singletonList(new Rectangle2D.Double(0, 2, 200, 2));
    }

    private static void checkStrip(final LabelFonts fonts) {
        final String a = repeat('W', 50);
        final String b = repeat('W', 20);
        assertThat(ScreenLabelPlacement.screenBounds(b, fonts.full()).getWidth()).isGreaterThan(200);
        assertThat(ScreenLabelPlacement.screenBounds(b, fonts.dense()).getWidth())
            .isLessThanOrEqualTo(200).isGreaterThan(130);
        assertThat(ScreenLabelPlacement.screenBounds(a, fonts.dense()).getWidth()).isGreaterThan(200);
        assertThat(ScreenLabelPlacement.truncateTo(a, fonts.full(), 200))
            .isEqualTo(ScreenLabelPlacement.truncateTo(b, fonts.full(), 200))
            .isEqualTo(ScreenLabelPlacement.truncateTo(repeat('W', 60), fonts.full(), 200));
    }

    private static void checkBand(final LabelFonts fonts) {
        checkStrip(fonts);
        final LabelPlacementRequest r = strip(1, repeat('W', 70), false);
        final PlacedLabel full = remembered(r, "edited", PlacedLabel.Rung.TRUNCATED_NEAR,
            fonts.full(), true, ScreenLabelPlacement.Slot.ABOVE, false);
        final PlacedLabel dense = remembered(r, "edited", PlacedLabel.Rung.TRUNCATED_DENSE_NEAR,
            fonts.dense(), true, ScreenLabelPlacement.Slot.ABOVE, false);
        assertThat(full.bounds().getMinY()).isCloseTo(1.655886, within(1e-4));
        assertThat(dense.bounds().getMinY()).isCloseTo(5.741915, within(1e-4));
        assertThat(full.bounds().intersects(band().get(0))).isTrue();
        assertThat(dense.bounds().intersects(band().get(0))).isFalse();
    }

    private static void assertFace(final PlacedLabel label, final String source,
            final String rendering, final Font font, final PlacedLabel.Rung rung,
            final ScreenLabelPlacement.Slot slot) {
        assertThat(label.sourceText()).isEqualTo(source);
        assertThat(label.text()).isEqualTo(rendering);
        assertThat(label.font()).isEqualTo(font);
        assertThat(label.rung()).isEqualTo(rung);
        assertThat(label.slot()).isEqualTo(slot);
        assertThat(label.mode()).isEqualTo(PlacedLabel.Mode.INTERIOR);
        assertThat(label.truncated()).isEqualTo(!rendering.equals(source));
        assertMeasured(label);
    }

    private static void assertMeasured(final PlacedLabel label) {
        final Rectangle2D size = ScreenLabelPlacement.screenBounds(label.text(), label.font());
        assertThat(label.width()).isCloseTo(size.getWidth(), within(1e-9));
        assertThat(label.height()).isCloseTo(size.getHeight(), within(1e-9));
        assertThat(label.bounds().getWidth()).isCloseTo(size.getWidth(), within(1e-9));
        assertThat(label.bounds().getHeight()).isCloseTo(size.getHeight(), within(1e-9));
    }

    private static void assertRenderSame(final PlacedLabel actual, final PlacedLabel expected) {
        assertThat(actual.endpoint()).isEqualTo(expected.endpoint());
        assertThat(actual.text()).isEqualTo(expected.text());
        assertThat(actual.sourceText()).isEqualTo(expected.sourceText());
        assertThat(actual.font()).isEqualTo(expected.font());
        assertThat(actual.mode()).isEqualTo(expected.mode());
        assertThat(actual.rung()).isEqualTo(expected.rung());
        assertThat(actual.slot()).isEqualTo(expected.slot());
        assertThat(actual.truncated()).isEqualTo(expected.truncated());
        assertThat(actual.width()).isEqualTo(expected.width());
        assertThat(actual.height()).isEqualTo(expected.height());
        assertThat(actual.anchorX()).isEqualTo(expected.anchorX());
        assertThat(actual.anchorY()).isEqualTo(expected.anchorY());
        assertThat(actual.emphaticAtAnchor()).isEqualTo(expected.emphaticAtAnchor());
        assertThat(actual.leader().isPresent()).isEqualTo(expected.leader().isPresent());
        if (actual.leader().isPresent()) {
            assertThat(actual.leader().get().start().x()).isEqualTo(expected.leader().get().start().x());
            assertThat(actual.leader().get().start().y()).isEqualTo(expected.leader().get().start().y());
            assertThat(actual.leader().get().end().x()).isEqualTo(expected.leader().get().end().x());
            assertThat(actual.leader().get().end().y()).isEqualTo(expected.leader().get().end().y());
        }
    }

    private static void assertClear(final PlacedLabel label, final LabelPlacementRequest request) {
        assertThat(request.placementArea().contains(label.bounds())).isTrue();
        for (final NodeGeometry geometry : request.geometry().nodes().values()) {
            final double radius = Math.max(2.0, geometry.radius() * request.zoom());
            final Rectangle2D square = new Rectangle2D.Double(
                request.screenX(geometry.center().x()) - radius,
                request.screenY(geometry.center().y()) - radius, radius * 2, radius * 2);
            assertThat(label.bounds().intersects(square)).isFalse();
        }
    }

    private static List<PlacedLabel> without(final List<PlacedLabel> labels, final String id) {
        final ProjectedEndpointKey omitted = ProjectedEndpointKey.ofNode(stableKey(id));
        final List<PlacedLabel> result = new ArrayList<PlacedLabel>();
        for (final PlacedLabel label : labels) {
            if (!label.endpoint().equals(omitted)) {
                result.add(label);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ T1

    @Test
    public void refreshesTheRetainedTitleWithAStableNodeIdentity() {
        final LabelFonts f = fonts();
        final ScreenLabelPlacement p = new ScreenLabelPlacement();
        final LabelPlacementRequest r1 = openRequest(1, "alpha");
        final LabelPlacementRequest r2 = openRequest(2, "omega renamed");
        assertThat(r1.projection().nodes().get(0).key()).isEqualTo(r2.projection().nodes().get(0).key());
        assertThat(r2.projection().generation()).isGreaterThan(r1.projection().generation());
        final List<PlacedLabel> old = p.place(r1, null, f);
        final List<PlacedLabel> current = p.place(r2, old, f);
        final PlacedLabel edited = findNode(current, "edited");
        assertThat(edited.endpoint()).isEqualTo(findNode(old, "edited").endpoint());
        assertFace(edited, "omega renamed", "omega renamed", f.full(),
            PlacedLabel.Rung.FULL_NEAR, ScreenLabelPlacement.Slot.ABOVE);
        assertThat(edited.forced()).isFalse();
        assertThat(edited.forcedAtBaseSlot()).isFalse();
        assertThat(edited.fullTextSlotWasFree()).isFalse();
        for (final PlacedLabel label : current) {
            assertThat(label.text()).isNotEqualTo("alpha");
        }
        for (final String id : Arrays.asList("bravo", "charlie", "delta")) {
            assertRenderSame(findNode(current, id), findNode(old, id));
            assertThat(findNode(current, id).forced()).isFalse();
            assertThat(findNode(current, id).forcedAtBaseSlot()).isFalse();
            assertThat(findNode(current, id).fullTextSlotWasFree()).isFalse();
        }
    }

    // ------------------------------------------------------------------ T2

    @Test
    public void refreshesTheTitleThroughTheSamePlacementCache() {
        final LabelFonts f = fonts();
        final LabelPlacementRequest r1 = openRequest(1, "alpha");
        final LabelPlacementRequest r2 = LabelPlacementRequest.of(
            openRequest(2, "omega renamed").projection(), r1.geometry(), r1.positions(),
            r1.zoom(), r1.centerX(), r1.centerY(), r1.placementArea(), r1.forced(), r1.renderingLevel());
        final ScreenLabelPlacementCache cache = new ScreenLabelPlacementCache();
        final List<PlacedLabel> first = cache.place(r1, f);
        assertThat(cache.place(r1, f)).isSameAs(first);
        assertThat(r2.geometry()).isSameAs(r1.geometry());
        assertThat(r2.positions()).isSameAs(r1.positions());
        assertThat(r2.projection().generation()).isGreaterThan(r1.projection().generation());
        final List<PlacedLabel> second = cache.place(r2, f);
        assertThat(second).isNotSameAs(first);
        assertThat(findNode(second, "edited").endpoint()).isEqualTo(findNode(first, "edited").endpoint());
        assertThat(findNode(second, "edited").text()).isEqualTo("omega renamed");
        assertThat(findNode(second, "edited").sourceText()).isEqualTo("omega renamed");
        assertThat(cache.place(r2, f)).isSameAs(second);
    }

    // ------------------------------------------------------------------ T3

    @Test
    public void keepsAnUnchangedFaceOnImmediateReplayAndAfterRetainedHistory() {
        final LabelFonts f = fonts();
        final ScreenLabelPlacement p = new ScreenLabelPlacement();
        final LabelPlacementRequest open = openRequest(1, "alpha");
        final List<PlacedLabel> initial = p.place(open, null, f);
        final List<PlacedLabel> replay = p.place(open, initial, f);
        final List<PlacedLabel> third = p.place(open, replay, f);
        for (final String id : Arrays.asList("edited", "bravo", "charlie", "delta")) {
            final PlacedLabel before = findNode(initial, id);
            final PlacedLabel after = findNode(replay, id);
            assertRenderSame(after, before);
            assertThat(before.fullTextSlotWasFree()).isTrue();
            assertThat(after.fullTextSlotWasFree()).isFalse();
            assertThat(after.forced()).isEqualTo(before.forced());
            assertThat(after.forcedAtBaseSlot()).isFalse();
            assertSamePlacement(findNode(third, id), after);
        }
        final String text = "Axiom Schema of Replacement a…";
        assertThat(ScreenLabelPlacement.screenBounds(text, f.full()).getWidth())
            .isCloseTo(193.873367, within(1e-4));
        assertThat(ScreenLabelPlacement.screenBounds(text, f.dense()).getWidth()).isLessThan(200);
        final Rectangle2D area = new Rectangle2D.Double(0, 0, 400, 50);
        final LabelPlacementRequest r1 = request(1, area, Collections.<ProjectedEndpointKey>emptySet(),
            new ProjectedNode[] {node("edited", text, text)}, new double[][] {{200, 28, 8}});
        final PlacedLabel full = remembered(r1, "edited", PlacedLabel.Rung.FULL_NEAR,
            f.full(), false, ScreenLabelPlacement.Slot.ABOVE, false);
        assertThat(area.contains(full.bounds())).isFalse();
        final List<PlacedLabel> dense = p.place(r1, null, f);
        final List<PlacedLabel> retained = p.place(r1, dense, f);
        assertFace(findNode(retained, "edited"), text, text, f.dense(),
            PlacedLabel.Rung.DENSE_NEAR, ScreenLabelPlacement.Slot.ABOVE);
        final LabelPlacementRequest r2 = request(2, area, Collections.<ProjectedEndpointKey>emptySet(),
            new ProjectedNode[] {node("edited", text, text)}, new double[][] {{200, 32, 8}});
        final PlacedLabel kept = findNode(p.place(r2, retained, f), "edited");
        assertFace(kept, text, text, f.dense(), PlacedLabel.Rung.DENSE_NEAR, ScreenLabelPlacement.Slot.ABOVE);
        final PlacedLabel expected = remembered(r2, "edited", PlacedLabel.Rung.DENSE_NEAR,
            f.dense(), false, ScreenLabelPlacement.Slot.ABOVE, false);
        assertSamePlacement(kept, expected);
        assertClear(kept, r2);
        assertFace(findNode(p.place(r2, null, f), "edited"), text, text, f.full(),
            PlacedLabel.Rung.FULL_NEAR, ScreenLabelPlacement.Slot.ABOVE);
    }

    // ------------------------------------------------------------------ T3b

    @Test
    public void keepsAValidSlotWhenAFreshPlacementWouldChooseAnother() {
        final LabelFonts f = fonts();
        final ScreenLabelPlacement p = new ScreenLabelPlacement();
        final Rectangle2D area = new Rectangle2D.Double(0, 0, 400, 300);
        final LabelPlacementRequest r1 = request(1, area, Collections.<ProjectedEndpointKey>emptySet(),
            new ProjectedNode[] {node("edited", "Alpha", "Alpha")}, new double[][] {{200, 150, 8}});
        final PlacedLabel above = remembered(r1, "edited", PlacedLabel.Rung.FULL_NEAR,
            f.full(), false, ScreenLabelPlacement.Slot.ABOVE, false);
        final PlacedLabel below = remembered(r1, "edited", PlacedLabel.Rung.FULL_NEAR,
            f.full(), false, ScreenLabelPlacement.Slot.BELOW, false);
        final PlacedLabel right = remembered(r1, "edited", PlacedLabel.Rung.FULL_NEAR,
            f.full(), false, ScreenLabelPlacement.Slot.RIGHT, false);
        final double x = 200 + above.width() / 2 - 0.25;
        final List<Rectangle2D> seeds = Arrays.<Rectangle2D>asList(
            new Rectangle2D.Double(x, above.anchorY() - 1, 0.5, 2),
            new Rectangle2D.Double(x, below.anchorY() - 1, 0.5, 2));
        assertThat(above.bounds().intersects(seeds.get(0))).isTrue();
        assertThat(below.bounds().intersects(seeds.get(1))).isTrue();
        for (final Rectangle2D seed : seeds) assertThat(right.bounds().intersects(seed)).isFalse();
        final List<PlacedLabel> old = p.place(r1, null, f, seeds);
        assertFace(findNode(old, "edited"), "Alpha", "Alpha", f.full(),
            PlacedLabel.Rung.FULL_NEAR, ScreenLabelPlacement.Slot.RIGHT);
        final LabelPlacementRequest r2 = request(2, area, Collections.<ProjectedEndpointKey>emptySet(),
            new ProjectedNode[] {node("edited", "Alpha", "Alpha")}, new double[][] {{199, 150, 8}});
        final PlacedLabel candidate = remembered(r2, "edited", PlacedLabel.Rung.FULL_NEAR,
            f.full(), false, ScreenLabelPlacement.Slot.ABOVE, false);
        assertThat(candidate.bounds().intersects(seeds.get(0))).isFalse();
        final PlacedLabel kept = findNode(p.place(r2, old, f, seeds), "edited");
        final PlacedLabel fresh = findNode(p.place(r2, null, f, seeds), "edited");
        assertFace(kept, "Alpha", "Alpha", f.full(), PlacedLabel.Rung.FULL_NEAR, ScreenLabelPlacement.Slot.RIGHT);
        assertFace(fresh, "Alpha", "Alpha", f.full(), PlacedLabel.Rung.FULL_NEAR, ScreenLabelPlacement.Slot.ABOVE);
        assertClear(kept, r2);
        assertClear(fresh, r2);
        for (final Rectangle2D seed : seeds) {
            assertThat(kept.bounds().intersects(seed)).isFalse();
            assertThat(fresh.bounds().intersects(seed)).isFalse();
        }
    }

    // ------------------------------------------------------------------ T3c

    @Test
    public void declinesAnObstructedUnchangedFaceWithoutTryingDenserRungs() {
        final LabelFonts f = fonts();
        final ScreenLabelPlacement p = new ScreenLabelPlacement();
        final Rectangle2D area = new Rectangle2D.Double(0, 0, 400, 300);
        final ProjectedNode[] nodes = {node("alpha", "Alpha", "Alpha"), node("beta", "Beta", "Beta")};
        final LabelPlacementRequest initial = request(1, area, Collections.<ProjectedEndpointKey>emptySet(),
            nodes, new double[][] {{200, 150, 8}, {160, 150, 8}});
        List<PlacedLabel> history = p.place(initial, null, f);
        List<PlacedLabel> controlHistory = history;
        assertFace(findNode(history, "alpha"), "Alpha", "Alpha", f.full(),
            PlacedLabel.Rung.FULL_NEAR, ScreenLabelPlacement.Slot.ABOVE);
        final int[] offsets = {-35, -20, 35, 20};
        final ScreenLabelPlacement.Slot[] slots = {ScreenLabelPlacement.Slot.BELOW,
            ScreenLabelPlacement.Slot.BELOW, ScreenLabelPlacement.Slot.ABOVE, ScreenLabelPlacement.Slot.ABOVE};
        for (int i = 0; i < offsets.length; i++) {
            final LabelPlacementRequest r = request(i + 2, area, Collections.<ProjectedEndpointKey>emptySet(),
                nodes, new double[][] {{200, 150, 8}, {200, 150 + offsets[i], 8}});
            if (i == 0) {
                final PlacedLabel full = remembered(r, "alpha", PlacedLabel.Rung.FULL_NEAR,
                    f.full(), false, ScreenLabelPlacement.Slot.ABOVE, false);
                final PlacedLabel dense = remembered(r, "alpha", PlacedLabel.Rung.DENSE_NEAR,
                    f.dense(), false, ScreenLabelPlacement.Slot.ABOVE, false);
                final Rectangle2D disc = new Rectangle2D.Double(192, 107, 16, 16);
                assertThat(full.bounds().getMinY() - 150).isCloseTo(-30.344114, within(1e-4));
                assertThat(dense.bounds().getMinY() - 150).isCloseTo(-26.258085, within(1e-4));
                assertThat(full.bounds().intersects(disc)).isTrue();
                assertThat(dense.bounds().intersects(disc)).isFalse();
            }
            history = p.place(r, history, f);
            controlHistory = p.place(r, without(controlHistory, "alpha"), f);
            final PlacedLabel actual = findNode(history, "alpha");
            final PlacedLabel control = findNode(controlHistory, "alpha");
            assertFace(actual, "Alpha", "Alpha", f.full(), PlacedLabel.Rung.FULL_NEAR, slots[i]);
            assertRenderSame(actual, control);
            assertThat(actual.forced()).isFalse();
            assertThat(actual.forcedAtBaseSlot()).isFalse();
            assertThat(control.fullTextSlotWasFree()).isTrue();
            assertThat(actual.fullTextSlotWasFree()).isEqualTo(i == 0 || i == 2);
        }
    }

    // ------------------------------------------------------------------ T3d

    @Test
    public void fallsBackUnderEarlierRetainedLabelObstacles() {
        final LabelFonts f = fonts();
        final ScreenLabelPlacement p = new ScreenLabelPlacement();
        final Rectangle2D area = new Rectangle2D.Double(0, 0, 300, 150);
        final ProjectedNode[] nodes = {node("alpha", "Alpha", "Alpha"), node("beta", "Beta", "Beta")};
        final LabelPlacementRequest oldRequest = request(1, area, Collections.<ProjectedEndpointKey>emptySet(),
            nodes, new double[][] {{100, 100, 8}, {130, 115, 8}});
        final LabelPlacementRequest r = request(2, area, Collections.<ProjectedEndpointKey>emptySet(),
            nodes, new double[][] {{100, 100, 8}, {130, 120, 8}});
        final PlacedLabel alpha = remembered(oldRequest, "alpha", PlacedLabel.Rung.FULL_NEAR,
            f.full(), false, ScreenLabelPlacement.Slot.RIGHT, false);
        final PlacedLabel beta = remembered(oldRequest, "beta", PlacedLabel.Rung.FULL_NEAR,
            f.full(), false, ScreenLabelPlacement.Slot.BELOW, false);
        assertThat(area.contains(beta.bounds())).isTrue();
        final PlacedLabel movedBelow = remembered(r, "beta", PlacedLabel.Rung.FULL_NEAR,
            f.full(), false, ScreenLabelPlacement.Slot.BELOW, false);
        assertThat(movedBelow.bounds().getMaxY()).isCloseTo(150.344114, within(1e-4));
        assertThat(area.contains(movedBelow.bounds())).isFalse();
        final PlacedLabel blockedAbove = remembered(r, "beta", PlacedLabel.Rung.FULL_NEAR,
            f.full(), false, ScreenLabelPlacement.Slot.ABOVE, false);
        assertThat(blockedAbove.bounds().intersects(alpha.bounds())).isTrue();
        final List<PlacedLabel> previous = Arrays.asList(alpha, beta);
        final List<PlacedLabel> replay = p.place(r, previous, f);
        final PlacedLabel actual = findNode(replay, "beta");
        assertFace(actual, "Beta", "Beta", f.full(), PlacedLabel.Rung.FULL_NEAR, ScreenLabelPlacement.Slot.RIGHT);
        assertSamePlacement(actual, findNode(p.place(r, without(previous, "beta"), f), "beta"));
        assertThat(findNode(replay, "alpha").slot()).isEqualTo(ScreenLabelPlacement.Slot.RIGHT);
        final List<PlacedLabel> fresh = p.place(r, null, f);
        assertThat(findNode(fresh, "alpha").slot()).isEqualTo(ScreenLabelPlacement.Slot.ABOVE);
        assertThat(findNode(fresh, "beta").slot()).isEqualTo(ScreenLabelPlacement.Slot.ABOVE);
        assertThat(actual.slot()).isNotEqualTo(findNode(fresh, "beta").slot());
    }

    // ------------------------------------------------------------------ T4

    @Test
    public void upgradesAShortenedTitleAtItsRememberedSlot() {
        final LabelFonts f = fonts();
        checkStrip(f);
        final ScreenLabelPlacement p = new ScreenLabelPlacement();
        final String b = repeat('W', 20);
        final List<PlacedLabel> old = p.place(strip(1, b, false), null, f);
        assertFace(findNode(old, "edited"), b, b, f.dense(),
            PlacedLabel.Rung.DENSE_NEAR, ScreenLabelPlacement.Slot.ABOVE);
        final PlacedLabel current = findNode(p.place(strip(2, "Alpha", false), old, f), "edited");
        assertFace(current, "Alpha", "Alpha", f.full(), PlacedLabel.Rung.FULL_NEAR, ScreenLabelPlacement.Slot.ABOVE);
    }

    @Test
    public void degradesAnExpandedTitleAtItsRememberedSlot() {
        final LabelFonts f = fonts();
        checkStrip(f);
        final ScreenLabelPlacement p = new ScreenLabelPlacement();
        final List<PlacedLabel> old = p.place(strip(1, "Alpha", false), null, f);
        assertFace(findNode(old, "edited"), "Alpha", "Alpha", f.full(),
            PlacedLabel.Rung.FULL_NEAR, ScreenLabelPlacement.Slot.ABOVE);
        final String b = repeat('W', 20);
        assertThat(ScreenLabelPlacement.textWidth(b, f.full())).isGreaterThan(200);
        assertThat(ScreenLabelPlacement.textWidth(b, f.dense())).isLessThanOrEqualTo(200);
        final LabelPlacementRequest r = strip(2, b, false);
        final PlacedLabel current = findNode(p.place(r, old, f), "edited");
        assertFace(current, b, b, f.dense(), PlacedLabel.Rung.DENSE_NEAR, ScreenLabelPlacement.Slot.ABOVE);
        assertClear(current, r);
    }

    @Test
    public void reLaddersWhenNoFaceFitsTheRememberedSlot() {
        final LabelFonts f = fonts();
        checkStrip(f);
        final ScreenLabelPlacement p = new ScreenLabelPlacement();
        final Rectangle2D area = new Rectangle2D.Double(0, 0, 200, 100);
        final LabelPlacementRequest oldRequest = request(1, area, Collections.<ProjectedEndpointKey>emptySet(),
            new ProjectedNode[] {node("edited", "Alpha", "Alpha")}, new double[][] {{140, 50, 8}});
        final PlacedLabel old = remembered(oldRequest, "edited", PlacedLabel.Rung.FULL_NEAR,
            f.full(), false, ScreenLabelPlacement.Slot.RIGHT, false);
        assertThat(old.bounds().getMinX()).isEqualTo(154);
        assertClear(old, oldRequest);
        final String a = repeat('W', 50);
        final LabelPlacementRequest r = request(2, area, Collections.<ProjectedEndpointKey>emptySet(),
            new ProjectedNode[] {node("edited", a, a)}, new double[][] {{140, 50, 8}});
        for (final Font font : Arrays.asList(f.full(), f.dense())) {
            assertThat(ScreenLabelPlacement.screenBounds(a, font).getWidth()).isGreaterThan(46);
            final String shortened = ScreenLabelPlacement.truncateTo(a, font, 130);
            assertThat(ScreenLabelPlacement.screenBounds(shortened, font).getWidth()).isGreaterThan(46);
        }
        final PlacedLabel current = findNode(p.place(r, Collections.singletonList(old), f), "edited");
        assertFace(current, a, ScreenLabelPlacement.truncateTo(a, f.full(),
            ScreenLabelPlacement.slotMaxWidth(ScreenLabelPlacement.Slot.LEFT)), f.full(),
            PlacedLabel.Rung.TRUNCATED_NEAR, ScreenLabelPlacement.Slot.LEFT);
        assertThat(current.slot()).isNotEqualTo(ScreenLabelPlacement.Slot.RIGHT);
        assertClear(current, r);
    }

    @Test
    public void retainsOnlyFacesFromTheRememberedSlotFamily() {
        final LabelFonts f = fonts();
        final ScreenLabelPlacement p = new ScreenLabelPlacement();
        final Rectangle2D area = new Rectangle2D.Double(0, 0, 600, 300);
        final String a = repeat('W', 50);
        final LabelPlacementRequest oldRequest = request(1, area, Collections.<ProjectedEndpointKey>emptySet(),
            new ProjectedNode[] {node("edited", a, a)}, new double[][] {{300, 150, 8}});
        final LabelPlacementRequest r = request(2, area, Collections.<ProjectedEndpointKey>emptySet(),
            new ProjectedNode[] {node("edited", "Alpha", "Alpha")}, new double[][] {{300, 150, 8}});
        final PlacedLabel near = remembered(oldRequest, "edited", PlacedLabel.Rung.TRUNCATED_NEAR,
            f.full(), true, ScreenLabelPlacement.Slot.RIGHT, false);
        final PlacedLabel far = remembered(oldRequest, "edited", PlacedLabel.Rung.TRUNCATED_DISPLACED,
            f.full(), true, ScreenLabelPlacement.Slot.RIGHT_FAR, false);
        final PlacedLabel n = findNode(p.place(r, Collections.singletonList(near), f), "edited");
        final PlacedLabel d = findNode(p.place(r, Collections.singletonList(far), f), "edited");
        assertFace(n, "Alpha", "Alpha", f.full(), PlacedLabel.Rung.FULL_NEAR, ScreenLabelPlacement.Slot.RIGHT);
        assertFace(d, "Alpha", "Alpha", f.full(), PlacedLabel.Rung.FULL_DISPLACED, ScreenLabelPlacement.Slot.RIGHT_FAR);
        assertThat(n.rung()).isIn(PlacedLabel.Rung.FULL_NEAR, PlacedLabel.Rung.DENSE_NEAR,
            PlacedLabel.Rung.TRUNCATED_NEAR, PlacedLabel.Rung.TRUNCATED_DENSE_NEAR);
        assertThat(d.rung()).isIn(PlacedLabel.Rung.FULL_DISPLACED, PlacedLabel.Rung.DENSE_DISPLACED,
            PlacedLabel.Rung.TRUNCATED_DISPLACED, PlacedLabel.Rung.TRUNCATED_DENSE_DISPLACED);
    }

    // ------------------------------------------------------------------ T4b

    @Test
    public void declinesAForcedSlotInsteadOfTakingADenseFace() {
        final LabelFonts f = fonts();
        final ScreenLabelPlacement p = new ScreenLabelPlacement();
        final Rectangle2D area = new Rectangle2D.Double(0, 0, 400, 300);
        final Set<ProjectedEndpointKey> forced = Collections.singleton(ProjectedEndpointKey.ofNode(stableKey("alpha")));
        final LabelPlacementRequest r1 = request(1, area, forced,
            new ProjectedNode[] {node("alpha", "Alpha", "Alpha"), node("beta", "Beta", "Beta")},
            new double[][] {{200, 150, 8}, {160, 150, 8}});
        final List<PlacedLabel> old = p.place(r1, null, f);
        assertThat(findNode(old, "alpha").slot()).isEqualTo(ScreenLabelPlacement.Slot.ABOVE);
        final LabelPlacementRequest r2 = request(2, area, forced,
            new ProjectedNode[] {node("alpha", "Gamma", "Gamma"), node("beta", "Beta", "Beta")},
            new double[][] {{200, 150, 8}, {200, 115, 8}});
        final Rectangle2D disc = new Rectangle2D.Double(192, 107, 16, 16);
        assertThat(remembered(r2, "alpha", PlacedLabel.Rung.FULL_NEAR, f.full(), false,
            ScreenLabelPlacement.Slot.ABOVE, false).bounds().intersects(disc)).isTrue();
        assertThat(remembered(r2, "alpha", PlacedLabel.Rung.DENSE_NEAR, f.dense(), false,
            ScreenLabelPlacement.Slot.ABOVE, false).bounds().intersects(disc)).isFalse();
        final PlacedLabel current = findNode(p.place(r2, old, f), "alpha");
        assertFace(current, "Gamma", "Gamma", f.full(), PlacedLabel.Rung.FULL_NEAR, ScreenLabelPlacement.Slot.BELOW);
        assertThat(current.forced()).isTrue();
        assertThat(current.forcedAtBaseSlot()).isFalse();
    }

    @Test
    public void rejectsDenseAndTruncatedFacesWhenPromotedToForced() {
        final LabelFonts f = fonts();
        checkStrip(f);
        final ScreenLabelPlacement p = new ScreenLabelPlacement();
        for (final int count : new int[] {20, 50}) {
            final String source = repeat('W', count);
            final List<PlacedLabel> old = p.place(strip(1, source, false), null, f);
            assertThat(findNode(old, "edited").rung()).isEqualTo(count == 20
                ? PlacedLabel.Rung.DENSE_NEAR : PlacedLabel.Rung.TRUNCATED_NEAR);
            final LabelPlacementRequest r = strip(2, source, true);
            final PlacedLabel full = remembered(r, "edited", PlacedLabel.Rung.FULL_NEAR,
                f.full(), false, ScreenLabelPlacement.Slot.ABOVE, false);
            assertThat(r.placementArea().contains(full.bounds())).isFalse();
            final PlacedLabel promoted = findNode(p.place(r, old, f), "edited");
            assertFace(promoted, source, source, f.full(), PlacedLabel.Rung.FULL_NEAR, ScreenLabelPlacement.Slot.ABOVE);
            assertThat(promoted.forced()).isTrue();
            assertThat(promoted.forcedAtBaseSlot()).isTrue();
        }
    }

    @Test
    public void keepsAnUnchangedForcedOverCapFace() {
        final LabelFonts f = fonts();
        final ScreenLabelPlacement p = new ScreenLabelPlacement();
        final String title = "Well-Ordering Theorem of Choice and Regularity";
        final LabelPlacementRequest r = overCap(1, title, true);
        final List<PlacedLabel> first = p.place(r, null, f);
        final PlacedLabel original = findNode(first, "edited");
        assertFace(original, title, title, f.full(), PlacedLabel.Rung.FULL_NEAR, ScreenLabelPlacement.Slot.ABOVE);
        assertThat(original.width()).isCloseTo(274.130005, within(1e-4)).isGreaterThan(200);
        assertThat(original.height()).isCloseTo(16.344114, within(1e-4));
        assertThat(original.anchorX()).isEqualTo(542);
        assertThat(original.anchorY()).isCloseTo(159.827943, within(1e-4));
        assertThat(original.forcedAtBaseSlot()).isTrue();
        assertClear(original, r);
        List<PlacedLabel> history = first;
        for (int i = 0; i < 2; i++) {
            history = p.place(r, history, f);
            final PlacedLabel kept = findNode(history, "edited");
            assertRenderSame(kept, original);
            assertThat(kept.forced()).isTrue();
            assertThat(kept.forcedAtBaseSlot()).isFalse();
            assertThat(kept.fullTextSlotWasFree()).isFalse();
        }
    }

    @Test
    public void rederivesAChangedForcedTitleWithoutApplyingTheSlotCap() {
        final LabelFonts f = fonts();
        final ScreenLabelPlacement p = new ScreenLabelPlacement();
        final String oldTitle = "Well-Ordering Theorem of Choice and Regularity";
        final LabelPlacementRequest first = overCap(1, oldTitle, true);
        final List<PlacedLabel> initial = p.place(first, null, f);
        final List<PlacedLabel> retained = p.place(first, initial, f);
        assertThat(findNode(initial, "edited").forcedAtBaseSlot()).isTrue();
        assertThat(findNode(retained, "edited").forcedAtBaseSlot()).isFalse();
        final String source = oldTitle + "!";
        final LabelPlacementRequest r = overCap(2, source, true);
        final PlacedLabel current = findNode(p.place(r, retained, f), "edited");
        assertFace(current, source, source, f.full(), PlacedLabel.Rung.FULL_NEAR, ScreenLabelPlacement.Slot.ABOVE);
        assertThat(current.width()).isGreaterThan(200);
        assertClear(current, r);
        assertThat(current.forced()).isTrue();
        assertThat(current.forcedAtBaseSlot()).isFalse();
    }

    // ------------------------------------------------------------------ T4c

    @Test
    public void detectsATitleEditHiddenByAnIdenticalTruncation() {
        final LabelFonts f = fonts();
        checkStrip(f);
        final ScreenLabelPlacement p = new ScreenLabelPlacement();
        final String a = repeat('W', 50);
        final String b = repeat('W', 20);
        final List<PlacedLabel> old = p.place(strip(1, a, false), null, f);
        final PlacedLabel previous = findNode(old, "edited");
        assertFace(previous, a, ScreenLabelPlacement.truncateTo(a, f.full(), 200), f.full(),
            PlacedLabel.Rung.TRUNCATED_NEAR, ScreenLabelPlacement.Slot.ABOVE);
        assertThat(previous.text()).isNotEqualTo(previous.sourceText())
            .isEqualTo(ScreenLabelPlacement.truncateTo(b, f.full(), 200));
        final PlacedLabel current = findNode(p.place(strip(2, b, false), old, f), "edited");
        assertFace(current, b, b, f.dense(), PlacedLabel.Rung.DENSE_NEAR, ScreenLabelPlacement.Slot.ABOVE);
    }

    @Test
    public void recordsTheDisplaySourceOnTruncatedAndRetainedOutputs() {
        final LabelFonts f = fonts();
        checkBand(f);
        final ScreenLabelPlacement p = new ScreenLabelPlacement();
        final String a3 = repeat('W', 70);
        final String a2 = repeat('W', 60);
        final List<PlacedLabel> first = p.place(strip(1, a3, false), null, f, band());
        final List<PlacedLabel> second = p.place(strip(2, a2, false), first, f, band());
        assertFace(findNode(first, "edited"), a3, ScreenLabelPlacement.truncateTo(a3, f.dense(), 200),
            f.dense(), PlacedLabel.Rung.TRUNCATED_DENSE_NEAR, ScreenLabelPlacement.Slot.ABOVE);
        assertFace(findNode(second, "edited"), a2, ScreenLabelPlacement.truncateTo(a2, f.dense(), 200),
            f.dense(), PlacedLabel.Rung.TRUNCATED_DENSE_NEAR, ScreenLabelPlacement.Slot.ABOVE);
        final LabelPlacementRequest thirdRequest = strip(3, a2, false);
        final PlacedLabel third = findNode(p.place(thirdRequest, second, f), "edited");
        assertSamePlacement(third, findNode(second, "edited"));
        assertThat(third.text()).isNotEqualTo(third.sourceText());
        assertThat(findNode(p.place(thirdRequest, null, f), "edited").rung())
            .isEqualTo(PlacedLabel.Rung.TRUNCATED_NEAR);
    }

    @Test
    public void clearsTruncationWhenTheTitleBecomesItsPreviousRendering() {
        final LabelFonts f = fonts();
        final ScreenLabelPlacement p = new ScreenLabelPlacement();
        final List<PlacedLabel> old = p.place(strip(1, repeat('W', 50), false), null, f);
        final PlacedLabel previous = findNode(old, "edited");
        assertThat(previous.truncated()).isTrue();
        assertThat(previous.text()).contains("…");
        final String title = previous.text();
        final PlacedLabel current = findNode(p.place(strip(2, title, false), old, f), "edited");
        assertFace(current, title, title, f.full(), PlacedLabel.Rung.FULL_NEAR, ScreenLabelPlacement.Slot.ABOVE);
        assertThat(current.truncated()).isFalse();
    }

    @Test
    public void recordsDisplayTextWhenProjectionFullTextDiffers() {
        final LabelFonts f = fonts();
        final String prefix = repeat('i', 77);
        final String display = prefix + "...";
        final String full = prefix + "hidden-tail-A";
        assertThat(display.codePointCount(0, display.length())).isEqualTo(80);
        assertThat(full.codePointCount(0, full.length())).isGreaterThan(80);
        final LabelPlacementRequest r = request(1, new Rectangle2D.Double(0, 0, 600, 300),
            Collections.<ProjectedEndpointKey>emptySet(),
            new ProjectedNode[] {node("edited", full, display)}, new double[][] {{300, 150, 8}});
        final ScreenLabelPlacement p = new ScreenLabelPlacement();
        final List<PlacedLabel> first = p.place(r, null, f);
        final PlacedLabel original = findNode(first, "edited");
        final PlacedLabel replay = findNode(p.place(r, first, f), "edited");
        assertThat(original.sourceText()).isEqualTo(display).isNotEqualTo(full);
        assertThat(replay.sourceText()).isEqualTo(display).isNotEqualTo(full);
    }

    @Test
    public void recordsSourceTextAtAllPlacementConstructionSites() {
        final LabelFonts f = fonts();
        final ScreenLabelPlacement p = new ScreenLabelPlacement();
        final String a = repeat('W', 50);
        final LabelPlacementRequest r = strip(1, a, false);
        final List<PlacedLabel> ladder = p.place(r, null, f);
        final PlacedLabel truncated = findNode(ladder, "edited");
        assertThat(truncated.truncated()).isTrue();
        assertThat(truncated.sourceText()).isEqualTo(a).isNotEqualTo(truncated.text());
        final PlacedLabel retained = findNode(p.place(r, ladder, f), "edited");
        assertThat(retained.sourceText()).isEqualTo(a).isNotEqualTo(retained.text());
        final PlacedLabel base = findNode(p.place(strip(2, a, true), ladder, f), "edited");
        assertThat(base.forcedAtBaseSlot()).isTrue();
        assertThat(base.sourceText()).isEqualTo(a);
        final LabelPlacementRequest tiny = request(1, new Rectangle2D.Double(0, 0, 4, 4),
            Collections.<ProjectedEndpointKey>emptySet(),
            new ProjectedNode[] {node("edited", "Alpha", "Alpha")}, new double[][] {{2, 2, 8}});
        final PlacedLabel hover = findNode(p.place(tiny, null, f), "edited");
        assertThat(hover.mode()).isEqualTo(PlacedLabel.Mode.HOVER_ONLY);
        assertThat(hover.sourceText()).isEqualTo("Alpha");
        final PlacedLabel enclosure = ScreenLabelPlacementShould.findEnclosure(
            ScreenLabelPlacementShould.placeEnclosure("Enclosure display", false,
                new Rectangle2D.Double(0, 0, 1280, 800),
                Collections.<ScreenLabelPlacementShould.SceneNode>emptyList(), RenderingLevel.FULL));
        assertThat(enclosure).isNotNull();
        assertThat(enclosure.sourceText()).isEqualTo("Enclosure display");
    }

    // ------------------------------------------------------------------ T4d

    @Test
    public void keepsAnOverCapFaceAfterDemotionAndOnTheNextReplay() {
        final LabelFonts f = fonts();
        final ScreenLabelPlacement p = new ScreenLabelPlacement();
        final String title = "Well-Ordering Theorem of Choice and Regularity";
        final List<PlacedLabel> first = p.place(overCap(1, title, true), null, f);
        final PlacedLabel forced = findNode(first, "edited");
        assertThat(forced.forced()).isTrue();
        assertThat(forced.forcedAtBaseSlot()).isTrue();
        final LabelPlacementRequest r = overCap(2, title, false);
        List<PlacedLabel> history = first;
        for (int i = 0; i < 2; i++) {
            history = p.place(r, history, f);
            final PlacedLabel current = findNode(history, "edited");
            assertRenderSame(current, forced);
            assertThat(current.forced()).isFalse();
            assertThat(current.forcedAtBaseSlot()).isFalse();
            assertThat(current.fullTextSlotWasFree()).isFalse();
            assertThat(current.width()).isGreaterThan(200);
            assertThat(current.truncated()).isFalse();
            assertThat(current.text()).isEqualTo(title);
            assertClear(current, r);
        }
    }

    // ------------------------------------------------------------------ T4e

    @Test
    public void keepsAnUnchangedTruncatedFaceWhenSpaceBecomesAvailable() {
        final LabelFonts f = fonts();
        checkBand(f);
        final ScreenLabelPlacement p = new ScreenLabelPlacement();
        final String a3 = repeat('W', 70);
        final String a2 = repeat('W', 60);
        final List<PlacedLabel> first = p.place(strip(1, a3, false), null, f, band());
        final List<PlacedLabel> shortened = p.place(strip(2, a2, false), first, f, band());
        final PlacedLabel before = findNode(shortened, "edited");
        assertFace(before, a2, ScreenLabelPlacement.truncateTo(a2, f.dense(), 200), f.dense(),
            PlacedLabel.Rung.TRUNCATED_DENSE_NEAR, ScreenLabelPlacement.Slot.ABOVE);
        final LabelPlacementRequest r = strip(3, a2, false);
        final PlacedLabel current = findNode(p.place(r, shortened, f), "edited");
        assertSamePlacement(current, before);
        assertClear(current, r);
        final PlacedLabel fresh = findNode(p.place(r, null, f), "edited");
        assertFace(fresh, a2, ScreenLabelPlacement.truncateTo(a2, f.full(), 200), f.full(),
            PlacedLabel.Rung.TRUNCATED_NEAR, ScreenLabelPlacement.Slot.ABOVE);
        assertClear(fresh, r);
    }

    // ------------------------------------------------------------------ T4f

    @Test
    public void advancesTheSourceWhenTwoTitlesRenderTheSameTruncation() {
        final LabelFonts f = fonts();
        checkStrip(f);
        final ScreenLabelPlacement p = new ScreenLabelPlacement();
        final String a = repeat('W', 50);
        final String a2 = repeat('W', 60);
        assertThat(a).isNotEqualTo(a2);
        for (final Font font : Arrays.asList(f.full(), f.dense())) {
            assertThat(ScreenLabelPlacement.textWidth(a, font)).isGreaterThan(200);
            assertThat(ScreenLabelPlacement.textWidth(a2, font)).isGreaterThan(200);
        }
        final List<PlacedLabel> first = p.place(strip(1, a, false), null, f);
        final PlacedLabel before = findNode(first, "edited");
        final PlacedLabel current = findNode(p.place(strip(2, a2, false), first, f), "edited");
        assertThat(current.rung()).isEqualTo(before.rung()).isEqualTo(PlacedLabel.Rung.TRUNCATED_NEAR);
        assertThat(current.text()).isEqualTo(before.text());
        assertThat(before.sourceText()).isEqualTo(a).isNotEqualTo(before.text());
        assertThat(current.sourceText()).isEqualTo(a2).isNotEqualTo(current.text());
        assertThat(current.truncated()).isTrue();
    }

    // ------------------------------------------------------------------ T4g

    @Test
    public void ignoresAnEditConfinedToTheProjectionHiddenTail() {
        final LabelFonts fixture = fonts();
        final LabelFonts f = LabelFonts.of(fixture.full().deriveFont(9f),
            fixture.dense().deriveFont(6.75f), fixture.emphatic().deriveFont(9f));
        final String prefix = repeat('i', 77);
        final String display = prefix + "...";
        final String full1 = prefix + "hidden-tail-A";
        final String full2 = prefix + "hidden-tail-B";
        assertThat(display.codePointCount(0, display.length())).isEqualTo(80);
        assertThat(full1.codePointCount(0, full1.length())).isGreaterThan(80);
        assertThat(full2.codePointCount(0, full2.length())).isGreaterThan(80);
        assertThat(full1).isNotEqualTo(full2);
        assertThat(ScreenLabelPlacement.screenBounds(display, f.full()).getWidth()).isLessThanOrEqualTo(200);
        final double hf = ScreenLabelPlacement.screenBounds(display, f.full()).getHeight();
        final double hd = ScreenLabelPlacement.screenBounds(display, f.dense()).getHeight();
        assertThat(hf).isGreaterThan(hd);
        final Rectangle2D area = new Rectangle2D.Double(0, 0, 400, 40);
        final ProjectedNode n1 = node("edited", full1, display);
        final ProjectedNode n2 = node("edited", full2, display);
        assertThat(n1.key()).isEqualTo(n2.key());
        assertThat(n1.label().displayText()).isEqualTo(n2.label().displayText());
        assertThat(n1.label().fullText()).isNotEqualTo(n2.label().fullText());
        final LabelPlacementRequest r1 = request(1, area, Collections.<ProjectedEndpointKey>emptySet(),
            new ProjectedNode[] {n1}, new double[][] {{200, 14 + (hf + hd) / 2, 8}});
        assertThat(area.contains(remembered(r1, "edited", PlacedLabel.Rung.FULL_NEAR, f.full(),
            false, ScreenLabelPlacement.Slot.ABOVE, false).bounds())).isFalse();
        assertThat(area.contains(remembered(r1, "edited", PlacedLabel.Rung.DENSE_NEAR, f.dense(),
            false, ScreenLabelPlacement.Slot.ABOVE, false).bounds())).isTrue();
        final ScreenLabelPlacement p = new ScreenLabelPlacement();
        final List<PlacedLabel> first = p.place(r1, null, f);
        final List<PlacedLabel> retained = p.place(r1, first, f);
        assertFace(findNode(retained, "edited"), display, display, f.dense(),
            PlacedLabel.Rung.DENSE_NEAR, ScreenLabelPlacement.Slot.ABOVE);
        final LabelPlacementRequest r2 = request(2, area, Collections.<ProjectedEndpointKey>emptySet(),
            new ProjectedNode[] {n2}, new double[][] {{200, 14 + hf + 1, 8}});
        assertThat(r2.forced()).isEqualTo(r1.forced());
        final PlacedLabel current = findNode(p.place(r2, retained, f), "edited");
        final PlacedLabel fresh = findNode(p.place(r2, null, f), "edited");
        assertFace(current, display, display, f.dense(), PlacedLabel.Rung.DENSE_NEAR, ScreenLabelPlacement.Slot.ABOVE);
        assertFace(fresh, display, display, f.full(), PlacedLabel.Rung.FULL_NEAR, ScreenLabelPlacement.Slot.ABOVE);
        assertClear(current, r2);
        assertClear(fresh, r2);
    }

    // ------------------------------------------------------------------ T5

    @Test
    public void remeasuresWidthAfterASameFontTitleEdit() {
        final LabelFonts f = fonts();
        final ScreenLabelPlacement p = new ScreenLabelPlacement();
        final Rectangle2D area = new Rectangle2D.Double(0, 0, 600, 400);
        final LabelPlacementRequest r1 = request(1, area, Collections.<ProjectedEndpointKey>emptySet(),
            new ProjectedNode[] {node("edited", "alpha", "alpha")}, new double[][] {{250, 200, 8}});
        final LabelPlacementRequest r2 = request(2, area, Collections.<ProjectedEndpointKey>emptySet(),
            new ProjectedNode[] {node("edited", "omega renamed", "omega renamed")}, new double[][] {{250, 200, 8}});
        final List<PlacedLabel> first = p.place(r1, null, f);
        final PlacedLabel before = findNode(first, "edited");
        final PlacedLabel current = findNode(p.place(r2, first, f), "edited");
        assertFace(before, "alpha", "alpha", f.full(), PlacedLabel.Rung.FULL_NEAR, ScreenLabelPlacement.Slot.ABOVE);
        assertFace(current, "omega renamed", "omega renamed", f.full(),
            PlacedLabel.Rung.FULL_NEAR, ScreenLabelPlacement.Slot.ABOVE);
        assertThat(current.width()).isNotEqualTo(before.width());
        assertThat(current.width()).isCloseTo(ScreenLabelPlacement.screenBounds("omega renamed", f.full()).getWidth(), within(1e-9));
    }

    @Test
    public void remeasuresHeightAndAnchorAfterAFaceTransition() {
        final LabelFonts f = fonts();
        checkStrip(f);
        final ScreenLabelPlacement p = new ScreenLabelPlacement();
        final List<PlacedLabel> first = p.place(strip(1, "Alpha", false), null, f);
        final PlacedLabel before = findNode(first, "edited");
        final String b = repeat('W', 20);
        final PlacedLabel current = findNode(p.place(strip(2, b, false), first, f), "edited");
        assertFace(current, b, b, f.dense(), PlacedLabel.Rung.DENSE_NEAR, ScreenLabelPlacement.Slot.ABOVE);
        final Rectangle2D measured = ScreenLabelPlacement.screenBounds(b, f.dense());
        assertThat(measured.getHeight()).isNotEqualTo(before.height());
        assertThat(current.height()).isCloseTo(measured.getHeight(), within(1e-9));
        assertThat(current.width()).isCloseTo(measured.getWidth(), within(1e-9));
        assertThat(current.anchorX()).isCloseTo(100, within(1e-9));
        assertThat(current.anchorY()).isCloseTo(32 - 8 - 6 - measured.getHeight() / 2, within(1e-9))
            .isNotEqualTo(before.anchorY());
        assertThat(current.bounds().getMinY()).isCloseTo(32 - 14 - measured.getHeight(), within(1e-9));
    }

    @Test
    public void recomputesBothEndsOfADiagonalLeaderAfterRetitling() {
        final LabelFonts F = fonts();
        final ScreenLabelPlacement p = new ScreenLabelPlacement();
        final Rectangle2D area = new Rectangle2D.Double(0, 0, 600, 400);
        final LabelPlacementRequest r1 = request(1, area, Collections.<ProjectedEndpointKey>emptySet(),
            new ProjectedNode[] {node("edited", "alpha", "alpha")}, new double[][] {{250, 200, 8}});
        final PlacedLabel diagonal = remembered(r1, "edited", PlacedLabel.Rung.FULL_NEAR,
            F.full(), false, ScreenLabelPlacement.Slot.ABOVE_RIGHT, false);
        final List<Rectangle2D> seeds = new ArrayList<Rectangle2D>();
        for (final ScreenLabelPlacement.Slot slot : Arrays.asList(ScreenLabelPlacement.Slot.ABOVE,
                ScreenLabelPlacement.Slot.BELOW, ScreenLabelPlacement.Slot.RIGHT, ScreenLabelPlacement.Slot.LEFT)) {
            final PlacedLabel cardinal = remembered(r1, "edited", PlacedLabel.Rung.FULL_NEAR, F.full(), false, slot, false);
            final Rectangle2D seed = new Rectangle2D.Double(cardinal.anchorX() - 0.5, cardinal.anchorY() - 0.5, 1, 1);
            assertThat(cardinal.bounds().intersects(seed)).isTrue();
            assertThat(diagonal.bounds().intersects(seed)).isFalse();
            seeds.add(seed);
        }
        final List<PlacedLabel> first = p.place(r1, null, F, seeds);
        final PlacedLabel old = findNode(first, "edited");
        assertFace(old, "alpha", "alpha", F.full(), PlacedLabel.Rung.FULL_NEAR, ScreenLabelPlacement.Slot.ABOVE_RIGHT);
        assertThat(old.leader()).isPresent();
        final LabelPlacementRequest r2 = request(2, area, Collections.<ProjectedEndpointKey>emptySet(),
            new ProjectedNode[] {node("edited", "omega renamed", "omega renamed")}, new double[][] {{250, 200, 8}});
        final PlacedLabel current = findNode(p.place(r2, first, F), "edited");
        assertFace(current, "omega renamed", "omega renamed", F.full(),
            PlacedLabel.Rung.FULL_NEAR, ScreenLabelPlacement.Slot.ABOVE_RIGHT);
        final double w = ScreenLabelPlacement.screenBounds("omega renamed", F.full()).getWidth();
        final double h = ScreenLabelPlacement.screenBounds("omega renamed", F.full()).getHeight();
        final double dx = 8.0 + 6.0 + w / 2.0;
        final double dy = -(8.0 + 6.0 + h / 2.0);
        final double d = Math.hypot(dx, dy);
        final double ux = dx / d;
        final double uy = dy / d;
        final double q = Math.max((8.0 + 6.0 - 3.0) / ux, -(8.0 + 6.0 - 3.0) / uy);
        assertThat(q).isLessThan(d);
        assertThat(q - 8.0).isGreaterThanOrEqualTo(2.0);
        assertThat(current.leader()).isPresent();
        final LeaderLine line = current.leader().get();
        assertThat(line.start().x()).isCloseTo(250.0 + 8.0 * ux, within(1e-9));
        assertThat(line.start().y()).isCloseTo(200.0 + 8.0 * uy, within(1e-9));
        assertThat(line.end().x()).isCloseTo(250.0 + q * ux, within(1e-9));
        assertThat(line.end().y()).isCloseTo(200.0 + q * uy, within(1e-9));
        assertThat(Math.hypot(line.start().x() - old.leader().get().start().x(),
            line.start().y() - old.leader().get().start().y())).isGreaterThan(1e-9);
        assertThat(Math.hypot(line.end().x() - old.leader().get().end().x(),
            line.end().y() - old.leader().get().end().y())).isGreaterThan(1e-9);
    }

    // ------------------------------------------------------------------ T6

    @Test
    public void declinesAnExpandedBoxAtADiscWithoutLeavingAnObstacle() {
        verifyExpansionRejection(false, false);
    }

    @Test
    public void declinesAnExpandedBoxAtAnEarlierLabelWithoutLeavingAnObstacle() {
        verifyExpansionRejection(true, false);
    }

    @Test
    public void declinesAnExpandedBoxOutsideTheAreaWithoutLeavingAnObstacle() {
        verifyExpansionRejection(false, true);
    }

    // ------------------------------------------------------------------ T7

    @Test
    public void rederivesAtTheRememberedSlotWhenTheFaceFontChanges() {
        final LabelFonts f = fonts();
        final LabelFonts oldFonts = LabelFonts.of(f.full().deriveFont(24f),
            f.dense().deriveFont(18f), f.emphatic().deriveFont(24f));
        final Rectangle2D area = new Rectangle2D.Double(0, 0, 400, 170);
        final ProjectedNode[] nodes = {node("edited", "Alpha", "Alpha")};
        final LabelPlacementRequest r1 = request(1, area, Collections.<ProjectedEndpointKey>emptySet(),
            nodes, new double[][] {{200, 120, 8}});
        final PlacedLabel old = remembered(r1, "edited", PlacedLabel.Rung.FULL_NEAR,
            oldFonts.full(), false, ScreenLabelPlacement.Slot.BELOW, false);
        assertClear(old, r1);
        final LabelPlacementRequest r2 = request(2, area, Collections.<ProjectedEndpointKey>emptySet(),
            nodes, new double[][] {{200, 135, 8}});
        final PlacedLabel oldAtCurrent = remembered(r2, "edited", PlacedLabel.Rung.FULL_NEAR,
            oldFonts.full(), false, ScreenLabelPlacement.Slot.BELOW, false);
        final PlacedLabel newAtCurrent = remembered(r2, "edited", PlacedLabel.Rung.FULL_NEAR,
            f.full(), false, ScreenLabelPlacement.Slot.BELOW, false);
        assertThat(area.contains(oldAtCurrent.bounds())).isFalse();
        assertClear(newAtCurrent, r2);
        assertThat(old.font()).isNotEqualTo(f.full());
        final ScreenLabelPlacement p = new ScreenLabelPlacement();
        final PlacedLabel current = findNode(p.place(r2, Collections.singletonList(old), f), "edited");
        assertFace(current, "Alpha", "Alpha", f.full(), PlacedLabel.Rung.FULL_NEAR, ScreenLabelPlacement.Slot.BELOW);
        assertSamePlacement(current, newAtCurrent);
        assertThat(current.forcedAtBaseSlot()).isFalse();
        final PlacedLabel fresh = findNode(p.place(r2, null, f), "edited");
        assertFace(fresh, "Alpha", "Alpha", f.full(), PlacedLabel.Rung.FULL_NEAR, ScreenLabelPlacement.Slot.ABOVE);
        assertClear(fresh, r2);
    }

    @Test
    public void reLaddersAPreviouslyHoverOnlyNodeFromItsCurrentTitle() {
        final LabelFonts f = fonts();
        final ScreenLabelPlacement p = new ScreenLabelPlacement();
        final LabelPlacementRequest tiny = request(1, new Rectangle2D.Double(0, 0, 4, 4),
            Collections.<ProjectedEndpointKey>emptySet(),
            new ProjectedNode[] {node("edited", "Alpha", "Alpha")}, new double[][] {{2, 2, 8}});
        final List<PlacedLabel> first = p.place(tiny, null, f);
        assertThat(findNode(first, "edited").mode()).isEqualTo(PlacedLabel.Mode.HOVER_ONLY);
        final LabelPlacementRequest roomy = request(2, new Rectangle2D.Double(0, 0, 600, 300),
            Collections.<ProjectedEndpointKey>emptySet(),
            new ProjectedNode[] {node("edited", "omega renamed", "omega renamed")}, new double[][] {{300, 150, 8}});
        final PlacedLabel current = findNode(p.place(roomy, first, f), "edited");
        assertFace(current, "omega renamed", "omega renamed", f.full(),
            PlacedLabel.Rung.FULL_NEAR, ScreenLabelPlacement.Slot.ABOVE);
        assertClear(current, roomy);
    }

    private static void verifyExpansionRejection(final boolean earlierLabel, final boolean areaEdge) {
        final LabelFonts f = fonts();
        final ScreenLabelPlacement p = new ScreenLabelPlacement();
        final String expanded = repeat('W', 10);
        final double laterY = 150 + 8 + 6 + ScreenLabelPlacement.screenBounds("i", f.full()).getHeight() / 2;
        final double blockerY = earlierLabel
            ? 150 + 9 + 6 + ScreenLabelPlacement.screenBounds("Beta", f.full()).getHeight() / 2 : 150;
        final Set<ProjectedEndpointKey> forced = earlierLabel
            ? new java.util.LinkedHashSet<ProjectedEndpointKey>(Arrays.asList(
                ProjectedEndpointKey.ofNode(stableKey("edited")), ProjectedEndpointKey.ofNode(stableKey("blocker"))))
            : Collections.singleton(ProjectedEndpointKey.ofNode(stableKey("edited")));
        final ProjectedNode[] oldNodes = areaEdge
            ? new ProjectedNode[] {node("edited", "alpha", "alpha"), node("later", "i", "i")}
            : new ProjectedNode[] {node("edited", "alpha", "alpha"), node("blocker", "Beta", "Beta"), node("later", "i", "i")};
        final ProjectedNode[] newNodes = areaEdge
            ? new ProjectedNode[] {node("edited", expanded, expanded), node("later", "i", "i")}
            : new ProjectedNode[] {node("edited", expanded, expanded), node("blocker", "Beta", "Beta"), node("later", "i", "i")};
        final double[][] discs = areaEdge ? new double[][] {{100, 150, 8}, {170, laterY, 8}}
            : new double[][] {{100, 150, 8}, {earlierLabel ? 220 : 215, blockerY, earlierLabel ? 9 : 8}, {170, laterY, 8}};
        final Rectangle2D area = new Rectangle2D.Double(0, 0, areaEdge ? 200 : 400, 300);
        final LabelPlacementRequest oldRequest = request(1, area, forced, oldNodes, discs);
        final LabelPlacementRequest currentRequest = request(2, area, forced, newNodes, discs);
        final PlacedLabel oldEdited = remembered(oldRequest, "edited", PlacedLabel.Rung.FULL_NEAR,
            f.full(), false, ScreenLabelPlacement.Slot.RIGHT, false);
        final PlacedLabel oldLater = remembered(oldRequest, "later", PlacedLabel.Rung.FULL_NEAR,
            f.full(), false, ScreenLabelPlacement.Slot.ABOVE, false);
        final PlacedLabel candidate = remembered(currentRequest, "edited", PlacedLabel.Rung.FULL_NEAR,
            f.full(), false, ScreenLabelPlacement.Slot.RIGHT, false);
        final List<PlacedLabel> previous = new ArrayList<PlacedLabel>();
        previous.add(oldEdited);
        previous.add(oldLater);
        assertClear(oldEdited, oldRequest);
        assertClear(oldLater, oldRequest);
        assertThat(candidate.bounds().getMinX()).isEqualTo(114);
        assertThat(candidate.bounds().getMaxX()).isGreaterThan(200);
        assertThat(candidate.bounds().getMinY()).isCloseTo(141.827943, within(1e-4));
        assertThat(candidate.bounds().getMaxY()).isCloseTo(158.172057, within(1e-4));
        assertThat(oldLater.bounds().intersects(candidate.bounds())).isTrue();
        assertThat(oldLater.bounds().intersects(oldEdited.bounds())).isFalse();
        Rectangle2D blocking = null;
        if (earlierLabel) {
            final PlacedLabel blocker = remembered(oldRequest, "blocker", PlacedLabel.Rung.FULL_NEAR,
                f.full(), false, ScreenLabelPlacement.Slot.ABOVE, false);
            previous.add(blocker);
            blocking = blocker.bounds();
            assertThat(blocker.anchorX()).isEqualTo(220);
            assertThat(blocker.anchorY()).isCloseTo(150, within(1e-9));
            assertThat(candidate.bounds().intersects(new Rectangle2D.Double(211, blockerY - 9, 18, 18))).isFalse();
        } else if (!areaEdge) {
            blocking = new Rectangle2D.Double(207, 142, 16, 16);
        }
        if (areaEdge) {
            assertThat(area.contains(candidate.bounds())).isFalse();
            assertThat(oldLater.bounds().getMaxX()).isLessThan(200);
        } else {
            assertThat(area.contains(candidate.bounds())).isTrue();
            assertThat(candidate.bounds().intersects(blocking)).isTrue();
            assertThat(oldEdited.bounds().intersects(blocking)).isFalse();
            assertThat(oldLater.bounds().intersects(blocking)).isFalse();
        }
        final List<PlacedLabel> rejected = p.place(currentRequest, previous, f);
        final PlacedLabel fallback = findNode(rejected, "edited");
        final PlacedLabel later = findNode(rejected, "later");
        assertFace(fallback, expanded, expanded, f.full(), PlacedLabel.Rung.FULL_NEAR, ScreenLabelPlacement.Slot.ABOVE);
        assertThat(fallback.forced()).isTrue();
        assertThat(fallback.forcedAtBaseSlot()).isFalse();
        assertFace(later, "i", "i", f.full(), PlacedLabel.Rung.FULL_NEAR, ScreenLabelPlacement.Slot.ABOVE);
        assertThat(later.bounds().intersects(candidate.bounds())).isTrue();
        assertThat(later.bounds().intersects(fallback.bounds())).isFalse();
        if (blocking != null) assertThat(later.bounds().intersects(blocking)).isFalse();
        final double[][] clearDiscs = areaEdge ? new double[][] {{100, 150, 8}, {170, laterY, 8}}
            : new double[][] {{100, 150, 8}, {300, blockerY, earlierLabel ? 9 : 8}, {170, laterY, 8}};
        final LabelPlacementRequest clearRequest = request(3, new Rectangle2D.Double(0, 0, 400, 300),
            forced, newNodes, clearDiscs);
        final List<PlacedLabel> accepted = p.place(clearRequest, previous, f);
        final PlacedLabel expandedAccepted = findNode(accepted, "edited");
        final PlacedLabel movedLater = findNode(accepted, "later");
        assertFace(expandedAccepted, expanded, expanded, f.full(), PlacedLabel.Rung.FULL_NEAR, ScreenLabelPlacement.Slot.RIGHT);
        assertThat(expandedAccepted.forcedAtBaseSlot()).isFalse();
        assertClear(expandedAccepted, clearRequest);
        assertThat(movedLater.mode()).isNotEqualTo(PlacedLabel.Mode.HOVER_ONLY);
        assertThat(movedLater.slot()).isNotEqualTo(ScreenLabelPlacement.Slot.ABOVE);
        assertThat(movedLater.bounds().intersects(expandedAccepted.bounds())).isFalse();
    }
}
