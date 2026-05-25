package org.freeplane.plugin.script.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import groovy.lang.Closure;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
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
import org.freeplane.features.map.NodeModel;
import org.freeplane.plugin.script.Activator;
import org.freeplane.plugin.script.ExecutingScriptContextStack;
import org.freeplane.plugin.script.NodeScript;
import org.freeplane.plugin.script.ScriptContext;
import org.freeplane.plugin.script.ScriptingPermissions;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

public class ControllerProxyTest {

    @Test
    public void askAi_delegatesToResolvedServiceAndPassesWrappedCallback() {
        AiRequestService requestService = mock(AiRequestService.class);
        AiRequestHandle expectedHandle = mock(AiRequestHandle.class);
        AtomicReference<AiRequestCallback> capturedCallback = new AtomicReference<AiRequestCallback>();
        when(requestService.askAi(any(), any())).thenAnswer(invocation -> {
            capturedCallback.set(invocation.getArgument(1));
            return expectedHandle;
        });
        ControllerProxy uut = new ControllerProxy(allowingScriptContext(), () -> requestService);
        AiRequest request = request();
        AtomicReference<AiRequestStatus> seenStatus = new AtomicReference<AiRequestStatus>();

        AiRequestHandle actualHandle = uut.askAi(request, result -> seenStatus.set(result.getStatus()));
        actualHandle.cancel();
        capturedCallback.get().accept(new AiRequestResult(AiRequestStatus.SUCCEEDED, "response", null));

        assertThat(actualHandle).isSameAs(expectedHandle);
        assertThat(seenStatus.get()).isEqualTo(AiRequestStatus.SUCCEEDED);
        verify(requestService).askAi(eq(request), any());
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
        when(requestService.askAi(any(), any())).thenReturn(expectedHandle);
        Activator activator = new Activator();
        activator.start(bundleContext);

        try {
            ControllerProxy uut = new ControllerProxy(allowingScriptContext());

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
        when(requestService.askAi(any(), any())).thenAnswer(invocation -> {
            capturedCallback.set(invocation.getArgument(1));
            return expectedHandle;
        });
        ControllerProxy uut = new ControllerProxy(allowingScriptContext(), () -> requestService);
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
        ControllerProxy uut = new ControllerProxy(allowingScriptContext(), () -> null);
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
    public void askAi_throwsPermissionDeniedWhenAiPermissionMissingAndDoesNotInvokeCallback() {
        ControllerProxy.AiRequestServiceResolver aiRequestServiceResolver = mock(ControllerProxy.AiRequestServiceResolver.class);
        ControllerProxy uut = new ControllerProxy(denyingScriptContext(), aiRequestServiceResolver);
        AtomicBoolean callbackInvoked = new AtomicBoolean(false);

        assertThatThrownBy(() -> uut.askAi(request(), result -> callbackInvoked.set(true)))
            .isInstanceOf(AiRequestRejectedException.class)
            .satisfies(error -> {
                AiRequestRejectedException rejected = (AiRequestRejectedException) error;
                assertThat(rejected.getStatus()).isEqualTo(AiRequestStatus.PERMISSION_DENIED);
                assertThat(rejected).hasMessage("AI request permission denied.");
            });

        assertThat(callbackInvoked.get()).isFalse();
        verifyNoInteractions(aiRequestServiceResolver);
    }

    @Test
    public void askAi_usesExecutingScriptContextWhenProxyContextMissing() {
        AiRequestService requestService = mock(AiRequestService.class);
        AiRequestHandle expectedHandle = mock(AiRequestHandle.class);
        when(requestService.askAi(any(), any())).thenReturn(expectedHandle);
        ControllerProxy uut = new ControllerProxy(null, () -> requestService);
        AtomicReference<AiRequestHandle> actualHandle = new AtomicReference<AiRequestHandle>();
        ScriptContext currentScriptContext = allowingScriptContext();

        ExecutingScriptContextStack.INSTANCE.withContext(currentScriptContext,
            () -> actualHandle.set(uut.askAi(request(), result -> {
            })));

        assertThat(actualHandle.get()).isSameAs(expectedHandle);
        assertThat(ExecutingScriptContextStack.INSTANCE.getCurrentContext()).isNull();
        verify(requestService).askAi(any(), any());
    }

    @Test
    public void askAi_deniesWhenNoScriptContextIsAvailable() {
        ControllerProxy.AiRequestServiceResolver aiRequestServiceResolver = mock(ControllerProxy.AiRequestServiceResolver.class);
        ControllerProxy uut = new ControllerProxy(null, aiRequestServiceResolver);

        assertThatThrownBy(() -> uut.askAi(request(), result -> {
        }))
            .isInstanceOf(AiRequestRejectedException.class)
            .satisfies(error -> assertThat(((AiRequestRejectedException) error).getStatus())
                .isEqualTo(AiRequestStatus.PERMISSION_DENIED));

        verifyNoInteractions(aiRequestServiceResolver);
    }

    @Test
    public void askAi_deniesFormulaLikeControllerProxyBeforeResolvingService() {
        ControllerProxy.AiRequestServiceResolver aiRequestServiceResolver = mock(ControllerProxy.AiRequestServiceResolver.class);
        NodeModel formulaNode = mock(NodeModel.class);
        ScriptContext formulaLikeContext = new ScriptContext(new NodeScript(formulaNode, "=c.askAi(...)"))
            .withEffectivePermissions(new ScriptingPermissions());
        ControllerProxy uut = new ControllerProxy(formulaLikeContext, aiRequestServiceResolver);

        assertThatThrownBy(() -> uut.askAi(request(), result -> {
        }))
            .isInstanceOf(AiRequestRejectedException.class)
            .satisfies(error -> assertThat(((AiRequestRejectedException) error).getStatus())
                .isEqualTo(AiRequestStatus.PERMISSION_DENIED));

        verifyNoInteractions(aiRequestServiceResolver);
    }

    @Test
    public void askAi_restoresOriginatingScriptContextForCallbackHelperLookups() throws Exception {
        BundleContext bundleContext = mock(BundleContext.class);
        @SuppressWarnings("unchecked")
        ServiceReference<AiRequestService> serviceReference = mock(ServiceReference.class);
        AiRequestService requestService = mock(AiRequestService.class);
        AiRequestHandle outerHandle = mock(AiRequestHandle.class);
        AiRequestHandle nestedHandle = mock(AiRequestHandle.class);
        AtomicReference<AiRequestCallback> capturedOuterCallback = new AtomicReference<AiRequestCallback>();
        AtomicReference<ScriptContext> contextSeenInsideCallback = new AtomicReference<ScriptContext>();
        AtomicReference<ScriptContext> contextSeenByNestedHelperLookup = new AtomicReference<ScriptContext>();
        AtomicReference<AiRequestHandle> nestedObservedHandle = new AtomicReference<AiRequestHandle>();
        when(bundleContext.getServiceReference(AiRequestService.class)).thenReturn(serviceReference);
        when(bundleContext.getService(serviceReference)).thenReturn(requestService);
        when(requestService.askAi(any(), any())).thenAnswer(invocation -> {
            AiRequestCallback callback = invocation.getArgument(1);
            if (capturedOuterCallback.get() == null) {
                capturedOuterCallback.set(callback);
                return outerHandle;
            }
            contextSeenByNestedHelperLookup.set(ExecutingScriptContextStack.INSTANCE.getCurrentContext());
            return nestedHandle;
        });
        Activator activator = new Activator();
        activator.start(bundleContext);
        ScriptContext originatingScriptContext = allowingScriptContext();

        try {
            ControllerProxy uut = new ControllerProxy(originatingScriptContext);

            AiRequestHandle actualOuterHandle = uut.askAi(request(), result -> {
                contextSeenInsideCallback.set(ExecutingScriptContextStack.INSTANCE.getCurrentContext());
                nestedObservedHandle.set(ScriptUtils.c().askAi(request(), nestedResult -> {
                }));
            });
            capturedOuterCallback.get().accept(new AiRequestResult(AiRequestStatus.SUCCEEDED, "response", null));

            assertThat(actualOuterHandle).isSameAs(outerHandle);
            assertThat(contextSeenInsideCallback.get()).isSameAs(originatingScriptContext);
            assertThat(contextSeenByNestedHelperLookup.get()).isSameAs(originatingScriptContext);
            assertThat(nestedObservedHandle.get()).isSameAs(nestedHandle);
            assertThat(ExecutingScriptContextStack.INSTANCE.getCurrentContext()).isNull();
            verify(requestService, times(2)).askAi(any(), any());
        } finally {
            activator.stop(bundleContext);
        }
    }

    private AiRequest request() {
        return new AiRequest(
            "Prompt",
            AiModelSelection.current(),
            AiToolAvailability.CURRENT,
            AiRequestMode.HIDDEN,
            Duration.ofSeconds(10));
    }

    private ScriptContext allowingScriptContext() {
        return new ScriptContext(null).withEffectivePermissions(allowingPermissions());
    }

    private ScriptContext denyingScriptContext() {
        return new ScriptContext(null).withEffectivePermissions(new ScriptingPermissions());
    }

    private ScriptingPermissions allowingPermissions() {
        Map<String, Boolean> permissions = new HashMap<String, Boolean>();
        permissions.put(ScriptingPermissions.RESOURCES_EXECUTE_SCRIPTS_WITHOUT_AI_REQUEST_RESTRICTION, true);
        return new ScriptingPermissions(permissions);
    }
}
