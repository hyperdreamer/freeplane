package org.freeplane.plugin.graph.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.freeplane.plugin.graph.adapter.EdtExecutor;
import org.junit.Test;

public class ProjectionBatcherShould {
    @Test
    public void burstCoalescesOnceAndTimestampsAfterDebounce() {
        TestEdt edt = new TestEdt();
        TestScheduler scheduler = new TestScheduler();
        TestClock clock = new TestClock(10L);
        List<AcceptedBatch> accepted = new ArrayList<AcceptedBatch>();
        ProjectionBatcher batcher = new ProjectionBatcher(edt, scheduler, clock, accepted::add);

        edt.runOnEdt(() -> {
            batcher.request(ChangeKind.TEXT);
            batcher.request(ChangeKind.STRUCTURE);
            batcher.request(ChangeKind.TEXT);
        });

        assertThat(accepted).isEmpty();
        assertThat(scheduler.tasks()).hasSize(3);
        assertThat(scheduler.tasks().get(2).delay()).isEqualTo(150L);
        assertThat(scheduler.tasks().get(2).unit()).isEqualTo(TimeUnit.MILLISECONDS);

        clock.set(20L);
        scheduler.runAllIncludingCancelled();

        assertThat(accepted).hasSize(1);
        assertThat(accepted.get(0).generation()).isEqualTo(1L);
        assertThat(accepted.get(0).acceptedAtNanos()).isEqualTo(20L);
        assertThat(accepted.get(0).kinds()).containsExactly(ChangeKind.TEXT, ChangeKind.STRUCTURE);
        assertThat(clock.reads()).isEqualTo(1);
    }

    @Test
    public void pendingKindsAreVisibleSynchronouslyOnTheEdt() {
        TestEdt edt = new TestEdt();
        TestScheduler scheduler = new TestScheduler();
        ProjectionBatcher batcher = new ProjectionBatcher(edt, scheduler, new TestClock(1L), batch -> { });

        edt.runOnEdt(() -> {
            batcher.request(ChangeKind.PIN);
            assertThat(batcher.hasPendingChanges()).isTrue();
            assertThat(batcher.pendingKinds()).containsExactly(ChangeKind.PIN);
        });
    }

    @Test
    public void staleDebounceRunnableCannotAcceptAReplacedBurst() {
        TestEdt edt = new TestEdt();
        TestScheduler scheduler = new TestScheduler();
        List<AcceptedBatch> accepted = new ArrayList<AcceptedBatch>();
        ProjectionBatcher batcher = new ProjectionBatcher(edt, scheduler, new TestClock(2L), accepted::add);

        edt.runOnEdt(() -> batcher.request(ChangeKind.TEXT));
        TestScheduledFuture first = scheduler.tasks().get(0);
        edt.runOnEdt(() -> batcher.request(ChangeKind.RELATIONSHIP));

        first.runEvenIfCancelled();
        assertThat(accepted).isEmpty();

        scheduler.tasks().get(1).runEvenIfCancelled();
        assertThat(accepted).hasSize(1);
        assertThat(accepted.get(0).kinds()).containsExactly(ChangeKind.TEXT, ChangeKind.RELATIONSHIP);
    }

    @Test
    public void closeCancelsDebounceAndRejectsLaterRequests() {
        TestEdt edt = new TestEdt();
        TestScheduler scheduler = new TestScheduler();
        List<AcceptedBatch> accepted = new ArrayList<AcceptedBatch>();
        ProjectionBatcher batcher = new ProjectionBatcher(edt, scheduler, new TestClock(3L), accepted::add);

        edt.runOnEdt(() -> batcher.request(ChangeKind.MAP_STATE));
        TestScheduledFuture task = scheduler.tasks().get(0);
        batcher.close();

        assertThat(task.cancelled()).isTrue();
        assertThatThrownBy(() -> batcher.request(ChangeKind.SETTINGS))
            .isInstanceOf(IllegalStateException.class);

        task.runEvenIfCancelled();
        assertThat(accepted).isEmpty();
        assertThat(batcher.hasPendingChanges()).isFalse();
    }

    @Test
    public void acceptedBatchCopiesAndOrdersKindsImmutably() {
        EnumSet<ChangeKind> source = EnumSet.of(ChangeKind.SETTINGS, ChangeKind.TEXT, ChangeKind.PIN);
        AcceptedBatch batch = new AcceptedBatch(4L, 99L, source);
        source.clear();

        assertThat(batch.generation()).isEqualTo(4L);
        assertThat(batch.acceptedAtNanos()).isEqualTo(99L);
        assertThat(batch.kinds()).containsExactly(ChangeKind.TEXT, ChangeKind.PIN, ChangeKind.SETTINGS);
        assertThatThrownBy(() -> batch.kinds().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(batch).isEqualTo(new AcceptedBatch(4L, 99L,
            EnumSet.of(ChangeKind.TEXT, ChangeKind.PIN, ChangeKind.SETTINGS)));
        assertThat(batch.hashCode()).isEqualTo(new AcceptedBatch(4L, 99L,
            EnumSet.of(ChangeKind.TEXT, ChangeKind.PIN, ChangeKind.SETTINGS)).hashCode());
        assertThatThrownBy(() -> new AcceptedBatch(-1L, 99L, EnumSet.of(ChangeKind.TEXT)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AcceptedBatch(1L, -1L, EnumSet.of(ChangeKind.TEXT)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AcceptedBatch(1L, 1L, Collections.<ChangeKind>emptySet()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static final class TestClock implements NanoClock {
        private long value;
        private final AtomicInteger reads = new AtomicInteger();

        private TestClock(long value) {
            this.value = value;
        }

        @Override
        public long nanoTime() {
            reads.incrementAndGet();
            return value;
        }

        private void set(long value) {
            this.value = value;
        }

        private int reads() {
            return reads.get();
        }
    }

    private static final class TestEdt implements EdtExecutor {
        private final Queue<Runnable> pending = new ArrayDeque<Runnable>();
        private boolean edt;

        @Override
        public <T> T call(Callable<T> task) {
            if (edt) {
                return callNow(task);
            }
            final AtomicReferenceValue<T> result = new AtomicReferenceValue<T>();
            runOnEdt(() -> result.value = callNow(task));
            return result.value;
        }

        @Override
        public void execute(Runnable task) {
            if (edt) {
                task.run();
            }
            else {
                pending.add(task);
            }
        }

        @Override
        public boolean isEdt() {
            return edt;
        }

        private void runOnEdt(Runnable task) {
            boolean previous = edt;
            edt = true;
            try {
                task.run();
            }
            finally {
                edt = previous;
            }
        }

        @SuppressWarnings("unused")
        private void runQueued() {
            while (!pending.isEmpty()) {
                runOnEdt(pending.remove());
            }
        }

        private static <T> T callNow(Callable<T> task) {
            try {
                return task.call();
            }
            catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        }
    }

    private static final class AtomicReferenceValue<T> {
        private T value;
    }

    private static final class TestScheduler extends AbstractExecutorService implements ScheduledExecutorService {
        private final List<TestScheduledFuture> tasks = new ArrayList<TestScheduledFuture>();
        private boolean shutdown;

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
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
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
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

        private List<TestScheduledFuture> tasks() {
            return tasks;
        }

        private void runAllIncludingCancelled() {
            for (TestScheduledFuture task : new ArrayList<TestScheduledFuture>(tasks)) {
                task.runEvenIfCancelled();
            }
        }
    }

    private static final class TestScheduledFuture implements ScheduledFuture<Object> {
        private final Runnable command;
        private final long delay;
        private final TimeUnit unit;
        private boolean cancelled;
        private boolean done;

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
            return Long.compare(getDelay(TimeUnit.NANOSECONDS), other.getDelay(TimeUnit.NANOSECONDS));
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancelled = true;
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return done;
        }

        @Override
        public Object get() {
            return null;
        }

        @Override
        public Object get(long timeout, TimeUnit unit) {
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

        private void runEvenIfCancelled() {
            done = true;
            command.run();
        }
    }
}
