package org.freeplane.plugin.script;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ExecutingScriptContextStack {
	public static final ExecutingScriptContextStack INSTANCE = new ExecutingScriptContextStack();

	private final ThreadLocal<List<ScriptContext>> stack = new ThreadLocal<List<ScriptContext>>() {
		@Override
		protected List<ScriptContext> initialValue() {
			return new ArrayList<ScriptContext>(8);
		}
	};

	private ExecutingScriptContextStack() {
	}

	public ScriptContext getCurrentContext() {
		final List<ScriptContext> contexts = stack.get();
		return contexts.isEmpty() ? null : contexts.get(contexts.size() - 1);
	}

	void push(final ScriptContext scriptContext) {
		stack.get().add(scriptContext);
	}

	void pop() {
		final List<ScriptContext> contexts = stack.get();
		if (!contexts.isEmpty()) {
			contexts.remove(contexts.size() - 1);
			if (contexts.isEmpty()) {
				stack.remove();
			}
		}
	}

	public void withContext(final ScriptContext scriptContext, final Runnable runnable) {
		Objects.requireNonNull(runnable, "runnable");
		push(scriptContext);
		try {
			runnable.run();
		}
		finally {
			pop();
		}
	}
}
