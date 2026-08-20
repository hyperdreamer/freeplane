package org.freeplane.plugin.graph.window;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.freeplane.core.util.TextUtils;
import org.freeplane.plugin.graph.command.GraphCommand;
import org.freeplane.plugin.graph.command.GraphCommands;
import org.freeplane.plugin.graph.control.CanvasState;
import org.freeplane.plugin.graph.control.GraphWorkspaceViewBinding;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.RelationshipResolution;
import org.freeplane.plugin.graph.projection.RelationshipStatus;
import org.freeplane.plugin.graph.workspace.model.RelationshipId;

final class PurgeConfirmationDialog extends JPanel {
    private static final long serialVersionUID = 1L;

    static final class MissingRow {
        private final RelationshipId relationshipId;
        private final String sourceDescription;
        private final String targetDescription;

        private MissingRow(final RelationshipId relationshipId, final String sourceDescription,
                final String targetDescription) {
            this.relationshipId = Objects.requireNonNull(relationshipId, "relationshipId");
            this.sourceDescription = requireText(sourceDescription, "sourceDescription");
            this.targetDescription = requireText(targetDescription, "targetDescription");
        }

        static MissingRow of(final RelationshipId relationshipId, final String sourceDescription,
                final String targetDescription) {
            return new MissingRow(relationshipId, sourceDescription, targetDescription);
        }

        RelationshipId relationshipId() {
            return relationshipId;
        }

        String sourceDescription() {
            return sourceDescription;
        }

        String targetDescription() {
            return targetDescription;
        }

        @Override
        public boolean equals(final Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof MissingRow)) {
                return false;
            }
            final MissingRow that = (MissingRow) other;
            return relationshipId.equals(that.relationshipId)
                && sourceDescription.equals(that.sourceDescription)
                && targetDescription.equals(that.targetDescription);
        }

        @Override
        public int hashCode() {
            return Objects.hash(relationshipId, sourceDescription, targetDescription);
        }
    }

    private final long displayedGeneration;
    private final List<MissingRow> rows;
    private final Consumer<GraphCommand> commandSink;
    private final JButton purgeButton = button("graph_workspace.action.purge", "purge");
    private boolean readOnly;

    PurgeConfirmationDialog(final long displayedGeneration, final List<MissingRow> rows,
            final Consumer<GraphCommand> commandSink) {
        super(new BorderLayout(0, 4));
        if (displayedGeneration < 0L) {
            throw new IllegalArgumentException("displayedGeneration must be nonnegative");
        }
        this.displayedGeneration = displayedGeneration;
        Objects.requireNonNull(rows, "rows");
        final List<MissingRow> copy = new ArrayList<MissingRow>(rows.size());
        for (final MissingRow row : rows) {
            copy.add(Objects.requireNonNull(row, "row"));
        }
        this.rows = Collections.unmodifiableList(copy);
        this.commandSink = Objects.requireNonNull(commandSink, "commandSink");
        setName("graph-workspace-purge-confirmation");
        setPreferredSize(new Dimension(420, 180));
        add(new JLabel(TextUtils.format("graph_workspace.purge.heading", Integer.valueOf(this.rows.size()))),
            BorderLayout.NORTH);
        final JPanel rowPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 3, 2));
        rowPanel.setName("graph-workspace-purge-rows");
        for (final MissingRow row : this.rows) {
            rowPanel.add(new JLabel(TextUtils.format("graph_workspace.purge.relationship",
                row.sourceDescription(), row.targetDescription())));
        }
        add(new JScrollPane(rowPanel), BorderLayout.CENTER);
        purgeButton.addActionListener(event -> purge());
        add(purgeButton, BorderLayout.SOUTH);
        updateButton();
    }

    PurgeConfirmationDialog(final CanvasState state, final List<GraphWorkspaceViewBinding.MapRegistration> mapRows,
            final Consumer<GraphCommand> commandSink) {
        this(Objects.requireNonNull(state, "state").generation(), state.projection(), mapRows, commandSink);
    }

    PurgeConfirmationDialog(final long displayedGeneration, final GraphProjection projection,
            final List<GraphWorkspaceViewBinding.MapRegistration> mapRows,
            final Consumer<GraphCommand> commandSink) {
        this(displayedGeneration, missingRows(Objects.requireNonNull(projection, "projection"),
            ContributorInspector.mapNames(mapRows)), commandSink);
    }

    static PurgeConfirmationDialog from(final CanvasState state,
            final List<GraphWorkspaceViewBinding.MapRegistration> mapRows,
            final Consumer<GraphCommand> commandSink) {
        return new PurgeConfirmationDialog(state, mapRows, commandSink);
    }

    long displayedGeneration() {
        return displayedGeneration;
    }

    List<MissingRow> rows() {
        return rows;
    }

    JButton purgeButton() {
        return purgeButton;
    }

    boolean isEmpty() {
        return rows.isEmpty();
    }

    void setReadOnly(final boolean value) {
        readOnly = value;
        updateButton();
    }

    boolean isReadOnly() {
        return readOnly;
    }

    void purge() {
        if (readOnly || rows.isEmpty()) {
            return;
        }
        final Set<RelationshipId> ids = new LinkedHashSet<RelationshipId>();
        for (final MissingRow row : rows) {
            ids.add(row.relationshipId);
        }
        commandSink.accept(GraphCommands.purge(displayedGeneration, ids));
    }

    private void updateButton() {
        purgeButton.setEnabled(!readOnly && !rows.isEmpty());
    }

    private static List<MissingRow> missingRows(final GraphProjection projection,
            final Map<org.freeplane.plugin.graph.workspace.model.MapReferenceId, String> mapNames) {
        final List<MissingRow> result = new ArrayList<MissingRow>();
        for (final RelationshipResolution resolution : projection.relationshipResolutions()) {
            if (resolution.status() != RelationshipStatus.UNRESOLVED_MISSING_NODE) {
                continue;
            }
            final org.freeplane.plugin.graph.workspace.model.GraphRelationshipRecord relationship =
                resolution.relationship();
            result.add(MissingRow.of(resolution.relationshipId(),
                GraphWindowEndpointLabels.description(projection, resolution.source(),
                    relationship.source().mapReferenceId(), mapNames),
                GraphWindowEndpointLabels.description(projection, resolution.target(),
                    relationship.target().mapReferenceId(), mapNames)));
        }
        return result;
    }

    private static JButton button(final String textKey, final String name) {
        final JButton result = new JButton(TextUtils.getText(textKey));
        result.setName("graph-workspace-purge-" + name);
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
