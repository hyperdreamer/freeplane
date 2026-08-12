# Confidentiality-Safe Node Label Extractor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for readability; controller state is canonical.

- **Task Identifier:** 2026-08-10-graph-workspace
- **Original backlog task:** Task 14, Extract confidentiality-safe node labels

**Goal:** Add a small Freeplane adapter that turns one already-reachable `NodeModel` into an immutable full/display label without evaluating formulas, resolving links, reading unreachable nodes, or assigning node identity.

**Architecture:** `SafeNodeLabelExtractor` is a deep module with one public operation, `extract(NodeModel)`. Its implementation is a closed conversion over the supplied node's own raw object, local format extension, direct hyperlink, and direct icons; it never enters `TextController` or traverses to another node. It returns the existing pure `SafeNodeLabel` value, retaining normalized full text and deriving a code-point-safe display label.

**Tech Stack:** Java 8 source/bytecode, Freeplane `NodeModel`/`NodeStyleModel`/`NodeLinks`/icon model APIs, `HtmlUtils`, Gradle, JUnit 4, AssertJ, and the existing `freeplane_plugin_graph` module.

## Global Constraints

- Follow `AGENTS.md`: Java source and target are 8, UTF-8, four-space indentation, JUnit 4/AssertJ/Mockito, and builds use escalated `gradle`, not Maven or the wrapper.
- Use Java at `/home/henry/.sdkman/candidates/java/21.0.8-zulu`; every Gradle command sets that exact `JAVA_HOME` and prepends its `bin` to `PATH`.
- This continuation starts from pushed `main` commit `85a0b51b70251a4dc67b9042ca5e13c0cb5cda87` and implements only original Graph Workspace Task 14.
- The implementation allowlist is exactly three paths: `SafeNodeLabelExtractor.java`, `SafeNodeLabelExtractorShould.java`, and `graph-safe-labels.mm`. Do not modify build files, core Freeplane code, `freeplane_api`, existing projection values, translations, launchers, or prior Task 13 files.
- Add no dependency. `freeplane_plugin_graph` already depends on `freeplane`; do not depend on formula, LaTeX, Markdown, script, or AI plugins.
- `extract(NodeModel)` accepts only a node already proven reachable by structural traversal. Production callers will invoke it on the EDT; the extractor does not search for, resolve, or manufacture another node.
- Read only the supplied node's own raw object, direct `NodeStyleModel` format, direct `NodeLinks` hyperlink, and direct icon list. Never read children, parents, map ID indexes, target nodes, notes, details, attributes, inherited styles, views, filters, or transformed content.
- Never call or reference `TextController`, any method whose name starts `getTransformed`, `getPlainTransformedText`, `MapModel.getNodeForID`, `MMapController.getNodeFromID_`, `NodeModel.createID`, `NodeLinks.getValidLink`, URL/file opening, or image loading.
- Direct HTML conversion uses only `HtmlUtils.htmlToPlain`. Formula, `FormattedFormula`, LaTeX, and Markdown labels retain normalized non-evaluated source. A local link label retains only its literal URI or `#ID` string and never target text.
- Fallback order after normalized raw content is direct hyperlink string, then first direct icon description/name, then literal `Node`. The returned `SafeNodeLabel` is never empty.
- Collapse every whitespace run to one ASCII space and trim it. Do not normalize case, resolve entities outside `HtmlUtils`, execute markup, or derive node numbering.
- `MAX_DISPLAY_CODE_POINTS` is exactly 80. If full text exceeds 80 Unicode code points, display text is the first 77 code points followed by ASCII `...`; never split a surrogate pair. Otherwise display text equals full text.
- The security mutant is mandatory: after green, replace the closed raw conversion with `TextController.getController().getPlainTransformedText(reachableNode)`, prove the named hostile-transformer confidentiality tests fail, restore the exact production SHA-256 immediately, and rerun green.
- Before staging, assert the index is empty. Stage only the exact three implementation paths, compare the cached-name list to the allowlist, and commit with exactly `2026-08-10-graph-workspace: Add safe graph labels`.

## Task 1: Extract confidentiality-safe node labels

**Implementer tier:** Capable

**Files:**

- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/SafeNodeLabelExtractor.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/SafeNodeLabelExtractorShould.java`
- Create: `freeplane_plugin_graph/src/test/resources/maps/graph-safe-labels.mm`

**Interfaces:**

- Consumes: one non-null `org.freeplane.features.map.NodeModel` that the caller has already established is reachable, plus existing `org.freeplane.plugin.graph.projection.input.SafeNodeLabel` with `SafeNodeLabel.of(String full, String display)`.
- Produces:

```java
package org.freeplane.plugin.graph.adapter;

import org.freeplane.features.map.NodeModel;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;

public final class SafeNodeLabelExtractor {
    public static final int MAX_DISPLAY_CODE_POINTS = 80;

    public SafeNodeLabel extract(NodeModel reachableNode);
}
```

- Interface invariants: `reachableNode` is required; `fullText()` and `displayText()` are non-empty normalized plain/source strings; extraction does not mutate the node or map; display text contains at most 80 code points.
- Read-only implementation references: `SafeNodeLabel.java`, `NodeModel.java`, `NodeStyleModel.java`, `NodeLinks.java`, `NamedIcon.java`, `IconDescription.java`, `HtmlUtils.java`, `FormattedFormula.java`, `IFormattedObject.java`, `MarkdownRenderer.java`, `LatexRenderer.java`, and the real-loader setup in `MapLeaseManagerShould.loadsTheRealFixtureThroughMapLoaderOnTheSuppliedEdtWithoutCreatingAView`.

- [ ] **Step 1: Create the real `.mm` fixture and failing behavioral tests**

Create `graph-safe-labels.mm` as a minimal valid Freeplane map containing directly addressable nodes for all persisted representations below. Use stable IDs except for the explicitly idless node.

```xml
<map version="freeplane 1.12.0">
  <node TEXT="safe labels" ID="ID_ROOT">
    <node TEXT="Plain label" ID="ID_PLAIN"/>
    <node TEXT="&lt;html&gt;&lt;body&gt;&lt;p&gt;HTML &lt;b&gt;label&lt;/b&gt;&lt;/p&gt;&lt;p&gt;second line&lt;/p&gt;&lt;/body&gt;&lt;/html&gt;" ID="ID_HTML"/>
    <node TEXT="=node['ID_HIDDEN'].text" ID="ID_FORMULA"/>
    <node TEXT="\latex   $x_2 = 3$" ID="ID_LATEX_PREFIX"/>
    <node TEXT="A_{m,n} = B" FORMAT="latexPatternFormat" ID="ID_LATEX_FORMAT"/>
    <node TEXT="**Markdown** [hidden](#ID_HIDDEN)" FORMAT="markdownPatternFormat" ID="ID_MARKDOWN"/>
    <node TEXT="" LINK="#ID_HIDDEN" ID="ID_LOCAL_LINK"/>
    <node TEXT="" ID="ID_ICON"><icon BUILTIN="idea"/></node>
    <node TEXT="" LINK="file:/private/report.pdf" ID="ID_ATTACHMENT"><icon BUILTIN="attach"/></node>
    <node TEXT="first&#xa;&#x9;second   third" ID="ID_WHITESPACE"/>
    <node TEXT="idless numbered node"/>
    <node TEXT="locked container" ENCRYPTED_CONTENT="opaque" ID="ID_LOCKED"/>
    <node TEXT="HIDDEN_LOCKED_SENTINEL" ID="ID_HIDDEN"/>
  </node>
</map>
```

If the stock reader rejects the fixture's intentionally opaque encrypted payload, keep the persisted locked container in the fixture with a reader-accepted encrypted value from an existing Freeplane fixture and build the detached locked-sentinel condition in test setup. Do not weaken the locked-sentinel assertion or add a production parser.

In `SafeNodeLabelExtractorShould`, use two layers of tests:

1. Load the real fixture with the same `FreeplaneHeadlessStarter`/`MapLoader` pattern already proven by `MapLeaseManagerShould`. Assert the stock reader yields the exact raw text, direct node format, link, icon, and null ID representations before testing extraction.
2. Use small in-memory `MapModel`/`NodeModel` setups for hostile-transformer and identity probes. Install a real `TextController` in a test `ModeController`, add an `IContentTransformer` that returns `HIDDEN_LOCKED_SENTINEL` whenever transformation is invoked, and restore the prior global `Controller` in `finally`/`@After`.

Add named tests covering all requirements:

```java
@Test public void extractsDirectPlainAndHtmlTextFromTheRealFixture();
@Test public void keepsFormulaLatexAndMarkdownAsNormalizedUnevaluatedSource();
@Test public void usesLiteralLinkWithoutDereferencingItsHiddenTarget();
@Test public void fallsBackToDirectIconAndAttachmentDescriptions();
@Test public void collapsesWhitespaceAndSplitsFullFromCodePointBoundedDisplayText();
@Test public void doesNotEnterTransformersForAFormulaReferencingALockedSentinel();
@Test public void doesNotEnterTransformersForALocalLinkReferencingALockedSentinel();
@Test public void doesNotAssignAnIdToANumberedIdlessNode();
@Test public void productionSourceContainsNoTransformationResolutionOrIdentityCalls();
```

Required assertions:

- Plain text is `Plain label`; HTML is converted by `HtmlUtils.htmlToPlain` and normalized to `HTML label second line`.
- Formula full text remains `=node['ID_HIDDEN'].text`; it never becomes the sentinel or a calculated value.
- Prefix LaTeX removes only the leading `\latex`/`\unparsedlatex` marker and following whitespace; format-tagged LaTeX keeps its source. Markdown keeps its source syntax while whitespace is normalized; no Markdown renderer, PlantUML extension, or link target is invoked.
- The local link label is exactly `#ID_HIDDEN`, even when the ID index still contains a detached/locked target with text `HIDDEN_LOCKED_SENTINEL`.
- The external attachment label is exactly its direct link string `file:/private/report.pdf`; an icon-only node uses the first direct icon's translated description, falling back to icon name if the description is blank. Neither case loads an icon bitmap or opens a URI.
- Whitespace collapses to `first second third`.
- A string containing 78 non-BMP emoji code points followed by `TAIL` keeps the complete full text; display has exactly 80 code points, consists of the first 77 code points plus `...`, and contains no unpaired surrogate.
- For both hostile-transformer tests, transformer invocation count remains zero and neither full nor display text contains `HIDDEN_LOCKED_SENTINEL` unless that literal is the supplied reachable node's own raw text.
- Set local node numbering true on an attached node whose `getID()` is null. Before and after extraction, `getID()` remains null and the map's registry has not gained an entry for it.
- The production-source guard reads only `SafeNodeLabelExtractor.java` and rejects these tokens: `TextController`, `getTransformed`, `getPlainTransformedText`, `getNodeForID`, `getNodeFromID_`, `createID`, `getValidLink`, `getChildren`, `getParent`, `toUrl`, `Files.`, and `Paths.`.

- [ ] **Step 2: Run the focused test and confirm the red phase**

```bash
env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-14 \
  :freeplane_plugin_graph:test --tests '*SafeNodeLabelExtractorShould' \
  -PTestLoggingFull --rerun-tasks
```

Expected: FAIL because `SafeNodeLabelExtractor` does not exist. Confirm the failure is compilation/missing-production behavior, not a malformed fixture or headless environment failure.

- [ ] **Step 3: Implement the closed conversion**

Implement one public class and private helpers only. Keep the conversion order explicit:

```java
public SafeNodeLabel extract(final NodeModel reachableNode) {
    Objects.requireNonNull(reachableNode, "reachableNode");
    final String full = firstNonEmpty(
        normalizedRawContent(reachableNode),
        normalizedDirectLink(reachableNode),
        normalizedDirectIcon(reachableNode),
        "Node");
    return SafeNodeLabel.of(full, displayText(full));
}
```

Use a closed raw-content conversion rather than a generic transformer:

- Read `reachableNode.getUserObject()` exactly once.
- For `FormattedFormula`, unwrap `getObject()` and preserve that source string.
- For other `IFormattedObject` values, unwrap `getObject()` once if it is a scalar (`CharSequence`, `Number`, `Boolean`, `Character`, `URI`, or `Hyperlink`); otherwise use the already-stored object's own string form without invoking a controller.
- For `String`/`CharSequence`, call `HtmlUtils.htmlToPlain` only when `HtmlUtils.isHtml` is true.
- Read only `NodeStyleModel.getNodeFormat(reachableNode)` for local format classification. Recognize exact local strings `latexPatternFormat`, `unparsedLatexPatternFormat`, and `markdownPatternFormat`; do not ask `NodeStyleController` for inherited/effective format.
- Remove only a leading `\latex` or `\unparsedlatex` token when it is followed by whitespace. Keep formula `=` source and Markdown/LaTeX source syntax; normalization then collapses whitespace.
- Treat raw `Hyperlink` and `URI` values as their literal string values without `toUrl()`.
- For fallback links call `NodeLinks.getLink(reachableNode)`, not `getValidLink`; use `Hyperlink.toString()` only.
- Inspect only `reachableNode.getIcons()`. For its first direct icon, use `IconDescription.getTranslatedDescription()` when available and nonblank, then `NamedIcon.getName()`. Never call `getIcon()`.
- Implement whitespace normalization by Unicode code point or `Character.isWhitespace`/`Character.isSpaceChar`; emit one ASCII space between runs and trim without changing other code points.
- Implement display truncation with `codePointCount` and `offsetByCodePoints`. The `...` is part of the 80-code-point limit.

Do not add a second extractor interface, injected converter, registry, plugin dependency, target resolver, compatibility path, or cache. The one-method module is the test seam.

- [ ] **Step 4: Run focused green and inspect the exact implementation scope**

```bash
env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-14 \
  :freeplane_plugin_graph:test --tests '*SafeNodeLabelExtractorShould' \
  -PTestLoggingFull --rerun-tasks
git status --short
git diff --check
```

Expected: focused tests PASS with zero failures/errors; status names exactly the three task files plus ignored SDD artifacts, and `git diff --check` is clean.

- [ ] **Step 5: Prove the confidentiality regression with one isolated mutant**

Record production and test SHA-256 values. Apply a temporary one-mechanism mutant that replaces the closed raw conversion at the start of `extract` with `TextController.getController().getPlainTransformedText(reachableNode)` and uses that transformed value as the label. Do not weaken, skip, rename, or edit the tests for the mutant.

Run only these tests:

```bash
env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-14 \
  :freeplane_plugin_graph:test \
  --tests '*SafeNodeLabelExtractorShould.doesNotEnterTransformersForAFormulaReferencingALockedSentinel' \
  --tests '*SafeNodeLabelExtractorShould.doesNotEnterTransformersForALocalLinkReferencingALockedSentinel' \
  --tests '*SafeNodeLabelExtractorShould.doesNotAssignAnIdToANumberedIdlessNode' \
  -PTestLoggingFull --rerun-tasks
```

Expected: the two sentinel tests fail because the hostile transformer was entered and the sentinel leaked; the ID assertion must also fail if the forbidden transformed path derives numbering/identity in the installed harness. If the ID assertion remains green, that is acceptable only when both confidentiality assertions fail for the intended reason; record the exact result rather than claiming all three failed.

Immediately apply the inverse patch, verify both files match their recorded SHA-256 values exactly, confirm no mutant diff remains, and rerun the complete focused class green. A mutant that does not make both confidentiality tests fail is not evidence; strengthen the hostile transformer setup before proceeding.

- [ ] **Step 6: Run the module and compatibility gates**

```bash
env JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu \
  PATH=/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin:$PATH \
  gradle -p /data/home/guest/Development/freeplane/.worktrees/graph-workspace-task-14 \
  :freeplane_plugin_graph:check -PTestLoggingFull --rerun-tasks

/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin/javap -verbose \
  freeplane_plugin_graph/build/classes/java/main/org/freeplane/plugin/graph/adapter/SafeNodeLabelExtractor.class \
  | rg 'major version: 52'

/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin/javap -public \
  freeplane_plugin_graph/build/classes/java/main/org/freeplane/plugin/graph/adapter/SafeNodeLabelExtractor.class
```

Expected: `check` and `verifyGraphBundle` PASS with zero failures/errors; bytecode major version is 52; public surface is only the class, `MAX_DISPLAY_CODE_POINTS`, public constructor, and `extract(NodeModel)`. Confirm `git diff --check` is clean, `git diff -- freeplane_api` is empty, and the implementation diff contains no forbidden tokens from Step 1.

- [ ] **Step 7: Stage the exact allowlist and commit**

```bash
test -z "$(git diff --cached --name-only)"
git add -- \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/SafeNodeLabelExtractor.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/SafeNodeLabelExtractorShould.java \
  freeplane_plugin_graph/src/test/resources/maps/graph-safe-labels.mm
git diff --cached --name-only
git commit -m "2026-08-10-graph-workspace: Add safe graph labels"
```

Expected staged names: exactly the three `Files` paths, with no SDD artifact, plan, generated resource, build output, core file, or prior-task file. After commit, `git status --short` is clean except ignored controller artifacts.
