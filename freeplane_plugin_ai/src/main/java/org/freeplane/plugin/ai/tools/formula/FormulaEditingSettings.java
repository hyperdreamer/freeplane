package org.freeplane.plugin.ai.tools.formula;

import org.freeplane.core.resources.ResourceController;

public class FormulaEditingSettings {
    public static final String FORMULA_EDITING_ENABLED_PROPERTY = "ai_formula_editing_enabled";

    private final ResourceController resourceController;

    public FormulaEditingSettings() {
        this(ResourceController.getResourceController());
    }

    public FormulaEditingSettings(ResourceController resourceController) {
        this.resourceController = resourceController;
    }

    public boolean isEnabled() {
        return resourceController != null && resourceController.getBooleanProperty(FORMULA_EDITING_ENABLED_PROPERTY);
    }
}
