package org.freeplane.plugin.ai.bootstrap;

import org.freeplane.main.osgi.IModeControllerExtensionProvider;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class Java8BootstrapActivatorTest {

    @Test
    public void startRegistersIncompatibleModeExtensionWhenRuntimeIsBelow17() throws Exception {
        BundleContext bundleContext = mock(BundleContext.class);
        TestableJava8BootstrapActivator uut = new TestableJava8BootstrapActivator();
        uut.runtimeJavaMajorVersion = 11;

        uut.start(bundleContext);

        assertThat(uut.registeredIncompatibleMessage).isEqualTo("LOW:11");
        assertThat(uut.runtimeActivatorInstantiationCount).isZero();
    }

    @Test
    public void startAndStopDelegateToRuntimeActivatorWhenRuntimeIsAtLeast17() throws Exception {
        BundleContext bundleContext = mock(BundleContext.class);
        BundleActivator runtimeActivator = mock(BundleActivator.class);
        TestableJava8BootstrapActivator uut = new TestableJava8BootstrapActivator();
        uut.runtimeJavaMajorVersion = 17;
        uut.runtimeActivatorToInstantiate = runtimeActivator;

        uut.start(bundleContext);
        uut.stop(bundleContext);

        verify(runtimeActivator).start(bundleContext);
        verify(runtimeActivator).stop(bundleContext);
        assertThat(uut.registeredIncompatibleMessage).isNull();
    }

    @Test
    public void startRegistersIncompatibleModeExtensionWhenRuntimeActivatorInstantiationFails()
            throws Exception {
        BundleContext bundleContext = mock(BundleContext.class);
        TestableJava8BootstrapActivator uut = new TestableJava8BootstrapActivator();
        uut.runtimeJavaMajorVersion = 17;
        uut.runtimeActivatorInstantiationFailure = new ReflectiveOperationException("boom");

        uut.start(bundleContext);

        assertThat(uut.registeredIncompatibleMessage)
            .isEqualTo("FAIL:17");
        assertThat(uut.runtimeActivatorInstantiationCount).isEqualTo(1);
    }

    @Test
    public void registerIncompatibleModeExtensionAddsIncompatibleTabInMindMapMode() throws Exception {
        BundleContext bundleContext = mock(BundleContext.class);
        ArgumentCaptor<IModeControllerExtensionProvider> providerCaptor =
            ArgumentCaptor.forClass(IModeControllerExtensionProvider.class);
        when(bundleContext.registerService(
            eq(IModeControllerExtensionProvider.class.getName()),
            providerCaptor.capture(),
            any())).thenAnswer(invocation -> null);

        TabCapturingBootstrapActivator uut = new TabCapturingBootstrapActivator();
        uut.registerIncompatibleModeExtension(bundleContext, "Need Java 17+");
        IModeControllerExtensionProvider provider = providerCaptor.getValue();

        provider.installExtension(null, null);

        assertThat(uut.addedTabMessage).isEqualTo("Need Java 17+");
    }

    private static class TestableJava8BootstrapActivator extends Java8BootstrapActivator {
        int runtimeJavaMajorVersion;
        int runtimeActivatorInstantiationCount;
        String registeredIncompatibleMessage;
        BundleActivator runtimeActivatorToInstantiate;
        Exception runtimeActivatorInstantiationFailure;

        @Override
        int resolveRuntimeJavaMajorVersion() {
            return runtimeJavaMajorVersion;
        }

        @Override
        BundleActivator instantiateRuntimeActivator() throws Exception {
            runtimeActivatorInstantiationCount++;
            if (runtimeActivatorInstantiationFailure != null) {
                throw runtimeActivatorInstantiationFailure;
            }
            return runtimeActivatorToInstantiate;
        }

        @Override
        void registerIncompatibleModeExtension(BundleContext context, String message) {
            registeredIncompatibleMessage = message;
        }

        @Override
        BootstrapMessageProvider createBootstrapMessageProvider() {
            return new BootstrapMessageProvider() {
                @Override
                public String runtimeVersionTooLow(String runtimeJavaMajorVersion) {
                    return "LOW:" + runtimeJavaMajorVersion;
                }

                @Override
                public String runtimeActivationFailed(String runtimeJavaMajorVersion) {
                    return "FAIL:" + runtimeJavaMajorVersion;
                }
            };
        }
    }

    private static class TabCapturingBootstrapActivator extends Java8BootstrapActivator {
        String addedTabMessage;

        @Override
        void addIncompatibleAiTab(String message) {
            addedTabMessage = message;
        }
    }
}
