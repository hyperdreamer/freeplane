# Task: Make Select matching nodes honor unfold matching branches
- **Ticket:** #2964
- **Scope:** When the quick-filter toolbar action `Select matching
  nodes` (`QuickFindAllAction`) runs while `Unfold matching
  branches` is selected and enabled, temporarily unfold the folded
  ancestor chains needed to expose the nodes that the action will
  select, then perform the existing visible-selection flow. Reuse the
  temporary folding machinery introduced by
  `2026-08-01-filter-auto-unfold` instead of adding a second
  search-owned unfolding path. Preserve current no-unfold behavior
  when the mode is inactive. Exclude logical hidden selection,
  broader search-result refactors, highlight changes, and unrelated
  filter history or navigation changes.
- **Motivation:** Issue `#2964` is still open for the select-action
  path. Commit `08f91c3d00` added a reusable view-owned temporary
  unfolding mode for filter results, but `QuickFindAllAction` does not
  drive it. Commit `377ae8ba1c` intentionally changed the action to
  visible-only traversal, so folded matches are skipped unless the new
  mode explicitly opens their ancestor branches first.
- **Scenario:**
  - A user enters a quick-filter condition, enables `Unfold matching
    branches`, and clicks `Select matching nodes`. Matches inside
    folded branches under the effective search root become unfolded
    and selected, while user-open branches stay preserved.
  - The same action runs while the mode is off or unavailable.
    Behavior stays as in `377ae8ba1c`: only matches already reachable
    in unfolded branches are selected.
  - The user has an active node filter that hides part of the map.
    `Select matching nodes` unfolds only nodes that both satisfy the
    quick condition and remain visible under the current active filter
    and search-root constraints.
  - After the action, if the user changes the selection, branches no
    longer needed by the new selection can fold back, but later
    filter, mode, or root recomputation must not hide the current
    selection.
- **Constraints:**
  - Reuse the `MapView` / `NodeViewFolder(false)` temporary folding
    mechanism from `2026-08-01-filter-auto-unfold`; do not add a
    parallel `FoundNodes`-style owner for this behavior.
  - Do not install a new active filter, create a filter-history entry,
    or alter quick-filter history just to drive unfolding.
  - The unfold driver set must match `QuickFindAllAction` semantics:
    direct node matches of the quick condition within the effective
    search root and current visible-filter constraints. `Hide matching
    nodes`, `Show descendants`, or connector-only filtering must not
    redefine which nodes get selected by this action.
  - Keep `377ae8ba1c` behavior unchanged when the unfold mode is
    inactive or disabled.
  - Keep the broader backlog task `2026-08-01-search-unfold-refactor`
    out of scope unless a tiny shared extraction is strictly needed.
- **Briefing:** `QuickFindAllAction` lives in
  `org.freeplane.features.filter` and currently loops with
  `filterController.findNext(..., Direction.FORWARD_VISIBLE,
  condition, selection.getFilter())`, selecting only nodes reachable
  through unfolded branches. The filter auto-unfold mode added in
  `08f91c3d00` lives on `FilterController` as a toolbar toggle and on
  `MapView` as `filterNodeViewFolder` plus
  `updateFilterNodeFolding()`, which unfolds ancestors for nodes
  marked by `Filter.isFilteredAsAncestor(...)`. `MapView
  .foldingWasSet(...)` already keeps that folder from claiming
  user-initiated fold changes. `Select matching nodes` does not
  currently apply a filter or fire the map-change hook that
  recomputes that folding state.
- **Research:**

  ```plantuml
  @startuml
  set separator none

  package "org.freeplane" {
    package "features" {
      package "filter" {
        class QuickFindAllAction {
          + actionPerformed(e)
        }
        class FilterConditionEditor {
          + getCondition() : ASelectableCondition
        }
        class FilterController {
          ~ findNext(from, end, direction, condition, filter) : NodeModel
          + isUnfoldMatchingBranchesSelected() : boolean
        }
        class Filter {
          + getCondition() : ICondition
          + isFilteredAsAncestor(node) : boolean
        }
        class ASelectableCondition
        class ICondition
      }

      package "map" {
        class MapController {
          + displayNode(node)
        }
        interface IMapSelection {
          + getSelected() : NodeModel
          + getEffectiveSearchRoot() : NodeModel
          + getFilter() : Filter
          + selectAsTheOnlyOneSelected(node)
          + toggleSelected(node)
          + makeTheSelected(node)
        }
        class NodeModel
      }
    }

    package "view.swing.map" {
      class MapView {
        + mapChanged(event)
      }
      class NodeViewFolder {
        + adjustFolding(unfoldNodeViews)
        + foldingWasSet(nodeView)
      }
    }
  }

  QuickFindAllAction --> FilterConditionEditor : reads quick condition
  QuickFindAllAction --> IMapSelection : reads root / selection\nupdates selection
  QuickFindAllAction --> FilterController : findNext(... FORWARD_VISIBLE ...)
  QuickFindAllAction --> MapController : displayNode(next)
  IMapSelection --> Filter : returns active filter
  MapView --> FilterController : reads unfold mode state
  MapView --> Filter : reads ancestor flags
  MapView o--> NodeViewFolder : temporary filter folding
  @enduml
  ```

  ```plantuml
  @startuml
  actor User
  participant "QuickFindAllAction" as QuickFindAllAction
  participant "FilterConditionEditor" as FilterConditionEditor
  participant "IMapSelection" as Selection
  participant "FilterController" as FilterController
  participant "MapController" as MapController

  User -> QuickFindAllAction : click Select matching nodes
  QuickFindAllAction -> FilterConditionEditor : getCondition()
  FilterConditionEditor --> QuickFindAllAction : condition
  QuickFindAllAction -> Selection : getSelected()\ngetEffectiveSearchRoot()\ngetFilter()
  Selection --> QuickFindAllAction : selected + searchRoot + filter
  loop visible matches
    QuickFindAllAction -> FilterController : findNext(next, searchRoot, FORWARD_VISIBLE, condition, filter)
    FilterController --> QuickFindAllAction : next visible match or null
    QuickFindAllAction -> MapController : displayNode(next)
    QuickFindAllAction -> Selection : selectAsTheOnlyOneSelected(next)\nor toggleSelected(next)
  end
  QuickFindAllAction -> Selection : makeTheSelected(searchStart)
  @enduml
  ```

  Research notes:
  - `QuickFindAllAction` switched from `Direction.FORWARD` to
    `Direction.FORWARD_VISIBLE` in commit `377ae8ba1c`, so traversal
    no longer enters folded branches before deciding which nodes
    match.
  - `QuickFindAllAction` never asks `MapView` to reuse
    `filterNodeViewFolder`, so folded matches stay unreachable unless
    another path has already unfolded their ancestors.
  - `MapView.updateFilterNodeFolding()` already computes ancestor
    views from a `Filter` and reuses
    `filterNodeViewFolder.adjustFolding(...)` to unfold needed
    branches and later refold only branches it opened.
  - `Filter` can compute ancestor flags for an arbitrary condition
    without becoming the active selection filter, and
    `appliesToVisibleElementsOnly` with a `baseFilter` limits that
    driver to nodes still accepted by the current active filter.
  - `QuickFindAllAction` searches only inside
    `selection.getEffectiveSearchRoot()`, so any unfolding driver
    broader than that subtree would exceed current action scope.
- **Analysis:**
  - The shared mechanism worth reusing is the view-owned temporary fold
    tracker plus ancestor-collection logic, not the filter-history
    path. The behavior difference is only the driver-filter source.
  - The quick-select path needs an ephemeral unfold-driver filter
    whose direct matches mirror `QuickFindAllAction` selection
    semantics. Reusing the actual toolbar filter options wholesale
    would be wrong because `Hide matching nodes` or descendant
    expansion changes filter-visibility semantics but does not change
    what this action means by “matching nodes”.
  - The unfold request should be rooted at the effective search root,
    because opening ancestor chains for matches outside the action's
    subtree would be a visible behavior change unrelated to the
    user's selection request.
  - Reusing `filterNodeViewFolder` is acceptable because the next
    ordinary filter, mode, or root recomputation already knows how to
    clear branches that the folder opened and that are no longer in
    the current driver set.
- **Design:**

  ```plantuml
  @startuml
  set separator none

  package "org.freeplane" {
    package "features" {
      package "filter" {
        class QuickFindAllAction {
          + actionPerformed(e)
        }
        class FilterConditionEditor {
          + getCondition() : ASelectableCondition
        }
        class FilterController {
          ~ createQuickSelectionFilter(condition, selection) : Filter
          ~ unfoldMatchingBranchesForQuickSelection(filter, selection)
          ~ findNextMatching(from, end, direction, matches, filter) : NodeModel
        }
        class Filter {
          + calculateFilterResults(root)
          + getFilterInfo(node) : FilterInfo
          + isFilteredAsAncestor(node) : boolean
        }
        class ASelectableCondition
      }

      package "map" {
        interface IMapSelection {
          + getSelected() : NodeModel
          + getEffectiveSearchRoot() : NodeModel
          + getFilter() : Filter
          + selectAsTheOnlyOneSelected(node)
          + toggleSelected(node)
          + makeTheSelected(node)
        }
        class NodeModel
      }
    }

    package "view.swing.map" {
      class MapView {
        + unfoldMatchingBranches(foldingFilter, foldingRoot)
      }
      class NodeViewFolder {
        + adjustFolding(unfoldNodeViews)
      }
    }
  }

  QuickFindAllAction --> FilterConditionEditor : reads quick condition
  QuickFindAllAction --> FilterController : resolves optional helper filter
  QuickFindAllAction --> IMapSelection : reads root / selection\nupdates selection
  FilterController --> Filter : creates ephemeral unfold driver
  FilterController --> MapView : unfolds matching branches
  MapView --> Filter : reads precomputed FilterInfo
  MapView o--> NodeViewFolder : shared temporary folding owner
  @enduml
  ```

  ```plantuml
  @startuml
  actor User
  participant "QuickFindAllAction" as QuickFindAllAction
  participant "FilterConditionEditor" as FilterConditionEditor
  participant "IMapSelection" as Selection
  participant "FilterController" as FilterController
  participant "Filter" as Filter
  participant "MapView" as MapView
  participant "NodeViewFolder" as NodeViewFolder

  User -> QuickFindAllAction : click Select matching nodes
  QuickFindAllAction -> FilterConditionEditor : getCondition()
  FilterConditionEditor --> QuickFindAllAction : quick condition
  QuickFindAllAction -> Selection : getSelected()\ngetEffectiveSearchRoot()\ngetFilter()
  Selection --> QuickFindAllAction : selected + searchRoot + activeFilter
  QuickFindAllAction -> FilterController : createQuickSelectionFilter(condition, selection)
  alt unfold mode active and helper filter available
    FilterController --> QuickFindAllAction : helper filter
    QuickFindAllAction -> Filter : calculateFilterResults(searchRoot)
    QuickFindAllAction -> FilterController : unfoldMatchingBranchesForQuickSelection(helper filter, selection)
    FilterController -> MapView : unfoldMatchingBranches(helper filter, searchRoot)
    loop recompute folded ancestors
      MapView -> Filter : isFilteredAsAncestor(node)
      MapView -> NodeViewFolder : adjustFolding(unfoldNodeViews)
    end
    QuickFindAllAction -> QuickFindAllAction : matches = helper FilterInfo::isMatched
  else unfold mode inactive
    FilterController --> QuickFindAllAction : null
    QuickFindAllAction -> QuickFindAllAction : matches = condition::checkNode
  end
  loop visible targets
    QuickFindAllAction -> FilterController : findNextMatching(next, searchRoot, FORWARD_VISIBLE, matches, activeFilter)
    FilterController --> QuickFindAllAction : next visible target or null
    QuickFindAllAction -> Selection : selectAsTheOnlyOneSelected(next)\nor toggleSelected(next)
  end
  QuickFindAllAction -> Selection : makeTheSelected(searchStart)
  @enduml
  ```

  Design notes:
  - Keep ordinary filter-mode and selection-driven recomputation on
    the same `MapView` folding path. `QuickFindAllAction` still
    supplies only a temporary driver `Filter`, while `MapView`
    separates filter-owned, pending quick-selection, and
    selection-owned requests inside the shared `NodeViewFolder` owner.
  - Quick select keeps using the raw quick condition as its target
    predicate. The filter toolbar's `Hide matching nodes` option is a
    real-filter visibility mode and does not redefine what this action
    means by `matching nodes`.
  - Build the helper `Filter` only when `Unfold matching branches` is
    active and node filtering is still applicable. That keeps the
    mode-off path on the existing visible-only lazy evaluation.
  - In the helper-filter path, `QuickFindAllAction` calculates the
    filter once and reuses its `FilterInfo` for both unfolding and
    visible-node selection. `MapView.unfoldMatchingBranches(...)`
    therefore consumes precomputed results instead of recalculating
    the quick condition.
  - `MapView` keeps the quick-selection request pending until the
    selection set updates, then replaces it with ancestor requests for
    the current selection. That lets later recomputation refold stale
    quick-selection branches without hiding the new selection.
  - The selection-driven folding lifecycle is mutually exclusive and
    should be represented explicitly as an enum state, not as a
    boolean flag. The meaningful states are: inactive, pending
    quick-selection handoff, and following the current selection.
  - `QuickFindAllAction` still keeps `Direction.FORWARD_VISIBLE`; the
    helper only changes which branches become visible before the
    shared visible traversal runs.
- **Test specification:**
  - **Automated tests:**
    - `QuickFindAllActionUnfoldMatchingBranchesModeTest`
      - `requestsSharedUnfoldingBeforeVisibleSelectionTraversalWhenHelperFilterIsAvailable`:
        the helper-filter path calculates the quick filter, unfolds
        matching branches, and only then starts `FORWARD_VISIBLE`
        traversal.
      - `selectsEveryMatchReturnedByVisibleTraversal`: the shared
        visible traversal still selects the first visible target and
        toggles each subsequent target.
      - `usesRawQuickSelectionConditionForLazySelectionWhenNoHelperFilterIsAvailable`:
        mode-off quick select uses the raw quick condition during
        visible traversal.
      - `usesCalculatedQuickSelectionFilterResultsForSelectionWhenHelperFilterIsAvailable`:
        mode-on quick select selects from precomputed helper-filter
        results instead of reevaluating the condition per visible node.
    - `MapViewFilterAutoUnfoldModeTest`
      - `unfoldsQuickSelectionMatchesWithinTheEffectiveSearchRoot`:
        the precomputed quick-selection driver unfolds matching
        branches inside its root and does not unfold a separate branch
        outside that root.
      - `doesNotUnfoldTheEffectiveSearchRootWhenItHasNoQuickSelectionMatch`:
        an empty precomputed quick-selection result does not unfold
        the search root.
      - `doesNotUnfoldQuickSelectionMatchesHiddenByTheActiveFilter`:
        a quick target rejected by the active filter does not drive
        unfolding.
      - `preservesActiveFilterUnfoldingWhileQuickSelectionAddsBranches`:
        quick-selection unfolding preserves active filter branches,
        and later recomputation keeps the quick-selection branch open
        while it still contains the current selection.
      - `selectionChangeRefoldsQuickSelectionBranchesThatAreNoLongerSelected`:
        when the selection changes after quick select, the shared
        folder refolds only the no-longer-selected quick-selection
        branch.
      - `filterRecomputationDoesNotHideTheCurrentSelectionAfterQuickSelectionDrivenUnfolding`:
        later filter recomputation preserves the branch needed for the
        current selection even after other quick-selection branches
        have folded back.
      - existing filter-mode cases remain passing, including
        `unfoldsMatchingBranchesAndPreservesUserOpenedBranches` and
        `clearsTrackedBranchesWhenConnectorFilteringIsSelected`.
    - `FilterControllerToggleUnfoldMatchingBranchesActionTest`
      - `quickSelectionFilterIsCreatedOnlyWhenModeIsActive`:
        the controller creates a helper filter only while unfold mode
        is active and connector-only filtering is not selected.
      - `quickSelectionUnfoldingDelegatesToMapView`:
        the controller forwards a precomputed helper filter to the
        current `MapView` together with the effective search root.
  - **Manual tests:**
    - In the quick-filter toolbar, enter a condition with matches in
      folded branches, enable `Unfold matching branches`, click
      `Select matching nodes`, and verify that all targets inside the
      current search root become visible and selected.
    - Repeat with the mode off and verify that only targets already in
      unfolded branches are selected.
    - Repeat with `Hide matching nodes` enabled and verify that quick
      select still targets the raw quick-condition matches rather than
      the hidden complement.
    - Repeat with an active node filter that hides part of the map and
      verify that quick select unfolds and selects only targets still
      visible through that active filter.
    - After selecting multiple matches from folded branches, change
      the selection to one result and verify that unrelated
      quick-opened branches refold while later hide/filter
      recomputation keeps the new selection visible.
    - Switch the filtered-element mode to connectors only, confirm the
      toggle is unavailable, run `Select matching nodes`, and verify
      that the action does not resurrect hidden unfold behavior from
      the stored toggle state.
- **Implementation notes:**
  - **Tradeoffs:**
    - Kept `Hide matching nodes` out of quick select. The action still
      targets raw quick-condition matches, while the helper filter is
      only a branch-unfolding aid constrained by the active filter.
    - Precompute the helper filter only in the unfold-on path. That
      preserves the existing visible-only lazy evaluation when the
      mode is off while still avoiding duplicate condition evaluation
      when the mode is on.
    - Keep quick-selection unfolding pending until the selection set
      changes, then convert it into selection-owned ancestor requests.
      That avoids hiding the pre-action selection before
      `QuickFindAllAction` finishes, while still allowing later
      selection changes to refold obsolete quick-selection branches.
    - Represent the selection-driven folding lifecycle with an enum
      state instead of a boolean because the implementation has three
      mutually exclusive modes: inactive, pending handoff, and
      following the current selection.
    - Prefer package-private test setup methods over reflection in the
      `MapView` folding tests. The test now uses explicit setup hooks
      for root attachment, displayed-root changes, and sibling-level
      initialization instead of reflective field writes.
