# Task: Add tool availability to assistant profiles

- **Task Identifier:** 2026-07-11-profile-tool-availability
- **Scope:** Add a persisted optional tool-availability override to assistant
  profiles, including “use current.” Store the effective profile setting in
  profile-switch transcript state alongside model configuration so restored
  chats retain the applied profile configuration. Selecting a profile in an
  existing chat applies its explicit model and tool settings regardless of how
  the chat was created; “use current” fields retain the existing session/global
  value. When an API operation selects a profile and explicitly supplies its own
  model or tool value, preserve that API value for its existing operation or
  session lifetime; unspecified or `CURRENT` API fields inherit from the
  selected profile. Existing profiles without the new field continue to mean
  “use current.”
- **Motivation:** Assistant profiles can already switch model configuration. A
  profile should be able to switch the corresponding tool policy in the same
  persisted and transcript-restorable configuration boundary instead of
  requiring an unrelated manual session or global change.
