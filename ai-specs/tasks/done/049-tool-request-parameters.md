# Task: Update AI tool request parameter structures
- **Task Identifier:** 2026-07-04-tool-parameters
- **Scope:** Update the AI plugin tool request structures for the read
  and search context tools so their generated tool schemas expose the
  intended request field names and no internal presence-helper fields.
  Keep the top-level `request` wrapper. Do not implement MCP-specific
  schema translation, MCP-specific dispatch rewriting, or wrong-request
  fallback handling.
- **Motivation:** The observed tool-call failure was first seen through
  MCP, but the underlying problem is the tool parameter contract:
  LangChain4j derives nested request schemas from the request DTO
  fields. The current DTO field names expose `summaryDepth`,
  `maximumTotalTextCharacters`, and `has...` helper fields to all tool
  callers that rely on the generated schema. Fixing only MCP would
  leave the real tool contract unchanged and would create divergent
  caller behavior.
- **Scenario:** A tool caller uses the generated schema for
  `readNodesWithDescendants`, `readNodesWithDescendantsAsPlainText`, or
  `searchNodes`. The caller still sends one top-level `request` object.
  Inside that request, read depth uses the precise field
  `additionalSummaryDepth`, character budget uses `maxCharacters`, and
  internal presence flags are absent from the schema. The tool runtime
  receives the same effective values and preserves existing traversal,
  search, defaulting, and budget behavior.
- **Constraints:**
  - Keep the top-level `request` wrapper for affected tools.
  - Change tool request structures, not MCP registry or dispatcher
    behavior.
  - Do not add flat top-level argument support.
  - Do not add legacy aliases for old request field names.
  - Do not implement wrong-request fallback or repair handling.
  - Do not change `freeplane_api` or the user-facing scripting API.
  - Preserve current read traversal, summary-depth arithmetic, search,
    defaulting, normalization, and budget enforcement semantics.
  - Preserve the distinction between `fullContentDepth` and the
    additional summary-only depth.
  - Do not use `Optional` as request DTO fields.
- **Briefing:** `AIToolSet` exposes one-argument `@Tool` methods whose
  parameter is a request DTO. `ToolExecutorFactory` builds LangChain4j
  `ToolSpecification`s and `DefaultToolExecutor`s from those methods.
  `ModelContextProtocolToolRegistry` converts the LangChain4j tool
  schema to MCP JSON, but it is not the source of the parameter model.
  The relevant request DTOs are
  `ReadNodesWithDescendantsRequest` and `SearchNodesRequest`. The
  relevant execution classes are `ReadNodesWithDescendantsTool` and
  `SearchNodesTool`.
- **Research:**
  ```plantuml
  @startuml
  set separator none
  package "org.freeplane.plugin.ai.tools" {
    package read {
      class ReadNodesWithDescendantsRequest {
        {static} DEFAULT_FULL_CONTENT_DEPTH : int
        {static} DEFAULT_SUMMARY_DEPTH : int
        {static} DEFAULT_MAXIMUM_TOTAL_TEXT_CHARACTERS : int
        - mapIdentifier : String
        - nodeIdentifiers : List<String>
        - contextSections : List<ContextSection>
        - fullContentDepth : Integer
        - summaryDepth : Integer
        - maximumTotalTextCharacters : Integer
        - hasFullContentDepth : boolean
        - hasSummaryDepth : boolean
        - hasMaximumTotalTextCharacters : boolean
        + getFullContentDepth() : Integer
        + getSummaryDepth() : Integer
        + getMaximumTotalTextCharacters() : Integer
        + hasFullContentDepth() : boolean
        + hasSummaryDepth() : boolean
        + hasMaximumTotalTextCharacters() : boolean
      }
    }
    package search {
      class SearchNodesRequest {
        {static} DEFAULT_LIMIT : int
        {static} DEFAULT_OFFSET : int
        {static} DEFAULT_MAXIMUM_TOTAL_TEXT_CHARACTERS : int
        {static} DEFAULT_MATCHING_MODE : SearchMatchingMode
        {static} DEFAULT_CASE_SENSITIVITY : SearchCaseSensitivity
        {static} DEFAULT_CONTENT_REQUEST : NodeContentRequest
        - mapIdentifier : String
        - queryText : String
        - subtreeRootNodeIdentifiers : List<String>
        - nodeContentRequestForSearch : NodeContentRequest
        - matchingMode : SearchMatchingMode
        - caseSensitivity : SearchCaseSensitivity
        - resultSections : List<SearchResultSection>
        - offset : Integer
        - limit : Integer
        - maximumTotalTextCharacters : Integer
        - hasMatchingMode : boolean
        - hasCaseSensitivity : boolean
        - hasOffset : boolean
        - hasLimit : boolean
        - hasMaximumTotalTextCharacters : boolean
        + getMaximumTotalTextCharacters() : Integer
        + hasMatchingMode() : boolean
        + hasCaseSensitivity() : boolean
        + hasOffset() : boolean
        + hasLimit() : boolean
        + hasMaximumTotalTextCharacters() : boolean
      }
    }
  }
  @enduml
  ```

  - LangChain4j `ToolSpecifications.parametersFrom(...)` creates the
    top-level tool parameter schema from method parameters. For the
    affected `AIToolSet` methods, the single method parameter is named
    `request`, so the top-level `request` wrapper is generated by the
    tool method signature.
  - LangChain4j `JsonSchemaElementUtils.jsonObjectOrReferenceSchemaFrom(...)`
    builds object schemas by iterating `type.getDeclaredFields()` and
    using `field.getName()` as the schema property name.
  - The same LangChain4j schema code skips static fields and synthetic
    implementation fields named `__$hits$__` or starting with `this$`.
    It does not skip ordinary private helper fields.
  - The schema code imports and uses Jackson `@JsonProperty` only to
    decide whether a field is required. It does not use
    `@JsonProperty` to rename schema fields and does not inspect
    getters for schema properties.
  - The schema code reads LangChain4j `@Description` from fields for
    schema descriptions.
  - LangChain4j `DefaultToolExecutor` binds incoming tool arguments by
    coercing the `request` argument to the request DTO type. For POJO
    DTOs, this coercion serializes the argument map and deserializes it
    to the DTO type, so the DTO's Jackson constructor annotations still
    control runtime binding.
  - Because schema generation is field-based but runtime binding is
    Jackson-based, stable tool parameter names require matching Java
    field names and `@JsonProperty` constructor names.
  - `ReadNodesWithDescendantsRequest` currently stores normalized
    default values in `fullContentDepth`, `summaryDepth`, and
    `maximumTotalTextCharacters`, so it uses separate `has...` fields
    to remember whether the caller supplied optional values.
  - `SearchNodesRequest` currently follows the same pattern for
    `matchingMode`, `caseSensitivity`, `offset`, `limit`, and
    `maximumTotalTextCharacters`.
  - `ReadNodesWithDescendantsTool` consumes
    `getSummaryDepth()` and `getMaximumTotalTextCharacters()` and uses
    `hasSummaryDepth()` in tool-call summaries.
  - `SearchNodesTool` consumes `getMaximumTotalTextCharacters()` and
    uses `hasMatchingMode()`, `hasCaseSensitivity()`, `hasOffset()`,
    and `hasLimit()` in tool-call summaries.
  - `AIToolSetToolExposureTest` currently verifies method exposure but
    does not assert nested request schema field names.
- **Analysis:**
  - The tool request DTO fields own the generated nested parameter
    names because LangChain4j schema generation is field-based.
  - Keep `request` because it is the consistent shape produced by the
    one-argument tool methods and applies across tool callers.
  - Rename `summaryDepth` to `additionalSummaryDepth` because the value
    is added after `fullContentDepth` and controls summary-only
    descendants.
  - Rename `maximumTotalTextCharacters` to `maxCharacters` because the
    shorter name is the intended tool budget field.
  - Remove helper backing fields by storing nullable request values
    and deriving presence from `field != null` so that helper state
    remains available to tools without appearing in schemas.
  - Do not replace helper backing fields with `Optional<T>` request
    fields: LangChain4j schema generation is field-based and does not
    special-case `Optional<T>`, so it would expose the wrong parameter
    shape.
  - Keep presence methods only where execution or summaries still need
    caller-supplied-vs-default distinction; remove the unused maximum
    character presence state.
- **Design:**
  ```plantuml
  @startuml
  set separator none
  package "org.freeplane.plugin.ai.tools" {
    package read {
      class ReadNodesWithDescendantsRequest {
        {static} DEFAULT_FULL_CONTENT_DEPTH : int
        {static} DEFAULT_ADDITIONAL_SUMMARY_DEPTH : int
        {static} DEFAULT_MAX_CHARACTERS : int
        - mapIdentifier : String
        - nodeIdentifiers : List<String>
        - contextSections : List<ContextSection>
        - fullContentDepth : Integer
        - additionalSummaryDepth : Integer
        - maxCharacters : Integer
        + getFullContentDepth() : Integer
        + getAdditionalSummaryDepth() : Integer
        + getMaxCharacters() : Integer
        + hasFullContentDepth() : boolean
        + hasAdditionalSummaryDepth() : boolean
      }
    }
    package search {
      class SearchNodesRequest {
        {static} DEFAULT_LIMIT : int
        {static} DEFAULT_OFFSET : int
        {static} DEFAULT_MAX_CHARACTERS : int
        {static} DEFAULT_MATCHING_MODE : SearchMatchingMode
        {static} DEFAULT_CASE_SENSITIVITY : SearchCaseSensitivity
        {static} DEFAULT_CONTENT_REQUEST : NodeContentRequest
        - mapIdentifier : String
        - queryText : String
        - subtreeRootNodeIdentifiers : List<String>
        - nodeContentRequestForSearch : NodeContentRequest
        - matchingMode : SearchMatchingMode
        - caseSensitivity : SearchCaseSensitivity
        - resultSections : List<SearchResultSection>
        - offset : Integer
        - limit : Integer
        - maxCharacters : Integer
        + getMaxCharacters() : Integer
        + hasMatchingMode() : boolean
        + hasCaseSensitivity() : boolean
        + hasOffset() : boolean
        + hasLimit() : boolean
      }
    }
  }
  @enduml
  ```

  - `ReadNodesWithDescendantsRequest` target request fields:

    ```json
    {
      "request": {
        "mapIdentifier": "<map id from getSelectedMapAndNodeIdentifiers>",
        "nodeIdentifiers": ["<node id>"],
        "contextSections": [
          "BREADCRUMB_PATH",
          "PARENT_SUMMARY",
          "QUALIFIERS",
          "HYPERLINK",
          "OUTGOING_CONNECTORS",
          "INCOMING_CONNECTORS",
          "CLONE_METADATA"
        ],
        "fullContentDepth": 0,
        "additionalSummaryDepth": 1,
        "maxCharacters": 65536
      }
    }
    ```

  - `SearchNodesRequest` target request fields:

    ```json
    {
      "request": {
        "mapIdentifier": "<map id from getSelectedMapAndNodeIdentifiers>",
        "queryText": "<text>",
        "subtreeRootNodeIdentifiers": ["<node id>"],
        "nodeContentRequestForSearch": {},
        "matchingMode": "CONTAINS | EQUALS | REGULAR_EXPRESSION",
        "caseSensitivity": "CASE_INSENSITIVE | CASE_SENSITIVE",
        "resultSections": ["BREADCRUMB_PATH"],
        "offset": 0,
        "limit": 200,
        "maxCharacters": 65536
      }
    }
    ```

  - `ReadNodesWithDescendantsRequest` stores
    `fullContentDepth`, `additionalSummaryDepth`, and `maxCharacters`
    as nullable boxed fields, not `Optional<T>` fields. Getters return
    existing defaults when the stored field is `null`.
    `hasFullContentDepth()` and `hasAdditionalSummaryDepth()` return
    whether the corresponding nullable field was supplied. No maximum
    character presence method remains unless a direct runtime use is
    found during implementation.
  - `SearchNodesRequest` stores `matchingMode`, `caseSensitivity`,
    `offset`, `limit`, and `maxCharacters` as nullable fields, not
    `Optional<T>` fields. Getters return existing defaults and keep the
    current non-negative normalization for `offset` and `limit`.
    Presence methods derive from the nullable fields for matching mode,
    case sensitivity, offset, and limit. No maximum character presence
    method remains unless a direct runtime use is found during
    implementation.
  - Remove the old helper backing fields from both request classes:
    no declared field name may start with `has`.
  - Update `ReadNodesWithDescendantsTool` to use
    `getAdditionalSummaryDepth()` and `getMaxCharacters()`. Tool-call
    summary text uses the label `additionalSummaryDepth` when the
    caller supplied that field.
  - Update `SearchNodesTool` to use `getMaxCharacters()` and retain
    the existing summary behavior for matching mode, case sensitivity,
    offset, and limit.
  - Update direct constructor call sites and tests to use the renamed
    constructor parameters and getters. Do not keep old getters or old
    constructor JSON property names.
  - Leave `AIToolSet`, `ModelContextProtocolToolRegistry`, and
    `ModelContextProtocolToolDispatcher` behavior unchanged except for
    tests that observe the generated tool schema or dispatch binding.
- **Test specification:**
  - **Automated tests:**
    - `AIToolSetToolExposureTest`
      - `contextGatheringRequestSchemasUseToolParameterStructures`:
        `readNodesWithDescendants`,
        `readNodesWithDescendantsAsPlainText`, and `searchNodes` keep
        top-level `request`; nested request schemas expose
        `additionalSummaryDepth` and `maxCharacters` where applicable;
        nested request schemas omit `summaryDepth`,
        `maximumTotalTextCharacters`, and all `has...` fields.
      - `readNodesWithDescendantsAsPlainText_isExposedAsToolMethod`:
        the plain-text read tool remains exposed with the request DTO
        parameter signature.
    - `ModelContextProtocolToolDispatcherTest`
      - `dispatchBindsCurrentToolRequestFieldNames`: dispatcher
        binding through LangChain4j accepts request JSON using
        `additionalSummaryDepth` and `maxCharacters` and the resulting
        DTO getters and presence methods return the expected values.
    - `ReadNodesWithDescendantsRequestTest`
      - `defaultsAndPresenceUseNullableRequestFields`: omitted optional
        read fields return current defaults and report presence as
        false; supplied `fullContentDepth` and
        `additionalSummaryDepth` values report presence as true.
    - `SearchNodesRequestTest`
      - `defaultsNormalizationAndPresenceUseNullableRequestFields`:
        omitted optional search fields return current defaults and
        report presence as false; supplied matching, case, offset, and
        limit values report presence as true and preserve current
        offset/limit normalization.
    - Existing `ReadNodesWithDescendantsToolTest`,
      `ReadNodesWithDescendantsToolSummaryTest`, `SearchNodesToolTest`,
      and `SearchNodesToolSummaryTest` continue to cover traversal,
      search, budget, and summary behavior after the request field
      rename.
