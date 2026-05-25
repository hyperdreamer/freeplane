package org.freeplane.plugin.script.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import groovy.lang.Closure;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.freeplane.api.ai.AiModelSelection;
import org.freeplane.api.ai.AiRequest;
import org.freeplane.api.ai.AiRequestCallback;
import org.freeplane.api.ai.AiRequestHandle;
import org.freeplane.api.ai.AiRequestMode;
import org.freeplane.api.ai.AiRequestRejectedException;
import org.freeplane.api.ai.AiRequestResult;
import org.freeplane.api.ai.AiRequestService;
import org.freeplane.api.ai.AiRequestStatus;
import org.freeplane.api.ai.AiToolAvailability;
import org.freeplane.plugin.script.Activator;
import org.junit.Test;
import org.mockito.InOrder;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

public class ControllerProxyTest {

    @Test
    public void askAi_delegatesToResolvedServiceAndPassesSamCallback() {
        AiRequestService requestService = mock(AiRequestService.class);
        AiRequestHandle expectedHandle = mock(AiRequestHandle.class);
        AtomicReference<AiRequestCallback> capturedCallback = new AtomicReference<AiRequestCallback>();
        when(requestService.askAi(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenAnswer(invocation -> {
                capturedCallback.set(invocation.getArgument(1));
                return expectedHandle;
            });
        ControllerProxy uut = new ControllerProxy(null, () -> requestService, () -> {
        });
        AiRequest request = request();
        AtomicReference<AiRequestStatus> seenStatus = new AtomicReference<AiRequestStatus>();

        AiRequestHandle actualHandle = uut.askAi(request, result -> seenStatus.set(result.getStatus()));
        actualHandle.cancel();
        capturedCallback.get().accept(new AiRequestResult(AiRequestStatus.SUCCEEDED, "response", null));

        assertThat(actualHandle).isSameAs(expectedHandle);
        assertThat(seenStatus.get()).isEqualTo(AiRequestStatus.SUCCEEDED);
        verify(requestService).askAi(org.mockito.ArgumentMatchers.eq(request), org.mockito.ArgumentMatchers.any());
        verify(expectedHandle).cancel();
    }

    @Test
    public void askAi_defaultResolverUsesScriptActivatorBundleContext() throws Exception {
        BundleContext bundleContext = mock(BundleContext.class);
        @SuppressWarnings("unchecked")
        ServiceReference<AiRequestService> serviceReference = mock(ServiceReference.class);
        AiRequestService requestService = mock(AiRequestService.class);
        AiRequestHandle expectedHandle = mock(AiRequestHandle.class);
        when(bundleContext.getServiceReference(AiRequestService.class)).thenReturn(serviceReference);
        when(bundleContext.getService(serviceReference)).thenReturn(requestService);
        when(requestService.askAi(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(expectedHandle);
        Activator activator = new Activator();
        activator.start(bundleContext);

        try {
            ControllerProxy uut = new ControllerProxy(null);

            AiRequestHandle actualHandle = uut.askAi(request(), result -> {
            });

            assertThat(actualHandle).isSameAs(expectedHandle);
            verify(bundleContext).getServiceReference(AiRequestService.class);
            verify(bundleContext).getService(serviceReference);
        } finally {
            activator.stop(bundleContext);
        }
    }

    @Test
    public void askAi_acceptsGroovyClosureCallbackOverload() {
        AiRequestService requestService = mock(AiRequestService.class);
        AiRequestHandle expectedHandle = mock(AiRequestHandle.class);
        AtomicReference<AiRequestCallback> capturedCallback = new AtomicReference<AiRequestCallback>();
        when(requestService.askAi(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenAnswer(invocation -> {
                capturedCallback.set(invocation.getArgument(1));
                return expectedHandle;
            });
        ControllerProxy uut = new ControllerProxy(null, () -> requestService, () -> {
        });
        AtomicReference<AiRequestStatus> seenStatus = new AtomicReference<AiRequestStatus>();
        Closure<Object> callback = new Closure<Object>(this, this) {
            public Object doCall(AiRequestResult result) {
                seenStatus.set(result.getStatus());
                return null;
            }
        };

        AiRequestHandle actualHandle = uut.askAi(request(), callback);
        capturedCallback.get().accept(new AiRequestResult(AiRequestStatus.SUCCEEDED, "response", null));

        assertThat(actualHandle).isSameAs(expectedHandle);
        assertThat(seenStatus.get()).isEqualTo(AiRequestStatus.SUCCEEDED);
    }

    @Test
    public void askAi_throwsAiUnavailableWhenServiceMissingAndDoesNotInvokeCallback() {
        ControllerProxy uut = new ControllerProxy(null, () -> null, () -> {
        });
        AtomicBoolean callbackInvoked = new AtomicBoolean(false);

        assertThatThrownBy(() -> uut.askAi(request(), result -> callbackInvoked.set(true)))
            .isInstanceOf(AiRequestRejectedException.class)
            .satisfies(error -> {
                AiRequestRejectedException rejected = (AiRequestRejectedException) error;
                assertThat(rejected.getStatus()).isEqualTo(AiRequestStatus.AI_UNAVAILABLE);
                assertThat(rejected).hasMessage("AI request service is unavailable.");
            });

        assertThat(callbackInvoked.get()).isFalse();
    }

    @Test
    public void askAi_checksNetworkPermissionBeforeResolvingService() {
        AiRequestService requestService = mock(AiRequestService.class);
        AiRequestHandle expectedHandle = mock(AiRequestHandle.class);
        when(requestService.askAi(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(expectedHandle);
        ControllerProxy.AiRequestServiceResolver aiRequestServiceResolver = mock(ControllerProxy.AiRequestServiceResolver.class);
        when(aiRequestServiceResolver.resolve()).thenReturn(requestService);
        ControllerProxy.NetworkPermissionChecker networkPermissionChecker = mock(ControllerProxy.NetworkPermissionChecker.class);
        ControllerProxy uut = new ControllerProxy(null, aiRequestServiceResolver, networkPermissionChecker);

        AiRequestHandle actualHandle = uut.askAi(request(), result -> {
        });

        assertThat(actualHandle).isSameAs(expectedHandle);
        InOrder inOrder = inOrder(networkPermissionChecker, aiRequestServiceResolver, requestService);
        inOrder.verify(networkPermissionChecker).check();
        inOrder.verify(aiRequestServiceResolver).resolve();
        inOrder.verify(requestService).askAi(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void askAi_throwsPermissionDeniedWhenNetworkCheckFailsAndDoesNotInvokeCallback() {
        ControllerProxy.AiRequestServiceResolver aiRequestServiceResolver = mock(ControllerProxy.AiRequestServiceResolver.class);
        ControllerProxy.NetworkPermissionChecker networkPermissionChecker = () -> {
            throw new SecurityException("denied");
        };
        ControllerProxy uut = new ControllerProxy(null, aiRequestServiceResolver, networkPermissionChecker);
        AtomicBoolean callbackInvoked = new AtomicBoolean(false);

        assertThatThrownBy(() -> uut.askAi(request(), result -> callbackInvoked.set(true)))
            .isInstanceOf(AiRequestRejectedException.class)
            .satisfies(error -> {
                AiRequestRejectedException rejected = (AiRequestRejectedException) error;
                assertThat(rejected.getStatus()).isEqualTo(AiRequestStatus.PERMISSION_DENIED);
                assertThat(rejected).hasMessage("denied");
            });

        assertThat(callbackInvoked.get()).isFalse();
        verifyNoInteractions(aiRequestServiceResolver);
    }

    private AiRequest request() {
        return new AiRequest(
            "Prompt",
            AiModelSelection.current(),
            AiToolAvailability.CURRENT,
            AiRequestMode.HIDDEN,
            Duration.ofSeconds(10));
    }
}
