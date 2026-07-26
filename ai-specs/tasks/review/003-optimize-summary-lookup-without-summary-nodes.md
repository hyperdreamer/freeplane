# Task: Optimize summary lookup without summary nodes
- **Task Identifier:** 2026-07-26-summary-lookup
- **Scope:** Add a fast path in
  `SummaryLevels.findSummaryNodeIndex(int)` that returns
  `NODE_NOT_FOUND` immediately when the precomputed
  `highestSummaryLevel` is zero. Preserve existing return values for
  parents that do contain summary structure, and add focused
  regression coverage around `findSummaryNodeIndex(...)`. Exclude
  broader summary-layout refactors, caching, and profiling-harness
  work.
- **Motivation:** Large parents without summary nodes currently pay
  O(N²) summary lookup cost during layout because each child probes
  for a following summary node that cannot exist. The reported map has
  very wide parents, so removing that avoidable scan is a small change
  with clear benefit.
- **Scenario:** When Freeplane lays out a wide parent whose children
  are ordinary items and not summaries, each lookup for a following
  summary node should resolve to `NODE_NOT_FOUND` without scanning
  later siblings. Layout and other summary-index callers must still
  see the same results as before when summary structure exists.
- **Constraints:**
  - Keep the optimization inside
    `SummaryLevels.findSummaryNodeIndex(int)` so all current callers
    share the same contract.
  - Do not change `summaryLevels`, `highestSummaryLevel`, or caller
    logic beyond what is required for the fast path and tests.
  - Preserve current behavior when `highestSummaryLevel > 0`.
- **Briefing:** `VerticalNodeViewLayoutStrategy.calculateNextVGap(int)`
  calls `viewLevels.findSummaryNodeIndex(index)` for every child
  during vertical layout. The same method is also used by
  `SummaryGroupEdgeListAdder`,
  `SummaryLevels.canInsertSummaryNode(...)`, and
  `SummaryNodeCreator`. `SummaryLevels` already computes
  `highestSummaryLevel` in its constructor while walking the child
  list. Existing coverage in `SummaryLevelsShould` checks basic return
  values for summary lookup but does not exercise the zero-summary
  fast path across multiple child indices.
- **Research:**
  - `SummaryLevels.findSummaryNodeIndex(int)` currently reads
    `summaryLevels[index]`, determines the child's side, and scans
    `index + 1 .. childCount - 1` until it finds a summary node on
    the same side or a first-group node at the same level.
  - When no summary nodes exist under a parent, the constructor leaves
    `highestSummaryLevel == 0`, so every existing path through
    `findSummaryNodeIndex(int)` already returns `NODE_NOT_FOUND`.
  - `VerticalNodeViewLayoutStrategy.calculateNextVGap(int)` uses only
    the returned index or `NODE_NOT_FOUND`, so an early
    `NODE_NOT_FOUND` result preserves its current layout decision for
    no-summary parents.
  - Other callers (`SummaryGroupEdgeListAdder`,
    `SummaryLevels.canInsertSummaryNode(...)`, and
    `SummaryNodeCreator`) also depend only on the contract
    `summary index or NODE_NOT_FOUND`.
  - `SummaryLevelsShould.FindSummaryNodeIndex` currently covers one
    no-summary single-node case and one positive summary case; it does
    not protect the new branch across multiple child indices or the
    `highestSummaryLevel > 0` path explicitly.
- **Analysis:**
  - Put the fast path in `SummaryLevels.findSummaryNodeIndex(int)`
    because the proof `highestSummaryLevel == 0` means no reachable
    summary node for every current caller, not only layout.
  - Keep the existing loop unchanged when `highestSummaryLevel > 0`
    so summary and first-group handling stays on the already-proven
    path.
  - Verify the optimization through return-value regression tests
    instead of timing assertions, because the changed contract surface
    is the returned summary index.
- **Design:**
  - `SummaryLevels.findSummaryNodeIndex(int)` first checks
    `highestSummaryLevel`. When it is zero, return `NODE_NOT_FOUND`
    before reading later siblings.
  - When `highestSummaryLevel > 0`, preserve the current loop and
    same-side / first-group checks exactly as they are today.
  - Extend `SummaryLevelsShould.FindSummaryNodeIndex` with a
    multi-child no-summary case that asserts `NODE_NOT_FOUND` for
    every child index under a parent with no summary nodes.
  - Keep one positive summary-structure case in the same container to
    assert that lookup still returns the real summary node index when
    summaries exist.
- **Test specification:**
  - **Automated tests:**
    - `SummaryLevelsShould.FindSummaryNodeIndex`
      - `returnNodeNotFoundForEveryIndex_IfHighestSummaryLevelIsZero`:
        a parent with multiple ordinary children and no summary nodes
        returns `NODE_NOT_FOUND` for each child index.
      - `returnSummaryNodeLevel1AfterTwoItems`: a parent with a real
        following summary node still returns that summary node index
        when `highestSummaryLevel > 0`.
