package org.freeplane.plugin.ai.chat.ui;

import java.awt.Dimension;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.JComboBox;
import javax.swing.SwingWorker;

import org.freeplane.core.util.TextUtils;
import org.freeplane.plugin.ai.model.AIModelCatalog;
import org.freeplane.plugin.ai.model.AIModelDescriptor;
import org.freeplane.plugin.ai.model.AIProviderConfiguration;
import org.freeplane.plugin.ai.model.ui.AIModelFilterState;
import org.freeplane.plugin.ai.model.ui.AIModelSelector;

class ChatModelSelector {
    private final AIProviderConfiguration configuration;
    private final AIModelCatalog modelCatalog;
    private final AIModelSelector modelSelector;
    private boolean isModelListLoadInProgress;
    private Consumer<AIModelDescriptor> modelSelectionChangeListener;
    private Consumer<AIModelDescriptor> explicitUserModelSelectionChangeListener;
    private String displayedSelectionValueOverride;

    ChatModelSelector(AIProviderConfiguration configuration, AIModelCatalog modelCatalog) {
        this(configuration, modelCatalog, AIModelFilterState.shared());
    }

    ChatModelSelector(AIProviderConfiguration configuration,
                      AIModelCatalog modelCatalog,
                      AIModelFilterState filterState) {
        this(
            configuration,
            modelCatalog,
            new AIModelSelector(
                configuration,
                filterState,
                Collections.emptyList(),
                ChatModelSelector::renderSelectedModelName));
    }

    ChatModelSelector(AIProviderConfiguration configuration,
                      AIModelCatalog modelCatalog,
                      AIModelFilterState filterState,
                      Supplier<String> noModelSelectedText) {
        this(
            configuration,
            modelCatalog,
            new AIModelSelector(
                configuration,
                filterState,
                Collections.emptyList(),
                ChatModelSelector::renderSelectedModelName,
                noModelSelectedText));
    }

    private ChatModelSelector(AIProviderConfiguration configuration,
                              AIModelCatalog modelCatalog,
                              AIModelSelector modelSelector) {
        this.configuration = configuration;
        this.modelCatalog = modelCatalog;
        this.modelSelector = modelSelector;
        modelSelector.setExplicitModelSelectionListener(this::onExplicitModelSelectionChanged);
    }

    JComboBox<AIModelDescriptor> getModelSelectionComboBox() {
        return modelSelector.getModelSelectionComboBox();
    }

    boolean hasAvailableSelectedModel() {
        AIModelDescriptor selectedModel = modelSelector.getSelectedModel();
        return selectedModel != null && !selectedModel.isUnavailable();
    }

    boolean hasAvailableModelSelection(String selectionValueOverride) {
        String selectionValue = normalizeSelectionValue(selectionValueOverride);
        if (selectionValue == null) {
            selectionValue = configuration.getSelectedModelValue();
        }
        return modelSelector.hasAvailableModelSelection(selectionValue);
    }

    void setMinimumAndPreferredWidth(int minimumWidth, int preferredWidth) {
        JComboBox<AIModelDescriptor> comboBox = getModelSelectionComboBox();
        Dimension preferredSize = comboBox.getPreferredSize();
        int width = Math.max(minimumWidth, preferredWidth);
        comboBox.setMinimumSize(new Dimension(minimumWidth, preferredSize.height));
        comboBox.setPreferredSize(new Dimension(width, preferredSize.height));
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
        modelSelector.setSelectedModelSelectionValue(effectiveSelectionValue());
    }

    void loadInitialModelSelectionList() {
        updateModelSelectionList(true);
    }

    private void updateModelSelectionList(boolean allowsRefresh) {
        if (isModelListLoadInProgress) {
            return;
        }
        isModelListLoadInProgress = true;
        getModelSelectionComboBox().setEnabled(false);
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
                }
                catch (Exception exception) {
                    modelDescriptors = Collections.emptyList();
                }
                applyModelSelectionList(modelDescriptors);
                isModelListLoadInProgress = false;
            }
        }.execute();
    }

    void applyModelSelectionList(List<AIModelDescriptor> modelDescriptors) {
        modelSelector.setAvailableModelDescriptors(modelDescriptors, effectiveSelectionValue());
        AIModelDescriptor selectedModel = modelSelector.getSelectedModel();
        if (selectedModel != null) {
            persistLegacySelectionIfNeeded(selectedModel.getSelectionValue());
            notifyModelSelectionChange(selectedModel);
        }
    }

    private void onExplicitModelSelectionChanged(AIModelDescriptor selectedModel) {
        configuration.setSelectedModelValue(selectedModel.getSelectionValue());
        notifyModelSelectionChange(selectedModel);
        notifyExplicitUserModelSelectionChange(selectedModel);
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

    private static String renderSelectedModelName(AIModelDescriptor modelDescriptor) {
        if (modelDescriptor.isUnavailable()) {
            return TextUtils.getText("ai_unknown_model");
        }
        String modelName = modelDescriptor.getModelName();
        int separatorIndex = modelName.indexOf('/');
        if (separatorIndex >= 0 && separatorIndex < modelName.length() - 1) {
            return modelName.substring(separatorIndex + 1);
        }
        return modelName;
    }
}
