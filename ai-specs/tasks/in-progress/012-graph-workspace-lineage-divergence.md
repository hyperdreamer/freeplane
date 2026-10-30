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

## 7. Semantic comparison: the local lineage is canonical

Follow-up analysis of section 5, which only compared structure. Compared the two tips content-wise:
local `plugin/graph-workspace` `d22d208309` against `origin/plugin/graph-workspace` `2b78cde01c`.

### 7.1 The remote is the same lineage, one lane behind — not a competing implementation

The commit subjects on either side are **the same lanes with different SHAs**:

```
LOCAL only      Expand selected relocation families by clearance ring   (residual)
LOCAL only      Defend per-ring deduplication and fix audit whitespace   (residual)
both            Bound clearance family selection and retries
both            Fix finite C2 clearance alternatives
both            Suppress crowded edge-conflict repair and recalibrate A7a
both            rebuild the boundary record for the published frame
...
```

That is a replayed (rebased) history. The `293 vs 232` counts are inflated by SHA divergence between
identical work, not by 293 commits of unique content.

### 7.2 The local line is a strict content superset

| Check (repo-wide, `remote → local`) | Result |
|---|---|
| Files the remote has and the local line lacks | **0** |
| Files the local line adds | 43 |
| Total differing files | 72 |

And the relationship is exact in the subsystem that matters: **`origin/plugin/graph-workspace` is
content-identical to the local line just before the residual lane**, except for `EdgeRouter.java`, where the
local side adds 77 lines — `bestControlPositionRung` with `CONTROL_FRACTIONS`/`OFFSET_FRACTIONS`, i.e. the
residual lane's control-position routing bows.

Only five production files differ at all:

| File | local-only commits | remote-only commits | churn |
|---|---|---|---|
| `layout/EdgeConflictCorrection.java` | 15 | 13 | 158 |
| `geometry/EdgeRouter.java` | 7 | 5 | 77 |
| `layout/LayoutWorker.java` | 4 | 3 | 25 |
| `layout/graphstream/GraphStreamLayoutEngine.java` | 5 | 5 | 21 |
| `layout/LayoutEngine.java` | 1 | 1 | 13 |

In every case the extra local commits are the residual lane; the rest are SHA-only duplicates. The remaining
differences are additive: residual-lane tests (`EdgeConflictCorrectionShould` +723, `EdgeRouterShould` +222,
`LayoutWorkerShould` +102, `RealWorkspaceCrossingShould` +179, `EdgeRouterFixture` +59), residual fixtures,
residual acceptance docs, the `Xvfb` script and doc from task 010, and local-only core work the remote never
had (`FrameResynchronizer` +369/−83 with its tests, `MFileManager` autosave +559 test lines, `EditNodeTextField`).

The only genuine removals are cosmetic: one line of `AGENTS.md`, one line of `ExportPdf.java`, and an
equal-size rewrite of `Resources_fr.properties`.

### 7.3 Recommendation

**Treat the local lineage as canonical.** It is justified empirically: nothing the remote has is missing
locally, and everything the local line adds is explainable by its own later lanes.

To repair the ref without losing anything:

1. **Back up the remote tip on a named ref first** —
   `git push origin 2b78cde01c:refs/heads/backup/graph-workspace-pre-sync-2b78cde0`.
   The 232 SHA-distinct commits then stay reachable on a named branch.
2. **Then force-push the local line** with `--force-with-lease`. Content-safe by 7.2, but it **rewrites a
   published branch**, so it needs explicit owner approval and should not be done as a side effect.
3. **Alternative, zero-risk:** leave `origin/plugin/graph-workspace` frozen, record it as superseded, and keep
   delivering through `candidate/*`. Nothing breaks; the stale ref stays misleading.

**Do not merge the two lineages.** A merge would conflict in 16 files over content the local line already
contains — busywork with no content gain, and a real chance of losing the residual lane in reconciliation.
