# Graph Workspace Batch E V15 Inherited Task 2 Recovery Design

## Goal

Recover the unreported V14 Task 2 accessibility implementation without discarding,
recreating, or treating the stopped child as approval evidence. Finish the exact
inherited three-file change in the existing recovery worktree, independently prove
its behavior, commit it, and complete fresh task and whole-branch review.

## Recovery Boundary

V14 is terminal at
`.superpowers/sdd/2026-08-19-graph-workspace-batch-e-v14-continuation`, revision
16, phase `DISPATCH_MISMATCH_BLOCKED`. Its Task 1 endpoint-visibility audit and
independent review were admitted before the terminal Task 2 dispatch. The Task 2
child received the exact stored `advanced` pointer but terminated on an upstream
provider error before writing a report or creating a commit. Its transcript and
claims are diagnostic history only.

The committed source baseline is
`efaaa8ded5e988eb8c4e9dd6c11cb186da02ac94`. The worktree intentionally contains
exactly these three unstaged paths:

- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvas.java`
- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvasShould.java`

The binary diff relative to that baseline has SHA-256
`845b208626004d4abcee08ee54869ebb6353f308a64d0bb6b38aa2daee6bdf37`.
The index is empty and `git diff --check` passes. These files are quarantined
implementation input, not a completed result. V15 preserves them in place and
must not reset, clean, checkout, stash, revert, or reproduce them elsewhere.

## Execution

A documentation-only V15 checkpoint is committed while the inherited three-file
diff remains unstaged. The new SDD run explicitly approves that intentional dirty
preflight and dispatches one fresh Advanced implementer.

The implementer directly inspects the current diff and source contracts. It proves
the new tests are falsifiable without changing the active worktree by exporting
the committed V15 checkout into a temporary directory and copying only the
inherited test file into that archive. The focused suite must then expose the
pre-fix parent/index and stale-arrow failures. The implementer removes the probe,
runs the focused, canvas, and complete graph-plugin suites against the inherited
implementation, corrects only demonstrated defects within the three-file
allowlist, and commits exactly those paths.

A fresh task reviewer inspects the V15 documentation checkpoint through the Task 2
commit and reruns the required tests. A fresh Frontier final reviewer covers merge
base `02f02355d9a33851a8a4417c4610d1897f716a50` through final `HEAD`, reconciles
V11 findings F-1 through F-7, and treats V14's stopped Task 2 output only as
recovery provenance.

## Behavior Contracts

The accessibility root resolves its live Swing parent on every call. Its parent
index is found by enumerating that parent's current accessible children. An
unattached canvas returns a null parent and index `-1`. Virtual endpoint objects
remain children of the canvas and resolve availability from current projection
visibility plus current node or hull geometry on every query.

An unmodified arrow with a currently traversable selection preserves directional
selection behavior. A valid selection with no directional candidate remains
consumed. A selection absent from current traversal order, including removed,
suppressed, and geometry-less endpoints, is cleared visually and falls through to
ordinary arrow panning without emitting selection or open intents. Shift arrows
remain unconditional accelerated panning. Tab, Shift+Tab, Enter, and Escape retain
their accepted behavior.

No dependency, public API, persistence format, resource, map access, or
`GraphIntent` nested type changes. Projection visibility remains independent of
Swing and geometry.

## Verification

V15 validates with the real `sdd-state` parser and an extraction check that proves
the complete task body survives parsing. Every child receives a renderer-produced
persisted envelope through a byte-stable pointer, and its first user message is
compared byte-for-byte before a report is admitted.

The implementation gate consists of the isolated pre-fix focused probe, the active
focused accessibility suite, all canvas `*Should` tests, and the complete
`:freeplane_plugin_graph:test` suite under Zulu 21. The source commit uses exactly
the three-file allowlist and subject
`2026-08-10-graph-workspace: Repair graph accessibility fallback`. Completion
requires fresh task approval, fresh Frontier final approval, a reconciled finding
ledger, and a clean worktree and index.
