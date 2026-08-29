# Graph Group Marker Color Design

**Date:** 2026-08-29
**Status:** Approved (design reviewed with user; mockup confirmed)

## Summary

Allow users to change the color of graph group markers. The color applies
application-wide (one preference) and is changed from a new toolbar button
placed directly next to the "Mark graph group" button, parallel to how the
cloud color button sits next to the cloud button.

The current hardcoded coral `#DF625D` becomes the **default** of the
preference, so existing users see no change until they pick a color.

## Decisions

- **Single application-wide color** (not per-node, like cloud color).
  Ruled out: per-node color stored in `GraphGroupModel` (the user explicitly
  chose a global color).
- **Stored as an application-wide preference** (`ResourceController`
  property, persisted in the user properties file), not per-map.
- **Toolbar-only button.** No entry in Preferences › Defaults. Rationale:
  the graph view is a plugin; a preferences-dialog entry would force core
  `preferences.xml` changes for a plugin feature. Default value is
  registered by the plugin via `setDefaultProperty` (same trick `MapView`
  uses for `spotlight_background_color`), keeping the core `freeplane.properties`
  untouched for default values.
- **Both views follow the preference:** the mind-map marker
  (`GraphGroupMarkerPainter`) and the Graph Workspace group boundary color
  (`GraphPainter.GROUP_BOUNDARY_COLOR`). They currently share the hardcoded
  coral; a single preference keeps them consistent.
- **No undo integration.** The color is a display preference (like selection
  colors), not map data. Cancelling the chooser is a no-op.

## Architecture

### Property

- Key: `graph_group_color`
- Default: `#DF625D` (current coral), registered by the plugin on install.
- Accessor: plugin-side helper (`GraphGroupColors.currentColor()`), reading
  `ResourceController.getColorProperty(KEY)` with a coral fallback when the
  property is missing or invalid. Note: `getColorProperty` on a missing
  key returns `""` and `ColorUtils.stringToColor("")` throws
  `NumberFormatException`, so the fallback must wrap the lookup in
try/catch.

### Writer: `GraphGroupColorAction`

- New `AFreeplaneAction` (key `GraphGroupColorAction`) in
  `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/group/`.
- `actionPerformed`: opens `ColorTracker.showCommonJColorChooserDialog`
  seeded with the current color; "reset to default" returns to coral
  `#DF625D`; on OK calls
  `ResourceController.setProperty(KEY, ColorUtils.colorToString(color))`.
- Registered/unregistered by `GraphGroupController` alongside
  `GraphGroupAction` (and removed in `close()`).

### Readers

- `GraphGroupMarkerPainter` (mind map): replace constant `CORAL` with the
  accessor; alpha variants (active fill 40, inactive fill 18, inactive
  stroke 120) are computed from the chosen color.
- `GraphPainter.GROUP_BOUNDARY_COLOR` (Graph Workspace canvas): replace the
  constant with the same accessor, read at paint time.

### Repaint trigger

Two small `IFreeplanePropertyListener`s, both filtered by the key and safe
on the EDT (property changes originate from the action on the EDT):

- `GraphModeExtension` registers one listener on install (removed in
  `close()`) that iterates
  `Controller.getCurrentController().getMapViewManager().getMapViews()` and
  calls `repaint()` on each `MapView` (the marker painter is invoked from
  `NodeView.paint`).
- `GraphWorkspaceWindow` registers one listener in its constructor
  (removed in `closeOnEdt()`) that calls `canvas.repaint()`; the window owns
  the canvas and is only constructed in the real app (`HeadlessGraphWorkspaceView`
  is used in headless tests), so the listener cannot outlive a closed window
  and no existing unit test constructs it. (`GraphCanvas.addNotify()` was
  rejected: `AccessibleGraphCanvasShould` attaches a real canvas to a JFrame
  without a `Controller`, so registering there would NPE in tests.)

### Menu entry

- `<Entry name="GraphGroupColorAction" />` inserted directly after
  `<Entry name="GraphGroupAction" />` in
  `freeplane/src/external/resources/xml/mindmapmodemenu.xml` (toolbar row 2,
  before `CloudColorAction`).

### Resources

- `freeplane.properties`: `GraphGroupColorAction.icon` reusing
  `/images/Colors24.svg?useAccentColor=true` (same as `CloudColorAction`).
- `Resources_en.properties`: `GraphGroupColorAction.text` and
  `GraphGroupColorAction.tooltip` (established plugin convention; existing
  integration test enforces this for `GraphGroupAction`).

## Data flow

Button click → color chooser → `setProperty` → `firePropertyChanged` →
property listener → repaint MapViews + windows → painters read new color on
next paint. Cancel = no-op. Missing/invalid property = coral fallback.

## Testing

- `GraphGroupColorActionShould`: chooser seeded with current color; OK writes
  property; cancel writes nothing; reset-to-default returns coral.
- Extend `GraphGroupMarkerPainterShould` and `GraphCanvasPaintShould`: paint
  with a mocked non-default property → pixels match chosen color; default
  property → existing coral expectations unchanged.
- Extend `GraphPluginIntegrationShould`: `GraphGroupColorAction` entry sits
  directly after `GraphGroupAction`; icon + translation keys present.
