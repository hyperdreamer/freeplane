# Task: Capture the current map as an AI-tool image
- **Task Identifier:** 2026-07-17-screenshot
- **Scope:**
  Add a shared AI read tool that captures the current Freeplane map as
  either the complete rendered map or the map content visible in its
  viewport and returns PNG image content. Expose the same capture modes
  and behavior through Freeplane's LangChain chat and MCP. Keep explicit
  map-view or component targeting, other Freeplane UI, and arbitrary
  desktop capture out of scope.
- **Motivation:**
  Textual script results cannot expose visual defects in rendered map
  content. Current-map image capture lets an AI assistant inspect the
  complete rendered map or its current viewport without requiring the
  user to take and provide a screenshot manually.
- **Constraints:**
  - Resolve the current map through Freeplane's ordinary current-map
    context; do not add a map-view, component, window, or node target.
  - Capture map content only. Do not include tooltips, popups, panels, or
    overlays outside the map component.
  - Keep the operation read-only and restricted to Freeplane-owned map
    content.
  - Bound output dimensions, total pixels, and encoded size. Do not
    allocate an unsafe full-map image or silently downscale it until map
    text is unusable.
  - Preserve equivalent request semantics, image content, limit behavior,
    and errors through LangChain chat and MCP.
