package org.freeplane.plugin.ai.chat.profile;

import java.awt.BorderLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.freeplane.core.ui.textchanger.TranslatedElementFactory;

public class AssistantProfilePaneBuilder {
    public static final String EDIT_PROFILES_TEXT_KEY = "ai_chat_edit_profiles";
    private final AssistantProfileSelectionModel selectionModel;
    private final AssistantProfileSelectionSync selectionSync;
    private final JComboBox<AssistantProfile> selector = new JComboBox<>();
    private final JButton editProfilesButton;
    private boolean updatingSelection;
    private JPanel panel;

    public AssistantProfilePaneBuilder(AssistantProfileSelectionModel selectionModel,
                                AssistantProfileSelectionSync selectionSync,
                                Icon assistantProfileIcon) {
        this.selectionModel = selectionModel;
        this.selectionSync = selectionSync;
        this.editProfilesButton = new JButton(assistantProfileIcon);
    }

    public void initialize() {
        selector.addActionListener(event -> handleAssistantProfileSelection());
        editProfilesButton.addActionListener(event -> openAssistantProfileManager());
        setAssistantProfileSelection(selectionModel.getSelectedProfile(), true);
    }

    public JPanel buildPanel() {
        if (panel == null) {
            panel = new JPanel(new BorderLayout(5, 0));
            panel.add(selector, BorderLayout.CENTER);
            TranslatedElementFactory.createTooltip(editProfilesButton, EDIT_PROFILES_TEXT_KEY);
            panel.add(editProfilesButton, BorderLayout.EAST);
        }
        return panel;
    }

    public void syncSelection(boolean fromTranscriptRestore) {
        AssistantProfile selected = selectionSync.selectForActivation(fromTranscriptRestore);
        setAssistantProfileSelection(selected, false);
    }

    public void setSelectionEnabled(boolean enabled) {
        selector.setEnabled(enabled);
        editProfilesButton.setEnabled(enabled);
    }

    private void handleAssistantProfileSelection() {
        if (updatingSelection) {
            return;
        }
        AssistantProfile profile = (AssistantProfile) selector.getSelectedItem();
        if (profile == null) {
            return;
        }
        selectionSync.handleUserSelection(profile);
    }

    public void openAssistantProfileManager() {
        AssistantProfileManagerDialog dialog = new AssistantProfileManagerDialog(
            SwingUtilities.getWindowAncestor(panel),
            selectionModel);
        dialog.openDialog();
        AssistantProfile current = selectionModel.getSelectedProfile();
        selectionModel.reloadProfiles();
        if (current != null) {
            selectionModel.selectById(current.getId());
            setAssistantProfileSelection(selectionModel.getSelectedProfile(), false);
        } else {
            setAssistantProfileSelection(selectionModel.getSelectedProfile(), false);
        }
    }

    private void setAssistantProfileSelection(AssistantProfile profile, boolean updateLastUsed) {
        updatingSelection = true;
        AssistantProfile resolved = profile == null ? AssistantProfile.defaultProfile() : profile;
        DefaultComboBoxModel<AssistantProfile> model = new DefaultComboBoxModel<>(
            selectionModel.getProfiles().toArray(new AssistantProfile[0]));
        selector.setModel(model);
        selector.setSelectedItem(resolved);
        selectionModel.setSelectedProfile(resolved, updateLastUsed);
        updatingSelection = false;
    }
}
