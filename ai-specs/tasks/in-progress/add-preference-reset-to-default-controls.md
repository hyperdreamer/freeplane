# Task: Add preference reset-to-default controls

- **Task Identifier:** 2026-07-11-preference-reset
- **Scope:** Add a reset-to-default button to every Preferences dialog
  property editor for which `ResourceController` supplies a non-null Freeplane
  default. Keep each button visible and enable it only while its editor is
  enabled and its current value differs from that default. Applying reset must
  restore the editor value and mark the corresponding user property for
  removal when the dialog is accepted. A later value change for that editor
  must cancel the pending removal. For path properties, compare values after
  expanding supported path placeholders, but restore the original
  placeholder-preserving default. Exclude non-property controls and properties
  without a Freeplane default.
- **Motivation:** Users need a visible, property-specific way to recognize and
  remove preference overrides without manually discovering or reproducing each
  Freeplane default value. Writing the current default as another user value is
  insufficient because it leaves an override that prevents future default
  changes from taking effect.
- **Scenario:** A user opens Preferences and sees a reset icon beside every
  property that has a Freeplane default. A reset icon is disabled when its
  editor already contains that default or when the editor itself is disabled.
  Equivalent path values compare equal after `{user.home}` and
  `{freeplaneuserdir}` expansion. When the user activates an enabled reset
  icon, the editor shows the literal Freeplane default and the dialog records
  that the user override is to be removed. If the user subsequently chooses or
  enters another value for that property, including by loading an options
  file, the removal is canceled. Accepting the dialog removes each remaining
  marked override and writes other edited values; canceling the dialog changes
  no persisted properties.
- **Constraints:**
  - Reset is a dialog-local action until the user accepts the Preferences
    dialog; it must not persist or notify application property listeners at
    button-click time.
  - Explicit reset must remove overrides from either normal or secret property
    storage while preserving the property's configured storage policy for
    future writes.
  - Existing Preferences validation and options-file save/load behavior must
    continue to operate on the editor values. Loading a value is a later value
    choice and therefore cancels a pending removal for that property.
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
- **Analysis:**
  - Applying reset marks the user override for removal on acceptance because
    writing the current default would continue to shadow future defaults.
  - Any later value change cancels that property's pending removal so that the
    user's final explicit value is persisted.
  - Path equality expands supported placeholders, while reset preserves the
    literal default, because placeholder text is Freeplane's canonical and
    portable default representation.
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
          ~ configureResetToDefault(defaultValue)
          ~ cancelPendingUserPropertyRemoval()
          ~ isUserPropertyRemovalPending() : boolean
        }
        class PathProperty {
          + getValue() : String
          # valuesEqual(first, second) : boolean
        }
        class PreferencePropertyResetControl {
          - property : PropertyBean
          - defaultValue : String
          - userPropertyRemovalPending : boolean
          - button : JButton
          ~ decorate(editor) : JComponent
          ~ setEditorEnabled(enabled)
          ~ cancelPendingRemoval()
          ~ isUserPropertyRemovalPending() : boolean
        }
        class OptionPanel {
          - addChildControls(parentControl, controlsTree)
          - loadOptions(inputStream)
          - getOptionProperties() : Properties
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

  `OptionPanel` configures a reset control on each created `PropertyBean` whose
  name has a non-null `ResourceController.getDefaultProperty(name)` result.
  `PropertyBean` decorates its existing editor component with that control, so
  normal, compound, and multi-row editors retain their current form layout.
  `KeyProperty` uses the same decoration hook in its custom row construction.
  Properties without defaults retain their existing rows unchanged.

  `PreferencePropertyResetControl` owns its button, enabled state, default
  value, pending-removal state, and listeners for the lifetime of the dialog
  editor. It uses `IconFont.createIconButton()`, the style-panel revert
  character, and the existing `reset_to_default` tooltip text. It listens to
  the associated `PropertyBean` events and to descendant Swing text documents
  found when it decorates the editor. Clicking it suppresses self-generated
  change handling, sets the associated editor to the raw default, marks
  removal pending, and refreshes the button. A subsequent editor event or text
  edit clears the pending marker and refreshes the button. Its enabled state
  is the conjunction of the editor's enabled state and
  `!property.valuesEqual(property.getValue(), defaultValue)`.

  `PropertyBean` composes the reset control only when configured by
  `OptionPanel`; its existing property-change contract remains unchanged.
  Loading an options-file value explicitly cancels pending removal for each
  loaded property even if setting that value emits no component event.
  Acceptance treats removal as pending only while the editor still equals the
  default, providing a final guard for custom editors that do not emit a
  supported event.

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
  Reset -> Editor : setValue(raw Freeplane default)
  Reset -> Reset : mark user-property removal pending
  alt user later changes this editor
    User -> Editor : choose or enter value
    Editor -> Reset : value changed
    Reset -> Reset : cancel pending removal
  end
  User -> OptionPanel : accept Preferences
  OptionPanel -> Feedback : writeProperties(values, pending removals)
  loop each property
    alt pending removal
      Feedback -> Resources : removeUserProperty(name)
      Resources -> Store : removeUserProperty(name)
      Store --> Resources : effective default revealed
    else ordinary value
      Feedback -> Resources : setProperty(name, value)
      Resources -> Store : setProperty(name, value)
    end
  end
  Resources -> Store : saveProperties()
  @enduml
  ```

  Acceptance validates the complete editor-value `Properties` exactly as
  today. The feedback receives those values plus the names still marked for
  removal. It calls `removeUserProperty` instead of `setProperty` for marked
  names and applies all other values normally. Removal deletes the explicit
  key from normal, secret, and secured property layers, leaves secret-storage
  routing metadata intact for future writes, and fires the normal effective
  property-change notification from the former user value to the revealed
  default. The restart notice and property-file save occur when either a value
  changed or an existing user override was removed. Cancel performs neither
  operation.
- **Test specification:**
  - **Automated tests:**
    - `OptionPanelTest`
      - `configuresResetForEveryPropertyBeanWithDefault`: controls created for
        properties with non-null defaults receive reset controls, while
        non-property controls and properties without defaults do not.
      - `loadedValueCancelsPendingRemoval`: loading an options-file value after
        reset cancels that property's pending removal.
    - `PreferencePropertyResetControlTest`
      - `resetButtonIsEnabledOnlyForEnabledNonDefaultEditor`: the rendered
        reset button is enabled exactly when its editor is enabled and differs
        from its default.
      - `resetButtonClickRestoresDefaultAndMarksRemoval`: clicking the rendered
        button writes the raw default to the editor and exposes pending user
        property removal.
      - `laterEditorChangeCancelsRemoval`: an editor change after reset cancels
        pending removal and recomputes button state.
      - `pathResetKeepsPlaceholderDefault`: resetting a non-default path writes
        the original placeholder-preserving default into the editor.
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
    - Change a parent boolean preference and confirm dependent editors and
      their reset buttons enable and disable together.
