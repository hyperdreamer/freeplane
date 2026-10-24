package org.freeplane.plugin.graph.layout.graphstream;

import static org.assertj.core.api.Assertions.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class InitialSpacingAcceptanceShould {
    @Test public void compareFrozenC0AndCandidateInSerialFreshJvms() throws Exception {
        assertThat(System.getProperty("java.awt.headless")).isEqualTo("true");
        Path repo = Paths.get("").toAbsolutePath();
        while (repo != null && !Files.exists(repo.resolve(".git"))) repo = repo.getParent();
        assertThat(repo).as("repository root").isNotNull();
        Set<String> classpath = new LinkedHashSet<String>();
        for (ClassLoader loader = getClass().getClassLoader(); loader != null; loader = loader.getParent()) {
            if (loader instanceof URLClassLoader) {
                for (URL url : ((URLClassLoader) loader).getURLs()) if (url.getProtocol().equals("file"))
                    classpath.add(Paths.get(url.toURI()).toString());
            }
        }
        classpath.addAll(Arrays.asList(System.getProperty("java.class.path").split(java.io.File.pathSeparator)));
        Path build = repo.resolve("freeplane_plugin_graph/build"); Files.createDirectories(build);
        Path output = Files.createTempDirectory(build, "initial-spacing-");
        Files.write(build.resolve("initial-spacing-current.txt"), (output.toString() + "\n").getBytes(StandardCharsets.UTF_8));
        Process process = new ProcessBuilder("python3", repo.resolve(
            "freeplane_plugin_graph/src/test/resources/initial-spacing/run-initial-spacing.py").toString(),
            "--repo", repo.toString(), "--classpath", String.join(java.io.File.pathSeparator, classpath),
            "--output", output.toString()).inheritIO().start();
        try {
            assertThat(process.waitFor(14L, TimeUnit.HOURS)).as("acceptance driver bounded completion; receipts %s", output).isTrue();
            assertThat(process.exitValue()).as("acceptance driver exit; receipts %s", output).isZero();
        }
        finally {
            if (process.isAlive()) process.destroyForcibly();
        }
        assertThat(Files.isRegularFile(output.resolve("manifest.json"))).isTrue();
        assertThat(Files.isRegularFile(output.resolve("comparison.json"))).isTrue();
        assertThat(Files.isRegularFile(output.resolve("acceptance.md"))).isTrue();
    }
}
