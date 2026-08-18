# Graph Workspace Batch E Task-Blocked Recovery Design

## Purpose

Resume Batch E after the dispatch-recovery run reached `TASK_BLOCKED` at
revision 13. Its round-one Task 25 fixer stopped without a report, source diff,
or commit, and the controller has no legal replacement-child transition from
`FIX_RUNNING`. The terminal run and all of its artifacts remain preserved.

This successor starts from the clean committed baseline
`c7d4e898e48b0f5d6aab1bc333d182b844941ac9`. It repairs the two open Task 25
findings, independently reviews that repair, then executes the already-approved
Task 26 and Task 27 contracts unchanged.

## Root Cause And Decision

The blocker is orchestration, not an unverified failure in the source tree. The
last fixer child exited after read-only inspection, before it wrote its required
report or changed the worktree. Reopening the terminal state or spawning another
fixer inside it would violate the deterministic state machine.

The Task 25 review independently established two real source defects:

1. `GraphTheme` replaces the persisted map color with an ID-hash lookup into a
   local palette, so distinct registered maps can collide before the workspace
   palette is exhausted.
2. `GraphPainter` keeps emphatic labels visible at `OVER_TARGET`, but selects the
   ordinary 7-point plain LOD font instead of the required bold, larger font.

The successor uses a fresh Task 1 for both corrections. It does not repeat the
discarded hash palette or a visibility-only label change.

## Map Color Boundary

`MapReference.color()` is already immutable, persisted through workspace XML,
and restricted to the fixed accessible workspace palette. The canvas must use
that assignment without reading a Freeplane model or a workspace store.

`GraphTheme` is the correct boundary because it is already an immutable canvas
value and is installed before painting. Its map-aware factory accepts the
registered immutable `MapReference` values, copies an ordered
`Map<MapReferenceId, Color>` assignment, and converts each canonical `#RRGGBB`
workspace color once. `GraphPainter` asks the theme for a treatment using the
enclosure's map ID and boundary tier.

The current `List<Color>` factory and `MapReferenceId` hash-modulo lookup are
removed. There is no alternate ID-derived color path. A theme configured for a
canvas with enclosures must contain each visible map assignment; a missing
assignment fails visibly instead of silently inventing a colliding identity.
The no-argument theme factory remains suitable for an empty canvas and ordinary
non-map UI colors, while an owning canvas-package presenter supplies registered
map values before painting workspace enclosures.

The correction stays inside the original Task 25 canvas boundary. It does not
alter persisted map records, workspace XML, projection values, color editing, or
the public `GraphCanvas` API.

## Emphatic Label Typography

`GraphTheme` owns a dedicated `emphaticLabelFont`: bold and larger than the
normal full-detail label font. `GraphPainter` selects that font for every
`BoundaryTier.EMPHATIC` enclosure before considering hover, selection, search,
or rendering level. Viewport zoom compensation remains the same for all label
fonts; automatic rendering level no longer changes the emphatic base font.

Subtle and node labels retain the existing normal and reduced LOD fonts.
Suppressed boundaries remain absent from painting, hit testing, traversal, and
accessibility.

## Tests And Scope

The Task 1 test change is confined to `GraphCanvasPaintShould` and uses local
fixtures. It must prove all of the following before production edits:

- two `MapReferenceId` values that collide under the retired six-color hash
  scheme render different treatments when their persisted assignments differ;
- reconstructing the same registered maps produces the same fill and stroke
  treatments, modeling workspace restoration;
- no map color comes from a local palette or an ID hash;
- the emphatic font is bold and larger than the normal font; and
- the same emphatic label extent is painted at `FULL`, `DENSE`, and
  `OVER_TARGET`, while ordinary labels retain their existing LOD behavior.

Task 1 may modify only `GraphTheme.java`, `GraphPainter.java`, and
`GraphCanvasPaintShould.java`. It runs the focused Task 25 canvas tests, the
geometry/prominence compatibility gate, `git diff --check`, and commits only
those paths. Tasks 2 and 3 preserve their approved ten-file and six-file
allowlists respectively.

## Verification

The successor requires fresh independent review after Task 1, after Task 2, and
after Task 3. Final review covers the branch from merge base
`02f02355d9a33851a8a4417c4610d1897f716a50` through the final head, reconciles
the carried F-1 and F-2 findings, and is followed by the full graph-plugin test
suite under Java 21.0.8-zulu.
