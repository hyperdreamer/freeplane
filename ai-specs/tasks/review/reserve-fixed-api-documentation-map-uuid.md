# Task: Reserve a fixed API documentation map UUID
- **Task Identifier:** 2026-06-09-api-map-uuid
- **Scope:** Introduce a stable reserved UUID for the internal API
  documentation map and make shared map lookup resolve that UUID
  automatically in any map-targeting tool call. Leave editing and
  scripting treatment for a later separately tracked subtask.
- **Motivation:** The documentation map currently behaves like a
  hidden session map with a random identifier. A stable reserved UUID
  should let tools and authorizers refer to the internal API map
  without first discovering or registering a session-specific value,
  and the shared lookup path should make that UUID usable
  immediately.
- **Constraints:**
  - Keep ordinary user maps on the existing per-session
    `AvailableMaps` identifier flow.
  - Do not keep a random-UUID compatibility fallback for the
    documentation map.
  - The reserved UUID must stay globally stable across tool calls and
    sessions.
  - Keep the first subtask limited to identity and map resolution.
    Do not design editing or scripting policy there.
- **Briefing:** Relevant files are
  `freeplane_plugin_ai/src/main/java/org/freeplane/plugin/ai/maps/AvailableMaps.java`,
  `freeplane_plugin_ai/src/main/java/org/freeplane/plugin/ai/tools/documentation/GetApiDocumentationTool.java`,
  `freeplane_plugin_ai/src/main/java/org/freeplane/plugin/ai/tools/documentation/ApiDocumentationMapLoader.java`,
  `freeplane_plugin_ai/src/main/java/org/freeplane/plugin/ai/tools/AIToolSetBuilder.java`,
  `freeplane_plugin_ai/src/main/java/org/freeplane/plugin/ai/mcpserver/ModelContextProtocolToolCallAuthorizer.java`,
  and representative map-targeting tools such as
  `freeplane_plugin_ai/src/main/java/org/freeplane/plugin/ai/tools/read/ReadNodesWithDescendantsTool.java`
  and
  `freeplane_plugin_ai/src/main/java/org/freeplane/plugin/ai/tools/search/SearchNodesTool.java`.
- **Research:**
  - `GetApiDocumentationTool.getApiDocumentation()` currently
    hidden-loads the installed documentation map and returns whatever
    UUID `AvailableMaps.getOrCreateMapIdentifier(mapModel)` generates
    for that `MapModel`.
  - `AvailableMaps` currently supports random per-session identifier
    assignment for ordinary maps plus lookup of already-registered
    maps. It does not currently special-case the documentation map.
  - Current map-targeting tools converge on
    `AvailableMaps.findMapModel(...)` for map resolution. That shared
    lookup path is therefore the natural place to make the reserved
    UUID usable in arbitrary tool calls.
  - `ModelContextProtocolToolCallAuthorizer` currently learns the
    documentation-map identifier by calling `getApiDocumentation()`
    and comparing `request.mapIdentifier` to that returned value for
    the MCP `DISABLED` documentation-only allowlist.

## Subtask: Reserve a fixed UUID and resolve it through AvailableMaps
- **Status:** review
- **Scope:** Make `getApiDocumentation()` return reserved UUID
  `new UUID(0L, 1L)`, and make `AvailableMaps.findMapModel(...)`
  automatically load and register the documentation map when that UUID
  is used in any map-targeting tool call.
- **Motivation:** A fixed UUID is incomplete if it still requires a
  separate discovery call before ordinary tool calls can use it.
  Central automatic resolution is the minimum coherent behavior for
  the fixed-UUID increment.
- **Scenario:**
  - The **internal API documentation map** is the installed
    `doc/api/freeplane-api.mm` map exposed through
    `getApiDocumentation()`.
  - Calling `getApiDocumentation()` always returns map identifier
    `00000000-0000-0000-0000-000000000001`.
  - When any tool call supplies that UUID as `mapIdentifier`,
    `AvailableMaps.findMapModel(...)` loads and registers the
    documentation map on demand if it is not already registered, then
    returns that `MapModel`.
  - MCP disabled-mode documentation access can compare against the
    same fixed UUID without making a discovery call just to recover a
    session-local random identifier.
- **Constraints:**
  - Keep the automatic-loading change centralized in
    `AvailableMaps.findMapModel(...)` instead of duplicating it across
    tools.
  - Keep ordinary user maps on the existing `AvailableMaps`
    random-UUID flow.
  - Do not add editing or scripting policy in this subtask.
- **Briefing:** Relevant files for this increment are
  `freeplane_plugin_ai/src/main/java/org/freeplane/plugin/ai/maps/AvailableMaps.java`,
  `freeplane_plugin_ai/src/main/java/org/freeplane/plugin/ai/tools/documentation/GetApiDocumentationTool.java`,
  `freeplane_plugin_ai/src/main/java/org/freeplane/plugin/ai/tools/documentation/ApiDocumentationMapLoader.java`,
  `freeplane_plugin_ai/src/main/java/org/freeplane/plugin/ai/tools/AIToolSetBuilder.java`,
  and
  `freeplane_plugin_ai/src/main/java/org/freeplane/plugin/ai/mcpserver/ModelContextProtocolToolCallAuthorizer.java`.
- **Research:**
  - No existing `AvailableMaps` API allows explicit registration of a
    caller-chosen UUID for a loaded `MapModel`.
  - `ApiDocumentationMapLoader` already encapsulates installed-file
    lookup and hidden documentation-map loading.
  - `GetApiDocumentationTool` still needs
    `ApiDocumentationMapLoader` directly because it also validates and
    reports structure against the installed documentation map file.
- **Analysis:**
  - The fixed UUID and automatic shared lookup belong in the same
    increment because a stable identifier that only works after a
    separate discovery call is an incomplete contract.
  - `AvailableMaps` should own the documentation-map UUID and the
    special-case shared lookup behavior because callers already use
    `AvailableMaps` as the map-resolution entry point.
  - There is no need to introduce a separate policy or indirection
    layer in this subtask. The first increment is only about identity
    and resolution.
- **Design:**

  ```plantuml
  @startuml
  set separator none

  package "org.freeplane.plugin.ai" {
    package "maps" {
      class AvailableMaps {
        {static} +UUID INTERNAL_API_DOCUMENTATION_MAP_IDENTIFIER
        +UUID getOrCreateMapIdentifier(MapModel)
        +UUID registerMapIdentifier(MapModel, UUID)
        +MapModel findMapModel(UUID, MapAccessListener)
      }
    }

    package "tools" {
      class AIToolSetBuilder {
        -AvailableMaps createAvailableMaps(MMapController)
      }

      package "documentation" {
        class GetApiDocumentationTool {
          +GetApiDocumentationResponse getApiDocumentation()
        }

        class ApiDocumentationMapLoader {
          +LoadedApiDocumentationMap loadInstalledApiMap()
          +MapModel loadInstalledApiMapModel()
        }
      }

      package "read" {
        class ReadNodesWithDescendantsTool
      }

      package "search" {
        class SearchNodesTool
      }

      package "create" {
        class CreateNodesTool
      }
    }

    package "mcpserver" {
      class ModelContextProtocolToolCallAuthorizer {
        +void assertAuthorized(String, JsonNode)
      }
    }
  }

  AIToolSetBuilder --> AvailableMaps : construct with loader
  AvailableMaps --> ApiDocumentationMapLoader : lazy-load reserved UUID
  GetApiDocumentationTool --> ApiDocumentationMapLoader : load installed map
  GetApiDocumentationTool --> AvailableMaps : register fixed UUID
  ReadNodesWithDescendantsTool --> AvailableMaps : findMapModel(...)
  SearchNodesTool --> AvailableMaps : findMapModel(...)
  CreateNodesTool --> AvailableMaps : findMapModel(...)
  ModelContextProtocolToolCallAuthorizer --> AvailableMaps : compare fixed UUID
  @enduml
  ```

  - Add `AvailableMaps.INTERNAL_API_DOCUMENTATION_MAP_IDENTIFIER =
    new UUID(0L, 1L)` as the stable public identifier for the internal
    API documentation map.
  - Add `AvailableMaps.registerMapIdentifier(MapModel, UUID)` so code
    that already owns a specific identifier can register a loaded map
    under that UUID without changing the ordinary random-UUID flow for
    other maps.
  - Construct `AvailableMaps` with a direct
    `ApiDocumentationMapLoader` dependency from `AIToolSetBuilder`.
  - Change `GetApiDocumentationTool.getApiDocumentation()` to:
    - return the reserved UUID string; and
    - ensure the loaded documentation map is registered under that UUID
      before returning.
  - Change `ModelContextProtocolToolCallAuthorizer` to compare
    incoming `mapIdentifier` values against
    `AvailableMaps.INTERNAL_API_DOCUMENTATION_MAP_IDENTIFIER.toString()`
    instead of calling `getApiDocumentation()` only to recover a
    session-specific identifier.

  ```plantuml
  @startuml
  participant "Map-targeting tool" as Tool
  participant "AvailableMaps" as Maps
  participant "ApiDocumentationMapLoader" as Loader
  participant "MapAccessListener" as Listener

  Tool -> Maps : findMapModel(request.mapIdentifier, listener)
  alt request.mapIdentifier == INTERNAL_API_DOCUMENTATION_MAP_IDENTIFIER
    alt documentation map not yet registered
      Maps -> Loader : loadInstalledApiMapModel()
      Loader --> Maps : documentation MapModel
      Maps -> Maps : registerMapIdentifier(mapModel, reservedUuid)
    end
    Maps -> Listener : onMapAccessed(reservedUuid, mapModel)
    Maps --> Tool : documentation MapModel
  else ordinary map identifier
    Maps -> Listener : onMapAccessed(request.mapIdentifier, mapModel)
    Maps --> Tool : registered MapModel or null
  end
  @enduml
  ```

  - Change `AvailableMaps.findMapModel(UUID, MapAccessListener)` so it
    keeps ordinary lookup behavior for ordinary UUIDs, but when the
    requested UUID equals
    `INTERNAL_API_DOCUMENTATION_MAP_IDENTIFIER` and no live map is
    currently registered, it:
    - calls `ApiDocumentationMapLoader.loadInstalledApiMapModel()`;
    - registers the returned `MapModel` under the reserved UUID; and
    - returns that map through the existing lookup path.
- **Test specification:**
  - **Automated tests:**
    - `gradle :freeplane_plugin_ai:test -Djava.net.preferIPv6Addresses=true -Djava.awt.headless=true`
    - Add focused tests for:
      - `getApiDocumentation()` returning
        `00000000-0000-0000-0000-000000000001`
      - the loaded documentation map being registered under that UUID
        after `getApiDocumentation()`
      - `AvailableMaps.findMapModel(...)` lazy-loading and registering
        the documentation map when called with the reserved UUID before
        prior discovery
      - reusing the already-registered documentation `MapModel` on a
        second lookup by the reserved UUID
      - a representative map-targeting tool call such as
        `searchNodes(...)` or `readNodesWithDescendants(...)` working
        with the reserved UUID without a prior `getApiDocumentation()`
        call
      - MCP `DISABLED` documentation access matching the fixed UUID
        without discovery-time random registration
      - `AvailableMaps.registerMapIdentifier(MapModel, UUID)`
        preserving generic explicit-registration behavior
  - **Manual tests:** N/A

## Subtask: Reject editing and scripting targets for the documentation map
- **Status:** review
- **Scope:** Reject documentation-map targets immediately for
  map-targeting editing tools and map-targeting scripting/formula
  tools after the shared reserved-UUID resolution path exists.
- **Motivation:** Once the documentation UUID becomes usable in any
  map-targeting tool call, mutation and scripting-target operations
  need an explicit and consistent stop path instead of falling through
  into normal map handling.
- **Scenario:**
  - The **internal API documentation map** uses public identifier
    `00000000-0000-0000-0000-000000000001`.
  - When an MCP client targets that UUID with a map-targeting editing
    or scripting/formula tool call, MCP authorization rejects the call
    before tool execution.
  - When any compatible editing or scripting/formula tool is invoked
    through another path, the tool still rejects the documentation-map
    UUID locally before changing state.
- **Constraints:**
  - Do not add a new policy class for this subtask.
  - Keep the documentation UUID public on `AvailableMaps`.
  - Do not route documentation-map policy through
    `AiCodeOperationAuthorizer.assertAuthorized(String, ScriptHost)`;
    that API has no `mapIdentifier`.
  - Keep immediate MCP rejection and local tool-side rejection in sync.
- **Briefing:** Relevant files are
  `freeplane_plugin_ai/src/main/java/org/freeplane/plugin/ai/maps/AvailableMaps.java`,
  `freeplane_plugin_ai/src/main/java/org/freeplane/plugin/ai/tools/MapTargetToolCallAuthorizer.java`,
  `freeplane_plugin_ai/src/main/java/org/freeplane/plugin/ai/tools/AIToolSet.java`,
  `freeplane_plugin_ai/src/main/java/org/freeplane/plugin/ai/mcpserver/ModelContextProtocolToolCallAuthorizer.java`,
  `freeplane_plugin_ai/src/main/java/org/freeplane/plugin/ai/tools/code/AiCodeOperationAuthorizer.java`,
  map-targeting editing tools such as
  `freeplane_plugin_ai/src/main/java/org/freeplane/plugin/ai/tools/create/CreateNodesTool.java`,
  `freeplane_plugin_ai/src/main/java/org/freeplane/plugin/ai/tools/edit/BatchEditTool.java`,
  `freeplane_plugin_ai/src/main/java/org/freeplane/plugin/ai/tools/delete/DeleteNodesTool.java`,
  `freeplane_plugin_ai/src/main/java/org/freeplane/plugin/ai/tools/move/MoveNodesTool.java`,
  `freeplane_plugin_ai/src/main/java/org/freeplane/plugin/ai/tools/move/MoveNodesIntoSummaryTool.java`,
  `freeplane_plugin_ai/src/main/java/org/freeplane/plugin/ai/tools/move/CreateSummaryTool.java`,
  `freeplane_plugin_ai/src/main/java/org/freeplane/plugin/ai/tools/connectors/ConnectorEditTool.java`,
  `freeplane_plugin_ai/src/main/java/org/freeplane/plugin/ai/tools/tagcategories/EditTagCategoriesTool.java`,
  and map-targeting scripting/formula tools such as
  `freeplane_plugin_ai/src/main/java/org/freeplane/plugin/ai/tools/formula/FormulaUpdateTool.java`.
- **Research:**
  - `ModelContextProtocolToolCallAuthorizer.assertAuthorized(String,
    JsonNode)` already sees both the tool name and request JSON, so it
    can extract `request.mapIdentifier` for map-targeting MCP calls.
  - `AIToolSet` is the ordinary non-MCP tool-call entry point. Its
    public tool methods such as `createNodes(...)`, `edit(...)`, and
    `previewFormulaUpdates(...)` receive typed requests that already
    carry `mapIdentifier`.
  - `AiCodeOperationAuthorizer.assertAuthorized(String, ScriptHost)`
    only sees the operation and `ScriptHost`; it has no
    `mapIdentifier`, so it cannot own documentation-map target policy.
- **Analysis:**
  - The documentation UUID is public, so no extra identity-ownership
    class is needed for policy.
  - The restriction is not MCP-specific, so the policy should not live
    only in `ModelContextProtocolToolCallAuthorizer`.
  - `AvailableMaps` should keep owning the public UUID constant, but
    policy helpers do not belong there.
  - One shared map-target tool-call authorizer should own this
    restriction and be invoked from both MCP authorization and the
    ordinary `AIToolSet` entry points.
  - `AiCodeOperationAuthorizer` remains unrelated because its API has
    no `mapIdentifier`.
- **Design:**

  ```plantuml
  @startuml
  set separator none

  package "org.freeplane.plugin.ai" {
    package "maps" {
      class AvailableMaps {
        {static} +UUID INTERNAL_API_DOCUMENTATION_MAP_IDENTIFIER
      }
    }

    package "tools" {
      class AIToolSet {
        +CreateNodesResponse createNodes(...)
        +List<EditResultItem> edit(...)
        +DeleteNodesResponse deleteNodes(...)
        +MoveNodesResponse moveNodes(...)
        +CreateSummaryResponse createSummary(...)
        +FormulaUpdatePreviewResponse previewFormulaUpdates(...)
        +FormulaUpdateApplyResponse applyFormulaUpdates(...)
      }

      class MapTargetToolCallAuthorizer {
        +void assertAuthorized(String, String)
      }

      package "code" {
        class AiCodeOperationAuthorizer {
          +void assertAuthorized(String, ScriptHost)
        }
      }
    }

    package "mcpserver" {
      class ModelContextProtocolToolCallAuthorizer {
        +void assertAuthorized(String, JsonNode)
      }
    }
  }

  MapTargetToolCallAuthorizer --> AvailableMaps : compare public UUID
  AIToolSet --> MapTargetToolCallAuthorizer : map-target tool calls
  ModelContextProtocolToolCallAuthorizer --> MapTargetToolCallAuthorizer : MCP pre-dispatch check
  ModelContextProtocolToolCallAuthorizer --> AiCodeOperationAuthorizer : code tools only
  @enduml
  ```

  - Keep `AvailableMaps.INTERNAL_API_DOCUMENTATION_MAP_IDENTIFIER` as
    the one public documentation-map identifier.
  - Add a shared `MapTargetToolCallAuthorizer` that owns only this
    restriction:
    - it knows which tool names are map-targeting editing tools;
    - it knows which tool names are map-targeting
      scripting/formula-target tools;
    - it compares a provided `mapIdentifier` against
      `AvailableMaps.INTERNAL_API_DOCUMENTATION_MAP_IDENTIFIER`; and
    - it throws the correct editing or scripting rejection message
      when the UUID matches.
  - Give `MapTargetToolCallAuthorizer` a narrow API:
    - `assertAuthorized(String toolName, String mapIdentifier)`
  - Do not route this through
    `AiCodeOperationAuthorizer.assertAuthorized(String, ScriptHost)`.

  ```plantuml
  @startuml
  participant "MCP client" as MCP
  participant "ModelContextProtocolToolCallAuthorizer" as McpAuth
  participant "MapTargetToolCallAuthorizer" as ToolAuth
  participant "AIToolSet" as ToolSet

  MCP -> McpAuth : assertAuthorized(toolName, requestJson)
  McpAuth -> ToolAuth : assertAuthorized(toolName, request.mapIdentifier)
  alt documentation UUID for editing or scripting/formula tool
    ToolAuth --> MCP : reject before dispatch
  else allowed target
    McpAuth --> ToolSet : dispatch tool call
    ToolSet -> ToolAuth : assertAuthorized(toolName, request.mapIdentifier)
    ToolAuth --> ToolSet : allowed
    ToolSet --> ToolSet : invoke underlying tool
  end
  @enduml
  ```

  - Update `ModelContextProtocolToolCallAuthorizer.assertAuthorized`
    to:
    - keep its current availability, formula-permission, and code-host
      authorization responsibilities; and
    - delegate documentation-map tool-target restriction to
      `MapTargetToolCallAuthorizer` after extracting
      `request.mapIdentifier` from the request JSON.
  - Update `AIToolSet` so each public map-targeting editing or
    scripting/formula tool method calls
    `MapTargetToolCallAuthorizer.assertAuthorized(toolName,
    request.getMapIdentifier())` before invoking the underlying tool.
  - Use the same rejection wording on both entry paths because they
    are invoking the same shared authorizer.
- **Test specification:**
  - **Automated tests:**
    - `gradle :freeplane_plugin_ai:test -Djava.net.preferIPv6Addresses=true -Djava.awt.headless=true`
    - Add focused tests for:
      - MCP authorization rejecting documentation-map targets for a
        representative editing tool call
      - MCP authorization rejecting documentation-map targets for a
        representative formula/scripting-target tool call
      - MCP authorization continuing to allow documentation-map reads
        where allowed by availability
      - `MapTargetToolCallAuthorizer.assertAuthorized(...)`
        rejecting the documentation UUID for a representative editing
        tool name and allowing an ordinary UUID
      - `MapTargetToolCallAuthorizer.assertAuthorized(...)`
        rejecting the documentation UUID for a representative
        formula/scripting tool name and allowing an ordinary UUID
      - `ModelContextProtocolToolCallAuthorizer` delegating the
        documentation-map restriction to
        `MapTargetToolCallAuthorizer`
      - `AIToolSet` delegating representative map-targeting editing
        and formula tool calls to `MapTargetToolCallAuthorizer`
      - `AiCodeOperationAuthorizer` remaining unchanged with respect to
        map-target policy because it still has no `mapIdentifier`
  - **Manual tests:** N/A
