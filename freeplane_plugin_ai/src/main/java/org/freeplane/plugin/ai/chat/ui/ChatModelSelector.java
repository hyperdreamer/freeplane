package org.freeplane.plugin.ai.chat.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.SwingWorker;
import org.freeplane.plugin.ai.model.AIModelCatalog;
import org.freeplane.plugin.ai.model.AIModelDescriptor;
import org.freeplane.plugin.ai.model.AIModelSelection;
import org.freeplane.plugin.ai.model.AIProviderConfiguration;

class ChatModelSelector {
    private final AIProviderConfiguration configuration;
    private final AIModelCatalog modelCatalog;
    private final JComboBox<AIModelDescriptor> modelSelectionComboBox;
    private boolean isModelSelectionUpdateInProgress;
    private boolean isModelListLoadInProgress;
    private Consumer<AIModelDescriptor> modelSelectionChangeListener;
    private Consumer<AIModelDescriptor> explicitUserModelSelectionChangeListener;
    private String displayedSelectionValueOverride;

    ChatModelSelector(AIProviderConfiguration configuration, AIModelCatalog modelCatalog) {
        this.configuration = configuration;
        this.modelCatalog = modelCatalog;
        this.modelSelectionComboBox = new JComboBox<>();
        this.modelSelectionComboBox.setRenderer(new ModelSelectionRenderer());
        this.modelSelectionComboBox.addActionListener(event -> onModelSelectionChanged());
    }

    JComboBox<AIModelDescriptor> getModelSelectionComboBox() {
        return modelSelectionComboBox;
    }

    void setMinimumAndPreferredWidth(int minimumWidth, int preferredWidth) {
        Dimension preferredSize = modelSelectionComboBox.getPreferredSize();
        int width = Math.max(minimumWidth, preferredWidth);
        modelSelectionComboBox.setMinimumSize(new Dimension(minimumWidth, preferredSize.height));
        modelSelectionComboBox.setPreferredSize(new Dimension(width, preferredSize.height));
    }

    void setModelSelectionChangeListener(Consumer<AIModelDescriptor> modelSelectionChangeListener) {
        this.modelSelectionChangeListener = modelSelectionChangeListener;
    }

    void setExplicitUserModelSelectionChangeListener(
        Consumer<AIModelDescriptor> explicitUserModelSelectionChangeListener) {
        this.explicitUserModelSelectionChangeListener = explicitUserModelSelectionChangeListener;
    }

    void setDisplayedSelectionValueOverride(String selectionValueOverride) {
        displayedSelectionValueOverride = normalizeSelectionValue(selectionValueOverride);
        applyDisplayedSelectionValue(false);
    }

    void loadInitialModelSelectionList() {
        updateModelSelectionList(true);
    }

    private void onModelSelectionChanged() {
        if (isModelSelectionUpdateInProgress) {
            return;
        }
        Object selectedValue = modelSelectionComboBox.getSelectedItem();
        if (!(selectedValue instanceof AIModelDescriptor)) {
            configuration.setSelectedModelValue("");
            notifyModelSelectionChange(null);
            notifyExplicitUserModelSelectionChange(null);
            return;
        }
        AIModelDescriptor selectedModel = (AIModelDescriptor) selectedValue;
        configuration.setSelectedModelValue(selectedModel.getSelectionValue());
        notifyModelSelectionChange(selectedModel);
        notifyExplicitUserModelSelectionChange(selectedModel);
    }

    private void updateModelSelectionList(boolean allowsRefresh) {
        if (isModelListLoadInProgress) {
            return;
        }
        isModelListLoadInProgress = true;
        modelSelectionComboBox.setEnabled(false);
        new SwingWorker<List<AIModelDescriptor>, Void>() {
            @Override
            protected List<AIModelDescriptor> doInBackground() {
                return modelCatalog.getAvailableModels(allowsRefresh);
            }

            @Override
            protected void done() {
                List<AIModelDescriptor> modelDescriptors;
                try {
                    modelDescriptors = get();
                } catch (Exception exception) {
                    modelDescriptors = Collections.emptyList();
                }
                applyModelSelectionList(modelDescriptors);
                isModelListLoadInProgress = false;
            }
        }.execute();
    }

    void applyModelSelectionList(List<AIModelDescriptor> modelDescriptors) {
        isModelSelectionUpdateInProgress = true;
        try {
            List<AIModelDescriptor> sortedModelDescriptors = new ArrayList<>(modelDescriptors);
            sortedModelDescriptors.sort(Comparator.comparing(AIModelDescriptor::getDisplayName, String.CASE_INSENSITIVE_ORDER));
            DefaultComboBoxModel<AIModelDescriptor> comboBoxModel = new DefaultComboBoxModel<>(
                sortedModelDescriptors.toArray(new AIModelDescriptor[0])
            );
            modelSelectionComboBox.setModel(comboBoxModel);
            applyDisplayedSelectionValue(true);
            modelSelectionComboBox.setEnabled(hasAnyProviderEnabled());
        } finally {
            isModelSelectionUpdateInProgress = false;
        }
    }

    private void applyDisplayedSelectionValue(boolean notifySelectionChange) {
        isModelSelectionUpdateInProgress = true;
        try {
            modelSelectionComboBox.setSelectedIndex(-1);
            DefaultComboBoxModel<AIModelDescriptor> comboBoxModel = currentComboBoxModel();
            applySelectionValue(descriptorsFrom(comboBoxModel), comboBoxModel, notifySelectionChange);
        } finally {
            isModelSelectionUpdateInProgress = false;
        }
    }

    private void applySelectionValue(List<AIModelDescriptor> modelDescriptors,
                                     DefaultComboBoxModel<AIModelDescriptor> comboBoxModel,
                                     boolean notifySelectionChange) {
        String selectionValue = effectiveSelectionValue();
        AIModelSelection selection = AIModelSelection.fromSelectionValue(selectionValue);
        if (selection == null) {
            return;
        }
        for (AIModelDescriptor modelDescriptor : modelDescriptors) {
            if (selection.getProviderName().equalsIgnoreCase(modelDescriptor.getProviderName())
                && selection.getModelName().equals(modelDescriptor.getModelName())) {
                modelSelectionComboBox.setSelectedItem(modelDescriptor);
                persistLegacySelectionIfNeeded(modelDescriptor.getSelectionValue());
                if (notifySelectionChange) {
                    notifyModelSelectionChange(modelDescriptor);
                }
                return;
            }
        }
        AIModelDescriptor unavailableSelection = AIModelDescriptor.unavailable(
            selection.getProviderName(),
            selection.getModelName());
        comboBoxModel.addElement(unavailableSelection);
        modelSelectionComboBox.setSelectedItem(unavailableSelection);
        persistLegacySelectionIfNeeded(unavailableSelection.getSelectionValue());
        if (notifySelectionChange) {
            notifyModelSelectionChange(unavailableSelection);
        }
    }

    private boolean hasAnyProviderEnabled() {
        boolean hasOpenrouterKey = configuration.getOpenRouterKey() != null && !configuration.getOpenRouterKey().isEmpty();
        boolean hasGeminiKey = configuration.getGeminiKey() != null && !configuration.getGeminiKey().isEmpty();
        return hasOpenrouterKey || hasGeminiKey || configuration.hasOllamaServiceAddress();
    }

    private DefaultComboBoxModel<AIModelDescriptor> currentComboBoxModel() {
        Object comboBoxModel = modelSelectionComboBox.getModel();
        if (comboBoxModel instanceof DefaultComboBoxModel) {
            @SuppressWarnings("unchecked")
            DefaultComboBoxModel<AIModelDescriptor> typedModel =
                (DefaultComboBoxModel<AIModelDescriptor>) comboBoxModel;
            return typedModel;
        }
        return new DefaultComboBoxModel<>();
    }

    private List<AIModelDescriptor> descriptorsFrom(DefaultComboBoxModel<AIModelDescriptor> comboBoxModel) {
        List<AIModelDescriptor> descriptors = new ArrayList<>();
        for (int index = 0; index < comboBoxModel.getSize(); index++) {
            AIModelDescriptor descriptor = comboBoxModel.getElementAt(index);
            if (descriptor != null) {
                descriptors.add(descriptor);
            }
        }
        return descriptors;
    }

    private String effectiveSelectionValue() {
        if (displayedSelectionValueOverride != null) {
            return displayedSelectionValueOverride;
        }
        return configuration.getSelectedModelValue();
    }

    private void persistLegacySelectionIfNeeded(String selectionValue) {
        if (displayedSelectionValueOverride != null) {
            return;
        }
        String storedSelectionValue = configuration.getStoredSelectedModelValue();
        if (storedSelectionValue == null || storedSelectionValue.isEmpty()) {
            configuration.setSelectedModelValue(selectionValue);
        }
    }

    private String normalizeSelectionValue(String selectionValue) {
        if (selectionValue == null) {
            return null;
        }
        String normalized = selectionValue.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void notifyModelSelectionChange(AIModelDescriptor modelDescriptor) {
        if (modelSelectionChangeListener != null) {
            modelSelectionChangeListener.accept(modelDescriptor);
        }
    }

    private void notifyExplicitUserModelSelectionChange(AIModelDescriptor modelDescriptor) {
        if (explicitUserModelSelectionChangeListener != null) {
            explicitUserModelSelectionChangeListener.accept(modelDescriptor);
        }
    }

    private static class ModelSelectionRenderer extends DefaultListCellRenderer {
        private String preferredSizeText;
        private boolean measuringPreferredSize;

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            preferredSizeText = null;
            if (value instanceof AIModelDescriptor) {
                AIModelDescriptor modelDescriptor = (AIModelDescriptor) value;
                preferredSizeText = index < 0 ? modelDescriptor.getDisplayName() : null;
                String renderedText = index < 0
                    ? renderSelectedModelName(modelDescriptor)
                    : modelDescriptor.getDisplayName();
                setText(renderedText);
            }
            return component;
        }

        @Override
        public Dimension getPreferredSize() {
            boolean previousMeasuringState = measuringPreferredSize;
            measuringPreferredSize = true;
            try {
                return super.getPreferredSize();
            } finally {
                measuringPreferredSize = previousMeasuringState;
            }
        }

        @Override
        public String getText() {
            if (measuringPreferredSize && preferredSizeText != null) {
                return preferredSizeText;
            }
            return super.getText();
        }

        private String renderSelectedModelName(AIModelDescriptor modelDescriptor) {
            if (modelDescriptor.isUnavailable()) {
                return modelDescriptor.getDisplayName();
            }
            String modelName = modelDescriptor.getModelName();
            int separatorIndex = modelName.indexOf('/');
            if (separatorIndex >= 0 && separatorIndex < modelName.length() - 1) {
                return modelName.substring(separatorIndex + 1);
            }
            return modelName;
        }
    }
}
