# Edge–node crossing residual — containment-aware relocation design

Task identifier: `2026-10-26-edge-node-crossing-residual`

Status: **approved by the user on 2026-10-26, revised after independent design review round 1.** The user
reported "Edges still cross over nodes" with a cropped canvas screenshot and asked whether moving node
positions would fix it, then selected option **B — relocate the obstructing node** from a four-way
comparison. This document is the design authority for the technical specification that follows it.

Revision 1 corrections: the anchor measurement in §2 Fact 2 was mislabelled and is restated against
`EdgeRouter.anchor`; the routing-impossibility claim is now a falsifiable test rather than an assertion;
§9.3 is aligned with the shared strict clearance predicate; §6.2 replaces an ill-defined "maximum free
corridor" with a finite per-proposal pre-screen built on the veto's own entry points; §6.3 pins a
slot-reservation rule that keeps the family bound at six and the pool bound at 48; §11 records the
supersession ledger; §3 is now explicitly a hypothesis.

Baseline and source: `plugin/graph-workspace` at
`bfaed5611d170b5b0b3dc93919685bd6c54035c5`. All investigation ran in the PM integration worktree; the
user's delivery checkout and their documents were read only.

## 1. Reported defect

The user's graph workspace `~/Documents/999.notebooks/01.math/math.fpg` registers three maps —
`Axiomatic_Set_Theory.mm`, `Point-set_Topology.mm`, `Real_Anaylsis.mm`. In the reported canvas region a
relationship edge is drawn as a straight line from one group disc to another and passes over a third,
non-incident disc.

## 2. Measured evidence

A read-only probe ran the unchanged production pipeline (`MapSnapshot` construction →
`ProjectionEngine` → `LayoutWorker` → `EdgeConflictCorrection` → `EdgeRouter`) over the real map files in
headless mode. Two consecutive runs produced identical output.

Projection and result:

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
Any fix aimed at budget exhaustion would be aimed at the wrong thing.

**Fact 2 — the router's lateral allowance is a fraction of the anchor chord.** `EdgeRouter.anchor`
returns `node.center()` for a node endpoint, so the length used by `EdgeRouter.bestRung` is the
**node-centre-to-node-centre** distance, not the distance between the drawn attachment points. With that
length `L`:

- a bow with control offset `f·L` has an apex of about `0.25·L` (`f = 0.50` is the largest fraction whose
  analytic ratio 1.147 794 survives `ROUTE_LENGTH_CAP = 1.25`);
- a same-side dogleg displaces the path by its full offset `f·L`, and the `f = 0.50` rung is rejected by
  the same ratio cap (1.535 184 > 1.25), so the largest legal rung is `0.30·L`;
- the required clearance is absolute: `radius + EDGE_NODE_CLEARANCE - ROUTE_EPSILON` under a strict `<`
  test, that is `8 + 3 = 11 wu` for a radius-8 disc.

Measured anchor distances of the edges adjacent to the reported pair:

| Anchor chord `L` (centre to centre) | Max bow apex `0.25·L` | Max dogleg offset `0.30·L` | Required |
| ---: | ---: | ---: | ---: |
| 33.596 wu | 8.399 | **10.079** | 11 |
| 36.182 wu | 9.045 | **10.854** | 11 |
| 41.170 wu | 10.293 | 12.351 | 11 |
| 44.120 wu | 11.030 | 13.236 | 11 |

This arithmetic *suggests* that an anchor chord shorter than `11 / 0.30 = 36.7 wu` admits no legal routed
path clear of a radius-8 disc placed on it, and that the shortest edges in the reported graph are in that
band. It is a suggestion, not a proof, and it is **not** the basis of the decision: §9 Test 1 must
falsify or confirm it through the router's own `clearsDiscs` predicate on the reproduced geometry before
option C is ruled out. The user's choice of B stands either way, because B and C act on different stages
and B is the mechanism the reported picture requires; but if Test 1 shows routing was sufficient, the
specification must say so and re-scope rather than claim an impossibility it never demonstrated.

**Fact 3 — the accepted repair sits exactly on the legality boundary.** The one accepted relocation places
the disc at perpendicular offset `11.000 wu` against a requirement of `11.000 wu`: zero margin. The
repair succeeds only while nothing else moves it, and a hull-pair veto on the same candidate leaves no
alternative target.

## 3. Root-cause hypothesis

`EdgeConflictCorrection.addEdgeOverNodeCandidates` proposes exactly six C2 targets: two perpendicular
senses crossed with the along-chord offsets `{0, −h, +h}` where `h = 2r + MIN_GAP + 3 = 25`. Every target
aims at signed distance `rho = r + EDGE_NODE_CLEARANCE`, that is, at the boundary and never beyond it.
Senses are ordered by displacement, not by whether the move is legal.

Inside nested enclosures a perpendicular move grows the disc's parent-hull extent, so the A3 veto
(`hullPairs_out ⊆ hullPairs_in`) can reject it; a move toward a neighbour trips the A2 veto
(`gapPairs_out ⊆ gapPairs_in`). Because all six candidates lie in the same ±25 wu ring, one blocked region
can reject the whole family, the conflict becomes a residual, and the router — whose allowance is
proportional to the chord — cannot rescue a short edge, leaving the straight fallback.

This is a **hypothesis**, not an established finding. The probe did not reproduce the user's exact
arrangement: on a fresh settle the single conflict is accepted and no edge violates clearance. §9 Test 1
is the experiment that decides it.

## 4. Goals

1. A C2 conflict whose obstructing disc has a retained candidate that passes the full A1–A4 verification,
   while the frame's verification budget remains, is relocated rather than published as a residual.
   Budget exhaustion, pinning, ineligible geometry and all-candidates-vetoed remain legal residual
   outcomes and are reported through the existing flags.
2. The accepted repair is never published exactly on the legality boundary.
3. The frozen settled-position evidence does not change: `settled-reference.csv` stays byte-identical and
   the pinned first-idle results stay exact.

## 5. Non-goals and deferrals

- **No routing change.** Option C (an absolute bend budget) is deferred as a separate future task. §9
  Test 1 decides whether it is also *insufficient* for this defect; the design does not assume it.
- **No layout-force change.** Option D (edge–node repulsion during relaxation) is deferred: it changes
  every settled position and invalidates `settled-reference.csv`, the first-idle pins and the 2000-node
  performance gates.
- **No shared-predicate change.** The strict `clearance < EDGE_NODE_CLEARANCE - ROUTE_EPSILON` test in
  both `EdgeConflictCorrection` and `EdgeRouter.clearsDiscs` stays exactly as it is. The margin in §6.1 is
  a property of the *proposed target*, not of the predicate.
- **No change to pins.** An actively pinned disc remains immovable and still publishes a residual, as the
  clearance specification's E9 case requires.
- **No change to enclosure membership, node radii, prominence, label placement or the settlement policy.**

## 6. Design

### 6.1 Clearance margin

Add package-private `CLEARANCE_MARGIN = 1.0` world units to `EdgeConflictCorrection`. Every C2 target aims
at `radius + EDGE_NODE_CLEARANCE + CLEARANCE_MARGIN` instead of `radius + EDGE_NODE_CLEARANCE`, so the
shared strict predicate has slack and a subsequent round that nudges the disc back cannot re-create the
crossing. This removes Fact 3. The predicate itself is untouched.

### 6.2 Per-proposal legality pre-screen

There is no "largest legal displacement" query to build: the veto entry points test a concrete candidate
position, and legality is not known to be monotone along the normal. The design therefore pre-screens
**each concrete proposal**, not a corridor:

```
preScreen(proposal) :=
      NodeSeparationProjection.violatingPairsCounted(projection, positionsWithOnlyThisMove, pinned, ...)
          .violations ⊆ baseline violations                          # A2
   && BoundaryInvariants.detectPairKeys(changedHulls(proposal), ...)
          .keys ⊆ baseline keys                                     # A3
```

using `GraphGeometryEngine.recomputeHulls` with `BoundaryInvariants.changedHulls` exactly as `verify` does.
The pre-screen reuses the veto's own entry points, so it can never disagree with them.

Rules the specification must pin:

- The pre-screen result is used **only for ordering**; it never accepts a move.
- Its work is charged to the frame's verification-work allowance, and a stop sets the existing
  `verificationBudgetExhausted` contract.
- A1 (complete conflict-set inclusion) and A4 (enclosure-path precondition) are **not** pre-screened; they
  remain decided by `verify`, because A2/A3 feasibility alone does not imply acceptance.
- The pre-screen runs on the single-move position set only. Batch legality is still decided by `verify`
  on the combined candidate positions.
- Candidates are regenerated from a fresh complete detection after every accepted round, as today.

### 6.3 Slot-reserving multi-ring family

Keep the existing six slots — `(sense σ ∈ {+1, −1}) × (along t ∈ {0, −h, +h})` with `h` unchanged — but
let each slot choose its **ring**:

```
for σ in {+1, −1}:
    for t in {0, −h, +h}:
        for k in 0 .. MAX_CLEARANCE_STEPS − 1:            # 5
            aim_k = radius + EDGE_NODE_CLEARANCE + CLEARANCE_MARGIN + k * MAX_CLEARANCE_STEP
            target = centre + (σ * aim_k − projection) * normal + t * alongTangent
            discard when non-finite, zero movement, or movement > MAX_EDGE_NODE_EXCURSION
        retain the smallest k in this slot that pre-screens legal, else the smallest k
```

with `MAX_CLEARANCE_STEPS = 5` and `MAX_CLEARANCE_STEP = 3.0 wu`. This yields **at most one candidate per
slot and therefore at most six per family** — the same family bound the governing specification already
pins — so the pool stays bounded by `MAX_CANDIDATE_FAMILIES_PER_ROUND × 6 = 48` entries exactly as today,
and no budget or timing contract changes.

Slot ordering is **pre-screened-legal slots first, then the existing order**: stage (`t = 0` before
diagonals), displacement within the chosen ring, `sideRank`, `alongRank`, then `CandidateIdentity`. Because
a slot contributes at most one candidate and the reservation is per slot, a legal later-ring diagonal can
never be pruned by the six direct targets — the defect the plain "generate 30, keep the best 12 by the old
order" scheme would have introduced.

### 6.4 Unchanged verification and budgets

A1–A4, the counted index and verification scans, `greedyDisjoint` batching with singleton retries,
`MAX_RELOCATION_ROUNDS = 4`, `MAX_VERIFICATIONS_PER_FRAME = 64`,
`EDGE_CONFLICT_MAX_VERIFICATION_WORK_PER_FRAME = 65 536`, `MAX_CANDIDATE_FAMILIES_PER_ROUND = 8` and
`MAX_CANDIDATES_PER_BATCH = 8` all stay exactly as they are. The pinned disc exclusion, the C1 crossing
family, the terminal rebuild and `MAX_EDGE_NODE_EXCURSION = 64` keep their current behaviour. The
pre-screen's added work is drawn from the same verification-work allowance, so the frame total remains
bounded by the existing cap.

### 6.5 Legacy removal

Per the repository's legacy-removal policy the fixed six-target construction is deleted, not retained
behind a flag: one active path, no compatibility branch, no parallel old/new implementation. Fixtures that
pin exact C2 target coordinates, order, selection counts, pool size or verification-work totals are
re-pinned deliberately and reconciled one by one against §11, each re-pin justified by the new family
rather than by an unexplained number.

## 7. Recorded decision and mockup

The user chose option B from a four-way composite comparison. The mockup is committed as the design record
at `docs/superpowers/specs/assets/2026-10-26-edge-node-crossing-residual-options.png`
(sha256 `b3a00d4af68a4e55854b419fa386fc6fc60480eef47cee1d8798b31223d212b6`).

The four alternatives were:

- **A — keep as today**: repair nodes, publish the residual, paint the straight fallback.
- **B — relocate the obstructing node** (chosen): the disc leaves the chord, edges stay straight.
- **C — absolute bend budget for the path**: nodes unmoved, the router gains a world-unit allowance.
- **D — push discs off edges while settling**: a repulsion force keeps the settled layout clear.

## 8. Error handling

- Ineligible geometry (non-finite coordinates, zero-length chord, non-positive radius) emits no candidates
  and keeps the conflict as a residual, exactly as today. It is not budget exhaustion.
- A pinned disc emits no candidates and keeps the conflict as a residual (E9).
- A slot whose every ring exceeds `MAX_EDGE_NODE_EXCURSION` contributes no candidate.
- A candidate that passes the pre-screen but fails `verify` is rejected like any other rejected candidate;
  the batch-then-singleton retry and the round limit are unchanged.
- Exhausting the verification allowance, the round limit or the frame work cap publishes the conflict as a
  residual with the existing flags and never as an exception.
- Existing malformed-input exceptions are unchanged.

## 9. Test strategy

Red first, in this order:

1. **Baseline falsification of the routing hypothesis.** Build the reported geometry as a fixture: the
   shortest measured anchor chord, the obstructing disc on it, two nested enclosures, sibling discs at
   `MIN_GAP`. (a) Assert through `EdgeRouter.clearsDiscs` whether any legal rung clears the disc, and record
   `edgesStraight`/`edgesBow`/`edgesDogleg`/`edgesFallback`. This either confirms the §2 Fact 2 arithmetic
   or refutes it; the specification must state which, and the decision to keep C deferred must be
   restated against the outcome. (b) With relocation disabled by an active pin on the obstructing disc,
   assert that the published path violates clearance — this proves the reported symptom is reachable from
   the router stage. (c) With the pin dormant, assert the conflict is relocated and the published path
   clears the disc by at least `radius + EDGE_NODE_CLEARANCE`.
2. **Root-cause attribution.** For the same fixture, record which veto rejects which of the six baseline
   targets, so §3's hypothesis is confirmed or replaced by the measured reason. If the fixture shows that
   relocation already succeeds and the crossing arrives by another route, the specification stops and
   re-scopes.
3. **Margin.** A fixture must assert that the accepted target sits at `radius + EDGE_NODE_CLEARANCE +
   CLEARANCE_MARGIN` and not at `radius + EDGE_NODE_CLEARANCE`. A separate fixture whose disc lies exactly
   `radius + EDGE_NODE_CLEARANCE` from the chord must **not** be reported as a conflict, matching the
   shared strict `< EDGE_NODE_CLEARANCE − ROUTE_EPSILON` predicate; a fixture `ROUTE_EPSILON` below that
   threshold must be reported.
4. **Slot reservation.** A fixture where the direct target in every slot is vetoed and a diagonal in a
   later ring is legal must retain that diagonal and resolve in one accepted round.
5. **Pre-screen agreement.** For a set of fixtures, assert that no candidate accepted by `verify` was
   pre-screened illegal, and that the pre-screen never accepts on its own.
6. **Invariants preserved.** Every existing A2/A3/A4 rejection fixture, the pinned-disc E9 fixture, the
   budget and cap reconciliation fixtures and the crossing-family fixtures stay green.
7. **No layout drift.** `src/test/resources/performance/settled-reference.csv` (sha256
   `e9e988b7967ea6bb4f503dfb4c5af7d3e74a3328e28e0987e71ff0f0b8ac0578`) remains byte-identical, and the
   pinned first-idle results — including `TypedForcesShould`'s two-map result at **3103** steps and the
   initial-spacing 3455 pin — are re-measured and must be unchanged. These are acceptance evidence, not
   inferences from "no force change".
8. **Budget evidence.** The measured graphs record `verifications`, `verificationWork` and
   `verificationBudgetExhausted` so the pre-screen and the slot-reserving family are shown not to exhaust
   the frame allowance, on both the reported-geometry fixture and the 2000-node reference scenario.

Characterisation, not a gate: sweep sibling distance and along-offset to record where the baseline gives up
and where the new family still succeeds, and record the numbers in the acceptance evidence.

## 10. Containment

The single open question is whether the reported picture is produced by the C2 veto path described in §3.
Test 1 and Test 2 in §9 answer it before any behaviour change, and the specification must refuse to
proceed if they contradict §3. That is a stop-and-re-scope gate, not an escape hatch: no implementation
step may be reordered ahead of it.

## 11. Supersession ledger

This design supersedes, in `docs/superpowers/specs/2026-10-16-edge-node-clearance-spec.md`:

- **§3 (finite C2 candidate family)** — the six fixed targets, `rho = r + EDGE_NODE_CLEARANCE` aiming, the
  `MAX_EDGE_NODE_EXCURSION`-only bound, and the stated total order are replaced by §6.1 and §6.3 here.
  The `n`/`u` basis construction, the zero normalization, the finite checks and the "do not invent a
  direction" rule are retained unchanged.
- **§4 (bounded family selection, batching and retry)** — the "at most six C2 candidates per family" and
  "at most 48 pool entries" numbers are retained; the selection policy gains the per-slot ring reservation
  and the pre-screened-first ordering of §6.2–§6.3. The eight-family bound, first-fit greedy batching, the
  singleton retries and the round/attempt limits are retained unchanged.
- **§2 (retained invariants)** — A1–A4, the counted scans, the prominence-scaled `MIN_GAP` predicate and the
  A4 enclosure-path assertion are retained verbatim. Only the candidate family feeding them changes.

Affected fixture expectations that must be re-pinned and justified individually:

| Fixture family | Superseded expectation |
| --- | --- |
| `E2` (radius-8 column) | target `(0, -11)` becomes `(0, -12)`; verification work changes |
| `E3` (diagonal) | target `(-25, -11)` becomes `(-25, -12)`; straight-path oracle recomputed |
| `E11` / "no candidate clears" | all six slots now search five rings; the "six verifications" count and the `432`-unit work figure are recomputed |
| `E12` (duplicate alternatives) | the `660`-unit work figure and the "six verifications" count are recomputed |
| Counters/stops fixtures | `verificationWork` totals shift by the pre-screen charge; every cap-reconciliation probe keeps its stated cap |

The `2026-10-16` amendment 1 (relationship fixture) is unaffected and stands. The
`2026-10-12-graph-edge-crossing-avoidance-spec.md` A1–A4 contract, C1 crossing geometry, pin semantics,
routing rules and counted-budget framework are retained except where the clauses above are superseded.
