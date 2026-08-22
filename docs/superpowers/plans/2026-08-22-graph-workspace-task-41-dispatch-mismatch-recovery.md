# Graph Workspace Task 41 Dispatch-Mismatch Recovery Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to execute this plan task-by-task.

**Goal:** Independently certify the committed Task 41 production cold-reload and lifecycle acceptance tests.

**Architecture:** A read-only successor audit evaluates the immutable two-file commit through production boundaries and serialized verification. A fresh task review follows; only an admitted finding may authorize a fix within the same two test files. Every dispatch uses a complete persisted role envelope through a byte-stable short pointer.

**Tech Stack:** Java 8 source/bytecode, Zulu Java 21.0.8, Gradle, JUnit 4, AssertJ, Mockito, Swing EDT, Freeplane map APIs, and existing Graph Workspace production APIs.

## Global Constraints

- Use exactly `/home/guest/.sdkman/candidates/java/21.0.8-zulu`; use `gradle`, never Maven or the Gradle wrapper.
- Preserve terminal predecessor `.superpowers/sdd/batch-j-task-41`; never edit or cite its report, prompt, transcript, state, or audit projection as successor evidence.
- Audit immutable commit `dfcd0f99010ad7dc04c167caecba002115a230f7`, parent `834d381f724c8606034a6bc5c878bb91d105cb63`, subject `2026-08-10-graph-workspace: Prove graph reload and cleanup`.
- The exact source range adds only `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceColdReloadShould.java` (SHA-256 `ebf21666566ed9fbc660582b4e1fdbce3ba7b7f2f78c39775934331dc8d02320`) and `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceLifecycleShould.java` (SHA-256 `e2a1f5ddfa5ab3e2cc1517567b3322e833c6a00646b5e88f168ac0a33df420d5`).
- The initial audit is read-only. A controller-authorized fix round may modify only those two test files; no production, build, translation, dependency, resource, shared-fixture, or compatibility change is allowed.
- Serialize focused/full tests and resource-sensitive probes after Task 39 and Task 40 verification is idle. Redirect verbose output to bounded temporary logs and remove it.
- Persist the full renderer output as a role envelope and dispatch only a short ASCII pointer. Compare candidate/pointer bytes before spawn and raw child-first-message/pointer bytes before report admission. Any mismatch is terminal.
- Use production controller/store/Freeplane writer/actors/leases/workers. Workspace save never saves `.mm`; source-map saves are explicit user actions. Cold reload closes handle/store/leases before production reopen. Lifecycle checks cover listener, lease, view, timer, temp, thread, and callback boundaries.
- Before a fix commit, require an empty index, exact allowlist stage, `git diff --check`, and subject `2026-08-10-graph-workspace: Prove graph reload and cleanup`.
- Frontier final review covers `834d381f724c8606034a6bc5c878bb91d105cb63..HEAD`.

## Task 1: Audit the committed Task 41 acceptance tests

**Implementer tier:** Capable

**Files:**
- Read-only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceColdReloadShould.java:1-end`
- Read-only: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceLifecycleShould.java:1-end`
- Read-only: production paths referenced by those tests under `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/:1-end`
- Read-only: `docs/superpowers/specs/2026-08-22-graph-workspace-task-41-dispatch-mismatch-recovery-design.md:1-end`
- Modify only in a controller-authorized fix round: the two Task 41 test paths above
- Initial audit writes only its report and creates no Git commit.

**Interfaces:** Consumes immutable Task 41 commit `dfcd0f99010ad7dc04c167caecba002115a230f7`; produces fresh identity, cold-reload, lifecycle, test, resource, and mutant evidence without source changes.

- [ ] **Step 1: Verify immutable identity and scope**

Verify current `HEAD`, clean source/index, exact implementation parent/subject, exact two-file range, `git diff --check`, known file hashes, and predecessor terminal state. Do not read or cite its child report or transcript.

- [ ] **Step 2: Audit production cold reload**

Inspect implementation and production call boundaries. Verify the test creates a real temporary `.mm` with views and `IUndoHandler`, uses randomized normal actors/connectors/groups through production APIs, explicitly saves source maps as user action, saves `.fpg`, closes handle/store/leases, reopens through production controller/loader, and compares persisted state/projection without workspace-triggered map save. Check deterministic seed/replay and falsifiable assertions.

- [ ] **Step 3: Audit lifecycle and cleanup**

Verify 25 open/close/restart cycles; listener, lease, view, timer, temporary-resource, and thread baselines; close during debounce; failed close Retry/Discard/Cancel without silent state loss; and no callbacks after close. Confirm synchronization is deterministic and assertions cannot pass through sleeps, swallowed failures, mocks disconnected from production, or cleanup that masks a leak.

- [ ] **Step 4: Run fresh serialized verification**

Only after no other Batch J verification child is active, run:

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test --tests '*GraphWorkspaceColdReloadShould' --tests '*GraphWorkspaceLifecycleShould' -PTestLoggingFull --rerun-tasks
JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu" PATH="$JAVA_HOME/bin:$PATH" gradle :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks
```

Record exact test/failure/error/skip totals. Delete temporary logs.

- [ ] **Step 5: Reproduce prescribed mutants read-only**

In disposable archives, independently skip dirty close save in `GraphWorkspaceStore.close` and remove the per-listener closed guard in `GraphUpdateCoordinator.publishProjection`. Require the corresponding acceptance tests to fail for the intended reason. Delete archives/logs; confirm active production/test hashes, `HEAD`, index, and worktree are unchanged.

- [ ] **Step 6: Write audit report and preserve source**

Write exactly one report at the supplied path. `DONE` requires every gate. Include `CHANGES: no source changes`, exact SHA/range/hashes, focused/full totals, mutant results, production/lifecycle evidence, concrete residual findings if any, unchanged `HEAD`, no residue, and confirmation predecessor child artifacts were excluded. Confirm no new source commit; only a later admitted fix finding may alter the two explicit test files.
