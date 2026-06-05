package org.freeplane.plugin.ai.chat.request;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.freeplane.api.ai.AiRequestCallback;
import org.freeplane.api.ai.AiRequestStatus;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HiddenAiRequestObserverBridgeTest {

    @Test
    public void succeededCompletesHandleWithResponse() {
        AtomicReference<org.freeplane.api.ai.AiRequestResult> seenResult =
            new AtomicReference<org.freeplane.api.ai.AiRequestResult>();
        AiRequestHandleImpl handle = newHandle(seenResult::set);
        HiddenAiRequestObserverBridge uut = new HiddenAiRequestObserverBridge(handle);

        uut.onSucceeded("response");

        assertThat(handle.isDone()).isTrue();
        assertThat(handle.getStatus()).isEqualTo(AiRequestStatus.SUCCEEDED);
        assertThat(seenResult.get().getResponse()).isEqualTo("response");
    }

    @Test
    public void failedMapsModelUnavailableStatus() {
        AtomicReference<org.freeplane.api.ai.AiRequestResult> seenResult =
            new AtomicReference<org.freeplane.api.ai.AiRequestResult>();
        AiRequestHandleImpl handle = newHandle(seenResult::set);
        HiddenAiRequestObserverBridge uut = new HiddenAiRequestObserverBridge(handle);

        uut.onFailed("model not found");

        assertThat(handle.isDone()).isTrue();
        assertThat(handle.getStatus()).isEqualTo(AiRequestStatus.MODEL_UNAVAILABLE);
        assertThat(seenResult.get().getDetail()).isEqualTo("model not found");
    }

    @Test
    public void cancelledCompletesExactlyOnce() {
        AtomicInteger callbackCount = new AtomicInteger();
        AtomicReference<AiRequestStatus> seenStatus = new AtomicReference<AiRequestStatus>();
        AiRequestHandleImpl handle = newHandle(result -> {
            callbackCount.incrementAndGet();
            seenStatus.set(result.getStatus());
        });
        HiddenAiRequestObserverBridge uut = new HiddenAiRequestObserverBridge(handle);

        uut.onCancelled();
        uut.onCancelled();

        assertThat(callbackCount.get()).isEqualTo(1);
        assertThat(seenStatus.get()).isEqualTo(AiRequestStatus.CANCELLED);
    }

    @Test
    public void cancelledAfterTimeoutReportsTimedOut() {
        AtomicReference<AiRequestStatus> seenStatus = new AtomicReference<AiRequestStatus>();
        AiRequestHandleImpl handle = newHandle(result ->
            seenStatus.set(result.getStatus()));
        handle.markTimedOut();
        HiddenAiRequestObserverBridge uut = new HiddenAiRequestObserverBridge(handle);

        uut.onCancelled();

        assertThat(handle.getStatus()).isEqualTo(AiRequestStatus.TIMED_OUT);
        assertThat(seenStatus.get()).isEqualTo(AiRequestStatus.TIMED_OUT);
    }

    private AiRequestHandleImpl newHandle(AiRequestCallback callback) {
        try {
            Constructor<?> constructor = AiRequestHandleImpl.class.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            Class<?> dispatcherType = constructor.getParameterTypes()[0];
            Object dispatcher = Proxy.newProxyInstance(
                dispatcherType.getClassLoader(),
                new Class<?>[] { dispatcherType },
                (proxy, method, args) -> {
                    ((Runnable) args[0]).run();
                    return null;
                });
            return (AiRequestHandleImpl) constructor.newInstance(dispatcher, callback);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }
}
