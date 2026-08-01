# Task: Allow AI to invoke script files

- **Task Identifier:** 2026-05-31-script-invocation
- **Scope:**
  Add a shared AI tool that invokes a script from an absolute local file
  path with optional JSON arguments. Execute the file's current content
  through the scripting engine registered for its extension, using the
  existing AI-script execution policy, permissions, selected-node context,
  output capture, diagnostics, and result conversion. Expose equivalent
  behavior through Freeplane's LangChain chat and MCP. In shown mode,
  identify the file by its full requested path and provide user-only ways
  to inspect it in a read-only internal viewer, a configured external
  program, or the system editor. Keep file discovery, complete-source
  disclosure to AI, file modification by the tool or internal viewer,
  registered menu-action execution modes, persistent per-file approval, and
  replacement of AI-owned code state out of scope.
- **Motivation:**
  AI may identify an existing script that already implements the requested
  behavior. Requiring AI to copy that script into AI-owned code state adds
  no execution-safety boundary: both forms are AI-requested code execution.
  Applying the same shown or hidden execution policy lets users retain the
  current execution control while avoiding source duplication and giving
  shown-mode users direct access to the file they may run.
- **Scenario:**
  AI requests execution using an absolute script path and optional JSON
  arguments. With `HIDDEN_AI_RUN`, Freeplane reads and executes the file's
  current content using AI-started permissions. With `SHOWN_USER_RUN`,
  Freeplane opens a dialog that initially shows the requested full path and
  arguments without loading source into an editable AI-owned draft. The user
  may display the current source in a read-only internal viewer, open the
  path with a configured external program, or open it with the system editor.
  Pressing Run executes the current file content with user-started
  permissions. If internally displayed content changed on disk, that Run
  attempt refreshes the viewer without executing; the user must inspect the
  refreshed content and press Run again. Both routes execute once against
  the node selected when execution starts and return the ordinary script-run
  response.
- **Constraints:**
  - Use the existing `SHOWN_USER_RUN` and `HIDDEN_AI_RUN` policy setting;
    do not add a file-specific execution policy or approval registry.
  - Expose invocation only when AI script execution is authorized. Preserve
    equivalent request semantics, authorization, behavior, and responses
    through LangChain chat and MCP.
  - Require an absolute path. Do not expand relative paths, browse
    directories, list configured scripts, or accept source text in the
    invocation request.
  - Accept an existing readable regular file only when its extension has a
    currently registered Freeplane scripting engine. Reject missing,
    unreadable, non-regular, unsupported, or no-longer-supported files
    without executing or trying a fallback interpretation.
  - Accept symbolic-link paths and use the requested absolute path in the
    dialog, response, and audit entry. Do not expose the resolved target
    path.
  - Accept the same optional JSON arguments as AI-owned scripts and bind them
    through the existing script-input mechanism.
  - Treat each file invocation as transient. It must not replace, modify, or
    persist as the AI-owned code draft, and it must not grant AI authority to
    modify the script file.
  - Do not return the complete file source or expose a source-reading
    operation to AI. Responses may contain the requested full path, initiation
    and execution states, available diagnostics, captured stdout, and the
    ordinary structured result. Compiler-provided diagnostics may include
    source excerpts.
  - Always execute content read from the current file when execution starts.
    Do not reuse compiled code or cached file content solely because a file
    timestamp or the existing short cache-check interval indicates that it
    may be unchanged.
  - Execute once against the current selected node. Do not apply
    `ExecuteScriptAction` modes for selected-node iteration or recursive
    execution.
  - Hidden execution uses the existing AI-started permission settings. A Run
    action in the shown dialog uses the existing user-started permission
    mode. Preserve the restriction against AI requests initiated by the
    running script.
  - The shown dialog initially displays the requested full path. Its internal
    source viewer is read-only and user-only.
  - If the internal viewer has loaded source and the current file content no
    longer matches it, pressing Run must reload the viewer and perform no
    execution. A subsequent Run may execute only if the displayed content
    still matches the current file.
  - Opening the internal viewer, configured external program, or system
    editor is not approval and must never start execution.
  - Configure an external-program executable and argument template. Replace
    its file placeholder directly without invoking a shell. If the template
    has no file placeholder, append the requested path as the final argument.
    Keep the system-editor action separate and use the operating-system file
    association.
  - Permit only one AI-owned or file-based script request to be waiting or
    running. Reject another execution request until the active request
    completes or is cancelled; do not queue or replace it.
  - Match current cancellation behavior: a shown request may be cancelled
    before execution, but an in-process script is not interruptible after it
    starts.
  - Audit every attempt in the application log with the requested full path,
    caller channel, shown or hidden policy, run initiator, observed file
    modification time when available, and terminal state. Do not add source,
    arguments, stdout, structured results, or a content fingerprint to this
    audit entry.
    Modification time is audit metadata, not proof of content identity and
    must not drive the internal-viewer change check.
- **Analysis:**
  - File origin does not change AI-requested execution authority, so the
    existing shown or hidden policy remains the controlling user choice.
  - Persistent approval identity and invalidation are unnecessary because
    each invocation is governed when it occurs rather than trusted for later
    executions.
  - The invocation remains separate from AI-owned code state so that the file
    stays the source of truth and the existing draft cannot be destroyed.
  - The internal viewer requires actual content comparison because file
    modification time does not reliably identify content.
  - Registered menu actions are not the invocation boundary because arbitrary
    supported script files have no action identity or execution mode.
  - File inspection remains user-only so that invocation does not become an
    unrestricted file-reading capability for AI.
