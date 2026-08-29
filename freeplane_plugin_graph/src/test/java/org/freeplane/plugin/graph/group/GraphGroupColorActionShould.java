package org.freeplane.plugin.graph.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.event.ActionEvent;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.ColorTracker;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.map.IMapSelection;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

public class GraphGroupColorActionShould {
    private ResourceController resources;
    private MockedStatic<ResourceController> resourceController;
    private MockedStatic<Controller> controllers;
    private MockedStatic<ColorTracker> colorTrackers;
    private MockedStatic<TextUtils> textUtils;

    @Before
    public void setUp() {
        resources = mock(ResourceController.class);
        resourceController = org.mockito.Mockito.mockStatic(ResourceController.class);
        resourceController.when(ResourceController::getResourceController).thenReturn(resources);
        textUtils = org.mockito.Mockito.mockStatic(TextUtils.class);
        textUtils.when(() -> TextUtils.getRawText(any(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        textUtils.when(() -> TextUtils.getRawText(any(String.class), any(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(1));
        textUtils.when(() -> TextUtils.getText(any(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        Controller controller = mock(Controller.class);
        IMapSelection selection = mock(IMapSelection.class);
        when(selection.getSelected()).thenReturn(mock(NodeModel.class));
        when(controller.getSelection()).thenReturn(selection);
        controllers = org.mockito.Mockito.mockStatic(Controller.class);
        controllers.when(Controller::getCurrentController).thenReturn(controller);
        colorTrackers = org.mockito.Mockito.mockStatic(ColorTracker.class);
    }

    @After
    public void tearDown() {
        colorTrackers.close();
        controllers.close();
        textUtils.close();
        resourceController.close();
    }

    @Test
    public void writesTheChosenColorToThePreference() {
        colorTrackers.when(() -> ColorTracker.showCommonJColorChooserDialog(any(NodeModel.class),
            anyString(), any(), any())).thenReturn(new Color(0x11, 0x22, 0x33));

        new GraphGroupColorAction().actionPerformed(new ActionEvent(this, 1, "color"));

        verify(resources).setProperty(GraphGroupColors.COLOR_PROPERTY_KEY, "#112233");
    }

    @Test
    public void writesNothingWhenTheChooserIsCancelled() {
        colorTrackers.when(() -> ColorTracker.showCommonJColorChooserDialog(any(NodeModel.class),
            anyString(), any(), any())).thenReturn(null);

        new GraphGroupColorAction().actionPerformed(new ActionEvent(this, 1, "color"));

        verify(resources, never()).setProperty(anyString(), anyString());
    }

    @Test
    public void seedsTheChooserWithTheCurrentColorAndTheCoralResetColor() {
        Color current = new Color(0x33, 0x44, 0x55);
        when(resources.getColorProperty(GraphGroupColors.COLOR_PROPERTY_KEY)).thenReturn(current);
        colorTrackers.when(() -> ColorTracker.showCommonJColorChooserDialog(any(NodeModel.class),
            anyString(), any(), any())).thenReturn(null);

        new GraphGroupColorAction().actionPerformed(new ActionEvent(this, 1, "color"));

        ArgumentCaptor<Color> initial = ArgumentCaptor.forClass(Color.class);
        ArgumentCaptor<Color> reset = ArgumentCaptor.forClass(Color.class);
        colorTrackers.verify(() -> ColorTracker.showCommonJColorChooserDialog(any(NodeModel.class),
            eq("choose_graph_group_color"), initial.capture(), reset.capture()));
        assertThat(initial.getValue()).isEqualTo(current);
        assertThat(reset.getValue()).isEqualTo(GraphGroupColors.DEFAULT_COLOR);
    }
}
