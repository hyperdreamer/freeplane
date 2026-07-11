/*
 *  Freeplane - mind map editor
 *  Copyright (C) 2008 Joerg Mueller, Daniel Polansky, Christian Foltin, Dimitry Polivaev
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.freeplane.core.resources.components;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.Objects;
import java.util.Vector;

import javax.swing.JComponent;

import com.jgoodies.forms.builder.DefaultFormBuilder;

public abstract class PropertyBean extends PropertyAdapter {
	
	final private List<PropertyChangeListener> mPropertyChangeListeners = new Vector<PropertyChangeListener>();
	private PreferencePropertyResetControl resetControl;

	public PropertyBean(final String name) {
		super(name);
	}

	public PropertyBean(final String name, final String label, final String description) {
		super(name, label, description);
	}

	public void addPropertyChangeListener(final PropertyChangeListener listener) {
		mPropertyChangeListeners.add(listener);
	}

	protected void firePropertyChangeEvent() {
		final PropertyChangeEvent evt = new PropertyChangeEvent(this, getName(), null, getValue());
		for (final PropertyChangeListener l : mPropertyChangeListeners) {
			l.propertyChange(evt);
		}
	}

	public abstract String getValue();

	@Override
	protected void appendToForm(DefaultFormBuilder builder, JComponent component) {
		final JComponent decoratedComponent = decorateValueComponent(component);
		super.appendToForm(builder, decoratedComponent);
		if (decoratedComponent != component) {
			component.setToolTipText(decoratedComponent.getToolTipText());
		}
	}

	protected JComponent decorateValueComponent(JComponent component) {
		return resetControl == null ? component : resetControl.decorate(component);
	}

	protected JComponent alignValueComponentWithResetControl(JComponent component) {
		return resetControl == null ? component : resetControl.alignWithButton(component);
	}

	protected boolean valuesEqual(String first, String second) {
		return Objects.equals(first, second);
	}

	void configureReset(String defaultValue) {
		resetControl = new PreferencePropertyResetControl(this, defaultValue);
	}

	void initializeResetState(boolean userPropertyWasSet) {
		if (resetControl != null) {
			resetControl.initialize(userPropertyWasSet);
		}
	}

	void cancelPendingUserPropertyRemoval() {
		if (resetControl != null) {
			resetControl.cancelPendingRemoval();
		}
	}

	boolean isValueChanged() {
		return resetControl != null && resetControl.isValueChanged();
	}

	void resetToDefault() {
		if (resetControl != null) {
			resetControl.resetToDefault();
		}
	}

	boolean isUserPropertyRemovalPending() {
		return resetControl != null && resetControl.isUserPropertyRemovalPending();
	}

	public void removePropertyChangeListener(final PropertyChangeListener listener) {
		mPropertyChangeListeners.remove(listener);
	}

	public abstract void setValue(String value);

	@Override
	public void setEnabled(boolean enabled) {
		super.setEnabled(enabled);
		if (resetControl != null) {
			resetControl.setEditorEnabled(enabled);
		}
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "(" + getName() + "->" + getValue() + ")";
	}
}
