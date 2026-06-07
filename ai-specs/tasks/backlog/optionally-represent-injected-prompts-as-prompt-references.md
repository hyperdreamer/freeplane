# Task: Optionally represent injected prompts as `{{prompt name}}`

- **Task Identifier:** 2026-05-26-prompt-reference-display
- **Scope:**
  Optionally show prompt insertions and prompt-driven visible-chat
  injections as `{{prompt name}}` references instead of raw prompt
  text.
- **Motivation:**
  Prompt identity would stay visible in the input field and visible chat
  transcript even when the underlying prompt text comes from a prompt
  definition.
- **Constraints:**
  - This is a follow-up task. Keep the raw prompt-text insertion and
    visible-chat injection baseline unchanged until this task becomes
    current.
  - When implemented, the representation must cover chat-input prompt
    completion, manual prompt actions, and script- or action-driven
    visible-chat prompt injection.
  - Hidden prompt execution and prompt lookup semantics remain out of
    scope until this task becomes current.
- **Briefing:**
  This follow-up depends on the raw prompt-text baseline established by
  prompt completion and visible-chat prompt execution.
- **Research:**
  Current prompt insertion and prompt-driven visible-chat injection use
  raw prompt text. The exact placeholder-resolution boundary is still to
  be designed.
- **Design:**
  To be done when this task becomes current.
- **Test specification:**
  - **Automated tests:**
    - To be done.
  - **Manual tests:**
    - To be done.
