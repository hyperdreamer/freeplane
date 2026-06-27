# Task: Add AI model configuration parameters
- **Task Identifier:** 2026-06-27-model-parameters
- **Scope:**
  Replace direct model-selection request plumbing with model
  configuration objects that contain model selection, thinking effort,
  and temperature. The task covers global default options, saved prompts,
  assistant profiles, visible-chat session metadata, transcript
  metadata, provider request mapping, public script request options, and
  a current-chat thinking effort switch. It does not add a standalone
  OpenAI provider.
- **Motivation:**
  Existing AI requests select a model and tool availability but cannot
  control common generation parameters. Thinking controls differ by
  provider family, while temperature is exposed by the current
  LangChain4j builders. Model selection and model parameters should be
  carried through one request configuration concept instead of parallel
  channels.
- **Scenario:**
  A user sets a global AI temperature and leaves thinking effort at the
  global default `medium`. Normal chat requests use the selected model,
  global temperature, and `medium` thinking effort.

  A saved prompt can choose a specific model, thinking effort, and
  temperature. Running that prompt uses its model configuration for the
  request. If the prompt opens a shown chat, follow-up messages in that
  chat keep using the prompt's explicit model-configuration values until
  chat metadata says otherwise.

  An assistant profile can define optional model-configuration values
  for thinking effort and temperature. When that profile is selected for
  a chat request, its explicit values participate in request-resolution
  together with global defaults and any stronger request or prompt value.

  In a visible chat, the user can switch the global thinking-effort
  preference with a drop-down selector using the same preference-backed
  behavior as the model selector.
- **Glossary:**

  ```mermaid
  graph TD
    GlobalDefault[Global defaults] -->|fills unset fields| EffectiveConfig[Effective configuration]
    PromptConfig[Prompt configuration] -->|can override| EffectiveConfig
    ProfileConfig[Profile configuration] -->|can override selected fields| EffectiveConfig
    ChatConfig[Current chat configuration] -->|can override where allowed| EffectiveConfig
    EffectiveConfig -->|is mapped to| ProviderRequest[Provider request]
  ```

  - Model configuration: the AI request configuration that contains
    model selection and optional model parameters.
    - It contains model selection, thinking effort, and temperature.
    - Each field inherits independently when unset.
  - Model selection: the provider/model choice currently represented by
    `AIModelSelection` internally and `AiModelSelection` in the public
    API.
    - It becomes a contained value inside model configuration.
    - Request boundaries use model configuration instead of direct model
      selection.
  - Thinking effort: a Freeplane-owned model-configuration field with
    the explicit values `max`, `xhigh`, `high`, `medium`, `low`,
    `minimal`, and `none`.
    - `none` is an explicit value, not the same as an inherited or blank
      value.
    - Provider mapping may collapse values when a provider exposes fewer
      levels.
  - Temperature: an optional numeric model-configuration field passed to
    provider builders when configured.
    - It is parsed as an optional `Double` value.
    - Blank temperature means the field is unset.
  - Inherited field: a model-configuration field that is not provided at
    the current request, prompt, profile, or chat layer.
    - Blank/null values mean the specific field is inherited.
    - There is no aggregate empty/inherit state for the full model
      configuration.
  - Provider mapping: the Freeplane conversion from effective model
    configuration to provider-specific LangChain4j builder calls.
    - OpenRouter uses the OpenAI-compatible builder path.
    - Gemini uses Gemini thinking configuration for thinking and the
      Gemini chat builder for temperature.
    - Ollama receives temperature and maps explicit named thinking
      effort to its boolean thinking flag in this increment.
- **Constraints:**
  - Use Freeplane-owned thinking-effort types and values. Do not use
    `GeminiThinkingConfig.GeminiThinkingLevel` as the shared UI,
    storage, or API type.
  - Replace direct request-boundary use of `AIModelSelection` and public
    `AiModelSelection` with model configuration. `AIModelSelection` and
    public `AiModelSelection` may remain only as contained sub-values or
    parsing helpers.
  - Do not add aggregate `isEmpty`, `hasAnything`, `inherit`, `unset`,
    or similar full-object state APIs to model configuration. Each field
    inherits independently.
  - The global thinking-effort preference has no inherited state; its
    default value is `medium`.
  - Preserve current provider behavior only when no resolved thinking
    effort exists. In particular, the existing Gemini 3 automatic
    thinking setup remains the inherited behavior for unset non-global
    configuration layers before the global default is applied.
  - Explicit Gemini thinking effort uses `GeminiThinkingConfig`.
    `max`, `xhigh`, and `high` map to Gemini `HIGH`; `medium`, `low`,
    and `minimal` map directly; `none` suppresses Gemini thinking setup
    for that request.
  - OpenRouter passes explicit thinking effort as the lowercase string
    accepted by the OpenAI-compatible builder path.
  - Ollama applies explicit temperature. Explicit `none` maps to
    `think(false)` and every other explicit thinking effort maps to
    `think(true)`; inherited thinking leaves the flag unset.
  - Existing prompt JSON with `modelSelectionValue` must be recovered at
    read time into the new prompt `modelConfiguration.modelSelection`.
    New writes use only the new model-configuration shape.
  - Existing profile JSON without model-configuration fields must load
    with every model-configuration field unset. New writes use only the
    new model-configuration shape.
  - Existing transcript JSON with `selectedModelOverride` must be
    recovered at read time into the new transcript/session model
    configuration. New writes use only the new model-configuration
    shape.
  - Current-chat UI scope for this task is thinking effort only.
    Temperature is configurable through global defaults, prompts, and
    profiles, not through the current-chat control.
  - The accepted thinking-effort strings are `max`, `xhigh`, `high`,
    `medium`, `low`, `minimal`, and `none`.
  - Temperature parsing must fail safely with a configuration error or
    unset field according to context; it must not crash the UI while
    loading persisted prompt or profile JSON.
  - Translation files edited for new UI labels must remain ASCII escaped
    and must be followed by the repository translation formatting gate.
- **Briefing:**
  AI provider configuration and provider construction live in
  `org.freeplane.plugin.ai.model`. `AIChatModelFactory` builds
  `OpenAiChatModel`, `GoogleAiGeminiChatModel`, and `OllamaChatModel`.
  The plugin currently stores global AI defaults in
  `freeplane_plugin_ai/src/main/resources/org/freeplane/plugin/ai/defaults.properties`
  and exposes them through
  `freeplane_plugin_ai/src/main/resources/org/freeplane/plugin/ai/preferences.xml`.

  Saved prompt data lives in `AiPrompt`, `AiPromptStore`,
  `AiPromptActionRegistry`, and `AiPromptManagerDialog`. Prompt-created
  visible chats already have selected-model and tool-availability
  session override patterns through `LiveChatSession`,
  `LiveChatController`, and `ChatTranscriptRecord`.

  Assistant profile data lives in `AssistantProfile`,
  `AssistantProfileStore`, `AssistantProfileSelectionSync`, and
  `AssistantProfileManagerDialog`. Profile switches are represented in
  chat memory by `AssistantProfileSwitchMessage` and in transcript JSON
  by `AssistantProfileTranscriptEntry`.

  Public script-facing request options live under
  `freeplane_api/src/main/java/org/freeplane/api/ai`. The current public
  request API exposes `AiModelSelection` directly through
  `AiRequestOptions`; this task replaces that boundary with public
  `AiModelConfiguration`.
- **Research:**

  ```plantuml
  @startuml
  set separator none
  package "org.freeplane.plugin.ai" {
    package "model" {
      class AIProviderConfiguration {
        +getSelectedModelValue()
        +getOpenrouterServiceAddress()
        +getOpenRouterKey()
        +getGeminiServiceAddress()
        +getGeminiKey()
        +getOllamaRequestHeaders()
      }
      class AIChatModelFactory {
        +createChatLanguageModel(AIProviderConfiguration)
      }
      class AIModelSelection {
        +fromSelectionValue(String)
        +createSelectionValue(String, String)
      }
    }
    package "api.ai" {
      class AiRequestOptions {
        +getModelSelection()
      }
      class AiModelSelection {
        +defaultModel()
        +explicit(String, String)
      }
    }
    package "prompt" {
      class AiPrompt {
        -name
        -prompt
        -showInChat
        -modelSelectionValue
        -toolAvailabilitySelectionValue
      }
      class AiPromptStore
      class AiPromptActionRegistry
    }
    package "chat.profile" {
      class AssistantProfile {
        -id
        -name
        -prompt
      }
      class AssistantProfileSelectionSync {
        +pendingProfileMessageIfDifferent()
        +resolveRequestProfile(String, String)
      }
    }
    package "chat.session" {
      class LiveChatSession {
        -toolAvailabilityOverride
        -selectedModelOverride
      }
      class LiveChatController {
        +sessionToolAvailabilityOverride(LiveChatSessionId)
        +sessionSelectedModelOverride(LiveChatSessionId)
      }
    }
    package "chat.history" {
      class ChatTranscriptRecord {
        -selectedModelOverride
        -toolAvailabilityOverride
      }
      class AssistantProfileTranscriptEntry {
        -profileId
        -profileName
        -profileMessage
      }
    }
  }
  AIChatModelFactory --> AIProviderConfiguration : reads provider settings
  AiRequestOptions --> AiModelSelection : exposes direct model selection
  AiPromptActionRegistry --> AiPrompt : copies persisted prompt data
  AssistantProfileSelectionSync --> AssistantProfile : creates profile switch messages
  LiveChatController --> LiveChatSession : stores visible-session overrides
  LiveChatController --> ChatTranscriptRecord : persists session metadata
  @enduml
  ```

  ```plantuml
  @startuml
  actor User
  participant "AIChatPanel" as Panel
  participant "LiveChatController" as Sessions
  participant "AIChatServiceFactory" as ServiceFactory
  participant "AIChatModelFactory" as ModelFactory
  participant "Provider builder" as Builder

  User -> Panel : send visible chat message
  Panel -> Sessions : selectedModelOverride and toolAvailabilityOverride
  Panel -> ServiceFactory : createService(..., selectedModelOverride)
  ServiceFactory -> ModelFactory : createChatLanguageModel(configuration)
  ModelFactory -> Builder : model/key/retries/provider options
  @enduml
  ```

  - `AIChatModelFactory` currently sets OpenRouter base URL, API key,
    model, retries, and `parallelToolCalls(false)` on
    `OpenAiChatModel.builder()`.
  - LangChain4j 1.15.1 `OpenAiChatModelBuilder` exposes
    `temperature(Double)` and `reasoningEffort(String)`.
  - `AIChatModelFactory` currently sets Gemini API key, model, retries,
    and optional base URL. It enables Gemini thinking automatically only
    when `modelName` starts with `gemini-3-`.
  - LangChain4j 1.15.1 Gemini builders expose `temperature(Double)` as
    a chat model builder option. This confirms Gemini temperature is not
    part of `GeminiThinkingConfig`.
  - `GeminiThinkingConfig.GeminiThinkingLevel` exposes only `MINIMAL`,
    `LOW`, `MEDIUM`, and `HIGH`.
  - `OllamaChatModelBuilder` exposes `temperature(Double)` and
    `think(Boolean)` but not named thinking-effort levels.
  - Internal `AIModelSelection` is only provider/model selection and
    parses the current `provider|model` string.
  - Public `AiModelSelection` is only provider/model selection plus the
    current/default marker exposed through `AiRequestOptions`.
  - `AiPrompt` currently persists `modelSelectionValue`; this is the
    prompt read-time recovery source for model selection.
  - `AssistantProfile` currently persists only id, name, and prompt; old
    profiles have no model-configuration values to recover.
  - `LiveChatSession` and `ChatTranscriptRecord` currently persist
    `selectedModelOverride`; this is the transcript/session read-time
    recovery source for model selection.
- **Analysis:**
  - Use model configuration as the request-boundary type because model
    choice, thinking effort, and temperature are one request
    configuration concept.
  - Keep model selection as a contained sub-value because provider/model
    choice remains a distinct concept inside the broader configuration.
  - Use a Freeplane-owned thinking-effort value set because Gemini's
    enum is incomplete for OpenRouter/OpenAI-compatible values and must
    not leak into Freeplane storage and UI.
  - Keep blank/null separate from `none` because inherited provider
    behavior and explicit no-thinking behavior are different states.
  - Configure Gemini temperature through the Gemini chat model builder
    because LangChain4j exposes it outside `GeminiThinkingConfig`.
  - Map thinking effort by provider because provider families expose
    different controls and the shared Freeplane value set is broader
    than Gemini's enum.
  - Replace the public script request model-selection boundary now
    because direct public `AiModelSelection` usage would otherwise
    remain a parallel request channel.
  - Keep only read-time recovery for old prompt and transcript fields
    because user data must be preserved, while new writes should not
    keep the legacy format alive.
  - Exclude a new standalone OpenAI provider because the current code
    has no such provider and this task only needs the existing
    OpenRouter/OpenAI-compatible builder path.
  - Use subtask-local vertical increments because core model
    configuration, prompt persistence, and profile/current-chat behavior
    can be reviewed and tested separately.
- **Design:**

  ```plantuml
  @startuml
  set separator none
  package "org.freeplane.plugin.ai" {
    package "model" {
      class AIModelSelection {
        -final String providerName
        -final String modelName
        +fromSelectionValue(String)
        +createSelectionValue(String, String)
        +getProviderName()
        +getModelName()
      }
      class AIModelConfiguration {
        -final AIModelSelection modelSelection
        -final AiThinkingEffort thinkingEffort
        -final Double temperature
        +withFallback(AIModelConfiguration)
        +getModelSelection()
        +getThinkingEffort()
        +getTemperature()
      }
      class AIProviderConfiguration {
        +getDefaultModelConfiguration()
      }
      class AIChatModelFactory {
        +createChatLanguageModel(AIProviderConfiguration)
        +createChatLanguageModel(AIProviderConfiguration, AIModelConfiguration)
      }
      class GeminiThinkingEffortMapper {
        +toThinkingLevel(AiThinkingEffort)
      }
    }
    package "api.ai" {
      enum AiThinkingEffort {
        MAX
        XHIGH
        HIGH
        MEDIUM
        LOW
        MINIMAL
        NONE
        +fromPreferenceValue(String)
        +toOpenAiValue()
      }
      class AiModelSelection {
        +defaultModel()
        +explicit(String, String)
      }
      class AiModelConfiguration {
        -final AiModelSelection modelSelection
        -final AiThinkingEffort thinkingEffort
        -final Double temperature
        +builder()
        +getModelSelection()
        +getThinkingEffort()
        +getTemperature()
      }
      class AiRequestOptions {
        +getModelConfiguration()
      }
    }
    package "prompt" {
      class AiPrompt {
        -modelConfiguration
      }
    }
    package "chat.profile" {
      class AssistantProfile {
        -modelConfiguration
      }
    }
    package "chat.memory" {
      class AssistantProfileSwitchMessage {
        -modelConfiguration
      }
    }
    package "chat.session" {
      class LiveChatSession {
        -modelConfigurationOverride
      }
      class LiveChatController {
        +sessionModelConfigurationOverride(LiveChatSessionId)
        +setSessionModelConfigurationOverride(LiveChatSessionId, AIModelConfiguration)
      }
    }
    package "chat.history" {
      class ChatTranscriptRecord {
        -modelConfigurationOverride
      }
      class AssistantProfileTranscriptEntry {
        -modelConfiguration
      }
    }
  }
  AIModelConfiguration --> AIModelSelection : contains selected model
  AIModelConfiguration --> AiThinkingEffort : contains optional thinking
  AIChatModelFactory --> AIModelConfiguration : applies effective configuration
  AIProviderConfiguration --> AIModelConfiguration : supplies global defaults
  AiRequestOptions --> AiModelConfiguration : exposes request configuration
  AiPrompt --> AIModelConfiguration : persists prompt configuration
  AssistantProfile --> AIModelConfiguration : persists profile configuration
  AssistantProfileSwitchMessage --> AIModelConfiguration : preserves selected profile configuration
  LiveChatSession --> AIModelConfiguration : stores visible-session overrides
  LiveChatController --> ChatTranscriptRecord : persists visible-session overrides
  @enduml
  ```

  ```plantuml
  @startuml
  actor User
  participant "AIChatPanel" as Panel
  participant "Model configuration resolver" as Resolver
  participant "AIChatServiceFactory" as ServiceFactory
  participant "AIChatModelFactory" as ModelFactory
  participant "Provider builder" as Builder

  User -> Panel : send request
  Panel -> Resolver : prompt/profile/session/global configuration sources
  Resolver -> ServiceFactory : effective model configuration
  ServiceFactory -> ModelFactory : configuration and effective model configuration
  ModelFactory -> Builder : model, temperature, provider-specific thinking mapping
  @enduml
  ```

  `AIModelConfiguration` is an immutable internal value object with
  three independently optional fields: model selection, public
  `AiThinkingEffort`, and temperature. Public `AiModelConfiguration` is
  the corresponding script-facing value object. Neither type has
  aggregate empty/inherit semantics.
  `strongerConfiguration.withFallback(fallbackConfiguration)`
  returns a new value with the first non-null value per field.

  Prompt-specific request creation passes prompt model configuration as
  the strongest override. Profile-specific configuration is preserved in
  profile switch messages so restored chats use the historical profile
  configuration values. Visible-session metadata stores prompt-created
  chat configuration and current-chat thinking overrides.

  Read-time recovery is explicit and one-way. Prompt JSON
  `modelSelectionValue` becomes `modelConfiguration.modelSelection` when
  loaded. Transcript JSON `selectedModelOverride` becomes
  `modelConfigurationOverride.modelSelection` when loaded. Existing
  profile JSON without model configuration loads with all fields unset.
  Saving writes only model-configuration fields.

  Provider mapping is isolated in `AIChatModelFactory` and helper
  mappers. OpenRouter receives lowercase thinking strings and optional
  temperature through `OpenAiChatModelBuilder`. Gemini receives optional
  temperature through the Gemini builder. Gemini explicit non-`none`
  thinking creates a `GeminiThinkingConfig` with mapped thinking level,
  `includeThoughts(true)`, `returnThinking(true)`, and
  `sendThinking(true)`. Gemini explicit `none` suppresses thinking
  configuration for that request. With inherited thinking, the existing
  Gemini 3 automatic thinking branch remains active. Ollama receives
  optional temperature. For explicit thinking effort, Ollama receives
  `think(false)` for `none` and `think(true)` for every other value;
  inherited thinking leaves the flag unset.
- **Test specification:**
  - Automated tests:
    - Internal request creation and public script request options use
      model configuration instead of direct model selection.
    - Provider factory applies global temperature to OpenRouter, Gemini,
      and Ollama builders when configured.
    - Provider factory applies OpenRouter thinking effort as the
      expected lowercase string and keeps `parallelToolCalls(false)`.
    - Provider factory maps Gemini `max`, `xhigh`, and `high` to
      `HIGH`, maps direct Gemini levels to their matching enum values,
      and suppresses Gemini thinking setup for explicit `none`.
    - Provider factory preserves the inherited Gemini 3 automatic
      thinking behavior when no explicit thinking effort is configured.
    - Invalid or blank global model-configuration preferences resolve
      according to the approved safe behavior without crashing model
      selection or provider construction.
    - Prompt persistence recovers old `modelSelectionValue`, profile
      persistence accepts missing model configuration, transcript
      restore recovers old `selectedModelOverride`, and new writes omit
      the old fields.
  - Manual tests: N/A

## Subtask: Introduce model configuration and provider mappings
- **Status:** done
- **Scope:**
  Add internal and public model-configuration value objects, replace
  direct model-selection request boundaries, parse global default model
  configuration, and map effective configuration to provider builders.
  This subtask does not add prompt/profile/current-chat UI fields beyond
  global preferences.
- **Motivation:**
  Provider behavior and request-boundary shape must exist before saved
  prompts, profiles, or chat sessions can route the new configuration
  safely. A narrow core slice gives later subtasks one tested mapping
  path.
- **Scenario:**
  A user configures `ai_temperature=0.2` and leaves thinking effort at
  its default `medium`. A regular chat request sends the selected model,
  temperature, and `medium` thinking effort to the provider.

  A user configures `ai_thinking_effort=high`. OpenRouter receives
  `high`; Gemini receives Gemini `HIGH`; Ollama receives `think(true)`.
  If the user configures `none`, OpenRouter receives `none`, Gemini
  thinking setup is suppressed, and Ollama receives `think(false)`.
- **Constraints:**
  - Keep existing provider key, service-address, retry, header, and
    OpenRouter parallel-tool-call behavior unchanged.
  - Replace public `AiRequestOptions.modelSelection(...)` and
    `getModelSelection()` with model-configuration request API. Do not
    retain a legacy public model-selection request path.
  - Do not change prompt JSON, profile JSON, chat transcript JSON, or
    current-chat UI in this subtask.
  - Non-global request, prompt, profile, and chat values must represent
    per-field inheritance. The global thinking-effort preference has a
    concrete default of `medium`; global temperature remains optional.
  - Temperature is optional and is applied only when parsing produces a
    valid `Double`.
- **Briefing:**
  Work in `org.freeplane.plugin.ai.model` for internal value types,
  configuration, and provider mapping. Work in `org.freeplane.api.ai`
  for public request API value types. Preference metadata lives in
  `freeplane_plugin_ai/src/main/resources/org/freeplane/plugin/ai` and
  English labels/tooltips in `Resources_en.properties`; edited
  translations require the repository translation formatting gate.
- **Research:**
  See shared Research. Local current-state facts:
  - `AIProviderConfiguration` currently exposes model/provider
    selection, provider credentials, service addresses, and model lists.
  - `AIChatModelFactory.createChatLanguageModel(...)` has one public
    factory path and can preserve internal call shape by delegating it to
    a new overload with model configuration.
  - Public `AiRequestOptions.Builder` currently has
    `modelSelection(AiModelSelection)` and `AiRequestOptions` has
    `getModelSelection()`.
  - The main Freeplane preferences XML supports decimal `number` fields
    elsewhere, but optional unset values are simpler to preserve through
    string-backed parsing for model-configuration fields.
- **Analysis:**
  - Store global thinking effort as an enum-backed combo value because
    the global option should have a concrete default.
  - Store global temperature as an optional string parsed to `Double`
    because an empty value must mean provider default rather than zero.
  - Replace the public API boundary in this first subtask because later
    profile and prompt work should not be built on a legacy direct model
    selection request path.
- **Design:**
  Add internal `AIModelConfiguration` and a Gemini mapper in the model
  package. Keep `AIModelSelection` only as the contained provider/model
  sub-value and parsing utility. Use public `AiThinkingEffort` as the
  canonical thinking enum for UI, script API, and provider mapping. Add
  public `AiModelConfiguration` under the public API package. Replace
  public request-option model-selection accessors with model-configuration
  accessors.

  Add `AIProviderConfiguration.getDefaultModelConfiguration()` using
  existing selected-model storage plus new preference keys
  `ai_thinking_effort` and `ai_temperature`. `ai_thinking_effort` uses
  an enum-backed combo whose default is `MEDIUM`. Add preference labels
  and tooltips documenting provider-specific thinking mapping and
  optional temperature behavior.

  `AIChatModelFactory.createChatLanguageModel(configuration)` delegates
  to an overload accepting `AIModelConfiguration` and merges request
  configuration over `configuration.getDefaultModelConfiguration()`.
  Provider builders receive only effective non-null fields. Explicit
  Gemini thinking branches before the existing Gemini 3 inherited branch
  so `none` can suppress that branch.
- **Test specification:**
  - Automated tests:
    - Public request options accept and expose model configuration and no
      longer expose direct model-selection request accessors.
    - Script-facing public API exposes `AiThinkingEffort` and accepts it
      through `AiModelConfiguration.builder().thinkingEffort(...)`.
    - Blank, null, invalid, and legacy `inherit` thinking-effort
      preference values resolve to `MEDIUM`.
    - All accepted thinking-effort strings parse case-insensitively to
      the expected Freeplane enum values.
    - Temperature preference parses valid decimal strings and leaves
      blank values unset.
    - OpenRouter builder request parameters contain configured
      temperature and lowercase thinking effort while keeping existing
      retry and parallel-tool-call behavior.
    - Gemini builder contains configured temperature and mapped thinking
      configuration for explicit non-`none` efforts.
    - Gemini explicit `none` prevents the inherited Gemini 3 thinking
      setup from being applied.
    - Ollama builder contains configured temperature, leaves `think`
      unset for inherited thinking, maps explicit `none` to
      `think(false)`, and maps every other explicit thinking effort to
      `think(true)`.
  - Manual tests: N/A

## Subtask: Add current-chat thinking effort selector
- **Status:** done
- **Scope:**
  Add a visible-chat thinking-effort drop-down selector beside the model
  selector. The selector follows the model selector's preference-backed
  behavior: user selection writes the global thinking-effort preference
  and clears the active session thinking override.
- **Motivation:**
  Users need a fast thinking-effort control without opening Preferences.
  Matching the model selector avoids two adjacent selectors with
  different global/session semantics.
- **Scenario:**
  In a visible chat, the selector initially shows the session thinking
  override when one exists; otherwise it shows the global
  thinking-effort preference. The user chooses `low`; Freeplane writes
  `ai_thinking_effort=LOW`, clears the active session thinking override,
  and future requests use `low` unless a stronger request, prompt, or
  session metadata value overrides it.
- **Constraints:**
  - Use user-facing text `thinking effort` for the control.
  - Keep the model selector and thinking selector on one line.
  - Keep the thinking selector width constant.
  - Keep the model selector at least as wide as the thinking selector.
  - Do not include a `default` item in the thinking selector.
  - User selection mutates the global `ai_thinking_effort` preference,
    matching the model selector's global selected-model behavior.
  - Do not add current-chat temperature UI.
  - Transcript persistence for chat model configuration remains in the
    later persisted-metadata subtask.
- **Research:**
  - `ChatModelSelector` writes `ai_selected_model` on user selection,
    reads the property when no displayed session override exists, and
    suppresses property writes during programmatic selection changes.
  - `AIChatPanel` clears the active session selected-model override on
    explicit user model selection. The thinking selector should use the
    same global-property plus session-override pattern.
- **Design:**
  Add a small chat selector backed by public `AiThinkingEffort` values.
  Put the model selector and thinking selector in a one-line custom
  layout that keeps the thinking selector at its preferred width and
  never lays out the model selector narrower than the thinking selector.
  The selector keeps an optional displayed session override;
  when the override is absent, it displays the global preference value.
  User selection writes `ai_thinking_effort`, clears the displayed
  override, and notifies `AIChatPanel` so the active session override is
  cleared. Property changes reapply the displayed value without firing
  the user-selection path. Visible request service creation still merges
  request configuration over session configuration so prompt/session
  values remain stronger than the global selector value.
- **Test specification:**
  - Automated tests:
    - The selector maps explicit values to `AiThinkingEffort`, has no
      prefix/default item, keeps a constant size, writes
      `ai_thinking_effort` on user selection, and suppresses writes for
      programmatic display changes.
    - Live chat sessions store and clear the thinking-effort override.
    - Visible request service creation receives the live session
      thinking-effort override when no stronger request value exists.
  - Manual tests: N/A

## Subtask: Add prompt model configuration
- **Status:** done
- **Scope:**
  Replace prompt direct `modelSelectionValue` storage with nested model
  configuration, recover existing prompt model selection at read time,
  add optional model-selection, thinking-effort, and temperature controls
  to prompt UI, and route prompt model configuration through hidden
  prompt execution and shown prompt chat session metadata.
- **Motivation:**
  Prompts are reusable request presets. They need model configuration so
  a prompt can consistently request a specific model and generation
  style without changing global defaults.
- **Scenario:**
  A user has an existing saved prompt with `modelSelectionValue` set.
  Loading prompts recovers that selected model into the new prompt model
  configuration. Saving the prompt writes only the new
  `modelConfiguration` shape.

  A user saves a prompt with `thinking effort=low` and
  `temperature=0.1`. Running it hidden sends those values only for that
  request. Running it shown opens a prompt chat whose first request and
  follow-up requests keep the prompt values through session metadata and
  transcript restore.
- **Constraints:**
  - Existing prompt JSON with `modelSelectionValue` must load into the
    new model configuration. New prompt JSON must not write
    `modelSelectionValue`.
  - A prompt launch must not write global model-configuration defaults.
  - Prompt model and thinking controls must use the same optional-field
    semantics: the inherited/current option stores an unset field, and
    an explicit option stores only that prompt field.
  - Prompt explicit values are stronger than profile, current-chat, and
    global values for the prompt request.
  - A shown prompt chat persists explicit prompt model-configuration
    values as session metadata so follow-up messages and transcript
    restore keep using them.
- **Briefing:**
  Reuse the existing prompt model/tool override patterns in
  `AiPromptManagerDialog`, `AiPromptActionRegistry`, `AIChatPanel`,
  `ChatPromptRunner`, `LiveChatSession`, `LiveChatController`, and
  `ChatTranscriptRecord`.
- **Research:**
  See shared Research. Local current-state facts:
  - `AiPrompt.copy()` and prompt equality/dirty checks must be updated
    whenever persisted prompt fields change.
  - Shown prompt chats already pass selected model and tool override
    values into live session state.
  - Jackson can support one-way recovery by accepting the old
    `modelSelectionValue` field for input while omitting it from new
    output.
  - The chat selector layout exists in `ModelConfigurationSelectorLayout`:
    it keeps model and thinking selectors on one line, keeps the
    thinking selector at preferred width, and does not lay out the model
    selector narrower than the thinking selector.
  - Prompt model selection already has an inherited/current option. The
    prompt thinking selector should mirror that behavior instead of
    writing `ai_thinking_effort`.
- **Analysis:**
  - Recover old prompt model selection only at read time because user
    prompts are persisted data, while retaining old write fields would
    keep parallel formats alive.
- **Design:**
  Extend `AiPrompt` with a nested `modelConfiguration` value. Add a
  read-time recovery setter or alias for old `modelSelectionValue` that
  populates only `modelConfiguration.modelSelection`. Remove new writes
  of the old field.

  Add prompt-manager model and thinking controls using the same one-line
  selector layout used by visible chat. The model selector keeps its
  existing inherited/current option; the thinking selector adds a matching
  inherited/current option and explicit `AiThinkingEffort` values. Both
  selectors update the prompt draft only, not global preferences. Add a
  temperature control as a separate optional field.

  Hidden prompt creation passes prompt model configuration into service
  creation for that request. Shown prompt chat creation stores prompt
  model configuration in live-session metadata and transcript metadata,
  then visible follow-up requests use that session metadata.
- **Test specification:**
  - Automated tests:
    - Prompt store recovers old prompt JSON `modelSelectionValue` into
      `modelConfiguration.modelSelection`.
    - Prompt store writes new prompt JSON without `modelSelectionValue`.
    - Prompt manager dirty-state, save, save-as-new, delete, and draft
      restore include model configuration fields.
    - Prompt manager model and thinking selectors use the shared
      one-line layout and store inherited/current choices as unset
      model-configuration fields without writing global preferences.
    - Hidden prompt execution passes prompt model configuration to
      service/model creation without changing global defaults.
    - Shown prompt chats persist prompt model configuration in
      live-session and transcript metadata.
    - Restored prompt chats reuse stored prompt model configuration for
      follow-up requests.
  - Manual tests: N/A

## Subtask: Add profile configuration and persisted chat model metadata
- **Status:** done
- **Scope:**
  Add optional model configuration to assistant profiles, recover
  existing profiles with every field unset, preserve selected profile
  configuration in profile switch messages, replace session/transcript
  selected-model override metadata with model configuration, and recover
  existing transcript selected-model metadata.
- **Motivation:**
  Profiles define assistant behavior and should be able to carry model
  parameters. Existing visible-chat metadata must also move from direct
  selected-model override storage to model configuration so follow-up
  requests have one configuration channel.
- **Scenario:**
  An existing profile file loads successfully without model-configuration
  fields. The profile behaves as before until the user edits and saves
  model-configuration values.

  A user selects a profile with a specific model, `thinking effort=high`,
  and `temperature=0.4`; requests using that profile resolve those
  values unless a stronger prompt/request value applies. The profile
  switch is recorded so transcript restore keeps the parameter values
  active.

  A visible chat has a live `minimal` thinking-effort override. Saving
  and restoring that chat preserves the override through transcript
  model-configuration metadata.
- **Constraints:**
  - Existing profile JSON without model configuration loads with every
    field unset. New profile JSON writes the new model-configuration
    shape.
  - Profile switch transcript entries must preserve the configuration
    values that were active when the profile was selected, not depend on
    later edits to the saved profile file.
  - Existing transcript JSON with `selectedModelOverride` must recover
    that value into session model configuration. New transcript JSON must
    not write `selectedModelOverride`.
  - Profile model and thinking controls must use the same optional-field
    semantics: the inherited/current option stores an unset field, and
    an explicit option stores only that profile field.
  - Persisted current-chat thinking metadata must not write the global
    thinking-effort preference during transcript restore. If the user
    explicitly changes the visible chat thinking selector after restore,
    it follows model-selector behavior: write the global preference and
    clear the active session override.
  - Current-chat temperature UI remains out of scope.
  - If profile and current-chat thinking values both exist, the
    confirmed resolution order keeps the profile value stronger than the
    current-chat value.
- **Briefing:**
  Profile data and manager UI live in
  `org.freeplane.plugin.ai.chat.profile`. Profile switch persistence uses
  `AssistantProfileSwitchMessage`, `AssistantProfileTranscriptEntry`,
  and `TranscriptMemoryMapper`.
- **Research:**
  See shared Research. Local current-state facts:
  - `AssistantProfileManagerDialog` currently uses hardcoded English
    labels; this subtask can either follow the existing local style or
    improve it only where needed for the new controls.
  - `AssistantProfileSwitchMessage` currently stores only id, name, and
    profile message, so it cannot preserve historical profile
    configuration values yet.
  - `LiveChatSession` and `ChatTranscriptRecord` currently store
    `selectedModelOverride`; both must move to model configuration with
    read-time recovery.
  - The chat selector layout exists in `ModelConfigurationSelectorLayout`:
    it should also be used for profile model/thinking controls so the
    model selector is not laid out narrower than the thinking selector.
  - Profile model and thinking selectors should mirror the prompt/model
    optional-field behavior instead of writing global preferences.
- **Analysis:**
  - Store profile configuration values in profile switch messages because
    a restored transcript should not silently adopt later edits to the
    saved profile file.
  - Persist chat thinking overrides in session/transcript model
    metadata because live override selection should survive transcript
    restore.
  - Recover old transcript selected-model metadata at read time because
    shown prompt chats are persisted user data.
- **Design:**
  Extend `AssistantProfile` with nested `modelConfiguration`. Existing
  profile files missing the object load with all fields unset. Extend
  profile manager UI with model and thinking controls using the same
  one-line selector layout used by visible chat and prompt UI. The model
  selector has an inherited/current option and explicit models; the
  thinking selector has a matching inherited/current option and explicit
  `AiThinkingEffort` values. Both selectors update the profile draft
  only, not global preferences. Add a temperature control as a separate
  optional field.

  Extend `AssistantProfileSwitchMessage`,
  `AssistantProfileTranscriptEntry`, and `TranscriptMemoryMapper` so
  selected profile model configuration is stored in chat memory and
  transcripts. Request creation resolves prompt, profile, session, and
  global configuration using the confirmed precedence.

  Replace `selectedModelOverride` session and transcript metadata with
  `modelConfigurationOverride`. Existing transcript `selectedModelOverride`
  is accepted only as a read-time recovery source. Restored session model
  configuration acts as the displayed override for the visible chat
  model/thinking selectors. Explicit user changes in those visible-chat
  selectors write the corresponding global preference and clear the
  active session override.
- **Test specification:**
  - Automated tests:
    - Profile store loads old profile JSON with every model-configuration
      field unset.
    - Profile manager save and reload preserve model selection, thinking
      effort, and temperature values in model configuration.
    - Profile manager model and thinking selectors use the shared
      one-line layout and store inherited/current choices as unset
      model-configuration fields without writing global preferences.
    - Profile switch messages and transcript entries preserve selected
      profile model configuration values.
    - Transcript restore recovers old `selectedModelOverride` into
      session model configuration and new transcript writes omit
      `selectedModelOverride`.
    - Request-configuration resolution applies the confirmed order:
      prompt explicit field, then profile explicit field, then
      current-chat override field, then global default field, then
      provider default.
    - Persisted current-chat thinking override metadata restores into
      the active session thinking override field and does not write the
      global preference during restore.
    - Explicit visible-chat selector changes after restore clear the
      active session thinking override and write `ai_thinking_effort`.
    - Transcript restore preserves current-chat thinking override and
      profile-carried configuration values.
  - Manual tests: N/A
