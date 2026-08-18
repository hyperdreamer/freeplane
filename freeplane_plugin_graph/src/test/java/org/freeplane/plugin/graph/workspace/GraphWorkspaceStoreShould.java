package org.freeplane.plugin.graph.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.freeplane.plugin.graph.workspace.io.WorkspaceMigration;
import org.freeplane.plugin.graph.workspace.io.WorkspaceMigrationRegistry;
import org.freeplane.plugin.graph.workspace.io.WorkspaceXmlCodec;
import org.freeplane.plugin.graph.workspace.model.MapReference;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.UnknownXml;
import org.freeplane.plugin.graph.workspace.model.Viewport;
import org.freeplane.plugin.graph.workspace.model.WorkspaceDocument;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class GraphWorkspaceStoreShould {
    private static final WorkspaceId ORIGINAL_ID =
        WorkspaceId.of("00000000-0000-0000-0000-000000000100");
    private static final WorkspaceId NEW_ID =
        WorkspaceId.of("00000000-0000-0000-0000-000000000200");
    private static final MapReferenceId MAP_ONE =
        MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    private static final MapReferenceId MAP_TWO =
        MapReferenceId.of("00000000-0000-0000-0000-000000000002");
    private static final long CROSS_THREAD_TIMEOUT_MILLIS = 1000L;

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void mapTransitionsAndKeepCommandResultModifiersImmutable() throws Exception {
        WorkspaceDocument document = WorkspaceDocument.createVersion1(ORIGINAL_ID);
        Object[] arguments = new Object[] { "before" };
        WorkspaceTransition applied = WorkspaceTransition.applied(document, "test.applied", arguments);
        arguments[0] = "after";
        WorkspaceIdentityChange identity = new WorkspaceIdentityChange(
            temporaryFolder.getRoot().toPath().resolve("old.fpg"),
            temporaryFolder.getRoot().toPath().resolve("new.fpg"), ORIGINAL_ID, NEW_ID);

        GraphCommandResult base = GraphCommandResult.from(applied);
        GraphCommandResult changed = base
            .withDirtySourceMaps(new HashSet<MapReferenceId>(Arrays.asList(MAP_TWO, MAP_ONE)))
            .withEditorViewActivated(true)
            .withIdentityChange(identity);

        assertThat(base.status()).isEqualTo(GraphCommandResult.Status.APPLIED);
        assertThat(base.messageKey()).isEqualTo("test.applied");
        assertThat(base.messageArguments()).containsExactly("before");
        assertThat(base.dirtySourceMaps()).isEmpty();
        assertThat(base.editorViewActivated()).isFalse();
        assertThat(base.identityChange()).isEmpty();
        assertThat(changed.status()).isEqualTo(GraphCommandResult.Status.APPLIED);
        assertThat(changed.messageKey()).isEqualTo("test.applied");
        assertThat(changed.messageArguments()).containsExactly("before");
        assertThat(changed.dirtySourceMaps()).containsExactly(MAP_ONE, MAP_TWO);
        assertThat(changed.editorViewActivated()).isTrue();
        assertThat(changed.identityChange()).isEqualTo(Optional.of(identity));
        assertThat(identity.oldPath().getFileName().toString()).isEqualTo("old.fpg");
        assertThat(identity.newPath().getFileName().toString()).isEqualTo("new.fpg");
        assertThat(identity.oldId()).isEqualTo(ORIGINAL_ID);
        assertThat(identity.newId()).isEqualTo(NEW_ID);
        assertThatThrownBy(() -> changed.messageArguments().add("later"))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> changed.dirtySourceMaps().add(MAP_ONE))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> GraphCommandResult.from(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> base.withDirtySourceMaps(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> base.withDirtySourceMaps(Collections.singleton(null)))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> base.withIdentityChange(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new WorkspaceIdentityChange(null,
            temporaryFolder.getRoot().toPath().resolve("new.fpg"), ORIGINAL_ID, NEW_ID))
            .isInstanceOf(NullPointerException.class);

        assertThat(GraphCommandResult.from(WorkspaceTransition.noOp(document, "test.no-op")).status())
            .isEqualTo(GraphCommandResult.Status.NO_OP);
        assertThat(GraphCommandResult.from(WorkspaceTransition.rejected(document, "test.rejected")).status())
            .isEqualTo(GraphCommandResult.Status.REJECTED);
    }

    @Test
    public void createSynchronouslyWritesAVersionOneDocumentAndStartsClean() throws Exception {
        Path workspace = workspacePath("create", "workspace.fpg");
        RecordingWriter writer = new RecordingWriter();
        TestScheduler scheduler = new TestScheduler();

        GraphWorkspaceStore store = GraphWorkspaceStore.create(workspace, codec(), writer, scheduler);
        List<WorkspaceStoreEvent> events = new ArrayList<WorkspaceStoreEvent>();
        ListenerRegistration registration = store.addListener(events::add);

        store.saveNow();

        assertThat(writer.writes()).hasSize(1);
        assertThat(writer.writes().get(0).target()).isEqualTo(canonical(workspace));
        assertThat(store.currentDocument().sourceFormatVersion()).isEqualTo(1);
        assertThat(store.currentDocument().id()).isNotNull();
        assertThat(codec().read(workspace)).isEqualTo(store.currentDocument());
        assertThat(store.isDirty()).isFalse();
        assertThat(scheduler.tasks()).isEmpty();
        assertThat(events).isEmpty();

        store.close();
        registration.close();
        assertThat(writer.writes()).hasSize(1);
    }

    @Test
    public void rejectCreatingOverAnExistingWorkspaceWithoutReplacingIt() throws Exception {
        Path workspace = workspacePath("existing", "workspace.fpg");
        byte[] existing = bytes("existing workspace");
        Files.write(workspace, existing);
        RecordingWriter writer = new RecordingWriter();

        assertThatThrownBy(() -> GraphWorkspaceStore.create(workspace, codec(), writer, new TestScheduler()))
            .isInstanceOf(IllegalArgumentException.class);

        assertThat(Files.readAllBytes(workspace)).isEqualTo(existing);
        assertThat(writer.writes()).isEmpty();
    }

    @Test
    public void openLoadsTheExactDocumentWithoutWriting() throws Exception {
        Path workspace = workspacePath("open", "workspace.fpg");
        RecordingWriter createWriter = new RecordingWriter();
        GraphWorkspaceStore created = GraphWorkspaceStore.create(workspace, codec(), createWriter, new TestScheduler());
        WorkspaceDocument expected = created.currentDocument();
        RecordingWriter openWriter = new RecordingWriter();

        GraphWorkspaceStore opened = GraphWorkspaceStore.open(workspace, codec(), openWriter, new TestScheduler());

        assertThat(opened.currentDocument()).isEqualTo(expected);
        assertThat(opened.isDirty()).isFalse();
        assertThat(openWriter.writes()).isEmpty();
    }

    @Test
    public void rejectAllMutationAndSaveOperationsForReadOnlyNewerDocumentsWithoutInvokingCommands() throws Exception {
        Path workspace = workspacePath("read-only", "workspace.fpg");
        WorkspaceDocument writable = WorkspaceDocument.createVersion1(ORIGINAL_ID);
        String newerXml = new String(codec().write(writable, workspace), StandardCharsets.UTF_8)
            .replace("format-version=\"1\"", "format-version=\"2\"");
        Files.write(workspace, newerXml.getBytes(StandardCharsets.UTF_8));
        RecordingWriter writer = new RecordingWriter();
        TestScheduler scheduler = new TestScheduler();
        GraphWorkspaceStore store = GraphWorkspaceStore.open(workspace, codec(), writer, scheduler);
        AtomicInteger invocations = new AtomicInteger();
        WorkspaceCommand command = new WorkspaceCommand() {
            @Override
            public WorkspaceTransition apply(WorkspaceDocument before) {
                invocations.incrementAndGet();
                return WorkspaceTransition.applied(before, "test.applied");
            }
        };

        GraphCommandResult execute = store.execute(command);
        GraphCommandResult viewport = store.updateViewport(viewport(1, 2, 3));
        GraphCommandResult undo = store.undo();
        GraphCommandResult redo = store.redo();

        assertThat(invocations.get()).isZero();
        assertThat(execute.status()).isEqualTo(GraphCommandResult.Status.REJECTED);
        assertThat(viewport.status()).isEqualTo(GraphCommandResult.Status.REJECTED);
        assertThat(undo.status()).isEqualTo(GraphCommandResult.Status.REJECTED);
        assertThat(redo.status()).isEqualTo(GraphCommandResult.Status.REJECTED);
        assertThat(execute.messageKey()).isEqualTo("graph_workspace.workspace.read_only");
        assertThat(writer.writes()).isEmpty();
        assertThat(scheduler.tasks()).isEmpty();
        assertThatThrownBy(store::saveNow).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> store.saveAs(workspacePath("read-only-target", "target.fpg")))
            .isInstanceOf(IllegalStateException.class);

        store.close();
        assertThat(store.currentDocument()).isEqualTo(codec().read(workspace));
    }

    @Test
    public void autosaveAfterExactlyOneHundredFiftyMillisecondsAndIgnoreStaleRunnables() throws Exception {
        Path workspace = workspacePath("autosave", "workspace.fpg");
        RecordingWriter writer = new RecordingWriter();
        TestScheduler scheduler = new TestScheduler();
        GraphWorkspaceStore store = GraphWorkspaceStore.create(workspace, codec(), writer, scheduler);

        assertThat(store.execute(addMap(MAP_ONE)).status()).isEqualTo(GraphCommandResult.Status.APPLIED);
        assertThat(store.updateViewport(viewport(4, -2, 2)).status()).isEqualTo(GraphCommandResult.Status.APPLIED);

        assertThat(scheduler.tasks()).hasSize(2);
        assertThat(scheduler.tasks().get(0).delay()).isEqualTo(150L);
        assertThat(scheduler.tasks().get(0).unit()).isEqualTo(TimeUnit.MILLISECONDS);
        assertThat(scheduler.tasks().get(0).cancelled()).isTrue();
        assertThat(scheduler.tasks().get(0).mayInterruptIfRunning()).isFalse();

        scheduler.run(0);

        assertThat(writer.writes()).hasSize(1);
        assertThat(store.isDirty()).isTrue();

        scheduler.run(1);

        assertThat(writer.writes()).hasSize(2);
        assertThat(store.isDirty()).isFalse();
        assertThat(codec().read(workspace)).isEqualTo(store.currentDocument());
    }

    @Test
    public void preserveHistoryAcrossNormalSavesAndAutosaveUndoAndRedo() throws Exception {
        Path workspace = workspacePath("history", "workspace.fpg");
        RecordingWriter writer = new RecordingWriter();
        TestScheduler scheduler = new TestScheduler();
        GraphWorkspaceStore store = GraphWorkspaceStore.create(workspace, codec(), writer, scheduler);

        store.execute(addMap(MAP_ONE));
        scheduler.run(0);
        GraphCommandResult undo = store.undo();
        scheduler.run(1);
        GraphCommandResult redo = store.redo();
        scheduler.run(2);

        assertThat(undo.status()).isEqualTo(GraphCommandResult.Status.APPLIED);
        assertThat(redo.status()).isEqualTo(GraphCommandResult.Status.APPLIED);
        assertThat(store.currentDocument().maps()).extracting(MapReference::id).containsExactly(MAP_ONE);
        assertThat(codec().read(workspace)).isEqualTo(store.currentDocument());
        assertThat(writer.writes()).hasSize(4);
        assertThat(store.isDirty()).isFalse();
    }

    @Test
    public void updateViewportWithoutChangingHistoryAndReportAnEqualViewportAsNoOp() throws Exception {
        Path workspace = workspacePath("viewport", "workspace.fpg");
        GraphWorkspaceStore store = GraphWorkspaceStore.create(workspace, codec(), new RecordingWriter(),
            new TestScheduler());
        Viewport changed = viewport(4, -2, 2);

        GraphCommandResult update = store.updateViewport(changed);
        GraphCommandResult undo = store.undo();
        GraphCommandResult equal = store.updateViewport(changed);

        assertThat(update.status()).isEqualTo(GraphCommandResult.Status.APPLIED);
        assertThat(update.messageKey()).isEqualTo("graph_workspace.viewport.updated");
        assertThat(store.currentDocument().viewport()).isEqualTo(changed);
        assertThat(undo.status()).isEqualTo(GraphCommandResult.Status.NO_OP);
        assertThat(undo.messageKey()).isEqualTo("graph_workspace.history.nothing_to_undo");
        assertThat(equal.status()).isEqualTo(GraphCommandResult.Status.NO_OP);
        assertThat(equal.messageKey()).isEqualTo("graph_workspace.command.no_change");
        assertThat(equal.messageArguments()).containsExactly("updateViewport");
    }

    @Test
    public void publishSchedulerRejectionAsASaveFailureWithoutRejectingTheAppliedCommand() throws Exception {
        Path workspace = workspacePath("scheduler-rejection", "workspace.fpg");
        RecordingWriter writer = new RecordingWriter();
        TestScheduler scheduler = new TestScheduler();
        scheduler.rejectScheduling();
        GraphWorkspaceStore store = GraphWorkspaceStore.create(workspace, codec(), writer, scheduler);
        List<WorkspaceStoreEvent> events = new ArrayList<WorkspaceStoreEvent>();
        store.addListener(events::add);

        GraphCommandResult result = store.execute(addMap(MAP_ONE));

        assertThat(result.status()).isEqualTo(GraphCommandResult.Status.APPLIED);
        assertThat(store.isDirty()).isTrue();
        assertThat(writer.writes()).hasSize(1);
        assertThat(events).extracting(WorkspaceStoreEvent::type)
            .containsExactly(WorkspaceStoreEvent.Type.DOCUMENT_CHANGED, WorkspaceStoreEvent.Type.SAVE_FAILED);
        assertThat(events.get(1).document()).isEqualTo(store.currentDocument());
        assertThat(events.get(1).identityChange()).isEmpty();
        assertThat(events.get(1).error()).containsInstanceOf(RejectedExecutionException.class);
    }

    @Test
    public void saveOnlyTheWorkspaceAndNeverRequireSourceMapContents() throws Exception {
        Path workspace = workspacePath("no-map-access", "workspace.fpg");
        Path missingMap = workspace.getParent().resolve("unavailable.mm");
        RecordingWriter writer = new RecordingWriter();
        GraphWorkspaceStore store = GraphWorkspaceStore.create(workspace, codec(), writer, new TestScheduler());

        store.execute(addMap(MAP_ONE, URI.create("unavailable.mm")));
        store.saveNow();

        assertThat(Files.exists(missingMap)).isFalse();
        assertThat(writer.writes()).extracting(Write::target).containsOnly(canonical(workspace));
        assertThat(codec().read(workspace).maps()).extracting(MapReference::storedUri)
            .containsExactly(URI.create("unavailable.mm"));
    }

    @Test
    public void retainPriorBytesAndDirtyStateAfterFailureThenAllowExplicitRetry() throws Exception {
        Path workspace = workspacePath("retry", "workspace.fpg");
        RecordingWriter writer = new RecordingWriter();
        GraphWorkspaceStore store = GraphWorkspaceStore.create(workspace, codec(), writer, new TestScheduler());
        byte[] initialBytes = Files.readAllBytes(workspace);
        store.execute(addMap(MAP_ONE));
        RuntimeException failure = new IllegalStateException("injected save failure");
        writer.failNext(failure);
        List<WorkspaceStoreEvent> events = new ArrayList<WorkspaceStoreEvent>();
        store.addListener(events::add);

        Throwable thrown = catchThrowable(store::saveNow);

        assertThat(thrown).isSameAs(failure);
        assertThat(Files.readAllBytes(workspace)).isEqualTo(initialBytes);
        assertThat(store.isDirty()).isTrue();
        assertThat(events).extracting(WorkspaceStoreEvent::type)
            .containsExactly(WorkspaceStoreEvent.Type.SAVE_FAILED);
        assertThat(events.get(0).error()).containsSame(failure);

        store.saveNow();

        assertThat(store.isDirty()).isFalse();
        assertThat(codec().read(workspace)).isEqualTo(store.currentDocument());
    }

    @Test
    public void saveAsClearsHistorySoUndoRedoCannotRestoreOldIdentityOrUris() throws Exception {
        Path originalDirectory = temporaryFolder.newFolder("save-as-original").toPath();
        Path original = originalDirectory.resolve("workspace.fpg");
        Path targetDirectory = temporaryFolder.newFolder("save-as-target").toPath();
        Path target = targetDirectory.resolve("renamed.fpg");
        Files.write(target, bytes("old target"));
        RecordingWriter writer = new RecordingWriter();
        TestScheduler scheduler = new TestScheduler();
        GraphWorkspaceStore store = GraphWorkspaceStore.create(original, codec(), writer, scheduler);
        store.execute(addMap(MAP_ONE, URI.create("maps/one.mm")));
        store.saveNow();
        WorkspaceDocument beforeSaveAs = store.currentDocument();
        List<WorkspaceStoreEvent> events = new ArrayList<WorkspaceStoreEvent>();
        store.addListener(events::add);

        WorkspaceIdentityChange change = store.saveAs(target);

        URI rewrittenUri = URI.create("../save-as-original/maps/one.mm");
        assertThat(change.oldPath()).isEqualTo(canonical(original));
        assertThat(change.newPath()).isEqualTo(canonical(target));
        assertThat(change.oldId()).isEqualTo(beforeSaveAs.id());
        assertThat(change.newId()).isEqualTo(store.currentDocument().id());
        assertThat(change.newId()).isNotEqualTo(change.oldId());
        assertThat(store.currentDocument().maps()).extracting(MapReference::storedUri).containsExactly(rewrittenUri);
        assertThat(store.isDirty()).isFalse();
        assertThat(events).extracting(WorkspaceStoreEvent::type)
            .containsExactly(WorkspaceStoreEvent.Type.IDENTITY_CHANGED, WorkspaceStoreEvent.Type.SAVED);
        assertThat(events.get(0).document()).isEqualTo(store.currentDocument());
        assertThat(events.get(0).identityChange()).isEqualTo(Optional.of(change));
        assertThat(events.get(0).error()).isEmpty();
        assertThat(events.get(1).document()).isEqualTo(store.currentDocument());
        assertThat(events.get(1).identityChange()).isEmpty();
        assertThat(events.get(1).error()).isEmpty();
        assertThat(codec().read(target)).isEqualTo(store.currentDocument());
        assertThat(codec().read(original)).isEqualTo(beforeSaveAs);

        GraphCommandResult undo = store.undo();
        GraphCommandResult redo = store.redo();

        assertThat(undo.status()).isEqualTo(GraphCommandResult.Status.NO_OP);
        assertThat(redo.status()).isEqualTo(GraphCommandResult.Status.NO_OP);
        assertThat(store.currentDocument().id()).isEqualTo(change.newId());
        assertThat(store.currentDocument().maps()).extracting(MapReference::storedUri).containsExactly(rewrittenUri);

        store.close();
        GraphWorkspaceStore reopened = GraphWorkspaceStore.open(target, codec(), new RecordingWriter(),
            new TestScheduler());
        assertThat(reopened.currentDocument().id()).isEqualTo(change.newId());
        assertThat(reopened.currentDocument().maps()).extracting(MapReference::storedUri).containsExactly(rewrittenUri);
    }

    @Test
    public void preserveOldStateHistoryAndTargetBytesWhenSaveAsFails() throws Exception {
        Path original = workspacePath("failed-save-as-original", "workspace.fpg");
        Path target = workspacePath("failed-save-as-target", "renamed.fpg");
        byte[] existingTarget = bytes("target bytes before failure");
        Files.write(target, existingTarget);
        RecordingWriter writer = new RecordingWriter();
        TestScheduler scheduler = new TestScheduler();
        GraphWorkspaceStore store = GraphWorkspaceStore.create(original, codec(), writer, scheduler);
        store.execute(addMap(MAP_ONE, URI.create("maps/one.mm")));
        WorkspaceDocument beforeFailure = store.currentDocument();
        RuntimeException failure = new IllegalStateException("injected save-as failure");
        writer.failNext(failure);
        List<WorkspaceStoreEvent> events = new ArrayList<WorkspaceStoreEvent>();
        store.addListener(events::add);

        Throwable thrown = catchThrowable(() -> store.saveAs(target));

        assertThat(thrown).isSameAs(failure);
        assertThat(store.currentDocument()).isEqualTo(beforeFailure);
        assertThat(store.isDirty()).isTrue();
        assertThat(Files.readAllBytes(target)).isEqualTo(existingTarget);
        assertThat(events).extracting(WorkspaceStoreEvent::type)
            .containsExactly(WorkspaceStoreEvent.Type.SAVE_FAILED);
        assertThat(events.get(0).document()).isEqualTo(beforeFailure);
        assertThat(events.get(0).error()).containsSame(failure);
        assertThat(scheduler.tasks().get(0).cancelled()).isTrue();

        scheduler.run(0);
        assertThat(writer.writes()).hasSize(1);
        assertThat(store.undo().status()).isEqualTo(GraphCommandResult.Status.APPLIED);
    }

    @Test
    public void deliverEventsInOrderRemoveListenersAndAllowReentrantSaveWithoutRecursion() throws Exception {
        Path workspace = workspacePath("events", "workspace.fpg");
        RecordingWriter writer = new RecordingWriter();
        TestScheduler scheduler = new TestScheduler();
        GraphWorkspaceStore store = GraphWorkspaceStore.create(workspace, codec(), writer, scheduler);
        List<WorkspaceStoreEvent> received = new ArrayList<WorkspaceStoreEvent>();
        AtomicInteger listenerCalls = new AtomicInteger();
        store.addListener(new WorkspaceStoreListener() {
            @Override
            public void onWorkspaceStoreEvent(WorkspaceStoreEvent event) {
                throw new IllegalStateException("listener failure");
            }
        });
        store.addListener(new WorkspaceStoreListener() {
            @Override
            public void onWorkspaceStoreEvent(WorkspaceStoreEvent event) {
                listenerCalls.incrementAndGet();
                received.add(event);
                if (event.type() == WorkspaceStoreEvent.Type.DOCUMENT_CHANGED) {
                    store.saveNow();
                }
            }
        });

        store.execute(addMap(MAP_ONE));

        assertThat(listenerCalls.get()).isEqualTo(2);
        assertThat(received).extracting(WorkspaceStoreEvent::type)
            .containsExactly(WorkspaceStoreEvent.Type.DOCUMENT_CHANGED, WorkspaceStoreEvent.Type.SAVED);
        assertThat(writer.writes()).hasSize(2);

        ListenerRegistration registration = store.addListener(received::add);
        registration.close();
        registration.close();
        store.updateViewport(viewport(3, 4, 1.5));

        assertThat(received).extracting(WorkspaceStoreEvent::type)
            .containsExactly(WorkspaceStoreEvent.Type.DOCUMENT_CHANGED, WorkspaceStoreEvent.Type.SAVED,
                WorkspaceStoreEvent.Type.DOCUMENT_CHANGED, WorkspaceStoreEvent.Type.SAVED);
    }

    @Test
    public void allowCrossThreadReadsToCompleteDuringDocumentChangedCallbacks() throws Exception {
        Path workspace = workspacePath("cross-thread-events", "workspace.fpg");
        GraphWorkspaceStore store = GraphWorkspaceStore.create(workspace, codec(), new RecordingWriter(),
            new TestScheduler());
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch workerCompleted = new CountDownLatch(1);
        AtomicBoolean completedBeforeCallbackReturned = new AtomicBoolean();
        AtomicReference<Thread> readerThread = new AtomicReference<Thread>();
        AtomicReference<Throwable> readerFailure = new AtomicReference<Throwable>();
        store.addListener(new WorkspaceStoreListener() {
            @Override
            public void onWorkspaceStoreEvent(WorkspaceStoreEvent event) {
                if (event.type() != WorkspaceStoreEvent.Type.DOCUMENT_CHANGED) {
                    return;
                }
                Thread reader = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        workerStarted.countDown();
                        try {
                            store.currentDocument();
                        }
                        catch (Throwable exception) {
                            readerFailure.set(exception);
                        }
                        finally {
                            workerCompleted.countDown();
                        }
                    }
                }, "GraphWorkspaceStoreShould-cross-thread-reader");
                readerThread.set(reader);
                reader.start();
                try {
                    if (workerStarted.await(CROSS_THREAD_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                        completedBeforeCallbackReturned.set(workerCompleted.await(CROSS_THREAD_TIMEOUT_MILLIS,
                            TimeUnit.MILLISECONDS));
                    }
                }
                catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        GraphCommandResult result;
        try {
            result = store.execute(addMap(MAP_ONE));
        }
        finally {
            Thread reader = readerThread.get();
            if (reader != null) {
                reader.join(CROSS_THREAD_TIMEOUT_MILLIS);
            }
        }

        Thread reader = readerThread.get();
        assertThat(result.status()).isEqualTo(GraphCommandResult.Status.APPLIED);
        assertThat(workerStarted.getCount()).isZero();
        assertThat(completedBeforeCallbackReturned.get()).isTrue();
        assertThat(workerCompleted.getCount()).isZero();
        assertThat(reader).isNotNull();
        assertThat(reader.isDaemon()).isFalse();
        assertThat(reader.isAlive()).isFalse();
        assertThat(readerFailure.get()).isNull();
    }

    @Test
    public void dirtyCloseSavesSynchronouslyBeforeClosing() throws Exception {
        Path workspace = workspacePath("close", "workspace.fpg");
        RecordingWriter writer = new RecordingWriter();
        TestScheduler scheduler = new TestScheduler();
        GraphWorkspaceStore store = GraphWorkspaceStore.create(workspace, codec(), writer, scheduler);
        List<WorkspaceStoreEvent> events = new ArrayList<WorkspaceStoreEvent>();
        store.addListener(events::add);
        store.execute(addMap(MAP_ONE));

        store.close();

        assertThat(writer.writes()).hasSize(2);
        assertThat(codec().read(workspace)).isEqualTo(store.currentDocument());
        assertThat(store.isDirty()).isFalse();
        assertThat(events).extracting(WorkspaceStoreEvent::type)
            .containsExactly(WorkspaceStoreEvent.Type.DOCUMENT_CHANGED, WorkspaceStoreEvent.Type.SAVED);
        assertThat(scheduler.tasks().get(0).cancelled()).isTrue();
        scheduler.run(0);
        assertThat(writer.writes()).hasSize(2);
        assertThat(scheduler.shutdownCalls()).isZero();
        assertThatThrownBy(() -> store.execute(addMap(MAP_TWO))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> store.updateViewport(viewport(1, 1, 1))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(store::undo).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(store::redo).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(store::saveNow).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> store.saveAs(workspacePath("closed-target", "target.fpg")))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> store.addListener(event -> {
        })).isInstanceOf(IllegalStateException.class);

        store.close();
        store.discardAndClose();
        assertThat(writer.writes()).hasSize(2);
    }

    @Test
    public void leaveFailedCloseOpenForRetryAndAllowExplicitDiscardWithoutWriting() throws Exception {
        Path workspace = workspacePath("failed-close", "workspace.fpg");
        RecordingWriter writer = new RecordingWriter();
        TestScheduler scheduler = new TestScheduler();
        GraphWorkspaceStore store = GraphWorkspaceStore.create(workspace, codec(), writer, scheduler);
        byte[] beforeChanges = Files.readAllBytes(workspace);
        store.execute(addMap(MAP_ONE));
        WorkspaceDocument dirtyDocument = store.currentDocument();
        List<WorkspaceStoreEvent> events = new ArrayList<WorkspaceStoreEvent>();
        store.addListener(events::add);
        RuntimeException failure = new IllegalStateException("injected close failure");
        writer.failNext(failure);

        Throwable thrown = catchThrowable(store::close);

        assertThat(thrown).isSameAs(failure);
        assertThat(store.isDirty()).isTrue();
        assertThat(store.currentDocument()).isSameAs(dirtyDocument);
        assertThat(store.currentDocument().maps()).extracting(MapReference::id).containsExactly(MAP_ONE);
        assertThat(events).extracting(WorkspaceStoreEvent::type)
            .containsExactly(WorkspaceStoreEvent.Type.SAVE_FAILED);
        assertThat(events.get(0).document()).isSameAs(dirtyDocument);
        assertThat(events.get(0).error()).containsSame(failure);

        GraphCommandResult undo = store.undo();

        assertThat(undo.status()).isEqualTo(GraphCommandResult.Status.APPLIED);
        assertThat(events).extracting(WorkspaceStoreEvent::type)
            .containsExactly(WorkspaceStoreEvent.Type.SAVE_FAILED, WorkspaceStoreEvent.Type.DOCUMENT_CHANGED);
        assertThat(events.get(1).document()).isEqualTo(store.currentDocument());
        assertThat(store.updateViewport(viewport(3, 4, 1.5)).status()).isEqualTo(GraphCommandResult.Status.APPLIED);
        store.saveNow();
        store.close();
        assertThat(store.isDirty()).isFalse();

        Path discardedWorkspace = workspacePath("discard", "workspace.fpg");
        RecordingWriter discardWriter = new RecordingWriter();
        TestScheduler discardScheduler = new TestScheduler();
        GraphWorkspaceStore discarded = GraphWorkspaceStore.create(discardedWorkspace, codec(), discardWriter,
            discardScheduler);
        byte[] persistedBeforeDiscard = Files.readAllBytes(discardedWorkspace);
        discarded.execute(addMap(MAP_ONE));

        discarded.discardAndClose();

        assertThat(discardWriter.writes()).hasSize(1);
        assertThat(Files.readAllBytes(discardedWorkspace)).isEqualTo(persistedBeforeDiscard);
        assertThat(discarded.isDirty()).isFalse();
        assertThat(discarded.currentDocument().maps()).extracting(MapReference::id).containsExactly(MAP_ONE);
        assertThat(discardScheduler.tasks().get(0).cancelled()).isTrue();
        discardScheduler.run(0);
        assertThat(discardWriter.writes()).hasSize(1);
        assertThat(discardScheduler.shutdownCalls()).isZero();
        assertThatThrownBy(() -> discarded.execute(addMap(MAP_TWO))).isInstanceOf(IllegalStateException.class);
        assertThat(beforeChanges).isNotEmpty();
    }

    @Test
    public void executeWithCompensationRestoresCleanPersistedBytesAndIsIdempotent() throws Exception {
        Path workspace = workspacePath("compensation-clean", "workspace.fpg");
        RecordingWriter writer = new RecordingWriter();
        TestScheduler scheduler = new TestScheduler();
        GraphWorkspaceStore store = GraphWorkspaceStore.create(workspace, codec(), writer, scheduler);
        byte[] beforeBytes = Files.readAllBytes(workspace);

        GraphWorkspaceStore.WorkspaceMutation mutation = store.executeWithCompensation(addMap(MAP_ONE));

        assertThat(mutation.result().status()).isEqualTo(GraphCommandResult.Status.APPLIED);
        assertThat(store.isDirty()).isTrue();
        assertThat(Files.readAllBytes(workspace)).isEqualTo(beforeBytes);

        GraphCommandResult compensation = mutation.compensateIfCurrent();

        assertThat(compensation.status()).isEqualTo(GraphCommandResult.Status.APPLIED);
        assertThat(store.currentDocument().maps()).isEmpty();
        assertThat(store.isDirty()).isFalse();
        assertThat(Files.readAllBytes(workspace)).isEqualTo(beforeBytes);
        assertThat(mutation.compensateIfCurrent()).isEqualTo(compensation);
    }

    @Test
    public void compensatesAfterAutosaveAndRestoresTheCapturedPersistedBytes() throws Exception {
        Path workspace = workspacePath("compensation-autosave", "workspace.fpg");
        RecordingWriter writer = new RecordingWriter();
        TestScheduler scheduler = new TestScheduler();
        GraphWorkspaceStore store = GraphWorkspaceStore.create(workspace, codec(), writer, scheduler);
        byte[] beforeBytes = Files.readAllBytes(workspace);

        GraphWorkspaceStore.WorkspaceMutation mutation = store.executeWithCompensation(addMap(MAP_ONE));
        scheduler.run(0);
        byte[] afterBytes = Files.readAllBytes(workspace);
        assertThat(afterBytes).isNotEqualTo(beforeBytes);
        assertThat(store.isDirty()).isFalse();

        GraphCommandResult compensation = mutation.compensateIfCurrent();

        assertThat(compensation.status()).isEqualTo(GraphCommandResult.Status.APPLIED);
        assertThat(Files.readAllBytes(workspace)).isEqualTo(beforeBytes);
        assertThat(store.currentDocument().maps()).isEmpty();
        assertThat(store.isDirty()).isFalse();
    }

    @Test
    public void rejectsCompensationAfterInterpositionOrSaveAsWithoutChangingCurrentState() throws Exception {
        Path workspace = workspacePath("compensation-interposition", "workspace.fpg");
        Path target = workspacePath("compensation-interposition-target", "renamed.fpg");
        GraphWorkspaceStore store = GraphWorkspaceStore.create(workspace, codec(), new RecordingWriter(),
            new TestScheduler());
        GraphWorkspaceStore.WorkspaceMutation mutation = store.executeWithCompensation(
            addMap(MAP_ONE, URI.create("maps/one.mm")));
        store.execute(new WorkspaceCommand() {
            @Override
            public WorkspaceTransition apply(final WorkspaceDocument current) {
                return WorkspaceTransition.applied(current.toBuilder().build(), "test.equal_interposition");
            }
        });

        GraphCommandResult interposed = mutation.compensateIfCurrent();

        assertThat(interposed.status()).isEqualTo(GraphCommandResult.Status.REJECTED);
        assertThat(store.currentDocument().maps()).extracting(MapReference::id).containsExactly(MAP_ONE);
        store.saveAs(target);
        GraphCommandResult replaced = mutation.compensateIfCurrent();
        assertThat(replaced.status()).isEqualTo(GraphCommandResult.Status.REJECTED);
        assertThat(store.currentDocument().id()).isEqualTo(codec().read(target).id());
    }

    @Test
    public void restoresAnAlreadyDirtyEnvelopeWhenCompensationSucceeds() throws Exception {
        Path workspace = workspacePath("compensation-dirty", "workspace.fpg");
        RecordingWriter writer = new RecordingWriter();
        TestScheduler scheduler = new TestScheduler();
        GraphWorkspaceStore store = GraphWorkspaceStore.create(workspace, codec(), writer, scheduler);

        store.execute(addMap(MAP_ONE, URI.create("maps/one.mm")));
        GraphWorkspaceStore.WorkspaceMutation mutation = store.executeWithCompensation(
            addMap(MAP_TWO, URI.create("maps/two.mm")));
        GraphCommandResult compensation = mutation.compensateIfCurrent();

        assertThat(compensation.status()).isEqualTo(GraphCommandResult.Status.APPLIED);
        assertThat(store.currentDocument().maps()).extracting(MapReference::id).containsExactly(MAP_ONE);
        assertThat(store.isDirty()).isTrue();
        scheduler.run(scheduler.tasks().size() - 1);
        assertThat(store.isDirty()).isFalse();
        assertThat(codec().read(workspace)).isEqualTo(store.currentDocument());
    }

    @Test
    public void keepsCompensationRetryableAfterATransientRestoreWriteFailure() throws Exception {
        Path workspace = workspacePath("compensation-retry", "workspace.fpg");
        RecordingWriter writer = new RecordingWriter();
        TestScheduler scheduler = new TestScheduler();
        GraphWorkspaceStore store = GraphWorkspaceStore.create(workspace, codec(), writer, scheduler);
        byte[] beforeBytes = Files.readAllBytes(workspace);
        GraphWorkspaceStore.WorkspaceMutation mutation = store.executeWithCompensation(addMap(MAP_ONE));
        scheduler.run(0);
        writer.failNext(new IllegalStateException("injected compensation failure"));

        GraphCommandResult first = mutation.compensateIfCurrent();
        GraphCommandResult second = mutation.compensateIfCurrent();

        assertThat(first.status()).isEqualTo(GraphCommandResult.Status.REJECTED);
        assertThat(first.messageKey()).isEqualTo("graph_workspace.history.compensation_incomplete");
        assertThat(second.status()).isEqualTo(GraphCommandResult.Status.APPLIED);
        assertThat(Files.readAllBytes(workspace)).isEqualTo(beforeBytes);
        assertThat(store.currentDocument().maps()).isEmpty();
    }

    private WorkspaceXmlCodec codec() {
        return new WorkspaceXmlCodec(new WorkspaceMigrationRegistry(Collections.<WorkspaceMigration>emptyList()));
    }

    private Path workspacePath(String directoryName, String fileName) throws IOException {
        return temporaryFolder.newFolder(directoryName).toPath().resolve(fileName);
    }

    private static Path canonical(Path path) {
        return new WorkspaceUriResolver().canonical(path);
    }

    private static WorkspaceCommand addMap(MapReferenceId id) {
        return addMap(id, URI.create("maps/one.mm"));
    }

    private static WorkspaceCommand addMap(MapReferenceId id, URI uri) {
        return WorkspaceCommands.addMap(id, uri);
    }

    private static Viewport viewport(double centerX, double centerY, double zoom) {
        return Viewport.of(centerX, centerY, zoom, Collections.<UnknownXml>emptyList());
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static final class RecordingWriter implements AtomicWorkspaceWriter {
        private final List<Write> writes = new ArrayList<Write>();
        private RuntimeException nextFailure;

        @Override
        public void write(Path target, byte[] bytes) {
            if (nextFailure != null) {
                RuntimeException failure = nextFailure;
                nextFailure = null;
                throw failure;
            }
            byte[] copy = Arrays.copyOf(bytes, bytes.length);
            writes.add(new Write(target, copy));
            try {
                Files.write(target, copy);
            }
            catch (IOException exception) {
                throw new AssertionError(exception);
            }
        }

        void failNext(RuntimeException failure) {
            nextFailure = failure;
        }

        List<Write> writes() {
            return writes;
        }
    }

    private static final class Write {
        private final Path target;
        private final byte[] bytes;

        private Write(Path target, byte[] bytes) {
            this.target = target;
            this.bytes = bytes;
        }

        private Path target() {
            return target;
        }

        @SuppressWarnings("unused")
        private byte[] bytes() {
            return Arrays.copyOf(bytes, bytes.length);
        }
    }

    private static final class TestScheduler extends AbstractExecutorService implements ScheduledExecutorService {
        private final List<TestScheduledFuture> tasks = new ArrayList<TestScheduledFuture>();
        private boolean rejectScheduling;
        private boolean shutdown;
        private int shutdownCalls;

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            if (rejectScheduling) {
                throw new RejectedExecutionException("injected scheduler rejection");
            }
            TestScheduledFuture future = new TestScheduledFuture(command, delay, unit);
            tasks.add(future);
            return future;
        }

        @Override
        public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period,
                TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay,
                TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void shutdown() {
            shutdownCalls++;
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdownCalls++;
            shutdown = true;
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        void rejectScheduling() {
            rejectScheduling = true;
        }

        List<TestScheduledFuture> tasks() {
            return tasks;
        }

        void run(int index) {
            tasks.get(index).runEvenIfCancelled();
        }

        int shutdownCalls() {
            return shutdownCalls;
        }
    }

    private static final class TestScheduledFuture implements ScheduledFuture<Object> {
        private final Runnable command;
        private final long delay;
        private final TimeUnit unit;
        private boolean cancelled;
        private boolean done;
        private boolean mayInterruptIfRunning;

        private TestScheduledFuture(Runnable command, long delay, TimeUnit unit) {
            this.command = command;
            this.delay = delay;
            this.unit = unit;
        }

        @Override
        public long getDelay(TimeUnit requestedUnit) {
            return requestedUnit.convert(delay, unit);
        }

        @Override
        public int compareTo(Delayed other) {
            return 0;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            if (done) {
                return false;
            }
            cancelled = true;
            this.mayInterruptIfRunning = mayInterruptIfRunning;
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return done || cancelled;
        }

        @Override
        public Object get() throws InterruptedException, ExecutionException {
            return null;
        }

        @Override
        public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException,
                TimeoutException {
            return null;
        }

        private long delay() {
            return delay;
        }

        private TimeUnit unit() {
            return unit;
        }

        private boolean cancelled() {
            return cancelled;
        }

        private boolean mayInterruptIfRunning() {
            return mayInterruptIfRunning;
        }

        private void runEvenIfCancelled() {
            command.run();
            done = true;
        }
    }
}
