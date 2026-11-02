# Graph workspace — LaTeX formulas in node title labels

Task identifier: `2026-11-03-graph-latex-titles`

Status: **design draft for frontier review.** The user answered the collaborative design gate with
"as proposed", accepting the four decisions recorded in section 2. Nothing is approved yet: section 11 lists
the questions this draft puts to the reviewer.

Baseline: `plugin/graph-workspace` at `fd7488d5ae7f92bcdb3f70fc76b09f6e881aacc0`. All investigation ran in the
PM integration worktree; the user's delivery checkout was read only and never built.

Revision history:

- r1 (this revision) — first complete design, grounded in a read-only trace of the label pipeline, the module's
  bundle gate, and the JLaTeXMath 1.0.7 jar that the distribution already ships.

## 1. Reported need

The user's screenshots of the graph workspace show node titles drawn as raw LaTeX source:

- `Theorem: $\bigcup\alpha\leslant\alpha$`
- `Theorem: Equivalence of $\leqsla…` (truncated mid-macro)
- `Theorem: $N$ is well-ordered by $\in$`
- `Theorem: initial segment`

The titles are legible as text but not as notation: `\bigcup`, `\alpha`, `\leslant` and `\in` are read as
backslash words, and the ellipsis in the second example lands inside a control sequence, so the visible text is
not even valid LaTeX. The mind map itself renders these same nodes with JLaTeXMath, so the graph view is the only
surface where the notation degrades.

The user asked whether MathJax would be a good way to display LaTeX in titles, then accepted the design proposal
in section 2.

## 2. Approved decisions

The user accepted all four recommendations verbatim ("as proposed"):

- **D1 — scope.** Inline formulas are first class. A title is a sequence of plain-text and formula runs, not only
  a whole-title formula. `Theorem: $\bigcup\alpha\leqslant\alpha$` is the reference case.
- **D2 — math signal.** Dollar delimiters inside the title text are the primary signal that a run is math. Nodes
  whose format is `latexPatternFormat` or `unparsedLatexPatternFormat`, and node content that starts with
  `\latex`, also count as math for the whole title.
- **D3 — degradation.** A formula that cannot be rendered, or that does not fit its slot, degrades to its raw
  LaTeX source. It is never dropped and never blank.
- **D4 — width budget.** A formula must fit today's slot limits (200 px vertical, 130 px horizontal, 150 px
  diagonal) and degrade like any other long title. The limits do not change.

## 3. Measured evidence

### 3.1 The title pipeline is three seams wide

A node title reaches the screen through exactly three places, and all three are string-typed today.

1. **Extraction** — `adapter.SafeNodeLabelExtractor.extract(NodeModel)` produces
   `projection.input.SafeNodeLabel(fullText, displayText)`. It converts HTML to plain text, strips a leading
   `\latex`/`\unparsedlatex` prefix unless the node format is one of the three pattern formats, normalizes
   whitespace, and caps `displayText` at `MAX_DISPLAY_CODE_POINTS = 80` code points. An existing test already
   pins the intent: `SafeNodeLabelExtractorShould.keepsFormulaLatexAndMarkdownAsNormalizedUnevaluatedSource`
   keeps formula *source*, unevaluated.
2. **Measurement** — `canvas.ScreenLabelPlacement` measures with the private static
   `screenBounds(String, Font)` (`font.getStringBounds(text, SCREEN_FRC)`) and truncates with the private static
   `truncateTo(String, Font, double)`, which cuts by `String.length()` and appends `\u2026`. Both are called from
   `placeNode`, `retained`, `ladder`, `baseSlot`, `hoverOnly`, `fullTextCandidateWasFree`, and the enclosure
   measurements. The result is `canvas.PlacedLabel`, which stores the drawn `String text`, its `Font`, the
   measured `width`/`height`, `truncated`, and the comparison key `sourceText`.
3. **Painting** — `canvas.GraphPainter.paintLabels` derives a world-space font
   `label.font().deriveFont(size / viewport.zoom)`, sets `theme.labelColor()`, and calls the private static
   `drawCentered(graphics, font, label.text(), anchorX, anchorY)`, which re-measures with
   `font.getStringBounds` and draws with `graphics.drawString`. The graphics transform is the world space of
   `GraphViewport`, so labels are effectively constant-size on screen.

Two further consumers read `SafeNodeLabel.displayText()` and must not change:

- `geometry.GraphGeometryEngine` and `geometry.BoundaryInvariants` measure enclosure label size through
  `GeometryTextMetrics.measure(String, BoundaryTier)` (implementation `AwtGeometryTextMetrics`) to reserve hull
  padding. `layout.graphstream.GraphStreamLayoutEngine` reserves relation spacing from
  `displayText().length() * CHAR_WIDTH_UPPER_BOUND`.
- `window.ContributorInspector` shows the title in the sidebar, and `canvas.AccessibleGraphCanvas` derives the
  accessible name and description from `info.label`.

Raw LaTeX source is therefore *wider* than a rendered formula at the same font size — `$\alpha$` measures as
eight characters of plain text but paints as one nine-pixel glyph. Every `displayText()` consumer that reserves
space from the string is a conservative upper bound, and stays correct if rendering only ever narrows the drawn
content.

### 3.2 The bundle gate pins the dependency set exactly

`freeplane_plugin_graph/build.gradle` declares `ext.bundleImports = 'nothing.*'`, `ext.bundleExports = ''`, and a
`verifyGraphBundle` task that `check` depends on. That task asserts, among others:

```groovy
assert dependencyFiles*.name.sort() == graphStreamChecksums.keySet().toList().sort()
assert sha256(dependency) == expectedChecksum
assert maximumClassMajor(dependency) <= 52
assert attributes.getValue('Bundle-ClassPath') ==
    ".,lib/plugin-${project.version}.jar,lib/gs-core-1.3.jar,lib/pherd-1.0.jar,lib/mbox2-1.0.jar"
assert !attributes.getValue('Import-Package')
assert !attributes.getValue('Export-Package')
```

A new runtime library is therefore not a one-line build change: the pinned name set, the checksum table, the
`Bundle-ClassPath` string, and the notice/licence assertions all move together. Any design that adds a library
without updating this task fails `check`.

### 3.3 JLaTeXMath is already in the distribution and fits the gate

`freeplane_plugin_latex/build.gradle` declares `lib 'org.scilab.forge:jlatexmath:1.0.7'` (plus the Greek and
Cyrillic font packs), and the installed distribution carries the jars under
`BIN/plugins/org.freeplane.plugin.latex/lib/`. Facts measured from that shipped jar:

- class-file major version is **51** for all 196 classes, so the gate's `maximumClassMajor(dependency) <= 52`
  assertion passes;
- jar size is 669,575 bytes (1,038,151 bytes with both font packs);
- `org/scilab/forge/jlatexmath/TeXSymbols.xml` defines `leqslant`; it does **not** define `leslant`, so the
  screenshot's `\leslant` is not renderable by this engine — D3's raw-source degradation is a live requirement,
  not a hypothetical;
- `TeXIcon` exposes `getIconWidth()`, `getIconHeight()`, `getBaseLine()`, `setForeground(...)` and
  `paintIcon(Component, Graphics, int, int)`, and `TeXFormula.new TeXIconBuilder()` builds an icon at a chosen
  pixel size, style and colour;
- the jar carries `META-INF/COPYING`, `META-INF/LICENSE`, and the font licences under
  `org/scilab/forge/jlatexmath/fonts/licences/`.

`freeplane_plugin_latex/src/main/java/org/freeplane/plugin/latex/TeXText.java` is the in-repo reference for the
build call:

```java
return tf.new TeXIconBuilder()
    .setFGColor(fgColor)
    .setStyle(style)
    .setSize(size)
    .setWidth(TeXConstants.UNIT_PIXEL, maxWidth, align)
    .setIsMaxWidth(true)
    .setInterLineSpacing(latexInterlineSpacingUnit, latexInterlineSpacingValue)
    .build();
```

It also shows the existing user escape hatch for macros the engine does not know: a `latex_macros`
`ResourceController` property is prepended to the formula source, so a user can define `\leslant` as
`\leqslant` once and have both the map and the graph honour it.

### 3.4 Environment prerequisite for build and test evidence

The checkout this design was written in has **no JDK and no Gradle**: `/usr/lib/jvm` holds two JRE-only
installs, `javac` is absent, `gradle` is not on `PATH`, and the network is unreachable. `AGENTS.md` specifies
`~/.sdkman/candidates/java/21.0.8-zulu` plus `gradle`, and neither exists here. Every test command in section 8
must therefore run on a machine that has them; this is recorded as an open item, not as a design assumption.

## 4. Design

### 4.1 Value model

A parsed title is a pure, immutable value with no AWT and no Freeplane types, so it can live beside
`SafeNodeLabel` without breaking the architecture rule that `projection..`, `geometry..`, `layout..` and
`canvas..` never depend on `org.freeplane.features..`:

```java
package org.freeplane.plugin.graph.projection.input;

public final class TitleText {
    public static TitleText plain(String text);          // one Plain run
    public List<Run> runs();                             // never empty
    public String plainSource();                         // debug and identity aid
    public boolean hasMath();

    public interface Run { }
    public static final class Plain implements Run { String text(); }
    public static final class Math implements Run {
        String source();        // formula body without the delimiters
        String rawSource();     // exactly what the user typed, used by D3
    }
}
```

`SafeNodeLabel` gains a fourth, derived field `title()` of type `TitleText`, computed once in its constructor
from `displayText` plus a math-signal flag supplied by the extractor. **`SafeNodeLabel.equals` and `hashCode`
keep comparing only `fullText` and `displayText`**, so projection identity, deduplication, and the existing
extractor tests keep their current meaning.

The `80`-code-point `displayText` cap stays where it is and applies to the *source* string, before parsing. A
formula therefore cannot be silently shortened by the cap in a way the parser cannot see: the cap produces
`…` inside the source, the parser treats an unterminated `$` as literal text, and D3 renders that raw text. This
is the same visible outcome as today for over-long titles, and it is the only place where the cap and the parser
interact.

### 4.2 Parsing rules

`TitleTextParser.parse(String source, boolean wholeTextIsMath)` is a single left-to-right scan. It is
deterministic, allocation-light, and total: it never throws.

| Input | Result |
| --- | --- |
| `wholeTextIsMath == true` (node format `latexPatternFormat`, `unparsedLatexPatternFormat`, or content began with `\latex`) | one `Math` run over the whole source |
| `text $a$ text` | `Plain("text ")`, `Math("a")`, `Plain(" text")` |
| `$a$` | one `Math` run |
| `$$a$$` | one `Math` run; display delimiters are not special-cased and do not inflate the size |
| `costs \$5 and $\alpha$` | `Plain("costs $5 and ")`, `Math("alpha")`; `\$` is a literal dollar |
| `unmatched $a` | one `Plain` run containing the literal source |
| `$$a$` … unbalanced | one `Plain` run containing the literal source |
| empty after delimiters | that run is dropped; if nothing remains the title is `Plain("")`-free and the caller keeps the existing fallback text |

The parser does not do macro substitution, does not interpret `\text{...}`, and does not look for `$$` beyond the
two-dollar case above. Brace-depth tracking exists only to stop a `$` inside `\text{$...}$` from closing the
formula early.

### 4.3 Measurement seam

A new package `canvas.title` holds the rendering-side types, which may use AWT and JLaTeXMath:

```java
public interface TitleMetrics {
    Box measure(TitleText title, Font font);      // screen-space pixels, matches SCREEN_FRC
}
public final class AwtTitleMetrics implements TitleMetrics { ... }        // default
public final class MemoizedTitleMetrics implements TitleMetrics { ... }   // per pass, per (title, size)
```

`Box` carries `width`, `height`, `ascent`, `descent`. Rules:

- A title with no math measures **exactly** as today: `font.getStringBounds(plainSource, SCREEN_FRC)`, same
  `Rectangle2D` — a pure-plain label must be pixel-identical to the baseline.
- A title with math measures as the horizontal sum of its runs; `ascent` is the maximum of the plain font's
  ascent and every formula icon's `getBaseLine()`; `descent` is the maximum of the plain descent and every
  icon's `getIconHeight() - getBaseLine()`; `height` is `ascent + descent`.
- Formula icons are built at `Math.round(font.getSize2D())` pixels, `TeXConstants.STYLE_TEXT`, and no maximum
  width, because the slot limit is enforced by the truncation ladder, not by the engine.

`ScreenLabelPlacement` keeps its private `screenBounds` for plain-only paths and gains the injected
`TitleMetrics` for the six title-measuring call sites. `screenBounds(String, Font)` and
`textWidth(String, Font)` stay package-visible and unchanged so the existing placement tests keep compiling and
keep asserting the same numbers.

### 4.4 Truncation ladder

`ScreenLabelPlacement.truncateTo(String, Font, double)` is unchanged for plain titles. Math titles go through
`TitleTruncation.truncate(TitleText, Font, double limit, TitleMetrics)`:

1. Whole title fits the limit → unchanged.
2. Otherwise drop trailing runs one at a time. A dropped `Math` run is dropped **whole**; a formula is never cut
   mid-source, which is exactly the defect visible in the user's `Equivalence of $\leqsla…` screenshot.
3. When only leading plain runs remain, truncate the last one by the existing character loop and append `\u2026`.
4. When only a `Math` run remains and it does not fit, replace it with `Plain(rawSource)` and apply the existing
   character truncation to that text (D3 and D4 together).

The ladder in `ScreenLabelPlacement.LADDER` is untouched: FULL/DENSE × near/displaced × truncated. Formula
rendering only changes *what* a rung measures and *what* a truncated rung draws.

### 4.5 Painting

`GraphPainter.drawCentered` is replaced by `TitlePainter.paint(Graphics2D, TitleText, Font, double x, double y,
Color color)`, which keeps the current centring rule (`baseline = (ascent - descent) / 2`) over the shared row
box from `TitleMetrics`:

- `Plain` runs draw with `graphics.drawString`, at the world-space font the caller already derives, so plain
  titles produce the same `drawString(text, x, y)` calls the paint tests capture today.
- `Math` runs are painted in **device space**: the painter converts the run's world anchor through
  `graphics.getTransform()`, then paints the cached icon inside a derived `Graphics2D` whose transform is the
  identity, so the formula lands at its logical screen size regardless of zoom. This is the same visible size
  the text path achieves by dividing the font by `zoom`, and it keeps the icon cache zoom-independent.
- Colour comes from the caller. `theme.labelColor()` is applied to the derived graphics; the dimming composite
  that `paintLabels` already sets stays in force, because the derived graphics inherits it.

`PlacedLabel.text()` keeps returning the drawn plain string, so `GraphCanvasPaintShould.texts(...)`,
`ScreenLabelPlacementShould`, the hover bar, and the accessible name all keep working. `PlacedLabel` gains
`title()` and exposes it for the painter; for a plain label `title()` is a single `Plain` run equal to `text()`.

### 4.6 Icon cache and build budget

Parsing and icon construction are the only new per-pass costs. `FormulaIcons` is a bounded cache:

- key `(source, pixelSize, style)`, LRU, capacity 512;
- one build per key per pass, memoized through `MemoizedTitleMetrics`;
- failures (`ParseException`, `InvalidSymbolTypeException`, or any other `RuntimeException` from the engine) are
  cached as failures, so a broken macro costs one attempt, not one per pass;
- a per-pass build budget of `MAX_NEW_ICONS_PER_PASS = 200`. A pass that exhausts the budget renders the
  remaining math runs as raw source **for that pass**, records that the budget was hit, and the canvas schedules
  exactly one follow-up placement pass. No timers, no threads, no partial frames: the follow-up is a placement
  request, so the result is deterministic and testable.

The budget exists because a first pass over a large formula-heavy map is otherwise unbounded work on the EDT.
Its value is a starting point that the acceptance measurement in section 8 must confirm or replace with a
measured number; it is not presented as a measured threshold.

### 4.7 Bundle and licence

`freeplane_plugin_graph/build.gradle` gains `lib('org.scilab.forge:jlatexmath:1.0.7') { transitive = false }`.
The two font packs are **not** added: math symbols come from the core jar's `jlm_*` fonts, and the packs only
affect text-mode Greek and Cyrillic inside `\text{...}`. A formula that needs them degrades under D3.

`verifyGraphBundle` changes in the same commit:

- split the checksum map into `graphStreamChecksums` (three entries, unchanged) and a `bundledChecksums` map that
  also pins `jlatexmath-1.0.7.jar`;
- assert the dependency name set against `bundledChecksums` and the `<= 52` class-major rule against every
  bundled jar;
- assert `Bundle-ClassPath` equals
  `".,lib/plugin-${project.version}.jar,lib/gs-core-1.3.jar,lib/pherd-1.0.jar,lib/mbox2-1.0.jar,lib/jlatexmath-1.0.7.jar"`;
- keep the GraphStream notice assertion and add `META-INF/NOTICE.jlatexmath.txt`, carrying the jar's
  `META-INF/COPYING` and font-licence provenance, copied the same way the GraphStream notice is;
- keep `!attributes.getValue('Import-Package')` and `!attributes.getValue('Export-Package')`, which is the
  reason this is a bundled `lib` rather than an OSGi import from `freeplane_plugin_latex`.

The alternative — exporting `org.scilab.forge.jlatexmath` from the LaTeX plugin and importing it here — is
rejected: this bundle's deliberate isolation (`bundleImports = 'nothing.*'`) exists so the graph view does not
depend on which other plugins are installed, and a graph view that cannot label nodes when the LaTeX plugin is
absent would be a new failure mode for a core feature.

## 5. Invariants

1. A title whose `TitleText` has no math produces byte-identical `drawString` calls and identical placement
   geometry to the baseline at the same request.
2. `SafeNodeLabel.displayText()`, `fullText()`, `equals` and `hashCode` are unchanged, so projection identity,
   deduplication, sidebar text, hull padding, and the accessible name are unchanged.
3. A `Math` run is never drawn as a partial source string and never drawn blank: it is either a rendered icon or
   the complete `rawSource` of the run.
4. Every measured box is finite and positive; a formula whose height exceeds `2.0 ×` the plain line height of
   its font is treated as unrenderable and degrades under D3.
5. Placement and painting agree: the painter never re-measures with a different metric than the placer used,
   and a label whose measured width exceeds its slot limit is not produced.
6. Icon construction is bounded: at most one build per `(source, size, style)` per pass, and at most
   `MAX_NEW_ICONS_PER_PASS` builds per pass, with at most one scheduled follow-up pass.
7. The bundle gate passes with the new jar: pinned checksums, exact `Bundle-ClassPath`, no
   `Import-Package`/`Export-Package`, class major `<= 52`.

## 6. Exact change set

| File | Change |
| --- | --- |
| `projection/input/TitleText.java` | new value type with `Plain` and `Math` runs |
| `projection/input/TitleTextParser.java` | new total, deterministic parser (section 4.2) |
| `projection/input/SafeNodeLabel.java` | add derived `title()`, `TitleText`-typed; equality unchanged |
| `adapter/SafeNodeLabelExtractor.java` | compute the math-signal flag from `NodeStyleModel.getNodeFormat` and the `\latex`/`\unparsedlatex` prefix, then pass it to the parser |
| `canvas/title/Box.java` | new measured box (`width`, `height`, `ascent`, `descent`) |
| `canvas/title/TitleMetrics.java`, `AwtTitleMetrics.java`, `MemoizedTitleMetrics.java` | new measurement seam |
| `canvas/title/FormulaIcons.java` | new bounded icon cache with failure memoization and per-pass budget |
| `canvas/title/TitleTruncation.java` | new run-aware truncation (section 4.4) |
| `canvas/title/TitlePainter.java` | new painter (section 4.5) |
| `canvas/ScreenLabelPlacement.java` | thread `TitleText` + `TitleMetrics` through the six measuring sites; `truncateTo` delegates for math titles; geometry behaviour unchanged for plain titles |
| `canvas/PlacedLabel.java` | carry the `TitleText` alongside `text()`; no accessor removed |
| `canvas/GraphPainter.java` | `drawCentered` replaced by `TitlePainter.paint`; label colour and dimming unchanged |
| `freeplane_plugin_graph/build.gradle` | add the `lib` dependency, update `verifyGraphBundle` per section 4.7 |
| `src/main/resources/META-INF/NOTICE.jlatexmath.txt` | new licence provenance file |

No file outside `freeplane_plugin_graph` changes. `freeplane_plugin_latex` is untouched, and the graph bundle
keeps its own copy of the jar.

## 7. Failure modes

| Failure | Behaviour |
| --- | --- |
| unknown macro (`\leslant`) | engine throws; the run draws its `rawSource`; the failure is cached |
| unbalanced `$` | parser produces literal `Plain` text; no engine call |
| formula wider than the slot | run dropped whole by the ladder; if it is the only run, `rawSource` is truncated by the existing character loop |
| formula taller than `2.0 ×` line height | treated as unrenderable, `rawSource` drawn |
| engine returns non-finite or non-positive metrics | treated as unrenderable; invariant 4 keeps the box contract |
| budget exhausted mid-pass | remaining math runs draw `rawSource` this pass; exactly one follow-up pass is scheduled |
| icon cache eviction under memory pressure | rebuild on demand; correctness unaffected |

## 8. Test strategy

New JUnit 4 + AssertJ tests, named in the house style:

- `projection/input/TitleTextParserShould` — plain text is one run; single and double delimiters; escaped `\$`;
  unmatched and unbalanced delimiters yield one literal `Plain`; `wholeTextIsMath` yields one `Math` run;
  `\text{$...}$` does not close early; empty-run dropping.
- `canvas/title/AwtTitleMetricsShould` — a plain title measures exactly as
  `font.getStringBounds(displayText, SCREEN_FRC)`; a math title measures as the run sum; ascent/descent are the
  maxima; height is `ascent + descent`; a plain-only title is pixel-identical to the baseline box.
- `canvas/title/TitleTruncationShould` — a fitting title is returned unchanged; a trailing `Math` run is dropped
  whole and never cut; a leading plain run is truncated with `\u2026` exactly as `truncateTo` does; a sole
  oversized `Math` run falls back to truncated `rawSource`; plain-only behaviour equals the existing
  `truncateTo`.
- `canvas/title/FormulaIconsShould` — one build per `(source, size, style)` per pass; a failing source is
  attempted once and then served from the failure cache; the budget stops builds at the limit; the cache is LRU
  bounded.
- `canvas/ScreenLabelPlacementShould` (extend) — a math label is measured by the icon, respects `slotMaxWidth`,
  and reaches `HOVER_ONLY` through the same ladder a plain over-long title does.
- `canvas/GraphCanvasPaintShould` (extend) — a plain title's captured `drawString` calls are identical to the
  baseline; a math run paints at the shared baseline; the dimming composite applies to formula runs; a forced
  hover label paints its formula.
- `adapter/SafeNodeLabelExtractorShould` (extend) — `latexPatternFormat`, `unparsedLatexPatternFormat` and a
  `\latex` prefix each yield one `Math` run; `displayText()` output and equality are unchanged for the existing
  fixtures.

Commands, to run on a machine with a JDK and Gradle:

```text
gradle :freeplane_plugin_graph:test
gradle :freeplane_plugin_graph:verifyGraphBundle
gradle :freeplane_plugin_graph:graphUiEvidence
```

Acceptance evidence additionally requires a screenshot of a fixture map containing the user's four label shapes
(`Theorem: $\bigcup\alpha\leslant\alpha$`, `Equivalence of $\leqsla…`, `$N$ is well-ordered by $\in$`,
`Theorem: initial segment`) rendered by the real canvas, showing the rendered formula for the renderable one and
readable raw source for `\leslant`, plus the first-pass timing that confirms or replaces
`MAX_NEW_ICONS_PER_PASS`.

## 9. Risks

1. **Bundle-gate drift.** The gate is the only thing keeping the graph plugin's dependency set honest; changing
   four assertions in one commit without a test that fails on each of them individually is the main regression
   risk. The change must add one assertion per pinned property, not one combined check.
2. **Device-space painting inside a world-space pass.** Painting math runs under an identity transform is a mode
   switch in an otherwise uniform painter. It must save and restore the transform and the composite, and a test
   must prove that a following plain label still draws in world space.
3. **Engines differ from the map.** A map node renders its LaTeX with `\text{...}` wrapping, display style,
   interline spacing and `latex_macros`; a label renders a formula body in text style inside a single line. The
   same source can therefore look different in the two views. This design accepts that difference and does not
   reuse `TeXText`, because wrapping a title in `\text{...}` would render `$...$` as literal text — but it must
   be stated in user-facing documentation rather than discovered.
4. **`\leslant` is unrepresentable.** The user's own screenshot contains a macro the engine cannot render. The
   first thing the user will see is a partially-degraded label, so the design must make D3's fallback look
   intentional (`\leslant` shown as readable text) rather than broken.
5. **Performance on formula-heavy maps.** Unmeasured in this environment (section 3.4). The budget is a
   structural guard, not a measured threshold.
6. **Truncation semantics vs. the live-title-refresh contract.** `ScreenLabelLiveTitleRefreshShould` pins that a
   retained label is only kept while its `sourceText` is unchanged. A formula whose rendering changes without a
   source change (for example after a theme or font change) must go through the same font/eligibility
   conditions that contract already defines, not through a new rule.

## 10. Out of scope

- MathJax, KaTeX, or any JavaScript engine. The canvas has no browser, and the measured-geometry contract needs
  numeric width and height before painting.
- Display-style formulas, multi-line formulas, `\begin{align}`, and formula editing in the graph view.
- Widening `VERTICAL_MAX_WIDTH`/`HORIZONTAL_MAX_WIDTH`/`DIAGONAL_MAX_WIDTH` (D4).
- Change to enclosure-label geometry: `GeometryTextMetrics` keeps measuring `displayText()`, because raw source
  is a conservative upper bound and switching it would move every hull fixture.
- The Greek and Cyrillic font packs, and text-mode Greek/Cyrillic inside `\text{...}`.
- Rendering formulas on edges, pins, hulls, or the hover bar.

## 11. Open questions for the reviewer

1. Is `TitleText` on `SafeNodeLabel` the right home, given `SafeNodeLabel` is also constructed by tests and by
   `ProjectionEngine.HullBuilder`? The alternative is a `TitleText` field on `ProjectedNode`/`ProjectedEnclosure`
   only, parsed in `ProjectionEngine`.
2. Should the parser accept `\(...\)` and `\[...\]` as delimiters? Neither appears in the user's maps; accepting
   them widens the surface without evidence of need.
3. Is the device-space painting of math runs acceptable, or must the whole title row be painted in one space for
   uniformity, at the cost of rebuilding icons per zoom step?
4. Is `MAX_NEW_ICONS_PER_PASS = 200` the right structural guard before measurement, or should the first
   implementation ship without a budget and rely on the cache alone?
5. Section 3.4 records that no JDK or Gradle exists in this environment. Which machine runs the section 8
   commands, and who confirms the first-pass timing?
