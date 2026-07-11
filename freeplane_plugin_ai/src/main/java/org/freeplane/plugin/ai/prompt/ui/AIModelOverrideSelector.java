package org.freeplane.plugin.ai.prompt.ui;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JComboBox;
import javax.swing.SwingWorker;

import org.freeplane.core.util.TextUtils;
import org.freeplane.plugin.ai.model.AIModelCatalog;
import org.freeplane.plugin.ai.model.AIModelDescriptor;
import org.freeplane.plugin.ai.model.AIProviderConfiguration;
import org.freeplane.plugin.ai.model.ui.AIModelFilterState;
import org.freeplane.plugin.ai.model.ui.AIModelSelector;

public class AIModelOverrideSelector {
    private final AIModelCatalog modelCatalog;
    private final AIModelSelector modelSelector;
    private boolean modelListLoadInProgress;
    private Consumer<String> modelSelectionChangeListener;

    public AIModelOverrideSelector(AIProviderConfiguration configuration, AIModelCatalog modelCatalog) {
        this(configuration, modelCatalog, AIModelFilterState.shared());
    }

    AIModelOverrideSelector(AIProviderConfiguration configuration,
                            AIModelCatalog modelCatalog,
                            AIModelFilterState filterState) {
        this.modelCatalog = modelCatalog;
        AIModelDescriptor currentModelOption = AIModelDescriptor.useCurrentModelOption(
            TextUtils.getText("ai_prompt_use_current_model"));
        modelSelector = new AIModelSelector(
            configuration,
            filterState,
            Collections.singletonList(currentModelOption),
            AIModelDescriptor::getDisplayName);
        modelSelector.setExplicitModelSelectionListener(this::onExplicitModelSelectionChanged);
    }

    public JComboBox<AIModelDescriptor> getModelSelectionComboBox() {
        return modelSelector.getModelSelectionComboBox();
    }

    public void setModelSelectionChangeListener(Consumer<String> modelSelectionChangeListener) {
        this.modelSelectionChangeListener = modelSelectionChangeListener;
    }

    public void refreshModelSelectionList(String selectionValue) {
        if (modelListLoadInProgress) {
            return;
        }
        modelListLoadInProgress = true;
        getModelSelectionComboBox().setEnabled(false);
        new SwingWorker<List<AIModelDescriptor>, Void>() {
            @Override
            protected List<AIModelDescriptor> doInBackground() {
                return modelCatalog.getAvailableModels(true);
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
                applyModelSelectionList(modelDescriptors, selectionValue);
                modelListLoadInProgress = false;
            }
        }.execute();
    }

    public void setSelectedModelSelectionValue(String selectionValue) {
        modelSelector.setSelectedModelSelectionValue(selectionValue);
    }

    public String getSelectedModelSelectionValue() {
        AIModelDescriptor selectedModel = modelSelector.getSelectedModel();
        return selectedModel == null ? "" : selectedModel.getSelectionValue();
    }

    void applyModelSelectionList(List<AIModelDescriptor> modelDescriptors, String selectionValue) {
        modelSelector.setAvailableModelDescriptors(modelDescriptors, selectionValue);
    }

    private void onExplicitModelSelectionChanged(AIModelDescriptor selectedModel) {
        if (modelSelectionChangeListener != null) {
            modelSelectionChangeListener.accept(selectedModel.getSelectionValue());
        }
    }
}
