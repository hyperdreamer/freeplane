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
    private Component temperatureSelector;

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
        if ("temperature".equals(constraints)) {
            temperatureSelector = component;
            return;
        }
        throw new IllegalArgumentException("Expected constraints 'model', 'thinking', or 'temperature'");
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
        if (component == temperatureSelector) {
            temperatureSelector = null;
        }
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        Insets insets = parent.getInsets();
        Dimension modelSize = preferredSize(modelSelector);
        Dimension thinkingSize = preferredSize(thinkingSelector);
        Dimension temperatureSize = preferredSize(temperatureSelector);
        int compactWidth = compactWidth(thinkingSize, temperatureSize);
        int width = insets.left + modelSize.width + gapWidth() + compactWidth + insets.right;
        int height = insets.top + maxHeight(modelSize, thinkingSize, temperatureSize) + insets.bottom;
        return new Dimension(width, height);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        Insets insets = parent.getInsets();
        Dimension thinkingSize = preferredSize(thinkingSelector);
        Dimension temperatureSize = preferredSize(temperatureSelector);
        int minimumModelWidth = Math.max(thinkingSize.width, temperatureSize.width);
        int width = insets.left + minimumModelWidth + gapWidth()
            + compactWidth(thinkingSize, temperatureSize) + insets.right;
        int height = insets.top + maxHeight(preferredSize(modelSelector), thinkingSize, temperatureSize)
            + insets.bottom;
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
        Dimension modelPreferredSize = modelSelector.getPreferredSize();
        Dimension thinkingSize = thinkingSelector.getPreferredSize();
        Dimension temperatureSize = preferredSize(temperatureSelector);
        int availableWidth = Math.max(0, parent.getWidth() - insets.left - insets.right);
        int minimumModelWidth = Math.max(thinkingSize.width, temperatureSize.width);
        int compactWidth = compactWidth(thinkingSize, temperatureSize);
        int modelWidth = Math.max(minimumModelWidth, availableWidth - compactWidth - gapWidth());
        int height = maxHeight(modelPreferredSize, thinkingSize, temperatureSize);
        int y = insets.top + Math.max(0, parent.getHeight() - insets.top - insets.bottom - height) / 2;
        int x = insets.left;
        modelSelector.setBounds(x, y, modelWidth, modelPreferredSize.height);
        x += modelWidth + gap;
        thinkingSelector.setBounds(x, y, thinkingSize.width, thinkingSize.height);
        x += thinkingSize.width;
        if (temperatureSelector != null) {
            x += gap;
            temperatureSelector.setBounds(x, y, temperatureSize.width, temperatureSize.height);
        }
    }

    private int gapWidth() {
        return gap;
    }

    private int compactWidth(Dimension thinkingSize, Dimension temperatureSize) {
        return thinkingSize.width + (temperatureSelector == null ? 0 : gap + temperatureSize.width);
    }

    private int maxHeight(Dimension modelSize, Dimension thinkingSize, Dimension temperatureSize) {
        return Math.max(modelSize.height, Math.max(thinkingSize.height, temperatureSize.height));
    }

    private Dimension preferredSize(Component component) {
        return component == null ? new Dimension(0, 0) : component.getPreferredSize();
    }
}
