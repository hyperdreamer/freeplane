# Task: Evaluate a throwaway script in one AI-tool call
- **Task Identifier:** 2026-07-17-evaluate

## Original user report

> **5a. A one-shot `evaluate(sourceText)`.** Today the cycle is `readCode` (to get the
> state token) → `writeCode` (with token) → `runCode` (with token). During development I
> hit a state-token fingerprint mismatch once and had to re-read. For fast iteration, a
> single "compile and run this source now" would collapse three calls into one. The token
> guards against concurrent edits and makes sense in general — perhaps it could be
> optional for throwaway evaluation.

- **Scope:**
  Add a shared one-shot AI tool that accepts Groovy source and arguments, then
  compiles and runs that source atomically using the same selected-node
  resolution, authorization, permission, diagnostics, output, and result rules
  as AI-owned `runCode`. Expose the same request contract and behavior
  through Freeplane's LangChain chat and MCP. Do not read, replace, or otherwise
  mutate the persistent AI-owned or attached-editor code state.
- **Motivation:**
  Short development probes and disposable tests do not need a persistent code
  draft. Requiring callers to synchronize, replace, and then run host state
  adds tool round trips and exposes throwaway evaluation to avoidable state
  token conflicts.
- **Analysis:**
  - The current shared tool set deliberately separates persistent `readCode`,
    `writeCode`, `compileCode`, and `runCode` operations.
  - A normal repeated edit does not always require three calls: `writeCode`
    returns the token needed by `runCode`. A preceding `readCode` is needed
    when the caller lacks the current token or must inspect existing state.
  - Making tokens optional on persistent code would weaken concurrent-edit
    protection. A separate stateless evaluation operation removes ceremony
    without weakening the stateful host contract.
  - The operation must reuse the existing script permission and result
    conversion paths; a second evaluator with different safety or output
    semantics would create an unsafe parallel execution path.
