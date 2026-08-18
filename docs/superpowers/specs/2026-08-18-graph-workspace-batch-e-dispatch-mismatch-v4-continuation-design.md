# Graph Workspace Batch E V4 Continuation Design

## Recovery Boundary

The v3 run is terminal in `DISPATCH_MISMATCH_BLOCKED`: its fixer spawn omitted `.superpowers/sdd` from the persisted `runRoot` prompt context, so no fixer report or source result is admissible. The v3 run root and all artifacts remain byte-preserved.

The valid carried source range remains `8d54ecda2157c06baa9b765cc92eb2a82e834506..54cab57876bb73bde13945bbbb8493ed7d34ab66`, plus the committed safe-search test correction `8ef6d2e88043ae406a49e07aa2b0608c40c62f76`. V4 audits these commits freshly and never recreates or rewrites them.

## Fresh Certification And Fix Loop

Task 1 first performs a no-source-change audit and independent review. It reruns focused/compatibility tests and disposable falsifiability probes for layout-anchor hit testing and projection-only safe search. Any new Important finding is routed through one bounded fixer and exact re-review; the fixer allowlist is determined by the reviewed finding and must be staged exactly.

The v3 reviewer exposed a valid additional edge-hit boundary: finite near-limit layout coordinates are supported, but `Line2D.ptSegDistSq` can return `NaN` on an extreme-span segment. V4 does not silently adopt that diagnostic report; the fresh reviewer must re-establish the defect, and any fix must preserve finite-coordinate support, reject non-finite distances, and add a falsifiable off-segment regression.

## Task 27 Boundary

After Task 1 is independently approved, Task 2 implements deterministic geometric keyboard traversal and lightweight virtual Swing accessibility over immutable graph state, geometry, and viewport values. It does not access source models, stores, workspace commands, or GraphStream types.

## Evidence Rules

- Preserve all terminal predecessor run roots, including v2 and v3.
- Do not cite blocked-run reports or transcripts as certification evidence.
- Persist each rendered prompt and pass those bytes verbatim to the typed child dispatch. Compare the completed child's initial user message before admitting any report.
- Use bounded logs, disposable probes, exact staged allowlists, and fresh task/final reviews.
- The fresh run root is intentionally short (`.superpowers/sdd/v4`) to make prompt context verification auditable.
