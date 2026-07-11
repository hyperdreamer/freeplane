package org.freeplane.plugin.ai.prompt.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.Collections;
import java.util.function.Consumer;

import org.freeplane.core.util.TextUtils;
import org.freeplane.plugin.ai.model.AIModelCatalog;
import org.freeplane.plugin.ai.model.AIModelDescriptor;
import org.freeplane.plugin.ai.model.AIProviderConfiguration;
import org.freeplane.plugin.ai.model.ui.AIModelFilterState;
import org.junit.Test;
import org.mockito.MockedStatic;

public class AIModelOverrideSelectorTest {

    @Test
    public void construction_suppliesUseCurrentModelAsAlwaysVisibleOption() {
        try (MockedStatic<TextUtils> textUtils = mockStatic(TextUtils.class)) {
            textUtils.when(() -> TextUtils.getText("ai_prompt_use_current_model")).thenReturn("Use current model");
            AIModelOverrideSelector uut = newSelector();

            uut.applyModelSelectionList(Collections.singletonList(descriptor()), "");

            assertThat(uut.getModelSelectionComboBox().getItemAt(0).getDisplayName())
                .isEqualTo("Use current model");
            assertThat(uut.getSelectedModelSelectionValue()).isEmpty();
        }
    }

    @Test
    public void applyModelSelectionList_keepsUnavailableRestoredSelectionVisible() {
        try (MockedStatic<TextUtils> textUtils = mockStatic(TextUtils.class)) {
            textUtils.when(() -> TextUtils.getText("ai_prompt_use_current_model")).thenReturn("Use current model");
            textUtils.when(() -> TextUtils.format(
                "ai_unavailable_format",
                "OpenRouter: openai/gpt-4.1-mini"
            )).thenReturn("OpenRouter: openai/gpt-4.1-mini unavailable");
            AIModelOverrideSelector uut = newSelector();

            uut.applyModelSelectionList(Collections.singletonList(descriptor()),
                "openrouter|openai/gpt-4.1-mini");

            AIModelDescriptor selectedItem =
                (AIModelDescriptor) uut.getModelSelectionComboBox().getSelectedItem();
            assertThat(selectedItem).isNotNull();
            assertThat(selectedItem.isUnavailable()).isTrue();
            assertThat(selectedItem.getSelectionValue()).isEqualTo("openrouter|openai/gpt-4.1-mini");
            assertThat(selectedItem.getDisplayName())
                .isEqualTo("OpenRouter: openai/gpt-4.1-mini unavailable");
        }
    }

    @Test
    public void explicitSelection_notifiesPromptOrProfileWithSelectionValue() {
        try (MockedStatic<TextUtils> textUtils = mockStatic(TextUtils.class)) {
            textUtils.when(() -> TextUtils.getText("ai_prompt_use_current_model")).thenReturn("Use current model");
            AIModelOverrideSelector uut = newSelector();
            @SuppressWarnings("unchecked")
            Consumer<String> listener = mock(Consumer.class);
            uut.setModelSelectionChangeListener(listener);
            AIModelDescriptor descriptor = descriptor();
            uut.applyModelSelectionList(Collections.singletonList(descriptor), "");

            uut.getModelSelectionComboBox().setSelectedItem(descriptor);
            uut.getModelSelectionComboBox().setSelectedIndex(0);

            verify(listener).accept("gemini|gemini-2.5-flash");
            verify(listener).accept("");
        }
    }

    @Test
    public void programmaticSelection_doesNotNotifyOwner() {
        try (MockedStatic<TextUtils> textUtils = mockStatic(TextUtils.class)) {
            textUtils.when(() -> TextUtils.getText("ai_prompt_use_current_model")).thenReturn("Use current model");
            AIModelOverrideSelector uut = newSelector();
            @SuppressWarnings("unchecked")
            Consumer<String> listener = mock(Consumer.class);
            uut.setModelSelectionChangeListener(listener);
            AIModelDescriptor descriptor = descriptor();
            uut.applyModelSelectionList(Collections.singletonList(descriptor), "");

            uut.setSelectedModelSelectionValue(descriptor.getSelectionValue());
            uut.setSelectedModelSelectionValue("");

            verify(listener, never()).accept(org.mockito.ArgumentMatchers.anyString());
        }
    }

    @Test
    public void applyModelSelectionList_sortsAvailableModelsAfterCurrentOption() {
        try (MockedStatic<TextUtils> textUtils = mockStatic(TextUtils.class)) {
            textUtils.when(() -> TextUtils.getText("ai_prompt_use_current_model")).thenReturn("Use current model");
            AIModelOverrideSelector uut = newSelector();
            AIModelDescriptor openRouter = new AIModelDescriptor(
                "openrouter", "openai/gpt-4.1-mini", "OpenRouter: openai/gpt-4.1-mini", false);

            uut.applyModelSelectionList(Arrays.asList(openRouter, descriptor()), "");

            assertThat(uut.getModelSelectionComboBox().getItemAt(1).getDisplayName())
                .isEqualTo("Gemini: gemini-2.5-flash");
            assertThat(uut.getModelSelectionComboBox().getItemAt(2)).isEqualTo(openRouter);
        }
    }

    private AIModelOverrideSelector newSelector() {
        return new AIModelOverrideSelector(
            mock(AIProviderConfiguration.class),
            mock(AIModelCatalog.class),
            new AIModelFilterState());
    }

    private AIModelDescriptor descriptor() {
        return new AIModelDescriptor(
            "gemini", "gemini-2.5-flash", "Gemini: gemini-2.5-flash", false);
    }
}
