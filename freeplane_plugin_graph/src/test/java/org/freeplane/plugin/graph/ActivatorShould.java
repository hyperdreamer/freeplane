package org.freeplane.plugin.graph;

import java.util.Dictionary;

import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.main.osgi.IModeControllerExtensionProvider;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockedConstruction;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ActivatorShould {

    @Test
    public void registerOneMindMapModeExtensionProvider() throws Exception {
        BundleContext context = mock(BundleContext.class);
        Activator activator = new Activator();

        activator.start(context);

        ArgumentCaptor<IModeControllerExtensionProvider> providerCaptor =
            ArgumentCaptor.forClass(IModeControllerExtensionProvider.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Dictionary<String, String[]>> propertiesCaptor =
            (ArgumentCaptor<Dictionary<String, String[]>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Dictionary.class);
        verify(context).registerService(
            eq(IModeControllerExtensionProvider.class.getName()),
            providerCaptor.capture(),
            propertiesCaptor.capture());
        assertThat(providerCaptor.getValue()).isInstanceOf(GraphModeExtension.class);
        assertThat(propertiesCaptor.getValue().get("mode")).containsExactly(MModeController.MODENAME);
    }

    @Test
    public void closeExtensionBeforeUnregisteringIt() throws Exception {
        BundleContext context = mock(BundleContext.class);
        ServiceRegistration<?> registration = mock(ServiceRegistration.class);
        when(context.registerService(
            eq(IModeControllerExtensionProvider.class.getName()),
            any(IModeControllerExtensionProvider.class),
            any(Dictionary.class))).thenReturn(registration);
        Activator activator = new Activator();

        try (MockedConstruction<GraphModeExtension> constructions = mockConstruction(GraphModeExtension.class)) {
            activator.start(context);
            activator.stop(context);

            GraphModeExtension extension = constructions.constructed().get(0);
            InOrder order = inOrder(extension, registration);
            order.verify(extension).close();
            order.verify(registration).unregister();
        }
    }
}
