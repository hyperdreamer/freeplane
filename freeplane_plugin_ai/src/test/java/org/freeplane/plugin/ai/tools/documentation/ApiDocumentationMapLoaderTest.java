package org.freeplane.plugin.ai.tools.documentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.freeplane.features.map.MapModel;
import org.junit.Test;

public class ApiDocumentationMapLoaderTest {
    @Test
    public void loadInstalledApiMap_throwsExactMissingMapError() throws Exception {
        File installationDir = Files.createTempDirectory("api-doc-map-loader-missing").toFile();
        ApiDocumentationMapLoader uut = new ApiDocumentationMapLoader(
            constantInstallationDir(installationDir),
            file -> mock(MapModel.class));

        assertThatThrownBy(uut::loadInstalledApiMap)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("API documentation map is not installed/generated. Expected file: "
                + new File(installationDir, "doc/api/freeplane-api.mm").getAbsolutePath()
                + ". Remedy: run gradle generateFreeplaneApiMap for this installation so freeplane-api.mm exists at that path.");
    }

    @Test
    public void loadInstalledApiMap_usesInstalledDocumentationPath() throws Exception {
        File installationDir = Files.createTempDirectory("api-doc-map-loader-success").toFile();
        File mapFile = new File(installationDir, "doc/api/freeplane-api.mm");
        mapFile.getParentFile().mkdirs();
        Files.write(mapFile.toPath(), "test".getBytes(StandardCharsets.UTF_8));
        MapModel mapModel = mock(MapModel.class);
        CapturingDocumentationMapOpener opener = new CapturingDocumentationMapOpener(mapModel);
        ApiDocumentationMapLoader uut = new ApiDocumentationMapLoader(constantInstallationDir(installationDir), opener);

        ApiDocumentationMapLoader.LoadedApiDocumentationMap loadedMap = uut.loadInstalledApiMap();

        assertThat(opener.loadedFile).isEqualTo(mapFile);
        assertThat(loadedMap.getMapFile()).isEqualTo(mapFile);
        assertThat(loadedMap.getMapModel()).isSameAs(mapModel);
    }

    private ApiDocumentationMapLoader.InstallationBaseDirectoryProvider constantInstallationDir(File installationDir) {
        return () -> installationDir;
    }

    private static final class CapturingDocumentationMapOpener implements ApiDocumentationMapLoader.DocumentationMapOpener {
        private final MapModel mapModel;
        private File loadedFile;

        private CapturingDocumentationMapOpener(MapModel mapModel) {
            this.mapModel = mapModel;
        }

        @Override
        public MapModel load(File file) {
            this.loadedFile = file;
            return mapModel;
        }
    }
}
