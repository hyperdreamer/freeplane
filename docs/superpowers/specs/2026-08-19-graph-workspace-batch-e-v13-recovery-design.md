# Graph Workspace Batch E V13 Recovery Design

## Goal

Recover the Batch E SDD work after V12's correlated Frontier reviewer stopped without a verdict, certify the committed endpoint-visibility remediation with fresh evidence, and complete the remaining accessibility-root and stale-arrow behavior fixes.

## Recovery Boundary

V12 is terminal at `.superpowers/sdd/v12`, revision 11, phase `DISPATCH_MISMATCH_BLOCKED`. Its reviewer transcript and missing-report condition are preserved as diagnostic history and are not evidence of approval. The valid Task 1 implementation commit is `ec8ed4dd6e341ad95f1d4ac70dc9ef34540ddf8c`, whose parent is the documentation commit `302ad25d130b11f04f8b8a5223bbebe06f81f0f2`.

V13 starts from the current clean branch tip `ec8ed4dd6e341ad95f1d4ac70dc9ef34540ddf8c`. It does not recreate or revert the committed visibility implementation. Its first task independently audits that exact range and obtains a fresh task review. Its second task implements the remaining V11 findings F-6 and F-7, while also enforcing the shared visibility rule at the virtual accessibility endpoint boundary.

## Architecture

`ProjectedEndpointVisibility` remains the projection-only source of suppression eligibility. Canvas consumers add current geometry requirements; the projection utility is not coupled to Swing or `GraphGeometry`. The V13 audit verifies that prominence, painting, hit testing, and traversal agree on this rule and preserve finite-coordinate arithmetic.

The root accessibility context resolves its Swing parent and child index dynamically from `GraphCanvas.getParent()` and the parent's accessible children. Virtual endpoint objects remain lightweight and derive all state from the current canvas state on each call. Keyboard arrow handling first validates a paint selection against the current traversal order. A valid selection with a directional candidate traverses; a valid selection without a candidate retains current behavior; a stale selection is cleared visually and falls through to normal pan behavior without emitting a selection or open intent.

## Task Boundaries

Task 1 is an audit-only certification task for the already committed nine-path visibility range. Its implementer must not modify source, index, or branch history. If independent review identifies a concrete defect, any V13 fix wave is confined to those nine paths and must produce a new commit and fresh re-review.

Task 2 owns only `AccessibleGraphCanvas.java`, `GraphInteractionController.java`, and `AccessibleGraphCanvasShould.java`. It implements F-6 and F-7 and tests removed, suppressed, and geometry-less stale selections, plus the live parent/index hierarchy. It consumes the admitted Task 1 review evidence but does not rely on V12's missing reviewer report.

## Verification

Every child receives a renderer-produced prompt envelope through a persisted pointer, and its first user message is compared byte-for-byte before its report is admitted. Each task receives an independent spec and quality review. The final Frontier review covers the original merge base `02f02355d9a33851a8a4417c4610d1897f716a50` through the final V13 `HEAD`, reconciles V11 findings F-1 through F-7, runs `gradle :freeplane_plugin_graph:test -PTestLoggingFull` under Zulu 21, and requires a clean worktree and index.
