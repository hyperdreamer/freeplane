package org.freeplane.plugin.graph.window;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

import org.freeplane.plugin.graph.command.GraphCommands;
import org.freeplane.plugin.graph.control.GraphWorkspaceHandle;
import org.freeplane.plugin.graph.workspace.model.DisplaySettings;
import org.freeplane.plugin.graph.workspace.model.DisplaySettings.CanvasTheme;

final class WorkspaceSettingsPanel extends JPanel {
    private static final int PANEL_WIDTH = 244;

    private final GraphWorkspaceHandle handle;
    private final JCheckBox showArrowheads = new JCheckBox("Show arrowheads");
    private final JComboBox<CanvasTheme> canvasTheme = new JComboBox<CanvasTheme>(CanvasTheme.values());
    private final JCheckBox rememberViewport = new JCheckBox("Remember viewport");
    private final JCheckBox dimUnrelated = new JCheckBox("Dim unrelated nodes");
    private final Set<String> approvedSettingNames = Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(
        "show-arrowheads", "canvas-theme", "remember-viewport", "dim-unrelated")));
    private DisplaySettings settings;
    private boolean readOnly;
    private boolean updating;

    WorkspaceSettingsPanel(final GraphWorkspaceHandle handle, final DisplaySettings initialSettings) {
        this.handle = Objects.requireNonNull(handle, "handle");
        setName("graph-workspace-settings");
        setLayout(new BorderLayout(0, 8));
        setBorder(new EmptyBorder(8, 8, 8, 8));
        setPreferredSize(new Dimension(PANEL_WIDTH, 0));
        setMinimumSize(new Dimension(PANEL_WIDTH, 0));

        final JLabel heading = new JLabel("Display", SwingConstants.LEADING);
        heading.setName("graph-workspace-settings-heading");
        add(heading, BorderLayout.NORTH);

        final JPanel controls = new JPanel(new GridLayout(0, 1, 0, 6));
        controls.setName("graph-workspace-settings-controls");
        showArrowheads.setName("graph-workspace-show-arrowheads");
        canvasTheme.setName("graph-workspace-canvas-theme");
        rememberViewport.setName("graph-workspace-remember-viewport");
        dimUnrelated.setName("graph-workspace-dim-unrelated");
        final JPanel themeRow = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
        themeRow.add(new JLabel("Canvas theme"));
        themeRow.add(canvasTheme);
        controls.add(showArrowheads);
        controls.add(themeRow);
        controls.add(rememberViewport);
        controls.add(dimUnrelated);
        add(controls, BorderLayout.CENTER);

        showArrowheads.addActionListener(event -> publishSettings());
        canvasTheme.addActionListener(event -> publishSettings());
        rememberViewport.addActionListener(event -> publishSettings());
        dimUnrelated.addActionListener(event -> publishSettings());
        setSettings(Objects.requireNonNull(initialSettings, "initialSettings"));
    }

    void setSettings(final DisplaySettings value) {
        settings = Objects.requireNonNull(value, "settings");
        updating = true;
        try {
            showArrowheads.setSelected(settings.showArrowheads());
            canvasTheme.setSelectedItem(settings.canvasTheme());
            rememberViewport.setSelected(settings.rememberViewport());
            dimUnrelated.setSelected(settings.dimUnrelatedNodes());
        }
        finally {
            updating = false;
        }
    }

    DisplaySettings settings() {
        return settings;
    }

    Set<String> approvedSettingNames() {
        return approvedSettingNames;
    }

    JCheckBox showArrowheads() {
        return showArrowheads;
    }

    JComboBox<CanvasTheme> canvasTheme() {
        return canvasTheme;
    }

    JCheckBox rememberViewport() {
        return rememberViewport;
    }

    JCheckBox dimUnrelated() {
        return dimUnrelated;
    }

    void setReadOnly(final boolean value) {
        readOnly = value;
        showArrowheads.setEnabled(!value);
        canvasTheme.setEnabled(!value);
        rememberViewport.setEnabled(!value);
        dimUnrelated.setEnabled(!value);
    }

    boolean isReadOnly() {
        return readOnly;
    }

    private void publishSettings() {
        if (updating || readOnly) {
            return;
        }
        settings = DisplaySettings.of(showArrowheads.isSelected(),
            (CanvasTheme) canvasTheme.getSelectedItem(), rememberViewport.isSelected(),
            dimUnrelated.isSelected(), settings.unknownXml());
        handle.execute(GraphCommands.display(settings));
    }
}
