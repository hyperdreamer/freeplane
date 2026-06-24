# Task: Attached script repair consent and callback output
- **Task Identifier:** 2026-06-22-script-output
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
  Script output is currently redirected during Groovy execution in
  `GroovyScript.execute(...)` and attached-editor runs use
  `ScriptEditorPanel.runCode(...)`.

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
  Non-editor script file execution through `ScriptingEngine.executeScript(File, ...)`
  uses `ScriptRunner` without an attached editor; `ScriptRunner` has a
  default output stream of `System.out` unless a caller supplies a
  different stream.

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
