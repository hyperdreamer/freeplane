package org.freeplane.plugin.graph.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.awt.Color;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.main.application.ApplicationResourceController;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

public class GraphGroupColorsShould {
    private MockedStatic<ResourceController> resourceController;

    @Before
    public void setUp() {
        resourceController = mockStatic(ResourceController.class);
    }

    @After
    public void tearDown() {
        resourceController.close();
    }

    @Test
    public void returnsTheDefaultCoralWhenThePropertyIsMissing() {
        ApplicationResourceController resources = mock(ApplicationResourceController.class);
        resourceController.when(ResourceController::getResourceController).thenReturn(resources);

        assertThat(GraphGroupColors.currentColor()).isEqualTo(GraphGroupColors.DEFAULT_COLOR);
    }

    @Test
    public void returnsTheDefaultCoralWhenThePropertyIsInvalid() {
        ApplicationResourceController resources = mock(ApplicationResourceController.class,
            CALLS_REAL_METHODS);
        doReturn("").when(resources).getProperty(anyString());
        resourceController.when(ResourceController::getResourceController).thenReturn(resources);

        assertThat(GraphGroupColors.currentColor()).isEqualTo(GraphGroupColors.DEFAULT_COLOR);
    }

    @Test
    public void returnsTheConfiguredColorWhenThePropertyIsSet() {
        ApplicationResourceController resources = mock(ApplicationResourceController.class,
            CALLS_REAL_METHODS);
        doReturn("#112233").when(resources).getProperty(GraphGroupColors.COLOR_PROPERTY_KEY);
        resourceController.when(ResourceController::getResourceController).thenReturn(resources);

        Color color = GraphGroupColors.currentColor();

        assertThat(color.getRed()).isEqualTo(0x11);
        assertThat(color.getGreen()).isEqualTo(0x22);
        assertThat(color.getBlue()).isEqualTo(0x33);
    }
}
