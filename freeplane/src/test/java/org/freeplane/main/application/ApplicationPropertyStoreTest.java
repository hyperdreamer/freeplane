package org.freeplane.main.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Properties;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ApplicationPropertyStoreTest {
	private Path temporaryDirectory;
	private ApplicationPropertyStore uut;

	@Before
	public void setUp() throws Exception {
		temporaryDirectory = Files.createTempDirectory("freeplane-property-store-");
		Properties defaultProperties = new Properties();
		File autoFile = temporaryDirectory.resolve("auto.properties").toFile();
		File secretsFile = temporaryDirectory.resolve("secrets.properties").toFile();
		uut = new ApplicationPropertyStore(defaultProperties, autoFile, secretsFile);
	}

	@After
	public void tearDown() throws Exception {
		if (temporaryDirectory == null || !Files.exists(temporaryDirectory)) {
			return;
		}
		try (Stream<Path> files = Files.walk(temporaryDirectory)) {
			files.sorted(Comparator.reverseOrder()).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				}
				catch (Exception e) {
				}
			});
		}
	}

	@Test
	public void secretPropertyIsVisibleThroughTwoArgGetPropertyAndUnsecuredProperties() {
		uut.setProperty("id", "secret-value");
		uut.persistPropertyInSecretsFile("id");

		assertThat(uut.getProperty("id", "fallback")).isEqualTo("secret-value");
		assertThat(uut.getUnsecuredProperties().getProperty("id")).isEqualTo("secret-value");
	}

	@Test
	public void twoArgGetPropertyReturnsDefaultForMissingProperty() {
		assertThat(uut.getProperty("missing", "fallback")).isEqualTo("fallback");
	}

	@Test
	public void unsecuredPropertiesDoNotContainSecureReadProperty() {
		uut.setProperty("secured.key", "value");
		uut.securePropertyForReadingAndModification("secured.key");

		assertThat(uut.getUnsecuredProperties().getProperty("secured.key")).isNull();
		assertThat(uut.getUnsecuredProperties().containsKey("secured.key")).isFalse();
	}

	@Test
	public void removeUserPropertyRevealsDefault() {
		uut.setDefaultProperty("property", "default");
		uut.setProperty("property", "override");

		uut.removeUserProperty("property");

		assertThat(uut.getProperty("property")).isEqualTo("default");
		assertThat(uut.isPropertySetByUser("property")).isFalse();
	}

	@Test
	public void removeUserPropertyDeletesSecretAndSecuredValues() throws Exception {
		uut.setDefaultProperty("property", "default");
		uut.setProperty("property", "secret override");
		uut.persistPropertyInSecretsFile("property");
		uut.securePropertyForModification("property");

		uut.removeUserProperty("property");

		assertThat(uut.getProperty("property")).isEqualTo("default");
		assertThat(uut.getSecuredProperties().getProperty("property")).isEqualTo("default");
		assertThat(uut.isPropertySetByUser("property")).isFalse();

		uut.setProperty("property", "later secret");
		uut.saveProperties("test");
		Properties autoProperties = loadProperties(temporaryDirectory.resolve("auto.properties"));
		Properties secretProperties = loadProperties(temporaryDirectory.resolve("secrets.properties"));
		assertThat(autoProperties).doesNotContainKey("property");
		assertThat(secretProperties).containsEntry("property", "later secret");
	}

	@Test
	public void unsecuredPropertiesEntrySetSkipsDefaultOnlyProperties() {
		Properties defaultProperties = new Properties();
		defaultProperties.setProperty("antialias", "enabled");
		File autoFile = temporaryDirectory.resolve("auto-second.properties").toFile();
		File secretsFile = temporaryDirectory.resolve("secrets-second.properties").toFile();
		ApplicationPropertyStore uut = new ApplicationPropertyStore(defaultProperties, autoFile, secretsFile);

		assertThat(uut.getUnsecuredProperties().entrySet()).isEmpty();
		assertThat(uut.getUnsecuredProperties().getProperty("antialias")).isEqualTo("enabled");
	}

	private Properties loadProperties(Path file) throws Exception {
		Properties properties = new Properties();
		try (java.io.InputStream input = Files.newInputStream(file)) {
			properties.load(input);
		}
		return properties;
	}
}
