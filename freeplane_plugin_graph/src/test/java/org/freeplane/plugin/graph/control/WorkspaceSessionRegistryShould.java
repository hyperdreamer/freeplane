package org.freeplane.plugin.graph.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.freeplane.plugin.graph.workspace.AtomicWorkspaceWriter;
import org.freeplane.plugin.graph.workspace.GraphWorkspaceStore;
import org.freeplane.plugin.graph.workspace.WorkspaceIdentityChange;
import org.freeplane.plugin.graph.workspace.WorkspaceUriResolver;
import org.freeplane.plugin.graph.workspace.io.WorkspaceMigration;
import org.freeplane.plugin.graph.workspace.io.WorkspaceMigrationRegistry;
import org.freeplane.plugin.graph.workspace.io.WorkspaceXmlCodec;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class WorkspaceSessionRegistryShould {
    private static final WorkspaceSessionId SESSION_A =
        WorkspaceSessionId.of("00000000-0000-0000-0000-000000000100");
    private static final WorkspaceSessionId SESSION_B =
        WorkspaceSessionId.of("00000000-0000-0000-0000-000000000200");
    private static final WorkspaceSessionId SESSION_C =
        WorkspaceSessionId.of("00000000-0000-0000-0000-000000000300");
    private static final WorkspaceSessionId SESSION_D =
        WorkspaceSessionId.of("00000000-0000-0000-0000-000000000400");
    private static final long CROSS_THREAD_TIMEOUT_MILLIS = 5000L;

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void canonicalOwnershipLetsDuplicateOpenFindTheOriginalSession() throws Exception {
        Path directory = temporaryFolder.newFolder("alias").toPath();
        Path realDirectory = directory.resolve("real");
        Path aliasDirectory = directory.resolve("alias");
        Files.createDirectories(realDirectory);
        Path realWorkspace = realDirectory.resolve("workspace.fpg");
        Files.write(realWorkspace, bytes("workspace"));
        Path aliasWorkspace = aliasDirectory.resolve("workspace.fpg");
        try {
            Files.createSymbolicLink(aliasDirectory, realDirectory);
        }
        catch (UnsupportedOperationException exception) {
            Assume.assumeTrue("symbolic links are unsupported", false);
            return;
        }
        catch (SecurityException exception) {
            Assume.assumeTrue("symbolic links are not permitted", false);
            return;
        }
        catch (IOException exception) {
            Assume.assumeTrue("symbolic links are unavailable", false);
            return;
        }
        Path normalizedWorkspace = realDirectory.resolve("child").resolve("..").resolve("workspace.fpg");

        WorkspaceSessionRegistry registry = new WorkspaceSessionRegistry();
        WorkspaceSessionId original = SESSION_A;
        WorkspaceSessionId duplicate = SESSION_B;

        assertThat(registry.register(original, aliasWorkspace)).isTrue();
        assertThat(registry.owner(realWorkspace)).contains(original);
        assertThat(registry.owner(normalizedWorkspace)).contains(original);
        assertThat(registry.owner(aliasWorkspace)).contains(original);
        assertThat(registry.register(duplicate, realWorkspace)).isFalse();
        assertThat(registry.owner(realWorkspace)).contains(original);
        assertThat(registry.register(original, normalizedWorkspace)).isTrue();
        assertThatThrownBy(() -> registry.register(original, directory.resolve("other.fpg")))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void serializesConcurrentReservationsForOneCanonicalTarget() throws Exception {
        WorkspaceSessionRegistry registry = new WorkspaceSessionRegistry();
        Path firstPath = workspacePath("concurrent-first", "workspace.fpg");
        Path secondPath = workspacePath("concurrent-second", "workspace.fpg");
        Path target = workspacePath("concurrent-target", "renamed.fpg");
        WorkspaceSessionId first = SESSION_A;
        WorkspaceSessionId second = SESSION_B;
        assertThat(registry.register(first, firstPath)).isTrue();
        assertThat(registry.register(second, secondPath)).isTrue();

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger rejections = new AtomicInteger();
        AtomicReference<WorkspaceSessionId> winner = new AtomicReference<WorkspaceSessionId>();
        AtomicReference<WorkspacePathReservation> winningToken = new AtomicReference<WorkspacePathReservation>();
        AtomicReference<Throwable> unexpected = new AtomicReference<Throwable>();

        Thread firstThread = new Thread(() -> attemptReservation(registry, first, target, start, done, successes,
            rejections, winner, winningToken, unexpected));
        Thread secondThread = new Thread(() -> attemptReservation(registry, second, target, start, done, successes,
            rejections, winner, winningToken, unexpected));
        firstThread.start();
        secondThread.start();
        start.countDown();
        assertThat(done.await(CROSS_THREAD_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue();
        firstThread.join(CROSS_THREAD_TIMEOUT_MILLIS);
        secondThread.join(CROSS_THREAD_TIMEOUT_MILLIS);

        assertThat(unexpected.get()).isNull();
        assertThat(successes.get()).isEqualTo(1);
        assertThat(rejections.get()).isEqualTo(1);
        WorkspaceSessionId winningSession = winner.get();
        assertThat(winningSession).isNotNull();
        assertThat(registry.owner(target)).contains(winningSession);
        winningToken.get().close();
        assertThat(registry.owner(target)).isEmpty();
        WorkspaceSessionId losingSession = winningSession.equals(first) ? second : first;
        WorkspacePathReservation retry = registry.reserveSaveAs(losingSession, target);
        assertThat(registry.owner(target)).contains(losingSession);
        retry.close();
    }

    @Test
    public void rejectsOwnedAndReservedTargetsBeforeTheCallerWritesBytes() throws Exception {
        WorkspaceSessionRegistry registry = new WorkspaceSessionRegistry();
        Path callerPath = workspacePath("caller", "workspace.fpg");
        Path committedOwnerPath = workspacePath("committed-owner", "workspace.fpg");
        Path pendingOwnerPath = workspacePath("pending-owner", "workspace.fpg");
        Path committedTarget = workspacePath("committed-target", "committed.fpg");
        Path pendingTarget = workspacePath("pending-target", "pending.fpg");
        WorkspaceSessionId caller = SESSION_A;
        WorkspaceSessionId committedOwner = SESSION_B;
        WorkspaceSessionId pendingOwner = SESSION_C;
        assertThat(registry.register(caller, callerPath)).isTrue();
        assertThat(registry.register(committedOwner, committedTarget)).isTrue();
        assertThat(registry.register(pendingOwner, pendingOwnerPath)).isTrue();

        int writesAfterCommittedRejection = writesAfterRejectedReservation(registry, caller, committedTarget);
        assertThat(writesAfterCommittedRejection).isZero();
        assertThat(registry.owner(committedTarget)).contains(committedOwner);
        assertThat(registry.owner(callerPath)).contains(caller);

        WorkspacePathReservation pendingReservation = registry.reserveSaveAs(pendingOwner, pendingTarget);
        int writesAfterPendingRejection = writesAfterRejectedReservation(registry, caller, pendingTarget);
        assertThat(writesAfterPendingRejection).isZero();
        assertThat(registry.owner(pendingTarget)).contains(pendingOwner);
        assertThat(registry.owner(pendingOwnerPath)).contains(pendingOwner);
        assertThat(registry.owner(callerPath)).contains(caller);
        pendingReservation.close();
    }

    @Test
    public void closingAfterSaveFailureReleasesOnlyThePendingTarget() throws Exception {
        WorkspaceSessionRegistry registry = new WorkspaceSessionRegistry();
        Path oldPath = workspacePath("failed-save-as", "workspace.fpg");
        Path target = workspacePath("failed-save-as-target", "renamed.fpg");
        Path otherPath = workspacePath("other-session", "workspace.fpg");
        WorkspaceSessionId first = SESSION_A;
        WorkspaceSessionId second = SESSION_B;
        assertThat(registry.register(first, oldPath)).isTrue();
        WorkspacePathReservation firstToken = registry.reserveSaveAs(first, target);
        assertThat(registry.owner(oldPath)).contains(first);
        assertThat(registry.owner(target)).contains(first);

        firstToken.close();
        firstToken.close();

        assertThat(registry.owner(oldPath)).contains(first);
        assertThat(registry.owner(target)).isEmpty();

        assertThat(registry.register(second, otherPath)).isTrue();
        WorkspacePathReservation secondToken = registry.reserveSaveAs(second, target);
        assertThat(registry.owner(target)).contains(second);

        firstToken.close();

        assertThat(registry.owner(target)).contains(second);
        assertThat(registry.owner(otherPath)).contains(second);
        secondToken.close();
    }

    @Test
    public void commitRekeysWithoutObservableUnownedWindow() throws Exception {
        assertThat(Modifier.isFinal(WorkspaceSessionRegistry.class.getModifiers()))
            .as("WorkspaceSessionRegistry must be final so no caller-supplied code can run under its monitor")
            .isTrue();
        Path original = workspacePath("rekey-original", "workspace.fpg");
        Path target = workspacePath("rekey-target", "renamed.fpg");
        WorkspaceSessionId session = SESSION_A;
        WorkspaceSessionRegistry registry = new WorkspaceSessionRegistry();
        assertThat(registry.register(session, original)).isTrue();
        WorkspacePathReservation reservation = registry.reserveSaveAs(session, target);
        assertThat(registry.owner(original)).contains(session);
        assertThat(registry.owner(target)).contains(session);

        GraphWorkspaceStore store = GraphWorkspaceStore.create(original, codec(), new RecordingWriter(),
            new ShutDownScheduler());
        WorkspaceIdentityChange change = store.saveAs(target);

        reservation.commit(change);

        assertThat(change.oldPath()).isEqualTo(canonical(original));
        assertThat(change.newPath()).isEqualTo(canonical(target));
        assertThat(change.oldId()).isNotEqualTo(change.newId());
        assertThat(registry.rekeyObservation().oldPathOwner()).isEqualTo(session);
        assertThat(registry.rekeyObservation().newPathOwner()).isEqualTo(session);
        assertThat(registry.owner(original)).isEmpty();
        assertThat(registry.owner(target)).contains(session);

        reservation.close();
        assertThat(registry.owner(target)).contains(session);
        store.discardAndClose();
    }

    @Test
    public void validatesIdentityChangeAndReservationLifecycle() throws Exception {
        WorkspaceSessionRegistry registry = new WorkspaceSessionRegistry();
        Path original = workspacePath("validate-original", "workspace.fpg");
        Path target = workspacePath("validate-target", "renamed.fpg");
        Path differentTarget = workspacePath("validate-other", "other.fpg");
        Path strayTarget = workspacePath("validate-stray", "stray.fpg");
        Path finalTarget = workspacePath("validate-final", "final.fpg");
        WorkspaceSessionId session = SESSION_A;
        assertThat(registry.register(session, original)).isTrue();
        WorkspacePathReservation reservation = registry.reserveSaveAs(session, target);

        GraphWorkspaceStore store = GraphWorkspaceStore.create(original, codec(), new RecordingWriter(),
            new ShutDownScheduler());
        WorkspaceIdentityChange wrongNewPath = store.saveAs(differentTarget);
        assertThatThrownBy(() -> reservation.commit(wrongNewPath)).isInstanceOf(IllegalArgumentException.class);
        assertThat(registry.owner(original)).contains(session);
        assertThat(registry.owner(target)).contains(session);

        WorkspaceIdentityChange wrongOldPath = store.saveAs(target);
        assertThatThrownBy(() -> reservation.commit(wrongOldPath)).isInstanceOf(IllegalArgumentException.class);
        assertThat(registry.owner(original)).contains(session);
        assertThat(registry.owner(target)).contains(session);

        reservation.close();
        WorkspaceIdentityChange afterClose = store.saveAs(strayTarget);
        assertThatThrownBy(() -> reservation.commit(afterClose)).isInstanceOf(IllegalStateException.class);

        WorkspacePathReservation committedToken = registry.reserveSaveAs(session, finalTarget);
        GraphWorkspaceStore reopened = GraphWorkspaceStore.open(original, codec(), new RecordingWriter(),
            new ShutDownScheduler());
        WorkspaceIdentityChange validChange = reopened.saveAs(finalTarget);
        committedToken.commit(validChange);
        assertThat(registry.owner(finalTarget)).contains(session);
        assertThat(registry.owner(original)).isEmpty();
        committedToken.close();
        assertThat(registry.owner(finalTarget)).contains(session);
        assertThatThrownBy(() -> committedToken.commit(validChange)).isInstanceOf(IllegalStateException.class);
        reopened.discardAndClose();
        store.discardAndClose();
    }

    @Test
    public void unregisterReleasesCommittedAndPendingOwnership() throws Exception {
        WorkspaceSessionRegistry registry = new WorkspaceSessionRegistry();
        Path firstPath = workspacePath("unregister-first", "workspace.fpg");
        Path secondPath = workspacePath("unregister-second", "workspace.fpg");
        Path target = workspacePath("unregister-target", "renamed.fpg");
        Path storePath = workspacePath("unregister-store", "store.fpg");
        Path storeTarget = workspacePath("unregister-store-target", "stored.fpg");
        WorkspaceSessionId first = SESSION_A;
        WorkspaceSessionId second = SESSION_B;
        assertThat(registry.register(first, firstPath)).isTrue();
        assertThat(registry.register(second, secondPath)).isTrue();
        WorkspacePathReservation reservation = registry.reserveSaveAs(first, target);
        assertThat(registry.owner(firstPath)).contains(first);
        assertThat(registry.owner(target)).contains(first);

        registry.unregister(first);

        assertThat(registry.owner(firstPath)).isEmpty();
        assertThat(registry.owner(target)).isEmpty();
        assertThat(registry.owner(secondPath)).contains(second);
        reservation.close();
        reservation.close();
        GraphWorkspaceStore store = GraphWorkspaceStore.create(storePath, codec(), new RecordingWriter(),
            new ShutDownScheduler());
        WorkspaceIdentityChange change = store.saveAs(storeTarget);
        assertThatThrownBy(() -> reservation.commit(change)).isInstanceOf(IllegalStateException.class);
        registry.unregister(first);
        registry.unregister(second);
        assertThat(registry.owner(secondPath)).isEmpty();
        store.discardAndClose();
    }

    private static void attemptReservation(WorkspaceSessionRegistry registry, WorkspaceSessionId session,
            Path target, CountDownLatch start, CountDownLatch done, AtomicInteger successes,
            AtomicInteger rejections, AtomicReference<WorkspaceSessionId> winner,
            AtomicReference<WorkspacePathReservation> winningToken, AtomicReference<Throwable> unexpected) {
        try {
            start.await();
        }
        catch (InterruptedException interruption) {
            unexpected.compareAndSet(null, interruption);
            done.countDown();
            return;
        }
        try {
            WorkspacePathReservation token = registry.reserveSaveAs(session, target);
            successes.incrementAndGet();
            winner.set(session);
            winningToken.set(token);
        }
        catch (IllegalStateException expected) {
            rejections.incrementAndGet();
        }
        catch (Throwable failure) {
            unexpected.compareAndSet(null, failure);
        }
        finally {
            done.countDown();
        }
    }

    private static int writesAfterRejectedReservation(WorkspaceSessionRegistry registry, WorkspaceSessionId session,
            Path target) {
        int writes = 0;
        try {
            WorkspacePathReservation token = registry.reserveSaveAs(session, target);
            token.close();
            writes++;
        }
        catch (IllegalStateException expected) {
        }
        return writes;
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

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static final class RecordingWriter implements AtomicWorkspaceWriter {
        private final List<Path> writes = new ArrayList<Path>();

        @Override
        public void write(Path target, byte[] bytes) {
            writes.add(target);
            try {
                Files.write(target, bytes);
            }
            catch (IOException failure) {
                throw new AssertionError(failure);
            }
        }
    }

    private static final class ShutDownScheduler extends AbstractExecutorService implements ScheduledExecutorService {
        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            throw new RejectedExecutionException("scheduler is shut down");
        }

        @Override
        public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            throw new RejectedExecutionException("scheduler is shut down");
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period,
                TimeUnit unit) {
            throw new RejectedExecutionException("scheduler is shut down");
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay,
                TimeUnit unit) {
            throw new RejectedExecutionException("scheduler is shut down");
        }

        @Override
        public void shutdown() {
        }

        @Override
        public List<Runnable> shutdownNow() {
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return true;
        }

        @Override
        public boolean isTerminated() {
            return true;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public void execute(Runnable command) {
            throw new RejectedExecutionException("scheduler is shut down");
        }
    }
}
