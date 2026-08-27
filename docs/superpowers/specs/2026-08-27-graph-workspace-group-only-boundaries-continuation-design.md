# Graph Workspace Group-Only Boundaries Continuation Design

- Date: 2026-08-27
- Status: Approved for implementation
- Scope: Recover the group-only-boundaries SDD work after the Task 2 review dispatch died without a verdict, preserving the two valid commits, then complete the remaining canvas work and the final review.

## Context

The run `2026-08-27-graph-workspace-group-only-boundaries` (plan digest `c8cea37b8b039f3c4374da6a7920a26c36856d3b973b27e5611deaeff44fcc4d`) progressed through Task 1 (commit `4fdefc93c7`, task-reviewed SPEC PASS / QUALITY APPROVED, `TASK_COMPLETE`) and Task 2 (commit `3d364e1768`, implementer status `DONE_WITH_CONCERNS`, both concerns observational and accepted by the controller ruling). The Task 2 review session (`01a0427d-8812-727d-a76d-cb9d59c6dff3`, frontier tier) terminated mid-analysis — while reproducing falsifiability probe 2 in a scratch clone — without writing `task-2-reviewer-report.md`. No review verdict exists for Task 2.

The controller recorded the mismatch, which routed to `DISPATCH_MISMATCH_BLOCKED` (terminal; the state machine accepts no continuation event there). Per the recovery skill, the blocked run root is preserved byte-for-byte (`.superpowers/sdd/2026-08-27-graph-workspace-group-only-boundaries`), the mismatched child's verdict is never admitted (there is none), and a fresh continuation run audits the exact committed range and completes the remaining work.

## Carried-forward state (Git commits are authority)

- `7beb2bf4ce` — committed design + plan (amended after Task 1 preflight found three plan defects)
- `4fdefc93c7` — Task 1: project group-marked nodes only (reviewed and approved)
- `3d364e1768` — Task 2: separate sibling boundaries by construction (implementer DONE_WITH_CONCERNS; **not yet independently reviewed**)
- Worktree `graph-workspace-group-only-boundaries` is clean at `3d364e1768`; `main` remains at `7beb2bf4ce`.

Task 2's commit changes exactly the 7 allowlisted files (`GraphStreamLayoutEngine.java`, `TypedSpringBox.java`, `TypedNodeParticle.java`, `PerceptualIdlePolicy.java`, `BoundarySeparationShould.java`, `PerceptualIdlePolicyShould.java`, `TypedForcesShould.java`), +800/-440, and the full module suite was BUILD SUCCESSFUL (753 tests, 0 failures) per the implementer's evidence — that claim is re-verified independently by the continuation audit, which must not cite the blocked run's reports as evidence.

## Design

### 1. Continuation audit task (no source changes)

A fresh task audits the exact range `4fdefc93c7..3d364e1768`:

- Verify the range contains exactly one commit touching exactly the 7 allowlisted files; `git show --check` clean; worktree clean.
- Verify the production behavior against the original plan's Task 2 requirements (the original plan file is the requirement source, read-only): size-aware ring packing with `hypot(maxW + GAP, maxH + GAP) / (2 sin(pi/N))`, root frames on the top ring, `BoundarySizes` constants 16/24/8/8/16, boundary repulsion factor 0.5 wired through `disp` (not the wiped `displacement` parameter), idle thresholds 0.05/0.10, hierarchy rest lengths 100/60.
- Independently reproduce the red/green and all three falsifiability probes in a **scratch clone** (`/tmp/gw-continuation-probe`), never touching the worktree; record measured values.
- Deliver a review-style verdict: SPEC PASS/FAIL, QUALITY APPROVED/REJECTED, findings ledger. Produces no commit.

The audit's findings feed the task review gate like any implementation; if the audit finds load-bearing defects, the normal fix wave applies.

### 2. Remaining work: canvas task

The original plan's Task 3 ("Render group boundaries from labels", advanced tier) is unchanged and becomes the continuation's second task: label-sized empty-hull octagons via `GraphGeometryEngine` with `GeometryTextMetrics`, coral non-root hull painting, node painting/hit/search/accessibility removal, `HullIntersection.siblingOverlap`, and the layout/geometry call sites threaded with metrics. Same Files list, same steps, same mutant.

### 3. Final review

The mandatory frontier final review covers the full continuation range `3d364e1768..HEAD` (the audit adds no commits), and the carried-forward Task 2 range by reference through the audit.

## Preserved invariants and rules

- The blocked run root, its `state.json`, `progress.md`, prompts, briefs, and reports stay byte-preserved and unreferenced as evidence.
- Continuation dispatches render and persist prompts before spawn; each admitted transcript's first user message must match the stored renderedPrompt byte-for-byte; typed tiers are the plan's tiers.
- The group-only rule, retained-dormant policy (ProjectedNode/prominence APIs stay), pin override, preserved layout invariants (SHA-256 identity, ordering, quality 0.10, cross-map cap 0.005, chooseNodePosition no-op) all carry over from the original design.
- Staging rule per task (empty index, exact allowlist subset, `git diff --cached --check`) unchanged.

## Scope

Only `freeplane_plugin_graph` changes; the audit task changes no source. No `freeplane_api` surface changes; no persistence format change; no new UI.
