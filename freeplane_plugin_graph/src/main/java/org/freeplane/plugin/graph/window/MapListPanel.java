package org.freeplane.plugin.graph.window;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Insets;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

import org.freeplane.core.util.TextUtils;
import org.freeplane.plugin.graph.command.GraphCommand;
import org.freeplane.plugin.graph.command.GraphCommands;
import org.freeplane.plugin.graph.control.GraphWorkspaceHandle;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;

final class MapListPanel extends JPanel {
    static final int ROW_HEIGHT = 52;
    private static final int PANEL_WIDTH = 264;

    @FunctionalInterface
    interface DeleteConfirmationPrompt {
        boolean confirmDelete(Component parent, String mapDisplayName);
    }

    enum RowState {
        ACTIVE,
        LOADING,
        MISSING,
        READ_ONLY,
        RETRYABLE,
        INACTIVE
    }

    static final class MapRow {
        private final MapReferenceId mapReferenceId;
        private final String displayName;
        private final RowState state;
        private final MapPartition partition;
        private final int projectedNodeCount;
        private final boolean selected;

        private MapRow(final MapReferenceId mapReferenceId, final String displayName, final RowState state,
                final MapPartition partition, final int projectedNodeCount, final boolean selected) {
            this.mapReferenceId = Objects.requireNonNull(mapReferenceId, "mapReferenceId");
            this.displayName = requireText(displayName, "displayName");
            this.state = Objects.requireNonNull(state, "state");
            this.partition = Objects.requireNonNull(partition, "partition");
            if (projectedNodeCount < 0) {
                throw new IllegalArgumentException("Projected node count must be nonnegative");
            }
            this.projectedNodeCount = projectedNodeCount;
            this.selected = selected;
        }

        static MapRow of(final MapReferenceId mapReferenceId, final String displayName, final RowState state,
                final MapPartition partition, final int projectedNodeCount, final boolean selected) {
            return new MapRow(mapReferenceId, displayName, state, partition, projectedNodeCount, selected);
        }

        static MapRow of(final MapReferenceId mapReferenceId, final String displayName, final RowState state,
                final int projectedNodeCount, final boolean selected) {
            final MapPartition inferred = state == RowState.INACTIVE
                ? MapPartition.INACTIVE : MapPartition.ACTIVE;
            return of(mapReferenceId, displayName, state, inferred, projectedNodeCount, selected);
        }

        MapReferenceId mapReferenceId() {
            return mapReferenceId;
        }

        String displayName() {
            return displayName;
        }

        RowState state() {
            return state;
        }

        MapPartition partition() {
            return partition;
        }

        int projectedNodeCount() {
            return projectedNodeCount;
        }

        boolean selected() {
            return selected;
        }

        MapRow withSelected(final boolean value) {
            return new MapRow(mapReferenceId, displayName, state, partition, projectedNodeCount, value);
        }

        private static String requireText(final String value, final String name) {
            Objects.requireNonNull(value, name);
            if (value.trim().isEmpty()) {
                throw new IllegalArgumentException(name + " must not be empty");
            }
            return value;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private static final class ScrollableListContainer extends JPanel implements javax.swing.Scrollable {
        private static final long serialVersionUID = 1L;

        private ScrollableListContainer() {
            setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS));
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(final java.awt.Rectangle visibleRect, final int orientation, final int direction) {
            return ROW_HEIGHT / 2;
        }

        @Override
        public int getScrollableBlockIncrement(final java.awt.Rectangle visibleRect, final int orientation, final int direction) {
            return ROW_HEIGHT * 2;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private final GraphWorkspaceHandle handle;
    private final Supplier<Path> pathChooser;
    private final DefaultListModel<MapRow> activeModel = new DefaultListModel<MapRow>();
    private final JList<MapRow> activeList = new JList<MapRow>(activeModel);
    private final DefaultListModel<MapRow> inactiveModel = new DefaultListModel<MapRow>();
    private final JList<MapRow> inactiveList = new JList<MapRow>(inactiveModel);
    private final JLabel activeHeader = new JLabel();
    private final JLabel inactiveHeader = new JLabel();
    private final JButton actionButton1 = button("graph_workspace.action.add_map", "add-map");
    private final JButton actionButton2 = button("graph_workspace.action.deactivate_map", "remove-map");
    private final JButton retryButton = button("graph_workspace.action.retry_map", "retry-map");
    private final JButton locateButton = button("graph_workspace.action.locate_map", "locate-map");
    private final DeleteConfirmationPrompt deletePrompt;
    private final List<java.util.function.Consumer<MapRow>> selectionListeners =
        new ArrayList<java.util.function.Consumer<MapRow>>();
    private MapReferenceId pendingSelectionId;
    private List<MapRow> rows = Collections.emptyList();
    private boolean readOnly;
    private boolean updatingSelection;

    MapListPanel(final GraphWorkspaceHandle handle, final Supplier<Path> pathChooser) {
        this(handle, pathChooser, defaultDeleteConfirmationPrompt());
    }

    MapListPanel(final GraphWorkspaceHandle handle, final Supplier<Path> pathChooser,
            final DeleteConfirmationPrompt deletePrompt) {
        this.handle = Objects.requireNonNull(handle, "handle");
        this.pathChooser = Objects.requireNonNull(pathChooser, "pathChooser");
        this.deletePrompt = Objects.requireNonNull(deletePrompt, "deletePrompt");
        setName("graph-workspace-map-list");
        setLayout(new BorderLayout(0, 4));
        setBorder(new EmptyBorder(6, 6, 6, 6));
        setPreferredSize(new Dimension(PANEL_WIDTH, 0));
        setMinimumSize(new Dimension(PANEL_WIDTH, 0));

        final JLabel heading = new JLabel(TextUtils.getText("graph_workspace.map_list.heading"));
        heading.setName("graph-workspace-map-list-heading");
        heading.setBorder(new EmptyBorder(0, 2, 2, 2));
        add(heading, BorderLayout.NORTH);

        activeHeader.setName("graph-workspace-active-header");
        activeHeader.setBorder(new EmptyBorder(4, 2, 2, 2));
        activeHeader.setFont(activeHeader.getFont().deriveFont(Font.BOLD, 10f));
        activeHeader.setForeground(Color.GRAY);

        activeList.setName("graph-workspace-active-map-list");
        activeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        activeList.setFixedCellHeight(ROW_HEIGHT);
        activeList.setVisibleRowCount(0);
        activeList.setCellRenderer(new RowRenderer());
        activeList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !updatingSelection) {
                updatingSelection = true;
                try {
                    inactiveList.clearSelection();
                    synchronizeSelection();
                }
                finally {
                    updatingSelection = false;
                }
            }
        });

        final JPanel activeSection = new JPanel(new BorderLayout());
        activeSection.add(activeHeader, BorderLayout.NORTH);
        activeSection.add(activeList, BorderLayout.CENTER);

        inactiveHeader.setName("graph-workspace-inactive-header");
        inactiveHeader.setBorder(new EmptyBorder(8, 2, 2, 2));
        inactiveHeader.setFont(inactiveHeader.getFont().deriveFont(Font.BOLD, 10f));
        inactiveHeader.setForeground(Color.GRAY);

        inactiveList.setName("graph-workspace-inactive-map-list");
        inactiveList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        inactiveList.setFixedCellHeight(ROW_HEIGHT);
        inactiveList.setVisibleRowCount(0);
        inactiveList.setCellRenderer(new RowRenderer());
        inactiveList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !updatingSelection) {
                updatingSelection = true;
                try {
                    activeList.clearSelection();
                    synchronizeSelection();
                }
                finally {
                    updatingSelection = false;
                }
            }
        });

        final JPanel inactiveSection = new JPanel(new BorderLayout());
        inactiveSection.add(inactiveHeader, BorderLayout.NORTH);
        inactiveSection.add(inactiveList, BorderLayout.CENTER);

        final ScrollableListContainer listContainer = new ScrollableListContainer();
        listContainer.add(activeSection);
        listContainer.add(inactiveSection);

        final JScrollPane scrollPane = new JScrollPane(listContainer);
        scrollPane.setName("graph-workspace-map-list-scroll");
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);

        final JPanel actions = new JPanel(new GridLayout(2, 2, 4, 4));
        actions.setName("graph-workspace-map-list-actions");
        actions.setBorder(new EmptyBorder(2, 0, 0, 0));
        actions.add(actionButton1);
        actions.add(actionButton2);
        actions.add(retryButton);
        actions.add(locateButton);
        add(actions, BorderLayout.SOUTH);

        actionButton1.addActionListener(event -> handleActionButton1());
        actionButton2.addActionListener(event -> handleActionButton2());
        retryButton.addActionListener(event -> retrySelected());
        locateButton.addActionListener(event -> locateSelected());
        updateButtons();
    }

    private static DeleteConfirmationPrompt defaultDeleteConfirmationPrompt() {
        return (parent, mapDisplayName) -> {
            if (GraphicsEnvironment.isHeadless()) {
                return true;
            }
            final String title = TextUtils.getText("graph_workspace.dialog.delete_map.title");
            final String message = TextUtils.format("graph_workspace.dialog.delete_map.message", mapDisplayName);
            return javax.swing.JOptionPane.showConfirmDialog(parent, message, title,
                javax.swing.JOptionPane.YES_NO_OPTION) == javax.swing.JOptionPane.YES_OPTION;
        };
    }

    void setRows(final List<MapRow> values) {
        Objects.requireNonNull(values, "rows");
        final List<MapRow> copy = new ArrayList<MapRow>(values.size());
        for (final MapRow row : values) {
            copy.add(Objects.requireNonNull(row, "row"));
        }
        rows = Collections.unmodifiableList(copy);
        updatingSelection = true;
        try {
            activeModel.clear();
            inactiveModel.clear();
            int selectedActiveIndex = -1;
            int selectedInactiveIndex = -1;

            final MapReferenceId targetSelection = pendingSelectionId != null
                ? pendingSelectionId : (selectedRow() != null ? selectedRow().mapReferenceId() : null);

            for (final MapRow row : rows) {
                if (row.partition() == MapPartition.ACTIVE) {
                    activeModel.addElement(row);
                    if (targetSelection != null && targetSelection.equals(row.mapReferenceId())) {
                        selectedActiveIndex = activeModel.size() - 1;
                    }
                    else if (row.selected() && selectedActiveIndex < 0 && targetSelection == null) {
                        selectedActiveIndex = activeModel.size() - 1;
                    }
                }
                else {
                    inactiveModel.addElement(row);
                    if (targetSelection != null && targetSelection.equals(row.mapReferenceId())) {
                        selectedInactiveIndex = inactiveModel.size() - 1;
                    }
                    else if (row.selected() && selectedInactiveIndex < 0 && targetSelection == null) {
                        selectedInactiveIndex = inactiveModel.size() - 1;
                    }
                }
            }

            activeHeader.setText(TextUtils.format("graph_workspace.map_list.active_heading", activeModel.size()));
            inactiveHeader.setText(TextUtils.format("graph_workspace.map_list.inactive_heading", inactiveModel.size()));

            activeList.setPreferredSize(new Dimension(PANEL_WIDTH, activeModel.size() * ROW_HEIGHT));
            inactiveList.setPreferredSize(new Dimension(PANEL_WIDTH, inactiveModel.size() * ROW_HEIGHT));

            if (selectedActiveIndex >= 0) {
                activeList.setSelectedIndex(selectedActiveIndex);
                inactiveList.clearSelection();
            }
            else if (selectedInactiveIndex >= 0) {
                inactiveList.setSelectedIndex(selectedInactiveIndex);
                activeList.clearSelection();
            }
            else {
                activeList.clearSelection();
                inactiveList.clearSelection();
            }
            pendingSelectionId = null;
        }
        finally {
            updatingSelection = false;
        }
        synchronizeSelection();
    }

    List<MapRow> rows() {
        return rows;
    }

    void addSelectionListener(final java.util.function.Consumer<MapRow> listener) {
        selectionListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    void selectMap(final MapReferenceId mapReferenceId) {
        Objects.requireNonNull(mapReferenceId, "mapReferenceId");
        updatingSelection = true;
        try {
            for (int i = 0; i < activeModel.size(); i++) {
                if (mapReferenceId.equals(activeModel.get(i).mapReferenceId())) {
                    activeList.setSelectedIndex(i);
                    inactiveList.clearSelection();
                    return;
                }
            }
            for (int i = 0; i < inactiveModel.size(); i++) {
                if (mapReferenceId.equals(inactiveModel.get(i).mapReferenceId())) {
                    inactiveList.setSelectedIndex(i);
                    activeList.clearSelection();
                    return;
                }
            }
            activeList.clearSelection();
            inactiveList.clearSelection();
        }
        finally {
            updatingSelection = false;
            synchronizeSelection();
        }
    }

    MapRow selectedRow() {
        if (activeList.getSelectedValue() != null) {
            return activeList.getSelectedValue();
        }
        return inactiveList.getSelectedValue();
    }

    JList<MapRow> activeList() {
        return activeList;
    }

    JList<MapRow> inactiveList() {
        return inactiveList;
    }

    JList<MapRow> rowList() {
        return activeList;
    }

    JButton addButton() {
        return actionButton1;
    }

    JButton removeButton() {
        return actionButton2;
    }

    JButton retryButton() {
        return retryButton;
    }

    JButton locateButton() {
        return locateButton;
    }

    int rowHeight() {
        return ROW_HEIGHT;
    }

    void setReadOnly(final boolean value) {
        readOnly = value;
        updateButtons();
    }

    boolean isReadOnly() {
        return readOnly;
    }

    void addMapFromChooser() {
        final Path path = pathChooser.get();
        if (path == null || readOnly) {
            return;
        }
        execute(GraphCommands.addMap(MapReferenceId.of(UUID.randomUUID()), path.toUri()));
    }

    void deactivateSelected() {
        final MapRow row = selectedRow();
        if (row != null && !readOnly && row.partition() == MapPartition.ACTIVE) {
            pendingSelectionId = row.mapReferenceId();
            execute(GraphCommands.removeMap(row.mapReferenceId()));
        }
    }

    void reactivateSelected() {
        final MapRow row = selectedRow();
        if (row != null && !readOnly && row.partition() == MapPartition.INACTIVE) {
            pendingSelectionId = row.mapReferenceId();
            execute(GraphCommands.reactivateMap(row.mapReferenceId()));
        }
    }

    void deleteSelected() {
        final MapRow row = selectedRow();
        if (row != null && !readOnly && row.partition() == MapPartition.INACTIVE) {
            if (deletePrompt.confirmDelete(this, row.displayName())) {
                pendingSelectionId = null;
                execute(GraphCommands.deleteMap(row.mapReferenceId()));
            }
        }
    }

    void retrySelected() {
        final MapRow row = selectedRow();
        if (row != null && !readOnly && row.partition() == MapPartition.ACTIVE && row.state() == RowState.RETRYABLE) {
            execute(GraphCommands.retryMap(row.mapReferenceId()));
        }
    }

    void locateSelected() {
        final MapRow row = selectedRow();
        final Path path = pathChooser.get();
        if (row != null && path != null && !readOnly) {
            execute(GraphCommands.locateMap(row.mapReferenceId(), path.toUri()));
        }
    }

    private void handleActionButton1() {
        final MapRow row = selectedRow();
        if (row != null && row.partition() == MapPartition.INACTIVE) {
            reactivateSelected();
        }
        else {
            addMapFromChooser();
        }
    }

    private void handleActionButton2() {
        final MapRow row = selectedRow();
        if (row != null && row.partition() == MapPartition.INACTIVE) {
            deleteSelected();
        }
        else {
            deactivateSelected();
        }
    }

    private void execute(final GraphCommand command) {
        handle.execute(Objects.requireNonNull(command, "command"));
    }

    private void synchronizeSelection() {
        final MapRow selected = selectedRow();
        final List<MapRow> next = new ArrayList<MapRow>(rows.size());
        for (final MapRow row : rows) {
            next.add(row.withSelected(selected != null && row.mapReferenceId().equals(selected.mapReferenceId())));
        }
        rows = Collections.unmodifiableList(next);
        updateButtons();
        for (final java.util.function.Consumer<MapRow> listener : selectionListeners) {
            listener.accept(selected);
        }
    }

    private void updateButtons() {
        final MapRow selected = selectedRow();
        if (selected == null || selected.partition() == MapPartition.ACTIVE) {
            actionButton1.setText(TextUtils.getText("graph_workspace.action.add_map"));
            actionButton1.setEnabled(!readOnly);
            actionButton2.setText(TextUtils.getText("graph_workspace.action.deactivate_map"));
            actionButton2.setEnabled(!readOnly && selected != null && selected.state() != RowState.READ_ONLY);
            retryButton.setEnabled(!readOnly && selected != null && selected.state() == RowState.RETRYABLE);
        }
        else {
            actionButton1.setText(TextUtils.getText("graph_workspace.action.reactivate_map"));
            actionButton1.setEnabled(!readOnly && selected.state() != RowState.READ_ONLY);
            actionButton2.setText(TextUtils.getText("graph_workspace.action.delete_map"));
            actionButton2.setEnabled(!readOnly && selected.state() != RowState.READ_ONLY);
            retryButton.setEnabled(false);
        }
        locateButton.setEnabled(!readOnly && selected != null
            && (selected.state() == RowState.MISSING || selected.state() == RowState.RETRYABLE));
    }

    private static JButton button(final String textKey, final String name) {
        final JButton button = new JButton(TextUtils.getText(textKey));
        button.setName("graph-workspace-" + name);
        button.setMargin(new Insets(2, 7, 2, 7));
        button.setFocusable(false);
        return button;
    }

    private static final class RowRenderer extends JPanel implements javax.swing.ListCellRenderer<MapRow> {
        private final JLabel color = new JLabel();
        private final JLabel name = new JLabel();
        private final JLabel status = new JLabel();
        private final JLabel count = new JLabel();

        private RowRenderer() {
            super(new BorderLayout(5, 0));
            setOpaque(true);
            setBorder(new EmptyBorder(5, 7, 5, 7));
            color.setPreferredSize(new Dimension(5, 5));
            color.setOpaque(true);
            add(color, BorderLayout.WEST);
            final JPanel text = new JPanel(new GridLayout(2, 1, 0, 0));
            text.setOpaque(false);
            name.setFont(name.getFont().deriveFont(Font.PLAIN));
            status.setFont(status.getFont().deriveFont(Font.PLAIN, Math.max(10.0f, status.getFont().getSize2D() - 1.0f)));
            text.add(name);
            text.add(status);
            add(text, BorderLayout.CENTER);
            count.setHorizontalAlignment(SwingConstants.TRAILING);
            add(count, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(final JList<? extends MapRow> list, final MapRow value,
                final int index, final boolean selected, final boolean cellHasFocus) {
            final Color background = selected ? list.getSelectionBackground() : list.getBackground();
            final Color foreground = selected ? list.getSelectionForeground() : list.getForeground();
            setBackground(background);
            name.setForeground(foreground);
            status.setForeground(foreground);
            count.setForeground(foreground);
            color.setBackground(colorFor(value.state()));
            name.setText(value.displayName());
            status.setText(labelFor(value.state()));
            count.setText(TextUtils.format("graph_workspace.map_list.node_count", Integer.valueOf(
                value.projectedNodeCount())));
            setPreferredSize(new Dimension(PANEL_WIDTH - 24, ROW_HEIGHT));
            return this;
        }

        private static String labelFor(final RowState state) {
            return TextUtils.getText("graph_workspace.map.status." + state.name().toLowerCase(
                java.util.Locale.ROOT));
        }

        private static Color colorFor(final RowState state) {
            switch (state) {
            case ACTIVE:
                return new Color(78, 121, 167);
            case LOADING:
                return new Color(237, 201, 72);
            case MISSING:
                return new Color(225, 87, 89);
            case READ_ONLY:
                return new Color(155, 155, 155);
            case RETRYABLE:
                return new Color(242, 142, 43);
            case INACTIVE:
                return new Color(155, 155, 155);
            default:
                return Color.GRAY;
            }
        }
    }
}
