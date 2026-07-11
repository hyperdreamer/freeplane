# Task: Add preference reset-to-default controls

- **Task Identifier:** 2026-07-11-preference-reset
- **Scope:** Add a reset-to-default button to every resettable property editor
  in the Preferences dialog. Match the reset control used by the style panel.
  Enable each button only when the editor's current value differs from the
  Freeplane default value, accounting for file-property placeholder values when
  comparing and resetting values.
- **Motivation:** Users need a visible, property-specific way to recognize and
  remove preference overrides without manually discovering or reproducing each
  Freeplane default value.
