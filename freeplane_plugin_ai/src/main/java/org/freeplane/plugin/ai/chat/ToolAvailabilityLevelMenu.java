package org.freeplane.plugin.ai.chat;

import java.util.EnumMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import org.freeplane.core.ui.LabelAndMnemonicSetter;
import org.freeplane.core.ui.textchanger.TranslatedElement;
import org.freeplane.core.ui.textchanger.TranslatedElementFactory;
import org.freeplane.core.util.TextUtils;

class ToolAvailabilityLevelMenu {
    private final Supplier<ToolAvailabilityLevel> effectiveToolAvailabilitySupplier;
    private final Consumer<ToolAvailabilityLevel> explicitUserSelectionHandler;
    private final EnumMap<ToolAvailabilityLevel, JRadioButtonMenuItem> toolAvailabilityMenuItems =
        new EnumMap<ToolAvailabilityLevel, JRadioButtonMenuItem>(ToolAvailabilityLevel.class);

    ToolAvailabilityLevelMenu(Supplier<ToolAvailabilityLevel> effectiveToolAvailabilitySupplier,
                             Consumer<ToolAvailabilityLevel> explicitUserSelectionHandler) {
        this.effectiveToolAvailabilitySupplier = effectiveToolAvailabilitySupplier;
        this.explicitUserSelectionHandler = explicitUserSelectionHandler;
    }

    void addTo(JPopupMenu menuPopup) {
        JMenu toolAvailabilityMenu = TranslatedElementFactory.createMenu(
            "OptionPanel." + ToolAvailabilityLevelSettings.TOOL_AVAILABILITY_PROPERTY);
        ButtonGroup buttonGroup = new ButtonGroup();
        addMenuItem(toolAvailabilityMenu, buttonGroup, ToolAvailabilityLevel.SCRIPT_EXECUTION);
        addMenuItem(toolAvailabilityMenu, buttonGroup, ToolAvailabilityLevel.EDITING);
        addMenuItem(toolAvailabilityMenu, buttonGroup, ToolAvailabilityLevel.READING);
        addMenuItem(toolAvailabilityMenu, buttonGroup, ToolAvailabilityLevel.DISABLED);
        menuPopup.add(toolAvailabilityMenu);
    }

    void refreshSelection() {
        ToolAvailabilityLevel effectiveToolAvailability = effectiveToolAvailabilitySupplier.get();
        for (ToolAvailabilityLevel toolAvailability : ToolAvailabilityLevel.values()) {
            JRadioButtonMenuItem menuItem = toolAvailabilityMenuItems.get(toolAvailability);
            if (menuItem != null) {
                menuItem.setSelected(toolAvailability == effectiveToolAvailability);
            }
        }
    }

    private void addMenuItem(JMenu menu, ButtonGroup buttonGroup, ToolAvailabilityLevel toolAvailability) {
        String labelKey = "OptionPanel.ToolAvailabilityLevel." + toolAvailability.name();
        JRadioButtonMenuItem menuItem = new JRadioButtonMenuItem();
        buttonGroup.add(menuItem);
        LabelAndMnemonicSetter.setLabelAndMnemonic(menuItem, TextUtils.getRawText(labelKey));
        TranslatedElement.TEXT.setKey(menuItem, labelKey);
        TranslatedElementFactory.createTooltip(menuItem, labelKey + ".tooltip");
        menuItem.addActionListener(event -> explicitUserSelectionHandler.accept(toolAvailability));
        toolAvailabilityMenuItems.put(toolAvailability, menuItem);
        menu.add(menuItem);
    }
}
