# Workspace Session Registry Final Review Recovery Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for readability; controller state is canonical.

- **Task Identifier:** 2026-08-10-graph-workspace
- **Original backlog task:** Task 29, Reserve workspace paths for live sessions
- **Original merge base:** `04d39279c4eed35254b0f234c8ec0c27c79a04bf`
- **Verified implementation commit:** `f9befe712c08ba77ae97b1669aa21b25e37302e3`
- **Superseded terminal run:** `.superpowers/sdd/2026-08-13-workspace-session-registry`, revision 18, `DISPATCH_MISMATCH_BLOCKED`

**Goal:** Establish a fresh canonical audit trail and Frontier final-review verdict for the already implemented and task-approved workspace session registry without changing its deliverables.

**Architecture:** Treat Git commit `f9befe712c08ba77ae97b1669aa21b25e37302e3` and the four allowlisted Task 29 files as immutable verification inputs. One read-only verification task reruns all behavioral, bundle, bytecode, API, and branch-shape gates; independent task review then precedes a whole-branch Frontier review from the original merge base. The new final ledger carries prior finding `F-1` and the superseded run's terminal dispatch mismatch so neither is silently discarded.

**Tech Stack:** Java 8 source/bytecode, JDK 21.0.8 Zulu, Gradle, JUnit 4, AssertJ, Git, deterministic subagent-driven development.

## Global Constraints

- Follow `AGENTS.md`: Java source and target compatibility are 8, UTF-8 encoding, four-space indentation, JUnit 4/AssertJ/Mockito tests, and builds use `gradle`, not Maven or the Gradle wrapper.
- Use Java at `/home/henry/.sdkman/candidates/java/21.0.8-zulu`; every Gradle and JDK command sets that exact `JAVA_HOME` and prepends its `bin` directory to `PATH`.
- Work only in `/data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-29-session-registry` on branch `2026-08-10-graph-workspace-task-29-session-registry`.
- Do not modify, stage, amend, or commit any production file, test file, build file, original plan, original run artifact, or prior commit. This recovery task is verification-only.
- Keep the original terminal run immutable at revision 18. Do not adopt its uncorrelated Frontier child, edit its state, or reuse its dispatch key.
- The verified implementation commit remains `f9befe712c08ba77ae97b1669aa21b25e37302e3`, with parent `0de6c475c86bfb3550e63425e1df3b8baa203a79` and exact subject `2026-08-10-graph-workspace: Reserve live workspace paths`.
- The implementation range `0de6c475c86bfb3550e63425e1df3b8baa203a79..f9befe712c08ba77ae97b1669aa21b25e37302e3` must contain exactly the original four Task 29 allowlisted paths.
- Carry prior finding `F-1` as Important, load-bearing, and recorded `fixed`: the former non-final registry and executable rekey override must remain absent in final code.
- Carry the superseded run's `DISPATCH_MISMATCH_BLOCKED` history into the final-review ledger as audit context, not as a code finding and not as evidence of final approval.
- A new Frontier reviewer must inspect the whole branch from `04d39279c4eed35254b0f234c8ec0c27c79a04bf` through the recovery-plan `HEAD`; only that correlated review may complete this run.

## Task 1: Reverify the immutable Task 29 implementation

**Implementer tier:** Advanced

**Files:**

- Verify read-only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/WorkspaceSessionId.java`
- Verify read-only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/WorkspacePathReservation.java`
- Verify read-only: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/WorkspaceSessionRegistry.java`
- Verify read-only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/WorkspaceSessionRegistryShould.java`
- Verify read-only: `.superpowers/sdd/2026-08-13-workspace-session-registry/state.json`
- Write report only at the dispatch-provided report path under the new ignored recovery run root.

**Interfaces:**

- Consumes: immutable implementation object `f9befe712c08ba77ae97b1669aa21b25e37302e3`; original task brief `.superpowers/sdd/2026-08-13-workspace-session-registry/task-1-brief.md`; original task-review report; round-1 fixer and rereviewer reports; terminal original state revision 18.
- Verifies: `WorkspaceSessionId.of(UUID|String)`; `WorkspacePathReservation.commit(WorkspaceIdentityChange)` and `close()`; and `WorkspaceSessionRegistry.register`, `owner`, `reserveSaveAs`, and `unregister` exactly as specified by the original task brief.
- Produces: no deliverable or Git change; one bounded implementer report with fresh command evidence and the unchanged `HEAD` SHA.

- [ ] **Step 1: Verify inherited Git and audit identity before running code**

Run:

```bash
wt=/data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-29-session-registry
original_run=$wt/.superpowers/sdd/2026-08-13-workspace-session-registry

git -C "$wt" status --short --branch
git -C "$wt" show -s --format='%H%n%P%n%s' f9befe712c08ba77ae97b1669aa21b25e37302e3
jq '{revision,phase,blockedReason,fixRound,finalFixUsed,findings}' "$original_run/state.json"
git -C "$wt" diff --name-only \
  0de6c475c86bfb3550e63425e1df3b8baa203a79..f9befe712c08ba77ae97b1669aa21b25e37302e3
```

Require a clean index/worktree, the exact implementation parent and subject from Global Constraints, original state revision 18 in `DISPATCH_MISMATCH_BLOCKED`, `fixRound` 1, `finalFixUsed` false, and `F-1` recorded `fixed`. Require exactly these implementation paths:

```text
freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/WorkspacePathReservation.java
freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/WorkspaceSessionId.java
freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/WorkspaceSessionRegistry.java
freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/WorkspaceSessionRegistryShould.java
```

Stop with `BLOCKED` if any immutable identity differs. Do not repair or edit it.

- [ ] **Step 2: Inspect the final implementation requirement by requirement**

Read the original task brief and the four immutable files. Confirm all public path inputs are canonicalized before the single registry monitor; committed ownership and pending reservations are indexed under that monitor; conflicts are checked before mutation; token close/unregister remove only exact token entries; and commit promotes the target before removing the old path.

Confirm `WorkspaceSessionRegistry` is final, has no overridable or caller-supplied executable rekey callback, and captures only passive registry-owned `RekeyObservation` values after target promotion and before old-path removal. Confirm the public surface has no map, lock, store, status query, force release, direct rekey, or test seam.

- [ ] **Step 3: Run focused and full verification fresh**

Run:

```bash
env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-29-session-registry \
  :freeplane_plugin_graph:test \
  --tests '*WorkspaceSessionRegistryShould' \
  -PTestLoggingFull --rerun-tasks

env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-29-session-registry \
  :freeplane_plugin_graph:test :freeplane_plugin_graph:verifyGraphBundle \
  -PTestLoggingFull --rerun-tasks
```

Require all seven named focused tests to pass. Aggregate every JUnit XML suite under `freeplane_plugin_graph/build/test-results/test`; require zero failures and zero errors, and report observed suite/test/skip totals. Require `verifyGraphBundle` to pass.

- [ ] **Step 4: Run bytecode, API, dependency, and Git gates**

Run `javap -verbose` on all three Task 29 production classes and require major version 52. Run `javap -public` on `WorkspaceSessionRegistry` and require a public final class exposing only its public constructor plus `register`, `owner`, `reserveSaveAs`, and `unregister`.

Search the production files and require no reference to `GraphWorkspaceStore`, file-writing APIs, executors, Freeplane model/view packages, Task 16/18 types, or an executable rekey hook. Run:

```bash
git -C /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-29-session-registry diff --check \
  04d39279c4eed35254b0f234c8ec0c27c79a04bf..HEAD
git -C /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-29-session-registry status --short
```

Require no diff-check output and a clean worktree.

- [ ] **Step 5: Write the verification report**

Write exactly one report at the dispatch-provided report path. Return `DONE` only if every inherited identity, behavior, focused test, full module, bundle, bytecode, API, dependency, and Git gate passes. Include exact commands, observed test totals, current `HEAD`, and a statement that no deliverable was modified.

Use `DONE_WITH_CONCERNS`, `NEEDS_CONTEXT`, or `BLOCKED` only under the role contract, with concrete evidence. Do not treat the uncorrelated prior Frontier approval as verification evidence.

- [ ] **Step 6: Commit nothing and prove recovery scope**

Run:

```bash
wt=/data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-29-session-registry
test -z "$(git -C "$wt" status --porcelain)"
test "$(git -C "$wt" rev-parse HEAD)" = "$(git -C "$wt" rev-parse HEAD^{commit})"
git -C "$wt" log -1 --oneline
```

Do not stage, amend, or commit. Expected: `HEAD` remains the committed recovery-plan object supplied in Dispatch Context, the index/worktree are clean, and no implementation or verification commit was created by this task.
