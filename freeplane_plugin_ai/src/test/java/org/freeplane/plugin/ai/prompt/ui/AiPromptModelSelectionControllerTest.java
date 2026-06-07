package org.freeplane.plugin.ai.prompt.ui;

import java.util.Collections;
import org.freeplane.core.util.TextUtils;
import org.freeplane.plugin.ai.model.AIModelCatalog;
import org.freeplane.plugin.ai.model.AIModelDescriptor;
import org.freeplane.plugin.ai.model.AIProviderConfiguration;
import org.junit.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

public class AiPromptModelSelectionControllerTest {

    @Test
    public void applyModelSelectionList_prependsUseCurrentModelOption() {
        AiPromptModelSelectionController uut = newController();

        try (MockedStatic<TextUtils> textUtils = mockStatic(TextUtils.class)) {
            textUtils.when(() -> TextUtils.getText("ai_prompt_use_current_model")).thenReturn("Use current model");

            uut.applyModelSelectionList(Collections.singletonList(new AIModelDescriptor(
                "openrouter",
                "openai/gpt-4.1-mini",
                "OpenRouter: openai/gpt-4.1-mini",
                false
            )), "");
        }

        assertThat(uut.getModelSelectionComboBox().getItemAt(0).getDisplayName()).isEqualTo("Use current model");
        assertThat(uut.getSelectedModelSelectionValue()).isEmpty();
    }

    @Test
    public void applyModelSelectionList_keepsUnavailableRestoredSelectionVisible() {
        AiPromptModelSelectionController uut = newController();

        try (MockedStatic<TextUtils> textUtils = mockStatic(TextUtils.class)) {
            textUtils.when(() -> TextUtils.getText("ai_prompt_use_current_model")).thenReturn("Use current model");
            textUtils.when(() -> TextUtils.format(
                "ai_unavailable_format",
                "OpenRouter: openai/gpt-4.1-mini"
            )).thenReturn("OpenRouter: openai/gpt-4.1-mini unavailable");

            uut.applyModelSelectionList(Collections.singletonList(new AIModelDescriptor(
                "gemini",
                "gemini-2.5-flash",
                "Gemini: gemini-2.5-flash",
                false
            )), "openrouter|openai/gpt-4.1-mini");
        }

        AIModelDescriptor selectedItem = (AIModelDescriptor) uut.getModelSelectionComboBox().getSelectedItem();
        assertThat(selectedItem).isNotNull();
        assertThat(selectedItem.isUnavailable()).isTrue();
        assertThat(selectedItem.getSelectionValue()).isEqualTo("openrouter|openai/gpt-4.1-mini");
        assertThat(selectedItem.getDisplayName()).isEqualTo("OpenRouter: openai/gpt-4.1-mini unavailable");
    }

    @Test
    public void setSelectedModelSelectionValue_selectsCurrentModelOptionForBlankValue() {
        AiPromptModelSelectionController uut = newController();

        try (MockedStatic<TextUtils> textUtils = mockStatic(TextUtils.class)) {
            textUtils.when(() -> TextUtils.getText("ai_prompt_use_current_model")).thenReturn("Use current model");

            uut.applyModelSelectionList(Collections.singletonList(new AIModelDescriptor(
                "gemini",
                "gemini-2.5-flash",
                "Gemini: gemini-2.5-flash",
                false
            )), "gemini|gemini-2.5-flash");
            uut.setSelectedModelSelectionValue("");
        }

        assertThat(uut.getSelectedModelSelectionValue()).isEmpty();
        assertThat(((AIModelDescriptor) uut.getModelSelectionComboBox().getSelectedItem()).getDisplayName())
            .isEqualTo("Use current model");
    }

    private AiPromptModelSelectionController newController() {
        return new AiPromptModelSelectionController(mock(AIProviderConfiguration.class), mock(AIModelCatalog.class));
    }
}
