package org.freeplane.plugin.ai.chat.ui;

import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.freeplane.api.ai.AiTemperature;
import org.freeplane.core.util.TextUtils;
import org.junit.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

public class ChatTemperatureMenuTest {
    @Test
    public void menuHasNoCurrentItemAndWritesSelectedTemperature() {
        AtomicReference<AiTemperature> effectiveTemperature =
            new AtomicReference<AiTemperature>(AiTemperature.modelDefault());
        AtomicReference<AiTemperature> seenSelection = new AtomicReference<AiTemperature>();

        try (MockedStatic<TextUtils> textUtils = mockStatic(TextUtils.class)) {
            stubTexts(textUtils);
            JPopupMenu popupMenu = new JPopupMenu();
            ChatTemperatureMenu menu = new ChatTemperatureMenu(
                effectiveTemperature::get,
                temperature -> {
                    seenSelection.set(temperature);
                    effectiveTemperature.set(temperature);
                });

            menu.addTo(popupMenu);
            menu.refreshSelection();

            JMenu temperatureMenu = (JMenu) popupMenu.getComponent(0);
            assertThat(menuItemTexts(temperatureMenu)).doesNotContain("Current");
            assertThat(menuItemTexts(temperatureMenu)).contains("Model default", "0", "0.2", "0.5", "0.7", "1.0", "Custom...");
            assertThat(temperatureMenu.getItem(0).isSelected()).isTrue();

            temperatureMenu.getItem(2).doClick();

            assertThat(seenSelection.get()).isEqualTo(AiTemperature.of(0.2));
        }
    }

    @Test
    public void customInputAcceptsFiniteNumberAndRejectsInvalidInput() {
        AtomicReference<AiTemperature> effectiveTemperature =
            new AtomicReference<AiTemperature>(AiTemperature.modelDefault());
        AtomicReference<AiTemperature> seenSelection = new AtomicReference<AiTemperature>();

        try (MockedStatic<TextUtils> textUtils = mockStatic(TextUtils.class)) {
            stubTexts(textUtils);
            JPopupMenu popupMenu = new JPopupMenu();
            ChatTemperatureMenu menu = new ChatTemperatureMenu(
                effectiveTemperature::get,
                temperature -> {
                    seenSelection.set(temperature);
                    effectiveTemperature.set(temperature);
                },
                initialValue -> seenSelection.get() == null ? "0.9" : "broken");

            menu.addTo(popupMenu);
            JMenu temperatureMenu = (JMenu) popupMenu.getComponent(0);
            temperatureMenu.getItem(7).doClick();

            assertThat(seenSelection.get()).isEqualTo(AiTemperature.of(0.9));
            menu.refreshSelection();
            assertThat(temperatureMenu.getItem(6).isVisible()).isTrue();
            assertThat(temperatureMenu.getItem(6).getText()).isEqualTo("0.9");
            assertThat(temperatureMenu.getItem(6).isSelected()).isTrue();

            temperatureMenu.getItem(7).doClick();

            assertThat(seenSelection.get()).isEqualTo(AiTemperature.of(0.9));
        }
    }

    private String[] menuItemTexts(JMenu menu) {
        String[] texts = new String[menu.getItemCount()];
        for (int index = 0; index < menu.getItemCount(); index++) {
            JMenuItem item = menu.getItem(index);
            texts[index] = item == null ? null : item.getText();
        }
        return texts;
    }

    private void stubTexts(MockedStatic<TextUtils> textUtils) {
        textUtils.when(() -> TextUtils.getRawText("ai_chat_temperature_label")).thenReturn("Temperature");
        textUtils.when(() -> TextUtils.getRawText("ai_temperature_custom")).thenReturn("Custom...");
        textUtils.when(() -> TextUtils.removeMnemonic("Temperature")).thenReturn("Temperature");
        textUtils.when(() -> TextUtils.removeMnemonic("Custom...")).thenReturn("Custom...");
        textUtils.when(() -> TextUtils.getText("ai_chat_temperature_label")).thenReturn("Temperature");
        textUtils.when(() -> TextUtils.getText("ai_temperature_custom_prompt")).thenReturn("Enter temperature:");
        textUtils.when(() -> TextUtils.getText("ai_temperature_model_default")).thenReturn("Model default");
        textUtils.when(() -> TextUtils.getOptionalText("ai_chat_temperature_label", null)).thenReturn("Temperature");
        textUtils.when(() -> TextUtils.getOptionalText("ai_temperature_custom", null)).thenReturn("Custom...");
    }
}
