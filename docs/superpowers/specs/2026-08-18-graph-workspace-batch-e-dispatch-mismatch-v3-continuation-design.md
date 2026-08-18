# Graph Workspace Batch E V3 Dispatch-Mismatch Continuation Design

## Recovery Boundary

The v2 continuation run is terminal in `DISPATCH_MISMATCH_BLOCKED`. Its Task 1 re-reviewer was spawned with a prompt whose `runRoot` omitted `.superpowers/sdd`, differing from the persisted rendered prompt. The re-reviewer report is diagnostic only and is not admitted by this successor.

The valid source deliverable is the committed test-only correction `8ef6d2e88043ae406a49e07aa2b0608c40c62f76`:

- Parent: `a1651c766ecb495a10f358df1d42666352735575`
- Changed path: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphSearchModelShould.java`
- Subject: `2026-08-10-graph-workspace: Make graph search boundary test falsifiable`

It must remain immutable. Recovery audits it; it never resets or recreates the commit.

## Task 1 Certification

A fresh read-only task certifies the complete Task 26 interaction/search range `8d54ecda2157c06baa9b765cc92eb2a82e834506..54cab57876bb73bde13945bbbb8493ed7d34ab66` and the follow-up fixture correction at `8ef6d2e8`.

The task proves two independent regressions are falsifiable in disposable archives:

1. Replacing `GraphHitIndex` with the predecessor version makes the layout-anchor hit test fail.
2. Appending a projected node's source identity to indexed safe text makes the corrected search test fail because its absent-query sentinel derives from that projected node.

The active branch may contain later controller documentation commits. Certification checks that `8ef6d2e8` is an ancestor and that all later changes are documentation-only; it does not require a moving branch tip to equal a source endpoint.

## Task 27 Boundary

Backlog Task 27 remains unchanged: deterministic geometric keyboard traversal and lightweight virtual Swing accessibility are implemented only after Task 1 passes fresh independent review. The implementation must use immutable canvas state, projection, geometry, and viewport values without source-model or workspace access.

## Evidence Rules

- Preserve every terminal predecessor run root, including v2, byte-for-byte.
- Do not cite blocked-run reports or transcripts as certification evidence.
- Persist and send each rendered prompt byte-for-byte; compare the completed child’s initial user message before admitting a report.
- Use bounded Gradle logs and disposable archive probes. Remove temporary archives and logs before reporting.
- Use a fresh independent task review and final Frontier review before declaring the run complete.
