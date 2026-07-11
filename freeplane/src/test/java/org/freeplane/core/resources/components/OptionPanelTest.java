package org.freeplane.core.resources.components;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import javax.swing.JButton;
import javax.swing.tree.DefaultMutableTreeNode;

import org.freeplane.core.resources.ResourceBundles;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.features.mode.Controller;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class OptionPanelTest {
    private Controller previousController;
    private ResourceController resources;

    @Before
    public void setUp() {
        previousController = Controller.getCurrentController();
        resources = mock(ResourceController.class);
        ResourceBundles resourceBundles = mock(ResourceBundles.class);
        when(resources.getResources()).thenReturn(resourceBundles);
        when(resourceBundles.getResourceString("reset_to_default")).thenReturn("Use default");
        when(resources.getResource("/fonts/icons.ttf"))
                .thenReturn(getClass().getResource("/fonts/icons.ttf"));
        Controller.setCurrentController(new Controller(resources));
    }

    @After
    public void tearDown() {
        Controller.setCurrentController(previousController);
    }

    @Test
    public void configuresResetForEveryPropertyBean() throws Exception {
        StringProperty propertyWithDefault = new StringProperty("with.default");
        propertyWithDefault.setValue("other");
        StringProperty propertyWithoutDefault = new StringProperty("without.default");
        SeparatorProperty nonPropertyControl = new SeparatorProperty("separator", "separator");
        when(resources.getDefaultProperty("with.default")).thenReturn("default");
        when(resources.getDefaultProperty("separator")).thenReturn("ignored");
        DefaultMutableTreeNode controlsTree = new DefaultMutableTreeNode();
        controlsTree.add(controlNode(propertyWithDefault));
        controlsTree.add(controlNode(propertyWithoutDefault));
        controlsTree.add(controlNode(nonPropertyControl));

        when(resources.getProperty("with.default")).thenReturn("other");
        OptionPanel optionPanel = new OptionPanel(null, (properties, removals) -> { });
        initializeControls(optionPanel, controlsTree);
        optionPanel.setProperties();

        PreferencePropertyResetControl resetControl = resetControl(propertyWithDefault);
        assertThat(resetControl).isNotNull();
        assertThat(resetControl(propertyWithoutDefault)).isNotNull();
        assertThat(nonPropertyControl).isNotInstanceOf(PropertyBean.class);
        verify(resources, never()).getDefaultProperty("separator");

        resetControl.decorate(propertyWithDefault.getValueComponent());
        assertThat(resetButton(resetControl).isEnabled()).isTrue();
        when(resources.getProperty("with.default")).thenReturn("default");
        optionPanel.setProperties();
        assertThat(resetButton(resetControl).isEnabled()).isFalse();
    }

    @Test
    public void resetAllRestoresDefaultsAndUnsetsOtherPropertiesWithoutWriting() throws Exception {
        StringProperty propertyAlreadyAtDefault = new StringProperty("already.default");
        propertyAlreadyAtDefault.setValue("first default");
        StringProperty propertyWithOtherValue = new StringProperty("other.value");
        propertyWithOtherValue.setValue("other");
        StringProperty propertyWithoutDefault = new StringProperty("without.default");
        propertyWithoutDefault.setValue("unchanged");
        when(resources.getDefaultProperty("already.default")).thenReturn("first default");
        when(resources.getDefaultProperty("other.value")).thenReturn("second default");
        when(resources.getProperty("already.default")).thenReturn("first default");
        when(resources.getProperty("other.value")).thenReturn("other");
        when(resources.getProperty("without.default")).thenReturn("unchanged");
        when(resources.isPropertySetByUser("already.default")).thenReturn(true);
        when(resources.isPropertySetByUser("other.value")).thenReturn(true);
        when(resources.isPropertySetByUser("without.default")).thenReturn(true);
        DefaultMutableTreeNode controlsTree = new DefaultMutableTreeNode();
        controlsTree.add(controlNode(propertyAlreadyAtDefault));
        controlsTree.add(controlNode(propertyWithOtherValue));
        controlsTree.add(controlNode(propertyWithoutDefault));
        OptionPanel.IOptionPanelFeedback feedback = mock(OptionPanel.IOptionPanelFeedback.class);
        OptionPanel optionPanel = new OptionPanel(null, feedback);
        initializeControls(optionPanel, controlsTree);
        optionPanel.setProperties();

        resetAllPropertiesToDefaults(optionPanel);

        assertThat(propertyAlreadyAtDefault.getValue()).isEqualTo("first default");
        assertThat(propertyAlreadyAtDefault.isUserPropertyRemovalPending()).isTrue();
        assertThat(propertyWithOtherValue.getValue()).isEqualTo("second default");
        assertThat(propertyWithOtherValue.isUserPropertyRemovalPending()).isTrue();
        assertThat(propertyWithoutDefault.getValue()).isEmpty();
        assertThat(propertyWithoutDefault.isUserPropertyRemovalPending()).isTrue();
        verify(feedback, never()).writeProperties(any(Properties.class), anySet());
    }

    @Test
    public void unchangedPropertiesAreExcludedFromPreferenceChanges() throws Exception {
        StringProperty propertyWithDefault = new StringProperty("with.default");
        StringProperty propertyWithoutDefault = new StringProperty("without.default");
        when(resources.getDefaultProperty("with.default")).thenReturn("default");
        when(resources.getProperty("with.default")).thenReturn("default");
        DefaultMutableTreeNode controlsTree = new DefaultMutableTreeNode();
        controlsTree.add(controlNode(propertyWithDefault));
        controlsTree.add(controlNode(propertyWithoutDefault));
        OptionPanel optionPanel = new OptionPanel(null, (properties, removals) -> { });
        initializeControls(optionPanel, controlsTree);
        optionPanel.setProperties();

        assertThat(changedOptionProperties(optionPanel)).isEmpty();

        propertyWithDefault.setValue("other");
        assertThat(changedOptionProperties(optionPanel)).containsOnlyKeys("with.default");
        assertThat(changedOptionProperties(optionPanel).getProperty("with.default")).isEqualTo("other");
    }

    @Test
    public void loadedValueCancelsPendingRemoval() throws Exception {
        StringProperty property = new StringProperty("property");
        when(resources.getDefaultProperty("property")).thenReturn("default");
        DefaultMutableTreeNode controlsTree = new DefaultMutableTreeNode();
        controlsTree.add(controlNode(property));
        when(resources.getProperty("property")).thenReturn("other");
        when(resources.isPropertySetByUser("property")).thenReturn(true);
        OptionPanel optionPanel = new OptionPanel(null, (properties, removals) -> { });
        initializeControls(optionPanel, controlsTree);
        optionPanel.setProperties();
        PreferencePropertyResetControl resetControl = resetControl(property);
        resetControl.decorate(property.getValueComponent());
        resetButton(resetControl).doClick();
        assertThat(property.isUserPropertyRemovalPending()).isTrue();

        loadOptions(optionPanel, "property=loaded\n");

        assertThat(property.getValue()).isEqualTo("loaded");
        assertThat(property.isUserPropertyRemovalPending()).isFalse();
    }

    private DefaultMutableTreeNode controlNode(IPropertyControl control) {
        return new DefaultMutableTreeNode(new IPropertyControlCreator() {
            @Override
            public IPropertyControl createControl() {
                return control;
            }

            @Override
            public String getPropertyName() {
                return control.getName();
            }
        });
    }

    private void initializeControls(OptionPanel optionPanel, DefaultMutableTreeNode controlsTree) throws Exception {
        Method method = OptionPanel.class.getDeclaredMethod("initControls", DefaultMutableTreeNode.class);
        method.setAccessible(true);
        method.invoke(optionPanel, controlsTree);
    }

    private Properties changedOptionProperties(OptionPanel optionPanel) throws Exception {
        Method method = OptionPanel.class.getDeclaredMethod("getChangedOptionProperties");
        method.setAccessible(true);
        return (Properties) method.invoke(optionPanel);
    }

    private void resetAllPropertiesToDefaults(OptionPanel optionPanel) throws Exception {
        Method method = OptionPanel.class.getDeclaredMethod("resetAllPropertiesToDefaults");
        method.setAccessible(true);
        method.invoke(optionPanel);
    }

    private void loadOptions(OptionPanel optionPanel, String content) throws Exception {
        Method method = OptionPanel.class.getDeclaredMethod("loadOptions", java.io.InputStream.class);
        method.setAccessible(true);
        method.invoke(optionPanel,
                new ByteArrayInputStream(content.getBytes(StandardCharsets.ISO_8859_1)));
    }

    private PreferencePropertyResetControl resetControl(PropertyBean property) throws Exception {
        Field field = PropertyBean.class.getDeclaredField("resetControl");
        field.setAccessible(true);
        return (PreferencePropertyResetControl) field.get(property);
    }

    private JButton resetButton(PreferencePropertyResetControl resetControl) throws Exception {
        Field field = PreferencePropertyResetControl.class.getDeclaredField("button");
        field.setAccessible(true);
        return (JButton) field.get(resetControl);
    }
}
