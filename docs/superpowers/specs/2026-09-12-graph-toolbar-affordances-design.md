# Graph Workspace Toolbar — Select, Connect, Search & Settings Affordances — Design

- Date: 2026-09-12
- Status: Collaborative design approved by the user (option B, wording 3, switch styling option A); reviewed to zero blockers over design-review rounds 1–5
- Ticket: none — Task Identifier: `2026-09-12-graph-toolbar-affordances`
- Scope: `freeplane_plugin_graph` (delivery branch `plugin/graph-workspace`)
- Mockup: `docs/superpowers/specs/images/2026-09-12-graph-toolbar-affordances-mockup.png`
- Design reviewer reports: `$STATE_ROOT/reports/design-review-1.md` … `-5.md` (round 5: zero blockers)
- Switch-styling probe evidence: `.superpowers/brainstorm/1753507-1789176239/artifacts/flatlaf-probe/` (renders, probe source, README) and the companion screen `.superpowers/brainstorm/1753507-1789176239/content/switch-chrome-v2.html`

## 1. Background

The Graph Workspace window's single-row toolbar (`WorkspaceToolbar`) lays out 14
named controls as 14 direct children (open, save, undo, redo, select, connect,
direction, search, settings, zoom-out, zoom-in, fit, reset, pin) in one
`FlowLayout` row pinned to 42 px (`WorkspaceToolbar.java:43,95`), added at
`GraphWorkspaceWindow.java:1044`; after this change Select and Connect share one
`ToolSwitch`, so the row has 13 direct children.
Four in-scope controls diverge from the user interface approved on 2026-08-10
(`.superpowers/brainstorm/935968-1786332691/content/graph-workspace-gui-design.html`,
reproduced in the user's report); the table also carries the out-of-scope
Direction row so the comparison is complete:

| Control | Approved 2026-08-10 mockup | Shipped today |
| --- | --- | --- |
| Select | icon-only pointer glyph, tooltip `Select` | text toggle `Select` (`WorkspaceToolbar.java:56`) |
| Connect | icon-only endpoint glyph, marked **active** in the mockup, tooltip `Create relationship` | text toggle `Connect` (`WorkspaceToolbar.java:57`) |
| Direction | three-segment labelled control | 128 px `JComboBox` — **out of scope** |
| Search | magnifier and the placeholder `Search nodes and maps` inside the field | bare 160×26 `JTextField`, no glyph, no placeholder, tooltip `Search graph` (`WorkspaceToolbar.java:60,120-122`) |
| Settings | gear glyph, tooltip `Graph settings`, right-aligned | text button `Settings` (`WorkspaceToolbar.java:61,123`) |

The mockup also gave the settings panel a `×` close control; that is deliberately
not built here (§3), and the panel stays openable only from the toolbar/menu. The
`Connect marked **active**` cell in the table describes that 2026-08-10 mockup;
the newly rendered `2026-09-12-graph-toolbar-affordances-mockup.png` shows Select
active instead.

The divergence is a documented scope carve-out, not a later decision: the
2026-09-11 icon design (`docs/superpowers/specs/2026-09-11-graph-toolbar-icons-design.md`
§3) listed `Select`, `Connect` and `Settings` as explicit non-goals and converted
only undo, redo, zoom-out and zoom-in to icons.

Evidence that these four controls are the weakest part of the row:

1. **Row pressure.** The 42 px `FlowLayout` has no wrapping strategy; the
   2026-09-11 design (§1) records that at 1920 px the last control wrapped into a
   clipped, unreachable second row. Text labels are pure width cost against that.
2. **Search affordance.** `GraphSearchModel` matches node and enclosure labels
   **and the owning map name** (`GraphSearchModel.java:108-110`), so the approved
   placeholder `Search nodes and maps` states the index truthfully, while the
   current tooltip `Search graph` hides it and the empty field states nothing.
3. **Settings discoverability.** The settings panel
   (`WorkspaceSettingsPanel`, added at `GraphWorkspaceWindow.java:1040`) starts
   **visible** and has no close affordance of its own: both the gear and the
   `View → Settings` item (`GraphWorkspaceWindow.java:1093`, which calls
   `settingsButton().doClick()`) route through the single action at
   `GraphWorkspaceWindow.java:458-463`. The control therefore owns state the user
   cannot otherwise read.
4. **Machinery already exists.** `ResourceController.getOptionalIcon` (with
   `?useAccentColor=true`) resolves SVG through `IconFactory` at the
   `toolbar_icon_height` preference, with the L&F colour replacements in
   `freeplane.properties:744-750`, and `WorkspaceToolbar.iconButton(...)`
   (`WorkspaceToolbar.java:371-383`) already uses it with a text fallback.

## 2. Goals

1. Give `Select`, `Connect`, `Search` and `Settings` the approved presentation
   without disturbing the rest of the row.
2. Make the search box state what it searches, in-place.
3. Make the settings panel's open/closed state visible on the control that owns it.
4. Reuse the existing icon pipeline: scalable SVG, preference-driven height,
   theme-adaptive colour, text fallback, no hardcoded pixel sizes.
5. Keep every existing control identity, enablement rule, listener, menu item,
   shortcut and the current search semantics.

## 3. Non-Goals

- The direction `JComboBox`, `Fit Graph`, `Reset Zoom`, `Pin Node`, the maps
  panel, the status bar and the canvas zoom treatment stay exactly as shipped.
- Right-aligning search and settings, or grouping them at the row's end.
- Any change to search semantics (it still dims non-matching nodes rather than
  filtering) or to search result computation.
- Any new settings control (for example the mockup's `Node size`, `Link
  thickness`, `Label visibility` sliders) and no `×` close control on the panel.
- Renaming the settings panel's own heading (`graph_workspace.settings.heading`
  is `Display`, `Resources_en.properties:930`); the toolbar tooltip uses the
  user-approved `Graph settings` while the panel keeps its heading.
- Converting the `View → Settings` menu item into a check-box item: it stays a
  plain item that delegates to the same action, so the gear remains the only
  surface that shows the panel's state.
- Custom chrome for the switch or the search field (no painted frame, radius,
  divider, or pill outline), and no new hardcoded colour anywhere in Java.
- Fixing the toolbar's row-height/overflow strategy: this change reduces row
  width, but the wrapping defect itself stays open and is only *measured* here.

## 4. Requirements

| ID | Requirement |
| --- | --- |
| R1 | `Select` and `Connect` render as icon-only `JToggleButton`s inside one `ToolSwitch` that owns their `ButtonGroup`. Exactly one is selected at all times, and the toolbar's local `ButtonGroup` (`WorkspaceToolbar.java:99-101`) is removed with the switch as its single owner. Each segment additionally carries the Look-and-Feel's toolbar-button client property when its icon resolves, so the pair renders as one grouped switch (R16). |
| R2 | Control names are unchanged: `graph-workspace-select`, `graph-workspace-connect`, `graph-workspace-search`, `graph-workspace-settings`. `approvedControlNames()` (`WorkspaceToolbar.java:69-71`) keeps its 14 entries; the row keeps 13 direct children (Select and Connect now live inside the switch). |
| R3 | Icons, labels, tooltips and accessible names come from one shared routine `configureIcon(button, labelTextKey, tooltipTextKey, iconPath)`: `labelTextKey` always supplies the button text; when the icon resolves, the text is cleared and the tooltip **and** accessible name are set from `tooltipTextKey`; when the icon does not resolve the text stays, and the tooltip and accessible name are left unset — the behaviour the existing tests already pin (`GraphWorkspaceWindowModelShould.java:242-268` for the resolved case and `294-309` for the fallback). Per control: Select `label = tooltip = graph_workspace.tool.select`; Connect `label = graph_workspace.tool.connect`, `tooltip = graph_workspace.tooltip.connect` (new, `Connect \u2014 create a cross-map relationship`); Settings `label = graph_workspace.action.settings`, `tooltip = graph_workspace.tooltip.settings` (value changed to `Graph settings`); the four existing icon buttons keep `labelTextKey == tooltipTextKey` (their current action keys) so their tests stay green. `WorkspaceToolbar.java:123` (`settingsButton.setToolTipText(...)`) is deleted, so a missing gear asset leaves the gear without a tooltip — the same rule as undo/redo/zoom, and a change confined to that fallback path. |
| R4 | Text fallback per control: Select shows `Select`, Connect shows `Connect`, Settings shows `Settings` — the current labels, never the long tooltip string. The search field paints the prompt from `graph_workspace.tooltip.search` (value changed to `Search nodes and maps`), which is also its tooltip and accessible name, set unconditionally because the field is not icon-dependent. |
| R5 | Four glyphs are resolved through `ResourceController.getOptionalIcon` with `?useAccentColor=true` and no pixel size: `/images/GraphSelect.svg` (pointer), `/images/GraphConnect.svg` (endpoints), `/images/GraphSettings.svg` (gear), `/images/GraphSearch.svg` (magnifier), sized by `toolbar_icon_height`. |
| R6 | The search control stays a single `JTextField` subclass, so the evidence harness keeps finding it by name and casting it to `JTextField` (`GraphWorkspaceUiEvidence.java:398,415`), and `searchField().getText()` remains the only query channel. |
| R7 | The magnifier is **persistent**: it is painted in every field state whenever an icon resolves, as the approved mockup's typed-text state shows. The prompt is painted only while the field is empty **and** unfocused. The prompt predicate is evaluated at paint time from live state (`getText().isEmpty() && !isFocusOwner()`), never from a cached flag. Layout is explicit: the glyph occupies the leading space reserved by the content margin and is painted inside that space with `GAP` before the text origin (`getInsets().left - <the margin installed by setPromptIcon>.left`), while typed text and the prompt start at `getInsets().left` — because the Look-and-Feel text border folds the margin into the insets, painting the glyph at `getInsets().left` would put it under the first character. The preferred height follows the glyph like every other icon control: `max(26, iconHeight + margin.top + margin.bottom)`, recomputed when the icon is set, so a `toolbar_icon_height` above the default cannot clip it. |
| R8 | The prompt is never field content: `getText()` returns `""` while only the prompt shows, and the document listener fires only for real edits. |
| R9 | The settings gear is a `JToggleButton`, and `gear.isSelected() == settingsPanel.isVisible()` holds after construction (the panel starts visible, so the gear starts selected) and after every mutation path: the gear click, the `View → Settings` item, and `setReadOnly` (`GraphWorkspaceWindow.java:993-996,1014`). One method owns the update: it reads `settingsPanel.isVisible()` first and then pushes that value to the gear, so the gear follows the panel and never the click. |
| R10 | Enablement is unchanged: Select always enabled; Connect disabled in read-only (`WorkspaceToolbar.java:357`); Settings stays enabled in read-only, as the window force-enables it after `setReadOnly`; the direction control and every other control keep their current rules. |
| R11 | No behaviour outside presentation changes: listeners, tool selection, search computation, keyboard shortcuts, menu items and status text are untouched; toolbar buttons stay non-focusable via `configure(...)` (`WorkspaceToolbar.java:391-395`) as today. |
| R12 | Row composition and order are unchanged apart from the two toggles sharing one `ToolSwitch`; the switch occupies the same row position and the 42 px row height is unchanged. |
| R13 | No new hardcoded UI colour and no custom-painted chrome: the switch never paints and the search field keeps its Look-and-Feel border, background and focus painting, painting only the magnifier (accent pipeline) and the prompt text in the field's own `getDisabledTextColor()`. The single Look-and-Feel hint used is R16's documented client property, which styles the segments and paints no colour of its own. |
| R14 | Translation values live in `freeplane/src/viewer/resources/translations/Resources_en.properties`; the em dash is written as `\u2014` and `gradle format_translation` is run. |
| R15 | The evidence run records the toolbar's layout-required width (`toolbar.getLayout().preferredLayoutSize(toolbar).width`, never `getPreferredSize()`, which is pinned to 0 at `WorkspaceToolbar.java:43,95`) plus the width of each in-scope control (both segments, the `ToolSwitch` wrapper, the search field, the gear), writing them to stdout and to `build/graph-ui-evidence/row-width.txt` (declared in `graphUiEvidence`'s `outputs.files`), and relies on the existing recursive `assertNoOverlap` containment check (`GraphWorkspaceUiEvidence.java:539-569`, called at 340 and 464) instead of a new clipping assertion. |
| R16 | Grouped-switch styling is the Look-and-Feel's own, chosen by the user after a rendered probe (`switch-chrome-v2.html`, option A): exactly `selectButton` and `connectButton` receive the documented client property `"JButton.buttonType" = "toolBarButton"`, set as a raw string in one helper (`applyToolbarSegmentStyle`), so no `com.formdev.flatlaf` import, no new build or OSGi dependency, and no custom painting are introduced. It is applied when that segment's icon resolved; when an icon does not resolve (fallback) the segment keeps the plain bordered toggle so the fallback label reads as an ordinary button. Every other control — the gear, undo/redo/zoom, fit, reset, pin — is never styled this way. On a Look-and-Feel that does not know the property, the segments degrade to plain adjacent toggles — the accepted minimum for this feature, recorded in §9. |

## 5. Approved UI and recorded deviations

Mockup (approved in the collaborative design session):
`docs/superpowers/specs/images/2026-09-12-graph-toolbar-affordances-mockup.png`

Three treatments were presented in the visual companion; the user chose **B**:

- **A — icon-only standalone toggles** (exactly the 2026-08-10 mockup).
- **B — grouped tool switch (chosen):** the same two glyphs adjacent in one
  switch, so "exactly one mode is active" is visible without hovering.
- **C — conservative:** keep the two text labels, change only Settings and the
  search box. Rejected: it drops the part of the approved mockup the user
  singled out.

Wording was chosen from three candidates; the user chose **3**: Select `Select`;
Connect `Connect — create a cross-map relationship`; Settings `Graph settings`;
search prompt/tooltip/accessible name `Search nodes and maps`.

Two decisions the PM made and put to the user, plus the styling question this design
reopened after rendering it, all three accepted:

1. **The gear shows its state** (`JToggleButton`), because the panel it toggles has
   no close affordance of its own.
2. **The text fallback stays**: a missing asset degrades to today's labels.
3. **The grouped switch is styled by the Look-and-Feel, not by us** (option A,
   R16): toolbars that know the client property render the active segment as a
   filled segment; others render two adjacent plain toggles.

Deviations from the mockup that this design accepts deliberately, because
building them would mean inventing chrome and violating R13:

| Mockup | Design | Why |
| --- | --- | --- |
| Single rounded outline around the two segments, blue frame | The two segments carry the Look-and-Feel's own toolbar-button treatment (option A): rendered with the bundled FlatLaf 3.7.2 at the design's zero-gap adjacency, the active segment becomes a filled segment and the inactive one a plain glyph, instead of two separate rounded rectangles | A painted outline would need a colour the Look-and-Feel does not expose portably; its own grouping is the honest equivalent and paints nothing. |
| Rounded search pill with a muted grey magnifier | The field keeps its LaF (rectangular) border and focus painting; the magnifier and prompt are painted inside it; the magnifier uses the accent pipeline like every other glyph | Same reason: no invented chrome; the accent colour is the only theme-adaptive source available for a vector glyph. |

## 6. Architecture

### 6.1 `ToolSwitch` (new, package-private, `window` package)

Owns exactly two responsibilities: the toggle group and the adjacency of the
segments. It paints nothing.

```java
final class ToolSwitch extends JPanel {
    ToolSwitch(final JToggleButton... segments) {
        setName("graph-workspace-tool-switch");
        setOpaque(false);
        setLayout(new GridLayout(1, segments.length, 0, 0));   // zero gap
        final ButtonGroup group = new ButtonGroup();
        for (final JToggleButton segment : segments) {
            group.add(segment);
            add(segment);
        }
    }
}
```

- The toolbar keeps the `selectButton` / `connectButton` fields, their listeners
  (`WorkspaceToolbar.java:145-146`), `selectButton.setSelected(true)`
  (`WorkspaceToolbar.java:98`) and the read-only enablement; the local
  `ButtonGroup` at `WorkspaceToolbar.java:99-101` is deleted with the switch as
  its single owner (legacy-removal policy, AGENTS.md).
- Segment painting stays the Look-and-Feel's own `JToggleButton` painting — no
  new colour (R13).
- **Decided (option A):** rendering the bundled FlatLaf 3.7.2 showed that two
  plain adjacent toggles paint two separate rounded rectangles, so the grouped
  look is produced by R16's client property instead:
  `segment.putClientProperty("JButton.buttonType", "toolBarButton")`, applied by
  `WorkspaceToolbar.applyToolbarSegmentStyle(AbstractButton)`, called exactly
  twice — on `selectButton` and `connectButton` in the toolbar constructor — and
  never from a shared helper, so the gear, undo/redo/zoom, fit, reset and pin keep
  their current appearance (the property name is a raw string: no
  `com.formdev.flatlaf` import, no new dependency, no custom painting). The probe
  renders — including the design's zero-gap adjacency — are archived in
  `.superpowers/brainstorm/1753507-1789176239/artifacts/flatlaf-probe/`, with the
  companion screen `.superpowers/brainstorm/1753507-1789176239/content/switch-chrome-v2.html`.
- `ToolSwitch` therefore stays purely structural: it owns the `ButtonGroup` and
  the zero-gap adjacency and touches no styling.

### 6.2 `GraphSearchField` (new, package-private, `window` package)

A `JTextField` subclass that paints the two in-field affordances:

```java
final class GraphSearchField extends JTextField {
    private static final int GAP = 6;
    private static final int TRAILING_INSET = 6;
    private Insets baseMargin;                       // captured in the constructor, null-tolerant

    void setPrompt(final String prompt) { ... }     // constructor-only, repaint only (R8)
    void setPromptIcon(final Icon icon) { ... }     // constructor-only; sets the margin, invalidates layout (R4, R7)

    private Insets currentMargin() {                 // the margin setPromptIcon installed
        final Insets margin = getMargin();
        return margin != null ? margin : baseMargin;
    }

    @Override
    protected void paintComponent(final Graphics graphics) {
        super.paintComponent(graphics);
        final Icon icon = promptIcon;
        if (icon != null) {                                              // persistent glyph (R7)
            // the margin (folded into getInsets() by the L&F text border) reserves
            // iconWidth + GAP ahead of the text, so the glyph sits inside that space,
            // GAP before the text origin — never at the text origin itself
            final int glyphX = getInsets().left - currentMargin().left;
            icon.paintIcon(this, graphics, Math.max(0, glyphX),
                (getHeight() - icon.getIconHeight()) / 2);
        }
        if (!getText().isEmpty() || isFocusOwner()) {
            return;                                                      // live predicate (R7)
        }
        // draw the prompt in getDisabledTextColor() at getInsets().left on the
        // font baseline (getInsets().top + FontMetrics.getAscent()), where typed
        // text would start
    }

    @Override
    public Dimension getPreferredSize() {
        // max(160, insets.left + promptWidth + insets.right)
        //   x max(26, iconHeight + margin.top + margin.bottom)
        // (the glyph already lives inside insets.left through the margin)
    }
}
```

- Chosen over a wrapper panel holding a `JLabel` plus a borderless field: one
  component means no focus-traversal change, no accessible-name plumbing across a
  container, the harness's `findNamed`/`JTextField` cast keeps working (R6), and
  `getText()`/`setText()` semantics are untouched.
- `setPromptIcon` captures the field's original margin as `baseMargin` in the
  constructor (null-tolerant), then applies
  `setMargin(new Insets(baseMargin.top, icon.getIconWidth() + GAP, baseMargin.bottom, TRAILING_INSET))`
  for an icon and restores `baseMargin` for `null`, so the rule is reversible and
  typed text starts after the glyph. Both setters are **constructor-only** (R8,
  §6.3), so no later layout path exists; if either is ever called after layout it
  must also call `revalidate()`.
- Preferred size is the field's own (`getPreferredSize()`): width
  `max(160, getInsets().left + promptWidth + getInsets().right)` — the insets come
  from the active Look-and-Feel, and because they already include the reserved
  glyph space there is no second `iconWidth + GAP` term (that would over-allocate
  the field by one glyph plus the gap) — and height
  `max(26, iconHeight + margin.top + margin.bottom)`, which keeps the glyph
  unclipped above the default `toolbar_icon_height`. The toolbar's hard-coded
  `setPreferredSize(new Dimension(160, 26))` (`WorkspaceToolbar.java:122`) is
  therefore deleted, or it would win and clip the approved prompt. The row
  therefore grows by the prompt's measured width and shrinks by three text labels;
  both are recorded by the evidence run (R15) rather than asserted from memory.
- The magnifier is resolved through the same icon pipeline (R5) and is optional.
- The field keeps the Look-and-Feel border, background and focus painting (R13).

### 6.3 `WorkspaceToolbar` changes

- One shared private routine `configureIcon(final AbstractButton button, final String labelTextKey,
  final String tooltipTextKey, final String iconPath)` implements R3; both
  `iconButton(...)` (for `JButton`) and the new `iconToggle(...)` (for
  `JToggleButton`) delegate to it, so the icon lookup, fallback, tooltip and
  accessible-name rules exist once. Styling is **not** part of that routine:
  `iconToggle(...)` produces a plain icon-only toggle for any caller, and
  `applyToolbarSegmentStyle(AbstractButton)` is called exactly twice — on
  `selectButton` and `connectButton` — checks `getIcon() != null` itself, and is
  the only place that sets the property, so the gear, which is also built by
  `iconToggle(...)`, never receives R16's property.
- `selectButton` / `connectButton` become `iconToggle(...)` calls with their
  label and tooltip keys; both are added to the toolbar as one `ToolSwitch`
  (replacing the `add(...)` calls at `WorkspaceToolbar.java:129-130`).
- `searchField` becomes a `GraphSearchField`; the fixed
  `setPreferredSize(new Dimension(160, 26))` at `WorkspaceToolbar.java:122` is
  deleted in favour of the field's content-derived preferred size (§6.2), and
  `setPrompt(TextUtils.getText("graph_workspace.tooltip.search"))` /
  `setPromptIcon(...)` are fed from
  `/images/GraphSearch.svg?useAccentColor=true` — the prompt text is never a
  literal in code (R4, R14).
- `settingsButton` becomes a `JToggleButton` built by `iconToggle(...)` with
  label `graph_workspace.action.settings` and tooltip
  `graph_workspace.tooltip.settings`, and `WorkspaceToolbar.java:123` is deleted
  (R3). The accessor's return type widens to `AbstractButton`: its callers are
  `setEnabled(...)` at `GraphWorkspaceWindow.java:994,1014`, `doClick()` at
  `GraphWorkspaceWindow.java:1093`, and `isEnabled()` at
  `GraphWorkspaceWindowModelShould.java:941`. It gains
  `void setSettingsVisible(boolean visible)`, a dumb state push that only sets the
  selected state (§6.4).
- `toggleButton(...)` (`WorkspaceToolbar.java:385-389`) becomes unused and is
  deleted (legacy-removal policy).
- `updateReadOnlyControls()` (`WorkspaceToolbar.java:352-363`) keeps its rules and
  must not touch the gear's selected state.

### 6.4 `GraphWorkspaceWindow` wiring

```java
// once, after createContent(): the panel starts visible (GraphWorkspaceWindow.java:1040)
toolbar.setSettingsVisible(settingsPanel.isVisible());

private void toggleSettingsPanel() {              // the single owner of the update (R9)
    settingsPanel.setVisible(!settingsPanel.isVisible());
    toolbar.setSettingsVisible(settingsPanel.isVisible());   // panel-derived, not click-derived
}
// toolbar.setSettingsAction(this::toggleSettingsPanel);
```

`setSettingsVisible` is the dumb push (selection state only) shared by the
construction sync and `toggleSettingsPanel`; the read, the toggle and the refresh
all happen in `toggleSettingsPanel`, so no second place can derive state from the
click. The panel itself, its heading and `createContent()` are unchanged, and the
panel gets no `×` (Non-Goal).

### 6.5 Assets (new)

`freeplane_plugin_graph/src/main/resources/images/`:

- `GraphSelect.svg` — filled pointer, single `#333` path.
- `GraphConnect.svg` — two endpoint circles joined by a line, `#333` stroke.
- `GraphSettings.svg` — gear outline, `#333` stroke.
- `GraphSearch.svg` — magnifier, `#333` stroke.

`#333` is the placeholder the L&F replacement rules rewrite
(`freeplane.properties:744-750`), so the glyphs follow light, dark and FlatLaf
themes exactly like `/images/undo.svg?useAccentColor=true` does today. The
plugin's existing `/images/GraphWorkspace.svg` proves that plugin-shipped SVGs
resolve through the same resource path
(`GraphPluginIntegrationShould.java:201,215`).

## 7. Error Handling

| Situation | Behaviour |
| --- | --- |
| Icon asset missing or unresolvable | R3/R4 fallback: the label instead of the glyph (Select/Connect use `graph_workspace.tool.*`, Settings uses `graph_workspace.action.settings`), tooltip and accessible name left unset exactly like today's icon buttons — including the gear, whose unconditional tooltip at `WorkspaceToolbar.java:123` is deleted; the search field paints the prompt without a magnifier. No control is ever blank. |
| Prompt and magnifier both unavailable | The field paints nothing extra and remains a working search field. |
| Field focused while empty | Prompt hidden, magnifier stays; typing shows text; clearing text re-shows the prompt once focus leaves. |
| Field has text | Magnifier stays painted; prompt absent; typed text begins at the content margin, i.e. right of the glyph (R7). |
| L&F font or `toolbar_icon_height` large enough to exceed the 42 px toolbar row | Both the field's width and its height follow the measurement (R7, R15), so the field itself does not clip; the residual is the row height alone — the pre-existing limit recorded in §9. |
| Read-only session | Select/Connect/Settings enablement exactly as today; the gear stays enabled and still reflects panel visibility. Connect's glyph is painted in the LaF's disabled treatment — the real-app read-only check (§10 item 5) is the evidence for its legibility. |
| Settings panel toggled from the `View` menu | It calls `settingsButton().doClick()`, i.e. the same action, so the gear's state follows (R9). |
| Panel made visible/hidden by code before the action runs | The action derives the new value from `settingsPanel.isVisible()`, so gear and panel cannot diverge (R9). |

## 8. Test Strategy

Unit tests (JUnit 4 + AssertJ, existing `GraphWorkspaceWindowModelShould` fixture
and its `EdtResources` scope):

1. **Switch composition** — both toggles have the same parent, that parent is a
   `ToolSwitch`, the two segments are adjacent with zero layout gap, the same
   `ButtonGroup` instance is shared by their models (`button.getModel().getGroup()`
   returns one group for both), and no other group holds the two (R1, R12);
   `approvedControlNames()` is unchanged (its 14 names include
   `add-map`/`remove-map`, which live in the maps panel, not in this row) and the
   toolbar row has 13 direct children, exactly one of which is the `ToolSwitch`
   (R2).
2. **Icon resolution** — with the mocked `ResourceController` returning a stub
   `Icon` for the four exact paths (`WorkspaceToolbar.java:371-383` pattern):
   each control has a non-null icon, `getText()` is null, the tooltip and
   accessible name equal the expected keys (the literals are pinned by test 3b),
   and `getName()` values are unchanged (R2, R3, R5).
3. **Text fallback** — with the lookup returning null: Select/Connect/Settings
   keep their label keys (`graph_workspace.tool.select`, `graph_workspace.tool.connect`,
   `graph_workspace.action.settings`) instead of the long tooltip key, their
   tooltips are null (the existing icon-button fallback rule), and they stay
   clickable — asserted through the observable (`doClick()` still notifies the
   tool listener), not through `isEnabled()` alone (R3, R4).
3b. **Resource values** — a `GraphPluginIntegrationShould`-style assertion reads
   `Resources_en.properties` and pins the new and changed literals:
   `graph_workspace.tooltip.connect` = `Connect — create a cross-map relationship`
   (em dash), `graph_workspace.tooltip.settings` = `Graph settings`,
   `graph_workspace.tooltip.search` = `Search nodes and maps`, while
   `graph_workspace.tool.select`, `graph_workspace.tool.connect` and
   `graph_workspace.action.settings` keep their existing values (R14, §5). The
   unit fixture mocks `TextUtils` to return keys, so tests 2/3 assert keys and
   this test pins the literals.
4. **Prompt is not content** — the field's text stays `""`, the search listener
   receives nothing until a real edit, and the document listener still fires for
   `setText` (R6, R8).
5. **Prompt and magnifier visibility are proven from paint output** — render the
   field to a `BufferedImage` with a painting icon stub and assert: empty and
   unfocused → magnifier pixels **and** prompt pixels; focused or non-empty →
   magnifier pixels only, no prompt pixels; and in the non-empty case the first
   text pixel lies at or right of the magnifier's right edge, so typed text cannot
   overlap the glyph (R7). The focused case replicates the shown-frame +
   `waitForFocus` pattern locally (`AccessibleGraphCanvasShould.java:486-529` is
   `private static` and typed to `GraphCanvas`, so it cannot be reused) and is
   guarded by `Assume.assumeFalse(GraphicsEnvironment.isHeadless())` as
   `GraphWorkspaceWindowModelShould.java:1040,1063,1087` already does; its absence
   from headless runs is accepted while the empty/non-empty assertions run
   everywhere. No stored flag may be queried (R7).
6. **Enablement preserved** — Connect disabled in read-only and enabled otherwise;
   Select always enabled; Settings enabled in read-only; direction control rules
   unchanged (R10).
7. **Gear state** — after construction the gear is selected because the panel is
   visible; a gear click flips both; the `View`-menu path flips both; calling the
   action after the test hides the panel directly still ends with
   `gear.isSelected() == settingsPanel.isVisible()`, which pins "follows the
   panel, not the click"; a state push after `setReadOnly` does not desynchronise
   them (R9, R10).
8. **Tool switching** — clicking Connect then Select moves the selection each way
   and the tool listener receives `CONNECT` then `SELECT`, so the switch is proven
   to drive behaviour rather than only to look grouped (R1, R11).
8b. **Grouped styling** — with a stubbed icon, `selectButton` and `connectButton`
   each carry `"JButton.buttonType" = "toolBarButton"` (read back with
   `getClientProperty`), and with the lookup returning null neither carries it, so
   the property is pinned to the resolved-icon case and the fallback stays a plain
   bordered toggle (R16). The same test asserts the negative: the gear, undo, redo,
   zoom-in and zoom-out carry no such property. The no-import rule is guarded by
   `verifyGraphBundle` (`freeplane_plugin_graph/build.gradle:88-96` asserts the
   bundle has no `Import-Package`), which fails if the plugin ever links FlatLaf,
   plus a source-text assertion in the `GraphPluginIntegrationShould.read(...)`
   style that the property is written as a literal, not through a FlatLaf constant.
9. **Search behaviour unchanged** — existing search tests keep passing; a
   non-matching query still dims rather than removes (R11).

Evidence and integration:

10. `:freeplane_plugin_graph:graphUiEvidence` — replace the non-painting
    `mock(Icon.class)` stub (`GraphWorkspaceUiEvidence.java:132-140`) with a
    `BufferedImage`-backed painting `Icon` for all eight toolbar icon paths: the
    four new ones plus the four pre-existing core ones, which paint nothing today
    and leave blank buttons in the committed image. Assert with
    `verify(controller).getOptionalIcon(<path>)` that the four new paths are
    requested and that the three controls hold non-null icons with null text;
    render the search field directly with empty text and assert prompt plus
    magnifier pixels (the desktop capture types `Alpha` before painting, so it
    shows the magnifier beside typed text, not the prompt — no `compactText`
    mapping can change that, so that claim is dropped from this design); record
    the layout-required row width to stdout and
    `build/graph-ui-evidence/row-width.txt` (declared with `rootProject.file(...)`,
    matching the task's `rootDir` working directory); assert that Select, Connect
    and Settings hold painted glyphs while the magnifier is covered by the direct
    field render; and regenerate
    `docs/superpowers/specs/images/2026-08-10-graph-workspace-implemented.png`
    (the path is kept, so `freeplane_plugin_graph/build.gradle:151-166` needs no
    rewiring, and the image is regenerated with the new toolbar). The harness runs
    headless with stubbed icons, so glyph shapes remain gated by the unit tests
    and the real-app check.
11. `:freeplane_plugin_graph:graphOsgiSmoke` — proves the four new SVGs are
    visible to the bundle at runtime, the failure mode classpath asset additions
    historically hit: the bundle is installed from an exploded directory whose
    root holds only `lib/` and `META-INF/`, with the images inside
    `lib/plugin-<version>.jar` reachable through `Bundle-ClassPath`, so the check
    must be `bundle.getResource("images/<name>.svg") != null` (plus reading its
    bytes) rather than `bundle.getEntry(...)`, which searches only the archive
    root (`GraphPluginOsgiSmoke.java:206-216`).
12. Real application on X11: glyph legibility and contrast in light and dark
    Look-and-Feels, at the default 16 pt and one non-default `toolbar_icon_height`
    (the search field must grow with the glyph instead of clipping it, while the
    42 px row itself stays the pre-existing residual), at HiDPI scale — including
    the scaled-margin case where the glyph sits a few pixels inside its reserved
    slot (§9) — with the read-only Connect glyph, the tooltips, and the switch's
    active state after read-only transitions.

## 9. Risks

| Risk | Mitigation |
| --- | --- |
| Icon-only modes are less obvious than words | Grouped switch shows exclusivity; tooltips and accessible names carry the wording; the text fallback and the unchanged menus remain as backstops. |
| Plugin-scoped SVG assets do not resolve in the running bundle | `graphOsgiSmoke` plus the real-app check; the fallback keeps the toolbar usable if it fails. |
| Accent replacement does not apply to the new assets | Assets use the `#333` placeholder the replacement rules target; the real-app check covers both themes; a non-adapting asset is a review blocker, not an accepted residual. |
| `GraphSearchField` painting breaks the harness's `JTextField` cast | The control stays a `JTextField` subclass and keeps its name (R6); the evidence run exercises it. |
| The evidence harness's icon stubs paint nothing (today's image shows blank icon buttons) | Test 10 switches to a painting stub for all eight paths, so the regenerated image is readable and the new controls cannot pass by accident. |
| Prompt text mistaken for a value | Prompt is painted, never set (R8), with a paint-output test (test 5). |
| Gear `JToggleButton` churn breaks the accessor's callers | Accessor type widened to `AbstractButton`; its only callers use `setEnabled`/`doClick`; the test at `GraphWorkspaceWindowModelShould.java:941` keeps compiling. |
| Gear/panel divergence at startup or after programmatic panel changes | Construction sync plus panel-derived action (R9), pinned by test 7. |
| Search field grows by the prompt's width | Width rule is explicit (§6.2), the row width is measured and recorded, and the three removed labels outweigh it at default metrics. |
| Disabled Connect glyph may look like the enabled one | No assumption made: the real-app read-only check (§10 item 5) is the evidence, and if the LaF does not dim the icon, the design is amended rather than silently shipped. |
| A Look-and-Feel that does not know the client property shows two adjacent plain toggles instead of the grouped treatment | Accepted minimum, decided with the user (option A, R16): the exclusivity is still visible through the selected segment, and no custom painting is introduced to compensate. The bundled FlatLaf, which the application uses, honours it. |
| Row height still clips at large `toolbar_icon_height` | Pre-existing and explicitly out of scope; the residual is recorded rather than silently inherited. Distinct from the field's own sizing, which follows the glyph (R7). |
| A Look-and-Feel that scales the content margin (FlatLaf with `flatlaf.uiScale` or a font-derived scale factor) widens the reserved glyph slot beyond `iconWidth + GAP`, so the glyph sits a few pixels inside the slot instead of flush at its leading edge | Non-overlap still holds exactly — the glyph's right edge stays `GAP` left of the text origin — and the default configuration (Metal's unscaled margin, Freeplane's `sun.java2d.uiScale=1` on Linux) is exact. The scaled case is reviewed in §10 item 5 and pinned by the HiDPI check rather than by a unit assertion, because the width of the slot is Look-and-Feel state the field does not own. |

## 10. Verification Plan

1. `gradle :freeplane_plugin_graph:test` — full plugin suite green, including the
   switch, prompt-paint and gear-state tests.
2. `gradle :freeplane_plugin_graph:graphOsgiSmoke` — the four assets resolve
   inside the bundle through its class path (`bundle.getResource`, §8 item 11).
3. `gradle :freeplane_plugin_graph:graphUiEvidence` — regenerated screenshot
   reviewed for the switch, prompt and gear, with the recorded row width. The
   harness renders with the JVM's default Look-and-Feel rather than FlatLaf, so
   the image carries the controls' geometry, text and painted glyphs but not
   FlatLaf's accent or selected-segment colours; those are checked in item 5.
4. `gradle format_translation` after the `Resources_en.properties` edit, then the
   encoding check from `freeplane/src/viewer/resources/translations/`
   (`file Resources_*.properties | grep -v "ASCII text"`).
5. Real-app walkthrough on X11 in both L&F themes covering: Select/Connect
   switching, their grouped rendering and tooltips, the read-only Connect
   appearance, search prompt show/hide and dimming behaviour, gear open/close
   from both the button and the `View` menu, and the switch's state across a
   read-only transition.
