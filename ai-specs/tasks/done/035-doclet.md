# Task: Generate a Freeplane scripting API mind map for LLM scripting
- **Task Identifier:** 2026-05-26-doclet
- **Scope:** Generate a build-time Freeplane `.mm` API map for the
  exact scripting API surface curated by
  `freeplane_plugin_script/build.gradle`, expose it through the narrow
  `getApiDocumentation()` discovery tool, keep the generated map
  content maintainable and stylable, and retire the obsolete runtime
  scripting API generator script.
- **Motivation:** The generated map is the primary AI-facing
  documentation artifact for Freeplane scripting and formulas. It must
  be searchable through existing map tools without pushing the full API
  into default chat context, and the old runtime generator path should
  not remain as a conflicting second source.
- **Constraints:**
  - Keep the primary artifact as a generated Freeplane `.mm` map.
  - Keep the covered class set aligned with the current curated
    scripting Javadoc source set.
  - Keep `getApiDocumentation()` narrow. It returns only a string
    containing the plain-text `structureSummary` plus JSON data for
    `mapIdentifier`, `rootNodeIdentifier`,
    `packagesRootNodeIdentifier`, and `apiGroupsRootNodeIdentifier`.
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
  `freeplane_plugin_script/src/doclet/java/org/freeplane/plugin/script/doclet/FreeplaneApiMapDoclet.java`,
  `freeplane_plugin_ai/src/main/java/org/freeplane/plugin/ai/tools/documentation/GetApiDocumentationTool.java`,
  `freeplane_plugin_ai/src/main/java/org/freeplane/plugin/ai/tools/documentation/ApiDocumentationStructureSummaryReader.java`,
  `freeplane_plugin_script/build.gradle`,
  `freeplane_plugin_script/scripts/apiGenerator.groovy`,
  `freeplane/src/main/java/org/freeplane/core/ui/components/IconSelectionPopupDialog.java`,
  `freeplane/src/main/java/org/freeplane/features/icon/IconDescription.java`,
  and the translation bundles containing
  `scripting_api_generator_*` keys.

## Subtask: Generate self-describing API map
- **Status:** done
- **Scope:** Generate `BIN/doc/api/freeplane-api.mm` from the curated
  scripting API source set with the current self-describing
  `How to use this map` / `Packages` / `API groups` structure,
  clone-based exact type reuse, and build integration.
- **Motivation:** The map is the primary AI-facing entry point for the
  scripting API and must stay usable without loading the whole API into
  default chat context.
- **Constraints:**
  - `Packages` stays structure-only: package nodes, exact type nodes,
    and nested Proxy inner types, with no member documentation.
  - `API groups` stays the full merged documentation tree.
  - Every API group includes a type-summary child. Use `Type` for
    single-type groups and `Types` for multi-type groups.
  - Merged-group provenance uses `Getter available on`,
    `Setter available on`, and `Available on` with clones of the
    corresponding type leaves.
  - Single-type groups omit redundant per-member availability.
  - Method labels include parameter names, parameter types, and thrown
    exception types directly in the signature.
  - Separate `Parameters` and `Returns` sections stay omitted.
- **Briefing:** Relevant files are
  `freeplane_plugin_script/build.gradle`,
  `freeplane_plugin_script/src/doclet/java/org/freeplane/plugin/script/doclet/ApiModelBuilder.java`,
  `freeplane_plugin_script/src/doclet/java/org/freeplane/plugin/script/doclet/FreeplaneMindMapWriter.java`,
  `freeplane_plugin_script/src/doclet/java/org/freeplane/plugin/script/doclet/JavadocCommentExtractor.java`,
  and doclet-generation tests under
  `freeplane_plugin_script/src/test/java/org/freeplane/plugin/script/doclet/`.
- **Research:**
  - The old map structure was usable but too large and too duplicative.
  - Most byte cost comes from XML/node scaffolding, not just long type
    names.
  - Search already supports subtree-root restriction, so the guide text
    can instruct callers to search under `Packages` or `API groups`
    selectively.
  - Freeplane map persistence supports clone references via shared-node
    attributes, so repeated exact type leaves can reuse content instead
    of repeating text.
- **Design:**
  - Use a build-only doclet source set in `freeplane_plugin_script` to
    generate the `.mm` file.
  - Generate the map to `BIN/doc/api/freeplane-api.mm` and keep it
    present after `gradle build`.
  - Use the top-level sections `How to use this map`, `Packages`, and
    `API groups`, with `Packages` on the left and `API groups` on the
    right.
  - Keep `How to use this map` as exactly one multiline guide leaf.
  - Use exact type leaves with `[interface]`, `[class]`, or `[enum]`.
  - Use content clones so API-group type leaves can be reused from
    provenance nodes and the later `Packages` tree.
  - Use conceptual family labels with compact package qualifiers only on
    collisions, for example `Convertible (org.freeplane.api)` and
    `Convertible (org.freeplane.plugin.script.proxy)`.
  - Keep method capability markers to `[read]` or `[write]` only, based
    on read/write surface availability.
  - Keep property markers on getter/setter semantics.
  - Keep the guide text explicit that the map is large, that shallow
    orientation under `API groups` is acceptable, and that search
    should precede in-depth reading.
  - Teach concrete search patterns in tool terms: use `searchNodes`
    with short text queries likely to appear in the documentation text,
    avoid full path strings and broad request phrases, and switch to
    `readNodesWithDescendants` plus branch reading for orientation when
    `searchNodes` is noisy.
- **Test specification:**
  - **Automated tests:**
    - `gradle :freeplane_plugin_script:test -Djava.net.preferIPv6Addresses=true -Djava.awt.headless=true`
    - `gradle :freeplane_plugin_script:generateFreeplaneApiMap -Djava.net.preferIPv6Addresses=true -Djava.awt.headless=true`
    - `gradle :freeplane_plugin_script:generateFreeplaneApiMap build -Djava.net.preferIPv6Addresses=true -Djava.awt.headless=true`
    - generation-level assertions for top-level sections, left/right
      branch positioning, multiline guide text, clone-based exact type
      reuse, merged availability labels, single-type-group omission of
      redundant availability, and current signature rendering.
  - **Manual tests:**
    - Open `BIN/doc/api/freeplane-api.mm` in Freeplane.
    - Verify top-level sections and left/right placement.
    - Verify `Packages` is structure-only.
    - Verify multi-type groups use `Types` and clone-based provenance.
    - Verify single-type groups use `Type` and do not repeat
      per-member availability.
- **Implementation notes:**
  - **Interpretations:**
    - Used content clones for repeated exact type leaves so later
      package-branch appearances can reuse earlier group-owned type
      content.
    - Drive per-member provenance from the actual exact-type count, not
      from the family-construction path, so single-type API groups like
      `Convertible (org.freeplane.api)` stay concise while still
      showing `Type`.
  - **Tradeoffs:**
    - The clearer `Packages` / `API groups` split improves navigation
      more than it reduces bytes; the dominant file-size cost remains
      XML/node scaffolding.
    - Search can still return one hit per visible clone appearance on
      broad queries, so the guide explicitly documents subtree-rooted
      search as the intended mitigation.

## Subtask: Add getApiDocumentation discovery tool
- **Status:** done
- **Scope:** Add the narrow zero-argument `getApiDocumentation()` tool
  that hidden-loads the generated API map and returns the identifiers
  and summary needed for follow-up map reads and searches.
- **Motivation:** Agents need a lightweight discovery entry point for
  the generated map before using broader content and search tools.
- **Constraints:**
  - Return only a string containing the plain-text
    `structureSummary` plus JSON data for `mapIdentifier`,
    `rootNodeIdentifier`, `packagesRootNodeIdentifier`, and
    `apiGroupsRootNodeIdentifier`.
  - If the API map is missing or invalid, return explicit errors only.
  - Keep broader documentation-query tooling out of this increment.
- **Briefing:** Relevant files are
  `freeplane_plugin_ai/src/main/java/org/freeplane/plugin/ai/tools/AIToolSet.java`,
  `freeplane_plugin_ai/src/main/java/org/freeplane/plugin/ai/tools/AIToolSetBuilder.java`,
  `freeplane_plugin_ai/src/main/java/org/freeplane/plugin/ai/tools/documentation/GetApiDocumentationTool.java`,
  and tests under
  `freeplane_plugin_ai/src/test/java/org/freeplane/plugin/ai/tools/documentation/`.
- **Research:**
  - Hidden loading must use the loaded-map registry rather than open-map
    view enumeration, because `ControllerMapModelProvider` only sees
    maps with views.
  - `AvailableMaps.getOrCreateMapIdentifier(mapModel)` is enough to make
    the hidden-loaded documentation map addressable by later tools.
  - `structureSummary` should come from the `How to use this map`
    section and preserve multiline guide text with child indentation.
- **Design:**
  - Split responsibilities across
    `GetApiDocumentationTool`,
    `GetApiDocumentationResponse`,
    `ApiDocumentationMapLoader`, and
    `ApiDocumentationStructureSummaryReader`.
  - Resolve the generated map from the installation `doc/api`
    directory, load/register it hidden, and return the root plus
    top-level `Packages` and `API groups` subtree identifiers.
  - Read `structureSummary` from the `How to use this map` subtree and
    preserve each physical line of the guide leaf.
  - Expose the tool result as a string: the plain-text
    `structureSummary`, followed by JSON containing the returned
    identifiers.
  - Use explicit missing-map and invalid-map errors with remedy text.
- **Test specification:**
  - **Automated tests:**
    - `gradle :freeplane_plugin_ai:test -Djava.net.preferIPv6Addresses=true -Djava.awt.headless=true`
    - targeted tests for hidden map loading, invalid-map errors,
      structure-summary rendering, and tool exposure.
  - **Manual tests:**
    - Run `getApiDocumentation()` in an installation containing the
      generated map.
    - Verify it returns the expected multiline `structureSummary` plus
      JSON containing `mapIdentifier`, `rootNodeIdentifier`,
      `packagesRootNodeIdentifier`, and
      `apiGroupsRootNodeIdentifier`.
    - Verify the missing-map error reports the expected path and remedy.

## Subtask: Externalize guide text and use a root-template mind map
- **Status:** done
- **Scope:** Move the `How to use this map` guide text to a doclet
  resource file and generate the final `.mm` by inserting generated
  content into a versioned root-only template mind map.
- **Motivation:** Guide wording and root styling are presentation
  content. They should be maintainable and stylable without Java-only
  edits.
- **Constraints:**
  - The guide resource must still produce exactly one multiline leaf
    under `How to use this map`.
  - The template `.mm` must be the source of map/root presentation.
  - The template must contain one `<map>` element and one root `<node>`
    used as the insertion target.
  - Generation must insert generated top-level sections under that root
    without overwriting template-owned map/root semantics.
  - `getApiDocumentation().structureSummary` must keep the same
    contract.
  - The generated map must remain deterministic and present after
    `gradle build`.
- **Briefing:** Relevant files are
  `freeplane_plugin_script/build.gradle`,
  `freeplane_plugin_script/src/doclet/java/org/freeplane/plugin/script/doclet/FreeplaneApiMapDoclet.java`,
  `freeplane_plugin_script/src/doclet/java/org/freeplane/plugin/script/doclet/FreeplaneMindMapWriter.java`,
  `freeplane_plugin_script/src/doclet/java/org/freeplane/plugin/script/doclet/ApiModelBuilder.java`,
  and new doclet resources under
  `freeplane_plugin_script/src/doclet/resources/`.
- **Research:**
  - The current writer creates the whole map/root structure from Java
    and hardcodes root styling details.
  - The current guide text lives in
    `ApiModelBuilder.buildGuideText()`.
  - `freeplane_plugin_script/build.gradle` currently disables doclet
    resources with `srcDirs = []`, so resource-backed guide/template
    input is not yet enabled.
  - Later finding based on code already changed during implementation:
    explicit task selection could still let root `cleanBUILD` delete
    `BIN/doc/api/freeplane-api.mm` after generation, so
    `generateFreeplaneApiMap` must also be ordered after root
    `cleanBUILD`.
  - Later finding based on code already changed during implementation:
    the template root may contain styling hooks that must stay before
    generated branch nodes, and the serialized map must not start with
    an XML declaration.
- **Design:**
  - Enable a doclet resources directory and add a UTF-8 guide text
    resource used to populate the guide leaf.
  - Add a versioned template `.mm` resource containing only the `<map>`
    element and one root `<node>`.
  - Parse the template and append the generated top-level sections
    under that root in document order.
  - Preserve template-owned map/root semantics; generated content owns
    only the inserted child `<node>` branches.
  - Preserve existing non-`node` children under the template root
    before any generated branch node is added.
  - Omit the XML declaration when serializing the generated `.mm` so
    the output stays compatible with historical Freeplane map files.
  - Remove root styling concerns from Java where the template now owns
    them.
  - Extend doclet tests to verify guide-text loading and template-root
    preservation.
  - Order `generateFreeplaneApiMap` after root `cleanBUILD` so combined
    invocations such as
    `gradle :freeplane_plugin_script:generateFreeplaneApiMap build`
    still leave `BIN/doc/api/freeplane-api.mm` present.
- **Test specification:**
  - **Automated tests:**
    - `gradle :freeplane_plugin_script:test -Djava.net.preferIPv6Addresses=true -Djava.awt.headless=true`
    - `gradle :freeplane_plugin_script:generateFreeplaneApiMap -Djava.net.preferIPv6Addresses=true -Djava.awt.headless=true`
    - `gradle :freeplane_plugin_script:generateFreeplaneApiMap build -Djava.net.preferIPv6Addresses=true -Djava.awt.headless=true`
    - generation-level assertions for guide-resource loading and
      template-root preservation.
  - **Manual tests:**
    - Inspect `BIN/doc/api/freeplane-api.mm`.
    - Confirm template root styling/text remains unchanged while the
      generated top-level sections appear under it.
    - Confirm the `How to use this map` guide still contains the
      externalized multiline text.
    - Confirm the generated file is still present after the combined
      `generateFreeplaneApiMap build` invocation.
    - Confirm the generated `.mm` starts directly with `<map ...>` and
      not with an XML declaration.
    - Confirm template hooks such as `MapStyle` still appear before the
      first generated branch node.
- **Implementation notes:**
  - **Interpretations:**
    - The guide text now comes from a UTF-8 doclet resource while the
      template `.mm` owns root presentation and root metadata.
    - Template preservation is semantic rather than byte-for-byte: the
      generator parses the template, appends generated child nodes
      under the template root, and rewrites the XML deterministically.
    - Serialization now intentionally omits the XML declaration to
      match historical Freeplane map expectations while keeping the
      template-owned hook structure intact ahead of generated
      branches.
  - **Tradeoffs:**
    - Kept the template contract minimal by requiring exactly one
      `<map>` and one root `<node>` with no preexisting child `<node>`
      branches, which avoids merge ambiguity in the writer.
    - Added resource-location logic to the doclet generation test so
      test Javadoc invocations can load processed doclet resources the
      same way the Gradle build does.

## Subtask: Remove the legacy scripting API generator script
- **Status:** done
- **Scope:** Remove the obsolete `freeplane_plugin_script/scripts/apiGenerator.groovy`
  generator path, stop shipping its menu-driven runtime generator, and
  preserve icon-name discoverability by showing internal icon names in
  the icon-selection status line.
- **Motivation:** The build-generated API mind map is now the
  authoritative scripting API reference. The legacy Groovy generator
  should not survive as a second, structurally obsolete path, but its
  user-facing icon-name discoverability should not be lost.
- **Constraints:**
  - Do not couple icon-name discoverability to `doc/api` or to the API
    documentation map.
  - Keep backward-compatible internal icon names visible to users where
    they choose icons for scripts.
  - Show icon status text in the form `Translated label
    (internalName) — Shortcut` when both name and shortcut are
    available.
  - Keep non-icon control actions such as icon-removal actions free of
    synthetic internal names.
- **Briefing:** Relevant files are
  `freeplane_plugin_script/scripts/apiGenerator.groovy`,
  `freeplane_plugin_script/build.gradle`,
  `freeplane/src/main/java/org/freeplane/core/ui/components/IconSelectionPopupDialog.java`,
  `freeplane/src/main/java/org/freeplane/features/icon/IconDescription.java`,
  `freeplane/src/main/java/org/freeplane/features/icon/UIIcon.java`,
  `freeplane/src/main/java/org/freeplane/features/icon/mindmapmode/IconAction.java`,
  `freeplane/src/main/java/org/freeplane/features/icon/mindmapmode/IconSelectionPlugin.java`,
  and translation bundles containing `scripting_api_generator_*` keys.
- **Research:**
  - `freeplane_plugin_script/scripts/apiGenerator.groovy` is the only
    remaining runtime generator path and is still copied into
    installations by `freeplane_plugin_script/build.gradle`.
  - The legacy script exposes the old API-map structure and also lists
    icon translations with their internal icon names.
  - `IconSelectionPopupDialog` currently shows only translated label
    and shortcut in the status line, although its filter already also
    matches the icon file name.
  - `IconSelectionPopupDialog` is used both by
    `IconSelectionPlugin` and by `IconProperty`, so a dialog-level
    change can cover both common icon-picking entry points.
- **Design:**
  - Remove `freeplane_plugin_script/scripts/apiGenerator.groovy` from
    the repository and from packaged scripts.
  - Remove dead `scripting_api_generator_*` translation keys from the
    translation bundles and run `gradle format_translation` after the
    edits.
  - Extend the icon-selection status text with an optional internal
    icon name supplied by actual icon entries, keeping the visible
    format `Translated label (internalName) — Shortcut` when a
    shortcut exists and `Translated label (internalName)` otherwise.
  - Expose that optional internal icon name through
    `IconDescription` or an equally narrow shared hook so
    `IconSelectionPopupDialog` can format the status line without
    caller-specific special cases.
  - Leave non-icon control actions on their current label/shortcut-only
    status presentation.
  - Add focused automated coverage for the status-text formatting so
    the icon and non-icon cases are verified without depending on a
    full UI interaction test.
- **Test specification:**
  - **Automated tests:**
    - `gradle format_translation -Djava.net.preferIPv6Addresses=true
      -Djava.awt.headless=true`
    - `gradle :freeplane:test :freeplane_plugin_script:build
      -Djava.net.preferIPv6Addresses=true -Djava.awt.headless=true`
    - targeted tests for icon-selection status formatting covering an
      actual icon entry and a non-icon control action.
  - **Manual tests:**
    - Open the icon selector and verify a normal icon status line reads
      as `Translated label (internalName) — Shortcut` when a shortcut
      exists.
    - Verify a normal icon without a shortcut shows
      `Translated label (internalName)`.
    - Verify icon-removal actions still show only their label and any
      shortcut, without parentheses for an internal icon name.
    - Verify the installed scripts no longer include
      `BIN/scripts/apiGenerator.groovy`.

## Subtask: Keep doclet-only sources out of Eclipse project
- **Status:** done
- **Scope:** Move doclet-only tests into their own Gradle source set
  and keep doclet-only sources out of the Eclipse project generated by
  the Gradle `eclipse` plugin.
- **Motivation:** `freeplane_plugin_script` now mixes Java 8 plugin
  code with Java 17 doclet support. The Gradle build handles that split,
  but the generated Eclipse project does not compile the doclet-only
  sources cleanly and should stop exposing them as ordinary Eclipse
  source folders.
- **Constraints:**
  - Keep the build-time doclet implementation in
    `freeplane_plugin_script`; do not move it into a separate project.
  - Keep ordinary plugin sources and ordinary plugin tests available in
    Eclipse.
  - Move only the doclet-specific tests into a separate source
    directory/source set.
  - Exclude both doclet implementation sources and doclet-test sources
    from the generated Eclipse classpath.
  - Keep Gradle automation compiling and running the doclet tests.
- **Briefing:** Relevant files are
  `freeplane_plugin_script/build.gradle`,
  `freeplane_plugin_script/src/doclet/java/`,
  `freeplane_plugin_script/src/docletTest/java/org/freeplane/plugin/script/doclet/`,
  and the generated Eclipse metadata for `freeplane_plugin_script/`.
- **Research:**
  - The generated Eclipse `.classpath` currently includes
    `src/doclet/java` and `src/doclet/resources` as source entries.
  - The same Eclipse project uses a Java 8 JRE container and Java 8
    compliance settings, so doclet-only sources do not fit that
    project model cleanly.
  - The doclet tests currently live under the regular `test` source set
    at `src/test/java/org/freeplane/plugin/script/doclet/` and depend
    on doclet-only classes.
  - The Gradle `eclipse` plugin can be customized after classpath
    generation, so doclet-only source entries can be removed from the
    generated `.classpath` while keeping the normal project intact.
  - Later finding based on code already changed during implementation:
    moving the doclet tests into `src/docletTest/java` lets the regular
    `test` source set drop its doclet-only classpath extension while
    preserving dedicated Gradle coverage through a separate
    `docletTest` task.
  - Later finding based on code already changed during implementation:
    removing the doclet and doclet-test source entries in
    `eclipse.classpath.file.whenMerged` is sufficient for the generated
    Eclipse `.classpath`; the project can stay on its normal Java 8
    Eclipse settings for ordinary plugin work.
- **Design:**
  - Add a dedicated `docletTest` source set rooted at
    `src/docletTest/java` for tests that exercise the doclet-only
    implementation.
  - Move the existing doclet tests from the regular `test` source set
    into `src/docletTest/java/org/freeplane/plugin/script/doclet/`.
  - Add a dedicated Gradle `docletTest` test task wired into `check` so
    build/test behavior remains covered after the move.
  - Remove the doclet-specific classpath extension from the regular
    `test` source set once the doclet tests move out.
  - Customize Eclipse classpath generation for
    `freeplane_plugin_script` so `src/doclet/java`,
    `src/doclet/resources`, and `src/docletTest/java` are excluded from
    the generated project.
- **Test specification:**
  - **Automated tests:**
    - `gradle :freeplane_plugin_script:test :freeplane_plugin_script:docletTest
      -Djava.net.preferIPv6Addresses=true -Djava.awt.headless=true`
    - `gradle :freeplane_plugin_script:eclipse
      -Djava.net.preferIPv6Addresses=true -Djava.awt.headless=true`
    - verify the generated `freeplane_plugin_script/.classpath` no
      longer contains doclet-only source entries.
  - **Manual tests:**
    - Refresh the Eclipse project and confirm the doclet-only sources
      are no longer compiled inside the regular plugin project.
    - Confirm ordinary plugin sources and non-doclet tests remain
      present in Eclipse.
- **Implementation notes:**
  - **Tradeoffs:**
    - Kept the doclet implementation in the same Gradle project and hid
      its source directories from Eclipse instead of changing the whole
      Eclipse project model or splitting the doclet into a separate
      module. That keeps the build-local doclet design intact while
      restoring a clean Java 8-oriented Eclipse project for ordinary
      plugin work.

## Subtask: Generate API groups for public nested types
- **Status:** done
- **Scope:** Make every visible nested type collected from the curated
  scripting API source set eligible for full API-group generation, not
  only for structural listing under its enclosing type. Cover
  `org.freeplane.api.ai.AiRequestOptions.Builder` and all similar
  public or protected nested exact types. Do not require Javadoc
  comments for methods to appear. Do not commit the generated
  `freeplane-api.mm` file.
- **Motivation:** The generated API map is the evidence source for the
  scripting assistant. If a public nested type is present only as a
  `Nested types` reference, the assistant has no generated-map evidence
  for that type's script-facing methods.
- **Scenario:** A script author or scripting assistant discovers
  `AiRequestOptions.builder()` in the API map. The returned
  `AiRequestOptions.Builder` type must lead to generated-map method
  evidence for the fluent builder calls and `build()`. The same rule
  applies to other visible nested exact types in the curated scripting
  API source set.
- **Constraints:**
  - Preserve the current `Packages` branch as structure-only navigation.
  - Preserve parent API-group `Nested types` references; they remain
    orientation links, not the only documentation for nested types.
  - Preserve existing proxy/API mirror grouping such as `Node` and
    `Convertible`; do not duplicate exact types already assigned to a
    mirrored family.
  - Use a general nested-type rule; do not special-case
    `AiRequestOptions.Builder`.
  - Keep generated output deterministic and keep using clone-based exact
    type reuse.
- **Briefing:** Relevant files are
  `freeplane_plugin_script/src/doclet/java/org/freeplane/plugin/script/doclet/ApiModelBuilder.java`,
  `freeplane_plugin_script/src/docletTest/java/org/freeplane/plugin/script/doclet/FreeplaneApiMapDocletGenerationTest.java`,
  `freeplane_plugin_script/build.gradle`, and
  `freeplane_api/src/main/java/org/freeplane/api/ai/AiRequestOptions.java`.
- **Research:**
  ```plantuml
  @startuml
  set separator none
  package "freeplane_plugin_script doclet" {
    package "current" {
      class ApiModelBuilder {
        +build()
        -collectIncludedTypes()
        -buildDocumentationFamilies()
        -isMirroredFamilyExactType(TypeElement)
        -buildApiGroupNode(DocumentationFamily)
        -projectSurface(TypeElement)
      }
      class DocumentationFamily
      class ApiMapNode
    }
  }
  ApiModelBuilder --> DocumentationFamily
  ApiModelBuilder --> ApiMapNode
  @enduml
  ```

  ```plantuml
  @startuml
  participant "Javadoc" as Javadoc
  participant "ApiModelBuilder" as Builder
  participant "API groups" as Groups
  participant "Packages" as Packages
  Javadoc -> Builder : collectIncludedTypes(top-level roots)
  Builder -> Builder : collectNestedTypes(top-level type)
  Builder -> Groups : buildDocumentationFamilies(top-level API + Proxy nested families)
  Builder -> Packages : buildPackageTypeNode(top-level + nested exact type nodes)
  @enduml
  ```

  - `collectIncludedTypes()` recursively records visible nested types in
    `includedTypesByQualifiedName`.
  - `buildPackageTypeNode()` already renders nested exact type nodes in
    the `Packages` branch.
  - `buildApiGroupNode()` can render properties, methods, constants, and
    nested-type references for any `DocumentationFamily` exact type.
  - `buildDocumentationFamilies()` currently creates singleton API
    groups only from `includedTopLevelTypes`.
  - `isMirroredFamilyExactType()` treats Proxy nested types as mirrored
    family members, but treats nested `org.freeplane.api` types as not
    mirrored.
  - The generated `BIN/doc/api/freeplane-api.mm` currently lists
    `AiRequestOptions.Builder` under `AiRequestOptions` / `Nested types`,
    but has no API group containing the builder methods `timeout`,
    `mode`, `modelSelection`, `toolAvailability`, `selectionOverride`,
    `systemMessage`, `exactSystemMessage`, `profile`, or `build`.
- **Analysis:**
  - Generate full API groups for visible nested exact types because the
    generated map is the scripting assistant's evidence source.
  - Keep `Packages` member-free because it is structural navigation, not
    the full documentation branch.
  - Keep parent `Nested types` references because they remain useful
    orientation from the enclosing API group.
  - Reuse the existing surface projection for nested-type members
    because the missing behavior is family eligibility, not method
    extraction.
- **Design:**
  ```plantuml
  @startuml
  set separator none
  package "freeplane_plugin_script doclet" {
    package "target" {
      class ApiModelBuilder {
        +build()
        -buildDocumentationFamilies()
        -isMirroredFamilyExactType(TypeElement)
        -familyBaseLabel(TypeElement)
        -buildApiGroupNode(DocumentationFamily)
        -projectSurface(TypeElement)
      }
      class DocumentationFamily {
        +addExactType(TypeElement)
      }
      class ApiMapNode
    }
  }
  ApiModelBuilder --> DocumentationFamily : creates mirrored and singleton families
  ApiModelBuilder --> ApiMapNode : renders API groups and packages
  @enduml
  ```

  ```plantuml
  @startuml
  participant "Javadoc" as Javadoc
  participant "ApiModelBuilder" as Builder
  participant "API groups" as Groups
  participant "Packages" as Packages
  Javadoc -> Builder : collectIncludedTypes(top-level roots)
  Builder -> Builder : collectNestedTypes(top-level type)
  Builder -> Groups : create mirrored families for current mirror rules
  Builder -> Groups : create singleton families for remaining exact types
  Builder -> Packages : build structural tree for top-level and nested types
  @enduml
  ```

  - Change family construction to track all exact types already assigned
    to mirrored families, not only mirrored top-level type names.
  - Create singleton documentation families from remaining included
    exact types, including visible nested types, unless the exact type is
    excluded.
  - Label singleton nested-type API groups with the same displayed exact
    type name used by exact type nodes, for example
    `AiRequestOptions.Builder`, so generic names such as `Builder` do
    not become ambiguous.
  - Keep Proxy nested types on their existing mirrored-family path so
    groups such as `Node` and `MindMap` keep their current labels and
    merged read/write surface behavior.
  - Leave member projection unchanged: nested-type API groups use the
    existing property, method, constant, documentation, and availability
    generation rules.
  - Extend the doclet fixture with a public nested API type that has
    visible builder-style methods and assert that the generated map has
    both the parent `Nested types` reference and a full nested-type API
    group with methods.
- **Test specification:**
  - **Automated tests:**
    - A doclet fixture with a public nested type under an included
      `org.freeplane.api` top-level type generates a full API group for
      the nested type.
    - The nested-type API group label uses the displayed exact type name
      including the enclosing type name.
    - The nested-type API group contains visible methods even when those
      methods have no Javadoc comments.
    - The enclosing type still contains a `Nested types` reference to
      the nested type.
    - The `Packages` branch still lists the nested exact type
      structurally and does not gain member documentation.
    - Existing mirrored Proxy/API groups keep their current labels and
      do not gain duplicate singleton groups for exact types already in
      mirrored families.
    - Generated output from the current curated sources contains an
      `AiRequestOptions.Builder` API group with its fluent option
      methods and `build()`.
  - **Manual tests:** N/A
