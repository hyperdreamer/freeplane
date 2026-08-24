# Graph Workspace Clean-Suite Timeout Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.

**Goal:** Remove the intermittent graph-plugin integration-test hang caused by an unrecognized map dialect and prove the clean plugin gate completes.

**Architecture:** Keep the remediation test-only. Make the shared graph projection fixture use the repository's built-in Freeplane dialect, remove the test's process-global interpreter mutation, and add a small fixture-header regression test that fails before the resource correction.

**Tech Stack:** Java 8-compatible JUnit 4, AssertJ, Freeplane map-version resources, Gradle 9.

## Global Constraints

- Do not modify production Java, Gradle build logic, performance thresholds, or smoke-task behavior.
- Use `/home/henry/.sdkman/candidates/java/21.0.8-zulu` for Gradle commands.
- Keep Java source compatible with Java 8 and use 4-space indentation.
- The final functional gate must include `:freeplane_plugin_graph:clean`, `:freeplane_plugin_graph:check`, `:freeplane_plugin_graph:test`, and `:freeplane_plugin_graph:build`.
- Do not retain a compatibility fallback for the unrecognized fixture dialect.

## Task 1: Make The Integration Fixture Use A Built-In Dialect
**Implementer tier:** Capable

**Files:**
- Modify: `freeplane_plugin_graph/src/test/resources/maps/graph-projection.mm:1`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceColdReloadShould.java:1-1450`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceColdReloadShould.java`
- Create: `docs/superpowers/specs/2026-08-24-graph-workspace-clean-suite-timeout-remediation-design.md`

**Interfaces:**
- Consumes: `MapVersionInterpreter.getVersionInterpreter(String)` and its public `anotherDialect` field from `org.freeplane.features.url.MapVersionInterpreter`.
- Produces: a graph projection fixture beginning with `<map version="freeplane 1.12.15">`, recognized by the repository map-version resource without calling `MapVersionInterpreter.addMapVersionInterpreter`.
- Produces: `GraphWorkspaceColdReloadShould.fixtureUsesBuiltInMapDialect()` as a JUnit 4 test method.

### Steps
- [ ] Before changing the fixture, add `fixtureUsesBuiltInMapDialect()` to `GraphWorkspaceColdReloadShould`. Read `/maps/graph-projection.mm` through the test class resource stream, assert the first line starts with `<map version="freeplane 1.12.15"`, and assert `MapVersionInterpreter.getVersionInterpreter(firstLine).anotherDialect` is false. Add the required `InputStream`, `IOException`, `StandardCharsets`, and `MapVersionInterpreter` imports.
- [ ] Run only the new test with `gradle --no-daemon --no-parallel :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.integration.GraphWorkspaceColdReloadShould.fixtureUsesBuiltInMapDialect`; confirm it fails because the current fixture starts with `freeplane 1.12.0`.
- [ ] Change the fixture header from `freeplane 1.12.0` to `freeplane 1.12.15`, leaving all nodes and IDs byte-for-byte unchanged.
- [ ] Remove `previousInterpreters`, the `mapVersionInterpreters()` and `restoreMapVersionInterpreters(...)` helpers, the `MapVersionInterpreter.addMapVersionInterpreter(...)` call in `FreeplaneScope`, and the `clearMapIoSingleton()`/interpreter restoration dependency only where it is no longer needed. Do not alter unrelated scope cleanup behavior.
- [ ] Run the focused class and confirm the new regression test and `coldReloadsProductionWorkspaceStateAndProjection` both pass without modal-dialog warnings.
- [ ] Inspect `git diff --check`, `git status --short`, and the changed-path list. Commit with subject `2026-08-24-graph-workspace: Stabilize clean graph integration fixture`.

### Verification
- [ ] The pre-change regression command fails for the expected old header.
- [ ] The focused class command exits 0 and emits its XML result.
- [ ] The commit contains no production or Gradle build changes.
