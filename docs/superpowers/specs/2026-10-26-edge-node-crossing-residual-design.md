# Graph edge–node crossings — two-stage fix (relocation expansion + off-midpoint routing)

Task identifier: `2026-10-26-edge-node-crossing-residual`

Status: **approved by the user on 2026-10-26 at the revision-3 pivot.** The user reported "Edges still cross
over nodes" with a cropped canvas screenshot, asked whether moving node positions would fix it, initially
chose relocation only (**B**), and after the routing premise was measured and refuted re-scoped to **B + C**
as the honest reading of the evidence. This document is the design authority for the technical
specification that follows it.

Revision history that matters for review:

- r1 proposed a `CLEARANCE_MARGIN` that only reduced feasibility; withdrawn.
- r2 claimed routing could never clear an anchor chord below 36.7 wu and used that to justify
  relocation-only. **Measured and refuted** (§2 Fact 4).
- r3 (this revision) drops the legality pre-screen entirely and replaces the ring-reservation rule with a
  staged search, because both were shown to destroy existing in-budget repairs. It adds the routing change
  the measurement actually calls for.

Baseline: `plugin/graph-workspace` at `bfaed5611d170b5b0b3dc93919685bd6c54035c5`. All investigation ran in
the PM integration worktree; the user's delivery checkout and their documents were read only.

## 1. Reported defect

The user's workspace `~/Documents/999.notebooks/01.math/math.fpg` registers three maps —
`Axiomatic_Set_Theory.mm`, `Point-set_Topology.mm`, `Real_Anaylsis.mm`. In the reported canvas region a
relationship edge is drawn as a straight line from one group disc to another and passes over a third,
non-incident disc.

## 2. Measured evidence

Two read-only probes ran the unchanged production pipeline headlessly, two consecutive runs each, identical
output.

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

**Fact 2 — the router's lateral allowance is a fraction of the anchor chord.** `EdgeRouter.anchor` returns
`node.center()` for a node endpoint, so `EdgeRouter.bestRung` scales offsets by the **centre-to-centre**
distance. A bow with control offset `f·L` peaks at about `0.25·L`; a same-side dogleg displaces by `f·L`. The
`f = 0.50` dogleg is rejected by `ROUTE_LENGTH_CAP = 1.25`, so the largest legal dogleg rung is `0.30·L`.
Clearance is strict: a disc violates when distance `< radius + EDGE_NODE_CLEARANCE - ROUTE_EPSILON`.

Measured anchor chords adjacent to the reported pair: 33.596, 36.182, 41.170, 44.120 wu.

### Probe 2 — router capability on exactly that geometry

`RouterClearanceProbe` builds A, B (radius 8, marked `graph_group`) at the measured chord, one radius-8
obstructing disc N, one native connector A→B, no pins, no other content, and calls the production
`EdgeRouter`. Reported clearance is the distance from N's centre, minus N's radius.

| Anchor chord | N at chord midpoint | N at 25 % along the drawn chord |
| ---: | --- | --- |
| 33.596 wu | `STRAIGHT`, clearance **−8.000** | `STRAIGHT`, clearance **−8.000** |
| 36.182 wu | **`BOW`, clearance +3.183** | `STRAIGHT`, clearance **−8.000** |
| 41.170 wu | **`BOW`, clearance +4.729** | `STRAIGHT`, clearance **−8.000** |
| 44.120 wu | **`BOW`, clearance +5.445** | `STRAIGHT`, clearance **−8.000** |

**Fact 3 — the router's blind spot is symmetry, not magnitude.** Every bow and every dogleg in the live
ladder is built from the **chord midpoint** and the **midpoint normal** (`EdgeRouter.bowPath`: control point
at the midpoint plus normal×offset; `EdgeRouter.doglegPath`: bends at 1/3 and 2/3 on the same normal). The
family is therefore symmetric about the midpoint. An obstacle displaced along the chord defeats **every**
rung of both ladders even when there is ample lateral room — clearance stays at −8.000 in all four
off-midpoint rows above.

**Fact 4 — the r2 routing-impossibility claim is refuted.** r2 asserted no anchor chord under 36.7 wu could
be routed clear. The 36.182 wu chord is cleared by a legal `0.50` bow with 3.183 wu of clearance. The real
floor is between 33.596 and 36.182 wu. The r2 premise — and therefore the argument that relocation is the
only possible mechanism — is withdrawn.

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

**Routing exhausts a midpoint-symmetric ladder.** Fact 3: for an obstacle away from the chord midpoint, no
rung of either ladder can reach it, regardless of available length.

These are hypotheses. Probe 1 did not reproduce the user's exact arrangement — on a fresh settle the single
conflict is accepted and nothing is violated — so which stage produced the screenshot is still open (§10).

## 4. Goals

1. **Relocation never loses ground.** For every input on which the current search accepts a repair, the new
   search accepts the same repair at the same coordinates with the same verification count and the same
   reported work. Expansion is attempted only where the current search fails.
2. **Routing never loses ground.** For every edge the current router clears on a midpoint rung, the new
   ladder keeps that exact path, classification and sample list. Off-midpoint rungs are tried only after the
   midpoint rungs fail.
3. **Off-midpoint obstacles become routable.** An obstacle displaced along the chord — measured above at
   −8.000 wu clearance and `STRAIGHT` — is cleared by a legal routed path where one exists.
4. **Relocation reaches a second ring.** A conflict whose every ring-0 target is vetoed by A2 or A3 but
   whose ring 1–4 target is legal is repaired rather than published as a residual.
5. Frozen evidence does not change: `settled-reference.csv` stays byte-identical, `TWO_MAP_FIRST_IDLE_STEP =
   3103` still holds, and `stopsAtFourAcceptedRoundsWithoutClaimingExhaustion` keeps `verifications = 4`
   with `repairs = 32`.

## 5. Non-goals

- **No legality pre-screen.** r2's A2/A3 pre-screen is withdrawn. It starves the shared verification-work
  allowance and can stop a frame with zero repairs, so it is not an optimisation worth having.
- **No clearance-margin change.** The strict predicate is untouched; C2 keeps aiming at
  `radius + EDGE_NODE_CLEARANCE`.
- **No layout-force change.** Option D stays deferred: it moves every settled position and invalidates
  `settled-reference.csv`, the first-idle pins and the performance gates.
- **No new public API.** Both changes are internal to `EdgeConflictCorrection` and `EdgeRouter`; no
  `freeplane_api` or core-module change.
- **No change to pins, enclosure membership, node radii, prominence, label placement or settlement policy.**
  A pinned disc remains immovable and still publishes a residual (E9).
- **No guarantee of completeness.** Residuals remain a legal outcome and the authoritative observable.

## 6. Design

The two stages are independent; neither depends on the other's outcome.

### 6.1 Routing: an apex-positioned bend family

Keep the live midpoint ladder exactly as it is, and extend it **after** it fails. For a chord with normal `n`,
tangent `u`, chord length `L`, anchors `p0`, `p1`:

```
apex(t) = p0 + A * (p1 - p0) + t * L * n          A = apex fraction along the chord
```

and build a quadratic Bézier from the trimmed attachments to `apex(t)`. The family is:

```
APEX_FRACTIONS   = { 0.5, 0.25, 0.75, 1.0/3.0, 2.0/3.0 }     # 0.5 first: today's midpoint bows
SIDE_ORDER       = { +1, -1 }
OFFSET_FRACTIONS = the live bow list, {0.15, -0.15, 0.30, -0.30, 0.50, -0.50}
```

`A = 0.5` with the bow list **is** today's bow rung set, so Goal 2 holds by construction provided the ladder
tries the entire `A = 0.5` group first. The extended ladder therefore keeps the published order
`straight → bow ladder (A = 0.5) → dogleg ladder → apex families (A ≠ 0.5)` exactly as `EdgeRouter.route`
sequences them today, and only the last group is new. Every candidate is subject to the existing, unchanged
gates: `|offset| <= MAX_BEND`, `analyticRatio(fraction, bow) <= ROUTE_LENGTH_CAP`, sampled
`length <= ROUTE_LENGTH_CAP * chordLength`, and `clearsDiscs` against all discs and edges.

Iteration order within the new group is total and deterministic: `A` in `APEX_FRACTIONS` order (skipping the
`A = 0.5` entry already consumed by the bow ladder), then `fraction` in `OFFSET_FRACTIONS` order, then side by
the sign already carried by the fraction. A dogleg analogue is **not** added: the dogleg's bends are already at
1/3 and 2/3, and the apex families at `A = 1/3` and `A = 2/3` cover the same reach with one bend instead of
two, which keeps the added work small.

The off-midpoint `analyticRatio` is not the circular-arc ratio of the midpoint bow, so the specification must
derive it and pin the exact per-`A` values, and must prove each `A = 0.5` entry reproduces today's ratio test
result bit-for-bit before the extension is reachable. Where an off-midpoint family member would fail the
length ratio it is skipped exactly as today's rungs are.

Fallback behaviour is unchanged: if every rung in the extended ladder fails, the straight chord is published
and `edgesFallback` is incremented.

### 6.2 Relocation: a staged search

Replace the single-ring family with two stages. **No pre-screen; no slot reservation.**

**Stage 0 — today's search, unchanged.** Generate today's six ring-0 candidates with today's code path,
today's ordering (`stage, movement, sideRank, alongRank, CandidateIdentity`), and today's batch-then-singleton
attempt schedule. Stage 0 consumes exactly the verification count and reported work it consumes today, so
Goal 1 holds by construction.

**Stage 1 — ring expansion, only if stage 0 fails.** Reached only when stage 0 produced no acceptance in this
round, which is exactly the case that yields a residual today. For `k = 1 .. MAX_CLEARANCE_STEPS - 1`:

```
rho_k = radius + EDGE_NODE_CLEARANCE + k * MAX_CLEARANCE_STEP          # radius 8 -> 14, 17, 20, 23
```

with `MAX_CLEARANCE_STEPS = 5` and `MAX_CLEARANCE_STEP = 3.0 wu`, generate the same six slots at `rho_k`,
discard non-finite / zero-movement / over-`MAX_EDGE_NODE_EXCURSION` proposals, and attempt them with the same
batch-then-singleton schedule. Rings are tried in ascending `k`, and within a ring by the existing total
order. A slot may therefore contribute at each ring, and a ring rejected by `verify` does not prevent later
rings from being tried — the limitation r2's reservation rule created.

Bound: `stage1Candidates = (MAX_CLEARANCE_STEPS - 1) * 6 = 24` per round, and the whole round remains subject
to the unchanged `MAX_VERIFICATIONS_PER_FRAME = 64`, `EDGE_CONFLICT_MAX_VERIFICATION_WORK_PER_FRAME = 65 536`,
`MAX_RELOCATION_ROUNDS = 4`, `MAX_CANDIDATE_FAMILIES_PER_ROUND = 8` and `MAX_CANDIDATES_PER_BATCH = 8`.
Stage 1 is only attempted while the frame's allowance remains; a stop sets the existing
`verificationBudgetExhausted` contract and publishes the complete residual that stage 0 would have published.

Why this ordering matters: `stopsAtFourAcceptedRoundsWithoutClaimingExhaustion` accepts in every round, so
stage 1 never runs there and its `verifications = 4` is preserved. `E1`/`E2`/`E3` accept at ring 0, so their
accepted positions and work totals are preserved. Only fixtures that produce a residual today can spend the
extra work — and those fixtures are precisely where a residual was previously unavoidable, so any resulting
change to their counters is an improvement, not a regression, and must be re-pinned as such.

### 6.3 Interaction

None required. Relocation works on positions before routing; routing works on the published positions. The
design does not claim that one makes the other unnecessary, and each must be independently sufficient for
its own fixture.

## 7. Recorded decision and mockup

The user chose **B + C** at the revision-3 pivot. The four-way mockup that framed the original choice is
committed as `docs/superpowers/specs/assets/2026-10-26-edge-node-crossing-residual-options.png` (sha256
`b3a00d4af68a4e55854b419fa386fc6fc60480eef47cee1d8798b31223d212b6`): **A** keep as today; **B** relocate the
obstructing node; **C** absolute bend budget for the path; **D** push discs off edges while settling. The
screenshot's blue/orange pair is the reported edge; the dashed disc is the obstruction.

## 8. Error handling

- Non-finite geometry, zero-length chord and non-positive radius emit no candidates, exactly as today.
- A pinned disc emits no candidates for relocation and keeps the conflict as a residual (E9).
- A routing candidate whose trimmed attachments are unavailable returns `null` and is skipped, as today.
- Exhausting any allowance publishes a residual with the existing flags, never an exception.
- Existing malformed-input exceptions in `EdgeConflictCorrection` and `EdgeRouter` are unchanged.

## 9. Test strategy

Red first.

**Routing (C).**
1. `RouterClearanceProbe` geometry as a checked-in fixture: chord 36.182 wu, radius-8 endpoints, radius-8
   obstacle at 25 % along the drawn chord. Today it yields `STRAIGHT` with −8.000 clearance; after the change
   it must yield a non-fallback path whose clearance is `>= radius + EDGE_NODE_CLEARANCE`. Record the shape
   and apex fraction actually chosen.
2. `A = 0.5` preservation: for the `settled-reference.csv` workload and for the existing `EdgeRouterShould`
   ladders, assert identical `edgesStraight`/`edgesBow`/`edgesDogleg`/`edgesFallback`, identical accepted
   paths and identical sample lists. This test must fail if the midpoint group is not tried first.
3. Clamping: an off-midpoint candidate that trips `ROUTE_LENGTH_CAP` or `MAX_BEND` is skipped, and the ladder
   continues rather than falling back.
4. Budget: `routeBudgetExhausted` and `detectionTruncated` behaviour on the 2000-node reference scenario with
   the extended ladder, showing the extra rungs do not exhaust the frame allowance.

**Relocation (B).**
5. Ring expansion: a fixture whose every ring-0 target is vetoed by A2 and whose ring-1 or ring-2 target in
   one slot is legal must be repaired in one accepted round, with the repair displacement equal to the
   expected ring.
6. A1-rejected retained ring: a fixture where ring 0 fails A1 but a later ring clears every chord must be
   repaired — the case r2's reservation rule could not reach.
7. Goal-1 preservation: `E1`, `E2`, `E3`, the prominence fixture, `stopsAtFourAcceptedRoundsWithoutClaimingExhaustion`
   (`verifications = 4`, `repairs = 32`) and the maximum-excursion fixture
   `acceptsTheC2TargetAtExactlyTheMaximumExcursion` keep their accepted positions, verification counts and
   work totals exactly. Any re-pinned number must be shown to come from a fixture that previously produced a
   residual.
8. Exhaustion: a fixture where stage 1 consumes the remaining allowance must set
   `verificationBudgetExhausted=true`, publish the complete residual that stage 0 would have published, leave
   `detectionTruncated=false`, and not retry.

**Cross-cutting.**
9. Frozen evidence: `src/test/resources/performance/settled-reference.csv` sha256
   `e9e988b7967ea6bb4f503dfb4c5af7d3e74a3328e28e0987e71ff0f0b8ac0578`; `TypedForcesShould`'s
   `TWO_MAP_FIRST_IDLE_STEP = 3103` exact; `INITIAL_SPACING_FIRST_IDLE_LIMIT = 6000` still holds. Re-measured,
   not inferred.
10. The reported workspace: re-run the real-map probe and record conflicts, repairs, router classifications
    and the clearance audit, so the acceptance evidence covers the actual maps rather than a synthetic
    analogue.

## 10. Containment

Which stage produced the user's screenshot is still unknown, because settling is path-dependent and probe 1
settles clean. The design therefore does not depend on that answer: it hardens both stages independently, and
each has its own red fixture. Test 10's real-map run is the acceptance check against the user's own data.

If the real-map run still shows a violated published path after both changes, the task stops and reports the
remaining residual — with the exact edge, disc and shortfall — rather than claiming success.

## 11. Supersession ledger

Routing, in `docs/superpowers/specs/2026-10-12-graph-edge-crossing-avoidance-spec.md`:

- **§1.4 Live rung list derived from `ROUTE_LENGTH_CAP`** — the eight-entry live ladder is extended by the
  off-midpoint apex families of §6.1. The `A = 0.5` prefix, its order and its `analyticRatio` values are
  retained and remain the first group tried.
- **§6.3 Candidate ladder, item 2 "Single bow"** — "control point at the chord midpoint plus the chord normal
  times the offset" becomes "control point at apex fraction `A` along the chord plus the chord normal times
  the offset", with `A = 0.5` first. Dogleg item and its ordering are unchanged.
- **§6.1–§6.3 processing, ladder, caps** — the caps `ROUTE_LENGTH_CAP`, `MAX_BEND`, and the counted routing
  allowances are retained unchanged; only the rung set grows.
- Fixtures that pin the live ladder's exact length, rung count or budget totals are re-pinned, and the
  `settled-reference.csv` workload's classifications must remain 21 straight / 1 bow / 0 dogleg / 0 fallback.

Relocation, in `docs/superpowers/specs/2026-10-16-edge-node-clearance-spec.md`:

- **§3.2 Targets and bounds** — the single-ring six-target family is superseded by §6.2's staged search. The
  basis construction, zero normalization, finite checks, the "do not invent a direction" rule,
  `rho = radius + EDGE_NODE_CLEARANCE` for ring 0, and the inclusive `MAX_EDGE_NODE_EXCURSION = 64` test are
  retained unchanged.
- **§3.3 Total order and deduplication** — retained unchanged; it now orders candidates within each ring.
- **§4.1 Selection** — the eight-family bound, conflict-key ordering and "incomplete detection never enters
  selection" are retained. The six-per-family and 48-per-pool bounds are retained *per stage*; stage 1 scans
  at most 24 further proposals per round.
- **§4.2 Pool lifecycle** — the schedule, batch-then-singleton attempts and round/attempt limits are retained;
  the stage boundary of §6.2 is added.
- **§6.1–§6.4 fixtures** — `E1`, `E2`, `E3`, the prominence fixture and both §7.1 controls keep their
  coordinates, verification counts and work totals because they accept at ring 0. Their numbers are assertions
  of Goal 1, not casualties of this change.
- **§7.1 controls / counters** — the counted units, caps and cap-reconciliation probes are retained; the
  `verificationWork` totals stay identical for every ring-0-accepting fixture.
- **§8.4 installed E3 evidence** — retained unchanged, including `verifications = 3` and
  `verificationWork = 252`, because installed E3 accepts at ring 0.

Retained without change: the `2026-10-16` amendment 1 relationship fixture, the A1–A4 verification contract,
C1 crossing geometry and its family, pin semantics, and the counted-budget framework.
