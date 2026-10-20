package org.freeplane.plugin.graph;

import java.util.Hashtable;

import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.main.application.CommandLineOptions;
import org.freeplane.main.osgi.IModeControllerExtensionProvider;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public final class Activator implements BundleActivator {
    private GraphModeExtension extension;
    private ServiceRegistration<?> extensionRegistration;

    @Override
    public void start(final BundleContext context) throws Exception {
        extension = new GraphModeExtension();
        final Hashtable<String, String[]> properties = new Hashtable<String, String[]>();
        properties.put("mode", new String[] { MModeController.MODENAME });
        extensionRegistration = context.registerService(
            IModeControllerExtensionProvider.class.getName(), extension, properties);
    }

    @Override
    public void stop(final BundleContext context) throws Exception {
        final GraphModeExtension extensionToClose = extension;
        final ServiceRegistration<?> registrationToRemove = extensionRegistration;
        extension = null;
        extensionRegistration = null;
        try {
            if (extensionToClose != null) {
                extensionToClose.close();
            }
        }
        finally {
            if (registrationToRemove != null) {
                registrationToRemove.unregister();
            }
        }
    }
}
