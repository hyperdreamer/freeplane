# Task: Attached script repair consent and callback output
- **Task Identifier:** 2026-06-22-script-output
- **Scope:**
  Govern AI repair consent and asynchronous callback output for script
  and formula editor interactions with attached AI code state.

- **Motivation:**
  Editor-originated AI repair must be explicit and consent-gated.
  Script-facing asynchronous AI callbacks should write to the output
  target that belongs to the originating script execution when such a
  target exists.

- **Briefing:**
  Relevant code spans the script, formula, and AI plugins. The script
  editor implements `AiCodeEditor` in
  `freeplane_plugin_script/src/main/java/org/freeplane/plugin/script/ScriptEditorPanel.java`.
  Formula editor repair behavior is in
  `freeplane_plugin_formula/src/main/java/org/freeplane/plugin/formula/FormulaEditor.java`.
  Attached editor state and repair routing are handled by
  `freeplane_plugin_ai/src/main/java/org/freeplane/plugin/ai/code/SingleEditorAttachmentService.java`.
  Script-facing AI requests are exposed through
  `freeplane_plugin_script/src/main/java/org/freeplane/plugin/script/proxy/ControllerProxy.java`
  and implemented by
  `freeplane_plugin_ai/src/main/java/org/freeplane/plugin/ai/chat/request/ScriptAiRequestService.java`.

## Subtask: Consent-gated repair and callback output
- **Status:** done

- **Scope:**
  Fix two attached-script editor behaviors:
  - manual run failures of an attached script editor must not
    automatically submit a chat message or start an AI repair request;
  - `println` executed inside asynchronous script-facing AI callbacks
    must be routed back to the originating script output target. For
    attached script-editor runs, that target is the script editor
    output window. For non-editor script runs, that target is the
    script execution output stream when one exists.

  In scope:
  - keep attached script failure state available as attached code state
    for AI tools and repair context;
  - make script manual-failure repair consent follow the same active
    confirmation boundary used by the formula editor;
  - cover script-facing `askAi(...)` callback output, and the shared
    callback path used by `runAiPrompt(...)`;
  - preserve behavior for non-editor scripts that have no durable
    output target.

  Out of scope:
  - changing `AiRequestOptions.exactSystemMessage(...)` behavior;
  - changing tool authorization or attached-editor write permissions;
  - making script-facing AI requests synchronous;
  - changing AI-owned host user-run follow-up behavior unless required
    to preserve the attached-editor fix;
  - changing UI saved-prompt launch behavior in `AIChatPanel.runPrompt(...)`,
    which has no script callback output path.

- **Motivation:**
  A manually run attached script that called `c.askAi(...)` failed
  after returning an unsupported `AiRequestHandleImpl` structured
  result. The attached-editor failure was then automatically posted to
  the AI chat and the AI modified the script. The expected repair flow
  is consent-gated. A later workaround returned `null`, but output from
  the asynchronous callback was not shown in the script editor output
  window because the original run-time output capture had already
  ended.

- **Scenario:**
  A user attaches the script editor to AI and manually runs a script.
  If the script run fails, the editor records the failure as the
  current attached code state and shows a repair confirmation prompt.
  If the user declines or closes the prompt, no chat message is
  submitted and no repair request starts. If the user accepts, the
  editor sends the repair request with the recorded failure state.

  A user runs an attached script that starts `c.askAi(...)` or
  `c.runAiPrompt(...)` and returns before the AI result arrives. When
  the asynchronous callback later executes, `println` output from the
  callback is appended to the same script editor output window
  associated with the originating manual run.

  A user runs a script from a file, menu, or another non-editor script
  entry point. If that execution has an output stream, asynchronous AI
  callback `println` output is written to that same output stream. If
  no durable output target is available, no editor window is invented
  and existing behavior is preserved.

- **Constraints:**
  - `recordCodeState(...)` must be passive with respect to chat
    submission and provider requests for attached editor manual
    failures.
  - `requestRepair(...)` remains the operation that starts an AI repair
    request.
  - Callback output routing must restore the previous `System.out`
    after the callback returns.
  - Callback output routing must not keep the original run-time
    `CapturedPrintStream` open after the initial script run finishes.
  - Non-editor script runs must not create or attach a script editor
    only to display callback output.
  - Existing permission checks for script-facing AI requests must stay
    in force.

- **Research:**
  ```plantuml
  @startuml
  title Current attached script manual failure
  participant User
  participant ScriptEditorPanel
  participant SingleEditorAttachmentService
  participant AIChatPanel

  User -> ScriptEditorPanel : press Run
  ScriptEditorPanel -> ScriptEditorPanel : runCode(..., USER)
  ScriptEditorPanel -> ScriptEditorPanel : renderManualRunResult(response)
  ScriptEditorPanel -> SingleEditorAttachmentService : recordCodeState(failure with runInitiator USER)
  SingleEditorAttachmentService -> AIChatPanel : submitMessageToSession(status text)
  AIChatPanel -> AIChatPanel : start visible AI request
  @enduml
  ```

  `ScriptEditorPanel.RunAction` calls `runCode(...,
  ScriptRunInitiator.USER)`, renders the result, records the attached
  code state, and then calls `showManualRunFailure(...)`. When an AI
  attachment exists, `showManualRunFailure(...)` returns without
  showing a local error dialog.

  `SingleEditorAttachmentService.recordCodeState(...)` stores the
  normalized state. If `shouldAutoPostAttachedManualFailure(...)` is
  true, it calls `AIChatPanel.submitMessageToSession(...)` with an
  automatic code-status message. That method switches to the session,
  creates a visible request runtime, and starts a visible provider
  request. The predicate is true for a user-initiated attached script
  failure because the state has `runInitiator == USER` and a failing
  `CodeState`.

  ```plantuml
  @startuml
  title Current formula validation failure
  participant User
  participant FormulaEditor
  participant SingleEditorAttachmentService

  User -> FormulaEditor : submit invalid formula
  FormulaEditor -> SingleEditorAttachmentService : recordCodeState(validation failure)
  FormulaEditor -> User : show repair confirmation
  User -> FormulaEditor : choose Yes
  FormulaEditor -> SingleEditorAttachmentService : requestRepair(failure state)
  @enduml
  ```

  `FormulaEditor.submitEditedText(...)` records validation failure
  state, shows `JOptionPane.showConfirmDialog(...)`, and calls
  `requestRepair(...)` only when the user selects yes. Its failure
  state has `runInitiator == null`, so the automatic user-run failure
  predicate in `SingleEditorAttachmentService` does not fire.

  ```plantuml
  @startuml
  title Current asynchronous callback output
  participant ScriptEditorPanel
  participant GroovyScript
  participant ControllerProxy
  participant ScriptAiRequestService

  ScriptEditorPanel -> GroovyScript : execute(outStream)
  GroovyScript -> GroovyScript : System.setOut(outStream)
  GroovyScript -> ControllerProxy : c.askAi(prompt, options, callback)
  ControllerProxy -> ScriptAiRequestService : askAi(..., wrappedCallback)
  GroovyScript -> GroovyScript : restore System.out
  ScriptAiRequestService -> ControllerProxy : invoke callback later
  ControllerProxy -> ControllerProxy : callback.accept(result)
  @enduml
  ```

  `ControllerProxy.executeAiServiceCall(...)` wraps callbacks only to
  restore the originating script context on
  `ExecutingScriptContextStack`. It does not preserve an output target.
  `GroovyScript.execute(...)` sets `System.out` to the run output
  stream only for the duration of `scriptWithBinding.run()`, then
  restores the previous stream. `ScriptEditorPanel.runCode(...)` uses
  a `CapturedPrintStream` for the immediate run result, and that
  capture is closed before an asynchronous AI callback can execute.
  Non-editor script file execution through
  `ScriptingEngine.executeScript(File, ...)` uses `ScriptRunner`
  without an attached editor; `ScriptRunner` has a default output
  stream of `System.out` unless a caller supplies a different stream.

- **Analysis:**
  - Attached script manual failures should follow the formula editor
    consent boundary because both flows are editor-originated repair
    requests for attached code.
  - `recordCodeState(...)` should not submit chat messages for
    attached editor manual failures because recording state and
    requesting repair are separate operations.
  - Callback stdout should be attached to the originating script
    execution context because the callback can run after the initial
    script execution has restored `System.out`.

- **Design:**
  ```plantuml
  @startuml
  title Target attached script manual failure
  participant User
  participant ScriptEditorPanel
  participant SingleEditorAttachmentService

  User -> ScriptEditorPanel : press Run
  ScriptEditorPanel -> ScriptEditorPanel : runCode(..., USER)
  ScriptEditorPanel -> ScriptEditorPanel : renderManualRunResult(response)
  ScriptEditorPanel -> SingleEditorAttachmentService : recordCodeState(failure)
  ScriptEditorPanel -> User : show repair confirmation
  User -> ScriptEditorPanel : choose Yes
  ScriptEditorPanel -> SingleEditorAttachmentService : requestRepair(failure state)
  @enduml
  ```

  `SingleEditorAttachmentService.recordCodeState(...)` stores the
  latest normalized attached code state and does not call
  `AIChatPanel.submitMessageToSession(...)` for attached editor manual
  failures. The existing `requestRepair(...)` path remains responsible
  for switching to the owning chat and starting the repair request.

  `ScriptEditorPanel.RunAction` uses the recorded failure state to
  show a repair confirmation prompt for attached script failures. If
  the user accepts, it calls `requestAttachedManualRepair(...)`. If
  the user declines, it leaves only the recorded code state.

  ```plantuml
  @startuml
  set separator none
  package "target callback output routing" {
    class ScriptContext {
      + getCallbackOutputStream() : PrintStream
      + withCallbackOutputStream(PrintStream) : ScriptContext
    }
    class GroovyScript {
      + execute(NodeModel, PrintStream, IFreeplaneScriptErrorHandler, ScriptContext) : Object
    }
    class GenericScript {
      + execute(NodeModel, PrintStream, IFreeplaneScriptErrorHandler, ScriptContext) : Object
    }
    class ControllerProxy {
      + askAi(String, AiRequestOptions, AiRequestCallback) : AiRequestHandle
      + runAiPrompt(String, Duration, AiRequestCallback) : AiRequestHandle
      + runAiPrompt(String, AiRequestOptions, AiRequestCallback) : AiRequestHandle
    }
    class ScriptEditorPanel {
      + runCode(RunCodeRequest) : RunCodeResponse
    }

    GroovyScript --> ScriptContext
    GenericScript --> ScriptContext
    ControllerProxy --> ScriptContext
    ScriptEditorPanel --> GroovyScript
  }
  @enduml
  ```

  `ScriptContext` carries an optional callback output stream. Script
  execution code sets that stream on the effective context when a
  suitable execution output stream exists. `ControllerProxy` reads the
  stream from the originating context and, while invoking an AI
  callback, temporarily sets `System.out` to that stream, then restores
  the previous stream in a `finally` block.

  For attached editor manual runs, `ScriptEditorPanel` supplies a
  script-editor output stream suitable for appending callback output to
  the output window. The callback stream is independent of the
  immediate run's `CapturedPrintStream`, so the initial run capture can
  still be closed when `runCode(...)` returns.

  For non-editor script runs, the callback output stream is the
  originating script execution output stream when one is available. If
  no suitable stream is present, `ControllerProxy` invokes the callback
  without changing `System.out`.

- **Test specification:**
  Automated tests:
  - Recording a user-initiated attached script failure stores the
    latest attached code state and does not call
    `AIChatPanel.submitMessageToSession(...)`.
  - A script manual run failure with an attached editor requests
    repair only after the confirmation path accepts repair.
  - A script manual run failure with an attached editor and a declined
    or absent repair confirmation does not call `requestRepair(...)`.
  - Formula editor validation failure behavior remains
    confirmation-gated and still calls `requestRepair(...)` only after
    user acceptance.
  - `ControllerProxy` invokes an asynchronous `askAi(...)` callback
    with `System.out` temporarily set to the originating script
    callback output stream and restores the previous `System.out`
    afterward.
  - The shared callback-output wrapper also applies to script-facing
    `runAiPrompt(...)` callbacks without weakening AI request
    permission checks.
  - A non-editor script execution with a callback output stream routes
    asynchronous AI callback `println` output to that stream without
    creating or attaching a script editor.
  - When no callback output stream is present, script-facing AI
    callbacks keep the existing output behavior and still receive the
    originating script context.

  Manual tests:
  - Attach the script editor to AI, run a failing script, decline the
    repair prompt, and verify no automatic AI request starts.
  - Run an attached script that calls `c.askAi(...)`, returns `null`,
    and prints from its callback; verify callback output appears in
    the script editor output window after the AI result arrives.

- **Implementation notes:**
  - **Interpretations:**
    - Treated the AI-owned script host as a non-editor callback-output
      case only. Its callback output remains bound to `System.out` so
      the short-lived run capture is not kept open and AI-owned
      user-run follow-up behavior is unchanged.

## Subtask: Prompt for AI repair before attachment
- **Status:** done

- **Scope:**
  Change editor repair prompting so AI repair help is offered before an
  editor is attached. Applies to script manual-run failures and formula
  validation failures.

  In scope:
  - show the AI repair confirmation when AI is configured, even if the
    editor is not currently attached;
  - attach the editor only after the user accepts AI help;
  - record the failure state on the attachment created after acceptance
    before requesting repair;
  - disable the attach-AI button when no AI provider is configured;
  - suppress the AI repair confirmation when no AI provider is
    configured.

  Out of scope:
  - changing provider configuration UI;
  - changing tool authorization after attachment;
  - changing script callback output routing from the previous subtask.

- **Motivation:**
  The current script repair prompt is shown only when the script editor
  is already attached to AI. The desired behavior is to ask for repair
  consent first and create the attachment only when the user accepts.
  When no AI provider is configured, offering AI repair is misleading
  and the attach-AI control should not be usable.

- **Scenario:**
  A user has a configured AI provider and runs a failing script from an
  unattached script editor. Freeplane shows the AI repair confirmation.
  If the user accepts, Freeplane attaches the script editor to AI,
  records the run failure as attached code state, and sends the repair
  request. If the user declines or closes the confirmation, Freeplane
  does not attach the editor and does not send a repair request.

  A user has a configured AI provider and submits an invalid formula
  from an unattached formula editor. Freeplane shows the AI repair
  confirmation. If the user accepts, Freeplane attaches the formula
  editor, records the validation failure as attached code state, and
  sends the repair request.

  A user has no configured AI provider. Script run failures and formula
  validation failures do not show the AI repair confirmation. The
  editor's attach-AI button is disabled. The local script or formula
  error remains visible through the non-AI error path.

- **Constraints:**
  - Attachment must happen only after accepted repair confirmation when
    the editor was not already attached.
  - Existing attachments must continue to be reused.
  - Failure state must be recorded before `requestRepair(...)` starts.
  - The attach-AI button must reflect AI provider configuration and
    attachment state.
  - The repair confirmation must not be shown when AI is unavailable or
    no provider is configured.

- **Research:**
  ```plantuml
  @startuml
  title Current script repair prompt before attachment
  participant User
  participant ScriptEditorPanel
  participant AiChatAttachmentService

  User -> ScriptEditorPanel : run failing script
  ScriptEditorPanel -> ScriptEditorPanel : recordAttachedManualRunState(aiChatAttachment, failure)
  alt aiChatAttachment exists
    ScriptEditorPanel -> User : show AI repair confirmation
  else no attachment
    ScriptEditorPanel -> User : show local error only
  end
  User -> ScriptEditorPanel : click attach-AI button
  ScriptEditorPanel -> AiChatAttachmentService : attachEditor(...)
  @enduml
  ```

  `ScriptEditorPanel.showManualRunFailure(...)` currently enters the
  confirmation path only when `aiChatAttachment != null` and a recorded
  code state exists. Otherwise it shows `UITools.errorMessage(...)`.
  `AttachToAiAction` looks up `AiChatAttachmentService` and attaches
  directly. `updateAiAttachButtonState()` only mirrors selected state;
  it does not disable the button when AI is unconfigured.

  ```plantuml
  @startuml
  title Current formula repair prompt before attachment
  participant User
  participant FormulaEditor
  participant AiChatAttachmentService

  User -> FormulaEditor : submit invalid formula
  FormulaEditor -> User : show AI repair confirmation
  User -> FormulaEditor : choose Yes
  FormulaEditor -> AiChatAttachmentService : attachEditor(...)
  FormulaEditor -> AiChatAttachmentService : recordCodeState(failure)
  FormulaEditor -> AiChatAttachmentService : requestRepair(failure)
  @enduml
  ```

  `FormulaEditor.submitEditedText(...)` already shows the repair
  confirmation before attachment and attaches on acceptance, but it
  does not suppress the AI repair confirmation when no AI provider is
  configured. Its attach button also only mirrors selected state.

  `AiChatAttachmentService` exposes only `attachEditor(...)`. The
  service is registered by the AI plugin even when no provider is
  configured. `AIChatPanel` has provider-configuration knowledge in a
  private `isProviderConfigured()` method based on OpenRouter key,
  Gemini key, or Ollama service address.

- **Analysis:**
  - The AI repair confirmation should be gated by AI provider
    configuration because accepting the prompt necessarily starts an AI
    repair request.
  - The attachment service needs to expose provider availability
    because script and formula editors can only see the attachment
    service, not `AIChatPanel` internals.
  - Script repair should match the formula attach-after-accept flow
    because both are editor-originated repair requests for attached
    code.

- **Design:**
  ```plantuml
  @startuml
  set separator none
  package "target repair availability and attachment" {
    interface AiChatAttachmentService {
      + isAiConfigured() : boolean
      + attachEditor(AiChatAttachableEditor, String) : AiChatAttachment
    }
    class SingleEditorAttachmentService {
      + isAiConfigured() : boolean
      + attachEditor(AiChatAttachableEditor, String) : AiChatAttachment
    }
    class AIChatPanel {
      + isAiProviderConfigured() : boolean
    }
    class ScriptEditorPanel {
      - attachToAi() : AiChatAttachment
      - canAttachToAi() : boolean
      - updateAiAttachButtonState() : void
    }
    class FormulaEditor {
      - attachToAi() : AiChatAttachment
      - canAttachToAi() : boolean
      - updateAiAttachButtonState() : void
    }

    SingleEditorAttachmentService ..|> AiChatAttachmentService
    SingleEditorAttachmentService --> AIChatPanel
    ScriptEditorPanel --> AiChatAttachmentService
    FormulaEditor --> AiChatAttachmentService
  }
  @enduml
  ```

  `AiChatAttachmentService.isAiConfigured()` reports whether AI repair
  can be offered. `SingleEditorAttachmentService` implements it by
  delegating to `AIChatPanel.isAiProviderConfigured()`, which exposes
  the existing provider-configuration check currently held privately in
  `AIChatPanel`.

  `ScriptEditorPanel.updateAiAttachButtonState()` and
  `FormulaEditor.updateAiAttachButtonState()` disable the attach-AI
  button when `AiChatAttachmentService` is absent or reports no
  configured provider. If an editor is already attached, the selected
  state remains accurate and detach remains possible.

  ```plantuml
  @startuml
  title Target script repair prompt before attachment
  participant User
  participant ScriptEditorPanel
  participant AiChatAttachmentService

  User -> ScriptEditorPanel : run failing script
  ScriptEditorPanel -> AiChatAttachmentService : isAiConfigured()
  alt AI configured
    ScriptEditorPanel -> User : show AI repair confirmation
    User -> ScriptEditorPanel : choose Yes
    ScriptEditorPanel -> AiChatAttachmentService : attachEditor(...) if needed
    ScriptEditorPanel -> AiChatAttachmentService : recordCodeState(failure)
    ScriptEditorPanel -> AiChatAttachmentService : requestRepair(failure)
  else AI not configured
    ScriptEditorPanel -> User : show local error only
  end
  @enduml
  ```

  Script repair state is built independently of current attachment.
  When AI repair is available, `ScriptEditorPanel` shows the repair
  confirmation. If the user accepts and no attachment exists, it calls
  `attachToAi()`, records the failure on the resulting attachment, and
  calls `requestRepair(...)`. If the user declines, no attachment is
  created. If AI repair is not available, the existing local error path
  is used and no AI repair confirmation appears.

  Formula repair keeps its current attach-after-accept behavior and
  adds the same AI-availability gate. When AI repair is not available,
  formula validation failure displays local validation failure details
  without the AI repair prompt.

- **Test specification:**
  Automated tests:
  - Script manual failure with configured AI and no existing attachment
    shows the repair confirmation, attaches after accepted
    confirmation, records the failure state, and requests repair.
  - Script manual failure with configured AI and no existing attachment
    does not attach or request repair when confirmation is declined.
  - Script manual failure with no configured AI does not show the AI
    repair confirmation, does not attach, and keeps the local error
    path.
  - Formula validation failure with configured AI and no existing
    attachment attaches after accepted confirmation, records the
    validation failure state, and requests repair.
  - Formula validation failure with no configured AI does not show the
    AI repair confirmation and keeps the local validation-failure path.
  - Script and formula attach-AI buttons are disabled when no AI
    provider is configured and enabled when one is configured.
  - `SingleEditorAttachmentService.isAiConfigured()` reports the
    provider-configuration state exposed by `AIChatPanel`.

  Manual tests:
  - With AI configured, run a failing unattached script, accept repair,
    and verify the editor attaches and AI receives the repair request.
  - With no AI configured, open script and formula editors and verify
    the attach-AI buttons are disabled and AI repair prompts are not
    shown for failures.
