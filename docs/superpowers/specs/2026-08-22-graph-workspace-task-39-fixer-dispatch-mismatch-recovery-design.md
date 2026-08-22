# Graph Workspace Task 39 Fixer Dispatch-Mismatch Recovery Design

## Context

The successor Task 39 run `.superpowers/sdd/batch-j-task-39-rereview-recovery` reached terminal `DISPATCH_MISMATCH_BLOCKED` while its capable fixer was implementing the admitted Scenario 19 finding. The persisted pointer named the run root `.../.superpowers/sdd/batch-j-task-39-rereview-recovery/...`, but the controller dispatched a manually typed pointer naming `...rereview-dispatch-mismatch-recovery/...`. The child therefore received 258 bytes instead of the persisted 240 bytes. Its source commit `e89f3b8ee87787cfa9c587d9ac2693a8edd82bc1` is preserved but unreviewed.

## Recovery

Create a distinct successor SDD run that audits the preserved F-1 fixer commit from scratch. The successor starts from current HEAD after an explicit preflight approval of the unreviewed carry-forward source. A capable child performs a read-only audit and serial focused/full test verification of Scenario 19 and confirms the earlier Task 39 corrections remain intact. A fresh Frontier reviewer then reviews the exact source range. If the candidate is sound, the task completes and proceeds to final review. If findings remain, only a controller-authorized fix round may modify the single acceptance test path.

Do not reset, discard, amend, or rewrite the preserved source commit. Do not use the terminal run's report, prompt, transcript, or state as approval evidence. All child reports and transport checks belong to the new run.

## Dispatch Transport

Persist the complete rendered role envelope. Persist the short ASCII pointer without a trailing newline. Before spawning, read the pointer file itself into the `spawn_subsession` prompt field via the controller's exact byte path, and compare the candidate bytes. After spawn, locate the child session by its exact session ID and extract only the first user message beginning with `Model tier:` for the byte comparison. Any mismatch is terminal.

## Completion

Completion requires fresh audit, independent Frontier review, no unresolved load-bearing findings, exact source scope, clean Git state, and a Frontier final review of `834d381f724c8606034a6bc5c878bb91d105cb63..HEAD`.
