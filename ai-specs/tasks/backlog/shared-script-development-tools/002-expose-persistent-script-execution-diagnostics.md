# Task: Expose asynchronous script-execution diagnostics
- **Task Identifier:** 2026-07-17-diagnostics

## Original user report

> **What happened:** `println` goes nowhere visible, so every diagnostic has to be
> accumulated into a `StringBuilder` and returned at the end of the script. Worse — and
> this bites GUI code specifically — **exceptions thrown in asynchronous callbacks are
> invisible**. A `ComponentListener` that throws during a pan, or the `done()` of a
> `SwingWorker`, runs *after* the `runCode` call has already returned its result. There is
> no channel through which that failure ever reaches the developer.
>
> For event-driven code (which is essentially all overlay/GUI scripting), this is a total
> blind spot: the synchronous call succeeds, and the thing that actually breaks does so
> later, silently.
>
> **The suggestion:** a log buffer that captures `stdout`/`stderr` **and uncaught EDT
> exceptions** during and after execution, readable via an MCP tool. This would make
> listener/timer/worker failures debuggable at all, instead of manifesting only as "the
> panel mysteriously stops updating."

- **Scope:**
  Provide context-associated asynchronous diagnostics for interactive Groovy
  script runs through two separately selectable increments. The first increment
  uses an explicit `observe` wrapper and requires no Groovy compiler
  instrumentation. The second increment can add automatic callback
  instrumentation later without changing the explicit-wrapper contract. For
  AI-owned runs, retain bounded diagnostics readable through equivalent
  LangChain chat and MCP tools. For manual and AI-triggered attached-editor
  runs, stream asynchronous diagnostics to the script editor's result console.
  Preserve normal Freeplane logging and do not attribute unobserved application
  activity to a script run.
- **Motivation:**
  Event-driven scripts can emit output or fail after the synchronous run has
  completed. An AI caller can receive a successful `runCode` result, and a
  script-editor user can see an initially successful run, while the callback
  implementing the actual behavior has stopped without a corresponding
  diagnostic where the script is being developed. Explicit wrapping can solve
  the common case cheaply; automatic instrumentation is a separate convenience
  and coverage decision.
- **Analysis:**
  - Keep the explicit wrapper and automatic instrumentation as separate
    increments because explicit observation is independently useful and does
    not require changes to Groovy compilation or compiled-script identity.
  - The current implementation captures synchronous stdout with
    `CapturedPrintStream` and includes it in `RunCodeResponse`. That capture
    ends when the script body returns. Synchronous stderr has no equivalent
    run-specific response channel.
  - `ScriptContext` already carries a callback output stream, and
    `ScriptRunner` supplies one when an output stream is available. The script
    editor passes its persistent result stream. `ControllerProxy` currently
    restores the originating context and output only for callbacks from
    `askAi` and `runAiPrompt`.
  - Freeplane's default uncaught-exception handler writes failures to the
    application log. A global log tail cannot provide exact attribution because
    it mixes script, application, and unrelated concurrent activity.
  - Shared mechanism belongs in the script layer: execution-context capture and
    restoration, output routing, and escaping-failure reporting. The AI host
    owns bounded retained diagnostics keyed by execution identity;
    `ScriptEditorPanel` owns live console presentation and its observer
    lifecycle.
  - A callback can outlive the synchronous run and a compiled-script cache
    entry. The diagnostics registry must not retain callbacks, script
    instances, or their class loaders. A callback should retain only the
    minimal execution handle needed to reach a bounded observer.
  - A dynamically compiled `GroovyScript` owns a `ScriptClassLoader`, and its
    child `MyGroovyClassLoader` defines generated script, closure, and anonymous
    callback classes. Generated code can call context support in the parent
    script plugin.
  - `ScriptingEngine` caches compiled scripts by source, language, and
    permissions and can reuse generated classes across executions and entry
    points. Any automatic instrumentation profile must participate in cache
    identity, while execution identity must remain per run or callback rather
    than static generated-class state.
  - `GroovyScript.createCompilerConfiguration()` is also used by
    `ClasspathScriptCompiler`. Automatic interactive instrumentation must use a
    separate compiler configuration so that precompiled library and add-on
    classes remain unchanged.
  - Groovy metaclass replacement is process-wide and does not reliably
    intercept direct Java calls. `InheritableThreadLocal` does not propagate
    correctly to pre-existing EDT and executor threads. Neither is an adequate
    replacement for explicit context capture.
  - Exact attribution is limited to an observed callback boundary and work
    executed synchronously within it. Asynchronous work created entirely inside
    unobserved Java, native, external, or precompiled library code remains
    outside the guarantee.

## Subtask: Observe explicitly wrapped asynchronous callbacks
- **Status:** backlog
- **Scope:**
  Add a script-layer `observe` API that wraps a Groovy closure or supported Java
  callback value explicitly. Capture the current execution identity,
  `ScriptContext`, diagnostics observer, and defining script class loader when
  the wrapper is created. On invocation, install that state, route stdout and
  stderr, invoke the callback, record a failure escaping the outermost observed
  callback, rethrow it, and restore previous thread state in `finally`. Replace
  temporary process-wide output-stream switching with a stable scripting output
  router that selects a sink from the current execution context and otherwise
  delegates to the original application streams. Add bounded AI diagnostics
  read and clear operations with equivalent LangChain chat and MCP behavior,
  and route attached-editor observations to its live result console. Define
  execution identity, retention, clearing, cancellation, and observer disposal.
- **Motivation:**
  Explicit wrapping provides deterministic attribution for callbacks selected
  by the script author without changing Groovy compilation, script cache keys,
  or unrelated script behavior. It delivers a useful debugging path even if
  automatic instrumentation is never implemented.
- **Analysis:**
  - Use explicit wrapping only, without compiler customizers, bytecode
    rewriting, metaclass replacement, or implicit callback discovery, because
    this increment is intended to remain cheap and isolated.
  - Guarantee attribution only for explicitly observed callbacks and work
    executed synchronously within them because no context crosses another
    asynchronous boundary automatically.
  - Keep callbacks and script objects out of the diagnostics registry so that
    expired observers and closed editors cannot retain generated class loaders;
    later output falls back to normal application streams and logging.

## Subtask: Observe Groovy callbacks through automatic instrumentation
- **Status:** backlog
- **Scope:**
  Add an opt-in interactive compilation profile that automatically gives
  script-defined closures, anonymous `Runnable` or `Callable` objects,
  listeners, `SwingWorker` implementations, and callback method references the
  context behavior provided by `observe`. Apply it to AI-owned and
  attached-editor runs while retaining the explicit wrapper as an escape hatch.
  Keep formulas, ordinary menu or file scripts, and classpath or add-on
  compilation uninstrumented unless separately opted in.
- **Motivation:**
  Automatic instrumentation removes the need to remember `observe` at every
  callback registration site and broadens coverage of event-driven scripts,
  while remaining optional because it increases compiler, cache, compatibility,
  and verification complexity.
- **Analysis:**
  - Reuse explicit observation so that automatic coverage does not create a
    parallel diagnostics or output-routing path.
  - Separate interactive instrumentation from shared compiler configuration and
    include its profile in compiled-script cache identity because existing
    generated classes are reused across runs and entry points.
  - Capture state per callback instance and restore both execution context and
    the defining class loader around invocation because static run state and
    ambient thread state are not valid across cached executions.
  - Record and rethrow only failures escaping the outermost callback that
    re-enters captured context so that nested exceptions remain catchable by
    surrounding script code.
  - Exclude callbacks created entirely by uninstrumented Java, native,
    external, or precompiled code because their creation boundary is not
    visible to the Groovy compiler.
