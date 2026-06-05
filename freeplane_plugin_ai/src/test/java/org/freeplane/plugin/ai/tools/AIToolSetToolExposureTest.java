package org.freeplane.plugin.ai.tools;

import dev.langchain4j.agent.tool.Tool;
import java.lang.reflect.Method;
import org.freeplane.plugin.ai.tools.read.ReadNodesWithDescendantsRequest;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
