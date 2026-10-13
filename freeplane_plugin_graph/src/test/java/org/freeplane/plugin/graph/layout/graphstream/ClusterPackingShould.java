package org.freeplane.plugin.graph.layout.graphstream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.junit.Test;

public class ClusterPackingShould {
    private static final double GAP = 8.0;
    private static final MapReferenceId MAP =
        MapReferenceId.of("00000000-0000-0000-0000-000000000001");

    private static ClusterPacking.ClusterItem content(double width, double height, int order) {
        return new ClusterPacking.ClusterItem(null, width, height, 0.0, 0.0, order);
    }

    private static ClusterPacking.ClusterItem item(String hullId, double width, double height,
            double anchorX, double anchorY, int order) {
        return new ClusterPacking.ClusterItem(hull(hullId), width, height, anchorX, anchorY, order);
    }

    private static EnclosureHullKey hull(String id) {
        return EnclosureHullKey.of(Collections.singletonList(EnclosureKey.of(
            SourceNodeKey.persisted(NodeReference.of(MAP, PersistedNodeId.of(id))))));
    }

    private static List<ClusterPacking.ClusterPlacement> pack(List<ClusterPacking.ClusterItem> items) {
        return ClusterPacking.packItems(items, GAP);
    }

    private static List<ClusterPacking.ClusterItem> sixItemFixture() {
        List<ClusterPacking.ClusterItem> items = new ArrayList<ClusterPacking.ClusterItem>();
        items.add(content(24.0, 18.0, 0));
        items.add(item("a", 30.0, 12.0, 3.0, -2.0, 1));
        items.add(item("b", 12.0, 30.0, -4.0, 5.0, 2));
        items.add(item("c", 20.0, 10.0, 0.0, 0.0, 3));
        items.add(item("d", 10.0, 10.0, 1.5, 1.5, 4));
        items.add(item("e", 16.0, 14.0, -2.0, -3.0, 5));
        return items;
    }

    private static void assertPlacementsEqual(List<ClusterPacking.ClusterPlacement> expected,
            List<ClusterPacking.ClusterPlacement> actual) {
        assertThat(actual).hasSameSizeAs(expected);
        for (int index = 0; index < expected.size(); index++) {
            ClusterPacking.ClusterPlacement left = expected.get(index);
            ClusterPacking.ClusterPlacement right = actual.get(index);
            assertThat(right.x).isEqualTo(left.x);
            assertThat(right.y).isEqualTo(left.y);
            assertThat(right.item.width).isEqualTo(left.item.width);
            assertThat(right.item.height).isEqualTo(left.item.height);
            assertThat(right.item.order).isEqualTo(left.item.order);
            assertThat(right.item.anchorKey).isEqualTo(left.item.anchorKey);
            assertThat(contactKeys(right)).isEqualTo(contactKeys(left));
        }
    }

    private static List<EnclosureHullKey> contactKeys(ClusterPacking.ClusterPlacement placement) {
        List<EnclosureHullKey> keys = new ArrayList<EnclosureHullKey>();
        for (ClusterPacking.ClusterItem contact : placement.contacts) {
            keys.add(contact.anchorKey);
        }
        return keys;
    }

    private static boolean recordsContact(ClusterPacking.ClusterPlacement placement,
            ClusterPacking.ClusterItem candidate) {
        for (ClusterPacking.ClusterItem contact : placement.contacts) {
            if (contact.order == candidate.order && Objects.equals(contact.anchorKey, candidate.anchorKey)
                    && contact.width == candidate.width && contact.height == candidate.height) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void emptyInputProducesNoPlacements() {
        assertThat(pack(Collections.<ClusterPacking.ClusterItem>emptyList())).isEmpty();
    }

    @Test
    public void singleItemIsPlacedAtTheOrigin() {
        List<ClusterPacking.ClusterPlacement> placements =
            pack(Collections.singletonList(content(10.0, 10.0, 0)));
        assertThat(placements).hasSize(1);
        assertThat(placements.get(0).x).isEqualTo(0.0);
        assertThat(placements.get(0).y).isEqualTo(0.0);
        assertThat(placements.get(0).contacts).isEmpty();
    }

    @Test
    public void twoItemsAreSeparatedByExactlyTheGap() {
        List<ClusterPacking.ClusterItem> items = new ArrayList<ClusterPacking.ClusterItem>();
        items.add(content(10.0, 10.0, 0));
        items.add(item("a", 10.0, 10.0, 0.0, 0.0, 1));
        List<ClusterPacking.ClusterPlacement> placements = pack(items);
        assertThat(placements).hasSize(2);
        ClusterPacking.ClusterPlacement contentPlacement = placements.get(0);
        ClusterPacking.ClusterPlacement child = placements.get(1);
        assertThat(contentPlacement.item.anchorKey).isNull();
        assertThat(child.item.anchorKey).isEqualTo(hull("a"));
        assertThat(child.x).isEqualTo(-18.0);
        assertThat(child.y).isEqualTo(0.0);
        double boxGapX = Math.abs(child.x - contentPlacement.x) - 10.0;
        double boxGapY = Math.abs(child.y - contentPlacement.y) - 10.0;
        assertThat(boxGapX).isBetween(GAP - ClusterPacking.CANDIDATE_EPSILON,
            GAP + ClusterPacking.CONTACT_GAP_TOLERANCE);
        assertThat(Math.max(boxGapX, boxGapY))
            .isGreaterThanOrEqualTo(-ClusterPacking.CANDIDATE_EPSILON);
        assertThat(contentPlacement.contacts).hasSize(1);
        assertThat(contentPlacement.contacts.get(0).anchorKey).isEqualTo(hull("a"));
        assertThat(contentPlacement.contacts.get(0).order).isEqualTo(1);
        assertThat(child.contacts).hasSize(1);
        assertThat(child.contacts.get(0).anchorKey).isNull();
        assertThat(child.contacts.get(0).order).isEqualTo(0);
    }

    @Test
    public void contentItemIsOrderedFirstAndPlacedAtTheOrigin() {
        List<ClusterPacking.ClusterItem> items = new ArrayList<ClusterPacking.ClusterItem>();
        items.add(content(100.0, 100.0, 0));
        items.add(item("a", 10.0, 10.0, 0.0, 0.0, 1));
        List<ClusterPacking.ClusterPlacement> placements = pack(items);
        assertThat(placements.get(0).item.anchorKey).isNull();
        assertThat(placements.get(0).x).isEqualTo(0.0);
        assertThat(placements.get(0).y).isEqualTo(0.0);
    }

    @Test
    public void remainingItemsAreOrderedByDescendingAreaThenProjectionOrder() {
        List<ClusterPacking.ClusterItem> items = new ArrayList<ClusterPacking.ClusterItem>();
        items.add(content(10.0, 10.0, 0));
        items.add(item("a", 30.0, 10.0, 0.0, 0.0, 2));
        items.add(item("b", 10.0, 30.0, 0.0, 0.0, 0));
        items.add(item("c", 20.0, 10.0, 0.0, 0.0, 1));
        List<ClusterPacking.ClusterPlacement> placements = pack(items);
        List<EnclosureHullKey> keys = new ArrayList<EnclosureHullKey>();
        for (ClusterPacking.ClusterPlacement placement : placements) {
            keys.add(placement.item.anchorKey);
        }
        // a (30x10) and b (10x30) both have area 300.0 and tie on the
        // descending-area key, so the ascending-order key decides: b (order 0)
        // precedes a (order 2). c (area 200.0) follows. The brief's fixture is
        // kept verbatim; the brief's stated expected list "null, a, b, c"
        // contradicts its own fixture and spec section 3.2 step 1.
        assertThat(keys).containsExactly((EnclosureHullKey) null, hull("b"), hull("a"), hull("c"));
    }

    @Test
    public void selectionMinimisesTheAssemblyBoundingBoxArea() {
        List<ClusterPacking.ClusterItem> items = new ArrayList<ClusterPacking.ClusterItem>();
        items.add(content(10.0, 10.0, 0));
        items.add(item("wide", 100.0, 2.0, 0.0, 0.0, 1));
        List<ClusterPacking.ClusterPlacement> placements = pack(items);
        ClusterPacking.ClusterPlacement wide = placements.get(1);
        assertThat(wide.item.anchorKey).isEqualTo(hull("wide"));
        assertThat(wide.x).isEqualTo(-5.0 - 100.0 * 0.5 - GAP);
        assertThat(wide.x).isEqualTo(-63.0);
        assertThat(wide.y).isEqualTo(0.0);
    }

    @Test
    public void equalAreaCandidatesTieBreakByDistanceFromTheOrigin() {
        List<ClusterPacking.ClusterItem> items = new ArrayList<ClusterPacking.ClusterItem>();
        items.add(content(10.0, 10.0, 0));
        items.add(item("wide", 100.0, 2.0, 0.0, 0.0, 1));
        items.add(item("c", 10.0, 10.0, 0.0, 0.0, 2));
        List<ClusterPacking.ClusterPlacement> placements = pack(items);
        ClusterPacking.ClusterPlacement third = placements.get(2);
        assertThat(third.x).isEqualTo(18.0);
        assertThat(third.y).isEqualTo(0.0);
    }

    @Test
    public void equalAreaAndDistanceCandidatesTieBreakByEnumerationOrder() {
        List<ClusterPacking.ClusterItem> items = new ArrayList<ClusterPacking.ClusterItem>();
        items.add(content(10.0, 10.0, 0));
        items.add(item("a", 10.0, 10.0, 0.0, 0.0, 1));
        List<ClusterPacking.ClusterPlacement> placements = pack(items);
        ClusterPacking.ClusterPlacement second = placements.get(1);
        assertThat(second.x).isEqualTo(-18.0);
        assertThat(second.y).isEqualTo(0.0);
    }

    @Test
    public void centreLineCandidateIsUsed() {
        List<ClusterPacking.ClusterItem> items = new ArrayList<ClusterPacking.ClusterItem>();
        items.add(content(10.0, 10.0, 0));
        items.add(item("wide", 100.0, 2.0, 0.0, 0.0, 1));
        items.add(item("c", 10.0, 10.0, 0.0, 0.0, 2));
        List<ClusterPacking.ClusterPlacement> placements = pack(items);
        ClusterPacking.ClusterPlacement third = placements.get(2);
        assertThat(third.x).isEqualTo(18.0);
        assertThat(third.y).isEqualTo(0.0);
        // The wide item contributes only y = +-14.0 edge candidates
        // (|0| + (2 + 10) * 0.5 + GAP); y = 0.0 is reached from the placed
        // items' centre lines, not from the wide item's edges.
        double wideEdgeY = (2.0 + 10.0) * 0.5 + GAP;
        assertThat(third.y).isNotEqualTo(wideEdgeY);
        assertThat(third.y).isNotEqualTo(-wideEdgeY);
    }

    @Test
    public void assemblyBoxEdgeCandidateIsUsed() {
        List<ClusterPacking.ClusterItem> items = new ArrayList<ClusterPacking.ClusterItem>();
        items.add(content(10.0, 10.0, 0));
        items.add(item("wide", 100.0, 2.0, 0.0, 0.0, 1));
        List<ClusterPacking.ClusterPlacement> placements = pack(items);
        ClusterPacking.ClusterPlacement wide = placements.get(1);
        double assemblyMinX = -5.0;
        assertThat(wide.x).isEqualTo(assemblyMinX - 100.0 * 0.5 - GAP);
        assertThat(wide.x).isEqualTo(-63.0);
        assertThat(wide.y).isEqualTo(0.0);
    }

    @Test
    public void inputListOrderDoesNotAffectPlacements() {
        List<ClusterPacking.ClusterItem> items = sixItemFixture();
        List<ClusterPacking.ClusterItem> originalOrder = new ArrayList<ClusterPacking.ClusterItem>(items);
        List<ClusterPacking.ClusterPlacement> first = pack(items);
        assertThat(items).containsExactlyElementsOf(originalOrder);
        List<ClusterPacking.ClusterItem> copy = new ArrayList<ClusterPacking.ClusterItem>(items);
        Collections.shuffle(copy, new Random(20261013L));
        List<ClusterPacking.ClusterPlacement> second = pack(copy);
        assertPlacementsEqual(first, second);
    }

    @Test
    public void placementsAreDeterministicAcrossRepeatedCalls() {
        List<ClusterPacking.ClusterItem> items = sixItemFixture();
        List<ClusterPacking.ClusterPlacement> first = pack(items);
        List<ClusterPacking.ClusterPlacement> second = pack(items);
        List<ClusterPacking.ClusterPlacement> third = pack(sixItemFixture());
        assertPlacementsEqual(first, second);
        assertPlacementsEqual(first, third);
    }

    @Test
    public void everyPairIsSeparatedOnAtLeastOneAxis() {
        List<ClusterPacking.ClusterPlacement> placements = pack(sixItemFixture());
        for (int first = 0; first < placements.size(); first++) {
            for (int second = first + 1; second < placements.size(); second++) {
                ClusterPacking.ClusterPlacement left = placements.get(first);
                ClusterPacking.ClusterPlacement right = placements.get(second);
                double boxGapX = Math.abs(left.x - right.x)
                    - (left.item.width + right.item.width) * 0.5;
                double boxGapY = Math.abs(left.y - right.y)
                    - (left.item.height + right.item.height) * 0.5;
                assertThat(Math.max(boxGapX, boxGapY))
                    .isGreaterThanOrEqualTo(-ClusterPacking.CANDIDATE_EPSILON);
            }
        }
    }

    @Test
    public void recordedContactsSatisfyTheContactPredicate() {
        List<ClusterPacking.ClusterPlacement> placements = pack(sixItemFixture());
        for (int first = 0; first < placements.size(); first++) {
            for (int second = first + 1; second < placements.size(); second++) {
                ClusterPacking.ClusterPlacement left = placements.get(first);
                ClusterPacking.ClusterPlacement right = placements.get(second);
                double boxGapX = Math.abs(left.x - right.x)
                    - (left.item.width + right.item.width) * 0.5;
                double boxGapY = Math.abs(left.y - right.y)
                    - (left.item.height + right.item.height) * 0.5;
                boolean recorded = recordsContact(left, right.item) || recordsContact(right, left.item);
                boolean xSeparates = boxGapX <= GAP + ClusterPacking.CONTACT_GAP_TOLERANCE
                    && boxGapY < -ClusterPacking.CONTACT_GAP_TOLERANCE;
                boolean ySeparates = boxGapY <= GAP + ClusterPacking.CONTACT_GAP_TOLERANCE
                    && boxGapX < -ClusterPacking.CONTACT_GAP_TOLERANCE;
                assertThat(recorded).isEqualTo(xSeparates || ySeparates);
                if (recorded) {
                    double separatingGap = xSeparates ? boxGapX : boxGapY;
                    assertThat(separatingGap).isBetween(GAP - ClusterPacking.CANDIDATE_EPSILON,
                        GAP + ClusterPacking.CONTACT_GAP_TOLERANCE);
                }
            }
        }
    }

    @Test
    public void duplicateAnchorKeysAreRejected() {
        List<ClusterPacking.ClusterItem> items = new ArrayList<ClusterPacking.ClusterItem>();
        items.add(item("a", 10.0, 10.0, 0.0, 0.0, 0));
        items.add(item("a", 10.0, 10.0, 0.0, 0.0, 1));
        assertThatThrownBy(() -> pack(items)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void invalidDimensionsAndGapAreRejected() {
        assertThatThrownBy(() -> ClusterPacking.packItems(null, GAP))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> pack(Collections.<ClusterPacking.ClusterItem>singletonList(null)))
            .isInstanceOf(NullPointerException.class);
        assertInvalidDimensions(0.0, 10.0);
        assertInvalidDimensions(-1.0, 10.0);
        assertInvalidDimensions(Double.NaN, 10.0);
        assertInvalidDimensions(Double.POSITIVE_INFINITY, 10.0);
        assertInvalidDimensions(10.0, 0.0);
        assertInvalidDimensions(10.0, -1.0);
        assertInvalidDimensions(10.0, Double.NaN);
        assertInvalidDimensions(10.0, Double.POSITIVE_INFINITY);
        assertThatThrownBy(() -> ClusterPacking.packItems(
            Collections.singletonList(content(10.0, 10.0, 0)), -1.0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClusterPacking.packItems(
            Collections.singletonList(content(10.0, 10.0, 0)), Double.NaN))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClusterPacking.packItems(
            Collections.singletonList(content(10.0, 10.0, 0)), Double.POSITIVE_INFINITY))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static void assertInvalidDimensions(double width, double height) {
        assertThatThrownBy(() -> pack(Collections.singletonList(content(width, height, 0))))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
