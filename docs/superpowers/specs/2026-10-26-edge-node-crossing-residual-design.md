# Graph edge–node crossings — two-stage fix (relocation expansion + off-midpoint routing)

Task identifier: `2026-10-26-edge-node-crossing-residual`

Status: **approved by the user on 2026-10-26 at the revision-3 pivot.** The user reported "Edges still cross
over nodes" with a cropped canvas screenshot, asked whether moving node positions would fix it, initially
chose relocation only (**B**), and after the routing premise was measured and refuted re-scoped to **B + C**.
This document is the design authority for the technical specification that follows it.

Revision history that matters for review:

- r1 proposed a `CLEARANCE_MARGIN` that only reduced feasibility; withdrawn.
- r2 claimed routing could never clear an anchor chord below 36.7 wu and used that to justify
  relocation-only. **Measured and refuted** (§2 Fact 4).
- r3 dropped the legality pre-screen and the ring-reservation rule, both of which destroy existing in-budget
  repairs, and adopted a staged search plus an off-midpoint routing family.
- r4 narrows two preservation goals that were over-claimed, corrects the routing evidence, and fixes the
  supersession and fixture requirements raised by review rounds 3 and 4.

Baseline: `plugin/graph-workspace` at `bfaed5611d170b5b0b3dc93919685bd6c54035c5`. All investigation ran in the
PM integration worktree; the user's delivery checkout and their documents were read only.

## 1. Reported defect

The user's workspace `~/Documents/999.notebooks/01.math/math.fpg` registers three maps —
`Axiomatic_Set_Theory.mm`, `Point-set_Topology.mm`, `Real_Anaylsis.mm`. In the reported canvas region a
relationship edge is drawn as a straight line from one group disc to another and passes over a third,
non-incident disc.

## 2. Measured evidence

Both probes are archived with their recorded output and a run recipe under `doc/edge-node-crossing/probe/`.
Each was run twice with byte-identical stdout.

### Probe 1 — the real workspace

`MapSnapshot` construction → `ProjectionEngine` → `LayoutWorker` → `EdgeConflictCorrection` → `EdgeRouter`.

| Measurement | Value |
| --- | --- |
| Source nodes in the three maps | 26 314 |
| Projected graph | **28 discs, 12 edges, 7 enclosures, 0 pins, 0 workspace relationships** |
| C1/C2 conflicts detected | 1 (`crossings=0`, `edgeOverNode=1`) |
| Relocation outcome | 1 accepted `TRANSLATION`, **3.238 wu**, residual **0** |
| Router outcome | **12 straight, 0 bow, 0 dogleg, 0 fallback** |
| Clearance audit on published paths | **0 of 12** edges violate `radius + 3` |
| Frame flags | all three exhaustion flags false |

**Fact 1 — the graph is small and no budget is exhausted.** Only nodes carrying `<graph_group version="1"/>`
become discs; every other node becomes a nested enclosure hull and a group node's subtree is not descended
into. 28 discs cannot exhaust the conflict-index or routing allowances.

**Fact 2 — the router's offsets are fractions of the anchor chord, and its peak displacement is not `0.25·L`
in general.** `EdgeRouter.anchor` returns `node.center()` for a node endpoint, so `EdgeRouter.bestRung` scales
offsets by the **centre-to-centre** distance `L`. For an untrimmed quadratic with control offset `f·L`, the
maximum normal displacement of the curve is `f·L/2`; `0.25·L` is that value only at `f = 0.50`. Endpoint
trimming moves the published attachments toward the control point, so the drawn path is not a scaled copy of
the untrimmed curve. A same-side dogleg displaces by its full `f·L`. The `f = 0.50` dogleg is rejected by
`ROUTE_LENGTH_CAP = 1.25`, so the largest legal dogleg rung is `0.30·L`. Clearance is strict: a disc violates
when its distance to the published path is `< radius + EDGE_NODE_CLEARANCE - ROUTE_EPSILON`.

Measured anchor chords adjacent to the reported pair: 33.596, 36.182, 41.170, 44.120 wu.

### Probe 2 — router capability on exactly that geometry

A, B (radius 8, `graph_group`) at the measured chord, one radius-8 obstructing disc N, one connector A→B, no
pins, no other content, production `EdgeRouter`. The table reports the **selected output**: shape, fallback
flag and the published polyline's surface clearance to N. It is not a per-rung clearance table.

| Anchor chord | N at chord midpoint | N at 25 % along the drawn straight chord |
| ---: | --- | --- |
| 33.596 wu | `STRAIGHT` fallback, clearance −8.000 | `STRAIGHT` fallback, clearance −8.000 |
| 36.182 wu | **`BOW`, clearance +3.183** | `STRAIGHT` fallback, clearance −8.000 |
| 41.170 wu | **`BOW`, clearance +4.729** | `STRAIGHT` fallback, clearance −8.000 |
| 44.120 wu | **`BOW`, clearance +5.445** | `STRAIGHT` fallback, clearance −8.000 |

A `STRAIGHT` fallback with clearance `−8.000` means the published chord passes through the disc centre; the
rejected rungs' own clearances were not recorded. At `L = 36.182` the largest midpoint bow leaves about
`+0.805 wu` and the largest legal dogleg about `+0.727 wu` of surface clearance to a quarter-chord obstacle —
both below the required 3 wu, neither at `−8`.

**Fact 3 — on the measured short chords, the existing curved family cannot reach an off-midpoint obstacle.**
Every bow and dogleg in the live ladder is built from the **chord midpoint** and the **midpoint normal**
(`EdgeRouter.bowPath`: control at the midpoint plus normal×offset; `EdgeRouter.doglegPath`: bends at 1/3 and
2/3 on the same normal). For the four measured chords, every rung leaves less than the required 3 wu of
surface clearance to an obstacle placed 25 % along the drawn straight chord, so the ladder falls back. This
is a statement about *this finite family on these short chords*. It is **not** a general impossibility: on a
long chord (measured by review round 4 at `L = 100` with the obstacle at 25 % of the drawn chord) the existing
`f = 0.30` midpoint bow clears by 5.196 wu. The design's claim is therefore: the family's reach is limited and
its shape is midpoint-symmetric, not that routing is universally impossible.

**Fact 4 — the r2 routing-impossibility claim is refuted.** r2 asserted no anchor chord under 36.7 wu could be
routed clear. The 36.182 wu chord is cleared by a legal `0.50` bow with 3.183 wu of surface clearance. The
"real floor" r2 derived is withdrawn; the observed threshold applies only to the centred fixture at the
probe's sampling policy. The r2 premise — and with it the argument that relocation is the only possible
mechanism — is withdrawn.

**Fact 5 — the accepted relocation lands exactly on the boundary.** The one accepted repair in probe 1 puts
the disc at perpendicular offset `11.000 wu` against a threshold of `11.000 wu`. The strict predicate permits
that, so boundary landing is not itself a defect. What the number shows is how little room the search has:
one blocked region empties the family.

## 3. Root-cause hypothesis

Two independent stages can each leave a crossing, with different failure modes.

**Relocation (C2) exhausts its only ring.** `addEdgeOverNodeCandidates` proposes exactly six targets — two
perpendicular senses × the along-chord offsets `{0, −h, +h}` with `h = 2r + MIN_GAP + 3 = 25` — and every
target aims at signed distance `rho = r + EDGE_NODE_CLEARANCE`, one ring and never a second. Inside nested
enclosures a perpendicular move grows the disc's parent-hull extent, so A3 (`hullPairs_out ⊆ hullPairs_in`)
can reject it; a move toward a neighbour trips A2 (`gapPairs_out ⊆ gapPairs_in`). All six share one ring, so
one blocked region empties the family and the conflict becomes a residual.

**Routing runs out of reach on a midpoint-symmetric ladder.** Fact 3: on the measured short chords the finite
curved family cannot place the bulge far enough from the midpoint to clear an off-midpoint obstacle.

These are hypotheses. Probe 1 did not reproduce the user's exact arrangement — on a fresh settle the single
conflict is accepted and nothing is violated — so which stage produced the screenshot is still open (§10).

## 4. Goals

Each goal is stated with the exact scope it can be proven under. Preservation claims are deliberately narrow;
an earlier revision asserted frame-wide preservation and that was false.

1. **Relocation preserves the legacy accepted sequence.** For an input whose legacy search finishes without
   ever reaching a *failed* round — that is, without a round in which no candidate is accepted — the new
   search produces the identical accepted repairs, the identical final positions, and the identical
   `rounds`, `verifications`, `verificationWork` and residual. For an input that does reach a failed round,
   the accepted prefix is identical and everything after it may differ; such an input is allowed to gain a
   repair, and its counters and final positions may then change.
2. **Routing preserves every per-edge decision under identical inputs.** For a given edge, given the same
   obstacle grid published by earlier edges and the same remaining routing allowance, the new ladder selects
   the identical path, classification and sample list when the current ladder accepts. Named fixtures must
   additionally be preserved exactly, end to end, including every later edge. Frame-wide preservation is
   **not** claimed: extending any edge's rung set can change the obstacle grid seen by later edges, and review
   round 4 produced a concrete two-edge counterexample that does exactly that.
3. **Off-midpoint obstacles on the measured short chords become routable.** A short chord whose obstacle sits
   away from the midpoint and which therefore falls back today is cleared by a legal routed path where one
   exists within the unchanged caps.
4. **Relocation reaches a second ring.** A conflict whose every ring-0 target is vetoed by A1, A2 or A3 but
   whose ring 1–4 target is legal is repaired rather than published as a residual, subject to the unchanged
   frame allowances.
5. Frozen evidence does not change: `settled-reference.csv` stays byte-identical, `TWO_MAP_FIRST_IDLE_STEP =
   3103` still holds, and `stopsAtFourAcceptedRoundsWithoutClaimingExhaustion` keeps `verifications = 4`
   with `repairs = 32`.

## 5. Non-goals

- **No legality pre-screen.** r2's A2/A3 pre-screen is withdrawn. It starves the shared verification-work
  allowance and can stop a frame with zero repairs.
- **No clearance-margin change.** The strict predicate is untouched; C2 keeps aiming at
  `radius + EDGE_NODE_CLEARANCE`.
- **No layout-force change.** Deferred: it moves every settled position and invalidates `settled-reference.csv`,
  the first-idle pins and the performance gates.
- **No dense or optimal routing.** This is not a shortest-path or visibility-graph rewrite; it is a finite
  extension of an existing ordered ladder.
- **No new public API.** Both changes are internal to `EdgeConflictCorrection` and `EdgeRouter`.
- **No change to pins, enclosure membership, node radii, prominence, label placement or settlement policy.**
  A pinned disc remains immovable and still publishes a residual (E9).
- **No completeness guarantee.** Residuals remain a legal outcome and the authoritative observable.

## 6. Design

The two stages are implemented independently; neither depends on the other's internals. Relocation still
feeds positions to routing, so they are not observationally independent — only separately ownable.

### 6.1 Routing: a control-position bend family

Keep the live ladder exactly as it is, and append one new group after it fails. For anchors `p0`, `p1`, anchor
chord length `L`, normal `n`:

```
control(A, f) = p0 + A * (p1 - p0) + f * L * n
```

`A` is the **control-position fraction** along the anchor chord, `f` the signed offset fraction that already
encodes the side. The new group is:

```
CONTROL_FRACTIONS = { 0.25, 0.75, 1.0/3.0, 2.0/3.0 }      # A = 0.5 is the existing bow ladder
OFFSET_FRACTIONS  = { +0.50, -0.50, +0.30, -0.30, +0.15, -0.15 }
```

The published order becomes `straight → bow ladder (A = 0.5) → dogleg ladder → control-position group`,
matching the existing sequence in `EdgeRouter.route` and adding only the last group. No separate side loop
runs: the sign of `f` selects the side. Within the new group the order is `A` in `CONTROL_FRACTIONS` order,
then `f` in `OFFSET_FRACTIONS` order.

Every candidate passes the existing, unchanged gates, in the existing order: `|f| * L <= MAX_BEND`, the
length ratio, sampled `length <= ROUTE_LENGTH_CAP * chordLength`, and the full `clears(...)` predicate — which
covers other node discs **and** other paths, unlike `clearsDiscs(...)` alone.

The `A = 0.5` ladder keeps its own implementation untouched, including its existing `analyticRatio`, so its
promised bit-for-bit behaviour is preserved. The control-position group needs its own length ratio, because
the existing function assumes a midpoint control:

```
R(A, f) = integral_0^1 2 * sqrt( (A + (1-2A) t)^2 + f^2 (1-2t)^2 ) dt
```

For the listed `A`/`f` combinations every `R(A, f)` is below 1.25, so in practice the analytic gate never
rejects an entry of this group and the binding constraints are `MAX_BEND` and the sampled length check. The
specification must pin the exact `R` values and must keep `MAX_BEND` as the control-offset cap.

No asymmetric dogleg is added. The dogleg's two bends already displace 1/3 and 2/3 of the chord, and the
existing dogleg ladder stays available, so the omission is a deliberate finite-family and work trade-off, not
a claim of equal coverage.

### 6.2 Relocation: a staged search

Replace the single-ring family with two stages. **No pre-screen; no slot reservation; no reordering.**

**Stage 0 — today's search, unchanged.** Today's six ring-0 candidates, today's code path, today's ordering
(`stage, movement, sideRank, alongRank, CandidateIdentity`), today's batch-then-singleton attempts. Stage 0
consumes exactly the verification count and reported work it consumes today.

**Stage 1 — ring expansion, only after a failed round.** The existing loop stops at its first round with no
acceptance, so stage 1 runs precisely where a residual is published today and cannot consume allowance that a
later legacy round would have needed. For `k = 1 .. MAX_CLEARANCE_STEPS - 1`:

```
rho_k = radius + EDGE_NODE_CLEARANCE + k * MAX_CLEARANCE_STEP          # radius 8 -> 14, 17, 20, 23
```

with `MAX_CLEARANCE_STEPS = 5` and `MAX_CLEARANCE_STEP = 3.0 wu`. For each ring, in ascending `k`, regenerate
the same six slots at `rho_k`, discard non-finite / zero-movement / over-`MAX_EDGE_NODE_EXCURSION` proposals,
and attempt them with the same batch-then-singleton schedule. A ring rejected by `verify` does not prevent
later rings from being tried.

Stage 1 expands **the families stage 0 already selected**, in the same conflict-key order; it does not reselect
families per ring and it does not regenerate C1 crossing alternatives. The C1 family is generated once, in
stage 0, and its candidates keep their places in the pool.

Work bound: stage 1 generates at most `4 rings × 6 slots × selected families` proposals in the failed round. With
`MAX_CANDIDATE_FAMILIES_PER_ROUND = 8` that is a worst case of `4 * 6 * 8 = 192` proposals, **not** 24 — 24 is
the per-family bound. The unchanged `MAX_VERIFICATIONS_PER_FRAME = 64`,
`EDGE_CONFLICT_MAX_VERIFICATION_WORK_PER_FRAME = 65 536`, `MAX_RELOCATION_ROUNDS = 4` and
`MAX_CANDIDATES_PER_BATCH = 8` bound the attempts actually made. Candidates are deduplicated inside each ring by
the existing `CandidateIdentity` rule, and the `MAX_EDGE_NODE_EXCURSION` filter removes most far rings on large
discs, so 192 is a ceiling and not an expectation. If the allowance runs out, the existing
`verificationBudgetExhausted` contract applies and the complete stage-0 residual is published.

After any acceptance the search returns to stage 0 for the next round, so expansion never changes the accepted
prefix that a ring-0 repair would have produced.

### 6.3 Interaction

No ordering dependency. Relocation runs before routing and changes the positions routing receives; that
coupling is exactly why Goal 2 is scoped per edge rather than per frame.

## 7. Recorded decision and mockup

The user chose **B + C** at the revision-3 pivot. The four-way mockup that framed the original choice is
committed as `docs/superpowers/specs/assets/2026-10-26-edge-node-crossing-residual-options.png` (sha256
`b3a00d4af68a4e55854b419fa386fc6fc60480eef47cee1d8798b31223d212b6`): **A** keep as today; **B** relocate the
obstructing node; **C** absolute bend budget for the path; **D** push discs off edges while settling.

## 8. Error handling

- Non-finite geometry, zero-length chord and non-positive radius emit no candidates, exactly as today.
- A pinned disc emits no candidates for relocation and keeps the conflict as a residual (E9).
- A routing candidate whose trimmed attachments are unavailable returns `null` and is skipped, as today.
- Exhausting any allowance publishes a residual with the existing flags, never an exception.
- Existing malformed-input exceptions in `EdgeConflictCorrection` and `EdgeRouter` are unchanged.

## 9. Test strategy

Red first. Every fixture is committed with explicit coordinates, radii, pins and expected identities.

**Routing (C).**

1. **Off-midpoint clearance.** Fixture: `A = (-18.091, 0)`, `B = (18.091, 0)`, radius-8 `graph_group`
   endpoints, radius-8 obstructing disc at `xN = -L/2 + 8 + 0.25*(L - 16) = -5.0455`, `yN = 0`. Today it is a
   `STRAIGHT` fallback. After the change it must be a non-fallback path, and the assertion is on the centre
   distance of every published sample segment: `centreDistance - 8 >= 3 - ROUTE_EPSILON`, equivalently
   surface clearance `>= 3 - ROUTE_EPSILON`. (An earlier draft demanded `>= radius + clearance` on surface
   clearance, which is a unit error.) Record the chosen `A`, `f` and shape.
2. **Ordering sensitivity, bows.** A radius-8 midpoint obstruction on a 100 wu anchor chord admits both a
   midpoint bow and a control-position bow whose defining points and samples differ. Moving the new group
   ahead of the midpoint ladder must fail this test.
3. **Ordering sensitivity, doglegs.** The existing `doglegAccepting` geometry also admits an `A = 0.75,
   f = 0.50` quadratic. Inserting the new group between the bow and dogleg ladders must fail this test.
4. **Named-fixture preservation.** `settled-reference.csv`'s workload keeps 21 straight / 1 bow / 0 dogleg /
   0 fallback with identical defining points and samples, and the existing `EdgeRouterShould` ladder fixtures
   keep their exact classifications.
5. **Genuine clamping.** At least one fixture where a control-position candidate trips `MAX_BEND` or the
   sampled length check, asserting it is skipped and the ladder continues. The listed `A`/`f` values do not
   trip the analytic ratio, so that gate must not be used as the clamping witness.
6. **Measurements, split by mode.** Preserve zero exhaustion and the performance gates on the **settled** A7a
   measurement window; preserve bounded work, valid flags, complete publication and the existing **5-second**
   `MAX_FRAME_NANOS` bound for **raw** A7b frames. Raw exhausted frames must never substitute for settled
   performance samples.

**Relocation (B).**

7. **A1-rejected ring 0.** Fixture, all radii 8, no enclosures: obstruction `N = (0,0)`; edges
   `(-100,0) → (100,0)`, `(-140,11) → (140,11)`, `(-140,-11) → (140,-11)`; all six edge endpoints actively
   pinned. Initially only the central edge conflicts with `N`. Every ring 0–3 candidate introduces a conflict
   with an upper or lower edge and fails A1; ring 4 at `rho = 23` clears all three chords. Assert the initial
   conflict set, all six ring-0 A1 rejections, acceptance at ring 4, and a final conflict set identical to the
   initial one. Under the stated schedule the first acceptance is verification 25; pin that number.
8. **Ring expansion under A2/A3.** A second fixture whose ring-0 targets are vetoed by A2 and A3 rather than
   A1, with the same identity, rejection and acceptance assertions.
9. **Goal-1 preservation.** `E1`, `E2`, `E3`, the prominence fixture,
   `stopsAtFourAcceptedRoundsWithoutClaimingExhaustion` (`verifications = 4`, `repairs = 32`) and
   `acceptsTheC2TargetAtExactlyTheMaximumExcursion` keep accepted positions, verification counts and work
   totals exactly. Any re-pinned number must come from a fixture that previously produced a residual.
10. **Exhaustion.** A fixture where stage 1 consumes the remaining allowance must set
    `verificationBudgetExhausted=true`, publish the complete stage-0 residual, leave `detectionTruncated=false`
    and not retry.

**Cross-cutting.**

11. **Frozen evidence.** `settled-reference.csv` sha256
    `e9e988b7967ea6bb4f503dfb4c5af7d3e74a3328e28e0987e71ff0f0b8ac0578`;
    `TWO_MAP_FIRST_IDLE_STEP = 3103` exact; `INITIAL_SPACING_FIRST_IDLE_LIMIT = 6000` still holds.
    Re-measured, not inferred.
12. **The reported workspace.** Re-run `doc/edge-node-crossing/probe/RealWorkspaceCrossingProbe` and record
    conflicts, repairs, classifications and the clearance audit, so acceptance covers the actual maps.

## 10. Containment

Which stage produced the user's screenshot is still unknown, because settling is path-dependent and probe 1
settles clean. The design does not depend on that answer: it hardens both stages independently and each has
its own red fixture. Test 12 is the acceptance check against the user's own data. If it still shows a violated
published path after both changes, the task stops and reports the exact edge, disc and shortfall rather than
claiming success.

## 11. Supersession ledger

Routing, in `docs/superpowers/specs/2026-10-12-graph-edge-crossing-avoidance-spec.md`:

- **§1.4 Live rung list derived from `ROUTE_LENGTH_CAP`** — the live ladder is **ten curved candidates (six
  bows and four doglegs) plus the straight entry**. It is extended by the control-position group. The
  `A = 0.5` prefix, its order and its `analyticRatio` values are retained and stay the first curved group tried.
- **§6.3 Candidate ladder** — item 2 ("control point at the chord midpoint plus the chord normal times the
  offset") is **retained unchanged**; a new item is added **after item 3 (dogleg)**, not by generalising item 2.
  Putting the new families in item 2 would place them before the dogleg ladder and is explicitly rejected.
- **§6.1–§6.3 processing, ladder, caps** — `ROUTE_LENGTH_CAP`, `MAX_BEND` and the counted routing allowances are
  retained; only the rung set grows, and the new group needs its own `R(A, f)` ratio.
- Fixtures pinning the live ladder's exact length, rung order or budget totals are re-pinned. The
  `settled-reference.csv` workload must keep 21 straight / 1 bow / 0 dogleg / 0 fallback.

Relocation, in `docs/superpowers/specs/2026-10-16-edge-node-clearance-spec.md`:

- **§3.2 Targets and bounds** — the single-ring six-target family is superseded by §6.2's staged search. The
  basis construction, zero normalization, finite checks, the "do not invent a direction" rule,
  `rho = radius + EDGE_NODE_CLEARANCE` for ring 0 and the inclusive `MAX_EDGE_NODE_EXCURSION = 64` test are
  retained unchanged.
- **§3.3 Total order and deduplication** — retained unchanged; it now orders candidates within each ring.
- **§4.1 Selection** — the eight-family bound, conflict-key ordering and "incomplete detection never enters
  selection" are retained. The six-per-family bound holds per ring; the 48-entry pool bound holds per ring;
  stage 1 expands only the families stage 0 selected and never regenerates C1 alternatives.
- **§4.2 Pool lifecycle** — the schedule, batch-then-singleton attempts and round/attempt limits are retained;
  the stage boundary of §6.2 is added, with a return to stage 0 after every acceptance.
- **§6.1–§6.4 fixtures** — `E1`, `E2`, `E3` and the prominence fixture accept at ring 0 and keep their
  coordinates, verification counts and work totals. They are assertions of Goal 1, not casualties.
- **§7.1 controls — superseded counter expectations.** The no-candidate control and the duplicate-alternative
  control **reject every one of the six ring-0 alternatives**, so stage 1 runs for them. Their expected
  `verifications`/`verificationWork` change from `6 / 432` to `30 / 2 160` and from `6 / 660` to `30 / 3 300`.
  Their residuals are unchanged. This extra work is the measured cost of the expanded search and must be
  re-pinned as such; it is not an improvement and must not be described as one.
- **§8.4 installed E3 evidence** — retained unchanged, including `verifications = 3` and
  `verificationWork = 252`, because installed E3 accepts at ring 0.

Retained without change: the `2026-10-16` amendment 1 relationship fixture, the A1–A4 verification contract,
C1 crossing geometry and its family, pin semantics, and the counted-budget framework.
