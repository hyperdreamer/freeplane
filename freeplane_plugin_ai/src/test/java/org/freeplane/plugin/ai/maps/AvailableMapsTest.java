package org.freeplane.plugin.ai.maps;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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
    public void findMapModel_returnsNullWhenIdentifierIsUnknown() {
        AvailableMaps uut = new AvailableMaps(new FakeMapModelProvider());

        assertThat(uut.findMapModel(UUID.randomUUID())).isNull();
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
