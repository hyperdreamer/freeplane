# Task: Replace prompt `showInChat` with three request modes

- **Task Identifier:** 2026-05-26-prompt-request-modes
- **Scope:**
  Replace prompt persistence and prompt-manager editing of
  `showInChat` with a persisted `AiRequestMode requestMode`, update
  manual prompt execution to use that mode, and make named-prompt
  script execution derive its default mode from the prompt.
- **Motivation:**
  The prompt dialog can currently express only shown-vs-hidden prompt
  execution, so it cannot save `ADD_TO_CHAT`. Prompts therefore cannot
  match the full set of prompt-style request modes already used by
  scripts.
- **Scenario:**
  A user saves prompt `Rewrite` with mode `Add to chat`. Running that
  prompt from the prompt menu appends the prompt text to the current
  visible chat when one exists, or starts a new visible chat and uses
  the prompt name there when none exists.

  Another prompt uses mode `Without chat`. Running it still uses the
  hidden prompt path with the existing cancel dialog.

  A script calls `runAiPrompt("Rewrite", ...)` without an explicit mode
  override. Freeplane derives `ADD_TO_CHAT` from the prompt itself
  instead of collapsing prompt defaults back to the old shown-vs-hidden
  boolean.
- **Constraints:**
  - Prompts may persist only these request modes:
    `SHOW_IN_CHAT`, `ADD_TO_CHAT`, and
    `HIDDEN_WITH_CANCEL_DIALOG`.
  - `HIDDEN` remains script-only. Do not offer or persist it in prompt
    management.
  - Legacy `ai-prompts.json` compatibility is required only for the old
    boolean field: `showInChat=true` maps to `SHOW_IN_CHAT`, and
    `showInChat=false` maps to `HIDDEN_WITH_CANCEL_DIALOG`.
  - User-facing prompt-manager labels must be `New chat`, `Add to
    chat`, and `Without chat`, even though `Without chat` persists as
    `HIDDEN_WITH_CANCEL_DIALOG`.
  - Prompt execution paths use prompt text as stored. Do not add
    apply-time trimming in each consumer.
  - Prompt text is already expected to be trimmed on save by
    `2026-05-26-chat-prompt-completion`. Do not reintroduce consumer-
    side trimming here.
  - Keep visible-chat naming rules aligned with script
    `ADD_TO_CHAT`: name a newly created visible chat from the prompt,
    but do not rename an already active visible chat.
  - Update translations using repository i18n rules.
- **Briefing:**
  Relevant classes are
  `freeplane_plugin_ai/src/main/java/org/freeplane/plugin/ai/prompt/AiPrompt.java`,
  `.../AiPromptStore.java`,
  `.../prompt/ui/AiPromptManagerDialog.java`,
  `.../chat/AIChatPanel.java`, and
  `.../chat/ScriptAiRequestService.java`.
  Translation keys live in
  `freeplane/src/editor/resources/translations/Resources_*.properties`
  and `freeplane/src/viewer/resources/translations/Resources_en.properties`.
- **Research:**
  - `AiPrompt` currently persists `showInChat`, and
    `AIChatPanel.runPrompt(AiPrompt, Component)` currently switches only
    between shown prompt chat and hidden prompt execution with the
    existing cancel dialog.
  - `ScriptAiRequestService.runAiPrompt(...)` currently derives prompt
    default mode from `showInChat`, so that boolean-based rule is the
    exact place this task must replace.
  - `AiPromptManagerDialog` edits `showInChat` through a checkbox and
    carries that boolean through `EditorState` dirty, save, and restore
    behavior.

  ```plantuml
  @startuml
  set separator none
  package "freeplane_plugin_ai.prompt" {
    class AiPrompt {
      +boolean showInChat
    }
    class AiPromptManagerDialog
    class AiPromptStore
  }
  package "freeplane_plugin_ai.chat" {
    class AIChatPanel
    class ScriptAiRequestService
  }

  AiPromptManagerDialog --> AiPrompt : edit/save showInChat
  AiPromptStore --> AiPrompt : persist showInChat
  AIChatPanel --> AiPrompt : isShowInChat()
  ScriptAiRequestService --> AiPrompt : derive default mode
  @enduml
  ```
- **Design:**
  - Replace `AiPrompt.showInChat` with persisted field
    `AiRequestMode requestMode` and accessor `getRequestMode()`.
    Persist prompt JSON under field name `requestMode`.
  - Persisted `requestMode` accepts only `SHOW_IN_CHAT`,
    `ADD_TO_CHAT`, and `HIDDEN_WITH_CANCEL_DIALOG`.
  - Legacy JSON still accepts `showInChat` and maps it to
    `SHOW_IN_CHAT` or `HIDDEN_WITH_CANCEL_DIALOG`. Missing or malformed
    non-legacy `requestMode` normalizes to
    `HIDDEN_WITH_CANCEL_DIALOG`.
  - `AiPromptManagerDialog` replaces the checkbox with a local mode
    selector backed by `AiRequestMode` and these exact translation
    keys:
    - `ai_prompt_mode_label`
    - `ai_prompt_mode_new_chat`
    - `ai_prompt_mode_add_to_chat`
    - `ai_prompt_mode_without_chat`
    Remove obsolete key `ai_prompt_show_in_chat`.
  - `AiPromptManagerDialog.EditorState` stores `AiRequestMode
    requestMode` instead of a boolean. Prompt text remains used exactly
    as stored.
  - `AIChatPanel.runPrompt(AiPrompt, Component)` switches on
    `requestMode`:
    - `SHOW_IN_CHAT` keeps the existing shown-prompt path;
    - `HIDDEN_WITH_CANCEL_DIALOG` keeps the existing hidden prompt path
      with the cancel dialog; and
    - `ADD_TO_CHAT` reuses the current visible-chat append behavior:
      append to the current visible chat when one exists, otherwise
      create a new visible chat and name it from the prompt.
  - Keep prompt `ADD_TO_CHAT` timeout-free. Reuse existing add-to-chat
    targeting logic inside `AIChatPanel` instead of routing prompt-menu
    actions through script request handles.
  - `ScriptAiRequestService.runAiPrompt(...)` changes its default-mode
    derivation from `showInChat` mapping to direct use of
    `requestMode`.

  ```plantuml
  @startuml
  set separator none
  package "freeplane_api" {
    enum AiRequestMode
  }
  package "freeplane_plugin_ai.prompt" {
    class AiPrompt {
      +AiRequestMode requestMode
    }
    class AiPromptManagerDialog
    class AiPromptStore
  }
  package "freeplane_plugin_ai.chat" {
    class AIChatPanel
    class ScriptAiRequestService
  }

  AiPromptManagerDialog --> AiPrompt : edit/save requestMode
  AiPromptStore --> AiPrompt : persist requestMode
  AIChatPanel --> AiPrompt : switch on requestMode
  ScriptAiRequestService --> AiPrompt : derive default mode
  @enduml
  ```
- **Test specification:**
  - **Automated tests:**
    - update `AiPromptManagerDialogTest` to cover mode dirty-state and
      mode save/save-as-new persistence;
    - update `AiPromptStoreTest` to cover JSON round-trip with
      `requestMode` and legacy JSON load with `showInChat=true/false`
      mapping to `SHOW_IN_CHAT` /
      `HIDDEN_WITH_CANCEL_DIALOG`;
    - update `ScriptAiRequestServiceTest` so named-prompt default mode
      comes from `requestMode`, including `ADD_TO_CHAT`;
    - update AI prompt execution tests so `ADD_TO_CHAT` appends to the
      current visible chat or names a new chat from the prompt, while
      `HIDDEN_WITH_CANCEL_DIALOG` keeps the cancel dialog path;
    - run `gradle -Djava.net.preferIPv6Addresses=true
      -Djava.awt.headless=true :freeplane_plugin_ai:test`;
    - run `gradle format_translation` after translation edits.
  - **Manual tests:**
    - save prompts with each of the three dialog modes and verify
      `New chat`, `Add to chat`, and `Without chat` each run with the
      intended behavior;
    - run `runAiPrompt(...)` from a script against a prompt in each
      mode and verify the default mode comes from the prompt;
    - load an old `ai-prompts.json` containing `showInChat=true` and
      `showInChat=false` and verify those prompts reopen as `New chat`
      and `Without chat` respectively.
