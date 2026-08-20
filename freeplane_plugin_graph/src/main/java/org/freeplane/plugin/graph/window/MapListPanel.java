package org.freeplane.plugin.graph.window;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
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

import org.freeplane.plugin.graph.command.GraphCommand;
import org.freeplane.plugin.graph.command.GraphCommands;
import org.freeplane.plugin.graph.control.GraphWorkspaceHandle;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;

final class MapListPanel extends JPanel {
    static final int ROW_HEIGHT = 52;
    private static final int PANEL_WIDTH = 264;

    enum RowState {
        ACTIVE,
        LOADING,
        MISSING,
        READ_ONLY,
        RETRYABLE
    }

    static final class MapRow {
        private final MapReferenceId mapReferenceId;
        private final String displayName;
        private final RowState state;
        private final int projectedNodeCount;
        private final boolean selected;

        private MapRow(final MapReferenceId mapReferenceId, final String displayName, final RowState state,
                final int projectedNodeCount, final boolean selected) {
            this.mapReferenceId = Objects.requireNonNull(mapReferenceId, "mapReferenceId");
            this.displayName = requireText(displayName, "displayName");
            this.state = Objects.requireNonNull(state, "state");
            if (projectedNodeCount < 0) {
                throw new IllegalArgumentException("Projected node count must be nonnegative");
            }
            this.projectedNodeCount = projectedNodeCount;
            this.selected = selected;
        }

        static MapRow of(final MapReferenceId mapReferenceId, final String displayName, final RowState state,
                final int projectedNodeCount, final boolean selected) {
            return new MapRow(mapReferenceId, displayName, state, projectedNodeCount, selected);
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

        int projectedNodeCount() {
            return projectedNodeCount;
        }

        boolean selected() {
            return selected;
        }

        MapRow withSelected(final boolean value) {
            return new MapRow(mapReferenceId, displayName, state, projectedNodeCount, value);
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

    private final GraphWorkspaceHandle handle;
    private final Supplier<Path> pathChooser;
    private final DefaultListModel<MapRow> model = new DefaultListModel<MapRow>();
    private final JList<MapRow> list = new JList<MapRow>(model);
    private final JButton addButton = button("Add", "add-map");
    private final JButton removeButton = button("Remove", "remove-map");
    private final JButton retryButton = button("Retry", "retry-map");
    private final JButton locateButton = button("Locate", "locate-map");
    private List<MapRow> rows = Collections.emptyList();
    private boolean readOnly;
    private boolean updatingSelection;

    MapListPanel(final GraphWorkspaceHandle handle, final Supplier<Path> pathChooser) {
        this.handle = Objects.requireNonNull(handle, "handle");
        this.pathChooser = Objects.requireNonNull(pathChooser, "pathChooser");
        setName("graph-workspace-map-list");
        setLayout(new BorderLayout(0, 4));
        setBorder(new EmptyBorder(6, 6, 6, 6));
        setPreferredSize(new Dimension(PANEL_WIDTH, 0));
        setMinimumSize(new Dimension(PANEL_WIDTH, 0));

        final JLabel heading = new JLabel("Maps");
        heading.setName("graph-workspace-map-list-heading");
        heading.setBorder(new EmptyBorder(0, 2, 2, 2));
        add(heading, BorderLayout.NORTH);

        list.setName("graph-workspace-map-list-rows");
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFixedCellHeight(ROW_HEIGHT);
        list.setVisibleRowCount(8);
        list.setCellRenderer(new RowRenderer());
        list.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !updatingSelection) {
                synchronizeSelection();
            }
        });
        add(new JScrollPane(list), BorderLayout.CENTER);

        final JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEADING, 3, 0));
        actions.setName("graph-workspace-map-list-actions");
        actions.setBorder(new EmptyBorder(2, 0, 0, 0));
        actions.add(addButton);
        actions.add(removeButton);
        actions.add(retryButton);
        actions.add(locateButton);
        add(actions, BorderLayout.SOUTH);

        addButton.addActionListener(event -> addMapFromChooser());
        removeButton.addActionListener(event -> removeSelected());
        retryButton.addActionListener(event -> retrySelected());
        locateButton.addActionListener(event -> locateSelected());
        updateButtons();
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
            model.clear();
            int selectedIndex = -1;
            for (int index = 0; index < rows.size(); index++) {
                final MapRow row = rows.get(index);
                model.addElement(row);
                if (row.selected() && selectedIndex < 0) {
                    selectedIndex = index;
                }
            }
            if (selectedIndex >= 0) {
                list.setSelectedIndex(selectedIndex);
            }
            else {
                list.clearSelection();
            }
        }
        finally {
            updatingSelection = false;
        }
        updateButtons();
    }

    List<MapRow> rows() {
        return rows;
    }

    void selectMap(final MapReferenceId mapReferenceId) {
        Objects.requireNonNull(mapReferenceId, "mapReferenceId");
        for (int index = 0; index < model.size(); index++) {
            if (mapReferenceId.equals(model.get(index).mapReferenceId())) {
                list.setSelectedIndex(index);
                return;
            }
        }
        list.clearSelection();
    }

    MapRow selectedRow() {
        return list.getSelectedValue();
    }

    JList<MapRow> rowList() {
        return list;
    }

    JButton addButton() {
        return addButton;
    }

    JButton removeButton() {
        return removeButton;
    }

    JButton retryButton() {
        return retryButton;
    }

    JButton locateButton() {
        return locateButton;
    }

    int rowHeight() {
        return list.getFixedCellHeight();
    }

    void setReadOnly(final boolean value) {
        readOnly = value;
        updateButtons();
    }

    boolean isReadOnly() {
        return readOnly;
    }

    private void addMapFromChooser() {
        final Path path = pathChooser.get();
        if (path == null || readOnly) {
            return;
        }
        execute(GraphCommands.addMap(MapReferenceId.of(UUID.randomUUID()), path.toUri()));
    }

    private void removeSelected() {
        final MapRow row = selectedRow();
        if (row != null && !readOnly) {
            execute(GraphCommands.removeMap(row.mapReferenceId()));
        }
    }

    private void retrySelected() {
        final MapRow row = selectedRow();
        if (row != null && !readOnly && row.state() == RowState.RETRYABLE) {
            execute(GraphCommands.retryMap(row.mapReferenceId()));
        }
    }

    private void locateSelected() {
        final MapRow row = selectedRow();
        final Path path = pathChooser.get();
        if (row != null && path != null && !readOnly) {
            execute(GraphCommands.locateMap(row.mapReferenceId(), path.toUri()));
        }
    }

    private void execute(final GraphCommand command) {
        handle.execute(Objects.requireNonNull(command, "command"));
    }

    private void synchronizeSelection() {
        final MapRow selected = list.getSelectedValue();
        final List<MapRow> next = new ArrayList<MapRow>(rows.size());
        for (final MapRow row : rows) {
            next.add(row.withSelected(selected != null && row.mapReferenceId().equals(selected.mapReferenceId())));
        }
        rows = Collections.unmodifiableList(next);
        updateButtons();
    }

    private void updateButtons() {
        final MapRow selected = selectedRow();
        addButton.setEnabled(!readOnly);
        removeButton.setEnabled(!readOnly && selected != null && selected.state() != RowState.READ_ONLY);
        retryButton.setEnabled(!readOnly && selected != null && selected.state() == RowState.RETRYABLE);
        locateButton.setEnabled(!readOnly && selected != null
            && (selected.state() == RowState.MISSING || selected.state() == RowState.RETRYABLE));
    }

    private static JButton button(final String text, final String name) {
        final JButton button = new JButton(text);
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
            count.setText(Integer.toString(value.projectedNodeCount()));
            setPreferredSize(new Dimension(PANEL_WIDTH - 24, ROW_HEIGHT));
            return this;
        }

        private static String labelFor(final RowState state) {
            switch (state) {
            case ACTIVE:
                return "Active";
            case LOADING:
                return "Loading";
            case MISSING:
                return "Missing";
            case READ_ONLY:
                return "Read only";
            case RETRYABLE:
                return "Retry available";
            default:
                throw new IllegalArgumentException("Unknown map row state");
            }
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
            default:
                return Color.GRAY;
            }
        }
    }
}
