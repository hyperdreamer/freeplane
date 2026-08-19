package org.freeplane.plugin.graph.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.freeplane.plugin.graph.adapter.EdtExecutor;
import org.freeplane.plugin.graph.control.AcceptedBatch;
import org.freeplane.plugin.graph.control.CanvasState;
import org.freeplane.plugin.graph.control.ChangeKind;
import org.freeplane.plugin.graph.control.GraphUpdateCoordinator;
import org.freeplane.plugin.graph.control.LayoutSettleLoop;
import org.freeplane.plugin.graph.control.OperationalStatus;
import org.freeplane.plugin.graph.control.WorkspaceMapCoordinator;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.ProjectionEngine;
import org.freeplane.plugin.graph.projection.RecoverableReason;
import org.freeplane.plugin.graph.projection.RelationshipResolution;
import org.freeplane.plugin.graph.projection.RelationshipStatus;
import org.freeplane.plugin.graph.projection.input.ProjectionInput;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.AtomicWorkspaceWriter;
import org.freeplane.plugin.graph.workspace.GraphCommandResult;
import org.freeplane.plugin.graph.workspace.GraphWorkspaceStore;
import org.freeplane.plugin.graph.workspace.WorkspaceCommand;
import org.freeplane.plugin.graph.workspace.WorkspaceCommands;
import org.freeplane.plugin.graph.workspace.WorkspaceTransition;
import org.freeplane.plugin.graph.workspace.io.WorkspaceMigration;
import org.freeplane.plugin.graph.workspace.io.WorkspaceMigrationRegistry;
import org.freeplane.plugin.graph.workspace.io.WorkspaceXmlCodec;
import org.freeplane.plugin.graph.workspace.model.GraphRelationshipRecord;
import org.freeplane.plugin.graph.workspace.model.MapReference;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.freeplane.plugin.graph.workspace.model.RelationshipDirection;
import org.freeplane.plugin.graph.workspace.model.RelationshipId;
import org.freeplane.plugin.graph.workspace.model.UnknownXml;
import org.freeplane.plugin.graph.workspace.model.WorkspaceDocument;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;

public class DefaultPurgeCommandHandlerShould {
    private static final WorkspaceId WORKSPACE_ID =
        WorkspaceId.of("00000000-0000-0000-0000-000000000301");
    private static final MapReferenceId MAP_ONE =
        MapReferenceId.of("00000000-0000-0000-0000-000000000311");
    private static final MapReferenceId MAP_TWO =
        MapReferenceId.of("00000000-0000-0000-0000-000000000312");
    private static final RelationshipId MISSING_ONE =
        RelationshipId.of("00000000-0000-0000-0000-000000000321");
    private static final RelationshipId MISSING_TWO =
        RelationshipId.of("00000000-0000-0000-0000-000000000322");
    private static final RelationshipId ACTIVE =
        RelationshipId.of("00000000-0000-0000-0000-000000000323");
    private static final RelationshipId RECOVERABLE =
        RelationshipId.of("00000000-0000-0000-0000-000000000324");

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void rejectsAnEmptyRelationshipSetWithoutMutatingTheStore() {
        Fixture fixture = fixture(projection(7L, missing(MISSING_ONE)), false);

        GraphCommandResult result = fixture.handler.purge(GraphCommands.purge(7L,
            Collections.<RelationshipId>emptySet()));

        assertRejected(result);
        assertNoStoreMutation(fixture);
    }

    @Test
    public void rejectsAStaleDisplayedGenerationWithoutMutatingTheStore() {
        Fixture fixture = fixture(projection(8L, missing(MISSING_ONE)), false);

        GraphCommandResult result = fixture.handler.purge(GraphCommands.purge(7L,
            Collections.singleton(MISSING_ONE)));

        assertRejected(result);
        assertNoStoreMutation(fixture);
    }

    @Test
    public void rejectsWhenAQueuedMapChangeCouldInvalidateTheCurrentRecord() {
        Fixture fixture = fixture(projection(7L, missing(MISSING_ONE)), true);

        GraphCommandResult result = fixture.handler.purge(GraphCommands.purge(7L,
            Collections.singleton(MISSING_ONE)));

        assertRejected(result);
        assertNoStoreMutation(fixture);
    }

    @Test
    public void rejectsWhenAReloadQueuesBeforeTheFinalExactRecordValidation() {
        Fixture fixture = fixture(projection(7L, missing(MISSING_ONE)), false);
        when(fixture.updates.hasPendingChanges()).thenReturn(false, true);

        GraphCommandResult result = fixture.handler.purge(GraphCommands.purge(7L,
            Collections.singleton(MISSING_ONE)));

        assertRejected(result);
        assertNoStoreMutation(fixture);
    }

    @Test
    public void rejectsActiveAndRecoverableRelationshipsAsAWholeRequest() {
        assertRejectsNonMissingRelationship(RelationshipStatus.ACTIVE, ACTIVE);
        assertRejectsNonMissingRelationship(RelationshipStatus.UNRESOLVED_RECOVERABLE, RECOVERABLE);
    }

    @Test
    public void rejectsWhenTheCoordinatorProjectionIsUnavailable() {
        Fixture fixture = fixture(null, false);

        GraphCommandResult result = fixture.handler.purge(GraphCommands.purge(7L,
            Collections.singleton(MISSING_ONE)));

        assertRejected(result);
        assertNoStoreMutation(fixture);
    }

    @Test
    public void rejectsWhenARealCoordinatorReportsARebuildFailure() throws Exception {
        WorkspaceMapCoordinator maps = mock(WorkspaceMapCoordinator.class);
        ProjectionEngine engine = mock(ProjectionEngine.class);
        LayoutSettleLoop loop = mock(LayoutSettleLoop.class);
        when(maps.capture(any(AcceptedBatch.class))).thenReturn(mock(ProjectionInput.class));
        when(engine.project(any(ProjectionInput.class))).thenReturn(projection(1L, missing(MISSING_ONE)))
            .thenThrow(new IllegalStateException("rebuild failure"));

        GraphUpdateCoordinator updates = new GraphUpdateCoordinator(maps, engine, loop);
        GraphWorkspaceStore store = mock(GraphWorkspaceStore.class);
        WorkspaceDocument document = document(record(MISSING_ONE, 1L));
        when(store.currentDocument()).thenReturn(document);
        when(store.execute(any(WorkspaceCommand.class))).thenReturn(
            GraphCommandResult.from(WorkspaceTransition.applied(document, "test.purged")));
        RecordingEdt edt = new RecordingEdt();
        DefaultPurgeCommandHandler handler = new DefaultPurgeCommandHandler(updates, store, edt);
        CountDownLatch failed = new CountDownLatch(1);
        updates.addProjectionListener(value -> {
            if (value.generation() == 1L) {
                updates.requestRebuild(ChangeKind.MAP_STATE);
            }
        });
        updates.addCanvasStateListener(state -> {
            if (state.status() == OperationalStatus.FAILED) {
                failed.countDown();
            }
        });

        try {
            updates.start();
            assertThat(failed.await(5L, TimeUnit.SECONDS)).isTrue();
            assertThat(updates.currentProjection().generation()).isEqualTo(1L);
            assertThat(updates.currentState().status()).isEqualTo(OperationalStatus.FAILED);

            GraphCommandResult result = handler.purge(GraphCommands.purge(1L,
                Collections.singleton(MISSING_ONE)));

            assertRejected(result);
            verify(store, never()).execute(any(WorkspaceCommand.class));
        }
        finally {
            updates.close();
        }
    }

    @Test
    public void rejectsWhenARequestedRelationshipHasNoCurrentRecord() {
        Fixture fixture = fixture(projection(7L, missing(MISSING_ONE)), false);

        GraphCommandResult result = fixture.handler.purge(GraphCommands.purge(7L,
            Collections.singleton(MISSING_TWO)));

        assertRejected(result);
        assertNoStoreMutation(fixture);
    }

    @Test
    public void revalidatesExactCurrentRecordsImmediatelyBeforeTheWorkspaceTransition() {
        GraphProjection before = projection(7L, missing(MISSING_ONE));
        GraphProjection after = projection(7L, active(MISSING_ONE));
        Fixture fixture = fixture(before, false);
        when(fixture.updates.currentProjection()).thenReturn(before, after);

        GraphCommandResult result = fixture.handler.purge(GraphCommands.purge(7L,
            Collections.singleton(MISSING_ONE)));

        assertRejected(result);
        assertNoStoreMutation(fixture);
    }

    @Test
    public void validatesAndExecutesOneTransitionOnTheEdtWithOnlyMissingRelationships() {
        GraphRelationshipRecord missingRecord = record(MISSING_ONE, 1L);
        GraphRelationshipRecord activeRecord = record(ACTIVE, 2L);
        WorkspaceDocument document = document(missingRecord, activeRecord);
        GraphProjection projection = projection(9L, missing(MISSING_ONE), active(ACTIVE));
        Fixture fixture = fixture(projection, false, document);
        ArgumentCaptor<WorkspaceCommand> command = ArgumentCaptor.forClass(WorkspaceCommand.class);
        when(fixture.updates.currentProjection()).thenAnswer(invocation -> {
            assertThat(fixture.edt.isEdt()).isTrue();
            return projection;
        });
        when(fixture.updates.hasPendingChanges()).thenAnswer(invocation -> {
            assertThat(fixture.edt.isEdt()).isTrue();
            return false;
        });
        when(fixture.store.execute(any(WorkspaceCommand.class))).thenAnswer(invocation -> {
            assertThat(fixture.edt.isEdt()).isTrue();
            return GraphCommandResult.from(WorkspaceTransition.applied(document, "test.purged"));
        });

        GraphCommandResult result = fixture.handler.purge(GraphCommands.purge(9L,
            Collections.singleton(MISSING_ONE)));

        assertThat(result.status()).isEqualTo(GraphCommandResult.Status.APPLIED);
        assertThat(fixture.edt.callCount).isEqualTo(1);
        verify(fixture.store).execute(command.capture());
        WorkspaceTransition transition = command.getValue().apply(document);
        assertThat(transition.after().relationships()).containsExactly(activeRecord);
        assertThat(fixture.edt.accessedFromEdt).isTrue();
    }

    @Test
    public void workspaceUndoRestoresEveryPurgedRelationship() throws Exception {
        Path workspace = temporaryFolder.newFolder("purge-undo").toPath().resolve("workspace.fpg");
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        GraphWorkspaceStore store = GraphWorkspaceStore.create(workspace, codec(), new FileWriter(), scheduler);
        try {
            assertApplied(store.execute(WorkspaceCommands.addMap(MAP_ONE, URI.create("one.mm"))));
            assertApplied(store.execute(WorkspaceCommands.addMap(MAP_TWO, URI.create("two.mm"))));
            assertApplied(store.execute(WorkspaceCommands.createRelationship(MISSING_ONE,
                node(MAP_ONE, "source-one"), node(MAP_TWO, "target-one"), RelationshipDirection.FORWARD)));
            assertApplied(store.execute(WorkspaceCommands.createRelationship(MISSING_TWO,
                node(MAP_ONE, "source-two"), node(MAP_TWO, "target-two"), RelationshipDirection.FORWARD)));
            List<GraphRelationshipRecord> before = store.currentDocument().relationships();

            GraphUpdateCoordinator updates = mock(GraphUpdateCoordinator.class);
            when(updates.currentProjection()).thenReturn(projection(11L, missing(MISSING_ONE), missing(MISSING_TWO)));
            CanvasState state = availableState();
            when(updates.currentState()).thenReturn(state);
            when(updates.hasPendingChanges()).thenReturn(false);
            RecordingEdt edt = new RecordingEdt();
            DefaultPurgeCommandHandler handler = new DefaultPurgeCommandHandler(updates, store, edt);
            Set<RelationshipId> requested = new LinkedHashSet<RelationshipId>(
                Arrays.asList(MISSING_ONE, MISSING_TWO));

            GraphCommandResult purged = handler.purge(GraphCommands.purge(11L, requested));

            assertThat(purged.status()).isEqualTo(GraphCommandResult.Status.APPLIED);
            assertThat(store.currentDocument().relationships()).isEmpty();
            assertThat(store.undo().status()).isEqualTo(GraphCommandResult.Status.APPLIED);
            assertThat(store.currentDocument().relationships()).containsExactlyElementsOf(before);
        }
        finally {
            store.discardAndClose();
            scheduler.shutdownNow();
        }
    }

    private void assertRejectsNonMissingRelationship(RelationshipStatus status, RelationshipId id) {
        Fixture fixture = fixture(projection(7L, missing(MISSING_ONE), resolution(id, status)), false);
        Set<RelationshipId> requested = new LinkedHashSet<RelationshipId>(Arrays.asList(MISSING_ONE, id));

        GraphCommandResult result = fixture.handler.purge(GraphCommands.purge(7L, requested));

        assertRejected(result);
        assertNoStoreMutation(fixture);
    }

    private Fixture fixture(GraphProjection projection, boolean pending) {
        return fixture(projection, pending, document(record(MISSING_ONE, 1L)));
    }

    private Fixture fixture(GraphProjection projection, boolean pending, WorkspaceDocument document) {
        GraphWorkspaceStore store = mock(GraphWorkspaceStore.class);
        GraphUpdateCoordinator updates = mock(GraphUpdateCoordinator.class);
        RecordingEdt edt = new RecordingEdt();
        when(store.currentDocument()).thenReturn(document);
        when(store.execute(any(WorkspaceCommand.class))).thenReturn(
            GraphCommandResult.from(WorkspaceTransition.applied(document, "test.purged")));
        when(updates.currentProjection()).thenReturn(projection);
        CanvasState state = availableState();
        when(updates.currentState()).thenReturn(state);
        when(updates.hasPendingChanges()).thenReturn(pending);
        return new Fixture(new DefaultPurgeCommandHandler(updates, store, edt), updates, store, edt);
    }

    private static CanvasState availableState() {
        CanvasState state = mock(CanvasState.class);
        when(state.status()).thenReturn(OperationalStatus.IDLE);
        return state;
    }

    private static void assertNoStoreMutation(Fixture fixture) {
        verify(fixture.store, never()).execute(any(WorkspaceCommand.class));
    }

    private static void assertRejected(GraphCommandResult result) {
        assertThat(result.status()).isEqualTo(GraphCommandResult.Status.REJECTED);
    }

    private static void assertApplied(GraphCommandResult result) {
        assertThat(result.status()).isEqualTo(GraphCommandResult.Status.APPLIED);
    }

    private static GraphProjection projection(long generation, RelationshipResolution... resolutions) {
        return GraphProjection.resolved(generation, Collections.emptyList(), Collections.emptyList(),
            Arrays.asList(resolutions), Collections.emptyList());
    }

    private static RelationshipResolution missing(RelationshipId id) {
        return resolution(id, RelationshipStatus.UNRESOLVED_MISSING_NODE);
    }

    private static RelationshipResolution active(RelationshipId id) {
        return resolution(id, RelationshipStatus.ACTIVE);
    }

    private static RelationshipResolution resolution(RelationshipId id, RelationshipStatus status) {
        GraphRelationshipRecord relationship = record(id, id.value().getLeastSignificantBits() & Long.MAX_VALUE);
        Optional<ProjectedEndpointKey> source = Optional.empty();
        Optional<ProjectedEndpointKey> target = Optional.empty();
        Set<RecoverableReason> reasons = Collections.emptySet();
        if (status == RelationshipStatus.ACTIVE) {
            source = Optional.of(endpoint(MAP_ONE, "source"));
            target = Optional.of(endpoint(MAP_TWO, "target"));
        }
        else if (status == RelationshipStatus.UNRESOLVED_RECOVERABLE) {
            target = Optional.of(endpoint(MAP_TWO, "target"));
            reasons = EnumSet.of(RecoverableReason.MAP_INACTIVE);
        }
        return RelationshipResolution.of(relationship, status, source, target, reasons);
    }

    private static ProjectedEndpointKey endpoint(MapReferenceId map, String id) {
        return ProjectedEndpointKey.ofNode(ProjectedNodeKey.of(SourceNodeKey.persisted(node(map, id))));
    }

    private static GraphRelationshipRecord record(RelationshipId id, long sequence) {
        long positiveSequence = sequence <= 0L ? 1L : sequence;
        return GraphRelationshipRecord.of(id, positiveSequence, node(MAP_ONE, "source-" + id),
            node(MAP_TWO, "target-" + id), RelationshipDirection.FORWARD, Collections.<UnknownXml>emptyList());
    }

    private static WorkspaceDocument document(GraphRelationshipRecord... relationships) {
        return WorkspaceDocument.createVersion1(WORKSPACE_ID).toBuilder()
            .maps(Arrays.asList(
                MapReference.of(MAP_ONE, 1L, URI.create("one.mm"), true, "#4E79A7",
                    Collections.<UnknownXml>emptyList()),
                MapReference.of(MAP_TWO, 2L, URI.create("two.mm"), true, "#F28E2B",
                    Collections.<UnknownXml>emptyList())))
            .relationships(Arrays.asList(relationships))
            .build();
    }

    private static NodeReference node(MapReferenceId map, String id) {
        return NodeReference.of(map, PersistedNodeId.of(id));
    }

    private WorkspaceXmlCodec codec() {
        return new WorkspaceXmlCodec(new WorkspaceMigrationRegistry(Collections.<WorkspaceMigration>emptyList()));
    }

    private static final class Fixture {
        private final DefaultPurgeCommandHandler handler;
        private final GraphUpdateCoordinator updates;
        private final GraphWorkspaceStore store;
        private final RecordingEdt edt;

        private Fixture(DefaultPurgeCommandHandler handler, GraphUpdateCoordinator updates,
                GraphWorkspaceStore store, RecordingEdt edt) {
            this.handler = handler;
            this.updates = updates;
            this.store = store;
            this.edt = edt;
        }
    }

    private static final class RecordingEdt implements EdtExecutor {
        private int callCount;
        private boolean accessedFromEdt;
        private boolean inCall;

        @Override
        public <T> T call(Callable<T> task) {
            callCount++;
            inCall = true;
            accessedFromEdt = true;
            try {
                return task.call();
            }
            catch (Exception failure) {
                throw new AssertionError(failure);
            }
            finally {
                inCall = false;
            }
        }

        @Override
        public void execute(Runnable task) {
            inCall = true;
            try {
                task.run();
            }
            finally {
                inCall = false;
            }
        }

        @Override
        public boolean isEdt() {
            accessedFromEdt |= inCall;
            return inCall;
        }
    }

    private static final class FileWriter implements AtomicWorkspaceWriter {
        @Override
        public void write(Path target, byte[] bytes) {
            try {
                Files.write(target, bytes);
            }
            catch (IOException failure) {
                throw new AssertionError(failure);
            }
        }
    }
}
