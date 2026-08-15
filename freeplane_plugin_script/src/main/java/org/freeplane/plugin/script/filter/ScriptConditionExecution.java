package org.freeplane.plugin.script.filter;

import org.freeplane.features.map.NodeModel;
import org.freeplane.plugin.script.FormulaThreadLocalStacks;
import org.freeplane.plugin.script.FormulaUtils;
import org.freeplane.plugin.script.NodeScript;
import org.freeplane.plugin.script.ScriptContext;
import org.freeplane.plugin.script.ScriptRunner;

final class ScriptConditionExecution {
    private ScriptConditionExecution() {
    }

    static ConditionExecutionResult execute(NodeModel node, String source, ScriptRunner scriptRunner) {
        ScriptContext scriptContext = new ScriptContext(new NodeScript(node, source));
        if (!FormulaThreadLocalStacks.INSTANCE.push(scriptContext)) {
            return ConditionExecutionResult.cycleDetected();
        }
        scriptRunner.setScriptContext(scriptContext);
        try {
            return ConditionExecutionResult.value(
                FormulaUtils.executeScript(scriptContext, () -> scriptRunner.execute(node)));
        } finally {
            FormulaThreadLocalStacks.INSTANCE.pop();
            scriptRunner.setScriptContext(null);
        }
    }

    static final class ConditionExecutionResult {
        private final boolean cycleDetected;
        private final Object value;

        private ConditionExecutionResult(boolean cycleDetected, Object value) {
            this.cycleDetected = cycleDetected;
            this.value = value;
        }

        static ConditionExecutionResult cycleDetected() {
            return new ConditionExecutionResult(true, null);
        }

        static ConditionExecutionResult value(Object value) {
            return new ConditionExecutionResult(false, value);
        }

        boolean isCycleDetected() {
            return cycleDetected;
        }

        Object getValue() {
            return value;
        }
    }
}
