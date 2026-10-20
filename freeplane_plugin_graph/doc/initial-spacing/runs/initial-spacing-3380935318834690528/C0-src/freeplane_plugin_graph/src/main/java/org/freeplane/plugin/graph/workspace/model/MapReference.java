package org.freeplane.plugin.graph.workspace.model;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class MapReference {
    private static final Set<String> APPROVED_COLORS = Collections.unmodifiableSet(
        new HashSet<String>(Arrays.asList(
            "#4E79A7", "#F28E2B", "#59A14F", "#E15759",
            "#76B7B2", "#B07AA1", "#EDC948", "#9C755F")));

    private final MapReferenceId id;
    private final long sequence;
    private final URI storedUri;
    private final boolean active;
    private final String color;
    private final List<UnknownXml> unknownXml;

    private MapReference(final MapReferenceId id, final long sequence, final URI storedUri,
            final boolean active, final String color, final List<UnknownXml> unknownXml) {
        this.id = Objects.requireNonNull(id, "id");
        if (sequence <= 0) {
            throw new IllegalArgumentException("Map sequence must be positive");
        }
        this.sequence = sequence;
        this.storedUri = validateStoredUri(storedUri);
        this.active = active;
        this.color = validateColor(color);
        this.unknownXml = UnknownXml.forRecord(unknownXml);
    }

    public static MapReference of(final MapReferenceId id, final long sequence, final URI storedUri,
            final boolean active, final String color, final List<UnknownXml> unknownXml) {
        return new MapReference(id, sequence, storedUri, active, color, unknownXml);
    }

    public MapReferenceId id() {
        return id;
    }

    public long sequence() {
        return sequence;
    }

    public URI storedUri() {
        return storedUri;
    }

    public boolean active() {
        return active;
    }

    public String color() {
        return color;
    }

    public List<UnknownXml> unknownXml() {
        return unknownXml;
    }

    private static URI validateStoredUri(final URI uri) {
        Objects.requireNonNull(uri, "storedUri");
        if (uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Stored map URI must not have a query or fragment");
        }
        if (uri.isAbsolute()) {
            if (!"file".equalsIgnoreCase(uri.getScheme()) || uri.isOpaque()) {
                throw new IllegalArgumentException("Stored map URI must be relative or an absolute file URI");
            }
        } else if (uri.isOpaque() || uri.getRawPath() == null || uri.getRawPath().isEmpty()) {
            throw new IllegalArgumentException("Relative stored map URI must be hierarchical and nonempty");
        }
        return uri;
    }

    private static String validateColor(final String value) {
        Objects.requireNonNull(value, "color");
        if (!APPROVED_COLORS.contains(value)) {
            throw new IllegalArgumentException("Map color must be one of the approved map colors");
        }
        return value;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MapReference)) {
            return false;
        }
        final MapReference that = (MapReference) other;
        return sequence == that.sequence && active == that.active && id.equals(that.id)
            && storedUri.equals(that.storedUri) && color.equals(that.color)
            && unknownXml.equals(that.unknownXml);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, sequence, storedUri, active, color, unknownXml);
    }

    @Override
    public String toString() {
        return "MapReference{" + "id=" + id + ", sequence=" + sequence + ", storedUri=" + storedUri
            + ", active=" + active + ", color=" + color + ", unknownXml=" + unknownXml + '}';
    }
}
