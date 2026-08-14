# Task: Add AI support for filter script conditions
- **Task Identifier:** 2026-08-14-filter-ai
- **Scope:**
  Add attached-editor AI support for Groovy-based filter script
  conditions with formula-like restrictions. Users editing a filter
  script condition must be able to attach the draft to AI, let AI
  read, write, and compile the attached draft, and receive
  submit-time diagnostics and AI repair prompts comparable to the
  formula editor. Update attached-editor authorization, tool guidance,
  and preference wording to recognize filter-condition scripts.
  Preserve existing filter matching semantics, existing saved filter
  XML, existing reminder and generic script editors, and the AI-owned
  script host outside this task.
- **Motivation:**
  Filter script conditions already execute Groovy with formula-style
  permissions, but their editor is still an isolated string editor
  with no AI attachment path. That leaves a behaviorally similar
  scripting surface behind the current attached-formula workflow and
  makes filter-condition authoring harder to diagnose and repair.
- **Scenario:**
  - A user opens a script filter condition from the filter toolbar,
    filter composer, or another `FilterConditionEditor` caller.
  - The editor shows the current Groovy condition text and an AI
    attach toggle like the formula editor.
  - When attached, AI can inspect, rewrite, and compile the current
    draft through `host=ATTACHED_EDITOR`, but it cannot run the draft
    as a general script.
  - When the user submits the draft, Freeplane first checks Groovy
    compilation and then validates the condition against the current
    node context.
  - A valid condition still behaves like the current runtime contract:
    `Boolean` results are used directly and `Number` results are true
    when non-zero.
  - If compilation or validation fails, the editor shows diagnostics
    and can offer AI repair for the attached draft.
  - Reminder and other non-filter uses of `ScriptComboBoxEditor`
    continue to behave as they do today.
- **Constraints:**
  - Keep attached filter-condition scripts non-runnable and
    argument-free like attached formulas.
  - Keep filter-condition execution and validation under
    `ScriptingPermissions.getFormulaPermissions()`.
  - Do not change unrelated `ScriptComboBoxEditor` callers to the new
    editor path.
  - Preserve the existing runtime truthiness contract for
    `ScriptCondition`: accept `Boolean` and `Number`, and treat other
    result types as validation or execution errors.
  - Reuse the existing attached-editor infrastructure instead of
    introducing a second AI attachment channel.
  - If `ai_formula_editing_enabled` governs attached filter-condition
    scripts too, update user-visible wording so the setting description
    stays true.
  - Keep mode-controller execute blocking aligned with formula-style
    validation so AI-assisted condition checks do not introduce map
    edits.
- **Briefing:**
  Filter-condition editing starts in
  `org.freeplane.features.filter.FilterConditionEditor`, which asks an
  `IElementaryConditionController` for a value editor. The script
  filter path lives in `org.freeplane.plugin.script.filter`, where
  `ScriptConditionController` currently returns `ScriptComboBoxEditor`
  and `ScriptCondition` evaluates Groovy conditions at runtime. The
  attached-editor AI infrastructure lives across
  `freeplane_plugin_ai`, `freeplane_plugin_formula`, and
  `freeplane_plugin_script`: `FormulaEditor` is the reference for a
  compile-only attached editor, `ScriptEditorPanel` is the reference
  for a runnable attached script editor, `SingleEditorAttachmentService`
  owns the single attached editor, and `AiCodeOperationAuthorizer`,
  `AiCodeToolSet`, and `ModelContextProtocolToolRegistry` gate and
  describe attached-editor tool behavior by content type.
- **Research:**
  ```plantuml
  @startuml
  set separator none
  package org.freeplane {
    package features.filter {
      class FilterConditionEditor {
        -setValuesEditor()
      }
      interface IElementaryConditionController {
        +getValueEditor(selectedProperty, selectedCondition): ComboBoxEditor
        +createCondition(selectedItem, simpleCond, value, matchCase, approximateMatching, ignoreDiacritics): ASelectableCondition
      }
    }
    package plugin.script {
      class ScriptComboBoxEditor {
        +getEditorComponent(): Component
        -editScript(selectAll)
      }
      class ScriptEditorPanel {
        +compileCode(request): CompileCodeResponse
        +runCode(request): RunCodeResponse
      }
    }
    package plugin.script.filter {
      class ScriptConditionController {
        +getValueEditor(selectedProperty, selectedCondition): ComboBoxEditor
        +createCondition(selectedItem, simpleCond, value, matchCase, approximateMatching, ignoreDiacritics): ASelectableCondition
      }
      class ScriptCondition {
        +getScript(): String
        +checkNode(node): boolean
      }
    }
    package plugin.formula {
      class FormulaEditor {
        +compileCode(request): CompileCodeResponse
        -submitEditedText(editedText): boolean
      }
    }
    package plugin.ai.code {
      class SingleEditorAttachmentService {
        +attachEditor(editor, contentType): AiChatAttachment
      }
    }
    package plugin.ai.tools.code {
      class AiCodeOperationAuthorizer {
        +assertAuthorized(operation, host)
      }
      class AiCodeToolSet {
        +systemMessageForChat(input): String
      }
    }
  }
  FilterConditionEditor --> IElementaryConditionController
  ScriptConditionController ..|> IElementaryConditionController
  ScriptConditionController --> ScriptComboBoxEditor
  ScriptConditionController --> ScriptCondition
  FormulaEditor --> SingleEditorAttachmentService
  ScriptEditorPanel --> SingleEditorAttachmentService
  AiCodeToolSet --> AiCodeOperationAuthorizer
  @enduml
  ```

  - `ScriptConditionController` currently creates
    `ScriptComboBoxEditor`, so filter script editing is a button that
    opens a local `JOptionPane` and returns plain text only.
  - `ScriptComboBoxEditor` is reused outside filter conditions through
    `IScriptEditorStarter.createComboBoxEditor(...)`, including the
    reminder UI in `TimeManagement`, so changing it globally would
    alter unrelated editors.
  - `ScriptCondition` already behaves more like formula evaluation than
    like general script execution: it builds a `GroovyScript` with
    `ScriptingPermissions.getFormulaPermissions()`, runs through
    `FormulaUtils.executeScript(...)`, and accepts only `Boolean` or
    `Number` results.
  - `FormulaEditor` already provides the desired attached-editor shape:
    it implements `AiCodeEditor`, uses source text only, allows
    `readCode`/`writeCode`/`compileCode`, rejects `runCode`, validates
    on submit, and can request AI repair for the attached draft.
  - `ScriptEditorPanel` is not a direct fit for filter conditions:
    it supports `argumentsJsonText`, uses permissive script
    permissions, and exposes `runCode`.
  - `AiCodeOperationAuthorizer` and `AiCodeToolSet` currently branch on
    only two attached-editor content types,
    `text/x-freeplane-formula-groovy` and
    `text/x-freeplane-script-groovy`.
  - `ModelContextProtocolToolRegistry` capability notes and the
    `OptionPanel.ai_formula_editing_enabled` text currently describe
    formula-only attached editing.
- **Analysis:**
  - Add filter-condition AI support through a filter-specific attached
    editor because the shared script combo editor is reused by
    non-filter callers with different semantics.
  - Treat attached filter-condition scripts like formulas for AI
    authorization and editing policy so that AI can assist authoring
    without gaining general script execution.
  - Keep submit-time validation compile-first and node-context-aware
    because the supported contract is a predicate-style condition, not
    a standalone runnable script.
  - Reuse `SingleEditorAttachmentService` and extend content-type
    branching minimally so that attached-editor behavior stays
    consistent across formula, script, and filter-condition drafts.
  - Update user-visible preference and tool descriptions when they
    start governing filter-condition scripts because formula-only
    wording would become false.
- **Design:**
  ```plantuml
  @startuml
  set separator none
  package org.freeplane {
    package features.ai.code {
      interface AiCodeEditor {
        +compileCode(request): CompileCodeResponse
        +runCode(request): RunCodeResponse
      }
    }
    package plugin.script.filter {
      class ScriptConditionController {
        +getValueEditor(selectedProperty, selectedCondition): ComboBoxEditor
      }
      class FilterScriptConditionComboBoxEditor {
        +getEditorComponent(): Component
        +setItem(anObject)
        +getItem(): Object
        -openEditor(selectAll)
      }
      class FilterScriptConditionEditor {
        +compileCode(request): CompileCodeResponse
        +runCode(request): RunCodeResponse
        -submitScript(editedText): boolean
      }
      class FilterScriptConditionValidationSupport {
        +compile(scriptText): CompileCodeResponse
        +validate(selectedNode, scriptText): AiChatCodeOperationResult
      }
      class ScriptCondition {
        +checkNode(node): boolean
      }
    }
    package plugin.ai.code {
      class SingleEditorAttachmentService {
        +attachEditor(editor, contentType): AiChatAttachment
      }
    }
    package plugin.ai.mcpserver {
      class ModelContextProtocolToolRegistry {
        +appendCapabilityNote(description, note): String
      }
    }
    package plugin.ai.tools.code {
      class AiCodeOperationAuthorizer {
        +assertAuthorized(operation, host)
      }
      class AiCodeToolSet {
        +systemMessageForChat(input): String
      }
    }
  }
  FilterScriptConditionEditor ..|> AiCodeEditor
  ScriptConditionController --> FilterScriptConditionComboBoxEditor
  FilterScriptConditionComboBoxEditor --> FilterScriptConditionEditor
  FilterScriptConditionEditor --> FilterScriptConditionValidationSupport
  FilterScriptConditionEditor --> SingleEditorAttachmentService
  ScriptCondition --> FilterScriptConditionValidationSupport
  AiCodeToolSet --> AiCodeOperationAuthorizer
  @enduml
  ```

  ```plantuml
  @startuml
  participant User
  participant FilterScriptConditionComboBoxEditor as ComboEditor
  participant FilterScriptConditionEditor as Editor
  participant SingleEditorAttachmentService as AttachmentService
  participant AiCodeToolSet as Tools
  participant FilterScriptConditionValidationSupport as ValidationSupport

  User -> ComboEditor : openEditor()
  ComboEditor -> Editor : show(currentScript)
  User -> Editor : attach AI
  Editor -> AttachmentService : attachEditor(this, "text/x-freeplane-formula-condition-groovy")
  AttachmentService --> Editor : AiChatAttachment
  Tools -> Editor : readCode / writeCode / compileCode
  User -> Editor : submit
  Editor -> ValidationSupport : compile(sourceText)
  ValidationSupport --> Editor : CompileCodeResponse
  Editor -> ValidationSupport : validate(selectedNode, sourceText)
  ValidationSupport --> Editor : success or failure
  Editor --> ComboEditor : acceptedScript or failure state
  @enduml
  ```

  - `ScriptConditionController.getValueEditor(...)` returns
    `FilterScriptConditionComboBoxEditor` only for filter script
    conditions. `IScriptEditorStarter.createComboBoxEditor(...)` and
    existing reminder or generic script editors stay on
    `ScriptComboBoxEditor`.
  - `FilterScriptConditionComboBoxEditor` keeps the current script
    string, preserves the existing short-preview button text and
    tooltip behavior, and opens a resizable non-modal
    `FilterScriptConditionEditor` instead of an inline
    `JOptionPane` text area.
  - `FilterScriptConditionEditor` hosts a Groovy `JEditorPane`, an AI
    attach toggle, and the current draft text. It implements
    `AiCodeEditor`. `getCodeStateContent()` and
    `replaceCodeStateContent(...)` use only `sourceText`; any non-empty
    `argumentsJsonText` is rejected.
  - The attached condition-formula content type is
    `text/x-freeplane-formula-condition-groovy`.
  - `FilterScriptConditionValidationSupport.compile(...)` compiles the
    draft with `ScriptingPermissions.getFormulaPermissions()` and maps
    diagnostics with `GroovyCompilerDiagnosticsMapper.toSourceDiagnostics(...)`.
  - `FilterScriptConditionEditor.runCode(...)` throws the same
    non-runnable attached-editor error used for formulas. AI may read,
    write, and compile attached filter-condition drafts, but never run
    them through `runCode`.
  - On submit, `FilterScriptConditionEditor.submitScript(...)` first
    compiles the visible draft and then validates it against the
    currently selected node. Validation runs with formula-style
    permissions and `FormulaUtils.callWithExecuteBlockedIfEnabled(...)`.
    `Boolean` results are accepted directly. `Number` results are
    accepted with the same non-zero truthiness rule as
    `ScriptCondition.checkNode(...)`. Any other result or execution
    error becomes a submit failure with diagnostics and an error
    message.
  - `FilterScriptConditionValidationSupport` owns the shared
    compile-and-validate rules. `ScriptCondition.checkNode(...)`
    delegates the reusable result-type and execution-policy logic to
    that support while keeping its existing runtime error reporting.
  - Failed submit validation records attached code state the same way
    as `FormulaEditor`, then offers AI repair when AI is configured.
    The repair prompt must require a valid filter-condition Groovy
    script that returns `Boolean` or `Number`.
  - `AiCodeOperationAuthorizer`, `AiCodeToolSet.systemMessageForChat(...)`,
    and `ModelContextProtocolToolRegistry` recognize
    `text/x-freeplane-formula-condition-groovy` as an attached,
    non-runnable, condition-formula editor content type. `writeCode` and
    `compileCode` require editing availability and
    `ai_formula_editing_enabled`; `runCode` rejects the content type.
  - Keep the stored preference key `ai_formula_editing_enabled` for
    compatibility, but update the displayed label and tooltip text to
    say that the setting governs content and condition formulas.
- **Test specification:**
  - **Automated tests:**
    - `FilterScriptConditionValidationSupportTest`
      - `compileReportsGroovyDiagnosticsWithLocations`: compile-only
        attached filter-condition validation returns Groovy diagnostics
        with line and column information when the compiler provides it.
      - `validateAcceptsBooleanAndNumberResults`: submit validation
        accepts `Boolean` and numeric results using the same truthiness
        contract as runtime `ScriptCondition.checkNode(...)`.
      - `validateRejectsOtherResultTypes`: submit validation rejects
        non-`Boolean`, non-`Number` results with a failure message that
        explains the required return types.
    - `FilterScriptConditionEditorTest`
      - `requestRepairIfConfirmedUsesAttachedCodeState`: failed submit
        validation records the current draft and requests AI repair when
        the user confirms.
      - `shouldEnableAiAttachButtonOnlyWhenAttachedOrAiAvailable`:
        the AI toggle enablement matches the formula-style attached
        editor rules.
    - `AiCodeOperationAuthorizerTest`
      - `attachedFilterConditionWriteAndCompileRequireFormulaEditingPermission`:
        attached filter-condition drafts stay read-only when the
        formula-editing gate is off.
      - `editingAvailabilityAllowsAttachedFilterConditionWriteAndCompileWhenFormulaEditingIsEnabled`:
        editing availability plus the formula-editing gate authorize
        `writeCode` and `compileCode` for the new content type.
      - `runCodeRejectsFilterConditionContent`: attached
        filter-condition drafts are not runnable.
    - `AiCodeToolSetTest`
      - `filterConditionSystemMessageExplainsConditionSemantics`:
        attached-editor guidance tells AI to keep the draft
        non-runnable, argument-free, and returning `Boolean` or
        `Number`.
  - **Manual tests:**
    - Open a script filter condition from the filter toolbar or filter
      composer, attach AI, let AI modify the draft, and confirm the
      editor keeps the modified text when the user accepts it.
    - Save a syntactically invalid filter-condition script and confirm
      the editor shows diagnostics and offers AI repair.
    - Save a script that returns a string and confirm submit validation
      rejects it before the filter is stored.
    - Open a reminder or other non-filter script editor and confirm it
      still uses its existing non-filter editing workflow.
- **Implementation notes:**
  - **Interpretations:**
    - Submit-time validation uses the node selected when the filter
      condition dialog is confirmed; attached AI compile operations
      remain node-independent.
  - **Tradeoffs:**
    - Kept `ScriptComboBoxEditor` unchanged and added a dedicated
      filter-condition combo editor and non-modal dialog so generic
      script callers retain their existing behavior.
    - Generalized `FormulaUtils` validation to accept raw scripts,
      avoiding a synthetic formula prefix and preserving filter source
      coordinates while retaining formula permissions and execution
      blocking.
