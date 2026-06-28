package org.freeplane.plugin.ai.prompt.ui;

import java.util.concurrent.atomic.AtomicReference;
import org.freeplane.api.ai.AiTemperature;
import org.freeplane.core.util.TextUtils;
import org.junit.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

public class AiTemperatureSelectionControllerTest {
    @Test
    public void selectorMapsCurrentModelDefaultPresetsAndCustomValues() {
        AtomicReference<AiTemperature> seenSelection = new AtomicReference<AiTemperature>(AiTemperature.of(0.1));

        try (MockedStatic<TextUtils> textUtils = mockStatic(TextUtils.class)) {
            stubTexts(textUtils);
            AiTemperatureSelectionController selector = new AiTemperatureSelectionController(
                true,
                initialValue -> seenSelection.get().equals(AiTemperature.of(0.1)) ? "0.9" : "broken");
            selector.setTemperatureSelectionChangeListener(seenSelection::set);

            assertThat(selector.getComboBox().getItemCount()).isEqualTo(8);
            assertThat(selector.getComboBox().getItemAt(0).toString()).isEqualTo("Current");
            assertThat(selector.getComboBox().getItemAt(1).toString()).isEqualTo("Model default");
            assertThat(selector.getSelectedTemperature()).isNull();

            selector.getComboBox().setSelectedIndex(1);
            assertThat(seenSelection.get()).isEqualTo(AiTemperature.modelDefault());

            selector.getComboBox().setSelectedIndex(3);
            assertThat(seenSelection.get()).isEqualTo(AiTemperature.of(0.2));

            seenSelection.set(AiTemperature.of(0.1));
            selector.getComboBox().setSelectedIndex(selector.getComboBox().getItemCount() - 1);
            assertThat(seenSelection.get()).isEqualTo(AiTemperature.of(0.9));
            assertThat(selector.getSelectedTemperature()).isEqualTo(AiTemperature.of(0.9));

            selector.getComboBox().setSelectedIndex(selector.getComboBox().getItemCount() - 1);
            assertThat(seenSelection.get()).isEqualTo(AiTemperature.of(0.9));
            assertThat(selector.getSelectedTemperature()).isEqualTo(AiTemperature.of(0.9));
        }
    }

    @Test
    public void selectorWithoutCurrentUsesModelDefaultForUnsetSelection() {
        try (MockedStatic<TextUtils> textUtils = mockStatic(TextUtils.class)) {
            stubTexts(textUtils);
            AiTemperatureSelectionController selector = new AiTemperatureSelectionController(false);

            assertThat(selector.getComboBox().getItemAt(0).toString()).isEqualTo("Model default");
            assertThat(selector.getSelectedTemperature()).isEqualTo(AiTemperature.modelDefault());
        }
    }

    private void stubTexts(MockedStatic<TextUtils> textUtils) {
        textUtils.when(() -> TextUtils.getText("ai_prompt_current")).thenReturn("Current");
        textUtils.when(() -> TextUtils.getText("ai_temperature_model_default")).thenReturn("Model default");
        textUtils.when(() -> TextUtils.getText("ai_temperature_custom")).thenReturn("Custom...");
        textUtils.when(() -> TextUtils.getText("ai_temperature_custom_prompt")).thenReturn("Enter temperature:");
        textUtils.when(() -> TextUtils.getText("ai_prompt_temperature_label")).thenReturn("Temperature");
    }
}
