# Task: Allow AI chat attachment for Formula Editor and Script Editor
- **Task Identifier:** 2026-05-27-editor-chat-attachment
- **Scope:** Let a user explicitly attach an open `FormulaEditor` or
  `ScriptEditorPanel` to AI chat via an editor-local AI button. While
  attached, AI chat must become visible and usable, AI must be able to
  read and overwrite the live editor text, AI must be able to compile the
  attached code, and AI must be able to inspect the latest attached
  issue through attached-editor tools available to both internal chat
  and MCP. Only one editor may be attached at a time. Script execution remains out of
  scope for this task. Formula execution while the editor stays open is
  also out of scope, except for a final pre-commit submit-time formula
  validation that now applies to all `FormulaEditor` submits. On a
  failed formula submit, Freeplane must show the diagnostics to the
  user, ask whether AI should try to fix the formula, and if the user
  agrees, open or reuse AI chat according to a user setting, attach the
  editor if needed, and send a built-in repair request with the current
  formula text and diagnostics.
- **Motivation:** Current AI tooling edits persisted node or attribute
  state, not the live text buffer held by an open editor dialog.
  `FormulaEditor` additionally blocks normal access to the main
  Freeplane window, so the user cannot reach AI chat while the editor is
  open. Current formula submit flow also hides the dialog before the new
  text is committed, which prevents a clear pre-commit validation and AI
  repair loop.
- **Scenario:** A user opens `FormulaEditor` or `ScriptEditorPanel` and
  clicks a new AI button in that editor. Freeplane shows AI chat,
  associates that editor with a chosen chat session, and lets AI read,
  overwrite, compile, and later inspect the editor's latest issue while the
  editor stays open. If the user submits a formula and the
  current text still starts with `=`, Freeplane validates the formula
  before commit while the editor remains visible. If validation fails,
  Freeplane shows the diagnostics in a popup that asks whether AI should
  try to fix the formula. If the user answers Yes, Freeplane sends a
  built-in repair request to AI. If the user answers No, the editor just
  remains open for manual editing. If the user removes the leading `=`,
  the content stops being a formula immediately, compile-for-AI fails
  with a not-a-formula error, and submit commits plain text with no
  formula validation.
- **Constraints:**
  - Attaching an editor must be explicit. Do not auto-attach merely
    because an editor is open.
  - Attaching an editor must not compile or execute its current text by
    itself.
  - At most one editor may be attached to AI chat at a time.
  - Attached-editor edits must operate on the live editor text
    component, not on persisted `NodeModel` or attribute content.
  - Closing an attached editor must clear the active attachment
    automatically.
  - Attaching a second editor must replace the previous attachment,
    except that a formula submit already in its final validation flow is
    not interruptible.
  - `FormulaEditor` must still support formula-reference insertion from
    the map.
  - Do not route attached-editor replacement through
    `TextualContentEditor` or the existing node-content `edit(...)`
    tool.
  - Attached-editor tools must be available through both internal chat
    and MCP.
  - `readAttachedEditor()` must be the pull-oriented state read for
    external callers: it returns `attached=false` when no editor is
    attached and, when attached, includes issue-presence state so MCP
    callers do not need a separate status tool.
  - This task must not add AI-triggered script execution, AI-triggered
    formula execution, or a general-purpose manual Execute button.
  - While `FormulaEditor` is open, Freeplane must not evaluate the live
    formula text merely because the dialog opened, the text changed, or
    the editor was attached.
  - The final formula submit validation is pre-commit and applies to all
    `FormulaEditor` submits, not only attached ones.
  - Removing the leading `=` stops formula behavior immediately.
  - `compileAttachedEditorContent()` for formulas must compile only the Groovy
    body after the leading `=` under formula permissions, with no
    execution and no persistent formula cache or dependency writes.
  - Final formula submit validation must use a temporary evaluation path
    with no persistent `FormulaCache` or dependency writes before
    commit.
  - If reusing the successful pre-commit evaluation result after commit
    is simple and low-risk, do it. If it is too complex or risky, a
    second normal post-commit evaluation is allowed.
  - Attached-editor tools and the AI repair flow are enabled by
    attachment state alone. They do not follow `ChatToolAvailability`.
  - Automatic chat traffic must not be created just because an attached
    formula submit failed. The user sees the diagnostics first and chat
    receives a repair request only if the user agrees.
  - The attach-mode setting must be a user setting, not an attach-time
    button, and its default must be `new_chat`.
  - Future script execution features stay in
    `ai-specs/tasks/backlog/groovy-script-execution-tool.md`.
- **Briefing:** `FormulaEditor` lives in
  `freeplane_plugin_formula/src/main/java/org/freeplane/plugin/formula`.
  `ScriptEditorPanel` lives in
  `freeplane_plugin_script/src/main/java/org/freeplane/plugin/script`.
  The AI chat UI is added as a tab in the main Freeplane tabbed panel by
  `freeplane_plugin_ai/src/main/java/org/freeplane/plugin/ai/Activator.java`.
  Current AI edit tooling is centered around `AIToolSet` and
  `TextualContentEditor`, which target persisted map content rather than
  open editor documents. Current script compilation and execution are
  centered around `ScriptingEngine` and `GroovyScript`. Current formula
  evaluation is centered around `FormulaUtils`,
  `FormulaTextTransformer`, `ScriptContext`, and `FormulaCache`.
- **Research:**
  - Observed facts:
    - `FormulaEditor` extends `EditNodeDialog`, but
      `FormulaEditor.configureDialog(...)` already forces
      `dialog.setModal(false)`. The blocking behavior is therefore not
      ordinary Swing dialog modality.
    - `FormulaEditor.show(Window)` currently installs a
      `GlassPaneManager` on the main window root pane before showing the
      dialog.
    - `GlassPaneManager.ancestorAdded(...)` makes the main root glass
      pane visible and calls
      `SwingUtilities.getWindowAncestor(rootPane).setFocusableWindowState(false)`.
      `ancestorRemoved(...)` reverses that state.
    - `GlassPaneNodeSelector` only routes mouse interaction to
      `MapView`, `MainView`, `JTable`, and scrollbars, and translates
      those interactions into formula-reference picking behavior.
      Normal main-window UI is not part of that allowed interaction set.
    - `ScriptEditorPanel` is a separate non-modal `JDialog`
      (`super(UITools.getCurrentFrame(), false)`) and does not install a
      `GlassPaneManager` or disable the main window focusable state.
    - `EditNodeDialog.LongNodeDialog.submit()` currently calls
      `super.submit()` before `getEditControl().ok(...)`, so the dialog
      is hidden before the edited text is committed.
    - `FormulaEditor.addPreviewPane(...)` currently executes once during
      dialog setup, calls `FormulaUtils.evalIfScript(getNode(),
      content)`, and uses the original `getText()` value rather than the
      live current `textEditor` content.
    - `FormulaTextTransformer.transformContent(...)` treats text as a
      formula only when `FormulaUtils.containsFormula(text)` is true.
      Removing the leading `=` therefore already stops formula behavior
      in normal runtime rendering.
    - `FormulaCache` caches both successful results and
      `ExecuteScriptException` objects by node and script text.
    - `ScriptContext.accessNode(...)`, `accessBranch(...)`,
      `accessClones(...)`, `accessAll(...)`, and
      `accessGlobalNode()` write persistent dependency state through
      `FormulaDependencies` when tracking is enabled.
    - `FormulaUtils.executeScript(node, script)` builds a `NodeScript`,
      creates a `ScriptContext(nodeScript)`, uses
      `ScriptingPermissions.getFormulaPermissions()`, and evaluates
      through `FormulaCache`.
    - `ScriptingEngine.executeScript(node, script, errorHandler,
      outStream, scriptContext, permissions)` already supports custom
      output capture, custom line capture, explicit permissions, and an
      explicit `ScriptContext`.
    - `GroovyScript.execute(...)` compiles inside `execute(...)`, can
      report compile/runtime line numbers through the provided
      `IFreeplaneScriptErrorHandler`, and uses the passed
      `ScriptContext`.
    - `LiveChatSession` already stores a `Set<String>` of map IDs, and
      chat summaries also carry multiple map IDs. Reusing a current chat
      across maps is therefore already a supported state.
    - `AIChatPanel` already knows how to create and switch live chat
      sessions and to focus the input area.
    - `AIToolSet` is shared by normal chat and MCP.
    - `ModelContextProtocolServer` starts once from the AI plugin
      `Activator`, so attached-editor tool registration must stay
      stable even though attachment state changes later.
    - `ToolExecutorFactory.createRegistry(...)` currently scans a single
      tool object through `toolSet.getClass().getDeclaredMethods()`, so
      Freeplane needs explicit multi-object merging to expose separate
      attached-editor tool objects consistently to both chat and MCP.
    - `freeplane_plugin_formula` and `freeplane_plugin_script` already
      depend on `freeplane`, but not on `freeplane_plugin_ai`.
    - `FormulaTextTransformer` and `ScriptEditorPanel` currently set
      their live editor panes to `text/groovy`, so the current editor
      content type does not distinguish formulas from scripts.
    - `freeplane_plugin_jsyntaxpane` `Activator` already registers
      `text/latex` programmatically through
      `DefaultSyntaxKit.registerContentType(...)`, so formula- and
      script-specific Groovy content types can be registered there
      without changing `GroovySyntaxKit` itself.
  - Implications:
    - The attachment contract should be exported from `freeplane`, not
      from `freeplane_api` and not from AI-plugin implementation
      packages.
    - Attached-editor editing needs a dedicated live-editor path separate
      from persisted node-content editing.
    - Attached-editor tools should be part of the shared chat and MCP
      tool surface, not chat-only.
    - Because MCP has no subscriptions, `readAttachedEditor()` should be
      the pull-oriented state read: it returns `attached=false` when no
      editor is attached and, when attached, includes content and
      issue-presence state.
    - Formula submit-time validation must change the current
      `EditNodeDialog` submit lifecycle for `FormulaEditor` so the
      dialog stays visible until validation succeeds.
    - Temporary formula submit validation must bypass persistent formula
      cache and dependency writes even though it still uses the current
      formula permissions, bindings, and runtime environment.
    - Because multi-map chat sessions already exist, the attach-mode
      setting can reuse the current chat literally, even when it already
      includes another map.

  ```plantuml
  @startuml
  actor User
  participant "FormulaEditor" as FE
  participant "FormulaUtils" as FU
  participant "EditNodeDialog" as END
  participant "FormulaTextTransformer" as FTT
  participant "FormulaCache / ScriptContext" as Cache
  participant "AI chat tab" as AI
  
  User -> FE: open formula editor
  FE -> FU: one-shot preview eval during dialog setup
  FU -> Cache: persistent cached evaluation
  FE -> END: current submit flow
  END -> END: hide dialog first
  END -> FE: call ok(newText)
  FE -> FTT: later normal formula evaluation
  FTT -> Cache: persistent cached evaluation
  FTT --> User: rendered value or error text
  FTT --> AI: nothing
  @enduml
  ```

- **Design:**
  - Split the work into three functional increments:
    1. shared attachment, attach-mode setting, and attached issue
       tool path,
    2. `ScriptEditorPanel` integration for attach, read, overwrite,
       compile, and latest issue state,
    3. `FormulaEditor` integration for attach, read, overwrite,
       compile,
       center-scoped reference picking, pre-commit final validation,
       and optional AI repair requests.
  - The ordering is intentional:
    - subtask 1 creates the cross-plugin contract, chat session routing,
      attached issue state, and the shared chat/MCP tool path,
    - subtask 2 validates the simpler script editor first,
    - subtask 3 then solves the formula-specific submit lifecycle and
      repair flow using the shared attachment foundation.

  ```plantuml
  @startuml
  left to right direction
  rectangle "Subtask 1\nshared attachment,\nchat mode setting,\nissue tool path" as S1
  rectangle "Subtask 2\nScriptEditorPanel\nAI attach + compile\nlatest issue state" as S2
  rectangle "Subtask 3\nFormulaEditor\npre-commit validation +\nAI repair flow" as S3
  
  S1 --> S2
  S1 --> S3
  S2 --> S3 : de-risk editor tool path
  @enduml
  ```

  ```plantuml
  @startuml
  actor User
  participant "FormulaEditor" as FE
  participant "FormulaSubmitValidationSupport" as Validate
  participant "Repair decision popup" as Popup
  participant "AiChatAttachment" as Attach
  participant "AI chat" as AI
  
  User -> FE: submit formula text
  FE -> Validate: validate current text before commit
  alt validation succeeds
    Validate --> FE: success
    FE -> Attach: clear stale issue
    FE -> FE: commit text
    FE --> User: close editor
  else validation fails
    Validate --> FE: diagnostics
    FE -> Attach: record issue
    FE -> Popup: show diagnostics + ask AI?
    alt user chooses Yes
      Popup --> FE: yes
      FE -> Attach: requestRepair(prompt, text, issue)
      Attach -> AI: open/reuse chat + send repair request
    else user chooses No
      Popup --> FE: no
    end
    FE --> User: keep editor open and attached
  end
  @enduml
  ```

- **Test specification:**
  - End-to-end completion requires all of the following:
    - only one editor can be attached at a time,
    - AI or MCP can read and overwrite attached live editor text,
    - AI or MCP can compile attached script or formula content and
      inspect compiler diagnostics,
    - attached-editor tools are available through both internal chat
      and MCP,
    - `readAttachedEditor()` exposes pull attachment state and issue
      presence without a separate status tool,
    - attached-editor tools remain available when an editor is attached
      even if normal map tools are disabled by `ChatToolAvailability`,
    - script and formula content are not executed merely because the
      editor is open or attached,
    - `FormulaEditor` no longer evaluates the live formula during dialog
      setup,
    - final formula validation happens before commit while the editor
      stays visible,
    - failed formula submit does not commit the text,
    - removing the leading `=` stops formula behavior immediately and
      submit then commits plain text,
    - failed formula submit shows diagnostics to the user before any AI
      request is sent,
    - AI repair request is sent only when the user agrees,
    - attached editors expose at most one latest issue through an
      attached-editor tool for later manual AI or MCP help,
    - successful formula submit closes and detaches the editor,
    - `ScriptEditorPanel` save and cancel behavior still uses its
      existing buffered model correctly,
    - `FormulaEditor` keeps map reference insertion while AI chat stays
      reachable and usable.
  - Final manual verification after all subtasks:
    - with no editor attached, call `readAttachedEditor()` through MCP
      and verify it returns `attached=false`,
    - attach script editor, rewrite live script, compile it from AI or
      MCP, and verify compilation diagnostics include line information,
    - attach formula editor, compile the current formula from AI or MCP,
      and verify compilation errors are returned without execution,
    - attach formula editor, let AI rewrite the formula, submit a
      failing formula, verify the popup shows diagnostics before any AI
      request, then choose Yes and verify AI chat opens and receives the
      built-in repair request,
    - attach formula editor, submit a failing formula, choose No, then
      manually ask AI for help and verify the latest issue is still readable through the
      attached-editor issue tool,
    - remove the leading `=` from formula text, verify
      `compileAttachedEditorContent()` fails with a not-a-formula error, then
      submit and verify plain text is committed and the editor closes,
    - verify the attach-mode setting uses a new chat by default and can
      instead reuse the current chat across maps,
    - close the attached editor, call `readAttachedEditor()` through MCP,
      and verify it returns `attached=false`,
    - verify successful formula submit closes the editor and clears the
      attachment.

## Subtask: Introduce shared editor-attachment service, attach-mode setting, and attached-issue tool path
- **Status:** review
- **Scope:** Add the cross-plugin editor-attachment contract in
  `freeplane`, wire a single-active-attachment service into the AI
  plugin, add the user setting that chooses whether explicit attach or
  AI repair attach uses a new chat or the current chat, and expose
  attached-editor tools for read, overwrite content, compile content,
  and the latest issue through both internal chat and MCP.
- **Motivation:** Both editors need the same attachment contract, the
  same AI-side compile surface, the same issue retrieval path,
  and the same attach-mode setting before the editor-specific UI work
  can stay coherent.
- **Constraints:**
  - Declare the public service contract in `freeplane`, not in
    `freeplane_api`.
  - Keep the base attachment contract editor-text-specific. Do not
    expose persisted node-content mutation policy through it.
  - Attached-editor tools must be registered for both chat and MCP.
  - `readAttachedEditor()` must be the pull-oriented status read. Do
    not add a separate attached-editor status tool.
  - This task must not add any AI code-execution tool.
  - Attached-editor tool availability must be governed by attachment
    state, not by `ChatToolAvailability`.
- **Briefing:** This subtask is the shared foundation for both editor UI
  integrations. It intentionally does not depend on any specific editor
  dialog implementation details beyond the live-text, compile, attach,
  diagnostics, and repair-request contracts.
- **Research:**
  - `AIChatPanel` already knows how to select its tab, create sessions,
    switch sessions, and focus the input area.
  - `LiveChatSession` already stores multiple map IDs.
  - `AIToolSetBuilder` currently creates the shared base tool surface
    for both visible chat requests and MCP server startup.
  - `ModelContextProtocolServer` starts once from the AI plugin
    `Activator`, so attached-editor tool registration must stay stable
    even though attachment state changes later.
  - `ToolExecutorFactory` currently scans only one tool object through
    `getDeclaredMethods()`, so attached-editor tools should be merged as
    separate tool objects instead of `AIToolSet` subclasses.
- **Design:**
  - Export `org.freeplane.features.ai.code` from `freeplane/build.gradle`.
  - Keep shared attachment contracts in
    `org.freeplane.features.ai.code`. Keep the AI-plugin implementation
    classes for this increment in `org.freeplane.plugin.ai.code`.
  - Add attach-mode setting:
    - property key: `ai_attached_editor_chat_mode`
    - values: `new_chat`, `reuse_current_chat`
    - default: `new_chat`
    - explicit attach and formula-repair auto-attach must use the same
      setting and the same replacement semantics.
  - `sourceFingerprint` uses SHA-256 so AI can compare current editor
    text with the source that produced the latest issue.
  - `AiChatCodeOperationResult` continues to use
    `compilerDiagnostics`, `standardOutput`, `result`,
    `errorCategory`, `errorMessage`, `lineNumber`, and
    `sourceFingerprint`.
  - Use the live editor pane content type as the AI-visible
    `contentType` after the editor-specific subtasks switch those panes
    to the new Freeplane-specific Groovy content types.
  - Register `text/x-freeplane-script-groovy` and
    `text/x-freeplane-formula-groovy` in
    `freeplane_plugin_jsyntaxpane/src/main/java/org/freeplane/plugin/jsyntaxpane/Activator.java`
    through `DefaultSyntaxKit.registerContentType(...,
    GroovySyntaxKit.class.getName())`.
  - `AiChatAttachmentService.attachEditor(editor, contentType)`:
    - chooses or creates the owning chat session according to
      `ai_attached_editor_chat_mode`,
    - stores the active attachment, its owning session, content type,
      current source fingerprint and the latest issue,
    - shows the chosen AI chat session and focuses the input,
    - replaces any previous attachment,
    - returns an idempotent attachment handle.
  - `AiChatAttachment` must support:
    - `detach()`,
    - `showOwningChat()`,
    - `recordIssue(...)`,
    - `clearIssue()`,
    - `requestRepair(...)`.
  - `AiChatRepairRequest` carries:
    - the fixed built-in repair prompt,
    - the current editor text,
    - the captured issue.
  - `AIToolSetBuilder.attachedEditorProvider(...)` stores the shared
    `AttachedEditorProvider` used to build attached-editor tools.
  - `AIToolSetBuilder.build()` returns the plain `AIToolSet` as the
    shared base tool object.
  - Add `AIToolSetBuilder.buildToolObjects()` returning an ordered
    `List<Object>` built from the same builder configuration and used by
    both chat and MCP:
    - the base `AIToolSet`,
    - plus `AttachedEditorToolSet`.
  - Update `AIChatPanel`, `ChatPromptRunner`, and AI-plugin
    `Activator` startup so every `AIToolSetBuilder` used for visible
    chat, prompt chat, and MCP receives the same
    `AttachedEditorProvider`.
  - `AIChatServiceFactory` / `AIChatService` keep using the base
    `AIToolSet` for the primary system message, but use the ordered
    tool-object list as the registration source for visible chat and
    prompt chat.
  - `ModelContextProtocolServer`,
    `ModelContextProtocolToolRegistry`, and
    `ModelContextProtocolToolDispatcher` all use that same ordered
    tool-object list so `tools/list` metadata and tool execution expose
    the same attached-editor surface.
  - On the chat path, attached-editor tools bypass
    `ChatToolAvailability` filtering. Normal map tools remain governed
    by `ChatToolAvailability`.
  - Update `ToolExecutorFactory` with
    `createRegistry(Collection<?> toolSets)`.
    - scan each tool object's declared `@Tool` methods,
    - preserve the builder-supplied tool-object order,
    - do not rely on reflection order within one tool object,
    - reject duplicate tool names across tool objects.
  - `AttachedEditorToolSet` is a separate tool object. Do not make it a
    subclass of `AIToolSet`.
  - `AttachedEditorToolSet` receives the chat
    `ToolCallSummaryHandler` directly so it can publish normal tool
    summaries without relying on `AIToolSet` inheritance.
  - `readAttachedEditor()`, `overwriteAttachedEditorContent(...)`,
    `compileAttachedEditorContent()`, and
    `getAttachedEditorLatestIssue()` are attached-editor tools exposed
    to both chat and MCP.
  - Tool behavior:
    - `readAttachedEditor()` is the pull-oriented attachment-state read:
      - when no editor is attached, it returns `attached=false`,
      - when an editor is attached, it returns `attached=true`,
        `contentType`, current text, current text fingerprint,
        capability flags, and `hasIssue`,
    - `overwriteAttachedEditorContent(...)` replaces the whole editor
      text and returns the new text fingerprint,
    - attaching an editor alone does not create an issue,
    - `compileAttachedEditorContent()` calls `AiChatCodeEditor.compileForAi()`,
      stores the returned result as the latest issue only when it is
      unsuccessful, clears the issue on success, and returns it,
    - `getAttachedEditorLatestIssue()` returns `hasIssue=false` when
      no current issue is stored, and otherwise returns the latest
      unsuccessful `AiChatCodeOperationResult`, whose
      `sourceFingerprint` lets AI detect stale issues,
    - if no editor is attached, all attached-editor tools except
      `readAttachedEditor()` fail with `No editor is attached.`
  - `AIChatService` composes the system message from
    `AIToolSet.systemMessageForChat(...)` plus the attachment guidance
    returned by `AttachedEditorToolSet.systemMessageForChat(...)`
    when an editor is attached.
  - Update `AttachedEditorToolSet.systemMessageForChat(...)`:
    - for attached scripts, instruct the model that the current task
      supports read, overwrite, compile, and the latest issue only,
    - for attached formulas, instruct the model that the current task
      supports read, overwrite, compile, the latest issue, and optional
      user-approved repair requests after submit failures, but not live
      execution while the editor remains open.

  ```plantuml
  @startuml
  set separator none
  package "freeplane" {
    package "org.freeplane.features.ai.code" {
      interface AiChatAttachableEditor {
        + getText() : String
        + replaceText(text : String)
      }
  
      interface AiChatCodeEditor {
        + compileForAi() : AiChatCodeOperationResult
      }
  
      interface AiChatAttachment {
        + detach()
        + showOwningChat()
        + recordIssue(result : AiChatCodeOperationResult)
        + clearIssue()
        + requestRepair(request : AiChatRepairRequest)
      }
  
      interface AiChatAttachmentService {
        + attachEditor(editor : AiChatAttachableEditor, contentType : String) : AiChatAttachment
      }
  
      class AiChatRepairRequest {
        + prompt : String
        + sourceText : String
        + issue : AiChatCodeOperationResult
      }
  
      class AiChatCodeOperationResult {
        + compilerDiagnostics : List<String>
        + standardOutput : String
        + result : String
        + errorCategory : String
        + errorMessage : String
        + lineNumber : Integer
        + sourceFingerprint : String
      }
    }
  }
  
  package "freeplane_plugin_ai" {
    package "org.freeplane.plugin.ai.chat" {
      class AIChatPanel {
        + showAndFocusInput()
        + switchToSession(sessionId)
        + setAttachedEditorProvider(provider : AttachedEditorProvider)
      }
    }
  
    package "org.freeplane.plugin.ai.code" {
      enum AttachedEditorChatMode {
        NEW_CHAT
        REUSE_CURRENT_CHAT
      }
  
      class AttachedEditorChatModeSettings {
        + get() : AttachedEditorChatMode
      }
  
      interface AttachedEditorProvider
      class SingleEditorAttachmentService
      class ReadAttachedEditorLatestIssueResponse {
        + hasIssue : boolean
        + issue : AiChatCodeOperationResult
      }
    }
  }
  
  package "freeplane_plugin_jsyntaxpane" {
    package "org.freeplane.plugin.jsyntaxpane" {
      class Activator
    }
  }
  
  package "de.sciss.syntaxpane.syntaxkits" {
    class GroovySyntaxKit
  }
  
  AiChatAttachableEditor <|-- AiChatCodeEditor
  AiChatAttachmentService <|.. SingleEditorAttachmentService
  AttachedEditorProvider <|.. SingleEditorAttachmentService
  SingleEditorAttachmentService --> AIChatPanel
  SingleEditorAttachmentService --> AttachedEditorChatModeSettings
  Activator ..> GroovySyntaxKit : registerContentType(...)
  @enduml
  ```

  ```plantuml
  @startuml
  set separator none
  package "freeplane_plugin_ai" {
    package "org.freeplane.plugin.ai.chat" {
      class AIChatService
      class AIChatServiceFactory
    }
  
    package "org.freeplane.plugin.ai.mcpserver" {
      class ModelContextProtocolServer
      class ModelContextProtocolToolRegistry {
        + listTools() : List
      }
      class ModelContextProtocolToolDispatcher
    }
  
    package "org.freeplane.plugin.ai.tools" {
      class AIToolSet {
        + systemMessageForChat(...)
      }
  
      class AIToolSetBuilder {
        + attachedEditorProvider(attachedEditorProvider : AttachedEditorProvider)
        + build() : AIToolSet
        + buildToolObjects() : List
      }
    }
  
    package "org.freeplane.plugin.ai.tools.utilities" {
      class ToolExecutorFactory {
        + createRegistry(toolSets : Collection) : ToolExecutorRegistry
      }
      class ToolExecutorRegistry
    }
  
    package "org.freeplane.plugin.ai.code" {
      interface AttachedEditorProvider
      class ReadAttachedEditorResponse {
        + attached : boolean
        + contentType : String
        + text : String
        + sourceFingerprint : String
        + supportsCompilation : boolean
        + hasIssue : boolean
      }
      class OverwriteAttachedEditorContentRequest {
        + text : String
      }
      class OverwriteAttachedEditorContentResponse {
        + sourceFingerprint : String
      }
      class ReadAttachedEditorLatestIssueResponse {
        + hasIssue : boolean
        + issue : AiChatCodeOperationResult
      }
      class AttachedEditorToolSet {
        + readAttachedEditor() : ReadAttachedEditorResponse
        + overwriteAttachedEditorContent(request : OverwriteAttachedEditorContentRequest) : OverwriteAttachedEditorContentResponse
        + compileAttachedEditorContent() : AiChatCodeOperationResult
        + getAttachedEditorLatestIssue() : ReadAttachedEditorLatestIssueResponse
        + systemMessageForChat(...) : String
      }
    }
  }
  
  AIToolSetBuilder --> AIToolSet
  AIToolSetBuilder --> AttachedEditorToolSet
  AttachedEditorToolSet --> AttachedEditorProvider
  AIChatServiceFactory --> AIToolSetBuilder
  AIChatService --> AIToolSet : base system message
  AIChatService --> AttachedEditorToolSet : attachment guidance
  AIChatService --> ToolExecutorRegistry
  ModelContextProtocolServer --> ModelContextProtocolToolRegistry
  ModelContextProtocolServer --> ModelContextProtocolToolDispatcher
  ModelContextProtocolToolRegistry ..> AIToolSet
  ModelContextProtocolToolRegistry ..> AttachedEditorToolSet
  ModelContextProtocolToolDispatcher ..> ToolExecutorFactory
  ToolExecutorFactory ..> AIToolSet
  ToolExecutorFactory ..> AttachedEditorToolSet
  ToolExecutorFactory --> ToolExecutorRegistry
  @enduml
  ```

- **Test specification:**
  - Automated tests:
    - `SingleEditorAttachmentServiceTest`
      - attaching replaces previous attachment,
      - attach-mode setting chooses new chat or current chat,
      - reusing the current chat works even when that chat already has
        another map,
      - the latest issue is stored per active attachment and is cleared
        on success,
      - `detach()` is idempotent.
    - `AttachedEditorToolSetTest`
      - `readAttachedEditor()` returns `attached=false` when no
        attachment exists,
      - when attached, `readAttachedEditor()` returns `contentType`,
        live text, capability flags, and `hasIssue`,
      - `overwriteAttachedEditorContent(...)` updates fake editor text,
      - `compileAttachedEditorContent()` stores the latest issue only on
        failure, clears it on success, and returns the compile result,
      - `getAttachedEditorLatestIssue()` returns the latest issue when
        one exists and `hasIssue=false` otherwise,
      - attached-editor tools except `readAttachedEditor()` fail with
        `No editor is attached.` when no attachment exists.
    - `ToolExecutorRegistryTest`
      - multiple tool objects expose merged tool names while keeping the
        builder-supplied tool-object order,
      - duplicate tool names across tool objects fail fast.
    - `AIChatPanel`-level test
      - `showAndFocusInput()` selects the tab and schedules input focus,
      - switching to the chosen chat session works for attach and repair.
    - `AIToolSetBuilder` / tool-object test
      - both chat and MCP tool-object lists contain `AIToolSet` plus
        `AttachedEditorToolSet`,
      - the configured `AttachedEditorProvider` reaches
        `AttachedEditorToolSet`,
      - attached-editor tools remain registered even when no editor is
        attached.
    - `AIChatService` / chat-path test
      - merged chat tool registration uses the ordered tool-object list,
      - attached-editor tools bypass `ChatToolAvailability` filtering,
      - attached-editor system-message guidance is appended only when an
        editor is attached.
    - `ModelContextProtocolServer` / MCP-path test
      - `tools/list` metadata and tool execution use the same ordered
        tool-object list,
      - MCP includes attached-editor tools without depending on chat-only
        wiring.
  - **Manual tests:** N/A
  - Verification command:
    - `gradle -Djava.net.preferIPv6Addresses=true -Djava.awt.headless=true :freeplane_plugin_ai:test :freeplane:test`
- **Implementation notes:**
  - **Tradeoffs:**
    - Attached-editor tools stay registered even with no active
      attachment, and `AIChatService` bypasses
      `ChatToolAvailability` by always unioning those tool names into
      chat registration. That keeps chat and MCP on one stable tool
      surface while still letting tool behavior enforce attachment
      state.
    - `ToolExecutorFactory` now sorts tool methods by signature within
      each tool object instead of depending on reflection order. That
      gives deterministic merged registration without adding a second
      ordering mechanism.

## Subtask: Attach Script Editor to AI chat and expose compile diagnostics
- **Status:** review
- **Scope:** Add an explicit AI button to `ScriptEditorPanel`, adapt it
  to the shared attachment and attached-code contracts, and let AI
  compile the current script against the current Groovy compiler path.
- **Motivation:** This is the simpler editor integration and should
  validate the attached-code contract before the formula-specific submit
  lifecycle work.
- **Constraints:**
  - Keep `ScriptEditorPanel` non-modal behavior unchanged.
  - Do not bypass the dialog's buffered script model.
  - Reuse the current Groovy compilation path and classpath. Do not add
    a separate script compiler.
  - Existing script execution behavior stays unchanged and out of scope
    for this task.
  - Detach only on actual close after the existing close-confirmation
    flow accepts the dialog disposal.
- **Briefing:** `ScriptEditorPanel` already has a top button row in its
  `JMenuBar`, uses `mScriptTextField` as the live text component, and
  already has script-plugin `Activator.getBundleContext()` available for
  OSGi lookup.
- **Research:**
  - `storeCurrent()` writes `mScriptTextField.getText()` into the
    dialog-local script model.
  - `RunAction` already stores current text and delegates to
    `IScriptModel.executeScript(...)`, but that execution path is not
    part of this task.
  - The attached script editor is always a Groovy editor, so compile-only
    diagnostics can target the current `GroovyScript` path directly.
- **Design:**
  - Add an `AI` button to the existing top `JMenuBar` button row next to
    the other editor actions.
  - Resolve `AiChatAttachmentService` through the script plugin bundle
    context.
  - Change the live script editor pane content type from `text/groovy`
    to `text/x-freeplane-script-groovy`.
  - When the AI button attaches the editor, pass
    `mScriptTextField.getContentType()` to
    `AiChatAttachmentService.attachEditor(...)`.
  - `compileForAi()` must call
    `ScriptingEngine.compileGroovyScriptForDiagnostics(...)` and map the
    result into `AiChatCodeOperationResult` with operation type
    `COMPILE`, trigger `AI`, source fingerprint, compiler diagnostics,
    error message, and line number.
  - After `compileForAi()` returns, store it as the latest issue only
    when it is unsuccessful, clear the current issue on success, and
    expose that state through `getAttachedEditorLatestIssue()`.
  - On confirmed dialog close in `disposeDialog(...)`:
    - detach the stored `AiChatAttachment` if present,
    - then continue existing save or cancel flow unchanged.
  - Add translation keys for the new AI button and any new compile
    labels introduced in this task.

  ```plantuml
  @startuml
  set separator none
  package "freeplane" {
    package "org.freeplane.features.ai.code" {
      interface AiChatCodeEditor
      interface AiChatAttachment
      class AiChatCodeOperationResult
    }
  }
  
  package "freeplane_plugin_script" {
    package "org.freeplane.plugin.script" {
      class ScriptEditorPanel {
        - aiChatAttachment : AiChatAttachment
        + getText() : String
        + replaceText(text : String)
        + compileForAi() : AiChatCodeOperationResult
        - disposeDialog()
      }
  
      class ScriptingEngine {
        + compileGroovyScriptForDiagnostics(script, permissions)
      }
    }
  }
  
  ScriptEditorPanel ..|> AiChatCodeEditor
  ScriptEditorPanel --> ScriptingEngine : compileGroovyScriptForDiagnostics(...)
  ScriptEditorPanel --> AiChatAttachment : recordIssue(...)
  @enduml
  ```

- **Test specification:**
  - **Automated tests:**
    - add unit coverage for any extracted helper that maps compiler
      diagnostics into `AiChatCodeOperationResult`,
    - add unit coverage for
      `ScriptingEngine.compileGroovyScriptForDiagnostics(...)` when the
      helper is introduced in a testable form.
  - **Manual tests:**
    - open script editor, attach to AI chat, rewrite the live script,
      compile it from AI, and verify compilation errors are returned
      with line information,
    - verify Groovy syntax highlighting still works in the script editor
      after the content-type change,
    - save after an AI rewrite and verify persisted script content
      matches the AI-updated live text,
    - cancel after an AI rewrite and verify the original script remains
      unchanged,
    - attach script editor after another editor and verify the earlier
      attachment is replaced,
    - compile from AI, then manually ask AI for help and verify the
      latest issue is readable through
      `getAttachedEditorLatestIssue()`.
  - Verification command:
    - `gradle -Djava.net.preferIPv6Addresses=true -Djava.awt.headless=true :freeplane_plugin_script:test :freeplane_plugin_ai:test`
- **Implementation notes:**
  - **Tradeoffs:**
    - Script-editor compilation reuses `GroovyScript` by adding a
      compile-only entry point instead of introducing a second Groovy
      compiler path. That keeps classpath, security, and parse behavior
      aligned with existing script execution while avoiding execution.

## Subtask: Attach Formula Editor to AI chat, preserve map reference picking, and validate formulas before commit
- **Status:** review
- **Scope:** Add an explicit AI button to `FormulaEditor`, replace the
  current whole-window blocker with a center-scoped selection overlay,
  let AI compile the current formula, change formula submit so final
  validation happens before commit while the editor remains visible, and
  optionally send a built-in AI repair request when the user approves it
  after a failed submit.
- **Motivation:** `FormulaEditor` is the real interaction problem. It
  already uses a non-modal dialog, but its current root-pane selection
  overlay blocks the rest of the frame, its current preview logic
  evaluates formula text while the editor is still open, and its submit
  lifecycle hides the dialog before commit.
- **Constraints:**
  - Overlay only the frame's `BorderLayout.CENTER` component, not the
    whole root pane.
  - Do not call `setFocusableWindowState(false)` on the main window.
  - Preserve existing formula-reference insertion behavior from map and
    attribute-table interaction.
  - Keep the attached-editor text path on `FormulaEditor.textEditor`
    only.
  - Reuse current formula permissions, script bindings, and
    `TextController.withNodeNumbering(true)` semantics.
  - Do not evaluate the live formula while the editor is open except for
    the final submit-time validation.
  - Do not add an AI formula-execution tool or a general-purpose
    in-editor Execute button in this task.
  - Failed submit must not commit the edited formula.
  - The user must see diagnostics before any AI repair request is sent.
  - Successful submit must close and detach the editor.
- **Briefing:** `FormulaEditor` is the only `EditNodeDialog` subclass in
  the repository, so small dialog-button and submit-hook extension
  points can stay local to this feature. `FormulaTextTransformer` is
  still the normal runtime entry point for committed formula evaluation.
- **Research:**
  - `FormulaEditor.show(Window)` currently installs
    `new GlassPaneManager(...)` directly.
  - `FormulaEditor.addPreviewPane(...)` currently evaluates once during
    dialog configuration and uses the original `getText()` value rather
    than the live current editor text.
  - `FormulaTextTransformer.transformContent(...)` already applies
    `textController.withNodeNumbering(true, ...)`, so committed runtime
    evaluation already defines the node-numbering context that submit
    validation should reuse.
  - `EditNodeDialog.LongNodeDialog.submit()` currently hides the dialog
    before commit.
  - `ScriptContext` already carries the node, base URL, and permissions
    context needed by Groovy execution; dependency writes are local to
    the `access*` methods.
- **Design:**
  - `CenterPaneNodeSelectionOverlay`:
    - attaches a transparent overlay only above the frame's current
      `BorderLayout.CENTER` component,
    - reuses `DelayedMouseListener(new GlassPaneNodeSelector(...), 2, 1)`
      for event translation,
    - never disables frame focus,
    - keeps overlay bounds synchronized with the center component.
  - Leave existing `GlassPaneManager` unchanged for other code paths.
  - Change the formula editor pane content type from `text/groovy` to
    `text/x-freeplane-formula-groovy`.
  - `FormulaEditor` activates the overlay on show, adds an `AI` button
    through `addAdditionalButtons(...)`, and attaches a live-editor
    adapter through `AiChatAttachmentService.attachEditor(...,
    textEditor.getContentType())` when the AI button is clicked.
  - Replace the current one-shot preview evaluation from dialog setup.
    Opening or attaching the formula editor must not evaluate the live
    formula text.
  - Change `EditNodeDialog` submit lifecycle:
    - add protected `boolean submitEditedText(String editedText)` to
      `EditNodeDialog`,
    - default implementation calls `getEditControl().ok(editedText)` and
      returns `true`,
    - change `LongNodeDialog.submit()` to call that hook first and only
      hide the dialog when it returns `true`.
  - `FormulaEditor.submitEditedText(String editedText)` must:
    - if the current text no longer starts with `=`:
      - clear any stale issue on the active attachment,
      - commit plain text through `getEditControl().ok(editedText)`,
      - return `true`,
    - otherwise run pre-commit validation while the editor remains open,
      - on validation success:
        - clear any stale issue on the active attachment,
        - commit through `getEditControl().ok(editedText)`,
        - return `true`,
      - on validation failure:
        - if attached, record the issue on the active attachment,
        - show a popup containing the diagnostics and the question
          whether AI should try to fix the formula,
        - if the user answers Yes:
          - if attached, send the repair request into the existing owning
            chat session,
          - if unattached, attach the editor using the normal attach-mode
            setting and normal replacement behavior, then send the
            repair request,
          - show/focus the chosen chat session,
        - if the user answers No, send nothing to chat,
        - keep the editor open,
        - return `false`.
  - `compileForAi()` for `FormulaEditor` must:
    - if the current text does not start with `=`:
      - return an unsuccessful `AiChatCodeOperationResult` whose error
        makes clear that the current content is not a formula,
    - otherwise compile only the body after the leading `=`,
      - use `ScriptingPermissions.getFormulaPermissions()`,
      - do not execute,
      - do not write persistent formula cache or dependency state,
      - store the result as the latest issue only when it is
        unsuccessful,
      - clear the current issue on success.
  - Add temporary submit validation support:
    - add `ScriptContext.withDependencyTracking(enabled)`,
    - when dependency tracking is disabled, `accessNode(...)`,
      `accessBranch(...)`, `accessClones(...)`, `accessAll(...)`, and
      `accessGlobalNode()` must not write `FormulaDependencies`,
    - add `FormulaUtils.validateFormula(node, formulaText, outStream,
      errorHandler)` that:
      - preserves current cycle detection, non-null-result check,
        formula permissions, and node bindings,
      - bypasses `FormulaCache`,
      - uses a `ScriptContext` with dependency tracking disabled,
      - evaluates only for the submit-time validation and returns the
        runtime result or throws.
    - add `FormulaSubmitValidationSupport.validateSubmittedFormula(...)`
      that:
      - wraps validation in
        `TextController.getController().withNodeNumbering(true, ...)`,
      - captures standard output, result, error category, error
        message, and line number,
      - maps them into `AiChatCodeOperationResult` with operation type
        `SUBMIT_VALIDATION`.
  - Successful pre-commit evaluation result reuse:
    - if promoting the successful temporary evaluation result into
      post-commit state is simple and low-risk, do it,
    - otherwise accept one normal post-commit re-evaluation after
      commit.
  - Add a fixed built-in repair prompt for formula submit failures.
    The repair request payload must include that prompt, the current
    editor text, and the captured issue.
  - Add static bundle-context storage to formula-plugin `Activator` so
    `FormulaEditor` can resolve `AiChatAttachmentService` without a
    direct dependency on AI-plugin implementation classes.

  ```plantuml
  @startuml
  set separator none
  package "freeplane" {
    package "org.freeplane.features.ai.code" {
      interface AiChatCodeEditor
      interface AiChatAttachment
      class AiChatCodeOperationResult
      class AiChatRepairRequest
    }
  
    package "org.freeplane.features.text.mindmapmode" {
      class EditNodeDialog {
        # addAdditionalButtons(buttonPane : JPanel)
        # submitEditedText(editedText : String) : boolean
      }
    }
  
    package "org.freeplane.view.swing.ui.mindmapmode" {
      class CenterPaneNodeSelectionOverlay
    }
  }
  
  package "freeplane_plugin_formula" {
    package "org.freeplane.plugin.formula" {
      class FormulaEditor {
        - aiChatAttachment : AiChatAttachment
        + getText() : String
        + replaceText(text : String)
        + compileForAi() : AiChatCodeOperationResult
        # submitEditedText(editedText : String) : boolean
      }
  
      class FormulaSubmitValidationSupport {
        + validateSubmittedFormula(node, formulaText) : AiChatCodeOperationResult
      }
  
      class Activator
    }
  }
  
  package "freeplane_plugin_script" {
    package "org.freeplane.plugin.script" {
      class FormulaUtils {
        + validateFormula(node, formulaText, outStream, errorHandler) : Object
      }
  
      class ScriptContext {
        + withDependencyTracking(enabled : boolean) : ScriptContext
      }
  
      class ScriptingPermissions {
        + getFormulaPermissions() : ScriptingPermissions
      }
    }
  }
  
  EditNodeDialog <|-- FormulaEditor
  FormulaEditor ..|> AiChatCodeEditor
  FormulaEditor --> CenterPaneNodeSelectionOverlay
  FormulaEditor --> FormulaSubmitValidationSupport
  FormulaEditor --> AiChatAttachment
  FormulaEditor --> Activator : bundle context
  FormulaSubmitValidationSupport --> FormulaUtils
  FormulaUtils --> ScriptContext
  FormulaUtils --> ScriptingPermissions
  @enduml
  ```

  ```plantuml
  @startuml
  actor User
  participant "FormulaEditor" as FE
  participant "FormulaSubmitValidationSupport" as Validate
  participant "AiChatAttachmentService" as AttachSvc
  participant "AiChatAttachment" as Attach
  participant "Repair popup" as Popup
  participant "AI chat" as AI
  
  User -> FE: click AI
  FE -> AttachSvc: attachEditor(live editor, contentType)
  AttachSvc --> FE: Attach
  Attach -> AI: show chosen chat session
  User -> FE: submit formula
  FE -> Validate: validateSubmittedFormula(current text)
  alt success
    Validate --> FE: success
    FE -> Attach: clearIssue()
    FE -> FE: commit text
    FE --> User: close editor
  else failure
    Validate --> FE: diagnostics
    FE -> Attach: recordIssue(issue)
    FE -> Popup: show diagnostics + ask AI?
    alt yes
      Popup --> FE: yes
      FE -> Attach: requestRepair(prompt, text, issue)
      Attach -> AI: show chosen chat session + send request
    else no
      Popup --> FE: no
    end
    FE --> User: keep editor open
  end
  @enduml
  ```

- **Test specification:**
  - **Automated tests:**
    - add unit coverage for `FormulaSubmitValidationSupport`,
    - add unit coverage for `ScriptContext.withDependencyTracking(...)`
      and the disabled dependency-write path,
    - add unit coverage for `FormulaUtils.validateFormula(...)` to
      verify temporary validation does not populate persistent formula
      cache or dependency state,
    - do **not** claim automated overlay-unblocking coverage unless a
      test actually verifies the right-side AI chat remains reachable.
  - **Manual tests:**
    - open formula editor, click map nodes, and confirm reference
      insertion still works,
    - attach formula editor to AI chat, compile the current formula from
      AI, and verify compilation errors return line information without
      executing the formula,
    - verify Groovy syntax highlighting still works in the formula editor
      after the content-type change,
    - verify opening or attaching the formula editor no longer evaluates
      the live formula text,
    - submit a failing formula and verify the text is not committed, the
      editor stays open, and the popup shows diagnostics before any AI
      request is sent,
    - for an attached failing formula, choose Yes and verify the repair
      request goes to the owning attached chat session,
    - for an attached failing formula, choose No, manually ask AI for
      help, and verify the latest issue is readable through
      `getAttachedEditorLatestIssue()`,
    - for an unattached failing formula, choose Yes and verify the
      editor attaches using the same attach-mode setting and the repair
      request goes to a new chat by default or to the current chat when
      the setting is changed,
    - remove the leading `=` from the text, verify
      `compileAttachedEditorContent()` reports not-a-formula, then submit and
      verify plain text is committed and the editor closes,
    - verify successful formula submit closes the editor and detaches
      it,
    - verify the chat tab remains clickable and typable while the dialog
      stays open,
    - click map nodes again after attachment and verify reference
      insertion still works,
    - close the formula editor and verify attached-editor AI requests now
      fail with `No editor is attached.`
  - Verification command:
    - `gradle -Djava.net.preferIPv6Addresses=true -Djava.awt.headless=true :freeplane_plugin_ai:test :freeplane:test`
- **Implementation notes:**
  - **Tradeoffs:**
    - The formula-selection overlay uses a scoped glass pane whose
      hit-testing only claims the frame's `BorderLayout.CENTER`
      component. That preserved existing `GlassPaneNodeSelector`
      behavior without changing the older whole-window
      `GlassPaneManager` path.
    - Submit validation reuses `FormulaUtils` with a no-cache,
      dependency-tracking-disabled path and accepts normal post-commit
      reevaluation instead of trying to promote temporary validation
      results into `FormulaCache`.
