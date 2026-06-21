# Task: Add leading slash prompt references to AI chat

- **Task Identifier:** 2026-06-21-slash-prompts
- **Scope:**
  Add leading slash prompt references to the AI chat input. The first
  increment is implemented and in review. A new follow-up subtask covers
  popup keyboard and placement refinements so that behavior can be
  reviewed before implementation.
- **Motivation:**
  Prompt reuse from menus starts prompt execution. Users also need a
  lighter chat-draft path that keeps prompt identity visible while the
  model receives prompt text. A leading slash interaction is simpler
  than arbitrary slash parsing or hotkey-only prompt insertion.
- **Scenario:**
  A user starts a chat draft with `/`, sees saved prompts, edits the
  leading slash text, and accepts `Summarize branch`. The input contains
  `/Summarize branch ` and the reference is underlined while it resolves
  to that saved prompt. The user adds `for release notes` and sends the
  message. The visible chat shows underlined `/Summarize branch`
  followed by `for release notes`; the model receives the saved prompt
  text followed by ` for release notes`.

  If the user enables full instruction history, an existing prompt
  reference message keeps the compact visible `/prompt name` text and
  adds a prompt subsection with only the captured saved prompt text. The
  following user-visible suffix is not repeated in that subsection. If
  the next-request instruction preview is visible and the draft resolves
  to a prompt reference, the preview includes a prompt block containing
  only the saved prompt text that would be substituted.

  The popup UI follow-up keeps the same prompt-reference semantics while
  refining the suggestion popup: it appears above the input, `Up` and
  `Down` navigate suggestions, `Tab` and `Enter` accept, `Escape`
  closes, and other editing keys continue editing the input.
- **Glossary:**
  ```mermaid
  flowchart TD
      User[User] -->|types and edits| SlashPromptQuery[Slash prompt query]
      SlashPromptQuery -->|may resolve to| PromptReference[Prompt reference]
      PromptReference -->|renders as| VisibleUserText[Visible user text]
      PromptReference -->|substitutes into| ModelFacingText[Model-facing text]
      ModelFacingText -->|is captured in| PromptReferenceTranscript[Prompt-reference transcript]
      VisibleUserText -->|is captured in| PromptReferenceTranscript
      PromptReference -->|offers| PromptCompletionPopup[Prompt completion popup]
  ```

  - **Slash prompt query:** leading chat-input text that starts with
    `/` and is used to filter saved prompt names.
    - It exists only at the start of the chat input.
    - Editing the query changes completion candidates instead of
      deleting the whole slash text.
  - **Prompt reference:** a leading `/prompt name` text range that
    resolves to a saved prompt by the send-time matching rule.
    - It remains visible in the input and visible chat.
    - It is underlined when the current text resolves to a saved prompt.
  - **Prompt completion popup:** a non-modal saved-prompt chooser shown
    while editing a leading slash prompt query.
    - Its selected candidate can be navigated and accepted without
      taking normal editing keys away from the input area.
  - **Model-facing text:** the text sent to the model for a user
    message.
    - For a prompt reference, it is built by replacing the leading
      prompt reference with the saved prompt text captured at send time.
    - For ordinary user messages, it remains the visible user text.
  - **Prompt-reference transcript:** persisted transcript data that
    stores the visible user text, captured saved prompt text, and
    captured model-facing text for a prompt reference.
    - It lets restored chats, redo, full instruction-history rendering,
      and prompt preview use the original captured prompt text even if
      the saved prompt changes later.
- **Constraints:**
  - Prompt completion opens only for input whose first character is
    `/`. Do not add arbitrary in-text slash completion or a hotkey-only
    prompt insertion flow in this task.
  - Prompt completion uses saved prompts only. The current unsaved draft
    prompt in the prompt manager is not a completion candidate.
  - Completion and prompt-reference substitution ignore prompt execution
    metadata: `showInChat`, model selection, and tool availability.
  - Manual prompt execution from menus and script `runAiPrompt(...)`
    behavior is unchanged.
  - Keep `AIChatPanel.inputArea` as a `JTextArea`. Use highlighting or
    custom painting for input underlining; do not replace it with
    `JEditorPane` or `JTextPane`.
  - Prompt text is used as stored. Do not trim prompt text at
    substitution time.
  - A leading slash message with no saved-prompt match is ordinary chat
    text.
  - Persist structured prompt-reference data instead of relying on only
    a boolean such as `startsWithPrompt`.
- **Briefing:**
  Relevant production code is in `freeplane_plugin_ai`. `AIChatPanel`
  owns `inputArea`, send handling, visible request startup, undo/redo,
  instruction preview, and prompt execution. `AiPromptActionRegistry`
  owns saved prompts. `SlashPromptCompletionController` owns the popup,
  prompt-query edits, and input underline. `PromptReferenceResolver`
  owns completion filtering and send-time resolution.
  `AssistantProfileChatMemory`, `ModelProjector`, `PanelProjector`, and
  `TranscriptProjector` decide which messages are sent to the model,
  rendered in the panel, and persisted. `ChatMemoryHistoryRenderer`,
  `ChatMessageRenderer`, and `NextRequestInstructionPreviewView` render
  visible messages and preview blocks.

## Subtask: Implement leading slash prompt references

- **Status:** done
- **Scope:**
  Implement leading slash prompt completion, prompt-reference
  substitution, visible chat rendering, transcript persistence,
  undo/redo behavior, failed-request restore behavior, full
  instruction-history prompt subsections, next-request prompt preview,
  and removal of the older prompt backlog task files. Popup keyboard and
  placement refinements are excluded and tracked in a separate subtask.
- **Motivation:**
  Users need to reuse saved prompt text inside normal chat drafts while
  preserving visible prompt identity and avoiding prompt-execution
  semantics.
- **Scenario:**
  A user enters `/Summarize branch for release notes`. If `Summarize
  branch` is the longest saved prompt name matching the leading slash
  text and followed by whitespace, Freeplane sends the saved prompt text
  plus ` for release notes` to the model, but visible chat keeps
  `/Summarize branch for release notes` with the leading reference
  underlined. Restored transcripts and redo use the captured prompt text
  from the original send, not the current saved prompt definition.
- **Constraints:**
  - Inline prompt references do not call `AIChatPanel.runPrompt(...)`.
  - Inline prompt references do not alter selected model or tool
    availability overrides.
  - Prompt preview and full instruction-history rendering show only the
    saved prompt text, not the user-visible suffix.
- **Briefing:**
  The implemented increment changes `AIChatPanel`,
  `SlashPromptCompletionController`, `PromptReferenceResolver`,
  `PromptReferenceUserMessage`, `ChatRequestFlow`, transcript DTOs and
  mappers, chat history renderers, and prompt registry access.
- **Research:**
  ```plantuml
  @startuml
  set separator none
  package "org.freeplane.plugin.ai.prompt" {
    class AiPrompt {
      +String getName()
      +String getPrompt()
      +boolean isShowInChat()
    }
    class AiPromptActionRegistry {
      ~List<AiPrompt> getPrompts()
      +AiPrompt findSavedPromptByName(String promptName)
      +void runPrompt(AiPrompt prompt, Component owner)
    }
  }
  package "org.freeplane.plugin.ai.chat.ui" {
    class AIChatPanel {
      -JTextArea inputArea
      -void sendMessage()
      +void runPrompt(AiPrompt prompt, Component owner)
    }
    class ChatMemoryHistoryRenderer
    class NextRequestInstructionPreviewView
  }
  package "org.freeplane.plugin.ai.chat.memory" {
    class AssistantProfileChatMemory {
      +void add(ChatMessage message)
      +List<ChatMessage> messages()
      +String undo()
      +List<ChatTranscriptEntry> transcriptEntriesForPersistence()
    }
    class TranscriptProjector
  }
  package "org.freeplane.plugin.ai.chat.history" {
    class ChatTranscriptEntry {
      -ChatTranscriptRole role
      -String text
      -String baseSystemText
    }
  }
  AIChatPanel --> AiPromptActionRegistry : prompt execution only
  AIChatPanel --> AssistantProfileChatMemory : one user-message string
  AssistantProfileChatMemory --> TranscriptProjector : persistence entries
  ChatMemoryHistoryRenderer --> NextRequestInstructionPreviewView : separate render surfaces
  @enduml
  ```

  Original observations:
  - `inputArea` was a plain `JTextArea` with send, undo, redo, and
    cancel bindings and no prompt completion controller.
  - Current visible chat, model-facing chat, undo restore text, copy
    source text, and transcript `USER.text` all used one user-message
    string.
  - Transcript persistence stored only `role`, `text`, and
    `baseSystemText` for ordinary user messages.
  - Existing prompt menu execution used `AIChatPanel.runPrompt(...)` and
    applied prompt execution metadata.
- **Analysis:**
  - Prompt completion is limited to leading slash input because the
    desired interaction follows the simpler Pi-style command placement.
  - A prompt reference renders as `/prompt name` because prompt identity
    should remain visible in the input and visible chat.
  - Edited slash text is kept and re-filtered because Pi keeps edited
    commands rather than deleting them.
  - Completion filtering matches only at saved-prompt word starts
    because single-letter queries should not match arbitrary letters
    inside words.
  - An unaccepted exact leading `/prompt name` resolves on send because
    the visible syntax should be authoritative.
  - Send-time matching uses the longest saved prompt name followed by
    end-of-input or whitespace because prompt names can contain spaces
    and can share prefixes.
  - Prompt-reference substitution captures saved prompt text at send
    time because restored chats and redo must not change when the saved
    prompt is later edited.
  - The input remains a `JTextArea` because a custom highlighter draws a
    foreground-colored underline with less behavior risk than replacing
    the text component.
  - Structured transcript fields are needed because a boolean flag says
    that a prompt reference existed but does not preserve what the model
    saw.
  - Prompt preview and full instruction-history prompt subsections omit
    the visible suffix because the user already sees that suffix in the
    input or chat message.
- **Design:**
  ```plantuml
  @startuml
  set separator none
  package "org.freeplane.plugin.ai.prompt" {
    class AiPromptActionRegistry {
      +List<AiPrompt> prompts()
      +AiPrompt findSavedPromptByName(String promptName)
    }
  }
  package "org.freeplane.plugin.ai.chat.ui" {
    class AIChatPanel {
      -JTextArea inputArea
      -SlashPromptCompletionController slashPromptCompletionController
      +void setPromptActionRegistry(AiPromptActionRegistry promptActionRegistry)
      -void sendMessage()
      -List<PreviewInstructionBlock> buildInstructionPreviewBlocks()
    }
    class SlashPromptCompletionController {
      +void install()
      +void refresh()
      +void closePopup()
    }
    class PromptReferenceResolver {
      +List<AiPrompt> completionCandidates(String text, int caretPosition, List<AiPrompt> prompts)
      +PromptReferenceMatch resolveLeadingReference(String text, List<AiPrompt> prompts)
    }
    class PromptReferenceMatch {
      +String visibleText
      +String modelFacingText
      +String promptName
      +String promptText
      +int referenceStartOffset
      +int referenceEndOffset
    }
    class ChatMemoryHistoryRenderer
    class ChatMessageRenderer
    class NextRequestInstructionPreviewView
  }
  package "org.freeplane.plugin.ai.chat.memory" {
    class PromptReferenceUserMessage {
      +String getVisibleText()
      +String getPromptName()
      +String getPromptText()
      +String getModelFacingText()
      +int getReferenceEndOffset()
    }
    class AssistantProfileChatMemory {
      +void useNextPromptReference(PromptReferenceUserMessage message)
      +void add(ChatMessage message)
      +String undo()
    }
    class TranscriptProjector
  }
  package "org.freeplane.plugin.ai.chat.history" {
    class ChatTranscriptEntry {
      -ChatTranscriptRole role
      -String text
      -String baseSystemText
      -String promptName
      -String promptText
      -String modelFacingText
      -Integer promptReferenceEndOffset
    }
  }
  AIChatPanel --> SlashPromptCompletionController
  AIChatPanel --> PromptReferenceResolver
  SlashPromptCompletionController --> AiPromptActionRegistry : prompts()
  PromptReferenceResolver --> AiPrompt
  AIChatPanel --> AssistantProfileChatMemory : useNextPromptReference(message)
  AssistantProfileChatMemory --> PromptReferenceUserMessage
  ChatMemoryHistoryRenderer --> ChatMessageRenderer : compact/full rendering
  AIChatPanel --> NextRequestInstructionPreviewView : prompt preview block
  TranscriptProjector --> ChatTranscriptEntry : visible + prompt + model text
  @enduml
  ```

  Implemented target:
  - `AiPromptActionRegistry.prompts()` returns defensive copies of saved
    prompts in saved prompt order and excludes the current draft prompt.
  - `AIChatPanel.setPromptActionRegistry(...)` receives the registry
    after prompt registry creation.
  - `SlashPromptCompletionController` attaches to `inputArea`, listens
    for document and caret changes, and owns prompt completion and input
    underline updates.
  - Completion opens only when the raw input starts with `/` and the
    caret is in the leading slash query. Empty query shows saved prompts
    in saved order. Non-empty query filters prompt names
    case-insensitively from a saved-prompt word start. A word start is
    the start of the prompt name or a character after whitespace.
  - Accepting a candidate replaces the leading slash query from offset
    `0` through the caret with `/<saved prompt name>` and applies the
    trailing-space rule.
  - `PromptReferenceResolver.resolveLeadingReference(...)` matches
    case-insensitively against the longest saved prompt name that starts
    immediately after `/` and is followed by end-of-input or whitespace.
  - `ChatRequestFlow` separates visible restore text from model-facing
    request text.
  - `PromptReferenceUserMessage` stores visible text, prompt name,
    captured prompt text, captured model-facing text, and reference end
    offset.
  - Model projection uses captured model-facing text. Visible rendering
    uses visible text and underlines the reference. Full instruction
    history and next-request preview show only captured prompt text.
  - Transcript persistence stores visible text, prompt name, prompt
    text, model-facing text, and reference end offset. Restore does not
    look up current saved prompts.
- **Test specification:**
  - **Automated tests:**
    - Prompt registry exposes saved prompts in order as defensive copies.
    - Leading slash completion opens only at input start, filters edited
      query text from prompt-name word starts, updates after edits, and
      closes on no match.
    - Completion acceptance replaces only the leading slash query,
      preserves suffix text, and applies the trailing-space rule.
    - Prompt-reference resolution uses longest saved-prompt match with
      boundary rules and treats unmatched slash text as ordinary chat.
    - Model-facing text substitutes captured saved prompt text and
      preserves suffix text for the model request.
    - Input underlining updates for prompt-relevant text edits only.
    - Sending a prompt reference starts the visible request with
      model-facing text while visible chat stores `/prompt name` text.
    - Prompt reference insertion and sending do not call prompt
      execution or change model/tool overrides.
    - Failed visible requests restore visible text, not model-facing
      text.
    - Chat undo restores visible text; redo restores the stored
      prompt-reference message without a fresh lookup.
    - Transcript persistence round-trips visible text, prompt name,
      prompt text, reference end offset, and captured model-facing text.
    - Visible chat copy and markdown-copy use visible text, not captured
      model-facing text.
    - Full instruction-history rendering shows a prompt subsection with
      captured saved prompt text and omits that subsection in brief mode.
    - Next-request preview shows a prompt block with only the saved
      prompt text and omits the user-visible suffix.
  - **Manual tests:**
    - Verify the input underline and rendered-chat underline use the
      normal text color and align under wrapped long prompt names.
    - Verify typing `/`, editing the query, accepting with keyboard or
      mouse, escaping the popup, and sending with the existing shortcut.

## Subtask: Refine prompt completion popup controls

- **Status:** done
- **Scope:**
  Refine the prompt completion popup UI only. Show the popup above the
  input field so it does not overlap the draft. While it is visible,
  `Up` and `Down` navigate candidates, `Tab` and `Enter` accept the
  selected candidate, `Escape` closes the popup, and all other editing
  keys remain handled by `inputArea`. Do not change prompt matching,
  send-time substitution, transcript persistence, preview behavior, or
  prompt execution.
- **Motivation:**
  The suggestion popup should behave like a lightweight completion menu
  without stealing ordinary text-editing behavior from the chat input.
- **Scenario:**
  A user types `/sum` and the prompt completion popup appears above the
  input field. Pressing `Down` and `Up` changes the selected prompt.
  Pressing `Tab` or `Enter` inserts the selected prompt reference.
  Pressing `Escape` closes the popup without changing the text. Pressing
  `Left`, `Right`, character keys, `Delete`, or `Backspace` edits the
  input normally and the popup updates from the changed slash query.
- **Constraints:**
  - Keep the popup non-modal and keep editing focus on `inputArea`.
  - Keep `JPopupMenu` as the popup implementation unless implementation
    proves it cannot be made non-focus-stealing or positioned reliably;
    do not switch to a non-modal dialog without review.
  - Do not add popup-specific `InputMap` or `ActionMap` entries.
  - Preserve existing send shortcut behavior when the popup is not
    visible.
  - Preserve existing cancellation behavior when the popup is not
    visible.
  - Preserve normal text-area behavior for editing keys not explicitly
    owned by the popup.
  - Do not change prompt-reference resolution or model-facing text.
- **Briefing:**
  `SlashPromptCompletionController` owns popup construction, key-event
  interception, candidate selection, and popup positioning. It currently
  attaches to `AIChatPanel.inputArea` and uses the prompt list supplied
  from `AiPromptActionRegistry.prompts()`. The follow-up should be
  confined to this controller and its tests unless implementation shows
  a directly necessary localized adjustment.
- **Research:**
  ```plantuml
  @startuml
  actor User
  participant InputArea as JTextArea
  participant SlashPromptCompletionController
  participant Popup as JPopupMenu
  participant CandidateList as JList

  User -> InputArea : type leading slash query
  InputArea -> SlashPromptCompletionController : document/caret change
  SlashPromptCompletionController -> Popup : show at caret below input
  SlashPromptCompletionController -> CandidateList : selected index
  User -> InputArea : Enter/Escape/Up/Down
  InputArea -> SlashPromptCompletionController : current mapped action
  User -> InputArea : Tab or editing keys
  InputArea -> InputArea : default or current text handling
  @enduml
  ```

  Current implementation facts:
  - The popup is positioned from the caret rectangle and currently uses
    a below-caret placement that can overlap the draft area.
  - `Enter`, `Escape`, `Up`, and `Down` are currently routed through
    input actions while the popup is visible.
  - `Tab` is not yet specified as an acceptance key.
  - Popup-specific input/action-map rewiring is the wrong mechanism for
    synchronizing the input area and popup; it should be replaced by an
    intercepting key listener on `inputArea`.
  - The popup/list must not take focus in a way that diverts normal
    editing keys away from `inputArea`.
- **Analysis:**
  - Popup controls are a separate subtask because they should be
    reviewed before implementation and do not change prompt-reference
    semantics.
  - The popup belongs above the input because overlap with the current
    draft obscures the text the user is editing.
  - Only `Up`, `Down`, `Tab`, `Enter`, and `Escape` get popup-specific
    handling because other keys are normal text-editing operations.
  - An intercepting `KeyListener` is the chosen synchronization point
    because it observes the focused input component while leaving the
    existing input and ancestor action maps authoritative when the popup
    is hidden.
  - `JPopupMenu` remains the selected popup type. A non-modal dialog is
    not part of this design unless `JPopupMenu` proves inadequate during
    implementation and the alternative is reviewed separately.
- **Design:**
  ```plantuml
  @startuml
  actor User
  participant InputArea as JTextArea
  participant SlashPromptCompletionController
  participant Popup as JPopupMenu
  participant CandidateList as JList

  User -> InputArea : type leading slash query
  InputArea -> SlashPromptCompletionController : document/caret change
  SlashPromptCompletionController -> Popup : show above input field
  User -> InputArea : Up or Down key event
  InputArea -> SlashPromptCompletionController : key listener intercepts and moves selection
  User -> InputArea : Tab or Enter key event
  InputArea -> SlashPromptCompletionController : key listener intercepts and accepts selected candidate
  User -> InputArea : Escape key event
  InputArea -> SlashPromptCompletionController : key listener intercepts and closes popup
  User -> InputArea : Left/Right/char/Delete/Backspace
  InputArea -> InputArea : normal editing
  InputArea -> SlashPromptCompletionController : document/caret change
  @enduml
  ```

  Target behavior:
  - Popup placement uses coordinates relative to `inputArea` so the
    popup's bottom edge is at or above the top edge of the input field.
    Horizontal placement may align with the caret but must be clamped so
    the popup remains usable within the input width when possible.
  - The popup is a `JPopupMenu`; the popup and candidate list remain
    non-focus-stealing, and `inputArea` stays the owner of key events.
  - `SlashPromptCompletionController` installs one `KeyListener` on
    `inputArea` for popup interception. It does not install
    popup-specific `InputMap` or `ActionMap` entries.
  - The key listener returns immediately when the popup is not visible,
    leaving existing input-area and ancestor actions unchanged.
  - While the popup is visible, unmodified `Up` and `Down` key presses
    move the candidate selection and are consumed.
  - While the popup is visible, unmodified `Tab` and `Enter` key presses
    accept the selected candidate and are consumed.
  - While the popup is visible, unmodified `Escape` closes the popup and
    is consumed.
  - Modified keys, including the existing send shortcut, are not treated
    as popup-control keys unless separately specified.
  - `Tab` and `Enter` acceptance must not leave a following typed
    tab/newline in the input; consume the corresponding typed event if
    Swing emits one after the accepted key press.
  - Editing keys not listed above are not intercepted for popup
    behavior; they continue to edit the `JTextArea`, and existing
    document/caret listeners update candidates afterward.
- **Test specification:**
  - **Automated tests:**
    - Popup placement is computed above the input field and does not
      use below-caret placement that overlaps the draft.
    - `Up` and `Down` change the selected candidate while the popup is
      visible.
    - `Tab` and `Enter` accept the selected candidate while the popup is
      visible.
    - `Escape` closes the popup without changing input text while the
      popup is visible.
    - `Enter`, `Tab`, `Escape`, `Up`, and `Down` preserve existing
      input-area or ancestor behavior when the popup is not visible.
    - Modified key strokes such as the existing send shortcut are not
      intercepted by popup handling.
    - `Tab` and `Enter` acceptance does not insert an extra tab or
      newline after accepting the selected candidate.
    - Character insertion, `Left`, `Right`, `Delete`, and `Backspace`
      remain ordinary input-area editing operations while the popup is
      visible and cause completion refresh through existing listeners.
    - The controller does not add popup-specific `InputMap` or
      `ActionMap` entries.
  - **Manual tests:**
    - In the real chat panel, verify the popup appears above the input
      and does not cover the typed draft.
    - With the popup open, verify `Up`, `Down`, `Tab`, `Enter`,
      `Escape`, and normal editing keys behave as specified.
