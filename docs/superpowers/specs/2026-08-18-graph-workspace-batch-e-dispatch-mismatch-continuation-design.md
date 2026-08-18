# Graph Workspace Batch E Dispatch-Mismatch Continuation Design

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to execute the continuation plan.

**Status:** Approved continuation design under the user's standing instruction to continue autonomously.

**Goal:** Finish Graph Workspace Batch E after the predecessor run became terminal because a re-review dispatch did not receive its stored prompt bytes, while preserving the valid Task 26 commits and obtaining fresh evidence for the remaining test-boundary defect before implementing Task 27.

## Context And Evidence Boundary

The predecessor run is permanently sealed at
`.superpowers/sdd/2026-08-18-graph-workspace-batch-e-task-blocked-recovery`, revision 24,
`DISPATCH_MISMATCH_BLOCKED`. Its state, progress ledger, prompts, reports, and child
transcripts remain unchanged and are diagnostic history only. No verdict from its
mismatched re-review child is admitted as evidence in this continuation.

The source branch contains these valid, immutable carry-forward commits:

- `56eee93d9c5432182519a23a886f181658defa8c` - Task 25 theme and typography remediation.
- `8d54ecda2157c06baa9b765cc92eb2a82e834506` - Task 26 interaction, hit-testing, and search implementation.
- `54cab57876bb73bde13945bbbb8493ed7d34ab66` - Task 26 layout-anchor correction and interaction-test expansion.

The continuation must independently inspect and review the exact Task 26 fix range
`8d54ecda2157c06baa9b765cc92eb2a82e834506..54cab57876bb73bde13945bbbb8493ed7d34ab66`.
The only known source-level audit concern is in the test fixture: the current raw
sentinel is held by a helper object but is not represented by any projected source
identity, so an unsafe search implementation could not be exposed by that assertion.
This statement is an audit hypothesis to verify from source and tests, not a carry-forward
review verdict.

## Design

### 1. Read-only carry-forward audit

The first continuation task is deliberately source-preserving. It reads the exact
committed Task 26 range, runs the focused interaction/search suite and the Task 1
compatibility suite, checks the original ten-path allowlist and commit metadata, and
performs a read-only archive/mutation probe for the edge-anchor and safe-search
assertions. It must not edit source files, the index, branch, or prior run roots.

The audit report records concrete line evidence and any residual finding. A fresh
independent task reviewer then reviews the same committed range. This creates new,
valid review evidence without re-running or admitting the blocked child.

### 2. Minimal safe-search fixture correction

If the independent review confirms the safe-search coverage defect, one bounded fix
round changes only `GraphSearchModelShould.java` unless review evidence proves a
production defect. The fixture will use a persisted `NodeReference`/`PersistedNodeId`
whose source-side identifier contains a unique raw/excluded sentinel, while the
corresponding `ProjectedNode` carries a different safe full label and map name. The
projection includes that endpoint, so a search implementation that incorrectly
indexes source identity can return the sentinel; the correct projection-only index
must return no result. The same test asserts the projected full safe label still
matches and the result set remains immutable and ordered.

No source model, raw label, transformer, or production test hook is added. The
correction stays within the existing Task 26 test allowlist and is verified with a
focused red/green probe plus a read-only base-mechanism mutation check.

### 3. Task 27 implementation

After Task 26 is freshly approved, the continuation dispatches the original Task 27
implementation as a separate Advanced task. It adds deterministic traversal order,
nearest directional selection, and package-private virtual Swing accessibility over
current immutable canvas/projection/geometry state. Suppressed endpoints remain
absent from traversal and accessibility. Existing Task 1/Task 26 public boundaries,
EDT behavior, safe-text rules, and keyboard semantics remain intact.

## Dispatch Integrity

Every continuation dispatch must use `sdd-state render-prompt`, persist the resulting
bytes in its intent before spawning, pass the exact bytes verbatim to
`spawn_subsession`, record the returned session immediately, and compare the child's
completed first user message byte-for-byte with the stored prompt before admitting
its report. A mismatch is terminal for that run; it is never reissued or silently
adopted. Typed model tiers remain the only model-selection channel.

## Verification Gates

- The continuation plan parses with `sdd-state validate-plan` and the real-parser
  body-truncation probe reports a surviving commit step for every task.
- The audit task leaves the source tree and index unchanged and proves the exact
  carry-forward range and allowlist.
- The corrected safe-search test fails against a deliberately unsafe source-identity
  indexing mutant and passes with the production projection-only implementation.
- Task 27 focused tests, Task 1/Task 26 compatibility tests, `git diff --check`, and
  exact staged-path checks pass under Zulu 21.
- A fresh Frontier final review covers the full branch from merge base, and the run
  reaches `COMPLETE` only with clean Git state, reconciled evidence, and no open
  load-bearing findings.

## Scope Exclusions

Do not rewrite valid Task 25/Task 26 production commits, alter GraphTheme or
GraphPainter as part of the search-test correction, modify the blocked run roots,
remove unrelated worktrees, add dependencies, or broaden Task 27 into native command
execution or workspace persistence.
