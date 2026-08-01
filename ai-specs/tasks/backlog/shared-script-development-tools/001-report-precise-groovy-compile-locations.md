# Task: Report precise Groovy compile diagnostic locations
- **Task Identifier:** 2026-07-17-compile-location

## Original user report

> **5b. Line and column in compile diagnostics.** Compilation errors come back with
> `"line":null,"column":null`. In a ~900-line script, the line number of a syntax error
> saves a real manual hunt. The Groovy compiler has that information; surfacing it through
> the MCP diagnostics would help a lot.

- **Scope:**
  Populate line and column for each Groovy source compilation diagnostic
  wherever the compiler provides that information. Apply the result
  consistently to `compileCode` and compile-before-run responses for AI-owned
  and attached-editor scripts, with the same response contract in Freeplane's
  LangChain chat and MCP, while preserving diagnostics that genuinely have no
  source location.
- **Motivation:**
  A diagnostic message without a source position makes syntax and import
  failures expensive to locate in large scripts. The AI tool response already
  has location fields, so reliable compiler positions can remove manual
  searching without expanding script authority.
- **Analysis:**
  - `CodeStateDiagnostic` already contains nullable `line` and `column`
    fields, and the AI code-state presentation already renders them when
    present. Both tool front ends use this shared response model, so this is
    not a missing transport-specific response-schema feature.
  - `ScriptingEngine.GroovyCompileResult` currently carries only one optional
    line number for the whole compilation result and has no column field.
  - The current Groovy path derives that line from a `GroovyRuntimeException`
    AST node or from message text, then assigns it only to the first returned
    diagnostic. Some compiler failures therefore produce a null line, and
    columns cannot be propagated at all.
  - The correct improvement is to extract structured Groovy compiler
    diagnostics with their individual source positions where available, not
    to invent positions or parse every rendered message as if it were a
    stable compiler API.
