package org.freeplane.plugin.script.filter;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.KeyboardFocusManager;
import java.util.List;

import javax.swing.Icon;
import javax.swing.JOptionPane;

import org.freeplane.core.ui.components.IconListComponent;
import org.freeplane.core.util.HtmlUtils;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.filter.condition.ASelectableCondition;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.n3.nanoxml.XMLElement;
import org.freeplane.plugin.script.ExecuteScriptException;
import org.freeplane.plugin.script.FormulaUtils;
import org.freeplane.plugin.script.GroovyScript;
import org.freeplane.plugin.script.ScriptRunner;
import org.freeplane.plugin.script.ScriptingPermissions;

public class ScriptCondition extends ASelectableCondition {
	private static final String SCRIPT_FILTER_DESCRIPTION_RESOURCE = "plugins/script_filter";
	private static final String SCRIPT_FILTER_ERROR_RESOURCE = "plugins/script_filter_error";
	private static final String SCRIPT_FILTER_EXECUTE_ERROR_RESOURCE = "plugins/script_filter_execute_error";
	static final String NAME = "script_condition";
	static final String TAG_NAME = "script";
	static final String ATTRIB_NAME = "SCRIPT"; // for backward compatibility
	final private ScriptRunner scriptRunner;
	final private String source;
	private boolean errorReported = false;
    private String errorMessage;

	static ASelectableCondition load(final XMLElement element) {
	    final XMLElement child = element.getFirstChildNamed(TAG_NAME);
	    if (child != null) {
		return new ScriptCondition(child.getContent());
	    } else {
		// read attribute for backward compatibility
		return new ScriptCondition(element.getAttribute(ATTRIB_NAME, null));
	    }
	}

	@Override
    public void fillXML(final XMLElement element) {
		final XMLElement child = new XMLElement(TAG_NAME);
		super.fillXML(element);
		child.setContent(source);
		element.addChild(child);
	}

	@Override
    protected String getName() {
	    return NAME;
    }

	public String getScript() {
		return source;
	}

	public ScriptCondition(final String script) {
		super();
		final ScriptingPermissions formulaPermissions = ScriptingPermissions.getFormulaPermissions();
		this.source = script;
		this.scriptRunner = new ScriptRunner(new GroovyScript(script, formulaPermissions));
	}


    private String createErrorDescription(final NodeModel node, String message, String template) {
        final String info = TextUtils.format(template, !errorReported ?  createDescription() : "...",
                node.createID() + ", " +  node.toString(), message.equals(errorMessage) ? "..." : message);
        errorMessage = message;
        return info;
    }

	@Override
	public boolean checkNode(NodeModel node){
        try {
            FilterScriptConditionValidationSupport.ConditionExecutionResult execution =
                FilterScriptConditionValidationSupport.executeCondition(node, source, scriptRunner);
            if (execution.isCycleDetected()) {
                return false;
            }
            Object result = execution.getValue();
            if (FilterScriptConditionValidationSupport.isConditionResult(result))
                return FilterScriptConditionValidationSupport.conditionResultAsBoolean(result);
            final String info = createErrorDescription(node, String.valueOf(result), SCRIPT_FILTER_ERROR_RESOURCE);
            setErrorStatus(info);
        }
        catch (ExecuteScriptException e) {
            final String info = createErrorDescription(node, String.valueOf(e.getMessage()), SCRIPT_FILTER_EXECUTE_ERROR_RESOURCE);
            setErrorStatus(info);
        }
        return false;
	}


	private void setErrorStatus(final String info) {
	    LogUtils.warn(info);
	    if(! errorReported){
	        errorReported = true;
	        JOptionPane.showMessageDialog(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner(), info,
	                TextUtils.getText("error"), JOptionPane.ERROR_MESSAGE);
	        String message = info.trim().replaceAll("\\s", " ");
	        if(message.length() > 80)
	            message = message.substring(0, 80);
	        Controller.getCurrentController().getViewController().out(message);
	    }
    }

	@Override
	protected String createDescription() {
		return TextUtils.format(SCRIPT_FILTER_DESCRIPTION_RESOURCE, source);
	}

    @Override
    protected List<Icon> createRenderedIcons(FontMetrics fontMetrics) {
        return createRenderedIconsFromDescription(fontMetrics);
    }

    @Override
    protected IconListComponent createGraphicComponent(FontMetrics  fontMetrics) {
	    final IconListComponent renderer = super.createGraphicComponent(fontMetrics);
	    final Dimension preferredSize = renderer.getPreferredSize();
	    if(preferredSize.width > 200) {
	        renderer.setPreferredSize(new Dimension(200, preferredSize.height));
        }
		if (preferredSize.width > 200 || source.contains("\n")) {
			renderer.setToolTipText(HtmlUtils.plainToHTML(source));
	    }
		return renderer;
    }

    @Override
    public int hashCode() {
         return source.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        ScriptCondition other = (ScriptCondition) obj;
        return source.equals(other.source);
    }
}
