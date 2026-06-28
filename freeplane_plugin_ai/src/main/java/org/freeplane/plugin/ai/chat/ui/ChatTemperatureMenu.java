package org.freeplane.plugin.ai.chat.ui;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import org.freeplane.api.ai.AiTemperature;
import org.freeplane.core.ui.LabelAndMnemonicSetter;
import org.freeplane.core.ui.textchanger.TranslatedElement;
import org.freeplane.core.ui.textchanger.TranslatedElementFactory;
import org.freeplane.core.util.TextUtils;
import org.freeplane.plugin.ai.model.AIModelTemperatureStorage;

class ChatTemperatureMenu {
    private static final String[] PRESET_VALUES = {"0", "0.2", "0.5", "0.7", "1.0"};

    private final Supplier<AiTemperature> effectiveTemperatureSupplier;
    private final Consumer<AiTemperature> explicitUserSelectionHandler;
    private final Function<String, String> customInputProvider;
    private final Map<String, JRadioButtonMenuItem> numericItems = new LinkedHashMap<String, JRadioButtonMenuItem>();
    private JRadioButtonMenuItem modelDefaultItem;
    private JRadioButtonMenuItem customValueItem;

    ChatTemperatureMenu(Supplier<AiTemperature> effectiveTemperatureSupplier,
                        Consumer<AiTemperature> explicitUserSelectionHandler) {
        this(effectiveTemperatureSupplier, explicitUserSelectionHandler, null);
    }

    ChatTemperatureMenu(Supplier<AiTemperature> effectiveTemperatureSupplier,
                        Consumer<AiTemperature> explicitUserSelectionHandler,
                        Function<String, String> customInputProvider) {
        this.effectiveTemperatureSupplier = effectiveTemperatureSupplier;
        this.explicitUserSelectionHandler = explicitUserSelectionHandler;
        this.customInputProvider = customInputProvider;
    }

    void addTo(JPopupMenu menuPopup) {
        JMenu temperatureMenu = TranslatedElementFactory.createMenu("ai_chat_temperature_label");
        ButtonGroup buttonGroup = new ButtonGroup();
        modelDefaultItem = addRadioItem(
            temperatureMenu,
            buttonGroup,
            TextUtils.getText("ai_temperature_model_default"),
            () -> explicitUserSelectionHandler.accept(AiTemperature.modelDefault()));
        for (String presetValue : PRESET_VALUES) {
            JRadioButtonMenuItem item = addRadioItem(
                temperatureMenu,
                buttonGroup,
                presetValue,
                () -> explicitUserSelectionHandler.accept(
                    AIModelTemperatureStorage.fromStoredValue(presetValue)));
            numericItems.put(presetValue, item);
        }
        customValueItem = addRadioItem(temperatureMenu, buttonGroup, "", () -> {
        });
        customValueItem.setVisible(false);
        temperatureMenu.add(createCustomActionItem());
        menuPopup.add(temperatureMenu);
    }

    void refreshSelection() {
        AiTemperature temperature = effectiveTemperatureSupplier.get();
        if (temperature == null || temperature.isModelDefault()) {
            hideCustomValueItem();
            if (modelDefaultItem != null) {
                modelDefaultItem.setSelected(true);
            }
            return;
        }
        String value = AIModelTemperatureStorage.toPreferenceValue(temperature);
        JRadioButtonMenuItem presetItem = presetItemFor(value);
        if (presetItem != null) {
            hideCustomValueItem();
            presetItem.setSelected(true);
            return;
        }
        if (customValueItem != null) {
            customValueItem.setText(value);
            customValueItem.setVisible(true);
            customValueItem.setSelected(true);
        }
    }

    private JRadioButtonMenuItem presetItemFor(String value) {
        for (Map.Entry<String, JRadioButtonMenuItem> entry : numericItems.entrySet()) {
            if (AIModelTemperatureStorage.sameNumericValue(entry.getKey(), value)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private void hideCustomValueItem() {
        if (customValueItem != null) {
            customValueItem.setVisible(false);
            customValueItem.setSelected(false);
        }
    }

    private JRadioButtonMenuItem addRadioItem(JMenu menu,
                                              ButtonGroup buttonGroup,
                                              String text,
                                              Runnable action) {
        JRadioButtonMenuItem menuItem = new JRadioButtonMenuItem(text);
        buttonGroup.add(menuItem);
        menuItem.addActionListener(event -> action.run());
        menu.add(menuItem);
        return menuItem;
    }

    private JMenuItem createCustomActionItem() {
        JMenuItem menuItem = new JMenuItem();
        LabelAndMnemonicSetter.setLabelAndMnemonic(menuItem, TextUtils.getRawText("ai_temperature_custom"));
        TranslatedElement.TEXT.setKey(menuItem, "ai_temperature_custom");
        menuItem.addActionListener(event -> handleCustomSelection());
        return menuItem;
    }

    private void handleCustomSelection() {
        String input = requestCustomTemperature();
        if (input == null) {
            refreshSelection();
            return;
        }
        AiTemperature temperature = AIModelTemperatureStorage.fromStoredValue(input);
        if (temperature == null || !temperature.isNumeric()) {
            refreshSelection();
            return;
        }
        explicitUserSelectionHandler.accept(temperature);
        refreshSelection();
    }

    private String requestCustomTemperature() {
        AiTemperature currentTemperature = effectiveTemperatureSupplier.get();
        String initialValue = currentTemperature != null && currentTemperature.isNumeric()
            ? AIModelTemperatureStorage.toPreferenceValue(currentTemperature)
            : "";
        if (customInputProvider != null) {
            return customInputProvider.apply(initialValue);
        }
        Object input = JOptionPane.showInputDialog(
            null,
            TextUtils.getText("ai_temperature_custom_prompt"),
            TextUtils.getText("ai_chat_temperature_label"),
            JOptionPane.QUESTION_MESSAGE,
            null,
            null,
            initialValue);
        return input == null ? null : input.toString();
    }
}
