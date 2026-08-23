package org.freeplane.plugin.graph.smoke;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Starts the real Freeplane launcher in a disposable user directory and verifies normal shutdown.
 */
public final class FreeplaneLaunchSmoke {
    private static final long ACTIVE_LOG_TIMEOUT_SECONDS = 180L;
    private static final long NORMAL_SHUTDOWN_TIMEOUT_SECONDS = 15L;
    private static final long TERM_SHUTDOWN_TIMEOUT_SECONDS = 5L;

    private FreeplaneLaunchSmoke() {
    }

    public static void main(final String[] arguments) throws Exception {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("Expected repository root and result path");
        }
        final Path root = Paths.get(arguments[0]).toAbsolutePath().normalize();
        final Path resultPath = Paths.get(arguments[1]).toAbsolutePath().normalize();
        final Path launcher = root.resolve("BIN/freeplane.sh");
        if (!Files.isRegularFile(launcher) || !Files.isExecutable(launcher)) {
            throw new IllegalStateException("Missing executable BIN/freeplane.sh: " + launcher);
        }

        final Path isolatedRoot = Files.createTempDirectory("freeplane-graph-launch-smoke-");
        final Path home = isolatedRoot.resolve("home");
        final Path config = isolatedRoot.resolve("config");
        Files.createDirectories(home);
        Files.createDirectories(config);
        writeLaunchProperties(config);
        final List<String> output = new ArrayList<String>();
        final AtomicBoolean graphBundleActive = new AtomicBoolean(false);
        final AtomicReference<Throwable> readerFailure = new AtomicReference<Throwable>();
        final String launchMarker = "graph-launch-smoke-" + isolatedRoot.getFileName().toString();
        final Process process = start(launcher, home, config, launchMarker);
        final Thread reader = readOutput(process.getInputStream(), output, graphBundleActive, readerFailure);
        reader.start();

        boolean termRequired = false;
        int exitCode = Integer.MIN_VALUE;
        try {
            awaitGraphBundleActive(process, graphBundleActive, output, readerFailure);
            if (!awaitProcessLifecycleTermination(process, launchMarker, NORMAL_SHUTDOWN_TIMEOUT_SECONDS)) {
                termRequired = true;
                process.destroy();
                terminateLaunchProcesses(launchMarker);
                if (!awaitProcessLifecycleTermination(process, launchMarker, TERM_SHUTDOWN_TIMEOUT_SECONDS)) {
                    process.destroyForcibly();
                    throw new AssertionError("Freeplane remained alive after normal shutdown and TERM\n"
                        + join(output));
                }
            }
            reader.join(2_000L);
            final Throwable failure = readerFailure.get();
            if (failure != null) {
                throw new IllegalStateException("Unable to read Freeplane launch output", failure);
            }
            exitCode = process.exitValue();
            final boolean normalQuitRequested = containsOutput(output, "(QuitAction)");
            if (!normalQuitRequested) {
                throw new AssertionError("Production QuitAction was not observed in the child output\n"
                    + join(output));
            }
            if (exitCode != 0) {
                throw new AssertionError("Freeplane launch smoke exited with " + exitCode + "\n"
                    + join(output));
            }
            final List<String> remainingProcesses = runningLaunchProcesses(launchMarker);
            if (!remainingProcesses.isEmpty()) {
                throw new AssertionError("Freeplane or plugin-owned graph process remains: "
                    + remainingProcesses);
            }
            writeResult(resultPath, graphBundleActive.get(), normalQuitRequested, termRequired,
                exitCode, remainingProcesses.isEmpty(), output);
            System.out.println("Freeplane launch smoke: graph bundle ACTIVE, production QuitAction, exit=" + exitCode
                + ", TERM required=" + termRequired + ", child process table clear=true");
        }
        finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(TERM_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
            terminateOrphans(launchMarker);
            reader.join(2_000L);
            deleteRecursively(isolatedRoot);
        }
    }

    private static void writeLaunchProperties(final Path config) throws IOException {
        final Path userDirectory = config.resolve("freeplane").resolve("1.12.x");
        Files.createDirectories(userDirectory);
        final String properties = "create_new_map_if_no_maps_are_loaded=false\n"
            + "always_load_last_maps=false\n"
            + "load_last_maps=false\n"
            + "load_last_map=false\n";
        Files.write(userDirectory.resolve("auto.properties"),
            properties.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static Process start(final Path launcher, final Path home, final Path config,
            final String launchMarker) throws IOException {
        final ProcessBuilder builder = new ProcessBuilder(launcher.toString(), "-XQuitAction");
        builder.directory(launcher.getParent().toFile());
        builder.redirectErrorStream(true);
        builder.environment().put("HOME", home.toString());
        builder.environment().put("XDG_CONFIG_HOME", config.toString());
        builder.environment().put("JAVA_HOME", "/home/henry/.sdkman/candidates/java/21.0.8-zulu");
        builder.environment().put("JAVA_OPTS",
            "-Dorg.freeplane.nosplash=true -Dgraph.launch.smoke.marker=" + launchMarker);
        return builder.start();
    }

    private static boolean awaitProcessLifecycleTermination(final Process process, final String launchMarker,
            final long timeoutSeconds) throws IOException, InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (System.nanoTime() < deadline) {
            if (!process.isAlive() && runningLaunchProcesses(launchMarker).isEmpty()) {
                return true;
            }
            Thread.sleep(100L);
        }
        return !process.isAlive() && runningLaunchProcesses(launchMarker).isEmpty();
    }

    private static List<String> runningLaunchProcesses(final String launchMarker) throws IOException, InterruptedException {
        final Process process = new ProcessBuilder("ps", "-eo", "pid=,args=")
            .redirectErrorStream(true).start();
        final List<String> matches = new ArrayList<String>();
        try (BufferedReader lines = new BufferedReader(new InputStreamReader(process.getInputStream(),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = lines.readLine()) != null) {
                if (line.contains(launchMarker)) {
                    matches.add(line.trim());
                }
            }
        }
        if (!process.waitFor(2L, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Unable to inspect launch-process state");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("Process-table inspection failed with " + process.exitValue());
        }
        return matches;
    }

    private static void terminateLaunchProcesses(final String launchMarker)
            throws IOException, InterruptedException {
        for (final String process : runningLaunchProcesses(launchMarker)) {
            final String pid = firstToken(process);
            if (pid == null) {
                continue;
            }
            final Process terminator = new ProcessBuilder("kill", "-TERM", pid).start();
            if (!terminator.waitFor(2L, TimeUnit.SECONDS)) {
                terminator.destroyForcibly();
            }
        }
    }

    private static void terminateOrphans(final String launchMarker) throws Exception {
        List<String> processes = runningLaunchProcesses(launchMarker);
        for (final String process : processes) {
            final String pid = firstToken(process);
            if (pid != null) {
                final Process terminator = new ProcessBuilder("kill", "-TERM", pid).start();
                terminator.waitFor(2L, TimeUnit.SECONDS);
            }
        }
        if (!processes.isEmpty()) {
            Thread.sleep(250L);
            processes = runningLaunchProcesses(launchMarker);
        }
        for (final String process : processes) {
            final String pid = firstToken(process);
            if (pid != null) {
                final Process terminator = new ProcessBuilder("kill", "-KILL", pid).start();
                terminator.waitFor(2L, TimeUnit.SECONDS);
            }
        }
    }

    private static String firstToken(final String value) {
        final String trimmed = value.trim();
        final int separator = trimmed.indexOf(' ');
        return separator < 0 ? trimmed : trimmed.substring(0, separator);
    }

    private static Thread readOutput(final InputStream stream, final List<String> output,
            final AtomicBoolean graphBundleActive, final AtomicReference<Throwable> readerFailure) {
        final Thread reader = new Thread(new Runnable() {
            @Override
            public void run() {
                try (BufferedReader lines = new BufferedReader(new InputStreamReader(stream,
                        StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = lines.readLine()) != null) {
                        synchronized (output) {
                            output.add(line);
                        }
                        if (line.contains("org.freeplane.plugin.graph")
                                && (line.contains("Started:") || line.contains("ACTIVE"))) {
                            graphBundleActive.set(true);
                        }
                    }
                }
                catch (final Throwable exception) {
                    readerFailure.set(exception);
                }
            }
        }, "graph-launch-smoke-output");
        reader.setDaemon(true);
        return reader;
    }

    private static void awaitGraphBundleActive(final Process process, final AtomicBoolean active,
            final List<String> output, final AtomicReference<Throwable> readerFailure) throws Exception {
        final long deadline = System.nanoTime()
            + TimeUnit.SECONDS.toNanos(ACTIVE_LOG_TIMEOUT_SECONDS);
        while (!active.get() && process.isAlive() && System.nanoTime() < deadline) {
            final Throwable failure = readerFailure.get();
            if (failure != null) {
                throw new IllegalStateException("Unable to read Freeplane launch output", failure);
            }
            Thread.sleep(100L);
        }
        if (!active.get()) {
            throw new AssertionError("Freeplane did not report the graph bundle ACTIVE\n" + join(output));
        }
    }

    private static boolean containsOutput(final List<String> output, final String fragment) {
        synchronized (output) {
            for (final String line : output) {
                if (line.contains(fragment)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void writeResult(final Path path, final boolean active, final boolean normalQuitRequested,
            final boolean termRequired, final int exitCode, final boolean childProcessTerminated,
            final List<String> output) throws IOException {
        final Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        final StringBuilder result = new StringBuilder();
        result.append("graphBundleActive=").append(active).append('\n');
        result.append("normalQuitRequested=").append(normalQuitRequested).append('\n');
        result.append("termRequired=").append(termRequired).append('\n');
        result.append("exitCode=").append(exitCode).append('\n');
        result.append("childProcessTerminated=").append(childProcessTerminated).append('\n');
        result.append("normalShutdownTimeoutSeconds=").append(NORMAL_SHUTDOWN_TIMEOUT_SECONDS).append('\n');
        result.append("termShutdownTimeoutSeconds=").append(TERM_SHUTDOWN_TIMEOUT_SECONDS).append('\n');
        result.append("outputLineCount=").append(output.size()).append('\n');
        Files.write(path, result.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String join(final List<String> output) {
        final StringBuilder result = new StringBuilder();
        synchronized (output) {
            for (final String line : output) {
                result.append(line).append('\n');
            }
        }
        return result.toString();
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
