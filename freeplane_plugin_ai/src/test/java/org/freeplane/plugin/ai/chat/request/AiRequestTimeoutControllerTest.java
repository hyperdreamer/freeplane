package org.freeplane.plugin.ai.chat.request;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.freeplane.api.ai.AiRequestStatus;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AiRequestTimeoutControllerTest {

    @Test
    public void armAfterStart_timesOutThroughCancelPathAndReportsTimedOutOnce() throws Exception {
        ScheduledExecutorService timeoutExecutor = Executors.newSingleThreadScheduledExecutor();
        try {
            CountDownLatch callbackLatch = new CountDownLatch(1);
            AtomicInteger callbackCount = new AtomicInteger();
            AtomicReference<AiRequestStatus> seenStatus = new AtomicReference<AiRequestStatus>();
            AiRequestHandleImpl handle = new AiRequestHandleImpl(Runnable::run, result -> {
                callbackCount.incrementAndGet();
                seenStatus.set(result.getStatus());
                callbackLatch.countDown();
            });
            handle.setCancelAction(() -> handle.complete(
                new org.freeplane.api.ai.AiRequestResult(
                    handle.isTimedOut() ? AiRequestStatus.TIMED_OUT : AiRequestStatus.CANCELLED,
                    null,
                    null)));
            AiRequestTimeoutController uut = new AiRequestTimeoutController(
                handle,
                timeoutExecutor::schedule,
                Duration.ofMillis(25));

            uut.armAfterStart();

            assertThat(callbackLatch.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(handle.isDone()).isTrue();
            assertThat(handle.isCancelled()).isTrue();
            assertThat(handle.getStatus()).isEqualTo(AiRequestStatus.TIMED_OUT);
            assertThat(seenStatus.get()).isEqualTo(AiRequestStatus.TIMED_OUT);
            assertThat(callbackCount.get()).isEqualTo(1);
        } finally {
            timeoutExecutor.shutdownNow();
        }
    }

    @Test
    public void completionCancelsArmedTimeout() throws Exception {
        ScheduledExecutorService timeoutExecutor = Executors.newSingleThreadScheduledExecutor();
        try {
            CountDownLatch callbackLatch = new CountDownLatch(1);
            AtomicInteger callbackCount = new AtomicInteger();
            AiRequestHandleImpl handle = new AiRequestHandleImpl(Runnable::run, result -> {
                callbackCount.incrementAndGet();
                callbackLatch.countDown();
            });
            AiRequestTimeoutController uut = new AiRequestTimeoutController(
                handle,
                timeoutExecutor::schedule,
                Duration.ofMillis(25));

            uut.armAfterStart();
            handle.complete(new org.freeplane.api.ai.AiRequestResult(AiRequestStatus.SUCCEEDED, "response", null));
            assertThat(callbackLatch.await(2, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(75L);

            assertThat(handle.isDone()).isTrue();
            assertThat(handle.isCancelled()).isFalse();
            assertThat(handle.getStatus()).isEqualTo(AiRequestStatus.SUCCEEDED);
            assertThat(callbackCount.get()).isEqualTo(1);
        } finally {
            timeoutExecutor.shutdownNow();
        }
    }

    @Test
    public void cancelTimerPreventsTimeoutBeforeRequestStart() throws Exception {
        ScheduledExecutorService timeoutExecutor = Executors.newSingleThreadScheduledExecutor();
        try {
            AtomicInteger callbackCount = new AtomicInteger();
            AiRequestHandleImpl handle = new AiRequestHandleImpl(Runnable::run, result -> callbackCount.incrementAndGet());
            AiRequestTimeoutController uut = new AiRequestTimeoutController(
                handle,
                timeoutExecutor::schedule,
                Duration.ofMillis(25));

            uut.cancelTimer();
            Thread.sleep(75L);

            assertThat(handle.isDone()).isFalse();
            assertThat(callbackCount.get()).isZero();
        } finally {
            timeoutExecutor.shutdownNow();
        }
    }
}
