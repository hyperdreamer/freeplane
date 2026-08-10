package org.freeplane.plugin.graph;

import java.lang.reflect.Field;
import java.util.Dictionary;

import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.main.osgi.IModeControllerExtensionProvider;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
        GraphModeExtension extension = mock(GraphModeExtension.class);
        ServiceRegistration<?> registration = mock(ServiceRegistration.class);
        Activator activator = new Activator();
        setField(activator, "extension", extension);
        setField(activator, "extensionRegistration", registration);

        activator.stop(null);

        InOrder order = inOrder(extension, registration);
        order.verify(extension).close();
        order.verify(registration).unregister();
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = Activator.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
