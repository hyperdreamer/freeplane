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
retired hash palette or a visibility-only label change.

## Map Color Boundary

`MapReference.color()` is already immutable, persisted through workspace XML,
and restricted to the fixed accessible workspace palette. The canvas must use
that assignment without reading a Freeplane model or a workspace store.

`GraphTheme` is the correct immutable canvas value. Its only map-aware resolver
has this concrete signature:

```java
public static GraphTheme resolve(CanvasTheme requested, List<MapReference> registeredMaps)
```

It defensively copies a deterministic `Map<MapReferenceId, Color>` assignment
and converts each canonical `#RRGGBB` workspace color once. The retired
`List<Color>` resolver, `Math.floorMod(hashCode(), palette.size())`,
`mapPalette()`, and every other ID-derived color path are removed. The
no-argument resolver creates an empty map assignment and is suitable only for
an empty canvas.

A theme configured for a canvas with enclosures must contain every visible map
assignment. A missing assignment throws a clear `IllegalStateException` when a
non-suppressed hull requests its treatment; it must never silently invent a
color or collapse two map identities. The map assignment is immutable after
resolution, and reconstructing it from the same registered references produces
the same fill and stroke treatments.

`GraphCanvas` owns the installed theme and is the actual production binding
path. Task 1 changes its existing package-private setter to this explicit
cross-package interface while retaining its EDT update behavior:

```java
public void setTheme(GraphTheme theme)
```

The workspace view resolves and installs the value before the first paint and
again whenever registered maps or the persisted canvas theme changes:

```java
GraphTheme theme = GraphTheme.resolve(settings.canvasTheme(), registeredMaps);
canvas.setTheme(theme);
canvas.setCanvasState(state);
```

The setter stores only the immutable `GraphTheme`. `GraphCanvas` never looks up
a `MapModel`, workspace store, or map record itself; the registered
`MapReference` values are supplied at the ownership boundary to `GraphTheme`.
Task 1 therefore includes `GraphCanvas.java` solely for this public binding
seam and its binding-path test. It does not alter persisted map records,
workspace XML, projection values, color editing, or other canvas behavior.

## Emphatic Label Typography

`GraphTheme` owns a dedicated `emphaticLabelFont()` that is bold and larger than
the normal full-detail label font. `GraphPainter` determines the enclosure font
with the emphatic tier before forced-state or rendering-level font selection and
before ordinary LOD suppression. Every `BoundaryTier.EMPHATIC` enclosure
therefore uses the dedicated, zoom-compensated font and remains visible at
`FULL`, `DENSE`, and `OVER_TARGET`.

Subtle and node labels retain the existing normal and reduced LOD fonts.
Selected, hovered, and search-matched normal labels retain their existing
forced visibility and full-detail font. Suppressed boundaries remain absent
from painting, hit testing, traversal, and accessibility, even if their
endpoint is selected, hovered, or search-matched.

## Tests And Scope

The Task 1 test change is confined to `GraphCanvasPaintShould` and uses local
fixtures. It must first be updated and run red against the untouched
`c7d4e898e48b0f5d6aab1bc333d182b844941ac9` baseline, with the failure
attributed to the absent persisted-color binding and emphatic-font behavior,
before production files are edited.

The test must prove all of the following:

- two distinct registered `MapReferenceId` values that collide under the
  retired six-color hash scheme render different treatments when their
  persisted assignments differ;
- painting through `GraphCanvas.setTheme(GraphTheme.resolve(...))`, rather than
  direct `GraphPainter` injection, uses the registered assignments;
  `GraphCanvas#setTheme` is publicly callable, and reconstructing the same
  registered maps produces the same fill and stroke treatments;
- a visible map with no registered assignment fails with the specified
  `IllegalStateException`, and no map color comes from a local palette or an ID
  hash;
- `emphaticLabelFont()` is bold and larger than the normal font; for an isolated
  emphatic label at zoom 1, its glyph bounds/metrics agree with the dedicated
  font and differ from the normal font at `FULL`, `DENSE`, and `OVER_TARGET`;
- unforced emphatic, subtle, node, and suppressed fixtures are painted at all
  three rendering levels; subtle and node labels retain their level-specific
  behavior, while the suppressed hull and label regions paint no pixels even
  when their endpoint is forced; and
- selected, hovered, and search-matched ordinary labels retain their current
  visibility and normal-font behavior at `OVER_TARGET`.

Task 1 may modify only these four paths:

- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphTheme.java`
- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java`
- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphCanvas.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java`

It runs the focused Task 25 canvas tests, the geometry/prominence compatibility
gate, `git diff --check`, and commits only those paths. Tasks 2 and 3 preserve
their approved ten-file and six-file allowlists respectively and remain
sequential.

## Verification

The successor requires fresh independent review after Task 1, after Task 2, and
after Task 3. Final review covers the branch from merge base
`02f02355d9a33851a8a4417c4610d1897f716a50` through the final head, reconciles
the carried F-1 and F-2 findings, and is followed by the full graph-plugin test
suite under Java 21.0.8-zulu.
