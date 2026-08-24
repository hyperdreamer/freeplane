# Graph Workspace Clean-Suite Remediation Design

## Goal

Make the Graph Workspace plugin's complete test task finish without failures by
repairing two isolated test fixtures. Production graph behavior, OSGi smoke
probes, build wiring, and the already-corrected Task 42 artifacts remain
unchanged.

## Evidence

At Task 42 correction commit `a093868c24b1b68f8e6986ad5e646572f2006ab1`:

- `WorkspaceDialogsShould` has nine deterministic failures. UI construction
  reaches `TextUtils` on both the JUnit thread and the AWT event-dispatch
  thread, but the class installs no `ResourceController` or `TextUtils` static
  mocks. Mockito static mocks are thread-local.
- With that class excluded, the graph-plugin test worker exits normally. The
  remaining failure is `GraphWorkspaceColdReloadShould`: its headless resource
  setup selects `freeplane/build/resources/viewer` as the application resource
  base, while map loading requires
  `xslt/freeplane_version_updater.xslt`, which is supplied by editor resources
  and is absent from that base.

The isolated run completed `699` tests with one bounded cold-reload failure and
no leaked Gradle test worker. This is a fixture-visibility problem, not a
graph-executor lifecycle problem.

## Scope

Modify only these test sources:

- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/WorkspaceDialogsShould.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceColdReloadShould.java`

No production source, Gradle configuration, generated resource, existing Task
42 deliverable, or terminal SDD run artifact is modified.

## Dialog Resource Fixture

`WorkspaceDialogsShould` will own a per-test resource fixture. Its JUnit
`@Before` and `@After` methods open and close the test-thread static mocks. A
separate helper opens equivalent mocks inside every EDT callback that constructs
a `GraphWorkspaceWindowModel`, and the corresponding mocks are closed on the
EDT during teardown.

The `TextUtils` stub returns resource keys for generic text and raw-text calls.
Its formatter supplies the six real contributor templates used by the existing
observable label assertions: source, middle, target, owner, key, and native
connector. Other formatter calls preserve the neighboring test pattern of
rendering the key and arguments deterministically. The assertions continue to
exercise the real dialog and window classes rather than asserting on mocks.

## Cold-Reload Resource Overlay

`HeadlessResourceFiles` already snapshots test-owned resources and restores
their previous bytes. It will extend that protocol to temporarily copy the
production editor XSLT
`freeplane/src/editor/resources/xslt/freeplane_version_updater.xslt` into the
configured viewer resource base at
`freeplane/build/resources/viewer/xslt/freeplane_version_updater.xslt`.

The starter therefore resolves the exact production XSLT through its normal
`ApplicationResourceController` resource-base lookup. Teardown restores the
prior file or removes the temporary file, just as it does for the existing
properties, version, map-version, and preference resources. The fixture does
not add a second resource-resolution path or alter application lookup code.

## Alternatives Rejected

Adding production changes to make resource lookup fall back across viewer and
editor directories would broaden behavior unrelated to the tests. Adding a
copied XSLT under plugin test resources would decouple the fixture from the
production XSLT that map loading actually uses. Creating a custom class loader
would add another lookup path and is unnecessary because the controlled viewer
base is already the path under test.

## Verification

The implementation will first reproduce both failures at the untouched
successor baseline. After the minimal fixture changes, it will run each focused
class, then the full `:freeplane_plugin_graph:test` task with Zulu JDK 21 and
explicit wall-clock bounds. The full module result must show zero failures and
zero errors and its test executor must exit. It will then run repository
`gradle test`, the three Task 42 smoke tasks, and the required focused
acceptance/performance gates before independent review.

The test-only mutations will be checked with a focused resource-visibility
mutant and with removal of an EDT resource fixture in a disposable worktree, so
the observed green result cannot arise from stale generated resources or a
vacant thread-local mock.
