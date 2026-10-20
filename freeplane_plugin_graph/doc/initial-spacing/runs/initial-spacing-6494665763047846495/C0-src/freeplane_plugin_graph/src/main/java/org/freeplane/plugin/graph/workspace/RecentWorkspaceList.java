package org.freeplane.plugin.graph.workspace;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.freeplane.api.TextWritingDirection;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.ConfigurationUtils;

public final class RecentWorkspaceList {
    public static final String PROPERTY_KEY = "graph_workspace_recent_workspaces";
    public static final int STORED_CAPACITY = 25;
    public static final int DISPLAY_CAPACITY = 8;

    public static final class Entry {
        private final Path path;
        private final String label;

        private Entry(final Path path) {
            this(path, labelFor(path));
        }

        /** Test factory: lets tests build expected values and use the path-based equality. */
        static Entry of(final Path path, final String label) {
            return new Entry(path, label);
        }

        private Entry(final Path path, final String label) {
            this.path = Objects.requireNonNull(path, "path");
            this.label = Objects.requireNonNull(label, "label");
        }

        public Path path() {
            return path;
        }

        public String label() {
            return label;
        }

        @Override
        public boolean equals(final Object other) {
            return this == other || other instanceof Entry && path.equals(((Entry) other).path);
        }

        @Override
        public int hashCode() {
            return path.hashCode();
        }

        @Override
        public String toString() {
            return "Entry[" + path + "]";
        }
    }

    private final Object monitor = new Object();
    private final Object persisterMonitor = new Object();
    private final boolean mutable;
    private final Consumer<String> persister;
    private final WorkspaceUriResolver uriResolver = new WorkspaceUriResolver();
    private List<Path> entries;
    private long revision;
    private long persistedRevision = -1L;

    public static RecentWorkspaceList standard() {
        ResourceController resources = null;
        try {
            resources = ResourceController.getResourceController();
        }
        catch (final NullPointerException ignored) {
        }
        final String stored = resources == null ? "" : resources.getProperty(PROPERTY_KEY, "");
        return new RecentWorkspaceList(stored, value -> {
            ResourceController current = null;
            try {
                current = ResourceController.getResourceController();
            }
            catch (final NullPointerException ignored) {
            }
            if (current != null) {
                current.setProperty(PROPERTY_KEY, value);
            }
        });
    }

    public static RecentWorkspaceList empty() {
        return new RecentWorkspaceList(Collections.<Path>emptyList(), value -> { }, false);
    }

    public RecentWorkspaceList(final String storedValue, final Consumer<String> persister) {
        this(decode(storedValue), Objects.requireNonNull(persister, "persister"), true);
    }

    private RecentWorkspaceList(final List<Path> initialEntries, final Consumer<String> persister,
            final boolean mutable) {
        this.entries = Collections.unmodifiableList(new ArrayList<Path>(initialEntries));
        this.persister = persister;
        this.mutable = mutable;
    }

    public void record(final Path workspaceFile) {
        Objects.requireNonNull(workspaceFile, "workspaceFile");
        if (!mutable) {
            return;
        }
        final Path canonical;
        try {
            canonical = uriResolver.canonical(workspaceFile);
        }
        catch (RuntimeException failure) {
            return;
        }
        final long snapshotRevision;
        final String encoded;
        synchronized (monitor) {
            final String canonicalText = canonical.toString();
            final List<Path> updated = new ArrayList<Path>(STORED_CAPACITY);
            updated.add(canonical);
            for (final Path existing : entries) {
                if (updated.size() == STORED_CAPACITY) {
                    break;
                }
                if (!existing.toString().equals(canonicalText)) {
                    updated.add(existing);
                }
            }
            entries = Collections.unmodifiableList(updated);
            revision++;
            snapshotRevision = revision;
            encoded = ConfigurationUtils.encodeListValue(texts(entries), true);
        }
        persist(snapshotRevision, encoded);
    }

    public void clear() {
        if (!mutable) {
            return;
        }
        final long snapshotRevision;
        synchronized (monitor) {
            entries = Collections.emptyList();
            revision++;
            snapshotRevision = revision;
        }
        persist(snapshotRevision, ConfigurationUtils.encodeListValue(Collections.<String>emptyList(), true));
    }

    public List<Entry> displayEntries() {
        synchronized (monitor) {
            final List<Entry> result = new ArrayList<Entry>(DISPLAY_CAPACITY);
            for (final Path path : entries) {
                if (result.size() == DISPLAY_CAPACITY) {
                    break;
                }
                if (isRegularFile(path)) {
                    result.add(new Entry(path));
                }
            }
            return Collections.unmodifiableList(result);
        }
    }

    public boolean hasStoredEntries() {
        synchronized (monitor) {
            return !entries.isEmpty();
        }
    }

    public Optional<Path> mostRecentExisting() {
        synchronized (monitor) {
            for (final Path path : entries) {
                if (isRegularFile(path)) {
                    return Optional.of(path);
                }
            }
            return Optional.empty();
        }
    }

    static String labelFor(final Path path) {
        Objects.requireNonNull(path, "path");
        final Path fileName = path.getFileName();
        final Path parent = path.getParent();
        final String raw;
        if (fileName == null) {
            raw = path.toString();
        }
        else if (parent == null) {
            raw = fileName.toString();
        }
        else {
            raw = fileName + " (" + parent + ")";
        }
        return TextWritingDirection.LEFT_TO_RIGHT.isolatePathSeparators(raw);
    }

    private static List<Path> decode(final String storedValue) {
        if (storedValue == null || storedValue.isEmpty()) {
            return Collections.emptyList();
        }
        final List<String> tokens;
        try {
            tokens = ConfigurationUtils.decodeListValue(storedValue, true);
        }
        catch (RuntimeException failure) {
            return Collections.emptyList();
        }
        final List<Path> decoded = new ArrayList<Path>(Math.min(tokens.size(), STORED_CAPACITY));
        for (final String token : tokens) {
            if (decoded.size() == STORED_CAPACITY) {
                break;
            }
            try {
                final Path candidate = Paths.get(token);
                if (candidate.isAbsolute()) {
                    decoded.add(candidate);
                }
            }
            catch (RuntimeException invalid) {
                // The token can never become a valid workspace path; drop it.
            }
        }
        return decoded;
    }

    private void persist(final long snapshotRevision, final String value) {
        synchronized (persisterMonitor) {
            if (snapshotRevision <= persistedRevision) {
                return;
            }
            persistedRevision = snapshotRevision;
            try {
                persister.accept(value);
            }
            catch (RuntimeException ignored) {
                // Recording is best effort.
            }
        }
    }

    private static boolean isRegularFile(final Path path) {
        try {
            return Files.isRegularFile(path);
        }
        catch (RuntimeException failure) {
            return false;
        }
    }

    private static List<String> texts(final List<Path> paths) {
        final List<String> result = new ArrayList<String>(paths.size());
        for (final Path path : paths) {
            result.add(path.toString());
        }
        return result;
    }
}
