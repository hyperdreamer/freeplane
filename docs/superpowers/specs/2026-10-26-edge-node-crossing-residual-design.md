# Edge–node crossing residual — multi-ring relocation design

Task identifier: `2026-10-26-edge-node-crossing-residual`

Status: **approved by the user on 2026-10-26, revised after independent design review rounds 1 and 2.** The
user reported "Edges still cross over nodes" with a cropped canvas screenshot and asked whether moving node
positions would fix it, then selected option **B — relocate the obstructing node** from a four-way
comparison. This document is the design authority for the technical specification that follows it.

Revision 2 removes a clearance margin that revision 1 introduced. That margin was an invention: the shared
clearance predicate is strict, so a disc at exactly `radius + EDGE_NODE_CLEARANCE` is legal, and any added
margin can only *reduce* feasibility — it breaks the existing maximum-excursion fixture and can turn a
legal A2 gap into a violation. The remaining defect is narrower and better evidenced: the C2 search never
looks beyond its first ring. Revision 2 also replaces an undefined "free corridor" with a pre-screen that
imitates `verify`'s exact A2/A3 sequence, makes that pre-screen ordering-only so it can never discard a
candidate, and pins the supersession ledger.

Baseline and source: `plugin/graph-workspace` at
`bfaed5611d170b5b0b3dc93919685bd6c54035c5`. All investigation ran in the PM integration worktree; the user's
delivery checkout and their documents were read only.

## 1. Reported defect

The user's graph workspace `~/Documents/999.notebooks/01.math/math.fpg` registers three maps —
`Axiomatic_Set_Theory.mm`, `Point-set_Topology.mm`, `Real_Anaylsis.mm`. In the reported canvas region a
relationship edge is drawn as a straight line from one group disc to another and passes over a third,
non-incident disc.

## 2. Measured evidence

A read-only probe ran the unchanged production pipeline (`MapSnapshot` construction → `ProjectionEngine` →
`LayoutWorker` → `EdgeConflictCorrection` → `EdgeRouter`) over the real map files in headless mode. Two
consecutive runs produced identical output.

| Measurement | Value |
| --- | --- |
| Source nodes in the three maps | 26 314 |
| Projected graph | **28 discs, 12 edges, 7 enclosures, 0 pins, 0 workspace relationships** |
| C1/C2 conflicts detected | 1 (`crossings=0`, `edgeOverNode=1`) |
| Relocation outcome | 1 accepted `TRANSLATION`, displacement **3.238 wu**, residual **0** |
| Router outcome | **12 straight, 0 bow, 0 dogleg, 0 fallback**, `obstacleChecks=8` |
| Clearance audit on published paths | **0 of 12** edges violate `radius + 3` |
| Frame flags | `detectionTruncated=false`, `indexBudgetExhausted=false`, `verificationBudgetExhausted=false` |

**Fact 1 — the graph is small and the budgets are not the cause.** Only nodes carrying
`<graph_group version="1"/>` become graph discs; every other node becomes a nested enclosure hull and a
group node's subtree is not descended into. The reported graph is therefore 28 discs wide, so neither the
conflict-index allowance (262 144 items) nor the routing allowance (131 072 obstacle checks) is exhausted.

**Fact 2 — the router's lateral allowance is a fraction of the anchor chord.** `EdgeRouter.anchor` returns
`node.center()` for a node endpoint, so the length `L` used by `EdgeRouter.bestRung` is the
**node-centre-to-centre** distance, not the distance between drawn attachment points. With that length:

- a bow with control offset `f·L` has an apex of about `0.25·L`; `f = 0.50` is the largest fraction whose
  analytic ratio 1.147 794 survives `ROUTE_LENGTH_CAP = 1.25`;
- a same-side dogleg displaces the path by its full offset `f·L`, and the `f = 0.50` rung is rejected by the
  same ratio cap (1.535 184 > 1.25), so the largest legal rung is `0.30·L`;
- the clearance predicate is strict: a disc violates when its distance is
  `< radius + EDGE_NODE_CLEARANCE - ROUTE_EPSILON`, so `11 wu` for a radius-8 disc.

Measured anchor distances of the edges adjacent to the reported pair:

| Anchor chord `L` (centre to centre) | Max bow apex `0.25·L` | Max dogleg offset `0.30·L` | Required |
| ---: | ---: | ---: | ---: |
| 33.596 wu | 8.399 | **10.079** | 11 |
| 36.182 wu | 9.045 | **10.854** | 11 |
| 41.170 wu | 10.293 | 12.351 | 11 |
| 44.120 wu | 11.030 | 13.236 | 11 |

This arithmetic *suggests* that an anchor chord shorter than `11 / 0.30 = 36.7 wu` admits no legal routed
path clear of a radius-8 disc placed on it, and the shortest edges here are in that band. It is a
suggestion, not a proof, and it is **not** the basis of the decision: §9 Test 1 falsifies or confirms it
through the router's own `clearsDiscs` predicate before option C is ruled out. The user's choice of B
stands either way, because B and C act on different stages — but if Test 1 shows routing was sufficient,
the specification must say so instead of claiming an impossibility it never demonstrated.

**Fact 3 — the accepted repair sits exactly on the boundary, which is legal.** The one accepted relocation
places the disc at perpendicular offset `11.000 wu` against a threshold of `11.000 wu`. The strict predicate
permits that, so the boundary landing is not itself a defect; what the measurement does show is how little
the search has to work with — one blocked ring and the family is empty.

## 3. Root-cause hypothesis

`EdgeConflictCorrection.addEdgeOverNodeCandidates` proposes exactly six C2 targets: two perpendicular senses
crossed with the along-chord offsets `{0, −h, +h}` where `h = 2r + MIN_GAP + 3 = 25`. Every target aims at
signed distance `rho = r + EDGE_NODE_CLEARANCE` — one ring, never a second. Senses are ordered by
displacement, not by whether the move is legal.

Inside nested enclosures a perpendicular move grows the disc's parent-hull extent, so the A3 veto
(`hullPairs_out ⊆ hullPairs_in`) can reject it; a move toward a neighbour trips the A2 veto
(`gapPairs_out ⊆ gapPairs_in`). Because all six candidates lie in the same ring, one blocked region can
reject the whole family. The conflict then becomes a residual, and the router — whose allowance is
proportional to the chord — cannot rescue a short edge, leaving the straight fallback.

This is a **hypothesis**, not an established finding. The probe did not reproduce the user's exact
arrangement: on a fresh settle the single conflict is accepted and no edge violates clearance. §9 Tests 1
and 2 are the experiment that decides it.

## 4. Goals

1. A C2 conflict whose obstructing disc has a retained candidate that passes the full A1–A4 verification,
   while the frame's verification budget remains, is relocated rather than published as a residual. Budget
   exhaustion, pinning, ineligible geometry, excursion overflow and all-candidates-vetoed remain legal
   residual outcomes, reported through the existing flags.
2. Expanding the search ring never removes a repair the current search would have found, and never changes a
   legal target's coordinates for the ring the current search already probes.
3. The frozen settled-position evidence does not change: `settled-reference.csv` stays byte-identical and
   the pinned first-idle results stay exact.

## 5. Non-goals and deferrals

- **No routing change.** Option C (an absolute bend budget) is deferred as a separate future task. §9 Test 1
  decides whether it is also *insufficient*; the design does not assume it.
- **No clearance-margin change.** The strict `clearance < EDGE_NODE_CLEARANCE - ROUTE_EPSILON` test in both
  `EdgeConflictCorrection` and `EdgeRouter.clearsDiscs` stays exactly as it is, and C2 targets keep aiming at
  `radius + EDGE_NODE_CLEARANCE`. Revision 1's `CLEARANCE_MARGIN` is withdrawn.
- **No layout-force change.** Option D (edge–node repulsion during relaxation) is deferred: it changes every
  settled position and invalidates `settled-reference.csv`, the first-idle pins and the 2000-node gates.
- **No change to pins.** An actively pinned disc remains immovable and still publishes a residual (E9).
- **No change to enclosure membership, node radii, prominence, label placement or the settlement policy.**

## 6. Design

### 6.1 Five rings per slot

Keep the existing six slots — `(sense σ ∈ {+1, −1}) × (along t ∈ {0, −h, +h})`, `h = 2r + MIN_GAP +
EDGE_NODE_CLEARANCE = 25` unchanged — and let each slot search five rings:

```
rho_k = disc.radius + EDGE_NODE_CLEARANCE + k * MAX_CLEARANCE_STEP      # k = 0 .. MAX_CLEARANCE_STEPS-1
target(σ, t, k) = centre + (σ * rho_k − projection) * normal + t * alongTangent
```

with `MAX_CLEARANCE_STEPS = 5` and `MAX_CLEARANCE_STEP = 3.0 wu`. For a radius-8 disc the rings sit at
11, 14, 17, 20 and 23 wu of perpendicular offset. `k = 0` reproduces today's target exactly, so Goal 2
holds by construction. A proposal is discarded when any component is non-finite, when movement is zero, or
when movement exceeds `MAX_EDGE_NODE_EXCURSION = 64` — the same rules as today, unchanged.

**Reservation rule.** For each slot, retain the candidate with the smallest `k`; that is one candidate per
slot and therefore **at most six per family**. The family bound the governing specification already pins is
preserved, so `MAX_CANDIDATE_FAMILIES_PER_ROUND × 6 = 48` pool entries is also preserved and no budget
contract changes. A later ring is only retained when every earlier ring in the same slot overflowed the
excursion cap or was ineligible; it is never dropped because a *different* slot was chosen.

**Ordering.** Slots are ordered pre-screened-legal first, then by the existing total order: stage (`t = 0`
before diagonals), displacement within the retained ring, `sideRank`, `alongRank`, then `CandidateIdentity`.
Because a slot contributes exactly one candidate, a legal diagonal can never be crowded out by the direct
targets.

**Bounded retry.** Within one round a slot does not advance past the ring it retained: A1 and A4 are decided
only by `verify`, so a retained ring that `verify` rejects makes that slot contribute nothing further and
the round ends as it does today. Later rounds exist only after an acceptance, as now. This is a deliberate
bound, not a guarantee: the design claims improvement, not completeness, and the residual set stays the
authoritative observable. Post-rejection ring advancement is out of scope for this task.

### 6.2 Ordering-only legality pre-screen

There is no "largest legal displacement" query to build: the veto entry points test a concrete candidate
position and legality is not known to be monotone along the normal. The pre-screen therefore evaluates
**each concrete proposal** with the same sequence `verify` runs, restricted to A2 and A3:

```
candidatePositions = positions with only this proposal applied
changed            = BoundaryInvariants.changedHulls(nodeHullsByKey, currentPositions, candidatePositions)
candidateGeometry  = GraphGeometryEngine.recomputeHulls(projection, candidatePositions, currentGeometry,
                                                        changed, metrics)
A2legal  := NodeSeparationProjection.violatingPairsCounted(projection, candidatePositions, allowance)
                .pairs()  ⊆  baseline A2 pairs
A3legal  := BoundaryInvariants.detectPairKeys(projection, candidateGeometry, allowance)
                .pairKeys()  ⊆  baseline A3 keys
```

The specification must pin the exact overloads, argument order, the identity form used for each subset test
(`pairs()` vs `pairKeys()`), and truncation handling; the sketches above name the production entry points,
not new geometry. `violatingPairsCounted` takes no `pinned` argument — the pinned exclusion stays where it
is today, in candidate generation.

Binding rules:

- **Ordering-only.** An `ILLEGAL` verdict *demotes* the candidate to the end of the pool; it never removes
  it. Nothing is filtered out, so a combination that only works as a batch is still available to
  `greedyDisjoint`, and the pre-screen can never contradict an acceptance.
- **Baselines are per accepted-position snapshot.** The baseline A2 pair set and A3 key set are computed
  once from `currentPositions`/`currentGeometry` at the start of each round and cached for that round, not
  recomputed per proposal. Candidate-side scans are per proposal.
- **The pre-screen is singleton-only.** Batch legality is still decided by `verify` on the combined
  positions; a batch may therefore succeed where each member alone is pre-screened `ILLEGAL`, and that is
  correct behaviour.
- **Tri-state result.** `LEGAL`, `ILLEGAL`, and `STOP`. `STOP` means the pre-screen's own work exhausted the
  frame allowance; it stops candidate generation and verification for the frame, keeps the last complete
  `C_in` as the residual, sets `verificationBudgetExhausted`, and charges every consumed unit to
  `verificationWork` exactly as `verify` does. Partial A2/A3 sets never influence ordering or publication.
- **A1 and A4 are not pre-screened.** A2/A3 feasibility does not imply acceptance; only `verify` accepts.
- Candidates are regenerated from a fresh complete detection after every accepted round, as today.

### 6.3 Unchanged verification and budgets

A1–A4, the counted index and verification scans, `greedyDisjoint` batching with singleton retries,
`MAX_RELOCATION_ROUNDS = 4`, `MAX_VERIFICATIONS_PER_FRAME = 64`,
`EDGE_CONFLICT_MAX_VERIFICATION_WORK_PER_FRAME = 65 536`, `MAX_CANDIDATE_FAMILIES_PER_ROUND = 8`,
`MAX_CANDIDATES_PER_BATCH = 8` and `MAX_EDGE_NODE_EXCURSION = 64` all stay as they are. The pre-screen draws
its work from the same verification-work allowance, so the frame total remains bounded by the existing cap.
The cap is a bound on counted work, not a latency guarantee: the pre-screen adds up to five singleton A2/A3
scans per slot before `verify` runs, and §9 Test 8 must measure the added cost on a workload containing an
actual C2 conflict rather than on a crossing-only workload.

### 6.4 Legacy removal

Per the repository's legacy-removal policy the single-ring construction is deleted, not retained behind a
flag: one active path, no compatibility branch, no parallel old/new implementation. Fixtures that pin exact
C2 selection counts, pool size, ordering or verification-work totals are re-pinned deliberately and
reconciled against §11, each re-pin justified by the new search rather than by an unexplained number.

## 7. Recorded decision and mockup

The user chose option B from a four-way composite comparison. The mockup is committed as the design record
at `docs/superpowers/specs/assets/2026-10-26-edge-node-crossing-residual-options.png` (sha256
`b3a00d4af68a4e55854b419fa386fc6fc60480eef47cee1d8798b31223d212b6`). The alternatives were: **A** keep as
today (repair nodes, publish the residual); **B** relocate the obstructing node (chosen); **C** absolute
bend budget for the path; **D** push discs off edges while settling.

## 8. Error handling

- Ineligible geometry (non-finite coordinates, zero-length chord, non-positive radius) emits no candidates
  and keeps the conflict as a residual, exactly as today. It is not budget exhaustion.
- A pinned disc emits no candidates and keeps the conflict as a residual (E9).
- A slot whose every ring exceeds `MAX_EDGE_NODE_EXCURSION` contributes no candidate.
- A candidate rejected by `verify` is rejected like today; batch-then-singleton retry and the round limit are
  unchanged, and the slot does not advance.
- Exhausting the verification allowance, the round limit, the frame work cap or a pre-screen `STOP` publishes
  the conflict as a residual with the existing flags and never as an exception.
- Existing malformed-input exceptions are unchanged.

## 9. Test strategy

Red first, in this order:

1. **Routing falsification.** Checked-in fixture, fully pinned: the four reported edges' anchor chords and
   disc coordinates, both endpoints at radius 8, one obstructing disc of radius 8 centred on the 36.182 wu
   chord, map identity `00000000-0000-0000-0000-000000000001`, generation 1, `AwtGeometryTextMetrics`
   Dialog/plain/12 with an antialiased `FontRenderContext`, all nodes visible, no pins, no enclosures unless
   a sub-case needs them. (a) Assert through `EdgeRouter.clearsDiscs` whether any legal rung clears the disc
   and record `edgesStraight`/`edgesBow`/`edgesDogleg`/`edgesFallback`. This confirms or refutes Fact 2; the
   specification must state which, and the decision to keep C deferred must be restated against the outcome.
   (b) With an active pin on the obstructing disc, assert the published path violates clearance — proving the
   symptom is reachable from the router stage. (c) With the pin dormant, assert the conflict is relocated and
   the published path clears the disc.
2. **Veto attribution.** A test-only oracle that runs A2 and A3 independently on each of the six baseline
   targets and records every observed veto, not only the first, because `verify` short-circuits internally.
   The oracle must be built on the production geometry entry points listed in §6.2. Its output confirms §3 or
   replaces it with the measured reason. If it shows that relocation already succeeds and the crossing
   arrives by another route, the specification stops and re-scopes.
3. **Boundary predicate.** A disc at exactly `radius + EDGE_NODE_CLEARANCE - ROUTE_EPSILON` is **not** a
   conflict (the predicate compares equal values); a disc at
   `radius + EDGE_NODE_CLEARANCE - ROUTE_EPSILON - delta` with a measured positive `delta` **is** a conflict.
   Rings are `k * 3.0` from `radius + EDGE_NODE_CLEARANCE`, so no ring lands on the epsilon boundary.
4. **Ring expansion.** A fixture where all six ring-0 targets are vetoed by A2 or A3 and the ring-1 or ring-2
   target in one slot is legal must resolve in one accepted round, with the repair's displacement equal to
   the expected ring.
5. **Pre-screen cannot discard.** A fixture where the only workable candidate is pre-screened `ILLEGAL` alone
   but accepted in a batch, asserting the candidate was still present in the pool and the batch was accepted.
   Plus a fixture asserting `ILLEGAL` candidates are demoted, not removed, from the pool order.
6. **Pre-screen stop.** A fixture whose pre-screen alone exhausts the verification allowance must produce
   `verificationBudgetExhausted=true`, an unchanged complete residual, `detection=false`, and no retry.
7. **Invariants preserved.** Every existing A2/A3/A4 rejection fixture, the pinned-disc E9 fixture, the
   maximum-excursion fixture `acceptsTheC2TargetAtExactlyTheMaximumExcursion`, the budget and cap
   reconciliation fixtures and the crossing-family fixtures stay green with unchanged accepted positions.
8. **No layout drift and budget evidence.** `src/test/resources/performance/settled-reference.csv` (sha256
   `e9e988b7967ea6bb4f503dfb4c5af7d3e74a3328e28e0987e71ff0f0b8ac0578`) stays byte-identical; the pinned
   first-idle results — `TypedForcesShould`'s two-map result at **3103** steps and the initial-spacing
   **3455** pin — are re-measured and must be unchanged. On the reported-geometry fixture and the 2000-node
   reference scenario, record `verifications`, `verificationWork` and `verificationBudgetExhausted` to show
   the pre-screen does not exhaust the frame allowance.

Characterisation, not a gate: sweep sibling distance and along-offset to record where the baseline gives up
and where the multi-ring search still succeeds, and record the numbers in the acceptance evidence.

## 10. Containment

The single open question is whether the reported picture is produced by the C2 single-ring exhaustion of §3.
Tests 1 and 2 answer it before any behaviour change, and the specification must refuse to proceed if they
contradict §3. That is a stop-and-re-scope gate: no implementation step may be reordered ahead of it.

## 11. Supersession ledger

This design supersedes, in `docs/superpowers/specs/2026-10-16-edge-node-clearance-spec.md`:

- **§3.2 Targets and bounds** — the six single-ring targets and `MAX_EDGE_NODE_EXCURSION` as the only spatial
  bound are replaced by §6.1's five rings per slot. `rho = radius + EDGE_NODE_CLEARANCE` is retained
  unchanged as ring 0; the `n`/`u` basis, the zero normalization, the finite checks, the "do not invent a
  direction" rule and the 64-unit inclusive excursion test are retained unchanged.
- **§3.3 Total order and deduplication** — `(stage, movement, sideRank, alongRank, candidateIdentity)` is
  retained as the intra-family order; the pre-screened-legal-first key of §6.1 is prepended to it.
- **§4.1 Selection** — the eight-family bound, the conflict-key ordering and "incomplete initial detection
  never enters selection" are retained. The "at most six C2 candidates per family" and "at most 48 pool
  entries" numbers are retained by construction under the reservation rule.
- **§4.2 Pool lifecycle** — the schedule, batch-then-singleton attempts and the round/attempt limits are
  retained; §6.1's bounded no-retry rule and §6.2's tri-state pre-screen result are added.
- **§6.1–§6.4 fixtures** — `E1 — nine independent obstructions`,
  `E2 — preferred side blocked`, `E3 — seven-node column/diagonal`, the prominence fixture and the
  no-candidate control keep their coordinates for the ring they select. `E1`'s target moves from
  `150i + 11` only if a later ring is selected; `E2` retains `(0, -11)` and `E3` retains `(-25, -11)` when
  ring 0 succeeds. **What changes is the selection count, the verification count and the
  `verificationWork` totals** (today `E2 = 30` and `E3 = 252`), which the specification must recompute and
  re-pin, together with `E11`'s `432`, `E12`'s `660` and the prominence fixture's `1 755`.
- **§7.1 controls** — the counters/stops probes and their cap reconciliation are retained; every pinned
  `verificationWork` total is recomputed for the added pre-screen charge.
- **§8.4 evidence** — the installed E3 replay and its published-position assertions are re-pinned against the
  positions the new search accepts.

Retained without change: the `2026-10-16` amendment 1 relationship fixture, the
`2026-10-12-graph-edge-crossing-avoidance-spec.md` A1–A4 contract, the C1 crossing geometry and family, pin
semantics, the EdgeRouter rules and rungs, and the counted-budget framework.
