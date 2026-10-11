# Graph Workspace Directional Arrowhead Prominence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.

**Goal:** Replace the fixed `8.0 / zoom` screen-space arrowhead in the Graph Workspace with the rule
`L = min(max(12.5, 8.68 x zoom), a x zoom)` screen px and the `0.84 : 1` aspect, so a directional
edge is legible at every zoom without letting a head outgrow the space its edge actually has.

**Architecture:** The production change is confined to
`org.freeplane.plugin.graph.canvas.GraphPainter`. Task 1 lands the three named constants and four
package-private static helpers that hold the whole rule, plus their unit tests. Task 2 rewires both
`paintEdges` call sites and `paintArrow` to the capped length and the two edge arrow flags, and
pins the paint path with pixel probes. Task 3 adds the dark-theme fill sample and runs the module
regression gate. Task 4 records running-application acceptance evidence. There is exactly one
rendering path: the old `8.0 / zoom` and `3.5 / zoom` literals are removed, not retained behind a
fallback.

**Tech Stack:** Java 8 language level on runtime Java 21.0.8-zulu, Gradle multi-project build
(module `freeplane_plugin_graph`), JUnit 4.13.2 with AssertJ 3.27.3, AWT `Graphics2D` /
`BufferedImage` under `java.awt.headless=true`.

**Implementation contract:** `docs/superpowers/specs/2026-10-10-graph-arrowhead-prominence-spec.md`
(the contract implemented here; every value a task needs is restated in the task, so no task has to
open it), derived from the approved design
`docs/superpowers/specs/2026-10-10-graph-arrowhead-prominence-design.md`.

## Global Constraints

- Java 8 language target with the Java 21.0.8-zulu toolchain: before every Gradle command run
  `export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"` and
  `export PATH="$JAVA_HOME/bin:$PATH"`.
- Use `gradle` (never `gradlew`, never Maven) from the worktree root. Add `-PTestLoggingFull`
  whenever a test run must show verbose failures.
- Sources are UTF-8; indentation is 4 spaces.
- No translation resources are touched (`GraphPainter` carries no translatable strings), so the
  repository rule `gradle format_translation` does not apply to this change.
- No new dependencies; no settings changes; no XML schema changes.
- The production change is confined to
  `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java`; the
  test change is confined to
  `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java`.
  Do not modify any other file. Task 4 additionally creates the two evidence files named in its
  `**Files:**` block and nothing else.
- Every commit message ends with the commit identifier `[2026-10-10-graph-arrowhead-prominence]`.
- Keep exactly one rendering path: the old `8.0 / zoom` and `3.5 / zoom` literals are removed, not
  retained behind a fallback.
- Tests run with `java.awt.headless=true` (the module's Gradle test configuration already sets it).
- The finite-length guard stays first in `paintArrow`: a degenerate direction must return before
  `availableArrowHeadWorldLength`, `effectiveArrowHeadLengthPx` or `arrowHeadHalfBasePx` are
  reached, so the cap can never be reached with `d = 0`.

## Task 1: Add the arrowhead rule constants and helpers to GraphPainter

**Implementer tier:** Standard
**Lane:** graph-arrowhead-painting
**Depends on:** none

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java` (`:37-38`, immediately after `final class GraphPainter {`, for the three constants; insert the four helpers immediately before `private static void paintArrow(...)` at `:149`)
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java` (insert both test methods immediately before `private static GraphTheme lightTheme()` at `:1019`)

**Interfaces:**

- Consumes: nothing; this is the first task. `GraphPainter` is a package-private `final` class in
  `org.freeplane.plugin.graph.canvas`, and `GraphCanvasPaintShould` is in the same package, so
  package-private `static` helpers are directly callable.
- Produces, all inside `org.freeplane.plugin.graph.canvas.GraphPainter`:
  - `private static final double ARROW_HEAD_MINIMUM_LENGTH_PX = 12.5;`
  - `private static final double ARROW_HEAD_LENGTH_PER_ZOOM = 0.62 * 14.0;`
  - `private static final double ARROW_HEAD_HALF_BASE_RATIO = 0.42;`
  - `static double arrowHeadLengthPx(final double zoom)` returning `max(12.5, 8.68 x zoom)` screen px
  - `static double availableArrowHeadWorldLength(final double attachmentDistanceWorld, final boolean arrowAtFirst, final boolean arrowAtSecond)` returning `arrowAtFirst && arrowAtSecond ? d / 2.0 : d` world units
  - `static double effectiveArrowHeadLengthPx(final double zoom, final double availableWorldLength)` returning `min(arrowHeadLengthPx(zoom), availableWorldLength x zoom)` screen px
  - `static double arrowHeadHalfBasePx(final double lengthPx)` returning `0.42 x lengthPx` screen px

- [ ] **Step 1: Write the two failing unit tests (spec item 1 and item 2)**

In `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java`,
insert this block immediately before the line `    private static GraphTheme lightTheme() {`. The
`within` import and every type used here already exist in the file.

```java
    @Test
    public void arrowHeadLengthIsFlooredUntilTheCrossoverAndGrowsStrictlyAboveIt() {
        assertThat(GraphPainter.arrowHeadLengthPx(0.6)).isCloseTo(12.5, within(1e-9));
        assertThat(GraphPainter.arrowHeadLengthPx(1.0)).isCloseTo(12.5, within(1e-9));
        assertThat(GraphPainter.arrowHeadLengthPx(1.44)).isCloseTo(12.5, within(1e-9));
        assertThat(GraphPainter.arrowHeadLengthPx(3.0)).isCloseTo(26.04, within(1e-9));
        assertThat(GraphPainter.arrowHeadLengthPx(1.45))
            .isGreaterThan(GraphPainter.arrowHeadLengthPx(12.5 / 8.68));
        assertThat(GraphPainter.arrowHeadHalfBasePx(12.5)).isCloseTo(5.25, within(1e-9));
    }

    @Test
    public void arrowHeadRuleCapAndBidirectionalSplitAreSelectedInTheHelpers() {
        assertThat(GraphPainter.availableArrowHeadWorldLength(6.0, false, false)).isCloseTo(6.0, within(1e-9));
        assertThat(GraphPainter.availableArrowHeadWorldLength(6.0, true, true)).isCloseTo(3.0, within(1e-9));
        assertThat(GraphPainter.availableArrowHeadWorldLength(6.0, true, false)).isCloseTo(6.0, within(1e-9));
        assertThat(GraphPainter.availableArrowHeadWorldLength(6.0, false, true)).isCloseTo(6.0, within(1e-9));
        assertThat(GraphPainter.effectiveArrowHeadLengthPx(1.0, 100.0)).isCloseTo(12.5, within(1e-9));
        assertThat(GraphPainter.effectiveArrowHeadLengthPx(0.6, 6.0)).isCloseTo(6.0 * 0.6, within(1e-9));
        assertThat(GraphPainter.effectiveArrowHeadLengthPx(1.0, 3.0)).isCloseTo(3.0, within(1e-9));
        assertThat(GraphPainter.arrowHeadHalfBasePx(GraphPainter.effectiveArrowHeadLengthPx(0.6, 6.0)))
            .isCloseTo(0.42 * 6.0 * 0.6, within(1e-9));
    }
```

The tolerance is `within(1e-9)` on every double; the strict growth check uses `isGreaterThan`, not
`isCloseTo`. Both flags are pinned individually: `(6.0, true, false)` and `(6.0, false, true)`
both stay 6.0, so a helper that halves on a single flag is caught here rather than only by the
painted item-5 probe in Task 2.

- [ ] **Step 2: Run the canvas paint tests and confirm the red failure**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould" -PTestLoggingFull
```

Expected: `BUILD FAILED` (exit status 1) at `:freeplane_plugin_graph:compileTestJava`, with
`cannot find symbol` naming `arrowHeadLengthPx`, `availableArrowHeadWorldLength`,
`effectiveArrowHeadLengthPx` and `arrowHeadHalfBasePx` in `GraphCanvasPaintShould.java`. No test
executes; this compile failure is the item-1 and item-2 red.

- [ ] **Step 3: Add the three named constants**

In `GraphPainter.java`, replace:

```java
final class GraphPainter {
```

with:

```java
final class GraphPainter {
    // Screen px. Floor that keeps a head legible below the growth crossover.
    private static final double ARROW_HEAD_MINIMUM_LENGTH_PX = 12.5;
    // 0.62 x the maximum node radius in world units:
    // GraphGeometryEngine.BASE_RADIUS 8.0 x NodeProminence.MAX_SCALE 1.75 = 14.0 world.
    private static final double ARROW_HEAD_LENGTH_PER_ZOOM = 0.62 * 14.0;
    // Head base is 0.84 x length; the aspect is preserved when the cap shrinks a head.
    private static final double ARROW_HEAD_HALF_BASE_RATIO = 0.42;
```

`ARROW_HEAD_LENGTH_PER_ZOOM` is the `8.68` of the design; `0.62 * 14.0` is written as the derivation
instead of the already-evaluated literal.

- [ ] **Step 4: Add the four package-private static helpers**

In `GraphPainter.java`, insert this block immediately before the line
`    private static void paintArrow(final Graphics2D graphics, final LayoutPoint tip, final LayoutPoint from,`:

```java
    // Screen px.
    static double arrowHeadLengthPx(final double zoom) {
        return Math.max(ARROW_HEAD_MINIMUM_LENGTH_PX, ARROW_HEAD_LENGTH_PER_ZOOM * zoom);
    }

    // World units in, world units out.
    static double availableArrowHeadWorldLength(final double attachmentDistanceWorld,
            final boolean arrowAtFirst, final boolean arrowAtSecond) {
        return arrowAtFirst && arrowAtSecond ? attachmentDistanceWorld / 2.0 : attachmentDistanceWorld;
    }

    // Screen px.
    static double effectiveArrowHeadLengthPx(final double zoom, final double availableWorldLength) {
        return Math.min(arrowHeadLengthPx(zoom), availableWorldLength * zoom);
    }

    // Screen px.
    static double arrowHeadHalfBasePx(final double lengthPx) {
        return ARROW_HEAD_HALF_BASE_RATIO * lengthPx;
    }
```

The `d` versus `d / 2` decision exists only inside `availableArrowHeadWorldLength`; it is never
taken inline at a call site. The helpers are unused by production code in this task; that is
deliberate, and `paintArrow` still carries the old literals so the pre-existing suite stays green.

- [ ] **Step 5: Compile production and test sources**

```bash
gradle :freeplane_plugin_graph:compileJava :freeplane_plugin_graph:compileTestJava
```

Expected: `BUILD SUCCESSFUL`, exit status 0.

- [ ] **Step 6: Run the canvas paint tests and confirm they pass**

```bash
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould" -PTestLoggingFull
```

Expected: `BUILD SUCCESSFUL`; the two new methods pass and every pre-existing
`GraphCanvasPaintShould` test still passes, because `paintArrow` is untouched in this task.

- [ ] **Step 7: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java \
    freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java
git commit -m "Add the arrowhead rule constants and helpers [2026-10-10-graph-arrowhead-prominence]"
```

## Task 2: Rewire paintArrow and pin the painted head with pixel probes

**Implementer tier:** Advanced
**Lane:** graph-arrowhead-painting
**Depends on:** Task 1

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java` (the two `paintArrow` call sites inside `paintEdges` at `:136-141`; the whole `paintArrow` method at `:149-172`)
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java` (the existing `fixture(double)` at `:1545-1589` becomes a delegate plus a two-arg overload; the pixel-profile helpers, `capFixture(boolean)` and the three tests are inserted immediately before `private static GraphTheme lightTheme()`)

**Interfaces:**

- Consumes from Task 1, exactly:
  - `static double GraphPainter.arrowHeadLengthPx(double zoom)`
  - `static double GraphPainter.availableArrowHeadWorldLength(double attachmentDistanceWorld, boolean arrowAtFirst, boolean arrowAtSecond)`
  - `static double GraphPainter.effectiveArrowHeadLengthPx(double zoom, double availableWorldLength)`
  - `static double GraphPainter.arrowHeadHalfBasePx(double lengthPx)`
- Consumes from the existing test file: `paint(CanvasState, GraphPaintState, GraphTheme, RenderingLevel)`
  and `paintAtZoom(CanvasState, GraphPaintState, GraphTheme, double)` (both use `SIZE = 240 x 140`
  and viewport centre `(0, 0)`), `paintCanvas(GraphCanvas)`, `colorPixelsIn(BufferedImage, Color, int, int, int, int)`,
  `nearColorPixelsIn(BufferedImage, Color, int, int, int, int, int)`, `lightTheme()`, `registeredMaps()`,
  `node(MapReferenceId, String, LayoutPoint)`, `source(MapReferenceId, String)`,
  `reference(MapReferenceId, String)`, `rectangle(double, double, double, double, LayoutPoint)`,
  `fixture(double firstRadius)` as it exists before this task, `GraphViewport.of(double, double, double)`,
  `GraphViewport.toScreen(LayoutPoint, Dimension)`, `GraphPaintState.empty()`, `RenderingLevel.FULL`,
  and the production types `GraphProjection`, `ProjectedEdge`, `ProjectedEdgeKey`,
  `ProjectedEndpointKey`, `ProjectedEnclosure`, `ProjectedNode`, `ProjectedNodeKey`,
  `EnclosureKey`, `EnclosureHullKey`, `EdgeContributor`, `ConnectorDescriptor`, `ConnectorSnapshot`,
  `SourceNodeKey`, `SafeNodeLabel`, `BoundaryTier`, `NodeGeometry`, `LayoutPoint`,
  `LayoutFrame`, `LayoutPositions`, `GraphGeometry`, `CanvasState`, `OperationalStatus`, `FIRST_MAP`.
- Produces:
  - `private static void GraphPainter.paintArrow(final Graphics2D graphics, final LayoutPoint tip, final LayoutPoint from, final Color color, final double zoom, final boolean arrowAtFirst, final boolean arrowAtSecond)`
  - `private static Fixture GraphCanvasPaintShould.fixture(final double firstRadius, final double secondRadius)`; the one-arg `fixture(double)` delegates to it with `10.0` for the second radius, exactly as before
  - `private static int GraphCanvasPaintShould.perpendicularRunPx(final BufferedImage image, final Color edgeColor, final int column, final int baselineY)`
  - `private static double GraphCanvasPaintShould.measuredHeadLengthPx(final BufferedImage image, final Point2D tipScreen, final Color edgeColor, final double zoom)`
  - `private static int GraphCanvasPaintShould.maximumPerpendicularRunPx(final BufferedImage image, final Color edgeColor, final int firstColumn, final int lastColumn, final int baselineY)`
  - `private static CanvasState GraphCanvasPaintShould.capFixture(final boolean bidirectional)`
  - `@Test public void paintedArrowHeadLengthTracksTheZoomRule()`
  - `@Test public void paintedArrowHeadFloorHoldsAtLowZoom()`
  - `@Test public void paintedArrowHeadCapBaseAndBidirectionalSplitAtPaintLevel()`

- [ ] **Step 1: Split the fixture builder into a two-radius overload**

In `GraphCanvasPaintShould.java`, replace:

```java
    private static Fixture fixture(final double firstRadius) {
        ProjectedNode first = node(FIRST_MAP, "first", LayoutPoint.of(-45.0, 0.0));
```

with:

```java
    private static Fixture fixture(final double firstRadius) {
        return fixture(firstRadius, 10.0);
    }

    private static Fixture fixture(final double firstRadius, final double secondRadius) {
        ProjectedNode first = node(FIRST_MAP, "first", LayoutPoint.of(-45.0, 0.0));
```

Then, in the two-arg method just created, replace:

```java
        nodes.put(second.key(), NodeGeometry.of(secondLabelAnchor(second), 10.0));
```

with:

```java
        nodes.put(second.key(), NodeGeometry.of(secondLabelAnchor(second), secondRadius));
```

The one-arg overload keeps the old behaviour exactly (second radius `10.0`); the head of
`fixture(*).edge` is at the second node, so the new probes need a 14-unit second node.

- [ ] **Step 2: Add the three pixel-profile helpers**

Insert this block immediately before the line `    private static GraphTheme lightTheme() {`
(after the two unit tests added by Task 1). `baselineY` is the device row of the edge axis and
`tipColumn` is the rounded device column of the geometry attachment point; the scan walks away
from the tip toward the edge source.

```java
    private static int perpendicularRunPx(final BufferedImage image, final Color edgeColor,
            final int column, final int baselineY) {
        return colorPixelsIn(image, edgeColor, column, baselineY - 32, column + 1, baselineY + 32);
    }

    private static double measuredHeadLengthPx(final BufferedImage image, final Point2D tipScreen,
            final Color edgeColor, final double zoom) {
        final double threshold = Math.max(3.0, 1.4 * zoom + 1.0);
        final int tipColumn = (int) Math.round(tipScreen.getX());
        final int baselineY = (int) Math.round(tipScreen.getY());
        double measured = 0.0;
        for (int distance = 0; distance <= 64; distance++) {
            if (perpendicularRunPx(image, edgeColor, tipColumn - distance, baselineY) >= threshold) {
                measured = distance;
            }
        }
        return measured;
    }

    private static int maximumPerpendicularRunPx(final BufferedImage image, final Color edgeColor,
            final int firstColumn, final int lastColumn, final int baselineY) {
        int maximum = 0;
        for (int column = Math.min(firstColumn, lastColumn); column <= Math.max(firstColumn, lastColumn);
                column++) {
            maximum = Math.max(maximum, perpendicularRunPx(image, edgeColor, column, baselineY));
        }
        return maximum;
    }
```

The measurement threshold is `max(3.0, 1.4 x zoom + 1.0)` px: 3.0 at 1.0x and 5.2 at 3.0x. The
anchor is the geometry attachment point, not the first visible pixel: the ring hides the first
`0.7 x zoom` px, so an anchored measurement does not under-measure.

- [ ] **Step 3: Add the spec item 3 test (painted head length)**

Insert this block immediately before the line `    private static GraphTheme lightTheme() {`
(after Step 2's helpers):

```java
    @Test
    public void paintedArrowHeadLengthTracksTheZoomRule() {
        Fixture fixture = fixture(14.0, 14.0);
        GraphTheme theme = lightTheme();
        BufferedImage imageOne = paintAtZoom(fixture.state, GraphPaintState.empty(), theme, 1.0);
        BufferedImage imageThree = paintAtZoom(fixture.state, GraphPaintState.empty(), theme, 3.0);
        Point2D tipOne = GraphViewport.of(0.0, 0.0, 1.0).toScreen(
            fixture.state.geometry().edgeAttachment(fixture.edge.second(),
                fixture.state.geometry().nodes().get(fixture.first.key()).center()),
            SIZE);
        Point2D tipThree = GraphViewport.of(0.0, 0.0, 3.0).toScreen(
            fixture.state.geometry().edgeAttachment(fixture.edge.second(),
                fixture.state.geometry().nodes().get(fixture.first.key()).center()),
            SIZE);

        assertThat(measuredHeadLengthPx(imageOne, tipOne, theme.edgeColor(), 1.0))
            .isCloseTo(12.5, within(1.5));
        assertThat(measuredHeadLengthPx(imageThree, tipThree, theme.edgeColor(), 3.0))
            .isCloseTo(26.0, within(2.0));
        assertThat(Math.max(3.0, 1.4 * 1.0 + 1.0)).isLessThan(0.84 * 12.5);
        assertThat(Math.max(3.0, 1.4 * 3.0 + 1.0)).isLessThan(0.84 * 26.04);
        assertThat((double) perpendicularRunPx(imageOne, theme.edgeColor(),
            (int) Math.round(tipOne.getX()) - 14, (int) Math.round(tipOne.getY())))
            .isLessThan(Math.max(3.0, 1.4 * 1.0 + 1.0));
        assertThat((double) perpendicularRunPx(imageThree, theme.edgeColor(),
            (int) Math.round(tipThree.getX()) - 28, (int) Math.round(tipThree.getY())))
            .isLessThan(Math.max(3.0, 1.4 * 3.0 + 1.0));
    }
```

The fixture is `fixture(14.0, 14.0)`: the head lands on the second node of radius 14 world units
(42 px at 3x), the attachment distance is `d = 62` world, and the cap is unbound at both tested
zooms. The tip is at world (31, 0), i.e. screen (151, 70) at 1.0x and (213, 70) at 3.0x. Tolerance
is `within(1.5)` at 1.0x and `within(2.0)` at 3.0x; these reject both the previous 8 px head and a
constant-screen-size rule (which would measure 12.5 px at 3x). The measured profile just beyond the
head base (`tip - 14` px at 1x, `tip - 28` px at 3x) falls below the threshold, where the
continuing line is.

- [ ] **Step 4: Add the spec item 4 test (floor)**

Insert this block immediately before the line `    private static GraphTheme lightTheme() {`
(after Step 3's test):

```java
    @Test
    public void paintedArrowHeadFloorHoldsAtLowZoom() {
        Fixture fixture = fixture(14.0, 14.0);
        GraphTheme theme = lightTheme();
        BufferedImage imageAt06 = paintAtZoom(fixture.state, GraphPaintState.empty(), theme, 0.6);
        BufferedImage imageAt10 = paintAtZoom(fixture.state, GraphPaintState.empty(), theme, 1.0);
        Point2D attachment = fixture.state.geometry().edgeAttachment(fixture.edge.second(),
            fixture.state.geometry().nodes().get(fixture.first.key()).center());
        Point2D tipAt06 = GraphViewport.of(0.0, 0.0, 0.6).toScreen(attachment, SIZE);
        Point2D tipAt10 = GraphViewport.of(0.0, 0.0, 1.0).toScreen(attachment, SIZE);

        double measuredAt06 = measuredHeadLengthPx(imageAt06, tipAt06, theme.edgeColor(), 0.6);
        double measuredAt10 = measuredHeadLengthPx(imageAt10, tipAt10, theme.edgeColor(), 1.0);
        assertThat(measuredAt06).isCloseTo(measuredAt10, within(1.5));
        assertThat(measuredAt06).isGreaterThanOrEqualTo(11.0);
        assertThat(measuredAt10).isGreaterThanOrEqualTo(11.0);
    }
```

The fixture is the same as item 3's; the tip screen points are (138.6, 70) at 0.6x and (151, 70) at
1.0x. Both measurements must be at least 11.0 px, and the previous 8 px head fails the 1.0x floor,
so the assertion discriminates.

- [ ] **Step 5: Add the `capFixture` builder**

Insert this block immediately before the line `    private static GraphTheme lightTheme() {`
(after Step 4's test). It returns the canvas state for one-headed (`bidirectional = false`) and
two-headed (`bidirectional = true`) runs.

```java
    private static CanvasState capFixture(final boolean bidirectional) {
        ProjectedNode target = node(FIRST_MAP, "cap-target", LayoutPoint.of(14.0, 0.0));
        EnclosureKey enclosureKey = EnclosureKey.of(source(FIRST_MAP, "cap-enclosure"));
        EnclosureHullKey hullKey = EnclosureHullKey.of(Collections.singletonList(enclosureKey));
        ProjectedEndpointKey enclosureEndpoint = ProjectedEndpointKey.ofEnclosure(enclosureKey);
        ProjectedEndpointKey nodeEndpoint = ProjectedEndpointKey.ofNode(target.key());
        ProjectedEnclosure enclosure = ProjectedEnclosure.of(hullKey, Collections.singletonList(enclosureKey),
            Collections.singletonList(SafeNodeLabel.of("cap-enclosure", "cap-enclosure")), "Map",
            Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(),
            Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.SUBTLE);

        Map<ProjectedNodeKey, NodeGeometry> nodes = new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
        nodes.put(target.key(), NodeGeometry.of(LayoutPoint.of(14.0, 0.0), 8.0));
        Map<EnclosureHullKey, HullGeometry> hulls = new LinkedHashMap<EnclosureHullKey, HullGeometry>();
        hulls.put(hullKey, rectangle(-50.0, -20.0, 0.0, 20.0, LayoutPoint.of(-25.0, 0.0)));

        ConnectorDescriptor descriptor = ConnectorDescriptor.of(
            SourceNodeKey.persisted(reference(FIRST_MAP, "cap-source")),
            reference(FIRST_MAP, "cap-target"), bidirectional, true, "source", "middle", "target");
        EdgeContributor contributor = EdgeContributor.nativeConnector(
            ConnectorSnapshot.of(0, descriptor), enclosureEndpoint, nodeEndpoint);
        ProjectedEdge edge = ProjectedEdge.of(ProjectedEdgeKey.of(enclosureEndpoint, nodeEndpoint),
            Collections.singletonList(contributor));
        GraphProjection projection = GraphProjection.projected(1L, Collections.singletonList(target),
            Collections.singletonList(enclosure), Collections.singletonList(edge),
            Collections.<RelationshipResolution>emptyList(), Collections.<PinProjection>emptyList());
        Map<ProjectedNodeKey, LayoutPoint> positions = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        positions.put(target.key(), LayoutPoint.of(14.0, 0.0));
        Map<EnclosureHullKey, LayoutPoint> anchors = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        anchors.put(hullKey, LayoutPoint.of(-25.0, 0.0));
        return CanvasState.of(1L, projection,
            LayoutFrame.of(1L, LayoutPositions.of(positions, anchors), false, 0),
            GraphGeometry.of(nodes, hulls), OperationalStatus.IDLE);
    }
```

A source node cannot pin the cap probe: the source disc would overpaint any overshoot that lands
inside it, so the scan would read the same value either way. The enclosure is filled before the
edges, so nothing overpaints the head body.

- [ ] **Step 6: Add the spec item 5 test (cap, base and bidirectional split at paint level)**

The fixture alignment is load-bearing. It is viewport `GraphViewport.of(0.0, 0.0, 1.0)`, a
240 x 140 image, the edge along world `y = 0` (screen `y = 70`), the enclosure boundary at world
`x = 0` (screen `x = 120`), and the target node centre at world `x = 14` with radius 8, so its
boundary - the head tip - is at world `x = 6` (screen `x = 126`). Then `d = 6` world = 6 px and the
capped base plane lands exactly on screen `x = 120`; the 5.04 px base covers four whole pixel rows
(68-71), giving an exact-colour run of 4 and a measured head length of 6. The ink window is screen
`x` 120-123 and `y` 66-74 with near-colour radius 80. Do not snap, round, or translate this
fixture: a shift of -0.25 px or +0.5 px drops the run below 3 and the measured length to 0, while a
+0.25 px shift still measures 6 with run 4.

Insert this block immediately before the line `    private static GraphTheme lightTheme() {`
(after Step 5's `capFixture`):

```java
    @Test
    public void paintedArrowHeadCapBaseAndBidirectionalSplitAtPaintLevel() {
        GraphTheme theme = lightTheme();
        BufferedImage oneHead = paint(capFixture(false), GraphPaintState.empty(), theme, RenderingLevel.FULL);
        BufferedImage twoHeads = paint(capFixture(true), GraphPaintState.empty(), theme, RenderingLevel.FULL);

        GraphCanvas canvas = new GraphCanvas();
        canvas.setSize(SIZE);
        canvas.setTheme(theme);
        canvas.setCanvasState(capFixture(true));
        canvas.setPaintState(GraphPaintState.empty());
        canvas.setViewport(GraphViewport.of(0.0, 0.0, 1.0));
        canvas.setShowArrowheads(false);
        BufferedImage plainLine = paintCanvas(canvas);

        // Cap: the node-end head is 6 world = 6 px long, not 12.5.
        assertThat(measuredHeadLengthPx(oneHead, new Point2D.Double(126.0, 70.0), theme.edgeColor(), 1.0))
            .isCloseTo(6.0, within(1.5));
        // Base follows the capped length: 5.04 px nominal, 4 px measured at the pinned alignment.
        assertThat((double) maximumPerpendicularRunPx(oneHead, theme.edgeColor(), 118, 126, 70))
            .isBetween(3.5, 6.0);
        // Bidirectional split: a = d / 2 = 3 world, so each base is 2.52 px and no run reaches 3.0.
        assertThat((double) maximumPerpendicularRunPx(twoHeads, theme.edgeColor(), 114, 126, 70))
            .isLessThan(3.0);
        // Non-vacuity: the enclosure-side head adds near-edge ink vs. the same edge with arrowheads off.
        assertThat(nearColorPixelsIn(twoHeads, theme.edgeColor(), 120, 66, 123, 74, 80))
            .isGreaterThan(nearColorPixelsIn(plainLine, theme.edgeColor(), 120, 66, 123, 74, 80));
    }
```

Tolerances and rationale: `within(1.5)` for the capped length; the base run bound is `[3.5, 6]` px
inclusive; the two-headed run bound is `< 3.0` px; the ink guard is a strict `>` on
`nearColorPixelsIn(..., 80)`. The near-colour predicate is required for the ink guard: a 3 px head
adds only sub-pixel coverage beyond the line, so the exact edge-colour count is equal in both
renders. Without the two arrow flags propagated, an implementation draws two 6 px heads with a 4 px
run and fails the `< 3.0` bound.

- [ ] **Step 7: Run the canvas paint tests and confirm the red failure set**

```bash
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould" -PTestLoggingFull
```

Expected: `BUILD FAILED` (exit status 1). The only failing tests are the three methods added by
this task; every pre-existing test still passes because the production `paintArrow` still carries
the old `8.0 / zoom` and `3.5 / zoom` literals and ignores the two flags. Confirm the failing set
mechanically:

```bash
python3 - <<'PY'
import xml.etree.ElementTree as ET
path = "freeplane_plugin_graph/build/test-results/test/TEST-org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould.xml"
root = ET.parse(path).getroot()
names = sorted(c.get("name") for c in root.iter("testcase")
               if c.find("failure") is not None or c.find("error") is not None)
for name in names:
    print(name)
PY
```

Expected output, exactly these three lines in this order:

```text
paintedArrowHeadCapBaseAndBidirectionalSplitAtPaintLevel
paintedArrowHeadFloorHoldsAtLowZoom
paintedArrowHeadLengthTracksTheZoomRule
```

No other test may fail. If the set differs, the fixture is wrong: fix the fixture, never the
production code, and re-run before continuing.

- [ ] **Step 8: Rewire both `paintEdges` call sites to pass the two arrow flags**

In `GraphPainter.java`, replace:

```java
            if (showArrowheads && edge.arrowAtFirst()) {
                paintArrow(graphics, first, second, theme.edgeColor(), viewport.zoom());
            }
            if (showArrowheads && edge.arrowAtSecond()) {
                paintArrow(graphics, second, first, theme.edgeColor(), viewport.zoom());
            }
```

with:

```java
            if (showArrowheads && edge.arrowAtFirst()) {
                paintArrow(graphics, first, second, theme.edgeColor(), viewport.zoom(),
                    edge.arrowAtFirst(), edge.arrowAtSecond());
            }
            if (showArrowheads && edge.arrowAtSecond()) {
                paintArrow(graphics, second, first, theme.edgeColor(), viewport.zoom(),
                    edge.arrowAtFirst(), edge.arrowAtSecond());
            }
```

The guards are unchanged; only the two arguments are added.

- [ ] **Step 9: Replace `paintArrow` with the capped implementation**

In `GraphPainter.java`, replace the entire existing method, from
`    private static void paintArrow(final Graphics2D graphics, final LayoutPoint tip, final LayoutPoint from,`
through its closing brace, with:

```java
    private static void paintArrow(final Graphics2D graphics, final LayoutPoint tip, final LayoutPoint from,
            final Color color, final double zoom, final boolean arrowAtFirst, final boolean arrowAtSecond) {
        final double dx = tip.x() - from.x();
        final double dy = tip.y() - from.y();
        final double length = Math.sqrt(dx * dx + dy * dy);
        if (!(length > 0.0) || !Double.isFinite(length)) {
            return;
        }
        final double unitX = dx / length;
        final double unitY = dy / length;
        final double perpendicularX = -unitY;
        final double perpendicularY = unitX;
        final double availableWorldLength =
            availableArrowHeadWorldLength(length, arrowAtFirst, arrowAtSecond);
        final double arrowLengthPx = effectiveArrowHeadLengthPx(zoom, availableWorldLength);
        final double arrowLength = arrowLengthPx / zoom;
        final double arrowWidth = arrowHeadHalfBasePx(arrowLengthPx) / zoom;
        final Path2D.Double arrow = new Path2D.Double();
        arrow.moveTo(tip.x(), tip.y());
        arrow.lineTo(tip.x() - unitX * arrowLength + perpendicularX * arrowWidth,
            tip.y() - unitY * arrowLength + perpendicularY * arrowWidth);
        arrow.lineTo(tip.x() - unitX * arrowLength - perpendicularX * arrowWidth,
            tip.y() - unitY * arrowLength - perpendicularY * arrowWidth);
        arrow.closePath();
        graphics.setColor(color);
        graphics.fill(arrow);
    }
```

The guard runs before sizing, so a degenerate direction never reaches the cap. The tip does not
move: it is exactly the geometry attachment point. The half-base is derived from the capped length,
so the `0.84 : 1` aspect survives capping. Painting stays fill-only and inherits the active
composite.

- [ ] **Step 10: Compile and run the canvas paint tests; confirm the green set**

```bash
gradle :freeplane_plugin_graph:compileJava :freeplane_plugin_graph:compileTestJava
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould" -PTestLoggingFull
```

Expected: both `BUILD SUCCESSFUL`. The three previously failing tests now pass, and every
pre-existing test, including `suppressAndRestoreDirectionalArrowheadsInThePaintedCanvas`, still
passes.

- [ ] **Step 11: Record the measured near-edge ink counts in the item-5 test as a comment**

The ink guard's exact counts must be recorded, not asserted. Temporarily insert this statement in
`paintedArrowHeadCapBaseAndBidirectionalSplitAtPaintLevel` immediately before the final
`// Non-vacuity:` assertion:

```java
        System.out.println("ARROWHEAD_EVIDENCE twoHeads="
            + nearColorPixelsIn(twoHeads, theme.edgeColor(), 120, 66, 123, 74, 80)
            + " plainLine="
            + nearColorPixelsIn(plainLine, theme.edgeColor(), 120, 66, 123, 74, 80));
```

Run only that method and read the counts back from the JUnit XML:

```bash
gradle :freeplane_plugin_graph:test \
  --tests "org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould.paintedArrowHeadCapBaseAndBidirectionalSplitAtPaintLevel" \
  -PTestLoggingFull
grep -o "ARROWHEAD_EVIDENCE twoHeads=[0-9]* plainLine=[0-9]*" \
  freeplane_plugin_graph/build/test-results/test/TEST-org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould.xml
```

Expected: `ARROWHEAD_EVIDENCE twoHeads=6 plainLine=3` (the round-10 measurement on this pinned
fixture). Then remove the `System.out.println` statement and replace it with:

```java
        // Pinned-fixture measurement (recorded, not asserted): twoHeads=6, plainLine=3.
```

If the observed counts differ from 6 and 3, put the observed values in the comment instead and
report the difference in the task report as an observational concern; do not change the assertion.

- [ ] **Step 12: Re-run the class and commit**

```bash
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould" -PTestLoggingFull
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java \
    freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java
git commit -m "Size arrowheads by zoom and cap them to the edge space [2026-10-10-graph-arrowhead-prominence]"
```

Expected: `BUILD SUCCESSFUL` before the commit; the commit contains only the two files above.

## Task 3: Add the dark-theme fill sample and run the module regression gate

**Implementer tier:** Standard
**Lane:** graph-arrowhead-painting
**Depends on:** Task 2

**Files:**

- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java` (insert the test method immediately before `private static GraphTheme lightTheme()`, after Task 2's `paintedArrowHeadCapBaseAndBidirectionalSplitAtPaintLevel`)
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java` (no edit is expected in this task; the file is listed because it is the subject of the regression gate and must remain unchanged except for Tasks 1-2)

**Interfaces:**

- Consumes: `fixture(double firstRadius, double secondRadius)` from Task 2; the item-3 roomy
  fixture geometry (`fixture(14.0, 14.0)`, head length `L = 12.5` px at zoom 1.0, tip screen
  `(151, 70)`); `lightTheme()`-style helpers already present in the class; `GraphTheme.resolve(CanvasTheme.DARK, registeredMaps())`;
  `paint(CanvasState, GraphPaintState, GraphTheme, RenderingLevel)` at zoom 1.0;
  `GraphTheme.edgeColor()`, `GraphTheme.nodeFill()`, `GraphTheme.nodeStroke()`.
- Produces: `@Test public void paintedArrowHeadInteriorUsesTheDarkThemeEdgeColour()`, plus the item-7
  module regression result.

- [ ] **Step 1: Add the spec item 6 dark-theme fill test**

Insert this block immediately before the line `    private static GraphTheme lightTheme() {`:

```java
    @Test
    public void paintedArrowHeadInteriorUsesTheDarkThemeEdgeColour() {
        Fixture fixture = fixture(14.0, 14.0);
        GraphTheme darkTheme = GraphTheme.resolve(CanvasTheme.DARK, registeredMaps());
        BufferedImage image = paint(fixture.state, GraphPaintState.empty(), darkTheme, RenderingLevel.FULL);

        assertThat(image.getRGB(144, 71)).isEqualTo(darkTheme.edgeColor().getRGB());
        assertThat(image.getRGB(144, 71)).isNotEqualTo(darkTheme.nodeFill().getRGB());
        assertThat(image.getRGB(144, 71)).isNotEqualTo(darkTheme.nodeStroke().getRGB());
    }
```

The sample is a fully covered pixel at `+0.1 L = 1.25` px perpendicular to the edge at mid-head:
world `(24.75, 1.25)`, i.e. pixel `(144, 71)`. It sits in the clear band between the `0.7` px
half-width of the line under the head and the `0.21 L = 2.625` px half-width of the head at
`L / 2`, so exact RGB matching holds. Tolerance: none; exact RGB equality is required. A larger
offset (`0.2 L`) is antialiased and a smaller one falls back onto the line, which is edge-coloured
even with no head drawn.

- [ ] **Step 2: Run the whole canvas paint class (spec item 6 passes; spec item 7 class gate)**

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould" -PTestLoggingFull
```

Expected: `BUILD SUCCESSFUL`; the new method passes, and
`suppressAndRestoreDirectionalArrowheadsInThePaintedCanvas` and every other existing
`GraphCanvasPaintShould` test still passes unchanged.

- [ ] **Step 3: Run the module regression gate**

```bash
gradle :freeplane_plugin_graph:test
```

Expected: `BUILD SUCCESSFUL`, exit status 0. This is the spec item 7 gate: the head is the only
changed geometry, so every existing test in the module passes. Record the executed test count in
the task report. If a test outside this plan's two files fails, stop and report it as a concern;
do not edit files outside the `**Files:**` blocks.

- [ ] **Step 4: Commit**

```bash
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java
git commit -m "Pin the dark-theme arrowhead fill and pass the module gate [2026-10-10-graph-arrowhead-prominence]"
```

The production file `GraphPainter.java` already carries the Task 2 commit and must not appear in
this commit.

## Task 4: Capture running-application acceptance evidence

**Implementer tier:** Capable
**Lane:** graph-arrowhead-evidence
**Depends on:** Tasks 1, 2 and 3

**Files:**

- Create: `docs/superpowers/specs/images/2026-10-10-graph-arrowhead-prominence-graph-workspace.png`
- Create: `docs/superpowers/plans/2026-10-10-graph-arrowhead-prominence-evidence.md`
- Transient and not committed: `/tmp/fp-arrowhead-evidence/**` (seed maps, app log, raw captures) and the git-ignored `BIN/` installation refreshed by `graphSmokeInstallation`

**Interfaces:**

- Consumes: the finished production path
  `private static void GraphPainter.paintArrow(Graphics2D graphics, LayoutPoint tip, LayoutPoint from, Color color, double zoom, boolean arrowAtFirst, boolean arrowAtSecond)`
  from Task 2, with the `max(12.5, 8.68 x zoom)` floor from Task 1; the module Gradle task
  `:freeplane_plugin_graph:graphSmokeInstallation` (`freeplane_plugin_graph/build.gradle:140-153`),
  which refreshes the git-ignored `BIN/` launcher tree; the app action `OpenGraphWorkspaceAction`
  registered in the View menu (`freeplane/src/external/resources/xml/mindmapmodemenu.xml:299-300`)
  and reachable as the first View-menu entry; the recent-workspace property
  `graph_workspace_recent_workspaces` (`RecentWorkspaceList.PROPERTY_KEY`); the app's XDG config
  directory layout (`XDG_CONFIG_HOME/freeplane/1.13.x` for app version 1.13.4); the native-connector
  `<arrowlink>` map format (`STARTARROW="NONE"`, `ENDARROW="DEFAULT"`); and the Xvfb / X11 tooling
  (`Xvfb`, `xdotool`, `import`).
- Produces: the two evidence files named above; no production or test source file changes.

The deliverable of this task is the screenshot and its evidence note, not a test. Every command
must run in the lane worktree (the directory printed by `git rev-parse --show-toplevel` from the
task's working directory); do not build or measure in a different checkout, because the evidence
must show this lane's code.

- [ ] **Step 1: Build the launcher from the lane worktree**

```bash
cd "$(git rev-parse --show-toplevel)"
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
gradle :freeplane_plugin_graph:graphSmokeInstallation --console=plain --offline
```

Expected: `BUILD SUCCESSFUL`; `BIN/` is refreshed in the lane worktree. `BIN` is git-ignored; never
stage or commit anything from it. Record the built revision:

```bash
git rev-parse HEAD
```

- [ ] **Step 2: Seed an isolated home, config, map and workspace**

```bash
RUN=/tmp/fp-arrowhead-evidence
rm -rf "$RUN"
mkdir -p "$RUN/home" "$RUN/config/freeplane/1.13.x" "$RUN/maps"
cat > "$RUN/maps/arrowheads.mm" <<'EOF'
<map version="freeplane 1.13.4">
  <node TEXT="arrowhead evidence" ID="ID_ROOT">
    <node TEXT="source" ID="ID_SOURCE">
      <arrowlink DESTINATION="ID_TARGET" STARTARROW="NONE" ENDARROW="DEFAULT" STARTINCLINATION="0;0;" ENDINCLINATION="0;0;" ID="Arrow_ID_arrowhead_evidence"/>
    </node>
    <node TEXT="target" ID="ID_TARGET"/>
  </node>
</map>
EOF
cat > "$RUN/maps/arrowheads.fpg" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<graph-workspace format-version="1" id="00000000-0000-0000-0000-0000000000A1">
    <maps>
        <map id="00000000-0000-0000-0000-0000000000B1" sequence="1" uri="arrowheads.mm" active="true" color="#4E79A7"/>
    </maps>
    <relationships/>
    <pins/>
    <viewport center-x="0.0" center-y="0.0" zoom="1.0"/>
    <display-settings show-arrowheads="true" canvas-theme="LIGHT" remember-viewport="true" dim-unrelated-nodes="false"/>
</graph-workspace>
EOF
cat > "$RUN/config/freeplane/1.13.x/auto.properties" <<EOF
create_new_map_if_no_maps_are_loaded=false
always_load_last_maps=false
load_last_maps=false
load_last_map=false
graph_workspace_recent_workspaces=$RUN/maps/arrowheads.fpg
EOF
```

The map carries one directed native connector (`source` -> `target`, one head at the target); the
workspace seeds that map and the recent-workspaces property so the View-menu action opens it
directly. `load_last_maps=false` and `create_new_map_if_no_maps_are_loaded=false` prevent any other
map from opening first.

- [ ] **Step 3: Start Xvfb and the application on the isolated home**

```bash
RUN=/tmp/fp-arrowhead-evidence
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
Xvfb :98 -screen 0 1600x1000x24 -nolisten tcp > "$RUN/xvfb.log" 2>&1 &
echo "xvfb=$!" > "$RUN/pids.txt"
sleep 1
cd "$(git rev-parse --show-toplevel)/BIN"
DISPLAY=:98 HOME="$RUN/home" XDG_CONFIG_HOME="$RUN/config" \
  JAVA_HOME="$JAVA_HOME" JAVA_OPTS="-Dorg.freeplane.nosplash=true" \
  ./freeplane.sh > "$RUN/app.log" 2>&1 &
echo "app=$!" >> "$RUN/pids.txt"
cat "$RUN/pids.txt"
```

Expected: the application starts under display `:98`; `$RUN/app.log` fills. Never kill a Freeplane
process whose userfpdir is the user's own `/home/henry/.config/freeplane`.

- [ ] **Step 4: Wait for the main window and open the Graph Workspace**

```bash
RUN=/tmp/fp-arrowhead-evidence
for attempt in $(seq 1 30); do
  if xdotool search --name '^Freeplane' >/dev/null 2>&1; then break; fi
  sleep 2
done
MAIN=$(xdotool search --name '^Freeplane' | head -1)
xdotool windowactivate --sync "$MAIN"
xdotool key --window "$MAIN" alt+v
sleep 1
xdotool key --window "$MAIN" Down
sleep 1
xdotool key --window "$MAIN" Return
for attempt in $(seq 1 15); do
  if xdotool search --name '^Graph Workspace$' >/dev/null 2>&1; then break; fi
  sleep 2
done
xdotool search --name '^Graph Workspace$' | head -1
```

Expected: the last command prints one window id. `Open Graph Workspace` is the first entry of the
View menu, and the action opens the seeded most-recent workspace directly. If no `Graph Workspace`
window appears within 30 s, capture the open menu and click the item instead:

```bash
import -window root "$RUN/view-menu.png"
```

Read `$RUN/view-menu.png` with the image reader, measure the integer screen coordinates of the
`Open Graph Workspace` item's centre, then run `xdotool mousemove` at those measured coordinates
followed by `xdotool click 1` and re-check `xdotool search --name '^Graph Workspace$'`.
For example, if the measurement is (86, 75), the command is `xdotool mousemove 86 75 click 1`.

- [ ] **Step 5: Capture the Graph Workspace window**

```bash
RUN=/tmp/fp-arrowhead-evidence
GRAPH=$(xdotool search --name '^Graph Workspace$' | head -1)
xdotool windowactivate --sync "$GRAPH"
sleep 8
import -window "$GRAPH" "$RUN/graph-workspace.png"
identify "$RUN/graph-workspace.png"
```

Expected: a PNG roughly 1280 x 800. Open it with the image reader and confirm: the node rows
`source` and `target` are visible, the edge between them is drawn, and the target end carries a
filled triangle in the edge colour that is clearly longer than the old 8 px head (12.5 px at the
workspace zoom of 1.0). If the arrowhead is not visible, inspect `$RUN/app.log` for the graph
bundle activation and the workspace-open messages; if the workspace did not load, fix the seed
files and repeat Steps 3-5. If the edge is present but the head is not rendered, stop and report
`BLOCKED` with the screenshot path rather than writing an evidence note.

- [ ] **Step 6: Copy the screenshot into the repository and confirm the revision**

```bash
cd "$(git rev-parse --show-toplevel)"
RUN=/tmp/fp-arrowhead-evidence
mkdir -p docs/superpowers/specs/images
cp "$RUN/graph-workspace.png" \
  docs/superpowers/specs/images/2026-10-10-graph-arrowhead-prominence-graph-workspace.png
git rev-parse HEAD
identify docs/superpowers/specs/images/2026-10-10-graph-arrowhead-prominence-graph-workspace.png
```

Expected: the PNG is copied and `git rev-parse HEAD` prints the Task 3 commit.

- [ ] **Step 7: Write the evidence note**

```bash
cd "$(git rev-parse --show-toplevel)"
REV=$(git rev-parse HEAD)
cat > docs/superpowers/plans/2026-10-10-graph-arrowhead-prominence-evidence.md <<EOF
# Graph Workspace Arrowhead Prominence Evidence

## Screenshot

- Deliverable: \`docs/superpowers/specs/images/2026-10-10-graph-arrowhead-prominence-graph-workspace.png\`.
- Shows: the running application's Graph Workspace with the seeded two-node map
  \`arrowhead evidence\` and one directed native connector (\`STARTARROW="NONE"\`,
  \`ENDARROW="DEFAULT"\`); the target-end arrowhead is the filled triangle produced by
  \`L = min(max(12.5, 8.68 x zoom), a x zoom)\` at the workspace zoom of 1.0, which is the
  12.5 px floor and visibly longer than the removed 8 px head.
- Source revision: \`$REV\` in the lane worktree, built with
  \`JAVA_HOME=\$HOME/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:graphSmokeInstallation --console=plain --offline\`.
- Run environment: Xvfb \`1600x1000x24\` on display \`:98\`, isolated \`HOME\` and
  \`XDG_CONFIG_HOME\`, seeded \`graph_workspace_recent_workspaces=/tmp/fp-arrowhead-evidence/maps/arrowheads.fpg\`.
- Capture: \`import -window "$(xdotool search --name 'Graph Workspace' | head -1)"\` from the lane
  worktree after the Graph Workspace window settled.
EOF
cat docs/superpowers/plans/2026-10-10-graph-arrowhead-prominence-evidence.md
```

The seeded workspace file and map under `/tmp/fp-arrowhead-evidence` are transient and are not
committed.

- [ ] **Step 8: Stop only the processes launched by this task and confirm the tree**

```bash
RUN=/tmp/fp-arrowhead-evidence
pkill -f "org.freeplane.userfpdir=$RUN/config/freeplane" 2>/dev/null || true
kill "$(sed -n 's/^app=//p' "$RUN/pids.txt")" 2>/dev/null || true
kill "$(sed -n 's/^xvfb=//p' "$RUN/pids.txt")" 2>/dev/null || true
sleep 2
git status --porcelain
```

Expected: only two untracked files are listed, the PNG and the evidence note; the user's own
Freeplane instance and display are untouched.

- [ ] **Step 9: Commit the evidence**

```bash
git add docs/superpowers/specs/images/2026-10-10-graph-arrowhead-prominence-graph-workspace.png \
    docs/superpowers/plans/2026-10-10-graph-arrowhead-prominence-evidence.md
git commit -m "Record running-application arrowhead acceptance evidence [2026-10-10-graph-arrowhead-prominence]"
```

Expected: the commit contains exactly the two evidence files. This task changes no production or
test source; the evidence files are the deliverable, not a test.
