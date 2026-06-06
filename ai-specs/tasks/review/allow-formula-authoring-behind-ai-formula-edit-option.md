# Task: Allow formula authoring behind explicit AI formula-edit permission
- **Task Identifier:** 2026-06-05-formula-authoring
- **Scope:** Track formula authoring across two increments: a
  baseline preview/apply architecture gated at
  `SCRIPT_EXECUTION`, then a follow-up that replaces that
  gate with explicit `ai_formula_editing_enabled` permission
  layered on editing availability. Across both increments,
  AI updates existing formulas, converts supported existing
  fields between formula and non-formula states, and creates
  formulas on newly created nodes only through dedicated
  preview/apply after those nodes exist in the map. Align
  attached-editor guidance, authorization, tool
  descriptions, preferences, and enforcement with the
  governing formula-authoring policy of each increment.
- **Motivation:** Current formula handling is inconsistent.
  Some editing paths reject formula content outright, some
  expose formula state as non-editable, and some
  attribute-edit paths already allow formula writes without
  a clear governing policy. This task aligns those surfaces
  under one explicit formula-authoring contract and its
  follow-up permission split.
- **Scenario:** Across both subtasks, AI authors formulas on
  existing nodes only through ordered
  `previewFormulaUpdates(...)` / `applyFormulaUpdates(...)`
  after the nodes exist in the map; create-path formulas are
  rejected. Preview compiles and evaluates before
  persistence, failures keep original content unchanged and
  return diagnostics, and the AI-visible schema models
  formula state separately from base content type. Subtask 1
  establishes the baseline script-execution-gated path and
  code-host boundary. Subtask 2 replaces that gate with
  explicit formula-edit permission layered on editing
  availability and aligns chat, MCP, and attached-editor
  exposure.
- **Constraints:**
  - Shared constraints across both subtasks:
    - The dedicated formula-update flow must support formula
      <-> non-formula conversion in both directions for the
      supported textual and attribute fields.
    - The affected AI tool contracts must stop modeling
      formula as a standalone base content type.
    - Direct formula creation is not allowed in
      `createNodes(...)` or `createSummary(...)`.
    - To create a formula on a new node, AI must first
      create the node, then apply the formula through the
      update flow after the node exists in the map.
    - Formula-targeting updates must preserve caller-
      specified order because dependency order can change
      evaluation results.
    - Formula-targeting previews must compile and evaluate
      before map content is updated.
    - If formula validation fails, the preview must be
      rejected and the original content must remain
      unchanged.
    - Formula validation failures must return diagnostics
      that support AI repair and retry.
    - Separate per-formula test creation is not required in
      this task.
    - Plausibility review is required after successful
      temporary formula evaluation and before persistence.
    - This task must not add formula execution through the
      map-editing tools as a persisted side effect of failed
      validation.
    - Tool and schema changes must stay consistent across
      chat and MCP exposure for the affected tool surface.
    - Remove inconsistent loopholes instead of preserving
      parallel old and new behavior.
  - Gate-specific constraints stay with the relevant
    subtask.
- **Briefing:** Shared code is concentrated in
  `freeplane_plugin_ai`, with baseline formula evaluation
  crossing into `freeplane_plugin_script` through
  `AiCodeHostService`. Common map-editing entry points
  remain `AIToolSet.edit(...)`, `AIToolSet.createNodes(...)`,
  `AIToolSet.createSummary(...)`, and
  `AIToolSet.fetchNodesForEditing(...)`. Gate-specific
  current-state findings and design deltas live in the
  subtasks.

## Subtask: Baseline formula preview/apply flow and code-host boundary at script-execution level
- **Status:** review
- **Scope:** Implement the baseline increment of the
  task-level formula-authoring contract using
  `SCRIPT_EXECUTION` as the formula-authoring gate for map
  formula preview/apply and attached-editor formula
  `writeCode` / `compileCode` authorization.
- **Motivation:** Establish the preview/apply architecture,
  formula DTOs and tooling, code-host evaluation boundary,
  and baseline attached-editor alignment before the explicit
  formula-edit-permission follow-up in Subtask 2.
- **Scenario:** Within the task-level workflow, this
  increment allows formula authoring only at
  `SCRIPT_EXECUTION`; lower availability states expose
  formula-backed fields as non-editable or reject
  formula-targeting requests.
- **Constraints:**
  - All task-level shared constraints apply.
  - Formula authoring in this increment remains unavailable
    below effective `SCRIPT_EXECUTION`.
- **Briefing:** This increment owns the initial
  `FormulaUpdateTool` flow,
  `AiCodeHostService.evaluateFormula(...)`, OSGi boundary
  reuse for formula preview, and the baseline attached-
  editor guidance and authorization changes.
- **Research:**
  - `fetchNodesForEditing` exposes formula text as
    `ContentType.FORMULA`, but `EditableContentReader` currently marks
    formula-backed text and attributes as non-editable.
  - In core Freeplane, formula is not stored as a separate content type.
    Formula detection comes from `TextController.isFormula(...)` and
    `FormulaUtils.containsFormula(...)`.
  - Formula detection is orthogonal to base textual format: formulas can
    be detected in plain text and in HTML content, and attributes do not
    have a separate content-type field at all.
  - Current formula detection APIs are boolean-style checks
    (`TextController.isFormula(...)`, `FormulaUtils.containsFormula(...)`),
    not multi-mode formula classifications.
  - `TextualContentEditor` currently rejects formula content for both
    initial content writes and edits of existing textual content.
  - `AttributesContentEditor` currently performs no formula-specific
    validation, so attribute `ADD` and `REPLACE` already allow formula
    writes without any script-execution gate.
  - `createNodes` and `createSummary` reuse `NodeContentWriteRequest`
    and `NodeContentApplier`, and `NodeCreationHierarchyBuilder`
    applies content while the new nodes are still being assembled.
    That path is not equivalent to update-time validation on an
    existing map node.
  - Current create-path textual guarding rejects only
    `ContentType.FORMULA`, but that does not prohibit raw values that
    Freeplane runtime would still recognize as formulas from their text
    content.
  - `BatchEditTool` resolves targets in request item order and applies
    compatible edits sequentially. That existing sequential structure is
    relevant because formula dependency order can matter.
  - Attached-editor code tools use content-type strings such as
    `text/x-freeplane-formula-groovy` to identify the editor host kind.
    That differs from the map-editing `ContentType` enum, which models
    rendered base content.
  - `AiCodeOperationAuthorizer` currently authorizes attached-editor
    `writeCode` and `compileCode` at the editing level regardless of
    whether the attached content is a formula.
  - `AiCodeToolSet.systemMessageForChat(...)` currently tells the model
    to keep attached formulas read-only, which conflicts with the new
    formula-authoring policy.
  - `FormulaEditor.submitEditedText(...)` already uses
    `FormulaSubmitValidationSupport.validateSubmittedFormula(...)` for
    pre-commit formula evaluation, and that helper already wraps
    `FormulaUtils.validateFormula(...)` and returns
    `AiChatCodeOperationResult`.
  - `FormulaEditor.compileCode(...)` already reuses
    `ScriptingEngine.compileGroovyScriptForDiagnostics(...)` for
    compile-only formula checks.
  - `FormulaSubmitValidationSupport` is package-private in
    `freeplane_plugin_formula`, so it is not itself a reusable
    cross-plugin boundary.
  - AI-owned script compile/run already crosses from
    `freeplane_plugin_ai` into `freeplane_plugin_script` through the
    core `AiCodeHostService` OSGi service interface rather than a
    direct plugin reference.
  - Current `AiOwnedScriptHostService` runs against the currently
    selected node and `aiStartedPermissions()`. Current
    `RunScriptRequest` does not identify a target map/node and does not
    express formula-permission evaluation.
  - `SingleEditorAttachmentService` already manages fingerprints,
    current-state checks, and repair submission for one attached live
    editor, but its request/response types are editor-host-specific.
  - Core `freeplane` and `freeplane_api` compile for Java 8, so shared
    AI code-host contract changes in those modules must stay
    Java-8-compatible.
  - Existing backlog task
    `ai-specs/tasks/backlog/edit-tool-content-type-policy.md`
    explicitly kept formula migration out of scope, so this task cannot
    rely on that backlog work as its prerequisite if it owns the
    formula-model split.

  ```plantuml
  @startuml
  actor "LLM" as LLM
  participant "fetchNodesForEditing" as Fetch
  participant "EditableContentReader" as Reader
  participant "TextualContentEditor" as TextEditor
  participant "AttributesContentEditor" as AttrEditor
  participant "AiCodeOperationAuthorizer" as CodeAuth
  participant "AiCodeToolSet" as CodeTools

  LLM -> Fetch: request editable formula field
  Fetch -> Reader: read editable content
  Reader --> LLM: ContentType.FORMULA + isEditable=false

  LLM -> TextEditor: edit textual formula content
  TextEditor --> LLM: reject formula edits

  LLM -> AttrEditor: add or replace attribute value
  AttrEditor --> LLM: accepts raw formula text

  LLM -> CodeAuth: attached formula writeCode / compileCode at EDITING
  CodeAuth --> LLM: allow
  CodeTools --> LLM: system guidance says formula is read-only
  @enduml
  ```
- **Analysis:**
  - Formula creation on newly created nodes cannot use the create-path
    request itself because pre-persistence evaluation and plausibility
    review require the node to exist in final map context.
  - Dedicated formula preview and apply tools are required because the
    user wants plausibility review before persistence, and the current
    `edit(...)` contract is mutation-only.
  - The formula-update flow must support formula <-> non-formula
    conversion in both directions because the user wants formula
    authoring on existing nodes, not only updates of already-formula
    fields.
  - Formula state should be modeled separately from base content type
    in the AI tool contract because that matches Freeplane semantics
    better than treating formula as a standalone content type.
  - The affected AI read and write schemas should expose that split
    consistently because a mixed model would keep contract semantics
    ambiguous.
  - Formula state should use a boolean-like contract shape because
    current Freeplane formula semantics are binary.
  - Formula-targeting writes should validate before persistence because
    the user wants immediate compile-and-run checking without leaving a
    broken formula on the node.
  - Failed validation should return diagnostics for iterative AI repair
    instead of ending the workflow after the first attempt.
  - Separate per-formula tests are not required because formula
    behavior depends on complex map context rather than an isolated
    test harness.
  - Plausibility review should be required after successful temporary
    evaluation and before persistence because a formula can be valid yet
    still yield an implausible result in node context.
  - Formula update order is semantically relevant because dependencies
    can point to nodes created or updated earlier in the same workflow.
  - The formula task should own that model split instead of depending
    on the backlog content-type-policy task because formula is not just
    another base content type.
  - Attached-editor code tools should keep their existing host-kind
    content-type strings because those identify editor host type, not
    map base content, but their formula write/compile authorization and
    guidance must follow the same script-execution policy.
  - Formula preview for map updates should reuse the existing
    `AiCodeHostService` OSGi service boundary instead of adding a
    direct `freeplane_plugin_ai` -> `freeplane_plugin_script`
    dependency.
  - Current `readCode(...)`, `writeCode(...)`, `compileCode(...)`, and
    `runScript(...)` are not sufficient as-is because formula preview
    needs explicit target map/node selection and formula-permission
    evaluation rather than current-selected-node AI-script execution.
  - The smallest architectural change is to extend
    `AiCodeHostService` with formula-evaluation support in core
    Java-8-compatible request/response types, then implement that in
    the script-plugin service already resolved through OSGi.
- **Design:**
  - Add one explicit formula-authoring policy for the current
    map-editing tool surface based on effective
    `ToolAvailabilityLevel.SCRIPT_EXECUTION`.
  - Replace formula-targeting use of `edit(...)` with a dedicated
    two-tool flow:
    - `previewFormulaUpdates(FormulaUpdatePreviewRequest request)`
    - `applyFormulaUpdates(FormulaUpdateApplyRequest request)`
  - Keep `edit(...)` for non-formula editing.
  - Add `FormulaUpdateTool` in
    `org.freeplane.plugin.ai.tools.formula`.
    `AIToolSet.previewFormulaUpdates(...)` and
    `AIToolSet.applyFormulaUpdates(...)` delegate to that tool.
  - Extend the core `AiCodeHostService` interface with
    `evaluateFormula(EvaluateFormulaRequest request)` returning
    `AiChatCodeOperationResult`.
  - Add Java-8-compatible core request type
    `EvaluateFormulaRequest` with explicit `mapIdentifier`,
    `nodeIdentifier`, and `formulaText` fields.
  - Implement `evaluateFormula(...)` in
    `freeplane_plugin_script` `AiOwnedScriptHostService` by resolving
    the requested node and calling the existing
    `FormulaUtils.validateFormula(...)` path with
    `ScriptingPermissions.getFormulaPermissions()`.
  - Route map formula preview through the existing OSGi-resolved
    `AiCodeHostService` boundary. `FormulaUpdateTool.previewFormulaUpdates(...)`
    uses `evaluateFormula(...)` instead of a direct script-plugin helper
    dependency.
  - Keep attached-editor formula submit validation aligned through the
    same lower-level `FormulaUtils.validateFormula(...)` path in the
    script plugin rather than introducing a second evaluation
    algorithm.
  - Keep `freeplane_plugin_ai` decoupled from
    `freeplane_plugin_script`; do not add a direct implementation
    dependency between those plugins for formula validation.
  - Extend the same policy to attached-editor formulas by requiring
    `SCRIPT_EXECUTION` for attached-editor formula `writeCode` and
    `compileCode`, while leaving attached script authorization rules
    unchanged.
  - Update tool descriptions and parameter descriptions so the
    supported formula behavior, ordering, preview/apply flow, and
    gating are stated directly instead of being implicit in code.
  - Remove `FORMULA` from
    `org.freeplane.plugin.ai.tools.content.ContentType`. The remaining
    enum values stay `PLAIN_TEXT`, `MARKDOWN`, `HTML`, and `LATEX`.
  - Keep attached-editor code-host content-type strings such as
    `text/x-freeplane-formula-groovy` unchanged because they identify
    editor host kind, not map base content.
  - Replace AI-visible `ContentType.FORMULA` usage in the affected
    fetch and create contracts with separate base-content and
    formula-state fields.
  - Structural request/response fields and implementation class design
    are defined in the PlantUML class diagrams below.
  - Request-field rules:
    - `FormulaUpdateItem.editedElement` is limited to `TEXT`,
      `DETAILS`, `NOTE`, and `ATTRIBUTES`.
    - `TEXT`, `DETAILS`, and `NOTE` allow `REPLACE` only.
    - `ATTRIBUTES` allows `ADD` and `REPLACE` only in this flow.
    - `originalContentType` and `originalIsFormula` are required for
      `TEXT`, `DETAILS`, and `NOTE`.
    - `targetIsFormula` is required for every `FormulaUpdateItem`.
    - `originalIsFormula` is optional for `ATTRIBUTES`; when provided on
      `REPLACE`, it is validated against the current attribute value.
    - `createNodes(...)` and `createSummary(...)` reject formula values
      in text, details, note, and attributes under this task.
    - Create-path rejection is value-based, not enum-based: after the
      same create-path normalization that would be used for storage,
      any candidate value that runtime formula detection would classify
      as a formula for that field is rejected.
    - This task does not add general base-content migration for existing
      textual edits beyond the current non-formula rules.
  - Preview and apply semantics:
    - `previewFormulaUpdates(...)` expands targets in request item order
      and validates them sequentially in that same order.
    - Earlier validated preview results are visible to later preview
      items in the same request so dependency-sensitive formula order is
      modeled correctly.
    - When one preview target fails validation, later targets are
      reported as `BLOCKED_BY_PREVIOUS_FAILURE`, and no `previewId` is
      returned.
    - When all expanded targets are `VALIDATED`, the response returns a
      `previewId`, evaluation results, and diagnostics-free candidate
      values for model plausibility review.
    - `applyFormulaUpdates(...)` accepts only a previously validated
      `previewId`.
    - `applyFormulaUpdates(...)` does not compare current map state with
      preview-time source snapshots.
    - `applyFormulaUpdates(...)` persists the exact validated candidate
      values in preview order.
    - `applyFormulaUpdates(...)` must still fail gracefully instead of
      crashing when a target node no longer exists or an attribute
      `REPLACE` target can no longer be resolved at apply time.
  - Validation and write rules:
    - below `SCRIPT_EXECUTION`, formula-backed editable text and
      formula-backed editable attributes report `isEditable=false`, and
      formula-targeting preview or apply requests fail validation.
    - at `SCRIPT_EXECUTION`, formula-backed editable text and
      formula-backed editable attributes report `isEditable=true`.
    - when `targetIsFormula=true`, the written raw value must satisfy
      `TextController.isFormula(...)`.
    - when `targetIsFormula=false`, the written raw value must not
      satisfy `TextController.isFormula(...)`.
    - textual updates continue to validate current base content through
      `originalContentType`; this task does not broaden existing base
      content migration rules.
    - create-path text, details, note, and attributes reject formula
      values and require follow-up formula updates after node creation.
    - That rejection must use the same runtime formula predicate that
      would later decide whether the stored value is a formula in that
      field context, rather than relying only on content-type metadata.
    - every formula-targeting preview compiles and evaluates before any
      map update is committed.
    - compile or evaluation failure leaves the original content
      unchanged and returns diagnostics in the preview response.
    - after successful temporary evaluation, the model must review the
      result for plausibility in node context before calling
      `applyFormulaUpdates(...)`.
    - tool descriptions and attached-formula guidance must tell the
      model to use validation diagnostics or implausible results to
      repair and re-preview instead of giving up after the first failed
      attempt.
  - Reader and writer changes:
    - `EditableContentReader` reports base content type plus formula
      state instead of `ContentType.FORMULA`.
    - `TextualContentEditor` and `AttributesContentEditor` gain
      `previewFormulaUpdate(...)` and `applyFormulaUpdate(...)`
      helpers used by the preview/apply flow.
    - `NodeContentApplier` and create-path validation reject formula
      values during node creation and summary creation.
    - Add an AI-plugin preview store named
      `FormulaUpdatePreviewStore` to retain validated preview sessions
      between `previewFormulaUpdates(...)` and `applyFormulaUpdates(...)`.
  - Attached-editor alignment:
    - `AiCodeOperationAuthorizer` becomes content-type-aware for the
      attached editor host. When the attached editor content type is
      `text/x-freeplane-formula-groovy`, `writeCode` and `compileCode`
      require `SCRIPT_EXECUTION`.
    - `AiCodeToolSet.systemMessageForChat(...)` for attached formulas
      no longer says the formula is read-only. It instead states that
      attached formula authoring is available only when the current tool
      availability exposes `writeCode` and `compileCode`, while keeping
      the existing value-computing and no-obvious-UI-driving guidance.
    - `Resources_en.properties` tool-availability tooltips must mention
      formula authoring as part of script-execution-level capability.

  ```plantuml
  @startuml
  set separator none
  package "org.freeplane.plugin.ai.tools.content" {
    enum ContentType {
      PLAIN_TEXT
      MARKDOWN
      HTML
      LATEX
    }

    class EditableText {
      + contentType : ContentType
      + isFormula : Boolean
      + isEditable : Boolean
    }

    class EditableAttribute {
      + isFormula : Boolean
      + isEditable : Boolean
    }
  }

  package "org.freeplane.plugin.ai.tools.formula" {
    class FormulaUpdatePreviewRequest {
      + mapIdentifier : String
      + userSummary : String
      + items : List<FormulaUpdateItem>
    }

    class FormulaUpdateItem {
      + nodeIdentifiers : List<String>
      + editedElement : EditedElement
      + originalContentType : ContentType
      + originalIsFormula : Boolean
      + targetIsFormula : Boolean
      + value : String
      + index : Integer
      + operation : EditOperation
      + targetKey : String
    }

    class FormulaUpdatePreviewResponse {
      + mapIdentifier : String
      + previewId : String
      + items : List<FormulaUpdatePreviewResultItem>
    }

    class FormulaUpdatePreviewResultItem {
      + itemIndex : Integer
      + nodeIdentifier : String
      + editedElement : EditedElement
      + status : FormulaUpdatePreviewStatus
      + candidateValue : String
      + evaluationResult : String
      + compilerDiagnostics : List<String>
      + errorMessage : String
      + lineNumber : Integer
    }

    enum FormulaUpdatePreviewStatus {
      VALIDATED
      FAILED_VALIDATION
      BLOCKED_BY_PREVIOUS_FAILURE
    }

    class FormulaUpdateApplyRequest {
      + mapIdentifier : String
      + previewId : String
    }

    class FormulaUpdateApplyResponse {
      + mapIdentifier : String
      + items : List<FormulaUpdateApplyResultItem>
    }

    class FormulaUpdateApplyResultItem {
      + itemIndex : Integer
      + nodeIdentifier : String
      + editedElement : EditedElement
      + status : FormulaUpdateApplyStatus
      + errorMessage : String
      + updatedContent : NodeContentItem
    }

    enum FormulaUpdateApplyStatus {
      APPLIED
      FAILED
    }
  }

  EditableText --> ContentType
  FormulaUpdateItem --> ContentType
  FormulaUpdatePreviewResponse --> FormulaUpdatePreviewResultItem
  FormulaUpdateApplyResponse --> FormulaUpdateApplyResultItem
  @enduml
  ```

  ```plantuml
  @startuml
  set separator none
  package "org.freeplane.plugin.ai.tools" {
    class AIToolSet {
      + previewFormulaUpdates(request : FormulaUpdatePreviewRequest) : FormulaUpdatePreviewResponse
      + applyFormulaUpdates(request : FormulaUpdateApplyRequest) : FormulaUpdateApplyResponse
      + edit(request : EditRequest) : List<EditResultItem>
      + createNodes(request : CreateNodesRequest) : CreateNodesResponse
      + createSummary(request : CreateSummaryRequest) : CreateSummaryResponse
    }
  }

  package "org.freeplane.plugin.ai.tools.formula" {
    class FormulaUpdateTool {
      + previewFormulaUpdates(request : FormulaUpdatePreviewRequest) : FormulaUpdatePreviewResponse
      + applyFormulaUpdates(request : FormulaUpdateApplyRequest) : FormulaUpdateApplyResponse
    }

    class FormulaUpdatePreviewStore {
      + save(previewResponse : FormulaUpdatePreviewResponse)
      + load(previewId : String) : FormulaUpdatePreviewResponse
      + remove(previewId : String)
    }
  }

  package "org.freeplane.plugin.ai.tools.content" {
    class EditableContentReader {
      + readEditableContent(nodeModel, request) : EditableContent
    }

    class NodeContentApplier {
      + apply(nodeModel, content)
    }
  }

  package "org.freeplane.plugin.ai.tools.edit" {
    class TextualContentEditor {
      + setInitialContent(nodeModel, content)
      + previewFormulaUpdate(nodeModel, item, textController) : FormulaUpdatePreviewResultItem
      + applyFormulaUpdate(nodeModel, item, candidateValue) : NodeContentItem
    }

    class AttributesContentEditor {
      + setInitialContent(nodeModel, attributesContent)
      + previewFormulaUpdate(nodeModel, item) : FormulaUpdatePreviewResultItem
      + applyFormulaUpdate(nodeModel, item, candidateValue) : NodeContentItem
    }
  }

  package "org.freeplane.features.ai.code" {
    interface AiCodeHostService {
      + evaluateFormula(request : EvaluateFormulaRequest) : AiChatCodeOperationResult
    }

    class EvaluateFormulaRequest {
      + mapIdentifier : String
      + nodeIdentifier : String
      + formulaText : String
    }

    class AiChatCodeOperationResult {
      + successful : boolean
      + compilerDiagnostics : List<String>
      + result : String
      + errorMessage : String
      + lineNumber : Integer
    }
  }

  package "org.freeplane.plugin.script.ai" {
    class AiOwnedScriptHostService {
      + evaluateFormula(request : EvaluateFormulaRequest) : AiChatCodeOperationResult
    }
  }

  package "org.freeplane.plugin.script" {
    class FormulaUtils {
      + validateFormula(node, formulaText, outStream, errorHandler) : Object
    }

    class ScriptingEngine {
      + compileGroovyScriptForDiagnostics(script, permissions) : GroovyCompileResult
    }
  }

  package "org.freeplane.plugin.formula" {
    class FormulaEditor {
      + submitEditedText(editedText) : boolean
      + compileCode(request) : CompileCodeResponse
    }
  }

  package "org.freeplane.plugin.ai.tools.code" {
    class AiCodeOperationAuthorizer {
      + authorizedToolNames()
      + assertAuthorized(operation, codeId, host)
    }

    class AiCodeToolSet {
      + writeCode(request)
      + compileCode(request)
      + systemMessageForChat(input)
    }
  }

  package "org.freeplane.plugin.ai.tools.create" {
    class NodeCreationHierarchyBuilder {
      + buildHierarchy(items, mapModel) : NodeCreationHierarchy
    }
  }

  AIToolSet --> FormulaUpdateTool
  FormulaUpdateTool --> FormulaUpdatePreviewStore
  FormulaUpdateTool --> EditableContentReader
  FormulaUpdateTool --> TextualContentEditor
  FormulaUpdateTool --> AttributesContentEditor
  FormulaUpdateTool --> AiCodeHostService
  AiOwnedScriptHostService ..|> AiCodeHostService
  AiOwnedScriptHostService --> FormulaUtils
  FormulaEditor --> ScriptingEngine
  NodeCreationHierarchyBuilder --> NodeContentApplier
  AiCodeToolSet --> AiCodeOperationAuthorizer
  @enduml
  ```

  ```plantuml
  @startuml
  actor "LLM" as LLM
  participant "createNodes / createSummary" as CreateTool
  participant "previewFormulaUpdates" as PreviewTool
  participant "FormulaUpdatePreviewStore" as PreviewStore
  participant "applyFormulaUpdates" as ApplyTool
  participant "AiCodeOperationAuthorizer" as CodeAuth

  LLM -> CreateTool: create nodes without formulas
  CreateTool --> LLM: created nodeIdentifiers

  LLM -> PreviewTool: preview ordered formula updates
  alt validation failure
    PreviewTool --> LLM: FAILED_VALIDATION / BLOCKED results
  else all validated
    PreviewTool -> PreviewStore: store validated preview
    PreviewTool --> LLM: previewId + evaluationResult values
    LLM -> LLM: plausibility review in node context
    LLM -> ApplyTool: applyFormulaUpdates(previewId)
    ApplyTool -> PreviewStore: load preview
    alt missing node or unresolved attribute target
      ApplyTool --> LLM: FAILED result without runtime crash
    else targets resolve successfully
      ApplyTool --> LLM: APPLIED results in preview order
    end
  end

  LLM -> CodeAuth: attached formula writeCode / compileCode
  alt attached formula and below SCRIPT_EXECUTION
    CodeAuth --> LLM: reject
  else attached formula at SCRIPT_EXECUTION
    CodeAuth --> LLM: allow
  end
  @enduml
  ```

  - This task no longer depends on
    `ai-specs/tasks/backlog/edit-tool-content-type-policy.md` as a
    prerequisite.
- **Test specification:**
  - Automated tests:
    - `EditableContentReaderTest`
      - verify editable text reports base `contentType` plus
        `isFormula`,
      - verify editable attributes report `isFormula`,
      - verify formula-backed text and attributes report
        `isEditable=false` below `SCRIPT_EXECUTION` and `true` at
        `SCRIPT_EXECUTION`.
    - formula evaluation service tests
      - verify `AiCodeHostService.evaluateFormula(...)` accepts explicit
        `mapIdentifier`, `nodeIdentifier`, and `formulaText` and
        returns `AiChatCodeOperationResult`,
      - verify `AiOwnedScriptHostService.evaluateFormula(...)` uses
        `ScriptingPermissions.getFormulaPermissions()`,
      - verify `FormulaUpdateTool.previewFormulaUpdates(...)` uses the
        code-host `evaluateFormula(...)` path,
      - verify `TextualContentEditor` formula helpers reject
        formula-targeting requests below `SCRIPT_EXECUTION`,
      - verify non-formula -> formula and formula -> non-formula helper
        validation follows `targetIsFormula`,
      - verify existing base-content compatibility rules still govern
        non-formula content migration,
      - verify attribute formula helpers follow the same gate.
    - `previewFormulaUpdates` tests
      - verify preview rejects requests below `SCRIPT_EXECUTION`,
      - verify preview expands targets in request order and validates
        them sequentially,
      - verify later preview items can depend on earlier validated
        preview items in the same request,
      - verify compile or evaluation failure returns
        `FAILED_VALIDATION` for the failed target,
      - verify later items become `BLOCKED_BY_PREVIOUS_FAILURE` after
        an earlier validation failure,
      - verify preview returns no `previewId` unless every expanded
        target is `VALIDATED`,
      - verify preview returns evaluation results needed for model
        plausibility review.
    - `applyFormulaUpdates` tests
      - verify apply rejects unknown preview IDs,
      - verify apply persists validated candidate values in preview
        order,
      - verify apply returns `FAILED` instead of crashing when a target
        node no longer exists,
      - verify apply returns `FAILED` instead of crashing when an
        attribute `REPLACE` target can no longer be resolved,
      - verify successful repaired re-preview and apply can then update
        the map.
    - create-path tests
      - verify `createNodes(...)` and `createSummary(...)` reject
        values that runtime formula detection would classify as
        formulas in text, details, note, and attributes, even when the
        request does not try to mark them as formula by metadata,
      - verify creating formula-bearing nodes requires a later
        `previewFormulaUpdates(...)` / `applyFormulaUpdates(...)` step
        after node creation.
    - code-tool authorization tests
      - verify attached formula `writeCode` and `compileCode` are
        rejected below `SCRIPT_EXECUTION`,
      - verify attached formula `readCode` remains readable at the
        lower availability levels that already permit attached-editor
        reading,
      - verify attached script authorization stays unchanged.
    - schema and description tests
      - verify `ContentType.FORMULA` is removed from the map-editing AI
        schema,
      - verify `previewFormulaUpdates(...)` and `applyFormulaUpdates(...)`
        expose the exact request and response fields from Design,
      - verify tool and parameter descriptions expose base content,
        formula state, ordered preview/apply semantics, and create-path
        rejection clearly,
      - verify attached-formula system guidance no longer says formulas
        are read-only.
  - Manual tests: N/A
- **Implementation notes:**
  - **Tradeoffs:**
    - `FormulaUpdatePreviewStore` is process-local shared state so a
      validated preview survives the separate `previewFormulaUpdates(...)`
      and `applyFormulaUpdates(...)` tool calls within the running
      Freeplane session.
    - Hidden-request `ChatPromptRunnerTest` SIGABRT reproduced under
      `~/.sdkman/candidates/java/21.0.5-zulu` but not under
      `~/.sdkman/candidates/java/21.0.8-zulu`. Temporary production-side
      test seams added during that investigation were removed again after
      retest on `21.0.8-zulu`; the only retained test-side mitigation was
      using a real `JPanel` owner instead of mocking `java.awt.Component`.

## Subtask: Explicit AI formula-edit permission and capability exposure
- **Status:** review
- **Scope:** Replace the script-execution-only formula-authoring gate
  from Subtask 1 with explicit `ai_formula_editing_enabled`
  permission layered on ordinary editing availability, and align
  attached-editor guidance, authorization, preferences, and related
  verification with that policy.
- **Motivation:** The baseline increment in Subtask 1 conflates
  formula authoring with script-execution-level availability. This
  follow-up separates formula editing from AI-owned script execution
  policy without changing the preview/apply architecture or OSGi
  service boundary.
- **Scenario:** Within the task-level workflow, this
  follow-up replaces the baseline `SCRIPT_EXECUTION`
  formula-authoring gate with editing availability plus
  `ai_formula_editing_enabled=true`, while leaving
  `ai_script_execution_policy` scoped to AI-owned script
  execution.
- **Constraints:**
  - All task-level shared constraints apply.
  - Formula authoring in the surfaces covered by this subtask must
    remain unavailable unless effective tool availability includes
    editing and `ai_formula_editing_enabled=true`.
  - `ai_script_execution_policy` must continue to govern only
    AI-owned script execution flow, not formula authoring.
- **Briefing:** This subtask preserves the preview/apply flow,
  code-host boundary, and formula-permission evaluation model from
  Subtask 1. Availability changes are controlled through
  `ToolAvailabilityLevel`, `AIChatService`,
  `AiCodeOperationAuthorizer`, and the new
  `ai_formula_editing_enabled` preference.
- **Research:**
  - `AiCodeOperationAuthorizer` currently authorizes attached-editor
    `writeCode` and `compileCode` at the editing level regardless of
    whether the attached content is a formula.
  - Existing AI preferences already separate `ai_tool_availability`
    from `ai_script_execution_policy`. The current
    `ai_script_execution_policy` label and tooltip describe only how
    AI-owned scripts are shown and whether AI may run them directly,
    while the default tool availability is `EDITING`.
  - MCP `tools/list` and MCP tool-metadata resource responses currently
    expose the static full tool registry, while
    `ModelContextProtocolToolCallAuthorizer` enforces availability only
    at MCP `tools/call` time.
- **Analysis:**
  - Attached-editor code tools should keep their existing host-kind
    content-type strings because those identify editor host type, not
    map base content, but their formula write/compile authorization and
    guidance must follow the same explicit formula-edit permission.
  - Formula authoring and formula preview evaluation should be governed
    by one explicit boolean preference because editing a formula and
    preview-evaluating it both exercise the same formula-permission
    boundary.
  - `ai_script_execution_policy` should not govern formula authoring
    because its current UI wording is about AI-owned script display and
    direct script execution, not formula editing.
  - Formula authoring should require ordinary editing availability plus
    explicit formula-edit permission, not `SCRIPT_EXECUTION`, because
    formula authoring is an editing capability with a separate
    permission boundary from AI-owned script execution.
  - MCP clients should not need to infer formula-edit permission only
    from MCP call-time rejection, because chat clients already learn
    effective capability from exposed tools, editability metadata, and
    guidance.
- **Design:**
  - Add one explicit formula-authoring policy for the current
    map-editing tool surface based on effective editing availability
    plus explicit `ai_formula_editing_enabled` permission.
  - Add boolean AI preference `ai_formula_editing_enabled` labeled
    `AI may edit formulas`, default `false`.
  - Extend the same policy to attached-editor formulas by requiring
    editing availability plus `ai_formula_editing_enabled=true` for
    attached-editor formula `writeCode` and `compileCode`, while
    leaving attached script authorization rules unchanged.
  - For MCP clients, keep MCP call-time authorization as enforcement
    but add explicit runtime capability signaling for formula
    authoring. MCP clients should learn formula-edit permission from an
    MCP-visible capability query plus normal editability metadata,
    rather than from static `tools/list` output plus failed tool calls
    alone.
  - Validation and write rules:
    - when editing availability is absent or
      `ai_formula_editing_enabled=false`, formula-backed editable text
      and formula-backed editable attributes report
      `isEditable=false`, and formula-targeting preview or apply
      requests fail validation.
    - when editing availability is present and
      `ai_formula_editing_enabled=true`, formula-backed editable text
      and formula-backed editable attributes report
      `isEditable=true`.
  - Attached-editor alignment:
    - `AiCodeOperationAuthorizer` becomes content-type-aware for the
      attached editor host. When the attached editor content type is
      `text/x-freeplane-formula-groovy`, `writeCode` and `compileCode`
      require editing availability plus
      `ai_formula_editing_enabled=true`.
    - `AiCodeToolSet.systemMessageForChat(...)` for attached formulas
      no longer says the formula is read-only. It instead states that
      attached formula authoring is available only when the current tool
      availability exposes `writeCode` and `compileCode` and formula
      editing permission is enabled, while keeping the existing
      value-computing and no-obvious-UI-driving guidance.
    - `preferences.xml`, `defaults.properties`, and
      `Resources_en.properties` must expose the new
      `ai_formula_editing_enabled` option clearly and must keep
      `ai_script_execution_policy` described as AI-owned script
      execution only.

  ```plantuml
  @startuml
  actor "LLM" as LLM
  participant "AiCodeOperationAuthorizer" as CodeAuth

  LLM -> CodeAuth: attached formula writeCode / compileCode
  alt editing unavailable or ai_formula_editing_enabled=false
    CodeAuth --> LLM: reject
  else editing available and ai_formula_editing_enabled=true
    CodeAuth --> LLM: allow
  end
  @enduml
  ```
- **Test specification:**
  - Automated tests:
    - `EditableContentReaderTest`
      - verify formula-backed text and attributes report
        `isEditable=false` when editing availability is absent or
        `ai_formula_editing_enabled=false`, and `true` only when
        editing availability is present and
        `ai_formula_editing_enabled=true`.
    - formula evaluation service tests
      - verify `TextualContentEditor` formula helpers reject
        formula-targeting requests when editing availability is absent
        or `ai_formula_editing_enabled=false`,
      - verify attribute formula helpers follow the same gate.
    - `previewFormulaUpdates` tests
      - verify preview rejects requests when editing availability is
        absent or `ai_formula_editing_enabled=false`,
    - code-tool authorization tests
      - verify attached formula `writeCode` and `compileCode` are
        rejected when editing availability is absent or
        `ai_formula_editing_enabled=false`,
      - verify attached formula `readCode` remains readable at the
        lower availability levels that already permit attached-editor
        reading,
      - verify attached script authorization stays unchanged,
      - verify `ai_script_execution_policy` changes do not alter
        formula authoring gates.
    - MCP exposure tests
      - verify MCP capability signaling reports formula authoring
        disabled when editing availability is absent or
        `ai_formula_editing_enabled=false`, and enabled only when
        editing availability is present and
        `ai_formula_editing_enabled=true`,
      - verify MCP call-time authorization still rejects
        formula-authoring tool calls when formula authoring is
        disabled.
    - schema and description tests
      - verify tool and parameter descriptions expose base content,
        formula state, ordered preview/apply semantics, create-path
        rejection, and the separate formula-edit permission clearly,
      - verify attached-formula system guidance no longer says formulas
        are read-only,
      - verify preferences/defaults/labels expose `AI may edit
        formulas` separately from AI-owned script execution policy.
  - Manual tests: N/A
- **Implementation notes:**
  - **Tradeoffs:**
    - MCP runtime capability signaling is exposed through dynamic
      `tools/list` and `mcp://tools` descriptions instead of a new MCP
      tool or resource identifier so Subtask 2 stays within the
      approved capability-signaling direction without introducing a new
      externally meaningful query name during implementation.
