# Task: Batch sibling renumbering during multi-node moves
- **Task Identifier:** 2026-07-26-batched-renumber
- **Scope:** Coalesce repeated sibling renumbering work triggered by
  `moveNodes(...)` so each affected parent view refreshes numbering
  once from the earliest changed child index after the final child
  order is known. Cover cross-parent moves and promote or demote
  operations that currently trigger many remove or insert passes.
  Exclude unrelated generic refresh-queue refactors.
- **Motivation:** Multi-node moves currently pay the sibling-tail
  renumbering cost once per moved node. Even after removing obviously
  redundant unnumbered updates, bulk promote and move operations will
  still repeat the same parent-level work many times unless the refresh
  is batched.
- **Constraints:**
  - Keep batching state owned by the affected parent-view refresh path,
    not by generic per-node delayed refresh machinery.
  - Preserve numbering results for the final tree order, including any
    recursive updates required by numbered subtrees.
