# Safe Map Snapshots And Traversal Resolution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for readability; controller state is canonical.

- **Task Identifier:** 2026-08-10-graph-workspace
- **Original backlog task:** Task 15, Build safe map snapshots and traversal resolution
- **Base commit:** `adf1f821fbe47a782c1a3bbefb3f929b5bd8d9e8`
- **Backlog correction:** Task 15's original seven-file allowlist could not implement its lease/model integration because the only live `MapModel` is private to `MapLeaseManager.LeaseImpl`. The corrected allowlist adds `MapLeaseManager.java` and keeps the public `MapLease` interface unchanged.

**Goal:** Build immutable, confidentiality-safe snapshots and exact traversal-based node resolution from a live map lease without assigning IDs, consulting Freeplane's flat ID index, or exposing content behind hidden or locked branches.

**Architecture:** A package-private callback contract beside `MapLease` is implemented only by `MapLeaseManager.LeaseImpl`; it funnels one bounded operation through the lease's `EdtExecutor` and supplies the current live model plus an exactly validated workspace order. `MapSnapshotFactory` copies the root tree into existing immutable projection-input values, retaining excluded identities with an opaque label, while `TraversalNodeResolver` independently walks the same live tree for exact action routing. Neither class caches `NodeModel`, and the public lease API never exposes `MapModel`.

**Tech Stack:** Java 8 source/bytecode, Freeplane `MapModel`/`NodeModel`/visibility/summary/encryption APIs, existing Graph Workspace lease and projection-input values, Gradle, JUnit 4, AssertJ, and the existing `freeplane_plugin_graph` module.

## Global Constraints

- Follow `AGENTS.md`: Java source and target compatibility are 8, UTF-8 encoding, four-space indentation, JUnit 4/AssertJ/Mockito tests, and builds use escalated `gradle`, not Maven or the Gradle wrapper.
- Use Java at `/home/henry/.sdkman/candidates/java/21.0.8-zulu`; every Gradle and JDK command sets that exact `JAVA_HOME` and prepends its `bin` directory to `PATH`.
- Work only in `/data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-15-safe-map-snapshots` on branch `2026-08-10-graph-workspace-task-15-safe-map-snapshots`, based on `adf1f821fbe47a782c1a3bbefb3f929b5bd8d9e8`.
- The implementation allowlist is exactly eight paths: `MapSnapshotFactory.java`, `TraversalNodeResolver.java`, `MapLease.java`, `MapLeaseManager.java`, `MapSnapshotFactoryShould.java`, `TraversalNodeResolverShould.java`, `graph-locked-branch.mm`, and `graph-legacy-idless.mm`, at the paths listed in Task 1. Do not modify build files, core Freeplane code, `freeplane_api`, existing immutable projection values, translations, launchers, prior-task files, or any ninth path.
- Add no dependency and no compatibility fallback. Keep `MapLease`'s existing public methods exactly unchanged; arbitrary third-party/fake `MapLease` implementations that do not implement the package-private access contract are rejected rather than supported by a second path.
- All live `MapModel` and `NodeModel` reads occur inside one synchronous callback run through the lease's own `EdtExecutor`. Do not return or cache a whole `MapModel`; a resolver may return only the exact reachable `NodeModel` requested by its public contract.
- Structural traversal from `MapModel.getRootNode()` is the sole authority. Never call or reference `MapModel.getNodeForID`, `MMapController.getNodeFromID_`, `NodeModel.createID`, registry enumeration, reflection into `MapModel.nodes`, parent-path lookup, a view/filter traversal, or any transformed-content API.
- Copy each reachable node's model-order child list and compute `structuralLeaf` from that raw list before testing exclusion. Folding and transient filtering never prune traversal. The only fold-sensitive exception is Freeplane's own `SummaryNode.isHidden(node)` predicate, exactly as specified by the approved design.
- A node is excluded when an ancestor is excluded, `NodeVisibility.isHidden(node)` is true, or `SummaryNode.isHidden(node)` is true. Excluded nodes and all structurally exposed descendants remain in the snapshot for identity only: use exactly `SafeNodeLabel.of("Node", "Node")`, set `graphGroup` false, never call `SafeNodeLabelExtractor` for them, and preserve their structural child indexes.
- `SHOW_HIDDEN_NODES` affects only `NodeVisibility.isHidden`; it restores explicitly hidden nodes to ordinary labeling and resolution. It does not override `SummaryNode.isHidden`. Visible summary nodes and free nodes follow ordinary snapshot and resolver rules.
- A marked Graph Group records `graphGroup=true` on a non-excluded snapshot but never stops adapter traversal. Nested markers and all descendants remain present and exactly resolvable; projection, not this adapter, performs active-group collapse.
- For a node with non-null `getID()`, build `SourceNodeKey.persisted(NodeReference.of(mapId, PersistedNodeId.of(id)))`. For an ID-less node, build `SourceNodeKey.transientPath(mapId, rawChildIndexesFromRoot)`. Root has an empty path; child indexes include excluded siblings. Never call `createID` or otherwise attach/register identity.
- `attachedPersistentIds` is exactly the set of non-null IDs encountered by safe root structural traversal, including excluded identity-only nodes and excluding detached descendants behind locked branches. `hasInaccessibleBranch` is true if any structurally reachable node has a locked `EncryptionModel`; it conservatively represents every unknown identity behind such branches.
- A locked encrypted node is itself reachable, normally labeled unless otherwise excluded, and structurally a leaf because its exposed child list is empty. No snapshot label, search input, or resolver result may contain or return a detached descendant, even when the map's private ID index still returns it after unlock/relock. Unlocking restores ordinary traversal without a cache reset.
- `MapReference.sequence()` is positive `long`; the package-private lease callback converts it exactly to `int` and rejects values above `Integer.MAX_VALUE` with `IllegalArgumentException`. Never narrow by cast or clamp. `MapSnapshot.mapName()` is the nonempty value of `MapModel.getTitle()` read on the EDT; reject a null/empty title rather than inventing a second naming policy.
- Use test-driven development: write focused behavior tests first, run them and observe failure due to missing production behavior, then write the minimum implementation and rerun green.
- The confidentiality mutant is mandatory: after green, replace only the persistent-ID branch of resolver traversal with `MapModel.getNodeForID`, prove the relocked-secret test fails for the expected leak, immediately restore exact production and test SHA-256 values, verify no mutant diff remains, and rerun green.
- Before staging, assert the index is empty. Stage only the exact eight implementation paths, compare the cached-name list byte-for-byte to the sorted allowlist, and commit with exactly `2026-08-10-graph-workspace: Build safe map snapshots and traversal resolution`.

## Task 1: Build safe map snapshots and traversal resolution

**Implementer tier:** Capable

**Files:**

- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/MapSnapshotFactory.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/TraversalNodeResolver.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/MapLease.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/MapLeaseManager.java:1-end`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/MapSnapshotFactoryShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/TraversalNodeResolverShould.java`
- Create: `freeplane_plugin_graph/src/test/resources/maps/graph-locked-branch.mm`
- Create: `freeplane_plugin_graph/src/test/resources/maps/graph-legacy-idless.mm`

**Interfaces:**

- Consumes: the existing public `MapLease` methods `mapReferenceId()`, `state()`, and `close()`; existing `EdtExecutor.call(Callable<T>)`; `MapReference.sequence()` retained privately by `LeaseImpl`; `SafeNodeLabelExtractor.extract(NodeModel)`; `GraphGroupModel.isMarked(NodeModel)`; and immutable `MapSnapshot`, `NodeSnapshot`, `SourceNodeKey`, `NodeReference`, and `PersistedNodeId` factories.
- Produces these public adapter operations and package-private integration contracts:

```java
package org.freeplane.plugin.graph.adapter;

import java.util.Optional;

import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.plugin.graph.projection.input.MapSnapshot;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;

public final class MapSnapshotFactory {
    public MapSnapshot snapshot(MapLease lease);
}

public final class TraversalNodeResolver {
    public Optional<NodeModel> resolve(MapLease lease, SourceNodeKey key);
}

interface MapLeaseAccess {
    <T> T withModelOnEdt(MapModelCallback<T> callback);
}

interface MapModelCallback<T> {
    T apply(MapModel model, int workspaceOrder);
}
```

- `MapLeaseAccess` and `MapModelCallback` are package-private top-level interfaces declared in `MapLease.java`; `MapLeaseManager.LeaseImpl` implements `MapLease, MapLeaseAccess`. `withModelOnEdt` requires a non-null callback, invokes it via `manager.edt.call`, snapshots `entry.model` and `entry.reference.sequence()` under `manager.monitor`, rejects a closed lease or missing model with `IllegalStateException`, validates the sequence before narrowing, releases the monitor, then invokes the callback on the EDT. It never holds `manager.monitor` while traversing the model.
- `MapSnapshotFactory.snapshot` and `TraversalNodeResolver.resolve` require a non-null lease; they reject a lease that is not `MapLeaseAccess` with `IllegalArgumentException`. They execute their complete operation in one `withModelOnEdt` callback and retain no mutable model object afterward.

- [ ] **Step 1: Create the two real map fixtures**

Create `graph-locked-branch.mm` as a minimal valid Freeplane map loaded by the stock headless `MapLoader`. It starts unlocked and contains stable IDs `ID_LOCK_ROOT`, `ID_LOCKED_CONTAINER`, `ID_LOCKED_SECRET`, and `ID_LOCK_SAFE_SIBLING`; the secret node's exact raw text is `RELOCKED_SECRET_SENTINEL`. Do not persist guessed ciphertext or a fake lock flag. In the test, attach a real `EncryptionModel` with a known test encrypter to `ID_LOCKED_CONTAINER`, use a real `MapWriter` to lock it, call `unlock()`, then lock it again. After relock, assert the exposed child list is empty while `map.getNodeForID("ID_LOCKED_SECRET")` still returns the sentinel node as a test-only control.

Create `graph-legacy-idless.mm` as a minimal valid Freeplane map with root `ID_IDLESS_ROOT`, persistent sibling `ID_BEFORE_IDLESS`, a node with `TEXT="idless numbered node" NUMBERED="true"` and no `ID` attribute, and persistent sibling `ID_AFTER_IDLESS`. Load it through the stock reader and first prove `NodeStyleModel.getNodeNumbering(node) == Boolean.TRUE` and `node.getID() == null`. Mark the preceding sibling hidden in test setup so the idless key must remain raw path `[1]`, not visible-child path `[0]`.

- [ ] **Step 2: Write failing lease, snapshot, and confidentiality tests**

Use the real package-private `MapLeaseManager` test constructor and a controlled `EdtExecutor`/loader, following `MapLeaseManagerShould`'s headless `MapLoader` setup. Acquire real `LeaseImpl` instances; do not test the production path only through a fake `MapLeaseAccess`. Add focused tests in `MapSnapshotFactoryShould` for:

```java
@Test public void snapshotsThroughTheLeaseEdtWithMapIdentityOrderAndTitle();
@Test public void rejectsWorkspaceSequenceAboveTheSnapshotIntRange();
@Test public void computesStructuralLeafBeforeExcludedChildrenAreClassified();
@Test public void keepsHiddenSubtreesAsOpaqueIdentityOnlySnapshots();
@Test public void showHiddenRestoresOrdinaryLabelsWithoutOverridingHiddenSummaries();
@Test public void keepsVisibleSummariesFreeNodesAndNestedGraphGroupsOrdinary();
@Test public void usesRawTransientPathsWithoutAssigningIds();
@Test public void recordsOnlySafelyTraversedPersistentIdsInSortedImmutableOrder();
@Test public void marksARelockedBranchInaccessibleWithoutSnapshottingItsSecret();
@Test public void productionSourcesForbidFlatLookupAndIdentityCreation();
```

Required snapshot assertions:

- A callback probe proves both metadata and every model read occur inside `EdtExecutor.call`; map ID equals the lease ID, order equals the positive reference sequence, and name equals the model title.
- A sequence of `Integer.MAX_VALUE` succeeds unchanged; `Integer.MAX_VALUE + 1L` throws `IllegalArgumentException` before callback traversal. The public `MapLease` reflection surface is unchanged and contains no method returning or accepting `MapModel`.
- A visible parent with only one explicitly hidden child has `structuralLeaf=false`, retains that child, and the child plus descendants are `excluded=true`, `graphGroup=false`, and labeled exactly `SafeNodeLabel.of("Node", "Node")`. Give an excluded node a hostile raw value whose conversion throws or records access; the factory must not enter it.
- With root `NodeVisibilityConfiguration.SHOW_HIDDEN_NODES`, explicitly hidden nodes use `SafeNodeLabelExtractor`, become non-excluded, and keep raw structural positions. A blank unfolded summary carrying `SummaryNodeFlag.SUMMARY` and children remains excluded; a non-hidden visible summary carrying the same flag remains ordinary. A node carrying a real `FreeNode` extension remains ordinary. Do not replace these model extensions with test booleans.
- Outer and nested `GraphGroupModel` markers are both recorded and all descendants remain in the snapshot. Adapter traversal never stops at a group marker.
- The loaded numbered fixture node gets transient path `[1]` and remains ID-less. Repeat with a `CountingMapModel` whose `registryNode` counts calls; before and after snapshot, `getID()` is null and registry count is zero.
- `attachedPersistentIds` contains every persistent ID visited from the root, including hidden identity-only IDs, in `PersistedNodeId.value()` order and in an unmodifiable set. It excludes `ID_LOCKED_SECRET` after relock. The relocked container is a reachable labeled structural leaf and sets `hasInaccessibleBranch=true`; no snapshot text contains `RELOCKED_SECRET_SENTINEL`. Unlocking restores the child snapshot and clears inaccessibility when no locked branch remains.
- The source guard reads only `MapSnapshotFactory.java` and `TraversalNodeResolver.java` and rejects `getNodeForID`, `getNodeFromID_`, `createID`, `registryNode`, reflection, and transformed-content lookup tokens. The direct `getNodeForID` control remains confined to test code.

- [ ] **Step 3: Write failing traversal resolver tests**

Add focused tests in `TraversalNodeResolverShould` for:

```java
@Test public void resolvesPersistentNodesByRootTraversalOnTheLeaseEdt();
@Test public void resolvesDescendantsWithoutStoppingAtGraphGroups();
@Test public void rejectsMismatchedMapsUnknownIdsAndUnknownTransientPaths();
@Test public void rejectsHiddenAndHiddenSummarySubtreesUntilTheyBecomeVisible();
@Test public void resolvesIdlessNodesByRawStructuralPathWithoutAssigningIds();
@Test public void refusesAStaleIndexedSecretAfterUnlockAndRelock();
@Test public void unlockRestoresThePreviouslyInaccessibleSecret();
```

Required resolver assertions:

- Persistent lookup performs model-order DFS from the root, comparing only non-null `NodeModel.getID()` values after exclusion checks. It returns the exact reachable `NodeModel`, including descendants under marked groups and visible summaries/free nodes.
- Mismatched `SourceNodeKey.mapReferenceId()`, absent persistent IDs, negative/impossible path components already rejected by the value type, and out-of-range structural paths return `Optional.empty()` without mutation or fallback.
- Transient lookup starts at root and follows raw child indexes exactly. It checks exclusion at root and every traversed node before returning; hidden/hidden-summary ancestors make the whole subtree unavailable. `SHOW_HIDDEN_NODES` restores explicitly hidden resolution at the same path but does not override `SummaryNode.isHidden`.
- While relocked, direct map-index lookup still returns `ID_LOCKED_SECRET` as the control, but resolver returns empty and never reads the sentinel text. After `EncryptionModel.unlock()`, the same persistent source key resolves without reconstructing the resolver or lease; relocking makes it empty again.

- [ ] **Step 4: Run the focused tests and confirm the red phase**

```bash
env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-15-safe-map-snapshots \
  :freeplane_plugin_graph:test \
  --tests '*MapSnapshotFactoryShould' \
  --tests '*TraversalNodeResolverShould' \
  -PTestLoggingFull --rerun-tasks
```

Expected: FAIL because the two production adapters and package-private lease callback contract do not exist. Confirm the failure is missing production behavior/API, not malformed XML, encryption setup, headless startup, or an unrelated baseline failure.

- [ ] **Step 5: Implement the package-private lease callback**

Keep the three-method public `MapLease` declaration byte-for-byte in behavior and add only the two package-private interfaces shown above in the same source file. Extend private `LeaseImpl` with `MapLeaseAccess` and implement one synchronous path:

1. Reject a null callback before scheduling.
2. Call `manager.edt.call(new Callable<T>() { ... })`; calling from the EDT remains safe because the executor's existing contract runs inline there.
3. Inside the callable, synchronize briefly on `manager.monitor`, reject `closed.get()`, copy `entry.model` and `entry.reference.sequence()`, then leave the monitor.
4. Reject a null model. Reject `sequence > Integer.MAX_VALUE`; positive sequence is already guaranteed by `MapReference`. Convert only after validation.
5. Invoke `callback.apply(model, (int) sequence)` on the EDT and return its value.

Do not add `modelOnEdt()`, expose `MapReference`, widen `MapLease`, hold the manager monitor during callback work, or add a second off-EDT path.

- [ ] **Step 6: Implement safe snapshot construction**

`MapSnapshotFactory.snapshot` casts once to `MapLeaseAccess` and builds all output within its callback. Require a non-null root and nonempty `model.getTitle()`. Use a private traversal accumulator for `LinkedHashSet<PersistedNodeId>` plus an inaccessible flag, and a recursive helper receiving the current raw path and `ancestorExcluded`.

For each node, copy `new ArrayList<NodeModel>(node.getChildren())` once. Derive the key without mutation, add a non-null ID to the attached set, detect a locked `EncryptionModel`, and compute exclusion exactly from ancestor exclusion, `NodeVisibility.isHidden`, and `SummaryNode.isHidden`. Recurse through the copied children in raw index order even when excluded. Use the fixed label and `graphGroup=false` for excluded nodes; otherwise call `SafeNodeLabelExtractor.extract(node)` exactly once and read `GraphGroupModel.isMarked(node)`. Construct `NodeSnapshot` with `structuralLeaf = copiedChildren.isEmpty()` and finally `MapSnapshot.of(lease.mapReferenceId(), workspaceOrder, mapTitle, rootSnapshot, attachedIds, hasInaccessibleBranch)`.

Do not inspect `EncryptionModel.hiddenChildren`, enumerate the ID registry, cache traversal results, prune graph groups, or use visible-child indexes.

- [ ] **Step 7: Implement exact traversal resolution**

`TraversalNodeResolver.resolve` rejects mismatched map IDs before traversal, then executes one callback. Compute exclusion with the same two predicates and ancestor behavior as the factory. For persistent keys, perform root-first model-order DFS, pruning an excluded node before comparing its ID and never descending through it. For transient keys, follow `structuralPath()` from root against each node's copied raw child list, rejecting exclusion and out-of-range indexes at every step. A locked branch needs no special child registry access: its exposed list is empty, so descendants are naturally unreachable. Ignore `workspaceOrder` in the resolver callback but keep the shared validated callback contract.

Do not delegate resolver behavior to snapshot key search: resolution must return the current live reachable `NodeModel` and immediately reflect hide/show, lock/unlock, and structural changes.

- [ ] **Step 8: Run focused green and inspect scope**

```bash
env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-15-safe-map-snapshots \
  :freeplane_plugin_graph:test \
  --tests '*MapSnapshotFactoryShould' \
  --tests '*TraversalNodeResolverShould' \
  -PTestLoggingFull --rerun-tasks

git status --short
git diff --check
```

Expected: both focused classes PASS with zero failures/errors; `git status --short` names exactly the eight implementation paths because the plan/backlog correction is already committed and ignored SDD artifacts are omitted; `git diff --check` is clean.

- [ ] **Step 9: Prove flat lookup leaks relocked content with one isolated mutant**

Record SHA-256 for both production classes and both test classes. Temporarily change only the persistent-key resolver branch to return `model.getNodeForID(requestedId)` instead of walking from the root. Do not edit, skip, or rename tests and do not mutate snapshot construction.

Run only:

```bash
env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-15-safe-map-snapshots \
  :freeplane_plugin_graph:test \
  --tests '*TraversalNodeResolverShould.refusesAStaleIndexedSecretAfterUnlockAndRelock' \
  -PTestLoggingFull --rerun-tasks
```

Expected: FAIL because the mutant returns the detached `RELOCKED_SECRET_SENTINEL` node while the real traversal returns empty. Immediately apply the inverse patch, verify all four files exactly match their recorded SHA-256 values, confirm no mutant diff remains, and rerun both complete focused classes green. If the named test does not fail for that exact reason, strengthen the real relock control before proceeding; a source-token guard failing is not sufficient mutant evidence.

- [ ] **Step 10: Run module, bundle, bytecode, and confidentiality gates**

```bash
env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-15-safe-map-snapshots \
  :freeplane_plugin_graph:test :freeplane_plugin_graph:verifyGraphBundle \
  -PTestLoggingFull --rerun-tasks

/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin/javap -verbose \
  freeplane_plugin_graph/build/classes/java/main/org/freeplane/plugin/graph/adapter/MapSnapshotFactory.class \
  freeplane_plugin_graph/build/classes/java/main/org/freeplane/plugin/graph/adapter/TraversalNodeResolver.class \
  freeplane_plugin_graph/build/classes/java/main/org/freeplane/plugin/graph/adapter/MapLease.class \
  freeplane_plugin_graph/build/classes/java/main/org/freeplane/plugin/graph/adapter/MapLeaseAccess.class \
  freeplane_plugin_graph/build/classes/java/main/org/freeplane/plugin/graph/adapter/MapModelCallback.class \
  | rg 'major version: 52'

/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin/javap -public \
  freeplane_plugin_graph/build/classes/java/main/org/freeplane/plugin/graph/adapter/MapLease.class
```

Expected: all graph-plugin tests and `verifyGraphBundle` PASS with zero failures/errors; all five named production classes report major version 52; public `MapLease` still exposes only `mapReferenceId`, `state`, and `close`; production source has none of the forbidden flat lookup/identity tokens; `git diff --check` is clean; `git diff -- freeplane_api freeplane/src` is empty.

- [ ] **Step 11: Stage the exact eight-path allowlist and commit**

```bash
test -z "$(git diff --cached --name-only)"
git add -- \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/MapSnapshotFactory.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/TraversalNodeResolver.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/MapLease.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/MapLeaseManager.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/MapSnapshotFactoryShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/TraversalNodeResolverShould.java \
  freeplane_plugin_graph/src/test/resources/maps/graph-locked-branch.mm \
  freeplane_plugin_graph/src/test/resources/maps/graph-legacy-idless.mm
diff -u \
  <(printf '%s\n' \
    freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/MapLease.java \
    freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/MapLeaseManager.java \
    freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/MapSnapshotFactory.java \
    freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/TraversalNodeResolver.java \
    freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/MapSnapshotFactoryShould.java \
    freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/TraversalNodeResolverShould.java \
    freeplane_plugin_graph/src/test/resources/maps/graph-legacy-idless.mm \
    freeplane_plugin_graph/src/test/resources/maps/graph-locked-branch.mm) \
  <(git diff --cached --name-only | LC_ALL=C sort)
git commit -m "2026-08-10-graph-workspace: Build safe map snapshots and traversal resolution"
```

Expected staged names: exactly the eight `Files` paths, no SDD artifact, plan/backlog file, generated resource, build output, core file, or prior-task file. After commit, `git status --short` is clean except ignored controller artifacts.
