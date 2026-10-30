# 012 — Graph workspace lineage divergence: read-only analysis

Task Identifier: `012-graph-workspace-lineage-divergence`
Status: **analysis only — no branch moved, no merge performed, nothing pushed to `origin/plugin/graph-workspace`**
Date: analysis run against local `plugin/graph-workspace` `4f9248440a` and `origin/plugin/graph-workspace` `2b78cde01c`

## 1. The divergence

| Quantity | Value |
|---|---|
| Local `plugin/graph-workspace` | `4f9248440a` |
| `origin/plugin/graph-workspace` | `2b78cde01c` |
| Commits only on local | **293** |
| Commits only on remote | **232** |
| Merge bases | **two** (`73f5b6e0a0`, `ac5d43b7cd`) |

Two merge bases means the histories have been merged in both directions before, so this is a criss-cross
history rather than a simple fork. Counting against a single merge base gives nonsense (837/776); the
`--left-right` count above is the authoritative one.

## 2. What the remote line contains

The 232 commits span 2026-09-28 to 2026-10-25 and include graph lanes that also exist locally, in parallel:

- `2026-10-12-graph-edge-crossing-avoidance`, `2026-10-13-graph-cluster-initial-proximity`,
  `2026-10-14-graph-fullscreen-mode`, `2026-10-13-graph-drag-pan-runaway`,
  `2026-10-13-map-entry-focus-during-graph-settle`, `2026-10-10-graph-arrowhead-prominence`,
  `2026-10-11-graph-arrowhead-zoom`, `2026-10-11-graph-live-node-edit-refresh`,
  `graph-initial-spacing`, `2026-10-23-graph-edge-length-and-label-connector`,
  `2026-10-16-edge-node-clearance`.

111 of the 232 touch `freeplane_plugin_graph/src`. The remainder are mostly `freeplane_plugin_graph/doc`
(10 076 changed paths) and `docs/superpowers`.

So the remote is **not** a small sibling lane that could be merged in mechanically. It is a second, longer-lived
evolution of the same subsystem.

## 3. Merge is not mechanical

`git merge-tree --write-tree` (read-only, no worktree change) reports a merge that conflicts in 16 files:

```
main/java/.../geometry/EdgeRouter.java                   (add/add)
main/java/.../layout/EdgeConflictCorrection.java         (add/add)
main/java/.../layout/graphstream/GraphStreamLayoutEngine.java
main/java/.../layout/LayoutWorker.java
test/java/.../geometry/EdgeRouterShould.java             (add/add)
test/java/.../geometry/EdgeRouterFixture.java            (add/add)
test/java/.../layout/EdgeConflictCorrectionShould.java   (add/add)
test/java/.../layout/graphstream/InitialSpacingShould.java (add/add)
test/java/.../canvas/AccessibleGraphCanvasShould.java
test/java/.../canvas/GraphCanvasPaintShould.java
test/java/.../canvas/GraphInteractionControllerShould.java
test/java/.../control/GraphUpdateCoordinatorShould.java
test/java/.../layout/LayoutWorkerShould.java
test/java/.../performance/GraphWorkspacePerformanceDiagnostic.java
test/java/.../performance/PerformanceTripwiresShould.java
test/java/.../window/GraphWorkspaceWindowModelShould.java
```

`EdgeRouter.java` and `EdgeConflictCorrection.java` are **add/add**: both lineages independently created the
edge-routing and conflict-correction subsystem. Resolving that means choosing semantics between two
implementations of the same feature, and validating the result against both lineages' acceptance evidence.
That is a subsystem-owner decision with its own plan — not something to attempt opportunistically, and not
something this task should do.

## 4. The hygiene work is still needed on the remote lineage

The remote line has **none** of the fixes delivered in tasks 010/011 (checked directly at `2b78cde01c`):

| Check on `origin/plugin/graph-workspace` | Result |
|---|---|
| `excludeTestsMatching` in the module build | 0 — the acceptance drivers are still in the ordinary suite |
| `InitialSpacingShould` suite membership | still includes `InitialSpacingAcceptanceShould` (duplicate wrapper report) |
| hard-coded 5000 ms settle budget in the window test | still present (2 occurrences) |
| wall-clock frame gating / liveness split | absent |
| `graphAcceptanceDrivers` task | absent |

So the seven original failures and the load-sensitive settle budgets are still live on that lineage.

## 5. Recommendation

**Do not merge or rebase the lineages as a side effect of test hygiene.** Test hygiene does not require
answering which lineage is canonical, and it should not be used as cover for a subsystem reconciliation.

**Port the hygiene delta instead.** The changes are small, self-describing and line-agnostic:

- driver exclusion plus `graphAcceptanceDrivers` task
- `InitialSpacingShould` suite membership
- wall-clock bound owned by the performance diagnostic, liveness/assertion split
- `X11_WM_BORDER_TOLERANCE` for screen containment
- settle budget as a liveness bound
- `awaitWithin`, and the pan test un-gated
- EDT-atomic scroll position in `GraphCanvasPaintShould`
- deterministic close-boundary coverage in `GraphUpdateCoordinatorShould` and the rewritten lifecycle test
- `scripts/run-suite-on-xvfb.sh` plus `doc/running-the-test-suite.md` (no upstream conflict at all)

Nine of those files also changed on the remote, so porting is re-application against different baselines
rather than a cherry-pick — but each change is small and independently justified, which is exactly why it is
tractable where a 232-commit merge is not.

**Which lineage is canonical remains an open decision for the repository owner.** It requires comparing feature
sets (for example whether the remote's arrowhead, drag-pan and live-refresh work should supersede local
equivalents), which is a product question, not a test one.

## 6. The hygiene delta has been ported (branch `pm/sync-hygiene-origin`)

Executed against `origin/plugin/graph-workspace` `2b78cde01c` in `.worktrees/sync-hygiene`.

**Result: every file applied cleanly.** All fifteen paths from the local hygiene delta applied with
`git apply` — including the files that conflict in a full three-way merge. The merge conflicts come from
*their* rewrites of those files, while this delta's hunks still match their context, so patch application
succeeds where a merge does not. That is why porting is tractable here and a 232-commit merge is not.

**Verification on the ported lineage:**

| Check | Result |
|---|---|
| `compileTestJava` | success |
| Full suite on `Xvfb` + `openbox` | 1439 tests, **1 failure**, 3 skipped |
| Production source changed by the port | **none** |

The single failure was `LayoutWorkerShould.terminateTheWorkerThreadAcrossRepeatedCloseCycles`
(`freeplane-graph-layout-worker-87` still alive), a file this port does not touch. It passed **4/4 in
isolation**, at load average 18–26, so it is a pre-existing load-sensitive flake on this lineage, not a
porting defect.

The original seven failures are gone on this lineage as well: no acceptance-driver failures, no duplicate
`InitialSpacingShould` wrapper report.

**Residual:** this lineage keeps its own load-sensitive tests (`LayoutWorkerShould`, and the `MapLeaseManager`
behaviour recorded in task 010 section 12.8). Porting the delta does not make every lineage-specific test
load-tolerant.
