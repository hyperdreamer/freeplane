# Native Connector Snapshots And Adapter Boundaries Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for readability; controller state is canonical.

- **Task Identifier:** 2026-08-10-graph-workspace
- **Original backlog task:** Task 16, Snapshot native connectors and enforce adapter boundaries
- **Base commit:** `04d39279c4eed35254b0f234c8ec0c27c79a04bf`

**Goal:** Add immutable native-connector snapshots to each safe map snapshot without exposing hidden or relocked nodes, assigning IDs, using Freeplane's stale flat ID index, or leaking mutable Freeplane types into pure graph packages.

**Architecture:** `ConnectorSnapshotFactory` pairs the live root traversal with the already-built immutable `MapSnapshot`, indexes only non-excluded reachable targets, then reads source-owned `NodeLinks` in model order and emits immutable connector descriptors. `MapSnapshotFactory` calls a package-private model callback helper inside its existing lease EDT operation so nodes and connectors form one coherent snapshot; the public connector factory entry point uses the same helper through `MapLeaseAccess`. An ArchUnit test fixes the adapter boundary and forbidden lookup APIs for all later tasks.

**Tech Stack:** Java 8 source/bytecode, Freeplane `NodeLinks`/`ConnectorModel`/`ConnectorArrows` model APIs, existing Graph Workspace lease and immutable projection-input values, ArchUnit 1.4.1, JUnit 4, AssertJ, Gradle, and the existing `freeplane_plugin_graph` module.

## Global Constraints

- Follow `AGENTS.md`: Java source and target compatibility are 8, UTF-8 encoding, four-space indentation, JUnit 4/AssertJ/Mockito tests, and builds use escalated `gradle`, not Maven or the Gradle wrapper.
- Use Java at `/home/henry/.sdkman/candidates/java/21.0.8-zulu`; every Gradle and JDK command sets that exact `JAVA_HOME` and prepends its `bin` directory to `PATH`.
- Work only in `/data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-16-native-connectors` on branch `2026-08-10-graph-workspace-task-16-native-connectors`, based on `04d39279c4eed35254b0f234c8ec0c27c79a04bf`.
- The implementation allowlist is exactly four paths: `ConnectorSnapshotFactory.java`, `MapSnapshotFactory.java`, `ConnectorSnapshotFactoryShould.java`, and `AdapterArchitectureShould.java`, at the paths listed in Task 1. Do not modify core Freeplane code, `freeplane_api`, `MapLease`, `MapLeaseManager`, immutable projection values, build files, fixtures, translations, launchers, prior tests, or any fifth implementation path.
- Add no dependency and no compatibility fallback. Keep public `MapLease` exactly unchanged. Use its package-private `MapLeaseAccess`/`MapModelCallback` seam and reject arbitrary leases that do not implement that seam.
- All `MapModel`, `NodeModel`, `NodeLinks`, and `ConnectorModel` reads occur inside one synchronous callback run through the lease's `EdtExecutor`. `MapSnapshotFactory.snapshot` must build safe nodes and connectors in the same callback; do not take two lease callbacks that can observe different map revisions.
- Structural traversal from `MapModel.getRootNode()` is the sole endpoint authority. Never call or reference `MapModel.getNodeForID`, `MapController.getNodeFromID_`, `NodeModel.createID`, `NodeLinkModel.getTarget`, `ConnectorModel.getTarget`, registry enumeration, reflection into a map registry, parent-path lookup, or view/filter traversal.
- The supplied safe `MapSnapshot` must match the live model exactly by map ID, workspace order, root key, raw child indexes, child counts, `structuralLeaf`, and exclusion classification. Recompute exclusion only with Task 15's approved live predicates (`ancestorExcluded`, `NodeVisibility.isHidden`, and `SummaryNode.isHidden`) and require equality with each paired `NodeSnapshot.excluded()`. Reject a mismatched or stale standalone snapshot before reading connector arrows or labels. Integrated construction from `MapSnapshotFactory` naturally satisfies this check.
- A connector source and target are eligible only when their paired `NodeSnapshot` is non-excluded and structurally reachable from the current root. Excluded identity-only nodes remain useful for recoverable relationship status but never contribute native connector labels, directions, or endpoints.
- Build target resolution from non-null IDs encountered during the paired root traversal. A target string not present in that reachable non-excluded index is omitted, including hidden targets and detached descendants behind a relocked `EncryptionModel`, even when Freeplane's private ID index can still return them.
- Enumerate only connector models directly owned by each reachable source through `NodeLinks.getLinkExtension(node).getLinks()`. Do not use `NodeLinks.getLinks(node)`, because clone expansion can invoke flat lookup and identity creation. Ignore hyperlinks and other `NodeLinkModel` subtypes.
- Connector occurrence is zero-based per source among native `ConnectorModel` entries in their stored list order. Increment it before target eligibility filtering so contributor keys remain stable when an earlier target becomes hidden, locked, missing, or reachable again. Non-connector links do not consume occurrences.
- Preserve duplicate identical native connectors as distinct occurrences. Emit sources in raw root depth-first model order and occurrences in source list order; `MapSnapshot.withConnectors` remains the immutable canonicalization boundary.
- Omit a persistent self-connector because existing `ConnectorDescriptor` forbids identical exact source and target identities. Still consume its occurrence so later contributor keys do not change.
- Read effective arrows as `connector.getArrows().orElse(ConnectorArrows.DEFAULT)`. `ArrowType.NONE` means no arrow at that end; every other `ArrowType` means an arrow. Do not consult `LinkController`, styles, filters, views, transformed text, or UI resources.
- Read labels only from `ConnectorModel.getSourceLabel()`, `getMiddleLabel()`, and `getTargetLabel()`, with absent values becoming `""`. Normalize each CRLF, CR, LF, or consecutive line-break run to one ASCII space. Do not trim other content, read either endpoint's text, invoke node label extraction, or include labels in `toString()` output.
- Pure graph packages (`projection`, `geometry`, `layout`, and `canvas`) may not depend on `org.freeplane.features..` or `org.freeplane.view..`. Production graph classes may not call the forbidden flat lookup, identity creation, or link-target convenience methods named above. Enforce both rules with ArchUnit over production classes.
- Use test-driven development: write the focused behavior and architecture tests first, run them and observe failure because `ConnectorSnapshotFactory` and connector integration are absent, then implement the minimum production behavior and rerun green.
- The confidentiality mutant is mandatory: after green, replace only reachable-target map lookup with `connector.getTarget()`-based acceptance, prove the named relocked-target test fails because the stale indexed target is admitted, immediately restore exact production and test SHA-256 values, verify no mutant diff remains, and rerun green.
- Before staging, assert the index is empty. Stage only the exact four implementation paths, compare the cached-name list byte-for-byte to the sorted allowlist, and commit with exactly `2026-08-10-graph-workspace: Snapshot native graph connectors`.

## Task 1: Snapshot native connectors and enforce adapter boundaries

**Implementer tier:** Advanced

**Files:**

- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/ConnectorSnapshotFactory.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/MapSnapshotFactory.java:1-end`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/ConnectorSnapshotFactoryShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/AdapterArchitectureShould.java`

**Interfaces:**

- Consumes: package-private `MapLeaseAccess.withModelOnEdt(MapModelCallback<T>)`; existing `MapSnapshotFactory` safe root traversal; `MapSnapshot.withConnectors(List<ConnectorSnapshot>)`; immutable `NodeSnapshot`, `SourceNodeKey`, `ConnectorSnapshot`, `ConnectorDescriptor`, `NodeReference`, and `PersistedNodeId`; and Freeplane `NodeLinks`, `NodeLinkModel`, `ConnectorModel`, `ConnectorArrows`, and `ArrowType`.
- Produces this public operation plus one package-private integration overload:

```java
package org.freeplane.plugin.graph.adapter;

import java.util.List;

import org.freeplane.features.map.MapModel;
import org.freeplane.plugin.graph.projection.input.ConnectorSnapshot;
import org.freeplane.plugin.graph.projection.input.MapSnapshot;

public final class ConnectorSnapshotFactory {
    public List<ConnectorSnapshot> snapshotReachableConnectors(MapLease lease, MapSnapshot safeNodes);

    List<ConnectorSnapshot> snapshotReachableConnectors(
        MapModel model, int workspaceOrder, MapSnapshot safeNodes);
}
```

- The public method rejects nulls, a `safeNodes.mapReferenceId()` mismatch with `lease.mapReferenceId()`, or a lease without `MapLeaseAccess`; it executes exactly one `withModelOnEdt` callback and delegates to the package-private overload.
- The package-private overload is the only traversal implementation. It verifies `workspaceOrder` and derives every live key using `safeNodes.mapReferenceId()`. `MapSnapshotFactory` owns one `ConnectorSnapshotFactory`, creates the connector-free `MapSnapshot` inside its existing callback, invokes the overload with that same `model` and `workspaceOrder`, then returns `safeNodes.withConnectors(connectors)` before leaving the callback.

- [ ] **Step 1: Write failing connector snapshot tests**

Create `ConnectorSnapshotFactoryShould` using `GraphAdapterTestSupport` from `MapSnapshotFactoryShould`. Construct native connectors directly with `ConnectorModel(source, targetId)` and attach them with `NodeLinks.createLinkExtension(source).addArrowlink(connector)` so tests never call `createID`. Add these named tests:

```java
@Test public void snapshotsReachableConnectorsOnTheLeaseEdtAndIntegratesThemIntoMapSnapshots();
@Test public void preservesDuplicateOccurrencesDirectionsLabelsAndTraversalOrder();
@Test public void keepsOccurrenceGapsWhenAnEarlierConnectorTargetIsUnreachable();
@Test public void omitsConnectorsWhoseSourceOrTargetIsExcludedWithoutReadingTheirLabels();
@Test public void omitsPersistentSelfConnectorsWithoutRenumberingLaterContributors();
@Test public void supportsTransientSourcesWithoutAssigningIds();
@Test public void rejectsMismatchedOrStaleSafeSnapshots();
@Test public void omitsAConnectorToARelockedStaleIndexedTarget();
```

Required assertions:

- A guarded map/source proves the public factory performs all model and link reads inside one lease EDT callback. Calling `new MapSnapshotFactory().snapshot(lease)` still adds exactly one callback total and returns connectors already integrated, proving there is no node/connector callback gap.
- Two equal `ConnectorModel` values attached to one source survive as occurrences `0` and `1`, with distinct `ContributorKey`s. A second source earlier in raw depth-first order appears first regardless of target ID or label ordering.
- Cover `ConnectorArrows.NONE`, `FORWARD`, `BACKWARD`, and `BOTH`, plus `Optional.empty()` falling back to `ConnectorArrows.DEFAULT`. Assert the exact `arrowAtSource`/`arrowAtTarget` booleans.
- Set raw labels to values containing `\r\n`, lone `\r`, lone `\n`, and repeated line breaks. Assert only line-break runs become one ASCII space, absent labels become empty strings, and neither source nor target node text is substituted.
- Put a non-connector local link before native connectors and prove it does not consume an occurrence. Put an unresolved native connector before a valid one and prove the valid connector keeps occurrence `1`; reveal the target and prove occurrences become `0, 1` without changing the second key.
- Hidden and Freeplane-hidden-summary sources produce no connectors and their `NodeLinks`/connector label getters are not entered. A visible source pointing to an excluded target also produces no connector and does not read connector labels. `SHOW_HIDDEN_NODES` restores both eligibility and the same occurrence.
- A persistent self-connector is omitted but consumes its occurrence, so the next admitted connector uses occurrence `1`.
- An ID-less reachable source uses its exact transient structural key and remains ID-less with a `CountingMapModel.registryNode` count of zero before and after connector snapshotting.
- A safe snapshot from another map, a different workspace order, a changed structure, or a node whose hidden/summary exclusion changed after snapshot construction throws `IllegalArgumentException` and does not read connector arrows/labels or assign IDs.
- In the real `graph-locked-branch.mm` unlock/relock setup, attach a visible safe-sibling connector whose target string is `ID_LOCKED_SECRET` and whose middle label is `RELOCKED_CONNECTOR_SENTINEL`. Test-only direct lookup proves `map.getNodeForID("ID_LOCKED_SECRET")` still returns the secret after relock, while both standalone and integrated production snapshots omit the connector and their connector labels contain no sentinel. Unlocking restores that same connector without rebuilding the factory or lease.

- [ ] **Step 2: Write the failing architecture tests**

Create `AdapterArchitectureShould` with `ClassFileImporter` and `ImportOption.Predefined.DO_NOT_INCLUDE_TESTS`, importing `org.freeplane.plugin.graph..`. Add named tests that enforce:

```java
@Test public void productionCodeDoesNotUseFlatLookupIdentityCreationOrConvenienceTargets();
@Test public void pureGraphPackagesDoNotDependOnMutableFreeplaneTypes();
```

Use ArchUnit `noClasses()` call rules for:

```text
MapModel.getNodeForID(String)
MapController.getNodeFromID_(String)
NodeModel.createID()
NodeLinkModel.getTarget()
```

The pure-package rule covers `org.freeplane.plugin.graph.projection..`, `geometry..`, `layout..`, and `canvas..` and rejects dependencies on `org.freeplane.features..` or `org.freeplane.view..`. Keep this test structural: do not read source files or maintain a class-name allowlist.

- [ ] **Step 3: Run the focused tests and confirm the red phase**

```bash
env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-16-native-connectors \
  :freeplane_plugin_graph:test \
  --tests '*ConnectorSnapshotFactoryShould' \
  --tests '*AdapterArchitectureShould' \
  -PTestLoggingFull --rerun-tasks
```

Expected: FAIL because `ConnectorSnapshotFactory` and integrated connector behavior do not exist. Confirm the failure is missing production behavior/API, not malformed encryption setup, ArchUnit import failure, headless startup, or an unrelated baseline failure.

- [ ] **Step 4: Implement paired structural indexing**

In `ConnectorSnapshotFactory`, reject invalid public inputs before scheduling where possible, cast to `MapLeaseAccess` once, and perform the complete standalone operation in one callback. In the package-private overload:

1. Require a non-null model and verify `safeNodes.workspaceOrder()` agrees with the callback; use `safeNodes.mapReferenceId()` as the expected map identity for every derived key.
2. Require the live root and recursively pair it with `safeNodes.root()` using raw child indexes.
3. At each pair, derive the expected `SourceNodeKey` from non-null `node.getID()` or the raw path without mutation; require exact equality with `NodeSnapshot.key()`, exact child-count equality, and `NodeSnapshot.structuralLeaf() == copiedChildren.isEmpty()`.
4. Recompute live exclusion from `ancestorExcluded || NodeVisibility.isHidden(node) || SummaryNode.isHidden(node)` and require it to equal `NodeSnapshot.excluded()`. Pass the live exclusion to descendants so stale classification fails closed at the first mismatch.
5. Record every non-excluded paired source in raw depth-first order. Add only non-excluded persistent nodes to a target map keyed by ID; reject duplicate reachable IDs as ambiguous.
6. Complete this whole pairing pass before reading any source links so connectors may point forward in traversal order.

Do not read labels, group markers, transformed content, filters, or views while checking freshness. The three live exclusion predicates are the minimum confidentiality check; every other safe-node field remains owned by Task 15.

- [ ] **Step 5: Implement connector extraction and coherent integration**

For each non-excluded source pair, read `NodeLinks.getLinkExtension(source)` and its raw unmodifiable `getLinks()` collection. Walk stored links in order, ignoring non-connectors. For each `ConnectorModel`, capture then increment the connector-only occurrence before any filtering. Resolve `getTargetID()` solely through the reachable target map; skip absent targets and persistent self-targets before reading arrows or labels.

Map arrows from `connector.getArrows().orElse(ConnectorArrows.DEFAULT)` and normalize only the three optional connector-owned labels. Build `ConnectorDescriptor.of(sourceKey, targetReference, arrowAtSource, arrowAtTarget, sourceLabel, middleLabel, targetLabel)` and `ConnectorSnapshot.of(occurrence, descriptor)`. Return an unmodifiable copy; `safeNodes.withConnectors` performs canonical validation and ordering.

Modify `MapSnapshotFactory` only inside its existing lease callback: first construct the connector-free safe snapshot exactly as before, then call the package-private connector helper with the same live model/order and return `withConnectors`. Preserve all Task 15 traversal, labeling, lock, sequence, and public lease behavior.

- [ ] **Step 6: Run focused green and inspect scope**

```bash
env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-16-native-connectors \
  :freeplane_plugin_graph:test \
  --tests '*ConnectorSnapshotFactoryShould' \
  --tests '*AdapterArchitectureShould' \
  -PTestLoggingFull --rerun-tasks

git -C /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-16-native-connectors status --short
git -C /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-16-native-connectors diff --check
```

Expected: both focused classes PASS with zero failures/errors; `git status --short` names exactly the four implementation paths because this plan is already committed and ignored SDD artifacts are omitted; `git diff --check` is clean.

- [ ] **Step 7: Prove stale flat target resolution leaks a relocked connector with one isolated mutant**

Record SHA-256 for both production files and both test files. Temporarily change only connector target acceptance from the structurally built target map to `connector.getTarget()` and construct the same `NodeReference` from its returned node ID. Do not edit, skip, or rename tests and do not mutate source traversal, labels, or architecture rules.

Run only:

```bash
env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-16-native-connectors \
  :freeplane_plugin_graph:test \
  --tests '*ConnectorSnapshotFactoryShould.omitsAConnectorToARelockedStaleIndexedTarget' \
  -PTestLoggingFull --rerun-tasks
```

Expected: FAIL because `ConnectorModel.getTarget()` delegates to the stale map ID index, admits `ID_LOCKED_SECRET`, and exposes the `RELOCKED_CONNECTOR_SENTINEL` contributor that structural traversal omits. Immediately apply the inverse patch, verify all four files exactly match their recorded SHA-256 values, confirm no mutant diff remains, and rerun both complete focused classes green. If the named test does not fail for that exact reason, strengthen the real relock control before proceeding; an architecture-rule failure alone is not sufficient mutant evidence.

- [ ] **Step 8: Run full module, bundle, bytecode, API, and confidentiality gates**

```bash
env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-16-native-connectors \
  :freeplane_plugin_graph:test :freeplane_plugin_graph:verifyGraphBundle \
  -PTestLoggingFull --rerun-tasks

env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  /home/henry/.sdkman/candidates/java/21.0.8-zulu/bin/javap -public \
  /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-16-native-connectors/freeplane_plugin_graph/build/classes/java/main/org/freeplane/plugin/graph/adapter/MapLease.class
```

Required evidence:

- Aggregate every JUnit XML suite under `freeplane_plugin_graph/build/test-results/test`; require zero failures/errors and explicitly report suites, tests, and skips.
- `verifyGraphBundle` passes and the built plugin JAR contains `ConnectorSnapshotFactory.class`.
- `javap -verbose` reports class-file major version exactly `52` for `ConnectorSnapshotFactory` and `MapSnapshotFactory`.
- `javap -public MapLease` still exposes only `mapReferenceId()`, `state()`, and `close()`, with no `MapModel` in any public signature.
- Search the two production adapters for forbidden tokens named in the architecture test as a controller-readable cross-check; tests and test-only stale-index controls are allowed.
- `git diff --check` passes; HEAD remains the pinned plan commit; the index is empty; the worktree has exactly the four allowlisted implementation paths.

- [ ] **Step 9: Commit the exact implementation allowlist**

```bash
cd /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-16-native-connectors
test -z "$(git diff --cached --name-only)"
git add -- \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/ConnectorSnapshotFactory.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/MapSnapshotFactory.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/ConnectorSnapshotFactoryShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/AdapterArchitectureShould.java
printf '%s\n' \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/ConnectorSnapshotFactory.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/MapSnapshotFactory.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/AdapterArchitectureShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/ConnectorSnapshotFactoryShould.java \
  | sort > /tmp/task16-expected.txt
git diff --cached --name-only | sort > /tmp/task16-actual.txt
cmp /tmp/task16-expected.txt /tmp/task16-actual.txt
git diff --cached --check
git commit -m "2026-08-10-graph-workspace: Snapshot native graph connectors"
```

Expected: one implementation commit above the plan commit, exactly four staged implementation paths, clean index/worktree after commit, and no core, fixture, build, translation, or prior-task change.
