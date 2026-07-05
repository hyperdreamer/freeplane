package org.freeplane.plugin.ai.maps;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.plugin.ai.tools.documentation.ApiDocumentationMapLoader;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AvailableMapsTest {
    @Test
    public void getCurrentMapIdentifier_returnsStableIdentifierForSameMap() {
        MapModel mapModel = mock(MapModel.class);
        FakeMapModelProvider mapModelProvider = new FakeMapModelProvider();
        mapModelProvider.setCurrentMapModel(mapModel);
        AvailableMaps uut = new AvailableMaps(mapModelProvider);

        UUID firstIdentifier = uut.getCurrentMapIdentifier();
        UUID secondIdentifier = uut.getCurrentMapIdentifier();

        assertThat(firstIdentifier).isNotNull();
        assertThat(secondIdentifier).isEqualTo(firstIdentifier);
    }

    @Test
    public void getAvailableMapIdentifiers_returnsIdentifiersForOpenMaps() {
        MapModel firstMapModel = mock(MapModel.class);
        MapModel secondMapModel = mock(MapModel.class);
        FakeMapModelProvider mapModelProvider = new FakeMapModelProvider();
        mapModelProvider.setOpenMapModels(Arrays.asList(firstMapModel, secondMapModel));
        AvailableMaps uut = new AvailableMaps(mapModelProvider);

        List<UUID> firstIdentifiers = uut.getAvailableMapIdentifiers();
        List<UUID> secondIdentifiers = uut.getAvailableMapIdentifiers();

        assertThat(firstIdentifiers).hasSize(2);
        assertThat(firstIdentifiers).containsExactlyElementsOf(secondIdentifiers);
    }

    @Test
    public void findMapModel_returnsMapModelForIdentifier() {
        MapModel mapModel = mock(MapModel.class);
        FakeMapModelProvider mapModelProvider = new FakeMapModelProvider();
        mapModelProvider.setCurrentMapModel(mapModel);
        AvailableMaps uut = new AvailableMaps(mapModelProvider);

        UUID mapIdentifier = uut.getCurrentMapIdentifier();

        assertThat(uut.findMapModel(mapIdentifier)).isSameAs(mapModel);
    }

    @Test
    public void findMapModel_returnsNullForClosedMapIdentifier() {
        MapModel mapModel = mock(MapModel.class);
        FakeMapModelProvider mapModelProvider = new FakeMapModelProvider();
        mapModelProvider.setCurrentMapModel(mapModel);
        mapModelProvider.setOpenMapModels(Collections.singletonList(mapModel));
        AvailableMaps uut = new AvailableMaps(mapModelProvider);
        UUID mapIdentifier = uut.getCurrentMapIdentifier();

        uut.onRemove(mapModel);
        mapModelProvider.setCurrentMapModel(null);
        mapModelProvider.setOpenMapModels(Collections.emptyList());

        assertThat(uut.findMapModel(mapIdentifier)).isNull();
    }

    @Test
    public void onCreate_reusesIdentifierForReopenedUrl() throws Exception {
        File mapFile = new File("build/tmp/available-maps/reopened.mm");
        MapModel firstMapModel = mapModelForFile(mapFile);
        MapModel reopenedMapModel = mapModelForFile(mapFile);
        FakeMapModelProvider mapModelProvider = new FakeMapModelProvider();
        mapModelProvider.setCurrentMapModel(firstMapModel);
        mapModelProvider.setOpenMapModels(Collections.singletonList(firstMapModel));
        AvailableMaps uut = new AvailableMaps(mapModelProvider);
        UUID firstIdentifier = uut.getCurrentMapIdentifier();

        uut.onRemove(firstMapModel);
        mapModelProvider.setCurrentMapModel(null);
        mapModelProvider.setOpenMapModels(Collections.emptyList());
        assertThat(uut.findMapModel(firstIdentifier)).isNull();
        mapModelProvider.setCurrentMapModel(reopenedMapModel);
        mapModelProvider.setOpenMapModels(Collections.singletonList(reopenedMapModel));
        uut.onCreate(reopenedMapModel);

        assertThat(uut.findMapModel(firstIdentifier)).isSameAs(reopenedMapModel);
        assertThat(uut.getCurrentMapIdentifier()).isEqualTo(firstIdentifier);
    }

    @Test
    public void getCurrentMapIdentifier_consumesClosedUrlIdentifier() throws Exception {
        File originalFile = new File("build/tmp/available-maps/original.mm");
        File savedAsFile = new File("build/tmp/available-maps/saved-as.mm");
        MapModel originalMapModel = mapModelForFile(originalFile);
        FakeMapModelProvider mapModelProvider = new FakeMapModelProvider();
        mapModelProvider.setCurrentMapModel(originalMapModel);
        mapModelProvider.setOpenMapModels(Collections.singletonList(originalMapModel));
        AvailableMaps uut = new AvailableMaps(mapModelProvider);
        UUID originalIdentifier = uut.getCurrentMapIdentifier();

        uut.onRemove(originalMapModel);
        MapModel reopenedMapModel = mapModelForFile(originalFile);
        mapModelProvider.setCurrentMapModel(reopenedMapModel);
        mapModelProvider.setOpenMapModels(Collections.singletonList(reopenedMapModel));
        uut.onCreate(reopenedMapModel);
        assertThat(uut.findMapModel(originalIdentifier)).isSameAs(reopenedMapModel);
        stubMapFile(reopenedMapModel, savedAsFile);
        uut.onRemove(reopenedMapModel);

        MapModel mapAtOriginalFileAfterSaveAs = mapModelForFile(originalFile);
        mapModelProvider.setCurrentMapModel(mapAtOriginalFileAfterSaveAs);
        mapModelProvider.setOpenMapModels(Collections.singletonList(mapAtOriginalFileAfterSaveAs));
        uut.onCreate(mapAtOriginalFileAfterSaveAs);
        UUID originalFileIdentifierAfterSaveAs = uut.getCurrentMapIdentifier();
        assertThat(originalFileIdentifierAfterSaveAs).isNotEqualTo(originalIdentifier);
        uut.onRemove(mapAtOriginalFileAfterSaveAs);

        MapModel mapAtSavedAsFile = mapModelForFile(savedAsFile);
        mapModelProvider.setCurrentMapModel(mapAtSavedAsFile);
        mapModelProvider.setOpenMapModels(Collections.singletonList(mapAtSavedAsFile));
        uut.onCreate(mapAtSavedAsFile);

        assertThat(uut.findMapModel(originalIdentifier)).isSameAs(mapAtSavedAsFile);
    }

    @Test
    public void onCreate_preservesIdentifierWhenReopenedMapIsSavedAsBeforeIdentifierRequest() throws Exception {
        File originalFile = new File("build/tmp/available-maps/original-before-request.mm");
        File savedAsFile = new File("build/tmp/available-maps/saved-as-before-request.mm");
        MapModel originalMapModel = mapModelForFile(originalFile);
        FakeMapModelProvider mapModelProvider = new FakeMapModelProvider();
        mapModelProvider.setCurrentMapModel(originalMapModel);
        mapModelProvider.setOpenMapModels(Collections.singletonList(originalMapModel));
        AvailableMaps uut = new AvailableMaps(mapModelProvider);
        UUID originalIdentifier = uut.getCurrentMapIdentifier();

        uut.onRemove(originalMapModel);
        mapModelProvider.setCurrentMapModel(null);
        mapModelProvider.setOpenMapModels(Collections.emptyList());
        MapModel reopenedMapModel = mapModelForFile(originalFile);
        mapModelProvider.setCurrentMapModel(reopenedMapModel);
        mapModelProvider.setOpenMapModels(Collections.singletonList(reopenedMapModel));
        uut.onCreate(reopenedMapModel);
        stubMapFile(reopenedMapModel, savedAsFile);
        uut.onRemove(reopenedMapModel);

        MapModel mapAtOriginalFileAfterSaveAs = mapModelForFile(originalFile);
        mapModelProvider.setCurrentMapModel(mapAtOriginalFileAfterSaveAs);
        mapModelProvider.setOpenMapModels(Collections.singletonList(mapAtOriginalFileAfterSaveAs));
        uut.onCreate(mapAtOriginalFileAfterSaveAs);
        UUID originalFileIdentifierAfterSaveAs = uut.getCurrentMapIdentifier();
        assertThat(originalFileIdentifierAfterSaveAs).isNotEqualTo(originalIdentifier);
        uut.onRemove(mapAtOriginalFileAfterSaveAs);

        MapModel mapAtSavedAsFile = mapModelForFile(savedAsFile);
        mapModelProvider.setCurrentMapModel(mapAtSavedAsFile);
        mapModelProvider.setOpenMapModels(Collections.singletonList(mapAtSavedAsFile));
        uut.onCreate(mapAtSavedAsFile);

        assertThat(uut.findMapModel(originalIdentifier)).isSameAs(mapAtSavedAsFile);
    }

    @Test
    public void findMapModel_returnsNullWhenIdentifierIsUnknown() {
        AvailableMaps uut = new AvailableMaps(new FakeMapModelProvider());

        assertThat(uut.findMapModel(UUID.randomUUID())).isNull();
    }

    @Test
    public void registerMapIdentifier_returnsMapModelForExplicitIdentifier() {
        MapModel mapModel = mock(MapModel.class);
        FakeMapModelProvider mapModelProvider = new FakeMapModelProvider();
        mapModelProvider.setCurrentMapModel(mapModel);
        mapModelProvider.setOpenMapModels(Collections.singletonList(mapModel));
        AvailableMaps uut = new AvailableMaps(mapModelProvider);
        UUID mapIdentifier = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

        UUID registeredIdentifier = uut.registerMapIdentifier(mapModel, mapIdentifier);

        assertThat(registeredIdentifier).isEqualTo(mapIdentifier);
        assertThat(uut.findMapModel(mapIdentifier)).isSameAs(mapModel);
    }

    @Test
    public void findMapModel_lazyLoadsDocumentationMapForReservedIdentifier() {
        MapModel documentationMapModel = mock(MapModel.class);
        ApiDocumentationMapLoader mapLoader = mock(ApiDocumentationMapLoader.class);
        when(mapLoader.loadInstalledApiMapModel()).thenReturn(documentationMapModel);
        AvailableMaps uut = new AvailableMaps(new FakeMapModelProvider(), mapLoader);

        MapModel firstLookup = uut.findMapModel(AvailableMaps.INTERNAL_API_DOCUMENTATION_MAP_IDENTIFIER);
        MapModel secondLookup = uut.findMapModel(AvailableMaps.INTERNAL_API_DOCUMENTATION_MAP_IDENTIFIER);

        assertThat(firstLookup).isSameAs(documentationMapModel);
        assertThat(secondLookup).isSameAs(documentationMapModel);
        verify(mapLoader).loadInstalledApiMapModel();
    }

    @Test
    public void findMapModel_keepsReservedDocumentationMapAvailable() {
        MapModel normalMapModel = mock(MapModel.class);
        MapModel documentationMapModel = mock(MapModel.class);
        ApiDocumentationMapLoader mapLoader = mock(ApiDocumentationMapLoader.class);
        when(mapLoader.loadInstalledApiMapModel()).thenReturn(documentationMapModel);
        FakeMapModelProvider mapModelProvider = new FakeMapModelProvider();
        mapModelProvider.setCurrentMapModel(normalMapModel);
        mapModelProvider.setOpenMapModels(Collections.singletonList(normalMapModel));
        AvailableMaps uut = new AvailableMaps(mapModelProvider, mapLoader);
        UUID normalMapIdentifier = uut.getCurrentMapIdentifier();
        assertThat(uut.findMapModel(AvailableMaps.INTERNAL_API_DOCUMENTATION_MAP_IDENTIFIER))
            .isSameAs(documentationMapModel);

        uut.onRemove(normalMapModel);
        mapModelProvider.setCurrentMapModel(null);
        mapModelProvider.setOpenMapModels(Collections.emptyList());

        assertThat(uut.findMapModel(normalMapIdentifier)).isNull();
        assertThat(uut.findMapModel(AvailableMaps.INTERNAL_API_DOCUMENTATION_MAP_IDENTIFIER))
            .isSameAs(documentationMapModel);
        verify(mapLoader).loadInstalledApiMapModel();
    }

    private static MapModel mapModelForFile(File file) throws Exception {
        MapModel mapModel = mock(MapModel.class);
        stubMapFile(mapModel, file);
        return mapModel;
    }

    private static void stubMapFile(MapModel mapModel, File file) throws Exception {
        when(mapModel.getURL()).thenReturn(file.toURI().toURL());
    }

    private static class FakeMapModelProvider implements MapModelProvider {
        private MapModel currentMapModel;
        private List<MapModel> openMapModels = new ArrayList<>();

        @Override
        public MapModel getCurrentMapModel() {
            return currentMapModel;
        }

        @Override
        public List<MapModel> getOpenMapModels() {
            return openMapModels;
        }

        @Override
        public NodeModel getCurrentSelectedNodeModel() {
            return null;
        }

        private void setCurrentMapModel(MapModel currentMapModel) {
            this.currentMapModel = currentMapModel;
        }

        private void setOpenMapModels(List<MapModel> openMapModels) {
            this.openMapModels = openMapModels;
        }
    }
}
