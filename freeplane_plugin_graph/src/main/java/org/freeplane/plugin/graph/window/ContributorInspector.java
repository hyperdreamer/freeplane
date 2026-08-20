package org.freeplane.plugin.graph.window;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.freeplane.plugin.graph.command.GraphCommand;
import org.freeplane.plugin.graph.command.GraphCommands;
import org.freeplane.plugin.graph.control.CanvasState;
import org.freeplane.plugin.graph.control.GraphWorkspaceViewBinding;
import org.freeplane.plugin.graph.projection.EdgeContributor;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEdgeKey;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.input.ConnectorDescriptor;
import org.freeplane.plugin.graph.projection.input.MapAvailability;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;

final class ContributorInspector extends JPanel {
    private static final long serialVersionUID = 1L;

    static final class ContributorRow {
        private final String sourceLabel;
        private final String middleLabel;
        private final String targetLabel;
        private final String ownerDisplayName;
        private final org.freeplane.plugin.graph.projection.ContributorKey key;
        private final Optional<ConnectorDescriptor> connectorDescriptor;

        private ContributorRow(final String sourceLabel, final String middleLabel, final String targetLabel,
                final String ownerDisplayName, final org.freeplane.plugin.graph.projection.ContributorKey key,
                final Optional<ConnectorDescriptor> connectorDescriptor) {
            this.sourceLabel = requireText(sourceLabel, "sourceLabel");
            this.middleLabel = Objects.requireNonNull(middleLabel, "middleLabel");
            this.targetLabel = requireText(targetLabel, "targetLabel");
            this.ownerDisplayName = Objects.requireNonNull(ownerDisplayName, "ownerDisplayName");
            this.key = Objects.requireNonNull(key, "key");
            this.connectorDescriptor = Objects.requireNonNull(connectorDescriptor, "connectorDescriptor");
        }

        static ContributorRow of(final String sourceLabel, final String middleLabel, final String targetLabel,
                final String ownerDisplayName, final org.freeplane.plugin.graph.projection.ContributorKey key,
                final Optional<ConnectorDescriptor> connectorDescriptor) {
            return new ContributorRow(sourceLabel, middleLabel, targetLabel, ownerDisplayName, key,
                connectorDescriptor);
        }

        String sourceLabel() {
            return sourceLabel;
        }

        String middleLabel() {
            return middleLabel;
        }

        String targetLabel() {
            return targetLabel;
        }

        String ownerDisplayName() {
            return ownerDisplayName;
        }

        Optional<String> ownerName() {
            return ownerDisplayName.isEmpty() ? Optional.<String>empty() : Optional.of(ownerDisplayName);
        }

        org.freeplane.plugin.graph.projection.ContributorKey key() {
            return key;
        }

        Optional<ConnectorDescriptor> connectorDescriptor() {
            return connectorDescriptor;
        }
    }

    private final long displayedGeneration;
    private final ProjectedEdgeKey edgeKey;
    private final List<ContributorRow> rows;
    private final Consumer<GraphCommand> commandSink;
    private final JButton deleteAllButton = button("Delete all", "delete-all");
    private final Map<org.freeplane.plugin.graph.projection.ContributorKey, JButton> deleteButtons =
        new LinkedHashMap<org.freeplane.plugin.graph.projection.ContributorKey, JButton>();
    private boolean readOnly;

    ContributorInspector(final long displayedGeneration, final GraphProjection projection,
            final ProjectedEdge edge, final List<GraphWorkspaceViewBinding.MapRegistration> mapRows,
            final Consumer<GraphCommand> commandSink) {
        super(new BorderLayout(0, 4));
        if (displayedGeneration < 0L) {
            throw new IllegalArgumentException("displayedGeneration must be nonnegative");
        }
        this.displayedGeneration = displayedGeneration;
        final ProjectedEdge displayedEdge = Objects.requireNonNull(edge, "edge");
        this.edgeKey = displayedEdge.key();
        this.commandSink = Objects.requireNonNull(commandSink, "commandSink");
        this.rows = Collections.unmodifiableList(buildRows(Objects.requireNonNull(projection, "projection"),
            displayedEdge, mapNames(mapRows)));
        setName("graph-workspace-contributor-inspector");
        setPreferredSize(new Dimension(360, 180));
        add(new JLabel("Contributors"), BorderLayout.NORTH);
        final JPanel rowPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 3, 2));
        rowPanel.setName("graph-workspace-contributor-rows");
        for (final ContributorRow row : rows) {
            final JButton delete = button("Delete", "delete-contributor");
            delete.addActionListener(event -> deleteOne(row.key));
            deleteButtons.put(row.key, delete);
            rowPanel.add(delete);
        }
        add(new JScrollPane(rowPanel), BorderLayout.CENTER);
        deleteAllButton.addActionListener(event -> deleteAll());
        add(deleteAllButton, BorderLayout.SOUTH);
        updateButtons();
    }

    ContributorInspector(final CanvasState state, final ProjectedEdge edge,
            final List<GraphWorkspaceViewBinding.MapRegistration> mapRows,
            final Consumer<GraphCommand> commandSink) {
        this(Objects.requireNonNull(state, "state").generation(), state.projection(), edge, mapRows, commandSink);
    }

    long displayedGeneration() {
        return displayedGeneration;
    }

    ProjectedEdgeKey edgeKey() {
        return edgeKey;
    }

    List<ContributorRow> rows() {
        return rows;
    }

    JButton deleteAllButton() {
        return deleteAllButton;
    }

    JButton deleteButton(final org.freeplane.plugin.graph.projection.ContributorKey key) {
        return deleteButtons.get(Objects.requireNonNull(key, "key"));
    }

    void setReadOnly(final boolean value) {
        readOnly = value;
        updateButtons();
    }

    boolean isReadOnly() {
        return readOnly;
    }

    void deleteOne(final org.freeplane.plugin.graph.projection.ContributorKey key) {
        if (readOnly) {
            return;
        }
        final ContributorRow row = rowFor(key);
        if (row == null) {
            return;
        }
        commandSink.accept(GraphCommands.deleteContributor(displayedGeneration, row.key,
            row.connectorDescriptor.orElse(null)));
    }

    void deleteAll() {
        if (readOnly || rows.isEmpty()) {
            return;
        }
        final List<org.freeplane.plugin.graph.projection.ContributorKey> keys =
            new ArrayList<org.freeplane.plugin.graph.projection.ContributorKey>(rows.size());
        final Map<org.freeplane.plugin.graph.projection.ContributorKey, ConnectorDescriptor> descriptors =
            new LinkedHashMap<org.freeplane.plugin.graph.projection.ContributorKey, ConnectorDescriptor>();
        for (final ContributorRow row : rows) {
            keys.add(row.key);
            if (row.connectorDescriptor.isPresent()) {
                descriptors.put(row.key, row.connectorDescriptor.get());
            }
        }
        commandSink.accept(GraphCommands.deleteAllContributors(displayedGeneration, edgeKey, keys, descriptors));
    }

    private ContributorRow rowFor(final org.freeplane.plugin.graph.projection.ContributorKey key) {
        final org.freeplane.plugin.graph.projection.ContributorKey value = Objects.requireNonNull(key, "key");
        for (final ContributorRow row : rows) {
            if (value.equals(row.key)) {
                return row;
            }
        }
        return null;
    }

    private void updateButtons() {
        for (final JButton button : deleteButtons.values()) {
            button.setEnabled(!readOnly);
        }
        deleteAllButton.setEnabled(!readOnly && !rows.isEmpty());
    }

    private static List<ContributorRow> buildRows(final GraphProjection projection, final ProjectedEdge edge,
            final Map<MapReferenceId, String> mapNames) {
        final List<ContributorRow> result = new ArrayList<ContributorRow>(edge.contributors().size());
        for (final EdgeContributor contributor : edge.contributors()) {
            final String source = GraphWindowEndpointLabels.displayText(projection, contributor.projectedSource());
            final String target = GraphWindowEndpointLabels.displayText(projection, contributor.projectedTarget());
            final String owner = mapNames.containsKey(contributor.source().mapReferenceId())
                ? mapNames.get(contributor.source().mapReferenceId()) : "";
            final Optional<ConnectorDescriptor> descriptor = contributor.connectorDescriptor();
            result.add(ContributorRow.of(source, contributor.middleLabel(), target, owner, contributor.key(),
                descriptor));
        }
        return result;
    }

    static Map<MapReferenceId, String> mapNames(final List<GraphWorkspaceViewBinding.MapRegistration> mapRows) {
        Objects.requireNonNull(mapRows, "mapRows");
        final Map<MapReferenceId, String> result = new LinkedHashMap<MapReferenceId, String>();
        for (final GraphWorkspaceViewBinding.MapRegistration row : mapRows) {
            final GraphWorkspaceViewBinding.MapRegistration value = Objects.requireNonNull(row, "map row");
            result.put(value.mapReferenceId(), value.displayName());
        }
        return Collections.unmodifiableMap(result);
    }

    private static JButton button(final String text, final String name) {
        final JButton result = new JButton(text);
        result.setName("graph-workspace-contributor-" + name);
        result.setMargin(new Insets(2, 7, 2, 7));
        result.setFocusable(false);
        return result;
    }

    private static String requireText(final String value, final String name) {
        Objects.requireNonNull(value, name);
        if (value.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return value;
    }
}

final class GraphWindowEndpointLabels {
    private GraphWindowEndpointLabels() {
    }

    static String displayText(final GraphProjection projection, final ProjectedEndpointKey endpoint) {
        final ProjectedEndpointKey value = Objects.requireNonNull(endpoint, "endpoint");
        if (value.isNode()) {
            for (final ProjectedNode node : Objects.requireNonNull(projection, "projection").nodes()) {
                if (node.key().equals(value.node().get())) {
                    final SafeNodeLabel label = node.label();
                    return label.displayText();
                }
            }
        }
        else if (value.isEnclosure()) {
            for (final org.freeplane.plugin.graph.projection.ProjectedEnclosure enclosure
                    : Objects.requireNonNull(projection, "projection").enclosures()) {
                final List<EnclosureKey> keys = enclosure.endpointKeys();
                for (int index = 0; index < keys.size(); index++) {
                    if (keys.get(index).equals(value.enclosure().get())) {
                        return enclosure.labels().get(index).displayText();
                    }
                }
            }
        }
        return "Unknown endpoint";
    }

    static String description(final GraphProjection projection, final Optional<ProjectedEndpointKey> endpoint,
            final MapReferenceId mapId, final Map<MapReferenceId, String> mapNames) {
        final String label = endpoint.isPresent() ? displayText(projection, endpoint.get()) : "Missing node";
        final String mapName = mapNames.get(mapId);
        return mapName == null ? label : mapName + " / " + label;
    }
}
