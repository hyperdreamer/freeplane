# Task: Improve AI model discovery and filtering

- **Task Identifier:** 2026-07-11-model-filter
- **Scope:** Improve access to available AI models through searchable model
  selectors and provider defaults that do not require maintenance as model
  catalogs change. Deliver the work as independently releasable selector and
  default-configuration increments.
- **Motivation:** Model catalogs change frequently and can contain enough entries
  that selecting a model by scanning the full list is inefficient. Curated
  default allowlists also become obsolete as providers change their catalogs.

## Subtask: Filter AI model selectors

- **Status:** in-progress
- **Scope:** Add literal case-insensitive substring filtering to the chat,
  prompt, and assistant-profile model selectors. Share one non-persisted filter
  value for the Freeplane application session. Preserve model selection and
  configuration semantics while filtering. Replace the sole
  `JFilterableComboBox` use with a domain-specific `TagSelector`. Introduce a
  shared `AIModelSelector` UI component while keeping `ChatModelSelector` and
  `AIModelOverrideSelector` as separate selection-policy owners. Do not change
  provider catalog filtering
  or its defaults in this increment.
- **Motivation:** Users need to narrow changing model catalogs quickly through
  their editable selectors, without transient search text becoming a selected
  or persisted model. Domain-specific selector classes keep listener state out
  of dialogs and table construction without generalizing incompatible tag and
  model-selection semantics.
- **Scenario:** A model selector normally shows its selected model, the
  translated “no model selected” placeholder when its selection is empty, or
  the translated short value “unknown” when the selected model is unavailable.
  Chat keeps draft input editable but disables sending while no available model
  is selected. Keyboard and programmatic visible-message submission also stop
  before request construction and report that an available model is required.
  Explicitly opening a selector popup restores and selects the filter text
  retained until Freeplane exits. Editing the normal selected-model text instead
  starts with the user's edit and opens the popup. After 200 ms without another
  editor change, every model whose complete drop-down display name contains the
  entered text, ignoring case, remains visible. Prompt and profile selectors
  continue to show “use current model” independently of the filter. Running a
  prompt action stops before chat creation or request construction when its
  explicit model is unavailable, or when it uses a missing or unavailable
  current model; the user receives a translated modal error dialog owned by the
  action source, including for actions invoked from the node popup menu. Choosing
  a listed model by mouse or keyboard updates the owning chat, prompt, or profile
  selection. Closing or canceling
  without choosing retains the filter for every model selector but restores the
  previously selected model as the closed editor value.
- **Constraints:**
  - Filter text is application-session state and must not be written to
    `AIProviderConfiguration`, prompt data, profile data, or other persistent
    storage.
  - Literal whitespace and punctuation participate in substring matching; the
    filter is not trimmed, tokenized, or interpreted as a pattern.
  - Typing and model-list rebuilding must not fire an explicit model-selection
    change or overwrite the selected model.
  - Editor changes restart one 200 ms Swing timer. Filtering must mutate the
    existing combo-box model only after the timer fires; it must not replace the
    model from inside a document notification.
  - Existing tag-editor behavior remains: empty input shows all tags,
    case-insensitive substring input narrows tags, an exact match selects an
    existing tag, and unmatched input can create a tag.
- **Briefing:** The change spans the core `freeplane` module and the
  `freeplane_plugin_ai` module. `JFilterableComboBox` currently implements
  debounced tag filtering and is used only by `TagEditor`.
  `ChatModelSelector` owns chat selection persistence and shortened closed-value
  rendering. `AiPromptModelSelectionController` is instantiated separately by
  prompt and assistant-profile manager dialogs and includes the synthetic “use
  current model” option. Swing editor preparation, popup, item, and document
  events can recursively fire while combo-box models or editor values are changed, so all
  programmatic transitions must remain guarded and tests must distinguish them
  from explicit selection.
- **Research:**

  ```plantuml
  @startuml
  set separator none
  package "Freeplane" {
    package "freeplane.core.ui.components" {
      class JFilterableComboBox {
        - itemSupplier
        - filterIsRunning
        - acceptAll
        - acceptItem
        - selectItem
        - updateListItems(init)
      }
    }
    package "freeplane.features.icon.mindmapmode" {
      class TagEditor {
        - createTagTable(tags)
        - createTagIfAbsent(spec, specContainsColor)
      }
    }
    package "org.freeplane.plugin.ai" {
      package "chat.ui" {
        class ChatModelSelector {
          - modelSelectionComboBox
          - displayedSelectionValueOverride
          - applyModelSelectionList(descriptors)
          - onModelSelectionChanged()
        }
      }
      package "prompt.ui" {
        class AiPromptModelSelectionController {
          - modelSelectionComboBox
          - availableModelDescriptors
          + refreshModelSelectionList(selectionValue)
          + setSelectedModelSelectionValue(selectionValue)
        }
        class AiPromptManagerDialog
      }
      package "chat.profile" {
        class AssistantProfileManagerDialog
      }
    }
  }
  TagEditor --> JFilterableComboBox : creates sole instance
  AiPromptManagerDialog --> AiPromptModelSelectionController
  AssistantProfileManagerDialog --> AiPromptModelSelectionController
  @enduml
  ```

  ```plantuml
  @startuml
  participant "Tag editor field" as Editor
  participant JFilterableComboBox as Combo
  participant "200 ms Timer" as Timer
  participant "Tag model" as Tags
  Editor -> Combo : focus gained
  Combo -> Combo : show popup
  Combo -> Tags : request all tags
  Tags --> Combo : tag stream
  Editor -> Combo : document changed
  Combo -> Combo : clear selected item
  Combo -> Timer : restart
  Timer -> Tags : request all tags
  Tags --> Combo : tag stream
  Combo -> Combo : retain contains matches\nand select exact match
  @enduml
  ```

  ```plantuml
  @startuml
  actor User
  participant ChatModelSelector as Chat
  participant AiPromptModelSelectionController as Prompt
  participant AIProviderConfiguration as Configuration
  User -> Chat : choose chat model
  Chat -> Configuration : set selected model value
  User -> Prompt : choose prompt or profile model
  Prompt -> Prompt : notify owning editor with selection value
  @enduml
  ```

  `JFilterableComboBox` is always editable at its sole use site. It clears the
  selected item on popup opening and document changes, rebuilds a
  `DefaultComboBoxModel` from a supplied stream after 200 ms, and selects the
  first exact match. Popup closure only removes its document listener.

  Neither AI model controller is currently editable or filterable. Each sorts
  available descriptors by `AIModelDescriptor.getDisplayName()`. Chat and
  prompt/profile selection controllers have different persistence contracts and
  separate model-list construction. Prompt and profile dialogs each create a
  fresh `AiPromptModelSelectionController`; chat has a single controller owned
  by the application-wide `AIChatPanel`.

  No automated tests currently cover `JFilterableComboBox` or its integration
  with `TagEditor`. Existing `ChatModelSelectorTest` and
  `AiPromptModelSelectionControllerTest` cover list construction, unavailable
  selections, rendering, and selection propagation but not editable-editor,
  focus, popup, or filtering behavior.
- **Analysis:**
  - Keep one in-memory filter value shared by every model selector so that the
    same search text follows the user across chat, prompt, and profile usage
    until Freeplane exits.
  - Keep the selected descriptor as the normal combo-box value and let Swing's
    editor preparation display it. Restore and select the shared filter when the
    popup is explicitly opened; a direct document edit starts filtering with
    the user's edited text.
  - Filter complete drop-down display names using literal case-insensitive
    substring matching so that both provider and model text are searchable.
  - Keep “use current model” visible regardless of filtering because it is a
    configuration action rather than a model.
  - Retain filter text after model selection so that selection does not destroy
    independent search state.
  - Replace `JFilterableComboBox` with domain-specific `TagSelector` and
    `AIModelSelector` components so that listeners and transient state stay
    outside table and dialog owners without creating a generic filtering
    abstraction.
  - Keep `ChatModelSelector` and `AIModelOverrideSelector` as separate policy
    owners because chat persistence and session overrides differ from the
    prompt/profile “use current model” contract, while composing the same
    `AIModelSelector` to avoid duplicated model-list and filtering behavior.
- **Design:**

  ```plantuml
  @startuml
  set separator none
  package "Freeplane" {
    package "freeplane.features.icon.mindmapmode" {
      class TagEditor {
        - createTagTable(tags)
        - createTagIfAbsent(spec, specContainsColor)
      }
      class TagSelector {
        - comboBox
        - filterTimer
        - filterIsRunning
        - tagSupplier
        - fontSupplier
        + TagSelector(tagSupplier, fontSupplier)
        + getComboBox()
        - installListeners()
        - updateListItems()
      }
    }
    package "org.freeplane.plugin.ai" {
      package "model" {
        class AIModelDescriptor {
          + getDisplayName()
          + getSelectionValue()
        }
        class AIProviderConfiguration {
          + getOpenRouterKey()
          + getGeminiKey()
          + hasOllamaServiceAddress()
        }
        package "ui" {
          class AIModelFilterState {
            - {static} sharedInstance
            - filterText
            + AIModelFilterState()
            + {static} shared()
            + getFilterText()
            + setFilterText(filterText)
          }
          class AIModelSelector {
            - modelSelectionComboBox
            - modelSelectionModel
            - filterUpdateTimer
            - configuration
            - filterState
            - availableModelDescriptors
            - alwaysVisibleOptions
            - selectedModelText
            - noModelSelectedText
            - selectedModel
            - updateInProgress
            + AIModelSelector(configuration, filterState, alwaysVisibleOptions, selectedModelText)
            + AIModelSelector(configuration, filterState, alwaysVisibleOptions, selectedModelText, noModelSelectedText)
            + getModelSelectionComboBox()
            + setAvailableModelDescriptors(descriptors, selectionValue)
            + setSelectedModelSelectionValue(selectionValue)
            + getSelectedModel()
            + hasAvailableModelSelection(selectionValue)
            + setExplicitModelSelectionListener(listener)
            - installFilterListeners()
            ~ applyPendingFilter()
            - applyFilter(filterText)
            - restoreSelectedModelDisplay()
          }
        }
      }
      package "chat.ui" {
        class AIChatPanel {
          - sendMessage()
          + runPrompt(prompt, owner)
          - updateInputState()
          + submitMessageToSession(sessionId, userMessage)
        }
        class ChatInputControls {
          + update(requestActive, providerConfigured, selectedModelAvailable)
        }
        class ChatModelSelector {
          - configuration
          - modelCatalog
          - modelSelector
          - displayedSelectionValueOverride
          ~ ChatModelSelector(configuration, modelCatalog)
          ~ ChatModelSelector(configuration, modelCatalog, filterState)
          ~ getModelSelectionComboBox()
          ~ hasAvailableSelectedModel()
          ~ hasAvailableModelSelection(selectionValueOverride)
          ~ applyModelSelectionList(descriptors)
          - onExplicitModelSelectionChanged(descriptor)
        }
      }
      package "prompt.ui" {
        class AIModelOverrideSelector {
          - modelCatalog
          - modelSelector
          + AIModelOverrideSelector(configuration, modelCatalog)
          ~ AIModelOverrideSelector(configuration, modelCatalog, filterState)
          + getModelSelectionComboBox()
          + setModelSelectionChangeListener(listener)
          + refreshModelSelectionList(selectionValue)
          + setSelectedModelSelectionValue(selectionValue)
          + getSelectedModelSelectionValue()
          - onExplicitModelSelectionChanged(descriptor)
        }
        class AiPromptManagerDialog
      }
      package "chat.profile" {
        class AssistantProfileManagerDialog
      }
    }
    package "javax.swing" {
      class JComboBox
    }
  }
  TagEditor --> TagSelector : embeds table editor
  TagSelector --> JComboBox : owns tag field and listeners
  AIChatPanel --> ChatInputControls : owns send-control policy
  AIChatPanel --> ChatModelSelector : checks selected availability
  ChatModelSelector --> AIModelSelector : owns chat presentation
  AIModelOverrideSelector --> AIModelSelector : owns override presentation
  AIModelSelector --> JComboBox : owns model field and listeners
  AIModelSelector --> AIModelFilterState : shares filter text
  AIModelSelector --> AIModelDescriptor : filters and selects
  AIModelSelector --> AIProviderConfiguration : resolves availability
  AiPromptManagerDialog --> AIModelOverrideSelector
  AssistantProfileManagerDialog --> AIModelOverrideSelector
  @enduml
  ```

  `AIModelSelector` is the AI-specific UI component shared by chat, prompt, and
  profile selection. It owns the editable combo box, complete sorted descriptor
  list, filtered model, popup and editor listeners, selected descriptor, and
  event-recursion guard. Its constructor receives provider configuration, the
  always-visible options, and the selected-display-text function needed by its
  owner. The production constructor resolves the translated no-selection text;
  an overload accepts its supplier for isolated tests. These are availability,
  data, and presentation parameters, not selection side effects.

  `AIModelFilterState` is a session-only state holder. Its shared instance starts
  with the empty string. `AIModelSelector` accepts a state instance for isolated
  tests and uses `AIModelFilterState.shared()` in production construction. No
  resource property represents this state.

  ```plantuml
  @startuml
  actor User
  participant "Model editor" as Editor
  participant AIModelSelector as Selector
  participant AIModelFilterState as State
  participant "200 ms Swing Timer" as Timer
  participant "ChatModelSelector /\nAIModelOverrideSelector" as Owner
  Selector -> Editor : normally show selected model
  alt user explicitly opens popup
    User -> Selector : open popup
    Selector -> State : get filter text
    State --> Selector : retained filter
    Selector -> Editor : restore and select filter text
  else user edits selected-model text
    User -> Editor : type or edit
    Editor -> Selector : document event
    Selector -> State : set edited filter text
    Selector -> Timer : restart
    Selector -> Selector : open popup
    Timer -> Selector : apply latest filter after quiet period
  end
  Selector -> Selector : mutate existing model to show\ncontains matches and always-visible options
  alt user chooses listed descriptor
    User -> Selector : mouse or keyboard selection
    Selector -> Owner : explicit descriptor selection
    Owner -> Owner : apply chat or override policy
  else popup closes or is canceled
    Selector -> Selector : keep selected descriptor unchanged
  end
  Selector -> Selector : restore complete model list
  Selector -> Editor : show selected model text
  @enduml
  ```

  Chat, prompt, and profile model combo boxes remain editable. Their normal
  combo-box selection is the selected descriptor, and Swing editor preparation
  renders its owner-specific selected text. A null selection is rendered as the
  translated “no model selected” placeholder without creating a selectable or
  persistent synthetic model. Chat renders an unavailable selected descriptor
  as translated “unknown”; its popup entry retains the complete unavailable
  provider and model identity. Opening through the drop-down control restores,
  selects, and immediately applies the shared filter. Editing the selected-model
  text records the edited filter, restarts a non-repeating 200 ms Swing timer,
  and opens the popup. The timer applies only the latest filter after editing
  pauses. No focus listener or delayed display restoration controls the normal
  value.

  `AIModelSelector` receives complete available descriptors from its owner,
  sorts them by display name, and enables the field when any provider is
  configured. A non-empty filter includes a model exactly when
  its complete display name contains the filter according to case-insensitive
  comparison. An empty filter includes every model. Always-visible options are
  prepended after every rebuild and do not participate in matching. If no model
  matches, chat shows an empty popup while prompt and profile show only “use
  current model”.

  `AIModelSelector` retains one `DefaultComboBoxModel` instance. Filter and
  restoration updates mutate that model instead of calling `JComboBox.setModel`,
  which would make Swing hide an open popup and could recursively mutate the
  editor document during its notification. Popup closure cancels the pending
  timer, restores the complete list and selected descriptor, and then lets the
  combo-box editor prepare the selected display text. `ChatModelSelector`
  supplies its existing shortened display function and no always-visible
  options. `AIModelOverrideSelector` supplies complete display names and the
  synthetic “use current model” option. `AIModelSelector` resolves available and
  unavailable descriptors but does not persist selections or notify chat,
  prompt, or profile state during programmatic updates.

  `ChatModelSelector` retains ownership of global configuration persistence,
  legacy-selection migration, displayed session overrides, selected-descriptor
  availability classification, and its normal and explicit selection
  notifications. `AIChatPanel` owns the message-send policy: it passes selected
  availability into `ChatInputControls` and checks it again before ordinary or
  programmatic visible-message request construction. An unavailable or null
  selected descriptor leaves draft input editable, disables the send button,
  and produces the translated “Select an available AI model” message if a
  non-button path attempts submission. Prompt actions converge on
  `AIChatPanel.runPrompt`. Before either shown or hidden execution, the panel
  resolves provider configuration and checks the prompt's effective selection
  against the catalog held by `ChatModelSelector`. “Use current model” resolves
  through global configuration. Missing current selection produces translated
  request-configuration text; an unavailable current or explicit prompt model
  produces translated prompt-specific text. Both failures return before chat
  creation, history mutation, or request construction. `RunAiPromptAction`
  supplies its action-event source component as the dialog owner, and prompt
  validation errors use a modal error dialog rather than chat/status output.
  Script/API request semantics remain unchanged. `AIModelOverrideSelector` replaces
  `AiPromptModelSelectionController` and retains ownership of the empty
  selection value representing “use current model” and prompt/profile selection
  notifications. Each owner receives explicit descriptor choices from its
  composed `AIModelSelector` and applies only its own side effects.

  `TagSelector` remains independent of `AIModelSelector`. It owns an ordinary
  editable `JComboBox<Tag>` and its popup, focus, document, and timer listeners.
  It preserves the current 200 ms filtering behavior, selection clearing,
  exact-match selection, renderer, and editor focus behavior. `TagEditor` embeds
  the configured combo box in its `DefaultCellEditor` and retains new-tag
  creation and table commit behavior. `JFilterableComboBox` is deleted after its
  only use and import are removed.
- **Test specification:**
  - **Automated tests:**
    - `AIModelSelectorTest`
      - `normalState_displaysSelectedModel`: the editable control uses its
        selected descriptor and Swing editor preparation to display the normal
        selected-model text without entering filtering mode.
      - `normalState_withoutSelectionDisplaysTranslatedPlaceholder`: a null
        selection displays translated placeholder text without selecting a
        synthetic model.
      - `openingPopup_restoresAndSelectsSharedFilter`: explicitly opening the
        popup restores the injected filter, selects all text, and enters
        filtering mode.
      - `typingInNormalEditor_startsFiltering`: a direct editor change replaces
        normal display state with the edited filter, retains the same combo-box
        model instance, and updates its items only after the pending 200 ms
        filter is applied.
      - `filtering_matchesCompleteDisplayNameIgnoringCase`: document edits show
        only models whose provider-and-model display names contain the literal
        filter, with empty and no-match cases covered.
      - `filtering_preservesAlwaysVisibleOptions`: every model rebuild retains
        supplied options independently of their display text.
      - `closingPopup_restoresSelectedModelWithoutChangingFilter`: popup closure
        restores the full list and selected display without emitting an
        explicit-selection callback or losing shared filter text.
      - `instances_shareInjectedFilterState`: separate selector instances read
        and update the same supplied filter state.
      - `explicitFilteredSelection_notifiesOwnerAndRetainsFilter`: mouse or
        keyboard-equivalent selection reports the chosen descriptor once and
        leaves filter text unchanged.
    - `ChatModelSelectorTest`
      - `filteredSelection_persistsModelAndRetainsSessionOverrideContract`: an
        explicit choice received from `AIModelSelector` preserves configuration
        persistence, normal notification, and explicit-notification behavior.
      - `selectedModelDisplay_remainsShortened`: the composed selector uses the
        existing shortened chat display while popup entries retain complete
        display names.
      - `renderer_showsUnknown_forSelectedUnavailableValueAndFullNameInDropdown`:
        an unavailable closed value is translated to “unknown” while its popup
        row retains the complete unavailable-model name.
      - `hasAvailableSelectedModel_rejectsUnavailableSelection`: an unknown
        selected descriptor is classified as unavailable for chat submission.
      - `hasAvailableSelectedModel_acceptsCatalogSelection`: a selected catalog
        descriptor is classified as available.
      - `hasAvailableModelSelection_resolvesCurrentAndExplicitValuesAgainstCatalog`:
        current and explicit prompt selection values are compared with the
        loaded catalog while stale values are rejected.
      - `setDisplayedSelectionValueOverride_showsOverrideWithoutPersistingConfiguration`:
        session display overrides remain programmatic and non-persistent.
    - `ChatInputControlsTest`
      - `unavailableSelectedModel_disablesSendingButKeepsInputEditable`: an
        unknown or absent selected model preserves draft editing while disabling
        the send control and showing the availability tooltip.
      - `availableSelectedModel_reenablesSending`: choosing a catalog model
        restores normal send controls.
    - `AiPromptActionRegistryTest`
      - `promptAction_passesEventSourceComponentAsErrorDialogOwner`: a prompt
        action passes its menu-item event source through to prompt execution.
    - `AIChatPanelScriptRequestTest`
      - `sendMessageStopsBeforeRequestConstructionForUnavailableModel`: a
        non-empty visible chat message retains its draft and returns before
        request construction when the selected descriptor is unknown.
      - `runPromptUsingMissingCurrentModelStopsBeforeRequestConstruction`: a
        prompt using current model presents the translated missing-selection
        modal error using the supplied owner and returns without constructing a
        request.
      - `runPromptStopsBeforeRequestConstructionForUnavailableExplicitModel`:
        a stale explicit prompt model presents the translated modal error using
        the supplied owner and returns before shown or hidden execution.
    - `AiRequestConfigurationResolverTest`
      - `reportsMissingSelectedModelAsConfigurationError`: missing effective
        selection returns the translated model-selection configuration error.
    - `AIModelOverrideSelectorTest`
      - `construction_suppliesUseCurrentModelAsAlwaysVisibleOption`: filtering
        through the composed selector always leaves the synthetic option visible.
      - `explicitSelection_notifiesPromptOrProfileWithSelectionValue`: model and
        “use current model” choices produce their existing external values.
      - `programmaticSelection_doesNotNotifyOwner`: restoring a prompt or profile
        selection updates only the composed selector.
    - `TagSelectorTest`
      - `editableTagComboBox_preservesFilteringBehavior`: the owned combo box
        shows all tags for empty text, applies delayed case-insensitive
        substring filtering, selects exact matches, and leaves unmatched text
        in the editor.
    - `TagEditorTest`
      - `tagSelector_preservesNewTagCommitBehavior`: the table editor commits an
        existing selected tag directly and creates a tag from unmatched editor
        text.

## Subtask: Let provider defaults accept all discovered models

- **Status:** backlog
- **Scope:** Replace curated default provider model-filter property values with
  defaults that accept every discovered model. Include a one-time migration:
  replace a stored user override when it is sufficiently similar to the former
  default value to represent that default, and preserve every other override.
  Preserve coherent behavior when provider discovery fails. The exact
  comparison rule, target values, migration trigger, and fallback behavior will
  be clarified when this subtask becomes current.
- **Motivation:** Provider model catalogs change without Freeplane releases, so
  curated defaults require recurring maintenance and hide newly introduced
  models.
