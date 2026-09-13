# Graph Maps Sidebar Resize Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Graph Workspace Maps sidebar resizable, hideable and per-workspace persistent, with a collapsed rail, a checkable `View → Maps sidebar` item, and one workspace mutation per layout gesture.

**Architecture:** `MapListPanel` moves inside a new `MapSidebarPanel` (rail plus list), which becomes the left component of a `JSplitPane` named `graph-workspace-split` in the graph area. One guarded apply path in `GraphWorkspaceWindowModel` (`applySidebarSettings`) is the only code that reads stored/session layout state or writes the `appliedSidebarWidth` commit baseline; three guarded entry points (`setSidebarCollapsed`, `commitSidebarWidth`, `resetSidebarWidth`) commit through the existing undoable `GraphCommands.display(...)` path. Width and hidden state are two new `DisplaySettings` fields persisted as optional `map-sidebar-width` / `map-sidebar-hidden` XML attributes (no format-version bump), with a session-only overlay while the binding is read-only. Pure clamp arithmetic lives in `MapSidebarLayout`.

**Tech Stack:** Java 8 language level, Swing (`JSplitPane`, `BasicSplitPaneUI`), JUnit 4 + AssertJ + Mockito, Gradle, XML via the existing `WorkspaceXmlCodec`, ISO-8859-1 translation bundles.

**Lane discipline:** Tasks are grouped into four lanes: `model-format` (Tasks 1–2), `sidebar-ui` (Tasks 3–6), `resources-assets` (Task 7), `window-wiring` (Tasks 8–11). A lane's tasks run sequentially. Lane file ownership is disjoint except for the one unavoidable handoff recorded here: Task 1 performs the atomic `DisplaySettings.of(...)` signature migration, which includes the four call sites in `GraphWorkspaceWindowModelShould.java`; after Task 1, that file is owned by `window-wiring`. No other task in any lane touches a file owned by another lane, and every cross-lane dependency is stated in the consuming task's `**Depends on:**` line.

## Global Constraints

- Java 8 language level (`-source 8`-compatible syntax): no `var`, no diamond with anonymous classes, no `List.of`, no records, no switch expressions. Run Gradle with Java 21 from `~/.sdkman/candidates/java/21.0.8-zulu`.
- No new dependencies and no new Gradle modules; only the existing JUnit 4, AssertJ, Mockito, Swing and GraphStream classpaths.
- Verification commands, exactly: `gradle :freeplane_plugin_graph:test`, `gradle :freeplane:test`, `gradle format_translation`, `gradle :freeplane_plugin_graph:graphOsgiSmoke`.
- Width constants, exactly: `MapSidebarLayout.DEFAULT_WIDTH = 264 (== DisplaySettings.DEFAULT_MAP_SIDEBAR_WIDTH)`, `MapSidebarLayout.MIN_WIDTH = 180`, `MapSidebarLayout.RAIL_WIDTH = 26`; logical split-pane divider size `6`, `0` while collapsed.
- XML attribute names, exactly: `map-sidebar-width` and `map-sidebar-hidden`, both optional on `display-settings`, both always written; `CURRENT_FORMAT_VERSION` stays `1` and no `WorkspaceMigration` is added.
- Do not add a compatibility overload of `DisplaySettings.of(...)`: the only factory signature is `of(boolean, CanvasTheme, boolean, boolean, int, boolean, List<UnknownXml>)`.
- Component names, exactly: `graph-workspace-split`, `graph-workspace-map-sidebar`, `graph-workspace-map-sidebar-rail`, `graph-workspace-map-sidebar-count`, `graph-workspace-map-sidebar-collapse`, `graph-workspace-map-sidebar-expand`, `graph-workspace-map-sidebar-label`, `graph-workspace-map-list-heading-row`, `graph-workspace-maps-sidebar-menu-item`; existing names `graph-workspace-graph-area`, `graph-workspace-map-list`, `graph-workspace-map-list-heading`, `graph-workspace-scroll-pane` stay unchanged.
- Translation keys and English values, exactly: `graph_workspace.action.maps_sidebar=Maps sidebar`, `graph_workspace.map_list.collapse=Hide maps sidebar`, `graph_workspace.map_list.expand=Show maps sidebar`, `graph_workspace.map_list.rail_label=MAPS`, `graph_workspace.map_list.rail_count={0} active maps`; `Resources_en.properties` stays ISO-8859-1 with pure ASCII values, and `gradle format_translation` owns the final key order.
- Regime conditions, exactly: Squeezed `U < MIN_WIDTH` (evaluated first), else Pinned `maxW == U`, else Normal `maxW > U`, with `U = MapSidebarLayout.effectiveMinimum(splitWidth, effectiveDividerWidth, insets)` and `maxW = MapSidebarLayout.maximumWidth(splitWidth)`.
- Commit baseline, exactly: `appliedSidebarWidth`, written only by `applySidebarSettings`; the property listener's re-clamp and the collapsed hold never write it.
- Every geometry call passes the effective laid-out divider width `((BasicSplitPaneUI) splitPane.getUI()).getDivider().getDividerSize()` (0 when the divider is null), never the logical `6`.
- All new window types are package-private `final` in `org.freeplane.plugin.graph.window`; `DisplaySettings` stays public in `org.freeplane.plugin.graph.workspace.model`.
- Every task follows TDD: write the failing test, run it and confirm the failure, implement the minimal change, run the tests and confirm they pass, then commit. Use `gradle` (never `gradlew`/`maven`) with escalation.
- Every commit message ends with the Task Identifier `[2026-09-12-graph-map-sidebar-resize]`.
- Change only the files named in a task's `**Files:**` block; do not refactor unrelated code.

## Task 1: DisplaySettings sidebar fields and the atomic call-site migration

**Implementer tier:** Standard

**Lane:** model-format

**Depends on:** none

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/model/DisplaySettings.java:8-85`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceSettingsPanel.java:145-153`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/io/WorkspaceXmlCodec.java:308-318`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GeneratedWorkspace.java:409-413`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java:1202-1206,1222-1228,1262-1266`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/WorkspaceCommandsShould.java:454-458`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/io/WorkspaceXmlCodecShould.java:234-240`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/WorkspaceHistoryShould.java:108-112,159-163,179-183`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/command/GraphCommandRouterShould.java:163-168`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/GraphWorkspacePresentationShould.java:27-31`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceModelAcceptanceShould.java:182-186`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/model/WorkspaceDomainShould.java:220-226`

**Interfaces:**

- Consumes: nothing from an earlier task; this is the first task.
- Produces: `DisplaySettings.DEFAULT_MAP_SIDEBAR_WIDTH = 264`; `DisplaySettings.of(boolean, CanvasTheme, boolean, boolean, int mapSidebarWidth, boolean mapSidebarHidden, List<UnknownXml>)`; `DisplaySettings.mapSidebarWidth(): int`; `DisplaySettings.mapSidebarHidden(): boolean`; `defaults()` returning width `264` and hidden `false`.

Note for the implementer: this is one atomic change. `gradle :freeplane_plugin_graph:test` compiles every test source file, so a single missed `DisplaySettings.of(...)` call site fails the whole compile. Do not add an overload.

- [ ] **Step 1: Write the failing model test**

Append these two methods to `WorkspaceDomainShould` (after `provideVersionOneDefaults`) and extend `provideVersionOneDefaults` with the two sidebar assertions shown.

```java
    @Test
    public void defaultsTheMapSidebarPresentationState() {
        assertThat(DisplaySettings.defaults().mapSidebarWidth()).isEqualTo(264);
        assertThat(DisplaySettings.defaults().mapSidebarWidth())
            .isEqualTo(DisplaySettings.DEFAULT_MAP_SIDEBAR_WIDTH);
        assertThat(DisplaySettings.defaults().mapSidebarHidden()).isFalse();
    }

    @Test
    public void includesTheMapSidebarFieldsInEqualityHashCodeAndToString() {
        DisplaySettings base = DisplaySettings.defaults();
        DisplaySettings wider = DisplaySettings.of(base.showArrowheads(), base.canvasTheme(),
            base.rememberViewport(), base.dimUnrelatedNodes(), 400, base.mapSidebarHidden(), base.unknownXml());
        DisplaySettings hidden = DisplaySettings.of(base.showArrowheads(), base.canvasTheme(),
            base.rememberViewport(), base.dimUnrelatedNodes(), base.mapSidebarWidth(), true, base.unknownXml());

        assertThat(wider).isNotEqualTo(base);
        assertThat(hidden).isNotEqualTo(base);
        assertThat(wider.hashCode()).isNotEqualTo(base.hashCode());
        assertThat(hidden.hashCode()).isNotEqualTo(base.hashCode());
        assertThat(base.toString()).contains("mapSidebarWidth=264").contains("mapSidebarHidden=false");
    }
```

Inside the existing `provideVersionOneDefaults` method, directly after the four existing `DisplaySettings.defaults()` assertions, add:

```java
        assertThat(DisplaySettings.defaults().mapSidebarWidth()).isEqualTo(264);
        assertThat(DisplaySettings.defaults().mapSidebarHidden()).isFalse();
```

Extend the existing `replaceCompleteDisplaySettingsAndTreatEqualSettingsAsNoChange` method in `WorkspaceCommandsShould`, directly after its existing `NO_OP` assertion, with the width-only case:

```java
        DisplaySettings wider = DisplaySettings.of(settings.showArrowheads(), settings.canvasTheme(),
            settings.rememberViewport(), settings.dimUnrelatedNodes(), 400, settings.mapSidebarHidden(),
            settings.unknownXml());
        WorkspaceTransition widthOnly = WorkspaceCommands.setDisplaySettings(wider).apply(updated.after());
        assertThat(widthOnly.status()).isEqualTo(WorkspaceTransition.Status.APPLIED);
        assertThat(widthOnly.after().displaySettings().mapSidebarWidth()).isEqualTo(400);

        WorkspaceTransition repeatedWidth = WorkspaceCommands.setDisplaySettings(wider).apply(widthOnly.after());
        assertThat(repeatedWidth.status()).isEqualTo(WorkspaceTransition.Status.NO_OP);
```

Add this method to `WorkspaceHistoryShould`:

```java
    @Test
    public void restoresSidebarWidthWithTheWholeDisplaySettingsSnapshot() {
        DisplaySettings before = DisplaySettings.of(true, DisplaySettings.CanvasTheme.LIGHT, true, true,
            DisplaySettings.DEFAULT_MAP_SIDEBAR_WIDTH, false, Collections.<UnknownXml>emptyList());
        DisplaySettings after = DisplaySettings.of(true, DisplaySettings.CanvasTheme.LIGHT, true, true, 400, true,
            Collections.<UnknownXml>emptyList());
        WorkspaceDocument document = document().toBuilder().displaySettings(before).build();
        WorkspaceHistory history = new WorkspaceHistory();

        WorkspaceTransition applied = history.execute(WorkspaceCommands.setDisplaySettings(after), document);
        assertThat(applied.status()).isEqualTo(WorkspaceTransition.Status.APPLIED);
        assertThat(applied.after().displaySettings().mapSidebarWidth()).isEqualTo(400);
        assertThat(applied.after().displaySettings().mapSidebarHidden()).isTrue();

        WorkspaceTransition undone = history.undo(applied.after());

        assertThat(undone.status()).isEqualTo(WorkspaceTransition.Status.APPLIED);
        assertThat(undone.after().displaySettings()).isEqualTo(before);
    }
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.workspace.model.WorkspaceDomainShould'`
Expected: FAIL, compilation error `cannot find symbol: method mapSidebarWidth()` / `method of(...)` argument mismatch.

- [ ] **Step 3: Replace `DisplaySettings.java` with the sidebar fields**

Replace the whole file body (keep the package declaration) with:

```java
package org.freeplane.plugin.graph.workspace.model;

import java.util.List;
import java.util.Objects;

public final class DisplaySettings {
    public enum CanvasTheme {
        FOLLOW_FREEPLANE,
        LIGHT,
        DARK
    }

    public static final int DEFAULT_MAP_SIDEBAR_WIDTH = 264;

    private final boolean showArrowheads;
    private final CanvasTheme canvasTheme;
    private final boolean rememberViewport;
    private final boolean dimUnrelatedNodes;
    private final int mapSidebarWidth;
    private final boolean mapSidebarHidden;
    private final List<UnknownXml> unknownXml;

    private DisplaySettings(final boolean showArrowheads, final CanvasTheme canvasTheme,
            final boolean rememberViewport, final boolean dimUnrelatedNodes, final int mapSidebarWidth,
            final boolean mapSidebarHidden, final List<UnknownXml> unknownXml) {
        this.showArrowheads = showArrowheads;
        this.canvasTheme = Objects.requireNonNull(canvasTheme, "canvasTheme");
        this.rememberViewport = rememberViewport;
        this.dimUnrelatedNodes = dimUnrelatedNodes;
        this.mapSidebarWidth = mapSidebarWidth;
        this.mapSidebarHidden = mapSidebarHidden;
        this.unknownXml = UnknownXml.forRecord(unknownXml);
    }

    public static DisplaySettings defaults() {
        return new DisplaySettings(true, CanvasTheme.FOLLOW_FREEPLANE, true, true,
            DEFAULT_MAP_SIDEBAR_WIDTH, false, java.util.Collections.<UnknownXml>emptyList());
    }

    public static DisplaySettings of(final boolean showArrowheads, final CanvasTheme canvasTheme,
            final boolean rememberViewport, final boolean dimUnrelatedNodes, final int mapSidebarWidth,
            final boolean mapSidebarHidden, final List<UnknownXml> unknownXml) {
        return new DisplaySettings(showArrowheads, canvasTheme, rememberViewport, dimUnrelatedNodes,
            mapSidebarWidth, mapSidebarHidden, unknownXml);
    }

    public boolean showArrowheads() {
        return showArrowheads;
    }

    public CanvasTheme canvasTheme() {
        return canvasTheme;
    }

    public boolean rememberViewport() {
        return rememberViewport;
    }

    public boolean dimUnrelatedNodes() {
        return dimUnrelatedNodes;
    }

    public int mapSidebarWidth() {
        return mapSidebarWidth;
    }

    public boolean mapSidebarHidden() {
        return mapSidebarHidden;
    }

    public List<UnknownXml> unknownXml() {
        return unknownXml;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DisplaySettings)) {
            return false;
        }
        final DisplaySettings that = (DisplaySettings) other;
        return showArrowheads == that.showArrowheads && rememberViewport == that.rememberViewport
            && dimUnrelatedNodes == that.dimUnrelatedNodes && canvasTheme == that.canvasTheme
            && mapSidebarWidth == that.mapSidebarWidth && mapSidebarHidden == that.mapSidebarHidden
            && unknownXml.equals(that.unknownXml);
    }

    @Override
    public int hashCode() {
        return Objects.hash(showArrowheads, canvasTheme, rememberViewport, dimUnrelatedNodes,
            mapSidebarWidth, mapSidebarHidden, unknownXml);
    }

    @Override
    public String toString() {
        return "DisplaySettings{" + "showArrowheads=" + showArrowheads + ", canvasTheme=" + canvasTheme
            + ", rememberViewport=" + rememberViewport + ", dimUnrelatedNodes=" + dimUnrelatedNodes
            + ", mapSidebarWidth=" + mapSidebarWidth + ", mapSidebarHidden=" + mapSidebarHidden
            + ", unknownXml=" + unknownXml + '}';
    }
}
```

- [ ] **Step 4: Pass the sidebar fields through `WorkspaceSettingsPanel.publishSettings()`**

Replace the `settings = DisplaySettings.of(...)` statement in `publishSettings()` (lines 148–151) with exactly:

```java
        settings = DisplaySettings.of(showArrowheads.isSelected(),
            (CanvasTheme) canvasTheme.getSelectedItem(), rememberViewport.isSelected(),
            dimUnrelated.isSelected(), settings.mapSidebarWidth(), settings.mapSidebarHidden(),
            settings.unknownXml());
```

- [ ] **Step 5: Migrate the remaining 14 call sites**

`WorkspaceXmlCodec.parseDisplaySettings` (lines 312–317) becomes exactly:

```java
        return DisplaySettings.of(
            booleanValue(requiredAttribute(element, "show-arrowheads"), "show-arrowheads"),
            enumValue(DisplaySettings.CanvasTheme.class, requiredAttribute(element, "canvas-theme"), "canvas-theme"),
            booleanValue(requiredAttribute(element, "remember-viewport"), "remember-viewport"),
            booleanValue(requiredAttribute(element, "dim-unrelated-nodes"), "dim-unrelated-nodes"),
            DisplaySettings.DEFAULT_MAP_SIDEBAR_WIDTH,
            false,
            unknownXml);
```

For each remaining call site, insert the single line `DisplaySettings.DEFAULT_MAP_SIDEBAR_WIDTH,` then the single line `false,` immediately before the argument that supplies `List<UnknownXml>` (the call's last argument). The sites, with the argument that follows the insertion point:

- `GeneratedWorkspace.java:411`: last argument is `NO_UNKNOWN_XML`.
- `GraphWorkspaceWindowModelShould.java:1204`: last argument is `emptyUnknownXml()`.
- `GraphWorkspaceWindowModelShould.java:1224`: last argument is `emptyUnknownXml()`.
- `GraphWorkspaceWindowModelShould.java:1226`: last argument is `emptyUnknownXml()`.
- `GraphWorkspaceWindowModelShould.java:1264`: last argument is `emptyUnknownXml()`.
- `WorkspaceCommandsShould.java:456`: last argument is `Collections.singletonList(unknown("display", "kept"))`.
- `WorkspaceXmlCodecShould.java:236`: last argument is the `Arrays.asList(unknownAttribute(...), unknownElement(...))` expression.
- `WorkspaceHistoryShould.java:110`: last argument is `Collections.<UnknownXml>emptyList()`.
- `WorkspaceHistoryShould.java:161`: last argument is `Collections.<UnknownXml>emptyList()` on the same statement.
- `WorkspaceHistoryShould.java:181`: last argument is `Collections.<UnknownXml>emptyList()`.
- `GraphCommandRouterShould.java:165`: last argument is the `Collections.singletonList(UnknownXml.attribute(...))` expression starting on the same statement.
- `GraphWorkspacePresentationShould.java:29`: last argument is `Collections.singletonList(unknown)`.
- `GraphWorkspaceModelAcceptanceShould.java:184`: last argument is `noUnknownXml()`.

The resulting tail of e.g. `GraphWorkspacePresentationShould.java:29` is exactly:

```java
        DisplaySettings settings = DisplaySettings.of(false, CanvasTheme.DARK, false, false,
            DisplaySettings.DEFAULT_MAP_SIDEBAR_WIDTH, false, Collections.singletonList(unknown));
```

For `WorkspaceHistoryShould.java:161` (a single-line call), the result is exactly:

```java
        WorkspaceTransition second = history.execute(WorkspaceCommands.setDisplaySettings(DisplaySettings.of(false,
            DisplaySettings.CanvasTheme.LIGHT, true, true, DisplaySettings.DEFAULT_MAP_SIDEBAR_WIDTH, false,
            Collections.<UnknownXml>emptyList())), undone.after());
```

- [ ] **Step 6: Run the tests and confirm they pass**

Run: `gradle :freeplane_plugin_graph:test`
Expected: PASS for the whole plugin suite (the signature migration compiles every source set).

- [ ] **Step 7: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/model/DisplaySettings.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceSettingsPanel.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/io/WorkspaceXmlCodec.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GeneratedWorkspace.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/WorkspaceCommandsShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/io/WorkspaceXmlCodecShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/WorkspaceHistoryShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/command/GraphCommandRouterShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/GraphWorkspacePresentationShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceModelAcceptanceShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/model/WorkspaceDomainShould.java
git commit -m "feat(graph): persist map sidebar width and hidden state in DisplaySettings [2026-09-12-graph-map-sidebar-resize]"
```

## Task 2: Persist the two sidebar attributes in WorkspaceXmlCodec

**Implementer tier:** Standard

**Lane:** model-format

**Depends on:** 1

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/io/WorkspaceXmlCodec.java:308-318`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/io/WorkspaceXmlCodec.java:565-577`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/io/WorkspaceXmlCodecShould.java:211-241`

**Interfaces:**

- Consumes: `DisplaySettings.of(boolean, CanvasTheme, boolean, boolean, int, boolean, List<UnknownXml>)`, `DisplaySettings.mapSidebarWidth()`, `DisplaySettings.mapSidebarHidden()` and `DisplaySettings.DEFAULT_MAP_SIDEBAR_WIDTH` from Task 1.
- Produces: decoding of optional `map-sidebar-width` / `map-sidebar-hidden` attributes (defaults `264` / `false`; malformed values throw `WorkspaceFormatException` with the messages in the note below) and writing of both attributes after `dim-unrelated-nodes` on every save.

- [ ] **Step 1: Write the failing codec tests**

Append these five methods to `WorkspaceXmlCodecShould` and add the private `displaySettingsDocument(String extraAttribute)` helper (`extraAttribute` includes its own leading space, e.g. `" map-sidebar-width=\"0\""`).

```java
    @Test
    public void roundTripsNonDefaultMapSidebarAttributes() throws Exception {
        DisplaySettings settings = DisplaySettings.of(false, DisplaySettings.CanvasTheme.DARK, false, true,
            400, true, Collections.<UnknownXml>emptyList());
        WorkspaceDocument document = WorkspaceDocument.createVersion1(WORKSPACE_ID).toBuilder()
            .displaySettings(settings)
            .build();
        Path location = temporaryFolder.newFolder("sidebar-round-trip").toPath().resolve("workspace.fpg");

        byte[] firstWrite = codec().write(document, location);
        String xml = new String(firstWrite, StandardCharsets.UTF_8);
        assertThat(xml).contains("map-sidebar-width=\"400\"").contains("map-sidebar-hidden=\"true\"");

        Files.write(location, firstWrite);
        WorkspaceDocument reread = codec().read(location);
        assertThat(reread.displaySettings().mapSidebarWidth()).isEqualTo(400);
        assertThat(reread.displaySettings().mapSidebarHidden()).isTrue();
        assertThat(codec().write(reread, location)).isEqualTo(firstWrite);
    }

    @Test
    public void defaultsAbsentMapSidebarAttributesTo264AndExpanded() throws Exception {
        WorkspaceDocument document = codec().read(resource("format-1-full.fpg"));

        assertThat(document.displaySettings().mapSidebarWidth())
            .isEqualTo(DisplaySettings.DEFAULT_MAP_SIDEBAR_WIDTH);
        assertThat(document.displaySettings().mapSidebarHidden()).isFalse();
        assertThat(document).isEqualTo(fullDocument());
    }

    @Test
    public void writesTheMapSidebarAttributesOnEverySave() throws Exception {
        WorkspaceDocument document = codec().read(resource("format-1-full.fpg"));
        Path location = temporaryFolder.newFolder("sidebar-defaults").toPath().resolve("workspace.fpg");

        String xml = new String(codec().write(document, location), StandardCharsets.UTF_8);

        assertThat(xml).containsOnlyOnce("map-sidebar-width=\"264\"");
        assertThat(xml).containsOnlyOnce("map-sidebar-hidden=\"false\"");
    }

    @Test
    public void preservesForeignDisplaySettingsAttributesAlongsideTheKnownMapSidebarAttributes() throws Exception {
        WorkspaceDocument document = codec().read(resource("format-1-full.fpg"));
        Path location = temporaryFolder.newFolder("sidebar-foreign").toPath().resolve("workspace.fpg");

        byte[] written = codec().write(document, location);
        String xml = new String(written, StandardCharsets.UTF_8);

        assertThat(xml).containsOnlyOnce("future:display-attribute=\"display-value\"");
        Files.write(location, written);
        assertThat(codec().read(location).displaySettings().unknownXml()).contains(
            unknownAttribute(UnknownXml.Owner.RECORD, "display-attribute", "display"));
    }

    @Test
    public void rejectsMalformedMapSidebarAttributes() throws Exception {
        String[] malformedWidths = { "", "abc", "0", "-1" };
        for (final String width : malformedWidths) {
            Path location = temporaryFolder.newFile("malformed-width-" + Math.abs(width.hashCode()) + ".fpg")
                .toPath();
            Files.write(location,
                displaySettingsDocument(" map-sidebar-width=\"" + width + "\"").getBytes(StandardCharsets.UTF_8));
            assertThatThrownBy(() -> codec().read(location)).isInstanceOf(WorkspaceFormatException.class)
                .hasMessageContaining("map-sidebar-width");
        }
        Path hidden = temporaryFolder.newFile("malformed-hidden.fpg").toPath();
        Files.write(hidden,
            displaySettingsDocument(" map-sidebar-hidden=\"yes\"").getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> codec().read(hidden)).isInstanceOf(WorkspaceFormatException.class)
            .hasMessageContaining("map-sidebar-hidden");
    }

    private static String displaySettingsDocument(final String extraAttribute) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<graph-workspace format-version=\"1\" id=\"" + WORKSPACE_ID + "\">"
            + "<maps></maps><relationships></relationships><pins></pins>"
            + "<viewport center-x=\"0\" center-y=\"0\" zoom=\"1\"/>"
            + "<display-settings show-arrowheads=\"true\" canvas-theme=\"FOLLOW_FREEPLANE\" "
            + "remember-viewport=\"true\" dim-unrelated-nodes=\"true\"" + extraAttribute + "/>"
            + "</graph-workspace>";
    }
```

Also update `fullDocument()`'s `DisplaySettings.of(...)` call (already migrated in Task 1 to `264`/`false`) so the backward-compatibility case is honest: the fixture `format-1-full.fpg` keeps only the four old attributes, and `fullDocument()` keeps `264`/`false`.

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.workspace.io.WorkspaceXmlCodecShould'`
Expected: FAIL — `roundTripsNonDefaultMapSidebarAttributes` fails because the written XML lacks the two attributes and the reread width is `264`, and `rejectsMalformedMapSidebarAttributes` fails because malformed values are silently ignored.

- [ ] **Step 3: Decode the two optional attributes**

Replace `parseDisplaySettings` (lines 308–318) with exactly:

```java
    private static DisplaySettings parseDisplaySettings(final Element element) {
        final List<UnknownXml> unknownXml = recordUnknownXml(element,
            new String[] { "show-arrowheads", "canvas-theme", "remember-viewport", "dim-unrelated-nodes",
                "map-sidebar-width", "map-sidebar-hidden" },
            Collections.<String>emptyList());
        return DisplaySettings.of(
            booleanValue(requiredAttribute(element, "show-arrowheads"), "show-arrowheads"),
            enumValue(DisplaySettings.CanvasTheme.class, requiredAttribute(element, "canvas-theme"), "canvas-theme"),
            booleanValue(requiredAttribute(element, "remember-viewport"), "remember-viewport"),
            booleanValue(requiredAttribute(element, "dim-unrelated-nodes"), "dim-unrelated-nodes"),
            element.hasAttribute("map-sidebar-width")
                ? positiveInt(requiredAttribute(element, "map-sidebar-width"), "map-sidebar-width")
                : DisplaySettings.DEFAULT_MAP_SIDEBAR_WIDTH,
            element.hasAttribute("map-sidebar-hidden")
                ? booleanValue(requiredAttribute(element, "map-sidebar-hidden"), "map-sidebar-hidden")
                : false,
            unknownXml);
    }
```

The messages produced by the existing helpers are exactly: empty width `Required map-sidebar-width attribute on display-settings must not be empty`; non-numeric width `map-sidebar-width must be an integer`; zero/negative width `map-sidebar-width must be positive`; hidden other than `true`/`false` `map-sidebar-hidden must be true or false`.

- [ ] **Step 4: Write both attributes on every save**

Replace the `attributes(...)` argument list in `displaySettingsXml` (lines 567–572) with exactly:

```java
        final NamespaceScope scope = appendKnownStart(output, "display-settings", attributes(
            "show-arrowheads", Boolean.toString(settings.showArrowheads()),
            "canvas-theme", settings.canvasTheme().name(),
            "remember-viewport", Boolean.toString(settings.rememberViewport()),
            "dim-unrelated-nodes", Boolean.toString(settings.dimUnrelatedNodes()),
            "map-sidebar-width", Integer.toString(settings.mapSidebarWidth()),
            "map-sidebar-hidden", Boolean.toString(settings.mapSidebarHidden())),
            unknownAttributes(settings.unknownXml(), UnknownXml.Owner.RECORD), parent);
```

- [ ] **Step 5: Run the tests and confirm they pass**

Run: `gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.workspace.io.WorkspaceXmlCodecShould'`
Expected: PASS, including `roundTripAllKnownAndUnknownFieldsWithoutWritingDuringRead` and the malformed cases.

Run: `gradle :freeplane_plugin_graph:test`
Expected: PASS for the whole plugin suite.

- [ ] **Step 6: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/io/WorkspaceXmlCodec.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/io/WorkspaceXmlCodecShould.java
git commit -m "feat(graph): persist map sidebar width and hidden state in workspace XML [2026-09-12-graph-map-sidebar-resize]"
```

## Task 3: MapSidebarLayout clamp arithmetic

**Implementer tier:** Fast

**Lane:** sidebar-ui

**Depends on:** 1

**Files:**

- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/MapSidebarLayout.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/MapSidebarLayoutShould.java`

**Interfaces:**

- Consumes: `DisplaySettings.DEFAULT_MAP_SIDEBAR_WIDTH` from Task 1.
- Produces: `MapSidebarLayout.DEFAULT_WIDTH: int = 264`, `MapSidebarLayout.MIN_WIDTH: int = 180`, `MapSidebarLayout.RAIL_WIDTH: int = 26`; `maximumWidth(int splitWidth): int`; `effectiveMinimum(int splitWidth, int effectiveDividerWidth, Insets insets): int`; `clampWidth(int requestedWidth, int splitWidth, int effectiveDividerWidth, Insets insets): int`; `canvasMinimumWidth(int splitWidth, int effectiveDividerWidth, Insets insets): int`. All are static, pure and package-private; `Insets` must be non-null at every call site.

- [ ] **Step 1: Write the failing arithmetic test**

```java
package org.freeplane.plugin.graph.window;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Insets;

import org.freeplane.plugin.graph.workspace.model.DisplaySettings;
import org.junit.Test;

public class MapSidebarLayoutShould {
    private static final Insets NO_INSETS = new Insets(0, 0, 0, 0);
    private static final Insets METAL_INSETS = new Insets(1, 1, 1, 1);

    @Test
    public void exposesTheApprovedWidthConstants() {
        assertThat(MapSidebarLayout.DEFAULT_WIDTH).isEqualTo(264);
        assertThat(MapSidebarLayout.DEFAULT_WIDTH).isEqualTo(DisplaySettings.DEFAULT_MAP_SIDEBAR_WIDTH);
        assertThat(MapSidebarLayout.MIN_WIDTH).isEqualTo(180);
        assertThat(MapSidebarLayout.RAIL_WIDTH).isEqualTo(26);
    }

    @Test
    public void computesMaximumWidthAsHalfTheSplitWidthWithTheMinimumFloor() {
        assertThat(MapSidebarLayout.maximumWidth(360)).isEqualTo(180);
        assertThat(MapSidebarLayout.maximumWidth(400)).isEqualTo(200);
        assertThat(MapSidebarLayout.maximumWidth(800)).isEqualTo(400);
        assertThat(MapSidebarLayout.maximumWidth(1000)).isEqualTo(500);
        assertThat(MapSidebarLayout.maximumWidth(0)).isEqualTo(180);
    }

    @Test
    public void computesTheEffectiveMinimumFromTheLayoutGeometry() {
        assertThat(MapSidebarLayout.effectiveMinimum(1000, 6, NO_INSETS)).isEqualTo(180);
        assertThat(MapSidebarLayout.effectiveMinimum(362, 6, NO_INSETS)).isEqualTo(180);
        assertThat(MapSidebarLayout.effectiveMinimum(186, 6, NO_INSETS)).isEqualTo(180);
        assertThat(MapSidebarLayout.effectiveMinimum(185, 6, NO_INSETS)).isEqualTo(179);
        assertThat(MapSidebarLayout.effectiveMinimum(150, 6, NO_INSETS)).isEqualTo(144);
        assertThat(MapSidebarLayout.effectiveMinimum(100, 6, NO_INSETS)).isEqualTo(94);
        assertThat(MapSidebarLayout.effectiveMinimum(6, 6, NO_INSETS)).isEqualTo(0);
        assertThat(MapSidebarLayout.effectiveMinimum(0, 6, NO_INSETS)).isEqualTo(0);
        assertThat(MapSidebarLayout.effectiveMinimum(150, 6, METAL_INSETS)).isEqualTo(142);
        assertThat(MapSidebarLayout.effectiveMinimum(186, 6, METAL_INSETS)).isEqualTo(178);
    }

    @Test
    public void clampsWidthsIntoTheEffectiveRange() {
        assertThat(MapSidebarLayout.clampWidth(100, 1000, 6, NO_INSETS)).isEqualTo(180);
        assertThat(MapSidebarLayout.clampWidth(180, 1000, 6, NO_INSETS)).isEqualTo(180);
        assertThat(MapSidebarLayout.clampWidth(500, 1000, 6, NO_INSETS)).isEqualTo(500);
        assertThat(MapSidebarLayout.clampWidth(501, 1000, 6, NO_INSETS)).isEqualTo(500);
        assertThat(MapSidebarLayout.clampWidth(1000, 1000, 6, NO_INSETS)).isEqualTo(500);
        assertThat(MapSidebarLayout.clampWidth(264, 150, 6, NO_INSETS)).isEqualTo(180);
        assertThat(MapSidebarLayout.clampWidth(0, 150, 6, NO_INSETS)).isEqualTo(144);
    }

    @Test
    public void keepsTheClampRangeNonEmptyAtEverySplitWidth() {
        for (int splitWidth = 0; splitWidth <= 1500; splitWidth++) {
            int lower = MapSidebarLayout.effectiveMinimum(splitWidth, 6, NO_INSETS);
            int upper = Math.max(lower, MapSidebarLayout.maximumWidth(splitWidth));
            assertThat(lower).isLessThanOrEqualTo(upper);
            assertThat(MapSidebarLayout.clampWidth(lower, splitWidth, 6, NO_INSETS)).isEqualTo(lower);
            assertThat(MapSidebarLayout.clampWidth(upper, splitWidth, 6, NO_INSETS)).isEqualTo(upper);
        }
    }

    @Test
    public void keepsCanvasMinimumWidthNonNegativeAndMonotonic() {
        int previous = -1;
        for (int splitWidth = 0; splitWidth <= 1500; splitWidth++) {
            int value = MapSidebarLayout.canvasMinimumWidth(splitWidth, 6, NO_INSETS);
            assertThat(value).isGreaterThanOrEqualTo(0);
            assertThat(value).isGreaterThanOrEqualTo(previous);
            previous = value;
        }
    }

    @Test
    public void computesTheCanvasMinimumFromTheLayoutGeometry() {
        assertThat(MapSidebarLayout.canvasMinimumWidth(1000, 6, NO_INSETS)).isEqualTo(494);
        assertThat(MapSidebarLayout.canvasMinimumWidth(362, 6, NO_INSETS)).isEqualTo(175);
        assertThat(MapSidebarLayout.canvasMinimumWidth(150, 6, NO_INSETS)).isEqualTo(0);
        assertThat(MapSidebarLayout.canvasMinimumWidth(100, 6, NO_INSETS)).isEqualTo(0);
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.window.MapSidebarLayoutShould'`
Expected: FAIL, `cannot find symbol: class MapSidebarLayout`.

- [ ] **Step 3: Write the minimal implementation**

```java
package org.freeplane.plugin.graph.window;

import java.awt.Insets;

import org.freeplane.plugin.graph.workspace.model.DisplaySettings;

final class MapSidebarLayout {
    static final int DEFAULT_WIDTH = DisplaySettings.DEFAULT_MAP_SIDEBAR_WIDTH;
    static final int MIN_WIDTH = 180;
    static final int RAIL_WIDTH = 26;

    private MapSidebarLayout() {
    }

    static int maximumWidth(final int splitWidth) {
        return Math.max(MIN_WIDTH, splitWidth / 2);
    }

    static int effectiveMinimum(final int splitWidth, final int effectiveDividerWidth, final Insets insets) {
        return Math.max(0, Math.min(MIN_WIDTH,
            splitWidth - effectiveDividerWidth - insets.left - insets.right));
    }

    static int clampWidth(final int requestedWidth, final int splitWidth, final int effectiveDividerWidth,
            final Insets insets) {
        final int lower = effectiveMinimum(splitWidth, effectiveDividerWidth, insets);
        final int upper = Math.max(lower, maximumWidth(splitWidth));
        return Math.max(lower, Math.min(upper, requestedWidth));
    }

    static int canvasMinimumWidth(final int splitWidth, final int effectiveDividerWidth, final Insets insets) {
        return Math.max(0, splitWidth - effectiveDividerWidth - insets.right
            - Math.max(effectiveMinimum(splitWidth, effectiveDividerWidth, insets), maximumWidth(splitWidth)));
    }
}
```

- [ ] **Step 4: Run the test and confirm it passes**

Run: `gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.window.MapSidebarLayoutShould'`
Expected: PASS, 7 tests.

- [ ] **Step 5: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/MapSidebarLayout.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/MapSidebarLayoutShould.java
git commit -m "feat(graph): add sidebar clamp arithmetic [2026-09-12-graph-map-sidebar-resize]"
```

## Task 4: MapSidebarPanel, MapSidebarRail and the MapListPanel heading row

**Implementer tier:** Advanced

**Lane:** sidebar-ui

**Depends on:** 3

**Files:**

- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/MapSidebarRail.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/MapSidebarPanel.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/MapListPanel.java:40,196-202,336-337,556-600`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/MapSidebarPanelShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/MapListPanelShould.java:30-60`

**Interfaces:**

- Consumes: `MapSidebarLayout.DEFAULT_WIDTH = 264`, `MapSidebarLayout.MIN_WIDTH = 180`, `MapSidebarLayout.RAIL_WIDTH = 26` from Task 3; the existing `MapListPanel(GraphWorkspaceHandle handle, Supplier<Path> pathChooser, DeleteConfirmationPrompt deletePrompt)` constructor and `MapListPanel.setReadOnly(boolean readOnly): void`.
- Produces: `MapSidebarRail()` with `getPreferredSize(): Dimension`, `setActiveMapCount(int count): void`, `restoreButton(): JButton`, `countBadge(): JLabel`; `MapSidebarPanel(MapListPanel mapList, CollapsedListener listener)` with `CollapsedListener.collapsedChanged(boolean collapsed): void`, `setCollapsed(boolean collapsed): void`, `isCollapsed(): boolean`, `setActiveMapCount(int count): void`, `setReadOnly(boolean readOnly): void`, `mapList(): MapListPanel`, `rail(): MapSidebarRail`, `collapseButton(): JButton`, and `static JButton chevronButton(String textKey, String name, String iconPath)`; `MapListPanel.collapseButton(): JButton` and `MapListPanel.syncListWidths(): void`.

Note: `MapSidebarPanel` calls `mapList.collapseButton()` and `MapListPanel` calls `MapSidebarPanel.chevronButton(...)`, so both classes and the `MapListPanel` change land in one commit. All three classes stay `final` and package-private. `chevronButton` resolves the icon through `ResourceController.getResourceController().getOptionalIcon(iconPath)`; when the icon is non-null the button is icon-only with a tooltip and accessible name from `textKey`, otherwise the localized text stays and no tooltip or accessible name is set. It never calls `setDisabledIcon`.

- [ ] **Step 1: Write the failing panel and rail test**

```java
package org.freeplane.plugin.graph.window;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.TextUtils;
import org.freeplane.plugin.graph.control.GraphWorkspaceHandle;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

public class MapSidebarPanelShould {
    private MockedStatic<TextUtils> textUtils;
    private MockedStatic<ResourceController> resourceController;

    @Before
    public void setUp() {
        resourceController = org.mockito.Mockito.mockStatic(ResourceController.class);
        resourceController.when(ResourceController::getResourceController)
            .thenReturn(mock(ResourceController.class));
        textUtils = org.mockito.Mockito.mockStatic(TextUtils.class);
        textUtils.when(() -> TextUtils.getText(any(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        textUtils.when(() -> TextUtils.getText(any(String.class), any(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        textUtils.when(() -> TextUtils.getRawText(any(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        textUtils.when(() -> TextUtils.getRawText(any(String.class), any(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        textUtils.when(() -> TextUtils.format(any(String.class), any(Object[].class)))
            .thenAnswer(invocation -> {
                Object[] arguments = invocation.getArguments();
                Object[] formatArguments = arguments.length == 2 && arguments[1] instanceof Object[]
                    ? (Object[]) arguments[1] : Arrays.copyOfRange(arguments, 1, arguments.length);
                return arguments[0] + Arrays.toString(formatArguments);
            });
    }

    @After
    public void tearDown() {
        textUtils.close();
        resourceController.close();
    }

    private static MapListPanel mapList() {
        GraphWorkspaceHandle handle = mock(GraphWorkspaceHandle.class);
        return new MapListPanel(handle, () -> Paths.get("/tmp/map.mm"), (parent, name) -> true);
    }

    private static MapSidebarPanel panel() {
        return new MapSidebarPanel(mapList(), collapsed -> { });
    }

    @Test
    public void showsOnlyOneOfRailAndMapList() {
        MapSidebarPanel panel = panel();
        assertThat(panel.mapList().isVisible()).isTrue();
        assertThat(panel.rail().isVisible()).isFalse();

        panel.setCollapsed(true);
        assertThat(panel.rail().isVisible()).isTrue();
        assertThat(panel.mapList().isVisible()).isFalse();

        panel.setCollapsed(false);
        assertThat(panel.mapList().isVisible()).isTrue();
        assertThat(panel.rail().isVisible()).isFalse();
    }

    @Test
    public void flipsThePreferredAndMinimumWidthsWithTheCollapsedFlag() {
        MapSidebarPanel panel = panel();
        assertThat(panel.getPreferredSize().width).isEqualTo(264);
        assertThat(panel.getMinimumSize().width).isEqualTo(180);

        panel.setCollapsed(true);
        assertThat(panel.getPreferredSize().width).isEqualTo(26);
        assertThat(panel.getMinimumSize().width).isEqualTo(26);
    }

    @Test
    public void notifiesTheCollapseListenerOncePerRealChange() {
        List<Boolean> changes = new ArrayList<Boolean>();
        MapSidebarPanel panel = new MapSidebarPanel(mapList(), changes::add);

        assertThat(changes).isEmpty();
        panel.setCollapsed(true);
        assertThat(changes).containsExactly(Boolean.TRUE);
        panel.setCollapsed(true);
        assertThat(changes).containsExactly(Boolean.TRUE);
        panel.setCollapsed(false);
        assertThat(changes).containsExactly(Boolean.TRUE, Boolean.FALSE);
    }

    @Test
    public void updatesTheRailBadgeFromTheActiveMapCount() {
        MapSidebarPanel panel = panel();

        panel.setActiveMapCount(3);
        assertThat(panel.rail().countBadge().getText()).isEqualTo("3");
        assertThat(panel.rail().countBadge().getToolTipText())
            .isEqualTo("graph_workspace.map_list.rail_count[3]");

        panel.setActiveMapCount(0);
        assertThat(panel.rail().countBadge().getText()).isEmpty();
        assertThat(panel.rail().countBadge().getToolTipText())
            .isEqualTo("graph_workspace.map_list.rail_count[0]");

        panel.setActiveMapCount(0);
        assertThat(panel.rail().countBadge().getText()).isEmpty();
    }

    @Test
    public void forwardsReadOnlyToTheMapListWithoutChangingVisibility() {
        MapSidebarPanel panel = panel();

        panel.setReadOnly(true);
        assertThat(panel.mapList().isReadOnly()).isTrue();
        assertThat(panel.mapList().isVisible()).isTrue();
        assertThat(panel.rail().isVisible()).isFalse();

        panel.setReadOnly(false);
        assertThat(panel.mapList().isReadOnly()).isFalse();
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.window.MapSidebarPanelShould'`
Expected: FAIL, `cannot find symbol: class MapSidebarPanel`.

- [ ] **Step 3: Implement `MapSidebarPanel`**

```java
package org.freeplane.plugin.graph.window;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import java.util.Objects;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JPanel;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.TextUtils;

final class MapSidebarPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    @FunctionalInterface
    interface CollapsedListener {
        void collapsedChanged(boolean collapsed);
    }

    private final MapListPanel mapList;
    private final MapSidebarRail rail;
    private final CollapsedListener listener;
    private boolean collapsed;
    private boolean readOnly;

    MapSidebarPanel(final MapListPanel mapList, final CollapsedListener listener) {
        this.mapList = Objects.requireNonNull(mapList, "mapList");
        this.listener = Objects.requireNonNull(listener, "listener");
        setName("graph-workspace-map-sidebar");
        setLayout(new BorderLayout());
        rail = new MapSidebarRail();
        rail.restoreButton().addActionListener(event -> setCollapsed(false));
        add(rail, BorderLayout.WEST);
        add(mapList, BorderLayout.CENTER);
        applyCollapsedState(false);
    }

    void setCollapsed(final boolean collapsed) {
        if (this.collapsed == collapsed) {
            return;
        }
        this.collapsed = collapsed;
        applyCollapsedState(collapsed);
        listener.collapsedChanged(collapsed);
    }

    boolean isCollapsed() {
        return collapsed;
    }

    void setActiveMapCount(final int count) {
        rail.setActiveMapCount(count);
    }

    void setReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        mapList.setReadOnly(readOnly);
    }

    MapListPanel mapList() {
        return mapList;
    }

    MapSidebarRail rail() {
        return rail;
    }

    JButton collapseButton() {
        return mapList.collapseButton();
    }

    static JButton chevronButton(final String textKey, final String name, final String iconPath) {
        final JButton button = new JButton(TextUtils.getText(textKey));
        final Icon icon = ResourceController.getResourceController().getOptionalIcon(iconPath);
        if (icon != null) {
            button.setIcon(icon);
            button.setText(null);
            button.setToolTipText(TextUtils.getText(textKey));
            button.getAccessibleContext().setAccessibleName(TextUtils.getText(textKey));
        }
        button.setName(name);
        button.setMargin(new Insets(2, 7, 2, 7));
        button.setFocusable(false);
        return button;
    }

    private void applyCollapsedState(final boolean collapsed) {
        rail.setVisible(collapsed);
        mapList.setVisible(!collapsed);
        final int width = collapsed ? MapSidebarLayout.RAIL_WIDTH : MapSidebarLayout.DEFAULT_WIDTH;
        setPreferredSize(new Dimension(width, 0));
        setMinimumSize(new Dimension(collapsed ? MapSidebarLayout.RAIL_WIDTH : MapSidebarLayout.MIN_WIDTH, 0));
    }
}
```

- [ ] **Step 4: Implement `MapSidebarRail`**

```java
package org.freeplane.plugin.graph.window;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.freeplane.core.util.TextUtils;

final class MapSidebarRail extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final Dimension RESTORE_BUTTON_SIZE = new Dimension(18, 18);

    private final JButton restoreButton = MapSidebarPanel.chevronButton(
        "graph_workspace.map_list.expand", "graph-workspace-map-sidebar-expand",
        "/images/MapSidebarExpand.svg?useAccentColor=true");
    private final JLabel countBadge = new JLabel();
    private final JLabel label = new VerticalLabel();
    private int activeMapCount = -1;

    MapSidebarRail() {
        setName("graph-workspace-map-sidebar-rail");
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        restoreButton.setPreferredSize(RESTORE_BUTTON_SIZE);
        restoreButton.setMaximumSize(RESTORE_BUTTON_SIZE);
        restoreButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        countBadge.setName("graph-workspace-map-sidebar-count");
        countBadge.setAlignmentX(Component.CENTER_ALIGNMENT);
        label.setName("graph-workspace-map-sidebar-label");
        label.setFont(label.getFont().deriveFont(java.awt.Font.BOLD, 10f));
        label.setForeground(java.awt.Color.GRAY);
        label.setOpaque(false);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        add(restoreButton);
        add(countBadge);
        add(label);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(MapSidebarLayout.RAIL_WIDTH, super.getPreferredSize().height);
    }

    void setActiveMapCount(final int count) {
        if (activeMapCount == count) {
            return;
        }
        activeMapCount = count;
        countBadge.setText(count == 0 ? "" : Integer.toString(count));
        countBadge.setToolTipText(TextUtils.format("graph_workspace.map_list.rail_count", Integer.valueOf(count)));
    }

    JButton restoreButton() {
        return restoreButton;
    }

    JLabel countBadge() {
        return countBadge;
    }

    private static final class VerticalLabel extends JLabel {
        private static final long serialVersionUID = 1L;

        private VerticalLabel() {
            super(TextUtils.getText("graph_workspace.map_list.rail_label"));
        }

        @Override
        public Dimension getPreferredSize() {
            final FontMetrics metrics = getFontMetrics(getFont());
            return new Dimension(metrics.getAscent() + metrics.getDescent(), metrics.stringWidth(getText()));
        }

        @Override
        protected void paintComponent(final Graphics graphics) {
            final Graphics2D copy = (Graphics2D) graphics.create();
            try {
                copy.rotate(Math.toRadians(90), getWidth() / 2.0, getHeight() / 2.0);
                super.paintComponent(copy);
            }
            finally {
                copy.dispose();
            }
        }
    }
}
```

- [ ] **Step 5: Write the failing `MapListPanelShould` additions**

In `MapListPanelShould`, add the `java.awt.Component`, `javax.swing.Icon`, `javax.swing.JLabel`, `javax.swing.JPanel`, `javax.swing.ListCellRenderer` and `static org.mockito.Mockito.when` imports, store the controller in a field, and append the five test methods plus the two helpers below. The `setUp()` change replaces the inline `thenReturn(mock(ResourceController.class))` with `thenReturn(controller)` after `controller = mock(ResourceController.class);`.

```java
    private ResourceController controller;

    private MapListPanel panel() {
        GraphWorkspaceHandle handle = mock(GraphWorkspaceHandle.class);
        return new MapListPanel(handle, () -> Paths.get("/tmp/map.mm"), (parent, name) -> true);
    }

    private static Component componentByName(final java.awt.Container parent, final String name) {
        for (final Component component : parent.getComponents()) {
            if (name.equals(component.getName())) {
                return component;
            }
        }
        throw new AssertionError("Missing component " + name);
    }

    @Test
    public void hostsTheHeadingLabelAndTheNamedCollapseButton() {
        MapListPanel panel = panel();

        JPanel headingRow = (JPanel) componentByName(panel, "graph-workspace-map-list-heading-row");
        assertThat(componentByName(headingRow, "graph-workspace-map-list-heading")).isInstanceOf(JLabel.class);
        assertThat(panel.collapseButton().getName()).isEqualTo("graph-workspace-map-sidebar-collapse");
        assertThat(panel.collapseButton().getText()).isEqualTo("graph_workspace.map_list.collapse");
    }

    @Test
    public void resolvesTheCollapseChevronIconAndFallsBackToText() {
        Icon icon = mock(Icon.class);
        when(controller.getOptionalIcon("/images/MapSidebarCollapse.svg?useAccentColor=true")).thenReturn(icon);
        MapListPanel panel = panel();

        assertThat(panel.collapseButton().getIcon()).isSameAs(icon);
        assertThat(panel.collapseButton().getText()).isNull();
        assertThat(panel.collapseButton().getToolTipText()).isEqualTo("graph_workspace.map_list.collapse");
        assertThat(panel.collapseButton().getAccessibleContext().getAccessibleName())
            .isEqualTo("graph_workspace.map_list.collapse");
    }

    @Test
    public void tracksTheListWidthWithThePanelContentWidthOnSetRows() {
        MapListPanel panel = panel();
        panel.setSize(400, 600);
        panel.setRows(Arrays.asList(
            MapListPanel.MapRow.of(ACTIVE_MAP, "Active Map", MapListPanel.RowState.ACTIVE, MapPartition.ACTIVE, 5, false),
            MapListPanel.MapRow.of(INACTIVE_MAP, "Inactive Map", MapListPanel.RowState.INACTIVE, MapPartition.INACTIVE, 0, false)));

        int contentWidth = 400 - panel.getInsets().left - panel.getInsets().right;
        assertThat(panel.activeList().getPreferredSize().width).isEqualTo(contentWidth);
        assertThat(panel.inactiveList().getPreferredSize().width).isEqualTo(contentWidth);
        assertThat(panel.activeList().getPreferredSize().height).isEqualTo(MapListPanel.ROW_HEIGHT);
    }

    @Test
    public void tracksTheListWidthOnAPanelResizeWithoutSetRows() {
        MapListPanel panel = panel();
        panel.setSize(264, 600);
        panel.setRows(Arrays.asList(
            MapListPanel.MapRow.of(ACTIVE_MAP, "Active Map", MapListPanel.RowState.ACTIVE, MapPartition.ACTIVE, 5, false)));

        panel.setSize(500, 600);
        GraphWorkspaceWindow.runOnEdt(() -> { });
        GraphWorkspaceWindow.runOnEdt(() -> { });

        assertThat(panel.activeList().getPreferredSize().width)
            .isEqualTo(500 - panel.getInsets().left - panel.getInsets().right);
    }

    @Test
    public void derivesTheRowRendererCellWidthFromTheListWidth() {
        MapListPanel panel = panel();
        panel.activeList().setSize(400, 300);
        ListCellRenderer renderer = panel.activeList().getCellRenderer();
        MapListPanel.MapRow row = MapListPanel.MapRow.of(ACTIVE_MAP, "Active Map",
            MapListPanel.RowState.ACTIVE, MapPartition.ACTIVE, 5, false);

        Component rendered = renderer.getListCellRendererComponent(panel.activeList(), row, 0, false, false);

        assertThat(rendered.getPreferredSize().width).isEqualTo(400);
        assertThat(rendered.getPreferredSize().height).isEqualTo(MapListPanel.ROW_HEIGHT);
    }
```

- [ ] **Step 6: Run the test and confirm it fails**

Run: `gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.window.MapListPanelShould'`
Expected: FAIL, `cannot find symbol: method collapseButton()` / `method syncListWidths()` and missing `graph-workspace-map-list-heading-row`.

- [ ] **Step 7: Change `MapListPanel`**

Apply these six edits to `MapListPanel.java`:

1. Delete the line `    private static final int PANEL_WIDTH = 264;` (line 40).
2. Add the field next to `private final JLabel inactiveHeader = new JLabel();`:

```java
    private final JButton collapseButton;
```

3. Replace the heading block (`:200-202`) with:

```java
        collapseButton = MapSidebarPanel.chevronButton("graph_workspace.map_list.collapse",
            "graph-workspace-map-sidebar-collapse", "/images/MapSidebarCollapse.svg?useAccentColor=true");
        final JPanel headingRow = new JPanel(new BorderLayout());
        headingRow.setName("graph-workspace-map-list-heading-row");
        headingRow.add(heading, BorderLayout.WEST);
        headingRow.add(collapseButton, BorderLayout.EAST);
        add(headingRow, BorderLayout.NORTH);
```

4. Replace the two size lines (`:196-197`) with:

```java
        setPreferredSize(new Dimension(MapSidebarLayout.DEFAULT_WIDTH, 0));
        setMinimumSize(new Dimension(0, 0));
```

5. In `setRows(...)` replace the two constant-width `setPreferredSize(...)` calls (`:336-337`) with a single call:

```java
            syncListWidths();
```

6. Append the component listener at the end of the constructor, directly after `updateButtons();`:

```java
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(final java.awt.event.ComponentEvent event) {
                syncListWidths();
            }
        });
```

7. Add the two methods directly after `int rowHeight()`:

```java
    JButton collapseButton() {
        return collapseButton;
    }

    void syncListWidths() {
        final int width = Math.max(0, getWidth() - getInsets().left - getInsets().right);
        activeList.setPreferredSize(new Dimension(width, activeModel.size() * ROW_HEIGHT));
        inactiveList.setPreferredSize(new Dimension(width, inactiveModel.size() * ROW_HEIGHT));
        activeList.revalidate();
        inactiveList.revalidate();
    }
```

8. In `RowRenderer.getListCellRendererComponent`, replace `setPreferredSize(new Dimension(PANEL_WIDTH - 24, ROW_HEIGHT));` with:

```java
            setPreferredSize(new Dimension(list.getWidth(), ROW_HEIGHT));
```

- [ ] **Step 8: Run both test classes and confirm they pass**

Run: `gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.window.MapSidebarPanelShould' --tests 'org.freeplane.plugin.graph.window.MapListPanelShould'`
Expected: PASS, 5 panel tests and the 6 existing plus 5 new list tests.

Run: `gradle :freeplane_plugin_graph:test`
Expected: PASS for the whole plugin suite; the existing `MapListPanelShould` cases stay green.

- [ ] **Step 9: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/MapSidebarPanel.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/MapSidebarRail.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/MapListPanel.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/MapSidebarPanelShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/MapListPanelShould.java
git commit -m "feat(graph): add the map sidebar panel, rail and heading controls [2026-09-12-graph-map-sidebar-resize]"
```

## Task 5: MapSidebarSplitClampShould L&F harness

**Implementer tier:** Standard

**Lane:** sidebar-ui

**Depends on:** 3

**Files:**

- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/MapSidebarSplitClampShould.java`

**Interfaces:**

- Consumes: `MapSidebarLayout.effectiveMinimum(int, int, Insets)`, `MapSidebarLayout.canvasMinimumWidth(int, int, Insets)`, `MapSidebarLayout.maximumWidth(int)`, `MapSidebarLayout.clampWidth(int, int, int, Insets)` and `MapSidebarLayout.MIN_WIDTH` from Task 3.
- Produces: nothing outside the test class; it is the falsifiable L&F probe for the production clamp rule, using a local emulation of the `DIVIDER_LOCATION_PROPERTY` listener because the production listener is private to `GraphWorkspaceWindowModel`.

Note: the harness runs under the JVM default Metal Look-and-Feel; Metal's insets are `(1, 1, 1, 1)` and the effective divider width is the divider component's own size. The harness must read the effective divider width from `((BasicSplitPaneUI) splitPane.getUI()).getDivider().getDividerSize()`, never the logical `6`. The commit expectations are literal values (`999` releases to `500`, `250` releases to `250`) and must not be written as `clampWidth(...)` calls.

- [ ] **Step 1: Write the harness test**

```java
package org.freeplane.plugin.graph.window;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.plaf.basic.BasicSplitPaneUI;

import org.junit.Test;

public class MapSidebarSplitClampShould {
    private static final int DIVIDER_SIZE = 6;

    private static final class ClampHarness {
        private final JPanel leftPanel = new JPanel();
        private final JPanel rightPanel = new JPanel();
        private final JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        private int splitWidth;
        private int storedWidth = 264;
        private int appliedWidth;
        private int commits;
        private int reclampResets;
        private boolean gestureActive;
        private boolean applying;

        private ClampHarness() {
            splitPane.setContinuousLayout(true);
            splitPane.setDividerSize(DIVIDER_SIZE);
            splitPane.setResizeWeight(0.0);
            splitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, event -> {
                if (applying) {
                    return;
                }
                final int location = splitPane.getDividerLocation();
                if (location < 0 || effectiveMinimum() < MapSidebarLayout.MIN_WIDTH) {
                    return;
                }
                final int clamped = MapSidebarLayout.clampWidth(location, splitWidth, effectiveDividerWidth(),
                    insets());
                if (clamped != location) {
                    reclampResets++;
                    runGuarded(() -> splitPane.setDividerLocation(clamped));
                }
            });
            divider().addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(final MouseEvent event) {
                    if (event.getButton() != MouseEvent.BUTTON1) {
                        return;
                    }
                    gestureActive = event.getClickCount() != 2;
                }

                @Override
                public void mouseReleased(final MouseEvent event) {
                    if (event.getButton() != MouseEvent.BUTTON1 || !gestureActive) {
                        return;
                    }
                    gestureActive = false;
                    commit(event.getX());
                }
            });
        }

        private void commit(final int location) {
            if (effectiveMinimum() < MapSidebarLayout.MIN_WIDTH) {
                return;
            }
            final int clamped = MapSidebarLayout.clampWidth(location, splitWidth, effectiveDividerWidth(), insets());
            if (clamped == appliedWidth) {
                return;
            }
            commits++;
            storedWidth = clamped;
        }

        private void runGuarded(final Runnable action) {
            final boolean previous = applying;
            applying = true;
            try {
                action.run();
            }
            finally {
                applying = previous;
            }
        }

        private int effectiveDividerWidth() {
            final Component divider = ((BasicSplitPaneUI) splitPane.getUI()).getDivider();
            return divider == null ? 0 : divider.getDividerSize();
        }

        private Insets insets() {
            final Insets value = splitPane.getInsets();
            return value == null ? new Insets(0, 0, 0, 0) : value;
        }

        private int effectiveMinimum() {
            return MapSidebarLayout.effectiveMinimum(splitWidth, effectiveDividerWidth(), insets());
        }

        private ClampHarness layoutAt(final int width) {
            splitWidth = width;
            leftPanel.setMinimumSize(new Dimension(
                MapSidebarLayout.effectiveMinimum(width, effectiveDividerWidth(), insets()), 0));
            rightPanel.setMinimumSize(new Dimension(
                MapSidebarLayout.canvasMinimumWidth(width, effectiveDividerWidth(), insets()), 0));
            splitPane.setSize(width, 300);
            splitPane.doLayout();
            appliedWidth = splitPane.getDividerLocation();
            return this;
        }

        private Component divider() {
            return ((BasicSplitPaneUI) splitPane.getUI()).getDivider();
        }

        private void press(final int x) {
            divider().dispatchEvent(mouseEvent(MouseEvent.MOUSE_PRESSED, x));
        }

        private void drag(final int x) {
            divider().dispatchEvent(mouseEvent(MouseEvent.MOUSE_DRAGGED, x));
        }

        private void release(final int x) {
            divider().dispatchEvent(mouseEvent(MouseEvent.MOUSE_RELEASED, x));
        }

        private MouseEvent mouseEvent(final int id, final int x) {
            return new MouseEvent(divider(), id, System.currentTimeMillis(), 0, x, 0, 1, false,
                MouseEvent.BUTTON1);
        }
    }

    @Test
    public void reportsMinimumDividerLocationAtOrAboveTheSidebarMinimum() {
        for (final int splitWidth : new int[] { 400, 800, 1000 }) {
            ClampHarness harness = new ClampHarness().layoutAt(splitWidth);

            assertThat(((BasicSplitPaneUI) harness.splitPane.getUI()).getMinimumDividerLocation())
                .isGreaterThanOrEqualTo(MapSidebarLayout.MIN_WIDTH);
        }
    }

    @Test
    public void reportsTheExactInteractiveCeilingAsMaximumWidth() {
        for (final int splitWidth : new int[] { 400, 800, 1000 }) {
            ClampHarness harness = new ClampHarness().layoutAt(splitWidth);

            assertThat(((BasicSplitPaneUI) harness.splitPane.getUI()).getMaximumDividerLocation())
                .isEqualTo(MapSidebarLayout.maximumWidth(splitWidth));
        }
        ClampHarness narrow = new ClampHarness().layoutAt(362);
        assertThat(((BasicSplitPaneUI) narrow.splitPane.getUI()).getMaximumDividerLocation()).isEqualTo(181);
    }

    @Test
    public void reclampsAProgrammaticEndMoveToTheAllowedMaximum() {
        ClampHarness harness = new ClampHarness().layoutAt(1000);

        harness.splitPane.setDividerLocation(1000 - 1);

        assertThat(harness.splitPane.getDividerLocation()).isEqualTo(MapSidebarLayout.maximumWidth(1000));
        assertThat(harness.commits).isZero();
    }

    @Test
    public void commitsOneClampedWidthForOneDividerDrag() {
        ClampHarness harness = new ClampHarness().layoutAt(1000);
        harness.press(harness.splitPane.getDividerLocation());
        harness.drag(999);
        harness.release(999);

        assertThat(harness.commits).isEqualTo(1);
        assertThat(harness.storedWidth).isEqualTo(500);

        ClampHarness moved = new ClampHarness().layoutAt(1000);
        moved.press(moved.splitPane.getDividerLocation());
        moved.drag(250);
        moved.release(250);

        assertThat(moved.commits).isEqualTo(1);
        assertThat(moved.storedWidth).isEqualTo(250);
    }

    @Test
    public void commitsNothingForAPressAndReleaseWithoutMovement() {
        ClampHarness harness = new ClampHarness().layoutAt(1000);
        int location = harness.splitPane.getDividerLocation();

        harness.press(location);
        harness.release(location);

        assertThat(harness.commits).isZero();
        assertThat(harness.storedWidth).isEqualTo(264);
    }

    @Test
    public void holdsTheSqueezedRegimeWithoutCommits() {
        for (final int splitWidth : new int[] { 100, 150 }) {
            ClampHarness harness = new ClampHarness().layoutAt(splitWidth);
            harness.press(harness.splitPane.getDividerLocation());
            harness.drag(splitWidth - 1);
            harness.release(splitWidth - 1);

            assertThat(harness.commits).isZero();
            assertThat(harness.storedWidth).isEqualTo(264);

            int resetsBefore = harness.reclampResets;
            harness.layoutAt(splitWidth);
            assertThat(harness.reclampResets).isEqualTo(resetsBefore);
        }
    }
}
```

- [ ] **Step 2: Run the test and confirm it passes**

Run: `gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.window.MapSidebarSplitClampShould'`
Expected: PASS, 6 tests. If `reportsTheExactInteractiveCeilingAsMaximumWidth` fails, the harness is feeding the logical `6` or the wrong insets instead of the laid-out divider width; fix the harness, not the assertion.

- [ ] **Step 3: Commit**

```bash
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/MapSidebarSplitClampShould.java
git commit -m "test(graph): pin the sidebar clamp against the split-pane look and feel [2026-09-12-graph-map-sidebar-resize]"
```

## Task 6: Translation keys and chevron assets

**Implementer tier:** Fast

**Lane:** resources-assets

**Depends on:** none

**Files:**

- Modify: `freeplane/src/viewer/resources/translations/Resources_en.properties:784-814,896-899`
- Create: `freeplane_plugin_graph/src/main/resources/images/MapSidebarCollapse.svg`
- Create: `freeplane_plugin_graph/src/main/resources/images/MapSidebarExpand.svg`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphPluginIntegrationShould.java:444-452`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/smoke/GraphPluginOsgiSmoke.java:74-76,244-303`

**Interfaces:**

- Consumes: nothing from an earlier task.
- Produces: the five resource keys of the Global Constraints; `images/MapSidebarCollapse.svg` and `images/MapSidebarExpand.svg` packaged into `lib/plugin-<version>.jar` and resolvable through `Bundle.getResource` and `ResourceController.getOptionalIcon`.

- [ ] **Step 1: Write the failing resource test**

Append to `GraphPluginIntegrationShould`, directly after `shipsTheFourRecentWorkspaceResourceKeys`:

```java
    @Test
    public void shipsTheGraphMapSidebarResourceKeys() throws IOException {
        Properties translations = properties("freeplane/src/viewer/resources/translations/Resources_en.properties");

        assertThat(translations.getProperty("graph_workspace.action.maps_sidebar")).isEqualTo("Maps sidebar");
        assertThat(translations.getProperty("graph_workspace.map_list.collapse")).isEqualTo("Hide maps sidebar");
        assertThat(translations.getProperty("graph_workspace.map_list.expand")).isEqualTo("Show maps sidebar");
        assertThat(translations.getProperty("graph_workspace.map_list.rail_label")).isEqualTo("MAPS");
        assertThat(translations.getProperty("graph_workspace.map_list.rail_count")).isEqualTo("{0} active maps");
    }
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.window.GraphPluginIntegrationShould'`
Expected: FAIL, `expected "Maps sidebar" but was null` (the five keys do not exist yet).

- [ ] **Step 3: Add the five keys**

Insert these five lines into `freeplane/src/viewer/resources/translations/Resources_en.properties`, keeping each value pure ASCII. Insert `graph_workspace.action.maps_sidebar=Maps sidebar` between `graph_workspace.action.locate_map` and `graph_workspace.action.open`; insert the other four between `graph_workspace.map_list.active_heading` and `graph_workspace.map_list.heading` (the `gradle format_translation` step owns the final ordering):

```properties
graph_workspace.action.maps_sidebar=Maps sidebar
graph_workspace.map_list.collapse=Hide maps sidebar
graph_workspace.map_list.expand=Show maps sidebar
graph_workspace.map_list.rail_count={0} active maps
graph_workspace.map_list.rail_label=MAPS
```

- [ ] **Step 4: Add the two SVG assets**

`freeplane_plugin_graph/src/main/resources/images/MapSidebarCollapse.svg` (left-pointing chevron):

```xml
<?xml version="1.0"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" role="img" aria-label="Hide maps sidebar"><path d="M15 4 7 12l8 8" style="fill:none;stroke:#333;stroke-width:1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>
```

`freeplane_plugin_graph/src/main/resources/images/MapSidebarExpand.svg` (right-pointing chevron):

```xml
<?xml version="1.0"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" role="img" aria-label="Show maps sidebar"><path d="M9 4l8 8-8 8" style="fill:none;stroke:#333;stroke-width:1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>
```

Both files are monochrome `#333` with no other colour and are resolved with `?useAccentColor=true`.

- [ ] **Step 5: Run the translation formatter and validate ASCII**

Run: `gradle format_translation`
Run: `file freeplane/src/viewer/resources/translations/Resources_*.properties | grep -v "ASCII text"`
Expected: no output.

- [ ] **Step 6: Extend the OSGi asset gate**

In `GraphPluginOsgiSmoke.assertGraphBundleImages`, extend the `images` array to:

```java
        final String[] images = new String[] {
            "images/GraphSelect.svg",
            "images/GraphConnect.svg",
            "images/GraphSettings.svg",
            "images/GraphSearch.svg",
            "images/MapSidebarCollapse.svg",
            "images/MapSidebarExpand.svg"
        };
```

Add this private method after `assertGraphBundleImages`:

```java
    private static void assertGraphSidebarIconResolves(final Bundle coreBundle) throws Exception {
        final Class<?> resourceControllerType = coreBundle.loadClass(
            "org.freeplane.core.resources.ResourceController");
        final Object controller = resourceControllerType.getMethod("getResourceController").invoke(null);
        if (controller == null) {
            throw new AssertionError("ResourceController is not initialized while the graph bundle is ACTIVE");
        }
        final Object icon = resourceControllerType.getMethod("getOptionalIcon", String.class)
            .invoke(controller, "/images/MapSidebarExpand.svg?useAccentColor=true");
        if (icon == null) {
            throw new AssertionError("MapSidebarExpand.svg did not resolve through ResourceController");
        }
    }
```

Invoke it from `main`, directly after `assertGraphBundleContents(graphBundle);`:

```java
            assertGraphBundleContents(graphBundle);
            assertGraphSidebarIconResolves(coreBundle);
```

- [ ] **Step 7: Run the tests and the OSGi smoke gate**

Run: `gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.window.GraphPluginIntegrationShould'`
Expected: PASS.

Run: `gradle :freeplane_plugin_graph:graphOsgiSmoke`
Expected: `Graph OSGi smoke: ACTIVE, three dependency jars/classes, and graph operation passed`; a missing or empty asset fails the run.

- [ ] **Step 8: Commit**

```bash
git add freeplane/src/viewer/resources/translations/Resources_en.properties \
  freeplane_plugin_graph/src/main/resources/images/MapSidebarCollapse.svg \
  freeplane_plugin_graph/src/main/resources/images/MapSidebarExpand.svg \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphPluginIntegrationShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/smoke/GraphPluginOsgiSmoke.java
git commit -m "feat(graph): ship the maps sidebar strings and chevron assets [2026-09-12-graph-map-sidebar-resize]"
```

## Task 7: Split-pane wiring and the single sidebar apply path

**Implementer tier:** Advanced

**Lane:** window-wiring

**Depends on:** 1, 4

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java:1-80,306-349,437-444,557-571,992-1007,1009-1034,1036-1049,1092-1096`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java:1-125,186-240,766-800,1185-1220,1276-1305,1560-1610`

**Interfaces:**

- Consumes: `MapSidebarPanel(MapListPanel, MapSidebarPanel.CollapsedListener)`, `MapSidebarPanel.setCollapsed(boolean)`, `MapSidebarPanel.isCollapsed()`, `MapSidebarPanel.setReadOnly(boolean)`, `MapSidebarPanel.collapseButton()`, `MapSidebarPanel.mapList()`, `MapSidebarPanel.rail()` from Task 4; `MapListPanel.syncListWidths()` from Task 4; `MapSidebarLayout.clampWidth/effectiveMinimum/canvasMinimumWidth/RAIL_WIDTH` from Task 3; `DisplaySettings.mapSidebarWidth()/mapSidebarHidden()` from Task 1.
- Produces: package-private `int appliedSidebarWidth()`; private fields `sidebar`, `splitPane`, `mapsSidebarMenuItem`, `appliedSidebarWidth`, `sidebarGestureActive`, `sidebarApplyPending`, `sidebarApplying`; private methods `applySidebarSettings(DisplaySettings settings): void`, `setSidebarCollapsed(boolean collapsed): void`, `commitSidebarDisplaySettings(int width, boolean hidden): void`, `runWithSidebarGuard(Runnable action): void`, `splitWidth(): int`, `effectiveDividerWidth(): int`, `splitInsets(): Insets`; the component `graph-workspace-split` and menu item `graph-workspace-maps-sidebar-menu-item`.

Note: in this task `applySidebarSettings` reads the argument directly (no session overlay) and `setSidebarCollapsed` commits whenever `!readOnly`; Task 9 adds the read-only overlay. The split pane is the only direct child added to `graphArea` in `CENTER`, so `graphArea` has exactly two direct children. `applySidebarSettings` is the only writer of `appliedSidebarWidth`; it is called from `applyPresentation`, from the split pane's `ComponentListener`, and from `setSidebarCollapsed`.

- [ ] **Step 1: Add the test helpers and the four failing cases**

Add the `java.awt.Container`, `javax.swing.JCheckBoxMenuItem` and `javax.swing.JSplitPane` imports to `GraphWorkspaceWindowModelShould`.

Add these helpers next to the existing `graphScrollPane(model)` helper (and delete that helper plus its local-index navigation):

```java
    private static JPanel graphArea(final GraphWorkspaceWindowModel model) {
        return (JPanel) componentByName(model.content(), "graph-workspace-graph-area");
    }

    private static JSplitPane splitPane(final GraphWorkspaceWindowModel model) {
        return (JSplitPane) componentByName(graphArea(model), "graph-workspace-split");
    }

    private static MapSidebarPanel sidebarPanel(final GraphWorkspaceWindowModel model) {
        return (MapSidebarPanel) splitPane(model).getLeftComponent();
    }

    private static JScrollPane canvasScrollPane(final GraphWorkspaceWindowModel model) {
        return (JScrollPane) splitPane(model).getRightComponent();
    }

    private static JCheckBoxMenuItem mapsSidebarMenuItem(final GraphWorkspaceWindowModel model) {
        return (JCheckBoxMenuItem) menuItemByName(model, "graph-workspace-maps-sidebar-menu-item");
    }

    private static Component componentByName(final Container parent, final String name) {
        for (final Component component : parent.getComponents()) {
            if (name.equals(component.getName())) {
                return component;
            }
        }
        throw new AssertionError("Missing component " + name);
    }

    private static JMenuItem menuItemByName(final GraphWorkspaceWindowModel model, final String name) {
        for (int menuIndex = 0; menuIndex < model.menuBar().getMenuCount(); menuIndex++) {
            final JMenu menu = model.menuBar().getMenu(menuIndex);
            for (final Component component : menu.getMenuComponents()) {
                if (component instanceof JMenuItem && name.equals(component.getName())) {
                    return (JMenuItem) component;
                }
            }
        }
        throw new AssertionError("Missing menu item " + name);
    }
```

Add these two command-counting helpers next to `assertViewportCommandCount`:

```java
    private static int displayCommandCount(final Fixture fixture) {
        final ArgumentCaptor<GraphCommand> commands = ArgumentCaptor.forClass(GraphCommand.class);
        verify(fixture.handle, org.mockito.Mockito.atLeast(0)).execute(commands.capture());
        int count = 0;
        for (final GraphCommand command : commands.getAllValues()) {
            if (command instanceof GraphCommands.Display) {
                count++;
            }
        }
        return count;
    }

    private static DisplaySettings lastDisplaySettings(final Fixture fixture) {
        final ArgumentCaptor<GraphCommand> commands = ArgumentCaptor.forClass(GraphCommand.class);
        verify(fixture.handle, org.mockito.Mockito.atLeast(0)).execute(commands.capture());
        DisplaySettings result = null;
        for (final GraphCommand command : commands.getAllValues()) {
            if (command instanceof GraphCommands.Display) {
                result = ((GraphCommands.Display) command).settings();
            }
        }
        return result;
    }
```

Add this `Fixture` method (it mirrors the production store's synchronous presentation re-read):

```java
        private Fixture answerSidebarRoundTrip() {
            final DisplaySettings[] committed = { binding.currentPresentation().displaySettings() };
            when(handle.execute(any(GraphCommand.class))).thenAnswer(invocation -> {
                final GraphCommand command = invocation.getArgument(0);
                if (command instanceof GraphCommands.Display) {
                    committed[0] = ((GraphCommands.Display) command).settings();
                }
                return null;
            });
            when(binding.currentPresentation())
                .thenAnswer(invocation -> presentation(committed[0], ACTIVE_ID));
            return this;
        }
```

Add the four test methods:

```java
    @Test
    public void preservesSidebarFieldsWhenADisplayCheckboxIsToggled() {
        DisplaySettings settings = DisplaySettings.of(true, CanvasTheme.FOLLOW_FREEPLANE, true, true, 400, true,
            emptyUnknownXml());
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)),
            false, WorkspaceSessionStatus.empty(), presentation(settings, ACTIVE_ID)).answerSidebarRoundTrip();
        GraphWorkspaceWindowModel model = fixture.model();

        model.settingsPanel().showArrowheads().doClick();

        assertThat(displayCommandCount(fixture)).isEqualTo(1);
        DisplaySettings committed = lastDisplaySettings(fixture);
        assertThat(committed.showArrowheads()).isFalse();
        assertThat(committed.mapSidebarWidth()).isEqualTo(400);
        assertThat(committed.mapSidebarHidden()).isTrue();
        model.close();
    }

    @Test
    public void appliesStoredSidebarStateWithoutEmittingACommand() {
        DisplaySettings initial = DisplaySettings.of(true, CanvasTheme.FOLLOW_FREEPLANE, true, true, 264, false,
            emptyUnknownXml());
        DisplaySettings changed = DisplaySettings.of(true, CanvasTheme.FOLLOW_FREEPLANE, true, true, 500, false,
            emptyUnknownXml());
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)),
            false, WorkspaceSessionStatus.empty(), presentation(initial, ACTIVE_ID)).answerSidebarRoundTrip();
        GraphWorkspaceWindowModel model = fixture.model();
        GraphWorkspaceWindow.runOnEdt(() -> {
            splitPane(model).setSize(1000, 300);
            splitPane(model).doLayout();
        });
        GraphWorkspaceWindow.runOnEdt(() -> { });

        model.execute(GraphCommands.display(changed));
        int afterCommand = displayCommandCount(fixture);
        model.acceptCanvasState(emptyState());

        assertThat(afterCommand).isEqualTo(1);
        assertThat(splitPane(model).getDividerLocation()).isEqualTo(500);
        assertThat(mapsSidebarMenuItem(model).isSelected()).isTrue();
        assertThat(displayCommandCount(fixture)).isEqualTo(1);
        model.close();
    }

    @Test
    public void showsTheStoredSidebarWidthOnOpenInsteadOfTheMinimum() {
        DisplaySettings settings = DisplaySettings.of(true, CanvasTheme.FOLLOW_FREEPLANE, true, true, 400, false,
            emptyUnknownXml());
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)),
            false, WorkspaceSessionStatus.empty(), presentation(settings, ACTIVE_ID)).answerSidebarRoundTrip();
        GraphWorkspaceWindowModel model = fixture.model();

        assertThat(splitPane(model).getWidth()).isZero();
        GraphWorkspaceWindow.runOnEdt(() -> {
            splitPane(model).setSize(1000, 300);
            splitPane(model).doLayout();
        });
        GraphWorkspaceWindow.runOnEdt(() -> { });

        assertThat(splitPane(model).getDividerLocation()).isEqualTo(400);
        model.close();
    }

    @Test
    public void hostsTheMapSidebarInASplitPaneInsideTheGraphArea() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();

        assertThat(model.content().getComponentCount()).isEqualTo(3);
        JPanel graphArea = graphArea(model);
        assertThat(graphArea.getComponentCount()).isEqualTo(2);
        assertThat(componentByName(graphArea, "graph-workspace-settings")).isSameAs(model.settingsPanel());
        assertThat(sidebarPanel(model).getName()).isEqualTo("graph-workspace-map-sidebar");
        assertThat(sidebarPanel(model).mapList()).isSameAs(model.mapList());
        assertThat(sidebarPanel(model)).isNotSameAs(graphArea.getComponent(0));
        assertThat(canvasScrollPane(model).getName()).isEqualTo("graph-workspace-scroll-pane");
        assertThat(canvasScrollPane(model).getViewport().getView()).isSameAs(model.canvas());
        model.close();
    }
```

Then rewrite the existing navigation assertions in place:

1. In `rendersVisibleShellControlsFromGraphWorkspaceResourceKeys`, replace `assertThat(model.mapList().getComponent(0).getName()).isEqualTo("graph-workspace-map-list-heading");` with:

```java
        JPanel headingRow = (JPanel) componentByName(model.mapList(), "graph-workspace-map-list-heading-row");
        assertThat(componentByName(headingRow, "graph-workspace-map-list-heading").getName())
            .isEqualTo("graph-workspace-map-list-heading");
```

2. In `composesAHeadlessModelessWorkspaceWithStablePanelsAndApprovedControls`, replace the block from `JPanel graphArea = (JPanel) model.content().getComponent(1);` through `assertThat(graphArea.getComponent(2)).isSameAs(model.settingsPanel());` with:

```java
        assertThat(model.content().getComponentCount()).isEqualTo(3);
        JPanel graphArea = graphArea(model);
        assertThat(graphArea.getComponentCount()).isEqualTo(2);
        assertThat(componentByName(graphArea, "graph-workspace-settings")).isSameAs(model.settingsPanel());
        JSplitPane splitPane = splitPane(model);
        assertThat(sidebarPanel(model)).isNotSameAs(graphArea.getComponent(0));
        assertThat(sidebarPanel(model).mapList()).isSameAs(model.mapList());
        assertThat(splitPane.getRightComponent()).isSameAs(canvasScrollPane(model));
        assertThat(canvasScrollPane(model).getName()).isEqualTo("graph-workspace-scroll-pane");
        assertThat(canvasScrollPane(model).getViewport().getView()).isSameAs(model.canvas());
```

3. In `growsTheScrollableSurfaceForVisibleWorldGeometry` and `persistsExternalScrollAsTheVisibleViewportOnce`, replace the two-line local navigation (lines 773–774 and 789–790) with:

```java
        JScrollPane graphScrollPane = canvasScrollPane(model);
```

4. In `scrollViewport`, replace `graphScrollPane(model)` with `canvasScrollPane(model)`, then delete the private static `graphScrollPane(GraphWorkspaceWindowModel)` helper entirely.

- [ ] **Step 2: Run the new cases and confirm they fail**

Run: `gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould'`
Expected: FAIL at compilation, `cannot find symbol: method splitPane(...)`, `cannot find symbol: class MapSidebarPanel` from the new helpers, and the topology assertions fail.

- [ ] **Step 3: Wire the split pane and the apply path**

In `GraphWorkspaceWindow.java`:

1. Add these imports next to the existing `java.awt`/`javax.swing` imports:

```java
import java.awt.Component;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JSplitPane;
import javax.swing.plaf.basic.BasicSplitPaneUI;
```

2. Add these fields after `private ContributorInspector contributorInspector;`:

```java
    private final MapSidebarPanel sidebar;
    private JSplitPane splitPane;
    private JCheckBoxMenuItem mapsSidebarMenuItem;
    private int appliedSidebarWidth;
    private boolean sidebarGestureActive;
    private boolean sidebarApplyPending;
    private boolean sidebarApplying;
```

3. Add the sidebar construction directly after `mapList.addSelectionListener(row -> updateMenuEnablement());`:

```java
        sidebar = new MapSidebarPanel(mapList, this::setSidebarCollapsed);
        sidebar.collapseButton().addActionListener(event -> setSidebarCollapsed(true));
```

4. Replace `createContent()` (`:1036-1049`) with:

```java
    private JPanel createContent() {
        final JPanel graphArea = new JPanel(new BorderLayout(0, 0));
        graphArea.setName("graph-workspace-graph-area");
        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebar, graphScrollPane);
        splitPane.setName("graph-workspace-split");
        splitPane.setContinuousLayout(true);
        splitPane.setDividerSize(6);
        splitPane.setResizeWeight(0.0);
        installSidebarListeners();
        graphArea.add(splitPane, BorderLayout.CENTER);
        graphArea.add(settingsPanel, BorderLayout.EAST);

        final JPanel result = new JPanel(new BorderLayout(0, 0));
        result.setName("graph-workspace-content");
        result.add(toolbar, BorderLayout.NORTH);
        result.add(graphArea, BorderLayout.CENTER);
        result.add(statusSlot, BorderLayout.SOUTH);
        return result;
    }

    private void installSidebarListeners() {
        splitPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(final ComponentEvent event) {
                applySidebarSettings(currentPresentation.displaySettings());
            }
        });
    }
```

5. Extend `applyPresentation` (`:557-571`) with `applySidebarSettings(settings);` as its final statement (before the closing brace; `settings` is the local already declared at the top of the method).

6. In `createMenuBar()`, directly after `view.add(viewSettingsMenuItem);` (`:1096`), add:

```java
        mapsSidebarMenuItem = new JCheckBoxMenuItem(TextUtils.getText("graph_workspace.action.maps_sidebar"));
        mapsSidebarMenuItem.setName("graph-workspace-maps-sidebar-menu-item");
        mapsSidebarMenuItem.addActionListener(event -> setSidebarCollapsed(!sidebar.isCollapsed()));
        view.add(mapsSidebarMenuItem);
```

7. In `updateMenuEnablement()`, add `mapsSidebarMenuItem.setEnabled(true);` directly after `viewSettingsMenuItem.setEnabled(true);`.

8. In `setReadOnlyOnEdt`, add `sidebar.setReadOnly(value);` directly after `mapList.setReadOnly(value);`.

9. Add the apply path and its guarded helpers after `applyPresentation`:

```java
    int appliedSidebarWidth() {
        return appliedSidebarWidth;
    }

    private void applySidebarSettings(final DisplaySettings settings) {
        final boolean hidden = settings.mapSidebarHidden();
        final int width = settings.mapSidebarWidth();
        if (sidebarGestureActive) {
            sidebarApplyPending = true;
            return;
        }
        if (splitPane.getWidth() <= 0) {
            sidebarApplyPending = true;
            return;
        }
        runWithSidebarGuard(new Runnable() {
            @Override
            public void run() {
                sidebar.setCollapsed(hidden);
                splitPane.setDividerSize(hidden ? 0 : 6);
                splitPane.setEnabled(!hidden);
                final int splitWidth = splitWidth();
                final int dividerWidth = effectiveDividerWidth();
                final Insets insets = splitInsets();
                final int effectiveMinimum = MapSidebarLayout.effectiveMinimum(splitWidth, dividerWidth, insets);
                final int canvasMinimum = MapSidebarLayout.canvasMinimumWidth(splitWidth, dividerWidth, insets);
                final int appliedWidth = hidden ? MapSidebarLayout.RAIL_WIDTH
                    : MapSidebarLayout.clampWidth(width, splitWidth, dividerWidth, insets);
                sidebar.setMinimumSize(new Dimension(hidden ? MapSidebarLayout.RAIL_WIDTH : effectiveMinimum, 0));
                splitPane.setDividerLocation(appliedWidth);
                mapsSidebarMenuItem.setSelected(!hidden);
                appliedSidebarWidth = appliedWidth;
                graphScrollPane.setMinimumSize(new Dimension(canvasMinimum, 0));
                sidebar.mapList().syncListWidths();
                sidebarApplyPending = false;
            }
        });
    }

    private void setSidebarCollapsed(final boolean collapsed) {
        if (sidebarApplying) {
            return;
        }
        sidebarGestureActive = false;
        runWithSidebarGuard(new Runnable() {
            @Override
            public void run() {
                sidebar.setCollapsed(collapsed);
            }
        });
        if (!readOnly) {
            commitSidebarDisplaySettings(currentPresentation.displaySettings().mapSidebarWidth(), collapsed);
        }
        applySidebarSettings(currentPresentation.displaySettings());
    }

    private void commitSidebarDisplaySettings(final int width, final boolean hidden) {
        final DisplaySettings current = currentPresentation.displaySettings();
        executeCommand(GraphCommands.display(DisplaySettings.of(current.showArrowheads(), current.canvasTheme(),
            current.rememberViewport(), current.dimUnrelatedNodes(), width, hidden, current.unknownXml())));
    }

    private void runWithSidebarGuard(final Runnable action) {
        final boolean previous = sidebarApplying;
        sidebarApplying = true;
        try {
            action.run();
        }
        finally {
            sidebarApplying = previous;
        }
    }

    private int splitWidth() {
        return splitPane.getWidth();
    }

    private int effectiveDividerWidth() {
        final Component divider = ((BasicSplitPaneUI) splitPane.getUI()).getDivider();
        return divider == null ? 0 : divider.getDividerSize();
    }

    private Insets splitInsets() {
        final Insets insets = splitPane.getInsets();
        return insets == null ? new Insets(0, 0, 0, 0) : insets;
    }
```

- [ ] **Step 4: Run the tests and confirm they pass**

Run: `gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould'`
Expected: PASS, including the four new methods and the rewritten existing assertions.

Run: `gradle :freeplane_plugin_graph:test`
Expected: PASS for the whole plugin suite.

- [ ] **Step 5: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java
git commit -m "feat(graph): host the maps sidebar in a split pane with one apply path [2026-09-12-graph-map-sidebar-resize]"
```

## Task 8: Divider gesture commit protocol

**Implementer tier:** Advanced

**Lane:** window-wiring

**Depends on:** 7

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java:1-80,1036-1060` (append to `installSidebarListeners` and add the commit methods after `setSidebarCollapsed`)
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java:1-125,1560-1610`

**Interfaces:**

- Consumes: `appliedSidebarWidth`, `applySidebarSettings(DisplaySettings)`, `splitWidth()`, `effectiveDividerWidth()`, `splitInsets()`, `commitSidebarDisplaySettings(int, boolean)`, `runWithSidebarGuard(Runnable)` from Task 7; `MapSidebarLayout.clampWidth/effectiveMinimum/MIN_WIDTH/DEFAULT_WIDTH/RAIL_WIDTH` from Task 3.
- Produces: private `commitSidebarWidth(int location): void`, `resetSidebarWidth(): void`, `runPendingSidebarApply(): void`; the divider `MouseListener`, the `DIVIDER_LOCATION_PROPERTY` `PropertyChangeListener` (including the collapsed hold) and the split-pane `FocusListener`.

Note: only `applySidebarSettings` writes `appliedSidebarWidth`; the property listener's re-clamp and the collapsed hold must never write it. `commitSidebarWidth` and `resetSidebarWidth` both read the three live-geometry locals first and return early while collapsed or in the squeezed regime (`effectiveMinimum < MIN_WIDTH`).

- [ ] **Step 1: Add the failing commit-protocol cases**

Add the `java.awt.event.FocusEvent` and `javax.swing.plaf.basic.BasicSplitPaneUI` imports, then add these helpers and five test methods to `GraphWorkspaceWindowModelShould`:

```java
    private static void layoutSidebarAt(final GraphWorkspaceWindowModel model, final int width) {
        GraphWorkspaceWindow.runOnEdt(() -> {
            splitPane(model).setSize(width, 300);
            splitPane(model).doLayout();
        });
        GraphWorkspaceWindow.runOnEdt(() -> { });
    }

    private static Component divider(final GraphWorkspaceWindowModel model) {
        return ((BasicSplitPaneUI) splitPane(model).getUI()).getDivider();
    }

    private static MouseEvent dividerEvent(final GraphWorkspaceWindowModel model, final int id,
            final int clickCount) {
        final Component divider = divider(model);
        return new MouseEvent(divider, id, System.currentTimeMillis(), 0, 0, 0, clickCount, false,
            MouseEvent.BUTTON1);
    }

    private static void dragSidebarTo(final GraphWorkspaceWindowModel model, final int rawLocation) {
        GraphWorkspaceWindow.runOnEdt(() -> {
            divider(model).dispatchEvent(dividerEvent(model, MouseEvent.MOUSE_PRESSED, 1));
            splitPane(model).setDividerLocation(rawLocation);
            divider(model).dispatchEvent(dividerEvent(model, MouseEvent.MOUSE_RELEASED, 1));
        });
        GraphWorkspaceWindow.runOnEdt(() -> { });
    }

    @Test
    public void commitsOneClampedSidebarWidthPerDividerDrag() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)),
            false).answerSidebarRoundTrip();
        GraphWorkspaceWindowModel model = fixture.model();
        layoutSidebarAt(model, 1000);

        dragSidebarTo(model, 1000 - splitPane(model).getInsets().right);

        assertThat(displayCommandCount(fixture)).isEqualTo(1);
        assertThat(lastDisplaySettings(fixture).mapSidebarWidth()).isEqualTo(500);

        Fixture moved = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)),
            false).answerSidebarRoundTrip();
        GraphWorkspaceWindowModel movedModel = moved.model();
        layoutSidebarAt(movedModel, 1000);

        dragSidebarTo(movedModel, 250);

        assertThat(displayCommandCount(moved)).isEqualTo(1);
        assertThat(lastDisplaySettings(moved).mapSidebarWidth()).isEqualTo(250);
        model.close();
        movedModel.close();
    }

    @Test
    public void commitsNothingForAPressAndReleaseWithoutMovement() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)),
            false).answerSidebarRoundTrip();
        GraphWorkspaceWindowModel model = fixture.model();
        layoutSidebarAt(model, 1000);
        int location = splitPane(model).getDividerLocation();

        GraphWorkspaceWindow.runOnEdt(() -> {
            divider(model).dispatchEvent(dividerEvent(model, MouseEvent.MOUSE_PRESSED, 1));
            divider(model).dispatchEvent(dividerEvent(model, MouseEvent.MOUSE_RELEASED, 1));
        });

        assertThat(displayCommandCount(fixture)).isZero();
        assertThat(model.appliedSidebarWidth()).isEqualTo(location);
        model.close();
    }

    @Test
    public void resetsTheSidebarWidthOnDividerDoubleClick() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)),
            false).answerSidebarRoundTrip();
        GraphWorkspaceWindowModel model = fixture.model();
        layoutSidebarAt(model, 1000);
        dragSidebarTo(model, 400);
        assertThat(lastDisplaySettings(fixture).mapSidebarWidth()).isEqualTo(400);

        GraphWorkspaceWindow.runOnEdt(() -> {
            divider(model).dispatchEvent(dividerEvent(model, MouseEvent.MOUSE_PRESSED, 2));
            divider(model).dispatchEvent(dividerEvent(model, MouseEvent.MOUSE_RELEASED, 2));
        });

        assertThat(displayCommandCount(fixture)).isEqualTo(2);
        assertThat(lastDisplaySettings(fixture).mapSidebarWidth()).isEqualTo(264);

        Fixture narrow = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)),
            false).answerSidebarRoundTrip();
        GraphWorkspaceWindowModel narrowModel = narrow.model();
        layoutSidebarAt(narrowModel, 400);
        dragSidebarTo(narrowModel, 190);

        GraphWorkspaceWindow.runOnEdt(() -> {
            divider(narrowModel).dispatchEvent(dividerEvent(narrowModel, MouseEvent.MOUSE_PRESSED, 2));
            divider(narrowModel).dispatchEvent(dividerEvent(narrowModel, MouseEvent.MOUSE_RELEASED, 2));
        });

        assertThat(displayCommandCount(narrow)).isEqualTo(2);
        assertThat(lastDisplaySettings(narrow).mapSidebarWidth()).isEqualTo(200);
        model.close();
        narrowModel.close();
    }

    @Test
    public void defersSidebarApplicationWhileTheDividerGestureIsActive() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)),
            false).answerSidebarRoundTrip();
        GraphWorkspaceWindowModel model = fixture.model();
        layoutSidebarAt(model, 1000);

        GraphWorkspaceWindow.runOnEdt(() ->
            divider(model).dispatchEvent(dividerEvent(model, MouseEvent.MOUSE_PRESSED, 1)));
        int before = splitPane(model).getDividerLocation();

        GraphWorkspaceWindow.runOnEdt(() -> model.acceptCanvasState(emptyState()));

        assertThat(splitPane(model).getDividerLocation()).isEqualTo(before);
        assertThat(displayCommandCount(fixture)).isZero();

        GraphWorkspaceWindow.runOnEdt(() -> {
            splitPane(model).setDividerLocation(400);
            divider(model).dispatchEvent(dividerEvent(model, MouseEvent.MOUSE_RELEASED, 1));
        });
        GraphWorkspaceWindow.runOnEdt(() -> { });

        assertThat(displayCommandCount(fixture)).isEqualTo(1);
        assertThat(lastDisplaySettings(fixture).mapSidebarWidth()).isEqualTo(400);
        model.close();
    }

    @Test
    public void preservesAnOutOfRangeStoredSidebarWidthAcrossNoOpGestures() {
        DisplaySettings wide = DisplaySettings.of(true, CanvasTheme.FOLLOW_FREEPLANE, true, true, 1000, false,
            emptyUnknownXml());
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)),
            false, WorkspaceSessionStatus.empty(), presentation(wide, ACTIVE_ID)).answerSidebarRoundTrip();
        GraphWorkspaceWindowModel model = fixture.model();
        layoutSidebarAt(model, 600);

        assertThat(splitPane(model).getDividerLocation()).isEqualTo(300);
        assertThat(model.appliedSidebarWidth()).isEqualTo(300);

        GraphWorkspaceWindow.runOnEdt(() -> {
            divider(model).dispatchEvent(dividerEvent(model, MouseEvent.MOUSE_PRESSED, 1));
            divider(model).dispatchEvent(dividerEvent(model, MouseEvent.MOUSE_RELEASED, 1));
            splitPane(model).dispatchEvent(new FocusEvent(splitPane(model), FocusEvent.FOCUS_LOST));
        });

        assertThat(displayCommandCount(fixture)).isZero();
        assertThat(model.appliedSidebarWidth()).isEqualTo(300);

        Fixture squeezed = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)),
            false).answerSidebarRoundTrip();
        GraphWorkspaceWindowModel squeezedModel = squeezed.model();
        layoutSidebarAt(squeezedModel, 150);

        GraphWorkspaceWindow.runOnEdt(() -> {
            divider(squeezedModel).dispatchEvent(dividerEvent(squeezedModel, MouseEvent.MOUSE_PRESSED, 1));
            divider(squeezedModel).dispatchEvent(dividerEvent(squeezedModel, MouseEvent.MOUSE_RELEASED, 1));
            splitPane(squeezedModel).dispatchEvent(new FocusEvent(splitPane(squeezedModel), FocusEvent.FOCUS_LOST));
        });

        assertThat(displayCommandCount(squeezed)).isZero();
        assertThat(lastDisplaySettings(squeezed)).isNull();
        model.close();
        squeezedModel.close();
    }

    @Test
    public void commitsTheKeyboardAdjustedSidebarWidthOnceOnFocusLoss() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)),
            false).answerSidebarRoundTrip();
        GraphWorkspaceWindowModel model = fixture.model();
        layoutSidebarAt(model, 1000);

        GraphWorkspaceWindow.runOnEdt(() -> {
            splitPane(model).dispatchEvent(new FocusEvent(splitPane(model), FocusEvent.FOCUS_GAINED));
            splitPane(model).getActionMap().get("selectMax").actionPerformed(
                new java.awt.event.ActionEvent(splitPane(model), java.awt.event.ActionEvent.ACTION_PERFORMED,
                    "selectMax"));
        });

        assertThat(splitPane(model).getDividerLocation()).isEqualTo(500);

        GraphWorkspaceWindow.runOnEdt(() ->
            splitPane(model).dispatchEvent(new FocusEvent(splitPane(model), FocusEvent.FOCUS_LOST)));

        assertThat(displayCommandCount(fixture)).isEqualTo(1);
        assertThat(lastDisplaySettings(fixture).mapSidebarWidth()).isEqualTo(500);
        model.close();
    }
```

- [ ] **Step 2: Run the new cases and confirm they fail**

Run: `gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould'`
Expected: FAIL — no commit is produced (the divider has no production mouse/focus/property listeners), so `displayCommandCount` is `0` where `1` is expected and the re-clamp assertion in `commitsTheKeyboardAdjustedSidebarWidthOnceOnFocusLoss` fails.

- [ ] **Step 3: Add the listeners and the commit methods**

Add the `java.awt.event.FocusAdapter`, `java.awt.event.FocusEvent`, `java.awt.event.MouseAdapter` and `java.awt.event.MouseEvent` imports.

Append to `installSidebarListeners()`, directly after the `addComponentListener(...)` block:

```java
        final Component divider = ((BasicSplitPaneUI) splitPane.getUI()).getDivider();
        if (divider != null) {
            divider.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(final MouseEvent event) {
                    if (event.getButton() != MouseEvent.BUTTON1) {
                        return;
                    }
                    if (event.getClickCount() == 2) {
                        sidebarGestureActive = false;
                        resetSidebarWidth();
                        return;
                    }
                    sidebarGestureActive = true;
                }

                @Override
                public void mouseReleased(final MouseEvent event) {
                    if (event.getButton() != MouseEvent.BUTTON1 || !sidebarGestureActive) {
                        return;
                    }
                    sidebarGestureActive = false;
                    commitSidebarWidth(splitPane.getDividerLocation());
                    runPendingSidebarApply();
                }
            });
        }
        splitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, event -> {
            if (sidebarApplying) {
                return;
            }
            final int splitWidth = splitWidth();
            final int dividerWidth = effectiveDividerWidth();
            final Insets insets = splitInsets();
            final int location = splitPane.getDividerLocation();
            if (sidebar.isCollapsed()) {
                if (location != MapSidebarLayout.RAIL_WIDTH) {
                    runWithSidebarGuard(new Runnable() {
                        @Override
                        public void run() {
                            splitPane.setDividerLocation(MapSidebarLayout.RAIL_WIDTH);
                        }
                    });
                }
                return;
            }
            if (sidebarGestureActive) {
                return;
            }
            if (MapSidebarLayout.effectiveMinimum(splitWidth, dividerWidth, insets) < MapSidebarLayout.MIN_WIDTH) {
                return;
            }
            final int clamped = MapSidebarLayout.clampWidth(location, splitWidth, dividerWidth, insets);
            if (clamped != location) {
                runWithSidebarGuard(new Runnable() {
                    @Override
                    public void run() {
                        splitPane.setDividerLocation(clamped);
                    }
                });
            }
        });
        splitPane.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(final FocusEvent event) {
                if (sidebarGestureActive) {
                    sidebarGestureActive = false;
                    runPendingSidebarApply();
                    return;
                }
                commitSidebarWidth(splitPane.getDividerLocation());
                runPendingSidebarApply();
            }
        });
```

Add the three commit methods directly after `setSidebarCollapsed`:

```java
    private void commitSidebarWidth(final int location) {
        final int splitWidth = splitWidth();
        final int dividerWidth = effectiveDividerWidth();
        final Insets insets = splitInsets();
        if (sidebar.isCollapsed()) {
            return;
        }
        if (MapSidebarLayout.effectiveMinimum(splitWidth, dividerWidth, insets) < MapSidebarLayout.MIN_WIDTH) {
            return;
        }
        final int clamped = MapSidebarLayout.clampWidth(location, splitWidth, dividerWidth, insets);
        if (clamped == appliedSidebarWidth) {
            return;
        }
        if (!readOnly) {
            commitSidebarDisplaySettings(clamped, currentPresentation.displaySettings().mapSidebarHidden());
        }
        applySidebarSettings(currentPresentation.displaySettings());
    }

    private void resetSidebarWidth() {
        final int splitWidth = splitWidth();
        final int dividerWidth = effectiveDividerWidth();
        final Insets insets = splitInsets();
        if (sidebar.isCollapsed() || sidebarGestureActive) {
            return;
        }
        if (MapSidebarLayout.effectiveMinimum(splitWidth, dividerWidth, insets) < MapSidebarLayout.MIN_WIDTH) {
            return;
        }
        final int clamped = MapSidebarLayout.clampWidth(MapSidebarLayout.DEFAULT_WIDTH, splitWidth, dividerWidth,
            insets);
        if (clamped == appliedSidebarWidth) {
            return;
        }
        if (!readOnly) {
            commitSidebarDisplaySettings(clamped, currentPresentation.displaySettings().mapSidebarHidden());
        }
        applySidebarSettings(currentPresentation.displaySettings());
    }

    private void runPendingSidebarApply() {
        if (sidebarApplyPending) {
            applySidebarSettings(currentPresentation.displaySettings());
        }
    }
```

- [ ] **Step 4: Run the tests and confirm they pass**

Run: `gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould'`
Expected: PASS, including all five new methods.

Run: `gradle :freeplane_plugin_graph:test`
Expected: PASS for the whole plugin suite.

- [ ] **Step 5: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java
git commit -m "feat(graph): commit one sidebar width per divider gesture [2026-09-12-graph-map-sidebar-resize]"
```

## Task 9: Collapsed hold, rail restore and the read-only session overlay

**Implementer tier:** Advanced

**Lane:** window-wiring

**Depends on:** 8

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java:306-360,557-680,992-1007`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java:1-125`

**Interfaces:**

- Consumes: everything produced by Task 8, plus `MapSidebarPanel.setCollapsed(boolean)`, `MapSidebarPanel.isCollapsed()`, `MapSidebarPanel.rail()`, `MapSidebarRail.restoreButton()` from Task 4 and `MapSidebarPanel.collapseButton()` from Task 7.
- Produces: private fields `Integer sessionSidebarWidth` / `Boolean sessionSidebarHidden` (both initially `null`); overlay-aware `applySidebarSettings`; overlay writes in `setSidebarCollapsed`, `commitSidebarWidth` and `resetSidebarWidth`; overlay clearing in `setReadOnlyOnEdt` when the binding becomes writable.

Note: the overlay wins only while `readOnly == true` and the corresponding field is non-null; the two fields are independent. A read-only session emits zero `GraphCommands.Display` commands while the controls stay enabled. The collapsed hold itself already exists in Task 8's `DIVIDER_LOCATION_PROPERTY` listener and must not write `appliedSidebarWidth`.

- [ ] **Step 1: Add the failing collapse and read-only cases**

Add these five test methods to `GraphWorkspaceWindowModelShould`:

```java
    @Test
    public void keepsTheViewMenuSidebarItemInSyncWithEveryToggle() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)),
            false).answerSidebarRoundTrip();
        GraphWorkspaceWindowModel model = fixture.model();
        layoutSidebarAt(model, 1000);

        assertThat(mapsSidebarMenuItem(model).isSelected()).isTrue();

        GraphWorkspaceWindow.runOnEdt(() -> sidebarPanel(model).collapseButton().doClick());
        assertThat(sidebarPanel(model).isCollapsed()).isTrue();
        assertThat(mapsSidebarMenuItem(model).isSelected()).isFalse();
        assertThat(displayCommandCount(fixture)).isEqualTo(1);
        assertThat(lastDisplaySettings(fixture).mapSidebarHidden()).isTrue();

        GraphWorkspaceWindow.runOnEdt(() -> sidebarPanel(model).rail().restoreButton().doClick());
        assertThat(sidebarPanel(model).isCollapsed()).isFalse();
        assertThat(mapsSidebarMenuItem(model).isSelected()).isTrue();
        assertThat(displayCommandCount(fixture)).isEqualTo(2);
        assertThat(lastDisplaySettings(fixture).mapSidebarHidden()).isFalse();

        GraphWorkspaceWindow.runOnEdt(() -> mapsSidebarMenuItem(model).doClick());
        assertThat(sidebarPanel(model).isCollapsed()).isTrue();
        assertThat(mapsSidebarMenuItem(model).isSelected()).isFalse();
        assertThat(displayCommandCount(fixture)).isEqualTo(3);

        Fixture stored = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)),
            false, WorkspaceSessionStatus.empty(),
            presentation(DisplaySettings.of(true, CanvasTheme.FOLLOW_FREEPLANE, true, true, 264, true,
                emptyUnknownXml()), ACTIVE_ID)).answerSidebarRoundTrip();
        GraphWorkspaceWindowModel storedModel = stored.model();
        layoutSidebarAt(storedModel, 1000);

        assertThat(sidebarPanel(storedModel).isCollapsed()).isTrue();
        assertThat(mapsSidebarMenuItem(storedModel).isSelected()).isFalse();
        model.close();
        storedModel.close();
    }

    @Test
    public void commitsNothingFromAGestureThatEndsWhileCollapsed() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)),
            false).answerSidebarRoundTrip();
        GraphWorkspaceWindowModel model = fixture.model();
        layoutSidebarAt(model, 1000);

        GraphWorkspaceWindow.runOnEdt(() -> {
            divider(model).dispatchEvent(dividerEvent(model, MouseEvent.MOUSE_PRESSED, 1));
            mapsSidebarMenuItem(model).doClick();
        });

        assertThat(displayCommandCount(fixture)).isEqualTo(1);
        assertThat(lastDisplaySettings(fixture).mapSidebarHidden()).isTrue();
        assertThat(sidebarPanel(model).isCollapsed()).isTrue();
        assertThat(splitPane(model).getDividerSize()).isZero();
        assertThat(splitPane(model).isEnabled()).isFalse();

        GraphWorkspaceWindow.runOnEdt(() -> {
            splitPane(model).setDividerLocation(400);
            divider(model).dispatchEvent(dividerEvent(model, MouseEvent.MOUSE_RELEASED, 1));
        });

        assertThat(displayCommandCount(fixture)).isEqualTo(1);
        assertThat(splitPane(model).getDividerLocation()).isEqualTo(26);
        model.close();
    }

    @Test
    public void keepsReadOnlySidebarInteractionsSessionOnly() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)),
            true).answerSidebarRoundTrip();
        GraphWorkspaceWindowModel model = fixture.model();
        layoutSidebarAt(model, 1000);

        dragSidebarTo(model, 400);
        GraphWorkspaceWindow.runOnEdt(() -> mapsSidebarMenuItem(model).doClick());

        assertThat(displayCommandCount(fixture)).isZero();
        assertThat(model.appliedSidebarWidth()).isEqualTo(26);
        assertThat(mapsSidebarMenuItem(model).isEnabled()).isTrue();
        assertThat(sidebarPanel(model).collapseButton().isEnabled()).isTrue();
        assertThat(sidebarPanel(model).rail().restoreButton().isEnabled()).isTrue();

        GraphWorkspaceWindow.runOnEdt(() -> mapsSidebarMenuItem(model).doClick());
        assertThat(sidebarPanel(model).isCollapsed()).isFalse();

        layoutSidebarAt(model, 800);
        assertThat(splitPane(model).getDividerLocation()).isEqualTo(400);
        assertThat(displayCommandCount(fixture)).isZero();

        model.setReadOnly(false);
        layoutSidebarAt(model, 900);

        assertThat(sidebarPanel(model).isCollapsed()).isFalse();
        assertThat(splitPane(model).getDividerLocation()).isEqualTo(264);
        assertThat(displayCommandCount(fixture)).isZero();
        model.close();
    }

    @Test
    public void restoresTheLastExpandedWidthOnExpand() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)),
            false).answerSidebarRoundTrip();
        GraphWorkspaceWindowModel model = fixture.model();
        layoutSidebarAt(model, 1000);
        dragSidebarTo(model, 400);
        assertThat(displayCommandCount(fixture)).isEqualTo(1);

        GraphWorkspaceWindow.runOnEdt(() -> sidebarPanel(model).collapseButton().doClick());

        assertThat(sidebarPanel(model).isCollapsed()).isTrue();
        assertThat(model.appliedSidebarWidth()).isEqualTo(26);
        assertThat(splitPane(model).getDividerSize()).isZero();
        assertThat(splitPane(model).isEnabled()).isFalse();
        assertThat(displayCommandCount(fixture)).isEqualTo(2);

        GraphWorkspaceWindow.runOnEdt(() -> sidebarPanel(model).rail().restoreButton().doClick());

        assertThat(sidebarPanel(model).isCollapsed()).isFalse();
        assertThat(splitPane(model).getDividerLocation()).isEqualTo(400);
        assertThat(splitPane(model).getDividerSize()).isEqualTo(6);
        assertThat(splitPane(model).isEnabled()).isTrue();
        assertThat(displayCommandCount(fixture)).isEqualTo(3);
        model.close();
    }

    @Test
    public void expandsFromCollapsedWithTheRestoredDividerWidthInOneReadOnlyApply() {
        DisplaySettings settings = DisplaySettings.of(true, CanvasTheme.FOLLOW_FREEPLANE, true, true, 400, true,
            emptyUnknownXml());
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)),
            true, WorkspaceSessionStatus.empty(), presentation(settings, ACTIVE_ID)).answerSidebarRoundTrip();
        GraphWorkspaceWindowModel model = fixture.model();
        layoutSidebarAt(model, 400);

        assertThat(sidebarPanel(model).isCollapsed()).isTrue();

        GraphWorkspaceWindow.runOnEdt(() -> mapsSidebarMenuItem(model).doClick());

        assertThat(displayCommandCount(fixture)).isZero();
        assertThat(sidebarPanel(model).isCollapsed()).isFalse();
        assertThat(splitPane(model).getDividerLocation()).isEqualTo(200);
        assertThat(sidebarPanel(model).getMinimumSize().width).isEqualTo(180);
        assertThat(canvasScrollPane(model).getMinimumSize().width).isEqualTo(193);
        assertThat(((BasicSplitPaneUI) splitPane(model).getUI()).getMaximumDividerLocation()).isEqualTo(200);
        model.close();
    }
```

- [ ] **Step 2: Run the new cases and confirm they fail**

Run: `gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould'`
Expected: FAIL — in read-only sessions the stored values revert the session interaction (`keepsReadOnlySidebarInteractionsSessionOnly` sees `collapse` handled but the resize re-applies `264`, not `400`), and the stored-hidden fixture reports the wrong View-item state.

- [ ] **Step 3: Add the session overlay**

In `GraphWorkspaceWindow.java`:

1. Add the two overlay fields after the `sidebarApplying` field:

```java
    private Integer sessionSidebarWidth;
    private Boolean sessionSidebarHidden;
```

2. Replace the first two statements of `applySidebarSettings` with the overlay-aware versions:

```java
        final boolean hidden = readOnly && sessionSidebarHidden != null
            ? sessionSidebarHidden.booleanValue() : settings.mapSidebarHidden();
        final int width = readOnly && sessionSidebarWidth != null
            ? sessionSidebarWidth.intValue() : settings.mapSidebarWidth();
```

3. In `setSidebarCollapsed`, add the overlay write directly after the `runWithSidebarGuard(...)` block and before the `if (!readOnly)` block:

```java
        sessionSidebarHidden = Boolean.valueOf(collapsed);
```

4. In `commitSidebarWidth`, add the overlay write directly after the `if (clamped == appliedSidebarWidth) { return; }` guard and before `if (!readOnly) {`:

```java
        sessionSidebarWidth = Integer.valueOf(clamped);
```

5. In `resetSidebarWidth`, add the same write directly after its `if (clamped == appliedSidebarWidth) { return; }` guard and before its `if (!readOnly) {`.

6. In `setReadOnlyOnEdt`, append this block as the last statement of the method:

```java
        if (!value) {
            sessionSidebarWidth = null;
            sessionSidebarHidden = null;
        }
```

- [ ] **Step 4: Run the tests and confirm they pass**

Run: `gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould'`
Expected: PASS, including all five new methods.

Run: `gradle :freeplane_plugin_graph:test`
Expected: PASS for the whole plugin suite.

- [ ] **Step 5: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java
git commit -m "feat(graph): keep the sidebar usable and session-only in read-only workspaces [2026-09-12-graph-map-sidebar-resize]"
```

## Task 10: Rail active-map badge and menu enablement pinning

**Implementer tier:** Advanced

**Lane:** window-wiring

**Depends on:** 9

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java:1171-1210`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java:1-125`

**Interfaces:**

- Consumes: `MapSidebarPanel.setActiveMapCount(int)` and `MapSidebarPanel.rail()` from Task 4; `MapSidebarPanel.collapseButton()` and `mapsSidebarMenuItem` from Task 7; `MapListPanel.MapRow.partition()`, `MapPartition.ACTIVE` and `MapListPanel.setRows(List)` from the existing code.
- Produces: the rail badge count derived from the same `rows` list handed to `mapList.setRows(rows)` in `updateMapRows(CanvasState state)`.

Note: `mapsSidebarMenuItem.setEnabled(true)` was added to `updateMenuEnablement` in Task 7 step 7; `keepsTheMapsMenuActionsEnabledWhileCollapsed` is the regression pin for it. The badge count is the number of `MapPartition.ACTIVE` rows of the last `updateMapRows` call — the same number `activeHeader` shows — and it updates whether the rail is visible or not. The badge text is empty at zero and the tooltip always reports the count.

- [ ] **Step 1: Add the failing badge and menu cases**

Add these two test methods to `GraphWorkspaceWindowModelShould`:

```java
    @Test
    public void mapsTheActivePartitionCountToTheRailBadge() {
        MapReferenceId activeId = id(201L);
        MapReferenceId inactiveId = id(202L);
        List<GraphWorkspaceViewBinding.MapRegistration> registrations = Arrays.asList(
            registration(activeId, "Active", MapAvailability.AVAILABLE),
            registration(inactiveId, "Inactive", MapAvailability.INACTIVE));
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), registrations, false).answerSidebarRoundTrip();
        GraphWorkspaceWindowModel model = fixture.model();

        assertThat(sidebarPanel(model).rail().countBadge().getText()).isEqualTo("1");
        assertThat(sidebarPanel(model).rail().countBadge().getToolTipText())
            .isEqualTo("graph_workspace.map_list.rail_count[1]");

        List<GraphWorkspaceViewBinding.MapRegistration> allInactive = Collections.singletonList(
            registration(inactiveId, "Inactive", MapAvailability.INACTIVE));
        Fixture empty = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), allInactive, false).answerSidebarRoundTrip();
        GraphWorkspaceWindowModel emptyModel = empty.model();

        assertThat(sidebarPanel(emptyModel).rail().countBadge().getText()).isEmpty();
        assertThat(sidebarPanel(emptyModel).rail().countBadge().getToolTipText())
            .isEqualTo("graph_workspace.map_list.rail_count[0]");

        GraphWorkspaceWindow.runOnEdt(() -> sidebarPanel(emptyModel).collapseButton().doClick());
        GraphWorkspaceWindow.runOnEdt(() -> emptyModel.acceptCanvasState(emptyState()));

        assertThat(sidebarPanel(emptyModel).rail().countBadge().getText()).isEmpty();
        assertThat(displayCommandCount(empty)).isEqualTo(1);
        model.close();
        emptyModel.close();
    }

    @Test
    public void keepsTheMapsMenuActionsEnabledWhileCollapsed() {
        MapReferenceId activeId = id(301L);
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), Collections.singletonList(registration(activeId, "Active", MapAvailability.AVAILABLE)),
            false).answerSidebarRoundTrip();
        GraphWorkspaceWindowModel model = fixture.model();
        layoutSidebarAt(model, 1000);
        model.mapList().selectMap(activeId);
        boolean addBefore = menuItem(model, "add-map").isEnabled();
        boolean deactivateBefore = menuItem(model, "deactivate-map").isEnabled();

        GraphWorkspaceWindow.runOnEdt(() -> mapsSidebarMenuItem(model).doClick());

        assertThat(sidebarPanel(model).isCollapsed()).isTrue();
        assertThat(menuItem(model, "add-map").isEnabled()).isEqualTo(addBefore);
        assertThat(menuItem(model, "deactivate-map").isEnabled()).isEqualTo(deactivateBefore);
        assertThat(mapsSidebarMenuItem(model).isEnabled()).isTrue();
        assertThat(mapsSidebarMenuItem(model).isSelected()).isFalse();
        model.close();
    }
```

- [ ] **Step 2: Run the badge case and confirm it fails**

Run: `gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould'`
Expected: FAIL — `mapsTheActivePartitionCountToTheRailBadge` sees an empty badge text and a `null` tooltip because `updateMapRows` never forwards the count. `keepsTheMapsMenuActionsEnabledWhileCollapsed` is expected to pass already (it pins the Task 7 enablement line); if it fails, the `mapsSidebarMenuItem.setEnabled(true)` line was lost and must be restored.

- [ ] **Step 3: Forward the active count**

In `updateMapRows`, replace the two statements

```java
        mapList.setRows(rows);
        updateMenuEnablement();
```

with:

```java
        int activeCount = 0;
        for (final MapListPanel.MapRow row : rows) {
            if (row.partition() == MapPartition.ACTIVE) {
                activeCount++;
            }
        }
        mapList.setRows(rows);
        sidebar.setActiveMapCount(activeCount);
        updateMenuEnablement();
```

- [ ] **Step 4: Run the tests and confirm they pass**

Run: `gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould'`
Expected: PASS, including both new methods.

- [ ] **Step 5: Run the full gates**

Run: `gradle :freeplane_plugin_graph:test`
Expected: PASS for the whole plugin suite.

Run: `gradle :freeplane:test`
Expected: PASS for the core suite (regression gate for the shared build and the resource edit).

- [ ] **Step 6: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java
git commit -m "feat(graph): show the active map count on the collapsed rail [2026-09-12-graph-map-sidebar-resize]"
```

