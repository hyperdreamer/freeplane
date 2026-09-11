# Graph Workspace Toolbar — Scalable Icons & Pin Toggle — Design

- Date: 2026-09-11
- Status: Approved at frontier design review round 3 (zero blockers); post-review plan-determinism corrections applied
- Ticket: none — Task Identifier: `2026-09-11-graph-toolbar-icons`
- Scope: `freeplane_plugin_graph` (feature branch `feature/graph-workspace`)
- Mockup: `docs/superpowers/specs/images/2026-09-11-graph-toolbar-icons-mockup.png`

## 1. Background

The Graph Workspace window (`GraphWorkspaceWindow`) hosts a single-row toolbar
(`WorkspaceToolbar`) built from a `FlowLayout` inside a panel whose height is
pinned to 42 px (`WorkspaceToolbar.PREFERRED_SIZE`). The toolbar currently adds
15 text-labelled controls:

`Open`, `Save`, `Undo Workspace Change`, `Redo Workspace Change`, `Select`,
`Connect`, direction combo, search field, `Settings`, `Zoom Out`, `Zoom In`,
`Fit Graph`, `Reset Zoom`, `Pin Node`, `Unpin Node`.

The user reported, with a screenshot, that at a 1920 px window width this row
already overflows. `Unpin Node`,
the last control, wraps into a second `FlowLayout` row that the fixed 42 px panel
height clips, so it is invisible and unreachable in the running application. The
installed plugin artifact
(`BIN/plugins/org.freeplane.plugin.graph/lib/plugin-1.13.4.jar`, built 2026-09-11)
contains the control; it is a layout defect, not a missing feature.

Freeplane core already provides a scalable icon pipeline for exactly this
problem. `ResourceController.getIcon(path)` resolves the asset and delegates to
`IconFactory.getIcon(url, IconFactory.DEFAULT_UI_ICON_HEIGHT)`, which reads the
user preference `toolbar_icon_height` (default `16 pt`, editable as
**Preferences → Appearance → Toolbar icon height**). `SVGIconCreator` renders
the SVG from vector at that height, applies the light/dark Look-and-Feel colour
replacements when the path carries `useAccentColor=true`, and therefore stays
crisp at any UI/HiDPI scale. The four assets needed here already ship with the
core application: `/images/undo.svg`, `/images/redo.svg`,
`/images/ZoomIn24.svg`, `/images/ZoomOut24.svg`.

Pin state is a workspace document concept (`WorkspaceDocument.pins()`), projected
as `PinProjection` entries in `CanvasState.projection().pins()`; an active
projection with a present `projectedNode()` marks that node pinned. Two places
duplicate that query today as private helpers
(`GraphInteractionController.isPinned`, `AccessibleGraphCanvas.isPinned`), and
the new pin toggle adds a third caller in the window, so the design consolidates
them (§6.4).

Pin interaction today:

- Toolbar: two separate buttons, both always enabled when not read-only; both
  click handlers are silent no-ops when nothing is selected.
- Canvas left-drag from an unpinned node pins it at the drop point.
- Canvas right-click on a pinned node unpins it; right-click elsewhere inspects
  an edge (`GraphIntent.InspectEdge`).
- Status bar: `Unpin All` (unchanged by this design).

## 2. Goals

1. Replace the text of exactly four toolbar controls — `Undo Workspace Change`,
   `Redo Workspace Change`, `Zoom Out`, `Zoom In` — with scalable icons, and show
   the original full name as a hover tooltip.
2. Replace the two pin toolbar buttons with one context-sensitive toggle that
   reflects the selected node's pinned state.
3. Make canvas right-click a symmetric pin toggle for the node under the cursor.
4. Keep the icon rendering scalable and preference-driven, with no hardcoded
   pixel size.
5. Preserve all existing control identities, enablement contracts, keyboard
   shortcuts, menus, and the status-bar `Unpin All`.

## 3. Non-Goals

- Converting any other toolbar control (including `Select`, `Connect`,
  `Settings`, `Fit Graph`, `Reset Zoom`, `Pin Node`) to icons.
- Changing the File/Edit menu items, accelerators, or the status bar.
- Changing the left-drag-to-pin gesture or the `Unpin All` behaviour.
- Introducing a wrapping/two-row toolbar layout; the toolbar keeps its current
  `FlowLayout` and 42 px height.
- Adding new translation keys.

## 4. Requirements

| ID | Requirement |
| --- | --- |
| R1 | Exactly four toolbar buttons (`undo`, `redo`, `zoom-out`, `zoom-in`) render icon-only: no visible text, an icon instead. Every other control keeps its current text. |
| R2 | Each icon-only button shows its original full action label (`graph_workspace.action.undo_workspace`, `…redo_workspace`, `…zoom_out`, `…zoom_in`) as a hover tooltip. |
| R3 | Each icon-only button exposes the same label as its accessible name, and keeps its existing `getName()` value (`graph-workspace-undo`, `graph-workspace-redo`, `graph-workspace-zoom-out`, `graph-workspace-zoom-in`). |
| R4 | Icons are resolved through `ResourceController.getOptionalIcon` with the core resource paths `/images/undo.svg?useAccentColor=true`, `/images/redo.svg?useAccentColor=true`, `/images/ZoomIn24.svg?useAccentColor=true`, `/images/ZoomOut24.svg?useAccentColor=true`. No pixel size is passed by the toolbar; sizing comes from `toolbar_icon_height`, and the button's preferred size derives from the resolved icon's size. |
| R5 | If an icon cannot be resolved, the button keeps its text label and remains fully usable (no blank button). |
| R6 | Enablement of the four buttons is unchanged: undo/redo keep the `workspaceUndoAvailable` / `workspaceRedoAvailable` + read-only rules; zoom buttons are always enabled. |
| R7 | There is exactly one pin toolbar button. Its label always reflects the selected node's pinned state — `Pin Node` when the selected node is not pinned, `Unpin Node` when it is — independent of read-only mode. |
| R8 | `Unpin Node` never appears as a separate toolbar control; `approvedControlNames()` no longer contains `unpin`. |
| R9 | Clicking the pin button pins the selected node at its current geometry centre (existing behaviour) or unpins it when pinned; the action is a no-op in read-only sessions (existing `executePin` / `executeUnpin` guards). |
| R10 | The pin button label and enablement refresh on every selection change and every canvas-state change, so pin/unpin/undo/redo/`Unpin All` performed anywhere keep it truthful. |
| R11 | The pin button is disabled when the selection is empty, when an enclosure (non-node) is selected, and in read-only sessions. Disabling never changes the label: a disabled button with a selected pinned node still reads `Unpin Node`. |
| R12 | Canvas right-click on a node toggles its pin: pinned → `GraphIntent.Unpin(node)`; not pinned → `GraphIntent.Pin(node, centre.x, centre.y)`. |
| R13 | Right-click does not change the selection and does not emit `GraphIntent.ChangeSelection`. |
| R14 | Right-click that does not hit a node keeps its current behaviour (edge inspection over an edge, nothing otherwise). Each right-click gesture dispatches at most one context action. |
| R15 | Read-only sessions do not mutate pin state through right-click; the existing command-execution guards remain the enforcement point. |
| R16 | The pinned-state query used by the toolbar, the right-click gesture, and the existing accessibility code comes from one shared helper rather than a further copy. |
| R17 | No new translation keys are added; tooltips reuse the existing `graph_workspace.action.*` keys. |

## 5. Approved UI

Mockup (approved in collaborative design):
`docs/superpowers/specs/images/2026-09-11-graph-toolbar-icons-mockup.png`

- Row 1 shows the current all-text toolbar.
- Row 2 shows the approved treatment: `Undo`, `Redo`, `Zoom Out` and `Zoom In`
  are icon-only (the real SVGs rendered at the toolbar icon height — default
  `16 pt`, i.e. 16 px × the current UI scale factor; the mockup shows ≈ 21 px,
  matching the reported window's scale), with `Undo Workspace Change` illustrated
  as the hover tooltip. All other controls are unchanged.
- Row 3 shows the pin toggle states: `Pin Node` (selected node not pinned),
  `Unpin Node` (selected node pinned), disabled `Pin Node` (no node or an
  enclosure selected), and disabled `Unpin Node` (read-only session with a pinned
  node selected).

Design decisions recorded from the collaborative session:

1. Scope is exactly the four controls above (not the whole toolbar).
2. Pin control is one toggle; the standalone `Unpin Node` button is removed.
3. Icons are scalable SVG resolved through the app pipeline, not fixed 20/24 px
   rasters.
4. Hover tooltips show the original full names.
5. Right-click toggles pin on the node under the cursor, anchored at the node
   centre, leaving the selection unchanged.
6. The pin label always reflects the selected node's pinned state; read-only
   mode disables the control without changing its label.

## 6. Architecture

### 6.1 Icon-only buttons (`WorkspaceToolbar`)

The four buttons stop being plain text buttons and become icon buttons built by
a single helper, so the icon lookup, tooltip, accessible name, and fallback are
defined once:

```java
private static JButton iconButton(final String textKey, final String name,
        final String iconPath) {
    final JButton button = new JButton(TextUtils.getText(textKey));
    final Icon icon = ResourceController.getResourceController().getOptionalIcon(iconPath);
    if (icon != null) {
        button.setIcon(icon);
        button.setText(null);
        button.setToolTipText(TextUtils.getText(textKey));
        button.getAccessibleContext().setAccessibleName(TextUtils.getText(textKey));
    }
    configure(button, name);
    return button;
}
```

- Text fallback (R5) keeps the headless model and any resource-missing build
  usable; `getOptionalIcon` avoids a `severe` log for an absent asset.
- Existing `configure(...)` margins and `setName(...)` are reused unchanged.
- Button field names, accessors, listeners, and enablement logic are untouched.
- No `setPreferredSize`; the button takes the icon's size (R4).
- The change adds `javax.swing.Icon` to `WorkspaceToolbar` and
  `org.freeplane.plugin.graph.projection.PinProjection` to `GraphWorkspaceWindow`
  (absent from its projection import block at lines 70-79).

### 6.2 Pin toggle (`WorkspaceToolbar` + `GraphWorkspaceWindow`)

`WorkspaceToolbar` keeps one `pinButton` and gains a single state entry point:

```java
void setPinState(final boolean enabled, final boolean pinned) {
    pinEnabled = enabled;
    pinPinned = pinned;
    updateReadOnlyControls();
}
```

`updateReadOnlyControls()` applies the truth table (R7/R11); the label always
reflects the selected node's pinned state and only enablement is affected by
read-only mode:

| `readOnly` | `pinEnabled` | `pinPinned` | Label | Enabled |
| --- | --- | --- | --- | --- |
| true | any | false | `Pin Node` | no |
| true | any | true | `Unpin Node` | no |
| false | false | false | `Pin Node` | no |
| false | true | false | `Pin Node` | yes |
| false | true | true | `Unpin Node` | yes |

The `pinEnabled=false, pinPinned=true` combination cannot occur (`pinned` is
computed only for a selected node) and is therefore omitted.

`GraphWorkspaceWindow` (the cited methods live in the nested
`GraphWorkspaceWindowModel`, `GraphWorkspaceWindow.java:296`):

- `updateStatusBar()` (already invoked on selection change and on every accepted
  canvas state) computes:
  `final boolean nodeSelected = selectedNode != null && currentState != null;`
  `final boolean pinned = nodeSelected && PinProjection.isPinned(currentState.projection(), selectedNode);`
  `toolbar.setPinState(nodeSelected, pinned);`
  This covers the `currentState == null` case without a separate branch: the pin
  state is computed as disabled.
- The click action becomes one method:

```java
private void togglePinSelectedNode() {
    if (selectedNode == null || currentState == null) {
        return;
    }
    if (PinProjection.isPinned(currentState.projection(), selectedNode)) {
        executeUnpin(selectedNode);
        return;
    }
    final NodeGeometry geometry = currentState.geometry().nodes().get(selectedNode);
    if (geometry != null) {
        executePin(selectedNode, geometry.center().x(), geometry.center().y());
    }
}
```

- `executePin` / `executeUnpin` (persisted-reference and read-only guards) stay
  as they are.
- Wiring `toolbar.setPinAction(this::togglePinSelectedNode)` replaces the
  separate pin/unpin wiring.

### 6.3 Right-click toggle (`GraphInteractionController`)

`handleContext` becomes:

```java
if (endpoint.isPresent() && endpoint.get().isNode()) {
    final ProjectedNodeKey node = endpoint.get().node().get();
    if (PinProjection.isPinned(state.projection(), node)) {
        emit(new GraphIntent.Unpin(node));
    }
    else {
        final NodeGeometry geometry = state.geometry().nodes().get(node);
        if (geometry != null) {
            emit(new GraphIntent.Pin(node, geometry.center().x(), geometry.center().y()));
        }
    }
    return;
}
// unchanged: edge inspection when no node is hit
```

- Existing `contextGestureDispatched` / `contextHandled` guards keep at most one
  context action per gesture (R14).
- The `geometry != null` check is defensive: `GraphHitIndex.from` only indexes
  nodes present in `state.geometry().nodes()` (`GraphHitIndex.java:61-63`), so a
  hit node always has geometry; the branch is code-reviewed, not unit-tested.
- `NodeGeometry` needs an import in `GraphInteractionController` (today it is
  referenced fully qualified at `GraphInteractionController.java:556`).
- No `ChangeSelection` is emitted (R13).
- Behaviour change to note: a right-click that hits a node no longer falls
  through to edge inspection at that point. Edge inspection still works wherever
  no node is hit.
- Read-only enforcement stays in the command-execution guards (R15), as today.

### 6.4 Shared pinned lookup (R16)

Add one public static query next to the pin model:

```java
// PinProjection
public static boolean isPinned(final GraphProjection projection, final ProjectedNodeKey node)
```

It returns true only when an `active()` pin's `projectedNode()` equals `node`
(`active()` already implies a present projected node) — the exact semantics of
the two existing private copies:

- `GraphInteractionController.isPinned` and `AccessibleGraphCanvas.isPinned` are
  replaced by calls to the shared helper (duplicates deleted).
- `GraphWorkspaceWindow.updateStatusBar` uses it, so the toolbar never
  re-implements the loop.

### 6.5 Removals

Following the repository's legacy-removal policy:

- `WorkspaceToolbar.unpinButton` field, accessor, `add(...)` call, and listener
  (`WorkspaceToolbar.java:63, 133, 149, 229-231`).
- `WorkspaceToolbar.unpinAction` field and `setUnpinAction`
  (`WorkspaceToolbar.java:74, 269-271`), orphaned once the listener goes.
- `WorkspaceToolbar.approvedControlNames` entry `unpin` (`WorkspaceToolbar.java:66`).
- The `unpinButton.setEnabled(!readOnly)` line inside `updateReadOnlyControls`
  (`WorkspaceToolbar.java:358`), replaced by `setPinState`.
- `GraphWorkspaceWindowModel.pinSelectedNode` (`GraphWorkspaceWindow.java:1282-1291`)
  and `unpinSelectedNode` (lines 1293-1297) plus their `setPinAction` /
  `setUnpinAction` wiring (lines 468-469);
  `setPinAction(this::togglePinSelectedNode)` replaces both.
- The two duplicated private `isPinned` helpers
  (`GraphInteractionController.java:572-580`, `AccessibleGraphCanvas.java:518-525`).

Unchanged and explicitly retained: the `GraphIntent.Unpin` path (right-click and
`Unpin All`), `GraphCommands.unpin/unpinAll`, the `graph_workspace.action.unpin`
resource key (still used by the toggle label), and the status-bar `Unpin All`.

## 7. Error Handling

| Situation | Behaviour |
| --- | --- |
| Icon asset missing / `ResourceController` returns no icon | Button keeps its text label; tooltip/accessible-name work is skipped; no exception (R5). |
| Selected node has no geometry (e.g. still materialising) | The pin branch no-ops; a pinned selection still unpins (the `isPinned` check comes first, matching R9). |
| Right-clicked node has no geometry | Defensive only: `GraphHitIndex.from` cannot return a node absent from `state.geometry()`, so the §6.3 guard is unreachable in practice and is code-reviewed rather than unit-tested. |
| Selected node has no persisted reference | `executePin`/`executeUnpin` no-op, as today. |
| Enclosure or empty selection | Pin button disabled (R11); right-click on an enclosure continues to fall through to edge inspection. |
| Read-only session | Pin button disabled, with the label still reflecting the selected node's pinned state; any intent that still reaches the window is rejected by the existing `!readOnly` guards (R7, R15). |
| `currentState == null` | Pin state is computed as disabled; no exception. |

## 8. Test Strategy

Unit tests (JUnit 4 + AssertJ, existing `*Should` naming and Mockito static mocks
already used by `GraphWorkspaceWindowModelShould`):

1. **Icon rendering** — with the mocked `ResourceController` returning a fake
   `Icon` for the four paths: each button has a non-null icon, `getText()` is
   null, `getToolTipText()` and the accessible name equal the action label,
   `getName()` is unchanged, and the preferred widths track the stub icon widths
   (stub two paths with different widths and assert the corresponding buttons
   differ) rather than merely being positive (R1–R4). The lookup is verified for
   the exact four resource paths.
   Fixture plumbing: `GraphWorkspaceWindowModelShould` discards the mocked
   `ResourceController` instance (`thenReturn(mock(ResourceController.class))`,
   lines 122-123) and constructs models inside the EDT-scoped `EdtResources`
   block (lines 1770-1791), so the fixture must expose that instance (or install
   the icon stubs inside the same scope) before the toolbar is built.
2. **Text fallback** — with the mocked lookup returning null: each button keeps
   text, no icon, and stays clickable (R5).
3. **Enablement preserved** — undo/redo availability and read-only rules still
   drive the undo/redo buttons, and the zoom buttons stay enabled with no history
   and in read-only sessions (R6).
4. **Pin toggle truth table** — unpinned selection → `Pin Node` enabled; pinned
   state published → `Unpin Node` enabled; empty selection and enclosure
   selection → disabled `Pin Node`; read-only with an unpinned selection →
   disabled `Pin Node`; read-only with a pinned selection → disabled `Unpin Node`
   (the label still reflects the pinned state, R7/R10/R11).
5. **Pin toggle actions** — click on an unpinned selected node executes
   `GraphCommands.Pin` with the node-centre coordinates; click while pinned
   executes `GraphCommands.Unpin`; and publishing a state whose selected node has
   no geometry makes the click a no-op (reachable because `selectedNode` survives
   state publication, unlike the unreachable right-click geometry guard). The
   empty-selection no-command path is proven by invoking `togglePinSelectedNode`
   directly, not `doClick()`, because a disabled `AbstractButton` never fires its
   listeners.
6. **State refresh paths** — label flips after a published canvas state with an
   active pin, after `GraphIntent.Unpin` (right-click path), after
   `GraphIntent.UnpinAll`, and after undo/redo session-status updates (R10).
   Those intents only call `handle.execute(...)` and do not mutate
   `currentState`, so each case must publish the post-command canvas state (or
   use a state-mutating fake handle) before asserting the label; otherwise the
   test passes against a broken refresh path.
7. **No standalone unpin** — toolbar no longer exposes/contains an unpin button;
   `approvedControlNames()` is asserted with `doesNotContain("unpin")` (the
   existing `contains(...)` call at `GraphWorkspaceWindowModelShould.java:198-200`
   would otherwise fail). The enclosure click at line 1469 becomes a
   disabled-button click and is vacuous; test 4 carries the real assertion (R8).
8. **Right-click toggle** — right-click on an unpinned node emits
   `GraphIntent.Pin(node, centre.x, centre.y)`; the click point must be an
   interior point away from the node centre so the test distinguishes the node
   centre from the cursor position; on a pinned node emits
   `GraphIntent.Unpin(node)`; over an edge only, still `InspectEdge`; no
   `ChangeSelection` is emitted; one action per gesture (R12–R14).
8b. **Read-only pin intents** — in a read-only fixture with persisted node keys
   (as in test 5, not the transient `nodeState(...)` fixture),
   `acceptIntent(new GraphIntent.Pin(...))` and
   `acceptIntent(new GraphIntent.Unpin(...))` must not call `handle.execute(...)`,
   and the toggle stays disabled (R15). Persisted keys are required so the
   assertion fails when only the `!readOnly` guard is removed.
9. **Shared lookup** — active pin with a projected node matches; dormant pin and
   a non-matching key do not (R16).

Evidence and integration:

10. `:freeplane_plugin_graph:graphUiEvidence` must be updated to stub the four
    icon lookups with fixed-size placeholder icons and to assert the pin toggle
    states. Decision: keep the existing two image `args` / `outputs.files`
    (`freeplane_plugin_graph/build.gradle:151-165`) and overwrite the workspace
    screenshot with the new toolbar; the non-default pin states (`Unpin Node`,
    read-only disabled variants) are asserted programmatically inside the harness
    instead of adding a third screenshot, so build.gradle needs no change. This
    requires exposing the harness's `ResourceController` mock
    (`GraphWorkspaceUiEvidence.java:102-114`). Pin-state assertions need
    persisted keys in the evidence state (`twoMapState()` currently uses
    transient keys at lines 206-211, which `PinProjection.active` rejects), and
    `ModelAccess.invoke` needs an added parameter-type fallback for
    `acceptIntent(GraphIntent)` and `setReadOnly(boolean)` (lines 560-568,
    594-605), or the harness must call `setPinState(...)` reflectively. The task
    runs with
    `java.awt.headless=true`, and its non-blank/no-overlap assertions pass even
    with the text fallback, so R1's icon rendering is gated by unit test #1; the
    evidence run is the layout/overlap gate.
11. Real SVG glyph rendering, tooltips, dark-theme palette replacement, and
    HiDPI scaling are verified in the running application; the plugin test suite
    and a manual pin/unpin walkthrough (buttons, drag, right-click, undo,
    `Unpin All`) are part of implementation acceptance.
12. `UndoRoutingShould` (lines 120-121) asserts undo/redo button text and stays
    green only while an unstubbed lookup returns no icon (text fallback), so the
    icon tests must not install a global icon stub that breaks it. Existing
    tooltip-key assertions in `GraphPluginIntegrationShould` (lines 203-212)
    cover other actions and remain green; any assertion that names the removed
    standalone unpin button is updated as part of the change.

## 9. Risks

| Risk | Mitigation |
| --- | --- |
| Headless tests get `null` icons from the mocked `ResourceController` and silently assert nothing about icons | Tests explicitly stub the four lookups and assert icon/tooltip/accessible-name, plus a separate fallback test. |
| Evidence run cannot render real SVG glyphs headlessly | Evidence stubs fixed-size placeholder icons for geometry; real rendering is verified in the running app (accepted for this change, documented). |
| Removing the separate unpin button breaks existing tests that reference `unpinButton()`/`approvedControlNames()` | Those call sites are identified (`GraphWorkspaceWindowModelShould` lines ~198, ~645, ~1451) and updated with the toggle tests. |
| Right-click behaviour change surprises users who previously reached edge inspection through a node | Accepted: nodes and edges are distinct hit targets; edge inspection still works wherever no node is hit. |
| `toolbar_icon_height` larger than roughly 28–30 pt makes icon buttons taller than the fixed 42 px row, reintroducing clipping | Explicit accepted residual: the row height is a Non-Goal, the default 16 pt is unaffected, and the verification records the boundary with one larger value. A clamp or a derived row height is a documented follow-up candidate. |
| Right-click acceptance tests regress | `GraphWorkspaceCommandAcceptanceShould.scenario14` right-clicks world (0,0) expecting `InspectEdge` (right-click at line 326; `InspectEdge` assertion at lines 328-332); its fixture projects enclosures only, with no nodes, so it still falls through to edge inspection and stays green. |
| Pin label lags behind fast interactions | The single refresh point (`updateStatusBar`) already runs for both selection and canvas-state changes; the tests cover each path. |

## 10. Verification Plan

1. `gradle :freeplane_plugin_graph:test` — full plugin suite green.
2. `gradle :freeplane_plugin_graph:graphUiEvidence` — regenerated shell evidence
   reviewed for icon-only geometry, pin toggle states, and no overlap.
3. Running-app check on X11: tooltips show the four full names; icons render
   crisply at HiDPI scale; dark theme renders them legibly; `Pin Node`/`Unpin
   Node` track selection, drag-pin, right-click toggle, undo/redo, and `Unpin
   All`. `IconFactory.DEFAULT_UI_ICON_HEIGHT` is a class-load-time snapshot
   (`IconFactory.java:42-43`) with no change listener, so a `toolbar_icon_height`
   change applies on restart, exactly like the core toolbar; the verification
   changes the preference and restarts.
