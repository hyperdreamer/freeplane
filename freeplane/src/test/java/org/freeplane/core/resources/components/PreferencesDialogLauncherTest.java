package org.freeplane.core.resources.components;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Properties;

import org.freeplane.core.resources.ResourceController;
import org.junit.Test;

public class PreferencesDialogLauncherTest {
    @Test
    public void appliesPendingResetByRemovingUserProperty() {
        ResourceController resources = mock(ResourceController.class);
        Properties properties = properties("property", "default");
        when(resources.isPropertySetByUser("property")).thenReturn(true);

        boolean changed = PreferencesDialogLauncher.applyPreferenceChanges(
                resources, properties, Collections.singleton("property"));

        assertThat(changed).isTrue();
        verify(resources).removeUserProperty("property");
        verify(resources, never()).setProperty("property", "default");
    }

    @Test
    public void appliesLaterValueInsteadOfRemovingUserProperty() {
        ResourceController resources = mock(ResourceController.class);
        Properties properties = properties("property", "later value");
        when(resources.getProperty("property")).thenReturn("default");

        boolean changed = PreferencesDialogLauncher.applyPreferenceChanges(
                resources, properties, Collections.emptySet());

        assertThat(changed).isTrue();
        verify(resources).setProperty("property", "later value");
        verify(resources, never()).removeUserProperty("property");
    }

    @Test
    public void removedOverrideCountsAsPreferenceChange() {
        ResourceController resources = mock(ResourceController.class);
        Properties properties = properties("property", "default");
        when(resources.isPropertySetByUser("property")).thenReturn(true);

        assertThat(PreferencesDialogLauncher.applyPreferenceChanges(
                resources, properties, Collections.singleton("property"))).isTrue();
    }

    private Properties properties(String key, String value) {
        Properties properties = new Properties();
        properties.setProperty(key, value);
        return properties;
    }
}
