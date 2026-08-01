# Task: Refactor search-result temporary unfolding to MapView
- **Task Identifier:** 2026-08-01-search-unfold-refactor
- **Scope:** Replace `org.freeplane.features.filter.FoundNodes`
  with a `MapView`-owned search-result unfolding helper for
  `FindAction` and `QuickFindAction`, using `NodeViewFolder` as the
  temporary fold-state primitive or extracting only the smallest
  additional shared mechanism needed. Preserve current search
  navigation behavior while moving temporary fold tracking from
  `MapModel` extensions to the active `MapView`. Exclude node-filter
  auto-unfold mode, search matching changes, outline or tag panel
  behavior, and unrelated selection or root-navigation refactors.
- **Motivation:** `FoundNodes` currently stores search path state on
  `MapModel` even though its behavior is applied against the current
  selection root and current view. That ownership mismatch makes the
  behavior harder to reason about and weakens reuse for later
  view-scoped folding features. A dedicated view-owned search helper
  would make lifecycle, multi-view behavior, and any future shared
  mechanism with filter folding explicit.
- **Scenario:**
  - A user repeats Quick Find in one map view. Search temporarily opens
    only the branches needed to display the current found node in that
    view and folds back only branches previously opened by search in
    that same view when they stop being part of the active found path.
  - The same map is shown in another view. Search-result temporary
    unfolding in the active view does not implicitly claim ownership of
    folding state in the other view.
- **Constraints:**
  - Preserve current find and quick-find user-visible navigation
    results unless the task explicitly documents and approves a
    behavior change.
  - Keep temporary search-result fold state per `MapView`, not per
    `MapModel`.
  - Remove `FoundNodes` rather than keeping parallel old and new
    search-folding paths.
  - Reuse `NodeViewFolder` directly when it suffices; extract only the
    smallest additional mechanism needed for search-specific fold-back
    policy.
  - Do not couple this refactor to the filter auto-unfold mode
    implementation.
- **Briefing:** Current search-result path management lives in
  `FoundNodes`, a `MapModel` extension used by `FindAction` and
  `QuickFindAction`. `FoundNodes.displayFoundNode(...)` stores node
  IDs, folds back the previous found path according to the current
  selection root, and relies on normal display or selection flow to
  open the new path. `NodeViewFolder` is already a view-scoped
  temporary folding helper used by `MapView` and drag or drop paths.
- **Research:**
  - `FoundNodes.get(map)` stores one instance per `MapModel`.
  - `FoundNodes` tracks `LinkedList<String> nodesUnfoldedByDisplay`,
    not `NodeView` instances.
  - `FoundNodes.displayFoundNode(...)` reads current selection root and
    current folding state, so its behavior already depends on active
    view context.
  - `MapView.display(...)` and `NodeView.setFolded(...)` are the
    current view-side unfolding path.
  - Freeplane supports multiple visible `MapView`s for one map.
- **Analysis:**
  - `FoundNodes` and `NodeViewFolder` share a temporary-folding theme
    but not the same current contract. `FoundNodes` also owns
    search-specific fold-back policy.
  - The refactor should move ownership first, then decide whether raw
    `NodeViewFolder` is enough or whether a thin search-specific
    wrapper is still required.
  - The key boundary is shared mechanism versus policy:
    `NodeViewFolder` can own temporary view fold state, while search
    navigation can remain in a search-specific owner if it still has
    distinct rules after the move.
- **Design:**
  - Introduce a `MapView`-owned search-result folding helper used only
    by search-navigation actions.
  - Make that helper use `NodeViewFolder` for temporary fold tracking
    and keep any remaining search-specific fold-back rules in the
    helper instead of on `MapModel`.
  - Update `FindAction` and `QuickFindAction` to target the current
    `MapView` helper instead of `FoundNodes`.
  - Remove `FoundNodes` after the new helper preserves the required
    behavior.
- **Test specification:**
  - Automated tests:
    - verify repeated find or quick-find folds back only branches
      previously opened by search in the same view;
    - verify user-open branches are not incorrectly claimed as
      search-owned;
    - verify search unfolding is view-scoped when multiple map views
      display the same map;
    - verify jump-in, jump-out, or root changes do not leave stale
      search-owned folding state behind.
  - Manual tests:
    - run find and quick-find repeatedly on folded matches and confirm
      only search-opened branches refold;
    - open two views of the same map, search in one, and confirm the
      other view does not inherit the same temporary search-owned fold
      state unless explicitly navigated there.
