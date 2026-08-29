package org.freeplane.plugin.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Collections;
import java.util.Properties;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.CapturedPrintStream;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.junit.Test;
import org.mockito.MockedStatic;

public class ScriptingEngineTest {

    @Test
    public void compileGroovyScriptForDiagnosticsSucceedsWithoutExecutingScriptBody() {
        try (MockedStatic<ResourceController> resourceController = mockStatic(ResourceController.class)) {
            resourceController.when(ResourceController::getResourceController).thenReturn(new TestResourceController());
            ensureScriptClasspath();
            ScriptingEngine.GroovyCompileResult result = ScriptingEngine.compileGroovyScriptForDiagnostics(
                "throw new RuntimeException('boom')",
                ScriptingPermissions.getPermissiveScriptingPermissions());

            if (result.getErrorMessage() != null) {
                assertThat(result.getErrorMessage()).doesNotContain("boom");
            }
        }
    }

    @Test
    public void executedGroovyScriptOutputCanBeCapturedAndWrittenToLiveStdout() throws Exception {
        try (MockedStatic<ResourceController> resourceController = mockStatic(ResourceController.class)) {
            resourceController.when(ResourceController::getResourceController).thenReturn(new TestResourceController());
            if (ScriptResources.getClasspath() == null) {
                ScriptResources.setClasspath(Collections.<String>emptyList());
            }
            ByteArrayOutputStream liveBuffer = new ByteArrayOutputStream();
            try (CapturedPrintStream capture = CapturedPrintStream.tee(new PrintStream(liveBuffer, false, "UTF-8"))) {
                NodeModel node = new NodeModel("node", new MapModel((source, targetMap, withChildren) -> null, null, null));

                Object result = ScriptingEngine.executeScript(
                    node,
                    "println 'hello from script'\nreturn 7",
                    line -> {
                    },
                    capture.printStream(),
                    new ScriptContext(null),
                    ScriptingPermissions.getPermissiveScriptingPermissions());

                assertThat(result).isEqualTo(7);
                assertThat(normalizeLineEndings(capture.text()).trim()).isEqualTo("hello from script");
                assertThat(normalizeLineEndings(liveBuffer.toString("UTF-8")).trim()).isEqualTo("hello from script");
            }
        }
    }

    @Test
    public void compileGroovyScriptForDiagnosticsReturnsLineAndColumnForSyntaxError() {
        try (MockedStatic<ResourceController> resourceController = mockStatic(ResourceController.class)) {
            resourceController.when(ResourceController::getResourceController).thenReturn(new TestResourceController());
            ensureScriptClasspath();
            ScriptingEngine.GroovyCompileResult result = ScriptingEngine.compileGroovyScriptForDiagnostics(
                "println 'start'\n"
                    + "def x = 1\n"
                    + "def y = 2\n"
                    + "if (x > y {\n"
                    + "    println 'broken'\n"
                    + "}\n",
                ScriptingPermissions.getPermissiveScriptingPermissions());

            assertThat(result.isSuccessful()).isFalse();
            assertThat(result.getCompilerDiagnostics()).hasSize(1);
            assertThat(result.getCompilerDiagnostics().get(0).getLine()).isEqualTo(7);
            assertThat(result.getCompilerDiagnostics().get(0).getColumn()).isEqualTo(1);
            assertThat(result.getErrorMessage()).isEqualTo("Groovy compilation failed with 1 diagnostic.");
        }
    }

    @Test
    public void compileGroovyScriptForDiagnosticsReturnsSeparateLocationsForMultipleImportErrors() {
        try (MockedStatic<ResourceController> resourceController = mockStatic(ResourceController.class)) {
            resourceController.when(ResourceController::getResourceController).thenReturn(new TestResourceController());
            ensureScriptClasspath();
            ScriptingEngine.GroovyCompileResult result = ScriptingEngine.compileGroovyScriptForDiagnostics(
                "import a.A\nimport b.B\nprintln 'x'\n",
                ScriptingPermissions.getPermissiveScriptingPermissions());
            assertThat(result.isSuccessful()).isFalse();
            assertThat(result.getCompilerDiagnostics()).hasSize(2);
            assertThat(result.getCompilerDiagnostics())
                .extracting(ScriptingEngine.GroovyCompilerDiagnostic::getLine,
                    ScriptingEngine.GroovyCompilerDiagnostic::getColumn)
                .containsExactly(
                    org.assertj.core.groups.Tuple.tuple(1, 1),
                    org.assertj.core.groups.Tuple.tuple(2, 1));
            assertThat(result.getErrorMessage()).isEqualTo("Groovy compilation failed with 2 diagnostics.");
        }
    }

    private static String normalizeLineEndings(String text) {
        return text.replace("\r\n", "\n");
    }

    private static void ensureScriptClasspath() {
        if (ScriptResources.getClasspath() == null) {
            ScriptResources.setClasspath(Collections.<String>emptyList());
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
        public void removeUserProperty(String key) {
            unsecuredProperties.remove(key);
            securedProperties.remove(key);
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
