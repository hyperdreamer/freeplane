# Graph workspace — LaTeX formulas in node title labels

Task identifier: `2026-11-03-graph-latex-titles`

Status: **design draft, revision 9, for the design-continuation gate.** The user answered the collaborative design gate with
"as proposed", accepting the four decisions recorded in section 2. Section 12 lists the questions this revision
puts to the reviewer.

Baseline: `plugin/graph-workspace` at `fd7488d5ae7f92bcdb3f70fc76b09f6e881aacc0`. All investigation ran in the PM
integration worktree; the user's delivery checkout was read only and never built.

Revision history:

- r1 — first complete design.
- r2 — applied review round 1 (F1–F8).
- r3 — applies review round 2. F2 and F4 were judged not resolved and are corrected here:
  invariant 5 is now scoped to ladder candidates (the retained face after demotion legitimately exceeds the cap),
  and the math-run transform now applies the base transform exactly once. Four new blocking findings are
  answered: N1 gives budget deferral its own recorded state so a retained truncated face cannot pin a deferred
  label; N2 separates the compact measurement cache from the icon cache and states a convergence and termination
  contract; N3 makes the placer produce a resolved row that the painter consumes without re-deciding anything;
  N4 corrects the baseline unit error (`getBaseLine()` is a ratio, not pixels). N5 adds the test and evidence
  files to the change set; N6 swaps the two font-pack sizes.
- r4 — applies review round 3: F2's remaining test wording is corrected; N2 (round 2) is answered with a deferred
  queue so cache capacity can never block progress; N3 (round 2) removes the paint-time icon lookup that
  contradicted the resolved-row contract; and the four round-3 findings are applied — the measurement key now
  includes the logical font, the plain-run font derivation keeps the existing `Math.max(1.0f, …)` clamp, the "no
  file outside the module changes" claim is narrowed to production source, and the transform test asserts an
  absolute expected position instead of repaint idempotence.
- r5 — removes the fixed 8192-entry capacity from the measurement memo and scopes it to the
  projection generation instead. Review of the derived specification showed that a fixed capacity reintroduces a
  regime where a drained key can be evicted and return to the queue, which makes the termination bound in section
  4.6 false; that section's claim that "cache capacity therefore cannot stall the queue" was therefore not true of
  the r4 mechanism. With no eviction inside a generation the queue drains monotonically, the `ceil(N / budget)`
  bound holds as written, and `FORMULA_MEASUREMENT_STALL` becomes a defensive diagnostic rather than a reachable
  steady state. Nothing else changes: the icon cache stays fixed at 512 because icons are rebuildable, and the
  budget, retention rule, ladder and painting contract are untouched.
- r6 — applies the focused review of r5. The follow-up condition is now "the pass deferred at
  least one key" instead of "the queue was non-empty when the pass began", which fixes the reachable case where a
  first pass discovers more work than its budget and nothing follows it up; the stall condition is correspondingly
  "a pass that deferred work measured nothing". Deferral is specified as an eventual promise bounded by the queue,
  not as a next-pass promise, because a label in the final batch of a three-batch queue is deferred twice. Icon
  eviction now rebuilds the icon from the cached measurement instead of re-enqueueing the key, which r5's
  no-reentry rule forbids. The memory paragraph names the form flag in the key, counts icons retained by live
  rows, and states plainly that a pathological projection can exhaust the heap rather than implying a bound.
- r7 — applies the second focused review's consistency findings: the follow-up and stall
  predicates are written as one formula with stall precedence and invariant 7 now mirrors it; the pass-count
  terminology distinguishes measurement passes from deferring passes and defines N as "unmeasured after the
  discovering pass"; invariant 8 states that a terminal measurement is not a promise of typeset rendering, since
  an over-width formula still degrades; the invariant's test references name the section 8 rows instead of the
  retired T-identifiers; and the risk section no longer claims an idempotence test.
- r8 — closes the three residual contradictions the verification pass found in r7: the budget rule
  no longer excludes the discovering pass from being a deferring pass and defines `T` (all keys in the generation)
  separately from the `N` used in the follow-up bound; the deferral and terminal-measurement promises are
  qualified by the two states that legitimately end a queue, the stall diagnostic and a projection generation
  change; and invariant 7 names the three cache tests that falsify it instead of miscounting them.
- r9 (this revision) — authorises the paint-ready row. The specification showed that a screen-pixel row cannot
  reproduce the existing plain drawing, because the plain paint font is clamped to one point
  (`GraphPainter.java:281-282`); a screen-pixel row scaled by zoom would have to reproduce a world-unit measurement
  that the clamp makes non-linear in zoom. `TitleRow` therefore carries paint-unit geometry and, for each plain
  run, the derived font it was measured with, and `TitlePainter` drops its `Font` parameter accordingly.
  `PlacedLabel`'s screen-pixel geometry, the slot caps, the ladder, the parser, the queue and the bundle gate are
  unchanged.

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

D3 and D4 together are the whole degradation contract, and section 4.4 is its single normative statement: a label
that overflows shows raw source, and that raw row may be ellipsized exactly as a long plain title is today.
"Never blank" forbids the empty label; it does not forbid the ellipsis that D4 explicitly allows.

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
- `window.ContributorInspector` shows the title in the sidebar, `canvas.AccessibleGraphCanvas` derives the
  accessible name from `node.label().fullText()` (`AccessibleGraphCanvas.java:473`), and
  `canvas.GraphSearchModel` searches `fullText()`.

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
- jar sizes are 669,575 bytes (core), 188,924 (Greek) and 185,652 (Cyrillic): **1,044,151 bytes** for all three;
- `org/scilab/forge/jlatexmath/TeXSymbols.xml` defines `leqslant`; it does **not** define `leslant`, so the
  screenshot's `\leslant` is not renderable by this engine — D3's raw-source degradation is a live requirement,
  not a hypothetical. The user's Freeplane configuration defines no `latex_macros` override, so nothing in this
  installation supplies the macro either;
- `TeXIcon` exposes `getIconWidth()`, `getIconHeight()`, `getBaseLine()`, `setForeground(...)` and
  `paintIcon(Component, Graphics, int, int)`, and `TeXFormula.new TeXIconBuilder()` builds an icon at a chosen
  pixel size, style and colour;
- the jar carries `META-INF/COPYING`, `META-INF/LICENSE`, and the font licences under
  `org/scilab/forge/jlatexmath/fonts/licences/`.

`freeplane_plugin_latex/src/main/java/org/freeplane/plugin/latex/TeXText.java:31-46` is the in-repo reference for
the build call, and it also shows that the map path prepends the `latex_macros` resource property. **The graph
path in this design does not honour that property** (section 10 records the follow-on). Until then the map and
the graph can disagree about macros the engine does not know.

### 3.4 The engine's baseline is a ratio, and paint colour is icon-local

Two facts from disassembling the shipped `TeXIcon.class` decide the design of sections 4.3 and 4.5, so they are
recorded with their evidence rather than assumed:

- `getBaseLine()F` computes `(float)(above / (above + below))` where `above = box.getHeight()*size + c +
  insets.top` and `below = box.getDepth()*size + c + insets.bottom`, ending in `ddiv`, `d2f`, `freturn`. It
  returns a **ratio of the above-baseline extent to the total extent**, not a pixel distance. Pixel ascent is
  therefore `getBaseLine() * getIconHeight()`, and descent is the remainder.
- `paintIcon(Component, Graphics, int, int)` casts the graphics to `Graphics2D` and chooses its colour as:
  `this.foreground` when set, else `component.getForeground()` when the component is non-null, else
  `Color.black`. A cached icon can therefore be recoloured with `setForeground(...)` immediately before painting,
  which is safe because painting is confined to the EDT.

### 3.5 The width cap has two documented exceptions

The cap is a property of the ladder, not of every placed label, and two existing tests pin that:

- **Forced labels.** `placeNode` falls back to `baseSlot` for a forced label (`ScreenLabelPlacement.java:433-448`),
  and `baseSlot` (`:658-668`) measures the full text and returns it without any width check.
  `ScreenLabelLiveTitleRefreshShould.keepsAnUnchangedForcedOverCapFace` (`:618-627`) asserts a forced `FULL_NEAR`
  label of width `274.130005`, greater than `VERTICAL_MAX_WIDTH = 200`, with `forcedAtBaseSlot() == true`.
- **Retained faces, including after demotion.** `keepsAnOverCapFaceAfterDemotionAndOnTheNextReplay` (`:768-787`)
  asserts that a label that was forced keeps its over-cap face after it stops being forced: `forced()` is false,
  `forcedAtBaseSlot()` is false, `width() > 200`, and the drawn text is unchanged. A retained face is not
  re-checked against the cap.

The design must therefore scope any width invariant to width-checked ladder candidates, which is what section 5.5
now does.

### 3.6 Environment prerequisite for build and test evidence

The checkout this design was written in has **no JDK and no Gradle**: `/usr/lib/jvm` holds two JRE-only installs,
`javac` is absent, `gradle` is not on `PATH`, and the network is unreachable. `AGENTS.md` specifies
`~/.sdkman/candidates/java/21.0.8-zulu` plus `gradle`, and neither exists here. Every test command in section 8
must therefore run on a machine that has them; this is recorded as a question in section 12, not as a design
assumption. The repository's own CI (`.github/workflows/ci.yml:15-16`) runs `ubuntu-latest` with Java 21, so
`gradle :freeplane_plugin_graph:test` and `verifyGraphBundle` have a designated machine; only the headful
acceptance run is unassigned.

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
two-dollar case above. Brace-depth tracking exists only to stop a `$` inside `\text{$...$}` from closing the
formula early.

### 4.3 Measurement

Three types, with a strict division of labour: **measurement produces numbers**, **icons are a cache**, and
**the resolved row is what the painter consumes**.

```java
// canvas.title — numbers only, cacheable, never holds an icon
public final class MeasuredTitle {
    public enum Availability { RENDERED, DEGRADED }
    Availability availability();
    List<MeasuredRun> runs();                 // offsets and extents in screen px
    double width(), height(), ascent(), descent();
}

// canvas.title — the pass-scoped icon service
public final class FormulaIcons {
    TeXIcon icon(String source, int px, int style);   // null when the engine rejects the source
}
```

Rules:

- A title with no math measures **exactly** as today: `font.getStringBounds(plainSource, SCREEN_FRC)`, same
  `Rectangle2D` — a pure-plain label must be pixel-identical to the baseline.
- A title with math measures as the horizontal sum of its runs; `ascent` is the maximum of the plain font's
  ascent and every formula icon's pixel ascent; `descent` is the maximum of the plain descent and every icon's
  pixel descent; `height` is `ascent + descent`.
- **Pixel ascent and descent.** Icons are built at `Math.round(font.getSize2D())` pixels and
  `TeXConstants.STYLE_TEXT`. Because `getBaseLine()` is a ratio (section 3.4), a run's pixel ascent is
  `icon.getBaseLine() * icon.getIconHeight()` and its pixel descent is `icon.getIconHeight() - ascent`. The
  design does not use `getBaseLine()` as a distance anywhere.
- **Availability.** A title is `DEGRADED` when any math run is unrenderable. A run is unrenderable when the
  engine throws, when the icon's metrics are non-finite or not strictly positive, or when the icon height exceeds
  `2.0 ×` the plain line height of the font. `MeasuredTitle` never carries a non-finite number, and
  `availability()` is the only channel for the unrenderable verdict.
- **Memoization.** `MeasuredTitle` is memoized by `(source-key, logical font, form flag)` in a map scoped to the
  current projection generation and cleared when that generation changes. `logical font` is the full `Font`
  identity — family, style and `getSize2D()` — not the rounded icon pixel size. The plain runs' widths and the
  `2.0 ×` line-height threshold both depend on that font, and `LabelFonts.of(Font, Font, Font)` accepts arbitrary
  faces, so a key without it would return one face's numbers for another. No icon is retained in the measurement
  cache. The *icon* key stays narrower, `(source, px, style)`, because an icon depends only on the formula and its
  pixel size (section 4.6).

`ScreenLabelPlacement` keeps its package-visible `screenBounds` for plain-only paths and gains the injected
measurement service for the title-measuring call sites. `screenBounds(String, Font)` and `textWidth(String, Font)`
stay package-visible and unchanged so the existing placement tests keep compiling and keep asserting the same
numbers.

### 4.4 Truncation and degradation ladder

`ScreenLabelPlacement.truncateTo(String, Font, double)` is unchanged, and remains the only truncator for plain
titles. Math titles are resolved by one normative rule set, which is D3 and D4 applied to the existing rungs:

1. **Rendered form** — the title as runs. Used by every non-truncating rung (`FULL_NEAR`, `FULL_DISPLACED`,
   `DENSE_NEAR`, `DENSE_DISPLACED`). Such a rung yields a candidate only if the measured title is `RENDERED` and
   its width is within `slotMaxWidth(slot)`; otherwise that rung yields no candidate, exactly as an over-wide
   plain title yields no candidate today.
2. **Degraded form** — every `Math` run replaced by a `Plain` run over its `rawSource`. Used by the truncating
   rungs (`TRUNCATED_*`) and as the final fallback. A truncating rung measures the degraded row and, if it
   exceeds the limit, applies the existing `truncateTo` character loop to that plain text.
3. **Unrenderable title** — when `availability()` is `DEGRADED` in the measured cache, the rendered form does not
   exist and only the degraded form is offered, through rungs 2.

Two states are recorded on the placed label and are *not* the same thing:

- `deferredForBudget` — the formula was not measured because the pass budget ran out (section 4.6). It is
  transient, but its promise is **eventual, not next-pass**: a deferred key is measured in a later batch of the
  same queue, unless the run stalls (zero budget, or a broken engine that cannot produce a measurement) or the
  projection generation changes, either of which ends that queue. A label in the final batch of a three-batch
  queue is deferred twice.
- `truncated` — the drawn text ends with the ellipsis, as today.

Consequences, stated plainly because they are visible to users:

- A formula is never omitted and a rendered formula is never partially painted: the run is either an icon or its
  complete source text.
- A row that overflows in every non-truncating rung degrades to raw source and may then be ellipsized. For the
  user's `Theorem: Equivalence of $\leqsla…` case this reproduces today's visible text, which is the honest
  outcome: the slot is narrower than the source, and D4 accepts that.
- The reference case `Theorem: $\bigcup\alpha\leqslant\alpha$` fits the 200 px vertical slot in rendered form
  (roughly 150 px), so it renders; the same title in a 130 px horizontal slot degrades.

The ladder in `ScreenLabelPlacement.LADDER` is untouched. Formula rendering changes only *what* a rung measures
and *what* a truncating rung draws.

### 4.5 The resolved row, and painting

The placer produces a `TitleRow` — the resolved, measured row that the painter consumes as data:

```java
// canvas.title
public final class TitleRow {
    public enum Form { RENDERED, DEGRADED }
    Form form();
    List<RunRow> runs();            // left-to-right, offsets in paint units
    double width(), height(), ascent(), descent();   // paint units
    boolean deferredForBudget();
}
public interface RunRow { double x(), width(); }
public final class PlainRunRow implements RunRow { String text; Font worldFont; double ascent, descent; ... }
public final class MathRunRow implements RunRow { String source; TeXIcon icon; double ascent, descent; ... }
```

**Paint units, not screen pixels.** `PlacedLabel` keeps its existing screen-pixel `width`/`height`, which the
placement maths and the slot caps use unchanged. `TitleRow` is a separate *paint-ready* structure whose numbers are
in the coordinate space the painter draws in, because the plain paint font is
`deriveFont(Math.max(1.0f, size / zoom))` (`GraphPainter.java:281-282`) and that clamp means no screen-pixel row can
reproduce today's plain drawing at high zoom. `PlainRunRow` therefore carries the derived font it was measured with,
and a plain run's ascent, descent and baseline offset come from that font's `LineMetrics` — including its leading —
so a plain-only row lands exactly where `drawCentered` puts it today.

`PlacedLabel` carries the `TitleRow` alongside its existing fields. A `MathRunRow` holds a **strong reference to
its `TeXIcon`**, so the row stays complete for as long as it is placed and icon-cache eviction cannot affect it.
**The painter consumes the row and never re-measures, never re-decides degradation, never chooses the form, and
never consults `FormulaIcons`.** That makes invariant 6 structural rather than a promise.

`GraphPainter.drawCentered` is replaced by
`TitlePainter.paint(Graphics2D g, TitleRow row, double worldX, double worldY, double zoom, Color color,
AffineTransform baseTransform)`. The painter takes no `Font`: every font it needs is carried by the row, so it
cannot measure anything.

**Coordinate contract.** Two transforms exist and the design names both:

- the **base transform** `B` is the `Graphics2D` transform `GraphPainter.paint` receives from the component. It is
  not assumed to be the identity: a scaled display or an offscreen image can arrive already transformed;
- the **world transform** `W` is what `GraphPainter.paint` then concatenates for `GraphViewport`. The transform at
  paint time is therefore `T = B·W`.

`GraphPainter.paint` captures `B = graphics.getTransform()` **before** applying `W` and passes it down. Row
offsets and extents are in screen pixels, so they convert to world units by dividing by `zoom`; that is why the
painter takes `zoom` explicitly instead of inferring it from a derived font size.

- `PlainRunRow` draws with `graphics.drawString` after
  `graphics.setFont(logicalFont.deriveFont(Math.max(1.0f, size / (float) zoom)))`, at `worldX + run.x()/zoom`.
  The `Math.max(1.0f, …)` clamp is part of today's behaviour (`GraphPainter.java:281-282`) and is preserved
  verbatim, so plain labels are unchanged even at zoom values where `size / zoom` would fall below one point.
- `MathRunRow` paints the icon exactly once through the base transform:
  1. `deviceAnchor = T.transform(worldAnchor)` — screen position of the run;
  2. `componentAnchor = B⁻¹.transform(deviceAnchor)` — the same point in component coordinates;
  3. `copy = graphics.create(); copy.setTransform(B); copy.translate(componentAnchor.x, componentAnchor.y);
     rowIcon.setForeground(color); rowIcon.paintIcon(null, copy, 0, 0); copy.dispose();`

  Step 2 is the correction from review round 2: painting under `B` while translating by the *device* anchor
  would have applied `B` twice. Translating by `componentAnchor` applies `B` exactly once, so a scaled display
  keeps correct icon size and position.
- Colour comes from `setForeground`, which section 3.4 shows is what `paintIcon` honours. Dimming is unaffected:
  the composite `paintLabels` sets is inherited by `graphics.create()`.

`PlacedLabel.text()` keeps returning the drawn plain string, so `GraphCanvasPaintShould.texts(...)`,
`ScreenLabelPlacementShould`, the hover bar, and the accessible name all keep working. For a `RENDERED` math
label, `text()` is the source string (what the user typed); for a `DEGRADED` label it is the degraded text,
which is exactly what is drawn.

### 4.6 Measurement cache, icon cache, budget, and convergence

Two caches with different jobs, because one cannot do both:

| Cache | Contents | Capacity | Purpose |
| --- | --- | --- | --- |
| measurement cache | `MeasuredTitle` numbers + availability, no icons | none fixed; cleared when the projection generation changes | supplies cached numbers; progress is tracked separately by the deferred queue |
| icon cache | `TeXIcon` per `(source, px, style)`, plus cached failures | 512, LRU | supplies icons during measurement; a placed row keeps its own reference afterwards |

Rules:

- The **build budget** `MAX_NEW_ICONS_PER_PASS = 200` counts *keys measured* per placement pass — not paints,
  not cache hits, and not queue entries that are already served from the measurement cache. Let `T` be the number
  of distinct `(title, font)` keys in the generation. A generation therefore needs `ceil(T / 200)` measurement
  passes in total (`0` when `T = 0`): the discovering pass measures up to 200 and defers any remainder, and each
  later pass measures up to 200 more. A pass that defers work is a deferring pass, so the discovering pass is one
  whenever `T > 200`.
- When the budget is exhausted, the remaining math titles are measured as `DEGRADED` **with
  `deferredForBudget = true`**, which section 4.4 distinguishes from a genuine unrenderable verdict.
- **Retention must not pin a deferred face.** `ScreenLabelPlacement.retained` (`:451-500`) keeps a remembered
  placement when its `sourceText` and font are unchanged, and re-draws it with
  `rememberedRow.truncating ? truncateTo(...) : source`. A deferred label remembered on a truncating rung would
  therefore stay degraded forever. The design adds one rule to `retained`: **a remembered placement whose
  `TitleRow.deferredForBudget()` is true is not retained**, so the next pass recomputes it. Ordinary plain-label
  retention is untouched, which keeps `ScreenLabelLiveTitleRefreshShould` meaningful.
- **Progress is tracked by a queue, not by the cache.** `ScreenLabelPlacement` keeps a deferred queue of keys
  that belong to the current projection generation and still need measurement. A pass drains the queue in order,
  measuring up to `MAX_NEW_ICONS_PER_PASS` keys and removing each measured key. The measurement cache has no
  fixed capacity and no eviction within a generation, so a drained key can never return to the queue, and cache
  size can never stall the drain.
- **Convergence and termination.** `ScreenLabelPlacementCache.place` reports `budgetExhaustedInLastPass()`. The
  cache sets a one-shot `followUpPending` flag and `GraphCanvas.labelsFor` posts exactly one `repaint()`. The
  follow-up is a cache miss because the cache's `Key` includes a `passEpoch` that the exhaustion increments.
- **Scheduling and stall.** The chain is driven by one predicate with stall precedence:

  ```text
  followUp = deferredThisPass > 0 && measuredThisPass > 0
  stall    = deferredThisPass > 0 && measuredThisPass == 0
  ```

  A follow-up is therefore scheduled whether or not the queue held work when the pass began, so a first pass
  that discovers more work than its budget is followed up. `stall` is `FORMULA_MEASUREMENT_STALL`: a defensive
  diagnostic reachable only with a zero budget or a broken engine, because a positive budget with no eviction
  inside a generation always measures at least `min(budget, deferred count)` keys. With no re-entry within a
  generation, a stable generation with N keys still unmeasured **after** the discovering pass completes in
  `ceil(N / budget)` further passes.
- Memory is projection-dependent rather than constant: the measurement cache holds one entry per distinct
  `(title, font, form)` key in the generation, the queue holds at most that many pending keys, and each live
  `PlacedLabel` row additionally retains the icons of its formula runs, so the 512-entry icon cache bounds
  *reusable* icons rather than total live icons. A pathological projection can therefore exhaust the heap. The
  design states that risk and claims no heap-safety bound, because any such bound would have to reintroduce the
  measurement eviction that r5 removed.

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
   deduplication, sidebar text, hull padding, accessibility, and search are unchanged.
3. No math run is omitted from the label, and no rendered formula is partially painted. In a truncating rung the
   whole title is drawn in degraded form, so a formula contributes its complete source text to the row that
   `truncateTo` may then ellipsize.
4. Every measured box is finite and positive; a formula whose pixel height exceeds `2.0 ×` the plain line height
   of its font is `DEGRADED` and resolved through section 4.4 rule 3.
5. **Every candidate produced by the ladder is within `slotMaxWidth` for its slot.** A forced label may exceed it
   through `baseSlot`, and a retained face may remain over the cap after demotion, exactly as
   `keepsAnUnchangedForcedOverCapFace` and `keepsAnOverCapFaceAfterDemotionAndOnTheNextReplay` pin today
   (section 3.5). Formula rendering neither narrows nor widens those exceptions.
6. The painter consumes the resolved `TitleRow`: it never re-measures, never re-decides availability, never
   chooses between rendered and degraded forms, and never reads the icon cache.
7. Measurement progress is bounded and terminates without a capacity cliff: at most `MAX_NEW_ICONS_PER_PASS` keys
   are measured per pass, no measured key re-enters the queue within a generation, and the follow-up predicate of
   section 4.6 holds exactly — `followUp = deferred > 0 && measured > 0`, with `stall` taking precedence when
   `deferred > 0 && measured == 0`. A stable generation with N keys still unmeasured after the discovering pass
   completes in `ceil(N / budget)` further passes. Falsified by `convergesAcrossFollowUps`,
   `convergesBeyondAnyFixedCacheSize` and `stallsOnlyWhenAPassThatDeferredWorkMeasuresNothing`.
8. A budget-deferred label is never retained, and wherever the queue is allowed to finish — that is, unless the
   run stalls or the projection generation changes — it reaches a **terminal measurement** within the queue's
   remaining batches, not necessarily on the next pass. Terminal measurement is not a promise that the label is
   drawn typeset: an engine rejection makes the terminal result degraded, and an accepted formula whose width
   exceeds the slot is still degraded by section 4.4 rule 2. The promise is that the label is no longer deferred.
9. The bundle gate passes with the new jar: pinned checksums, exact `Bundle-ClassPath`, no
   `Import-Package`/`Export-Package`, class major `<= 52`.

## 6. Exact change set

Production files:

| File | Change |
| --- | --- |
| `projection/input/TitleText.java` | new value type with `Plain` and `Math` runs |
| `projection/input/TitleTextParser.java` | new total, deterministic parser (section 4.2) |
| `projection/input/SafeNodeLabel.java` | add derived `title()`; existing constructor and equality unchanged |
| `adapter/SafeNodeLabelExtractor.java` | compute the math-signal flag from `NodeStyleModel.getNodeFormat` and the `\latex`/`\unparsedlatex` prefix, then pass it to the parser |
| `canvas/title/MeasuredTitle.java`, `MeasuredRun.java` | new measurement result: numbers and availability only, no icons |
| `canvas/title/TitleMeasurer.java` | new measurement service with a generation-scoped memo, budget and queue (section 4.3) |
| `canvas/title/FormulaIcons.java` | new 512-entry LRU icon cache with failure memoization |
| `canvas/title/TitleRow.java`, `RunRow.java`, `PlainRunRow.java`, `MathRunRow.java` | new resolved row the painter consumes |
| `canvas/title/TitleTruncation.java` | rendered/degraded form selection and degraded-row truncation (section 4.4) |
| `canvas/title/TitlePainter.java` | new painter with the base/world coordinate contract (section 4.5) |
| `canvas/ScreenLabelPlacement.java` | thread `TitleText` and the measurer through the measuring sites; rendered form for non-truncating rungs, degraded form for truncating rungs; plain behaviour unchanged; refuse to retain a budget-deferred face; expose the budget signal |
| `canvas/ScreenLabelPlacementCache.java` | `passEpoch` in `Key`, one-shot `followUpPending`, `takeFollowUpRequest()`, `budgetExhaustedInLastPass()` |
| `canvas/PlacedLabel.java` | carry the `TitleRow`; no accessor removed |
| `canvas/GraphPainter.java` | capture `baseTransform`, pass it and `zoom` to `TitlePainter`, replace `drawCentered`; label colour and dimming unchanged |
| `canvas/GraphCanvas.java` | post a single `repaint()` in `labelsFor` when `takeFollowUpRequest()` is true |
| `freeplane_plugin_graph/build.gradle` | add the `lib` dependency; update `verifyGraphBundle`; keep the two existing `graphUiEvidence` output paths |
| `src/main/resources/META-INF/NOTICE.jlatexmath.txt` | new licence provenance file |

Test and evidence files (the inventory is exact, so section 8's files are listed here too):

| File | Change |
| --- | --- |
| `test/.../projection/input/TitleTextParserShould.java` | new |
| `test/.../canvas/title/TitleMeasurerShould.java` | new, including the baseline-ratio fixture |
| `test/.../canvas/title/TitleTruncationShould.java` | new |
| `test/.../canvas/title/TitlePainterShould.java` | new, including the non-identity base transform |
| `test/.../canvas/title/FormulaIconsShould.java` | new |
| `test/.../canvas/ScreenLabelPlacementShould.java` | extended |
| `test/.../canvas/ScreenLabelPlacementCacheShould.java` | new, follow-up and convergence |
| `test/.../canvas/GraphCanvasPaintShould.java` | extended |
| `test/.../canvas/ScreenLabelLiveTitleRefreshShould.java` | extended for the deferred-retention rule, existing cases unchanged |
| `test/.../adapter/SafeNodeLabelExtractorShould.java` | extended |
| `test/.../smoke/GraphWorkspaceUiEvidence.java` | extend the fixture with the four reference titles and emit first-pass timing |

**Production source** stays inside `freeplane_plugin_graph`. The complete deliverable does not, and the earlier
claim is narrowed accordingly: the existing `graphUiEvidence` task writes two tracked PNGs under
`docs/superpowers/specs/images/2026-08-10-graph-workspace-implemented.png` and
`…-graph-group-marker-implemented.png` (`build.gradle:178-182`), so running the acceptance command updates those
files, and any new formula-evidence PNG is added there deliberately rather than as a side effect. No user-facing
documentation file exists in the repository to update: `doc/` holds only the two edge-node-crossing study
directories. The user-visible behaviour is recorded in section 4.8, and the wiki update is the out-of-scope
follow-on recorded in section 10.

## 7. Failure modes

| Failure | Behaviour |
| --- | --- |
| unknown macro (`\leslant`) | engine throws; the run is `DEGRADED`; the title uses degraded form; the failure is cached in the icon cache |
| unbalanced `$` | parser produces literal `Plain` text; no engine call |
| formula wider than the slot | rendered form yields no candidate; the truncating rungs use degraded form, then `truncateTo` |
| formula taller than `2.0 ×` line height | `DEGRADED`; degraded form |
| engine returns non-finite or non-positive metrics | `DEGRADED`; invariant 4 keeps the box contract |
| budget exhausted mid-pass | remaining math runs are `DEGRADED` with `deferredForBudget`; never retained; one follow-up request |
| pass deferred work but measured nothing | `FORMULA_MEASUREMENT_STALL`, no further follow-up, degraded text stays visible |
| icon evicted from the 512-entry cache | a placed row keeps its own icon reference, so painting is unaffected; a later row resolution rebuilds the icon from the cached measurement without re-enqueueing or re-measuring the key |
| icon build throws during measurement | that run is `DEGRADED`, so the row carries plain text and painting has nothing to build |

## 8. Test strategy

New JUnit 4 + AssertJ tests, named in the house style. Each invariant in section 5 is listed with the test that
fails if the invariant is removed.

| Invariant | Falsifying test |
| --- | --- |
| 1 | `GraphCanvasPaintShould.plainTitlesDrawTheSameStringsAsTheBaseline` |
| 2 | `SafeNodeLabelExtractorShould` existing cases plus `safeNodeLabelEqualityIgnoresTheDerivedTitle` |
| 3 | `GraphCanvasPaintShould.unrenderableFormulaDrawsItsCompleteRawSource` and `truncatingRungNeverPaintsAPartialIcon` |
| 4 | `TitleMeasurerShould.rejectsNonFiniteAndNonPositiveIconMetrics` and `rejectsFormulaTallerThanTwiceTheLineHeight` |
| 5 | `ScreenLabelPlacementShould.formulaCandidateRespectsItsSlotLimit` plus the two existing over-cap tests, unchanged |
| 6 | `TitlePainterShould.painterConsumesTheResolvedRowWithoutMeasuring` (a measurer and an icon service that both throw if touched during paint) |
| 7 | `ScreenLabelPlacementCacheShould.convergesAcrossFollowUps`, `convergesBeyondAnyFixedCacheSize` with 20000 keys, and `stallsOnlyWhenAPassThatDeferredWorkMeasuresNothing` |
| 8 | `ScreenLabelPlacementCacheShould.deferredTitleIsMeasuredWithinTheQueueBound` (600 keys, budget 200, the final-batch label is deferred twice and then terminally measured) and `ScreenLabelPlacementShould.doesNotRetainABudgetDeferredFace` |
| 9 | `verifyGraphBundle`, plus `graphBundleGateFailsWhenTheFormulaJarIsDropped` as a deliberate mutation of the pinned set |

Additional new tests:

- `projection/input/TitleTextParserShould` — plain text is one run; single and double delimiters; escaped `\$`;
  unmatched and unbalanced delimiters yield one literal `Plain`; `wholeTextIsMath` yields one `Math` run;
  `\text{$...}$` does not close early; empty-run dropping.
- `canvas/title/TitleMeasurerShould` — a plain title measures exactly as
  `font.getStringBounds(displayText, SCREEN_FRC)`; a math title measures as the run sum; ascent and descent are
  the maxima; height is `ascent + descent`; `samePxDifferentFontDoesNotShareAMeasurement` changes the plain font
  family and a fractional size that rounds to the same icon pixels and asserts the numbers differ;
  **`baselineMatchesAnIndependentPixelFixture`** asserts that a
  single-letter icon's ascent, computed as `getBaseLine() * getIconHeight()`, lands within 1 px of the ascent
  implied by a reference `TeXIcon.paintIcon` render into a `BufferedImage` — the fixture that fails if the ratio
  is ever treated as a pixel distance again.
- `canvas/title/TitleTruncationShould` — rendered form is chosen for a fitting title; degraded form for an
  overflowing one; `truncateTo` is applied to the degraded row; a plain-only title behaves exactly as today.
- `canvas/title/TitlePainterShould` — a plain run draws at the world-space `drawString` coordinates, including
  the `Math.max(1.0f, …)` clamp at zoom 24 with a 12-point font; a math run paints at an **independently
  calculated** absolute device position for a nonzero anchor, a translated-and-scaled `B` and a non-unit zoom, so
  a double-applied `B` fails the assertion; a following plain label still receives the original transform.
- `canvas/title/FormulaIconsShould` — one icon per `(source, px, style)`; a failing source is attempted once and
  then served from the failure cache; the LRU is bounded at 512.
- `canvas/ScreenLabelPlacementShould` (extend) — a math label is measured by the icon, reaches `HOVER_ONLY`
  through the same ladder a plain over-long title does, and **every ladder candidate** stays within
  `slotMaxWidth`; a retained face and a forced `baseSlot` face keep their documented exemptions.
- `canvas/ScreenLabelPlacementCacheShould` (new) — `followUpPassIsACacheMissExactlyOnce`; `convergesAcrossFollowUps`
  with 1000 distinct formula keys against a 200 budget and a 512 icon cache; `convergesBeyondAnyFixedCacheSize`
  with 20000 keys, asserting every key is measured, the queue drains, and no further follow-up is requested;
  requested; `stallsOnlyWhenAPassThatDeferredWorkMeasuresNothing`.
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

Acceptance evidence requires the `GraphWorkspaceUiEvidence` fixture to carry the four reference titles and to
print the first-pass timing, so the screenshot and the budget number come from the same run. The current fixture
(`GraphWorkspaceUiEvidence.java:244`) supplies only `SafeNodeLabel.of("Alpha", "Alpha")` and `"Beta"`, so the
command as it stands can pass with formula rendering disabled — that gap is closed in the change set.

## 9. Risks

1. **Bundle-gate drift.** The gate is the only thing keeping the graph plugin's dependency set honest; changing
   four assertions in one commit without a test that fails on each of them individually is the main regression
   risk. The change adds one assertion per pinned property, and section 8 names the mutation test for the pinned
   set.
2. **Two coordinate spaces in one painter.** The base/world contract of section 4.5 is the part most likely to
   regress silently, because a wrong choice only shows on a scaled display or as a double-applied offset.
   `TitlePainterShould` covers the non-identity transform and asserts an absolute expected paint position.
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
6. **Performance on formula-heavy maps.** Unmeasured in this environment (section 3.6). The budget is a
   structural guard with a stated convergence contract, not a measured threshold.
7. **Truncation semantics vs. the live-title-refresh contract.** `ScreenLabelLiveTitleRefreshShould` pins that a
   retained label is only kept while its `sourceText` is unchanged. A deferred face is now excluded from
   retention (section 4.6), which is the one deliberate addition to that contract and is tested directly.
8. **Two caches drifting apart.** A measurement entry that survives while its icon was evicted must not change
   the measured numbers. `TitleMeasurerShould` measures the same key twice with the icon cache cleared in
   between and asserts identical numbers.

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
  resources) and included in the measurement and icon cache keys, so a macro edit invalidates correctly.
- **Follow-on, not this change:** the user-wiki text for section 4.8.

## 11. Accessibility and identity

Unchanged by construction, and asserted by invariant 2: the accessible name keeps using
`node.label().fullText()`, `GraphSearchModel` keeps searching `fullText()`, `ContributorInspector` keeps showing
`displayText()`, and projection identity keeps comparing `fullText` and `displayText`. A screen reader therefore
continues to read `$N$ is well-ordered by $\in$` verbatim, which is the same string it reads today.

## 12. Questions carried to the design-continuation gate

Design review rounds 1–3 raised 12 blocking and 6 non-blocking findings; every one was verified against source
before it was acted on. Revision r5 is a self-amendment raised while specifying r4: the specification review showed
that r4's fixed measurement-cache capacity reintroduced the eviction regime that r4's own termination argument
depends on, so the memo is now scoped to the projection generation and the bound holds without a capacity cliff. Round 3's answers settled three design choices, recorded here so they are not reopened: wholesale
degradation in a truncating rung is consistent with D3 and D4; excluding only `deferredForBudget` rows from
retention is narrow enough to preserve the plain-label retention contract; and the base transform belongs in a
carrier captured before the world transform is concatenated.

What remains genuinely open, and needs a person rather than a reviewer:

1. **The budget number.** `MAX_NEW_ICONS_PER_PASS = 200` is a structural guard with a proven termination property,
   not a measured threshold. The first implementation should confirm or replace it with the timing printed by the
   acceptance run. Counting title measurements does not directly bound icon construction for a title with several
   formula runs.
2. **The acceptance host and owner.** CI covers `test` and `verifyGraphBundle`. The screenshot and first-pass
   timing come from `graphUiEvidence`, which needs a machine that can render, and nobody is assigned to confirm
   them.
3. **Whether a fourth review round is worth it.** Round 3 changed the measurement key, the plain-font clamp, the
   progress contract, the painter's icon ownership, the change-set boundary, and one test's assertion method.
   Each change is small and verified, but together they touch every section a reviewer would read again.
