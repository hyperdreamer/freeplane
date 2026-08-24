# Graph Workspace Map-Version Cache Remediation Design

**Date:** 2026-08-24

## Problem

The first timeout-remediation commit replaces the graph projection fixture's unsupported
`freeplane 1.12.0` header with the built-in `freeplane 1.12.15` dialect and removes its
custom interpreter registration. The focused cold-reload class passes, but the clean
graph-plugin gate still fails.

`MapLeaseManagerShould.loadsTheRealFixtureThroughMapLoaderOnTheSuppliedEdtWithoutCreatingAView`
starts a headless controller whose resource setup omits `xml/mapVersions.xml`. Its real
fixture load calls `MapVersionInterpreter.values()`, which catches the missing-resource
failure and stores an empty static array. That cache is process-global. A later
`GraphWorkspaceColdReloadShould` assertion therefore gets `MapVersionInterpreter.DEFAULT`
for the built-in fixture header, where `anotherDialect` is true. The same contaminated
suite run also exposes lifecycle failures that do not occur in isolated affected-class
runs.

## Decision

Keep the remediation test-only and repair both headless test boundaries.

1. `MapLeaseManagerShould.HeadlessResourceScope` will overlay the editor
   `xml/mapVersions.xml` alongside its existing properties and preferences. It will save
   and restore both the copied resource and the inherited `MapVersionInterpreter.values`
   array, clearing the cache only after the overlay is in place so the test loads the
   repository dialect table.
2. `GraphWorkspaceIntegrationSupport.FreeplaneScope` will snapshot its inherited
   interpreter array, clear it after `HeadlessResourceFiles` installs the graph test
   resources, and restore the exact inherited array during close. It will not add a
   custom dialect.
3. The graph fixture regression will deliberately begin with an empty interpreter cache,
   create a graph headless scope, prove that `freeplane 1.12.15` is recognized as a
   native dialect, and prove that scope close restores the inherited cache.

This follows the existing graph adapter test pattern for process-global Freeplane
state, while keeping resource ownership local to the test scopes that need it.

## Non-goals

- Do not modify production `MapVersionInterpreter`, `MapLoader`, `MFileManager`, or
  `ApplicationResourceController`.
- Do not change Gradle test execution, test timeouts, performance thresholds, or smoke
  tasks.
- Do not reintroduce a custom `freeplane 1.12.0` dialect registration.
- Do not change lifecycle test behavior without a separately reproduced root cause.

## Verification

1. The real MapLease fixture test fails before its map-version resource overlay exists
   and passes after it, with `anotherDialect == false`.
2. The graph fixture test fails against a deliberately empty inherited cache before its
   scope reset exists and passes after it, including cache restoration on close.
3. The selected MapLease and cold-reload classes pass together in one fresh JVM.
4. A clean `:freeplane_plugin_graph:clean :freeplane_plugin_graph:check
   :freeplane_plugin_graph:test :freeplane_plugin_graph:build` gate passes. If either
   lifecycle test still fails, capture its XML evidence and return to root-cause
   investigation rather than modifying it speculatively.
