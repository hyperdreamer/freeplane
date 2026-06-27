package org.freeplane.plugin.ai.chat.ui;

import java.awt.Dimension;
import javax.swing.JButton;
import javax.swing.JPanel;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ModelConfigurationSelectorLayoutTest {
    @Test
    public void layoutDoesNotShrinkModelSelectorBelowThinkingSelectorWidth() {
        JPanel panel = new JPanel(new ModelConfigurationSelectorLayout(2));
        JButton modelSelector = new JButton();
        modelSelector.setPreferredSize(new Dimension(280, 24));
        JButton thinkingSelector = new JButton();
        thinkingSelector.setPreferredSize(new Dimension(100, 24));
        panel.add(modelSelector, "model");
        panel.add(thinkingSelector, "thinking");

        panel.setSize(new Dimension(150, 24));
        panel.doLayout();

        assertThat(modelSelector.getWidth()).isEqualTo(thinkingSelector.getPreferredSize().width);
        assertThat(thinkingSelector.getX()).isEqualTo(modelSelector.getWidth() + 2);
    }

    @Test
    public void layoutGivesAvailableWidthToModelSelectorWhenEnoughSpaceExists() {
        JPanel panel = new JPanel(new ModelConfigurationSelectorLayout(2));
        JButton modelSelector = new JButton();
        modelSelector.setPreferredSize(new Dimension(280, 24));
        JButton thinkingSelector = new JButton();
        thinkingSelector.setPreferredSize(new Dimension(100, 24));
        panel.add(modelSelector, "model");
        panel.add(thinkingSelector, "thinking");

        panel.setSize(new Dimension(500, 24));
        panel.doLayout();

        assertThat(modelSelector.getWidth()).isEqualTo(398);
        assertThat(thinkingSelector.getWidth()).isEqualTo(100);
        assertThat(thinkingSelector.getX()).isEqualTo(400);
    }
}
