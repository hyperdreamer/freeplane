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

import org.freeplane.core.util.TextUtils;
import org.freeplane.plugin.graph.command.GraphCommand;
import org.freeplane.plugin.graph.command.GraphCommands;
import org.freeplane.plugin.graph.control.CanvasState;
import org.freeplane.plugin.graph.control.OperationalStatus;
import org.freeplane.plugin.graph.control.WorkspaceSessionStatus;
import org.freeplane.plugin.graph.control.GraphWorkspaceViewBinding;
import org.freeplane.plugin.graph.projection.RelationshipResolution;
import org.freeplane.plugin.graph.projection.RelationshipStatus;
import org.freeplane.plugin.graph.projection.input.MapAvailability;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;

final class GraphStatusBar extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final int NODE_WARNING_THRESHOLD = 2_000;
    private static final int EDGE_WARNING_THRESHOLD = 5_000;

    static final class MapStatus {
        private final MapReferenceId mapReferenceId;
        private final String displayName;
        private final MapAvailability availability;
        private final int projectedNodeCount;

        private MapStatus(final MapReferenceId mapReferenceId, final String displayName,
                final MapAvailability availability, final int projectedNodeCount) {
            this.mapReferenceId = Objects.requireNonNull(mapReferenceId, "mapReferenceId");
            this.displayName = requireText(displayName, "displayName");
            this.availability = Objects.requireNonNull(availability, "availability");
            if (projectedNodeCount < 0) {
                throw new IllegalArgumentException("projectedNodeCount must be nonnegative");
            }
            this.projectedNodeCount = projectedNodeCount;
        }

        MapReferenceId mapReferenceId() {
            return mapReferenceId;
        }

        String displayName() {
            return displayName;
        }

        MapAvailability availability() {
            return availability;
        }

        int projectedNodeCount() {
            return projectedNodeCount;
        }
    }

    static final class Status {
        private final List<MapStatus> mapStatuses;
        private final int projectedNodeCount;
        private final int projectedEdgeCount;
        private final String selectedEndpointText;
        private final OperationalStatus layoutStatus;
        private final int recoverableCount;
        private final int missingNodeCount;
        private final boolean workspaceDirty;
        private final boolean saveFailed;
        private final List<String> dirtySourceMapNames;
        private final int dirtySourceMapCount;
        private final boolean workspaceUndoAvailable;
        private final boolean workspaceRedoAvailable;
        private final boolean sourceMapUndoAvailable;
        private final boolean readOnly;

        private Status(final List<MapStatus> mapStatuses, final int projectedNodeCount,
                final int projectedEdgeCount, final String selectedEndpointText,
                final OperationalStatus layoutStatus, final int recoverableCount, final int missingNodeCount,
                final boolean workspaceDirty, final boolean saveFailed, final List<String> dirtySourceMapNames,
                final int dirtySourceMapCount, final boolean workspaceUndoAvailable,
                final boolean workspaceRedoAvailable, final boolean sourceMapUndoAvailable,
                final boolean readOnly) {
            this.mapStatuses = copyMapStatuses(mapStatuses);
            this.projectedNodeCount = nonnegative(projectedNodeCount, "projectedNodeCount");
            this.projectedEdgeCount = nonnegative(projectedEdgeCount, "projectedEdgeCount");
            this.selectedEndpointText = Objects.requireNonNull(selectedEndpointText, "selectedEndpointText");
            this.layoutStatus = Objects.requireNonNull(layoutStatus, "layoutStatus");
            this.recoverableCount = nonnegative(recoverableCount, "recoverableCount");
            this.missingNodeCount = nonnegative(missingNodeCount, "missingNodeCount");
            this.workspaceDirty = workspaceDirty;
            this.saveFailed = saveFailed;
            this.dirtySourceMapNames = copyStrings(dirtySourceMapNames, "dirtySourceMapNames");
            this.dirtySourceMapCount = nonnegative(dirtySourceMapCount, "dirtySourceMapCount");
            this.workspaceUndoAvailable = workspaceUndoAvailable;
            this.workspaceRedoAvailable = workspaceRedoAvailable;
            this.sourceMapUndoAvailable = sourceMapUndoAvailable;
            this.readOnly = readOnly;
        }

        static Status from(final CanvasState state, final Optional<String> selectedEndpointText,
                final List<GraphWorkspaceViewBinding.MapRegistration> mapRows,
                final WorkspaceSessionStatus sessionStatus, final boolean readOnly) {
            final CanvasState value = state;
            final WorkspaceSessionStatus session = Objects.requireNonNull(sessionStatus, "sessionStatus");
            final List<GraphWorkspaceViewBinding.MapRegistration> registrations =
                Objects.requireNonNull(mapRows, "mapRows");
            final Map<MapReferenceId, Integer> nodeCounts = new LinkedHashMap<MapReferenceId, Integer>();
            int projectedNodeCount = 0;
            int projectedEdgeCount = 0;
            int recoverableCount = 0;
            int missingNodeCount = 0;
            OperationalStatus layoutStatus = OperationalStatus.LOADING;
            if (value != null) {
                projectedNodeCount = value.projection().projectedNodeCount();
                projectedEdgeCount = value.projection().projectedEdgeCount();
                layoutStatus = value.status();
                for (final org.freeplane.plugin.graph.projection.ProjectedEnclosure enclosure
                        : value.projection().enclosures()) {
                    if (enclosure.mapRoot()) {
                        continue;
                    }
                    final Integer count = nodeCounts.get(enclosure.mapReferenceId());
                    nodeCounts.put(enclosure.mapReferenceId(),
                        Integer.valueOf(count == null ? 1 : count.intValue() + 1));
                }
                for (final RelationshipResolution resolution : value.projection().relationshipResolutions()) {
                    if (resolution.status() == RelationshipStatus.UNRESOLVED_RECOVERABLE) {
                        recoverableCount++;
                    }
                    else if (resolution.status() == RelationshipStatus.UNRESOLVED_MISSING_NODE) {
                        missingNodeCount++;
                    }
                }
            }
            final List<MapStatus> mapStatuses = new ArrayList<MapStatus>(registrations.size());
            final Map<MapReferenceId, String> names = new LinkedHashMap<MapReferenceId, String>();
            for (final GraphWorkspaceViewBinding.MapRegistration registration : registrations) {
                final GraphWorkspaceViewBinding.MapRegistration row = Objects.requireNonNull(registration,
                    "map row");
                names.put(row.mapReferenceId(), row.displayName());
                final Integer count = nodeCounts.get(row.mapReferenceId());
                mapStatuses.add(new MapStatus(row.mapReferenceId(), row.displayName(), row.availability(),
                    count == null ? 0 : count.intValue()));
            }
            final List<String> dirtyNames = new ArrayList<String>();
            for (final MapReferenceId mapId : session.dirtySourceMaps()) {
                final String name = names.get(mapId);
                if (name != null) {
                    dirtyNames.add(name);
                }
            }
            final Optional<String> selected = Objects.requireNonNull(selectedEndpointText,
                "selectedEndpointText");
            return new Status(mapStatuses, projectedNodeCount, projectedEdgeCount,
                selected.isPresent() ? selected.get() : "", layoutStatus, recoverableCount, missingNodeCount,
                session.workspaceDirty(), session.saveFailed(), dirtyNames, session.dirtySourceMaps().size(),
                session.workspaceUndoAvailable(), session.workspaceRedoAvailable(),
                session.sourceMapUndoTarget().isPresent() && session.sourceMapUndoTarget().get().canUndo(), readOnly);
        }

        List<MapStatus> mapStatuses() {
            return mapStatuses;
        }

        int projectedNodeCount() {
            return projectedNodeCount;
        }

        int projectedEdgeCount() {
            return projectedEdgeCount;
        }

        String selectedEndpointText() {
            return selectedEndpointText;
        }

        OperationalStatus layoutStatus() {
            return layoutStatus;
        }

        int recoverableCount() {
            return recoverableCount;
        }

        int missingNodeCount() {
            return missingNodeCount;
        }

        boolean workspaceDirty() {
            return workspaceDirty;
        }

        boolean saveFailed() {
            return saveFailed;
        }

        List<String> dirtySourceMapNames() {
            return dirtySourceMapNames;
        }

        int dirtySourceMapCount() {
            return dirtySourceMapCount;
        }

        boolean workspaceUndoAvailable() {
            return workspaceUndoAvailable;
        }

        boolean workspaceRedoAvailable() {
            return workspaceRedoAvailable;
        }

        boolean workspaceHistoryAvailable() {
            return workspaceUndoAvailable || workspaceRedoAvailable;
        }

        boolean sourceMapUndoAvailable() {
            return sourceMapUndoAvailable;
        }

        boolean readOnly() {
            return readOnly;
        }

        private static List<MapStatus> copyMapStatuses(final List<MapStatus> values) {
            Objects.requireNonNull(values, "mapStatuses");
            final List<MapStatus> copy = new ArrayList<MapStatus>(values.size());
            for (final MapStatus value : values) {
                copy.add(Objects.requireNonNull(value, "map status"));
            }
            return Collections.unmodifiableList(copy);
        }

        private static List<String> copyStrings(final List<String> values, final String name) {
            Objects.requireNonNull(values, name);
            final List<String> copy = new ArrayList<String>(values.size());
            for (final String value : values) {
                copy.add(Objects.requireNonNull(value, name + " entry"));
            }
            return Collections.unmodifiableList(copy);
        }

        private static int nonnegative(final int value, final String name) {
            if (value < 0) {
                throw new IllegalArgumentException(name + " must be nonnegative");
            }
            return value;
        }
    }

    private final Consumer<GraphCommand> commandSink;
    private final JLabel mapAvailability = label("map-availability");
    private final JLabel projectedCounts = label("projected-counts");
    private final JLabel selectedEndpoint = label("selected-endpoint");
    private final JLabel layoutState = label("layout-state");
    private final JLabel unresolvedCounts = label("unresolved-counts");
    private final JLabel workspaceState = label("workspace-state");
    private final JLabel sourceMapState = label("source-map-state");
    private final JLabel historyState = label("history-state");
    private final JLabel nodeWarning = label("node-warning");
    private final JLabel edgeWarning = label("edge-warning");
    private final JButton retrySaveButton = button("graph_workspace.action.retry_save", "retry-save");
    private final JButton restartLayoutButton = button("graph_workspace.action.restart_layout", "restart-layout");
    private final JButton unpinAllButton = button("graph_workspace.action.unpin_all", "unpin-all");
    private Status status;

    GraphStatusBar(final Consumer<GraphCommand> commandSink) {
        super(new BorderLayout(0, 0));
        this.commandSink = Objects.requireNonNull(commandSink, "commandSink");
        setName("graph-workspace-status-bar");
        setPreferredSize(new Dimension(0, 26));
        setMinimumSize(new Dimension(0, 26));

        final JPanel details = new JPanel(new FlowLayout(FlowLayout.LEADING, 7, 2));
        details.setName("graph-workspace-status-details");
        details.add(mapAvailability);
        details.add(projectedCounts);
        details.add(selectedEndpoint);
        details.add(layoutState);
        details.add(unresolvedCounts);
        details.add(workspaceState);
        details.add(sourceMapState);
        details.add(historyState);
        details.add(nodeWarning);
        details.add(edgeWarning);
        add(details, BorderLayout.CENTER);

        final JPanel actions = new JPanel(new FlowLayout(FlowLayout.TRAILING, 3, 0));
        actions.setName("graph-workspace-status-actions");
        actions.add(retrySaveButton);
        actions.add(restartLayoutButton);
        actions.add(unpinAllButton);
        add(actions, BorderLayout.EAST);

        retrySaveButton.addActionListener(event -> emit(GraphCommands.retrySave()));
        restartLayoutButton.addActionListener(event -> emit(GraphCommands.restartLayout()));
        unpinAllButton.addActionListener(event -> emit(GraphCommands.unpinAll()));
        setStatus(Status.from(null, Optional.<String>empty(), Collections.emptyList(),
            WorkspaceSessionStatus.empty(), false));
    }

    void setStatus(final CanvasState state, final Optional<String> selectedEndpointText,
            final List<GraphWorkspaceViewBinding.MapRegistration> mapRows,
            final WorkspaceSessionStatus sessionStatus, final boolean readOnly) {
        setStatus(Status.from(state, selectedEndpointText, mapRows, sessionStatus, readOnly));
    }

    void setStatus(final Status value) {
        status = Objects.requireNonNull(value, "status");
        mapAvailability.setText(mapAvailabilityText(value));
        projectedCounts.setText(TextUtils.format("graph_workspace.status.counts",
            Integer.valueOf(value.projectedNodeCount), Integer.valueOf(value.projectedEdgeCount)));
        selectedEndpoint.setText(value.selectedEndpointText.isEmpty()
            ? TextUtils.getText("graph_workspace.status.selected.none")
            : TextUtils.format("graph_workspace.status.selected", value.selectedEndpointText));
        layoutState.setText(TextUtils.format("graph_workspace.status.layout", TextUtils.getText(
            "graph_workspace.layout_status." + value.layoutStatus.name().toLowerCase(java.util.Locale.ROOT))));
        unresolvedCounts.setText(TextUtils.format("graph_workspace.status.unresolved",
            Integer.valueOf(value.recoverableCount), Integer.valueOf(value.missingNodeCount)));
        workspaceState.setText(value.workspaceDirty
            ? value.saveFailed ? TextUtils.getText("graph_workspace.status.workspace.save_failed")
                : TextUtils.getText("graph_workspace.status.workspace.dirty")
            : TextUtils.getText("graph_workspace.status.workspace.saved"));
        sourceMapState.setText(sourceMapText(value));
        historyState.setText(TextUtils.getText(value.workspaceHistoryAvailable()
            ? "graph_workspace.status.history.available" : "graph_workspace.status.history.empty"));
        nodeWarning.setText(TextUtils.getText("graph_workspace.warning.nodes"));
        edgeWarning.setText(TextUtils.getText("graph_workspace.warning.edges"));
        nodeWarning.setVisible(value.projectedNodeCount >= NODE_WARNING_THRESHOLD);
        edgeWarning.setVisible(value.projectedEdgeCount >= EDGE_WARNING_THRESHOLD);
        retrySaveButton.setEnabled(!value.readOnly);
        unpinAllButton.setEnabled(!value.readOnly);
        restartLayoutButton.setEnabled(true);
    }

    void setReadOnly(final boolean readOnly) {
        if (status == null) {
            return;
        }
        setStatus(new Status(status.mapStatuses, status.projectedNodeCount, status.projectedEdgeCount,
            status.selectedEndpointText, status.layoutStatus, status.recoverableCount, status.missingNodeCount,
            status.workspaceDirty, status.saveFailed, status.dirtySourceMapNames, status.dirtySourceMapCount,
            status.workspaceUndoAvailable, status.workspaceRedoAvailable, status.sourceMapUndoAvailable, readOnly));
    }

    Status status() {
        return status;
    }

    JLabel mapAvailabilityLabel() {
        return mapAvailability;
    }

    JLabel projectedCountsLabel() {
        return projectedCounts;
    }

    JLabel selectedEndpointLabel() {
        return selectedEndpoint;
    }

    JLabel layoutStateLabel() {
        return layoutState;
    }

    JLabel unresolvedCountsLabel() {
        return unresolvedCounts;
    }

    JLabel workspaceStateLabel() {
        return workspaceState;
    }

    JLabel sourceMapStateLabel() {
        return sourceMapState;
    }

    JLabel historyStateLabel() {
        return historyState;
    }

    JLabel nodeWarning() {
        return nodeWarning;
    }

    JLabel edgeWarning() {
        return edgeWarning;
    }

    JButton retrySaveButton() {
        return retrySaveButton;
    }

    JButton restartLayoutButton() {
        return restartLayoutButton;
    }

    JButton unpinAllButton() {
        return unpinAllButton;
    }

    private void emit(final GraphCommand command) {
        commandSink.accept(Objects.requireNonNull(command, "command"));
    }

    private static String mapAvailabilityText(final Status value) {
        if (value.mapStatuses.isEmpty()) {
            return TextUtils.getText("graph_workspace.status.maps.none");
        }
        final StringBuilder result = new StringBuilder(TextUtils.getText("graph_workspace.status.maps.prefix"));
        for (int index = 0; index < value.mapStatuses.size(); index++) {
            if (index > 0) {
                result.append(", ");
            }
            final MapStatus map = value.mapStatuses.get(index);
            result.append(TextUtils.format("graph_workspace.status.map", map.displayName, TextUtils.getText(
                "graph_workspace.map.availability." + map.availability.name().toLowerCase(java.util.Locale.ROOT))));
        }
        return result.toString();
    }

    private static String sourceMapText(final Status value) {
        if (value.dirtySourceMapCount == 0) {
            return TextUtils.getText("graph_workspace.status.source_maps.clean");
        }
        if (value.dirtySourceMapNames.isEmpty()) {
            return TextUtils.format("graph_workspace.status.source_maps.dirty_count",
                Integer.valueOf(value.dirtySourceMapCount));
        }
        return TextUtils.format("graph_workspace.status.source_maps.dirty_names",
            Integer.valueOf(value.dirtySourceMapCount), join(value.dirtySourceMapNames));
    }

    private static String join(final List<String> values) {
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                result.append(", ");
            }
            result.append(values.get(index));
        }
        return result.toString();
    }

    private static JLabel label(final String name) {
        final JLabel result = new JLabel();
        result.setName("graph-workspace-status-" + name);
        return result;
    }

    private static JButton button(final String textKey, final String name) {
        final JButton result = new JButton(TextUtils.getText(textKey));
        result.setName("graph-workspace-status-" + name);
        result.setMargin(new Insets(1, 6, 1, 6));
        result.setFocusable(false);
        return result;
    }

    private static String requireText(final String value, final String name) {
        Objects.requireNonNull(value, name);
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return value;
    }
}
