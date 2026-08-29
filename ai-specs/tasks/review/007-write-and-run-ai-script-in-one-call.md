# Task: Write and run an AI-owned script in one AI-tool call
- **Task Identifier:** 2026-07-17-evaluate
- **Scope:**
  Add a shared `writeAndRunCode` AI tool that accepts
  `CodeStateContent`, stores that content as the current AI-owned
  script, then compiles and runs it in the same call. Reuse the same
  AI-owned script storage, validation, execution, diagnostics, and user
  approval dialog behavior as the existing separate `writeCode`,
  `compileCode`, and `runCode` flow. Expose the tool through the shared
  LangChain chat and MCP code-tool surfaces. Do not add attached-editor
  execution through this tool.
- **Motivation:**
  The current AI-host workflow may require `readCode` only to obtain the
  current state token before `writeCode` and `runCode`. For short
  development loops, the user wants one call that stores the script,
  reuses the usual AI script dialog when approval is required, and
  leaves the resulting script available for later inspection or edits.
- **Scenario:**
  - AI has Groovy source and optional JSON arguments for an AI-owned
    script.
  - AI calls `writeAndRunCode` with `content.sourceText` and optional
    `content.argumentsJsonText`.
  - Freeplane stores that content as the current AI-owned script,
    compiles it, and runs it using the same selected-node lookup,
    permissions, diagnostics, stdout capture, and structured-result
    conversion as the existing AI-owned run flow.
  - If the current AI execution policy requires user approval,
    Freeplane shows the usual AI script dialog with that stored content,
    allows the user to edit it, returns the usual waiting response, and
    later runs the user-approved dialog content.
  - Success returns stdout and structured result. Compile or run
    failures return the same failure-state and diagnostic contract as
    AI-owned `runCode`.
  - After the call, `readCode(host=AI)` shows the stored AI-owned
    content and latest state from that flow, including any user edits
    made in the approval dialog before the final run.
  - `readCode(host=ATTACHED_EDITOR)` remains unchanged.
- **Constraints:**
  - Reuse the existing AI-owned script path for content storage, JSON
    argument parsing, Groovy compile diagnostics, selected-node lookup,
    permission selection, stdout capture, structured-result conversion,
    and run-listener notifications.
  - Persist the provided content into the AI-owned host so the user can
    inspect or modify it in the usual dialog and later via
    `readCode(host=AI)`.
  - Keep AI-started versus user-started permission and dialog behavior
    aligned with `AI_SCRIPT_EXECUTION_POLICY` and
    `AI_SCRIPT_USER_RUN_PERMISSION_MODE`, including shown-user-run
    waiting and unrestricted user runs.
  - Keep the new tool AI-host-only and script-only; do not add
    attached-editor, formula, or file-backed execution through this
    tool.
  - Do not require a prior `readCode` or an `expectedStateToken` for
    this one-call operation. Existing token-checked `writeCode`,
    `compileCode`, and `runCode` remain unchanged.
- **Briefing:**
  - Shared code-tool API: `freeplane/src/main/java/org/freeplane/features/ai/code`
  - Shared chat/MCP tool surface: `freeplane_plugin_ai/.../tools/code/AiCodeToolSet`
  - Code-tool authorization: `AiCodeOperationAuthorizer`,
    `ModelContextProtocolToolCallAuthorizer`
  - AI-host routing: `RoutingAiCodeHostService`
  - AI-owned storage, dialog, compile/run behavior:
    `freeplane_plugin_script/.../AiOwnedScriptHostService`
  - MCP delayed completion for user-approved AI runs:
    `ModelContextProtocolAiCodeHostService`,
    `ModelContextProtocolToolDispatcher`
- **Research:**
  - `AiCodeHostService` currently exposes `readCode`, `writeCode`,
    `compileCode`, `runCode`, and `evaluateFormula`. There is no one-call
    entry point that both stores and runs AI-owned script content.
  - `AiCodeToolSet` currently exposes `readCode`, `writeCode`,
    `compileCode`, and `runCode`. Its chat guidance explicitly says that
    `compileCode` and `runCode` work on stored host state, not on source
    text passed directly in the tool call.
  - `AiCodeOperationAuthorizer` and
    `ModelContextProtocolToolCallAuthorizer` currently authorize code
    tools by operation name plus host. `runCode` also requires existing
    stored runnable script content.
  - `AiOwnedScriptHostService.doWriteCode(...)` already persists
    `CodeStateContent`, updates the dialog view, and records the latest
    AI-owned state.
  - `AiOwnedScriptHostService.doRunCode(...)` already synchronizes
    dialog edits back into `currentScript`, validates JSON and Groovy,
    applies the execution policy, shows the usual dialog for
    `SHOWN_USER_RUN`, and records the resulting latest state.
  - `AiOwnedScriptHostService.runFromDialog(...)` already persists user
    edits from the AI script dialog before validation and execution.
    That matches the required approval-time editing behavior.
  - `ModelContextProtocolAiCodeHostService` and
    `ModelContextProtocolToolDispatcher` already handle delayed
    completion for AI `runCode` responses that return
    `WAITING_FOR_USER_RUN`. The new one-call tool needs the same MCP
    completion path.
- **Design:**
  - Add `WriteAndRunCodeRequest` to
    `org.freeplane.features.ai.code` with one field,
    `content: CodeStateContent`. Add the corresponding
    `WriteAndRunCodeToolRequest` in `freeplane_plugin_ai` so chat and
    MCP accept:
    `{"request":{"content":{"sourceText":...,"argumentsJsonText":...}}}`.
    The request has no `host` and no `expectedStateToken`.
  - Add `RunCodeResponse writeAndRunCode(WriteAndRunCodeRequest
    request)` to `AiCodeHostService`.
  - Route `writeAndRunCode(...)` only to the AI host.
    `RoutingAiCodeHostService` delegates to the AI host.
    `SingleEditorAttachmentService` and the fallback host reject the
    operation as unsupported, consistent with the existing AI-only
    routing used for `evaluateFormula(...)`.
  - Add `writeAndRunCode(...)` to `AiCodeToolSet`, include it in tool
    registration and summaries, and update `systemMessageForChat(...)`
    so AI-host guidance states that this tool stores provided content in
    the AI host and runs it without a prior `readCode`.
  - Authorize `writeAndRunCode` whenever script execution is available.
    Unlike `runCode`, it must not require pre-existing stored code,
    because the operation creates that code state itself.
  - Implement `AiOwnedScriptHostService.doWriteAndRunCode(...)` by
    reusing the same persistent content ownership as `doWriteCode(...)`
    and the same execution path as `doRunCode(...)`.
    The method should:
    - sanitize and store the provided content into
      `currentScript.storedContent`;
    - update the latest edited AI-owned state and refresh the dialog if
      it is open; and then
    - continue through the existing stored-script validation/run path
      without demanding an external `expectedStateToken`.
  - Refactor only enough shared host logic to avoid duplicating storage
    and run behavior across `doWriteCode(...)`, `doRunCode(...)`, and
    `doWriteAndRunCode(...)`.
  - Keep `readCode`, `writeCode`, `compileCode`, and `runCode`
    unchanged. `writeAndRunCode` is an additional convenience entry
    point, not a replacement.
  - `writeAndRunCode` returns the normal persistent AI-owned run
    contract:
    - `host = ScriptHost.AI`
    - `contentType = text/x-freeplane-script-groovy`
    - `stateToken` is the normal persisted AI-owned token, not `null`
    - `codeState`, `runInitiator`, diagnostics, `errorMessage`,
      `stdout`, and `structuredResult` keep the same meanings as in
      `runCode`
  - When `AI_SCRIPT_EXECUTION_POLICY` is `SHOWN_USER_RUN`,
    `writeAndRunCode` must show the existing AI script dialog with the
    newly stored content, return the same
    `WAITING_FOR_USER_RUN` response used by `runCode`, and let
    `runFromDialog(...)` / `dialogCancelled()` produce the final result.
    Any user edits in that dialog must persist exactly as they already
    do today.
  - Generalize the MCP delayed-completion path from `runCode`-only to
    AI execution tools that can return `WAITING_FOR_USER_RUN`.

  ```plantuml
  @startuml
  participant Agent
  participant AiCodeToolSet as Tools
  participant RoutingAiCodeHostService as Routing
  participant AiOwnedScriptHostService as Host
  participant CurrentScript as StoredState
  participant AiOwnedScriptDialog as Dialog

  Agent -> Tools : writeAndRunCode(content)
  Tools -> Routing : writeAndRunCode(request)
  Routing -> Host : doWriteAndRunCode(request)
  Host -> StoredState : storedContent = sanitize(content)
  Host -> Host : latestState = EDITED
  alt SHOWN_USER_RUN policy
    Host -> Dialog : show usual AI script dialog
    Host --> Agent : WAITING_FOR_USER_RUN
    User -> Dialog : inspect/edit and Run or Cancel
    Dialog -> Host : runFromDialog(editedContent) or dialogCancelled()
    Host -> StoredState : persist edited content
    Host -> Host : validate and execute
  else direct AI run
    Host -> Host : validate and execute
  end
  Host -> StoredState : latestState = final response
  Host --> Agent : final RunCodeResponse or deferred MCP completion
  @enduml
  ```
- **Test specification:**
  - **Automated tests:**
    - `AiOwnedScriptHostServiceTest`
      - `writeAndRunCodeCreatesAiOwnedStateAndRunsIt`
      - `writeAndRunCodeReplacesExistingAiOwnedStateWithoutPriorRead`
      - `writeAndRunCodeReturnsGroovyDiagnosticLocations`
      - `writeAndRunCodeShownUserRunReusesDialogAndPersistsUserEdits`
    - `RoutingAiCodeHostServiceTest`
      - `writeAndRunCodeRoutesRequestsToAiHost`
    - `SingleEditorAttachmentServiceTest`
      - `writeAndRunCodeRejectsAttachedEditors`
    - `AiCodeToolSetTest`
      - `writeAndRunCodePublishesSummariesAndForwardsContent`
      - `systemMessageMentionsWriteAndRunCodeAsOneCallPersistentExecution`
    - `AiCodeOperationAuthorizerTest`
      - `scriptExecutionAvailabilityAddsWriteAndRunCode`
      - `writeAndRunCodeDoesNotRequireExistingStoredAiCode`
      - `writeAndRunCodeRejectsWhenScriptExecutionIsUnavailable`
    - `ModelContextProtocolToolCallAuthorizerTest`
      - `writeAndRunCodeDelegatesToAiCodeOperationAuthorizerForAiHost`
    - `ModelContextProtocolAiCodeHostServiceTest`
      - `writeAndRunCodeReturnsWaitingAndAwaitReturnsFinalUserRunResponseWithinTimeout`
    - `ModelContextProtocolToolDispatcherTest`
      - `dispatchBindsWriteAndRunCodeRequestFieldsForAiCodeTools`
      - `dispatchCompletesWaitingWriteAndRunCodeWithTerminalResponse`
- **Implementation notes:**
  - **Interpretations:** The one-call operation uses the newly stored current state token internally only to reuse the existing AI-owned run path; callers provide neither a token nor a prior `readCode` result.
  - **Tradeoffs:** Kept `writeAndRunCode` as a separate public operation while extracting only the shared persistent-content storage and MCP delayed-completion mechanics needed to avoid parallel execution paths.
