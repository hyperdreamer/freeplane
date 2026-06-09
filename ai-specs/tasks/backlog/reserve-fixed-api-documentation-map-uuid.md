# Task: Reserve a fixed API documentation map UUID
- **Task Identifier:** 2026-06-09-api-map-uuid
- **Scope:** Make `getApiDocumentation()` return the reserved API
  documentation map UUID `new UUID(0L, 1L)`, make read/search access
  to that UUID lazy-load the installed documentation map when needed,
  and reject editing or scripting access for that UUID.
- **Motivation:** The documentation map currently behaves like a
  hidden session map with a random identifier. A stable reserved UUID
  should let tools and authorizers refer to the internal API map
  without first discovering or registering a session-specific value,
  while keeping the documentation map read-only.
- **Scenario:**
  - The **internal API documentation map** is the installed
    `doc/api/freeplane-api.mm` map exposed through
    `getApiDocumentation()`.
  - The internal API documentation map always uses the reserved UUID
    `00000000-0000-0000-0000-000000000001`.
  - When a read/search tool receives that UUID and the documentation
    map is not yet loaded, the tool resolves it by loading and
    registering the map transparently and then continues.
  - Any request that would edit that map or use it as a scripting
    target is rejected explicitly.
- **Constraints:**
  - Keep ordinary user maps on the existing per-session
    `AvailableMaps` identifier flow.
  - Do not keep a random-UUID compatibility fallback for the
    documentation map.
  - The reserved UUID must stay globally stable across tool calls and
    sessions.
  - Hidden loading must still work when the documentation map has no
    open view.
  - Read/search access may resolve the reserved UUID, but mutation and
    script-targeting flows must reject it before changing state.
- **Briefing:** Relevant files are
  `freeplane_plugin_ai/src/main/java/org/freeplane/plugin/ai/maps/AvailableMaps.java`,
  `freeplane_plugin_ai/src/main/java/org/freeplane/plugin/ai/tools/documentation/GetApiDocumentationTool.java`,
  `freeplane_plugin_ai/src/main/java/org/freeplane/plugin/ai/tools/documentation/ApiDocumentationMapLoader.java`,
  `freeplane_plugin_ai/src/main/java/org/freeplane/plugin/ai/mcpserver/ModelContextProtocolToolCallAuthorizer.java`,
  map-targeting read tools such as
  `freeplane_plugin_ai/src/main/java/org/freeplane/plugin/ai/tools/read/ReadNodesWithDescendantsTool.java`
  and
  `freeplane_plugin_ai/src/main/java/org/freeplane/plugin/ai/tools/search/SearchNodesTool.java`,
  and tests around `GetApiDocumentationTool`, `AvailableMaps`, and MCP
  authorization.
- **Research:**
  - `GetApiDocumentationTool.getApiDocumentation()` currently
    hidden-loads the installed documentation map and returns whatever
    UUID `AvailableMaps.getOrCreateMapIdentifier(mapModel)` generates
    for that `MapModel`.
  - `AvailableMaps` currently resolves only maps already registered in
    its weak maps; it has no reserved-UUID branch and no loader hook
    for the documentation map.
  - Map-targeting tools currently parse `request.mapIdentifier`, call
    `availableMaps.findMapModel(...)`, and fail with `Unknown map
    identifier: ...` when the UUID is not already registered.
  - `ModelContextProtocolToolCallAuthorizer` currently learns the
    documentation-map identifier by calling `getApiDocumentation()`
    and comparing `request.mapIdentifier` to that returned value for
    the MCP `DISABLED` documentation-only allowlist.
  - Nothing in the current map-resolution path reserves the
    documentation map as read-only once its UUID is known.
  - `AiCodeOperationAuthorizer` currently authorizes code-host
    operations only by availability level and host; it does not know
    about any map UUID.
- **Analysis:**
  - Reserve `new UUID(0L, 1L)` for the internal API documentation map
    because callers and authorizers need a stable identifier
    independent of session-local registration.
  - Resolve the reserved UUID lazily because documentation read/search
    calls should work even before explicit discovery or any visible
    map opening.
  - Treat the reserved UUID as read-only because the documentation map
    is a generated internal artifact, not user-editable working data.
- **Design:**
  - Introduce one central reserved-UUID resolution path for the
    internal API documentation map so callers do not need ad-hoc
    `getApiDocumentation()` side effects just to resolve that map.
  - Make `getApiDocumentation()` return the reserved UUID string for
    `new UUID(0L, 1L)` instead of a random per-map registration value.
  - When the shared map-resolution path receives that UUID and no live
    map is registered for it, load the installed documentation map
    through `ApiDocumentationMapLoader`, register it under the
    reserved UUID, and return it.
  - Keep the lazy-load path read-only by default: read/search tools
    may resolve the reserved UUID, but mutation tools and any
    scripting-target path that can still reach that map must reject it
    with explicit errors instead of operating on the loaded map.
  - Simplify MCP documentation-map authorization to compare against
    the fixed reserved UUID instead of forcing a discovery call just
    to recover a session-specific random UUID.
- **Test specification:**
  - **Automated tests:**
    - `gradle :freeplane_plugin_ai:test -Djava.net.preferIPv6Addresses=true -Djava.awt.headless=true`
    - Add focused tests for:
      - `getApiDocumentation()` returning
        `00000000-0000-0000-0000-000000000001`
      - lazy resolution of the reserved UUID when the documentation
        map was not yet loaded
      - reusing the same loaded documentation `MapModel` after the
        first lazy resolution
      - rejecting mutation-tool requests that target the reserved UUID
      - rejecting any scripting-target path that can still operate on
        the reserved UUID
      - MCP `DISABLED` documentation access still working with the
        fixed UUID and no discovery-time random registration
  - **Manual tests:** N/A
