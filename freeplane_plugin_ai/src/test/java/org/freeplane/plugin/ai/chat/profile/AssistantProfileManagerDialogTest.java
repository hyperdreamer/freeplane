package org.freeplane.plugin.ai.chat.profile;

import javax.swing.JDialog;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.resources.WindowConfigurationStorage;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AssistantProfileManagerDialogTest {

    @Test
    public void windowConfigurationProperty_matchesPlannedKey() {
        assertThat(AssistantProfileManagerDialog.WINDOW_CONFIGURATION_PROPERTY)
            .isEqualTo("ai_assistant_profile_manager_dialog_window_configuration")
            .isNotEqualTo("ai_prompt_manager_dialog_window_configuration");
    }

    @Test
    public void windowGeometryPersistence_restoresSavedBoundsWhenPropertyExists() {
        ResourceController resourceController = mock(ResourceController.class);
        WindowConfigurationStorage storage = mock(WindowConfigurationStorage.class);
        JDialog dialog = mock(JDialog.class);
        Runnable defaultPlacement = mock(Runnable.class);
        when(resourceController.getProperty(AssistantProfileManagerDialog.WINDOW_CONFIGURATION_PROPERTY))
            .thenReturn("saved");
        AssistantProfileManagerDialog.WindowGeometryPersistence persistence =
            new AssistantProfileManagerDialog.WindowGeometryPersistence(
                AssistantProfileManagerDialog.WINDOW_CONFIGURATION_PROPERTY,
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
        when(resourceController.getProperty(AssistantProfileManagerDialog.WINDOW_CONFIGURATION_PROPERTY))
            .thenReturn(null);
        AssistantProfileManagerDialog.WindowGeometryPersistence persistence =
            new AssistantProfileManagerDialog.WindowGeometryPersistence(
                AssistantProfileManagerDialog.WINDOW_CONFIGURATION_PROPERTY,
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
        AssistantProfileManagerDialog.WindowGeometryPersistence persistence =
            new AssistantProfileManagerDialog.WindowGeometryPersistence(
                AssistantProfileManagerDialog.WINDOW_CONFIGURATION_PROPERTY,
                mock(ResourceController.class),
                storage);

        persistence.store(dialog);

        verify(storage).storeDialogPositions(dialog);
    }
}
