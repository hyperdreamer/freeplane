# Task: Skip redundant sibling refresh when numbering is off
- **Task Identifier:** 2026-07-26-unnumbered-refresh
- **Scope:** Reduce move and promote cost by changing
  `NodeView.numberChanged(int)` so it skips
  `MainView.updateText(NodeModel)` for following siblings whose own
  numbering is disabled. Preserve current refresh behavior for
  numbered siblings and their numbered descendant paths, and add
  focused regression coverage for the move-triggered numbering refresh
  path. Exclude batching or coalescing of numbering refresh; that is
  tracked separately.
- **Motivation:** On unnumbered maps, move and promote operations
  still re-measure every following sibling's text after each remove or
  insert event even when no displayed number can change. That work
  dominates the reported slow path and appears independently
  removable.
- **Scenario:** When a node move changes the order of children under a
  parent, following siblings with numbering disabled should not
  rebuild their displayed text just to refresh nonexistent numbering.
  Following siblings whose numbering is enabled must still refresh the
  displayed number prefixes for themselves and any numbered descendant
  path that depends on them.
- **Constraints:**
  - Skip work only when numbering is disabled for that sibling.
  - Preserve current stop-at-unnumbered-subtree behavior during
    recursive numbering refresh.
  - Do not introduce batching, delayed refresh state, or broader
    `NodeView` refresh refactors in this task.
- **Briefing:** `NodeView.onNodeDeleted(...)` and
  `NodeView.onNodeInserted(...)` call `numberChanged(...)` on the
  parent view after child-order changes. `numberChanged(int)` currently
  walks all following child `NodeView`s, calls
  `MainView.updateText(view.getNode())`, and then recurses with
  `view.numberChanged(0)`. `MainView.updateText(NodeModel)` reruns the
  transformed-text pipeline. `FormatContentTransformer` prepends node
  numbers only when `textController.getNodeNumbering(node)` is true.
  The recursive call already stops at unnumbered subtrees because
  `numberChanged(0)` does nothing unless the current node itself is
  numbered. Existing test scaffolding under
  `org.freeplane.view.swing.map` shows how to construct lightweight
  `MapView` and `NodeView` test doubles with mocked controllers.
- **Research:**
  - `NodeView.numberChanged(int)` currently updates the text of every
    following sibling with a `MainView` before asking that sibling to
    recurse into `numberChanged(0)`.
  - On this path the following sibling nodes are not themselves being
    edited; the parent is refreshing them because sibling order can
    change displayed numbering.
  - For an unnumbered sibling, the recursive call already stops
    immediately because `numberChanged(0)` requires the current node
    to be numbered.
  - `MainView.updateText(NodeModel)` reruns transformed text
    generation, so calling it here pays the full text-measurement path
    even when the numbering transform cannot change the displayed
    label.
  - `FormatContentTransformer` returns the original value unchanged
    when both node numbering and explicit formatting are absent, and
    adds numbering prefixes only when
    `textController.getNodeNumbering(node)` is true.
  - `FormulaUpdateChangeListener` separately reevaluates formula
    dependencies on parent insert, delete, and move events, so this
    numbering refresh path is not the only move-time refresh
    mechanism.
  - Focused regression coverage can exercise
    `NodeView.numberChanged(int)` directly with lightweight test
    doubles instead of full `moveNodes(...)` integration.
- **Analysis:**
  - Gate sibling `updateText(...)` on the same
    `textController.getNodeNumbering(view.getNode())` predicate that
    already decides whether `view.numberChanged(0)` can do any work,
    because this task removes only work that current numbering logic
    already considers irrelevant.
  - Keep the parent-level traversal and numbered-subtree refresh path
    unchanged so numbered siblings still recompute their visible
    prefixes on the existing path.
  - Verify behavior through `MainView.updateText(NodeModel)` call
    counts in focused `NodeView` tests instead of timing assertions.
- **Design:**
  - In `NodeView.numberChanged(int)`, compute whether each following
    sibling is numbered before refreshing it.
  - If the sibling is unnumbered, skip
    `childMainView.updateText(view.getNode())` and do not recurse into
    `view.numberChanged(0)`, matching the current no-op behavior of
    that recursive call.
  - If the sibling is numbered, keep both the direct
    `updateText(...)` call and the recursive `numberChanged(0)` call.
  - Add `NodeViewNumberingRefreshTest` under
    `org.freeplane.view.swing.map` with lightweight `MapView`,
    `NodeView`, and `MainView` test doubles that count
    `updateText(NodeModel)` calls.
- **Test specification:**
  - **Automated tests:**
    - `NodeViewNumberingRefreshTest`
      - `skipUnnumberedSiblingTextRefreshDuringNumberChanged`:
        parent-level numbering refresh skips `updateText(NodeModel)`
        for an unnumbered following sibling while still refreshing a
        numbered sibling in the same range.
      - `refreshNumberedDescendantsOfNumberedSibling`:
        a numbered following sibling still refreshes its own
        `MainView` and a numbered descendant when the parent refresh
        reaches that subtree.
