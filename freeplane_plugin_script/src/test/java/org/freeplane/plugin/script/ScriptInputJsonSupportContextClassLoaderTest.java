package org.freeplane.plugin.script;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

import org.junit.Test;

public class ScriptInputJsonSupportContextClassLoaderTest {

    @Test
    public void parsesValidJsonWhenContextClassLoaderCannotSeeGroovyServices() {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(new ClassLoader(null) {
            @Override
            public URL getResource(String name) {
                return null;
            }

            @Override
            public Enumeration<URL> getResources(String name) throws IOException {
                return Collections.emptyEnumeration();
            }
        });
        try {
            ScriptInputJsonSupport.ParseResult result = ScriptInputJsonSupport.parseInputText(
                "{\"a\":1}",
                "context-class-loader-regression-test");

            assertThat(result.isSuccessful()).isTrue();
            assertThat(result.getArgsValue()).isInstanceOf(Map.class);
            assertThat(((Map<?, ?>) result.getArgsValue()).get("a")).isEqualTo(1);
        }
        finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }
}
