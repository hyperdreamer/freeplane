package org.freeplane.plugin.graph.window;

import java.awt.GridLayout;

import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

final class ToolSwitch extends JPanel {
    ToolSwitch(final JToggleButton... segments) {
        setName("graph-workspace-tool-switch");
        setOpaque(false);
        setLayout(new GridLayout(1, segments.length, 0, 0));
        final ButtonGroup group = new ButtonGroup();
        for (final JToggleButton segment : segments) {
            group.add(segment);
            add(segment);
        }
    }
}
