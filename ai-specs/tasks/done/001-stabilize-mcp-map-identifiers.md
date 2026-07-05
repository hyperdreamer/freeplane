# Task: Stabilize MCP map identifiers across map reopen
- **Ticket:** #2920
- **Scope:** Change AI plugin map identifier tracking so a normal map
  identifier is valid only while its map is currently open, and so the
  same saved map URL reuses its identifier after close and reopen within
  the same Freeplane process. Keep identifiers random and process-local;
  do not add deterministic URL UUIDs, persistent `.mm` UUIDs, or any file
  format change.
- **Motivation:** Closing a map can leave its old `MapModel` reachable
  through the MCP map registry. MCP tools can then read and write a
  detached model and report success while the visible reopened map is not
  changed. Reusing the identifier for the same URL after reopen avoids
  forcing clients onto a ghost while keeping application restart behavior
  unchanged.
- **Scenario:** A client obtains `mapIdentifier` for a saved map at URL
  `oldUrl`. While the map is open, tools using `mapIdentifier` address
  the visible map. After the user closes only that map, tools using
  `mapIdentifier` fail with `Unknown map identifier`. If the user
  reopens `oldUrl` in the same Freeplane process, the map-create
  lifecycle callback restores `mapIdentifier` to the reopened visible
  map. If that open map is saved as `newUrl`, later close/reopen
  preserves `mapIdentifier` for `newUrl`, not for `oldUrl`.
- **Constraints:**
  - Keep the existing MCP request/response field shape and existing UUID
    string format.
  - Keep identifiers random and process-local.
  - Do not persist map identifiers in `.mm` files.
  - Do not keep a closed map addressable through its old identifier.
  - Do not treat a permanent `URL -> UUID` map as authoritative while a
    map is open; this would keep old URLs bound after Save As.
  - Preserve the reserved internal API documentation map identifier.
  - Keep unsaved maps identified only by their current `MapModel`; once
    closed, their identifiers are not recoverable by URL.
- **Briefing:** The affected code is in `freeplane_plugin_ai`.
  `AvailableMaps` owns the AI/MCP map identifier registry and is used by
  the tool implementations before they access `MapModel` instances.
  `ControllerMapModelProvider` can list currently open maps from
  `IMapViewManager`. Closing the last view of a map removes it from the
  view manager and fires normal map removal. Save As keeps the same
  `MapModel` and changes its URL. The fix uses map lifecycle callbacks
  to capture and invalidate identifiers at close time, and to restore a
  previously assigned identifier when a map with a matching URL is
  reopened.
- **Research:**

  ```plantuml
  @startuml
  set separator none
  package "org.freeplane.plugin.ai.maps" {
    interface MapModelProvider {
      getCurrentMapModel()
      getOpenMapModels()
      getCurrentSelectedNodeModel()
    }
    class ControllerMapModelProvider {
      getOpenMapModels()
    }
    class AvailableMaps {
      {static} INTERNAL_API_DOCUMENTATION_MAP_IDENTIFIER : UUID
      - mapIdentifiersByMapModel : WeakHashMap<MapModel, UUID>
      - mapReferencesByIdentifier : HashMap<UUID, WeakReference<MapModel>>
      + getCurrentMapIdentifier() : UUID
      + getAvailableMapIdentifiers() : List<UUID>
      + findMapModel(UUID) : MapModel
      + getOrCreateMapIdentifier(MapModel) : UUID
      + registerMapIdentifier(MapModel, UUID) : UUID
    }
    class MapModel {
      getURL() : URL
      getFile() : File
    }
    MapModelProvider <|.. ControllerMapModelProvider
    AvailableMaps --> MapModelProvider
    AvailableMaps --> MapModel
  }
  @enduml
  ```

  - `AvailableMaps.getOrCreateMapIdentifier(...)` currently creates a
    random UUID for a `MapModel` and registers it in
    `mapIdentifiersByMapModel`.
  - `AvailableMaps.findMapModel(...)` currently resolves the reverse
    `WeakReference<MapModel>` and does not check whether the resolved
    model is still open.
  - `AvailableMaps.removeClearedReferences()` removes only reverse
    references whose weak reference has been cleared by GC.
  - `ControllerMapModelProvider.getOpenMapModels()` returns the unique
    `MapModel` instances from `Controller.getCurrentController()` and
    `IMapViewManager.getMaps().values()`.
  - `MapViewController.close(...)` calls
    `MapController.closeWithoutSaving(map)` when the last view of a map
    is closed.
  - `MapController.closeWithoutSaving(...)` fires map removal and calls
    `map.releaseResources()`.
  - `Activator` can create one shared production `AvailableMaps`
    instance, register it as a map lifecycle listener on the
    `MMapController`, and inject it into chat and MCP tool-set builders.
  - `MFileManager.save(map, file)` records `urlBefore`, calls
    `setFile(map, file)`, writes the file, and fires a
    `UrlManager.MAP_URL` `MapChangeEvent` after successful Save As when
    the URL changed.
  - Existing `AvailableMapsTest` covers stable identifiers for the same
    `MapModel`, explicit registration, unknown identifiers, available
    map listing, and lazy loading of the reserved API documentation map.
- **Analysis:**
  - Use an in-process closed-map URL cache because clients need reopen
    stability while Freeplane stays open, not cross-application-restart
    identity.
  - Cache URL identifiers only for closed maps so that a Save As changes
    which URL inherits the current map identifier.
  - Capture closed-map identifiers in a lifecycle `onRemove` callback
    because lazy pruning cannot guarantee the closed `MapModel` still
    exists in weak-reference-backed registries.
  - Rebind a reopened map in lifecycle `onCreate` only if its URL has a
    cached closed-map identifier. Do not create new identifiers in
    `onCreate`.
  - Save As before any client request after reopen is handled because
    the reopened `MapModel` already has its restored identifier before
    its URL changes.
  - Do not keep a secondary lazy closed-map discovery path; lifecycle
    callbacks are the authoritative close/reopen signals.
- **Design:**

  ```plantuml
  @startuml
  set separator none
  package "org.freeplane.plugin.ai.maps" {
    interface IMapLifeCycleListener {
      onCreate(MapModel) : void
      onRemove(MapModel) : void
    }
    interface MapModelProvider {
      getCurrentMapModel()
      getOpenMapModels()
      getCurrentSelectedNodeModel()
    }
    class AvailableMaps {
      {static} INTERNAL_API_DOCUMENTATION_MAP_IDENTIFIER : UUID
      - mapIdentifiersByMapModel : WeakHashMap<MapModel, UUID>
      - mapReferencesByIdentifier : HashMap<UUID, WeakReference<MapModel>>
      - closedMapIdentifiersByUrlKey : HashMap<String, UUID>
      + onCreate(MapModel) : void
      + onRemove(MapModel) : void
      + getCurrentMapIdentifier() : UUID
      + getAvailableMapIdentifiers() : List<UUID>
      + findMapModel(UUID) : MapModel
      + getOrCreateMapIdentifier(MapModel) : UUID
      + registerMapIdentifier(MapModel, UUID) : UUID
      - removeClosedMapIdentifier(MapModel) : UUID
      - rememberClosedMapIdentifier(MapModel, UUID) : void
      - getUrlKey(MapModel) : String
      - removeMapReference(UUID, MapModel) : void
    }
    class AIToolSetBuilder {
      AIToolSetBuilder(AvailableMaps)
    }
    class Activator {
      installExtension(ModeController)
    }
    class MapModel {
      getURL() : URL
    }
    IMapLifeCycleListener <|.. AvailableMaps
    AvailableMaps --> MapModelProvider
    AvailableMaps --> MapModel
    Activator --> AvailableMaps
    Activator --> AIToolSetBuilder
    AIToolSetBuilder --> AvailableMaps
  }
  @enduml
  ```

  ```plantuml
  @startuml
  participant "MMapController" as Controller
  participant "AvailableMaps" as Maps
  participant "closedMapIdentifiersByUrlKey" as UrlCache
  participant "MCP tool" as Tool

  Controller -> Maps: onRemove(map at oldUrl)
  Maps -> UrlCache: put(oldUrl, mapIdentifier)
  Maps -> Maps: remove MapModel and reverse references
  Tool -> Maps: findMapModel(mapIdentifier)
  Maps --> Tool: null

  Controller -> Maps: onCreate(reopened map at oldUrl)
  Maps -> UrlCache: remove(oldUrl)
  UrlCache --> Maps: mapIdentifier
  Maps -> Maps: register reopened MapModel with mapIdentifier
  Tool -> Maps: findMapModel(mapIdentifier)
  Maps --> Tool: reopened MapModel
  @enduml
  ```

  `AvailableMaps` will add `closedMapIdentifiersByUrlKey`, keyed by
  `map.getURL().toExternalForm()`. A null URL produces no key.

  `AvailableMaps` will implement `IMapLifeCycleListener` for close and
  reopen handling. `Activator` will create one shared production
  `AvailableMaps`, register it once with the `MMapController`, pass it
  to `AIChatPanel`, and pass it to the MCP `AIToolSetBuilder`.
  `AIToolSetBuilder` will require `AvailableMaps` as a constructor
  dependency and will not create or register map registries.

  `onRemove(MapModel)` will cache `getUrlKey(map) -> identifier` when
  the map has a non-reserved identifier and a URL key, then remove both
  the `MapModel -> UUID` entry and the matching reverse reference. The
  old identifier is therefore unknown while the map is closed.

  `onCreate(MapModel)` will consume a cached identifier only when the
  reopened map URL exists in `closedMapIdentifiersByUrlKey`. It will not
  create a new UUID for unrelated newly opened maps.

  `findMapModel(UUID)` will only resolve live reverse references, except
  for the reserved internal API documentation map identifier.

  `getOrCreateMapIdentifier(MapModel)` will first reuse an existing
  registered identifier for the same open model. If none exists and the
  map has a URL key, it will remove and reuse the cached identifier from
  `closedMapIdentifiersByUrlKey`. If no cached identifier exists, it
  will create a random UUID as today.

  Save As before any client request after reopen is handled by
  `onCreate`: the reopened `MapModel` already has its restored
  identifier before its URL changes, so later `onRemove` caches that
  identifier under the new URL.

  Unsaved maps have no URL key. They keep their existing identifier while
  open and become unknown after close.
- **Test specification:**
  - **Automated tests:**
    - `AvailableMapsTest`
      - `findMapModel_returnsNullForClosedMapIdentifier`: a lifecycle
        close removes the reverse reference, so the old identifier is not
        addressable while the map is closed.
      - `onCreate_reusesIdentifierForReopenedUrl`: after lifecycle close
        and reopen for a saved map, `onCreate` restores the old
        identifier to the reopened `MapModel`.
      - `getCurrentMapIdentifier_consumesClosedUrlIdentifier`: after a
        saved map is reopened and then saved under a different URL, the
        original URL no longer receives the consumed identifier.
      - `onCreate_preservesIdentifierWhenReopenedMapIsSavedAsBeforeIdentifierRequest`:
        Save As after reopen but before any MCP identifier request keeps
        the restored identifier with the reopened `MapModel`.
      - `findMapModel_keepsReservedDocumentationMapAvailable`: lifecycle
        removal of normal maps does not remove or reload the reserved
        internal API documentation map.
    - `AIToolSetBuilderTest`
      - builder tests construct tool sets with an injected
        `AvailableMaps`; the builder no longer creates or registers map
        registries.
