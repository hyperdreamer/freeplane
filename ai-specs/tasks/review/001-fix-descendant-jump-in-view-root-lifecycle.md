# Task: Fix descendant jump-in view root lifecycle
- **Ticket:** #2941
- **Scope:** Replace the current PR #2943 symptom patch with a
  root-swap fix that keeps a reused descendant `NodeView` and its
  subtree valid when it becomes the view root after jumping into a node
  from a detached current root. Preserve current jump-out behavior.
  Exclude unrelated map-view refactors and separate jump-out/history
  work tracked under `#2940`.
- **Motivation:** The current proposed fix re-registers the new root
  view after a destructive root swap. That likely restores immediate
  child rendering for the reported bug, but it leaves the wrong
  lifecycle in place: the jump-in path still destroys the reused
  subtree before repairing one lost viewer registration. The task is to
  fix that path at the source.
- **Scenario:** Freeplane reopens a map inside a previously jumped-in
  node. The user jumps into one of that node's already visible children
  and then creates a new child under the current view root. The new
  child must appear immediately, and jumping back out must keep the map
  view consistent without stale or duplicate same-map node viewers.
- **Constraints:**
  - Keep the fix localized to `MapView` / `NodeView` root-swap
    lifecycle. Do not do a broader view-root refactor.
  - Do not change root-history persistence, startup restore policy, or
    jump-out ancestor-path reveal behavior in this task; those belong to
    separate `#2940` work.
  - Preserve current `rootsHistory` and jump-out behavior.
  - Do not leave detached same-map `NodeView` instances registered in
    `NodeModel.getViewers()`.
  - Do not keep parallel old/new execution paths or a symptom-only
    fallback once the root-cause fix exists.
- **Briefing:** `MapView.setRootNode(NodeModel)` classifies descendant
  reuse as `RootChange.JUMP_IN`. `MapView.setRootNode(NodeView,
  RootChange)` calls `restoreRootNodeTemporarily()` before installing
  the new root. `restoreRootNode(int, boolean)` either reattaches the
  current root to its parent or destroys it through
  `currentRootView.remove()`. `NodeView.remove()` recursively removes
  child views, fires view-removal hooks, deregisters each view from its
  `NodeModel`, and deselects it. `MapView.getNodeView(NodeModel)`
  returns the first same-map `NodeView` it finds in
  `node.getViewers()`.
- **Research:**
  ```plantuml
  @startuml
  title Current descendant jump-in from a detached current root
  actor User
  participant "MapView" as MapView
  participant "currentRootView\n(detached NodeView)" as CurrentRoot
  participant "newRootView\n(descendant NodeView)" as NewRoot
  participant "NodeModel\nfor new root node" as NewRootNode

  User -> MapView : setRootNode(targetNode)
  MapView -> MapView : getNodeView(targetNode)
  MapView -> MapView : rootChange = JUMP_IN
  MapView -> MapView : restoreRootNodeTemporarily()
  MapView -> CurrentRoot : remove()
  CurrentRoot -> NewRoot : remove()
  NewRoot -> NewRootNode : removeViewer(newRootView)
  MapView -> MapView : currentRootView = newRootView
  MapView -> MapView : add(newRootView, ROOT_NODE_COMPONENT_INDEX)
  User -> NewRootNode : insert child
  NewRootNode -> NewRootNode : fireNodeInserted(...)
  @enduml
  ```
  - `MapView.setRootNode(NodeModel)` reuses an existing `NodeView`
    when `getNodeView(node)` finds one and marks descendant reuse as
    `RootChange.JUMP_IN`.
  - In the reported `#2941` path,
    `restoreRootNodeTemporarily()` enters the
    `currentRootParentView == null` branch, so it calls
    `currentRootView.remove()` instead of reattaching the detached
    root to a parent.
  - `NodeView.remove()` recursively removes child views and calls
    `getNode().removeViewer(this)` for every removed `NodeView`,
    including the descendant view that is about to become the new root.
  - `NodeModel.fireNodeInserted(...)` notifies only the viewers that
    remain registered on the parent node.
  - `MapView.getNodeView(NodeModel)` iterates `node.getViewers()` and
    returns the first `NodeView` whose `getMap() == this`, so leaving a
    detached same-map view registered would be unsafe.
  - PR `#2943` adds a post-swap repair in `MapView.setRootNode(...)`
    that re-adds `newRootView` to its node's viewers if needed. That
    patch does not change the destructive temporary-restore branch.
  - Current tests under `org/freeplane/view/swing/map` cover layout
    behavior but do not currently exercise a displayable `MapView`
    root-swap regression for this path.
- **Analysis:**
  - The fix should preserve the reused descendant subtree during
    `RootChange.JUMP_IN`, because the current bug comes from destroying
    that subtree before reusing its root.
  - The detached-root removal branch must not be deleted
    unconditionally, because obsolete same-map views would otherwise
    stay in `NodeModel.getViewers()` and could be returned by
    `MapView.getNodeView(NodeModel)`.
  - The PR `#2943` viewer re-registration patch should be removed once
    the root-swap lifecycle keeps the reused root registered, so that
    one root-cause fix remains instead of a destructive path plus a
    repair.
  - This lifecycle fix is independent of `#2940` jump-out/history
    decisions, because the same destructive descendant-reuse path can be
    triggered by orphan roots created through restart restore, bookmark
    `open as root`, or any other `setViewRoot(...)` caller.
- **Design:**
  ```plantuml
  @startuml
  title Target jump-in root swap without destroying reused subtree
  actor User
  participant "MapView" as MapView
  participant "currentRootView\n(detached NodeView)" as CurrentRoot
  participant "newRootView\n(descendant NodeView)" as NewRoot
  participant "NodeModel\nfor new root node" as NewRootNode

  User -> MapView : setRootNode(targetNode)
  MapView -> MapView : getNodeView(targetNode)
  MapView -> MapView : rootChange = JUMP_IN
  MapView -> MapView : remove(ROOT_NODE_COMPONENT_INDEX)\nwithout NodeView.remove()
  MapView -> MapView : currentRootView = newRootView
  MapView -> MapView : add(newRootView, ROOT_NODE_COMPONENT_INDEX)
  User -> NewRootNode : insert child
  NewRootNode -> NewRoot : onNodeInserted(parent, child, index)
  @enduml
  ```
  - Keep `restoreRootNodeTemporarily()` non-destructive only for the
    descendant-reuse jump-in case: when `rootChange == JUMP_IN` and the
    current root is already detached from its parent, remove the
    current root component from `MapView` without calling
    `NodeView.remove()`.
  - Preserve the existing reattach behavior when
    `currentRootParentView != null`.
  - Preserve destructive `NodeView.remove()` cleanup only for paths
    that abandon a detached root instead of reusing a descendant from
    its live subtree.
  - After the non-destructive detach, install `newRootView` as the
    current root without re-registering it manually; its existing
    viewer registration should remain intact.
  - Remove the PR `#2943` post-swap `addViewer(...)` repair once the
    non-destructive jump-in path is in place.
  - Keep the implementation local to the jump-in swap path. Do not add
    startup ancestor unfolding, root-history persistence, or jump-out
    selection policy changes here.
  - Re-check jump-out from the reused root to ensure the preserved
    detached root and `rootsHistory` still produce the original
    ancestor view without duplicate registrations.
- **Test specification:**
  - Manual tests:
    - Follow the `#2941` reproducer on a build with the change:
      restore a jumped-in root after restart, jump into one of its
      visible children, create a child under that node, and verify that
      the child appears immediately without a later jump-out rebuild.
    - From the same session, jump back out and confirm that the parent
      subtree, selection, and inserted child stay visible and
      consistent.
    - Repeat jump-in and jump-out on a map that was not restored after
      restart and confirm that ordinary root changes still behave as
      before, with no invisible insertions or duplicated views.
