# Graph Workspace Label / Leader-Line Clearance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for readability; controller state is canonical.

**Goal:** Trim every leader line so it stops `LEADER_CLEARANCE = 3.0` screen px clear of its own
label box, drop leaders shorter than `MIN_VISIBLE_LEADER = 2.0` screen px, and make the painted
leader stroke screen-constant, without changing any placement decision.

**Architecture:** One linear lane, one file-disjoint sequence, two behaviour commits plus one
falsification task. Task 1 adds the `LeaderLine` value type and the `leader()`/`leaderStroke()`
seams that reproduce today's geometry (`end == anchor`), so the pre-existing suite stays green and
the red tests can compile. Task 2 writes the new geometry, painted-ink and stroke tests against
that scaffold, captures the red run, then implements the slab-clipping trim, the minimum visible
leader and the zoom-compensated stroke and flips the lane-0 enclosure pin. Task 3 runs the two
mutation checks (M1 trim, M2 compensation) and the §6 acceptance sweep, leaving raw output under
the module's git-ignored evidence path; it changes no tracked file.

**Tech Stack:** Java 8 language level on runtime Java 21, Gradle plugin project
`freeplane_plugin_graph`, JUnit 4.13.2 with AssertJ 3.27.3, AWT `Graphics2D`/`BufferedImage`
under `java.awt.headless=true`.

**Implementation contract:** `docs/superpowers/specs/2026-09-12-graph-label-leader-clearance-spec.md`
(revision of 2026-09-12; this plan restates every value its tasks need, so a task never has to open
it), derived from the approved design
`docs/superpowers/specs/2026-09-12-graph-label-leader-clearance-design.md` (revision 3).

### Requirement coverage (specification section -> task)

- §1 C1 `LEADER_CLEARANCE = 3.0`: Task 1 (declared at its final value), Task 2 (enforced).
- §1 C2 `MIN_VISIBLE_LEADER = 2.0`: Task 1 (declared at its final value), Task 2 (enforced).
- §1 C3-C6 `SLOT_GAP`/`DISPLACED_OFFSET`/`ARC_GAP`/`EXTERNAL_GAP` unchanged: all tasks (regression gates).
- §1 C7/C8 screen-constant leader stroke: Task 1 (seam returning the uncompensated stroke), Task 2 (compensated), Task 3 (M2).
- §1 C9/C14 fonts and line heights: Task 2 (test fixtures).
- §1 C10 zoom bound 9 and C15 zoom matrix `0.25/1/2/4`: Global Constraints, Task 2.
- §1 C11 `LEADER_CLEARANCE + MIN_VISIBLE_LEADER <= SLOT_GAP`: Task 2 (`keepsEveryLeaderClearOfItsOwnLabelBox` non-vacuity counts).
- §1 C12/C13 painted ink bounds: Task 2 (§4.3), Task 3 (M2 evidence).
- §1 C16 legacy `inkMask` delta: Task 2 (legacy harness left untouched, not reused).
- §2.1 `LeaderLine`: Task 1.
- §2.2 `PlacedLabel.leader()`: Task 1.
- §2.3 constants, two seams, slab algebra, call sites: Task 1 (seams with today's geometry), Task 2 (slab trim and minimum length).
- §2.4 `GraphPainter` end point and stroke: Task 1 (end point, uncompensated seam), Task 2 (compensation).
- §2.5 deleted code: Task 1 (`leaderStart()` accessor and method and every reader).
- §3.1 font metrics: Task 2 (asserted fixture values).
- §3.2 trimmed node-leader lengths and per-zoom counts `7/14/5/3`: Task 2.
- §3.3 enclosure lane 0 absence / lane 1 presence: Task 2 (lane 0 flip, lane 1 fixture), Task 3 (verification).
- §3.4 painted-ink reference values: Task 2 (§4.3 green), Task 3 (M1/M2).
- §4.1 migration of the 45 `leaderStart` hits: Task 1 (32 test reads plus 13 production hits), Task 2 (green-only flip of `:797-800` and the two `assertLeaderClearsBox` insertions).
- §4.2 geometry test: Task 2.
- §4.3 painted-ink test: Task 2, Task 3 (mutation evidence).
- §4.4 stroke unit test: Task 2.
- §4.5 lane-1 fixture and test: Task 2, Task 3 (presence verification).
- §5.1 scaffold commit: Task 1.
- §5.2 red commit: Task 2.
- §5.3 green commit: Task 2.
- §5.4 M1: Task 3.
- §5.5 M2: Task 3.
- §6 V1-V4: Task 1 (V1/V3 partial gates), Task 2 (V1/V2/V3), Task 3 (V1-V4 formal sweep).
- §6 V5-V8: Task 3 (V5/V6) with V7/V8 as the M1/M2 evidence.
- §7 open-point resolutions: embodied in Tasks 1 and 2.

## Global Constraints

- Pinned constant `LEADER_CLEARANCE = 3.0` screen px, declared in `ScreenLabelPlacement` as a package-private `static final double` (spec C1).
- Pinned constant `MIN_VISIBLE_LEADER = 2.0` screen px, declared in the same place (spec C2); a trimmed leader shorter than this is not drawn.
- The clearance and painted-ink guarantee is bounded to `zoom <= 9` (spec C10); every new test uses the zoom matrix `0.25, 1.0, 2.0, 4.0` (spec C15) — do not add a zoom above `9`.
- The leader stroke must be screen-constant: `new BasicStroke((float) (theme.edgeStroke().getLineWidth() / zoom), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)`; `theme.edgeStroke()` is `BasicStroke(1.4f, CAP_ROUND, JOIN_ROUND)`, so the painted screen width is a constant `1.4` px (spec C7/C8). Before this change it is `1.4 * zoom`.
- Ordering that must stay true: `LEADER_CLEARANCE + MIN_VISIBLE_LEADER <= SLOT_GAP` (`3 + 2 <= 6`, spec C11). Unchanged neighbours: `SLOT_GAP = 6.0`, `DISPLACED_OFFSET = 30.0`, `ARC_GAP = 1.0`, `EXTERNAL_GAP = 4.0`.
- Build with Java 21: prefix every Gradle invocation with `JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu`; use the repository `gradle` binary (never `gradlew`, never Maven) from the worktree root; add `-PTestLoggingFull` for failure detail; tests run with `java.awt.headless=true`.
- Every commit message ends with the commit identifier `[2026-09-12-graph-label-leader-clearance]`.
- No file outside the current task's `**Files:**` block may be modified. Do not edit sources or tests outside the block, and do not delete or weaken any existing test except the exact migrations named in the tasks.
- The scaffold task (Task 1) must leave the whole pre-existing `:freeplane_plugin_graph:test` suite green and must not flip the lane-0 enclosure pin. The red run (Task 2) must fail exactly on the named tests and no others. The green commits must not change any placement decision: label anchors, sizes, slots, counts and hull geometry are all unchanged.
- Raw command evidence is written under `freeplane_plugin_graph/build/leader-clearance-evidence/` (already git-ignored by `*/build`). Never `git add` or commit anything from that directory; never run `gradle clean` while evidence is needed.

## Task 1: Scaffold the LeaderLine API over today's leader geometry

**Implementer tier:** Advanced
**Lane:** leader-clearance
**Depends on:** none

**Files:**

- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/LeaderLine.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/PlacedLabel.java` (line 8 import, `:35`, `:42`, `:62`, `:126-128`)
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacement.java` (`:28-32` constants, `:218`, `:267`, `:298-305` external path, `:327`, `:331`, `:337-340` `enclosureLabel`, `:467`, `:512`, `:555-565` replaced by two seams, `:577`, `:588` empties)
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java` (`:239-254` label loop, new seam immediately before `worldX` at `:257`)
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacementShould.java` (the 32 `leaderStart` reads at 260-262, 265-267, 270, 275, 290, 293, 298, 326, 329, 330, 489-490, 492-494, 510, 770, 785, 797-799, 813, 825, 856, 891, 898, 1199, 1202)

**Interfaces:**

- Consumes: `LayoutPoint.of(double x, double y)`, `LayoutPoint.x()`, `LayoutPoint.y()`,
  `LayoutPoint.equals(Object)` (both coordinates compared with `Double.compare`); the `PlacedLabel`
  constructor's parameter order `(ProjectedEndpointKey endpoint, String text, java.awt.Font font,
  PlacedLabel.Mode mode, PlacedLabel.Rung rung, double anchorX, double anchorY, double width,
  double height, boolean truncated, boolean forced, boolean emphaticAtAnchor, boolean forcedAtBaseSlot,
  boolean fullTextSlotWasFree, Optional<LeaderLine> leader, ScreenLabelPlacement.Slot slot)` (only
  the last-but-one type changes); `GraphTheme.edgeStroke()` returning `java.awt.BasicStroke`;
  `GraphViewport.zoom()`; `GraphPainter.worldX(double, GraphViewport, java.awt.Dimension)` and
  `GraphPainter.worldY(double, GraphViewport, java.awt.Dimension)`.
- Produces: `public final class LeaderLine` with `public LayoutPoint start()`, `public LayoutPoint end()`
  and a package-private `LeaderLine(LayoutPoint start, LayoutPoint end)` that rejects nulls, non-finite
  coordinates and equal endpoints; `PlacedLabel.leader()` returning `Optional<LeaderLine>`
  (`leaderStart()` is deleted); `ScreenLabelPlacement.LEADER_CLEARANCE = 3.0` and
  `ScreenLabelPlacement.MIN_VISIBLE_LEADER = 2.0` (package-private `static final double`);
  `private static Optional<LeaderLine> ScreenLabelPlacement.leader(LayoutPoint start, double anchorX,
  double anchorY, double width, double height)` and `private static Optional<LeaderLine>
  ScreenLabelPlacement.nodeLeader(ScreenLabelPlacement.Slot slot, double centerX, double centerY,
  double radius, double anchorX, double anchorY, double width, double height)` — in this task both
  return today's geometry; `static java.awt.BasicStroke GraphPainter.leaderStroke(GraphTheme theme,
  double zoom)` returning `theme.edgeStroke()` (uncompensated).

- [ ] **Step 1: Create `LeaderLine`**

Create `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/LeaderLine.java` with exactly this content:

```java
package org.freeplane.plugin.graph.canvas;

import java.util.Objects;

import org.freeplane.plugin.graph.geometry.LayoutPoint;

public final class LeaderLine {
    private final LayoutPoint start;
    private final LayoutPoint end;

    LeaderLine(final LayoutPoint start, final LayoutPoint end) {
        final LayoutPoint checkedStart = Objects.requireNonNull(start, "start");
        final LayoutPoint checkedEnd = Objects.requireNonNull(end, "end");
        if (!Double.isFinite(checkedStart.x()) || !Double.isFinite(checkedStart.y())
                || !Double.isFinite(checkedEnd.x()) || !Double.isFinite(checkedEnd.y())) {
            throw new IllegalArgumentException("Leader line coordinates must be finite");
        }
        if (checkedStart.equals(checkedEnd)) {
            throw new IllegalArgumentException("Leader line endpoints must differ");
        }
        this.start = checkedStart;
        this.end = checkedEnd;
    }

    public LayoutPoint start() {
        return start;
    }

    public LayoutPoint end() {
        return end;
    }
}
```

The constructor stays package-private on purpose: only `ScreenLabelPlacement` may create lines, so
"start present, end absent" is unrepresentable. Do not add `equals`, `hashCode` or `toString`; no
test compares `LeaderLine` values.

- [ ] **Step 2: Replace `PlacedLabel`'s leader field with `Optional<LeaderLine>`**

Edit `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/PlacedLabel.java` with
these five exact replacements. The field, parameter and accessor are renamed; the parameter keeps its
position, so no call site changes argument order.

Delete the now-unused import (the only user was the old field):

```java
import org.freeplane.plugin.graph.geometry.LayoutPoint;
```

Replace the field declaration:

```java
    private final Optional<LayoutPoint> leaderStart;
```

with:

```java
    private final Optional<LeaderLine> leader;
```

Replace the constructor parameter:

```java
            final boolean fullTextSlotWasFree, final Optional<LayoutPoint> leaderStart,
```

with:

```java
            final boolean fullTextSlotWasFree, final Optional<LeaderLine> leader,
```

Replace the assignment:

```java
        this.leaderStart = Objects.requireNonNull(leaderStart, "leaderStart");
```

with:

```java
        this.leader = Objects.requireNonNull(leader, "leader");
```

Replace the accessor:

```java
    public Optional<LayoutPoint> leaderStart() {
        return leaderStart;
    }
```

with:

```java
    public Optional<LeaderLine> leader() {
        return leader;
    }
```

- [ ] **Step 3: Declare the two constants at their final values**

Edit `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacement.java`.
Replace:

```java
    static final double EXTERNAL_GAP = 4.0;
    static final int SUBTLE_EXTERNAL_CANDIDATE_BUDGET = 8;
```

with:

```java
    static final double EXTERNAL_GAP = 4.0;
    static final double LEADER_CLEARANCE = 3.0;
    static final double MIN_VISIBLE_LEADER = 2.0;
    static final int SUBTLE_EXTERNAL_CANDIDATE_BUDGET = 8;
```

Both are declared at their final values but are unused in this task; that is deliberate and
compiles without warnings. Verify with:

```bash
grep -n "LEADER_CLEARANCE\|MIN_VISIBLE_LEADER" freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacement.java
```

Expected output, exactly two lines:

```text
32:    static final double LEADER_CLEARANCE = 3.0;
33:    static final double MIN_VISIBLE_LEADER = 2.0;
```

- [ ] **Step 4: Replace `leaderStart(...)` with the two seams, still returning today's geometry**

In `ScreenLabelPlacement.java`, replace the entire existing method and its javadoc:

```java
    /**
     * Spec C12: a leader line exists for every slot except ABOVE and BELOW. Its start is the
     * disc-rim point on the centre -> anchor direction, matching the committed generator.
     */
    private static Optional<LayoutPoint> leaderStart(final Slot slot, final double centerX,
            final double centerY, final double radius, final double anchorX, final double anchorY) {
        if (slot == Slot.ABOVE || slot == Slot.BELOW) {
            return Optional.empty();
        }
        final double dx = anchorX - centerX;
        final double dy = anchorY - centerY;
        final double distance = Math.max(1e-6, Math.hypot(dx, dy));
        return Optional.of(LayoutPoint.of(centerX + dx / distance * radius,
            centerY + dy / distance * radius));
    }
```

with:

```java
    /**
     * Core trim: any start (disc rim, hull boundary). End = first entry of [start -> anchor] into
     * inflate(R, LEADER_CLEARANCE), when that entry is strictly between start and the centre and the
     * surviving segment is at least MIN_VISIBLE_LEADER long.
     *
     * Scaffold: returns today's geometry (end == anchor); the trim lands in the clearance commit.
     */
    private static Optional<LeaderLine> leader(final LayoutPoint start, final double anchorX,
            final double anchorY, final double width, final double height) {
        return Optional.of(new LeaderLine(start, LayoutPoint.of(anchorX, anchorY)));
    }

    /** Node labels: computes the rim start, then delegates to the core. */
    private static Optional<LeaderLine> nodeLeader(final Slot slot, final double centerX,
            final double centerY, final double radius, final double anchorX, final double anchorY,
            final double width, final double height) {
        if (slot == Slot.ABOVE || slot == Slot.BELOW) {
            return Optional.empty();
        }
        final double dx = anchorX - centerX;
        final double dy = anchorY - centerY;
        final double distance = Math.max(1e-6, Math.hypot(dx, dy));
        final LayoutPoint start = LayoutPoint.of(centerX + dx / distance * radius,
            centerY + dy / distance * radius);
        return leader(start, anchorX, anchorY, width, height);
    }
```

The rim arithmetic of `nodeLeader` is the deleted method's arithmetic, unchanged, including the
`Math.max(1e-6, hypot)` guard. `leader(...)` must not call `nodeLeader`; the enclosure-external path
below calls the core directly, because its label carries `Slot.ABOVE` and `nodeLeader` returns empty
for that slot.

- [ ] **Step 5: Update the three call sites and the six empty-`Optional` sites**

In `ScreenLabelPlacement.java`, replace the enclosure-external block:

```java
                    final LayoutPoint leaderWorld = hull.nearestBoundaryPoint(
                        worldPoint(context, anchorX, anchorY));
                    final LayoutPoint leader = LayoutPoint.of(context.request.screenX(leaderWorld.x()),
                        context.request.screenY(leaderWorld.y()));
                    context.obstacles.add(candidate);
                    return enclosureLabel(endpoint, text, font, PlacedLabel.Mode.EXTERNAL,
                        PlacedLabel.Rung.FULL_NEAR, anchorX, anchorY, size, forced, false,
                        Optional.of(leader));
```

with:

```java
                    final LayoutPoint leaderWorld = hull.nearestBoundaryPoint(
                        worldPoint(context, anchorX, anchorY));
                    final LayoutPoint start = LayoutPoint.of(context.request.screenX(leaderWorld.x()),
                        context.request.screenY(leaderWorld.y()));
                    context.obstacles.add(candidate);
                    return enclosureLabel(endpoint, text, font, PlacedLabel.Mode.EXTERNAL,
                        PlacedLabel.Rung.FULL_NEAR, anchorX, anchorY, size, forced, false,
                        leader(start, anchorX, anchorY, size.getWidth(), size.getHeight()));
```

Replace the `enclosureLabel` signature and body:

```java
            final boolean emphaticAtAnchor, final Optional<LayoutPoint> leaderStart) {
        return new PlacedLabel(endpoint, text, font, mode, rung, anchorX, anchorY, size.getWidth(),
            size.getHeight(), false, forced, emphaticAtAnchor, false, false, leaderStart, Slot.ABOVE);
```

with:

```java
            final boolean emphaticAtAnchor, final Optional<LeaderLine> leader) {
        return new PlacedLabel(endpoint, text, font, mode, rung, anchorX, anchorY, size.getWidth(),
            size.getHeight(), false, forced, emphaticAtAnchor, false, false, leader, Slot.ABOVE);
```

Replace the retained-placement call site:

```java
            false, false, leaderStart(previous.slot(), centerX, centerY, radius, anchor[0], anchor[1]),
            previous.slot());
```

with:

```java
            false, false, nodeLeader(previous.slot(), centerX, centerY, radius, anchor[0], anchor[1],
                previous.width(), previous.height()),
            previous.slot());
```

Replace the ladder call site:

```java
                    fullTextWasFree, leaderStart(slot, centerX, centerY, radius, anchor[0], anchor[1]), slot);
```

with:

```java
                    fullTextWasFree, nodeLeader(slot, centerX, centerY, radius, anchor[0], anchor[1],
                        size.getWidth(), size.getHeight()), slot);
```

Then replace all six occurrences of the exact string `Optional.<LayoutPoint>empty()` with
`Optional.<LeaderLine>empty()` (currently at lines 218, 267, 327, 331, 577, 588: the interior
enclosure label, the arc enclosure label, both anchor-terminal returns, the base slot and the
hover-only label). Verify:

```bash
grep -c "Optional.<LayoutPoint>empty()" freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacement.java
```

Expected: `0`. And:

```bash
grep -c "Optional.<LeaderLine>empty()" freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacement.java
```

Expected: `6`.

- [ ] **Step 6: Rewrite the `GraphPainter` leader draw and add the stroke seam**

Edit `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java`.
First compute the stroke once before the label loop. Replace:

```java
            final java.awt.Dimension size, final boolean dimUnrelated) {
        for (final PlacedLabel label : labels) {
```

with:

```java
            final java.awt.Dimension size, final boolean dimUnrelated) {
        final BasicStroke leaderStroke = leaderStroke(theme, viewport.zoom());
        for (final PlacedLabel label : labels) {
```

Then replace the draw block:

```java
            if (label.leaderStart().isPresent()) {
                graphics.setStroke(theme.edgeStroke());
                graphics.draw(new Line2D.Double(worldX(label.leaderStart().get().x(), viewport, size),
                    worldY(label.leaderStart().get().y(), viewport, size), anchorX, anchorY));
            }
```

with:

```java
            if (label.leader().isPresent()) {
                final LeaderLine line = label.leader().get();
                graphics.setStroke(leaderStroke);
                graphics.draw(new Line2D.Double(
                    worldX(line.start().x(), viewport, size), worldY(line.start().y(), viewport, size),
                    worldX(line.end().x(), viewport, size), worldY(line.end().y(), viewport, size)));
            }
```

Finally insert the seam immediately before `private static double worldX(...)`:

```java
    static BasicStroke leaderStroke(final GraphTheme theme, final double zoom) {
        return theme.edgeStroke();
    }
```

This is the uncompensated stroke on purpose: the compensation is Task 2, and this task's gate is
that the entire pre-existing suite stays green.

- [ ] **Step 7: Migrate every test read of `leaderStart()` to `leader()`**

In `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacementShould.java`
apply two literal replacements over the whole file, in this order:

1. Replace every occurrence of the exact string `.leaderStart().get()` with `.leader().get().start()`.
2. Replace every remaining occurrence of the exact string `.leaderStart()` with `.leader()`.

Do not touch `leaderCrossings`' old segment endpoint yet — that is the next bullet. After both
replacements there are zero `leaderStart` tokens in the file. Then replace the `leaderCrossings`
segment endpoint so the helper describes the segment that is actually painted:

```java
            segments.add(new double[] { start.x(), start.y(), label.anchorX(), label.anchorY() });
```

with:

```java
            LayoutPoint end = label.leader().get().end();
            segments.add(new double[] { start.x(), start.y(), end.x(), end.y() });
```

Expected migrated pins, unchanged in value: `Theorem`'s `LEFT` start `(499.0, 148.0)` and
`Power Set`'s `RIGHT_FAR` start `(595.0, 182.0)`; the pan/zoom deltas; the rim radius,
collinearity and forward assertions on `leader().get().start()`; the lane-0 enclosure start
`(0, -50)`; and `leaderCrossings == 0`.

**Do not flip `ScreenLabelPlacementShould.java:797` in this task.** That pin still reads
`assertThat(label.leader()).isPresent();` and still passes here because the scaffold's `end` equals
the anchor. Flipping it to `isEmpty()` belongs to Task 2 step 12 (the green step), because only the
trim implemented there turns the lane-0 stub into a 1.0 px segment that `MIN_VISIBLE_LEADER`
drops; flipping it now would break this task's "the whole existing suite stays green" gate.

- [ ] **Step 8: Compile main and test sources**

Run:

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:compileJava :freeplane_plugin_graph:compileTestJava
```

Expected: `BUILD SUCCESSFUL`, exit status 0. If the compile fails, fix the exact edit named in the
compiler error and re-run; do not adjust any test expectation.

- [ ] **Step 9: Run the whole plugin suite and confirm the pre-existing baseline is still green**

Run:

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test
```

Expected: `BUILD SUCCESSFUL`; zero failing tests (the specification's Appendix A.3 baseline is
`BUILD SUCCESSFUL` in about 1 m 11 s; duration varies). Record the executed test count in the task
report. If any test fails, the scaffold is behaviour-changing: fix the scaffold edit, never the test.

- [ ] **Step 10: Run the removal gate**

Run:

```bash
grep -rn leaderStart freeplane_plugin_graph/src
```

Expected: no output (exit status 1). Any match means one of the 45 hits was missed.

- [ ] **Step 11: Commit the scaffold**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/LeaderLine.java \
    freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/PlacedLabel.java \
    freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacement.java \
    freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java \
    freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacementShould.java
git commit -m "Scaffold LeaderLine over today's leader geometry [2026-09-12-graph-label-leader-clearance]"
```

## Task 2: Prove the clearance with red tests, then trim the leaders

**Implementer tier:** Advanced
**Lane:** leader-clearance
**Depends on:** Task 1

**Files:**

- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacementShould.java` (new tests and helpers inserted immediately before `static final class SceneNode {`, currently `:1572`; the two insertions in `assertNodeLeadersAtTheRim` at `:293` and `assertRetainedNodeLeadersAtTheRim` at `:329`; the green flip at `:797-800`)
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java` (`within` import at `:3-4`; new test inserted immediately before `private static GraphTheme lightTheme()`, currently `:948`)
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacement.java` (the scaffold `leader(...)` body that replaced old `:555-565`)
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java` (the `leaderStroke(...)` seam inserted by Task 1 before `worldX`)
- Create: `freeplane_plugin_graph/build/leader-clearance-evidence/task-2-red.txt` (git-ignored build path; never `git add`)

**Interfaces:**

- Consumes: `PlacedLabel.leader()` returning `Optional<LeaderLine>`; `LeaderLine.start()` and
  `LeaderLine.end()` returning `LayoutPoint`; `ScreenLabelPlacement.LEADER_CLEARANCE = 3.0` and
  `ScreenLabelPlacement.MIN_VISIBLE_LEADER = 2.0`; `static java.awt.BasicStroke
  GraphPainter.leaderStroke(GraphTheme theme, double zoom)`; `ScreenLabelPlacement.SCREEN_FRC`; the
  existing test utilities `denseScene()`, `longScene()`, `LONG_NAMES`, `place(List<SceneNode> scene,
  double zoom, Rectangle2D area, Rectangle2D standIn, Set<ProjectedEndpointKey> forced,
  RenderingLevel level, List<PlacedLabel> previous)`, `placeEnclosure(String text, boolean emphatic,
  Rectangle2D area, List<SceneNode> nodes, RenderingLevel level)`, `enclosureRequest(String text,
  boolean emphatic, Rectangle2D area, List<SceneNode> nodes, RenderingLevel level)`,
  `findEnclosure(List<PlacedLabel> placed)`, `area(double width, double height)`,
  `standIn(List<SceneNode> scene, double zoom)`, `forced(String... names)`, `fonts()`, and
  `GraphTheme.resolve(CanvasTheme.LIGHT)`.
- Produces: `static void assertLeaderClearsBox(PlacedLabel label)`; `private static int
  assertLeadersClear(List<SceneNode> scene, double zoom, Rectangle2D standIn, String forcedName)`;
  `@Test public void keepsEveryLeaderClearOfItsOwnLabelBox()`;
  `@Test public void keepsLeaderInkOutOfItsOwnGlyphInk()`;
  `private static void assertPaintedSeparation(GraphTheme theme, List<SceneNode> scene, double zoom,
  Rectangle2D standIn, String forcedName)`; `private static void
  assertEnclosurePaintedSeparation(GraphTheme theme, Rectangle2D area)`; `static boolean[]
  leaderMask(PlacedLabel label, GraphTheme theme, double zoom, int width, int height)`; `static
  boolean[] glyphMask(PlacedLabel label, double zoom, int width, int height)`; `static boolean[]
  inkPixels(java.awt.image.BufferedImage image, int width, int height)`;
  `@Test public void placesEmphaticEnclosureLabelsExternallyInTheSecondLaneWithATrimmedLeader()`;
  `static PlacedLabel secondLaneEnclosureLabel(Rectangle2D area)`; `@Test public void
  keepsTheLeaderStrokeScreenConstant()` in `GraphCanvasPaintShould`; the green `leader(...)` body
  and the compensated `leaderStroke(...)` body.

- [ ] **Step 1: Add the §4.2 geometry test and its two helpers**

In `ScreenLabelPlacementShould.java`, insert this block immediately before the line
`    static final class SceneNode {`:

```java
    @Test
    public void keepsEveryLeaderClearOfItsOwnLabelBox() {
        double[] zooms = { 0.25, 1.0, 2.0, 4.0 };
        int[] expectedLeaders = { 7, 14, 5, 3 };
        for (int index = 0; index < zooms.length; index++) {
            double zoom = zooms[index];
            int leaders = assertLeadersClear(denseScene(), zoom, standIn(denseScene(), zoom),
                "Axiom of Choice");
            leaders += assertLeadersClear(longScene(), zoom, standIn(longScene(), zoom), LONG_NAMES[0]);
            assertThat(leaders).as("leader-carrying labels at zoom " + zoom)
                .isEqualTo(expectedLeaders[index]);
        }

        Rectangle2D area = area(320.0, 320.0);
        List<PlacedLabel> lane0 = placeEnclosure("Basic Definitions and Theorems", true, area,
            Collections.<SceneNode>emptyList(), RenderingLevel.FULL);
        assertThat(lane0).hasSize(1);
        assertThat(lane0.get(0).mode()).isEqualTo(PlacedLabel.Mode.EXTERNAL);
        assertThat(lane0.get(0).anchorY() - area.getHeight() * 0.5).isCloseTo(-64.215072, within(1e-6));
        assertThat(lane0.get(0).leader()).isEmpty();

        assertLeaderClearsBox(secondLaneEnclosureLabel(area));
    }

    private static int assertLeadersClear(List<SceneNode> scene, double zoom, Rectangle2D standIn,
            String forcedName) {
        Rectangle2D area = area(1128.0, 364.0);
        List<PlacedLabel> placed = place(scene, zoom, area, standIn, forced(forcedName),
            RenderingLevel.FULL, null);
        int leaders = 0;
        for (PlacedLabel label : placed) {
            if (label.mode() == PlacedLabel.Mode.HOVER_ONLY || !label.leader().isPresent()) {
                continue;
            }
            assertLeaderClearsBox(label);
            leaders++;
        }
        return leaders;
    }

    static void assertLeaderClearsBox(PlacedLabel label) {
        assertThat(label.leader()).as(label.text()).isPresent();
        LeaderLine line = label.leader().get();
        LayoutPoint start = line.start();
        LayoutPoint end = line.end();
        double anchorX = label.anchorX();
        double anchorY = label.anchorY();
        Rectangle2D box = label.bounds();

        // (b) distance(end, R) >= LEADER_CLEARANCE - 1e-9
        double gapX = Math.max(0.0, Math.max(box.getMinX() - end.x(), end.x() - box.getMaxX()));
        double gapY = Math.max(0.0, Math.max(box.getMinY() - end.y(), end.y() - box.getMaxY()));
        assertThat(Math.hypot(gapX, gapY)).as(label.text() + " end clearance")
            .isGreaterThanOrEqualTo(ScreenLabelPlacement.LEADER_CLEARANCE - 1e-9);

        // (a) end lies on [S, C] strictly between them
        double ux = anchorX - start.x();
        double uy = anchorY - start.y();
        double t = ((end.x() - start.x()) * ux + (end.y() - start.y()) * uy) / (ux * ux + uy * uy);
        assertThat(t).as(label.text() + " entry parameter").isStrictlyBetween(0.0, 1.0);
        assertThat(end.x()).as(label.text() + " collinear x").isCloseTo(start.x() + t * ux, within(1e-9));
        assertThat(end.y()).as(label.text() + " collinear y").isCloseTo(start.y() + t * uy, within(1e-9));

        // (c) [S, end] does not meet inflate(R, LEADER_CLEARANCE - 1e-6)
        double slack = ScreenLabelPlacement.LEADER_CLEARANCE - 1e-6;
        Rectangle2D inner = new Rectangle2D.Double(box.getMinX() - slack, box.getMinY() - slack,
            box.getWidth() + 2.0 * slack, box.getHeight() + 2.0 * slack);
        assertThat(inner.intersectsLine(start.x(), start.y(), end.x(), end.y()))
            .as(label.text() + " inner clearance").isFalse();

        // (d) [S, end] does not meet R
        assertThat(box.intersectsLine(start.x(), start.y(), end.x(), end.y()))
            .as(label.text() + " box intersection").isFalse();

        // (e) |end - S| >= MIN_VISIBLE_LEADER
        assertThat(Math.hypot(end.x() - start.x(), end.y() - start.y()))
            .as(label.text() + " visible leader")
            .isGreaterThanOrEqualTo(ScreenLabelPlacement.MIN_VISIBLE_LEADER);
    }
```

The assertion order inside `assertLeaderClearsBox` is (b), (a), (c), (d), (e) and is pinned: the red
phase must fail on clause (b), not on the `t < 1` clause. The per-zoom leader counts `7 / 14 / 5 / 3`
are the specification's §3.2 sums over the dense and long fixtures; they are the non-vacuity guard
that keeps the zoom-4 mutation evidence meaningful (dense at zoom 4 carries no leader).

- [ ] **Step 2: Add the §4.3 painted-ink test and its four helpers**

Still in `ScreenLabelPlacementShould.java`, insert this block immediately after the block from Step 1
(before `    static final class SceneNode {`):

```java
    @Test
    public void keepsLeaderInkOutOfItsOwnGlyphInk() {
        GraphTheme theme = GraphTheme.resolve(CanvasTheme.LIGHT);
        for (double zoom : new double[] { 0.25, 1.0, 2.0, 4.0 }) {
            assertPaintedSeparation(theme, denseScene(), zoom, standIn(denseScene(), zoom),
                "Axiom of Choice");
            assertPaintedSeparation(theme, longScene(), zoom, standIn(longScene(), zoom), LONG_NAMES[0]);
        }
        assertEnclosurePaintedSeparation(theme, area(320.0, 320.0));
    }

    private static void assertPaintedSeparation(GraphTheme theme, List<SceneNode> scene, double zoom,
            Rectangle2D standIn, String forcedName) {
        Rectangle2D area = area(1128.0, 364.0);
        List<PlacedLabel> placed = place(scene, zoom, area, standIn, forced(forcedName),
            RenderingLevel.FULL, null);
        int width = (int) area.getWidth();
        int height = (int) area.getHeight();
        for (PlacedLabel label : placed) {
            if (label.mode() == PlacedLabel.Mode.HOVER_ONLY || !label.leader().isPresent()) {
                continue;
            }
            boolean[] leaderMask = leaderMask(label, theme, zoom, width, height);
            boolean[] glyphMask = glyphMask(label, zoom, width, height);
            Rectangle2D box = label.bounds();
            int inside = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (leaderMask[y * width + x] && box.contains(x + 0.5, y + 0.5)) {
                        inside++;
                    }
                }
            }
            assertThat(inside).as(label.text() + " leader ink centres inside the box at zoom " + zoom)
                .isZero();
            assertThat(overlaps(leaderMask, glyphMask))
                .as(label.text() + " leader/glyph ink overlap at zoom " + zoom).isFalse();
        }
    }

    private static void assertEnclosurePaintedSeparation(GraphTheme theme, Rectangle2D area) {
        PlacedLabel label = secondLaneEnclosureLabel(area);
        int width = (int) area.getWidth();
        int height = (int) area.getHeight();
        boolean[] leaderMask = leaderMask(label, theme, 1.0, width, height);
        boolean[] glyphMask = glyphMask(label, 1.0, width, height);
        Rectangle2D box = label.bounds();
        int inside = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (leaderMask[y * width + x] && box.contains(x + 0.5, y + 0.5)) {
                    inside++;
                }
            }
        }
        assertThat(inside).as("enclosure lane-1 leader ink centres inside the box").isZero();
        assertThat(overlaps(leaderMask, glyphMask)).as("enclosure lane-1 leader/glyph ink").isFalse();
    }

    static boolean[] leaderMask(PlacedLabel label, GraphTheme theme, double zoom, int width, int height) {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(width, height,
            java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                java.awt.RenderingHints.VALUE_RENDER_SPEED);
            java.awt.geom.AffineTransform world = new java.awt.geom.AffineTransform();
            world.translate(width * 0.5, height * 0.5);
            world.scale(zoom, zoom);
            graphics.transform(world);
            graphics.setColor(java.awt.Color.BLACK);
            graphics.setStroke(GraphPainter.leaderStroke(theme, zoom));
            LeaderLine line = label.leader().get();
            graphics.draw(new java.awt.geom.Line2D.Double(
                (line.start().x() - width * 0.5) / zoom, (line.start().y() - height * 0.5) / zoom,
                (line.end().x() - width * 0.5) / zoom, (line.end().y() - height * 0.5) / zoom));
        }
        finally {
            graphics.dispose();
        }
        return inkPixels(image, width, height);
    }

    static boolean[] glyphMask(PlacedLabel label, double zoom, int width, int height) {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(width, height,
            java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                java.awt.RenderingHints.VALUE_RENDER_SPEED);
            java.awt.geom.AffineTransform world = new java.awt.geom.AffineTransform();
            world.translate(width * 0.5, height * 0.5);
            world.scale(zoom, zoom);
            graphics.transform(world);
            java.awt.Font font = label.font().deriveFont(
                Math.max(1.0f, label.font().getSize2D() / (float) zoom));
            graphics.setFont(font);
            graphics.setColor(java.awt.Color.BLACK);
            Rectangle2D bounds = font.getStringBounds(label.text(), ScreenLabelPlacement.SCREEN_FRC);
            java.awt.font.LineMetrics metrics = font.getLineMetrics(label.text(),
                ScreenLabelPlacement.SCREEN_FRC);
            float baseline = (metrics.getAscent() - metrics.getDescent()) * 0.5f;
            graphics.drawString(label.text(),
                (float) ((label.anchorX() - width * 0.5) / zoom - bounds.getWidth() * 0.5),
                (float) ((label.anchorY() - height * 0.5) / zoom + baseline));
        }
        finally {
            graphics.dispose();
        }
        return inkPixels(image, width, height);
    }

    static boolean[] inkPixels(java.awt.image.BufferedImage image, int width, int height) {
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
```

The mask frame is the placement area with viewport centre `(0, 0)`; the screen-to-world mapping
inside a mask is the production `GraphPainter.worldX/worldY` with `viewport.centerX() =
viewport.centerY() = 0`, so the drawn endpoints are `(screen - size/2) / zoom` under
`translate(size/2) * scale(zoom)`. `leaderMask` uses the production seam
`GraphPainter.leaderStroke(theme, zoom)`, never a mirror. `glyphMask` reproduces the painter's font
derivation and `LineMetrics` centring. The legacy `inkMask` helper is not modified and not reused
(its `FontMetrics` baseline differs by 0.156033 px).

- [ ] **Step 3: Add the §4.5 lane-1 enclosure test and its helper**

Still in `ScreenLabelPlacementShould.java`, insert this block immediately after the block from Step 2
(before `    static final class SceneNode {`):

```java
    @Test
    public void placesEmphaticEnclosureLabelsExternallyInTheSecondLaneWithATrimmedLeader() {
        Rectangle2D area = area(320.0, 320.0);
        PlacedLabel label = secondLaneEnclosureLabel(area);

        assertThat(label.mode()).isEqualTo(PlacedLabel.Mode.EXTERNAL);
        assertThat(label.anchorX() - area.getWidth() * 0.5).isCloseTo(0.0, within(1e-6));
        assertThat(label.anchorY() - area.getHeight() * 0.5).isCloseTo(-88.645215, within(1e-6));
        assertThat(label.width()).isCloseTo(235.291611, within(1e-6));
        assertThat(label.height()).isCloseTo(20.430143, within(1e-6));
        assertThat(label.leader()).isPresent();
        LayoutPoint start = label.leader().get().start();
        LayoutPoint end = label.leader().get().end();
        assertThat(start.x() - area.getWidth() * 0.5).isCloseTo(0.0, within(1e-6));
        assertThat(start.y() - area.getHeight() * 0.5).isCloseTo(-50.0, within(1e-6));
        assertThat(end.x() - area.getWidth() * 0.5).isCloseTo(0.0, within(1e-6));
        assertThat(end.y() - area.getHeight() * 0.5).isCloseTo(-75.430143, within(1e-6));
        assertThat(Math.hypot(end.x() - start.x(), end.y() - start.y()))
            .isCloseTo(25.430143, within(1e-6));
        assertLeaderClearsBox(label);
    }

    static PlacedLabel secondLaneEnclosureLabel(Rectangle2D area) {
        LabelPlacementRequest request = enclosureRequest("Basic Definitions and Theorems", true, area,
            Collections.<SceneNode>emptyList(), RenderingLevel.FULL);
        Rectangle2D seed = new Rectangle2D.Double(100.0, 84.0, 120.0, 24.0);
        List<PlacedLabel> placed = new ScreenLabelPlacement().place(request, null, fonts(),
            Collections.singletonList(seed));
        return findEnclosure(placed);
    }
```

The seed `(100, 84, 120, 24)` rejects the lane-0 external candidate (`y in [85.569856, 106.0]`) and
is clear of lane 1 (`y in [61.139713, 81.569857]`), so the label lands in lane 1 with its box near
face `28.430143` px from the hull edge and its trimmed end `(0, -75.430143)`.

- [ ] **Step 4: Insert the two `assertLeaderClearsBox` calls into the rim helpers**

In `assertNodeLeadersAtTheRim`, replace:

```java
            assertThat(label.leader()).as(slot + " " + label.text()).isPresent();
            SceneNode node = sceneNode(scene, nodeName(label.endpoint()));
```

with:

```java
            assertThat(label.leader()).as(slot + " " + label.text()).isPresent();
            assertLeaderClearsBox(label);
            SceneNode node = sceneNode(scene, nodeName(label.endpoint()));
```

In `assertRetainedNodeLeadersAtTheRim`, replace:

```java
            assertThat(label.leader()).as(slot + " " + label.text()).isPresent();
            LayoutPoint start = label.leader().get().start();
```

with:

```java
            assertThat(label.leader()).as(slot + " " + label.text()).isPresent();
            assertLeaderClearsBox(label);
            LayoutPoint start = label.leader().get().start();
```

These add §4.2 coverage to the pan/zoom-retention fixtures at no extra test cost; their rim,
collinearity and forward pins are unchanged.

- [ ] **Step 5: Add the §4.4 stroke unit test to `GraphCanvasPaintShould`**

Edit `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java`.
Add the static import next to the existing AssertJ imports (after
`import static org.assertj.core.api.Assertions.assertThatThrownBy;`):

```java
import static org.assertj.core.api.Assertions.within;
```

Then insert this test immediately before `    private static GraphTheme lightTheme() {`:

```java
    @Test
    public void keepsTheLeaderStrokeScreenConstant() {
        GraphTheme theme = lightTheme();
        for (double zoom : new double[] { 0.25, 1.0, 2.0, 4.0 }) {
            assertThat((double) GraphPainter.leaderStroke(theme, zoom).getLineWidth() * zoom)
                .as("leader stroke screen width at zoom " + zoom)
                .isCloseTo(theme.edgeStroke().getLineWidth(), within(1e-6));
        }
    }
```

The expected screen width is `1.4` px at every zoom; the corresponding world widths are
`5.6 / 1.4 / 0.7 / 0.35`.

- [ ] **Step 6: Run the red suite against the scaffold and capture the output**

Run (from the worktree root):

```bash
mkdir -p freeplane_plugin_graph/build/leader-clearance-evidence
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test \
  --tests '*ScreenLabelPlacementShould' --tests '*GraphCanvasPaintShould' -PTestLoggingFull \
  > freeplane_plugin_graph/build/leader-clearance-evidence/task-2-red.txt 2>&1
echo "exit=$?"
```

Expected: `exit=1`; the captured file contains `BUILD FAILED`; no production file changed (the only
edits so far in this task are tests). Confirm:

```bash
grep -c "BUILD FAILED" freeplane_plugin_graph/build/leader-clearance-evidence/task-2-red.txt
```

Expected: `1`.

- [ ] **Step 7: Confirm the red failure set is exactly the specified one**

Run:

```bash
python3 - <<'PY'
import xml.etree.ElementTree as ET
for cls in ["ScreenLabelPlacementShould", "GraphCanvasPaintShould"]:
    path = "freeplane_plugin_graph/build/test-results/test/TEST-org.freeplane.plugin.graph.canvas.%s.xml" % cls
    root = ET.parse(path).getroot()
    names = sorted(c.get("name") for c in root.iter("testcase")
                   if c.find("failure") is not None or c.find("error") is not None)
    for name in names:
        print(name)
PY
```

Expected output, exactly these eight lines in this order:

```text
drawsNodeLeadersForEverySlotExceptAboveAndBelow
keepsEveryLeaderClearOfItsOwnLabelBox
keepsLeaderInkOutOfItsOwnGlyphInk
keepsPlacementsStickyAcrossAPan
placesEmphaticEnclosureLabelsExternallyInTheSecondLaneWithATrimmedLeader
recomputesTheLeaderStartOfRetainedLabelsAfterALargePan
recomputesTheLeaderStartOfRetainedLabelsAfterAZoomChange
keepsTheLeaderStrokeScreenConstant
```

No other test may fail. In particular `placesEmphaticEnclosureLabelsExternallyWithALeader` and
`keepsPaintedInkSeparatedAcrossTheZoomMatrix` must still pass (its lane-0 pin flip belongs to the
green step).

- [ ] **Step 8: Confirm the red failure shapes**

Run:

```bash
grep -q "Axiom of Choice end clearance" freeplane_plugin_graph/build/leader-clearance-evidence/task-2-red.txt \
  && echo "L1.2 red confirmed"
grep -q "Axiom of Choice leader ink centres inside the box at zoom 0.25" \
  freeplane_plugin_graph/build/leader-clearance-evidence/task-2-red.txt \
  && echo "L2 red confirmed"
grep -q "leader stroke screen width at zoom 0.25" \
  freeplane_plugin_graph/build/leader-clearance-evidence/task-2-red.txt \
  && echo "C7 red confirmed"
grep -q -- "-75.430143" \
  freeplane_plugin_graph/build/leader-clearance-evidence/task-2-red.txt \
  && echo "lane-1 end pin red confirmed"
```

Expected output, exactly these four lines:

```text
L1.2 red confirmed
L2 red confirmed
C7 red confirmed
lane-1 end pin red confirmed
```

The shapes are the specification's §5.2 list: the scaffold's `end == anchor` gives
`distance(end, R) = 0 < LEADER_CLEARANCE - 1e-9` on dense `Axiom of Choice` at zoom 0.25 (clause
(b)); leader ink sits at the label centre so `inside > 0` (clause (b); the exact count is not
pinned, only `> 0`); the uncompensated stroke gives `1.4 * 0.25 = 0.35` against `1.4`; and the
lane-1 label's `end` is the scaffold anchor `(0, -88.645215)` instead of `(0, -75.430143)`. Record
the observed `inside` counts from the AssertJ messages in the task report.

- [ ] **Step 9: Commit the red tests**

```bash
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacementShould.java \
    freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java
git commit -m "Add the leader-clearance red tests against the scaffold [2026-09-12-graph-label-leader-clearance]"
```

The evidence file stays in the ignored build directory and is never staged.

- [ ] **Step 10: Implement the slab-clipping trim**

In `ScreenLabelPlacement.java`, replace the whole scaffold body (including its scaffold note) with
the final implementation:

```java
    /**
     * Core trim: any start (disc rim, hull boundary). End = first entry of [start -> anchor] into
     * inflate(R, LEADER_CLEARANCE), when that entry is strictly between start and the centre and the
     * surviving segment is at least MIN_VISIBLE_LEADER long.
     *
     * Scaffold: returns today's geometry (end == anchor); the trim lands in the clearance commit.
     */
    private static Optional<LeaderLine> leader(final LayoutPoint start, final double anchorX,
            final double anchorY, final double width, final double height) {
        return Optional.of(new LeaderLine(start, LayoutPoint.of(anchorX, anchorY)));
    }
```

with:

```java
    /**
     * Core trim: any start (disc rim, hull boundary). End = first entry of [start -> anchor] into
     * inflate(R, LEADER_CLEARANCE), when that entry is strictly between start and the centre and the
     * surviving segment is at least MIN_VISIBLE_LEADER long.
     */
    private static Optional<LeaderLine> leader(final LayoutPoint start, final double anchorX,
            final double anchorY, final double width, final double height) {
        final double dx = anchorX - start.x();
        final double dy = anchorY - start.y();
        final double minX = anchorX - width * 0.5 - LEADER_CLEARANCE;
        final double maxX = anchorX + width * 0.5 + LEADER_CLEARANCE;
        final double minY = anchorY - height * 0.5 - LEADER_CLEARANCE;
        final double maxY = anchorY + height * 0.5 + LEADER_CLEARANCE;
        double entry = Double.NEGATIVE_INFINITY;
        if (dx > 0.0) {
            entry = Math.max(entry, (minX - start.x()) / dx);
        }
        else if (dx < 0.0) {
            entry = Math.max(entry, (maxX - start.x()) / dx);
        }
        if (dy > 0.0) {
            entry = Math.max(entry, (minY - start.y()) / dy);
        }
        else if (dy < 0.0) {
            entry = Math.max(entry, (maxY - start.y()) / dy);
        }
        if (!(entry > 0.0) || !(entry < 1.0)) {
            return Optional.empty();
        }
        final LayoutPoint end = LayoutPoint.of(start.x() + entry * dx, start.y() + entry * dy);
        if (Math.hypot(end.x() - start.x(), end.y() - start.y()) < MIN_VISIBLE_LEADER) {
            return Optional.empty();
        }
        return Optional.of(new LeaderLine(start, end));
    }
```

This is the max-of-near-face-crossings slab computation: `t` is `entry`, the four degenerate returns
are (1) no entry (`!(entry > 0.0)`, covering `dx == dy == 0` and `S` on or inside the inflated box),
(2) entry not strictly between `S` and `C` (`!(entry < 1.0)`), (3) surviving length below
`MIN_VISIBLE_LEADER`, computed from the stored `end` and `start`. Use exact comparisons, no epsilon.

- [ ] **Step 11: Compensate the leader stroke**

In `GraphPainter.java`, replace:

```java
    static BasicStroke leaderStroke(final GraphTheme theme, final double zoom) {
        return theme.edgeStroke();
    }
```

with:

```java
    static BasicStroke leaderStroke(final GraphTheme theme, final double zoom) {
        return new BasicStroke((float) (theme.edgeStroke().getLineWidth() / zoom),
            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    }
```

`zoom > 0` is guaranteed by `GraphViewport`, and the painted screen width becomes a constant `1.4`
px. Do not change the font derivation above it.

- [ ] **Step 12: Flip the lane-0 enclosure pin**

In `ScreenLabelPlacementShould.java`, replace the whole lane-0 presence and start-coordinate
assertion (the three physical lines):

```java
        assertThat(label.leader()).isPresent();
        assertThat(label.leader().get().start().x() - area.getWidth() * 0.5).isCloseTo(0.0, within(1e-6));
        assertThat(label.leader().get().start().y() - area.getHeight() * 0.5)
            .isCloseTo(-50.0, within(1e-6));
```

with:

```java
        assertThat(label.leader()).isEmpty();
```

Delete all three lines: `:800` terminates the y-assertion but carries no `leaderStart` token, so
deleting only `:798-799` leaves a dangling terminator and the file does not compile. Keep the
method's placement pins (`mode`, `anchorX`, `anchorY`) and do not rename the method. The start
coordinate `(0, -50)` now lives in the lane-1 test added in Step 3.

- [ ] **Step 13: Run the two specified classes and confirm green**

Run:

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test \
  --tests '*ScreenLabelPlacementShould' --tests '*GraphCanvasPaintShould'
```

Expected: `BUILD SUCCESSFUL`; zero failing tests. The four new tests pass, every migrated pin
(`meanLeader 48.046026`, `maxLeader 77.894615`, `maxLeader 78.656464`, the `assertBounds` maxima,
`leaderCrossings == 0`, the per-zoom counts `7 / 14 / 5 / 3`) still passes, and
`keepsPaintedInkSeparatedAcrossTheZoomMatrix` still passes unchanged. If a migrated pin moves, stop
and report the observed value; do not edit the literal silently.

- [ ] **Step 14: Run the whole plugin suite**

Run:

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test
```

Expected: `BUILD SUCCESSFUL`; zero failing tests, including `GraphWorkspaceModelAcceptanceShould`.
The full-repository `gradle test` sweep belongs to Task 3.

- [ ] **Step 15: Re-run the removal gate**

Run:

```bash
grep -rn leaderStart freeplane_plugin_graph/src
```

Expected: no output (exit status 1).

- [ ] **Step 16: Commit the green implementation**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacement.java \
    freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java \
    freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacementShould.java
git commit -m "Trim leader lines clear of the label box with a screen-constant stroke [2026-09-12-graph-label-leader-clearance]"
```

## Task 3: Falsify the trim and the stroke, then close the acceptance sweep

**Implementer tier:** Standard
**Lane:** leader-clearance
**Depends on:** Task 2

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacement.java` (M1 mutation only: temporarily replace the `leader(...)` body with the scaffold body, restore with `git checkout` before continuing, never commit or stage)
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java` (M2 mutation only: temporarily replace the `leaderStroke(...)` body with the uncompensated body, restore with `git checkout` before continuing, never commit or stage)
- Create: `freeplane_plugin_graph/build/leader-clearance-evidence/task-3-m1.txt` (git-ignored)
- Create: `freeplane_plugin_graph/build/leader-clearance-evidence/task-3-m2.txt` (git-ignored)
- Create: `freeplane_plugin_graph/build/leader-clearance-evidence/task-3-acceptance.txt` (git-ignored)
- Test (read-only verification): `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacementShould.java`, `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java`

**Interfaces:**

- Consumes: the green `private static Optional<LeaderLine> ScreenLabelPlacement.leader(LayoutPoint
  start, double anchorX, double anchorY, double width, double height)` body and the compensated
  `static java.awt.BasicStroke GraphPainter.leaderStroke(GraphTheme theme, double zoom)` body from
  Task 2; `@Test keepsEveryLeaderClearOfItsOwnLabelBox()`, `@Test
  keepsLeaderInkOutOfItsOwnGlyphInk()`, `@Test
  placesEmphaticEnclosureLabelsExternallyInTheSecondLaneWithATrimmedLeader()` and `@Test
  keepsTheLeaderStrokeScreenConstant()` from Task 2; `PlacedLabel.leader()`; the Task 1 seams and
  constants.
- Produces: raw command transcripts `task-3-m1.txt`, `task-3-m2.txt` and `task-3-acceptance.txt`
  under `freeplane_plugin_graph/build/leader-clearance-evidence/`; no tracked file change and no
  commit.

- [ ] **Step 1: Confirm the starting state**

Run:

```bash
git status --porcelain
git log --oneline -1
```

Expected: `git status --porcelain` prints nothing; `git log --oneline -1` prints the Task 2 green
commit whose subject starts `Trim leader lines clear of the label box`. If the tree is dirty, stop
and report; do not start a mutation on a dirty tree.

- [ ] **Step 2: Apply mutation M1 — revert the trim only**

In `ScreenLabelPlacement.java`, replace the green `leader(...)` body:

```java
        final double dx = anchorX - start.x();
        final double dy = anchorY - start.y();
        final double minX = anchorX - width * 0.5 - LEADER_CLEARANCE;
        final double maxX = anchorX + width * 0.5 + LEADER_CLEARANCE;
        final double minY = anchorY - height * 0.5 - LEADER_CLEARANCE;
        final double maxY = anchorY + height * 0.5 + LEADER_CLEARANCE;
        double entry = Double.NEGATIVE_INFINITY;
        if (dx > 0.0) {
            entry = Math.max(entry, (minX - start.x()) / dx);
        }
        else if (dx < 0.0) {
            entry = Math.max(entry, (maxX - start.x()) / dx);
        }
        if (dy > 0.0) {
            entry = Math.max(entry, (minY - start.y()) / dy);
        }
        else if (dy < 0.0) {
            entry = Math.max(entry, (maxY - start.y()) / dy);
        }
        if (!(entry > 0.0) || !(entry < 1.0)) {
            return Optional.empty();
        }
        final LayoutPoint end = LayoutPoint.of(start.x() + entry * dx, start.y() + entry * dy);
        if (Math.hypot(end.x() - start.x(), end.y() - start.y()) < MIN_VISIBLE_LEADER) {
            return Optional.empty();
        }
        return Optional.of(new LeaderLine(start, end));
```

with the scaffold body:

```java
        return Optional.of(new LeaderLine(start, LayoutPoint.of(anchorX, anchorY)));
```

Leave the constants, the compensated `leaderStroke` and the flipped lane-0 pin in place. Verify the
mutation is present and uncommitted:

```bash
git status --porcelain
```

Expected, exactly one line:

```text
 M freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacement.java
```

- [ ] **Step 3: Run the suite under M1 and capture the output**

Run:

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test \
  --tests '*ScreenLabelPlacementShould' --tests '*GraphCanvasPaintShould' -PTestLoggingFull \
  > freeplane_plugin_graph/build/leader-clearance-evidence/task-3-m1.txt 2>&1
echo "exit=$?"
```

Expected: `exit=1`; the file contains `BUILD FAILED`.

- [ ] **Step 4: Confirm the M1 failure set**

Run the same XML listing command as Task 2 step 7:

```bash
python3 - <<'PY'
import xml.etree.ElementTree as ET
for cls in ["ScreenLabelPlacementShould", "GraphCanvasPaintShould"]:
    path = "freeplane_plugin_graph/build/test-results/test/TEST-org.freeplane.plugin.graph.canvas.%s.xml" % cls
    root = ET.parse(path).getroot()
    names = sorted(c.get("name") for c in root.iter("testcase")
                   if c.find("failure") is not None or c.find("error") is not None)
    for name in names:
        print(name)
PY
```

Expected output, exactly these eight lines and nothing from `GraphCanvasPaintShould`:

```text
drawsNodeLeadersForEverySlotExceptAboveAndBelow
keepsEveryLeaderClearOfItsOwnLabelBox
keepsLeaderInkOutOfItsOwnGlyphInk
keepsPlacementsStickyAcrossAPan
placesEmphaticEnclosureLabelsExternallyInTheSecondLaneWithATrimmedLeader
placesEmphaticEnclosureLabelsExternallyWithALeader
recomputesTheLeaderStartOfRetainedLabelsAfterALargePan
recomputesTheLeaderStartOfRetainedLabelsAfterAZoomChange
```

`keepsTheLeaderStrokeScreenConstant` must pass under M1 (the compensation is untouched), and the
flipped lane-0 test must fail because its un-trimmed leader is present again.

- [ ] **Step 5: Confirm the M1 failure shapes**

Run:

```bash
grep -q "Axiom of Choice end clearance" freeplane_plugin_graph/build/leader-clearance-evidence/task-3-m1.txt \
  && echo "M1 L1.2 first red at zoom 0.25 confirmed"
grep -q "leader ink centres inside the box at zoom 0.25" \
  freeplane_plugin_graph/build/leader-clearance-evidence/task-3-m1.txt \
  && echo "M1 L2 first red at zoom 0.25 confirmed"
grep -q "leader stroke screen width" freeplane_plugin_graph/build/leader-clearance-evidence/task-3-m1.txt \
  && echo "UNEXPECTED: stroke test failed under M1" || echo "M1 stroke test passed as expected"
```

Expected output, exactly these three lines:

```text
M1 L1.2 first red at zoom 0.25 confirmed
M1 L2 first red at zoom 0.25 confirmed
M1 stroke test passed as expected
```

A JUnit method stops at its first failing assertion, so one run prints only the zoom-0.25 message for
`keepsEveryLeaderClearOfItsOwnLabelBox` and `keepsLeaderInkOutOfItsOwnGlyphInk`. The specification's
"fails at every zoom (0.25/1/2/4)" is structural, not directly observable here: the reverted body
returns `end == anchor` for every zoom and input, which is what the removed clauses asserted. The
M2 run and the green re-run in Step 6 are the cross-checks.

Record the observed `inside` counts from the AssertJ messages (the mirror in the specification §3.4
measures 28 for dense `Axiom of Choice` at zoom 0.25 and 28-121 per label at zoom 1). Note in the
report whether `Ultrafilter Lemma and Boolean Pr...` at zoom 1/2 was the one label whose trimmed
stroke lands between glyphs; `Axiom Schema of Replacement a...` at zoom 1 still overlaps and is not
exempt.

- [ ] **Step 6: Restore M1 and confirm green**

Run:

```bash
git checkout -- freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacement.java
git status --porcelain
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test \
  --tests '*ScreenLabelPlacementShould' --tests '*GraphCanvasPaintShould'
```

Expected: the first command prints nothing; the suite ends `BUILD SUCCESSFUL`. Do not continue while
any modification is uncommitted.

- [ ] **Step 7: Apply mutation M2 — revert the compensation only**

In `GraphPainter.java`, replace:

```java
    static BasicStroke leaderStroke(final GraphTheme theme, final double zoom) {
        return new BasicStroke((float) (theme.edgeStroke().getLineWidth() / zoom),
            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    }
```

with the uncompensated body:

```java
    static BasicStroke leaderStroke(final GraphTheme theme, final double zoom) {
        return theme.edgeStroke();
    }
```

Leave the trim and `MIN_VISIBLE_LEADER` in place. Verify:

```bash
git status --porcelain
```

Expected, exactly one line:

```text
 M freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java
```

- [ ] **Step 8: Run the suite under M2 and capture the output**

Run:

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test \
  --tests '*ScreenLabelPlacementShould' --tests '*GraphCanvasPaintShould' -PTestLoggingFull \
  > freeplane_plugin_graph/build/leader-clearance-evidence/task-3-m2.txt 2>&1
echo "exit=$?"
```

Expected: `exit=1`; the file contains `BUILD FAILED`.

- [ ] **Step 9: Confirm the M2 failure set and shapes**

Run the XML listing command (same as Task 2 step 7). Expected output, exactly these two lines:

```text
keepsLeaderInkOutOfItsOwnGlyphInk
keepsTheLeaderStrokeScreenConstant
```

Then confirm the shapes:

```bash
grep -q "leader stroke screen width at zoom 0.25" freeplane_plugin_graph/build/leader-clearance-evidence/task-3-m2.txt \
  && echo "M2 C7 first red at zoom 0.25 confirmed"
grep -q "leader ink centres inside the box at zoom 4.0" freeplane_plugin_graph/build/leader-clearance-evidence/task-3-m2.txt \
  && echo "M2 L2 red at zoom 4.0 confirmed"
grep -q "Axiom of Choice end clearance" freeplane_plugin_graph/build/leader-clearance-evidence/task-3-m2.txt \
  && echo "UNEXPECTED: geometry test failed under M2" || echo "M2 geometry test passed as expected"
```

Expected output, exactly these three lines:

```text
M2 C7 first red at zoom 0.25 confirmed
M2 L2 red at zoom 4.0 confirmed
M2 geometry test passed as expected
```

`keepsTheLeaderStrokeScreenConstant` stops at its first failing zoom, `0.25` (`0.35` against `1.4`);
the specification's full failure set `0.25 / 2 / 4` follows from the same uncompensated width
(`1.4 * 2 = 2.8`, `1.4 * 4 = 5.6`; zoom 1 passes trivially). `keepsLeaderInkOutOfItsOwnGlyphInk` fails at zoom 4 only, where the uncompensated
`1.4 * 4 = 5.6` world stroke paints `7.000` px with `3.500` px overhang against the 3 px clearance,
so clause (b) fires for the three long-fixture leaders there (the mirror measures `inside = 3`
each, `9` total); clause (a) does not fire. `keepsEveryLeaderClearOfItsOwnLabelBox` passes because
the geometry is untouched.

- [ ] **Step 10: Restore M2 and confirm green**

Run:

```bash
git checkout -- freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java
git status --porcelain
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test \
  --tests '*ScreenLabelPlacementShould' --tests '*GraphCanvasPaintShould'
```

Expected: the first command prints nothing; the suite ends `BUILD SUCCESSFUL`.

- [ ] **Step 11: Verify the lane-1 enclosure fixture is present and green**

The lane-1 fixture is already added in Task 2 step 3; do not re-add it. Verify:

```bash
grep -n "placesEmphaticEnclosureLabelsExternallyInTheSecondLaneWithATrimmedLeader\|static PlacedLabel secondLaneEnclosureLabel" \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacementShould.java
```

Expected, exactly two lines (line numbers may differ):

```text
    public void placesEmphaticEnclosureLabelsExternallyInTheSecondLaneWithATrimmedLeader() {
    static PlacedLabel secondLaneEnclosureLabel(Rectangle2D area) {
```

Its pinned values (`anchor (0, -88.645215)`, `start (0, -50)`, `end (0, -75.430143)`,
`|end - S| = 25.430143`, lane-0 `leader()` empty, lane-1 `leader()` present and clearance-clean)
were proven by the green run in Steps 6 and 10.

- [ ] **Step 12: Acceptance V1 — compile main and test sources**

Run, appending to the acceptance transcript:

```bash
echo "== V1 compile ==" >> freeplane_plugin_graph/build/leader-clearance-evidence/task-3-acceptance.txt
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:compileJava :freeplane_plugin_graph:compileTestJava \
  >> freeplane_plugin_graph/build/leader-clearance-evidence/task-3-acceptance.txt 2>&1
echo "exit=$?"
```

Expected: `exit=0`; the transcript contains `BUILD SUCCESSFUL`. No new compile warnings from unused
imports or unused constants.

- [ ] **Step 13: Acceptance V2 — the two specified classes**

```bash
echo "== V2 targeted ==" >> freeplane_plugin_graph/build/leader-clearance-evidence/task-3-acceptance.txt
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test \
  --tests '*ScreenLabelPlacementShould' --tests '*GraphCanvasPaintShould' \
  >> freeplane_plugin_graph/build/leader-clearance-evidence/task-3-acceptance.txt 2>&1
echo "exit=$?"
```

Expected: `exit=0`; `BUILD SUCCESSFUL`; the four new methods pass; every migrated pin
(`meanLeader 48.046026`, `maxLeader 77.894615`, `maxLeader 78.656464`, the `assertBounds` maxima,
`leaderCrossings == 0`) still passes; `keepsPaintedInkSeparatedAcrossTheZoomMatrix` still passes
unchanged.

- [ ] **Step 14: Acceptance V3 — the whole plugin module**

```bash
echo "== V3 plugin suite ==" >> freeplane_plugin_graph/build/leader-clearance-evidence/task-3-acceptance.txt
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test \
  >> freeplane_plugin_graph/build/leader-clearance-evidence/task-3-acceptance.txt 2>&1
echo "exit=$?"
```

Expected: `exit=0`; `BUILD SUCCESSFUL`, including `GraphWorkspaceModelAcceptanceShould`; no test
outside the named files changes behaviour.

- [ ] **Step 15: Acceptance V4 — the full repository suite**

```bash
echo "== V4 full repository ==" >> freeplane_plugin_graph/build/leader-clearance-evidence/task-3-acceptance.txt
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle test \
  >> freeplane_plugin_graph/build/leader-clearance-evidence/task-3-acceptance.txt 2>&1
echo "exit=$?"
```

Expected: `exit=0`; `BUILD SUCCESSFUL`. This run is long; do not interrupt it. The specification
did not pre-measure this baseline, so if it fails, capture the failing test names from the
transcript, determine whether any of them touches `leader()`/`leaderStroke()`, and report the
failure as a concern instead of editing files outside this task's Files block.

- [ ] **Step 16: Acceptance V5 and V6 — removal and constants gates**

```bash
echo "== V5 removal gate ==" >> freeplane_plugin_graph/build/leader-clearance-evidence/task-3-acceptance.txt
grep -rn leaderStart freeplane_plugin_graph/src >> freeplane_plugin_graph/build/leader-clearance-evidence/task-3-acceptance.txt 2>&1
echo "v5_exit=$?"
echo "== V6 constants gate ==" >> freeplane_plugin_graph/build/leader-clearance-evidence/task-3-acceptance.txt
grep -rn "LEADER_CLEARANCE\|MIN_VISIBLE_LEADER" freeplane_plugin_graph/src/main \
  >> freeplane_plugin_graph/build/leader-clearance-evidence/task-3-acceptance.txt 2>&1
```

Expected: `v5_exit=1` and no `leaderStart` line in the transcript. The V6 transcript block contains
exactly seven lines, all with the path
`freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacement.java`:
the two declarations (`static final double LEADER_CLEARANCE = 3.0;` and
`static final double MIN_VISIBLE_LEADER = 2.0;`), the four `LEADER_CLEARANCE` uses inside
`leader(...)` (`minX`, `maxX`, `minY`, `maxY`) and the one `MIN_VISIBLE_LEADER` comparison. Confirm:

```bash
grep -c "LEADER_CLEARANCE\|MIN_VISIBLE_LEADER" freeplane_plugin_graph/build/leader-clearance-evidence/task-3-acceptance.txt
grep "LEADER_CLEARANCE\|MIN_VISIBLE_LEADER" freeplane_plugin_graph/build/leader-clearance-evidence/task-3-acceptance.txt | grep -cv "canvas/ScreenLabelPlacement.java"
```

Expected: `7` and `0`.

- [ ] **Step 17: Confirm the repository is untouched and report**

Run:

```bash
git status --porcelain
git log --oneline -3
```

Expected: `git status --porcelain` prints nothing (the evidence files are inside the git-ignored
module `build/` directory); the three most recent subjects are the Task 2 green commit, the Task 2
red commit and the Task 1 scaffold commit, each ending in
`[2026-09-12-graph-label-leader-clearance]`. There is no commit in this task: the deliverable is the
three evidence transcripts plus the verification result. Report the M1/M2 failing sets, the observed
`inside` counts, the V1-V6 outcomes and the acceptance transcript path.
