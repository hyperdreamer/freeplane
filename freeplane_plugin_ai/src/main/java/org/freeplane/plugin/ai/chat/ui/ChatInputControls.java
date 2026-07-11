package org.freeplane.plugin.ai.chat.ui;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JTextArea;

class ChatInputControls {
    private final JTextArea inputArea;
    private final JButton sendButton;
    private final Icon sendIcon;
    private final Icon stopIcon;
    private final Icon preferencesIcon;
    private final String sendTooltipText;
    private final String cancelTooltipText;
    private final String preferencesTooltipText;
    private final String noProviderConfiguredText;
    private final String modelUnavailableText;
    private final Runnable undoRedoButtonStateUpdater;

    ChatInputControls(JTextArea inputArea,
                      JButton sendButton,
                      Icon sendIcon,
                      Icon stopIcon,
                      Icon preferencesIcon,
                      String sendTooltipText,
                      String cancelTooltipText,
                      String preferencesTooltipText,
                      String noProviderConfiguredText,
                      String modelUnavailableText,
                      Runnable undoRedoButtonStateUpdater) {
        this.inputArea = inputArea;
        this.sendButton = sendButton;
        this.sendIcon = sendIcon;
        this.stopIcon = stopIcon;
        this.preferencesIcon = preferencesIcon;
        this.sendTooltipText = sendTooltipText;
        this.cancelTooltipText = cancelTooltipText;
        this.preferencesTooltipText = preferencesTooltipText;
        this.noProviderConfiguredText = noProviderConfiguredText;
        this.modelUnavailableText = modelUnavailableText;
        this.undoRedoButtonStateUpdater = undoRedoButtonStateUpdater;
    }

    void update(boolean requestActive, boolean providerConfigured,
                boolean selectedModelAvailable) {
        if (requestActive) {
            undoRedoButtonStateUpdater.run();
            return;
        }
        if (!providerConfigured) {
            setNoProviderState();
        }
        else if (!selectedModelAvailable) {
            setModelUnavailableState();
        }
        else {
            setProviderReadyState();
        }
        undoRedoButtonStateUpdater.run();
    }

    void setRequestActiveState() {
        sendButton.setText(null);
        sendButton.setIcon(stopIcon);
        sendButton.setToolTipText(cancelTooltipText);
    }

    private void setProviderReadyState() {
        inputArea.setEditable(true);
        sendButton.setEnabled(true);
        if (noProviderConfiguredText != null && noProviderConfiguredText.equals(inputArea.getText())) {
            inputArea.setText("");
        }
        sendButton.setText(null);
        sendButton.setIcon(sendIcon);
        sendButton.setToolTipText(sendTooltipText);
    }

    private void setModelUnavailableState() {
        inputArea.setEditable(true);
        sendButton.setEnabled(false);
        if (noProviderConfiguredText != null && noProviderConfiguredText.equals(inputArea.getText())) {
            inputArea.setText("");
        }
        sendButton.setText(null);
        sendButton.setIcon(sendIcon);
        sendButton.setToolTipText(modelUnavailableText);
    }

    private void setNoProviderState() {
        inputArea.setEditable(false);
        sendButton.setEnabled(true);
        inputArea.setText(noProviderConfiguredText);
        inputArea.setCaretPosition(0);
        sendButton.setText(null);
        sendButton.setIcon(preferencesIcon);
        sendButton.setToolTipText(preferencesTooltipText);
    }

}
