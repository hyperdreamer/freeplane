package org.freeplane.plugin.ai.tools.utilities;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public class ToolExecutorFactory {
    private final boolean wrapToolArgumentsExceptions;
    private final boolean propagateToolExecutionExceptions;
    private final Supplier<Boolean> cancellationSupplier;

    public ToolExecutorFactory(boolean wrapToolArgumentsExceptions, boolean propagateToolExecutionExceptions) {
        this(wrapToolArgumentsExceptions, propagateToolExecutionExceptions, null);
    }

    public ToolExecutorFactory(boolean wrapToolArgumentsExceptions,
                               boolean propagateToolExecutionExceptions,
                               Supplier<Boolean> cancellationSupplier) {
        this.wrapToolArgumentsExceptions = wrapToolArgumentsExceptions;
        this.propagateToolExecutionExceptions = propagateToolExecutionExceptions;
        this.cancellationSupplier = cancellationSupplier;
    }

    public ToolExecutorRegistry createRegistry(Object toolSet) {
        Objects.requireNonNull(toolSet, "toolSet");
        return createRegistry(Collections.singletonList(toolSet));
    }

    public ToolExecutorRegistry createRegistry(Collection<?> toolSets) {
        Objects.requireNonNull(toolSets, "toolSets");
        Map<String, ToolExecutor> executorsByName = new LinkedHashMap<String, ToolExecutor>();
        Map<ToolSpecification, ToolExecutor> executorsBySpecification =
            new LinkedHashMap<ToolSpecification, ToolExecutor>();
        List<ToolSpecification> specifications = new ArrayList<ToolSpecification>();
        for (Object toolSet : toolSets) {
            if (toolSet == null) {
                throw new NullPointerException("toolSets contains null");
            }
            for (Method method : sortedToolMethods(toolSet.getClass())) {
                ToolSpecification specification = ToolSpecifications.toolSpecificationFrom(method);
                if (executorsByName.containsKey(specification.name())) {
                    throw new IllegalArgumentException("Duplicate tool name: " + specification.name());
                }
                DefaultToolExecutor executor = DefaultToolExecutor.builder()
                    .object(toolSet)
                    .originalMethod(method)
                    .methodToInvoke(method)
                    .wrapToolArgumentsExceptions(wrapToolArgumentsExceptions)
                    .propagateToolExecutionExceptions(propagateToolExecutionExceptions)
                    .build();
                ToolExecutor toolExecutor = new EventDispatchToolExecutor(executor);
                if (cancellationSupplier != null) {
                    toolExecutor = new CancellationToolExecutor(toolExecutor, cancellationSupplier);
                }
                executorsByName.put(specification.name(), toolExecutor);
                executorsBySpecification.put(specification, toolExecutor);
                specifications.add(specification);
            }
        }
        ToolSpecifications.validateSpecifications(specifications);
        return new ToolExecutorRegistry(
            Collections.unmodifiableMap(executorsByName),
            Collections.unmodifiableMap(executorsBySpecification));
    }

    private List<Method> sortedToolMethods(Class<?> toolSetClass) {
        Method[] declaredMethods = toolSetClass.getDeclaredMethods();
        List<Method> toolMethods = new ArrayList<Method>(declaredMethods.length);
        for (Method method : declaredMethods) {
            if (method.isAnnotationPresent(Tool.class)) {
                toolMethods.add(method);
            }
        }
        Collections.sort(toolMethods, Comparator
            .comparing(Method::getName)
            .thenComparing(this::signatureKey));
        return toolMethods;
    }

    private String signatureKey(Method method) {
        StringBuilder builder = new StringBuilder();
        builder.append(method.getReturnType().getName()).append('#');
        for (Class<?> parameterType : method.getParameterTypes()) {
            builder.append(parameterType.getName()).append(';');
        }
        return builder.toString();
    }
}
