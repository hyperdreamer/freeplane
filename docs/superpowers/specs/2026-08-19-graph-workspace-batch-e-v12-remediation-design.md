# Graph Workspace Batch E V12 Remediation Design

## Goal

Clear the three load-bearing findings from the V11 whole-branch review while preserving V11 as terminal evidence and keeping the Graph Workspace contracts narrow: suppressed endpoints must not affect any visible surface, the accessibility root must participate in the Swing hierarchy, and stale keyboard selections must fall back to normal arrow panning.

## Context and Constraints

V11 reached `FINAL_BLOCKED` because F-5 required production paths outside its immutable six-file allowlist. F-5, F-6, and F-7 were independently reproduced at `fbdab24a6e23b6ba0ee29a30fc2f663743021245`. V12 is a fresh SDD run on that branch and may expand scope only to the paths required by those findings and their regression tests.

The implementation remains Java 8 source-compatible, uses the existing Gradle/JUnit 4/AssertJ/Mockito stack, and preserves finite layout coordinates. Projection code must remain independent of Swing and `GraphGeometry`; geometry is created after projection and prominence calculation.

## Chosen Architecture

Add a pure `ProjectedEndpointVisibility` projection utility that derives the ordered set of projected node endpoints and non-suppressed enclosure endpoints. This is the shared suppression rule. `ProminenceCalculator` uses it when indexing target hulls and node targets. Canvas consumers use the same set and require the endpoint's current node or hull geometry before painting, hit testing, traversal, or accessibility exposes it. This keeps projection layering intact while ensuring all surfaces agree on suppression and geometry availability.

`GraphPainter` will skip an edge unless both endpoints are in the shared visible set and have usable current geometry. `GraphHitIndex` and `GraphTraversalOrder` will use the same set plus their existing geometry checks. Existing suppressed hull and geometry-less endpoint behavior remains excluded rather than being reconstructed through fallback layout positions.

`AccessibleGraphCanvas` will resolve its root parent dynamically from `GraphCanvas.getParent()` when that container is accessible, and compute the root index by enumerating the parent's accessible children. `GraphInteractionController` will validate a paint selection against the current traversal order before arrow traversal; an invalid selection is cleared visually and the key follows the ordinary pan path.

## Task Decomposition

### Task 1: Cross-surface endpoint visibility

Create `ProjectedEndpointVisibility` and apply it to painter, hit index, traversal, accessibility order, and prominence. Add focused projection, paint, hit-test, and prominence regressions for an edge targeting a suppressed single-map root and for endpoints without current geometry. The task must preserve existing finite-coordinate edge hit arithmetic and all Task 25/26 behavior.

### Task 2: Accessibility root hierarchy and stale keyboard fallback

Make the root accessibility context report its actual accessible Swing parent and parent index. Add a container-backed regression. Validate stale removed, suppressed, and geometry-less selections before arrow traversal, clear the stale visual selection, and pan normally when no current directional candidate exists. Add regressions for each stale-selection category without changing valid selected-arrow traversal, Shift acceleration, Tab, Enter, or Escape behavior.

## Error Handling and Compatibility

Missing or suppressed endpoints are skipped, never clamped or synthesized. Missing geometry makes a canvas endpoint unavailable. A root accessibility context with no accessible Swing parent returns `null` and index `-1`. A stale selection does not emit an open or traversal intent; it is cleared locally and the arrow pan is consumed through the existing pan path.

No dependencies, public API module exports, persistence formats, map access, or GraphIntent nested types change.

## Verification

Each task follows red-green TDD with focused JUnit runs under Zulu 21 and a task review. V12's Frontier final review covers merge base `02f02355d9a33851a8a4417c4610d1897f716a50` through final `HEAD`, carries findings F-1 through F-7 in its ledger, runs `:freeplane_plugin_graph:test -PTestLoggingFull`, checks `git diff --check`, and requires a clean worktree.
