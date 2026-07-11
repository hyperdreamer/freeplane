package org.freeplane.core.resources.components;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

import org.freeplane.core.resources.ResourceBundles;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.features.mode.Controller;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class PreferencePropertyResetControlTest {
    private Controller previousController;

    @Before
    public void setUp() {
        previousController = Controller.getCurrentController();
        ResourceController resources = mock(ResourceController.class);
        ResourceBundles resourceBundles = mock(ResourceBundles.class);
        when(resources.getResources()).thenReturn(resourceBundles);
        when(resourceBundles.getResourceString("reset_to_default")).thenReturn("Use default");
        when(resources.getResource("/fonts/icons.ttf"))
                .thenReturn(getClass().getResource("/fonts/icons.ttf"));
        when(resources.getFreeplaneUserDirectory()).thenReturn("/tmp/freeplane-user");
        Controller.setCurrentController(new Controller(resources));
    }

    @After
    public void tearDown() {
        Controller.setCurrentController(previousController);
    }

    @Test
    public void resetButtonReflectsDefaultOverrideAndEditorEnabledState() {
        StringProperty property = configuredStringProperty("other", "default");
        JButton resetButton = resetButton(property.decorateValueComponent(property.getValueComponent()));

        assertThat(resetButton.isEnabled()).isTrue();

        property.setValue("default");
        assertThat(resetButton.isEnabled()).isFalse();

        property.initializeResetState(true);
        assertThat(resetButton.isEnabled()).isTrue();

        property.setValue("other");
        property.setEnabled(false);
        assertThat(resetButton.isEnabled()).isFalse();

        property.setEnabled(true);
        assertThat(resetButton.isEnabled()).isTrue();
    }

    @Test
    public void resetButtonClickRestoresDefaultAndMarksRemoval() {
        StringProperty property = configuredStringProperty("other", "default");
        JButton resetButton = resetButton(property.decorateValueComponent(property.getValueComponent()));

        resetButton.doClick();

        assertThat(property.getValue()).isEqualTo("default");
        assertThat(property.isUserPropertyRemovalPending()).isTrue();
        assertThat(resetButton.isEnabled()).isFalse();
    }

    @Test
    public void laterEditorChangeCancelsRemoval() {
        StringProperty property = configuredStringProperty("other", "default");
        JButton resetButton = resetButton(property.decorateValueComponent(property.getValueComponent()));
        resetButton.doClick();

        ((JTextField) property.getValueComponent()).setText("later value");

        assertThat(property.isUserPropertyRemovalPending()).isFalse();
        assertThat(resetButton.isEnabled()).isTrue();
    }

    @Test
    public void resetWithoutRegisteredDefaultDisplaysUnsetValueAndMarksRemoval() {
        StringProperty property = configuredStringProperty("", null, false);
        JButton resetButton = resetButton(property.decorateValueComponent(property.getValueComponent()));
        assertThat(resetButton.isEnabled()).isFalse();

        ((JTextField) property.getValueComponent()).setText("edited value");
        assertThat(resetButton.isEnabled()).isTrue();
        resetButton.doClick();

        assertThat(property.getValue()).isEmpty();
        assertThat(property.isUserPropertyRemovalPending()).isTrue();
        assertThat(resetButton.isEnabled()).isFalse();
    }

    @Test
    public void pathResetKeepsPlaceholderDefault() {
        PathProperty property = new PathProperty("path", true, null);
        property.setValue("/other/path");
        property.configureReset("{freeplaneuserdir}/default");
        property.initializeResetState(false);
        JButton resetButton = resetButton(property.decorateValueComponent(new JButton()));

        resetButton.doClick();

        assertThat(property.getValue()).isEqualTo("{freeplaneuserdir}/default");
        assertThat(property.isUserPropertyRemovalPending()).isTrue();
    }

    @Test
    public void radioButtonRowsReserveResetButtonWidth() {
        RadioButtonProperty property = new RadioButtonProperty("property",
                Arrays.asList("first", "second", "third"),
                Arrays.asList("First", "Second", "Third"));
        property.setValue("second");
        property.configureReset("first");
        property.initializeResetState(false);
        PropertyPane pane = new PropertyPane();

        pane.addProperty(property);

        List<JRadioButton> radioButtons = descendantsOfType(pane, JRadioButton.class);
        assertThat(radioButtons).hasSize(3);
        Container firstRow = radioButtons.get(0).getParent();
        int resetButtonWidth = firstRow.getComponent(0).getPreferredSize().width;
        for (JRadioButton radioButton : radioButtons) {
            Container row = radioButton.getParent();
            assertThat(row.getComponent(1)).isSameAs(radioButton);
            assertThat(row.getComponent(0).getPreferredSize().width).isEqualTo(resetButtonWidth);
        }
    }

    private <T extends Component> List<T> descendantsOfType(Container parent, Class<T> type) {
        List<T> matches = new ArrayList<T>();
        for (Component child : parent.getComponents()) {
            if (type.isInstance(child)) {
                matches.add(type.cast(child));
            }
            if (child instanceof Container) {
                matches.addAll(descendantsOfType((Container) child, type));
            }
        }
        return matches;
    }

    private StringProperty configuredStringProperty(String value, String defaultValue) {
        return configuredStringProperty(value, defaultValue, false);
    }

    private StringProperty configuredStringProperty(String value, String defaultValue,
            boolean userPropertyWasSet) {
        StringProperty property = new StringProperty("property");
        property.setValue(value);
        property.configureReset(defaultValue);
        property.initializeResetState(userPropertyWasSet);
        return property;
    }

    private JButton resetButton(JComponent decoratedComponent) {
        Container container = (Container) decoratedComponent;
        return (JButton) container.getComponent(0);
    }
}
