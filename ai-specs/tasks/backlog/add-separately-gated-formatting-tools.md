# Task: Add separately gated formatting tools for AI and MCP
- **Task Identifier:** 2026-06-07-formatting-tools
- **Scope:** Add AI/MCP formatting inspection and formatting-editing
  capabilities behind a separate enable/disable setting, so formatting
  surface can be restricted independently of the current general tool
  availability levels.
- **Motivation:** Current AI/MCP tools expose content editing and main
  style assignment, but they do not expose detailed node formatting or
  style-vs-local formatting provenance. That keeps the tool surface
  smaller, but it also blocks safe formatting-aware inspection and
  editing. The user explicitly wants formatting tools to remain
  separately controllable.
- **Scenario:** A user enables ordinary AI tools for reading or editing
  map content but keeps formatting tools disabled. In that state, AI can
  still read content and assign supported styles, but it cannot inspect
  or mutate node formatting properties beyond the existing style-level
  surface. When the user later enables formatting tools explicitly, AI
  and MCP clients can inspect formatting with per-property provenance
  and can apply narrowly scoped formatting edits without blindly
  repeating style-governed properties at node level.
- **Constraints:**
  - Keep formatting capability separately controllable from the current
    `DISABLED` / `READING` / `EDITING` / `SCRIPT_EXECUTION` tool
    availability levels.
  - Default behavior must preserve the current restricted formatting
    surface when the new setting is disabled.
  - Formatting governed by styles should not be repeated at node level
    unless a property is intentionally overridden.
  - Any formatting read contract should distinguish at least:
    effective value, local node value, and source/provenance.
  - Node formatting and rich-text content formatting are separate
    concerns and should not be merged into one opaque write surface.
  - Rich-text rules must stay compatible with current Freeplane text
    behavior: no whole-body HTML styling, HTML 3.2 baseline, and
    additional support for `span` elements with `style`.
- **Briefing:** Relevant current AI/MCP gating is in
  `freeplane_plugin_ai`. `ToolAvailabilityLevel` and
  `ToolAvailabilityLevelSettings` define the current tool-surface enum
  and preference. `ModelContextProtocolToolCallAuthorizer` enforces MCP
  authorization. The current structured formatting surface is minimal:
  `NodeStyleContentReader` exposes only `activeStyles` and `mainStyle`,
  and `NodeStyleContentEditor` edits only `mainStyle`. No current AI/MCP
  tool exposes per-property formatting values or style-vs-local
  provenance.
- **Research:**
  - Current structured AI/MCP tools can read or write content, tags,
    icons, hyperlinks, connectors, summaries, and styles at main-style
    level, but not detailed formatting properties.
  - `ToolAvailabilityLevel` currently gates tools only through one enum
    with four values. It has no orthogonal formatting capability flag.
  - `ToolAvailabilityLevelSettings` currently reads the shared AI tool
    availability preference from `ai_tool_availability`.
  - `ModelContextProtocolToolCallAuthorizer` authorizes MCP tools from
    the current tool-availability level and separate formula-editing
    permission.
  - `NodeStyleContentReader` currently exposes only:
    - `readActiveStyles(nodeModel)`
    - `readMainStyle(nodeModel)`
  - `NodeStyleContentEditor` currently supports only main-style set or
    clear operations.
  - User clarification for future formatting work:
    - formatting tools were intentionally not exposed to keep the tool
      surface smaller;
    - if added later, they should be separately enabled or disabled;
    - style-governed formatting should not be redundantly written at
      node level;
    - rich-text formatting should avoid whole-body HTML styling and stay
      within Freeplane's supported HTML profile.
- **Analysis:**
  - A separate formatting-tools gate is preferable to overloading the
    existing tool-availability enum because the user wants formatting
    risk and surface area controlled independently from ordinary content
    editing.
  - Read-side formatting provenance should be designed before or
    together with write-side formatting edits, because blind formatting
    writes are not safe enough.
  - The contract should separate node-formatting edits from rich-text
    content edits, because they have different semantics and different
    safety rules.
- **Design:**
  - Add a separate AI/MCP preference for formatting tools, default
    disabled.
  - When the setting is disabled:
    - keep current behavior;
    - do not register, advertise, or authorize the new formatting
      tools.
  - When the setting is enabled, add at least one read-side formatting
    tool that returns per-property formatting metadata such as:
    - effective value,
    - local node value,
    - provenance/source category,
    - enough information to detect redundant local overrides.
  - Add write-side formatting tools only with explicit per-property
    semantics, including the ability to clear local overrides instead of
    always writing values.
  - Keep main-style assignment as a separate simpler surface rather than
    forcing all style changes through low-level formatting tools.
  - Keep rich-text content editing on the existing textual-content path;
    do not treat inline HTML content styling as if it were node-format
    state.
  - Revisit whether the new capability should affect:
    - internal AI chat tools,
    - MCP tool exposure,
    - script-facing AI request options,
    - or all of them uniformly.
- **Test specification:**
  - Automated tests:
    - Verify the new formatting-tools setting defaults to disabled.
    - Verify formatting tools are absent or unauthorized when the
      setting is disabled.
    - Verify enabling the setting exposes the intended formatting
      read/edit surface without changing unrelated tool availability.
    - Verify read-side formatting metadata distinguishes local override
      from style-derived value for representative properties.
    - Verify write-side formatting can clear a local override instead of
      rewriting the same effective value.
    - Verify main-style editing continues to work independently of the
      new formatting-tools gate unless explicitly redesigned.
    - Verify rich-text content editing tests still enforce the existing
      HTML profile and avoid unexpected whole-body styling behavior.
  - Manual tests:
    - Use an MCP client with formatting tools disabled and confirm that
      formatting inspection/edit requests are unavailable while ordinary
      content tools still work.
    - Enable formatting tools and confirm that a formatting-inspection
      request can distinguish style-derived formatting from local node
      overrides on a styled node.
    - Confirm that clearing a local override preserves the style-driven
      visual result where appropriate.
