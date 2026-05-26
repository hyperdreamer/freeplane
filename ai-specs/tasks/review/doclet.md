# Task: Generate a Freeplane scripting API mind map for LLM scripting
- **Task Identifier:** 2026-05-26-doclet
- **Scope:** Generate a build-time Freeplane `.mm` API map for the
  exact scripting API surface curated by
  `freeplane_plugin_script/build.gradle`, and expose it through the
  narrow `getApiDocumentation()` discovery tool.
- **Motivation:** The generated map is the primary AI-facing
  documentation artifact for Freeplane scripting and formulas. It must
  be searchable through existing map tools without pushing the full API
  into default chat context.
- **Constraints:**
  - Keep the primary artifact as a generated Freeplane `.mm` map.
  - Keep the covered class set aligned with the current curated
    scripting Javadoc source set.
  - Keep `getApiDocumentation()` narrow. It returns only
    `mapIdentifier`, `rootNodeIdentifier`, and `structureSummary`.
  - Keep HTML Javadoc as the human/deep-reference companion.
  - Do not commit the generated `freeplane-api.mm` file.
  - The generated map must remain present after `gradle build`.
  - Top-level map structure is now:
    - `How to use this map`
    - `Packages`
    - `API groups`
  - `Packages` is positioned left; `API groups` is positioned right.
  - `How to use this map` contains exactly one multiline guide leaf.
  - Property markers remain `[read]`, `[write]`, `[read-write]`.
  - Method markers remain `[read]` and `[write]` only.
- **Briefing:** Relevant files are
  `freeplane_plugin_script/src/doclet/java/org/freeplane/plugin/script/doclet/ApiModelBuilder.java`,
  `freeplane_plugin_script/src/doclet/java/org/freeplane/plugin/script/doclet/FreeplaneMindMapWriter.java`,
  `freeplane_plugin_ai/src/main/java/org/freeplane/plugin/ai/tools/documentation/ApiDocumentationStructureSummaryReader.java`,
  `freeplane_plugin_ai/src/main/java/org/freeplane/plugin/ai/tools/documentation/GetApiDocumentationTool.java`,
  and `freeplane_plugin_script/build.gradle`.
- **Research:**
  - The old map structure was usable but too large and too duplicative.
  - Most byte cost comes from node/XML scaffolding, not just long type
    names.
  - Search already supports subtree-root restriction, so the guide text
    can instruct callers to search under `Packages` or `API groups`
    selectively.
  - Freeplane map persistence supports clone references via shared-node
    attributes, so repeated exact type leaves can reuse content instead
    of repeating text.
- **Design:**
  - `How to use this map` contains one multiline guide leaf that
    explains:
    - use `API groups` for full merged documentation,
    - use `Packages` for the exact package/type index,
    - clone appearances can duplicate broad search hits,
    - subtree-rooted search can limit results to one branch,
    - capability-marker semantics, and
    - grouping/order rules.
  - `Packages` contains only package/type structure:
    - package nodes,
    - exact type nodes with `[interface]`, `[class]`, or `[enum]`, and
    - nested Proxy inner types.
  - `Packages` contains no member documentation.
  - `API groups` contains all full documentation.
  - A merged family groups related exact types such as `MindMap`,
    `Node`, or `Controller`.
  - A singleton family groups one exact type such as
    `HtmlUtils` or `Quantity`.
  - Family labels use the simple name by default and add a compact
    package qualifier on collisions, for example
    `Convertible (org.freeplane.api)` and
    `Convertible (org.freeplane.plugin.script.proxy)`.
  - Every API group includes a type-summary child. Use `Type` for
    single-type groups and `Types` for multi-type groups. Those type
    nodes are the canonical exact type leaves for clone reuse.
  - Merged-group property provenance uses:
    - `Getter available on`
    - `Setter available on`
  - Merged-group method/constant/nested-type provenance uses:
    - `Available on`
  - Those provenance nodes contain clones of the corresponding type
    leaves.
  - `Packages` later reuses those same exact type leaves as clones.
  - Groups with one exact type omit per-member availability because
    every member belongs to that one exact backing type.
  - Method labels include parameter names and types directly in the
    signature and also include declared thrown exception types.
  - Separate `Parameters` and `Returns` sections are omitted to avoid
    repeating signature structure. Extra method docs are folded into the
    method description text plus `Since`, `Deprecated`, and `Examples`
    when present.
  - Deprecated `Map` / `MapRO` alias families remain excluded when the
    canonical `MindMap` family exists.
  - `getApiDocumentation().structureSummary` preserves the multiline
    guide leaf and indents each physical line at the child depth.
- **Test specification:**
  - **Automated tests:**
    - `freeplane_plugin_script:test`
    - `freeplane_plugin_ai:test`
    - generation-level assertions for:
      - top-level sections,
      - left/right branch positioning,
      - multiline guide text,
      - clone-based exact type reuse,
      - merged availability labels,
      - single-type-group omission of redundant availability, and
      - multiline `structureSummary` rendering.
  - **Manual tests:**
    - Run `gradle :freeplane_plugin_script:generateFreeplaneApiMap`.
    - Verify `BIN/doc/api/freeplane-api.mm` exists.
    - Open the map in Freeplane.
    - Verify top-level sections and left/right placement.
    - Verify all API groups use `Type` or `Types` as appropriate and
      that multi-type groups use clone-based availability.
    - Verify `Packages` is structure-only.
    - Verify single-type groups do not repeat per-member availability.
    - Verify `getApiDocumentation()` returns the expected
      `structureSummary` and that subtree-rooted search can target only
      `Packages` or only `API groups`.
- **Implementation notes:**
  - **Interpretations:**
    - Used content clones for repeated exact type leaves so later
      package-branch appearances can reuse earlier group-owned type
      content.
    - Drive per-member provenance from the actual exact-type count, not
      from the family-construction path, so single-type API groups like
      `Convertible (org.freeplane.api)` stay concise while still showing
      `Type`.
    - Kept the task artifact concise because it is used for alignment,
      not for preserving every intermediate planning branch.
  - **Tradeoffs:**
    - The clearer `Packages` / `API groups` split improves navigation
      more than it reduces bytes; the dominant file-size cost remains
      XML/node scaffolding.
    - Search can still return one hit per visible clone appearance on
      broad queries, so the guide explicitly documents subtree-rooted
      search as the intended mitigation.
