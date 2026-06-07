package org.freeplane.plugin.script.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.stream.Collectors;
import org.freeplane.api.Controller;
import org.freeplane.api.ControllerRO;
import org.junit.Test;

public class ControllerApiExposureTest {

    @Test
    public void aiMethodsAreExposedOnlyOnReadWriteControllerInterfaces() {
        assertThat(methodNames(Controller.class)).contains("askAi", "runAiPrompt");
        assertThat(methodNames(Proxy.Controller.class)).contains("askAi", "runAiPrompt");
        assertThat(methodNames(ControllerRO.class)).doesNotContain("askAi", "runAiPrompt");
        assertThat(methodNames(Proxy.ControllerRO.class)).doesNotContain("askAi", "runAiPrompt");
    }

    @Test
    public void groovyClosureAiOverloadsExistOnlyOnImplementingClass() {
        assertThat(methodSignatures(Controller.class))
            .doesNotContain("askAi(String,AiRequestOptions,Closure)")
            .doesNotContain("runAiPrompt(String,Duration,Closure)")
            .doesNotContain("runAiPrompt(String,AiRequestOptions,Closure)");
        assertThat(methodSignatures(Proxy.Controller.class))
            .doesNotContain("askAi(String,AiRequestOptions,Closure)")
            .doesNotContain("runAiPrompt(String,Duration,Closure)")
            .doesNotContain("runAiPrompt(String,AiRequestOptions,Closure)");
        assertThat(methodSignatures(ControllerProxy.class))
            .contains("askAi(String,AiRequestOptions,Closure)")
            .contains("runAiPrompt(String,Duration,Closure)")
            .contains("runAiPrompt(String,AiRequestOptions,Closure)");
    }

    private java.util.List<String> methodNames(Class<?> type) {
        return Arrays.stream(type.getMethods())
            .map(method -> method.getName())
            .distinct()
            .collect(Collectors.toList());
    }

    private java.util.List<String> methodSignatures(Class<?> type) {
        return Arrays.stream(type.getMethods())
            .map(method -> method.getName() + "(" + Arrays.stream(method.getParameterTypes())
                .map(Class::getSimpleName)
                .collect(Collectors.joining(",")) + ")")
            .distinct()
            .collect(Collectors.toList());
    }
}
