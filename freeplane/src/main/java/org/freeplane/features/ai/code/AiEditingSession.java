package org.freeplane.features.ai.code;

import java.awt.event.ActionListener;
import java.util.Objects;
import java.util.function.Supplier;

import javax.swing.JToggleButton;

public final class AiEditingSession {
    private final AiChatAttachableEditor editor;
    private final String contentType;
    private final JToggleButton attachButton;
    private final Supplier<AiChatAttachmentService> attachmentServiceProvider;
    private final ActionListener toggleListener;
    private AiChatAttachment currentAttachment;
    private boolean closed;

    public AiEditingSession(AiChatAttachableEditor editor,
                            String contentType,
                            JToggleButton attachButton,
                            Supplier<AiChatAttachmentService> attachmentServiceProvider) {
        this.editor = Objects.requireNonNull(editor, "editor");
        this.contentType = Objects.requireNonNull(contentType, "contentType");
        this.attachButton = Objects.requireNonNull(attachButton, "attachButton");
        this.attachmentServiceProvider = Objects.requireNonNull(
            attachmentServiceProvider,
            "attachmentServiceProvider");
        this.toggleListener = event -> toggle();
        attachButton.addActionListener(toggleListener);
        updateToggleButton();
    }

    public boolean canStart() {
        if (closed) {
            return false;
        }
        AiChatAttachmentService attachmentService = attachmentService();
        return attachmentService != null && attachmentService.isAiConfigured();
    }

    public boolean start() {
        if (closed) {
            return false;
        }
        if (currentAttachment != null) {
            return true;
        }
        AiChatAttachmentService attachmentService = attachmentService();
        if (attachmentService == null || !attachmentService.isAiConfigured()) {
            updateToggleButton();
            return false;
        }
        setCurrentAttachment(attachmentService.attachEditor(editor, contentType));
        return currentAttachment != null;
    }

    public boolean isActive() {
        return currentAttachment != null;
    }

    public void toggle() {
        if (isActive()) {
            end();
        } else {
            start();
        }
    }

    public void end() {
        if (!closed) {
            endCurrentAttachment();
        }
    }

    public void rememberFailure(ReadCodeResponse state) {
        if (currentAttachment != null && state != null) {
            currentAttachment.recordCodeState(state);
        }
    }

    public void forgetFailure() {
        if (currentAttachment != null) {
            currentAttachment.clearCodeState();
        }
    }

    public boolean askForRepair(ReadCodeResponse state, String repairInstruction) {
        if (state == null || repairInstruction == null) {
            return false;
        }
        if (currentAttachment == null && !start()) {
            return false;
        }
        if (currentAttachment == null) {
            return false;
        }
        currentAttachment.recordCodeState(state);
        currentAttachment.requestRepair(new AiChatRepairRequest(repairInstruction, state));
        return true;
    }

    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        attachButton.removeActionListener(toggleListener);
        endCurrentAttachment();
        updateToggleButton();
    }

    private AiChatAttachmentService attachmentService() {
        return attachmentServiceProvider.get();
    }

    private void setCurrentAttachment(AiChatAttachment attachment) {
        currentAttachment = attachment;
        if (attachment != null) {
            attachment.setDetachHandler(() -> handleDetached(attachment));
        }
        updateToggleButton();
    }

    private void handleDetached(AiChatAttachment attachment) {
        if (currentAttachment == attachment) {
            currentAttachment = null;
            updateToggleButton();
        }
    }

    private void endCurrentAttachment() {
        AiChatAttachment attachment = currentAttachment;
        currentAttachment = null;
        updateToggleButton();
        if (attachment != null) {
            attachment.detach();
        }
    }

    private void updateToggleButton() {
        attachButton.setSelected(currentAttachment != null);
        attachButton.setEnabled(!closed && (currentAttachment != null || canStart()));
    }
}
