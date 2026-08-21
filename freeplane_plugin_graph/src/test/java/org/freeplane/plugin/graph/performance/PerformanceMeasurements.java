package org.freeplane.plugin.graph.performance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class PerformanceMeasurements {
    public static final long DIAGNOSTIC_ONLY_THRESHOLD = -1L;
    public static final long STRICT_FORCE_NANOS = 50_000_000L;
    public static final long STRICT_FULL_WORKER_NANOS = 100_000_000L;
    public static final long STRICT_FIRST_FRAME_P95_NANOS = 150_000_000L;
    public static final long STRICT_FIRST_FRAME_P99_NANOS = 300_000_000L;
    public static final long NORMAL_FIRST_FRAME_P99_NANOS = 1_500_000_000L;
    public static final long STRICT_EDT_SWAP_NANOS = 2_000_000L;
    public static final String CSV_HEADER =
        "scenario,stage,warmupCount,measuredCount,p50Nanos,p95Nanos,p99Nanos,maxNanos,"
            + "normalThresholdNanos,strictThresholdNanos,failureCount,discardCount,pass";

    public enum Stage {
        SNAPSHOT("snapshot"),
        PROJECTION("projection"),
        DIFF("diff"),
        MUTATION("mutation"),
        FORCE("force"),
        CORRECTION("correction"),
        HULL("hull"),
        LABEL("label"),
        FULL_WORKER("full-worker"),
        EDT_SWAP("edt-swap"),
        REPAINT("repaint"),
        ACCEPTED_BATCH_FIRST_FRAME("accepted-batch-first-frame");

        private final String wireName;

        Stage(final String name) {
            wireName = name;
        }

        public String wireName() {
            return wireName;
        }

        public String stageName() {
            return wireName;
        }

        @Override
        public String toString() {
            return wireName;
        }

        public static List<String> names() {
            final List<String> result = new ArrayList<String>();
            for (final Stage stage : values()) {
                result.add(stage.wireName);
            }
            return Collections.unmodifiableList(result);
        }

        public static Stage fromName(final String name) {
            final String value = Objects.requireNonNull(name, "name");
            for (final Stage stage : values()) {
                if (stage.wireName.equals(value)) {
                    return stage;
                }
            }
            throw new IllegalArgumentException("Unknown performance stage: " + value);
        }
    }

    private final String scenario;
    private final int expectedWarmupCount;
    private final int expectedMeasuredCount;
    private final EnumMap<Stage, Samples> samples = new EnumMap<Stage, Samples>(Stage.class);

    public PerformanceMeasurements(final String scenario, final int warmupCount, final int measuredCount) {
        this.scenario = requireScenario(scenario);
        if (warmupCount < 0 || measuredCount < 0) {
            throw new IllegalArgumentException("Sample counts must be nonnegative");
        }
        expectedWarmupCount = warmupCount;
        expectedMeasuredCount = measuredCount;
        for (final Stage stage : Stage.values()) {
            samples.put(stage, new Samples());
        }
    }

    public PerformanceMeasurements(final String scenario) {
        this(scenario, 0, 0);
    }

    public String scenario() {
        return scenario;
    }

    public int expectedWarmupCount() {
        return expectedWarmupCount;
    }

    public int expectedMeasuredCount() {
        return expectedMeasuredCount;
    }

    public int warmupTarget() {
        return expectedWarmupCount;
    }

    public int measuredTarget() {
        return expectedMeasuredCount;
    }

    public void recordWarmup(final Stage stage, final long durationNanos) {
        final Samples target = samples.get(Objects.requireNonNull(stage, "stage"));
        checkedDuration(durationNanos);
        if (target.warmups.size() >= expectedWarmupCount) {
            throw new IllegalStateException("Warm-up sample limit exceeded for " + stage);
        }
        target.warmups.add(Long.valueOf(durationNanos));
    }

    public void recordMeasured(final Stage stage, final long durationNanos) {
        final Samples target = samples.get(Objects.requireNonNull(stage, "stage"));
        checkedDuration(durationNanos);
        if (target.measured.size() >= expectedMeasuredCount) {
            throw new IllegalStateException("Measured sample limit exceeded for " + stage);
        }
        target.measured.add(Long.valueOf(durationNanos));
    }

    public void addWarmup(final Stage stage, final long durationNanos) {
        recordWarmup(stage, durationNanos);
    }

    public void addMeasured(final Stage stage, final long durationNanos) {
        recordMeasured(stage, durationNanos);
    }

    public void recordDuration(final Stage stage, final long startNanos, final long endNanos,
            final boolean warmup) {
        final long duration = checkedDuration(startNanos, endNanos);
        if (warmup) {
            recordWarmup(stage, duration);
        }
        else {
            recordMeasured(stage, duration);
        }
    }

    public void recordFailure(final Stage stage) {
        samples.get(Objects.requireNonNull(stage, "stage")).failureCount++;
    }

    public void recordFailure(final Stage stage, final int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Failure count must be nonnegative");
        }
        samples.get(Objects.requireNonNull(stage, "stage")).failureCount += count;
    }

    public void recordDiscard(final Stage stage) {
        samples.get(Objects.requireNonNull(stage, "stage")).discardCount++;
    }

    public void recordDiscard(final Stage stage, final int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Discard count must be nonnegative");
        }
        samples.get(Objects.requireNonNull(stage, "stage")).discardCount += count;
    }

    public int warmupCount(final Stage stage) {
        return samples.get(Objects.requireNonNull(stage, "stage")).warmups.size();
    }

    public int measuredCount(final Stage stage) {
        return samples.get(Objects.requireNonNull(stage, "stage")).measured.size();
    }

    public int failureCount(final Stage stage) {
        return samples.get(Objects.requireNonNull(stage, "stage")).failureCount;
    }

    public int discardCount(final Stage stage) {
        return samples.get(Objects.requireNonNull(stage, "stage")).discardCount;
    }

    public List<Long> warmupSamples(final Stage stage) {
        return immutableCopy(samples.get(Objects.requireNonNull(stage, "stage")).warmups);
    }

    public List<Long> measuredSamples(final Stage stage) {
        return immutableCopy(samples.get(Objects.requireNonNull(stage, "stage")).measured);
    }

    public Summary summary(final Stage stage) {
        final Stage value = Objects.requireNonNull(stage, "stage");
        return summary(value, normalThresholdNanos(scenario, value),
            strictThresholdNanos(scenario, value), false);
    }

    public Summary summary(final Stage stage, final long normalThresholdNanos,
            final long strictThresholdNanos, final boolean strictMode) {
        final Stage value = Objects.requireNonNull(stage, "stage");
        if (normalThresholdNanos < DIAGNOSTIC_ONLY_THRESHOLD
                || strictThresholdNanos < DIAGNOSTIC_ONLY_THRESHOLD) {
            throw new IllegalArgumentException("Thresholds must be -1 or nonnegative");
        }
        final Samples stored = samples.get(value);
        final List<Long> measured = immutableCopy(stored.measured);
        final List<Long> sortedMeasured = new ArrayList<Long>(measured);
        Collections.sort(sortedMeasured);
        final long p50 = measured.isEmpty() ? DIAGNOSTIC_ONLY_THRESHOLD
            : NearestRankPercentile.of(sortedMeasured, 0.50);
        final long p95 = measured.isEmpty() ? DIAGNOSTIC_ONLY_THRESHOLD
            : NearestRankPercentile.of(sortedMeasured, 0.95);
        final long p99 = measured.isEmpty() ? DIAGNOSTIC_ONLY_THRESHOLD
            : NearestRankPercentile.of(sortedMeasured, 0.99);
        long maximum = DIAGNOSTIC_ONLY_THRESHOLD;
        for (final Long valueNanos : measured) {
            maximum = Math.max(maximum, valueNanos.longValue());
        }

        boolean pass = stored.failureCount == 0 && stored.discardCount == 0
            && stored.warmups.size() == expectedWarmupCount
            && stored.measured.size() == expectedMeasuredCount && !measured.isEmpty();
        if (pass && normalThresholdNanos >= 0L) {
            pass = p95 <= normalThresholdNanos;
            if (pass && value == Stage.ACCEPTED_BATCH_FIRST_FRAME) {
                pass = p99 <= NORMAL_FIRST_FRAME_P99_NANOS;
            }
        }
        if (pass && strictMode && strictThresholdNanos >= 0L) {
            pass = p95 <= strictThresholdNanos;
            if (pass && value == Stage.ACCEPTED_BATCH_FIRST_FRAME) {
                pass = p99 <= STRICT_FIRST_FRAME_P99_NANOS;
            }
        }
        return new Summary(scenario, value, stored.warmups.size(), measured.size(), p50, p95, p99,
            maximum, normalThresholdNanos, strictThresholdNanos, stored.failureCount,
            stored.discardCount, pass);
    }

    public Map<Stage, Summary> summaries(final boolean strictMode) {
        final Map<Stage, Summary> result = new EnumMap<Stage, Summary>(Stage.class);
        for (final Stage stage : Stage.values()) {
            result.put(stage, summary(stage, normalThresholdNanos(scenario, stage),
                strictThresholdNanos(scenario, stage), strictMode));
        }
        return Collections.unmodifiableMap(result);
    }

    public String toCsv() {
        return toCsv(false);
    }

    public String toCsv(final boolean strictMode) {
        final StringBuilder output = new StringBuilder();
        output.append(CSV_HEADER).append('\n');
        for (final Stage stage : Stage.values()) {
            output.append(summaries(strictMode).get(stage).csvRow()).append('\n');
        }
        return output.toString();
    }

    public void writeCsv(final Path location, final boolean strictMode) throws IOException {
        Objects.requireNonNull(location, "location");
        Files.write(location, toCsv(strictMode).getBytes(StandardCharsets.UTF_8));
    }

    public static long checkedDuration(final long startNanos, final long endNanos) {
        final long duration;
        try {
            duration = Math.subtractExact(endNanos, startNanos);
        }
        catch (final ArithmeticException exception) {
            throw new IllegalArgumentException("Nanosecond duration overflow", exception);
        }
        return checkedDuration(duration);
    }

    public static long checkedDuration(final long durationNanos) {
        if (durationNanos < 0L) {
            throw new IllegalArgumentException("Nanosecond duration must be nonnegative");
        }
        return durationNanos;
    }

    public static long normalThresholdNanos(final String scenario, final Stage stage) {
        requireScenario(scenario);
        final Stage value = Objects.requireNonNull(stage, "stage");
        switch (value) {
        case FORCE:
            return 250_000_000L;
        case FULL_WORKER:
            return 500_000_000L;
        case ACCEPTED_BATCH_FIRST_FRAME:
            return 750_000_000L;
        case EDT_SWAP:
            return 10_000_000L;
        default:
            return DIAGNOSTIC_ONLY_THRESHOLD;
        }
    }

    public static long strictThresholdNanos(final String scenario, final Stage stage) {
        final String name = requireScenario(scenario);
        final Stage value = Objects.requireNonNull(stage, "stage");
        if (!"reference-2000-5000".equals(name)) {
            return DIAGNOSTIC_ONLY_THRESHOLD;
        }
        switch (value) {
        case FORCE:
            return STRICT_FORCE_NANOS;
        case FULL_WORKER:
            return STRICT_FULL_WORKER_NANOS;
        case ACCEPTED_BATCH_FIRST_FRAME:
            return STRICT_FIRST_FRAME_P95_NANOS;
        case EDT_SWAP:
            return STRICT_EDT_SWAP_NANOS;
        default:
            return DIAGNOSTIC_ONLY_THRESHOLD;
        }
    }

    public static long strictP99ThresholdNanos(final String scenario, final Stage stage) {
        return "reference-2000-5000".equals(requireScenario(scenario))
                && stage == Stage.ACCEPTED_BATCH_FIRST_FRAME
            ? STRICT_FIRST_FRAME_P99_NANOS : DIAGNOSTIC_ONLY_THRESHOLD;
    }

    private static String requireScenario(final String value) {
        Objects.requireNonNull(value, "scenario");
        if (value.isEmpty() || value.indexOf(',') >= 0 || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Scenario must be a nonempty CSV-safe name");
        }
        return value;
    }

    private static List<Long> immutableCopy(final List<Long> values) {
        return Collections.unmodifiableList(new ArrayList<Long>(values));
    }

    private static final class Samples {
        private final List<Long> warmups = new ArrayList<Long>();
        private final List<Long> measured = new ArrayList<Long>();
        private int failureCount;
        private int discardCount;
    }

    public static final class Summary {
        private final String scenario;
        private final Stage stage;
        private final int warmupCount;
        private final int measuredCount;
        private final long p50Nanos;
        private final long p95Nanos;
        private final long p99Nanos;
        private final long maxNanos;
        private final long normalThresholdNanos;
        private final long strictThresholdNanos;
        private final int failureCount;
        private final int discardCount;
        private final boolean pass;

        private Summary(final String scenario, final Stage stage, final int warmupCount,
                final int measuredCount, final long p50Nanos, final long p95Nanos, final long p99Nanos,
                final long maxNanos, final long normalThresholdNanos, final long strictThresholdNanos,
                final int failureCount, final int discardCount, final boolean pass) {
            this.scenario = scenario;
            this.stage = stage;
            this.warmupCount = warmupCount;
            this.measuredCount = measuredCount;
            this.p50Nanos = p50Nanos;
            this.p95Nanos = p95Nanos;
            this.p99Nanos = p99Nanos;
            this.maxNanos = maxNanos;
            this.normalThresholdNanos = normalThresholdNanos;
            this.strictThresholdNanos = strictThresholdNanos;
            this.failureCount = failureCount;
            this.discardCount = discardCount;
            this.pass = pass;
        }

        public String scenario() { return scenario; }
        public Stage stage() { return stage; }
        public String stageName() { return stage.wireName(); }
        public int warmupCount() { return warmupCount; }
        public int measuredCount() { return measuredCount; }
        public long p50Nanos() { return p50Nanos; }
        public long p95Nanos() { return p95Nanos; }
        public long p99Nanos() { return p99Nanos; }
        public long maxNanos() { return maxNanos; }
        public long maximumNanos() { return maxNanos; }
        public long normalThresholdNanos() { return normalThresholdNanos; }
        public long strictThresholdNanos() { return strictThresholdNanos; }
        public int failureCount() { return failureCount; }
        public int discardCount() { return discardCount; }
        public boolean pass() { return pass; }
        public boolean passed() { return pass; }

        public String csvRow() {
            return scenario + "," + stage.wireName() + "," + warmupCount + "," + measuredCount + ","
                + p50Nanos + "," + p95Nanos + "," + p99Nanos + "," + maxNanos + ","
                + normalThresholdNanos + "," + strictThresholdNanos + "," + failureCount + ","
                + discardCount + "," + Boolean.toString(pass);
        }

        @Override
        public String toString() {
            return csvRow();
        }
    }
}
