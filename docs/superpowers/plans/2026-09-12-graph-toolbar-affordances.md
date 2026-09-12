# Graph Workspace Toolbar Affordances Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking; controller state is canonical.

**Goal:** Give the Graph Workspace toolbar the approved Select/Connect grouped switch, the in-field `Search nodes and maps` prompt with a persistent magnifier, and a settings gear that reflects the settings panel's visibility, with four new accent-aware SVG glyphs and no new UI colour, dependency or behaviour change.

**Architecture:** Six sequential tasks in one lane. Two new package-private `window` classes (`ToolSwitch`, `GraphSearchField`) sit behind `WorkspaceToolbar`, which gains one shared `configureIcon` routine, an `iconToggle` builder, the raw-string `"JButton.buttonType" = "toolBarButton"` segment style and the `setSettingsVisible` push; `GraphWorkspaceWindowModel` owns the gear/panel synchronization; four plugin SVGs resolve through `ResourceController.getOptionalIcon`; the resource values, source guards, OSGi asset proof and regenerated UI evidence close the verification matrix. One lane is required because Tasks 1-3 all edit `WorkspaceToolbar.java` and append tests to `GraphWorkspaceWindowModelShould.java`, and Tasks 4-6 read the state those tasks leave behind.

**Tech Stack:** Java 8 (class major version 52), Swing (`JToggleButton`, `ButtonGroup`, `GridLayout`, `JTextField` painting), `ResourceController.getOptionalIcon` / `IconFactory` / `toolbar_icon_height`, JUnit 4 with AssertJ and Mockito, Gradle plugin project `freeplane_plugin_graph`, OSGi plugin via Knopflerfish 8.0.11.

Requirement coverage (design R1-R16): Task 1 implements R1, R2, R12 and R16's styling contract, plus R3/R4/R5 for Select and Connect (spec test items 1 and 8); Task 2 implements R4's search prompt, R6, R7, R8 and R13's field painting (spec items 4 and 5; item 9's search suites are re-run in its Step 4); Task 3 implements R3/R4/R5 for the gear, R9, R10, R11's unchanged wiring and R16's negative assertion (spec items 2, 3, 6, 7 and 8b); Task 4 implements R14 and the source guards of items 1 and 8b (spec item 3b); Task 5 implements R5's asset half and spec item 11 through `bundle.getResource` (its unmocked-resolution sub-item is blocked, see Task 5); Task 6 implements R15 and spec items 10 and 12, and re-runs the item 9 suites (all existing search suites) in its full-suite command. R11 and R12 are additionally re-proven by Task 6's full plugin suite.

## Global Constraints

- Java 8 source and bytecode target (class major version 52); do not use APIs newer than Java 8; UTF-8 encoding; four-space indentation; `final` parameters matching the surrounding `org.freeplane.plugin.graph` style.
- Use `JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu` and the repository `gradle` (never `gradlew` or Maven), from the repository root; add `-PTestLoggingFull` for verbose failures.
- Never modify or delete an existing test method or assertion; only append new test methods, imports and private helpers. Every existing suite stays green.
- Production Java changes are confined to `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/` plus the four new SVGs and `freeplane/src/viewer/resources/translations/Resources_en.properties`; no other main source or resource file changes.
- Control names are exactly `graph-workspace-tool-switch`, `graph-workspace-select`, `graph-workspace-connect`, `graph-workspace-search`, `graph-workspace-settings`; `approvedControlNames()` keeps its 14 entries and the toolbar row has 13 direct children.
- Icon lookup strings are exactly `/images/GraphSelect.svg?useAccentColor=true`, `/images/GraphConnect.svg?useAccentColor=true`, `/images/GraphSettings.svg?useAccentColor=true`, `/images/GraphSearch.svg?useAccentColor=true`; no pixel size may be passed.
- Resource values: new `graph_workspace.tooltip.connect=Connect \u2014 create a cross-map relationship` (the em dash is the six ASCII characters `\u2014` in the file), changed `graph_workspace.tooltip.search=Search nodes and maps` and `graph_workspace.tooltip.settings=Graph settings`; `graph_workspace.tool.select=Select`, `graph_workspace.tool.connect=Connect`, `graph_workspace.action.settings=Settings` and `graph_workspace.settings.heading=Display` stay unchanged. No production Java source contains the prompt text as a literal.
- The only Look-and-Feel client property is exactly `"JButton.buttonType" = "toolBarButton"`, written as raw string literals in `applyToolbarSegmentStyle`; no `com.formdev.flatlaf` import, no new build or OSGi dependency, no custom painting and no new hardcoded UI colour.
- Fixed values: `GAP = 6`, `TRAILING_INSET = 6`, width floor `160`, height floor `26`, prompt predicate `getText().isEmpty() && !isFocusOwner()`, glyph x `getInsets().left - currentMargin().left`, glyph y `(getHeight() - icon.getIconHeight()) / 2`, prompt x `getInsets().left`, prompt baseline `getInsets().top + getFontMetrics(getFont()).getAscent()`, preferred height `max(26, iconHeight + margin.top + margin.bottom)`.
- The row-width evidence file is `build/graph-ui-evidence/row-width.txt` with six `label=<int>` lines in this order: `toolbar.layoutPreferredWidth`, `select.width`, `connect.width`, `toolSwitch.width`, `search.width`, `settings.width`; the toolbar width is `toolbar.getLayout().preferredLayoutSize(toolbar).width`, never `toolbar.getPreferredSize()`.
- Legacy-removal policy: delete the toolbar-local `ButtonGroup`, `toggleButton(...)` and the unconditional `settingsButton.setToolTipText(...)` line; keep no compatibility fallback.
- Verification commands, run exactly as written: `gradle :freeplane_plugin_graph:test -PTestLoggingFull`; `gradle :freeplane_plugin_graph:graphOsgiSmoke`; `gradle :freeplane_plugin_graph:graphUiEvidence`; `gradle format_translation` then `cd freeplane/src/viewer/resources/translations && file Resources_*.properties | grep -v "ASCII text"` (expect no output); `gradle :freeplane_plugin_graph:verifyGraphBundle`.
- Expected suite counts at the end of the plan: `GraphWorkspaceWindowModelShould` 72 (62 existing plus 10 new), `GraphPluginIntegrationShould` 12 (10 existing plus 2 new), whole plugin suite 909 (897 existing plus 12 new); `hidesSearchPromptWhileFocused` is skipped when the JVM is headless.
- Evidence and smoke harnesses run headless (`java.awt.headless=true`); never assert on real icon pixels from the real `IconFactory`, and never let a unit test install a global icon stub that leaks into another suite.
- The real-application checks (specification §7.5) are manual and recorded in the task report; a failed falsifier there is a review blocker, not an accepted residual.
- The commit message form is exactly `<type>: <subject> [2026-09-12-graph-toolbar-affordances]`.

## Task 1: Group Select and Connect in one styled tool switch

**Implementer tier:** Standard
**Lane:** graph-toolbar-affordances

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/ToolSwitch.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceToolbar.java:16-26` (remove the `ButtonGroup` import)
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceToolbar.java:56-57` (the two toggle fields)
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceToolbar.java:98-101` (group ownership)
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceToolbar.java:129-130` (row assembly)
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceToolbar.java:371-389` (icon helpers)
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java` (append after the test that ends at current line 350)

**Interfaces:**
- Consumes: `TextUtils.getText(String)`; `ResourceController.getResourceController()` and `ResourceController.getOptionalIcon(String)`; the private `WorkspaceToolbar.configure(AbstractButton button, String name)`; `button(String textKey, String name)`.
- Produces: `final class ToolSwitch extends JPanel { ToolSwitch(final JToggleButton... segments); }` (owns one `ButtonGroup`, `GridLayout(1, n, 0, 0)`, name `graph-workspace-tool-switch`, non-opaque, paints nothing); `private static JToggleButton WorkspaceToolbar.iconToggle(final String labelTextKey, final String tooltipTextKey, final String name, final String iconPath)`; `private static void WorkspaceToolbar.configureIcon(final AbstractButton button, final String labelTextKey, final String tooltipTextKey, final String iconPath)`; `private static void WorkspaceToolbar.applyToolbarSegmentStyle(final AbstractButton segment)`; `private static JButton WorkspaceToolbar.iconButton(final String textKey, final String name, final String iconPath)` (unchanged signature, now delegating to `configureIcon(button, textKey, textKey, iconPath)`); `GraphWorkspaceWindowModelShould.groupsSelectAndConnectInOneToolSwitch()`; `GraphWorkspaceWindowModelShould.switchesToolsThroughTheSharedButtonGroup()`.

- [ ] **Step 1: Write the two failing tests**

Add the imports `java.awt.GridLayout`, `javax.swing.ButtonGroup`, `javax.swing.JToggleButton` and `org.freeplane.plugin.graph.canvas.InteractionTool` to the import block of `GraphWorkspaceWindowModelShould.java`.

Insert both methods immediately after the closing brace of `keepsUndoRedoEnablementRulesAndZoomButtonsEnabledWithoutHistoryAndInReadOnlySessions` (current line 350) and before `public void growsTheScrollableSurfaceForVisibleWorldGeometry()`:

```java
    @Test
    public void groupsSelectAndConnectInOneToolSwitch() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        WorkspaceToolbar toolbar = model.toolbar();
        JToggleButton select = toolbar.selectButton();
        JToggleButton connect = toolbar.connectButton();

        assertThat(select.getParent()).isInstanceOf(ToolSwitch.class);
        assertThat(connect.getParent()).isSameAs(select.getParent());
        ToolSwitch toolSwitch = (ToolSwitch) select.getParent();
        GridLayout layout = (GridLayout) toolSwitch.getLayout();
        assertThat(layout.getRows()).isEqualTo(1);
        assertThat(layout.getColumns()).isEqualTo(2);
        assertThat(layout.getHgap()).isZero();
        assertThat(layout.getVgap()).isZero();
        assertThat(toolSwitch.getName()).isEqualTo("graph-workspace-tool-switch");
        assertThat(toolSwitch.isOpaque()).isFalse();
        assertThat(toolSwitch.getComponentCount()).isEqualTo(2);
        assertThat(toolSwitch.getComponent(0)).isSameAs(select);
        assertThat(toolSwitch.getComponent(1)).isSameAs(connect);
        ButtonGroup group = select.getModel().getGroup();
        assertThat(group).isNotNull();
        assertThat(connect.getModel().getGroup()).isSameAs(group);
        assertThat(Collections.list(group.getElements()))
            .containsExactly(select.getModel(), connect.getModel());
        assertThat(toolbar.approvedControlNames()).containsExactlyInAnyOrder(
            "open", "save", "add-map", "remove-map", "select", "connect", "direction", "search",
            "settings", "zoom-in", "zoom-out", "fit-graph", "reset-zoom", "pin");
        int toolSwitchCount = 0;
        for (Component component : toolbar.getComponents()) {
            if (component instanceof ToolSwitch) {
                toolSwitchCount++;
            }
        }
        assertThat(toolbar.getComponentCount()).isEqualTo(13);
        assertThat(toolSwitchCount).isEqualTo(1);
        model.close();
    }

    @Test
    public void switchesToolsThroughTheSharedButtonGroup() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        List<InteractionTool> tools = new ArrayList<InteractionTool>();
        model.toolbar().setToolListener(tools::add);

        model.toolbar().connectButton().doClick();
        assertThat(model.toolbar().connectButton().isSelected()).isTrue();
        assertThat(model.toolbar().selectButton().isSelected()).isFalse();
        model.toolbar().selectButton().doClick();
        assertThat(model.toolbar().selectButton().isSelected()).isTrue();
        assertThat(model.toolbar().connectButton().isSelected()).isFalse();
        assertThat(tools).containsExactly(InteractionTool.CONNECT, InteractionTool.SELECT);
        model.close();
    }
```

The first test is the falsifier for R1/R2/R12: adding the toggles directly to the toolbar fails the parent assertion, a non-zero gap fails the layout assertion, a leftover third child fails the 13-count, and a stale toolbar-local group cannot satisfy the group-identity assertion by construction.

- [ ] **Step 2: Run the tests and confirm they fail**

Run:

```bash
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould" -PTestLoggingFull --rerun-tasks
```

Expected: FAIL to compile with `cannot find symbol: class ToolSwitch`, because `ToolSwitch` does not exist yet.

- [ ] **Step 3: Create `ToolSwitch` and rewire the toolbar**

Create `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/ToolSwitch.java` with exactly this content:

```java
package org.freeplane.plugin.graph.window;

import java.awt.GridLayout;

import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

final class ToolSwitch extends JPanel {
    ToolSwitch(final JToggleButton... segments) {
        setName("graph-workspace-tool-switch");
        setOpaque(false);
        setLayout(new GridLayout(1, segments.length, 0, 0));
        final ButtonGroup group = new ButtonGroup();
        for (final JToggleButton segment : segments) {
            group.add(segment);
            add(segment);
        }
    }
}
```

In `WorkspaceToolbar.java`, delete the import line `import javax.swing.ButtonGroup;`.

Replace the two toggle field initializers:

```java
    private final JToggleButton selectButton = toggleButton("graph_workspace.tool.select", "select");
    private final JToggleButton connectButton = toggleButton("graph_workspace.tool.connect", "connect");
```

with:

```java
    private final JToggleButton selectButton = iconToggle("graph_workspace.tool.select",
        "graph_workspace.tool.select", "select", "/images/GraphSelect.svg?useAccentColor=true");
    private final JToggleButton connectButton = iconToggle("graph_workspace.tool.connect",
        "graph_workspace.tooltip.connect", "connect", "/images/GraphConnect.svg?useAccentColor=true");
```

Replace the constructor group block:

```java
        selectButton.setSelected(true);
        final ButtonGroup tools = new ButtonGroup();
        tools.add(selectButton);
        tools.add(connectButton);
```

with:

```java
        selectButton.setSelected(true);
        applyToolbarSegmentStyle(selectButton);
        applyToolbarSegmentStyle(connectButton);
```

Replace the two row entries:

```java
        add(selectButton);
        add(connectButton);
```

with:

```java
        add(new ToolSwitch(selectButton, connectButton));
```

Replace the whole existing `iconButton` method and the whole existing `toggleButton` method (current lines 371-389) with:

```java
    private static JButton iconButton(final String textKey, final String name,
            final String iconPath) {
        final JButton button = new JButton(TextUtils.getText(textKey));
        configureIcon(button, textKey, textKey, iconPath);
        configure(button, name);
        return button;
    }

    private static JToggleButton iconToggle(final String labelTextKey, final String tooltipTextKey,
            final String name, final String iconPath) {
        final JToggleButton button = new JToggleButton(TextUtils.getText(labelTextKey));
        configureIcon(button, labelTextKey, tooltipTextKey, iconPath);
        configure(button, name);
        return button;
    }

    private static void configureIcon(final AbstractButton button, final String labelTextKey,
            final String tooltipTextKey, final String iconPath) {
        button.setText(TextUtils.getText(labelTextKey));
        final Icon icon = ResourceController.getResourceController().getOptionalIcon(iconPath);
        if (icon != null) {
            button.setIcon(icon);
            button.setText(null);
            button.setToolTipText(TextUtils.getText(tooltipTextKey));
            button.getAccessibleContext().setAccessibleName(TextUtils.getText(tooltipTextKey));
        }
    }

    private static void applyToolbarSegmentStyle(final AbstractButton segment) {
        if (segment.getIcon() != null) {
            segment.putClientProperty("JButton.buttonType", "toolBarButton");
        }
    }
```

No other production edit belongs to this task: `configure(...)` stays the only place that sets name, margin and focusability, and `iconToggle` never styles.

- [ ] **Step 4: Run the focused suites and confirm they pass**

Run:

```bash
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould" --tests "org.freeplane.plugin.graph.window.UndoRoutingShould" -PTestLoggingFull --rerun-tasks
```

Expected: PASS. `GraphWorkspaceWindowModelShould` reports 64 tests (62 existing plus `groupsSelectAndConnectInOneToolSwitch` and `switchesToolsThroughTheSharedButtonGroup`); `UndoRoutingShould` reports its 3 existing tests unchanged and green, because its unstubbed `getOptionalIcon` returns null and the four existing icon buttons keep their label fallback. If a count differs, stop and report the mismatch instead of adjusting assertions.

- [ ] **Step 5: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/ToolSwitch.java \
    freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceToolbar.java \
    freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java
git commit -m "feat: group Select and Connect in one styled tool switch [2026-09-12-graph-toolbar-affordances]"
```

## Task 2: Paint the search prompt and magnifier in the toolbar field

**Implementer tier:** Standard
**Lane:** graph-toolbar-affordances

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphSearchField.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceToolbar.java:60` (the search field declaration)
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceToolbar.java:120-122` (the search-field constructor wiring)
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java` (append after `switchesToolsThroughTheSharedButtonGroup`)

**Interfaces:**
- Consumes: from Task 1, `WorkspaceToolbar.configureIcon(...)`, `WorkspaceToolbar.iconToggle(...)` and `WorkspaceToolbar.applyToolbarSegmentStyle(...)`; `ResourceController.getResourceController().getOptionalIcon(String)`; `TextUtils.getText(String)`; the existing document listener and `publishSearch()` (`WorkspaceToolbar.java:154-169` and `:342-344`) stay attached to the same field instance.
- Produces: `final class GraphSearchField extends JTextField { GraphSearchField(); void setPrompt(final String prompt); void setPromptIcon(final Icon icon); private Insets currentMargin(); @Override protected void paintComponent(final Graphics graphics); @Override public Dimension getPreferredSize(); }`; `GraphWorkspaceWindowModelShould.keepsTheSearchPromptOutOfTheDocument()`; `GraphWorkspaceWindowModelShould.paintsSearchPromptAndMagnifierFromLiveState()`; `GraphWorkspaceWindowModelShould.hidesSearchPromptWhileFocused()`.

- [ ] **Step 1: Write the three failing tests and their private helpers**

Add the imports `java.awt.BorderLayout`, `java.awt.Color`, `java.awt.Graphics`, `java.awt.Insets`, `javax.swing.JButton` and `javax.swing.JFrame` to the import block of `GraphWorkspaceWindowModelShould.java`, and add this class constant next to the existing `OPEN_PATH` constant:

```java
    private static final Color SEARCH_STUB_COLOR = new Color(0xFFFF00FF);
```

Insert the three test methods immediately after the closing brace of `switchesToolsThroughTheSharedButtonGroup` and before `public void growsTheScrollableSurfaceForVisibleWorldGeometry()`:

```java
    @Test
    public void keepsTheSearchPromptOutOfTheDocument() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        List<String> queries = new ArrayList<String>();
        model.toolbar().setSearchListener(queries::add);

        assertThat(model.toolbar().searchField().getText()).isEmpty();
        assertThat(queries).isEmpty();
        model.toolbar().searchField().setText("Alpha");
        assertThat(model.toolbar().searchField().getText()).isEqualTo("Alpha");
        assertThat(queries).containsExactly("Alpha");
        model.toolbar().searchField().setText("");
        assertThat(model.toolbar().searchField().getText()).isEmpty();
        assertThat(queries).containsExactly("Alpha", "");
        model.close();
    }

    @Test
    public void paintsSearchPromptAndMagnifierFromLiveState() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        RecordingPaintingIcon stub = new RecordingPaintingIcon(16, 16, SEARCH_STUB_COLOR);
        fixture.stubIcon("/images/GraphSearch.svg?useAccentColor=true", stub);
        GraphWorkspaceWindowModel model = fixture.model();
        JTextField field = model.toolbar().searchField();

        assertThat(field.getText()).isEmpty();
        assertThat(field.getPreferredSize().height).isEqualTo(26);
        assertThat(field.getPreferredSize().width).isEqualTo(Math.max(160, field.getInsets().left
            + field.getFontMetrics(field.getFont()).stringWidth("graph_workspace.tooltip.search")
            + field.getInsets().right));

        BufferedImage emptyImage = paintField(field);
        assertThat(pixelsMatching(emptyImage, SEARCH_STUB_COLOR)).isGreaterThan(0);
        assertThat(pixelsMatching(emptyImage, field.getDisabledTextColor())).isGreaterThan(0);
        assertThat(stub.lastX).isEqualTo(field.getInsets().left - field.getMargin().left);
        assertThat(stub.lastY).isEqualTo((field.getHeight() - stub.getIconHeight()) / 2);

        GraphSearchField tallField = new GraphSearchField();
        tallField.setPrompt("graph_workspace.tooltip.search");
        tallField.setPromptIcon(new RecordingPaintingIcon(40, 40, SEARCH_STUB_COLOR));
        Insets tallMargin = tallField.getMargin();
        assertThat(tallField.getPreferredSize().height)
            .isEqualTo(40 + tallMargin.top + tallMargin.bottom)
            .isGreaterThan(26);

        field.setText("Results");
        BufferedImage textImage = paintField(field);
        assertThat(pixelsMatching(textImage, SEARCH_STUB_COLOR)).isGreaterThan(0);
        assertThat(pixelsMatching(textImage, field.getDisabledTextColor())).isZero();
        int firstTextPixel = -1;
        int bandTop = field.getInsets().top;
        int bandBottom = bandTop + field.getFontMetrics(field.getFont()).getHeight();
        for (int y = bandTop; y < bandBottom && firstTextPixel < 0; y++) {
            for (int x = field.getInsets().left; x < field.getWidth() - field.getInsets().right; x++) {
                int rgb = textImage.getRGB(x, y);
                if (rgb != field.getBackground().getRGB() && rgb != SEARCH_STUB_COLOR.getRGB()) {
                    firstTextPixel = x;
                    break;
                }
            }
        }
        assertThat(firstTextPixel).isGreaterThanOrEqualTo(stub.lastX + stub.getIconWidth());
        model.close();
    }

    @Test
    public void hidesSearchPromptWhileFocused() {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        GraphSearchField field = new GraphSearchField();
        field.setPrompt("graph_workspace.tooltip.search");
        RecordingPaintingIcon stub = new RecordingPaintingIcon(16, 16, SEARCH_STUB_COLOR);
        field.setPromptIcon(stub);
        BufferedImage[] focusedImage = new BufferedImage[1];
        int[] recordedX = new int[1];
        int[] recordedY = new int[1];
        withFocusedField(field, new Runnable() {
            @Override
            public void run() {
                focusedImage[0] = paintField(field);
                recordedX[0] = field.getInsets().left - field.getMargin().left;
                recordedY[0] = (field.getHeight() - stub.getIconHeight()) / 2;
            }
        });

        assertThat(focusedImage[0]).isNotNull();
        assertThat(pixelsMatching(focusedImage[0], SEARCH_STUB_COLOR)).isGreaterThan(0);
        assertThat(pixelsMatching(focusedImage[0], field.getDisabledTextColor())).isZero();
        assertThat(stub.lastX).isEqualTo(recordedX[0]);
        assertThat(stub.lastY).isEqualTo(recordedY[0]);
    }
```

Insert these private helpers next to the existing `paintCanvas` and `nonBackgroundPixels` helpers (after `nonBackgroundPixels`):

```java
    private static BufferedImage paintField(final JTextField field) {
        field.setSize(field.getPreferredSize());
        BufferedImage image = new BufferedImage(Math.max(1, field.getWidth()),
            Math.max(1, field.getHeight()), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            field.paint(graphics);
        }
        finally {
            graphics.dispose();
        }
        return image;
    }

    private static int pixelsMatching(final BufferedImage image, final Color color) {
        int expected = color.getRGB();
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) == expected) {
                    count++;
                }
            }
        }
        return count;
    }

    private static void withFocusedField(final GraphSearchField field, final Runnable action) {
        final JFrame[] frame = new JFrame[1];
        try {
            GraphWorkspaceWindow.runOnEdt(new Runnable() {
                @Override
                public void run() {
                    JFrame value = new JFrame();
                    value.setLayout(new BorderLayout());
                    value.add(field, BorderLayout.CENTER);
                    value.add(new JButton("Next"), BorderLayout.SOUTH);
                    value.setSize(500, 400);
                    value.setVisible(true);
                    field.requestFocusInWindow();
                    frame[0] = value;
                }
            });
            waitForFieldFocus(field);
            GraphWorkspaceWindow.runOnEdt(action);
        }
        finally {
            if (frame[0] != null) {
                GraphWorkspaceWindow.runOnEdt(new Runnable() {
                    @Override
                    public void run() {
                        frame[0].dispose();
                    }
                });
            }
        }
    }

    private static void waitForFieldFocus(final GraphSearchField field) {
        for (int attempt = 0; attempt < 100; attempt++) {
            final boolean[] focused = new boolean[1];
            GraphWorkspaceWindow.runOnEdt(new Runnable() {
                @Override
                public void run() {
                    field.requestFocusInWindow();
                    focused[0] = field.isFocusOwner();
                }
            });
            if (focused[0]) {
                return;
            }
            try {
                Thread.sleep(10L);
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
        }
        throw new AssertionError("Search field did not gain focus");
    }

    private static final class RecordingPaintingIcon implements Icon {
        private final int width;
        private final int height;
        private final Color color;
        private int lastX = -1;
        private int lastY = -1;

        private RecordingPaintingIcon(final int width, final int height, final Color color) {
            this.width = width;
            this.height = height;
            this.color = color;
        }

        @Override
        public void paintIcon(final Component component, final Graphics graphics, final int x, final int y) {
            lastX = x;
            lastY = y;
            graphics.setColor(color);
            graphics.fillRect(x, y, width, height);
        }

        @Override
        public int getIconWidth() {
            return width;
        }

        @Override
        public int getIconHeight() {
            return height;
        }
    }
```

The text-pixel scan reads only the baseline band and starts at `getInsets().left`, so border and focus painting are excluded; the first non-background pixel in that region is a typed-text pixel. The focus test is a local replication of the shown-frame and `waitForFocus` pattern (`AccessibleGraphCanvasShould.java:486-529`) because that helper is `private static` and typed to `GraphCanvas`; its absence from headless runs is accepted while the empty/non-empty assertions run everywhere.

- [ ] **Step 2: Run the tests and confirm they fail**

Run:

```bash
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould" -PTestLoggingFull --rerun-tasks
```

Expected: FAIL to compile with `cannot find symbol: class GraphSearchField`, because `GraphSearchField` does not exist yet.

- [ ] **Step 3: Create `GraphSearchField` and wire the toolbar**

Create `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphSearchField.java` with exactly this content:

```java
package org.freeplane.plugin.graph.window;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;

import javax.swing.Icon;
import javax.swing.JTextField;

final class GraphSearchField extends JTextField {
    private static final int GAP = 6;
    private static final int TRAILING_INSET = 6;

    private final Insets baseMargin;
    private String prompt;
    private Icon promptIcon;

    GraphSearchField() {
        baseMargin = getMargin();
    }

    void setPrompt(final String prompt) {
        this.prompt = prompt;
        repaint();
    }

    void setPromptIcon(final Icon icon) {
        promptIcon = icon;
        final Insets base = baseMargin != null ? baseMargin : new Insets(0, 0, 0, 0);
        if (icon != null) {
            setMargin(new Insets(base.top, icon.getIconWidth() + GAP, base.bottom, TRAILING_INSET));
        }
        else {
            setMargin(baseMargin);
        }
        repaint();
    }

    private Insets currentMargin() {
        final Insets margin = getMargin();
        return margin != null ? margin : baseMargin;
    }

    @Override
    protected void paintComponent(final Graphics graphics) {
        super.paintComponent(graphics);
        final Icon icon = promptIcon;
        if (icon != null) {
            final Insets margin = currentMargin();
            final int marginLeft = margin == null ? 0 : margin.left;
            final int glyphX = getInsets().left - marginLeft;
            icon.paintIcon(this, graphics, Math.max(0, glyphX),
                (getHeight() - icon.getIconHeight()) / 2);
        }
        if (!getText().isEmpty() || isFocusOwner()) {
            return;
        }
        if (prompt != null) {
            graphics.setColor(getDisabledTextColor());
            graphics.drawString(prompt, getInsets().left,
                getInsets().top + getFontMetrics(getFont()).getAscent());
        }
    }

    @Override
    public Dimension getPreferredSize() {
        final int promptWidth = prompt == null ? 0 : getFontMetrics(getFont()).stringWidth(prompt);
        final int width = Math.max(160, getInsets().left + promptWidth + getInsets().right);
        final Insets margin = currentMargin() == null ? new Insets(0, 0, 0, 0) : currentMargin();
        final int iconHeight = promptIcon == null ? 0 : promptIcon.getIconHeight();
        final int height = Math.max(26, iconHeight + margin.top + margin.bottom);
        return new Dimension(width, height);
    }
}
```

Both setters are constructor-only; if either is ever called after layout it must also call `revalidate()` because the prompt feeds `getPreferredSize()`.

In `WorkspaceToolbar.java`, replace:

```java
    private final JTextField searchField = new JTextField();
```

with:

```java
    private final GraphSearchField searchField = new GraphSearchField();
```

Replace the constructor wiring:

```java
        searchField.setName("graph-workspace-search");
        searchField.setToolTipText(TextUtils.getText("graph_workspace.tooltip.search"));
        searchField.setPreferredSize(new Dimension(160, 26));
```

with:

```java
        searchField.setName("graph-workspace-search");
        searchField.setToolTipText(TextUtils.getText("graph_workspace.tooltip.search"));
        searchField.getAccessibleContext().setAccessibleName(
            TextUtils.getText("graph_workspace.tooltip.search"));
        searchField.setPrompt(TextUtils.getText("graph_workspace.tooltip.search"));
        searchField.setPromptIcon(ResourceController.getResourceController()
            .getOptionalIcon("/images/GraphSearch.svg?useAccentColor=true"));
```

The fixed `setPreferredSize(new Dimension(160, 26))` is deleted so the field's own `getPreferredSize()` wins; the prompt text is never a literal in production code. The accessor stays `JTextField searchField()`.

- [ ] **Step 4: Run the focused suites and confirm they pass**

Run:

```bash
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould" --tests "org.freeplane.plugin.graph.canvas.GraphSearchModelShould" --tests "org.freeplane.plugin.graph.integration.GraphWorkspaceCommandAcceptanceShould" -PTestLoggingFull --rerun-tasks
```

Expected: PASS. `GraphWorkspaceWindowModelShould` reports 67 tests (64 after Task 1 plus `keepsTheSearchPromptOutOfTheDocument`, `paintsSearchPromptAndMagnifierFromLiveState` and `hidesSearchPromptWhileFocused`; the focus test is skipped when the JVM is headless); `GraphSearchModelShould` reports its 4 existing tests and `GraphWorkspaceCommandAcceptanceShould` its 14 existing tests, both unchanged and green, proving a non-matching query still dims rather than filters. If a count differs, stop and report the mismatch instead of adjusting assertions.

- [ ] **Step 5: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphSearchField.java \
    freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceToolbar.java \
    freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java
git commit -m "feat: paint the search prompt and magnifier in the toolbar field [2026-09-12-graph-toolbar-affordances]"
```

## Task 3: Make the settings gear an icon toggle that carries panel state

**Implementer tier:** Advanced
**Lane:** graph-toolbar-affordances

**Files:**
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceToolbar.java:61` (the settings field)
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceToolbar.java:123` (delete the unconditional gear tooltip)
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceToolbar.java:209-211` (widen the accessor)
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java:458-463` (the settings action)
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java:504` (construction sync)
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java` (insert `toggleSettingsPanel` before `private void setReadOnlyOnEdt(final boolean value)`, current line 987)
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java` (append after `hidesSearchPromptWhileFocused`)

**Interfaces:**
- Consumes: from Tasks 1-2, `WorkspaceToolbar.configureIcon(...)`, `WorkspaceToolbar.iconToggle(...)`, `WorkspaceToolbar.applyToolbarSegmentStyle(...)`, `ToolSwitch`, `GraphSearchField`; the existing `WorkspaceToolbar.settingsAction` field, `WorkspaceSettingsPanel`, `model.settingsPanel()`, `model.setReadOnly(boolean)`, `model.toolbar().settingsButton().doClick()`, and the menu path `viewSettingsMenuItem` -> `toolbar.settingsButton().doClick()` (`GraphWorkspaceWindow.java:1092-1093`).
- Produces: `AbstractButton WorkspaceToolbar.settingsButton()` (widened from `JButton`); `void WorkspaceToolbar.setSettingsVisible(final boolean visible)` (selected state only, never reads the panel and never toggles); `private void GraphWorkspaceWindowModel.toggleSettingsPanel()` (reads `settingsPanel.isVisible()`, toggles, then pushes the panel-derived value to the gear); `GraphWorkspaceWindowModelShould.resolvesTheToolbarAffordanceIconsThroughTheSharedRoutine()`; `keepsToolbarAffordanceTextFallbackWhenIconsDoNotResolve()`; `preservesToolbarEnablementInReadOnlySessions()`; `keepsTheSettingsGearSynchronizedWithThePanel()`; `stylesOnlyTheSwitchSegmentsWhenIconsResolve()`.

- [ ] **Step 1: Write the five failing tests**

Insert the five methods immediately after the closing brace of `hidesSearchPromptWhileFocused` and before `public void growsTheScrollableSurfaceForVisibleWorldGeometry()`:

```java
    @Test
    public void resolvesTheToolbarAffordanceIconsThroughTheSharedRoutine() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        Icon selectIcon = icon(16, 16);
        Icon connectIcon = icon(16, 16);
        Icon settingsIcon = icon(16, 16);
        Icon searchIcon = icon(16, 16);
        fixture.stubIcon("/images/GraphSelect.svg?useAccentColor=true", selectIcon);
        fixture.stubIcon("/images/GraphConnect.svg?useAccentColor=true", connectIcon);
        fixture.stubIcon("/images/GraphSettings.svg?useAccentColor=true", settingsIcon);
        fixture.stubIcon("/images/GraphSearch.svg?useAccentColor=true", searchIcon);
        GraphWorkspaceWindowModel model = fixture.model();

        assertThat(model.toolbar().selectButton().getIcon()).isSameAs(selectIcon);
        assertThat(model.toolbar().selectButton().getText()).isNull();
        assertThat(model.toolbar().selectButton().getToolTipText()).isEqualTo("graph_workspace.tool.select");
        assertThat(model.toolbar().selectButton().getAccessibleContext().getAccessibleName())
            .isEqualTo("graph_workspace.tool.select");
        assertThat(model.toolbar().selectButton().getName()).isEqualTo("graph-workspace-select");
        assertThat(model.toolbar().connectButton().getIcon()).isSameAs(connectIcon);
        assertThat(model.toolbar().connectButton().getText()).isNull();
        assertThat(model.toolbar().connectButton().getToolTipText())
            .isEqualTo("graph_workspace.tooltip.connect");
        assertThat(model.toolbar().connectButton().getAccessibleContext().getAccessibleName())
            .isEqualTo("graph_workspace.tooltip.connect");
        assertThat(model.toolbar().connectButton().getName()).isEqualTo("graph-workspace-connect");
        assertThat(model.toolbar().settingsButton().getIcon()).isSameAs(settingsIcon);
        assertThat(model.toolbar().settingsButton().getText()).isNull();
        assertThat(model.toolbar().settingsButton().getToolTipText())
            .isEqualTo("graph_workspace.tooltip.settings");
        assertThat(model.toolbar().settingsButton().getAccessibleContext().getAccessibleName())
            .isEqualTo("graph_workspace.tooltip.settings");
        assertThat(model.toolbar().settingsButton().getName()).isEqualTo("graph-workspace-settings");
        assertThat(model.toolbar().searchField().getToolTipText()).isEqualTo("graph_workspace.tooltip.search");
        assertThat(model.toolbar().searchField().getAccessibleContext().getAccessibleName())
            .isEqualTo("graph_workspace.tooltip.search");

        verify(fixture.resourceController()).getOptionalIcon("/images/GraphSelect.svg?useAccentColor=true");
        verify(fixture.resourceController()).getOptionalIcon("/images/GraphConnect.svg?useAccentColor=true");
        verify(fixture.resourceController()).getOptionalIcon("/images/GraphSettings.svg?useAccentColor=true");
        verify(fixture.resourceController()).getOptionalIcon("/images/GraphSearch.svg?useAccentColor=true");
        model.close();
    }

    @Test
    public void keepsToolbarAffordanceTextFallbackWhenIconsDoNotResolve() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        List<InteractionTool> tools = new ArrayList<InteractionTool>();
        model.toolbar().setToolListener(tools::add);
        int[] settingsClicks = new int[1];
        model.toolbar().setSettingsAction(() -> settingsClicks[0]++);

        assertThat(model.toolbar().selectButton().getText()).isEqualTo("graph_workspace.tool.select");
        assertThat(model.toolbar().selectButton().getIcon()).isNull();
        assertThat(model.toolbar().selectButton().getToolTipText()).isNull();
        assertThat(model.toolbar().connectButton().getText()).isEqualTo("graph_workspace.tool.connect");
        assertThat(model.toolbar().connectButton().getIcon()).isNull();
        assertThat(model.toolbar().connectButton().getToolTipText()).isNull();
        assertThat(model.toolbar().settingsButton().getText()).isEqualTo("graph_workspace.action.settings");
        assertThat(model.toolbar().settingsButton().getIcon()).isNull();
        assertThat(model.toolbar().settingsButton().getToolTipText()).isNull();

        model.toolbar().selectButton().doClick();
        model.toolbar().connectButton().doClick();
        model.toolbar().settingsButton().doClick();
        assertThat(tools).containsExactly(InteractionTool.SELECT, InteractionTool.CONNECT);
        assertThat(settingsClicks[0]).isEqualTo(1);
        model.close();
    }

    @Test
    public void preservesToolbarEnablementInReadOnlySessions() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false,
            WorkspaceSessionStatus.empty());
        GraphWorkspaceWindowModel model = fixture.model();

        assertThat(model.toolbar().selectButton().isEnabled()).isTrue();
        assertThat(model.toolbar().connectButton().isEnabled()).isTrue();
        assertThat(model.toolbar().settingsButton().isEnabled()).isTrue();
        assertThat(model.toolbar().searchField().isEnabled()).isTrue();
        assertThat(model.toolbar().directionComboBox().isEnabled()).isTrue();
        assertThat(model.toolbar().undoButton().isEnabled()).isFalse();
        assertThat(model.toolbar().redoButton().isEnabled()).isFalse();
        assertThat(model.toolbar().zoomInButton().isEnabled()).isTrue();
        assertThat(model.toolbar().zoomOutButton().isEnabled()).isTrue();
        assertThat(model.toolbar().pinButton().isEnabled()).isFalse();
        model.close();

        Fixture readOnlyFixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), true,
            WorkspaceSessionStatus.empty());
        GraphWorkspaceWindowModel readOnlyModel = readOnlyFixture.model();

        assertThat(readOnlyModel.toolbar().selectButton().isEnabled()).isTrue();
        assertThat(readOnlyModel.toolbar().connectButton().isEnabled()).isFalse();
        assertThat(readOnlyModel.toolbar().settingsButton().isEnabled()).isTrue();
        assertThat(readOnlyModel.toolbar().searchField().isEnabled()).isTrue();
        assertThat(readOnlyModel.toolbar().directionComboBox().isEnabled()).isFalse();
        assertThat(readOnlyModel.toolbar().undoButton().isEnabled()).isFalse();
        assertThat(readOnlyModel.toolbar().redoButton().isEnabled()).isFalse();
        assertThat(readOnlyModel.toolbar().zoomInButton().isEnabled()).isTrue();
        assertThat(readOnlyModel.toolbar().zoomOutButton().isEnabled()).isTrue();
        assertThat(readOnlyModel.toolbar().pinButton().isEnabled()).isFalse();
        readOnlyModel.close();
    }

    @Test
    public void keepsTheSettingsGearSynchronizedWithThePanel() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();

        assertThat(model.settingsPanel().isVisible()).isTrue();
        assertThat(model.toolbar().settingsButton().isSelected()).isTrue();

        model.toolbar().settingsButton().doClick();
        assertThat(model.settingsPanel().isVisible()).isFalse();
        assertThat(model.toolbar().settingsButton().isSelected()).isFalse();

        menuItem(model, "settings").doClick();
        assertThat(model.settingsPanel().isVisible()).isTrue();
        assertThat(model.toolbar().settingsButton().isSelected()).isTrue();

        model.settingsPanel().setVisible(false);
        assertThat(model.toolbar().settingsButton().isSelected()).isTrue();
        model.toolbar().settingsButton().doClick();
        assertThat(model.settingsPanel().isVisible()).isTrue();
        assertThat(model.toolbar().settingsButton().isSelected()).isTrue();

        model.setReadOnly(true);
        assertThat(model.toolbar().settingsButton().isEnabled()).isTrue();
        assertThat(model.toolbar().settingsButton().isSelected())
            .isEqualTo(model.settingsPanel().isVisible());
        model.close();
    }

    @Test
    public void stylesOnlyTheSwitchSegmentsWhenIconsResolve() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        fixture.stubIcon("/images/GraphSelect.svg?useAccentColor=true", icon(16, 16));
        fixture.stubIcon("/images/GraphConnect.svg?useAccentColor=true", icon(16, 16));
        fixture.stubIcon("/images/GraphSettings.svg?useAccentColor=true", icon(16, 16));
        fixture.stubIcon("/images/GraphSearch.svg?useAccentColor=true", icon(16, 16));
        fixture.stubIcon("/images/undo.svg?useAccentColor=true", icon(16, 16));
        fixture.stubIcon("/images/redo.svg?useAccentColor=true", icon(16, 16));
        fixture.stubIcon("/images/ZoomIn24.svg?useAccentColor=true", icon(16, 16));
        fixture.stubIcon("/images/ZoomOut24.svg?useAccentColor=true", icon(16, 16));
        GraphWorkspaceWindowModel model = fixture.model();

        assertThat(model.toolbar().selectButton().getClientProperty("JButton.buttonType"))
            .isEqualTo("toolBarButton");
        assertThat(model.toolbar().connectButton().getClientProperty("JButton.buttonType"))
            .isEqualTo("toolBarButton");
        assertThat(model.toolbar().settingsButton().getIcon()).isNotNull();
        assertThat(model.toolbar().undoButton().getIcon()).isNotNull();
        assertThat(model.toolbar().redoButton().getIcon()).isNotNull();
        assertThat(model.toolbar().zoomInButton().getIcon()).isNotNull();
        assertThat(model.toolbar().zoomOutButton().getIcon()).isNotNull();
        assertThat(model.toolbar().settingsButton().getClientProperty("JButton.buttonType")).isNull();
        assertThat(model.toolbar().undoButton().getClientProperty("JButton.buttonType")).isNull();
        assertThat(model.toolbar().redoButton().getClientProperty("JButton.buttonType")).isNull();
        assertThat(model.toolbar().zoomInButton().getClientProperty("JButton.buttonType")).isNull();
        assertThat(model.toolbar().zoomOutButton().getClientProperty("JButton.buttonType")).isNull();
        model.close();

        Fixture fallbackFixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel fallbackModel = fallbackFixture.model();

        assertThat(fallbackModel.toolbar().selectButton().getClientProperty("JButton.buttonType")).isNull();
        assertThat(fallbackModel.toolbar().connectButton().getClientProperty("JButton.buttonType")).isNull();
        fallbackModel.close();
    }
```

The eight stubs are required for the first fixture so the gear, undo, redo, zoom-in and zoom-out hold resolved icons while their negative property assertion is checked; without them the negative would hold vacuously.

- [ ] **Step 2: Run the tests and confirm they fail**

Run:

```bash
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould" -PTestLoggingFull --rerun-tasks
```

Expected: FAIL to compile with `cannot find symbol: method isSelected()` on the `JButton` returned by `settingsButton()`, because the gear is not a toggle yet.

- [ ] **Step 3: Convert the gear, add the state push and wire the window model**

In `WorkspaceToolbar.java`, replace:

```java
    private final JButton settingsButton = button("graph_workspace.action.settings", "settings");
```

with:

```java
    private final JToggleButton settingsButton = iconToggle("graph_workspace.action.settings",
        "graph_workspace.tooltip.settings", "settings", "/images/GraphSettings.svg?useAccentColor=true");
```

Delete this constructor line:

```java
        settingsButton.setToolTipText(TextUtils.getText("graph_workspace.tooltip.settings"));
```

so the gear's tooltip now comes only from `configureIcon` on icon resolution, the same fallback rule as undo/redo/zoom.

Replace the accessor:

```java
    JButton settingsButton() {
        return settingsButton;
    }
```

with:

```java
    AbstractButton settingsButton() {
        return settingsButton;
    }

    void setSettingsVisible(final boolean visible) {
        settingsButton.setSelected(visible);
    }
```

`setSettingsVisible` is a dumb state push: it only sets the selected state, never reads the panel and never toggles. The accessor's callers (`GraphWorkspaceWindow.java:994,1014,1093` and `GraphWorkspaceWindowModelShould.java:941`) all use `setEnabled`, `doClick` or `isEnabled`, so they compile unchanged.

In `GraphWorkspaceWindow.java`, replace the anonymous settings action:

```java
        toolbar.setSettingsAction(new Runnable() {
            @Override
            public void run() {
                settingsPanel.setVisible(!settingsPanel.isVisible());
            }
        });
```

with:

```java
        toolbar.setSettingsAction(this::toggleSettingsPanel);
```

Replace:

```java
        content = createContent();
```

with:

```java
        content = createContent();
        toolbar.setSettingsVisible(settingsPanel.isVisible());
```

Insert this method immediately before `private void setReadOnlyOnEdt(final boolean value) {` (current line 987):

```java
    private void toggleSettingsPanel() {
        settingsPanel.setVisible(!settingsPanel.isVisible());
        toolbar.setSettingsVisible(settingsPanel.isVisible());
    }

```

The read, the toggle and the refresh all live in `toggleSettingsPanel`, so no second place can derive state from the click; the construction sync at `content = createContent();` makes `gear.isSelected() == settingsPanel.isVisible()` true from construction because the panel starts visible. The panel keeps its heading, gets no `×` close control, and the `View -> Settings` item still delegates to `toolbar.settingsButton().doClick()`.

- [ ] **Step 4: Run the suite and confirm it passes**

Run:

```bash
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould" -PTestLoggingFull --rerun-tasks
```

Expected: PASS. `GraphWorkspaceWindowModelShould` reports 72 tests (67 after Task 2 plus `resolvesTheToolbarAffordanceIconsThroughTheSharedRoutine`, `keepsToolbarAffordanceTextFallbackWhenIconsDoNotResolve`, `preservesToolbarEnablementInReadOnlySessions`, `keepsTheSettingsGearSynchronizedWithThePanel` and `stylesOnlyTheSwitchSegmentsWhenIconsResolve`). If the count differs, stop and report the mismatch instead of adjusting assertions.

- [ ] **Step 5: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceToolbar.java \
    freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java \
    freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java
git commit -m "feat: reflect settings panel visibility on the toolbar gear [2026-09-12-graph-toolbar-affordances]"
```

## Task 4: Add the affordance resource values and the source guards

**Implementer tier:** Fast
**Lane:** graph-toolbar-affordances

**Files:**
- Modify: `freeplane/src/viewer/resources/translations/Resources_en.properties:959-962`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphPluginIntegrationShould.java` (append after the test that ends at current line 455)

**Interfaces:**
- Consumes: `Properties.load(InputStream)` through the existing `properties(String relativePath)` helper (`GraphPluginIntegrationShould.java:466`); the existing `read(String relativePath)` helper (`:474`); the exact file content Tasks 1-3 leave in `WorkspaceToolbar.java`.
- Produces: `GraphPluginIntegrationShould.shipsTheGraphToolbarAffordanceResourceKeys()`; `GraphPluginIntegrationShould.keepsTheToolbarSwitchGroupOwnedByToolSwitch()`; `GraphPluginIntegrationShould.keepsTheToolbarSegmentStylingDependencyFree()`; the resource values `graph_workspace.tooltip.connect=Connect \u2014 create a cross-map relationship`, `graph_workspace.tooltip.search=Search nodes and maps`, `graph_workspace.tooltip.settings=Graph settings`.

- [ ] **Step 1: Write the three failing tests**

Insert these methods immediately after the closing brace of `shipsTheFourRecentWorkspaceResourceKeys` (current line 455) and before `private static ModeController configuredModeController()`:

```java
    @Test
    public void shipsTheGraphToolbarAffordanceResourceKeys() throws IOException {
        Properties translations = properties(
            "freeplane/src/viewer/resources/translations/Resources_en.properties");

        assertThat(translations.getProperty("graph_workspace.tooltip.connect"))
            .isEqualTo("Connect \u2014 create a cross-map relationship");
        assertThat(translations.getProperty("graph_workspace.tooltip.settings")).isEqualTo("Graph settings");
        assertThat(translations.getProperty("graph_workspace.tooltip.search")).isEqualTo("Search nodes and maps");
        assertThat(translations.getProperty("graph_workspace.tool.select")).isEqualTo("Select");
        assertThat(translations.getProperty("graph_workspace.tool.connect")).isEqualTo("Connect");
        assertThat(translations.getProperty("graph_workspace.action.settings")).isEqualTo("Settings");
    }

    @Test
    public void keepsTheToolbarSwitchGroupOwnedByToolSwitch() throws IOException {
        String toolbar = read(
            "freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceToolbar.java");

        assertThat(toolbar).doesNotContain("new ButtonGroup");
    }

    @Test
    public void keepsTheToolbarSegmentStylingDependencyFree() throws IOException {
        String toolbar = read(
            "freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceToolbar.java");

        assertThat(toolbar).contains("putClientProperty(\"JButton.buttonType\", \"toolBarButton\")");
        assertThat(toolbar).doesNotContain("com.formdev.flatlaf");
    }
```

`\u2014` in the Java string literal is the em dash the properties file produces after `Properties.load`; the guard methods fail if a toolbar-local `ButtonGroup` ever returns or if the client property is rewritten through a FlatLaf constant.

- [ ] **Step 2: Run the tests and confirm the resource test fails**

Run:

```bash
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.window.GraphPluginIntegrationShould" -PTestLoggingFull --rerun-tasks
```

Expected: FAIL in `shipsTheGraphToolbarAffordanceResourceKeys` with `expected: "Connect \u2014 create a cross-map relationship" but was: null`, because `graph_workspace.tooltip.connect` does not exist yet; the two source guards pass.

- [ ] **Step 3: Edit the translation file**

In `freeplane/src/viewer/resources/translations/Resources_en.properties`, change line 961:

```
graph_workspace.tooltip.search=Search graph
```

to:

```
graph_workspace.tooltip.search=Search nodes and maps
```

and line 962:

```
graph_workspace.tooltip.settings=Display settings
```

to:

```
graph_workspace.tooltip.settings=Graph settings
```

Insert one new line between `graph_workspace.tool.select=Select` (line 959) and `graph_workspace.tooltip.relationship_direction=Relationship direction`, written with the six ASCII characters `\u2014`:

```
graph_workspace.tooltip.connect=Connect \u2014 create a cross-map relationship
```

Do not change `graph_workspace.tool.select`, `graph_workspace.tool.connect`, `graph_workspace.action.settings` or `graph_workspace.settings.heading`.

- [ ] **Step 4: Format the translations and confirm the encoding**

Run:

```bash
gradle format_translation
```

then:

```bash
cd freeplane/src/viewer/resources/translations
file Resources_*.properties | grep -v "ASCII text"
```

Expected: both commands exit 0; the second prints nothing. `format_translation` owns the final key order and keeps the file ASCII before and after the formatter. Then confirm only the intended translation file is modified:

```bash
git status --porcelain -- freeplane/src/viewer/resources/translations
```

Expected: only ` M freeplane/src/viewer/resources/translations/Resources_en.properties`; if any other translation file appears, stop and report the formatter drift instead of committing it.

- [ ] **Step 5: Run the integration suite and the bundle guard**

Run:

```bash
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.window.GraphPluginIntegrationShould" -PTestLoggingFull --rerun-tasks
gradle :freeplane_plugin_graph:verifyGraphBundle
```

Expected: PASS. `GraphPluginIntegrationShould` reports 12 tests (10 existing plus `shipsTheGraphToolbarAffordanceResourceKeys`, `keepsTheToolbarSwitchGroupOwnedByToolSwitch` and `keepsTheToolbarSegmentStylingDependencyFree`); `verifyGraphBundle` is BUILD SUCCESSFUL and its `Import-Package` assertion fails the build if the plugin ever links FlatLaf.

- [ ] **Step 6: Commit**

```bash
git add freeplane/src/viewer/resources/translations/Resources_en.properties \
    freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphPluginIntegrationShould.java
git commit -m "chore: add toolbar affordance resource values and source guards [2026-09-12-graph-toolbar-affordances]"
```

## Task 5: Ship the four glyphs and prove them inside the OSGi bundle

**Implementer tier:** Fast
**Lane:** graph-toolbar-affordances

**Files:**
- Create: `freeplane_plugin_graph/src/main/resources/images/GraphSelect.svg`
- Create: `freeplane_plugin_graph/src/main/resources/images/GraphConnect.svg`
- Create: `freeplane_plugin_graph/src/main/resources/images/GraphSettings.svg`
- Create: `freeplane_plugin_graph/src/main/resources/images/GraphSearch.svg`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/smoke/GraphPluginOsgiSmoke.java:1-14` (imports)
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/smoke/GraphPluginOsgiSmoke.java:206-216` (bundle contents)

**Interfaces:**
- Consumes: the exact icon lookup strings from R5; `Bundle.getResource(String name)` (not `Bundle.getEntry`, which searches only the bundle archive root); `URL.openStream()`.
- Produces: `images/GraphSelect.svg` (filled pointer, `#333`), `images/GraphConnect.svg` (two endpoint circles joined by a line, `#333` stroke), `images/GraphSettings.svg` (gear outline, `#333` stroke), `images/GraphSearch.svg` (magnifier, `#333` stroke); the extended `GraphPluginOsgiSmoke.assertGraphBundleContents(Bundle bundle)` asset check.

- [ ] **Step 1: Create the four assets**

Create `freeplane_plugin_graph/src/main/resources/images/GraphSelect.svg` with exactly this content:

```xml
<?xml version="1.0"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" role="img" aria-label="Select tool"><path d="M5 2v16.5l4.2-4.2 2.7 6.1 2.5-1.2-2.7-6 5.7-.2z" style="fill:#333"/></svg>
```

Create `freeplane_plugin_graph/src/main/resources/images/GraphConnect.svg` with exactly this content:

```xml
<?xml version="1.0"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" role="img" aria-label="Create relationship"><g style="fill:none;stroke:#333;stroke-width:2" stroke-linecap="round"><path d="M8.1 15.9 15.9 8.1"/><circle cx="6.5" cy="17.5" r="3"/><circle cx="17.5" cy="6.5" r="3"/></g></svg>
```

Create `freeplane_plugin_graph/src/main/resources/images/GraphSettings.svg` with exactly this content:

```xml
<?xml version="1.0"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" role="img" aria-label="Graph settings"><g style="fill:none;stroke:#333;stroke-width:1.8" stroke-linecap="round"><circle cx="12" cy="12" r="6.2"/><circle cx="12" cy="12" r="2.6"/><path d="M12 2.6v3.2M12 18.2v3.2M2.6 12h3.2M18.2 12h3.2M5.4 5.4l2.2 2.2M16.4 16.4l2.2 2.2M18.6 5.4l-2.2 2.2M7.6 16.4l-2.2 2.2"/></g></svg>
```

Create `freeplane_plugin_graph/src/main/resources/images/GraphSearch.svg` with exactly this content:

```xml
<?xml version="1.0"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" role="img" aria-label="Search nodes and maps"><g style="fill:none;stroke:#333;stroke-width:2" stroke-linecap="round"><circle cx="10.5" cy="10.5" r="6"/><path d="M14.9 14.9 20.5 20.5"/></g></svg>
```

Every painted element uses only `#333` in a form matched by the Look-and-Feel replacement rules (`#333"`, `#333;`, `#333333`), so `?useAccentColor=true` rewrites the whole glyph to `${Label.foreground}` exactly as `/images/undo.svg` does today.

- [ ] **Step 2: Extend the OSGi smoke with the four `getResource` checks**

Add `import java.io.InputStream;` after `import java.io.IOException;` in `GraphPluginOsgiSmoke.java`.

Insert this block inside `assertGraphBundleContents`, immediately after the existing `for (final String entry : entries)` loop:

```java
        final String[] images = new String[] {
            "images/GraphSelect.svg",
            "images/GraphConnect.svg",
            "images/GraphSettings.svg",
            "images/GraphSearch.svg"
        };
        for (final String image : images) {
            final URL resource = bundle.getResource(image);
            if (resource == null) {
                throw new AssertionError("Graph bundle is missing " + image);
            }
            int totalBytes = 0;
            final InputStream input = resource.openStream();
            try {
                final byte[] buffer = new byte[4096];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    totalBytes += read;
                }
            }
            finally {
                input.close();
            }
            if (totalBytes <= 0) {
                throw new AssertionError("Graph bundle resource is empty: " + image);
            }
        }
```

Do not use `bundle.getEntry(...)`: the graph bundle is installed from the exploded directory `BIN/plugins/org.freeplane.plugin.graph`, whose root holds only `lib/` and `META-INF/`; the SVGs live in `lib/plugin-<version>.jar` and are reachable only through `Bundle-ClassPath`.

- [ ] **Step 3: Run the OSGi smoke**

Run:

```bash
gradle :freeplane_plugin_graph:graphOsgiSmoke
```

Expected: BUILD SUCCESSFUL with the final stdout line `Graph OSGi smoke: ACTIVE, three dependency jars/classes, and graph operation passed`. A missing or mis-packaged asset fails the `bundle.getResource(image) != null` check with `Graph bundle is missing images/<name>.svg`.

### Blocked specification item

Specification §7.2 additionally requires an unmocked end-to-end resolution assertion: with the plugin ACTIVE, `ApplicationResourceController.getResourceController().getOptionalIcon("/images/GraphSelect.svg?useAccentColor=true")` must return a non-null icon. That half is **not implementable in this smoke as specified and must not be added** until the specification is amended: the smoke installs but never starts the core bundle, so `Controller.getCurrentController()` is null and `ResourceController.getResourceController()` (`freeplane/src/main/java/org/freeplane/core/resources/ResourceController.java:69-70`) throws `NullPointerException` before any lookup happens. Probe evidence against the exploded installation built from revision `1ca81c35a1`: after `graphBundle.start(Bundle.START_TRANSIENT)`, the graph bundle is ACTIVE (32) while the core bundle stays RESOLVED (4), `Controller.getCurrentController()` is null, and `getOptionalIcon` propagates the NPE. Activating the core bundle instead starts the whole headless Freeplane application inside the smoke (asynchronous plugin installation on the EDT), which the specification does not authorise and which is not deterministic. The four `bundle.getResource` checks above are the executable part of §7.2. The real-application checks in Task 6 (specification §7.5 items 1-2) remain the end-to-end asset proof. Do not work around this gap with a fallback, a wait loop or a core-bundle start.

- [ ] **Step 4: Commit**

```bash
git add freeplane_plugin_graph/src/main/resources/images/GraphSelect.svg \
    freeplane_plugin_graph/src/main/resources/images/GraphConnect.svg \
    freeplane_plugin_graph/src/main/resources/images/GraphSettings.svg \
    freeplane_plugin_graph/src/main/resources/images/GraphSearch.svg \
    freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/smoke/GraphPluginOsgiSmoke.java
git commit -m "feat: ship toolbar affordance glyphs and prove them in the OSGi bundle [2026-09-12-graph-toolbar-affordances]"
```

## Task 6: Record the row width, regenerate the UI evidence and run the real-application checks

**Implementer tier:** Advanced
**Lane:** graph-toolbar-affordances

**Files:**
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/smoke/GraphWorkspaceUiEvidence.java:1-72` (imports, `ICON_PIXEL_COLOR`, `PaintingIcon`)
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/smoke/GraphWorkspaceUiEvidence.java:132-140` (the icon stubs)
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/smoke/GraphWorkspaceUiEvidence.java:139-141` (the four new-path verifications)
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/smoke/GraphWorkspaceUiEvidence.java:322-340` (capture and the new assertions)
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/smoke/GraphWorkspaceUiEvidence.java:461-470` (render helpers)
- Modify: `freeplane_plugin_graph/build.gradle:162-164` (declared outputs)
- Regenerated evidence: `docs/superpowers/specs/images/2026-08-10-graph-workspace-implemented.png` (path kept so `build.gradle:151-166` needs no rewiring)

**Interfaces:**
- Consumes: the eight exact icon paths from Tasks 1-3 and the existing four; the reflective `ModelAccess.invoke(String name, Object... arguments)`; the existing `findNamed(Container, String)`, `requireComponent(JComponent, String)`, `render(JPanel)`, `paintAndVerify(Path, JPanel)` and `assertNoOverlap(Container)` (`GraphWorkspaceUiEvidence.java:539-569`) helpers; the search field stays a `JTextField` subclass, so the `findNamed(root, "graph-workspace-search")` -> `JTextField` cast (`:398,415`) keeps working.
- Produces: `private static final Color GraphWorkspaceUiEvidence.ICON_PIXEL_COLOR`; `private static final class GraphWorkspaceUiEvidence.PaintingIcon implements Icon`; `EvidenceImages.verifyToolbarAffordances()`, `verifySearchPromptPaint()`, `recordRowWidth()`, `renderComponent(JComponent)`, `pixelsMatching(BufferedImage, Color)`; `build/graph-ui-evidence/row-width.txt` with six ordered `label=<int>` lines; the regenerated `docs/superpowers/specs/images/2026-08-10-graph-workspace-implemented.png`.

- [ ] **Step 1: Apply the harness changes**

Add `import static org.mockito.Mockito.verify;` next to the existing Mockito static imports and `import java.awt.Graphics;` after `import java.awt.Graphics2D;`.

Add this constant next to the existing `FIRST_MAP`/`SECOND_KEY` constants:

```java
    private static final Color ICON_PIXEL_COLOR = new Color(0x00C853);
```

Replace the non-painting placeholder block inside `main`:

```java
                    final Icon placeholder = mock(Icon.class);
                    when(placeholder.getIconWidth()).thenReturn(Integer.valueOf(16));
                    when(placeholder.getIconHeight()).thenReturn(Integer.valueOf(16));
                    for (final String path : new String[] {
                            "/images/undo.svg?useAccentColor=true",
                            "/images/redo.svg?useAccentColor=true",
                            "/images/ZoomIn24.svg?useAccentColor=true",
                            "/images/ZoomOut24.svg?useAccentColor=true" }) {
                        when(controller.getOptionalIcon(path)).thenReturn(placeholder);
                    }
```

with:

```java
                    final Icon glyph = new PaintingIcon();
                    for (final String path : new String[] {
                            "/images/undo.svg?useAccentColor=true",
                            "/images/redo.svg?useAccentColor=true",
                            "/images/ZoomIn24.svg?useAccentColor=true",
                            "/images/ZoomOut24.svg?useAccentColor=true",
                            "/images/GraphSelect.svg?useAccentColor=true",
                            "/images/GraphConnect.svg?useAccentColor=true",
                            "/images/GraphSettings.svg?useAccentColor=true",
                            "/images/GraphSearch.svg?useAccentColor=true" }) {
                        when(controller.getOptionalIcon(path)).thenReturn(glyph);
                    }
```

Immediately after `images.capture();` add:

```java
                    verify(controller).getOptionalIcon("/images/GraphSelect.svg?useAccentColor=true");
                    verify(controller).getOptionalIcon("/images/GraphConnect.svg?useAccentColor=true");
                    verify(controller).getOptionalIcon("/images/GraphSettings.svg?useAccentColor=true");
                    verify(controller).getOptionalIcon("/images/GraphSearch.svg?useAccentColor=true");
```

Replace `capture()`:

```java
        void capture() {
            root.setSize(new Dimension(1280, 800));
            layoutRecursively(root);
            dispatchInteractions();
            verifyPinToggleStates(pinnedTwoMapState());
            paintAndVerify(desktop, root);
```

with:

```java
        void capture() {
            root.setSize(new Dimension(1280, 800));
            layoutRecursively(root);
            verifyToolbarAffordances();
            verifySearchPromptPaint();
            recordRowWidth();
            dispatchInteractions();
            verifyPinToggleStates(pinnedTwoMapState());
            paintAndVerify(desktop, root);
```

Insert these methods immediately after `capture()` and before `private void verifyWorkspacePaint(final JPanel panel)`:

```java
        private void verifyToolbarAffordances() {
            final AbstractButton select = (AbstractButton) findNamed(root, "graph-workspace-select");
            final AbstractButton connect = (AbstractButton) findNamed(root, "graph-workspace-connect");
            final AbstractButton settings = (AbstractButton) findNamed(root, "graph-workspace-settings");
            requireComponent(select, "select toggle");
            requireComponent(connect, "connect toggle");
            requireComponent(settings, "settings toggle");
            if (select.getIcon() == null || select.getText() != null
                    || connect.getIcon() == null || connect.getText() != null
                    || settings.getIcon() == null || settings.getText() != null) {
                throw new AssertionError("Toolbar affordance icons were not resolved: select="
                    + select.getText() + ", connect=" + connect.getText()
                    + ", settings=" + settings.getText());
            }
            final BufferedImage image = render(root);
            if (pixelsMatching(image, ICON_PIXEL_COLOR) <= 0) {
                throw new AssertionError("Toolbar affordance glyphs were not painted");
            }
        }

        private void verifySearchPromptPaint() {
            final JTextField search = (JTextField) findNamed(root, "graph-workspace-search");
            requireComponent(search, "search field");
            final BufferedImage image = renderComponent(search);
            final int glyphPixels = pixelsMatching(image, ICON_PIXEL_COLOR);
            final int promptPixels = pixelsMatching(image, search.getDisabledTextColor());
            if (glyphPixels <= 0 || promptPixels <= 0) {
                throw new AssertionError("Search prompt/magnifier paint failed: glyph=" + glyphPixels
                    + ", prompt=" + promptPixels);
            }
        }

        private void recordRowWidth() {
            final JPanel toolbar = (JPanel) modelAccess.invoke("toolbar");
            final JComponent select = findNamed(root, "graph-workspace-select");
            final JComponent connect = findNamed(root, "graph-workspace-connect");
            final JComponent toolSwitch = findNamed(root, "graph-workspace-tool-switch");
            final JComponent search = findNamed(root, "graph-workspace-search");
            final JComponent settings = findNamed(root, "graph-workspace-settings");
            requireComponent(select, "select toggle");
            requireComponent(connect, "connect toggle");
            requireComponent(toolSwitch, "tool switch");
            requireComponent(search, "search field");
            requireComponent(settings, "settings toggle");
            final String[] labels = new String[] {
                "toolbar.layoutPreferredWidth", "select.width", "connect.width", "toolSwitch.width",
                "search.width", "settings.width"
            };
            final int[] values = new int[] {
                toolbar.getLayout().preferredLayoutSize(toolbar).width,
                select.getWidth(), connect.getWidth(), toolSwitch.getWidth(), search.getWidth(),
                settings.getWidth()
            };
            final String[] lines = new String[labels.length];
            for (int index = 0; index < labels.length; index++) {
                lines[index] = labels[index] + "=" + values[index];
            }
            final Path widthFile = Paths.get("build/graph-ui-evidence/row-width.txt");
            try {
                Files.createDirectories(widthFile.getParent());
                Files.write(widthFile, Arrays.asList(lines));
                final java.util.List<String> written = Files.readAllLines(widthFile);
                if (written.size() != labels.length) {
                    throw new AssertionError("Row-width evidence file has " + written.size() + " lines");
                }
                for (int index = 0; index < labels.length; index++) {
                    final String expected = labels[index] + "=";
                    if (!written.get(index).startsWith(expected)) {
                        throw new AssertionError("Row-width evidence line " + index + " was "
                            + written.get(index));
                    }
                    Integer.parseInt(written.get(index).substring(expected.length()));
                }
            }
            catch (final java.io.IOException exception) {
                throw new IllegalStateException("Unable to write row-width evidence " + widthFile, exception);
            }
            for (final String line : lines) {
                System.out.println("Graph row width: " + line);
            }
        }
```

Insert these helpers immediately before the existing `private static BufferedImage render(final JPanel panel)`:

```java
        private static BufferedImage renderComponent(final JComponent component) {
            component.setSize(component.getPreferredSize());
            final BufferedImage image = new BufferedImage(Math.max(1, component.getWidth()),
                Math.max(1, component.getHeight()), BufferedImage.TYPE_INT_ARGB);
            final Graphics2D graphics = image.createGraphics();
            try {
                component.paint(graphics);
            }
            finally {
                graphics.dispose();
            }
            return image;
        }

        private static int pixelsMatching(final BufferedImage image, final Color color) {
            final int expected = color.getRGB();
            int count = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if (image.getRGB(x, y) == expected) {
                        count++;
                    }
                }
            }
            return count;
        }
```

Insert this nested class immediately before `private static final class EvidenceImages`:

```java
    private static final class PaintingIcon implements Icon {
        private final BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);

        private PaintingIcon() {
            final Graphics2D graphics = image.createGraphics();
            try {
                graphics.setColor(ICON_PIXEL_COLOR);
                graphics.fillRect(0, 0, 16, 16);
            }
            finally {
                graphics.dispose();
            }
        }

        @Override
        public void paintIcon(final Component component, final Graphics graphics, final int x, final int y) {
            graphics.drawImage(image, x, y, null);
        }

        @Override
        public int getIconWidth() {
            return 16;
        }

        @Override
        public int getIconHeight() {
            return 16;
        }
    }
```

The harness renders the search field directly with empty text before `dispatchInteractions`, so the `"Alpha"` typed at line 415 stays untouched and the committed desktop image shows the magnifier beside typed text; the direct render is what proves the prompt. The harness runs with the JVM default Look-and-Feel rather than FlatLaf, so the image carries geometry, text and painted glyphs but not FlatLaf's accent or selected-segment colours.

In `freeplane_plugin_graph/build.gradle`, replace:

```groovy
    outputs.files(
        rootProject.file('docs/superpowers/specs/images/2026-08-10-graph-workspace-implemented.png'),
        rootProject.file('docs/superpowers/specs/images/2026-08-10-graph-group-marker-implemented.png'))
```

with:

```groovy
    outputs.files(
        rootProject.file('build/graph-ui-evidence/row-width.txt'),
        rootProject.file('docs/superpowers/specs/images/2026-08-10-graph-workspace-implemented.png'),
        rootProject.file('docs/superpowers/specs/images/2026-08-10-graph-group-marker-implemented.png'))
```

- [ ] **Step 2: Run the evidence harness and confirm it passes**

Run:

```bash
gradle :freeplane_plugin_graph:graphUiEvidence
```

Expected: BUILD SUCCESSFUL with the final stdout line `Graph UI evidence: EDT shell interactions, desktop workspace, and marker paints passed`, preceded by six `Graph row width: <label>=<int>` lines with positive integers in this order: `toolbar.layoutPreferredWidth`, `select.width`, `connect.width`, `toolSwitch.width`, `search.width`, `settings.width`. `build/graph-ui-evidence/row-width.txt` exists with the same six lines; `docs/superpowers/specs/images/2026-08-10-graph-workspace-implemented.png` is regenerated in place by the same run. The harness's own existence, ordering and integer-parse assertions for the width file and its containment check `assertNoOverlap` are the gate; no new clipping assertion is added.

- [ ] **Step 3: Run the full plugin suite and confirm the counts**

Run:

```bash
gradle :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks
```

Expected: BUILD SUCCESSFUL. `GraphWorkspaceWindowModelShould` reports 72 tests (62 existing plus the 10 added in Tasks 1-3), `GraphPluginIntegrationShould` reports 12 tests (10 existing plus the 2 added in Task 4), and the whole plugin suite reports 909 tests (897 existing plus 12 new). `hidesSearchPromptWhileFocused` is skipped when the JVM is headless, so a 908-passed/1-skipped breakdown on a headless runner is the accepted reading. Confirm the counts in the generated test report; if a count differs, stop and report the mismatch instead of adjusting assertions.

- [ ] **Step 4: Verify the changed-file scope**

Run:

```bash
git status --porcelain
git diff --name-only
git diff --cached --name-only
```

Expected: across the whole plan the only changed main files are `WorkspaceToolbar.java`, `GraphWorkspaceWindow.java`, the new `ToolSwitch.java`, the new `GraphSearchField.java`, the four new `freeplane_plugin_graph/src/main/resources/images/Graph*.svg` assets and `freeplane/src/viewer/resources/translations/Resources_en.properties`; the only changed test and build files are `GraphWorkspaceWindowModelShould.java`, `GraphPluginIntegrationShould.java`, the new-file additions in `GraphWorkspaceUiEvidence.java` and `GraphPluginOsgiSmoke.java`, and `freeplane_plugin_graph/build.gradle`; the regenerated `docs/superpowers/specs/images/2026-08-10-graph-workspace-implemented.png` is changed. No existing test method was modified (`git diff` on the two existing test files must contain only added methods, imports and private helpers).

- [ ] **Step 5: Record the real-application checks**

Run a full distribution build and start the application:

```bash
gradle dist
BIN/freeplane.sh
```

On X11, open a Graph Workspace and record the observations in the task report for each specification §7.5 item: (1) Select/Connect switching, grouped rendering, tooltips and an active segment visible without hover; (2) light and dark Look-and-Feels with no leftover `#333` and legible glyphs; (3) the default 16 pt and one non-default `toolbar_icon_height`, where the search field grows with the glyph instead of clipping it while the 42 px row residual is unchanged; (4) HiDPI scale, where the glyph sits inside its reserved slot and never overlaps typed text; (5) the read-only Connect glyph and the switch's active state across a read-only transition with the gear enabled and reflecting panel visibility; (6) the search prompt show/hide and dimming behaviour, gear open/close from both the gear and `View -> Settings`, and the three tooltips/accessible names. A wrong or unreadable glyph, no accent adaptation in light or dark, a clipped magnifier, overlapping glyph/text at HiDPI, a visibly undimmed disabled Connect glyph, or a gear state that does not match the panel is a review blocker, not an accepted residual. This step is manual and is not part of the automated gate; record the observation, do not change code to satisfy it.

- [ ] **Step 6: Commit**

```bash
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/smoke/GraphWorkspaceUiEvidence.java \
    freeplane_plugin_graph/build.gradle \
    docs/superpowers/specs/images/2026-08-10-graph-workspace-implemented.png
git commit -m "test: record toolbar row width and regenerate the UI evidence [2026-09-12-graph-toolbar-affordances]"
```
