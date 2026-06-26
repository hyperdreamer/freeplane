# Task: Replace SecurityManager-based script and formula sandbox

- **Task Identifier:** 2026-06-26-sandbox
- **Scope:**
  Replace Freeplane's `SecurityManager`-based Groovy script/formula
  sandbox across all existing execution paths that use the shared
  permission model. The replacement must let Freeplane run on Java 25
  without weakening protection for untrusted mind maps. Preserve direct
  Java-object sharing and in-process execution unless implementation
  evidence falsifies that path.

  This task is now split into implementation subtasks. Each subtask must
  remain coherent if released without later siblings. Java 25 script and
  formula support must not be claimed until the final replacement path is
  complete and verified.
- **Motivation:**
  Freeplane lets mind maps contain executable Groovy scripts and
  formulas. Opening or using an untrusted map can therefore execute
  attacker-controlled JVM code. The current mitigation relies on Java
  `SecurityManager`, `Policy`, `ProtectionDomain`, `Permission`, and
  `AccessController`. On Java 24+ the security manager is no longer a
  usable enforcement mechanism; on checked Zulu Java 25.0.3 the classes
  still exist but `System.setSecurityManager(...)` throws
  `UnsupportedOperationException`, and `-Djava.security.manager=allow`
  fails during VM initialization. Freeplane needs a replacement before it
  can safely support Java 25.
- **Scenario:**
  A user opens a mind map from an untrusted source. The map contains a
  formula, script filter, or embedded script that tries to read or write
  files, open network connections, execute processes, load native code,
  exit the JVM, mutate process-wide state, load helper code, define
  bytecode, use reflection or method handles to reach restricted APIs,
  or register persistent callbacks that later perform those actions.
  Freeplane itself must remain usable, including mediated loading of
  maps, templates, documentation, icons, and approved user resources, but
  code executing as the untrusted script/formula/filter must be blocked
  unless the corresponding Freeplane-owned permission or mediated
  operation applies.
- **Constraints:**
  - Treat untrusted mind maps as capable of containing malicious Groovy
    scripts, formulas, and script filters.
  - The attacker can use Java and Groovy APIs available to script code,
    reflection, method handles, dynamic class loading, in-memory class
    definition, existing classpath classes, generated Groovy code, and
    Grape when allowed.
  - The attacker cannot install plugins, modify the Freeplane
    installation, or modify the user's configured script classpath or
    Freeplane user `lib` jars under restricted execution.
  - Preserve direct Java-object sharing as the first accepted path.
    Out-of-process isolation or a restricted/proxied scripting API is a
    fallback only if implementation evidence falsifies the in-process
    design.
  - Do not add an unsafe Java 24+/Java 25 script/formula mode.
  - Raise the implementation baseline for this replacement to Java 17.
    Java 8 compatibility is not retained as a hard constraint.
  - Keep Groovy 4.0.27 for the first replacement path unless
    implementation testing proves a Java 25 incompatibility.
  - Do not rely on Groovy's or Java's `SecurityManager`-dependent checks
    for enforcement.
  - Preserve existing trusted local-code behavior for configured script
    classpath entries and Freeplane user `lib` jars.
  - Treat Grape-loaded code as script-origin code, not trusted local
    code. Grape has a separate permission independent of file and
    network permissions.
  - Formulas and script filters are value-computing execution. They
    cannot use Grape and cannot create asynchronous or persistent
    execution.
  - Ordinary scripts may keep asynchronous and persistent execution only
    when the originating restricted context is propagated to later
    execution.
  - Preserve current mediated Freeplane loading behavior for maps,
    templates, documentation, bundled icons/resources, and approved user
    `resources`/`icons` without granting blanket file-read or network
    permission.
  - AI request permission is separate from the external sandbox
    permission set and does not define externally unrestricted mode.
  - Externally unrestricted ordinary scripts are defined by file read,
    file write/delete, network, and process/native execution permissions
    all being enabled. Formula-specific restrictions still apply to
    formula execution.
  - Do not add a user-facing advanced permission for reflection, method
    handles, arbitrary class loading/definition, or `Unsafe`.
  - Signed-script trust remains optional legacy compatibility. Preserve
    it only if implementation evidence shows it is effective and cheap
    to keep.
- **Briefing:**
  - Architecture direction is recorded in this task file.
  - Main current scripting classes are in `freeplane_plugin_script`:
    `ScriptingRegistration`, `ScriptingPolicy`,
    `ScriptingSecurityManager`, `InternationalizedSecurityManager`,
    `ScriptClassLoader`, `GroovyScript`, `GroovyShell`,
    `GenericScript`, `ScriptSecurity`, `ScriptingPermissions`,
    `ScriptResources`, `FormulaUtils`, `ScriptRunner`,
    `ScriptContext`, `ExecutingScriptContextStack`, and proxy classes
    under `org.freeplane.plugin.script.proxy`.
  - Formula rendering/validation also involves `freeplane_plugin_formula`
    and `FormulaTextTransformer`.
  - Current non-script security dependencies include launcher startup,
    OSGi startup/security-manager clearing, script-registration
    security-manager clearing, XSLT export policy use, and
    `ApplicationPropertyStore` secured-property checks.
  - Current `AccessController.doPrivileged(...)` call sites include
    resource/icon mediation, UI/file chooser workarounds, preview/cache
    internals, map/template/documentation loading, event/listener
    notification, bug-report work, script/Groovy class loading, Grape,
    class-loader/context-loader plumbing, and AI callback plumbing.
  - Current launch/package paths still add
    `-Djava.security.manager=allow` in shell/batch/jpackage paths, and
    `Launcher.launchWithoutUICheck(...)` calls
    `System.setSecurityManager(new SecurityManager(){...})` unless
    disabled.
  - Current project build files default most projects to Java 8
    source/target compatibility. `freeplane_plugin_ai` already has a
    Java 17 path, and the script plugin doclet source set is compiled for
    Java 17.
  - No existing Byte Buddy dependency or Java-agent module was found in
    the checked Gradle project structure.
- **Research:**

  ```plantuml
  @startuml
  actor "Script / formula" as Script
  participant "ScriptingEngine" as Engine
  participant "GroovyScript / GenericScript" as ScriptImpl
  participant "ScriptClassLoader" as ScriptLoader
  participant "ScriptingSecurityManager" as ScriptPermissions
  participant "ScriptingPolicy" as Policy
  participant "JVM SecurityManager" as SecurityManager
  participant "JDK / OS resource" as Resource

  Script -> Engine: executeScript(...)
  Engine -> ScriptImpl: compile / execute
  ScriptImpl -> ScriptLoader: setSecurityManager(...)
  ScriptImpl -> SecurityManager: AccessController.doPrivileged(...)
  ScriptLoader -> ScriptPermissions: implies(permission)
  SecurityManager -> Policy: implies(domain, permission)
  Policy -> ScriptLoader: delegate permission decision
  Script -> Resource: file / network / exec / class loading
  Resource -> SecurityManager: checkPermission(...)
  SecurityManager -> Policy: permission decision
  @enduml
  ```

  - Current enforcement is stack/protection-domain/class-loader based
    and depends on an active JVM security manager. Without that manager,
    `ScriptingPolicy` is not a complete enforcement mechanism.
  - `ScriptingSecurityManager` maps script preferences to Java
    permissions for file read, file write/delete, network, process
    execution, native loading, file descriptors, preferences, and URL
    permissions. It grants `AllPermission` when ordinary script file
    read, file write/delete, network, and process/native restrictions are
    all disabled.
  - The restricted default path does not grant class-loader creation,
    access-declared-members, reflective access suppression, or broad
    runtime permissions; current denial of those bypasses depends on
    Java permission checks being active.
  - `ScriptProxy` lets scripts request nested-script permissions such as
    file read, file write/delete, network, process/native execution, AI
    request, and all permissions. Current escalation denial depends on
    `ScriptingSecurityManager.checkRequiredPermissions()` calling the
    active JVM security manager.
  - Current formula execution uses `FormulaUtils`, `NodeScript`,
    `ScriptContext`, formula permissions, dependency tracking, and the
    separate `formula_block_mode_controller_execute` guard. The formula
    execute guard blocks actor-backed `MModeController.execute(...)` but
    is not a sandbox replacement.
  - Script filters currently use formula permissions and formula
    dependency tracking.
  - `ScriptContext` records script data and permissions but not an
    explicit execution kind; the replacement needs explicit ordinary
    script/formula/script-filter/AI-owned distinctions.
  - `ScriptingPolicy` grants reads from Freeplane user `resources` and
    `icons` directories even without general file-read permission, and
    grants requested permissions to code under the Freeplane user `lib`
    directory before delegating to script-loader permissions.
  - `LoaderProxy` lets scripts load maps from `File`, `URL`, string
    path/URL, or direct XML content. `MapLoader.loadMap(...)` reads the
    selected source inside `doPrivileged(...)`, parses it as a map, and
    has documentation/template variants. This mediated behavior is
    intentionally preserved.
  - Groovy 4.0.27 runs a simple script on checked Zulu Java 25.0.3.
    Groovy 5.0.6 and Groovy 6.0.0-alpha-1 still contain sandbox-relevant
    reflection, dynamic-code, metaclass, and Grape surfaces.
  - Groovy/Grape can route artifacts into selected loaders through
    `classLoader`, `refObject`, and `systemClassLoader` mechanisms.
    Freeplane's `GrapeMetaClass` and Groovy 5 Grape code use
    `AccessController.doPrivileged(...)`; Groovy 6 alpha removed some
    `AccessController` references but still has loader-routing concerns.
  - Public Groovy metaclass APIs expose persistent/global mutation paths
    such as `setMetaClass`, `removeMetaClass`,
    `setMetaClassCreationHandle`, and listener mutation.
  - Existing async/persistent context preservation is incomplete.
    `NodeChangeListenerForScript` captures `ScriptContext` and context
    class loader but does not push `ExecutingScriptContextStack`; AI
    callbacks do push captured context.
  - Local Java 25 `javap` inspection identified guard candidates across
    file I/O, network I/O, process/native execution, JVM exit/halt,
    class loading/definition, reflection access suppression, method
    handles, `Unsafe`, preferences/properties/global setters,
    instrumentation/attach, module mutation, JMX mutation/invocation,
    and logging configuration APIs.
  - OpenSearch's Java-agent Security Manager replacement is useful prior
    art for Byte Buddy, endpoint interception, stack walking, and policy
    checking, but its static/global policy model is not directly
    sufficient for Freeplane's per-script dynamic permission model.
  - Current OpenSearch agent method selection is manual and narrow. It
    uses Byte Buddy matchers for `SocketChannel`/`Socket` `connect`,
    `Files`/`FileChannel`/`FileSystemProvider` methods named `write`,
    `createFile`, `createDirectories`, `createLink`, `copy`, `move`,
    `newByteChannel`, `delete`, `deleteIfExists`, `read`, and `open`,
    plus `System.exit`, `Runtime.halt`, and JDK-24+ `Subject.getSubject`.
  - OpenSearch endpoint decisions use bootstrap-visible `AgentPolicy`,
    `StackWalker` caller class/protection-domain extraction, Java
    `Policy.implies(...)`, static trusted-host/file-system sets, and an
    exit-allowed class-chain predicate. It stops protection-domain stack
    collection at JDK or OpenSearch replacement `AccessController`
    `doPrivileged` frames and filters JDK `jrt:` code sources.
  - OpenSearch does not provide the full Freeplane endpoint catalog: it
    does not guard Runtime exec, native library loading, class loading
    or definition, reflection suppression, method handles, `Unsafe`,
    preferences/properties/global setters, attach/instrumentation,
    module mutation, JMX mutation/invocation, logging configuration, or
    most non-`connect` socket operations in the inspected current agent.
  - Maven Central metadata checked during research showed
    `net.bytebuddy:byte-buddy` and `net.bytebuddy:byte-buddy-agent`
    `1.18.10`; their regular jars are multi-release jars with Java 9
    module descriptors, Java 8 base bytecode, and ASM `9.10.1` in the
    parent POM.
- **Analysis:**
  - The selected architecture is an in-process Freeplane-owned sandbox
    runtime plus a launch-time Java agent and explicit safe-operation
    wrappers.
  - Java 17 is selected as the implementation baseline because retaining
    Java 8 compatibility would force reflection, multi-release, or
    duplicate guard code for APIs central to Java 25 sandbox enforcement.
  - A thread-local active context alone is insufficient because
    callbacks, listeners, executors, timers, and script-created threads
    can later run without the original script stack.
  - Class-origin detection alone is insufficient because trusted
    Freeplane or Groovy code can later act on script-tainted state with
    no script class on the stack.
  - The replacement therefore needs both active execution context and
    code/resource origin tagging.
  - Endpoint guards are required because file/network/process/native,
    class loading/definition, reflection, method handles, `Unsafe`, and
    global mutation endpoints are reachable outside Freeplane proxies.
  - Safe-operation wrappers are required because trusted Freeplane code
    invoked by scripts must still perform narrow mediated operations
    without making the calling script generally unrestricted.
  - Configured script classpath and user `lib` jars are preserved as
    trusted local code. Grape-loaded code is not trusted local code
    because an untrusted script can select dependency coordinates,
    repositories, cached artifacts, and loader routing.
  - Ordinary script asynchronous/persistent execution is preserved only
    with context propagation. Formulas and script filters deny async and
    persistent execution because they are value-computing.
  - Freeplane-only call-site checks and proxy wrappers are insufficient
    as the security boundary because JDK/Groovy capability endpoints are
    reachable through reflection, method handles, generated classes,
    custom loaders, Grape-loaded code, and existing classpath helpers
    without going through Freeplane proxy APIs.
  - Out-of-process isolation remains a fallback only if the in-process
    path is falsified. It would substantially redesign the current live
    Java-object scripting API.
  - Disabling scripts/formulas or adding an unsafe Java 24+/Java 25 mode
    is rejected because it would weaken protection for untrusted mind
    maps.
  - Continuing to rely on `SecurityManager`, policy files, or launcher
    flags is not viable on Java 25 because the VM rejects
    security-manager enabling and installation.
  - The accepted security claim is not a catch-all JVM escape guarantee.
    It is a finite guarded capability boundary with fail-closed handling
    for unclassified script-origin execution paths and regression tests
    for known bypass families.
- **Design:**
  - Use this task's Analysis and Design sections as the governing design
    input. If implementation evidence falsifies the in-process design,
    stop and revise this task before continuing.
  - Implement a Freeplane-owned sandbox runtime that owns effective
    permissions, execution kind, active execution context, code/resource
    origin tags, and safe-operation scope.
  - Implement a launch-time Java agent with a small bootstrap-visible
    guard surface. The agent guards JDK/Groovy capability endpoints and
    delegates permission decisions to Freeplane-owned sandbox state.
  - Replace `AccessController.doPrivileged(...)` uses with
    Freeplane-owned safe-operation wrappers. Wrapper scope must be
    non-forgeable by script-origin code, auditable, limited to the
    approved operation, and absent during script-supplied callbacks.
  - Preserve direct Java-object sharing. Do not switch to out-of-process
    isolation or a restricted/proxied scripting API unless this task is
    revised after implementation evidence falsifies the in-process path.
  - Preserve mediated map/template/documentation/icon/resource loading as
    explicit Freeplane operations rather than as general file/network
    grants.
  - Preserve user `lib` and configured script classpath as trusted local
    code. Treat Grape-loaded code and generated/cached script code as
    script-origin unless explicitly classified otherwise by the sandbox
    runtime.
  - Remove Java 25-incompatible launcher/startup behavior only when the
    replacement sandbox path is ready: remove
    `-Djava.security.manager=allow`, stop calling
    `System.setSecurityManager(...)`, stop relying on
    `java.security.policy`/`freeplane.policy` for sandbox enforcement,
    and package the sandbox agent and required module-open options.

  ```plantuml
  @startuml
  actor "Script / formula / script filter" as Script
  participant "Sandbox runtime" as Runtime
  participant "Groovy / JSR-223 execution" as Execution
  participant "Generated, cached, or Grape-loaded code" as Helper
  participant "Safe-operation wrapper" as SafeOp
  participant "Java agent endpoint guards" as Agent
  participant "JDK / OS capability" as Capability
  participant "Normal Freeplane code" as Freeplane

  Script -> Runtime: enter(kind, permissions, origin)
  Runtime -> Execution: run with active context
  Execution -> Helper: compile / load / define / invoke
  Helper -> Runtime: inherit origin or fail closed
  Execution -> Agent: guarded capability attempt
  Helper -> Agent: guarded capability attempt
  Agent -> Runtime: check context, origin, safe operation
  Runtime --> Agent: allow or deny
  Agent -> Capability: allowed operation only
  Freeplane -> SafeOp: approved mediated operation
  SafeOp -> Runtime: scoped safe operation
  SafeOp -> Agent: guarded internal access
  SafeOp --> Runtime: restore prior scope before callbacks
  Runtime -> Script: exit and restore prior context
  @enduml
  ```
- **Test specification:**
  - Each implementation subtask must include automated tests for its own
    delivered behavior and must not split implementation and tests.
  - Global acceptance requires tests that restricted scripts deny file
    read/write/delete, network connect/listen, process execution, native
    loading, JVM exit/halt, class-loader creation, class definition,
    reflection access suppression, method-handle private lookup/class
    definition, `Unsafe`, global metaclass mutation, Grape without Grape
    permission, Grape loader-routing into trusted loaders, and helper
    code bypasses.
  - Global acceptance requires tests that allowed operations still work:
    explicitly permitted file/network/process/native operations,
    Freeplane intended Groovy meta features, normal Groovy dynamic
    dispatch, mediated icon/resource loading, approved user
    `resources`/`icons`, mediated map/template/documentation loading,
    user `lib`/configured classpath trusted code, and externally
    unrestricted ordinary scripts.
  - Global acceptance requires tests that formulas and script filters
    cannot use Grape, cannot become externally unrestricted through the
    ordinary script four-permission shortcut, cannot create async or
    persistent execution, and still keep the existing
    `MModeController.execute(...)` guard.
  - Global acceptance requires tests that async ordinary-script callbacks
    and persistent executable objects run with the originating restricted
    context and restore previous context afterward.
  - Global acceptance requires tests that every existing shared-permission
    script/formula source enters the replacement sandbox with equivalent
    effective permissions, including AI-owned script/formula paths where
    applicable.
  - Global acceptance requires Java 25 startup and sandbox enforcement
    without `SecurityManager` installation, Java permission checks,
    `-Djava.security.manager=allow`, or unsafe Java 24+/25 modes.
  - Manual tests: N/A

## Subtask: Raise implementation baseline to Java 17
- **Status:** backlog
- **Scope:**
  Raise the Freeplane implementation baseline needed for this sandbox
  replacement to Java 17 while preserving current sandbox behavior on
  still-supported runtimes. Update Gradle compatibility/toolchain
  settings, launcher capability metadata, runtime documentation, and CI
  expectations needed for a coherent Java 17 baseline. Do not remove or
  weaken the current `SecurityManager` sandbox in this subtask.
- **Motivation:**
  Java 8 compatibility would force reflection, duplicate code, or
  multi-release complexity around APIs central to Java 25 sandbox
  enforcement. A Java 17 baseline reduces replacement-sandbox risk and
  aligns the implementation with modern JDK and agent APIs.
- **Design:**
  - Update build and runtime metadata so the sandbox replacement work can
    use Java 17 source/runtime APIs directly.
  - Known baseline update locations include root `build.gradle`,
    `freeplane/build.gradle`, `freeplane_api/build.gradle`,
    `freeplane_mac/build.gradle`, `freeplane_plugin_ai/build.gradle`,
    `freeplane_plugin_script/build.gradle`, and launcher/runtime metadata
    such as `BIN/freeplane.sh` OSGi execution-environment capability.
  - Review Java-8 bootstrap or compatibility exceptions, especially the
    AI plugin bootstrap source set, and keep them only when they still
    have a post-Java-17 purpose.
  - Do not remove `-Djava.security.manager=allow` or
    `System.setSecurityManager(...)` in this subtask unless the current
    sandbox remains active and tests prove no weakening. Java 25 startup
    enablement belongs to the final subtask.
  - Keep Groovy 4.0.27 unless Java 25 implementation testing later proves
    it unusable.
  - Leave Java 25 script/formula support disabled until the final sandbox
    replacement subtasks are complete.
- **Test specification:**
  - Automated tests:
    - The project compiles with the selected Java 17 baseline settings.
    - Existing script/formula sandbox tests still pass on a supported
      non-Java-25 runtime after the baseline change.
    - Launcher metadata no longer advertises an incompatible Java 8-only
      runtime assumption.
    - Any retained Java-8 compatibility/bootstrap exception is covered by
      an explicit test or documented as intentionally still needed.
  - Manual tests: N/A

## Subtask: Introduce sandbox runtime context and execution kinds
- **Status:** backlog
- **Scope:**
  Add the Freeplane-owned sandbox runtime model for effective
  permissions, execution kind, active context, and origin classification.
  Wire every existing shared-permission script/formula entry point into
  runtime entry/exit scopes while keeping existing Java permission checks
  active where they still work.
- **Motivation:**
  Java 25 replacement enforcement needs explicit Freeplane-owned context
  and execution kind before endpoint guards and safe-operation wrappers
  can make correct decisions.
- **Design:**
  - Add explicit ordinary-script, formula, script-filter, and AI-owned
    execution kinds.
  - Preserve nested `ScriptProxy` permission-escalation checks through
    Freeplane-owned runtime decisions.
  - Mark configured script classpath/user `lib` as trusted local code and
    generated/cached/Grape-loaded script code as script-origin according
    to this task's architecture decision.
  - Deny formulas and script filters from registering async/persistent
    execution through Freeplane-owned APIs introduced or touched in this
    subtask.
- **Test specification:**
  - Automated tests:
    - Every existing shared-permission script/formula entry point enters
      a sandbox runtime scope with the expected execution kind.
    - Nested scripts cannot exceed the originating restricted context's
      effective permissions.
    - Formula and script-filter execution use value-computing kinds and
      cannot become ordinary unrestricted scripts.
    - AI-owned script/formula paths preserve the corresponding non-AI
      sandbox boundary plus separate AI permission handling.
  - Manual tests: N/A

## Subtask: Replace privileged Freeplane mediation with safe-operation wrappers
- **Status:** backlog
- **Scope:**
  Classify and replace current `AccessController.doPrivileged(...)` call
  sites with Freeplane-owned safe-operation wrappers or ordinary code.
  Preserve mediated map/template/documentation/icon/resource behavior
  without granting blanket file/network permission.
- **Motivation:**
  Current privileged scopes are both necessary for some trusted Freeplane
  operations and dangerous if treated as a generic sandbox bypass. The
  replacement needs narrow, auditable, non-forgeable Freeplane-owned
  mediation.
- **Design:**
  - Classify every current `doPrivileged(...)` use as obsolete, ordinary
    application internal, script-facing safe operation, or dangerous
    general privilege elevation.
  - Implement safe-operation scope that guarded endpoints can recognize
    but script-origin code cannot create.
  - Ensure safe-operation scope is absent before script-supplied
    callbacks, closures, listeners, `Runnable`, `Callable`, map filters,
    node-change listeners, and AI callbacks execute.
  - Replace `ApplicationPropertyStore`'s `AllPermission` check with a
    Freeplane-owned sandbox guard.
- **Test specification:**
  - Automated tests:
    - Restricted scripts can use approved mediated icon/resource and
      map/template/documentation operations without receiving general
      file/network permission.
    - Approved user resource/icon access remains limited to Freeplane
      user `resources` and `icons` directories.
    - Safe-operation wrappers cannot be forged or invoked by restricted
      scripts to read arbitrary files, open network connections, execute
      processes, define classes, suppress access checks, or load native
      code.
    - Safe-operation scope is not active while script-supplied executable
      objects run.
    - Secured property mutation is denied to restricted scripts unless
      the replacement permission model explicitly allows it.
  - Manual tests: N/A

## Subtask: Harden Groovy, Grape, metaclass, and script-origin loading
- **Status:** backlog
- **Scope:**
  Guard Groovy-specific code introduction and persistent mutation paths:
  Grape, compile-time grabs, Groovy runtime compilation, generated helper
  classes, cached/precompiled scripts, metaclass registry mutation,
  categories/mixins/extension modules, and script-reachable class
  loaders.
- **Motivation:**
  A Java endpoint guard alone is insufficient if Groovy can introduce or
  mutate code so later trusted execution performs restricted actions
  without script-origin context.
- **Design:**
  - Enforce separate Grape permission for ordinary scripts/add-ons only.
  - Deny Grape for formulas and script filters.
  - Prevent Grape loader/refObject/system-classloader routing into
    trusted or unrelated class loaders.
  - Treat Grape-loaded artifacts and generated/cached script helper
    classes as script-origin code.
  - Block restricted scripts from persistent/global metaclass registry
    mutation while preserving Freeplane-installed Groovy meta features.
  - Distinguish Groovy-owned reflection needed for normal dispatch from
    script-authored reflection used as a bypass.
- **Test specification:**
  - Automated tests:
    - Grape is denied without Grape permission even when file-read or
      network permission exists.
    - Grape with only Grape permission can perform dependency-loading
      operations but not direct general file/network operations.
    - Grape cannot load artifacts into trusted/unrelated class loaders.
    - Generated, cached, precompiled, and Grape-loaded helper code cannot
      exceed the originating script's sandbox restrictions.
    - Restricted scripts cannot register persistent metaclass mutations
      that later affect trusted Freeplane code.
    - Freeplane's intended Groovy meta features and normal dynamic
      dispatch still work.
  - Manual tests: N/A

## Subtask: Define method-level endpoint guard catalog
- **Status:** backlog
- **Scope:**
  Produce the method-level endpoint catalog that the Java agent and
  runtime guards must implement. This is a user-requested catalog/proof
  subtask before endpoint-guard implementation, not a runtime behavior
  release by itself.
- **Motivation:**
  The protected capability list is the core sandbox boundary. A broad
  family list such as file, network, process, reflection, and class
  loading is not precise enough for implementation approval or security
  review.
- **Design:**
  - The catalog is method-level and Java 25 based. Each entry below must
    be validated against JDK 25 source during this subtask before the
    guard implementation subtask starts.
  - Default rule for an agent-advice entry: if no restricted context and
    no script-origin caller is present, allow; if a matching
    Freeplane-owned safe-operation scope applies, allow only for that
    operation and target; otherwise require the corresponding script
    permission and execution kind; fail closed for unsupported argument
    shapes.
  - Initial candidate method-level catalog:
    - File metadata/read via `java.io.File`: agent advice on
      `canRead()`, `exists()`, `isFile()`, `isDirectory()`,
      `lastModified()`, `length()`, `list()`, `list(FilenameFilter)`,
      `listFiles()`, `listFiles(FilenameFilter)`, and
      `listFiles(FileFilter)`; require file-read or approved mediated
      user `resources`/`icons`/map-template-doc operation.
    - File create/write/delete via `java.io.File`: agent advice on
      `createNewFile()`, `delete()`, `deleteOnExit()`, `mkdir()`,
      `mkdirs()`, `renameTo(File)`, `setLastModified(long)`,
      `setReadOnly()`, `setWritable(boolean, boolean)`,
      `setWritable(boolean)`, `setReadable(boolean, boolean)`,
      `setReadable(boolean)`, `setExecutable(boolean, boolean)`,
      `setExecutable(boolean)`, `createTempFile(String, String, File)`,
      and `createTempFile(String, String)`; require file-write/delete as
      appropriate.
    - Classic file streams/readers/writers: agent advice on constructors
      `FileInputStream(String)`, `FileInputStream(File)`,
      `FileInputStream(FileDescriptor)`, `FileReader(String)`,
      `FileReader(File)`, `FileReader(FileDescriptor)`,
      `FileReader(String, Charset)`, `FileReader(File, Charset)`,
      `FileOutputStream(String)`, `FileOutputStream(String, boolean)`,
      `FileOutputStream(File)`, `FileOutputStream(File, boolean)`,
      `FileOutputStream(FileDescriptor)`, `FileWriter(String)`,
      `FileWriter(String, boolean)`, `FileWriter(File)`,
      `FileWriter(File, boolean)`, `FileWriter(FileDescriptor)`,
      `FileWriter(String, Charset)`, `FileWriter(String, Charset, boolean)`,
      `FileWriter(File, Charset)`, `FileWriter(File, Charset, boolean)`,
      `RandomAccessFile(String, String)`, and
      `RandomAccessFile(File, String)`; classify mode/constructor as
      read or write. FileDescriptor overloads are fail-closed unless a
      trusted Freeplane safe operation supplied the descriptor.
    - NIO file APIs: agent advice on `Files.newInputStream(Path,
      OpenOption...)`, `newOutputStream(Path, OpenOption...)`,
      `newByteChannel(Path, Set, FileAttribute...)`,
      `newByteChannel(Path, OpenOption...)`, `createFile(Path,
      FileAttribute...)`, `createDirectory(Path, FileAttribute...)`,
      `createDirectories(Path, FileAttribute...)`, `createTempFile(Path,
      String, String, FileAttribute...)`, `createTempFile(String,
      String, FileAttribute...)`, `createTempDirectory(Path, String,
      FileAttribute...)`, `createTempDirectory(String, FileAttribute...)`,
      `createSymbolicLink(Path, Path, FileAttribute...)`,
      `createLink(Path, Path)`, `delete(Path)`, `deleteIfExists(Path)`,
      `copy(Path, Path, CopyOption...)`, `copy(InputStream, Path,
      CopyOption...)`, `copy(Path, OutputStream)`, `move(Path, Path,
      CopyOption...)`, `setAttribute(Path, String, Object,
      LinkOption...)`, `setLastModifiedTime(Path, FileTime)`,
      `exists(Path, LinkOption...)`, `readAllBytes(Path)`,
      `readString(Path)`, `readString(Path, Charset)`,
      `readAllLines(Path)`, `readAllLines(Path, Charset)`, `write(Path,
      byte[], OpenOption...)`, `write(Path, Iterable, Charset,
      OpenOption...)`, `write(Path, Iterable, OpenOption...)`,
      `writeString(Path, CharSequence, OpenOption...)`,
      `writeString(Path, CharSequence, Charset, OpenOption...)`,
      `list(Path)`, `walk(Path, int, FileVisitOption...)`,
      `walk(Path, FileVisitOption...)`, `find(Path, int, BiPredicate,
      FileVisitOption...)`, `lines(Path)`, and `lines(Path, Charset)`;
      classify by open options and source/target paths.
    - File-system provider APIs: agent advice on
      `FileSystemProvider.newInputStream(Path, OpenOption...)`,
      `newOutputStream(Path, OpenOption...)`, `newByteChannel(Path, Set,
      FileAttribute...)`, `createDirectory(Path, FileAttribute...)`,
      `createSymbolicLink(Path, Path, FileAttribute...)`,
      `createLink(Path, Path)`, `delete(Path)`, `deleteIfExists(Path)`,
      `copy(Path, Path, CopyOption...)`, `move(Path, Path,
      CopyOption...)`, `setAttribute(Path, String, Object,
      LinkOption...)`, and `exists(Path, LinkOption...)`; trusted
      provider exclusions must be explicit and not script-controllable.
    - File channels: agent advice on `FileChannel.open(Path, Set,
      FileAttribute...)`, `FileChannel.open(Path, OpenOption...)`,
      `FileChannel.map(MapMode, long, long)`, `FileChannel.map(MapMode,
      long, long, Arena)`, `AsynchronousFileChannel.open(Path, Set,
      ExecutorService, FileAttribute...)`, and
      `AsynchronousFileChannel.open(Path, OpenOption...)`; read/write is
      derived from open options and map mode. Per-operation `read`/`write`
      methods are secondary guards only if source validation shows an
      unguarded channel acquisition path remains.
    - Socket constructors and connects: agent advice on `Socket(String,
      int)`, `Socket(InetAddress, int)`, `Socket(String, int,
      InetAddress, int)`, `Socket(InetAddress, int, InetAddress, int)`,
      `Socket(String, int, boolean)`, `Socket(InetAddress, int,
      boolean)`, `Socket.connect(SocketAddress)`, and
      `Socket.connect(SocketAddress, int)`; require network permission
      or approved mediated operation.
    - Server/listening sockets: agent advice on `ServerSocket(int)`,
      `ServerSocket(int, int)`, `ServerSocket(int, int, InetAddress)`,
      `ServerSocket.bind(SocketAddress)`, `ServerSocket.bind(SocketAddress,
      int)`, and `ServerSocket.accept()`; require network permission.
    - Datagram/multicast sockets: agent advice on `DatagramSocket()`,
      `DatagramSocket(SocketAddress)`, `DatagramSocket(int)`,
      `DatagramSocket(int, InetAddress)`, `DatagramSocket.bind(SocketAddress)`,
      `DatagramSocket.connect(InetAddress, int)`,
      `DatagramSocket.connect(SocketAddress)`, `DatagramSocket.send(DatagramPacket)`,
      `DatagramSocket.receive(DatagramPacket)`, `MulticastSocket()`,
      `MulticastSocket(int)`, `MulticastSocket(SocketAddress)`,
      `MulticastSocket.joinGroup(InetAddress)`,
      `MulticastSocket.leaveGroup(InetAddress)`,
      `MulticastSocket.joinGroup(SocketAddress, NetworkInterface)`,
      `MulticastSocket.leaveGroup(SocketAddress, NetworkInterface)`, and
      `MulticastSocket.send(DatagramPacket, byte)`; require network
      permission.
    - URL and HTTP networking: agent advice on `URL.openConnection()`,
      `URL.openConnection(Proxy)`, `URLConnection.connect()`,
      `URLConnection.getInputStream()`, `URLConnection.getOutputStream()`,
      `HttpURLConnection.setFollowRedirects(boolean)`,
      `HttpsURLConnection.setDefaultHostnameVerifier(HostnameVerifier)`,
      `HttpsURLConnection.setDefaultSSLSocketFactory(SSLSocketFactory)`,
      `HttpClient.send(HttpRequest, BodyHandler)`,
      `HttpClient.sendAsync(HttpRequest, BodyHandler)`, and
      `HttpClient.sendAsync(HttpRequest, BodyHandler, PushPromiseHandler)`;
      connect/send/input require network, output is network plus any
      request-body side effects.
    - Process execution and native loading: agent advice on
      `ProcessBuilder.start()`, all six public `Runtime.exec(...)`
      overloads, `System.load(String)`, `System.loadLibrary(String)`,
      `Runtime.load(String)`, and `Runtime.loadLibrary(String)`; require
      process/native permission.
    - Foreign-function/native access: agent advice on
      `java.lang.foreign.Linker.nativeLinker()`,
      `Linker.downcallHandle(MemorySegment, FunctionDescriptor, Option...)`,
      `Linker.downcallHandle(FunctionDescriptor, Option...)`,
      `Linker.upcallStub(MethodHandle, FunctionDescriptor, Arena, Option...)`,
      `SymbolLookup.libraryLookup(String, Arena)`,
      `SymbolLookup.libraryLookup(Path, Arena)`,
      `MemorySegment.ofAddress(long)`, and `MemorySegment.reinterpret(...)`
      overloads; require process/native permission or deny for restricted
      scripts if no safe inherited-origin model exists.
    - JVM termination: agent advice on `System.exit(int)`,
      `Runtime.exit(int)`, and `Runtime.halt(int)`; deny for restricted
      scripts unless an explicit trusted Freeplane exit path applies.
    - Class-loader creation and class definition: agent advice on
      protected `ClassLoader(String, ClassLoader)`,
      `ClassLoader(ClassLoader)`, `ClassLoader()`, public
      `URLClassLoader(URL[], ClassLoader)`, `URLClassLoader(URL[])`,
      `URLClassLoader(URL[], ClassLoader, URLStreamHandlerFactory)`,
      `URLClassLoader(String, URL[], ClassLoader)`,
      `URLClassLoader(String, URL[], ClassLoader, URLStreamHandlerFactory)`,
      protected `ClassLoader.defineClass(byte[], int, int)`,
      `defineClass(String, byte[], int, int)`, `defineClass(String,
      byte[], int, int, ProtectionDomain)`, and `defineClass(String,
      ByteBuffer, ProtectionDomain)`; restricted scripts are denied
      unless the sandbox can attach inherited script-origin restrictions.
    - Method handles and hidden classes: agent advice on
      `MethodHandles.privateLookupIn(Class, MethodHandles.Lookup)`,
      `MethodHandles.Lookup.defineClass(byte[])`,
      `defineHiddenClass(byte[], boolean, ClassOption...)`, and
      `defineHiddenClassWithClassData(byte[], Object, boolean,
      ClassOption...)`; restricted scripts denied except proven Groovy
      internals under inherited restrictions.
    - Reflection access suppression: agent advice on
      `AccessibleObject.setAccessible(AccessibleObject[], boolean)`,
      `AccessibleObject.setAccessible(boolean)`, and
      `AccessibleObject.trySetAccessible()`; restricted scripts denied
      except proven Groovy internals for normal dispatch.
    - Unsafe: agent advice on `jdk.internal.misc.Unsafe.getUnsafe()`,
      `Unsafe.defineClass(String, byte[], int, int, ClassLoader,
      ProtectionDomain)`, `Unsafe.defineClass0(...)`, and
      `Unsafe.allocateInstance(Class)`; additionally catalog any reachable
      `sun.misc.Unsafe` facade on the target runtime. Restricted scripts
      denied.
    - System properties and streams: agent advice on `System.setIn(InputStream)`,
      `System.setOut(PrintStream)`, `System.setErr(PrintStream)`,
      `System.setProperties(Properties)`, `System.setProperty(String,
      String)`, and `System.clearProperty(String)`; restricted scripts
      denied except explicit safe property mediation.
    - Preferences: agent advice on `Preferences.put(String, String)`,
      `remove(String)`, `clear()`, `flush()`, and `sync()`; mutation
      requires the replacement preference permission or trusted
      Freeplane mediation.
    - Process-wide factory/default setters: agent advice on
      `URL.setURLStreamHandlerFactory(URLStreamHandlerFactory)`,
      `ProxySelector.setDefault(ProxySelector)`,
      `Authenticator.setDefault(Authenticator)`,
      `CookieHandler.setDefault(CookieHandler)`,
      `ResponseCache.setDefault(ResponseCache)`, `URLConnection.setDefaultAllowUserInteraction(boolean)`,
      `URLConnection.setDefaultUseCaches(String, boolean)`,
      `URLConnection.setDefaultRequestProperty(String, String)`,
      `Security.insertProviderAt(Provider, int)`, `Security.addProvider(Provider)`,
      `Security.removeProvider(String)`, `Security.setProperty(String,
      String)`, `Locale.setDefault(Locale)`, `Locale.setDefault(Category,
      Locale)`, `TimeZone.setDefault(TimeZone)`, `DriverManager.registerDriver(Driver)`,
      `DriverManager.registerDriver(Driver, DriverAction)`, and
      `DriverManager.deregisterDriver(Driver)`; restricted scripts
      denied.
    - Thread and async creation/context: agent/runtime guard on
      `Thread.start()`, `Thread.setContextClassLoader(ClassLoader)`,
      `Thread.setDefaultUncaughtExceptionHandler(UncaughtExceptionHandler)`,
      `Timer.schedule(...)` overloads, `ThreadPoolExecutor.execute(Runnable)`,
      `ForkJoinPool.execute(...)`, `ForkJoinPool.submit(...)`,
      `ForkJoinPool.invoke(ForkJoinTask)`, scheduled-executor
      `schedule(...)`, and executor-service `submit(...)`; formulas and
      script filters denied, ordinary scripts require context capture and
      restoration for script-owned executable objects.
    - Instrumentation and attach: agent advice on
      `Instrumentation.retransformClasses(Class<?>...)`,
      `Instrumentation.redefineClasses(ClassDefinition...)`,
      `Instrumentation.appendToBootstrapClassLoaderSearch(JarFile)`,
      `Instrumentation.appendToSystemClassLoaderSearch(JarFile)`,
      `Instrumentation.redefineModule(...)`, `VirtualMachine.attach(String)`,
      `VirtualMachine.attach(VirtualMachineDescriptor)`,
      `VirtualMachine.loadAgentLibrary(String, String)`,
      `loadAgentLibrary(String)`, `loadAgentPath(String, String)`,
      `loadAgentPath(String)`, `loadAgent(String, String)`, and
      `loadAgent(String)`; restricted scripts denied.
    - Module mutation: agent advice on `Module.addReads(Module)`,
      `Module.addExports(String, Module)`, `Module.addOpens(String,
      Module)`, `ModuleLayer.defineModulesWithOneLoader(...)`,
      `ModuleLayer.defineModulesWithManyLoaders(...)`, and
      `ModuleLayer.defineModules(...)`; restricted scripts denied unless
      trusted Freeplane startup/module mediation applies.
    - JMX mutation/invocation: agent advice on all public
      `MBeanServer.createMBean(...)` overloads,
      `registerMBean(Object, ObjectName)`, `unregisterMBean(ObjectName)`,
      `setAttribute(ObjectName, Attribute)`, `setAttributes(ObjectName,
      AttributeList)`, `invoke(ObjectName, String, Object[], String[])`,
      and `getClassLoader(ObjectName)`; restricted scripts denied unless
      a trusted Freeplane operation explicitly mediates it.
    - Logging configuration: agent advice on `LogManager.readConfiguration()`,
      `readConfiguration(InputStream)`, `updateConfiguration(...)` both
      overloads, and `reset()`; restricted scripts denied.
  - Negative/coverage notes:
    - OpenSearch's inspected method set is a subset only and cannot be
      reused as Freeplane's method catalog.
    - Runtime instrumentation must not use a global method-name
      `contains` rule as the security boundary. Name-pattern scans are
      allowed for candidate discovery, but enforcement entries must bind
      at least owner type, method name, signature/argument shape, and
      capability rule. A `namedOneOf(...)` group is acceptable only
      inside a specific owner type when every selected overload is
      classified explicitly.
    - Already-loaded trusted native code remains outside the Java-level
      endpoint guarantee, as it effectively is under the current
      `SecurityManager` model. The catalog guards restricted-script
      native-code introduction and Java foreign/native entry points; any
      script-facing Java wrapper around trusted native code must be
      classified as a Freeplane/Groovy/API endpoint and guarded or denied
      separately.
    - `FileDescriptor`-based file constructors and already-open streams
      are not safely classifiable by path and therefore fail closed for
      restricted scripts unless created by trusted mediation.
    - If JDK source review shows a lower stable choke point covers an
      overload group, the catalog may replace multiple public overloads
      with that choke point only when regression tests prove equivalent
      coverage.
    - If any listed method cannot be reliably instrumented on Java 25,
      the implementing subtask must either choose a higher-level deny
      path or return to planning and revise this task before continuing.
- **Test specification:**
  - Automated tests:
    - Catalog validation checks reject entries missing method signature,
      capability kind, guard rule, argument extraction, or regression
      test mapping.
    - For every catalog entry, at least one planned or implemented test
      case exists for denial and, where applicable, allowed execution.
    - Catalog entries identify whether the guard is agent advice,
      runtime wrapper, Groovy/Grape guard, or safe-operation validation.
  - Manual tests: N/A

## Subtask: Add Java agent endpoint guards and build artifact
- **Status:** backlog
- **Scope:**
  Add the launch-time Java agent artifact that implements the approved
  method-level endpoint catalog needed for Java 25 sandbox enforcement.
  This subtask creates the agent and build output but does not wire every
  OS-specific launcher or jpackage image.
- **Motivation:**
  Freeplane proxy checks cannot reliably mediate JDK/Groovy endpoints
  reached through reflection, generated classes, custom loaders,
  Grape-loaded code, or existing helper code. Java 25 enforcement needs
  endpoint guards outside Freeplane call sites.
- **Design:**
  - Add a non-OSGi Freeplane-owned agent artifact or equivalent build
    output.
  - Use a small bootstrap-visible guard surface and delegate decisions to
    the Freeplane sandbox runtime.
  - Implement the method targets and guard rules from the endpoint guard
    catalog subtask.
  - Prefer launch-time `-javaagent` over dynamic self-attach.
  - Define the module-open options required by the agent, but leave
    OS-specific launcher/jpackage integration to the platform subtasks.
  - Keep Java 25 script/formula support disabled until final end-to-end
    validation is complete.
- **Test specification:**
  - Automated tests:
    - Restricted scripts are denied each cataloged protected endpoint
      when the corresponding permission or safe operation is absent.
    - Explicitly permitted ordinary scripts retain documented external
      capabilities.
    - Reflection, method handles, generated helpers, and script-origin
      loaded classes cannot bypass endpoint guards.
    - Normal Freeplane code outside restricted execution remains able to
      perform required application operations.
    - The agent artifact is produced with the expected manifest and can
      be loaded by a controlled test JVM.
  - Manual tests: N/A

## Subtask: Wire sandbox agent into freeplane.sh and Linux distribution
- **Status:** backlog
- **Scope:**
  Wire the replacement sandbox agent and required Java options into the
  Unix/Linux script-launch path, primarily `BIN/freeplane.sh`, and the
  distribution tasks that ship that launcher.
- **Motivation:**
  The shell launcher currently adds `-Djava.security.manager=allow` on
  newer Java runtimes and needs a platform-specific replacement path for
  Java 25 startup with the sandbox agent.
- **Design:**
  - Remove or gate the shell-launcher security-manager option only when
    the replacement agent option is present and sandbox enforcement stays
    active.
  - Add launch-time `-javaagent` and required module-open options for the
    shell launcher.
  - Update OSGi execution-environment capability metadata in
    `BIN/freeplane.sh` for the Java 17 baseline and Java 25 target.
  - Ensure Linux/BIN distribution assembly includes the agent artifact
    at the path referenced by `freeplane.sh`.
  - Keep Windows and macOS jpackage wiring in their own subtasks.
- **Test specification:**
  - Automated tests:
    - `freeplane.sh` no longer enables the Java security manager on
      Java 25.
    - `freeplane.sh` includes the replacement agent and required
      module-open options when launching with the replacement sandbox.
    - Distribution output contains the agent at the path referenced by
      `freeplane.sh`.
    - Java 25 shell-launch startup is covered by a controlled launch
      test or equivalent script assertion.
  - Manual tests: N/A

## Subtask: Wire sandbox agent into Windows jpackage
- **Status:** backlog
- **Scope:**
  Wire the replacement sandbox agent and required Java options into the
  Windows packaging path, including `win.dist.gradle` jpackage options
  and `BIN/freeplane.bat` if it remains part of the Windows distribution.
- **Motivation:**
  Windows currently receives the security-manager launcher option through
  both batch/distribution paths and jpackage configuration. Java 25
  support requires a Windows-specific packaged launch path with the
  replacement agent instead.
- **Design:**
  - Remove or gate `-Djava.security.manager=allow` from the Windows
    launcher/package path only when the replacement agent option is
    present and sandbox enforcement stays active.
  - Add launch-time `-javaagent` and required module-open options to the
    Windows jpackage configuration.
  - Ensure the Windows image includes the agent artifact at the path
    referenced by the packaged launcher.
  - Keep shell and macOS jpackage wiring in their own subtasks.
- **Test specification:**
  - Automated tests:
    - Windows jpackage configuration no longer enables the Java security
      manager for the Java 25 sandbox path.
    - Windows jpackage configuration includes the replacement agent and
      required module-open options.
    - Windows package/image inputs include the agent at the referenced
      path.
    - `BIN/freeplane.bat`, if still shipped, is consistent with the
      Windows sandbox launch configuration.
  - Manual tests: N/A

## Subtask: Wire sandbox agent into macOS jpackage
- **Status:** backlog
- **Scope:**
  Wire the replacement sandbox agent and required Java options into the
  macOS packaging path, including `mac.dist.gradle` jpackage options and
  the generated application image layout.
- **Motivation:**
  macOS jpackage currently receives the security-manager launcher option.
  Java 25 support requires a macOS-specific packaged launch path with the
  replacement agent instead.
- **Design:**
  - Remove or gate `-Djava.security.manager=allow` from the macOS
    jpackage path only when the replacement agent option is present and
    sandbox enforcement stays active.
  - Add launch-time `-javaagent` and required module-open options to the
    macOS jpackage configuration.
  - Ensure the macOS app image includes the agent artifact at the path
    referenced by the packaged launcher.
  - Keep shell and Windows jpackage wiring in their own subtasks.
- **Test specification:**
  - Automated tests:
    - macOS jpackage configuration no longer enables the Java security
      manager for the Java 25 sandbox path.
    - macOS jpackage configuration includes the replacement agent and
      required module-open options.
    - macOS package/image inputs include the agent at the referenced
      path.
  - Manual tests: N/A

## Subtask: Enable Java 25 sandbox path and retire SecurityManager enforcement
- **Status:** backlog
- **Scope:**
  Remove active reliance on Java `SecurityManager`, `Policy`,
  `ProtectionDomain`, `AllPermission`, `AccessController`,
  `java.security.policy`, and `freeplane.policy` for sandbox
  enforcement. Enable supported Java 25 startup with the replacement
  sandbox active.
- **Motivation:**
  Java 25 rejects security-manager enabling and installation. Freeplane
  must start and enforce script/formula restrictions without that JVM
  mechanism before Java 25 support can be claimed.
- **Design:**
  - Verify the shell, Windows jpackage, and macOS jpackage subtasks have
    removed or gated `-Djava.security.manager=allow` for the Java 25
    sandbox path and added the replacement agent consistently.
  - Stop calling `System.setSecurityManager(...)` at startup.
  - Retire or isolate `InternationalizedSecurityManager`,
    `ScriptingPolicy`, and Java permission checks where they no longer
    provide enforcement.
  - Preserve localized/diagnostic denial behavior through replacement
    sandbox exceptions where needed.
  - Verify that no unsafe Java 24+/Java 25 script/formula mode exists.
- **Test specification:**
  - Automated tests:
    - Freeplane starts on Java 25 from all supported launch/package paths
      without security-manager launcher flags or
      `System.setSecurityManager(...)` calls.
    - Restricted script/formula/filter behavior is enforced on Java 25 by
      the replacement sandbox, not by Java permission checks.
    - Existing supported script/formula permissions retain intended
      behavior.
    - Java 25 cannot enter a mode where untrusted scripts/formulas run
      without the replacement sandbox.
  - Manual tests: N/A
