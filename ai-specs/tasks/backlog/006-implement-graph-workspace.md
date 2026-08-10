# Graph Workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.

- **Task Identifier:** 2026-08-10-graph-workspace

**Goal:** Add a bundled Graph Workspace feature that opens selected Freeplane maps as one interactive, persistent, live graph in a separate Swing window while preserving source-map ownership, undo behavior, confidentiality, and upstream `.mm` compatibility.

**Architecture:** One Java 8 OSGi plugin contains versioned workspace persistence, a Freeplane adapter that publishes immutable snapshots, a pure deterministic projection engine, a GraphStream-private layout engine, immutable geometry/canvas state, and Swing controller/window code. Workspace commands mutate only `.fpg` state; map-owned commands use normal Freeplane actors. A generation-aware pipeline reads maps on the EDT, projects and lays out off the EDT, repeatedly publishes settling frames, and swaps immutable canvas state on the EDT.

**Tech Stack:** Java 8 source/bytecode, Gradle multi-project build, Knopflerfish OSGi, JUnit 4, AssertJ, Mockito, ArchUnit tests, Swing/AWT, Freeplane map APIs, structured XML APIs, GraphStream `gs-core` 1.3 with `pherd` 1.0 and `mbox2` 1.0.

**Scope decision:** Keep one sequential plan. Persistence, projection, layout, canvas, and map undo routing share identity and lifecycle contracts, and the fixed 2,000-node/5,000-edge gate must exercise their production composition.

## Global Constraints

- Follow `AGENTS.md`: Java source and target compatibility are 8, encoding is UTF-8, indentation is 4 spaces, tests use JUnit 4/AssertJ/Mockito, and build commands use escalated `gradle`, not Maven or the Gradle wrapper.
- Implementation builds require Java at `~/.sdkman/candidates/java/21.0.8-zulu`. That path is absent on the current host, so implementation preflight must stop until it is installed or the maintainer gives an explicit written waiver naming an exact `JAVA_HOME`; never silently substitute another JDK.
- The bundled module is `freeplane_plugin_graph` with symbolic name `org.freeplane.plugin.graph`; it extends MindMap mode only, adds no mode, changes no `freeplane_api` surface, and neither subclasses nor replaces `MapView`.
- Plan approval explicitly authorizes one narrow repository-policy exception: two public-in-Java, unsupported internal SPI classes in the already exported implementation package `org.freeplane.view.swing.map`. This adds no newly exported package and no `freeplane_api` contract, but existing OSGi consumers of that package can see the classes. No other core behavior change is authorized.
- Every cross-package type named in an `Interfaces` block is public with the exact signature shown. Implementation classes used only within one package remain package-private. The graph bundle exports no package.
- GraphStream types stay private to `org.freeplane.plugin.graph.layout.graphstream`; no signature outside that package exposes them. A public internal factory with GraphStream-free signatures is the only construction seam.
- Distribute only unchanged `gs-core-1.3.jar`, `pherd-1.0.jar`, and `mbox2-1.0.jar` under the approved LGPLv3 option, with canonical license text, attribution, exact checksums, and source links. Do not add `gs-ui`, Scala, a wrapper bundle, exports, or launcher changes.
- Keep configured `Import-Package: nothing.*`; generated import/export headers may be absent or empty. Never instantiate `LayoutRunner`; all GraphStream mutation and `compute()` calls run on one serialized plugin worker.
- Fixed layout constraints are SpringBox quality `0.10`, aggregate cross-map displacement cap `0.005` per particle per step, rigid whole-map correction, and perceptual displacement instead of `getStabilization()`. Spike values `0.15` containment and `0.30` hierarchy are calibration defaults, not immutable targets; preserve containment < hierarchy < same-map attraction and the fixed cap.
- The engineering target is jointly 2,000 projected nodes and 5,000 projected edges. It is not a preference or selectable tier; warn when either limit is exceeded.
- Freeplane `MapModel` and `NodeModel` reads occur only on the EDT. Mutable Freeplane types stay in `group`, `adapter`, and `command`, never projection/layout/geometry/canvas.
- Endpoint resolution is traversal-based. Never use `MapModel.getNodeForID`, `MMapController.getNodeFromID_`, or `NodeModel.createID` to resolve or manufacture workspace identity.
- Safe labels use a closed raw-content conversion and never call `TextController.getTransformed*`. Convert direct HTML with Freeplane's `HtmlUtils.htmlToPlain`; use normalized non-evaluated source for formulas/LaTeX/Markdown; use a nonresolving URI/ID fallback for links; use icon/attachment description when text is empty. Never expose unreachable text.
- Do not add a map fingerprint, provenance stamp, content hash, or content heuristic. Moved maps are rebound only by explicit Locate, which rejects a file already bound in that workspace.
- `.fpg` saving never saves `.mm`. Graph Group and native connectors use map actors/undo; cross-map relationships, pins, map registration, purge, and display settings use workspace history.
- No map edit executes without `IUndoHandler`. Same-map commands materialize/reuse one editor view per touched map/session, reject read-only maps, and fail before mutation when undo is unavailable.
- Marker persistence is additive-only: `<graph_group version="1"/>`; unknown versions remain unknown/inactive; stock Freeplane opens/saves the map; no existing meaning changes.
- Markers enclose the whole on-screen subtree representation, use fixed coral `#DF625D`, remain independent of four cloud shapes, sit outside an ordinary cloud, show inactive nested markers muted/dashed, and permit leaves. Folded descendants have no map-view geometry; the folded root represents them.
- Projection excludes persisted hidden and Freeplane-hidden-summary subtrees; visible summaries/free nodes use normal rules. Capture structural-leaf truth from model children before exclusion; fold/filter/view state does not define it.
- Boundary tiering counts active map registrations, including Loading/Missing, so availability does not restyle another map. Two or more active registrations make available roots emphatic; one suppresses its root and promotes first-level boundaries. A root unary chain remains emphatic; there are exactly two visible styles.
- Map-tier separation translates every particle of an unpinned map uniformly. Any pinned projected node makes that map immovable; one movable side takes full correction, two blocked sides retain pins and report Unpin.
- Workspace XML parsing disables DTDs/external entities. Loads/migrations are transactional. Non-finite or nonpositive numeric viewport fields are malformed and fail load; Fit Graph fallback applies only to a syntactically valid finite viewport whose visible world rectangle does not overlap the current graph. Saves use a same-directory temp and atomic replace; failure retains prior file, state, history, dirty flag, and Retry Save.
- Dirty close cancels debounce and synchronously saves. Failure leaves the session open for Retry/Discard/Cancel; no close silently discards state.
- There are three command scopes: application-level Open uses `GraphWorkspaceController`; main-editor `GraphGroupAction` uses a map actor without a workspace; every operation inside an existing Graph Workspace session uses `GraphWorkspaceHandle.execute(GraphCommand)`. Session window code never mutates stores/controllers directly.
- Purge and native contributor deletion carry displayed generation. On the EDT reject stale generation/pending map changes and revalidate exact current records immediately before mutation.
- Keyboard rule: unmodified arrows traverse endpoints when one is selected; unmodified arrows pan when none is selected; Shift+arrows always perform accelerated pan.
- Security/concurrency tasks include a named one-mechanism mutant after green. Save production SHA-256, apply with `apply_patch`, prove the named assertion fails, apply inverse immediately, verify SHA-256, rerun green, and confirm no mutant diff before staging.
- Every task stages only its exact `Files` paths, never a directory. Before `git add`, assert the index is empty; after staging, compare `git diff --cached --name-only` to the explicit task allowlist and abort on any extra/missing path.
- Keep changes minimal and remove superseded paths. Do not add compatibility fallbacks, duplicate execution paths, user force controls, map color editing, graph export, AI relationships, or source-map auto-save.
- Add only English source translations to viewer `Resources_en.properties`; do not bulk-edit Weblate-managed editor translations. Run `gradle format_translation` and ASCII validation.
- Every task commit starts `2026-08-10-graph-workspace:` and uses an imperative subject.

## Task 1: Scaffold the OSGi plugin and dependency policy

**Implementer tier:** Advanced

**Files:**
- Modify: `settings.gradle:1-end`
- Create: `freeplane_plugin_graph/build.gradle`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/Activator.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/GraphModeExtension.java`
- Create: `freeplane_plugin_graph/src/main/resources/META-INF/LICENSES/LGPL-3.0.txt`
- Create: `freeplane_plugin_graph/src/main/resources/META-INF/NOTICE.graphstream.txt`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/ActivatorShould.java`

**Interfaces:**
```java
public final class GraphModeExtension implements IModeControllerExtensionProvider, AutoCloseable {
    public void installExtension(ModeController modeController, CommandLineOptions options);
    public void close();
}
public final class Activator implements BundleActivator {
    public void start(BundleContext context);
    public void stop(BundleContext context);
}
```

- [ ] **Step 1: Add module and failing activation/packaging checks**

Declare `implementation project(':freeplane')`, `testImplementation 'com.tngtech.archunit:archunit:1.4.1'`, and exactly three non-transitive `lib` dependencies for gs-core/pherd/mbox2. Add module after formula. Test MindMap-only provider registration. Gradle checks initially fail for absent classes/notices/manifest.

- [ ] **Step 2: Run red**
```bash
gradle :freeplane_plugin_graph:test :freeplane_plugin_graph:check -PTestLoggingFull
```
Expected: FAIL; resolved `lib` contains no JUnit 4.12, gs-ui, or Scala.

- [ ] **Step 3: Implement lifecycle and exact policy checks**

Register resource loader and one extension service; stop closes extension then unregisters. Verify jars/checksums: gs-core `2d6a6f92f86c624fcbf468fc7e9cb9c8e3fb7e14c72ad578edb04cc36b0b66cd`, pherd `9e74f3702d13756faece5987147c937c09b6837a38ed32199f59c26697b94230`, mbox2 `3c2db334867211f385a2d62d061818268443f361381f78bbc53f9e897e145983`. Assert Bnd instruction `nothing.*`; generated headers absent/empty; bundle classpath exact; plugin classes major 52; dependencies <=52; notices packaged.

- [ ] **Step 4: Run green**
```bash
gradle :freeplane_plugin_graph:test :freeplane_plugin_graph:check :freeplane_plugin_graph:build -PTestLoggingFull
```
Expected: PASS.

- [ ] **Step 5: Commit exact allowlist**
```bash
test -z "$(git diff --cached --name-only)"
git add -- settings.gradle freeplane_plugin_graph/build.gradle freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/Activator.java freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/GraphModeExtension.java freeplane_plugin_graph/src/main/resources/META-INF/LICENSES/LGPL-3.0.txt freeplane_plugin_graph/src/main/resources/META-INF/NOTICE.graphstream.txt freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/ActivatorShould.java
git diff --cached --name-only
git commit -m "2026-08-10-graph-workspace: Add the graph plugin bundle"
```
Expected staged names: exactly the seven `Files` paths.

## Task 2: Define immutable workspace domain types

**Implementer tier:** Advanced

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/model/WorkspaceId.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/model/MapReferenceId.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/model/PersistedNodeId.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/model/NodeReference.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/model/RelationshipId.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/model/RelationshipDirection.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/model/WorkspaceCompatibility.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/model/MapReference.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/model/GraphRelationshipRecord.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/model/PinRecord.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/model/Viewport.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/model/DisplaySettings.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/model/UnknownXml.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/model/WorkspaceDocument.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/model/WorkspaceDomainShould.java`

**Interfaces:**
```java
public enum RelationshipDirection { FORWARD, BIDIRECTIONAL, UNDIRECTED }
public enum WorkspaceCompatibility { WRITABLE_VERSION_1, READ_ONLY_NEWER }
public final class NodeReference {
    public static NodeReference of(MapReferenceId map, PersistedNodeId node);
    public MapReferenceId mapReferenceId(); public PersistedNodeId nodeId();
}
public final class WorkspaceDocument {
    public static WorkspaceDocument createVersion1(WorkspaceId id);
    public WorkspaceId id(); public int formatVersion(); public int sourceFormatVersion();
    public WorkspaceCompatibility compatibility(); public List<MapReference> maps();
    public List<GraphRelationshipRecord> relationships(); public List<PinRecord> pins();
    public Viewport viewport(); public DisplaySettings displaySettings();
    public List<UnknownXml> unknownXml(); public Builder toBuilder();
    public static final class Builder {
        public Builder id(WorkspaceId id);
        public Builder sourceFormatVersion(int version);
        public Builder compatibility(WorkspaceCompatibility compatibility);
        public Builder maps(List<MapReference> maps);
        public Builder relationships(List<GraphRelationshipRecord> relationships);
        public Builder pins(List<PinRecord> pins);
        public Builder viewport(Viewport viewport);
        public Builder displaySettings(DisplaySettings settings);
        public Builder unknownXml(List<UnknownXml> unknownXml);
        public WorkspaceDocument build();
    }
}
```
All ID classes have `of(UUID/String)` and `value()`; all records expose typed getters and validating factories. Every `Builder` setter copies defensively and revalidates in `build()`, so Task 4 produces modified documents solely through `toBuilder()`.

- [ ] **Step 1: Write immutability/invariant tests**
Assert defensive copies/equality, finite values, unique IDs/sequences, valid references, deterministic order, and impossibility of transient keys/derived status.
- [ ] **Step 2: Run red**
```bash
gradle :freeplane_plugin_graph:test --tests '*WorkspaceDomainShould' -PTestLoggingFull
```
- [ ] **Step 3: Implement final Java 8 values**
No Lombok/records; explicit validation/equality.
- [ ] **Step 4: Run green**
```bash
gradle :freeplane_plugin_graph:test --tests '*WorkspaceDomainShould' -PTestLoggingFull
```
- [ ] **Step 5: Commit exact allowlist**
Stage each of the 15 `Files` paths explicitly, verify staged names equal the list, then:
```bash
git commit -m "2026-08-10-graph-workspace: Define workspace domain values"
```

## Task 3: Parse, migrate, and preserve workspace XML

**Implementer tier:** Capable

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/io/WorkspaceFormatException.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/io/WorkspaceXmlCodec.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/io/WorkspaceMigration.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/io/WorkspaceMigrationRegistry.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/io/WorkspaceXmlCodecShould.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/io/WorkspaceMigrationRegistryShould.java`
- Create: `freeplane_plugin_graph/src/test/resources/workspace/format-1-full.fpg`
- Create: `freeplane_plugin_graph/src/test/resources/workspace/format-newer-lossless.fpg`
- Create: `freeplane_plugin_graph/src/test/resources/workspace/format-newer-invalid.fpg`

**Interfaces:**
```java
public final class WorkspaceFormatException extends RuntimeException { public WorkspaceFormatException(String message, Throwable cause); }
public interface WorkspaceMigration { int fromVersion(); int toVersion(); WorkspaceDocument migrate(WorkspaceDocument source); }
public final class WorkspaceXmlCodec {
    public WorkspaceXmlCodec(WorkspaceMigrationRegistry migrations);
    public WorkspaceDocument read(Path file);
    public byte[] write(WorkspaceDocument document, Path location);
}
```

- [ ] **Step 1: Write secure codec tests**
Round trip all known/unknown fields, stale status ignored, deterministic bytes, complete migration chain, newer lossless read-only, newer invalid failure, read-only write rejection, XXE sentinel not read.
- [ ] **Step 2: Run red**
```bash
gradle :freeplane_plugin_graph:test --tests '*WorkspaceXmlCodecShould' --tests '*WorkspaceMigrationRegistryShould' -PTestLoggingFull
```
- [ ] **Step 3: Implement structured transactional parse/write**
Disable DTD/external entities/schema; validate/migrate locally; preserve unknown structured XML; never write during read.
- [ ] **Step 4: Mutant**
Enable DOCTYPE; `rejectExternalEntityWithoutReadingSentinel` fails; restore SHA and green.
- [ ] **Step 5: Commit after green exact nine-file allowlist**
```bash
gradle :freeplane_plugin_graph:test --tests '*WorkspaceXmlCodecShould' --tests '*WorkspaceMigrationRegistryShould' -PTestLoggingFull
git commit -m "2026-08-10-graph-workspace: Add secure workspace XML persistence"
```

## Task 4: Implement workspace transitions and history envelope

**Implementer tier:** Capable

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/WorkspaceCommand.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/WorkspaceTransition.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/WorkspaceCommands.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/WorkspaceHistory.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/WorkspaceCommandsShould.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/WorkspaceHistoryShould.java`

**Interfaces:**
```java
public interface WorkspaceCommand { WorkspaceTransition apply(WorkspaceDocument before); }
public final class WorkspaceTransition {
    public enum Status { APPLIED, NO_OP, REJECTED }
    public static WorkspaceTransition applied(WorkspaceDocument after, String key, Object... args);
    public static WorkspaceTransition noOp(WorkspaceDocument same, String key, Object... args);
    public static WorkspaceTransition rejected(WorkspaceDocument same, String key, Object... args);
    public Status status(); public WorkspaceDocument after(); public String messageKey(); public List<Object> messageArguments();
}
public final class WorkspaceHistory {
    public WorkspaceTransition execute(WorkspaceCommand command, WorkspaceDocument current);
    public WorkspaceTransition undo(WorkspaceDocument current); public WorkspaceTransition redo(WorkspaceDocument current);
    public boolean canUndo(); public boolean canRedo(); public void clear();
}
```
Undo/redo overlays current non-history envelope (`WorkspaceId`, source/format compatibility, viewport) onto historical undoable state. Save As clears history in Task 6 so old relative URIs never return.

- [ ] **Step 1: Write command/history tests**
Add/reactivate/remove/Locate, relationship CRUD/sequences, pin/unpin, display, purge exact IDs, redo clearing, non-applied history behavior; `pan -> undo/redo` preserves latest viewport.
- [ ] **Step 2: Run red**
```bash
gradle :freeplane_plugin_graph:test --tests '*WorkspaceCommandsShould' --tests '*WorkspaceHistoryShould' -PTestLoggingFull
```
- [ ] **Step 3: Implement immutable transitions/envelope merge**
Different URI always new registration; Locate alone rebinds; no content/hash API. Initial accessible palette: `#4E79A7 #F28E2B #59A14F #E15759 #76B7B2 #B07AA1 #EDC948 #9C755F`.
- [ ] **Step 4: Commit after green exact six-file allowlist**
```bash
gradle :freeplane_plugin_graph:test --tests '*WorkspaceCommandsShould' --tests '*WorkspaceHistoryShould' -PTestLoggingFull
git commit -m "2026-08-10-graph-workspace: Add workspace transitions and history"
```

## Task 5: Add URI resolution and atomic file replacement

**Implementer tier:** Advanced

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/AtomicWorkspaceWriter.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/WorkspaceUriResolver.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/WorkspaceSaveException.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/AtomicWorkspaceWriterShould.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/WorkspaceUriResolverShould.java`

**Interfaces:**
```java
public final class WorkspaceSaveException extends RuntimeException { public WorkspaceSaveException(Path target, Throwable cause); }
public interface AtomicWorkspaceWriter { void write(Path target, byte[] bytes) throws WorkspaceSaveException; }
public final class WorkspaceUriResolver {
    public URI toStoredUri(Path workspace, Path map); public Path resolve(Path workspace, URI stored);
    public URI rewriteForSaveAs(Path oldWorkspace, Path newWorkspace, URI stored);
    public Path canonical(Path path);
}
```

- [ ] **Step 1: Write path/failure tests**
Relative same-root, absolute cross-root, Save As rewrite, moved directory tree preserving relative layout, canonical equality, atomic failure prior bytes/temp cleanup.
- [ ] **Step 2: Run red**
```bash
gradle :freeplane_plugin_graph:test --tests '*AtomicWorkspaceWriterShould' --tests '*WorkspaceUriResolverShould' -PTestLoggingFull
```
- [ ] **Step 3: Implement same-directory temp + `ATOMIC_MOVE, REPLACE_EXISTING`**
No in-place fallback.
- [ ] **Step 4: Mutant**
Direct-write target; `atomicFailureRetainsPreviousFile` fails; restore.
- [ ] **Step 5: Commit after green exact five-file allowlist**
```bash
gradle :freeplane_plugin_graph:test --tests '*AtomicWorkspaceWriterShould' --tests '*WorkspaceUriResolverShould' -PTestLoggingFull
git commit -m "2026-08-10-graph-workspace: Add atomic workspace file writes"
```

## Task 6: Add store autosave, Save As, and close safety

**Implementer tier:** Capable

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/GraphCommandResult.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/WorkspaceIdentityChange.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/WorkspaceStoreEvent.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/WorkspaceStoreListener.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/ListenerRegistration.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/GraphWorkspaceStore.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/GraphWorkspaceStoreShould.java`

**Interfaces:**
```java
public final class GraphCommandResult {
    public enum Status { APPLIED, NO_OP, REJECTED }
    public static GraphCommandResult from(WorkspaceTransition transition);
    public GraphCommandResult withDirtySourceMaps(Set<MapReferenceId> maps);
    public GraphCommandResult withEditorViewActivated(boolean value);
    public GraphCommandResult withIdentityChange(WorkspaceIdentityChange change);
    public Status status(); public String messageKey(); public List<Object> messageArguments();
    public Set<MapReferenceId> dirtySourceMaps(); public boolean editorViewActivated();
    public Optional<WorkspaceIdentityChange> identityChange();
}
public final class WorkspaceIdentityChange { public Path oldPath(); public Path newPath(); public WorkspaceId oldId(); public WorkspaceId newId(); }
public interface WorkspaceStoreListener { void onWorkspaceStoreEvent(WorkspaceStoreEvent event); }
public interface ListenerRegistration extends AutoCloseable { void close(); }
public final class GraphWorkspaceStore implements AutoCloseable {
    public static GraphWorkspaceStore create(Path file, WorkspaceXmlCodec codec, AtomicWorkspaceWriter writer, ScheduledExecutorService scheduler);
    public static GraphWorkspaceStore open(Path file, WorkspaceXmlCodec codec, AtomicWorkspaceWriter writer, ScheduledExecutorService scheduler);
    public WorkspaceDocument currentDocument(); public GraphCommandResult execute(WorkspaceCommand command);
    public GraphCommandResult updateViewport(Viewport viewport); public GraphCommandResult undo(); public GraphCommandResult redo();
    public void saveNow(); public WorkspaceIdentityChange saveAs(Path target); public boolean isDirty();
    public ListenerRegistration addListener(WorkspaceStoreListener listener); public void discardAndClose(); public void close();
}
```
`WorkspaceStoreEvent` enum type is `DOCUMENT_CHANGED`, `IDENTITY_CHANGED`, `SAVED`, `SAVE_FAILED`; getters expose document/optional identity/error.

- [ ] **Step 1: Write store lifecycle tests**
Create/nonexistent, open/read-only, 150 ms autosave, Save, undo/redo autosave, viewport nonhistory, Save As new UUID/rewrite/typed event/history clear, `Save As -> undo/redo -> reopen` cannot restore old identity/URIs, prior-file failure/Retry, no `.mm` access, dirty close synchronous, failed close stays open, discard explicit, no writes after close.
- [ ] **Step 2: Run red**
```bash
gradle :freeplane_plugin_graph:test --tests '*GraphWorkspaceStoreShould' -PTestLoggingFull
```
- [ ] **Step 3: Implement one owner and clear history after successful Save As**
Only publish identity after atomic write succeeds.
- [ ] **Step 4: Mutants**
Skip close save -> immediate-close test fails; retain history after Save As -> old-URI test fails; restore.
- [ ] **Step 5: Commit after green exact seven-file allowlist**
```bash
gradle :freeplane_plugin_graph:test --tests '*GraphWorkspaceStoreShould' -PTestLoggingFull
git commit -m "2026-08-10-graph-workspace: Add workspace store lifecycle"
```

## Task 7: Project structural nodes and enclosures

**Implementer tier:** Capable

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/input/SourceNodeKey.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/input/SafeNodeLabel.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/input/NodeSnapshot.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/input/MapSnapshot.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/ProjectedNodeKey.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/ProjectedNode.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/EnclosureKey.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/EnclosureHullKey.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/ProjectedEnclosure.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/GraphProjection.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/ProjectionEngine.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/StructuralProjectionShould.java`

**Interfaces:**
```java
public final class SafeNodeLabel { public static SafeNodeLabel of(String full, String display); public String fullText(); public String displayText(); }
public final class NodeSnapshot {
    public SourceNodeKey key(); public SafeNodeLabel label(); public boolean structuralLeaf();
    public boolean graphGroup(); public boolean excluded(); public List<NodeSnapshot> children();
}
public final class MapSnapshot {
    public MapReferenceId mapReferenceId(); public int workspaceOrder(); public String mapName();
    public NodeSnapshot root(); public Set<PersistedNodeId> attachedPersistentIds();
    public boolean hasInaccessibleBranch(); public List<ConnectorSnapshot> connectors();
}
public final class GraphProjection {
    public long generation(); public List<ProjectedNode> nodes(); public List<ProjectedEnclosure> enclosures();
    public List<ProjectedEdge> edges(); public List<RelationshipResolution> relationshipResolutions();
    public List<PinProjection> pins(); public int projectedNodeCount(); public int projectedEdgeCount();
}
public final class ProjectionEngine {
    public GraphProjection projectStructure(long generation, WorkspaceDocument workspace, List<MapSnapshot> maps);
}
```
Task 7 creates `MapSnapshot` with an empty `connectors()` list and `GraphProjection` with empty `edges()`/`relationshipResolutions()`/`pins()`; Task 8 populates resolutions/pins and Task 9 populates connectors/edges. `attachedPersistentIds()` carries identity only and never exposes label text.
All output types are public immutable ordered values. Source key is persisted NodeReference or transient structural path.

- [ ] **Step 1: Tests**
Leaves independent fold/filter/view; groups/nested/leaf; hidden/summary exclusion; visible summary/free; locked leaf; enclosure/unary/branch; clone keys; visible parent with only hidden child remains non-leaf empty enclosure.
- [ ] **Step 2: Red**
```bash
gradle :freeplane_plugin_graph:test --tests '*StructuralProjectionShould' -PTestLoggingFull
```
- [ ] **Step 3: Implement precedence group -> structuralLeaf -> enclosure**
Continue safe identity traversal below groups while suppressing emission.
- [ ] **Step 4: Commit after green exact twelve-file allowlist**
```bash
gradle :freeplane_plugin_graph:test --tests '*StructuralProjectionShould' -PTestLoggingFull
git commit -m "2026-08-10-graph-workspace: Project map structure and enclosures"
```

## Task 8: Resolve endpoints, statuses, pins, and boundary tiers

**Implementer tier:** Capable

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/input/MapAvailability.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/input/ProjectionInput.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/ProjectedEndpointKey.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/BoundaryTier.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/RelationshipStatus.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/RecoverableReason.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/RelationshipResolution.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/PinProjection.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/ProjectedEnclosure.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/GraphProjection.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/ProjectionEngine.java:1-end`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/EndpointResolutionShould.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/EnclosureTierShould.java`

**Interfaces:**
```java
public enum MapAvailability { INACTIVE, LOADING, AVAILABLE, MISSING, UNREADABLE, PASSWORD_REQUIRED, RELOAD_REQUIRED }
public enum RelationshipStatus { ACTIVE, UNRESOLVED_RECOVERABLE, UNRESOLVED_MISSING_NODE }
public enum BoundaryTier { EMPHATIC, SUBTLE, SUPPRESSED }
public final class ProjectedEndpointKey {
    public static ProjectedEndpointKey ofNode(ProjectedNodeKey node);
    public static ProjectedEndpointKey ofEnclosure(EnclosureKey enclosure);
    public MapReferenceId mapReferenceId();
}
public final class ProjectionInput { public long generation(); public WorkspaceDocument workspace(); public List<MapSnapshot> maps(); public Map<MapReferenceId, MapAvailability> availability(); }
public final class ProjectionEngine { public GraphProjection project(ProjectionInput input); }
```

- [ ] **Step 1: Complete status/tier tests**
Inside group/leaf/enclosure/former group; availability table; attached excluded recoverable; inaccessible ambiguity recoverable; only fully accessible absence missing; dormant pins. Active registration count controls tiers through Loading/Missing/Retry/remove/reactivate.
- [ ] **Step 2: Red**
```bash
gradle :freeplane_plugin_graph:test --tests '*EndpointResolutionShould' --tests '*EnclosureTierShould' -PTestLoggingFull
```
- [ ] **Step 3: Implement ambiguity toward recoverable**
- [ ] **Step 4: Mutant**
Declare missing before inaccessible check; named test fails; restore.
- [ ] **Step 5: Commit after green exact thirteen-file allowlist**
```bash
gradle :freeplane_plugin_graph:test --tests '*EndpointResolutionShould' --tests '*EnclosureTierShould' -PTestLoggingFull
git commit -m "2026-08-10-graph-workspace: Resolve graph endpoints safely"
```

## Task 9: Consolidate edge contributors and direction coverage

**Implementer tier:** Advanced

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/input/ConnectorDescriptor.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/input/ConnectorSnapshot.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/ContributorKey.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/EdgeContributor.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/ProjectedEdgeKey.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/ProjectedEdge.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/DirectionCoverage.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/ProjectionDiff.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/input/MapSnapshot.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/GraphProjection.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/ProjectionEngine.java:1-end`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/EdgeProjectionShould.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/DirectionCoverageShould.java`

**Interfaces:**
```java
public final class ConnectorDescriptor {
    public SourceNodeKey source(); public NodeReference target(); public boolean arrowAtSource(); public boolean arrowAtTarget();
    public String sourceLabel(); public String middleLabel(); public String targetLabel();
}
public final class ConnectorSnapshot { public ContributorKey key(); public int occurrence(); public ConnectorDescriptor descriptor(); }
public final class ContributorKey {
    public static ContributorKey nativeConnector(MapReferenceId map, SourceNodeKey source, int occurrence);
    public static ContributorKey graphRelationship(RelationshipId relationship);
}
public final class DirectionCoverage {
    public static boolean covers(Collection<EdgeContributor> contributors, NodeReference source, NodeReference target, RelationshipDirection requested);
}
public final class ProjectionDiff { public static ProjectionDiff between(GraphProjection before, GraphProjection after); }
```

- [ ] **Step 1: Arrow/contributor/exact-pair tests**
Complete union truth table, canonical orientation, identical contributors retained, internal omission, deterministic order, exact-pair coverage.
- [ ] **Step 2: Red**
```bash
gradle :freeplane_plugin_graph:test --tests '*EdgeProjectionShould' --tests '*DirectionCoverageShould' -PTestLoggingFull
```
- [ ] **Step 3: Implement consolidation/diff**
- [ ] **Step 4: Mutant projected-pair coverage; named test fails; restore**
- [ ] **Step 5: Commit after green exact thirteen-file allowlist**
```bash
gradle :freeplane_plugin_graph:test --tests '*EdgeProjectionShould' --tests '*DirectionCoverageShould' -PTestLoggingFull
git commit -m "2026-08-10-graph-workspace: Consolidate projected relationships"
```

## Task 10: Prove projection determinism and pure reload equivalence

**Implementer tier:** Advanced

**Files:**
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/ProjectionDiff.java:1-end`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/ProjectionDeterminismShould.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/ProjectionPureReloadShould.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/testmodel/MutableProjectionScenario.java`

**Interfaces:** Consumes Tasks 3, 7-9; produces seeded property harness only.

- [ ] **Step 1: Seeded permutation tests**
Shuffle commutative structural/group/map/connector/relationship/pin/text operations; report seed; HashMap insertion independence; text-only stable keys; affected-only structural diff; pure codec/rebuilt snapshot equality.
- [ ] **Step 2: Run baseline**
```bash
gradle :freeplane_plugin_graph:test --tests '*ProjectionDeterminismShould' --tests '*ProjectionPureReloadShould' -PTestLoggingFull
```
- [ ] **Step 3: Normalize any failing order source**
- [ ] **Step 4: Named mutants**
Reverse map comparator -> `mapOrderIndependentOfHashInsertion` fails; classify text change remove/add -> `textOnlyRetainsPositionKeys` fails; restore.
- [ ] **Step 5: Commit after green exact four-file allowlist**
```bash
gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.projection.*' -PTestLoggingFull
git commit -m "2026-08-10-graph-workspace: Prove projection invariants"
```

## Task 11: Persist and edit Graph Group markers

**Implementer tier:** Capable

**Files:**
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/GraphModeExtension.java:1-end`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/group/GraphGroupModel.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/group/GraphGroupBuilder.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/group/GraphGroupController.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/group/GraphGroupAction.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/group/GraphGroupPersistenceShould.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/group/GraphGroupActionShould.java`
- Create: `freeplane_plugin_graph/src/test/resources/maps/graph-group-known.mm`
- Create: `freeplane_plugin_graph/src/test/resources/maps/graph-group-unknown-version.mm`

**Interfaces:**
```java
public final class GraphGroupModel implements IExtension { public static final int FORMAT_VERSION=1; public static boolean isMarked(NodeModel node); }
public final class GraphGroupController implements IExtension {
    public boolean isMarked(NodeModel node); public void setMarked(Collection<NodeModel> nodes, boolean marked);
    public int affectedClonePositionCount(Collection<NodeModel> nodes);
}
```
- [ ] **Step 1: Tests** known/unknown/plugin-disabled round trip, clones/copy, one undo/redo, event/count, leaf, no stamp/ID.
- [ ] **Step 2: Red** `gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.group.*' -PTestLoggingFull`
- [ ] **Step 3: Implement CloudBuilder/CloudAction pattern with shared actor and unknown preservation**
- [ ] **Step 4: Unknown-drop mutant fails; restore**
- [ ] **Step 5: Commit after green exact nine-file allowlist**
```bash
git commit -m "2026-08-10-graph-workspace: Add Graph Group map markers"
```

## Task 12: Add the NodeView decoration SPI and marker painter

**Implementer tier:** Capable

**Files:**
- Create: `freeplane/src/main/java/org/freeplane/view/swing/map/NodeViewDecorationPainter.java`
- Create: `freeplane/src/main/java/org/freeplane/view/swing/map/NodeViewDecorationRegistry.java`
- Modify: `freeplane/src/main/java/org/freeplane/view/swing/map/NodeView.java:1597-1672`
- Create: `freeplane/src/test/java/org/freeplane/view/swing/map/NodeViewDecorationRegistryTest.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/GraphModeExtension.java:1-end`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/group/GraphGroupMarkerPainter.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/group/GraphGroupMarkerPainterShould.java`

**Interfaces:**
```java
public interface NodeViewDecorationPainter { void paint(NodeView nodeView, Graphics2D graphics); }
public final class NodeViewDecorationRegistry implements IExtension {
    public static NodeViewDecorationRegistry of(ModeController modeController);
    public void add(NodeViewDecorationPainter painter); public void remove(NodeViewDecorationPainter painter);
    public boolean isEmpty();
}
```
These unsupported public classes live in an already-exported implementation package; no new package export is added. `of(ModeController)` lazily creates and installs the registry as a `ModeController` extension and returns the same instance thereafter. `NodeView` reads it during the CLOUDS pass via its own `ModeController`, treating absent/empty as a no-op; the plugin registers and unregisters its painter through the same accessor.

- [ ] **Step 1: Tests** no-registry unchanged; lazy `of(ModeController)` returns one installed instance; ordering/removal/duplicates; copied/disposed graphics isolation; whole visible subtree; coral/faint; four clouds; outer gap; inactive dashed; leaf; no geometry change.
- [ ] **Step 2: Red** run core and plugin paint tests.
- [ ] **Step 3: Add only CLOUDS-pass registry call; isolate each painter with `graphics.create()/dispose()`; teardown in extension close**
- [ ] **Step 4: Commit after green exact seven-file allowlist**
```bash
git commit -m "2026-08-10-graph-workspace: Render Graph Group decorations"
```

## Task 13: Manage viewless map leases and reload state

**Implementer tier:** Capable

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/EdtExecutor.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/MapOperationalState.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/MapAdapterEvent.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/MapAdapterListener.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/MapLease.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/MapLeaseManager.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/MapLeaseManagerShould.java`
- Create: `freeplane_plugin_graph/src/test/resources/maps/graph-projection.mm`

**Interfaces:**
```java
public interface EdtExecutor { <T> T call(Callable<T> task); void execute(Runnable task); boolean isEdt(); }
public enum MapOperationalState { LOADING, AVAILABLE, MISSING, UNREADABLE, PASSWORD_REQUIRED, RELOAD_REQUIRED }
public interface MapAdapterListener { void onMapAdapterEvent(MapAdapterEvent event); }
public interface MapLease extends AutoCloseable { MapReferenceId mapReferenceId(); MapOperationalState state(); void close(); }
public final class MapLeaseManager implements AutoCloseable {
    public CompletionStage<MapLease> acquire(MapReference reference); public void release(MapReferenceId id);
    public ListenerRegistration addListener(MapAdapterListener listener); public void close();
}
```

- [ ] **Step 1: Tests** canonical reuse; load on EDT without a view using `MapLoader.getMap()`; reference count; editor close; release; states; authoritative open/unsaved editor; clean manager-owned viewless external reload; no editor-owned replacement.
- [ ] **Step 2: Red** `gradle :freeplane_plugin_graph:test --tests '*MapLeaseManagerShould' -PTestLoggingFull`
- [ ] **Step 3: Implement exact ownership/reload**
Publish Loading, then EDT load without `.withView()`. For external save, only if model was created by manager, has no editor view, and is saved/clean: remove listeners, `MMapController.closeWithoutSaving(model)`, load replacement viewlessly, attach listeners. Editor-owned or dirty becomes RELOAD_REQUIRED; never close it.
- [ ] **Step 4: Listener-removal mutant fails; restore**
- [ ] **Step 5: Commit after green exact eight-file allowlist**
```bash
git commit -m "2026-08-10-graph-workspace: Add map lease lifecycle"
```

## Task 14: Extract confidentiality-safe node labels

**Implementer tier:** Capable

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/SafeNodeLabelExtractor.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/SafeNodeLabelExtractorShould.java`
- Create: `freeplane_plugin_graph/src/test/resources/maps/graph-safe-labels.mm`

**Interfaces:**
```java
public final class SafeNodeLabelExtractor {
    public static final int MAX_DISPLAY_CODE_POINTS=80;
    public SafeNodeLabel extract(NodeModel reachableNode);
}
```

- [ ] **Step 1: Tests** direct plain/HTML via `HtmlUtils`, formula/LaTeX/Markdown normalized source without evaluation, link URI/ID fallback without target dereference, icon/attachment fallback, newline collapse, full/display split, visible formula and link referencing hidden/locked sentinel never leak, idless numbered node never gets ID.
- [ ] **Step 2: Red** `gradle :freeplane_plugin_graph:test --tests '*SafeNodeLabelExtractorShould' -PTestLoggingFull`
- [ ] **Step 3: Implement closed conversion**
Never invoke `TextController.getTransformedObject`, `getTransformedText`, or `getPlainTransformedText`. Read only reachable node-owned raw content/extensions; strip presentation markup without resolving references.
- [ ] **Step 4: Mutant** replace closed converter with `getPlainTransformedText`; hidden-link/formula/idless tests fail; restore.
- [ ] **Step 5: Commit after green exact three-file allowlist**
```bash
git commit -m "2026-08-10-graph-workspace: Add safe graph labels"
```

## Task 15: Build safe map snapshots and traversal resolution

**Implementer tier:** Capable

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/MapSnapshotFactory.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/TraversalNodeResolver.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/MapLease.java:1-end`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/MapSnapshotFactoryShould.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/TraversalNodeResolverShould.java`
- Create: `freeplane_plugin_graph/src/test/resources/maps/graph-locked-branch.mm`
- Create: `freeplane_plugin_graph/src/test/resources/maps/graph-legacy-idless.mm`

**Interfaces:**
```java
public final class MapSnapshotFactory { public MapSnapshot snapshot(MapLease lease); }
public final class TraversalNodeResolver { public Optional<NodeModel> resolve(MapLease lease, SourceNodeKey key); }
```
`MapLease` gains package integration method `MapModel modelOnEdt()` guarded by EdtExecutor; no other package gets direct model.

- [ ] **Step 1: Tests** structuralLeaf before pruning; hidden/summary identity-only no label; show-hidden; group descendants; visible summary/free; locked leaf/inaccessible; idless transient/no mutation; resolver traversal; unlock restore.
- [ ] **Step 2: Red** run snapshot/resolver tests.
- [ ] **Step 3: Implement identity-only and label-safe passes on EDT**
- [ ] **Step 4: Flat-lookup mutant fails locked sentinel; restore**
- [ ] **Step 5: Commit after green exact seven-file allowlist**
```bash
git commit -m "2026-08-10-graph-workspace: Publish safe map snapshots"
```

## Task 16: Snapshot native connectors and enforce adapter boundaries

**Implementer tier:** Advanced

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/ConnectorSnapshotFactory.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/MapSnapshotFactory.java:1-end`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/ConnectorSnapshotFactoryShould.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/AdapterArchitectureShould.java`

**Interfaces:**
```java
public final class ConnectorSnapshotFactory { public List<ConnectorSnapshot> snapshotReachableConnectors(MapLease lease, MapSnapshot safeNodes); }
```
Each connector receives per-source occurrence plus full immutable `ConnectorDescriptor`; connector labels are direct raw connector-owned text with newline collapse, not target-node text.

- [ ] **Step 1: Tests** distinct identical occurrences, direction/three labels/order, unreachable source/target omission, architecture rejects flat ID/createID and Freeplane types in pure packages.
- [ ] **Step 2: Red** run connector/architecture tests.
- [ ] **Step 3: Enumerate reachable source `NodeLinks` on EDT and merge into immutable MapSnapshot**
- [ ] **Step 4: Commit after green exact four-file allowlist**
```bash
git commit -m "2026-08-10-graph-workspace: Snapshot native graph connectors"
```

## Task 17: Compute deterministic hull and attachment geometry

**Implementer tier:** Advanced

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/LayoutPoint.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/LayoutPositions.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/NodeGeometry.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/HullGeometry.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/HullIntersection.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/GraphGeometry.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/GraphGeometryEngine.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/HullGeometryShould.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/HullIntersectionShould.java`

**Interfaces:**
```java
public final class LayoutPoint { public static LayoutPoint of(double x, double y); public double x(); public double y(); }
public final class LayoutPositions {
    public static LayoutPositions of(Map<ProjectedNodeKey, LayoutPoint> nodes, Map<EnclosureHullKey, LayoutPoint> anchors);
    public Map<ProjectedNodeKey, LayoutPoint> nodes(); public Map<EnclosureHullKey, LayoutPoint> anchors();
}
public final class GraphGeometry {
    public Map<ProjectedNodeKey, NodeGeometry> nodes(); public Map<EnclosureHullKey, HullGeometry> hulls();
    public Map<EnclosureKey, LabelPlacement> labels();
    public LayoutPoint edgeAttachment(ProjectedEndpointKey endpoint, LayoutPoint toward);
}
public final class GraphGeometryEngine { public GraphGeometry computeHulls(GraphProjection projection, LayoutPositions positions); }
public final class HullIntersection { public static LayoutPoint minimumSeparatingTranslation(HullGeometry a, HullGeometry b); }
```
Task 17 leaves `labels()` empty; Task 18 populates it. All geometry public immutable ordered primitives.

- [ ] **Step 1: Tests** bottom-up, child containment, smooth deterministic closed path, empty enclosure label anchor, exact intersection, nearest attachment, deep equality.
- [ ] **Step 2: Red** run geometry tests.
- [ ] **Step 3: Implement convex padded exact polygon plus smooth paint path that does not cut inside child shapes**
- [ ] **Step 4: Commit after green exact nine-file allowlist**
```bash
git commit -m "2026-08-10-graph-workspace: Add graph hull geometry"
```

## Task 18: Place enclosure labels with bounded padding

**Implementer tier:** Advanced

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/GeometryTextMetrics.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/AwtGeometryTextMetrics.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/LabelPlacement.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/LabelPlacementEngine.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/GraphGeometry.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/GraphGeometryEngine.java:1-end`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/LabelPlacementShould.java`

**Interfaces:**
```java
public interface GeometryTextMetrics { Dimension2D measure(String displayText, BoundaryTier tier); }
public final class AwtGeometryTextMetrics implements GeometryTextMetrics { public AwtGeometryTextMetrics(Font font, FontRenderContext context); }
public final class LabelPlacementEngine { public GraphGeometry place(GraphProjection projection, GraphGeometry hulls, GeometryTextMetrics metrics); }
```

- [ ] **Step 1: Tests** interior, arc, external/leader, hover-only, padding cap, emphatic never hover, unary order, collisions/bounds; only displayText measured.
- [ ] **Step 2: Red** run label tests.
- [ ] **Step 3: Implement deterministic ladder with documented initial 1.5x cap**
- [ ] **Step 4: Commit after green exact seven-file allowlist**
```bash
git commit -m "2026-08-10-graph-workspace: Add bounded enclosure labels"
```

## Task 19: Implement private GraphStream typed physics

**Implementer tier:** Capable

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutEngine.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutCalibration.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutRequest.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutFrame.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutFactory.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/TypedSpringBox.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/TypedNodeParticle.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/GraphStreamBoundaryShould.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java`

**Interfaces:**
```java
public interface LayoutEngine extends AutoCloseable { LayoutFrame apply(LayoutRequest request); LayoutFrame step(); void reset(); void close(); }
public final class LayoutCalibration { public static LayoutCalibration spikeDefaults(); public double containment(); public double hierarchy(); public double sameMap(); }
public final class LayoutRequest {
    public static LayoutRequest of(WorkspaceId workspace, GraphProjection projection, ProjectionDiff diff, List<PinProjection> pins);
    public WorkspaceId workspace(); public GraphProjection projection(); public ProjectionDiff diff(); public List<PinProjection> pins();
}
public final class LayoutFrame {
    public long stepIndex(); public LayoutPositions positions(); public boolean failed();
}
public final class GraphStreamLayoutFactory { public static LayoutEngine create(LayoutCalibration calibration); }
```
Task 20 extends `LayoutFrame` with conflicts and idle measurement. Factory is public internal/GraphStream-free; engine/typed subclasses package-private. Protected GraphStream overrides inside package-private classes are allowed; externally visible signatures are GraphStream-free.

- [ ] **Step 1: Tests** boundary, no LayoutRunner, quality, seeds, particles/anchors/springs, ordered strengths, aggregate cap fanout, 100 churn.
- [ ] **Step 2: Red** run layout tests.
- [ ] **Step 3: Implement typed solver; use `MultiGraph` so distinct edges never trip simple-graph parallel-edge rejection; attach the layout sink before populating nodes/edges so every particle is created; cap aggregate once/particle; do not use layout.weight stiffness**
- [ ] **Step 4: Per-edge-cap mutant fails; restore**
- [ ] **Step 5: Commit after green exact ten-file allowlist**
```bash
git commit -m "2026-08-10-graph-workspace: Add private GraphStream physics"
```

## Task 20: Serialize layout work, pins, correction, and idle measurement

**Implementer tier:** Capable

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutConflict.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/PerceptualIdlePolicy.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/MapTierCorrection.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutWorker.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutFrame.java:1-end`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/LayoutWorkerShould.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/MapTierCorrectionShould.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/PerceptualIdlePolicyShould.java`

**Interfaces:**
```java
public final class PerceptualIdlePolicy {
    public static final class IdleMeasurement { public double rms(); public double max(); public int consecutiveStableFrames(); public boolean idle(); }
    public PerceptualIdlePolicy(int consecutive, double rms, double max);
    public IdleMeasurement observe(LayoutPositions before, LayoutPositions after);
}
public final class MapTierCorrection {
    public static final class CorrectionResult { public LayoutPositions positions(); public List<LayoutConflict> conflicts(); }
    public CorrectionResult apply(GraphProjection projection, LayoutPositions positions, GraphGeometry geometry);
}
public final class LayoutWorker implements AutoCloseable {
    public CompletionStage<LayoutFrame> submit(LayoutRequest request); public CompletionStage<LayoutFrame> step();
    public void pause(); public void restart(); public LayoutFrame lastValidFrame(); public void close();
}
```
`LayoutFrame` gains `public List<LayoutConflict> conflicts()` and `public PerceptualIdlePolicy.IdleMeasurement idle()` in this task.

- [ ] **Step 1: Tests** serialization, pin/dormant/unpin, rigid uniform correction, one/two blocked, conflicts, worker failure/restart, 25 close, measured idle injectable defaults.
- [ ] **Step 2: Red** run worker/correction/idle tests.
- [ ] **Step 3: Implement one executor and exact correction**
- [ ] **Step 4: Root-only and pinned-translation mutants fail; restore**
- [ ] **Step 5: Commit after green exact eight-file allowlist**
```bash
git commit -m "2026-08-10-graph-workspace: Add the owned layout worker"
```

## Task 21: Batch changes and capture projection input

**Implementer tier:** Capable

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/NanoClock.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/ChangeKind.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/AcceptedBatch.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/ProjectionBatcher.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/WorkspaceMapCoordinator.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/ProjectionBatcherShould.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/WorkspaceMapCoordinatorShould.java`

**Interfaces:**
```java
public interface NanoClock { long nanoTime(); }
public enum ChangeKind { TEXT, STRUCTURE, RELATIONSHIP, MAP_STATE, PIN, SETTINGS }
public final class AcceptedBatch { public long generation(); public long acceptedAtNanos(); public Set<ChangeKind> kinds(); }
public final class ProjectionBatcher { public void request(ChangeKind kind); public void close(); }
public final class WorkspaceMapCoordinator { public ProjectionInput capture(AcceptedBatch batch); }
```

- [ ] **Step 1: Tests** 150 ms coalescing, pending set synchronously on EDT, acceptance timestamp after debounce, EDT snapshots, complete input, close cancellation.
- [ ] **Step 2: Red** run control tests.
- [ ] **Step 3: Implement deterministic scheduler/clock injection**
- [ ] **Step 4: Debounce mutant**
Temporarily accept every request immediately; `burstCoalescesOnceAndTimestampsAfterDebounce` must fail. Restore exact SHA and rerun green.
- [ ] **Step 5: Commit after green exact seven-file allowlist**
```bash
git commit -m "2026-08-10-graph-workspace: Batch live map changes"
```

## Task 22: Publish repeated settling frames

**Implementer tier:** Capable

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/OperationalStatus.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/CanvasState.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/CanvasStateListener.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/LayoutSettleLoop.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/LayoutSettleLoopShould.java`

**Interfaces:**
```java
public interface CanvasStateListener { void onCanvasState(CanvasState state); }
public final class CanvasState { public long generation(); public GraphProjection projection(); public LayoutFrame layout(); public GraphGeometry geometry(); public OperationalStatus status(); }
public final class LayoutSettleLoop implements AutoCloseable {
    public CompletionStage<Void> start(AcceptedBatch batch, GraphProjection projection, ProjectionDiff diff, CanvasStateListener listener);
    public void pause(); public void restart(); public void close();
}
```

- [ ] **Step 1: Tests** serial step->correction->hull->label->stale check->EDT publish repeatedly until pause/failure/idle/close; multiple frames; no overlap; fake accepted workload allows >=10 complete publications/sec; coalesced swaps; stale cancellation; restart.
- [ ] **Step 2: Red** run settle tests.
- [ ] **Step 3: Implement chained futures; worker never touches Swing; EDT listener only**
- [ ] **Step 4: One-frame and stale-check mutants fail; restore**
- [ ] **Step 5: Commit after green exact five-file allowlist**
```bash
git commit -m "2026-08-10-graph-workspace: Publish settling layout frames"
```

## Task 23: Integrate live coordinator and immutable observation

**Implementer tier:** Capable

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/GraphProjectionListener.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/GraphUpdateCoordinator.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/GraphUpdateCoordinatorShould.java`

**Interfaces:**
```java
public interface GraphProjectionListener { void onGraphProjection(GraphProjection projection); }
public final class GraphUpdateCoordinator implements AutoCloseable {
    public void start(); public CanvasState currentState(); public GraphProjection currentProjection();
    public ListenerRegistration addCanvasStateListener(CanvasStateListener listener);
    public ListenerRegistration addProjectionListener(GraphProjectionListener listener);
    public void requestRebuild(ChangeKind kind); public boolean hasPendingChanges();
    public void pauseLayout(); public void restartLayout(); public void resetLayout(); public void close();
}
```

- [ ] **Step 1: Tests** initial loading/empty, generation ordering, projection/diff/settle composition, label-only positions, failures retain last frame/new seeds, ordered listeners, no callbacks after close.
- [ ] **Step 2: Red** run coordinator test.
- [ ] **Step 3: Implement state owner and event wiring**
- [ ] **Step 4: Pending-state mutant**
Temporarily leave `hasPendingChanges()` false after a queued event; `queuedChangeIsVisibleBeforeDebounceAcceptance` must fail. Restore exact SHA and rerun green.
- [ ] **Step 5: Commit after green exact three-file allowlist**
```bash
git commit -m "2026-08-10-graph-workspace: Publish live immutable graph state"
```

## Task 24: Paint viewport, themes, and adaptive detail

**Implementer tier:** Capable

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphCanvas.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphViewport.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphTheme.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/RenderingLevel.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/AdaptiveRenderingPolicy.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPaintState.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphViewportShould.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/AdaptiveRenderingPolicyShould.java`

**Interfaces:**
```java
public final class GraphCanvas extends JComponent {
    public void setCanvasState(CanvasState state); public void setPaintState(GraphPaintState state);
    public void setViewport(GraphViewport viewport); public GraphViewport viewport(); public void fitGraph(); public void resetZoom();
}
public final class GraphViewport {
    public static GraphViewport of(double centerX, double centerY, double zoom);
    public static GraphViewport from(Viewport persisted);
    public double centerX(); public double centerY(); public double zoom();
    public Viewport toPersisted(); public boolean overlaps(double minX, double minY, double maxX, double maxY, Dimension size);
}
public final class GraphPaintState {
    public static GraphPaintState empty();
    public GraphPaintState withSelection(ProjectedEndpointKey selected);
    public GraphPaintState withHover(ProjectedEndpointKey hovered);
    public GraphPaintState withSearchMatches(Set<ProjectedEndpointKey> matches);
    public Optional<ProjectedEndpointKey> selection(); public Optional<ProjectedEndpointKey> hover();
    public Set<ProjectedEndpointKey> searchMatches();
}
public final class AdaptiveRenderingPolicy { public RenderingLevel forCounts(int nodes, int edges); public boolean exceedsEngineeringTarget(int nodes, int edges); }
```
Task 25 extends `GraphPaintState` with connection preview and dim state. GraphPainter is package-private and paints normal JComponent/offscreen tests; no print/export API.

- [ ] **Step 1: Transform/policy/BufferedImage tests** layer order, tiers, arrows/multiplicity, labels, states/themes/bounds, node LOD exact, warning if nodes>2000 or edges>5000, above-target remains editable. `GraphViewportShould` asserts malformed non-finite/nonpositive persisted values are rejected by Task 2/3 before canvas construction, while a finite persisted viewport whose visible world rectangle does not overlap current graph bounds invokes Fit Graph.
- [ ] **Step 2: Red** run canvas tests.
- [ ] **Step 3: Implement full-bleed immutable paint and EDT-local viewport**
- [ ] **Step 4: Commit after green exact ten-file allowlist**
```bash
git commit -m "2026-08-10-graph-workspace: Paint the graph canvas"
```

## Task 25: Add hit testing, search, and interaction intents

**Implementer tier:** Capable

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/InteractionTool.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphHitIndex.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphIntent.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionListener.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphSearchModel.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphCanvas.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPaintState.java:1-end`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphInteractionControllerShould.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphSearchModelShould.java`

**Interfaces:**
```java
public interface GraphInteractionListener { void onGraphIntent(GraphIntent intent); }
public final class GraphInteractionController { public void install(GraphCanvas canvas); public void uninstall(); public void setTool(InteractionTool tool); public void setRelationshipDirection(RelationshipDirection direction); }
```
GraphIntent concrete public nested types: OpenSourceNode, Pin, Unpin, UnpinAll, Connect, InspectEdge, DeleteContributor, DeleteAllContributors, ChangeSelection.

- [ ] **Step 1: Synthetic tests** exact hits, select/hover/dim, open, pointer zoom, pan when no selection, selected arrows reserved for traversal, Shift arrows accelerated pan always, pin/unpin semantics, preview/Esc, inspection, search full safe text, hover tooltip full safe text, uninstall.
- [ ] **Step 2: Red** run interaction/search tests.
- [ ] **Step 3: Implement transient state and intents only**
- [ ] **Step 4: Commit after green exact ten-file allowlist**
```bash
git commit -m "2026-08-10-graph-workspace: Add graph interaction intents"
```

## Task 26: Expose keyboard traversal and accessible virtual children

**Implementer tier:** Advanced

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/TraversalDirection.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphTraversalOrder.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvas.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphCanvas.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java:1-end`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvasShould.java`

**Interfaces:**
```java
public enum TraversalDirection { UP, DOWN, LEFT, RIGHT }
public final class GraphTraversalOrder {
    public List<ProjectedEndpointKey> tabOrder(CanvasState state);
    public Optional<ProjectedEndpointKey> nearest(CanvasState state, ProjectedEndpointKey from, TraversalDirection direction);
}
```
`nearest` filters to the requested screen-space half-plane, then chooses minimum squared distance with deterministic endpoint order as the tie-breaker. AccessibleGraphCanvas is package-private context implementation.

- [ ] **Step 1: Tests** Tab order; selected unmodified arrows traverse; none-selected arrows pan; Shift arrows pan regardless; Enter/Esc; virtual children role/name/full safe label+map/state/action; no color-only/excluded text.
- [ ] **Step 2: Red** run accessibility test.
- [ ] **Step 3: Implement context rule and virtual children without per-node Swing components**
- [ ] **Step 4: Commit after green exact six-file allowlist**
```bash
git commit -m "2026-08-10-graph-workspace: Make the graph keyboard accessible"
```

## Task 27: Execute native connector edits and source navigation safely

**Implementer tier:** Capable

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/command/MapUndoTarget.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/command/ViewMaterializationTracker.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/command/FreeplaneMapCommandExecutor.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/command/SourceNavigation.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/command/FreeplaneMapCommandExecutorShould.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/command/SourceNavigationShould.java`

**Interfaces:**
```java
public final class FreeplaneMapCommandExecutor {
    public GraphCommandResult createConnector(SourceNodeKey source, SourceNodeKey target, RelationshipDirection direction);
    public GraphCommandResult deleteConnector(ContributorKey key, ConnectorDescriptor expected);
    public GraphCommandResult undoCurrentSourceMap(); public Optional<MapUndoTarget> currentUndoTarget();
}
public final class MapUndoTarget { public MapReferenceId mapReferenceId(); public String mapName(); public boolean canUndo(); }
public final class SourceNavigation { public GraphCommandResult open(SourceNodeKey source); }
```

- [ ] **Step 1: Tests** materialize/reuse/three maps; read-only; undo guard; one transaction; no workspace/source save; dirty result; direction; traversal select; idless rejection with normal-save-once message; use `addConnector(source,target.getID())`; delete re-enumerates occurrence and exact descriptor.
- [ ] **Step 2: Red** run command tests.
- [ ] **Step 3: Implement preflight then actor transaction**
- [ ] **Step 4: Undo-guard mutant fails; restore**
- [ ] **Step 5: Commit after green exact six-file allowlist**
```bash
git commit -m "2026-08-10-graph-workspace: Execute source-map graph commands safely"
```

## Task 28: Reserve workspace paths for live sessions

**Implementer tier:** Advanced

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/WorkspaceSessionId.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/WorkspacePathReservation.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/WorkspaceSessionRegistry.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/WorkspaceSessionRegistryShould.java`

**Interfaces:**
```java
public interface WorkspacePathReservation extends AutoCloseable { void commit(WorkspaceIdentityChange change); void close(); }
public final class WorkspaceSessionRegistry {
    public boolean register(WorkspaceSessionId id, Path canonicalPath);
    public Optional<WorkspaceSessionId> owner(Path canonicalPath);
    public WorkspacePathReservation reserveSaveAs(WorkspaceSessionId id, Path canonicalTarget);
    public void unregister(WorkspaceSessionId id);
}
```

- [ ] **Step 1: Tests** canonical ownership, duplicate focus lookup, concurrent reservations serialized, target owned by another session rejected before bytes, failure releases reservation, commit rekeys old->new atomically.
- [ ] **Step 2: Red** run registry tests.
- [ ] **Step 3: Implement synchronized reservation table independent of store**
- [ ] **Step 4: Atomic-rekey mutant**
Temporarily remove old-path ownership before installing new-path ownership; `commitRekeysWithoutObservableUnownedWindow` must fail under an injected barrier. Restore exact SHA and rerun green.
- [ ] **Step 5: Commit after green exact four-file allowlist**
```bash
git commit -m "2026-08-10-graph-workspace: Reserve live workspace paths"
```

## Task 29: Route workspace and relationship commands

**Implementer tier:** Capable

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/command/GraphCommand.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/command/GraphCommands.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/command/PurgeCommandHandler.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/command/ContributorDeletionHandler.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/command/GraphCommandRouter.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/command/GraphCommandRouterShould.java`

**Interfaces:**
```java
public interface GraphCommand { }
public final class GraphCommands {
    public static final class Purge implements GraphCommand { public long displayedGeneration(); public Set<RelationshipId> relationships(); }
    public static final class DeleteContributor implements GraphCommand { public long displayedGeneration(); public ContributorKey contributor(); public Optional<ConnectorDescriptor> expectedConnector(); }
    public static final class DeleteAllContributors implements GraphCommand { public long displayedGeneration(); public ProjectedEdgeKey edge(); public List<ContributorKey> contributors(); public Map<ContributorKey, ConnectorDescriptor> expectedConnectors(); }
    public static Purge purge(long displayedGeneration, Set<RelationshipId> relationships);
    public static DeleteContributor deleteContributor(long displayedGeneration, ContributorKey contributor, ConnectorDescriptor expected);
    public static DeleteAllContributors deleteAllContributors(long displayedGeneration, ProjectedEdgeKey edge, List<ContributorKey> contributors, Map<ContributorKey, ConnectorDescriptor> expected);
}
public interface PurgeCommandHandler { GraphCommandResult purge(GraphCommands.Purge command); }
public interface ContributorDeletionHandler { GraphCommandResult deleteOne(GraphCommands.DeleteContributor command); GraphCommandResult deleteAll(GraphCommands.DeleteAllContributors command); }
public final class GraphCommandRouter {
    public GraphCommandResult execute(GraphCommand command); public Optional<MapUndoTarget> currentMapUndoTarget();
}
```
GraphCommands factories cover session map add/retry/remove/Locate, relationship CRUD, generation-bound purge/delete, pin/unpin, display, viewport, workspace undo/redo, source-map undo, Save/Retry Save/Save As, layout pause/restart/reset, connect, and source open. Map Retry re-acquires the existing registration's lease without changing its UUID/URI and rejects inactive registrations with an explicit reason. Application Open and GraphGroupAction are intentionally absent.

- [ ] **Step 1: Tests** every session command routed; map Retry re-acquires the same active registration and rejects inactive registrations; same/cross map; self/coverage; transient rejection with save-once; workspace/map undo; display/viewport/save/layout/nav; Save As reserves via Task28 before `store.saveAs`, commits on success, releases on failure, and another live target leaves bytes/identity/registry unchanged.
- [ ] **Step 2: Red** run router tests with fake purge/deletion handlers.
- [ ] **Step 3: Implement one session intent boundary**
- [ ] **Step 4: Save As reservation mutant**
Temporarily call `store.saveAs` before acquiring `WorkspacePathReservation`; `saveAsCannotWriteBeforeReservation` must fail. Restore exact SHA and rerun green.
- [ ] **Step 5: Commit after green exact six-file allowlist**
```bash
git commit -m "2026-08-10-graph-workspace: Route graph session commands"
```

## Task 30: Revalidate and execute purge safely

**Implementer tier:** Capable

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/command/DefaultPurgeCommandHandler.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/command/DefaultPurgeCommandHandlerShould.java`

**Interfaces:**
```java
public final class DefaultPurgeCommandHandler implements PurgeCommandHandler {
    public DefaultPurgeCommandHandler(GraphUpdateCoordinator updates, GraphWorkspaceStore store, EdtExecutor edt);
    public GraphCommandResult purge(GraphCommands.Purge command);
}
```
Purge command carries displayed generation and relationship IDs.

- [ ] **Step 1: Tests** empty disabled/rejected; stale generation; pending change; any ACTIVE/RECOVERABLE rejects all; lock/reload queued before purge protects record; same EDT validation+transition; only UNRESOLVED_MISSING_NODE; undo restores.
- [ ] **Step 2: Red** run purge tests.
- [ ] **Step 3: Implement exact current-state revalidation**
- [ ] **Step 4: Remove generation/pending check mutant fails; restore**
- [ ] **Step 5: Commit after green exact two-file allowlist**
```bash
git commit -m "2026-08-10-graph-workspace: Protect purge from stale state"
```

## Task 31: Revalidate contributor deletion and owner-local undo

**Implementer tier:** Capable

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/command/ContributorDeletionPlan.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/command/DefaultContributorDeletionHandler.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/command/ContributorDeletionPlanShould.java`

**Interfaces:**
```java
public final class DefaultContributorDeletionHandler implements ContributorDeletionHandler {
    public DefaultContributorDeletionHandler(GraphUpdateCoordinator updates, GraphWorkspaceStore store, FreeplaneMapCommandExecutor maps, EdtExecutor edt);
    public GraphCommandResult deleteOne(GraphCommands.DeleteContributor command);
    public GraphCommandResult deleteAll(GraphCommands.DeleteAllContributors command);
}
```
Delete commands carry displayed generation, contributor key(s), and exact ConnectorDescriptor/relationship ID.

- [ ] **Step 1: Tests** stale/pending rejection; native list insertion/deletion/property change between inspector and command rejects; exact current descriptor re-resolves on EDT; relationship exact ID; delete-one owner; delete-all prevalidates all, one map entry per map + one workspace entry, rollback before commit on failure; later undo remains owner-local and explained.
- [ ] **Step 2: Red** run deletion tests.
- [ ] **Step 3: Implement generation+descriptor revalidation and compensation**
- [ ] **Step 4: Occurrence-only mutant deletes wrong connector and test fails; restore**
- [ ] **Step 5: Commit after green exact three-file allowlist**
```bash
git commit -m "2026-08-10-graph-workspace: Delete exact graph contributors"
```

## Task 32: Implement workspace controller, handle, and close lifecycle

**Implementer tier:** Capable

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/GraphWorkspaceOpenException.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/GraphWorkspaceController.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/GraphWorkspaceHandle.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/GraphWorkspaceViewBinding.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/GraphWorkspaceView.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/GraphWorkspaceViewFactory.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/WorkspaceCloseController.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceHandle.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceController.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/DefaultGraphWorkspaceControllerShould.java`

**Interfaces:**
```java
public final class GraphWorkspaceOpenException extends RuntimeException { public GraphWorkspaceOpenException(Path path, Throwable cause); }
public interface GraphWorkspaceController { GraphWorkspaceHandle open(Path workspaceFile); }
public interface GraphWorkspaceHandle extends AutoCloseable {
    GraphProjection currentProjection(); GraphCommandResult execute(GraphCommand command);
    ListenerRegistration addProjectionListener(GraphProjectionListener listener); void close();
}
public interface GraphWorkspaceViewBinding {
    CanvasState currentCanvasState(); ListenerRegistration addCanvasStateListener(CanvasStateListener listener);
}
public interface GraphWorkspaceView { void show(); void focus(); void close(); }
public interface GraphWorkspaceViewFactory { GraphWorkspaceView create(GraphWorkspaceHandle handle, GraphWorkspaceViewBinding binding, WorkspaceCloseController close); }
```
WorkspaceCloseController has `saveAndClose`, `retrySaveAndClose`, `discardAndClose`, `cancelClose`.

- [ ] **Step 1: Tests** nonexistent create; existing; malformed no partial/write; newer read-only; duplicate path focuses existing; distinct sessions; SaveAs rekey; exact projection and canvas observation; close ordering/save failure stays open/Retry/Discard/Cancel; immediate reopen latest; release all.
- [ ] **Step 2: Red** run controller tests.
- [ ] **Step 3: Construct only after successful load/create; use Task28 registry and actual Task30/31 handlers; exact close order**
- [ ] **Step 4: Skip-close-save mutant fails; restore**
- [ ] **Step 5: Commit after green exact ten-file allowlist**
```bash
git commit -m "2026-08-10-graph-workspace: Own workspace session lifecycle"
```

## Task 33: Build the modeless Swing workspace shell and panels

**Implementer tier:** Capable

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/SwingGraphWorkspaceViewFactory.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/MapListPanel.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceToolbar.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceSettingsPanel.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java`

**Interfaces:** public Swing factory implements Task32 factory; window is package-private JFrame. Toolbar receives both application `GraphWorkspaceController` for Open and existing-session handle for all other controls.

- [ ] **Step 1: Headless tests** menu/toolbar/map list/full canvas/settings/status slot layout, no nested cards, stable sizes, all approved controls/settings/map row states, LAF, read-only disabling, application Open calls controller, session controls handle, no setVisible in tests. Initial construction applies the workspace's persisted viewport through `GraphCanvas.setViewport`; if its finite visible world rectangle does not overlap current graph bounds, it calls `fitGraph`.
- [ ] **Step 2: Red** run window model tests.
- [ ] **Step 3: Implement thin Swing shell and panel models, including persisted-viewport application and Fit Graph fallback**
- [ ] **Step 4: Commit after green exact six-file allowlist**
```bash
git commit -m "2026-08-10-graph-workspace: Build the graph workspace shell"
```

## Task 34: Add status, contributor, purge, and close dialogs

**Implementer tier:** Capable

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/ContributorInspector.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/PurgeConfirmationDialog.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphStatusBar.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceCloseDialog.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java:1-end`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/WorkspaceDialogsShould.java`

**Interfaces:** dialogs receive immutable view models and emit GraphCommand or close-controller methods; no store access.

- [ ] **Step 1: Tests** every status field, warning either limit, Retry/Restart/Unpin, safe contributor labels/owners, generation-bound delete/purge, purge disabled/no recoverable/list both endpoints, close Retry/Discard/Cancel, editor activation restores graph focus.
- [ ] **Step 2: Red** run dialog/status tests.
- [ ] **Step 3: Implement headless-testable models plus thin dialogs**
- [ ] **Step 4: Commit after green exact six-file allowlist**
```bash
git commit -m "2026-08-10-graph-workspace: Add graph workspace operational UI"
```

## Task 35: Wire plugin actions, menus, icons, i18n, and undo keys

**Implementer tier:** Capable

**Files:**
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/GraphModeExtension.java:1-end`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/OpenGraphWorkspaceAction.java`
- Create: `freeplane_plugin_graph/src/main/resources/images/GraphGroup.svg`
- Create: `freeplane_plugin_graph/src/main/resources/images/GraphWorkspace.svg`
- Modify: `freeplane/src/external/resources/xml/mindmapmodemenu.xml:55-65`
- Modify: `freeplane/src/external/resources/xml/mindmapmodemenu.xml:290-305`
- Modify: `freeplane/src/viewer/resources/freeplane.properties:750-810`
- Modify: `freeplane/src/viewer/resources/translations/Resources_en.properties:1-end`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/UndoRoutingShould.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphPluginIntegrationShould.java`

**Interfaces:** OpenGraphWorkspaceAction uses application controller. GraphGroupAction remains direct map actor. Window Ctrl+Z/Y emit session workspace commands; explicit source-map undo emits session command.

- [ ] **Step 1: Tests** plugin gating/resources, existing/new chooser, GraphGroup beside cloud/clone tooltip, action scopes, undo keys/menu enabled names, every session control handle route.
- [ ] **Step 2: Red** run integration tests.
- [ ] **Step 3: Register controller/view/persistence/painter/actions; XML entries; icons; English keys**
- [ ] **Step 4: Run tests/core compile/format/ASCII**
```bash
gradle :freeplane_plugin_graph:test -PTestLoggingFull
gradle :freeplane:compileJava
gradle format_translation
file freeplane/src/editor/resources/translations/Resources_*.properties freeplane/src/viewer/resources/translations/Resources_en.properties | grep -v "ASCII text"
test -z "$(git diff --name-only -- freeplane/src/editor/resources/translations)"
test "$(git diff --name-only -- freeplane/src/viewer/resources/translations)" = "freeplane/src/viewer/resources/translations/Resources_en.properties"
```
- [ ] **Step 5: Commit exact nine-path allowlist**
Stage the nine distinct paths from `Files` (`mindmapmodemenu.xml` is one path with two edited regions), verify staged names equal that list, then:
```bash
git commit -m "2026-08-10-graph-workspace: Wire graph workspace entry points"
```

## Task 36: Add generated production performance diagnostics

**Implementer tier:** Capable

**Files:**
- Modify: `freeplane_plugin_graph/build.gradle:1-end`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/NearestRankPercentile.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GeneratedWorkspace.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/PerformanceMeasurements.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GraphWorkspacePerformanceDiagnostic.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/PerformanceTripwiresShould.java`

**Interfaces:** Gradle task `graphPerformanceDiagnostic`; output under `build/graph-performance/` including exact `two-map.fpg`, `three-map.fpg`, `reference-2000-5000.fpg`; `NearestRankPercentile.of(sortedNanos,p)` uses index `ceil(p*N)-1`.

- [ ] **Step 1: Add Gradle task and failing ledger/tripwire test**
Task exists but fails because generator/diagnostic classes absent.
- [ ] **Step 2: Run red**
```bash
gradle :freeplane_plugin_graph:graphPerformanceDiagnostic -PTestLoggingFull
```
- [ ] **Step 3: Implement exact reference** 20 maps, 2000 nodes, 5000 edges (3500/1500), 1200 anchors, 2000 containment, 1180 hierarchy, 3200 particles, 8180 springs, seed 20260810, 400 warmup, 300 samples. Measure snapshot/projection/diff/mutation/force/correction/hull/label/full worker/EDT swap/repaint/accepted-batch-first-frame. Start at AcceptedBatch.acceptedAtNanos; end after EDT CanvasState reference assignment; injected NanoClock. Normal CI 5x strict ceiling plus exact invariants. Add deterministic stress variants for two and three maps, one map holding at least 80 percent of projected nodes, concentrated cross-map clusters, and one/two pinned maps.
- [ ] **Step 4: Run green**
```bash
gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.performance.*' -PTestLoggingFull
gradle :freeplane_plugin_graph:graphPerformanceDiagnostic -PTestLoggingFull
```
- [ ] **Step 5: Commit exact six-file allowlist**
```bash
git commit -m "2026-08-10-graph-workspace: Add graph performance diagnostics"
```

## Task 37: Calibrate and pass strict production targets

**Implementer tier:** Capable

**Files:**
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutCalibration.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/PerceptualIdlePolicy.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/GraphUpdateCoordinator.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/GraphGeometryEngine.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java:1-end`
- Create: `docs/superpowers/specs/2026-08-10-graph-workspace-performance-report.md`

**Interfaces:** consumes Task36 exact diagnostics; produces strict passing calibration/report before acceptance.

- [ ] **Step 1: Run strict baseline**
```bash
gradle :freeplane_plugin_graph:graphPerformanceDiagnostic -PgraphStrictPerformance -PTestLoggingFull
```
Targets: force p95<=50ms, full worker p95<=100ms, accepted-batch-first-frame p95<=150ms/p99<=300ms, EDT swap p95<=2ms.
- [ ] **Step 2: Tune only measured bottlenecks**
Keep O(N+E) rebuild unless proven; fixed quality/cap/rigid pins/determinism/immutable publication; calibrate ordered multipliers/idle traces.
- [ ] **Step 3: Re-run strict and focused tests**
- [ ] **Step 4: Write environment/ledger/percentile/results/calibration report**
- [ ] **Step 5: Commit exact seven-file allowlist**
```bash
git commit -m "2026-08-10-graph-workspace: Calibrate graph performance"
```

## Task 38: Verify persistence, projection, geometry, and marker acceptance

**Implementer tier:** Capable

**Files:**
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceModelAcceptanceShould.java`

**Interfaces:** consumes design scenarios and production Tasks 1-37.

- [ ] **Step 1: Implement named scenarios**
01 reopen restores maps/viewport/pins/colors/settings; 02 only structural leaves/groups including hidden-only-child enclosure; 03 required enclosures/interior fixture labels; 04 outer marker/inner reactivate; 05 duplicate connectors; 06 opposite arrows; 07 collapsed internal omitted; 10 removed map dormant/reactivate; 12 ungroup attaches ancestor; 13 persisted pin/settling; 18 single root suppressed; 19 second active restyles/loading missing no flicker; 23 clone marker composition; 26 save workspace then externally move entire `.fpg` plus relative `maps/` directory tree and reopen resolving paths; 27 stock reader marker; 28 four clouds; 29 inactive nested visible.
- [ ] **Step 2: Run red/green**
```bash
gradle :freeplane_plugin_graph:test --tests '*GraphWorkspaceModelAcceptanceShould' -PTestLoggingFull
```
- [ ] **Step 3: Run strict Task37 result as scenario 15 prerequisite and assert report says PASS**
- [ ] **Step 4: Commit exact one-file allowlist**
```bash
git commit -m "2026-08-10-graph-workspace: Verify graph model acceptance"
```

## Task 39: Verify command, security, and UI acceptance

**Implementer tier:** Capable

**Files:**
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceCommandAcceptanceShould.java`

**Interfaces:** consumes production command/window Tasks 25-35 and performance report Task37.

- [ ] **Step 1: Implement named scenarios**
08 same-map native connector/map undo/dirty; 09 cross-map only `.fpg`; 11 delete endpoint/map undo/reactivate; 14 full pan/zoom/fit/reset/search/hover/select/open/inspect; 16 ID-less persistent command rejects atomically and displays explicit normal "open and save the map once" request, then a normal map save assigns the ordinary file ID and the reissued command succeeds with that ID; 17 dense three-map distinction; 20 pinned conflict/Unpin; 21 locked no leak/purge; 22 missing purge list/undo; 24 one tab per map/reuse; 25 multiplicity cue or no-op reason. Also Save As live-target rejection and separate histories.
- [ ] **Step 2: Run tests with security mutants restored**
```bash
gradle :freeplane_plugin_graph:test --tests '*GraphWorkspaceCommandAcceptanceShould' -PTestLoggingFull
```
- [ ] **Step 3: Commit exact one-file allowlist**
```bash
git commit -m "2026-08-10-graph-workspace: Verify graph command acceptance"
```

## Task 40: Prove production cold reload and lifecycle cleanup

**Implementer tier:** Capable

**Files:**
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceColdReloadShould.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceLifecycleShould.java`

**Interfaces:** consumes production controller/store/Freeplane writer/actors/leases/workers.

- [ ] **Step 1: Randomized production cold reload**
Create temp `.mm` with views/IUndoHandler; apply randomized normal actors/connectors/groups; explicitly save maps as test-user action; save `.fpg`; close handle/store/leases; reopen all through production controller/loader; compare state/projection. Workspace never invokes map save.
- [ ] **Step 2: Lifecycle** 25 open/close/restart; listener/lease/view/timer/temp/thread baseline; close during debounce; failed close Retry/Discard/Cancel; no callbacks after close.
- [ ] **Step 3: Run tests and prescribed close/stale mutants, restore exact SHA**
```bash
gradle :freeplane_plugin_graph:test --tests '*GraphWorkspaceColdReloadShould' --tests '*GraphWorkspaceLifecycleShould' -PTestLoggingFull
```
- [ ] **Step 4: Commit exact two-file allowlist**
```bash
git commit -m "2026-08-10-graph-workspace: Prove graph reload and cleanup"
```

## Task 41: Automate OSGi smoke, UI evidence, and final verification

**Implementer tier:** Capable

**Files:**
- Modify: `freeplane_plugin_graph/build.gradle:1-end`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/smoke/GraphPluginOsgiSmoke.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/smoke/GraphWorkspaceUiEvidence.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/smoke/FreeplaneLaunchSmoke.java`
- Create: `docs/superpowers/specs/2026-08-10-graph-workspace-implementation-verification.md`
- Create: `docs/superpowers/specs/images/2026-08-10-graph-workspace-implemented.png`
- Create: `docs/superpowers/specs/images/2026-08-10-graph-group-marker-implemented.png`

**Interfaces:** Gradle tasks `graphOsgiSmoke`, `graphUiEvidence`, `freeplaneLaunchSmoke`; no human/manual checkpoint.

- [ ] **Step 1: Add failing smoke/evidence tasks**
OSGi probe uses actual `BIN/framework.jar`/`BIN/props.xargs`, installs the built bundle, asserts ACTIVE, accepted three jars/classes/operation, requests framework stop, and asserts every plugin-owned worker/timer ended. UI evidence constructs the real full root panel on EDT, loads deterministic two-map state, dispatches synthetic interactions, paints desktop/narrow images, asserts nonblank/no overlap, and writes the declared PNGs. Launch smoke starts `BIN/freeplane.sh` in an isolated test user directory, waits for the graph bundle ACTIVE log, requests normal application shutdown, and waits 15 seconds. If framework-owned threads keep the process alive, send TERM and wait 5 seconds; fail if the process or any plugin-owned graph worker remains, and record whether TERM was required.
- [ ] **Step 2: Run smoke tasks**
```bash
gradle :freeplane_plugin_graph:graphOsgiSmoke :freeplane_plugin_graph:graphUiEvidence :freeplane_plugin_graph:freeplaneLaunchSmoke -PTestLoggingFull
```
- [ ] **Step 3: Run full verification**
```bash
gradle :freeplane_plugin_graph:clean :freeplane_plugin_graph:check :freeplane_plugin_graph:test :freeplane_plugin_graph:build -PTestLoggingFull
gradle :freeplane_plugin_graph:graphPerformanceDiagnostic -PgraphStrictPerformance -PTestLoggingFull
gradle :freeplane:compileJava
gradle format_translation
git diff --exit-code -- freeplane/src/editor/resources/translations freeplane/src/viewer/resources/translations
gradle test -PTestLoggingFull
file freeplane/src/editor/resources/translations/Resources_*.properties freeplane/src/viewer/resources/translations/Resources_en.properties | grep -v "ASCII text"
rg -n "LayoutRunner|getNodeForID|getNodeFromID_|createID|fingerprint|provenance" freeplane_plugin_graph/src/main/java
```
Expected all pass; ASCII filter/forbidden production scan no prohibited path.
- [ ] **Step 4: Write final report**
Record license/checksum/manifest, exact environment/commands/test counts, performance report link, all 29 scenario results, lifecycle, OSGi/launch smoke, images, and residual hardware risk.
- [ ] **Step 5: Commit exact seven-file allowlist**
```bash
git commit -m "2026-08-10-graph-workspace: Verify Graph Workspace end to end"
```
