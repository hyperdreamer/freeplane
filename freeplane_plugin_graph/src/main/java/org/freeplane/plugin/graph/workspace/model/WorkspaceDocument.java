package org.freeplane.plugin.graph.workspace.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class WorkspaceDocument {
    private static final int FORMAT_VERSION = 1;
    private static final Comparator<MapReference> MAP_ORDER = new Comparator<MapReference>() {
        @Override
        public int compare(final MapReference first, final MapReference second) {
            return Long.compare(first.sequence(), second.sequence());
        }
    };
    private static final Comparator<GraphRelationshipRecord> RELATIONSHIP_ORDER =
        new Comparator<GraphRelationshipRecord>() {
            @Override
            public int compare(final GraphRelationshipRecord first, final GraphRelationshipRecord second) {
                return Long.compare(first.sequence(), second.sequence());
            }
        };
    private static final Comparator<PinRecord> PIN_ORDER = new Comparator<PinRecord>() {
        @Override
        public int compare(final PinRecord first, final PinRecord second) {
            int result = first.node().mapReferenceId().value().toString()
                .compareTo(second.node().mapReferenceId().value().toString());
            if (result != 0) {
                return result;
            }
            return first.node().nodeId().value().compareTo(second.node().nodeId().value());
        }
    };

    private final WorkspaceId id;
    private final int formatVersion;
    private final int sourceFormatVersion;
    private final WorkspaceCompatibility compatibility;
    private final List<MapReference> maps;
    private final List<GraphRelationshipRecord> relationships;
    private final List<PinRecord> pins;
    private final Viewport viewport;
    private final DisplaySettings displaySettings;
    private final List<UnknownXml> unknownXml;

    private WorkspaceDocument(final WorkspaceId id, final int sourceFormatVersion,
            final WorkspaceCompatibility compatibility, final List<MapReference> maps,
            final List<GraphRelationshipRecord> relationships, final List<PinRecord> pins,
            final Viewport viewport, final DisplaySettings displaySettings,
            final List<UnknownXml> unknownXml) {
        this.id = Objects.requireNonNull(id, "id");
        validateCompatibility(sourceFormatVersion, compatibility);
        this.formatVersion = FORMAT_VERSION;
        this.sourceFormatVersion = sourceFormatVersion;
        this.compatibility = compatibility;

        this.maps = normalizeMaps(maps);
        final Set<MapReferenceId> registeredMapIds = mapIds(this.maps);
        this.relationships = normalizeRelationships(relationships, registeredMapIds);
        this.pins = normalizePins(pins, registeredMapIds);
        this.viewport = Objects.requireNonNull(viewport, "viewport");
        this.displaySettings = Objects.requireNonNull(displaySettings, "displaySettings");
        this.unknownXml = UnknownXml.forContainers(unknownXml);
    }

    public static WorkspaceDocument createVersion1(final WorkspaceId id) {
        return new Builder().id(id)
            .sourceFormatVersion(1)
            .compatibility(WorkspaceCompatibility.WRITABLE_VERSION_1)
            .maps(Collections.<MapReference>emptyList())
            .relationships(Collections.<GraphRelationshipRecord>emptyList())
            .pins(Collections.<PinRecord>emptyList())
            .viewport(Viewport.of(0, 0, 1, Collections.<UnknownXml>emptyList()))
            .displaySettings(DisplaySettings.defaults())
            .unknownXml(Collections.<UnknownXml>emptyList())
            .build();
    }

    public WorkspaceId id() {
        return id;
    }

    public int formatVersion() {
        return formatVersion;
    }

    public int sourceFormatVersion() {
        return sourceFormatVersion;
    }

    public WorkspaceCompatibility compatibility() {
        return compatibility;
    }

    public List<MapReference> maps() {
        return maps;
    }

    public List<GraphRelationshipRecord> relationships() {
        return relationships;
    }

    public List<PinRecord> pins() {
        return pins;
    }

    public Viewport viewport() {
        return viewport;
    }

    public DisplaySettings displaySettings() {
        return displaySettings;
    }

    public List<UnknownXml> unknownXml() {
        return unknownXml;
    }

    public Builder toBuilder() {
        return new Builder()
            .id(id)
            .sourceFormatVersion(sourceFormatVersion)
            .compatibility(compatibility)
            .maps(maps)
            .relationships(relationships)
            .pins(pins)
            .viewport(viewport)
            .displaySettings(displaySettings)
            .unknownXml(unknownXml);
    }

    private static void validateCompatibility(final int sourceFormatVersion,
            final WorkspaceCompatibility compatibility) {
        Objects.requireNonNull(compatibility, "compatibility");
        if (sourceFormatVersion <= 0) {
            throw new IllegalArgumentException("Source format version must be positive");
        }
        if (compatibility == WorkspaceCompatibility.WRITABLE_VERSION_1 && sourceFormatVersion != FORMAT_VERSION) {
            throw new IllegalArgumentException("Writable documents must have source format version 1");
        }
        if (compatibility == WorkspaceCompatibility.READ_ONLY_NEWER && sourceFormatVersion <= FORMAT_VERSION) {
            throw new IllegalArgumentException("Read-only documents must come from a newer format version");
        }
    }

    private static List<MapReference> normalizeMaps(final List<MapReference> values) {
        Objects.requireNonNull(values, "maps");
        final List<MapReference> copy = new ArrayList<MapReference>(values.size());
        final Set<MapReferenceId> ids = new HashSet<MapReferenceId>();
        final Set<Long> sequences = new HashSet<Long>();
        for (final MapReference value : values) {
            final MapReference map = Objects.requireNonNull(value, "map");
            if (!ids.add(map.id())) {
                throw new IllegalArgumentException("Map IDs must be unique");
            }
            if (!sequences.add(map.sequence())) {
                throw new IllegalArgumentException("Map sequences must be unique");
            }
            copy.add(map);
        }
        Collections.sort(copy, MAP_ORDER);
        return Collections.unmodifiableList(copy);
    }

    private static List<GraphRelationshipRecord> normalizeRelationships(
            final List<GraphRelationshipRecord> values, final Set<MapReferenceId> registeredMapIds) {
        Objects.requireNonNull(values, "relationships");
        final List<GraphRelationshipRecord> copy = new ArrayList<GraphRelationshipRecord>(values.size());
        final Set<RelationshipId> ids = new HashSet<RelationshipId>();
        final Set<Long> sequences = new HashSet<Long>();
        for (final GraphRelationshipRecord value : values) {
            final GraphRelationshipRecord relationship = Objects.requireNonNull(value, "relationship");
            if (!ids.add(relationship.id())) {
                throw new IllegalArgumentException("Relationship IDs must be unique");
            }
            if (!sequences.add(relationship.sequence())) {
                throw new IllegalArgumentException("Relationship sequences must be unique");
            }
            if (!registeredMapIds.contains(relationship.source().mapReferenceId())
                    || !registeredMapIds.contains(relationship.target().mapReferenceId())) {
                throw new IllegalArgumentException("Relationship endpoints must reference registered maps");
            }
            if (relationship.source().mapReferenceId().equals(relationship.target().mapReferenceId())) {
                throw new IllegalArgumentException("Relationship endpoints must cross maps");
            }
            copy.add(relationship);
        }
        Collections.sort(copy, RELATIONSHIP_ORDER);
        return Collections.unmodifiableList(copy);
    }

    private static List<PinRecord> normalizePins(final List<PinRecord> values,
            final Set<MapReferenceId> registeredMapIds) {
        Objects.requireNonNull(values, "pins");
        final List<PinRecord> copy = new ArrayList<PinRecord>(values.size());
        final Set<NodeReference> nodes = new HashSet<NodeReference>();
        for (final PinRecord value : values) {
            final PinRecord pin = Objects.requireNonNull(value, "pin");
            if (!registeredMapIds.contains(pin.node().mapReferenceId())) {
                throw new IllegalArgumentException("Pinned nodes must reference registered maps");
            }
            if (!nodes.add(pin.node())) {
                throw new IllegalArgumentException("Pinned node identities must be unique");
            }
            copy.add(pin);
        }
        Collections.sort(copy, PIN_ORDER);
        return Collections.unmodifiableList(copy);
    }

    private static Set<MapReferenceId> mapIds(final List<MapReference> maps) {
        final Set<MapReferenceId> ids = new HashSet<MapReferenceId>();
        for (final MapReference map : maps) {
            ids.add(map.id());
        }
        return ids;
    }

    private static <T> List<T> copyBuilderList(final List<T> values, final String name) {
        Objects.requireNonNull(values, name);
        final List<T> copy = new ArrayList<T>(values.size());
        for (final T value : values) {
            copy.add(Objects.requireNonNull(value, name + " entry"));
        }
        return Collections.unmodifiableList(copy);
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WorkspaceDocument)) {
            return false;
        }
        final WorkspaceDocument that = (WorkspaceDocument) other;
        return formatVersion == that.formatVersion && sourceFormatVersion == that.sourceFormatVersion
            && id.equals(that.id) && compatibility == that.compatibility && maps.equals(that.maps)
            && relationships.equals(that.relationships) && pins.equals(that.pins)
            && viewport.equals(that.viewport) && displaySettings.equals(that.displaySettings)
            && unknownXml.equals(that.unknownXml);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, formatVersion, sourceFormatVersion, compatibility, maps, relationships, pins,
            viewport, displaySettings, unknownXml);
    }

    @Override
    public String toString() {
        return "WorkspaceDocument{" + "id=" + id + ", formatVersion=" + formatVersion
            + ", sourceFormatVersion=" + sourceFormatVersion + ", compatibility=" + compatibility
            + ", maps=" + maps + ", relationships=" + relationships + ", pins=" + pins
            + ", viewport=" + viewport + ", displaySettings=" + displaySettings
            + ", unknownXml=" + unknownXml + '}';
    }

    public static final class Builder {
        private WorkspaceId id;
        private int sourceFormatVersion = FORMAT_VERSION;
        private WorkspaceCompatibility compatibility = WorkspaceCompatibility.WRITABLE_VERSION_1;
        private List<MapReference> maps = Collections.emptyList();
        private List<GraphRelationshipRecord> relationships = Collections.emptyList();
        private List<PinRecord> pins = Collections.emptyList();
        private Viewport viewport = Viewport.of(0, 0, 1, Collections.<UnknownXml>emptyList());
        private DisplaySettings displaySettings = DisplaySettings.defaults();
        private List<UnknownXml> unknownXml = Collections.emptyList();

        public Builder() {
        }

        public Builder id(final WorkspaceId id) {
            this.id = id;
            return this;
        }

        public Builder sourceFormatVersion(final int version) {
            this.sourceFormatVersion = version;
            return this;
        }

        public Builder compatibility(final WorkspaceCompatibility compatibility) {
            this.compatibility = compatibility;
            return this;
        }

        public Builder maps(final List<MapReference> maps) {
            this.maps = copyBuilderList(maps, "maps");
            return this;
        }

        public Builder relationships(final List<GraphRelationshipRecord> relationships) {
            this.relationships = copyBuilderList(relationships, "relationships");
            return this;
        }

        public Builder pins(final List<PinRecord> pins) {
            this.pins = copyBuilderList(pins, "pins");
            return this;
        }

        public Builder viewport(final Viewport viewport) {
            this.viewport = viewport;
            return this;
        }

        public Builder displaySettings(final DisplaySettings settings) {
            this.displaySettings = settings;
            return this;
        }

        public Builder unknownXml(final List<UnknownXml> unknownXml) {
            this.unknownXml = copyBuilderList(unknownXml, "unknownXml");
            return this;
        }

        public WorkspaceDocument build() {
            return new WorkspaceDocument(id, sourceFormatVersion, compatibility, maps, relationships, pins,
                viewport, displaySettings, unknownXml);
        }
    }
}
