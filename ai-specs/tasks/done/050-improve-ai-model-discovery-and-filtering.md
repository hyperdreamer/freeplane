# Task: Improve AI model discovery and filtering

- **Task Identifier:** 2026-07-11-model-filter
- **Scope:** Improve access to available AI models through searchable selectors,
  independent provider configurations, and automatic tool-capable model
  discovery that does not require maintained default lists. Deliver the work as
  independently releasable selector and provider-discovery increments.
- **Motivation:** Model catalogs change frequently and can contain enough entries
  that selecting a model by scanning the full list is inefficient. Curated
  defaults become obsolete, and one OpenRouter-specific configuration cannot
  represent simultaneous OpenAI, OpenRouter, Requesty, and custom services.

## Subtask: Filter AI model selectors

- **Status:** done
- **Scope:** Add literal case-insensitive substring filtering to chat, prompt,
  and assistant-profile model selectors, sharing one non-persisted filter for
  the Freeplane session. Replace the sole `JFilterableComboBox` use with a
  domain-specific `TagSelector`. Share AI selector mechanics through
  `AIModelSelector` while retaining separate chat and override policy owners.
  Keep provider catalog filtering and defaults unchanged.
- **Motivation:** Users need to narrow large changing model catalogs without
  transient search text becoming selected or persisted state. Domain-specific
  components keep stateful Swing listener behavior out of dialogs and table
  construction without merging incompatible tag and model semantics.
- **Scenario:** A closed selector displays its selected model, translated “no
  model selected” for an empty selection, or translated “unknown” for an
  unavailable chat selection. Explicit popup opening restores and selects the
  session filter. Direct editor changes start filtering and, after 200 ms of
  inactivity, retain models whose complete provider-and-model display name
  contains the literal text ignoring case. “Use current model” remains visible
  in prompt and profile selectors. Up and Down highlight filtered rows without
  selecting; Enter or a mouse choice commits. Canceling restores the prior
  selected display while retaining the shared filter. Missing or unavailable
  models disable chat sending without disabling draft editing, and visible chat
  or prompt submission stops before request construction with a translated
  modal error owned by the action source.
- **Constraints:**
  - Filter text remains application-session state and is never persisted.
  - Whitespace and punctuation participate literally; input is not trimmed,
    tokenized, or interpreted as a pattern.
  - Typing, rebuilding, and Up/Down highlighting never change selected,
    persisted, prompt, profile, or session model state.
  - Only Enter or a mouse choice commits a highlighted model.
  - One non-repeating 200 ms Swing timer restarts after each editor change.
    Filtering mutates one `DefaultComboBoxModel` after document notification;
    it never replaces the model during notification.
  - Tag editing retains empty-input, contains-match, exact-match, and unmatched
    new-tag creation behavior.
- **Briefing:** The implementation spans `freeplane` tag editing and
  `freeplane_plugin_ai` selector, chat, prompt, and profile UI. Swing editor,
  popup, item, and document events can recurse, so programmatic transitions are
  guarded and remain distinguishable from explicit user selections.
- **Research:** `JFilterableComboBox` was editable only through `TagEditor` and
  combined tag-specific filtering with listener lifecycle. Chat persistence and
  shortened rendering already differed from prompt/profile “use current model”
  semantics. Existing tests covered model-list construction and propagation but
  not editable filtering, popup lifecycle, or keyboard highlighting.
- **Analysis:**
  - Keep selected descriptors as normal combo-box values so Swing owns closed
    display preparation.
  - Share filter text independently from selected values so selection does not
    destroy search state.
  - Filter full display names so provider and model text are searchable.
  - Keep “use current model” outside filtering because it is a configuration
    action, not a model.
  - Use separate `TagSelector` and `AIModelSelector` components because their
    matching, selection, and side-effect contracts differ.
  - Keep `ChatModelSelector` and `AIModelOverrideSelector` as policy owners
    because persistence and override semantics differ.
- **Design:**

  ```plantuml
  @startuml
  set separator none
  package "Freeplane" {
    package "freeplane.features.icon.mindmapmode" {
      class TagEditor
      class TagSelector {
        - comboBox
        - filterTimer
        + getComboBox()
      }
    }
    package "org.freeplane.plugin.ai" {
      package "model.ui" {
        class AIModelFilterState {
          - filterText
          + {static} shared()
        }
        class AIModelSelector {
          - modelSelectionModel
          - filterUpdateTimer
          + setAvailableModelDescriptors(descriptors, selectionValue)
          + setSelectedModelSelectionValue(selectionValue)
          + hasAvailableModelSelection(selectionValue)
          + setExplicitModelSelectionListener(listener)
        }
      }
      package "chat.ui" {
        class ChatModelSelector {
          + hasAvailableSelectedModel()
          + hasAvailableModelSelection(selectionValue)
        }
        class ChatInputControls {
          + update(requestActive, providerConfigured, selectedModelAvailable)
        }
        class AIChatPanel {
          + runPrompt(prompt, owner)
          + submitMessageToSession(sessionId, message)
        }
      }
      package "prompt.ui" {
        class AIModelOverrideSelector
      }
    }
    package "javax.swing" {
      class JComboBox
    }
  }
  TagEditor --> TagSelector
  TagSelector --> JComboBox
  ChatModelSelector --> AIModelSelector
  AIModelOverrideSelector --> AIModelSelector
  AIModelSelector --> AIModelFilterState
  AIModelSelector --> JComboBox
  AIChatPanel --> ChatModelSelector
  AIChatPanel --> ChatInputControls
  @enduml
  ```

  `TagSelector` owns tag-field listeners, filtering, focus behavior, rendering,
  and exact-match selection; `TagEditor` retains table commit and new-tag
  creation. The obsolete `JFilterableComboBox` is removed.

  `AIModelSelector` owns one editable combo box, one stable model, the complete
  sorted descriptor list, popup/editor/key listeners, the timer, selected
  descriptor, unavailable descriptor presentation, and recursion guards.
  `AIModelFilterState.shared()` owns session filter text and remains injectable
  for tests. Programmatic updates never invoke the explicit-selection callback.

  ```plantuml
  @startuml
  actor User
  participant "Model editor" as Editor
  participant AIModelSelector as Selector
  participant AIModelFilterState as State
  participant "200 ms Swing Timer" as Timer
  participant "Popup JList" as Popup
  participant "ChatModelSelector or AIModelOverrideSelector" as Owner
  User -> Selector : explicitly open popup
  Selector -> State : read retained filter
  Selector -> Editor : restore and select filter
  User -> Editor : edit text
  Editor -> State : store filter
  Editor -> Timer : restart
  Timer -> Selector : apply latest contains filter
  User -> Popup : Up or Down
  Popup -> Popup : move highlight only
  alt Enter or mouse choice
    User -> Selector : commit highlighted descriptor
    Selector -> Owner : explicit selection
  else cancel
    Selector -> Editor : restore selected display
  end
  @enduml
  ```

  Popup closure cancels the timer, restores the complete list and selected
  descriptor, and lets Swing prepare the closed editor value. Normal display
  uses no focus listener, hierarchy listener, or delayed `invokeLater`
  restoration. Prompt/profile selectors prepend “use current model” after every
  rebuild. Chat uses shortened closed rendering; unavailable popup rows retain
  full provider/model identity.

  `ChatModelSelector` retains global persistence, session display overrides,
  legacy-selection migration, and availability classification.
  `AIModelOverrideSelector` retains empty-value “use current model” semantics and
  prompt/profile notification. `AIChatPanel` and `ChatInputControls` own request
  policy: unavailable selections preserve drafts, disable sending, and stop all
  visible-message or prompt paths before chat creation, history mutation, or
  request construction. `RunAiPromptAction` supplies its event-source component
  to the translated modal error dialog. Script/API request semantics remain
  unchanged.
- **Test specification:**
  - **Automated tests:**
    - `AIModelSelectorTest`
      - `normalState_displaysSelectedModel`: closed state uses the selected
        descriptor and Swing editor preparation.
      - `normalState_withoutSelectionDisplaysTranslatedPlaceholder`: empty
        selection displays the placeholder without a synthetic selection.
      - `openingPopup_restoresAndSelectsSharedFilter`: explicit opening restores
        selected filter text.
      - `typingInNormalEditor_startsFiltering`: direct editing retains the model
        instance and applies only the pending 200 ms update.
      - `filtering_matchesCompleteDisplayNameIgnoringCase`: literal filtering
        covers empty, matching, and no-match cases.
      - `filtering_preservesAlwaysVisibleOptions`: rebuilds retain supplied
        options independently of display text.
      - `closingPopup_restoresSelectedModelWithoutChangingFilter`: closure emits
        no explicit callback and preserves filter state.
      - `instances_shareInjectedFilterState`: separate selectors share injected
        session state.
      - `arrowKeysHighlightEveryFilteredRowAndEnterCommitsOnlyHighlightedModel`:
        Up/Down change only row highlight; Enter commits the final highlight.
      - `explicitFilteredSelection_notifiesOwnerAndRetainsFilter`: a committed
        choice notifies once without clearing the filter.
    - `ChatModelSelectorTest`
      - `filteredSelection_persistsModelAndRetainsSessionOverrideContract`:
        explicit selection preserves persistence and notification semantics.
      - `selectedModelDisplay_remainsShortened`: closed and popup rendering keep
        their distinct formats.
      - `renderer_showsUnknown_forSelectedUnavailableValueAndFullNameInDropdown`:
        unavailable presentation preserves full popup identity.
      - `hasAvailableSelectedModel_rejectsUnavailableSelection`: unknown or
        absent selections are unavailable.
      - `hasAvailableSelectedModel_acceptsCatalogSelection`: catalog selections
        are available.
      - `hasAvailableModelSelection_resolvesCurrentAndExplicitValuesAgainstCatalog`:
        prompt selections resolve against the loaded catalog.
      - `setDisplayedSelectionValueOverride_showsOverrideWithoutPersistingConfiguration`:
        session display overrides remain non-persistent.
    - `ChatInputControlsTest`
      - `unavailableSelectedModel_disablesSendingButKeepsInputEditable`: draft
        editing remains enabled while sending is disabled.
      - `availableSelectedModel_reenablesSending`: an available choice restores
        sending.
    - `AiPromptActionRegistryTest`
      - `promptAction_passesEventSourceComponentAsErrorDialogOwner`: popup-menu
        actions supply a visible modal owner.
    - `AIChatPanelScriptRequestTest`
      - `sendMessageStopsBeforeRequestConstructionForUnavailableModel`: visible
        chat preserves its draft and performs no request construction.
      - `runPromptUsingMissingCurrentModelStopsBeforeRequestConstruction`: a
        missing current model reports the translated modal error.
      - `runPromptStopsBeforeRequestConstructionForUnavailableExplicitModel`: an
        unavailable explicit prompt model stops shown and hidden execution.
    - `AiRequestConfigurationResolverTest`
      - `reportsMissingSelectedModelAsConfigurationError`: missing effective
        selection returns the translated configuration error.
    - `AIModelOverrideSelectorTest`
      - `construction_suppliesUseCurrentModelAsAlwaysVisibleOption`: the
        synthetic option remains visible while filtering.
      - `explicitSelection_notifiesPromptOrProfileWithSelectionValue`: committed
        model and current-model choices preserve external values.
      - `programmaticSelection_doesNotNotifyOwner`: restoration has no owner
        side effect.
    - `TagSelectorTest`
      - `editableTagComboBox_preservesFilteringBehavior`: delayed filtering,
        exact selection, and unmatched input remain intact.
    - `TagEditorTest`
      - `tagSelector_preservesNewTagCommitBehavior`: table commit selects an
        existing tag or creates an unmatched tag.

## Subtask: Discover tool-capable models across providers

- **Status:** done
- **Scope:** Add independent OpenAI, OpenRouter, Requesty, and Custom
  OpenAI-compatible provider configurations alongside Gemini and Ollama. Let
  every configured provider contribute tool-capable text models concurrently.
  Give every provider a new model-list property whose empty default enables
  automatic discovery and whose literal entries provide an explicit trusted
  list. Migrate non-default values from obsolete model-list properties and
  remove those properties. Keep assistant-profile tool availability outside
  this increment.
- **Motivation:** Provider catalogs change without Freeplane releases, while
  maintained literal defaults hide new models. Users also need OpenAI,
  Requesty, and custom OpenAI-compatible endpoints without repurposing their
  OpenRouter configuration or credentials.
- **Scenario:** A user can configure any combination of OpenAI, OpenRouter,
  Requesty, Custom, Gemini, and Ollama. Each configured provider contributes
  independently identified models to the same selectors. An empty model field
  discovers every model confirmed to support text output and tools; a
  wildcard-only field filters that discovered set. A field containing any
  literal model ID instead supplies a trusted explicit list without discovery
  or metadata checks. A provider whose discovery fails contributes no automatic
  models, while failures or omissions in native capability metadata use exact
  OpenRouter metadata when possible. Existing selected models remain visible
  under the unavailable-selection behavior defined by the selector subtask.
- **Constraints:**
  - Provider identities, configuration properties, credentials, discovery
    results, and request routing remain independent; no provider-selection radio
    button or shared provider field is introduced.
  - OpenRouter capability matching is exact. Known provider prefixes may be
    added deterministically for OpenAI and Google models, but aliases, fuzzy
    matches, and inferred model-family matches are forbidden.
  - Automatic catalogs exclude models without confirmed tool support even when
    tools are Disabled because an existing transcript can contain tool calls
    and tool results.
  - Explicit literal entries are trusted user assertions of tool compatibility.
    Wildcards in a mixed literal-and-wildcard value do not add models.
  - A failed discovery refresh makes that provider unavailable; stale data must
    not be presented as current availability.
  - Custom authentication is limited to an optional API key sent as an
    `Authorization: Bearer` header.
  - Assistant-profile tool availability remains tracked by
    `ai-specs/tasks/backlog/001-add-tool-availability-to-assistant-profiles.md`.
- **Briefing:** `AIModelCatalog` currently owns OpenRouter and Ollama HTTP
  discovery, static 30-minute caches, wildcard filtering, literal-list fallback,
  and Gemini literal-list construction. `AIProviderConfiguration` reads the
  three provider configurations directly from `ResourceController`.
  `AIChatModelFactory` recognizes only `openrouter`, `gemini`, and `ollama`.
  Plugin defaults and Preferences fields live in `defaults.properties` and
  `preferences.xml`; API keys are secured in `Activator`. The target change
  separates reusable OpenAI-compatible HTTP mechanics from provider-specific
  capability policy and gives session cache state an explicit injectable owner.
- **Research:**

  ```plantuml
  @startuml
  set separator none
  package "Freeplane AI" {
    package "org.freeplane.plugin.ai.model" {
      class AIModelCatalog {
        - {static} cachedOpenrouterModels
        - {static} cachedOllamaModels
        + getAvailableModels(allowsRefresh)
        - fetchOpenrouterModels()
        - fetchOllamaModels()
        - getGeminiModelsFromList()
        - filterModelDescriptors(models, allowlist)
      }
      class AIProviderConfiguration {
        + getOpenrouterServiceAddress()
        + getOpenRouterKey()
        + getOpenrouterModelAllowlistValue()
        + getGeminiServiceAddress()
        + getGeminiKey()
        + getGeminiModelListValue()
        + getOllamaServiceAddress()
        + getOllamaModelAllowlistValue()
      }
      class AIChatModelFactory {
        + {static} createChatLanguageModel(configuration, requestConfiguration)
      }
      class AIModelDescriptor
    }
  }
  AIModelCatalog --> AIProviderConfiguration
  AIModelCatalog --> AIModelDescriptor
  AIChatModelFactory --> AIProviderConfiguration
  @enduml
  ```

  ```plantuml
  @startuml
  participant AIModelCatalog as Catalog
  participant AIProviderConfiguration as Configuration
  participant "OpenRouter /models" as OpenRouter
  participant "Ollama /api/tags" as Ollama
  Catalog -> Configuration : read configured keys, URLs, and lists
  Catalog -> OpenRouter : GET when OpenRouter key exists
  OpenRouter --> Catalog : data entries or empty failure result
  Catalog -> Catalog : filter with OpenRouter allowlist
  Catalog -> Catalog : parse configured Gemini literals
  Catalog -> Ollama : GET when Ollama URL exists
  Ollama --> Catalog : installed models or failed result
  Catalog -> Catalog : filter with Ollama allowlist
  @enduml
  ```

  Gemini `ListModels` returned 54 models, including 39 advertising
  `generateContent`, but reports no tool capability and includes TTS, image,
  music, robotics, computer-use, and deep-research models. OpenAI `ListModels`
  returned 125 account-visible IDs with only `id`, `object`, `created`, and
  `owned_by`. OpenRouter's unauthenticated catalog returned 345 models with
  `supported_parameters` and input/output modalities; exact matching covered 15
  of 30 directly discovered `gemini-*` generation IDs and 46 of 125 OpenAI IDs.
  Requesty's authenticated `/models` endpoint returned 580 `chat` models, 545
  with `supports_tool_calling=true`. Ollama `/api/show` reports local model
  capabilities including `completion` and `tools`, while `/api/tags` does not.

  OpenAI, OpenRouter, and Requesty use `GET {base URL}/models` and a top-level
  `data` array containing model `id` values. OpenRouter adds architecture and
  supported-parameter metadata; Requesty adds `api` and
  `supports_tool_calling`. The standard OpenAI response has neither extension.
- **Analysis:**
  - Keep provider configurations concurrent and independently persisted because
    a shared selection would prevent combinations and could send credentials to
    the wrong service.
  - Use exact direct-catalog and OpenRouter intersections for OpenAI and Gemini
    because direct discovery proves account visibility while OpenRouter supplies
    missing capability metadata.
  - Use provider-native metadata for OpenRouter and Requesty because their model
    responses explicitly report the capabilities needed by Freeplane.
  - Let Custom use recognized OpenRouter or Requesty metadata first and exact
    OpenRouter metadata second because custom OpenAI-compatible schemas are not
    uniform.
  - Keep explicit lists as trusted overrides because capable private, aliased,
    or newly released models may not have usable public metadata.
  - Use new property names instead of a migration marker so removal of each old
    key records completion without risking a later intentional value.
  - Reject known non-tool models because disabling new tools does not remove old
    tool-call messages from a transcript.
- **Design:**

  ```plantuml
  @startuml
  set separator none
  package "Freeplane AI" {
    package "org.freeplane" {
      package "plugin.ai.model" {
      enum OpenAICompatibleProvider {
        OPENAI
        OPENROUTER
        REQUESTY
        CUSTOM
        + getProviderName()
        + getDisplayName()
      }
      enum CapabilitySupport {
        SUPPORTED
        UNSUPPORTED
        UNKNOWN
      }
      enum AIModelListMode {
        AUTOMATIC
        EXPLICIT
      }
      class OpenAICompatibleProviderConfiguration {
        + provider : OpenAICompatibleProvider
        + serviceAddress : String
        + modelsAddress : String
        + apiKey : String
        + modelListConfiguration : AIModelListConfiguration
        + isConfigured() : boolean
      }
      class AIModelListConfiguration {
        + mode : AIModelListMode
        + literalModelNames : List~<String>
        + wildcardPatterns : List~<Pattern>
        + {static} parse(value : String) : AIModelListConfiguration
        + isExplicit() : boolean
        + accepts(modelName : String) : boolean
      }
      class AIModelCapabilities {
        + textOutput : CapabilitySupport
        + toolCalling : CapabilitySupport
        + isToolCapableTextModel() : boolean
      }
      class DiscoveredAIModel {
        + providerName : String
        + modelName : String
        + freeModel : boolean
        + capabilities : AIModelCapabilities
      }
      class OpenAIModelItem {
        + id : String
        + supportedParameters : List~<String>
        + outputModalities : List~<String>
        + api : String
        + supportsToolCalling : Boolean
      }
      class AIModelDiscoveryResult {
        + successful : boolean
        + models : List~<DiscoveredAIModel>
        + {static} success(models : List~<DiscoveredAIModel>) : AIModelDiscoveryResult
        + {static} failed() : AIModelDiscoveryResult
      }
      class AIModelCatalogCacheKey {
        + providerName : String
        + modelsAddress : String
        + metadataAddress : String
        + authenticationFingerprint : String
      }
      interface OpenAIModelMetadataInterpreter {
        + interpret(modelItem : OpenAIModelItem) : AIModelCapabilities
      }
      class OpenRouterModelMetadataInterpreter
      class RequestyModelMetadataInterpreter
      class OpenAICompatibleModelDiscovery {
        + discover(configuration : OpenAICompatibleProviderConfiguration) : AIModelDiscoveryResult
      }
      class OpenRouterModelMetadataCatalog {
        + find(providerQualifiedModelId : String) : OpenAIModelItem
      }
      class GeminiModelDiscovery {
        + discover(configuration : AIProviderConfiguration) : AIModelDiscoveryResult
      }
      class OllamaModelDiscovery {
        + discover(configuration : AIProviderConfiguration) : AIModelDiscoveryResult
      }
      class AIModelCatalogState {
        + {static} shared() : AIModelCatalogState
        + getFresh(cacheKey : AIModelCatalogCacheKey) : AIModelDiscoveryResult
        + recordSuccess(cacheKey : AIModelCatalogCacheKey, models : List~<DiscoveredAIModel>)
        + recordFailure(cacheKey : AIModelCatalogCacheKey)
      }
      class AIModelCatalog {
        + getAvailableModels(allowsRefresh : boolean) : List~<AIModelDescriptor>
      }
      class AIProviderConfiguration {
        + getOpenAICompatibleConfigurations() : List~<OpenAICompatibleProviderConfiguration>
        + isGeminiConfigured() : boolean
        + isOllamaConfigured() : boolean
      }
      class AIModelListPreferenceMigration {
        + migrate(resourceController : ResourceController)
      }
      class AIChatModelFactory {
        + {static} createChatLanguageModel(configuration : AIProviderConfiguration, requestConfiguration : AIModelConfiguration) : ChatModel
      }
      class AIModelDescriptor
      class AIModelConfiguration
      }
      package "core.resources" {
        class ResourceController
      }
    }
    package "dev.langchain4j.model.chat" {
      interface ChatModel
    }
  }
  OpenAICompatibleProviderConfiguration --> OpenAICompatibleProvider
  OpenAICompatibleProviderConfiguration --> AIModelListConfiguration
  AIModelListConfiguration --> AIModelListMode
  DiscoveredAIModel --> AIModelCapabilities
  AIModelCapabilities --> CapabilitySupport
  OpenAIModelMetadataInterpreter --> OpenAIModelItem
  OpenAIModelMetadataInterpreter <|.. OpenRouterModelMetadataInterpreter
  OpenAIModelMetadataInterpreter <|.. RequestyModelMetadataInterpreter
  OpenAICompatibleModelDiscovery --> OpenAIModelMetadataInterpreter
  OpenAICompatibleModelDiscovery --> AIModelDiscoveryResult
  OpenRouterModelMetadataCatalog --> OpenAIModelItem
  GeminiModelDiscovery --> AIModelDiscoveryResult
  OllamaModelDiscovery --> AIModelDiscoveryResult
  AIModelCatalogState --> AIModelCatalogCacheKey
  AIModelCatalog --> OpenAICompatibleModelDiscovery
  AIModelCatalog --> OpenRouterModelMetadataCatalog
  AIModelCatalog --> GeminiModelDiscovery
  AIModelCatalog --> OllamaModelDiscovery
  AIModelCatalog --> AIModelCatalogState
  AIModelCatalog --> AIProviderConfiguration
  AIModelCatalog --> AIModelDescriptor
  AIProviderConfiguration --> OpenAICompatibleProviderConfiguration
  AIModelListPreferenceMigration --> ResourceController
  AIChatModelFactory --> OpenAICompatibleProvider
  AIChatModelFactory --> AIModelConfiguration
  AIChatModelFactory --> ChatModel
  @enduml
  ```

  `AIModelListConfiguration` is the shared value parser for provider model
  fields. Empty and wildcard-only values are automatic. Automatic discovery
  first rejects models without confirmed tool and text capability and then
  applies every configured wildcard as an allowlist pattern. Any literal entry
  makes the entire value explicit: literal entries become descriptors, wildcard
  entries are ignored, and no provider or metadata request is made for that
  provider.

  The four `OpenAICompatibleProvider` values are data identities consumed by one
  `OpenAICompatibleModelDiscovery`; they are not four duplicated discovery
  classes. The discovery mechanism owns authenticated `GET` requests, the
  common `data[].id` response contract, timeout handling, and success/failure
  distinction. `OpenAIModelMetadataInterpreter` implementations own the two
  verified extension schemas. OpenRouter requires `tools` in
  `supported_parameters` and `text` in output modalities. Requesty requires
  `api=chat` and `supports_tool_calling=true`; image generation remains an
  allowed additional capability. `OpenRouterModelMetadataCatalog` fetches the
  fixed public `https://openrouter.ai/api/v1/models` endpoint without
  authentication and only when an automatic provider needs fallback metadata.
  OpenRouter provider discovery separately uses its configured URL and key.

  ```plantuml
  @startuml
  participant AIModelCatalog as Catalog
  participant AIProviderConfiguration as Configuration
  participant AIModelListConfiguration as ModelList
  participant OpenAICompatibleModelDiscovery as OpenAIDiscovery
  participant GeminiModelDiscovery as GeminiDiscovery
  participant OllamaModelDiscovery as OllamaDiscovery
  participant OpenRouterModelMetadataCatalog as Metadata
  participant AIModelCatalogState as State
  Catalog -> Configuration : enumerate independently configured providers
  loop each configured provider
    Catalog -> ModelList : parse provider model value
    alt explicit literals
      ModelList --> Catalog : trusted literal IDs
    else automatic
      Catalog -> State : get fresh result for exact configuration
      alt refresh required
        alt OpenAI-compatible provider
          Catalog -> OpenAIDiscovery : discover provider models
          OpenAIDiscovery --> Catalog : AIModelDiscoveryResult
        else Gemini
          Catalog -> GeminiDiscovery : discover Gemini models
          GeminiDiscovery --> Catalog : AIModelDiscoveryResult
        else Ollama
          Catalog -> OllamaDiscovery : discover installed models
          OllamaDiscovery --> Catalog : AIModelDiscoveryResult
        end
        alt discovery failed
          Catalog -> State : recordFailure
        else discovery succeeded
          loop capability metadata missing
            Catalog -> Metadata : exact provider-qualified lookup
            Metadata --> Catalog : model metadata or no match
          end
          Catalog -> Catalog : retain tool-capable text models matching wildcards
          Catalog -> State : recordSuccess
        end
      end
    end
  end
  Catalog --> Catalog : create combined sorted descriptors
  @enduml
  ```

  Gemini discovery requests `{gemini base URL}/models?pageSize=1000` with
  `x-goog-api-key`, follows `nextPageToken` if returned, retains
  `generateContent` entries as candidates, removes the `models/` resource
  prefix, and looks up `google/{modelName}` in OpenRouter metadata. OpenAI looks
  up `openai/{modelName}`. Custom first interprets native OpenRouter or Requesty
  fields; otherwise it looks up its returned ID unchanged. No other prefix or
  alias inference occurs.

  Ollama discovery requests `/api/tags`, then `/api/show` for every installed
  model and retains entries reporting both `completion` and `tools`. Its
  configured bearer key is applied to both requests. A failed `/api/tags`
  request fails provider discovery. A failed or metadata-free `/api/show`
  response performs an exact unchanged-ID OpenRouter fallback; no fallback match
  omits that model.

  `AIModelCatalogState` owns application-session cache state and is injectable
  for tests. A successful result is fresh for 30 minutes and is reused without
  a network request. Each cache key includes provider identity, effective model
  and metadata endpoints, and a non-reversible authentication fingerprint; the
  cache key never stores or persists credential text. Configuration changes
  invalidate immediately. A failed attempted refresh removes that source's
  cached result, so failure does not present stale availability.

  The final provider configuration is:

  | Provider | Persisted identity | Service property and default | Secret property | New model property | Model endpoint |
  |---|---|---|---|---|---|
  | OpenAI | `openai` | `ai_openai_service_address=https://api.openai.com/v1` | `ai_openai_key` | `ai_openai_models` | service URL + `/models` |
  | OpenRouter | `openrouter` | `ai_openrouter_service_address=https://openrouter.ai/api/v1` | `ai_openrouter_key` | `ai_openrouter_models` | service URL + `/models` |
  | Requesty | `requesty` | `ai_requesty_service_address=https://router.requesty.ai/v1` | `ai_requesty_key` | `ai_requesty_models` | service URL + `/models` |
  | Custom | `custom` | `ai_custom_service_address=` | `ai_custom_key` | `ai_custom_models` | `ai_custom_models_address` or service URL + `/models` |
  | Gemini | `gemini` | `ai_gemini_service_address=https://generativelanguage.googleapis.com/v1beta` | `ai_gemini_key` | `ai_gemini_models` | service URL + `/models` |
  | Ollama | `ollama` | `ai_ollama_service_address=` | `ai_ollama_api_key` | `ai_ollama_models` | service URL + `/api/tags` and `/api/show` |

  OpenAI, OpenRouter, Requesty, and Gemini are configured only when their own
  key is nonblank. Custom is configured when its service URL is nonblank and
  sends its bearer key only when nonblank. Ollama is configured when its service
  URL is nonblank. Every URL and model property has a separate Preferences
  field; every secret is separately persisted in `secrets.properties`.
  `ai_custom_models_address` is optional and has an empty default.

  `AIModelListPreferenceMigration` runs after new plugin defaults are registered
  and before their keys are secured. It performs these obsolete-to-new moves:

  | Obsolete property | New property |
  |---|---|
  | `ai_openrouter_model_allowlist` | `ai_openrouter_models` |
  | `ai_gemini_model_list` | `ai_gemini_models` |
  | `ai_ollama_model_allowlist` | `ai_ollama_models` |

  If the new property is already user-set, migration preserves it. Otherwise it
  parses the old comma/newline-separated value into a trimmed, deduplicated,
  order-independent model-ID set. A set equal to any known bundled historical
  default is discarded so the new empty default takes effect; every different
  set is copied unchanged to the new property. The obsolete user property is
  removed in every case. Old defaults are not registered, and no migration
  marker or compatibility read path remains.

  `AIChatModelFactory` routes `openai`, `openrouter`, `requesty`, and `custom`
  through one OpenAI-compatible builder using only the selected provider's URL
  and key. Gemini and Ollama retain their dedicated builders. Provider display
  names remain part of each descriptor, so equal model IDs from different
  services remain distinct in selectors. Existing `openrouter|model`
  selections require no migration.
- **Test specification:**
  - **Automated tests:**
    - `AIModelListConfigurationTest`
      - `emptyAndWildcardOnlyValuesUseAutomaticMode`: empty input accepts every
        eligible discovered model and wildcard-only input filters the eligible
        set.
      - `literalEntryMakesWholeValueExplicit`: literals become the trusted list
        and wildcard entries in the same value are ignored.
    - `OpenAICompatibleModelDiscoveryTest`
      - `parsesStandardModelResponseForEveryProviderIdentity`: the shared
        mechanism parses `data[].id`, applies the selected provider identity,
        and tolerates unknown response fields.
      - `usesProviderEndpointAndBearerCredential`: each independent
        configuration uses only its own model URL and key; Custom omits the
        header when its key is blank.
      - `distinguishesSuccessfulEmptyResponseFromFailure`: HTTP, I/O, and parse
        failures return failed discovery while a valid empty list succeeds.
    - `OpenRouterModelMetadataInterpreterTest`
      - `requiresToolsAndTextOutput`: tools plus text output is accepted,
        additional output modalities remain accepted, and missing tools or text
        is rejected.
    - `RequestyModelMetadataInterpreterTest`
      - `requiresChatApiAndToolCalling`: chat models with tool calling are
        accepted regardless of additional image-generation support; non-chat or
        non-tool models are rejected.
    - `OpenRouterModelMetadataCatalogTest`
      - `matchesOnlyExactProviderQualifiedIds`: OpenAI and Google prefixes match
        exact IDs while aliases and similar names do not.
      - `failedRefreshMakesMetadataUnavailable`: a failed attempted refresh does
        not return an older metadata catalog.
    - `GeminiModelDiscoveryTest`
      - `discoversAllPagesAndUsesExactGoogleMetadata`: pagination and
        `models/` normalization produce only exact, tool-capable text matches.
      - `failedListRequestFailsDiscovery`: failed direct discovery does not use
        OpenRouter as an availability substitute.
    - `OllamaModelDiscoveryTest`
      - `retainsCompletionModelsWithTools`: `/api/tags` entries require matching
        `/api/show` completion and tools capabilities.
      - `showFailureUsesExactOpenRouterFallback`: unavailable native metadata
        uses only an exact unchanged model ID and otherwise omits the model.
      - `appliesConfiguredBearerKeyToTagsAndShow`: both Ollama requests use its
        own optional key.
    - `AIModelCatalogTest`
      - `combinesEveryConfiguredProvider`: simultaneous provider results retain
        distinct provider identities and display names.
      - `explicitProviderSkipsDiscoveryAndMetadata`: literal mode performs no
        provider or OpenRouter request and trusts its entries.
      - `automaticOpenAIUsesExactDirectAndMetadataIntersection`: only directly
        visible OpenAI IDs with exact suitable OpenRouter metadata remain.
      - `automaticOpenRouterAndRequestyUseNativeMetadata`: each provider applies
        its own metadata interpreter without cross-provider configuration.
      - `automaticCustomPrefersNativeMetadataThenOpenRouter`: recognized native
        metadata is authoritative and absent metadata uses exact fallback.
      - `providerFailureRemovesOnlyThatProvider`: one failed source contributes
        no models without removing successful independent providers.
      - `freshCacheAvoidsRequestsAndFailedRefreshClearsSource`: cache freshness,
        configuration invalidation, and failure semantics follow the designed
        lifecycle.
    - `AIProviderConfigurationTest`
      - `returnsIndependentConfiguredProviders`: all nonblank provider
        configurations coexist and retain separate URLs, model fields, and
        secrets.
      - `providerActivationUsesItsOwnRequiredField`: cloud providers require
        their keys, Custom requires its URL, and Ollama requires its URL.
    - `AIModelListPreferenceMigrationTest`
      - `discardsEveryKnownHistoricalDefault`: normalized historical OpenRouter,
        Gemini, and Ollama defaults leave the new empty defaults effective and
        remove old keys.
      - `copiesDifferentLegacyOverrideAndRemovesOldKey`: a user-modified list is
        preserved under the new property.
      - `preservesExistingNewValueAndRemovesOldKey`: an already migrated value
        is never overwritten.
    - `AIChatModelFactoryTest`
      - `createsEachOpenAICompatibleProviderWithOwnConfiguration`: all four
        identities use the shared OpenAI-compatible mechanism with the selected
        provider's URL and key.
      - `rejectsUnknownProviderIdentity`: unsupported persisted identities do
        not route through another provider.
  - **Manual tests:**
    - Configure OpenAI, OpenRouter, Requesty, Custom, Gemini, and Ollama together
      and verify that selectors show distinct provider-qualified model entries.
    - Verify empty, wildcard-only, and literal model fields through Preferences,
      including Custom with and without its model-endpoint override.
    - Temporarily make one configured model endpoint unreachable and verify only
      that provider becomes unavailable after refresh.
