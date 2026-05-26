# Task: Add Control-Space prompt completion to AI chat input

- **Task Identifier:** 2026-05-26-chat-prompt-completion
- **Scope:**
  Add prompt completion to `AIChatPanel.inputArea` so `Ctrl+Space`
  offers prompt names and inserts prompt text into the chat input
  instead of executing the prompt. As part of the same increment, trim
  prompt text when a prompt is saved so completion can use stored
  prompt text unchanged.
- **Motivation:**
  Users can currently run prompts immediately, but they cannot reuse
  prompt text inside an existing chat without copying and pasting it
  manually.
- **Scenario:**
  In the AI chat input, a user types `countse`, presses `Ctrl+Space`,
  selects the prompt `Count selected nodes`, and Freeplane replaces
  only the typed prefix with the prompt text as stored. Text after the
  caret stays unchanged, and Freeplane ensures one trailing space
  unless the insertion ends at the end of the input or is already
  followed by whitespace.

  Another user types `csn`, presses `Ctrl+Space`, and finds the same
  prompt by acronym. Typing `count se` does not match because spaces
  and separators break the typed prefix.

  If the user selects text first and then presses `Ctrl+Space`, the
  chooser shows all prompts and accepting one replaces the selection.
- **Constraints:**
  - Completion uses prompts only. Ignore the current draft prompt in
    the prompt manager dialog.
  - Trim prompt text when saving a prompt. The current draft prompt may
    stay untrimmed until the user saves it.
  - Completion is opened only by `Ctrl+Space` in this task. Do not add
    the previously discussed input-area popup menu.
  - Use a lightweight non-modal chooser popup anchored at the caret.
    Do not reuse JSyntaxPane's modal completion dialog.
  - With an explicit selection, `Ctrl+Space` shows all prompts and
    accepting a candidate replaces the selection.
  - With no explicit selection, derive the filter from the contiguous
    letter-or-digit prefix immediately before the caret. If that prefix
    is empty, show all prompts. If it is non-empty and no prompt name
    matches it, do not open the chooser and do not change the input.
  - Match prompt names case-insensitively against exactly two prompt-
    name keys:
    - full-name key = prompt name lowercased with every non-letter/
      digit removed;
    - acronym key = prompt name lowercased to the first letter or digit
      of each segment split by non-letter/digit characters.
  - Spaces and separators break the typed prefix instead of being
    ignored, so `countse` and `csn` may match `Count selected nodes`,
    while `count se` must not match.
  - Assume prompt text is already trimmed when the prompt was saved.
    Completion must not trim prompt text at apply time.
  - Accepting a completion must not consume characters after the caret
    except an explicit selection, and must ensure exactly one trailing
    plain space unless the insertion already ends at end-of-input or is
    followed by whitespace. It does not insert a leading space
    automatically.
  - Completion inserts prompt text only. Prompt request mode, model
    selection, and tool selection are irrelevant to completion.
- **Briefing:**
  Relevant classes are
  `freeplane_plugin_ai/src/main/java/org/freeplane/plugin/ai/chat/AIChatPanel.java`,
  `.../prompt/AiPromptActionRegistry.java`,
  `.../prompt/ui/AiPromptManagerDialog.java`, and
  `.../prompt/AiPromptStore.java`. `AIChatPanel.inputArea` is a plain
  `JTextArea` with send/undo/redo bindings only. JSyntaxPane
  completion exists elsewhere in the application, but it is built for a
  `SyntaxDocument` and a modal dialog.
- **Research:**
  - `AIChatPanel.inputArea` currently has no prompt completion UI and no
    access to prompts.
  - `AiPromptActionRegistry` already owns the separation between
    prompts and the current draft prompt, but it does not yet expose
    the prompt list needed by completion.
  - Prompt text is not yet guaranteed to be trimmed at save time, so
    completion cannot safely assume stored prompt text is normalized
    unless this task adds that save-time trimming.
  - JSyntaxPane's `Ctrl+Space` completion is a useful interaction
    reference, but it depends on `SyntaxDocument` token selection and a
    modal `ComboCompletionDialog`, so it is not a drop-in fit for
    `AIChatPanel.inputArea`.

  ```plantuml
  @startuml
  set separator none
  package "freeplane_plugin_ai.prompt" {
    class AiPromptActionRegistry
    class AiPrompt
  }
  package "freeplane_plugin_ai.chat" {
    class AIChatPanel {
      -JTextArea inputArea
    }
  }

  AIChatPanel ..> AiPromptActionRegistry : no prompt list access yet
  AIChatPanel --> AIChatPanel : send/undo/redo only
  @enduml
  ```
- **Design:**
  - Add `public List<AiPrompt> prompts()` to `AiPromptActionRegistry`.
    It returns defensive copies of prompts in prompt order and excludes
    the current draft prompt.
  - `AiPromptManagerDialog.EditorState.save(...)` and
    `saveAsNew(...)` trim prompt text before persistence. Completion and
    other prompt-text consumers then use stored prompt text unchanged.
  - Add package-private helper `PromptCompletionSupport` under
    `org.freeplane.plugin.ai.chat` for pure completion logic:
    prefix extraction, full-name/acronym key normalization, candidate
    filtering in prompt order, and replacement/trailing-space
    calculation.
  - `AIChatPanel` gets exact setter
    `setPromptActionRegistry(AiPromptActionRegistry promptActionRegistry)`.
    `Activator` installs the registry after prompt-registry creation.
    `AIChatPanel` asks that registry for prompts when `Ctrl+Space` is
    pressed.
  - `AIChatPanel` owns the popup UI and `Ctrl+Space` binding. Keep the
    popup implementation inside `AIChatPanel` rather than introducing a
    second top-level UI type.
  - The popup shows prompt names in prompt order, filtered by case-
    insensitive prefix on the full-name key or acronym key.
  - If there is an explicit selection, ignore the selected text for
    matching and show all prompts.
  - If there is no explicit selection, use only the contiguous
    letter-or-digit prefix immediately before the caret as the filter.
    Characters after the caret never contribute to matching and are not
    consumed by completion.
  - Accepting a candidate replaces the explicit selection when one
    exists, otherwise only the typed prefix immediately before the
    caret. The inserted text is `prompt.getPrompt()` as stored, plus
    one trailing plain space unless the insertion is at end-of-input or
    the next character is already whitespace.
  - `Escape` closes the chooser without changes. `Enter` or double-
    click accepts the selected candidate.

  ```plantuml
  @startuml
  actor User
  participant AIChatPanel
  participant AiPromptActionRegistry
  participant PromptCompletionSupport
  participant InputArea as JTextArea

  User -> AIChatPanel : Ctrl+Space
  AIChatPanel -> AiPromptActionRegistry : prompts()
  AIChatPanel -> PromptCompletionSupport : derive prefix + filter candidates
  alt candidates found
    AIChatPanel -> User : show lightweight popup at caret
    User -> AIChatPanel : Enter or double-click candidate
    AIChatPanel -> PromptCompletionSupport : build replacement text
    AIChatPanel -> JTextArea : replace selection/prefix
  else no candidates for non-empty prefix
    AIChatPanel --> User : no popup
  end
  @enduml
  ```
- **Test specification:**
  - **Automated tests:**
    - update `AiPromptActionRegistryTest` to cover `prompts()`
      defensive copies, prompt-order preservation, and exclusion of the
      current draft prompt;
    - update `AiPromptManagerDialogTest` and `AiPromptStoreTest` to
      cover prompt-text trimming on save;
    - add `PromptCompletionSupportTest` covering full-name key,
      acronym key, prefix extraction, `countse` and `csn` matches,
      `count se` non-match, and trailing-space insertion rules;
    - add `AIChatPanel` completion tests covering empty-prefix show-all
      behavior, unmatched-prefix no-popup behavior, explicit-selection
      replacement, prefix-only replacement, use of stored prompt text
      without apply-time trimming, and keyboard acceptance/cancel
      behavior;
    - run `gradle -Djava.net.preferIPv6Addresses=true
      -Djava.awt.headless=true :freeplane_plugin_ai:test`.
  - **Manual tests:**
    - type an empty chat input, press `Ctrl+Space`, and verify all
      prompts appear;
    - type `countse`, press `Ctrl+Space`, accept `Count selected
      nodes`, and verify only the typed prefix is replaced while text
      after the caret stays intact;
    - type `csn`, press `Ctrl+Space`, and verify the same prompt is
      offered;
    - type `count se`, press `Ctrl+Space`, and verify no chooser opens;
    - select text in the input area, press `Ctrl+Space`, accept a
      prompt, and verify the selection is replaced with the stored
      prompt text plus the trailing-space rule.
