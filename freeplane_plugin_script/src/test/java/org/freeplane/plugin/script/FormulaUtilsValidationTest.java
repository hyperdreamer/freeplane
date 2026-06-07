package org.freeplane.plugin.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.junit.Test;
import org.mockito.MockedStatic;

public class FormulaUtilsValidationTest {

    @Test
    public void validateFormulaDoesNotPopulatePersistentCacheOrDependencyState() throws Exception {
        try (MockedStatic<ResourceController> resourceController = mockStatic(ResourceController.class)) {
            resourceController.when(ResourceController::getResourceController).thenReturn(new TestResourceController());
            if (ScriptResources.getClasspath() == null) {
                ScriptResources.setClasspath(Collections.<String>emptyList());
            }
            MapModel map = new MapModel((source, targetMap, withChildren) -> null, null, null);
            NodeModel parent = new NodeModel("parent", map);
            NodeModel child = new NodeModel("child", map);
            parent.insert(child, 0);
            ByteArrayOutputStream outputBuffer = new ByteArrayOutputStream();

            Object result = FormulaUtils.validateFormula(
                child,
                "=node.parent.text",
                new PrintStream(outputBuffer, false, "UTF-8"),
                line -> {
                });

            assertThat(result).isEqualTo("parent");
            assertThat(map.getExtension(FormulaCache.class)).isNull();
            assertThat(toList(FormulaDependencies.getPossibleDependencies(parent))).isEmpty();
        }
    }

    private List<NodeModel> toList(Iterable<NodeModel> nodes) {
        List<NodeModel> list = new ArrayList<NodeModel>();
        for (NodeModel node : nodes) {
            list.add(node);
        }
        return list;
    }

    private static class TestResourceController extends ResourceController {
        private final Properties unsecuredProperties = new Properties();
        private final Properties securedProperties = new Properties();

        private TestResourceController() {
            super();
            unsecuredProperties.setProperty("compiled_script_cache_size", "8");
            unsecuredProperties.setProperty("formula_disable_caching", "false");
        }

        @Override
        public String getFreeplaneUserDirectory() {
            return System.getProperty("java.io.tmpdir");
        }

        @Override
        public Properties getUnsecuredProperties() {
            return unsecuredProperties;
        }

        @Override
        public String getProperty(String key) {
            return unsecuredProperties.getProperty(key);
        }

        @Override
        public void saveProperties() {
        }

        @Override
        public void setDefaultProperty(String key, String value) {
            unsecuredProperties.setProperty(key, value);
        }

        @Override
        public void setProperty(String property, String value) {
            unsecuredProperties.setProperty(property, value);
        }

        @Override
        public Properties getSecuredProperties() {
            return securedProperties;
        }

        @Override
        public void securePropertyForModification(String key) {
        }

        @Override
        public void securePropertyForReadingAndModification(String key) {
        }

        @Override
        public void persistPropertyInSecretsFile(String key) {
        }
    }
}
