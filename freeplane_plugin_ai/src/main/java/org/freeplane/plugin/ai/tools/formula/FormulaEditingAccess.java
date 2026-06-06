package org.freeplane.plugin.ai.tools.formula;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevel;

public final class FormulaEditingAccess {
    public static final Set<String> FORMULA_TOOL_NAMES = Collections.unmodifiableSet(
        new LinkedHashSet<String>(Arrays.asList("previewFormulaUpdates", "applyFormulaUpdates")));

    private FormulaEditingAccess() {
    }

    public static boolean isFormulaTool(String toolName) {
        return toolName != null && FORMULA_TOOL_NAMES.contains(toolName);
    }

    public static boolean isFormulaEditingAllowed(ToolAvailabilityLevel toolAvailability,
                                                  boolean formulaEditingEnabled) {
        return formulaEditingEnabled
            && toolAvailability != null
            && toolAvailability.includesEditing();
    }
}
