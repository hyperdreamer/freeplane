package org.freeplane.plugin.graph.geometry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Dimension2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.RelationshipResolution;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.junit.Test;

public class LabelPlacementShould {
    private static final MapReferenceId MAP =
        MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    private static final String MAP_NAME = "Map";

    // Mutation: bypassing the interior candidate would change the required mode and bounds.
    @Test
    public void placesAShortLabelInTheLargestInteriorGap() {
        EnclosureHullKey hullKey = hullKey("interior");
        ProjectedEnclosure enclosure = enclosure(hullKey, BoundaryTier.SUBTLE,
            SafeNodeLabel.of("full text never measured", "short"));
        GraphGeometry source = geometry(Collections.singletonMap(hullKey,
            rectangle(-50.0, 0.0, 50.0, 20.0, LayoutPoint.of(0.0, 10.0))));
        RecordingMetrics metrics = new RecordingMetrics(dimension(8.0, 4.0));

        GraphGeometry placed = place(projection(Collections.singletonList(enclosure)), source, metrics);

        LabelPlacement label = placed.labels().get(enclosureKey("interior"));
        assertThat(label.mode()).isEqualTo(LabelPlacement.Mode.INTERIOR);
        assertThat(label.leaderStart()).isEmpty();
        assertPlacementInside(label, placed.hulls().get(hullKey));
        assertThat(source.labels()).isEmpty();
        assertThat(source.hulls().get(hullKey)).isEqualTo(
            rectangle(-50.0, 0.0, 50.0, 20.0, LayoutPoint.of(0.0, 10.0)));
        assertThat(metrics.texts()).containsExactly("short");
        assertThat(metrics.tiers()).containsExactly(BoundaryTier.SUBTLE);
    }

    // Mutation: allowing rounded bounds to collapse lets both labels occupy the same interior anchor.
    @Test
    public void keepsCoarseUlpBoundsPositiveAndPreventsCoLocatedInteriorLabels() {
        double anchorX = Math.nextUp(Math.scalb(1.0, 55));
        assertThat(Math.ulp(anchorX)).isEqualTo(8.0);
        EnclosureKey firstEndpoint = enclosureKey("coarse-ulp-first");
        EnclosureKey secondEndpoint = enclosureKey("coarse-ulp-second");
        EnclosureHullKey key = EnclosureHullKey.of(Arrays.asList(firstEndpoint, secondEndpoint));
        ProjectedEnclosure enclosure = enclosure(key, BoundaryTier.SUBTLE,
            SafeNodeLabel.of("first full", "first"), SafeNodeLabel.of("second full", "second"));
        LayoutPoint sharedAnchor = LayoutPoint.of(anchorX, 20.0);
        GraphGeometry source = geometry(Collections.singletonMap(key,
            rectangle(anchorX - 80.0, 0.0, anchorX + 80.0, 40.0, sharedAnchor)));

        GraphGeometry placed = place(projection(Collections.singletonList(enclosure)), source,
            new RecordingMetrics(dimension(4.0, 4.0)));

        LabelPlacement first = placed.labels().get(firstEndpoint);
        LabelPlacement second = placed.labels().get(secondEndpoint);
        assertThat(first.mode()).isEqualTo(LabelPlacement.Mode.INTERIOR);
        assertThat(first.anchor()).isEqualTo(sharedAnchor);
        assertThat(second.mode()).isNotEqualTo(LabelPlacement.Mode.INTERIOR);
        assertThat(second.anchor()).isNotEqualTo(sharedAnchor);
        assertThat(first.minX()).isLessThan(first.maxX());
        assertThat(first.minY()).isLessThan(first.maxY());
        assertNoVisiblePlacementCollisions(placed.labels());

        LabelPlacement publicValue = LabelPlacement.of("public", LabelPlacement.Mode.INTERIOR,
            sharedAnchor, 4.0, 4.0, Optional.<LayoutPoint>empty());
        assertThat(publicValue.minX()).isLessThan(publicValue.maxX());
        assertThat(publicValue.minY()).isLessThan(publicValue.maxY());
    }

    // Mutation: reserving a stale suppressed placement would force the visible label out of its interior.
    @Test
    public void ignoresStaleSuppressedPlacementsWhenReservingVisibleCandidates() {
        EnclosureHullKey visibleKey = hullKey("visible-current");
        EnclosureHullKey suppressedKey = hullKey("suppressed-stale");
        ProjectedEnclosure visible = enclosure(visibleKey, BoundaryTier.SUBTLE,
            SafeNodeLabel.of("visible full", "visible"));
        ProjectedEnclosure suppressed = enclosure(suppressedKey, BoundaryTier.SUPPRESSED,
            SafeNodeLabel.of("suppressed full", "suppressed"));
        Map<EnclosureHullKey, HullGeometry> hulls = new LinkedHashMap<EnclosureHullKey, HullGeometry>();
        hulls.put(visibleKey, rectangle(-50.0, 0.0, 50.0, 20.0, LayoutPoint.of(0.0, 10.0)));
        hulls.put(suppressedKey, rectangle(-50.0, 0.0, 50.0, 20.0, LayoutPoint.of(0.0, 10.0)));
        Map<EnclosureKey, LabelPlacement> staleLabels = new LinkedHashMap<EnclosureKey, LabelPlacement>();
        staleLabels.put(enclosureKey("suppressed-stale"), LabelPlacement.of("stale",
            LabelPlacement.Mode.INTERIOR, LayoutPoint.of(0.0, 10.0), 8.0, 4.0,
            Optional.<LayoutPoint>empty()));
        RecordingMetrics metrics = new RecordingMetrics(dimension(8.0, 4.0));

        GraphGeometry placed = place(projection(Arrays.asList(visible, suppressed)),
            GraphGeometry.of(Collections.<ProjectedNodeKey, NodeGeometry>emptyMap(), hulls, staleLabels), metrics);

        assertThat(placed.labels().keySet()).containsExactly(enclosureKey("visible-current"));
        assertThat(placed.labels().get(enclosureKey("visible-current")).mode())
            .isEqualTo(LabelPlacement.Mode.INTERIOR);
        assertThat(metrics.texts()).containsExactly("visible");
    }

    // Mutation: ignoring edge population or endpoint order would change the selected arc.
    @Test
    public void reservesTheLeastPopulatedArcWhenInteriorDoesNotFit() {
        EnclosureHullKey firstKey = hullKey("arc-first");
        EnclosureHullKey secondKey = hullKey("arc-second");
        ProjectedEnclosure first = enclosure(firstKey, BoundaryTier.SUBTLE,
            SafeNodeLabel.of("first full", "first"));
        ProjectedEnclosure second = enclosure(secondKey, BoundaryTier.SUBTLE,
            SafeNodeLabel.of("second full", "second"));
        Map<EnclosureHullKey, HullGeometry> hulls = new LinkedHashMap<EnclosureHullKey, HullGeometry>();
        hulls.put(firstKey, rectangle(-50.0, 0.0, 50.0, 20.0, LayoutPoint.of(-45.0, 10.0)));
        hulls.put(secondKey, rectangle(-50.0, 0.0, 50.0, 20.0, LayoutPoint.of(-45.0, 10.0)));
        RecordingMetrics metrics = new RecordingMetrics(dimension(40.0, 8.0));

        GraphGeometry placed = place(projection(Arrays.asList(first, second)), geometry(hulls), metrics);

        LabelPlacement firstPlacement = placed.labels().get(enclosureKey("arc-first"));
        LabelPlacement secondPlacement = placed.labels().get(enclosureKey("arc-second"));
        assertThat(firstPlacement.mode()).isEqualTo(LabelPlacement.Mode.ARC);
        assertThat(secondPlacement.mode()).isEqualTo(LabelPlacement.Mode.ARC);
        assertThat(firstPlacement.leaderStart()).isEmpty();
        assertThat(secondPlacement.leaderStart()).isEmpty();
        assertThat(firstPlacement.anchor()).isNotEqualTo(secondPlacement.anchor());
        assertNoVisiblePlacementCollisions(placed.labels());
        assertThat(placed).isEqualTo(
            place(projection(Arrays.asList(first, second)), geometry(hulls),
                new RecordingMetrics(dimension(40.0, 8.0))));
    }

    // Mutation: stopping before the external ladder would remove the required leader.
    @Test
    public void placesAnExternalLabelWithALeaderAfterInteriorAndArcFail() {
        List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        Map<EnclosureHullKey, HullGeometry> hulls = new LinkedHashMap<EnclosureHullKey, HullGeometry>();
        for (String id : Arrays.asList("external-first", "external-second", "external-target")) {
            EnclosureHullKey key = hullKey(id);
            enclosures.add(enclosure(key, BoundaryTier.SUBTLE, SafeNodeLabel.of(id + " full", id)));
            hulls.put(key, rectangle(-50.0, 0.0, 50.0, 20.0, LayoutPoint.of(-45.0, 10.0)));
        }
        GraphGeometry placed = place(projection(enclosures), geometry(hulls),
            new RecordingMetrics(dimension(40.0, 8.0)));

        EnclosureKey targetKey = enclosureKey("external-target");
        LabelPlacement target = placed.labels().get(targetKey);
        HullGeometry targetHull = placed.hulls().get(hullKey("external-target"));
        assertThat(target.mode()).isEqualTo(LabelPlacement.Mode.EXTERNAL);
        assertThat(target.leaderStart()).isPresent();
        assertThat(targetHull.contains(target.anchor())).isFalse();
        assertThat(target.leaderStart().get())
            .isEqualTo(targetHull.nearestBoundaryPoint(target.anchor()));
        assertNoVisiblePlacementCollisions(placed.labels());
    }

    // Mutation: removing the finite subtle hover fallback would leave dense labels unhandled.
    @Test
    public void demotesOnlySubtleLabelsToHoverWhenAllVisibleCandidatesCollide() {
        final int subtleCount = 20;
        List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        Map<EnclosureHullKey, HullGeometry> hulls = new LinkedHashMap<EnclosureHullKey, HullGeometry>();
        for (int index = 0; index < subtleCount; index++) {
            String id = "subtle-" + index;
            EnclosureHullKey key = hullKey(id);
            enclosures.add(enclosure(key, BoundaryTier.SUBTLE,
                SafeNodeLabel.of(id + " full", id)));
            hulls.put(key, rectangle(-50.0, 0.0, 50.0, 20.0, LayoutPoint.of(-45.0, 10.0)));
        }
        GraphGeometry placed = place(projection(enclosures), geometry(hulls),
            new RecordingMetrics(dimension(40.0, 8.0)));

        List<LabelPlacement> placements = new ArrayList<LabelPlacement>(placed.labels().values());
        int firstHover = -1;
        for (int index = 0; index < placements.size(); index++) {
            if (placements.get(index).mode() == LabelPlacement.Mode.HOVER_ONLY) {
                firstHover = index;
                break;
            }
        }
        assertThat(firstHover).isGreaterThanOrEqualTo(0);
        for (int index = firstHover; index < placements.size(); index++) {
            assertThat(placements.get(index).mode()).isEqualTo(LabelPlacement.Mode.HOVER_ONLY);
            assertThat(placements.get(index).leaderStart()).isEmpty();
        }
        assertNoVisiblePlacementCollisions(placed.labels());
        assertThat(placed).isEqualTo(place(projection(enclosures), geometry(hulls),
            new RecordingMetrics(dimension(40.0, 8.0))));
    }

    // Mutation: adding a hover-only fallback for emphatic labels must fail this test.
    @Test
    public void neverDemotesAnEmphaticLabelToHover() {
        final int subtleCount = 20;
        List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        Map<EnclosureHullKey, HullGeometry> hulls = new LinkedHashMap<EnclosureHullKey, HullGeometry>();
        for (int index = 0; index < subtleCount; index++) {
            String id = "emphatic-subtle-" + index;
            EnclosureHullKey key = hullKey(id);
            enclosures.add(enclosure(key, BoundaryTier.SUBTLE,
                SafeNodeLabel.of(id + " full", id)));
            hulls.put(key, rectangle(-50.0, 0.0, 50.0, 20.0, LayoutPoint.of(-45.0, 10.0)));
        }
        EnclosureHullKey emphaticKey = hullKey("emphatic-target");
        enclosures.add(enclosure(emphaticKey, BoundaryTier.EMPHATIC,
            SafeNodeLabel.of("emphatic full", "emphatic")));
        hulls.put(emphaticKey, rectangle(-50.0, 0.0, 50.0, 20.0, LayoutPoint.of(-45.0, 10.0)));

        GraphGeometry placed = place(projection(enclosures), geometry(hulls),
            new RecordingMetrics(dimension(40.0, 8.0)));

        LabelPlacement emphatic = placed.labels().get(enclosureKey("emphatic-target"));
        assertThat(emphatic.mode()).isEqualTo(LabelPlacement.Mode.EXTERNAL);
        assertThat(emphatic.mode()).isNotEqualTo(LabelPlacement.Mode.HOVER_ONLY);
        assertThat(emphatic.leaderStart()).isPresent();
        assertNoVisiblePlacementCollisions(placed.labels());
        assertThat(placed).isEqualTo(place(projection(enclosures), geometry(hulls),
            new RecordingMetrics(dimension(40.0, 8.0))));
    }

    // Mutation: limiting emphatic external search to 64 lanes makes the final label unplaceable.
    @Test
    public void continuesEmphaticExternalLaneSearchPastSixtyFourCandidates() {
        final int labelCount = 65;
        String[] ids = new String[labelCount];
        SafeNodeLabel[] labels = new SafeNodeLabel[labelCount];
        for (int index = 0; index < labelCount; index++) {
            ids[index] = "emphatic-lane-" + index;
            labels[index] = SafeNodeLabel.of("full " + index, "lane " + index);
        }
        EnclosureHullKey key = hullKey(ids);
        ProjectedEnclosure enclosure = enclosure(key, BoundaryTier.EMPHATIC, labels);
        GraphGeometry placed = place(projection(Collections.singletonList(enclosure)),
            geometry(Collections.singletonMap(key,
                rectangle(-50.0, 0.0, 50.0, 20.0, LayoutPoint.of(0.0, 10.0)))),
            new RecordingMetrics(dimension(200.0, 8.0)));

        LabelPlacement last = placed.labels().get(enclosureKey(ids[labelCount - 1]));
        assertThat(placed.labels()).hasSize(labelCount);
        assertThat(last.mode()).isEqualTo(LabelPlacement.Mode.EXTERNAL);
        assertThat(last.anchor().y()).isLessThan(-764.0);
        assertNoVisiblePlacementCollisions(placed.labels());
    }

    // Mutation: removing the 1.5x interior-padding limit must fail this test.
    @Test
    public void capsInteriorHullPaddingAtOneAndAHalfTimesTheOriginalClearance() {
        ProjectedNode node = projectedNode("cap-node");
        EnclosureHullKey key = hullKey("cap-hull");
        ProjectedEnclosure enclosure = enclosure(key, BoundaryTier.SUBTLE,
            Collections.singletonList(node.key()), Collections.<EnclosureHullKey>emptyList(),
            SafeNodeLabel.of("cap full", "cap"));
        GraphProjection projection = projection(Collections.singletonList(node),
            Collections.singletonList(enclosure));
        LayoutPositions positions = LayoutPositions.of(
            Collections.singletonMap(node.key(), LayoutPoint.of(0.0, 0.0)),
            Collections.singletonMap(key, LayoutPoint.of(0.0, 0.0)));
        GraphGeometry original = new GraphGeometryEngine().computeHulls(projection, positions);

        GraphGeometry expanded = place(projection, original, new RecordingMetrics(dimension(52.0, 4.0)));
        HullGeometry expandedHull = expanded.hulls().get(key);
        assertThat(expanded.labels().get(enclosureKey("cap-hull")).mode())
            .isEqualTo(LabelPlacement.Mode.INTERIOR);
        assertThat(expandedHull.maxX()).isCloseTo(26.0, within(1e-9));
        assertThat(expandedHull.maxX() - original.nodes().get(node.key()).radius())
            .isCloseTo(18.0, within(1e-9));
        assertThat(expandedHull.maxX() - original.nodes().get(node.key()).radius()).isLessThanOrEqualTo(24.0);

        GraphGeometry rejected = place(projection, original, new RecordingMetrics(dimension(68.0, 4.0)));
        GraphGeometry retry = place(projection, original, new RecordingMetrics(dimension(68.0, 4.0)));
        assertThat(rejected.labels().get(enclosureKey("cap-hull")).mode())
            .isNotEqualTo(LabelPlacement.Mode.INTERIOR);
        assertThat(rejected.hulls().get(key).maxX()).isLessThanOrEqualTo(32.0);
        assertThat(rejected.hulls().get(key).maxX() - original.nodes().get(node.key()).radius())
            .isLessThanOrEqualTo(24.0);
        assertThat(retry).isEqualTo(rejected);
    }

    // Mutation: traversing unary endpoints through an unordered set must fail this test.
    @Test
    public void preservesUnaryEndpointOrderAndAvoidsPlacementCollisions() {
        EnclosureKey firstEndpoint = enclosureKey("unary-first");
        EnclosureKey secondEndpoint = enclosureKey("unary-second");
        EnclosureHullKey key = EnclosureHullKey.of(Arrays.asList(firstEndpoint, secondEndpoint));
        ProjectedEnclosure enclosure = enclosure(key, BoundaryTier.SUBTLE,
            SafeNodeLabel.of("first full", "first"), SafeNodeLabel.of("second full", "second"));
        GraphGeometry source = geometry(Collections.singletonMap(key,
            rectangle(-50.0, 0.0, 50.0, 20.0, LayoutPoint.of(-45.0, 10.0))));

        GraphGeometry placed = place(projection(Collections.singletonList(enclosure)), source,
            new RecordingMetrics(dimension(40.0, 8.0)));

        assertThat(new ArrayList<EnclosureKey>(placed.labels().keySet()))
            .containsExactly(firstEndpoint, secondEndpoint);
        assertThat(placed.labels()).containsKeys(firstEndpoint, secondEndpoint);
        assertNoVisiblePlacementCollisions(placed.labels());
        assertThat(placed).isEqualTo(place(projection(Collections.singletonList(enclosure)), source,
            new RecordingMetrics(dimension(40.0, 8.0))));

        Map<EnclosureKey, LabelPlacement> reversed = new LinkedHashMap<EnclosureKey, LabelPlacement>();
        reversed.put(secondEndpoint, LabelPlacement.of("second", LabelPlacement.Mode.ARC,
            LayoutPoint.of(0.0, 15.0), 40.0, 8.0, Optional.<LayoutPoint>empty()));
        reversed.put(firstEndpoint, LabelPlacement.of("first", LabelPlacement.Mode.ARC,
            LayoutPoint.of(0.0, 5.0), 40.0, 8.0, Optional.<LayoutPoint>empty()));
        GraphGeometry reversedSource = GraphGeometry.of(Collections.<ProjectedNodeKey, NodeGeometry>emptyMap(),
            Collections.singletonMap(key, source.hulls().get(key)), reversed);
        GraphGeometry reordered = place(projection(Collections.singletonList(enclosure)), reversedSource,
            new RecordingMetrics(dimension(40.0, 8.0)));
        assertThat(new ArrayList<EnclosureKey>(reordered.labels().keySet()))
            .containsExactly(firstEndpoint, secondEndpoint);
    }

    // Mutation: substituting fullText for displayText in metrics must fail this test.
    @Test
    public void measuresOnlySafeDisplayTextAndRejectsInvalidMetricResults() {
        EnclosureHullKey key = hullKey("metrics");
        ProjectedEnclosure enclosure = enclosure(key, BoundaryTier.SUBTLE,
            SafeNodeLabel.of("full text must never be measured", "safe display"));
        GraphProjection projection = projection(Collections.singletonList(enclosure));
        GraphGeometry source = geometry(Collections.singletonMap(key,
            rectangle(-50.0, 0.0, 50.0, 20.0, LayoutPoint.of(0.0, 10.0))));
        RecordingMetrics metrics = new RecordingMetrics(dimension(8.0, 4.0));

        place(projection, source, metrics);

        assertThat(metrics.texts()).containsExactly("safe display");
        assertThat(metrics.texts()).doesNotContain("full text must never be measured", "Map", "metrics");
        for (Dimension2D invalid : Arrays.asList(null, dimension(-1.0, 1.0), dimension(0.0, 1.0),
            dimension(Double.NaN, 1.0), dimension(1.0, Double.POSITIVE_INFINITY))) {
            assertThatThrownBy(() -> place(projection, source, new RecordingMetrics(invalid)))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // Mutation: exposing mutable copies or accepting mismatched geometry keys must fail this test.
    @Test
    public void returnsDeepImmutableDeterministicGeometryAndRejectsMismatchedInputs() {
        EnclosureHullKey key = hullKey("immutable");
        ProjectedEnclosure enclosure = enclosure(key, BoundaryTier.SUBTLE,
            SafeNodeLabel.of("immutable full", "immutable"));
        GraphProjection projection = projection(Collections.singletonList(enclosure));
        Map<EnclosureHullKey, HullGeometry> sourceHulls = new LinkedHashMap<EnclosureHullKey, HullGeometry>();
        sourceHulls.put(key, rectangle(-50.0, 0.0, 50.0, 20.0, LayoutPoint.of(0.0, 10.0)));
        Map<EnclosureHullKey, HullGeometry> callerHulls = new LinkedHashMap<EnclosureHullKey, HullGeometry>(sourceHulls);
        Map<EnclosureKey, LabelPlacement> callerLabels = new LinkedHashMap<EnclosureKey, LabelPlacement>();
        callerLabels.put(enclosureKey("immutable"), LabelPlacement.of("immutable", LabelPlacement.Mode.INTERIOR,
            LayoutPoint.of(0.0, 10.0), 8.0, 4.0, Optional.<LayoutPoint>empty()));
        GraphGeometry value = GraphGeometry.of(Collections.<ProjectedNodeKey, NodeGeometry>emptyMap(),
            callerHulls, callerLabels);
        callerHulls.clear();
        callerLabels.clear();
        assertThat(value.labels()).containsKey(enclosureKey("immutable"));
        assertThatThrownBy(() -> value.labels().put(enclosureKey("other"),
            LabelPlacement.of("other", LabelPlacement.Mode.INTERIOR, LayoutPoint.of(0.0, 10.0),
                8.0, 4.0, Optional.<LayoutPoint>empty())))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> LabelPlacement.of("", LabelPlacement.Mode.INTERIOR,
            LayoutPoint.of(0.0, 0.0), 1.0, 1.0, Optional.<LayoutPoint>empty()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LabelPlacement.of("x", LabelPlacement.Mode.EXTERNAL,
            LayoutPoint.of(0.0, 0.0), 1.0, 1.0, Optional.<LayoutPoint>empty()))
            .isInstanceOf(IllegalArgumentException.class);

        GraphGeometry first = place(projection, geometry(sourceHulls), new RecordingMetrics(dimension(8.0, 4.0)));
        GraphGeometry second = place(projection, geometry(sourceHulls), new RecordingMetrics(dimension(8.0, 4.0)));
        assertThat(first).isEqualTo(second);
        assertThat(new ArrayList<EnclosureKey>(first.labels().keySet()))
            .containsExactly(enclosureKey("immutable"));

        EnclosureHullKey mismatched = hullKey("mismatched");
        assertThatThrownBy(() -> place(projection(Collections.singletonList(enclosure(mismatched,
            BoundaryTier.SUBTLE, SafeNodeLabel.of("mismatched", "mismatched")))), geometry(sourceHulls),
            new RecordingMetrics(dimension(8.0, 4.0))))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> place(projection(Arrays.asList(enclosure, enclosure)), geometry(sourceHulls),
            new RecordingMetrics(dimension(8.0, 4.0))))
            .isInstanceOf(IllegalArgumentException.class);

        EnclosureHullKey suppressedKey = hullKey("suppressed");
        RecordingMetrics suppressedMetrics = new RecordingMetrics(dimension(8.0, 4.0));
        GraphGeometry suppressed = place(
            projection(Collections.singletonList(enclosure(suppressedKey, BoundaryTier.SUPPRESSED,
                SafeNodeLabel.of("suppressed full", "suppressed")))),
            geometry(Collections.singletonMap(suppressedKey,
                rectangle(-20.0, 0.0, 20.0, 20.0, LayoutPoint.of(0.0, 10.0)))), suppressedMetrics);
        assertThat(suppressed.labels()).isEmpty();
        assertThat(suppressedMetrics.texts()).isEmpty();
    }

    // Mutation: using a default font render context produces bounds different from the supplied context.
    @Test
    public void awtMetricsUseTheSuppliedFontRenderContextAndTier() {
        Font font = new Font("Dialog", Font.PLAIN, 12);
        FontRenderContext context = new FontRenderContext(AffineTransform.getScaleInstance(2.0, 3.0), false, false);
        AwtGeometryTextMetrics metrics = new AwtGeometryTextMetrics(font, context);
        String displayText = "known text";

        Dimension2D subtle = metrics.measure(displayText, BoundaryTier.SUBTLE);
        Dimension2D emphatic = metrics.measure(displayText, BoundaryTier.EMPHATIC);
        Rectangle2D expectedSubtle = font.getStringBounds(displayText, context);
        Rectangle2D defaultSubtle = font.getStringBounds(displayText,
            new FontRenderContext(new AffineTransform(), true, true));
        Font emphaticFont = font.deriveFont(Font.BOLD, font.getSize2D() * 1.2f);
        Rectangle2D expectedEmphatic = emphaticFont.getStringBounds(displayText, context);

        assertThat(expectedSubtle.getWidth()).isNotEqualTo(defaultSubtle.getWidth());
        assertThat(subtle.getWidth()).isEqualTo(expectedSubtle.getWidth());
        assertThat(subtle.getHeight()).isEqualTo(expectedSubtle.getHeight());
        assertThat(emphatic.getWidth()).isEqualTo(expectedEmphatic.getWidth());
        assertThat(emphatic.getHeight()).isEqualTo(expectedEmphatic.getHeight());
        assertThat(subtle.getWidth()).isFinite().isGreaterThan(0.0);
        assertThat(subtle.getHeight()).isFinite().isGreaterThan(0.0);
        assertThat(emphatic.getWidth()).isFinite().isGreaterThanOrEqualTo(subtle.getWidth());
        assertThat(emphatic.getHeight()).isFinite().isGreaterThanOrEqualTo(subtle.getHeight());
        assertThatThrownBy(() -> metrics.measure(null, BoundaryTier.SUBTLE))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> metrics.measure("", BoundaryTier.SUBTLE))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> metrics.measure("text", null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> metrics.measure("text", BoundaryTier.SUPPRESSED))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static GraphGeometry place(final GraphProjection projection, final GraphGeometry geometry,
            final GeometryTextMetrics metrics) {
        return new LabelPlacementEngine().place(projection, geometry, metrics);
    }

    private static GraphGeometry geometry(final Map<EnclosureHullKey, HullGeometry> hulls) {
        return GraphGeometry.of(Collections.<ProjectedNodeKey, NodeGeometry>emptyMap(), hulls);
    }

    private static GraphProjection projection(final List<ProjectedEnclosure> enclosures) {
        return GraphProjection.projected(1, Collections.<ProjectedNode>emptyList(), enclosures,
            Collections.<ProjectedEdge>emptyList(), Collections.<RelationshipResolution>emptyList(),
            Collections.<PinProjection>emptyList());
    }

    private static GraphProjection projection(final List<ProjectedNode> nodes,
            final List<ProjectedEnclosure> enclosures) {
        return GraphProjection.projected(1, nodes, enclosures, Collections.<ProjectedEdge>emptyList(),
            Collections.<RelationshipResolution>emptyList(), Collections.<PinProjection>emptyList());
    }

    private static ProjectedEnclosure enclosure(final EnclosureHullKey hullKey, final BoundaryTier tier,
            final SafeNodeLabel... labels) {
        return enclosure(hullKey, tier, Collections.<ProjectedNodeKey>emptyList(),
            Collections.<EnclosureHullKey>emptyList(), labels);
    }

    private static ProjectedEnclosure enclosure(final EnclosureHullKey hullKey, final BoundaryTier tier,
            final List<ProjectedNodeKey> directNodes, final List<EnclosureHullKey> directEnclosures,
            final SafeNodeLabel... labels) {
        return ProjectedEnclosure.of(hullKey, hullKey.endpointKeys(), Arrays.asList(labels), MAP_NAME,
            Optional.<EnclosureHullKey>empty(), directNodes, directEnclosures, true, tier);
    }

    private static ProjectedNode projectedNode(final String id) {
        return ProjectedNode.of(nodeKey(id), SafeNodeLabel.of(id + " full", id), MAP_NAME, false);
    }

    private static EnclosureHullKey hullKey(final String... ids) {
        List<EnclosureKey> keys = new ArrayList<EnclosureKey>();
        for (String id : ids) {
            keys.add(enclosureKey(id));
        }
        return EnclosureHullKey.of(keys);
    }

    private static EnclosureKey enclosureKey(final String id) {
        return EnclosureKey.of(source(id));
    }

    private static ProjectedNodeKey nodeKey(final String id) {
        return ProjectedNodeKey.of(source(id));
    }

    private static SourceNodeKey source(final String id) {
        return SourceNodeKey.persisted(NodeReference.of(MAP, PersistedNodeId.of(id)));
    }

    private static HullGeometry rectangle(final double minX, final double minY, final double maxX,
            final double maxY, final LayoutPoint anchor) {
        return HullGeometry.of(Arrays.asList(LayoutPoint.of(minX, minY), LayoutPoint.of(maxX, minY),
            LayoutPoint.of(maxX, maxY), LayoutPoint.of(minX, maxY)), anchor);
    }

    private static Dimension2D dimension(final double width, final double height) {
        return new TestDimension(width, height);
    }

    private static void assertPlacementInside(final LabelPlacement placement, final HullGeometry hull) {
        assertThat(hull.contains(LayoutPoint.of(placement.minX(), placement.minY()))).isTrue();
        assertThat(hull.contains(LayoutPoint.of(placement.minX(), placement.maxY()))).isTrue();
        assertThat(hull.contains(LayoutPoint.of(placement.maxX(), placement.minY()))).isTrue();
        assertThat(hull.contains(LayoutPoint.of(placement.maxX(), placement.maxY()))).isTrue();
    }

    private static void assertNoVisiblePlacementCollisions(
            final Map<EnclosureKey, LabelPlacement> labels) {
        List<LabelPlacement> visible = new ArrayList<LabelPlacement>();
        for (LabelPlacement placement : labels.values()) {
            if (placement.mode() != LabelPlacement.Mode.HOVER_ONLY) {
                visible.add(placement);
            }
        }
        for (int first = 0; first < visible.size(); first++) {
            for (int second = first + 1; second < visible.size(); second++) {
                LabelPlacement a = visible.get(first);
                LabelPlacement b = visible.get(second);
                boolean overlaps = a.minX() < b.maxX() && b.minX() < a.maxX()
                    && a.minY() < b.maxY() && b.minY() < a.maxY();
                assertThat(overlaps).as("visible label rectangles %d and %d", first, second).isFalse();
            }
        }
    }

    private static final class RecordingMetrics implements GeometryTextMetrics {
        private final Dimension2D result;
        private final List<String> texts = new ArrayList<String>();
        private final List<BoundaryTier> tiers = new ArrayList<BoundaryTier>();

        private RecordingMetrics(final Dimension2D result) {
            this.result = result;
        }

        @Override
        public Dimension2D measure(final String displayText, final BoundaryTier tier) {
            texts.add(displayText);
            tiers.add(tier);
            return result;
        }

        private List<String> texts() {
            return texts;
        }

        private List<BoundaryTier> tiers() {
            return tiers;
        }
    }

    private static final class TestDimension extends Dimension2D {
        private double width;
        private double height;

        private TestDimension(final double width, final double height) {
            this.width = width;
            this.height = height;
        }

        @Override
        public double getWidth() {
            return width;
        }

        @Override
        public double getHeight() {
            return height;
        }

        @Override
        public void setSize(final double width, final double height) {
            this.width = width;
            this.height = height;
        }
    }
}
