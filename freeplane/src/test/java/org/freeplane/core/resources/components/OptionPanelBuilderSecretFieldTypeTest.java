package org.freeplane.core.resources.components;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.util.Enumeration;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.Test;

public class OptionPanelBuilderSecretFieldTypeTest {

	@Test
	public void createsSecretPropertyForSecretXmlTag() {
		OptionPanelBuilder uut = new OptionPanelBuilder();

		uut.load(new StringReader(
			"<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
				+ "<preferences_structure xmlns=\"http://freeplane.sf.net/ui/preferences/1.0\">"
				+ "<tabbed_pane><tab name=\"plugins\"><separator name=\"ai\">"
				+ "<secret name=\"api_key\"/>"
				+ "<string name=\"service_address\"/>"
				+ "</separator></tab></tabbed_pane>"
				+ "</preferences_structure>"));

		IPropertyControlCreator secretCreator = findCreatorByPropertyName(uut.getRoot(), "api_key");
		IPropertyControlCreator stringCreator = findCreatorByPropertyName(uut.getRoot(), "service_address");

		assertThat(secretCreator).isNotNull();
		assertThat(secretCreator.createControl()).isInstanceOf(SecretProperty.class);
		assertThat(stringCreator).isNotNull();
		assertThat(stringCreator.createControl()).isInstanceOf(StringProperty.class);
	}

	@Test
	public void createsChoiceOrNumberPropertyForChoiceOrNumberXmlTag() {
		OptionPanelBuilder uut = new OptionPanelBuilder();

		uut.load(new StringReader(
			"<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
				+ "<preferences_structure xmlns=\"http://freeplane.sf.net/ui/preferences/1.0\">"
				+ "<tabbed_pane><tab name=\"plugins\"><separator name=\"ai\">"
				+ "<choice_or_number name=\"ai_temperature\" blankValue=\"model_default\" customText=\"custom\">"
				+ "<choice value=\"model_default\" text=\"model default\"/>"
				+ "<choice value=\"0\" text=\"0\"/>"
				+ "<choice value=\"1.0\" text=\"1.0\"/>"
				+ "</choice_or_number>"
				+ "</separator></tab></tabbed_pane>"
				+ "</preferences_structure>"));

		IPropertyControlCreator creator = findCreatorByPropertyName(uut.getRoot(), "ai_temperature");
		ChoiceOrNumberProperty property = (ChoiceOrNumberProperty) creator.createControl();

		property.setValue("");
		assertThat(property.getValue()).isEqualTo("model_default");
		property.setValue("1");
		assertThat(property.getValue()).isEqualTo("1.0");
		property.setValue("0.9");
		assertThat(property.getValue()).isEqualTo("0.9");
		property.setValue("broken");
		assertThat(property.getValue()).isEqualTo("model_default");
	}

	private IPropertyControlCreator findCreatorByPropertyName(DefaultMutableTreeNode root, String propertyName) {
		Enumeration<?> nodes = root.preorderEnumeration();
		while (nodes.hasMoreElements()) {
			Object node = nodes.nextElement();
			if (!(node instanceof DefaultMutableTreeNode)) {
				continue;
			}
			Object userObject = ((DefaultMutableTreeNode) node).getUserObject();
			if (!(userObject instanceof IPropertyControlCreator)) {
				continue;
			}
			IPropertyControlCreator creator = (IPropertyControlCreator) userObject;
			if (propertyName.equals(creator.getPropertyName())) {
				return creator;
			}
		}
		return null;
	}
}
