package org.freeplane.plugin.ai.chat.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JTextArea;

import org.junit.Test;

public class ChatInputControlsTest {
    @Test
    public void unavailableSelectedModel_disablesSendingButKeepsInputEditable() {
        JTextArea inputArea = new JTextArea("draft");
        JButton sendButton = new JButton();
        ChatInputControls uut = controls(inputArea, sendButton, new AtomicInteger());

        uut.update(false, true, false);

        assertThat(inputArea.isEditable()).isTrue();
        assertThat(inputArea.getText()).isEqualTo("draft");
        assertThat(sendButton.isEnabled()).isFalse();
        assertThat(sendButton.getToolTipText()).isEqualTo("Select an available AI model.");
    }

    @Test
    public void availableSelectedModel_reenablesSending() {
        JTextArea inputArea = new JTextArea("draft");
        JButton sendButton = new JButton();
        ChatInputControls uut = controls(inputArea, sendButton, new AtomicInteger());
        uut.update(false, true, false);

        uut.update(false, true, true);

        assertThat(sendButton.isEnabled()).isTrue();
        assertThat(sendButton.getToolTipText()).isEqualTo("Send");
    }

    private ChatInputControls controls(JTextArea inputArea,
                                       JButton sendButton,
                                       AtomicInteger updates) {
        return new ChatInputControls(
            inputArea,
            sendButton,
            mock(Icon.class),
            mock(Icon.class),
            mock(Icon.class),
            "Send",
            "Cancel",
            "Preferences",
            "No provider configured",
            "Select an available AI model.",
            updates::incrementAndGet);
    }
}
