package org.freeplane.plugin.ai.maps;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;

public class AvailableMaps {
    private final MapModelProvider mapModelProvider;
    private final Map<MapModel, UUID> mapIdentifiersByMapModel = new WeakHashMap<>();
    private final Map<UUID, WeakReference<MapModel>> mapReferencesByIdentifier = new HashMap<>();
    public AvailableMaps(MapModelProvider mapModelProvider) {
        this.mapModelProvider = Objects.requireNonNull(mapModelProvider, "mapModelProvider");
    }

    public UUID getCurrentMapIdentifier() {
        MapModel mapModel = mapModelProvider.getCurrentMapModel();
        if (mapModel == null) {
            return null;
        }
        return getOrCreateMapIdentifier(mapModel);
    }

    public MapModel getCurrentMapModel() {
        return mapModelProvider.getCurrentMapModel();
    }

    public NodeModel getCurrentSelectedNodeModel() {
        return mapModelProvider.getCurrentSelectedNodeModel();
    }

    public List<UUID> getAvailableMapIdentifiers() {
        List<MapModel> mapModels = mapModelProvider.getOpenMapModels();
        List<UUID> mapIdentifiers = new ArrayList<>();
        if (mapModels == null || mapModels.isEmpty()) {
            removeClearedReferences();
            return mapIdentifiers;
        }
        for (MapModel mapModel : mapModels) {
            if (mapModel == null) {
                continue;
            }
            mapIdentifiers.add(getOrCreateMapIdentifier(mapModel));
        }
        removeClearedReferences();
        return mapIdentifiers;
    }

    public MapModel findMapModel(UUID mapIdentifier) {
        return findMapModel(mapIdentifier, null);
    }

    public MapModel findMapModel(UUID mapIdentifier, MapAccessListener mapAccessListener) {
        if (mapIdentifier == null) {
            return null;
        }
        WeakReference<MapModel> mapReference = mapReferencesByIdentifier.get(mapIdentifier);
        if (mapReference == null) {
            return null;
        }
        MapModel mapModel = mapReference.get();
        if (mapModel == null) {
            mapReferencesByIdentifier.remove(mapIdentifier);
        }
        if (mapModel != null && mapAccessListener != null) {
            mapAccessListener.onMapAccessed(mapIdentifier, mapModel);
        }
        return mapModel;
    }

    public UUID getOrCreateMapIdentifier(MapModel mapModel) {
        Objects.requireNonNull(mapModel, "mapModel");
        UUID mapIdentifier = mapIdentifiersByMapModel.get(mapModel);
        if (mapIdentifier == null) {
            mapIdentifier = UUID.randomUUID();
            mapIdentifiersByMapModel.put(mapModel, mapIdentifier);
        }
        WeakReference<MapModel> mapReference = mapReferencesByIdentifier.get(mapIdentifier);
        MapModel referencedMapModel = mapReference == null ? null : mapReference.get();
        if (referencedMapModel != mapModel) {
            mapReferencesByIdentifier.put(mapIdentifier, new WeakReference<>(mapModel));
        }
        return mapIdentifier;
    }


    private void removeClearedReferences() {
        Iterator<Map.Entry<UUID, WeakReference<MapModel>>> iterator = mapReferencesByIdentifier.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, WeakReference<MapModel>> entry = iterator.next();
            WeakReference<MapModel> mapReference = entry.getValue();
            if (mapReference.get() == null) {
                iterator.remove();
            }
        }
    }

    public interface MapAccessListener {
        void onMapAccessed(UUID mapIdentifier, MapModel mapModel);
    }
}
