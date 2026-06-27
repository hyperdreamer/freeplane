package org.freeplane.plugin.ai.prompt.ui;

import java.util.Arrays;
import javax.swing.JDialog;
import org.freeplane.api.ai.AiThinkingEffort;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.resources.WindowConfigurationStorage;
import org.freeplane.plugin.ai.model.AIModelConfiguration;
import org.freeplane.plugin.ai.model.AIModelSelection;
import org.freeplane.plugin.ai.prompt.AiPrompt;
import org.freeplane.plugin.ai.prompt.AiPromptStore;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AiPromptManagerDialogTest {
    private static final String DEFAULT_NEW_PROMPT_NAME = "New Prompt";

    @Test
    public void windowConfigurationProperty_matchesPlannedKey() {
        assertThat(AiPromptManagerDialog.WINDOW_CONFIGURATION_PROPERTY)
            .isEqualTo("ai_prompt_manager_dialog_window_configuration")
            .isNotEqualTo("ai_assistant_profile_manager_dialog_window_configuration");
    }

    @Test
    public void windowGeometryPersistence_restoresSavedBoundsWhenPropertyExists() {
        ResourceController resourceController = mock(ResourceController.class);
        WindowConfigurationStorage storage = mock(WindowConfigurationStorage.class);
        JDialog dialog = mock(JDialog.class);
        Runnable defaultPlacement = mock(Runnable.class);
        when(resourceController.getProperty(AiPromptManagerDialog.WINDOW_CONFIGURATION_PROPERTY))
            .thenReturn("saved");
        AiPromptManagerDialog.WindowGeometryPersistence persistence =
            new AiPromptManagerDialog.WindowGeometryPersistence(
                AiPromptManagerDialog.WINDOW_CONFIGURATION_PROPERTY,
                resourceController,
                storage);

        persistence.restoreOrApplyDefault(dialog, defaultPlacement);

        verify(storage).restoreDialogPositions(dialog);
        verify(defaultPlacement, never()).run();
    }

    @Test
    public void windowGeometryPersistence_usesFallbackWhenNoPropertyExists() {
        ResourceController resourceController = mock(ResourceController.class);
        WindowConfigurationStorage storage = mock(WindowConfigurationStorage.class);
        JDialog dialog = mock(JDialog.class);
        Runnable defaultPlacement = mock(Runnable.class);
        when(resourceController.getProperty(AiPromptManagerDialog.WINDOW_CONFIGURATION_PROPERTY))
            .thenReturn(null);
        AiPromptManagerDialog.WindowGeometryPersistence persistence =
            new AiPromptManagerDialog.WindowGeometryPersistence(
                AiPromptManagerDialog.WINDOW_CONFIGURATION_PROPERTY,
                resourceController,
                storage);

        persistence.restoreOrApplyDefault(dialog, defaultPlacement);

        verify(defaultPlacement).run();
        verify(storage, never()).restoreDialogPositions(dialog);
    }

    @Test
    public void windowGeometryPersistence_storesBounds() {
        WindowConfigurationStorage storage = mock(WindowConfigurationStorage.class);
        JDialog dialog = mock(JDialog.class);
        AiPromptManagerDialog.WindowGeometryPersistence persistence =
            new AiPromptManagerDialog.WindowGeometryPersistence(
                AiPromptManagerDialog.WINDOW_CONFIGURATION_PROPERTY,
                mock(ResourceController.class),
                storage);

        persistence.store(dialog);

        verify(storage).storeDialogPositions(dialog);
    }

    @Test
    public void editorState_marksShowInChatChangesAsDirty() {
        AiPromptManagerDialog.EditorState state = new AiPromptManagerDialog.EditorState();
        state.loadSavedPrompts(Arrays.asList(new AiPrompt("Rewrite", "Prompt", false)));
        state.selectSavedPrompt(0);

        state.updateDraft("Rewrite", "Prompt", true, "", "");

        assertThat(state.isDirty()).isTrue();
    }

    @Test
    public void editorState_marksModelSelectionChangesAsDirty() {
        AiPromptManagerDialog.EditorState state = new AiPromptManagerDialog.EditorState();
        state.loadSavedPrompts(Arrays.asList(new AiPrompt("Rewrite", "Prompt", false, "", "")));
        state.selectSavedPrompt(0);

        state.updateDraft("Rewrite", "Prompt", false, "openrouter|openai/gpt-4.1-mini", "");

        assertThat(state.isDirty()).isTrue();
    }

    @Test
    public void editorState_marksThinkingEffortAndTemperatureChangesAsDirty() {
        AiPromptManagerDialog.EditorState state = new AiPromptManagerDialog.EditorState();
        state.loadSavedPrompts(Arrays.asList(new AiPrompt("Rewrite", "Prompt", false, "", "")));
        state.selectSavedPrompt(0);

        state.updateDraft("Rewrite", "Prompt", false, "", AiThinkingEffort.LOW, Double.valueOf(0.2), "");

        assertThat(state.isDirty()).isTrue();
    }

    @Test
    public void editorState_marksToolSelectionChangesAsDirty() {
        AiPromptManagerDialog.EditorState state = new AiPromptManagerDialog.EditorState();
        state.loadSavedPrompts(Arrays.asList(new AiPrompt("Rewrite", "Prompt", false, "", "")));
        state.selectSavedPrompt(0);

        state.updateDraft("Rewrite", "Prompt", false, "", "reading");

        assertThat(state.isDirty()).isTrue();
    }

    @Test
    public void editorState_save_overwritesSelectedSavedPrompt() {
        AiPromptManagerDialog.EditorState state = new AiPromptManagerDialog.EditorState();
        state.loadSavedPrompts(Arrays.asList(new AiPrompt("Rewrite", "Prompt", false, "", "")));
        state.selectSavedPrompt(0);
        state.updateDraft("Rewrite better", "Other prompt", true, "openrouter|openai/gpt-4.1-mini", "disabled");

        state.save(DEFAULT_NEW_PROMPT_NAME);

        assertThat(state.getSavedPrompts())
            .extracting(
                AiPrompt::getName,
                AiPrompt::getPrompt,
                AiPrompt::isShowInChat,
                AiPrompt::getModelSelectionValue,
                AiPrompt::getToolAvailabilitySelectionValue)
            .containsExactly(tuple(
                "Rewrite better",
                "Other prompt",
                true,
                "openrouter|openai/gpt-4.1-mini",
                "disabled"));
        assertThat(state.getSelectedSavedPromptIndex()).isEqualTo(0);
        assertThat(state.isDirty()).isFalse();
    }

    @Test
    public void editorState_save_preservesModelConfigurationFields() {
        AiPromptManagerDialog.EditorState state = new AiPromptManagerDialog.EditorState();
        state.loadSavedPrompts(Arrays.asList(new AiPrompt("Rewrite", "Prompt", false, "", "")));
        state.selectSavedPrompt(0);

        state.updateDraft("Rewrite", "Prompt", false,
            "openrouter|openai/gpt-4.1-mini",
            AiThinkingEffort.HIGH,
            Double.valueOf(0.4),
            "");
        state.save(DEFAULT_NEW_PROMPT_NAME);

        assertThat(state.getSavedPrompts().get(0).getModelConfiguration())
            .isEqualTo(AIModelConfiguration.of(
                AIModelSelection.fromSelectionValue("openrouter|openai/gpt-4.1-mini"),
                AiThinkingEffort.HIGH,
                Double.valueOf(0.4)));
    }

    @Test
    public void editorState_save_createsPromptForNewDraft() {
        AiPromptManagerDialog.EditorState state = new AiPromptManagerDialog.EditorState();
        state.loadSavedPrompts(Arrays.asList(new AiPrompt("Rewrite", "Prompt", false)));
        state.beginNewDraft();
        state.updateDraft("", "Summarize subtree", false, "", "reading");

        state.save(DEFAULT_NEW_PROMPT_NAME);

        assertThat(state.getSavedPrompts())
            .extracting(AiPrompt::getName, AiPrompt::getModelSelectionValue, AiPrompt::getToolAvailabilitySelectionValue)
            .containsExactly(tuple("Rewrite", "", ""), tuple("New Prompt", "", "reading"));
        assertThat(state.getSelectedSavedPromptIndex()).isEqualTo(1);
    }

    @Test
    public void editorState_saveAsNew_keepsOriginalPromptAndAddsVariant() {
        AiPromptManagerDialog.EditorState state = new AiPromptManagerDialog.EditorState();
        state.loadSavedPrompts(Arrays.asList(new AiPrompt(
            "Rewrite", "Prompt", false, "gemini|gemini-2.5-flash", "editing")));
        state.selectSavedPrompt(0);
        state.updateDraft("Rewrite", "Other prompt", true, "openrouter|openai/gpt-4.1-mini", "disabled");

        state.saveAsNew(DEFAULT_NEW_PROMPT_NAME);

        assertThat(state.getSavedPrompts())
            .extracting(
                AiPrompt::getName,
                AiPrompt::getPrompt,
                AiPrompt::isShowInChat,
                AiPrompt::getModelSelectionValue,
                AiPrompt::getToolAvailabilitySelectionValue)
            .containsExactly(
                tuple("Rewrite", "Prompt", false, "gemini|gemini-2.5-flash", "editing"),
                tuple("Rewrite 1", "Other prompt", true, "openrouter|openai/gpt-4.1-mini", "disabled"));
        assertThat(state.getSelectedSavedPromptIndex()).isEqualTo(1);
        assertThat(state.isDirty()).isFalse();
    }

    @Test
    public void editorState_saveAsNew_isDisabledForUnsavedDraft() {
        AiPromptManagerDialog.EditorState state = new AiPromptManagerDialog.EditorState();
        state.loadSavedPrompts(Arrays.asList(new AiPrompt("Rewrite", "Prompt", false)));
        state.beginNewDraft();

        assertThat(state.canSaveAsNew()).isFalse();
        assertThatThrownBy(() -> state.saveAsNew(DEFAULT_NEW_PROMPT_NAME))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void editorState_deleteSelectedPrompt_selectsNextSavedPrompt() {
        AiPromptManagerDialog.EditorState state = new AiPromptManagerDialog.EditorState();
        state.loadSavedPrompts(Arrays.asList(
            new AiPrompt("Rewrite", "Prompt", false),
            new AiPrompt("Summarize", "Other", true, "openrouter|openai/gpt-4.1-mini", "reading")));
        state.selectSavedPrompt(0);

        state.deleteSelectedPrompt();

        assertThat(state.getSavedPrompts())
            .extracting(AiPrompt::getName)
            .containsExactly("Summarize");
        assertThat(state.getSelectedSavedPromptIndex()).isEqualTo(0);
        assertThat(state.getCurrentDraft().getName()).isEqualTo("Summarize");
        assertThat(state.getCurrentDraft().getModelSelectionValue()).isEqualTo("openrouter|openai/gpt-4.1-mini");
        assertThat(state.getCurrentDraft().getToolAvailabilitySelectionValue()).isEqualTo("reading");
    }

    @Test
    public void editorState_restoreKeepsNewDraftWithEmptySelectedPromptName() {
        AiPromptManagerDialog.EditorState state = new AiPromptManagerDialog.EditorState();

        state.loadState(
            Arrays.asList(new AiPrompt("Rewrite", "Prompt", false)),
            new AiPromptStore.PersistedDialogState("", new AiPrompt(
                "", "Draft", true, "openrouter|openai/gpt-4.1-mini", "disabled")),
            DEFAULT_NEW_PROMPT_NAME);

        assertThat(state.getSelectedSavedPromptIndex()).isEqualTo(-1);
        assertThat(state.getCurrentDraft().getPrompt()).isEqualTo("Draft");
        assertThat(state.getCurrentDraft().getModelSelectionValue()).isEqualTo("openrouter|openai/gpt-4.1-mini");
        assertThat(state.getCurrentDraft().getToolAvailabilitySelectionValue()).isEqualTo("disabled");
        assertThat(state.isDirty()).isTrue();
    }

    @Test
    public void editorState_restoreClearsMissingSelectedPromptNameAndKeepsDraft() {
        AiPromptManagerDialog.EditorState state = new AiPromptManagerDialog.EditorState();

        state.loadState(
            Arrays.asList(new AiPrompt("Rewrite", "Prompt", false)),
            new AiPromptStore.PersistedDialogState("Missing", new AiPrompt(
                "Unsaved", "Draft", true, "openrouter|openai/gpt-4.1-mini", "reading")),
            DEFAULT_NEW_PROMPT_NAME);

        assertThat(state.getSelectedSavedPromptIndex()).isEqualTo(-1);
        assertThat(state.getCurrentDraft().getName()).isEqualTo("Unsaved");
        assertThat(state.getCurrentDraft().getPrompt()).isEqualTo("Draft");
        assertThat(state.getCurrentDraft().getModelSelectionValue()).isEqualTo("openrouter|openai/gpt-4.1-mini");
        assertThat(state.getCurrentDraft().getToolAvailabilitySelectionValue()).isEqualTo("reading");
        assertThat(state.isDirty()).isTrue();
    }

    @Test
    public void editorState_persistsEmptySelectedPromptNameForNewDraft() {
        AiPromptManagerDialog.EditorState state = new AiPromptManagerDialog.EditorState();
        state.loadSavedPrompts(Arrays.asList(new AiPrompt("Rewrite", "Prompt", false)));
        state.beginNewDraft();
        state.updateDraft("", "Draft", false, "openrouter|openai/gpt-4.1-mini", "disabled");

        AiPromptStore.PersistedDialogState persistedDialogState = state.createPersistedDialogState();

        assertThat(persistedDialogState.getSelectedPromptName()).isEmpty();
        assertThat(persistedDialogState.getDraft().getPrompt()).isEqualTo("Draft");
        assertThat(persistedDialogState.getDraft().getModelSelectionValue())
            .isEqualTo("openrouter|openai/gpt-4.1-mini");
        assertThat(persistedDialogState.getDraft().getToolAvailabilitySelectionValue())
            .isEqualTo("disabled");
    }

    private static org.assertj.core.groups.Tuple tuple(Object... values) {
        return org.assertj.core.groups.Tuple.tuple(values);
    }
}
