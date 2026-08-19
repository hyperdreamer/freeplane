# Graph Workspace Batch E Continuation Design

**Task Identifier:** 2026-08-10-graph-workspace
**Batch:** E continuation after dispatch mismatch
**Status:** Approved for execution 2026-08-18

## Purpose

Continue Batch E from the valid Task 25 commit after the prior SDD run was
terminally blocked by a reviewer dispatch prompt mismatch. The blocked run and
its artifacts remain immutable evidence; no report from that run is admitted.

## Baseline

The continuation starts at `cd21a68a82c0914bb14f351b473e04884132284c`, whose
parent is `02f02355d9a33851a8a4417c4610d1897f716a50`. That commit contains the
exact ten Task 25 allowlisted files and passed the focused Task 25 suite. The
first continuation task performs a fresh read-only audit and independent review
of that exact range. It must not recreate Task 25 merely to repeat its red/green
cycle. If the fresh review finds a load-bearing defect, a bounded fix round may
modify only those ten Task 25 paths.

## Scope

After the carry-forward audit, implement the remaining original backlog Tasks
26 and 27 in order, with the original exact file allowlists, public signatures,
behavioral contracts, focused tests, independent task reviews, and one final
whole-branch review. Task 26 emits immutable intents and transient interaction
state only. Task 27 adds deterministic traversal and virtual Swing
accessibility. Canvas code never reads source map models, writes workspace data,
executes commands, exposes GraphStream types, or adds compatibility fallbacks.

## Review Integrity

Every new child receives the controller-rendered prompt bytes stored in the new
run state before spawn. The typed tier is the binding channel. A child whose
first message differs from the stored prompt is not admissible; the run records
`DISPATCH_MISMATCH_BLOCKED` and a later fresh continuation is required.

The continuation plan is committed before its SDD run is initialized. The old
run root, old progress ledger, old state, and old child transcript remain
untouched.
