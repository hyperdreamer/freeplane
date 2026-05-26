package org.freeplane.plugin.ai.tools.documentation;

import java.io.File;
import java.util.Objects;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.mindmapmode.MMapController;
import org.freeplane.features.url.mindmapmode.MapLoader;

public class ApiDocumentationMapLoader {
    private static final String RELATIVE_API_MAP_PATH = "doc/api/freeplane-api.mm";

    private final InstallationBaseDirectoryProvider installationBaseDirectoryProvider;
    private final DocumentationMapOpener documentationMapOpener;

    public ApiDocumentationMapLoader(MMapController mapController) {
        this(new ResourceControllerInstallationBaseDirectoryProvider(), new DocumentationMapLoaderOpener(mapController));
    }

    ApiDocumentationMapLoader(InstallationBaseDirectoryProvider installationBaseDirectoryProvider,
                              DocumentationMapOpener documentationMapOpener) {
        this.installationBaseDirectoryProvider = Objects.requireNonNull(
            installationBaseDirectoryProvider, "installationBaseDirectoryProvider");
        this.documentationMapOpener = Objects.requireNonNull(documentationMapOpener, "documentationMapOpener");
    }

    public LoadedApiDocumentationMap loadInstalledApiMap() {
        File installationBaseDir = installationBaseDirectoryProvider.getInstallationBaseDirectory();
        File mapFile = new File(installationBaseDir, RELATIVE_API_MAP_PATH);
        if (!mapFile.isFile() || !mapFile.canRead()) {
            throw new IllegalStateException(buildMissingMapMessage(mapFile));
        }
        MapModel mapModel = documentationMapOpener.load(mapFile);
        if (mapModel == null) {
            throw new IllegalStateException("API documentation map could not be loaded from "
                + mapFile.getAbsolutePath() + ".");
        }
        return new LoadedApiDocumentationMap(mapFile, mapModel);
    }

    private String buildMissingMapMessage(File mapFile) {
        return "API documentation map is not installed/generated. Expected file: "
            + mapFile.getAbsolutePath()
            + ". Remedy: run gradle generateFreeplaneApiMap for this installation so freeplane-api.mm exists at that path.";
    }

    interface InstallationBaseDirectoryProvider {
        File getInstallationBaseDirectory();
    }

    interface DocumentationMapOpener {
        MapModel load(File file);
    }

    static final class LoadedApiDocumentationMap {
        private final File mapFile;
        private final MapModel mapModel;

        LoadedApiDocumentationMap(File mapFile, MapModel mapModel) {
            this.mapFile = Objects.requireNonNull(mapFile, "mapFile");
            this.mapModel = Objects.requireNonNull(mapModel, "mapModel");
        }

        public File getMapFile() {
            return mapFile;
        }

        public MapModel getMapModel() {
            return mapModel;
        }
    }

    private static final class ResourceControllerInstallationBaseDirectoryProvider
        implements InstallationBaseDirectoryProvider {
        @Override
        public File getInstallationBaseDirectory() {
            return new File(ResourceController.getResourceController().getInstallationBaseDir());
        }
    }

    private static final class DocumentationMapLoaderOpener implements DocumentationMapOpener {
        private final MMapController mapController;

        private DocumentationMapLoaderOpener(MMapController mapController) {
            this.mapController = Objects.requireNonNull(mapController, "mapController");
        }

        @Override
        public MapModel load(File file) {
            return new MapLoader(mapController.getMModeController())
                .load(file)
                .asDocumentation()
                .getMap();
        }
    }
}
