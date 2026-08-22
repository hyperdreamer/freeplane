# Graph Workspace Task 39 Final Review Dispatch-Mismatch Recovery Design

## Context

The Task 39 recovery run `.superpowers/sdd/batch-j-task-39-fixer-recovery` reached terminal `DISPATCH_MISMATCH_BLOCKED` during its mandatory Frontier final review. The persisted pointer named the actual run root `...batch-j-task-39-fixer-recovery/...`, but the spawned prompt manually named `...batch-j-task-39-fixer-dispatch-mismatch-recovery/...`; the child received 257 bytes instead of 239. Its final-review report is inadmissible despite an APPROVED verdict.

The source and task acceptance work are preserved through current HEAD `f2b16e5bf508108fa6a54cb8f0d8193d174e4bcb`. The Task 39 source candidate `e89f3b8ee87787cfa9c587d9ac2693a8edd82bc1` passed fresh audit, independent task review, and serial focused/full tests. No source rollback or rewrite is authorized.

## Recovery

Create a distinct successor run whose sole task is a fresh read-only audit of the complete range `834d381f724c8606034a6bc5c878bb91d105cb63..f2b16e5bf508108fa6a54cb8f0d8193d174e4bcb`. The audit independently checks plan alignment, exact source scope, all named model scenarios, the fixed Scenario 19 enclosure/pin contract, strict performance evidence, confidentiality/identity boundaries, test evidence, and direct regressions. A fresh Frontier task reviewer evaluates that audit. After task approval, the controller dispatches a new mandatory Frontier final review over the same exact range.

Do not edit or cite the terminal final-review report, prompt, transcript, state, or audit projection as approval evidence. Preserve all predecessor run roots and current source commits. Any later source fix requires a newly admitted finding and remains limited to the acceptance test path.

## Dispatch Transport

Persist each complete renderer role envelope. Persist the short pointer without a trailing newline. Build the `spawn_subsession` prompt by reading the exact pointer file bytes; do not manually compose a path string. Compare the candidate and pointer before spawn, record the returned session ID, then compare the raw first child user message bytes with the pointer before admitting any report. Any mismatch is terminal.

## Completion

Completion requires fresh audit, fresh task review, fresh final review, no unresolved load-bearing findings, clean Git state, and exact full-range evidence.
