package org.freeplane.plugin.ai.prompt.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.resources.WindowConfigurationStorage;
import org.freeplane.core.ui.textchanger.TranslatedElementFactory;
import org.freeplane.core.util.TextUtils;
import org.freeplane.api.ai.AiTemperature;
import org.freeplane.api.ai.AiThinkingEffort;
import org.freeplane.plugin.ai.chat.ui.ModelConfigurationSelectorLayout;
import org.freeplane.plugin.ai.model.AIModelCatalog;
import org.freeplane.plugin.ai.model.AIModelConfiguration;
import org.freeplane.plugin.ai.model.AIModelDescriptor;
import org.freeplane.plugin.ai.model.AIProviderConfiguration;
import org.freeplane.plugin.ai.prompt.AiPrompt;
import org.freeplane.plugin.ai.prompt.AiPromptActionRegistry;
import org.freeplane.plugin.ai.prompt.AiPromptNameValidator;
import org.freeplane.plugin.ai.prompt.AiPromptStore;

public class AiPromptManagerDialog extends JDialog {
    private static final long serialVersionUID = 1L;
    static final String WINDOW_CONFIGURATION_PROPERTY =
        "ai_prompt_manager_dialog_window_configuration";
    private static final String DEFAULT_NEW_PROMPT_NAME = "New Prompt";

    private final AiPromptActionRegistry promptActionRegistry;
    private final EditorState editorState;
    private final DefaultListModel<AiPrompt> listModel = new DefaultListModel<AiPrompt>();
    private final JList<AiPrompt> promptsList = new JList<AiPrompt>(listModel);
    private final JTextField nameField = new JTextField();
    private final JTextArea promptArea = new JTextArea();
    private final JCheckBox showInChatCheckBox = new JCheckBox(TextUtils.getText("ai_prompt_show_in_chat"));
    private final JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    private final JButton newButton = new JButton(TextUtils.getText("ai_prompt_new"));
    private final JButton sendButton = new JButton(TextUtils.getText("ai_prompt_send"));
    private final JButton saveAsNewButton = new JButton(TextUtils.getText("ai_prompt_save_as_new"));
    private final JButton saveButton = new JButton(TextUtils.getText("save"));
    private final JButton deleteButton = new JButton(TextUtils.getText("delete"));
    private final JButton closeButton = new JButton(TextUtils.getText("close"));
    private final AIProviderConfiguration configuration = new AIProviderConfiguration();
    private final AIModelOverrideSelector modelSelectionController =
        new AIModelOverrideSelector(configuration, new AIModelCatalog(configuration));
    private final AiPromptThinkingEffortSelectionController thinkingEffortSelectionController =
        new AiPromptThinkingEffortSelectionController();
    private final AiTemperatureSelectionController temperatureSelectionController =
        new AiTemperatureSelectionController(true);
    private final AiPromptToolSelectionController toolSelectionController =
        new AiPromptToolSelectionController();
    private final WindowGeometryPersistence windowGeometryPersistence;
    private boolean updatingFields;
    private boolean updatingSelection;

    public AiPromptManagerDialog(Component owner, AiPromptActionRegistry promptActionRegistry) {
        this(owner, promptActionRegistry,
            new WindowGeometryPersistence(WINDOW_CONFIGURATION_PROPERTY));
    }

    AiPromptManagerDialog(Component owner, AiPromptActionRegistry promptActionRegistry,
                          WindowGeometryPersistence windowGeometryPersistence) {
        super(findOwnerWindow(owner));
        this.promptActionRegistry = promptActionRegistry;
        this.editorState = promptActionRegistry.getDialogState();
        this.windowGeometryPersistence = windowGeometryPersistence;
        setTitle(TextUtils.getText("ai_prompt_manager_title"));
        setModal(true);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                requestClose();
            }
        });
        setLayout(new BorderLayout(10, 10));
        setPreferredSize(new Dimension(520, 420));
        buildUi();
        refreshUi();
        pack();
        ensureButtonRowIsVisible();
        restoreOrPlaceWindow(owner);
    }

    public void openDialog() {
        modelSelectionController.refreshModelSelectionList(editorState.getCurrentDraft().getModelSelectionValue());
        setVisible(true);
    }

    private void buildUi() {
        promptsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        promptsList.addListSelectionListener(event -> {
            if (event.getValueIsAdjusting()) {
                return;
            }
            handlePromptSelectionChange();
        });

        DocumentListener documentListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                updateDraftFromFields();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                updateDraftFromFields();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                updateDraftFromFields();
            }
        };
        nameField.getDocument().addDocumentListener(documentListener);
        promptArea.getDocument().addDocumentListener(documentListener);
        showInChatCheckBox.addActionListener(event -> updateDraftFromFields());
        modelSelectionController.setModelSelectionChangeListener(selectionValue -> updateDraftFromFields());
        thinkingEffortSelectionController.setThinkingEffortSelectionChangeListener(
            thinkingEffort -> updateDraftFromFields());
        temperatureSelectionController.setTemperatureSelectionChangeListener(
            temperature -> updateDraftFromFields());
        toolSelectionController.setToolSelectionChangeListener(selectionValue -> updateDraftFromFields());

        JPanel listPanel = createLabeledPanel("ai_prompt_list_label", new JScrollPane(promptsList));

        JPanel namePanel = createLabeledPanel("ai_prompt_name_label", nameField);

        JComboBox<AIModelDescriptor> modelSelectionComboBox = modelSelectionController.getModelSelectionComboBox();
        JPanel modelPanel = createTitledPanel("ai_prompt_model_label", modelSelectionComboBox);

        JComboBox<AiPromptThinkingEffortSelectionController.ThinkingEffortOption> thinkingEffortComboBox =
            thinkingEffortSelectionController.getThinkingEffortComboBox();
        JPanel thinkingEffortPanel = createTitledPanel("ai_prompt_thinking_effort_label", thinkingEffortComboBox);

        JComboBox<AiTemperatureSelectionController.TemperatureOption> temperatureComboBox =
            temperatureSelectionController.getComboBox();
        JPanel temperaturePanel = createTitledPanel("ai_prompt_temperature_label", temperatureComboBox);

        JPanel modelConfigurationSelectorPanel = new JPanel(new ModelConfigurationSelectorLayout(5));
        modelConfigurationSelectorPanel.add(modelPanel, "model");
        modelConfigurationSelectorPanel.add(thinkingEffortPanel, "thinking");
        modelConfigurationSelectorPanel.add(temperaturePanel, "temperature");

        JComboBox<AiPromptToolSelectionController.ToolSelectionOption> toolSelectionComboBox =
            toolSelectionController.getToolSelectionComboBox();
        JPanel toolPanel = createTitledPanel("ai_prompt_tool_label", toolSelectionComboBox);

        promptArea.setLineWrap(true);
        promptArea.setWrapStyleWord(true);
        JPanel promptPanel = createLabeledPanel("ai_prompt_prompt_label", new JScrollPane(promptArea));

        JPanel selectionPanel = new JPanel(new BorderLayout(5, 0));
        selectionPanel.add(modelConfigurationSelectorPanel, BorderLayout.CENTER);
        selectionPanel.add(toolPanel, BorderLayout.EAST);

        JPanel fieldsPanel = new JPanel(new BorderLayout(5, 5));
        fieldsPanel.add(namePanel, BorderLayout.NORTH);
        fieldsPanel.add(selectionPanel, BorderLayout.SOUTH);

        JPanel rightPanel = new JPanel(new BorderLayout(5, 5));
        rightPanel.add(fieldsPanel, BorderLayout.NORTH);
        rightPanel.add(promptPanel, BorderLayout.CENTER);
        rightPanel.add(showInChatCheckBox, BorderLayout.SOUTH);

        JPanel contentPanel = new JPanel(new BorderLayout(10, 10));
        contentPanel.add(listPanel, BorderLayout.WEST);
        contentPanel.add(rightPanel, BorderLayout.CENTER);

        newButton.addActionListener(event -> beginNewPromptDraft());
        sendButton.addActionListener(event -> sendPrompt());
        saveAsNewButton.addActionListener(event -> savePromptAsNew());
        saveButton.addActionListener(event -> savePrompt());
        deleteButton.addActionListener(event -> deletePrompt());
        closeButton.addActionListener(event -> requestClose());

        buttonPanel.add(newButton);
        buttonPanel.add(sendButton);
        buttonPanel.add(saveAsNewButton);
        buttonPanel.add(saveButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(closeButton);

        add(contentPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private JPanel createLabeledPanel(String titleKey, JComponent component) {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.add(TranslatedElementFactory.createLabel(titleKey), BorderLayout.NORTH);
        panel.add(component, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createTitledPanel(String titleKey, JComponent component) {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        TranslatedElementFactory.createTitledBorder(panel, titleKey);
        panel.add(component, BorderLayout.CENTER);
        return panel;
    }

    private void handlePromptSelectionChange() {
        if (updatingSelection) {
            return;
        }
        int selectedIndex = promptsList.getSelectedIndex();
        int currentIndex = editorState.getSelectedSavedPromptIndex();
        if (selectedIndex < 0 || selectedIndex == currentIndex) {
            refreshPromptSelection();
            return;
        }
        updateDraftFromFields();
        if (handleDirtyDecision(() -> editorState.selectSavedPrompt(selectedIndex))) {
            refreshUi();
        }
    }

    private void beginNewPromptDraft() {
        updateDraftFromFields();
        if (handleDirtyDecision(editorState::beginNewDraft)) {
            refreshUi();
            SwingUtilities.invokeLater(nameField::requestFocusInWindow);
        }
    }

    private void deletePrompt() {
        if (!editorState.isEditingSavedPrompt()) {
            return;
        }
        updateDraftFromFields();
        if (handleDirtyDecision(() -> {
            editorState.deleteSelectedPrompt();
            promptActionRegistry.persistStateAndRefreshMenus();
        })) {
            refreshUi();
        }
    }

    private void savePrompt() {
        updateDraftFromFields();
        editorState.save(defaultNewPromptName());
        promptActionRegistry.persistStateAndRefreshMenus();
        refreshUi();
    }

    private void savePromptAsNew() {
        if (!editorState.canSaveAsNew()) {
            return;
        }
        updateDraftFromFields();
        editorState.saveAsNew(defaultNewPromptName());
        promptActionRegistry.persistStateAndRefreshMenus();
        refreshUi();
    }

    private void sendPrompt() {
        updateDraftFromFields();
        promptActionRegistry.runPrompt(editorState.getCurrentDraft(), this);
    }

    private void requestClose() {
        updateDraftFromFields();
        promptActionRegistry.persistStateIfChanged();
        closeDialog();
    }

    private boolean handleDirtyDecision(Runnable continueAction) {
        if (!editorState.isDirty()) {
            continueAction.run();
            return true;
        }
        DirtyDraftDecision decision = askDirtyDraftDecision();
        switch (decision) {
            case SAVE:
                savePrompt();
                continueAction.run();
                return true;
            case DISCARD:
                continueAction.run();
                return true;
            case CANCEL:
            default:
                refreshUi();
                return false;
        }
    }

    private DirtyDraftDecision askDirtyDraftDecision() {
        Object[] options = {
            TextUtils.getText("save"),
            TextUtils.getText("ai_prompt_discard"),
            TextUtils.getText("cancel")
        };
        String message = TextUtils.getText("ai_prompt_unsaved_changes")
            + "\n"
            + TextUtils.getText("ai_prompt_unsaved_changes_explanation");
        int result = JOptionPane.showOptionDialog(
            this,
            message,
            TextUtils.getText("ai_prompt_manager_title"),
            JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[0]);
        switch (result) {
            case JOptionPane.YES_OPTION:
                return DirtyDraftDecision.SAVE;
            case JOptionPane.NO_OPTION:
                return DirtyDraftDecision.DISCARD;
            default:
                return DirtyDraftDecision.CANCEL;
        }
    }

    private void updateDraftFromFields() {
        if (updatingFields) {
            return;
        }
        editorState.updateDraft(
            nameField.getText(),
            promptArea.getText(),
            showInChatCheckBox.isSelected(),
            modelSelectionController.getSelectedModelSelectionValue(),
            thinkingEffortSelectionController.getSelectedThinkingEffort(),
            temperatureSelectionController.getSelectedTemperature(),
            toolSelectionController.getSelectedToolAvailabilitySelectionValue());
        refreshButtons();
    }

    private void refreshUi() {
        refreshPromptList();
        refreshEditorFromState();
        refreshButtons();
    }

    private void refreshPromptList() {
        updatingSelection = true;
        try {
            listModel.clear();
            for (AiPrompt prompt : editorState.getSavedPrompts()) {
                listModel.addElement(prompt);
            }
            refreshPromptSelection();
        }
        finally {
            updatingSelection = false;
        }
    }

    private void refreshPromptSelection() {
        int selectedIndex = editorState.getSelectedSavedPromptIndex();
        if (selectedIndex >= 0 && selectedIndex < listModel.getSize()) {
            promptsList.setSelectedIndex(selectedIndex);
        }
        else {
            promptsList.clearSelection();
        }
    }

    private void refreshEditorFromState() {
        updatingFields = true;
        try {
            AiPrompt currentDraft = editorState.getCurrentDraft();
            nameField.setText(currentDraft.getName());
            promptArea.setText(currentDraft.getPrompt() == null ? "" : currentDraft.getPrompt());
            showInChatCheckBox.setSelected(currentDraft.isShowInChat());
            modelSelectionController.setSelectedModelSelectionValue(currentDraft.getModelSelectionValue());
            thinkingEffortSelectionController.setSelectedThinkingEffort(currentDraft.getThinkingEffort());
            temperatureSelectionController.setSelectedTemperature(currentDraft.getTemperature());
            toolSelectionController.setSelectedToolAvailabilitySelectionValue(
                currentDraft.getToolAvailabilitySelectionValue());
        }
        finally {
            updatingFields = false;
        }
    }

    private void refreshButtons() {
        deleteButton.setEnabled(editorState.isEditingSavedPrompt());
        saveAsNewButton.setEnabled(editorState.canSaveAsNew());
    }

    private String defaultNewPromptName() {
        return TextUtils.getText("ai_prompt_new_name");
    }

    private void ensureButtonRowIsVisible() {
        Insets insets = getInsets();
        int minimumWidth = Math.max(
            getWidth(),
            buttonPanel.getPreferredSize().width + insets.left + insets.right);
        setMinimumSize(new Dimension(minimumWidth, getHeight()));
        if (getWidth() < minimumWidth) {
            setSize(minimumWidth, getHeight());
        }
    }

    private void closeDialog() {
        windowGeometryPersistence.store(this);
        setVisible(false);
        dispose();
    }

    private void restoreOrPlaceWindow(Component owner) {
        windowGeometryPersistence.restoreOrApplyDefault(this,
            () -> setLocationRelativeTo(owner));
    }

    private static Window findOwnerWindow(Component owner) {
        if (owner instanceof Window) {
            return (Window) owner;
        }
        return owner == null ? null : SwingUtilities.getWindowAncestor(owner);
    }

    enum DirtyDraftDecision {
        SAVE,
        DISCARD,
        CANCEL
    }

    static class WindowGeometryPersistence {
        private final String propertyKey;
        private final ResourceController resourceController;
        private final WindowConfigurationStorage storage;

        WindowGeometryPersistence(String propertyKey) {
            this(propertyKey, ResourceController.getResourceController(),
                new WindowConfigurationStorage(propertyKey));
        }

        WindowGeometryPersistence(String propertyKey,
                                 ResourceController resourceController,
                                 WindowConfigurationStorage storage) {
            this.propertyKey = propertyKey;
            this.resourceController = resourceController;
            this.storage = storage;
        }

        void restoreOrApplyDefault(JDialog dialog, Runnable defaultPlacement) {
            if (resourceController.getProperty(propertyKey) != null) {
                storage.restoreDialogPositions(dialog);
            }
            else if (defaultPlacement != null) {
                defaultPlacement.run();
            }
        }

        void store(JDialog dialog) {
            storage.storeDialogPositions(dialog);
        }
    }

    public static class EditorState {
        private final List<AiPrompt> savedPrompts = new ArrayList<AiPrompt>();
        private String selectedPromptName = "";
        private AiPrompt currentDraft = emptyDraft();

        public void loadState(List<AiPrompt> prompts, AiPromptStore.PersistedDialogState dialogState, String defaultName) {
            savedPrompts.clear();
            for (AiPrompt prompt : AiPromptNameValidator.normalizeAndDeduplicate(prompts, defaultName)) {
                savedPrompts.add(prompt.copy());
            }
            selectedPromptName = dialogState == null ? "" : safe(dialogState.getSelectedPromptName());
            currentDraft = dialogState == null ? emptyDraft() : safePrompt(dialogState.getDraft());
            if (!selectedPromptName.isEmpty() && findSavedPromptIndexByName(selectedPromptName) < 0) {
                selectedPromptName = "";
            }
        }

        public void loadSavedPrompts(List<AiPrompt> prompts) {
            loadState(prompts, new AiPromptStore.PersistedDialogState(), DEFAULT_NEW_PROMPT_NAME);
        }

        public List<AiPrompt> getSavedPrompts() {
            List<AiPrompt> copies = new ArrayList<AiPrompt>();
            for (AiPrompt prompt : savedPrompts) {
                copies.add(prompt.copy());
            }
            return copies;
        }

        public void selectSavedPrompt(int index) {
            AiPrompt selectedPrompt = savedPrompts.get(index).copy();
            selectedPromptName = selectedPrompt.getName();
            currentDraft = selectedPrompt.copy();
        }

        public void beginNewDraft() {
            selectedPromptName = "";
            currentDraft = emptyDraft();
        }

        public boolean isEditingSavedPrompt() {
            return getSelectedSavedPromptIndex() >= 0;
        }

        public boolean canSaveAsNew() {
            return isEditingSavedPrompt();
        }

        public int getSelectedSavedPromptIndex() {
            return findSavedPromptIndexByName(selectedPromptName);
        }

        public AiPrompt getCurrentDraft() {
            return currentDraft.copy();
        }

        public void updateDraft(String name, String prompt, boolean showInChat,
                                String modelSelectionValue,
                                String toolAvailabilitySelectionValue) {
            updateDraft(name, prompt, showInChat, modelSelectionValue, null, null,
                toolAvailabilitySelectionValue);
        }

        public void updateDraft(String name, String prompt, boolean showInChat,
                                String modelSelectionValue,
                                AiThinkingEffort thinkingEffort,
                                AiTemperature temperature,
                                String toolAvailabilitySelectionValue) {
            currentDraft.setName(safe(name));
            currentDraft.setPrompt(safe(prompt));
            currentDraft.setShowInChat(showInChat);
            currentDraft.setModelSelectionValue(safe(modelSelectionValue));
            currentDraft.setThinkingEffort(thinkingEffort);
            currentDraft.setTemperature(temperature);
            currentDraft.setToolAvailabilitySelectionValue(safe(toolAvailabilitySelectionValue));
        }

        public boolean isDirty() {
            return !samePrompt(currentDraft, baselineDraft());
        }

        public void save(String defaultName) {
            AiPrompt normalizedDraft = AiPromptNameValidator.normalizeForSave(
                currentDraft,
                promptsExcludingSelected(),
                defaultName);
            int selectedIndex = getSelectedSavedPromptIndex();
            if (selectedIndex >= 0) {
                savedPrompts.set(selectedIndex, normalizedDraft.copy());
            }
            else {
                savedPrompts.add(normalizedDraft.copy());
            }
            selectedPromptName = normalizedDraft.getName();
            currentDraft = normalizedDraft.copy();
        }

        public void saveAsNew(String defaultName) {
            if (!canSaveAsNew()) {
                throw new IllegalStateException("Save as new requires a selected saved prompt.");
            }
            AiPrompt normalizedDraft = AiPromptNameValidator.normalizeForSave(
                currentDraft,
                getSavedPrompts(),
                defaultName);
            savedPrompts.add(normalizedDraft.copy());
            selectedPromptName = normalizedDraft.getName();
            currentDraft = normalizedDraft.copy();
        }

        public void deleteSelectedPrompt() {
            int selectedIndex = getSelectedSavedPromptIndex();
            if (selectedIndex < 0) {
                return;
            }
            savedPrompts.remove(selectedIndex);
            if (savedPrompts.isEmpty()) {
                beginNewDraft();
                return;
            }
            selectSavedPrompt(Math.min(selectedIndex, savedPrompts.size() - 1));
        }

        public AiPromptStore.PersistedDialogState createPersistedDialogState() {
            return new AiPromptStore.PersistedDialogState(selectedPromptName, currentDraft);
        }

        private List<AiPrompt> promptsExcludingSelected() {
            int selectedIndex = getSelectedSavedPromptIndex();
            List<AiPrompt> prompts = new ArrayList<AiPrompt>();
            for (int index = 0; index < savedPrompts.size(); index++) {
                if (index == selectedIndex) {
                    continue;
                }
                prompts.add(savedPrompts.get(index).copy());
            }
            return prompts;
        }

        private AiPrompt baselineDraft() {
            int selectedIndex = getSelectedSavedPromptIndex();
            return selectedIndex >= 0 ? savedPrompts.get(selectedIndex).copy() : emptyDraft();
        }

        private int findSavedPromptIndexByName(String promptName) {
            for (int index = 0; index < savedPrompts.size(); index++) {
                if (safe(savedPrompts.get(index).getName()).equals(promptName)) {
                    return index;
                }
            }
            return -1;
        }

        private static boolean samePrompt(AiPrompt left, AiPrompt right) {
            return safe(left.getName()).equals(safe(right.getName()))
                && safe(left.getPrompt()).equals(safe(right.getPrompt()))
                && left.isShowInChat() == right.isShowInChat()
                && java.util.Objects.equals(left.getModelConfiguration(), right.getModelConfiguration())
                && safe(left.getToolAvailabilitySelectionValue())
                    .equals(safe(right.getToolAvailabilitySelectionValue()));
        }

        private static AiPrompt safePrompt(AiPrompt prompt) {
            AiPrompt safePrompt = prompt == null ? emptyDraft() : prompt.copy();
            safePrompt.setName(safe(safePrompt.getName()));
            safePrompt.setPrompt(safe(safePrompt.getPrompt()));
            safePrompt.setModelSelectionValue(safe(safePrompt.getModelSelectionValue()));
            safePrompt.setModelConfiguration(safePrompt.getModelConfiguration());
            safePrompt.setToolAvailabilitySelectionValue(safe(safePrompt.getToolAvailabilitySelectionValue()));
            return safePrompt;
        }

        private static String safe(String value) {
            return value == null ? "" : value;
        }

        private static AiPrompt emptyDraft() {
            return new AiPrompt("", "", false, "", "");
        }
    }
}
