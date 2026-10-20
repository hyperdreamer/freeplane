package org.freeplane.plugin.graph.command;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.freeplane.plugin.graph.projection.ContributorKey;
import org.freeplane.plugin.graph.projection.ProjectedEdgeKey;
import org.freeplane.plugin.graph.projection.input.ConnectorDescriptor;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.DisplaySettings;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.RelationshipDirection;
import org.freeplane.plugin.graph.workspace.model.RelationshipId;

public final class GraphCommands {
    private GraphCommands() {
    }

    public static AddMap addMap(final MapReferenceId proposedId, final URI storedUri) {
        return new AddMap(proposedId, storedUri);
    }

    public static RetryMap retryMap(final MapReferenceId mapReferenceId) {
        return new RetryMap(mapReferenceId);
    }

    public static RemoveMap removeMap(final MapReferenceId mapReferenceId) {
        return new RemoveMap(mapReferenceId);
    }

    public static ReactivateMap reactivateMap(final MapReferenceId mapReferenceId) {
        return new ReactivateMap(mapReferenceId);
    }

    public static DeleteMap deleteMap(final MapReferenceId mapReferenceId) {
        return new DeleteMap(mapReferenceId);
    }

    public static LocateMap locateMap(final MapReferenceId mapReferenceId, final URI replacementUri) {
        return new LocateMap(mapReferenceId, replacementUri);
    }

    public static CreateRelationship createRelationship(final RelationshipId id, final NodeReference source,
            final NodeReference target, final RelationshipDirection direction) {
        return new CreateRelationship(id, source, target, direction);
    }

    public static UpdateRelationship updateRelationship(final RelationshipId id, final NodeReference source,
            final NodeReference target, final RelationshipDirection direction) {
        return new UpdateRelationship(id, source, target, direction);
    }

    public static DeleteRelationship deleteRelationship(final RelationshipId id) {
        return new DeleteRelationship(id);
    }

    public static Purge purge(final long displayedGeneration, final Set<RelationshipId> relationships) {
        return new Purge(displayedGeneration, relationships);
    }

    public static DeleteContributor deleteContributor(final long displayedGeneration,
            final ContributorKey contributor, final ConnectorDescriptor expected) {
        return new DeleteContributor(displayedGeneration, contributor, expected);
    }

    public static DeleteAllContributors deleteAllContributors(final long displayedGeneration,
            final ProjectedEdgeKey edge, final List<ContributorKey> contributors,
            final Map<ContributorKey, ConnectorDescriptor> expected) {
        return new DeleteAllContributors(displayedGeneration, edge, contributors, expected);
    }

    public static Pin pin(final NodeReference node, final double x, final double y) {
        return new Pin(node, x, y);
    }

    public static Unpin unpin(final NodeReference node) {
        return new Unpin(node);
    }

    public static UnpinAll unpinAll() {
        return new UnpinAll();
    }

    public static Display display(final DisplaySettings settings) {
        return new Display(settings);
    }

    public static Display setDisplaySettings(final DisplaySettings settings) {
        return display(settings);
    }

    public static Viewport viewport(final org.freeplane.plugin.graph.workspace.model.Viewport viewport) {
        return new Viewport(viewport);
    }

    public static Viewport updateViewport(final org.freeplane.plugin.graph.workspace.model.Viewport viewport) {
        return viewport(viewport);
    }

    public static UndoWorkspace undoWorkspace() {
        return new UndoWorkspace();
    }

    public static UndoWorkspace undo() {
        return undoWorkspace();
    }

    public static RedoWorkspace redoWorkspace() {
        return new RedoWorkspace();
    }

    public static RedoWorkspace redo() {
        return redoWorkspace();
    }

    public static UndoSourceMap undoSourceMap() {
        return new UndoSourceMap();
    }

    public static Save save() {
        return new Save();
    }

    public static RetrySave retrySave() {
        return new RetrySave();
    }

    public static SaveAs saveAs(final Path target) {
        return new SaveAs(target);
    }

    public static PauseLayout pauseLayout() {
        return new PauseLayout();
    }

    public static RestartLayout restartLayout() {
        return new RestartLayout();
    }

    public static ResetLayout resetLayout() {
        return new ResetLayout();
    }

    public static Connect connect(final SourceNodeKey source, final SourceNodeKey target,
            final RelationshipDirection direction) {
        return new Connect(source, target, direction);
    }

    public static Connect createConnector(final SourceNodeKey source, final SourceNodeKey target,
            final RelationshipDirection direction) {
        return connect(source, target, direction);
    }

    public static OpenSource openSource(final SourceNodeKey source) {
        return new OpenSource(source);
    }

    public static final class AddMap implements GraphCommand {
        private final MapReferenceId proposedId;
        private final URI storedUri;

        private AddMap(final MapReferenceId proposedId, final URI storedUri) {
            this.proposedId = Objects.requireNonNull(proposedId, "proposedId");
            this.storedUri = Objects.requireNonNull(storedUri, "storedUri");
        }

        public MapReferenceId proposedId() {
            return proposedId;
        }

        public MapReferenceId mapReferenceId() {
            return proposedId;
        }

        public URI storedUri() {
            return storedUri;
        }
    }

    public static final class RetryMap implements GraphCommand {
        private final MapReferenceId mapReferenceId;

        private RetryMap(final MapReferenceId mapReferenceId) {
            this.mapReferenceId = Objects.requireNonNull(mapReferenceId, "mapReferenceId");
        }

        public MapReferenceId mapReferenceId() {
            return mapReferenceId;
        }
    }

    public static final class RemoveMap implements GraphCommand {
        private final MapReferenceId mapReferenceId;

        private RemoveMap(final MapReferenceId mapReferenceId) {
            this.mapReferenceId = Objects.requireNonNull(mapReferenceId, "mapReferenceId");
        }

        public MapReferenceId mapReferenceId() {
            return mapReferenceId;
        }
    }

    public static final class ReactivateMap implements GraphCommand {
        private final MapReferenceId mapReferenceId;

        private ReactivateMap(final MapReferenceId mapReferenceId) {
            this.mapReferenceId = Objects.requireNonNull(mapReferenceId, "mapReferenceId");
        }

        public MapReferenceId mapReferenceId() {
            return mapReferenceId;
        }
    }

    public static final class DeleteMap implements GraphCommand {
        private final MapReferenceId mapReferenceId;

        private DeleteMap(final MapReferenceId mapReferenceId) {
            this.mapReferenceId = Objects.requireNonNull(mapReferenceId, "mapReferenceId");
        }

        public MapReferenceId mapReferenceId() {
            return mapReferenceId;
        }
    }

    public static final class LocateMap implements GraphCommand {
        private final MapReferenceId mapReferenceId;
        private final URI replacementUri;

        private LocateMap(final MapReferenceId mapReferenceId, final URI replacementUri) {
            this.mapReferenceId = Objects.requireNonNull(mapReferenceId, "mapReferenceId");
            this.replacementUri = Objects.requireNonNull(replacementUri, "replacementUri");
        }

        public MapReferenceId mapReferenceId() {
            return mapReferenceId;
        }

        public URI replacementUri() {
            return replacementUri;
        }
    }

    public static final class CreateRelationship implements GraphCommand {
        private final RelationshipId id;
        private final NodeReference source;
        private final NodeReference target;
        private final RelationshipDirection direction;

        private CreateRelationship(final RelationshipId id, final NodeReference source,
                final NodeReference target, final RelationshipDirection direction) {
            this.id = Objects.requireNonNull(id, "id");
            this.source = Objects.requireNonNull(source, "source");
            this.target = Objects.requireNonNull(target, "target");
            this.direction = Objects.requireNonNull(direction, "direction");
        }

        public RelationshipId id() {
            return id;
        }

        public NodeReference source() {
            return source;
        }

        public NodeReference target() {
            return target;
        }

        public RelationshipDirection direction() {
            return direction;
        }
    }

    public static final class UpdateRelationship implements GraphCommand {
        private final RelationshipId id;
        private final NodeReference source;
        private final NodeReference target;
        private final RelationshipDirection direction;

        private UpdateRelationship(final RelationshipId id, final NodeReference source,
                final NodeReference target, final RelationshipDirection direction) {
            this.id = Objects.requireNonNull(id, "id");
            this.source = Objects.requireNonNull(source, "source");
            this.target = Objects.requireNonNull(target, "target");
            this.direction = Objects.requireNonNull(direction, "direction");
        }

        public RelationshipId id() {
            return id;
        }

        public NodeReference source() {
            return source;
        }

        public NodeReference target() {
            return target;
        }

        public RelationshipDirection direction() {
            return direction;
        }
    }

    public static final class DeleteRelationship implements GraphCommand {
        private final RelationshipId id;

        private DeleteRelationship(final RelationshipId id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        public RelationshipId id() {
            return id;
        }
    }

    public static final class Purge implements GraphCommand {
        private final long displayedGeneration;
        private final Set<RelationshipId> relationships;

        private Purge(final long displayedGeneration, final Set<RelationshipId> relationships) {
            this.displayedGeneration = generation(displayedGeneration);
            this.relationships = copySet(relationships, "relationships");
        }

        public long displayedGeneration() {
            return displayedGeneration;
        }

        public Set<RelationshipId> relationships() {
            return relationships;
        }
    }

    public static final class DeleteContributor implements GraphCommand {
        private final long displayedGeneration;
        private final ContributorKey contributor;
        private final ConnectorDescriptor expectedConnector;

        private DeleteContributor(final long displayedGeneration, final ContributorKey contributor,
                final ConnectorDescriptor expectedConnector) {
            this.displayedGeneration = generation(displayedGeneration);
            this.contributor = Objects.requireNonNull(contributor, "contributor");
            this.expectedConnector = expectedConnector;
        }

        public long displayedGeneration() {
            return displayedGeneration;
        }

        public ContributorKey contributor() {
            return contributor;
        }

        public Optional<ConnectorDescriptor> expectedConnector() {
            return Optional.ofNullable(expectedConnector);
        }
    }

    public static final class DeleteAllContributors implements GraphCommand {
        private final long displayedGeneration;
        private final ProjectedEdgeKey edge;
        private final List<ContributorKey> contributors;
        private final Map<ContributorKey, ConnectorDescriptor> expectedConnectors;

        private DeleteAllContributors(final long displayedGeneration, final ProjectedEdgeKey edge,
                final List<ContributorKey> contributors,
                final Map<ContributorKey, ConnectorDescriptor> expectedConnectors) {
            this.displayedGeneration = generation(displayedGeneration);
            this.edge = Objects.requireNonNull(edge, "edge");
            this.contributors = copyList(contributors, "contributors");
            this.expectedConnectors = copyMap(expectedConnectors, "expectedConnectors");
        }

        public long displayedGeneration() {
            return displayedGeneration;
        }

        public ProjectedEdgeKey edge() {
            return edge;
        }

        public List<ContributorKey> contributors() {
            return contributors;
        }

        public Map<ContributorKey, ConnectorDescriptor> expectedConnectors() {
            return expectedConnectors;
        }
    }

    public static final class Pin implements GraphCommand {
        private final NodeReference node;
        private final double x;
        private final double y;

        private Pin(final NodeReference node, final double x, final double y) {
            this.node = Objects.requireNonNull(node, "node");
            this.x = x;
            this.y = y;
        }

        public NodeReference node() {
            return node;
        }

        public double x() {
            return x;
        }

        public double y() {
            return y;
        }
    }

    public static final class Unpin implements GraphCommand {
        private final NodeReference node;

        private Unpin(final NodeReference node) {
            this.node = Objects.requireNonNull(node, "node");
        }

        public NodeReference node() {
            return node;
        }
    }

    public static final class UnpinAll implements GraphCommand {
        private UnpinAll() {
        }
    }

    public static final class Display implements GraphCommand {
        private final DisplaySettings settings;

        private Display(final DisplaySettings settings) {
            this.settings = Objects.requireNonNull(settings, "settings");
        }

        public DisplaySettings settings() {
            return settings;
        }
    }

    public static final class Viewport implements GraphCommand {
        private final org.freeplane.plugin.graph.workspace.model.Viewport viewport;

        private Viewport(final org.freeplane.plugin.graph.workspace.model.Viewport viewport) {
            this.viewport = Objects.requireNonNull(viewport, "viewport");
        }

        public org.freeplane.plugin.graph.workspace.model.Viewport viewport() {
            return viewport;
        }
    }

    public static final class UndoWorkspace implements GraphCommand {
        private UndoWorkspace() {
        }
    }

    public static final class RedoWorkspace implements GraphCommand {
        private RedoWorkspace() {
        }
    }

    public static final class UndoSourceMap implements GraphCommand {
        private UndoSourceMap() {
        }
    }

    public static final class Save implements GraphCommand {
        private Save() {
        }
    }

    public static final class RetrySave implements GraphCommand {
        private RetrySave() {
        }
    }

    public static final class SaveAs implements GraphCommand {
        private final Path target;

        private SaveAs(final Path target) {
            this.target = Objects.requireNonNull(target, "target");
        }

        public Path target() {
            return target;
        }
    }

    public static final class PauseLayout implements GraphCommand {
        private PauseLayout() {
        }
    }

    public static final class RestartLayout implements GraphCommand {
        private RestartLayout() {
        }
    }

    public static final class ResetLayout implements GraphCommand {
        private ResetLayout() {
        }
    }

    public static final class Connect implements GraphCommand {
        private final SourceNodeKey source;
        private final SourceNodeKey target;
        private final RelationshipDirection direction;

        private Connect(final SourceNodeKey source, final SourceNodeKey target,
                final RelationshipDirection direction) {
            this.source = Objects.requireNonNull(source, "source");
            this.target = Objects.requireNonNull(target, "target");
            this.direction = Objects.requireNonNull(direction, "direction");
        }

        public SourceNodeKey source() {
            return source;
        }

        public SourceNodeKey target() {
            return target;
        }

        public RelationshipDirection direction() {
            return direction;
        }
    }

    public static final class OpenSource implements GraphCommand {
        private final SourceNodeKey source;

        private OpenSource(final SourceNodeKey source) {
            this.source = Objects.requireNonNull(source, "source");
        }

        public SourceNodeKey source() {
            return source;
        }
    }

    private static long generation(final long value) {
        if (value < 0L) {
            throw new IllegalArgumentException("displayedGeneration must be nonnegative");
        }
        return value;
    }

    private static <T> Set<T> copySet(final Set<T> values, final String name) {
        Objects.requireNonNull(values, name);
        final Set<T> copy = new LinkedHashSet<T>();
        for (final T value : values) {
            copy.add(Objects.requireNonNull(value, name + " entry"));
        }
        return Collections.unmodifiableSet(copy);
    }

    private static <T> List<T> copyList(final List<T> values, final String name) {
        Objects.requireNonNull(values, name);
        final List<T> copy = new ArrayList<T>(values.size());
        for (final T value : values) {
            copy.add(Objects.requireNonNull(value, name + " entry"));
        }
        return Collections.unmodifiableList(copy);
    }

    private static <K, V> Map<K, V> copyMap(final Map<K, V> values, final String name) {
        Objects.requireNonNull(values, name);
        final Map<K, V> copy = new LinkedHashMap<K, V>();
        for (final Map.Entry<K, V> entry : values.entrySet()) {
            copy.put(Objects.requireNonNull(entry.getKey(), name + " key"),
                Objects.requireNonNull(entry.getValue(), name + " value"));
        }
        return Collections.unmodifiableMap(copy);
    }
}
