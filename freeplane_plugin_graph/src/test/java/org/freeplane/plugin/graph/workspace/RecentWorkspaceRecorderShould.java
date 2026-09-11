package org.freeplane.plugin.graph.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
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
    public void ignoresDocumentChangedSavedAndSaveFailedEvents() throws Exception {
        Fixture fixture = fixture();
        WorkspaceDocument document = WorkspaceDocument.createVersion1(
            WorkspaceId.of("00000000-0000-0000-0000-000000000001"));
        Path newPath = temporaryFolder.newFile("mismatched-target.fpg").toPath().toRealPath();
        WorkspaceIdentityChange change = new WorkspaceIdentityChange(
            temporaryFolder.getRoot().toPath().resolve("mismatched-source.fpg"), newPath,
            WorkspaceId.of("00000000-0000-0000-0000-000000000001"),
            WorkspaceId.of("00000000-0000-0000-0000-000000000002"));
        WorkspaceStoreEvent mismatched = savedEventCarryingIdentityChange(document, change);

        assertThat(mismatched.type()).isEqualTo(WorkspaceStoreEvent.Type.SAVED);
        assertThat(mismatched.identityChange()).contains(change);

        fixture.listener().onWorkspaceStoreEvent(WorkspaceStoreEvent.documentChanged(document));
        fixture.listener().onWorkspaceStoreEvent(WorkspaceStoreEvent.saved(document));
        fixture.listener().onWorkspaceStoreEvent(WorkspaceStoreEvent.saveFailed(document,
            new IllegalStateException("save failed")));
        fixture.listener().onWorkspaceStoreEvent(mismatched);

        assertThat(fixture.list.hasStoredEntries()).isFalse();
        assertThat(fixture.persisterCalls).hasValue(0);
    }

    private static WorkspaceStoreEvent savedEventCarryingIdentityChange(final WorkspaceDocument document,
            final WorkspaceIdentityChange change) throws Exception {
        Constructor<WorkspaceStoreEvent> constructor = WorkspaceStoreEvent.class.getDeclaredConstructor(
            WorkspaceStoreEvent.Type.class, WorkspaceDocument.class, WorkspaceIdentityChange.class,
            Throwable.class);
        constructor.setAccessible(true);
        return constructor.newInstance(WorkspaceStoreEvent.Type.SAVED, document, change, null);
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
