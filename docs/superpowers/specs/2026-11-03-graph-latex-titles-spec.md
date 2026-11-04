# Graph Workspace LaTeX node titles — technical specification

Task identifier: `2026-11-03-graph-latex-titles`

Specification date: 2026-11-03. Status: ready for review; implementation and verification have not been run by
this specification authoring task.

Design authority: [2026-11-03-graph-latex-titles-design.md](2026-11-03-graph-latex-titles-design.md), approved
revision r4, design commit `571c7bb3a37603797c1b799827d6d52c75662b78`, blob
`b12838a597df80095a5ba1c1f3ccdebf7b86f597`, SHA-256
`1a3d7af575f41bca9925bc06a18f27adfca48c56c1f87a67bb0791644e19fce8`.

Application baseline: `fd7488d5ae7f92bcdb3f70fc76b09f6e881aacc0` (branch `plugin/graph-workspace`).

Authoring this specification authorised no build, test, commit, branch, worktree, or host transition. The
verification commands in section 10 are instructions for the implementation phase, not evidence that they ran.

## 1. Result and scope

A node whose title contains LaTeX formulas draws those formulas as typeset notation in the graph workspace instead
of as backslash source text. A formula that the bundled JLaTeXMath engine cannot parse, or that does not fit its
label slot, is drawn as its raw source text exactly as today. Nothing else about the graph changes: the same
labels are placed in the same slots, the same titles are readable by screen readers and search, the same hover
bar and sidebar text appear, and the same hull and layout padding is reserved.

The change is confined to `freeplane_plugin_graph` production source plus the test and evidence files listed in
section 2. It adds one bundled runtime library (JLaTeXMath 1.0.7) to the graph plugin and updates that plugin's
bundle gate accordingly.

Truth table for a title, stated as observable behaviour:

| Title | Slot | Result |
| --- | --- | --- |
| `Theorem: initial segment` | any | unchanged plain label |
| `Theorem: $\bigcup\alpha\leqslant\alpha$` | 200 px vertical | formula typeset inside the sentence |
| `Theorem: $\bigcup\alpha\leslant\alpha$` | any | unchanged raw source text (`\leslant` is unknown to the engine) |
| `Theorem: Equivalence of $\leqsla…` | 130 px horizontal | raw source text, ellipsized by the existing rule |
| any whole-title `\latex …` node | any | one formula run |

## 2. Exact implementation change set

All Java paths are relative to `freeplane_plugin_graph/`. Java 8 source level, four-space indentation.

| File | Required change |
| --- | --- |
| `src/main/java/org/freeplane/plugin/graph/projection/input/TitleText.java` | new immutable run model (3.1) |
| `src/main/java/org/freeplane/plugin/graph/projection/input/TitleTextParser.java` | new total parser (3.2, 5.1) |
| `src/main/java/org/freeplane/plugin/graph/projection/input/SafeNodeLabel.java` | add derived `title()`; existing constructor/factory/equality unchanged (3.3) |
| `src/main/java/org/freeplane/plugin/graph/adapter/SafeNodeLabelExtractor.java` | compute the whole-text math signal (5.2) |
| `src/main/java/org/freeplane/plugin/graph/canvas/title/MeasuredTitle.java` | new measurement result (3.4) |
| `src/main/java/org/freeplane/plugin/graph/canvas/title/MeasuredRun.java` | new per-run measurement (3.5) |
| `src/main/java/org/freeplane/plugin/graph/canvas/title/FormulaIcons.java` | new icon cache with failure memoization (3.6, 5.4) |
| `src/main/java/org/freeplane/plugin/graph/canvas/title/TitleMeasurer.java` | new memoizing measurement service and budget counter (3.7, 5.3) |
| `src/main/java/org/freeplane/plugin/graph/canvas/title/TitleRow.java` | new resolved row (3.8) |
| `src/main/java/org/freeplane/plugin/graph/canvas/title/RunRow.java`, `PlainRunRow.java`, `MathRunRow.java` | resolved run types (3.9) |
| `src/main/java/org/freeplane/plugin/graph/canvas/title/TitleTruncation.java` | rendered/degraded selection and degraded truncation (5.5) |
| `src/main/java/org/freeplane/plugin/graph/canvas/title/TitlePainter.java` | new painter (3.10, 5.6) |
| `src/main/java/org/freeplane/plugin/graph/canvas/PlacedLabel.java` | add the `titleRow` field and accessor; all existing declarations preserved (3.11) |
| `src/main/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacement.java` | thread `TitleText`/`TitleMeasurer`; rendered vs degraded per rung; deferral rule; budget signal (3.12, 5.3–5.6) |
| `src/main/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacementCache.java` | `passEpoch` in `Key`, one-shot follow-up request (3.13, 5.7) |
| `src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java` | capture the base transform, delegate label text to `TitlePainter` (3.14) |
| `src/main/java/org/freeplane/plugin/graph/canvas/GraphCanvas.java` | one `repaint()` request after an exhausted pass (3.15) |
| `build.gradle` | add the `lib` dependency and the bundle-gate assertions (section 8) |
| `src/main/resources/META-INF/NOTICE.jlatexmath.txt` | new licence provenance file (section 8) |
| `src/test/java/org/freeplane/plugin/graph/projection/input/TitleTextParserShould.java` | new |
| `src/test/java/org/freeplane/plugin/graph/canvas/title/TitleMeasurerShould.java` | new |
| `src/test/java/org/freeplane/plugin/graph/canvas/title/TitleTruncationShould.java` | new |
| `src/test/java/org/freeplane/plugin/graph/canvas/title/TitlePainterShould.java` | new |
| `src/test/java/org/freeplane/plugin/graph/canvas/title/FormulaIconsShould.java` | new |
| `src/test/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacementCacheShould.java` | new |
| `src/test/java/org/freeplane/plugin/graph/canvas/ScreenLabelPlacementShould.java` | extended |
| `src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java` | extended, plus three constructor call sites |
| `src/test/java/org/freeplane/plugin/graph/canvas/ScreenLabelLiveTitleRefreshShould.java` | one constructor call site, plus the deferral case |
| `src/test/java/org/freeplane/plugin/graph/adapter/SafeNodeLabelExtractorShould.java` | extended |
| `src/test/java/org/freeplane/plugin/graph/smoke/GraphWorkspaceUiEvidence.java` | four reference titles and first-pass timing |

No other file changes. `PlacedLabel` is constructed in six production sites — `enclosureLabel` (:341), `retained`
(:492), `ladder` retained-row (:519), `ladder` candidate (:564), `baseSlot` (:666), `hoverOnly` (:677) — and in
three test sites: `ScreenLabelLiveTitleRefreshShould.java:107`, `GraphCanvasPaintShould.java:1919` and
`GraphCanvasPaintShould.java:1976`. Every one gains the new argument.

## 3. Java declaration contracts

### 3.1 `TitleText`

`public final class org.freeplane.plugin.graph.projection.input.TitleText`

- Immutable. Fields: `private final List<Run> runs`.
- Nested `public interface Run`; nested `public static final class Plain implements Run` with `private final String text` and `public String text()`; nested `public static final class Math implements Run` with `private final String source`, `private final String rawSource`, `public String source()`, `public String rawSource()`.
- `public static TitleText plain(String text)` builds one `Plain` run.
- `public static TitleText of(List<Run> runs)` copies the list defensively into an unmodifiable list and rejects a null or empty list with `IllegalArgumentException("runs must not be empty")`.
- `public List<Run> runs()`, `public boolean hasMath()`, `public String plainSource()` (concatenation of every run's user-visible source, i.e. `Plain.text()` and `Math.rawSource()`).
- `equals`/`hashCode` over `runs`, with `Plain` and `Math` implementing both themselves. Two titles are equal exactly when their run sequences are equal.

### 3.2 `TitleTextParser`

`public final class org.freeplane.plugin.graph.projection.input.TitleTextParser`

- Private constructor; `public static TitleText parse(String source, boolean wholeTextIsMath)`.
- Total: it never throws for any non-null input, including empty input (returns `TitleText.plain("")`).
- `null` source is rejected with `NullPointerException("source")`.
- The scan is section 5.1.

### 3.3 `SafeNodeLabel`

`public final class org.freeplane.plugin.graph.projection.input.SafeNodeLabel`

- Adds `private final TitleText title` and `public TitleText title()`.
- The existing `of(String full, String display)` factory keeps its signature and delegates to a new
  `of(String full, String display, TitleText title)`; the two-string form uses `TitleText.plain(display)`. The
  private constructor gains the `title` parameter and rejects null with `NullPointerException("title")`.
- **`equals` and `hashCode` are unchanged**: they compare `fullText` and `displayText` only. The derived title is
  deliberately excluded so projection identity, deduplication, and the existing extractor tests keep their
  meaning. This is a documented exception to the usual value-type rule and is asserted by a test (T2).

### 3.4 `MeasuredTitle`

`public final class org.freeplane.plugin.graph.canvas.title.MeasuredTitle`

- `public enum Availability { RENDERED, DEGRADED }`.
- Fields, in order: `Availability availability`, `List<MeasuredRun> runs`, `double width`, `double height`,
  `double ascent`, `double descent`, `boolean deferredForBudget`.
- `public static MeasuredTitle rendered(List<MeasuredRun> runs, double width, double height, double ascent, double descent)`.
- `public static MeasuredTitle degraded(List<MeasuredRun> runs, double width, double height, double ascent, double descent)`.
- `public MeasuredTitle deferred()` returns a copy with `deferredForBudget = true`.
- Accessors: `availability()`, `runs()`, `width()`, `height()`, `ascent()`, `descent()`, `deferredForBudget()`.
- Validation in the private constructor: every double is finite and `width`, `height` are `> 0.0`, otherwise
  `IllegalArgumentException("measurement must be finite and positive")`. A degraded title is measured from its
  plain source, so it always satisfies this.

### 3.5 `MeasuredRun`

`public final class org.freeplane.plugin.graph.canvas.title.MeasuredRun`

- `public enum Kind { PLAIN, MATH }`.
- Fields: `Kind kind`, `String source` (plain text or formula body), `String rawSource` (plain text, or the
  delimited source of a formula), `double x`, `double width`, `double ascent`, `double descent`.
- `public static MeasuredRun plain(String text, double x, double width, double ascent, double descent)`.
- `public static MeasuredRun math(String source, String rawSource, double x, double width, double ascent, double descent)`.
- All offsets and extents are in **screen pixels** at the logical label font size.

### 3.6 `FormulaIcons`

`public final class org.freeplane.plugin.graph.canvas.title.FormulaIcons`

- `public static final int CAPACITY = 512`.
- Fields: `LinkedHashMap<Key, TeXIcon> icons` (access-ordered, `removeEldestEntry` at `CAPACITY`), `Set<Key>
  failures`, `int builds`, `int hits`.
- `public TeXIcon icon(String source, int px, int style)` returns the cached icon, builds it on a miss, and
  returns `null` when the engine rejects the source. A rejected key is recorded in `failures` and never rebuilt,
  so a broken macro costs one attempt per process per `(source, px, style)`.
- `public int buildCount()` and `public int hitCount()` are test and diagnostic accessors.
- `Key` is a private static final class with `source`, `px`, `style`, `equals` and `hashCode`.
- A build exception of any `RuntimeException` type is caught, recorded as a failure, and returns `null`. An
  `Error` is not caught.
- Icons are built with `TeXFormula.new TeXIconBuilder().setStyle(style).setSize(px).build()`. No foreground
  colour is set at build time; `TitlePainter` sets it before painting (section 5.6).

### 3.7 `TitleMeasurer`

`public final class org.freeplane.plugin.graph.canvas.title.TitleMeasurer`

- `public static final int MAX_NEW_MEASUREMENTS_PER_PASS = 200`.
- `public static final int CAPACITY = 8192`.
- Fields: `FormulaIcons icons`, `LinkedHashMap<Key, MeasuredTitle> cache` (access-ordered LRU at `CAPACITY`),
  `int newMeasurementsInPass`, `long generation`.
- `public MeasuredTitle measure(TitleText title, Font font)` returns the memoized measurement, computing it on a
  miss and counting the miss against the pass budget through `consumeBudgetSlot()`.
- `public boolean isKnown(TitleText title, Font font)` is true when the measurement for that title and font is
  memoized.
- `public boolean consumeBudgetSlot()` returns false once the pass has already produced `MAX_NEW_MEASUREMENTS_PER_PASS`
  new measurements.
- `public void beginPass(long projectionGeneration)` resets `newMeasurementsInPass` and records the generation.
- `public boolean budgetExhaustedInLastPass()`.
- `Key` includes the joined run source, a form flag, and the **full logical font identity**: family, style and
  `getSize2D()`. Two different fonts can share an icon pixel size and must not share a measurement.
- Measurement rules are section 5.3.

### 3.8 `TitleRow`

`public final class org.freeplane.plugin.graph.canvas.title.TitleRow`

- `public enum Form { RENDERED, DEGRADED }`.
- Fields: `Form form`, `List<RunRow> runs`, `double width`, `double height`, `double ascent`, `double descent`,
  `boolean deferredForBudget`.
- `public static TitleRow resolve(MeasuredTitle measured, TitleText title, FormulaIcons icons, int px, int style)`
  materializes the chosen form: for each `MeasuredRun` it produces a `PlainRunRow`, or a `MathRunRow` carrying
  the icon obtained from `icons.icon(source, px, style)`. Because a `RENDERED` measurement guarantees every math
  run was renderable when it was measured, a `null` icon here is impossible within one generation; if it happens
  the run is resolved as a `PlainRunRow` over its raw source and `form` becomes `DEGRADED`, which keeps the row
  total and keeps painting free of decisions.
- Accessors for every field. Validation mirrors `MeasuredTitle`.

### 3.9 `RunRow`, `PlainRunRow`, `MathRunRow`

`public interface org.freeplane.plugin.graph.canvas.title.RunRow` declares `double x()` and `double width()`.

`public final class PlainRunRow implements RunRow` adds `String text`, `double ascent`, `double descent`;
constructor `(String text, double x, double width, double ascent, double descent)`.

`public final class MathRunRow implements RunRow` adds `String source`, `TeXIcon icon`, `double ascent`,
`double descent`; constructor `(String source, TeXIcon icon, double x, double width, double ascent, double descent)`.
`TeXIcon` is never null in this type.

### 3.10 `TitlePainter`

`public final class org.freeplane.plugin.graph.canvas.title.TitlePainter`

- `public static void paint(Graphics2D graphics, TitleRow row, Font logicalFont, double anchorX, double anchorY,
  double zoom, Color colour, AffineTransform baseTransform)`.
- `anchorX` and `anchorY` are the label's **world** anchor. `logicalFont` is the label face at its logical size.
  `zoom` is the viewport zoom. `baseTransform` is the transform the component supplied to `GraphPainter.paint`
  before the world transform was concatenated.
- The method is stateless. It throws `IllegalArgumentException` for a null row, font, colour or transform, and
  for a non-finite anchor or a non-positive zoom.
- Painting sequence is section 5.6.

### 3.11 `PlacedLabel`

- Adds one field `private final TitleRow titleRow` and one package-private accessor `TitleRow titleRow()`.
- The single package-private constructor gains `TitleRow titleRow` as its **last** parameter. The parameter is
  rejected with `NullPointerException("titleRow")`.
- Every existing accessor, validation rule, and field keeps its current meaning. `text()` still returns the drawn
  plain string: the joined source for a `RENDERED` row, the truncated degraded text for a `DEGRADED` row.
- A `TitleRow` whose geometry disagrees with the label's `width`/`height` is a contract violation; the
  constructor asserts equality within `1e-6` and throws `IllegalArgumentException("title row geometry must match the label")`.

### 3.12 `ScreenLabelPlacement`

- `place(...)` gains no parameter. The class gains a package-private constructor taking a `TitleMeasurer`; the
  existing no-argument constructor uses a default `TitleMeasurer` with a default `FormulaIcons`.
- `screenBounds(String, Font)` and `textWidth(String, Font)` keep their visibility and behaviour.
- A new package-private `TitleText fullTitle(ProjectedNode node)` returns `node.label().title()`; a new
  package-private `Font measuringFont(Slot slot, LabelFonts fonts)` maps a slot to the full or dense face.
- `truncateTo(String, Font, double)` is unchanged and remains the only truncator for plain text.
- `retained(...)` gains the deferral exclusion of section 5.5 step 6.
- The budget and follow-up signals are section 5.7.

### 3.13 `ScreenLabelPlacementCache`

- `Key` gains `private final long passEpoch` and includes it in `equals`/`hashCode`.
- Fields: `long passEpoch`, `boolean followUpPending`.
- `public List<PlacedLabel> place(LabelPlacementRequest request, LabelFonts fonts)` behaves as today for a key
  match, except that when the last computation exhausted the budget it bumps `passEpoch` once, which makes the
  next call for otherwise identical state a miss.
- `public boolean takeFollowUpRequest()` returns `true` at most once per exhaustion and clears the flag.
- `public boolean budgetExhaustedInLastPass()` delegates to the placement.

### 3.14 `GraphPainter`

- `paint(...)` captures `final AffineTransform baseTransform = graphics.getTransform();` as its first statement.
- `paintLabels(...)` passes `baseTransform` and `viewport.zoom()` to `TitlePainter.paint` and keeps deriving the
  world font exactly as today, including the `Math.max(1.0f, size / zoom)` clamp (`GraphPainter.java:281-282`).
- `drawCentered(Graphics2D, Font, String, double, double)` is deleted once no caller remains.
- Everything else — colours, dimming composite, leader lines, ordering — is unchanged.

### 3.15 `GraphCanvas`

- `labelsFor(...)` posts one `repaint()` when `labelPlacementCache.takeFollowUpRequest()` is true, and nothing
  otherwise. The call happens after `place(...)` returns and before the labels are returned.

## 4. Construction-site rules

| Site | Arguments that change |
| --- | --- |
| `enclosureLabel` | the enclosure's `label().title()` measured through the placement's measurer, plain form only |
| `retained` retained-row | the current node's `title()` resolved to the remembered rung's form |
| `ladder` retained-row | the same |
| `ladder` candidate | the current node's `title()` resolved to the rendered form, or the degraded form for a truncating rung |
| `baseSlot` | the current node's `title()`, rendered form when available, else degraded |
| `hoverOnly` | the current node's `title()`, rendered form when available, else degraded |

An enclosure label is never formula-parsed today: `ProjectedEnclosure.labels()` carries `SafeNodeLabel` values
built by `ProjectionEngine.HullBuilder`, and the design keeps their measurement on `displayText()`. Their rows are
therefore always a single `PlainRunRow`, and their geometry must be byte-identical to the baseline. A test asserts
this (T9).

## 5. Algorithms

### 5.1 Parser scan

Input `source`, `wholeTextIsMath`.

1. If `wholeTextIsMath` is true, return one `Math` run with `source` and `rawSource = "$" + source + "$"` when the
   source has no delimiters of its own, otherwise `rawSource = source`.
2. Otherwise scan left to right with index `i`, an `int braceDepth = 0`, and a `StringBuilder plain`.
3. On `\\` at `i`: append `\\` and the next character to `plain` (if any) and advance two.
4. On `$` at `i`: if `plain` is non-empty, flush it as a `Plain` run and clear the builder. Find the closing `$`
   using the delimiter scan of step 5. If no closer exists, append the unmatched `$` to `plain` and continue after it.
   If a closer exists, emit a `Math` run over the body and continue after the closer.
5. Delimiter scan from `i + 1`: tracks `braceDepth` (`{` increments, `}` decrements, never below zero) and honours
   `\\` escaping; a `$` is a closer only when `braceDepth == 0`. This is what stops `\text{$x$}$` from closing
   inside the `\text`.
6. At end of input, flush any remaining `plain` as a `Plain` run.
7. Drop empty runs. If nothing remains, return `TitleText.plain(source)`.
8. A `Math` run records `rawSource` as the exact substring including both delimiters, so D3 degradation reproduces
   what the user typed.

### 5.2 Whole-text math signal

`SafeNodeLabelExtractor` passes `wholeTextIsMath = true` when either condition holds:

1. `NodeStyleModel.getNodeFormat(node)` is `latexPatternFormat` or `unparsedLatexPatternFormat`; or
2. the node's raw content, before whitespace normalization, starts with `\latex` or `\unparsedlatex` followed by
   whitespace.

This flag is computed where the format and the raw prefix are already available, in the adapter. `markdownPatternFormat`
does not set the flag; dollar delimiters inside markdown source still produce math runs through the ordinary scan.

### 5.3 Measurement

`TitleMeasurer.measure(title, font)`:

1. Build the memo key from `title`, the form flag, and the full font identity. Return the memoized value on a hit.
2. If the title has no math run, measure exactly as today:
   `font.getStringBounds(title.plainSource(), ScreenLabelPlacement.SCREEN_FRC)` and return a `RENDERED`
   `MeasuredTitle` with one `MeasuredRun.plain` at `x = 0`, `ascent = -bounds.getY()`,
   `descent = bounds.getHeight() - ascent`, `width = bounds.getWidth()`.
3. Otherwise, for each run in order: measure plain runs with `font.getStringBounds(text, SCREEN_FRC)`; for formula
   runs obtain `icons.icon(source, px, style)` where `px = Math.round(font.getSize2D())` and
   `style = TeXConstants.STYLE_TEXT`.
4. If any formula icon is `null`, any icon dimension is non-finite or not strictly positive, or any icon height
   exceeds `2.0 ×` the plain line height of `font` (`font.getLineMetrics("Hg", SCREEN_FRC).getHeight()`), return
   the **degraded** measurement computed from the degraded title instead (step 7).
5. Compose the row: `x` accumulates the previous widths; `ascent` is the maximum of the plain ascent and every
   formula `ascent = icon.getBaseLine() * icon.getIconHeight()`; `descent` is the maximum of the plain descent and
   every formula `descent = icon.getIconHeight() - ascent`; `height = ascent + descent`; `width` is the sum.
   `getBaseLine()` is a ratio and must never be used as a distance.
6. Store the memo entry and count it against the pass budget.
7. Degraded measurement: replace every formula run with its `rawSource` as plain text and measure the resulting
   single plain string with `font.getStringBounds`, producing a `DEGRADED` result. Degraded results are memoized
   under the same key with the form flag set to degraded.

### 5.4 Icon cache behaviour

1. `icon(source, px, style)` returns the cached icon on a hit and increments `hitCount`.
2. A recorded failure returns `null` and does not increment `buildCount`.
3. On a miss and no recorded failure, build the icon, increment `buildCount`, insert it, and evict the
   least-recently-used entry when over `CAPACITY`.
4. A `RuntimeException` from the engine records a failure and returns `null`.

### 5.5 Form selection, truncation and retention

For a node title, in this order:

1. Compute `TitleText title = node.label().title()`.
2. Let `rendered = measurer.measure(title, font)` and, when `rendered.availability() == RENDERED` and the rung
   is non-truncating, accept the candidate when `rendered.width() <= slotMaxWidth(slot)`.
3. When the rung is truncating, use the degraded form: measure the degraded title, and if its width exceeds
   `slotMaxWidth(slot)` apply the existing `truncateTo` character loop to the degraded plain string.
4. When `rendered.availability() == DEGRADED`, only the degraded form is offered, through truncating rungs and
   the final fallback.
5. If the budget is exhausted and the title is not known, produce a degraded measurement marked
   `deferredForBudget = true`, use it as the degraded form, and do not consult the engine.
6. `retained(...)` refuses a remembered placement whose `titleRow().deferredForBudget()` is true; every other
   retention condition is unchanged. This is the only change to the retention contract.
7. The truncated flag is true exactly when the drawn plain text ended in the ellipsis.

### 5.6 Painting

`TitlePainter.paint`:

1. Compute the row box from the row's own numbers. Baseline offset is `(ascent - descent) / 2.0`, matching
   `drawCentered` today.
2. For each `PlainRunRow`: `graphics.setFont(logicalFont.deriveFont(Math.max(1.0f, logicalFont.getSize2D() / zoom)))`,
   `graphics.setColor(colour)`, and `graphics.drawString(text, (float) (anchorX + run.x() / zoom),
   (float) (anchorY + baseline / zoom))`.
3. For each `MathRunRow`:
   1. `Point2D device = graphics.getTransform().transform(new Point2D.Double(anchorX + run.x() / zoom,
      anchorY + baseline / zoom), null)`;
   2. `Point2D component = baseTransform.inverseTransform(device, null)`;
   3. `Graphics2D copy = (Graphics2D) graphics.create(); try { copy.setTransform(baseTransform);
      copy.translate(component.getX(), component.getY()); run.icon().setForeground(colour);
      run.icon().paintIcon(null, copy, 0, 0); } finally { copy.dispose(); }`
4. The base transform is applied exactly once. `baseTransform.inverseTransform` may throw
   `NoninvertibleTransformException`; it is wrapped in `IllegalStateException("base transform must be invertible")`.

### 5.7 Budget, follow-up and the documented capacity limit

1. Before each placement pass, `ScreenLabelPlacement` calls `measurer.beginPass(request.projection().generation())`.
2. Up to `MAX_NEW_MEASUREMENTS_PER_PASS` new measurements are permitted per pass; a cache hit never consumes budget.
3. A pass that defers work reports it: `placement.budgetExhaustedInLastPass()` is true.
4. `ScreenLabelPlacementCache.place` then sets `followUpPending = true`, bumps `passEpoch` so the next identical
   request is a cache miss, and `GraphCanvas.labelsFor` posts one `repaint()`.
5. A follow-up is requested only when the pass deferred work **and** produced at least one new measurement.
   Because the measurement memo is only invalidated by capacity eviction inside a generation, a deferring pass
   reduces the number of unknown titles by at least one, so the chain ends in at most
   `ceil(N / MAX_NEW_MEASUREMENTS_PER_PASS)` further passes for N unknown titles.
6. **Documented capacity limit.** The memo holds `8192` entries. A projection generation with more than 8192
   distinct `(title, font)` pairs exceeds it, and the least-recently-used entries are evicted. Evicted titles are
   measured again when their label is next placed, which costs work but is bounded per pass by the same budget;
   labels whose measurement is unavailable in a given pass are drawn in degraded form. The follow-up chain still
   terminates: it stops as soon as a pass defers nothing, and the number of follow-ups is bounded by the number of
   distinct titles. In that regime a formula label may therefore alternate between typeset and raw text across
   passes; this is accepted because a graph with more than 8192 distinct formula-bearing titles is beyond the
   rendering budget of the view for other reasons as well. The behaviour is specified rather than fixed, and a
   test pins it (T7).

## 6. State transitions and checkable invariants

1. **Plain identity.** For a title with no math, the drawn `drawString` calls and the label geometry equal the
   baseline for the same request. Falsified by T1.
2. **Identity preservation.** `SafeNodeLabel.equals`/`hashCode` ignore `title`; `displayText()`, `fullText()`,
   accessibility, search and enclosure geometry are unchanged. Falsified by T2 and T9.
3. **No omission, no partial formula.** Every math run in the drawn row is either an icon or its complete raw
   source; no icon is partially painted. Falsified by T3.
4. **Finite positive measurement.** Every `MeasuredTitle` satisfies `width > 0`, `height > 0`, all finite; a
   formula taller than `2.0 ×` the plain line height forces the degraded form. Falsified by T4.
5. **Slot cap scope.** Every ladder candidate satisfies `width <= slotMaxWidth(slot)`; a forced `baseSlot` label
   and a retained face after demotion are exempt. Falsified by T5 and by the two existing over-cap tests, which
   must pass unchanged.
6. **Painter purity.** `TitlePainter` never calls `TitleMeasurer.measure` and never calls `FormulaIcons.icon`.
   Falsified by T6, whose measurer and icon service throw on any call.
7. **Bounded progress.** At most `MAX_NEW_MEASUREMENTS_PER_PASS` new measurements per pass; the follow-up chain
   ends with a pass that defers nothing; `budgetExhaustedInLastPass()` is false then. Falsified by T7 and T8.
8. **Deferral is not retention.** A row with `deferredForBudget` is never retained, and the pass after a
   deferral renders the icon it deferred. Falsified by T8.
9. **Bundle gate.** Pinned dependency set, checksums, exact `Bundle-ClassPath`, class major `<= 52`, no
   `Import-Package`/`Export-Package`, notice present. Falsified by T10.

## 7. Test declarations

Every test class in `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/`; JUnit 4 with AssertJ,
matching the module's existing style. Fonts come from the existing package-private pinned-font loader; tests never
depend on host fonts.

| Id | Class | Test method | Fixture | Assertion | Failure discriminator |
| --- | --- | --- | --- | --- | --- |
| T1 | `canvas.GraphCanvasPaintShould` | `plainTitlesDrawTheSameStringsAsTheBaseline` | existing two-node paint fixture | the captured `drawString` strings, x and y for a fixed request are identical to the recorded baseline values captured from the unmodified painter for the same fixture | recording draw calls with a mock `Graphics2D` and comparing to constants: any change in font derivation, centring, or offsets changes the numbers |
| T2 | `adapter.SafeNodeLabelExtractorShould` | `safeNodeLabelEqualityIgnoresTheDerivedTitle` | two labels with equal `displayText` but different `title` | `equals` true, `hashCode` equal, `displayText()` unchanged | constructing the two labels through different factories: if `title` entered equality the assertion fails |
| T3 | `canvas.GraphCanvasPaintShould` | `unrenderableFormulaDrawsItsCompleteRawSource` | title `Theorem: $\bigcup\alpha\leslant\alpha$` | the drawn plain strings contain the exact substring `$\bigcup\alpha\leslant\alpha$` | mutating the degraded path to drop the run, or to substitute a placeholder, fails the substring assertion |
| T3b | `canvas.GraphCanvasPaintShould` | `truncatingRungNeverPaintsAPartialIcon` | a formula wider than the 130 px slot | no `MathRunRow` is painted in that rung and the drawn text ends with the ellipsis | allowing the truncating rung to use the rendered form paints an icon and fails the "no math run" assertion |
| T4 | `canvas.title.TitleMeasurerShould` | `rejectsNonFiniteAndNonPositiveIconMetrics` | a stub `FormulaIcons` returning an icon whose `getIconWidth()` is `0` | `availability()` is `DEGRADED` and all numbers are finite and positive | deleting the guard yields `RENDERED` with `width == 0` |
| T4b | `canvas.title.TitleMeasurerShould` | `rejectsFormulaTallerThanTwiceTheLineHeight` | a stub icon of height `3 ×` the line height | `availability()` is `DEGRADED` | removing the threshold yields `RENDERED` |
| T4c | `canvas.title.TitleMeasurerShould` | `baselineMatchesAnIndependentPixelFixture` | one icon for `x` at 20 px | `ascent` computed from `getBaseLine() * getIconHeight()` equals, within 1 px, the ascent measured by painting the icon into a `BufferedImage` and finding the topmost opaque row | treating `getBaseLine()` as a distance yields an ascent near 0.7 px and fails by an order of magnitude |
| T4d | `canvas.title.TitleMeasurerShould` | `samePixelDifferentFontDoesNotShareAMeasurement` | the same title measured with two font families whose sizes round to the same icon pixels | the two `width` values differ | dropping the font from the memo key returns the first width twice |
| T4e | `canvas.title.TitleMeasurerShould` | `plainTitleMeasuresExactlyAsTheBaseline` | a plain title | the returned numbers equal `font.getStringBounds(text, SCREEN_FRC)` | any arithmetic change fails exact comparison |
| T5 | `canvas.ScreenLabelPlacementShould` | `formulaCandidateRespectsItsSlotLimit` | a formula title wider than 130 px in a `RIGHT` slot | the placed label is `DEGRADED`, its width is `<= 130`, and no `MathRunRow` remains | skipping the width check places a rendered row wider than the limit |
| T6 | `canvas.title.TitlePainterShould` | `painterConsumesTheResolvedRowWithoutMeasuring` | a `TitleRow` plus a measurer and icon service that both throw `AssertionError` on call | painting completes and draws the row | any paint-time measurement or icon lookup throws |
| T6b | `canvas.title.TitlePainterShould` | `nonIdentityBaseTransformPositionsTheIconOnce` | `baseTransform = translate(37, 11) × scale(1.25)`, zoom `1.6`, anchor `(120, -40)` | the icon's painted origin equals the independently computed `B·W·p` within `0.5` px, and a following plain label draws at its world coordinates | a double-applied `B` lands at `B²Wp` and fails by tens of pixels |
| T7 | `canvas.ScreenLabelPlacementCacheShould` | `convergesAcrossFollowUps` | 1000 distinct formula titles, budget 200 | after `ceil(1000/200)` follow-ups every title is `RENDERED` and `takeFollowUpRequest()` is false | removing `passEpoch` from `Key` returns the first degraded list forever |
| T7b | `canvas.ScreenLabelPlacementCacheShould` | `degradesRatherThanRepeatingFollowUpsWhenKeysExceedCapacity` | 9000 distinct formula titles against the 8192 memo | the follow-up chain ends, no further repaint is requested, and the surviving labels are either `RENDERED` or explicitly `DEGRADED` | an unbounded retry loop never clears the follow-up flag and fails the bounded-pass assertion |
| T8 | `canvas.ScreenLabelPlacementCacheShould` | `deferredFollowUpRendersTheIconItDeferred` | one title, budget 0 for the first pass | the first pass is `DEGRADED` with `deferredForBudget()`, the second is `RENDERED` | removing the retention exclusion keeps the degraded face and fails |
| T8b | `canvas.ScreenLabelPlacementShould` | `doesNotRetainABudgetDeferredFace` | a remembered deferred row | `retained` returns null and the pass recomputes | retaining it returns the degraded row |
| T9 | `canvas.ScreenLabelPlacementShould` | `enclosureLabelsKeepTheirPlainGeometry` | an enclosure whose display text begins with `\\latex` | the enclosure row has one `PlainRunRow` and geometry identical to the baseline test's constants | measuring enclosures through the engine changes the width |
| T10 | `build.gradle` (`verifyGraphBundle`) | `graphBundleGateFailsWhenTheFormulaJarIsDropped` | a temporary copy of the dependency assertions evaluated without `jlatexmath-1.0.7.jar` | the name-set assertion throws | removing the new dependency from the pinned set makes the gate pass and the test fails |

Additional declared tests:

- `projection.input.TitleTextParserShould` — `plainTextIsOneRun`, `singleDelimitersSplitIntoThreeRuns`,
  `doubleDollarIsOneMathRun`, `escapedDollarStaysLiteral`, `unmatchedDollarIsLiteralText`,
  `unbalancedDoubleDollarIsLiteralText`, `wholeTextIsMathProducesOneRun`, `textCommandDollarDoesNotCloseEarly`,
  `emptyRunsAreDropped`, `emptySourceYieldsOneEmptyPlainRun`.
- `canvas.title.TitleTruncationShould` — `fittingTitleKeepsRenderedForm`, `overflowingTitleUsesDegradedForm`,
  `degradedRowIsTruncatedWithTheEllipsis`, `plainTitleMatchesTruncateToExactly`.
- `canvas.title.FormulaIconsShould` — `buildsEachKeyOnce`, `cachesFailuresWithoutRebuilding`,
  `evictsLeastRecentlyUsedBeyondCapacity`.
- `canvas.ScreenLabelPlacementCacheShould` — `followUpPassIsACacheMissExactlyOnce`,
  `noFollowUpWhenNothingWasDeferred`.
- `adapter.SafeNodeLabelExtractorShould` — `latexPatternFormatYieldsOneMathRun`,
  `unparsedLatexPatternFormatYieldsOneMathRun`, `latexPrefixYieldsOneMathRun`,
  `displayTextIsUnchangedForEveryExistingFixture`.

## 8. Build and bundle changes

Dependency, added to `dependencies { }` in `freeplane_plugin_graph/build.gradle`:

```groovy
lib('org.scilab.forge:jlatexmath:1.0.7') {
    transitive = false
}
```

Pinned checksum of `jlatexmath-1.0.7.jar`: `6d04c3843fc98d2eea9ba4cff0be7b80540ca75c5a4e0b3463fd1219c48cc65d`
(identical for the installed distribution copy and the Gradle module cache).

`verifyGraphBundle` changes:

```groovy
def graphStreamChecksums = [
    'gs-core-1.3.jar': '2d6a6f92f86c624fcbf468fc7e9cb9c8e3fb7e14c72ad578edb04cc36b0b66cd',
    'pherd-1.0.jar':   '9e74f3702d13756faece5987147c937c09b6837a38ed32199f59c26697b94230',
    'mbox2-1.0.jar':   '3c2db334867211f385a2d62d061818268443f361381f78bbc53f9e897e145983'
]
def bundledChecksums = new LinkedHashMap<String, String>(graphStreamChecksums)
bundledChecksums.put('jlatexmath-1.0.7.jar',
    '6d04c3843fc98d2eea9ba4cff0be7b80540ca75c5a4e0b3463fd1219c48cc65d')

assert dependencyFiles*.name.sort() == bundledChecksums.keySet().toList().sort()
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
```

The existing GraphStream notice assertions stay and keep iterating `graphStreamChecksums`. A new resource file
`src/main/resources/META-INF/NOTICE.jlatexmath.txt` records the jar's provenance and licence structure, copied by
the same mechanism that copies `NOTICE.graphstream.txt`:

- JLaTeXMath 1.0.7, `org.scilab.forge:jlatexmath:1.0.7`, GPL version 2 with the classpath exception summarised
  from the jar's own `META-INF/LICENSE`, plus the archive's bundled font licence families (OFL for the AMS fonts,
  Knuth licence for the Computer Modern and Cyrillic fonts, free licence for `dsrom10`, public domain for
  `stmary10`/`rsfs10`, GPL version 2 for the Greek fonts), each also present under
  `org/scilab/forge/jlatexmath/fonts/licences/` inside the jar.
- The file records the jar's SHA-256 so the notice and the gate cannot drift apart silently.

`graphUiEvidence` keeps its two existing tracked output paths under `docs/superpowers/specs/images/`. New formula
evidence, if the acceptance run produces a third image, is written to a new tracked path beside them and added to
`outputs.files`.

## 9. Errors, compatibility and cleanup

Failure paths:

| Situation | Behaviour |
| --- | --- |
| engine throws on a formula | icon cache records the failure; title is `DEGRADED`; raw source drawn; one attempt per process per key |
| parser sees an unmatched or unbalanced delimiter | whole title is plain text; no engine call |
| formula taller than `2.0 ×` line height | `DEGRADED` |
| icon metrics non-finite or non-positive | `DEGRADED` |
| pass budget exhausted | deferred `DEGRADED`; never retained; one follow-up pass |
| follow-up pass defers nothing | no further repaint |
| base transform not invertible | `IllegalStateException("base transform must be invertible")` from painting; no partial draw |
| title row geometry disagrees with the label box | `IllegalArgumentException("title row geometry must match the label")` at construction |
| measurement cache over 8192 entries in one generation | LRU eviction; affected labels degrade for that pass; follow-up chain still terminates (section 5.7) |

Behaviour that must not change, asserted by tests that must pass unmodified:

- `ScreenLabelPlacementShould` — every existing test, especially `boundsEveryPlacedLabelByItsSlotForms`,
  `assertInk`-based collision tests, and the pinned pixel constants.
- `ScreenLabelLiveTitleRefreshShould` — every existing test, including `keepsAnUnchangedForcedOverCapFace`,
  `keepsAnOverCapFaceAfterDemotionAndOnTheNextReplay` and `keepsAnUnchangedTruncatedFaceWhenSpaceBecomesAvailable`.
- `GraphCanvasPaintShould` — every existing assertion other than the three constructor call sites, which gain the
  row argument only.
- `AccessibleGraphCanvasShould`, `GraphSearchModelShould`, `AdapterArchitectureShould` — unmodified.
- `SafeNodeLabelExtractorShould` — every existing assertion except the new math-signal cases.

No translation resources change. No resource bundle keys are added. No XML format changes. No workspace
persistence changes.

## 10. Verification commands and evidence

Run on a machine with a JDK and Gradle (`AGENTS.md`: Java from `~/.sdkman/candidates/java/21.0.8-zulu`; the
repository CI uses Ubuntu with Java 21):

```text
gradle :freeplane_plugin_graph:test
gradle :freeplane_plugin_graph:verifyGraphBundle
gradle :freeplane_plugin_graph:graphUiEvidence
```

Expected evidence:

1. `test` — all module tests green, including the new `TitleTextParserShould`, `TitleMeasurerShould`,
   `TitleTruncationShould`, `TitlePainterShould`, `FormulaIconsShould` and `ScreenLabelPlacementCacheShould`.
2. `verifyGraphBundle` — passes with the four pinned jars, the extended `Bundle-ClassPath`, and the notice file
   present in the bundle.
3. `graphUiEvidence` — regenerates the two existing images and prints the first-pass measurement count and
   elapsed time for the four reference titles. The count confirms or replaces
   `MAX_NEW_MEASUREMENTS_PER_PASS = 200`; the timing is the only input that can justify a different value.

None of these commands was executed while writing this specification. The environment that authored it has no
JDK and no Gradle, and the network is unreachable, so no test, bundle, or evidence result is claimed anywhere in
this document.

## 11. Implementation decisions recorded where the design is silent

| Decision | Justification |
| --- | --- |
| Parser lives in `projection.input`, not `canvas.title` | the ArchUnit rule keeps `projection..` free of `org.freeplane.features..`, and the parser needs no AWT or engine types |
| All rendering seam types live in a new `canvas.title` package | keeps `canvas..`'s existing public surface unchanged and groups the rendering seam |
| `MathRunRow` holds a strong `TeXIcon` reference | the resolved-row contract forbids paint-time icon lookup, so the row must own its icon |
| Measurement memo is keyed by full font identity, icons by pixel size | widths and thresholds depend on the font; icons do not |
| Enclosure labels always use the plain form | the design leaves enclosure geometry on `displayText()`; a plain row makes that structural |
| Budget exhaustion produces a `DEGRADED` measurement rather than an exception | the label must still draw, and D3 requires raw source rather than a blank |
| Capacity overflow degrades labels instead of retrying forever | section 5.7 states the limit explicitly; an unbounded retry chain would repaint the canvas indefinitely |
| `TitleTextParser` treats `$$` as one formula without display styling | display styling would inflate the label box past the slot limits D4 fixes |
