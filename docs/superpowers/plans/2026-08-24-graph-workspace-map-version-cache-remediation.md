# Graph Workspace Map-Version Cache Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.

**Goal:** Make graph-plugin headless map fixture tests load the built-in map-version
table deterministically and pass the clean plugin verification gate.

**Architecture:** The two headless scopes own temporary resource overlays and a
process-global `MapVersionInterpreter.values` cache. Each scope must snapshot the
inherited cache, install its own `xml/mapVersions.xml`, reset the cache to cause a lazy
reload from those resources, and restore the exact inherited array during close. No
production behavior changes.

**Tech Stack:** Java 8-compatible JUnit 4, AssertJ, Gradle 9, Freeplane headless
controller test fixtures.

## Global Constraints

- Modify only graph plugin test Java, graph test resources already changed by the parent
  commit, and this successor's design and plan documentation.
- Do not modify production Java, Gradle build logic, performance thresholds, timeout
  values, or smoke-task behavior.
- Use `/home/henry/.sdkman/candidates/java/21.0.8-zulu` for Gradle commands.
- Keep Java source compatible with Java 8 and use 4-space indentation.
- Do not add `MapVersionInterpreter.addMapVersionInterpreter(...)` or retain a custom
  `freeplane 1.12.0` fallback.
- The final functional gate must include `:freeplane_plugin_graph:clean`,
  `:freeplane_plugin_graph:check`, `:freeplane_plugin_graph:test`, and
  `:freeplane_plugin_graph:build`.

## Task 1: Isolate Map Lease Fixture Map-Version Resources

**Implementer tier:** Capable

**Files:**

- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/MapLeaseManagerShould.java:1-3000`

**Interfaces:**

- Consumes: the built-in `freeplane/src/editor/resources/xml/mapVersions.xml` dialect
  table and `MapVersionInterpreter.getVersionInterpreter(String)`.
- Produces: `HeadlessResourceScope` that overlays and restores
  `build/resources/test/xml/mapVersions.xml` and causes a fresh interpreter lookup
  while its headless controller is active.
- Produces: an assertion in
  `loadsTheRealFixtureThroughMapLoaderOnTheSuppliedEdtWithoutCreatingAView()` that
  resolves the copied fixture header with `anotherDialect == false`.

- [ ] **Step 1: Write the failing resource-boundary assertion**

Add the `MapVersionInterpreter` and `StandardCharsets` imports. In the existing real-fixture test, after the
fixture has loaded and before the leases are released, read the copied map's first line
with `Files.readAllLines(map, StandardCharsets.UTF_8).get(0)` and assert:

```java
assertThat(MapVersionInterpreter.getVersionInterpreter(firstLine).anotherDialect).isFalse();
```

Keep the existing MapLoader, supplied-EDT, and viewless-model assertions unchanged.

- [ ] **Step 2: Run the focused red test**

Run:

```text
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH gradle --no-daemon --no-parallel :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.adapter.MapLeaseManagerShould.loadsTheRealFixtureThroughMapLoaderOnTheSuppliedEdtWithoutCreatingAView
```

Expected: FAIL at the new `anotherDialect` assertion because the current scope cannot
provide `/xml/mapVersions.xml` and the static cache becomes empty.

- [ ] **Step 3: Make the scope own the map-version table and cache lifecycle**

Extend `HeadlessResourceScope` using the established
`MapSnapshotFactoryShould.HeadlessResourceScope` pattern:

```java
private final Path testMapVersions;
private final byte[] previousMapVersions;
private final MapVersionInterpreter[] previousMapVersionInterpreters;
```

At construction, capture the current interpreter array by reflecting on the private
`MapVersionInterpreter.values` field. Locate
`freeplane/src/editor/resources/xml/mapVersions.xml`, require it to be a regular file,
and copy it with a byte backup to `testResourceDirectory.resolve("xml/mapVersions.xml")`.
After the test resource overlay exists, set the reflected static field to `null` so the
headless controller lazily reloads the built-in table. Let `close()` declare `throws Exception`
so it can restore the reflected field, then restore the inherited interpreter array and the prior
map-version resource bytes alongside the existing properties, version, and preferences restoration.
Keep system-property restoration and all MapLease behavior unchanged.

- [ ] **Step 4: Run the focused green test**

Re-run the Step 2 command.

Expected: PASS, one test; the real fixture remains viewless and resolves to a dialect
where `anotherDialect` is false.

- [ ] **Step 5: Inspect and commit Task 1**

Run `git diff --check`, inspect the changed path list, and commit only the Task 1 test
source with:

```text
2026-08-24-graph-workspace: Isolate map lease map-version resources
```

## Task 2: Reinitialize The Graph Integration Scope Cache

**Implementer tier:** Capable

**Files:**

- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceColdReloadShould.java:1-1450`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceColdReloadShould.java`

**Interfaces:**

- Consumes: the `MapVersionInterpreter.values` static array through the test-only
  reflection helpers and the `HeadlessResourceFiles` map-version overlay.
- Produces: `FreeplaneScope` which reloads the built-in table even if the inherited
  cache is empty, then restores its inherited array on close.
- Produces: `fixtureUsesBuiltInMapDialect()` that verifies both the
  `freeplane 1.12.15` fixture header and native interpretation after a deliberately
  empty cache.

- [ ] **Step 1: Write the stale-cache regression**

Change `fixtureUsesBuiltInMapDialect()` so it reads and asserts the first fixture line
as before, snapshots the reflected interpreter array, assigns an empty
`new MapVersionInterpreter[0]`, then creates `GraphWorkspaceIntegrationSupport.FreeplaneScope`.
Inside the scope, assert:

```java
assertThat(MapVersionInterpreter.getVersionInterpreter(firstLine).anotherDialect).isFalse();
```

Close the scope and assert the empty inherited array was restored. Use an outer
`finally` to restore the test process's original array even if the assertion fails.
Expose package-visible test helpers on `GraphWorkspaceIntegrationSupport` as needed;
do not add a production helper.

- [ ] **Step 2: Run the focused red test**

Run:

```text
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH gradle --no-daemon --no-parallel :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.integration.GraphWorkspaceColdReloadShould.fixtureUsesBuiltInMapDialect
```

Expected: FAIL at the native-dialect assertion because the current `FreeplaneScope`
uses the injected empty static cache rather than reloading its installed resources.

- [ ] **Step 3: Reset and restore the cache within FreeplaneScope**

Restore the test-only `previousInterpreters` field and reflection helpers in
`GraphWorkspaceIntegrationSupport.FreeplaneScope`. Capture the inherited array before
constructing `HeadlessResourceFiles`; after that resource overlay is installed, assign
`null` to the reflected `values` field. In `close()`, restore `previousInterpreters`
before releasing the headless resources. Do not call `addMapVersionInterpreter`, and
keep the existing `MMapIO` singleton cleanup intact.

- [ ] **Step 4: Run focused and contamination green gates**

Run both commands:

```text
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH gradle --no-daemon --no-parallel :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.integration.GraphWorkspaceColdReloadShould
```

```text
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH gradle --no-daemon --no-parallel :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.adapter.MapLeaseManagerShould --tests org.freeplane.plugin.graph.integration.GraphWorkspaceColdReloadShould
```

Expected: both commands PASS; the second command covers the prior cross-class cache
contamination route.

- [ ] **Step 5: Run the clean module gate**

Run:

```text
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH gradle --no-daemon --no-parallel :freeplane_plugin_graph:clean :freeplane_plugin_graph:check :freeplane_plugin_graph:test :freeplane_plugin_graph:build
```

Expected: PASS. If a lifecycle assertion fails, inspect its XML output and report it;
do not modify lifecycle code without a new causal reproduction.

- [ ] **Step 6: Inspect and commit Task 2**

Run `git diff --check`, `git status --short`, and inspect the changed paths. Commit
only Task 2's test source with:

```text
2026-08-24-graph-workspace: Reset graph integration map dialect cache
```
