# Graph Workspace Toolbar — Select, Connect, Search & Settings Affordances — Specification

- Date: 2026-09-12
- Status: implementation-ready specification derived from the approved design
  (`docs/superpowers/specs/2026-09-12-graph-toolbar-affordances-design.md`,
  design-review rounds 1–5, round 5 zero blockers)
- Ticket: none — Task Identifier: `2026-09-12-graph-toolbar-affordances`
- Scope: `freeplane_plugin_graph` (delivery branch `plugin/graph-workspace`)
- Approved UI: `docs/superpowers/specs/images/2026-09-12-graph-toolbar-affordances-mockup.png`
  (option B, wording 3, switch-styling option A)
- Switch-styling probe: `.superpowers/brainstorm/1753507-1789176239/artifacts/flatlaf-probe/`
  (`ToolGroupProbe.java`, renders, `README.md`) and
  `.superpowers/brainstorm/1753507-1789176239/content/switch-chrome-v2.html`
- Code baseline inspected for this specification: `1ca81c35a1`
  (commit `478e54c6f3` adds only the design document and mockup; the design
  commit's parent is the code under change).
- Short path convention used below: `WorkspaceToolbar.java` =
  `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceToolbar.java`;
  `GraphWorkspaceWindow.java` = the same package's `GraphWorkspaceWindow.java`;
  `GraphWorkspaceWindowModelShould.java` =
  `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java`.

## 1. Scope

### 1.1 In scope (copied from design §2)

1. Give `Select`, `Connect`, `Search` and `Settings` the approved presentation
   without disturbing the rest of the row.
2. Make the search box state what it searches, in-place.
3. Make the settings panel's open/closed state visible on the control that owns it.
4. Reuse the existing icon pipeline: scalable SVG, preference-driven height,
   theme-adaptive colour, text fallback, no hardcoded pixel sizes.
5. Keep every existing control identity, enablement rule, listener, menu item,
   shortcut and the current search semantics.

### 1.2 Non-goals (copied from design §3; no widening)

- The direction `JComboBox`, `Fit Graph`, `Reset Zoom`, `Pin Node`, the maps
  panel, the status bar and the canvas zoom treatment stay exactly as shipped.
- Right-aligning search and settings, or grouping them at the row's end.
- Any change to search semantics (it still dims non-matching nodes rather than
  filtering) or to search result computation.
- Any new settings control (for example the mockup's `Node size`, `Link
  thickness`, `Label visibility` sliders) and no `×` close control on the panel.
- Renaming the settings panel's own heading (`graph_workspace.settings.heading`
  is `Display`, `freeplane/src/viewer/resources/translations/Resources_en.properties:930`);
  the toolbar tooltip uses the user-approved `Graph settings` while the panel
  keeps its heading.
- Converting the `View → Settings` menu item into a check-box item: it stays a
  plain item that delegates to the same action, so the gear remains the only
  toolbar surface that shows the panel's state.
- Custom chrome for the switch or the search field (no painted frame, radius,
  divider, or pill outline), and no new hardcoded colour anywhere in Java.
- Fixing the toolbar's row-height/overflow strategy: this change reduces row
  width, but the wrapping defect itself stays open and is only *measured* here.

### 1.3 Approved UI and accepted deviations (copied from design §5)

The approved treatment is **option B — grouped tool switch**: the Select and
Connect glyphs are adjacent in one switch so "exactly one mode is active" is
visible without hovering. Rejected: option A (icon-only standalone toggles) and
option C (keep the text labels, change only Settings/search). Wording chosen
(wording 3): Select `Select`; Connect `Connect — create a cross-map
relationship`; Settings `Graph settings`; search prompt/tooltip/accessible name
`Search nodes and maps`. Two PM decisions plus the reopened styling question,
all accepted: the gear shows its state (`JToggleButton`); the text fallback
stays; the grouped switch is styled by the Look-and-Feel, not by us (option A,
R16).

| Mockup | This specification | Why |
| --- | --- | --- |
| Single rounded outline around the two segments, blue frame | The two segments carry the Look-and-Feel's own toolbar-button treatment (R16): with the bundled FlatLaf 3.7.2 at zero-gap adjacency the active segment becomes a filled segment and the inactive one a plain glyph, instead of two separate rounded rectangles | A painted outline would need a colour the Look-and-Feel does not expose portably; its own grouping is the honest equivalent and paints nothing. On a Look-and-Feel that does not know the property, two adjacent plain toggles are the accepted minimum. |
| Rounded search pill with a muted grey magnifier | The field keeps its LaF (rectangular) border and focus painting; the magnifier and prompt are painted inside it; the magnifier uses the accent pipeline like every other glyph | No invented chrome; the accent colour is the only theme-adaptive source available for a vector glyph. |
| Settings panel `×` close control | Not built; the panel is toggled from the gear and the `View → Settings` item only | Recorded non-goal (design §3); prevents the table from reading as an oversight. |

## 2. Traceability — design requirement to specification section

| ID | Requirement (design §4) | Realised in this spec |
| --- | --- | --- |
| R1 | Select and Connect are icon-only toggles inside one `ToolSwitch` that owns their `ButtonGroup`; exactly one selected; the toolbar's local `ButtonGroup` is removed; each segment gets the toolbar-button client property when its icon resolves (R16) | §3.1, §3.3.2, §3.3.3, §3.3.5; tests 1, 8, 8b |
| R2 | Control names unchanged; `approvedControlNames()` keeps its 14 entries; the row keeps 13 direct children | §4.3; tests 1, 2 |
| R3 | One shared `configureIcon(button, labelTextKey, tooltipTextKey, iconPath)`; per-control label/tooltip keys; resolved icon clears text and sets tooltip+accessible name; fallback keeps the label text and leaves tooltip/accessible unset; the four existing icon buttons keep `labelTextKey == tooltipTextKey`; `WorkspaceToolbar.java:123` deleted | §3.3.1, §3.3.4, §3.3.6, §4.3; tests 2, 3 |
| R4 | Text fallback values; search prompt/tooltip/accessible name from `graph_workspace.tooltip.search`, set unconditionally | §3.3.1, §3.3.4, §4.1; tests 3, 3b, 4 |
| R5 | Four glyphs resolved via `ResourceController.getOptionalIcon` with `?useAccentColor=true` and no pixel size; paths `GraphSelect.svg`, `GraphConnect.svg`, `GraphSettings.svg`, `GraphSearch.svg` | §3.3.4, §4.2; tests 2, 10 |
| R6 | Search control stays a single `JTextField` subclass; `searchField().getText()` remains the only query channel | §3.2, §3.3.4; test 4, 10 |
| R7 | Magnifier persistent; prompt only empty+unfocused; live predicate; glyph inside the leading margin; text/prompt at `getInsets().left`; preferred height follows the glyph | §3.2.3, §3.2.4, §5 (E2–E5, E8–E10); test 5 |
| R8 | Prompt is never field content; document listener fires only for real edits | §3.2.2, §3.2.3; test 4 |
| R9 | Gear is a `JToggleButton`; `gear.isSelected() == settingsPanel.isVisible()` after construction and every mutation path; one owner of the update reads the panel first and pushes to the gear | §3.4; test 7 |
| R10 | Enablement unchanged: Select always enabled; Connect disabled read-only; Settings enabled read-only; direction and all other controls keep their rules | §4.4; test 6 |
| R11 | No behaviour outside presentation changes: listeners, tool selection, search computation, shortcuts, menu items, status text; buttons stay non-focusable | §3.3.6, §4.3; tests 4, 8, 9 |
| R12 | Row composition and order unchanged apart from the shared switch; 42 px row height unchanged | §4.3; test 1 |
| R13 | No new hardcoded UI colour and no custom-painted chrome; prompt uses the field's own `getDisabledTextColor()`; one documented Look-and-Feel client property | §1.3, §3.1, §3.2.1, §5 (E11) |
| R14 | Translation values in `freeplane/src/viewer/resources/translations/Resources_en.properties`; em dash as `\u2014`; `gradle format_translation` run | §4.1, §7.4; test 3b |
| R15 | Evidence records the layout-required toolbar width plus in-scope control widths to stdout and `build/graph-ui-evidence/row-width.txt`; no new clipping assertion; existing `assertNoOverlap` reused | §7.3; test 10 |
| R16 | Exactly `selectButton` and `connectButton` receive `"JButton.buttonType" = "toolBarButton"` as a raw string when their icon resolved; no FlatLaf import/dependency; no other control styled | §3.3.3 (styling contract), §4.2 (asset paths); test 8b (property, negative, source-literal and no-import guards); §7.1 (`verifyGraphBundle`) |

## 3. Contracts

### 3.1 `ToolSwitch` (new, package-private, `org.freeplane.plugin.graph.window`)

```java
final class ToolSwitch extends JPanel {
    ToolSwitch(final JToggleButton... segments);
}
```

Constructor, in this order:

1. `setName("graph-workspace-tool-switch")`.
2. `setOpaque(false)`.
3. `setLayout(new GridLayout(1, segments.length, 0, 0))` — zero horizontal and
   vertical gap (the design's exact probe adjacency).
4. Create one `new ButtonGroup()`.
5. For each segment in argument order: `group.add(segment); add(segment);`.

Ownership contract:

- `ToolSwitch` is the single owner of the `ButtonGroup`. The toolbar deletes its
  local `ButtonGroup tools` (`WorkspaceToolbar.java:99-101`) and the now-unused
  `import javax.swing.ButtonGroup;` (`WorkspaceToolbar.java:18`).
- `ToolSwitch` must not paint: no override of `paint`, `paintComponent`,
  `paintBorder` or `update`; no border, background or foreground is installed.
- `ToolSwitch` must not style: it sets no client property, border or margin on
  its segments. Segment styling is `WorkspaceToolbar.applyToolbarSegmentStyle`
  only (§3.3.3).
- `ToolSwitch` adds no listeners and mutates no selection beyond the
  `ButtonGroup` membership.

Observables a test asserts without private state:

- `getName().equals("graph-workspace-tool-switch")`, `!isOpaque()`.
- `getLayout() instanceof GridLayout` with `getRows() == 1`,
  `getColumns() == 2`, `getHgap() == 0`, `getVgap() == 0`.
- `getComponentCount() == 2`, `getComponent(0) == selectButton`,
  `getComponent(1) == connectButton` (argument order preserved).
- `selectButton.getParent() == connectButton.getParent() == the ToolSwitch`.
- `selectButton.getModel().getGroup()` is non-null,
  `connectButton.getModel().getGroup()` is the same instance, and iterating the
  group's elements yields exactly those two models (so no other group holds
  them).

### 3.2 `GraphSearchField` (new, package-private, `window` package)

`final class GraphSearchField extends JTextField` — a single component, no
wrapper panel, no focus-traversal change, no accessible-name plumbing across a
container; `searchField().getText()` remains the query channel (R6).

#### 3.2.1 Members

```java
final class GraphSearchField extends JTextField {
    private static final int GAP = 6;             // leading gap between glyph and text origin
    private static final int TRAILING_INSET = 6;  // right content margin while a glyph is shown
    private Insets baseMargin;                    // captured in GraphSearchField(), null-tolerant
    private String prompt;
    private Icon promptIcon;

    GraphSearchField() { baseMargin = getMargin(); }

    void setPrompt(final String prompt);
    void setPromptIcon(final Icon icon);
    private Insets currentMargin();               // getMargin() != null ? getMargin() : baseMargin
    @Override protected void paintComponent(final Graphics graphics);
    @Override public Dimension getPreferredSize();
}
```

The class keeps the Look-and-Feel border, background and focus painting; it
paints only the magnifier and the prompt (R13). `GraphSearchField()` captures
the LaF-installed margin after `super()` and before any setter runs.

#### 3.2.2 Setters (constructor-only)

- `setPrompt(prompt)`: assigns `prompt`, then `repaint()`. The prompt is never
  written to the document (`getText()` is unaffected). The design makes both
  setters constructor-only; if either is ever invoked after layout it must also
  call `revalidate()` (the prompt feeds `getPreferredSize()`).
- `setPromptIcon(icon)`: assigns `promptIcon = icon` and computes the margin,
  calling the effective base `base`:
  - `base = baseMargin != null ? baseMargin : new Insets(0, 0, 0, 0)`;
  - `icon != null` → `setMargin(new Insets(base.top, icon.getIconWidth() + GAP,
    base.bottom, TRAILING_INSET))`;
  - `icon == null` → `setMargin(baseMargin)` (restores the exact captured value,
    including `null`).
  - Then `repaint()`; if invoked after layout, `revalidate()`.
- `currentMargin()` returns `getMargin() != null ? getMargin() : baseMargin`;
  every use of a margin value treats a `null` result as zero insets (the
  null-tolerance required by the capture/restore rule).

#### 3.2.3 Paint algorithm (exact derivations)

`paintComponent(graphics)` performs exactly, in order:

1. `super.paintComponent(graphics)`.
2. If `promptIcon != null` — the glyph is persistent in every state (R7):
   - `glyphX = getInsets().left - currentMargin().left` where `currentMargin()`
     is the margin installed by `setPromptIcon` (null → 0); the Look-and-Feel
     text border folds that margin into `getInsets()`, so this x is the leading
     edge of a slot that ends at the text origin, leaving the glyph's trailing
     edge `GAP` left of it;
   - `glyphY = (getHeight() - promptIcon.getIconHeight()) / 2` (integer
     division);
   - `promptIcon.paintIcon(this, graphics, Math.max(0, glyphX), glyphY)`.
3. If `!getText().isEmpty() || isFocusOwner()` → `return`. The predicate is
   evaluated live at paint time from the field state, never from a cached flag.
4. Prompt (when `prompt != null`): draw in `getDisabledTextColor()` at
   `x = getInsets().left`, baseline `y = getInsets().top +
   getFontMetrics(getFont()).getAscent()` — exactly where typed text starts.

#### 3.2.4 Preferred size

```java
@Override
public Dimension getPreferredSize() {
    final int promptWidth = prompt == null ? 0 : getFontMetrics(getFont()).stringWidth(prompt);
    final int width = Math.max(160, getInsets().left + promptWidth + getInsets().right);
    final Insets margin = currentMargin() == null ? new Insets(0, 0, 0, 0) : currentMargin();
    final int iconHeight = promptIcon == null ? 0 : promptIcon.getIconHeight();
    final int height = Math.max(26, iconHeight + margin.top + margin.bottom);
    return new Dimension(width, height);
}
```

- The width does **not** add `iconWidth + GAP` again: the margin installed by
  `setPromptIcon` is already folded into `getInsets()` by the LaF text border.
- The height follows the glyph, recomputed when the icon is set, so a
  `toolbar_icon_height` above the 16 pt default cannot clip the magnifier; with
  no icon the width/height rules still apply.
- `WorkspaceToolbar.java:122`
  (`searchField.setPreferredSize(new Dimension(160, 26))`) is deleted, so this
  rule wins.

Observables a test asserts without reading private state: `getText()`,
`isFocusOwner()`, `getToolTipText()`, the accessible name, `getMargin()`,
`getInsets()`, `getFontMetrics(getFont())`, `getPreferredSize()`, the paint
output rendered into a `BufferedImage`, and the `(x, y)` coordinates recorded
by a painting `Icon` stub passed through `setPromptIcon`.

### 3.3 `WorkspaceToolbar` changes

#### 3.3.1 Shared icon routine

```java
private static void configureIcon(final AbstractButton button, final String labelTextKey,
        final String tooltipTextKey, final String iconPath)
```

Behaviour, in order:

1. `button.setText(TextUtils.getText(labelTextKey))` — the button text always
   starts as the label.
2. `final Icon icon = ResourceController.getResourceController().getOptionalIcon(iconPath)`.
3. `icon != null`: `button.setIcon(icon)`, `button.setText(null)`,
   `button.setToolTipText(TextUtils.getText(tooltipTextKey))`,
   `button.getAccessibleContext().setAccessibleName(TextUtils.getText(tooltipTextKey))`.
4. `icon == null`: the label text stays; the icon stays null; the tooltip and
   accessible name stay unset. This is the existing fallback rule pinned at
   `GraphWorkspaceWindowModelShould.java:294-309`, now applied to the new
   controls too.

`configureIcon` sets no name, margin, focusability or client property;
`configure(button, name)` (`WorkspaceToolbar.java:391-395`) remains the only
place for those. Styling is not part of this routine.

Per-control key table:

| Control | labelTextKey (fallback text) | tooltipTextKey (tooltip + accessible name) | iconPath | Fallback text |
| --- | --- | --- | --- | --- |
| `selectButton` | `graph_workspace.tool.select` | `graph_workspace.tool.select` | `/images/GraphSelect.svg?useAccentColor=true` | `Select` |
| `connectButton` | `graph_workspace.tool.connect` | `graph_workspace.tooltip.connect` | `/images/GraphConnect.svg?useAccentColor=true` | `Connect` |
| `settingsButton` | `graph_workspace.action.settings` | `graph_workspace.tooltip.settings` | `/images/GraphSettings.svg?useAccentColor=true` | `Settings` |
| `undoButton` | `graph_workspace.action.undo_workspace` | same key | `/images/undo.svg?useAccentColor=true` | action key text |
| `redoButton` | `graph_workspace.action.redo_workspace` | same key | `/images/redo.svg?useAccentColor=true` | action key text |
| `zoomInButton` | `graph_workspace.action.zoom_in` | same key | `/images/ZoomIn24.svg?useAccentColor=true` | action key text |
| `zoomOutButton` | `graph_workspace.action.zoom_out` | same key | `/images/ZoomOut24.svg?useAccentColor=true` | action key text |

The four existing icon buttons keep `labelTextKey == tooltipTextKey`, so their
existing resolved/fallback tests stay green (R3). The search field is not an
`AbstractButton`: its prompt, tooltip and accessible name are fed from
`graph_workspace.tooltip.search` unconditionally (§3.3.4).

#### 3.3.2 Helpers

```java
private static JButton iconButton(final String textKey, final String name, final String iconPath);
private static JToggleButton iconToggle(final String labelTextKey, final String tooltipTextKey,
        final String name, final String iconPath);
```

- `iconButton` keeps its current 3-argument signature and delegates with
  `configureIcon(button, textKey, textKey, iconPath)`; the four existing call
  sites (`WorkspaceToolbar.java:52-55,62-65`) are unchanged, then
  `configure(button, name)`.
- `iconToggle` builds `new JToggleButton(TextUtils.getText(labelTextKey))`,
  delegates to `configureIcon(button, labelTextKey, tooltipTextKey, iconPath)`,
  then `configure(button, name)`. It produces a plain icon-only toggle and
  never styles.
- `toggleButton(...)` (`WorkspaceToolbar.java:385-389`) becomes unused and is
  deleted.

#### 3.3.3 Segment styling

```java
private static void applyToolbarSegmentStyle(final AbstractButton segment)
```

- If `segment.getIcon() != null`:
  `segment.putClientProperty("JButton.buttonType", "toolBarButton")` — raw
  string literals; no `com.formdev.flatlaf` import, no new build or OSGi
  dependency, no custom painting (R16).
- Called exactly twice in the `WorkspaceToolbar` constructor, on
  `selectButton` and `connectButton` (at the position of the deleted local
  `ButtonGroup`, `WorkspaceToolbar.java:99-101`), after their field initializers
  resolved the icons.
- Never called from `iconToggle`, `configureIcon`, `configure`, or any other
  builder; never called on the gear, undo/redo/zoom/fit/reset/pin. Therefore a
  missing icon leaves the segment as a plain bordered toggle, and the gear keeps
  its normal toggle appearance even though it is built by `iconToggle(...)`.

#### 3.3.4 Search-field wiring and gear conversion

Field declarations:

- `private final GraphSearchField searchField = new GraphSearchField();`
  (`WorkspaceToolbar.java:60`); the accessor stays
  `JTextField searchField()` (`WorkspaceToolbar.java:205-207`, R6).
- `private final JToggleButton settingsButton = iconToggle(
  "graph_workspace.action.settings", "graph_workspace.tooltip.settings",
  "settings", "/images/GraphSettings.svg?useAccentColor=true");`
  (`WorkspaceToolbar.java:61`). The accessor widens to
  `AbstractButton settingsButton()` (`WorkspaceToolbar.java:209-211`); its
  callers need no other change (`GraphWorkspaceWindow.java:994,1014,1093`;
  `GraphWorkspaceWindowModelShould.java:941`).

Constructor wiring for the search field, replacing
`WorkspaceToolbar.java:120-123`:

```java
searchField.setName("graph-workspace-search");                          // unchanged
searchField.setToolTipText(TextUtils.getText("graph_workspace.tooltip.search"));   // unchanged
searchField.getAccessibleContext().setAccessibleName(
    TextUtils.getText("graph_workspace.tooltip.search"));               // new, unconditional (R4)
searchField.setPrompt(TextUtils.getText("graph_workspace.tooltip.search"));        // new, no Java literal
searchField.setPromptIcon(ResourceController.getResourceController()
    .getOptionalIcon("/images/GraphSearch.svg?useAccentColor=true"));   // new, same icon pipeline (R5)
```

Deleted lines: `searchField.setPreferredSize(new Dimension(160, 26));`
(`WorkspaceToolbar.java:122`) and
`settingsButton.setToolTipText(TextUtils.getText("graph_workspace.tooltip.settings"));`
(`WorkspaceToolbar.java:123`). The gear's tooltip now comes only from
`configureIcon` on icon resolution; the same rule as undo/redo/zoom.

New gear state push:

```java
void setSettingsVisible(final boolean visible) {
    settingsButton.setSelected(visible);
}
```

It only sets the selected state (dumb push); it never reads the panel, never
toggles and never touches the action.

The document listener (`WorkspaceToolbar.java:154-169`) and `publishSearch()`
(`WorkspaceToolbar.java:342-344`) are unchanged and stay attached to the same
field instance; because the prompt is paint-only, every published query is a
real edit.

#### 3.3.5 Row assembly

- Keep `selectButton.setSelected(true)` (`WorkspaceToolbar.java:98`).
- Replace the local `ButtonGroup` (`WorkspaceToolbar.java:99-101`) with the two
  `applyToolbarSegmentStyle` calls (§3.3.3).
- Replace `add(selectButton); add(connectButton);`
  (`WorkspaceToolbar.java:129-130`) with a single
  `add(new ToolSwitch(selectButton, connectButton));` at the same row position
  (after `add(redoButton)`, before `add(directionComboBox)`). No `ToolSwitch`
  field is required.
- Row order (13 direct children):
  `open, save, undo, redo, ToolSwitch(select, connect), direction, search,
  settings, zoomOut, zoomIn, fit, reset, pin`.

#### 3.3.6 Unchanged in `WorkspaceToolbar`

- Listeners: `WorkspaceToolbar.java:140-153`, including
  `selectButton`/`connectButton` (`:145-146`) and the gear (`:148`).
- `chooseTool` read-only guard (`WorkspaceToolbar.java:304-308`).
- `updateReadOnlyControls()` rules (`WorkspaceToolbar.java:352-363`); it must
  not touch the gear's selected state.
- `configure(...)` (`WorkspaceToolbar.java:391-395`) — buttons stay
  non-focusable with the same margins; `button(...)` for text-only controls is
  unchanged.
- `approvedControlNames()` (`WorkspaceToolbar.java:69-71`) and the 42 px
  `PREFERRED_SIZE` (`WorkspaceToolbar.java:43,95-96`).
- No keyboard shortcut, menu item, status text or search computation change
  (R11).

### 3.4 `GraphWorkspaceWindow` / `GraphWorkspaceWindowModel` wiring

In `GraphWorkspaceWindowModel`:

```java
// replaces the anonymous Runnable at GraphWorkspaceWindow.java:458-463
toolbar.setSettingsAction(this::toggleSettingsPanel);

private void toggleSettingsPanel() {                    // the single owner of the update (R9)
    settingsPanel.setVisible(!settingsPanel.isVisible());
    toolbar.setSettingsVisible(settingsPanel.isVisible());   // panel-derived, not click-derived
}
```

Construction sync: immediately after `content = createContent();`
(`GraphWorkspaceWindow.java:504`) call once:

```java
toolbar.setSettingsVisible(settingsPanel.isVisible());
```

The panel starts visible (default `JPanel` visibility; the only
`setVisible` on it is the toggle at `GraphWorkspaceWindow.java:461`) and is
added at `GraphWorkspaceWindow.java:1040`, so the sync selects the gear at
construction.

Unchanged:

- `createContent()` (`GraphWorkspaceWindow.java:1035-1047`) and the panel itself
  (`WorkspaceSettingsPanel`, including the heading `Display`).
- The `View → Settings` item stays a plain item and keeps the same path:
  `viewSettingsMenuItem = item("graph_workspace.action.settings", "settings",
  event -> toolbar.settingsButton().doClick());`
  (`GraphWorkspaceWindow.java:1092-1093`); `updateMenuEnablement()` keeps
  `viewSettingsMenuItem.setEnabled(true)` (`GraphWorkspaceWindow.java:1015`).
- `setReadOnlyOnEdt` (`GraphWorkspaceWindow.java:991-1006`): the gear stays
  enabled because of `toolbar.settingsButton().setEnabled(true)` at `:994`; the
  gear's selected state is not touched. `updateMenuEnablement` repeats the
  force-enable at `:1014`.
- Outer `GraphWorkspaceWindow.setReadOnly` (`GraphWorkspaceWindow.java:198-200`)
  and everything else about the window.

## 4. Resources, assets, names and enablement

### 4.1 Resource keys

File: `freeplane/src/viewer/resources/translations/Resources_en.properties`.

| Key | Current | Required value | Notes |
| --- | --- | --- | --- |
| `graph_workspace.tooltip.connect` | absent | `Connect \u2014 create a cross-map relationship` | New key; the em dash **must** be written as the six characters `\u2014` (file stays ISO-8859-1/ASCII). Insert so the sorted file stays sorted; `gradle format_translation` owns the final order. |
| `graph_workspace.tooltip.search` | `Search graph` (line 961) | `Search nodes and maps` | Changed; drives the search prompt, tooltip and accessible name. |
| `graph_workspace.tooltip.settings` | `Display settings` (line 962) | `Graph settings` | Changed; gear tooltip and accessible name. |
| `graph_workspace.tool.select` | `Select` (line 959) | unchanged | Select label fallback + tooltip/accessible name. |
| `graph_workspace.tool.connect` | `Connect` (line 958) | unchanged | Connect label fallback. |
| `graph_workspace.action.settings` | `Settings` (line 808) | unchanged | Settings label fallback. |
| `graph_workspace.settings.heading` | `Display` (line 930) | unchanged | Panel heading; explicitly not renamed (non-goal). |

No production Java source contains the prompt text as a literal; the prompt is
always `TextUtils.getText("graph_workspace.tooltip.search")` (R14). The
resource-literal assertions of §6 item 3b deliberately pin the literals in test
source, which is where they belong.

### 4.2 Icon assets

Directory: `freeplane_plugin_graph/src/main/resources/images/` (the existing
`GraphWorkspace.svg`, `GraphGroup.svg` live there and prove the plugin resource
path, `GraphPluginIntegrationShould.java:213-215`).

| File | Glyph | Required content properties |
| --- | --- | --- |
| `GraphSelect.svg` | Filled pointer | Root `<svg>` with a `viewBox` (the existing assets use a 24×24 grid; any square grid is acceptable); single `#333` filled path (e.g. `fill="#333"` / `style="fill:#333"`). |
| `GraphConnect.svg` | Two endpoint circles joined by a line | Root `viewBox`; `#333` stroke on the joining line and `#333` on the circles. |
| `GraphSettings.svg` | Gear outline | Root `viewBox`; `#333` stroke. |
| `GraphSearch.svg` | Magnifier | Root `viewBox`; `#333` stroke. |

Monochrome requirement: every painted element uses `#333` (or `#333333`) in a
form matched by the Look-and-Feel replacement rules `#333"`, `#333;`,
`#333333` (`freeplane/src/viewer/resources/freeplane.properties:744-750`), and
no other colour. Then `?useAccentColor=true` rewrites the whole glyph to
`${Label.foreground}` exactly as `freeplane/src/viewer/resources/images/undo.svg`
(`style="fill:#333"`) does today.

Exact lookup strings passed to `ResourceController.getOptionalIcon` (no pixel
size; `freeplane/src/main/java/org/freeplane/core/resources/ResourceController.java:390-435`,
`freeplane/src/main/java/org/freeplane/features/icon/factory/IconFactory.java:42-43`
for `toolbar_icon_height`):

- `/images/GraphSelect.svg?useAccentColor=true`
- `/images/GraphConnect.svg?useAccentColor=true`
- `/images/GraphSettings.svg?useAccentColor=true`
- `/images/GraphSearch.svg?useAccentColor=true`

### 4.3 Control names and row composition

| Surface | Name |
| --- | --- |
| Switch wrapper | `graph-workspace-tool-switch` |
| Select | `graph-workspace-select` (unchanged) |
| Connect | `graph-workspace-connect` (unchanged) |
| Search | `graph-workspace-search` (unchanged) |
| Settings | `graph-workspace-settings` (unchanged) |
| Undo / redo / zoom-in / zoom-out / direction / fit / reset / pin | unchanged |

`approvedControlNames()` keeps its 14 entries (`WorkspaceToolbar.java:69-71`:
`open, save, add-map, remove-map, select, connect, direction, search, settings,
zoom-in, zoom-out, fit-graph, reset-zoom, pin`); the 13 direct children are the
row listed in §3.3.5 (the maps-panel names `add-map`/`remove-map` are not row
children). Row position and the 42 px height are unchanged.

### 4.4 Enablement and gear-state matrix

| Control | Normal | Read-only | Source / rule |
| --- | --- | --- | --- |
| `selectButton` | enabled | enabled | never disabled; read-only Connect click re-selects Select (`WorkspaceToolbar.java:304-308`) |
| `connectButton` | enabled | disabled | `WorkspaceToolbar.java:357` |
| `searchField` | enabled | enabled | not touched by enablement |
| `settingsButton` (gear) | enabled | enabled | toolbar sets `!readOnly` (`:358`), window force-enables `true` after (`GraphWorkspaceWindow.java:994,1014`) |
| `directionComboBox` | enabled | disabled | `WorkspaceToolbar.java:362` |
| `open` | enabled | enabled | never disabled: `WorkspaceToolbar.java:352-363` does not touch `openButton` (`:49`), and `openWorkspace()` (`:290-295`) has no read-only guard |
| `save` / `saveAs` | enabled | disabled | `WorkspaceToolbar.java:353-354` |
| `undo` / `redo` | history + `!readOnly` | disabled | `WorkspaceToolbar.java:355-356` |
| `pin` | `!readOnly && pinEnabled` | disabled | `WorkspaceToolbar.java:359-361` |
| zoom / fit / reset | enabled | enabled | unchanged |

Gear selected state (`isSelected()`):

| Transition | Required result |
| --- | --- |
| construction | `gear.isSelected() == settingsPanel.isVisible()` (both `true`; sync in §3.4) |
| gear click | `gear.isSelected() == settingsPanel.isVisible()` (both flip) |
| `View → Settings` item | same as gear click (delegates to `doClick()`) |
| panel visibility changed directly by code, then action runs | equality holds; the action reads the panel and pushes to the gear |
| `setReadOnly(true/false)` | equality unchanged; gear stays enabled |

## 5. Error and edge behaviour — testable cases

| # | Situation | Required observable | Proven by |
| --- | --- | --- | --- |
| E1 | Icon asset missing/unresolvable (Select, Connect, Settings) | `getIcon() == null`; `getText()` is the label-key value (never the long tooltip key); `getToolTipText() == null`; accessible name unset; `doClick()` still fires the tool/settings listener. No control is blank. | test 3 |
| E2 | Search asset missing | No stub-colour pixels; the prompt is still painted at `getInsets().left`; the field still accepts text. | test 5 (no-icon variant) |
| E3 | Prompt and magnifier both unavailable (null/empty prompt, null icon) | The field paints nothing extra; `getText()` still tracks edits; no exception. | tests 4, 5 |
| E4 | Field empty and focused | Prompt pixels (`getDisabledTextColor()`) == 0; magnifier stub pixels > 0; glyph x/y unchanged. | test 5 (focus case, non-headless) |
| E5 | Field has text | Magnifier stub pixels > 0; prompt pixels == 0; first text pixel x >= glyph x + icon width (text begins right of the glyph). | test 5 |
| E6 | Read-only session | Connect disabled; Select and gear enabled; `gear.isSelected() == settingsPanel.isVisible()`; Connect glyph uses the LaF's disabled treatment (real-app evidence). | tests 6, 7; §7.5 |
| E7 | Panel hidden/shown programmatically before the action runs | The action derives the new value from `settingsPanel.isVisible()`, so gear and panel cannot diverge. | test 7 |
| E8 | Unscaled (default) margin | Glyph x == `getInsets().left - margin.left` == the border lead; typed text starts at `getInsets().left` == glyph x + iconWidth + `GAP`. | test 5 |
| E9 | LaF that scales the content margin (FlatLaf `flatlaf.uiScale` / font-derived scale) | Non-overlap still holds exactly (glyph right edge stays `GAP` left of the text origin); the glyph may sit a few pixels inside its reserved slot. Reviewed in the HiDPI real-app check, not unit-pinned (the slot width is LaF state the field does not own). | §7.5 |
| E10 | `toolbar_icon_height` / font large enough to exceed the default | Field preferred height grows to the glyph rule (`max(26, iconHeight + margin.top + margin.bottom)`), so the field does not clip its glyph; the residual is the pre-existing 42 px row height alone. | test 5; §7.5 |
| E11 | Look-and-Feel without `"JButton.buttonType"` support | Two adjacent plain toggles; exactly one selected; the minimum accepted with the user (option A); no custom painting is added to compensate. | test 1; §7.5 |

## 6. Test specification

Unit tests are JUnit 4 + AssertJ. Items 1–9 live in
`GraphWorkspaceWindowModelShould` (existing `Fixture` and `EdtResources` static
mock scope) except item 3b and the two source guards of items 1 and 8b, which
live in `GraphPluginIntegrationShould`.
Item 5's focus case and its 40×40 height assertion use fresh
`GraphSearchField` instances in/without a shown frame; the render and pixel
cases use the model's field. The named test methods below are fixed so
the verification tasks can reference them.

Common fixture facts: `Fixture.model()` (`GraphWorkspaceWindowModelShould.java:2128-2132`)
constructs the real `GraphWorkspaceWindowModel` on the EDT (`:2144-2159`);
`fixture.stubIcon(path, icon)` registers icon lookups (`:2119-2122`);
`TextUtils.getText` returns the key itself (`:129-139`), so unit assertions
compare keys, and item 3b pins the literals.

1. **Switch composition** — `groupsSelectAndConnectInOneToolSwitch`
   - Fixture: `fixture(..., false)`.
   - Assert: `selectButton.getParent() == connectButton.getParent()` and the
     parent is a `ToolSwitch`; the parent's `GridLayout` has rows 1, columns 2,
     hgap 0, vgap 0; `getModel().getGroup()` is one non-null instance shared by
     both models with exactly those two elements; `approvedControlNames()`
     equals the 14-name set; the toolbar has exactly 13 direct children with
     exactly one `ToolSwitch`, whose children are `selectButton`, `connectButton`
     in that order.
   - Falsifier: adding the toggles directly to the toolbar fails the parent
     assertion; a non-zero gap fails the layout assertion; removing/adding a
     child fails the 13-count; a leftover toolbar-local `ButtonGroup` is caught
     by a source guard in `GraphPluginIntegrationShould` asserting that
     `WorkspaceToolbar.java` contains no `new ButtonGroup` (group identity alone
     cannot see a group that no model references).
2. **Icon resolution** — `resolvesTheToolbarAffordanceIconsThroughTheSharedRoutine`
   - Fixture: `fixture(..., false)` with `stubIcon` for the four exact paths.
   - Assert: for Select/Connect/Settings `getIcon()` is the stub,
     `getText() == null`, `getToolTipText()` equals
     `graph_workspace.tool.select` / `graph_workspace.tooltip.connect` /
     `graph_workspace.tooltip.settings`, the accessible name equals the same
     key, and `getName()` is unchanged; `verify(fixture.resourceController())`
     called `getOptionalIcon` for all four exact paths; the search field's
     tooltip and accessible name equal `graph_workspace.tooltip.search`
     unconditionally.
   - Falsifier: swapping label/tooltip keys fails the equality; a missing lookup
     fails `verify`; leaving text set fails `getText() == null`.
3. **Text fallback** — `keepsToolbarAffordanceTextFallbackWhenIconsDoNotResolve`
   - Fixture: `fixture(..., false)` with no icon stubs; install recording
     listeners via `toolbar().setToolListener(...)` and
     `toolbar().setSettingsAction(...)`.
   - Assert: Select text `graph_workspace.tool.select`, Connect text
     `graph_workspace.tool.connect`, Settings text
     `graph_workspace.action.settings` (label keys, not tooltip keys); each
     `getIcon() == null` and `getToolTipText() == null`; `selectButton.doClick()`
     records `InteractionTool.SELECT`, `connectButton.doClick()` records
     `CONNECT`, `settingsButton.doClick()` runs the settings action once (not
     `isEnabled()` alone).
   - Falsifier: using the tooltip key as fallback text fails the text
     assertion; leaving the deleted `:123` line makes the Settings tooltip
     non-null; a disabled fallback button fails the click assertions.
3b. **Resource values** — in `GraphPluginIntegrationShould`:
    `shipsTheGraphToolbarAffordanceResourceKeys`
    (mirrors `shipsTheFourRecentWorkspaceResourceKeys`, `:444`)
    - Fixture: `properties("freeplane/src/viewer/resources/translations/Resources_en.properties")`.
    - Assert: `graph_workspace.tooltip.connect` ==
      `Connect — create a cross-map relationship` (a real em dash after
      `Properties.load`), `graph_workspace.tooltip.settings` == `Graph settings`,
      `graph_workspace.tooltip.search` == `Search nodes and maps`, and
      `graph_workspace.tool.select` == `Select`,
      `graph_workspace.tool.connect` == `Connect`,
      `graph_workspace.action.settings` == `Settings`.
    - Falsifier: any missing/renamed/wrong value fails (`null` for a missing
      key).
4. **Prompt is not content** — `keepsTheSearchPromptOutOfTheDocument`
   - Fixture: `fixture(..., false)`; recording
     `Consumer<String>` installed with `toolbar().setSearchListener(...)`.
   - Assert: after construction `searchField().getText().isEmpty()` and the
     recorder is empty; after `setText("Alpha")` the recorder received exactly
     `["Alpha"]` and `getText()` is `"Alpha"`; after `setText("")` the recorder
     received `["Alpha", ""]`.
   - Falsifier: a constructor `setText(prompt)` fails the empty-text/empty
     recorder assertions; a removed document listener fails the recorder
     assertions; the prompt itself can never appear in `getText()`.
5. **Prompt and magnifier visibility proven from paint output** —
   `paintsSearchPromptAndMagnifierFromLiveState` (headless-safe) and
   `hidesSearchPromptWhileFocused` (non-headless,
   `Assume.assumeFalse(GraphicsEnvironment.isHeadless())`)
   - Fixture: `fixture(..., false)` with a painting `Icon` stub for
     `/images/GraphSearch.svg?useAccentColor=true` (fixed size 16×16, paints a
     unique colour and records the last `paintIcon` x/y); the height assertion
     uses **a fresh `GraphSearchField`** carrying the prompt and a second,
     deliberately taller stub (40×40) — the model's field keeps the 16×16 stub,
     because `Fixture.stubIcon` has one icon per path and the toolbar resolves
     the path once — so the height rule is exercised above the 26 px floor
     without disturbing the x/y and pixel assertions; plus, for
     the focus case, a fresh `GraphSearchField` configured with the same prompt
     and stub in a shown `JFrame` (local replication of the
     `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvasShould.java:486-529` shown-frame + `waitForFocus`
     pattern; the helper is private and typed to `GraphCanvas`, so it cannot be
     reused).
   - Assert (empty, unfocused; size the field first — `completeInitialLayout()`
     never sizes it (`GraphWorkspaceWindow.java:938-947`), so set
     `field.setSize(field.getPreferredSize())` before painting, or the height is
     0 and no pixel assertion can hold — then render it to a `BufferedImage`):
     recorded x == `field.getInsets().left - field.getMargin().left`; recorded
     y == `(field.getHeight() - stub.getIconHeight()) / 2`; stub-colour pixels
     > 0; pixels equal to `field.getDisabledTextColor()` > 0;
     `field.getPreferredSize().height == 26` with the 16×16 stub and
     `field.getPreferredSize().height == 40 + margin.top + margin.bottom`
     (strictly greater than 26) with the 40×40 stub — the assertion a
     hard-coded 26 px cannot satisfy; and `width == max(160, insets.left +
     fm.stringWidth(prompt) + insets.right)`.
   - Assert (empty, focused): stub-colour pixels > 0; disabled-text-colour
     pixels == 0; the recorded x/y equal the field-under-test's own
     `(getInsets().left - getMargin().left)` and
     `(getHeight() - stubHeight) / 2` — the fresh frame may size the field
     differently from the model's, so the expectation is derived from the field
     under test rather than copied.
   - Assert (text `"Results"`): stub-colour pixels > 0; disabled-text-colour
     pixels == 0; the first pixel whose colour is the field's foreground colour
     or an anti-aliased blend of it against the background, scanned only inside
     the text baseline band (`y` from `getInsets().top` to `getInsets().top +
     fm.getHeight()`) so border and focus painting are excluded, has
     x >= recorded x + stub width.
   - Falsifier: a cached prompt predicate fails the focus/empty transition;
     painting the glyph at `getInsets().left` fails the recorded-x assertion;
     dropping the margin rule makes text overlap the glyph; ignoring
     `setPromptIcon` fails the stub-pixel assertions; a hard-coded 26 px height
     fails the 40×40 height assertion. No stored flag is
     queried — all evidence is paint output or recorded paint coordinates (R7).
6. **Enablement preserved** — `preservesToolbarEnablementInReadOnlySessions`
   - Fixture: `fixture(..., false)` and `fixture(..., true)`.
   - Assert: normal — select/connect/settings/search/direction enabled;
     read-only — select enabled, connect disabled, settings enabled, direction
     disabled; existing undo/redo/zoom/pin rules unchanged (extends
     `GraphWorkspaceWindowModelShould.java:318-348`).
   - Falsifier: disabling settings in read-only fails; enabling Connect in
     read-only fails; disabling Select fails.
7. **Gear state** — `keepsTheSettingsGearSynchronizedWithThePanel`
   - Fixture: `fixture(..., false)`.
   - Assert: after construction panel visible and gear selected;
     `settingsButton().doClick()` → both false;
     `menuItem(model, "settings").doClick()` (helper
     `GraphWorkspaceWindowModelShould.java:2032`) → both true; then
     `settingsPanel().setVisible(false)` (gear still selected) and
     `settingsButton().doClick()` → panel true and gear true; after
     `model.setReadOnly(true)` equality holds and gear is enabled.
   - Falsifier: without the construction sync the first assertion fails; a
     no-op `setSettingsVisible` fails the direct-hide case (the click would
     leave the gear unselected while the panel is visible); a click-derived
     action fails the same case; clearing selection in read-only fails the last
     assertion.
8. **Tool switching** — `switchesToolsThroughTheSharedButtonGroup`
   - Fixture: `fixture(..., false)`; recording tool listener.
   - Assert: `connectButton.doClick()` → connect selected, select unselected,
     listener received `CONNECT`; then `selectButton.doClick()` → select
     selected, connect unselected, listener received `SELECT` (order
     `CONNECT`, `SELECT`).
   - Falsifier: a switch that only looks grouped fails the listener
     assertions; a group allowing both selected fails the selection
     assertions.
8b. **Grouped styling** — `stylesOnlyTheSwitchSegmentsWhenIconsResolve`
    - Fixture: `fixture(..., false)` with **all eight** toolbar icon stubs — the
      four new paths plus `/images/undo.svg?useAccentColor=true`,
      `/images/redo.svg?useAccentColor=true`,
      `/images/ZoomIn24.svg?useAccentColor=true`,
      `/images/ZoomOut24.svg?useAccentColor=true` — so the other icon controls
      hold resolved icons while the negative is checked; and a second
      `fixture(..., false)` with no stubs at all.
    - Assert: with the eight stubs, `selectButton.getClientProperty("JButton.buttonType")`
      and `connectButton.getClientProperty("JButton.buttonType")` both equal
      `"toolBarButton"`, while the gear, undo, redo, zoom-in and zoom-out return
      null for the same key **with their own icons resolved** (without those four
      stubs the negative would hold vacuously); with no stubs, neither segment
      carries the property — the fallback case.
    - Source guard in `GraphPluginIntegrationShould`:
      `read("freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceToolbar.java")` contains the
      literal `putClientProperty("JButton.buttonType", "toolBarButton")` and
      does not contain `com.formdev.flatlaf`; `verifyGraphBundle`
      (`freeplane_plugin_graph/build.gradle:82-106`, `check.dependsOn` at
      `:109`) fails the build if the bundle gains an `Import-Package`.
    - Falsifier: the property on the gear/undo/redo/zoom fails the negative
      assertion; a FlatLaf constant fails the source-literal guard; styling on
      fallback fails the no-icon assertion.
9. **Search behaviour unchanged**
   - Existing suites, no new test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphSearchModelShould`
     (owner map-name match at
     `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphSearchModel.java:108-110`;
     assertions at
     `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphSearchModelShould.java:43-93`),
     `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceCommandAcceptanceShould.java`
     (search assertions at `:320`, `:437-439`),
     `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java`
     (search-match painting at `:750-755`) and item 4's listener assertions.
   - Assert: a non-matching query still produces an empty match set and the
     canvas dims rather than removes non-matching nodes.
   - Falsifier: filtering nodes instead of dimming, or changing the owner
     map-name match, fails those existing tests.

Evidence and integration tests (items 10 and 11) and the real-app item 12 are
specified in §7.3–§7.5.

## 7. Verification commands and evidence artefacts

Use `gradle` with escalation and Java 21 from
`~/.sdkman/candidates/java/21.0.8-zulu` (AGENTS.md).

### 7.1 Test task

`gradle :freeplane_plugin_graph:test`

Evidence: the plugin suite is green, including the switch, prompt-paint,
fallback, resource-literal and gear-state tests above. `verifyGraphBundle` is
wired into `check` (`freeplane_plugin_graph/build.gradle:109`).

### 7.2 OSGi smoke (design test item 11)

`gradle :freeplane_plugin_graph:graphOsgiSmoke`

Required change in `GraphPluginOsgiSmoke.assertGraphBundleContents`
(`freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/smoke/GraphPluginOsgiSmoke.java:206-216`):
for each of `images/GraphSelect.svg`, `images/GraphConnect.svg`,
`images/GraphSettings.svg`, `images/GraphSearch.svg`, require
`bundle.getResource(name) != null` and read non-empty bytes from the returned
URL. Do **not** use `bundle.getEntry(...)`: the graph bundle is installed from
the exploded directory `BIN/plugins/org.freeplane.plugin.graph`, whose root holds
only `lib/` and `META-INF/`; the SVGs live in `lib/plugin-<version>.jar` and are
reachable only through `Bundle-ClassPath` (`freeplane_plugin_graph/build.gradle:88-89`).

Because `bundle.getResource` does not exercise `ResourceController`'s
resource-loader path, the smoke also asserts one end-to-end resolution: with the
plugin ACTIVE, `ApplicationResourceController.getResourceController()
.getOptionalIcon("/images/GraphSelect.svg?useAccentColor=true")` must return a
non-null icon, which also exercises the plugin classloader registration
(`GraphModeExtension.java:40`).

Evidence: the smoke completes and reports the graph bundle contents. Falsifier: a
missing or mis-packaged asset fails `getResource(name) != null` (or the
non-empty-bytes read); using `getEntry` would false-fail even with a correctly
shipped asset because it searches only the archive root.

### 7.3 UI-evidence task (design test item 10)

`gradle :freeplane_plugin_graph:graphUiEvidence` (task
`freeplane_plugin_graph/build.gradle:151-165`)

Required changes in
`freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/smoke/GraphWorkspaceUiEvidence.java`:

1. Replace the non-painting `mock(Icon.class)` placeholder (`:132-140`) with a
   `BufferedImage`-backed painting `Icon` returned for all eight toolbar paths:
   the four new ones plus `/images/undo.svg?useAccentColor=true`,
   `/images/redo.svg?useAccentColor=true`,
   `/images/ZoomIn24.svg?useAccentColor=true`,
   `/images/ZoomOut24.svg?useAccentColor=true`.
2. `verify(controller).getOptionalIcon(<path>)` for the four new paths; assert
   Select, Connect and Settings hold non-null icons with `getText() == null`.
3. Render the search field directly with empty text **before
   `dispatchInteractions`**, so the `"Alpha"` typed at `:415` is left untouched
   and the desktop capture keeps its typed-text state; assert prompt pixels
   (field's disabled text colour) plus magnifier stub pixels. The direct field
   render is what proves the prompt, because the committed desktop image shows
   the magnifier beside typed text.
4. Record to stdout and to `build/graph-ui-evidence/row-width.txt`:
   `toolbar.getLayout().preferredLayoutSize(toolbar).width` and the widths of
   `selectButton`, `connectButton`, the `ToolSwitch` wrapper, the search field
   and the gear. Never use `toolbar.getPreferredSize()` (pinned to 0 at
   `WorkspaceToolbar.java:43,95`). The file format fixed by this specification
   is one `label=<int>` line per measurement in this order:
   `toolbar.layoutPreferredWidth`, `select.width`, `connect.width`,
   `toolSwitch.width`, `search.width`, `settings.width`.
5. Add `rootProject.file('build/graph-ui-evidence/row-width.txt')` to
   `graphUiEvidence`'s `outputs.files` (`build.gradle:162-164`), matching the
   task's `rootDir` working directory; the writer creates the directory. Because
   that task declares `outputs.upToDateWhen { false }` (`:152`), Gradle does not
   validate the declared file, so the harness must assert that
   `row-width.txt` exists and parses as the six ordered `label=<int>` lines
   above; otherwise a silently missing width record would pass the evidence run.
6. Keep the existing recursive `assertNoOverlap`
   (`GraphWorkspaceUiEvidence.java:539-569`, called at `:340` and `:464`) as the
   containment check; add no new clipping assertion.
7. Regenerate `docs/superpowers/specs/images/2026-08-10-graph-workspace-implemented.png`
   (path kept; args declared at `build.gradle:160-161`).

Evidence: the regenerated desktop image (switch with painted glyphs, gear,
typed-text magnifier), the direct field render assertions, and
`build/graph-ui-evidence/row-width.txt` with the layout-required width. The
harness runs under the JVM default Look-and-Feel rather than FlatLaf, so the
image carries geometry/text/painted glyphs but not FlatLaf's accent or
selected-segment colours; those are covered by §7.5. Falsifier: an unstubbed new
path fails `verify(getOptionalIcon(path))` and leaves the control with a null
icon (failing the non-null-icon assertion); a text fallback fails
`getText() == null`; a missing or malformed width file fails the harness's
existence/parse assertion for the declared output (an undeclared file is caught
only by reviewing `build.gradle`); a toolbar subtree escaping its parent or
overlapping a sibling fails `assertNoOverlap`.

### 7.4 Translation formatting

After editing `freeplane/src/viewer/resources/translations/Resources_en.properties`:

```
gradle format_translation
cd freeplane/src/viewer/resources/translations
file Resources_*.properties | grep -v "ASCII text"     # expect no output
```

`format_translation` sorts the file and owns the final key order
(`freeplane/format_translation.gradle:9-28`; sorter and its ordering
`freeplane_ant/src/main/java/org/freeplane/ant/FormatTranslation.java:220-247`, `Collections.sort` at `:221`).
The em dash is `\u2014` so the file stays ASCII before and after formatter runs.

### 7.5 Real-application checks (X11, design test item 12)

Run the built application on X11 and record screenshots/notes in the task's
completion report. Cover:

1. Select/Connect switching, their grouped rendering and tooltips; the active
   segment visible without hover.
2. Light and dark Look-and-Feels: glyph legibility/contrast and accent
   replacement (no leftover `#333`).
3. Default 16 pt and one non-default `toolbar_icon_height`: the search field
   grows with the glyph instead of clipping it; the 42 px row residual is
   unchanged (pre-existing).
4. HiDPI scale (including the scaled-margin case E9): the glyph sits inside its
   reserved slot and never overlaps typed text.
5. Read-only Connect glyph legibility and the switch's active state across a
   read-only transition; gear enabled and reflecting panel visibility.
6. Search prompt show/hide and dimming behaviour; gear open/close from both the
   gear and `View → Settings`; the three tooltips/accessible names.

Falsifier: any check that shows a wrong/unreadable glyph, no accent adaptation
in light or dark, a clipped magnifier, overlapping glyph/text at HiDPI, a
visibly undimmed disabled Connect glyph, or a gear state that does not match the
panel is a review blocker. In particular, if the LaF does not dim the disabled
Connect glyph, the design is amended rather than shipped silently (design §9).

## 8. Design questions

None. Every R1–R16 requirement in the approved design is realisable as
specified, and the design's code references were re-read and verified against
`1ca81c35a1`; no design/code disagreement was found. The few silences are
mechanical and fixed by this specification, not open questions:

- test method names and their grouping (items 1–9 in
  `GraphWorkspaceWindowModelShould`, item 3b in `GraphPluginIntegrationShould`);
- the `row-width.txt` line format (`label=<int>`, §7.3);
- the parameter order of the new private helpers `iconToggle` and
  `applyToolbarSegmentStyle` (§9).

None of these changes behaviour, UI or the approved scope.

## 9. Interface digests

Signatures other tasks may depend on (all in
`org.freeplane.plugin.graph.window` unless stated):

```java
// new, package-private
final class ToolSwitch extends JPanel {
    ToolSwitch(final JToggleButton... segments);          // GridLayout(1, n, 0, 0); owns the ButtonGroup
    // no other members
}

// new, package-private
final class GraphSearchField extends JTextField {
    GraphSearchField();                                   // package-private; captures the LaF margin as baseMargin
    void setPrompt(final String prompt);                  // package-private, constructor-only; repaint (revalidate after layout)
    void setPromptIcon(final Icon icon);                  // package-private, constructor-only; margin + repaint
    private Insets currentMargin();                       // private: margin installed by setPromptIcon, else baseMargin
    @Override protected void paintComponent(final Graphics graphics);
    @Override public Dimension getPreferredSize();
}

// changed, package-private members of WorkspaceToolbar
final class WorkspaceToolbar extends javax.swing.JPanel {
    JTextField searchField();                             // unchanged; instance is a GraphSearchField (R6)
    AbstractButton settingsButton();                      // widened from JButton
    void setSettingsVisible(final boolean visible);       // selected state only

    private static JButton iconButton(final String textKey, final String name,
            final String iconPath);                       // unchanged signature; label == tooltip
    private static JToggleButton iconToggle(final String labelTextKey, final String tooltipTextKey,
            final String name, final String iconPath);
    private static void configureIcon(final AbstractButton button, final String labelTextKey,
            final String tooltipTextKey, final String iconPath);
    private static void applyToolbarSegmentStyle(final AbstractButton segment);
    // private static void toggleButton(...) is deleted
}

// GraphWorkspaceWindowModel: private void toggleSettingsPanel(); no public API change
```

Cross-task constants fixed by this specification:

| Interface | Value |
| --- | --- |
| Switch name | `graph-workspace-tool-switch` |
| Client property (raw strings) | key `JButton.buttonType`, value `toolBarButton` |
| `GAP` / `TRAILING_INSET` | `6` / `6` |
| Default field width floor / height floor | `160` / `26` |
| Regex-free prompt predicate | `getText().isEmpty() && !isFocusOwner()` |
| Glyph x / y | `getInsets().left - margin.left`, `(getHeight() - icon.getIconHeight()) / 2` |
| Prompt x / baseline | `getInsets().left`, `getInsets().top + FontMetrics.getAscent()` |
| Icon lookup strings | four `/images/Graph*.svg?useAccentColor=true` paths (§4.2) |
| Resource keys | §4.1, including the new `graph_workspace.tooltip.connect` with `\u2014` |
| Row-width evidence file | `build/graph-ui-evidence/row-width.txt` (declared output), `label=<int>` lines |
