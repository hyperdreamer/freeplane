# Technical Specification: Most Recent Workspaces

- Date: 2026-09-10
- Status: Draft (ready for implementation)
- Approved design: `docs/superpowers/specs/2026-09-10-most-recent-workspaces-design.md`
  (SHA-256 `23b57bcb1d213c0c6b1ccc67d6a1417ddccce874830290f438e2b0786032a0b1`)
- Approved mockup: `docs/superpowers/specs/images/2026-09-10-most-recent-workspaces-mockup.png`
- Target module: `freeplane_plugin_graph` in the delivery worktree
  `/data/home/guest/Development/freeplane/.worktrees/graph-workspace`
- Toolchain: Java 21 at `/home/henry/.sdkman/candidates/java/21.0.8-zulu`, `gradle`

---

## 1. Scope and Non-Goals

### 1.1 In scope

1. **Recent Workspaces submenu.** The graph workspace window's **File** menu gains a
   `Recent Workspaces` submenu, placed directly after `Save As...` and before the existing
   separator and `Close`. It lists the newest existing workspace files first, labelled
   `file name (full containing folder)`, capped at 8 visible entries, with an enabled
   `Clear Recent Workspaces` action whenever stored entries exist.
2. **Recent-workspace reopening.** `View > Open Graph Workspace` in the main window first
   tries the most recently used stored workspace that still exists, opens it with
   `openExisting(Path)` (which can never create a workspace), and falls back to the existing
   file chooser when no stored workspace can be opened. A failed recent open is reported
   through the command message sink before the chooser appears.

The recollection is one application-wide list persisted in Freeplane user properties under
`graph_workspace_recent_workspaces`; the graph workspace window's `File > Open...` entry and
the chooser keep today's create-or-open semantics.

### 1.2 Non-goals

- No recents entry in the main window's `View` menu; the graph workspace window's `File` menu
  is the only menu surface.
- No workspace file name in the graph workspace window title.
- No preferences UI for either cap; the stored cap (25) and displayed cap (8) are constants.
- No pinning, grouping, per-workspace metadata, or per-entry removal. `Clear Recent Workspaces`
  is the only removal affordance.
- No `.fpg` schema, version, or migration change; the recents list is never stored in workspace files.
- No new persistence machinery and no changed flush timing: the property is updated in memory by
  `ResourceController.setProperty` and flushed with the rest of the user properties on shutdown
  and preferences close, exactly like core's recent-maps list.
- No `freeplane_api` source, bytecode, or OSGi export change (`ext.bundleExports = ''` stays empty).
- No behavior change for files selected in the chooser, including files that fail to open.

### 1.3 File change map

| Action | Path |
| --- | --- |
| Create | `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/RecentWorkspaceList.java` |
| Create | `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/RecentWorkspaceRecorder.java` |
| Modify | `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/GraphWorkspaceController.java` |
| Modify | `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceController.java` |
| Modify | `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java` |
| Modify | `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/SwingGraphWorkspaceViewFactory.java` |
| Modify | `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/OpenGraphWorkspaceAction.java` |
| Modify | `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/GraphModeExtension.java` |
| Modify | `freeplane/src/viewer/resources/translations/Resources_en.properties` |
| Create | `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/RecentWorkspaceListShould.java` |
| Create | `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/RecentWorkspaceRecorderShould.java` |
| Create | `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/OpenGraphWorkspaceActionShould.java` |
| Modify | `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java` |
| Modify | `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceControllerShould.java` |
| Modify | `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphPluginIntegrationShould.java` |

No other file is touched. `GraphWorkspaceUiEvidence.java` is deliberately **not** modified; it
keeps working because the 9-argument `GraphWorkspaceWindowModel` constructor survives verbatim
(section 5.1).

---

## 2. `RecentWorkspaceList`

Package `org.freeplane.plugin.graph.workspace`; file
`freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/RecentWorkspaceList.java`.

### 2.1 Exact public API

```java
public final class RecentWorkspaceList {
    public static final String PROPERTY_KEY = "graph_workspace_recent_workspaces";
    public static final int STORED_CAPACITY = 25;
    public static final int DISPLAY_CAPACITY = 8;

    public static final class Entry {
        public Path path();
        public String label();
    }

    public static RecentWorkspaceList standard();
    public static RecentWorkspaceList empty();
    public RecentWorkspaceList(String storedValue, Consumer<String> persister);

    public void record(Path workspaceFile);
    public void clear();
    public List<Entry> displayEntries();
    public boolean hasStoredEntries();
    public Optional<Path> mostRecentExisting();

    static String labelFor(Path path);   // package-private label helper / test seam
}
```

No Swing type appears in the class. It is safe to call from the EDT and from background
threads.

### 2.2 Reference implementation

The implementation is fixed as follows; any deviation that changes observable behavior is a
defect.

```java
package org.freeplane.plugin.graph.workspace;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.freeplane.api.TextWritingDirection;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.ConfigurationUtils;

public final class RecentWorkspaceList {
    public static final String PROPERTY_KEY = "graph_workspace_recent_workspaces";
    public static final int STORED_CAPACITY = 25;
    public static final int DISPLAY_CAPACITY = 8;

    public static final class Entry {
        private final Path path;
        private final String label;

        private Entry(final Path path) {
            this(path, labelFor(path));
        }

        /** Test factory: lets tests build expected values and use the path-based equality. */
        static Entry of(final Path path, final String label) {
            return new Entry(path, label);
        }

        private Entry(final Path path, final String label) {
            this.path = Objects.requireNonNull(path, "path");
            this.label = Objects.requireNonNull(label, "label");
        }

        public Path path() {
            return path;
        }

        public String label() {
            return label;
        }

        @Override
        public boolean equals(final Object other) {
            return this == other || other instanceof Entry && path.equals(((Entry) other).path);
        }

        @Override
        public int hashCode() {
            return path.hashCode();
        }

        @Override
        public String toString() {
            return "Entry[" + path + "]";
        }
    }

    private final Object monitor = new Object();
    private final Object persisterMonitor = new Object();
    private final boolean mutable;
    private final Consumer<String> persister;
    private final WorkspaceUriResolver uriResolver = new WorkspaceUriResolver();
    private List<Path> entries;
    private long revision;
    private long persistedRevision = -1L;

    public static RecentWorkspaceList standard() {
        ResourceController resources = null;
        try {
            resources = ResourceController.getResourceController();
        }
        catch (final NullPointerException ignored) {
        }
        final String stored = resources == null ? "" : resources.getProperty(PROPERTY_KEY, "");
        return new RecentWorkspaceList(stored, value -> {
            ResourceController current = null;
            try {
                current = ResourceController.getResourceController();
            }
            catch (final NullPointerException ignored) {
            }
            if (current != null) {
                current.setProperty(PROPERTY_KEY, value);
            }
        });
    }

    public static RecentWorkspaceList empty() {
        return new RecentWorkspaceList(Collections.<Path>emptyList(), value -> { }, false);
    }

    public RecentWorkspaceList(final String storedValue, final Consumer<String> persister) {
        this(decode(storedValue), Objects.requireNonNull(persister, "persister"), true);
    }

    private RecentWorkspaceList(final List<Path> initialEntries, final Consumer<String> persister,
            final boolean mutable) {
        this.entries = Collections.unmodifiableList(new ArrayList<Path>(initialEntries));
        this.persister = persister;
        this.mutable = mutable;
    }

    public void record(final Path workspaceFile) {
        Objects.requireNonNull(workspaceFile, "workspaceFile");
        if (!mutable) {
            return;
        }
        final Path canonical;
        try {
            canonical = uriResolver.canonical(workspaceFile);
        }
        catch (RuntimeException failure) {
            return;
        }
        final long snapshotRevision;
        final String encoded;
        synchronized (monitor) {
            final String canonicalText = canonical.toString();
            final List<Path> updated = new ArrayList<Path>(STORED_CAPACITY);
            updated.add(canonical);
            for (final Path existing : entries) {
                if (updated.size() == STORED_CAPACITY) {
                    break;
                }
                if (!existing.toString().equals(canonicalText)) {
                    updated.add(existing);
                }
            }
            entries = Collections.unmodifiableList(updated);
            revision++;
            snapshotRevision = revision;
            encoded = ConfigurationUtils.encodeListValue(texts(entries), true);
        }
        persist(snapshotRevision, encoded);
    }

    public void clear() {
        if (!mutable) {
            return;
        }
        final long snapshotRevision;
        synchronized (monitor) {
            entries = Collections.emptyList();
            revision++;
            snapshotRevision = revision;
        }
        persist(snapshotRevision, ConfigurationUtils.encodeListValue(Collections.<String>emptyList(), true));
    }

    public List<Entry> displayEntries() {
        synchronized (monitor) {
            final List<Entry> result = new ArrayList<Entry>(DISPLAY_CAPACITY);
            for (final Path path : entries) {
                if (result.size() == DISPLAY_CAPACITY) {
                    break;
                }
                if (isRegularFile(path)) {
                    result.add(new Entry(path));
                }
            }
            return Collections.unmodifiableList(result);
        }
    }

    public boolean hasStoredEntries() {
        synchronized (monitor) {
            return !entries.isEmpty();
        }
    }

    public Optional<Path> mostRecentExisting() {
        synchronized (monitor) {
            for (final Path path : entries) {
                if (isRegularFile(path)) {
                    return Optional.of(path);
                }
            }
            return Optional.empty();
        }
    }

    static String labelFor(final Path path) {
        Objects.requireNonNull(path, "path");
        final Path fileName = path.getFileName();
        final Path parent = path.getParent();
        final String raw;
        if (fileName == null) {
            raw = path.toString();
        }
        else if (parent == null) {
            raw = fileName.toString();
        }
        else {
            raw = fileName + " (" + parent + ")";
        }
        return TextWritingDirection.LEFT_TO_RIGHT.isolatePathSeparators(raw);
    }

    private static List<Path> decode(final String storedValue) {
        if (storedValue == null || storedValue.isEmpty()) {
            return Collections.emptyList();
        }
        final List<String> tokens;
        try {
            tokens = ConfigurationUtils.decodeListValue(storedValue, true);
        }
        catch (RuntimeException failure) {
            return Collections.emptyList();
        }
        final List<Path> decoded = new ArrayList<Path>(Math.min(tokens.size(), STORED_CAPACITY));
        for (final String token : tokens) {
            if (decoded.size() == STORED_CAPACITY) {
                break;
            }
            try {
                final Path candidate = Paths.get(token);
                if (candidate.isAbsolute()) {
                    decoded.add(candidate);
                }
            }
            catch (RuntimeException invalid) {
                // The token can never become a valid workspace path; drop it.
            }
        }
        return decoded;
    }

    private void persist(final long snapshotRevision, final String value) {
        synchronized (persisterMonitor) {
            if (snapshotRevision <= persistedRevision) {
                return;
            }
            persistedRevision = snapshotRevision;
            try {
                persister.accept(value);
            }
            catch (RuntimeException ignored) {
                // Recording is best effort.
            }
        }
    }

    private static boolean isRegularFile(final Path path) {
        try {
            return Files.isRegularFile(path);
        }
        catch (RuntimeException failure) {
            return false;
        }
    }

    private static List<String> texts(final List<Path> paths) {
        final List<String> result = new ArrayList<String>(paths.size());
        for (final Path path : paths) {
            result.add(path.toString());
        }
        return result;
    }
}
```

### 2.3 Value type and label contract

- `Entry` is an immutable value type holding a non-null `Path`. Its `label()` is computed once
  at construction through `labelFor`. Two `Entry` values are equal exactly when their paths are
  equal; `equals`/`hashCode` are defined on `path` so tests can assert `displayEntries()` lists
  directly through the package-private `Entry.of(Path, String)` test factory.
- `labelFor(Path)`:
  - `getFileName() == null` (filesystem root) → the label is `path.toString()`.
  - `getParent() == null` and a non-null file name → the label is the file name.
  - otherwise → `fileName + " (" + path.getParent() + ")"`, i.e. the full absolute containing
    folder, never just the immediate segment.
  - The result is always passed through
    `TextWritingDirection.LEFT_TO_RIGHT.isolatePathSeparators`, matching core's recent-map menu
    label policy (`LastOpenedList.createOpenMapItemName`). This is why
    `/p/a/x.fpg` and `/q/a/x.fpg` receive different labels while sharing a file name.
- `labelFor` is package-private static so `RecentWorkspaceListShould` can assert formatting
  without a window.

### 2.4 Persistence contract

- Key: `PROPERTY_KEY` = `graph_workspace_recent_workspaces`, read with
  `ResourceController.getProperty(PROPERTY_KEY, "")` and written with
  `ResourceController.setProperty(PROPERTY_KEY, value)` as a user property.
- Encoding: `ConfigurationUtils.encodeListValue(pathsAsStrings, true)`; entries are written in
  newest-first order, each the canonical absolute `Path.toString()`, joined by the platform path
  separator doubled (`File.pathSeparator + File.pathSeparator`). Windows drive letters are safe
  because the delimiter is doubled.
- Decoding: `ConfigurationUtils.decodeListValue(storedValue, true)`.
- **Decoding never canonicalizes.** Stored absolute strings are kept verbatim. They are only
  validated with `Paths.get` (a token that throws, e.g. an embedded NUL, is dropped) and an
  absoluteness check (relative tokens are dropped). Missing files are **not** dropped; they are
  filtered only from display. This preserves hide-but-keep for network shares and removable
  drives.
- **Decode-time truncation.** After filtering, at most the newest `STORED_CAPACITY` (25) entries
  are kept, so a hand-edited or legacy property value cannot defeat the bounded-probe rule.
- **Canonicalization happens only in `record`**, through the same `WorkspaceUriResolver` that
  `DefaultGraphWorkspaceController.open` uses, wrapped so a failure (unreachable parent, symlink
  loop, ACL change) skips the entry instead of throwing.
- `clear()` writes the empty string (`encodeListValue(emptyList, true)`).
- Inherited core limitation (accepted, documented): because the doubled separator is the
  delimiter and the decoder trims whitespace around it, a path containing two consecutive
  platform separators or a trailing space directly before a separator cannot round-trip. Such
  names are legal on POSIX and Windows; the limitation is core's, not new.
- `standard()` tolerates a `null` result from `ResourceController.getProperty` and a `null`
  `ResourceController`; `GraphPluginIntegrationShould` installs the extension against a mocked
  `ApplicationResourceController` whose `getProperty` returns `null`, and
  `ConfigurationUtils.decodeListValue` dereferences its argument. The constructor maps `null`
  and `""` to an empty list.

### 2.5 Mutation semantics

- `record(Path)`:
  1. `Objects.requireNonNull(workspaceFile, "workspaceFile")` is a programming-error
     precondition, outside the best-effort guarantee.
  2. No-op when the instance is an `empty()` instance (`mutable == false`).
  3. Canonicalize; on `RuntimeException`, return without mutation (no persister call).
  4. Under the internal monitor, de-duplicate by canonical `path.toString()` and promote the
     canonical path to the head, then truncate to `STORED_CAPACITY` from the head (the oldest
     entry is evicted).
  5. Snapshot `(revision, encodedValue)` under the monitor, release the monitor, then persist
     outside it.
- `clear()`: no-op on `empty()` instances; otherwise clears all stored entries, snapshots a new
  revision, and persists `""` outside the monitor. It writes even when the list was already
  empty.
- `displayEntries()`: walks stored entries newest-first, returns an `Entry` for every stored
  path that is currently a regular file, stops at `DISPLAY_CAPACITY` (8). Hidden entries consume
  no display slots.
- `hasStoredEntries()`: true when at least one entry is stored, displayable or not.
- `mostRecentExisting()`: newest stored path that is currently a regular file, or empty.
- `empty()`: a fresh inert instance on every call. Its `record` and `clear` are complete no-ops
  (neither mutate nor persist), `hasStoredEntries()` is false, and display/resolution return
  empty. Freshness protects the test-only constructor overloads that default to `empty()` from
  sharing state. `standard()` and the `(String, Consumer<String>)` constructor produce mutable
  instances.
- `record(null)` and `labelFor(null)` throw `NullPointerException`; no other input can make
  `record`, `clear`, `displayEntries`, `hasStoredEntries`, or `mostRecentExisting` throw.

### 2.6 Thread safety and the monotonic-revision persister guard

- All state (`entries`, `revision`) is guarded by a private `monitor`. `record` and `clear`
  snapshot under that monitor and call the persister outside it, so a menu rebuild can never
  block behind a persister that synchronously notifies every `ResourceController` property
  listener.
- Persistence is serialized by a separate `persisterMonitor`. A snapshot whose revision is not
  greater than the last revision already submitted is discarded. This makes out-of-order
  persistence impossible when two mutations race; without it, an older snapshot could overwrite
  a newer one and lose the newest entry. A persister failure does not throw: the attempt still
  counts as the latest revision, and the next newer revision is attempted normally.

### 2.7 Bounded I/O

- One `displayEntries()` call and one `mostRecentExisting()` call each perform at most one
  `Files.isRegularFile` probe per stored entry, i.e. at most `STORED_CAPACITY` (25) probes, since
  both decode and `record` cap the stored list at 25.
- Every probe is wrapped and never throws; `IOException` and `SecurityException` become `false`.
- No other I/O runs on the EDT during a popup rebuild or a resolution.

---

## 3. Recent-open contract: `GraphWorkspaceController.openExisting(Path)`

### 3.1 Interface

`GraphWorkspaceController` gains one method; the existing method is unchanged:

```java
public interface GraphWorkspaceController {
    GraphWorkspaceHandle open(Path workspaceFile);
    GraphWorkspaceHandle openExisting(Path workspaceFile);
}
```

### 3.2 `DefaultGraphWorkspaceController` implementation

Imports to add to `DefaultGraphWorkspaceController.java`: `java.nio.file.NoSuchFileException`,
`org.freeplane.plugin.graph.workspace.RecentWorkspaceList`,
`org.freeplane.plugin.graph.workspace.RecentWorkspaceRecorder`.

The current `open(Path)` body is moved verbatim into a private
`open(Path workspaceFile, boolean createMissing)` overload, with exactly one change: the
create decision becomes `create = createMissing && !Files.exists(path);`. The public
`open(Path)` delegates with `createMissing = true`, so its create-or-open semantics are
unchanged. The new public `openExisting(Path)` canonicalizes, requires a regular file, and then
calls the private overload with `createMissing = false`:

```java
@Override
public GraphWorkspaceHandle open(final Path workspaceFile) {
    return open(workspaceFile, true);
}

@Override
public GraphWorkspaceHandle openExisting(final Path workspaceFile) {
    Objects.requireNonNull(workspaceFile, "workspaceFile");
    final Path path;
    try {
        path = uriResolver.canonical(workspaceFile);
    }
    catch (RuntimeException failure) {
        throw new GraphWorkspaceOpenException(workspaceFile, failure);
    }
    try {
        if (!Files.isRegularFile(path)) {
            throw new GraphWorkspaceOpenException(path, new NoSuchFileException(path.toString()));
        }
        return open(path, false);
    }
    catch (GraphWorkspaceOpenException failure) {
        throw failure;
    }
    catch (RuntimeException failure) {
        throw new GraphWorkspaceOpenException(path, failure);
    }
}
```

`NoSuchFileException` is `java.nio.file.NoSuchFileException`. Exact call sequence:

1. Reject `null` with `NullPointerException` (programming error; the recents entry points never
   pass `null`).
2. Canonicalize through `WorkspaceUriResolver.canonical`. On failure wrap the original input
   path into `GraphWorkspaceOpenException`.
3. Require `Files.isRegularFile(canonical)`. A missing path, a directory, and a vanished file
   produce `GraphWorkspaceOpenException` whose cause is `NoSuchFileException`; a
   `SecurityException` produces `GraphWorkspaceOpenException(path, securityException)` with the
   `SecurityException` itself as the cause, so the two cases stay distinguishable.
4. Call `open(canonical, false)`. A `GraphWorkspaceOpenException` is rethrown unchanged; any
   other `RuntimeException` — including the `IllegalStateException` of a shut-down controller,
   a second canonicalization failure, and registry inconsistencies — is wrapped into
   `GraphWorkspaceOpenException` with the canonical path.

### 3.3 Differences from `open(Path)`

| Aspect | `open(Path)` | `openExisting(Path)` |
| --- | --- | --- |
| Missing file | Creates a new workspace via the store's create branch | Never creates; fails with `GraphWorkspaceOpenException(NoSuchFileException)` |
| Directory | Falls through to the store open path and fails later | Rejected up front by the regular-file check |
| Canonicalization | Throws `IllegalArgumentException` on failure | Wraps into `GraphWorkspaceOpenException` |
| Shut-down controller | Throws `IllegalStateException` | Wraps into `GraphWorkspaceOpenException` |
| Already-open workspace | Focuses and returns the existing handle | Focuses and returns the existing handle (same shared code) |
| Intended caller | File chooser (create-or-open) | Recents submenu and `View > Open Graph Workspace` |
| Recording | `finishOpen` records; focus return records | Same, through the shared code |

The structural no-create guarantee comes from `createMissing = false`: even if the file
disappears after the regular-file check, `create` stays `false`, the session factory is called
with `create = false`, and the production store's open path fails instead of creating.

### 3.4 Forwarding controller

`GraphModeExtension.ForwardingGraphWorkspaceController` implements `openExisting` and converts
the unbound-controller state to the same exception type:

```java
@Override
public org.freeplane.plugin.graph.control.GraphWorkspaceHandle openExisting(final java.nio.file.Path path) {
    if (delegate == null) {
        throw new org.freeplane.plugin.graph.control.GraphWorkspaceOpenException(path,
            new IllegalStateException("Graph workspace controller is not initialized"));
    }
    return delegate.openExisting(path);
}
```

`open(Path)` keeps its existing `IllegalStateException("Graph workspace controller is not
initialized")` behavior.

---

## 4. Recording

### 4.1 `RecentWorkspaceRecorder`

Package `org.freeplane.plugin.graph.workspace`; file
`freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/RecentWorkspaceRecorder.java`.
The class is `public` because the `control` package constructs it.

```java
package org.freeplane.plugin.graph.workspace;

import java.util.Objects;

public final class RecentWorkspaceRecorder implements WorkspaceStoreListener, AutoCloseable {
    private final RecentWorkspaceList recentWorkspaces;
    private final ListenerRegistration registration;

    public RecentWorkspaceRecorder(final GraphWorkspaceStore store, final RecentWorkspaceList recentWorkspaces) {
        this.recentWorkspaces = Objects.requireNonNull(recentWorkspaces, "recentWorkspaces");
        this.registration = Objects.requireNonNull(store, "store").addListener(this);
    }

    @Override
    public void onWorkspaceStoreEvent(final WorkspaceStoreEvent event) {
        if (event.type() == WorkspaceStoreEvent.Type.IDENTITY_CHANGED) {
            event.identityChange().ifPresent(change -> recentWorkspaces.record(change.newPath()));
        }
    }

    @Override
    public void close() {
        registration.close();
    }
}
```

- The constructor self-registers through `GraphWorkspaceStore.addListener(this)` and keeps the
  returned `ListenerRegistration`; this is the object shape that makes Save As recording directly
  testable, because the production session factory is private and cannot be fired through an
  injected `SessionFactory`.
- `onWorkspaceStoreEvent` records `change.newPath()` only for `IDENTITY_CHANGED`; every other
  event type (`DOCUMENT_CHANGED`, `SAVED`, `SAVE_FAILED`) is ignored. It never throws: `record`
  is non-throwing and the identity change is read through the optional accessor.
- `close()` releases the store registration and is idempotent; `GraphWorkspaceStore`'s
  registration removal is a set removal.
- Save As ordering: `GraphWorkspaceStore.saveAs` publishes `IDENTITY_CHANGED` and then `SAVED`;
  the recorder's `record(newPath)` promotes the new file, and the previous path stays in the list
  because it still exists. `GraphWorkspaceStore.drainEvents` already swallows listener
  exceptions.

### 4.2 `SessionResources` and `ResourceSet` plumbing

- `SessionResources` gains a `final RecentWorkspaceRecorder recorder;` field.
- The existing 6-, 7-, and 8-argument constructors remain and delegate to the new 9-argument
  canonical constructor with `recorder = null`; several existing tests construct
  `SessionResources` directly, so these overloads must survive source-compatibly. The canonical
  constructor is:

```java
SessionResources(final GraphWorkspaceStore store, final WorkspaceMapCoordinator maps,
        final GraphUpdateCoordinator updates, final MapLeaseManager leaseManager,
        final GraphCommandRouter router, final ScheduledExecutorService scheduler,
        final boolean newlyCreated, final WorkspaceCreationOwnership creationOwnership,
        final RecentWorkspaceRecorder recorder) {
    this.store = Objects.requireNonNull(store, "store");
    this.maps = maps;
    this.updates = Objects.requireNonNull(updates, "updates");
    this.leaseManager = Objects.requireNonNull(leaseManager, "leaseManager");
    this.router = Objects.requireNonNull(router, "router");
    this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    this.newlyCreated = newlyCreated;
    this.creationOwnership = creationOwnership;
    this.recorder = recorder;
}
```

- `ResourceSet` gains a `final RecentWorkspaceRecorder recorder;` field and this constructor:
  ```java
  private ResourceSet(final GraphWorkspaceStore store, final WorkspaceMapCoordinator maps,
          final GraphUpdateCoordinator updates, final MapLeaseManager leaseManager,
          final ScheduledExecutorService scheduler, final boolean newlyCreated,
          final WorkspaceCreationOwnership creationOwnership,
          final RecentWorkspaceRecorder recorder) {
      this.store = store;
      this.maps = maps;
      this.updates = updates;
      this.leaseManager = leaseManager;
      this.scheduler = scheduler;
      this.newlyCreated = newlyCreated;
      this.creationOwnership = creationOwnership;
      this.recorder = recorder;
  }
  ```
  Both `ResourceSet.from(SessionResources)` and `ResourceSet.from(SessionResources, Path)` copy
  `resources.recorder`.
- `closeRemainingResources(ResourceSet)` first closes the recorder and records any failure, then
  closes updates/maps/lease manager/scheduler as today. `cleanupResources(ResourceSet)` already
  calls `store.discardAndClose()` first; closing the recorder afterwards is a no-op against the
  cleared listener list. Reached paths: session close (`closeSession` → `finishClose`),
  shutdown (`shutdownSession` → `closeRemainingResourcesOffEdt`), asynchronous close
  (`closeRemainingResourcesAsync`), and rollback (`cleanupResources`).
- Save failure does not close the recorder: `closeSession` first calls `store.close()`; when it
  throws, the session is reopened and `closeRemainingResources` is not reached. On retry
  success, the store clears its listener list and `closeRemainingResources` closes the recorder.

### 4.3 `ProductionSessionFactory`

`ProductionSessionFactory` gains a `RecentWorkspaceList recentWorkspaces` field and constructor
parameter. In `open(...)`, after the store is created/opened and the creation ownership is
captured, it creates the recorder immediately:

```java
store = create ? GraphWorkspaceStore.create(path, codec, writer, scheduler)
    : GraphWorkspaceStore.open(path, codec, writer, scheduler);
if (create) {
    creationOwnership = WorkspaceCreationOwnership.capture(path);
}
final RecentWorkspaceRecorder recorder = new RecentWorkspaceRecorder(store, recentWorkspaces);
```

It passes the recorder to the 9-argument `SessionResources`. Its construction-failure catch path
builds the `ResourceSet` **without** a recorder:
```java
new ResourceSet(store, maps, updates, leaseManager, scheduler, create, creationOwnership, null)
```
That is safe because `cleanupResources` calls `store.discardAndClose()` first and that clears the store's listener list.

### 4.4 Controller recording points

`DefaultGraphWorkspaceController` gains a `private final RecentWorkspaceList recentWorkspaces;`
field (non-null) and a private best-effort helper:

```java
private void recordRecentWorkspace(final Path path) {
    try {
        recentWorkspaces.record(path);
    }
    catch (RuntimeException ignored) {
        // Recording is best effort and must never roll back a successful open.
    }
}
```

Recording points:

1. **Open success.** In `finishOpen`, immediately after `session.publishOpen();` and before
   `return handle;`, call `recordRecentWorkspace(path);`. The path is already canonical here.
   This covers chooser opens (including a nonexistent path chosen in the chooser that creates a
   workspace), recent-entry opens, and the main-window action.
2. **Focus of an already-open workspace.** In the private `open(Path, boolean)` loop, in the
   `existing.awaitOpen()` branch, directly before `return existing.handle;` after a successful
   `existing.focus()`, call `recordRecentWorkspace(path);`. This covers re-opening a workspace
   that is already open and re-records it after `clear()`.

Save As needs no controller call because the per-session recorder handles `IDENTITY_CHANGED`.
Both controller call sites are wrapped so an unexpected recording failure cannot reach
`finishOpen`'s catch block, which rolls a successfully opened session back.

### 4.5 Constructor wiring

```java
public DefaultGraphWorkspaceController(final ModeController modeController,
        final GraphWorkspaceViewFactory viewFactory, final RecentWorkspaceList recentWorkspaces) {
    this(new WorkspaceSessionRegistry(), modeController, viewFactory, recentWorkspaces);
}

public DefaultGraphWorkspaceController(final ModeController modeController,
        final GraphWorkspaceViewFactory viewFactory) {
    this(modeController, viewFactory, RecentWorkspaceList.empty());
}

private DefaultGraphWorkspaceController(final WorkspaceSessionRegistry sessions,
        final ModeController modeController, final GraphWorkspaceViewFactory viewFactory,
        final RecentWorkspaceList recentWorkspaces) {
    this(sessions, new ProductionSessionFactory(modeController, sessions, recentWorkspaces), viewFactory,
        recentWorkspaces);
}

DefaultGraphWorkspaceController(final WorkspaceSessionRegistry sessions, final SessionFactory sessionFactory,
        final GraphWorkspaceViewFactory viewFactory, final RecentWorkspaceList recentWorkspaces) {
    this.sessions = Objects.requireNonNull(sessions, "sessions");
    this.sessionFactory = Objects.requireNonNull(sessionFactory, "sessionFactory");
    this.viewFactory = Objects.requireNonNull(viewFactory, "viewFactory");
    this.recentWorkspaces = Objects.requireNonNull(recentWorkspaces, "recentWorkspaces");
}

DefaultGraphWorkspaceController(final WorkspaceSessionRegistry sessions, final SessionFactory sessionFactory,
        final GraphWorkspaceViewFactory viewFactory) {
    this(sessions, sessionFactory, viewFactory, RecentWorkspaceList.empty());
}
```

The existing 3-argument package-private test constructor survives and defaults to
`RecentWorkspaceList.empty()`, so no existing test starts touching user properties.

---

## 5. UI: `File > Recent Workspaces`

### 5.1 Window/model constructor overload set

All constructors below are package-private in
`org.freeplane.plugin.graph.window.GraphWorkspaceWindow.java`.

`GraphWorkspaceWindowModel`:

| Arity | Signature | Behavior |
| --- | --- | --- |
| 5 | `(GraphWorkspaceHandle, GraphWorkspaceViewBinding, GraphWorkspaceController, Supplier<Path>, Runnable)` | unchanged; delegates to the 9-argument form |
| 6 | 5 + `Runnable graphFocus` | unchanged; delegates to the 9-argument form |
| 7 | `(handle, binding, controller, pathChooser, WorkspaceCloseController, Runnable closeRequest, Runnable graphFocus)` | unchanged; delegates to the 9-argument form |
| 8 | 7 + `Runnable closeCompletion` | unchanged; delegates to the 9-argument form |
| 9 | `(handle, binding, controller, pathChooser, closeController, closeRequest, graphFocus, closeCompletion, Consumer<String> commandMessageSink)` | **survives verbatim**; body becomes `this(..., commandMessageSink, RecentWorkspaceList.empty());` |
| 10 | 9 + `RecentWorkspaceList recentWorkspaces` | canonical; stores all fields, including a new `private final GraphWorkspaceController applicationController;` and `private final RecentWorkspaceList recentWorkspaces;` |

The 9-argument signature exists specifically because
`GraphWorkspaceUiEvidence.ModelAccess.create` reflectively requires
`(GraphWorkspaceHandle, GraphWorkspaceViewBinding, GraphWorkspaceController, Supplier, WorkspaceCloseController,
Runnable, Runnable, Runnable, Consumer)`; it must not be removed, renamed, or reordered.

The model gains exactly three fields: `private final GraphWorkspaceController applicationController;`
(stored by the canonical constructor; `openRecentWorkspace` needs it),
`private final RecentWorkspaceList recentWorkspaces;` (assigned from the canonical constructor;
the 9-argument form passes `RecentWorkspaceList.empty()`) and `private JMenu recentWorkspacesMenu;`
(assigned in `createMenuBar()`, used by the rebuild method and by the click handler).

`GraphWorkspaceWindow`:

| Arity | Signature | Behavior |
| --- | --- | --- |
| 4 | `(handle, binding, closeController, applicationController)` | unchanged; delegates to the 5-argument form with the default chooser |
| 5 | 4 + `Supplier<Path> pathChooser` | unchanged; delegates to the 6-argument form with `RecentWorkspaceList.empty()` |
| 6 | 5 + `RecentWorkspaceList recentWorkspaces` | canonical; passes the list as the tenth model argument |

`HeadlessGraphWorkspaceView` gets the analogous 6-argument canonical constructor
`(handle, binding, closeController, applicationController, pathChooser, recentWorkspaces)` whose
5-argument form delegates with `RecentWorkspaceList.empty()`, and constructs the model with the
10-argument constructor using a no-op message sink.

`SwingGraphWorkspaceViewFactory`:

| Arity | Signature | Behavior |
| --- | --- | --- |
| 1 | `public (GraphWorkspaceController)` | unchanged; delegates with `GraphWorkspaceWindow::chooseWorkspacePath` and `RecentWorkspaceList.empty()` |
| 2 | `public (GraphWorkspaceController, RecentWorkspaceList)` | production; delegates with the default chooser |
| 2 | package-private `(GraphWorkspaceController, Supplier<Path>)` | unchanged; delegates with `RecentWorkspaceList.empty()` |
| 3 | package-private `(GraphWorkspaceController, Supplier<Path>, RecentWorkspaceList)` | canonical; `create(...)` passes the list to both `GraphWorkspaceWindow` and `HeadlessGraphWorkspaceView` |

### 5.2 Menu construction

Imports to add to `GraphWorkspaceWindow.java`: `javax.swing.event.PopupMenuEvent`,
`javax.swing.event.PopupMenuListener`, `org.freeplane.plugin.graph.workspace.RecentWorkspaceList`.

In `GraphWorkspaceWindowModel.createMenuBar()`, insert the submenu directly after
`fileSaveAsMenuItem` and before the existing separator and Close item:

```java
file.add(fileSaveAsMenuItem);
recentWorkspacesMenu = new JMenu(TextUtils.getText("graph_workspace.menu.recent_workspaces"));
recentWorkspacesMenu.setName("graph-workspace-recent-workspaces-menu");
recentWorkspacesMenu.getPopupMenu().addPopupMenuListener(new PopupMenuListener() {
    @Override
    public void popupMenuWillBecomeVisible(final PopupMenuEvent event) {
        rebuildRecentWorkspacesMenu();
    }

    @Override
    public void popupMenuWillBecomeInvisible(final PopupMenuEvent event) {
    }

    @Override
    public void popupMenuCanceled(final PopupMenuEvent event) {
    }
});
file.add(recentWorkspacesMenu);
file.addSeparator();
file.add(item("graph_workspace.action.close", "close", event -> closeRequest.run()));
```

Required names and keys:

- `JMenu` component name: `graph-workspace-recent-workspaces-menu`; text
  `graph_workspace.menu.recent_workspaces`.
- Entry items: text is `Entry.label()`; component name is
  `"graph-workspace-recent-workspace-" + index` (0-based display order).
- Empty row: text `graph_workspace.recent_workspaces.empty`; component name
  `graph-workspace-recent-workspaces-empty`; disabled (`setEnabled(false)`).
- Clear item: text `graph_workspace.action.clear_recent_workspaces`; component name
  `graph-workspace-recent-workspaces-clear`; enabled.

### 5.3 Package-private rebuild method

```java
void rebuildRecentWorkspacesMenu() {
    recentWorkspacesMenu.removeAll();
    final List<RecentWorkspaceList.Entry> entries = recentWorkspaces.displayEntries();
    if (entries.isEmpty()) {
        final JMenuItem empty = new JMenuItem(TextUtils.getText("graph_workspace.recent_workspaces.empty"));
        empty.setName("graph-workspace-recent-workspaces-empty");
        empty.setEnabled(false);
        recentWorkspacesMenu.add(empty);
        if (recentWorkspaces.hasStoredEntries()) {
            recentWorkspacesMenu.addSeparator();
            recentWorkspacesMenu.add(clearRecentWorkspacesItem());
        }
        recentWorkspacesMenu.setEnabled(true);
        return;
    }
    for (int index = 0; index < entries.size(); index++) {
        final RecentWorkspaceList.Entry entry = entries.get(index);
        final JMenuItem item = new JMenuItem(entry.label());
        item.setName("graph-workspace-recent-workspace-" + index);
        item.addActionListener(event -> openRecentWorkspace(entry.path()));
        recentWorkspacesMenu.add(item);
    }
    recentWorkspacesMenu.addSeparator();
    recentWorkspacesMenu.add(clearRecentWorkspacesItem());
    recentWorkspacesMenu.setEnabled(true);
}

private JMenuItem clearRecentWorkspacesItem() {
    final JMenuItem item = new JMenuItem(TextUtils.getText("graph_workspace.action.clear_recent_workspaces"));
    item.setName("graph-workspace-recent-workspaces-clear");
    item.addActionListener(event -> {
        recentWorkspaces.clear();
        rebuildRecentWorkspacesMenu();
    });
    return item;
}
```

Construction rules (normative):

- **Populated:** one enabled item per `displayEntries()` entry in order, then a separator, then
  the enabled Clear item.
- **Empty, nothing stored:** one disabled `No recent workspaces` item and nothing else.
- **Empty, stored entries hidden:** one disabled `No recent workspaces` item, then a separator,
  then the enabled Clear item, so hidden entries can still be purged.
- **The submenu is never disabled.** `rebuildRecentWorkspacesMenu` explicitly sets it enabled, and
  no code path disables it. A disabled `JMenu` could never reopen until the window is recreated.
- `/`-separated menu structure is searched by test helpers; the rebuild is the only place menu
  contents change.

### 5.4 Click handling

```java
private void openRecentWorkspace(final Path path) {
    try {
        applicationController.openExisting(path);
    }
    catch (RuntimeException failure) {
        commandMessageSink.accept(TextUtils.format("graph_workspace.recent_workspaces.open_failed", path));
        rebuildRecentWorkspacesMenu();
    }
}
```

- Entries always call `applicationController.openExisting(path)`, never `open(path)`, so no
  workspace can be created even when the file disappears between building the menu and clicking.
  The pre-click existence filter decides only what is shown; correctness does not depend on it.
- The catch is `RuntimeException` (the same UI-boundary policy as the action). A failed click
  reports the formatted message through the existing `commandMessageSink` and rebuilds the menu
  from storage so the popup reflects current existence. It never edits the stored list, so a
  temporarily unavailable file stays remembered.
- A click on a session that is momentarily `OPENING` or `CLOSING` surfaces the controller's
  "Workspace is still being opened or closed" `GraphWorkspaceOpenException` and is reported like
  any other failed open; the pending session presents the workspace moments later.

---

## 6. `OpenGraphWorkspaceAction`

File `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/OpenGraphWorkspaceAction.java`.

### 6.1 Constructor set

Imports to add to `OpenGraphWorkspaceAction.java`: `java.util.Optional`,
`java.util.function.Consumer`, `org.freeplane.core.util.TextUtils`,
`org.freeplane.features.mode.Controller`, `org.freeplane.plugin.graph.workspace.RecentWorkspaceList`.

```java
public OpenGraphWorkspaceAction(final GraphWorkspaceController applicationController) {
    this(applicationController, RecentWorkspaceList.empty());
}

public OpenGraphWorkspaceAction(final GraphWorkspaceController applicationController,
        final RecentWorkspaceList recentWorkspaces) {
    this(applicationController, recentWorkspaces, defaultMessageSink());
}

public OpenGraphWorkspaceAction(final GraphWorkspaceController applicationController,
        final RecentWorkspaceList recentWorkspaces, final Consumer<String> messageSink) {
    this(applicationController, recentWorkspaces, GraphWorkspaceWindow::chooseWorkspacePath, messageSink);
}

OpenGraphWorkspaceAction(final GraphWorkspaceController applicationController,
        final RecentWorkspaceList recentWorkspaces, final Supplier<Path> pathChooser,
        final Consumer<String> messageSink) {
    super(KEY);
    this.applicationController = Objects.requireNonNull(applicationController, "applicationController");
    this.recentWorkspaces = Objects.requireNonNull(recentWorkspaces, "recentWorkspaces");
    this.pathChooser = Objects.requireNonNull(pathChooser, "pathChooser");
    this.messageSink = Objects.requireNonNull(messageSink, "messageSink");
}

OpenGraphWorkspaceAction(final GraphWorkspaceController applicationController,
        final Supplier<Path> pathChooser) {
    this(applicationController, RecentWorkspaceList.empty(), pathChooser, defaultMessageSink());
}

private static Consumer<String> defaultMessageSink() {
    return message -> Controller.getCurrentController().getViewController().out(message);
}
```

- The 4-argument package-private constructor is the test seam: it is the only way to inject the
  list, the chooser, and the sink together.
- The existing package-private `(controller, Supplier<Path>)` constructor keeps its meaning:
  with `RecentWorkspaceList.empty()` it resolves nothing and always uses the chooser, so
  `GraphPluginIntegrationShould.opensOnlyPathsSelectedByTheInjectedWorkspaceChooser` stays green.
- The default sink is lazy: `defaultMessageSink()` creates a lambda, and
  `Controller.getCurrentController()` is evaluated only when a message is actually reported.
  Constructing the action — including at install time against a mocked `Controller` — never
  touches the controller.

### 6.2 Resolution and opening

`actionPerformed` is fixed to:

```java
@Override
public void actionPerformed(final ActionEvent event) {
    final Optional<Path> recent = recentWorkspaces.mostRecentExisting();
    if (recent.isPresent()) {
        try {
            applicationController.openExisting(recent.get());
            return;
        }
        catch (RuntimeException failure) {
            report("graph_workspace.recent_workspaces.open_failed", recent.get());
        }
    }
    final Path chosen = pathChooser.get();
    if (chosen != null) {
        applicationController.open(chosen);
    }
}

private void report(final String messageKey, final Object... arguments) {
    messageSink.accept(TextUtils.format(messageKey, arguments));
}
```

- `mostRecentExisting()` already skips missing files, so a missing newest entry falls through to
  the next existing one without a chooser.
- A recent open failure is reported with the stored path, then the chooser runs. The entry point
  is never a silent no-op; today no one catches `GraphWorkspaceOpenException`.
- The chooser branch is intentionally unchanged: it calls `open(chosen)` so a new file name
  chosen in the dialog still creates a workspace, and a chooser-selected file that fails to open
  keeps today's behavior (logged only).
- The UI boundary catches `RuntimeException` even though `openExisting` is specified to wrap
  everything, so the guarantee holds even if a controller implementation violates its contract.
  `IllegalArgumentException` and `IllegalStateException` from the recent open are reported and
  followed by the chooser exactly like `GraphWorkspaceOpenException`.
- `afterMapChange` stays an empty override.

---

## 7. Wiring in `GraphModeExtension`

Imports to add to `GraphModeExtension.java`: `org.freeplane.plugin.graph.workspace.RecentWorkspaceList`
(and `org.freeplane.plugin.graph.control.GraphWorkspaceOpenException`, already available by
fully-qualified name in the delegation snippet).

Inside `installExtension`, after obtaining `resourceController` and before creating the
controller, create exactly one application-wide list and inject it at every point:

```java
final RecentWorkspaceList recentWorkspaces = RecentWorkspaceList.standard();
final ForwardingGraphWorkspaceController viewController = new ForwardingGraphWorkspaceController();
final DefaultGraphWorkspaceController completedController = new DefaultGraphWorkspaceController(
    modeController, new SwingGraphWorkspaceViewFactory(viewController, recentWorkspaces), recentWorkspaces);
viewController.bind(completedController);
graphWorkspaceController = completedController;
openGraphWorkspaceAction = new OpenGraphWorkspaceAction(viewController, recentWorkspaces);
```

- One `RecentWorkspaceList.standard()` instance per `installExtension`; the controller, the view
  factory (which passes it to `GraphWorkspaceWindow`/`HeadlessGraphWorkspaceView`), and the
  action all receive the same object.
- Injection points: `DefaultGraphWorkspaceController(ModeController, GraphWorkspaceViewFactory,
  RecentWorkspaceList)`, `SwingGraphWorkspaceViewFactory(GraphWorkspaceController,
  RecentWorkspaceList)`, `OpenGraphWorkspaceAction(GraphWorkspaceController,
  RecentWorkspaceList)`.
- `ForwardingGraphWorkspaceController` gains `openExisting` delegation as specified in
  section 3.4. The `GraphWorkspaceController` interface change is the only interface change.
- The early-return `if (this.modeController == modeController) return;` and `close()` behavior are
  unchanged; a later install rebuilds a fresh list from current properties.

---

## 8. Resource Bundle Changes

File `freeplane/src/viewer/resources/translations/Resources_en.properties` (the file where every
other `graph_workspace.*` key lives) gains exactly these four lines, inserted in the file's
existing alphabetical order:

| Key | Value | Insert between |
| --- | --- | --- |
| `graph_workspace.action.clear_recent_workspaces` | `Clear Recent Workspaces` | `graph_workspace.action.cancel=Cancel` and `graph_workspace.action.close=Close` |
| `graph_workspace.menu.recent_workspaces` | `Recent Workspaces` | `graph_workspace.menu.maps=Maps` and `graph_workspace.menu.view=View` |
| `graph_workspace.recent_workspaces.empty` | `No recent workspaces` | `graph_workspace.purge.unavailable=...` and `graph_workspace.relationship.created=...` |
| `graph_workspace.recent_workspaces.open_failed` | `Could not open the recent workspace: {0}` | immediately after `graph_workspace.recent_workspaces.empty` |

- The file stays ISO-8859-1 with `\uXXXX` escapes; these four values are plain ASCII, so no
  escapes are needed.
- Only the English bundle carries `graph_workspace.*` keys; other locales fall back.
- After the edit run `gradle format_translation` from the repository root and keep the formatted
  result. If the formatter reorders other lines, keep that reformatting in the same change.
- One message key (`graph_workspace.recent_workspaces.open_failed`) covers both failure shapes:
  the file vanished between display and click, and the file exists but is corrupt or unreadable;
  both surface as `GraphWorkspaceOpenException` from `openExisting`.

---

## 9. Error Behavior and Edge Cases

Every item is an observable behavior and is pinned by the tests in section 10.

| # | Condition | Observable behavior |
| --- | --- | --- |
| EC1 | No stored entries, or every stored entry missing | The submenu rebuild shows exactly one disabled `No recent workspaces` row and no Clear item when nothing is stored (and Clear when entries are stored); the submenu stays enabled. `View > Open Graph Workspace` calls the chooser and never `openExisting`. Neither entry point throws. |
| EC2 | All stored entries missing but stored | The submenu shows the disabled empty row, a separator, and the enabled `Clear Recent Workspaces`; clicking Clear empties the stored list and persists `""`. |
| EC3 | Entry vanishes between menu build and click | The click calls `openExisting`, which throws `GraphWorkspaceOpenException(NoSuchFileException)`; the handler reports `graph_workspace.recent_workspaces.open_failed` with the path through `commandMessageSink` and rebuilds the menu; no workspace file is created and `open` is never called. |
| EC4 | Recent file exists but cannot be opened (corrupt XML, unreadable) | Submenu click reports the same formatted message and rebuilds; the entry stays stored and listed because it still exists. The action reports and then calls the chooser. |
| EC5 | Chooser-selected file fails to open | `open` behavior is unchanged; no recents-style report is added; existing logging only. |
| EC6 | Persister failure | `record`/`clear` swallow the failure; the open or Save As succeeds and the in-memory list keeps the recorded path. |
| EC7 | Record path is unusable (canonicalization throws) | `record` returns without mutating; no persister call; opens are unaffected. |
| EC8 | Workspace opened read-only | Still recorded; `finishOpen` records regardless of `WorkspaceCompatibility`. |
| EC9 | Flush timing | Unchanged: `setProperty` updates in-memory user properties; flush happens on shutdown and preferences close as for core's recent maps. A hard crash may lose the newest entry. |
| EC10 | Already-open workspace | `open`/`openExisting` focus the existing window and record the path again; a momentarily `OPENING`/`CLOSING` session surfaces `GraphWorkspaceOpenException` and is reported cosmetically; a path that is the in-flight Save As target of another workspace resolves to that workspace's window. |
| EC11 | Mocked or missing `ResourceController` | `standard()` tolerates both the mocked case (a null `getProperty` result) and the absent case (`getResourceController()` throwing `NullPointerException` because no `Controller` is installed, as in headless tests): it yields an empty list and a persister that becomes a no-op. Pinned by F1 and by the existing install-extension test. |

---

## 10. Test Plan

All new tests are deterministic, headless JUnit 4 tests using AssertJ and Mockito 5.18.0 (the
repository versions). No test shows a window; any assertion that requires a real window uses
`org.junit.Assume.assumeFalse(GraphicsEnvironment.isHeadless())` as the existing tests do.
EDT rules follow `GraphWorkspaceWindowModelShould`: models are constructed and menu rebuilds are
triggered inside `GraphWorkspaceWindow.runOnEdt(...)`, which rethrows `AssertionError` unchanged.

### 10.1 `RecentWorkspaceListShould` (new)

Path: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/RecentWorkspaceListShould.java`.

Shared fixture: `@Rule TemporaryFolder`, a capturing persister
`new RecentWorkspaceList(storedValue, value -> persisted.set(value))`, and `Files.createFile` /
`Files.delete` for real or missing entries. Canonical expectations come from `Path.toRealPath()`.

| # | Test | Seam / assertion | Reverted guarantee it catches |
| --- | --- | --- | --- |
| A1 | `ordersEntriesNewestFirst` | record three existing files; `displayEntries()` paths newest-first and labels equal `labelFor(path)` | ordering policy |
| A2 | `promotesARecordedPathInsteadOfDuplicatingIt` | record A, B, then A again; `displayEntries()` is exactly `[A, B]` and the persisted value decodes to two entries | de-dup/promotion on re-record |
| A3 | `evictsTheOldestEntryAtTheStoredCapacity` | record 26 existing files; the last captured value decodes to 25 paths with the first file absent | stored cap 25 / oldest eviction |
| A4 | `truncatesDisplayedEntriesAtTheDisplayCapacity` | record 10 existing files; `displayEntries().size() == 8` and starts newest | displayed cap 8 |
| A5 | `filtersMissingFilesFromDisplayButKeepsThemStored` | record A and M (both exist), delete M; `displayEntries()` is `[A]`, `hasStoredEntries()` true, a new list from the captured value still reports a stored entry; recreating M on disk makes a fresh list display both | hide-but-keep, no pruning |
| A6 | `skipsMissingEntriesWhenResolvingTheMostRecentExisting` | record existing B, then existing A, then delete A (A newest); `mostRecentExisting()` is B | newest-existing resolution |
| A7 | `clearsEveryStoredEntryAndPersistsTheEmptyString` | record an existing file, then `clear()`; `hasStoredEntries()` false, `displayEntries()` empty, captured value is exactly `""` | clear semantics |
| A8 | `roundTripsThePersistenceFormatThroughEncodeListValue` | record two files; captured value equals `ConfigurationUtils.encodeListValue([canonicalFirst, canonicalSecond], true)`; constructing from that value yields the same paths | encode/decode contract |
| A9 | `dropsInvalidStoredTokensAtConstruction` | construct from a value containing one absolute path, one relative token, and `"\u0000bad"`; only the absolute path is stored; `null` and `""` produce empty lists without throwing | null/blank/malformed tolerance |
| A10 | `truncatesDecodedValuesToTheStoredCapacity` | construct from a hand-built value of 26 absolute tokens, then record an existing file; the captured value holds 25 entries (the new path plus the first 24 decoded) and not the last two decoded tokens | decode-time truncation, bounded-probe precondition |
| A11 | `recordNeverThrowsOnAnUnusablePath` | `Assume.assumeTrue(File.separatorChar == '/')` (on Windows the JDK maps a non-directory prefix to `NoSuchFileException`, so the path resolves and no failure exists to exercise); path `blocker/child.fpg` where `blocker` is a regular file; `record` returns without throwing, list stays empty, persister never called | canonicalization failure is skipped |
| A12 | `recordAndClearNeverThrowWhenThePersisterFails` | persister throws `IllegalStateException`; `record(existingFile)` does not throw and `hasStoredEntries()` is true; `clear()` does not throw | best-effort persistence |
| A13 | `keepsUnresolvableStoredPathsAcrossAnUnrelatedRecord` | stored value contains an absolute path blocked by a regular-file parent (e.g. `blocker.fpg/child.fpg` where `blocker.fpg` is a regular file); construction, `displayEntries()`, and `mostRecentExisting()` do not throw and keep it stored; after recording an unrelated existing file the captured value still contains the unresolvable token | decode never canonicalizes, never prunes, and needs no symlink privilege |
| A14 | `recordsTheCanonicalPathAndNotTheGivenVariant` | record `dir/../dir/x.fpg`; the captured value contains `dir/x.fpg`'s real path and not the `..` string | record canonicalizes |
| A15 | `emptyReturnsFreshInertInstances` | `empty()` is not `isSameAs` another `empty()`; recording an existing path leaves `hasStoredEntries()` false and `displayEntries()` empty; `clear()` is a no-op | fresh instances, no cross-test leakage |
| A16 | `labelHelperUsesFileNameAndFullContainingFolder` | path `Paths.get("/a/b/x.fpg")`; `labelFor(path)` equals `"x.fpg (" + path.getParent() + ")"` (expected value derived from the same `Path`, so it also holds on Windows); labels for `/p/a/x.fpg` and `/q/a/x.fpg` differ | label policy / full folder |
| A17 | `labelHelperFallsBackToThePathStringForARoot` | `labelFor(temporaryFolder.getRoot().toPath().getRoot())` equals `TextWritingDirection.LEFT_TO_RIGHT.isolatePathSeparators(root.toString())` and does not throw | null-`getFileName` fallback |
| A18 | `standardToleratesAnAbsentResourceController` | `mockStatic(ResourceController.class)` with `getResourceController()` throwing `NullPointerException` (the absent-`Controller` case): `RecentWorkspaceList.standard()` returns a list with `hasStoredEntries()` false and `displayEntries()` empty, and `record(existingFile)`/`clear()` do not throw | EC11 tolerance without a live `Controller` |

### 10.2 `OpenGraphWorkspaceActionShould` (new)

Path: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/OpenGraphWorkspaceActionShould.java`.

Fixture: `@Rule TemporaryFolder`; a real `RecentWorkspaceList` with a capturing persister for
resolution tests; a Mockito mock of `GraphWorkspaceController`; a recording `Consumer<String>`
sink; a `Supplier<Path>` chooser that records invocation; static mock of `TextUtils` whose
`format(String, Object[])` returns `key + Arrays.toString(arguments)` (the same convention as
`GraphWorkspaceWindowModelShould.formattedText`). The action is built with the package-private
4-argument constructor.

| # | Test | Seam / assertion | Reverted guarantee it catches |
| --- | --- | --- | --- |
| B1 | `opensTheMostRecentExistingEntryWithOpenExisting` | list holds an existing file; `actionPerformed`; `verify(controller).openExisting(canonicalPath)`; `verify(controller, never()).open(any())`; chooser never invoked | recents use `openExisting` |
| B2 | `fallsThroughToTheNextExistingEntryWhenTheNewestIsMissing` | list has a missing newest file and an existing older file; the older canonical path is opened with `openExisting` | skip-missing resolution |
| B3 | `fallsBackToTheChooserWhenNoStoredEntryIsUsable` | empty list; chooser returns P; `verify(controller).open(P)` and `verify(controller, never()).openExisting(any())` | chooser fallback and create-or-open semantics |
| B4 | `reportsAndFallsBackWhenTheRecentOpenThrowsGraphWorkspaceOpenException` | `openExisting` throws; chooser returns P; sink is exactly `"graph_workspace.recent_workspaces.open_failed[<recentPath>]"`; `open(P)` called | report-then-chooser |
| B5 | `reportsAndFallsBackWhenTheRecentOpenThrowsIllegalArgumentException` | `openExisting` throws `IllegalArgumentException`; same sink message and chooser fallback | UI boundary catches `RuntimeException`, not just `GraphWorkspaceOpenException` |
| B6 | `reportsAndFallsBackWhenTheRecentOpenThrowsIllegalStateException` | `openExisting` throws `IllegalStateException`; same sink message and chooser fallback | same |
| B7 | `constructsWithTheLazyDefaultMessageSink` | `new OpenGraphWorkspaceAction(mock(GraphWorkspaceController.class))` and the `(controller, RecentWorkspaceList.empty())` overload construct without touching `Controller`; `getKey()` is `OpenGraphWorkspaceAction.KEY` | lazy default sink / short overload defaults |

### 10.3 `GraphWorkspaceWindowModelShould` (extend)

Add `@Rule public final TemporaryFolder temporaryFolder` and extend `Fixture` with a
`RecentWorkspaceList` field defaulting to `RecentWorkspaceList.empty()` plus a helper that builds
a fixture with an injected list; the model is constructed with the 10-argument constructor inside
`GraphWorkspaceWindow.runOnEdt`. Menu rebuilds are triggered by calling the package-private
`model.rebuildRecentWorkspacesMenu()` inside `GraphWorkspaceWindow.runOnEdt(...)`; readings and
assertions happen in the same runnable. Real lists are built with the
`(String, Consumer<String>)` constructor and `record()` of temp files.

| # | Test | Seam / assertion | Reverted guarantee it catches |
| --- | --- | --- | --- |
| C1 | `placesRecentWorkspacesMenuBetweenSaveAsAndClose` | File menu (`graph-workspace-file-menu`) components are, in order: Open, Save, Save As, `graph-workspace-recent-workspaces-menu`, a `JSeparator`, Close; the menu text key is `graph_workspace.menu.recent_workspaces` | exact File menu placement |
| C2 | `rebuildsRecentEntriesInOrderWithLabels` | two existing files; after rebuild the menu has two items named `graph-workspace-recent-workspace-0/1` whose texts are the `Entry.label()` values in order, then a separator and the clear item; the menu `isEnabled()` is true | item construction, ordering, labels, enabled submenu |
| C3 | `limitsRebuiltEntriesToTheDisplayCap` | ten existing files; rebuild yields exactly 8 entry items | displayed cap 8 in the model |
| C4 | `hidesMissingEntriesFromTheRebuiltMenu` | one existing and one recorded-then-deleted file; rebuild lists only the existing file | hide-but-keep in the menu |
| C5 | `showsDisabledEmptyRowWithoutClearWhenNothingIsStored` | `empty()` list; pre-disable the menu (`recentWorkspacesMenu.setEnabled(false)`) before rebuild; rebuild yields exactly one disabled item named `graph-workspace-recent-workspaces-empty` with text key `graph_workspace.recent_workspaces.empty`, no separator, no clear item, and the menu is enabled again | empty row, no Clear, explicit re-enable |
| C6 | `keepsClearAndTheMenuEnabledWhenOnlyHiddenEntriesAreStored` | list stores a recorded-then-deleted file; pre-disable the menu before rebuild; rebuild yields the disabled empty row, then a separator, then the enabled clear item, and the menu is enabled again | Clear reachable for hidden entries, explicit re-enable |
| C7 | `clearsTheListAndRebuildsWhenClearIsClicked` | click `graph-workspace-recent-workspaces-clear`; list `hasStoredEntries()` false; the rebuilt menu has the empty row and no clear item | Clear semantics and rebuild |
| C8 | `reportsAFailedOpenFromTheMenuAndNeverCreatesAWorkspace` | list with an existing file; rebuild; delete the file; stub `openExisting(any(Path.class))` to throw `GraphWorkspaceOpenException`; click item 0 via `doClick()`; sink is exactly `"graph_workspace.recent_workspaces.open_failed[<path>]"`; `verify(applicationController, never()).open(any())`; the rebuilt menu now shows the empty row (and Clear, because the entry is still stored) | UI-boundary catch, report, refresh, no create |
| C9 | `rebuildsTheMenuFromThePopupListener` | find the `JMenu` by name; read `menu.getPopupMenu().getPopupMenuListeners()` and invoke the recents `popupMenuWillBecomeVisible(new PopupMenuEvent(menu.getPopupMenu()))` (the popup also carries the L&F listener, so the test must select the recents one, for example the last registered) for a list with one existing file; the menu then has one entry item | popup-listener wiring on the popup menu |
| C10 | `keepsTheNineArgumentConstructorForTheUiEvidenceHarness` | `GraphWorkspaceWindowModel.class.getDeclaredConstructor(GraphWorkspaceHandle.class, GraphWorkspaceViewBinding.class, GraphWorkspaceController.class, Supplier.class, WorkspaceCloseController.class, Runnable.class, Runnable.class, Runnable.class, Consumer.class)` resolves on the EDT-constructed fixture; the resulting model holds the recent menu in the empty state | 9-argument constructor compatibility |

### 10.4 `DefaultGraphWorkspaceControllerShould` (extend)

Use the new package-private 4-argument constructor
`(WorkspaceSessionRegistry, SessionFactory, GraphWorkspaceViewFactory, RecentWorkspaceList)` with
a real list and the existing `resources(boolean)` helper; capture the `create` flag through the
injected `SessionFactory`; capture the close controller through the view-factory lambda as the
existing test `keepsTheSessionOpenWhenSaveFailsAndReleasesEverythingOnlyAfterRetrySucceeds` does.
Recorder-bearing resources use the new 9-argument `SessionResources` constructor with a Mockito
mock of `RecentWorkspaceRecorder`.

| # | Test | Seam / assertion | Reverted guarantee it catches |
| --- | --- | --- | --- |
| D1 | `recordsAnOpenedWorkspaceInTheRecentList` | open an existing file; `mostRecentExisting()` contains its real path and the captured persisted value contains that path | recording on successful open (`finishOpen`) |
| D2 | `recordsAgainWhenAnAlreadyOpenWorkspaceIsFocusedAfterClear` | open, `recentWorkspaces.clear()`, open the same file again (focus path); it is recorded again and resolves as most recent | recording on focus return |
| D3 | `aFailingPersisterDoesNotFailTheOpenOrHideTheWindow` | list whose persister throws; open an existing file; the handle is non-null, the recording view's `showCount` is 1, and no exception escapes | recording is best effort; open success is never rolled back |
| D4 | `openExistingNeverCreatesAWorkspaceForAMissingPath` | capturing `SessionFactory`; `assertThatThrownBy(openExisting(missing))` is `GraphWorkspaceOpenException` with a `NoSuchFileException` cause; factory call count 0 (no `create=true` ever observed); file still absent; `sessions.owner(path)` empty | bare delegation to creating `open` |
| D5 | `openExistingForcesACreateDisabledOpenWhenTheFileVanishesAfterTheCheck` | `mockStatic(Files.class)` with `isRegularFile(path)` returning `true` and `exists(path)` returning `false`; factory captures `create` and throws; the captured `create` is `false` and the failure is `GraphWorkspaceOpenException` | pre-check plus creating `open` (captured `create` becomes `true`); the race window |
| D6 | `openExistingOpensAnExistingFileWithoutCreating` | existing file; factory captures `create` false; handle returned and view shown | `openExisting` shares the non-creating path |
| D7 | `openExistingWrapsCanonicalizationFailure` | `Assume.assumeTrue(File.separatorChar == '/')` for the same ENOTDIR reason as A11; path `blocker/child.fpg` beneath a regular file; `GraphWorkspaceOpenException` with an `IllegalArgumentException` cause; factory never called | canonicalization wrapping |
| D8 | `openExistingWrapsShutdownFailure` | open once, `shutdown()`, then `openExisting` on an existing file; `GraphWorkspaceOpenException` with an `IllegalStateException` cause | shut-down wrapping |
| D9 | `closesTheSessionRecorderWhenTheSessionCloses` | `SessionResources` with a mocked `RecentWorkspaceRecorder`; capture close controller; `saveAndClose()` true; `verify(recorder).close()` | teardown via `closeRemainingResources` on close |
| D10 | `closesTheSessionRecorderOnShutdown` | same resources; `controller.shutdown()` off the EDT; `verify(recorder).close()` | teardown via `closeRemainingResourcesOffEdt` |
| D11 | `keepsTheSessionRecorderOpenOnSaveFailureAndClosesItOnRetry` | `store.close()` throws; `saveAndClose()` false → `verify(recorder, never()).close()`; then `store.close()` succeeds and `retrySaveAndClose()` true → `verify(recorder).close()` | recorder survives a failed save; closes on retry |

D5 note: the `mockStatic(Files.class)` stub is confined to the test thread (Mockito static mocks
are thread-local). `WorkspaceUriResolver.canonical` uses only `Path.toRealPath`/`toAbsolutePath`,
so canonicalization is unaffected by the stub; the only `Files` calls in `openExisting` are the
regular-file check and the create decision. The test's `SessionFactory` must throw when called so
that the wrapped failure is observed as `GraphWorkspaceOpenException`, and it must capture its
`create` argument before throwing.

### 10.5 `RecentWorkspaceRecorderShould` (new)

Path: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/RecentWorkspaceRecorderShould.java`.

Fixture: a Mockito mock `GraphWorkspaceStore` whose `addListener` captures the listener and
returns a mock `ListenerRegistration`; a real `RecentWorkspaceList` with a capturing persister;
events built through the package-private `WorkspaceStoreEvent.identityChanged(document, change)`,
`documentChanged(document)`, `saved(document)`, `saveFailed(document, error)` factories with a
mocked `WorkspaceDocument` and `WorkspaceId.of(...)`.

| # | Test | Seam / assertion | Reverted guarantee it catches |
| --- | --- | --- | --- |
| E1 | `recordsTheNewPathOnIdentityChanged` | fire an `IDENTITY_CHANGED` event with a new path; the list has that path stored and the persister was called | Save As recording seam |
| E2 | `ignoresDocumentChangedSavedAndSaveFailedEvents` | fire the other three event types; `hasStoredEntries()` is false and the persister was never called | event-type filter |
| E3 | `registersItselfAndReleasesTheRegistrationOnClose` | `verify(store).addListener(recorder)`; `close()` calls `registration.close()`; a second `close()` does not fail | self-registration and teardown |

### 10.6 `GraphPluginIntegrationShould` (extend)

| # | Test | Seam / assertion | Reverted guarantee it catches |
| --- | --- | --- | --- |
| F1 | `wiresApplicationWideRecentWorkspacesThroughUserProperties` | mock `ApplicationResourceController`; `when(getProperty(RecentWorkspaceList.PROPERTY_KEY, "")).thenReturn(seededValue)`; `mockConstruction(DefaultGraphWorkspaceController.class)` captures constructor arguments; the third argument is a `RecentWorkspaceList` reflecting the seeded value; capture the list given to `SwingGraphWorkspaceViewFactory` and to `OpenGraphWorkspaceAction` and assert `isSameAs` the controller's list, so a second or inert instance cannot slip in; recording a second temp file triggers `setProperty(PROPERTY_KEY, value)` captured; `clear()` triggers `setProperty(PROPERTY_KEY, "")` | property-backed wiring and one shared application-wide instance |
| F2 | `shipsTheFourRecentWorkspaceResourceKeys` | read `Resources_en.properties`; assert the four exact key/value pairs from section 8 | resource bundle content |
| F3 | `delegatesOpenExistingThroughTheForwardingController` | two mechanisms, both pinned: (a) bound — seed the mocked `ResourceController.getProperty` so the installed action's list holds one existing temp file, invoke the captured `OpenGraphWorkspaceAction.actionPerformed` (no chooser appears because the recents path resolves), then `verify(constructedController).openExisting(path)` and `verify(constructedController, never()).open(any())`; (b) unbound — `ForwardingGraphWorkspaceController` is `private static final` inside `GraphModeExtension` (`GraphModeExtension.java:133`) and the test lives in another package, so instantiate it reflectively (`Class.forName("org.freeplane.plugin.graph.GraphModeExtension$ForwardingGraphWorkspaceController")`, `setAccessible(true)` for the no-arg constructor and `openExisting(Path)`) and assert `openExisting` throws `GraphWorkspaceOpenException` wrapping the `IllegalStateException` | forwarding-controller fidelity: the production action path cannot silently fall back to create-or-open |
| F4 | `passesTheSharedListIntoTheCreatedHeadlessView` | `Assume.assumeTrue(GraphicsEnvironment.isHeadless())` (the mirror of F5, and mandatory here: this machine has a live `DISPLAY`, and the root `test` task sets no `java.awt.headless`, so the factory would otherwise build a real window); then `new SwingGraphWorkspaceViewFactory(controller, list)` with a list holding one recorded existing file, `create(handle, binding, close)`, reach the returned `HeadlessGraphWorkspaceView`'s package-private `model()`, rebuild, and assert the recents menu holds exactly that one entry; a factory built without the list yields the empty row instead | factory → view → model propagation of the shared list |
| F5 | `passesTheSharedListIntoTheCreatedSwingWindow` | same as F4 on the non-headless branch, guarded by `Assume.assumeFalse(GraphicsEnvironment.isHeadless())`: the created `GraphWorkspaceWindow`'s menu bar contains the recents submenu, and firing its recents popup listener yields the recorded entry | window-branch propagation (skipped in headless CI, covered by construction in the headless case) |

### 10.7 Regression tests that must stay green

- `GraphPluginIntegrationShould.opensOnlyPathsSelectedByTheInjectedWorkspaceChooser` — pins the
  surviving `(controller, Supplier<Path>)` action constructor and chooser `open` semantics.
- `GraphPluginIntegrationShould.installsAndRemovesTheApplicationScopedWorkspaceActionWithTheExistingGraphExtension`
  and `shutsDownWorkspaceSessionsBeforeRemovingGraphActionsAndExtensions` — pin extension
  lifecycle and action registration; the former also proves `standard()` survives a mocked
  `getProperty` returning `null`.
- `GraphPluginIntegrationShould.placesAndDescribesBothGraphActionsWithTheirOwnIcons` — no menu XML
  is touched.
- All existing `GraphWorkspaceWindowModelShould` tests — the fixture uses the 9-argument
  constructor, which must survive.
- `WorkspaceDialogsShould` and `UndoRoutingShould` — use the 8-argument model constructor.
- `GraphWorkspaceColdReloadShould` and `GraphWorkspaceLifecycleShould` — use
  `new DefaultGraphWorkspaceController(modeController, views)`, which now defaults to
  `RecentWorkspaceList.empty()`.
- `GraphWorkspaceUiEvidence` (`gradle :freeplane_plugin_graph:graphUiEvidence`) — reflective
  9-argument constructor lookup and the generated UI evidence PNGs.
- `GraphWorkspaceStoreShould`, `WorkspaceUriResolverShould`, and the rest of the module suite —
  no changed behavior outside the allowlist in section 1.3.

---

## 11. Definition of Done

| Requirement | Evidence (test, command, artifact) |
| --- | --- |
| File > Recent Workspaces submenu with exact placement and policy | C1–C9; `gradle :freeplane_plugin_graph:test` |
| View > Open Graph Workspace recent resolution and fallback | B1–B7; `gradle :freeplane_plugin_graph:test` |
| `RecentWorkspaceList` policy (caps, ordering, de-dup, hide-but-keep, labels, tolerance) | A1–A18; `gradle :freeplane_plugin_graph:test` |
| Persistence format and property wiring | A8, A18, F1; captured `setProperty` evidence in the test report |
| `openExisting` never creates and wraps all failures | D4–D8, F3; captured `create` flag in the test report |
| Recording on open, focus, and Save As; best-effort isolation | D1–D3, E1–E3; `gradle :freeplane_plugin_graph:test` |
| Recorder teardown through the resource funnel | D9–D11; `gradle :freeplane_plugin_graph:test` |
| 9-argument `GraphWorkspaceWindowModel` constructor compatibility | C10; `gradle :freeplane_plugin_graph:graphUiEvidence` regenerates `docs/superpowers/specs/images/2026-08-10-graph-workspace-implemented.png` without reflective-constructor failure |
| One shared application-wide list reaches every window and view | F1, F4, F5; `gradle :freeplane_plugin_graph:test` |
| Four resource keys with exact text and encoding | F2; `gradle format_translation` output |
| No regressions | section 10.7; full `gradle :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks` |

Verification commands, run from the root of whichever worktree the change is applied in:

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle format_translation
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  gradle :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:graphUiEvidence
```

Artifacts: JUnit reports under `freeplane_plugin_graph/build/reports/tests/test/`, the formatted
`Resources_en.properties`, and the regenerated UI evidence image. The approved mockup
`docs/superpowers/specs/images/2026-09-10-most-recent-workspaces-mockup.png` remains the visual
record; the menu itself is pinned structurally by C1–C10, not by a popup screenshot.

---

## 12. Open Questions and Recorded Clarifications

No design gap blocks implementation. Two under-specified points were resolved as follows and are
recorded for the reviewer:

1. **`openExisting` falsifiability.** The design's controller test description ("opening a path
   that does not exist fails with `GraphWorkspaceOpenException`, the captured `create` flag is
   never `true`") cannot, by itself, distinguish the specified structural no-create contract
   from a pre-check-plus-`open()` implementation, because the regular-file check fails before
   the factory is reached in both. This specification therefore fixes the private
   `open(Path, boolean createMissing)` overload with `create = createMissing && !Files.exists(path)`
   and adds test D5, which stubs `Files.isRegularFile` true and `Files.exists` false to force the
   vanish-after-check window. A pre-check-plus-`open()` implementation captures `create == true`
   and fails D5; a bare `open(path)` implementation fails D4.
2. **`empty()` no-op versus freshness.** The design states that `record` and `clear` on an
   `empty()` instance are no-ops and also justifies per-call freshness by a shared-singleton leak
   from recorded paths. This specification follows the literal rule (fully inert `empty()`
   instances) and keeps freshness as a redundant safety property for the test-only overloads.
   A15 pins both readings (`empty()` instances differ, are inert, and do not leak recorded
   paths), so the two rules are jointly enforced rather than merely reconciled.
