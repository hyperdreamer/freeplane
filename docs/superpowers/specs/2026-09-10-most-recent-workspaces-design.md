# Most Recent Workspaces Design

**Date:** 2026-09-10
**Status:** Approved (design reviewed with user; mockup confirmed)

## Summary

Make recently used graph workspaces easy to reopen.

1. The graph workspace window's **File** menu gains a **Recent Workspaces**
   submenu listing recently used workspace files, newest first.
2. Clicking **View ▸ Open Graph Workspace** in the main window opens the most
   recently used workspace that still exists, instead of always prompting with
   a file chooser. When no remembered workspace can be opened, the file chooser
   appears exactly as it does today.

The recollection is one application-wide list, shared by all graph workspace
windows and persisted across restarts in Freeplane's user properties.

## Decisions

- **Submenu, not inline entries.** The File menu keeps a stable shape
  (Open…, Save, Save As…, Recent Workspaces ▸, separator, Close) and the list
  can grow without bloating the menu. Ruled out: inline entries, which force a
  small hard cap and push Close far down; and a parallel copy in the main
  window's View menu, which duplicates the same menu in two places.
- **Hide missing files, keep them stored** ("hide-but-keep"). A file that does
  not exist right now is not listed, but stays in the stored list and reappears
  when the file does. Ruled out: greyed `name (missing)` rows, which cannot be
  clicked and consume visible slots; and hiding with pruning, which loses the
  shortcut permanently the first time a network share or removable drive is
  simply not mounted. Ruled out: core Freeplane's behaviour for mind maps
  (list stale entries, ask to remove them after a failed open), which trades a
  tidy menu for a prompt on every stale entry.
- **A recent entry never creates a workspace.** `DefaultGraphWorkspaceController.open`
  treats a nonexistent path as "create a new workspace". Every recents-based
  entry point therefore requires an **existing regular file** before calling
  `open`. The chooser path keeps today's behaviour, so creating a workspace by
  choosing a new name still works.
- **Application-wide, user-property backed persistence.** One list for the
  whole application, stored through `ResourceController` like core's recent
  maps. No per-window lists, no workspace file content, no new file format.
- **No preferences UI.** The stored cap (25) and the displayed cap (8) are
  constants. A preferences entry is deferred until someone needs to tune them.
- **View ▸ Open Graph Workspace opens the newest *existing* entry**, falling
  back to the chooser only when no stored entry currently exists. This keeps
  the entry point consistent with hide-but-keep: a missing newest entry skips
  to the next existing one instead of sending the user to a chooser they just
  navigated away from.
- **The submenu is never disabled.** A disabled `JMenu` cannot reopen, so
  disabling it when the list is empty would freeze it off until the window is
  recreated. Instead the submenu stays enabled and shows a single disabled
  `No recent workspaces` row when nothing is displayable. *(Correction to the
  reviewed design sketch, which said "greyed out when empty".)*
- **Clear stays reachable while hidden entries exist.** Because hiding does
  not prune, `Clear Recent Workspaces` is offered whenever the *stored* list is
  non-empty, even if nothing is displayable. Otherwise hidden entries could
  never be removed by the user.
- **Recording is best-effort.** A failure to persist the list must never fail
  an open or a save.

## Mockup

![Recent Workspaces submenu](images/2026-09-10-most-recent-workspaces-mockup.png)

File menu (graph workspace window) and the submenu it opens. Entries are
labelled `file name (containing folder)`. Only files that exist right now are
listed; missing files stay in the stored list.

## Architecture

### Module: `RecentWorkspaceList`

New class `org.freeplane.plugin.graph.workspace.RecentWorkspaceList`. This is
the whole feature's policy in one place, with no Swing dependency:

```java
public final class RecentWorkspaceList {
    public static final class Entry {          // path + display label
        public Path path();
        public String label();
    }

    public static RecentWorkspaceList standard();   // ResourceController-backed
    public static RecentWorkspaceList empty();      // no stored value, no persistence
    public RecentWorkspaceList(String storedValue, Consumer<String> persister);

    public void record(Path workspaceFile);         // promote to newest, de-duplicate, cap
    public void clear();                            // drop every stored entry
    public List<Entry> displayEntries();            // newest first, existing files only, capped
    public boolean hasStoredEntries();
    public Optional<Path> mostRecentExisting();     // newest existing file, if any
}
```

- Owns ordering (newest first), de-duplication on re-record, the stored cap
  (25) and display cap (8), existence filtering, label formatting, and the
  persisted encoding. Callers never see property keys, separators, or path
  policy.
- Paths are canonicalized through the existing
  `WorkspaceUriResolver.canonical`, the same normalizer
  `DefaultGraphWorkspaceController.open` already uses, so `..` or symlink
  variants cannot become duplicate entries.
- `displayEntries` only returns **existing regular files**, in stored order,
  truncated to the display cap. Hidden entries do not consume display slots.
- Label policy: `file name (containing folder)`, or just the file name when
  the path has no parent.
- Thread-safe: internally synchronized (recording can happen off the EDT,
  menu construction happens on it).
- The two zero-argument factories keep the distinction between production
  (properties-backed) and tests (no persistence) explicit; the constructor
  with a persister is the single seam the unit tests drive.

### Recording

Two existing seams, no new event machinery:

- **Open success** — `DefaultGraphWorkspaceController.finishOpen`, after the
  session publishes OPEN: `recentWorkspaces.record(path)`. The path is already
  canonical there, and this covers workspaces opened from the chooser, from a
  recent entry, and from the main window's action, including a workspace file
  that was created by opening a nonexistent path chosen in the chooser.
- **Save As success** — a `WorkspaceStoreListener` registered per session on
  `WorkspaceStoreEvent.Type.IDENTITY_CHANGED` calls
  `record(change.newPath())`. The new file becomes newest; the previous path
  remains in the list because that file still exists.

The recorder is a session resource: `ProductionSessionFactory` registers the
listener immediately after creating the store, `SessionResources` carries it,
and the existing resource-teardown funnel (`closeRemainingResources` and
`cleanupResources`, reached from shutdown, close, and rollback) closes it. That
funnel is chosen deliberately so the recorder survives a failed save — on save
failure the store stays open and only reopens the session, and listeners are
cleared by the store itself only on a successful close.

### Menu

`GraphWorkspaceWindowModel.createMenuBar` inserts a `JMenu`
(`graph_workspace.menu.recent_workspaces`, component name
`graph-workspace-recent-workspaces-menu`) directly after `fileSaveAsMenuItem`
and before the existing separator and Close item.

- Contents are rebuilt in a `PopupMenuListener` on
  `popupMenuWillBecomeVisible`, so the existence filter is never stale and no
  list-change listener is needed.
- Non-empty: one `JMenuItem` per `displayEntries()` entry, then a separator,
  then `Clear Recent Workspaces`.
- Empty: one disabled `No recent workspaces` item, then — only when
  `hasStoredEntries()` — a separator and `Clear Recent Workspaces`.
- Item activation guards with `Files.isRegularFile(path)` **before** calling
  `applicationController.open(path)`. When the file vanished between opening
  the menu and clicking, the model reports
  `graph_workspace.recent_workspaces.missing` through the existing
  `commandMessageSink` and refreshes the menu; it never calls `open`, so no
  workspace is created.
- `Clear Recent Workspaces` calls `clear()` and rebuilds the menu.

### View ▸ Open Graph Workspace

`OpenGraphWorkspaceAction` gains a constructor taking `(GraphWorkspaceController,
RecentWorkspaceList)`; it resolves the path as "newest existing recent entry,
otherwise the existing chooser supplier", preserving the current no-op when the
chooser is cancelled. The existing `(controller, Supplier<Path>)` constructor is
unchanged, so current tests keep their meaning.

### Wiring

`GraphModeExtension.installExtension` creates the single list and injects it:

- `new DefaultGraphWorkspaceController(modeController, viewFactory, recentWorkspaces)`
- `new SwingGraphWorkspaceViewFactory(viewController, recentWorkspaces)`
- `new OpenGraphWorkspaceAction(viewController, recentWorkspaces)`

`GraphWorkspaceWindow` and `GraphWorkspaceWindowModel` take the list through
their existing constructor chains. Test-only short overloads default to
`RecentWorkspaceList.empty()` so no existing test starts touching user
properties.

### Resources

New keys in `freeplane/src/viewer/resources/translations/Resources_en.properties`
(where every other `graph_workspace.*` key lives):

- `graph_workspace.menu.recent_workspaces=Recent Workspaces`
- `graph_workspace.action.clear_recent_workspaces=Clear Recent Workspaces`
- `graph_workspace.recent_workspaces.empty=No recent workspaces`
- `graph_workspace.recent_workspaces.missing=The workspace file no longer exists: {0}`

Only the English bundle carries `graph_workspace.*` keys today; other locales
fall back. Per repository rules the file stays ISO-8859-1 with `\uXXXX` escapes
and `gradle format_translation` is run after the edit (these four values are
plain ASCII).

## Data flow

- **Open a workspace** (chooser, recent entry, or main-window action) →
  `DefaultGraphWorkspaceController.open` → session opens → `finishOpen` →
  `record(path)` → properties updated in memory → flushed with the rest of the
  user properties on shutdown or preferences close.
- **Save As** → command routed to `GraphWorkspaceStore.saveAs` →
  `IDENTITY_CHANGED` event → recorder → `record(newPath)`.
- **File ▸ Recent Workspaces ▸** → popup about to become visible →
  `displayEntries()` (fresh existence filter, capped) → menu items → click →
  existing-file guard → `open(path)` or report + refresh.
- **View ▸ Open Graph Workspace** → `mostRecentExisting()` → `open(path)`, or
  the chooser when the result is empty.

## Error handling and edge cases

- No stored entries, or every stored entry missing: submenu shows
  `No recent workspaces`; View ▸ Open Graph Workspace falls back to the
  chooser. Neither is a dead end.
- All stored entries missing but stored: `Clear Recent Workspaces` is still
  offered so the entries can be purged.
- Entry vanishes between menu build and click: reported, menu refreshed, no
  workspace created.
- A recent file that exists but fails to open (corrupt or unsupported) behaves
  exactly as it does today when chosen from the chooser; no new catch is added.
- Persistence failure or an unusable path never fails an open or Save As; the
  list simply stays as it was.
- A workspace opened read-only still becomes recent: it opened successfully.
- Flush timing matches core's recent-maps list (properties are written on
  shutdown and preferences close), so a hard crash can lose the newest entry —
  the same exposure core already has. No new persistence machinery is added.
- Opening an already-open workspace continues to focus its window rather than
  creating a second one.

## Testing

- `RecentWorkspaceListShould` (new, headless): newest-first ordering;
  re-recording an existing path promotes instead of duplicating; stored cap 25
  evicts the oldest; display cap 8 truncates; missing files are filtered out of
  `displayEntries` and `mostRecentExisting` yet survive a write/read round-trip
  (hide-but-keep); `clear()` empties stored entries; label formatting including
  the parentless case; persisted-value round-trip through
  `encodeListValue`/`decodeListValue`; `record` never throws on an unusable
  path or a failing persister.
- Open resolution: covered at the `OpenGraphWorkspaceAction` level — newest
  existing entry wins, a missing newest entry falls through to the next
  existing entry, and an empty list delegates to the chooser supplier.
- `GraphWorkspaceWindowModelShould` (extend): the File menu contains
  `Recent Workspaces` directly after `Save As…` and before the Close separator;
  popup-open rebuilds items in order with the expected labels and cap; missing
  files are absent; the empty case shows the disabled `No recent workspaces`
  row and keeps Clear when entries are stored; clearing empties the list; the
  vanished-file click guard reports through the message sink and never calls
  `open`.
- Regression: `GraphPluginIntegrationShould` menu-XML and action-registration
  checks stay green — no menu XML is touched.
- Verification evidence: `gradle :freeplane_plugin_graph:test` green, the
  touched core resources formatted with `gradle format_translation`, and a File
  menu screenshot with the submenu open produced through the existing UI
  evidence harness (`GraphWorkspaceUiEvidence`).

## Out of scope

- A recents entry in the main window's View menu (the graph workspace window's
  File menu is the chosen surface).
- Showing workspace file names in the graph workspace window title.
- Preferences-dialog control over either cap.
- Pinning, grouping, or per-workspace metadata in the list.
- Any change to how opening a corrupt workspace reports failure.
