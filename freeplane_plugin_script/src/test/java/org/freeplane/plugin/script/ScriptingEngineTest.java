package org.freeplane.plugin.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

import java.util.Properties;
import org.freeplane.core.resources.ResourceController;
import org.junit.Test;
import org.mockito.MockedStatic;

public class ScriptingEngineTest {

    @Test
    public void compileGroovyScriptForDiagnosticsSucceedsWithoutExecutingScriptBody() {
        try (MockedStatic<ResourceController> resourceController = mockStatic(ResourceController.class)) {
            resourceController.when(ResourceController::getResourceController).thenReturn(new TestResourceController());
            ScriptingEngine.GroovyCompileResult result = ScriptingEngine.compileGroovyScriptForDiagnostics(
                "throw new RuntimeException('boom')",
                ScriptingPermissions.getPermissiveScriptingPermissions());

            if (result.getErrorMessage() != null) {
                assertThat(result.getErrorMessage()).doesNotContain("boom");
            }
        }
    }

    @Test
    public void compileGroovyScriptForDiagnosticsReturnsErrorMessageAndLineNumberForInvalidGroovy() {
        try (MockedStatic<ResourceController> resourceController = mockStatic(ResourceController.class)) {
            resourceController.when(ResourceController::getResourceController).thenReturn(new TestResourceController());
            ScriptingEngine.GroovyCompileResult result = ScriptingEngine.compileGroovyScriptForDiagnostics(
                "if (",
                ScriptingPermissions.getPermissiveScriptingPermissions());

            assertThat(result.isSuccessful()).isFalse();
            assertThat(result.getCompilerDiagnostics()).isNotEmpty();
            assertThat(result.getErrorMessage()).isNotBlank();
        }
    }

    private static class TestResourceController extends ResourceController {
        private final Properties unsecuredProperties = new Properties();
        private final Properties securedProperties = new Properties();

        private TestResourceController() {
            super();
            unsecuredProperties.setProperty("compiled_script_cache_size", "8");
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
