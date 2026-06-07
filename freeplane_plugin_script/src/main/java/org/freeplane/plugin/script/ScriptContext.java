package org.freeplane.plugin.script;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import org.freeplane.core.util.Compat;
import org.freeplane.features.attribute.Attribute;
import org.freeplane.features.explorer.AccessedNodes;
import org.freeplane.features.map.NodeModel;
import org.freeplane.plugin.script.dependencies.RelatedElements;

public class ScriptContext implements AccessedNodes{

	private final NodeScript nodeScript;

	private final RelatedElements relatedElements;

	private final ScriptingPermissions effectivePermissions;
	private final boolean dependencyTrackingEnabled;

	public ScriptContext(NodeScript nodeScript) {
		this(nodeScript, nodeScript != null ? new RelatedElements(nodeScript.node) : null, null, true);
	}

	private ScriptContext(NodeScript nodeScript, RelatedElements relatedElements,
			ScriptingPermissions effectivePermissions, boolean dependencyTrackingEnabled) {
		this.nodeScript = nodeScript;
		this.relatedElements = relatedElements;
		this.effectivePermissions = effectivePermissions;
		this.dependencyTrackingEnabled = dependencyTrackingEnabled;
	}

	public NodeScript getNodeScript() {
		return nodeScript;
	}

	public URL getBaseUrl() {
		return nodeScript != null ? nodeScript.getBaseUrl() : null;
	}

	public ScriptingPermissions getEffectivePermissions() {
		return effectivePermissions;
	}

	public ScriptContext withEffectivePermissions(ScriptingPermissions effectivePermissions) {
		if (this.effectivePermissions == effectivePermissions)
			return this;
		return new ScriptContext(nodeScript, relatedElements, effectivePermissions, dependencyTrackingEnabled);
	}

	public ScriptContext withDependencyTracking(boolean dependencyTrackingEnabled) {
		if (this.dependencyTrackingEnabled == dependencyTrackingEnabled)
			return this;
		return new ScriptContext(nodeScript, relatedElements, effectivePermissions, dependencyTrackingEnabled);
	}

	public File toAbsoluteFile(File file) {
		final File absoluteFile;
		if(file.isAbsolute())
			absoluteFile = file;
		else {
			final URL baseUrl = getBaseUrl();
			if (baseUrl == null)
				throw new IllegalStateException("Can not use relative files without base URL");
			else {
				final File parentFile = Compat.urlToFile(baseUrl).getAbsoluteFile().getParentFile();
				absoluteFile = new File(parentFile, file.getPath());
			}
		}
		return absoluteFile;
	}

	public URL toUrl(String path) {
		try {
			File file = new File(path);
			if(file.isAbsolute()) {
				return file.getCanonicalFile().toURL();
			}
			else  {
				URL baseUrl = getBaseUrl();
				if (baseUrl != null){
					return new URL(baseUrl, path);
				}
				else
					return file.getCanonicalFile().toURL();
			}
		}
		catch (IOException e) {
			throw new IllegalArgumentException(e);
		}
	}

	@Override
	public void accessAttribute(final NodeModel accessedNode, Attribute accessedAttribute) {
		if(nodeScript != null)
			relatedElements.relateAttribute(accessedNode, accessedAttribute);
	}

	@Override
	public void accessValue(NodeModel accessedNode) {
		if(nodeScript != null)
			relatedElements.relateNode(accessedNode);
	}

	@Override
	public void accessNode(final NodeModel accessedNode) {
		if(nodeScript != null) {
            if (dependencyTrackingEnabled)
			    FormulaDependencies.accessNode(nodeScript.node, accessedNode);
			relatedElements.relateMap(accessedNode.getMap());
		}
	}

    @Override
    public void accessBranch(final NodeModel accessedNode) {
        if(nodeScript != null && dependencyTrackingEnabled)
            FormulaDependencies.accessBranch(nodeScript.node, accessedNode);
    }

    @Override
    public void accessClones(final NodeModel accessedNode) {
        if(nodeScript != null && dependencyTrackingEnabled)
            FormulaDependencies.accessClones(nodeScript.node, accessedNode);
    }

	@Override
	public void accessAll() {
		if(nodeScript != null && dependencyTrackingEnabled)
			FormulaDependencies.accessAll(nodeScript.node);
	}
	@Override
	public void accessGlobalNode() {
		if(nodeScript != null && dependencyTrackingEnabled)
			FormulaDependencies.accessGlobalNode(nodeScript.node);
	}

	public RelatedElements getRelatedElements() {
		if(nodeScript != null)
			return relatedElements;
		else
			throw new IllegalStateException("Accessed values not tracked without related node");
	}

	@Override
	public String toString() {
		return String.valueOf(nodeScript);
	}

}
