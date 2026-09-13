# Graph Workspace — Maps Sidebar: Adjustable Width and Hideable — Specification

- Date: 2026-09-12
- Status: implementation-ready specification derived from the approved design
  (`docs/superpowers/specs/2026-09-12-graph-map-sidebar-resize-design.md`, design-review
  rounds 1–5; round 5 reports zero blockers and zero majors; the committed revision's
  post-round-5 baseline provenance is recorded in §9.1 S8)
- Ticket: none — Task Identifier: `2026-09-12-graph-map-sidebar-resize`
- Scope: `freeplane_plugin_graph` (delivery branch `plugin/graph-workspace`), plus five new keys
  in `freeplane/src/viewer/resources/translations/Resources_en.properties` and two new plugin
  SVG assets
- Approved UI: `docs/superpowers/specs/images/2026-09-12-graph-map-sidebar-resize-mockup.png`
  (scope A — left Maps sidebar only; hide affordance A — in-panel chevron + collapsed rail; rail
  treatment variant 3 — vertical label plus active-map count; persistence A — per workspace;
  resize bounds A — 180 px min, 50 % max, 264 px default, commit on release, double-click reset;
  read-only behaviour B — usable, session-only)
- Code baseline inspected for this specification: `aa6ac48b02` (the run's design commit
  `d3f819a0f8` adds only the design document and mockups; the code under change is its parent)
- Design reviewer reports: `$STATE_ROOT/reports/design-review-<n>.md`
- Mockup generator: `docs/superpowers/specs/mockups/2026-09-12-graph-map-sidebar-resize/MapSidebarMockups.java`
  (a design artefact; it is not part of the build and is not changed by this specification)

Short path convention used below (all under
`freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/`):

- `GraphWorkspaceWindow.java` = `window/GraphWorkspaceWindow.java` (contains both
  `GraphWorkspaceWindow` and package-private `GraphWorkspaceWindowModel`)
- `MapListPanel.java` = `window/MapListPanel.java`
- `WorkspaceSettingsPanel.java` = `window/WorkspaceSettingsPanel.java`
- `WorkspaceToolbar.java` = `window/WorkspaceToolbar.java`
- `WorkspaceXmlCodec.java` = `workspace/io/WorkspaceXmlCodec.java`
- `DisplaySettings.java` = `workspace/model/DisplaySettings.java`
- `WorkspaceCommands.java` = `workspace/WorkspaceCommands.java`
- `GraphWorkspaceWindowModelShould.java` =
  `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java`

Normative keywords: **must**, **must not**, **shall** are binding. Behaviour is specified as
externally observable state unless a private field is named explicitly (the field names in §3.9.2
are part of the contract because the design names them and the test plan asserts their effects).

## 1. Scope

### 1.1 In scope (the design's goals 1–6)

1. The Maps sidebar width is adjustable by dragging a divider on its right edge, with the
   Look-and-Feel's split-pane affordances and no custom divider painting.
2. The sidebar can be hidden and restored from three places: the heading collapse chevron, the
   collapsed rail's restore chevron, and a checkable `View → Maps sidebar` item whose checked
   state always reflects the layout.
3. Width and hidden state persist per workspace, travel with the workspace file, add no format
   version bump, and never lose unknown XML.
4. Everything in the sidebar keeps working while hidden: the `Maps` menu remains the full action
   surface and the rail carries the active-map count.
5. One layout gesture produces at most one workspace mutation; programmatic re-application never
   produces one.
6. The right Display panel keeps its shipped appearance and behaviour; only its settings
   pass-through line changes (§3.7).

### 1.2 Non-goals

- Resizing or restyling the right Display panel; giving it a drag divider.
- Moving, docking, floating or re-ordering the Maps sidebar (left edge only).
- Animating collapse/expand.
- A toolbar toggle button for the sidebar.
- Persisting layout in Freeplane preferences (global) or per session only.
- Changing `MapListPanel`'s row content, ordering, selection model, or the map actions.
- A format-version bump or a `WorkspaceMigration` for this change.
- A compatibility overload of `DisplaySettings.of(...)` (repository Legacy Removal Policy).
- Any change to `WorkspaceToolbar`, `GraphCanvas`, the projection/layout code, or the workspace
  command/undo machinery beyond the `DisplaySettings` signature and the five new resource keys.

### 1.3 Approved treatment and specification notes

| Mockup element | This specification | Why |
| --- | --- | --- |
| Collapsed-rail badge painted as a filled accent pill | A plain `JLabel` whose text is the numeral (empty at zero), tooltip from `graph_workspace.map_list.rail_count` | Design §4.2/§8: "background and border from the current Look-and-Feel; no custom painting", "the badge itself paints only the numeral", "labels rather than painted graphics". The pill in the mockup is a design approximation; no new colour is introduced. |
| Rotated `MAPS` label | A `JLabel` drawn rotated 90° clockwise (text reads top-to-bottom), as in the mockup's `drawRail` (`g.rotate(Math.toRadians(90))`) | The design fixes "vertical label" (variant 3) and "rotated `MAPS` label"; the mockup generator is the only source for the direction. |
| Collapse chevron `◀` / restore chevron `▶` | Icon-only buttons from the existing accent SVG pipeline, text fallback localized | Design §6.2/§6.4; same fallback rule as `WorkspaceToolbar.iconButton(...)` (`WorkspaceToolbar.java:382-388`, `configureIcon` `:398-409`). |

## 2. Traceability

| Design goal / requirement | Implemented in this specification |
| --- | --- |
| Goal 1 — draggable width, standard affordances (`Design §2.1`, `§4.1`, `§4.3`) | §3.2 (`MapSidebarLayout`), §3.9.3 (`applySidebarSettings`), §3.9.5 (commit triggers), §4.1–§4.3; tests 5.3 (`MapSidebarSplitClampShould`), 5.6 cases 2, 4, 6, 11, 12 |
| Goal 2 — hide/restore from chevron, rail, `View` item (`§2.2`, `§4.1`, `§4.2`, `§6.3`–`§6.5`) | §3.3 (`MapSidebarRail`), §3.4 (`MapSidebarPanel`), §3.5 (heading row), §3.9.4 (`setSidebarCollapsed`), §3.9.8; tests 5.4, 5.6 cases 5, 8, 9, 15 |
| Goal 3 — per-workspace persistence, no version bump, unknown XML kept (`§2.3`, `§5.1`) | §3.7 (`WorkspaceXmlCodec`), §3.8 (`WorkspaceSettingsPanel` pass-through), §3.6 (`DisplaySettings`), §4.5; tests 5.7, 5.8, 5.9 |
| Goal 4 — sidebar content usable while hidden, rail count (`§2.4`, `§4.2`) | §3.3 (badge), §3.9.7 (`updateMapRows` count), §4.4 (zero-map row); tests 5.4 case 4, 5.6 cases 13, 14 |
| Goal 5 — at most one mutation per gesture, none from re-application (`§2.5`, `§4.3`) | §3.9.3, §3.9.5 (baseline and triggers), §4.2 (suppression), tests 5.3 cases 4–6, 5.6 cases 2, 6, 7, 11, 12 |
| Goal 6 — Display panel unchanged except the pass-through (`§2.6`, `§5.1`) | §3.8 (two fields passed through), test 5.6 case 3; `WorkspaceSettingsPanel`'s controls, boundary, names and enablement are otherwise untouched |
| Design §6.1 constants/formulas | §3.2, §4.1 |
| Design §6.2 rail contents | §3.3 |
| Design §6.3 panel state/API | §3.4 |
| Design §6.4 list/heading changes | §3.5 |
| Design §6.5 window wiring | §3.9 |
| Design §6.6 assets and strings | §3.10 |
| Design §7 error/edge table | §4.4 |
| Design §8 accessibility | §7 |
| Design §5.3 read-only overlay | §3.9.8, §4.5 |
| Design §9 test strategy | §5 |

## 3. Contracts

### 3.1 Constants and component names

| Item | Value | Source |
| --- | --- | --- |
| `MapSidebarLayout.DEFAULT_WIDTH` | `264` (`= DisplaySettings.DEFAULT_MAP_SIDEBAR_WIDTH`) | design §6.1 |
| `MapSidebarLayout.MIN_WIDTH` | `180` | design §4.1, §6.1 |
| `MapSidebarLayout.RAIL_WIDTH` | `26` | design §4.2, §6.1 |
| `DisplaySettings.DEFAULT_MAP_SIDEBAR_WIDTH` | `264` (public constant, model layer) | design §5.1 |
| Default `mapSidebarHidden` | `false` | design §5.1 |
| Split-pane divider size (logical) | `6` (`setDividerSize(6)`; `0` while collapsed) | design §4.3, §6.5 |
| XML attribute, width | `map-sidebar-width` (optional) | design §5.1 |
| XML attribute, hidden | `map-sidebar-hidden` (optional) | design §5.1 |
| Split pane name | `graph-workspace-split` | design §6.5 |
| Sidebar panel name | `graph-workspace-map-sidebar` | design §6 |
| Rail name | `graph-workspace-map-sidebar-rail` | design §6 |
| Rail count badge name | `graph-workspace-map-sidebar-count` | design §6.2 |
| Heading collapse button name | `graph-workspace-map-sidebar-collapse` | design §6.4 |
| `View` check item name | `graph-workspace-maps-sidebar-menu-item` | design §6.5 |
| Map list panel name (unchanged) | `graph-workspace-map-list` | existing `MapListPanel.java:193` |
| Map list heading label name (unchanged) | `graph-workspace-map-list-heading` | existing `MapListPanel.java:200` |
| Canvas scroll pane name (unchanged) | `graph-workspace-scroll-pane` | existing `GraphWorkspaceWindow.java:438` |
| **Specification-fixed** rail restore button name | `graph-workspace-map-sidebar-expand` | design does not name it; §9 S5 |
| **Specification-fixed** rail vertical label name | `graph-workspace-map-sidebar-label` | design does not name it; §9 S5 |
| **Specification-fixed** map-list heading row name | `graph-workspace-map-list-heading-row` | design does not name the new `NORTH` container; §9 S5 |
| Collapse chevron icon path | `/images/MapSidebarCollapse.svg?useAccentColor=true` | design §6.6 |
| Restore chevron icon path | `/images/MapSidebarExpand.svg?useAccentColor=true` | design §6.2, §6.6 |

### 3.2 `MapSidebarLayout` (new, package-private, `org.freeplane.plugin.graph.window`)

```java
final class MapSidebarLayout {
    static final int DEFAULT_WIDTH = DisplaySettings.DEFAULT_MAP_SIDEBAR_WIDTH; // 264
    static final int MIN_WIDTH = 180;
    static final int RAIL_WIDTH = 26;

    private MapSidebarLayout() {
    }

    static int maximumWidth(final int splitWidth);
    static int effectiveMinimum(final int splitWidth, final int effectiveDividerWidth, final Insets insets);
    static int clampWidth(final int requestedWidth, final int splitWidth, final int effectiveDividerWidth,
            final Insets insets);
    static int canvasMinimumWidth(final int splitWidth, final int effectiveDividerWidth, final Insets insets);
}
```

Exact arithmetic (all `int`, pixels, pure; `Insets` must be non-null at every call site):

```java
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
```

Contract notes (design §6.1):

- `DEFAULT_WIDTH` re-uses the model constant so the literal `264` exists once; `MapListPanel`'s
  initial preferred width is set from the same constant (§3.5).
- `clampWidth`'s range is never empty because `effectiveMinimum <= max(effectiveMinimum, maximumWidth)`;
  that fixed point is what lets the re-clamp listener converge in every regime.
- `canvasMinimumWidth` is expressed in the Look-and-Feel's own terms: for a horizontal split the
  L&F computes `maxDividerLocation = splitPaneWidth - rightMinimum - effectiveDividerWidth - insets.right`
  and then wraps it in `max(minimumDividerLocation, …)`. With
  `rightMinimum = canvasMinimumWidth(...)` the value `max(effectiveMinimum, maximumWidth)` is
  reproduced exactly whenever it is above the L&F minimum, and collapses onto the L&F's
  `minimumDividerLocation` otherwise (the pinned and squeezed regimes of §4.1).
- **Every** call to the three geometry functions must pass the effective laid-out divider width
  (`((BasicSplitPaneUI) splitPane.getUI()).getDivider().getDividerSize()`, which FlatLaf scales),
  never the logical `6` passed to `setDividerSize` (design §4.3, review round 4 B4-1 and round 5).
  The logical `6` appears only in `setDividerSize(6)` and `setDividerSize(hidden ? 0 : 6)`.

### 3.3 `MapSidebarRail` (new, package-private, `window`)

```java
final class MapSidebarRail extends JPanel {
    MapSidebarRail();
    @Override public Dimension getPreferredSize();
    void setActiveMapCount(final int count);
    JButton restoreButton();   // test accessor (specification-fixed, §3.1)
    JLabel countBadge();       // test accessor (specification-fixed, §3.1)
}
```

Construction and contents, top to bottom (design §4.2, §6.2):

1. `setName("graph-workspace-map-sidebar-rail")`; `setLayout(new BoxLayout(this, BoxLayout.Y_AXIS))`;
   every child is aligned to `Component.CENTER_ALIGNMENT`. The panel is opaque, so the JPanel
   background and the L&F border chrome apply; **no custom painting** is added to the panel.
2. Restore chevron: `MapSidebarPanel.chevronButton("graph_workspace.map_list.expand",
   "graph-workspace-map-sidebar-expand", "/images/MapSidebarExpand.svg?useAccentColor=true")`
   (§3.4 helper). 18 px square: after the helper, `setPreferredSize(new Dimension(18, 18))` and
   `setMaximumSize(new Dimension(18, 18))`; tooltip/accessible name are set from the same key
   only when the icon resolves, and the localized text is the visible fallback otherwise (§3.4
   step 4). A click is wired by `MapSidebarPanel`
   (after it constructs the rail) to `setCollapsed(false)`; see §9 S2.
3. Count badge: `new JLabel()`, name `graph-workspace-map-sidebar-count`.
4. Rotated label: a private static `VerticalLabel extends JLabel` carrying
   `TextUtils.getText("graph_workspace.map_list.rail_label")`, name
   `graph-workspace-map-sidebar-label`, font derived small/bold in the style of the shipped
   `activeHeader` (`MapListPanel.java:204-207`), muted foreground, `setOpaque(false)`.

`getPreferredSize()`: `new Dimension(MapSidebarLayout.RAIL_WIDTH, super.getPreferredSize().height)`.

`VerticalLabel` recipe (design has no further detail; §9 S6):

```java
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
```

`setActiveMapCount(int)` (design §4.2, §6.2):

- Remembers the last value (initial sentinel `-1`).
- Unchanged value ⇒ return immediately (idempotent; no repaint churn).
- Otherwise: `count == 0` ⇒ `countBadge().setText("")` (nothing is painted; no `0` pill);
  `count > 0` ⇒ `countBadge().setText(Integer.toString(count))` (the numeral only).
- In both cases set `countBadge().setToolTipText(TextUtils.format(
  "graph_workspace.map_list.rail_count", Integer.valueOf(count)))`, so the tooltip reports the
  count even at zero. The tooltip is therefore reachable while the badge text is empty.

### 3.4 `MapSidebarPanel` (new, package-private, `window`)

```java
final class MapSidebarPanel extends JPanel {
    @FunctionalInterface
    interface CollapsedListener {
        void collapsedChanged(final boolean collapsed);
    }

    MapSidebarPanel(final MapListPanel mapList, final CollapsedListener listener);
    void setCollapsed(final boolean collapsed);
    boolean isCollapsed();
    void setActiveMapCount(final int count);
    void setReadOnly(final boolean readOnly);
    MapListPanel mapList();
    MapSidebarRail rail();
    JButton collapseButton();

    // specification-fixed shared helper (§3.1, §9 S2)
    static JButton chevronButton(final String textKey, final String name, final String iconPath);
}
```

State (design §6.3, corrected in review round 1 m7): `collapsed` (layout state, Java-default
`false` until the first real `setCollapsed` change) and `readOnly` (a forwarding flag for the map
list, not layout state).

Construction:

1. `Objects.requireNonNull(mapList, "mapList")`, `Objects.requireNonNull(listener, "listener")`.
2. `setName("graph-workspace-map-sidebar")`; `setLayout(new BorderLayout())`.
3. Create the rail: `final MapSidebarRail rail = new MapSidebarRail();` and wire its restore
   button to `setCollapsed(false)`; then `add(rail, BorderLayout.WEST)` and
   `add(mapList, BorderLayout.CENTER)`.
4. Apply the initial expanded state without notifying the listener: `applyCollapsedState(false)`
   (private helper below) sets `rail.setVisible(false)`, `mapList.setVisible(true)`,
   `setPreferredSize(new Dimension(MapSidebarLayout.DEFAULT_WIDTH, 0))` and
   `setMinimumSize(new Dimension(MapSidebarLayout.MIN_WIDTH, 0))`. The constructor **must not**
   call `listener.collapsedChanged(...)`: the window is still evaluating
   `new MapSidebarPanel(mapList, this::setSidebarCollapsed)` (`GraphWorkspaceWindow.java:443`),
   so a callback would reach `setSidebarCollapsed` before the window's `sidebar` field is
   assigned. `collapsed` keeps its Java-default `false`, so a later `setCollapsed(false)` is the
   idempotent no-op it should be.
5. No list-width `ComponentListener` is installed on this panel: `MapListPanel` installs its own
   on the component whose width it tracks (§3.5, §9 S3), so no sibling layout ordering is
   assumed.

`setCollapsed(boolean)` — idempotent by design (§6.3) so it cannot re-enter its own listener:

```java
void setCollapsed(final boolean collapsed) {
    if (this.collapsed == collapsed) {
        return;
    }
    this.collapsed = collapsed;
    applyCollapsedState(collapsed);
    listener.collapsedChanged(collapsed);
}

private void applyCollapsedState(final boolean collapsed) {
    rail().setVisible(collapsed);
    mapList().setVisible(!collapsed);
    final int width = collapsed ? MapSidebarLayout.RAIL_WIDTH : MapSidebarLayout.DEFAULT_WIDTH;
    setPreferredSize(new Dimension(width, 0));
    setMinimumSize(new Dimension(collapsed ? MapSidebarLayout.RAIL_WIDTH : MapSidebarLayout.MIN_WIDTH, 0));
}
```

- Exactly one of rail/list is visible.
- Expanded: preferred `DEFAULT_WIDTH` (264), minimum `MIN_WIDTH` (180). Collapsed: preferred and
  minimum `RAIL_WIDTH` (26). The window then narrows the expanded minimum to
  `effectiveMinimum(...)` in its apply path (§3.9.3 step 8) — the panel itself has no split-pane
  geometry (§9 S1).
- The listener fires only for real changes and never from the constructor; the window's guard
  suppresses the callback for its own programmatic `setCollapsed` calls (§3.9.4).
- `setReadOnly(boolean)` stores the flag and calls `mapList().setReadOnly(value)`; it never changes
  visibility.
- `setActiveMapCount(int)` forwards to `rail().setActiveMapCount(count)`.
- `collapseButton()` returns `mapList().collapseButton()`.
- The rail's restore chevron is wired by the panel to `setCollapsed(false)`; the heading collapse
  button's action is supplied by the window (design §6.4); see §9 S2.

`chevronButton(textKey, name, iconPath)` — the icon pipeline shared by both chevrons, the
resolved/fallback semantics of `WorkspaceToolbar.iconButton(...)` (`:382-388`) and
`configureIcon` (`:398-409`), minus `applyToolbarSegmentStyle` and the toolbar's derived disabled
icon (§9 S7):

1. `final JButton button = new JButton(TextUtils.getText(textKey));`
2. `final Icon icon = ResourceController.getResourceController().getOptionalIcon(iconPath);`
3. `icon != null`: `setIcon(icon)`, `setText(null)`, `setToolTipText(TextUtils.getText(textKey))`,
   `getAccessibleContext().setAccessibleName(TextUtils.getText(textKey))`.
4. `icon == null`: the text from step 1 stays; tooltip and accessible name stay unset.
5. `setName(name)`, `setMargin(new Insets(2, 7, 2, 7))`, `setFocusable(false)`.
6. No client property and no derived disabled icon (both chevrons stay enabled in every state;
   §9 S7); no custom painting.

### 3.5 `MapListPanel` changes

Kept: row content, partitions, selection, actions, `ROW_HEIGHT`, all names except the new heading
row, and every existing method.

Removed: `private static final int PANEL_WIDTH = 264;` (`MapListPanel.java:40`). The initial
preferred width is `MapSidebarLayout.DEFAULT_WIDTH` instead, so the literal `264` exists once
(design §6.1, review round 2 m2-4).

Changed members and exact new behaviour (design §6.4):

| Element | Before | After |
| --- | --- | --- |
| `setPreferredSize` (`:196`) | `(PANEL_WIDTH, 0)` | `(MapSidebarLayout.DEFAULT_WIDTH, 0)` — seeds the first divider layout |
| `setMinimumSize` (`:197`) | `(PANEL_WIDTH, 0)` | `(0, 0)` — `BorderLayout` propagates child minimums upward; the real minimum belongs to `MapSidebarPanel` |
| `NORTH` component (`:200-202`) | the heading `JLabel` directly | a new `JPanel(BorderLayout)` named `graph-workspace-map-list-heading-row`, holding the existing heading `JLabel` (`WEST`, name and border unchanged) and the collapse button (`EAST`) |
| collapse button | — | `MapSidebarPanel.chevronButton("graph_workspace.map_list.collapse", "graph-workspace-map-sidebar-collapse", "/images/MapSidebarCollapse.svg?useAccentColor=true")`, exposed as `JButton collapseButton()`; the window attaches the action |
| list preferred sizes (`:336-337`) | `(PANEL_WIDTH, rows * ROW_HEIGHT)` | `(syncListWidths()` value`, rows * ROW_HEIGHT)`; see below |
| `RowRenderer` cell width (`:594`) | `(PANEL_WIDTH - 24, ROW_HEIGHT)` | `(list.getWidth(), ROW_HEIGHT)`, derived from the `list` argument already passed to `getListCellRendererComponent` |

New package-private method:

```java
void syncListWidths();
```

- `width = Math.max(0, getWidth() - getInsets().left - getInsets().right)` — the panel's current
  content width.
- `activeList.setPreferredSize(new Dimension(width, activeModel.size() * ROW_HEIGHT))` and the
  same for `inactiveList`; then `revalidate()` on both lists so the new preferred size takes
  effect.
- Called from `setRows(...)` (replacing the two constant-width calls) and from a
  `ComponentListener.componentResized` installed by `MapListPanel` itself, so a sidebar resized
  without a subsequent `setRows(...)` does not keep stale list widths. (The design names
  `MapSidebarPanel` as the listener's owner; §9 S3 documents the placement fix: the listener
  reads the width of the component it is installed on, whose bounds `setBounds` sets
  synchronously, so no sibling layout ordering is assumed.)
- The window also calls it once per `applySidebarSettings` pass after the divider is placed
  (§3.9.3 step 12).

The renderer's `getListCellRendererComponent` sets `setPreferredSize(new Dimension(list.getWidth(),
ROW_HEIGHT))`; a wider sidebar therefore paints full-width rows and selection highlight. Nothing
else in `RowRenderer` changes.

### 3.6 `DisplaySettings` (changed, public, `workspace.model`)

```java
public final class DisplaySettings {
    public static final int DEFAULT_MAP_SIDEBAR_WIDTH = 264;

    private final int mapSidebarWidth;
    private final boolean mapSidebarHidden;

    private DisplaySettings(final boolean showArrowheads, final CanvasTheme canvasTheme,
            final boolean rememberViewport, final boolean dimUnrelatedNodes,
            final int mapSidebarWidth, final boolean mapSidebarHidden, final List<UnknownXml> unknownXml);

    public static DisplaySettings defaults();
    public static DisplaySettings of(final boolean showArrowheads, final CanvasTheme canvasTheme,
            final boolean rememberViewport, final boolean dimUnrelatedNodes,
            final int mapSidebarWidth, final boolean mapSidebarHidden, final List<UnknownXml> unknownXml);

    public int mapSidebarWidth();
    public boolean mapSidebarHidden();
}
```

- `defaults()` returns `new DisplaySettings(true, CanvasTheme.FOLLOW_FREEPLANE, true, true,
  DEFAULT_MAP_SIDEBAR_WIDTH, false, Collections.<UnknownXml>emptyList())` (existing call sites
  `WorkspaceDocument.java:77,270`, `GraphWorkspacePresentation.java:33`,
  `GraphWorkspaceViewBinding.java:18` and the tests keep working).
- The private constructor and `of(...)` keep the existing parameter order and append
  `mapSidebarWidth, mapSidebarHidden` before `unknownXml`; both assign the fields.
- `equals` and `hashCode` include `mapSidebarWidth` and `mapSidebarHidden`; `toString` adds
  `mapSidebarWidth=` and `mapSidebarHidden=` between `dimUnrelatedNodes` and `unknownXml`.
- **No compatibility overload** of `of(...)` is added. All 15 existing explicit call sites must be
  updated to the 7-argument form (verified list, design §5.1):
  main `WorkspaceSettingsPanel.java:149`, `WorkspaceXmlCodec.java:312`; tests
  `GeneratedWorkspace.java:411`, `GraphWorkspaceWindowModelShould.java:1204,1224,1226,1264`,
  `WorkspaceCommandsShould.java:456`, `WorkspaceXmlCodecShould.java:236`,
  `WorkspaceHistoryShould.java:110,161,181`, `GraphCommandRouterShould.java:165`,
  `GraphWorkspacePresentationShould.java:29`, `GraphWorkspaceModelAcceptanceShould.java:184`.
  Compilation of the whole module is the falsifier for a missed site.

### 3.7 `WorkspaceXmlCodec` changes

Decoding — `parseDisplaySettings` (`WorkspaceXmlCodec.java:308-318`) becomes:

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

Decoding rules (exact; `positiveInt` and `booleanValue` already exist,
`WorkspaceXmlCodec.java:827-838`, `:866-874`):

| Input | Result |
| --- | --- |
| attribute absent | `264` / `false` (defaults; no format bump, earlier builds load unchanged) |
| `map-sidebar-width=""` | `requiredAttribute` → `WorkspaceFormatException` "Required map-sidebar-width attribute on display-settings must not be empty" |
| `map-sidebar-width="abc"` | `positiveInt` → `WorkspaceFormatException` "map-sidebar-width must be an integer" |
| `map-sidebar-width="0"` or negative | `positiveInt` → `WorkspaceFormatException` "map-sidebar-width must be positive" |
| any positive valid integer, including values outside 180 … 50 % | accepted and stored verbatim; clamping is display-only |
| `map-sidebar-hidden=""` | `requiredAttribute` → "Required map-sidebar-hidden attribute on display-settings must not be empty" |
| `map-sidebar-hidden="yes"`/other | `booleanValue` → "map-sidebar-hidden must be true or false" |
| `map-sidebar-hidden="true"/"false"` | parsed |

Writing — `displaySettingsXml` (`:565-577`) adds the two attributes to the existing
`attributes(...)` call, in this order, always:

```java
"map-sidebar-width", Integer.toString(settings.mapSidebarWidth()),
"map-sidebar-hidden", Boolean.toString(settings.mapSidebarHidden())
```

After `"dim-unrelated-nodes"`. Consequences:

- Both names are known attributes to `recordUnknownXml` (`:327-330`) and to `attributes(...)`, so
  they are never duplicated into unknown XML and no other unknown-XML behaviour changes.
- The writer always emits them; files written by earlier builds gain the two attributes on their
  next save and stay byte-stable afterwards (`roundTripAllKnownAndUnknownFieldsWithoutWritingDuringRead`).
- `CURRENT_FORMAT_VERSION` stays `1`; no `WorkspaceMigration` is added.
- Read-only documents are still rejected by `write` (`:84-90`).

### 3.8 `WorkspaceSettingsPanel` change (single line)

`publishSettings()` (`:145-153`) must pass the current sidebar fields through:

```java
settings = DisplaySettings.of(showArrowheads.isSelected(),
    (CanvasTheme) canvasTheme.getSelectedItem(), rememberViewport.isSelected(),
    dimUnrelated.isSelected(), settings.mapSidebarWidth(), settings.mapSidebarHidden(),
    settings.unknownXml());
```

Without this pass-through, toggling any Display checkbox would silently reset the sidebar to
264/expanded (design review round 1 B1). Nothing else in the panel changes: controls, names,
`approvedSettingNames` (`:39-40`), boundary, preferred width `244` (`:30`), enablement and
`setSettings` are untouched.

### 3.9 `GraphWorkspaceWindowModel` wiring

All changes are in `window/GraphWorkspaceWindow.java` (the model class is in the same file).

#### 3.9.1 Construction and content

- The split pane is created by `createContent()` (`:1036-1049`) as
  `new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebar, graphScrollPane)` with
  `setName("graph-workspace-split")`, `setContinuousLayout(true)`, `setDividerSize(6)`,
  `setResizeWeight(0.0)`, and installed as `graphArea.add(splitPane, BorderLayout.CENTER)`.
  `mapList` is no longer added to `graphArea` directly. `settingsPanel` stays
  `graphArea.add(settingsPanel, BorderLayout.EAST)`. The graph area keeps its existing name
  `graph-workspace-graph-area` (`:1038`), so it has exactly two direct children — the split pane
  in `CENTER` and `settingsPanel` in `EAST`; the sidebar panel is the split pane's left
  component, never a child of `graphArea` (tests navigate by name and role, §5.1).
- The sidebar panel is created next to `mapList = new MapListPanel(...)`
  (`GraphWorkspaceWindow.java:443`), before `createContent()`, as
  `sidebar = new MapSidebarPanel(mapList, this::setSidebarCollapsed);`, and the heading collapse
  button's action is attached immediately:
  `sidebar.collapseButton().addActionListener(event -> setSidebarCollapsed(true));`.
- The split pane's listeners are installed in `createContent()` right after construction:
  divider `MouseListener` (press/release/double-click), split-pane `PropertyChangeListener` for
  `JSplitPane.DIVIDER_LOCATION_PROPERTY`, split-pane `FocusListener`, and split-pane
  `ComponentListener` (§3.9.5, §3.9.6).
- `View → Maps sidebar`: a `JCheckBoxMenuItem` created immediately after `viewSettingsMenuItem`
  (`:1093-1095`) and added after it: text `TextUtils.getText("graph_workspace.action.maps_sidebar")`,
  name `graph-workspace-maps-sidebar-menu-item`, listener
  `event -> setSidebarCollapsed(!sidebar.isCollapsed())`. It is created directly, not through
  `item(...)`, whose `"graph-workspace-menu-item-"` prefix (`:1383`) would produce a different
  name (B3). `updateMenuEnablement` (`:1009-1034`) adds
  `mapsSidebarMenuItem.setEnabled(true)` next to `viewSettingsMenuItem.setEnabled(true)`
  (`:1016`), so the item is enabled in every state, including read-only. No other code enables or
  disables it.
- `applyPresentation(GraphWorkspacePresentation value)` (`:557-571`) ends with
  `applySidebarSettings(next.displaySettings())` (the local `settings = next.displaySettings()`
  at `:562`), so every presentation change flows through the single apply path.

#### 3.9.2 Fields (names are part of the contract)

```java
private final MapSidebarPanel sidebar;
private JSplitPane splitPane;                 // created in createContent()
private JCheckBoxMenuItem mapsSidebarMenuItem;
private int appliedSidebarWidth;
private boolean sidebarGestureActive;
private boolean sidebarApplyPending;
private boolean sidebarApplying;              // the re-entrancy guard
private Integer sessionSidebarWidth;          // session overlay (read-only)
private Boolean sessionSidebarHidden;         // session overlay (read-only)

int appliedSidebarWidth();                    // package-private test accessor (§5.6 case 11)
```

The field stays `private`; the package-private accessor exists so §5.6 case 11 can assert the
commit baseline without field reflection (the suite is in the same package). All other helpers
are private:

```java
private int splitWidth();                        // splitPane.getWidth()
private int effectiveDividerWidth();             // divider component's getDividerSize(), null -> 0
private Insets splitInsets();                    // splitPane.getInsets(), null -> new Insets(0, 0, 0, 0)
private void applySidebarSettings(final DisplaySettings settings);
private void setSidebarCollapsed(final boolean collapsed);
private void commitSidebarWidth(final int location);
private void resetSidebarWidth();
private void runPendingSidebarApply();
private void runWithSidebarGuard(final Runnable action);
private void commitSidebarDisplaySettings(final int width, final boolean hidden);
```

`effectiveDividerWidth()` (**must** be used by every clamp input, design §4.3):

```java
private int effectiveDividerWidth() {
    final Component divider = ((BasicSplitPaneUI) splitPane.getUI()).getDivider();
    return divider == null ? 0 : divider.getDividerSize();
}
```

`commitSidebarDisplaySettings(width, hidden)` builds the next immutable snapshot from the current
presentation and sends it through the one undoable path:

```java
final DisplaySettings current = currentPresentation.displaySettings();
executeCommand(GraphCommands.display(DisplaySettings.of(current.showArrowheads(),
    current.canvasTheme(), current.rememberViewport(), current.dimUnrelatedNodes(), width, hidden,
    current.unknownXml())));
```

#### 3.9.3 `applySidebarSettings(DisplaySettings settings)` — the single apply path

This is the only code that reads stored/session state and the only writer of
`appliedSidebarWidth` (design §6.5). Exact order:

1. Compute the effective hidden/width from the **session overlay** when the workspace is read-only,
   otherwise from the argument:
   - `hidden = readOnly && sessionSidebarHidden != null ? sessionSidebarHidden.booleanValue() : settings.mapSidebarHidden()`
   - `width  = readOnly && sessionSidebarWidth  != null ? sessionSidebarWidth.intValue()   : settings.mapSidebarWidth()`
2. If `sidebarGestureActive`: `sidebarApplyPending = true`; return (defer; §3.9.5).
3. If `splitPane.getWidth() <= 0`: `sidebarApplyPending = true`; return (initial layout; §3.9.6).
4. `runWithSidebarGuard(...)` around steps 5–13:
5. `sidebar.setCollapsed(hidden)` (its listener is suppressed by the guard).
6. `splitPane.setDividerSize(hidden ? 0 : 6)`, then `splitPane.setEnabled(!hidden)`. The
   divider-size change must precede step 7: `effectiveDividerWidth()` reads the divider component's
   laid-out size, so on a hidden → expanded transition the geometry read must see the restored
   `6`/L&F-scaled width and never the collapsed `0`. This ordering is what keeps one apply
   sufficient on the read-only path (where no commit round-trip follows) and for undo/redo or
   `acceptCanvasState` transitions that flip `hidden` (review round 2, M2-1; design §6.5).
7. Read live geometry **after** step 6: `splitWidth = splitWidth()`,
   `d = effectiveDividerWidth()`, `insets = splitInsets()`; compute
   `effectiveMinimum = MapSidebarLayout.effectiveMinimum(splitWidth, d, insets)`,
   `canvasMinimum = MapSidebarLayout.canvasMinimumWidth(splitWidth, d, insets)` and
   `appliedWidth = hidden ? MapSidebarLayout.RAIL_WIDTH
   : MapSidebarLayout.clampWidth(width, splitWidth, d, insets)`.
8. `sidebar.setMinimumSize(new Dimension(hidden ? MapSidebarLayout.RAIL_WIDTH : effectiveMinimum, 0))`.
9. `splitPane.setDividerLocation(appliedWidth)`.
10. `mapsSidebarMenuItem.setSelected(!hidden)`.
11. `appliedSidebarWidth = appliedWidth`.
12. `graphScrollPane.setMinimumSize(new Dimension(canvasMinimum, 0))` and
    `sidebar.mapList().syncListWidths()`.
13. `sidebarApplyPending = false`.

`runWithSidebarGuard` saves the previous value, sets `sidebarApplying = true`, runs the action and
restores the previous value in `finally`, so nested uses are safe.

Callers: `applyPresentation(GraphWorkspacePresentation)` (`:557-571`, passing
`next.displaySettings()`) and the split pane's `ComponentListener` (`componentResized`,
§3.9.6). No other code calls `applySidebarSettings`.

#### 3.9.4 The three guarded user entry points

All three are owned by the window and are the only user paths that write the session overlay or
commit (design §6.5).

**`setSidebarCollapsed(boolean collapsed)`** — called by the heading chevron (window action), the
rail chevron (panel listener) and the `View` item:

1. If `sidebarApplying`: return (listener re-entrance guard).
2. `sidebarGestureActive = false` (a collapse during a drag terminates the gesture; §4.3).
3. `runWithSidebarGuard(() -> sidebar.setCollapsed(collapsed))`.
4. `sessionSidebarHidden = Boolean.valueOf(collapsed)`.
5. If `!readOnly`: `commitSidebarDisplaySettings(currentPresentation.displaySettings().mapSidebarWidth(), collapsed)`.
6. `applySidebarSettings(currentPresentation.displaySettings())` — places the divider (restores the
   effective width on expand) and restores `dividerSize`/`enabled` in every direction.

**`commitSidebarWidth(int location)`** — called by the two commit triggers (§3.9.5):

1. `final int splitWidth = splitWidth(); final int d = effectiveDividerWidth();
   final Insets insets = splitInsets();` — the three live-geometry locals every clamp call in
   this sketch uses (review round 2, m2-1).
2. Return if `sidebar.isCollapsed()`.
3. Return if the squeezed regime holds:
   `MapSidebarLayout.effectiveMinimum(splitWidth, d, insets) < MapSidebarLayout.MIN_WIDTH`.
4. `clamped = MapSidebarLayout.clampWidth(location, splitWidth, d, insets)`; return if
   `clamped == appliedSidebarWidth` (the baseline makes no-op gestures and post-apply focus losses
   no-ops and preserves an out-of-range stored width).
5. `sessionSidebarWidth = Integer.valueOf(clamped)`.
6. If `!readOnly`: `commitSidebarDisplaySettings(clamped, currentPresentation.displaySettings().mapSidebarHidden())`.
7. `applySidebarSettings(currentPresentation.displaySettings())` — refreshes `appliedSidebarWidth`
   uniformly (in the writable case from the committed snapshot, in the read-only case from the
   overlay).

**`resetSidebarWidth()`** — the divider double-click handler (§4.1: expanded and gesture not in
flight):

1. `final int splitWidth = splitWidth(); final int d = effectiveDividerWidth();
   final Insets insets = splitInsets();` — the same live-geometry locals as
   `commitSidebarWidth`.
2. Return if `sidebar.isCollapsed()`, if `sidebarGestureActive`, or in the squeezed regime
   (`MapSidebarLayout.effectiveMinimum(splitWidth, d, insets) < MapSidebarLayout.MIN_WIDTH`,
   step 3 of `commitSidebarWidth`).
3. `clamped = MapSidebarLayout.clampWidth(MapSidebarLayout.DEFAULT_WIDTH, splitWidth, d, insets)`
   — the reset commits the default **clamped into the current geometry**, like every other
   commit path, so it can never persist a value outside `[U, max(U, maxW)]`; at a ceiling below
   264 the committed value is the ceiling (review round 2, M2-2; §4.2).
4. Return early when `clamped == appliedSidebarWidth` — the same equal-baseline no-op as
   `commitSidebarWidth` step 5, so a double-click at the already-applied width commits nothing
   (§4.2/§4.4).
5. `sessionSidebarWidth = Integer.valueOf(clamped)`.
6. If `!readOnly`: `commitSidebarDisplaySettings(clamped, currentPresentation.displaySettings().mapSidebarHidden())`.
7. `applySidebarSettings(currentPresentation.displaySettings())`.

#### 3.9.5 Commit triggers and gesture invariants (exact)

Divider mouse listener on `((BasicSplitPaneUI) splitPane.getUI()).getDivider()`, null-guarded
(design §4.3):

```java
mousePressed(event):
    if (event.getButton() != MouseEvent.BUTTON1) return;
    if (event.getClickCount() == 2) { sidebarGestureActive = false; resetSidebarWidth(); return; }
    sidebarGestureActive = true;

mouseReleased(event):
    if (event.getButton() != MouseEvent.BUTTON1 || !sidebarGestureActive) return;
    sidebarGestureActive = false;
    commitSidebarWidth(splitPane.getDividerLocation());
    runPendingSidebarApply();
```

Split-pane `FocusListener`:

```java
focusLost(event):
    if (sidebarGestureActive) {                  // lost release: recover, do not commit half a drag
        sidebarGestureActive = false;
        runPendingSidebarApply();
        return;
    }
    commitSidebarWidth(splitPane.getDividerLocation());   // keyboard adjustment commit
    runPendingSidebarApply();
```

Split-pane `PropertyChangeListener` on `JSplitPane.DIVIDER_LOCATION_PROPERTY` (the single re-clamp
place for programmatic and keyboard moves):

```java
propertyChanged(event):
    if (sidebarApplying) return;                          // our own move
    final int splitWidth = splitWidth();
    final int d = effectiveDividerWidth();
    final Insets insets = splitInsets();
    final int location = splitPane.getDividerLocation();
    if (sidebar.isCollapsed()) {                          // collapsed hold (§4.3)
        if (location != MapSidebarLayout.RAIL_WIDTH) {
            runWithSidebarGuard(() -> splitPane.setDividerLocation(MapSidebarLayout.RAIL_WIDTH));
        }
        return;
    }
    if (sidebarGestureActive) return;                     // L&F owns the range during a drag
    if (MapSidebarLayout.effectiveMinimum(splitWidth, d, insets) < MapSidebarLayout.MIN_WIDTH) return;
    final int clamped = MapSidebarLayout.clampWidth(location, splitWidth, d, insets);
    if (clamped != location) {
        runWithSidebarGuard(() -> splitPane.setDividerLocation(clamped));
    }
```

Invariants:

- `sidebarGestureActive` is set only on a single-click divider press and cleared on release, on a
  double-click press, on collapse, and on split-pane focus loss. While it is set,
  `applySidebarSettings` is deferred (step 2) and the property listener does not re-clamp.
- The property listener and the collapsed hold **must not** write `appliedSidebarWidth`; only
  `applySidebarSettings` writes it. This is what keeps a keyboard adjustment committable at focus
  loss while a programmatic apply still refreshes the baseline (design §4.3; §9 S4).
- Both commit triggers read the live divider location and commit only when the clamped value
  differs from `appliedSidebarWidth`; neither the stored value nor an L&F layout push is the
  baseline.
- `runPendingSidebarApply()` runs `applySidebarSettings(currentPresentation.displaySettings())`
  when `sidebarApplyPending` is set, after the commit trigger has finished.
- Programmatic divider moves (step 9 and the hold) are made under the guard and never commit.
- Neither trigger commits while collapsed or in the squeezed regime; the double-click path is a
  no-op there too (§4.2, §4.3).
- A gesture that ends where it started produces no command: the baseline comparison is false or,
  redundantly, `SetDisplaySettingsCommand` no-ops on equal settings
  (`WorkspaceCommands.java:474-476`, class `:464-479`).

#### 3.9.6 Initial layout

The model constructor calls `applyPresentation(currentPresentation)` (`:507`) before `pack()`
(`:147`), when the split pane has no width; a width applied then would clamp to
`maximumWidth(0) == MIN_WIDTH` (180), not the stored width. `applySidebarSettings` therefore
defers while the split width is not positive (step 3) and the split pane's
`ComponentListener.componentResized` performs the first real application (it also refreshes
`canvasMinimumWidth` and re-applies the effective width on every later resize):

```java
componentResized(event):
    applySidebarSettings(currentPresentation.displaySettings());
```

The first application with a positive width shows the stored width, not the minimum (design §6.5).

#### 3.9.7 Map rows and the rail badge

`updateMapRows(...)` (`:1171-1209`) counts the rows it hands to `mapList.setRows(rows)` (`:1207`):

```java
int activeCount = 0;
for (final MapListPanel.MapRow row : rows) {
    if (row.partition() == MapPartition.ACTIVE) {
        activeCount++;
    }
}
mapList.setRows(rows);
sidebar.setActiveMapCount(activeCount);
```

The count is the number of `ACTIVE` rows of the last `setRows(...)` call, i.e. the same number
`activeHeader` shows as `ACTIVE (n)` (`MapListPanel.java:333`). Loading, retryable, missing and
read-only rows all count because the partition is chosen by availability alone
(`GraphWorkspaceWindow.java:1200-1201`). The badge updates on every row refresh, hidden or not.

#### 3.9.8 Read-only workspaces and the session overlay

- `setReadOnlyOnEdt` (`:992-1007`) additionally calls `sidebar.setReadOnly(value)` (which forwards
  to `mapList.setReadOnly`), and when the binding becomes writable (`!value`) clears the overlay:
  `sessionSidebarWidth = null; sessionSidebarHidden = null;` so stored values win again.
- Every user entry point writes the overlay before committing; the commit is issued **only when
  `!readOnly`**. A read-only workspace therefore emits zero `GraphCommands.Display` commands while
  the controls stay enabled (§3.9.4, §4.5).
- `applySidebarSettings` prefers the overlay only when `readOnly` (§3.9.3 step 1), so a canvas
  refresh or window resize cannot revert a session interaction; when the binding is writable the
  overlay is ignored and the stored values win.
- `View → Maps sidebar`, the heading chevron and the rail chevron stay enabled in read-only
  sessions; the badge keeps updating.

### 3.10 Resources and assets

Translation keys — add to `freeplane/src/viewer/resources/translations/Resources_en.properties`
(ISO-8859-1, ASCII; the English base bundle is the fallback for every other locale, exactly as
the existing `graph_workspace.*` keys):

| Key | English value |
| --- | --- |
| `graph_workspace.action.maps_sidebar` | `Maps sidebar` |
| `graph_workspace.map_list.collapse` | `Hide maps sidebar` |
| `graph_workspace.map_list.expand` | `Show maps sidebar` |
| `graph_workspace.map_list.rail_label` | `MAPS` |
| `graph_workspace.map_list.rail_count` | `{0} active maps` |

- `graph_workspace.action.maps_sidebar` is the `View → Maps sidebar` item text;
  `collapse`/`expand` are the chevron text fallbacks, tooltips and accessible names;
  `rail_label` is the rotated rail label; `rail_count` is the badge tooltip and is formatted with
exactly one `Integer` argument
  (`TextUtils.format("graph_workspace.map_list.rail_count", Integer.valueOf(count))`). The badge
  paints only the numeral, and the wording deliberately has no plural handling, matching the
  shipped `graph_workspace.map_list.node_count` (`{0} nodes`, `MapListPanel.java:592-593`,
  `Resources_en.properties:899`), which already renders `1 nodes`/`0 nodes`.
- All five values are ASCII (no escapes); `gradle format_translation` owns the final key order.

New assets in `freeplane_plugin_graph/src/main/resources/images/`:

| File | Required content |
| --- | --- |
| `MapSidebarCollapse.svg` | Left-pointing chevron; root `<svg>` with a `viewBox`; monochrome `#333` (a form matched by the accent replacement rules in `freeplane/src/viewer/resources/freeplane.properties:744-750`); no other colour |
| `MapSidebarExpand.svg` | Right-pointing chevron; same rules |

Both are resolved with `?useAccentColor=true`, exactly like
`freeplane/src/viewer/resources/images/undo.svg`. The mockup generator is not part of the build
and is not modified.

## 4. Regimes and behaviour

### 4.1 The three regimes (exact)

All conditions use the live geometry of §3.2: `splitWidth = splitPane.getWidth()`,
`d = effectiveDividerWidth()`, `insets = splitInsets()`, `U = effectiveMinimum(splitWidth, d, insets)`,
`maxW = maximumWidth(splitWidth)`.

| Regime | Condition (evaluated in the order Squeezed → Pinned → Normal) | L&F interactive range | Width commits |
| --- | --- | --- | --- |
| Normal | `U == MIN_WIDTH` and `maxW > U` (last, when neither below applies) | `[U + insets.left, maxW]` (the exact ceiling holds when the arithmetic is fed `d`) | allowed |
| Pinned | `U == MIN_WIDTH` and `maxW == U` (the range collapses at `MIN_WIDTH`) | reported range `[U + insets.left, U + insets.left]` (the L&F wraps the physical maximum in `max(minimumDividerLocation, …)`); the `DragController`'s internal `(minX, maxX)` is `(U + insets.left, U)`, so `maxX < minX` disables dragging (the insets-shifted pinned band below) | allowed; they can only restate `MIN_WIDTH` |
| Squeezed | `U < MIN_WIDTH` (takes precedence) | collapses onto `U` (plus the L&F minimum offset); the sidebar shrinks with the window | **suppressed** — no commit path may persist anything |

The three conditions are mutually exclusive and exhaustive under that evaluation order: the
squeezed test runs first even though `U < MIN_WIDTH` implies `maxW >= MIN_WIDTH > U`; otherwise
`U == MIN_WIDTH` (because `effectiveMinimum` never exceeds `MIN_WIDTH`) and the `maxW == U` /
`maxW > U` split is exact. The commit and re-clamp paths test `U < MIN_WIDTH` explicitly, so the
ordering itself never changes behaviour.

Verified evidence for the formulas (design review round 5, FlatLaf 3.7.2, zero insets, effective
divider width 6/12/18 at `uiScale` 1/2/3; the automated tests run under Metal):

| `splitWidth` | `U` | `maxW` | Regime | L&F reachable range | committed by a no-op gesture |
| --- | --- | --- | --- | --- | --- |
| 100 | 94 | 180 | squeezed | `[94, 94]` | none |
| 150 | 144 | 180 | squeezed | `[144, 144]` | none |
| 185 | 179 | 180 | squeezed | `[179, 179]` | none |
| 186 | 180 | 180 | pinned | `[180, 180]` | at most 180 |
| 362 | 180 | 181 | normal | `[180, 181]` | only a change |
| 1000 | 180 | 500 | normal | `[180, 500]` | only a change |

With Metal's insets `(1, 1, 1, 1)` the reported lower bound is `U + 1`, and the `DragController`
is disabled in the insets-shifted pinned band (`188 <= splitWidth <= 361`) because its internal
`maxX` is one below `minX`; this is the documented L&F degradation behind the design's "roughly
186/188 … 361" wording. The clamp arithmetic itself is regime-independent.

### 4.2 Commit-suppression rule

A width commit is suppressed when **any** of the following holds; the stored value is preserved
and nothing is persisted:

- the sidebar is collapsed (`sidebar.isCollapsed()`);
- the squeezed regime holds (`U < MIN_WIDTH`);
- the clamped location equals `appliedSidebarWidth`;
- the workspace is read-only (the overlay is written; no command);
- there is no gesture/adjustment in flight for the trigger in question
  (`sidebarGestureActive` at mouse release; a lost release at focus loss).

A clamp never persists a value outside `[U, max(U, maxW)]`: `commitSidebarWidth` and
`resetSidebarWidth` both clamp first — the reset clamps `DEFAULT_WIDTH` into the current range,
so at a ceiling below 264 it commits the ceiling, not 264 — and no gesture can persist a value
outside the range. A valid but out-of-range **stored** value is displayed clamped and preserved
until a gesture changes the displayed width.

### 4.3 Collapsed-state hold

While collapsed:

- the divider is held at `RAIL_WIDTH`: every location change our own code did not initiate
  (the property listener, `sidebarApplying == false`) is reset to `RAIL_WIDTH` under the guard —
  invisible, because `dividerSize` is `0`;
- `splitPane.setDividerSize(0)` and `splitPane.setEnabled(false)` (the divider cannot be grabbed
  and no resize cursor appears; `BasicSplitPaneDivider.MouseHandler.mousePressed` gates on
  `splitPane.isEnabled()`);
- both commit triggers and the double-click path return early, so a drag that began before the
  collapse cannot widen the rail or commit a width;
- `setSidebarCollapsed` clears `sidebarGestureActive` first, terminating a straddling drag;
- `appliedSidebarWidth` is `RAIL_WIDTH` (set by the apply), so the baseline never reports an
  expanded width while collapsed.

Expanding restores the effective (stored/session) width clamped to the current geometry, because
`setSidebarCollapsed(false)` writes the overlay and calls `applySidebarSettings`, which sets
`dividerSize` back to `6`, enables the split pane and places the divider.

### 4.4 Error and edge behaviour (per failure mode)

| Case | Required behaviour |
| --- | --- |
| Malformed `map-sidebar-width` (empty / non-integer / `<= 0`) | `WorkspaceXmlCodec.read` throws `WorkspaceFormatException`; the workspace is rejected like any other known-attribute violation. No partial document is used. |
| Malformed `map-sidebar-hidden` | `booleanValue(...)` throws `WorkspaceFormatException` ("must be true or false"). |
| Valid but out-of-range stored width | displayed clamped (`clampWidth`); preserved and **not rewritten** until a gesture changes the displayed width; a no-op press/release and a focus loss are no-ops. |
| Squeezed regime | sidebar shrinks with the window; divider immobile; commit triggers and double-click are no-ops; stored width preserved; the re-clamp has a fixed point (zero listener re-sets per layout pass) so there is no validation loop. |
| Pinned regime | divider immobile; a commit can only restate `MIN_WIDTH`. |
| No-op gesture (press/release without movement, focus loss without a keyboard adjustment, focus loss after a programmatic apply) | no command; the baseline is `appliedSidebarWidth`, so a preserved out-of-range stored width survives. |
| Window resized while expanded | `canvasMinimumWidth` refreshed and the effective width re-applied under the guard; no mutation. |
| Window resized while collapsed | divider held at `RAIL_WIDTH`; still disabled; no mutation. |
| Drag release | exactly one `DisplaySettings` command, or none when the clamped value equals the displayed width. |
| Double-click reset (expanded, no gesture in flight, not squeezed) | exactly one `DisplaySettings` command carrying `clampWidth(DEFAULT_WIDTH, splitWidth, d, insets)` — 264 while the ceiling is at least 264, the ceiling otherwise; a no-op while collapsed, squeezed, or when the clamped value equals `appliedSidebarWidth`. |
| Keyboard adjustment (L&F arrows, `HOME`/`END`) | clamped on every change (never deferred); committed once when the split pane loses focus; `HOME`/`END` output is re-clamped immediately and never displayed or persisted out of range. |
| Lost release (window deactivation mid-drag) | on the next divider press or split-pane focus loss the flag is cleared first; a focus-loss recovery does not commit the abandoned drag and runs the pending apply. |
| Undo/redo of any display setting | the apply path restores the whole snapshot (width, hidden, other fields) and syncs the `View` item. |
| Hide then reopen the workspace | collapsed state restored from `map-sidebar-hidden`; divider locked at the rail; stored width restored on expand. |
| Read-only workspace | controls usable, zero commands, session overlay keeps the interaction visible across refreshes and resizes; reopen restores the file's values. |
| Read-only workspace + window resize | session width re-applied clamped; still zero commands. |
| Zero active maps while collapsed | badge text empty (no `0` pill); tooltip still reports the count. |
| Collapse requested while a drag is in flight | gesture flag cleared, divider held at `RAIL_WIDTH`; both commit triggers return early; only the hidden commit is emitted. |
| Canvas refresh arrives mid-drag | apply deferred until the gesture ends; the divider does not snap; the pending apply runs after the release commit. |
| Initial open | the first application waits for a positive split width, so the stored width (not the minimum clamp) is shown. |
| Workspace opened in a narrower window | the stored width is clamped on apply and is **not** persisted; a later larger window shows the stored width again. |
| Foreign unknown attributes/elements inside `display-settings` | preserved and re-emitted unchanged; the two new attributes are never duplicated into unknown XML. |
| Missing icon asset for a chevron | the chevron keeps its localized text fallback, no tooltip/accessible name is set, and the click still toggles the sidebar. |

### 4.5 Read-only session-overlay precedence

Precedence used by `applySidebarSettings` (design §5.3):

1. `readOnly == true` and `sessionSidebarHidden != null` / `sessionSidebarWidth != null`:
   the overlay value wins for the corresponding field.
2. `readOnly == false`: the stored value from the current presentation wins; the overlay is
   written by gestures but ignored, and it is cleared when the binding becomes writable.
3. The two fields are independent; a gesture that changes only the width leaves the stored hidden
   flag authoritative, and vice versa.

## 5. Test specification

### 5.1 Conventions and fixtures

- JUnit 4 + AssertJ, `*Should` names, `freeplane_plugin_graph/src/test/java`. All tests run
  headless; Swing components are created without a displayable frame.
- The automated Swing tests run under the JVM default `MetalLookAndFeel`
  (`BasicSplitPaneUI`/`MetalLookAndFeel`); FlatLaf is not on the plugin test runtime classpath and
  must not be installed by a test. Assertions that depend on Metal's insets are labelled as such.
- `GraphWorkspaceWindowModelShould` fixture additions (existing `Fixture`, `EdtResources`,
  `RESOURCES`, `runOnEdt` patterns at `:2667-2787`):
  - `stubIcon(path, icon)` (`:2691-2694`) before `model()` for chevron icon resolution.
  - A sidebar round-trip answer for tests that assert committed geometry, installed before
    `model()`:
    ```java
    final DisplaySettings[] committed = { presentation.displaySettings() };
    when(fixture.handle.execute(any(GraphCommand.class))).thenAnswer(invocation -> {
        final GraphCommand command = invocation.getArgument(0);
        if (command instanceof GraphCommands.Display) {
            committed[0] = ((GraphCommands.Display) command).settings();
        }
        return null;
    });
    when(fixture.binding.currentPresentation())
        .thenAnswer(invocation -> presentation(committed[0], ACTIVE_ID));
    ```
    This makes `executeCommand`'s `refreshPresentation()` behave like the production store (the
    controller re-reads the document synchronously) and is required for the geometry assertions.
  - All sidebar stimulus **in this suite** (sizing/`doLayout`, `MouseEvent`/`FocusEvent` dispatch,
    `doClick`) must run inside `GraphWorkspaceWindow.runOnEdt(...)` because `EdtResources`
    installs the `TextUtils` and `ResourceController` Mockito static mocks on the EDT and Mockito
    static mocks are thread-local (the existing tests use the same pattern, e.g. `:1633`). The
    rule is scoped to `GraphWorkspaceWindowModelShould`; the two unit suites that install their
    mocks on the test thread are listed below.
  - EDT flush rule for resize-dependent cases: `setSize`/`doLayout` only post
    `COMPONENT_RESIZED` (`java.awt.Component.notifyNewBounds` →
    `Toolkit.getEventQueue().postEvent`), so a case that sizes a component and then asserts state
    computed by a `ComponentListener` must flush the EDT after sizing. In
    `GraphWorkspaceWindowModelShould`, run the sizing and the assertions in separate
    `GraphWorkspaceWindow.runOnEdt(...)` calls (each is a FIFO `invokeAndWait` barrier), or
    explicitly dispatch `new ComponentEvent(splitPane, ComponentEvent.COMPONENT_RESIZED)` on the
    EDT. In `MapListPanelShould` the resize stays on the test thread and empty
    `runOnEdt(() -> { })` calls are the barrier (§5.5 case 3), so the `@Before` mocks are never
    needed on the EDT. This applies to §5.5 case 3 and §5.6 cases 2, 6, 7 and 10, and to any other
    case that lays the split pane out before asserting listener-driven state.
  - Helper replacements for the navigation sites. `graphArea` has exactly two direct children —
    the split pane in `CENTER` and `settingsPanel` in `EAST` — and the sidebar panel is the split
    pane's left component, so no helper may navigate by an insertion index (§3.9.1, review
    round 2 B2-1):
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
    Existing sites to update: the old `(JPanel) model.content().getComponent(1)` plus
    `graphArea.getComponent(1)` pairs at `:232-233`, `:774` and `:790`, and the private
    `graphScrollPane(model)` helper at `:1604`; all of them now use `splitPane(model)` /
    `canvasScrollPane(model)` (case 16). The design name
    `graph-workspace-maps-sidebar-menu-item` is not the `graph-workspace-menu-item-` convention
    of the existing `menuItem(model, name)` helper (`GraphWorkspaceWindowModelShould.java:2576-2587`),
    so `menuItem(model, "maps-sidebar")` cannot find it (B3); `menuItemByName` matches the exact
    design name instead.
- `MapSidebarPanelShould` / `MapListPanelShould` construct their components directly on the test
  thread (static `TextUtils` mocks in `@Before`, as `MapListPanelShould.java:33-49`), with a
  `MapListPanel` built exactly as `MapListPanelShould.java:71-72`. Their stimulus and assertions
  stay on the test thread: the `@Before` mocks are not visible on the EDT, so running this
  stimulus through `GraphWorkspaceWindow.runOnEdt(...)` would make `TextUtils`-dependent
  assertions (e.g. §5.4 case 4's tooltip) unobservable (review round 2, m2-4). `MapListPanelShould`
  case 3 keeps its `runOnEdt(...)` calls purely as FIFO EDT barriers for the resize callback; the
  runnable posted there must not touch the mocked statics (it only resizes or measures components,
  and `syncListWidths` formats no text).
- `MapSidebarSplitClampShould` needs no mocks: plain `JPanel`s and a real `JSplitPane`.

### 5.2 `MapSidebarLayoutShould` (new, pure arithmetic)

File: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/MapSidebarLayoutShould.java`.

1. `exposesTheApprovedWidthConstants` — `DEFAULT_WIDTH == 264`,
   `DEFAULT_WIDTH == DisplaySettings.DEFAULT_MAP_SIDEBAR_WIDTH`, `MIN_WIDTH == 180`,
   `RAIL_WIDTH == 26`.
2. `computesMaximumWidthAsHalfTheSplitWidthWithTheMinimumFloor` — `maximumWidth(360) == 180`,
   `maximumWidth(400) == 200`, `maximumWidth(800) == 400`, `maximumWidth(1000) == 500`,
   `maximumWidth(0) == 180`.
3. `computesTheEffectiveMinimumFromTheLayoutGeometry` — with `new Insets(0, 0, 0, 0)`:
   `effectiveMinimum(1000, 6)` = 180, `362` → 180, `186` → 180, `185` → 179, `150` → 144,
   `100` → 94, `6` → 0, `0` → 0; with `new Insets(1, 1, 1, 1)`: `150` → 142, `186` → 178.
4. `clampsWidthsIntoTheEffectiveRange` — at `splitWidth = 1000`, `d = 6`, zero insets:
   `clampWidth(100, …) == 180`, `clampWidth(180, …) == 180`, `clampWidth(500, …) == 500`,
   `clampWidth(501, …) == 500`, `clampWidth(1000, …) == 500`; at `splitWidth = 150`:
   `clampWidth(264, …) == 180` (the ceiling, not the squeezed minimum) and
   `clampWidth(0, …) == 144` (below the lower bound it clamps up to
   `effectiveMinimum(150, 6)`; the 144 value itself is pinned by case 3 and §4.1).
5. `keepsTheClampRangeNonEmptyAtEverySplitWidth` — for `splitWidth` in `0..1500`: `lower =
   effectiveMinimum(...) <= upper = Math.max(lower, maximumWidth(...))`, and `clampWidth(lower) ==
   lower`, `clampWidth(upper) == upper` (the re-clamp fixed point).
6. `keepsCanvasMinimumWidthNonNegativeAndMonotonic` — `canvasMinimumWidth >= 0` for
   `splitWidth` in `0..1500`, and non-decreasing over that range.
7. `computesTheCanvasMinimumFromTheLayoutGeometry` — at `splitWidth = 1000`, `d = 6`, zero
   insets: `canvasMinimumWidth(...) == 494`; at `362`: `175`; at `150`: `0`; at `100`: `0`
   (hand-computed from §3.2; the interactive-ceiling claim is §5.3 case 2, which reads the real
   L&F).

Falsifiers: a wrong floor in `maximumWidth` fails 2; a `MIN_WIDTH`-fixed `effectiveMinimum` fails
3; using the logical divider width in the squeeze tests (e.g. `effectiveMinimum(150, 0)` = 150)
fails 3; an inverted clamp fails 4/5; a wrong subtrahend in `canvasMinimumWidth` fails 7.

### 5.3 `MapSidebarSplitClampShould` (new, headless Swing, Metal)

File: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/MapSidebarSplitClampShould.java`.

Fixture: a private `ClampHarness` that builds a real `JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
leftPanel, rightPanel)` with `setContinuousLayout(true)`, `setDividerSize(6)`,
`setResizeWeight(0.0)`; on every `layoutAt(width)` it sets the left minimum to
`MapSidebarLayout.effectiveMinimum(width, d, insets)` and the right minimum to
`MapSidebarLayout.canvasMinimumWidth(width, d, insets)` where `d` is read from
`((BasicSplitPaneUI) splitPane.getUI()).getDivider().getDividerSize()` and `insets` from
`splitPane.getInsets()`; sets `splitPane.setSize(width, 300)`, calls `doLayout()` and returns.
`layoutAt` and the L&F location queries are synchronous and read the L&F directly, so this class
needs no EDT flush. The harness also installs the §3.9.5 property listener **rule** (a local
emulation, since the production listener is private to `GraphWorkspaceWindowModel`) and records
`commits`, `reclampResets` and the raw release location each commit was computed from;
`storedWidth` is the harness's persisted value (default 264).

1. `reportsMinimumDividerLocationAtOrAboveTheSidebarMinimum` — at `splitWidth` 400, 800 and
   1000: `((BasicSplitPaneUI) splitPane.getUI()).getMinimumDividerLocation() >= MapSidebarLayout.MIN_WIDTH`.
2. `reportsTheExactInteractiveCeilingAsMaximumWidth` — at 400/800/1000:
   `getMaximumDividerLocation() == MapSidebarLayout.maximumWidth(splitWidth)` with `d` in the
   arithmetic; at 362 → 181.
3. `reclampsAProgrammaticEndMoveToTheAllowedMaximum` — `splitPane.setDividerLocation(splitWidth - 1)`
   (the L&F's `END` value under Metal is `splitWidth - insets.right`) is followed by the harness
   listener, and `splitPane.getDividerLocation()` equals `maximumWidth(splitWidth)`; the harness
   records no commit from the programmatic move.
4. `commitsOneClampedWidthForOneDividerDrag` — at `splitWidth = 1000`, press/drag/release on
   `((BasicSplitPaneUI) splitPane.getUI()).getDivider()`; the harness records the release location
   it dispatched (not `splitPane.getDividerLocation()`, which the L&F's own divider `MouseHandler`
   may already have adjusted before the harness listener runs), and a drag whose recorded release
   location is
   `999` records exactly one commit with the literal committed value `500`, and on a fresh
   harness a drag released at `250` commits exactly `250`. The expectations are literals
   hand-computed from §3.2 and the recorded raw release location, not `clampWidth(...)`, so the
   production helper cannot define them.
5. `commitsNothingForAPressAndReleaseWithoutMovement` — press/release at the current location:
   `commits == 0` and `storedWidth` unchanged.
6. `holdsTheSqueezedRegimeWithoutCommits` — at `splitWidth` 100 and 150: after layout, a drag
   attempt and a no-op press/release produce `commits == 0`, `storedWidth == 264`, and one further
   `doLayout()` pass followed by one more emits no listener re-set (`reclampResets` unchanged),
   proving the fixed point.

Falsifiers: feeding the logical `6` instead of `d` fails 2 at scale > 1 (and is why the harness
reads the divider component); a wrong clamp constant fails 4; the round-4 baseline rule
(baseline = clamped applied value without the squeezed early return) fails 6 by committing the
settled squeezed value; a missing property listener fails 3.

### 5.4 `MapSidebarPanelShould` (new, headless Swing)

File: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/MapSidebarPanelShould.java`.

Fixture: `MapListPanel` built as in `MapListPanelShould.java:71-72`; a recording
`CollapsedListener`; the `MapSidebarPanel` constructed directly.

1. `showsOnlyOneOfRailAndMapList` — immediately after construction (no `setCollapsed` call):
   `mapList().isVisible()` and `!rail().isVisible()`; after `setCollapsed(true)`:
   `rail().isVisible()` and `!mapList().isVisible()`; back again.
2. `flipsThePreferredAndMinimumWidthsWithTheCollapsedFlag` — immediately after construction:
   `getPreferredSize().width == 264`, `getMinimumSize().width == 180`; collapsed: both `26`.
3. `notifiesTheCollapseListenerOncePerRealChange` — the recording listener is still empty after
   construction (the constructor must not notify); `setCollapsed(true)` fires it once with
   `true`; a repeated `setCollapsed(true)` fires nothing; `setCollapsed(false)` fires once with
   `false`.
4. `updatesTheRailBadgeFromTheActiveMapCount` — `setActiveMapCount(3)`:
   `rail().countBadge().getText() == "3"` and tooltip
   `graph_workspace.map_list.rail_count[3]` (the fixture's `TextUtils.format` mock renders
   `key[args]`); `setActiveMapCount(0)`: text `""` and tooltip
   `graph_workspace.map_list.rail_count[0]`; a repeated call with the same value changes neither.
5. `forwardsReadOnlyToTheMapListWithoutChangingVisibility` — `setReadOnly(true)`:
   `mapList().isReadOnly()` and visibility unchanged; `setReadOnly(false)` restores it.

### 5.5 `MapListPanelShould` (updated)

File: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/MapListPanelShould.java`
(existing six cases unchanged).

1. `hostsTheHeadingLabelAndTheNamedCollapseButton` — the heading row is located by name
   (`graph-workspace-map-list-heading-row`, via the `componentByName(...)` helper) rather than by
   index; it contains the
   `graph-workspace-map-list-heading` label and `collapseButton()` named
   `graph-workspace-map-sidebar-collapse`; with a stubbed
   `/images/MapSidebarCollapse.svg?useAccentColor=true` icon the button is icon-only and carries
   the tooltip/accessible name `graph_workspace.map_list.collapse`, without a stub the text is
   `graph_workspace.map_list.collapse`.
2. `tracksTheListWidthWithThePanelContentWidthOnSetRows` — `setSize(400, 600); setRows(rows);`
   then `activeList().getPreferredSize().width == 400 - getInsets().left - getInsets().right` and
   the height is `rows.size() * MapListPanel.ROW_HEIGHT`; the same for `inactiveList()`.
3. `tracksTheListWidthOnAPanelResizeWithoutSetRows` — after `setRows(...)` at 264, `setSize(500, 600)`
   on the test thread and flush the posted `COMPONENT_RESIZED` with two empty
   `GraphWorkspaceWindow.runOnEdt(() -> { })` barriers (§5.1 EDT flush; the barriers carry no
   component work, so the `@Before` mocks are not needed on the EDT), then assert on the test
   thread that `activeList().getPreferredSize().width` equals the new content width without
   another `setRows(...)`.
4. `derivesTheRowRendererCellWidthFromTheListWidth` — size the list first with
   `activeList().setSize(400, 300)`; obtain the renderer through `activeList().getCellRenderer()`,
   call `getListCellRendererComponent(activeList(), row, 0, false, false)` and assert the returned
   component's preferred width equals the literal `400` and its preferred height equals
   `MapListPanel.ROW_HEIGHT` (regression for `PANEL_WIDTH - 24` = 240; a zero-width list is not a
   valid pass condition).

### 5.6 `GraphWorkspaceWindowModelShould` (updated)

File: `GraphWorkspaceWindowModelShould.java`. Named cases (all using the §5.1 fixture additions
and Metal, with the §5.1 EDT flush wherever a case lays the split pane out before asserting
listener-driven state):

1. `hostsTheMapSidebarInASplitPaneInsideTheGraphArea` — `content()` has 3 children and the panel
   named `graph-workspace-graph-area` is one of them; `graphArea` has exactly 2 direct children:
   the named split pane (`graph-workspace-split`) and `model.settingsPanel()` (the `EAST` child
   per §3.9.1), and the sidebar panel is **not** a direct child of `graphArea`; the split pane's
   left component is the sidebar panel (`graph-workspace-map-sidebar`, whose `mapList()` is
   `model.mapList()`) and its right component is the scroll pane named
   `graph-workspace-scroll-pane` whose view is `model.canvas()`. Navigation uses
   `graphArea(model)`, `splitPane(model)`, `sidebarPanel(model)` and `canvasScrollPane(model)`
   (§5.1); no child index is asserted. Rewrites the `:232-233` assertions in place (case 16).
2. `commitsOneClampedSidebarWidthPerDividerDrag` — layout the split pane at 1000 with the default
   presentation (264) via the component listener in one `runOnEdt` block and assert in a second
   (§5.1 EDT flush); clear the handle; dispatch `MOUSE_PRESSED` on the divider, move the raw
   divider location to the L&F `END` value (`splitWidth - insets.right` = 999 under Metal; the
   property listener is suppressed while the gesture is active, so the raw value survives), and
   dispatch `MOUSE_RELEASED` once (§5.1 stimulus rules; the harness records the release location
   it dispatched, because the L&F's own divider `MouseHandler` may already have adjusted
   `splitPane.getDividerLocation()`); exactly one `GraphCommands.Display` is
   executed and its `settings().mapSidebarWidth()` is the independent literal `500`; on a second
   fixture the same sequence with the raw location `250` commits the independent literal `250`.
   The expectations are literals derived from §3.2 and never `clampWidth(...)` calls (§5.3 case 4
   pins the same two literals at the harness level). A press/release without movement on a fresh
   fixture executes no `Display` command.
3. `preservesSidebarFieldsWhenADisplayCheckboxIsToggled` — fixture presentation with width 400 and
   hidden `true`; `settingsPanel().showArrowheads().doClick()`; exactly one `Display` command and
   its settings keep `mapSidebarWidth() == 400` and `mapSidebarHidden() == true` while flipping
   `showArrowheads()`. This is the `WorkspaceSettingsPanel` pass-through regression (§3.8).
4. `resetsTheSidebarWidthOnDividerDoubleClick` — on a fixture laid out at 1000, after a drag to
   the literal `400`, a double-click on the divider (`MOUSE_PRESSED` with `clickCount == 2`)
   yields exactly one `Display` command with `mapSidebarWidth() == 264` (the literal default,
   inside the 1000 px range); on a fixture laid out at 400 (whose ceiling is the literal `200`),
   after a drag to the literal `190`, the double-click commits the literal `200`, i.e. the reset commits
   the **clamped** default (§3.9.4, §4.2, M2-2); while collapsed or squeezed the double-click
   executes no command.
5. `keepsTheViewMenuSidebarItemInSyncWithEveryToggle` — `mapsSidebarMenuItem(model)` (looked up
   by the exact design name; §5.1, B3) is a checked `JCheckBoxMenuItem`; from expanded, a
   heading-chevron click collapses (one `Display` command, `mapSidebarHidden() == true`); the
   rail-button click then expands (one command, `false`); the `View` item click then collapses
   (one command, `true`); after every step
   `isSelected() == !sidebarPanel(model).isCollapsed()`; a stored collapse (initial presentation
   hidden `true`) leaves the item unchecked.
6. `appliesStoredSidebarStateWithoutEmittingACommand` — lay the split pane out first (§5.1 EDT
   flush); after clearing the handle, a changed presentation with width 500/hidden `false`
   delivered through `acceptCanvasState(...)` (and `model.execute(GraphCommands.display(changed))`
   with the `:1223-1240` pattern) moves the divider and the collapsed flag under the guard and
   executes no extra `Display` command; the `View` item is re-synced.
7. `defersSidebarApplicationWhileTheDividerGestureIsActive` — lay the split pane out first (§5.1
   EDT flush); press, then deliver a changed presentation via `acceptCanvasState(...)`; the
   divider must not move and no command is emitted; on release, one `Display` command with the
   dragged width is emitted and the deferred apply runs once (the round-trip answer makes the
   final divider the committed width).
8. `commitsNothingFromAGestureThatEndsWhileCollapsed` — press, then `setSidebarCollapsed(true)`
   through the `View` item; assert exactly one `Display` command (hidden `true`), the divider is
   held at 26, `dividerSize == 0` and `splitPane.isEnabled() == false`; the release then emits no
   width command.
9. `keepsReadOnlySidebarInteractionsSessionOnly` — read-only fixture; drag to a different width
   and toggle the `View` item: zero `Display` commands; a `setSize`-driven resize re-applies the
   session width (not the stored 264) and still emits zero commands; the chevrons and the `View`
   item stay enabled; `model.setReadOnly(false)` followed by an apply returns to the stored values.
10. `showsTheStoredSidebarWidthOnOpenInsteadOfTheMinimum` — presentation width 400; before any
    size the divider is deferred; after the split pane is laid out at 1000 in one `runOnEdt`
    block and observed in a second (§5.1 EDT flush) the divider location is 400 (not the
    effective minimum).
11. `preservesAnOutOfRangeStoredSidebarWidthAcrossNoOpGestures` — stored width 1000, split pane
    laid out at 600: the divider is clamped to the literal `300` and
    `model.appliedSidebarWidth()` (the package-private accessor of §3.9.2) is 300; a
    press/release without movement and a `FOCUS_LOST` event execute no command and the
    presentation's stored `mapSidebarWidth()` stays 1000; at `splitWidth` 150 with stored 264
    a press/release and a `FOCUS_LOST` likewise execute no command and leave the stored value
    unchanged.
12. `commitsTheKeyboardAdjustedSidebarWidthOnceOnFocusLoss` — layout at 1000; dispatch
   `FocusEvent.FOCUS_GAINED` to the split pane; invoke
   `splitPane.getActionMap().get("selectMax").actionPerformed(...)` (the L&F action, which sets
   `splitWidth - insets.right`); assert the property listener re-clamped the divider to
   `maximumWidth(1000) == 500`; dispatch `FocusEvent.FOCUS_LOST`; exactly one `Display` command
   with `mapSidebarWidth() == 500`.
13. `mapsTheActivePartitionCountToTheRailBadge` — one `AVAILABLE` and one `INACTIVE` registration:
    badge text `"1"` and tooltip `graph_workspace.map_list.rail_count[1]`; all-inactive
    registrations: text `""` and tooltip `graph_workspace.map_list.rail_count[0]`; after a hidden
    toggle the badge still updates on the next `acceptCanvasState(...)`/`updateMapRows` path.
14. `keepsTheMapsMenuActionsEnabledWhileCollapsed` — collapse (collapsed commit applied through the
    round-trip answer) and assert the `Maps` menu items' enablement (`add-map`, `deactivate-map`,
    …) equals the expanded-session values for the same selection, and the `View` sidebar item stays
    enabled.
15. `restoresTheLastExpandedWidthOnExpand` — layout at 1000 and drag/release at the raw location
    `400` (an independent literal inside the range), collapse, assert
    `sidebarPanel(model).isCollapsed()` and the rail is 26 with `dividerSize == 0`/disabled,
    expand, assert the divider location is the literal `400` (the last expanded width, never a
    `clampWidth(...)` expression) and `dividerSize == 6`/enabled.
16. Updated existing cases: `rendersVisibleShellControlsFromGraphWorkspaceResourceKeys`
    (`:196`) asserts the heading row and its named label; `composesAHeadlessModelessWorkspace…`
    (`:228-236`) is **rewritten in place** — its method name and every non-topology assertion
    stay, and its old `graphArea`/scroll-pane index block is replaced by case 1's name-based
    topology assertions, so no existing test method is deleted (§5.10, review round 2 m2-7);
    `growsTheScrollableSurfaceForVisibleWorldGeometry` (`:774`),
    `persistsExternalScrollAsTheVisibleViewportOnce` (`:790`) and the `graphScrollPane(model)`
    helper (`:1604`) use the §5.1 helpers (`splitPane(model)` / `canvasScrollPane(model)`).
17. `expandsFromCollapsedWithTheRestoredDividerWidthInOneReadOnlyApply` — read-only fixture (no
    commit round-trip follows the single apply) whose presentation is hidden `true` with width
    400; lay the split pane out at 400 in one `runOnEdt` block and observe in a second (§5.1 EDT
    flush); clear the handle; expand through the `View` item; assert zero `Display` commands, the
    divider location literal `200`, the sidebar minimum literal `180`, the canvas scroll pane
    minimum literal `193` and
    `((BasicSplitPaneUI) splitPane.getUI()).getMaximumDividerLocation()` literal `200`. The
    four literals are labelled Metal-insets-specific (`insets = (1, 1, 1, 1)`, `d = 6`):
    `U = 180`, `maxW = 200`, `clampWidth(400, …) = 200`, `canvasMinimumWidth = 400 − 6 − 1 − 200`.
    With the pre-fix hoisted read (`d` = the collapsed `0`) the canvas minimum would be `199` and
    the L&F ceiling `194`, so the case pins "read the effective divider width after
    `setDividerSize`" (review round 2, M2-1).

### 5.7 `WorkspaceXmlCodecShould` (updated)

File: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/io/WorkspaceXmlCodecShould.java`.
`fullDocument()`'s `DisplaySettings.of(...)` call (`:236`) moves to the 7-argument factory with
`264`/`false` (the fixture file
`src/test/resources/workspace/format-1-full.fpg` keeps its four old attributes so the backward
compatibility case stays honest).

1. `roundTripsNonDefaultMapSidebarAttributes` — build a document with
   `DisplaySettings.of(false, DARK, false, true, 400, true, unknownXml)`, write it, read it back,
   assert `mapSidebarWidth() == 400`, `mapSidebarHidden() == true`, and that the written XML
   contains `map-sidebar-width="400"` and `map-sidebar-hidden="true"`; a second write is
   byte-identical.
2. `defaultsAbsentMapSidebarAttributesTo264AndExpanded` — `codec().read(resource("format-1-full.fpg"))`
   yields `mapSidebarWidth() == 264`, `mapSidebarHidden() == false` and equals `fullDocument()`.
3. `writesTheMapSidebarAttributesOnEverySave` — writing a document read from a file that lacks the
   attributes produces both attributes exactly once in the output.
4. `preservesForeignDisplaySettingsAttributesAlongsideTheKnownMapSidebarAttributes` — the
   `future:display-attribute` of `format-1-full.fpg` appears exactly once in the written XML and
   remains in `document.displaySettings().unknownXml()` after a round-trip.
5. `rejectsMalformedMapSidebarAttributes` — five temp XML documents (width `""`, `"abc"`, `"0"`,
   `"-1"`; hidden `"yes"`) each fail `codec().read(...)` with `WorkspaceFormatException` and the
   messages of §3.7.
6. Existing malformed/round-trip cases remain green (the format version stays `1`, so no migration
   test changes).

### 5.8 Domain, command and history suites (updated)

- `WorkspaceDomainShould`: `defaultsTheMapSidebarPresentationState` (`defaults()` width 264, hidden
  false — extend `:220-226`); `includesTheMapSidebarFieldsInEqualityHashCodeAndToString` (two
  settings differing only in width, and two differing only in hidden, are unequal with different
  hash codes; `toString` contains both names).
- `WorkspaceCommandsShould.replaceCompleteDisplaySettingsAndTreatEqualSettingsAsNoChange`
  (`:454-468`): a width-only change applies with `graph_workspace.display.updated`, and the same
  settings again are `NO_OP` (extended with `mapSidebarWidth`/`mapSidebarHidden`).
- `WorkspaceHistoryShould`: `restoresSidebarWidthWithTheWholeDisplaySettingsSnapshot` — a
  width-only `setDisplaySettings` is undoable and the undo restores the previous width together
  with all other fields; existing call sites `:110,161,181` move to the 7-argument factory.
- `GraphCommandRouterShould:109,165`, `GraphWorkspacePresentationShould:29`,
  `GraphWorkspaceModelAcceptanceShould:184`, `GeneratedWorkspace:411`: signature updates only;
  compilation plus the existing assertions are the falsifier.
- `WorkspaceSettingsPanel` is exercised through case 3 of §5.6; `GraphWorkspaceWindowModelShould`
  also keeps `refreshesCanvasPresentationAfterADisplayCommandWithoutResettingOtherSettings`
  (`:1223-1240`) green.

### 5.9 `GraphPluginIntegrationShould` (specification-added resource assertions)

File: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphPluginIntegrationShould.java`,
following `shipsTheFourRecentWorkspaceResourceKeys` (`:444-452`) and
`shipsTheGraphToolbarAffordanceResourceKeys`:

- `shipsTheGraphMapSidebarResourceKeys` — read
  `freeplane/src/viewer/resources/translations/Resources_en.properties` and assert:
  `graph_workspace.action.maps_sidebar == "Maps sidebar"`,
  `graph_workspace.map_list.collapse == "Hide maps sidebar"`,
  `graph_workspace.map_list.expand == "Show maps sidebar"`,
  `graph_workspace.map_list.rail_label == "MAPS"`,
  `graph_workspace.map_list.rail_count == "{0} active maps"`.

Falsifier: a missing key returns `null`; a wrong value fails equality.

### 5.10 Existing suites

`gradle :freeplane_plugin_graph:test` must run the whole existing plugin suite green, including
`MapListPanelShould`'s six existing cases, `GraphWorkspaceWindowModelShould`'s existing cases and
`WorkspaceHistoryShould`/`WorkspaceCommandsShould`/`WorkspaceXmlCodecShould` after the signature
updates. No existing test method may be deleted; the four navigation assertions listed in §5.1 are
updated in place.

## 6. Verification

Use `gradle` with escalation and Java 21 from `~/.sdkman/candidates/java/21.0.8-zulu` (AGENTS.md).

### 6.1 Automated gates

1. `gradle :freeplane_plugin_graph:test` — the plugin suite is green, including the new
   `MapSidebarLayoutShould`, `MapSidebarSplitClampShould`, `MapSidebarPanelShould` classes and the
   updated `MapListPanelShould`, `GraphWorkspaceWindowModelShould`, `WorkspaceXmlCodecShould`,
   `WorkspaceDomainShould`, `WorkspaceCommandsShould`, `WorkspaceHistoryShould`,
   `GraphCommandRouterShould`, `GraphWorkspacePresentationShould`,
   `GraphWorkspaceModelAcceptanceShould`, `GeneratedWorkspace` and
   `GraphPluginIntegrationShould`.
2. `gradle :freeplane:test` — the core suite is green after the `DisplaySettings` signature
   change (the `freeplane` module does not use `DisplaySettings`, so this is a regression gate for
   the resource edit and the shared build).
3. `gradle format_translation` after editing
   `freeplane/src/viewer/resources/translations/Resources_en.properties`; then
   `cd freeplane/src/viewer/resources/translations && file Resources_*.properties | grep -v "ASCII text"`
   produces no output (the five values are pure ASCII; `{0}` stays literal).
4. A `264` literal guard: `grep -rn "264" freeplane_plugin_graph/src/main/java` yields only
   `DisplaySettings.DEFAULT_MAP_SIDEBAR_WIDTH` and (optionally) comments; `MapListPanel` contains
   no `PANEL_WIDTH` and no `264`.

### 6.2 OSGi asset gate (specification-added, mirroring the toolbar spec §7.2)

`gradle :freeplane_plugin_graph:graphOsgiSmoke`, with
`GraphPluginOsgiSmoke.assertGraphBundleImages` (invoked from `assertGraphBundleContents`,
`GraphPluginOsgiSmoke.java:223`; method `:244`) extended for
`images/MapSidebarCollapse.svg` and `images/MapSidebarExpand.svg`: for each,
`bundle.getResource(name) != null` plus a non-empty byte read. Use `getResource` (not `getEntry`):
the exploded bundle root holds only `lib/` and `META-INF/`, and the SVGs live in
`lib/plugin-<version>.jar` reachable through `Bundle-ClassPath`. Additionally assert one
end-to-end `ResourceController.getOptionalIcon("/images/MapSidebarExpand.svg?useAccentColor=true")`
resolution while the bundle is ACTIVE. Falsifier: a missing or mis-packaged asset fails the
assertion; the text fallback alone must not be counted as shipping the asset.

### 6.3 Real-application evidence (recorded in the completion report)

Run the built application on X11 and capture screenshots/notes:

1. Expanded: drag the divider through 180…50 %, the cursor and highlighting are the L&F's; the
   sidebar content and lists paint full width at 400 px.
2. Double-click resets to the clamped default (264 in a window wide enough for it) and one undo
   entry appears.
3. Chevron, rail chevron and `View → Maps sidebar` each toggle; the check mark always matches the
   layout, including after undo/redo and while collapsed.
4. Rail: `MAPS` label, restore chevron, active-map count (and no numeral at zero), Maps menu
   actions still usable.
5. Save/reopen: width and hidden state survive; a hand-edited out-of-range width is displayed
   clamped and survives a no-op reopen/gesture.
6. Read-only workspace: controls usable, no save is attempted, session width/hidden survive a
   window resize; reopen restores the file's values.
7. HiDPI: start the application with `-Dflatlaf.uiScale=2.0` on the JVM command line and verify
   `getMaximumDividerLocation() == maximumWidth` at 1000 px of graph area, a converged re-clamp
   (zero listener re-sets per layout pass), and no commit at 150 px (design §9.2; the exact
   evidence recorded is the printed divider range and the absence of a dirty/save prompt).

Falsifier: any observed divergence (snap during a canvas refresh, a commit at a squeezed width, a
stale check mark, a missing rail control, a clipped label) is a review blocker.

## 7. Accessibility

- The divider keeps the L&F's built-in behaviour: resize cursor, `HOME`/`END` actions, keyboard
  resize gated on split-pane focus; no custom divider painting and no change to focusability.
- Keyboard resize is observable because the commit trigger listens to split-pane focus loss, the
  component the L&F uses for keyboard divider resize (`BasicSplitPaneUI.Handler.focusGained/focusLost`
  toggles `dividerKeyboardResize` on the split pane).
- Both chevrons carry localized tooltips and accessible names from the same keys through the
  `chevronButton` path when their icons resolve, and fall back to the localized text otherwise
  (design §8).
- The rail label and the badge are labels, not painted graphics; the badge tooltip states the
  count in words.
- `View → Maps sidebar` is the keyboard-reachable toggle in every state, follows the menu
  conventions of `View → Settings`, and stays enabled in read-only workspaces.
- Names added for assistive technology: `graph-workspace-map-sidebar`,
  `graph-workspace-map-sidebar-rail`, `graph-workspace-map-sidebar-expand`,
  `graph-workspace-map-sidebar-collapse`, `graph-workspace-map-sidebar-count`,
  `graph-workspace-map-sidebar-label`, `graph-workspace-maps-sidebar-menu-item`.

## 8. Interface digests

All new types are package-private and `final` in `org.freeplane.plugin.graph.window`; the
`DisplaySettings` changes are public in `org.freeplane.plugin.graph.workspace.model`.

```java
final class MapSidebarLayout {                           // no Swing, pure arithmetic
    static final int DEFAULT_WIDTH;                      // DisplaySettings.DEFAULT_MAP_SIDEBAR_WIDTH == 264
    static final int MIN_WIDTH;                          // 180
    static final int RAIL_WIDTH;                         // 26
    private MapSidebarLayout();
    static int maximumWidth(int splitWidth);
    static int effectiveMinimum(int splitWidth, int effectiveDividerWidth, Insets insets);
    static int clampWidth(int requestedWidth, int splitWidth, int effectiveDividerWidth, Insets insets);
    static int canvasMinimumWidth(int splitWidth, int effectiveDividerWidth, Insets insets);
}

final class MapSidebarRail extends JPanel {
    MapSidebarRail();
    @Override public Dimension getPreferredSize();
    void setActiveMapCount(int count);
    JButton restoreButton();
    JLabel countBadge();
}

final class MapSidebarPanel extends JPanel {
    @FunctionalInterface interface CollapsedListener { void collapsedChanged(boolean collapsed); }
    MapSidebarPanel(MapListPanel mapList, CollapsedListener listener);
    void setCollapsed(boolean collapsed);
    boolean isCollapsed();
    void setActiveMapCount(int count);
    void setReadOnly(boolean readOnly);
    MapListPanel mapList();
    MapSidebarRail rail();
    JButton collapseButton();
    static JButton chevronButton(String textKey, String name, String iconPath);
}

// MapListPanel (changed, package-private members)
final class MapListPanel extends JPanel {
    JButton collapseButton();          // new
    void syncListWidths();             // new
    // PANEL_WIDTH removed; setMinimumSize becomes (0, 0)
}

// DisplaySettings (changed, public)
public final class DisplaySettings {
    public static final int DEFAULT_MAP_SIDEBAR_WIDTH = 264;
    public static DisplaySettings defaults();
    public static DisplaySettings of(boolean showArrowheads, CanvasTheme canvasTheme,
        boolean rememberViewport, boolean dimUnrelatedNodes, int mapSidebarWidth,
        boolean mapSidebarHidden, List<UnknownXml> unknownXml);
    public int mapSidebarWidth();
    public boolean mapSidebarHidden();
}

// GraphWorkspaceWindowModel (private members, plus one package-private test accessor)
int appliedSidebarWidth();                    // package-private test accessor (§3.9.2, §5.6 case 11)
private void applySidebarSettings(DisplaySettings settings);
private void setSidebarCollapsed(boolean collapsed);
private void commitSidebarWidth(int location);
private void resetSidebarWidth();
private void runPendingSidebarApply();
private void runWithSidebarGuard(Runnable action);
private void commitSidebarDisplaySettings(int width, boolean hidden);
private int splitWidth();
private int effectiveDividerWidth();
private Insets splitInsets();
```

Cross-task constants fixed by this specification:

| Interface | Value |
| --- | --- |
| Width bounds | `MIN_WIDTH = 180`, `DEFAULT_WIDTH = 264`, `RAIL_WIDTH = 26` |
| Regime conditions | Squeezed `U < MIN_WIDTH`; else Pinned `maxW == U`; else Normal `maxW > U` |
| Commit baseline | `appliedSidebarWidth`; written only by `applySidebarSettings` |
| XML attributes | `map-sidebar-width`, `map-sidebar-hidden` (both optional, written always) |
| Component names | §3.1 |
| Resource keys | §3.10 |
| Icon paths | `/images/MapSidebarCollapse.svg?useAccentColor=true`, `/images/MapSidebarExpand.svg?useAccentColor=true` |

## 9. Design silences resolved by this specification, and reference corrections

### 9.1 Specification decisions on design silences

- **S1 — whose minimum is `effectiveMinimum`.** Design §6.3 has `MapSidebarPanel.setCollapsed`
  recompute `effectiveMinimum(splitWidth, effectiveDividerWidth, insets)`, but the panel is created
  before the split pane and has no geometry. This specification pins: the panel's expanded minimum
  is `MIN_WIDTH` (its standalone contract, asserted by `MapSidebarPanelShould`), and the window
  overrides it with the live `effectiveMinimum(...)` in `applySidebarSettings` step 8 (asserted by
  the split-clamp tests and `MapSidebarPanel`'s L&F behaviour). Behaviour is unchanged.
- **S2 — chevron wiring.** Design §6.4 says the window supplies the heading button's action, and
  §6.3/§6.5 define one `CollapsedListener`. This specification pins: the window attaches the
  heading button action to `collapseButton()`; the panel wires the rail's restore button (which it
  creates) to `MapSidebarPanel.setCollapsed(false)`, which reaches the window through the single
  `CollapsedListener` (`this::setSidebarCollapsed`, registered through the constructor).
  The window's guard suppresses the listener callback for its own `setCollapsed` calls, so both
  paths produce exactly one command.
- **S3 — list-width listener placement.** Design §6.4 names `MapSidebarPanel` as the
  `ComponentListener` owner. `setSize`/`doLayout` post `componentResized` asynchronously
  (`Component.notifyNewBounds` → `Toolkit.getEventQueue().postEvent`), so no sibling layout
  ordering can be assumed. This specification installs the listener on `MapListPanel` itself —
  the component whose width it tracks, read from its own `getWidth()`, which `setBounds` sets
  synchronously — and additionally calls `syncListWidths()` at the end of
  `applySidebarSettings`; the design's requirement — a resize without a subsequent `setRows(...)`
  does not keep stale list widths — is preserved, and the tests that exercise it flush the EDT
  (§5.1, M4).
- **S4 — what writes `appliedSidebarWidth`.** Design §4.3 lists "post-keyboard re-clamp" among the
  programmatic applies that update the baseline. If the property listener's own re-clamp wrote the
  baseline, a keyboard adjustment could never commit on focus loss, contradicting the same
  section's keyboard rule. This specification pins: only `applySidebarSettings` writes
  `appliedSidebarWidth`; the property listener's re-clamp and the collapsed hold do not. The
  "post-keyboard re-clamp" is the apply caused by the keyboard adjustment's own command
  round-trip, which runs after the commit.
- **S5 — names the design leaves unnamed.** The rail restore button, the rail vertical label and
  the new map-list heading row receive the names in §3.1; tests depend on them.
- **S6 — vertical-label recipe.** §3.3 fixes the rotation and preferred-size rules, matching the
  mockup generator's 90° clockwise treatment.
- **S7 — chevron disabled icons.** The chevrons never become disabled (design §4.1/§5.3), so
  `chevronButton` does not install the toolbar's derived disabled icon; if an enablement change is
  ever introduced, the L&F default applies until the treatment is revisited.
- **S8 — provenance of the design-review-5 approval (m13).** `design-review-5.md:28,105-106`
  records a commit baseline captured at divider press (`sidebarGestureStartLocation`), an
  identifier absent from the committed design; the committed design instead pins
  `appliedSidebarWidth` in §4.3 and §6.5, and its own header records that the final minor fix
  (m5-1) replaced the gesture-start baseline after round 5 ran. The revision this specification
  derives from is therefore the committed post-round-5 revision; the `appliedSidebarWidth`
  baseline is the one pinned here (§9.1 S4), and its verification belongs to this specification's
  review trail rather than to design-review-5.

- **S9 — the reset contract (round-3 finding 4; PM-ratified design amendment).** Design §4.1
  says the double-click resets the width "to 264 px and commits it", but 264 lies outside
  `[U, max(U, maxW)]` whenever the split width is between 360 and 527 px, so an unclamped reset
  would violate the design's own "every committed width is inside those bounds" rule. The
  specification therefore resets to `clampWidth(DEFAULT_WIDTH, …)` (§3.9.4) and the design's §4.1
  bullet was amended to match: the reset targets the default width clamped into the current
  allowed range, so at a ceiling below 264 the committed value is the ceiling. Behaviour for every
  window at or above the default ceiling is unchanged, and this is the only design contradiction
  the specification resolved rather than encoded.

### 9.2 Reference corrections against the design (code is authoritative)

| Design reference | Current code |
| --- | --- |
| `WorkspaceSettingsPanel.java:26` (preferred width 244) | `:30` |
| `WorkspaceSettingsPanel.publishSettings()` `:145-152` | `:145-153` |
| `WorkspaceXmlCodec.parseDisplaySettings` `:308-319` | `:308-318` |
| `GraphWorkspaceWindowModel.createContent()` `:1036-1047` | `:1036-1049` |
| maps-menu wiring `GraphWorkspaceWindow.java:1095-1113` | `:1097-1115` |
| `WorkspaceCommands.java:464-478` (`SetDisplaySettingsCommand`) | `:464-479` |
| `GraphWorkspaceStore.updateViewport` `:141-165` | exact |

All other design citations were re-read at `aa6ac48b02` and are exact; the 15
`DisplaySettings.of(...)` call sites and their 10 files are exactly as the design lists
(`DisplaySettings.of` declaration `DisplaySettings.java:34`; `defaults()` `:29`; private
constructor `:19-24`).

### 9.3 Changes made for spec-review-1 (verification index)

| Finding | Change |
| --- | --- |
| B1 | §5.2 case 4 now asserts `clampWidth(264, …) == 180` at `splitWidth = 150` (the ceiling). Its second expectation stays `clampWidth(0, …) == 144`: with `lower = effectiveMinimum(150, 6) = 144` and `upper = max(144, maximumWidth(150)) = 180`, `Math.max(144, Math.min(180, 0))` is 144. The review's proposed `180` for a zero request contradicts the §3.2 formula, so no test asserts it; the squeezed 144 value remains pinned by §5.2 case 3 and the §4.1 table. |
| B2 | §3.4 adds the listener-free `applyCollapsedState(boolean)`: the constructor calls it once with `false` (step 4) and `setCollapsed` delegates to it after the idempotence guard. §5.4 cases 1–3 pin the initial expanded state and the constructor's silence. |
| B3 | §5.1 adds `menuItemByName(...)`/`mapsSidebarMenuItem(model)`, which match the exact design name `graph-workspace-maps-sidebar-menu-item`; §5.6 case 5 uses the helper. §3.9.1 records that the item is created directly, not through `item(...)` (whose `graph-workspace-menu-item-` prefix would rename it). |
| M1 | §4.1 evaluates the regimes in the order Squeezed → Pinned → Normal, with `U == MIN_WIDTH` for Pinned/Normal; §8's cross-task constants match. |
| M2 | §3.9.1/§3.9.3 use the real `applyPresentation(GraphWorkspacePresentation value)` signature and `next.displaySettings()` (`:557-571`, local `:562`). |
| M3 | §3.4, §3.5 and §9.1 S3 replace the Swing dispatch-order claim with the real reason: the listener sits on the component whose width it tracks and reads its own synchronously set `getWidth()`, plus the apply-time `syncListWidths()`. |
| M4 | §5.1 adds the EDT flush/dispatch rule; §5.5 case 3 and §5.6 cases 2, 6, 7 and 10 apply it explicitly (the §5.6 preamble covers the remaining layout cases). |
| M5 | §5.3 records the raw release location and case 4 asserts literal commits (`999 → 500`, `250 → 250`) instead of restating `clampWidth(...)`. |
| M6 | §5.5 case 4 sizes the list (`setSize(400, 300)`) and asserts the literal width `400` plus `ROW_HEIGHT`. |
| M7 | §5.2 case 7 asserts hand-computed `canvasMinimumWidth` literals (`494`, `175`, `0`, `0`); the interactive-ceiling probe stays in §5.3 case 2. |

The thirteen minor findings are folded into the sections they affect: m1 §3.2, m2 §3.4, m3 §3.3,
m4 §3.9.5, m5 §3.5, m6 §3.9.7, m7 §9.2, m8 §3.8, m9 §6.2, m10 §1.3/§3.4, m11 §5.6 case 5,
m12 §5.1 helpers, m13 §9.1 S8. Citation corrections were re-read against the worktree code; where
a review line number differed from the code, the code's line is used
(`WorkspaceToolbar.configureIcon` is `:398-409`; `GraphPluginOsgiSmoke.assertGraphBundleImages`
is `:244`, invoked at `:223`).

### 9.4 Changes made for spec-review-2 (verification index)

| Finding | Change |
| --- | --- |
| B2-1 | §3.9.1 now pins `graphArea`'s existing name (`graph-workspace-graph-area`) and its two direct children; §5.1 replaces the index-based helpers with name/role navigation (`graphArea(model)`, `splitPane(model)`, `sidebarPanel(model)`, `canvasScrollPane(model)`, `componentByName`) and updates the existing navigation sites; §5.6 case 1 asserts the real topology and uses no child index. |
| M2-1 | §3.9.3 step 6 (`setDividerSize`/`setEnabled`) now precedes the step-7 geometry read, so a hidden → expanded transition clamps with the restored divider width and one apply suffices on the read-only path. Renumbered cross-references are updated (§3.4 step 8, §3.5 step 12, §9.1 S1 step 8, §3.9.5 "step 9"). New §5.6 case 17 pins the single read-only apply with literals (`200` divider, `180` sidebar minimum, `193` canvas minimum, `200` L&F ceiling, Metal insets) against the stale `d = 0` values (`199`/`194`). |
| M2-2 | §3.9.4 `resetSidebarWidth` now writes and commits `clampWidth(DEFAULT_WIDTH, splitWidth, d, insets)`; §4.2 states the reset clamps into the current range; §4.4 gains the reset row; §5.6 case 4 pins the literal `264` at 1000 and the clamped literal `200` at 400. |
| m2-1 | §3.9.4's `commitSidebarWidth` and `resetSidebarWidth` sketches declare the `splitWidth`/`d`/`insets` locals. |
| m2-2 | §3.9.2 and §8 add the package-private `appliedSidebarWidth()` accessor; §5.6 case 11 uses it instead of reading the private field. |
| m2-3 | §5.6 case 2 asserts the independent literals `500`/`250` and case 15 the literal `400`; no window-test expectation restates `clampWidth(...)`. |
| m2-4 | §5.1 scopes the EDT stimulus rule to `GraphWorkspaceWindowModelShould`, states that `MapSidebarPanelShould`/`MapListPanelShould` stay on the test thread (the `@Before` mocks are not visible on the EDT), and adjusts the flush rule and §5.5 case 3 to barrier-only `runOnEdt` calls. |
| m2-5 | §4.1's pinned row now reports `[U + insets.left, U + insets.left]` and explains the `DragController` internal `(minX, maxX)` disabling. |
| m2-6 | §3.9.6 states the pre-`pack()` clamp is `maximumWidth(0) == MIN_WIDTH` (180). |
| m2-7 | §5.6 case 16 rewrites `composesAHeadlessModelessWorkspace…` in place (name and non-topology assertions kept), preserving §5.10's no-deletion rule. |

## 10. Open issues

None outstanding. One design contradiction was resolved by the specification and ratified by the
PM — the reset contract in §9.1 S9 — and the approved design document was amended in the same
commit so the two documents agree. No other contradiction between the approved design and the
current code was found beyond the paragraph-level citation drift listed in §9.2, which does not
change any specified behaviour.
