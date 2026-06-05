package org.freeplane.plugin.ai.prompt;

import java.awt.Component;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.swing.SwingUtilities;
import org.freeplane.core.util.TextUtils;
import org.freeplane.plugin.ai.chat.ui.AIChatPanel;
import org.freeplane.plugin.ai.prompt.ui.AiPromptManagerDialog;

public class AiPromptActionRegistry {
    static final String PROMPT_ACTION_PREFIX = "RunAiPromptAction.";

    private final AiPromptStore store;
    private final AIChatPanel aiChatPanel;
    private final Runnable menusRebuilder;
    private final Map<String, RunAiPromptAction> actionsByKey = new LinkedHashMap<String, RunAiPromptAction>();
    private final AiPromptManagerDialog.EditorState dialogState = new AiPromptManagerDialog.EditorState();
    private volatile Map<String, AiPrompt> savedPromptSnapshotByNormalizedName = Collections.emptyMap();
    private AiPromptStore.PersistedState lastPersistedState;

    AiPromptActionRegistry(AiPromptStore store, AIChatPanel aiChatPanel, Runnable menusRebuilder) {
        this.store = Objects.requireNonNull(store, "store");
        this.aiChatPanel = Objects.requireNonNull(aiChatPanel, "aiChatPanel");
        this.menusRebuilder = Objects.requireNonNull(menusRebuilder, "menusRebuilder");
        reloadState();
    }

    List<AiPrompt> getPrompts() {
        return dialogState.getSavedPrompts();
    }

    public AiPromptManagerDialog.EditorState getDialogState() {
        return dialogState;
    }

    List<RunAiPromptAction> promptActions() {
        List<RunAiPromptAction> actions = new ArrayList<RunAiPromptAction>();
        Set<String> validKeys = new LinkedHashSet<String>();
        for (AiPrompt prompt : dialogState.getSavedPrompts()) {
            String actionKey = actionKey(prompt.getName());
            validKeys.add(actionKey);
            RunAiPromptAction action = actionsByKey.get(actionKey);
            if (action == null) {
                action = new RunAiPromptAction(actionKey, prompt, aiChatPanel);
                actionsByKey.put(actionKey, action);
            }
            else {
                action.updatePrompt(prompt);
            }
            actions.add(action);
        }
        removeStaleActions(validKeys);
        return actions;
    }

    void openPromptManager() {
        AiPromptManagerDialog dialog = new AiPromptManagerDialog(findOwnerComponent(), this);
        dialog.openDialog();
    }

    public void persistStateAndRefreshMenus() {
        persistStateIfChanged();
        menusRebuilder.run();
    }

    public void persistStateIfChanged() {
        AiPromptStore.PersistedState currentState = createPersistedState();
        if (sameState(lastPersistedState, currentState)) {
            return;
        }
        store.saveState(currentState);
        lastPersistedState = copyState(currentState);
        savedPromptSnapshotByNormalizedName = createSavedPromptSnapshot(currentState.getSavedPrompts());
    }

    public void runPrompt(AiPrompt prompt) {
        runPrompt(prompt, null);
    }

    public void runPrompt(AiPrompt prompt, Component owner) {
        if (prompt != null) {
            aiChatPanel.runPrompt(prompt.copy(), owner);
        }
    }

    public AiPrompt findSavedPromptByName(String promptName) {
        AiPrompt prompt = savedPromptSnapshotByNormalizedName.get(normalizedPromptKey(promptName));
        return prompt == null ? null : prompt.copy();
    }

    static String actionKey(String promptName) {
        return PROMPT_ACTION_PREFIX + AiPromptNameValidator.normalizeName(promptName);
    }

    private void reloadState() {
        AiPromptStore.PersistedState persistedState = store.loadState();
        dialogState.loadState(
            persistedState.getSavedPrompts(),
            persistedState.getDialogState(),
            defaultNewPromptName());
        lastPersistedState = createPersistedState();
        savedPromptSnapshotByNormalizedName = createSavedPromptSnapshot(lastPersistedState.getSavedPrompts());
    }

    private AiPromptStore.PersistedState createPersistedState() {
        return new AiPromptStore.PersistedState(
            dialogState.getSavedPrompts(),
            dialogState.createPersistedDialogState());
    }

    private static AiPromptStore.PersistedState copyState(AiPromptStore.PersistedState state) {
        return state == null ? AiPromptStore.emptyState() : state.copy();
    }

    private static boolean sameState(AiPromptStore.PersistedState first, AiPromptStore.PersistedState second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null) {
            return false;
        }
        if (!samePrompts(first.getSavedPrompts(), second.getSavedPrompts())) {
            return false;
        }
        return sameDialogState(first.getDialogState(), second.getDialogState());
    }

    private static boolean sameDialogState(AiPromptStore.PersistedDialogState first,
                                           AiPromptStore.PersistedDialogState second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null) {
            return false;
        }
        return safe(first.getSelectedPromptName()).equals(safe(second.getSelectedPromptName()))
            && samePrompt(first.getDraft(), second.getDraft());
    }

    private static boolean samePrompts(List<AiPrompt> first, List<AiPrompt> second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null || first.size() != second.size()) {
            return false;
        }
        for (int index = 0; index < first.size(); index++) {
            if (!samePrompt(first.get(index), second.get(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean samePrompt(AiPrompt first, AiPrompt second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null) {
            return false;
        }
        return safe(first.getName()).equals(safe(second.getName()))
            && safe(first.getPrompt()).equals(safe(second.getPrompt()))
            && first.isShowInChat() == second.isShowInChat()
            && safe(first.getModelSelectionValue()).equals(safe(second.getModelSelectionValue()))
            && safe(first.getToolAvailabilitySelectionValue())
                .equals(safe(second.getToolAvailabilitySelectionValue()));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private Map<String, AiPrompt> createSavedPromptSnapshot(List<AiPrompt> savedPrompts) {
        Map<String, AiPrompt> snapshot = new LinkedHashMap<String, AiPrompt>();
        if (savedPrompts != null) {
            for (AiPrompt prompt : savedPrompts) {
                if (prompt == null) {
                    continue;
                }
                snapshot.put(normalizedPromptKey(prompt.getName()), prompt.copy());
            }
        }
        return Collections.unmodifiableMap(snapshot);
    }

    private String normalizedPromptKey(String promptName) {
        return AiPromptNameValidator.normalizeName(promptName).toLowerCase(Locale.ROOT);
    }

    private void removeStaleActions(Set<String> validKeys) {
        Iterator<Map.Entry<String, RunAiPromptAction>> iterator = actionsByKey.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, RunAiPromptAction> entry = iterator.next();
            if (!validKeys.contains(entry.getKey())) {
                iterator.remove();
            }
        }
    }

    private Component findOwnerComponent() {
        if (SwingUtilities.getWindowAncestor(aiChatPanel) != null) {
            return aiChatPanel;
        }
        return null;
    }

    private String defaultNewPromptName() {
        try {
            return TextUtils.getText("ai_prompt_new_name");
        }
        catch (RuntimeException error) {
            return "New Prompt";
        }
        catch (Error error) {
            return "New Prompt";
        }
    }
}
