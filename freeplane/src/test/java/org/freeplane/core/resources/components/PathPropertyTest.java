package org.freeplane.core.resources.components;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.freeplane.core.resources.ResourceBundles;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.features.mode.Controller;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class PathPropertyTest {
    private Controller previousController;

    @Before
    public void setUp() {
        previousController = Controller.getCurrentController();
        ResourceController resources = mock(ResourceController.class);
        ResourceBundles resourceBundles = mock(ResourceBundles.class);
        when(resources.getResources()).thenReturn(resourceBundles);
        when(resourceBundles.getResourceString(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(resources.getFreeplaneUserDirectory()).thenReturn("/tmp/freeplane-user");
        Controller.setCurrentController(new Controller(resources));
    }

    @After
    public void tearDown() {
        Controller.setCurrentController(previousController);
    }

    @Test
    public void placeholderPathEqualsExpandedPath() {
        PathProperty property = new PathProperty("path", true, null);
        String userHome = System.getProperty("user.home");

        assertThat(property.valuesEqual("{user.home}/maps", userHome + "/maps")).isTrue();
        assertThat(property.valuesEqual("{freeplaneuserdir}/maps", "/tmp/freeplane-user/maps")).isTrue();
        assertThat(property.valuesEqual("{freeplaneuserdir}/maps", "/tmp/other/maps")).isFalse();
    }
}
