# Workspace Session Registry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reserve canonical workspace paths for live Graph Workspace sessions so duplicate opens can focus the existing session and Save As can claim, release, or atomically rekey ownership before any bytes are written.

**Architecture:** `WorkspaceSessionRegistry` owns committed path ownership and pending Save As reservations behind one monitor, while path canonicalization stays outside that monitor through the existing `WorkspaceUriResolver`. A registry-owned reservation token is the only capability that may promote its target to committed ownership; closing an uncommitted token rolls back only that target, and committing validates the exact store-produced `WorkspaceIdentityChange` before installing the new path and removing the old path in one synchronized transition.

**Tech Stack:** Java 8 source/bytecode, existing Graph Workspace workspace/path value types, JUnit 4, AssertJ, Gradle 9.0.0, and Azul Zulu JDK 21.0.8.

## Global Constraints

- Follow `/data/home/guest/Development/freeplane/AGENTS.md`: Java source and target compatibility remain 8, encoding is UTF-8, indentation is four spaces, tests use JUnit 4 and AssertJ, and builds use `gradle`, never Maven or the Gradle wrapper.
- Run every Gradle and JDK command with `JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu` and that JDK's `bin` first on `PATH`.
- Work only in `/data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-29-session-registry` on branch `2026-08-10-graph-workspace-task-29-session-registry`, based on `04d39279c4eed35254b0f234c8ec0c27c79a04bf`.
- The implementation allowlist is exactly four paths: `WorkspaceSessionId.java`, `WorkspacePathReservation.java`, `WorkspaceSessionRegistry.java`, and `WorkspaceSessionRegistryShould.java` at the paths listed below. Do not modify stores, URI resolution, workspace model types, build files, fixtures, translations, or any fifth implementation path.
- Add no dependency, compatibility fallback, global singleton, file write, store access, callback into `GraphWorkspaceStore`, background thread, or mutable Freeplane model dependency.
- Canonicalize every caller-supplied path with the existing `WorkspaceUriResolver.canonical(Path)` before acquiring the registry monitor. Registry state stores canonical paths only.
- Use one registry monitor for every committed-owner, pending-reservation, session-path, and token-state transition. Never call filesystem canonicalization while holding that monitor.
- Use strict TDD: add the focused behavior tests first, run them and observe failure because Task 29 types are absent, then add only the minimum production behavior and rerun green.
- Before staging, require an empty index. Stage only the exact four implementation paths, compare the staged-name list byte-for-byte with the sorted allowlist, and commit with exactly `2026-08-10-graph-workspace: Reserve live workspace paths`.

## Task 1: Reserve workspace paths for live sessions

**Implementer tier:** Advanced

**Files:**

- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/WorkspaceSessionId.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/WorkspacePathReservation.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/WorkspaceSessionRegistry.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/WorkspaceSessionRegistryShould.java`

**Interfaces:**

- Consumes: `WorkspaceUriResolver.canonical(Path): Path`, which resolves existing aliases with `toRealPath()` and canonicalizes a missing suffix through its nearest existing ancestor; and `WorkspaceIdentityChange.oldPath()`, `newPath()`, `oldId()`, and `newId()` from the already-merged workspace store.
- Produces:

```java
package org.freeplane.plugin.graph.control;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import org.freeplane.plugin.graph.workspace.WorkspaceIdentityChange;

public final class WorkspaceSessionId {
    public static WorkspaceSessionId of(UUID value);
    public static WorkspaceSessionId of(String canonicalUuid);
    public UUID value();
}

public interface WorkspacePathReservation extends AutoCloseable {
    void commit(WorkspaceIdentityChange change);
    @Override
    void close();
}

public final class WorkspaceSessionRegistry {
    public boolean register(WorkspaceSessionId id, Path canonicalPath);
    public Optional<WorkspaceSessionId> owner(Path canonicalPath);
    public WorkspacePathReservation reserveSaveAs(WorkspaceSessionId id, Path canonicalTarget);
    public void unregister(WorkspaceSessionId id);
}
```

- `WorkspaceSessionId` is an immutable UUID value object matching the existing graph ID conventions: reject null, require the canonical 36-character UUID shape for string input, and implement value equality, hash code, and canonical `toString()`.
- `register(id, path)` canonicalizes first. It returns `true` for a new registration and for an idempotent repeat of the same session/path. It returns `false` when the canonical path is already committed or reserved by another session, leaving all state unchanged. Reusing one session ID for another path throws `IllegalStateException`.
- `owner(path)` canonicalizes first and returns the owning session for either a committed path or a pending Save As target. A reservation therefore prevents both another registration and another reservation before the caller writes bytes.
- `reserveSaveAs(id, target)` requires an already registered session, a target different from its current path, no other active reservation for that session, and a target that is neither committed nor reserved. State violations throw `IllegalStateException`; reserving the current path throws `IllegalArgumentException`. On success the canonical target is immediately visible through `owner(target)`.
- `WorkspacePathReservation.close()` is idempotent. Before commit it removes only that exact pending target and leaves the current committed path owned. After commit it is a no-op and must not release the new committed path.
- `WorkspacePathReservation.commit(change)` canonicalizes `change.oldPath()` and `change.newPath()` before entering the registry monitor. It requires this exact live token, the session's current path to equal the canonical old path, and the token target to equal the canonical new path. A path mismatch throws `IllegalArgumentException` without changing ownership; a closed, committed, unregistered, replaced, or otherwise stale token throws `IllegalStateException`.
- A valid commit promotes the target to committed ownership before removing the old committed ownership, then removes the pending reservation, updates the session path, and marks the token committed, all while holding one monitor. A package-private no-op rekey observation seam may receive the old-path and new-path owners captured directly from the in-memory indexes after target promotion and before old-path removal, so the required order mutant is deterministic without reentering public methods or canonicalizing under the monitor; no production caller can supply or invoke it.
- `unregister(id)` is idempotent. It removes the session's committed path and any exact pending reservation under the same monitor, invalidates that token, and never affects another session's ownership.

- [ ] **Step 1: Write the failing focused tests**

Create `WorkspaceSessionRegistryShould` in package `org.freeplane.plugin.graph.control` with deterministic UUID literals, JUnit `TemporaryFolder`, real `WorkspaceUriResolver` canonical behavior through the registry, and no sleeps. Add these named tests:

```java
@Test public void canonicalOwnershipLetsDuplicateOpenFindTheOriginalSession();
@Test public void serializesConcurrentReservationsForOneCanonicalTarget();
@Test public void rejectsOwnedAndReservedTargetsBeforeTheCallerWritesBytes();
@Test public void closingAfterSaveFailureReleasesOnlyThePendingTarget();
@Test public void commitRekeysWithoutObservableUnownedWindow();
@Test public void validatesIdentityChangeAndReservationLifecycle();
@Test public void unregisterReleasesCommittedAndPendingOwnership();
```

Required behavior and independent assertions:

- Register an existing workspace through one normalized or symbolic alias, prove `owner` through another canonical alias returns the exact first `WorkspaceSessionId`, prove a second session's `register` returns `false`, and prove same-session/same-path registration is idempotent while same-session/different-path registration throws.
- Register two sessions on distinct old paths. Start two real threads from one `CountDownLatch`; both attempt the same missing canonical Save As target. Join with a bounded guard only, assert exactly one token and one `IllegalStateException`, assert `owner(target)` is the winning session, close the winning token, then prove the losing session can reserve that target. Do not use elapsed-time polling or `Thread.sleep`.
- For both a committed target and a pending target, place a caller-owned write counter immediately after `reserveSaveAs`; assert the reservation throws before the counter can advance and all existing owners remain unchanged.
- Simulate save failure by closing an uncommitted token twice. Assert the old path remains owned, the target becomes unowned, another session can reserve it, and closing the stale first token again cannot remove the second reservation.
- Supply the package-private rekey observation seam with a probe that records the old-path and new-path owners passed from the in-memory indexes at the exact internal transition point; do not call public path methods from the seam. Create a real `WorkspaceIdentityChange` through `GraphWorkspaceStore.create(...).saveAs(...)` using a test writer that writes the supplied bytes and a scheduler that is always shut down. During the probe both captured owners must be the same session; after commit only the public new-path owner remains; closing the committed token is a no-op.
- Reserve one target, then pass a real store-produced identity change with a different old path or new path and assert `IllegalArgumentException` plus unchanged old/target ownership. Assert commit after close and second commit fail with `IllegalStateException`. Assert a valid commit followed by close leaves the new ownership intact.
- Unregister with a pending token, assert both current and target ownership disappear, assert the token can close repeatedly but cannot commit, and prove another session's committed ownership is untouched. Repeated unregister is a no-op.

Before adding production code, name the break each test catches: path aliases bypassing duplicate detection; non-serialized target claims; writing before ownership; rollback removing the wrong claim; old-path removal before target promotion; stale/mismatched token acceptance; and unregister leaking or stealing ownership.

- [ ] **Step 2: Run the focused test and confirm the RED phase**

```bash
env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-29-session-registry \
  :freeplane_plugin_graph:test \
  --tests '*WorkspaceSessionRegistryShould' \
  -PTestLoggingFull --rerun-tasks
```

Expected: FAIL during test compilation because `WorkspaceSessionId`, `WorkspacePathReservation`, and `WorkspaceSessionRegistry` do not exist. Confirm the failure is those missing Task 29 types, not malformed fixtures, test thread leakage, an unrelated compile failure, or a test that accidentally exercises only a fake.

- [ ] **Step 3: Implement the immutable session identifier**

Create `WorkspaceSessionId` as a final immutable value object. Copy the existing typed-ID validation behavior rather than depending on package-private `UuidValueSupport`: `of(UUID)` rejects null, `of(String)` rejects null and noncanonical UUID text before `UUID.fromString`, `value()` returns the immutable UUID, and `equals`, `hashCode`, and `toString` delegate to that value. Do not add generation, ordering, serialization, or mutable state.

- [ ] **Step 4: Implement synchronized ownership and reservation state**

Create the reservation interface and registry. Use one private monitor, one `WorkspaceUriResolver`, committed ownership indexed both by canonical path and session ID, pending reservations indexed by canonical target and session ID, and a private registry-owned token implementation. Canonicalize public path arguments before entering synchronized sections. Check all conflicts before mutating any map.

Keep token state under the registry monitor. On rollback, remove a pending entry only when both indexes still point to that exact token. On commit, validate the exact token and both canonical paths, promote the target, capture both owners directly from the committed-owner index and invoke the package-private rekey observation seam with those immutable values, remove the old committed path, clear both pending indexes, update the session path, and mark the token committed without releasing the monitor. `owner` checks committed ownership first and then the exact pending token owner. Return `Optional.empty()` only when neither exists.

Do not expose internal maps, a reservation status query, a force-release operation, or a direct rekey method. The token interface is the only Save As transition capability.

- [ ] **Step 5: Run focused GREEN and inspect scope**

```bash
env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-29-session-registry \
  :freeplane_plugin_graph:test \
  --tests '*WorkspaceSessionRegistryShould' \
  -PTestLoggingFull --rerun-tasks

git -C /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-29-session-registry status --short
git -C /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-29-session-registry diff --check
```

Expected: the focused class passes with no failures; status names exactly the four implementation paths because this plan is already committed; and `git diff --check` is clean.

- [ ] **Step 6: Prove the atomic-rekey test is falsifiable**

Record SHA-256 for all four implementation files. Temporarily change only the commit transition order so it removes the old committed path before promoting the new committed path, while leaving the injected rekey observation at the point between those two operations. Run only:

```bash
env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-29-session-registry \
  :freeplane_plugin_graph:test \
  --tests '*WorkspaceSessionRegistryShould.commitRekeysWithoutObservableUnownedWindow' \
  -PTestLoggingFull --rerun-tasks
```

Expected: FAIL because the probe receives no old-path owner before target promotion. Immediately restore the exact production and test SHA-256 values, verify all four files match, confirm no mutant diff remains, and rerun the complete focused class green. A timeout, compilation error, or failure unrelated to old-path ownership is not valid mutation evidence.

- [ ] **Step 7: Run full module, bundle, bytecode, and API gates**

```bash
env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-29-session-registry \
  :freeplane_plugin_graph:test :freeplane_plugin_graph:verifyGraphBundle \
  -PTestLoggingFull --rerun-tasks

env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  /home/henry/.sdkman/candidates/java/21.0.8-zulu/bin/javap -public \
  /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-29-session-registry/freeplane_plugin_graph/build/classes/java/main/org/freeplane/plugin/graph/control/WorkspaceSessionRegistry.class
```

Required evidence:

- Aggregate every JUnit XML suite under `freeplane_plugin_graph/build/test-results/test`; require zero failures and zero errors, and report the observed suite/test/skip totals rather than guessing them.
- `verifyGraphBundle` passes and the built plugin JAR contains all three Task 29 production classes.
- `javap -verbose` reports class-file major version exactly 52 for all three production classes.
- `javap -public WorkspaceSessionRegistry` exposes only its public constructor plus `register`, `owner`, `reserveSaveAs`, and `unregister`; no map, lock, store, mutable Freeplane type, or test seam is public.
- Search the production files to confirm they do not import or reference `GraphWorkspaceStore`, file-writing APIs, executors, Freeplane model/view packages, or Task 16/18 types.
- `git diff --check` passes, the index is empty, and the worktree has exactly the four allowlisted implementation paths.

- [ ] **Step 8: Commit the exact implementation allowlist**

```bash
cd /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-29-session-registry
test -z "$(git diff --cached --name-only)"
git add -- \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/WorkspaceSessionId.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/WorkspacePathReservation.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/WorkspaceSessionRegistry.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/WorkspaceSessionRegistryShould.java
printf '%s\n' \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/WorkspacePathReservation.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/WorkspaceSessionId.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/WorkspaceSessionRegistry.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/WorkspaceSessionRegistryShould.java \
  | sort > /tmp/task29-expected.txt
git diff --cached --name-only | sort > /tmp/task29-actual.txt
cmp /tmp/task29-expected.txt /tmp/task29-actual.txt
git diff --cached --check
git commit -m "2026-08-10-graph-workspace: Reserve live workspace paths"
```

Expected: one implementation commit above the committed plan, exactly four staged implementation paths, and a clean index/worktree after commit.
