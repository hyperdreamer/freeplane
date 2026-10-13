# Graph Workspace Initial Cluster Proximity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking. Controller state is canonical;
> leave the boxes unticked.

- **Task Identifier:** 2026-10-13-graph-cluster-initial-proximity
- **Specification (authoritative contract):** `docs/superpowers/specs/2026-10-13-graph-cluster-initial-proximity-spec.md`
- **Approved design:** `docs/superpowers/specs/2026-10-13-graph-cluster-initial-proximity-design.md` (v7, scope decision A)
- **Base commit:** `9b8259bcd1cb2c8d37121e63bcac0107386fed0d`, plus the design commit `5610934d62`
- **Prototype evidence (read-only; do not edit):** `docs/superpowers/specs/mockups/2026-10-13-graph-cluster-initial-proximity/prototype/`
- **Integration worktree:** the worktree this plan was written in (repo root from `git rev-parse --show-toplevel`)

**Goal:** Seed every fresh Graph Workspace layout with the smallest anchor-centred per-axis
footprints the layout can justify, packed deterministically at `SIBLING_GAP = 8.0` at both
levels (separate maps and nested sibling sub-clusters), with hierarchy rest lengths taken from
the packed parent-child anchor distance and anchor separation made offset-aware; settled
behaviour is reported, not held.

**Architecture:** `ClusterPacking` is the single, pure placement authority. `BoundarySizes`
memoizes anchor-centred `Footprint`s and `ClusterPacking` placements (content item + direct
child footprints) and is the only producer of seeding geometry. `Seeds` consumes the memoized
placements; `topology()` derives hierarchy rest lengths from them; `TypedSpringBox` separates
per-axis footprint boxes instead of circles. `LayoutCalibration.containment()` rises to `1.0`
as the calibrated companion of the new `NODE_SETTLE_MARGIN = 8.0`. Scope A: no holding
mechanism, no displacement caps, no label-space reservation.

**Tech Stack:** Java 8 source level run on Zulu 21 (`~/.sdkman/candidates/java/21.0.8-zulu`),
Gradle multi-project build (module `freeplane_plugin_graph`), JUnit 4 with AssertJ, GraphStream
1.3 confined to the package-private `org.freeplane.plugin.graph.layout.graphstream` adapter.

## Global Constraints

- Toolchain: before every Gradle command run `export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"` and `export PATH="$JAVA_HOME/bin:$PATH"`; use `gradle`, never `gradlew` or Maven. The module test command is `gradle :freeplane_plugin_graph:test`; add `-PTestLoggingFull` whenever a run must show full failure output.
- Java 8 source level, UTF-8, four-space indentation, JUnit 4 + AssertJ. Production excerpts use Java 8 syntax.
- All production and test changes live in `freeplane_plugin_graph`. `freeplane_api` is untouched. No Gradle/build file changes; Gradle compiles every source file under `src/test/java`.
- No other production or test file may change. The complete allowed file surface is: `LayoutCalibration.java`, `GraphStreamLayoutEngine.java`, `ClusterPacking.java` (new), `TypedSpringBox.java`, `TypedNodeParticle.java`, `ClusterPackingShould.java` (new), `BoundaryFootprintShould.java` (new), `ClusterProximityShould.java` (new), `TypedForcesShould.java`, `BoundarySeparationShould.java`, `ReferenceRepulsionFixture.java`. Tasks 9 and 10 additionally write evidence only, never production or test code.
- Scope A boundary: do not add a holding mechanism, packing skeleton, contact springs, top-level packing links, frozen rest lengths, per-step displacement caps, or any new `ForceKind`; do not reserve label space in the layout. The prototype overlay's `rawPackingDisplacement`/`rawBoundaryDisplacement` fields, its three-argument `addBoundaryRepulsion`, and its cap-limit constants are deleted-experiment scaffolding and must not be ported.
- Constants (exact values): `SIBLING_GAP = 8.0`, `NODE_SETTLE_MARGIN = 8.0` (new), `FRAME_CLEARANCE = 16.0`, `CHAR_WIDTH_UPPER_BOUND = 16.0`, `CHAR_HEIGHT_UPPER_BOUND = 24.0`, `BOUNDARY_PADDING = 8.0`, `CANDIDATE_EPSILON = 1e-9` (new), `CONTACT_GAP_TOLERANCE = 0.75` (new), `CONTAINMENT = 1.0` (was `0.15`), `HIERARCHY = 0.30`, `SAME_MAP = 1.0`, `BOUNDARY_REPULSION_FACTOR = 0.5`, `CROSS_MAP_DISPLACEMENT_LIMIT = 0.005`, `NODE_RADIUS = 8.0`, `NodeSeparationProjection.MIN_GAP = 6.0`.
- Acceptance tolerances: a contact gutter is in `[SIBLING_GAP - CANDIDATE_EPSILON, SIBLING_GAP + CONTACT_GAP_TOLERANCE]`; non-overlap of any packed pair is `max(boxGapX, boxGapY) >= -CANDIDATE_EPSILON`; the packed-position separation-box invariant is `forceGapX >= -CANDIDATE_EPSILON || forceGapY >= -CANDIDATE_EPSILON` for non-ancestor pairs. An assertion of the form `gap == 8.0` or `gap >= 8.0` must not be written.
- Documented endpoint: run to the first idle frame, else the 2000th step, then always 200 extra steps; the stability window is the next 1000 steps. Idle within the 2000-step budget and zero all-pairs non-ancestor painted crossings are asserted; settled hold deviation, painted gaps, containment overflow and marginal overlaps are reported, never asserted.
- 200-child wall clock: `<= 3.0 s` for `LayoutWorker.submit` (measured 0.580 s submit / 1.252 s for 10 steps).
- Settled and reported values are quoted from spec sections 8.3.2-8.3.5 only; values from `probe-cap-sweep.txt`, `probe-containment-sweep.txt` and `superseded-v2/` must not be cited. The committed prototype artifacts `probe-final.txt` and `probe-baseline.txt` must not be edited.
- Commit subject must end with the commit identifier `[2026-10-13-graph-cluster-initial-proximity]`. Stage exactly the files named by the task; never amend a commit.


## Task 1: Raise the containment calibration and re-order the calibration assertion

**Implementer tier:** Fast
**Lane:** calibration
**Depends on:** none

**Ownership:** `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutCalibration.java`, `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/GraphStreamBoundaryShould.java`

**Validation:** `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.GraphStreamBoundaryShould`

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutCalibration.java:4`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/GraphStreamBoundaryShould.java:78-86`

**Interfaces:**

- Consumes: `LayoutCalibration.spikeDefaults()`, `containment()`, `hierarchy()`, `sameMap()` (signatures unchanged).
- Produces: `LayoutCalibration.spikeDefaults().containment() == 1.0`, `hierarchy() == 0.30`, `sameMap() == 1.0`. Containment springs are now as stiff as same-map links, which is why the ordering assertion changes.

- [ ] **Step 1: Rewrite the failing ordering assertion**

Replace the body of `provideOrderedPositiveCalibrationStrengths` (currently lines 78-86) with exactly:

```java
    @Test
    public void provideOrderedPositiveCalibrationStrengths() {
        LayoutCalibration calibration = LayoutCalibration.spikeDefaults();

        assertThat(calibration.containment()).isPositive();
        assertThat(calibration.hierarchy()).isPositive();
        assertThat(calibration.sameMap()).isPositive();
        assertThat(calibration.containment()).isEqualTo(1.0);
        assertThat(calibration.hierarchy()).isEqualTo(0.30);
        assertThat(calibration.sameMap()).isEqualTo(1.0);
        assertThat(calibration.hierarchy()).isLessThan(calibration.containment());
        assertThat(calibration.containment()).isLessThanOrEqualTo(calibration.sameMap());
    }
```

The old ordering `containment() < hierarchy()` no longer expresses the intended stiffness: containment springs are now as stiff as same-map links.

- [ ] **Step 2: Run the test and confirm it fails**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.layout.GraphStreamBoundaryShould" -PTestLoggingFull
```

Expected: `FAILED`, 5 tests, 1 failure; the message is `Expecting actual: 0.15 to be equal to: 1.0` from the rewritten method. This is the documented `containment() = 0.15` red state.

- [ ] **Step 3: Raise the constant**

In `LayoutCalibration.java`, change only the first constant initializer:

```java
    private static final double CONTAINMENT = 1.0;   // was 0.15
    private static final double HIERARCHY   = 0.30;  // unchanged
    private static final double SAME_MAP    = 1.0;   // unchanged
```

Do not touch the constructor, `spikeDefaults()`, or any accessor.

- [ ] **Step 4: Run the test and confirm it passes**

```bash
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.layout.GraphStreamBoundaryShould"
```

Expected: `BUILD SUCCESSFUL`, 5 tests, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutCalibration.java \
        freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/GraphStreamBoundaryShould.java
git commit -m "feat(graph): raise containment stiffness to 1.0 [2026-10-13-graph-cluster-initial-proximity]"
```


## Task 2: Deterministic cluster packer with unit tests

**Implementer tier:** Standard
**Lane:** packing
**Depends on:** none

**Ownership:** `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/ClusterPacking.java` (new), `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/ClusterPackingShould.java` (new)

**Validation:** `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.graphstream.ClusterPackingShould`

**Files:**

- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/ClusterPacking.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/ClusterPackingShould.java`

**Interfaces:**

- Consumes: `EnclosureHullKey` (identity only; the packer never reads projection data), `Double.compare`, `Math.hypot`.
- Produces: the exact package-private surface in spec section 2.3:
  `static List<ClusterPlacement> packItems(List<ClusterItem> items, double gap)`, constants `CANDIDATE_EPSILON = 1e-9` and `CONTACT_GAP_TOLERANCE = 0.75`, and the nested immutable payload types `ClusterItem(EnclosureHullKey anchorKey, double width, double height, double anchorX, double anchorY, int order)` and `ClusterPlacement(ClusterItem item, double x, double y)` with `final List<ClusterItem> contacts = new ArrayList<ClusterItem>()`. `anchorKey == null` marks the content item. All members are package-private; no GraphStream type enters the signature.
- `packItems` is pure and stateless: it copies before sorting, allocates new placements, never mutates `items`, and is deterministic for equal inputs.

- [ ] **Step 1: Write the production class**

Create `ClusterPacking.java` with exactly this content (the algorithm is spec section 3.2; the code below is its transcription):

```java
package org.freeplane.plugin.graph.layout.graphstream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.freeplane.plugin.graph.projection.EnclosureHullKey;

/**
 * Deterministic V1 rectangle packer: the single placement authority for seeding
 * and footprints. Pure and stateless; contacts are metadata no force reads.
 */
final class ClusterPacking {
    static final double CANDIDATE_EPSILON = 1e-9;
    static final double CONTACT_GAP_TOLERANCE = 0.75;

    private static final Comparator<ClusterItem> ITEM_ORDER = new Comparator<ClusterItem>() {
        @Override
        public int compare(final ClusterItem first, final ClusterItem second) {
            if ((first.anchorKey == null) != (second.anchorKey == null)) {
                return first.anchorKey == null ? -1 : 1;
            }
            final int byArea = Double.compare(second.width * second.height, first.width * first.height);
            if (byArea != 0) {
                return byArea;
            }
            return Integer.compare(first.order, second.order);
        }
    };

    private ClusterPacking() {
    }

    static List<ClusterPlacement> packItems(final List<ClusterItem> items, final double gap) {
        Objects.requireNonNull(items, "items");
        requireFiniteNonNegative(gap, "gap");
        final List<ClusterItem> ordered = new ArrayList<ClusterItem>(items.size());
        final Set<EnclosureHullKey> seen = new LinkedHashSet<EnclosureHullKey>();
        for (final ClusterItem item : items) {
            Objects.requireNonNull(item, "item");
            requirePositiveFinite(item.width, "width");
            requirePositiveFinite(item.height, "height");
            if (item.anchorKey != null && !seen.add(item.anchorKey)) {
                throw new IllegalArgumentException("duplicate anchor key " + item.anchorKey);
            }
            ordered.add(item);
        }
        if (ordered.isEmpty()) {
            return new ArrayList<ClusterPlacement>();
        }
        Collections.sort(ordered, ITEM_ORDER);
        final List<ClusterPlacement> placed = new ArrayList<ClusterPlacement>(ordered.size());
        for (final ClusterItem item : ordered) {
            placed.add(place(item, placed, gap));
        }
        return placed;
    }

    private static ClusterPlacement place(final ClusterItem item, final List<ClusterPlacement> placed,
            final double gap) {
        if (placed.isEmpty()) {
            return new ClusterPlacement(item, 0.0, 0.0);
        }
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        final List<Double> xs = new ArrayList<Double>();
        final List<Double> ys = new ArrayList<Double>();
        for (final ClusterPlacement p : placed) {
            xs.add(Double.valueOf(p.x - (p.item.width + item.width) * 0.5 - gap));
            xs.add(Double.valueOf(p.x + (p.item.width + item.width) * 0.5 + gap));
            ys.add(Double.valueOf(p.y - (p.item.height + item.height) * 0.5 - gap));
            ys.add(Double.valueOf(p.y + (p.item.height + item.height) * 0.5 + gap));
            xs.add(Double.valueOf(p.x));
            ys.add(Double.valueOf(p.y));
            minX = Math.min(minX, p.x - p.item.width * 0.5);
            maxX = Math.max(maxX, p.x + p.item.width * 0.5);
            minY = Math.min(minY, p.y - p.item.height * 0.5);
            maxY = Math.max(maxY, p.y + p.item.height * 0.5);
        }
        xs.add(Double.valueOf(minX - item.width * 0.5 - gap));
        xs.add(Double.valueOf(maxX + item.width * 0.5 + gap));
        xs.add(Double.valueOf(minX + item.width * 0.5));
        xs.add(Double.valueOf(maxX - item.width * 0.5));
        ys.add(Double.valueOf(minY - item.height * 0.5 - gap));
        ys.add(Double.valueOf(maxY + item.height * 0.5 + gap));
        ys.add(Double.valueOf(minY + item.height * 0.5));
        ys.add(Double.valueOf(maxY - item.height * 0.5));

        final List<Double> sortedX = dedupeSorted(xs);
        final List<Double> sortedY = dedupeSorted(ys);
        ClusterPlacement best = null;
        double bestArea = 0.0;
        double bestRadius = 0.0;
        int bestIndex = 0;
        int index = 0;
        for (final Double xValue : sortedX) {
            for (final Double yValue : sortedY) {
                index++;
                final double x = xValue.doubleValue();
                final double y = yValue.doubleValue();
                if (!isClear(x, y, item, placed, gap)) {
                    continue;
                }
                final double area = assemblyArea(x, y, item, placed);
                final double radius = Math.hypot(x, y);
                if (!isBetter(area, radius, index, best != null, bestArea, bestRadius, bestIndex)) {
                    continue;
                }
                best = new ClusterPlacement(item, x, y);
                bestArea = area;
                bestRadius = radius;
                bestIndex = index;
            }
        }
        if (best == null) {
            throw new IllegalStateException("Cluster packing found no free slot");
        }
        recordContacts(best, placed, gap);
        return best;
    }

    private static List<Double> dedupeSorted(final List<Double> values) {
        Collections.sort(values);
        final List<Double> result = new ArrayList<Double>(values.size());
        for (final Double value : values) {
            if (result.isEmpty()
                    || Math.abs(value.doubleValue() - result.get(result.size() - 1).doubleValue())
                        > CANDIDATE_EPSILON) {
                result.add(value);
            }
        }
        return result;
    }

    private static boolean isClear(final double x, final double y, final ClusterItem item,
            final List<ClusterPlacement> placed, final double gap) {
        for (final ClusterPlacement p : placed) {
            final double clearanceX = Math.abs(x - p.x) - (item.width + p.item.width) * 0.5;
            final double clearanceY = Math.abs(y - p.y) - (item.height + p.item.height) * 0.5;
            if (clearanceX < gap && clearanceY < gap) {
                return false;
            }
        }
        return true;
    }

    private static double assemblyArea(final double x, final double y, final ClusterItem item,
            final List<ClusterPlacement> placed) {
        double minX = x - item.width * 0.5;
        double maxX = x + item.width * 0.5;
        double minY = y - item.height * 0.5;
        double maxY = y + item.height * 0.5;
        for (final ClusterPlacement p : placed) {
            minX = Math.min(minX, p.x - p.item.width * 0.5);
            maxX = Math.max(maxX, p.x + p.item.width * 0.5);
            minY = Math.min(minY, p.y - p.item.height * 0.5);
            maxY = Math.max(maxY, p.y + p.item.height * 0.5);
        }
        return (maxX - minX) * (maxY - minY);
    }

    private static boolean isBetter(final double area, final double radius, final int index,
            final boolean found, final double bestArea, final double bestRadius, final int bestIndex) {
        if (!found) {
            return true;
        }
        if (area < bestArea - CANDIDATE_EPSILON) {
            return true;
        }
        if (Math.abs(area - bestArea) > CANDIDATE_EPSILON) {
            return false;
        }
        if (radius < bestRadius - CANDIDATE_EPSILON) {
            return true;
        }
        return Math.abs(radius - bestRadius) <= CANDIDATE_EPSILON && index < bestIndex;
    }

    private static void recordContacts(final ClusterPlacement placement, final List<ClusterPlacement> placed,
            final double gap) {
        for (final ClusterPlacement p : placed) {
            final double boxGapX = Math.abs(placement.x - p.x)
                - (placement.item.width + p.item.width) * 0.5;
            final double boxGapY = Math.abs(placement.y - p.y)
                - (placement.item.height + p.item.height) * 0.5;
            if ((boxGapX <= gap + CONTACT_GAP_TOLERANCE && boxGapY < -CONTACT_GAP_TOLERANCE)
                    || (boxGapY <= gap + CONTACT_GAP_TOLERANCE && boxGapX < -CONTACT_GAP_TOLERANCE)) {
                placement.contacts.add(p.item);
                p.contacts.add(placement.item);
            }
        }
    }

    private static void requirePositiveFinite(final double value, final String name) {
        if (!(value > 0.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be positive and finite");
        }
    }

    private static void requireFiniteNonNegative(final double value, final String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }

    static final class ClusterItem {
        final EnclosureHullKey anchorKey;
        final double width;
        final double height;
        final double anchorX;
        final double anchorY;
        final int order;

        ClusterItem(final EnclosureHullKey anchorKey, final double width, final double height,
                final double anchorX, final double anchorY, final int order) {
            this.anchorKey = anchorKey;
            this.width = width;
            this.height = height;
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.order = order;
        }
    }

    static final class ClusterPlacement {
        final ClusterItem item;
        final double x;
        final double y;
        final List<ClusterItem> contacts = new ArrayList<ClusterItem>();

        ClusterPlacement(final ClusterItem item, final double x, final double y) {
            this.item = item;
            this.x = x;
            this.y = y;
        }
    }
}
```

The candidate set, dedupe, clearance, selection key and contacts predicate are the contract; do not add a polar/lattice fallback and do not add a fallback-path test. The defensive `IllegalStateException` is provably unreachable for non-empty placed sets (spec section 3.2 step 5).

- [ ] **Step 2: Write the test class with its exact assertions**

Create `ClusterPackingShould.java` in package `org.freeplane.plugin.graph.layout.graphstream` with the helpers and the sixteen tests below. Use `import static org.assertj.core.api.Assertions.assertThat;` and `import static org.assertj.core.api.Assertions.assertThatThrownBy;`. Shared helpers (exact):

```java
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
```

Test-by-test assertions (each test creates its own item list; no test reads another test's state):

1. `emptyInputProducesNoPlacements` - `assertThat(pack(Collections.<ClusterPacking.ClusterItem>emptyList())).isEmpty();`
2. `singleItemIsPlacedAtTheOrigin` - pack `content(10.0, 10.0, 0)`; assert size 1, `placements.get(0).x == 0.0`, `y == 0.0`, `contacts` empty.
3. `twoItemsAreSeparatedByExactlyTheGap` - pack `content(10.0, 10.0, 0)` and `item("a", 10.0, 10.0, 0.0, 0.0, 1)`. Expected child placement `(-18.0, 0.0)`. Assert:
   - `boxGapX = Math.abs(child.x - content.x) - 10.0` is in `[GAP - ClusterPacking.CANDIDATE_EPSILON, GAP + ClusterPacking.CONTACT_GAP_TOLERANCE]` (measured contact gaps bottom out at `7.999999999999972`, hence the lower tolerance);
   - `Math.max(boxGapX, boxGapY) >= -ClusterPacking.CANDIDATE_EPSILON` with `boxGapY = Math.abs(child.y - content.y) - 10.0`;
   - `content.contacts` contains exactly the child item and `child.contacts` contains exactly the content item (compare by `anchorKey`/`order`, not list identity).
4. `contentItemIsOrderedFirstAndPlacedAtTheOrigin` - pack `content(100.0, 100.0, 0)` plus `item("a", 10.0, 10.0, 0.0, 0.0, 1)`. Assert `placements.get(0).item.anchorKey == null` and `placements.get(0).x == 0.0`, `y == 0.0` even though the child has smaller area.
5. `remainingItemsAreOrderedByDescendingAreaThenProjectionOrder` - pack `content(10.0, 10.0, 0)`, `item("a", 30.0, 10.0, 0.0, 0.0, 2)`, `item("b", 10.0, 30.0, 0.0, 0.0, 0)`, `item("c", 20.0, 10.0, 0.0, 0.0, 1)` (equal areas for `b` and `c`). Assert placement order of `item.anchorKey` is `null`, `a`, `b`, `c` (equal areas keep ascending `order`: `b` before `c`).
6. `selectionMinimisesTheAssemblyBoundingBoxArea` - pack `content(10.0, 10.0, 0)` and `item("wide", 100.0, 2.0, 0.0, 0.0, 1)`. The nearest clear candidate `(0.0, 14.0)` has assembly area 2000.0, while `(-63.0, 0.0)` has 1180.0; assert the wide item is at exactly `(-63.0, 0.0)` and that this is the assembly-box edge `minX - width/2 - gap = -5.0 - 50.0 - 8.0`.
7. `equalAreaCandidatesTieBreakByDistanceFromTheOrigin` - pack `content(10.0, 10.0, 0)`, `item("wide", 100.0, 2.0, 0.0, 0.0, 1)`, `item("c", 10.0, 10.0, 0.0, 0.0, 2)`. After `wide` is placed at `(-63.0, 0.0)`, the equal-area competitors for `c` are `(-126.0, 0.0)` (area 1360.0) and `(18.0, 0.0)` (area 1360.0); assert `c` is at exactly `(18.0, 0.0)` (radius 18.0 beats radius 126.0).
8. `equalAreaAndDistanceCandidatesTieBreakByEnumerationOrder` - pack `content(10.0, 10.0, 0)` and `item("a", 10.0, 10.0, 0.0, 0.0, 1)`. The four clear candidates `(-18,0)`, `(0,-18)`, `(0,18)`, `(18,0)` all have area 280.0 and radius 18.0; ascending-X-then-Y enumeration makes `(-18.0, 0.0)` the winner. Assert exactly that.
9. `centreLineCandidateIsUsed` - reuse the three-item case of test 7: assert the third placement is at exactly `(18.0, 0.0)`, whose `y` is the first placed item's centre line; also assert the enumeration would not reach `y = 0.0` from any edge candidate of the wide item alone (documented in the test comment).
10. `assemblyBoxEdgeCandidateIsUsed` - reuse the two-item case of test 6: assert the wide item is at `(-63.0, 0.0)`, the assembly-box outer edge `minX - item.width/2 - SIBLING_GAP` of the placed content item.
11. `inputListOrderDoesNotAffectPlacements` - build one fixed six-item list (mix of widths/heights/anchor offsets and orders); pack it, shuffle a copy with `Collections.shuffle(copy, new Random(20261013L))`, pack again, and assert field-wise equality (`x`, `y`, `item.width`, `item.height`, `item.anchorKey`, `item.order`) plus equal `contacts` contents (`anchorKey` lists).
12. `placementsAreDeterministicAcrossRepeatedCalls` - pack the same list twice and once from freshly constructed identical items; assert field-wise equality and equal `contacts` contents, as in test 11.
13. `everyPairIsSeparatedOnAtLeastOneAxis` - the six-item list of test 11: for every pair assert `Math.max(boxGapX, boxGapY) >= -ClusterPacking.CANDIDATE_EPSILON` with `boxGapX = |x1-x2| - (w1+w2)/2` and `boxGapY = |y1-y2| - (h1+h2)/2`.
14. `recordedContactsSatisfyTheContactPredicate` - the six-item list of test 11: for every recorded contact the separating-axis gap is in `[GAP - ClusterPacking.CANDIDATE_EPSILON, GAP + ClusterPacking.CONTACT_GAP_TOLERANCE]`; for every non-recorded pair the contact predicate (`(boxGapX <= GAP + 0.75 && boxGapY < -0.75) || (boxGapY <= GAP + 0.75 && boxGapX < -0.75)`) is false. Compare recorded `contacts` by contents.
15. `duplicateAnchorKeysAreRejected` - pack `item("a", 10.0, 10.0, 0.0, 0.0, 0)` twice; assert `IllegalArgumentException`.
16. `invalidDimensionsAndGapAreRejected` - assert `NullPointerException` for `packItems(null, GAP)` and for a list containing `null`; `IllegalArgumentException` for width `0.0`, `-1.0`, `Double.NaN`, `Double.POSITIVE_INFINITY`, and likewise for height, and for `packItems(singletonList(content(10.0, 10.0, 0)), -1.0)`, `Double.NaN`, `Double.POSITIVE_INFINITY`.

- [ ] **Step 3: Run the test and confirm it fails for the missing API**

The test file compiles against `ClusterPacking` only after Step 1; if you write Step 2 before Step 1, record the compile error `cannot find symbol: class ClusterPacking` as the red state, then implement Step 1 and continue. With Step 1 in place, run:

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.layout.graphstream.ClusterPackingShould" -PTestLoggingFull
```

Expected: `BUILD SUCCESSFUL`, 16 tests, 0 failures (Step 1 and Step 2 land in one task; the red state is the compile error when Step 2 is written first, or a deliberately inverted selection key if you want a behavioural red first).

- [ ] **Step 4: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/ClusterPacking.java \
        freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/ClusterPackingShould.java
git commit -m "feat(graph): add deterministic cluster packer [2026-10-13-graph-cluster-initial-proximity]"
```


## Task 3: Anchor-centred per-axis footprints and packed placements in BoundarySizes

**Implementer tier:** Standard
**Lane:** footprint
**Depends on:** Task 2

**Ownership:** `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java` (the `BoundarySizes` nested class only; additive), `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/BoundaryFootprintShould.java` (new)

**Validation:** `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.graphstream.BoundaryFootprintShould`

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java` (`BoundarySizes` constants and fields near the top of the class, and new methods before the existing `enclosure(...)` accessor)
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/BoundaryFootprintShould.java`

**Interfaces:**

- Consumes: `ClusterPacking.packItems(List<ClusterItem>, double)` and `ClusterPacking.ClusterItem`/`ClusterPlacement` from Task 2; existing `contentRingRadius(EnclosureHullKey)`, `discRadius(ProjectedNodeKey)`, `enclosure(EnclosureHullKey)`, `enclosures()`.
- Produces (package-private, spec section 2.2/2.4):
  `static final class Footprint { final double width; final double height; final double anchorX; final double anchorY; Footprint(double, double, double, double); }`;
  `static final double NODE_SETTLE_MARGIN = 8.0`;
  `Footprint footprint(EnclosureHullKey key)`;
  `List<ClusterPacking.ClusterPlacement> placements(EnclosureHullKey key)`;
  `ClusterPacking.ClusterPlacement placementOf(EnclosureHullKey parentKey, EnclosureHullKey childKey)`;
  `List<ClusterPacking.ClusterPlacement> topLevelPlacements()`;
  `ClusterPacking.ClusterPlacement topLevelPlacementOf(EnclosureHullKey key)`.
- This task is additive: it leaves every existing `BoundarySizes` member in place so the module still compiles and passes. Task 4 switches consumers and deletes the legacy estimator.

- [ ] **Step 1: Add the footprint and placement members**

Add `NODE_SETTLE_MARGIN = 8.0` to the `BoundarySizes` constants. Add the memoization fields next to the existing private fields:

```java
        private final Map<EnclosureHullKey, Footprint> footprints = new LinkedHashMap<EnclosureHullKey, Footprint>();
        private final Map<EnclosureHullKey, List<ClusterPacking.ClusterPlacement>> packedPlacements =
            new LinkedHashMap<EnclosureHullKey, List<ClusterPacking.ClusterPlacement>>();
        private List<ClusterPacking.ClusterPlacement> topLevelPlacements;
```

Add these methods (spec sections 3.1 and 2.4). `footprint` is memoized and recurses only through `placements`; `placements` is memoized and recurses only through child `footprint`s, so each enclosure evaluates once.

```java
        Footprint footprint(final EnclosureHullKey key) {
            final Footprint cached = footprints.get(key);
            if (cached != null) {
                return cached;
            }
            final ProjectedEnclosure enclosure = enclosuresByHull.get(key);
            final Footprint result;
            if (enclosure.directNodes().isEmpty() && enclosure.directEnclosures().isEmpty()) {
                // Empty enclosure: the existing label-sized box is the only remaining use of
                // the character/padding constants.
                double width = 2.0 * BOUNDARY_PADDING;
                double height = CHAR_HEIGHT_UPPER_BOUND + 2.0 * BOUNDARY_PADDING;
                for (final SafeNodeLabel label : enclosure.labels()) {
                    width = Math.max(width,
                        label.displayText().length() * CHAR_WIDTH_UPPER_BOUND + 2.0 * BOUNDARY_PADDING);
                }
                result = new Footprint(width, height, 0.0, 0.0);
            }
            else {
                final List<ClusterPacking.ClusterPlacement> placed = placements(key);
                double minX = Double.POSITIVE_INFINITY;
                double maxX = Double.NEGATIVE_INFINITY;
                double minY = Double.POSITIVE_INFINITY;
                double maxY = Double.NEGATIVE_INFINITY;
                for (final ClusterPacking.ClusterPlacement p : placed) {
                    minX = Math.min(minX, p.x - p.item.width * 0.5);
                    maxX = Math.max(maxX, p.x + p.item.width * 0.5);
                    minY = Math.min(minY, p.y - p.item.height * 0.5);
                    maxY = Math.max(maxY, p.y + p.item.height * 0.5);
                }
                // Not re-centred: the assembly keeps the anchor frame and carries its offset.
                result = new Footprint(maxX - minX + 2.0 * FRAME_CLEARANCE,
                    maxY - minY + 2.0 * FRAME_CLEARANCE,
                    -(minX + maxX) * 0.5, -(minY + maxY) * 0.5);
            }
            footprints.put(key, result);
            return result;
        }

        List<ClusterPacking.ClusterPlacement> placements(final EnclosureHullKey key) {
            final List<ClusterPacking.ClusterPlacement> cached = packedPlacements.get(key);
            if (cached != null) {
                return cached;
            }
            final ProjectedEnclosure enclosure = enclosuresByHull.get(key);
            final List<ClusterPacking.ClusterItem> items = new ArrayList<ClusterPacking.ClusterItem>();
            if (!enclosure.directNodes().isEmpty()) {
                final double ring = contentRingRadius(key);
                double maxDisc = 0.0;
                for (final ProjectedNodeKey nodeKey : enclosure.directNodes()) {
                    maxDisc = Math.max(maxDisc, discRadius(nodeKey));
                }
                final double half = ring + maxDisc + NODE_SETTLE_MARGIN;
                items.add(new ClusterPacking.ClusterItem(null, 2.0 * half, 2.0 * half, 0.0, 0.0, 0));
            }
            int order = 0;
            for (final EnclosureHullKey child : enclosure.directEnclosures()) {
                final Footprint childFootprint = footprint(child);
                items.add(new ClusterPacking.ClusterItem(child, childFootprint.width, childFootprint.height,
                    childFootprint.anchorX, childFootprint.anchorY, order++));
            }
            final List<ClusterPacking.ClusterPlacement> result = items.isEmpty()
                ? new ArrayList<ClusterPacking.ClusterPlacement>()
                : ClusterPacking.packItems(items, SIBLING_GAP);
            packedPlacements.put(key, result);
            return result;
        }

        ClusterPacking.ClusterPlacement placementOf(final EnclosureHullKey parentKey,
                final EnclosureHullKey childKey) {
            for (final ClusterPacking.ClusterPlacement placement : placements(parentKey)) {
                if (placement.item.anchorKey != null && placement.item.anchorKey.equals(childKey)) {
                    return placement;
                }
            }
            return null;
        }

        List<ClusterPacking.ClusterPlacement> topLevelPlacements() {
            if (topLevelPlacements != null) {
                return topLevelPlacements;
            }
            final List<ClusterPacking.ClusterItem> items = new ArrayList<ClusterPacking.ClusterItem>();
            int order = 0;
            for (final ProjectedEnclosure enclosure : enclosuresByHull.values()) {
                if (enclosure.mapRoot() || !enclosure.parentHull().isPresent()) {
                    final Footprint fp = footprint(enclosure.hullKey());
                    items.add(new ClusterPacking.ClusterItem(enclosure.hullKey(), fp.width, fp.height,
                        fp.anchorX, fp.anchorY, order++));
                }
            }
            topLevelPlacements = ClusterPacking.packItems(items, SIBLING_GAP);
            return topLevelPlacements;
        }

        ClusterPacking.ClusterPlacement topLevelPlacementOf(final EnclosureHullKey key) {
            for (final ClusterPacking.ClusterPlacement placement : topLevelPlacements()) {
                if (placement.item.anchorKey != null && placement.item.anchorKey.equals(key)) {
                    return placement;
                }
            }
            return null;
        }
```

Add the nested immutable payload type next to the existing private `Size` holder:

```java
        static final class Footprint {
            final double width;
            final double height;
            final double anchorX;
            final double anchorY;

            Footprint(final double width, final double height, final double anchorX, final double anchorY) {
                this.width = width;
                this.height = height;
                this.anchorX = anchorX;
                this.anchorY = anchorY;
            }
        }
```

Properties pinned by the tests: the content item is always placed first at `(0, 0)`; the assembly is never re-centred; `anchorX/anchorY` is the anchor position relative to the box centre, so `boxCentre = anchor - (anchorX, anchorY)`; `FRAME_CLEARANCE` is added per side at every nesting level; a leaf enclosure with direct nodes has `anchorX == anchorY == 0.0`.

- [ ] **Step 2: Write BoundaryFootprintShould**

Create `BoundaryFootprintShould.java` in package `org.freeplane.plugin.graph.layout.graphstream`, using `GraphStreamLayoutEngine.BoundarySizes` directly (`new GraphStreamLayoutEngine.BoundarySizes(projection)`), JUnit 4 and AssertJ. Build each synthetic projection locally with the same `ProjectedNode.of` / `ProjectedEnclosure.of` / `GraphProjection.projected` constructors already used by `ReferenceRepulsionFixture` (`Collections.<ProjectedEdge>emptyList()`, `Collections.<RelationshipResolution>emptyList()`, `Collections.<PinProjection>emptyList()`). Test names and exact assertions:

1. `leafFootprintIsSquareAroundTheAnchor` - an enclosure with exactly two direct nodes (both default prominence, `discRadius == 8.0`): `width == height == 2.0 * (contentRingRadius + 8.0 + NODE_SETTLE_MARGIN + FRAME_CLEARANCE)`; with `contentRingRadius = (2 * 8.0 + NodeSeparationProjection.MIN_GAP) / (2 * sin(pi/2)) = 11.0`, assert the concrete value `86.0`; `anchorX == 0.0`, `anchorY == 0.0`.
2. `singleDirectNodeFootprint` - one direct node: `contentRingRadius == 0.0`, so `width == height == 2.0 * (8.0 + 8.0 + 16.0) == 64.0`; `anchorX == anchorY == 0.0`.
3. `nestedFootprintIsThePackedChildBoxPlusFrameClearance` - parent with two empty children, labels `"aaaaaaaa"` (8 chars, box `144 x 40`) and `"bbbb"` (4 chars, box `80 x 40`). The packer places the larger child at `(0, 0)` and the smaller at `(-120.0, 0.0)`; the parent bounding box is `x in [-160, 72]`, `y in [-20, 20]`. Assert `width == 264.0`, `height == 72.0`, `anchorX == 44.0`, `anchorY == 0.0`, and that these equal the memoized `placements(parent)` bbox formula `maxX - minX + 2 * FRAME_CLEARANCE`, `-(minX + maxX) / 2`.
4. `emptyEnclosureUsesTheLabelSizedBox` - empty enclosure labelled `"wide"`: `width == 80.0` (`max(16, 4 * 16 + 16)`), `height == 40.0` (`24 + 16`), `anchorX == anchorY == 0.0`; `placements(hull)` is empty.
5. `singleChildIsPlacedAtTheAnchorOrigin` - parent with one empty child labelled `"child"` (box `96 x 40`): child placement `(0.0, 0.0)`; parent footprint `128.0 x 72.0`; `anchorX == anchorY == 0.0`.
6. `topLevelMembershipMatchesSeedsPredicate` - a projection with a `mapRoot() == true` root, a nested child, and a parentless `mapRoot() == false` enclosure. `topLevelPlacements()` contains exactly the root and the parentless enclosure; `topLevelPlacementOf(nestedChild) == null`; `topLevelPlacementOf(root) != null`.
7. `contentItemIsPlacedAtTheAnchor` - **added in Task 5** (needs the A-H fixture builders defined in `ClusterProximityShould`); do not write it here.
8. `footprintsAreMemoisedAndRecursionTerminates` - build a three-level projection (root with two children, each child with two children) and assert `sizes.footprint(hull)` returns the same instance (`isSameAs`) on repeated calls for every enclosure, and likewise `sizes.placements(hull)` for the root; the recursion terminates within the test's normal run.
9. `packedFootprintBoxesDoNotOverlapAcrossTheFixtureSet` - **added in Task 5** (needs the A-H fixtures); do not write it here.

- [ ] **Step 3: Run the focused class and the neighbouring suites**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.layout.graphstream.BoundaryFootprintShould" -PTestLoggingFull
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.layout.graphstream.ClusterPackingShould" --tests "org.freeplane.plugin.graph.layout.BoundarySeparationShould"
```

Expected: both commands `BUILD SUCCESSFUL`; the new class runs 8 tests with 0 failures and the existing suites are unchanged (this step is purely additive).

- [ ] **Step 4: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java \
        freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/BoundaryFootprintShould.java
git commit -m "feat(graph): compute anchor-centred packed footprints [2026-10-13-graph-cluster-initial-proximity]"
```


## Task 4: Seed from packed placements, packed hierarchy rests, offset-aware separation, legacy removal

**Implementer tier:** Advanced
**Lane:** engine
**Depends on:** Tasks 2 and 3

**Ownership:** `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java`, `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/TypedSpringBox.java`, `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/TypedNodeParticle.java`, plus the four change-induced test updates in `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java` and `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationShould.java`

**Validation:** `gradle :freeplane_plugin_graph:test`

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java` (constants, `synchronize()`, `topology()`, `BoundarySizes`, `Seeds`)
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/TypedSpringBox.java` (`addBoundaryRepulsion`, `configureAnchorExtents`)
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/TypedNodeParticle.java` (extents fields/accessors, delete `boundaryRadius()`)
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java` (`hierarchyAnchorsSeedOnTheGroupRing`, `hierarchyLinksPullNestedAnchorsTowardTheHierarchyRestLength`, `twoMapWorkspaceSettlesToIdle`)
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationShould.java` (`siblingAnchorsRemainSeparatedAfterSettling`)

**Interfaces:**

- Consumes: `BoundarySizes.footprint/placements/placementOf/topLevelPlacements/topLevelPlacementOf` and `Footprint` from Task 3; `ClusterPacking.ClusterPlacement` from Task 2.
- Produces: `TypedSpringBox.configureAnchorExtents(String id, double halfWidth, double halfHeight, double offsetX, double offsetY)`; `TypedNodeParticle.configureExtents(double, double, double, double)` and accessors `separationHalfWidth()`, `separationHalfHeight()`, `separationOffsetX()`, `separationOffsetY()`; engine frames whose anchors and direct nodes are the memoized packed composition. `TypedSpringBox.addBoundaryRepulsion(TypedNodeParticle, Vector3)` keeps its two-argument signature.

### 4A. Production changes

- [ ] **Step 1: Record the expected-red baseline**

Before editing, run the module suite with the current (Task 1-3) state and confirm `BUILD SUCCESSFUL` except the four environmental failures `ActivatorShould` (x2) and `AccessibleGraphCanvasShould` (x2). Record the exact failure list in your report.

- [ ] **Step 2: Rewrite `Seeds.center` and delete `topRingPosition`**

Replace the `center(EnclosureHullKey)` body with the memoized packed composition (spec section 3.4) and delete `topRingPosition` entirely:

```java
        private Position center(final EnclosureHullKey key) {
            final Position cached = centers.get(key);
            if (cached != null) {
                return cached;
            }
            final Position center;
            if (isTopLevel(key)) {
                final ClusterPacking.ClusterPlacement placement = sizes.topLevelPlacementOf(key);
                center = placement == null ? new Position(0.0, 0.0)
                    : new Position(placement.x + placement.item.anchorX,
                                   placement.y + placement.item.anchorY);
            }
            else {
                final ProjectedEnclosure enclosure = sizes.enclosure(key);
                final EnclosureHullKey parentKey = enclosure.parentHull().get();
                final Position parentCenter = center(parentKey);
                final ClusterPacking.ClusterPlacement placement = sizes.placementOf(parentKey, key);
                center = placement == null ? parentCenter
                    : new Position(parentCenter.x + placement.x + placement.item.anchorX,
                                   parentCenter.y + placement.y + placement.item.anchorY);
            }
            centers.put(key, center);
            return center;
        }
```

`nodePosition` is unchanged (the direct-node ring stays centred on the parent anchor). `isTopLevel` is unchanged: `enclosure == null || enclosure.mapRoot() || !enclosure.parentHull().isPresent()`.

- [ ] **Step 3: Topology: anchor radius 0.0 and packed hierarchy rests**

In `topology()`:

1. Change the anchor particle creation to `DesiredParticle.anchor(enclosure.hullKey(), 0.0)`. After this change the anchor's scalar radius is dead: native repulsion is position-based, anchor separation uses per-axis extents, and `TypedNodeParticle.scaleRepulsion` exempts anchors.
2. Delete `final Map<EnclosureHullKey, Integer> depths = enclosureDepths(projection);` and the methods `enclosureDepths` and `depthOf`, and delete `hierarchyRestLength`.
3. Replace the per-enclosure hierarchy construction (both the `parentHull().isPresent()` link and the `directEnclosures()` loop) with exactly:

```java
            if (enclosure.parentHull().isPresent()) {
                final EnclosureHullKey parentKey = enclosure.parentHull().get();
                final ClusterPacking.ClusterPlacement placement =
                    sizes.placementOf(parentKey, enclosure.hullKey());
                final double rest = placement == null ? 0.0
                    : Math.hypot(placement.x + placement.item.anchorX,
                                 placement.y + placement.item.anchorY);
                addHierarchyLink(result, hierarchyPairs, anchorIds.get(parentKey), anchor, rest);
            }
```

Every direct child is visited by this same loop, so the link set is identical to the old two-sided construction plus `hierarchyPairs` deduplication. Keep the existing containment `for (final ProjectedNodeKey child : enclosure.directNodes())` loop and the rest of the loop body unchanged; only the hierarchy construction changes. Keep relationship rest lengths unchanged (`radiusOf(first) + radiusOf(second) + NodeSeparationProjection.MIN_GAP`, anchors resolving to `contentRingRadius`).

- [ ] **Step 4: Configure anchor extents in `synchronize()`**

In `GraphStreamLayoutEngine.synchronize()`, immediately after `springBox.configureParticle(...)` and before the pin override, add:

```java
            if (desired.anchorKey != null) {
                final BoundarySizes.Footprint footprint = sizes.footprint(desired.anchorKey);
                springBox.configureAnchorExtents(desired.id, footprint.width * 0.5, footprint.height * 0.5,
                    -footprint.anchorX, -footprint.anchorY);
            }
```

The call is outside the `state == null` branch: retained anchors are reconfigured on every synchronize because a reordered projection changes footprints.

- [ ] **Step 5: Offset-aware separation box force**

In `TypedNodeParticle`, replace `private double boundaryRadius()` (and its `separationRadius` usage as the anchor extent) with:

```java
    private double separationHalfWidth = -1.0;
    private double separationHalfHeight = -1.0;
    private double separationOffsetX = 0.0;
    private double separationOffsetY = 0.0;

    void configureExtents(final double halfWidth, final double halfHeight, final double offsetX,
            final double offsetY) {
        separationHalfWidth = halfWidth;
        separationHalfHeight = halfHeight;
        separationOffsetX = offsetX;
        separationOffsetY = offsetY;
    }

    double separationHalfWidth() {
        return separationHalfWidth;
    }

    double separationHalfHeight() {
        return separationHalfHeight;
    }

    double separationOffsetX() {
        return separationOffsetX;
    }

    double separationOffsetY() {
        return separationOffsetY;
    }
```

Delete `double boundaryRadius()`. `separationRadius()`, `configure(radius, pinned)`, `scaleRepulsion`, `move`, `attraction`, `repulsionN2`, `repulsionNLogN`, `baseSeparationRadius()` and `BOUNDARY_REPULSION_FACTOR` are unchanged.

In `TypedSpringBox`, add the delegating configuration method and replace the circular branch of `addBoundaryRepulsion` with the box branch. Keep the guard structure (anchors only, skip self, skip ancestor pairs via `isAncestorPair`), keep the two-argument signature, and keep `BOUNDARY_REPULSION_FACTOR = 0.5`:

```java
    void configureAnchorExtents(final String id, final double halfWidth, final double halfHeight,
            final double offsetX, final double offsetY) {
        final TypedNodeParticle particle = typedParticles.get(id);
        if (particle != null) {
            particle.configureExtents(halfWidth, halfHeight, offsetX, offsetY);
        }
    }

    void addBoundaryRepulsion(final TypedNodeParticle particle, final Vector3 displacement) {
        final Boolean isAnchor = anchorFlags.get(particle.getId().toString());
        if (isAnchor == null || !isAnchor.booleanValue()) {
            return;
        }
        for (final Map.Entry<String, TypedNodeParticle> entry : typedParticles.entrySet()) {
            final Boolean otherAnchor = anchorFlags.get(entry.getKey());
            if (otherAnchor == null || !otherAnchor.booleanValue()) {
                continue;
            }
            final TypedNodeParticle other = entry.getValue();
            if (other == particle) {
                continue;
            }
            if (isAncestorPair(particle.getId().toString(), other.getId().toString())) {
                continue;
            }
            final Point3 own = particle.getPosition();
            final Point3 position = other.getPosition();
            final double ownCentreX = own.x + particle.separationOffsetX();
            final double ownCentreY = own.y + particle.separationOffsetY();
            final double otherCentreX = position.x + other.separationOffsetX();
            final double otherCentreY = position.y + other.separationOffsetY();
            final double forceGapX = Math.abs(ownCentreX - otherCentreX)
                - (particle.separationHalfWidth() + other.separationHalfWidth()
                    + GraphStreamLayoutEngine.BoundarySizes.SIBLING_GAP);
            final double forceGapY = Math.abs(ownCentreY - otherCentreY)
                - (particle.separationHalfHeight() + other.separationHalfHeight()
                    + GraphStreamLayoutEngine.BoundarySizes.SIBLING_GAP);
            if (forceGapX >= 0.0 || forceGapY >= 0.0) {
                continue;
            }
            if (-forceGapX <= -forceGapY) {
                // Equal overlaps resolve along x; the push is away from the other
                // footprint-box centre (anchor - offset), not the other anchor.
                final double push = (ownCentreX >= otherCentreX ? 1.0 : -1.0)
                    * (-forceGapX) * BOUNDARY_REPULSION_FACTOR;
                displacement.set(0, displacement.at(0) + push);
            }
            else {
                final double push = (ownCentreY >= otherCentreY ? 1.0 : -1.0)
                    * (-forceGapY) * BOUNDARY_REPULSION_FACTOR;
                displacement.set(1, displacement.at(1) + push);
            }
        }
    }
```

- [ ] **Step 6: Delete the legacy estimator and the depth helpers**

In `GraphStreamLayoutEngine`, delete exactly: `GROUP_SPACING`, `SUB_GROUP_SPACING`, `BoundarySizes.MAX_RENDERED_NODE_RADIUS`, `BoundarySizes.boundaryRadius`, `BoundarySizes.directNodeRingRadius`, `BoundarySizes.directNodeReach`, `BoundarySizes.childBoundaryReach`, `BoundarySizes.ringRadius`, `BoundarySizes.reachOf`, `BoundarySizes.sizeOf`, the private `Size` holder, the private `sizes` map (`Map<EnclosureHullKey, Size>`, renamed conceptually to `footprints` in Task 3; only the old map is deleted here), the now-unused `nodesByKey` map and its constructor population, the `Seeds.topRingPosition` method, and the sibling-ring branch of `Seeds.center` (already replaced in Step 2). Retain `contentRingRadius`, `discRadius`, `SIBLING_GAP`, `FRAME_CLEARANCE`, `CHAR_WIDTH_UPPER_BOUND`, `CHAR_HEIGHT_UPPER_BOUND`, `BOUNDARY_PADDING` (empty-enclosure label branch only), `ForceKind.HIERARCHY` and `LayoutCalibration.hierarchy()`.

- [ ] **Step 7: Compile and observe exactly the expected red set**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test -PTestLoggingFull
```

Expected: compile success, and exactly these four change-induced failures (review-6's faithful full-suite run), plus the four environmental failures recorded in Step 1:

- `BoundarySeparationShould.siblingAnchorsRemainSeparatedAfterSettling`
- `TypedForcesShould.twoMapWorkspaceSettlesToIdle` (`expected: 45 but was: 756`)
- `TypedForcesShould.hierarchyAnchorsSeedOnTheGroupRing`
- `TypedForcesShould.hierarchyLinksPullNestedAnchorsTowardTheHierarchyRestLength`

If the failure set differs, stop and report `BLOCKED` with the exact diff; do not improvise test edits.

### 4B. The four change-induced test updates

- [ ] **Step 8: Rewrite `TypedForcesShould.hierarchyAnchorsSeedOnTheGroupRing`**

Keep the existing three-empty-group + root fixture (labels `"group-0"`, `"group-1"`, `"group-2"` are 7 characters, so each empty-enclosure footprint is `128 x 40`). Replace the `ringRadius`/`maximumWidth` assertions with the packed composition:

```java
        try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
            LayoutFrame frame = engine.apply(request(WORKSPACE_ONE, projection, projection,
                Collections.<PinProjection>emptyList()));

            assertThat(frame.positions().anchors().get(rootHull())).isEqualTo(LayoutPoint.of(0.0, 0.0));
            assertThat(frame.positions().anchors().get(groupHulls.get(0))).isEqualTo(LayoutPoint.of(0.0, 0.0));
            assertThat(frame.positions().anchors().get(groupHulls.get(1))).isEqualTo(LayoutPoint.of(-136.0, 0.0));
            assertThat(frame.positions().anchors().get(groupHulls.get(2))).isEqualTo(LayoutPoint.of(136.0, 0.0));
            for (int first = 0; first < groupHulls.size(); first++) {
                for (int second = first + 1; second < groupHulls.size(); second++) {
                    double boxGapX = Math.abs(frame.positions().anchors().get(groupHulls.get(first)).x()
                        - frame.positions().anchors().get(groupHulls.get(second)).x()) - 128.0;
                    double boxGapY = Math.abs(frame.positions().anchors().get(groupHulls.get(first)).y()
                        - frame.positions().anchors().get(groupHulls.get(second)).y()) - 40.0;
                    assertThat(Math.max(boxGapX, boxGapY)).isGreaterThanOrEqualTo(SIBLING_GAP - 1.0e-9);
                }
            }
        }
```

The positions are the exact packer output for three equal-area `128 x 40` items (first at the origin, second at the origin-nearest minimum-area slot `(-136, 0)`, third at the distance-nearest equal-area slot `(136, 0)`), so assert them with `isEqualTo`, not a tolerance. Delete the now-unused helpers `ringRadius`, `maximumWidth`, `widthOf`, `heightOf` and the constants `CHAR_WIDTH_UPPER_BOUND`, `CHAR_HEIGHT_UPPER_BOUND`, `BOUNDARY_PADDING`, `FRAME_CLEARANCE` from this class; keep `SIBLING_GAP`.

- [ ] **Step 9: Rewrite `TypedForcesShould.hierarchyLinksPullNestedAnchorsTowardTheHierarchyRestLength`**

Keep the existing `hierarchyProjection(generation, includeHierarchy)` helper and the parent pin at `(0.0, 0.0)`. Add the engine-frame identity assertion and replace the old `nestedDistance > 100.0` expectation with the measured single-child contract:

```java
        // Engine frame: a single child is placed at the parent anchor origin, so the packed
        // parent-child anchor distance (the HIERARCHY rest) is 0 and the child seeds at (0, 0).
        try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
            LayoutFrame applied = engine.apply(request(WORKSPACE_ONE, nested, nested, pins));
            assertThat(applied.positions().anchors().get(hierarchyParentHull()))
                .isEqualTo(LayoutPoint.of(0.0, 0.0));
            assertThat(applied.positions().anchors().get(hierarchyChildHull()))
                .isEqualTo(LayoutPoint.of(0.0, 0.0));
        }
        double nestedDistance = anchorDistanceAfterSteps(WORKSPACE_ONE, nested, pins, 300);
        double peerDistance = anchorDistanceAfterSteps(WORKSPACE_ONE, peers, pins, 300);

        // Native repulsion alone settles a single child; measured on the final configuration:
        // B 9.3, D 11.1, F 10.1-12.1, synthetic single-child 5.5-19.8.
        assertThat(nestedDistance).isGreaterThan(0.0);
        assertThat(nestedDistance).isLessThan(20.0);
        assertThat(nestedDistance).isLessThan(peerDistance);
```

The HIERARCHY rest itself is not visible from this package; the engine-frame equality plus the `< 20.0` bound is the observable form of "rest 0 for a single centred child".

- [ ] **Step 10: Re-baseline `TypedForcesShould.twoMapWorkspaceSettlesToIdle`**

Change `private static final int TWO_MAP_FIRST_IDLE_STEP = 45;` to `756` (review-6's faithful full-suite measurement with the final configuration). Keep the `firstIdle` non-null assertion and the following 100-frame `rms <= 0.05` / `max <= 0.10` shape. Run the test; if the observed first idle differs from 756, set the constant to the observed value and record both in your report.

- [ ] **Step 11: Rewrite `BoundarySeparationShould.siblingAnchorsRemainSeparatedAfterSettling`**

Replace the `assertNoSiblingOverlap` label-box call with the painted-hull contract, and keep the `<= 1000.0` anchor-span bound:

```java
        GraphGeometry geometry = new GraphGeometryEngine().computeHulls(projection, frame.positions(),
            new AwtGeometryTextMetrics(new Font(Font.SANS_SERIF, Font.PLAIN, 12),
                new FontRenderContext(null, true, true)));
        HullGeometry axioms = geometry.hulls().get(ReferenceRepulsionFixture.axiomsHull());
        HullGeometry definitions = geometry.hulls().get(ReferenceRepulsionFixture.definitionsHull());
        assertThat(HullIntersection.siblingOverlap(axioms, definitions)).isFalse();
        assertThat(geometry.hulls().get(ReferenceRepulsionFixture.zfcHull())).isNotNull();
```

Add the imports `org.freeplane.plugin.graph.geometry.AwtGeometryTextMetrics`, `GraphGeometry`, `GraphGeometryEngine`, `HullGeometry`, `HullIntersection`, `java.awt.Font`, `java.awt.font.FontRenderContext`. The measured settled painted gap for this pair is `36.0` (spec section 8.3.2) and the measured all-pairs crossings are `0`; do not assert the gap value itself.

- [ ] **Step 12: Run the module suite to green**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test -PTestLoggingFull
```

Expected: `BUILD SUCCESSFUL` except exactly the four environmental failures from Step 1; the four change-induced failures are gone.

- [ ] **Step 13: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java \
        freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/TypedSpringBox.java \
        freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/TypedNodeParticle.java \
        freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java \
        freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationShould.java
git commit -m "feat(graph): seed and separate anchors from packed footprints [2026-10-13-graph-cluster-initial-proximity]"
```


## Task 5: Fixtures A-H and the exact initial-frame acceptance tests

**Implementer tier:** Standard
**Lane:** acceptance
**Depends on:** Task 4

**Ownership:** `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/ClusterProximityShould.java` (new), `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/BoundaryFootprintShould.java` (add tests 7 and 9)

**Validation:** `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.graphstream.ClusterProximityShould --tests org.freeplane.plugin.graph.layout.graphstream.BoundaryFootprintShould`

**Files:**

- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/ClusterProximityShould.java`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/BoundaryFootprintShould.java` (append tests 7 and 9 from Task 3 Steps 2)

**Interfaces:**

- Consumes: `BoundarySizes.footprint/placements/placementOf/topLevelPlacements/topLevelPlacementOf`, `ClusterPacking` payload types, public `GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())`, `LayoutEngine.apply(LayoutRequest)`, `LayoutWorker.submit(LayoutRequest)`, `LayoutRequest.of`, `ProjectionDiff.between`, `GraphGeometryEngine.computeHulls`, `AwtGeometryTextMetrics`.
- Produces: `static WorkspaceId WORKSPACE = WorkspaceId.of("00000000-0000-0000-0000-0000000000aa")`; `static GraphProjection fixture(char name)` for `'A'..'H'`; `static List<PinProjection> fixturePins(char name)`; `static GraphProjection wideProjection(long generation)`; `static Map<EnclosureHullKey, LayoutPoint> composeAnchors(GraphProjection)`; package-visible `static LayoutFrame engineFrame(GraphProjection, List<PinProjection>)` and `static LayoutFrame workerFrame(GraphProjection, List<PinProjection>)`. Tasks 6 reuses these helpers; `BoundaryFootprintShould` tests 7 and 9 call `ClusterProximityShould.fixture(...)`, `fixturePins(...)` and `wideProjection(...)`.

- [ ] **Step 1: Write the fixture builders**

Create `ClusterProximityShould.java` in the package `org.freeplane.plugin.graph.layout.graphstream` with `import static org.assertj.core.api.Assertions.assertThat;`. Shared ids: workspace `00000000-0000-0000-0000-0000000000aa`; maps `...0001` (MAP_ONE) through `...0004` (MAP_FOUR). The fixture tables of spec section 7.2 are authoritative; build each with the existing `ProjectedNode.of`, `ProjectedEnclosure.of`, `GraphProjection.projected` constructors and the `hull(map, id)` helper below. Restated requirements, one line per fixture:

- **A** (MAP_ONE, `mapName = "M"`, `graphGroup = true`): `root` "Axiomatic Set Theory" mapRoot SUPPRESSED children `[zfc]`; `zfc` "ZFC" EMPHATIC parent `root` children `[axioms, defs]`; `axioms` "Axioms" SUBTLE parent `zfc` direct nodes `[n1, n2, n3]` labelled `"Fundation / Regularity"` (**sic**), `"Replacement Scheme"`, `"Axiom of Choice"`; `defs` "Basic Definitions and Theorems" SUBTLE parent `zfc` direct node `[n4]` labelled `"Theorem"`.
- **B** (`mapName = "Map"`, `graphGroup = false`): MAP_ONE `a-root` "Map a" EMPHATIC mapRoot children `[a-sub]`, `a-sub` "a fundamentals" SUBTLE parent `a-root` nodes `a-n0..a-n5` (6) labelled `"Theorem: a statement i with a long label"`; MAP_TWO `b-root` "Map b" EMPHATIC mapRoot children `[b-sub]`, `b-sub` "b fundamentals" SUBTLE parent `b-root` nodes `b-n0, b-n1` (2) labelled `"Theorem: b statement i with a long label"`.
- **C = D + pin**; **D**: MAP_ONE `a-root` "Map a" EMPHATIC mapRoot children `[a-sub]`, `a-sub` "a fundamentals" SUBTLE parent `a-root` nodes `a-n0..a-n3` (4) labelled `"Theorem: a statement i with a long label"`; MAP_TWO `b-root` "Map b" EMPHATIC mapRoot children `[b-sub]`, `b-sub` "b fundamentals" SUBTLE parent `b-root` nodes `b-n0..b-n3` (4) labelled `"Theorem: b statement i with a long label"`. Three relationships `a-ni <-> b-ni` for `i = 0,1,2`, direction `FORWARD`, id `String.format("20000000-0000-0000-0000-%012d", new Object[] {Integer.valueOf(i + 1)})`, sequence `i + 1`, no unknown XML; each edge is `ProjectedEdge.of(ProjectedEdgeKey.of(ProjectedEndpointKey.ofNode(a-ni), ProjectedEndpointKey.ofNode(b-ni)), Collections.singletonList(EdgeContributor.graphRelationship(record, sourceEndpoint, targetEndpoint)))`. C additionally pins `b-n0` at `(60.0, 20.0)` via `PinProjection.active(PinRecord.of(b-n0.source().persistedReference().get(), 60.0, 20.0, Collections.<UnknownXml>emptyList()), b-n0)`.
- **E** (MAP_ONE): `root` "Root map" mapRoot SUPPRESSED direct nodes `[r0, r1]` children `[child]`; `child` "child group" SUBTLE parent `root` nodes `[c0, c1, c2]`. Labels `"Theorem: root statement zero with a long label"`, `"Theorem: root statement one with a long label"`, `"Theorem: child statement i with a long label"`.
- **F** (MAP_ONE..MAP_FOUR): for `m = 0..3` with prefix `"m" + m`: `mM-root` "Map mM" EMPHATIC mapRoot children `[mM-sub]`; `mM-sub` "mM fundamentals" SUBTLE parent `mM-root` nodes `mM-n0..mM-n{count-1}` with `count = 2 + m` (2, 3, 4, 5), labels `"Theorem: mM statement i with a long label"`.
- **G** (MAP_ONE): `g-root` "Map g" EMPHATIC mapRoot children `[g0, g1, g2, g3]`; each `gK` "group K" SUBTLE parent `g-root` nodes `gK-n0, gK-n1` labelled `"Theorem: gK statement i with a long label"`.
- **H** (MAP_ONE): `h-root` "Root map" mapRoot SUPPRESSED direct nodes `[r0, r1]` children `[h-c1, h-c2]`; `h-c1` "child one group" EMPHATIC parent `h-root` nodes `[c1-0, c1-1]` children `[h-g1]`; `h-g1` "grandchild group" SUBTLE parent `h-c1` nodes `[g1-0, g1-1]`; `h-c2` "child two group" SUBTLE parent `h-root` nodes `[c2-0, c2-1]`. Labels are `"Theorem: <prefix> statement i with a long label"` with prefixes `root`, `child one`, `grandchild`, `child two`.
- `wideProjection(long generation)` (supplementary, package-visible for Task 6): MAP_ONE, `wide-root` "Root" EMPHATIC mapRoot no direct nodes, 200 empty children `wide-0..wide-199` (label `"wide-i"`, SUBTLE, parent `wide-root`), no edges.

Exact key helper:

```java
    static EnclosureHullKey hull(MapReferenceId map, String id) {
        return EnclosureHullKey.of(Collections.singletonList(EnclosureKey.of(
            SourceNodeKey.persisted(NodeReference.of(map, PersistedNodeId.of(id))))));
    }
```

- [ ] **Step 2: Write the composition helper mirroring the engine arithmetic**

```java
    static Map<EnclosureHullKey, LayoutPoint> composeAnchors(final GraphProjection projection) {
        final GraphStreamLayoutEngine.BoundarySizes sizes =
            new GraphStreamLayoutEngine.BoundarySizes(projection);
        final Map<EnclosureHullKey, LayoutPoint> result =
            new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            result.put(enclosure.hullKey(), composedAnchor(sizes, enclosure.hullKey()));
        }
        return result;
    }

    private static LayoutPoint composedAnchor(final GraphStreamLayoutEngine.BoundarySizes sizes,
            final EnclosureHullKey key) {
        final ProjectedEnclosure enclosure = sizes.enclosure(key);
        if (enclosure.mapRoot() || !enclosure.parentHull().isPresent()) {
            final ClusterPacking.ClusterPlacement placement = sizes.topLevelPlacementOf(key);
            return placement == null ? LayoutPoint.of(0.0, 0.0)
                : LayoutPoint.of(placement.x + placement.item.anchorX,
                                 placement.y + placement.item.anchorY);
        }
        final EnclosureHullKey parentKey = enclosure.parentHull().get();
        final LayoutPoint parentCenter = composedAnchor(sizes, parentKey);
        final ClusterPacking.ClusterPlacement placement = sizes.placementOf(parentKey, key);
        return placement == null ? parentCenter
            : LayoutPoint.of(parentCenter.x() + placement.x + placement.item.anchorX,
                             parentCenter.y() + placement.y + placement.item.anchorY);
    }
```

The addition order (`parentCenter.x() + placement.x + placement.item.anchorX`) must match `Seeds.center` exactly so `isEqualTo` holds at 0.0 delta. Define the two shared frame helpers that Task 6 reuses:

```java
    static LayoutFrame engineFrame(final GraphProjection projection, final List<PinProjection> pins) {
        try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
            return engine.apply(LayoutRequest.of(WORKSPACE, projection,
                ProjectionDiff.between(projection, projection), pins));
        }
    }

    static LayoutFrame workerFrame(final GraphProjection projection, final List<PinProjection> pins)
            throws Exception {
        final LayoutWorker worker = new LayoutWorker(LayoutCalibration.spikeDefaults());
        try {
            return worker.submit(LayoutRequest.of(WORKSPACE, projection,
                ProjectionDiff.between(projection, projection), pins)).toCompletableFuture()
                .get(5L, TimeUnit.SECONDS);
        }
        finally {
            worker.close();
        }
    }
```

- [ ] **Step 3: Write test 1, `initialFrameEqualsTheDeterministicPacking`**

For every fixture A-H at the **engine frame** (`engineFrame(projection, fixturePins(name))` from Step 2; the engine frame is the seeded frame before `BoundarySeparationCorrection`):

- every anchor equals `composeAnchors(projection)` exactly, key by key, with `assertThat(actual).isEqualTo(expected)`;
- every direct node equals `parentAnchor.x + radius * Math.cos(angle)`, `parentAnchor.y + radius * Math.sin(angle)` with `index = parent.directNodes().indexOf(node)`, `count = parent.directNodes().size()`, `radius = sizes.contentRingRadius(parentKey)`, `angle = 2.0 * Math.PI * Math.max(0, index) / Math.max(1, count)`; skip `b-n0` of fixture C (pinned) and assert it equals `LayoutPoint.of(60.0, 20.0)`;
- content at the anchor: for every enclosure with direct nodes, `sizes.placements(key).get(0).item.anchorKey == null` and `x == 0.0`, `y == 0.0`;
- top-level membership: for every enclosure, `(sizes.topLevelPlacementOf(key) != null) == (mapRoot() || !parentHull().isPresent())`;
- packed non-overlap for every **non-ancestor** pair: with `boxCentre = anchor - (footprint.anchorX, footprint.anchorY)`, `boxGapX = |boxCentreX1 - boxCentreX2| - (footprint1.width + footprint2.width) * 0.5`, likewise y, assert `Math.max(boxGapX, boxGapY) >= -ClusterPacking.CANDIDATE_EPSILON`;
- every contact gutter: for every enclosure assembly and every `placement` in `sizes.placements(key)`, for every recorded contact, the separating-axis box gap is in `[SIBLING_GAP - CANDIDATE_EPSILON, SIBLING_GAP + CONTACT_GAP_TOLERANCE]`;
- determinism: a second fresh engine's `positions()` is `isEqualTo` the first.

Measured values this pins (spec sections 7.1.3 and 8.3.7): max anchor delta `0.0`, node delta `0.0`, packed-box violations `0`; fixture A's footprints are `axioms 89.4 x 89.4`, `defs 64.0 x 64.0`, `zfc 193.4 x 121.4 (36.0, 0.0)`, `root 225.4 x 153.4 (0.0, 0.0)`.

- [ ] **Step 4: Write test 2, `generatedProjectionsComposeIdenticallyAndDoNotOverlap`**

Build a fixed-seed generator with `new java.util.Random(20261013L)` and 400 rounds. Per round: `mapCount = 1 + random.nextInt(6)`; for each map build a `mapRoot` root; `nodeCount = random.nextInt(7)` direct nodes labelled `repeat('x', 1 + random.nextInt(24))` with `graphGroup = random.nextBoolean()`; `childCount = random.nextInt(6)` child enclosures to nesting depth at most 3, each child empty when `random.nextBoolean()` else carrying `random.nextInt(7)` direct nodes; every child is listed once in its parent's `directEnclosures()` and has that parent as its `parentHull()`. Per generated projection assert: engine-frame anchors equal a **fresh** `BoundarySizes` composition exactly, and every non-ancestor pair satisfies `Math.max(boxGapX, boxGapY) >= -ClusterPacking.CANDIDATE_EPSILON` with the footprint boxes above. Review-6 measured 0 packed-box violations over 20 075 non-ancestor pairs.

- [ ] **Step 5: Write tests 3 and 4, the worker-path checks**

Test 3 `initialFrameThroughWorkerKeepsSeedsWithoutPins`: for A, B, D, E, F, G, H (never C) get `engineFrame(projection, emptyPins)` and `workerFrame(projection, emptyPins)` for the same request, then `assertThat(worker.positions()).isEqualTo(engine.positions())` plus the non-overlap and contact-gutter checks of Step 3. Test 4 `initialFrameThroughWorkerWithPinOnlyChecksNonOverlap`: for C only, run `workerFrame(projection, fixturePins('C'))`, assert the non-overlap and contact-gutter checks plus `frame.positions().nodes().get(b-n0) == LayoutPoint.of(60.0, 20.0)`; do **not** assert packing equality through the worker (the post-geometry correction translates pinned fixture anchors by up to `380.45`). Close every worker in a `finally` block (the `workerFrame` helper above already does).

- [ ] **Step 6: Add the fixture-dependent tests to BoundaryFootprintShould**

Add to `BoundaryFootprintShould` (which imports `ClusterProximityShould` fixtures from the same package):

- `contentItemIsPlacedAtTheAnchor` - for every fixture A-H in which an enclosure has direct nodes, `sizes.placements(key).get(0).item.anchorKey == null` and its `x == 0.0`, `y == 0.0` (fixtures with direct nodes: A axioms/defs, B a-sub/b-sub, C a-sub/b-sub, D a-sub/b-sub, E root/child, F all four subs, G all four groups, H h-root/h-c1/h-g1/h-c2).
- `packedFootprintBoxesDoNotOverlapAcrossTheFixtureSet` - for A-H plus `wideProjection(1L)`, compose every enclosure's **absolute box centre** at packed positions (`boxCentre = packedAnchor - (footprint.anchorX, footprint.anchorY)`; top-level anchors from `topLevelPlacements()`, nested anchors from `parentCentre + placement + item anchor offset`), then for every **non-ancestor** pair assert `forceGapX >= -ClusterPacking.CANDIDATE_EPSILON || forceGapY >= -ClusterPacking.CANDIDATE_EPSILON` with `forceGapX = |centreX1 - centreX2| - (footprint1.width + footprint2.width) * 0.5 - SIBLING_GAP`, likewise y. Ancestor/descendant pairs are nested by construction and are excluded; review-6 measured 0 violations on this set.

- [ ] **Step 7: Run the focused classes to green**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.layout.graphstream.ClusterProximityShould" \
  --tests "org.freeplane.plugin.graph.layout.graphstream.BoundaryFootprintShould" -PTestLoggingFull
```

Expected: `BUILD SUCCESSFUL`; `ClusterProximityShould` runs 4 tests and `BoundaryFootprintShould` runs 10 tests, 0 failures. If a fixture assertion fails, report the exact fixture/hull/pair and the observed vs expected values; do not relax an exact `isEqualTo` to a tolerance.

- [ ] **Step 8: Commit**

```bash
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/ClusterProximityShould.java \
        freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/BoundaryFootprintShould.java
git commit -m "test(graph): pin the packed initial frame for fixtures A-H [2026-10-13-graph-cluster-initial-proximity]"
```


## Task 6: Settled acceptance, evidence block, and the 200-child wall clock

**Implementer tier:** Standard
**Lane:** acceptance
**Depends on:** Task 5

**Ownership:** `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/ClusterProximityShould.java` (add tests 5 and 6 and the evidence printer), `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/ClusterPackingShould.java` (add test 17)

**Validation:** `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.graphstream.ClusterProximityShould --tests org.freeplane.plugin.graph.layout.graphstream.ClusterPackingShould`

**Files:**

- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/ClusterProximityShould.java`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/ClusterPackingShould.java`

**Interfaces:**

- Consumes: Task 5's `fixture(char)`, `fixturePins(char)`, `wideProjection(long)`, `engineFrame`, `workerFrame`; `GraphGeometryEngine.computeHulls(GraphProjection, LayoutPositions, GeometryTextMetrics)`, `AwtGeometryTextMetrics(Font, FontRenderContext)`, `HullGeometry.exactPolygon()`, `HullIntersection`/separating-axis gap.
- Produces: the printed evidence lines of spec section 8.4 item 2 and the asserted idle/crossing/timing contract of section 8.2.

- [ ] **Step 1: Write test 5, `settledFixturesReachIdleWithinBudgetAndDoNotCross`**

For every fixture A-H, using `LayoutWorker(LayoutCalibration.spikeDefaults())` and the documented endpoint (first idle, else the 2000th step, then always 200 extra steps):

- step up to 2000 and assert an idle frame was found (`assertThat(firstIdle).isNotNull()` and `assertThat(idleStep).isLessThanOrEqualTo(2000)`); measured idle 22-785 (A 42, B 28, C 785, D 428, E 23, F 135, G 28, H 22);
- take 200 further steps to the endpoint frame;
- compute painted hulls with `GraphGeometryEngine.computeHulls(projection, endpoint.positions(), new AwtGeometryTextMetrics(new Font("Dialog", Font.PLAIN, 12), new FontRenderContext(null, true, true)))` (headless);
- count all-pairs **non-ancestor** crossings with the maximum separating-axis gap on `exactPolygon()`: for polygons `a` and `b`, collect the outward edge normals of both plus the centroid direction, `gap = max over axes of (min projection of b - max projection of a)`, and a pair crosses when `gap < 0.0`; assert `assertThat(crossings).isEqualTo(0)`; measured crossings 0 and minimum separation A 36.0, B 31.2, C 0.0, D 35.7, F 19.7, G 18.6, H 83.5 (E has no non-ancestor pair);
- then the 1000-step stability window, tracking the maximum hold deviation for the evidence printer (reported, **never asserted**); measured maxima A 4.6, B 14.6, C 12.1, D 78.7, E 2.1, F 27.2, G 9.9, H 2.1.

The named pairs tracked and printed per fixture (hull keys via `ClusterProximityShould.hull(map, id)`): A `axioms` <-> `defs`; B, C, D `a-root` <-> `b-root`; E `child` <-> `root` parent anchor; F `m3-root` <-> `m2-root`, `m3-root` <-> `m1-root`, `m1-root` <-> `m0-root`; G every adjacent `gK` <-> `g(K+1)` pair; H `h-c1` <-> `h-g1` and `h-c1` <-> `h-c2`.

Use the exact `hullDistance` helper convention below (copied from the stored probe's convention so the printed gaps match `probe-final.txt`):

```java
    private static double hullDistance(final HullGeometry first, final HullGeometry second) {
        final List<LayoutPoint> a = first.exactPolygon();
        final List<LayoutPoint> b = second.exactPolygon();
        final List<double[]> axes = new ArrayList<double[]>();
        addAxes(axes, a);
        addAxes(axes, b);
        double cx = 0.0;
        double cy = 0.0;
        for (final LayoutPoint p : a) {
            cx += p.x();
            cy += p.y();
        }
        cx /= a.size();
        cy /= a.size();
        double dx = 0.0;
        double dy = 0.0;
        for (final LayoutPoint p : b) {
            dx += p.x();
            dy += p.y();
        }
        dx = dx / b.size() - cx;
        dy = dy / b.size() - cy;
        final double length = Math.hypot(dx, dy);
        if (length > 0.0) {
            axes.add(new double[] {dx / length, dy / length});
        }
        double best = Double.NEGATIVE_INFINITY;
        for (final double[] axis : axes) {
            double minB = Double.POSITIVE_INFINITY;
            double maxA = Double.NEGATIVE_INFINITY;
            for (final LayoutPoint p : b) {
                minB = Math.min(minB, axis[0] * p.x() + axis[1] * p.y());
            }
            for (final LayoutPoint p : a) {
                maxA = Math.max(maxA, axis[0] * p.x() + axis[1] * p.y());
            }
            best = Math.max(best, minB - maxA);
        }
        return best;
    }

    private static void addAxes(final List<double[]> axes, final List<LayoutPoint> polygon) {
        for (int index = 0; index < polygon.size(); index++) {
            final LayoutPoint first = polygon.get(index);
            final LayoutPoint second = polygon.get((index + 1) % polygon.size());
            final double dx = second.x() - first.x();
            final double dy = second.y() - first.y();
            final double length = Math.hypot(dx, dy);
            if (length > 0.0) {
                axes.add(new double[] {-dy / length, dx / length});
            }
        }
    }
```

- [ ] **Step 2: Write test 6, `settledEvidenceIsRecorded`**

Print one stable evidence line per fixture and one per named pair / enclosure; the lines must contain the fields `idle`, `endpointStep`, `packed`, `settled`, `windowMaxHoldDeviation`, `paintedGap`, `minSeparation`, `containmentOverflow`, `marginalOverlap`. Use `System.out.printf` with one decimal place for lengths so the output diffs cleanly. Expected values (spec sections 8.3.2 and 8.3.3; report these, do not assert them):

| Fixture | idle | named pair(s) | packed | settled | window max | painted gap | min sep | containment overflow | marginal overlap |
|---|---|---|---|---|---|---|---|---|---|
| A | 42 | `Axioms`<->`Basic Definitions and Theorems` | 84.7 | 84.6 | 4.6 | 36.0 | 36.0 | 8.1 (`ZFC`) | -0.1 |
| B | 28 | `Map a`<->`Map b` | 137.0 | 140.9 | 14.6 | 31.1 | 31.2 | 5.7 (`Map b`) | 0 |
| C | 785 | `Map a`<->`Map b` | 135.1 | 147.2 | 12.1 | 0.0 | 0.0 | 14.0 (`Map a`) | 0 |
| D | 428 | `Map a`<->`Map b` | 135.1 | 147.2 | 78.7 | 35.7 | 35.7 | 5.9 (`Map b`) | 0 |
| E | 23 | `child group`<->parent anchor | 79.7 | 81.4 | 2.1 | nested | - | 0.0 | 0 |
| F | 135 | `m3`<->`m2`, `m3`<->`m1`, `m1`<->`m0` | 138.3/135.4/127.7 | 151.2/151.8/127.8 | 27.2 | 37.5/19.7/25.4 | 19.7 | 3.7 (`Map m3`) | 0 |
| G | 28 | adjacent groups | 94.0 | 93.6-103.9 | 9.9 | 18.6-34.2 | 18.6 | 0.2 (`Map g`) | -0.4/-0.2 |
| H | 22 | child/grandchild pairs | 78.0 | 78.6-79.3 | 2.1 | nested | 83.5 | 0.0 | 0 |

Containment overflow is the maximum per-side painted-hull bbox excursion beyond `anchor +/- halfExtent - anchored offset` relative to the settled anchor; marginal overlap is a non-ancestor pair whose settled footprint-box `forceGapX < 0 && forceGapY < 0`. The 1000-step window maxima are drift outliers (D 78.7, F 27.2) and are reported as such, with no hold band asserted.

Also record the **large reorder** (spec section 8.3.5), asserted nowhere. Build `mapProjection(long generation, int mapCount)` as defined in spec section 7.2: for map index `m` (0-based) the map id is `MAP_{m+1}` for `m < 4` else `00000000-0000-0000-0000-%012d` with `100 + m`; prefix `mM`; child hull `mM-sub` (`mM fundamentals`) with node count `(m == mapCount - 1 && mapCount > 4) ? 8 : 2 + m`; root hull `mM-root` (`Map mM`, mapRoot, EMPHATIC, child `[mM-sub]`, no direct nodes). Settle 4 maps in a `LayoutWorker`, then submit the 5-map projection, and print the retained-anchor moves `applyMax`, `applySum` and the next-step maximum. Expected (spec section 8.3.5): `m0` 0.0/0.0, `m1` 24.1, `m2` 17.1, `m3` 245.9; totals `applyMax 245.9`, `applySum 574.2`, `stepMax 142.1`; post-reorder idle 384 and eventual retained drift 353.8 (review-6 provenance). Report these, do not assert them.

- [ ] **Step 3: Add the 200-child wall-clock test to ClusterPackingShould**

```java
    @Test
    public void twoHundredChildrenPackWithinThreeSeconds() throws Exception {
        final GraphProjection projection = ClusterProximityShould.wideProjection(1L);
        final LayoutWorker worker = new LayoutWorker(LayoutCalibration.spikeDefaults());
        try {
            final long start = System.nanoTime();
            await(worker.submit(LayoutRequest.of(ClusterProximityShould.WORKSPACE, projection,
                ProjectionDiff.between(projection, projection), Collections.<PinProjection>emptyList())));
            final double submitSeconds = (System.nanoTime() - start) / 1.0e9;
            System.out.printf("wideProjection submit (apply + packing): %.3f s%n", submitSeconds);
            assertThat(submitSeconds).isLessThanOrEqualTo(3.0);
        }
        finally {
            worker.close();
        }
    }
```

Add a private `await(CompletionStage<LayoutFrame>)` helper that gets with a 5-second timeout (as in the existing test classes). Measured 0.580 s submit / 1.252 s for 10 steps; the assertion is `<= 3.0` s.

- [ ] **Step 4: Run the focused classes and capture the stdout**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.layout.graphstream.ClusterProximityShould" \
  --tests "org.freeplane.plugin.graph.layout.graphstream.ClusterPackingShould" -PTestLoggingFull
```

Expected: `BUILD SUCCESSFUL`; `ClusterProximityShould` runs 6 tests and `ClusterPackingShould` runs 17 tests, 0 failures. Copy the full evidence stdout into your task report (spec section 8.4 item 2).

- [ ] **Step 5: Commit**

```bash
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/ClusterProximityShould.java \
        freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/ClusterPackingShould.java
git commit -m "test(graph): assert settled idle, crossings and 200-child timing [2026-10-13-graph-cluster-initial-proximity]"
```


## Task 7: Re-baseline TypedForcesShould against the packed contract

**Implementer tier:** Standard
**Lane:** regression
**Depends on:** Tasks 4 and 6

**Ownership:** `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java`

**Validation:** `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.TypedForcesShould`

**Files:**

- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java`

**Interfaces:**

- Consumes: the public engine, `LayoutWorker`, `LayoutCalibration.spikeDefaults()` and the fixture builders already in the class.
- Produces: the class's §7.3 dispositions finalized, with every re-baselined value recorded in the task report. Task 4 already rewrote `hierarchyAnchorsSeedOnTheGroupRing`, `hierarchyLinksPullNestedAnchorsTowardTheHierarchyRestLength` and `TWO_MAP_FIRST_IDLE_STEP = 756`; do not change those again unless a run shows a different measurement (then record the observed value).

- [ ] **Step 1: Run the class and classify every result**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.layout.TypedForcesShould" -PTestLoggingFull
```

The class has 16 tests. Expected dispositions under the final configuration:

- keep as-is, must pass: `produceIdenticalFramesForEqualRequests`, `smallWorkspaceInitialPositionsAreNotCollapsedIntoTheOrigin` (`> 1.0`), `firstStepDoesNotTeleportSeededParticlesOntoTheirNeighbours` (hard `< 8.0`), `aTopologyChangeDoesNotTeleportRetainedParticles` (hard `< 8.0`), `capAggregateCrossMapFanOutDisplacementOncePerParticle` (`<= 0.0050000001`), `largeWorkspacesSeedWiderThanTheMinimumSpread` (`> 150.0`; the packed spread is larger than the old ring spread), `settleRelationshipFreeNestedBoundaryProjectionToIdle` (idle within 2000; measured 42 for fixture A);
- re-run and adapt only if a recorded measurement changed: `acceptRelationshipAndHierarchyEdgesBetweenTheSameAnchors`, `exposeEveryEnclosureAnchor`, `retainHierarchyAnchorsAcrossSteps`, `returnToBaselineCoverageAfterOneHundredAddRemoveCycles`, `observeDirectNodeContainmentInfluenceOnEnclosureAnchorCoordinates` (containment is now stiffer; the anchor-with-pin-nearer relation must still hold), `nativeRepulsionDisplacementsSumToZeroWithoutDrift` (sum `<= 1.0e-9`);
- already rewritten by Task 4: `hierarchyAnchorsSeedOnTheGroupRing`, `hierarchyLinksPullNestedAnchorsTowardTheHierarchyRestLength`, `twoMapWorkspaceSettlesToIdle`.

For any test that fails, record the observed value and the old expectation in the report before adjusting the expectation; never weaken a hard movement bound (`< 8.0` per step, `<= 0.0050000001` cross-map) and never turn an exact engine-frame `isEqualTo` into a tolerance.

- [ ] **Step 2: Verify the test-local mirrors are gone**

```bash
grep -n "ringRadius\|maximumWidth\|widthOf\|heightOf\|CHAR_WIDTH_UPPER_BOUND\|CHAR_HEIGHT_UPPER_BOUND\|BOUNDARY_PADDING\|FRAME_CLEARANCE" \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java
```

Expected: no matches for `ringRadius`, `maximumWidth`, `widthOf`, `heightOf`, `CHAR_WIDTH_UPPER_BOUND`, `CHAR_HEIGHT_UPPER_BOUND`, `BOUNDARY_PADDING`, `FRAME_CLEARANCE` (Task 4 deleted them; `SIBLING_GAP` remains and is used by the rewritten seed test). If any survivor is still referenced by a test, keep it only where the test needs it; otherwise delete it per the legacy-removal policy.

- [ ] **Step 3: Re-run the class to green and commit**

```bash
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.layout.TypedForcesShould"
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java
git commit -m "test(graph): re-baseline typed force fixtures on packed geometry [2026-10-13-graph-cluster-initial-proximity]"
```

Expected: `BUILD SUCCESSFUL`, 16 tests, 0 failures.


## Task 8: Re-baseline BoundarySeparationShould and delete the ReferenceRepulsionFixture estimator mirror

**Implementer tier:** Advanced
**Lane:** regression
**Depends on:** Tasks 4 and 7

**Ownership:** `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationShould.java`, `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/ReferenceRepulsionFixture.java`

**Validation:** `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.BoundarySeparationShould`

**Files:**

- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationShould.java`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/ReferenceRepulsionFixture.java`

**Interfaces:**

- Consumes: public engine/worker, `GraphGeometryEngine.computeHulls`, `AwtGeometryTextMetrics`, `HullIntersection.siblingOverlap`, `HullGeometry.contains(LayoutPoint)`, `ReferenceRepulsionFixture` projection builders and hull/node accessors.
- Produces: the final §7.3 dispositions for the class; `ReferenceRepulsionFixture` without `Sizes`, `boundaryRadius(GraphProjection, EnclosureHullKey)`, `MAX_RENDERED_NODE_RADIUS`, and the constants only they used.

- [ ] **Step 1: Rewrite the three seed-geometry tests to the packed contract**

- `siblingBoundariesSeedWithoutOverlap` - at the engine frame for `wideSiblingFixture()` (all empty enclosures, so the label-sized box is the exact footprint: `width = max(16, len * 16 + 16)`, `height = 40`), assert for every pair `Math.max(boxGapX, boxGapY) >= SIBLING_GAP - 1.0e-9` with `boxGapX = |x1 - x2| - (w1 + w2) * 0.5`, `boxGapY = |y1 - y2| - (h1 + h2) * 0.5`. Drop the ring-radius span math.
- `nestedGroupRingsDoNotInterleave` - same fixture as today (root with two groups, each group with three `128 x 40` empty subgroups). At the engine frame assert the exact packed anchors: `group-0 == (0.0, 0.0)`, `group-1 == (-440.0, 0.0)`, and within each group the subgroups at `(0.0, 0.0)`, `(-136.0, 0.0)`, `(136.0, 0.0)` relative to the group anchor. Assert each sibling subgroup pair satisfies the box-gap bound above. Drop `ringRadius` and `maximumReach`.
- `rootFramesSeparateOnTheTopRing` - same two-map fixture as today (each root has one `128 x 40` empty group child, so each root footprint is `160 x 72`). At the engine frame assert `root(0) == (0.0, 0.0)`, `root(1) == (-168.0, 0.0)`, and `max(boxGapX, boxGapY) >= SIBLING_GAP - 1.0e-9` with the `160 x 72` boxes. Drop `reachOf`/`widthOf`-based math.

- [ ] **Step 2: Rewrite the settled and ancestor-exclusion assertions**

- `settledSiblingBoundariesRemainSeparated` - after 1500 steps compute painted hulls and assert `HullIntersection.siblingOverlap(box1, box2)` is false for every sibling pair in `wideSiblingFixture()`; print the measured settled gaps. Do not assert an 8.0 gap after settling.
- `directParentChildAnchorsSatisfyTheProximityInvariantAfterSettling` - replace `distance <= ReferenceRepulsionFixture.boundaryRadius(parent) - 16.0` with the painted-hull containment check: for every child with a parent, compute the geometry and assert `parentHull.contains(childAnchor)` is true; print the measured per-axis anchor offset (reported, not asserted at 4 units).
- `grandchildAnchorsAreExcludedFromBoundaryRepulsion` - keep the intent with the measured bound `distance(root, axioms) < 100.0` (measured 27.9 on A); record the measured value.
- `coincidentParentChildAnchorsStayExcluded` - replace the `boundaryRadius(root) - 16.0` limit with `distance(root, zfc) < 100.0` (measured 37.0 on A); record the measured value.
- `reparentedBoundaryRefreshesAncestorExclusion` - replace the `boundaryRadius(axioms) - 16.0` limit with `distance(axioms, definitions) < 100.0` after reparenting (the packed parent-child rest is ~84.7); record the measured value.
- `siblingAnchorsRemainSeparatedAfterSettling` - keep Task 4's painted-hull `siblingOverlap == false` and the `<= 1000.0` anchor-span bound.
- `pinnedNodesKeepTheirForcedPositions` - pin coordinates must stay exact; re-run unchanged.
- `pinnedFixtureSettlesWithPinPositionUnchanged` - keep the pin `(1059.0, -145.0)` exact; re-run and record the measured first idle and the worst `rms`/`max` over the 100-frame window; if a threshold fails, use the measured value (record old and new).
- `unpinTransitionSettlesAfterFormerPinReleased` - re-run; the released pin must move `> 10.0` and the fixture must idle within 2000; record the measured first idle.
- `repeatedSettlesAreDeterministic` - re-run unchanged (determinism must hold).
- `directNodesLieInsideTheirParentHullAfterGeometryComputation` - re-run and record the hull-containing positions; adjust only if a recorded measurement changed.
- `reproducedCasePipelineFixtureCrossesAtTheRawSettle`, `reproducedCasePipelineFixturePublishesRepairedFrames`, `reproducedCaseSettleSequenceReachesIdleWithStableCorrectionDeltas` - re-run; re-baseline idle/RMS/delta goldens with recorded measurements only where a run fails, keeping the falsifiable shape (a raw crossing must still be detectable and repaired).

- [ ] **Step 3: Delete the test-local estimator mirrors**

In `BoundarySeparationShould` delete the static helpers `ringRadius`, `maximumReach`, `reachOf`, `widthOf`, `heightOf` and any constants that become unused (`CHAR_WIDTH_UPPER_BOUND`, `CHAR_HEIGHT_UPPER_BOUND`, `BOUNDARY_PADDING`, `FRAME_CLEARANCE`); keep `SIBLING_GAP`. Delete `assertNoSiblingOverlap` once no caller remains (the packed-gap and painted-hull replacements above).

In `ReferenceRepulsionFixture` delete the private `Sizes` class, `boundaryRadius(GraphProjection, EnclosureHullKey)`, `MAX_RENDERED_NODE_RADIUS`, and the constants that only they use (`CHAR_WIDTH_UPPER_BOUND`, `CHAR_HEIGHT_UPPER_BOUND`, `BOUNDARY_PADDING`, `SIBLING_GAP`, `FRAME_CLEARANCE`). Keep the projection builders (`referenceProjection`, `reparentedProjection`), the node/hull accessors and `WORKSPACE`/`MAP`.

- [ ] **Step 4: Confirm no production consumer of the deleted mirrors remains**

```bash
grep -rn "boundaryRadius\|MAX_RENDERED_NODE_RADIUS\|directNodeRingRadius\|directNodeReach\|childBoundaryReach\|reachOf\|sizeOf" \
  freeplane_plugin_graph/src/main freeplane_plugin_graph/src/test | grep -v "/build/"
```

Expected: no matches. If a match remains, report `BLOCKED` with the path; do not keep dead code.

- [ ] **Step 5: Run the classes to green and commit**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.layout.BoundarySeparationShould" \
  --tests "org.freeplane.plugin.graph.layout.TypedForcesShould" -PTestLoggingFull
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationShould.java \
        freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/ReferenceRepulsionFixture.java
git commit -m "test(graph): re-baseline boundary separation on packed footprints [2026-10-13-graph-cluster-initial-proximity]"
```

Expected: `BUILD SUCCESSFUL`, 0 failures in both classes.


## Task 9: Full-suite acceptance and evidence recording

**Implementer tier:** Advanced
**Lane:** evidence
**Depends on:** Tasks 7 and 8
**Runs alone:** this task builds and runs the repository-wide suite; do not dispatch it concurrently with any other task.

**Ownership:** no repository files. The deliverable is the task report (the task evidence file); do not stage or commit anything.

**Validation:** `gradle :freeplane_plugin_graph:test` and `gradle test`

**Files:** none (report-only).

**Interfaces:**

- Consumes: every artifact produced by Tasks 1-8.
- Produces: the recorded evidence of spec section 8.4 items 1-4: commands and configuration, the full `ClusterProximityShould` and 200-child stdout, the full-suite result including the enumerated §7.3 tests, and the reported-vs-asserted summary.

- [ ] **Step 1: Run the module suite and classify the failures**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(git rev-parse --show-toplevel)"
gradle :freeplane_plugin_graph:test -PTestLoggingFull 2>&1 | tee /tmp/graph-cluster-module-suite.txt
```

Expected: `BUILD SUCCESSFUL` (or a failure set that is exactly the four environmental failures `ActivatorShould` x2 and `AccessibleGraphCanvasShould` x2, which fail identically on production). The suite is about 1 074 tests across 87 `*Should` classes. Record the total test count and the exact failure list. Any other failure is a change-induced regression: report `BLOCKED` with the failing test and the observed output.

- [ ] **Step 2: Run the project suite**

```bash
gradle test -PTestLoggingFull 2>&1 | tee /tmp/graph-cluster-project-suite.txt
```

Record the result and the exact failure set. Compare with the module-suite set; a project-suite failure outside `freeplane_plugin_graph` that is unrelated to the layout package must be recorded with its test name and message. Do not edit any other module.

- [ ] **Step 3: Run the enumerated §7.3 tests as a named subset**

```bash
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.layout.GraphStreamBoundaryShould" \
  --tests "org.freeplane.plugin.graph.layout.TypedForcesShould" \
  --tests "org.freeplane.plugin.graph.layout.BoundarySeparationShould" \
  --tests "org.freeplane.plugin.graph.layout.graphstream.ClusterPackingShould" \
  --tests "org.freeplane.plugin.graph.layout.graphstream.BoundaryFootprintShould" \
  --tests "org.freeplane.plugin.graph.layout.graphstream.ClusterProximityShould" -PTestLoggingFull
```

Expected: `BUILD SUCCESSFUL`. Record the per-class test counts.

- [ ] **Step 4: Capture the evidence block stdout**

Re-run `ClusterProximityShould` alone and save its stdout; the evidence lines must include the fixture table values of Task 6 Step 2 (idle, packed/settled distances, 1000-step window maximum hold deviation, painted gaps, containment overflow, marginal overlaps, large-reorder motion). Also capture 200-child timing from `ClusterPackingShould.twoHundredChildrenPackWithinThreeSeconds` (measured 0.580 s submit / 1.252 s for 10 steps).

- [ ] **Step 5: Write the reported-vs-asserted summary into the report**

Record, as report text, exactly the §8.2 table with the observed status of each row: initial frame equals packing (asserted), no-overlap and contact gutter (asserted), pinned worker-path non-overlap plus pin (asserted), packed-position invariant (asserted), idle within budget and zero crossings (asserted), 200-child `<= 3.0 s` (asserted), enumerated existing tests re-baselined and full suites (asserted), and the reported values (settled hold deviation, painted gaps, window maxima, containment overflow, marginal overlaps, large reorder). Quote only values from spec sections 8.3.2-8.3.5; state explicitly that no settled hold band is asserted (window max hold deviation reaches 78.7 on D).

- [ ] **Step 6: Report (no commit)**

State `STATUS: DONE` (or `DONE_WITH_CONCERNS` naming the environmental failures and any project-suite failures), the exact commands, the test counts, the failure sets, and the evidence summary. There is no repository change to commit; do not create a commit for this task.


## Task 10: Label readability gate (baseline vs post-change)

**Implementer tier:** Advanced
**Lane:** evidence
**Depends on:** Task 9
**Runs alone:** this task starts a real application on a virtual X display and builds a second scratch worktree; do not dispatch it concurrently with any other task.

**Ownership:** `docs/superpowers/plans/2026-10-13-graph-cluster-initial-proximity-label-gate.md` (new, evidence text), `docs/superpowers/specs/mockups/2026-10-13-graph-cluster-initial-proximity/label-gate/baseline/` and `.../label-gate/post/` (new, screenshots). No production or test file changes.

**Validation:** the headless `ScreenLabelPlacement` measurements and the screenshots exist for both configurations and are reported with a delta; there is no numeric pass/fail threshold.

**Files:**

- Create: `docs/superpowers/plans/2026-10-13-graph-cluster-initial-proximity-label-gate.md`
- Create: `docs/superpowers/specs/mockups/2026-10-13-graph-cluster-initial-proximity/label-gate/baseline/*.png`
- Create: `docs/superpowers/specs/mockups/2026-10-13-graph-cluster-initial-proximity/label-gate/post/*.png`

**Interfaces:**

- Consumes: `ScreenLabelPlacement.place(LabelPlacementRequest, List<PlacedLabel>, LabelFonts)`, `LabelPlacementRequest.of(GraphProjection, GraphGeometry, LayoutPositions, double zoom, double centerX, double centerY, Rectangle2D, Set<ProjectedEndpointKey>, RenderingLevel)`, `LabelFonts.from(GraphTheme.resolve(DisplaySettings.CanvasTheme.LIGHT))`, `PlacedLabel.bounds()`, `PlacedLabel.leader()`.
- Produces: the side-by-side crowding metrics and screenshots required by spec sections 8.4 item 5 and 8.5. This is a user-review gate: if crowding materially exceeds the recorded baseline, decision 3 (no world-space label allowance) is re-opened with the user; any mitigation must be screen-space, never a world-space label reservation.

- [ ] **Step 1: Build the baseline scratch worktree**

```bash
cd "$(git rev-parse --show-toplevel)"
git worktree add --detach /tmp/graph-label-baseline 9b8259bcd1cb2c8d37121e63bcac0107386fed0d
```

Do not modify the base-commit worktree; it is a read-only baseline build. Remove the scratch worktree with `git worktree remove /tmp/graph-label-baseline` in the final step.

- [ ] **Step 2: Write and run the headless label probe on both configurations**

Write a scratch probe under `/tmp/graph-label-probe/probe/org/freeplane/plugin/graph/canvas/LabelGateProbe.java` in package `org.freeplane.plugin.graph.canvas` so it can call the package-private `ScreenLabelPlacement.place(...)` overload. The probe:

1. builds the A-H fixture projections with the same fixture data as `ClusterProximityShould` (copy the builders into the probe so it compiles against either worktree's main classes; the projection APIs are unchanged);
2. settles each fixture with `LayoutWorker` at the documented endpoint (first idle else 2000 steps, then 200 steps);
3. computes `GraphGeometry` with `GraphGeometryEngine.computeHulls(projection, positions, new AwtGeometryTextMetrics(new Font("Dialog", Font.PLAIN, 12), new FontRenderContext(null, true, true)))`;
4. calls `new ScreenLabelPlacement().place(LabelPlacementRequest.of(projection, geometry, positions, zoom, centerX, centerY, new Rectangle2D.Double(0.0, 0.0, 1440.0, 900.0), Collections.<ProjectedEndpointKey>emptySet(), RenderingLevel.FULL), null, LabelFonts.from(GraphTheme.resolve(DisplaySettings.CanvasTheme.LIGHT)))` at `zoom = 1.0` (fit-all) and `zoom = 4.0` (reading zoom);
5. prints one line per fixture/zoom: overlapping label pairs (count of label pairs whose `PlacedLabel.bounds()` intersect), `maxLeaderLength`, `meanLeaderLength`, `leaderLengthOverFootprint` (`leader length / min(width, height)` of the label's enclosure footprint), and the label count.

Run the probe against the baseline worktree classes and against the current worktree classes, saving `/tmp/graph-label-baseline.txt` and `/tmp/graph-label-post.txt`. Use this exact recipe (the classpath follows the stored prototype README):

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
REPO="$(git rev-parse --show-toplevel)"
BASELINE=/tmp/graph-label-baseline
GC=$(find "$HOME/.gradle/caches/modules-2/files-2.1/org.graphstream" -name "*.jar" | tr '\n' ':')
gradle -p "$REPO" :freeplane_plugin_graph:compileTestJava
gradle -p "$BASELINE" :freeplane_plugin_graph:compileTestJava
CP_POST="$REPO/freeplane_plugin_graph/build/classes/java/main:$REPO/freeplane_plugin_graph/build/classes/java/test:$REPO/freeplane/build/classes/java/main:$GC"
CP_BASE="$BASELINE/freeplane_plugin_graph/build/classes/java/main:$BASELINE/freeplane_plugin_graph/build/classes/java/test:$BASELINE/freeplane/build/classes/java/main:$GC"
mkdir -p /tmp/graph-label-probe/classes
"$JAVA_HOME/bin/javac" -source 8 -target 8 -nowarn -cp "$CP_POST" -d /tmp/graph-label-probe/classes \
  /tmp/graph-label-probe/src/org/freeplane/plugin/graph/canvas/LabelGateProbe.java
"$JAVA_HOME/bin/java" -Djava.awt.headless=true -cp "/tmp/graph-label-probe/classes:$CP_BASE" \
  org.freeplane.plugin.graph.canvas.LabelGateProbe | tee /tmp/graph-label-baseline.txt
"$JAVA_HOME/bin/java" -Djava.awt.headless=true -cp "/tmp/graph-label-probe/classes:$CP_POST" \
  org.freeplane.plugin.graph.canvas.LabelGateProbe | tee /tmp/graph-label-post.txt
```

If no idle frame is found within the budget, use the documented 2000th-step endpoint and record it.

- [ ] **Step 3: Capture real-application screenshots**

Use the project skill `capture-freeplane-real-app-ui-evidence` (`/home/henry/.pi/agent/projects-memory/freeplane/skills/capture-freeplane-real-app-ui-evidence/SKILL.md`) on a virtual X display. Capture, for both the baseline worktree and the current worktree, one fit-all screenshot and one reading-zoom screenshot for a workspace containing at least fixture B (two maps) and fixture G (four sibling subgroups). Save baseline images under `.../label-gate/baseline/` and post-change images under `.../label-gate/post/`. If the real-application capture cannot run in this environment (no X display, missing VM), record that as an `observational` concern and continue with the headless metrics; do not fabricate screenshots.

- [ ] **Step 4: Write the label-gate evidence note**

Write `docs/superpowers/plans/2026-10-13-graph-cluster-initial-proximity-label-gate.md` with: the two commands/configurations, the baseline and post-change metric tables (per fixture and zoom), the delta, the screenshot paths, and the explicit statement that this is a user-review gate with no numeric pass/fail threshold. If crowding materially exceeds the baseline, state that decision 3 must be re-opened with the user and that any mitigation must be screen-space.

- [ ] **Step 5: Commit the evidence and remove the scratch worktree**

```bash
git add docs/superpowers/plans/2026-10-13-graph-cluster-initial-proximity-label-gate.md \
        docs/superpowers/specs/mockups/2026-10-13-graph-cluster-initial-proximity/label-gate
git commit -m "docs(graph): record the label readability gate [2026-10-13-graph-cluster-initial-proximity]"
git worktree remove /tmp/graph-label-baseline
git status --porcelain
```

Expected: the commit contains only the label-gate evidence; `git status --porcelain` prints nothing.


## Execution Notes

### Suggested lane grouping

Tasks run in numeric order; the lanes below are the PM's projection groups. Every group is sequential
inside itself, and the numbered dependency edges (`Depends on`) are the only scheduling constraints.

- **calibration:** Task 1. Files: `LayoutCalibration.java`, `GraphStreamBoundaryShould.java`.
- **packing:** Task 2, with Task 6's `ClusterPackingShould` addition (Task 6 is primarily `acceptance`; it touches `ClusterPackingShould.java`, so project it into the packing lane if lanes are split).
- **footprint:** Task 3, with Task 5's `BoundaryFootprintShould` additions (same caveat).
- **engine:** Task 4. Files: `GraphStreamLayoutEngine.java`, `TypedSpringBox.java`, `TypedNodeParticle.java`, plus the four change-induced test updates in `TypedForcesShould.java`/`BoundarySeparationShould.java` (same files as the regression lane; Task 4 must precede Tasks 7 and 8).
- **acceptance:** Tasks 5 and 6. Files: `ClusterProximityShould.java` (created in Task 5, extended in Task 6), plus the two cross-lane test additions above.
- **regression:** Tasks 7 and 8. Files: `TypedForcesShould.java`, `BoundarySeparationShould.java`, `ReferenceRepulsionFixture.java`.
- **evidence:** Tasks 9 and 10. Task 9 is report-only; Task 10 writes evidence files under `docs/`.

A lane projection that parallelises Tasks 1-8 must not run two tasks that share a file concurrently:
`GraphStreamLayoutEngine.java` (Tasks 3, 4), `BoundaryFootprintShould.java` (Tasks 3, 5),
`ClusterPackingShould.java` (Tasks 2, 6), `ClusterProximityShould.java` (Tasks 5, 6),
`TypedForcesShould.java` (Tasks 4, 7), `BoundarySeparationShould.java` (Tasks 4, 8). The safest
projection is the numeric order; the lane groups exist for review assignment and for splitting the
evidence lane off.

### Interface boundaries between lanes

- **packing -> footprint:** `ClusterPacking.packItems(List<ClusterItem>, double): List<ClusterPlacement>`,
  `ClusterItem(EnclosureHullKey anchorKey, double width, double height, double anchorX, double anchorY, int order)`,
  `ClusterPlacement(ClusterItem item, double x, double y)` with `contacts`, `CANDIDATE_EPSILON = 1e-9`,
  `CONTACT_GAP_TOLERANCE = 0.75`. `anchorKey == null` is the content item.
- **footprint -> engine:** `BoundarySizes.Footprint(width, height, anchorX, anchorY)`,
  `footprint(EnclosureHullKey)`, `placements(EnclosureHullKey)`, `placementOf(parent, child)`,
  `topLevelPlacements()`, `topLevelPlacementOf(key)`, `NODE_SETTLE_MARGIN = 8.0`.
- **engine -> springs:** `TypedSpringBox.configureAnchorExtents(id, halfWidth, halfHeight, offsetX, offsetY)`
  and `TypedNodeParticle.separationHalfWidth()/separationHalfHeight()/separationOffsetX()/separationOffsetY()`.
  `addBoundaryRepulsion(particle, displacement)` keeps its two-argument signature.
- **engine -> acceptance tests:** public `LayoutEngine.apply`/`LayoutWorker.submit` frames, plus package-private
  `BoundarySizes` for expected-value composition. The engine frame is the seeded frame **before**
  `BoundarySeparationCorrection`; pinned fixture C is exempt from worker-path packing equality
  (measured divergence 380.45).
- **acceptance -> evidence:** the printed `ClusterProximityShould` evidence lines and the
  `twoHundredChildrenPackWithinThreeSeconds` timing line; Task 9 copies them verbatim into the report.

### Tasks that must run alone

- Task 9 (repository-wide `gradle test`): builds and runs every module; do not dispatch concurrently.
- Task 10 (real application on a virtual X display plus a second scratch worktree): exclusive display and
  build resources; do not dispatch concurrently.
- Task 4's full-module run and Task 6's evidence run are self-contained but should not overlap another
  Gradle invocation in the same worktree (Gradle daemon lock contention).

### Evidence and reporting

Each task report must name the exact command, the test counts, and the observed failure set. Task 4
records the expected-red set (`siblingAnchorsRemainSeparatedAfterSettling`,
`twoMapWorkspaceSettlesToIdle` 45 -> 756, `hierarchyAnchorsSeedOnTheGroupRing`,
`hierarchyLinksPullNestedAnchorsTowardTheHierarchyRestLength`) and then the green set. Tasks 7 and 8
record every re-baselined value with its old expectation. Task 9 records the §8.4 items 1-4 and the
§8.2 reported-vs-asserted table. Task 10 records the §8.4 item 5 label-gate delta.

### Known residual

The spec's open item 7 (`mapRoot()`-with-`parentHull()` enclosures are both top-level and placed in
their parent's assembly) is preserved by the existing `Seeds.isTopLevel` predicate and is not exercised
by fixtures A-H. No task may add a fixture for it; if the user confirms such projections can occur, it
is a follow-up outside this plan's scope.
