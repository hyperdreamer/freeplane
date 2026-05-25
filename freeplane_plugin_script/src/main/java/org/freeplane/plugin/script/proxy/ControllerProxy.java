/**
 *
 */
package org.freeplane.plugin.script.proxy;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.InputStream;
import java.net.SocketPermission;
import java.net.URL;
import java.security.AccessControlException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import javax.swing.Icon;
import javax.swing.filechooser.FileFilter;

import org.freeplane.api.AttributeValueSerializer;
import org.freeplane.api.MindMap;
import org.freeplane.api.Node;
import org.freeplane.api.NodeCondition;
import org.freeplane.api.Script;
import org.freeplane.api.ai.AiRequest;
import org.freeplane.api.ai.AiRequestCallback;
import org.freeplane.api.ai.AiRequestHandle;
import org.freeplane.api.ai.AiRequestRejectedException;
import org.freeplane.api.ai.AiRequestService;
import org.freeplane.api.ai.AiRequestStatus;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.IEditHandler.FirstAction;
import org.freeplane.core.undo.IUndoHandler;
import org.freeplane.core.util.FreeplaneVersion;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.export.mindmapmode.ExportController;
import org.freeplane.features.export.mindmapmode.IExportEngine;
import org.freeplane.features.filter.condition.ICondition;
import org.freeplane.features.icon.factory.IconStoreFactory;
import org.freeplane.features.map.IMapSelection;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.mindmapmode.MMapModel;
import org.freeplane.features.mapio.mindmapmode.MMapIO;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.features.text.TextController;
import org.freeplane.features.text.mindmapmode.MTextController;
import org.freeplane.features.ui.IMapViewManager;
import org.freeplane.features.ui.ViewController;
import org.freeplane.plugin.script.Activator;
import org.freeplane.plugin.script.ScriptContext;

import groovy.lang.Closure;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

class ControllerProxy implements Proxy.Controller {
	private final ScriptContext scriptContext;
    private final AiRequestServiceResolver aiRequestServiceResolver;
    private final NetworkPermissionChecker networkPermissionChecker;

	public ControllerProxy(final ScriptContext scriptContext) {
		this(scriptContext, ControllerProxy::lookupAiRequestService, ControllerProxy::checkNetworkPermission);
	}

    ControllerProxy(final ScriptContext scriptContext,
                    AiRequestServiceResolver aiRequestServiceResolver,
                    NetworkPermissionChecker networkPermissionChecker) {
        this.scriptContext = scriptContext;
        this.aiRequestServiceResolver = aiRequestServiceResolver;
        this.networkPermissionChecker = networkPermissionChecker;
    }

	@Override
	public void centerOnNode(final Node center) {
		final NodeModel nodeModel = ((NodeProxy) center).getDelegate();
		Controller.getCurrentController().getSelection().scrollNodeToCenter(nodeModel, false);
	}

	@Override
	public void edit(Node node) {
		editImpl(node, true);
	}

	@Override
	public void editInPopup(Node node) {
		editImpl(node, false);
	}

	private void editImpl(Node node, boolean editInline) {
	    final NodeModel nodeModel = ((NodeProxy) node).getDelegate();
		Controller.getCurrentController().getSelection().selectAsTheOnlyOneSelected(nodeModel);
		((MTextController) TextController.getController()).edit(FirstAction.EDIT_CURRENT, !editInline);
    }

	@Override
	public Node getSelected() {
		reportArbitraryNodeAccess();
		IMapSelection selection = Controller.getCurrentController().getSelection();
		return selection != null ? new NodeProxy(selection.getSelected(), scriptContext) : null;
	}



	@Override
	public Node getViewRoot() {
		reportArbitraryNodeAccess();
		IMapSelection selection = Controller.getCurrentController().getSelection();
		return selection != null ? new NodeProxy(selection.getSelectionRoot(), scriptContext) : null;
	}

	private void reportArbitraryNodeAccess() {
		if (scriptContext != null)
			scriptContext.accessAll();
	}

	@Override
	public List<? extends Node> getSelecteds() {
		reportArbitraryNodeAccess();
		return ProxyUtils.createNodeList(Controller.getCurrentController().getSelection().getOrderedSelection(), scriptContext);
	}

	@Override
	public List<? extends Node> getSortedSelection(final boolean differentSubtrees) {
		reportArbitraryNodeAccess();
		return ProxyUtils.createNodeList(Controller.getCurrentController().getSelection()
		    .getSortedSelection(differentSubtrees), scriptContext);
	}

    @Override
	public void select(final Node toSelect) {
        if (toSelect != null) {
            final NodeModel nodeModel = ((NodeProxy) toSelect).getDelegate();
            Controller.getCurrentModeController().getMapController().displayNode(nodeModel);
            Controller.getCurrentController().getSelection().selectAsTheOnlyOneSelected(nodeModel);
        }
    }

    @Override
	public void selectBranch(final Node branchRoot) {
        if (branchRoot != null) {
            final NodeModel nodeModel = ((NodeProxy) branchRoot).getDelegate();
            Controller.getCurrentModeController().getMapController().displayNode(nodeModel);
            Controller.getCurrentController().getSelection().selectBranch(nodeModel, false);
        }
    }

	@Override
	public void select(final Collection<? extends Node> toSelect) {
		final Iterator<? extends Node> it = toSelect.iterator();
		if (!it.hasNext()) {
			return;
		}
		final Node firstNode = it.next();
		select(firstNode);
		while (it.hasNext()) {
			final Node nextNode = it.next();
			final NodeModel nodeModel = ((NodeProxy) nextNode).getDelegate();
			Controller.getCurrentModeController().getMapController().displayNode(nodeModel);
			Controller.getCurrentController().getSelection().toggleSelected(nodeModel);
		}
	}

    @Override
	public void selectMultipleNodes(final Collection<? extends Node> toSelect) {
	    select(toSelect);
	}

	@Override
	public void deactivateUndo() {
		final MapModel map = Controller.getCurrentController().getMap();
		if (map instanceof MapModel) {
			MModeController modeController = ((MModeController) Controller.getCurrentModeController());
			modeController.deactivateUndo((MMapModel) map);
		}
	}

	@Override
	public void undo() {
		final MapModel map = Controller.getCurrentController().getMap();
		final IUndoHandler undoHandler = map.getExtension(IUndoHandler.class);
		undoHandler.undo();
	}

	@Override
	public void redo() {
		final MapModel map = Controller.getCurrentController().getMap();
		final IUndoHandler undoHandler = map.getExtension(IUndoHandler.class);
		undoHandler.redo();
	}

	@Override
	public void setStatusInfo(final String info) {
		final ViewController viewController = getViewController();
		viewController.out(info);
	}

	private ViewController getViewController() {
		return Controller.getCurrentController().getViewController();
	}

	private IMapViewManager getMapViewManager() {
		return Controller.getCurrentController().getMapViewManager();
	}

	@Override
	public void setStatusInfo(final String infoPanelKey, final String info) {
		final ViewController viewController = getViewController();
		viewController.addStatusInfo(infoPanelKey, info, null);
	}

	@Override
	public void setStatusInfo(final String infoPanelKey, final String info, final String iconKey) {
		final ViewController viewController = getViewController();
		viewController.addStatusInfo(infoPanelKey, info, IconStoreFactory.ICON_STORE.getUIIcon(iconKey).getIcon());
	}

	@Override
	@Deprecated
	public void setStatusInfo(final String infoPanelKey, final Icon icon) {
		final ViewController viewController = getViewController();
		viewController.addStatusInfo(infoPanelKey, null, icon);
	}

	@Override
	public FreeplaneVersion getFreeplaneVersion() {
		return FreeplaneVersion.getVersion();
	}

	@Override
	public File getUserDirectory() {
	    return new File(ResourceController.getResourceController().getFreeplaneUserDirectory());
    }

	@Override
	@Deprecated
	public List<? extends Node> find(final ICondition condition) {
		reportArbitraryNodeAccess();
		return ProxyUtils.find(condition, currentMapRootNode(), scriptContext);
	}

	@Override
	public List<? extends Node> find(NodeCondition condition) {
		reportArbitraryNodeAccess();
		return ProxyUtils.find(condition, currentMapRootNode(), scriptContext);
	}

	@Override
	public List<? extends Node> find(boolean withAncestors, boolean withDescendants, NodeCondition condition) {
		reportArbitraryNodeAccess();
		return ProxyUtils.find(withAncestors, withDescendants, condition, currentMapRootNode(), scriptContext);
	}

	private NodeModel currentMapRootNode() {
		return Controller.getCurrentController().getMap().getRootNode();
	}
	@Override
	public List<? extends Node> find(final Closure<Boolean> closure) {
		reportArbitraryNodeAccess();
		return ProxyUtils.find(closure, currentMapRootNode(), scriptContext);
	}
	@Override
	public List<? extends Node> find(boolean withAncestors, boolean withDescendants, final Closure<Boolean> closure) {
		reportArbitraryNodeAccess();
		return ProxyUtils.find(withAncestors, withDescendants, closure, currentMapRootNode(), scriptContext);
	}

	// NodeRO: R
	@Override
	public List<? extends Node> findAll() {
		reportArbitraryNodeAccess();
		return ProxyUtils.findAll(currentMapRootNode(), scriptContext, false);
    }

	// NodeRO: R
	@Override
	public List<? extends Node> findAllDepthFirst() {
		reportArbitraryNodeAccess();
		return ProxyUtils.findAll(currentMapRootNode(), scriptContext, true);
    }

	@Override
	public MindMap newMindMap() {
		final MMapIO mapIO = MMapIO.getInstance();
		final MapModel newMap = mapIO.newMapFromDefaultTemplate();
		return newMap != null ? new MapProxy(newMap, scriptContext) : null;
	}


    @Override
	public float getZoom() {
	    return getMapViewManager().getZoom();
    }

    @Override
	public void setZoom(float ratio) {
    	getMapViewManager().setZoom(ratio);
    }

    @Override
	public boolean isInteractive() {
        return !GraphicsEnvironment.isHeadless();
    }

    @Override
	public List<String> getExportTypeDescriptions() {
        final ArrayList<String> list = new ArrayList<String>();
        for (FileFilter fileFilter : ExportController.getContoller().getMapExportFileFilters()) {
            list.add(fileFilter.getDescription());
        }
        return list;
    }

    @Override
	public void export(MindMap map, File destFile, String exportTypeDescription, boolean overwriteExisting) {
        String destinationName = destFile.getName();
        String destinationExtension = destinationName.substring(destinationName.lastIndexOf('.'));
		List<FileFilter> fileFilters = ExportController.getContoller().getMapExportFileFilters();
		final FileFilter filter = findExportFileFilterByDescription(fileFilters, exportTypeDescription, destinationExtension);
        if (filter == null) {
            throw new IllegalArgumentException("no export defined for '" + exportTypeDescription + "'");
        }
        else if (!overwriteExisting && destFile.exists()) {
            throw new RuntimeException("destination file " + destFile.getAbsolutePath()
                    + " already exists - set overwriteExisting to true?");
        }
		HashMap<FileFilter, IExportEngine> exportEngines = ExportController.getContoller().getMapExportEngines();
		final IExportEngine exportEngine = exportEngines.get(filter);
		MapModel mapDelegate = ((MapProxy) map).getDelegate();
		exportEngine.export(Collections.singletonList(mapDelegate.getRootNode()), destFile);
		LogUtils.info("exported " + map.getFile() + " to " + destFile.getAbsolutePath());
    }

    private FileFilter findExportFileFilterByDescription(List<FileFilter> fileFilters, String exportTypeDescription, String destinationExtension) {
        String exportTypeDescriptionLowerCase = exportTypeDescription.toLowerCase();
        String destinationExtensionLowerCase = destinationExtension.toLowerCase();
		for (FileFilter fileFilter : fileFilters) {
            String filterDescriptionLowerCase = fileFilter.getDescription().toLowerCase();
            if (filterDescriptionLowerCase.equals(exportTypeDescriptionLowerCase)
                    || filterDescriptionLowerCase.contains(destinationExtensionLowerCase) &&
                       filterDescriptionLowerCase.contains(exportTypeDescriptionLowerCase) )
                return fileFilter;
        }
        return null;
    }

    @Override
	public List<MindMap> getOpenMindMaps() {
    	return getMapViewManager().getMaps().values().stream()
    	.distinct()
    	.map(m -> new MapProxy(m, scriptContext))
    	.collect(Collectors.toList());
    }

	@Override
	public Proxy.Loader mapLoader(File file) {
		return LoaderProxy.of(file, scriptContext);
	}

	@Override
	public Proxy.Loader load(File file) {
		return mapLoader(file);
	}

	@Override
	public Proxy.Loader mapLoader(URL url) {
		return LoaderProxy.of(url, scriptContext);
	}

	@Override
	public Proxy.Loader load(URL url) {
		return mapLoader(url);
	}

	@Override
	public Proxy.Loader load(String file) {
		return mapLoader(file);
	}

	@Override
	public Proxy.Loader mapLoader(String fileOrContent) {
		return LoaderProxy.of(fileOrContent, scriptContext);
	}

	@Override
	public Proxy.Loader mapLoader(InputStream inputStream) {
		return LoaderProxy.of(inputStream, scriptContext);
	}

	@Override
	public Script script(File file) {
		return new FileScriptProxy(file, scriptContext);
	}

	@Override
	public Script script(String script, String type) {
		return new StringScriptProxy(script, type, scriptContext);
	}

	@Override
	public AttributeValueSerializer getAttributeValueSerializer() {
		return StaticAttributeValueSerializer.INSTANCE;
	}

	@Override
	public ExecutorService getMainThreadExecutorService() {
		return Controller.getCurrentController().getMainThreadExecutorService();
	}

    @Override
    public AiRequestHandle askAi(AiRequest request, AiRequestCallback callback) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(callback, "callback");
        try {
            networkPermissionChecker.check();
        } catch (SecurityException permissionDenied) {
            throw new AiRequestRejectedException(
                AiRequestStatus.PERMISSION_DENIED,
                permissionDenied.getMessage());
        }
        return AccessController.doPrivileged(new PrivilegedAction<AiRequestHandle>() {
            @Override
            public AiRequestHandle run() {
                AiRequestService aiRequestService = aiRequestServiceResolver.resolve();
                if (aiRequestService == null) {
                    throw new AiRequestRejectedException(
                        AiRequestStatus.AI_UNAVAILABLE,
                        "AI request service is unavailable.");
                }
                return aiRequestService.askAi(request, callback);
            }
        });
    }

    public AiRequestHandle askAi(AiRequest request, Closure<?> callback) {
        Objects.requireNonNull(callback, "callback");
        return askAi(request, result -> callback.call(result));
    }

    private static AiRequestService lookupAiRequestService() {
        BundleContext bundleContext = Activator.getBundleContext();
        if (bundleContext == null) {
            return null;
        }
        ServiceReference<AiRequestService> serviceReference = bundleContext.getServiceReference(AiRequestService.class);
        if (serviceReference == null) {
            return null;
        }
        return bundleContext.getService(serviceReference);
    }

    private static void checkNetworkPermission() {
        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager == null) {
            return;
        }
        try {
            securityManager.checkPermission(new SocketPermission("*", "connect"));
        } catch (AccessControlException accessControlException) {
            String message = accessControlException.getMessage();
            throw new SecurityException(message == null ? "AI request network access denied." : message,
                accessControlException);
        }
    }

    interface AiRequestServiceResolver {
        AiRequestService resolve();
    }

    interface NetworkPermissionChecker {
        void check();
    }

}
