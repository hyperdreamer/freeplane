package org.freeplane.core.resources.components;

import java.awt.Component;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.tree.DefaultMutableTreeNode;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.resources.components.OptionPanel.IOptionPanelFeedback;
import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.util.TextUtils;

public final class PreferencesDialogLauncher {

    private PreferencesDialogLauncher() {
    }

    public static void open(DefaultMutableTreeNode controls,
            boolean arePropertyValidatorsEnabled, ActionEvent triggeringEvent) {
    	open(controls, null, arePropertyValidatorsEnabled, triggeringEvent);
    }
    public static void open(DefaultMutableTreeNode controls, String selectedProperty,
            boolean arePropertyValidatorsEnabled, ActionEvent triggeringEvent) {
        JDialog dialog = null;
        if (triggeringEvent != null) {
            final Object source = triggeringEvent.getSource();
            if (source instanceof Component) {
                final Window window = SwingUtilities.getWindowAncestor((Component) source);
                dialog = createDialog(window);
            }
        }
        if (dialog == null) {
            dialog = createDialog((Window) UITools.getMenuComponent());
        }
        if (dialog == null) {
            return;
        }
        dialog.setResizable(true);
        dialog.setUndecorated(false);
        final OptionPanel options = new OptionPanel(dialog, new IOptionPanelFeedback() {
            public void writeProperties(final Properties props, final Set<String> userPropertiesToRemove) {
                final ResourceController resources = ResourceController.getResourceController();
                if (applyPreferenceChanges(resources, props, userPropertiesToRemove)) {
                    JOptionPane.showMessageDialog(UITools.getMenuComponent(), TextUtils
                            .getText("option_changes_may_require_restart"));
                    resources.saveProperties();
                    UITools.resetMenuBarOnMac();
                }
            }
        });
        if (arePropertyValidatorsEnabled) {
            options.enablePropertyValidators();
        }

        final String marshalled = ResourceController.getResourceController()
                .getProperty(OptionPanel.PREFERENCE_STORAGE_PROPERTY);
        final OptionPanelWindowConfigurationStorage storage = OptionPanelWindowConfigurationStorage.decorateDialog(
                marshalled, dialog);
        final String actionCommand = triggeringEvent != null ? triggeringEvent.getActionCommand() : null;
        if (actionCommand != null && actionCommand.startsWith(OptionPanelConstants.OPTION_PANEL_RESOURCE_PREFIX)) {
            options.setSelectedPanel(actionCommand);
        }
        else if (storage != null) {
            options.setSelectedPanel(storage.getPanel());
        }
        options.buildPanel(controls);
        options.setProperties();

        final String title = TextUtils.getText("ShowPreferencesAction.dialog");
        dialog.setTitle(title);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(final WindowEvent event) {
                options.closeWindow();
            }
        });
        final Action action = new AbstractAction() {
            private static final long serialVersionUID = 1L;

            public void actionPerformed(final ActionEvent arg0) {
                options.closeWindow();
            }
        };
        UITools.addEscapeActionToDialog(dialog, action);
        if (storage == null) {
            UITools.setBounds(dialog, -1, -1, dialog.getPreferredSize().width + 50, -1);
        }

        if (selectedProperty != null) {
            ComponentListener visibilityListener = new ComponentAdapter() {
                public void componentShown(ComponentEvent evt) {
                    options.highlight(selectedProperty);
                }
            };
            dialog.addComponentListener(visibilityListener);
        }

        dialog.setVisible(true);
    }

    static boolean applyPreferenceChanges(ResourceController resources, Properties properties,
            Set<String> userPropertiesToRemove) {
        boolean propertiesChanged = false;
        for (final Object keyObject : properties.keySet()) {
            final String key = keyObject.toString();
            if (userPropertiesToRemove.contains(key)) {
                continue;
            }
            final String newProperty = properties.getProperty(key);
            propertiesChanged = propertiesChanged || !newProperty.equals(resources.getProperty(key));
            resources.setProperty(key, newProperty);
        }
        for (String key : userPropertiesToRemove) {
            final boolean userPropertyWasSet = resources.isPropertySetByUser(key);
            final String oldValue = resources.getProperty(key);
            resources.removeUserProperty(key);
            final String newValue = resources.getProperty(key);
            propertiesChanged = propertiesChanged || userPropertyWasSet
                    || !Objects.equals(oldValue, newValue);
        }
        return propertiesChanged;
    }

    private static JDialog createDialog(final Window window) {
        if (window instanceof Dialog) {
            final JDialog dialog = new JDialog((Dialog) window, true);
            dialog.applyComponentOrientation(window.getComponentOrientation());
            return dialog;
        }
        if (window instanceof Frame) {
            final JDialog dialog = new JDialog((Frame) window, true);
            dialog.applyComponentOrientation(window.getComponentOrientation());
            return dialog;
        }
        return null;
    }
}
