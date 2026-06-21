# Task: Capture AI chat instruction state and support request-scoped system/profile messages

- **Task Identifier:** 2026-06-13-message-override
- **Scope:**
  Capture effective system and profile instruction state in visible
  chats, render that state as dedicated instruction blocks, and extend
  the script AI request API so `askAi(...)` and
  `runAiPrompt(..., AiRequestOptions, ...)` can supply request-scoped
  system and profile messages. Rename visible request mode
  `SHOW_IN_CHAT` to `SHOW_IN_NEW_CHAT`. Normal request-scoped system
  text is base instruction text only; dynamic Freeplane guidance
  remains composed per request. Add an exact request system-message
  option that suppresses Freeplane-composed system additions for that
  request state while leaving tool authorization to
  `AiToolAvailability`. Add an independent next-request instruction
  preview for visible chats.
- **Motivation:**
  Current visible chats do not persist the effective system message and
  do not snapshot effective profile message text. Restored chats can
  therefore use later global system-message or profile-configuration
  values rather than the values that governed the original
  conversation. Script requests also need a local way to set system and
  profile instruction context without mutating global settings.
- **Scenario:**
  A script calls `c.askAi(...)` with `AiRequestMode.SHOW_IN_NEW_CHAT`,
  a request system message, and an explicit profile message. Freeplane
  trims both messages, starts a new visible chat, stores the system
  message as the chat-start instruction block, stores the profile
  message as a profile event before the script prompt, and sends the
  request using that instruction state. Restoring the chat later uses
  those stored snapshots, not the current global system-message
  property or current profile definitions.

  A script calls `c.askAi(...)` with `AiRequestMode.ADD_TO_CHAT` and a
  request system message equal to the selected chat's captured system
  message after trimming. Freeplane appends to the selected chat and
  inserts a requested profile event only if it differs from the active
  profile event. If the trimmed request system message differs,
  Freeplane starts a new visible chat with that system message and runs
  the request there.

  A script calls `c.askAi(...)` with `AiRequestMode.HIDDEN` or
  `AiRequestMode.HIDDEN_WITH_CANCEL_DIALOG` and a request system
  message. Freeplane trims that text, uses it as base system text,
  composes applicable dynamic Freeplane guidance for the hidden
  request, omits Markdown response guidance, and keeps tool
  authorization governed by `AiToolAvailability`.

  A script calls `c.askAi(...)` with
  `AiRequestOptions.exactSystemMessage(...)`. Freeplane trims the text,
  uses it as exact provider system text for the request state, and
  suppresses Freeplane-composed system additions such as
  tool-availability, profile-control, Markdown, map-selection, and
  code-host guidance. Tool exposure is still governed by
  `AiToolAvailability`, and provider-side tool metadata remains outside
  Freeplane-composed system text.

  A user enables next-request instruction preview. Freeplane renders
  the chat using the current historical instruction rendering setting
  and shows a clearly labelled, non-persistent preview component for
  the next visible request. The preview appears after the profile
  selector and before the message input, uses current tool availability
  and pending profile injection state, includes Freeplane-composed
  dynamic system guidance unless the chat/request state is exact,
  excludes LangChain4j-generated tool descriptions, and does not change
  the transcript.
- **Glossary:**
  ```mermaid
  flowchart LR
    A["global ai_system_message"] -- default for new chat --> B["captured chat base system message"]
    C["AiRequestOptions.systemMessage"] -- normal request base override --> B
    R["AiRequestOptions.exactSystemMessage"] -- exact request override --> B
    R -- sets exact flag --> S["exact system-message flag"]
    B -- immutable base text --> D["effective provider system text"]
    E["Freeplane provider guidance"] -- dynamic guidance unless exact --> D
    S -- suppresses Freeplane additions --> D
    L["visible vs hidden request"] -- controls Markdown guidance --> E
    J["AiToolAvailability"] -- controls tool guidance --> E
    F["AiRequestOptions.profile(name)"] -- resolves configured profile --> G["profile message event"]
    H["AiRequestOptions.profile(name, message)"] -- explicit local event --> G
    G -- enables profile-control guidance --> E
    G -- applies from event onward --> I["following turns"]
    J -- authorizes --> K["tool exposure"]
    C -- does not authorize --> K
    R -- does not authorize --> K
    G -- does not authorize --> K
    D -- sent to provider --> M["provider system text"]
    D -- rendered when committed --> N["full instruction history"]
    O["pending profile event"] -- preview-only input --> P["next request instruction preview"]
    B --> P
    E --> P
    S --> P
    P -- not persisted --> Q["chat rendering surface"]
  ```

  - `captured chat system message`
    - Trimmed user/request base system instruction stored with a
      visible chat at chat creation. It is immutable for that chat,
      persisted, and restored with its exact/non-exact state. It is
      combined with dynamic Freeplane guidance before provider
      submission or full instruction rendering unless exact
      system-message state requires exact text.
  - `profile message event`
    - Trimmed profile instruction snapshot inserted at a specific point
      in the conversation. It affects subsequent calls, not earlier
      turns. It is rendered as a profile block and persisted with its
      effective text.
  - `request system message`
    - Optional trimmed base system text supplied through
      `AiRequestOptions.systemMessage(String)`. It never suppresses
      dynamic Freeplane guidance. In visible modes, it determines a new
      chat's captured base system message and determines whether
      `ADD_TO_CHAT` may append to the selected chat.
  - `exact request system message`
    - Optional trimmed exact system text supplied through
      `AiRequestOptions.exactSystemMessage(String)`. It suppresses
      Freeplane-composed system additions for the request state but does
      not change `AiToolAvailability`, tool exposure, or provider-side
      generated tool metadata.
  - `request profile message`
    - Optional profile instruction supplied through
      `AiRequestOptions.profile(String name)` or
      `AiRequestOptions.profile(String name, String message)`.
      `profile(name)` resolves and snapshots a configured profile.
      `profile(name, message)` creates a local request/chat event and
      must not create, update, overwrite, select, or persist a
      configured/global profile.
  - `Freeplane provider guidance`
    - Generated system guidance needed for Freeplane tool, selection,
      code-host, profile-control, Markdown, or protocol behavior. It is
      distinct from captured base system text, remains subordinate to
      `AiToolAvailability`, and is omitted only when exact system-message
      state requires exact text. Profile-control guidance is included
      only when profile messages are present or about to be injected.
      Markdown response guidance is included only for visible chat
      requests.
  - `next request instruction preview`
    - Non-persistent rendering of the effective instruction blocks that
      would be sent by the next visible request. It is shown in a
      separate preview component after the profile selector and before
      the message input, uses rendering close to committed instruction
      messages, and is clearly labelled as preview. Because user-message
      submission enforces pending profile changes, preview applies any
      pending profile event before resolving the previewed system text.
      It excludes LangChain4j-generated tool descriptions.
- **Constraints:**
  - Keep `AiToolAvailability` / `ToolAvailabilityLevel` as the real
    authorization boundary. System/profile text must not grant or
    suppress tools beyond existing authorization rules.
  - Always trim system-message, profile-name, and profile-message text
    before storage, comparison, rendering, or provider submission.
  - Treat an explicit empty system message as an empty system message,
    not as "use the global default".
  - Captured and normal request system messages are base user text only.
    They must not override or bypass dynamic Freeplane guidance.
  - `AiRequestOptions.exactSystemMessage(String)` is the only request API
    that may suppress Freeplane-composed system additions. It must not
    change tool authorization, tool exposure, or provider-side generated
    tool metadata.
  - Do not mutate a visible chat's captured base system message after
    chat creation.
  - `SHOW_IN_NEW_CHAT` always creates a new visible chat.
  - `ADD_TO_CHAT` is the only script mode that may append to an
    existing visible chat.
  - If `ADD_TO_CHAT` supplies a system message and either the trimmed
    value or exactness differs from the selected chat's captured system
    state, create a new visible chat instead of appending.
  - If `ADD_TO_CHAT` supplies no system message, append using the
    selected chat's captured system message when a selected chat is
    available. Do not compare with the current global
    `ai_system_message` in this case.
  - Do not add a separate "remove no-tools hint" option in this task.
  - Profile-control guidance must not be added to requests whose chat
    memory has no profile message and whose request will not inject a
    profile message.
  - Markdown response guidance must not be added to hidden requests.
  - Full instruction rendering must include Freeplane-composed dynamic
    system guidance for non-exact system messages and exact system text
    for exact system messages, but not automatically
    generated LangChain4j tool descriptions or schemas.
  - Next-request instruction preview must be rendered in a separate
    component after the profile selector and before the message input.
    It must be non-persistent and must not affect transcript
    save/restore, undo/redo, token accounting, committed chat-history
    selection/copy, or provider request history.
  - Keep the public scripting surface on `AiRequestOptions`; do not add
    positional `askAi(...)` overloads for system/profile messages.
- **Briefing:**
  Relevant code areas:
  - `freeplane_api/src/main/java/org/freeplane/api/ai/AiRequestMode.java`
  - `freeplane_api/src/main/java/org/freeplane/api/ai/AiRequestOptions.java`
  - `freeplane_plugin_ai/.../chat/request/ScriptAiRequestService.java`
  - `freeplane_plugin_ai/.../chat/request/ResolvedAiRequest.java`
  - `freeplane_plugin_ai/.../chat/request/AiRequestExecutionCoordinator.java`
  - `freeplane_plugin_ai/.../chat/ui/AIChatPanel.java`
  - `freeplane_plugin_ai/.../chat/request/ChatPromptRunner.java`
  - `freeplane_plugin_ai/.../chat/request/AIChatService.java`
  - `freeplane_plugin_ai/.../chat/request/AIChatServiceFactory.java`
  - `freeplane_plugin_ai/.../chat/request/SystemInstructionComposer.java`
  - `freeplane_plugin_ai/.../chat/request/SystemInstructionContext.java`
  - `freeplane_plugin_ai/.../chat/session/LiveChatController.java`
  - `freeplane_plugin_ai/.../chat/session/LiveChatSession.java`
  - `freeplane_plugin_ai/.../chat/session/TranscriptMemoryMapper.java`
  - `freeplane_plugin_ai/.../chat/history/ChatTranscriptRecord.java`
  - `freeplane_plugin_ai/.../chat/history/ChatTranscriptRole.java`
  - `freeplane_plugin_ai/.../chat/history/AssistantProfileTranscriptEntry.java`
  - `freeplane_plugin_ai/.../chat/memory/AssistantProfileChatMemory.java`
  - `freeplane_plugin_ai/.../chat/memory/GeneralSystemMessage.java`
  - `freeplane_plugin_ai/.../chat/memory/AssistantProfileSwitchMessage.java`
  - `freeplane_plugin_ai/.../chat/profile/AssistantProfileSelectionSync.java`
  - `freeplane_plugin_ai/.../chat/ui/ChatMemoryHistoryRenderer.java`
  - `freeplane_plugin_ai/.../tools/MessageBuilder.java`
- **Subtask order:**
  1. Persist captured chat system message.
  2. Persist profile message snapshots.
  3. Render the system-message chat-start block.
  4. Add brief/full rendering for instruction blocks.
  5. Add script API and routing changes.
  6. Compose dynamic instruction text and preview next request.
  7. Add exact request system-message API.

## Subtask: Persist captured chat system message
- **Status:** review

- **Research:**
  ```plantuml
  @startuml
  set separator none
  package "freeplane_plugin_ai.chat.memory" {
    class AssistantProfileChatMemory {
      - generalSystemMessage : GeneralSystemMessage
      + messages() : List<ChatMessage>
      + transcriptEntriesForPersistence() : List<ChatTranscriptEntry>
    }
    class GeneralSystemMessage {
      + text() : String
    }
  }
  package "freeplane_plugin_ai.chat.history" {
    class ChatTranscriptRecord {
      - entries : List<ChatTranscriptEntry>
    }
    class ChatTranscriptEntry {
      - role : ChatTranscriptRole
      - text : String
    }
    enum ChatTranscriptRole {
      USER
      ASSISTANT
      ASSISTANT_PROFILE_SYSTEM
      AUTOMATIC_CODE_STATUS
      REMOVED_FOR_SPACE_SYSTEM
    }
  }
  package "freeplane_plugin_ai.chat.session" {
    class TranscriptMemoryMapper {
      + seedTranscriptWithHiddenExchange(ChatMemory, Iterable<ChatTranscriptEntry>, String) : void
      + toTranscriptEntries(ChatMemory) : List<ChatTranscriptEntry>
    }
  }
  package "freeplane_plugin_ai.tools" {
    class MessageBuilder {
      + SYSTEM_MESSAGE_PROPERTY : String
      + buildForChat(ToolAvailabilityLevel) : String
    }
  }
  AssistantProfileChatMemory --> GeneralSystemMessage
  ChatTranscriptRecord "1" o-- "*" ChatTranscriptEntry
  ChatTranscriptEntry --> ChatTranscriptRole
  TranscriptMemoryMapper ..> AssistantProfileChatMemory
  MessageBuilder ..> ChatTranscriptRecord : no captured source
  @enduml
  ```

  - `AssistantProfileChatMemory` already has a `GeneralSystemMessage`
    slot. `messages()` and panel projection can include it, but
    transcript persistence currently omits it.
  - `TranscriptMemoryMapper.toTranscriptEntries(...)` delegates to
    `AssistantProfileChatMemory.transcriptEntriesForPersistence()`;
    current transcript roles do not include a system-message role.
  - `ChatTranscriptRecord` stores session metadata and transcript
    entries but has no captured system-message field.
  - `MessageBuilder` currently reads `ai_system_message` from
    `ResourceController` during request-time system-message
    construction.

- **Analysis:**
  - A visible chat's system message is chat-start state. It must be
    captured once and not changed later.
  - Global `ai_system_message` is only a default for new chats or
    migration of old transcripts without stored system messages.
  - Persisting the system message as a transcript entry keeps it in the
    same ordered chat-history model as other visible instruction
    blocks.

- **Design:**
  ```plantuml
  @startuml
  set separator none
  package "freeplane_plugin_ai.chat.memory" {
    class AssistantProfileChatMemory {
      - generalSystemMessage : GeneralSystemMessage
      + messages() : List<ChatMessage>
      + transcriptEntriesForPersistence() : List<ChatTranscriptEntry>
    }
    class GeneralSystemMessage {
      + text() : String
    }
  }
  package "freeplane_plugin_ai.chat.history" {
    class ChatTranscriptRecord {
      - entries : List<ChatTranscriptEntry>
    }
    class ChatTranscriptEntry {
      - role : ChatTranscriptRole
      - text : String
    }
    enum ChatTranscriptRole {
      SYSTEM
      USER
      ASSISTANT
      ASSISTANT_PROFILE_SYSTEM
      AUTOMATIC_CODE_STATUS
      REMOVED_FOR_SPACE_SYSTEM
    }
  }
  package "freeplane_plugin_ai.chat.session" {
    class TranscriptMemoryMapper {
      + seedTranscriptWithHiddenExchange(ChatMemory, Iterable<ChatTranscriptEntry>, String) : void
      + toTranscriptEntries(ChatMemory) : List<ChatTranscriptEntry>
    }
    class LiveChatController {
      + startNewPromptChat(ChatMemory, String, String, ToolAvailabilityLevel) : LiveChatSessionId
      - seedTranscriptMemory(LiveChatSession, ChatTranscriptRecord) : void
    }
  }
  package "freeplane_plugin_ai.tools" {
    class MessageBuilder {
      + buildForChat(String, ToolAvailabilityLevel) : String
      + buildForChat(ToolAvailabilityLevel) : String
    }
  }
  AssistantProfileChatMemory --> GeneralSystemMessage
  ChatTranscriptRecord "1" o-- "*" ChatTranscriptEntry
  ChatTranscriptEntry --> ChatTranscriptRole
  TranscriptMemoryMapper ..> GeneralSystemMessage
  LiveChatController ..> GeneralSystemMessage
  MessageBuilder ..> GeneralSystemMessage : uses captured text
  @enduml
  ```

  - Add `SYSTEM` to `ChatTranscriptRole` or an equivalent transcript
    representation that round-trips a single captured system message.
  - Persist `GeneralSystemMessage` as the first transcript instruction
    entry when it is present.
  - Restore the `SYSTEM` entry into `AssistantProfileChatMemory` as a
    `GeneralSystemMessage` before user/assistant transcript entries.
  - On visible chat creation, resolve the captured system message as:
    1. request system message, when supplied;
    2. otherwise current global `ai_system_message`;
    3. otherwise empty string.
  - Trim before storing. Preserve explicit empty string.
  - Legacy transcripts without a stored system message restore with a
    trimmed snapshot of the current global `ai_system_message` and
    persist that snapshot on the next save.
  - Refactor visible-chat system-message composition so existing chats
    use their captured `GeneralSystemMessage` instead of re-reading the
    global property.

- **Test specification:**
  - Automated tests:
    - Extend `AssistantProfileChatMemoryTest` to verify
      `GeneralSystemMessage` is projected for model use and included in
      transcript persistence.
    - Extend `TranscriptMemoryMapperTest` and `ChatTranscriptStoreTest`
      to verify system-message transcript save/restore and legacy
      transcript migration defaulting.
    - Add/adjust `MessageBuilderTest` or `AIChatServiceTest` coverage
      so visible chat services build provider guidance from a supplied
      captured base system message rather than directly reading the
      global property for existing chats.
  - Manual tests:
    - Restore an old transcript without a system-message entry and
      verify it receives a trimmed current-global snapshot.

## Subtask: Persist profile message snapshots
- **Status:** review

- **Research:**
  ```plantuml
  @startuml
  set separator none
  package "freeplane_plugin_ai.chat.profile" {
    class AssistantProfile {
      - id : String
      - name : String
      - prompt : String
    }
    class AssistantProfileSelectionModel {
      + getSelectedProfile() : AssistantProfile
      + findProfileById(String) : AssistantProfile
    }
    class AssistantProfileSelectionSync {
      + setChatMemory(ChatMemory) : void
      + maybeInjectBeforeUserMessage() : void
    }
  }
  package "freeplane_plugin_ai.chat.memory" {
    class AssistantProfileSwitchMessage {
      - profileId : String
      - profileName : String
      + getProfileId() : String
      + getProfileName() : String
    }
    class AssistantProfileInstructionMessage {
      - profileId : String
      - profileName : String
    }
    class AssistantProfileChatMemory {
      - profileInstructionFactory : ProfileInstructionFactory
      + transcriptEntriesForPersistence() : List<ChatTranscriptEntry>
    }
  }
  package "freeplane_plugin_ai.chat.history" {
    class AssistantProfileTranscriptEntry {
      - profileId : String
      - profileName : String
      - containsProfileDefinition : boolean
    }
  }
  AssistantProfileSelectionModel --> AssistantProfile
  AssistantProfileSelectionSync ..> AssistantProfileSelectionModel
  AssistantProfileSelectionSync ..> AssistantProfileSwitchMessage
  AssistantProfileChatMemory --> AssistantProfileSwitchMessage
  AssistantProfileChatMemory ..> AssistantProfileInstructionMessage
  AssistantProfileTranscriptEntry ..> AssistantProfileSwitchMessage : id and name only
  @enduml
  ```

  - `AssistantProfileSwitchMessage` currently stores profile id/name
    and builds marker text from name.
  - `AssistantProfileInstructionMessage` can include a profile
    definition, but current transcript persistence does not store the
    effective profile prompt text.
  - `AssistantProfileTranscriptEntry` stores profile id/name and
    `containsProfileDefinition`, but no durable text snapshot.
  - `AssistantProfileSelectionSync` resolves profile instructions from
    the current profile configuration, so later profile edits can alter
    restored old chats.

- **Analysis:**
  - A profile message is a point-in-time event. Its effective text must
    be snapshotted when the event is created.
  - Profile id/name alone are insufficient for restore correctness.
  - A changed current profile prompt is a behavior change and should be
    represented as a new profile event before the next user/script
    message.
  - Duplicate profile events are noise. If the active profile event has
    the same trimmed name and same trimmed effective message, no new
    event should be added.

- **Design:**
  ```plantuml
  @startuml
  set separator none
  package "freeplane_plugin_ai.chat.profile" {
    class AssistantProfileSelectionModel {
      + getSelectedProfile() : AssistantProfile
      + findProfileById(String) : AssistantProfile
    }
    class AssistantProfileSelectionSync {
      + setChatMemory(ChatMemory) : void
      + maybeInjectBeforeUserMessage() : void
    }
  }
  package "freeplane_plugin_ai.chat.memory" {
    class AssistantProfileSwitchMessage {
      - profileId : String
      - profileName : String
      - profileMessage : String
      + getProfileId() : String
      + getProfileName() : String
      + getProfileMessage() : String
    }
    class AssistantProfileInstructionMessage {
      - profileId : String
      - profileName : String
      - profileMessage : String
      + getProfileMessage() : String
    }
    class AssistantProfileChatMemory {
      - profileInstructionFactory : ProfileInstructionFactory
      + transcriptEntriesForPersistence() : List<ChatTranscriptEntry>
    }
  }
  package "freeplane_plugin_ai.chat.history" {
    class AssistantProfileTranscriptEntry {
      - profileId : String
      - profileName : String
      - profileMessage : String
      - containsProfileDefinition : boolean
    }
  }
  AssistantProfileSelectionSync ..> AssistantProfileSwitchMessage : injects when changed
  AssistantProfileChatMemory --> AssistantProfileSwitchMessage
  AssistantProfileChatMemory ..> AssistantProfileInstructionMessage : projects snapshot
  AssistantProfileTranscriptEntry <.. AssistantProfileSwitchMessage : stores snapshot
  @enduml
  ```

  - Extend `AssistantProfileSwitchMessage` with a trimmed effective
    profile message snapshot.
  - Extend `AssistantProfileTranscriptEntry` with the trimmed effective
    profile message snapshot. Keep id/name fields readable for older
    transcripts.
  - Resolve effective profile event text as:
    1. explicit message from `profile(name, message)`, if non-empty;
    2. configured profile prompt from `profile(name)`, if non-empty;
    3. `Now you have the profile <Name>.`, if profile name is
       non-empty.
  - If no profile option is supplied, create no profile event.
  - If `profile(name)` is used and no configured profile with that
    trimmed name exists, or more than one profile has that name, fail
    with `AiRequestStatus.CONFIGURATION_ERROR` before provider start.
  - `profile(name, message)` is local only: it must not create, update,
    overwrite, select, or persist a configured/global profile.
  - For visible chats, insert a requested profile event before the
    script/prompt user message only if trimmed name or trimmed effective
    message differs from the active profile event.
  - In AI Chat UI, compare selected profile id/name/effective text to
    the latest active profile event before the next user message. If
    name and effective text match, skip injection; otherwise inject a
    new profile event.
  - Model projection must use the stored snapshot, not current profile
    configuration, when reconstructing the latest profile instruction
    after compaction or transcript restore.

- **Test specification:**
  - Automated tests:
    - Extend `AssistantProfileChatMemoryTest` to verify profile switch
      messages carry effective profile text snapshots, model projection
      uses the stored snapshot after compaction, and profile messages do
      not count toward compaction token budgets.
    - Extend `TranscriptMemoryMapperTest` and `ChatTranscriptStoreTest`
      to verify profile message snapshot persistence and restore.
    - Add `AssistantProfileSelectionSyncTest` coverage for changed
      profile prompt text causing a new profile event and unchanged
      name/message skipping duplicate injection.
    - Add tests proving `profile(name, message)` does not mutate
      configured profiles.
  - Manual tests:
    - Change the selected profile's prompt text, send another chat
      message, and verify a new profile block appears before the
      message.

## Subtask: Render system-message chat-start block
- **Status:** review

- **Research:**
  ```plantuml
  @startuml
  set separator none
  package "freeplane_plugin_ai.chat.memory" {
    class PanelProjector {
      + buildRenderEntries(GeneralSystemMessage, FilteredChatMessages) : List<ChatMemoryRenderEntry>
    }
    class ChatMemoryRenderEntry {
      + chatMessage() : ChatMessage
    }
    class GeneralSystemMessage {
      + text() : String
    }
    class AssistantProfileSwitchMessage {
      + getProfileName() : String
    }
  }
  package "freeplane_plugin_ai.chat.ui" {
    class ChatMemoryHistoryRenderer {
      + rebuildFromMessages(List<ChatMemoryRenderEntry>) : void
    }
    class MessageHistory {
      + appendMessage(String, String, String) : void
    }
  }
  PanelProjector --> ChatMemoryRenderEntry
  ChatMemoryRenderEntry --> GeneralSystemMessage
  ChatMemoryRenderEntry --> AssistantProfileSwitchMessage
  ChatMemoryHistoryRenderer ..> GeneralSystemMessage : currently hides
  ChatMemoryHistoryRenderer ..> AssistantProfileSwitchMessage : renders profile block
  ChatMemoryHistoryRenderer --> MessageHistory
  @enduml
  ```

  - `PanelProjector` already includes `GeneralSystemMessage` in render
    entries.
  - `ChatMemoryHistoryRenderer` currently hides `GeneralSystemMessage`.
  - Profile switches are already rendered as profile blocks, but the
    block text currently depends on profile name rather than a full
    stored effective message snapshot.

- **Analysis:**
  - Persisted instruction state should be visible or at least directly
    accessible. A hidden persisted system message would make restored
    chat behavior hard to understand.
  - Rendering the chat-start system block makes the immutable chat
    system message clear without implying it changed mid-chat.

- **Design:**
  ```plantuml
  @startuml
  set separator none
  package "freeplane_plugin_ai.chat.memory" {
    class ChatMemoryRenderEntry {
      + chatMessage() : ChatMessage
    }
    class GeneralSystemMessage {
      + text() : String
    }
    class AssistantProfileSwitchMessage {
      + getProfileName() : String
      + getProfileMessage() : String
    }
  }
  package "freeplane_plugin_ai.chat.ui" {
    class ChatMemoryHistoryRenderer {
      + rebuildFromMessages(List<ChatMemoryRenderEntry>) : void
    }
    enum RenderCategory {
      SYSTEM
      PROFILE
      USER
      ASSISTANT
      TOOL_CALL
    }
    class MessageHistory {
      + appendMessage(String, String, String) : void
    }
  }
  ChatMemoryRenderEntry --> GeneralSystemMessage
  ChatMemoryRenderEntry --> AssistantProfileSwitchMessage
  ChatMemoryHistoryRenderer ..> GeneralSystemMessage : renders system block
  ChatMemoryHistoryRenderer ..> AssistantProfileSwitchMessage : renders snapshot block
  ChatMemoryHistoryRenderer --> RenderCategory
  ChatMemoryHistoryRenderer --> MessageHistory
  @enduml
  ```

  - Render `GeneralSystemMessage` as a dedicated start-chat instruction
    block instead of hiding it.
  - Render profile blocks using the stored profile event name/message
    snapshot from the active render entry.
  - Keep existing user/assistant/tool rendering unchanged.
  - Do not add the brief/full switch in this subtask; that is the next
    subtask.

- **Test specification:**
  - Automated tests:
    - Extend `ChatMemoryHistoryRendererTest` to verify system-message
      blocks render instead of being hidden.
    - Verify rendered profile blocks use stored snapshot data and not
      current profile configuration.
  - Manual tests:
    - Restore a chat and confirm system/profile instruction blocks are
      visible in the history.

## Subtask: Add brief/full rendering for instruction blocks
- **Status:** review

- **Research:**
  ```plantuml
  @startuml
  set separator none
  package "freeplane_plugin_ai.chat.ui" {
    class ChatMemoryHistoryRenderer {
      + rebuildFromMessages(List<ChatMemoryRenderEntry>) : void
    }
    class AIChatPanel {
      - messageHistory : MessageHistory
    }
    class MessageHistory {
      + appendMessage(String, String, String) : void
    }
  }
  package "freeplane_plugin_ai.chat.memory" {
    class GeneralSystemMessage
    class AssistantProfileSwitchMessage
  }
  AIChatPanel --> ChatMemoryHistoryRenderer
  ChatMemoryHistoryRenderer --> MessageHistory
  ChatMemoryHistoryRenderer ..> GeneralSystemMessage : no mode
  ChatMemoryHistoryRenderer ..> AssistantProfileSwitchMessage : brief only
  @enduml
  ```

  - Profile blocks are already rendered briefly in the chat history.
  - System blocks become renderable in the preceding subtask, but there
    is no useful brief system-message form.
  - Existing chat rendering goes through `ChatMemoryHistoryRenderer` and
    `MessageHistory` style classes.

- **Analysis:**
  - Brief rendering preserves chat density and current profile-block
    behavior.
  - In brief mode, only profile blocks are shown. System-message blocks
    are hidden because a label-only system block adds little value.
  - Full rendering is needed for transparency and review of captured
    instruction text.
  - One switch should control all persisted system/profile instruction
    messages so the user can choose between dense chat history and full
    instruction visibility.

- **Design:**
  ```plantuml
  @startuml
  set separator none
  package "freeplane_plugin_ai.chat.ui" {
    enum InstructionMessageRenderingMode {
      BRIEF
      FULL
    }
    class ChatMemoryHistoryRenderer {
      - instructionMessageRenderingMode : InstructionMessageRenderingMode
      + setInstructionMessageRenderingMode(InstructionMessageRenderingMode) : void
      + rebuildFromMessages(List<ChatMemoryRenderEntry>) : void
    }
    class AIChatPanel {
      - showInstructionMessagesAction : Action
    }
    class MessageHistory {
      + appendMessage(String, String, String) : void
    }
  }
  package "freeplane_plugin_ai.chat.memory" {
    class GeneralSystemMessage {
      + text() : String
    }
    class AssistantProfileSwitchMessage {
      + getProfileName() : String
      + getProfileMessage() : String
    }
  }
  AIChatPanel --> InstructionMessageRenderingMode
  AIChatPanel --> ChatMemoryHistoryRenderer
  ChatMemoryHistoryRenderer --> InstructionMessageRenderingMode
  ChatMemoryHistoryRenderer --> MessageHistory
  ChatMemoryHistoryRenderer ..> GeneralSystemMessage : hidden in BRIEF, full in FULL
  ChatMemoryHistoryRenderer ..> AssistantProfileSwitchMessage : brief in BRIEF, full in FULL
  @enduml
  ```

  - Add a chat-panel switch for instruction-block rendering mode.
  - Use short UI text `Show instruction messages` and tooltip text
    `Show full system and profile messages in the chat`.
  - Default to brief mode to preserve current chat-history density.
  - Brief mode renders profile blocks in their brief label form, such
    as `Profile: <Name>`, and hides system-message blocks.
  - Full mode renders the full trimmed system and profile content from
    the stored snapshots.
  - Apply the switch to both `GeneralSystemMessage` and profile event
    render entries: system entries are hidden in brief mode and shown
    full in full mode; profile entries are brief in brief mode and full
    in full mode.
  - Persist the UI preference if the surrounding chat UI already has an
    appropriate settings pattern; otherwise keep it session-local.

- **Test specification:**
  - Automated tests:
    - Extend `ChatMemoryHistoryRendererTest` to verify brief mode shows
      profile blocks, hides system blocks, and full mode shows full
      system/profile content.
    - Add UI/controller tests for switching modes if the switch has a
      controller or persisted preference.
  - Manual tests:
    - Toggle brief/full mode and verify rendered system/profile blocks
      update without changing transcript content.

- **Implementation notes:**
  - **Tradeoffs:**
    - Kept the instruction-message rendering switch session-local
      because the surrounding chat popup has no existing persisted
      preference pattern for this kind of display toggle.

## Subtask: Add script API and routing changes
- **Status:** review

- **Research:**
  ```plantuml
  @startuml
  autonumber
  actor Script
  participant ControllerProxy
  participant ScriptAiRequestService
  participant AiRequestExecutionCoordinator
  participant AIChatPanel
  participant ChatPromptRunner
  participant AIChatService
  participant AIToolSet
  participant MessageBuilder

  Script -> ControllerProxy : askAi(prompt, options, callback)
  ControllerProxy -> ScriptAiRequestService : askAi(prompt, options, callback)
  ScriptAiRequestService -> AiRequestExecutionCoordinator : ResolvedAiRequest
  AiRequestExecutionCoordinator -> AIChatPanel : mode dispatch
  AIChatPanel -> ChatPromptRunner : start shown/add/hidden request
  ChatPromptRunner -> AIChatService : create request service
  AIChatService -> AIToolSet : systemMessageForChat(input, availability)
  AIToolSet -> MessageBuilder : buildForChat(availability)
  MessageBuilder --> AIToolSet : global ai_system_message + guidance
  AIToolSet --> AIChatService : provider system message
  @enduml
  ```

  ```plantuml
  @startuml
  set separator none
  package "freeplane_api.ai" {
    enum AiRequestMode {
      SHOW_IN_CHAT
      ADD_TO_CHAT
      HIDDEN_WITH_CANCEL_DIALOG
      HIDDEN
    }
    class AiRequestOptions {
      - timeout : Duration
      - mode : AiRequestMode
      - modelSelection : AiModelSelection
      - toolAvailability : AiToolAvailability
      - selectionOverride : AiSelectionOverride
      + getMode() : AiRequestMode
    }
  }
  package "freeplane_plugin_ai.chat.request" {
    class ResolvedAiRequest {
      - promptText : String
      - promptDisplayName : String
      - mode : AiRequestMode
      - modelSelection : AiModelSelection
      - toolAvailability : AiToolAvailability
      - selectionOverride : AiSelectionOverride
    }
  }
  AiRequestOptions --> AiRequestMode
  ResolvedAiRequest --> AiRequestMode
  @enduml
  ```

  - `AIChatPanel.startShownAiRequest(...)` already starts a new prompt
    chat.
  - `AIChatPanel.startAddToChatAiRequestAtDispatch(...)` currently
    chooses the current chat when the chat tab is selected, otherwise a
    new chat.
  - `ScriptAiRequestService` normalizes `AiRequestOptions` into
    `ResolvedAiRequest` for raw `askAi(...)` and saved prompt
    execution.
  - `AIChatService.systemMessageProvider(...)` currently starts from
    `AIToolSet.systemMessageForChat(...)` and appends code-host
    guidance when available.

- **Analysis:**
  - `SHOW_IN_CHAT` is ambiguous because `ADD_TO_CHAT` also shows in
    chat. The API should say what it does: create a new visible chat.
  - `ADD_TO_CHAT` should be the only mode that may append to an
    existing visible chat.
  - If a visible script request asks for a different system message,
    appending would mutate the meaning of an existing chat. Starting a
    new chat preserves the immutability rule.
  - Hidden exact system messages are justified by exact request control,
    not by small token savings.

- **Design:**
  ```plantuml
  @startuml
  set separator none
  package "freeplane_api.ai" {
    enum AiRequestMode {
      SHOW_IN_NEW_CHAT
      ADD_TO_CHAT
      HIDDEN_WITH_CANCEL_DIALOG
      HIDDEN
    }
    class AiRequestOptions {
      - timeout : Duration
      - mode : AiRequestMode
      - modelSelection : AiModelSelection
      - toolAvailability : AiToolAvailability
      - selectionOverride : AiSelectionOverride
      - systemMessage : String
      - profileName : String
      - profileMessage : String
      + getSystemMessage() : String
      + getProfileName() : String
      + getProfileMessage() : String
      + builder() : Builder
    }
    class Builder {
      + timeout(Duration) : Builder
      + mode(AiRequestMode) : Builder
      + modelSelection(AiModelSelection) : Builder
      + toolAvailability(AiToolAvailability) : Builder
      + selectionOverride(AiSelectionOverride) : Builder
      + systemMessage(String) : Builder
      + profile(String) : Builder
      + profile(String, String) : Builder
      + build() : AiRequestOptions
    }
  }
  package "freeplane_plugin_ai.chat.request" {
    class ResolvedAiRequest {
      - systemMessage : String
      - profileName : String
      - profileMessage : String
      + getSystemMessage() : String
      + getProfileName() : String
      + getProfileMessage() : String
    }
    class AIChatService {
      + systemMessageProvider(ToolAvailabilityLevel) : Function<Object, String>
    }
  }
  AiRequestOptions --> AiRequestMode
  AiRequestOptions +-- Builder
  ResolvedAiRequest --> AiRequestMode
  @enduml
  ```

  ```plantuml
  @startuml
  autonumber
  actor Script
  participant ControllerProxy
  participant ScriptAiRequestService
  participant AiRequestExecutionCoordinator
  participant AIChatPanel
  participant LiveChatController
  participant ChatPromptRunner
  participant AIChatService

  Script -> ControllerProxy : askAi(prompt, options, callback)
  ControllerProxy -> ScriptAiRequestService : normalize system/profile fields
  ScriptAiRequestService -> AiRequestExecutionCoordinator : ResolvedAiRequest
  AiRequestExecutionCoordinator -> AIChatPanel : dispatch by AiRequestMode
  AIChatPanel -> LiveChatController : choose or create compatible visible chat
  LiveChatController -> LiveChatController : compare captured system message for ADD_TO_CHAT
  AIChatPanel -> LiveChatController : insert non-duplicate profile event
  AIChatPanel -> ChatPromptRunner : start request
  ChatPromptRunner -> AIChatService : create service with captured/request system state
  @enduml
  ```

  - Replace `AiRequestMode.SHOW_IN_CHAT` with
    `AiRequestMode.SHOW_IN_NEW_CHAT`. Do not keep a legacy enum alias
    unless explicitly required later.
  - Extend `AiRequestOptions` with nullable `systemMessage`,
    `profileName`, and `profileMessage` state and getters.
  - Add builder methods:
    - `systemMessage(String)`;
    - `profile(String name)`;
    - `profile(String name, String message)`.
  - Trim all three values during build or request normalization.
  - `systemMessage(null)` means no request system message. Any
    non-`null` value, including `""`, is explicit.
  - `profile(String name)` resolves a configured profile by trimmed
    display name. Null, empty, missing, or ambiguous name fails with
    `AiRequestStatus.CONFIGURATION_ERROR` before provider start.
  - `profile(String name, String message)` creates a local explicit
    profile event. Reject `null` message; callers must use
    `profile(name)` for configured-profile lookup. This overload must
    not mutate configured/global profiles.
  - For `profile(name, message)`, if trimmed message is empty and
    trimmed name is non-empty, use `Now you have the profile <Name>.`.
    If trimmed name is empty and trimmed message is non-empty, render
    the event with a generic profile label. If both are empty, create no
    profile event.
  - Copy request system/profile fields into `ResolvedAiRequest` for raw
    `askAi(...)` and `runAiPrompt(..., AiRequestOptions, ...)`.
  - Visible routing:
    - `SHOW_IN_NEW_CHAT` always creates a new visible chat with the
      request system message or current global default snapshot.
    - `ADD_TO_CHAT` with no request system message appends to the
      selected chat when available; if no selected chat is available,
      start a new chat with the current global default snapshot. Do not
      compare with the current global `ai_system_message` when a
      selected chat is available.
    - `ADD_TO_CHAT` with a request system message compares the trimmed
      value to the selected chat's captured system message. If equal,
      append. If different, start a new visible chat with the request
      system message.
    - Requested visible profile events are inserted before the script
      prompt only when they differ from the active profile event.
  - Hidden routing:
    - For hidden effective modes, a non-`null` request system message
      is the exact provider system content for that one request.
    - Hidden exact system content skips
      `AIToolSet.systemMessageForChat(...)` and
      `AiCodeToolSet.systemMessageForChat(...)` guidance assembly.
    - Hidden requests without a request system message keep existing
      composed system-message behavior.
    - Hidden profile messages may be injected into the temporary hidden
      chat memory for that one request. They are not persisted.

- **Test specification:**
  - Automated tests:
    - Extend `freeplane_api` `AiRequestOptionsTest` for
      `SHOW_IN_NEW_CHAT`, `systemMessage` defaults, `profile(name)` and
      `profile(name, message)` trimming, stored profile name/message
      getters, explicit empty system message preservation, null-message
      rejection for the two-argument overload, and builder round-trip.
    - Extend `ScriptAiRequestServiceTest` to verify raw `askAi(...)`
      and `runAiPrompt(..., AiRequestOptions, ...)` copy the new
      request fields into `ResolvedAiRequest`.
    - Add profile-resolution tests for `profile(name)` configured-
      profile success, null/empty/missing name failure, duplicate name
      failure, `profile(name, message)` explicit text,
      `profile("", message)` generic-label explicit text,
      two-argument overload not modifying configured profiles, and
      empty-message fallback to `Now you have the profile <Name>.`.
    - Extend `AIChatPanelScriptRequestTest` to verify:
      - `SHOW_IN_NEW_CHAT` always starts a new chat with the
        request/default system message snapshot;
      - `ADD_TO_CHAT` appends to the selected chat when no request
        system message is supplied, without comparing current global
        `ai_system_message`;
      - `ADD_TO_CHAT` appends when the trimmed request system message
        equals the selected chat system message;
      - `ADD_TO_CHAT` starts a new chat when the trimmed request system
        message differs;
      - requested profile events are inserted before the script prompt
        when they differ from the active profile;
      - requested profile events are skipped when trimmed profile name
        and trimmed effective message both match the active profile.
    - Extend `AIChatServiceTest` to verify hidden non-`null` request
      system message returns exact provider system content and bypasses
      `AIToolSet` and `AiCodeToolSet` guidance composition.
  - Manual tests:
    - Run `askAi` with `SHOW_IN_NEW_CHAT`, request system message, and
      explicit profile message; verify a new chat starts and both
      instruction blocks are stored.
    - Run `askAi` with `ADD_TO_CHAT` and matching/different system
      messages; verify append/new-chat behavior.
    - Run hidden `askAi` with a request system message; verify the
      provider request uses exactly that system content and no
      Freeplane-added system guidance while tool availability remains
      enforced.


## Subtask: Compose dynamic instruction text and preview next request
- **Status:** review

- **Research:**
  ```plantuml
  @startuml
  set separator none
  package "freeplane_plugin_ai.chat.request" {
    class AIChatService {
      - systemMessage : String
      - exactSystemMessage : boolean
      + systemMessageProvider(ToolAvailabilityLevel) : Function
    }
    class ChatPromptRunner {
      + submitHiddenRequest(..., systemMessage, exactSystemMessage, requestedProfileMessage) : boolean
      + startShownPrompt(..., requestedProfileMessage) : boolean
    }
  }
  package "freeplane_plugin_ai.tools" {
    class MessageBuilder {
      + buildForChat(String, ToolAvailabilityLevel) : String
    }
  }
  package "freeplane_plugin_ai.chat.memory" {
    class GeneralSystemMessage {
      + text() : String
    }
    class AssistantProfileChatMemory {
      + capturedSystemMessage() : String
      + latestProfileSwitchMessage() : AssistantProfileSwitchMessage
    }
  }
  package "freeplane_plugin_ai.chat.ui" {
    enum InstructionMessageRenderingMode {
      BRIEF
      FULL
    }
    class ChatMemoryHistoryRenderer
  }
  AIChatService ..> MessageBuilder : dynamic provider text
  AIChatService ..> GeneralSystemMessage : separate stored text
  ChatPromptRunner ..> AIChatService : exact hidden override exists
  ChatMemoryHistoryRenderer ..> GeneralSystemMessage : renders stored text
  @enduml
  ```

  - Current implementation stores a `GeneralSystemMessage` in chat
    memory but composes dynamic provider system text separately in
    `AIChatService.systemMessageProvider(...)`.
  - Hidden script requests can currently set `exactSystemMessage` and
    bypass `AIToolSet.systemMessageForChat(...)` and
    `AiCodeToolSet.systemMessageForChat(...)` guidance assembly.
  - `MessageBuilder.buildForChat(...)` currently includes
    profile-control guidance for all composed chat system messages and
    includes Markdown response guidance whenever it is used, including
    hidden dynamic requests.
  - `InstructionMessageRenderingMode` currently supports only `BRIEF`
    and `FULL`, so there is no non-persistent preview of the next
    visible request's instruction state.
  - LangChain4j supplies generated tool descriptions/schemas outside
    Freeplane's `systemMessageProvider(...)` string. Those generated
    descriptions are effective provider context but are not directly
    part of the Freeplane-composed system text.

- **Analysis:**
  - Captured/request system text is base user text. Treating it as an
    exact provider system message hides required dynamic Freeplane
    guidance and can omit tool/protocol constraints.
  - Rendering only stored base text is incomplete because the provider
    receives dynamic Freeplane guidance in addition to the base text.
  - Rendering future dynamic guidance as committed chat history is also
    wrong when a pending profile event has not yet been injected.
  - A separate next-request preview toggle can show what the next
    visible request would send without persisting unsent profile or
    system instruction blocks.
  - Rendering preview in a separate component is simpler and safer than
    treating preview blocks as a transient suffix of chat history,
    because committed-history append, selection, and copy behavior stay
    unchanged.

- **Design:**
  ```plantuml
  @startuml
  set separator none
  package "freeplane_plugin_ai.chat.request" {
    class SystemInstructionComposer {
      + compose(SystemInstructionContext) : String
    }
    class SystemInstructionContext {
      - baseSystemMessage : String
      - toolAvailability : ToolAvailabilityLevel
      - visibility : RequestVisibility
      - hasProfileInstruction : boolean
    }
    enum RequestVisibility {
      VISIBLE
      HIDDEN
    }
    class AIChatService
  }
  package "freeplane_plugin_ai.chat.memory" {
    class GeneralSystemMessage {
      + baseText() : String
      + composedText() : String
    }
    class AssistantProfileChatMemory {
      + capturedSystemMessage() : String
      + hasProfileInstruction() : boolean
    }
  }
  package "freeplane_plugin_ai.chat.ui" {
    enum InstructionHistoryRenderingMode {
      BRIEF
      FULL
    }
    class ChatMemoryHistoryRenderer {
      - historyMode : InstructionHistoryRenderingMode
      + rebuildFromMessages(...) : void
    }
    class NextRequestInstructionPreviewView {
      + showPreview(List<PreviewInstructionBlock>) : void
      + hidePreview() : void
    }
    class PreviewInstructionBlock {
      - label : String
      - text : String
      - kind : PreviewInstructionKind
    }
    enum PreviewInstructionKind {
      SYSTEM
      PROFILE
    }
    class AIChatPanel
  }
  SystemInstructionComposer --> SystemInstructionContext
  AIChatService ..> SystemInstructionComposer : provider text
  AIChatPanel ..> SystemInstructionComposer : preview text
  AIChatPanel --> NextRequestInstructionPreviewView
  GeneralSystemMessage ..> SystemInstructionComposer : committed composed text
  @enduml
  ```

  - Replace exact hidden system-message handling with composed system
    instruction handling. A request system message is always trimmed
    base text and must not bypass dynamic Freeplane guidance.
  - Introduce a single Freeplane system-instruction composer used for
    provider calls, committed full rendering, and next-request preview
    rendering. Its output excludes LangChain4j-generated tool
    descriptions/schemas.
  - Composer inputs must include at least:
    - trimmed base system text;
    - effective `ToolAvailabilityLevel`;
    - visible vs hidden request visibility;
    - whether a profile message is already present or will be injected;
    - code-host guidance source, when available.
  - Composer output rules:
    - include base system text when non-empty;
    - include tool-availability, map-selection, read-only/no-tools,
      tool-call-wrapper, and code-host guidance as applicable;
    - include profile-control guidance only if a committed or pending
      profile message applies to the request;
    - include Markdown response guidance only for visible chat
      requests;
    - exclude automatically generated LangChain4j tool descriptions and
      schemas from rendered instruction text.
  - Keep captured visible-chat system text as base text for
    `ADD_TO_CHAT` compatibility checks. Compare request base text to
    selected-chat captured base text, not to composed dynamic text.
  - Before a visible provider request starts, resolve any requested or
    pending profile event, compose the actual Freeplane system text for
    that request, update the committed rendered system instruction
    state, then append the profile event and user message.
  - Hidden requests compose provider system text from the same composer
    with hidden visibility. Hidden requests do not persist or render the
    composed text, and they omit Markdown response guidance.
  - Replace the single instruction rendering checkbox with two
    independent controls:
    - historical instruction rendering: brief vs full committed
      instruction history;
    - next-request instruction preview: off vs on.
  - Valid UI states are dense history without preview, full committed
    history without preview, dense history with preview, and full
    committed history with preview.
  - Use checkbox-style controls, not radio buttons, because previewing
    the next request is independent from rendering historical
    instruction blocks in full.
  - Suggested UI labels:
    - `Show instruction history` for committed historical full
      rendering;
    - `Preview next request instructions` for the non-persistent
      preview.
  - Committed full system and profile instruction blocks should share
    the same instruction-message color/style because both are controlled
    by historical instruction rendering and both are instruction
    messages at the user-visible level.
  - Preview must use a separate component placed after the profile
    selector and before the message input. The profile selector is an
    input to the preview, so placing the preview after it makes the
    preview read as the effect of the selected profile and other current
    request controls. The component should reuse the chat message
    renderer/style scheme, use its own preview style/color close to the
    instruction-message style, and rely on separate placement plus block
    labels so preview content cannot be mistaken for sent transcript
    content. Use dedicated translation keys for preview system/profile
    block labels. Do not render a preview title inside the component.
  - Preview must resolve instruction state as if the next visible user
    message were being submitted: apply any pending profile event first,
    include the effective profile block in the preview, using the
    pending profile message when present and otherwise repeating the
    latest committed profile message, and set the composer
    profile-instruction input from the post-injection preview state.
    Render the preview system block before any profile block, with a
    horizontal separator between the system and profile blocks. If the
    chat has no committed conversation entries, or only MCP tool-call
    summaries, hide the initial history system block; when preview is
    enabled, show the preview component as the only instruction
    rendering. Preview blocks must not be added to chat memory,
    transcript persistence, undo/redo history, token accounting,
    provider request history, or the upper chat-history panel.
  - When preview is enabled, refresh preview rendering whenever any
    preview input changes, including current/selected chat, captured or
    default base system text, effective tool availability, code-host
    guidance state, formula-editing availability, committed profile
    messages, pending selected/requested profile name, or pending
    selected/requested profile message text.
  - Preview-component blocks must not be appended to
    `ChatMessageHistory` and must not participate in committed
    chat-history selection/copy. When a user message, assistant
    response, tool summary, profile event, or other committed entry is
    appended while preview is visible, append it normally to committed
    history and refresh or hide the separate preview component
    independently.

- **Test specification:**
  - Automated tests:
    - Add composer tests for visible vs hidden output, empty/base
      system text, tool-availability guidance, profile-control guidance
      present only when profile messages exist or are pending, Markdown
      guidance omitted for hidden requests, and code-host guidance
      inclusion.
    - Extend `AIChatServiceTest` and hidden request tests to verify a
      request system message is base text plus dynamic guidance, not an
      exact bypass.
    - Extend visible request tests to verify committed full rendering
      uses the same Freeplane-composed text sent through
      `systemMessageProvider(...)`, excluding LangChain4j-generated
      tool schemas.
    - Extend `AIChatPanelScriptRequestTest` to verify `ADD_TO_CHAT`
      compatibility compares captured base system text, not composed
      dynamic text.
    - Extend `ChatMemoryHistoryRendererTest` or panel rendering tests
      for brief/full historical rendering independent from preview
      on/off, including shared committed instruction styling for full
      system and profile blocks.
    - Add preview component/panel tests covering placement after the
      profile selector and before the message input, distinct preview
      styling close to instruction-message styling, translated preview
      labels, empty-chat suppression of the initial base-only history
      system block, suppression of the initial history system block when the
      only other entries are MCP tool-call summaries, no preview
      rendering in the upper chat-history panel, exclusion from
      transcript persistence, and exclusion from committed chat-history
      selection/copy.
    - Add tests proving preview applies pending profile injection before
      composing the previewed system text, includes the effective
      profile block, repeats the latest committed profile message when
      no pending profile change exists, enables profile-control guidance
      from that post-injection preview state, and does not mutate chat
      memory.
    - Add tests proving enabled preview refreshes when preview inputs
      change, including tool availability, base system text, code-host
      guidance state, pending profile name, and pending profile message
      text.
    - Add tests proving committed entries appended while preview is
      visible are appended only to committed history and do not require
      suffix reordering; the separate preview component is refreshed or
      hidden independently.
  - Manual tests:
    - Create a visible chat with a request system message and verify
      full rendering immediately shows Freeplane-composed guidance, not
      only the base user text.
    - Change tool availability and verify preview changes while
      committed history remains unchanged until a request starts.
    - Enable preview and verify it appears after the profile selector
      and before the message input, not as a committed chat-history
      entry.
    - Run a hidden request with a request system message and verify
      provider system text includes dynamic Freeplane guidance but no
      Markdown response guidance.
    - Use a chat with no profile messages and verify profile-control
      guidance is absent; then inject a profile and verify it appears.

## Subtask: Add exact request system-message API
- **Status:** done

- **Research:**
  ```plantuml
  @startuml
  set separator none
  package "freeplane_api.ai" {
    class AiRequestOptions {
      - systemMessage : String
      + getSystemMessage() : String
      + builder() : Builder
    }
    class Builder {
      + systemMessage(String) : Builder
    }
  }
  package "freeplane_plugin_ai.chat.request" {
    class ResolvedAiRequest {
      - systemMessage : String
      + getSystemMessage() : String
    }
    class SystemInstructionContext {
      - baseSystemMessage : String
      - toolAvailability : ToolAvailabilityLevel
      - visibility : RequestVisibility
      - hasProfileInstruction : boolean
      - codeHostGuidance : String
    }
    class SystemInstructionComposer {
      + compose(SystemInstructionContext) : String
    }
    class AIChatService {
      - baseSystemMessage : String
      - hiddenRequest : boolean
      + systemMessageProvider(ToolAvailabilityLevel) : Function<Object, String>
    }
  }
  package "freeplane_plugin_ai.tools" {
    class MessageBuilder {
      + buildForChat(String, ToolAvailabilityLevel, boolean, boolean) : String
    }
  }
  package "freeplane_plugin_ai.chat.memory" {
    class GeneralSystemMessage {
      - baseText : String
      - composedText : String
      + baseText() : String
      + composedText() : String
    }
  }
  package "freeplane_plugin_ai.chat.history" {
    class ChatTranscriptEntry {
      - text : String
      - baseSystemText : String
    }
  }
  AiRequestOptions --> ResolvedAiRequest : copies text only
  ResolvedAiRequest --> AIChatService
  AIChatService --> SystemInstructionContext
  SystemInstructionComposer ..> SystemInstructionContext
  SystemInstructionComposer ..> MessageBuilder : always composes guidance
  GeneralSystemMessage --> ChatTranscriptEntry : persists no exact flag
  @enduml
  ```

  - `AiRequestOptions.Builder.systemMessage(String)` stores one nullable
    system-message string. `AiRequestOptions` has no state that
    distinguishes a normal base override from an exact override.
  - `ScriptAiRequestService` copies only the normalized system-message
    text into `ResolvedAiRequest` for raw `askAi(...)` and
    `runAiPrompt(..., AiRequestOptions, ...)`.
  - `SystemInstructionComposer.compose(...)` always calls
    `MessageBuilder.buildForChat(...)` and then appends code-host
    guidance when present. Its context has no exact-system flag.
  - `AIChatService.systemMessageProvider(...)` chooses either the
    request base system message or the configured global message, then
    asks `SystemInstructionComposer` to add dynamic guidance.
  - Visible chat memory stores `GeneralSystemMessage(baseText,
    composedText)`. Transcript persistence stores `text` and
    `baseSystemText`, but no flag that would preserve exact-system
    behavior across restore or later visible-chat requests.
  - `ADD_TO_CHAT` compatibility compares only the request base system
    text with the selected chat's captured base system text.

- **Analysis:**
  - Keep `systemMessage(String)` as a normal base override because
    existing callers rely on dynamic Freeplane guidance being composed
    around that text.
  - Exact system-message behavior needs explicit state because the same
    trimmed text can mean either "base text plus dynamic additions" or
    "exact provider system text".
  - Visible chats need to persist exactness with captured system state;
    otherwise restore or a later turn could silently add dynamic
    guidance to a chat that started with an exact system message.
  - `ADD_TO_CHAT` compatibility must include exactness, not only base
    text, because appending an exact request to a non-exact chat or the
    reverse changes the effective provider system contract.
  - Exactness suppresses Freeplane-composed system additions only. It
    must not change `AiToolAvailability`, allowed tool names, or
    provider-side generated tool descriptions/schemas.

- **Design:**
  ```plantuml
  @startuml
  set separator none
  package "freeplane_api.ai" {
    class AiRequestOptions {
      - systemMessage : String
      - isSystemMessageExact : boolean
      + getSystemMessage() : String
      + isSystemMessageExact() : boolean
      + builder() : Builder
    }
    class Builder {
      + systemMessage(String) : Builder
      + exactSystemMessage(String) : Builder
    }
  }
  package "freeplane_plugin_ai.chat.request" {
    class ResolvedAiRequest {
      - systemMessage : String
      - isSystemMessageExact : boolean
      + getSystemMessage() : String
      + isSystemMessageExact() : boolean
    }
    class SystemInstructionContext {
      - baseSystemMessage : String
      - isSystemMessageExact : boolean
      - toolAvailability : ToolAvailabilityLevel
      - visibility : RequestVisibility
      - hasProfileInstruction : boolean
      - codeHostGuidance : String
    }
    class SystemInstructionComposer {
      + compose(SystemInstructionContext) : String
    }
    class AIChatService {
      - baseSystemMessage : String
      - isSystemMessageExact : boolean
      - hiddenRequest : boolean
      + systemMessageProvider(ToolAvailabilityLevel) : Function<Object, String>
    }
  }
  package "freeplane_plugin_ai.chat.memory" {
    class GeneralSystemMessage {
      - baseText : String
      - composedText : String
      - isSystemMessageExact : boolean
      + baseText() : String
      + composedText() : String
      + isSystemMessageExact() : boolean
    }
  }
  package "freeplane_plugin_ai.chat.history" {
    class ChatTranscriptEntry {
      - text : String
      - baseSystemText : String
      - isSystemMessageExact : boolean
    }
  }
  AiRequestOptions --> ResolvedAiRequest
  ResolvedAiRequest --> AIChatService
  AIChatService --> SystemInstructionContext
  SystemInstructionComposer ..> SystemInstructionContext
  GeneralSystemMessage --> ChatTranscriptEntry
  @enduml
  ```

  - Add `AiRequestOptions.Builder.exactSystemMessage(String)` and
    `AiRequestOptions.isSystemMessageExact()`.
  - `systemMessage(String)` keeps current semantics and sets
    `isSystemMessageExact` to `false`.
  - `exactSystemMessage(String)` stores the same normalized
    `systemMessage` value and sets `isSystemMessageExact` to `true` when
    the value is non-`null`. `exactSystemMessage(null)` clears the
    request system message and clears exactness.
  - Explicit empty exact text is valid: `exactSystemMessage("")` builds
    an empty exact system message with `isSystemMessageExact() == true`.
  - If a builder receives both `systemMessage(...)` and
    `exactSystemMessage(...)`, the later call wins for both text and
    exactness.
  - Copy `isSystemMessageExact` into `ResolvedAiRequest` for raw
    `askAi(...)` and `runAiPrompt(..., AiRequestOptions, ...)`.
  - Add `isSystemMessageExact` to `SystemInstructionContext`. When it is
    true, `SystemInstructionComposer.compose(...)` returns the trimmed
    base system message exactly and does not call
    `MessageBuilder.buildForChat(...)` or append code-host guidance.
  - Hidden requests pass `isSystemMessageExact` through
    `ChatPromptRunner`, `AIChatServiceFactory`, and `AIChatService`.
    Normal request system messages keep current dynamic composition;
    exact request system messages produce exact provider system text.
  - Visible new-chat routing stores both captured base text and
    exactness in `GeneralSystemMessage`. New chats without a request
    system message use the global default with non-exact system state.
  - Persist and restore the exact flag with system transcript entries.
  - `ADD_TO_CHAT` with a request system message appends only when both
    the trimmed base text and `isSystemMessageExact` match the selected
    chat. Otherwise it starts a new visible chat. `ADD_TO_CHAT` without
    a request system message appends to the selected chat when available
    and preserves that chat's existing exactness.
  - Committed full instruction rendering and next-request preview pass
    the visible chat's exact flag into `SystemInstructionComposer`, so
    exact chats show exact system text and non-exact chats show composed
    dynamic guidance.
  - Do not change allowed-tool calculation, tool registration, profile
    event insertion, prompt-reference composition, token accounting, or
    LangChain4j-generated tool descriptions/schemas.

- **Test specification:**
  - Automated tests:
    - Extend `AiRequestOptionsTest` to verify
      `exactSystemMessage(String)`, `isSystemMessageExact()`, trimming,
      explicit empty exact text, `exactSystemMessage(null)` clearing
      exactness, and last-call-wins behavior with `systemMessage(...)`.
    - Extend `ScriptAiRequestServiceTest` to verify raw `askAi(...)`
      and `runAiPrompt(..., AiRequestOptions, ...)` copy the exact flag
      into `ResolvedAiRequest`.
    - Extend `SystemInstructionComposerTest` to verify exact contexts
      return exact trimmed base text for visible and hidden requests,
      all tool-availability levels, profile-control input, and
      code-host guidance input, including explicit empty exact system
      text.
    - Extend `AIChatServiceTest` to verify exact provider system text
      bypasses Freeplane-composed dynamic guidance while allowed tool
      selection remains governed by `AiToolAvailability`.
    - Extend `TranscriptMemoryMapperTest` and `ChatTranscriptStoreTest`
      to verify exact-system flag persistence and restore.
    - Extend `AIChatPanelScriptRequestTest` to verify visible new-chat
      creation with exact system state, committed system rendering with
      exact text, `ADD_TO_CHAT` compatibility comparing both base text
      and exactness, `ADD_TO_CHAT` without a request system message
      preserving selected-chat exactness, and next-request preview
      rendering exact text for exact chats.
  - Manual tests: N/A
