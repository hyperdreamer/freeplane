# Graph Workspace LaTeX node titles — technical specification

Task identifier: `2026-11-03-graph-latex-titles`

Specification date: 2026-11-03. Revision: draft 3. Status: ready for review; implementation and verification have
not been run by this authoring task.

Design authority: [2026-11-03-graph-latex-titles-design.md](2026-11-03-graph-latex-titles-design.md), approved
revision **r8**, design commit `6f8ac68e3f37be08334108a6ecf6158129eeddf1`, blob
`067ea560f75a6044828005a774f1bd523fd8c0dd`, SHA-256
`1ade315ce6e3216e5c1c2a6bd287b5cd2b581492f2b76af8c8d393f3fe068935`, approved by the operator on 2026-11-03.

Application baseline: `fd7488d5ae7f92bcdb3f70fc76b09f6e881aacc0` (branch `plugin/graph-workspace`).

Authoring this specification authorised no build, test, commit, branch, worktree, or host transition.

Draft history: draft 1 (`933cb6f19c`) first specification; draft 2 (`b969b60b5a`) applied specification review
round 1; draft 3 (this revision) applies review round 2 and re-bases on design r8. The changes are: the resolved
row is now a **paint-ready world-unit** structure built at placement time, so the painter never measures anything
(S4, R2); deferral is a distinct memo form so a deferred entry is never served as a terminal measurement (S6);
budget consumption has one authoritative point and never touches plain titles (S7); the parser's escape rules keep
ordinary backslashes intact and make brace depth escape-aware (R1); combined format/prefix extraction is specified
(R4); and the test and bundle-gate controls are rebuilt to be genuinely falsifiable (S14–S17, R5, R6).

## 1. Result and scope

A node whose title contains LaTeX formulas draws those formulas as typeset notation in the graph workspace instead
of as backslash source text. A formula the bundled JLaTeXMath engine cannot parse, or that does not fit its label
slot, is drawn as its raw source text exactly as today. Nothing else about the graph changes: identical label
slots, identical readability for screen readers and search, identical hover and sidebar text, identical hull and
layout padding.

| Title | Slot | Result |
| --- | --- | --- |
| `Theorem: initial segment` | any | unchanged plain label |
| `Theorem: $\bigcup\alpha\leqslant\alpha$` | 200 px vertical | formula typeset inside the sentence |
| `Theorem: $\bigcup\alpha\leslant\alpha$` | any | unchanged raw source text (`\leslant` is unknown to the engine) |
| `Theorem: Equivalence of $\leqsla…` | 130 px horizontal | raw source text, ellipsized by the existing rule |
| a whole-title `\latex …` node | any | one formula run |

## 2. Exact implementation change set

Production paths relative to `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/`; test paths relative
to `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/`. Java 8, four-space indentation.

| File | Required change |
| --- | --- |
| `projection/input/TitleText.java` | new immutable run model (§3.1) |
| `projection/input/TitleTextParser.java` | new total parser (§3.2, §5.1) |
| `projection/input/SafeNodeLabel.java` | derived `title()` plus a public three-argument factory (§3.3) |
| `adapter/SafeNodeLabelExtractor.java` | whole-text math signal and combined format/prefix handling (§5.2) |
| `canvas/title/TitleMetrics.java` | shared render context and numeric helpers (§3.4) |
| `canvas/title/MeasuredTitle.java` | new measurement result (§3.5) |
| `canvas/title/MeasuredRun.java` | new per-run measurement (§3.6) |
| `canvas/title/FormulaIcons.java` | icon cache with failure memoization (§3.7, §5.4) |
| `canvas/title/TitleMeasurer.java` | measurement service, budget, generation queue, stall (§3.8, §5.3, §5.7) |
| `canvas/title/TitleRow.java` | new paint-ready row (§3.9) |
| `canvas/title/RunRow.java`, `PlainRunRow.java`, `MathRunRow.java` | paint-ready run types (§3.10) |
| `canvas/title/TitleTruncation.java` | degraded text and truncation glue (§3.11, §5.5) |
| `canvas/title/TitlePainter.java` | painter with no measurement (§3.12, §5.6) |
| `canvas/PlacedLabel.java` | `titleRow` field and accessor (§3.13) |
| `canvas/ScreenLabelPlacement.java` | title plumbing, form selection, deferral, budget signal (§3.14, §5.3–§5.7) |
| `canvas/ScreenLabelPlacementCache.java` | `passEpoch`, follow-up request, injection (§3.15, §5.7) |
| `canvas/GraphPainter.java` | base-transform capture and label delegation (§3.16) |
| `canvas/GraphCanvas.java` | one `repaint()` after an exhausted pass (§3.17) |
| `build.gradle` | dependency and bundle-gate assertions (§8) |
| `src/main/resources/META-INF/NOTICE.jlatexmath.txt` | licence provenance (§8) |
| `projection/input/TitleTextParserShould.java` | new (T11–T14) |
| `canvas/title/TitleMeasurerShould.java` | new (T4 series, T7 series) |
| `canvas/title/TitleTruncationShould.java` | new (T15) |
| `canvas/title/TitlePainterShould.java` | new (T1b, T3d, T6, T6b) |
| `canvas/title/FormulaIconsShould.java` | new (T16) |
| `canvas/ScreenLabelPlacementCacheShould.java` | new (T7, T8, T17, T18) |
| `canvas/ScreenLabelPlacementShould.java` | extended (T4e, T5, T8b, T9) |
| `canvas/GraphCanvasPaintShould.java` | extended (T1, T3, T3b); two `PlacedLabel` sites (`:1919`, `:1976`) |
| `canvas/ScreenLabelLiveTitleRefreshShould.java` | one `PlacedLabel` site (`:107`) plus the deferral case |
| `adapter/SafeNodeLabelExtractorShould.java` | extended (T2, T10) |
| `smoke/GraphWorkspaceUiEvidence.java` | four reference titles, first-pass count and timing |

Tracked image outputs, regenerated by the evidence run and therefore part of the deliverable rather than authored:
`docs/superpowers/specs/images/2026-08-10-graph-workspace-implemented.png` and
`…-graph-group-marker-implemented.png`. No other file changes.

`PlacedLabel` construction sites: six production — `enclosureLabel` (`ScreenLabelPlacement.java:341`), `retained`
twice (`:492`, `:519`), the `ladder` candidate (`:564`), `baseSlot` (`:666`), `hoverOnly` (`:677`) — and three in
tests: `GraphCanvasPaintShould.java:1919`, `:1976`, `ScreenLabelLiveTitleRefreshShould.java:107`. `ladder` itself
starts at `:526`.

## 3. Java declaration contracts

### 3.1 `TitleText`

`public final class org.freeplane.plugin.graph.projection.input.TitleText`

- Immutable; one field `private final List<Run> runs`, copied into an unmodifiable list.
- `public interface Run`. `public static final class Plain implements Run` with `private final String text`,
  `public Plain(String text)` (null → `NullPointerException("text")`) and `public String text()`.
  `public static final class Math implements Run` with `private final String source`, `private final String
  rawSource`, `public Math(String source, String rawSource)` (nulls → `NullPointerException` naming the argument),
  `source()` and `rawSource()`.
- `public static TitleText plain(String text)`; null → `IllegalArgumentException("text must not be null")`.
- `public static TitleText of(List<Run> runs)`; null list → `NullPointerException("runs")`, empty →
  `IllegalArgumentException("runs must not be empty")`, null element → `NullPointerException("run")`.
- `runs()`, `hasMath()`, `plainSource()` (concatenation of `Plain.text()` and `Math.rawSource()`).
- `equals`/`hashCode` over `runs`; `Plain` and `Math` implement both, so run **kind** is part of identity and a
  plain run cannot collide with a formula run of identical characters.

### 3.2 `TitleTextParser`

`public final class …projection.input.TitleTextParser`

- Private constructor; `public static TitleText parse(String source, boolean wholeTextIsMath)`; null source →
  `NullPointerException("source")`. Total: never throws for non-null input.
- Empty input returns `TitleText.plain("")` regardless of `wholeTextIsMath` (empty check precedes everything).
- Scan rules: §5.1.

### 3.3 `SafeNodeLabel`

- Adds `private final TitleText title` and `public TitleText title()`.
- `public static SafeNodeLabel of(String full, String display)` delegates to
  `of(full, display, TitleText.plain(display))`.
- `public static SafeNodeLabel of(String full, String display, TitleText title)`; nulls →
  `NullPointerException` naming the argument; existing non-empty text rules unchanged.
- **`equals`/`hashCode` compare `fullText` and `displayText` only.** The derived title is excluded so projection
  identity, deduplication and the existing extractor tests keep their meaning (T2).

### 3.4 `TitleMetrics`

`public final class …canvas.title.TitleMetrics`

- `public static final FontRenderContext SCREEN_FRC = new FontRenderContext(null, true, true);` — declared here
  because `ScreenLabelPlacement.SCREEN_FRC` is package-private in `canvas`, a sibling package. T4e pins the two
  contexts equal by measurement.
- `public static final int STYLE = TeXConstants.STYLE_TEXT;`
- `public static final double MAX_HEIGHT_FACTOR = 2.0;`
- `public static int px(Font font)` → `Math.max(1, Math.round(font.getSize2D()))`.
- `public static double lineHeight(Font font)` → `font.getLineMetrics("Hg", SCREEN_FRC).getHeight()`.
- `public static Font worldFont(Font logicalFont, double zoom)` → `logicalFont.deriveFont(Math.max(1.0f, (float)
  (logicalFont.getSize2D() / zoom)))`. This is the clamp `GraphPainter` uses today (`:281-282`), extracted so the
  row builder and the painter cannot disagree.

### 3.5 `MeasuredTitle`

`public final class …canvas.title.MeasuredTitle`

- `public enum Availability { RENDERED, DEGRADED }`.
- Fields in order: `Availability availability`, `List<MeasuredRun> runs`, `double width`, `double height`,
  `double ascent`, `double descent`, `boolean deferredForBudget`.
- `public static MeasuredTitle rendered(...)`, `public static MeasuredTitle degraded(...)` taking runs and the four
  numbers; `public MeasuredTitle deferred()` returns a copy with the flag set.
- Accessors for every field.
- Validation: defensive copy, no null runs; all doubles finite; `width >= 0`, `height > 0`, `ascent >= 0`,
  `descent >= 0`; violations → `IllegalArgumentException("measurement must be finite and non-negative: " + field)`.
  All numbers are **screen pixels** at the logical font size.

### 3.6 `MeasuredRun`

`public final class …canvas.title.MeasuredRun`

- `public enum Kind { PLAIN, MATH }`.
- Fields: `Kind kind`, `String source`, `String rawSource`, `double x`, `double width`, `double ascent`,
  `double descent`; accessors of the same names.
- `public static MeasuredRun plain(String text, double x, double width, double ascent, double descent)` and
  `public static MeasuredRun math(String body, String rawSource, double x, double width, double ascent,
  double descent)`.
- Validation: non-null strings, finite numbers, `x >= 0`, `width >= 0`, `ascent >= 0`, `descent >= 0`.

### 3.7 `FormulaIcons`

`public final class …canvas.title.FormulaIcons`

- `public static final int CAPACITY = 512;`
- `public FormulaIcons()`; `public FormulaIcons(int capacity)` (`capacity < 1` →
  `IllegalArgumentException("capacity must be positive")`).
- `public TeXIcon icon(String source, int px, int style)` returns the cached icon, builds on a miss, and returns
  `null` when the engine rejects the source; a rejected key is recorded and never rebuilt for the lifetime of this
  instance.
- `buildCount()`, `hitCount()`, `failureCount()`.
- Build body: `final TeXFormula formula = new TeXFormula(source); final TeXIcon icon =
  formula.new TeXIconBuilder().setStyle(style).setSize(px).build();`. `RuntimeException` records a failure and
  returns `null`; `Error` propagates.

### 3.8 `TitleMeasurer`

`public final class …canvas.title.TitleMeasurer`

- `public static final int DEFAULT_MAX_NEW_MEASUREMENTS_PER_PASS = 200;`
- `public TitleMeasurer()`; `public TitleMeasurer(FormulaIcons icons, int maxNewMeasurementsPerPass)` for
  injection (null icons → `NullPointerException`, non-positive budget → `IllegalArgumentException`).
- **Memo form.** `private static final class Key { TitleText title; Font font; Form form; }` with
  `enum Form { RENDERED, DEGRADED, DEFERRED }`. `title` participates through `TitleText.equals` (run kinds
  included), `font` through `getFamily()`, `getStyle()` and `getSize2D()`.
- Fields: `FormulaIcons icons`, `int budget`, `Map<Key, MeasuredTitle> cache` (a `LinkedHashMap` returned as an
  unmodifiable view is not required; iteration order irrelevant), `LinkedHashSet<Key> pending`,
  `int newMeasurementsInPass`, `long generation`, `boolean stalled`, `String stallDiagnostic`.
- `public void beginPass(long projectionGeneration)`: on a generation change clear `cache`, `pending`, `stalled`
  and `stallDiagnostic`, and set `generation`; always reset `newMeasurementsInPass` to 0.
- `public MeasuredTitle measure(TitleText title, Font font)`:
  1. `RENDERED` hit → return it. `DEFERRED` hit → treat as unknown (see 4).
  2. No math → compute the plain measurement, memoize under `RENDERED`, return it. **Plain titles never consume
     budget and are never deferred.**
  3. Unknown rendered key and `consumeBudgetSlot()` returns false → `defer(title, font)`.
  4. Otherwise compute per §5.3 and memoize.
- `public MeasuredTitle measureDegraded(TitleText title, Font font)`:
  1. `DEGRADED` hit → return it.
  2. No math → return `measure(title, font)` (plain titles are free).
  3. Budget exhausted → `defer(title, font)`.
  4. Otherwise compute the degraded measurement (§5.3 step 7) and memoize under `DEGRADED`.
- `public MeasuredTitle defer(TitleText title, Font font)` returns the degraded measurement with
  `deferredForBudget = true`, memoizes it under `DEFERRED`, and adds the key to `pending`. It never calls the engine.
- `public boolean isKnown(TitleText, Font)`, `public boolean isPending(TitleText, Font)`,
  `public boolean consumeBudgetSlot()`, `budgetExhaustedInLastPass()`, `stalled()`, `stallDiagnostic()`,
  `pendingCount()`, `cacheSize()`, `newMeasurementsInLastPass()`, `public void markStalled(int remaining)`.
- `consumeBudgetSlot()` is the **only** budget mutation: it returns `true` and increments
  `newMeasurementsInPass` while `newMeasurementsInPass < budget`, else `false`.

### 3.9 `TitleRow`

`public final class …canvas.title.TitleRow`

- `public enum Form { RENDERED, DEGRADED }`.
- **All geometry in this type is in world units appropriate for painting.** Fields: `Form form`,
  `List<RunRow> runs`, `double width`, `double height`, `double ascent`, `double descent`,
  `boolean deferredForBudget`; accessors for each.
- `public static TitleRow resolve(MeasuredTitle measured, TitleText title, FormulaIcons icons, Font logicalFont,
  double zoom)`:
  - `final Font worldFont = TitleMetrics.worldFont(logicalFont, zoom);`
  - `final double scale = 1.0 / zoom;`
  - plain runs: `String paintText = source`; `Rectangle2D b = worldFont.getStringBounds(paintText,
    TitleMetrics.SCREEN_FRC)`; `LineMetrics lm = worldFont.getLineMetrics(paintText, TitleMetrics.SCREEN_FRC)`;
    run geometry `x = measuredRun.x() * scale`, `width = b.getWidth()`, `ascent = -b.getY()`,
    `descent = b.getHeight() + b.getY()`.
  - math runs: `x = measuredRun.x() * scale`, `width = measuredRun.width() * scale`,
    `ascent = measuredRun.ascent() * scale`, `descent = measuredRun.descent() * scale`, and the icon from
    `icons.icon(body, TitleMetrics.px(logicalFont), TitleMetrics.STYLE)`.
  - row `width` is the sum of run widths; `ascent`/`descent` are the maxima; `height = ascent + descent`.
  - **The form never changes here.** A `RENDERED` measurement whose icon lookup returns `null` throws
    `IllegalStateException("rendered measurement lost its icon")`; a `DEGRADED` measurement contains no math runs,
    so it resolves without an icon lookup.
- Defensive copying and validation mirror §3.5: finite numbers, `width >= 0`, `height > 0`, `ascent >= 0`,
  `descent >= 0`.

### 3.10 `RunRow`, `PlainRunRow`, `MathRunRow`

- `public interface RunRow` → `double x()`, `double width()`.
- `public final class PlainRunRow implements RunRow` — `public PlainRunRow(String paintText, double x, double
  width, double ascent, double descent)`; accessors `paintText()`, `x()`, `width()`, `ascent()`, `descent()`.
- `public final class MathRunRow implements RunRow` — `public MathRunRow(String body, TeXIcon icon, double x,
  double width, double ascent, double descent)`; accessors `body()`, `icon()`, `x()`, `width()`, `ascent()`,
  `descent()`. Null icon → `NullPointerException("icon")`.
- Both validate finite numbers and `x >= 0`, `width >= 0`, `ascent >= 0`, `descent >= 0`.

### 3.11 `TitleTruncation`

`public final class …canvas.title.TitleTruncation`

- `public static String degradedPlainText(TitleText title)` — concatenation of `Plain.text()` and
  `Math.rawSource()` in run order; adds no characters the user did not type. Null → `NullPointerException`.
- `public static String truncated(String text, Font worldFont, double limit)` — thin delegation to
  `ScreenLabelPlacement.truncateTo` is **not** possible from this package (package-private). The method therefore
  re-implements the same loop with the same `\u2026` ellipsis and the same `stripTrailing` rule, and T15 asserts it
  returns exactly what `truncateTo` returns for the shared fixtures via a `canvas` test.
- `public static boolean fits(String text, Font worldFont, double limit)` returns
  `worldFont.getStringBounds(text, TitleMetrics.SCREEN_FRC).getWidth() <= limit`.

### 3.12 `TitlePainter`

`public final class …canvas.title.TitlePainter`

- `public static void paint(Graphics2D graphics, TitleRow row, double anchorX, double anchorY, double zoom,
  Color colour, AffineTransform baseTransform)`.
- **No `Font` parameter and no measurement.** The row already carries world-unit paint geometry and the plain
  runs carry their `paintText`. `zoom` is used only to convert a formula's ascent from world units to device
  pixels (§5.6 step 5).
- Validation before any drawing: non-null `graphics`, `row`, `colour`, `baseTransform` (`NullPointerException`
  naming the parameter); finite `anchorX`, `anchorY`, `zoom` with `zoom > 0`; and
  `Double.isFinite(baseTransform.getDeterminant()) && baseTransform.getDeterminant() != 0.0`, else
  `IllegalArgumentException("base transform must be invertible")`. NaN determinants are therefore rejected (S12).
- Stateless. No field or parameter refers to `TitleMeasurer`, `FormulaIcons` or `MeasuredTitle`.

### 3.13 `PlacedLabel`

- Adds `private final TitleRow titleRow` and package-private `TitleRow titleRow()`.
- The constructor gains `TitleRow titleRow` as its **last** parameter, after `slot`; null →
  `NullPointerException("titleRow")`.
- Every existing accessor and rule keeps its meaning; `truncated()` keeps its existing definition (the drawn text
  differs from the source), not "ends with an ellipsis" (S13).
- Geometry consistency: `titleRow.width() * zoom` must equal `width` within `1e-6`, and likewise for height;
  a mismatch throws `IllegalArgumentException("title row geometry must match the label")`. The placement that
  builds the label supplies the `zoom` it used.

### 3.14 `ScreenLabelPlacement`

- Keeps the no-argument constructor (default `TitleMeasurer`); adds package-private
  `ScreenLabelPlacement(TitleMeasurer measurer)`.
- Adds package-private `measurer()`, `budgetExhaustedInLastPass()`, `measurementStalled()`, `measurementDiagnostic()`.
- `place(...)` calls `measurer.beginPass(request.projection().generation())` first.
- `screenBounds`/`textWidth`/`truncateTo` keep their visibility and behaviour.
- `retained(...)` gains the deferral exclusion (§5.5 step 6).

### 3.15 `ScreenLabelPlacementCache`

- `Key` gains `private final long passEpoch`, included in `equals`/`hashCode`, and `Key.of` gains a
  `long passEpoch` parameter (`ScreenLabelPlacementCache.java:57` currently takes `(request, fonts)` only) (S11).
- Keeps the no-argument constructor; adds package-private
  `ScreenLabelPlacementCache(ScreenLabelPlacement placement)` for tests (S14).
- `place(...)` behaves as today on a key match, except that after a computation which exhausted the budget it sets
  `followUpPending` and increments `passEpoch`, so the next identical request is a miss.
- `public boolean takeFollowUpRequest()` returns `true` once and clears the flag;
  `budgetExhaustedInLastPass()` and `measurementStalled()` delegate to the placement.

### 3.16 `GraphPainter`

- `paint(...)` captures `final AffineTransform baseTransform = graphics.getTransform();` first.
- `paintLabels(...)` keeps `graphics.setFont(label.font().deriveFont(...))` for nothing (deleted) and instead calls
  `TitlePainter.paint(graphics, label.titleRow(), anchorX, anchorY, viewport.zoom(), theme.labelColor(),
  baseTransform)`. Colour, composite, leader lines and ordering are unchanged.
- `drawCentered(...)` is deleted.

### 3.17 `GraphCanvas`

- `labelsFor(...)` posts one `repaint()` when `labelPlacementCache.takeFollowUpRequest()` is true.

## 4. Construction-site rules

| Site | Row supplied |
| --- | --- |
| `enclosureLabel` (`:341`) | plain row over the enclosure `displayText()`, built with the placement's logical font and zoom |
| `retained` (`:492`, `:519`) | the row rebuilt for the remembered rung's form |
| `ladder` candidate (`:564`) | rendered form for non-truncating rungs, degraded form for truncating rungs |
| `baseSlot` (`:666`) | degraded form |
| `hoverOnly` (`:677`) | degraded form |

Degraded is the design's normative final fallback, so forced and hover labels show raw source when no rendered
candidate fits. Enclosure labels are never formula-parsed: their rows are always a single `PlainRunRow`, and T9
asserts their geometry is unchanged even when the derived title is math-bearing.

## 5. Algorithms

### 5.1 Parser scan

1. `source.isEmpty()` → `TitleText.plain("")`.
2. `wholeTextIsMath` → one `Math` run: `source` is the input with a single leading and trailing `$` removed if and
   only if both are present and unescaped; `rawSource` is the input **verbatim**, never a synthesised delimiter
   pair (S3).
3. Otherwise scan with index `i`, `StringBuilder plain`, `List<Run> runs`, `int braceDepth = 0`,
   `boolean unmatched = false`.
4. **Escape rule.** On `\\` at `i` with a following character `c`:
   - `c == '$'` → append `$` to `plain`, advance 2 (the backslash is consumed; `\$` is a literal dollar);
   - `c == '\\'` → append `\` to `plain`, advance 2;
   - any other `c` → append **both** `\\` and `c`, advance 2. Ordinary titles such as `C:\tmp` therefore keep
     their backslash (R1).
   A trailing lone `\\` appends `\\` and ends the scan.
5. **Escaped braces do not count.** In the delimiter search of step 7, `\{` and `\}` are skipped as a pair and never
   change `braceDepth`, so `$\{x$` closes normally (R1).
6. `$$` at `i`: search for the next unescaped `$$` at depth 0. Found → flush `plain`, emit one `Math` run with
   `rawSource` including both `$$`, continue after them. Not found → `unmatched = true`, stop.
7. `$` at `i`: search forward for the next unescaped `$` at `braceDepth == 0` (`{` increments, `}` decrements but
   never below 0, escaped braces skipped). Found → flush `plain`, emit one `Math` run with `rawSource` including
   both `$`, continue after the closer. Not found → `unmatched = true`, stop.
8. `unmatched` → the whole input becomes one `Plain` run (no partial formula survives, and an earlier valid
   formula cannot leak). Otherwise flush the remaining `plain`, drop empty runs, and if nothing remains return
   `TitleText.plain(source)`.

Worked examples asserted by T11–T14:

| Input | Runs |
| --- | --- |
| `costs \$5 and $\alpha$` | `Plain("costs $5 and ")`, `Math("\\alpha", "$\\alpha$")` |
| `$$a$$` | `Math("a", "$$a$$")` |
| `text $a$ text` | `Plain("text ")`, `Math("a", "$a$")`, `Plain(" text")` |
| `$\text{$x$}$` | `Math("\\text{$x$}", "$\\text{$x$}$")` |
| `$\{x$` | `Math("\\{x", "$\\{x$")` |
| `C:\tmp and $\alpha$` | `Plain("C:\\tmp and ")`, `Math("\\alpha", "$\\alpha$")` |
| `unmatched $a` | `Plain("unmatched $a")` |
| `$a$ and $b` | `Plain("$a$ and $b")` |
| `$a$ and $b$` | `Math("a", "$a$")`, `Plain(" and ")`, `Math("b", "$b$")` |

### 5.2 Whole-text math and combined format/prefix handling

`SafeNodeLabelExtractor` sets `wholeTextIsMath` when `NodeStyleModel.getNodeFormat(node)` is `latexPatternFormat`
or `unparsedLatexPatternFormat`, or when the raw content starts with `\latex`/`\unparsedlatex` plus whitespace.

`removeLatexPrefix` returns the source unchanged for `latexPatternFormat`, `unparsedLatexPatternFormat` and
`markdownPatternFormat` (`SafeNodeLabelExtractor.java:73-75`), so a node that combines one of those formats **and**
a `\latex` prefix keeps the prefix in `displayText`. That existing behaviour must not change (R4). The rule for
the derived title is therefore:

1. `fullText` and `displayText` are produced exactly as today.
2. The parser input is `displayText`, unless the display text starts with `\latex`/`\unparsedlatex` followed by
   whitespace, in which case the prefix and one following space are removed **for the formula body only**; the
   removed text remains part of `rawSource` so degradation reproduces the user's own text.
3. In that combined case `wholeTextIsMath` is true, because the node declared itself a formula.

T10 asserts both the combined fixture and the unchanged `displayText`.

### 5.3 Measurement

`TitleMeasurer.measure(title, font)`:

1. `RENDERED` hit → return.
2. No math → plain measurement via `font.getStringBounds(title.plainSource(), TitleMetrics.SCREEN_FRC)` with
   `ascent = -bounds.getY()`, `descent = bounds.getHeight() - ascent`, `width = bounds.getWidth()`; one
   `MeasuredRun.plain` at `x = 0`. No budget, no queue.
3. If `consumeBudgetSlot()` returns `false` → `defer(title, font)`.
4. For each run in order: plain runs via `getStringBounds` with `TitleMetrics.SCREEN_FRC`; formula runs via
   `icons.icon(source, TitleMetrics.px(font), TitleMetrics.STYLE)`.
5. Any `null` icon, any non-finite or non-positive icon dimension, any icon height greater than
   `TitleMetrics.MAX_HEIGHT_FACTOR * TitleMetrics.lineHeight(font)`, or a non-finite `getBaseLine() * height`
   → memoize `measureDegradedInBudget(title, font)` under `RENDERED` and return it. The budget slot was already
   consumed by step 3, so the degraded computation that follows does **not** consume a second one (S7).
6. Compose: cumulative `x`; `ascent = max(plain ascent, icon.getBaseLine() * icon.getIconHeight())`;
   `descent = max(plain descent, icon.getIconHeight() - thatAscent)`; `height = ascent + descent`;
   `width` = sum. `getBaseLine()` is a ratio and is never used as a distance.
7. Memoize under `RENDERED` and return.

`measureDegradedInBudget(title, font)` (private): builds `TitleTruncation.degradedPlainText(title)`, measures it
with `getStringBounds`, returns a `DEGRADED` `MeasuredTitle` with one plain run.

`TitleMeasurer.defer(title, font)` (public): returns `measureDegradedInBudget(...).deferred()`, memoizes it under
`DEFERRED`, and adds the key to `pending`. A `DEFERRED` memo entry is never returned as a terminal measurement:
steps 1 of `measure`/`measureDegraded` ignore it, so a later pass recomputes.

### 5.4 Icon cache behaviour

Hit → return and count; recorded failure → `null`; miss → build (§3.7), insert, evict least-recently-used beyond
capacity; `RuntimeException` → record failure, return `null`.

### 5.5 Form selection, truncation and retention

1. `TitleText title = node.label().title()`; `Font font` is the rung's face from `LadderRung.font(LabelFonts)`.
2. Non-truncating rung: `MeasuredTitle rendered = measurer.measure(title, font)`; accept when
   `rendered.availability() == RENDERED` and `rendered.width() <= slotMaxWidth(slot)`.
3. Truncating rung: `MeasuredTitle degraded = measurer.measureDegraded(title, font)`; when its width exceeds the
   limit, truncate its text with `TitleTruncation.truncated(text, TitleMetrics.worldFont(font, zoom), limit)` and
   build the row from the truncated string as one plain run.
4. A `DEGRADED` result offers only the degraded form.
5. `baseSlot`/`hoverOnly` use the degraded form.
6. `retained(...)` refuses a remembered placement whose `titleRow().deferredForBudget()` is true.
7. `truncated` is `!drawnText.equals(sourceText)`.

### 5.6 Painting

Given `row`, `anchorX`, `anchorY`, `zoom`, `colour`, `baseTransform`:

1. Validate (§3.12).
2. `double left = anchorX - row.width() * 0.5;` and
   `double baseline = anchorY + (row.ascent() - row.descent()) * 0.5;` — both already world units, so a plain-only
   row lands exactly where `drawCentered` puts it today, including at the font clamp.
3. `PlainRunRow`: `graphics.setColor(colour)`, `graphics.setFont(run.worldFont())`, then
   `graphics.drawString(run.paintText(), (float) (left + run.x()), (float) baseline)`. The run carries the derived
   `worldFont` from `TitleRow.resolve`, so the painter derives nothing.
4. `MathRunRow`:
   1. `Point2D deviceAnchor = graphics.getTransform().transform(new Point2D.Double(left + run.x(), baseline), null);`
   2. `Point2D deviceTop = new Point2D.Double(deviceAnchor.getX(), deviceAnchor.getY() - run.ascent() * zoom);`
   3. `Point2D component = baseTransform.inverseTransform(deviceTop, null);`
   4. `Graphics2D copy = (Graphics2D) graphics.create(); try { copy.setTransform(baseTransform);
      copy.translate(component.getX(), component.getY()); run.icon().setForeground(colour);
      run.icon().paintIcon(null, copy, 0, 0); } finally { copy.dispose(); }`
   The ascent is converted to device pixels **before** the inverse transform (S4), so the icon's top edge sits
   exactly `ascent` device pixels above the shared baseline and `B` is applied exactly once.

### 5.7 Budget, queue, follow-up and stall

1. Each pass starts with `beginPass(generation)`.
2. At most `budget` new measurements per pass; hits, plain titles and deferrals cost nothing.
3. A pass reports exhaustion when it deferred at least one key.
4. `ScreenLabelPlacementCache.place` sets `followUpPending`, increments `passEpoch`, and `GraphCanvas.labelsFor`
   posts one `repaint()`.
5. Predicate with stall precedence:
   ```text
   followUp = deferredThisPass > 0 && measuredThisPass > 0
   stall    = deferredThisPass > 0 && measuredThisPass == 0
   ```
6. On stall the measurer records `FORMULA_MEASUREMENT_STALL: <n> formula titles unmeasured after <p> passes`,
   requests no further follow-up, and keeps deferred titles degraded until the generation changes.
7. No key can re-enter `pending` within a generation, so a stable generation with `T` distinct keys completes in
   `ceil(T / budget)` measurement passes; `T = 0` completes in one pass with no deferral.

## 6. Invariants

1. **Plain identity.** A title with no math produces byte-identical `drawString` arguments and identical geometry
   to the baseline at the same request, at zoom 1 and at the zoom-24 clamp. (T1, T1b)
2. **Identity preservation.** `SafeNodeLabel` equality ignores the derived title; `displayText`, `fullText`,
   accessibility, search and enclosure geometry are unchanged, including a math-bearing enclosure title. (T2, T9)
3. **No omission, no partial formula.** Every drawn math run is an icon whose top edge is `ascent` device pixels
   above the shared baseline; every degraded formula contributes its complete raw source. (T3, T3b, T3c, T3d)
4. **Finite measurement.** Every `MeasuredTitle` has finite numbers, `height > 0`, `width >= 0`, non-negative
   ascent/descent; over-height, zero-width or non-finite-baseline icons degrade. (T4, T4b, T4c, T4f, T4g)
5. **Slot cap scope.** Every ladder candidate respects `slotMaxWidth` for vertical, horizontal and diagonal slots
   and both faces; forced `baseSlot` labels and retained faces after demotion are exempt. (T5)
6. **Painter purity.** `TitlePainter` has no measurement dependency: no field or parameter of type
   `TitleMeasurer`, `FormulaIcons` or `MeasuredTitle`, and its source contains no `getStringBounds` or
   `getLineMetrics` call. (T6)
7. **Bounded progress.** At most `budget` new measurements per pass; the predicate of §5.7 holds exactly; a
   stable generation with `T` keys completes in `ceil(T / budget)` passes. (T7, T7b, T7c)
8. **Deferral is terminal-eventually.** A deferred row is never retained, and wherever the queue is allowed to
   finish it reaches a terminal measurement within the remaining batches; terminal measurement does not promise
   typeset drawing. (T8, T8b)
9. **Bundle gate and notice.** Pinned name set and checksums, exact `Bundle-ClassPath`, class major `<= 52`, no
   `Import-Package`/`Export-Package`, a present `META-INF/NOTICE.jlatexmath.txt` whose text carries the jar's
   checksum and the licence phrases the jar actually contains. (T19)

## 7. Test declarations

Every test class constructs its own `FormulaIcons` and `TitleMeasurer`; no shared static state. Faces come from
`new Font(Font.SANS_SERIF, Font.PLAIN, 12)` and `new Font(Font.SANS_SERIF, Font.BOLD, 12)`; logical families
always resolve, so no pinned font bytes are needed (S15).

| Id | Class | Method | Fixture and assertion | Discriminator |
| --- | --- | --- | --- | --- |
| T1 | `canvas.GraphCanvasPaintShould` | `plainTitlesDrawTheSameStringsAsTheBaseline` | frozen baseline fixture, zoom 1; recorded strings and coordinates equal constants | any change in centring or offsets moves the numbers |
| T1b | `canvas.title.TitlePainterShould` | `plainRowAtZoomAboveTheClampUsesTheClampedFont` | 12 pt, zoom 24; drawn font size is `1.0f` and baseline equals that font's `LineMetrics` | dividing logical metrics by zoom yields 0.5 pt |
| T2 | `adapter.SafeNodeLabelExtractorShould` | `safeNodeLabelEqualityIgnoresTheDerivedTitle` | equal display text, different titles; `equals` and `hashCode` equal | putting `title` into equality fails |
| T3 | `canvas.GraphCanvasPaintShould` | `unrenderableFormulaDrawsItsCompleteRawSource` | `Theorem: $\bigcup\alpha\leslant\alpha$`, roomy slot; `DEGRADED`, raw substring present, no `MathRunRow` | dropping the run fails the substring |
| T3b | `canvas.GraphCanvasPaintShould` | `truncatingRungNeverPaintsAPartialIcon` | formula wider than 130 px; `DEGRADED`, ellipsis present, no `MathRunRow` | a rendered truncating rung paints an icon |
| T3c | `canvas.title.TitleMeasurerShould` | `multipleFormulasDegradeWholesale` | `a $\leslant$ b $\alpha$`; `DEGRADED`, one plain run, both raw sources | per-run degradation leaves a math run |
| T3d | `canvas.title.TitlePainterShould` | `formulaBaselineMatchesTheTextBaseline` | mixed row, zoom 1; icon top equals `baseline - ascent` within 0.5 px | painting at the baseline instead of above it fails by one ascent |
| T4 | `canvas.title.TitleMeasurerShould` | `rejectsNonFiniteAndNonPositiveIconMetrics` | stub icon width 0; `DEGRADED` | removing the guard throws from validation |
| T4b | `canvas.title.TitleMeasurerShould` | `rejectsFormulaTallerThanTwiceTheLineHeight` | stub height `3 ×` line height; `DEGRADED` | removing the threshold yields `RENDERED` |
| T4c | `canvas.title.TitleMeasurerShould` | `baselineRatioIsAppliedInPixels` | two stub icons with `getBaseLine()` 0.25/0.75 and height 20; asserted ascent is 5 and 15 | using the ratio as a distance gives 0.25/0.75 |
| T4d | `canvas.title.TitleMeasurerShould` | `sameIconPixelsDifferentFontKeepsSeparateMeasurements` | `a $\alpha$` with 12 pt sans and 12 pt sans bold, both to 12 px icons; `cacheSize() == 2` | dropping the font from the key yields `cacheSize() == 1` |
| T4e | `canvas.ScreenLabelPlacementShould` | `titleMeasurerMatchesPlacementScreenBounds` | plain title; `measurer.measure(...)` numbers equal `screenBounds(text, font)` exactly | a divergent `FontRenderContext` fails equality. Lives in `canvas` because `screenBounds` is package-private (R3) |
| T4f | `canvas.title.TitleMeasurerShould` | `emptyTitleIsMeasuredWithoutThrowing` | `TitleText.plain("")`; `width == 0`, `height > 0` | a positive-width precondition throws |
| T4g | `canvas.title.TitleMeasurerShould` | `rejectsNonFiniteBaselineRatio` | stub `getBaseLine()` = `NaN`; `DEGRADED` | an unvalidated ratio propagates `NaN` |
| T5 | `canvas.ScreenLabelPlacementShould` | `formulaCandidateRespectsEverySlotCap` | over-cap titles in vertical, horizontal and diagonal slots with both faces; each `DEGRADED` and within the cap | skipping the check admits an oversized rendered row |
| T6 | `canvas.title.TitlePainterShould` | `painterHasNoMeasurementDependency` | reflection over declared fields and `paint` parameters plus a source read of `TitlePainter.java` | a paint-time lookup adds a dependency or a `getStringBounds` call |
| T6b | `canvas.title.TitlePainterShould` | `nonIdentityBaseTransformPositionsTheIconOnce` | `B = translate(37,11)·scale(1.25)`, zoom 1.6; icon origin equals independently computed `B·W·p` within 0.5 px; a following plain run keeps world coordinates | a double-applied `B` lands at `B²Wp` |
| T7 | `canvas.ScreenLabelPlacementCacheShould` | `measuresAtMostTheBudgetAndDefersTheRest` | budget 2, five unknown titles; after one pass `newMeasurementsInLastPass() == 2`, `pendingCount() == 3`, follow-up requested | removing the budget measures all five |
| T7b | `canvas.ScreenLabelPlacementCacheShould` | `completesInTheQueueBound` | budget 2, 600 titles; follow-ups continue until `pendingCount() == 0`, the pass count is `ceil(600/2)`, `stalled()` false | a stalled or endless chain fails the count |
| T7c | `canvas.ScreenLabelPlacementCacheShould` | `stallsOnlyWhenAPassThatDeferredWorkMeasuresNothing` | budget 0 injected; first pass defers 1 and measures 0; `stalled()` true, diagnostic prefix `FORMULA_MEASUREMENT_STALL`, no follow-up | a stall without the predicate fails the prose expectation |
| T8 | `canvas.ScreenLabelPlacementCacheShould` | `deferredTitleIsMeasuredWithinTheQueueBound` | budget 1, three unknown titles; first pass defers two, subsequent passes measure them, all `RENDERED` at the end | removing `passEpoch` returns the first degraded list forever |
| T8b | `canvas.ScreenLabelPlacementShould` | `doesNotRetainABudgetDeferredFace` | remembered, non-`HOVER_ONLY`, `TRUNCATED_NEAR` deferred row; `retained` returns null | retaining it returns the degraded row |
| T9 | `canvas.ScreenLabelPlacementShould` | `enclosureLabelWithMathTitleKeepsPlainGeometry` | enclosure built via `SafeNodeLabel.of(raw, display, TitleText.of(Math))`; one `PlainRunRow`, frozen geometry constants | engine-facing enclosure measurement changes the width |
| T10 | `adapter.SafeNodeLabelExtractorShould` | `combinedPatternFormatAndLatexPrefixIsParsedAsOneFormula` | `latexPatternFormat` node whose source starts with `\latex`; `displayText` unchanged, `title()` one `Math` run, `rawSource` keeps the prefix | dropping the prefix rule yields a plain title (R4) |
| T11 | `projection.input.TitleTextParserShould` | `plainTextIsOneRun` … `emptySourceYieldsOneEmptyPlainRun` | the nine worked examples of §5.1 plus empty-source precedence | each example is a direct assertion |
| T12 | `projection.input.TitleTextParserShould` | `ordinaryBackslashesSurvive` | `C:\tmp and $\alpha$` splits with `C:\tmp` intact | consuming every escape yields `C:tmp` (R1) |
| T13 | `projection.input.TitleTextParserShould` | `escapedBracesDoNotAffectDepth` | `$\{x$` is one math run with body `\{x` | naive depth counting rolls the title back (R1) |
| T14 | `projection.input.TitleTextParserShould` | `unmatchedDelimiterRollsBackTheWholeTitle` | `$a$ and $b`; one plain run | leaking the first formula fails |
| T15 | `canvas.title.TitleTruncationShould` | `matchesPlacementTruncationForSharedFixtures` | three widths; `TitleTruncation.truncated` equals `ScreenLabelPlacement.truncateTo` when called from a `canvas` test | a differing loop yields different ellipsis positions |
| T16 | `canvas.title.FormulaIconsShould` | `buildsEachKeyOnce`, `cachesFailuresWithoutRebuilding`, `evictsLeastRecentlyUsedBeyondCapacity` | counting stub engine via capacity 1 and repeated keys | removing failure memoization rebuilds |
| T17 | `canvas.ScreenLabelPlacementCacheShould` | `structuredKeySeparation` | a plain title whose text is `$a$` and a parsed math title with the same characters; `cacheSize() == 2` | joined-string keys collapse to one |
| T18 | `canvas.ScreenLabelPlacementCacheShould` | `resolvedRowSurvivesIconEviction` | resolve a row, evict its icon (capacity 1 plus a second key), repaint; no exception, icon present in the row | a row that refers to the cache fails |
| T19 | `build.gradle` (`verifyGraphBundle`) | built-in control | one list-equality assertion binds both directions: `assert [true, false] == [sameNameSet(expectedNames), sameNameSet(expectedNames.findAll { it != 'jlatexmath-1.0.7.jar' })]` | deleting either side breaks the list equality; deleting the pin makes the second member true and fails |

## 8. Build and bundle changes

```groovy
lib('org.scilab.forge:jlatexmath:1.0.7') {
    transitive = false
}
```

Pinned checksum: `6d04c3843fc98d2eea9ba4cff0be7b80540ca75c5a4e0b3463fd1219c48cc65d` (identical in the installed
distribution and the Gradle module cache).

`verifyGraphBundle` additions and replacements:

```groovy
def graphStreamChecksums = [
    'gs-core-1.3.jar': '2d6a6f92f86c624fcbf468fc7e9cb9c8e3fb7e14c72ad578edb04cc36b0b66cd',
    'pherd-1.0.jar':   '9e74f3702d13756faece5987147c937c09b6837a38ed32199f59c26697b94230',
    'mbox2-1.0.jar':   '3c2db334867211f385a2d62d061818268443f361381f78bbc53f9e897e145983'
]
def bundledChecksums = new LinkedHashMap<String, String>(graphStreamChecksums)
bundledChecksums.put('jlatexmath-1.0.7.jar',
    '6d04c3843fc98d2eea9ba4cff0be7b80540ca75c5a4e0b3463fd1219c48cc65d')

def expectedNames = bundledChecksums.keySet().toList().sort()
def sameNameSet = { List<String> names -> names.collect { it }.sort() == expectedNames }
assert [true, false] == [sameNameSet(dependencyFiles*.name),
                         sameNameSet(expectedNames.findAll { it != 'jlatexmath-1.0.7.jar' })]

bundledChecksums.each { fileName, expectedChecksum ->
    final File dependency = dependencyFiles.find { it.name == fileName }
    assert dependency != null
    assert sha256(dependency) == expectedChecksum
    assert maximumClassMajor(dependency) <= 52
}
assert attributes.getValue('Bundle-ClassPath') ==
    ".,lib/plugin-${project.version}.jar,lib/gs-core-1.3.jar,lib/pherd-1.0.jar,lib/mbox2-1.0.jar,lib/jlatexmath-1.0.7.jar"
assert !attributes.getValue('Import-Package')
assert !attributes.getValue('Export-Package')

final java.util.jar.JarEntry latexNotice = bundle.getEntry('META-INF/NOTICE.jlatexmath.txt')
assert latexNotice != null
final String latexNoticeText = bundle.getInputStream(latexNotice).getText('UTF-8')
assert latexNoticeText.contains('6d04c3843fc98d2eea9ba4cff0be7b80540ca75c5a4e0b3463fd1219c48cc65d')
assert latexNoticeText.contains('org.scilab.forge:jlatexmath:1.0.7')
assert latexNoticeText.contains('GNU GENERAL PUBLIC LICENSE')
assert latexNoticeText.contains('either version 2 of the License, or (at your option) any later version')
assert latexNoticeText.contains('As a special exception')
```

`latexNotice` is a distinct name because the existing GraphStream block already declares `final String notice`
inside its own scope (`build.gradle:96`); the GraphStream assertions stay unchanged (R5). The three asserted
phrases are literal text of the jar's own `META-INF/LICENSE` and `META-INF/COPYING`; the notice describes the
GPL-with-classpath-exception arrangement in its own words rather than asserting a phrase the jar does not contain
(R6).

`src/main/resources/META-INF/NOTICE.jlatexmath.txt` records: the coordinate and SHA-256 above; that the archive is
GPL version 2 or later with a special linking exception, quoting the jar; and one line per bundled font family
(OFL for the AMS fonts, Knuth licence for the Computer Modern and Cyrillic families, free licence for `dsrom10`,
public domain for `stmary10`/`rsfs10`, GPL version 2 for the Greek fonts), pointing at
`org/scilab/forge/jlatexmath/fonts/licences/`.

## 9. Errors, compatibility and cleanup

| Situation | Behaviour |
| --- | --- |
| engine throws | failure recorded; rendered measurement `DEGRADED`; raw source drawn |
| unmatched/unbalanced delimiter | whole title one plain run; no engine call |
| formula over `2.0 ×` line height, zero/negative/non-finite metric, non-finite baseline | `DEGRADED` |
| empty title | `width == 0`, `height > 0`, drawn as today |
| budget exhausted | deferred `DEGRADED`, queued, never retained, one follow-up request |
| pass deferred work and measured nothing | `FORMULA_MEASUREMENT_STALL`, no further request, degraded for that generation |
| non-invertible or non-finite base transform | `IllegalArgumentException("base transform must be invertible")` before drawing |
| row geometry disagrees with the label box | `IllegalArgumentException("title row geometry must match the label")` |
| rendered measurement loses its icon at resolution | `IllegalStateException("rendered measurement lost its icon")` |

Existing tests that must pass unmodified (only the three `PlacedLabel` call sites gain the row argument):
`ScreenLabelPlacementShould`, `ScreenLabelLiveTitleRefreshShould` (including `keepsAnUnchangedForcedOverCapFace`,
`keepsAnOverCapFaceAfterDemotionAndOnTheNextReplay`,
`keepsAnUnchangedTruncatedFaceWhenSpaceBecomesAvailable`), `GraphCanvasPaintShould`, `AccessibleGraphCanvasShould`,
`GraphSearchModelShould`, `AdapterArchitectureShould`, and every existing `SafeNodeLabelExtractorShould` assertion.

No translation resource, resource-bundle key, XML format or workspace persistence change.

## 10. Verification commands and evidence

```text
gradle :freeplane_plugin_graph:test
gradle :freeplane_plugin_graph:verifyGraphBundle
gradle :freeplane_plugin_graph:graphUiEvidence
```

1. `test` — all module tests green, including every class in §7.
2. `verifyGraphBundle` — four pinned jars, extended `Bundle-ClassPath`, notice entry with its checksum and licence
   phrases, and the bound naming control.
3. `graphUiEvidence` — regenerates the two tracked images and prints the first-pass measurement count and elapsed
   time for the four reference titles, which are the only inputs that may change the default budget of 200.

None of these commands ran while authoring this specification: the authoring environment has no JDK, no Gradle, and
no network. No test, bundle or evidence result is claimed here.

## 11. Decision record

Inherited from the approved design (r8): `$$…$$` is one text-style formula; wholesale degradation on truncating
rungs; enclosure labels keep `displayText()` geometry; only `deferredForBudget` rows are excluded from retention;
the base transform is captured before the world transform and applied once; the per-generation queue with
`FORMULA_MEASUREMENT_STALL`; the measurement memo is generation-scoped with no fixed capacity.

New decisions this specification makes where the design is silent:

| Decision | Justification |
| --- | --- |
| `TitleRow` carries world-unit paint geometry and each `PlainRunRow` carries its derived `worldFont` | the approved design forbids paint-time measurement, and the world font depends on zoom, which only the placement knows (R2) |
| Parser lives in `projection.input` | keeps `projection..` free of Freeplane feature types; no AWT needed |
| Rendering types live in `canvas.title` | keeps the existing `canvas..` surface unchanged and confines the engine dependency |
| `TitleMetrics.SCREEN_FRC` mirrors the placement's context | that constant is package-private; T4e compares the two by measurement |
| `TitleTruncation.truncated` re-implements the placement's loop | `truncateTo` is package-private; T15 requires equality from a `canvas` test |
| Budget is constructor-injectable | makes the per-pass quota and the stall path falsifiable without reflection |
| `TitleRow.resolve` throws when a rendered icon vanishes | degrading there would need geometry the row does not have |
| `truncated` keeps the source-versus-drawn definition | existing retention logic depends on it |
| A stalled generation keeps degraded titles until it changes | the design's stall outcome has no other exit |
