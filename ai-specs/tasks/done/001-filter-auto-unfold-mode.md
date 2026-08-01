# Task: Add filter mode for temporary match-branch unfolding
- **Task Identifier:** 2026-08-01-filter-auto-unfold
- **Scope:** Add a new main-map node-filter toolbar mode that
  temporarily unfolds the ancestor chain needed to expose nodes
  directly selected by the active node filter, updates that temporary
  state as filter results, mode state, or the displayed root change,
  and re-folds nodes that were auto-unfolded by the mode when they no
  longer serve any directly selected branch under the next
  recomputation. Keep nodes that were already unfolded before the mode
  acted unfolded. Treat nodes shown only through the `show
  descendants` option as non-driving for this folding state. Analyze
  the current temporary-unfold helpers around `FoundNodes` and
  `NodeViewFolder`, and apply only the smallest shared mechanism
  extraction or consolidation that preserves their distinct behavior.
  Exclude unrelated find or navigation behavior changes and exclude the
  outline and tag panels from this task.
- **Motivation:** Filtered results can currently remain hidden behind
  folded branches, and the product lacks a reusable main-map mode that
  reveals matching branches while restoring only the temporary folding
  changes that the mode itself introduced. The work also needs a
  structure review because `FoundNodes` and `NodeViewFolder` both manage
  temporary unfolding, but the current code suggests different owners
  and lifecycles that should be checked before any merge.
- **Scenario:**
  - A user enables `Unfold matching branches` in the main map filter
    toolbar and applies a node filter whose directly selected results
    sit below folded ancestors. The current map view opens those
    ancestors temporarily so the selected result branches are reachable
    in the displayed tree.
  - The user applies a different node filter while the mode stays on.
    Branches that are still needed for directly selected results remain
    open. Branches opened only by the mode and no longer needed close
    again. Branches the user had already opened stay open.
  - The user enables `Show descendants`. Nodes shown only because they
    descend from a directly selected result stay visible if the filter
    already makes them visible, but they do not cause unrelated
    ancestor paths to open.
  - The user switches filtering to connectors only. The toggle becomes
    disabled and the map view removes only the temporary folding state
    created by this mode. If the user later returns to node filtering
    while the toggle is still selected, the mode recomputes from the
    current node filter.
  - The user traverses filter history with Undo Filter or Redo Filter
    while `Unfold matching branches` remains selected. The restored
    filter semantics become active, the mode selection itself does not
    change, and the map view recomputes temporary unfolding for the
    restored filter. If history restores a connector-only filter, the
    toggle becomes disabled and temporary unfolding clears without
    clearing the stored mode selection.
  - The user turns the mode off. The map view removes only the
    temporary folding state created by this mode.
- **Glossary:**
  - `Unfold matching branches`: independent main-map toolbar mode that
    temporarily opens folded ancestor chains for node-filter results.
    - It changes folding only.
    - It is owned by `FilterController`, not by `Filter`.
    - It does not participate in filter undo/redo or quick-filter
      history.
  - `Direct filter result`: node directly selected by the current node
    filter before ancestor or descendant expansion is applied.
    - With normal filtering, it is a node whose condition matches.
    - With inverted filtering, it is a node whose condition does not
      match.

  ```mermaid
  flowchart LR
    mode["Unfold matching branches"] -->|"opens ancestor chain for"| direct["Direct filter result"]
    direct -->|"drives"| folding["Temporary fold state"]
    descendants["Show descendants"] -->|"does not drive"| folding
  ```
- **Constraints:**
  - The mode applies to main-map node filters generally, not only to
    quick-filter actions.
  - Only nodes that directly satisfy the active filter condition, or
    directly satisfy its negated predicate when hiding matches, may
    drive auto-unfold ancestry.
  - Nodes shown only because `show descendants` is enabled must remain
    outside the auto-unfold driver set.
  - Re-fold only nodes unfolded automatically by this mode; preserve
    user-chosen folding state.
  - Put the toggle in the filter toolbar result-visibility group,
    between `show descendants` and `hide matching nodes`.
  - Disable the toggle when filtering connectors only, without clearing
    its stored selected state.
  - Keep the mode outside filter undo/redo and quick-filter history.
  - Traversing filter history must not change the stored mode flag. It
    must recompute or clear temporary unfolding from the restored filter
    and filtered-element target.
  - Keep the outline and tag panels unchanged in this task.
  - If shared code is extracted between `FoundNodes` and
    `NodeViewFolder`, keep navigation-owned state and view-owned state
    separate unless a real common contract is proven.
- **Briefing:**
  - Filter semantics are assembled in
    `org.freeplane.features.filter.FilterController` and carried by
    `org.freeplane.features.filter.Filter`.
  - Main-map temporary fold state belongs in
    `org.freeplane.view.swing.map.MapView`, which already owns one
    `NodeViewFolder` instance for selection-follow folding.
  - `org.freeplane.features.filter.FoundNodes` is a map-owned search
    helper used by `FindAction` and `QuickFindAction`; it is not part of
    ordinary filter application.
  - `FilterController.updateSettingsFromHistory()` restores only state
    copied into `Filter` history, so a controller-owned toolbar mode can
    stay independent from filter undo/redo.
  - UI wiring for the new toggle also needs menu, translation, and icon
    updates under `freeplane/src/viewer/resources` and
    `freeplane/src/external/resources/xml`.
- **Research:**

  ```plantuml
  @startuml
  set separator none
  package "freeplane" {
    package "org.freeplane" {
      package "features.filter" {
        class FindAction {
          +findNext(direction)
        }
        class QuickFindAction {
          +executeAction(reFocusSearchInputField)
        }
        class FoundNodes {
          -LinkedList<String> nodesUnfoldedByDisplay
          +displayFoundNode(node)
        }
      }
      package "view.swing.map" {
        class MapView {
          -NodeViewFolder nodeViewFolder
          +foldingWasSet(nodeView)
        }
        class NodeViewFolder {
          -Set<NodeView> unfoldedNodeViews
          -boolean unfoldsSingleChildren
          +adjustFolding(unfoldNodeViews)
          +foldingWasSet(view)
          +reset()
        }
      }
      package "view.swing.ui.mindmapmode" {
        class MNodeDragListener {
          -NodeViewFolder nodeFolder
        }
        class MNodeDropListener {
          -NodeViewFolder nodeFolder
        }
      }
    }
  }
  FindAction --> FoundNodes : reuses search path
  QuickFindAction --> FoundNodes : reuses search path
  MapView o--> NodeViewFolder : selection-follow folding
  MNodeDragListener o--> NodeViewFolder : drag folding
  MNodeDropListener o--> NodeViewFolder : drop folding
  @enduml
  ```

  ```plantuml
  @startuml
  actor User
  participant FilterController
  participant Filter
  participant "MapController" as MapController
  participant MapView
  participant "MapViewController" as MapViewController
  participant MapAwareOutlinePane

  User -> FilterController : applyFilter(force)
  FilterController -> FilterController : createFilter(selectedCondition, baseFilter)
  FilterController -> Filter : calculateFilterResults(map)
  FilterController -> MapController : fireMapChanged(Filter.class)
  MapController -> MapView : mapChanged(Filter.class)
  MapView -> MapView : updateAllNodeViews()
  FilterController -> MapViewController : fireFilterChanged()
  MapViewController -> MapAwareOutlinePane : afterFilterChange(view, filter)
  @enduml
  ```

  - `FilterController.createFilter(...)` currently copies several
    toolbar toggles into a `Filter` instance. That pattern is suitable
    for filter semantics, but it would also couple a presentation-only
    folding mode to filter history and quick-filter icon rendering.
  - `Filter.calculateFilterResults(...)` always records the direct
    condition outcome as `MATCHES` or `NO_MATCH`, then adds separate
    ancestor and descendant flags. That is enough to distinguish direct
    filter results from nodes shown only because ancestors or
    descendants are also shown.
  - `FilterHistory.undo()` and `redo()` already restore a historical
    `Filter` by calling `FilterController.applyFilter(true, filter)`
    before `FilterController.updateSettingsFromHistory()` resynchronizes
    the toolbar from that restored `Filter`. That means history
    traversal already emits the normal `Filter.class` map-change path;
    the missing behavior is keeping the controller-owned mode selection
    unchanged while updating its enabled state from the restored
    filtered-element target.
  - `MapView.mapChanged(Filter.class)` already refreshes the main tree on
    every filter change. `MapViewController.fireFilterChanged()` is used
    by listeners such as `MapAwareOutlinePane`, so it is not the right
    hook for a main-map-only folding mode that should leave outline and
    tag panels unchanged.
  - `NodeViewFolder.adjustFolding(...)` already implements the required
    temporary-fold semantics: it unfolds the requested views with their
    ancestors, remembers only nodes it actually opened, and folds back
    remembered nodes that drop out of the next request set.
    `NodeViewFolder(false)` avoids the single-child expansion used by
    selection-follow behavior.
  - `FoundNodes.displayFoundNode(...)` serves a different lifecycle. It
    keeps map-level search-navigation paths by node ID and folds back the
    prior found-node path according to selection-root rules. It does not
    own per-view filter state.
  - `NodeView.isContentVisible()` and
    `NodeView.getAncestorWithVisibleContent()` show that visibility and
    folding are already separate concerns in the main tree.
- **Analysis:**
  - Keep visibility and folding independent because current `NodeView`
    behavior already supports visible descendants below hidden parents.
  - Keep `Unfold matching branches` on `FilterController` because it
    changes only main-map folding presentation and should stay outside
    `Filter` history and quick-filter history.
  - Reuse a view-owned `NodeViewFolder` for the new mode because it
    already preserves user-open branches and folds back only branches it
    opened itself.
  - Keep `FoundNodes` separate because it owns map-level search-path
    restoration, not view-level filter folding.
  - Derive the unfolding driver set from direct filter results only so
    that `show descendants` never opens extra branches.
  - Disable the mode for connector-only filtering because folding affects
    only node-tree presentation.
- **Design:**

  ```plantuml
  @startuml
  set separator none
  package "freeplane" {
    package "org.freeplane" {
      package "features.filter" {
        class FilterController {
          -ButtonModel unfoldMatchingBranches
          +createFilter(selectedCondition, baseFilter)
          +getUnfoldMatchingBranches()
          +isUnfoldMatchingBranchesSelected()
          +undo()
          +redo()
          -applyUnfoldMatchingBranchesMode()
          -updateSettingsFromFilter(filter)
          -updateSettingsFromHistory()
          -updateUnfoldMatchingBranchesAvailability()
        }
        class FilterHistory {
          +undo()
          +redo()
          +getCurrentFilter()
        }
        class Filter {
          +isFilteredAsAncestor(node)
          +getFilteredElement()
        }
        class ToggleUnfoldMatchingBranchesAction {
          +actionPerformed(e)
        }
        class FoundNodes {
          +displayFoundNode(node)
        }
      }
      package "view.swing.map" {
        class MapView {
          -NodeViewFolder filterNodeViewFolder
          +foldingWasSet(nodeView)
          +mapChanged(event)
          -updateFilterNodeFolding()
          -collectAncestorViews(rootView)
        }
        class NodeViewFolder {
          +adjustFolding(unfoldNodeViews)
          +foldingWasSet(view)
          +reset()
        }
      }
    }
  }
  FilterController o--> FilterHistory : owns filter history
  FilterController --> Filter : creates and restores
  FilterController --> ToggleUnfoldMatchingBranchesAction : registers
  MapView ..> FilterController : reads mode selection
  MapView --> Filter : checks ancestor state
  MapView o--> NodeViewFolder : filter-owned temporary folding
  @enduml
  ```

  ```plantuml
  @startuml
  actor User
  participant FilterController
  participant "MapController" as MapController
  participant MapView
  participant NodeViewFolder

  alt filter changes
    User -> FilterController : applyFilter(force)
    FilterController -> MapController : fireMapChanged(Filter.class)
    MapController -> MapView : mapChanged(Filter.class)
    MapView -> MapView : updateAllNodeViews()
    MapView -> MapView : updateFilterNodeFolding()
  else unfold mode toggles
    User -> FilterController : applyUnfoldMatchingBranchesMode()
    FilterController -> MapController : fireMapChanged(ToggleUnfoldMatchingBranchesAction.class)
    MapController -> MapView : mapChanged(ToggleUnfoldMatchingBranchesAction.class)
    MapView -> MapView : updateFilterNodeFolding()
  end
  alt mode active, node filter with condition, not connector-only
    MapView -> MapView : collectAncestorViews(currentRootView)
    MapView -> NodeViewFolder : adjustFolding(driverViews)
  else mode inactive or unsupported filter target
    MapView -> NodeViewFolder : adjustFolding(emptySet)
  end
  @enduml
  ```

  ```plantuml
  @startuml
  actor User
  participant FilterController
  participant FilterHistory
  participant "MapController" as MapController
  participant MapView
  participant NodeViewFolder

  User -> FilterController : undo() / redo()
  FilterController -> FilterHistory : undo() / redo()
  FilterHistory -> FilterController : applyFilter(true, restoredFilter)
  FilterController -> MapController : fireMapChanged(Filter.class)
  MapController -> MapView : mapChanged(Filter.class)
  MapView -> MapView : updateAllNodeViews()
  MapView -> MapView : updateFilterNodeFolding()
  alt mode selected and restored filter targets nodes
    MapView -> MapView : collectAncestorViews(currentRootView)
    MapView -> NodeViewFolder : adjustFolding(driverViews)
  else mode off or restored filter targets connectors
    MapView -> NodeViewFolder : adjustFolding(emptySet)
  end
  FilterController -> FilterController : updateSettingsFromHistory()
  FilterController -> FilterController : updateSettingsFromFilter(restoredFilter)
  FilterController -> FilterController : updateUnfoldMatchingBranchesAvailability()
  note right of FilterController
    Keep unfoldMatchingBranches selected state unchanged.
    Only enabled state follows restored filtered-element target.
  end note
  @enduml
  ```

  - Add `ToggleUnfoldMatchingBranchesAction` alongside the existing filter
    mode actions. It mirrors the other toggle actions, binds to a new
    `ButtonModel unfoldMatchingBranches`, and updates its enabled state
    whenever the filtered-element selection changes.
  - Keep the mode on `FilterController`, not on `Filter`. Do not add a
    new `Filter` constructor parameter and do not change `Filter`-
    owned equality, hash, or icon behavior for this mode.
  - Add `Filter.isFilteredAsAncestor(NodeModel node)` so the ancestor-flag test
    stays in the filter layer without carrying the new mode state:
    - return `false` when no condition is active;
    - for ordinary filtering, return `true` only when the node's
      `FilterInfo` contains `SHOW_AS_MATCHED_ANCESTOR`;
    - for inverted filtering, return `true` only when the node's
      `FilterInfo` contains `SHOW_AS_HIDDEN_ANCESTOR`;
    - ignore direct-match and descendant-only flags.
  - Extend `FilterController` with the new button model, toolbar toggle,
    action registration, persisted default property, translation keys,
    icon property, and menu entry. Place the toggle between `show
    descendants` and `hide matching nodes`. Disable it when
    `FilteredElement.CONNECTOR` is selected.
  - Update `updateUI()` and `updateSettingsFromFilter(...)` to call
    `updateUnfoldMatchingBranchesAvailability()` after the filtered-
    element button models are synchronized. That must change only the
    toggle's enabled state, never its selected state.
  - Use these exact external identifiers for the new mode:
    - action key and class name: `ToggleUnfoldMatchingBranchesAction`
    - persisted default property key:
      `filter.unfoldMatchingBranches`
    - translation key: `ToggleUnfoldMatchingBranchesAction.text`
    - icon property:
      `ToggleUnfoldMatchingBranchesAction.icon=/images/filter_unfolds.svg?useAccentColor=true`
  - Keep the mode outside `FilterController.createFilter(...)`,
    `applyNoFiltering(MapModel)`, `updateSettingsFromFilter(...)`, and
    `updateSettingsFromHistory()`. Toggling the mode must not add a new
    filter-history entry and filter undo or redo must not change the
    mode state.
  - Add `FilterController.applyUnfoldMatchingBranchesMode()` that fires
    `MapController.fireMapChanged(...)` with property
    `ToggleUnfoldMatchingBranchesAction.class` for the current map. Do not use
    `MapViewController.fireFilterChanged()` for this mode.
  - Add a dedicated `NodeViewFolder filterNodeViewFolder = new
    NodeViewFolder(false)` to `MapView`. Do not reuse the existing
    `nodeViewFolder`, because that helper is tied to selection-follow
    behavior and single-child unfolding.
  - Add `MapView.updateFilterNodeFolding()` and call it after the view
    refreshes for `Filter.class` changes, after mode-toggle map-change
    events, and after root changes. Because filter undo and redo reuse
    the same `Filter.class` refresh path, this method also becomes the
    history-traversal recomputation hook. The method scans only the
    currently displayed `NodeView` tree, unfolds folded node views whose
    nodes have the active ancestor flag, and repeats until no deeper
    folded ancestor views remain under `currentRootView`.
  - When the mode is inactive, no condition is active, or filtering is
    connectors only, `MapView.updateFilterNodeFolding()` calls
    `filterNodeViewFolder.adjustFolding(Collections.emptySet())` so only
    mode-opened branches close again.
  - Keep the stored mode state selected when connector-only filtering is
    chosen, but disable the action and suppress unfolding until node
    filtering becomes active again.
  - Update `MapView.foldingWasSet(NodeView nodeView)` so manual folding
    or unfolding also notifies `filterNodeViewFolder`. That keeps user
    changes from remaining classified as mode-owned state until the next
    recomputation.
  - Leave `FoundNodes` unchanged unless implementation reveals a tiny,
    clearly shared ancestor-closure helper worth extracting. Do not merge
    it with `NodeViewFolder`; their state owners and lifecycles remain
    different.
  - Keep `MapAwareOutlinePane` and the tag panels unchanged in this task
    even though they can build `Filter` instances too.
- **Test specification:**
  - **Automated tests:**
    - `FilterAncestorTest`
      - `marksOnlyAncestorsForOrdinaryFiltering`:
        ordinary filtering marks only ancestor-flagged nodes as
        ancestors; direct-match and descendant-only nodes are excluded.
      - `marksOnlyAncestorsWhenMatchesAreHidden`:
        inverted filtering marks only hidden-match ancestor nodes as
        ancestors.
    - `MapViewFilterAutoUnfoldModeTest`
      - `unfoldsMatchingBranchesAndPreservesUserOpenedBranches`: active
        mode unfolds folded ancestors for direct filter results while
        leaving already-open branches outside mode-owned fold-back state.
      - `refoldsOnlyPreviouslyAutoUnfoldedBranchesWhenDriverSetChanges`:
        the next recomputation folds back only branches previously opened
        by the mode and no longer needed by the direct-result set.
      - `clearsTrackedBranchesWhenModeTurnsOffOrConnectorFilteringIsSelected`:
        turning the mode off or switching to connector-only filtering
        folds back only mode-opened branches.
      - `recomputesTemporaryFoldingForTheRestoredFilterWhenHistoryIsTraversed`:
        with the mode selected, undoing or redoing filter history
        recomputes temporary folding from the restored filter instead of
        leaving the prior driver set in place.
      - `recomputesAgainstTheDisplayedRootAfterRootChanges`: jump-in or
        jump-out with an active node filter recomputes temporary folding
        against the currently displayed root subtree.
    - `FilterControllerToggleUnfoldMatchingBranchesActionTest`
      - `keepsUnfoldMatchingBranchesOutsideFilterHistory`: toggling the
        mode does not change created `Filter` instances and filter undo
        or redo do not change the mode state.
      - `disablesTheToggleForConnectorOnlyFilteringWithoutClearingItsState`:
        connector-only filtering disables the action while preserving the
        stored selected state for later node filtering.
      - `historyTraversalUpdatesAvailabilityWithoutOverwritingModeSelection`:
        when undo or redo restores a connector-only or node-targeting
        filter, the toggle enablement follows the restored filtered-
        element target while the stored selected state remains unchanged.
  - **Manual tests:**
    - In the main map filter toolbar, verify that `Unfold matching
      branches` appears between `Show descendants` and `Hide matching
      nodes` and becomes disabled when connector-only filtering is
      selected.
    - With a folded branch containing a direct filter result and
      `Show descendants` enabled, verify that only branches needed for
      direct filter results open and that descendant-only visible nodes
      do not open additional branches.
    - With the mode selected, create at least two filter-history entries
      plus one connector-only entry, then use Undo Filter and Redo
      Filter. Verify that the mode stays selected throughout, temporary
      unfolding recomputes for restored node filters, and connector-only
      history entries disable the toggle and clear only mode-opened
      branches.
- **Implementation notes:**
  - **Interpretations:**
    - Treated `NoFilteringCondition` as producing no active ancestor
      state so the mode does not unfold the whole displayed tree when no
      effective node filter is active.
  - **Tradeoffs:**
    - Kept ancestor-state checks in `Filter` and reused
      `NodeViewFolder(false)` only as the temporary folding tracker.
      `MapView` now iteratively unfolds folded ancestor views instead of
      broadening `NodeViewFolder` or scanning model nodes outside the
      existing displayed view tree.
