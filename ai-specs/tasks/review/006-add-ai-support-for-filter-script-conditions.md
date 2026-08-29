# Task: Add AI support for condition formulas
- **Task Identifier:** 2026-08-14-filter-ai
- **Scope:**
  Deliver attached-editor AI support for condition formulas and a
  standalone behavior-preserving refactoring of the shared mechanisms
  used by content-formula and condition-formula editors.
- **Motivation:**
  Condition formulas need the same controlled attached-editor AI path
  as content formulas. The completed feature also exposed duplicated
  editor, attachment, and validation mechanisms that should not evolve
  independently.
- **Scenario:**
  - Content formulas compute content values, while condition formulas
    evaluate filter predicates against a node.
  - Both use the same attached-editor AI operations, but retain their
    distinct source and result policies.
  - The refactoring changes structure only; user-visible behavior and
    runtime contracts remain unchanged.
- **Glossary:**
  - **Content formula:** A Groovy formula edited through the formula
    editor to compute content or another formula value.
  - **Condition formula:** A Groovy filter condition edited through the
    filter editor; it returns `Boolean` or `Number` for node matching.

  ```mermaid
  flowchart LR
      C[Content formula] -- computes --> V[content value]
      Q[Condition formula] -- selects --> N[matching nodes]
  ```

## Subtask: Add AI support for condition formulas
- **Status:** review
- **Scope:**
  Add attached-editor AI support for Groovy-based filter script
  conditions with content-formula restrictions. Users editing a filter
  script condition must be able to attach the draft to AI, let AI
  read, write, and compile the attached draft, and receive
  submit-time diagnostics and AI repair prompts comparable to the
  formula editor. Update attached-editor authorization, tool guidance,
  and preference wording to recognize condition formulas.
  Preserve existing filter matching semantics, existing saved filter
  XML, existing reminder and generic script editors, and the AI-owned
  script host outside this task.
- **Motivation:**
  Condition formulas already execute Groovy with formula-style
  permissions, but their editor is still an isolated string editor
  with no AI attachment path. That leaves a behaviorally similar
  scripting surface behind the current attached-formula workflow and
  makes condition-formula authoring harder to diagnose and repair.
- **Scenario:**
  - A user opens a condition formula from the filter toolbar,
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
  - Keep attached condition formulas non-runnable and
    argument-free like attached formulas.
  - Keep condition-formula execution and validation under
    `ScriptingPermissions.getFormulaPermissions()`.
  - Do not change unrelated `ScriptComboBoxEditor` callers to the new
    editor path.
  - Preserve the existing runtime truthiness contract for
    `ScriptCondition`: accept `Boolean` and `Number`, and treat other
    result types as validation or execution errors.
  - Reuse the existing attached-editor infrastructure instead of
    introducing a second AI attachment channel.
  - If `ai_formula_editing_enabled` governs attached condition-formula
    scripts too, update user-visible wording so the setting description
    stays true.
  - Keep mode-controller execute blocking aligned with formula-style
    validation so AI-assisted condition checks do not introduce map
    edits.
- **Briefing:**
  Condition-formula editing starts in
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
  package "Current condition-formula structure" {
    package javax.swing {
      interface ComboBoxEditor
    }
    package org.freeplane {
      package features {
        package filter {
          class FilterConditionEditor {
            -setValuesEditor()
          }
          interface IElementaryConditionController {
            +getValueEditor(selectedProperty, selectedCondition): ComboBoxEditor
            +createCondition(selectedItem, simpleCond, value, matchCase, approximateMatching, ignoreDiacritics): ASelectableCondition
          }
        }
      }
      package plugin {
        package script {
          class ScriptComboBoxEditor {
            +getEditorComponent(): Component
            -editScript(selectAll)
          }
          class ScriptEditorPanel {
            +compileCode(request): CompileCodeResponse
            +runCode(request): RunCodeResponse
          }
          package filter {
            class ScriptConditionController {
              +getValueEditor(selectedProperty, selectedCondition): ComboBoxEditor
              +createCondition(selectedItem, simpleCond, value, matchCase, approximateMatching, ignoreDiacritics): ASelectableCondition
            }
            class ScriptCondition {
              +getScript(): String
              +checkNode(node): boolean
            }
          }
        }
        package formula {
          class FormulaEditor {
            +compileCode(request): CompileCodeResponse
            -submitEditedText(editedText): boolean
          }
        }
        package ai {
          package code {
            class SingleEditorAttachmentService {
              +attachEditor(editor, contentType): AiChatAttachment
            }
          }
          package tools.code {
            class AiCodeOperationAuthorizer {
              +assertAuthorized(operation, host)
            }
            class AiCodeToolSet {
              +systemMessageForChat(input): String
            }
          }
        }
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
  - Add condition-formula AI support through a filter-specific attached
    editor because the shared script combo editor is reused by
    non-filter callers with different semantics.
  - Treat attached condition formulas like formulas for AI
    authorization and editing policy so that AI can assist authoring
    without gaining general script execution.
  - Keep submit-time validation compile-first and node-context-aware
    because the supported contract is a predicate-style condition, not
    a standalone runnable script.
  - Reuse `SingleEditorAttachmentService` and extend content-type
    branching minimally so that attached-editor behavior stays
    consistent across formula, script, and condition-formula drafts.
  - Update user-visible preference and tool descriptions when they
    start governing condition formulas because formula-only
    wording would become false.
- **Design:**
  ```plantuml
  @startuml
  set separator none
  package "Target condition-formula structure" {
    package javax.swing {
      interface ComboBoxEditor
    }
    package org.freeplane {
      package features {
        package filter {
          class FilterConditionEditor
          interface IElementaryConditionController
        }
        package ai.code {
          interface AiCodeEditor {
            +compileCode(request): CompileCodeResponse
            +runCode(request): RunCodeResponse
          }
        }
      }
      package plugin {
        package script {
          package filter {
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
        }
        package ai {
          package code {
            class SingleEditorAttachmentService {
              +attachEditor(editor, contentType): AiChatAttachment
            }
          }
          package mcpserver {
            class ModelContextProtocolToolRegistry {
              +appendCapabilityNote(description, note): String
            }
          }
          package tools.code {
            class AiCodeOperationAuthorizer {
              +assertAuthorized(operation, host)
            }
            class AiCodeToolSet {
              +systemMessageForChat(input): String
            }
          }
        }
      }
    }
  }
  FilterConditionEditor --> IElementaryConditionController : requests value editor
  FilterScriptConditionEditor ..|> AiCodeEditor
  ScriptConditionController ..|> IElementaryConditionController : implements
  ScriptConditionController --> FilterScriptConditionComboBoxEditor : supplies
  FilterConditionEditor --> ComboBoxEditor : installs returned editor
  FilterScriptConditionComboBoxEditor --> FilterScriptConditionEditor : opens/reuses dialog
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
    write, and compile attached condition-formula drafts, but never run
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
  - `FilterScriptConditionValidationSupport` owns the condition-specific
    source, selected-node, and result policy over
    `GroovyScriptValidation`. `ScriptCondition.checkNode(...)`
    delegates runtime context handling to `ScriptConditionExecution`
    while keeping its existing runtime error reporting.
  - Failed submit validation records attached code state the same way
    as `FormulaEditor`, then offers AI repair when AI is configured.
    The repair prompt must require a valid condition-formula Groovy
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
        attached condition-formula validation returns Groovy diagnostics
        with line and column information when the compiler provides it.
      - `validateAcceptsBooleanAndNumberResults`: submit validation
        accepts `Boolean` and numeric results using the same truthiness
        contract as runtime `ScriptCondition.checkNode(...)`.
      - `validateRejectsOtherResultTypes`: submit validation rejects
        non-`Boolean`, non-`Number` results with a failure message that
        explains the required return types.
    - `AiCodeOperationAuthorizerTest`
      - `attachedFilterConditionWriteAndCompileRequireFormulaEditingPermission`:
        attached condition-formula drafts stay read-only when the
        formula-editing gate is off.
      - `editingAvailabilityAllowsAttachedFilterConditionWriteAndCompileWhenFormulaEditingIsEnabled`:
        editing availability plus the formula-editing gate authorize
        `writeCode` and `compileCode` for the new content type.
      - `runCodeRejectsFilterConditionContent`: attached
        condition-formula drafts are not runnable.
    - `AiCodeToolSetTest`
      - `filterConditionSystemMessageExplainsConditionSemantics`:
        attached-editor guidance tells AI to keep the draft
        non-runnable, argument-free, and returning `Boolean` or
        `Number`.
  - **Manual tests:**
    - Open a condition formula from the filter toolbar or filter
      composer, attach AI, let AI modify the draft, and confirm the
      editor keeps the modified text when the user accepts it.
    - Save a syntactically invalid condition formula and confirm
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
      condition-formula combo editor and non-modal dialog so generic
      script callers retain their existing behavior.
    - Generalized `FormulaUtils` validation to accept raw scripts,
      avoiding a synthetic formula prefix and preserving filter source
      coordinates while retaining formula permissions and execution
      blocking.


## Subtask: Refactor shared formula editor mechanisms
- **Status:** review
- **Scope:**
  Refactor the completed content-formula and condition-formula attached
  editor paths so their stable combo-box presentation, AI chat attachment
  lifecycle, and Groovy validation orchestration are implemented once and
  consumed through delegation. Move condition runtime execution out of
  the validation component. Make no intended change to user-visible
  behavior, AI authorization, filter matching, saved filter XML, formula
  semantics, or generic script execution.
  - Include `ScriptComboBoxEditor` and
    `FilterScriptConditionComboBoxEditor` shared button and preview
    behavior.
  - Include the common AI attach/detach, toggle-state, cleanup, and
    repair-request lifecycle used by `FormulaEditor` and
    `FilterScriptConditionEditor`.
  - Include the common Groovy compile and submit-validation mechanics
    used by `FormulaValidationSupport` and
    `FilterScriptConditionValidationSupport`.
  - Exclude new editor behavior, new AI permissions, new content types,
    and changes to the AI-owned script host contract.
- **Motivation:**
  The feature increment left parallel implementations of the same
  button presentation, attached-editor chat lifecycle, diagnostics
  handling, and validation-result plumbing. These paths already have
  different domain policies, but their stable mechanisms should not
  evolve independently.
- **Scenario:**
  1. A formula editor or condition-formula editor opens with a button
     that can connect its current text to AI.
  2. The button shows whether this editor is connected and whether a
     connection can be started.
  3. Starting the connection lets AI read, change, and compile the
     editor's text. AI cannot run these formulas.
  4. If the editor rejects the text, the latest failure can be kept for
     AI. After the user agrees, AI receives the failed text, diagnostics,
     and the editor-specific repair instruction.
  5. Disconnecting or closing the editor ends the connection and updates
     the button.
  6. Formula and condition validation remain separate; generic runnable
     script editors retain their existing behavior.
- **Constraints:**
  - Use delegation, not inheritance, between `FormulaEditor` and
    `FilterScriptConditionEditor`; they have different dialog base
    classes and lifecycle owners.
  - `AiEditingSession` owns the temporary AI connection and its button
    state, but not formula or condition validation policy, dialog layout,
    user confirmation, or repair-instruction wording.
  - Keep `FormulaValidationSupport` and
    `FilterScriptConditionValidationSupport` as policy facades unless
    the refactoring proves that a narrower compatible boundary is safer.
  - Keep content formulas and condition formulas as distinct content
    types and distinct result policies.
  - Preserve formula permissions and mode-controller execute blocking.
  - Preserve existing public and service-facing contracts; this is a
    behavior-preserving refactoring.
- **Briefing:**
  `ScriptComboBoxEditor` and
  `FilterScriptConditionComboBoxEditor` independently own the same
  script preview button behavior but open different dialogs.
  `FormulaEditor` and `FilterScriptConditionEditor` independently own
  AI attachment lifecycle and repair handling but have different dialog
  inheritance and validation policies. `FormulaValidationSupport` and
  `FilterScriptConditionValidationSupport` independently capture output,
  line information, compiler diagnostics, fingerprints, and
  `AiChatCodeOperationResult` values. `SingleEditorAttachmentService`
  already owns the backend attachment protocol and is not replaced by
  this refactoring.
- **Research:**
  ```plantuml
  @startuml
  set separator none
  package "Current shared-editor structure" {
    package javax.swing {
      interface ComboBoxEditor
    }
    package org.freeplane {
      package features {
        package filter {
          class FilterConditionEditor
          interface IElementaryConditionController
        }
      }
      package plugin {
        package formula {
          class FormulaEditor
        }
        package script {
          class ScriptComboBoxEditor
          class FormulaValidationSupport
          package filter {
            class ScriptConditionController
            class FilterScriptConditionComboBoxEditor
            class FilterScriptConditionEditor
            class FilterScriptConditionValidationSupport
            class ScriptCondition
          }
        }
        package ai {
          package code {
            class SingleEditorAttachmentService
          }
        }
      }
    }
  }
  FilterConditionEditor --> IElementaryConditionController : requests value editor
  FilterConditionEditor --> ComboBoxEditor : installs returned editor
  ScriptConditionController ..|> IElementaryConditionController : implements
  ScriptConditionController --> FilterScriptConditionComboBoxEditor : supplies
  FilterScriptConditionComboBoxEditor ..|> ComboBoxEditor : implements
  FilterScriptConditionComboBoxEditor --> FilterScriptConditionEditor : opens/reuses
  FormulaEditor ..> FormulaValidationSupport
  FormulaEditor ..> SingleEditorAttachmentService
  FilterScriptConditionEditor ..> FilterScriptConditionValidationSupport
  FilterScriptConditionEditor ..> SingleEditorAttachmentService
  ScriptCondition ..> FilterScriptConditionValidationSupport
  @enduml
  ```
  - The two combo-box editors duplicate script state, preview text,
    tooltip, action listeners, sizing, and selected-item handling.
  - The two attached editors duplicate attachment lookup, toggle state,
    detach callbacks, cleanup, code-state recording, and repair request
    construction.
  - Validation support duplicates the mechanics around compilation,
    source diagnostics, output capture, node numbering, line capture,
    exception conversion, and AI validation responses.
  - The condition execution helper currently lives in
    `FilterScriptConditionValidationSupport` even though it serves
    runtime `ScriptCondition.checkNode(...)`, not attached-editor
    validation.
- **Analysis:**
  - Use delegation rather than inheritance because the two dialogs do
    not share a safe superclass contract.
  - Name the shared lifecycle component `AiEditingSession` because
    it represents the temporary period in which one editor works with
    AI, including connection, failure state, repair, and shutdown.
  - Keep formula and condition validation as separate policy facades
    over shared Groovy validation mechanics because their source and
    result contracts differ.
  - Treat the refactoring as a standalone, behavior-preserving
    increment so it can be reviewed and accepted without a later
    feature change.
- **Design:**
  ```plantuml
  @startuml
  set separator none
  package "Target shared-editor structure" {
    package javax.swing {
      interface ComboBoxEditor
    }
    package org.freeplane {
      package features {
        package filter {
          class FilterConditionEditor
          interface IElementaryConditionController
        }
        package ai.code {
          class AiEditingSession {
            - editor : AiChatAttachableEditor
            - contentType : String
            - attachButton : JToggleButton
            - attachmentServiceProvider : Supplier<AiChatAttachmentService>
            - toggleListener : ActionListener
            - currentAttachment : AiChatAttachment
            - closed : boolean
            --
            + AiEditingSession(editor, contentType, attachButton, attachmentServiceProvider)
            + canStart() : boolean
            + start() : boolean
            + isActive() : boolean
            + toggle() : void
            + end() : void
            + rememberFailure(state : ReadCodeResponse) : void
            + forgetFailure() : void
            + askForRepair(state : ReadCodeResponse, repairInstruction : String) : boolean
            + close() : void
            - attachmentService() : AiChatAttachmentService
            - setCurrentAttachment(attachment : AiChatAttachment) : void
            - handleDetached(attachment : AiChatAttachment) : void
            - endCurrentAttachment() : void
            - updateToggleButton() : void
          }
        }
      }
      package plugin {
        package script {
          class ScriptComboBoxEditor
          class ScriptEditorButton
          class GroovyScriptValidation
          class FormulaValidationSupport
          package filter {
            class ScriptConditionController
            class FilterScriptConditionComboBoxEditor
            class FilterScriptConditionEditor
            class FilterScriptConditionValidationSupport
            class ScriptConditionExecution
            class ScriptCondition
          }
        }
        package formula {
          class FormulaEditor
        }
      }
    }
  }
  FilterConditionEditor --> IElementaryConditionController : requests value editor
  FilterConditionEditor --> ComboBoxEditor : installs returned editor
  ScriptConditionController ..|> IElementaryConditionController : implements
  ScriptConditionController --> FilterScriptConditionComboBoxEditor : supplies
  ScriptComboBoxEditor ..|> ComboBoxEditor : implements
  FilterScriptConditionComboBoxEditor ..|> ComboBoxEditor : implements
  ScriptComboBoxEditor --> ScriptEditorButton : delegates preview/button
  FilterScriptConditionComboBoxEditor --> ScriptEditorButton : delegates preview/button
  FilterScriptConditionComboBoxEditor --> FilterScriptConditionEditor : opens/reuses dialog
  FormulaEditor --> AiEditingSession : delegates AI editing
  FilterScriptConditionEditor --> AiEditingSession : delegates AI editing
  FormulaValidationSupport --> GroovyScriptValidation
  FilterScriptConditionValidationSupport --> GroovyScriptValidation
  ScriptCondition --> ScriptConditionExecution
  @enduml
  ```
  - `ScriptEditorButton` owns the shared script value, preview text,
    tooltip, button action dispatch, and button sizing. It accepts an
    editor-opening callback. `ScriptComboBoxEditor` and
    `FilterScriptConditionComboBoxEditor` delegate those operations
    while retaining ownership of their different dialog lifecycles.
  - `FilterConditionEditor` is the outer filter UI. It installs the
    `ComboBoxEditor` returned by `ScriptConditionController`.
    `FilterScriptConditionComboBoxEditor` is that small value-field
    adapter: it keeps the current text, opens or reuses the dialog, and
    receives accepted text through a callback. `FilterScriptConditionEditor`
    is the dialog itself; it owns text editing, validation, submit, and
    AI editing. The two classes are related by dialog creation and the
    accepted-text callback, not by inheritance.
  - `AiEditingSession` represents one temporary AI editing period for
    one editor. Each host supplies its `AiChatAttachableEditor`, content
    type, AI toggle button, and attachment-service lookup. The session
    owns starting and ending the current `AiChatAttachment`, toggle
    state, detach callbacks, shutdown, remembered failure state in the
    attachment, and repair requests. The host supplies user
    confirmation, validation-failure state, and repair instruction.
    The button is the visible representation of the session state: the
    host creates and translates it, while the session updates its
    enabled and selected state.
  - `canStart()` reports whether the session is not closed and the AI
    service is configured. `start()` is idempotent, creates the
    attachment, and returns whether the session is active. `toggle()`
    starts or ends it. `end()` detaches without permanently closing the
    session; `close()` ends it and removes the button listener.
  - `rememberFailure()` and `forgetFailure()` forward state to the
    active attachment without duplicating it locally. `askForRepair()`
    is called after the host's confirmation, starts the session if
    needed, records the supplied failure, sends the supplied repair
    instruction, and reports whether the request was sent.
  - `GroovyScriptValidation` owns shared raw-script compilation and
    validation mechanics: formula-permission execution, mode-controller
    execute blocking, source fingerprints, compiler diagnostic mapping,
    standard-output capture, node numbering, line capture, and
    `AiChatCodeOperationResult` construction. It does not decide whether
    a result is valid for a domain.
  - `FormulaValidationSupport` remains the content-formula policy
    facade. It strips and validates the formula prefix, delegates
    common mechanics, and accepts the existing formula result contract.
  - `FilterScriptConditionValidationSupport` remains the condition-formula
    policy facade. It validates raw source against the selected node and
    accepts only `Boolean` or `Number`, with the existing numeric
    truthiness rule.
  - `ScriptConditionExecution` owns the runtime script context,
    cycle-stack handling, `ScriptRunner` context lifecycle, and cached
    execution call currently held by `FilterScriptConditionValidationSupport`.
    `ScriptCondition` retains runtime error reporting and result policy.
  - `SingleEditorAttachmentService`, content-type authorization,
    compile-only behavior, translation values, and saved filter data
    remain unchanged.
- **Test specification:**
  - **Automated tests:**
    - `ScriptEditorButtonTest` verifies shared preview text, tooltip,
      item updates, action dispatch, and selection behavior used by both
      combo-box editors.
    - `AiEditingSessionTest` verifies start, end, toggle state,
      external detach, shutdown, failure-state forwarding, and repair
      requests without requiring either dialog implementation.
    - `GroovyScriptValidationTest` verifies shared compilation
      diagnostics, source fingerprints, output capture, line capture,
      node-numbering, and failure conversion.
    - Existing `FormulaValidationSupportTest` and
      `FilterScriptConditionValidationSupportTest` verify that their
      distinct source and result policies remain unchanged.
    - Existing `AiOwnedScriptHostServiceTest`, formula editor tests,
      and attached-editor authorization/tool tests remain green.
  - **Manual tests:**
    - Open a content formula and a condition formula and confirm their
      dialogs, AI toggle behavior, repair flow, and submit behavior are
      unchanged.
    - Open a reminder or generic script editor and confirm its existing
      modal and runnable workflow is unchanged.
- **Implementation notes:**
  - **Interpretations:**
    - Kept OSGi service lookup in each host and injected the lookup into
      `AiEditingSession` because the formula and script plugins own
      different activator contexts.
  - **Tradeoffs:**
    - Left `ScriptEditorPanel` on its existing runnable attachment path;
      its selection, arguments, permissions, and manual-run repair flow
      are different from formula editing.
    - Removed editor-local attachment helper tests and moved their
      shared lifecycle coverage to `AiEditingSessionTest`.
