# Graph workspace — LaTeX formulas in node title labels

Task identifier: `2026-11-03-graph-latex-titles`

Status: **design draft, revision 2, for frontier review.** The user answered the collaborative design gate
with "as proposed", accepting the four decisions recorded in section 2. Section 12 lists the questions this
revision puts to the reviewer.

Baseline: `plugin/graph-workspace` at `fd7488d5ae7f92bcdb3f70fc76b09f6e881aacc0`. All investigation ran in the
PM integration worktree; the user's delivery checkout was read only and never built.

Revision history:

- r1 — first complete design, grounded in a read-only trace of the label pipeline, the module's bundle gate,
  and the JLaTeXMath 1.0.7 jar that the distribution already ships.
- r2 (this revision) — applies review round 1's five blocking and three non-blocking findings. F1 reconciled
  truncation with D3 in 4.4; F2 scoped the width invariant to non-forced labels in 5.5; F3 added the follow-up
  pass lifecycle and the two files it needs in 4.6 and 6; F4 replaced identity-transform painting with an
  explicit coordinate contract in 4.5; F5 added the missing falsifying tests in 8; F6 removed the unplumbed
  `latex_macros` claim from 3.3; F7 replaced the unenforceable documentation requirement with a user-visible
  behaviour section and an explicit out-of-scope wiki follow-on; F8 corrected the truncation character, the
  method visibility wording, and the combined jar size.

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
  LaTeX source. It is never blank, and a rendered formula is never partially drawn.
- **D4 — width budget.** A formula must fit today's slot limits (200 px vertical, 130 px horizontal, 150 px
  diagonal) and degrade like any other long title. The limits do not change.

D3 and D4 together are the whole degradation contract, and section 4.4 is its single normative statement: a
label that overflows shows raw source, and that raw row may be ellipsized exactly as a long plain title is
today. "Never blank" forbids the empty label; it does not forbid the ellipsis that D4 explicitly allows.

## 3. Measured evidence

### 3.1 The title pipeline is three seams wide

A node title reaches the screen through exactly three places, and all three are string-typed today.

1. **Extraction** — `adapter.SafeNodeLabelExtractor.extract(NodeModel)` produces
   `projection.input.SafeNodeLabel(fullText, displayText)`. It converts HTML to plain text, strips a leading
   `\latex`/`\unparsedlatex` prefix unless the node format is one of the three pattern formats, normalizes
   whitespace, and caps `displayText` with `MAX_DISPLAY_CODE_POINTS = 80` code points, appending the literal
   three-character `"..."` (`SafeNodeLabelExtractor.java:151-157`). An existing test already pins the intent:
   `SafeNodeLabelExtractorShould.keepsFormulaLatexAndMarkdownAsNormalizedUnevaluatedSource` keeps formula
   *source*, unevaluated.
2. **Measurement** — `canvas.ScreenLabelPlacement` measures with the package-visible static
   `screenBounds(String, Font)` (`font.getStringBounds(text, SCREEN_FRC)`) and truncates with the
   package-visible static `truncateTo(String, Font, double)`, which cuts by `String.length()` and appends
   `ELLIPSIS = "\u2026"` — a different character from the extractor's `"..."`.
   `placeNode` (`ScreenLabelPlacement.java:433-448`) delegates to `retained`, then `ladder`, then `baseSlot`
   when forced, then `hoverOnly`; each of those, plus `fullTextCandidateWasFree` and the enclosure measurements,
   calls `screenBounds`/`truncateTo`. The result is `canvas.PlacedLabel`, which stores the drawn `String text`,
   its `Font`, the measured `width`/`height`, `truncated`, and the comparison key `sourceText`.
3. **Painting** — `canvas.GraphPainter.paintLabels` derives a world-space font
   `label.font().deriveFont(size / viewport.zoom)`, sets `theme.labelColor()`, and calls the private static
   `drawCentered(graphics, font, label.text(), anchorX, anchorY)`, which re-measures with
   `font.getStringBounds` and draws with `graphics.drawString`. `GraphPainter.paint` receives the component's
   `Graphics2D` and applies the world transform itself (`GraphPainter.java:63-72`), so labels are effectively
   constant-size on screen.

Two further consumers read `SafeNodeLabel.displayText()` and must not change:

- `geometry.GraphGeometryEngine` and `geometry.BoundaryInvariants` measure enclosure label size through
  `GeometryTextMetrics.measure(String, BoundaryTier)` (implementation `AwtGeometryTextMetrics`) to reserve hull
  padding. `layout.graphstream.GraphStreamLayoutEngine` reserves relation spacing from
  `displayText().length() * CHAR_WIDTH_UPPER_BOUND`.
- `window.ContributorInspector` shows the title in the sidebar, and `canvas.AccessibleGraphCanvas` derives the
  accessible name and description from `info.label`.

Raw LaTeX source is therefore *wider* than a rendered formula at the same font size — `$\alpha$` measures as
seven characters of plain text but paints as one glyph. Every `displayText()` consumer that reserves space from
the string is a conservative upper bound, and stays correct if rendering only ever narrows the drawn content.

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
- jar sizes are 669,575 bytes (core), 185,652 (Greek) and 188,924 (Cyrillic): **1,044,151 bytes** for all three;
- `org/scilab/forge/jlatexmath/TeXSymbols.xml` defines `leqslant`; it does **not** define `leslant`, so the
  screenshot's `\leslant` is not renderable by this engine — D3's raw-source degradation is a live requirement,
  not a hypothetical. The user's Freeplane configuration defines no `latex_macros` override, so nothing in this
  installation supplies the macro either;
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

It also shows that the map path prepends the `latex_macros` resource property (`TeXText.java:31-46`). **The
graph path in this design does not honour that property.** Plumbing it would put Freeplane's resource layer
inside `canvas..` or duplicate cache invalidation for macro changes, and section 10 records it as a follow-on
with the seam it would use. Until then the statement in this section is the honest one: the map and the graph
can disagree about macros the engine does not know.

### 3.4 The forced-label exception to the width cap

`placeNode` (`ScreenLabelPlacement.java:433-448`) falls back to `baseSlot` for a forced label, and `baseSlot`
(`:658-668`) measures the full text and returns it without any width check. `ScreenLabelLiveTitleRefreshShould
.keepsAnUnchangedForcedOverCapFace` (`:618-627`) pins that behaviour, asserting a forced `FULL_NEAR` label of
width `274.130005` — greater than `VERTICAL_MAX_WIDTH = 200` — with `forcedAtBaseSlot() == true`. The width cap
is therefore a property of the ladder, not of every placed label, and the design must not claim otherwise.

### 3.5 Environment prerequisite for build and test evidence

The checkout this design was written in has **no JDK and no Gradle**: `/usr/lib/jvm` holds two JRE-only
installs, `javac` is absent, `gradle` is not on `PATH`, and the network is unreachable. `AGENTS.md` specifies
`~/.sdkman/candidates/java/21.0.8-zulu` plus `gradle`, and neither exists here. Every test command in section 8
must therefore run on a machine that has them; this is recorded as a question in section 12, not as a design
assumption.

## 4. Design

### 4.1 Value model

A parsed title is a pure, immutable value with no AWT and no Freeplane types, so it can live beside
`SafeNodeLabel` without breaking the architecture rule that `projection..`, `geometry..`, `layout..` and
`canvas..` never depend on `org.freeplane.features..`:

```java
package org.freeplane.plugin.graph.projection.input;

public final class TitleText {
    public static TitleText plain(String text);          // one Plain run
    public static TitleText of(List<Run> runs);          // validated, never empty
    public List<Run> runs();                             // never empty
    public String plainSource();                         // concatenation of run sources, for debug
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
from `displayText` plus a math-signal flag supplied by the extractor. Its existing two-string factory and
constructor stay and default to `TitleText.plain(displayText)`, so every existing caller and test is unaffected.
**`SafeNodeLabel.equals` and `hashCode` keep comparing only `fullText` and `displayText`**, so projection
identity, deduplication, and the existing extractor tests keep their current meaning.

The `80`-code-point `displayText` cap stays where it is and applies to the *source* string, before parsing. A
title capped with `"..."` in the middle of a formula leaves an unterminated `$`, the parser treats that as
literal text, and D3 renders the raw text. This is the same visible outcome as today for over-long titles, and
it is the only place where the cap and the parser interact.

### 4.2 Parsing rules

`TitleTextParser.parse(String source, boolean wholeTextIsMath)` is a single left-to-right scan. It is
deterministic, allocation-light, and total: it never throws.

| Input | Result |
| --- | --- |
| `wholeTextIsMath == true` (node format `latexPatternFormat`, `unparsedLatexPatternFormat`, or content began with `\latex`) | one `Math` run over the whole source |
| `text $a$ text` | `Plain("text ")`, `Math("a")`, `Plain(" text")` |
| `$a$` | one `Math` run |
| `$$a$$` | one `Math` run; display delimiters are not special-cased and do not inflate the size |
| `costs \$5 and $\alpha$` | `Plain("costs $5 and ")`, `Math("\\alpha")`; `\$` is a literal dollar |
| `unmatched $a` | one `Plain` run containing the literal source |
| `$$a$` … unbalanced | one `Plain` run containing the literal source |
| empty after delimiters | that run is dropped; `TitleText.of` rejects an empty run list, so the parser returns `TitleText.plain(source)` |

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
  width, because the slot limit is enforced by the ladder, not by the engine.
- Every box must be finite and strictly positive. A run whose icon violates that, or whose height exceeds
  `2.0 ×` the plain line height of the font, is reported by the metrics as **unrenderable** and the whole title
  is measured in its degraded form (section 4.4 rule 3). The metrics never return a non-finite box.

`ScreenLabelPlacement` keeps its package-visible `screenBounds` for plain-only paths and gains the injected
`TitleMetrics` for the title-measuring call sites. `screenBounds(String, Font)` and `textWidth(String, Font)`
stay package-visible and unchanged so the existing placement tests keep compiling and keep asserting the same
numbers.

### 4.4 Truncation and degradation ladder

`ScreenLabelPlacement.truncateTo(String, Font, double)` is unchanged, and remains the only truncator for plain
titles. Math titles are resolved by one normative rule set, which is D3 and D4 applied to the existing rungs:

1. **Rendered form** — the title as runs. Used by every non-truncating rung (`FULL_NEAR`, `FULL_DISPLACED`,
   `DENSE_NEAR`, `DENSE_DISPLACED`). Such a rung yields a candidate only if the rendered row is renderable and
   its measured width is within `slotMaxWidth(slot)`; otherwise that rung yields no candidate, exactly as an
   over-wide plain title yields no candidate today.
2. **Degraded form** — every `Math` run replaced by a `Plain` run over its `rawSource`. Used by the truncating
   rungs (`TRUNCATED_*`) and as the final fallback. A truncating rung measures the degraded row and, if it
   exceeds the limit, applies the existing `truncateTo` character loop to that plain text.
3. **Unrenderable title** — if any math run is unrenderable, the rendered form does not exist and only the
   degraded form is offered, through rungs 2.

Consequences, stated plainly because they are visible to users:

- A formula is never omitted and a rendered formula is never partially painted: the run is either an icon or its
  complete source text.
- A row that overflows in every non-truncating rung degrades to raw source and may then be ellipsized. For the
  user's `Theorem: Equivalence of $\leqsla…` case this reproduces today's visible text, which is the honest
  outcome: the slot is narrower than the source, and D4 accepts that.
- The reference case `Theorem: $\bigcup\alpha\leqslant\alpha$` fits the 200 px vertical slot in rendered form
  (roughly 150 px), so it renders; the same title in a 130 px horizontal slot degrades.
- Wholesale degradation in a truncating rung is a deliberate simplification of "which run gives way", chosen
  because any finer rule either drops a formula or cuts a control sequence — the two outcomes D3 forbids.
  Section 12, question 1 asks the reviewer to confirm this reading.

The ladder in `ScreenLabelPlacement.LADDER` is untouched. Formula rendering changes only *what* a rung measures
and *what* a truncating rung draws. `PlacedLabel.truncated()` keeps its meaning: the drawn text ends with the
ellipsis.

### 4.5 Painting and the coordinate contract

`GraphPainter.drawCentered` is replaced by `TitlePainter.paint(Graphics2D g, TitleText title, Font font,
double worldX, double worldY, Color color, AffineTransform baseTransform)`, which keeps the current centring
rule (`baseline = (ascent - descent) / 2`) over the shared row box from `TitleMetrics`.

**Coordinate contract.** Two transforms exist and the design names both:

- the **base transform** is the `Graphics2D` transform `GraphPainter.paint` receives from the component. It is
  not assumed to be the identity: `GraphCanvas.paintComponent` passes the component graphics, and a scaled
  display or an offscreen image can arrive already transformed;
- the **world transform** is what `GraphPainter.paint` then concatenates for `GraphViewport`. It is world space,
  and it is the only transform `TitlePainter` is allowed to remove.

`GraphPainter.paint` captures `baseTransform = graphics.getTransform()` **before** applying the world transform
and passes it down. For a math run, `TitlePainter` computes the run's device anchor by transforming the world
anchor through the current transform, then paints the icon inside `graphics.create()` with
`copy.setTransform(baseTransform)` and a translation to the device anchor. That removes the world transform
while preserving the component's device transform, so a scaled display keeps correct icon size and position.

- `Plain` runs draw with `graphics.drawString` at the world-space font the caller already derives, i.e. exactly
  as today.
- `Math` runs paint as described, at the logical font size, so the icon cache is zoom-independent.
- Colour comes from the caller (`theme.labelColor()`); the dimming composite `paintLabels` already sets stays in
  force because `graphics.create()` inherits it. `TitlePainter` disposes the derived graphics in a `finally`.

`PlacedLabel.text()` keeps returning the drawn plain string, so `GraphCanvasPaintShould.texts(...)`,
`ScreenLabelPlacementShould`, the hover bar, and the accessible name all keep working. `PlacedLabel` gains
`title()` and exposes it for the painter; for a plain label `title()` is a single `Plain` run equal to `text()`.
For a truncated math label, `text()` is the degraded, ellipsized plain text — which is exactly what is drawn.

### 4.6 Icon cache, build budget, and the follow-up pass

Parsing and icon construction are the only new per-pass costs. `FormulaIcons` is a bounded cache:

- key `(source, pixelSize, style)`, LRU, capacity 512;
- one build per key per pass, memoized through `MemoizedTitleMetrics`;
- failures (`ParseException`, `InvalidSymbolTypeException`, or any other `RuntimeException` from the engine) are
  cached as failures, so a broken macro costs one attempt, not one per pass;
- a per-pass build budget of `MAX_NEW_ICONS_PER_PASS = 200`. A pass that exhausts the budget treats every
  remaining math run as unrenderable, so D3's degraded form is used for those labels in that pass, and records
  that the budget was hit.

**The follow-up pass.** A deferred label cannot be recovered by simply repainting: `ScreenLabelPlacementCache
.place` returns `lastPlacements` whenever its `Key` matches (`ScreenLabelPlacementCache.java:17-24`), and a
repaint of an unchanged canvas produces an identical key. The design therefore names the mechanism:

- `ScreenLabelPlacement` owns the `FormulaIcons` instance and exposes `boolean budgetExhaustedInLastPass()`;
- `ScreenLabelPlacementCache.place` consults it after a computation and, when true, sets a one-shot
  `followUpPending` flag and includes a `passEpoch` component in its `Key` that it increments, so the very next
  `place` call for the same state is a cache miss;
- `GraphCanvas.labelsFor` calls `labelPlacementCache.place(...)` and then, if `takeFollowUpRequest()` returns
  true, posts a single `repaint()`.

The result is deterministic and testable: at most one extra placement pass per budget exhaustion, no timers, no
threads, no partial frames. The budget matters because a first pass over a large formula-heavy map is otherwise
unbounded work on the EDT. Its value is a starting point that the section 8 acceptance measurement must confirm
or replace with a measured number; it is not presented as a measured threshold.

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
  `META-INF/COPYING` provenance and the font licences, copied the same way the GraphStream notice is;
- keep `!attributes.getValue('Import-Package')` and `!attributes.getValue('Export-Package')`, which is the
  reason this is a bundled `lib` rather than an OSGi import from `freeplane_plugin_latex`.

The alternative — exporting `org.scilab.forge.jlatexmath` from the LaTeX plugin and importing it here — is
rejected: this bundle's deliberate isolation (`bundleImports = 'nothing.*'`) exists so the graph view does not
depend on which other plugins are installed, and a graph view that cannot label nodes when the LaTeX plugin is
absent would be a new failure mode for a core feature.

### 4.8 What users will see

Stated once, because it is user-visible and section 3.3 removed the claim that both views behave alike:

- A short formula inside a title renders for the first time.
- A formula that the engine cannot parse shows its source text, exactly as today, and stays clickable and
  readable.
- A title that does not fit its slot shows raw source and may be ellipsized, as today.
- A formula that needs `latex_macros` renders in the map but not in the graph until the follow-on in section 10.
- The hover bar, the sidebar, the accessible name, and search continue to use the plain source text.

## 5. Invariants

1. A title whose `TitleText` has no math produces byte-identical `drawString` calls and identical placement
   geometry to the baseline at the same request.
2. `SafeNodeLabel.displayText()`, `fullText()`, `equals` and `hashCode` are unchanged, so projection identity,
   deduplication, sidebar text, hull padding, and the accessible name are unchanged.
3. No math run is omitted from the label, and no rendered formula is partially painted. In a truncating rung the
   whole title is drawn in degraded form, so a formula contributes its complete source text to the row that
   `truncateTo` may then ellipsize.
4. Every measured box is finite and positive; a formula whose height exceeds `2.0 ×` the plain line height of
   its font is treated as unrenderable and degrades through section 4.4 rule 3.
5. For a **non-forced** label, every placed label respects `slotMaxWidth` for its slot. A forced label may
   exceed it through `baseSlot`, exactly as `keepsAnUnchangedForcedOverCapFace` pins today (section 3.4);
   formula rendering does not narrow that exception and does not widen the cap.
6. Placement and painting agree: the painter never re-measures with a different metric than the placer used.
7. Icon construction is bounded: at most one build per `(source, size, style)` per pass, at most
   `MAX_NEW_ICONS_PER_PASS` builds per pass, and at most one scheduled follow-up pass per exhaustion.
8. The bundle gate passes with the new jar: pinned checksums, exact `Bundle-ClassPath`, no
   `Import-Package`/`Export-Package`, class major `<= 52`.

## 6. Exact change set

| File | Change |
| --- | --- |
| `projection/input/TitleText.java` | new value type with `Plain` and `Math` runs |
| `projection/input/TitleTextParser.java` | new total, deterministic parser (section 4.2) |
| `projection/input/SafeNodeLabel.java` | add derived `title()`; existing constructor and equality unchanged |
| `adapter/SafeNodeLabelExtractor.java` | compute the math-signal flag from `NodeStyleModel.getNodeFormat` and the `\latex`/`\unparsedlatex` prefix, then pass it to the parser |
| `canvas/title/Box.java` | new measured box (`width`, `height`, `ascent`, `descent`) |
| `canvas/title/TitleMetrics.java`, `AwtTitleMetrics.java`, `MemoizedTitleMetrics.java` | new measurement seam, including the unrenderable verdict of section 4.3 |
| `canvas/title/FormulaIcons.java` | new bounded icon cache with failure memoization and per-pass budget |
| `canvas/title/TitleTruncation.java` | new rendered/degraded form selection and degraded-row truncation (section 4.4) |
| `canvas/title/TitlePainter.java` | new painter with the explicit base/world coordinate contract (section 4.5) |
| `canvas/ScreenLabelPlacement.java` | thread `TitleText` + `TitleMetrics` through the measuring sites; rendered form for non-truncating rungs, degraded form for truncating rungs; plain behaviour unchanged; expose the budget signal |
| `canvas/ScreenLabelPlacementCache.java` | `passEpoch` in `Key`, one-shot `followUpPending`, `takeFollowUpRequest()` |
| `canvas/PlacedLabel.java` | carry the `TitleText` alongside `text()`; no accessor removed |
| `canvas/GraphPainter.java` | capture `baseTransform`, pass it to `TitlePainter`, replace `drawCentered`; label colour and dimming unchanged |
| `canvas/GraphCanvas.java` | one line in `labelsFor`: post a single `repaint()` when `takeFollowUpRequest()` is true |
| `freeplane_plugin_graph/build.gradle` | add the `lib` dependency, update `verifyGraphBundle` per section 4.7 |
| `src/main/resources/META-INF/NOTICE.jlatexmath.txt` | new licence provenance file |

No file outside `freeplane_plugin_graph` changes, and no user-facing documentation file exists in the
repository to update: `doc/` holds only the two edge-node-crossing study directories. The user-visible
behaviour is recorded in section 4.8, and the wiki update is the out-of-scope follow-on recorded in section 10.

## 7. Failure modes

| Failure | Behaviour |
| --- | --- |
| unknown macro (`\leslant`) | engine throws; the run is unrenderable; the title uses degraded form; the failure is cached |
| unbalanced `$` | parser produces literal `Plain` text; no engine call |
| formula wider than the slot | rendered form yields no candidate; the truncating rungs use degraded form, then `truncateTo` |
| formula taller than `2.0 ×` line height | unrenderable; degraded form |
| engine returns non-finite or non-positive metrics | unrenderable; invariant 4 keeps the box contract |
| budget exhausted mid-pass | remaining math runs unrenderable this pass; exactly one follow-up pass scheduled |
| icon cache eviction under memory pressure | rebuild on demand; correctness unaffected |

## 8. Test strategy

New JUnit 4 + AssertJ tests, named in the house style. Each invariant in section 5 is listed with the test that
fails if the invariant is removed.

| Invariant | Falsifying test |
| --- | --- |
| 1 | `GraphCanvasPaintShould.plainTitlesDrawTheSameStringsAsTheBaseline` |
| 2 | `SafeNodeLabelExtractorShould` existing cases plus `safeNodeLabelEqualityIgnoresTheDerivedTitle` |
| 3 | `GraphCanvasPaintShould.unrenderableFormulaDrawsItsCompleteRawSource` and `truncatingRungNeverPaintsAPartialIcon` |
| 4 | `AwtTitleMetricsShould.rejectsNonFiniteAndNonPositiveIconMetrics` and `rejectsFormulaTallerThanTwiceTheLineHeight` |
| 5 | `ScreenLabelPlacementShould.formulaLabelRespectsItsSlotLimit` and `ScreenLabelLiveTitleRefreshShould.keepsAnUnchangedForcedOverCapFace` (existing) |
| 6 | `GraphCanvasPaintShould.paintedRunOriginsMatchTheMeasuredBox` |
| 7 | `FormulaIconsShould.buildsEachKeyOncePerPass`, `exhaustsItsBudgetThenRequestsOneFollowUp`, and `ScreenLabelPlacementCacheShould.followUpPassIsACacheMissExactlyOnce` |
| 8 | `verifyGraphBundle`, plus `graphBundleGateFailsWhenTheFormulaJarIsDropped` as a deliberate mutation of the pinned set |

Additional new tests:

- `projection/input/TitleTextParserShould` — plain text is one run; single and double delimiters; escaped `\$`;
  unmatched and unbalanced delimiters yield one literal `Plain`; `wholeTextIsMath` yields one `Math` run;
  `\text{$...}$` does not close early; empty-run dropping.
- `canvas/title/AwtTitleMetricsShould` — a plain title measures exactly as
  `font.getStringBounds(displayText, SCREEN_FRC)`; a math title measures as the run sum; ascent/descent are the
  maxima; height is `ascent + descent`.
- `canvas/title/TitleTruncationShould` — rendered form is chosen for a fitting title; degraded form for an
  overflowing one; `truncateTo` is applied to the degraded row; a plain-only title behaves exactly as today.
- `canvas/title/TitlePainterShould` — a plain run draws at the world-space `drawString` coordinates; a math run
  paints at the device anchor with `baseTransform` preserved; a following plain label still draws in world
  space after a math run; a non-identity incoming transform (`scale(1.25, 1.25)`) positions and sizes the icon
  consistently with the measured box.
- `canvas/title/FormulaIconsShould` — one build per `(source, size, style)` per pass; a failing source is
  attempted once and then served from the failure cache; the budget stops builds at the limit; the cache is LRU
  bounded.
- `canvas/ScreenLabelPlacementShould` (extend) — a math label is measured by the icon, reaches `HOVER_ONLY`
  through the same ladder a plain over-long title does, and never exceeds `slotMaxWidth` when not forced.
- `canvas/GraphCanvasPaintShould` (extend) — plain-title `drawString` capture is identical to the baseline; a
  math run paints at the shared baseline; the dimming composite applies to formula runs; a forced hover label
  paints its formula; an unrenderable formula paints its complete raw source.
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
rendered by the real canvas, showing a rendered formula for the renderable one and readable raw source for
`\leslant`, plus the first-pass timing that confirms or replaces `MAX_NEW_ICONS_PER_PASS`.

## 9. Risks

1. **Bundle-gate drift.** The gate is the only thing keeping the graph plugin's dependency set honest; changing
   four assertions in one commit without a test that fails on each of them individually is the main regression
   risk. The change adds one assertion per pinned property, not one combined check, and section 8 names the
   mutation test for the pinned set.
2. **Two coordinate spaces in one painter.** The base/world contract of section 4.5 is the part most likely to
   regress silently, because a wrong choice only shows on a scaled display. `TitlePainterShould` must include
   the non-identity incoming transform case and a following plain label.
3. **Wholesale degradation in a truncating rung.** A slightly over-wide label loses all of its icons at once
   rather than keeping the leading ones. This is deliberate (section 4.4) and the alternative is cutting a
   control sequence. It is listed because users will notice the visual discontinuity between the `FULL` and
   `TRUNCATED` rungs of a formula-heavy label.
4. **Engines differ from the map.** A map node renders its LaTeX with `\text{...}` wrapping, display style,
   interline spacing and `latex_macros`; a label renders a formula body in text style on a single line, without
   macros. The design does not reuse `TeXText`, because wrapping a title in `\text{...}` would render `$...$` as
   literal text. Section 4.8 states the visible consequences.
5. **`\leslant` is unrepresentable here.** The user's own screenshot and the empty `latex_macros` configuration
   mean the first visible result is a partially degraded label. The fallback must look intentional rather than
   broken, which is why the degraded form keeps the complete source text.
6. **Performance on formula-heavy maps.** Unmeasured in this environment (section 3.5). The budget is a
   structural guard, not a measured threshold.
7. **Truncation semantics vs. the live-title-refresh contract.** `ScreenLabelLiveTitleRefreshShould` pins that a
   retained label is only kept while its `sourceText` is unchanged. A formula whose rendering changes without a
   source change (a theme or font change) must go through the same font/eligibility conditions that contract
   already defines, not through a new rule.

## 10. Out of scope

- MathJax, KaTeX, or any JavaScript engine. The canvas has no browser, and the measured-geometry contract needs
  numeric width and height before painting.
- Display-style formulas, multi-line formulas, `\begin{align}`, and formula editing in the graph view.
- Widening `VERTICAL_MAX_WIDTH`/`HORIZONTAL_MAX_WIDTH`/`DIAGONAL_MAX_WIDTH` (D4).
- Change to enclosure-label geometry: `GeometryTextMetrics` keeps measuring `displayText()`, because raw source
  is a conservative upper bound and switching it would move every hull fixture.
- The Greek and Cyrillic font packs, and text-mode Greek/Cyrillic inside `\text{...}`.
- Rendering formulas on edges, pins, hulls, or the hover bar.
- **Follow-on, not this change:** honouring the `latex_macros` resource property in graph labels. The seam it
  would use is a `FormulaMacros` provider supplied by `adapter` (the only layer allowed to read Freeplane
  resources) and included in the `FormulaIcons` cache key, so a macro edit invalidates correctly.
- **Follow-on, not this change:** the user-wiki text for section 4.8.

## 11. Accessibility and identity

Unchanged by construction, and asserted by invariant 2: the accessible name and description keep using the
plain source text, the hover bar and `ContributorInspector` keep showing `displayText()`, and projection identity
keeps comparing `fullText` and `displayText`. A screen reader therefore continues to read `$N$ is well-ordered by
$\in$` verbatim, which is the same string it reads today.

## 12. Questions for the reviewer

1. Is the section 4.4 split — rendered form for non-truncating rungs, wholesale degraded form for truncating
   rungs — the right reading of D3 and D4, given that the alternative either drops a formula or cuts a control
   sequence?
2. Is the follow-up mechanism in section 4.6 (a `passEpoch` in the cache key plus a one-shot flag) the smallest
   correct recovery, or should `ScreenLabelPlacementCache` expose an explicit `invalidate()` instead?
3. Is `baseTransform` captured in `GraphPainter.paint` the right carrier for the device transform, or should
   `TitlePainter` derive it from the current transform by inverting the world transform instead?
4. Is `MAX_NEW_ICONS_PER_PASS = 200` the right structural guard before measurement, or should the first
   implementation ship without a budget and rely on the cache alone?
5. Section 3.5 records that no JDK or Gradle exists in this environment. Which machine runs the section 8
   commands, and who confirms the first-pass timing and the acceptance screenshot?
