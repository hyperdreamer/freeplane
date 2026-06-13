# Task: Add request-scoped AI system message override

- **Task Identifier:** 2026-06-13-message-override
- **Scope:**
  Add a request-scoped exact system-message override to the script AI
  request API for hidden effective request modes only. Visible chat
  modes stay out of scope until visible-chat system-message behavior is
  designed. Do not include the already-completed removal of
  `Answer as a general-purpose assistant.` from disabled-tools
  guidance in this task.
- **Motivation:**
  Script callers still cannot send an exact system message for one
  `askAi(...)` or `runAiPrompt(...)` request without mutating the
  global `ai_system_message` property. The first reported use case is a
  script that sends node text to the model and uses the callback result
  to rewrite node content. That use case does not require visible chat
  state.
- **Scenario:**
  A Groovy script rewrites the selected node text by calling
  `c.askAi(...)` with `AiToolAvailability.DISABLED`,
  `AiRequestMode.HIDDEN` or `AiRequestMode.HIDDEN_WITH_CANCEL_DIALOG`,
  and `AiRequestOptions.systemMessageOverride("You are an expert
  editor ...")`. Freeplane sends that exact system message for that
  one request, still exposes no tools, and does not append built-in
  no-tools, profile-control, Markdown, or code-host guidance. Visible
  chat requests keep current behavior and must not accept the override
  in this task.
- **Glossary:**
  ```mermaid
  flowchart LR
    A["AiRequestOptions.systemMessageOverride(String)"] -- sets --> B["system message override"]
    B -- replaces --> C["request system message"]
    C -- applies to --> D["hidden script AI request"]
    E["AiToolAvailability"] -- still limits --> F["tool access"]
    B -- does not change --> F
  ```

  - `system message override`
    - Meaning: request-scoped exact replacement for the system message
      used for one hidden script AI request.
    - Usage:
      - When present, it replaces the entire built-in system message
        for that request.
      - It does not change tool authorization, tool filtering, model
        selection, timeout, or selection override behavior.
      - It is out of scope for visible chat modes in this task.
  - `AiRequestOptions.systemMessageOverride(String)`
    - Meaning: shared term from Freeplane public script AI request
      API.
    - Usage:
      - `null` means no override; Freeplane composes the normal system
        message.
      - Any non-`null` string, including the empty string, becomes the
        exact system message text for that request.
      - The method belongs on `AiRequestOptions.Builder`; no new
        positional `askAi(String, String, ...)` overload is added.
- **Constraints:**
  - Keep `AiToolAvailability` as the real authorization boundary.
    System-message text must not grant or suppress tools beyond the
    existing availability rules.
  - Limit this task to hidden effective request modes. Visible modes
    `SHOW_IN_CHAT` and `ADD_TO_CHAT` must not gain
    `systemMessageOverride(...)` behavior here.
  - Do not add `LiveChatSession` or transcript persistence for user
    system-message state in this task.
  - Keep the public scripting surface on `AiRequestOptions`; do not
    add a second `askAi(...)` overload with a separate system-message
    parameter.
  - Keep normal visible chat behavior property-driven: requests without
    override continue using the current composed system message.
- **Briefing:**
  The public request contract lives in `freeplane_api` under
  `org.freeplane.api.ai`. Groovy scripts call it through
  `ControllerProxy` in `freeplane_plugin_script`. Script requests are
  normalized by `ScriptAiRequestService` into `ResolvedAiRequest`, then
  routed through `AIChatPanel` and `ChatPromptRunner` to a
  request-scoped `AIChatService`. `AIChatService` builds the effective
  system message through `AIToolSet.systemMessageForChat(...)`, which
  delegates to `MessageBuilder`; attached code guidance is appended in
  `AIChatService` when `AiCodeToolSet` is present. Hidden requests do
  not create visible chat state; visible requests do.
- **Research:**
  ```plantuml
  @startuml
  autonumber
  actor Script
  participant ControllerProxy
  participant ScriptAiRequestService
  participant AIChatPanel
  participant ChatPromptRunner
  participant AIChatService
  participant AIToolSet
  participant MessageBuilder

  Script -> ControllerProxy : askAi(prompt, options, callback)
  ControllerProxy -> ScriptAiRequestService : askAi(prompt, options, wrappedCallback)
  ScriptAiRequestService -> AIChatPanel : askAi(ResolvedAiRequest)
  AIChatPanel -> ChatPromptRunner : start or submit request
  ChatPromptRunner -> AIChatService : create request-scoped service
  AIChatService -> AIToolSet : systemMessageForChat(input, availability)
  AIToolSet -> MessageBuilder : buildForChat(availability)
  MessageBuilder --> AIToolSet : configured ai_system_message + built-in guidance
  AIToolSet --> AIChatService : base system message
  AIChatService --> ChatPromptRunner : system message provider for request
  @enduml
  ```

  - `AiRequestOptions` currently carries `timeout`, `mode`,
    `modelSelection`, `toolAvailability`, and `selectionOverride`, but
    no request-scoped system-message field.
  - `ScriptAiRequestService.askAi(...)` and
    `runAiPrompt(..., AiRequestOptions, ...)` both resolve their input
    into `ResolvedAiRequest`, so a new request-scoped system-message
    field must be threaded through both paths.
  - `ResolvedAiRequest` currently carries prompt text, prompt display
    name, timeout, mode, model selection, tool availability, and
    selection override only.
  - `AIChatService.systemMessageProvider(...)` currently starts from
    `AIToolSet.systemMessageForChat(...)` and then appends
    `AiCodeToolSet.systemMessageForChat(...)` when code-host guidance
    is available.
  - Hidden requests do not need visible-chat persistence. Visible
    requests do, because they interact with chat restore, selected-chat
    state, and `ADD_TO_CHAT` reuse.
  - For normal composed requests, `MessageBuilder` reads
    `ai_system_message` from `ResourceController` during request-time
    system-message construction rather than from `LiveChatSession`, so
    later requests without per-request override use the current
    property value.
  - The disabled-tools persona sentence
    `Answer as a general-purpose assistant.` has already been removed
    separately and is not part of this backlog task anymore.
- **Analysis:**
  - Open questions:
    - Should `systemMessageOverride(...)` be available for non-hidden
      chats?
    - If visible-chat support is reconsidered later, what should
      `ADD_TO_CHAT` compare when deciding whether to reuse the current
      chat or start a new one?
    - If visible-chat support is reconsidered later, should a visible
      request without `systemMessageOverride(...)` always keep the
      current chat's user system message, or should some other rule
      apply?
    - If visible-chat support is reconsidered later, how should an
      explicit user system message persist across transcript restore,
      selected-chat changes, and later user edits?
    - If visible-chat support is reconsidered later, should a new
      visible chat without override snapshot the current
      `ai_system_message` property or keep resolving it dynamically on
      later requests?
    - If visible-chat support is reconsidered later, what exact public
      rejection contract should visible-mode overrides use before a
      visible-chat system-message model exists?
  - The following analysis and resulting design assume visible chat
    system messages are not overridden.
  - Final decisions:
    - Limit the planned feature to hidden effective modes because the
      first reported use case works there and hidden requests avoid the
      unresolved visible-chat persistence and UX model.
    - Add the new control to `AiRequestOptions` because system-message
      choice is request metadata alongside mode, model selection, tool
      availability, and selection override.
    - Use the public builder method name
      `systemMessageOverride(String)` because it describes request-
      scoped replacement semantics without implying a global
      configuration mutation.
    - Treat any non-`null` override as the exact system message,
      including the empty string, because that single mechanism covers
      both explicit replacement text and the request-scoped "no system
      message" case.
- **Design:**
  ```plantuml
  @startuml
  set separator none
  package "org.freeplane.api.ai" {
    class AiRequestOptions {
      - timeout : Duration
      - mode : AiRequestMode
      - modelSelection : AiModelSelection
      - toolAvailability : AiToolAvailability
      - selectionOverride : AiSelectionOverride
      - systemMessageOverride : String
      + getSystemMessageOverride() : String
      + builder() : Builder
    }
    class Builder {
      + timeout(Duration) : Builder
      + mode(AiRequestMode) : Builder
      + modelSelection(AiModelSelection) : Builder
      + toolAvailability(AiToolAvailability) : Builder
      + selectionOverride(AiSelectionOverride) : Builder
      + systemMessageOverride(String) : Builder
      + build() : AiRequestOptions
    }
    AiRequestOptions +--> Builder
  }

  package "org.freeplane.plugin.ai.chat.request" {
    class ResolvedAiRequest {
      - promptText : String
      - promptDisplayName : String
      - timeout : Duration
      - mode : AiRequestMode
      - modelSelection : AiModelSelection
      - toolAvailability : AiToolAvailability
      - selectionOverride : AiSelectionOverride
      - systemMessageOverride : String
      + getSystemMessageOverride() : String
    }
    class ScriptAiRequestService {
      + askAi(String, AiRequestOptions, AiRequestCallback) : AiRequestHandle
      + runAiPrompt(String, AiRequestOptions, AiRequestCallback) : AiRequestHandle
    }
    class AIChatService {
      - systemMessageOverride : String
      + systemMessageProvider(ToolAvailabilityLevel) : Function<Object, String>
    }
    class AIChatServiceFactory {
      + createService(..., systemMessageOverride : String) : AIChatService
    }
    class ChatPromptRunner {
      - createPromptChatService(..., systemMessageOverride : String) : AIChatService
    }
  }

  package "org.freeplane.plugin.ai.chat.ui" {
    class AIChatPanel {
      + startShownAiRequest(ResolvedAiRequest, ...) : void
      + startAddToChatAiRequestAtDispatch(ResolvedAiRequest, ...) : ChatRequestFlow
      + startHiddenAiRequest(ResolvedAiRequest, ...) : void
      - createVisibleRequestService(..., systemMessageOverride : String) : AIChatService
    }
  }

  ScriptAiRequestService ..> AiRequestOptions : reads override
  ScriptAiRequestService ..> ResolvedAiRequest : populates override
  AIChatPanel ..> ChatPromptRunner : shown or hidden request service
  ChatPromptRunner ..> AIChatServiceFactory : hidden prompt service
  @enduml
  ```

  - Public API:
    - Extend `AiRequestOptions` with nullable
      `systemMessageOverride` state, `getSystemMessageOverride()`, and
      `Builder.systemMessageOverride(String)`.
    - Preserve the provided override text exactly. `null` means
      "compose the normal system message"; any non-`null` value,
      including `""`, means "use this exact system message".
  - Request normalization:
    - Extend `ResolvedAiRequest` with nullable
      `systemMessageOverride` state and a getter.
    - In `ScriptAiRequestService`, copy the override from
      `AiRequestOptions` into every `ResolvedAiRequest` created by raw
      `askAi(...)` and by `runAiPrompt(..., AiRequestOptions, ...)`.
  - Hidden-only support:
    - Apply the override only when the effective request mode is
      hidden.
    - Visible modes `SHOW_IN_CHAT` and `ADD_TO_CHAT` must not execute
      with a non-`null` override.
    - The exact public rejection type and detail text remain one of
      the backlog open questions for this task.
  - System-message composition:
    - Thread the request's nullable `systemMessageOverride` into the
      request-scoped `AIChatService` used for hidden execution.
    - In `AIChatService.systemMessageProvider(...)`, if the stored
      override is non-`null`, return it directly and skip both
      `AIToolSet.systemMessageForChat(...)` and
      `AiCodeToolSet.systemMessageForChat(...)`.
    - When the override is `null`, keep the existing composition path
      and availability-based tool filtering.
    - Keep tool authorization and tool exposure driven solely by
      `AiToolAvailability` / `ToolAvailabilityLevel`; the override only
      changes prompt text.
  - Out of scope:
    - No `LiveChatSession` or `ChatTranscriptRecord` changes in this
      task.
    - No visible-chat persistence, restore, selected-chat, or
      `ADD_TO_CHAT` reuse model work in this task.
- **Test specification:**
  - Automated tests:
    - Extend `freeplane_api` `AiRequestOptionsTest` to verify the new
      builder field defaults to `null`, exposes explicit values, and
      preserves an empty-string exact override.
    - Extend `freeplane_plugin_ai`
      `ScriptAiRequestServiceTest` to verify raw `askAi(...)` and
      `runAiPrompt(..., AiRequestOptions, ...)` copy
      `systemMessageOverride` into `ResolvedAiRequest`.
    - Extend `freeplane_plugin_ai` `AIChatServiceTest` to verify a
      non-`null` override makes `systemMessageProvider(...)` return the
      exact override and bypass both `AIToolSet` and `AiCodeToolSet`
      guidance composition.
    - Add request-validation tests for visible effective modes once the
      rejection contract is chosen.
  - Manual tests:
    - Run a Groovy script that calls `c.askAi(...)` with
      `AiToolAvailability.DISABLED`, `AiRequestMode.HIDDEN` or
      `AiRequestMode.HIDDEN_WITH_CANCEL_DIALOG`, and
      `AiRequestOptions.systemMessageOverride("You are an expert
      editor...")`; confirm the outgoing provider request contains
      exactly that system message and no appended built-in guidance.
    - Run the same hidden script without `systemMessageOverride(...)`;
      confirm the outgoing provider request uses the normal composed
      system message.
    - Run a visible script request with `systemMessageOverride(...)`
      after the rejection contract is chosen; confirm Freeplane rejects
      it before any visible-chat state is created or changed.
