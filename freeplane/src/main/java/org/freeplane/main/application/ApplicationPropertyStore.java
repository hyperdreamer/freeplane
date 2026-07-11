package org.freeplane.main.application;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.security.AllPermission;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

class ApplicationPropertyStore {
	private static final AllPermission ALL_PERMISSION = new AllPermission();

	private final File autoPropertiesFile;
	private final File secretsPropertiesFile;
	private final Properties defProps;
	private final Properties props;
	private final Properties secretsProps;
	private final Properties securedProps;
	private final Set<String> securedForReadingPropertyKeys;
	private final Set<String> persistedInSecretsFilePropertyKeys;
	private final Properties unsecuredPropertiesView;
	private final boolean hasLoadedAutoProperties;
	private final boolean hasLoadedSecretsProperties;

	ApplicationPropertyStore(Properties defaultProperties, File autoPropertiesFile, File secretsPropertiesFile) {
		this.autoPropertiesFile = autoPropertiesFile;
		this.secretsPropertiesFile = secretsPropertiesFile;
		defProps = defaultProperties;
		props = new SortedProperties(defProps);
		hasLoadedAutoProperties = loadUserProperties(props, this.autoPropertiesFile);
		secretsProps = new SortedProperties(props);
		hasLoadedSecretsProperties = loadUserProperties(secretsProps, this.secretsPropertiesFile);
		securedProps = new Properties(secretsProps);
		securedForReadingPropertyKeys = new HashSet<>();
		persistedInSecretsFilePropertyKeys = new HashSet<>();
		unsecuredPropertiesView = new JoinedPropertiesView();
	}

	boolean hasLoadedAutoProperties() {
		return hasLoadedAutoProperties;
	}

	boolean hasLoadedSecretsProperties() {
		return hasLoadedSecretsProperties;
	}

	void replacePropertyKey(String oldKey, String newKey) {
		replacePropertyKey(props, oldKey, newKey);
		replacePropertyKey(secretsProps, oldKey, newKey);
	}

	String getDefaultProperty(String key) {
		return defProps.getProperty(key);
	}

	void setDefaultProperty(String key, String value) {
		defProps.setProperty(key, value);
	}

	Properties getUnsecuredProperties() {
		return unsecuredPropertiesView;
	}

	String getProperty(String key) {
		if (securedForReadingPropertyKeys.contains(key)) {
			checkSecurityPermission();
		}
		return securedProps.getProperty(key);
	}

	String getProperty(String key, String defaultValue) {
		String value = getProperty(key);
		return value != null ? value : defaultValue;
	}

	void setProperty(String key, String value) {
		if (securedProps.containsKey(key)) {
			checkSecurityPermission();
			securedProps.setProperty(key, value);
		}
		if (persistedInSecretsFilePropertyKeys.contains(key)) {
			secretsProps.setProperty(key, value);
			props.remove(key);
		}
		else {
			props.setProperty(key, value);
		}
	}

	void saveProperties(String freeplaneVersion) {
		try (OutputStream out = new FileOutputStream(autoPropertiesFile)) {
			final OutputStreamWriter outputStreamWriter = new OutputStreamWriter(out, "8859_1");
			outputStreamWriter.write("#Freeplane ");
			outputStreamWriter.write(freeplaneVersion);
			outputStreamWriter.write('\n');
			outputStreamWriter.flush();
			props.store(out, null);
		}
		catch (final Exception ex) {
		}
		try (OutputStream out = new FileOutputStream(secretsPropertiesFile)) {
			final OutputStreamWriter outputStreamWriter = new OutputStreamWriter(out, "8859_1");
			outputStreamWriter.write("#Freeplane ");
			outputStreamWriter.write(freeplaneVersion);
			outputStreamWriter.write('\n');
			outputStreamWriter.flush();
			secretsProps.store(out, null);
		}
		catch (final Exception ex) {
		}
	}

	Properties getSecuredProperties() {
		checkSecurityPermission();
		return securedProps;
	}

	void securePropertyForModification(String key) {
		checkSecurityPermission();
		if (!securedProps.containsKey(key)) {
			String propertyValue = getProperty(key);
			if (propertyValue != null) {
				securedProps.setProperty(key, propertyValue);
			}
		}
	}

	void securePropertyForReadingAndModification(String key) {
		securePropertyForModification(key);
		securedForReadingPropertyKeys.add(key);
	}

	void persistPropertyInSecretsFile(String key) {
		if (props.containsKey(key)) {
			checkSecurityPermission();
			String value = props.getProperty(key);
			if (value != null) {
				secretsProps.setProperty(key, value);
			}
			props.remove(key);
		}
		persistedInSecretsFilePropertyKeys.add(key);
	}

	boolean isPropertySetByUser(String key) {
		return props.containsKey(key) || secretsProps.containsKey(key);
	}

	void removeUserProperty(String key) {
		props.remove(key);
		secretsProps.remove(key);
		securedProps.remove(key);
	}

	private boolean loadUserProperties(Properties target, File file) {
		try (InputStream in = new FileInputStream(file)) {
			target.load(in);
			return true;
		}
		catch (final Exception ex) {
			return false;
		}
	}

	private void replacePropertyKey(Properties sourceProperties, String oldKey, String newKey) {
		if (sourceProperties.containsKey(oldKey) && !sourceProperties.containsKey(newKey)) {
			sourceProperties.put(newKey, sourceProperties.getProperty(oldKey));
		}
	}

	private boolean isUnsecuredKey(String key) {
		return !securedForReadingPropertyKeys.contains(key);
	}

	private String getUnsecuredProperty(String key) {
		if (!isUnsecuredKey(key)) {
			return null;
		}
		return secretsProps.getProperty(key);
	}

	private Set<String> unsecuredKeys() {
		TreeSet<String> keys = new TreeSet<>();
		for (Object key : props.keySet()) {
			keys.add((String) key);
		}
		for (Object key : secretsProps.keySet()) {
			keys.add((String) key);
		}
		keys.removeAll(securedForReadingPropertyKeys);
		return keys;
	}

	private Object putUnsecuredProperty(String key, String value) {
		Object oldValue = getUnsecuredProperty(key);
		setProperty(key, value);
		return oldValue;
	}

	private Object removeUnsecuredProperty(String key) {
		Object oldValue = getUnsecuredProperty(key);
		if (persistedInSecretsFilePropertyKeys.contains(key)) {
			secretsProps.remove(key);
		}
		else {
			props.remove(key);
			secretsProps.remove(key);
		}
		securedProps.remove(key);
		return oldValue;
	}

	private static void checkSecurityPermission() {
		SecurityManager sm = System.getSecurityManager();
		if (sm != null) {
			sm.checkPermission(ALL_PERMISSION);
		}
	}

	private class JoinedPropertiesView extends Properties {
		private static final long serialVersionUID = 1L;

		@Override
		public synchronized Enumeration<Object> keys() {
			return Collections.enumeration(new TreeSet<Object>(unsecuredKeys()));
		}

		@Override
		public Set<Map.Entry<Object, Object>> entrySet() {
			Map<Object, Object> entries = new LinkedHashMap<>();
			for (String key : unsecuredKeys()) {
				entries.put(key, getUnsecuredProperty(key));
			}
			return entries.entrySet();
		}

		@Override
		public String getProperty(String key) {
			return getUnsecuredProperty(key);
		}

		@Override
		public String getProperty(String key, String defaultValue) {
			String value = getProperty(key);
			return value != null ? value : defaultValue;
		}

		@Override
		public synchronized Object get(Object key) {
			if (!(key instanceof String)) {
				return null;
			}
			return getUnsecuredProperty((String) key);
		}

		@Override
		public synchronized boolean containsKey(Object key) {
			if (!(key instanceof String)) {
				return false;
			}
			String propertyKey = (String) key;
			return isUnsecuredKey(propertyKey)
					&& (props.containsKey(propertyKey) || secretsProps.containsKey(propertyKey));
		}

		@Override
		public synchronized Object put(Object key, Object value) {
			return putUnsecuredProperty((String) key, (String) value);
		}

		@Override
		public synchronized Object remove(Object key) {
			if (!(key instanceof String)) {
				return null;
			}
			return removeUnsecuredProperty((String) key);
		}

		@Override
		public Set<String> stringPropertyNames() {
			return unsecuredKeys();
		}

		@Override
		public synchronized Enumeration<?> propertyNames() {
			return Collections.enumeration(unsecuredKeys());
		}
	}
}
