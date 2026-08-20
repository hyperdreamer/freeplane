package org.freeplane.plugin.graph.window;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JToggleButton;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.freeplane.plugin.graph.canvas.GraphCanvas;
import org.freeplane.plugin.graph.canvas.GraphInteractionController;
import org.freeplane.plugin.graph.canvas.InteractionTool;
import org.freeplane.plugin.graph.canvas.GraphViewport;
import org.freeplane.plugin.graph.command.GraphCommands;
import org.freeplane.plugin.graph.control.GraphWorkspaceController;
import org.freeplane.plugin.graph.control.GraphWorkspaceHandle;
import org.freeplane.plugin.graph.workspace.model.RelationshipDirection;

final class WorkspaceToolbar extends javax.swing.JPanel {
    private static final double ZOOM_FACTOR = 1.25;
    private static final Dimension PREFERRED_SIZE = new Dimension(0, 42);

    private final GraphWorkspaceController applicationController;
    private final GraphWorkspaceHandle handle;
    private final GraphCanvas canvas;
    private final Supplier<Path> pathChooser;
    private final JButton openButton = button("Open", "open");
    private final JButton saveButton = button("Save", "save");
    private final JButton saveAsButton = button("Save As", "save-as");
    private final JButton undoButton = button("Undo", "undo");
    private final JButton redoButton = button("Redo", "redo");
    private final JToggleButton selectButton = toggleButton("Select", "select");
    private final JToggleButton connectButton = toggleButton("Connect", "connect");
    private final JComboBox<RelationshipDirection> directionComboBox =
        new JComboBox<RelationshipDirection>(RelationshipDirection.values());
    private final JTextField searchField = new JTextField();
    private final JButton settingsButton = button("Settings", "settings");
    private final JButton zoomInButton = button("Zoom In", "zoom-in");
    private final JButton zoomOutButton = button("Zoom Out", "zoom-out");
    private final JButton fitGraphButton = button("Fit Graph", "fit-graph");
    private final JButton resetZoomButton = button("Reset Zoom", "reset-zoom");
    private final JButton pinButton = button("Pin", "pin");
    private final JButton unpinButton = button("Unpin", "unpin");
    private final Set<String> approvedControlNames = Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(
        "open", "save", "add-map", "remove-map", "select", "connect", "direction", "search", "settings",
        "zoom-in", "zoom-out", "fit-graph", "reset-zoom", "pin", "unpin")));
    private Consumer<String> searchListener = value -> { };
    private Consumer<GraphViewport> viewportListener = value -> { };
    private Consumer<InteractionTool> toolListener = value -> { };
    private Consumer<RelationshipDirection> directionListener = value -> { };
    private Runnable settingsAction = () -> { };
    private Runnable pinAction = () -> { };
    private Runnable unpinAction = () -> { };
    private GraphInteractionController interactionController;
    private boolean readOnly;

    WorkspaceToolbar(final GraphWorkspaceController applicationController, final GraphWorkspaceHandle handle,
            final GraphCanvas canvas, final Supplier<Path> pathChooser) {
        this.applicationController = Objects.requireNonNull(applicationController, "applicationController");
        this.handle = Objects.requireNonNull(handle, "handle");
        this.canvas = Objects.requireNonNull(canvas, "canvas");
        this.pathChooser = Objects.requireNonNull(pathChooser, "pathChooser");
        setName("graph-workspace-toolbar");
        setLayout(new FlowLayout(FlowLayout.LEADING, 3, 4));
        setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        setPreferredSize(PREFERRED_SIZE);
        setMinimumSize(new Dimension(0, PREFERRED_SIZE.height));

        selectButton.setSelected(true);
        final ButtonGroup tools = new ButtonGroup();
        tools.add(selectButton);
        tools.add(connectButton);
        directionComboBox.setName("graph-workspace-direction");
        directionComboBox.setToolTipText("Relationship direction");
        directionComboBox.setPreferredSize(new Dimension(128, 26));
        searchField.setName("graph-workspace-search");
        searchField.setToolTipText("Search graph");
        searchField.setPreferredSize(new Dimension(160, 26));
        settingsButton.setToolTipText("Display settings");

        add(openButton);
        add(saveButton);
        add(undoButton);
        add(redoButton);
        add(selectButton);
        add(connectButton);
        add(directionComboBox);
        add(searchField);
        add(settingsButton);
        add(zoomOutButton);
        add(zoomInButton);
        add(fitGraphButton);
        add(resetZoomButton);
        add(pinButton);
        add(unpinButton);

        openButton.addActionListener(event -> openWorkspace());
        saveButton.addActionListener(event -> execute(GraphCommands.save()));
        saveAsButton.addActionListener(event -> saveAsWorkspace());
        undoButton.addActionListener(event -> execute(GraphCommands.undoWorkspace()));
        redoButton.addActionListener(event -> execute(GraphCommands.redoWorkspace()));
        selectButton.addActionListener(event -> chooseTool(InteractionTool.SELECT));
        connectButton.addActionListener(event -> chooseTool(InteractionTool.CONNECT));
        directionComboBox.addActionListener(event -> directionListener.accept(selectedDirection()));
        settingsButton.addActionListener(event -> settingsAction.run());
        zoomInButton.addActionListener(event -> changeZoom(ZOOM_FACTOR));
        zoomOutButton.addActionListener(event -> changeZoom(1.0 / ZOOM_FACTOR));
        fitGraphButton.addActionListener(event -> fitGraph());
        resetZoomButton.addActionListener(event -> resetZoom());
        pinButton.addActionListener(event -> pinAction.run());
        unpinButton.addActionListener(event -> unpinAction.run());
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(final DocumentEvent event) {
                publishSearch();
            }

            @Override
            public void removeUpdate(final DocumentEvent event) {
                publishSearch();
            }

            @Override
            public void changedUpdate(final DocumentEvent event) {
                publishSearch();
            }
        });
        updateReadOnlyControls();
    }

    JButton openButton() {
        return openButton;
    }

    JButton saveButton() {
        return saveButton;
    }

    JButton saveAsButton() {
        return saveAsButton;
    }

    JButton undoButton() {
        return undoButton;
    }

    JButton redoButton() {
        return redoButton;
    }

    JToggleButton selectButton() {
        return selectButton;
    }

    JToggleButton connectButton() {
        return connectButton;
    }

    JComboBox<RelationshipDirection> directionComboBox() {
        return directionComboBox;
    }

    JTextField searchField() {
        return searchField;
    }

    JButton settingsButton() {
        return settingsButton;
    }

    JButton zoomInButton() {
        return zoomInButton;
    }

    JButton zoomOutButton() {
        return zoomOutButton;
    }

    JButton fitGraphButton() {
        return fitGraphButton;
    }

    JButton resetZoomButton() {
        return resetZoomButton;
    }

    JButton pinButton() {
        return pinButton;
    }

    JButton unpinButton() {
        return unpinButton;
    }

    Set<String> approvedControlNames() {
        return approvedControlNames;
    }

    void setInteractionController(final GraphInteractionController controller) {
        interactionController = controller;
    }

    void setSearchListener(final Consumer<String> listener) {
        searchListener = Objects.requireNonNull(listener, "listener");
    }

    void setViewportListener(final Consumer<GraphViewport> listener) {
        viewportListener = Objects.requireNonNull(listener, "listener");
    }

    void setToolListener(final Consumer<InteractionTool> listener) {
        toolListener = Objects.requireNonNull(listener, "listener");
    }

    void setDirectionListener(final Consumer<RelationshipDirection> listener) {
        directionListener = Objects.requireNonNull(listener, "listener");
    }

    void setSettingsAction(final Runnable action) {
        settingsAction = Objects.requireNonNull(action, "action");
    }

    void setPinAction(final Runnable action) {
        pinAction = Objects.requireNonNull(action, "action");
    }

    void setUnpinAction(final Runnable action) {
        unpinAction = Objects.requireNonNull(action, "action");
    }

    void setReadOnly(final boolean value) {
        readOnly = value;
        updateReadOnlyControls();
    }

    boolean isReadOnly() {
        return readOnly;
    }

    private void openWorkspace() {
        final Path path = pathChooser.get();
        if (path != null) {
            applicationController.open(path);
        }
    }

    private void saveAsWorkspace() {
        final Path path = pathChooser.get();
        if (path != null && !readOnly) {
            execute(GraphCommands.saveAs(path));
        }
    }

    private void chooseTool(final InteractionTool tool) {
        if (readOnly && tool == InteractionTool.CONNECT) {
            selectButton.setSelected(true);
            return;
        }
        if (interactionController != null) {
            interactionController.setTool(tool);
        }
        toolListener.accept(tool);
    }

    private RelationshipDirection selectedDirection() {
        return (RelationshipDirection) directionComboBox.getSelectedItem();
    }

    private void changeZoom(final double factor) {
        final GraphViewport current = canvas.viewport();
        canvas.setViewport(GraphViewport.of(current.centerX(), current.centerY(), current.zoom() * factor));
        viewportListener.accept(canvas.viewport());
    }

    private void fitGraph() {
        canvas.fitGraph();
        viewportListener.accept(canvas.viewport());
    }

    private void resetZoom() {
        canvas.resetZoom();
        viewportListener.accept(canvas.viewport());
    }

    private void publishSearch() {
        searchListener.accept(searchField.getText());
    }

    private void execute(final org.freeplane.plugin.graph.command.GraphCommand command) {
        if (!readOnly) {
            handle.execute(Objects.requireNonNull(command, "command"));
        }
    }

    private void updateReadOnlyControls() {
        saveButton.setEnabled(!readOnly);
        saveAsButton.setEnabled(!readOnly);
        undoButton.setEnabled(!readOnly);
        redoButton.setEnabled(!readOnly);
        connectButton.setEnabled(!readOnly);
        settingsButton.setEnabled(!readOnly);
        pinButton.setEnabled(!readOnly);
        unpinButton.setEnabled(!readOnly);
        directionComboBox.setEnabled(!readOnly);
    }

    private static JButton button(final String text, final String name) {
        final JButton button = new JButton(text);
        configure(button, name);
        return button;
    }

    private static JToggleButton toggleButton(final String text, final String name) {
        final JToggleButton button = new JToggleButton(text);
        configure(button, name);
        return button;
    }

    private static void configure(final AbstractButton button, final String name) {
        button.setName("graph-workspace-" + name);
        button.setMargin(new Insets(2, 7, 2, 7));
        button.setFocusable(false);
    }
}
