# Graph Workspace Arrowhead Zoom Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.

**Goal:** Replace the shipped screen-space arrowhead floor `L = min(max(12.5, 8.68 x zoom), a x zoom)` with the
world-space proportional rule `L = min(12.5, a) x zoom`, so a head is a constant `12.5` world units long
(`12.5 x zoom` screen px, `12.5` px at 1x) instead of staying node-sized when the graph is zoomed out.

**Architecture:** The whole production change is confined to
`org.freeplane.plugin.graph.canvas.GraphPainter`: the constant block becomes one new constant
`ARROW_HEAD_WORLD_LENGTH = 12.5` (world units) plus the unchanged `ARROW_HEAD_HALF_BASE_RATIO = 0.42`, and
`arrowHeadLengthPx(zoom)` returns `ARROW_HEAD_WORLD_LENGTH * zoom`. Task 1 builds and freezes the pre-change
launcher binary before any edit, because `BIN/gitinfo.txt` records HEAD only. Task 2 rewrites the affected
`GraphCanvasPaintShould` rule, cap and paint tests, lands the production change, passes the module gate and
commits. Task 3 builds the after binary, captures the reporter's map through seeded workspace copies at the
three recorded viewports with the before/after binary pair, measures the low-zoom heads, and commits the
eight evidence images. There is exactly one rendering path: the floor rule is replaced, not retained.

**Tech Stack:** Java 8 language level on runtime Java 21.0.8-zulu, Gradle multi-project build (module
`freeplane_plugin_graph`), JUnit 4 with AssertJ, AWT `Graphics2D` / `BufferedImage` under
`java.awt.headless=true`; acceptance evidence additionally uses Xvfb, xdotool and ImageMagick 7
(`import`, `compare`, `convert`, `identify`).

**Implementation contract:** `docs/superpowers/specs/2026-10-11-graph-arrowhead-zoom-spec.md` (the
authority implemented here; every value a task needs is restated in the task), derived from the approved
design `docs/superpowers/specs/2026-10-11-graph-arrowhead-zoom-design.md`. The preserved 1x appearance is
the one approved in `docs/superpowers/specs/2026-10-10-graph-arrowhead-prominence-spec.md`.

## Global Constraints

- Java 8 language target; before every Gradle command run `export JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu` and `export PATH="$JAVA_HOME/bin:$PATH"`.
- Use `gradle` (never `gradlew`, never Maven) from the worktree root, with `--console=plain --offline`; add `-PTestLoggingFull` whenever a test run must show verbose failures.
- Sources are UTF-8; indentation is 4 spaces.
- No translation resources are touched (`GraphPainter` carries no translatable strings), so the repository rule `gradle format_translation` does not apply to this change.
- No new dependencies, no settings changes, no XML/schema changes, no display-settings change, no `GraphTheme` or `CanvasTheme` change, and no zoom clamp: `GraphViewport` continues to reject only non-finite or non-positive zoom.
- The production change is confined to `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java`; the test change is confined to `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java`. Do not modify any other tracked file. Task 3 additionally creates exactly the eight evidence PNGs named in its `**Files:**` block and nothing else.
- Keep exactly one rendering path: after Task 2 the identifiers `ARROW_HEAD_MINIMUM_LENGTH_PX` and `ARROW_HEAD_LENGTH_PER_ZOOM` do not occur anywhere under `freeplane_plugin_graph/src/main/java`; they are not renamed, aliased or retained behind a fallback. The new constant is `ARROW_HEAD_WORLD_LENGTH = 12.5` (world units) and `ARROW_HEAD_HALF_BASE_RATIO = 0.42` is unchanged.
- The exact rule is `arrowHeadLengthPx(zoom) = 12.5 x zoom` screen px, `availableArrowHeadWorldLength(d, arrowAtFirst, arrowAtSecond) = arrowAtFirst && arrowAtSecond ? d / 2.0 : d` world units, `effectiveArrowHeadLengthPx(zoom, a) = min(arrowHeadLengthPx(zoom), a x zoom)` screen px, `arrowHeadHalfBasePx(L) = 0.42 x L` screen px; the helpers stay package-private `static` with their names, units and roles.
- The finite-length guard `!(length > 0.0) || !Double.isFinite(length)` stays first in `paintArrow`, before any sizing call; no per-target-node sizing, no arrowhead shape variants, no fade-out, no suppression threshold, no screen-space floor, and no change to `GraphCanvas.showArrowheads` or the light/dark `GraphTheme` colours.
- Every commit message ends with the commit identifier `[2026-10-11-graph-arrowhead-zoom]`.
- Tests run with `java.awt.headless=true` (the module's Gradle test configuration already sets it).
- The before launcher must be built and frozen in Task 1 before any production edit; `BIN/` is git-ignored (`/BIN*/` in `.gitignore`) and `/tmp/pm-arrowhead-zoom/` is outside the repository, so neither may ever be staged or committed.
- Task 3 must run the application with an isolated `HOME` and `XDG_CONFIG_HOME` on an Xvfb display other than the user's `:0`; it must never write to `/home/henry/.config/freeplane`, never signal the user's Freeplane process (`-Dorg.freeplane.userfpdir=/home/henry/.config/freeplane`), and never kill any process it did not start (only the PIDs it recorded and the isolated-config match `org.freeplane.userfpdir=/tmp/pm-arrowhead-zoom/config/freeplane` may be killed).

## Task 1: Freeze the pre-change launcher binary

**Implementer tier:** Standard
**Lane:** graph-arrowhead-zoom
**Depends on:** none

**Files:**

- Read/verify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java` (the shipped rule must still be present; no edit in this task)
- Create (outside version control): `/tmp/pm-arrowhead-zoom/BIN-before/` (a byte copy of the worktree `BIN/` built from the pre-change revision)
- Not modified: no tracked file; do not `git add` or `git commit` anything in this task

**Interfaces:**

- Consumes: the lane worktree at its pre-change base revision. Nothing from any earlier task.
- Produces: `/tmp/pm-arrowhead-zoom/BIN-before/` with `BIN-before/freeplane.sh` and `BIN-before/gitinfo.txt` carrying `git-revision=<BEFORE_REV>`, where `BEFORE_REV` is the pre-change `git rev-parse HEAD`. Produces the recorded `BEFORE_REV` string and the recorded gitinfo line for Task 3.

- [ ] **Step 1: Verify the worktree is pre-change and clean**

```bash
cd "$(git rev-parse --show-toplevel)"
git status --porcelain
git rev-parse HEAD
grep -n "ARROW_HEAD_MINIMUM_LENGTH_PX\|ARROW_HEAD_LENGTH_PER_ZOOM" \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java
grep -n "ARROW_HEAD_WORLD_LENGTH" \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java
```

Expected: `git status --porcelain` prints nothing; `git rev-parse HEAD` prints one revision, to be recorded as `BEFORE_REV`; the first grep prints lines 39, 42 and 160 (declaration, declaration, use); the second grep prints nothing.

If `ARROW_HEAD_WORLD_LENGTH` is already present, or the two shipped constants are absent, the production change has already been applied and the before binary cannot be built: stop and report BLOCKED, quoting the applied-change evidence, rather than building a mislabelled "before" binary.

- [ ] **Step 2: Build the launcher from the pre-change revision**

```bash
export JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"
gradle :freeplane_plugin_graph:graphSmokeInstallation --console=plain --offline
```

Expected: `BUILD SUCCESSFUL`, exit status 0.

- [ ] **Step 3: Verify the built revision and freeze the binary**

```bash
RUN=/tmp/pm-arrowhead-zoom
mkdir -p "$RUN"
git rev-parse HEAD
cat BIN/gitinfo.txt
rm -rf "$RUN/BIN-before"
cp -a BIN "$RUN/BIN-before"
test -x "$RUN/BIN-before/freeplane.sh"
cat "$RUN/BIN-before/gitinfo.txt"
```

Expected: `git rev-parse HEAD` is still `BEFORE_REV`; `BIN/gitinfo.txt` contains `git-revision=<BEFORE_REV>`; `BIN-before/freeplane.sh` exists and is executable; the copied gitinfo is byte-identical to `BIN/gitinfo.txt`. The `rm -rf "$RUN/BIN-before"` before the copy is required: `cp -a BIN "$RUN/BIN-before"` would nest a second `BIN` directory if `$RUN/BIN-before` already existed.

- [ ] **Step 4: Confirm the fallback binary is untouched**

```bash
ls -d "$RUN/BIN" 2>/dev/null && cat "$RUN/BIN/gitinfo.txt"
```

Expected: if `/tmp/pm-arrowhead-zoom/BIN` exists, its gitinfo is `git-revision=d8132e6089a76f4acd9b77fe0580f8967b203da6 ...` (the guest checkout, the spec's fallback for Task 3). Leave it exactly as it is. `$RUN/BIN-before` is the primary pre-change binary; `$RUN/BIN` is only the documented fallback.

- [ ] **Step 5: Record and do not commit**

```bash
git status --porcelain
```

Expected: no output (the `BIN/` tree is git-ignored and `/tmp/pm-arrowhead-zoom/` is outside the repository). There is no tracked-file change to commit in this task, so there is no commit step; record in the task report: `BEFORE_REV`, the full `BIN-before/gitinfo.txt` line, the exact build command, and that no tracked file was modified. The run identifier is `[2026-10-11-graph-arrowhead-zoom]`.

Verification for the reviewer: `grep -o 'git-revision=[0-9a-f]*' /tmp/pm-arrowhead-zoom/BIN-before/gitinfo.txt` equals the `BEFORE_REV` recorded in the task report, and `git -C "$(git rev-parse --show-toplevel)" status --porcelain` is empty.

## Task 2: Replace the arrowhead rule and update the canvas paint tests

**Implementer tier:** Standard
**Lane:** graph-arrowhead-zoom
**Depends on:** Task 1

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java:38-44` (the constant block) and `:158-161` (`arrowHeadLengthPx`)
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java:1019-1028` (rename and rewrite), `:1030-1041` (insert two assertions), `:1072-1099` (rewrite the 3.0 expectations), `:1101-1117` (delete, replaced by `paintedArrowHeadShrinksWhenZoomedOut`), `:1119-1152` (add the `capFixture` overload), `:1154-1182` (append the capped 3.0 paint probe)
- Unchanged and must keep passing: `:1184-1193` `paintedArrowHeadInteriorUsesTheDarkThemeEdgeColour`, `:619` `suppressAndRestoreDirectionalArrowheadsInThePaintedCanvas`, and every other test in the class
- Create (outside version control): the refreshed git-ignored `BIN/` after the commit

The line ranges are the pre-change coordinates; after the first edit in the test file, locate each method by its exact name from the code below, not by raw line number.

**Interfaces:**

- Consumes from the existing production class `org.freeplane.plugin.graph.canvas.GraphPainter` (package-private `final`, same package as the test): `static double arrowHeadLengthPx(double zoom)`, `static double availableArrowHeadWorldLength(double attachmentDistanceWorld, boolean arrowAtFirst, boolean arrowAtSecond)`, `static double effectiveArrowHeadLengthPx(double zoom, double availableWorldLength)`, `static double arrowHeadHalfBasePx(double lengthPx)`; the `private static void paintArrow(Graphics2D graphics, LayoutPoint tip, LayoutPoint from, Color color, double zoom, boolean arrowAtFirst, boolean arrowAtSecond)` signature and body order; the two `paintEdges` call sites at `:144-149`.
- Consumes from the test class: `paint(CanvasState, GraphPaintState, GraphTheme, RenderingLevel)`, `paintAtZoom(CanvasState, GraphPaintState, GraphTheme, double)`, `paintCanvas(GraphCanvas)`, `perpendicularRunPx(BufferedImage, Color, int, int)`, `measuredHeadLengthPx(BufferedImage, Point2D, Color, double)`, `maximumPerpendicularRunPx(BufferedImage, Color, int, int, int)`, `nearColorPixelsIn(BufferedImage, Color, int, int, int, int, int)`, `fixture(double, double)`, `capFixture(boolean)`, `lightTheme()`, `SIZE`.
- Produces: `private static final double GraphPainter.ARROW_HEAD_WORLD_LENGTH = 12.5;` (world units); `private static final double GraphPainter.ARROW_HEAD_HALF_BASE_RATIO = 0.42;` (unchanged); `static double GraphPainter.arrowHeadLengthPx(double zoom)` returning `ARROW_HEAD_WORLD_LENGTH * zoom`; test methods `arrowHeadLengthIsProportionalToZoom`, `arrowHeadRuleCapAndBidirectionalSplitAreSelectedInTheHelpers`, `paintedArrowHeadLengthTracksTheZoomRule`, `paintedArrowHeadShrinksWhenZoomedOut`, `paintedArrowHeadCapBaseAndBidirectionalSplitAtPaintLevel`; `private static CanvasState GraphCanvasPaintShould.capFixture(boolean bidirectional, double targetCenterX)` with the one-arg `capFixture(boolean)` delegating with `14.0`; the refreshed `BIN/` whose `gitinfo.txt` carries `git-revision=<AFTER_REV>` (the commit from this task).

- [ ] **Step 1: Rewrite the rule unit test (spec item 1)**

In `GraphCanvasPaintShould.java`, replace the whole method `arrowHeadLengthIsFlooredUntilTheCrossoverAndGrowsStrictlyAboveIt` (its `@Test` line through its closing brace, `:1019-1028`) with:

```java
    @Test
    public void arrowHeadLengthIsProportionalToZoom() {
        assertThat(GraphPainter.arrowHeadLengthPx(0.6)).isCloseTo(7.5, within(1e-9));
        assertThat(GraphPainter.arrowHeadLengthPx(0.378)).isCloseTo(4.725, within(1e-9));
        assertThat(GraphPainter.arrowHeadLengthPx(1.0)).isCloseTo(12.5, within(1e-9));
        assertThat(GraphPainter.arrowHeadLengthPx(1.44)).isCloseTo(18.0, within(1e-9));
        assertThat(GraphPainter.arrowHeadLengthPx(3.0)).isCloseTo(37.5, within(1e-9));
        assertThat(GraphPainter.arrowHeadLengthPx(0.5))
            .isLessThan(GraphPainter.arrowHeadLengthPx(1.0));
        assertThat(GraphPainter.arrowHeadHalfBasePx(12.5)).isCloseTo(5.25, within(1e-9));
    }
```

The tolerance is `within(1e-9)` on every double. `12.5 at 1.0` and `5.25` half-base pass on both rules and are retained as invariants; `7.5` at 0.6, `4.725` at 0.378 and `18.0` at 1.44 fail on the shipped floor (`12.5`), and `37.5` at 3.0 fails on the shipped growth term (`26.04`).

- [ ] **Step 2: Extend the helper test (spec item 2)**

In `arrowHeadRuleCapAndBidirectionalSplitAreSelectedInTheHelpers`, insert immediately after the existing line `assertThat(GraphPainter.effectiveArrowHeadLengthPx(1.0, 100.0)).isCloseTo(12.5, within(1e-9));`:

```java
        assertThat(GraphPainter.effectiveArrowHeadLengthPx(0.6, 100.0)).isCloseTo(7.5, within(1e-9));
        assertThat(GraphPainter.effectiveArrowHeadLengthPx(3.0, 100.0)).isCloseTo(37.5, within(1e-9));
```

Keep every existing assertion in the method unchanged, including the four `availableArrowHeadWorldLength(6.0, ...)` pins (`6.0 / 3.0 / 6.0 / 6.0`) and `effectiveArrowHeadLengthPx(0.6, 6.0)` which must stay written as the expression `6.0 * 0.6`, never the literal `3.6`. On the shipped code the first new line returns the 12.5 floor (expects 7.5) and the second returns 26.04 (expects 37.5).

- [ ] **Step 3: Rewrite the painted 3.0 expectations (spec item 3)**

Replace the whole method `paintedArrowHeadLengthTracksTheZoomRule` (`:1072-1099`) with:

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
            .isCloseTo(37.5, within(2.0));
        assertThat(Math.max(3.0, 1.4 * 1.0 + 1.0)).isLessThan(0.84 * 12.5);
        assertThat(Math.max(3.0, 1.4 * 3.0 + 1.0)).isLessThan(0.84 * 37.5);
        assertThat((double) perpendicularRunPx(imageOne, theme.edgeColor(),
            (int) Math.round(tipOne.getX()) - 14, (int) Math.round(tipOne.getY())))
            .isLessThan(Math.max(3.0, 1.4 * 1.0 + 1.0));
        assertThat((double) perpendicularRunPx(imageThree, theme.edgeColor(),
            (int) Math.round(tipThree.getX()) - 39, (int) Math.round(tipThree.getY())))
            .isLessThan(Math.max(3.0, 1.4 * 3.0 + 1.0));
    }
```

The fixture is `fixture(14.0, 14.0)`: `d = 62` world, one head, cap unbound at both zooms. The tip is world `(31, 0)`, screen `(151, 70)` at 1.0 and `(213, 70)` at 3.0; the scan threshold is `max(3.0, 1.4 x zoom + 1.0)` = `3.0` at 1.0 and `5.2` at 3.0; the base is `0.84 x L` = `10.5` / `31.5` px. The two run samples are `1.5` px past the new base planes (`tipOne.x - 14`, `tipThree.x - 39`); the removed `tipThree.x - 28` sample would now lie inside the `37.5` px head. On the shipped code the 3.0 measurement is about `26` px and fails `37.5 +/- 2.0`.

- [ ] **Step 4: Replace the floor paint test (spec item 4)**

Delete the whole method `paintedArrowHeadFloorHoldsAtLowZoom` (`:1101-1117`) and add in its place:

```java
    @Test
    public void paintedArrowHeadShrinksWhenZoomedOut() {
        Fixture fixture = fixture(14.0, 14.0);
        GraphTheme theme = lightTheme();
        BufferedImage imageAt06 = paintAtZoom(fixture.state, GraphPaintState.empty(), theme, 0.6);
        BufferedImage imageAt10 = paintAtZoom(fixture.state, GraphPaintState.empty(), theme, 1.0);
        LayoutPoint attachment = fixture.state.geometry().edgeAttachment(fixture.edge.second(),
            fixture.state.geometry().nodes().get(fixture.first.key()).center());
        Point2D tipAt06 = GraphViewport.of(0.0, 0.0, 0.6).toScreen(attachment, SIZE);
        Point2D tipAt10 = GraphViewport.of(0.0, 0.0, 1.0).toScreen(attachment, SIZE);

        double measuredAt06 = measuredHeadLengthPx(imageAt06, tipAt06, theme.edgeColor(), 0.6);
        double measuredAt10 = measuredHeadLengthPx(imageAt10, tipAt10, theme.edgeColor(), 1.0);
        assertThat(measuredAt06).isCloseTo(7.5, within(1.5));
        assertThat(measuredAt10).isCloseTo(12.5, within(1.5));
        assertThat(measuredAt06).isLessThan(measuredAt10);
    }
```

The same roomy fixture is used; the tip is screen `(138.6, 70)` at 0.6 and `(151, 70)` at 1.0. On the shipped code `measuredAt06` is about `12.5` and fails both `isCloseTo(7.5, within(1.5))` and the strict comparison.

- [ ] **Step 5: Add the parameterised `capFixture` overload (spec item 5)**

Replace the whole method `capFixture(boolean)` (`:1119-1152`) with the delegate plus the two-arg builder. Exactly three occurrences of the old literal `14.0` become `targetCenterX`: the `LayoutPoint` passed to `node(...)`, the centre inside `NodeGeometry.of(...)`, and the `positions.put(...)` value. The hull rectangle, anchor and connector descriptor stay as they are.

```java
    private static CanvasState capFixture(final boolean bidirectional) {
        return capFixture(bidirectional, 14.0);
    }

    private static CanvasState capFixture(final boolean bidirectional, final double targetCenterX) {
        ProjectedNode target = node(FIRST_MAP, "cap-target", LayoutPoint.of(targetCenterX, 0.0));
        EnclosureKey enclosureKey = EnclosureKey.of(source(FIRST_MAP, "cap-enclosure"));
        EnclosureHullKey hullKey = EnclosureHullKey.of(Collections.singletonList(enclosureKey));
        ProjectedEndpointKey enclosureEndpoint = ProjectedEndpointKey.ofEnclosure(enclosureKey);
        ProjectedEndpointKey nodeEndpoint = ProjectedEndpointKey.ofNode(target.key());
        ProjectedEnclosure enclosure = ProjectedEnclosure.of(hullKey, Collections.singletonList(enclosureKey),
            Collections.singletonList(SafeNodeLabel.of("cap-enclosure", "cap-enclosure")), "Map",
            Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(),
            Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.SUBTLE);

        Map<ProjectedNodeKey, NodeGeometry> nodes = new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
        nodes.put(target.key(), NodeGeometry.of(LayoutPoint.of(targetCenterX, 0.0), 8.0));
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
        positions.put(target.key(), LayoutPoint.of(targetCenterX, 0.0));
        Map<EnclosureHullKey, LayoutPoint> anchors = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        anchors.put(hullKey, LayoutPoint.of(-25.0, 0.0));
        return CanvasState.of(1L, projection,
            LayoutFrame.of(1L, LayoutPositions.of(positions, anchors), false, 0),
            GraphGeometry.of(nodes, hulls), OperationalStatus.IDLE);
    }
```

- [ ] **Step 6: Append the capped 3.0 paint probe (spec item 5)**

In `paintedArrowHeadCapBaseAndBidirectionalSplitAtPaintLevel`, append this block after the existing `nearColorPixelsIn` non-vacuity assertion and before the method's closing brace. Every pre-existing assertion in the method (the `d = 6` cap, base and bidirectional runs and the recorded `twoHeads=5, plainLine=3` comment) stays exactly as it is.

```java
        // Capped rule at a zoom above the shipped 12.5 / 8.68 ~ 1.4401 crossover:
        // d = 10 world, a = 10 world < 12.5, so L = min(12.5, 10) x 3.0 = 30 px.
        // The shipped rule draws min(max(12.5, 8.68 x 3.0), 10 x 3.0) = 26.04 px.
        BufferedImage cappedAtThree = paintAtZoom(capFixture(false, 18.0), GraphPaintState.empty(), theme, 3.0);
        assertThat(measuredHeadLengthPx(cappedAtThree, new Point2D.Double(150.0, 70.0),
            theme.edgeColor(), 3.0)).isCloseTo(30.0, within(1.5));
```

The `d = 10` probe puts the target node centre at world `(18, 0)`, radius `8.0`, so the tip is world `(10, 0)` = screen `150 = 120 + 10 x 3.0`, and the enclosure boundary stays world `x = 0` = screen `120`; the `30` px head ends exactly on that boundary. The anchor is the geometry attachment point `(150, 70)`, never the first visible pixel: at zoom 3.0 the node ring covers `2.1` px by the tip. On the shipped code the measurement is about `26` px and fails `30.0 +/- 1.5`.

- [ ] **Step 7: Run the class and confirm the exact red failure set**

```bash
export JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould" -PTestLoggingFull
```

Expected: `BUILD FAILED` (exit status 1). The production code is still the shipped rule, so exactly the five rewritten/added methods fail. Confirm the set mechanically:

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

Expected output, exactly these five lines in this order:

```text
arrowHeadLengthIsProportionalToZoom
arrowHeadRuleCapAndBidirectionalSplitAreSelectedInTheHelpers
paintedArrowHeadCapBaseAndBidirectionalSplitAtPaintLevel
paintedArrowHeadLengthTracksTheZoomRule
paintedArrowHeadShrinksWhenZoomedOut
```

No other test may fail. If the set differs, fix the test fixture, never the production code, and re-run before continuing.

- [ ] **Step 8: Replace the constant block in GraphPainter**

In `GraphPainter.java`, replace the whole constant block (the comment at line 38 through the `ARROW_HEAD_HALF_BASE_RATIO` line at 44):

```java
    // Screen px. Floor that keeps a head legible below the growth crossover.
    private static final double ARROW_HEAD_MINIMUM_LENGTH_PX = 12.5;
    // 0.62 x the maximum node radius in world units:
    // GraphGeometryEngine.BASE_RADIUS 8.0 x NodeProminence.MAX_SCALE 1.75 = 14.0 world.
    private static final double ARROW_HEAD_LENGTH_PER_ZOOM = 0.62 * 14.0;
    // Head base is 0.84 x length; the aspect is preserved when the cap shrinks a head.
    private static final double ARROW_HEAD_HALF_BASE_RATIO = 0.42;
```

with exactly:

```java
    // World units. 12.5 / 14.0 ~ 0.8929 of the maximum node radius:
    // GraphGeometryEngine.BASE_RADIUS 8.0 x NodeProminence.MAX_SCALE 1.75 = 14.0 world.
    private static final double ARROW_HEAD_WORLD_LENGTH = 12.5;
    // Head base is 0.84 x length; the aspect is preserved when the cap shrinks a head.
    private static final double ARROW_HEAD_HALF_BASE_RATIO = 0.42;
```

`ARROW_HEAD_MINIMUM_LENGTH_PX` and `ARROW_HEAD_LENGTH_PER_ZOOM` are deleted, not retained behind a fallback. The `12.5` is a literal: `GraphGeometryEngine.BASE_RADIUS` and `NodeProminence.MAX_SCALE` are both `private static final`, so the `12.5 / 14.0` ratio appears in the comment only.

- [ ] **Step 9: Replace the `arrowHeadLengthPx` body**

In `GraphPainter.java`, replace the method at `:158-161`:

```java
    // Screen px.
    static double arrowHeadLengthPx(final double zoom) {
        return Math.max(ARROW_HEAD_MINIMUM_LENGTH_PX, ARROW_HEAD_LENGTH_PER_ZOOM * zoom);
    }
```

with exactly:

```java
    // Screen px.
    static double arrowHeadLengthPx(final double zoom) {
        return ARROW_HEAD_WORLD_LENGTH * zoom;
    }
```

Change nothing else: keep `availableArrowHeadWorldLength`, `effectiveArrowHeadLengthPx` and `arrowHeadHalfBasePx` unchanged; keep the `paintArrow` signature, its body, its finite-length guard and the axis/perpendicular/path construction; keep both `paintEdges` call sites; keep the fill and every other paint method.

- [ ] **Step 10: Compile and confirm the green set**

```bash
gradle :freeplane_plugin_graph:compileJava :freeplane_plugin_graph:compileTestJava
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould" -PTestLoggingFull
```

Expected: `BUILD SUCCESSFUL` for both; the five previously failing methods pass, and every pre-existing test, including `suppressAndRestoreDirectionalArrowheadsInThePaintedCanvas` and `paintedArrowHeadInteriorUsesTheDarkThemeEdgeColour`, still passes.

- [ ] **Step 11: Run the full module regression gate**

```bash
gradle :freeplane_plugin_graph:test --console=plain --offline
```

Expected: `BUILD SUCCESSFUL`, exit status 0. The head is the only changed geometry, so every existing test in the module passes. Record the executed test count in the task report. If any test outside this plan's two files fails, stop and report it as a concern; do not edit files outside the `**Files:**` block.

- [ ] **Step 12: Confirm exactly one rendering path**

```bash
grep -rn "ARROW_HEAD_MINIMUM_LENGTH_PX\|ARROW_HEAD_LENGTH_PER_ZOOM" \
  freeplane_plugin_graph/src/main/java
grep -rn "ARROW_HEAD_WORLD_LENGTH" freeplane_plugin_graph/src/main/java
```

Expected: the first grep prints nothing; the second prints exactly two lines, the declaration in `GraphPainter.java` and its use inside `arrowHeadLengthPx`. No fallback branch, no second constant, no second paint path.

- [ ] **Step 13: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java \
    freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java
git commit -m "Make arrowhead size proportional to zoom [2026-10-11-graph-arrowhead-zoom]"
```

The commit contains exactly the two files above.

- [ ] **Step 14: Build the after binary from the committed revision**

```bash
export JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"
git status --porcelain
git rev-parse HEAD
gradle :freeplane_plugin_graph:graphSmokeInstallation --console=plain --offline
cat BIN/gitinfo.txt
```

Expected: `git status --porcelain` prints nothing (commit first, because `BIN/gitinfo.txt` records HEAD only, with no dirty marker); `git rev-parse HEAD` prints one revision, recorded as `AFTER_REV`; the build is `BUILD SUCCESSFUL`; `BIN/gitinfo.txt` contains `git-revision=<AFTER_REV>`. If the revision and gitinfo disagree, record `git status --porcelain` plus a diff fingerprint alongside the revision in the task report, because the after binary is then not single-variable clean.

## Task 3: Capture the running-application acceptance evidence

**Implementer tier:** Capable
**Lane:** graph-arrowhead-zoom
**Depends on:** Tasks 1 and 2

**Files:**

- Create: `docs/superpowers/specs/images/2026-10-11-graph-arrowhead-zoom-before-01485.png`
- Create: `docs/superpowers/specs/images/2026-10-11-graph-arrowhead-zoom-after-01485.png`
- Create: `docs/superpowers/specs/images/2026-10-11-graph-arrowhead-zoom-before-03781.png`
- Create: `docs/superpowers/specs/images/2026-10-11-graph-arrowhead-zoom-after-03781.png`
- Create: `docs/superpowers/specs/images/2026-10-11-graph-arrowhead-zoom-before-100.png`
- Create: `docs/superpowers/specs/images/2026-10-11-graph-arrowhead-zoom-after-100.png`
- Create: `docs/superpowers/specs/images/2026-10-11-graph-arrowhead-zoom-after-01485-head-8x.png`
- Create: `docs/superpowers/specs/images/2026-10-11-graph-arrowhead-zoom-after-03781-head-8x.png`
- Transient, never committed: `/tmp/pm-arrowhead-zoom/captures/`, `/crops/`, `/evidence/`, `/home/`, `/config/`, `/run-capture.sh`, `/menu-coords.env`, `/app.log`, `/xvfb.log`, `/app.pid`, `/xvfb.pid`, and the git-ignored worktree `BIN/`
- Never written or read as configuration: `/home/henry/.config/freeplane/**`; never signalled: any process carrying `-Dorg.freeplane.userfpdir=/home/henry/.config/freeplane`
- Unchanged: `docs/superpowers/specs/images/2026-10-11-graph-arrowhead-zoom-reported.png` (the committed reporter screenshot)

**Interfaces:**

- Consumes: `/tmp/pm-arrowhead-zoom/BIN-before/` and its `gitinfo.txt` (`BEFORE_REV`) from Task 1; the worktree `BIN/` and its `gitinfo.txt` (`AFTER_REV`) from Task 2; the validated seeds `/tmp/pm-arrowhead-zoom/maps/before-01485.fpg`, `before-03781.fpg`, `before-100.fpg` and the map copies `Axiomatic_Set_Theory.mm`, `Point-set_Topology.mm`, `Real_Anaylsis.mm`; the spec's §9.1 fallback `/tmp/pm-arrowhead-zoom/BIN` with `gitinfo d8132e6089a76f4acd9b77fe0580f8967b203da6`.
- Produces: the eight committed PNGs above, plus the recorded measurements (per-capture applied zoom and node diameter, the 1.0 `compare -metric AE` output, and the low-zoom head lengths and base widths) in the task report.

### Acceptance targets restated from the specification

| Seeded workspace file | centre (world) | zoom | source of the recorded state |
| --- | --- | --- | --- |
| `before-01485.fpg` | `-5428.751107787859, -987.5363258400164` | `0.14846481908261` | live `math.fpg` |
| `before-03781.fpg` | `-920.3025170052431, -422.47416434224544` | `0.37809529736878794` | `math.sync-conflict-20261011-131807-QPOKO56 (Arch-MSSD0's conflicted copy 2026-10-11).fpg` |
| `before-100.fpg` | `-920.3025170052431, -422.47416434224544` | `1.0` | same scene as `before-03781.fpg` at unit zoom |

Each seed carries `<display-settings show-arrowheads="true" canvas-theme="FOLLOW_FREEPLANE" remember-viewport="true" dim-unrelated-nodes="true" map-sidebar-width="335" map-sidebar-hidden="true">` and three `uri="file:///tmp/pm-arrowhead-zoom/maps/<file>.mm"` values. The uncapped-edge criterion for the head measurement is an attachment distance of at least `84.19503069642815` world at `0.14846481908261` (the before rule's `max(12.5 / zoom, 8.68)`) and at least `33.06044821765584` world at `0.37809529736878794`, which also satisfies the after rule's `12.5` world; the same edge is measured in both the before and the after capture. The after head is `12.5 x zoom` px = `1.855810238532625` px at `0.14846481908261` and `4.726191217109849` px at `0.37809529736878794`; the before head is `12.5` px at both low zooms. The 1.0 pair must compare pixel-identical; the low-zoom ratio check is `0.84 +/- 0.08` wherever the measured head length is at least `4` px.

### Open items recorded from the specification

1. **Ratio-check scope at 0.1485.** The literal rule applies `0.84 +/- 0.08` to the `0.3781` crops and to every crop whose measured head length is at least `4` px, which includes the before-01485 head (about `12.5` px) but exempts the after-01485 head (about `1.86` px). This plan follows the literal per-head rule and reports the before-01485 ratio too; if the exemption is read as covering both `0.1485` captures, omit only the before-01485 ratio check and state that reading in the task report.
2. **Ring-covered length.** The specification measures the head "from the tip (excluding the ring-covered `0.7 x zoom` px) to the base plane". This plan reports the full geometry-attachment-point-to-base-plane distance (the same quantity the unit tests measure, since they anchor at the attachment point) and records the `0.7 x zoom` ring-covered correction separately as `0.10392537335782699` px at `0.14846481908261` and `0.2646667081581515` px at `0.37809529736878794`. The spec's numeric targets are the full `12.5 x zoom` values, which both readings satisfy within `+/- 0.5` px at these zooms.
3. **Pre-change revision provenance.** Specification §9.1 names design commits `c6072243` and `028770a98d` as equivalent pre-change bases. This plan instead pins the lane worktree's actual pre-change HEAD (`BEFORE_REV` from Task 1) and records it; the design commits touch only `docs/` and a non-compiled mockup renderer, so the built binary is identical, while the lane revision remains the authoritative single variable. A task report that claims one of the specification's revisions instead of `BEFORE_REV` is wrong.
4. **Measured-edge identity.** The specification leaves the choice of the measured uncapped edge open. This plan records, per low-zoom capture, the fallback identity the specification allows: the tip and base pixel coordinates plus the edge's visible length in the capture, together with the crop geometry.
5. **Fallback before binary.** If `/tmp/pm-arrowhead-zoom/BIN-before` is absent, use `/tmp/pm-arrowhead-zoom/BIN` (guest checkout, `gitinfo d8132e6089a76f4acd9b77fe0580f8967b203da6`) as the before binary per specification §9.1, and state in the report that the 1.0 pair then compares two revisions differing by `MapLeaseManager.java` and its tests, which do not touch the arrowhead paint path.

- [ ] **Step 1: Verify the binary pair and the after revision**

```bash
cd "$(git rev-parse --show-toplevel)"
export JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"
RUN=/tmp/pm-arrowhead-zoom
git status --porcelain
AFTER_REV=$(git rev-parse HEAD)
grep -o 'git-revision=[0-9a-f]*' BIN/gitinfo.txt
cat "$RUN/BIN-before/gitinfo.txt" 2>/dev/null || echo "BIN-before MISSING"
```

Expected: `git status --porcelain` prints nothing; `BIN/gitinfo.txt` contains `git-revision=$AFTER_REV`. If it does not, rebuild and re-record, because a fixer commit may have landed after Task 2:

```bash
gradle :freeplane_plugin_graph:graphSmokeInstallation --console=plain --offline
grep -o 'git-revision=[0-9a-f]*' BIN/gitinfo.txt
```

If `BIN-before` is missing, apply open item 5 and record the fallback; otherwise `BEFORE_REV` is the gitinfo value from `BIN-before`.

- [ ] **Step 2: Prepare the isolated run tree and verify the three validated seeds**

```bash
RUN=/tmp/pm-arrowhead-zoom
mkdir -p "$RUN/captures" "$RUN/crops" "$RUN/evidence" "$RUN/home" \
  "$RUN/config/freeplane/1.12.x" "$RUN/config/freeplane/1.13.x"
ls -l "$RUN/maps/before-01485.fpg" "$RUN/maps/before-03781.fpg" "$RUN/maps/before-100.fpg" \
  "$RUN/maps/Axiomatic_Set_Theory.mm" "$RUN/maps/Point-set_Topology.mm" "$RUN/maps/Real_Anaylsis.mm"
grep -o '<viewport[^>]*>' "$RUN/maps/before-01485.fpg" "$RUN/maps/before-03781.fpg" "$RUN/maps/before-100.fpg"
grep -o 'uri="file:///tmp/pm-arrowhead-zoom/maps/[^"]*"' "$RUN/maps/before-03781.fpg"
grep -o '<display-settings[^>]*>' "$RUN/maps/before-03781.fpg"
```

Expected: all six files exist; the viewports are the three rows of the table (`0.14846481908261`, `0.37809529736878794`, `1.0` with the two centres); the three `uri=` values point under `$RUN/maps`; the display settings carry `show-arrowheads="true"`, `canvas-theme="FOLLOW_FREEPLANE"`, `remember-viewport="true"` and `dim-unrelated-nodes="true"`. If a seed is absent, re-create it from the reporter's workspace family exactly as specification §9.2 describes (copy `math.fpg` or the named sibling, rewrite the three `uri=` values to the `$RUN/maps/<file>.mm` copies, set the `<viewport>` to the table's centre and zoom, keep the display-settings attributes) and record the re-seed; do not invent different values.

- [ ] **Step 3: Start Xvfb on a display separate from the user's**

```bash
RUN=/tmp/pm-arrowhead-zoom
test ! -e /tmp/.X11-unix/X99 || { echo ":99 is in use; use :98 and record the change"; exit 1; }
Xvfb :99 -screen 0 1600x1000x24 -nolisten tcp > "$RUN/xvfb.log" 2>&1 &
echo $! > "$RUN/xvfb.pid"
sleep 1
DISPLAY=:99 xdotool getdisplaygeometry
```

Expected: `1600 1000`. Never start or capture on the user's `:0` (`DISPLAY` in this shell is `:0`; every evidence command must set `DISPLAY=:99` explicitly).

- [ ] **Step 4: Write the measured-menu capture helper**

Save this as `$RUN/run-capture.sh` (it is transient and never committed):

```bash
#!/bin/bash
# usage: run-capture.sh <name> <bin-dir> <seed-fpg> <zoom>
set -u
RUN=/tmp/pm-arrowhead-zoom
name="$1"; bindir="$2"; seed="$3"; zoom="$4"
source "$RUN/menu-coords.env"
cp "$seed" "$RUN/maps/current.fpg"
for v in 1.12.x 1.13.x; do
  printf '%s\n' \
    'always_load_last_maps=false' \
    'create_new_map_if_no_maps_are_loaded=false' \
    "graph_workspace_recent_workspaces=$RUN/maps/current.fpg" \
    'load_last_map=false' \
    'load_last_maps=false' \
    > "$RUN/config/freeplane/$v/auto.properties"
done
cd "$bindir"
echo "=== $name ($bindir) $(date -Is) ===" >> "$RUN/app.log"
DISPLAY=:99 HOME="$RUN/home" XDG_CONFIG_HOME="$RUN/config" \
  JAVA_HOME="$JAVA_HOME" JAVA_OPTS="-Dorg.freeplane.nosplash=true" \
  ./freeplane.sh >> "$RUN/app.log" 2>&1 &
APP_PID=$!
cd - >/dev/null
for attempt in $(seq 1 30); do
  MAIN=$(xdotool search --name '^Freeplane' | head -1)
  [ -z "$MAIN" ] && MAIN=$(xdotool search --onlyvisible --class freeplane | tail -1)
  [ -n "$MAIN" ] && break
  sleep 2
done
[ -n "$MAIN" ] || { import -window root "$RUN/failed-$name-root.png"; echo "$name: no main window"; exit 1; }
xdotool windowactivate --sync "$MAIN"
xdotool mousemove "$VIEW_X" "$VIEW_Y" click 1
sleep 1
xdotool mousemove "$ITEM_X" "$ITEM_Y" click 1
for attempt in $(seq 1 30); do
  GRAPH=$(xdotool search --name 'Graph Workspace' | head -1)
  [ -n "$GRAPH" ] && break
  sleep 2
done
[ -n "$GRAPH" ] || { import -window root "$RUN/failed-$name-root.png"; echo "$name: no graph window"; exit 1; }
xdotool windowactivate --sync "$GRAPH"
sleep 8
stable="not-checked"
for attempt in $(seq 1 6); do
  import -window "$GRAPH" "$RUN/captures/$name-a.png"
  sleep 4
  import -window "$GRAPH" "$RUN/captures/$name-b.png"
  stable=$(compare -metric AE "$RUN/captures/$name-a.png" "$RUN/captures/$name-b.png" null: 2>&1)
  [ "$stable" = "0 (0)" ] && break
  sleep 5
done
cp "$RUN/captures/$name-b.png" "$RUN/captures/$name.png"
kill "$APP_PID" 2>/dev/null || true
pkill -f "org.freeplane.userfpdir=$RUN/config/freeplane" 2>/dev/null || true
sleep 3
echo "$name zoom=$zoom settle=$stable"
grep -o '<viewport[^>]*>' "$RUN/maps/current.fpg"
```

The only kill targets are the PID this script started and the isolated-config match `org.freeplane.userfpdir=$RUN/config/freeplane`; neither can match the user's instance, whose match string is `-Dorg.freeplane.userfpdir=/home/henry/.config/freeplane`.

- [ ] **Step 5: Measure the menu coordinates and capture the first (before-03781) run**

```bash
RUN=/tmp/pm-arrowhead-zoom
export JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"
chmod +x "$RUN/run-capture.sh"
cp "$RUN/maps/before-03781.fpg" "$RUN/maps/current.fpg"
for v in 1.12.x 1.13.x; do
  printf '%s\n' \
    'always_load_last_maps=false' \
    'create_new_map_if_no_maps_are_loaded=false' \
    "graph_workspace_recent_workspaces=$RUN/maps/current.fpg" \
    'load_last_map=false' \
    'load_last_maps=false' \
    > "$RUN/config/freeplane/$v/auto.properties"
done
cd "$RUN/BIN-before"
echo "=== menu-measurement ($RUN/BIN-before) $(date -Is) ===" >> "$RUN/app.log"
DISPLAY=:99 HOME="$RUN/home" XDG_CONFIG_HOME="$RUN/config" \
  JAVA_HOME="$JAVA_HOME" JAVA_OPTS="-Dorg.freeplane.nosplash=true" \
  ./freeplane.sh >> "$RUN/app.log" 2>&1 &
APP_PID=$!
cd - >/dev/null
for attempt in $(seq 1 30); do
  MAIN=$(xdotool search --name '^Freeplane' | head -1)
  [ -z "$MAIN" ] && MAIN=$(xdotool search --onlyvisible --class freeplane | tail -1)
  [ -n "$MAIN" ] && break
  sleep 2
done
xdotool windowactivate --sync "$MAIN"
import -window root "$RUN/view-menu-closed.png"
```

The display has no window manager, so read `$RUN/view-menu-closed.png` with the image reader, measure the centre of the `View` menu label and write the coordinate pair into `$RUN/menu-coords.env` as `VIEW_X=<x>` and `VIEW_Y=<y>`. The reference geometry from the validated seed run on the same `1600x1000` display is `View` at about `(310, 111)`; measured coordinates always win.

```bash
source "$RUN/menu-coords.env"
xdotool mousemove "$VIEW_X" "$VIEW_Y" click 1
sleep 1
import -window root "$RUN/view-menu-open.png"
```

Read `$RUN/view-menu-open.png` with the image reader, confirm the first item reads `Open Graph Workspace`, measure its centre and append `ITEM_X=<x>` and `ITEM_Y=<y>` to `$RUN/menu-coords.env` (reference `(393, 141)`). Then stop this measuring instance without capturing, because the helper performs the click itself:

```bash
kill "$APP_PID" 2>/dev/null || true
pkill -f "org.freeplane.userfpdir=$RUN/config/freeplane" 2>/dev/null || true
sleep 3
source "$RUN/menu-coords.env"
echo "VIEW=($VIEW_X,$VIEW_Y) ITEM=($ITEM_X,$ITEM_Y)"
```

- [ ] **Step 6: Capture the six runs**

```bash
RUN=/tmp/pm-arrowhead-zoom
export JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"
source "$RUN/menu-coords.env"
AFTER_BIN="$(git rev-parse --show-toplevel)/BIN"
"$RUN/run-capture.sh" before-03781 "$RUN/BIN-before" "$RUN/maps/before-03781.fpg" 0.37809529736878794
"$RUN/run-capture.sh" after-03781  "$AFTER_BIN"       "$RUN/maps/before-03781.fpg" 0.37809529736878794
"$RUN/run-capture.sh" before-01485 "$RUN/BIN-before" "$RUN/maps/before-01485.fpg" 0.14846481908261
"$RUN/run-capture.sh" after-01485  "$AFTER_BIN"       "$RUN/maps/before-01485.fpg" 0.14846481908261
"$RUN/run-capture.sh" before-100   "$RUN/BIN-before" "$RUN/maps/before-100.fpg"   1.0
"$RUN/run-capture.sh" after-100    "$AFTER_BIN"       "$RUN/maps/before-100.fpg"   1.0
```

Expected: each run prints its name, zoom and `settle=0 (0)`, followed by the read-back `<viewport ...>` from the freshly rewritten `current.fpg`. The read-back zoom is the actually applied zoom: it should equal the seeded zoom; if it does not, record both and use `12.5 x read-back zoom` as the acceptance target. If a run prints `settle` other than `0 (0)` after six attempts, define the settle check on the canvas rectangle measured in Step 8 instead (a blinking text caret in the toolbar search field is the only expected non-determinism), require `0 (0)` there, and record the reason and the rectangle. If a run prints `no main window` or `no graph window`, read `$RUN/failed-<name>-root.png`, re-measure the menu coordinates, and re-run; never proceed with a missing capture.

- [ ] **Step 7: Confirm each applied zoom with the node diameter**

For each of the six final captures, pick one cleanly visible node disc, crop it at 8x nearest-neighbour magnification, and record the magnified horizontal disc extent along the disc's midline:

```bash
RUN=/tmp/pm-arrowhead-zoom
convert "$RUN/captures/<name>.png" -crop <W>x<H>+<X>+<Y> +repage \
  -filter point -resize 800% "$RUN/crops/<name>-node-8x.png"
convert "$RUN/crops/<name>-node-8x.png" -crop <W>x1+0+<Ymid> +repage txt:- | head -120
```

Record the crop geometry, the magnified disc count, `disc_px = magnified_count / 8.0`, and `disc_px / applied_zoom`; the ratio must fall in `16 ... 28` (node radius `8.0 x scale`, `scale` in `[1.0, 1.75]`). If the applied zoom cannot be read back because the application did not rewrite the workspace file, use the seeded zoom from the table. Record the measurement in the task report with the capture name.

- [ ] **Step 8: Diff the 1.0 pair**

Read the two 1.0 captures with the image reader, measure one canvas-only rectangle that excludes the menu bar, the toolbar, the left `MAPS` sidebar, the right `Display` sidebar and the bottom status bar, and apply the identical crop to both (reference geometry on a `1280x800` window: canvas about `x` 37-1029, `y` 62-779; measure it, do not assume it):

```bash
RUN=/tmp/pm-arrowhead-zoom
convert "$RUN/captures/before-100.png" -crop <W>x<H>+<X>+<Y> +repage "$RUN/crops/before-100-canvas.png"
convert "$RUN/captures/after-100.png" -crop <W>x<H>+<X>+<Y> +repage "$RUN/crops/after-100-canvas.png"
compare -metric AE "$RUN/crops/before-100-canvas.png" "$RUN/crops/after-100-canvas.png" null: 2>&1
```

Expected output exactly `0 (0)` (ImageMagick 7.1.2-32 writes the metric to stderr, hence `2>&1`; ImageMagick 6 prints `0`). Record the crop geometry and the exact output. If it is nonzero, re-capture both runs after the settled state (the layouts must be identical, not merely similar); a persistent nonzero is recorded as a finding, not smoothed over.

- [ ] **Step 9: Locate and measure the four low-zoom heads**

For each of `before-01485`, `after-01485`, `before-03781`, `after-03781`, choose one visible directional head on an edge that is uncapped under both rules (attachment distance at least `84.19503069642815` world at `0.14846481908261`, at least `33.06044821765584` world at `0.37809529736878794`). Since the layout distance is not exposed, record the edge's fallback identity: its tip and base pixel coordinates in the capture and its visible length. Crop the smallest rectangle containing the head plus 8 px of line on each side and magnify 8x with a point filter:

```bash
RUN=/tmp/pm-arrowhead-zoom
convert "$RUN/captures/after-03781.png" -crop <W>x<H>+<X>+<Y> +repage -filter point -resize 800% \
  "$RUN/crops/after-03781-head-8x.png"
```

In each 8x crop, measure in magnified px (a) the head length along the edge axis from the tip to the base plane, and (b) the base width perpendicular to the axis at the base plane; divide both by `8` and record them with the tip/base coordinates in original-image px and the crop geometry. Use `convert "$RUN/crops/<name>-head-8x.png" -crop <W>x1+0+<Ybase> +repage txt:-` (and the same for one column along the axis) to make the counts auditable. Acceptance:

- after-01485 measures `1.855810238532625` px within `+/- 0.5` px (rounded `1.86`); after-03781 measures `4.726191217109849` px within `+/- 0.5` px (rounded `4.73`); if the applied zoom differs from the seeded value, the target is `12.5 x applied_zoom`.
- before-01485 and before-03781 each measure `12.5` px within `+/- 0.5` px.
- at each zoom the after measurement is strictly smaller than the before measurement.
- the measured base : length ratio is `0.84 +/- 0.08` for both `0.3781` crops and for the before-01485 crop (measured length about `12.5` px, at least `4` px); the after-01485 head is below `4` px and is exempt from the ratio check: record its counts and assert only that it is present and strictly shorter than the before head (see open item 1).

- [ ] **Step 10: Stage the evidence images**

```bash
RUN=/tmp/pm-arrowhead-zoom
cd "$(git rev-parse --show-toplevel)"
EVID=freeplane_plugin_graph/build/evidence/2026-10-11-graph-arrowhead-zoom
mkdir -p "$EVID" docs/superpowers/specs/images
for name in before-01485 after-01485 before-03781 after-03781 before-100 after-100; do
  cp "$RUN/captures/$name.png" "$EVID/2026-10-11-graph-arrowhead-zoom-$name.png"
  cp "$EVID/2026-10-11-graph-arrowhead-zoom-$name.png" "docs/superpowers/specs/images/2026-10-11-graph-arrowhead-zoom-$name.png"
done
cp "$RUN/crops/after-01485-head-8x.png" docs/superpowers/specs/images/2026-10-11-graph-arrowhead-zoom-after-01485-head-8x.png
cp "$RUN/crops/after-03781-head-8x.png" docs/superpowers/specs/images/2026-10-11-graph-arrowhead-zoom-after-03781-head-8x.png
ls -l docs/superpowers/specs/images/2026-10-11-graph-arrowhead-zoom-*
```

Expected: the six overviews and the two magnified after heads are present, and `2026-10-11-graph-arrowhead-zoom-reported.png` is untouched. `freeplane_plugin_graph/build/` is git-ignored through `*/build` in `.gitignore`.

- [ ] **Step 11: Commit the eight images**

```bash
cd "$(git rev-parse --show-toplevel)"
git status --porcelain
git add docs/superpowers/specs/images/2026-10-11-graph-arrowhead-zoom-before-01485.png \
        docs/superpowers/specs/images/2026-10-11-graph-arrowhead-zoom-after-01485.png \
        docs/superpowers/specs/images/2026-10-11-graph-arrowhead-zoom-before-03781.png \
        docs/superpowers/specs/images/2026-10-11-graph-arrowhead-zoom-after-03781.png \
        docs/superpowers/specs/images/2026-10-11-graph-arrowhead-zoom-before-100.png \
        docs/superpowers/specs/images/2026-10-11-graph-arrowhead-zoom-after-100.png \
        docs/superpowers/specs/images/2026-10-11-graph-arrowhead-zoom-after-01485-head-8x.png \
        docs/superpowers/specs/images/2026-10-11-graph-arrowhead-zoom-after-03781-head-8x.png
git commit -m "Record running-application arrowhead zoom acceptance evidence [2026-10-11-graph-arrowhead-zoom]"
```

`git status --porcelain` before the add must list exactly those eight files (plus nothing else under `docs/superpowers/specs/images/`). The commit contains exactly those eight image files; no production or test source changes.

- [ ] **Step 12: Stop the isolated processes and verify the user environment**

```bash
RUN=/tmp/pm-arrowhead-zoom
kill "$(cat "$RUN/xvfb.pid")" 2>/dev/null || true
sleep 1
pgrep -af "org.freeplane.userfpdir=/home/henry/.config/freeplane"
pgrep -af "Xvfb :99" || echo "isolated Xvfb stopped"
cd "$(git rev-parse --show-toplevel)"
git status --porcelain
```

Expected: the user's own Freeplane process is still listed and was never signalled; the isolated `Xvfb :99` is not listed; `git status --porcelain` prints nothing. No command in this task wrote under `/home/henry/.config/freeplane` or used the user's display.

- [ ] **Step 13: Record the completion report**

In the task report, record exactly: the integration revision (`git rev-parse HEAD`) and `BIN/gitinfo.txt` of the after build; the `BEFORE_REV` and which before binary was used (`BIN-before` or the §9.1 fallback); the build command of specification §9.1; the six seeded viewports and their `.fpg` files; the applied zoom read back per capture; the measured node diameter and its `disc_px / zoom` ratio per capture; the 1.0 crop geometry and the exact `compare -metric AE` command and output; and the measured head lengths and base widths of the four low-zoom heads with their tip/base pixel coordinates and magnified counts.

Verification for the reviewer: `git show --stat HEAD` lists exactly the eight committed PNGs; `git rev-parse HEAD` matches the `BIN/gitinfo.txt` recorded in the report; every acceptance number in the report is one of the values in Step 9 or a recorded measured count; `git diff --stat` between the Task 2 commit and this one shows no source or test change.
