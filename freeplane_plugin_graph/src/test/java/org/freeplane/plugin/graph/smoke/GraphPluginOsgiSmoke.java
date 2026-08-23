package org.freeplane.plugin.graph.smoke;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.knopflerfish.framework.Main;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.launch.Framework;

/**
 * Runs the graph bundle against the same Knopflerfish installation used by Freeplane.
 */
public final class GraphPluginOsgiSmoke {
    private static final String GRAPH_SYMBOLIC_NAME = "org.freeplane.plugin.graph";
    private static final String CORE_SYMBOLIC_NAME = "org.freeplane.core";
    private static final long ACTIVE_TIMEOUT_MILLIS = 30_000L;
    private static final long STOP_TIMEOUT_MILLIS = 15_000L;

    private GraphPluginOsgiSmoke() {
    }

    public static void main(final String[] arguments) throws Exception {
        final Path root = root(arguments);
        final Path bin = root.resolve("BIN").toAbsolutePath().normalize();
        final Path frameworkJar = requireFile(bin.resolve("framework.jar"), "framework.jar");
        final Path props = requireFile(bin.resolve("props.xargs"), "props.xargs");
        final Path coreBundlePath = requireBundleDirectory(bin.resolve("core").resolve(CORE_SYMBOLIC_NAME),
            CORE_SYMBOLIC_NAME);
        final Path graphBundlePath = requireBundleDirectory(bin.resolve("plugins").resolve(GRAPH_SYMBOLIC_NAME),
            GRAPH_SYMBOLIC_NAME);

        assertFrameworkJarIsUsed(frameworkJar);
        final Path storage = Files.createTempDirectory("freeplane-graph-osgi-smoke-");
        Framework framework = null;
        Bundle graphBundle = null;
        boolean frameworkStopped = false;
        Throwable failure = null;
        try {
            System.setProperty("java.awt.headless", "true");
            System.setProperty("org.knopflerfish.framework.readonly", "false");
            System.setProperty("org.osgi.framework.storage", storage.resolve("fwdir").toString());
            System.setProperty("org.knopflerfish.gosg.jars",
                "reference:file:" + coreBundlePath.toAbsolutePath().normalize());
            System.setProperty("org.freeplane.basedirectory", bin.toString());
            System.setProperty("org.freeplane.globalresourcedir", bin.resolve("resources").toString());
            System.setProperty("org.freeplane.user.dir", storage.resolve("user").toString());
            Files.createDirectories(storage.resolve("user"));

            final Main main = new Main();
            framework = main.start(new String[] { "-xargs", props.toString(),
                "-Forg.osgi.framework.storage=" + storage.resolve("fwdir").toString(), "-bg" });
            final BundleContext context = framework.getBundleContext();
            installIfAbsent(context, CORE_SYMBOLIC_NAME, coreBundlePath);
            graphBundle = installIfAbsent(context, GRAPH_SYMBOLIC_NAME, graphBundlePath);
            if (graphBundle.getState() != Bundle.ACTIVE) {
                graphBundle.start(Bundle.START_TRANSIENT);
            }
            awaitActive(graphBundle);
            assertGraphBundleContents(graphBundle);
            assertGraphOperation(graphBundle);
            System.out.println("Graph OSGi smoke: ACTIVE, three dependency jars/classes, and graph operation passed");
        }
        catch (final Throwable exception) {
            failure = exception;
        }
        finally {
            try {
                if (graphBundle != null) {
                    stopBundle(graphBundle);
                }
            }
            catch (final Throwable exception) {
                failure = combine(failure, exception);
            }
            try {
                if (framework != null) {
                    framework.stop();
                    final FrameworkEvent event = framework.waitForStop(STOP_TIMEOUT_MILLIS);
                    if (event.getType() != FrameworkEvent.STOPPED) {
                        throw new AssertionError("The OSGi framework stopped with event type " + event.getType());
                    }
                    frameworkStopped = true;
                }
            }
            catch (final InterruptedException exception) {
                Thread.currentThread().interrupt();
                failure = combine(failure, exception);
            }
            catch (final Throwable exception) {
                failure = combine(failure, exception);
            }
            try {
                if (!frameworkStopped) {
                    throw new AssertionError("The OSGi framework did not stop");
                }
                awaitPluginThreadsEnded();
            }
            catch (final Throwable exception) {
                failure = combine(failure, exception);
            }
            try {
                deleteRecursively(storage);
            }
            catch (final Throwable exception) {
                failure = combine(failure, exception);
            }
        }
        rethrow(failure);
    }

    private static Throwable combine(final Throwable current, final Throwable additional) {
        if (current == null) {
            return additional;
        }
        current.addSuppressed(additional);
        return current;
    }

    private static void rethrow(final Throwable failure) throws Exception {
        if (failure == null) {
            return;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        if (failure instanceof Exception) {
            throw (Exception) failure;
        }
        throw new IllegalStateException("OSGi smoke failed", failure);
    }

    private static Path root(final String[] arguments) {
        if (arguments.length > 0 && arguments[0] != null && !arguments[0].trim().isEmpty()) {
            return Paths.get(arguments[0]).toAbsolutePath().normalize();
        }
        return Paths.get("").toAbsolutePath().normalize();
    }

    private static Path requireFile(final Path path, final String description) {
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Missing actual " + description + ": " + path);
        }
        return path;
    }

    private static Path requireBundleDirectory(final Path path, final String symbolicName) {
        if (!Files.isDirectory(path)) {
            throw new IllegalStateException("Missing actual bundle " + symbolicName + ": " + path);
        }
        requireFile(path.resolve("META-INF").resolve("MANIFEST.MF"), symbolicName + " manifest");
        return path;
    }

    private static void assertFrameworkJarIsUsed(final Path frameworkJar) throws Exception {
        final URL source = Main.class.getProtectionDomain().getCodeSource().getLocation();
        final Path loaded = Paths.get(source.toURI()).toAbsolutePath().normalize();
        if (!Files.isSameFile(loaded, frameworkJar)) {
            throw new AssertionError("The OSGi probe did not load BIN/framework.jar: " + loaded);
        }
    }

    private static Bundle installIfAbsent(final BundleContext context, final String symbolicName,
            final Path bundlePath) throws BundleException {
        final Bundle existing = context.getBundle(symbolicName);
        if (existing != null) {
            return existing;
        }
        return context.installBundle(location(bundlePath));
    }

    private static String location(final Path path) {
        return "reference:" + path.toAbsolutePath().normalize().toUri().toString();
    }

    private static void awaitActive(final Bundle bundle) throws InterruptedException {
        final long deadline = System.currentTimeMillis() + ACTIVE_TIMEOUT_MILLIS;
        while (bundle.getState() != Bundle.ACTIVE && System.currentTimeMillis() < deadline) {
            Thread.sleep(50L);
        }
        if (bundle.getState() != Bundle.ACTIVE) {
            throw new AssertionError("Graph bundle did not become ACTIVE; state=" + bundle.getState());
        }
    }

    private static void assertGraphBundleContents(final Bundle bundle) throws Exception {
        final String[] entries = new String[] {
            "lib/gs-core-1.3.jar",
            "lib/pherd-1.0.jar",
            "lib/mbox2-1.0.jar"
        };
        for (final String entry : entries) {
            if (bundle.getEntry(entry) == null) {
                throw new AssertionError("Graph bundle is missing " + entry);
            }
        }

        final String[] classes = new String[] {
            "org.graphstream.graph.Graph",
            "org.graphstream.graph.implementations.SingleGraph",
            "org.graphstream.stream.file.FileSourceDGS",
            "org.graphstream.ui.layout.springbox.implementations.SpringBox",
            "org.graphstream.ui.layout.springbox.implementations.LinLog",
            "org.graphstream.ui.graphicGraph.GraphicGraph",
            "org.miv.pherd.Particle",
            "org.miv.pherd.ntree.NTree",
            "org.miv.mbox.net.Receiver",
            "org.graphstream.stream.net.HTTPSource"
        };
        for (final String className : classes) {
            bundle.loadClass(className);
        }
    }

    private static void assertGraphOperation(final Bundle bundle) throws Exception {
        final Class<?> singleGraph = bundle.loadClass("org.graphstream.graph.implementations.SingleGraph");
        final Object graph = singleGraph.getConstructor(String.class).newInstance("graph-osgi-smoke");
        singleGraph.getMethod("addNode", String.class).invoke(graph, "one");
        singleGraph.getMethod("addNode", String.class).invoke(graph, "two");
        singleGraph.getMethod("addEdge", String.class, String.class, String.class)
            .invoke(graph, "edge", "one", "two");
        final int nodeCount = ((Number) singleGraph.getMethod("getNodeCount").invoke(graph)).intValue();
        final int edgeCount = ((Number) singleGraph.getMethod("getEdgeCount").invoke(graph)).intValue();
        if (nodeCount != 2 || edgeCount != 1) {
            throw new AssertionError("Graph operation produced nodes=" + nodeCount + ", edges=" + edgeCount);
        }

        final Class<?> springBox = bundle.loadClass(
            "org.graphstream.ui.layout.springbox.implementations.SpringBox");
        final Object layout = springBox.getConstructor().newInstance();
        springBox.getMethod("setQuality", double.class).invoke(layout, Double.valueOf(0.10));
        final String algorithmName = (String) springBox.getMethod("getLayoutAlgorithmName").invoke(layout);
        if (!"SpringBox".equals(algorithmName)) {
            throw new AssertionError("Unexpected GraphStream layout algorithm: " + algorithmName);
        }
    }

    private static void stopBundle(final Bundle bundle) throws BundleException {
        if (bundle.getState() == Bundle.ACTIVE || bundle.getState() == Bundle.STARTING) {
            bundle.stop(Bundle.STOP_TRANSIENT);
        }
    }

    private static void awaitPluginThreadsEnded() throws InterruptedException {
        final long deadline = System.currentTimeMillis() + 5_000L;
        List<String> names;
        do {
            names = pluginThreadNames();
            if (names.isEmpty()) {
                return;
            }
            Thread.sleep(50L);
        }
        while (System.currentTimeMillis() < deadline);
        throw new AssertionError("Plugin-owned graph workers/timers remain after framework stop: " + names);
    }

    private static List<String> pluginThreadNames() {
        final Set<Thread> threads = new HashSet<Thread>(Thread.getAllStackTraces().keySet());
        final List<String> names = new ArrayList<String>();
        for (final Thread thread : threads) {
            if (thread.isAlive() && thread.getName().startsWith("freeplane-graph-")) {
                names.add(thread.getName());
            }
        }
        return names;
    }

    private static void deleteRecursively(final Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        final List<Path> paths = new ArrayList<Path>();
        Files.walk(root).forEach(paths::add);
        for (int index = paths.size() - 1; index >= 0; index--) {
            Files.deleteIfExists(paths.get(index));
        }
    }
}
