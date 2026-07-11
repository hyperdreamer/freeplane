package org.freeplane.plugin.ai.model.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.accessibility.Accessible;
import javax.swing.AbstractAction;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.plaf.basic.BasicComboBoxEditor;
import javax.swing.plaf.basic.ComboPopup;

import org.freeplane.core.ui.components.JComboBoxFactory;
import org.freeplane.core.util.TextUtils;
import org.freeplane.plugin.ai.model.AIModelDescriptor;
import org.freeplane.plugin.ai.model.AIModelSelection;
import org.freeplane.plugin.ai.model.AIProviderConfiguration;

public class AIModelSelector {
    private static final int FILTER_UPDATE_DELAY_MILLISECONDS = 200;
    private static final String HIGHLIGHT_NEXT_MODEL_ACTION = "highlightNextAiModel";
    private static final String HIGHLIGHT_PREVIOUS_MODEL_ACTION = "highlightPreviousAiModel";
    private static final String COMMIT_HIGHLIGHTED_MODEL_ACTION = "commitHighlightedAiModel";

    private final AIProviderConfiguration configuration;
    private final AIModelFilterState filterState;
    private final List<AIModelDescriptor> alwaysVisibleOptions;
    private final Function<AIModelDescriptor, String> selectedModelText;
    private final Supplier<String> noModelSelectedText;
    private final DefaultComboBoxModel<AIModelDescriptor> modelSelectionModel;
    private final JComboBox<AIModelDescriptor> modelSelectionComboBox;
    private final JTextField editorComponent;
    private final Timer filterUpdateTimer;
    private List<AIModelDescriptor> availableModelDescriptors = Collections.emptyList();
    private AIModelDescriptor selectedModel;
    private Consumer<AIModelDescriptor> explicitModelSelectionListener;
    private boolean updateInProgress;
    private boolean filtering;

    public AIModelSelector(AIProviderConfiguration configuration,
                           AIModelFilterState filterState,
                           List<AIModelDescriptor> alwaysVisibleOptions,
                           Function<AIModelDescriptor, String> selectedModelText) {
        this(
            configuration,
            filterState,
            alwaysVisibleOptions,
            selectedModelText,
            () -> TextUtils.getText("ai_no_model_selected"));
    }

    public AIModelSelector(AIProviderConfiguration configuration,
                           AIModelFilterState filterState,
                           List<AIModelDescriptor> alwaysVisibleOptions,
                           Function<AIModelDescriptor, String> selectedModelText,
                           Supplier<String> noModelSelectedText) {
        this.configuration = configuration;
        this.filterState = filterState;
        this.alwaysVisibleOptions = Collections.unmodifiableList(
            new ArrayList<AIModelDescriptor>(alwaysVisibleOptions));
        this.selectedModelText = selectedModelText;
        this.noModelSelectedText = noModelSelectedText;
        modelSelectionModel = new DefaultComboBoxModel<AIModelDescriptor>();
        modelSelectionComboBox = JComboBoxFactory.create(modelSelectionModel);
        modelSelectionComboBox.setEditable(true);
        modelSelectionComboBox.setEditor(new ModelComboBoxEditor());
        modelSelectionComboBox.setRenderer(new ModelSelectionRenderer());
        editorComponent = (JTextField) modelSelectionComboBox.getEditor().getEditorComponent();
        filterUpdateTimer = new Timer(
            FILTER_UPDATE_DELAY_MILLISECONDS,
            event -> applyPendingFilter());
        filterUpdateTimer.setRepeats(false);
        installListeners();
        installEditorKeyBindings();
    }

    public JComboBox<AIModelDescriptor> getModelSelectionComboBox() {
        return modelSelectionComboBox;
    }

    public void setAvailableModelDescriptors(List<AIModelDescriptor> descriptors,
                                             String selectionValue) {
        List<AIModelDescriptor> sortedDescriptors = new ArrayList<AIModelDescriptor>(descriptors);
        sortedDescriptors.sort(Comparator.comparing(
            AIModelDescriptor::getDisplayName,
            String.CASE_INSENSITIVE_ORDER));
        availableModelDescriptors = sortedDescriptors;
        selectedModel = resolveSelectedDescriptor(selectionValue);
        if (filtering) {
            applyFilter(filterState.getFilterText(), false);
        }
        else {
            restoreCompleteModel();
        }
        modelSelectionComboBox.setEnabled(hasAnyProviderEnabled());
    }

    public void setSelectedModelSelectionValue(String selectionValue) {
        selectedModel = resolveSelectedDescriptor(selectionValue);
        if (filtering) {
            applyFilter(filterState.getFilterText(), false);
        }
        else {
            restoreCompleteModel();
        }
    }

    public AIModelDescriptor getSelectedModel() {
        return selectedModel;
    }

    public boolean hasAvailableModelSelection(String selectionValue) {
        AIModelSelection selection = AIModelSelection.fromSelectionValue(selectionValue);
        if (selection == null) {
            return false;
        }
        for (AIModelDescriptor descriptor : availableModelDescriptors) {
            if (selection.getProviderName().equalsIgnoreCase(descriptor.getProviderName())
                && selection.getModelName().equals(descriptor.getModelName())) {
                return true;
            }
        }
        return false;
    }

    public void setExplicitModelSelectionListener(
        Consumer<AIModelDescriptor> explicitModelSelectionListener) {
        this.explicitModelSelectionListener = explicitModelSelectionListener;
    }

    boolean isFiltering() {
        return filtering;
    }

    private void installListeners() {
        editorComponent.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                filterChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                filterChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                filterChanged();
            }
        });
        modelSelectionComboBox.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent event) {
                beginFiltering();
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent event) {
                endFiltering();
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent event) {
                endFiltering();
            }
        });
        modelSelectionComboBox.addItemListener(event -> {
            if (event.getStateChange() == ItemEvent.SELECTED) {
                explicitSelectionChanged();
            }
        });
    }

    private void installEditorKeyBindings() {
        bindEditorAction(
            KeyEvent.VK_DOWN,
            HIGHLIGHT_NEXT_MODEL_ACTION,
            () -> movePopupHighlight(1));
        bindEditorAction(
            KeyEvent.VK_UP,
            HIGHLIGHT_PREVIOUS_MODEL_ACTION,
            () -> movePopupHighlight(-1));
        bindEditorAction(
            KeyEvent.VK_ENTER,
            COMMIT_HIGHLIGHTED_MODEL_ACTION,
            this::commitHighlightedModel);
    }

    private void bindEditorAction(int keyCode, String actionKey, Runnable action) {
        editorComponent.getInputMap(JComponent.WHEN_FOCUSED).put(
            KeyStroke.getKeyStroke(keyCode, 0), actionKey);
        editorComponent.getActionMap().put(
            actionKey,
            new AbstractAction() {
                private static final long serialVersionUID = 1L;

                @Override
                public void actionPerformed(ActionEvent event) {
                    action.run();
                }
            });
    }

    private void movePopupHighlight(int direction) {
        if (filterUpdateTimer.isRunning()) {
            applyPendingFilter();
        }
        JList<Object> popupList = popupList();
        if (popupList == null || popupList.getModel().getSize() == 0) {
            return;
        }
        int selectedIndex = popupList.getSelectedIndex();
        int targetIndex;
        if (selectedIndex < 0) {
            targetIndex = direction > 0 ? 0 : popupList.getModel().getSize() - 1;
        }
        else {
            targetIndex = Math.max(
                0,
                Math.min(popupList.getModel().getSize() - 1, selectedIndex + direction));
        }
        popupList.setSelectedIndex(targetIndex);
        popupList.ensureIndexIsVisible(targetIndex);
    }

    private void commitHighlightedModel() {
        JList<Object> popupList = popupList();
        if (popupList == null || !(popupList.getSelectedValue() instanceof AIModelDescriptor)) {
            return;
        }
        modelSelectionComboBox.setSelectedItem(popupList.getSelectedValue());
        modelSelectionComboBox.hidePopup();
        if (filtering) {
            endFiltering();
        }
    }

    private JList<Object> popupList() {
        Accessible popup = modelSelectionComboBox.getUI().getAccessibleChild(modelSelectionComboBox, 0);
        return popup instanceof ComboPopup ? ((ComboPopup) popup).getList() : null;
    }

    private void filterChanged() {
        if (updateInProgress) {
            return;
        }
        filtering = true;
        String filterText = editorComponent.getText();
        filterState.setFilterText(filterText);
        filterUpdateTimer.restart();
        if (modelSelectionComboBox.isShowing() && !modelSelectionComboBox.isPopupVisible()) {
            modelSelectionComboBox.showPopup();
        }
    }

    private void beginFiltering() {
        if (filtering) {
            return;
        }
        filtering = true;
        setEditorText(filterState.getFilterText());
        applyFilter(filterState.getFilterText(), true);
    }

    void applyPendingFilter() {
        filterUpdateTimer.stop();
        if (filtering) {
            applyFilter(filterState.getFilterText(), false);
        }
    }

    private void endFiltering() {
        filterUpdateTimer.stop();
        if (!filtering) {
            return;
        }
        filtering = false;
        restoreCompleteModel();
    }

    private void applyFilter(String filterText, boolean selectFilterText) {
        String normalizedFilter = filterText.toLowerCase(Locale.ROOT);
        List<AIModelDescriptor> displayedDescriptors = new ArrayList<AIModelDescriptor>();
        displayedDescriptors.addAll(alwaysVisibleOptions);
        for (AIModelDescriptor descriptor : availableModelDescriptors) {
            if (normalizedFilter.isEmpty()
                || descriptor.getDisplayName().toLowerCase(Locale.ROOT).contains(normalizedFilter)) {
                displayedDescriptors.add(descriptor);
            }
        }
        replaceModel(displayedDescriptors, null);
        if (selectFilterText) {
            selectFilterText();
        }
    }

    private void selectFilterText() {
        editorComponent.setCaretPosition(editorComponent.getText().length());
        editorComponent.moveCaretPosition(0);
    }

    private void restoreCompleteModel() {
        List<AIModelDescriptor> displayedDescriptors = new ArrayList<AIModelDescriptor>();
        displayedDescriptors.addAll(alwaysVisibleOptions);
        displayedDescriptors.addAll(availableModelDescriptors);
        if (selectedModel != null && !displayedDescriptors.contains(selectedModel)) {
            displayedDescriptors.add(selectedModel);
        }
        replaceModel(displayedDescriptors, selectedModel);
    }

    private void setEditorText(String text) {
        boolean previousUpdateState = updateInProgress;
        updateInProgress = true;
        try {
            editorComponent.setText(text);
        }
        finally {
            updateInProgress = previousUpdateState;
        }
    }

    private void replaceModel(List<AIModelDescriptor> descriptors,
                              AIModelDescriptor selection) {
        updateInProgress = true;
        try {
            modelSelectionModel.removeAllElements();
            for (AIModelDescriptor descriptor : descriptors) {
                modelSelectionModel.addElement(descriptor);
            }
            modelSelectionModel.setSelectedItem(selection);
        }
        finally {
            updateInProgress = false;
        }
    }

    private AIModelDescriptor resolveSelectedDescriptor(String selectionValue) {
        AIModelSelection selection = AIModelSelection.fromSelectionValue(selectionValue);
        if (selection == null) {
            for (AIModelDescriptor option : alwaysVisibleOptions) {
                if (option.usesCurrentModel()) {
                    return option;
                }
            }
            return null;
        }
        for (AIModelDescriptor descriptor : availableModelDescriptors) {
            if (selection.getProviderName().equalsIgnoreCase(descriptor.getProviderName())
                && selection.getModelName().equals(descriptor.getModelName())) {
                return descriptor;
            }
        }
        return AIModelDescriptor.unavailable(selection.getProviderName(), selection.getModelName());
    }

    private void explicitSelectionChanged() {
        if (updateInProgress) {
            return;
        }
        Object selectedItem = modelSelectionComboBox.getSelectedItem();
        if (!(selectedItem instanceof AIModelDescriptor)) {
            return;
        }
        selectedModel = (AIModelDescriptor) selectedItem;
        if (explicitModelSelectionListener != null) {
            explicitModelSelectionListener.accept(selectedModel);
        }
    }

    private boolean hasAnyProviderEnabled() {
        return configuration.hasConfiguredProvider();
    }

    private class ModelComboBoxEditor extends BasicComboBoxEditor {
        @Override
        public void setItem(Object item) {
            if (filtering) {
                return;
            }
            boolean previousUpdateState = updateInProgress;
            updateInProgress = true;
            try {
                if (item instanceof AIModelDescriptor) {
                    super.setItem(selectedModelText.apply((AIModelDescriptor) item));
                }
                else if (item == null) {
                    super.setItem(noModelSelectedText.get());
                }
                else {
                    super.setItem(item);
                }
            }
            finally {
                updateInProgress = previousUpdateState;
            }
        }
    }

    private class ModelSelectionRenderer extends DefaultListCellRenderer {
        private String preferredSizeText;
        private boolean measuringPreferredSize;

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            Component component = super.getListCellRendererComponent(
                list, value, index, isSelected, cellHasFocus);
            preferredSizeText = null;
            if (value instanceof AIModelDescriptor) {
                AIModelDescriptor descriptor = (AIModelDescriptor) value;
                preferredSizeText = descriptor.getDisplayName();
                setText(index < 0
                    ? selectedModelText.apply(descriptor)
                    : descriptor.getDisplayName());
            }
            return component;
        }

        @Override
        public Dimension getPreferredSize() {
            boolean previousMeasuringState = measuringPreferredSize;
            measuringPreferredSize = true;
            try {
                return super.getPreferredSize();
            }
            finally {
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
    }
}
