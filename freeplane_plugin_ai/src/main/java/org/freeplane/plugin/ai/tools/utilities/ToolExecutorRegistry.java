package org.freeplane.plugin.ai.tools.utilities;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class ToolExecutorRegistry {
    private final Map<String, ToolExecutor> executorsByName;
    private final Map<ToolSpecification, ToolExecutor> executorsBySpecification;

    ToolExecutorRegistry(Map<String, ToolExecutor> executorsByName,
                         Map<ToolSpecification, ToolExecutor> executorsBySpecification) {
        this.executorsByName = executorsByName;
        this.executorsBySpecification = executorsBySpecification;
    }

    public Map<String, ToolExecutor> getExecutorsByName() {
        return executorsByName;
    }

    public Map<ToolSpecification, ToolExecutor> getExecutorsBySpecification() {
        return executorsBySpecification;
    }

    public ToolExecutorRegistry filtered(Collection<String> allowedToolNames) {
        if (allowedToolNames == null || allowedToolNames.isEmpty()) {
            return new ToolExecutorRegistry(Collections.<String, ToolExecutor>emptyMap(),
                Collections.<ToolSpecification, ToolExecutor>emptyMap());
        }
        Set<String> allowedToolNameSet = new LinkedHashSet<String>(allowedToolNames);
        if (allowedToolNameSet.containsAll(executorsByName.keySet())) {
            return this;
        }
        Map<String, ToolExecutor> filteredExecutorsByName = new LinkedHashMap<String, ToolExecutor>();
        for (Map.Entry<String, ToolExecutor> entry : executorsByName.entrySet()) {
            if (allowedToolNameSet.contains(entry.getKey())) {
                filteredExecutorsByName.put(entry.getKey(), entry.getValue());
            }
        }
        Map<ToolSpecification, ToolExecutor> filteredExecutorsBySpecification =
            new LinkedHashMap<ToolSpecification, ToolExecutor>();
        for (Map.Entry<ToolSpecification, ToolExecutor> entry : executorsBySpecification.entrySet()) {
            if (filteredExecutorsByName.containsKey(entry.getKey().name())) {
                filteredExecutorsBySpecification.put(entry.getKey(), entry.getValue());
            }
        }
        return new ToolExecutorRegistry(
            Collections.unmodifiableMap(filteredExecutorsByName),
            Collections.unmodifiableMap(filteredExecutorsBySpecification));
    }
}
