package org.freeplane.main.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.freeplane.core.resources.IFreeplanePropertyListener;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ApplicationResourceControllerTest {
    private Path temporaryDirectory;

    @Before
    public void setUp() throws Exception {
        temporaryDirectory = Files.createTempDirectory("freeplane-resource-controller-");
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
    public void removeUserPropertyPublishesRevealedDefault() {
        Properties defaults = new Properties();
        defaults.setProperty("property", "default");
        File autoFile = temporaryDirectory.resolve("auto.properties").toFile();
        File secretsFile = temporaryDirectory.resolve("secrets.properties").toFile();
        ApplicationPropertyStore store = new ApplicationPropertyStore(defaults, autoFile, secretsFile);
        ApplicationResourceController uut = new ApplicationResourceController(store);
        uut.setProperty("property", "override");
        AtomicReference<PropertyChange> change = new AtomicReference<PropertyChange>();
        uut.addPropertyChangeListener(new IFreeplanePropertyListener() {
            @Override
            public void propertyChanged(String propertyName, String newValue, String oldValue) {
                change.set(new PropertyChange(propertyName, newValue, oldValue));
            }
        });

        uut.removeUserProperty("property");

        assertThat(change.get().propertyName).isEqualTo("property");
        assertThat(change.get().newValue).isEqualTo("default");
        assertThat(change.get().oldValue).isEqualTo("override");
    }

    private static class PropertyChange {
        private final String propertyName;
        private final String newValue;
        private final String oldValue;

        private PropertyChange(String propertyName, String newValue, String oldValue) {
            this.propertyName = propertyName;
            this.newValue = newValue;
            this.oldValue = oldValue;
        }
    }
}
