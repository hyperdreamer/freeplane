# Task: Add preference reset-to-default controls

- **Task Identifier:** 2026-07-11-preference-reset
- **Scope:** Add a reset control to every Preferences dialog property editor.
  For properties with a registered Freeplane default, reset must display that
  default; for properties without one, it must display the editor's unset
  representation by assigning `null`. In either case, reset marks the user
  property for removal when the dialog is accepted. Keep each control visible
  and enable it only while its editor is enabled and a user override exists or
  the editor differs from its reset state. A later value change must cancel
  pending removal. For paths, compare expanded placeholders but restore the
  literal registered default. Accepting Preferences must write only edited
  properties so untouched defaults and editor fallbacks do not become user
  overrides. Exclude non-property controls. Add a `Defaults` button as the
  first dialog action; it resets every property editor without saving or
  closing and uses a `Use defaults` tooltip.
- **Motivation:** Users need a visible, property-specific way to recognize and
  remove preference overrides without manually discovering or reproducing each
  Freeplane default value. Writing the current default as another user value is
  insufficient because it leaves an override that prevents future default
  changes from taking effect.
- **Scenario:** A user opens Preferences and sees a reset icon immediately
  before every property editor. An icon is disabled when its editor is
  disabled or when resetting would neither change the displayed value nor
  remove an existing user override. Activating it displays the registered
  default, or the editor's unset representation when no default is registered,
  and records removal of the user override. Equivalent paths compare equal
  after `{user.home}` and `{freeplaneuserdir}` expansion. A subsequent editor
  choice, including an options-file load, cancels pending removal. Accepting
  removes marked overrides and writes only changed, unmarked values; untouched
  values are not promoted to overrides. Canceling changes nothing persisted.
  The first dialog action, `Defaults`, applies the same reset intent to every
  editor but neither saves nor closes the dialog.
- **Constraints:**
  - Reset is a dialog-local action until the user accepts the Preferences
    dialog; it must not persist or notify application property listeners at
    button-click time.
  - Explicit reset must remove overrides from either normal or secret property
    storage while preserving the property's configured storage policy for
    future writes.
  - Existing Preferences validation and options-file save/load behavior must
    continue to operate on all editor values. Loading a value is a later value
    choice and therefore cancels pending removal for that property.
  - Acceptance must persist only values changed from their loaded editor state;
    an untouched registered default or control fallback must not become a user
    override.
  - The control must use the style panel's revert icon presentation rather
    than introduce a second visual convention.
- **Briefing:** The Preferences dialog is built in the `freeplane` module by
  `OptionPanel` from `IPropertyControl` instances created by
  `OptionPanelBuilder`. Persistable editors derive from `PropertyBean`;
  `PropertyAdapter` owns their shared label-and-editor row construction.
  `PreferencesDialogLauncher` currently applies every returned value through
  `ResourceController.setProperty`. Application defaults and user overrides
  are separated inside `ApplicationPropertyStore`, including overrides routed
  to `secrets.properties`. `PathProperty` accepts literal
  `{user.home}` and `{freeplaneuserdir}` prefixes and expands them only for file
  use. The style panel revert icon is constructed through `IconFont`.
- **Research:**

  ```plantuml
  @startuml
  set separator none
  package "Current preference handling" {
    interface IPropertyControl {
      + appendToForm(builder)
      + setEnabled(enabled)
    }
    abstract class PropertyBean {
      + getValue() : String
      + setValue(value)
      + addPropertyChangeListener(listener)
    }
    class PathProperty {
      - value : String
      - path() : String
    }
    class OptionPanel {
      - controls : Vector<IPropertyControl>
      - getOptionProperties() : Properties
      + setProperties()
    }
    interface "OptionPanel.IOptionPanelFeedback" as Feedback {
      + writeProperties(properties)
    }
    class PreferencesDialogLauncher
    abstract class ResourceController {
      + getProperty(key) : String
      + getDefaultProperty(key) : String
      + setProperty(key, value)
      + isPropertySetByUser(key) : boolean
    }
    class ApplicationResourceController
    class ApplicationPropertyStore {
      - defProps : Properties
      - props : Properties
      - secretsProps : Properties
      + setProperty(key, value)
    }

    IPropertyControl <|.. PropertyBean
    PropertyBean <|-- PathProperty
    OptionPanel o-- IPropertyControl
    OptionPanel --> Feedback
    PreferencesDialogLauncher ..|> Feedback
    PreferencesDialogLauncher --> ResourceController
    ResourceController <|-- ApplicationResourceController
    ApplicationResourceController *-- ApplicationPropertyStore
  }
  @enduml
  ```

  ```plantuml
  @startuml
  actor User
  participant OptionPanel
  participant "PreferencesDialogLauncher feedback" as Feedback
  participant ApplicationResourceController as Resources
  participant ApplicationPropertyStore as Store

  User -> OptionPanel : accept Preferences
  OptionPanel -> OptionPanel : collect every PropertyBean value
  OptionPanel -> Feedback : writeProperties(all values)
  loop every returned property
    Feedback -> Resources : setProperty(name, value)
    Resources -> Store : setProperty(name, value)
    Store --> Resources : user override stored
  end
  Resources -> Store : saveProperties()
  @enduml
  ```

  `OptionPanel.setProperties()` initializes each editor from the effective
  property value. On acceptance, `getOptionProperties()` returns every
  `PropertyBean` value without representing removal intent, so even a value
  equal to its Freeplane default is written as a user override.

  `ApplicationPropertyStore` maintains default properties separately from
  normal and secret user properties, but exposes no operation that removes a
  user property while revealing its default again. `ResourceController`
  exposes `isPropertySetByUser` but likewise has no removal operation.

  Most `PropertyBean` subclasses issue their existing property-change event
  for interactive selections. Text, secret, and path editors do not report
  every in-progress value edit through that event, so reset state cannot rely
  on the existing listener contract alone.

  `PathProperty` stores and displays its raw string. It expands
  `{freeplaneuserdir}` and `{user.home}` only when constructing a file path;
  consequently a placeholder default and an equivalent absolute editor value
  currently compare as different strings.

  Some preference definitions have no registered default. In that case
  `setProperties()` passes `null` to the editor, whose `setValue` implementation
  produces a control-specific unset display such as empty text, `false`, a
  numeric fallback, or the first combo entry. Existing acceptance then writes
  that displayed fallback as a user property even if the user made no edit.
- **Analysis:**
  - Applying reset marks the user override for removal on acceptance because
    writing the current default would continue to shadow future defaults.
  - Any later value change cancels that property's pending removal so that the
    user's final explicit value is persisted.
  - Path equality expands supported placeholders, while reset preserves the
    literal default, because placeholder text is Freeplane's canonical and
    portable default representation.
  - Missing registered defaults do not prevent override removal. Assigning
    `null` reproduces the same control-specific unset representation used when
    the dialog initially reads an absent property.
  - Persisting only values changed from their loaded editor state prevents both
    registered defaults and control fallbacks from being promoted to user
    overrides merely by accepting Preferences.
- **Design:**

  ```plantuml
  @startuml
  set separator none
  package org.freeplane {
    package core.resources {
      abstract class ResourceController {
        + removeUserProperty(key)
      }
      package components {
        abstract class PropertyBean {
          - resetControl : PreferencePropertyResetControl
          + getValue() : String
          + setValue(value)
          + setEnabled(enabled)
          # appendToForm(builder, editor)
          # decorateValueComponent(editor) : JComponent
          # valuesEqual(first, second) : boolean
          ~ configureReset(defaultValue)
          ~ initializeResetState(userPropertyWasSet)
          ~ cancelPendingUserPropertyRemoval()
          ~ isValueChanged() : boolean
          ~ isUserPropertyRemovalPending() : boolean
        }
        class PathProperty {
          + getValue() : String
          # valuesEqual(first, second) : boolean
        }
        class PreferencePropertyResetControl {
          - property : PropertyBean
          - defaultValue : String
          - loadedValue : String
          - resetValue : String
          - userPropertyWasSet : boolean
          - userPropertyRemovalPending : boolean
          - button : JButton
          ~ decorate(editor) : JComponent
          ~ initialize(userPropertyWasSet)
          ~ setEditorEnabled(enabled)
          ~ cancelPendingRemoval()
          ~ isValueChanged() : boolean
          ~ isUserPropertyRemovalPending() : boolean
        }
        class OptionPanel {
          - addChildControls(parentControl, controlsTree)
          - loadOptions(inputStream)
          - resetAllPropertiesToDefaults()
          - getOptionProperties() : Properties
          - getChangedOptionProperties() : Properties
          - getUserPropertiesToRemove() : Set<String>
        }
        interface "OptionPanel.IOptionPanelFeedback" as Feedback {
          + writeProperties(properties, userPropertiesToRemove)
        }
        class PreferencesDialogLauncher {
          + {static} open(controls, selectedProperty, validatorsEnabled, event)
          ~ {static} applyPreferenceChanges(resources, properties, removals) : boolean
        }
      }
    }
    package main {
      package application {
        class ApplicationResourceController {
          + removeUserProperty(key)
        }
        class ApplicationPropertyStore {
          + removeUserProperty(key)
        }
      }
      package applet {
        class AppletResourceController {
          + removeUserProperty(key)
        }
      }
    }

    PropertyBean *-- PreferencePropertyResetControl
    PropertyBean <|-- PathProperty
    OptionPanel o-- PropertyBean
    OptionPanel --> Feedback
    PreferencesDialogLauncher ..|> Feedback
    PreferencesDialogLauncher --> ResourceController
    ResourceController <|-- ApplicationResourceController
    ResourceController <|-- AppletResourceController
    ApplicationResourceController *-- ApplicationPropertyStore
  }
  @enduml
  ```

  `OptionPanel` configures a reset control on every created `PropertyBean`,
  passing its nullable `ResourceController.getDefaultProperty(name)` result.
  After loading each effective property, it records the normalized editor value
  and whether a user override exists. Its `Defaults` action is the first button
  in the bottom row and invokes every reset control without invoking feedback,
  saving, or closing. `PropertyBean` decorates its existing editor component
  with that control, placing the reset button immediately before the value
  component. Multi-row radio-button editors reserve the same leading width on
  every subsequent row so all choices remain aligned. `KeyProperty` uses the
  same decoration hook in its custom row construction. Non-property controls
  retain their existing rows unchanged.

  `PreferencePropertyResetControl` owns its button, nullable default, loaded
  editor value, initial user-override state, reset-result value,
  pending-removal state, and listeners for the dialog lifetime. It uses
  `IconFont.createIconButton()`, the style-panel revert character, and the
  existing `reset_to_default` tooltip. Clicking suppresses self-generated
  change handling, assigns the raw default or `null` when absent, captures the
  editor's normalized result, marks removal pending, and refreshes the button.
  A later editor event or text edit clears the pending marker. The button is
  enabled only when the editor is enabled, reset is not pending, and either a
  user override existed at load time or the editor differs from its reset
  target or loaded unset state.

  `PropertyBean` composes the reset control only when configured by
  `OptionPanel`; its existing property-change contract remains unchanged.
  Loading an options-file value explicitly cancels pending removal even if
  setting that value emits no component event. Acceptance treats removal as
  pending only while the editor still equals the captured normalized reset
  result, providing a final guard for custom editors that emit no supported
  event. Value-change detection compares the current editor value with the
  normalized value captured by `setProperties()`.

  `PathProperty.getValue()` reads the displayed field after it exists so reset
  state and acceptance see in-progress text. `PathProperty.valuesEqual`
  expands a leading `{user.home}` or `{freeplaneuserdir}` in both operands and
  compares the resulting `File` values. It does not rewrite either stored or
  displayed operand. Other `PropertyBean` implementations retain exact
  null-safe string equality.

  ```plantuml
  @startuml
  actor User
  participant PreferencePropertyResetControl as Reset
  participant PropertyBean as Editor
  participant OptionPanel
  participant "PreferencesDialogLauncher feedback" as Feedback
  participant ApplicationResourceController as Resources
  participant ApplicationPropertyStore as Store

  User -> Reset : click enabled reset icon
  Reset -> Editor : setValue(raw default or null)
  Reset -> Reset : capture normalized reset result
  Reset -> Reset : mark user-property removal pending
  alt user later changes this editor
    User -> Editor : choose or enter value
    Editor -> Reset : value changed
    Reset -> Reset : cancel pending removal
  end
  User -> OptionPanel : accept Preferences
  OptionPanel -> Feedback : writeProperties(changed values, pending removals)
  loop each pending removal
    Feedback -> Resources : removeUserProperty(name)
    Resources -> Store : removeUserProperty(name)
    Store --> Resources : default or absence revealed
  end
  loop each changed unmarked property
    Feedback -> Resources : setProperty(name, value)
    Resources -> Store : setProperty(name, value)
  end
  Resources -> Store : saveProperties()
  @enduml
  ```

  Acceptance and options-file export validate or serialize the complete
  editor-value `Properties`. Persistence feedback instead receives only values
  changed from their loaded normalized editor state, plus names still marked
  for removal. It removes marked names and writes changed unmarked values.
  Removal deletes the explicit key from normal, secret, and secured property
  layers, leaves secret-storage routing metadata intact for future writes, and
  fires the normal effective property-change notification from the former user
  value to the revealed default or absence. The restart notice and property
  save occur when a changed value was written or an existing override was
  removed. Cancel performs neither operation.
- **Test specification:**
  - **Automated tests:**
    - `OptionPanelTest`
      - `configuresResetForEveryPropertyBean`: properties with or without
        registered defaults receive reset controls; non-property controls do
        not.
      - `resetAllRestoresDefaultsAndUnsetsOtherPropertiesWithoutWriting`: the
        global action restores registered defaults, unsets other properties,
        marks removals, and leaves persistence feedback untouched.
      - `unchangedPropertiesAreExcludedFromPreferenceChanges`: untouched
        registered defaults and unset editor fallbacks are not returned for
        persistence, while an edited value is returned.
      - `loadedValueCancelsPendingRemoval`: loading an options-file value after
        reset cancels that property's pending removal.
    - `PreferencePropertyResetControlTest`
      - `resetButtonReflectsDefaultOverrideAndEditorEnabledState`: the rendered
        reset button accounts for editor enabled state, value equality, and an
        existing override even when its value equals the registered default.
      - `resetButtonClickRestoresDefaultAndMarksRemoval`: clicking the rendered
        button writes the raw default to the editor and exposes pending user
        property removal.
      - `laterEditorChangeCancelsRemoval`: an editor change after reset cancels
        pending removal and recomputes button state.
      - `resetWithoutRegisteredDefaultDisplaysUnsetValueAndMarksRemoval`: a
        property without a registered default receives its unset editor value
        and pending removal.
      - `pathResetKeepsPlaceholderDefault`: resetting a non-default path writes
        the original placeholder-preserving default into the editor.
      - `radioButtonRowsReserveResetButtonWidth`: every radio-button row
        reserves the reset button's leading width so all choices remain
        horizontally aligned.
    - `ComboPropertyTest`
      - `nullValueSelectsFirstChoiceWithoutLoggingAnError`: intentional unset
        selects the existing first-choice fallback without reporting a severe
        invalid-value error.
    - `PathPropertyTest`
      - `placeholderPathEqualsExpandedPath`: `{user.home}` and
        `{freeplaneuserdir}` defaults compare equal to their corresponding
        expanded path values, while unrelated paths remain different.
    - `PreferencesDialogLauncherTest`
      - `appliesPendingResetByRemovingUserProperty`: accepted reset names call
        `removeUserProperty` and are not written through `setProperty`.
      - `appliesLaterValueInsteadOfRemovingUserProperty`: a reset canceled by a
        later value choice writes that final value normally.
      - `removedOverrideCountsAsPreferenceChange`: removing an existing user
        override triggers save and restart-notice handling.
    - `ApplicationResourceControllerTest`
      - `removeUserPropertyPublishesRevealedDefault`: removing an override
        notifies property listeners with the former user value and the newly
        effective default.
    - `ApplicationPropertyStoreTest`
      - `removeUserPropertyRevealsDefault`: removing a normal override deletes
        the explicit value and restores default lookup.
      - `removeUserPropertyDeletesSecretAndSecuredValues`: removal clears the
        explicit value from secret and secured layers without changing future
        secret-storage routing.
  - **Manual tests:**
    - Open each Preferences tab and confirm reset icons align with their
      editors and visually match the style panel revert control, including
      compound and radio-button editors.
    - Confirm `Defaults` is the first bottom-row button, has the `Use defaults`
      tooltip, resets every editor to its registered default or unset state,
      and neither closes nor saves the dialog.
    - Open and accept Preferences without edits, then confirm no new user
      overrides are persisted for defaults or control fallbacks.
    - Change a parent boolean preference and confirm dependent editors and
      their reset buttons enable and disable together.
- **Implementation notes:**
  - **Interpretations:**
    - Resetting a property without a registered default calls `setValue(null)`
      and captures the editor's normalized result; it does not invent a common
      empty, false, numeric, or first-choice default.
    - A non-editable combo interprets this intentional `null` input as its
      existing first-choice fallback without severe logging. Unsupported
      non-null values remain errors.
  - **Tradeoffs:**
    - Pending reset acceptance calls `removeUserProperty` even when no
      persisted user override remains, so a transient secured value is also
      cleared. Save and restart handling still occurs only when a persisted
      user override existed or the effective value changed.
    - Returning an edited value to its loaded representation is treated as no
      value change and preserves any pre-existing override. Explicit reset is
      required to remove that override.
