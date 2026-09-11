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
