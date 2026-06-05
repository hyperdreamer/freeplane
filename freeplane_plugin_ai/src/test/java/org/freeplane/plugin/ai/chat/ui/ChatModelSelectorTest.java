package org.freeplane.plugin.ai.chat.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.util.Collections;
import java.util.function.Consumer;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import org.freeplane.core.util.TextUtils;
import org.freeplane.plugin.ai.model.AIModelCatalog;
import org.freeplane.plugin.ai.model.AIModelDescriptor;
import org.freeplane.plugin.ai.model.AIProviderConfiguration;
import org.junit.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ChatModelSelectorTest {
    @Test
    public void renderer_showsTextAfterFirstSlash_forSelectedValue() {
        ChatModelSelector uut = newController();
        JComboBox<AIModelDescriptor> selector = uut.getModelSelectionComboBox();
        AIModelDescriptor descriptor = new AIModelDescriptor(
            "openrouter",
            "openai/gpt-4.1-mini",
            "OpenRouter: openai/gpt-4.1-mini",
            false
        );

        JLabel label = renderLabel(selector, descriptor, -1);

        assertThat(label.getText()).isEqualTo("gpt-4.1-mini");
    }

    @Test
    public void renderer_showsDisplayName_forDropdownItems() {
        ChatModelSelector uut = newController();
        JComboBox<AIModelDescriptor> selector = uut.getModelSelectionComboBox();
        AIModelDescriptor descriptor = new AIModelDescriptor(
            "openrouter",
            "openai/gpt-4.1-mini",
            "OpenRouter: openai/gpt-4.1-mini",
            false
        );

        JLabel label = renderLabel(selector, descriptor, 0);

        assertThat(label.getText()).isEqualTo("OpenRouter: openai/gpt-4.1-mini");
    }

    @Test
    public void renderer_usesDisplayNameForPreferredSize_whenSelectedValueIsShortened() {
        ChatModelSelector uut = newController();
        JComboBox<AIModelDescriptor> selector = uut.getModelSelectionComboBox();
        AIModelDescriptor descriptor = new AIModelDescriptor(
            "openrouter",
            "openai/gpt-4.1-mini",
            "OpenRouter: openai/gpt-4.1-mini",
            false
        );

        JLabel selectedLabel = renderLabel(selector, descriptor, -1);
        String selectedText = selectedLabel.getText();
        Dimension selectedPreferredSize = selectedLabel.getPreferredSize();
        JLabel dropdownLabel = renderLabel(selector, descriptor, 0);
        Dimension dropdownPreferredSize = dropdownLabel.getPreferredSize();

        assertThat(selectedText).isEqualTo("gpt-4.1-mini");
        assertThat(selectedPreferredSize.width).isGreaterThanOrEqualTo(dropdownPreferredSize.width);
    }

    @Test
    public void renderer_showsFormattedDisplayName_forSelectedUnavailableValue() {
        ChatModelSelector uut = newController();
        JComboBox<AIModelDescriptor> selector = uut.getModelSelectionComboBox();
        AIModelDescriptor descriptor;
        try (MockedStatic<TextUtils> textUtils = mockStatic(TextUtils.class)) {
            textUtils.when(() -> TextUtils.format(
                "ai_unavailable_format",
                "OpenRouter: openai/gpt-4.1-mini"
            )).thenReturn("OpenRouter: openai/gpt-4.1-mini unavailable");
            descriptor = AIModelDescriptor.unavailable("openrouter", "openai/gpt-4.1-mini");
        }

        JLabel label = renderLabel(selector, descriptor, -1);

        assertThat(label.getText()).isEqualTo("OpenRouter: openai/gpt-4.1-mini unavailable");
    }

    @Test
    public void constructor_usesRegularComboboxSizing() {
        ChatModelSelector uut = newController();
        JComboBox<AIModelDescriptor> selector = uut.getModelSelectionComboBox();

        assertThat(selector.getPrototypeDisplayValue()).isNull();
        assertThat(selector.getItemCount()).isZero();
    }

    @Test
    public void applyModelSelectionList_keepsUnavailableStoredSelectionSelected() {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        when(configuration.getStoredSelectedModelValue()).thenReturn("openrouter|openai/gpt-4.1-mini");
        when(configuration.getSelectedModelValue()).thenReturn("openrouter|openai/gpt-4.1-mini");
        ChatModelSelector uut = new ChatModelSelector(configuration, mock(AIModelCatalog.class));

        try (MockedStatic<TextUtils> textUtils = mockStatic(TextUtils.class)) {
            textUtils.when(() -> TextUtils.format(
                "ai_unavailable_format",
                "OpenRouter: openai/gpt-4.1-mini"
            )).thenReturn("OpenRouter: openai/gpt-4.1-mini unavailable");

            uut.applyModelSelectionList(Collections.singletonList(new AIModelDescriptor(
                "gemini",
                "gemini-2.5-flash",
                "Gemini: gemini-2.5-flash",
                false
            )));
        }

        AIModelDescriptor selectedItem = (AIModelDescriptor) uut.getModelSelectionComboBox().getSelectedItem();
        assertThat(selectedItem).isNotNull();
        assertThat(selectedItem.getSelectionValue()).isEqualTo("openrouter|openai/gpt-4.1-mini");
        assertThat(selectedItem.isUnavailable()).isTrue();
        verify(configuration, never()).setSelectedModelValue("");
    }

    @Test
    public void applyModelSelectionList_persistsLegacySelectionWhenUnavailable() {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        when(configuration.getStoredSelectedModelValue()).thenReturn(null);
        when(configuration.getSelectedModelValue()).thenReturn("openrouter|openai/gpt-4.1-mini");
        ChatModelSelector uut = new ChatModelSelector(configuration, mock(AIModelCatalog.class));

        try (MockedStatic<TextUtils> textUtils = mockStatic(TextUtils.class)) {
            textUtils.when(() -> TextUtils.format(
                "ai_unavailable_format",
                "OpenRouter: openai/gpt-4.1-mini"
            )).thenReturn("OpenRouter: openai/gpt-4.1-mini unavailable");

            uut.applyModelSelectionList(Collections.singletonList(new AIModelDescriptor(
                "gemini",
                "gemini-2.5-flash",
                "Gemini: gemini-2.5-flash",
                false
            )));
        }

        verify(configuration).setSelectedModelValue("openrouter|openai/gpt-4.1-mini");
    }

    @Test
    public void selectionChange_fromUnavailableToAvailable_persistsNewSelectionValue() {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        when(configuration.getStoredSelectedModelValue()).thenReturn("openrouter|openai/gpt-4.1-mini");
        when(configuration.getSelectedModelValue()).thenReturn("openrouter|openai/gpt-4.1-mini");
        ChatModelSelector uut = new ChatModelSelector(configuration, mock(AIModelCatalog.class));
        AIModelDescriptor availableDescriptor = new AIModelDescriptor(
            "gemini",
            "gemini-2.5-flash",
            "Gemini: gemini-2.5-flash",
            false
        );

        try (MockedStatic<TextUtils> textUtils = mockStatic(TextUtils.class)) {
            textUtils.when(() -> TextUtils.format(
                "ai_unavailable_format",
                "OpenRouter: openai/gpt-4.1-mini"
            )).thenReturn("OpenRouter: openai/gpt-4.1-mini unavailable");

            uut.applyModelSelectionList(Collections.singletonList(availableDescriptor));
        }
        uut.getModelSelectionComboBox().setSelectedItem(availableDescriptor);

        verify(configuration, atLeastOnce()).setSelectedModelValue("gemini|gemini-2.5-flash");
    }

    @Test
    public void setDisplayedSelectionValueOverride_showsOverrideWithoutPersistingConfiguration() {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        when(configuration.getStoredSelectedModelValue()).thenReturn("gemini|gemini-2.5-flash");
        when(configuration.getSelectedModelValue()).thenReturn("gemini|gemini-2.5-flash");
        ChatModelSelector uut = new ChatModelSelector(configuration, mock(AIModelCatalog.class));
        AIModelDescriptor geminiDescriptor = new AIModelDescriptor(
            "gemini",
            "gemini-2.5-flash",
            "Gemini: gemini-2.5-flash",
            false
        );
        AIModelDescriptor openRouterDescriptor = new AIModelDescriptor(
            "openrouter",
            "openai/gpt-4.1-mini",
            "OpenRouter: openai/gpt-4.1-mini",
            false
        );

        uut.applyModelSelectionList(java.util.Arrays.asList(geminiDescriptor, openRouterDescriptor));
        uut.setDisplayedSelectionValueOverride("openrouter|openai/gpt-4.1-mini");

        AIModelDescriptor selectedItem = (AIModelDescriptor) uut.getModelSelectionComboBox().getSelectedItem();
        assertThat(selectedItem).isEqualTo(openRouterDescriptor);
        verify(configuration, never()).setSelectedModelValue("openrouter|openai/gpt-4.1-mini");
    }

    @Test
    public void selectionChange_withDisplayedSelectionOverride_notifiesExplicitUserListener() {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        when(configuration.getStoredSelectedModelValue()).thenReturn("gemini|gemini-2.5-flash");
        when(configuration.getSelectedModelValue()).thenReturn("gemini|gemini-2.5-flash");
        AIModelCatalog modelCatalog = mock(AIModelCatalog.class);
        ChatModelSelector uut = new ChatModelSelector(configuration, modelCatalog);
        @SuppressWarnings("unchecked")
        Consumer<AIModelDescriptor> listener = mock(Consumer.class);
        uut.setExplicitUserModelSelectionChangeListener(listener);
        JComboBox<AIModelDescriptor> selector = uut.getModelSelectionComboBox();
        AIModelDescriptor geminiDescriptor = new AIModelDescriptor(
            "gemini",
            "gemini-2.5-flash",
            "Gemini: gemini-2.5-flash",
            false
        );
        AIModelDescriptor openRouterDescriptor = new AIModelDescriptor(
            "openrouter",
            "openai/gpt-4.1-mini",
            "OpenRouter: openai/gpt-4.1-mini",
            false
        );

        uut.applyModelSelectionList(java.util.Arrays.asList(geminiDescriptor, openRouterDescriptor));
        uut.setDisplayedSelectionValueOverride("openrouter|openai/gpt-4.1-mini");
        selector.setSelectedItem(geminiDescriptor);

        verify(configuration, atLeastOnce()).setSelectedModelValue("gemini|gemini-2.5-flash");
        verify(listener).accept(geminiDescriptor);
    }

    @Test
    public void selectionChange_persistsProviderAndModelValue() {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        AIModelCatalog modelCatalog = mock(AIModelCatalog.class);
        ChatModelSelector uut = new ChatModelSelector(configuration, modelCatalog);
        JComboBox<AIModelDescriptor> selector = uut.getModelSelectionComboBox();
        AIModelDescriptor descriptor = new AIModelDescriptor(
            "openrouter",
            "openai/gpt-4.1-mini",
            "OpenRouter: openai/gpt-4.1-mini",
            false
        );

        selector.addItem(descriptor);
        selector.setSelectedItem(descriptor);

        verify(configuration, atLeastOnce()).setSelectedModelValue("openrouter|openai/gpt-4.1-mini");
    }

    private ChatModelSelector newController() {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        AIModelCatalog modelCatalog = mock(AIModelCatalog.class);
        return new ChatModelSelector(configuration, modelCatalog);
    }

    private JLabel renderLabel(JComboBox<AIModelDescriptor> selector, AIModelDescriptor descriptor, int index) {
        ListCellRenderer<? super AIModelDescriptor> renderer = selector.getRenderer();
        JList<AIModelDescriptor> list = new JList<>();
        Component component = renderer.getListCellRendererComponent(list, descriptor, index, false, false);
        return (JLabel) component;
    }
}
