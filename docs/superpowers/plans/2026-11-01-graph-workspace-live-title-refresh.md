# Graph Workspace Live Title Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax. Leave the saved plan unticked; the controller owns progress.

**Goal:** Refresh an already open graph canvas's retained node titles from current projection display text while preserving the approved KEEP, RE-DERIVE and DECLINE behavior.

**Architecture:** Carry the complete projection display source in each immutable `PlacedLabel`. Classify retention using source equality, the current face font and eligibility; either remeasure the remembered face, derive the first fitting face at its remembered slot from the existing `LADDER`, or decline into the unchanged placement ladder. Test placement, cache generation isolation and two paints of the same real canvas without changing cache, canvas, painter, projection or layout behavior.

**Tech Stack:** Java 8-compatible Java, Swing/Java2D, JUnit 4, AssertJ, repository-pinned Noto Sans fixture fonts; installed Gradle run with SDKMAN Java 21.0.8-zulu.

**Execution owner:** PM run `pm-run-20261101-064156-4fca4349`. One lane, `live-title-refresh`; strictly sequential dependencies `1 -> 2 -> 3 -> 4 -> 5`. The controller preserves PM ownership and returns evidence to that PM; this plan does not initialize a standalone run or finish a branch.

**Authorities:** Approved design `docs/superpowers/specs/2026-11-01-graph-workspace-live-title-refresh-design.md`, commit `42cb992ceb1810bc662a81dd34e281dfbd92bf41`, SHA-256 `9f19e43599129b5d44c01b984ed65e77548008c3f2de5a12c27ad112a0176c91`; approved specification `docs/superpowers/specs/2026-11-01-graph-workspace-live-title-refresh-spec.md`, commit `3bd99a7b96d8e80f5e58eade9c4496ccb70afbbe`, 64,938 bytes. Application baseline is `8267175e52f49216ae6222bc002e48020690d67c` on `plugin/graph-workspace`. Source ranges below refer to this inspected baseline and are anchors, not permission to replace surrounding code.

**Authoring boundary:** This planning task creates only this file. All Java edits, Gradle commands and commit commands below belong to the subsequent implementation phase. The planner does not run them.

### File structure and responsibilities

- Modify `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/PlacedLabel.java:20-64,102-104`: immutable package-private source field, mandatory constructor argument and accessor.
- Modify `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacement.java:337-343,451-473,513-517,607-629`: five constructor sites and the retention method; all other placement algorithms remain unchanged.
- Create `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/ScreenLabelLiveTitleRefreshShould.java`: all 31 declared T1-T7 placement/cache tests and private stable-ID fixtures.
- Modify `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java:1909-1927,1971-1987,2668-2668`: two existing constructor calls, two new T8 tests, and local fixture helpers inserted immediately before the final class brace. Preserve every existing assertion/helper behavior.

### Self-review and coverage ledger

The specification was read in full before decomposition. Constructor/metadata contracts (sections 2-3) are Task 1; section 4 and all transitions/ordering in section 5 are Task 2; section 6 fixtures and T1, T2, T3, T3b, T3c, T3d, T4, T4b, T4c, T4d, T4e, T4f, T4g, T5, T6 and T7 are Task 3; T8 is Task 4; T9, section 8 error/compatibility restrictions and section 9 evidence/diff review are Task 5. Section 10's thirteen resolutions are repeated in the relevant task contracts. No test ID or lettered variant is dropped. All 31 placement methods and both canvas methods are assigned explicitly.

Each task restates the signatures it consumes and produces. No old-arity constructor, new production helper, second face table, visibility widening, source normalization or baseline failure fabrication is planned. Each task has one lane, one tier, explicit dependencies, exact files, checkbox steps, literal verification commands and a final commit step. All subheadings within tasks use H3 or deeper. The author validates the saved file and also imports the controller's actual parser to check that all five complete commit commands survive parsing.

**T8 diverging-control clarification (resolved by the PM):** the specification's T8 diverging-control bullet originally required the retained placement to keep "the face and slot that direct placement of `alpha` produces there" while also requiring the fresh placement to differ, which cannot both hold. The PM corrected that sentence in specification commit 7df2d081a67c0363bc9a2061b0261907d45d583c: retained keeps the remembered slot and takes the best face for it, and the fresh placement differs in slot or face. Task 4 implements the clarified rule. No design requirement changed.

## Global Constraints

All Java paths below are relative to the repository root. Use Java 8 source-compatible constructs and the existing four-space style.
No other implementation file changes are needed. In particular, do not change `ScreenLabelPlacementShould`, `ScreenLabelPlacementCache` or its key, `GraphCanvas`, `GraphPainter`, `LabelFonts`, the projection/adapter/event plumbing, layout, enclosure algorithms, translations, resources or Gradle configuration.
An enclosure's metadata is populated through the common constructor only. Its placement, flags, ordering and rendering are unchanged. Do not add an old-arity constructor or an inferred-source compatibility overload.
No new public type, method or field is introduced.
No production helper is required. Resolve the remembered row by scanning the existing eight-entry `LADDER`, and use its `font(context.fonts)`, `truncating` and `far` members directly. Eligibility and candidate evaluation may be inline in `retained`. The conceptual operations in section 4 are pseudocode expansions, not additional Java APIs.
Use `Font.equals`, not reference equality. Source equality is exact `String.equals` on display text; do not normalize it again and do not compare to `previous.text()`.
There is **no width-cap rejection on KEEP**, for either forced or ordinary nodes.
Only the remembered slot is tried here; do not loop over a row's slots. Forced re-derivation bypasses the non-truncating width-cap check but still requires containment and obstacle clearance. Forced rows are never dense or truncating.
Ordinary non-truncating candidates use a strict `>` cap rejection, so exact equality fits.
Do not add a new post-truncation cap check, alter Unicode/string slicing, replace the ellipsis, or invent a new truncation policy.
Every rejection occurs before obstacle insertion. DECLINE returns `null` and changes no context state.
There are at most two scans of the fixed eight-entry `LADDER`. Do not add another cache, retained-placement invalidation pass, result type, policy table or mutable cross-frame state.
The context starts with all visible node-disc **axis-aligned bounding squares**, using the existing minimum screen radius of 2, plus seed obstacles. Do not substitute circular intersection geometry. Radius sorting is descending and stable for ties. Placement passes remain: forced nodes, emphatic then subtle enclosures, ordinary nodes.
Use JUnit 4 `@Test`, AssertJ, and the existing package `org.freeplane.plugin.graph.canvas`. Every test below must have substantive assertions; no name-only tests or relaxed existing expectations. All production placement assertions find an endpoint by stable key, never by rendered title or list index.
Use `ScreenLabelPlacementShould.fonts()` for placement geometry tests: repository Noto Sans regular/bold subset fonts, normally full 12 and dense 9. Do not copy/change that font loader or use host logical fonts for pinned geometry.
Assert stated width/clearance preconditions in the actual tests so broken fixture assumptions cannot become passing evidence.
Retention decline is an ordinary `null` result consumed by `placeNode`; do not throw, log an error or leave a partially inserted box.
Preserve `PlacedLabel`'s existing null checks and finite/positive geometry validation. Do not catch those programming errors and silently substitute stale data.
Replace the stale retained-value copy path. Do not retain a second legacy execution path, a title-equality-only workaround, cache invalidation on every rename, a feature flag, a deprecated constructor or a compatibility fallback.
No persistence migration, XML version bump or cleanup of saved workspaces is needed: `sourceText` exists only on in-memory placement outputs. The first pass after startup has no previous value and follows the existing null guard.
All existing construction sites must compile against the single new signature, including test-only sites. No public export or OSGi change is needed because the class's public API is unchanged and source metadata stays package-private.
Read each command's actual exit result and the relevant XML files under `freeplane_plugin_graph/build/test-results/test/TEST-*.xml`. Record executed class/test counts, failures and errors separately for focused and full runs. A timeout, hang, dependency-resolution failure or missing XML is not a passing suite.
Do not replace the full module suite with focused results or quote an old test count as current evidence. No acceptance-driver or launch-smoke invocation is necessary for this local placement fix.

## Task 1: Add mandatory source metadata and update all seven constructor sites

**Implementer tier:** Standard

**Lane:** live-title-refresh

**Dependencies:** None. Deliver only constructor plumbing; the retention behavior changes in Task 2.

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/PlacedLabel.java:20-64` (field and constructor)
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/PlacedLabel.java:102-104` (accessor insertion)
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacement.java:337-343` (enclosure constructor)
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacement.java:467-472` (retained constructor)
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacement.java:513-517` (ladder constructor)
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacement.java:607-611` (base constructor)
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacement.java:621-631` (hover-only constructor)
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java:1919-1922` (hidden constructor)
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java:1976-1979` (retained constructor)
- Test (unchanged): `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacementShould.java`
- Test (constructor arguments only): `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java`

**Interfaces:**

- Consumes: existing `ProjectedEndpointKey`, `Font`, `PlacedLabel.Mode`, `PlacedLabel.Rung`, `Optional<LeaderLine>`, and `ScreenLabelPlacement.Slot`; `ProjectedNode.label(): SafeNodeLabel`; `SafeNodeLabel.displayText(): String`. All remain unchanged.
- Produces: `final String sourceText;` and package-private `String sourceText()` in `PlacedLabel`, plus the single mandatory package-private constructor below. All seven old construction sites consume it.
- Preserves: every existing public declaration and all constructor geometry checks, exception text and slot semantics; `keepsAdjacentAboveLabelsLeaderlessAndHoverOnlyLabelsUnpainted(): void`; `private static PlacedLabel retainedLabel(CanvasState state, GraphTheme theme, double zoom, ScreenLabelPlacement.Slot slot)`.

- [ ] **Step 1: Add the immutable field, replace the constructor signature, and add the accessor**

Place `final String sourceText;` beside the existing private text/truncation metadata. The field and accessor are package-private, deliberately not public. Use this exact constructor and preserve existing validation/order for the existing fields:

```java
PlacedLabel(final ProjectedEndpointKey endpoint, final String text, final Font font,
        final Mode mode, final Rung rung, final double anchorX, final double anchorY,
        final double width, final double height, final boolean truncated,
        final String sourceText, final boolean forced, final boolean emphaticAtAnchor,
        final boolean forcedAtBaseSlot, final boolean fullTextSlotWasFree,
        final Optional<LeaderLine> leader, final ScreenLabelPlacement.Slot slot) {
    this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
    this.text = Objects.requireNonNull(text, "text");
    this.font = Objects.requireNonNull(font, "font");
    this.mode = Objects.requireNonNull(mode, "mode");
    this.rung = Objects.requireNonNull(rung, "rung");
    if (!Double.isFinite(anchorX) || !Double.isFinite(anchorY) || !Double.isFinite(width)
            || !Double.isFinite(height) || !(width > 0.0) || !(height > 0.0)) {
        throw new IllegalArgumentException("Placed label geometry must be finite and positive");
    }
    this.anchorX = anchorX;
    this.anchorY = anchorY;
    this.width = width;
    this.height = height;
    this.truncated = truncated;
    this.sourceText = Objects.requireNonNull(sourceText, "sourceText");
    this.forced = forced;
    this.emphaticAtAnchor = emphaticAtAnchor;
    this.forcedAtBaseSlot = forcedAtBaseSlot;
    this.fullTextSlotWasFree = fullTextSlotWasFree;
    this.leader = Objects.requireNonNull(leader, "leader");
    this.slot = slot;
}

String sourceText() {
    return sourceText;
}
```

Do not add a new empty-string policy, an old-arity overload, value equality, serialization, or an inferred-source fallback.

- [ ] **Step 2: Update the five production constructor calls atomically with Step 1**

Only add the new argument in the four factories outside `retained`. In `retained` this task adds current source metadata but deliberately leaves the old text/font/geometry behavior for Task 2; do not copy `previous.sourceText()`. Exact call replacements:

```java
// enclosureLabel
return new PlacedLabel(endpoint, text, font, mode, rung, anchorX, anchorY, size.getWidth(),
    size.getHeight(), false, text, forced, emphaticAtAnchor, false, false, leader, Slot.ABOVE);

// retained (constructor plumbing only in this task)
return new PlacedLabel(previous.endpoint(), previous.text(), previous.font(), previous.mode(),
    previous.rung(), anchor[0], anchor[1], previous.width(), previous.height(),
    previous.truncated(), context.nodes.get(key).label().displayText(),
    isForced(context, key), previous.emphaticAtAnchor(), false, false,
    nodeLeader(previous.slot(), centerX, centerY, radius, anchor[0], anchor[1],
        previous.width(), previous.height()), previous.slot());

// ladder
return new PlacedLabel(endpoint, candidateText, font, PlacedLabel.Mode.INTERIOR,
    rung.rung, anchor[0], anchor[1], size.getWidth(), size.getHeight(),
    truncated, fullText, forced, false, false,
    fullTextWasFree, nodeLeader(slot, centerX, centerY, radius, anchor[0], anchor[1],
        size.getWidth(), size.getHeight()), slot);

// baseSlot
return new PlacedLabel(endpoint, fullText, font, PlacedLabel.Mode.INTERIOR,
    PlacedLabel.Rung.FULL_NEAR, anchor[0], anchor[1], size.getWidth(), size.getHeight(),
    false, fullText, forced, false, true, false, Optional.<LeaderLine>empty(), Slot.ABOVE);

// hoverOnly
return new PlacedLabel(endpoint, fullText, font, PlacedLabel.Mode.HOVER_ONLY,
    PlacedLabel.Rung.HOVER_ONLY, anchor[0], anchor[1], size.getWidth(), size.getHeight(),
    false, fullText, false, false, false, false, Optional.<LeaderLine>empty(), Slot.ABOVE);
```

- [ ] **Step 3: Update the two existing paint-test constructor calls without changing their behavior or assertions**

```java
// keepsAdjacentAboveLabelsLeaderlessAndHoverOnlyLabelsUnpainted
PlacedLabel hidden = new PlacedLabel(above.endpoint(), above.text(), above.font(),
    PlacedLabel.Mode.HOVER_ONLY, PlacedLabel.Rung.HOVER_ONLY, above.anchorX(), above.anchorY(),
    above.width(), above.height(), false, above.text(), false, false, false, false,
    Optional.<LeaderLine>empty(), ScreenLabelPlacement.Slot.ABOVE);

// retainedLabel: preserve FULL_DISPLACED even when slot is RIGHT
PlacedLabel previous = new PlacedLabel(ProjectedEndpointKey.ofNode(node.key()),
    node.label().displayText(), font, PlacedLabel.Mode.INTERIOR, PlacedLabel.Rung.FULL_DISPLACED,
    0, 0, bounds.getWidth(), bounds.getHeight(), false, node.label().displayText(),
    false, false, false, false, Optional.<LeaderLine>empty(), slot);
```

- [ ] **Step 4: Verify the constructor migration keeps the existing suite green**

Run from the implementation checkout:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" \
PATH="$HOME/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH" \
gradle :freeplane_plugin_graph:test --rerun-tasks -PTestLoggingFull \
  --tests 'org.freeplane.plugin.graph.canvas.ScreenLabelPlacementShould' \
  --tests 'org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould' \
  --tests 'org.freeplane.plugin.graph.integration.GraphWorkspaceLiveNodeRefreshShould'
```

Expected: exit 0, executed tests, no failures/errors. Read each selected class's current XML and record counts separately. This task edits `ScreenLabelPlacement`; existing assertions must stay green. Verify all seven original call sites are migrated and no old constructor remains. No new behavioral RED run is claimed for this mechanical prerequisite.

- [ ] **Step 5: Commit**

```bash
git diff --check
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/PlacedLabel.java freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacement.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java
git diff --cached --check
git commit -m "2026-11-01-graph-workspace-live-title-refresh: carry display source in placed labels"
```

## Task 2: Replace stale retention with KEEP, RE-DERIVE and DECLINE

**Implementer tier:** Standard

**Lane:** live-title-refresh

**Dependencies:** Task 1 must be committed with its existing-suite verification passing.

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacement.java:451-473` (entire `retained` body; range refers to original source).
- Test (unchanged): `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacementShould.java`
- Test (unchanged): `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java`

**Interfaces:**

- Consumes: `String PlacedLabel.sourceText()` and `PlacedLabel(ProjectedEndpointKey endpoint, String text, Font font, Mode mode, Rung rung, double anchorX, double anchorY, double width, double height, boolean truncated, String sourceText, boolean forced, boolean emphaticAtAnchor, boolean forcedAtBaseSlot, boolean fullTextSlotWasFree, Optional<LeaderLine> leader, ScreenLabelPlacement.Slot slot)` from Task 1.
- Consumes unchanged private members: `LadderRung[] LADDER`; `LadderRung.rung: PlacedLabel.Rung`, `truncating: boolean`, `far: boolean`, `Font font(LabelFonts fonts)`; `Context.nodes: Map<ProjectedNodeKey, ProjectedNode>`, `Context.fonts: LabelFonts`, `Context.request: LabelPlacementRequest`, `Context.obstacles: List<Rectangle2D>`, `double Context.screenX(ProjectedNodeKey key)`, `double Context.screenY(ProjectedNodeKey key)`, `double Context.radius(ProjectedNodeKey key)`; `boolean isForced(Context context, ProjectedNodeKey key)` and `boolean intersectsAny(List<Rectangle2D> obstacles, Rectangle2D candidate)`.
- Consumes unchanged geometry helpers: `static double[] slotAnchor(Slot slot, double centerX, double centerY, double radius, double width, double height)`; `static double slotMaxWidth(Slot slot)`; `static Rectangle2D screenBounds(String text, Font font)`; `static double textWidth(String text, Font font)`; `static String truncateTo(String text, Font font, double limit)`; `static Rectangle2D rectangle(double x, double y, double width, double height)`; `private static Optional<LeaderLine> nodeLeader(Slot slot, double centerX, double centerY, double radius, double anchorX, double anchorY, double width, double height)`.
- Produces: same signature `private static PlacedLabel retained(final Context context, final ProjectedNodeKey key, final PlacedLabel previous)`, returning a refreshed accepted value or `null` with no obstacle mutation on decline. No new production helper.
- Preserves unchanged fallback interfaces: `private static PlacedLabel placeNode(final Context context, final ProjectedNodeKey key, final boolean forced, final PlacedLabel previous)`; `private static PlacedLabel ladder(final Context context, final ProjectedNodeKey key, final ProjectedEndpointKey endpoint, final String fullText, final boolean forced)`; `private static PlacedLabel baseSlot(final Context context, final ProjectedNodeKey key, final ProjectedEndpointKey endpoint, final String fullText, final boolean forced)`; `private static PlacedLabel hoverOnly(final Context context, final ProjectedNodeKey key, final ProjectedEndpointKey endpoint, final String fullText)`. Here `fullText` is projection display text.

- [ ] **Step 1: Replace only `retained` with this complete method**

```java
private static PlacedLabel retained(final Context context, final ProjectedNodeKey key,
        final PlacedLabel previous) {
    if (previous == null || previous.mode() == PlacedLabel.Mode.HOVER_ONLY) {
        return null;
    }
    final String source = context.nodes.get(key).label().displayText();
    final Slot slot = previous.slot();
    final boolean forced = isForced(context, key);
    final double centerX = context.screenX(key);
    final double centerY = context.screenY(key);
    final double radius = context.radius(key);
    final boolean far = slot == Slot.ABOVE_FAR || slot == Slot.BELOW_FAR
        || slot == Slot.RIGHT_FAR || slot == Slot.LEFT_FAR;
    LadderRung rememberedRow = null;
    for (final LadderRung row : LADDER) {
        if (row.rung == previous.rung()) {
            rememberedRow = row;
            break;
        }
    }
    final boolean keepClass = rememberedRow != null && rememberedRow.far == far
        && (!forced || rememberedRow.rung == PlacedLabel.Rung.FULL_NEAR
            || rememberedRow.rung == PlacedLabel.Rung.FULL_DISPLACED)
        && previous.sourceText().equals(source)
        && rememberedRow.font(context.fonts).equals(previous.font());
    if (keepClass) {
        final Font font = rememberedRow.font(context.fonts);
        final String text = rememberedRow.truncating
            ? truncateTo(source, font, slotMaxWidth(slot)) : source;
        final boolean truncated = !text.equals(source);
        final Rectangle2D size = screenBounds(text, font);
        final double[] anchor = slotAnchor(slot, centerX, centerY, radius,
            size.getWidth(), size.getHeight());
        final Rectangle2D box = rectangle(anchor[0], anchor[1], size.getWidth(), size.getHeight());
        if (!context.request.placementArea().contains(box)
                || intersectsAny(context.obstacles, box)) {
            return null;
        }
        final Optional<LeaderLine> leader = nodeLeader(slot, centerX, centerY, radius,
            anchor[0], anchor[1], size.getWidth(), size.getHeight());
        context.obstacles.add(box);
        return new PlacedLabel(previous.endpoint(), text, font, previous.mode(), rememberedRow.rung,
            anchor[0], anchor[1], size.getWidth(), size.getHeight(), truncated, source, forced,
            previous.emphaticAtAnchor(), false, false, leader, slot);
    }
    for (final LadderRung row : LADDER) {
        if (row.far != far || (forced && row.rung != PlacedLabel.Rung.FULL_NEAR
                && row.rung != PlacedLabel.Rung.FULL_DISPLACED)) {
            continue;
        }
        final Font font = row.font(context.fonts);
        final double limit = slotMaxWidth(slot);
        if (!forced && !row.truncating && textWidth(source, font) > limit) {
            continue;
        }
        final String text = row.truncating ? truncateTo(source, font, limit) : source;
        final boolean truncated = !text.equals(source);
        final Rectangle2D size = screenBounds(text, font);
        final double[] anchor = slotAnchor(slot, centerX, centerY, radius,
            size.getWidth(), size.getHeight());
        final Rectangle2D box = rectangle(anchor[0], anchor[1], size.getWidth(), size.getHeight());
        if (!context.request.placementArea().contains(box)
                || intersectsAny(context.obstacles, box)) {
            continue;
        }
        final Optional<LeaderLine> leader = nodeLeader(slot, centerX, centerY, radius,
            anchor[0], anchor[1], size.getWidth(), size.getHeight());
        context.obstacles.add(box);
        return new PlacedLabel(previous.endpoint(), text, font, previous.mode(), row.rung,
            anchor[0], anchor[1], size.getWidth(), size.getHeight(), truncated, source, forced,
            previous.emphaticAtAnchor(), false, false, leader, slot);
    }
    return null;
}
```

- [ ] **Step 2: Review the three outcomes and preserved algorithms against these exact assertions**

KEEP is entered only when a remembered `LADDER` row exists, its `far` family matches the slot, forced eligibility holds, exact display sources match and the current row font `equals` the previous font. KEEP remeasures and recomputes anchors/leaders even for unchanged inputs, applies no width-cap rejection, and returns `null` immediately when geometry is unacceptable. It never tries a denser face on that decline.

RE-DERIVE occurs for a changed source, changed face font, missing/ineligible row or promotion. It scans existing `LADDER` order at exactly one remembered slot. Only ordinary non-truncating candidates reject width strictly greater than the cap; forced re-derivation skips that cap but still requires containment and clearance and permits only FULL_NEAR/FULL_DISPLACED. Truncation is current rendering/source inequality. No post-truncation cap check.

Both accepted paths preserve endpoint, mode, emphatic flag and slot; use current source/font/text/dimensions/anchor/leader/forced membership; clear base/free diagnostic flags; add exactly one accepted rectangle. All rejection paths add nothing. Existing null/hover guards, fallback order, square-disc obstacles, sorting, enclosure passes, `LADDER`, `truncateTo`, `fullTextCandidateWasFree`, `ladder`, `baseSlot` and `hoverOnly` remain unchanged. No error logging, recovery shim or missing-row dereference.

- [ ] **Step 3: Verify this `ScreenLabelPlacement` edit keeps the existing suite green**

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" \
PATH="$HOME/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH" \
gradle :freeplane_plugin_graph:test --rerun-tasks -PTestLoggingFull \
  --tests 'org.freeplane.plugin.graph.canvas.ScreenLabelPlacementShould' \
  --tests 'org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould' \
  --tests 'org.freeplane.plugin.graph.integration.GraphWorkspaceLiveNodeRefreshShould'
```

Expected: exit 0 with executed tests and zero failures/errors; read actual XML counts. Keep all existing assertions green, especially `retainsASlotForAOnePixelMove`, `invalidatesAStaleSlotAndReLadders`, `resetsTheBaseSlotFlagWhenRetainingAnO4Label`, all absolute coordinate pins, leader measurements and enclosure cases. The old paint helper's FULL_DISPLACED/RIGHT mismatch now re-derives a near full-font row without changing its pinned RIGHT geometry. The approved delivery order adds the new behavioral tests in Task 3; do not claim the unchanged suite is a newly demonstrated RED witness.

- [ ] **Step 4: Commit**

```bash
git diff --check
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacement.java
git diff --cached --check
git commit -m "2026-11-01-graph-workspace-live-title-refresh: refresh retained faces from current display text"
```

## Task 3: Add all T1–T7 placement and cache regressions

**Implementer tier:** Advanced

**Lane:** live-title-refresh

**Dependencies:** Tasks 1 and 2 committed and verified; this task adds tests only.

**Files:**

- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/ScreenLabelLiveTitleRefreshShould.java`
- Read/test (unchanged): `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacementShould.java:996-1011,1043-1052,1078-1090` (existing pinned fonts and enclosure fixture seams).

**Interfaces:**

- Consumes the mandatory Task 1 constructor: `PlacedLabel(ProjectedEndpointKey endpoint, String text, Font font, Mode mode, Rung rung, double anchorX, double anchorY, double width, double height, boolean truncated, String sourceText, boolean forced, boolean emphaticAtAnchor, boolean forcedAtBaseSlot, boolean fullTextSlotWasFree, Optional<LeaderLine> leader, ScreenLabelPlacement.Slot slot)` and package-private `String PlacedLabel.sourceText()`.
- Consumes: `public List<PlacedLabel> ScreenLabelPlacement.place(final LabelPlacementRequest request, final List<PlacedLabel> previous, final LabelFonts fonts)`; package-private overload `List<PlacedLabel> place(final LabelPlacementRequest request, final List<PlacedLabel> previous, final LabelFonts fonts, final List<Rectangle2D> seedObstacles)`; `public List<PlacedLabel> ScreenLabelPlacementCache.place(final LabelPlacementRequest request, final LabelFonts fonts)`.
- Consumes: `static LabelFonts ScreenLabelPlacementShould.fonts()`; `static List<PlacedLabel> ScreenLabelPlacementShould.placeEnclosure(String text, boolean emphatic, Rectangle2D area, List<ScreenLabelPlacementShould.SceneNode> nodes, RenderingLevel level)`; `static PlacedLabel ScreenLabelPlacementShould.findEnclosure(List<PlacedLabel> placed)`; `public static LabelFonts LabelFonts.of(final Font full, final Font dense, final Font emphatic)`.
- Consumes unchanged measurements: `static Rectangle2D ScreenLabelPlacement.screenBounds(final String text, final Font font)`; `static double[] slotAnchor(final Slot slot, final double centerX, final double centerY, final double radius, final double width, final double height)`; `static double slotMaxWidth(final Slot slot)`; `static double textWidth(final String text, final Font font)`; `static String truncateTo(final String text, final Font font, final double limit)`; `static Rectangle2D rectangle(final double x, final double y, final double width, final double height)`.
- Consumes: `GraphProjection.structure(long generation, List<ProjectedNode> nodes, List<ProjectedEnclosure> enclosures)`; `GraphGeometry.of(Map<ProjectedNodeKey, NodeGeometry> nodes, Map<EnclosureHullKey, HullGeometry> hulls)`; `LayoutPositions.of(Map<ProjectedNodeKey, LayoutPoint> nodes, Map<EnclosureHullKey, LayoutPoint> enclosures)`; `LabelPlacementRequest.of(GraphProjection projection, GraphGeometry geometry, LayoutPositions positions, double zoom, double centerX, double centerY, Rectangle2D placementArea, Set<ProjectedEndpointKey> forced, RenderingLevel renderingLevel)`.
- Produces `public class ScreenLabelLiveTitleRefreshShould` with all 31 public, no-argument `void` methods named in the steps below, each annotated `@Test`, and these private helpers (complete implementations immediately below): `ProjectedNodeKey stableKey(final String id)`; `ProjectedNode node(final String id, final String fullText, final String displayText)`; `LabelPlacementRequest request(final long generation, final Rectangle2D area, final Set<ProjectedEndpointKey> forced, final ProjectedNode[] nodes, final double[][] screenDiscs)`; `PlacedLabel findNode(final List<PlacedLabel> labels, final String id)`; `PlacedLabel remembered(final LabelPlacementRequest request, final String id, final PlacedLabel.Rung rung, final Font font, final boolean truncating, final ScreenLabelPlacement.Slot slot, final boolean forcedAtBaseSlot)`; `void assertSamePlacement(final PlacedLabel actual, final PlacedLabel expected)`; `String repeat(final char character, final int count)`. All helpers are static. No production seam is added.

- [ ] **Step 1: Create the test class and these complete shared fixtures**

Start with this compilable fixture class, then add each substantive test specified in Steps 2–17. Assertions following the code are exact test contracts, not optional examples; give each required method its own `@Test`.

```java
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
        assertThat(screenDiscs).hasSize(nodes.length);
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
}
```

Use exact equality for identical inputs/replay; `within(1e-9)` for independently calculated geometry and only `within(1e-4)` for the stated rounded pins. Tests comparing different diagnostic provenance must assert those flags separately and compare the other fields individually; do not weaken `assertSamePlacement`. Every production result is found using `findNode`, never by title or result index. A leader test obtains its previous label from production, never the leader-empty synthetic helper.

Common fixtures, declared locally in each named test unless a private fixture above supplies the data: all areas begin at zero; ordinary radius is 8; no seeds/enclosures except where stated. Use the same `LabelFonts F = fonts()` instance throughout each history.

- Open: `area=(0,0,1280,800)`, ordered IDs `edited,bravo,charlie,delta`, centres `(600,400),(680,400),(640,360),(640,440)`, edited `alpha -> omega renamed`, others display/full text equal ID.
- Strip: `area=(0,0,200,50)`, edited centre `(100,32)`, `B=repeat('W',20)`, `A=repeat('W',50)`, `A2=repeat('W',60)`, `A3=repeat('W',70)`. Assert `W(B,F.full)>200`, `W(B,F.dense)<=200`, `W(B,F.dense)>130`, `W(A,F.dense)>200`, equal full-font `truncateTo(A,full,200)/truncateTo(B,full,200)` and equal truncations of A/A2. `W/H` mean `screenBounds` width/height, not new production helpers.
- Height band: strip plus seed `(0,2,200,2)`. Assert full ABOVE top near `1.655886` intersects the band and dense ABOVE top near `5.741915` clears it. A/A2/A3 start `TRUNCATED_DENSE_NEAR/ABOVE`; removing the seed frees full-font truncated ABOVE.
- Over-cap: `area=(0,0,1128,364)`, forced edited at `(542,182)`, source `Well-Ordering Theorem of Choice and Regularity`; assert full width near `274.130005 >200`, height near `16.344114`, full ABOVE anchor near `(542,159.827943)`, area contains box, no disc collision. Fresh value is base-flagged `FULL_NEAR/ABOVE`.

Add these additional private fixture/assertion helpers inside the same class. Exact produced signatures: `LabelPlacementRequest openRequest(long generation, String title)`; `LabelPlacementRequest strip(long generation, String title, boolean forced)`; `LabelPlacementRequest overCap(long generation, String title, boolean forced)`; `List<Rectangle2D> band()`; `void checkStrip(LabelFonts fonts)`; `void checkBand(LabelFonts fonts)`; `void assertFace(PlacedLabel label, String source, String rendering, Font font, PlacedLabel.Rung rung, ScreenLabelPlacement.Slot slot)`; `void assertMeasured(PlacedLabel label)`; `void assertRenderSame(PlacedLabel actual, PlacedLabel expected)`; `void assertClear(PlacedLabel label, LabelPlacementRequest request)`; `List<PlacedLabel> without(List<PlacedLabel> labels, String id)`; `void verifyExpansionRejection(boolean earlierLabel, boolean areaEdge)`. All are static; `verifyExpansionRejection` is supplied with the T6 bodies. These are test-only helpers, not production APIs or a second face policy.

```java
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
```

- [ ] **Step 2: T1 — implement `refreshesTheRetainedTitleWithAStableNodeIdentity`**

Exact assertions: use one placement instance on open generations 1/2, initial previous null, then the whole first result. Keys and endpoints of edited projection nodes are equal; generation advances; generation 2 edited `text()` and `sourceText()` both equal `omega renamed` and not `alpha`; no output `text()` equals `alpha`; unchanged nodes agree by their stable endpoints with diagnostic transitions asserted separately. Edited face/slot remain `FULL_NEAR/ABOVE`, full-font measurement equals new width/height, forced/base/free flags false on retention. Stale baseline copies `alpha`; that semantic string assertion is the failure discriminator, not constructor compilation.

```java
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
```

- [ ] **Step 3: T2 — implement `refreshesTheTitleThroughTheSamePlacementCache`**

Build generation 1 via `request` and generation 2 via this exact cache-isolation construction; `secondProjection` is generation 2 with only the edited `SafeNodeLabel` changed:

```java
final LabelPlacementRequest secondRequest = LabelPlacementRequest.of(secondProjection,
    firstRequest.geometry(), firstRequest.positions(), firstRequest.zoom(),
    firstRequest.centerX(), firstRequest.centerY(), firstRequest.placementArea(),
    firstRequest.forced(), firstRequest.renderingLevel());
final ScreenLabelPlacementCache cache = new ScreenLabelPlacementCache();
final List<PlacedLabel> first = cache.place(firstRequest, F);
assertThat(cache.place(firstRequest, F)).isSameAs(first);
assertThat(secondRequest.geometry()).isSameAs(firstRequest.geometry());
assertThat(secondRequest.positions()).isSameAs(firstRequest.positions());
assertThat(secondRequest.projection().generation()).isGreaterThan(firstRequest.projection().generation());
final List<PlacedLabel> second = cache.place(secondRequest, F);
assertThat(second).isNotSameAs(first);
assertThat(findNode(second, "edited").text()).isEqualTo("omega renamed");
assertThat(findNode(second, "edited").sourceText()).isEqualTo("omega renamed");
assertThat(cache.place(secondRequest, F)).isSameAs(second);
```

Keep viewport, area, forced membership, rendering level and fonts identical. Do not build generation 2 through `request` and accidentally change the identity-sensitive positions object. Cache generation invalidation already works; baseline's wrong assertion is stale text, not list identity.

```java
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
```

- [ ] **Step 4: T3 — implement `keepsAnUnchangedFaceOnImmediateReplayAndAfterRetainedHistory`**

Replay an open request immediately and compare every endpoint, text/source, face, font, truncation, dimension, anchor and leader; explicitly assert initial non-truncating `fullTextSlotWasFree=true` becomes false on retention. Replay the retained list again; now use `assertSamePlacement` on every stable ID, including flags.

Within this method also use area `400x50`, edited `(200,28)`, title `Axiom Schema of Replacement a…` (actual final U+2026). Assert full width near `193.873367` and dense width below 200. Full ABOVE leaves top, production fresh chooses `DENSE_NEAR/ABOVE`. Replay at y=28 to produce retained history, then move to y=32 with identical source/fonts/eligibility and use that retained result as previous. Assert dense face/text/measurements survive; compute expected current anchor/leader from that face and geometry. Null-previous control at y=32 must choose `FULL_NEAR/ABOVE`. Unconditional re-derivation would upgrade; baseline is the preservation oracle.

```java
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
```

- [ ] **Step 5: T3b — implement `keepsAValidSlotWhenAFreshPlacementWouldChooseAnother`**

Area `400x300`, Alpha `(200,150)`, source Alpha. Measure full `w,h`; seed two rectangles of width `0.5`, height `2`, x=`200+w/2-0.25`, centred vertically at full ABOVE and BELOW anchors. Assert they block the full initial ABOVE/BELOW boxes and clear RIGHT; production first result is `FULL_NEAR/RIGHT`. Move centre to `(199,150)`, retain seeds unchanged; assert upper seed now clears ABOVE. Replay must keep RIGHT; null-previous control chooses ABOVE. Both boxes must be contained, clear of seeds and node square. Merely replaying identical inputs is insufficient to establish retention remains enabled.

```java
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
```

- [ ] **Step 6: T3c — implement `declinesAnObstructedUnchangedFaceWithoutTryingDenserRungs`**

Area `400x300`, ordinary Alpha `(200,150)` first, Beta `(160,150)` second; both radius 8 and matching title IDs. Production Alpha initially `FULL_NEAR/ABOVE`. Move Beta to `(200,115)` with unchanged titles/fonts. Assert full ABOVE relative vertical interval near `[-30.344114,-14]` meets Beta square `[-43,-27]`, but dense ABOVE `[-26.258085,-14]` clears it.

Compare ordinary replay with a control omitting only Alpha's previous value and retaining Beta's previous value. Both Alpha results must be full-font `FULL_NEAR/BELOW`, never dense ABOVE. Drive independent histories through Beta relative y `-35,-20,+35,+20` (first comparison is the -35 pass); each pass feeds its own prior outputs, removing only Alpha from the control. Assert equal endpoint, source/text, font/row, dimensions, anchors and leader, and slot sequence `BELOW,BELOW,ABOVE,ABOVE`. Compare diagnostic provenance separately: accepted retention clears free flag, successful non-truncating fallback may set it. Never reset all histories/obstacles.

```java
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
```

- [ ] **Step 7: T3d — implement `fallsBackUnderEarlierRetainedLabelObstacles`**

Area `300x150`; current ordinary Alpha `(100,100)`, Beta `(130,120)`. Synthesize valid old full Alpha/RIGHT and Beta/BELOW; construct Beta previous in an otherwise matching y=115 request so its old BELOW box is contained. Current Alpha/RIGHT is acceptable; current Beta/BELOW ends near `150.344114` outside area. Assert Beta fallback ABOVE intersects accepted retained Alpha/RIGHT, so replay chooses `FULL_NEAR/RIGHT`. Compare Beta against a control omitting only Beta's previous label, preserving Alpha. Independently assert wholly fresh Alpha and Beta both use ABOVE, and Beta fresh slot differs from replay. Source is unchanged throughout.

```java
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
```

- [ ] **Step 8: T4 — implement four independent changed-title tests**

Give these four methods exact substantive assertions:

1. `upgradesAShortenedTitleAtItsRememberedSlot`: strip B -> Alpha, production initial `DENSE_NEAR/ABOVE`, then `FULL_NEAR/ABOVE`, full Alpha visible, source Alpha, no truncation and current full measurement.
2. `degradesAnExpandedTitleAtItsRememberedSlot`: strip Alpha -> B, full -> `DENSE_NEAR/ABOVE`, source/text B, no truncation, current dense measurement; assert full near fails cap and selected dense near fits. Family-filtered earlier rows do not silently bypass these preconditions.
3. `reLaddersWhenNoFaceFitsTheRememberedSlot`: area `200x100`, edited `(140,50)`, synthetic old full Alpha/RIGHT. Assert old RIGHT fits in the 46-pixel interval beginning x=154. Change to A; assert every eligible new RIGHT rendering (full, dense, truncated full, truncated dense) exceeds available width/containment. Final current A output is `TRUNCATED_NEAR/LEFT`, not RIGHT; source A and truncation true.
4. `retainsOnlyFacesFromTheRememberedSlotFamily`: area `600x300`, edited `(300,150)`, old A synthetic truncated RIGHT and independently RIGHT_FAR; change to Alpha. Near result must be `FULL_NEAR/RIGHT`, far result `FULL_DISPLACED/RIGHT_FAR`; verify rung family membership explicitly. No cross-family row is acceptable.

```java
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
```

- [ ] **Step 9: T4b — implement four independent forced-policy tests**

1. `declinesAForcedSlotInsteadOfTakingADenseFace`: area `400x300`, Alpha initially `(200,150)` and Beta `(160,150)` radius 8, Alpha forced. Produce old Alpha full ABOVE, then change Alpha -> Gamma and move Beta to `(200,115)`. Assert full retained ABOVE is blocked, dense would clear, only full near eligible. Final Gamma must be full-font `FULL_NEAR/BELOW`, source/text Gamma, forced true, truncation false, base flag false. This preservation control need not fail baseline.
2. `rejectsDenseAndTruncatedFacesWhenPromotedToForced`: independent strip B/dense and A/truncated production histories. Promote each unchanged title to forced; assert old dense/truncated row excluded, full source visible via `FULL_NEAR` base slot, full font, forced/base true, no truncation. Assert full width leaves the 200-pixel area, so uncapped retained full candidate still cannot fit.
3. `keepsAnUnchangedForcedOverCapFace`: over-cap production first has base true; two unchanged forced replays each preserve endpoint/mode/row/text/font/slot/width/height/anchor/leader, width >200, forced true, base/free false. Assert flag transitions separately rather than strict equality of intentionally changed flags.
4. `rederivesAChangedForcedTitleWithoutApplyingTheSlotCap`: take retained over-cap value and append `!`; assert new full width still >200, candidate inside clear area. Output source/text advance, full ABOVE/rung retained, forced true, base false. A wrong cap would force the fallback base path with base true.

```java
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
```

- [ ] **Step 10: T4c — implement all five metadata/collision/transition tests**

1. `detectsATitleEditHiddenByAnIdenticalTruncation`: unbanded strip A initially `TRUNCATED_NEAR/ABOVE`, source A != rendering; assert old rendering equals `truncateTo(B,F.full(),200)`. Edit to B; expect `DENSE_NEAR/ABOVE`, all B shown, source B, truncation false.
2. `recordsTheDisplaySourceOnTruncatedAndRetainedOutputs`: height-band A3 -> A2, initial and accepted-retained outputs both `TRUNCATED_DENSE_NEAR/ABOVE`; sources equal respective display text and differ from rendering. Use the retained A2 output for a third unchanged-A2 pass with seed removed; expect truncated dense stays. Fresh unseeded control must select `TRUNCATED_NEAR/ABOVE`. This pins both ladder and retained source assignment, not just the initial assignment.
3. `clearsTruncationWhenTheTitleBecomesItsPreviousRendering`: unbanded A -> exactly previous rendered text including U+2026. Current source/text equals that literal string, full-font near now fits, and `truncated=false`; do not infer truncation from presence of an ellipsis.
4. `recordsDisplayTextWhenProjectionFullTextDiffers`: `prefix=repeat('i',77)`, `display=prefix+"..."`, `full=prefix+"hidden-tail-A"`. Assert display has 80 code points, full >80 and differs; construct `SafeNodeLabel.of(full,display)`. On production first placement and replay, `sourceText=display` and `sourceText!=full`. This is storage coverage; T4g below separately proves comparison.
5. `recordsSourceTextAtAllPlacementConstructionSites`: separately assert source on truncated ladder, accepted retention, forced base, hover-only, and enclosure results. For hover-only use ordinary Alpha at `(2,2)` in `4x4`; assert mode HOVER_ONLY and source Alpha. For enclosure call `ScreenLabelPlacementShould.placeEnclosure("Enclosure display",false,new Rectangle2D.Double(0,0,1280,800),Collections.<ScreenLabelPlacementShould.SceneNode>emptyList(),RenderingLevel.FULL)`; find via `findEnclosure`, assert non-null source exactly enclosure display. Enclosures remain unretained. Truncated/retained/base outputs must use complete display source, not their rendering.

```java
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
```

- [ ] **Step 11: T4d — implement `keepsAnOverCapFaceAfterDemotionAndOnTheNextReplay`**

Produce over-cap forced base output. Empty only forced set, leave title/fonts/geometry unchanged; replay that ordinary result once more. Both outputs have forced false, base false, no truncation, full source visible and width >200. Face, slot, measurement, anchor and leader equal forced predecessor exactly. Assert intentional diagnostic changes separately. KEEP must not start applying ordinary RE-DERIVE caps to this history.

```java
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
```

- [ ] **Step 12: T4e — implement `keepsAnUnchangedTruncatedFaceWhenSpaceBecomesAvailable`**

Under band produce A3 then shorten to A2, obtaining accepted retained `TRUNCATED_DENSE_NEAR/ABOVE`. Remove band with A2 unchanged. Assert metadata before and after is source A2, rendering according to dense truncation, truncation true, retained dense face and acceptable new box. Assert fresh control or independently measured full candidate now fits and fresh selects `TRUNCATED_NEAR/ABOVE`. This witness must use the already shortened retained A2 output, not an unedited initial face.

```java
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
```

- [ ] **Step 13: T4f — implement `advancesTheSourceWhenTwoTitlesRenderTheSameTruncation`**

Unbanded strip A -> A2; assert distinct display sources, same full-font truncation at remembered slot, and neither source fits earlier full-text eligible near faces. Rendering and row may be identical; `sourceText` must advance to A2 and remain distinct from rendered text. Do not claim this alone proves entry into RE-DERIVE; T4c/T4e/T4g own that discrimination.

```java
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
```

- [ ] **Step 14: T4g — implement `ignoresAnEditConfinedToTheProjectionHiddenTail`**

Use actual extractor-style display: prefix 77 `i`, display prefix+three ASCII dots (exactly 80 code points), full1/full2 prefix+`hidden-tail-A`/`hidden-tail-B`, both >80 code points. Construct labels explicitly; do not modify editor/extractor. Derive pinned regular full 9 pt, dense 6.75 pt via `LabelFonts.of`; same fonts each pass. Assert full display width <=200.

Let hf/hd be measured full/dense heights. Area `400x40`, initial centre `(200,14+(hf+hd)/2)`: assert full ABOVE outside top but dense ABOVE contained, production `DENSE_NEAR/ABOVE`. Replay unchanged once; then use full2, same display, centre `(200,14+hf+1)`. Assert old/current IDs equal, displays equal, full texts differ, fonts/eligibility unchanged; new full ABOVE fits, remembered dense ABOVE fits. Retention must stay dense and source=display. Fresh must choose `FULL_NEAR/ABOVE`. Comparing against fullText instead of displayText would upgrade and fail. Never substitute 12-pt rounded coordinates for these measured heights.

```java
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
```

- [ ] **Step 15: T5 — implement all three measurement/leader tests**

1. `remeasuresWidthAfterASameFontTitleEdit`: isolated roomy ordinary alpha/FULL_NEAR/ABOVE -> omega renamed; assert full widths differ, font stays full, emitted width equals `screenBounds(newText,F.full()).getWidth()` within `1e-9`; source/text current.
2. `remeasuresHeightAndAnchorAfterAFaceTransition`: strip Alpha -> B; full/dense heights differ. Assert new dense height equals current bounds, new anchor `(100,32-8-6-newHeight/2)` within `1e-9` and differs from old; also assert new width and actual bounds match new measurements.
3. `recomputesBothEndsOfADiagonalLeaderAfterRetitling`: area `600x400`, node `(250,200)`, radius8. Obtain initial alpha FULL_NEAR/ABOVE_RIGHT through production by seeding four `1x1` rectangles centred at full ABOVE/BELOW/RIGHT/LEFT anchors. Assert each seed intersects its cardinal candidate and clears diagonal candidate, prior leader present. Retitle omega renamed and remove seeds; remembered ABOVE_RIGHT remains accepted, leader present. Independently calculate expected start/end from NEW dimensions, not the private production leader method:

```java
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
```

```java
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
```

- [ ] **Step 16: T6 — implement all three rejection/accepted-expansion pairs**

Method names: `declinesAnExpandedBoxAtADiscWithoutLeavingAnObstacle`, `declinesAnExpandedBoxAtAnEarlierLabelWithoutLeavingAnObstacle`, `declinesAnExpandedBoxOutsideTheAreaWithoutLeavingAnObstacle`. Each is a separate `@Test`, and each includes BOTH its rejection run and accepted-expansion control with all assertions below.

Common setup: forced edited E radius8 `(100,150)`, synthetic old FULL_NEAR/RIGHT source alpha, new source `repeat('W',10)`. Old box starts x=114 and clears everything; new full RIGHT starts x=114 and extends beyond x=200, vertical interval near `[141.827943,158.172057]`. Assert these measurements, old containment/clearance, new named rejection and that only full near is eligible (forced+near). Later ordinary L text i radius8 `(170,150+8+6+H(i,F.full)/2)`, synthetic previous FULL_NEAR/ABOVE. Its label centred `(170,150)` is wholly inside a clear portion of rejected expanded E/RIGHT, outside old E/RIGHT; L's disc below it does not obstruct E. E's forced-ladder full ABOVE fallback clears L's ABOVE box. Preserve L previous in EVERY run.

- Disc: area400x300, nodes ordered E,B,L, ordinary Beta B radius8 `(215,150)`. Assert B disc square intersects expanded E/RIGHT but not old E/RIGHT or L label. Accepted control moves only B to `(300,150)` with same title.
- Earlier-label: area400x300, forced Beta B radius9 at `(220,150+9+6+H(Beta,F.full)/2)`, valid full-font ABOVE previous label centred `(220,150)`. Radius sorting puts B before E; B label hits expanded E/RIGHT but B disc below label does not. L ordinary last. Control moves B to x=300, preserving source/ABOVE face.
- Area: area200x300, only E,L; new E/RIGHT leaves right edge, old E/RIGHT/L ABOVE fit, no other obstacle blocks. Control widens to400x300 and preserves specified SCREEN centres through request coordinate conversion; do not preserve old world centres by mistake.

Every rejection must assert current E text/source, full-font ABOVE fallback, base=false, L still ABOVE, L bounds intersect rejected expanded RIGHT box, and L bounds intersect neither blocking obstacle nor E accepted fallback. These are positive no-phantom witnesses; mere non-overlap is insufficient.

Every accepted control must assert E stays RIGHT, current source/full font/current measurements, base=false. L must leave overlapping ABOVE, remain visible (mode not HOVER_ONLY), and accepted L bounds must not intersect accepted enlarged E box. This proves acceptance DOES insert the enlarged obstacle; do not only test rejection absence. No production private reflection or rectangle-state accessor is needed.

```java
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
```

- [ ] **Step 17: T7 and guards — implement the two named tests**

`rederivesAtTheRememberedSlotWhenTheFaceFontChanges`: title Alpha, area400x170. Old pinned fonts24/18 derived from same face, current12/9. Initial centre `(200,120)`, synthetic valid full old BELOW. Move `(200,135)`, source unchanged. Assert old-height BELOW now outside bottom, current-full BELOW fits and current-full ABOVE fits. Replay current fonts must use current full font/measurement/source/anchor at retained BELOW, base=false; null-previous current-font control chooses ABOVE. Font change triggers RE-DERIVE despite unacceptable old geometry; treating it as unchanged geometric decline picks wrong ABOVE. Use `LabelFonts.of` without changing production font loader.

`reLaddersAPreviouslyHoverOnlyNodeFromItsCurrentTitle`: produce ordinary Alpha HOVER_ONLY in tiny4x4 scene; give roomy current request a changed title, pass old HOVER_ONLY result. Assert visible current text/source through ordinary ladder, no attempted missing-row dereference. First-placement null guard is also exercised by every initial production request. This preservation test need not fail baseline.

```java
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
```

- [ ] **Step 18: Verify all 31 new methods and the existing placement suite**

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" \
PATH="$HOME/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH" \
gradle :freeplane_plugin_graph:test --rerun-tasks -PTestLoggingFull \
  --tests 'org.freeplane.plugin.graph.canvas.ScreenLabelLiveTitleRefreshShould'

JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" \
PATH="$HOME/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH" \
gradle :freeplane_plugin_graph:test --rerun-tasks -PTestLoggingFull \
  --tests 'org.freeplane.plugin.graph.canvas.ScreenLabelLiveTitleRefreshShould' \
  --tests 'org.freeplane.plugin.graph.canvas.ScreenLabelPlacementShould' \
  --tests 'org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould' \
  --tests 'org.freeplane.plugin.graph.integration.GraphWorkspaceLiveNodeRefreshShould'
```

Expected both exit0; current XML must show 31 executed new methods, zero failures/errors/skips, and existing classes green with their actual current counts. Inventory names against the 31 declarations in this task; none may be an empty/name-only method. This test-only task must not edit protected production or existing assertion files to obtain green. Preservation controls can pass baseline; source accessor compilation is not behavioral RED evidence. Task 2's production change is already integrated by approved task order, so report post-fix contract evidence honestly rather than claiming an unobserved baseline run.

- [ ] **Step 19: Commit**

```bash
git diff --check
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/ScreenLabelLiveTitleRefreshShould.java
git diff --cached --check
git commit -m "2026-11-01-graph-workspace-live-title-refresh: cover placement cache and retained histories"
```

## Task 4: Add T8 real-canvas refresh controls

**Implementer tier:** Advanced

**Lane:** live-title-refresh

**Dependencies:** Task 3 committed and passing. Specification commit 7df2d081a67c0363bc9a2061b0261907d45d583c clarified the T8 diverging-control rule; Step 1 restates it and the code below implements it.

**Files:**

- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java:2668-2668` (insert new tests/helpers immediately before final class brace; constructor-only Task 1 changes remain intact).
- Test (unchanged): `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/ScreenLabelLiveTitleRefreshShould.java`
- Read (unchanged): `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphCanvas.java:72-105,431-445`

**Interfaces:**

- Consumes: `void GraphCanvas.setTheme(final GraphTheme value)`; `void setViewport(final GraphViewport value)`; `void setPaintState(final GraphPaintState state)`; `void setCanvasState(final CanvasState state)`; existing Swing `setSize(Dimension)`, `getSize(): Dimension`; `private static BufferedImage paintCanvas(final GraphCanvas canvas)`; `private static BufferedImage paintPlaced(CanvasState state, List<PlacedLabel> labels, GraphTheme theme, double zoom, Dimension size, GraphPaintState paintState)`; `private static int differentPixels(BufferedImage first, BufferedImage second)`; `private static GraphTheme lightTheme()` and existing `SIZE=240x140`, `FIRST_MAP`.
- Consumes: `GraphTheme.labelFont(): Font`, `denseLabelFont(): Font`; `LabelFonts.from(final GraphTheme theme): LabelFonts`; `ScreenLabelPlacement.place(final LabelPlacementRequest request, final List<PlacedLabel> previous, final LabelFonts fonts): List<PlacedLabel>`; `ScreenLabelPlacement.textWidth(final String text, final Font font): double`; `ScreenLabelPlacement.slotMaxWidth(final Slot slot): double`.
- Consumes stable persisted key construction, `ProjectedNode.of(ProjectedNodeKey,SafeNodeLabel,String,boolean)`, `GraphProjection.projected(long,List<ProjectedNode>,List<ProjectedEnclosure>,List<ProjectedEdge>,List<RelationshipResolution>,List<PinProjection>)`, `LayoutFrame.of(long,LayoutPositions,boolean,int)`, `CanvasState.of(long,GraphProjection,LayoutFrame,GraphGeometry,EdgePaths,OperationalStatus)`, `GraphGeometry.of(Map<ProjectedNodeKey,NodeGeometry>,Map<EnclosureHullKey,HullGeometry>)`, `LayoutPositions.of(Map<ProjectedNodeKey,LayoutPoint>,Map<EnclosureHullKey,LayoutPoint>)`.
- Produces exact JUnit signatures: `@Test public void paintsTheCurrentTitleOnAReusedCanvasLikeAnAgreeingFreshCanvas() throws Exception`; `@Test public void paintsARefreshedRetainedFaceInsteadOfThePreviousTitleOnAReusedCanvas() throws Exception`.
- Produces exact private helper signatures: `private static CanvasState liveTitleState(final long generation, final String displayText, final LayoutPoint center)`; `private static GraphCanvas liveTitleCanvas(final CanvasState state, final GraphTheme theme)`; `private static LabelPlacementRequest liveTitleRequest(final CanvasState state)`. No cache access seam, production font factory or production change.

- [ ] **Step 1: Restate the clarified T8 diverging-control rule**

The clarified rule, from specification commit 7df2d081a67c0363bc9a2061b0261907d45d583c: the retained generation-2
placement of `alpha`, given the generation-1 placement as previous, keeps the remembered slot and takes the best
face that fits that slot; that is placement of `alpha` restricted to the remembered slot, not a full fresh
placement. The fresh placement of `alpha` with no previous labels differs from that retained result in slot or
face. Do not alter source, design or specification files and do not change the retention algorithm.

The following implementation is the concrete target for that rule: retain the remembered slot, derive its current
best full-font face, demonstrate a different fresh choice, and compare reused pixels against independently
computed retained labels and stale labels.

- [ ] **Step 2: Add the three complete local fixture helpers**

All required imports already exist in this test class. Insert only new members; keep existing helper behavior and assertions untouched. These helpers are called on EDT by both tests.

```java
private static CanvasState liveTitleState(final long generation,
        final String displayText, final LayoutPoint center) {
    final ProjectedNodeKey key = ProjectedNodeKey.of(SourceNodeKey.persisted(
        NodeReference.of(FIRST_MAP, PersistedNodeId.of("live-title-edited"))));
    final ProjectedNode node = ProjectedNode.of(key,
        SafeNodeLabel.of(displayText, displayText), "Map", false);
    final GraphProjection projection = GraphProjection.projected(generation,
        Collections.singletonList(node), Collections.<ProjectedEnclosure>emptyList(),
        Collections.<ProjectedEdge>emptyList(), Collections.<RelationshipResolution>emptyList(),
        Collections.<PinProjection>emptyList());
    final Map<ProjectedNodeKey, LayoutPoint> positions = Collections.singletonMap(key, center);
    final LayoutPositions layoutPositions = LayoutPositions.of(positions,
        Collections.<EnclosureHullKey, LayoutPoint>emptyMap());
    final LayoutFrame layout = LayoutFrame.of(generation, layoutPositions, false, 0);
    final GraphGeometry geometry = GraphGeometry.of(
        Collections.singletonMap(key, NodeGeometry.of(center, 8.0)),
        Collections.<EnclosureHullKey, HullGeometry>emptyMap());
    return CanvasState.of(generation, projection, layout, geometry, EdgePaths.empty(),
        OperationalStatus.IDLE);
}

private static GraphCanvas liveTitleCanvas(final CanvasState state, final GraphTheme theme) {
    assertThat(SwingUtilities.isEventDispatchThread()).isTrue();
    final GraphCanvas canvas = new GraphCanvas();
    canvas.setTheme(theme);
    canvas.setViewport(GraphViewport.of(0.0, 0.0, 1.0));
    canvas.setPaintState(GraphPaintState.empty());
    canvas.setCanvasState(state);
    canvas.setSize(SIZE);
    return canvas;
}

private static LabelPlacementRequest liveTitleRequest(final CanvasState state) {
    return LabelPlacementRequest.of(state.projection(), state.geometry(), state.layout().positions(),
        1.0, 0.0, 0.0, new Rectangle2D.Double(0.0, 0.0, SIZE.width, SIZE.height),
        Collections.<ProjectedEndpointKey>emptySet(), RenderingLevel.FULL);
}
```

- [ ] **Step 3: Add the complete agreeing-fresh-control test**

This test has no unresolved assertion. Direct placement proves fresh equality is valid for this fixture before any image equality is asserted. Endpoint selection uses the persisted key rather than a list index/title.

```java
@Test
public void paintsTheCurrentTitleOnAReusedCanvasLikeAnAgreeingFreshCanvas()
        throws Exception {
    SwingUtilities.invokeAndWait(() -> {
        final GraphTheme theme = lightTheme();
        final LabelFonts fonts = LabelFonts.from(theme);
        final CanvasState first = liveTitleState(1L, "alpha", LayoutPoint.of(0.0, 0.0));
        final CanvasState second = liveTitleState(2L, "omega renamed", LayoutPoint.of(0.0, 0.0));
        final ProjectedEndpointKey key = ProjectedEndpointKey.ofNode(
            ProjectedNodeKey.of(SourceNodeKey.persisted(NodeReference.of(FIRST_MAP,
                PersistedNodeId.of("live-title-edited")))));
        final ScreenLabelPlacement placement = new ScreenLabelPlacement();
        final List<PlacedLabel> oldLabels = placement.place(liveTitleRequest(first), null, fonts);
        final List<PlacedLabel> retainedLabels = placement.place(liveTitleRequest(second), oldLabels, fonts);
        final List<PlacedLabel> freshLabels = placement.place(liveTitleRequest(second), null, fonts);
        final PlacedLabel retained = retainedLabels.stream()
            .filter(label -> label.endpoint().equals(key)).findFirst().get();
        final PlacedLabel fresh = freshLabels.stream()
            .filter(label -> label.endpoint().equals(key)).findFirst().get();
        assertThat(retained.endpoint()).isEqualTo(fresh.endpoint());
        assertThat(retained.text()).isEqualTo("omega renamed");
        assertThat(fresh.text()).isEqualTo(retained.text());
        assertThat(retained.sourceText()).isEqualTo("omega renamed");
        assertThat(fresh.sourceText()).isEqualTo(retained.sourceText());
        assertThat(retained.font()).isEqualTo(theme.labelFont());
        assertThat(fresh.font()).isEqualTo(retained.font());
        assertThat(retained.rung()).isEqualTo(PlacedLabel.Rung.FULL_NEAR);
        assertThat(fresh.rung()).isEqualTo(retained.rung());
        assertThat(retained.slot()).isEqualTo(ScreenLabelPlacement.Slot.ABOVE);
        assertThat(fresh.slot()).isEqualTo(retained.slot());
        assertThat(retained.mode()).isEqualTo(PlacedLabel.Mode.INTERIOR);
        assertThat(fresh.mode()).isEqualTo(retained.mode());
        assertThat(retained.truncated()).isFalse();
        assertThat(fresh.truncated()).isFalse();
        assertThat(new double[] {retained.width(), retained.height(), retained.anchorX(), retained.anchorY()})
            .isEqualTo(new double[] {fresh.width(), fresh.height(), fresh.anchorX(), fresh.anchorY()});
        assertThat(retained.leader()).isEmpty();
        assertThat(fresh.leader()).isEmpty();
        final GraphCanvas reusedCanvas = liveTitleCanvas(first, theme);
        assertThat(reusedCanvas.getSize()).isEqualTo(SIZE);
        final BufferedImage firstImage = paintCanvas(reusedCanvas);
        reusedCanvas.setCanvasState(second);
        assertThat(reusedCanvas.getSize()).isEqualTo(SIZE);
        final BufferedImage reusedImage = paintCanvas(reusedCanvas);
        final GraphCanvas freshCanvas = liveTitleCanvas(second, theme);
        final BufferedImage freshImage = paintCanvas(freshCanvas);
        assertThat(differentPixels(firstImage, reusedImage)).isGreaterThan(0);
        assertThat(differentPixels(reusedImage, freshImage)).isZero();
    });
}
```

All setup/state replacement/painting occurs in `invokeAndWait` and EDT assertion failures propagate to JUnit through it. The same Canvas A is painted before and after replacement. No theme/viewport reset between frames, containing scroll pane, pan, hover, zoom, cache clear or new component replaces its cache. Actual component size remains SIZE after state replacement.

- [ ] **Step 4: Add the diverging-control test for the clarified rule**

Measure the actual theme's logical SansSerif faces. Failure to satisfy full>200/dense<=200 is a failing fixture with both metrics in its message, never a skip or adjusted expectation. Select the first listed centre where direct old/fresh alpha choices differ and report all discovered rows/slots if none does. Then assert retained keeps the remembered slot with its best fitting face, and that the fresh placement differs from it in slot or face; a host-dependent collapse fails explicitly. This code implements design T8, specification 10.11 and the stale/refreshed-image assertions.

```java
@Test
public void paintsARefreshedRetainedFaceInsteadOfThePreviousTitleOnAReusedCanvas()
        throws Exception {
    SwingUtilities.invokeAndWait(() -> {
        final GraphTheme theme = lightTheme();
        final LabelFonts fonts = LabelFonts.from(theme);
        String oldTitle = null;
        double fullWidth = 0.0;
        double denseWidth = 0.0;
        for (int n = 20; n <= 200; n++) {
            final char[] characters = new char[n];
            Arrays.fill(characters, 'W');
            final String candidate = new String(characters);
            fullWidth = ScreenLabelPlacement.textWidth(candidate, theme.labelFont());
            denseWidth = ScreenLabelPlacement.textWidth(candidate, theme.denseLabelFont());
            if (fullWidth > ScreenLabelPlacement.slotMaxWidth(ScreenLabelPlacement.Slot.ABOVE)) {
                oldTitle = candidate;
                break;
            }
        }
        assertThat(oldTitle).as("bounded title search: full=%s dense=%s", fullWidth, denseWidth)
            .isNotNull();
        assertThat(fullWidth).as("full=%s dense=%s", fullWidth, denseWidth).isGreaterThan(200.0);
        assertThat(denseWidth).as("full=%s dense=%s", fullWidth, denseWidth)
            .isLessThanOrEqualTo(200.0);
        final ProjectedEndpointKey key = ProjectedEndpointKey.ofNode(
            ProjectedNodeKey.of(SourceNodeKey.persisted(NodeReference.of(FIRST_MAP,
                PersistedNodeId.of("live-title-edited")))));
        final ScreenLabelPlacement placement = new ScreenLabelPlacement();
        final List<LayoutPoint> candidates = Arrays.asList(LayoutPoint.of(-40.0, 0.0),
            LayoutPoint.of(-30.0, 0.0), LayoutPoint.of(-20.0, 0.0), LayoutPoint.of(-50.0, 0.0));
        final List<String> observed = new ArrayList<String>();
        LayoutPoint chosen = null;
        for (final LayoutPoint candidate : candidates) {
            final List<PlacedLabel> old = placement.place(
                liveTitleRequest(liveTitleState(1L, oldTitle, candidate)), null, fonts);
            final List<PlacedLabel> fresh = placement.place(
                liveTitleRequest(liveTitleState(2L, "alpha", candidate)), null, fonts);
            final PlacedLabel oldLabel = old.stream()
                .filter(label -> label.endpoint().equals(key)).findFirst().get();
            final PlacedLabel freshLabel = fresh.stream()
                .filter(label -> label.endpoint().equals(key)).findFirst().get();
            observed.add(candidate + ": old=" + oldLabel.rung() + "/" + oldLabel.slot()
                + " fresh=" + freshLabel.rung() + "/" + freshLabel.slot());
            if (oldLabel.rung() != freshLabel.rung() || oldLabel.slot() != freshLabel.slot()) {
                chosen = candidate;
                break;
            }
        }
        assertThat(chosen).as("diverging candidate rows/slots: %s", observed).isNotNull();
        final CanvasState first = liveTitleState(1L, oldTitle, chosen);
        final CanvasState second = liveTitleState(2L, "alpha", chosen);
        final List<PlacedLabel> oldLabels = placement.place(liveTitleRequest(first), null, fonts);
        final List<PlacedLabel> refreshedLabels = placement.place(liveTitleRequest(second), oldLabels, fonts);
        final List<PlacedLabel> freshLabels = placement.place(liveTitleRequest(second), null, fonts);
        final PlacedLabel oldLabel = oldLabels.stream()
            .filter(label -> label.endpoint().equals(key)).findFirst().get();
        final PlacedLabel refreshed = refreshedLabels.stream()
            .filter(label -> label.endpoint().equals(key)).findFirst().get();
        final PlacedLabel fresh = freshLabels.stream()
            .filter(label -> label.endpoint().equals(key)).findFirst().get();
        assertThat(oldLabel.mode()).isNotEqualTo(PlacedLabel.Mode.HOVER_ONLY);
        assertThat(refreshed.mode()).isEqualTo(PlacedLabel.Mode.INTERIOR);
        assertThat(refreshed.text()).isEqualTo("alpha");
        assertThat(refreshed.sourceText()).isEqualTo("alpha");
        assertThat(refreshed.font()).isEqualTo(theme.labelFont());
        assertThat(refreshed.truncated()).isFalse();
        assertThat(refreshed.slot()).isEqualTo(oldLabel.slot());
        final boolean far = oldLabel.slot() == ScreenLabelPlacement.Slot.ABOVE_FAR
            || oldLabel.slot() == ScreenLabelPlacement.Slot.BELOW_FAR
            || oldLabel.slot() == ScreenLabelPlacement.Slot.RIGHT_FAR
            || oldLabel.slot() == ScreenLabelPlacement.Slot.LEFT_FAR;
        assertThat(refreshed.rung()).isEqualTo(far
            ? PlacedLabel.Rung.FULL_DISPLACED : PlacedLabel.Rung.FULL_NEAR);
        assertThat(refreshed.rung() != fresh.rung() || refreshed.slot() != fresh.slot())
            .as("retained=%s/%s fresh=%s/%s; candidates=%s", refreshed.rung(),
                refreshed.slot(), fresh.rung(), fresh.slot(), observed).isTrue();
        final GraphCanvas reusedCanvas = liveTitleCanvas(first, theme);
        assertThat(reusedCanvas.getSize()).isEqualTo(SIZE);
        final BufferedImage firstImage = paintCanvas(reusedCanvas);
        reusedCanvas.setCanvasState(second);
        assertThat(reusedCanvas.getSize()).isEqualTo(SIZE);
        final BufferedImage reusedImage = paintCanvas(reusedCanvas);
        final BufferedImage staleImage = paintPlaced(second, oldLabels, theme, 1.0, SIZE,
            GraphPaintState.empty());
        final BufferedImage expectedImage = paintPlaced(second, refreshedLabels, theme, 1.0, SIZE,
            GraphPaintState.empty());
        assertThat(differentPixels(firstImage, reusedImage)).isGreaterThan(0);
        assertThat(differentPixels(reusedImage, staleImage)).isGreaterThan(0);
        assertThat(differentPixels(reusedImage, expectedImage)).isZero();
    });
}
```

Do not assert equality with a fresh canvas in this diverging fixture. Both image controls use generation-2 state, the same theme font instances, viewport and actual SIZE; the stale control differs only in receiving generation-1 labels. No pixel hash, reflection, cache exposure or production font injection.

- [ ] **Step 5: Verify all focused classes and preserve existing paint assertions**

After the focused run above, run:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" \
PATH="$HOME/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH" \
gradle :freeplane_plugin_graph:test --rerun-tasks -PTestLoggingFull \
  --tests 'org.freeplane.plugin.graph.canvas.ScreenLabelLiveTitleRefreshShould' \
  --tests 'org.freeplane.plugin.graph.canvas.ScreenLabelPlacementShould' \
  --tests 'org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould' \
  --tests 'org.freeplane.plugin.graph.integration.GraphWorkspaceLiveNodeRefreshShould'
```

Require exit0 and XML with zero failures/errors, both named T8 tests actually executed, and all previous paint assertions unchanged/green. Theme-dependent fixture failures are failures, not a reason to weaken expected output or pin host fonts. Preserve original FULL_DISPLACED/RIGHT synthetic helper.

- [ ] **Step 6: Commit**

Only after the verification steps above pass:

```bash
git diff --check
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java
git diff --cached --check
git commit -m "2026-11-01-graph-workspace-live-title-refresh: verify reused canvas live title pixels"
```

## Task 5: Verify the full ordinary module suite and implementation boundaries

**Implementer tier:** Standard

**Lane:** live-title-refresh

**Dependencies:** Task 4 committed and all focused evidence passing.

**Files:**

- Read/review only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/PlacedLabel.java`
- Read/review only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacement.java`
- Read/test only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/ScreenLabelLiveTitleRefreshShould.java`
- Read/test only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java`
- Protected read-only oracle: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacementShould.java`
- No source or evidence file creation/edit is authorized by this verification-only task. Return evidence through the existing PM/controller channel. A needed repair must be sent to the owner of the relevant preceding task, not smuggled into verification.

**Interfaces:**

- Consumes the final mandatory package-private constructor `PlacedLabel(ProjectedEndpointKey endpoint, String text, Font font, Mode mode, Rung rung, double anchorX, double anchorY, double width, double height, boolean truncated, String sourceText, boolean forced, boolean emphaticAtAnchor, boolean forcedAtBaseSlot, boolean fullTextSlotWasFree, Optional<LeaderLine> leader, ScreenLabelPlacement.Slot slot)`, field `final String sourceText` and accessor `String sourceText()`; unchanged public `PlacedLabel` declarations.
- Consumes `ScreenLabelPlacement.retained(final Context context, final ProjectedNodeKey key, final PlacedLabel previous): PlacedLabel` and unchanged public `place(final LabelPlacementRequest request, final List<PlacedLabel> previous, final LabelFonts fonts): List<PlacedLabel>`.
- Consumes all 31 no-argument public void tests in `ScreenLabelLiveTitleRefreshShould` plus `paintsTheCurrentTitleOnAReusedCanvasLikeAnAgreeingFreshCanvas() throws Exception` and `paintsARefreshedRetainedFaceInsteadOfThePreviousTitleOnAReusedCanvas() throws Exception`.
- Produces no Java API. Produces a PM result with actual focused/full command exits, executed class/test counts, failures/errors/skips and XML presence, the boundary review result, and the clarified T8 control result. No invented historical test count or unrun command is evidence.

- [ ] **Step 1: Run the focused verification vector and read its current XML immediately**

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" \
PATH="$HOME/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH" \
gradle :freeplane_plugin_graph:test --rerun-tasks -PTestLoggingFull \
  --tests 'org.freeplane.plugin.graph.canvas.ScreenLabelLiveTitleRefreshShould' \
  --tests 'org.freeplane.plugin.graph.canvas.ScreenLabelPlacementShould' \
  --tests 'org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould' \
  --tests 'org.freeplane.plugin.graph.integration.GraphWorkspaceLiveNodeRefreshShould'
```

Exact acceptance: command exit0, all four named classes executed, 31 required placement/cache methods and both required canvas methods present/executed, no failures/errors or skipped new contracts. Read `freeplane_plugin_graph/build/test-results/test/TEST-*.xml`; record focused totals before the full command overwrites reports. Run this exact read-only XML evidence command immediately after the focused command, then run it again after Step 2's full-module command. Current existing test counts come from XML, not estimates. The script checks every required method exactly once and rejects skipped new contracts.

```bash
python3 - <<'PY'
from collections import Counter
from pathlib import Path
import json
import xml.etree.ElementTree as ET

root = Path('freeplane_plugin_graph/build/test-results/test')
files = sorted(root.glob('TEST-*.xml'))
assert files, 'Missing current Gradle XML results'
reports = {}
totals = dict(tests=0, failures=0, errors=0, skipped=0)
for path in files:
    suite = ET.parse(path).getroot()
    assert suite.tag == 'testsuite', str(path)
    name = suite.attrib['name']
    assert name not in reports, 'Duplicate suite: ' + name
    counts = {key: int(suite.attrib.get(key, '0')) for key in totals}
    assert counts['failures'] == 0 and counts['errors'] == 0, (name, counts)
    cases = suite.findall('testcase')
    reports[name] = (counts, cases)
    for key in totals:
        totals[key] += counts[key]
    print(json.dumps(dict(suite=name, **counts), sort_keys=True))

placement_class = 'org.freeplane.plugin.graph.canvas.ScreenLabelLiveTitleRefreshShould'
canvas_class = 'org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould'
required_placement = '''
refreshesTheRetainedTitleWithAStableNodeIdentity
refreshesTheTitleThroughTheSamePlacementCache
keepsAnUnchangedFaceOnImmediateReplayAndAfterRetainedHistory
keepsAValidSlotWhenAFreshPlacementWouldChooseAnother
declinesAnObstructedUnchangedFaceWithoutTryingDenserRungs
fallsBackUnderEarlierRetainedLabelObstacles
upgradesAShortenedTitleAtItsRememberedSlot
degradesAnExpandedTitleAtItsRememberedSlot
reLaddersWhenNoFaceFitsTheRememberedSlot
retainsOnlyFacesFromTheRememberedSlotFamily
declinesAForcedSlotInsteadOfTakingADenseFace
rejectsDenseAndTruncatedFacesWhenPromotedToForced
keepsAnUnchangedForcedOverCapFace
rederivesAChangedForcedTitleWithoutApplyingTheSlotCap
detectsATitleEditHiddenByAnIdenticalTruncation
recordsTheDisplaySourceOnTruncatedAndRetainedOutputs
clearsTruncationWhenTheTitleBecomesItsPreviousRendering
recordsDisplayTextWhenProjectionFullTextDiffers
recordsSourceTextAtAllPlacementConstructionSites
keepsAnOverCapFaceAfterDemotionAndOnTheNextReplay
keepsAnUnchangedTruncatedFaceWhenSpaceBecomesAvailable
advancesTheSourceWhenTwoTitlesRenderTheSameTruncation
ignoresAnEditConfinedToTheProjectionHiddenTail
remeasuresWidthAfterASameFontTitleEdit
remeasuresHeightAndAnchorAfterAFaceTransition
recomputesBothEndsOfADiagonalLeaderAfterRetitling
declinesAnExpandedBoxAtADiscWithoutLeavingAnObstacle
declinesAnExpandedBoxAtAnEarlierLabelWithoutLeavingAnObstacle
declinesAnExpandedBoxOutsideTheAreaWithoutLeavingAnObstacle
rederivesAtTheRememberedSlotWhenTheFaceFontChanges
reLaddersAPreviouslyHoverOnlyNodeFromItsCurrentTitle
'''.split()
required_canvas = [
    'paintsTheCurrentTitleOnAReusedCanvasLikeAnAgreeingFreshCanvas',
    'paintsARefreshedRetainedFaceInsteadOfThePreviousTitleOnAReusedCanvas',
]
assert len(required_placement) == len(set(required_placement)) == 31
for name in (placement_class, canvas_class,
        'org.freeplane.plugin.graph.canvas.ScreenLabelPlacementShould',
        'org.freeplane.plugin.graph.integration.GraphWorkspaceLiveNodeRefreshShould'):
    assert name in reports, 'Required suite did not execute: ' + name
    assert reports[name][0]['tests'] > 0, 'Empty required suite: ' + name
for name, required in ((placement_class, required_placement), (canvas_class, required_canvas)):
    cases = reports[name][1]
    inventory = Counter(case.attrib['name'] for case in cases)
    for method in required:
        assert inventory[method] == 1, (name, method, inventory[method])
    for case in cases:
        if case.attrib['name'] in required:
            assert case.find('skipped') is None, (name, case.attrib['name'], 'skipped')
            assert case.find('failure') is None and case.find('error') is None
assert reports[placement_class][0]['tests'] == 31
print(json.dumps(dict(suites=len(reports), **totals), sort_keys=True))
PY
```

- [ ] **Step 2: T9 — run the entire ordinary graph-module suite without a class filter**

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" \
PATH="$HOME/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH" \
gradle :freeplane_plugin_graph:test --rerun-tasks -PTestLoggingFull
```

Exact acceptance: exit0, current XML present, ordinary task's actually selected tests executed, failures/errors zero. Keep existing exclusions of multi-hour `GraphEdgeLengthSettleShould` and `InitialSpacingAcceptanceShould`; do not add/remove exclusions, run a launcher or claim those drivers were tested. A timeout, dependency-resolution failure, hang, missing XML or solely UP-TO-DATE result is not a full-suite pass. Report actual full counts separately from focused counts.

- [ ] **Step 3: Review the exact implementation diff and protected bytes**

Use the approved specification commit as the implementation base (its parent design commit changes documentation only); restrict the implementation review to `freeplane_plugin_graph/` so the planner's new Markdown file is not mistaken for a fifth Java edit:

```bash
git diff --check 3bd99a7b96d8e80f5e58eade9c4496ccb70afbbe -- freeplane_plugin_graph/
git diff --name-only 3bd99a7b96d8e80f5e58eade9c4496ccb70afbbe -- freeplane_plugin_graph/
git diff 3bd99a7b96d8e80f5e58eade9c4496ccb70afbbe -- freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/PlacedLabel.java freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacement.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/ScreenLabelLiveTitleRefreshShould.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java
git diff --exit-code 3bd99a7b96d8e80f5e58eade9c4496ccb70afbbe -- freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacementShould.java freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacementCache.java freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphCanvas.java freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/LabelFonts.java
```

Expected Java change list is exactly these four paths, no generated output tracked. The protected-file comparison must exit 0; a nonempty diff is a failed boundary gate:


```text
freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/PlacedLabel.java
freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacement.java
freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java
freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/ScreenLabelLiveTitleRefreshShould.java
```

Assert exactly one new package-private final field and accessor, one mandatory constructor signature with no old-arity overload, current display source at all seven original sites, public declarations unchanged, no extra state/cache/face table or production helper. Outside retained, all four factory changes are argument-list additions only. `LADDER`, geometry/truncation helpers, ordering, enclosure logic, fallback caps and all excluded files remain unchanged. Existing GraphCanvasPaintShould methods retain every assertion/behavior; only their two constructor arguments change, plus new T8 members. `ScreenLabelPlacementShould` must be byte-unchanged.

Review the thirteen specification section10 resolutions explicitly: display vs full/rendering; three transitions; constructor placement; both cap scopes and demotion; one face table/family; measurement/leader/square obstacles; omit-only-one fallback control; stable IDs/cache positions identity; rendering/source truncation flag; all named tests and preservation provenance; both real-canvas controls; ordinary full-suite scope; theme-metric portability. Require the T8 literal conflict's approved disposition, not merely a note that the intended tests passed.

- [ ] **Step 4: Return precise evidence to the execution owner**

Return actual command exits, XML-derived class/test/failure/error/skip counts for each focused/full run, all 31 placement names and both canvas methods accounted for, four-file allowlist/protected-byte result, current public-API/source-assignment review, and the clarified T8 control result. If any criterion fails, report failed/blocked and stop finalization; do not weaken old assertions or label the suite passing. No optional baseline runtime probe is required. If one was separately performed, discriminate semantic failures from constructor compile errors and ensure no shim/mutant ships.

- [ ] **Step 5: Commit**

This verification-only task changes no file. After every gate above passes, record its successful verification boundary with the task identifier in a commit; do not stage unrelated or generated changes. If the index is nonempty (`git diff --cached --quiet` exits nonzero), stop before committing so another owner's staged changes cannot be included:

```bash
git diff --check
git diff --cached --check
git diff --cached --quiet
git commit --allow-empty -m "2026-11-01-graph-workspace-live-title-refresh: verify ordinary graph module suite"
```
