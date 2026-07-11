/*
 *  Freeplane - mind map editor
 *  Copyright (C) 2008 Joerg Mueller, Daniel Polansky, Christian Foltin, Dimitry Polivaev
 *
 *  This file is created by Dimitry Polivaev in 2008.
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
package org.freeplane.main.application;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

import org.freeplane.core.resources.IFreeplanePropertyListener;
import org.freeplane.core.resources.ResourceBundles;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.Compat;
import org.freeplane.core.util.FreeplaneVersion;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.filter.FilterController;
import org.freeplane.features.icon.IconController;
import org.freeplane.features.icon.mindmapmode.MIconController;
import org.freeplane.features.mode.mindmapmode.MModeController;

/**
 * @author Dimitry Polivaev
 */
public class ApplicationResourceController extends ResourceController {
    private static final String USE_SYSTEM_LOCALE_PROPERTY = "useSystemLocale";

    public static File getUserPreferencesFile() {
        final String freeplaneDirectory = Compat.getApplicationUserDirectory();
        final File userPropertiesFolder = new File(freeplaneDirectory);
        final File autoPropertiesFile = new File(userPropertiesFolder, "auto.properties");
        return autoPropertiesFile;
    }

    private static File getUserSecretsFile() {
        final String freeplaneDirectory = Compat.getApplicationUserDirectory();
        final File userPropertiesFolder = new File(freeplaneDirectory);
        final File secretsPropertiesFile = new File(userPropertiesFolder, "secrets.properties");
        return secretsPropertiesFile;
    }

	final private ApplicationPropertyStore propertyStore;
	private LastOpenedList lastOpened;
	public static final String FREEPLANE_BASEDIRECTORY_PROPERTY = "org.freeplane.basedirectory";
	public static final String FREEPLANE_GLOBALRESOURCEDIR_PROPERTY = "org.freeplane.globalresourcedir";
	public static final String DEFAULT_FREEPLANE_GLOBALRESOURCEDIR = "resources";
    private final ArrayList<File> resourceDirectories;
    private final Set<ClassLoader> resourceLoaders;

	public static void showSysInfo() {
		final StringBuilder info = new StringBuilder();
		info.append("freeplane_version = ");
		final FreeplaneVersion freeplaneVersion = FreeplaneVersion.getVersion();
		info.append(freeplaneVersion);
		String revision = freeplaneVersion.getRevision();

		info.append("; freeplane_xml_version = ");
		info.append(FreeplaneVersion.XML_VERSION);
		if(! revision.equals("")){
			info.append("\ngit revision = ");
			info.append(revision);
		}
		info.append("\njava_version = ");
		info.append(System.getProperty("java.version"));
		info.append("; os_name = ");
		info.append(System.getProperty("os.name"));
		info.append("; os_version = ");
		info.append(System.getProperty("os.version"));
		LogUtils.info(info.toString());
	}


	public static String RESOURCE_BASE_DIRECTORY;
	public static String INSTALLATION_BASE_DIRECTORY;
	static {
		try {
			RESOURCE_BASE_DIRECTORY = new File(System.getProperty(ApplicationResourceController.FREEPLANE_GLOBALRESOURCEDIR_PROPERTY,
			ApplicationResourceController.DEFAULT_FREEPLANE_GLOBALRESOURCEDIR)).getCanonicalPath();
			INSTALLATION_BASE_DIRECTORY = new File(System.getProperty(ApplicationResourceController.FREEPLANE_BASEDIRECTORY_PROPERTY, RESOURCE_BASE_DIRECTORY + "/..")).getCanonicalPath();
		} catch (IOException e) {
		}
	}

	ApplicationResourceController(ApplicationPropertyStore propertyStore) {
		super();
		this.propertyStore = propertyStore;
		resourceDirectories = new ArrayList<File>(0);
		resourceLoaders = new LinkedHashSet<ClassLoader>();
	}

	public ApplicationResourceController() {
		super();
		resourceDirectories = new ArrayList<File>(2);
		Properties defaultProperties = readDefaultPreferences();
		propertyStore = new ApplicationPropertyStore(defaultProperties, getUserPreferencesFile(), getUserSecretsFile());
		if (!propertyStore.hasLoadedAutoProperties() && !propertyStore.hasLoadedSecretsProperties()) {
			System.err.println("User properties not found, new file created");
		}
		replacePropertyKey("keepSelectedNodeVisibleAfterZoom", "keepSelectedNodeVisible");
		replacePropertyKey("foldingsymbolwidth", "foldingsymbolsize");
		final File userDir = createUserDirectory();
		final String resourceBaseDir = getResourceBaseDir();
		if (resourceBaseDir != null) {
			try {
				final File userResourceDir = new File(userDir, "resources");
				userResourceDir.mkdirs();
				resourceDirectories.add(userResourceDir);
				final File resourceDir = new File(resourceBaseDir);
				resourceDirectories.add(resourceDir);
			}
			catch (final Exception e) {
				e.printStackTrace();
			}
		}
		resourceLoaders = new LinkedHashSet<>();
		if(! getBooleanProperty(USE_SYSTEM_LOCALE_PROPERTY))
		    setDefaultLocale(getProperty(ResourceBundles.RESOURCE_LANGUAGE));
		addPropertyChangeListener(new IFreeplanePropertyListener() {
			@Override
			public void propertyChanged(final String propertyName, final String newValue, final String oldValue) {
				if (propertyName.equals(ResourceBundles.RESOURCE_LANGUAGE)) {
					loadAnotherLanguage();
				}
			}
		});
	}

    private void replacePropertyKey(String oldKey, String newKey) {
		propertyStore.replacePropertyKey(oldKey, newKey);
    }

	private File createUserDirectory() {
		final File userPropertiesFolder = new File(getFreeplaneUserDirectory());
		try {
			if (!userPropertiesFolder.exists()) {
				userPropertiesFolder.mkdirs();
			}
			return userPropertiesFolder;
		}
		catch (final Exception e) {
			e.printStackTrace();
			System.err.println("Cannot create folder for user properties and logging: '"
			        + userPropertiesFolder.getAbsolutePath() + "'");
			return null;
		}
	}

	@Override
	public String getDefaultProperty(final String key) {
		return propertyStore.getDefaultProperty(key);
	}

	@Override
	public String getFreeplaneUserDirectory() {
		return Compat.getApplicationUserDirectory();
	}

	public LastOpenedList getLastOpenedList() {
		return lastOpened;
	}

	@Override
	public Properties getUnsecuredProperties() {
		return propertyStore.getUnsecuredProperties();
	}

	@Override
	public String getProperty(final String key) {
	    return propertyStore.getProperty(key);
	}

	@Override
	public String getProperty(final String key, final String defaultValue) {
		return propertyStore.getProperty(key, defaultValue);
	}

	@Override
	public URL getResource(final String resourcePath) {
		return AccessController.doPrivileged(new PrivilegedAction<URL>() {

			@Override
			public URL run() {
				final String relName = removeSlashAtStart(resourcePath);
				for(File directory : resourceDirectories) {
					File fileResource = new File(directory, relName);
					if (fileResource.exists()) {
						try {
							return Compat.fileToUrl(fileResource);
						} catch (MalformedURLException e) {
							throw new RuntimeException(e);
						}
					}
				}
				{
				    URL resource = ApplicationResourceController.super.getResource(resourcePath);
				    if (resource != null) {
				        return resource;
				    }
				}
				if ("/lib/freeplaneviewer.jar".equals(resourcePath)) {
				    final String rootDir = new File(getResourceBaseDir()).getAbsoluteFile().getParent();
					try {
						final File try1 = new File(rootDir + "/plugins/org.freeplane.core/lib/freeplaneviewer.jar");
						if (try1.exists()) {
							return try1.toURL();
						}
						final File try2 = new File(rootDir + "/lib/freeplaneviewer.jar");
						if (try2.exists()) {
							return try2.toURL();
						}
					}
					catch (final MalformedURLException e) {
						e.printStackTrace();
					}
				}
				for(ClassLoader loader : resourceLoaders) {
				    URL resource = loader.getResource(resourcePath);
				    if(resource  != null)
				        return resource;
				}

				return null;
			}
		});
	}

	private String removeSlashAtStart(final String name) {
		final String relName;
		if (name.startsWith("/")) {
			relName = name.substring(1);
		}
		else {
			relName = name;
		}
		return relName;
	}

	@Override
	public String getResourceBaseDir() {
		return RESOURCE_BASE_DIRECTORY;
	}

	@Override
	public String getInstallationBaseDir() {
		return INSTALLATION_BASE_DIRECTORY;
    }

	public void registerResourceLoader(ClassLoader loader) {
	    resourceLoaders.add(loader);
	}

	@Override
	public void init() {
		lastOpened = new LastOpenedList();
		super.init();
	}

	private Properties readDefaultPreferences() {
		final Properties props = new Properties();
		readDefaultPreferences(props, ResourceController.FREEPLANE_PROPERTIES);
		final String propsLocs = props.getProperty("load_next_properties", "");
		readDefaultPreferences(props, propsLocs.split(";"));
		return props;
	}

	private void readDefaultPreferences(final Properties props, final String[] locArray) {
		for (final String loc : locArray) {
			readDefaultPreferences(props, loc);
		}
	}

	private void readDefaultPreferences(final Properties props, final String propsLoc) {
		final URL defaultPropsURL = getResource(propsLoc);
		loadProperties(props, defaultPropsURL);
	}

	@Override
	public void removeUserProperty(String key) {
		final String oldValue = getProperty(key);
		propertyStore.removeUserProperty(key);
		firePropertyChanged(key, getProperty(key), oldValue);
	}

	@Override
	public void saveProperties() {
		MModeController modeController = MModeController.getMModeController();
		if(modeController != null) {
			MIconController iconController = (MIconController)modeController.getExtension(IconController.class);
			if(iconController != null)
				iconController.saveRecentlyUsedActions();
		}
		propertyStore.saveProperties(FreeplaneVersion.getVersion().toString());
		try {
			((ResourceBundles)getResources()).saveUserResources();
		}
		catch (final Exception ex) {
		}
		FilterController filterController = FilterController.getCurrentFilterController();
		if(filterController != null)
			filterController.saveConditions();
	}

	/**
	 * @param pProperties
	 */
	private void setDefaultLocale(final String lang ) {
		if (lang == null) {
			return;
		}
		Locale localeDef = null;
		switch (lang.length()) {
			case 2:
				localeDef = new Locale(lang);
				break;
			case 5:
				localeDef = new Locale(lang.substring(0, 2), lang.substring(3, 5));
				break;
			default:
				return;
		}
		Locale.setDefault(localeDef);
	}

	@Override
	public void setDefaultProperty(final String key, final String value) {
		propertyStore.setDefaultProperty(key, value);
	}

	@Override
	public void setProperty(final String key, final String value) {
		final String oldValue = getProperty(key);
		if (oldValue == value) {
			return;
		}
		if (oldValue != null && oldValue.equals(value)) {
			return;
		}
		propertyStore.setProperty(key, value);
		firePropertyChanged(key, value, oldValue);
	}

    @Override
    public Properties getSecuredProperties() {
        return propertyStore.getSecuredProperties();
    }

    @Override
    public void securePropertyForModification(String key) {
		propertyStore.securePropertyForModification(key);
    }
    @Override
    public void securePropertyForReadingAndModification(String key) {
		propertyStore.securePropertyForReadingAndModification(key);
    }

    @Override
    public void persistPropertyInSecretsFile(String key) {
		propertyStore.persistPropertyInSecretsFile(key);
    }

    @Override
    public boolean isPropertySetByUser(String key) {
        return propertyStore.isPropertySetByUser(key);
    }
}
