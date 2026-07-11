package org.freeplane.plugin.ai.chat.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.util.Arrays;
import java.util.Collections;
import java.util.function.Consumer;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import org.freeplane.core.util.TextUtils;
import org.freeplane.plugin.ai.model.AIModelCatalog;
import org.freeplane.plugin.ai.model.AIModelDescriptor;
import org.freeplane.plugin.ai.model.AIProviderConfiguration;
import org.freeplane.plugin.ai.model.ui.AIModelFilterState;
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
    public void selectedModelDisplay_remainsShortened() {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        when(configuration.getOpenRouterKey()).thenReturn("key");
        when(configuration.getStoredSelectedModelValue())
            .thenReturn("openrouter|openai/gpt-4.1-mini");
        when(configuration.getSelectedModelValue())
            .thenReturn("openrouter|openai/gpt-4.1-mini");
        ChatModelSelector uut = newSelector(configuration, mock(AIModelCatalog.class));
        AIModelDescriptor descriptor = new AIModelDescriptor(
            "openrouter",
            "openai/gpt-4.1-mini",
            "OpenRouter: openai/gpt-4.1-mini",
            false
        );

        uut.applyModelSelectionList(Collections.singletonList(descriptor));

        JTextField editor = (JTextField) uut.getModelSelectionComboBox()
            .getEditor().getEditorComponent();
        assertThat(editor.getText()).isEqualTo("gpt-4.1-mini");
        assertThat(uut.getModelSelectionComboBox().getItemAt(0).getDisplayName())
            .isEqualTo("OpenRouter: openai/gpt-4.1-mini");
    }

    @Test
    public void hasAvailableSelectedModel_rejectsUnavailableSelection() {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        when(configuration.getStoredSelectedModelValue()).thenReturn("openrouter|missing/model");
        when(configuration.getSelectedModelValue()).thenReturn("openrouter|missing/model");
        ChatModelSelector uut = newSelector(configuration, mock(AIModelCatalog.class));

        try (MockedStatic<TextUtils> textUtils = mockStatic(TextUtils.class)) {
            textUtils.when(() -> TextUtils.format(
                "ai_unavailable_format",
                "OpenRouter: missing/model"
            )).thenReturn("OpenRouter: missing/model unavailable");
            textUtils.when(() -> TextUtils.getText("ai_unknown_model")).thenReturn("unknown");

            uut.applyModelSelectionList(Collections.emptyList());

            assertThat(uut.hasAvailableSelectedModel()).isFalse();
        }
    }

    @Test
    public void hasAvailableSelectedModel_acceptsCatalogSelection() {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        when(configuration.getStoredSelectedModelValue())
            .thenReturn("openrouter|openai/gpt-4.1-mini");
        when(configuration.getSelectedModelValue())
            .thenReturn("openrouter|openai/gpt-4.1-mini");
        ChatModelSelector uut = newSelector(configuration, mock(AIModelCatalog.class));
        AIModelDescriptor descriptor = new AIModelDescriptor(
            "openrouter",
            "openai/gpt-4.1-mini",
            "OpenRouter: openai/gpt-4.1-mini",
            false
        );

        uut.applyModelSelectionList(Collections.singletonList(descriptor));

        assertThat(uut.hasAvailableSelectedModel()).isTrue();
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
    public void setMinimumAndPreferredWidth_keepsModelSelectorAtLeastGivenMinimum() {
        ChatModelSelector uut = newController();

        uut.setMinimumAndPreferredWidth(150, 120);

        assertThat(uut.getModelSelectionComboBox().getMinimumSize().width).isEqualTo(150);
        assertThat(uut.getModelSelectionComboBox().getPreferredSize().width).isEqualTo(150);

        uut.setMinimumAndPreferredWidth(150, 280);

        assertThat(uut.getModelSelectionComboBox().getMinimumSize().width).isEqualTo(150);
        assertThat(uut.getModelSelectionComboBox().getPreferredSize().width).isEqualTo(280);
    }

    @Test
    public void renderer_showsUnknown_forSelectedUnavailableValueAndFullNameInDropdown() {
        ChatModelSelector uut = newController();
        JComboBox<AIModelDescriptor> selector = uut.getModelSelectionComboBox();
        try (MockedStatic<TextUtils> textUtils = mockStatic(TextUtils.class)) {
            textUtils.when(() -> TextUtils.format(
                "ai_unavailable_format",
                "OpenRouter: openai/gpt-4.1-mini"
            )).thenReturn("OpenRouter: openai/gpt-4.1-mini unavailable");
            textUtils.when(() -> TextUtils.getText("ai_unknown_model")).thenReturn("unknown");
            AIModelDescriptor descriptor = AIModelDescriptor.unavailable(
                "openrouter", "openai/gpt-4.1-mini");

            String selectedValue = renderLabel(selector, descriptor, -1).getText();
            String dropdownValue = renderLabel(selector, descriptor, 0).getText();

            assertThat(selectedValue).isEqualTo("unknown");
            assertThat(dropdownValue)
                .isEqualTo("OpenRouter: openai/gpt-4.1-mini unavailable");
        }
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
        ChatModelSelector uut = newSelector(configuration, mock(AIModelCatalog.class));

        try (MockedStatic<TextUtils> textUtils = mockStatic(TextUtils.class)) {
            textUtils.when(() -> TextUtils.format(
                "ai_unavailable_format",
                "OpenRouter: openai/gpt-4.1-mini"
            )).thenReturn("OpenRouter: openai/gpt-4.1-mini unavailable");
            textUtils.when(() -> TextUtils.getText("ai_unknown_model")).thenReturn("unknown");

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
        ChatModelSelector uut = newSelector(configuration, mock(AIModelCatalog.class));

        try (MockedStatic<TextUtils> textUtils = mockStatic(TextUtils.class)) {
            textUtils.when(() -> TextUtils.format(
                "ai_unavailable_format",
                "OpenRouter: openai/gpt-4.1-mini"
            )).thenReturn("OpenRouter: openai/gpt-4.1-mini unavailable");
            textUtils.when(() -> TextUtils.getText("ai_unknown_model")).thenReturn("unknown");

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
        ChatModelSelector uut = newSelector(configuration, mock(AIModelCatalog.class));
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
            textUtils.when(() -> TextUtils.getText("ai_unknown_model")).thenReturn("unknown");

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
        ChatModelSelector uut = newSelector(configuration, mock(AIModelCatalog.class));
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
        ChatModelSelector uut = newSelector(configuration, modelCatalog);
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
    public void filteredSelection_persistsModelAndRetainsSessionOverrideContract() throws Exception {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        when(configuration.getOpenRouterKey()).thenReturn("key");
        when(configuration.getStoredSelectedModelValue()).thenReturn("gemini|gemini-2.5-flash");
        when(configuration.getSelectedModelValue()).thenReturn("gemini|gemini-2.5-flash");
        AIModelFilterState filterState = new AIModelFilterState();
        filterState.setFilterText("gpt");
        ChatModelSelector uut = newSelector(
            configuration, mock(AIModelCatalog.class), filterState);
        @SuppressWarnings("unchecked")
        Consumer<AIModelDescriptor> normalListener = mock(Consumer.class);
        @SuppressWarnings("unchecked")
        Consumer<AIModelDescriptor> explicitListener = mock(Consumer.class);
        uut.setModelSelectionChangeListener(normalListener);
        uut.setExplicitUserModelSelectionChangeListener(explicitListener);
        AIModelDescriptor geminiDescriptor = new AIModelDescriptor(
            "gemini", "gemini-2.5-flash", "Gemini: gemini-2.5-flash", false);
        AIModelDescriptor openRouterDescriptor = new AIModelDescriptor(
            "openrouter", "openai/gpt-4.1-mini", "OpenRouter: openai/gpt-4.1-mini", false);
        uut.applyModelSelectionList(Arrays.asList(geminiDescriptor, openRouterDescriptor));
        JComboBox<AIModelDescriptor> comboBox = uut.getModelSelectionComboBox();
        SwingUtilities.invokeAndWait(() -> {
            PopupMenuEvent event = new PopupMenuEvent(comboBox);
            for (PopupMenuListener listener : comboBox.getPopupMenuListeners()) {
                listener.popupMenuWillBecomeVisible(event);
            }
            comboBox.setSelectedItem(openRouterDescriptor);
        });

        verify(configuration).setSelectedModelValue("openrouter|openai/gpt-4.1-mini");
        verify(normalListener).accept(openRouterDescriptor);
        verify(explicitListener).accept(openRouterDescriptor);
        assertThat(filterState.getFilterText()).isEqualTo("gpt");
    }

    @Test
    public void selectionChange_persistsProviderAndModelValue() {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        AIModelCatalog modelCatalog = mock(AIModelCatalog.class);
        ChatModelSelector uut = newSelector(configuration, modelCatalog);
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

    private ChatModelSelector newSelector(AIProviderConfiguration configuration,
                                          AIModelCatalog modelCatalog) {
        return newSelector(configuration, modelCatalog, new AIModelFilterState());
    }

    private ChatModelSelector newSelector(AIProviderConfiguration configuration,
                                          AIModelCatalog modelCatalog,
                                          AIModelFilterState filterState) {
        return new ChatModelSelector(
            configuration,
            modelCatalog,
            filterState,
            () -> "No model selected");
    }

    private ChatModelSelector newController() {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        AIModelCatalog modelCatalog = mock(AIModelCatalog.class);
        return newSelector(configuration, modelCatalog);
    }

    private JLabel renderLabel(JComboBox<AIModelDescriptor> selector, AIModelDescriptor descriptor, int index) {
        ListCellRenderer<? super AIModelDescriptor> renderer = selector.getRenderer();
        JList<AIModelDescriptor> list = new JList<>();
        Component component = renderer.getListCellRendererComponent(list, descriptor, index, false, false);
        return (JLabel) component;
    }
}
