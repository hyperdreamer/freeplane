# Task: Show visible run state in AI-owned script dialog
- **Task Identifier:** 2026-08-02-script-run-feedback
- **Scope:**
  Update the AI-owned script dialog so clicking Run visibly enters a
  running state immediately, disables the run control while
  compilation or execution is in progress, and shows success or
  failure when the attempt completes. Reset the control back to Run
  for a later attempt so the user does not have to infer state only
  from chat-side follow-up.
- **Motivation:**
  Today clicking Run can appear to do nothing. The dialog's current
  interaction gives little or no visible confirmation that work
  started, is still in progress, or has already failed.
- **Constraints:**
  - The running state must become visible before compile or execution
    work blocks the dialog; a label change that never repaints does
    not satisfy the task.
  - Either forcing the visible state change to paint immediately or
    deferring the actual run with `invokeLater` is acceptable.
    Choose the smaller reliable change.
  - Preserve current AI-owned user-run semantics unless later
    planning explicitly changes them, including user-initiated
    execution and chat follow-up messages.
  - Exclude unrelated compile-diagnostic work and broader script
    execution redesign.
