# Graph Workspace Clean-Suite Timeout Remediation Design

**Date:** 2026-08-24

## Problem

The focused graph-plugin gate can hang while running `GraphWorkspaceColdReloadShould`.
A captured test-JVM dump shows the JUnit worker blocked in
`SwingUtilities.invokeAndWait` while the EDT is blocked in
`UITools.showMessage` from `MFileManager.loadTreeImpl`. The modal warning is the
unknown/newer map-dialect path.

The integration fixture begins with `freeplane 1.12.0`, which is not part of the
repository's built-in `mapVersions.xml` dialect table. The test compensates by
calling `MapVersionInterpreter.addMapVersionInterpreter` for each temporary
scope. That method mutates a process-global, lazily initialized array. The
registration is not an appropriate synchronization boundary for concurrent
map loading, so the fixture can intermittently be classified as an unknown
 dialect and open a modal dialog in a headless test.

## Goals

- Make the graph integration fixture use a dialect already recognized by the
  normal built-in resource table.
- Remove the test's mutable global interpreter registration and restoration
  dependency.
- Add a fast regression check that fails if the fixture is changed back to an
  unrecognized dialect.
- Keep the change test-only and preserve production UI behavior.

## Non-goals

- Do not change `MFileManager`, `MapLoader`, `MapVersionInterpreter`, Gradle
  test configuration, or timeout values.
- Do not suppress or auto-dismiss production modal dialogs.
- Do not broaden the fixture or integration scenario.

## Chosen Approach

Update `maps/graph-projection.mm` to use the built-in `freeplane 1.12.15`
dialect. In `GraphWorkspaceColdReloadShould`, remove the per-scope custom
`MapVersionInterpreter` registration and the corresponding snapshot/restore
plumbing. Add a small test-side fixture-header assertion that reads the map
resource and verifies the header starts with the built-in dialect marker and
that `MapVersionInterpreter.getVersionInterpreter` does not report
`anotherDialect`.

This keeps the test on the same resource path production uses, eliminates the
race-prone global mutation, and makes a future fixture-version regression fail
as an ordinary assertion rather than hanging the EDT.

## Verification

1. The new fixture-header regression test is observed failing before the fixture
   change and passing after it.
2. `GraphWorkspaceColdReloadShould` passes in isolation.
3. A clean `:freeplane_plugin_graph:clean :freeplane_plugin_graph:check
   :freeplane_plugin_graph:test :freeplane_plugin_graph:build` gate passes and
   emits the repaired class XML.
4. The repository test suite and Graph Workspace OSGi/UI/launch smoke tasks
   pass.
5. The worktree is clean and the remediation commit changes only test code,
   test resources, and its design/plan documentation.
