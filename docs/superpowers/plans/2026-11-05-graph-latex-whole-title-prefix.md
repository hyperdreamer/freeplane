# Graph Workspace Whole-Title `\latex` Prefix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic subagent-driven-development controller to implement this plan task-by-task.

**Goal:** Make whole-title `\latex` declarations extract as one formula, measure literal degraded text, and ensure painted math rows use the exact strings accepted by placement.

**Architecture:** Add a budget-free literal measurement API, then derive formula declarations and bodies separately from the existing byte-stable `fullText` and capped `displayText`. Update only the two `placeMathNode` row inputs, add the prescribed extracted-label and 250-title tests, and finish with production-path and application evidence.

**Tech Stack:** Java 8-compatible source, JUnit 4, AssertJ, AWT font metrics, bundled JLaTeXMath, Java 21 for verification, and Gradle. Add no dependencies.

**Normative inputs:** `docs/superpowers/specs/2026-11-05-graph-latex-whole-title-prefix-spec.md` (authoritative), `docs/superpowers/specs/2026-11-05-graph-latex-whole-title-prefix-design.md`, and `docs/superpowers/specs/2026-11-03-graph-latex-titles-spec.md` (context only).

**File map:** Production changes are confined to `canvas/title/TitleMeasurer.java`, `adapter/SafeNodeLabelExtractor.java`, and the two specified expressions in `canvas/ScreenLabelPlacement.java`. Tests are confined to `TitleMeasurerShould.java`, `SafeNodeLabelExtractorShould.java`, `GraphTitleFormulaShould.java`, `ScreenLabelPlacementShould.java`, and the new `canvas/title/LatexWholeTitleSampleShould.java`. Generated sample and application evidence goes under `freeplane_plugin_graph/doc/latex-whole-title/` and `freeplane_plugin_graph/build/evidence/`.

## Global Constraints

- Use Java 8 syntax and four-space indentation. Run build gates with Java 21 and `gradle`, not `gradlew` or Maven; if the required toolchain is absent, stop and request PM/user approval before installing packages.
- Keep all authored implementation, test, and evidence files within `freeplane_plugin_graph`; keep any temporary acceptance checkout and its generated `BIN`, `DIST`, and build outputs inside `freeplane_plugin_graph/doc/latex-whole-title/`, then remove that checkout after capture.
- Keep `SafeNodeLabel.fullText()` and `displayText()` byte-for-byte identical to current extraction for every node. Keep the 80-code-point display cap, 77-code-point prefix plus literal `...`, and code-point indexing unchanged.
- Preserve ordinary inline parsing through `TitleTextParser.parse(displayText, false)` when no whole-title signal exists. Do not modify `TitleText`, `TitleTextParser`, search, accessibility, hull geometry, or enclosure-label construction.
- Change only `SafeNodeLabelExtractor` whole-title/body derivation, the additive `TitleMeasurer.measureDegradedText` method and its isolated cache namespace, and the two authorized `placeMathNode` row-source expressions.
- Do not implement deferred items D1-D6. Do not change enclosure labels, slot caps, fonts, slots, rungs, leaders, obstacles, retention, `truncateTo`, fallback visibility/flags, dependencies, bundle metadata, or `freeplane_plugin_graph/build.gradle`.
- The task lane sequence is intentionally serial: measurement API, extractor, placement rows, sample harness, then acceptance evidence. Each task commits only its assigned scoped changes using its stated subject.

## Task 1: Add Literal Degraded-Text Measurement

**Lane:** latex-title
**Category:** literal-degraded-measurement-api
**Implementer tier:** Standard
**Depends on:** none

Never execute `reboot`, `shutdown`, `poweroff`, `halt`, `kexec`, any `systemctl` power or sleep verb, `init 0|6`, or `telinit 0|6`; never restart or stop the display manager or `graphical.target`; never terminate your session or signal the process tree that hosts the PM.

**Files:**
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/title/TitleMeasurer.java:22-40,109-170`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/title/TitleMeasurerShould.java:1-180`

**Interfaces:**
- Produces: `public MeasuredTitle measureDegradedText(String text, Font font, boolean deferredForBudget)` on `TitleMeasurer`.
- Consumes: `TitleMetrics.SCREEN_FRC: FontRenderContext`; `TitleText.plain(String text): TitleText`; `MeasuredRun.plain(String text, double x, double width, double ascent, double descent): MeasuredRun`; `MeasuredTitle.degraded(List<MeasuredRun> runs, double width, double height, double ascent, double descent): MeasuredTitle`; `MeasuredTitle.deferred(): MeasuredTitle`.

**Tests:**
- `org.freeplane.plugin.graph.canvas.title.TitleMeasurerShould.degradedTextIsMeasuredFromItsOwnString` (T8)

### Steps

- [ ] Add T8 using `text = "literal $a$"` and `new Font(Font.SANS_SERIF, Font.PLAIN, 12)`. Begin a pass; call the new API with `true` and `false`, then call `measure(TitleText.plain(text), font)`. Assert both literal measurements are `DEGRADED`, each has one plain run whose source is exactly `text`, the exact `font.getStringBounds(text, TitleMetrics.SCREEN_FRC)` width, height `Math.max(ascent + descent, 1.0e-6)`, and deferred flags true/false respectively. Before the separate title measurement, assert zero new measurements, zero deferred count, zero pending count, and no exhausted budget; assert the plain title measurement is `RENDERED`.
- [ ] Run `gradle :freeplane_plugin_graph:test --tests '*TitleMeasurerShould'` with the method absent and confirm the expected compile-red unresolved `measureDegradedText(String, Font, boolean)`. Do not commit the intermediate red state.
- [ ] Add `DEGRADED_TEXT` to the existing `TitleMeasurer.Form` namespace. Use a key equivalent to `(text, font.getFamily(), font.getStyle(), font.getSize2D(), DEGRADED_TEXT)`; with the existing `Key` representation, key it by `TitleText.plain(text)` plus `Form.DEGRADED_TEXT` so it cannot collide with `measure(TitleText.plain(text), font)`.
- [ ] Implement the public method by reading `Rectangle2D bounds = font.getStringBounds(text, TitleMetrics.SCREEN_FRC)`, setting `width = bounds.getWidth()`, `ascent = -bounds.getY()`, `descent = bounds.getHeight() + bounds.getY()`, and `height = Math.max(ascent + descent, 1.0e-6)`. Create exactly one `MeasuredRun.plain(text, 0.0, width, ascent, descent)` and a `MeasuredTitle.degraded(...)`. Cache that undeferred measurement; return it for `false` and `cached.deferred()` for `true` on each call.
- [ ] Do not call `consumeBudgetSlot()`, `defer(...)`, or mutate `pending`, `deferredInPass`, or any budget/stall state. Keep `measure`, `measureDegraded`, `defer`, queue behavior, and the existing `DEGRADED` namespace unchanged.
- [ ] Run `gradle :freeplane_plugin_graph:test --tests '*TitleMeasurerShould'` and require T8 and the existing class tests to pass.

**Commit:** `feat(graph): add literal degraded text measurement (2026-11-05-graph-latex-whole-title-prefix)`

## Task 2: Derive Whole-Title Formula Sources

**Lane:** latex-title
**Category:** whole-title-extraction
**Implementer tier:** Standard
**Depends on:** Task 1

Never execute `reboot`, `shutdown`, `poweroff`, `halt`, `kexec`, any `systemctl` power or sleep verb, `init 0|6`, or `telinit 0|6`; never restart or stop the display manager or `graphical.target`; never terminate your session or signal the process tree that hosts the PM.

**Files:**
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/adapter/SafeNodeLabelExtractor.java:1-177`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/adapter/SafeNodeLabelExtractorShould.java:1-591`

**Interfaces:**
- Consumes: `sourceText(Object rawContent): String`, `convertHtml(String source): String`, `removeLatexPrefix(String source, String localFormat): String`, `hasWhitespaceAfter(String source, int prefixLength): boolean`, `isWhitespace(int codePoint): boolean`, `normalizeWhitespace(String value): String`, and `displayText(String fullText): String` from `SafeNodeLabelExtractor`; `TitleTextParser.parse(String source, boolean wholeTextIsMath): TitleText`; `TitleText.Math(String source, String rawSource)`; `TitleText.of(List<TitleText.Run> runs): TitleText`; `SafeNodeLabel.of(String full, String display, TitleText title): SafeNodeLabel`.
- Produces: unchanged `public SafeNodeLabel extract(NodeModel reachableNode)` behavior for `fullText`/`displayText`, plus `private String trimLeadingWhitespace(String value)` and the whole-title `TitleText.Math` derivation in `labelFor(NodeModel node, String full)`.

**Tests:**
- `org.freeplane.plugin.graph.adapter.SafeNodeLabelExtractorShould.latexPrefixedNodeIsOneFormulaRun` (T1)
- `org.freeplane.plugin.graph.adapter.SafeNodeLabelExtractorShould.latexPrefixedTextWithoutDelimitersIsWrappedFormula` (T2)
- `org.freeplane.plugin.graph.adapter.SafeNodeLabelExtractorShould.combinedPatternFormatAndPrefixStripsBodyPrefix` (T3)
- `org.freeplane.plugin.graph.adapter.SafeNodeLabelExtractorShould.plainNodeKeepsInlineParsing` (T4, preserved green control)
- `org.freeplane.plugin.graph.adapter.SafeNodeLabelExtractorShould.declarationProvenanceAndFallbacks` (T5)
- `org.freeplane.plugin.graph.adapter.SafeNodeLabelExtractorShould.emptyPrefixBodyKeepsOrdinaryLabel` (T6)
- `org.freeplane.plugin.graph.adapter.SafeNodeLabelExtractorShould.cappedDisplayTextIsTheBodySource` (T7)
- `org.freeplane.plugin.graph.adapter.SafeNodeLabelExtractorShould.markdownPatternFormatWithNoDollarBodyIsWrapped` (T14; its specified assertion is bare source `K`, not a `\text{}` wrapper)

### Steps

- [ ] Add T1-T7 and T14 to `SafeNodeLabelExtractorShould` with the exact fixtures and assertions in specification §7.1. Keep T4 as the inline-parser control. Include all null/empty/link/icon cases in T6 and the supplementary-character cap fixture in T7.
- [ ] Run `gradle :freeplane_plugin_graph:test --tests '*SafeNodeLabelExtractorShould'` before changing production code. Confirm the expected extractor assertions fail while existing unrelated tests remain attributable; do not alter the existing `extract` computation of `full` or `displayText(full)`.
- [ ] Add `trimLeadingWhitespace(String)`: return `null` for null; otherwise advance by Unicode code point while the existing `isWhitespace(int)` returns true, and return the original suffix. Leave the existing whitespace predicate unchanged (`Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)`).
- [ ] In `labelFor`, derive `rawContent`, `directText`, and `content = sourceText(rawContent)` separately from `full`. For null or empty content set the intermediate formula fields to null or empty as specified and establish no formula signal, even for pattern formats. For nonempty content compute `converted = directText ? convertHtml(content) : content`, then left-trim it for declaration recognition; never HTML-convert a non-`CharSequence` `FormattedFormula`.
- [ ] Recognize `"\\latex"` or `"\\unparsedlatex"` only at the start of the left-trimmed content and only with the existing Unicode whitespace predicate immediately after the exact prefix. Set `declaredBody` by removing the recognized declaration and left-trimming again; an empty declared body has no formula signal. Keep format recognition case-sensitive; only `latexPatternFormat` and `unparsedLatexPatternFormat` declare a nonempty unprefixed body, while Markdown alone does not.
- [ ] Compute `strippedByLegacy` only from the specified fact: a declaration exists, format is not any of the three pattern formats, converted content begins with that declaration at offset zero, and the following code point is whitespace. Do not infer provenance by comparing normalized strings.
- [ ] For formula body selection start from capped `displayText`. If `strippedByLegacy` is false and a non-null declaration remains at offset zero followed by whitespace, remove exactly that one declaration and left-trim the suffix; otherwise remove nothing. A non-prefixed formula-pattern body remains unchanged. When the body contains any literal `$`, use it bare; otherwise wrap ordinary-format bodies as `"\\text{" + body + "}"`. Never wrap any pattern-format body, including Markdown. Construct one `TitleText.Math(formulaSource, displayText)` run so `rawSource()` remains the current display string.
- [ ] If there is no whole-title signal, preserve `TitleTextParser.parse(displayText, false)`. Do not change `TitleText`, the parser, `fullText`, `displayText`, fallback labels, or the extractor's search/accessibility inputs.
- [ ] Run `gradle :freeplane_plugin_graph:test --tests '*SafeNodeLabelExtractorShould'`; require T1-T7 and T14 to pass and T4 plus existing extractor tests to remain green.

**Commit:** `feat(graph): extract whole-title LaTeX declarations (2026-11-05-graph-latex-whole-title-prefix)`

## Task 3: Build Placement Rows From Placed Text

**Lane:** latex-title
**Category:** placed-title-row-construction
**Implementer tier:** Advanced
**Depends on:** Tasks 1 and 2

Never execute `reboot`, `shutdown`, `poweroff`, `halt`, `kexec`, any `systemctl` power or sleep verb, `init 0|6`, or `telinit 0|6`; never restart or stop the display manager or `graphical.target`; never terminate your session or signal the process tree that hosts the PM.

**Files:**
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacement.java:497-557`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphTitleFormulaShould.java:1-89`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacementShould.java:1835-1881`

**Interfaces:**
- Consumes: `TitleMeasurer.measureDegradedText(String text, Font font, boolean deferredForBudget): MeasuredTitle` from Task 1; `TitleMeasurer.measureDegraded(TitleText title, Font font): MeasuredTitle`; `TitleRow.resolve(MeasuredTitle measured, IconSource icons, Font logicalFont, double zoom): TitleRow`; `TitleTruncation.degradedPlainText(TitleText title): String`; `TitleTruncation.truncated(String text, Font worldFont, double limit): String`; `SafeNodeLabelExtractor.extract(NodeModel reachableNode): SafeNodeLabel` from Task 2.
- Produces: the two exact `placeMathNode(Context context, ProjectedNodeKey key, boolean forced, TitleText title): PlacedLabel` row-source expressions; test helper `placeExtracted(NodeModel node, boolean forced): PlacedLabel`.

**Tests:**
- `org.freeplane.plugin.graph.canvas.GraphTitleFormulaShould.paintedRowMatchesThePlacedText` (T9)
- `org.freeplane.plugin.graph.canvas.GraphTitleFormulaShould.renderedTitleUsesOneMathRun` (T10)
- `org.freeplane.plugin.graph.canvas.GraphTitleFormulaShould.reportedTitleFitsTheVerticalSlot` (T11)
- `org.freeplane.plugin.graph.canvas.ScreenLabelPlacementShould.truncateToMirrorsTitleTruncation` (T12, green control)

### Steps

- [ ] Add T9-T11 in `GraphTitleFormulaShould`. Use the marked-child `NodeModel` fixture, `GraphGroupModel`, and the exact extracted-label helper below; leave the existing `place(String)` helper unchanged because it builds its own title and uses `graphGroup=false`.

```java
private static PlacedLabel placeExtracted(final NodeModel node, final boolean forced) {
    final SafeNodeLabel label = new SafeNodeLabelExtractor().extract(node);
    final ProjectedNodeKey key = ProjectedNodeKey.of(SourceNodeKey.persisted(
        NodeReference.of(MAP, PersistedNodeId.of("n1"))));
    final ProjectedNode projected = ProjectedNode.of(key, label, "Map", true);
    final LayoutPoint center = LayoutPoint.of(0.0, 0.0);
    final Map<ProjectedNodeKey, NodeGeometry> geometry = new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
    geometry.put(key, NodeGeometry.of(center, 6.0));
    final Map<ProjectedNodeKey, LayoutPoint> positions = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
    positions.put(key, center);
    final GraphProjection projection = GraphProjection.structure(1L, Arrays.asList(projected),
        Collections.<ProjectedEnclosure>emptyList());
    final Set<ProjectedEndpointKey> forcedEndpoints = new LinkedHashSet<ProjectedEndpointKey>();
    if (forced) {
        forcedEndpoints.add(ProjectedEndpointKey.ofNode(key));
    }
    final LabelPlacementRequest request = LabelPlacementRequest.of(projection,
        GraphGeometry.of(geometry, Collections.<EnclosureHullKey, HullGeometry>emptyMap()),
        LayoutPositions.of(positions, Collections.<EnclosureHullKey, LayoutPoint>emptyMap()),
        1.0, 0.0, 0.0, new Rectangle2D.Double(0.0, 0.0, 1128.0, 364.0), forcedEndpoints, RenderingLevel.FULL);
    final List<PlacedLabel> placed = new ScreenLabelPlacement().place(request, null,
        LabelFonts.from(GraphTheme.from(CanvasTheme.LIGHT)), Collections.<Rectangle2D>emptyList());
    assertThat(placed).hasSize(1);
    return placed.get(0);
}
```

- [ ] T9 uses a forced, marked child whose content is `"\\latex "` plus 100 `W` characters; require truncation, `DEGRADED`, one `PlainRunRow`, and `paintText() == label.text()`. T10 uses the reported theorem and requires one rendered `MathRunRow`, exact bare body, non-null icon, and placement width equal to measured typeset width. T11 requires the calibrated `[193.49, 194.51]` px width, `ABOVE`, `FULL_NEAR`, no truncation, and width at most 200 px.
- [ ] Add T12 to `ScreenLabelPlacementShould`: compare `TitleTruncation.truncated` with `ScreenLabelPlacement.truncateTo` for the 96-`W` fixture, world fonts derived at zooms `1.0` and `2.0`, and limits `130.0`, `150.0`, and `200.0`; require exact string equality for all six pairs.
- [ ] First run the new placement tests with the old row expressions and observe the T9 paint-text mismatch. Then change only these two expressions in `placeMathNode`, carrying the deferred flag through `TitleRow.resolve`:

```java
rowSource = truncated ? context.measurer.measureDegradedText(degradedText, font, degraded.deferredForBudget()) : degraded;
```

```java
final TitleRow row = TitleRow.resolve(context.measurer.measureDegradedText(fallbackText, font, degraded.deferredForBudget()), context.measurer.iconSource(), font, zoom);
```

- [ ] Preserve the non-truncated candidate's `degraded` measurement and the terminal fallback's own `fallbackText`. Make no other statement change in `ScreenLabelPlacement.java`; keep caps, fonts, slots, flags, obstacles, enclosure path, and `truncateTo` byte-identical.
- [ ] Run `gradle :freeplane_plugin_graph:test --tests '*GraphTitleFormulaShould' --tests '*ScreenLabelPlacementShould'`. Require T9-T12 and the existing placement suite to pass.
- [ ] Coverage boundary: specification §7.1 has no named test that directly forces the terminal `HOVER_ONLY` fallback row construction. Implement that second expression exactly as §4 requires and do not invent a new test name or change fallback visibility semantics.

**Commit:** `fix(graph): build math paint rows from placed text (2026-11-05-graph-latex-whole-title-prefix)`

## Task 4: Add the Production Sample Harness

**Lane:** latex-title
**Category:** production-sample-measurement-harness
**Implementer tier:** Standard
**Depends on:** Tasks 1-3

Never execute `reboot`, `shutdown`, `poweroff`, `halt`, `kexec`, any `systemctl` power or sleep verb, `init 0|6`, or `telinit 0|6`; never restart or stop the display manager or `graphical.target`; never terminate your session or signal the process tree that hosts the PM.

**Files:**
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/title/LatexWholeTitleSampleShould.java`

**Interfaces:**
- Consumes: `SafeNodeLabelExtractor.extract(NodeModel reachableNode): SafeNodeLabel`; `SafeNodeLabel.title(): TitleText`; `TitleMeasurer(IconSource icons, int maxNewMeasurementsPerPass)`; `TitleMeasurer.beginPass(long projectionGeneration): void`; `TitleMeasurer.measure(TitleText title, Font font): MeasuredTitle`; `TitleMeasurer.deferredInLastPass(): int`.
- Produces: `org.freeplane.plugin.graph.canvas.title.LatexWholeTitleSampleShould.productionTotalsMatchTheRecordedSample()` and, when `LATEX_PROBE_DUMP` is an absolute path, one TSV row per sample body containing body, availability, width, and run count.

**Tests:**
- `org.freeplane.plugin.graph.canvas.title.LatexWholeTitleSampleShould.productionTotalsMatchTheRecordedSample` (T13)

### Steps

- [ ] Create the test in the title package and read the module-relative `doc/latex-whole-title/titles-sample.json` from the test JVM working directory. It is a JSON array of strings and contains 250 bodies.
- [ ] Keep this test dependency-free: the graph module declares no JSON parser dependency and specification §6 forbids dependency changes. Add a private test-only JSON string-array reader in this new class that handles JSON whitespace, quoted strings, `\\`, `\"`, the standard one-character escapes, and `\uXXXX`; reject malformed input, unterminated strings, non-string array entries, and trailing data. Do not add a production parser or another file.
- [ ] For every body construct `new NodeModel("\\latex " + body, null)`, extract it through `SafeNodeLabelExtractor`, and require exactly one `TitleText.Math` run. Use one `TitleMeasurer(new FormulaIcons(), 1000)`, call `beginPass(1L)` once, and measure all extracted titles at `new Font(Font.SANS_SERIF, Font.PLAIN, 12)`. Require `deferredInLastPass() == 0` and require the run source to be `"\\text{" + body + "}"` exactly when the input body contains no `$`; otherwise require the body verbatim. Do not assert a width total.
- [ ] When `LATEX_PROBE_DUMP` is set, require an absolute target path and write exactly one TSV row per body with its body, availability, width, and run count. Create the target parent directory as needed; do not modify `titles-sample.json` or existing probes/results.
- [ ] Run `gradle :freeplane_plugin_graph:test --tests '*LatexWholeTitleSampleShould'` from the repository root and require T13 to pass. This task adds the production-path sample/evidence harness; it is not a test-only patch to make an existing failing assertion green.

**Commit:** `test(graph): add whole-title production sample harness (2026-11-05-graph-latex-whole-title-prefix)`

## Task 5: Capture Production-Path Acceptance Evidence

**Lane:** acceptance
**Category:** production-path-and-application-evidence
**Implementer tier:** Capable
**Depends on:** Task 4

Never execute `reboot`, `shutdown`, `poweroff`, `halt`, `kexec`, any `systemctl` power or sleep verb, `init 0|6`, or `telinit 0|6`; never restart or stop the display manager or `graphical.target`; never terminate your session or signal the process tree that hosts the PM.

**Files:**
- Create: `freeplane_plugin_graph/doc/latex-whole-title/production-sample-results.tsv`
- Create: `freeplane_plugin_graph/doc/latex-whole-title/acceptance-checkout/` (temporary candidate checkout; remove after capture)
- Create: `freeplane_plugin_graph/build/evidence/latex-whole-title-acceptance.png`
- Create: `freeplane_plugin_graph/build/evidence/acceptance-record.txt`
- Create: `freeplane_plugin_graph/build/evidence/runtime/` (temporary isolated app home, config, Xvfb log, and XWD; remove after capture)

**Interfaces:**
- Consumes: T13 `LatexWholeTitleSampleShould.productionTotalsMatchTheRecordedSample()` with `LATEX_PROBE_DUMP`; the unchanged `gradle :freeplane_plugin_graph:test`, `gradle :freeplane:compileJava`, `gradle :freeplane_plugin_graph:verifyGraphBundle`, and `gradle dist` gates; the approved map and required node IDs in specification §8.
- Produces: 250-row production-path TSV, the acceptance PNG, and a text record containing the candidate commit, built graph-plugin JAR SHA-256, map SHA-256, PNG path, and host/font details.

**Tests:**
- `org.freeplane.plugin.graph.canvas.title.LatexWholeTitleSampleShould.productionTotalsMatchTheRecordedSample` (T13, rerun with `LATEX_PROBE_DUMP` to emit the TSV)

### Steps

- [ ] Confirm a JDK 21 and `gradle` are already available. If not, stop and request PM/user approval before installing `jdk21-openjdk` or Gradle; do not install packages without that approval. Record the actual `java -version` and `gradle --version` in the acceptance record.
- [ ] Record the candidate integration-worktree `git rev-parse HEAD`. To keep generated distribution and other-repository build outputs within the authorized evidence directory, create a temporary local Git clone at `freeplane_plugin_graph/doc/latex-whole-title/acceptance-checkout/`, pinned to that HEAD. Confirm the clone's `git rev-parse HEAD` is identical before running gates; do not copy or modify source files by hand.
- [ ] In the temporary checkout, set the approved JDK 21 environment and run the focused gates in order:

```sh
gradle :freeplane_plugin_graph:test --tests '*SafeNodeLabelExtractorShould' --tests '*GraphTitleFormulaShould'
gradle :freeplane_plugin_graph:test
gradle :freeplane:compileJava
gradle :freeplane_plugin_graph:verifyGraphBundle
```

- [ ] Still from the temporary checkout, run T13 with an absolute dump path in the integration worktree's authorized evidence directory:

```sh
LATEX_PROBE_DUMP="$INTEGRATION_ROOT/freeplane_plugin_graph/doc/latex-whole-title/production-sample-results.tsv" \
  gradle :freeplane_plugin_graph:test --tests '*LatexWholeTitleSampleShould'
```

Require 250 rows, one Math run per extracted title, and zero deferred measurements. Do not assert or record a host-independent aggregate width.
- [ ] Before distribution, create `BIN` inside the temporary checkout only. Run `gradle dist`, calculate `sha256sum BIN/plugins/org.freeplane.plugin.graph/lib/plugin-1.13.4.jar`, and record that digest with the matching candidate commit. Do not create root-level `BIN/` or `DIST/` in the integration worktree.
- [ ] Create the isolated runtime paths under `freeplane_plugin_graph/build/evidence/runtime/`, then start `Xvfb :99 -screen 0 1600x1000x24 -nolisten tcp` only if `:99` is unused. Launch the just-built app from the temporary checkout's `BIN` with the isolated `HOME`, `XDG_CONFIG_HOME`, `DISPLAY=:99`, the JDK 21 `JAVA_HOME`, and `JAVA_OPTS="-Dorg.freeplane.nosplash=true"`. Stop only the Freeplane process and Xvfb instance started for this capture.
- [ ] In that app, open `/home/henry/Documents/999.notebooks/01.math/00.foundation.of.math/02.set_theory/mindmap/Axiomatic_Set_Theory.mm`, search `is well-ordered by`, open Graph Workspace, explicitly add the opened map to the workspace, and wait for layout settlement. Capture the Graph Workspace window, verifying `xdotool getwindowname` prints exactly `Graph Workspace`; use `xdotool`, `xwd`, and `ffmpeg` to write `freeplane_plugin_graph/build/evidence/latex-whole-title-acceptance.png`.
- [ ] Record the PNG path, matching candidate commit, plugin JAR SHA-256, map SHA-256 `840358a03666d02f445e3aeb5ba84d9852e913318a3bf6c1776f50593aaff08c`, and the Sans Serif 12 / Noto Sans host information from `freeplane_plugin_graph/doc/latex-whole-title/environment.txt` in `acceptance-record.txt`. Confirm the four required marked nodes appear: `ID_215702338`, `ID_297617399`, `ID_1222855984`, and plain control `ID_1887656421`.
- [ ] Remove only the named temporary checkout and runtime scratch files after the evidence is complete, retaining the TSV, PNG, and acceptance record. Verify the candidate source commit and existing probe/sample inputs are unchanged; do not modify any path outside `freeplane_plugin_graph`.

**Commit:** `test(graph): capture whole-title production acceptance evidence (2026-11-05-graph-latex-whole-title-prefix)`
