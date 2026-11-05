# Graph workspace — paint what was placed, and honour the whole-title `\latex` signal

Task identifier: `2026-11-05-graph-latex-whole-title-prefix`

Status: **design revision 5, for review round 5.** Round-1 F1–F11, round-2 G1–G11, round-3 H1–H9 and round-4 J1–J3
are applied; §9, §10, §11 and §12 record each one.

Predecessor: `docs/superpowers/specs/2026-11-03-graph-latex-titles-design.md` (r10) and its specification. This design
repairs two defects on that seam. It does not re-open D1–D4 and does not repair unrelated predecessor gaps, which §5
lists as deferred with their reasons.

Baseline: `plugin/graph-workspace` at `5fc5aba0f4`. All investigation ran in the PM integration worktree; the user's
delivery checkout and mind maps were read only.

## 1. Reported need

`Theorem: $ON$ is well-ordered by $\in$` is painted as raw LaTeX source in the graph workspace while the mind map
typesets it. The screenshot shows the **complete** raw string with no ellipsis, and the label is wider than the space
reserved for it.

The node is a declared formula, and that is the user's convention across the map:

```xml
<node TEXT="\latex Theorem: $ON$ is well-ordered by $\in$" ID="ID_215702338" ...>
```

Of the 7455 nodes in `Axiomatic_Set_Theory.mm`, 7397 carry a `TEXT` attribute and 2464 of those are `\latex`-prefixed;
2458 of the prefixed ones carry inline `$…$`, 6 carry LaTeX text only.

The predecessor specification already required both behaviours this report needs: §5.2 requires `wholeTextIsMath`
when "the raw content starts with `\latex`/`\unparsedlatex` plus whitespace"; §5.5.3 requires the paint row to be
built **from the shortened string**.

## 2. Approved direction and its scope

The user approved: honour the whole-title `\latex` signal so a `\latex` node is measured and painted as one formula
body, and leave inline `$…$` handling for plain nodes unchanged.

**Scope, stated precisely** (rounds 1–3): the graph paints the body through **its own** renderer — `TeXFormula`,
text style (`TitleMetrics.STYLE`), one line, no user-configured `LATEX_MACROS`, no interline-spacing or max-width
settings, and the existing excessive-height rejection. Two presentation rules follow and are stated rather than
implied:

- A body that contains **no `$` at all** is wrapped in `\text{…}`, exactly as the map's own renderer presents text
  (`TeXText.java:38-40`). There is no delimiter to reinterpret, so this is the map's upright text.
- A body that **contains `$`** is painted bare. Its text runs therefore appear in TeX's math style rather than the
  map's upright text. Matching the map exactly for those bodies needs the `\text{…}` wrap, which changes delimiter
  interpretation for `$$…$$`, nested `$` and escaped braces; §5 D5 defers it with that reason instead of shipping a
  wrap whose grouping cannot be guaranteed.

This remains **not** a byte-identical reproduction of the mind map: multiline bodies, user macros and display style
are out of scope as the predecessor states, and enclosure labels stay plain because hull geometry measures them.

## 3. Measured evidence

Artifacts archived in `freeplane_plugin_graph/doc/latex-whole-title/`: `bundle-manifest.txt`, `environment.txt`
(host `SANS_SERIF` resolves to Noto Sans; the map's sha256 `840358a03666d02f445e3aeb5ba84d9852e913318a3bf6c1776f50593aaff08c`
matches the frozen input), `titles-sample.json`, `sample-results.tsv`, `probe-sample-results.js`,
`probe-design-rule.js`, `probe-rejection-reasons.js`, and the per-question probes.

**These are prototype measurements.** They construct `TitleText` directly from the sampled bodies, so they do not
pass through extraction or the 80-code-point cap (round-3 H6). They measure the rendering question this design
answers — availability and width at the label font for one formula body — and they are labelled as such. The
production path is measured in stage 3 of §7, not here.

### 3.1 The reported title

At the graph's label font, `new Font(Font.SANS_SERIF, PLAIN, 12)` (`GraphTheme.java:66`):

| Form | Run layout | Typeset width | 200 px vertical cap |
| --- | --- | --- | --- |
| One formula body | `Math("Theorem: $ON$ is well-ordered by $\in$")` | **194.00** | fits |
| Inline fragment parse (what the code does) | `Plain`, `Math("ON")`, `Plain`, `Math("\in")` | **200.95** | over by 0.95 |

`placeMathNode` accepts the typeset row only when `rendered.width() <= slotMaxWidth(slot)`; the widest slot is the
200 px vertical one, so at 200.95 px the typeset row is rejected in every slot.

### 3.2 Why raw source appears complete

`placeMathNode` truncates only the *label text*:

```java
degradedText = TitleTruncation.truncated(degradedText, font, limit);              // shortened
rowSource = truncated ? context.measurer.measureDegraded(title, font) : degraded; // whole title again
```

`TitleRow.resolve(rowSource, …)` then carries the **complete** raw source, and `TitlePainter` draws the row's
`paintText`, never `PlacedLabel.text()`. The reserved box is the truncated width; the painted string is the full
source — the reported screenshot exactly.

### 3.3 Why the declared formula is never recognised

`extract` computes `full` from `normalizedRawContent(node)`, which ends with `removeLatexPrefix(converted, localFormat)`
(`SafeNodeLabelExtractor.java:58`). For an ordinary node that strips `\latex` plus the following space. `labelFor`
then tests `full.startsWith("\\latex")` (`:41`) on the already stripped string, so the whole-title branch is dead
code for every `\latex`-prefixed node whose format is not a pattern format.

### 3.4 Why a combined pattern format degrades

`labelFor` builds the body as `patternLatex ? full : removeLatexPrefix(full, format)` (`:42-43`).
`removeLatexPrefix` returns its input unchanged for `latexPatternFormat`, `unparsedLatexPatternFormat` and
`markdownPatternFormat`, so a node combining one of those formats with a `\latex` prefix keeps the marker in the
body: body `\latex   $x_2 = 3$` is DEGRADED, while body `$x_2 = 3$` renders at 38.00 px.

### 3.5 Reach on the user's map (prototype measurement)

250 uniformly sampled `\latex` bodies (`titles-sample.json`, per-title results in `sample-results.tsv`):

| Interpretation | Typeset | Rendered but over 200 px | Engine/height rejection |
| --- | --- | --- | --- |
| one bare formula body | 248 | 15 | 2 |
| one always-wrapped body | 247 | 15 | 3 (adds `$$…$$`, which `\text{…}` rejects) |
| inline fragments (today) | 248 | 17 | 2 |

The two rejections of the bare form are **height rejections, not `\fbox` rejections** (round-3 H8): both bodies
construct icons taller than `TitleMetrics.MAX_HEIGHT_FACTOR × lineHeight` for this font — 40 px and 36 px against a
32.69 px ceiling (2 × 16.344 px) — so the height rule rejects them. `\fbox` itself typesets:
`\fbox{\lambda}` builds at 28×30 and `\Bigg\{\fbox{\lambda^\theta}\Bigg\}` at 52×40, and one `\fbox` body in the
sample renders at 58 px with a 32 px height, just under the ceiling. `probe-rejection-reasons.js` records the
construction result and icon height per body in `rejection-reasons.tsv`, so no rejection is inferred from
`DEGRADED` alone.

Five of the 250 bodies carry LaTeX commands outside `$…$`; under inline parsing those commands reach the screen as
characters.

## 4. Change set

Production paths relative to `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/`.

### 4.1 `canvas/ScreenLabelPlacement` — paint what was placed

In `placeMathNode`:

1. The truncated branch builds its row from the shortened string:
   `rowSource = truncated ? measurer.measureDegradedText(degradedText, font, degraded.deferredForBudget()) : degraded`
   (§3.2, predecessor §5.5.3).
2. The terminal `HOVER_ONLY` fallback builds its row from its own `fallbackText` the same way. Its `forced` flag, its
   obstacle registration and its slot stay exactly as they are today — this design does **not** change which
   fallbacks are painted (§5 D3).

Nothing else in this file changes: caps, fonts, slots, rungs, leaders, retention, the enclosure path and `truncateTo`
stay byte-identical.

### 4.2 `canvas/title/TitleMeasurer` — a degraded measurement of a literal string

`public MeasuredTitle measureDegradedText(String text, Font font, boolean deferredForBudget)`:

- returns `MeasuredTitle.degraded(...)` — never `RENDERED`, and never delegated to `measure`/`measureDegraded`,
  which would classify a literal string as plain text (round-2 G4);
- one run only: `MeasuredRun.plain(text, 0, width, ascent, descent)` from
  `font.getStringBounds(text, TitleMetrics.SCREEN_FRC)`, with the existing `Math.max(…, 1.0e-6)` height floor;
- memoized under a separate namespace keyed by `(text, family, style, size, DEGRADED_TEXT)`, so it can never collide
  with a title key, and it consumes **no** budget slot, enqueues nothing, and cannot affect
  `budgetExhaustedInLastPass`, `stalled()` or `pendingCount()`;
- the `deferredForBudget` argument is carried into the returned measurement, so `TitleRow.resolve` reproduces the
  original row's deferral state. The undeferred literal measurement is what the cache holds; the flag is applied per
  call (round-3 G4 disposition).

`measure`, `measureDegraded`, `defer`, the deferral queue and the cache contract are unchanged.

### 4.3 `adapter/SafeNodeLabelExtractor` — honour the declared formula

`fullText` and `displayText` are produced exactly as today, byte for byte, because search (`GraphSearchModel`),
accessibility (`AccessibleGraphCanvas`) and hull geometry (`GraphGeometryEngine`) read them (round-2 G1). The formula
signal and the body are derived **without** touching those strings:

1. `content` is today's `sourceText(node.getUserObject())`. A null or empty `content` produces **no** formula signal
   and keeps the ordinary link/icon/`"Node"` fallback path.
2. `converted` is today's `directText ? convertHtml(content) : content`; the direct-text gate stays, so a
   `FormattedFormula` holding HTML-looking text remains literal.
3. **Recognition is decided on the left-trimmed `converted`, independently of the format exclusions** (round-4 J1):
   `trimmed` is `converted` with leading whitespace removed; `declaration` is `\latex` when `trimmed` starts with it
   and the next character is whitespace, `\unparsedlatex` in the same shape, and otherwise null. This is the same
   whitespace predicate `removeLatexPrefix` uses, applied at the left-trimmed offset, so a leading-whitespace or
   pattern-format declaration is recognised exactly like a bare one.
4. **Declared-body emptiness is decided there too** (round-3 H1, round-4 J1): `declaredBody` is `trimmed` after the
   declaration and its following whitespace, left-trimmed again. If a declaration is present and `declaredBody` is
   empty, there is no formula signal and the ordinary path runs — a source of `\latex ` alone never classifies the
   `"Node"` fallback as a formula.
5. `declaresWholeFormula` is true when a declaration is present, or the node format is `latexPatternFormat` /
   `unparsedLatexPatternFormat` with a non-empty body.
6. The body comes from `displayText`, so the 80-code-point cap applies (round-1 F2), with **provenance carried as a
   fact, never inferred from a textual comparison** (round-3 H2, round-4 J2):
   `strippedByLegacy` is true exactly when today's `removeLatexPrefix` would have removed the declaration — the
   format is not one of the three pattern formats, `converted` starts with the declaration at offset zero, and
   whitespace follows it. Then:
   - `strippedByLegacy` true → the declaration is already absent from `displayText`; remove nothing. `\latex \latex $a$`
     therefore yields the body `\latex $a$`.
   - `strippedByLegacy` false and `displayText` starts with the declaration followed by whitespace → remove exactly
     that one declaration and its whitespace (the leading-whitespace and pattern-format cases).
   - otherwise remove nothing.
7. The formula source is `\text{` + body + `}` when the body contains no `$` character, and the bare body otherwise
   (§2). Pattern-format bodies are never wrapped, because a pattern format declares a formula, not text.
8. `TitleText.Math.source()` is that formula source and `rawSource()` is today's `displayText`, so degradation
   reproduces exactly what the label shows today.
9. Everything else is untouched: the two-string `SafeNodeLabel.of` factory keeps its plain default and remains the
   excluded-node sentinel's constructor, and nodes with neither declaration nor pattern format keep inline parsing.

`TitleTextParser` and `TitleText` are **not** modified.

### 4.4 Round-4 corrections folded in

- Recognition runs on the left-trimmed converted text with the existing whitespace predicate, and no longer defers to
  `removeLatexPrefix`'s offset-zero and format-exclusion behaviour (J1).
- Legacy prefix removal is a separate operation from recognition, and `strippedByLegacy` is an explicit fact rather
  than a prefix comparison of `displayText` (J2).

## 5. Deferred, with reasons

Recorded so the next run inherits them instead of re-deriving them; none is required by this report.

| # | Gap | Why deferred |
| --- | --- | --- |
| D1 | Formula titles never use the `DENSE_NEAR`/`DENSE_DISPLACED` rungs the predecessor §4.4 assigns them, and `retained()` skips math titles | Pre-existing divergence from the predecessor design that affects all 2464 titles, independent of this defect; changing it alters the typography of every formula label and needs its own evidence |
| D2 | `placeMathNode` tries near slots only; plain titles also get `FAR_SLOTS` | Pre-existing placement gap; adding far slots reorders rungs and needs displaced-rung metadata and leader geometry |
| D3 | The terminal `HOVER_ONLY` math fallback clears `forced`, so a forced label can be invisible | Pre-existing; making it visible without obstacle and base-slot semantics would let later labels overlap it |
| D4 | Truncation ellipsizes inside a control sequence | Approved by predecessor D3/D4 for over-cap titles; round-2 review accepted dropping the token-boundary rule |
| D5 | Mixed bodies (text plus `$…$`) paint their text in TeX math style instead of the map's upright `\text{…}` text | The wrap changes delimiter interpretation for `$$…$$`, nested `$` and escaped braces (round-3 H3); it needs its own delimiter-safety design and evidence |

## 6. Test strategy

Baseline status is stated for each test. `fails today` means a genuinely red behavioural run against the baseline
production code, never a missing test class.

| # | Test | Baseline | Asserts |
| --- | --- | --- | --- |
| T1 | `SafeNodeLabelExtractorShould.latexPrefixedNodeIsOneFormulaRun` | fails today | `\latex Theorem: $ON$ is well-ordered by $\in$`: one `Math` run, `source()` the bare body, `rawSource()` today's display text, `displayText()` unchanged |
| T2 | `SafeNodeLabelExtractorShould.latexPrefixedTextWithoutDelimitersIsWrappedFormula` | fails today | `\latex K\"onig's Theorem I`: one `Math` run whose source is `\text{K\"onig's Theorem I}` |
| T3 | `SafeNodeLabelExtractorShould.combinedPatternFormatAndPrefixStripsBodyPrefix` | fails today | `latexPatternFormat` + `\latex $x_2=3$`: source `$x_2=3$`, unwrapped, `rawSource` keeps today's display text |
| T4 | `SafeNodeLabelExtractorShould.plainNodeKeepsInlineParsing` | passes today | `$a$ and $b$` on a plain node still parses to plain/math runs |
| T5 | `SafeNodeLabelExtractorShould.declarationProvenanceAndFallbacks` | fails today | `\latex \latex $a$` keeps one declaration in the body; a leading-whitespace prefix produces the same body as its stripped form; HTML-wrapped content classifies the same way; `displayText` stays exactly today's value in every case; a `FormattedFormula` holding HTML-looking text stays literal. Fixtures stay below the 80-code-point cap. |
| T6 | `SafeNodeLabelExtractorShould.emptyPrefixBodyKeepsOrdinaryLabel` | fails today for the leading-whitespace variant | `\latex ` and `\unparsedlatex ` alone, whitespace variants, pattern formats, null and empty content, and a node whose only label is a link or icon: no `Math` run, no exception, fallback unchanged |
| T7 | `SafeNodeLabelExtractorShould.cappedDisplayTextIsTheBodySource` | fails today | a prefixed title longer than 80 code points, including a supplementary character: `fullText` keeps the whole source, `displayText` keeps the cap and its `...`, and the body is built from the capped text |
| T8 | `TitleMeasurerShould.degradedTextIsMeasuredFromItsOwnString` | new test, red because the method is absent | `measureDegradedText` returns `DEGRADED`, one plain run holding exactly the supplied string, width equal to its bounds, `deferredForBudget` copied from its argument, no budget consumed, `pendingCount() == 0`, and no cache collision with `measure` of the same text |
| T9 | `GraphTitleFormulaShould.paintedRowMatchesThePlacedText` | fails today | an **over-cap** formula fixture, extracted from a `NodeModel` through `MapSnapshotFactory` and placed in a forced `ABOVE` slot: `truncated()` is true, the row is `DEGRADED`, its single run is a `PlainRunRow`, and its `paintText()` equals `label.text()` exactly |
| T10 | `GraphTitleFormulaShould.renderedTitleUsesOneMathRun` | fails today | the reported node yields a `RENDERED` row whose single run is a `MathRunRow` with `body()` equal to the bare body and a non-null icon, and whose placement width equals the measured typeset width |
| T11 | `GraphTitleFormulaShould.reportedTitleFitsTheVerticalSlot` | fails today | the extracted label typesets at 194.00 ± 0.51 px and is placed `FULL_NEAR` in `ABOVE`, not `TRUNCATED_NEAR` |
| T12 | `ScreenLabelPlacementShould.truncateToMirrorsTitleTruncation` | **passes today** — a new test that the two existing loops already agree | creates the mirror assertion the predecessor's T15 specifies; it is a green control, and its absence never causes a compilation failure |

`MathRunRow` has no `paintText()`; T9/T10 therefore assert run *type* and content per run kind rather than one string
oracle. Widths use the graph's own font construction with a host tolerance calibrated in the test setup.

## 7. Red-phase and verification recipe

`gradle :freeplane:compileJava` does not compile graph tests and cannot express these failures, so the evidence is
staged (round-2 G10, round-3 G5):

1. **API stage** — only T8 references a symbol that does not exist. Record
   `:freeplane_plugin_graph:test --tests '*TitleMeasurerShould'` failing to compile on the absent
   `measureDegradedText`, before implementing it. T12 is not part of this stage.
2. **Behaviour stage** — with that scaffold in place and the production change absent, record
   `:freeplane_plugin_graph:test --tests '*SafeNodeLabelExtractorShould' --tests '*GraphTitleFormulaShould'`
   failing on T1, T2, T3, T5, T6, T7, T9, T10, T11 with their named assertions.
3. **Green stage** — after the change: the same selections, then `:freeplane_plugin_graph:test` and
   `:freeplane:compileJava`, then `:freeplane_plugin_graph:verifyGraphBundle`. Stage 3 also re-measures the 250-body
   sample **through extraction and the cap** and records the production-path totals next to the prototype ones.
4. **Application stage** — build the application in this worktree with `gradle dist` (the repository's documented
   distribution task, run with the installed JDK 21), record the sha256 of the launched
   `BIN/plugins/org.freeplane.plugin.graph/lib/plugin-1.13.4.jar` and the commit, then launch it on a virtual X
   display, open `Axiomatic_Set_Theory.mm`, search `is well-ordered by`, open the graph workspace, and capture the
   window containing `ID_215702338`, `ID_735350856` (`\latex Hint: $\{x,y\}$`), `ID_839546075`
   (`\latex Let $y\in ON$, and $x\in y$ be given.`) and `ID_1851408788` (`\latex So $\in$ totally ordered $y$
   strictly.`). The recorded jar hash is what distinguishes this build from any existing delivery installation.

Toolchain prerequisite: the delivery host has no JDK and no `gradle` on `PATH` (a JRE 8 and a JRE 27 only; a cached
7.3.3 distribution in `~/.gradle`; `pacman` offers `jdk21-openjdk 21.0.13` and `gradle 9.8.2`). Execution must
install those two packages before stage 1 can run, with the user informed first.

## 8. Risks

- **Host font metrics.** 194.00 px is this host's font; T11 uses a tolerance calibrated in the test setup, and the
  application capture runs on the delivery host.
- **Mixed-body typography.** Bodies containing `$` are painted bare, so their words appear in TeX math style (§2,
  D5). The capture includes `ID_839546075`, a text-heavy mixed title, so the change is visible and reviewable.
- **Text-only bodies are wrapped.** Six bodies across the whole map carry no delimiters; one of them is in the
  250-body sample. The wrap is their only presentation, and a body with an unbalanced brace would fail to typeset and
  degrade. `sample-results.tsv` records the current outcome.
- **Height rejections stay.** Two sampled bodies build icons taller than the ceiling (40 px and 36 px). Documented D3
  behaviour, out of scope.

## 9. Round-1 findings applied

| # | Finding | Resolution |
| --- | --- | --- |
| F1 | Blocking: the row is rebuilt from the whole title, so the paint overflows the placed box | §3.2 diagnosed; 4.1.1/4.1.2; T9 |
| F2 | Body/raw built from `full`, bypassing the 80-code-point cap | 4.3.6 derives the body from `displayText`; T7 |
| F3 | Math titles bypass retention/ladder/forced behaviour | §5 D1–D3 defer it with reasons; §4 changes no placement rule |
| F4 | Raw predicate can disagree with normalization | 4.3.2–4.3.5 derive signal and body from one converted, left-trimmed representation while `displayText`/`fullText` stay byte-identical; T5 |
| F5 | Body conversion incomplete (prefix-only, null, single `$` pair) | 4.3.3 decides emptiness from content; 4.3.5 strips with provenance and never re-parses; T3, T5, T6 |
| F6 | Token-boundary truncation underspecified and an amendment | dropped; §5 D4 |
| F7 | "exactly as the mind map renders it" overclaims | §2 narrowed, including which bodies get upright text |
| F8 | T5 does not discriminate the extractor repair | T9/T10/T11 extract from a `NodeModel` through `MapSnapshotFactory` |
| F9 | The synthetic harness cannot capture a real map | §7 stage 4 |
| F10 | Tests described that do not exist | §6 marks baseline status; T8 new-red, T12 new-green |
| F11 | Measurement claims not reproducible | §3, §7 and the archived artifacts |

## 10. Round-2 findings applied

| # | Finding | Resolution |
| --- | --- | --- |
| G1 | Blocking: prefix stripping conflicts with unchanged text and `rawSource` | 4.3 keeps `fullText`/`displayText` byte-identical and strips only the body; T1/T5 |
| G2 | Blocking: whole-title parsing rewrites mixed delimiters | 4.3.6 removes the re-parse; §2/4.3.7 never wrap a body containing `$`; T3/T5 |
| G3 | Prefix-only bodies cannot be handled from the display-body check | 4.3.3 (superseded by H1, which decides emptiness from content); T6 |
| G4 | `measureDegradedText` cache and deferral semantics incomplete | 4.2; T8 |
| G5 | Retention and dense-font behaviour still excluded | §5 D1 |
| G6 | Appending `FAR_SLOTS` does not preserve rung priority or metadata | removed; §5 D2 |
| G7 | Keeping `forced` makes an unreserved fallback visible | removed; §5 D3 |
| G8 | T8's rendered-row oracle is incompatible with the row API | §6 T9/T10 assert per-run type and content; T9 uses an over-cap fixture (H4) |
| G9 | Capped-source rule has no regression protection | T7, T2 |
| G10 | The red-phase recipe cannot produce the stated evidence | §7 stages 1–2, corrected again by H5 |
| G11 | Acceptance targets incomplete; measurements prospective | §7 stage 4 names four node IDs; §3 and §7 label prototype versus production measurement (H6) |

## 11. Round-3 findings applied

| # | Finding | Resolution |
| --- | --- | --- |
| H1 | Blocking: prefix-only content is checked after its identity is lost | 4.3.3–4.3.4 decide the declaration and its emptiness from `converted` before `firstNonEmpty`; T6 gains the leading-whitespace variant |
| H2 | Display-derived stripping needs provenance; T5 overstates equivalence | 4.3.6 strips only when the original declaration survived; T5 asserts provenance and stays below both caps, while T7 owns the capped variants |
| H3 | Preserving delimiter bytes does not preserve their interpretation after wrapping | the wrap is confined to bodies with no `$`; mixed and `$$…$$` bodies are never wrapped; §5 D5 records what that costs and why |
| H4 | T9's fixture cannot truncate after the repair | T9 uses an over-cap fixture; T10/T11 keep the reported title |
| H5 | T12 cannot produce the claimed compilation failure | T12 is a new green control; stage 1 attributes the compilation failure to T8 alone |
| H6 | The archived sample measures a different pipeline | §3 is labelled prototype measurement; §7 stage 3 re-measures through extraction and the cap |
| H7 | The acceptance recipe does not prepare the application | §7 stage 4 names `gradle dist`, the JDK, and the launched jar's sha256 |
| H8 | Evidence descriptions contradict the files | node counting corrected; rejection reasons recorded as height rejections by `probe-rejection-reasons.js`; §3.5 gives rendered-over-cap and rejection columns separately |
| H9 | Round-2 disposition | G4, G5, G6, G7, G9 closed above; G1, G2, G8, G10, G11 closed by H2, H3, H4, H5, H6/H7 |

## 12. Round-4 findings applied

| # | Finding | Resolution |
| --- | --- | --- |
| J1 | Blocking: recognition deferred to `removeLatexPrefix`, so a leading-whitespace or pattern-format declaration was missed | 4.3.3 recognises the declaration on the left-trimmed converted text with the same whitespace predicate, independently of the format exclusions; 4.3.4 decides emptiness there; T5/T6 |
| J2 | Major: a textual prefix match does not establish provenance | 4.3.6 carries `strippedByLegacy` as an explicit fact and strips from `displayText` only when the original declaration survived; T5 asserts both provenance cases |
| J3 | Minor: §8 misreported sample versus whole-map counts and described both rejections as `\Bigg` constructs | §8 corrected: six bodies across the map, one in the 250-body sample; the rejections are stated as height rejections only |

H3, H4, H5, H6 and H7 were accepted as resolved by round 4; H8 is closed by J3 and H9's closure record is corrected
here.

## 13. Round-5 result

Review round 5 returned **ZERO BLOCKERS**. The two round-4 contradictions were accepted as resolved, §4.3 steps 1–9
were judged one implementable algorithm consistent with T1–T12, and no new defect was found. Its one minor finding —
stale step numbers in the resolution tables above — is applied here, so no finding remains open. Round 5 restated
that compilation, red/green outcomes, production widths, the bundle gate and the application evidence cannot be
verified without a build; §7 records those as staged evidence rather than claims.
