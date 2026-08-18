# Graph Workspace Batch E V10 Recovery Design

## Recovery Boundary

V9 is terminal at `DISPATCH_MISMATCH_BLOCKED`. Its child received a manually transcribed prompt differing from the persisted full prompt, so its report and findings are diagnostic only. Preserve V9 byte-for-byte and do not cite its output as evidence.

The committed source baseline remains `e740e9c741f1f2aa6db4c0567e1957bf0416a63d` in the existing `graph-batch-e-recovery` worktree. V10 Task 1 is a fresh bounded correction task: independently reproduce any finite multiplication-cancellation failure through the public `GraphHitIndex` path, write a falsifiable regression, correct the two-path arithmetic implementation, verify it, and receive fresh review.

## File-Backed Dispatch Envelope

The tracked-session tool accepts only literal prompt text and has no file-valued prompt parameter. Prior manual reproduction of the renderer's long prompt caused four terminal byte mismatches. V10 therefore separates the prompt transport from the role contract:

1. The controller runs `sdd-state render-prompt` and persists the full role contract as an immutable envelope beneath the V10 run root.
2. The dispatch intent persists a short ASCII pointer prompt whose only instruction is to read and obey that envelope. The controller compares the pointer candidate bytes with its persisted prompt before spawn.
3. The child first reads the envelope, which contains the renderer-produced role contract, task brief path, report path, and exact scope. The envelope itself is read-only.
4. After completion, the controller compares the child's first user message with the pointer prompt byte-for-byte before admitting a report.

This adapter preserves a deterministic, persisted prompt transport despite the API's lack of a file-input field. It does not modify the global SDD skill or invent a server-side parameter.

## Numerical Contract

`GraphHitIndex.edgeAt` must distinguish every representable positive finite point-to-segment distance from zero, including residuals produced by cancellation of products with large, nearly equal magnitudes. It must preserve ordinary behavior, mixed-magnitude coordinates, near-limit spans, subnormal offsets, zero-length segments, projection clamping, zero and finite tolerance comparisons, non-finite rejection, nearest-edge ordering, and key ties. Do not reject or clamp finite geometry merely because coordinates are extreme.

## Downstream Work

After fresh Task 1 review approval, implement Task 27 deterministic traversal and virtual Swing accessibility over immutable canvas state and geometry. Preserve Task 25/26 behavior. A fresh final Frontier review covers the full branch.
