# Graph Workspace Toolbar — Scalable Icons & Pin Toggle — Implementation Specification

- Date: 2026-09-11
- Task Identifier: `2026-09-11-graph-toolbar-icons` (no Ticket ID)
- Status: Specified for implementation planning; binding design is frontier review round 3 (zero blockers)
- Scope: `freeplane_plugin_graph` (branch `feature/graph-workspace`)
- Binding design: `docs/superpowers/specs/2026-09-11-graph-toolbar-icons-design.md`
- Approved mockup: `docs/superpowers/specs/images/2026-09-11-graph-toolbar-icons-mockup.png`
- Source revision: `50e87ba14a1b6273679fea994085e738b3510ffa` (`feature/graph-workspace`, 2026-09-11 15:01:12 +0800). Every line anchor below was verified against this revision.
- Repository conventions: Java 8 language level, 4-space indent, UTF-8, JUnit 4 `*Should` tests, AssertJ assertions, Mockito static mocks for `TextUtils`/`ResourceController`.

This specification is derived strictly from the approved design. It defines five production changes (one shared query, one toolbar rewrite, one window toggle, one interaction-controller rewrite, one call-site consolidation), the complete removal list from design §6.5, and thirteen new tests plus the existing-test changes and contract guards listed in §4.3. It adds no features, APIs, runtime flags, or translation keys beyond the approved design.

## 1. Purpose and Reading Guide

The Graph Workspace toolbar overflows its fixed 42 px single `FlowLayout` row at 1920 px window width; `Unpin Node`, the last control, wraps off the row and is clipped. The approved design replaces exactly four text buttons (`Undo Workspace Change`, `Redo Workspace Change`, `Zoom Out`, `Zoom In`) with preference-scaled SVG icons resolved through `ResourceController.getOptionalIcon`, and replaces the separate `Pin Node` / `Unpin Node` buttons with one context-sensitive pin toggle whose label always reflects the selected node's pinned state. Canvas right-click becomes a symmetric pin toggle at the hit node's geometry centre without changing the selection.

Requirement identifiers used in this specification:

- `P1`–`P5`: production changes (§2); `P6` is the signature, call-site and removal summary (§2.6).
- `E1`–`E9`: error and edge behavior (§3).
- `C1`–`C7`: existing tests that change or must stay green by contract (§4.3).
- `T1`–`T13`: new tests (§4.4), including the evidence-harness gate (§4.5).
- `V1`–`V5`: verification requirements (§6).
- Design requirements are referenced by their design IDs `R1`–`R17`; §8 maps each one to the spec sections and tests that discharge it.

Normative keywords: **must**, **must not**, **shall** are binding. “Byte-for-byte” code shapes are illustrative of behavior; incidental whitespace may differ.

## 2. Production Changes

All production changes are confined to `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/`. No other main source tree changes. No translation resource file changes.

### 2.1 P1 — Shared pinned query in `PinProjection`

File: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/PinProjection.java` (81 lines).

Add exactly this public static method between `dormant()` (current lines 48-50) and `x()` (current line 52):

```java
public static boolean isPinned(final GraphProjection projection, final ProjectedNodeKey node) {
    for (final PinProjection pin : projection.pins()) {
        if (pin.active() && pin.projectedNode().isPresent()
                && node.equals(pin.projectedNode().get())) {
            return true;
        }
    }
    return false;
}
```

Semantics (`R16`): returns `true` only when an active pin’s projected node is present and equal to `node`; returns `false` for dormant pins, other keys, and an empty pin list. This is the exact semantics of the two private copies being deleted (P4, P5), including the redundant `isPresent()` check that mirrors `active()`. No import is needed: `GraphProjection`, `ProjectedNodeKey`, and `PinProjection` are all in `org.freeplane.plugin.graph.projection`. No null checks are added; callers pass non-null state as today.

### 2.2 P2 — Icon-only buttons and single pin toggle in `WorkspaceToolbar`

File: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceToolbar.java` (379 lines).

#### 2.2.1 P2a — `iconButton` helper and the four icon fields

Add `import javax.swing.Icon;` after `import javax.swing.DefaultListCellRenderer;` (current line 19).

Add `import org.freeplane.core.resources.ResourceController;` before `import org.freeplane.core.util.TextUtils;` (current line 28).

Replace the four field initializers (current lines 50-51 and 58-59):

Before:

```java
private final JButton undoButton = button("graph_workspace.action.undo_workspace", "undo");
private final JButton redoButton = button("graph_workspace.action.redo_workspace", "redo");
...
private final JButton zoomInButton = button("graph_workspace.action.zoom_in", "zoom-in");
private final JButton zoomOutButton = button("graph_workspace.action.zoom_out", "zoom-out");
```

After (`R1`, `R4`):

```java
private final JButton undoButton = iconButton("graph_workspace.action.undo_workspace", "undo",
    "/images/undo.svg?useAccentColor=true");
private final JButton redoButton = iconButton("graph_workspace.action.redo_workspace", "redo",
    "/images/redo.svg?useAccentColor=true");
...
private final JButton zoomInButton = iconButton("graph_workspace.action.zoom_in", "zoom-in",
    "/images/ZoomIn24.svg?useAccentColor=true");
private final JButton zoomOutButton = iconButton("graph_workspace.action.zoom_out", "zoom-out",
    "/images/ZoomOut24.svg?useAccentColor=true");
```

The four paths are exact and must not be altered (`R4`). No `setPreferredSize` is applied; the button derives its preferred size from the resolved icon.

Add this private static helper directly after `button(...)` (current lines 362-366), reusing `configure(...)` unchanged:

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

Semantics:

- `R1`: on a resolved icon the button is icon-only (`setText(null)`) and `getName()` keeps `graph-workspace-<name>` through the unchanged `configure(...)`.
- `R2`: the tooltip is the original full action label from `TextUtils.getText(textKey)`.
- `R3`: the accessible name is the same full label; `getName()` remains `graph-workspace-undo`, `graph-workspace-redo`, `graph-workspace-zoom-out`, `graph-workspace-zoom-in`.
- `R5`/`E1`: when `getOptionalIcon` returns `null`, the constructor text is retained, icon/tooltip/accessible-name mutations are skipped, no exception is thrown, and `getOptionalIcon` (not `getIcon`) avoids a `severe` log for a missing asset.
- No pixel size is passed; `ResourceController.getOptionalIcon` delegates to `IconFactory.DEFAULT_UI_ICON_HEIGHT` / `SVGIconCreator` (`R4`).
- Button fields, accessors, listeners (`undoButton.addActionListener` line 138 and `redoButton…` line 139, `zoomInButton`/`zoomOutButton` lines 144-145), and `approvedControlNames` (minus `unpin`) are untouched.

#### 2.2.2 P2b — Single pin toggle state

Remove the `unpinButton` field (current line 63) and the `unpinAction` field (current line 74). Add two fields after `private boolean workspaceRedoAvailable;` (current line 78):

```java
private boolean pinEnabled;
private boolean pinPinned;
```

Remove `add(unpinButton);` (current line 133) and `unpinButton.addActionListener(event -> unpinAction.run());` (current line 149). The retained wiring is `pinButton.addActionListener(event -> pinAction.run());` (current line 148).

Remove the accessor `JButton unpinButton()` (current lines 229-231) and the setter `void setUnpinAction(...)` (current lines 269-271). `setPinAction` (current lines 265-267) is retained.

Add one state entry point next to `setHistoryAvailability` (current lines 282-287):

```java
void setPinState(final boolean enabled, final boolean pinned) {
    pinEnabled = enabled;
    pinPinned = pinned;
    updateReadOnlyControls();
}
```

Rewrite `updateReadOnlyControls()` (current lines 350-360) to the truth table below; delete the `unpinButton.setEnabled(!readOnly);` line (current line 358):

```java
private void updateReadOnlyControls() {
    saveButton.setEnabled(!readOnly);
    saveAsButton.setEnabled(!readOnly);
    undoButton.setEnabled(!readOnly && workspaceUndoAvailable);
    redoButton.setEnabled(!readOnly && workspaceRedoAvailable);
    connectButton.setEnabled(!readOnly);
    settingsButton.setEnabled(!readOnly);
    pinButton.setEnabled(!readOnly && pinEnabled);
    pinButton.setText(TextUtils.getText(pinPinned
        ? "graph_workspace.action.unpin" : "graph_workspace.action.pin"));
    directionComboBox.setEnabled(!readOnly);
}
```

Truth table (`R7`, `R11`), exactly as approved in design decision 6:

| `readOnly` | `pinEnabled` | `pinPinned` | Label | Enabled |
| --- | --- | --- | --- | --- |
| true | any | false | `Pin Node` | no |
| true | any | true | `Unpin Node` | no |
| false | false | false | `Pin Node` | no |
| false | true | false | `Pin Node` | yes |
| false | true | true | `Unpin Node` | yes |

The `pinEnabled=false, pinPinned=true` cell cannot occur (`pinned` is only computed for a selected node) and is deliberately omitted. Read-only mode **must not** change the label; it only forces `isEnabled() == false` even when `pinPinned` is true (`R7`, user ruling recorded in the design).

The label text key `graph_workspace.action.unpin` remains in `freeplane/src/viewer/resources/translations/Resources_en.properties` (line 812) and is reused by the toggle; no translation key is added (`R17`).

#### 2.2.3 P2c — Toolbar removals

Within `WorkspaceToolbar.java`, after P2a/P2b:

- `unpinButton` field (line 63) — removed; no replacement field.
- `add(unpinButton);` (line 133) — removed.
- `unpinButton.addActionListener(...)` (line 149) — removed.
- `JButton unpinButton()` accessor (lines 229-231) — removed.
- `unpinAction` field (line 74) — removed.
- `void setUnpinAction(...)` (lines 269-271) — removed.
- `approvedControlNames` entry `"unpin"` (line 66) — removed, leaving `"pin"` as the last entry.
- `unpinButton.setEnabled(!readOnly);` (line 358) — removed, replaced by `setPinState`/`updateReadOnlyControls`.

No compatibility shims or deprecated members are kept (repository legacy-removal policy). The `pinButton` field, `pinButton()` accessor, `pinAction` field, and `setPinAction` are retained.

### 2.3 P3 — Pin state publication and toggle in `GraphWorkspaceWindow`

File: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java` (1493 lines). The cited methods live in `GraphWorkspaceWindowModel`, the second package-private top-level class in the same file (declared at line 296).

Add `import org.freeplane.plugin.graph.projection.PinProjection;` between `GraphProjection` (line 73) and `ProjectedEdge` (line 74) in the projection import block (absent at lines 70-79).

Replace the wiring (current lines 468-469):

Before:

```java
toolbar.setPinAction(this::pinSelectedNode);
toolbar.setUnpinAction(this::unpinSelectedNode);
```

After:

```java
toolbar.setPinAction(this::togglePinSelectedNode);
```

In `updateStatusBar()` (current line 628), insert immediately after the `toolbar.setHistoryAvailability(...)` statement (current lines 631-632) and before `undoWorkspaceAction.setEnabled(...)`:

```java
final boolean nodeSelected = selectedNode != null && currentState != null;
final boolean pinned = nodeSelected && PinProjection.isPinned(currentState.projection(), selectedNode);
toolbar.setPinState(nodeSelected, pinned);
```

Semantics (`R7`, `R10`): `updateStatusBar()` already runs on every `ChangeSelection` intent (line 1242), on every accepted canvas state (`acceptCanvasState` line 967), and on every session-status update (`acceptSessionStatus` line 622), so the pin label and enablement refresh on all three paths. The short-circuit `nodeSelected &&` covers `currentState == null` without a separate branch (`E7`); the button is then disabled and labelled `Pin Node`.

Replace `pinSelectedNode()` (current lines 1282-1291) and `unpinSelectedNode()` (current lines 1293-1297) with one method in the same location:

Before:

```java
private void pinSelectedNode() {
    if (selectedNode == null || currentState == null) {
        return;
    }
    final NodeReference reference = selectedNode.source().persistedReference().orElse(null);
    final NodeGeometry geometry = currentState.geometry().nodes().get(selectedNode);
    if (reference != null && geometry != null) {
        executePin(selectedNode, geometry.center().x(), geometry.center().y());
    }
}

private void unpinSelectedNode() {
    if (selectedNode != null) {
        executeUnpin(selectedNode);
    }
}
```

After (`R9`):

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

Ordering constraint (`R9`, `E2`): the `isPinned` test precedes the geometry lookup, so a pinned selection still unpins when geometry is absent. The persisted-reference guard moves entirely into `executePin`/`executeUnpin` (current lines 1299-1311), which are unchanged: both read `node.source().persistedReference().orElse(null)` and no-op when absent or when `readOnly` is true (`E4`, `R15`). The `NodeReference` import (line 85) and `NodeGeometry` import (line 69) remain used.

### 2.4 P4 — Right-click toggle in `GraphInteractionController`

File: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java` (726 lines).

Add `import org.freeplane.plugin.graph.geometry.NodeGeometry;` after `import org.freeplane.plugin.graph.geometry.LayoutPoint;` (line 22). The existing fully qualified use at line 556 may stay as is; the new code below uses the imported simple name.

In `beginSelect(...)` (line 441), replace the pinned lookup (line 445):

Before:

```java
final boolean pinCandidate = value != null && value.isNode()
    && !isPinned(state, value.node().get());
```

After:

```java
final boolean pinCandidate = value != null && value.isNode()
    && !PinProjection.isPinned(state.projection(), value.node().get());
```

Replace `handleContext(...)` (current lines 494-513):

Before:

```java
private void handleContext(final MouseEvent event) {
    final CanvasState state = canvas.canvasState();
    if (state == null) {
        return;
    }
    final LayoutPoint world = canvas.worldAt(event.getPoint());
    final Optional<ProjectedEndpointKey> endpoint = canvas.hitIndex().endpointAt(world);
    if (endpoint.isPresent() && endpoint.get().isNode()
            && isPinned(state, endpoint.get().node().get())) {
        emit(new GraphIntent.Unpin(endpoint.get().node().get()));
        return;
    }
    final double zoom = canvas.viewport().zoom();
    final double tolerance = Double.isFinite(zoom) && zoom > 0.0
        ? EDGE_TOLERANCE_PIXELS / zoom : EDGE_TOLERANCE_PIXELS;
    final Optional<ProjectedEdgeKey> edge = canvas.hitIndex().edgeAt(world, tolerance);
    if (edge.isPresent()) {
        emit(new GraphIntent.InspectEdge(edge.get()));
    }
}
```

After (`R12`, `R13`, `R14`):

```java
private void handleContext(final MouseEvent event) {
    final CanvasState state = canvas.canvasState();
    if (state == null) {
        return;
    }
    final LayoutPoint world = canvas.worldAt(event.getPoint());
    final Optional<ProjectedEndpointKey> endpoint = canvas.hitIndex().endpointAt(world);
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
    final double zoom = canvas.viewport().zoom();
    final double tolerance = Double.isFinite(zoom) && zoom > 0.0
        ? EDGE_TOLERANCE_PIXELS / zoom : EDGE_TOLERANCE_PIXELS;
    final Optional<ProjectedEdgeKey> edge = canvas.hitIndex().edgeAt(world, tolerance);
    if (edge.isPresent()) {
        emit(new GraphIntent.InspectEdge(edge.get()));
    }
}
```

Semantics:

- `R12`: a right-click that hits a node toggles it: pinned → `GraphIntent.Unpin(node)`; not pinned → `GraphIntent.Pin(node, geometry.center().x(), geometry.center().y())`. The anchor is the node’s geometry centre, never the cursor world point.
- `R13`: no `GraphIntent.ChangeSelection` is emitted anywhere in `handleContext`; the selection is unchanged.
- `R14`: `contextGestureDispatched` / `contextHandled` (lines 204-265) keep at most one `handleContext` action per gesture; the node branch returns early, so a node hit no longer falls through to edge inspection.
- `E3`: the `geometry != null` check is defensive only. `GraphHitIndex.from` indexes node endpoints only when `state.geometry().nodes().get(node.key())` is non-null (`GraphHitIndex.java:61-63`), so a hit node always has geometry. The branch is unreachable in practice; it is code-reviewed and **must not** be unit-tested and **must not** be removed by this change.
- `E5`: a right-click that hits only an enclosure, or nothing, keeps the existing fall-through to `edgeAt`/`InspectEdge`.
- `R15`: read-only enforcement stays in `GraphWorkspaceWindow.executePin`/`executeUnpin`; `GraphInteractionController` continues to emit intents regardless of read-only mode, exactly as before.

Delete the now-unused private helper `isPinned(CanvasState, ProjectedNodeKey)` (current lines 572-580). The `PinProjection` import (line 27) and `ProjectedNodeKey` import (line 26) remain used.

### 2.5 P5 — Shared lookup in `AccessibleGraphCanvas`

File: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvas.java` (673 lines).

In the endpoint accessible state computation, replace the pinned lookup (current line 476):

Before:

```java
isSelected(state), isPinned(state, node.key()), visibleOutgoingTargets,
```

After:

```java
isSelected(state), PinProjection.isPinned(state.projection(), node.key()), visibleOutgoingTargets,
```

Delete the now-unused private static helper `isPinned(CanvasState, ProjectedNodeKey)` (current lines 518-526) and the now-unused `ProjectedNodeKey` import (line 39). The `PinProjection` import (line 34) remains used; `CanvasState` remains used by `isSelected` and the surrounding methods.

### 2.6 P6 — Signature, call-site, and removal summary

| Symbol | File | Before | After |
| --- | --- | --- | --- |
| `PinProjection.isPinned` | `projection/PinProjection.java` | — | `public static boolean isPinned(GraphProjection, ProjectedNodeKey)` (new, §2.1) |
| `WorkspaceToolbar.iconButton` | `window/WorkspaceToolbar.java` | — | `private static JButton iconButton(String textKey, String name, String iconPath)` (new, §2.2.1) |
| `WorkspaceToolbar.setPinState` | `window/WorkspaceToolbar.java` | — | `void setPinState(boolean enabled, boolean pinned)` (new, §2.2.2) |
| `WorkspaceToolbar.unpinButton` field/accessor | `window/WorkspaceToolbar.java` | field line 63, accessor 229-231 | removed |
| `WorkspaceToolbar.unpinAction` field, `setUnpinAction` | `window/WorkspaceToolbar.java` | field line 74, setter 269-271 | removed |
| `WorkspaceToolbar.approvedControlNames` | `window/WorkspaceToolbar.java` | `..., "pin", "unpin"` (line 66) | `..., "pin"` |
| `WorkspaceToolbar.updateReadOnlyControls` | `window/WorkspaceToolbar.java` | pin/unpin enablement (lines 357-358) | `pinButton.setEnabled(!readOnly && pinEnabled)` + truthful text (lines 350-360) |
| `GraphWorkspaceWindowModel.pinSelectedNode` / `unpinSelectedNode` | `window/GraphWorkspaceWindow.java` | lines 1282-1291 / 1293-1297 | replaced by `togglePinSelectedNode()` |
| `GraphWorkspaceWindowModel` wiring | `window/GraphWorkspaceWindow.java` | `setPinAction`/`setUnpinAction` lines 468-469 | `setPinAction(this::togglePinSelectedNode)` |
| `GraphWorkspaceWindowModel.updateStatusBar` | `window/GraphWorkspaceWindow.java` | history availability only (line 631) | adds `toolbar.setPinState(nodeSelected, pinned)` |
| `GraphInteractionController.isPinned` | `canvas/GraphInteractionController.java` | lines 572-580 | removed; calls use `PinProjection.isPinned` |
| `GraphInteractionController.handleContext` | `canvas/GraphInteractionController.java` | lines 494-513 | symmetric node toggle (§2.4) |
| `AccessibleGraphCanvas.isPinned` | `canvas/AccessibleGraphCanvas.java` | lines 518-526 | removed; call uses `PinProjection.isPinned` |

Explicitly retained (design §6.5): the `GraphIntent.Unpin` path (right-click and `Unpin All`), `GraphCommands.unpin`/`unpinAll`, the `graph_workspace.action.unpin` resource key (line 812 of `freeplane/src/viewer/resources/translations/Resources_en.properties`), the status-bar `Unpin All`, and the `pinButton`/`setPinAction` toolbar members.

### 2.7 Explicitly unchanged behavior

- `R6`: undo/redo enablement keeps `workspaceUndoAvailable`/`workspaceRedoAvailable` plus read-only rules; zoom-in/zoom-out remain always enabled. All other controls keep text and current enablement.
- Existing toolbar control identities, listeners, keyboard shortcuts, menus, and the status-bar `Unpin All` are untouched.
- The canvas left-drag-to-pin gesture and its `GraphIntent.Pin` emission are untouched.
- No new translation keys; the four tooltips reuse `graph_workspace.action.undo_workspace`, `…redo_workspace`, `…zoom_out`, `…zoom_in` (`R17`).
- No wrapping/two-row toolbar, no row-height change, no `setPreferredSize` on icon buttons.

## 3. Error and Edge Behavior

| ID | Situation | Required behavior |
| --- | --- | --- |
| `E1` | Icon asset missing / `getOptionalIcon` returns `null` | The button keeps its constructor text label; `setIcon`, `setToolTipText`, and `setAccessibleName` are skipped; no exception; `getName()` and enablement unchanged (`R5`). |
| `E2` | Selected node has no geometry and is not pinned | `togglePinSelectedNode` no-ops after the empty-selection/`currentState` guard and the `geometry != null` test; no command. |
| `E2b` | Selected node has no geometry but is pinned | The `isPinned` branch runs first, so `executeUnpin` is reached; if the persisted reference is absent it still no-ops (`E4`). |
| `E3` | Right-clicked node has no geometry | Defensive only; `GraphHitIndex.from` never indexes a node without geometry (`GraphHitIndex.java:61-63`), so the branch is unreachable. Code review only; no unit test; no removal. |
| `E4` | Selected node has no persisted reference | `executePin`/`executeUnpin` no-op; no command is executed. |
| `E5` | Enclosure or empty selection | Pin button disabled (`R11`); right-click on an enclosure falls through to edge inspection (`R14`). |
| `E6` | Read-only session | Pin button disabled but label still reflects the selected node’s pinned state (`R7`, `R11`); any `Pin`/`Unpin` intent that still reaches the window is rejected by the existing `!readOnly` guards (`R15`). |
| `E7` | `currentState == null` | `nodeSelected` is `false`, so `setPinState(false, false)` runs; no `NullPointerException` (`R10`). |
| `E8` | `setPinState(false, true)` | Cannot occur: `pinned` is computed only when `nodeSelected` is true. The truth table intentionally omits the cell. |
| `E9` | Duplicate context trigger events in one gesture | `contextGestureDispatched`/`contextHandled` still yield exactly one emitted intent per right-click gesture (`R14`). |

## 4. Test Plan

### 4.1 Conventions

- JUnit 4 (`org.junit.Test`), public `void` test methods, `*Should` class names, AssertJ `assertThat`, Mockito `mock`/`when`/`verify`/`ArgumentCaptor`.
- All tests run headless (`java.awt.headless=true` is set by the plugin test setup). No test asserts on real icon pixels or real `ResourceController`/`IconFactory` behavior.
- `GraphWorkspaceWindowModelShould` constructs models inside `GraphWorkspaceWindow.runOnEdt`; assertions run on the test thread after construction/`acceptIntent`/`acceptCanvasState` return. Disabled buttons never fire listeners, so disabled paths that must not command are asserted by direct method invocation (§4.4 T8), not `doClick()`.
- Falsifiability rules applied to every test below: differential assertions instead of merely positive ones; persisted keys wherever a command or pin must actually exist; post-command canvas-state publication before any refresh assertion; direct listener invocation for session-status refreshes.
- Additions only: no existing test method may be deleted except the specific edits named in §4.3.

### 4.2 Fixtures

#### 4.2.1 `GraphWorkspaceWindowModelShould` fixture plumbing (lines 122-123, 1775-1790)

Current state: `setUp()` (lines 122-123) creates a static `ResourceController` mock on the test thread and discards the instance; `EdtResources` (constructor lines 1775-1790) creates a second EDT-scoped static mock and also discards its instance; `Fixture.modelWithoutLayout()` (lines 1753-1767) constructs the model inside the same EDT runnable.

Required change:

1. Keep the test-thread mock at lines 122-123 unchanged; it is the default text-fallback controller for non-icon tests.
2. `GraphWorkspaceWindowModelShould.EdtResources` gains a `private final ResourceController controller;` field and a `ResourceController controller()` accessor. Extend `EdtResources` with:

```java
private EdtResources(final Map<String, Icon> iconStubs) {
    controller = mock(ResourceController.class);
    resourceController = org.mockito.Mockito.mockStatic(ResourceController.class);
    resourceController.when(ResourceController::getResourceController).thenReturn(controller);
    when(controller.getOptionalIcon(any(String.class)))
        .thenAnswer(invocation -> iconStubs.get(invocation.getArgument(0)));
    // existing TextUtils static-mock stubbing is unchanged
}
```

Keep the existing no-arg constructor as a delegating overload `this(Collections.<String, Icon>emptyMap());` so `keepsTheNineArgumentConstructorForTheUiEvidenceHarness` (line 1309) needs no edit.
3. `Fixture` (lines 1715-1768) gains:

```java
private final Map<String, Icon> iconStubs = new LinkedHashMap<String, Icon>();
private EdtResources resources;

private Fixture stubIcon(final String path, final Icon icon) {
    iconStubs.put(path, icon);
    return this;
}

private ResourceController resourceController() {
    return resources.controller();
}
```

`Fixture.modelWithoutLayout(...)` creates `edtResources[0] = new EdtResources(iconStubs);`, constructs the model as today, then stores `resources = edtResources[0];` before returning. The stub map must be populated **before** `fixture.model()` is called; the answer reads the live map during toolbar construction on the EDT.
4. Add a local icon factory helper:

```java
private static Icon icon(final int width, final int height) {
    final Icon icon = mock(Icon.class);
    when(icon.getIconWidth()).thenReturn(Integer.valueOf(width));
    when(icon.getIconHeight()).thenReturn(Integer.valueOf(height));
    return icon;
}
```

New imports needed in the test class: `java.lang.reflect.Method`, `javax.swing.Icon`, `org.freeplane.plugin.graph.control.WorkspaceSessionStatusListener`, `org.freeplane.plugin.graph.projection.PinProjection`, `org.freeplane.plugin.graph.workspace.model.PinRecord`. Mockito helpers stay fully qualified in the existing file style (`org.mockito.Mockito.times`, `org.mockito.Mockito.clearInvocations`, e.g. lines 1343, 1452), or the equivalent static imports are added.

Every new test obtains its fixture through the existing `fixture(...)` factories (lines 1358-1403) and, for icon tests, calls `fixture.stubIcon(...)` before `fixture.model()`. No new fixture class or file is needed for T1–T10 and T12–T13.

#### 4.2.2 Persisted vs transient node states (lines 1505-1517 vs 1547-1558)

- `persistedNodeState(ACTIVE_ID, LayoutPoint.of(2.0, -3.0))` (lines 1505-1517) builds a `SourceNodeKey.persisted(NodeReference.of(ACTIVE_ID, PersistedNodeId.of("selected")))` node with geometry centre `(2.0, -3.0)`. This is the **only** state usable where `GraphCommands.Pin`/`Unpin` must be observed, because `executePin`/`executeUnpin` require `source().persistedReference()`.
- `nodeState(ACTIVE_ID, ...)` (lines 1547-1558) uses `SourceNodeKey.transientPath(...)`; its `persistedReference()` is empty, so pin commands silently no-op. It is used only for icon rendering, enablement, and label tests that do not assert commands.
- `boundaryWithNodeState()` (lines 1519-1545) supplies the enclosure-plus-transient-node selection used for the disabled pin-on-enclosure assertion.

New state builders to add next to them:

```java
private static CanvasState persistedPinnedNodeState(MapReferenceId mapId, LayoutPoint center,
        double pinX, double pinY) {
    // same projection/geometry/layout as persistedNodeState(mapId, center),
    // but GraphProjection.projected(0L, nodes, emptyList(), emptyList(), emptyList(),
    //     Collections.singletonList(PinProjection.active(
    //         PinRecord.of(NodeReference.of(mapId, PersistedNodeId.of("selected")), pinX, pinY,
    //             Collections.emptyList()), key)))
}

private static CanvasState persistedNodeStateWithoutGeometry(MapReferenceId mapId) {
    // same persisted key and projection as persistedNodeState(mapId, ...),
    // but GraphGeometry.of(emptyMap(), emptyMap()) and
    // LayoutPositions.of(emptyMap(), emptyMap())
}

private static CanvasState persistedPinnedNodeStateWithoutGeometry(MapReferenceId mapId) {
    // same as persistedPinnedNodeState(mapId, ...), but with
    // GraphGeometry.of(emptyMap(), emptyMap()) and LayoutPositions.of(emptyMap(), emptyMap())
}
```

All three helpers return `CanvasState.of(0L, projection, layout, geometry, OperationalStatus.IDLE)`.

#### 4.2.3 Session-status listener

For T9, capture the listener registered during construction:

```java
ArgumentCaptor<WorkspaceSessionStatusListener> statusListener =
    ArgumentCaptor.forClass(WorkspaceSessionStatusListener.class);
verify(fixture.binding).addSessionStatusListener(statusListener.capture());
statusListener.getValue().onWorkspaceSessionStatus(
    WorkspaceSessionStatus.of(true, true, false, false, Collections.<MapReferenceId>emptySet(),
        java.util.Optional.<org.freeplane.plugin.graph.command.MapUndoTarget>empty()));
```

#### 4.2.4 `GraphInteractionControllerShould` fixture (lines 856-1009)

`Fixture.create()` already provides exactly the right differential state: `first` is an unpinned persisted node at centre `(-40.0, 0.0)` with radius `20.0`; `second` is a pinned persisted node at centre `(40.0, 0.0)` with radius `8.0`; `edgeKey` connects them; `context(canvas, x, y)` dispatches `MOUSE_PRESSED` with `BUTTON3` (lines 614-616). No fixture change is needed. T12/T13 obtain their state through `Fixture.create()` and record intents with the existing `RecordingListener`.

#### 4.2.5 `GraphWorkspaceUiEvidence` fixture (lines 102-114, 206-231, 560-568, 594-605)

- The mocked `ResourceController` created at line 114 (`thenReturn(mock(ResourceController.class))`) is hoisted to a named local inside the existing try-with-resources block:

```java
final ResourceController controller = mock(ResourceController.class);
resourceController.when(ResourceController::getResourceController).thenReturn(controller);
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
final EvidenceImages images = new EvidenceImages(desktop, marker);
```

`EvidenceImages` needs no constructor signature change; the stubs are installed on the same EDT static-mock scope that constructs the model.
- `twoMapState()` (lines 206-231) switches both node keys from `SourceNodeKey.transientPath(...)` to `SourceNodeKey.persisted(NodeReference.of(FIRST_MAP, PersistedNodeId.of("first")))` and `…SECOND_MAP, PersistedNodeId.of("second")` so `PinProjection.active` accepts them. Refactor `twoMapState()` so the node list and the `firstKey`/`secondKey`/`firstReference`/`secondReference` locals are computed once, then add `pinnedTwoMapState()` that reuses them with `GraphProjection.projected(7L, nodes, emptyList(), emptyList(), emptyList(), Collections.singletonList(PinProjection.active(PinRecord.of(secondReference, 110.0, 32.0, Collections.emptyList()), secondKey)))`.
- `ModelAccess.invoke` parameter-type limits (lines 560-568, 594-605): extend `findMethod(...)` so that, in addition to the existing `CanvasState.class` fallback, one-argument invocations resolve:
  - `GraphIntent` when `GraphIntent.class.isAssignableFrom(parameterTypes[0])` (covers `acceptIntent(new GraphIntent.ChangeSelection(...))`), by `type.getDeclaredMethod(name, GraphIntent.class)`;
  - `boolean` when `parameterTypes[0] == Boolean.class` (covers `setReadOnly(Boolean.TRUE)`), by `type.getDeclaredMethod(name, boolean.class)`.
  New imports in the harness: `java.util.Optional`, `javax.swing.Icon`, `org.freeplane.plugin.graph.canvas.GraphIntent`, `org.freeplane.plugin.graph.projection.ProjectedEndpointKey`, `org.freeplane.plugin.graph.projection.PinProjection`, `org.freeplane.plugin.graph.workspace.model.NodeReference`, `org.freeplane.plugin.graph.workspace.model.PersistedNodeId`, `org.freeplane.plugin.graph.workspace.model.PinRecord`.
- `build.gradle` stays unchanged: the task keeps the existing two `args`/`outputs.files` at lines 151-165 and overwrites the first screenshot in place.

#### 4.2.6 `PinProjectionShould` fixture (new file)

New file `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/PinProjectionShould.java`:

```java
package org.freeplane.plugin.graph.projection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.freeplane.plugin.graph.workspace.model.PinRecord;
import org.junit.Test;

public class PinProjectionShould {
    @Test
    public void resolvesPinnedStateFromActiveProjectionPins() {
        // map "00000000-0000-0000-0000-000000000001"; three persisted keys
        // "first" (active pin at 1.0/2.0), "second" (dormant pin), "third" (no pin)
        // GraphProjection.projected(1L, nodes, emptyList(), emptyList(), emptyList(),
        //     Arrays.asList(PinProjection.active(activeRecord, first), PinProjection.dormant(dormantRecord)));
        // assertThat(PinProjection.isPinned(projection, first)).isTrue();
        // assertThat(PinProjection.isPinned(projection, second)).isFalse();
        // assertThat(PinProjection.isPinned(projection, third)).isFalse();
    }
}
```

`PinProjection.active` requires `projected.source().persistent()` and `persistedReference().get().equals(pin.node())`, so the record references and keys must be built from the same `NodeReference`.

### 4.3 Existing tests that change or must stay green

| ID | Test | Lines | Change |
| --- | --- | --- | --- |
| `C1` | `GraphWorkspaceWindowModelShould.composesAHeadlessModelessWorkspaceWithStablePanelsAndApprovedControls` | 181-215 (assertion 198-200) | Replace the `contains(..., "pin", "unpin")` expectation with `contains(..., "pin")` **and** `.doesNotContain("unpin")` (`R8`). |
| `C2` | `GraphWorkspaceWindowModelShould.disablesMutatingControlsForReadOnlySessions` | 638-651 (line 646) | Delete `unpinButton().isEnabled()` (accessor no longer exists); assert `pinButton().isEnabled()` is `false` and, for this fixture’s empty selection, `pinButton().getText()` equals `"graph_workspace.action.pin"`. |
| `C3` | `GraphWorkspaceWindowModelShould.selectsTheSelectedNodeMapRowAndPinsTheSelectedNode` | 1427-1454 (lines 1451-1452) | Keep the Pin-at-centre assertions. Replace the `unpinButton().doClick()` no-command check with a direct reflective invocation of `togglePinSelectedNode` after `ChangeSelection(empty)`; still `verify(fixture.handle, times(1)).execute(any(GraphCommand.class))` (or `never()` after `clearInvocations`). A disabled button never fires, so the direct invocation is what proves the empty-selection guard. The `pinButton().doClick()` at line 1469 in `boundarySelectionDoesNotPinOrSelectAMapRow` becomes a disabled-button click and stays green vacuously; T4's enclosure step carries the real enclosure assertion. |
| `C4` | `UndoRoutingShould.keepsWorkspaceAndSourceMapHistoryActionsIndependentAndLocalized` | 114-161 (assertions 120-121) | **Must not change.** It stays green because its mocked `ResourceController` returns `null` from the unstubbed `getOptionalIcon`, so the four buttons keep their text labels via the fallback (`R5`). This test is the guard that no test installs a global icon stub. |
| `C5` | `GraphInteractionControllerShould.translateHoverPinAndContextActions` | 360-401 | **Must not change.** Right-click on the pinned `second` node still emits `Unpin(secondNodeKey)`; right-click at `(0.0, 0.0)` still hits the edge and emits `InspectEdge(edgeKey)`. |
| `C6` | `GraphWorkspaceCommandAcceptanceShould.scenario14SupportsPanZoomFitResetSearchHoverSelectOpenAndInspect` | 299-336 | **Must not change.** Its fixture projects enclosures only (no nodes), so the right-click at world `(0,0)` still falls through to `InspectEdge`. |
| `C7` | `GraphWorkspaceWindowModelShould.keepsTheNineArgumentConstructorForTheUiEvidenceHarness` | 1309-1348 | No edit: the `EdtResources()` no-arg overload is preserved (§4.2.1). |

`GraphPluginIntegrationShould` has no assertion naming the removed standalone unpin control (verified at revision `50e87ba1`); its tooltip-key assertions (lines 203-212) remain green because no translation key is added or removed.

### 4.4 New tests

#### T1 — `GraphWorkspaceWindowModelShould.rendersFourIconOnlyControlsThroughTheScalableIconLookup`

- File: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java`.
- Fixture: `fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()), nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)), registrations, false)`; then `fixture.stubIcon("/images/undo.svg?useAccentColor=true", icon(24, 16))`, `…redo… icon(16, 16)`, `…ZoomIn24… icon(20, 16)`, `…ZoomOut24… icon(28, 16)`; then `fixture.model()`.
- Assert per button (`undoButton`, `redoButton`, `zoomInButton`, `zoomOutButton`): `getIcon()` is same as the stubbed instance; `getText()` is `null`; `getToolTipText()` equals the action key; `getAccessibleContext().getAccessibleName()` equals the action key; `getName()` equals `graph-workspace-undo` / `-redo` / `-zoom-in` / `-zoom-out`.
- Differential size assertion (`R4`): `undoButton().getPreferredSize().width - redoButton().getPreferredSize().width == 8` and `zoomOutButton().getPreferredSize().width - zoomInButton().getPreferredSize().width == 8`. Both pairs share byte-identical L&F insets/margins, so the difference isolates the icon widths; the text fallback computes widths from differently shaped glyphs and does not reliably produce 8.
- Exact-path verification: `verify(fixture.resourceController()).getOptionalIcon(...)` for each of the four exact path strings (`R4`).
- Falsifiability: if `setIcon`/`setText(null)` is not applied, `getText()` is non-null; if a wrong path is used, the stub map returns `null` and the fallback leaves text; if the toolbar hardcodes a size, the width differences stop tracking the stubs.

#### T2 — `GraphWorkspaceWindowModelShould.keepsTextFallbackWhenTheIconLookupReturnsNoIcon`

- Fixture: same as T1 but **no** `stubIcon` calls, so the `EdtResources` answer returns `null` for every path.
- Assert per button: `getIcon()` is `null`; `getText()` equals the action key; `getName()` is unchanged; `getToolTipText()` is `null`.
- Clickability (`R5`): `clearInvocations(fixture.handle)`, `model.toolbar().zoomInButton().doClick()`, then `verify(fixture.handle, times(1)).execute(any(GraphCommand.class))` (the zoom gesture publishes a `GraphCommands.Viewport`, exactly as in `preservesLoadedViewportUnknownXmlThroughToolbarZoomFitAndReset`).
- Falsifiability: if the fallback cleared text or left a blank button, the text assertions fail; if the button were not wired, the click produces no command.

#### T3 — `GraphWorkspaceWindowModelShould.keepsUndoRedoEnablementRulesAndZoomButtonsEnabledWithoutHistoryAndInReadOnlySessions`

- Fixture A: `fixture(..., nodeState(...), registrations, false, WorkspaceSessionStatus.empty())`; assert `undoButton().isEnabled()` false, `redoButton().isEnabled()` false, `zoomInButton().isEnabled()` true, `zoomOutButton().isEnabled()` true; then `clearInvocations(fixture.handle)`, `zoomInButton().doClick()`, `verify(fixture.handle, times(1)).execute(any(GraphCommand.class))` (the zoom gesture publishes a `GraphCommands.Viewport` only when the session is not read-only).
- Fixture B: the same with `readOnly = true`; the four enablement assertions repeat with the same expected results (`R6`). Do **not** assert a command here: `publishViewport` suppresses commands in read-only sessions (`GraphWorkspaceWindow.java:674-684`). Reuse Fixture A's pattern — `clearInvocations(fixture.handle)`, `zoomInButton().doClick()`, then `verify(fixture.handle, never()).execute(any(GraphCommand.class))` — so the assertion is falsifiable for read-only suppression.
- Falsifiability: a refactor that disables zoom buttons in read-only mode or couples them to history fails; the pre-change assertions at `UndoRoutingShould` lines 124-134 and 154-155 keep the undo/redo half green.

#### T4 — `GraphWorkspaceWindowModelShould.tracksPinToggleLabelAndEnablementAcrossSelectionAndPinnedStates`

- Fixture: `persistedNodeState(ACTIVE_ID, LayoutPoint.of(2.0, -3.0))`, `readOnly = false`, model; `nodeKey`/`nodeEndpoint` as in `selectsTheSelectedNodeMapRowAndPinsTheSelectedNode`.
- Sequence and assertions:
  1. Initial empty selection: label `graph_workspace.action.pin`, `isEnabled()` false.
  2. `acceptIntent(ChangeSelection(Optional.of(nodeEndpoint)))`: label `graph_workspace.action.pin`, enabled true (unpinned selected node).
  3. `acceptCanvasState(persistedPinnedNodeState(ACTIVE_ID, LayoutPoint.of(2.0, -3.0), 2.0, -3.0))`: label `graph_workspace.action.unpin`, enabled true (`R7`/`R10` canvas-state refresh).
  4. `acceptIntent(ChangeSelection(Optional.empty()))`: label `graph_workspace.action.pin`, disabled (empty selection, `R11`).
- Enclosure part (same test, second fixture with `boundaryWithNodeState()` and `boundaryEndpoint` as in `boundarySelectionDoesNotPinOrSelectAMapRow`): after `ChangeSelection(Optional.of(boundaryEndpoint))`, label `graph_workspace.action.pin`, disabled (`R11`).
- Falsifiability: if `updateStatusBar` does not call `setPinState`, step 3 keeps the `Pin Node` label; if `nodeSelected` wrongly counts enclosures, the enclosure assertion fails.

#### T5 — `GraphWorkspaceWindowModelShould.keepsPinLabelTruthfulWhenReadOnlyDisablesTheToggle`

- Fixture: `persistedPinnedNodeState(ACTIVE_ID, LayoutPoint.of(2.0, -3.0), 2.0, -3.0)`, `readOnly = true`, model; select the persisted node.
- Assert: label `graph_workspace.action.unpin` **and** `isEnabled()` false — a disabled button that still reports the pinned state (`R7`/`R11`, design decision 6).
- Then `acceptCanvasState(persistedNodeState(ACTIVE_ID, LayoutPoint.of(2.0, -3.0)))`: label `graph_workspace.action.pin`, disabled.
- Falsifiability: a `readOnly ? "Pin Node" : label` implementation passes the second half but fails the first; a hidden `pinButton.setText(null)` in read-only fails both.

#### T6 — `GraphWorkspaceWindowModelShould.removesTheStandaloneUnpinControlFromToolbarAndApprovedNames`

- Fixture: any non-read-only `nodeState` fixture; model.
- Assert: `model.toolbar().approvedControlNames()` `.doesNotContain("unpin")` and `.contains("pin")`; iterate `model.toolbar().getComponents()` and assert no component `getName()` equals `graph-workspace-unpin` (`R8`).
- Falsifiability: retaining the field/add call recreates the named component; retaining the set entry fails the first assertion.

#### T7 — `GraphWorkspaceWindowModelShould.pinsTheSelectedNodeAtItsGeometryCentreAndUnpinsWhenPinned`

- Fixture: `persistedNodeState(ACTIVE_ID, LayoutPoint.of(2.0, -3.0))`, `readOnly = false`, model; select `nodeEndpoint`.
- Click path 1 (`R9`): `model.toolbar().pinButton().doClick()`; `ArgumentCaptor<GraphCommand>`; verify exactly one `execute`; assert `GraphCommands.Pin` with `node()` equal to `NodeReference.of(ACTIVE_ID, PersistedNodeId.of("selected"))`, `x() == 2.0`, `y() == -3.0` (geometry centre).
- Click path 2 (`R9`): `clearInvocations(fixture.handle)`; `acceptCanvasState(persistedPinnedNodeState(ACTIVE_ID, LayoutPoint.of(2.0, -3.0), 2.0, -3.0))`; assert label `unpin`; `doClick()`; verify exactly one `execute` and assert `GraphCommands.Unpin` with the same node reference.
- Falsifiability: an unpinned selection that emits `Unpin` fails path 1; a pinned selection that emits `Pin` again (wrong branch order) fails path 2; cursor/anchor mistakes fail the `x()`/`y()` assertions.

#### T8 — `GraphWorkspaceWindowModelShould.doesNotCommandPinWithoutGeometryOrSelection`

- Fixture: `persistedNodeStateWithoutGeometry(ACTIVE_ID)`, `readOnly = false`, model; select the persisted key; assert `pinButton().isEnabled()` is true (node selected, not pinned).
- `model.toolbar().pinButton().doClick()`; `verify(fixture.handle, never()).execute(any(GraphCommand.class))` — reachable no-geometry branch (`E2`), because `selectedNode` survives state publication.
- Pinned variant (`E2b`): publish `persistedPinnedNodeStateWithoutGeometry(ACTIVE_ID)` while the same persisted key stays selected; the `isPinned` branch runs first, so `pinButton().doClick()` executes exactly one `GraphCommands.Unpin` (the pin branch is skipped even though geometry is absent); verify that command, then `clearInvocations(fixture.handle)` so the following `never()` counts only the empty-selection step.
- `acceptIntent(ChangeSelection(Optional.empty()))`; invoke `togglePinSelectedNode` directly through reflection:

```java
Method method = GraphWorkspaceWindowModel.class.getDeclaredMethod("togglePinSelectedNode");
method.setAccessible(true);
method.invoke(model);
```

  then `verify(fixture.handle, never()).execute(any(GraphCommand.class))` — the empty-selection guard is not reachable through `doClick()` on a disabled button, so direct invocation is required. The reflective tests (`C3`'s test and this one) must declare `throws Exception` (or wrap the `Method` calls) to compile.
- Falsifiability: removing the `geometry != null` guard dereferences a null geometry and fails the test with a `NullPointerException` before any command is emitted; removing the empty-selection guard throws on the direct invocation; a branch order that checks geometry before the pinned state fails the pinned variant (`E2b`).

#### T9 — `GraphWorkspaceWindowModelShould.refreshesPinToggleAfterPostCommandCanvasStatePublication`

- Fixture: `persistedNodeState(ACTIVE_ID, LayoutPoint.of(2.0, -3.0))`, `readOnly = false`, model; select `nodeEndpoint`; label is `pin`.
- Canvas-state path (`R10`): `acceptCanvasState(persistedPinnedNodeState(...))`; label `unpin`.
- `Unpin` intent path (`R10`): capture commands, `acceptIntent(new GraphIntent.Unpin(nodeKey))`; assert `GraphCommands.Unpin` executed. The intent does not mutate `currentState`; now `acceptCanvasState(persistedNodeState(...))` and only then assert label `pin`. The post-command state publication is what makes this assertion non-vacuous.
- `UnpinAll` intent path (`R10`): `acceptCanvasState(persistedPinnedNodeState(...))` (label `unpin`), `acceptIntent(new GraphIntent.UnpinAll())`, assert `GraphCommands.UnpinAll` executed, `acceptCanvasState(persistedNodeState(...))`, assert label `pin`.
- Session-status path (`R10`, invariance): with the pinned state published and label `unpin`, invoke the captured `WorkspaceSessionStatusListener` (lines 273-275 of `UndoRoutingShould` show the invocation pattern; the capture is at 253-255) with a status whose undo flag is true; assert the label is still `unpin` and enabled — this step asserts that session updates do not clobber pin state; the falsifiers for the refresh wiring are the canvas-state and intent steps above.
- Falsifiability: removing `toolbar.setPinState(...)` from `updateStatusBar` leaves the step-2 label at `pin`; asserting before publishing the post-command state would have passed vacuously on the intent paths.

#### T10 — `GraphWorkspaceWindowModelShould.rejectsPinAndUnpinIntentsInReadOnlySessions`

- Fixture: `persistedNodeState(ACTIVE_ID, LayoutPoint.of(2.0, -3.0))`, `readOnly = true`, model; `nodeKey` persisted.
- `model.acceptIntent(new GraphIntent.Pin(nodeKey, 5.0, 6.0));` then `model.acceptIntent(new GraphIntent.Unpin(nodeKey));`
- Assert `verify(fixture.handle, never()).execute(any(GraphCommand.class))` and `pinButton().isEnabled()` is false (`R15`).
- Persisted key required: with the transient `nodeState` fixture, `executePin`/`executeUnpin` no-op on the missing persisted reference and the test would pass even if the `!readOnly` guard were deleted.

#### T11 — `PinProjectionShould.resolvesPinnedStateFromActiveProjectionPins`

- File: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/PinProjectionShould.java` (new, §4.2.6).
- Fixture: one projection, three persisted keys `first`/`second`/`third`; active pin on `first`, dormant pin whose record references `second`, no pin for `third`.
- Assert: `PinProjection.isPinned(projection, first)` true; `isPinned(projection, second)` false (dormant); `isPinned(projection, third)` false (non-matching key) (`R16`).
- Falsifiability: matching only on `active()` returns true for `second`’s dormant pin? No — dormant has no key; a “first active pin wins regardless of key” implementation returns true for `third`, failing; an implementation that ignores `projectedNode` fails the first assertion.

#### T12 — `GraphInteractionControllerShould.togglesPinOnTheNodeUnderTheContextClickAtItsCentre`

- File: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphInteractionControllerShould.java`.
- Fixture: `Fixture.create()`; install `new GraphInteractionController(new RecordingListener())` on `fixture.canvas()`.
- Drive/assert (`R12`, `R13`):
  1. `dispatch(canvas, context(canvas, -30.0, 0.0))` — an interior point of the unpinned first node, 10 units from its centre. Assert `listener.last()` equals `new GraphIntent.Pin(fixture.firstNodeKey, -40.0, 0.0)`: the anchor is the geometry centre, not the cursor `(-30.0, 0.0)`.
  2. Assert `listener.intents` contains no `GraphIntent.ChangeSelection` (e.g. `assertThat(listener.intents).extracting("class").doesNotContain(GraphIntent.ChangeSelection.class)`), so the selection is unchanged.
  3. `dispatch(canvas, context(canvas, 35.0, 0.0))` — an interior point of the pinned second node. Assert `listener.last()` equals `new GraphIntent.Unpin(fixture.secondNodeKey)`.
  4. `dispatch(canvas, context(canvas, 0.0, 0.0))` — no node hit. Assert `listener.last()` equals `new GraphIntent.InspectEdge(fixture.edgeKey)` (edge inspection retained where no node is hit).
- Falsifiability: using the cursor point yields `Pin(firstNodeKey, -30.0, 0.0)`; retaining the old fall-through after a node hit yields `InspectEdge` from the edge under the cursor; emitting `ChangeSelection` fails step 2.

#### T13 — `GraphInteractionControllerShould.dispatchesAtMostOneContextActionPerGesture`

- Fixture: fresh `Fixture.create()`; install the controller and listener.
- Drive: dispatch `BUTTON3` `MOUSE_PRESSED`, `MOUSE_RELEASED`, and `MOUSE_CLICKED` at `(-30.0, 0.0)` (reuse the generic `click(canvas, id, x, y, count, button)` helper with `MouseEvent.BUTTON3`). Assert `listener.intents` has size 1 and its only element is `new GraphIntent.Pin(fixture.firstNodeKey, -40.0, 0.0)` (`R14`).
- Falsifiability: removing `contextGestureDispatched`/`contextHandled` yields three intents; the first is still the correct `Pin`, so the size assertion is the discriminator.

### 4.5 Evidence-harness gate (`GraphWorkspaceUiEvidence`)

- Update as specified in §4.2.5: hoisted `ResourceController` mock with four placeholder-icon stubs, persisted keys in `twoMapState()`, `pinnedTwoMapState()`, and `ModelAccess` fallbacks for `GraphIntent` and `boolean`.
- Add a harness method `verifyPinToggleStates(CanvasState pinnedState)` called from `capture()` after `dispatchInteractions()`:

```java
final JComponent pin = findNamed(root, "graph-workspace-pin");
requireComponent(pin, "pin toggle");
final ProjectedEndpointKey firstEndpoint =
    ProjectedEndpointKey.ofNode(pinnedState.projection().nodes().get(0).key());
final ProjectedEndpointKey secondEndpoint =
    ProjectedEndpointKey.ofNode(pinnedState.projection().nodes().get(1).key());
modelAccess.invoke("acceptCanvasState", pinnedState);
modelAccess.invoke("acceptIntent", new GraphIntent.ChangeSelection(Optional.of(secondEndpoint)));
assertPinState((AbstractButton) pin, "unpin", true, "pinned selection");
modelAccess.invoke("setReadOnly", Boolean.TRUE);
assertPinState((AbstractButton) pin, "unpin", false, "read-only pinned selection");
modelAccess.invoke("acceptCanvasState", unpinnedState());
assertPinState((AbstractButton) pin, "pin", false, "read-only unpinned selection");
modelAccess.invoke("setReadOnly", Boolean.FALSE);
assertPinState((AbstractButton) pin, "pin", true, "unpinned selection");
```

  where `unpinnedState()` is the persisted `twoMapState()` and `assertPinState` throws `AssertionError` unless `getText()` equals the compacted label (`"pin"`/`"unpin"` via `compactText`) and `isEnabled()` matches. `R1`’s real-glyph rendering is deliberately not gated here: the placeholder stub only proves icon-only geometry and layout/no-overlap; T1 gates the icon pipeline.
- The task keeps the existing two `args`/`outputs.files` (`freeplane_plugin_graph/build.gradle:151-165`) and overwrites `docs/superpowers/specs/images/2026-08-10-graph-workspace-implemented.png` with the new toolbar. `build.gradle` and translation files must not change.

### 4.6 Launch commands

From the repository root, with the repository `gradle` (not `gradlew`):

- Full plugin suite: `gradle :freeplane_plugin_graph:test` (add `-PTestLoggingFull` for verbose failures).
- Targeted: `gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould' --tests 'org.freeplane.plugin.graph.canvas.GraphInteractionControllerShould' --tests 'org.freeplane.plugin.graph.projection.PinProjectionShould' --tests 'org.freeplane.plugin.graph.window.UndoRoutingShould'`.
- Evidence: `gradle :freeplane_plugin_graph:graphUiEvidence`.

## 5. Non-Blocking Review Notes

### 5.1 Accepted residual — `toolbar_icon_height` vs the fixed 42 px row

`WorkspaceToolbar.PREFERRED_SIZE` pins the row to `42` px (`WorkspaceToolbar.java:41`), and `FlowLayout` adds vertical gaps of `4` px above and below. Raising the user preference **Preferences → Appearance → Toolbar icon height** above roughly `28-30 pt` makes the icon buttons taller than the row and reintroduces clipping for the trailing controls. This is an **accepted residual** of the approved design: the row height is a non-goal, the default `16 pt` is unaffected, and the manual verification (V4) records the boundary with one larger value. A clamp to the available row height or a derived row height is a documented follow-up candidate, not part of this change.

### 5.2 Right-click behavior change

A right-click that hits a node no longer falls through to edge inspection at that world point (the node branch returns early). Nodes and edges remain distinct hit targets, and edge inspection still works wherever no node is hit; `GraphWorkspaceCommandAcceptanceShould.scenario14` remains green because its fixture has no nodes (C6). This is an accepted, design-approved behavior change.

### 5.3 Evidence placeholder icons

`GraphWorkspaceUiEvidence` runs headless with `java.awt.headless=true` and cannot render the real SVGs; the evidence run stubs fixed 16×16 placeholder icons so the layout/overlap gate is meaningful. Real glyph sharpness, accent-colour replacement, tooltips, and HiDPI scaling are verified in the running application (V4).

### 5.4 Icon-height preference snapshot

`IconFactory.DEFAULT_UI_ICON_HEIGHT` is a class-load-time snapshot (`IconFactory.java:42-43`) with no change listener, so a `toolbar_icon_height` preference change applies on restart, exactly like the core toolbar. V4 changes the preference and restarts to verify.

### 5.5 Truth-table omission

The `readOnly=true`/`pinEnabled=false`/`pinPinned=true` cell cannot occur (pinned is computed only for a selected node) and is intentionally omitted rather than coded defensively. No test may assert it.

### 5.6 R1 split between unit test and evidence

The evidence run cannot distinguish a real icon from a text fallback (its non-blank/no-overlap assertions pass either way). `R1` is therefore gated by T1 (stubbed icons, exact paths, text null, size differential), and the evidence run is only the layout/no-overlap gate. This mirrors the design’s stated test strategy and is not a coverage gap.

### 5.7 Deleted private helpers

The two deleted `isPinned` helpers were private and had no external callers; their removal is verified at compile time by the single shared call sites (P4 `beginSelect`/`handleContext`, P5 `AccessibleGraphCanvas`). No behavioral test is needed for deletion once T11 proves the shared query and C5/C6 prove the call-site behavior.

## 6. Verification

`V1`: `gradle :freeplane_plugin_graph:test` from the repository root must exit successfully. Use the repository `gradle`, not `gradlew`; add `-PTestLoggingFull` for verbose failures.

`V2`: The run must include every existing plugin test green plus the 13 new tests. Expected counts at revision `50e87ba1`: `GraphWorkspaceWindowModelShould` 52 → 62 (+T1–T10), `GraphInteractionControllerShould` 23 → 25 (+T12, T13), new `PinProjectionShould` 1 (T11), `UndoRoutingShould` 3 unchanged (C4), and the other existing suites (including `GraphWorkspaceCommandAcceptanceShould` and `GraphPluginIntegrationShould`) unchanged and green.

`V3`: `gradle :freeplane_plugin_graph:graphUiEvidence` must exit successfully, print `Graph UI evidence: EDT shell interactions, desktop workspace, and marker paints passed`, regenerate `docs/superpowers/specs/images/2026-08-10-graph-workspace-implemented.png` with the icon-only toolbar and a single pin button, and pass the new pin-state assertions inside the harness. Manual review of the regenerated image: four icon slots present, all controls left of the clipped right edge, no sibling overlap.

`V4` (manual acceptance, design §10-11): after a full build, run `BIN/freeplane.sh` and open a workspace. Confirm: the four tooltips show the full names; icons stay crisp under HiDPI scaling and legible in the dark theme; `Pin Node`/`Unpin Node` track selection, drag-pin, right-click toggle, undo/redo, and `Unpin All`; a `toolbar_icon_height` increase and restart reproduces the accepted clipping boundary of §5.1. These checks are not part of the automated gate.

`V5`: `freeplane_plugin_graph/build.gradle` and every `Resources_*.properties` file are byte-identical to revision `50e87ba1`. The only main-source files changed are the five named in §2; no other main source file changes.

## 7. Out of Scope / Known Residuals

- Converting any other toolbar control (including `Select`, `Connect`, `Settings`, `Fit Graph`, `Reset Zoom`) to icons; adding icons to the pin toggle.
- Changing the File/Edit menu items, accelerators, or the status-bar `Unpin All`.
- Changing the left-drag-to-pin gesture.
- Wrapping/two-row toolbar layout or any dynamic row-height/clamp mechanism (§5.1).
- New translation keys; tooltips reuse `graph_workspace.action.*`.
- Removing `GraphIntent.Unpin`, `GraphCommands.unpin`/`unpinAll`, the `graph_workspace.action.unpin` resource key, or the pin model.
- The unreachable right-click “node hit but no geometry” branch: reviewed, neither tested nor removed (§2.4 `E3`).
- Any change to core `ResourceController`/`IconFactory`/`SVGIconCreator` or to projection/geometry/layout code.
- Sub-dialogs, settings, or persistence formats.

## 8. Traceability

| Design requirement | Spec sections | Tests |
| --- | --- | --- |
| `R1` | §2.2.1 (P2a) | T1; evidence §4.5 layout gate |
| `R2` | §2.2.1 | T1 |
| `R3` | §2.2.1 | T1 |
| `R4` | §2.2.1; §5.6 | T1; V3 |
| `R5` | §2.2.1; §3 `E1` | T2; C4 |
| `R6` | §2.2.2; §2.7 | T3; `UndoRoutingShould` lines 124-155 |
| `R7` | §2.2.2; §2.3; §3 `E6` | T4, T5; §4.5 `assertPinState` |
| `R8` | §2.2.3; §2.6 | T6; C1, C2 |
| `R9` | §2.3 | T7; T8; C3 |
| `R10` | §2.2.2; §2.3 | T4, T9 |
| `R11` | §2.2.2; §2.3; §3 `E5`, `E6` | T4, T5 |
| `R12` | §2.4 | T12 |
| `R13` | §2.4 | T12 |
| `R14` | §2.4; §3 `E5` | T12, T13; C5, C6 |
| `R15` | §2.3; §3 `E6` | T10 |
| `R16` | §2.1, §2.4, §2.5 | T11; compile-time call sites |
| `R17` | §2.2.1, §2.2.2, §7 | T1, T2; V5 |

| Design section | Spec coverage |
| --- | --- |
| §6.1 icon-only buttons | P2a, T1, T2 |
| §6.2 pin toggle | P2b, P3, T4, T5, T7–T10 |
| §6.3 right-click toggle | P4, T12, T13 |
| §6.4 shared lookup | P1, P4, P5, T11 |
| §6.5 removals | §2.2.3, §2.3, §2.4, §2.5, §2.6, T6 |
| §7 error handling | §3 `E1`–`E9` |
| §8 tests 1–12 | T1–T13, C1–C7, §4.5 |
| §9 risks / §10 verification | §5, §6 |
