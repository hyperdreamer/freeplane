package org.freeplane.plugin.script.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.stream.Collectors;
import org.freeplane.api.Controller;
import org.freeplane.api.ControllerRO;
import org.freeplane.api.ai.AiRequest;
import org.junit.Test;

public class ControllerApiExposureTest {

    @Test
    public void askAiIsExposedOnlyOnReadWriteControllerInterfaces() {
        assertThat(methodNames(Controller.class)).contains("askAi");
        assertThat(methodNames(Proxy.Controller.class)).contains("askAi");
        assertThat(methodNames(ControllerRO.class)).doesNotContain("askAi");
        assertThat(methodNames(Proxy.ControllerRO.class)).doesNotContain("askAi");
    }

    @Test
    public void groovyClosureAskAiOverloadExistsOnlyOnImplementingClass() {
        assertThat(methodSignatures(Controller.class)).doesNotContain("askAi(AiRequest,Closure)");
        assertThat(methodSignatures(Proxy.Controller.class)).doesNotContain("askAi(AiRequest,Closure)");
        assertThat(methodSignatures(ControllerProxy.class)).contains("askAi(AiRequest,Closure)");
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
