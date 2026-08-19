# Graph Workspace Batch E V14 Continuation Design

## Goal

Recover from V13's terminal `TASK_BLOCKED` state, freshly certify the committed endpoint-visibility remediation, and complete the remaining accessibility-root and stale-arrow behavior fixes without recreating the valid source commit or creating another worktree.

## Recovery Boundary

V13 is terminal at `.superpowers/sdd/v13`, revision 7, phase `TASK_BLOCKED`. Its implementer report and all other V13 artifacts remain diagnostic history and are not approval evidence. V13 was blocked because its task brief required `HEAD=ec8ed4dd6e341ad95f1d4ac70dc9ef34540ddf8c` even though the required V13 recovery documentation was already a clean committed descendant.

The valid endpoint-visibility source range is exactly `302ad25d130b11f04f8b8a5223bbebe06f81f0f2..ec8ed4dd6e341ad95f1d4ac70dc9ef34540ddf8c`. The current recovery branch tip is `a6f928802a13900bd94c75b6f93d1ce3bff3a71c`; commits after `ec8ed4dd6e` are documentation-only. V14 audits the immutable source range independently and accepts the clean documentation-only descendant as its checkout baseline. It does not reset, rewrite, or re-run V13.

## Chosen Architecture

`ProjectedEndpointVisibility` remains a pure projection utility. It supplies the suppression-eligible endpoint set, while canvas consumers additionally require current finite node or hull geometry. Prominence, painting, hit testing, traversal, and accessibility therefore agree on endpoint eligibility without coupling projection to Swing or `GraphGeometry`.

The root accessibility context resolves its Swing parent and child index dynamically from the live component hierarchy. Virtual endpoint objects derive availability from the current projection and geometry on every access. Keyboard arrow handling validates a paint selection against the current traversal order; a stale selection is cleared visually and follows the existing ordinary pan path without emitting traversal or open intents.

## Task Boundaries

Task 1 is a read-only audit of the already committed nine-path visibility range. It must not modify source, tests, the index, or branch history. The audit compares the exact `302ad25d13..ec8ed4dd6e` source range while separately proving that the current `HEAD` differs from `ec8ed4dd6e` only in documentation paths.

Task 2 owns only `AccessibleGraphCanvas.java`, `GraphInteractionController.java`, and `AccessibleGraphCanvasShould.java`. It implements the remaining F-6 and F-7 behavior and verifies that virtual accessibility endpoints obey the shared visibility rule. Its baseline is the clean current branch after Task 1, not an exact historical commit.

## Error Handling and Compatibility

Suppressed, missing, or geometry-less endpoints are skipped; they are never painted, hit, traversed, or exposed through accessibility. Valid finite `LayoutPoint` coordinates, including extreme finite values, retain their existing arithmetic and are not clamped or rejected. An unattached accessibility root reports no parent and index `-1`. A stale selection emits no traversal or open intent and falls through to normal arrow panning.

No dependencies, public API exports, persistence formats, map access, resource files, or `GraphIntent` nested types change. Existing valid selected-arrow traversal, no-selection panning, Shift acceleration, Tab cycling, Enter validation, and Escape ordering remain intact.

## Verification

Each task uses red-green tests where source changes are required, bounded Zulu 21 Gradle commands, and independent task review. Every child receives a renderer-produced persisted prompt and its first user message is compared byte-for-byte before its report is admitted. The final Frontier review covers merge base `02f02355d9a33851a8a4417c4610d1897f716a50` through the final branch tip, reconciles V11 findings F-1 through F-7, runs `:freeplane_plugin_graph:test -PTestLoggingFull`, and requires a clean worktree and index.
