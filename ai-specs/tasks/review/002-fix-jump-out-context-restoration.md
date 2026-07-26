# Task: Fix jump-out context restoration for restored view roots
- **Ticket:** #2940
- **Scope:** Fix jump-out context restoration when the current view root
  was restored or otherwise has no visible ancestor path. Persist
  model-based root history for the single restored view per map, and
  make jump out reveal enough ancestor context in the active view to
  place the old root back into broader map context without changing its
  own fold state. Keep this work separate from the `#2941` jump-in
  subtree-lifecycle fix.
- **Motivation:** Freeplane currently remembers only one current root
  and one selected node per map, not the jump-out history behind that
  root. After restart, jumping out from a restored non-map root can drop
  the user to the map root without restoring visible context. The user
  loses place even though ordinary jump out is supposed to restore
  broader context.
- **Scenario:** Freeplane reopens a map whose last visible root was a
  descendant node. Startup should restore that descendant root without
  unfolding its hidden ancestors. Later, jump out should restore the
  broader view root, reveal only the ancestor path needed to show the
  old root in context, and keep any deeper selection only when it is
  still visible without opening the old root itself.
- **Constraints:**
  - Jump-out semantics are source-independent. They do not branch on
    whether the current root came from jump-in, restart restore,
    bookmark `open as root`, slide, or another entry path.
  - Runtime root history remains per map view, but restart persistence
    restores it only for the single restored view per map.
  - If multiple views of the same map are open, persist navigation
    state from the last active view of that map.
  - Startup restore must not unfold ancestors.
  - Jump out must reveal ancestor context in the active view only,
    ordinary jump-out style. Do not use `FoundNodes`-style ownership or
    add a new auto-refold layer.
  - Jump out must not change the old root's own fold state.
  - If a deeper selected descendant stays hidden because the old root is
    folded in restored broader context, select the old root.
  - If `rootsHistory` is empty, jump out still falls back to the map
    root and should reveal the ancestor path needed to show the old
    root there.
- **Briefing:** The change is centered in
  `org.freeplane.main.application.LastOpenedList` and
  `org.freeplane.view.swing.map.MapView`. `LastOpenedList` currently
  persists one selected node and one view root per recent file.
  `MapView` currently keeps jump-in history as `List<NodeView>` and uses
  that list only inside runtime jump out. `FoundNodes` is relevant as a
  contrast because it owns a separate auto-refold policy that this task
  must not reuse.
- **Research:**
  ```plantuml
  @startuml
  title Current restart restore loses jump-out context
  actor User
  participant "LastOpenedList" as LastOpenedList
  participant "MapViewManager" as MapViewManager
  participant "MapView" as MapView

  LastOpenedList -> MapViewManager : setViewRoot(lastRootNodeId)
  MapViewManager -> MapView : setRootNode(restoredRootNode)
  MapView -> MapView : rootsHistory stays empty
  note over MapView : currentRootParentView may stay null

  User -> MapViewManager : usePreviousViewRoot()
  MapViewManager -> MapView : usePreviousViewRoot()
  MapView -> MapView : rootsHistory empty => mapRootView
  MapView -> MapView : no persisted broader context
  @enduml
  ```
  - `LastOpenedList.selectLastVisitedNode(...)` restores only
    `lastRootNodeId` and `lastVisitedNodeId`.
  - `LastOpenedList.saveProperties()` writes those values after calling
    `updateLastVisitedNodeIds()`, which currently iterates every open map
    view. When several views of the same map are open, whichever one is
    visited last in that iteration overwrites the shared per-map slot.
  - `MapView.rootsHistory` is currently `List<NodeView>`. It stores only
    non-root jump-in history and cannot survive restart when ancestor
    views were never materialized.
  - `MapView.usePreviousViewRoot()` currently pops a `NodeView` from that
    runtime list or falls back to `mapRootView` when the list is empty.
  - `MapView.display(node)` materializes the ancestor path for `node` on
    the current view and unfolds only parent `NodeView`s on that path.
    Calling `display(oldRootNode)` reveals the old root in context
    without opening the old root's own folded subtree.
  - `restoreRootNode()` currently calls `rootsHistory.forEach(
    NodeView::keepUnfolded)` before clearing the list, so the current
    ordinary jump-out reveal style is view-only and sticky, not model
    folding and not `FoundNodes` ownership.
- **Analysis:**
  - Persist root history only for the single restored view per map
    because runtime history belongs to one `MapView` and full multi-view
    session restore is out of scope.
  - Represent runtime root history by `NodeModel` and persist it as node
    IDs because restart restore must recover history before ancestor
    `NodeView`s exist.
  - Keep startup restore neutral and orphan roots allowed so that jump
    out, not load, owns broader-context reveal.
  - Reveal only the ancestor path to the old root on jump out so that
    broader context is restored without changing the old root's own fold
    state.
  - Preserve deeper selection only when it is already visible after that
    old-root reveal because jump out must not open the old root's own
    subtree.
  - Treat the map root as the implicit broader context when
    `rootsHistory` is empty because first-level jump-in from the map root
    records no explicit history today.
  - Persist navigation state from the last active view of each map
    because the current save-order overwrite across same-map views is an
    implementation artifact, not a coherent policy.
- **Design:**
  ```plantuml
  @startuml
  title Target restore and jump-out flow
  actor User
  participant "LastOpenedList" as LastOpenedList
  participant "MapView" as MapView
  participant "MapViewManager" as MapViewManager

  LastOpenedList -> MapView : setRootsHistoryNodeIds(recentFile.lastRootHistoryNodeIds)
  LastOpenedList -> MapViewManager : setViewRoot(restoredRootNode)
  note over MapView : restore root only\nno ancestor unfolding at startup

  User -> MapViewManager : usePreviousViewRoot()
  MapViewManager -> MapView : usePreviousViewRoot()
  MapView -> MapView : newRootNode = history.pop() or map root
  MapView -> MapView : install broader root
  MapView -> MapView : display(oldRootNode)
  MapView -> MapView : keep deeper selection only if visible\nelse select oldRootNode
  @enduml
  ```
  ```plantuml
  @startuml
  set separator none
  package org.freeplane {
    package main.application {
      class LastOpenedList {
        - {static} LAST_ROOTS_HISTORY
        + afterViewChange(Component, Component)
        + afterViewClose(Component)
        + saveProperties()
      }
      class RecentFile {
        + String restorable
        + String lastVisitedNodeId
        + String lastRootNodeId
        + List<String> lastRootHistoryNodeIds
      }
      LastOpenedList *-- RecentFile
    }
    package view.swing.map {
      class MapView {
        - List<NodeModel> rootsHistory
        + setRootNode(NodeModel)
        + usePreviousViewRoot()
        + getRootsHistoryNodeIds()
        + setRootsHistoryNodeIds(List<String>)
      }
    }
    package features.map {
      class NodeModel
    }
    LastOpenedList ..> MapView : persist / restore
    MapView --> NodeModel : rootsHistory
  }
  @enduml
  ```
  - Add a new recent-file property key `lastRootsHistory`. Persist one
    outer list entry per recent file, and encode each per-map root
    history entry with `ConfigurationUtils.encodeListValue(..., false)`
    inside the existing outer `encodeListValue(..., true)` scheme.
  - Extend `RecentFile` with `lastRootHistoryNodeIds` and include that
    field in restore, copy, and save logic.
  - Change `MapView.rootsHistory` from `List<NodeView>` to
    `List<NodeModel>`. Keep the existing push rule: add the current root
    only on non-root `RootChange.JUMP_IN`.
  - Add `MapView.getRootsHistoryNodeIds()` and
    `MapView.setRootsHistoryNodeIds(List<String>)` so `MapView` remains
    the owner of runtime root-history resolution while `LastOpenedList`
    owns serialization. `setRootsHistoryNodeIds(...)` must clear the
    current history and skip missing node IDs.
  - Stop using `saveProperties()` iteration over every map view as the
    authority for persisted navigation state. Instead, record
    navigation-state updates for `oldView` in `afterViewChange(...)`,
    record close-time state in `afterViewClose(...)`, and update only
    the currently selected view once more during `saveProperties()`.
  - In `selectLastVisitedNode(...)`, load persisted root-history node IDs
    into the target `MapView` before calling `setViewRoot(root)`. Do not
    materialize ancestor paths there.
  - Rework `MapView.usePreviousViewRoot()` to resolve the broader root as
    a `NodeModel`. When the previous root has no current same-map
    `NodeView`, create a new orphan root view for that node instead of
    forcing ancestor unfolding.
  - After installing the broader root during jump out, call
    `display(oldRootNode)` to reveal only the ancestor path needed to
    show the old root in the active view.
  - Determine jump-out selection after that reveal. Keep the previously
    selected descendant only if it is still content-visible. Otherwise,
    select the old root. Do not call `display(previouslySelectedNode)`.
  - When `restoreRootNode()` clears history on non-temporary root
    restoration, resolve each stored `NodeModel` back to a same-map
    `NodeView` when available and call `keepUnfolded()` only for those
    resolved views.
  - If restored history is stale and the old root is no longer a
    descendant of the broader root, skip the old-root reveal and select
    the broader root.
- **Test specification:**
  - **Automated tests:**
    - `LastOpenedListTest`
      - `selectLastVisitedNodeRestoresRootHistoryWithoutUnfoldingAncestors`:
        startup restore loads persisted root history into the current
        `MapView`, restores only the saved root, and does not unfold the
        ancestor path at load time.
      - `afterViewChangeAndSavePropertiesPersistNavigationStateFromLastActiveSameMapView`:
        when two views of the same map exist, the last active view for
        that map supplies `lastVisitedNodeId`, `lastRootNodeId`, and
        `lastRootHistoryNodeIds`.
      - `savePropertiesStoresNestedRootHistoryListPerRecentFile`:
        nested root-history serialization writes and restores one
        per-map stack entry for each recent file.
    - `MapViewJumpOutContextRestorationTest`
      - `usePreviousViewRootRevealsOldRootPathOnMapRootFallback`:
        empty history falls back to the map root and reveals the old
        root path without unfolding the old root itself.
      - `usePreviousViewRootRestoresPreviousRootFromModelHistory`:
        persisted model history restores a previous non-root broader root
        even when no current `NodeView` exists for it.
      - `usePreviousViewRootSelectsOldRootWhenDeeperSelectionRemainsHidden`:
        a deeper descendant selection falls back to the old root when
        the old root stays folded in the restored broader context.
  - **Manual tests:**
    - Reproduce the restart case from `#2940`: reopen a map inside a
      jumped-in root, use jump out, and verify that broader context is
      restored without startup ancestor unfolding.
    - Jump into a first-level child from the map root, restart, then
      jump out and verify that the map-root fallback still reveals the
      old root path.
    - Open a bookmark as root from the map root, use jump out, and
      verify that the same map-root fallback reveal rule applies there
      too.
- **Implementation notes:**
  - **Tradeoffs:**
    - Delayed `LastOpenedList` map-view-change listener registration
      until `FreeplaneGUIStarter.finishStartup()` instead of adding a
      startup guard inside `LastOpenedList`, so startup map switches do
      not snapshot pre-restore view state while deferred
      `afterViewDisplayed(...)` restoration still reaches the listener
      once the hidden content pane becomes visible.
    - Stopped updating persisted navigation state in
      `LastOpenedList.afterViewClose(...)` because a background close of
      another same-map view would otherwise overwrite the chosen
      last-active-view policy with close-order noise.
