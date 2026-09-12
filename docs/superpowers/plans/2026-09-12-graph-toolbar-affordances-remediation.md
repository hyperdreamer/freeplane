# Graph Toolbar Affordances — Remediation Plan (final-review F-10, F-14)

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for readability; controller state is canonical.

**Goal:** Close the two in-lane residuals of the final frontier review of the graph toolbar affordances lane: a disabled toolbar glyph must be visibly distinguishable from an enabled one (F-10, Important, load-bearing), and the no-icon search-prompt paint variant the specification already specifies must be pinned by a test (F-14, Minor).

**Architecture:** Both changes are local to `org.freeplane.plugin.graph.window`. Task 1 extends the shared `WorkspaceToolbar.configureIcon(...)` routine so every icon-only control it builds also receives a derived disabled icon (the SVG pipeline returns non-`ImageIcon` instances, which FlatLaf leaves unchanged when a button is disabled); Task 2 adds one paint-output test for the `GraphSearchField` prompt rendered without a prompt icon. The two tasks share `GraphWorkspaceWindowModelShould.java`, so they run in one lane, in order.

**Tech Stack:** Java 8 (class major 52), Swing, JUnit 4 with AssertJ, Gradle (`:freeplane_plugin_graph:test`), Freeplane OSGi plugin `freeplane_plugin_graph` (delivery branch `plugin/graph-workspace`).

Requirement coverage: this plan discharges final-review finding **F-10** in Task 1 and **F-14** in Task 2. It also carries the parked residuals recorded in the design/spec amendment committed with it: F-11 (HiDPI prompt clipping at `flatlaf.uiScale=2`, accepted residual), F-12 (plan test-count arithmetic, corrected in the amendment), F-13 (pre-existing `loadClass` classpath shadowing in the OSGi smoke, recorded as an out-of-lane follow-up). F-1 was already fixed and verified in the parent lane and must be re-reconciled by the final reviewer of the new run.

## Global Constraints

- Java 8 source and bytecode target (class major version 52); UTF-8; four-space indentation; `final` parameters matching the surrounding `org.freeplane.plugin.graph` style.
- Use `JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu` and the repository `gradle` (never `gradlew`), from the repository root; add `-PTestLoggingFull` for verbose failures.
- Never modify or delete an existing test method or assertion; only append new test methods, imports and private helpers.
- Production changes are confined to `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceToolbar.java`; the only test file that may change is `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java`.
- The enabled rendering must not change: each icon-only control keeps exactly the icon resolved from its existing path, and `getName()`, tooltip, accessible name, `configure(...)` margins and focusability stay as they are.
- No new hardcoded colour: the disabled treatment is derived from the same icon instance by alpha reduction (`DISABLED_ICON_ALPHA = 0.45f`), not from a colour constant.
- No new dependency, no `com.formdev.flatlaf` import, no custom painting of the button itself.
- Resource keys, control names, the zero-gap `ToolSwitch`, the `graph-workspace-tool-switch` name, the `"JButton.buttonType" = "toolBarButton"` property and the four SVG assets are unchanged by this plan.
- Expected suite counts after this plan: `GraphWorkspaceWindowModelShould` 74 (72 existing plus 2 new), whole plugin suite 912 (910 existing plus 2 new); `hidesSearchPromptWhileFocused` is skipped when the JVM is headless.
- Verification commands, run exactly as written: `gradle :freeplane_plugin_graph:test -PTestLoggingFull`; `gradle :freeplane_plugin_graph:verifyGraphBundle`; `gradle :freeplane_plugin_graph:graphOsgiSmoke`.
- The commit message form is exactly `<type>: <subject> [2026-09-12-graph-toolbar-affordances]`.

## Task 1: Give disabled toolbar glyphs a visible disabled treatment

**Implementer tier:** Standard
**Lane:** graph-toolbar-remediation

**Files:**
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceToolbar.java` (`configureIcon`, the shared icon routine added by task 1 of the parent lane)
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java` (append only)

**Interfaces:**
- Consumes: `private static void WorkspaceToolbar.configureIcon(AbstractButton button, String labelTextKey, String tooltipTextKey, String iconPath)`; `ResourceController.getOptionalIcon(String)`; the existing fixture helpers `fixture(..., boolean)`, `fixture.stubIcon(String path, Icon icon)`.
- Produces: `private static final float WorkspaceToolbar.DISABLED_ICON_ALPHA = 0.45f`; `private static Icon WorkspaceToolbar.disabledIconOf(Icon icon)`; the behaviour that every icon-only control built by `configureIcon` returns a non-null `getDisabledIcon()` distinct from `getIcon()`; `GraphWorkspaceWindowModelShould.dimsDisabledToolbarGlyphs()`.

**Steps:**

- [ ] **Step 1: Write the failing test.** Append to `GraphWorkspaceWindowModelShould.java`:

```java
    @Test
    public void dimsDisabledToolbarGlyphs() {
        final Icon stub = paintingIcon(DISABLED_PROBE_COLOR, 16, 16);
        final Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false,
            iconStubs("/images/GraphSelect.svg?useAccentColor=true",
                "/images/GraphConnect.svg?useAccentColor=true", stub));
        final WorkspaceToolbar toolbar = fixture.model().toolbar();
        for (final AbstractButton button : new AbstractButton[] {
                toolbar.selectButton(), toolbar.connectButton() }) {
            assertThat(button.getIcon()).isNotNull();
            assertThat(button.getDisabledIcon()).as("%s disabled icon", button.getName()).isNotNull();
            assertThat(button.getDisabledIcon()).isNotSameAs(button.getIcon());
            assertThat(averageLuminance(button.getDisabledIcon())).as("%s dimmed", button.getName())
                .isLessThan(averageLuminance(button.getIcon()));
        }
    }
```

Add the private helpers `paintingIcon(Color, int, int)`, `iconStubs(String, String, Icon)` (or reuse the existing stub plumbing if it already accepts several paths) and `averageLuminance(Icon)` (render the icon into a `BufferedImage` of its size and average the alpha-weighted luminance of its pixels), following the existing paint-capture helpers in that class.

- [ ] **Step 2: Observe the failure.** `gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould" -PTestLoggingFull --rerun-tasks` must fail on `getDisabledIcon()` being null for `graph-workspace-select` (record the exact assertion message).

- [ ] **Step 3: Implement the disabled icon.** In `WorkspaceToolbar`, next to `configureIcon`, add:

```java
    private static final float DISABLED_ICON_ALPHA = 0.45f;

    private static Icon disabledIconOf(final Icon icon) {
        final int width = icon.getIconWidth();
        final int height = icon.getIconHeight();
        if (width <= 0 || height <= 0) {
            return icon;
        }
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = image.createGraphics();
        try {
            icon.paintIcon(null, graphics, 0, 0);
        }
        finally {
            graphics.dispose();
        }
        final float[] scales = { 1f, 1f, 1f, DISABLED_ICON_ALPHA };
        return new ImageIcon(applyAlpha(image, scales));
    }
```

with a small private `applyAlpha(BufferedImage, float[])` that returns a new `BufferedImage` produced by `new RescaleOp(scales, new float[4], null).filter(image, null)`, or equivalent direct pixel copying when `RescaleOp` is unavailable headlessly. Then, inside `configureIcon`, when the icon resolves, also call `button.setDisabledIcon(disabledIconOf(icon))` before `configure(button, name)`.

- [ ] **Step 4: Observe the pass.** Re-run the Step 2 command; `dimsDisabledToolbarGlyphs` must pass and no existing test may change its result.

- [ ] **Step 5: Scoped verification and commit.** Run `gradle :freeplane_plugin_graph:test -PTestLoggingFull` (expect `GraphWorkspaceWindowModelShould` 73 tests: 72 existing plus this one, whole suite 911, 0 failures, 0 errors) and `gradle :freeplane_plugin_graph:verifyGraphBundle`; then commit with `feat: dim disabled toolbar glyphs [2026-09-12-graph-toolbar-affordances]`.

## Task 2: Pin the no-icon search prompt paint variant

**Implementer tier:** Fast
**Lane:** graph-toolbar-remediation

**Files:**
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java` (append only)

**Interfaces:**
- Consumes: `GraphSearchField(GraphSearchField)`, its `setPrompt(String)`, `setPromptIcon(Icon)`, `getPreferredSize()`, `getInsets()`, `getFontMetrics(Font)`, `getDisabledTextColor()`; the paint-capture helpers added by task 6 of the parent lane (`renderComponent`, `pixelsMatching`) or their equivalents in that class.
- Produces: `GraphWorkspaceWindowModelShould.paintsTheSearchPromptWithoutAMagnifier`.

**Steps:**

- [ ] **Step 1: Write the failing test.** Append a test that builds a fresh `GraphSearchField` inside the existing `EdtResources` scope, calls `field.setPrompt("Search nodes and maps")` and `field.setPromptIcon(null)`, sizes it with `field.setSize(field.getPreferredSize())`, renders it to a `BufferedImage`, and asserts: prompt-coloured pixels (the field's `getDisabledTextColor()` or an anti-aliased blend of it) are present at `x >= field.getInsets().left`; no pixel carries a stub colour (no prompt icon was installed); `field.getPreferredSize().width == Math.max(160, field.getInsets().left + field.getFontMetrics(field.getFont()).stringWidth("Search nodes and maps") + field.getInsets().right)`; `field.getPreferredSize().height == 26`; and `field.getText().isEmpty()`.

- [ ] **Step 2: Observe the failure.** Run `gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould" -PTestLoggingFull --rerun-tasks` and record the exact failure. The spec (§5 E2/E3) requires this variant; the parent lane's task 2 always installed a 16×16 stub, so the assertion must fail first for a reason you can name (for example a helper that assumes an icon is installed, or a missing import), not merely because a method does not exist.

- [ ] **Step 3: Make it pass without touching production code.** The production behaviour is already correct (`paintComponent` guards `promptIcon != null`, `getPreferredSize()` handles a null icon); this task pins it. If a helper needs a null-icon-safe overload, add it to the test class only. Do not change `GraphSearchField.java`; if the assertion fails for a real production reason, stop and report it as a finding instead of adjusting the assertion.

- [ ] **Step 4: Scoped verification and commit.** Run `gradle :freeplane_plugin_graph:test -PTestLoggingFull`: `GraphWorkspaceWindowModelShould` must report 74 tests (72 existing plus the two added by this plan), the whole suite 912, with 0 failures and 0 errors. Commit with `test: pin the no-icon search prompt variant [2026-09-12-graph-toolbar-affordances]`.
