# Edge–node crossing residual — containment-aware relocation design

Task identifier: `2026-10-26-edge-node-crossing-residual`

Status: **approved by the user on 2026-10-26**. The user reported "Edges still cross over nodes" with a
cropped canvas screenshot and asked whether moving node positions would fix it, then selected option
**B — relocate the obstructing node** from a four-way comparison. This document is the design
authority for the technical specification that follows it.

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
`ProjectionEngine` → `LayoutWorker` → `EdgeConflictCorrection` → `EdgeRouter`) over the real map files
in headless mode. Two consecutive runs produced identical output.

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

Two structural facts follow from the measurement and are the reason this design exists.

**Fact 1 — the graph is small and the budgets are not the cause.** Only nodes carrying
`<graph_group version="1"/>` become graph discs; every other node becomes a nested enclosure hull and a
group node's subtree is not descended into. The reported graph is therefore 28 discs wide, so neither
the conflict-index allowance (262 144 items) nor the routing allowance (131 072 obstacle checks) is
exhausted. Any fix aimed at budget exhaustion would be aimed at the wrong thing.

**Fact 2 — the router can never clear a short chord, so relocation is the only mechanism that can.**
The required clearance is absolute: `radius + EDGE_NODE_CLEARANCE = 8 + 3 = 11 wu`. The router's lateral
allowance is a fraction of the anchor chord: a bow control offset of `f·L` gives an apex of `0.25·L`, and
the largest dogleg that survives `ROUTE_LENGTH_CAP = 1.25` is `0.30·L`. Measured on the reported edges:

| Attachment chord `L` | Max legal bow `0.25·L` | Max legal dogleg `0.30·L` | Required |
| ---: | ---: | ---: | ---: |
| 33.596 wu | 8.399 | **10.079** | 11 |
| 36.182 wu | 9.045 | **10.854** | 11 |
| 41.170 wu | 10.293 | 12.351 | 11 |
| 44.120 wu | 11.030 | 13.236 | 11 |

A chord shorter than `11 / 0.30 = 36.7 wu` admits no legal routed path that clears a radius-8 disc
placed on it. The reported edges are in that band.

**Fact 3 — the accepted repair sits exactly on the legality boundary.** The one accepted relocation
places the disc at perpendicular offset `11.000 wu` against a requirement of `11.000 wu`: zero margin.
The repair succeeds only while nothing else moves it, and a hull-pair veto on the same candidate leaves
no alternative.

## 3. Root cause

`EdgeConflictCorrection.addEdgeOverNodeCandidates` proposes exactly six C2 targets: two perpendicular
senses crossed with the along-chord offsets `{0, −h, +h}` where `h = 2r + MIN_GAP + 3 = 25`. Every
target aims at signed distance `rho = r + EDGE_NODE_CLEARANCE`, that is, at the boundary and never
beyond it. Senses are ordered by displacement, not by whether the move is legal.

Inside nested enclosures a perpendicular move grows the disc's parent-hull extent, so the A3 veto
(`hullPairs_out ⊆ hullPairs_in`) rejects it; a move toward a neighbour trips the A2 veto
(`gapPairs_out ⊆ gapPairs_in`). Because all six candidates lie in the same ±25 wu ring, one blocked
region rejects the whole family, the conflict becomes a residual the router cannot repair, and the
straight fallback is painted. That is the reported picture.

## 4. Goals

1. A C2 conflict whose obstructing disc can be relocated **without creating a new hull pair and without
   creating a new node-gap violation** is always relocated.
2. The repair is never published exactly on the legality boundary.
3. No settled position changes: the frozen layout fixtures, the `settled-reference.csv` ledger and the
   performance gates are untouched.

## 5. Non-goals and deferrals

- **No routing change.** Option C (an absolute bend budget) cannot fix a chord under 36.7 wu and is
  therefore insufficient for this defect; it is deferred as a separate future task for long chords.
- **No layout-force change.** Option D (edge–node repulsion during relaxation) is deferred: it changes
  every settled position and invalidates `settled-reference.csv`, the first-idle pins and the
  2000-node performance gates.
- **No change to pins.** An actively pinned disc remains immovable and still publishes a residual, as
  the clearance specification's E9 case requires.
- **No change to enclosure membership, node radii, prominence, label placement or the settlement
  policy.**

## 6. Design

### 6.1 Clearance margin

Add package-private `CLEARANCE_MARGIN = 1.0` world units to `EdgeConflictCorrection`. Every C2 target
aims at `radius + EDGE_NODE_CLEARANCE + CLEARANCE_MARGIN` instead of `radius + EDGE_NODE_CLEARANCE`, so
the router's `>=` clearance test has slack and a subsequent round that nudges the disc back cannot
re-create the crossing. This directly removes Fact 3.

### 6.2 Directed side selection

For each perpendicular sense, measure the **free corridor**: the largest perpendicular displacement that
keeps the candidate legal, computed from the same data the veto already uses — the new-line hull-pair
predicate of `BoundaryInvariants` for A3 and the `r_i + r_j + NodeSeparationProjection.MIN_GAP` predicate
for A2. A sense whose corridor admits the required shift is *feasible*. Senses are ordered
**feasible-first, then by displacement**, preserving the existing displacement tie-break whenever both
senses are feasible.

Binding constraint: the corridor must be computed by the same predicate the veto applies, not by a second
approximation of it. If a cheap closed-form bound diverges from the veto on any fixture, the veto wins and
the bound is wrong. The specification must name the exact predicate entry points reused for the corridor
query, so the implementation cannot drift into a parallel geometry.

### 6.3 Bounded multi-step candidate family

Replace the six fixed targets with

```
for sense in {+1, −1}:                       # 2
    for k in 0 .. MAX_CLEARANCE_STEPS − 1:   # 5, step MAX_CLEARANCE_STEP = 3.0 wu
        aim = radius + EDGE_NODE_CLEARANCE + CLEARANCE_MARGIN + k * MAX_CLEARANCE_STEP
        for along in {0, −h, +h}:            # 3, h unchanged at 2r + MIN_GAP + 3
            target = centre + (sense * aim − projection) * normal + along * alongTangent
```

Thirty proposals per family, truncated to the first `MAX_C2_CANDIDATES_PER_FAMILY = 12` under the
existing total order after discarding ineligible ones, and still bounded per candidate by
`MAX_EDGE_NODE_EXCURSION = 64`. Rationale: when the nearest legal ring is blocked by a sibling hull the
next ring is frequently free, and today the search never looks there.

### 6.4 Unchanged verification and budgets

A1–A4, the counted index and verification scans, `greedyDisjoint` batching with singleton retries,
`MAX_RELOCATION_ROUNDS = 4`, `MAX_VERIFICATIONS_PER_FRAME = 64`,
`EDGE_CONFLICT_MAX_VERIFICATION_WORK_PER_FRAME`, `MAX_CANDIDATE_FAMILIES_PER_ROUND = 8` and
`MAX_CANDIDATES_PER_BATCH = 8` all stay exactly as they are. The pinned disc exclusion, the C1 crossing
family, and the terminal rebuild keep their current behaviour.

### 6.5 Legacy removal

Per the repository's legacy-removal policy the fixed six-target construction is deleted, not retained
behind a flag: one active path, no compatibility branch, no parallel old/new implementation. Fixtures
that pin exact C2 target coordinates, order, counts or verification-work totals are re-pinned
deliberately, and each re-pin is justified by the new family rather than by an unexplained number.

## 7. Recorded decision and mockup

The user chose option B from a four-way composite comparison. The mockup is committed as the design
record at `docs/superpowers/specs/assets/2026-10-26-edge-node-crossing-residual-options.png`
(sha256 `b3a00d4af68a4e55854b419fa386fc6fc60480eef47cee1d8798b31223d212b6`).

The four alternatives were:

- **A — keep as today**: repair nodes, publish the residual, paint the straight fallback. This is the
  measured baseline and the reported symptom.
- **B — relocate the obstructing node** (chosen): the disc leaves the chord, edges stay straight.
- **C — absolute bend budget for the path**: nodes unmoved, the router gains a world-unit allowance.
- **D — push discs off edges while settling**: a repulsion force keeps the settled layout clear.

## 8. Error handling

- Ineligible geometry (non-finite coordinates, zero-length chord, non-positive radius) emits no
  candidates and keeps the conflict as a residual, exactly as today. It is not budget exhaustion.
- A pinned disc emits no candidates and keeps the conflict as a residual (E9).
- An unreachable corridor in both senses falls through to the next ring; exhausting every ring with all
  candidates vetoed publishes the conflict as a residual with the existing flags, and never as an
  exception.
- Existing malformed-input exceptions in `EdgeConflictCorrection` are unchanged.
- The per-frame allowances bound the extra work; exhausting them sets the existing
  `verificationBudgetExhausted` flag and stops retrying, without changing the published residual set.

## 9. Test strategy

Red first, in this order:

1. **Reported-geometry reproduction.** A fixture with a 36.182 wu attachment chord, the obstructing disc
   at perpendicular offset `0`, two nested enclosures and sibling discs at `MIN_GAP` must currently
   publish a crossing residual, and must be resolved by the change with the final routed path clearing
   the disc by at least `radius + EDGE_NODE_CLEARANCE`.
2. **Corridor direction.** A fixture where one sense is blocked by a sibling hull and the opposite sense
   is free must choose the free sense in the first verification, with the blocked sense's target absent
   from the retained family.
3. **Margin.** A fixture whose disc ends within `CLEARANCE_MARGIN` of the boundary must be carried past
   it, and a fixture where the disc sits at exactly `radius + EDGE_NODE_CLEARANCE` must be reported as a
   conflict rather than as clear.
4. **Multi-ring necessity.** A fixture where every candidate in the first ring is vetoed and a candidate
   in a later ring is legal must resolve in one accepted round.
5. **Invariants preserved.** Every existing A2/A3/A4 rejection fixture, the pinned-disc E9 fixture, the
   budget and cap reconciliation fixtures, and the crossing-family fixtures stay green.
6. **No layout drift.** The full `freeplane_plugin_graph` suite passes, and the frozen
   `src/test/resources/performance/settled-reference.csv` (sha256
   `e9e988b7967ea6bb4f503dfb4c5af7d3e74a3328e28e0987e71ff0f0b8ac0578`) remains byte-identical.
7. **Verification budget evidence.** The measured graphs record `verifications`, `verificationWork` and
   `verificationBudgetExhausted` so the larger family is shown not to exhaust the frame allowance.

Characterisation, not a gate: sweep sibling distance and along-offset to record where the baseline gives
up and where the new family still succeeds, and record the numbers in the acceptance evidence.

## 10. Open question this design deliberately front-loads

The probe did not reproduce the user's exact arrangement, because settling is path-dependent. Which veto
fires in their instance is therefore not yet established. Test 1 in §9 is the answer: if the
reported-geometry fixture shows that relocation already succeeds and the crossing arrives by another
route — for example a routed path published for pre-repair positions — the specification must stop and
re-scope, and this design's implementation must not proceed.
