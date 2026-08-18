# Graph Workspace Batch E V9 Recovery Design

## Recovery Boundary

V8 is terminal at `DISPATCH_MISMATCH_BLOCKED`: its audit child received manually transcribed prompt bytes that differed from the persisted render and stopped without a report. Preserve V8 unchanged. No V8 child output is evidence.

The valid source baseline remains `e740e9c741f1f2aa6db4c0567e1957bf0416a63d` in the existing `graph-batch-e-recovery` worktree. Its two changed files are `GraphHitIndex.java` and `GraphInteractionControllerShould.java`. V9 begins with a clean worktree and an audit-only Task 1; it does not recreate or change the committed correction unless a fresh reviewer opens a bounded fix round.

## Prompt Handoff Control

The prior terminal runs exposed manual prompt transcription as a deterministic failure source. For V9, each dispatched prompt is rendered once under the run root. Immediately before the tool call, the controller writes the exact proposed `prompt` parameter bytes to a temporary candidate file and verifies `cmp -s candidate persisted-prompt`. The byte-identical candidate is then used for the tool call. After completion, compare the child first user message to the persisted prompt before admitting any result. A mismatch remains terminal.

## Technical Audit

Task 1 fresh evidence must cover finite edge geometry across ordinary and extreme exponents, including multiplicative cancellation. It must not rely on older rejected reports. A reviewer can direct a two-file fix when a concrete finite residual is proven to disappear or a tolerance/order contract fails.

Task 2 remains deterministic keyboard traversal and virtual accessibility over immutable state and geometry after Task 1 approval. Final review remains branch-wide and fresh.
