package org.freeplane.plugin.script;

import java.util.Hashtable;

import org.freeplane.api.Controller;
import org.freeplane.features.ai.code.AiCodeHostService;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.main.application.CommandLineOptions;
import org.freeplane.main.osgi.IModeControllerExtensionProvider;
import org.freeplane.plugin.script.ai.AiOwnedScriptHostService;
import org.freeplane.plugin.script.proxy.ScriptUtils;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class Activator implements BundleActivator {
    private static volatile BundleContext bundleContext;
    private ServiceRegistration<?> aiOwnedScriptHostServiceRegistration;

    public static BundleContext getBundleContext() {
        return bundleContext;
    }

	/*
	 * (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
	 */
	@Override
	public void start(final BundleContext context) throws Exception {
        bundleContext = context;
		final Hashtable<String, String[]> props = new Hashtable<String, String[]>();
		props.put("mode", new String[] { MModeController.MODENAME });
		context.registerService(IModeControllerExtensionProvider.class.getName(),
		    new IModeControllerExtensionProvider() {
			    @Override
				public void installExtension(ModeController modeController, CommandLineOptions options) {
				    new ScriptingRegistration().register(modeController, options);
			    }
		    }, props);
		context.registerService(Controller.class.getName(), ScriptUtils.c(), new Hashtable<String, String[]>());
        Hashtable<String, String> aiHostServiceProperties = new Hashtable<String, String>();
        aiHostServiceProperties.put(AiOwnedScriptHostService.HOST_REGISTRATION_PROPERTY,
            AiOwnedScriptHostService.AI_HOST_REGISTRATION_VALUE);
        aiOwnedScriptHostServiceRegistration = context.registerService(
            AiCodeHostService.class.getName(),
            new AiOwnedScriptHostService(),
            aiHostServiceProperties);
	}

	/*
	 * (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	@Override
	public void stop(final BundleContext context) throws Exception {
        if (aiOwnedScriptHostServiceRegistration != null) {
            aiOwnedScriptHostServiceRegistration.unregister();
            aiOwnedScriptHostServiceRegistration = null;
        }
        if (bundleContext == context) {
            bundleContext = null;
        }
	}
}
