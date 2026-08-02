# Task: Report precise Groovy compile diagnostic locations
- **Task Identifier:** 2026-07-17-compile-location

## Subtask: Report precise Groovy compile diagnostic locations
- **Status:** review
- **Scope:**
  Populate line and column for each Groovy source compilation
  diagnostic wherever the compiler provides that information. Apply the
  result consistently to `compileCode` and compile-before-run
  responses for AI-owned and attached-editor scripts. In the same
  increment, expose compiler failures directly in the AI-owned script
  dialog and avoid presenting the same compiler-detail payload to AI
  twice through separate response or summary channels. Preserve
  diagnostics that genuinely have no source location. In the approved
  follow-up on this task, also reuse structured formula compile
  diagnostics for attached-formula `compileCode`, formula
  submit-validation compile failures, and formula AI-repair state so
  compile failures are not duplicated there either. In a separate
  follow-up subtask, align formula-internal Groovy source layout with
  visible formula text by replacing the stripped leading `=` with a
  space during formula-text-to-Groovy conversion.
- **Motivation:**
  The current behavior has three related defects. First, locations are
  lost and large scripts return `"line":null,"column":null`.
  Second, AI-owned user runs can fail compilation without showing the
  user a direct result in the dialog, while AI-facing summaries can
  receive the same compiler detail twice. Third, attached-formula
  compile diagnostics on line 1 currently refer to the stripped Groovy
  body rather than the visible formula text, so first-line columns are
  off by one in the editor.
- **Scenario:**
  - A developer compiles a Groovy script through `compileCode` or
    triggers compile-before-run validation through `runCode`.
  - If Groovy reports one or more source diagnostics, each returned
    `CodeStateDiagnostic` carries that diagnostic's own line and
    column.
  - If Groovy reports a diagnostic without a source position, the
    response keeps `line` and `column` as `null` instead of guessing.
  - If an AI-owned dialog run fails during compilation, the dialog
    shows the compiler failure directly without requiring AI chat
    follow-up.
  - AI-facing summaries include the detailed compiler diagnostics once,
    not repeated as the same full text through another field.
- **Constraints:**
  - Preserve `null` line and column when the compiler genuinely does
    not provide a source position.
  - Keep AI-owned and attached-editor script diagnostics aligned on
    the same response contract.
  - Do not recover locations by parsing rendered diagnostic text as if
    it were the stable compiler API.
  - Do not treat compile failures as `stdout` or invent a compile-time
    `stderr` channel inside the existing response model.
  - Avoid showing the same full compiler-detail payload to AI twice.
  - Apply the formula leading-`=` replacement only when converting
    formula text to Groovy source; do not alter raw Groovy contexts
    such as filter conditions that do not strip a formula prefix.
  - Keep formula compile and formula execution on the same
    formula-text-to-Groovy conversion so their source coordinates do
    not diverge.
- **Briefing:**
  AI-owned compile and compile-before-run validation go through
  `AiOwnedScriptHostService.validate(...)`. Attached-editor compile and
  compile-before-run validation go through
  `ScriptEditorPanel.compileCode(...)` and
  `ScriptEditorPanel.runCode(...)`. AI-owned user runs are presented
  through `AiOwnedScriptDialog`, and AI chat follow-up summaries use
  `AutomaticCodeStatusMessage`. In the approved follow-up on this
  task, `FormulaEditor.compileCode(...)`,
  `FormulaEditor.submitEditedText(...)`, and
  `FormulaValidationSupport.validateFormula(...)` also participate so
  attached-formula compile failures and formula submit-validation
  failures reuse the same compile diagnostics without repeating the
  same compiler detail.
- **Research:**
  ```plantuml
  @startuml
  set separator none
  package org.freeplane {
    package plugin.script {
      class ScriptingEngine {
        {static} compileGroovyScriptForDiagnostics(script, permissions): GroovyCompileResult
      }
      class "ScriptingEngine.GroovyCompileResult" as GroovyCompileResult {
        +successful: boolean
        +compilerDiagnostics: List<String>
        +errorMessage: String
        +lineNumber: Integer
      }
      class ScriptEditorPanel {
        +compileCode(request): CompileCodeResponse
        -runCode(request, runInitiator): RunCodeResponse
        -renderManualRunResult(response)
      }
    }
    package plugin.script.ai {
      class AiOwnedScriptHostService {
        -validate(content, stateToken, permissions): ValidationOutcome
        -runFromDialog(content): RunCodeResponse
      }
      class AiOwnedScriptDialog {
        +showCode()
        +showAndFocus()
      }
    }
    package plugin.ai.chat.memory {
      class AutomaticCodeStatusMessage {
        {static} forRunResponse(response)
      }
    }
    package features.ai.code {
      class CodeStateDiagnostics {
        {static} sourceDiagnostics(messages, line): List<CodeStateDiagnostic>
      }
      class CodeStateDiagnostic {
        +field: CodeStateField
        +message: String
        +line: Integer
        +column: Integer
      }
      class CompileCodeResponse {
        +diagnostics: List<CodeStateDiagnostic>
        +errorMessage: String
      }
      class RunCodeResponse {
        +diagnostics: List<CodeStateDiagnostic>
        +errorMessage: String
        +stdout: String
      }
    }
  }
  ScriptingEngine --> GroovyCompileResult
  AiOwnedScriptHostService --> ScriptingEngine
  AiOwnedScriptHostService --> CodeStateDiagnostics
  AiOwnedScriptDialog --> AiOwnedScriptHostService
  ScriptEditorPanel --> ScriptingEngine
  ScriptEditorPanel --> CodeStateDiagnostics
  AutomaticCodeStatusMessage --> RunCodeResponse
  CodeStateDiagnostics --> CodeStateDiagnostic
  @enduml
  ```

  ```plantuml
  @startuml
  participant User
  participant AiOwnedScriptDialog as Dialog
  participant AiOwnedScriptHostService as Host
  participant ScriptingEngine as Engine
  participant "CodeStateDiagnostics.sourceDiagnostics" as SharedLineMapper
  participant AutomaticCodeStatusMessage as ChatSummary

  User -> Dialog : click Run
  Dialog -> Host : runFromDialog(currentContent)
  Host -> Engine : compileGroovyScriptForDiagnostics(sourceText, permissions)
  Engine --> Host : GroovyCompileResult(messages, lineNumber, errorMessage)
  Host -> SharedLineMapper : sourceDiagnostics(messages, lineNumber)
  SharedLineMapper --> Host : first diagnostic gets line\ncolumn stays null
  Host --> Dialog : RunCodeResponse(INVALID_SCRIPT, diagnostics, errorMessage, stdout=null)
  Dialog --> User : no direct compile result shown
  Host -> ChatSummary : later user-run follow-up may format response
  ChatSummary --> User : diagnostics + repeated errorMessage
  @enduml
  ```

  - Freeplane uses `org.apache.groovy:groovy-all:4.0.27`.
  - Groovy 4 already exposes structured compiler messages through
    `MultipleCompilationErrorsException`, `ErrorCollector`,
    `SyntaxErrorMessage`, `LocatedMessage`, and `SyntaxException`.
  - `ScriptingEngine.GroovyCompileResult` currently exposes compiler
    messages, one optional `lineNumber`, and no column field.
  - `CodeStateDiagnostics.sourceDiagnostics(...)` converts source
    messages into `CodeStateDiagnostic` values with a shared line,
    always `null` column, and assigns the shared line only to the
    first emitted diagnostic.
  - `CompileCodeResponse` has no `stdout` field. `RunCodeResponse` and
    `ReadCodeResponse` do have `stdout`, but compile failures happen
    before script execution and currently return `stdout = null`.
  - For current Groovy compile failures, `diagnostics` and
    `errorMessage` both carry the same full compiler-detail body in
    different channels.
  - `AiOwnedScriptDialog` currently has no output or result surface.
    After `runFromDialog(...)` returns `INVALID_SCRIPT`, it reloads the
    code but does not render diagnostics or `errorMessage`.
  - `AutomaticCodeStatusMessage` currently appends both diagnostics and
    `errorMessage`. For compile failures this repeats the compiler
    detail in AI chat follow-up.
  - `ScriptEditorPanel` manual runs already show compile or run failure
    text to the user through `errorMessage` in the result pane or
    error dialog.
  - Reproduction with an AI-owned script containing
    `if (x > y { ... }` returned `codeState=INVALID_SCRIPT` with
    `diagnostics[0].line = null` and `diagnostics[0].column = null`
    even though the compiler message contains
    `@ line 7, column 1`.
  - Reproduction with an AI-owned script containing
    `import does.not.Exist` returned `diagnostics[0].line = null` and
    `diagnostics[0].column = null` even though the compiler message
    contains `@ line 1, column 1`.
  - After the user clicked Run for the AI-owned script,
    `readCode(host=AI)` returned `codeState=INVALID_SCRIPT` with
    `runInitiator=USER`, and the diagnostic still had `line = null`
    and `column = null` even though the compiler message includes
    `@ line 1, column 1` and `@ line 2, column 1`. This confirms the
    compile-before-run path preserves the same missing-location bug.
  - The attached-editor host is not readable at the current
    availability level, so direct attached-editor reproduction in
    tools is blocked here. Source inspection still shows the attached
    editor compile and run paths use the same
    `CodeStateDiagnostics.sourceDiagnostics(...)` call.
- **Analysis:**
  - Return one structured diagnostic per Groovy compiler `Message`
    because per-message source position is the stable boundary the
    callers need.
  - Make structured diagnostics the authoritative AI-facing detailed
    channel for compiler failures.
  - Keep `errorMessage` only as a short aggregate summary or fallback,
    not as a second full compiler-detail dump.
  - Keep Groovy compiler-message extraction in `ScriptingEngine`, keep
    code-state mapping in a script-plugin adapter, and keep user-facing
    text rendering in explicit presentation helpers or views.
  - Keep `stdout` for actual script output only; compile failures are
    validation results, not execution output.
  - Render AI-owned dialog compile failures locally from the response
    instead of relying on later AI chat summaries.
- **Design:**
  ```plantuml
  @startuml
  set separator none
  package org.freeplane {
    package plugin.script {
      class ScriptingEngine {
        {static} compileGroovyScriptForDiagnostics(script, permissions): GroovyCompileResult
      }
      class "ScriptingEngine.GroovyCompileResult" as GroovyCompileResult {
        +successful: boolean
        +compilerDiagnostics: List<GroovyCompilerDiagnostic>
        +errorMessage: String
      }
      class "ScriptingEngine.GroovyCompilerDiagnostic" as GroovyCompilerDiagnostic {
        +message: String
        +line: Integer
        +column: Integer
      }
      class GroovyCompilerDiagnosticsMapper {
        {static} toSourceDiagnostics(diagnostics): List<CodeStateDiagnostic>
      }
      class CodeStateDiagnosticTextFormatter {
        {static} format(diagnostics): String
      }
      class ScriptEditorPanel {
        +compileCode(request): CompileCodeResponse
        -runCode(request, runInitiator): RunCodeResponse
        -renderManualRunResult(response)
      }
    }
    package plugin.script.ai {
      class AiOwnedScriptHostService {
        -validate(content, stateToken, permissions): ValidationOutcome
        -runFromDialog(content): RunCodeResponse
      }
      class AiOwnedScriptDialog {
        +showCode()
        +showAndFocus()
        -showRunFailure(response)
      }
    }
    package plugin.ai.chat.memory {
      class AutomaticCodeStatusMessage {
        {static} forRunResponse(response)
      }
    }
    package features.ai.code {
      class CodeStateDiagnostic {
        +field: CodeStateField
        +message: String
        +line: Integer
        +column: Integer
      }
    }
  }
  ScriptingEngine --> GroovyCompileResult
  GroovyCompileResult --> GroovyCompilerDiagnostic
  GroovyCompilerDiagnosticsMapper --> GroovyCompilerDiagnostic
  GroovyCompilerDiagnosticsMapper --> CodeStateDiagnostic
  CodeStateDiagnosticTextFormatter --> CodeStateDiagnostic
  AiOwnedScriptHostService --> ScriptingEngine
  AiOwnedScriptHostService --> GroovyCompilerDiagnosticsMapper
  AiOwnedScriptDialog --> CodeStateDiagnosticTextFormatter
  ScriptEditorPanel --> ScriptingEngine
  ScriptEditorPanel --> GroovyCompilerDiagnosticsMapper
  ScriptEditorPanel --> CodeStateDiagnosticTextFormatter
  AutomaticCodeStatusMessage --> CodeStateDiagnosticTextFormatter
  @enduml
  ```

  ```plantuml
  @startuml
  participant User
  participant AiOwnedScriptDialog as Dialog
  participant AiOwnedScriptHostService as Host
  participant ScriptingEngine as Engine
  participant GroovyCompilerDiagnosticsMapper as Mapper
  participant CodeStateDiagnosticTextFormatter as Formatter
  participant AutomaticCodeStatusMessage as ChatSummary

  User -> Dialog : click Run
  Dialog -> Host : runFromDialog(currentContent)
  Host -> Engine : compileGroovyScriptForDiagnostics(sourceText, permissions)
  Engine --> Engine : extract one GroovyCompilerDiagnostic\nper Groovy compiler Message
  Engine --> Host : GroovyCompileResult(diagnostics, short errorMessage)
  Host -> Mapper : toSourceDiagnostics(diagnostics)
  Mapper --> Host : CodeStateDiagnostic(message, line, column)*
  Host --> Dialog : RunCodeResponse(INVALID_SCRIPT, diagnostics, short errorMessage, stdout=null)
  Dialog -> Formatter : format(diagnostics)
  Formatter --> Dialog : plain-text diagnostics for display
  Dialog --> User : direct compile failure output
  Host -> ChatSummary : later user-run follow-up may format response
  ChatSummary --> User : diagnostics once\nerrorMessage only if non-duplicate fallback
  @enduml
  ```

  - Add `ScriptingEngine.GroovyCompilerDiagnostic` as the exact
    per-diagnostic value type with `message`, `line`, and `column`.
  - Change `ScriptingEngine.GroovyCompileResult` to carry
    `List<GroovyCompilerDiagnostic>` and remove the shared
    `lineNumber` field.
  - In `ScriptingEngine.compileGroovyScriptForDiagnostics(...)`, when
    `ExecuteScriptException.getCause()` is a
    `MultipleCompilationErrorsException`, iterate
    `getErrorCollector().getErrors()` and build one
    `GroovyCompilerDiagnostic` per Groovy `Message`.
  - Render each diagnostic message from the individual Groovy
    `Message.write(...)` output, trimmed for response use, so the
    message text stays compiler-authored while line and column come
    from typed APIs.
  - Extract positions without message parsing:
    - `SyntaxErrorMessage` uses
      `getCause().getStartLine()` and `getCause().getStartColumn()`.
    - `LocatedMessage` uses
      `getContext().getStartLine()` and `getContext().getStartColumn()`.
    - `SimpleMessage`, `ExceptionMessage`, and unknown Groovy
      `Message` implementations keep `line = null` and
      `column = null` unless another typed source position is directly
      available.
  - For non-collector compilation failures, keep the current aggregate
    fallback path but set `errorMessage` to a short summary such as a
    compile-failed summary string rather than repeating the whole
    compiler-detail body when structured diagnostics are present.
  - Add `org.freeplane.plugin.script.GroovyCompilerDiagnosticsMapper`
    as the shared adapter from
    `List<GroovyCompilerDiagnostic>` to
    `List<CodeStateDiagnostic>`.
  - Add a shared plain-text formatter for
    `List<CodeStateDiagnostic>` so user-facing and AI-summary surfaces
    can render the same structured diagnostics consistently.
  - Update `AiOwnedScriptHostService.validate(...)`,
    `ScriptEditorPanel.compileCode(...)`,
    `ScriptEditorPanel.runCode(...)`, and
    `FormulaEditor.compileCode(...)` to use the mapper instead of
    `CodeStateDiagnostics.sourceDiagnostics(...)` for
    `ScriptingEngine.GroovyCompileResult`.
  - Update `AiOwnedScriptDialog` to show compile failures directly from
    response diagnostics and summary text after `runFromDialog(...)`
    returns a non-success result.
  - Update `AutomaticCodeStatusMessage` so when structured diagnostics
    are present it does not append the same full compiler-detail text a
    second time through `errorMessage`. Render `errorMessage` only when
    diagnostics are absent or when it contributes distinct fallback
    information.
  - Update attached-editor failure presentation so if `errorMessage`
    becomes summary-only, detailed compiler text still comes from the
    structured diagnostics formatter rather than from a duplicated raw
    compiler dump.
  - Leave `CodeStateDiagnostics.sourceDiagnostics(List<String>, Integer)`
    in place for existing line-only callers that are outside this
    task's Groovy compile-diagnostic scope, such as formula
    validation results.
- **Test specification:**
  - **Automated tests:**
    - `ScriptingEngineTest`
      - `compileGroovyScriptForDiagnosticsReturnsLineAndColumnForSyntaxError`:
        a syntax error produces a diagnostic with the compiler's own
        line and column.
      - `compileGroovyScriptForDiagnosticsReturnsSeparateLocationsForMultipleImportErrors`:
        multiple unresolved imports produce separate diagnostics with
        their own locations.
      - `compileGroovyScriptForDiagnosticsUsesSummaryErrorMessageWhenStructuredDiagnosticsExist`:
        compiler failures with structured diagnostics keep a short
        summary in `errorMessage` instead of repeating the full detail.
    - `GroovyCompilerDiagnosticsMapperTest`
      - `toSourceDiagnosticsPreservesNullLocations`: a structured
        diagnostic without a source position maps to a
        `CodeStateDiagnostic` with `line = null` and `column = null`.
    - `CodeStateDiagnosticTextFormatterTest`
      - `formatIncludesLineAndColumnWhenPresent`: formatted diagnostic
        text includes exact line and column.
      - `formatOmitsMissingLocations`: diagnostics without source
        position render without invented locations.
    - `AiOwnedScriptHostServiceTest`
      - `compileCodeReturnsGroovyDiagnosticLocations`: AI-owned
        `compileCode` returns per-diagnostic line and column.
      - `runFromDialogReturnsGroovyDiagnosticLocationsForCompileFailure`:
        AI-owned user-run compile-before-run returns the same
        per-diagnostic locations.
    - `ScriptEditorPanelTest`
      - `compileCodeReturnsGroovyDiagnosticLocations`: attached-editor
        `compileCode` returns the same per-diagnostic locations.
      - `runCodeReturnsGroovyDiagnosticLocationsForCompileFailure`:
        attached-editor `runCode` returns the same per-diagnostic
        locations on compile-before-run failure.
      - `renderManualRunResultShowsFormattedDiagnosticsForCompileFailure`:
        attached-editor manual-run output still shows detailed compiler
        failure text when `errorMessage` is summary-only.
    - `AutomaticCodeStatusMessageTest`
      - `formatRunResponseDoesNotRepeatCompilerDetailWhenDiagnosticsExist`:
        AI follow-up text includes detailed compiler diagnostics once.
    - `FormulaEditorTest`
      - `compileCodeReturnsGroovyDiagnosticLocations`: attached-formula
        `compileCode` returns structured Groovy diagnostics with line
        and column.
      - `buildValidationFailureMessageShowsDiagnosticsWithoutDuplicatingSummary`:
        formula submit-validation compile failures render diagnostics
        once instead of showing the same compiler detail both as the
        main message and again under a diagnostics section.
    - `FormulaValidationSupportTest`
      - `validateFormulaReturnsCompilerDiagnosticsSummaryWithoutExecutingValidator`:
        formula submit validation returns compiler diagnostics plus a
        summary-only `errorMessage` and does not execute the validator
        after compile failure.
- **Implementation notes:**
  - **Tradeoffs:**
    - Placed `CodeStateDiagnosticTextFormatter` in
      `org.freeplane.features.ai.code` instead of the script plugin so
      both the script plugin and the AI plugin can share one formatter
      without introducing a plugin dependency from AI to script.
    - Kept formula compile diagnostics in Groovy compiler coordinate
      space instead of shifting first-line columns to count the leading
      `=`. The structured fields and the compiler-authored message body
      stay consistent that way; shifting only the structured fields
      would make the two disagree.
  - **Interpretations:**
    - Kept detailed compiler text in structured diagnostics and used
      `errorMessage` only as a short summary for Groovy compile
      failures. `AutomaticCodeStatusMessage` omits that summary when
      detailed diagnostics are already present.
    - Extracted attached-editor compile preflight into package-visible
      `ScriptEditorPanel` helpers so tests can cover the compile and
      compile-before-run failure mapping without `Unsafe` or Swing
      dialog construction.
    - Reused attached-formula compile preflight during formula submit
      validation so compile failures now feed the same structured
      diagnostics into attached-editor `compileCode`, the formula
      validation dialog, and the AI repair state.

## Subtask: Align formula source coordinates with visible formula text
- **Status:** review
- **Scope:**
  Replace the leading formula marker `=` with one space in the Groovy
  source returned by `FormulaUtils.scriptOf(String)`. Use that shared
  conversion for attached-formula compile preflight, formula
  submit-validation preflight, formula execution, and the formula
  dependency and cache identity paths that currently use stripped
  formula text. Leave raw Groovy sources unchanged, including script
  filter conditions and other contexts that pass source directly to
  `ScriptingEngine`.
- **Motivation:**
  The current formula compile path reports first-line coordinates in
  stripped-script space, so attached-formula diagnostics can point one
  column left of the visible editor text. Replacing the marker instead
  of removing it makes compiler coordinates match the visible formula
  text while keeping compile, validation, execution, dependency
  tracking, and cache identity on one source layout.
- **Scenario:**
  - For visible formula text beginning with `=`, the formula's Groovy
    source has the same length and line breaks, with a space in place of
    the visible marker.
  - A first-line compiler diagnostic reports the column occupied by the
    corresponding visible formula text. Later-line line and column
    values remain unchanged.
  - Attached-formula compile, submit validation, and execution use the
    same converted source. Formula dependency and cache lookups use the
    same converted source as formula execution.
  - Raw Groovy contexts, including script filter conditions, retain
    their original source and coordinate layout.
- **Constraints:**
  - Change only formula-text-to-Groovy conversion, not raw Groovy
    script contexts.
  - Preserve formula behavior, source fingerprints, and line breaks
    apart from first-line source-column alignment.
  - Keep one conversion point; do not shift diagnostic coordinates
    after compilation or apply separate per-caller offsets.
  - Verify formula execution, validation, dependency tracking, and
    cache matching after the source-layout change.
- **Briefing:**
  `FormulaUtils.scriptOf(String)` is the current shared conversion
  point. `FormulaEditor.compileFormulaCodeStateContent(...)` and
  `FormulaValidationSupport.validateFormula(...)` call it before
  compiler preflight. `FormulaUtils.evalIfScript(...)` and
  `FormulaUtils.validateFormula(...)` use it before execution and
  validation. `FormulaUtils.getRelatedElements(...)` and
  `NodeScript.scriptIsContainedIn(...)` use the resulting script for
  dependency and cache identity. `ScriptCondition` constructs a
  `GroovyScript` directly from its raw filter source and does not use
  `FormulaUtils.scriptOf(...)`. The existing sibling subtask already
  provides structured compiler diagnostics; this subtask changes the
  source coordinates supplied to that existing contract.
- **Research:**
  ```plantuml
  @startuml
  participant "Visible formula text" as Text
  participant FormulaEditor as Editor
  participant FormulaValidationSupport as Validation
  participant FormulaUtils as Utils
  participant ScriptingEngine as Engine
  participant NodeScript as ScriptKey
  participant FormulaCache as Cache
  participant ScriptCondition as Filter
  Text -> Editor : compileFormulaCodeStateContent(formulaText)
  Editor -> Utils : scriptOf(formulaText)
  Utils --> Editor : formulaText.substring(1)
  Editor -> Engine : compile(stripped formula source)
  Validation -> Utils : scriptOf(formulaText)
  Utils --> Validation : formulaText.substring(1)
  Validation -> Engine : compile(stripped formula source)
  Utils -> Engine : execute(stripped formula source)
  Utils -> ScriptKey : store stripped formula source
  ScriptKey -> Cache : use stripped source as cache key
  Filter -> Engine : compile raw Groovy source
  @enduml
  ```

  - `FormulaUtils.scriptOf(String)` currently returns
    `object.substring(1)`, so it removes the visible formula marker and
    shifts every first-line column left by one.
  - The identified formula paths already converge on that method:
    `FormulaEditor.compileFormulaCodeStateContent(...)`,
    `FormulaValidationSupport.validateFormula(...)`,
    `FormulaUtils.evalIfScript(...)`,
    `FormulaUtils.validateFormula(...)`, and
    `FormulaUtils.getRelatedElements(...)`. `NodeScript` compares its
    stored script with `FormulaUtils.scriptOf(...)`, while
    `FormulaCache` uses the resulting `NodeScript.script` for cache
    reads and writes.
  - `ScriptingEngine` compiles and executes the source it receives; it
    has no formula-prefix knowledge. `ScriptCondition` passes raw
    filter source directly to `GroovyScript`, so it is outside the
    formula conversion path.
  - `FormulaValidationSupport` keeps the visible formula text for its
    source fingerprint and only converts the formula source passed to
    compile and validation. The fingerprint therefore does not need a
    coordinate-related change.
  - The existing `FormulaEditorTest.compileCodeReturnsGroovyDiagnosticLocations`
    covers a multiline formula with unresolved imports and currently
    expects first-line column `1`; the first-line expectation must become
    column `2` while the second-line column remains `1`.
- **Analysis:**
  - Use `FormulaUtils.scriptOf(String)` as the single conversion point
    because all in-scope formula paths already use it.
  - Replace the marker in the source rather than shifting diagnostic
    fields after compilation so compiler-authored text, structured
    coordinates, and formula execution share one source layout.
  - Keep `ScriptingEngine` formula-agnostic so normalization cannot
    leak into raw Groovy callers.
- **Design:**
  ```plantuml
  @startuml
  participant "Visible formula text" as Text
  participant FormulaEditor as Editor
  participant FormulaValidationSupport as Validation
  participant FormulaUtils as Utils
  participant ScriptingEngine as Engine
  participant NodeScript as ScriptKey
  participant FormulaCache as Cache
  participant ScriptCondition as Filter
  Text -> Editor : compileFormulaCodeStateContent(formulaText)
  Editor -> Utils : scriptOf(formulaText)
  Utils --> Editor : same-length source with first char space
  Editor -> Engine : compile(formula source)
  Validation -> Utils : scriptOf(formulaText)
  Utils --> Validation : same-length source with first char space
  Validation -> Engine : compile(formula source)
  Utils -> Engine : execute(formula source)
  Utils -> ScriptKey : store same formula source
  ScriptKey -> Cache : read and write same source key
  Filter -> Engine : compile unchanged raw Groovy source
  @enduml
  ```

  - Change only the implementation of
    `FormulaUtils.scriptOf(String)`: for the inputs accepted by the
    existing callers, return a string whose first character is a space
    and whose remaining characters are unchanged. Preserve the source
    length and all line breaks.
  - Keep the existing callers on that method. Compile preflight in
    `FormulaEditor` and `FormulaValidationSupport`, formula execution
    and validation in `FormulaUtils`, and dependency/cache identity in
    `FormulaUtils` and `NodeScript` will then receive the same source
    layout without caller-specific coordinate arithmetic.
  - Do not change `ScriptingEngine`, `GroovyScript`, or
    `ScriptCondition` for formula normalization. They continue to
    operate on the source supplied by their callers; raw Groovy callers
    remain unchanged.
  - Keep `FormulaValidationSupport` fingerprints based on visible
    formula text. Keep formula results, validation side effects, and
    dependency relationships unchanged; only the source character at
    the former marker position changes.
  - A first-line diagnostic column increases by one because Groovy now
    sees the replacement space. Line numbers, later-line columns, and
    diagnostics without a source position are unchanged.
- **Test specification:**
  - **Automated tests:**
    - `FormulaUtilsTest`
      - `scriptOfReplacesLeadingFormulaMarkerWithSpaceWithoutChangingLength`:
        converting `=line1\nline2` produces ` line1\nline2`, preserving
        source length, line breaks, and every character after the marker.
      - `evalIfScriptPreservesFormulaResultAfterSourceLayoutChange`:
        a valid formula still evaluates to the same value after the
        marker is replaced by a space.
      - `nodeScriptUsesReplacedMarkerForFormulaIdentity`:
        a `NodeScript` created with the converted formula source matches
        the visible formula text through `scriptIsContainedIn`, covering
        the identity used by dependency and cache lookup paths.
    - `FormulaEditorTest`
      - `compileCodeReturnsGroovyDiagnosticLocationsAlignedWithVisibleFormulaText`:
        for `=import a.A\nimport b.B\n1`, the first unresolved-import
        diagnostic reports line `1`, column `2`, and the second reports
        line `2`, column `1`.
    - `FormulaValidationSupportTest`
      - `validateFormulaReturnsCompilerDiagnosticsSummaryWithoutExecutingValidator`:
        the submit preflight still returns the short compilation summary,
        does not invoke the validator, and its compiler-authored
        diagnostics report the visible coordinates (line `1`, column
        `2`, and line `2`, column `1`).
    - `FormulaUtilsValidationTest`
      - `validateFormulaDoesNotPopulatePersistentCacheOrDependencyState`:
        formula validation still returns the expected value without
        populating persistent formula cache or dependency state after
        the source conversion change.
- **Implementation notes:**
  - **Interpretations:**
    - Treated `FormulaUtils.scriptOf(...)` as the complete formula-only
      source-conversion boundary because every in-scope formula caller
      already uses it, while raw Groovy callers pass source directly.
  - **Tradeoffs:**
    - Replaced the marker with a space at the shared conversion point
      instead of adding caller-specific diagnostic offsets, preserving
      source length and letting Groovy produce the aligned coordinates.
