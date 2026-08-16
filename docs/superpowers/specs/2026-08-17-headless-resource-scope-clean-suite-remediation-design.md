# Headless Resource Scope Clean-Suite Remediation Design

**Date:** 2026-08-17

## Goal

Make `:freeplane_plugin_graph:test --rerun-tasks` reproducible from a clean checkout by repairing the headless adapter-test resource setup, while preserving the primary setup error when initialization fails.

## Context

The terminal asynchronous-reset recovery review found that a fresh detached worktree fails in `GraphAdapterTestSupport.HeadlessResourceScope`. The scope resolves `testMapVersions` as `build/resources/test/xml/mapVersions.xml`, records any preexisting bytes, and copies the editor resource before creating the shared `xml` parent. `ConnectorSnapshotFactoryShould` then calls `headless.close()` during `@AfterClass` even if `@BeforeClass` failed before assigning `headless`, producing a secondary null-pointer failure that obscures the primary setup error.

The defect is unchanged between the Batch D merge base and the blocked recovery HEAD. It is independent of settling lifecycle behavior but invalidates the recovery plan's required clean full-module verification gate.

## Scope

Only these test fixtures change:

- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/MapSnapshotFactoryShould.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/ConnectorSnapshotFactoryShould.java`

No production source, build configuration, generated resources, lifecycle implementation, existing recovery plans, or terminal SDD evidence changes.

## Design

### Create the Exact Resource Parent Before Copying

`HeadlessResourceScope` will call `Files.createDirectories(testMapVersions.getParent())` immediately after resolving `testMapVersions` and before reading/copying the file. This explicitly establishes the destination invariant for the map-version resource instead of relying on preference-resource setup order to create the same parent incidentally.

The scope continues to snapshot existing map-version bytes before overwriting them and continues to restore or delete the file during close. No paths, resource contents, system properties, or headless starter behavior change.

### Preserve the Primary Setup Failure

`ConnectorSnapshotFactoryShould.tearDownHeadlessResources()` will close the static scope only when `@BeforeClass` assigned it. If setup fails, JUnit retains the original setup exception without adding a null-pointer teardown failure. A successfully initialized scope still closes exactly once through the existing lifecycle.

This is intentionally a narrow lifecycle guard. It does not add a shared resource abstraction, alter the private scope API, or suppress failures from `HeadlessMapScope.close()`.

## Verification

1. In a fresh detached worktree at the pre-fix revision, run the full graph-plugin suite with the repository Java 21 runtime and `--rerun-tasks`. Record the expected primary `NoSuchFileException` for `xml/mapVersions.xml` and the former secondary teardown failure.
2. After the fixture change, repeat the exact full suite in a new clean detached worktree without manually creating generated resource directories. Aggregate JUnit XML and require zero failures and zero errors.
3. Prove the teardown guard with a one-mechanism source mutant in a clean detached worktree: temporarily bypass only the new map-version parent creation while retaining the null guard. `ConnectorSnapshotFactoryShould` must fail from the original `NoSuchFileException` and must not report the secondary null-pointer teardown failure. Restore exact source bytes before final verification.
4. Run the focused connector fixture and the full graph-plugin module suite, then verify the commit changes exactly the two allowed fixture files and has no whitespace errors.

## Non-Goals

- Refactoring similar headless-resource scopes in unrelated adapter tests.
- Changing Gradle resource processing to precreate empty directories.
- Modifying the existing Graph Workspace lifecycle implementation or final-blocked run.
- Treating precreated build output as a test workaround.

## Success Criteria

A clean detached checkout runs `gradle :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks` successfully, setup failures remain attributable to their original cause, and the remediation commit is limited to the two authorized test fixtures.
