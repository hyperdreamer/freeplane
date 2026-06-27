package org.freeplane.plugin.ai.chat.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager2;

public class ModelConfigurationSelectorLayout implements LayoutManager2 {
    private final int gap;
    private Component modelSelector;
    private Component thinkingSelector;

    public ModelConfigurationSelectorLayout(int gap) {
        this.gap = Math.max(0, gap);
    }

    @Override
    public void addLayoutComponent(Component component, Object constraints) {
        if ("model".equals(constraints)) {
            modelSelector = component;
            return;
        }
        if ("thinking".equals(constraints)) {
            thinkingSelector = component;
            return;
        }
        throw new IllegalArgumentException("Expected constraints 'model' or 'thinking'");
    }

    @Override
    public void addLayoutComponent(String name, Component component) {
        addLayoutComponent(component, name);
    }

    @Override
    public void removeLayoutComponent(Component component) {
        if (component == modelSelector) {
            modelSelector = null;
        }
        if (component == thinkingSelector) {
            thinkingSelector = null;
        }
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        Insets insets = parent.getInsets();
        Dimension modelSize = preferredSize(modelSelector);
        Dimension thinkingSize = preferredSize(thinkingSelector);
        int width = insets.left + modelSize.width + gap + thinkingSize.width + insets.right;
        int height = insets.top + Math.max(modelSize.height, thinkingSize.height) + insets.bottom;
        return new Dimension(width, height);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        Insets insets = parent.getInsets();
        Dimension thinkingSize = preferredSize(thinkingSelector);
        int width = insets.left + thinkingSize.width + gap + thinkingSize.width + insets.right;
        int height = insets.top + thinkingSize.height + insets.bottom;
        return new Dimension(width, height);
    }

    @Override
    public Dimension maximumLayoutSize(Container target) {
        return new Dimension(Integer.MAX_VALUE, preferredLayoutSize(target).height);
    }

    @Override
    public float getLayoutAlignmentX(Container target) {
        return 0;
    }

    @Override
    public float getLayoutAlignmentY(Container target) {
        return 0.5f;
    }

    @Override
    public void invalidateLayout(Container target) {
    }

    @Override
    public void layoutContainer(Container parent) {
        if (modelSelector == null || thinkingSelector == null) {
            return;
        }
        Insets insets = parent.getInsets();
        Dimension thinkingSize = thinkingSelector.getPreferredSize();
        Dimension modelPreferredSize = modelSelector.getPreferredSize();
        int availableWidth = Math.max(0, parent.getWidth() - insets.left - insets.right);
        int modelWidth = Math.max(thinkingSize.width, availableWidth - thinkingSize.width - gap);
        int height = Math.max(modelPreferredSize.height, thinkingSize.height);
        int y = insets.top + Math.max(0, parent.getHeight() - insets.top - insets.bottom - height) / 2;
        modelSelector.setBounds(insets.left, y, modelWidth, modelPreferredSize.height);
        thinkingSelector.setBounds(insets.left + modelWidth + gap, y, thinkingSize.width, thinkingSize.height);
    }

    private Dimension preferredSize(Component component) {
        return component == null ? new Dimension(0, 0) : component.getPreferredSize();
    }
}
