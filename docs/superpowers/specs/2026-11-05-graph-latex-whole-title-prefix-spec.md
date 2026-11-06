# Graph Workspace: Whole-Title `\latex` Prefix Technical Specification

Task identifier: `2026-11-05-graph-latex-whole-title-prefix`

Specification date: 2026-11-05. Revision: 3. Status: implementation contract. The three gaps the drafter returned are resolved in §9, and the design's round-5 review records zero blockers.

Design authority: [2026-11-05-graph-latex-whole-title-prefix-design.md](2026-11-05-graph-latex-whole-title-prefix-design.md), revision 5. The design records zero blockers at its round-5 review. This specification does not revise that design.

Predecessor specification: [2026-11-03-graph-latex-titles-spec.md](2026-11-03-graph-latex-titles-spec.md).

Implementation scope is `freeplane_plugin_graph`. The authoring task creates this document only; the PM commits it. Java snippets use Java 8 syntax and four-space indentation.

## 1. Result and Scope

For a non-empty node content value declaring whole-title math, the graph title is one `TitleText.Math` run derived from the capped `displayText`. Existing `fullText` and `displayText` production remains byte-for-byte unchanged. Plain nodes without a whole-title declaration retain inline parsing through the existing `TitleTextParser.parse(displayText, false)` path.

The only production behavior changes authorized here are the whole-title declaration/body derivation in `SafeNodeLabelExtractor`, literal degraded-string measurement in `TitleMeasurer.measureDegradedText`, and two paint-row input expressions in `ScreenLabelPlacement.placeMathNode`.

The reported body is `Theorem: $ON$ is well-ordered by $\in$`. At the graph label font `new Font(Font.SANS_SERIF, Font.PLAIN, 12)` recorded in `GraphTheme.java:66` and `freeplane_plugin_graph/doc/latex-whole-title/environment.txt`, its one-formula prototype width is `194.00 px`. The previous inline-fragment interpretation measured `200.95 px`, exceeding the vertical cap `200 px` by `0.95 px`. These are the design's prototype measurements; production-path confirmation belongs to §7.

## 2. SafeNodeLabelExtractor

### 2.1 Visibility and Helpers

`SafeNodeLabelExtractor.extract(NodeModel)` remains `public`. `labelFor(NodeModel, String)`, `normalizedRawContent(NodeModel)`, `sourceText(Object)`, `convertHtml(String)`, `removeLatexPrefix(String, String)`, `hasWhitespaceAfter(String, int)`, `isWhitespace(int)`, `normalizeWhitespace(String)`, and `displayText(String)` remain `private`.

The only new named helper required by this specification is `private String trimLeadingWhitespace(String)`. It returns `null` for `null`; otherwise it advances by Unicode code point while the existing private `isWhitespace(int)` returns true, then returns the original suffix. The existing predicate is exactly `Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)`. All other names in §2.2 are local values in the existing private `labelFor`; no other helper is introduced.

### 2.2 Ordered Rules

1. Compute `fullText` exactly as today in `extract`: first non-empty of `normalizedRawContent(node)`, `normalizedDirectLink(node)`, `normalizedDirectIcon(node)`, and the literal `"Node"`. `normalizedRawContent` still applies the current direct-text HTML conversion, `removeLatexPrefix`, and whitespace normalization. `displayText(fullText)` then applies the existing 80-code-point cap. Do not derive the formula body from `fullText`.

2. Set `rawContent = node.getUserObject()`, `directText = rawContent instanceof CharSequence`, and `content = sourceText(rawContent)`. If `content == null`, set `converted`, `trimmed`, and `declaredBody` to `null`; set `declaration = null` and `strippedByLegacy = false`; do not set a whole-title formula signal, even if the node format is a pattern format. If `content.equals("")`, use empty strings for `converted`, `trimmed`, and `declaredBody`, and likewise set no declaration or whole-title formula signal. In either case continue through the ordinary fallback and inline-parser path.

3. For non-null, non-empty `content`, compute exactly `converted = directText ? convertHtml(content) : content`. `FormattedFormula` is not a `CharSequence`, so HTML-looking text held by a `FormattedFormula` is not passed to `convertHtml`. Set `trimmed = trimLeadingWhitespace(converted)`. This is a left trim only; it does not collapse interior whitespace or alter the suffix.

4. Set `declaration` to `"\\latex"` if `trimmed.startsWith("\\latex")` and `hasWhitespaceAfter(trimmed, "\\latex".length())`; otherwise set it to `"\\unparsedlatex"` if `trimmed.startsWith("\\unparsedlatex")` and `hasWhitespaceAfter(trimmed, "\\unparsedlatex".length())`; otherwise set it to `null`. Recognition is on `trimmed`, before and independently of node-format exclusions. The required whitespace is the first code point after the exact prefix and uses the predicate in §2.1. A prefix without following whitespace is not a declaration.

5. Set `declaredBody = trimmed` when `declaration == null`. Otherwise set `declaredBody = trimLeadingWhitespace(trimmed.substring(declaration.length()))`. If a declaration exists and `declaredBody.isEmpty()`, there is no whole-title formula signal. Thus `"\\latex "`, `"\\unparsedlatex "`, `"\\latex\t"`, and `"\\unparsedlatex\u00A0"` have empty declared bodies. A leading-whitespace prefix-only value can remain ordinary visible text under the unchanged `fullText` path, but it is not a formula.

6. Let `format = NodeStyleModel.getNodeFormat(node)`. Set `declaresWholeFormula` true exactly when `content` is non-null and non-empty, `declaredBody` is non-null and non-empty, and either `declaration != null`, or `format` is exactly `"latexPatternFormat"` or `"unparsedLatexPatternFormat"`. The format names are case-sensitive. `"markdownPatternFormat"` alone does not declare whole-title math.

7. Set `displayText` using today's `displayText(fullText)`. Its cap remains `SafeNodeLabelExtractor.MAX_DISPLAY_CODE_POINTS == 80`: when `fullText` exceeds 80 code points, retain its first 77 code points and append the literal three-character string `"..."`. The boundary is computed with `codePointCount`/`offsetByCodePoints`, not UTF-16 code-unit indexing. Set `strippedByLegacy` true exactly when `declaration != null`, `format` is not any of `"latexPatternFormat"`, `"unparsedLatexPatternFormat"`, or `"markdownPatternFormat"`, `converted.startsWith(declaration)` at offset zero, and `hasWhitespaceAfter(converted, declaration.length())` is true. This records the existing `removeLatexPrefix` result as provenance; it is not inferred by comparing strings.

8. If `declaresWholeFormula` is false, construct the title with the existing `TitleTextParser.parse(displayText, false)`. If it is true, start `body` as `displayText`. Then, only when `strippedByLegacy` is false **and `declaration != null`** and `displayText` starts at offset zero with `declaration` followed by whitespace, set `body = trimLeadingWhitespace(displayText.substring(declaration.length()))`; otherwise remove nothing. The `declaration != null` guard is required: a non-prefixed `latexPatternFormat` or `unparsedLatexPatternFormat` title declares a whole formula with no declaration at all, and its body is `displayText` unchanged. When `strippedByLegacy` is true, remove nothing from `displayText`. This removes at most one declaration from the display string. The 80-code-point cap therefore applies before body selection.

9. For a whole-title declaration, set the formula source to `"\\text{" + body + "}"` when `body.indexOf('$') < 0`, and to `body` when `body.indexOf('$') >= 0`. Dollar detection is the literal presence of U+0024 anywhere in `body`; it does not parse pairing, escaping, or nesting. A `latexPatternFormat` or `unparsedLatexPatternFormat` body is never wrapped. A `markdownPatternFormat` body is **not** exempt: when its whole-title signal comes from a declaration, it follows the same rule as every other body, so a Markdown node whose text is `\\latex K` yields source `\\text{K}` while `rawSource()` keeps `\\latex K`. Return a singleton `TitleText.Math(formulaSource, displayText)` in `SafeNodeLabel.of(fullText, displayText, title)`. Thus `TitleText.Math.source()` is the computed formula source and `TitleText.Math.rawSource()` is exactly today's `displayText`.

### 2.3 Intermediate Field Traces

The following traces pin the exact string carried by every named extraction value. Backslash escapes are Java-string notation.

- Reported ordinary-format title: `content = "\\latex Theorem: $ON$ is well-ordered by $\\in$"`; `converted` and `trimmed` equal `content`; `declaration = "\\latex"`; `declaredBody = "Theorem: $ON$ is well-ordered by $\\in$"`; `strippedByLegacy = true`; `fullText = displayText = "Theorem: $ON$ is well-ordered by $\\in$"`; `TitleText.Math.source() = TitleText.Math.rawSource() = "Theorem: $ON$ is well-ordered by $\\in$"`.
- Ordinary-format unparsed prefix: `content = converted = trimmed = "\\unparsedlatex $x$"`; `declaration = "\\unparsedlatex"`; `declaredBody = "$x$"`; `strippedByLegacy = true`; `fullText = displayText = TitleText.Math.source() = TitleText.Math.rawSource() = "$x$"`.
- Leading whitespace: `content = converted = "  \\latex   $a$"`; `trimmed = "\\latex   $a$"`; `declaration = "\\latex"`; `declaredBody = "$a$"`; `strippedByLegacy = false`; `fullText = displayText = "\\latex $a$"`; `TitleText.Math.source() = "$a$"`; `rawSource() = "\\latex $a$"`.
- Doubled ordinary declaration: `content = converted = trimmed = "\\latex \\latex $a$"`; `declaration = "\\latex"`; `declaredBody = "\\latex $a$"`; `strippedByLegacy = true`; `fullText = displayText = "\\latex $a$"`; `TitleText.Math.source() = TitleText.Math.rawSource() = "\\latex $a$"`.
- Direct HTML text: `content = "<html><body>\\latex $a$</body></html>"`; `converted = trimmed = "\\latex $a$"`; `declaration = "\\latex"`; `declaredBody = "$a$"`; `strippedByLegacy = true`; `fullText = displayText = TitleText.Math.source() = TitleText.Math.rawSource() = "$a$"`.
- `FormattedFormula` HTML-looking value: `content = converted = trimmed = declaredBody = "<html><body>literal formula source</body></html>"`; `declaration = null`; `strippedByLegacy = false`; `fullText = displayText` is that exact string; the title is one Plain run, not a Math run.
- `latexPatternFormat` with no prefix and text body: `content = converted = trimmed = declaredBody = "K\\\"onig's Theorem I"`; `declaration = null`; `strippedByLegacy = false`; `fullText = displayText = "K\\\"onig's Theorem I"`; the single Math source is the body verbatim, without `\\text{...}`.
- `latexPatternFormat` with prefix: `content = converted = trimmed = "\\latex $x_2=3$"`; `declaration = "\\latex"`; `declaredBody = "$x_2=3$"`; `strippedByLegacy = false`; `fullText = displayText = rawSource = "\\latex $x_2=3$"`; `source() = "$x_2=3$"`.
- `markdownPatternFormat` with a no-dollar declaration: `content = converted = trimmed = "\\latex K"`; `declaration = "\\latex"`; `declaredBody = "K"`; `strippedByLegacy = false` (markdown is excluded from legacy removal); `fullText = displayText = rawSource = "\\latex K"`; `source() = "\\text{K}"`.
- `unparsedLatexPatternFormat` with prefix: `content = converted = trimmed = "\\unparsedlatex $x$"`; `declaration = "\\unparsedlatex"`; `declaredBody = "$x$"`; `strippedByLegacy = false`; `fullText = displayText = rawSource = "\\unparsedlatex $x$"`; `source() = "$x$"`.
- `markdownPatternFormat` without prefix: `content = converted = trimmed = declaredBody = "$a$ and $b$"`; `declaration = null`; `strippedByLegacy = false`; `fullText = displayText = "$a$ and $b$"`; the format alone does not declare whole-title math, so inline parsing is retained.
- `markdownPatternFormat` with a dollar-bearing prefix: `content = converted = trimmed = "\\latex $a$"`; `declaration = "\\latex"`; `declaredBody = "$a$"`; `strippedByLegacy = false`; `fullText = displayText = rawSource = "\\latex $a$"`; `source() = "$a$"`.
- `markdownPatternFormat` with a no-dollar prefix: for `content = converted = trimmed = "\\latex K"`, `declaration = "\\latex"`, `declaredBody = "K"`, `strippedByLegacy = false`, and `fullText = displayText = rawSource = "\\latex K"`; `source()` is the G1 gap.
- Null content: `content = converted = trimmed = declaredBody = null`; `declaration = null`; `strippedByLegacy = false`; `fullText = displayText = "Node"` when there is no link or icon; no whole-title signal.
- Empty content: `content = converted = trimmed = declaredBody = ""`; `declaration = null`; `strippedByLegacy = false`; `fullText = displayText = "Node"` when there is no link or icon; no whole-title signal.

### 2.4 Exact Cases

| Input and format | `fullText` / `displayText` | `declaration`, `strippedByLegacy`, body | `TitleText.Math.source()` / `rawSource()` |
| --- | --- | --- | --- |
| `"\\latex Theorem: $ON$ is well-ordered by $\\in$"`, ordinary format | `"Theorem: $ON$ is well-ordered by $\\in$"` / same | `"\\latex"`, `true`, `"Theorem: $ON$ is well-ordered by $\\in$"` | `"Theorem: $ON$ is well-ordered by $\\in$"` / display text |
| `"\\unparsedlatex $x$"`, ordinary format | `"$x$"` / same | `"\\unparsedlatex"`, `true`, `"$x$"` | `"$x$"` / `"$x$"` |
| `"\\latex K\\\"onig's Theorem I"`, ordinary format | `"K\\\"onig's Theorem I"` / same | `"\\latex"`, `true`, `"K\\\"onig's Theorem I"` | `"\\text{K\\\"onig's Theorem I}"` / display text |
| `"\\latex $x_2=3$"`, `latexPatternFormat` | `"\\latex $x_2=3$"` / same | `"\\latex"`, `false`, `"$x_2=3$"` | `"$x_2=3$"` / `"\\latex $x_2=3$"` |
| `"  \\latex   $a$"`, ordinary format | `"\\latex $a$"` / same | `"\\latex"`, `false`, `"$a$"` | `"$a$"` / `"\\latex $a$"` |
| `"\\latex \\latex $a$"`, ordinary format | `"\\latex $a$"` / same | `"\\latex"`, `true`, `"\\latex $a$"` | `"\\latex $a$"` / `"\\latex $a$"` |
| `"\\latex $a$"`, `markdownPatternFormat` | `"\\latex $a$"` / same | `"\\latex"`, `false`, `"$a$"` | `"$a$"` / `"\\latex $a$"` |
| `"$a$ and $b$"`, ordinary or `markdownPatternFormat`, no prefix | unchanged | no declaration; no whole-title signal | Existing inline parser yields `Math("a", "$a$")`, `Plain(" and ")`, `Math("b", "$b$")` |
| `"$a$ and $b$"`, `latexPatternFormat` or `unparsedLatexPatternFormat` | unchanged | no declaration; whole-title signal from non-empty pattern body | One Math source `"$a$ and $b$"`; raw source is the display text |
| `FormattedFormula("<html><body>literal formula source</body></html>", "ignored")` | the literal HTML-looking string / same | no declaration; no whole-title signal | One ordinary `Plain` run; no Math run |
| `"\\latex \\text{unclosed"`, ordinary format | `"\\text{unclosed"` / same | `"\\latex"`, `true`, `"\\text{unclosed"` | `"\\text{\\text{unclosed}"` / `"\\text{unclosed"`; the unbalanced braces are not repaired by extraction and the renderer degrades on rejection |

A prefix-only declaration has no formula signal: `content == "\\latex "` in an ordinary format produces the existing `"Node"` fallback; `content == " \\latex  "` leaves `fullText == "\\latex"` under existing normalization and remains ordinary text. Null and empty content likewise retain the link/icon/`"Node"` fallback. For a non-empty pattern-format body without a prefix, `latexPatternFormat` and `unparsedLatexPatternFormat` declare a whole formula and pass that body bare, including when it contains no dollar. `markdownPatternFormat` without a prefix retains inline parsing. With a `\latex` prefix, Markdown retains the prefix in `displayText`, recognizes the declaration, and removes one prefix for the body; its no-dollar wrapping result is G1.

## 3. TitleMeasurer.measureDegradedText

The required signature is:

```java
public MeasuredTitle measureDegradedText(String text, Font font, boolean deferredForBudget)
```

The result always has `MeasuredTitle.Availability.DEGRADED` and exactly one `MeasuredRun.plain(text, 0.0, width, ascent, descent)`. Compute `Rectangle2D bounds = font.getStringBounds(text, TitleMetrics.SCREEN_FRC)`, `width = bounds.getWidth()`, `ascent = -bounds.getY()`, and `descent = bounds.getHeight() + bounds.getY()`. Set `height = Math.max(ascent + descent, 1.0e-6)`. The height floor `1.0e-6` is the existing `degradedMeasurement` floor in `TitleMeasurer.java` and the design's pinned constant.

Memoize the undeferred literal measurement in the existing `TitleMeasurer.cache`, in a distinct `DEGRADED_TEXT` namespace. The complete key is `(text, font.getFamily(), font.getStyle(), font.getSize2D(), DEGRADED_TEXT)`. It cannot collide with title measurements, including `measure(TitleText.plain(text), font)`. The namespace and cache are per `TitleMeasurer` instance and the cache remains projection-generation scoped by `beginPass`.

This method does not call `consumeBudgetSlot()`, `defer(...)`, or mutate the pending/deferred queue. It consumes no budget and has no effect on `newMeasurementsInLastPass()`, `deferredInLastPass()`, `pendingCount()`, `budgetExhaustedInLastPass()`, or `stalled()`. Cache the undeferred measurement; for each call, apply the argument per call: false returns `deferredForBudget() == false`, and true returns the same measurement shape with `deferredForBudget() == true`.

`TitleRow.resolve(MeasuredTitle, IconSource, Font, double)` receives that returned measurement. It copies `measured.deferredForBudget()` into `TitleRow.deferredForBudget()`; it does not infer or recompute the flag. The existing `measure`, `measureDegraded`, `defer`, and budget/queue behavior are unchanged.

## 4. ScreenLabelPlacement.placeMathNode

Replace only the two row-source expressions below.

Truncated candidate row source:

```java
rowSource = truncated ? context.measurer.measureDegradedText(degradedText, font, degraded.deferredForBudget()) : degraded;
```

Terminal `HOVER_ONLY` fallback row construction:

```java
final TitleRow row = TitleRow.resolve(context.measurer.measureDegradedText(fallbackText, font, degraded.deferredForBudget()), context.measurer.iconSource(), font, zoom);
```

In both cases `degraded.deferredForBudget()` is passed to the literal measurement and then carried by `TitleRow.resolve`. The non-truncated candidate continues to use `degraded`; the fallback continues to use its own `fallbackText`.

No other statement in `ScreenLabelPlacement.java` changes. In particular, caps, fonts, slots, rungs, leaders, obstacles, retention, the enclosure path, `truncateTo`, and the existing forced/fallback flags remain byte-identical. The two lines do not authorize changes to fallback visibility or obstacle registration.

## 5. Deferred Design Items

| ID | Deferred item | Required disposition |
| --- | --- | --- |
| D1 | Formula titles do not use `DENSE_NEAR`/`DENSE_DISPLACED`, and `retained()` skips math titles. | Do not add dense rungs or math retention in this change. The divergence predates this defect, affects all formula labels, and needs separate typography evidence. |
| D2 | `placeMathNode` tries near slots only; plain titles also use `FAR_SLOTS`. | Do not add far slots or change rung order/leader metadata in this change. |
| D3 | Terminal `HOVER_ONLY` math fallback clears `forced`, so a forced label can be invisible. | Keep the current fallback flag, slot, and obstacle behavior. Making it visible without base-slot and obstacle semantics is a separate design. |
| D4 | Truncation can ellipsize inside a LaTeX control sequence. | Keep the existing character-based `truncateTo`; token-boundary truncation is not authorized. |
| D5 | Mixed bodies containing text and `$...$` render their text in TeX math style. | Bodies containing any dollar are passed bare. Do not add a `\\text{...}` wrapper or delimiter parser in this change; safe mixed-body wrapping needs separate design and evidence. |

## 6. What Must Not Change

- `SafeNodeLabel.fullText()` and `displayText()` outputs remain byte-for-byte identical to the existing extractor for every node. `displayText()` remains the current normalized, capped user-visible string; it is not replaced by the formula body.
- `GraphSearchModel` continues to search `fullText()`. `AccessibleGraphCanvas` continues to expose `fullText()` for accessibility. No search or accessibility string changes.
- Hull geometry and layout padding continue to consume `displayText()` as before. Enclosure labels remain plain and retain their existing measured geometry; they do not enter `placeMathNode`.
- `TitleText` and `TitleTextParser` are not modified. Plain nodes without a recognized whole-title declaration keep existing inline `$...$` parsing.
- The graph bundle manifest, dependency set, and `verifyGraphBundle` gate remain unchanged. Do not modify `freeplane_plugin_graph/build.gradle`, add a dependency, or change bundle metadata for this change.
- Production and test changes remain within `freeplane_plugin_graph`. Do not modify the approved design, predecessor documents, task plans, execution manifests, lane projections, or other plan-facing work outside the module. The sole requested documentation addition is this specification.

## 7. Tests and Verification

### 7.1 Test Inventory

The existing `place(String)` helper parses its own argument, builds `ProjectedNode` with `graphGroup=false` and an
empty forced set, and cannot accept an extracted label. T9-T11 therefore add one private helper:

```java
private static PlacedLabel placeExtracted(final NodeModel node, final boolean forced) {
    final SafeNodeLabel label = new SafeNodeLabelExtractor().extract(node);
    final ProjectedNodeKey key = ProjectedNodeKey.of(SourceNodeKey.persisted(
        NodeReference.of(MAP, PersistedNodeId.of("n1"))));
    final ProjectedNode projected = ProjectedNode.of(key, label, "Map", true);   // true: projected as a disc
    // same geometry, positions, projection, request, fonts and empty seed obstacles as place(String),
    // except that `forced` contains ProjectedEndpointKey.ofNode(key) when the argument is true
    return new ScreenLabelPlacement().place(request, null,
        LabelFonts.from(GraphTheme.from(CanvasTheme.LIGHT)), Collections.<Rectangle2D>emptyList()).get(0);
}
```

The existing `place(String)` helper stays unchanged and keeps `graphGroup=false`; it cannot be reused because it
derives the title itself.

Baseline status is against the design's pre-change production baseline. “Fails today” is a behavioral failure, not a missing test class. T8 is a compile-red API probe. T12 is an intentionally green mirror control.

| # | Test class and method | Fixture construction | Exact assertions | Baseline |
| --- | --- | --- | --- | --- |
| T1 | `org.freeplane.plugin.graph.adapter.SafeNodeLabelExtractorShould.latexPrefixedNodeIsOneFormulaRun` | `new NodeModel("\\latex Theorem: $ON$ is well-ordered by $\\in$", null)` with ordinary node format; pass through `SafeNodeLabelExtractor.extract`. | `title().runs().size() == 1`; the run is `TitleText.Math`; `source() == "Theorem: $ON$ is well-ordered by $\\in$"`; `rawSource() == displayText() == "Theorem: $ON$ is well-ordered by $\\in$"`; `fullText()` is the same normalized string. | Fails today. |
| T2 | `SafeNodeLabelExtractorShould.latexPrefixedTextWithoutDelimitersIsWrappedFormula` | `new NodeModel("\\latex K\\\"onig's Theorem I", null)` with ordinary format. | One Math run; `source() == "\\text{K\\\"onig's Theorem I}"`; `rawSource()` and `displayText()` both equal `"K\\\"onig's Theorem I"`. | Fails today. |
| T3 | `SafeNodeLabelExtractorShould.combinedPatternFormatAndPrefixStripsBodyPrefix` | Set `NodeStyleModel.setNodeFormat(node, "latexPatternFormat")` on `new NodeModel("\\latex $x_2=3$", null)`. | `displayText() == "\\latex $x_2=3$"`; one Math run; `source() == "$x_2=3$"` with no `\\text{}` wrapper; `rawSource() == "\\latex $x_2=3$"`. | Fails today. |
| T4 | `SafeNodeLabelExtractorShould.plainNodeKeepsInlineParsing` | `new NodeModel("$a$ and $b$", null)` with no node format or prefix. | Runs, in order: `Math("a", "$a$")`, `Plain(" and ")`, `Math("b", "$b$")`; no whole-title conversion. | Passes today. |
| T5 | `SafeNodeLabelExtractorShould.declarationProvenanceAndFallbacks` | Extract five under-cap nodes: `"\\latex \\latex $a$"`; `"  \\latex   $a$"`; `"\\latex   $a$"`; direct text `"<html><body>\\latex $a$</body></html>"`; and `new FormattedFormula("<html><body>literal formula source</body></html>", "ignored")`. | Doubled declaration: `fullText()`/`displayText()` are `"\\latex $a$"`; one Math source and raw source are `"\\latex $a$"`. Leading-whitespace case: display is `"\\latex $a$"`, source is `"$a$"`, raw is the display. Stripped case: display/source/raw are `"$a$"`. HTML direct-text case: display/source/raw are `"$a$"`. FormattedFormula case: full/display and its sole Plain text are the exact HTML-looking string; no Math run. | Fails today. |
| T6 | `SafeNodeLabelExtractorShould.emptyPrefixBodyKeepsOrdinaryLabel` | Ordinary-format nodes with `"\\latex "`, `"\\unparsedlatex "`, `"\\latex \t"`, `"\\unparsedlatex\u00A0"`, `" \\latex  "`, and `" \\unparsedlatex  "`; empty nodes in each of `latexPatternFormat`, `unparsedLatexPatternFormat`, and `markdownPatternFormat`; null and empty user objects; construct the link-only fixture with `MapModel map = mapWithRoot(); NodeModel source = new NodeModel("", map); map.getRootNode().insert(source); NodeLinks.createLinkExtension(source).setLocalHyperlink(source, ID_HIDDEN)`; construct the icon-only fixture with `TestIcon descriptionlessIcon = new TestIcon("fallback-name", ""); NodeModel iconOnly = new NodeModel("", null); iconOnly.addIcon(descriptionlessIcon)`. | No extraction throws and none of these results has a Math run. The first four ordinary prefix-only nodes resolve to `"Node"`; the two leading-whitespace cases retain ordinary display `"\\latex"` and `"\\unparsedlatex"`; each empty-content pattern node, null node, and empty node resolves to `"Node"`; link-only remains `"#ID_HIDDEN"`; icon-only remains `"fallback-name"`. | Fails today for the two leading-whitespace prefix-only variants **and** for the empty `latexPatternFormat` and `unparsedLatexPatternFormat` nodes, where today's extractor turns the `"Node"` fallback into a Math run. |
| T7 | `SafeNodeLabelExtractorShould.cappedDisplayTextIsTheBodySource` | Build `body` in Java 8 by appending `'a'` **76** times, `repeatedEmoji(1)`, then `"bbbbbbbbbb"`; set node content to `"\\latex " + body`, ordinary format. `repeatedEmoji(1)` is the existing fixture helper and yields U+1F600. The body is therefore 87 code points: 76 + 1 + 10. | `fullText() == body`; `displayText()` is the first 77 code points of `body` followed by `"..."`, i.e. 76 `a` characters, the emoji, then `"..."`; it has 80 code points and no unpaired surrogate. The single Math run has `rawSource() == displayText()` and `source() == "\\text{" + displayText() + "}"`; every `b` of the ten-character suffix is absent from the body source. | Fails today. |
| T8 | `org.freeplane.plugin.graph.canvas.title.TitleMeasurerShould.degradedTextIsMeasuredFromItsOwnString` | `text = "literal $a$"`; font `new Font(Font.SANS_SERIF, Font.PLAIN, 12)`; begin a pass, call `measureDegradedText(text, font, true)` and `measureDegradedText(text, font, false)`, then `measure(TitleText.plain(text), font)`. | Both literal measurements are `DEGRADED`, have one plain run whose `source()` equals `text`, width equal to `font.getStringBounds(text, TitleMetrics.SCREEN_FRC).getWidth()`, and height `Math.max(ascent + descent, 1.0e-6)`. Deferred flags are respectively true and false. Immediately after the two literal calls and before the separate title measurement, `newMeasurementsInLastPass() == 0`, `deferredInLastPass() == 0`, `pendingCount() == 0`, and `budgetExhaustedInLastPass() == false`. Then `measure(TitleText.plain(text), font)` is `RENDERED`, distinguishing it from the `DEGRADED_TEXT` cache entry. | New test; compile-red because the method is absent. |
| T9 | `org.freeplane.plugin.graph.canvas.GraphTitleFormulaShould.paintedRowMatchesThePlacedText` | One-node fixture, constructed inline with no new helper: `MapModel map = new MapModel((source, targetMap, withChildren) -> null, null, null);` then `NodeModel formula = new NodeModel("\\latex " + new String(new char[100]).replace('\0', 'W'), map); map.getRootNode().insert(formula); formula.putExtension(new GraphGroupModel());` — the same `MapModel` and lambda idiom `SafeNodeLabelExtractorShould.java:282` and `GraphGroupPersistenceShould.java:148` already use. The `GraphGroupModel` mark is required: an unmarked child becomes an enclosure, and only a marked node is projected as a disc (`ProjectionEngine.projectSourceNode`). Place it with `placeExtracted(formula, true)` (the helper above), so the label comes from the extractor, the projected node is a disc, and the endpoint is forced. | `label.truncated() == true`; `label.titleRow().form() == DEGRADED`; there is exactly one run and it is `PlainRunRow`; that run's `paintText()` equals `label.text()` exactly. | Fails today. |
| T10 | `GraphTitleFormulaShould.renderedTitleUsesOneMathRun` | The same marked-child fixture with user object `"\\latex Theorem: $ON$ is well-ordered by $\\in$"`; `placeExtracted(node, false)` with no obstacles in the normal near-slot request. | Row form is `RENDERED`; one run, of type `MathRunRow`; `body() == "Theorem: $ON$ is well-ordered by $\\in$"`; icon is non-null; placement width equals the measured typeset width. | Fails today. |
| T11 | `GraphTitleFormulaShould.reportedTitleFitsTheVerticalSlot` | The T10 marked-child fixture placed with `placeExtracted(node, false)`, one node, no obstacles, 12-point Sans Serif plain label font; the near slots are requested so the selected slot is `ABOVE`. | Measured/placement width is within `0.51 px` of `194.00 px`, i.e. `[194.00 - 0.51, 194.00 + 0.51] == [193.49, 194.51]`; `slot() == ABOVE`; `rung() == FULL_NEAR`; `truncated() == false`; width is at most the `200 px` vertical cap and not `TRUNCATED_NEAR`. | Fails today. |
| T12 | `org.freeplane.plugin.graph.canvas.ScreenLabelPlacementShould.truncateToMirrorsTitleTruncation` | Java 8 fixture `new String(new char[96]).replace('\0', 'W')`; logical font `new Font(Font.SANS_SERIF, Font.PLAIN, 12)`; derive test fonts with `TitleMetrics.worldFont(logicalFont, zoom)` for zooms `1.0` and `2.0`; compare limits `130.0`, `150.0`, and `200.0`. | For each of the six `(zoom, limit)` pairs, `TitleTruncation.truncated(text, font, limit).equals(ScreenLabelPlacement.truncateTo(text, font, limit))`. No numeric tolerance is used. | Passes today; new green control. |
| T13 | `org.freeplane.plugin.graph.canvas.title.LatexWholeTitleSampleShould.productionTotalsMatchTheRecordedSample` | Read the module-relative sample `doc/latex-whole-title/titles-sample.json` (the test JVM's working directory is `freeplane_plugin_graph`, as existing tests' `src/main/...` paths assume). For each of the 250 bodies build `new NodeModel("\\latex " + body, null)` and extract with `SafeNodeLabelExtractor`. Measure with `new TitleMeasurer(new FormulaIcons(), 1000)` — the public two-argument constructor — and one `beginPass(1L)`, so the 250 titles cannot be deferred by the default 200-measurement budget. | Every one of the 250 extracted titles has exactly one `Math` run; `deferredInLastPass() == 0`; and for each row the run's `source()` is `\text{...}` exactly when the body contains no `$`. When `LATEX_PROBE_DUMP` is set to an absolute path, one TSV row per body is written there with body, availability, width and run count. No width total is asserted, because glyph metrics are host-specific; the dump is evidence, not a gate. | Fails today: through today's production path (whitespace normalization, the 80-code-point cap, then the inline parser) 196 of the 250 bodies yield a single Math run, 45 yield several runs and 9 yield a single Plain run, so the one-Math-run assertion fails for 54 rows. The parser-only probe figures (201 single-Math, 46 multi-run, 3 single-Plain) are recorded as probe evidence in `probe-baseline-runs.js`, not as this test's baseline. |
| T14 | `SafeNodeLabelExtractorShould.markdownPatternFormatWithNoDollarBodyIsWrapped` | `NodeStyleModel.setNodeFormat(node, "markdownPatternFormat")` on `new NodeModel("\\latex K", null)`. | `displayText() == fullText() == "\\latex K"`; one Math run; `source() == "\\text{K}"`; `rawSource() == "\\latex K"`. | Fails today; pins the G1 resolution. |

The 0.51 px tolerance in T11 is the design's host-calibrated tolerance. Its exact asserted interval is shown above; no alternate tolerance is permitted by this specification.

### 7.2 Red Phase and Verification Commands

Run from the integration repository root with Java 21 from `~/.sdkman/candidates/java/21.0.8-zulu` and Gradle available:

```sh
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
```

If the host matches the design's recorded state with no JDK or Gradle on `PATH`, inform the user before installing `jdk21-openjdk 21.0.13` and `gradle 9.8.2`; do not install packages as part of this specification task.

1. **API red.** Add T8 to `TitleMeasurerShould` without adding `measureDegradedText`. Run:

   ```sh
   gradle :freeplane_plugin_graph:test --tests '*TitleMeasurerShould'
   ```

   Expected verdict: `BUILD FAILED` during test compilation, with unresolved `measureDegradedText(String, Font, boolean)`. This stage records the absent API, not a test assertion failure.

2. **API restoration, then behavior red.** Add `measureDegradedText` exactly as §3 specifies. It is additive and is
   referenced by no production path, so there is **no temporary scaffold and nothing to remove**: the stage-1
   compilation error disappears because the final method now exists. T8 turns green while `placeMathNode` and the
   extractor remain unchanged. Run:

   ```sh
   gradle :freeplane_plugin_graph:test --tests '*TitleMeasurerShould'
   gradle :freeplane_plugin_graph:test --tests '*SafeNodeLabelExtractorShould' --tests '*GraphTitleFormulaShould'
   ```

   Expected verdicts: the first command passes, including T8. The second `BUILD FAILED` on T1, T2, T3, T5, T6, T7, T9,
   T10 and T11, with T4 green. T12 is not in this selection.

3. **Green module gates.** After the complete change, run these commands in order:

   ```sh
   gradle :freeplane_plugin_graph:test --tests '*SafeNodeLabelExtractorShould' --tests '*GraphTitleFormulaShould'
   gradle :freeplane_plugin_graph:test
   gradle :freeplane:compileJava
   gradle :freeplane_plugin_graph:verifyGraphBundle
   ```

   Expected verdict: all four commands exit successfully; T1-T12 pass; `verifyGraphBundle` runs unchanged.

4. **Production-path remeasurement.** `LatexWholeTitleSampleShould` (§7.1) reads the module-relative sample
   `doc/latex-whole-title/titles-sample.json` (the test JVM runs with `freeplane_plugin_graph` as its working
   directory), builds one `NodeModel` per body, extracts through `SafeNodeLabelExtractor`, measures with a
   `TitleMeasurer` whose budget is 1000, and asserts the structural totals. To write the evidence table, set
   `LATEX_PROBE_DUMP` to an **absolute** path and run the same test:

   ```sh
   LATEX_PROBE_DUMP="$PWD/freeplane_plugin_graph/doc/latex-whole-title/production-sample-results.tsv" \
     gradle :freeplane_plugin_graph:test --tests '*LatexWholeTitleSampleShould'
   ```

   Expected verdict: the test passes and the TSV appears with 250 rows. The recorded production totals replace the
   prototype totals of the design's §3.5 as the claim of record. No test constant depends on a host width.

5. **Distribution gate.** For §7 acceptance evidence, after the green module gates run:

   ```sh
   gradle dist
   sha256sum BIN/plugins/org.freeplane.plugin.graph/lib/plugin-1.13.4.jar
   git rev-parse HEAD
   ```

   Expected verdict: distribution task succeeds; record the launched jar's SHA-256 and the exact commit printed by `git rev-parse` with the capture record. The jar digest identifies the tested build and must not be inferred from an existing installation.

## 8. Acceptance Capture Procedure

1. From the integration worktree, use the JDK 21 and Gradle preconditions in §7.2. This worktree has no `BIN/`, and
   `freeplane:createGitProperty` writes `BIN/gitinfo.txt` without creating the parent, so create it first, then
   distribute:

   ```sh
   mkdir -p BIN
   gradle dist
   sha256sum BIN/plugins/org.freeplane.plugin.graph/lib/plugin-1.13.4.jar
   git rev-parse HEAD
   ```

   Record both values before launch; the jar digest is what distinguishes this build from any existing installation.
2. Create isolated runtime directories and an evidence directory:

   ```sh
   mkdir -p /tmp/graph-latex-whole-title-acceptance/home
   mkdir -p /tmp/graph-latex-whole-title-acceptance/config
   mkdir -p freeplane_plugin_graph/build/evidence
   ```

3. Start a dedicated virtual X server on the unused display `:99`:

   ```sh
   Xvfb :99 -screen 0 1600x1000x24 -nolisten tcp > /tmp/graph-latex-whole-title-acceptance/xvfb.log 2>&1 &
   xvfb_pid=$!
   ```

   Stop only this Xvfb and the Freeplane app launched for this capture.
4. From the repository root, launch the just-built app from `BIN` with the isolated home/config and JDK:

   ```sh
   cd BIN && DISPLAY=:99 HOME=/tmp/graph-latex-whole-title-acceptance/home XDG_CONFIG_HOME=/tmp/graph-latex-whole-title-acceptance/config JAVA_HOME="$JAVA_HOME" JAVA_OPTS="-Dorg.freeplane.nosplash=true" ./freeplane.sh
   ```

5. In that Freeplane instance, open `/home/henry/Documents/999.notebooks/01.math/00.foundation.of.math/02.set_theory/mindmap/Axiomatic_Set_Theory.mm`, search for `is well-ordered by`, and open the Graph Workspace. A freshly created workspace holds no maps, so **add the opened map to the workspace explicitly** with the Graph Workspace's add-map action and wait for the layout to settle before capturing.
6. Only nodes marked as graph groups are projected as discs, so only those carry a node label. The map's eleven marked nodes include the four required ones: `ID_215702338` (`\\latex Theorem: $ON$ is well-ordered by $\\in$`), `ID_297617399` (`\\latex Theorem: Equivalence of $\\leqslant$ and $\\subseteq$`), `ID_1222855984` (`\\latex Theorem: $\\bigcup\\alpha\\leqslant\\alpha$`), and the plain control `ID_1887656421` (`Theorem: initial segement`). Capture the Graph Workspace window with the tools present on this host:

   ```sh
   wid=$(DISPLAY=:99 xdotool search --name 'Graph Workspace' | head -n 1)
   DISPLAY=:99 xdotool getwindowname "$wid"   # must print exactly: Graph Workspace
   DISPLAY=:99 xdotool windowsize "$wid" 1600 1000
   DISPLAY=:99 xwd -id "$wid" -out /tmp/graph-latex-whole-title-acceptance/graph.xwd
   ffmpeg -y -i /tmp/graph-latex-whole-title-acceptance/graph.xwd \
     freeplane_plugin_graph/build/evidence/latex-whole-title-acceptance.png
   ```

   The workspace window's title is `Graph Workspace`, not `Freeplane`: the main application frame is a different
   window, so searching for `Freeplane` would capture the wrong one. The `getwindowname` check is required before the
   capture. `xdotool`, `xwd`, `ffmpeg` and `Xvfb` are installed; no other capture utility is assumed.
7. Record the PNG path, jar SHA-256, commit, map SHA-256 `840358a03666d02f445e3aeb5ba84d9852e913318a3bf6c1776f50593aaff08c`, and host/font information from `freeplane_plugin_graph/doc/latex-whole-title/environment.txt`. The expected label font is Sans Serif plain 12; the environment evidence resolves it to Noto Sans.

The four-label capture is an application acceptance check, not a substitute for T1-T13, the module suite, `:freeplane:compileJava`, or `verifyGraphBundle`.

## 9. Gaps Returned to the Design, and Their Resolution

The drafter returned three gaps. All three are resolved here; none returns to the design, because none changes an
approved decision.

- **G1 — Markdown prefix with a no-dollar body. Resolved: it is wrapped.** A `markdownPatternFormat` node whose
  whole-title signal comes from a `\\latex` or `\\unparsedlatex` declaration is treated as a declared formula, so the
  no-dollar wrap rule applies and `TitleText.Math.source()` is `\\text{` + body + `}`. The pattern-format exception
  exists only for the two formats that declare a formula (`latexPatternFormat`, `unparsedLatexPatternFormat`); a
  markdown format declares text, so the declaration governs the presentation. A dollar-containing
  Markdown-prefixed body stays bare, exactly as §2.2 states for every dollar-bearing body.
- **G2 — Behavior-red compile scaffold. Resolved: there is no scaffold.** Stage 1 records the compilation failure with
  T8 present and `measureDegradedText` absent. Stage 2 adds that method in its final form; because it is additive and
  no production path calls it, the stage-1 failure disappears with no temporary code and nothing to remove. T8 is
  green at stage 2 while T1-T3, T5-T7 and T9-T11 remain red because the extractor and `placeMathNode` are still
  unchanged. §7.2 states this ordering.
- **G3 — Production-path 250-title remeasurement. Resolved: `LatexWholeTitleSampleShould` is the harness.** It loads
  `freeplane_plugin_graph/doc/latex-whole-title/titles-sample.json`, builds one `NodeModel` per body with the text
  `\\latex ` + body, extracts through `SafeNodeLabelExtractor`, measures at the label font, and asserts the
  production-path totals. With `LATEX_PROBE_DUMP` set it writes
  `freeplane_plugin_graph/doc/latex-whole-title/production-sample-results.tsv`, which becomes the evidence artifact.
  §7.2 stage 4 gives the command.
