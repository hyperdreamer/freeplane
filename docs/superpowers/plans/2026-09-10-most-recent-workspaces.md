# Most Recent Workspaces Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `Recent Workspaces` submenu to the graph workspace window and make `View > Open Graph Workspace` reopen the most recently used workspace that still exists, backed by one application-wide list persisted under the user property `graph_workspace_recent_workspaces`.

**Architecture:** A new Swing-free `RecentWorkspaceList` owns ordering, caps (stored 25, displayed 8), hide-but-keep existence filtering, label formatting, and the `ConfigurationUtils` persistence encoding. A per-session `RecentWorkspaceRecorder` listens for `IDENTITY_CHANGED` so Save As is recorded; the controller records on open success and on focus of an already-open workspace, and the new `GraphWorkspaceController.openExisting(Path)` never takes the create branch. `GraphModeExtension` creates exactly one `RecentWorkspaceList.standard()` and injects it into the controller, the Swing/headless view factory, and `OpenGraphWorkspaceAction`.

**Tech Stack:** Java 8 source and bytecode; Java 21 Zulu toolchain; Gradle; JUnit 4; AssertJ; Mockito 5.18.0; Swing; Freeplane OSGi plugin architecture.

## Global Constraints

- Work only in the lane worktree you were given (its absolute path is stated in your task brief). Never create, modify, or delete anything under `/data/home/guest/Development/freeplane/.worktrees/graph-workspace` — that is the delivery target, which the Project Manager keeps pristine until an explicit delivery decision. Commit only on the branch already checked out in your lane worktree, and never run `git worktree`, `git switch`, `git checkout <branch>`, `git reset`, or `git clean`.
- Java 8 source and target bytecode (class major version 52) built with the Java 21 toolchain at `/home/henry/.sdkman/candidates/java/21.0.8-zulu`; run every Gradle command as `JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle ...`.
- Source files are UTF-8; indentation is 4 spaces.
- Tests use JUnit 4 with AssertJ and Mockito 5.18.0 (the repository versions).
- Module test command: `JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test`; add `-PTestLoggingFull --rerun-tasks` for verbose, forced failures.
- Resource bundles are ISO-8859-1 with `\uXXXX` escapes and no literal non-ASCII bytes; after editing any bundle run `JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle format_translation` and keep the formatted result.
- Every task commit carries this plan's identifier, e.g. `git commit -m "Add recent workspace list module [2026-09-10-most-recent-workspaces]"`.
- The existing 9-argument `GraphWorkspaceWindowModel` constructor `(GraphWorkspaceHandle, GraphWorkspaceViewBinding, GraphWorkspaceController, Supplier<Path>, WorkspaceCloseController, Runnable, Runnable, Runnable, Consumer<String>)` must survive verbatim; `GraphWorkspaceUiEvidence.ModelAccess.create` reflectively requires exactly that signature.
- The plugin's OSGi metadata stays unchanged: `ext.bundleExports = ''` and `ext.bundleImports = 'nothing.*'`; no `freeplane_api` source, bytecode, or export change.
- No new runtime dependency, no new `.fpg` schema, version, migration, or `freeplane_api` change.
- Only the 15 files named in the Approved Specification section 1.3 change map may be created or modified; do not touch any other file.
- Every task follows strict TDD: write or extend a falsifiable test, run it red, make the smallest implementation change, rerun it green, inspect the diff, and create the specified commit.

## Task 1: RecentWorkspaceList policy module

**Implementer tier:** Fast

**Lane:** recent-list

**Depends on:** none

**Files:**

- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/RecentWorkspaceList.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/RecentWorkspaceListShould.java`

**Interfaces:**

- Consumes: `WorkspaceUriResolver.canonical(Path): Path` from `org.freeplane.plugin.graph.workspace` (existing); `ConfigurationUtils.encodeListValue(List<String>, boolean): String` and `ConfigurationUtils.decodeListValue(String, boolean): List<String>` from `org.freeplane.core.util` (existing); `TextWritingDirection.LEFT_TO_RIGHT.isolatePathSeparators(String): String` from `org.freeplane.api` (existing); `ResourceController.getResourceController(): ResourceController`, `ResourceController.getProperty(String, String): String`, and `ResourceController.setProperty(String, String): void` from `org.freeplane.core.resources` (existing).
- Produces: `public final class RecentWorkspaceList` in `org.freeplane.plugin.graph.workspace` with `public static final String PROPERTY_KEY = "graph_workspace_recent_workspaces"`, `public static final int STORED_CAPACITY = 25`, `public static final int DISPLAY_CAPACITY = 8`, nested `public static final class Entry` with `public Path path()` and `public String label()`, `public static RecentWorkspaceList standard()`, `public static RecentWorkspaceList empty()`, `public RecentWorkspaceList(String storedValue, Consumer<String> persister)`, `public void record(Path workspaceFile)`, `public void clear()`, `public List<Entry> displayEntries()`, `public boolean hasStoredEntries()`, `public Optional<Path> mostRecentExisting()`, and package-private `static String labelFor(Path path)`.

- [ ] **Step 1: Write the failing test class A1-A18**

Create `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/RecentWorkspaceListShould.java` with exactly this content:

```java
package org.freeplane.plugin.graph.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.freeplane.api.TextWritingDirection;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.ConfigurationUtils;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;

public class RecentWorkspaceListShould {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void ordersEntriesNewestFirst() throws Exception {
        Path first = temporaryFolder.newFile("first.fpg").toPath().toRealPath();
        Path second = temporaryFolder.newFile("second.fpg").toPath().toRealPath();
        Path third = temporaryFolder.newFile("third.fpg").toPath().toRealPath();
        RecentWorkspaceList list = new RecentWorkspaceList("", value -> { });

        list.record(first);
        list.record(second);
        list.record(third);

        assertThat(list.displayEntries()).containsExactly(
            RecentWorkspaceList.Entry.of(third, RecentWorkspaceList.labelFor(third)),
            RecentWorkspaceList.Entry.of(second, RecentWorkspaceList.labelFor(second)),
            RecentWorkspaceList.Entry.of(first, RecentWorkspaceList.labelFor(first)));
    }

    @Test
    public void promotesARecordedPathInsteadOfDuplicatingIt() throws Exception {
        Path a = temporaryFolder.newFile("promote-a.fpg").toPath().toRealPath();
        Path b = temporaryFolder.newFile("promote-b.fpg").toPath().toRealPath();
        AtomicReference<String> persisted = new AtomicReference<String>();
        RecentWorkspaceList list = new RecentWorkspaceList("", persisted::set);

        list.record(a);
        list.record(b);
        list.record(a);

        assertThat(list.displayEntries()).containsExactly(
            RecentWorkspaceList.Entry.of(a, RecentWorkspaceList.labelFor(a)),
            RecentWorkspaceList.Entry.of(b, RecentWorkspaceList.labelFor(b)));
        assertThat(ConfigurationUtils.decodeListValue(persisted.get(), true)).hasSize(2);
    }

    @Test
    public void evictsTheOldestEntryAtTheStoredCapacity() throws Exception {
        AtomicReference<String> persisted = new AtomicReference<String>();
        RecentWorkspaceList list = new RecentWorkspaceList("", persisted::set);
        List<Path> files = new ArrayList<Path>();
        for (int index = 0; index < 26; index++) {
            Path file = temporaryFolder.newFile("cap-" + index + ".fpg").toPath().toRealPath();
            files.add(file);
            list.record(file);
        }

        List<String> decoded = ConfigurationUtils.decodeListValue(persisted.get(), true);

        assertThat(decoded).hasSize(RecentWorkspaceList.STORED_CAPACITY);
        assertThat(decoded.get(0)).isEqualTo(files.get(25).toString());
        assertThat(decoded).doesNotContain(files.get(0).toString());
    }

    @Test
    public void truncatesDisplayedEntriesAtTheDisplayCapacity() throws Exception {
        RecentWorkspaceList list = new RecentWorkspaceList("", value -> { });
        List<Path> files = new ArrayList<Path>();
        for (int index = 0; index < 10; index++) {
            Path file = temporaryFolder.newFile("display-" + index + ".fpg").toPath().toRealPath();
            files.add(file);
            list.record(file);
        }

        List<RecentWorkspaceList.Entry> entries = list.displayEntries();

        assertThat(entries).hasSize(RecentWorkspaceList.DISPLAY_CAPACITY);
        assertThat(entries.get(0).path()).isEqualTo(files.get(9));
        assertThat(entries.get(7).path()).isEqualTo(files.get(2));
    }

    @Test
    public void filtersMissingFilesFromDisplayButKeepsThemStored() throws Exception {
        Path existing = temporaryFolder.newFile("kept-existing.fpg").toPath().toRealPath();
        Path missing = temporaryFolder.newFile("kept-missing.fpg").toPath().toRealPath();
        AtomicReference<String> persisted = new AtomicReference<String>();
        RecentWorkspaceList list = new RecentWorkspaceList("", persisted::set);
        list.record(existing);
        list.record(missing);

        Files.delete(missing);

        assertThat(list.displayEntries()).containsExactly(
            RecentWorkspaceList.Entry.of(existing, RecentWorkspaceList.labelFor(existing)));
        assertThat(list.hasStoredEntries()).isTrue();

        RecentWorkspaceList reloaded = new RecentWorkspaceList(persisted.get(), value -> { });
        assertThat(reloaded.hasStoredEntries()).isTrue();
        assertThat(reloaded.displayEntries()).containsExactly(
            RecentWorkspaceList.Entry.of(existing, RecentWorkspaceList.labelFor(existing)));

        Files.createFile(missing);

        RecentWorkspaceList restored = new RecentWorkspaceList(persisted.get(), value -> { });
        assertThat(restored.displayEntries()).containsExactly(
            RecentWorkspaceList.Entry.of(missing, RecentWorkspaceList.labelFor(missing)),
            RecentWorkspaceList.Entry.of(existing, RecentWorkspaceList.labelFor(existing)));
    }

    @Test
    public void skipsMissingEntriesWhenResolvingTheMostRecentExisting() throws Exception {
        Path older = temporaryFolder.newFile("resolution-older.fpg").toPath().toRealPath();
        Path newest = temporaryFolder.newFile("resolution-newest.fpg").toPath().toRealPath();
        RecentWorkspaceList list = new RecentWorkspaceList("", value -> { });
        list.record(older);
        list.record(newest);

        Files.delete(newest);

        assertThat(list.mostRecentExisting()).contains(older);
    }

    @Test
    public void clearsEveryStoredEntryAndPersistsTheEmptyString() throws Exception {
        Path existing = temporaryFolder.newFile("cleared.fpg").toPath().toRealPath();
        AtomicReference<String> persisted = new AtomicReference<String>();
        RecentWorkspaceList list = new RecentWorkspaceList("", persisted::set);
        list.record(existing);

        list.clear();

        assertThat(list.hasStoredEntries()).isFalse();
        assertThat(list.displayEntries()).isEmpty();
        assertThat(persisted.get()).isEqualTo("");
    }

    @Test
    public void roundTripsThePersistenceFormatThroughEncodeListValue() throws Exception {
        Path first = temporaryFolder.newFile("round-first.fpg").toPath().toRealPath();
        Path second = temporaryFolder.newFile("round-second.fpg").toPath().toRealPath();
        AtomicReference<String> persisted = new AtomicReference<String>();
        RecentWorkspaceList list = new RecentWorkspaceList("", persisted::set);

        list.record(first);
        list.record(second);

        assertThat(persisted.get()).isEqualTo(ConfigurationUtils.encodeListValue(
            Arrays.asList(second.toString(), first.toString()), true));

        RecentWorkspaceList reloaded = new RecentWorkspaceList(persisted.get(), value -> { });
        assertThat(reloaded.displayEntries()).containsExactly(
            RecentWorkspaceList.Entry.of(second, RecentWorkspaceList.labelFor(second)),
            RecentWorkspaceList.Entry.of(first, RecentWorkspaceList.labelFor(first)));
    }

    @Test
    public void dropsInvalidStoredTokensAtConstruction() throws Exception {
        Path absolute = temporaryFolder.newFile("absolute.fpg").toPath().toRealPath();
        String stored = ConfigurationUtils.encodeListValue(Arrays.asList(
            absolute.toString(), "relative.fpg", "\u0000bad"), true);

        RecentWorkspaceList list = new RecentWorkspaceList(stored, value -> { });

        assertThat(list.displayEntries()).containsExactly(
            RecentWorkspaceList.Entry.of(absolute, RecentWorkspaceList.labelFor(absolute)));
        assertThat(new RecentWorkspaceList(null, value -> { }).hasStoredEntries()).isFalse();
        assertThat(new RecentWorkspaceList("", value -> { }).hasStoredEntries()).isFalse();
    }

    @Test
    public void truncatesDecodedValuesToTheStoredCapacity() throws Exception {
        List<String> tokens = new ArrayList<String>();
        for (int index = 0; index < 26; index++) {
            tokens.add(temporaryFolder.getRoot().toPath().resolve("decoded-" + index + ".fpg").toString());
        }
        String stored = ConfigurationUtils.encodeListValue(tokens, true);
        Path recorded = temporaryFolder.newFile("decoded-recorded.fpg").toPath().toRealPath();
        AtomicReference<String> persisted = new AtomicReference<String>();
        RecentWorkspaceList list = new RecentWorkspaceList(stored, persisted::set);

        list.record(recorded);

        List<String> decoded = ConfigurationUtils.decodeListValue(persisted.get(), true);
        assertThat(decoded).hasSize(RecentWorkspaceList.STORED_CAPACITY);
        assertThat(decoded.get(0)).isEqualTo(recorded.toString());
        assertThat(decoded).contains(tokens.get(23));
        assertThat(decoded).doesNotContain(tokens.get(24), tokens.get(25));
    }

    @Test
    public void recordNeverThrowsOnAnUnusablePath() throws Exception {
        Assume.assumeTrue(File.separatorChar == '/');
        Path blocker = temporaryFolder.newFile("blocker.fpg").toPath().toRealPath();
        Path unusable = blocker.resolve("child.fpg");
        AtomicInteger persisterCalls = new AtomicInteger();
        RecentWorkspaceList list = new RecentWorkspaceList("", value -> persisterCalls.incrementAndGet());

        assertThatCode(() -> list.record(unusable)).doesNotThrowAnyException();

        assertThat(list.hasStoredEntries()).isFalse();
        assertThat(persisterCalls).hasValue(0);
    }

    @Test
    public void recordAndClearNeverThrowWhenThePersisterFails() throws Exception {
        Path existing = temporaryFolder.newFile("failing-persister.fpg").toPath().toRealPath();
        RecentWorkspaceList list = new RecentWorkspaceList("", value -> {
            throw new IllegalStateException("persister failed");
        });

        assertThatCode(() -> list.record(existing)).doesNotThrowAnyException();

        assertThat(list.hasStoredEntries()).isTrue();
        assertThatCode(list::clear).doesNotThrowAnyException();
    }

    @Test
    public void keepsUnresolvableStoredPathsAcrossAnUnrelatedRecord() throws Exception {
        Path blocker = temporaryFolder.newFile("unresolvable-blocker.fpg").toPath().toRealPath();
        Path unresolvable = blocker.resolve("child.fpg");
        String stored = ConfigurationUtils.encodeListValue(Arrays.asList(unresolvable.toString()), true);
        Path unrelated = temporaryFolder.newFile("unrelated.fpg").toPath().toRealPath();
        AtomicReference<String> persisted = new AtomicReference<String>();
        RecentWorkspaceList list = new RecentWorkspaceList(stored, persisted::set);

        assertThatCode(list::displayEntries).doesNotThrowAnyException();
        assertThatCode(list::mostRecentExisting).doesNotThrowAnyException();
        assertThat(list.hasStoredEntries()).isTrue();

        list.record(unrelated);

        assertThat(persisted.get()).contains(unresolvable.toString());
    }

    @Test
    public void recordsTheCanonicalPathAndNotTheGivenVariant() throws Exception {
        Path directory = temporaryFolder.newFolder("canonical-dir").toPath().toRealPath();
        Path file = Files.createFile(directory.resolve("x.fpg")).toRealPath();
        Path variant = directory.resolve("..").resolve("canonical-dir").resolve("x.fpg");
        AtomicReference<String> persisted = new AtomicReference<String>();
        RecentWorkspaceList list = new RecentWorkspaceList("", persisted::set);

        list.record(variant);

        assertThat(persisted.get()).contains(file.toString());
        assertThat(persisted.get()).doesNotContain("..");
    }

    @Test
    public void emptyReturnsFreshInertInstances() throws Exception {
        Path existing = temporaryFolder.newFile("inert.fpg").toPath().toRealPath();
        RecentWorkspaceList first = RecentWorkspaceList.empty();
        RecentWorkspaceList second = RecentWorkspaceList.empty();

        assertThat(first).isNotSameAs(second);
        first.record(existing);
        first.clear();

        assertThat(first.hasStoredEntries()).isFalse();
        assertThat(first.displayEntries()).isEmpty();
        assertThat(second.hasStoredEntries()).isFalse();
    }

    @Test
    public void labelHelperUsesFileNameAndFullContainingFolder() {
        Path path = Paths.get("/a/b/x.fpg");

        assertThat(RecentWorkspaceList.labelFor(path)).isEqualTo("x.fpg (" + path.getParent() + ")");
        assertThat(RecentWorkspaceList.labelFor(Paths.get("/p/a/x.fpg")))
            .isNotEqualTo(RecentWorkspaceList.labelFor(Paths.get("/q/a/x.fpg")));
    }

    @Test
    public void labelHelperFallsBackToThePathStringForARoot() {
        Path root = temporaryFolder.getRoot().toPath().getRoot();

        assertThat(RecentWorkspaceList.labelFor(root))
            .isEqualTo(TextWritingDirection.LEFT_TO_RIGHT.isolatePathSeparators(root.toString()));
    }

    @Test
    public void standardToleratesAnAbsentResourceController() throws Exception {
        Path existing = temporaryFolder.newFile("absent-resources.fpg").toPath().toRealPath();
        try (MockedStatic<ResourceController> resources =
                org.mockito.Mockito.mockStatic(ResourceController.class)) {
            resources.when(ResourceController::getResourceController)
                .thenThrow(new NullPointerException("no controller"));

            RecentWorkspaceList list = RecentWorkspaceList.standard();

            assertThat(list.hasStoredEntries()).isFalse();
            assertThat(list.displayEntries()).isEmpty();
            assertThatCode(() -> list.record(existing)).doesNotThrowAnyException();
            assertThatCode(list::clear).doesNotThrowAnyException();
        }
    }
}
```

- [ ] **Step 2: Run the new test and confirm it fails**

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.workspace.RecentWorkspaceListShould'
```

Expected: FAIL, compilation error `cannot find symbol: class RecentWorkspaceList`.

- [ ] **Step 3: Implement RecentWorkspaceList**

Create `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/RecentWorkspaceList.java` with exactly this content:

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

- [ ] **Step 4: Run the tests and confirm they pass**

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  gradle :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks \
  --tests 'org.freeplane.plugin.graph.workspace.RecentWorkspaceListShould'
```

Expected: PASS, 18 tests (A1-A18). On Linux, A11 runs; on Windows it is skipped by its assumption.

- [ ] **Step 5: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/RecentWorkspaceList.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/RecentWorkspaceListShould.java
git commit -m "Add recent workspace list module [2026-09-10-most-recent-workspaces]"
```

## Task 2: RecentWorkspaceRecorder

**Implementer tier:** Fast

**Lane:** recent-recorder

**Depends on:** Task 1

**Files:**

- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/RecentWorkspaceRecorder.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/RecentWorkspaceRecorderShould.java`

**Interfaces:**

- Consumes: `RecentWorkspaceList.record(Path): void`, `RecentWorkspaceList.hasStoredEntries(): boolean`, and `RecentWorkspaceList.mostRecentExisting(): Optional<Path>` from Task 1; `public RecentWorkspaceList(String storedValue, Consumer<String> persister)` from Task 1; existing `GraphWorkspaceStore.addListener(WorkspaceStoreListener): ListenerRegistration`, `WorkspaceStoreEvent.type(): Type`, `WorkspaceStoreEvent.identityChange(): Optional<WorkspaceIdentityChange>`, `WorkspaceIdentityChange.newPath(): Path`, `WorkspaceStoreEvent.identityChanged(WorkspaceDocument, WorkspaceIdentityChange)`, `WorkspaceStoreEvent.documentChanged(WorkspaceDocument)`, `WorkspaceStoreEvent.saved(WorkspaceDocument)`, `WorkspaceStoreEvent.saveFailed(WorkspaceDocument, Throwable)`, and `ListenerRegistration.close(): void`; existing `WorkspaceDocument.createVersion1(WorkspaceId)` and `WorkspaceId.of(String)`.
- Produces: `public final class RecentWorkspaceRecorder implements WorkspaceStoreListener, AutoCloseable` with `public RecentWorkspaceRecorder(GraphWorkspaceStore store, RecentWorkspaceList recentWorkspaces)`, `public void onWorkspaceStoreEvent(WorkspaceStoreEvent event)`, and `public void close()`.

- [ ] **Step 1: Write the failing test class E1-E3**

Create `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/RecentWorkspaceRecorderShould.java` with exactly this content:

```java
package org.freeplane.plugin.graph.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.freeplane.plugin.graph.workspace.model.WorkspaceDocument;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class RecentWorkspaceRecorderShould {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void recordsTheNewPathOnIdentityChanged() throws Exception {
        Fixture fixture = fixture();
        Path newPath = temporaryFolder.newFile("save-as-target.fpg").toPath().toRealPath();
        WorkspaceDocument document = WorkspaceDocument.createVersion1(
            WorkspaceId.of("00000000-0000-0000-0000-000000000001"));
        WorkspaceIdentityChange change = new WorkspaceIdentityChange(
            temporaryFolder.getRoot().toPath().resolve("save-as-source.fpg"), newPath,
            WorkspaceId.of("00000000-0000-0000-0000-000000000001"),
            WorkspaceId.of("00000000-0000-0000-0000-000000000002"));

        fixture.listener().onWorkspaceStoreEvent(WorkspaceStoreEvent.identityChanged(document, change));

        assertThat(fixture.list.hasStoredEntries()).isTrue();
        assertThat(fixture.list.mostRecentExisting()).contains(newPath);
        assertThat(fixture.persisted.get()).isNotNull();
        assertThat(fixture.persisterCalls).hasValue(1);
    }

    @Test
    public void ignoresDocumentChangedSavedAndSaveFailedEvents() {
        Fixture fixture = fixture();
        WorkspaceDocument document = WorkspaceDocument.createVersion1(
            WorkspaceId.of("00000000-0000-0000-0000-000000000001"));

        fixture.listener().onWorkspaceStoreEvent(WorkspaceStoreEvent.documentChanged(document));
        fixture.listener().onWorkspaceStoreEvent(WorkspaceStoreEvent.saved(document));
        fixture.listener().onWorkspaceStoreEvent(WorkspaceStoreEvent.saveFailed(document,
            new IllegalStateException("save failed")));

        assertThat(fixture.list.hasStoredEntries()).isFalse();
        assertThat(fixture.persisterCalls).hasValue(0);
    }

    @Test
    public void registersItselfAndReleasesTheRegistrationOnClose() {
        Fixture fixture = fixture();

        verify(fixture.store).addListener(fixture.recorder);
        assertThatCode(fixture.recorder::close).doesNotThrowAnyException();
        assertThatCode(fixture.recorder::close).doesNotThrowAnyException();
        verify(fixture.registration, times(2)).close();
    }

    private Fixture fixture() {
        GraphWorkspaceStore store = mock(GraphWorkspaceStore.class);
        ListenerRegistration registration = mock(ListenerRegistration.class);
        AtomicReference<WorkspaceStoreListener> listener = new AtomicReference<WorkspaceStoreListener>();
        when(store.addListener(any(WorkspaceStoreListener.class))).thenAnswer(invocation -> {
            listener.set(invocation.getArgument(0));
            return registration;
        });
        AtomicReference<String> persisted = new AtomicReference<String>();
        AtomicInteger persisterCalls = new AtomicInteger();
        RecentWorkspaceList list = new RecentWorkspaceList("", value -> {
            persisted.set(value);
            persisterCalls.incrementAndGet();
        });
        RecentWorkspaceRecorder recorder = new RecentWorkspaceRecorder(store, list);
        return new Fixture(store, registration, listener, persisted, persisterCalls, list, recorder);
    }

    private static final class Fixture {
        private final GraphWorkspaceStore store;
        private final ListenerRegistration registration;
        private final AtomicReference<WorkspaceStoreListener> listener;
        private final AtomicReference<String> persisted;
        private final AtomicInteger persisterCalls;
        private final RecentWorkspaceList list;
        private final RecentWorkspaceRecorder recorder;

        private Fixture(final GraphWorkspaceStore store, final ListenerRegistration registration,
                final AtomicReference<WorkspaceStoreListener> listener,
                final AtomicReference<String> persisted, final AtomicInteger persisterCalls,
                final RecentWorkspaceList list, final RecentWorkspaceRecorder recorder) {
            this.store = store;
            this.registration = registration;
            this.listener = listener;
            this.persisted = persisted;
            this.persisterCalls = persisterCalls;
            this.list = list;
            this.recorder = recorder;
        }

        private WorkspaceStoreListener listener() {
            return listener.get();
        }
    }
}
```

- [ ] **Step 2: Run the new test and confirm it fails**

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.workspace.RecentWorkspaceRecorderShould'
```

Expected: FAIL, compilation error `cannot find symbol: class RecentWorkspaceRecorder`.

- [ ] **Step 3: Implement RecentWorkspaceRecorder**

Create `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/RecentWorkspaceRecorder.java` with exactly this content:

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

- [ ] **Step 4: Run the tests and confirm they pass**

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  gradle :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks \
  --tests 'org.freeplane.plugin.graph.workspace.RecentWorkspaceRecorderShould'
```

Expected: PASS, 3 tests (E1-E3).

- [ ] **Step 5: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/RecentWorkspaceRecorder.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/RecentWorkspaceRecorderShould.java
git commit -m "Record recent workspaces on identity changes [2026-09-10-most-recent-workspaces]"
```

## Task 3: Non-creating openExisting on the workspace controller

**Implementer tier:** Advanced

**Lane:** controller

**Depends on:** none

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/GraphWorkspaceController.java:1-7`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceController.java:1-50`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceController.java:281-324`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceControllerShould.java:1-50`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceControllerShould.java:1200-1212`

**Interfaces:**

- Consumes: `WorkspaceUriResolver.canonical(Path): Path`; `GraphWorkspaceOpenException(Path, Throwable)`; `WorkspaceSessionRegistry.owner(Path): Optional<WorkspaceSessionId>`, `WorkspaceSessionRegistry.register(WorkspaceSessionId, Path): boolean`, `WorkspaceSessionRegistry.unregister(WorkspaceSessionId): void`; `SessionFactory.open(Path, WorkspaceSessionId, boolean): SessionResources`; the existing package-private test constructor `DefaultGraphWorkspaceController(WorkspaceSessionRegistry, SessionFactory, GraphWorkspaceViewFactory)`.
- Produces: `public interface GraphWorkspaceController` gains `GraphWorkspaceHandle openExisting(Path workspaceFile)`; `DefaultGraphWorkspaceController` implements it and gains the private `GraphWorkspaceHandle open(Path workspaceFile, boolean createMissing)` overload with `create = createMissing && !Files.exists(path);`. The public `open(Path)` behavior is unchanged: it delegates with `createMissing = true`.

- [ ] **Step 1: Write the failing tests D4-D8**

Add these imports to `DefaultGraphWorkspaceControllerShould.java` (after `import java.nio.file.Files;` and after `import org.junit.Rule;`):

```java
import java.io.File;
import java.nio.file.NoSuchFileException;
```

```java
import org.junit.Assume;
```

```java
import org.mockito.MockedStatic;
```

Insert these five test methods into `DefaultGraphWorkspaceControllerShould.java` immediately before `private static void awaitPathPresent(...)`:

```java
    @Test
    public void openExistingNeverCreatesAWorkspaceForAMissingPath() throws Exception {
        Path missing = temporaryFolder.getRoot().toPath().resolve("never-created.fpg");
        WorkspaceSessionRegistry sessions = new WorkspaceSessionRegistry();
        AtomicInteger factoryCalls = new AtomicInteger();
        DefaultGraphWorkspaceController controller = new DefaultGraphWorkspaceController(sessions,
            (path, id, create) -> {
                factoryCalls.incrementAndGet();
                return resources(false);
            }, (handle, binding, close) -> new RecordingView());

        assertThatThrownBy(() -> controller.openExisting(missing))
            .isInstanceOf(GraphWorkspaceOpenException.class)
            .hasCauseInstanceOf(NoSuchFileException.class);

        assertThat(factoryCalls).hasValue(0);
        assertThat(Files.exists(missing)).isFalse();
        assertThat(sessions.owner(missing)).isEmpty();
    }

    @Test
    public void openExistingForcesACreateDisabledOpenWhenTheFileVanishesAfterTheCheck() throws Exception {
        Path workspace = temporaryFolder.getRoot().toPath().resolve("vanishing.fpg");
        WorkspaceSessionRegistry sessions = new WorkspaceSessionRegistry();
        AtomicReference<Boolean> capturedCreate = new AtomicReference<Boolean>();
        DefaultGraphWorkspaceController controller = new DefaultGraphWorkspaceController(sessions,
            (path, id, create) -> {
                capturedCreate.set(create);
                throw new IllegalStateException("store open failed");
            }, (handle, binding, close) -> new RecordingView());

        try (MockedStatic<Files> files = org.mockito.Mockito.mockStatic(Files.class)) {
            files.when(() -> Files.isRegularFile(any(Path.class))).thenReturn(true);
            files.when(() -> Files.exists(any(Path.class))).thenReturn(false);

            assertThatThrownBy(() -> controller.openExisting(workspace))
                .isInstanceOf(GraphWorkspaceOpenException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
        }

        assertThat(capturedCreate).hasValue(false);
    }

    @Test
    public void openExistingOpensAnExistingFileWithoutCreating() throws Exception {
        Path workspace = temporaryFolder.newFile("open-existing.fpg").toPath().toRealPath();
        WorkspaceSessionRegistry sessions = new WorkspaceSessionRegistry();
        DefaultGraphWorkspaceController.SessionResources resources = resources(false);
        AtomicReference<Boolean> capturedCreate = new AtomicReference<Boolean>();
        RecordingView view = new RecordingView();
        DefaultGraphWorkspaceController controller = new DefaultGraphWorkspaceController(sessions,
            (path, id, create) -> {
                capturedCreate.set(create);
                return resources;
            }, (handle, binding, close) -> view);

        GraphWorkspaceHandle handle = controller.openExisting(workspace);

        assertThat(handle).isNotNull();
        assertThat(capturedCreate).hasValue(false);
        assertThat(view.showCount).hasValue(1);
        assertThat(sessions.owner(workspace)).isPresent();
    }

    @Test
    public void openExistingWrapsCanonicalizationFailure() throws Exception {
        Assume.assumeTrue(File.separatorChar == '/');
        Path blocker = temporaryFolder.newFile("blocker.fpg").toPath().toRealPath();
        Path workspace = blocker.resolve("child.fpg");
        WorkspaceSessionRegistry sessions = new WorkspaceSessionRegistry();
        AtomicInteger factoryCalls = new AtomicInteger();
        DefaultGraphWorkspaceController controller = new DefaultGraphWorkspaceController(sessions,
            (path, id, create) -> {
                factoryCalls.incrementAndGet();
                return resources(false);
            }, (handle, binding, close) -> new RecordingView());

        assertThatThrownBy(() -> controller.openExisting(workspace))
            .isInstanceOf(GraphWorkspaceOpenException.class)
            .hasCauseInstanceOf(IllegalArgumentException.class);

        assertThat(factoryCalls).hasValue(0);
    }

    @Test
    public void openExistingWrapsShutdownFailure() throws Exception {
        Path first = temporaryFolder.newFile("shutdown-open.fpg").toPath().toRealPath();
        Path second = temporaryFolder.newFile("shutdown-reopen.fpg").toPath().toRealPath();
        WorkspaceSessionRegistry sessions = new WorkspaceSessionRegistry();
        DefaultGraphWorkspaceController.SessionResources resources = resources(false);
        DefaultGraphWorkspaceController controller = new DefaultGraphWorkspaceController(sessions,
            (path, id, create) -> resources, (handle, binding, close) -> new RecordingView());
        controller.open(first);

        controller.shutdown();

        assertThatThrownBy(() -> controller.openExisting(second))
            .isInstanceOf(GraphWorkspaceOpenException.class)
            .hasCauseInstanceOf(IllegalStateException.class);
    }
```

- [ ] **Step 2: Run the new tests and confirm they fail**

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.control.DefaultGraphWorkspaceControllerShould'
```

Expected: FAIL, compilation error `cannot find symbol: method openExisting(Path)`.

- [ ] **Step 3: Add the interface method and the private create-controlled overload**

Replace the whole body of `GraphWorkspaceController.java` with:

```java
package org.freeplane.plugin.graph.control;

import java.nio.file.Path;

public interface GraphWorkspaceController {
    GraphWorkspaceHandle open(Path workspaceFile);

    GraphWorkspaceHandle openExisting(Path workspaceFile);
}
```

Add `import java.nio.file.NoSuchFileException;` to `DefaultGraphWorkspaceController.java` next to the existing `import java.nio.file.Files;`.

Replace the existing `public GraphWorkspaceHandle open(final Path workspaceFile)` method (lines 281-324) with exactly:

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

    private GraphWorkspaceHandle open(final Path workspaceFile, final boolean createMissing) {
        final Path path = uriResolver.canonical(Objects.requireNonNull(workspaceFile, "workspaceFile"));
        while (true) {
            Session existing = null;
            Session session = null;
            boolean create = false;
            synchronized (monitor) {
                if (shutdownStarted) {
                    throw new IllegalStateException("Graph workspace controller is shut down");
                }
                final Optional<WorkspaceSessionId> owner = sessions.owner(path);
                if (owner.isPresent()) {
                    existing = openSessions.get(owner.get());
                    if (existing == null) {
                        throw new GraphWorkspaceOpenException(path,
                            new IllegalStateException("Workspace registry owner has no live session"));
                    }
                }
                else {
                    final WorkspaceSessionId sessionId = WorkspaceSessionId.of(UUID.randomUUID());
                    if (!sessions.register(sessionId, path)) {
                        continue;
                    }
                    create = createMissing && !Files.exists(path);
                    session = new Session(sessionId, path);
                    openSessions.put(sessionId, session);
                }
            }
            if (existing != null) {
                if (SwingUtilities.isEventDispatchThread() && (existing.isOpening() || existing.isClosing())) {
                    throw new GraphWorkspaceOpenException(path,
                        new IllegalStateException("Workspace is still being opened or closed"));
                }
                if (existing.awaitOpen()) {
                    if (existing.focus()) {
                        return existing.handle;
                    }
                    continue;
                }
                continue;
            }
            return finishOpen(session, path, create);
        }
    }
```

- [ ] **Step 4: Run the tests and confirm they pass**

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  gradle :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks \
  --tests 'org.freeplane.plugin.graph.control.DefaultGraphWorkspaceControllerShould'
```

Expected: PASS, all existing tests plus D4-D8. On Linux D7 runs; on Windows it is skipped by its assumption.

- [ ] **Step 5: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/GraphWorkspaceController.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceController.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceControllerShould.java
git commit -m "Add non-creating openExisting to the workspace controller [2026-09-10-most-recent-workspaces]"
```

## Task 4: Record opened and focused workspaces

**Implementer tier:** Advanced

**Lane:** controller

**Depends on:** Task 1, Task 2

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceController.java:1-50`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceController.java:55-100`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceController.java:208-235`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceController.java:250-280`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceController.java:300-330`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceController.java:615-625`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceController.java:695-710`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceController.java:1068-1120`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceControllerShould.java:1-60`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceControllerShould.java:1200-1250`

**Interfaces:**

- Consumes: `RecentWorkspaceList.record(Path): void` and `RecentWorkspaceList.mostRecentExisting(): Optional<Path>` from Task 1; `public RecentWorkspaceList(String storedValue, Consumer<String> persister)` from Task 1; `new RecentWorkspaceRecorder(GraphWorkspaceStore, RecentWorkspaceList)` and `RecentWorkspaceRecorder.close(): void` from Task 2; the private `open(Path workspaceFile, boolean createMissing)` overload and the focus/`finishOpen` structure created by Task 3 in this same lane; existing `GraphWorkspaceStore.addListener`, `GraphWorkspaceStore.create/open`, and `WorkspaceCreationOwnership.capture`.
- Produces: `public DefaultGraphWorkspaceController(ModeController modeController, GraphWorkspaceViewFactory viewFactory, RecentWorkspaceList recentWorkspaces)`, the surviving `public DefaultGraphWorkspaceController(ModeController, GraphWorkspaceViewFactory)` defaulting to `RecentWorkspaceList.empty()`, the package-private `DefaultGraphWorkspaceController(WorkspaceSessionRegistry, SessionFactory, GraphWorkspaceViewFactory, RecentWorkspaceList)`, the surviving package-private 3-argument form defaulting to `RecentWorkspaceList.empty()`, the 9-argument canonical `SessionResources(GraphWorkspaceStore, WorkspaceMapCoordinator, GraphUpdateCoordinator, MapLeaseManager, GraphCommandRouter, ScheduledExecutorService, boolean, WorkspaceCreationOwnership, RecentWorkspaceRecorder)`, and recorder teardown through `closeRemainingResources(ResourceSet)`.

- [ ] **Step 1: Write the failing tests D1-D3 and D9-D11**

Add these imports to `DefaultGraphWorkspaceControllerShould.java` (after the `org.freeplane.plugin.graph.workspace.GraphWorkspaceStore` import):

```java
import org.freeplane.plugin.graph.workspace.RecentWorkspaceList;
import org.freeplane.plugin.graph.workspace.RecentWorkspaceRecorder;
```

Insert these six test methods into `DefaultGraphWorkspaceControllerShould.java` immediately before `private static void awaitPathPresent(...)`:

```java
    @Test
    public void recordsAnOpenedWorkspaceInTheRecentList() throws Exception {
        Path workspace = temporaryFolder.newFile("recorded-open.fpg").toPath().toRealPath();
        WorkspaceSessionRegistry sessions = new WorkspaceSessionRegistry();
        DefaultGraphWorkspaceController.SessionResources resources = resources(false);
        AtomicReference<String> persisted = new AtomicReference<String>();
        RecentWorkspaceList list = new RecentWorkspaceList("", persisted::set);
        DefaultGraphWorkspaceController controller = new DefaultGraphWorkspaceController(sessions,
            (path, id, create) -> resources, (handle, binding, close) -> new RecordingView(), list);

        controller.open(workspace);

        assertThat(list.mostRecentExisting()).contains(workspace);
        assertThat(persisted.get()).contains(workspace.toString());
    }

    @Test
    public void recordsAgainWhenAnAlreadyOpenWorkspaceIsFocusedAfterClear() throws Exception {
        Path workspace = temporaryFolder.newFile("refocused.fpg").toPath().toRealPath();
        WorkspaceSessionRegistry sessions = new WorkspaceSessionRegistry();
        DefaultGraphWorkspaceController.SessionResources resources = resources(false);
        AtomicReference<String> persisted = new AtomicReference<String>();
        RecentWorkspaceList list = new RecentWorkspaceList("", persisted::set);
        DefaultGraphWorkspaceController controller = new DefaultGraphWorkspaceController(sessions,
            (path, id, create) -> resources, (handle, binding, close) -> new RecordingView(), list);
        controller.open(workspace);
        list.clear();
        assertThat(list.mostRecentExisting()).isEmpty();

        controller.open(workspace);

        assertThat(list.mostRecentExisting()).contains(workspace);
    }

    @Test
    public void aFailingPersisterDoesNotFailTheOpenOrHideTheWindow() throws Exception {
        Path workspace = temporaryFolder.newFile("failing-persister-open.fpg").toPath().toRealPath();
        WorkspaceSessionRegistry sessions = new WorkspaceSessionRegistry();
        DefaultGraphWorkspaceController.SessionResources resources = resources(false);
        RecordingView view = new RecordingView();
        RecentWorkspaceList list = new RecentWorkspaceList("", value -> {
            throw new IllegalStateException("persister failed");
        });
        DefaultGraphWorkspaceController controller = new DefaultGraphWorkspaceController(sessions,
            (path, id, create) -> resources, (handle, binding, close) -> view, list);

        GraphWorkspaceHandle handle = controller.open(workspace);

        assertThat(handle).isNotNull();
        assertThat(view.showCount).hasValue(1);
    }

    @Test
    public void closesTheSessionRecorderWhenTheSessionCloses() throws Exception {
        Path workspace = temporaryFolder.newFile("recorder-close.fpg").toPath().toRealPath();
        WorkspaceSessionRegistry sessions = new WorkspaceSessionRegistry();
        RecentWorkspaceRecorder recorder = mock(RecentWorkspaceRecorder.class);
        DefaultGraphWorkspaceController.SessionResources resources = resources(false, recorder);
        AtomicReference<WorkspaceCloseController> close = new AtomicReference<WorkspaceCloseController>();
        DefaultGraphWorkspaceController controller = new DefaultGraphWorkspaceController(sessions,
            (path, id, create) -> resources, (handle, binding, closeController) -> {
                close.set(closeController);
                return new RecordingView();
            }, new RecentWorkspaceList("", value -> { }));
        controller.open(workspace);

        assertThat(close.get().saveAndClose()).isTrue();

        verify(recorder).close();
    }

    @Test
    public void closesTheSessionRecorderOnShutdown() throws Exception {
        Path workspace = temporaryFolder.newFile("recorder-shutdown.fpg").toPath().toRealPath();
        WorkspaceSessionRegistry sessions = new WorkspaceSessionRegistry();
        RecentWorkspaceRecorder recorder = mock(RecentWorkspaceRecorder.class);
        DefaultGraphWorkspaceController.SessionResources resources = resources(false, recorder);
        DefaultGraphWorkspaceController controller = new DefaultGraphWorkspaceController(sessions,
            (path, id, create) -> resources, (handle, binding, close) -> new RecordingView(),
            new RecentWorkspaceList("", value -> { }));
        controller.open(workspace);

        controller.shutdown();

        verify(recorder).close();
    }

    @Test
    public void keepsTheSessionRecorderOpenOnSaveFailureAndClosesItOnRetry() throws Exception {
        Path workspace = temporaryFolder.newFile("recorder-retry.fpg").toPath().toRealPath();
        WorkspaceSessionRegistry sessions = new WorkspaceSessionRegistry();
        RecentWorkspaceRecorder recorder = mock(RecentWorkspaceRecorder.class);
        DefaultGraphWorkspaceController.SessionResources resources = resources(false, recorder);
        AtomicReference<WorkspaceCloseController> close = new AtomicReference<WorkspaceCloseController>();
        DefaultGraphWorkspaceController controller = new DefaultGraphWorkspaceController(sessions,
            (path, id, create) -> resources, (handle, binding, closeController) -> {
                close.set(closeController);
                return new RecordingView();
            }, new RecentWorkspaceList("", value -> { }));
        controller.open(workspace);
        doThrow(new IllegalStateException("save failed")).when(resources.store).close();

        assertThat(close.get().saveAndClose()).isFalse();
        verify(recorder, never()).close();

        org.mockito.Mockito.doNothing().when(resources.store).close();
        assertThat(close.get().retrySaveAndClose()).isTrue();
        verify(recorder).close();
    }
```

Add this helper immediately before `private DefaultGraphWorkspaceController.SessionResources resources(boolean newlyCreated)`:

```java
    private DefaultGraphWorkspaceController.SessionResources resources(boolean newlyCreated,
            RecentWorkspaceRecorder recorder) {
        GraphWorkspaceStore store = mock(GraphWorkspaceStore.class);
        when(store.addListener(any())).thenReturn(mock(ListenerRegistration.class));
        return new DefaultGraphWorkspaceController.SessionResources(store, null,
            mock(GraphUpdateCoordinator.class), mock(MapLeaseManager.class), mock(GraphCommandRouter.class),
            mock(ScheduledExecutorService.class), newlyCreated, null, recorder);
    }
```

- [ ] **Step 2: Run the new tests and confirm they fail**

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.control.DefaultGraphWorkspaceControllerShould'
```

Expected: FAIL, compilation error `constructor SessionResources cannot be applied to given types` and `DefaultGraphWorkspaceController cannot be applied to given types`.

- [ ] **Step 3: Add the recorder plumbing and recording points**

Add to `DefaultGraphWorkspaceController.java` next to the existing `WorkspaceUriResolver` import:

```java
import org.freeplane.plugin.graph.workspace.RecentWorkspaceList;
import org.freeplane.plugin.graph.workspace.RecentWorkspaceRecorder;
```

Replace the whole `SessionResources` class (lines 55-96) with:

```java
    static final class SessionResources {
        final GraphWorkspaceStore store;
        final WorkspaceMapCoordinator maps;
        final GraphUpdateCoordinator updates;
        final MapLeaseManager leaseManager;
        final GraphCommandRouter router;
        final ScheduledExecutorService scheduler;
        final boolean newlyCreated;
        final WorkspaceCreationOwnership creationOwnership;
        final RecentWorkspaceRecorder recorder;

        SessionResources(final GraphWorkspaceStore store, final GraphUpdateCoordinator updates,
                final MapLeaseManager leaseManager, final GraphCommandRouter router,
                final ScheduledExecutorService scheduler, final boolean newlyCreated) {
            this(store, null, updates, leaseManager, router, scheduler, newlyCreated, null, null);
        }

        SessionResources(final GraphWorkspaceStore store, final WorkspaceMapCoordinator maps,
                final GraphUpdateCoordinator updates, final MapLeaseManager leaseManager,
                final GraphCommandRouter router, final ScheduledExecutorService scheduler,
                final boolean newlyCreated) {
            this(store, maps, updates, leaseManager, router, scheduler, newlyCreated, null, null);
        }

        SessionResources(final GraphWorkspaceStore store, final WorkspaceMapCoordinator maps,
                final GraphUpdateCoordinator updates, final MapLeaseManager leaseManager,
                final GraphCommandRouter router, final ScheduledExecutorService scheduler,
                final boolean newlyCreated, final WorkspaceCreationOwnership creationOwnership) {
            this(store, maps, updates, leaseManager, router, scheduler, newlyCreated, creationOwnership, null);
        }

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
    }
```

Replace the whole `ResourceSet` class (lines 208-238) with:

```java
    private static final class ResourceSet {
        private final GraphWorkspaceStore store;
        private final WorkspaceMapCoordinator maps;
        private final GraphUpdateCoordinator updates;
        private final MapLeaseManager leaseManager;
        private final ScheduledExecutorService scheduler;
        private final boolean newlyCreated;
        private final WorkspaceCreationOwnership creationOwnership;
        private final RecentWorkspaceRecorder recorder;

        private ResourceSet(final GraphWorkspaceStore store, final WorkspaceMapCoordinator maps,
                final GraphUpdateCoordinator updates, final MapLeaseManager leaseManager,
                final ScheduledExecutorService scheduler, final boolean newlyCreated,
                final WorkspaceCreationOwnership creationOwnership, final RecentWorkspaceRecorder recorder) {
            this.store = store;
            this.maps = maps;
            this.updates = updates;
            this.leaseManager = leaseManager;
            this.scheduler = scheduler;
            this.newlyCreated = newlyCreated;
            this.creationOwnership = creationOwnership;
            this.recorder = recorder;
        }

        private static ResourceSet from(final SessionResources resources, final Path path) {
            return new ResourceSet(resources.store, resources.maps, resources.updates, resources.leaseManager,
                resources.scheduler, resources.newlyCreated,
                resources.creationOwnership != null ? resources.creationOwnership
                    : resources.newlyCreated ? WorkspaceCreationOwnership.capture(path) : null,
                resources.recorder);
        }

        private static ResourceSet from(final SessionResources resources) {
            return new ResourceSet(resources.store, resources.maps, resources.updates, resources.leaseManager,
                resources.scheduler, resources.newlyCreated, resources.creationOwnership, resources.recorder);
        }
    }
```

Add the new field and replace the constructors (lines 250-279) with:

```java
    private final Object monitor = new Object();
    private final WorkspaceSessionRegistry sessions;
    private final SessionFactory sessionFactory;
    private final GraphWorkspaceViewFactory viewFactory;
    private final RecentWorkspaceList recentWorkspaces;
    private final WorkspaceUriResolver uriResolver = new WorkspaceUriResolver();
    private final Map<WorkspaceSessionId, Session> openSessions =
        new HashMap<WorkspaceSessionId, Session>();
    private boolean shutdownStarted;
    private boolean shutdownCompleted;
    private RuntimeException shutdownFailure;

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

In the private `open(Path, boolean)` loop, replace:

```java
                if (existing.awaitOpen()) {
                    if (existing.focus()) {
                        return existing.handle;
                    }
                    continue;
                }
```

with:

```java
                if (existing.awaitOpen()) {
                    if (existing.focus()) {
                        recordRecentWorkspace(path);
                        return existing.handle;
                    }
                    continue;
                }
```

Immediately after the private `open(Path, boolean)` method, add:

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

In `finishOpen`, replace:

```java
            session.publishOpen();
            return handle;
```

with:

```java
            session.publishOpen();
            recordRecentWorkspace(path);
            return handle;
```

Replace `closeRemainingResources(ResourceSet)` (lines 697-701) with:

```java
    private static RuntimeException closeRemainingResources(final ResourceSet resources) {
        if (resources == null) {
            return null;
        }
        RuntimeException failure = null;
        if (resources.recorder != null) {
            try {
                resources.recorder.close();
            }
            catch (RuntimeException exception) {
                failure = recordFailure(failure, exception);
            }
        }
        failure = recordFailure(failure, closeRemainingResources(resources.updates, resources.maps,
            resources.leaseManager, resources.scheduler));
        return failure;
    }
```

Replace `ProductionSessionFactory` (lines 1068-1120) with:

```java
    private static final class ProductionSessionFactory implements SessionFactory {
        private final ModeController modeController;
        private final WorkspaceSessionRegistry sessions;
        private final RecentWorkspaceList recentWorkspaces;

        private ProductionSessionFactory(final ModeController modeController,
                final WorkspaceSessionRegistry sessions, final RecentWorkspaceList recentWorkspaces) {
            this.modeController = Objects.requireNonNull(modeController, "modeController");
            this.sessions = Objects.requireNonNull(sessions, "sessions");
            this.recentWorkspaces = Objects.requireNonNull(recentWorkspaces, "recentWorkspaces");
        }

        @Override
        public SessionResources open(final Path path, final WorkspaceSessionId sessionId, final boolean create) {
            final ScheduledExecutorService scheduler = newStoreScheduler();
            GraphWorkspaceStore store = null;
            MapLeaseManager leaseManager = null;
            WorkspaceMapCoordinator maps = null;
            GraphUpdateCoordinator updates = null;
            WorkspaceCreationOwnership creationOwnership = null;
            try {
                final WorkspaceXmlCodec codec = new WorkspaceXmlCodec(
                    new WorkspaceMigrationRegistry(Collections.emptyList()));
                final AtomicWorkspaceWriter writer = AtomicWorkspaceWriter.standard();
                store = create ? GraphWorkspaceStore.create(path, codec, writer, scheduler)
                    : GraphWorkspaceStore.open(path, codec, writer, scheduler);
                if (create) {
                    creationOwnership = WorkspaceCreationOwnership.capture(path);
                }
                final RecentWorkspaceRecorder recorder = new RecentWorkspaceRecorder(store, recentWorkspaces);
                leaseManager = new MapLeaseManager(path, modeController);
                maps = new WorkspaceMapCoordinator(store, leaseManager);
                updates = new GraphUpdateCoordinator(maps, store, leaseManager, new ProjectionEngine(),
                    new LayoutSettleLoop(store.currentDocument().id()));
                final EdtExecutor edt = new SwingEdtExecutor();
                final ViewMaterializationTracker views = new ViewMaterializationTracker(modeController);
                final FreeplaneMapCommandExecutor mapCommands = new FreeplaneMapCommandExecutor(store, maps::find,
                    modeController, edt, views);
                final SourceNavigation navigation = new SourceNavigation(store, maps::find, modeController, edt, views);
                final DefaultPurgeCommandHandler purge = new DefaultPurgeCommandHandler(updates, store, edt);
                final DefaultContributorDeletionHandler deletion =
                    new DefaultContributorDeletionHandler(updates, store, mapCommands, edt);
                final GraphCommandRouter router = new GraphCommandRouter(store, maps, mapCommands, navigation,
                    updates, sessions, sessionId, purge, deletion);
                updates.start();
                return new SessionResources(store, maps, updates, leaseManager, router, scheduler, create,
                    creationOwnership, recorder);
            }
            catch (RuntimeException failure) {
                throw new SessionConstructionException(failure,
                    new ResourceSet(store, maps, updates, leaseManager, scheduler, create, creationOwnership, null));
            }
        }
    }
```

- [ ] **Step 4: Run the tests and confirm they pass**

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  gradle :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks \
  --tests 'org.freeplane.plugin.graph.control.DefaultGraphWorkspaceControllerShould'
```

Expected: PASS, all existing tests plus D1-D3 and D9-D11.

- [ ] **Step 5: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceController.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceControllerShould.java
git commit -m "Record opened and focused workspaces in the recent list [2026-09-10-most-recent-workspaces]"
```

## Task 5: Recent Workspaces submenu in the graph workspace window

**Implementer tier:** Advanced

**Lane:** window

**Depends on:** Task 1, Task 3

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java:1-80`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java:92-160`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java:296-380`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java:1024-1095`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java:1338-1365`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java:1-100`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java:1043-1100`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java:1333-1400`

**Interfaces:**

- Consumes: `RecentWorkspaceList.empty()`, `RecentWorkspaceList.displayEntries(): List<Entry>`, `RecentWorkspaceList.hasStoredEntries(): boolean`, `RecentWorkspaceList.clear(): void`, `RecentWorkspaceList.Entry.path(): Path`, `RecentWorkspaceList.Entry.label(): String`, and `public RecentWorkspaceList(String storedValue, Consumer<String> persister)` from Task 1; `GraphWorkspaceController.openExisting(Path): GraphWorkspaceHandle` from Task 3; existing `TextUtils.getText(String): String` and `TextUtils.format(String, Object...): String`.
- Produces: package-private `GraphWorkspaceWindow(GraphWorkspaceHandle, GraphWorkspaceViewBinding, WorkspaceCloseController, GraphWorkspaceController, Supplier<Path>, RecentWorkspaceList)`, package-private `HeadlessGraphWorkspaceView(GraphWorkspaceHandle, GraphWorkspaceViewBinding, WorkspaceCloseController, GraphWorkspaceController, Supplier<Path>, RecentWorkspaceList)`, package-private `GraphWorkspaceWindowModel(GraphWorkspaceHandle, GraphWorkspaceViewBinding, GraphWorkspaceController, Supplier<Path>, WorkspaceCloseController, Runnable, Runnable, Runnable, Consumer<String>, RecentWorkspaceList)`, the surviving 9-argument `GraphWorkspaceWindowModel` constructor, and package-private `void rebuildRecentWorkspacesMenu()`.

- [ ] **Step 1: Write the failing tests C1-C10**

In `GraphWorkspaceWindowModelShould.java`, add these imports in their alphabetical positions:

```java
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.util.function.Supplier;

import javax.swing.JSeparator;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import org.freeplane.plugin.graph.control.GraphWorkspaceOpenException;
import org.freeplane.plugin.graph.workspace.RecentWorkspaceList;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import static org.mockito.Mockito.never;
```

Add the `TemporaryFolder` rule directly after `private MockedStatic<ResourceController> resourceController;`:

```java
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();
```

Insert these test methods immediately before `private static GraphCommandResult commandResult(final WorkspaceTransition transition)`:

```java
    @Test
    public void placesRecentWorkspacesMenuBetweenSaveAsAndClose() {
        Fixture fixture = fixture(RecentWorkspaceList.empty());
        GraphWorkspaceWindowModel model = fixture.model();

        JMenu file = menu(model, "graph-workspace-file-menu");

        assertThat(file.getMenuComponentCount()).isEqualTo(6);
        assertThat(file.getMenuComponent(2).getName()).isEqualTo("graph-workspace-menu-item-save-as");
        assertThat(file.getMenuComponent(3)).isInstanceOf(JMenu.class);
        JMenu recents = (JMenu) file.getMenuComponent(3);
        assertThat(recents.getName()).isEqualTo("graph-workspace-recent-workspaces-menu");
        assertThat(recents.getText()).isEqualTo("graph_workspace.menu.recent_workspaces");
        assertThat(file.getMenuComponent(4)).isInstanceOf(JSeparator.class);
        assertThat(file.getMenuComponent(5).getName()).isEqualTo("graph-workspace-menu-item-close");
        model.close();
    }

    @Test
    public void rebuildsRecentEntriesInOrderWithLabels() throws Exception {
        Path first = temporaryFolder.newFile("menu-first.fpg").toPath().toRealPath();
        Path second = temporaryFolder.newFile("menu-second.fpg").toPath().toRealPath();
        RecentWorkspaceList list = recentList(first, second);
        Fixture fixture = fixture(list);
        GraphWorkspaceWindowModel model = fixture.model();
        List<RecentWorkspaceList.Entry> entries = list.displayEntries();

        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                model.rebuildRecentWorkspacesMenu();
                JMenu recents = menu(model, "graph-workspace-recent-workspaces-menu");
                assertThat(recents.isEnabled()).isTrue();
                assertThat(recents.getMenuComponentCount()).isEqualTo(4);
                assertThat(((JMenuItem) recents.getMenuComponent(0)).getName())
                    .isEqualTo("graph-workspace-recent-workspace-0");
                assertThat(((JMenuItem) recents.getMenuComponent(0)).getText())
                    .isEqualTo(entries.get(0).label());
                assertThat(((JMenuItem) recents.getMenuComponent(1)).getName())
                    .isEqualTo("graph-workspace-recent-workspace-1");
                assertThat(((JMenuItem) recents.getMenuComponent(1)).getText())
                    .isEqualTo(entries.get(1).label());
                assertThat(recents.getMenuComponent(2)).isInstanceOf(JSeparator.class);
                JMenuItem clear = (JMenuItem) recents.getMenuComponent(3);
                assertThat(clear.getName()).isEqualTo("graph-workspace-recent-workspaces-clear");
                assertThat(clear.isEnabled()).isTrue();
            }
        });
        model.close();
    }

    @Test
    public void limitsRebuiltEntriesToTheDisplayCap() throws Exception {
        RecentWorkspaceList list = new RecentWorkspaceList("", value -> { });
        for (int index = 0; index < 10; index++) {
            list.record(temporaryFolder.newFile("menu-cap-" + index + ".fpg").toPath().toRealPath());
        }
        Fixture fixture = fixture(list);
        GraphWorkspaceWindowModel model = fixture.model();

        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                model.rebuildRecentWorkspacesMenu();
                JMenu recents = menu(model, "graph-workspace-recent-workspaces-menu");
                assertThat(recents.getMenuComponentCount()).isEqualTo(10);
                for (int index = 0; index < RecentWorkspaceList.DISPLAY_CAPACITY; index++) {
                    assertThat(((JMenuItem) recents.getMenuComponent(index)).getName())
                        .isEqualTo("graph-workspace-recent-workspace-" + index);
                }
                assertThat(recents.getMenuComponent(8)).isInstanceOf(JSeparator.class);
                assertThat(((JMenuItem) recents.getMenuComponent(9)).getName())
                    .isEqualTo("graph-workspace-recent-workspaces-clear");
            }
        });
        model.close();
    }

    @Test
    public void hidesMissingEntriesFromTheRebuiltMenu() throws Exception {
        Path existing = temporaryFolder.newFile("menu-existing.fpg").toPath().toRealPath();
        Path missing = temporaryFolder.newFile("menu-missing.fpg").toPath().toRealPath();
        RecentWorkspaceList list = recentList(existing, missing);
        Files.delete(missing);
        Fixture fixture = fixture(list);
        GraphWorkspaceWindowModel model = fixture.model();
        String label = list.displayEntries().get(0).label();

        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                model.rebuildRecentWorkspacesMenu();
                JMenu recents = menu(model, "graph-workspace-recent-workspaces-menu");
                assertThat(recents.getMenuComponentCount()).isEqualTo(3);
                JMenuItem entry = (JMenuItem) recents.getMenuComponent(0);
                assertThat(entry.getName()).isEqualTo("graph-workspace-recent-workspace-0");
                assertThat(entry.getText()).isEqualTo(label);
                assertThat(recents.getMenuComponent(1)).isInstanceOf(JSeparator.class);
                assertThat(((JMenuItem) recents.getMenuComponent(2)).getName())
                    .isEqualTo("graph-workspace-recent-workspaces-clear");
            }
        });
        model.close();
    }

    @Test
    public void showsDisabledEmptyRowWithoutClearWhenNothingIsStored() {
        Fixture fixture = fixture(RecentWorkspaceList.empty());
        GraphWorkspaceWindowModel model = fixture.model();

        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                JMenu recents = menu(model, "graph-workspace-recent-workspaces-menu");
                recents.setEnabled(false);

                model.rebuildRecentWorkspacesMenu();

                assertThat(recents.isEnabled()).isTrue();
                assertThat(recents.getMenuComponentCount()).isEqualTo(1);
                JMenuItem empty = (JMenuItem) recents.getMenuComponent(0);
                assertThat(empty.getName()).isEqualTo("graph-workspace-recent-workspaces-empty");
                assertThat(empty.getText()).isEqualTo("graph_workspace.recent_workspaces.empty");
                assertThat(empty.isEnabled()).isFalse();
            }
        });
        model.close();
    }

    @Test
    public void keepsClearAndTheMenuEnabledWhenOnlyHiddenEntriesAreStored() throws Exception {
        Path missing = temporaryFolder.newFile("menu-hidden.fpg").toPath().toRealPath();
        RecentWorkspaceList list = recentList(missing);
        Files.delete(missing);
        Fixture fixture = fixture(list);
        GraphWorkspaceWindowModel model = fixture.model();

        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                JMenu recents = menu(model, "graph-workspace-recent-workspaces-menu");
                recents.setEnabled(false);

                model.rebuildRecentWorkspacesMenu();

                assertThat(recents.isEnabled()).isTrue();
                assertThat(recents.getMenuComponentCount()).isEqualTo(3);
                JMenuItem empty = (JMenuItem) recents.getMenuComponent(0);
                assertThat(empty.getName()).isEqualTo("graph-workspace-recent-workspaces-empty");
                assertThat(empty.isEnabled()).isFalse();
                assertThat(recents.getMenuComponent(1)).isInstanceOf(JSeparator.class);
                JMenuItem clear = (JMenuItem) recents.getMenuComponent(2);
                assertThat(clear.getName()).isEqualTo("graph-workspace-recent-workspaces-clear");
                assertThat(clear.isEnabled()).isTrue();
            }
        });
        model.close();
    }

    @Test
    public void clearsTheListAndRebuildsWhenClearIsClicked() throws Exception {
        Path existing = temporaryFolder.newFile("menu-clear.fpg").toPath().toRealPath();
        RecentWorkspaceList list = recentList(existing);
        Fixture fixture = fixture(list);
        GraphWorkspaceWindowModel model = fixture.model();

        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                model.rebuildRecentWorkspacesMenu();
                JMenu recents = menu(model, "graph-workspace-recent-workspaces-menu");
                JMenuItem clear = (JMenuItem) recents.getMenuComponent(2);
                assertThat(clear.getName()).isEqualTo("graph-workspace-recent-workspaces-clear");

                clear.doClick();

                assertThat(list.hasStoredEntries()).isFalse();
                assertThat(recents.getMenuComponentCount()).isEqualTo(1);
                assertThat(((JMenuItem) recents.getMenuComponent(0)).getName())
                    .isEqualTo("graph-workspace-recent-workspaces-empty");
            }
        });
        model.close();
    }

    @Test
    public void reportsAFailedOpenFromTheMenuAndNeverCreatesAWorkspace() throws Exception {
        Path existing = temporaryFolder.newFile("menu-failing.fpg").toPath().toRealPath();
        RecentWorkspaceList list = recentList(existing);
        Fixture fixture = fixture(list);
        List<String> messages = new ArrayList<String>();
        GraphWorkspaceWindowModel model = fixture.model(messages::add);
        when(fixture.applicationController.openExisting(any(Path.class)))
            .thenThrow(new GraphWorkspaceOpenException(existing, new IllegalStateException("missing")));
        AtomicReference<JMenuItem> entry = new AtomicReference<JMenuItem>();

        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                model.rebuildRecentWorkspacesMenu();
                JMenu recents = menu(model, "graph-workspace-recent-workspaces-menu");
                assertThat(recents.getMenuComponentCount()).isEqualTo(3);
                entry.set((JMenuItem) recents.getMenuComponent(0));
            }
        });

        Files.delete(existing);

        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                entry.get().doClick();

                assertThat(messages).containsExactly(
                    "graph_workspace.recent_workspaces.open_failed[" + existing + "]");
                JMenu recents = menu(model, "graph-workspace-recent-workspaces-menu");
                assertThat(recents.getMenuComponentCount()).isEqualTo(3);
                assertThat(((JMenuItem) recents.getMenuComponent(0)).getName())
                    .isEqualTo("graph-workspace-recent-workspaces-empty");
                assertThat(((JMenuItem) recents.getMenuComponent(2)).getName())
                    .isEqualTo("graph-workspace-recent-workspaces-clear");
            }
        });
        verify(fixture.applicationController, never()).open(any());
        model.close();
    }

    @Test
    public void rebuildsTheMenuFromThePopupListener() throws Exception {
        Path existing = temporaryFolder.newFile("menu-popup.fpg").toPath().toRealPath();
        RecentWorkspaceList list = recentList(existing);
        Fixture fixture = fixture(list);
        GraphWorkspaceWindowModel model = fixture.model();

        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                JMenu recents = menu(model, "graph-workspace-recent-workspaces-menu");

                recentsPopupListener(recents).popupMenuWillBecomeVisible(
                    new PopupMenuEvent(recents.getPopupMenu()));

                assertThat(recents.getMenuComponentCount()).isEqualTo(3);
                assertThat(((JMenuItem) recents.getMenuComponent(0)).getName())
                    .isEqualTo("graph-workspace-recent-workspace-0");
            }
        });
        model.close();
    }

    @Test
    public void keepsTheNineArgumentConstructorForTheUiEvidenceHarness() {
        Fixture fixture = fixture(RecentWorkspaceList.empty());
        final GraphWorkspaceWindowModel[] result = new GraphWorkspaceWindowModel[1];
        final EdtResources[] edtResources = new EdtResources[1];
        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                edtResources[0] = new EdtResources();
                try {
                    Constructor<GraphWorkspaceWindowModel> constructor =
                        GraphWorkspaceWindowModel.class.getDeclaredConstructor(
                            GraphWorkspaceHandle.class, GraphWorkspaceViewBinding.class,
                            GraphWorkspaceController.class, Supplier.class, WorkspaceCloseController.class,
                            Runnable.class, Runnable.class, Runnable.class, Consumer.class);
                    result[0] = constructor.newInstance(fixture.handle, fixture.binding,
                        fixture.applicationController, (Supplier<Path>) () -> OPEN_PATH,
                        fixture.closeController, (Runnable) () -> { }, (Runnable) () -> { },
                        (Runnable) () -> { }, (Consumer<String>) message -> { });
                }
                catch (ReflectiveOperationException failure) {
                    throw new AssertionError(failure);
                }
            }
        });
        RESOURCES.add(edtResources[0]);
        GraphWorkspaceWindowModel model = result[0];

        assertThat(model).isNotNull();
        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                model.rebuildRecentWorkspacesMenu();
                JMenu recents = menu(model, "graph-workspace-recent-workspaces-menu");
                assertThat(recents.getMenuComponentCount()).isEqualTo(1);
                assertThat(((JMenuItem) recents.getMenuComponent(0)).getName())
                    .isEqualTo("graph-workspace-recent-workspaces-empty");
            }
        });
        model.close();
    }
```

Add these helpers immediately after the existing `private static JMenuItem menuItem(final GraphWorkspaceWindowModel model, final String name)` helper:

```java
    private static RecentWorkspaceList recentList(final Path... paths) {
        RecentWorkspaceList list = new RecentWorkspaceList("", value -> { });
        for (Path path : paths) {
            list.record(path);
        }
        return list;
    }

    private static JMenu menu(final GraphWorkspaceWindowModel model, final String name) {
        for (int menuIndex = 0; menuIndex < model.menuBar().getMenuCount(); menuIndex++) {
            JMenu menu = model.menuBar().getMenu(menuIndex);
            if (name.equals(menu.getName())) {
                return menu;
            }
        }
        throw new AssertionError("Missing menu " + name);
    }

    private static PopupMenuListener recentsPopupListener(final JMenu menu) {
        PopupMenuListener[] listeners = menu.getPopupMenu().getPopupMenuListeners();
        for (PopupMenuListener listener : listeners) {
            if (!listener.getClass().getName().startsWith("javax.swing.")) {
                return listener;
            }
        }
        throw new AssertionError("Missing recent-workspaces popup listener");
    }
```

Replace the last `fixture(...)` overload (lines 1064-1082) with this chain:

```java
    private static Fixture fixture(Viewport viewport, CanvasState state,
            List<GraphWorkspaceViewBinding.MapRegistration> registrations, boolean readOnly,
            WorkspaceSessionStatus sessionStatus, GraphWorkspacePresentation presentation) {
        return fixture(viewport, state, registrations, readOnly, sessionStatus, presentation,
            RecentWorkspaceList.empty());
    }

    private static Fixture fixture(Viewport viewport, CanvasState state,
            List<GraphWorkspaceViewBinding.MapRegistration> registrations, boolean readOnly,
            WorkspaceSessionStatus sessionStatus, GraphWorkspacePresentation presentation,
            RecentWorkspaceList recentWorkspaces) {
        GraphWorkspaceController applicationController = mock(GraphWorkspaceController.class);
        GraphWorkspaceHandle handle = mock(GraphWorkspaceHandle.class);
        WorkspaceCloseController closeController = mock(WorkspaceCloseController.class);
        GraphWorkspaceViewBinding binding = mock(GraphWorkspaceViewBinding.class);
        ListenerRegistration registration = mock(ListenerRegistration.class);
        ListenerRegistration sessionRegistration = mock(ListenerRegistration.class);
        when(binding.currentViewport()).thenReturn(viewport);
        when(binding.currentCanvasState()).thenReturn(state);
        when(binding.currentMapRows()).thenReturn(registrations);
        when(binding.isReadOnly()).thenReturn(readOnly);
        when(binding.currentPresentation()).thenReturn(presentation);
        when(binding.currentSessionStatus()).thenReturn(sessionStatus);
        when(binding.addCanvasStateListener(any())).thenReturn(registration);
        when(binding.addSessionStatusListener(any())).thenReturn(sessionRegistration);
        return new Fixture(applicationController, handle, closeController, binding, registration,
            sessionRegistration, recentWorkspaces);
    }

    private static Fixture fixture(final RecentWorkspaceList recentWorkspaces) {
        return fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()), emptyState(),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false,
            WorkspaceSessionStatus.empty(), presentation(DisplaySettings.defaults(), ACTIVE_ID),
            recentWorkspaces);
    }
```

In the `Fixture` class, replace the field list, constructor, and `modelWithoutLayout(commandMessageSink)` construction with:

```java
    private static final class Fixture {
        private final GraphWorkspaceController applicationController;
        private final GraphWorkspaceHandle handle;
        private final WorkspaceCloseController closeController;
        private final GraphWorkspaceViewBinding binding;
        private final ListenerRegistration registration;
        private final ListenerRegistration sessionRegistration;
        private final RecentWorkspaceList recentWorkspaces;

        private Fixture(GraphWorkspaceController applicationController, GraphWorkspaceHandle handle,
                WorkspaceCloseController closeController, GraphWorkspaceViewBinding binding,
                ListenerRegistration registration, ListenerRegistration sessionRegistration,
                RecentWorkspaceList recentWorkspaces) {
            this.applicationController = applicationController;
            this.handle = handle;
            this.closeController = closeController;
            this.binding = binding;
            this.registration = registration;
            this.sessionRegistration = sessionRegistration;
            this.recentWorkspaces = recentWorkspaces;
        }
```

and inside `modelWithoutLayout(final Consumer<String> commandMessageSink)`, replace the model construction with:

```java
                    result[0] = new GraphWorkspaceWindowModel(handle, binding, applicationController,
                        () -> OPEN_PATH, closeController, () -> { }, () -> { }, () -> { }, commandMessageSink,
                        recentWorkspaces);
```

- [ ] **Step 2: Run the new tests and confirm they fail**

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould'
```

Expected: FAIL, compilation error `cannot find symbol: method rebuildRecentWorkspacesMenu()` and `constructor GraphWorkspaceWindowModel cannot be applied to given types`.

- [ ] **Step 3: Implement the window, headless view, and model constructors and the submenu**

In `GraphWorkspaceWindow.java`, add these imports next to the existing `java.util.function.Supplier` import and the Swing imports:

```java
import java.nio.file.Path;
```

```java
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
```

```java
import org.freeplane.plugin.graph.workspace.RecentWorkspaceList;
```

Turn the existing 5-argument `GraphWorkspaceWindow` constructor into the 6-argument canonical form by adding the sixth parameter:

```java
    GraphWorkspaceWindow(final GraphWorkspaceHandle handle, final GraphWorkspaceViewBinding binding,
            final WorkspaceCloseController closeController, final GraphWorkspaceController applicationController,
            final Supplier<java.nio.file.Path> pathChooser, final RecentWorkspaceList recentWorkspaces) {
```

and by passing the list as the tenth model argument:

```java
            }, message -> Controller.getCurrentController().getViewController().out(message), recentWorkspaces);
```

Add the new 5-argument delegate and keep the 4-argument delegate unchanged:

```java
    GraphWorkspaceWindow(final GraphWorkspaceHandle handle, final GraphWorkspaceViewBinding binding,
            final WorkspaceCloseController closeController, final GraphWorkspaceController applicationController,
            final Supplier<java.nio.file.Path> pathChooser) {
        this(handle, binding, closeController, applicationController, pathChooser, RecentWorkspaceList.empty());
    }
```

In `GraphWorkspaceWindowModel`, add the three fields next to the existing ones:

```java
    private final GraphWorkspaceController applicationController;
    private final RecentWorkspaceList recentWorkspaces;
```

```java
    private JMenu recentWorkspacesMenu;
```

Replace the 9-argument model constructor header and its two assignment lines so that the 9-argument form delegates and a new 10-argument canonical constructor holds the old body:

```java
    GraphWorkspaceWindowModel(final GraphWorkspaceHandle handle, final GraphWorkspaceViewBinding binding,
            final GraphWorkspaceController applicationController, final Supplier<java.nio.file.Path> pathChooser,
            final WorkspaceCloseController closeController, final Runnable closeRequest,
            final Runnable graphFocus, final Runnable closeCompletion, final Consumer<String> commandMessageSink) {
        this(handle, binding, applicationController, pathChooser, closeController, closeRequest, graphFocus,
            closeCompletion, commandMessageSink, RecentWorkspaceList.empty());
    }

    GraphWorkspaceWindowModel(final GraphWorkspaceHandle handle, final GraphWorkspaceViewBinding binding,
            final GraphWorkspaceController applicationController, final Supplier<java.nio.file.Path> pathChooser,
            final WorkspaceCloseController closeController, final Runnable closeRequest,
            final Runnable graphFocus, final Runnable closeCompletion, final Consumer<String> commandMessageSink,
            final RecentWorkspaceList recentWorkspaces) {
        this.handle = Objects.requireNonNull(handle, "handle");
        this.applicationController = Objects.requireNonNull(applicationController, "applicationController");
        this.recentWorkspaces = Objects.requireNonNull(recentWorkspaces, "recentWorkspaces");
        this.binding = Objects.requireNonNull(binding, "binding");
        this.closeController = Objects.requireNonNull(closeController, "closeController");
        this.closeRequest = Objects.requireNonNull(closeRequest, "closeRequest");
        this.closeCompletion = Objects.requireNonNull(closeCompletion, "closeCompletion");
        this.commandMessageSink = Objects.requireNonNull(commandMessageSink, "commandMessageSink");
        Objects.requireNonNull(pathChooser, "pathChooser");
```

Delete the now-duplicated `Objects.requireNonNull(applicationController, "applicationController");` line from the canonical body.

In `createMenuBar()`, replace:

```java
        fileSaveAsMenuItem = item("graph_workspace.action.save_as", "save-as",
            event -> toolbar.saveAsButton().doClick());
        file.add(fileSaveAsMenuItem);
        file.addSeparator();
        file.add(item("graph_workspace.action.close", "close", event -> closeRequest.run()));
```

with:

```java
        fileSaveAsMenuItem = item("graph_workspace.action.save_as", "save-as",
            event -> toolbar.saveAsButton().doClick());
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

Immediately after `createMenuBar()`, add:

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

Replace the `HeadlessGraphWorkspaceView` constructors (lines 1344-1361) with:

```java
    HeadlessGraphWorkspaceView(final GraphWorkspaceHandle handle, final GraphWorkspaceViewBinding binding,
            final WorkspaceCloseController closeController, final GraphWorkspaceController applicationController,
            final Supplier<java.nio.file.Path> pathChooser, final RecentWorkspaceList recentWorkspaces) {
        this.closeController = Objects.requireNonNull(closeController, "closeController");
        model = new GraphWorkspaceWindowModel(handle, binding, applicationController, pathChooser, closeController,
            new Runnable() {
                @Override
                public void run() {
                    requestClose();
                }
            }, null, new Runnable() {
                @Override
                public void run() {
                    close();
                }
            }, message -> { }, recentWorkspaces);
        model.completeInitialLayout();
    }

    HeadlessGraphWorkspaceView(final GraphWorkspaceHandle handle, final GraphWorkspaceViewBinding binding,
            final WorkspaceCloseController closeController, final GraphWorkspaceController applicationController,
            final Supplier<java.nio.file.Path> pathChooser) {
        this(handle, binding, closeController, applicationController, pathChooser, RecentWorkspaceList.empty());
    }
```

- [ ] **Step 4: Run the tests and confirm they pass**

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  gradle :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks \
  --tests 'org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould'
```

Expected: PASS, all existing model tests plus C1-C10.

Also run the UI evidence harness to prove the reflective 9-argument lookup still works; it rewrites the tracked evidence images, so discard them afterwards to stay inside the file change map:

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:graphUiEvidence
git checkout -- docs/superpowers/specs/images
```

Expected: `BUILD SUCCESSFUL`; no reflective-constructor failure.

- [ ] **Step 5: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java
git commit -m "Add the Recent Workspaces submenu to the graph window [2026-09-10-most-recent-workspaces]"
```

## Task 6: Recent resolution in OpenGraphWorkspaceAction

**Implementer tier:** Standard

**Lane:** window-action

**Depends on:** Task 1, Task 3

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/OpenGraphWorkspaceAction.java:1-41`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/OpenGraphWorkspaceActionShould.java`

**Interfaces:**

- Consumes: `RecentWorkspaceList.empty()`, `RecentWorkspaceList.mostRecentExisting(): Optional<Path>`, and `public RecentWorkspaceList(String storedValue, Consumer<String> persister)` from Task 1; `GraphWorkspaceController.openExisting(Path): GraphWorkspaceHandle` from Task 3; existing `GraphWorkspaceController.open(Path)`, `GraphWorkspaceWindow.chooseWorkspacePath(): Path`, `TextUtils.format(String, Object...): String`, and `Controller.getCurrentController().getViewController().out(String)`.
- Produces: `public OpenGraphWorkspaceAction(GraphWorkspaceController applicationController)`, `public OpenGraphWorkspaceAction(GraphWorkspaceController applicationController, RecentWorkspaceList recentWorkspaces)`, `public OpenGraphWorkspaceAction(GraphWorkspaceController applicationController, RecentWorkspaceList recentWorkspaces, Consumer<String> messageSink)`, the surviving package-private `OpenGraphWorkspaceAction(GraphWorkspaceController applicationController, Supplier<Path> pathChooser)`, and the package-private 4-argument test seam `OpenGraphWorkspaceAction(GraphWorkspaceController, RecentWorkspaceList, Supplier<Path>, Consumer<String>)`.

- [ ] **Step 1: Write the failing test class B1-B7**

Create `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/OpenGraphWorkspaceActionShould.java` with exactly this content:

```java
package org.freeplane.plugin.graph.window;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.event.ActionEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.mode.Controller;
import org.freeplane.plugin.graph.control.GraphWorkspaceController;
import org.freeplane.plugin.graph.control.GraphWorkspaceOpenException;
import org.freeplane.plugin.graph.workspace.RecentWorkspaceList;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;

public class OpenGraphWorkspaceActionShould {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();
    private MockedStatic<TextUtils> textUtils;
    private MockedStatic<ResourceController> resourceController;

    @Before
    public void setUp() {
        resourceController = org.mockito.Mockito.mockStatic(ResourceController.class);
        resourceController.when(ResourceController::getResourceController)
            .thenReturn(mock(ResourceController.class));
        textUtils = org.mockito.Mockito.mockStatic(TextUtils.class);
        textUtils.when(() -> TextUtils.getText(any(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        textUtils.when(() -> TextUtils.getText(any(String.class), any(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        textUtils.when(() -> TextUtils.getRawText(any(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        textUtils.when(() -> TextUtils.getRawText(any(String.class), any(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        textUtils.when(() -> TextUtils.format(any(String.class), any(Object[].class)))
            .thenAnswer(invocation -> formattedText(invocation));
    }

    @After
    public void tearDown() {
        textUtils.close();
        resourceController.close();
    }

    @Test
    public void opensTheMostRecentExistingEntryWithOpenExisting() throws Exception {
        Path existing = temporaryFolder.newFile("recent-open.fpg").toPath().toRealPath();
        GraphWorkspaceController controller = mock(GraphWorkspaceController.class);
        RecentWorkspaceList list = recentList(new AtomicReference<String>(), existing);
        AtomicInteger chooserCalls = new AtomicInteger();
        List<String> messages = new ArrayList<String>();
        OpenGraphWorkspaceAction action = new OpenGraphWorkspaceAction(controller, list,
            () -> {
                chooserCalls.incrementAndGet();
                return null;
            }, messages::add);

        action.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "open"));

        verify(controller).openExisting(existing);
        verify(controller, never()).open(any());
        assertThat(chooserCalls).hasValue(0);
        assertThat(messages).isEmpty();
    }

    @Test
    public void fallsThroughToTheNextExistingEntryWhenTheNewestIsMissing() throws Exception {
        Path existing = temporaryFolder.newFile("older-existing.fpg").toPath().toRealPath();
        Path missing = temporaryFolder.getRoot().toPath().resolve("missing-newest.fpg");
        GraphWorkspaceController controller = mock(GraphWorkspaceController.class);
        RecentWorkspaceList list = recentList(new AtomicReference<String>(), existing, missing);
        OpenGraphWorkspaceAction action = new OpenGraphWorkspaceAction(controller, list,
            () -> null, message -> { });

        action.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "open"));

        verify(controller).openExisting(existing);
    }

    @Test
    public void fallsBackToTheChooserWhenNoStoredEntryIsUsable() throws Exception {
        Path chosen = temporaryFolder.newFile("chooser-chosen.fpg").toPath().toRealPath();
        GraphWorkspaceController controller = mock(GraphWorkspaceController.class);
        OpenGraphWorkspaceAction action = new OpenGraphWorkspaceAction(controller,
            RecentWorkspaceList.empty(), () -> chosen, message -> { });

        action.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "open"));

        verify(controller).open(chosen);
        verify(controller, never()).openExisting(any());
    }

    @Test
    public void reportsAndFallsBackWhenTheRecentOpenThrowsGraphWorkspaceOpenException() throws Exception {
        Path recent = temporaryFolder.newFile("failing-recent.fpg").toPath().toRealPath();
        Path chosen = temporaryFolder.newFile("fallback.fpg").toPath().toRealPath();
        GraphWorkspaceController controller = mock(GraphWorkspaceController.class);
        RecentWorkspaceList list = recentList(new AtomicReference<String>(), recent);
        when(controller.openExisting(recent))
            .thenThrow(new GraphWorkspaceOpenException(recent, new IllegalStateException("gone")));
        List<String> messages = new ArrayList<String>();
        OpenGraphWorkspaceAction action = new OpenGraphWorkspaceAction(controller, list, () -> chosen, messages::add);

        action.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "open"));

        assertThat(messages).containsExactly(
            "graph_workspace.recent_workspaces.open_failed[" + recent + "]");
        verify(controller).open(chosen);
    }

    @Test
    public void reportsAndFallsBackWhenTheRecentOpenThrowsIllegalArgumentException() throws Exception {
        Path recent = temporaryFolder.newFile("illegal-recent.fpg").toPath().toRealPath();
        Path chosen = temporaryFolder.newFile("illegal-fallback.fpg").toPath().toRealPath();
        GraphWorkspaceController controller = mock(GraphWorkspaceController.class);
        RecentWorkspaceList list = recentList(new AtomicReference<String>(), recent);
        when(controller.openExisting(recent)).thenThrow(new IllegalArgumentException("bad path"));
        List<String> messages = new ArrayList<String>();
        OpenGraphWorkspaceAction action = new OpenGraphWorkspaceAction(controller, list, () -> chosen, messages::add);

        action.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "open"));

        assertThat(messages).containsExactly(
            "graph_workspace.recent_workspaces.open_failed[" + recent + "]");
        verify(controller).open(chosen);
    }

    @Test
    public void reportsAndFallsBackWhenTheRecentOpenThrowsIllegalStateException() throws Exception {
        Path recent = temporaryFolder.newFile("state-recent.fpg").toPath().toRealPath();
        Path chosen = temporaryFolder.newFile("state-fallback.fpg").toPath().toRealPath();
        GraphWorkspaceController controller = mock(GraphWorkspaceController.class);
        RecentWorkspaceList list = recentList(new AtomicReference<String>(), recent);
        when(controller.openExisting(recent)).thenThrow(new IllegalStateException("shut down"));
        List<String> messages = new ArrayList<String>();
        OpenGraphWorkspaceAction action = new OpenGraphWorkspaceAction(controller, list, () -> chosen, messages::add);

        action.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "open"));

        assertThat(messages).containsExactly(
            "graph_workspace.recent_workspaces.open_failed[" + recent + "]");
        verify(controller).open(chosen);
    }

    @Test
    public void constructsWithTheLazyDefaultMessageSink() {
        try (MockedStatic<Controller> controller = org.mockito.Mockito.mockStatic(Controller.class)) {
            controller.when(Controller::getCurrentController)
                .thenThrow(new AssertionError("Controller touched during construction"));

            OpenGraphWorkspaceAction first = new OpenGraphWorkspaceAction(mock(GraphWorkspaceController.class));
            OpenGraphWorkspaceAction second = new OpenGraphWorkspaceAction(mock(GraphWorkspaceController.class),
                RecentWorkspaceList.empty());

            assertThat(first.getKey()).isEqualTo(OpenGraphWorkspaceAction.KEY);
            assertThat(second.getKey()).isEqualTo(OpenGraphWorkspaceAction.KEY);
        }
    }

    private static RecentWorkspaceList recentList(final AtomicReference<String> persisted, final Path... paths) {
        RecentWorkspaceList list = new RecentWorkspaceList("", persisted::set);
        for (Path path : paths) {
            list.record(path);
        }
        return list;
    }

    private static String formattedText(final org.mockito.invocation.InvocationOnMock invocation) {
        final Object[] invocationArguments = invocation.getArguments();
        final Object[] formatArguments = invocationArguments[1] instanceof Object[]
            ? (Object[]) invocationArguments[1]
            : new Object[0];
        return invocationArguments[0] + java.util.Arrays.toString(formatArguments);
    }
}
```

- [ ] **Step 2: Run the new test and confirm it fails**

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.window.OpenGraphWorkspaceActionShould'
```

Expected: FAIL, compilation error `constructor OpenGraphWorkspaceAction cannot be applied to given types`.

- [ ] **Step 3: Implement the constructor set and recent resolution**

Replace the whole body of `OpenGraphWorkspaceAction.java` with:

```java
package org.freeplane.plugin.graph.window;

import java.awt.event.ActionEvent;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.ui.menubuilders.generic.UserRole;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.mode.Controller;
import org.freeplane.plugin.graph.control.GraphWorkspaceController;
import org.freeplane.plugin.graph.workspace.RecentWorkspaceList;

public final class OpenGraphWorkspaceAction extends AFreeplaneAction {
    public static final String KEY = "OpenGraphWorkspaceAction";
    private static final long serialVersionUID = 1L;

    private final GraphWorkspaceController applicationController;
    private final RecentWorkspaceList recentWorkspaces;
    private final Supplier<Path> pathChooser;
    private final Consumer<String> messageSink;

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

    @Override
    public void afterMapChange(final UserRole userRole) {
    }
}
```

- [ ] **Step 4: Run the tests and confirm they pass**

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  gradle :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks \
  --tests 'org.freeplane.plugin.graph.window.OpenGraphWorkspaceActionShould' \
  --tests 'org.freeplane.plugin.graph.window.GraphPluginIntegrationShould'
```

Expected: PASS, 7 new tests (B1-B7) plus the surviving `opensOnlyPathsSelectedByTheInjectedWorkspaceChooser` regression.

- [ ] **Step 5: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/OpenGraphWorkspaceAction.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/OpenGraphWorkspaceActionShould.java
git commit -m "Resolve Open Graph Workspace from the recent list [2026-09-10-most-recent-workspaces]"
```

## Task 7: Propagate the shared list through the view factory

**Implementer tier:** Standard

**Lane:** integration

**Depends on:** Task 1, Task 5

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/SwingGraphWorkspaceViewFactory.java:1-74`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphPluginIntegrationShould.java:1-40`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphPluginIntegrationShould.java:180-219`

**Interfaces:**

- Consumes: the package-private 6-argument `GraphWorkspaceWindow(GraphWorkspaceHandle, GraphWorkspaceViewBinding, WorkspaceCloseController, GraphWorkspaceController, Supplier<Path>, RecentWorkspaceList)` and the package-private 6-argument `HeadlessGraphWorkspaceView(GraphWorkspaceHandle, GraphWorkspaceViewBinding, WorkspaceCloseController, GraphWorkspaceController, Supplier<Path>, RecentWorkspaceList)` from Task 5; `RecentWorkspaceList.empty()`, `public RecentWorkspaceList(String storedValue, Consumer<String> persister)`, `RecentWorkspaceList.record(Path): void`, and `RecentWorkspaceList.DISPLAY_CAPACITY` from Task 1; existing `Viewport.of(double, double, double, List<UnknownXml>)`, `WorkspaceSessionStatus.empty()`, and `GraphWorkspaceWindow.runOnEdt(Runnable)`.
- Produces: `public SwingGraphWorkspaceViewFactory(GraphWorkspaceController applicationController, RecentWorkspaceList recentWorkspaces)`; the surviving public 1-argument form defaulting to `RecentWorkspaceList.empty()`; the surviving package-private `SwingGraphWorkspaceViewFactory(GraphWorkspaceController, Supplier<Path>)` defaulting to `RecentWorkspaceList.empty()`; and the package-private canonical `SwingGraphWorkspaceViewFactory(GraphWorkspaceController, Supplier<Path>, RecentWorkspaceList)` whose `create(GraphWorkspaceHandle, GraphWorkspaceViewBinding, WorkspaceCloseController): GraphWorkspaceView` passes the list to both the Swing window and the headless view.

- [ ] **Step 1: Write the failing tests F4 and F5**

In `GraphPluginIntegrationShould.java`, add these imports in their alphabetical positions:

```java
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.util.Collections;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import org.freeplane.plugin.graph.control.GraphWorkspaceHandle;
import org.freeplane.plugin.graph.control.GraphWorkspaceView;
import org.freeplane.plugin.graph.control.GraphWorkspaceViewBinding;
import org.freeplane.plugin.graph.control.WorkspaceCloseController;
import org.freeplane.plugin.graph.control.WorkspaceSessionStatus;
import org.freeplane.plugin.graph.workspace.RecentWorkspaceList;
import org.freeplane.plugin.graph.workspace.model.Viewport;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;
```

Add the `TemporaryFolder` rule directly after the `private MockedStatic<TextUtils> textUtils;` field:

```java
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();
```

Insert these two test methods and six helpers immediately before `private static ModeController configuredModeController()`:

```java
    @Test
    public void passesTheSharedListIntoTheCreatedHeadlessView() throws Exception {
        Assume.assumeTrue(GraphicsEnvironment.isHeadless());
        Path workspace = temporaryFolder.newFile("factory-headless.fpg").toPath().toRealPath();
        GraphWorkspaceController controller = mock(GraphWorkspaceController.class);
        GraphWorkspaceHandle handle = mock(GraphWorkspaceHandle.class);
        GraphWorkspaceViewBinding binding = modelBinding();
        WorkspaceCloseController close = mock(WorkspaceCloseController.class);
        RecentWorkspaceList list = new RecentWorkspaceList("", value -> { });
        list.record(workspace);

        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                try (MockedStatic<TextUtils> edtTextUtils = edtTextUtils();
                        MockedStatic<ResourceController> edtResourceController = edtResourceController()) {
                    GraphWorkspaceView view = new SwingGraphWorkspaceViewFactory(controller, list)
                        .create(handle, binding, close);
                    assertThat(view).isInstanceOf(HeadlessGraphWorkspaceView.class);
                    HeadlessGraphWorkspaceView headless = (HeadlessGraphWorkspaceView) view;
                    headless.model().rebuildRecentWorkspacesMenu();
                    JMenu recents = recentsMenu(headless.model().menuBar());
                    assertThat(recents.getMenuComponentCount()).isEqualTo(3);
                    assertThat(((JMenuItem) recents.getMenuComponent(0)).getName())
                        .isEqualTo("graph-workspace-recent-workspace-0");
                    view.close();
                }
            }
        });

        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                try (MockedStatic<TextUtils> edtTextUtils = edtTextUtils();
                        MockedStatic<ResourceController> edtResourceController = edtResourceController()) {
                    GraphWorkspaceView view = new SwingGraphWorkspaceViewFactory(controller)
                        .create(handle, binding, close);
                    HeadlessGraphWorkspaceView headless = (HeadlessGraphWorkspaceView) view;
                    headless.model().rebuildRecentWorkspacesMenu();
                    JMenu recents = recentsMenu(headless.model().menuBar());
                    assertThat(recents.getMenuComponentCount()).isEqualTo(1);
                    assertThat(((JMenuItem) recents.getMenuComponent(0)).getName())
                        .isEqualTo("graph-workspace-recent-workspaces-empty");
                    view.close();
                }
            }
        });
    }

    @Test
    public void passesTheSharedListIntoTheCreatedSwingWindow() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        Path workspace = temporaryFolder.newFile("factory-window.fpg").toPath().toRealPath();
        GraphWorkspaceController controller = mock(GraphWorkspaceController.class);
        GraphWorkspaceHandle handle = mock(GraphWorkspaceHandle.class);
        GraphWorkspaceViewBinding binding = modelBinding();
        WorkspaceCloseController close = mock(WorkspaceCloseController.class);
        RecentWorkspaceList list = new RecentWorkspaceList("", value -> { });
        list.record(workspace);

        GraphWorkspaceWindow.runOnEdt(new Runnable() {
            @Override
            public void run() {
                try (MockedStatic<TextUtils> edtTextUtils = edtTextUtils();
                        MockedStatic<ResourceController> edtResourceController = edtResourceController()) {
                    GraphWorkspaceView view = new SwingGraphWorkspaceViewFactory(controller, list)
                        .create(handle, binding, close);
                    assertThat(view).isInstanceOf(GraphWorkspaceWindow.class);
                    GraphWorkspaceWindow window = (GraphWorkspaceWindow) view;
                    JMenu recents = recentsMenu(window.getJMenuBar());
                    recentsPopupListener(recents).popupMenuWillBecomeVisible(
                        new PopupMenuEvent(recents.getPopupMenu()));
                    assertThat(recents.getMenuComponentCount()).isEqualTo(3);
                    assertThat(((JMenuItem) recents.getMenuComponent(0)).getName())
                        .isEqualTo("graph-workspace-recent-workspace-0");
                    view.close();
                }
            }
        });
    }

    private static GraphWorkspaceViewBinding modelBinding() {
        GraphWorkspaceViewBinding binding = mock(GraphWorkspaceViewBinding.class);
        when(binding.currentViewport()).thenReturn(Viewport.of(0.0, 0.0, 1.0, Collections.emptyList()));
        when(binding.currentCanvasState()).thenReturn(null);
        when(binding.currentSessionStatus()).thenReturn(WorkspaceSessionStatus.empty());
        when(binding.currentMapRows()).thenReturn(Collections.emptyList());
        return binding;
    }

    private static JMenu recentsMenu(final JMenuBar menuBar) {
        for (int index = 0; index < menuBar.getMenuCount(); index++) {
            JMenu menu = menuBar.getMenu(index);
            for (Component component : menu.getMenuComponents()) {
                if (component instanceof JMenu
                        && "graph-workspace-recent-workspaces-menu".equals(component.getName())) {
                    return (JMenu) component;
                }
            }
        }
        throw new AssertionError("Missing recent workspaces menu");
    }

    private static PopupMenuListener recentsPopupListener(final JMenu menu) {
        PopupMenuListener[] listeners = menu.getPopupMenu().getPopupMenuListeners();
        for (PopupMenuListener listener : listeners) {
            if (!listener.getClass().getName().startsWith("javax.swing.")) {
                return listener;
            }
        }
        throw new AssertionError("Missing recent-workspaces popup listener");
    }

    private static MockedStatic<TextUtils> edtTextUtils() {
        MockedStatic<TextUtils> textUtils = org.mockito.Mockito.mockStatic(TextUtils.class);
        textUtils.when(() -> TextUtils.getText(any(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        textUtils.when(() -> TextUtils.getText(any(String.class), any(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        textUtils.when(() -> TextUtils.getRawText(any(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        textUtils.when(() -> TextUtils.getRawText(any(String.class), any(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        return textUtils;
    }

    private static MockedStatic<ResourceController> edtResourceController() {
        MockedStatic<ResourceController> resources = org.mockito.Mockito.mockStatic(ResourceController.class);
        resources.when(ResourceController::getResourceController).thenReturn(mock(ResourceController.class));
        return resources;
    }
```

The two `edtTextUtils()` and `edtResourceController()` helpers are required because Mockito static mocks are active only on the thread that created them: the factory builds the view on the EDT, so the EDT needs its own TextUtils and ResourceController mocks.

- [ ] **Step 2: Run the new tests and confirm they fail**

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.window.GraphPluginIntegrationShould'
```

Expected: FAIL, compilation error `constructor SwingGraphWorkspaceViewFactory cannot be applied to given types`. On this machine F4 is skipped by its assumption and F5 runs.

- [ ] **Step 3: Add the list to the factory constructor set and pass it through**

Replace the whole body of `SwingGraphWorkspaceViewFactory.java` with:

```java
package org.freeplane.plugin.graph.window;

import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.freeplane.plugin.graph.control.GraphWorkspaceController;
import org.freeplane.plugin.graph.control.GraphWorkspaceHandle;
import org.freeplane.plugin.graph.control.GraphWorkspaceView;
import org.freeplane.plugin.graph.control.GraphWorkspaceViewBinding;
import org.freeplane.plugin.graph.control.GraphWorkspaceViewFactory;
import org.freeplane.plugin.graph.control.WorkspaceCloseController;
import org.freeplane.plugin.graph.workspace.RecentWorkspaceList;

public final class SwingGraphWorkspaceViewFactory implements GraphWorkspaceViewFactory {
    private final GraphWorkspaceController applicationController;
    private final Supplier<Path> pathChooser;
    private final RecentWorkspaceList recentWorkspaces;

    public SwingGraphWorkspaceViewFactory(final GraphWorkspaceController applicationController) {
        this(applicationController, GraphWorkspaceWindow::chooseWorkspacePath, RecentWorkspaceList.empty());
    }

    public SwingGraphWorkspaceViewFactory(final GraphWorkspaceController applicationController,
            final RecentWorkspaceList recentWorkspaces) {
        this(applicationController, GraphWorkspaceWindow::chooseWorkspacePath, recentWorkspaces);
    }

    SwingGraphWorkspaceViewFactory(final GraphWorkspaceController applicationController,
            final Supplier<Path> pathChooser) {
        this(applicationController, pathChooser, RecentWorkspaceList.empty());
    }

    SwingGraphWorkspaceViewFactory(final GraphWorkspaceController applicationController,
            final Supplier<Path> pathChooser, final RecentWorkspaceList recentWorkspaces) {
        this.applicationController = Objects.requireNonNull(applicationController, "applicationController");
        this.pathChooser = Objects.requireNonNull(pathChooser, "pathChooser");
        this.recentWorkspaces = Objects.requireNonNull(recentWorkspaces, "recentWorkspaces");
    }

    @Override
    public GraphWorkspaceView create(final GraphWorkspaceHandle handle, final GraphWorkspaceViewBinding binding,
            final WorkspaceCloseController close) {
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(close, "close");
        final AtomicReference<GraphWorkspaceView> view = new AtomicReference<GraphWorkspaceView>();
        final Runnable construction = new Runnable() {
            @Override
            public void run() {
                if (GraphicsEnvironment.isHeadless()) {
                    view.set(new HeadlessGraphWorkspaceView(handle, binding, close, applicationController,
                        pathChooser, recentWorkspaces));
                }
                else {
                    view.set(new GraphWorkspaceWindow(handle, binding, close, applicationController, pathChooser,
                        recentWorkspaces));
                }
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            construction.run();
        }
        else {
            try {
                SwingUtilities.invokeAndWait(construction);
            }
            catch (final InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while constructing graph workspace window", exception);
            }
            catch (final java.lang.reflect.InvocationTargetException exception) {
                final Throwable cause = exception.getCause();
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
                if (cause instanceof Error) {
                    throw (Error) cause;
                }
                throw new IllegalStateException("Graph workspace window construction failed", cause);
            }
        }
        return Objects.requireNonNull(view.get(), "workspace view");
    }
}
```

- [ ] **Step 4: Run the tests and confirm they pass**

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  gradle :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks \
  --tests 'org.freeplane.plugin.graph.window.GraphPluginIntegrationShould'
```

Expected: PASS. F5 runs on this DISPLAY machine; F4 is skipped and is the coverage for headless CI.

- [ ] **Step 5: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/SwingGraphWorkspaceViewFactory.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphPluginIntegrationShould.java
git commit -m "Propagate the recent list through the workspace view factory [2026-09-10-most-recent-workspaces]"
```

## Task 8: Wire one application-wide recent workspace list

**Implementer tier:** Advanced

**Lane:** integration

**Depends on:** Task 3, Task 4, Task 6, Task 7

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/GraphModeExtension.java:1-148`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphPluginIntegrationShould.java:1-60`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphPluginIntegrationShould.java:180-300`

**Interfaces:**

- Consumes: `RecentWorkspaceList.standard()` and `RecentWorkspaceList.PROPERTY_KEY` from Task 1; `public DefaultGraphWorkspaceController(ModeController, GraphWorkspaceViewFactory, RecentWorkspaceList)` from Task 4; `public SwingGraphWorkspaceViewFactory(GraphWorkspaceController, RecentWorkspaceList)` from Task 7; `public OpenGraphWorkspaceAction(GraphWorkspaceController, RecentWorkspaceList)` from Task 6; `GraphWorkspaceController.openExisting(Path): GraphWorkspaceHandle` from Task 3; existing `ResourceController.getProperty(String, String): String`, `ResourceController.setProperty(String, String): void`, and `ConfigurationUtils.encodeListValue(List<String>, boolean): String`.
- Produces: `GraphModeExtension.installExtension` creates exactly one `RecentWorkspaceList.standard()` and injects it into `DefaultGraphWorkspaceController`, `SwingGraphWorkspaceViewFactory`, and `OpenGraphWorkspaceAction`; `GraphModeExtension.ForwardingGraphWorkspaceController` gains `public GraphWorkspaceHandle openExisting(Path path)` that wraps the unbound state in `GraphWorkspaceOpenException` and otherwise delegates.

- [ ] **Step 1: Write the failing tests F1 and F3**

In `GraphPluginIntegrationShould.java`, add these imports in their alphabetical positions:

```java
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import org.freeplane.core.util.ConfigurationUtils;
import org.freeplane.plugin.graph.control.GraphWorkspaceOpenException;
import org.freeplane.plugin.graph.workspace.RecentWorkspaceList;
import org.mockito.MockedConstruction;
```

Add these static imports next to the existing `org.mockito` static imports:

```java
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
```

The `TemporaryFolder` rule added by Task 7 is already present. Insert these two test methods immediately before `private static ModeController configuredModeController()`:

```java
    @Test
    public void wiresApplicationWideRecentWorkspacesThroughUserProperties() throws Exception {
        ApplicationResourceController applicationResources = mock(ApplicationResourceController.class);
        resourceController.when(ResourceController::getResourceController).thenReturn(applicationResources);
        Path first = temporaryFolder.newFile("seeded-workspace.fpg").toPath().toRealPath();
        Path second = temporaryFolder.newFile("recorded-workspace.fpg").toPath().toRealPath();
        when(applicationResources.getProperty(RecentWorkspaceList.PROPERTY_KEY, ""))
            .thenReturn(ConfigurationUtils.encodeListValue(Arrays.asList(first.toString()), true));
        AtomicReference<RecentWorkspaceList> controllerList = new AtomicReference<RecentWorkspaceList>();
        AtomicReference<RecentWorkspaceList> factoryList = new AtomicReference<RecentWorkspaceList>();
        AtomicReference<RecentWorkspaceList> actionList = new AtomicReference<RecentWorkspaceList>();
        ModeController modeController = configuredModeController();
        GraphModeExtension extension = new GraphModeExtension();

        try (MockedConstruction<DefaultGraphWorkspaceController> controllerConstruction =
                mockConstruction(DefaultGraphWorkspaceController.class, (mock, context) ->
                    controllerList.set((RecentWorkspaceList) context.arguments().get(2)));
             MockedConstruction<SwingGraphWorkspaceViewFactory> factoryConstruction =
                mockConstruction(SwingGraphWorkspaceViewFactory.class, (mock, context) ->
                    factoryList.set((RecentWorkspaceList) context.arguments().get(1)));
             MockedConstruction<OpenGraphWorkspaceAction> actionConstruction =
                mockConstruction(OpenGraphWorkspaceAction.class, (mock, context) ->
                    actionList.set((RecentWorkspaceList) context.arguments().get(1)))) {
            extension.installExtension(modeController, null);

            assertThat(controllerConstruction.constructed()).hasSize(1);
            assertThat(controllerList.get()).isNotNull();
            assertThat(controllerList.get().mostRecentExisting()).contains(first);
            assertThat(factoryList.get()).isSameAs(controllerList.get());
            assertThat(actionList.get()).isSameAs(controllerList.get());

            controllerList.get().record(second);
            controllerList.get().clear();

            ArgumentCaptor<String> persisted = ArgumentCaptor.forClass(String.class);
            verify(applicationResources, times(2)).setProperty(eq(RecentWorkspaceList.PROPERTY_KEY),
                persisted.capture());
            assertThat(persisted.getAllValues().get(0)).isEqualTo(
                ConfigurationUtils.encodeListValue(Arrays.asList(second.toString(), first.toString()), true));
            assertThat(persisted.getAllValues().get(1)).isEqualTo("");

            extension.close();
        }
    }

    @Test
    public void delegatesOpenExistingThroughTheForwardingController() throws Exception {
        ApplicationResourceController applicationResources = mock(ApplicationResourceController.class);
        resourceController.when(ResourceController::getResourceController).thenReturn(applicationResources);
        Path recent = temporaryFolder.newFile("forwarded.fpg").toPath().toRealPath();
        when(applicationResources.getProperty(RecentWorkspaceList.PROPERTY_KEY, ""))
            .thenReturn(ConfigurationUtils.encodeListValue(Arrays.asList(recent.toString()), true));
        ModeController modeController = configuredModeController();
        GraphModeExtension extension = new GraphModeExtension();

        try (MockedConstruction<DefaultGraphWorkspaceController> constructions =
                mockConstruction(DefaultGraphWorkspaceController.class)) {
            extension.installExtension(modeController, null);
            DefaultGraphWorkspaceController constructed = constructions.constructed().get(0);
            ArgumentCaptor<AFreeplaneAction> actions = ArgumentCaptor.forClass(AFreeplaneAction.class);
            verify(modeController, times(3)).addAction(actions.capture());
            OpenGraphWorkspaceAction action = null;
            for (AFreeplaneAction candidate : actions.getAllValues()) {
                if (candidate instanceof OpenGraphWorkspaceAction) {
                    action = (OpenGraphWorkspaceAction) candidate;
                }
            }
            assertThat(action).isNotNull();

            action.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "recent"));

            verify(constructed).openExisting(recent);
            verify(constructed, never()).open(any(java.nio.file.Path.class));
            extension.close();
        }

        Class<?> forwardingType = Class.forName(
            "org.freeplane.plugin.graph.GraphModeExtension$ForwardingGraphWorkspaceController");
        Constructor<?> constructor = forwardingType.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object forwarding = constructor.newInstance();
        Method openExisting = forwardingType.getDeclaredMethod("openExisting", java.nio.file.Path.class);
        openExisting.setAccessible(true);
        try {
            openExisting.invoke(forwarding, recent);
            throw new AssertionError("Expected GraphWorkspaceOpenException");
        }
        catch (java.lang.reflect.InvocationTargetException failure) {
            assertThat(failure.getCause()).isInstanceOf(GraphWorkspaceOpenException.class);
            assertThat(failure.getCause()).hasCauseInstanceOf(IllegalStateException.class);
        }
    }
```

- [ ] **Step 2: Run the new tests and confirm they fail**

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.window.GraphPluginIntegrationShould'
```

Expected: FAIL. F1 fails because `installExtension` still builds the controller and the factory without the shared list, and the reflection path in F3 fails because `ForwardingGraphWorkspaceController` has no `openExisting`.

- [ ] **Step 3: Create the single list and inject it everywhere**

Add to `GraphModeExtension.java` next to the existing `org.freeplane.plugin.graph.control` imports:

```java
import org.freeplane.plugin.graph.workspace.RecentWorkspaceList;
```

In `installExtension`, replace:

```java
        final ForwardingGraphWorkspaceController viewController = new ForwardingGraphWorkspaceController();
        final DefaultGraphWorkspaceController completedController = new DefaultGraphWorkspaceController(modeController,
            new SwingGraphWorkspaceViewFactory(viewController));
        viewController.bind(completedController);
        graphWorkspaceController = completedController;
        openGraphWorkspaceAction = new OpenGraphWorkspaceAction(viewController);
```

with:

```java
        final RecentWorkspaceList recentWorkspaces = RecentWorkspaceList.standard();
        final ForwardingGraphWorkspaceController viewController = new ForwardingGraphWorkspaceController();
        final DefaultGraphWorkspaceController completedController = new DefaultGraphWorkspaceController(modeController,
            new SwingGraphWorkspaceViewFactory(viewController, recentWorkspaces), recentWorkspaces);
        viewController.bind(completedController);
        graphWorkspaceController = completedController;
        openGraphWorkspaceAction = new OpenGraphWorkspaceAction(viewController, recentWorkspaces);
```

In `ForwardingGraphWorkspaceController`, add this method after the existing `open` method:

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

- [ ] **Step 4: Run the tests and confirm they pass**

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  gradle :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks \
  --tests 'org.freeplane.plugin.graph.window.GraphPluginIntegrationShould'
```

Expected: PASS, F1 and F3 plus all surviving `GraphPluginIntegrationShould` regressions, including `installsAndRemovesTheApplicationScopedWorkspaceActionWithTheExistingGraphExtension` (which proves `standard()` tolerates a mocked `getProperty` returning null), `opensOnlyPathsSelectedByTheInjectedWorkspaceChooser`, and `placesAndDescribesBothGraphActionsWithTheirOwnIcons`.

- [ ] **Step 5: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/GraphModeExtension.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphPluginIntegrationShould.java
git commit -m "Wire one application-wide recent workspace list [2026-09-10-most-recent-workspaces]"
```

## Task 9: Recent workspace resource keys

**Implementer tier:** Fast

**Lane:** integration

**Depends on:** none

**Files:**

- Modify: `freeplane/src/viewer/resources/translations/Resources_en.properties:785-786`
- Modify: `freeplane/src/viewer/resources/translations/Resources_en.properties:901-902`
- Modify: `freeplane/src/viewer/resources/translations/Resources_en.properties:913-914`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphPluginIntegrationShould.java:180-330`

**Interfaces:**

- Consumes: the existing `GraphPluginIntegrationShould.properties(String relativePath): Properties` helper; nothing from Tasks 1-8.
- Produces: the four keys `graph_workspace.action.clear_recent_workspaces=Clear Recent Workspaces`, `graph_workspace.menu.recent_workspaces=Recent Workspaces`, `graph_workspace.recent_workspaces.empty=No recent workspaces`, and `graph_workspace.recent_workspaces.open_failed=Could not open the recent workspace: {0}`.

- [ ] **Step 1: Write the failing test F2**

Insert this test method into `GraphPluginIntegrationShould.java` immediately before `private static ModeController configuredModeController()`:

```java
    @Test
    public void shipsTheFourRecentWorkspaceResourceKeys() throws IOException {
        Properties translations = properties("freeplane/src/viewer/resources/translations/Resources_en.properties");

        assertThat(translations.getProperty("graph_workspace.action.clear_recent_workspaces"))
            .isEqualTo("Clear Recent Workspaces");
        assertThat(translations.getProperty("graph_workspace.menu.recent_workspaces"))
            .isEqualTo("Recent Workspaces");
        assertThat(translations.getProperty("graph_workspace.recent_workspaces.empty"))
            .isEqualTo("No recent workspaces");
        assertThat(translations.getProperty("graph_workspace.recent_workspaces.open_failed"))
            .isEqualTo("Could not open the recent workspace: {0}");
    }
```

- [ ] **Step 2: Run the new test and confirm it fails**

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.window.GraphPluginIntegrationShould'
```

Expected: FAIL, `shipsTheFourRecentWorkspaceResourceKeys` fails because the four keys are absent.

- [ ] **Step 3: Add the four keys in alphabetical order and format the bundle**

Insert these four lines into `freeplane/src/viewer/resources/translations/Resources_en.properties`. Keep the file ISO-8859-1 with `\uXXXX` escapes; the four values are plain ASCII.

Between `graph_workspace.action.cancel=Cancel` (line 785) and `graph_workspace.action.close=Close` (line 786):

```properties
graph_workspace.action.clear_recent_workspaces=Clear Recent Workspaces
```

Between `graph_workspace.menu.maps=Maps` (line 901) and `graph_workspace.menu.view=View` (line 902):

```properties
graph_workspace.menu.recent_workspaces=Recent Workspaces
```

Between `graph_workspace.purge.unavailable=The purge operation is unavailable.` (line 914) and `graph_workspace.relationship.created=Relationship created.` (line 915):

```properties
graph_workspace.recent_workspaces.empty=No recent workspaces
graph_workspace.recent_workspaces.open_failed=Could not open the recent workspace: {0}
```

Then run the repository formatter from the repository root and keep its result:

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu gradle format_translation
```

- [ ] **Step 4: Run the tests and confirm they pass**

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  gradle :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks \
  --tests 'org.freeplane.plugin.graph.window.GraphPluginIntegrationShould'
```

Expected: PASS, all `GraphPluginIntegrationShould` tests including F2.

Then run the full module suite and the encoding checks required by the Definition of Done:

```bash
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  gradle :freeplane_plugin_graph:test -PTestLoggingFull --rerun-tasks
file freeplane/src/viewer/resources/translations/Resources_en.properties | grep -v "ASCII text"
grep -n "u[0-9][0-9][0-9][0-9]" freeplane/src/viewer/resources/translations/Resources_en.properties
```

Expected: the full `freeplane_plugin_graph` suite passes with no regressions; `file` reports ASCII text (no output from the `grep -v`); the escape scan lists only pre-existing escaped entries and none of the four new keys.

- [ ] **Step 5: Commit**

```bash
git add freeplane/src/viewer/resources/translations/Resources_en.properties \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphPluginIntegrationShould.java
git commit -m "Add recent workspace resource keys [2026-09-10-most-recent-workspaces]"
```
