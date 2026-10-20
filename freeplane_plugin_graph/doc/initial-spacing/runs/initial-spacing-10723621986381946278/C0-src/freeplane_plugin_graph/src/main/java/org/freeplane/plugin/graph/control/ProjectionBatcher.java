package org.freeplane.plugin.graph.control;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;

import org.freeplane.plugin.graph.adapter.EdtExecutor;

public final class ProjectionBatcher implements AutoCloseable {
    private static final long DEBOUNCE_MILLIS = 150L;
    private static final long CLOCK_ORIGIN_NANOS = System.nanoTime();
    private static final NanoClock SYSTEM_CLOCK = new NanoClock() {
        @Override
        public long nanoTime() {
            return System.nanoTime() - CLOCK_ORIGIN_NANOS;
        }
    };

    private final Object monitor = new Object();
    private final EdtExecutor edt;
    private final ScheduledExecutorService scheduler;
    private final NanoClock clock;
    private final Consumer<AcceptedBatch> acceptedCallback;
    private final boolean ownsScheduler;
    private final EnumSet<ChangeKind> pendingKinds = EnumSet.noneOf(ChangeKind.class);

    private ScheduledFuture<?> pendingFuture;
    private long scheduledGeneration;
    private long acceptedGeneration;
    private int activeCallbacks;
    private boolean closed;

    public ProjectionBatcher() {
        this(new Consumer<AcceptedBatch>() {
            @Override
            public void accept(final AcceptedBatch batch) {
            }
        });
    }

    public ProjectionBatcher(final Consumer<AcceptedBatch> acceptedCallback) {
        this(new SwingEdtExecutor(), newDefaultScheduler(), SYSTEM_CLOCK, acceptedCallback, true);
    }

    ProjectionBatcher(final EdtExecutor edt, final ScheduledExecutorService scheduler,
            final NanoClock clock, final Consumer<AcceptedBatch> acceptedCallback) {
        this(edt, scheduler, clock, acceptedCallback, false);
    }

    private ProjectionBatcher(final EdtExecutor edt, final ScheduledExecutorService scheduler,
            final NanoClock clock, final Consumer<AcceptedBatch> acceptedCallback, final boolean ownsScheduler) {
        this.edt = Objects.requireNonNull(edt, "edt");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.acceptedCallback = Objects.requireNonNull(acceptedCallback, "acceptedCallback");
        this.ownsScheduler = ownsScheduler;
    }

    public void request(final ChangeKind kind) {
        final ChangeKind value = Objects.requireNonNull(kind, "kind");
        synchronized (monitor) {
            requireOpenLocked();
        }
        if (edt.isEdt()) {
            requestOnEdt(value);
        }
        else {
            edt.execute(new Runnable() {
                @Override
                public void run() {
                    requestOnEdt(value);
                }
            });
        }
    }

    @Override
    public void close() {
        boolean interrupted = false;
        synchronized (monitor) {
            if (!closed) {
                closed = true;
                scheduledGeneration++;
                pendingKinds.clear();
                cancelPendingLocked();
            }
            while (activeCallbacks > 0) {
                try {
                    monitor.wait();
                }
                catch (InterruptedException interruption) {
                    interrupted = true;
                }
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        if (ownsScheduler) {
            scheduler.shutdownNow();
        }
    }

    boolean hasPendingChanges() {
        synchronized (monitor) {
            return !pendingKinds.isEmpty();
        }
    }

    Set<ChangeKind> pendingKinds() {
        synchronized (monitor) {
            if (pendingKinds.isEmpty()) {
                return Collections.emptySet();
            }
            return Collections.unmodifiableSet(EnumSet.copyOf(pendingKinds));
        }
    }

    private void requestOnEdt(final ChangeKind kind) {
        synchronized (monitor) {
            if (closed) {
                return;
            }
            pendingKinds.add(kind);
            cancelPendingLocked();
            final long token = ++scheduledGeneration;
            final ScheduledFuture<?> future;
            try {
                future = scheduler.schedule(new Runnable() {
                    @Override
                    public void run() {
                        acceptIfCurrent(token);
                    }
                }, DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);
            }
            catch (RuntimeException failure) {
                pendingFuture = null;
                throw failure;
            }
            if (!closed && token == scheduledGeneration && !pendingKinds.isEmpty()) {
                pendingFuture = future;
            }
            else {
                future.cancel(false);
            }
        }
    }

    private void acceptIfCurrent(final long token) {
        final AcceptedBatch accepted;
        synchronized (monitor) {
            if (closed || token != scheduledGeneration || pendingKinds.isEmpty()) {
                return;
            }
            final EnumSet<ChangeKind> kinds = EnumSet.copyOf(pendingKinds);
            pendingKinds.clear();
            pendingFuture = null;
            final long generation = ++acceptedGeneration;
            final long acceptedAtNanos = clock.nanoTime();
            accepted = new AcceptedBatch(generation, acceptedAtNanos, kinds);
            activeCallbacks++;
        }
        try {
            acceptedCallback.accept(accepted);
        }
        catch (RuntimeException ignored) {
            // The accepted state is already committed before client code runs.
        }
        finally {
            synchronized (monitor) {
                activeCallbacks--;
                monitor.notifyAll();
            }
        }
    }

    private void cancelPendingLocked() {
        if (pendingFuture != null) {
            pendingFuture.cancel(false);
            pendingFuture = null;
        }
    }

    private void requireOpenLocked() {
        if (closed) {
            throw new IllegalStateException("Projection batcher is closed");
        }
    }

    private static ScheduledExecutorService newDefaultScheduler() {
        return Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(final Runnable runnable) {
                final Thread thread = new Thread(runnable, "freeplane-graph-projection-batcher");
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    private static final class SwingEdtExecutor implements EdtExecutor {
        @Override
        public <T> T call(final Callable<T> task) {
            Objects.requireNonNull(task, "task");
            if (isEdt()) {
                return callNow(task);
            }
            final AtomicReference<T> result = new AtomicReference<T>();
            final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
            try {
                SwingUtilities.invokeAndWait(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            result.set(task.call());
                        }
                        catch (Throwable exception) {
                            failure.set(exception);
                        }
                    }
                });
            }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while executing on the EDT", interrupted);
            }
            catch (Exception exception) {
                throw new IllegalStateException("Unable to execute on the EDT", exception);
            }
            if (failure.get() != null) {
                throw new IllegalStateException("EDT task failed", failure.get());
            }
            return result.get();
        }

        @Override
        public void execute(final Runnable task) {
            Objects.requireNonNull(task, "task");
            if (isEdt()) {
                task.run();
            }
            else {
                SwingUtilities.invokeLater(task);
            }
        }

        @Override
        public boolean isEdt() {
            return SwingUtilities.isEventDispatchThread();
        }

        private static <T> T callNow(final Callable<T> task) {
            try {
                return task.call();
            }
            catch (Exception failure) {
                throw new IllegalStateException("EDT task failed", failure);
            }
        }
    }
}
