# Task: Add tri-state AI temperature configuration
- **Task Identifier:** 2026-06-28-tri-state-temperature
- **Scope:**
  Replace nullable numeric AI temperature configuration with an explicit
  temperature state that distinguishes inherited/current fields,
  model-default fields, and numeric fields. Apply the new state to
  public request options, internal request resolution, global
  preferences, saved prompts, assistant profiles, visible-chat session
  metadata, transcript metadata, provider mapping, prompt/profile
  dialogs, and the AI chat popup menu.

  In scope:
  - add a public temperature value object for numeric and model-default
    states;
  - make unset/null temperature fields mean inherited/current only;
  - store global temperature through a compact preference control with no
    `Current` option;
  - replace prompt/profile temperature text fields with compact
    selectors containing `Current`, `Model default`, numeric presets,
    and `Custom...`;
  - add a `Temperature` submenu to the AI chat popup menu containing
    `Model default`, numeric presets, and `Custom...`;
  - preserve old persisted numeric temperature values and ignore invalid
    persisted values without dropping prompt, profile, or transcript
    records.

  Out of scope:
  - changing model-selection, thinking-effort, or tool-availability
    semantics except where temperature session metadata shares the same
    model-configuration object;
  - adding provider-specific temperature range validation;
  - adding a top-bar temperature selector beside the model and thinking
    selectors.
- **Motivation:**
  A nullable `Double` cannot distinguish an inherited temperature from an
  explicit request to use the model default. That makes it impossible
  for a prompt, profile, session, or script request to suppress a lower
  numeric default. The text-field temperature UI is also too error-prone
  and inconsistent with the compact model and thinking controls.
- **Scenario:**
  A user sets global AI temperature to `0.7`. A saved prompt whose
  temperature is `Current` inherits `0.7`. A saved prompt whose
  temperature is `Model default` sends no temperature to the provider,
  even though the global default is `0.7`. A saved prompt whose
  temperature is `0.2` sends `0.2`.

  In the AI chat popup menu, the user chooses `Temperature -> Model
  default`. Freeplane writes the global `ai_temperature` preference to
  the model-default state, clears any active visible-session
  temperature override, and future ordinary chat requests send no
  temperature unless a stronger prompt, profile, session, or script
  request sets one.

  A user loads existing prompt, profile, or transcript JSON containing a
  numeric `temperature` property. Freeplane keeps that numeric value.
  Loading invalid persisted temperature data leaves only that temperature
  field unset and does not discard the containing record.
- **Glossary:**

  ```mermaid
  graph TD
    Current[Current / inherited field] -->|stores no field value| Fallback[Fallback chain]
    ModelDefault[Model default] -->|explicitly suppresses lower numeric values| EffectiveTemperature[Effective temperature]
    Numeric[Numeric temperature] -->|explicit value| EffectiveTemperature
    Fallback --> EffectiveTemperature
    EffectiveTemperature -->|numeric only| ProviderBuilder[Provider builder temperature call]
    EffectiveTemperature -->|model default| OmittedTemperature[No provider temperature call]
  ```

  - Temperature state: the value of one temperature field after parsing a
    layer of model configuration.
    - It is either model default or a numeric temperature.
    - The absence of a temperature state means current/inherited.
  - Current: the prompt/profile UI choice that stores no temperature
    field at that layer.
    - It is not a global preference value because global preferences have
      no lower layer to inherit from.
  - Model default: an explicit temperature state that causes provider
    builders to receive no temperature value.
    - It is stronger than lower fallback layers.
  - Numeric temperature: a finite `double` temperature state.
    - Provider-specific accepted ranges are not validated in this task.
  - Temperature preset: one of the fixed numeric UI choices `0`, `0.2`,
    `0.5`, `0.7`, or `1.0`.
  - Custom temperature: a finite numeric temperature entered through a
    `Custom...` dialog and stored as the numeric string.
- **Constraints:**
  - Use the exact user-facing labels `Current`, `Model default`,
    `Temperature`, and `Custom...`.
  - Do not show `Current` in global Preferences or the AI chat popup menu.
  - Prompt and profile controls must treat `Current` as an unset field,
    not as model default.
  - Model default must be an explicit non-null temperature state in
    model configuration so it suppresses fallback numeric values.
  - Do not add `AiTemperature.current()` or any aggregate model
    configuration `empty`, `inherit`, `unset`, `isEmpty`, or
    `hasAnything` API.
  - Do not add `final` modifiers to new class declarations.
  - Prompt/profile temperature UI must not be a plain text field and
    must not occupy its own row.
  - The chat popup edit items remain without tooltips.
  - Existing blank global `ai_temperature` values recover as model
    default.
  - Existing numeric persisted temperature values recover as numeric
    temperature states.
  - Invalid prompt/profile/transcript temperature values are ignored for
    that field only.
  - Translation files edited for new UI labels must remain ASCII escaped
    and must be followed by the repository translation formatting gate.
- **Briefing:**
  Public script-facing model request options live in
  `freeplane_api/src/main/java/org/freeplane/api/ai`. Internal model
  configuration and provider mapping live in
  `freeplane_plugin_ai/src/main/java/org/freeplane/plugin/ai/model`.
  Global AI defaults are stored in
  `freeplane_plugin_ai/src/main/resources/org/freeplane/plugin/ai/defaults.properties`
  and exposed by
  `freeplane_plugin_ai/src/main/resources/org/freeplane/plugin/ai/preferences.xml`.

  Prompt temperature data and UI currently live in `AiPrompt` and
  `AiPromptManagerDialog`. Assistant profile temperature data and UI
  currently live in `AssistantProfile` and
  `AssistantProfileManagerDialog`. Visible-session and transcript model
  configuration metadata currently use `AIModelConfiguration` through
  `LiveChatSession`, `LiveChatController`, `ChatTranscriptRecord`, and
  `TranscriptMemoryMapper`.

  The Freeplane option panel is built by
  `org.freeplane.core.resources.components.OptionPanelBuilder`, whose
  XML handlers currently support fixed combos, editable combos through
  direct builder calls, numbers, strings, and radio buttons, but no
  non-editable preset-plus-custom numeric control.
- **Research:**

  ```plantuml
  @startuml
  title Current nullable temperature flow
  set separator none
  package "Freeplane AI" {
    package "freeplane_api" {
      package "org.freeplane.api.ai" {
        class AiModelConfiguration {
          -Double temperature
          +getTemperature(): Double
          +builder(): Builder
        }
      }
    }
    package "freeplane_plugin_ai" {
      package "org.freeplane.plugin.ai" {
        package "model" {
          class AIModelConfiguration {
            -Double temperature
            +getTemperature(): Double
            +withFallback(AIModelConfiguration): AIModelConfiguration
          }
          class AIProviderConfiguration {
            +getDefaultModelConfiguration(): AIModelConfiguration
          }
          class AIChatModelFactory {
            +createChatLanguageModel(AIProviderConfiguration, AIModelConfiguration)
          }
        }
        package "prompt.ui" {
          class AiPromptManagerDialog {
            -JTextField temperatureField
          }
        }
        package "chat.profile" {
          class AssistantProfileManagerDialog {
            -JTextField temperatureField
          }
        }
      }
    }
  }
  AiModelConfiguration --> AIModelConfiguration : mapped by AiRequestMappings
  AIProviderConfiguration --> AIModelConfiguration : parses global default
  AIModelConfiguration --> AIChatModelFactory : effective configuration
  AiPromptManagerDialog --> AIModelConfiguration : writes nullable Double
  AssistantProfileManagerDialog --> AIModelConfiguration : writes nullable Double
  @enduml
  ```

  ```plantuml
  @startuml
  title Current prompt/profile temperature layout
  [Model selector] -right- [Thinking selector]
  [Model selector] -down- [Temperature text field]
  [Temperature text field] -down- [Prompt text]
  @enduml
  ```

  - `AIModelConfiguration.withFallback(...)` currently falls back on
    temperature whenever the stronger layer has `temperature == null`.
    That makes explicit model default unrepresentable.
  - `AIProviderConfiguration.getDefaultModelConfiguration()` currently
    parses `ai_temperature` as a nullable `Double`; blank and invalid
    values become null.
  - `AIChatModelFactory` applies temperature to OpenRouter, Gemini, and
    Ollama only when the effective temperature `Double` is non-null.
  - `AiPrompt` and `AssistantProfile` normalize an all-null model
    configuration to null. With a model-default temperature state, a
    configuration containing only that temperature is no longer empty.
  - Prompt and profile dialogs currently use `JTextField` temperature
    controls placed on a separate row below model and thinking controls.
  - `ModelConfigurationSelectorLayout` currently supports `model` and
    `thinking` slots. It keeps both on one line and keeps model width at
    least as wide as the thinking selector.
  - `ToolAvailabilityLevelMenu` provides the current popup-menu pattern:
    radio button items are refreshed before display and explicit user
    selection calls back into `AIChatPanel`.
  - `OptionPanelBuilder` can be extended with another XML element
    handler and an `IPropertyControl` implementation for the new
    compact preference control.
- **Analysis:**
  - Temperature needs a value object because the state space is not a
    nullable number. Null is already assigned to field inheritance.
  - Global temperature controls cannot have `Current`: a global setting
    has no lower configuration layer.
  - A model-default temperature state must participate in the same
    per-field fallback algorithm as numeric temperature. It must be
    non-null, so it blocks lower numeric values, but provider mapping
    must omit the provider builder temperature call.
  - Preferences need a compact preset-plus-custom control rather than a
    string field to avoid free-form invalid values in normal use while
    still allowing provider-specific numeric values outside the presets.
  - Prompt/profile UI should reuse one temperature selector controller so
    both dialogs keep identical dirty-state and persistence semantics.
  - The chat popup menu should mutate global `ai_temperature` and clear
    only the active session temperature override, matching the existing
    model/thinking pattern without adding a top-bar temperature control.
- **Design:**

  ```plantuml
  @startuml
  title Target temperature state classes and persistence
  set separator none
  package "Freeplane AI" {
    package "freeplane_api" {
      package "org.freeplane.api.ai" {
        class AiTemperature {
          +modelDefault(): AiTemperature
          +of(double): AiTemperature
          +isModelDefault(): boolean
          +isNumeric(): boolean
          +getValue(): Double
        }
        class AiModelConfiguration {
          -AiTemperature temperature
          +getTemperature(): AiTemperature
          +builder(): Builder
        }
        class "AiModelConfiguration.Builder" as PublicBuilder {
          +temperature(AiTemperature): Builder
        }
      }
    }
    package "freeplane_plugin_ai" {
      package "org.freeplane.plugin.ai.model" {
        class AIModelConfiguration {
          -AiTemperature temperature
          +getTemperature(): AiTemperature
          +withFallback(AIModelConfiguration): AIModelConfiguration
          +getStoredTemperature(): Object
        }
        class AIModelTemperatureStorage {
          +fromStoredValue(Object): AiTemperature
          +fromGlobalPreferenceValue(String): AiTemperature
          +toStoredValue(AiTemperature): Object
          +toPreferenceValue(AiTemperature): String
        }
        class AIProviderConfiguration {
          +getDefaultModelConfiguration(): AIModelConfiguration
          +setTemperatureValue(AiTemperature)
        }
        class AIChatModelFactory {
          +createChatLanguageModel(AIProviderConfiguration, AIModelConfiguration)
        }
      }
    }
  }
  AiModelConfiguration --> AiTemperature
  PublicBuilder --> AiTemperature
  AIModelConfiguration --> AiTemperature
  AIModelConfiguration --> AIModelTemperatureStorage : JSON read/write helpers
  AIProviderConfiguration --> AIModelTemperatureStorage : preference parsing
  AIChatModelFactory --> AiTemperature : numeric -> builder call
  @enduml
  ```

  Add public `AiTemperature` in `org.freeplane.api.ai`. It has two
  concrete states: model default and numeric. It has no current state;
  unset/null model-configuration fields continue to mean current or
  inherited. `AiTemperature.of(double)` rejects NaN and infinity.

  Change public `AiModelConfiguration` and internal
  `AIModelConfiguration` to hold `AiTemperature`. The public builder
  accepts `temperature(AiTemperature)`. Internal Jackson persistence
  keeps the JSON property name `temperature` but uses
  `AIModelTemperatureStorage`: numeric states write as JSON numbers,
  model default writes as the string `model_default`, and null
  writes nothing. Existing JSON numbers read as numeric states. Blank or
  invalid persisted values read as null for prompt/profile/transcript
  records.

  `AIModelConfiguration.withFallback(...)` keeps its per-field behavior:
  if the stronger configuration has a non-null temperature state, it uses
  that state; otherwise it falls back. Model default is therefore an
  explicit fallback blocker. Normalizers in prompts, profiles, sessions,
  and transcripts treat model-default temperature as present data.

  `AIProviderConfiguration.getDefaultModelConfiguration()` parses the
  global `ai_temperature` preference with global semantics: `null`,
  blank, `model_default`, and invalid values become
  `AiTemperature.modelDefault()`, while finite numeric strings become
  numeric temperature states. New default properties use
  `ai_temperature = model_default`. `setTemperatureValue(...)` writes
  `model_default` or the canonical numeric string.

  Provider mapping changes only the temperature branch. OpenRouter,
  Gemini, and Ollama receive a temperature builder call only when the
  effective `AiTemperature` is numeric. Model-default effective
  temperature sends no temperature to the provider.

  ```plantuml
  @startuml
  title Target UI controls
  set separator none
  package "Freeplane AI" {
    package "freeplane_api" {
      package "org.freeplane.api.ai" {
        class AiTemperature
      }
    }
    package "freeplane_plugin_ai" {
      package "org.freeplane.plugin.ai" {
        package "model" {
          class AIProviderConfiguration {
            +setTemperatureValue(AiTemperature)
          }
        }
        package "prompt.ui" {
          class AiTemperatureSelectionController {
            +AiTemperatureSelectionController(boolean includeCurrent)
            +getComboBox(): JComboBox
            +setSelectedTemperature(AiTemperature)
            +getSelectedTemperature(): AiTemperature
            +setTemperatureSelectionChangeListener(Consumer<AiTemperature>)
          }
        }
        package "chat.ui" {
          class ModelConfigurationSelectorLayout {
            +addLayoutComponent(Component, Object)
            +layoutContainer(Container)
          }
          class ChatTemperatureMenu {
            +addTo(JPopupMenu)
            +refreshSelection()
          }
        }
      }
    }
    package "freeplane" {
      package "org.freeplane.core.resources.components" {
        class ChoiceOrNumberProperty {
          +getValue(): String
          +setValue(String)
          +getValueComponent(): JComponent
        }
        class OptionPanelBuilder {
          +initReadManager()
        }
      }
    }
  }
  AiTemperatureSelectionController --> AiTemperature
  ChatTemperatureMenu --> AIProviderConfiguration : writes ai_temperature
  ChoiceOrNumberProperty --> OptionPanelBuilder : XML element choice_or_number
  @enduml
  ```

  Add reusable `AiTemperatureSelectionController` for prompt/profile
  dialogs. With `includeCurrent=true`, it offers `Current`, `Model
  default`, presets `0`, `0.2`, `0.5`, `0.7`, `1.0`, and `Custom...`.
  `Current` returns null. `Model default` returns
  `AiTemperature.modelDefault()`. Presets and accepted custom input
  return numeric states. Invalid custom input leaves the previous
  selection and stored draft unchanged.

  Extend `ModelConfigurationSelectorLayout` with an optional
  `temperature` slot. The model selector remains flexible; thinking and
  temperature selectors stay at preferred width; the model selector is
  never narrower than the widest compact selector. Prompt and profile
  dialogs place model, thinking, and temperature on the same row. Prompt
  tool selection may remain adjacent to that row, but temperature no
  longer uses a text field or a separate row.

  Add `ChatTemperatureMenu` to the AI chat popup after the tool
  availability menu. It offers `Model default`, the numeric presets,
  and `Custom...`; it has no `Current` item. Its checked state displays
  the active session temperature override when one exists, otherwise the
  global preference. Selecting an item writes `ai_temperature`, clears
  only the active session temperature override, and refreshes the popup
  state. Existing edit menu items keep no tooltips.

  Add `ChoiceOrNumberProperty` and XML element `choice_or_number` to the
  core preference components. The property displays fixed choices plus a
  non-editable `Custom...` item that opens a finite-number input dialog.
  It supports `blankValue="model_default"` so old blank preference
  values display as model default. Replace `<string name="ai_temperature"/>`
  in the AI preferences XML with `choice_or_number` choices for
  `model_default`, `0`, `0.2`, `0.5`, `0.7`, and `1.0`.
- **Test specification:**
  - Automated tests:
    - Public `AiTemperature` represents model-default and numeric
      states, rejects NaN and infinity, and has no current state.
    - Public and internal model configuration store temperature as
      `AiTemperature`; null still means inherited/current.
    - Internal fallback uses a stronger model-default temperature
      instead of falling back to a lower numeric temperature.
    - Global temperature preference parsing maps blank,
      `model_default`, and invalid values to model default and maps
      finite numeric strings to numeric states.
    - Prompt/profile/transcript JSON read old numeric `temperature`
      values as numeric states, read `model_default` as model
      default, omit null/current fields on write, and ignore invalid
      temperature values without dropping records.
    - Provider factory calls OpenRouter, Gemini, and Ollama temperature
      builders only for numeric effective temperature and omits the
      calls for model-default effective temperature.
    - Request-configuration resolution lets prompt, profile, session, or
      script model-default temperature suppress lower numeric defaults.
    - Prompt and profile temperature selectors expose `Current`,
      `Model default`, presets, and `Custom...`; `Current` stores an
      unset field, model default stores an explicit state, numeric
      choices store numeric states, and invalid custom input preserves the
      previous draft value.
    - Prompt/profile model, thinking, and temperature controls share one
      row, and the model selector is not laid out narrower than thinking
      or temperature.
    - AI chat popup temperature menu has no `Current` item, writes the
      global `ai_temperature` preference on user selection, and clears
      only the active session temperature override.
    - The `choice_or_number` preference control displays old blank
      `ai_temperature` values as model default, stores
      `model_default` for model default, stores numeric strings for
      presets/custom values, and rejects invalid custom input without
      changing the stored value.
  - Manual tests: N/A
