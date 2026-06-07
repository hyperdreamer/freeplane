package org.freeplane.plugin.ai.chat.profile;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.resources.WindowConfigurationStorage;

class AssistantProfileManagerDialog extends JDialog {
    private static final long serialVersionUID = 1L;
    static final String WINDOW_CONFIGURATION_PROPERTY =
        "ai_assistant_profile_manager_dialog_window_configuration";

    private final AssistantProfileSelectionModel selectionModel;
    private final DefaultListModel<AssistantProfile> listModel = new DefaultListModel<>();
    private final JList<AssistantProfile> profilesList = new JList<>(listModel);
    private final JTextField nameField = new JTextField();
    private final JTextArea promptArea = new JTextArea();
    private final JButton deleteButton = new JButton("Delete");
    private final WindowGeometryPersistence windowGeometryPersistence;

    AssistantProfileManagerDialog(Window owner, AssistantProfileSelectionModel selectionModel) {
        this(owner, selectionModel,
            new WindowGeometryPersistence(WINDOW_CONFIGURATION_PROPERTY));
    }

    AssistantProfileManagerDialog(Window owner,
                                  AssistantProfileSelectionModel selectionModel,
                                  WindowGeometryPersistence windowGeometryPersistence) {
        super(owner);
        this.selectionModel = selectionModel;
        this.windowGeometryPersistence = windowGeometryPersistence;
        setTitle("Assistant Profiles");
        setModal(true);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                closeDialog();
            }
        });
        setLayout(new BorderLayout(10, 10));
        setPreferredSize(new Dimension(520, 360));
        buildUi();
        loadProfiles();
        pack();
        restoreOrPlaceWindow();
    }

    void openDialog() {
        setVisible(true);
    }

    private void buildUi() {
        profilesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        profilesList.addListSelectionListener(event -> {
            if (event.getValueIsAdjusting()) {
                return;
            }
            AssistantProfile profile = profilesList.getSelectedValue();
            if (profile == null) {
                nameField.setText("");
                promptArea.setText("");
                deleteButton.setEnabled(false);
                return;
            }
            nameField.setText(profile.getName());
            promptArea.setText(profile.getPrompt());
            deleteButton.setEnabled(listModel.getSize() > 1);
        });
        nameField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent event) {
                updateSelectedProfileFromFields();
            }
        });

        JPanel listPanel = new JPanel(new BorderLayout(5, 5));
        listPanel.add(new JLabel("Profiles"), BorderLayout.NORTH);
        listPanel.add(new JScrollPane(profilesList), BorderLayout.CENTER);

        JPanel editorPanel = new JPanel(new BorderLayout(5, 5));
        editorPanel.add(new JLabel("Name"), BorderLayout.NORTH);
        editorPanel.add(nameField, BorderLayout.CENTER);
        JPanel promptPanel = new JPanel(new BorderLayout(5, 5));
        promptPanel.add(new JLabel("Prompt"), BorderLayout.NORTH);
        promptArea.setLineWrap(true);
        promptArea.setWrapStyleWord(true);
        promptPanel.add(new JScrollPane(promptArea), BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new BorderLayout(5, 5));
        rightPanel.add(editorPanel, BorderLayout.NORTH);
        rightPanel.add(promptPanel, BorderLayout.CENTER);

        JPanel contentPanel = new JPanel(new BorderLayout(10, 10));
        contentPanel.add(listPanel, BorderLayout.WEST);
        contentPanel.add(rightPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton newButton = new JButton("New");
        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Cancel");

        newButton.addActionListener(event -> createProfile());
        okButton.addActionListener(event -> confirmDialog());
        deleteButton.addActionListener(event -> deleteProfile());
        cancelButton.addActionListener(event -> closeDialog());

        buttonPanel.add(newButton);
        buttonPanel.add(okButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(cancelButton);

        add(contentPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void loadProfiles() {
        listModel.clear();
        for (AssistantProfile profile : selectionModel.getProfiles()) {
            listModel.addElement(profile);
        }
        if (!listModel.isEmpty()) {
            profilesList.setSelectedIndex(0);
        }
    }

    private void createProfile() {
        AssistantProfile profile = new AssistantProfile(UUID.randomUUID().toString(), "New Profile", "");
        listModel.addElement(profile);
        profilesList.setSelectedValue(profile, true);
        SwingUtilities.invokeLater(nameField::requestFocusInWindow);
    }

    private void deleteProfile() {
        AssistantProfile profile = profilesList.getSelectedValue();
        if (profile == null || listModel.getSize() <= 1) {
            return;
        }
        int index = profilesList.getSelectedIndex();
        listModel.removeElement(profile);
        if (index >= listModel.getSize()) {
            index = listModel.getSize() - 1;
        }
        if (index >= 0) {
            profilesList.setSelectedIndex(index);
        } else {
            nameField.setText("");
            promptArea.setText("");
        }
        persistProfiles();
    }

    private void updateSelectedProfileFromFields() {
        AssistantProfile profile = profilesList.getSelectedValue();
        if (profile == null) {
            return;
        }
        profile.setName(nameField.getText());
        profile.setPrompt(promptArea.getText());
        profilesList.repaint();
    }

    private void confirmDialog() {
        updateSelectedProfileFromFields();
        persistProfiles();
        closeDialog();
    }

    private void persistProfiles() {
        List<AssistantProfile> profiles = new ArrayList<>();
        for (int index = 0; index < listModel.getSize(); index++) {
            profiles.add(listModel.getElementAt(index));
        }
        selectionModel.saveProfiles(profiles);
    }

    private void closeDialog() {
        windowGeometryPersistence.store(this);
        setVisible(false);
        dispose();
    }

    private void restoreOrPlaceWindow() {
        windowGeometryPersistence.restoreOrApplyDefault(this,
            () -> setLocationRelativeTo(null));
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
}
