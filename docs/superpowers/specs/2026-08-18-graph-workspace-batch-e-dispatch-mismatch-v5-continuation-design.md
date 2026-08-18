# Graph Workspace Batch E V5 Continuation Design

## Recovery Boundary

The v4 run is terminal in `DISPATCH_MISMATCH_BLOCKED`: its fixer spawn changed the rendered role-contract text, so no fixer result is admissible. The v4 run root and all artifacts remain byte-preserved.

The valid carried source range remains `8d54ecda2157c06baa9b765cc92eb2a82e834506..54cab57876bb73bde13945bbbb8493ed7d34ab66`, plus the committed safe-search test correction `8ef6d2e88043ae406a49e07aa2b0608c40c62f76`. V5 audits these commits and applies the finite-coordinate edge-hit correction as a normal bounded Task 1 source change, avoiding a separate fixer dispatch for the already verified review defect.

## Task 1 Boundary

Task 1 establishes the carry-forward range and tests, writes an extreme-span off-segment regression first, then changes only `GraphHitIndex.java` and `GraphInteractionControllerShould.java`. The production correction must preserve finite near-limit coordinates, reject non-finite distance calculations, compare point-to-segment distances without squared overflow, and preserve ordinary hit ordering. It also reruns the anchor and safe-search falsifiability probes before commit. An independent reviewer reviews the complete range plus the new correction.

## Task 2 Boundary

After Task 1 is independently approved, Task 2 implements deterministic geometric keyboard traversal and lightweight virtual Swing accessibility over immutable graph state, geometry, and viewport values. It does not access source models, stores, workspace commands, or GraphStream types.

## Evidence Rules

- Preserve all terminal predecessor run roots, including v2, v3, and v4.
- Do not cite blocked-run reports or transcripts as certification evidence.
- Persist each rendered prompt and pass those bytes verbatim to the typed child dispatch. Compare the completed child's initial user message before admitting any report.
- Use bounded logs, disposable probes, exact staged allowlists, and fresh task/final reviews.
- Keep the V5 run root short (`.superpowers/sdd/v5`) for auditable prompt context.
