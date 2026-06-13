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
import org.freeplane.plugin.ai.tools.documentation.ApiDocumentationMapLoader;

public class AvailableMaps {
    public static final UUID INTERNAL_API_DOCUMENTATION_MAP_IDENTIFIER = new UUID(0L, 1L);

    private final MapModelProvider mapModelProvider;
    private final ApiDocumentationMapLoader apiDocumentationMapLoader;
    private final Map<MapModel, UUID> mapIdentifiersByMapModel = new WeakHashMap<>();
    private final Map<UUID, WeakReference<MapModel>> mapReferencesByIdentifier = new HashMap<>();

    public AvailableMaps(MapModelProvider mapModelProvider) {
        this(mapModelProvider, null);
    }

    public AvailableMaps(MapModelProvider mapModelProvider, ApiDocumentationMapLoader apiDocumentationMapLoader) {
        this.mapModelProvider = Objects.requireNonNull(mapModelProvider, "mapModelProvider");
        this.apiDocumentationMapLoader = apiDocumentationMapLoader;
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
        MapModel mapModel = registeredMapModel(mapIdentifier);
        if (mapModel == null && INTERNAL_API_DOCUMENTATION_MAP_IDENTIFIER.equals(mapIdentifier)) {
            mapModel = loadInternalApiDocumentationMap();
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
        }
        return registerMapIdentifier(mapModel, mapIdentifier);
    }

    public UUID registerMapIdentifier(MapModel mapModel, UUID mapIdentifier) {
        Objects.requireNonNull(mapModel, "mapModel");
        Objects.requireNonNull(mapIdentifier, "mapIdentifier");
        UUID previousIdentifier = mapIdentifiersByMapModel.put(mapModel, mapIdentifier);
        if (previousIdentifier != null && !previousIdentifier.equals(mapIdentifier)) {
            mapReferencesByIdentifier.remove(previousIdentifier);
        }
        WeakReference<MapModel> previousReference = mapReferencesByIdentifier.put(mapIdentifier,
            new WeakReference<>(mapModel));
        MapModel previousMapModel = previousReference == null ? null : previousReference.get();
        if (previousMapModel != null && previousMapModel != mapModel) {
            UUID previousMapIdentifier = mapIdentifiersByMapModel.get(previousMapModel);
            if (mapIdentifier.equals(previousMapIdentifier)) {
                mapIdentifiersByMapModel.remove(previousMapModel);
            }
        }
        return mapIdentifier;
    }

    private MapModel registeredMapModel(UUID mapIdentifier) {
        WeakReference<MapModel> mapReference = mapReferencesByIdentifier.get(mapIdentifier);
        if (mapReference == null) {
            return null;
        }
        MapModel mapModel = mapReference.get();
        if (mapModel == null) {
            mapReferencesByIdentifier.remove(mapIdentifier);
        }
        return mapModel;
    }

    private MapModel loadInternalApiDocumentationMap() {
        if (apiDocumentationMapLoader == null) {
            throw new IllegalStateException("Internal API documentation map loading is not configured.");
        }
        MapModel mapModel = apiDocumentationMapLoader.loadInstalledApiMapModel();
        registerMapIdentifier(mapModel, INTERNAL_API_DOCUMENTATION_MAP_IDENTIFIER);
        return mapModel;
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
