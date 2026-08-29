package org.freeplane.plugin.script;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;

import org.freeplane.core.resources.ResourceBundles;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.features.mode.Controller;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class ScriptEditorButtonTest {
    private MockedStatic<Controller> controller;

    @Before
    public void setUp() {
        controller = mockStatic(Controller.class);
        Controller currentController = mock(Controller.class);
        ResourceController resourceController = mock(ResourceController.class);
        ResourceBundles resourceBundles = mock(ResourceBundles.class);
        when(currentController.getResourceController()).thenReturn(resourceController);
        when(resourceController.getResources()).thenReturn(resourceBundles);
        when(resourceBundles.getResourceString("EditScript")).thenReturn("Edit script");
        controller.when(Controller::getCurrentController).thenReturn(currentController);
    }

    @After
    public void tearDown() {
        controller.close();
    }

    @Test
    public void setItemUpdatesDisplayedTextAndTooltip() {
        ScriptEditorButton button = new ScriptEditorButton(new Object(), selectAll -> { });
        String script = "  first line\nsecond line";

        button.setItem(script);

        JButton component = (JButton) button.getEditorComponent();
        assertThat(button.getItem()).isEqualTo(script);
        assertThat(component.getText()).isEqualTo("first line second line");
        assertThat(component.getToolTipText()).isNotNull();
    }

    @Test
    public void setItemAndNotifySendsEventFromSuppliedSource() {
        Object source = new Object();
        ScriptEditorButton button = new ScriptEditorButton(source, selectAll -> { });
        List<ActionEvent> events = new ArrayList<ActionEvent>();
        button.addActionListener(events::add);

        button.setItemAndNotify("updated");

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getSource()).isSameAs(source);
        assertThat(button.getItem()).isEqualTo("updated");
    }

    @Test
    public void buttonClickAndSelectAllRequestDifferentEditorModes() {
        List<Boolean> selectAllValues = new ArrayList<Boolean>();
        ScriptEditorButton button = new ScriptEditorButton(new Object(), selectAllValues::add);

        ((JButton) button.getEditorComponent()).doClick();
        button.selectAll();

        assertThat(selectAllValues).containsExactly(false, true);
    }
}
