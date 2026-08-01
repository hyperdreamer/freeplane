# Task: Run asynchronous script continuations after settling
- **Task Identifier:** 2026-07-17-settle

## Original user report

> **What happened:** a `runCode` executes **on the EDT**, so you cannot wait for a
> `SwingWorker` to finish within the same execution — `Thread.sleep` blocks the very EDT
> that would run its `done()`. I had to split every asynchronous test into **two separate
> `runCode` calls** and rely on the worker completing in the gap between them (e.g. "call 1
> opens the panel and fires the search; call 2, seconds later, inspects the 500 results and
> closes the panel").
>
> The same limitation showed up testing `scrollNodeToCenter` (slow/animated scroll) and the
> live-search debounce timer: I could only observe the *final* state by forcing the
> animation off, never the intermediate behavior, because sleeping to wait would freeze
> the animation.
>
> **The suggestion:** a mode that says "run this, and when the EDT next goes idle (or after
> N ms of quiescence), run this continuation and return its result." That would make
> `SwingWorker`, `Timer`, and animated scrolling verifiable deterministically in a single
> round-trip, instead of a fragile two-call dance.

- **Scope:**
  Add a shared `runCode` orchestration mode that runs an initial script phase,
  yields the EDT so scheduled work can proceed, waits off the EDT for a defined
  and bounded settling condition, then runs a continuation as part of the same
  authorized orchestration and returns its result in one AI-tool round trip.
  Expose the same request contract and behavior through Freeplane's LangChain
  chat and MCP. Include timeout, cancellation, and failure reporting for both
  phases.
- **Motivation:**
  `SwingWorker`, Swing `Timer`, debounce, and animated UI behavior cannot be
  inspected by sleeping inside the current script run because that blocks the
  EDT work being tested. Splitting setup and inspection across independent AI
  tool calls requires a guessed delay and introduces avoidable timing races.
- **Analysis:**
  - `AiOwnedScriptHostService` executes `runCode` on the EDT through
    `invokeAndWait`, confirming that `Thread.sleep` inside the script prevents
    EDT callbacks and painting from progressing.
  - The existing MCP request thread can wait without blocking the EDT. Its
    user-run completion path already uses this pattern with a latch and a Swing
    timer, so asynchronous orchestration is feasible. The orchestration cannot
    remain in the MCP adapter; it must be shared with LangChain chat.
  - "The EDT is idle" is not sufficient to prove that a worker, debounce
    timer, or animation has completed. The settling contract needs an
    observable completion condition, bounded quiescence rule, or explicit
    delay plus timeout before this task can be designed precisely.
  - The orchestration must retain authorization, arguments, permissions, state
    token, cancellation, and diagnostics association across both phases. It
    must not freeze or restore map-view or node selection; each phase observes
    normal Freeplane state when it executes, using existing `runCode` context
    resolution rather than a new public map/view targeting contract.
