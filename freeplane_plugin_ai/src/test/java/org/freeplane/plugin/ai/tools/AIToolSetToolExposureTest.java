package org.freeplane.plugin.ai.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.freeplane.plugin.ai.tools.read.ReadNodesWithDescendantsRequest;
import org.junit.Test;

import dev.langchain4j.agent.tool.Tool;

public class AIToolSetToolExposureTest {
    @Test
    public void readNodesWithDescendantsAsPlainText_isExposedAsToolMethod() throws Exception {
        Method method = AIToolSet.class.getMethod(
            "readNodesWithDescendantsAsPlainText",
            ReadNodesWithDescendantsRequest.class);

        Tool annotation = method.getAnnotation(Tool.class);

        assertThat(annotation).isNotNull();
    }

    @Test
    public void getApiDocumentation_isExposedAsToolMethod() throws Exception {
        Method method = AIToolSet.class.getMethod("getApiDocumentation");

        Tool annotation = method.getAnnotation(Tool.class);

        assertThat(annotation).isNotNull();
    }
}
