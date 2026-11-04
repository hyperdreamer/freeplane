# Graph Workspace LaTeX node titles — technical specification

Task identifier: `2026-11-03-graph-latex-titles`

Specification date: 2026-11-03. Status: ready for review (draft 2); implementation and verification have not been
run by this specification authoring task.

Design authority: [2026-11-03-graph-latex-titles-design.md](2026-11-03-graph-latex-titles-design.md), approved
revision r4, design commit `571c7bb3a37603797c1b799827d6d52c75662b78`, blob
`b12838a597df80095a5ba1c1f3ccdebf7b86f597`, SHA-256
`1a3d7af575f41bca9925bc06a18f27adfca48c56c1f87a67bb0791644e19fce8`.

Application baseline: `fd7488d5ae7f92bcdb3f70fc76b09f6e881aacc0` (branch `plugin/graph-workspace`).

Authoring this specification authorised no build, test, commit, branch, worktree, or host transition. The
verification commands in section 10 are instructions for the implementation phase, not evidence that they ran.

Draft history:

- draft 1 (commit `933cb6f19c`) — first specification.
- draft 2 (this revision) — applies specification review round 1. The progress mechanism is the approved
  per-generation queue with an explicit stall outcome (S1); the parser scan is rewritten to produce the design's
  own examples (S2); whole-title degradation no longer synthesises delimiters (S3); the painter keeps the existing
  centred-plain behaviour and aligns formula icons on the shared baseline (S4); the Java recipes are made valid
  (S5); title identity, form lookup and budget accounting are given single authoritative definitions (S6, S7);
  slot-to-font selection is removed in favour of the rung's own font (S8); resolution can no longer change form
  after measurement (S9); the final fallback follows the design's normative order (S10); every declaration is
  complete (S11); numeric and error boundaries are closed (S12); `truncated` keeps its existing meaning (S13);
  every test control is made genuinely falsifiable (S14–S16); the notice gate asserts its own file (S17); and the
  inventory and attribution corrections are applied (S18).

## 1. Result and scope

A node whose title contains LaTeX formulas draws those formulas as typeset notation in the graph workspace instead
of as backslash source text. A formula that the bundled JLaTeXMath engine cannot parse, or that does not fit its
label slot, is drawn as its raw source text exactly as today. Nothing else about the graph changes: the same
labels are placed in the same slots, the same titles are readable by screen readers and search, the same hover bar
and sidebar text appear, and the same hull and layout padding is reserved.

The change is confined to `freeplane_plugin_graph` production source plus the test, evidence, and image files
listed in section 2. It adds one bundled runtime library (JLaTeXMath 1.0.7) and updates that plugin's bundle gate.

Truth table, stated as observable behaviour:

| Title | Slot | Result |
| --- | --- | --- |
| `Theorem: initial segment` | any | unchanged plain label |
| `Theorem: $\bigcup\alpha\leqslant\alpha$` | 200 px vertical | formula typeset inside the sentence |
| `Theorem: $\bigcup\alpha\leslant\alpha$` | any | unchanged raw source text (`\leslant` is unknown to the engine) |
| `Theorem: Equivalence of $\leqsla…` | 130 px horizontal | raw source text, ellipsized by the existing rule |
| a whole-title `\latex …` node | any | one formula run |

## 2. Exact implementation change set

Production paths relative to `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/`. Java 8 source
level, four-space indentation.

| File | Required change |
| --- | --- |
| `projection/input/TitleText.java` | new immutable run model (3.1) |
| `projection/input/TitleTextParser.java` | new total parser (3.2, 5.1) |
| `projection/input/SafeNodeLabel.java` | add derived `title()` and a three-argument factory; existing two-argument factory, equality and accessors unchanged (3.3) |
| `adapter/SafeNodeLabelExtractor.java` | compute the whole-text math signal (5.2) |
| `canvas/title/TitleMetrics.java` | new shared font-render context and unit constants (3.4) |
| `canvas/title/MeasuredTitle.java` | new measurement result (3.5) |
| `canvas/title/MeasuredRun.java` | new per-run measurement (3.6) |
| `canvas/title/FormulaIcons.java` | new icon cache with failure memoization (3.7, 5.4) |
| `canvas/title/TitleMeasurer.java` | new memoizing measurement service, budget and per-generation queue (3.8, 5.3, 5.7) |
| `canvas/title/TitleRow.java` | new resolved row (3.9) |
| `canvas/title/RunRow.java`, `PlainRunRow.java`, `MathRunRow.java` | resolved run types (3.10) |
| `canvas/title/TitleTruncation.java` | form selection and degraded truncation (3.11, 5.5) |
| `canvas/title/TitlePainter.java` | new painter (3.12, 5.6) |
| `canvas/PlacedLabel.java` | add the `titleRow` field and accessor; all existing declarations preserved (3.13) |
| `canvas/ScreenLabelPlacement.java` | thread `TitleText`/`TitleMeasurer`; rendered vs degraded per rung; deferral and stall; budget signal (3.14, 5.3–5.7) |
| `canvas/ScreenLabelPlacementCache.java` | `passEpoch` in `Key`, one-shot follow-up request, measurer and budget injection (3.15, 5.7) |
| `canvas/GraphPainter.java` | capture the base transform and delegate label painting (3.16) |
| `canvas/GraphCanvas.java` | one `repaint()` after an exhausted pass (3.17) |
| `build.gradle` | add the `lib` dependency and the bundle-gate assertions, including the notice check (section 8) |
| `src/main/resources/META-INF/NOTICE.jlatexmath.txt` | new licence provenance file (section 8) |

Test and evidence paths relative to `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/`:

| File | Change |
| --- | --- |
| `projection/input/TitleTextParserShould.java` | new |
| `canvas/title/TitleMeasurerShould.java` | new |
| `canvas/title/TitleTruncationShould.java` | new |
| `canvas/title/TitlePainterShould.java` | new |
| `canvas/title/FormulaIconsShould.java` | new |
| `canvas/ScreenLabelPlacementCacheShould.java` | new |
| `canvas/ScreenLabelPlacementShould.java` | extended |
| `canvas/GraphCanvasPaintShould.java` | extended; two `PlacedLabel` construction sites (`:1919`, `:1976`) gain the row argument |
| `canvas/ScreenLabelLiveTitleRefreshShould.java` | one construction site (`:107`) gains the row argument; deferral case added |
| `adapter/SafeNodeLabelExtractorShould.java` | extended |
| `smoke/GraphWorkspaceUiEvidence.java` | four reference titles, first-pass measurement count and timing |

Tracked image outputs of `graphUiEvidence`, relative to the repository root, are part of the deliverable and are
regenerated rather than authored: `docs/superpowers/specs/images/2026-08-10-graph-workspace-implemented.png` and
`docs/superpowers/specs/images/2026-08-10-graph-group-marker-implemented.png`. No other file changes.

`PlacedLabel` is constructed in six production sites: `enclosureLabel` (`ScreenLabelPlacement.java:341`),
`retained` twice (`:492` and `:519`), the `ladder` candidate (`:564`), `baseSlot` (`:666`) and `hoverOnly` (`:677`).
`ladder` itself begins at `:526`; both `:492` and `:519` are inside `retained`.

## 3. Java declaration contracts

### 3.1 `TitleText`

`public final class org.freeplane.plugin.graph.projection.input.TitleText`

- Immutable. One field: `private final List<Run> runs`, copied defensively into an unmodifiable list.
- Nested `public interface Run`. Nested `public static final class Plain implements Run` with
  `private final String text` and `public String text()`, plus
  `public Plain(String text)` which rejects null with `NullPointerException("text")`.
- Nested `public static final class Math implements Run` with `private final String source`,
  `private final String rawSource`, `public String source()`, `public String rawSource()`, plus
  `public Math(String source, String rawSource)` rejecting nulls with
  `NullPointerException("source")` / `NullPointerException("rawSource")`.
- `public static TitleText plain(String text)` returns one `Plain` run; `null` becomes `IllegalArgumentException("text must not be null")`.
- `public static TitleText of(List<Run> runs)` rejects null with `NullPointerException("runs")`, an empty list with
  `IllegalArgumentException("runs must not be empty")`, and any null element with
  `NullPointerException("run")`.
- `public List<Run> runs()`, `public boolean hasMath()`, `public String plainSource()` (concatenation of
  `Plain.text()` and `Math.rawSource()` in order).
- `equals`/`hashCode` over `runs`; `Plain` and `Math` implement both themselves, so run **kind** participates in
  identity and a plain run can never collide with a formula run of the same text. `toString` renders the runs with
  their kind for diagnostics.

### 3.2 `TitleTextParser`

`public final class org.freeplane.plugin.graph.projection.input.TitleTextParser`

- Private constructor; `public static TitleText parse(String source, boolean wholeTextIsMath)`.
- Total for every non-null input; `null` source is rejected with `NullPointerException("source")`.
- Empty input returns `TitleText.plain("")` in every case, including when `wholeTextIsMath` is true. The
  precedence is: empty input first, then whole-text math, then the delimiter scan.
- The scan is section 5.1.

### 3.3 `SafeNodeLabel`

`public final class org.freeplane.plugin.graph.projection.input.SafeNodeLabel`

- Adds `private final TitleText title` and `public TitleText title()`.
- `public static SafeNodeLabel of(String full, String display)` keeps its signature and delegates to
  `of(full, display, TitleText.plain(display))`.
- `public static SafeNodeLabel of(String full, String display, TitleText title)` is public, rejects nulls with
  `NullPointerException("full")` / `("display")` / `("title")`, and enforces the existing non-empty text rules.
- **`equals` and `hashCode` are unchanged**: they compare `fullText` and `displayText` only. The derived title is
  deliberately excluded so projection identity, deduplication, and the existing extractor tests keep their
  meaning. This is documented and asserted by T2.

### 3.4 `TitleMetrics`

`public final class org.freeplane.plugin.graph.canvas.title.TitleMetrics`

- `public static final FontRenderContext SCREEN_FRC = new FontRenderContext(null, true, true);` — the same flags
  `ScreenLabelPlacement.SCREEN_FRC` uses today, declared here because that constant is package-private in `canvas`
  and this package is a sibling.
- `public static final int STYLE = org.scilab.forge.jlatexmath.TeXConstants.STYLE_TEXT;`
- `public static final double MAX_HEIGHT_FACTOR = 2.0;`
- `public static int px(Font font)` returns `Math.max(1, Math.round(font.getSize2D()))`.
- `public static double lineHeight(Font font)` returns `font.getLineMetrics("Hg", SCREEN_FRC).getHeight()`.
- Private constructor.

T4e asserts the equality of measurements taken through this context and through
`ScreenLabelPlacement.screenBounds`, so the two contexts cannot silently diverge.

### 3.5 `MeasuredTitle`

`public final class org.freeplane.plugin.graph.canvas.title.MeasuredTitle`

- `public enum Availability { RENDERED, DEGRADED }`.
- Fields in order: `Availability availability`, `List<MeasuredRun> runs`, `double width`, `double height`,
  `double ascent`, `double descent`, `boolean deferredForBudget`.
- `public static MeasuredTitle rendered(List<MeasuredRun> runs, double width, double height, double ascent, double descent)`.
- `public static MeasuredTitle degraded(List<MeasuredRun> runs, double width, double height, double ascent, double descent)`.
- `public MeasuredTitle deferred()` returns a copy with `deferredForBudget = true`.
- Accessors for every field.
- Validation: `runs` is copied defensively with no null elements; every double is finite; `width >= 0.0`;
  `height > 0.0`; `ascent` and `descent` are finite and `>= 0.0`. Violations throw
  `IllegalArgumentException("measurement must be finite and non-negative")` with the offending field named.
  `width == 0.0` is legal and occurs only for an empty plain title (S12).

### 3.6 `MeasuredRun`

`public final class org.freeplane.plugin.graph.canvas.title.MeasuredRun`

- `public enum Kind { PLAIN, MATH }`.
- Fields: `Kind kind`, `String source`, `String rawSource`, `double x`, `double width`, `double ascent`,
  `double descent`, all private final, with accessors of the same names.
- `public static MeasuredRun plain(String text, double x, double width, double ascent, double descent)`.
- `public static MeasuredRun math(String source, String rawSource, double x, double width, double ascent, double descent)`.
- Validation: non-null strings, finite numbers, `x >= 0.0`, `width >= 0.0`, `ascent >= 0.0`, `descent >= 0.0`,
  otherwise `IllegalArgumentException("measured run must be finite and non-negative")` naming the field.
- Offsets and extents are screen pixels at the logical label font size.

### 3.7 `FormulaIcons`

`public final class org.freeplane.plugin.graph.canvas.title.FormulaIcons`

- `public static final int CAPACITY = 512;`
- Constructor `public FormulaIcons()` uses `CAPACITY`; `public FormulaIcons(int capacity)` exists for tests and
  rejects `capacity < 1` with `IllegalArgumentException("capacity must be positive")`.
- Fields: `LinkedHashMap<Key, TeXIcon> icons` (access-ordered, `removeEldestEntry` over capacity),
  `Set<Key> failures`, `int builds`, `int hits`.
- `public TeXIcon icon(String source, int px, int style)` returns the cached icon, builds on a miss, and returns
  `null` when the engine rejects the source. A rejected key is recorded in `failures` and never rebuilt **for the
  lifetime of this instance**. A default instance is process-lifetime because `ScreenLabelPlacement`'s
  no-argument constructor creates exactly one and every `GraphCanvas` reuses one cache; tests construct their own
  (S7).
- `public int buildCount()`, `public int hitCount()`, `public int failureCount()`.
- The build call is source-bound:

```java
final TeXFormula formula = new TeXFormula(source);
final TeXIcon icon = formula.new TeXIconBuilder()
    .setStyle(style)
    .setSize(px)
    .build();
```

- Any `RuntimeException` from that block records a failure and returns `null`; an `Error` propagates.

### 3.8 `TitleMeasurer`

`public final class org.freeplane.plugin.graph.canvas.title.TitleMeasurer`

- `public static final int DEFAULT_MAX_NEW_MEASUREMENTS_PER_PASS = 200;`
- `public static final int CAPACITY = 8192;`
- `public TitleMeasurer()` uses a new `FormulaIcons()` and the default budget.
- `public TitleMeasurer(FormulaIcons icons, int maxNewMeasurementsPerPass, int capacity)` is the injectable form
  used by tests; it rejects null, a non-positive budget, and a capacity below 1.
- Fields: `FormulaIcons icons`, `int budget`, `LinkedHashMap<Key, MeasuredTitle> cache` (access-ordered LRU at
  capacity), `LinkedHashSet<Key> pending` (insertion-ordered queue), `int newMeasurementsInPass`, `long generation`,
  `boolean stalled`.
- `public void beginPass(long projectionGeneration)`:
  - when `projectionGeneration != generation`, clears `cache`, `pending`, `stalled`, and sets `generation`;
  - always resets `newMeasurementsInPass` to 0.
- `public MeasuredTitle measure(TitleText title, Font font)` returns the memoized rendered measurement, or the
  memoized degraded measurement when the rendered form is unavailable.
- `public MeasuredTitle measureDegraded(TitleText title, Font font)` returns the memoized degraded measurement,
  computing it if necessary, and never returns `RENDERED`.
- `public boolean isKnown(TitleText title, Font font)` is true when a rendered **or** degraded entry exists.
- `public boolean isPending(TitleText title, Font font)` reports queue membership.
- `public MeasuredTitle defer(TitleText title, Font font)` returns the degraded measurement marked
  `deferredForBudget`, adds the key to `pending`, and does not consult the engine.
- `public boolean budgetExhaustedInLastPass()`, `public boolean stalled()`, `public int pendingCount()`,
  `public int cacheSize()`, `public int newMeasurementsInLastPass()`.
- `public String stallDiagnostic()` returns `FORMULA_MEASUREMENT_STALL: <n> formula titles unmeasured after <p> passes`.
- `Key` is a private static final class over `TitleText`, `Font`, and `Form` where `Form` is
  `RENDERED` or `DEGRADED`. Because `TitleText.equals` compares run kinds (3.1), the key cannot conflate a plain
  title with a formula title that shares its raw characters. The font participates through its full identity
  (`getFamily()`, `getStyle()`, `getSize2D()`), not through `px` (S6).

### 3.9 `TitleRow`

`public final class org.freeplane.plugin.graph.canvas.title.TitleRow`

- `public enum Form { RENDERED, DEGRADED }`.
- Fields: `Form form`, `List<RunRow> runs`, `double width`, `double height`, `double ascent`, `double descent`,
  `boolean deferredForBudget`; all private final with accessors.
- `public static TitleRow resolve(MeasuredTitle measured, FormulaIcons icons, int px, int style)`:
  - for a `PLAIN` `MeasuredRun` produces a `PlainRunRow`;
  - for a `MATH` `MeasuredRun` obtains `icons.icon(source, px, style)` and produces a `MathRunRow`;
  - **never changes the form.** If a `RENDERED` measurement's icon is unexpectedly `null`, resolution throws
    `IllegalStateException("rendered measurement lost its icon")` rather than inventing a differently measured
    degraded row (S9). A `DEGRADED` measurement has no `MATH` runs, so it resolves without any icon lookup.
  - validation mirrors `MeasuredTitle`.
- Because `FormulaIcons` rebuilds an evicted key on demand, an eviction between measurement and resolution cannot
  produce that exception; only a nondeterministic engine could, and that is a defect worth failing loudly.

### 3.10 `RunRow`, `PlainRunRow`, `MathRunRow`

- `public interface RunRow` declares `double x()` and `double width()`.
- `public final class PlainRunRow implements RunRow` with `public PlainRunRow(String text, double x, double width,
  double ascent, double descent)` and accessors `text()`, `x()`, `width()`, `ascent()`, `descent()`; null text
  throws `NullPointerException("text")`, non-finite numbers throw
  `IllegalArgumentException("run geometry must be finite")`.
- `public final class MathRunRow implements RunRow` with `public MathRunRow(String source, TeXIcon icon, double x,
  double width, double ascent, double descent)` and accessors `source()`, `icon()`, `x()`, `width()`, `ascent()`,
  `descent()`; null source or icon throws `NullPointerException`, non-finite numbers as above.

### 3.11 `TitleTruncation`

`public final class org.freeplane.plugin.graph.canvas.title.TitleTruncation`

- `public static String degradedPlainText(TitleText title)` returns the concatenation of `Plain.text()` and
  `Math.rawSource()` in run order. That is exactly the text the degraded form displays, so degradation adds no
  characters the user did not type.
- `public static String truncated(String text, Font font, double limit, ScreenLabelPlacement placementHolder)`
  is **not** introduced; degradation reuses `ScreenLabelPlacement.truncateTo`, which stays the single truncator.
- The method rejects null with `NullPointerException("title")` and never throws for a well-formed title.
- The degraded *measurement* is not built here: section 5.3 step 7 measures the degraded string directly, so this
  class holds only the string transform and cannot disagree with the measurement.

### 3.12 `TitlePainter`

`public final class org.freeplane.plugin.graph.canvas.title.TitlePainter`

- `public static void paint(Graphics2D graphics, TitleRow row, Font logicalFont, double anchorX, double anchorY,
  double zoom, Color colour, AffineTransform baseTransform)`.
- `anchorX`/`anchorY` are the label's world anchor (its centre), `logicalFont` the label face at logical size,
  `zoom` the viewport zoom, `baseTransform` the transform the component supplied before the world transform.
- Validation, all before any drawing: `graphics`, `row`, `logicalFont`, `colour`, `baseTransform` non-null
  (`NullPointerException` naming the parameter); `zoom` finite and `> 0.0`; `anchorX`/`anchorY` finite; and
  `baseTransform.isIdentity() || baseTransform.getDeterminant() != 0.0`, otherwise
  `IllegalArgumentException("base transform must be invertible")`. Because validation precedes drawing, the
  no-partial-draw promise holds (S12).
- Stateless; no field of the class refers to `TitleMeasurer`, `FormulaIcons`, or `MeasuredTitle`, which T6 asserts
  structurally.

### 3.13 `PlacedLabel`

- Adds `private final TitleRow titleRow` and a package-private `TitleRow titleRow()`.
- The single package-private constructor gains `TitleRow titleRow` as its last parameter (after `slot`), rejected
  with `NullPointerException("titleRow")`.
- Every existing accessor, field and validation rule keeps its current meaning. `truncated()` keeps its existing
  definition — the drawn text differs from the source text — and is **not** redefined as "ends with an ellipsis"
  (S13).
- The constructor asserts that `titleRow` geometry matches `width`/`height` within `1e-6`, and that
  `titleRow.deferredForBudget()` is consistent with a remembered placement; a mismatch throws
  `IllegalArgumentException("title row geometry must match the label")`.

### 3.14 `ScreenLabelPlacement`

- Retains its no-argument constructor, which builds one `TitleMeasurer` with a process-default `FormulaIcons`.
- Adds a package-private constructor `ScreenLabelPlacement(TitleMeasurer measurer)` for tests.
- Adds package-private `TitleMeasurer measurer()`, `boolean budgetExhaustedInLastPass()`, `boolean
  measurementStalled()`, and `String measurementDiagnostic()` for the cache and for tests.
- `place(...)` calls `measurer.beginPass(request.projection().generation())` before the first label.
- `screenBounds(String, Font)` and `textWidth(String, Font)` keep their visibility and behaviour, and
  `truncateTo(String, Font, double)` remains the only plain-text truncator.
- The slot-to-font helper of draft 1 is **deleted**: face selection stays in `LadderRung.font(LabelFonts)` and each
  measuring call site already holds the font it is measuring with (S8).
- `retained(...)` gains the deferral exclusion of section 5.5 step 6.

### 3.15 `ScreenLabelPlacementCache`

- `Key` gains `private final long passEpoch` and includes it in `equals`/`hashCode`.
- Retains its no-argument constructor and adds package-private `ScreenLabelPlacementCache(ScreenLabelPlacement
  placement, LabelFonts fonts)` so tests can inject their own placement and measurer (S14).
- `public List<PlacedLabel> place(LabelPlacementRequest request, LabelFonts fonts)` behaves as today for a key
  match, except that when the last computation exhausted the measurement budget it bumps `passEpoch` once, making
  the next call for otherwise identical state a miss.
- `public boolean takeFollowUpRequest()` returns `true` at most once per exhaustion and clears the flag.
- `public boolean budgetExhaustedInLastPass()`, `public boolean measurementStalled()` delegate to the placement.

### 3.16 `GraphPainter`

- `paint(...)` captures `final AffineTransform baseTransform = graphics.getTransform();` as its first statement.
- `paintLabels(...)` keeps deriving the world font exactly as today, including
  `Math.max(1.0f, (float) (label.font().getSize2D() / viewport.zoom()))` (`GraphPainter.java:281-282`), and calls
  `TitlePainter.paint(graphics, label.titleRow(), label.font(), anchorX, anchorY, viewport.zoom(),
  theme.labelColor(), baseTransform)`.
- `drawCentered(Graphics2D, Font, String, double, double)` is deleted once no caller remains.
- Colours, the dimming composite, leader lines and ordering are unchanged.

### 3.17 `GraphCanvas`

- `labelsFor(...)` posts one `repaint()` when `labelPlacementCache.takeFollowUpRequest()` is true, and nothing
  otherwise, after `place(...)` returns.

## 4. Construction-site rules

| Site | Row supplied |
| --- | --- |
| `enclosureLabel` (`:341`) | one `PlainRunRow` over the enclosure `displayText()` |
| `retained` remembered row (`:492`, `:519`) | the row form remembered for that rung, recomputed from the current measurement |
| `ladder` candidate (`:564`) | rendered form for non-truncating rungs, degraded form for truncating rungs |
| `baseSlot` (`:666`) | degraded form (design section 4.4 rule 2: the degraded form is the final fallback) |
| `hoverOnly` (`:677`) | degraded form, same rule |

Draft 1 chose the rendered form for `baseSlot` and `hoverOnly`. That was a specification decision contradicting the
design's normative fallback order, and it is corrected here (S10). A forced label whose rendered form does not fit
therefore falls back to its raw source, which is what D3 and D4 together require.

Enclosure labels are never formula-parsed: `ProjectedEnclosure.labels()` carries `SafeNodeLabel` values built by
`ProjectionEngine.HullBuilder`, and `GeometryTextMetrics` keeps measuring `displayText()`. Their rows are always a
single `PlainRunRow` and their geometry must be byte-identical to the baseline; T9 asserts this with a
math-bearing derived title whose display text is plain.

## 5. Algorithms

### 5.1 Parser scan

Input `source`, `wholeTextIsMath`.

1. If `source.isEmpty()`, return `TitleText.plain("")`.
2. If `wholeTextIsMath` is true, return one `Math` run whose `source` is the input verbatim **without synthesising
   delimiters**, and whose `rawSource` is the input verbatim. The caller has already stripped any `\latex` prefix,
   so the raw fallback is exactly what remains of what the user typed (S3). If the input already begins and ends
   with a single `$`, the delimiters are removed from `source` and retained in `rawSource`.
3. Otherwise scan left to right over `source` with index `i`, keeping a `StringBuilder plain` and a
   `List<Run> runs`, and a `boolean sawUnmatchedDelimiter`.
4. `\\` followed by any character: append **only the escaped character** to `plain` and advance two. `\$` therefore
   contributes a literal `$` and never opens a formula (design section 4.2 example). A trailing lone `\\` appends
   `\\` and ends the scan.
5. `$$` at `i`: find the next `$$`. If found, flush `plain`, emit one `Math` run over the body between the
   delimiters with `rawSource` including both `$$`, and continue after them. If not found, set
   `sawUnmatchedDelimiter` and stop the scan.
6. `$` at `i`: find the closing `$` per step 7. If found, flush `plain`, emit one `Math` run over the body with
   `rawSource` including both `$`, and continue after the closer. If not found, set `sawUnmatchedDelimiter` and stop.
7. A closer is the next `$` that is not escaped and occurs at brace depth `0`; `{` increments and `}` decrements a
   depth counter that never falls below zero. This is what stops `\text{$x$}$` from closing inside the `\text`.
8. If `sawUnmatchedDelimiter` is true, the **whole title** is returned as `TitleText.plain(source)`: no partial
   formula survives, and a later valid formula cannot leak through (S2, and the design's "unmatched delimiter
   yields one literal `Plain`" rule). This rollback rule is total and order-independent.
9. Otherwise flush any remaining `plain`, drop empty runs, and if nothing remains return `TitleText.plain(source)`.

Worked examples, which the parser tests assert verbatim:

| Input | Runs |
| --- | --- |
| `costs \$5 and $\alpha$` | `Plain("costs $5 and ")`, `Math("\\alpha", "$\\alpha$")` |
| `$$a$$` | `Math("a", "$$a$$")` |
| `text $a$ text` | `Plain("text ")`, `Math("a", "$a$")`, `Plain(" text")` |
| `$\text{$x$}$` | `Math("\\text{$x$}", "$\\text{$x$}$")` |
| `unmatched $a` | `Plain("unmatched $a")` |
| `$a$ and $b` | `Plain("$a$ and $b")` |
| `$a$ and $b$` | `Math("a", "$a$"), `Plain(" and ")`, `Math("b", "$b$")` |

### 5.2 Whole-text math signal

`SafeNodeLabelExtractor` passes `wholeTextIsMath = true` when either condition holds:

1. `NodeStyleModel.getNodeFormat(node)` is `latexPatternFormat` or `unparsedLatexPatternFormat`; or
2. the node's raw content, before whitespace normalization, starts with `\latex` or `\unparsedlatex` followed by
   whitespace.

The flag is computed in the adapter, where the format and the raw prefix are available. `markdownPatternFormat` does
not set it; dollar delimiters inside markdown source still produce math through the ordinary scan. The existing
prefix stripping runs before parsing, so the parser's input is the stripped source.

### 5.3 Measurement

`TitleMeasurer.measure(title, font)`:

1. Look up `Key(title, font, RENDERED)`. On a hit return the memoized value.
2. If the title has no math run, measure as today: `font.getStringBounds(title.plainSource(), TitleMetrics.SCREEN_FRC)`,
   with `ascent = -bounds.getY()`, `descent = bounds.getHeight() - ascent`, `width = bounds.getWidth()`,
   one `MeasuredRun.plain` at `x = 0`. **Plain titles never consume budget** and are always measured (S7); this
   keeps every plain label's rung behaviour identical to the baseline.
3. Otherwise, before building anything, verify the title is not deferred: if `consumeBudgetSlot()` returns false and
   the rendered key is unknown, return `defer(title, font)` and stop.
4. Consume one budget slot, then for each run in order: measure plain runs with
   `font.getStringBounds(text, TitleMetrics.SCREEN_FRC)`; for formula runs obtain
   `icons.icon(source, TitleMetrics.px(font), TitleMetrics.STYLE)`.
5. If any icon is `null`, or any icon dimension is non-finite or not strictly positive, or any icon height exceeds
   `TitleMetrics.MAX_HEIGHT_FACTOR * TitleMetrics.lineHeight(font)`, memoize and return
   `measureDegraded(title, font)`.
6. Compose the row: `x` accumulates previous widths; `ascent` is the maximum of the plain ascent and every formula
   `ascent = icon.getBaseLine() * icon.getIconHeight()`; `descent` is the maximum of the plain descent and every
   formula `descent = icon.getIconHeight() - ascent`; `height = ascent + descent`; `width` is the sum.
   `getBaseLine()` is a ratio and is never used as a distance. A non-finite derived `ascent` or `descent` is
   treated as an invalid metric and routed to step 5.
7. Memoize under `(title, font, RENDERED)`, increment `newMeasurementsInPass`, remove the key from `pending`, and
   return the result.

`TitleMeasurer.measureDegraded(title, font)`:

1. Look up `(title, font, DEGRADED)`. On a hit return it.
2. Consume one budget slot; if none is available, return `defer(title, font)`.
3. Build the degraded text with `TitleTruncation.degradedPlainText(title)`, measure it with
   `font.getStringBounds(text, TitleMetrics.SCREEN_FRC)`, produce one `MeasuredRun.plain`, and memoize under
   `(title, font, DEGRADED)`.

A failed (unrenderable) rendered measurement is recorded under its rendered key as a `DEGRADED` result, so the
engine is consulted at most once per key per cache lifetime; that recording counts against the budget because it
was a new computation.

### 5.4 Icon cache behaviour

1. A cached icon returns on a hit and increments `hitCount`.
2. A recorded failure returns `null` without incrementing `buildCount`.
3. A miss builds with section 3.7's source-bound call, increments `buildCount`, inserts, and evicts the
   least-recently-used entry over capacity.
4. A `RuntimeException` records a failure, increments `failureCount`, and returns `null`.

### 5.5 Form selection, truncation and retention

For a placed node title, in this order:

1. `TitleText title = node.label().title()`.
2. Non-truncating rung: `MeasuredTitle rendered = measurer.measure(title, font)`; accept the candidate when
   `rendered.availability() == RENDERED` and `rendered.width() <= slotMaxWidth(slot)`.
3. Truncating rung: `MeasuredTitle degraded = measurer.measureDegraded(title, font)`; if its width exceeds
   `slotMaxWidth(slot)`, apply the existing `truncateTo` to its plain text and resolve a `PlainRunRow` from the
   truncated string.
4. If `measure` returns a `DEGRADED` result (unrenderable or deferred), only the degraded form is offered, through
   truncating rungs and the final fallback.
5. `baseSlot` and `hoverOnly` use the degraded form (section 4).
6. `retained(...)` refuses a remembered placement whose `titleRow().deferredForBudget()` is true, and refuses one
   whose remembered rung form differs from the form the current measurement would produce. Every other retention
   condition is unchanged. This is the only change to the retention contract.
7. `truncated` keeps its existing definition: the drawn text differs from the source (S13).

### 5.6 Painting

`TitlePainter.paint`:

1. Validate per section 3.12, before any drawing.
2. When the row contains no `MathRunRow`, paint it exactly as `drawCentered` does today: derive
   `Font worldFont = logicalFont.deriveFont(Math.max(1.0f, (float) (logicalFont.getSize2D() / zoom)))`, take
   `Rectangle2D bounds = worldFont.getStringBounds(text, TitleMetrics.SCREEN_FRC)` and
   `LineMetrics line = worldFont.getLineMetrics(text, TitleMetrics.SCREEN_FRC)`, and draw with
   `graphics.drawString(text, (float) (anchorX - bounds.getWidth() * 0.5),
   (float) (anchorY + (line.getAscent() - line.getDescent()) * 0.5f))`. A plain-only row is therefore pixel-identical
   to the baseline, including at the font clamp (S4).
3. Otherwise the row contains math and the row's own numbers govern. The row is centred on the anchor:
   `double left = anchorX - (row.width() / (2.0 * zoom));` and the baseline is
   `double baseline = anchorY + ((row.ascent() - row.descent()) / (2.0 * zoom));`.
4. For each `PlainRunRow`: set the world font from step 2, then
   `graphics.drawString(text, (float) (left + run.x() / zoom), (float) baseline)`.
5. For each `MathRunRow`:
   1. `double runLeft = left + run.x() / zoom;`
   2. `Point2D device = graphics.getTransform().transform(new Point2D.Double(runLeft, baseline), null);`
   3. `Point2D component = baseTransform.inverseTransform(device, null);`
   4. `Graphics2D copy = (Graphics2D) graphics.create(); try { copy.setTransform(baseTransform);
      copy.translate(component.getX(), component.getY() - run.ascent() / zoom);
      run.icon().setForeground(colour); run.icon().paintIcon(null, copy, 0, 0); } finally { copy.dispose(); }`
   The `- run.ascent() / zoom` offset places the icon's top edge above the shared baseline by its measured ascent,
   so text and formula baselines coincide. The base transform is applied exactly once.
6. `NoninvertibleTransformException` from step 5.3 is impossible after validation, but if it occurs it is wrapped in
   `IllegalStateException("base transform must be invertible")`.

### 5.7 Budget, queue, follow-up and stall

This is the approved mechanism (design section 4.6), implemented rather than substituted.

1. Each placement pass begins with `beginPass(request.projection().generation())`.
2. At most `MAX_NEW_MEASUREMENTS_PER_PASS` new measurements (rendered or degraded) are computed per pass. Cache
   hits, plain titles, and deferrals consume no budget.
3. When the budget is exhausted, an unknown formula title is deferred: `defer(...)` records it in `pending`, stores
   the deferred degraded measurement under its degraded key, and returns it. Deferral never asks the engine.
4. A pass reports exhaustion when it deferred at least one title. `ScreenLabelPlacementCache.place` then sets
   `followUpPending`, bumps `passEpoch` so the next identical request is a miss, and `GraphCanvas.labelsFor` posts
   one `repaint()`.
5. **Queue priority.** A pass first serves keys already in `pending` in insertion order, then keys encountered for
   the first time. Serving a pending key either measures it (removing it from `pending`) or defers it again when
   the budget is still exhausted.
6. **Stall.** If a pass ends with `pending` non-empty and removed nothing from it, the measurer sets `stalled`,
   stops requesting follow-ups, and reports `FORMULA_MEASUREMENT_STALL: <n> formula titles unmeasured after <p>
   passes`. Those titles keep their deferred degraded form until the projection generation changes, which clears
   `pending`, `stalled`, and the cache. There is no unbounded repaint chain: a pass either drains at least one
   pending key or stalls permanently for that generation.
7. **Capacity overflow.** Above `CAPACITY = 8192` distinct `(title, font)` keys in one generation, evicted keys
   become unknown again and can re-enter `pending`. The stall rule in step 6 still terminates the chain, because a
   pass that makes no drain stops it. The observable consequence is specified and accepted: in a projection with
   more than 8192 distinct formula-bearing titles, later labels may keep their raw-source form for the lifetime of
   that generation. T7b pins it. This is not an amendment: it is the design's own `FORMULA_MEASUREMENT_STALL`
   outcome applied to the capacity boundary.

## 6. State transitions and checkable invariants

1. **Plain identity.** For a title with no math, the drawn `drawString` calls and the label geometry equal the
   baseline for the same request, at zoom 1 and at the zoom-24 clamp. Falsified by T1.
2. **Identity preservation.** `SafeNodeLabel.equals`/`hashCode` ignore `title`; `displayText()`, `fullText()`,
   accessibility, search and enclosure geometry are unchanged, including for an enclosure whose derived title is
   math-bearing. Falsified by T2 and T9.
3. **No omission, no partial formula.** Every math run in a drawn row is an icon placed on the shared baseline, and
   every degraded formula contributes its complete raw source. Falsified by T3, T3b and T3c.
4. **Finite measurement.** Every `MeasuredTitle` has finite numbers, `height > 0`, `width >= 0`, `ascent`,
   `descent >= 0`; an over-height, zero-width or non-finite-baseline icon forces the degraded form. Falsified by
   T4, T4b, T4f and T4g.
5. **Slot cap scope.** Every ladder candidate satisfies `width <= slotMaxWidth(slot)` for vertical, horizontal and
   diagonal slots and for both faces; a forced `baseSlot` label and a retained face after demotion are exempt.
   Falsified by T5 and by the two existing over-cap tests, which must pass unchanged.
6. **Painter purity.** `TitlePainter` and its parameters reference neither `TitleMeasurer` nor `FormulaIcons`, and
   painting never measures or builds an icon. Falsified by T6.
7. **Bounded progress.** At most the configured budget of new measurements per pass; each deferring pass either
   drains a pending key or sets the stall state; `takeFollowUpRequest()` is false once nothing is deferred or the
   stall is set. Falsified by T7, T7b and T7c.
8. **Deferral is not retention.** A row with `deferredForBudget` is never retained, and the pass after a
   positive-progress deferral renders the icon it deferred. Falsified by T8 and T8b.
9. **Bundle gate and notice.** Pinned dependency set and checksums, exact `Bundle-ClassPath`, class major `<= 52`,
   no `Import-Package`/`Export-Package`, present `META-INF/NOTICE.jlatexmath.txt` whose content carries the jar's
   checksum and licence provenance, and the `jlatexmath` entry in `META-INF/COPYING` form. Falsified by T10.

## 7. Test declarations

JUnit 4 with AssertJ, in the module's existing style. Test classes may live in `canvas.title` and therefore cannot
use the package-private `ScreenLabelPlacementShould.fonts()`; each new class declares its own pinned fixture fonts
through `new Font("Noto Sans", Font.PLAIN, 12)` and `new Font("Noto Sans", Font.BOLD, 12)`, matching the faces the
existing loader builds, plus the two extra faces T4d needs (`Font.SERIF`, `Font.MONOSPACED`, both 12 pt).
`TitleMeasurer` and `FormulaIcons` are rendered deterministic by injection: every measurement test constructs its
own `FormulaIcons`, so no state leaks between tests.

| Id | Class | Method | Fixture | Assertion | Failure discriminator |
| --- | --- | --- | --- | --- | --- |
| T1 | `canvas.GraphCanvasPaintShould` | `plainTitlesDrawTheSameStringsAsTheBaseline` | existing two-node fixture, zoom 1 | recorded `drawString` strings, x and y equal the frozen baseline constants | any change to font derivation, centring or offsets moves the numbers |
| T1b | `canvas.title.TitlePainterShould` | `plainRowAtZoomAboveTheClampUsesTheClampedFont` | 12 pt font, zoom 24, one `PlainRunRow` | the drawn font size is exactly `1.0f` and the baseline equals the `LineMetrics` of that clamped font | dividing logical metrics by zoom instead of using the clamped world font yields 0.5 pt and fails |
| T2 | `adapter.SafeNodeLabelExtractorShould` | `safeNodeLabelEqualityIgnoresTheDerivedTitle` | equal `displayText`, different `TitleText` | `equals` true, `hashCode` equal | putting `title` into equality fails it |
| T3 | `canvas.GraphCanvasPaintShould` | `unrenderableFormulaDrawsItsCompleteRawSource` | `Theorem: $\bigcup\alpha\leslant\alpha$`, roomy slot | exactly one row, `DEGRADED`, drawn text contains the exact raw substring, no `MathRunRow` | dropping the run or substituting a placeholder fails the substring |
| T3b | `canvas.GraphCanvasPaintShould` | `truncatingRungNeverPaintsAPartialIcon` | formula wider than the 130 px slot | the placed row is `DEGRADED`, drawn text ends with the ellipsis, no `MathRunRow` | letting a truncating rung use the rendered form paints an icon |
| T3c | `canvas.TitleMeasurerShould` | `multipleFormulasDegradeWholesale` | `a $\leslant$ b $\alpha$` | `DEGRADED`, one plain run, both raw sources present, no partial icon | per-run degradation leaves one `MathRunRow` and fails |
| T3d | `canvas.title.TitlePainterShould` | `formulaBaselineMatchesTheTextBaseline` | mixed row `Theorem: $x$` at zoom 1 | the icon's painted top edge equals `baseline - ascent` within 0.5 px, computed independently from the row numbers | painting the icon top at the baseline instead of above it fails by one ascent |
| T4 | `canvas.title.TitleMeasurerShould` | `rejectsNonFiniteAndNonPositiveIconMetrics` | stub icon with `getIconWidth() == 0` | `DEGRADED`, no exception thrown | removing the guard throws from construction or returns `RENDERED` |
| T4b | `canvas.title.TitleMeasurerShould` | `rejectsFormulaTallerThanTwiceTheLineHeight` | stub icon height `3 ×` line height | `DEGRADED` | removing the threshold yields `RENDERED` |
| T4c | `canvas.title.TitleMeasurerShould` | `ascentMatchesAnIndependentBaselineOracle` | one icon for `\mathrm{x}` at 12 px, and a plain `x` drawn with the same font | paint both into separate images on a chosen baseline; `ascent` equals the icon ink-top offset from that baseline within 1 px, and the two ink tops differ by less than 1 px | treating `getBaseLine()` as a distance gives ~0.7 px instead of ~9 px, failing by an order of magnitude |
| T4d | `canvas.title.TitleMeasurerShould` | `samePixelDifferentFontDoesNotShareAMeasurement` | `a $\alpha$` measured with `Font.SERIF` 12 and `Font.MONOSPACED` 12 | the two `width` values differ by more than 1 px, and `cacheSize()` is 2 | dropping the font from the key returns the first width twice and `cacheSize()` is 1 |
| T4e | `canvas.title.TitleMeasurerShould` | `plainTitleMeasuresExactlyAsThePlacementBaseline` | a plain title | numbers equal `ScreenLabelPlacement.screenBounds(text, font)` exactly | a different `FontRenderContext` or arithmetic difference fails equality |
| T4f | `canvas.title.TitleMeasurerShould` | `emptyTitleIsMeasuredWithoutThrowing` | `TitleText.plain("")` | `width == 0.0`, `height > 0.0`, `RENDERED` | a positive-width precondition throws |
| T4g | `canvas.title.TitleMeasurerShould` | `rejectsNonFiniteBaselineRatio` | stub icon whose `getBaseLine()` is `Float.NaN` | `DEGRADED` | an unvalidated ratio propagates `NaN` into the box |
| T5 | `canvas.ScreenLabelPlacementShould` | `formulaCandidateRespectsEverySlotCap` | formula titles sized just over the vertical (200), horizontal (130) and diagonal (150) caps, with both faces | each placed row is `DEGRADED`, `width <= slotMaxWidth(slot)`, no `MathRunRow` | skipping the check admits an oversized rendered candidate in at least one combination |
| T6 | `canvas.title.TitlePainterShould` | `painterHasNoMeasurementDependency` | reflection over `TitlePainter`'s declared fields and `paint` parameter types, plus a paint of a rendered row with a counting `FormulaIcons` | no field or parameter type is `TitleMeasurer`/`FormulaIcons`/`MeasuredTitle`; `FormulaIcons.buildCount()` is unchanged by painting | adding a paint-time lookup makes the reflection assertion or the build count fail |
| T6b | `canvas.title.TitlePainterShould` | `nonIdentityBaseTransformPositionsTheIconOnce` | `baseTransform = translate(37, 11) · scale(1.25)`, zoom 1.6, anchor `(120, -40)`, then a following `PlainRunRow` | icon origin equals the independently computed `B·W·p` within 0.5 px; the plain run is drawn at its world coordinates | a double-applied `B` lands at `B²Wp`, tens of pixels away |
| T7 | `canvas.ScreenLabelPlacementCacheShould` | `measuresAtMostTheBudgetAndDefersTheRest` | measurer with budget 2, five unknown formula titles | after one pass `newMeasurementsInLastPass() == 2`, `pendingCount() == 3`, `takeFollowUpRequest()` true | removing the budget measures all five and fails the counts |
| T7b | `canvas.ScreenLabelPlacementCacheShould` | `stallsInsteadOfRepeatingFollowUpsWhenKeysExceedCapacity` | measurer with capacity 8 and 20 formula titles, budget 2 | the follow-up chain ends within 20 passes, `measurementStalled()` is true, `takeFollowUpRequest()` false, `stallDiagnostic()` starts with `FORMULA_MEASUREMENT_STALL` | an unbounded retry chain never clears the request and fails the bounded-pass assertion |
| T7c | `canvas.ScreenLabelPlacementCacheShould` | `eachDeferringPassDrainsAPendingKey` | capacity 100, 6 titles, budget 2 | pass 1 defers 4; pass 2 defers 2 and drains 2; pass 3 defers 0 and stalls false | a pass that removes nothing while pending is non-empty sets the stall flag and fails |
| T8 | `canvas.ScreenLabelPlacementCacheShould` | `deferredFollowUpRendersTheIconItDeferred` | one formula title, budget 1 | the first pass is `DEGRADED` with `deferredForBudget`, the second is `RENDERED` | removing the retention exclusion keeps the degraded row |
| T8b | `canvas.ScreenLabelPlacementShould` | `doesNotRetainABudgetDeferredFace` | a remembered, non-`HOVER_ONLY`, `TRUNCATED_NEAR` row marked deferred | `retained` returns null and the pass recomputes | retaining it returns the degraded row |
| T9 | `canvas.ScreenLabelPlacementShould` | `enclosureLabelWithMathTitleKeepsPlainGeometry` | enclosure built with `SafeNodeLabel.of(raw, "Enclosure display", TitleText.of(Math))` | the row has one `PlainRunRow`, and width/height/anchor equal the frozen baseline constants for that scene | measuring the enclosure through the engine changes the width |
| T10 | `build.gradle` (`verifyGraphBundle`) | built-in gate control (no separate test class) | the real gate logic | the gate asserts the pinned name set through one closure, then asserts that the same closure **rejects** the pinned set with `jlatexmath-1.0.7.jar` removed, and asserts the notice entry, its checksum line, and its licence text | deleting the `jlatexmath` pin makes `expectedNames` equal the reduced list, so `assert !sameNameSet(reduced)` fails and the gate breaks |

Additional declared tests:

- `projection.input.TitleTextParserShould` — `plainTextIsOneRun`, `singleDelimitersSplitIntoThreeRuns`,
  `doubleDollarIsOneMathRun`, `escapedDollarIsLiteralAndUnescaped`, `unmatchedDollarRollsBackTheWholeTitle`,
  `laterValidFormulaDoesNotSurviveAnUnmatchedDelimiter`, `wholeTextIsMathProducesOneRun`,
  `wholeTextWithDelimitersKeepsThemInTheRawSource`, `textCommandDollarDoesNotCloseEarly`,
  `escapedDollarInsideFormula`, `emptyRunsAreDropped`, `emptySourceYieldsOneEmptyPlainRun`,
  `emptySourceWinsOverWholeTextMath`, `unbalancedDoubleDollarRollsBack`, `trailingBackslashIsLiteral`.
- `canvas.title.TitleTruncationShould` — `degradedPlainTextJoinsRawSources`,
  `degradedPlainTextKeepsPlainTextVerbatim`, `degradedPlainTextAddsNoCharacters`.
- `canvas.title.FormulaIconsShould` — `buildsEachKeyOnce`, `cachesFailuresWithoutRebuilding`,
  `evictsLeastRecentlyUsedBeyondCapacity`, `capacityMustBePositive`.
- `canvas.ScreenLabelPlacementCacheShould` — `followUpPassIsACacheMissExactlyOnce`,
  `noFollowUpWhenNothingWasDeferred`, `generationChangeClearsPendingAndStall`.
- `canvas.title.TitleMeasurerShould` — `generationChangeClearsCacheAndPending`,
  `failedMeasurementIsReusedWithoutASecondBuild`, `mixedRunsComposeInOrder`,
  `plainTitleNeverConsumesBudget`.
- `adapter.SafeNodeLabelExtractorShould` — `latexPatternFormatYieldsOneMathRun`,
  `unparsedLatexPatternFormatYieldsOneMathRun`, `latexPrefixYieldsOneMathRun`,
  `markdownFormatKeepsDollarMath`, `displayTextIsUnchangedForEveryExistingFixture`.
- `canvas.GraphCanvasPaintShould` — `forcedHoverLabelPaintsItsFormula` (a forced `HOVER_ONLY` label paints one
  `MathRunRow`), `formulaRunsRespectTheDimmingComposite` (a dimmed label's formula is painted under the same
  composite), `paintingAfterAMathRunKeepsTheWorldTransformForTheNextPlainLabel`.

## 8. Build and bundle changes

Added inside `dependencies { }` in `freeplane_plugin_graph/build.gradle`:

```groovy
lib('org.scilab.forge:jlatexmath:1.0.7') {
    transitive = false
}
```

Pinned checksum of `jlatexmath-1.0.7.jar`:
`6d04c3843fc98d2eea9ba4cff0be7b80540ca75c5a4e0b3463fd1219c48cc65d` (verified identical for the installed
distribution copy and the Gradle module cache).

`verifyGraphBundle` gains:

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

assert sameNameSet(dependencyFiles*.name)
// Built-in control: the same comparison must reject the pinned set without the formula jar.
assert !sameNameSet(expectedNames.findAll { it != 'jlatexmath-1.0.7.jar' })

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

final java.util.jar.JarEntry notice = bundle.getEntry('META-INF/NOTICE.jlatexmath.txt')
assert notice != null
final String noticeText = bundle.getInputStream(notice).getText('UTF-8')
assert noticeText.contains('6d04c3843fc98d2eea9ba4cff0be7b80540ca75c5a4e0b3463fd1219c48cc65d')
assert noticeText.contains('org.scilab.forge:jlatexmath:1.0.7')
assert noticeText.contains('GNU GENERAL PUBLIC LICENSE')
assert noticeText.contains('classpath exception')
```

The existing GraphStream loop over `graphStreamChecksums` and its `NOTICE.graphstream.txt` assertions stay
unchanged, so the three original checksums remain pinned exactly as today.

`src/main/resources/META-INF/NOTICE.jlatexmath.txt` content requirements:

- the coordinate `org.scilab.forge:jlatexmath:1.0.7` and the jar's SHA-256, as asserted above;
- the statement that the archive is distributed under the GNU General Public License version 2 **with the
  classpath exception**, quoted from the jar's own `META-INF/LICENSE`, and that the jar also carries
  `META-INF/COPYING`;
- one line per bundled font licence family recorded in the jar: OFL for `eufb10`/`eufm10`/`msam10`/`msbm10`, the
  Knuth licence for the Computer Modern and Cyrillic families, the free licence for `dsrom10`, public domain for
  `stmary10`/`rsfs10`, and GPL version 2 for the Greek fonts, each pointing at
  `org/scilab/forge/jlatexmath/fonts/licences/` inside the jar.

`graphUiEvidence` keeps its two tracked output paths and gains the four reference titles plus a printed
first-pass measurement count and elapsed time.

## 9. Errors, compatibility and cleanup

| Situation | Behaviour |
| --- | --- |
| engine throws on a formula | failure recorded in `FormulaIcons`; rendered measurement is `DEGRADED`; raw source drawn; one attempt per key per instance lifetime |
| unmatched or unbalanced delimiter | whole title returned as one plain run; no engine call |
| formula taller than `2.0 ×` line height | `DEGRADED` |
| icon dimension zero, negative or non-finite; baseline ratio non-finite | `DEGRADED` |
| empty title | measured with `width == 0`, `height > 0`; drawn as the existing fallback text |
| pass budget exhausted | deferred `DEGRADED`, queued in `pending`, never retained; one follow-up request |
| pass drains nothing while `pending` is non-empty | `FORMULA_MEASUREMENT_STALL`, no further request; deferred titles stay degraded for that generation |
| non-invertible base transform | `IllegalArgumentException("base transform must be invertible")` before any drawing |
| resolved row geometry disagrees with the label box | `IllegalArgumentException("title row geometry must match the label")` |
| rendered measurement loses its icon at resolution | `IllegalStateException("rendered measurement lost its icon")` |
| `null` graphics, row, font, colour, transform, or non-finite anchor/zoom | `NullPointerException` naming the parameter, or `IllegalArgumentException` |

Existing tests that must pass unmodified (only the three `PlacedLabel` constructor call sites gain the row
argument, and the two `GraphCanvasPaintShould` sites plus the `ScreenLabelLiveTitleRefreshShould` site are the only
touches):

- `ScreenLabelPlacementShould` — every existing test, including the pinned pixel constants and ink-collision
  assertions.
- `ScreenLabelLiveTitleRefreshShould` — every existing test, including `keepsAnUnchangedForcedOverCapFace`,
  `keepsAnOverCapFaceAfterDemotionAndOnTheNextReplay`, `keepsAnUnchangedTruncatedFaceWhenSpaceBecomesAvailable`.
- `GraphCanvasPaintShould` — every existing assertion.
- `AccessibleGraphCanvasShould`, `GraphSearchModelShould`, `AdapterArchitectureShould` — unmodified.
- `SafeNodeLabelExtractorShould` — every existing assertion.

No translation resource, resource-bundle key, XML format, or workspace persistence change.

## 10. Verification commands and evidence

Run on a machine with a JDK and Gradle (`AGENTS.md`: Java from `~/.sdkman/candidates/java/21.0.8-zulu`; the
repository CI runs Ubuntu with Java 21):

```text
gradle :freeplane_plugin_graph:test
gradle :freeplane_plugin_graph:verifyGraphBundle
gradle :freeplane_plugin_graph:graphUiEvidence
```

Expected evidence:

1. `test` — all module tests green, including every new class in section 7.
2. `verifyGraphBundle` — passes with four pinned jars, the extended `Bundle-ClassPath`, the notice entry and its
   checksum line, and the built-in naming control.
3. `graphUiEvidence` — regenerates the two tracked images and prints the first-pass measurement count and elapsed
   time for the four reference titles. That count and time are the only inputs that may justify changing
   `DEFAULT_MAX_NEW_MEASUREMENTS_PER_PASS` from 200.

None of these commands ran while writing this specification: the authoring environment has no JDK, no Gradle, and
no network. No test, bundle, or evidence result is claimed anywhere in this document.

## 11. Decision record

Inherited from the approved design, not new proposals:

- `$$...$$` renders as one formula in text style with no display styling (design section 4.2).
- Wholesale degradation on truncating rungs rather than per-run shedding (design section 4.4).
- Enclosure labels keep `displayText()` geometry and are excluded from formula rendering (design section 10).
- Exclusion of only `deferredForBudget` rows from retention (design section 4.6).
- The base transform is captured before the world transform and applied exactly once (design section 4.5).
- The per-generation queue and the `FORMULA_MEASUREMENT_STALL` outcome (design section 4.6).

New decisions this specification makes where the design is silent, each with its justification:

| Decision | Justification |
| --- | --- |
| Parser lives in `projection.input` | the ArchUnit rule keeps `projection..` free of Freeplane feature types, and the parser needs no AWT or engine types |
| Rendering types live in a new `canvas.title` package | keeps `canvas..`'s existing public surface unchanged and confines the engine dependency |
| `TitleMetrics` declares its own `FontRenderContext` with today's flags | `ScreenLabelPlacement.SCREEN_FRC` is package-private in `canvas`, and T4e pins the two contexts equal |
| The per-pass budget is constructor-injectable with a default of 200 | the design requires a budget; injectability is what makes the quota falsifiable without reflection |
| `TitleRow.resolve` throws instead of degrading when a rendered icon vanishes | degrading there would require re-measuring geometry the row does not have (S9) |
| `truncated` keeps the source-versus-drawn definition | the existing retention logic depends on that meaning (S13) |
| A stalled generation keeps degraded titles until the generation changes | the design's stall outcome has no other exit, and inventing one would be a new mechanism |
