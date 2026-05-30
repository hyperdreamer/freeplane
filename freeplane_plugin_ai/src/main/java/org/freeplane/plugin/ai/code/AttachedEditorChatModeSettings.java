package org.freeplane.plugin.ai.code;

import java.util.Objects;
import org.freeplane.core.resources.ResourceController;

public class AttachedEditorChatModeSettings {
    public static final String ATTACHED_EDITOR_CHAT_MODE_PROPERTY = "ai_attached_editor_chat_mode";

    private final ResourceController resourceController;

    public AttachedEditorChatModeSettings() {
        this(ResourceController.getResourceController());
    }

    AttachedEditorChatModeSettings(ResourceController resourceController) {
        this.resourceController = Objects.requireNonNull(resourceController, "resourceController");
    }

    public AttachedEditorChatMode get() {
        return AttachedEditorChatMode.fromPreferenceValue(
            resourceController.getProperty(ATTACHED_EDITOR_CHAT_MODE_PROPERTY));
    }
}
