# Graph edge-node crossings - residual-fix technical specification

**Task:** `2026-10-26-edge-node-crossing-residual`  
**Status:** implementation-ready amendment, subject to the reviewer items in the final section  
**Baseline:** `bfaed5611d170b5b0b3dc93919685bd6c54035c5` (`plugin/graph-workspace`)  
**Runtime target:** Java 8  
**Authority:** `docs/superpowers/specs/2026-10-26-edge-node-crossing-residual-design.md` (approved by the user at the revision-3 pivot on 2026-10-26)

This document specifies the only implementation permitted for the residual edge-node crossing defect. It changes the internal relocation search in `EdgeConflictCorrection` and the internal routing ladder in `EdgeRouter`. It does not change the public API, layout-force policy, pins, enclosure membership, prominence, label placement, settlement policy, strict clearance predicate, or the existing counted-budget framework. [Design §§5, 6.1-6.3, 8]

All distances below are world units. All keys are the canonical strings produced by the existing `CanonicalKeys` helpers. Every fixture uses the existing production projection, geometry, positions, pin, enclosure, and edge-construction helpers; no parallel fixture format is permitted. [Design §9; 2026-10-16 spec §§2-3]

## 1. Normative authority and retained contracts

### 1.1 Scope and source of truth

The approved design is binding. A clause in this document is normative only when it is marked by a design citation or explicitly identified as retained text from one of the two amended specifications. The cited design section controls if this document and an earlier specification differ. [Design §§1, 11]

The implementation must remain Java 8 source compatible: no Java 9 APIs, private interface methods, local-variable type inference, records, lambdas where the surrounding source does not use them, or newer language constructs may be introduced. [Design §5; repository guidelines]

The implementation must not add a legality pre-screen, reserve candidate slots, change the strict clearance margin, change the force relaxation, generalize the midpoint bow implementation, add an asymmetric dogleg, or promise completeness. [Design §5]

### 1.2 Relocation preservation contract

The following is the normative Goal 1 contract, reproduced verbatim from the approved design:

> **Relocation preserves the legacy accepted sequence.** For an input whose legacy search finishes without ever reaching a *failed* round — that is, without a round in which no candidate is accepted — the new search produces the identical accepted repairs, the identical final positions, and the identical `rounds`, `verifications`, `verificationWork` and residual. For an input that does reach a failed round, the accepted prefix is identical and everything after it may differ; such an input is allowed to gain a repair, and its counters and final positions may then change. [Design §4, Goal 1]

The no-failed-round input class is asserted by `repairsNineIndependentObstructionsInBoundedBatches`, E1, E2, E3, the prominence fixture, and `acceptsTheC2TargetAtExactlyTheMaximumExcursion`. The failed-round class is asserted by `fullyBlockedFixture`, `duplicateChordFixture`, the A1 ring-expansion fixture, the A2/A3 ring-expansion fixture once the reviewer supplies its missing construction, and the stage-1 exhaustion witness. [Design §§4, 6.2, 9 tests 7-10; 2026-10-16 spec §§6.1-6.4, 7.1]

### 1.3 Routing preservation contract

The following is the normative Goal 2 contract, reproduced verbatim from the approved design:

> **Routing preserves every per-edge decision under identical inputs.** For a given edge, given the same obstacle grid published by earlier edges and the same remaining routing allowance, the new ladder selects the identical path, classification and sample list when the current ladder accepts. Named fixtures must additionally be preserved exactly, end to end, including every later edge. Frame-wide preservation is **not** claimed: extending any edge's rung set can change the obstacle grid seen by later edges, and review round 4 produced a concrete two-edge counterexample that does exactly that. [Design §4, Goal 2]

This is a per-edge contract, not a frame-wide equivalence claim. The asserting routing fixtures are the settled-reference workload, `laterChordBow`, `doglegAccepting`, `tightFan`, `reportedConvexQuad`, `cellCornerFan`, `closeParallelPaths`, the off-midpoint fixture, the midpoint ordering fixture, and the real-workspace replay. [Design §§4, 6.3, 9 tests 1-6 and 12; 2026-10-12 spec §§6.3, 6.10]

### 1.4 Publication and verification contracts retained from 2026-10-16

A rejected relocation attempt changes no position, geometry, conflict set, or repair record. An accepted attempt installs its verified positions, geometry, and complete detection together and records one `Repair` per candidate with the exact repair kind and moved-node map. All candidates in an accepted attempt share its zero-based `batchIndex`. C2 candidates use `RepairKind.TRANSLATION`. [2026-10-16 spec §2; Design §§6.2, 8]

The C2 family retains the 2026-10-16 basis and eligibility rules. For chord endpoints `a` and `b`, `d = b - a`, `L = hypot(d.x, d.y)`, `rawNormal = (-d.y / L, d.x / L)`, and `n` is the lexicographically smaller of `rawNormal` and `-rawNormal`, using `Double.compare` after signed-zero normalization. `u = (-n.y, n.x)`. A horizontal chord always has `n = (0,-1)` and `u = (1,0)`, independent of traversal direction. [2026-10-16 spec §3.1; Design §6.2]

For a disc centre `p`, radius `r`, signed projection `s = (p-a) dot n`, and ring radius `rho`, a target is:

```text
p + (sigma * rho - s) * n + q * u
```

where `sigma` is `+1` or `-1` and `q` is `0`, `-h`, or `+h`. Ring 0 uses `rho = r + EDGE_NODE_CLEARANCE`; `h = 2*r + MIN_GAP + EDGE_NODE_CLEARANCE`. No candidate is generated for a pinned disc, a non-finite input, a non-positive radius, a zero-length chord, a non-finite intermediate, zero movement, or movement greater than `MAX_EDGE_NODE_EXCURSION`. No value is clamped and no direction or radius is invented. [2026-10-16 spec §§3.1-3.2; Design §§3, 6.2, 8]

A candidate that would move an enclosure endpoint or swap nodes with different enclosure membership remains ineligible. Such a discarded candidate is not a verification. [Design §5.2; 2026-10-16 spec §3.2]

A1-A4 remain the complete verification contract. A1 requires the output edge-conflict key set to be a strict subset of the current set. A2 requires `gapPairs_out` to be a subset of `gapPairs_in`, using counted checks for both current and candidate positions. A3 requires `hullPairs_out` to be a subset of `hullPairs_in`. A4 retains the existing enclosure and membership invariants. [Design §§3, 6.2, 11; 2026-10-16 §§3.2, 5.1]

## 2. Routing ladder extension

### 2.1 Existing router contract

Edges are processed in ascending `CanonicalKeys.edge` order. The straight attachment chord is accepted when it clears the existing predicate. If it does not, the existing bow and same-side dogleg rungs are tried in their pinned order. If every permitted rung fails, the straight chord is published as a fallback and `edgesFallback` is incremented. Obstacles are every other visible edge, using its published path when already processed and its straight chord otherwise, plus every projected node disc to which the edge is not attached. Hulls are never routing obstacles. [Design §§4, 6.1, 6.3; 2026-10-12 spec §§3.1, 6.1-6.3; `EdgeRouter` class contract]

The retained constants are `MAX_BEND = 96.0`, `ROUTE_LENGTH_CAP = 1.25`, `EDGE_NODE_CLEARANCE = 3.0`, `ROUTE_EPSILON = 1e-9`, and `EdgePath.BOW_SAMPLE_TOLERANCE = 0.75`. A bow has three defining points and a dogleg has four. Samples remain immutable and contain at least two points. [Design §§2, 6.1, 8; 2026-10-12 spec §§1.4-1.5; `EdgePath` contract]

The retained curved ladder is exactly:

| Group | Fraction order | Analytic ratio `R(0.5,f)` | Status |
|---|---|---:|---|
| bow | `+0.15`, `-0.15` | `1.014804` | live |
| bow | `+0.30`, `-0.30` | `1.057116` | live |
| bow | `+0.50`, `-0.50` | `1.147794` | live |
| same-side dogleg | `+0.15`, `-0.15` | `1.064390` | live |
| same-side dogleg | `+0.30`, `-0.30` | `1.230242` | live |
| same-side dogleg | `+0.50`, `-0.50` | `1.535184` | excluded by `ROUTE_LENGTH_CAP` |

The live order is `straight`, bow `+0.15`, `-0.15`, `+0.30`, `-0.30`, `+0.50`, `-0.50`, dogleg `+0.15`, `-0.15`, `+0.30`, `-0.30`. A rung is skipped when `abs(f) * L > MAX_BEND`. Acceptance uses the sampled length `<= ROUTE_LENGTH_CAP * chordLength`, not the analytic ratio alone. [Design §§6.1, 11; 2026-10-12 spec §§1.4, 6.3]

### 2.2 New control-position group

For anchor points `p0`, `p1`, anchor chord length `L`, and canonical unit normal `n`, define exactly:

```text
control(A, f) = p0 + A * (p1 - p0) + f * L * n
```

The new constants and their order are exactly:

```text
CONTROL_FRACTIONS = { 0.25, 0.75, 1.0/3.0, 2.0/3.0 }
OFFSET_FRACTIONS  = { +0.50, -0.50, +0.30, -0.30, +0.15, -0.15 }
```

`A = 0.5` is the existing bow ladder. The published group order is exactly `straight -> bow ladder (A = 0.5) -> dogleg ladder -> control-position group`. The new group is appended after the dogleg ladder. It must not be inserted before the midpoint bow group, between the bow and dogleg groups, or inside either existing group. [Design §6.1]

There is no separate side loop. The sign of `f` selects the side. Within the new group, iterate `A` in `CONTROL_FRACTIONS` order and then `f` in `OFFSET_FRACTIONS` order. [Design §6.1]

The `A = 0.5` ladder and its existing `analyticRatio` implementation remain untouched. Existing midpoint bow defining points, samples, lengths, order, and classifications must remain bit-for-bit unchanged whenever Goal 2 applies. The control-position group has its own ratio gate and must not call the midpoint-only ratio as a substitute. [Design §§4 Goal 2, 6.1]

For each control-position candidate, apply the existing gates in this exact sequence, which is the sequence `EdgeRouter.bestRung` already applies to every rung:

1. Reject when `abs(f) * L > MAX_BEND`.
2. Reject when the control-position analytic ratio exceeds `ROUTE_LENGTH_CAP`.
3. Reject when the sampled `candidate.length() > ROUTE_LENGTH_CAP * chordLength`.
4. Apply the full `clears(candidate, edge, grid, geometry, budget)` predicate.

No additional gate is introduced. Unavailable trimmed attachments are not a separate rejection stage: they are the existing `null` return of `bowPath` / `attachment`, which skips the rung exactly as it does today.

The final gate must be `clears(...)`, not `clearsDiscs(...)`: it must check both other node discs and other paths. A route budget stop returns no candidate and is reconciled through the existing stop flags. [Design §6.1; `EdgeRouter.bestRung` contract]

### 2.3 Control-position ratio table

The normative ratio is:

```text
R(A, f) = integral_0^1 2 * sqrt((A + (1 - 2*A) * t)^2
                                 + f^2 * (1 - 2*t)^2) dt
```

The following table is the computed value for every listed pair. The sign columns are included separately even though the value is even in `f`.

| `A` | `f = +0.50` | `f = -0.50` | `f = +0.30` | `f = -0.30` | `f = +0.15` | `f = -0.15` |
|---:|---:|---:|---:|---:|---:|---:|
| `0.25` | `1.159854311690731` | `1.159854311690731` | `1.064241004799091` | `1.064241004799091` | `1.017221360974041` | `1.017221360974041` |
| `0.75` | `1.159854311690841` | `1.159854311690841` | `1.064241004799023` | `1.064241004799023` | `1.017221360974069` | `1.017221360974069` |
| `1/3` | `1.153020461743610` | `1.153020461743610` | `1.060106539650193` | `1.060106539650193` | `1.015781166936747` | `1.015781166936747` |
| `2/3` | `1.153020461743631` | `1.153020461743631` | `1.060106539650204` | `1.060106539650204` | `1.015781166936771` | `1.015781166936771` |

Derivation: substitute the four `A` values and six signed `f` values into the displayed integral; adaptive Simpson evaluation of the integrand produced the table, and the squared `f` term proves the positive/negative pairs are equal. The trailing-digit differences between the `A` and `1 - A` rows are quadrature noise, not a real asymmetry: any implementation must compare these values with a tolerance of `1e-9` rather than for exact equality. Every table value is below `1.25`; therefore no listed control-position pair is rejected by the analytic ratio in the pinned table. [Design §6.1]

The sampled bow count remains the existing sagitta rule `n = max(1, ceil(sqrt(d / (2 * BOW_SAMPLE_TOLERANCE))))`, publishing `n + 1` points. The retained derived values are largest drawn radius `14.0`, largest inflated-disc diameter `34.0`, maximum sagitta `1.5`, and sampling tolerance `0.75`. [Design §§6.1, 11; 2026-10-12 spec §1.5]

### 2.4 Routing fixtures

Every routing fixture below has generation `1L`, no enclosures, no pins unless stated, and radius `8.0` for every named node unless a different radius is stated. The expected route diagnostics are the diagnostics of the existing `EdgeRouteDiagnostics` record; no relocation repair is part of a routing-only fixture. [Design §9]

| Test and asserting fixture | Exact input and initial result | Required result |
|---|---|---|
| `routesTheMeasuredOffMidpointObstacleWithAControlPositionBow` | Nodes `A=(-18.091,0)`, `B=(18.091,0)`, `N=(-5.0455,0)`; edge `A -> B`; `L=36.182`; all radii `8`; no pins/enclosures. Initial straight centre distance to `N` is `0`, so surface clearance is `-8.0`; current ladder publishes `STRAIGHT` fallback (`edgesFallback=1`, other shape counters `0`). | The first accepted new group member is `A=0.25`, `f=+0.50`, shape `BOW`, control `(-9.0455,18.091)`. Every published sample segment must satisfy `centreDistance - 8 >= 3 - ROUTE_EPSILON`; the measured minimum centre distance is `11.374205532928446`, so the measured surface clearance is `3.374205532928446`. Record the selected `A`, `f`, shape, defining points, and samples. [Design §9 test 1] |
| `rejectsMovingControlPositionGroupAheadOfMidpointBows` | Nodes `A=(-50,0)`, `B=(50,0)`, `N=(0,0)`, all radius `8`; edge `A -> B`; no pins/enclosures. The straight chord intersects `N`. | The existing midpoint group must win with `BOW`, `A=0.5`, `f=+0.30`, control `(0,30)`, before the control-position group. The selected path and its samples must differ from the legal control candidate `A=0.25`, `f=+0.50`, control `(-25,50)`. A mutation that puts the new group before the midpoint group fails. [Design §9 test 2] |
| `keepsTheDoglegLadderAheadOfTheControlPositionGroup` | Existing `EdgeRouterFixture.doglegAccepting()`: `A=(0,0)`, `B=(100,0)`, upper and lower obstacle discs at `(60.44,13.77)` and `(60.44,-13.77)`, obstacle radii `12.0`, endpoint radii `14.0`, one edge `A -> B`, no pins/enclosures. Straight and all live bows are blocked; the `+0.30` same-side dogleg clears at `16.229` against the required `15.0`. | The selected shape is `DOGLEG`, `edgesDogleg=1`, `edgesStraight=0`, `edgesBow=0`, `edgesFallback=0`. Its bends are the same-side `+0.30` bends at one-third and two-thirds of the chord; the legal control alternative `A=0.75`, `f=+0.50`, control `(75,50)` must not be selected. A mutation that inserts the control group between bow and dogleg fails. [Design §9 test 3; `EdgeRouterFixture.doglegAccepting`]
| `preservesTheSettledReferenceAndNamedLadderFixtures` | `settled-reference.csv` is compiled by `SettledReferenceFixtureCompiler`: generation `1L`; nodes `K01..K25`, all radius `8.0`; no pins/enclosures; edges `K01-K02`, `K03-K04`, and `K05-K06-K07-...-K25` as listed in §5.1; the first four coordinates are `(-90,0)`, `(90,0)`, `(0,-25)`, `(0,25)`, and the remaining coordinates are the five-column serpentine lattice below. | The workload remains exactly `21 straight / 1 bow / 0 dogleg / 0 fallback`; every existing defining-point list and sample list is identical. `laterChordBow`, `doglegAccepting`, `tightFan`, `reportedConvexQuad`, `cellCornerFan`, and `closeParallelPaths` retain their exact classifications and named assertions. [Design §9 test 4; 2026-10-12 spec §§6.10, 14.1]
| `skipsControlPositionCandidatesThatExceedMaxBend` | Nodes `A=(-100,0)`, `B=(100,0)`, `N=(-84.5,0)`, radius `8`, one edge `A -> B`, no pins/enclosures. This is the measured 200-unit off-midpoint witness: all current midpoint/dogleg candidates fail the node clearance, while the control candidate `A=0.25`, `f=+0.30` clears. | `A=0.25`, `f=+0.50` is considered before `f=+0.30` and is skipped by `abs(f)*L = 100 > 96`; `f=-0.50` is skipped for the same reason. The selected path is the legal control-position `BOW` with `A=0.25`, `f=+0.30`, control `(-50,60)`, and measured centre clearance `11.163` or greater. The initial route is a straight fallback; the final route is non-fallback and has no residual. The test must prove that the max-bend gate, not the analytic ratio, skipped the `.50` pair. [Design §9 test 5]
| `preservesSettledAndRawPerformanceModesSeparately` | Settled A7a uses the committed `settled-reference.csv` and the production correction/router pipeline. Raw A7b uses `GeneratedWorkspace` scenario `reference-2000-5000`: 20 maps, 100 nodes/map, 60 enclosures/map, 175 native connectors/map, 400 cross-map contributors, 300 relationships, generation `1L`. | Settled samples retain zero exhaustion and the existing performance gates. Raw frames retain bounded counted work, valid flags, complete publication, and `GraphWorkspacePerformanceDiagnostic.RAW_REFERENCE_MAX_FRAME_NANOS = 5_000_000_000L`. Raw exhausted frames never substitute for settled samples. [Design §9 test 6; 2026-10-16 spec §1; repository performance contract] |

For all routing candidates, the clearance assertion is surface clearance `centreDistance - radius >= EDGE_NODE_CLEARANCE - ROUTE_EPSILON`. The expression `radius + clearance` is not a surface-clearance assertion and must not be used. [Design §9 test 1; Design §2 Fact 2]

## 3. Relocation ring expansion

### 3.1 Stages and constants

Stage 0 is today's six-candidate ring-0 search. It retains today's candidate generation, conflict-key selection, order `(stage, movement, sideRank, alongRank, CandidateIdentity)`, greedy disjoint batching, singleton retry schedule, and limits. Stage 0 must consume exactly the verification count and reported work it consumes today when it accepts or when it reaches a failed round. [Design §6.2; 2026-10-16 spec §§3.2-4.2]

Stage 1 is entered only after stage 0 completes a round with no accepted candidate and no stop flag. It expands only the candidate families selected by stage 0, in the same canonical conflict-key order. It does not reselect families per ring. It does not regenerate C1 crossing alternatives. C1 alternatives are generated once in stage 0 and retain their stage-0 places in the candidate pool. [Design §6.2]

Use exactly:

```text
MAX_CLEARANCE_STEPS = 5
MAX_CLEARANCE_STEP  = 3.0
rho_k = radius + EDGE_NODE_CLEARANCE + k * MAX_CLEARANCE_STEP
k = 1, 2, 3, 4
```

For radius `8.0`, the ring radii are `rho_0=11`, `rho_1=14`, `rho_2=17`, `rho_3=20`, and `rho_4=23`. Each expanded ring regenerates the same direct and diagonal six slots with the same `h`; only `rho` changes. [Design §6.2]

Within each ring, deduplicate candidates by the existing `CandidateIdentity` only. Do not deduplicate candidates across different rings. A ring rejected by `verify` does not prevent later rings from being tried. [Design §6.2; 2026-10-16 spec §3.3]

Discard non-finite, zero-movement, and over-excursion proposals before verification. `MAX_EDGE_NODE_EXCURSION = 64.0` is inclusive: movement exactly `64.0` is eligible; movement greater than `64.0` is rejected without clamping. [Design §§6.2, 8; 2026-10-16 spec §3.2]

The stage-1 proposal ceiling is `4 rings * 6 slots * selected families`. With at most eight selected families this is `4 * 6 * 8 = 192` proposals, not `24`; `24` is the per-family bound. The unchanged `MAX_VERIFICATIONS_PER_FRAME = 64`, `EDGE_CONFLICT_MAX_VERIFICATION_WORK_PER_FRAME = 65_536`, `MAX_RELOCATION_ROUNDS = 4`, and `MAX_CANDIDATES_PER_BATCH = 8` still bound actual attempts. [Design §6.2]

After any acceptance, return to stage 0 for the next round. Ring expansion must never modify the accepted prefix that a ring-0 repair would have produced. [Design §6.2]

### 3.2 Exact round-loop modification

The existing round loop in `EdgeConflictCorrection.relocate` is retained verbatim. Its flag handling, its
`DETECTION_STOP` / `verificationWorkAtCap()` / attempt-cap branches, its `reconcileStopFlags()` calls, its
post-acceptance complete rescan and its terminal rebuild are **not** re-specified here and must not be
rewritten: the specification deliberately defines this change as a minimal diff, because the earlier draft
reproduced the loop and omitted two of those flag branches.

The implementation is exactly these **four** edits, and no other change to `relocate`:

**(E1) Candidate generation returns the selected families with their provenance.** Change
`generateCandidates` to return both the pool and the ordered families it selected. A family is not a bare
`List<Candidate>`: it carries the `Conflict` that generated it, because that is the only object that knows the
family's chord, its disc, and whether it is a crossing. Add exactly one nested type, and change nothing about
`Candidate` or `CandidateIdentity`:

```text
static final class SelectedFamily {
    final Conflict conflict;            // the stage-0 Conflict that generated this family
    final List<Candidate> candidates;   // its stage-0 candidates, in generated order
}
```

`generateCandidates` returns `PoolGeneration { List<Candidate> pool; List<SelectedFamily> selectedFamilies; }`,
retaining the existing `MAX_CANDIDATE_FAMILIES_PER_ROUND` bound, conflict-key ordering, `CandidateIdentity`
deduplication and the "incomplete initial detection never enters selection" rule. `selectedFamilies` is ordered
by the same conflict key as the flattening order. C1 crossing alternatives are generated here and only here.

**(E2) Stage 1 is attempted only after a failed round.** Insert the stage-1 block immediately after the
existing inner pool loop ends, guarded by the same condition the existing code already uses to leave the
round:

```text
// after the existing inner "while (!pool.isEmpty() ...)" loop
if (!acceptedThisRound && !verificationStopped
        && rounds < MAX_RELOCATION_ROUNDS
        && verifications < MAX_VERIFICATIONS_PER_FRAME) {
    final List<Candidate> stage1 =
        expandSelectedFamiliesByRing(selectedFamilies, conflicts, currentPositions, pinned,
            enclosurePaths);
    // Body is character-for-character the existing inner pool loop, with "pool" replaced by
    // "stage1" and with the same DETECTION_STOP / verificationWorkAtCap() / attempt-cap
    // branches, the same reconcileStopFlags() calls and the same verifications++ accounting.
    runExistingPoolLoop(stage1);
}
```

`runExistingPoolLoop` denotes the unchanged inner loop body; the implementer must not write a second,
simplified copy of it. Because the guard contains `!acceptedThisRound`, stage 1 is unreachable on any round
in which stage 0 accepted, which is what preserves the legacy accepted sequence.

**(E3) Stage 1 expands only the stage-0-selected families, and never regenerates C1.**

```text
List<Candidate> expandSelectedFamiliesByRing(List<SelectedFamily> selectedFamilies,
        LayoutPositions positions, Set<ProjectedNodeKey> pinned)
```

The contract:

- Only C2 families take part. A family whose `conflict.isCrossing()` is true is skipped, so C1 alternatives are
  generated once, in stage 0, and never appear at `k >= 1`. This is an executable filter on the `SelectedFamily`
  record, not a comment and not an inference from `Candidate` contents.
- A C2 family is expanded from `family.conflict.firstChord()` and `family.conflict.disc()` - the very objects
  that generated it - using the existing eligibility and finite checks. No new family is introduced, and no
  conflict is re-derived by matching coordinates.
- **Why those recorded objects are still current.** Stage 1 runs only when stage 0 accepted nothing in that
  round, and nothing else mutates `currentPositions` or `currentGeometry` within a round. The stage-0 detection
  and its `Conflict` objects therefore describe exactly the geometry in force when stage 1 runs, so the
  expansion must use them directly. The implementation must not recompute the chord or disc from positions, and
  must not let `selectedFamilies` outlive its round: because E4 returns to stage 0, every round builds a fresh
  one.
- For `k = 1 .. MAX_CLEARANCE_STEPS - 1` in ascending order, and within a ring by the existing total order
  `(stage, movement, sideRank, alongRank, CandidateIdentity)`, emit the six slot targets with
  `rho_k = radius + EDGE_NODE_CLEARANCE + k * MAX_CLEARANCE_STEP`, discarding non-finite, zero-movement and
  over-`MAX_EDGE_NODE_EXCURSION` proposals.
- Deduplicate with `CandidateIdentity` **within each ring only**; a ring may repeat an identity an earlier ring
  emitted, because the target coordinates differ.
- Pinned discs emit nothing, exactly as in stage 0.

**(E4) Return to stage 0 after every acceptance.** No extra code is required: the existing round loop
re-detects and re-generates at the top of the next iteration, so the next round always begins at stage 0.
The specification states this as a requirement and the test in §4 must fail if a stage-1 selection or
`selectedFamilies` value leaks into the following round.

Everything else — `MAX_VERIFICATIONS_PER_FRAME`, `EDGE_CONFLICT_MAX_VERIFICATION_WORK_PER_FRAME`,
`MAX_RELOCATION_ROUNDS`, `MAX_CANDIDATES_PER_BATCH`, the per-attempt `verifications++`, the batch-then-singleton
schedule, the publication and terminal-rebuild rules — is unchanged. [Design §§6.2, 6.3, 8]

### 3.3 Counted work and stop boundaries

A verification is counted only after a candidate or candidate batch survives generation and is actually submitted to `verify`. Candidate generation, deduplication, pinned-node filtering, zero-movement filtering, non-finite filtering, and max-excursion filtering do not increment `verifications`. [Design §6.2; 2026-10-16 spec §§3.2, 4.1]

A rejected stage-0 candidate does not permit stage 1 to reselect a different family. A stage-1 acceptance increments `rounds` once, records the accepted repairs with that round's batch indexes, and returns to a fresh stage-0 generation after the complete post-acceptance detection. [Design §6.2]

If any allowance is exhausted, use the existing `verificationBudgetExhausted` contract and publish the complete stage-0 residual. Do not throw. Do not retry a singleton after a work-stop rejected its batch. [Design §8; 2026-10-16 spec §5]

## 4. Relocation fixtures and exact expected outcomes

### 4.1 Design test 7: A1-rejected ring 0

Test name: `expandsTheA1RejectedFamilyToTheFourthRing`.

| Item | Required value |
|---|---|
| Generation | `1L` |
| Nodes | `A=(-100,0)`, `B=(100,0)`, `N=(0,0)`, `U=(-140,11)`, `V=(140,11)`, `D=(-140,-11)`, `E=(140,-11)` |
| Radii | `8.0` for all seven nodes |
| Enclosures | none |
| Edges | `A -> B`, `U -> V`, `D -> E` |
| Pins | active pins on `A`, `B`, `U`, `V`, `D`, and `E`; `N` is not pinned |
| Initial node-gap/hull state | no node-gap or hull residuals |
| Initial edge conflict | exactly `edgeNodePair(A-B,N)`; `crossingsDetected=0`, `edgeOverNodeDetected=1` |
| Ring 0 | all six candidates at `rho=11` are rejected by A1 because they introduce an upper or lower edge conflict |
| Rings 1-3 | all six candidates at `rho=14`, `17`, and `20` are rejected by A1 for the same upper/lower-edge identity |
| Ring 4 first candidate | canonical horizontal normal is `(0,-1)`; the first equal-movement direct target is `N=(0,-23)`; `rho=23`; it clears all three chords |
| First acceptance | verification `25` (`24` rejected ring-0-through-ring-3 proposals plus the first ring-4 attempt) |
| Repair | one `TRANSLATION`, moved node `{N}`, from `(0,0)` to `(0,-23)`, `batchIndex=0` |
| Final geometry/path | no C2 residual, no node-gap or hull residual, central path is `STRAIGHT` with samples `(-92,0)` and `(92,0)`, length `184.0` |
| Flags | `detectionTruncated=false`, `verificationBudgetExhausted=false`, `indexBudgetExhausted=false` |

The test must record the initial conflict key, all six ring-0 A1 rejection identities, the first accepted ring and verification number, the exact repair coordinates, and the final complete conflict scan. [Design §9 test 7]

### 4.2 Design test 8: A2/A3 ring expansion

Test name: `expandsTheA2RejectedFamilyWithoutReselectingIt`.

The approved design does not assert that a pure-A2/A3 ring-0 veto fixture exists, because with radius-8 discs,
`MIN_GAP = 6` and a 3 wu ring step a blocker within `8 + 8 + 6 = 22` of a ring-`k` target is within 25 of the
same slot's ring-`k + 1` target. The specification resolves this with a **construct-or-declare** rule that is
executable either way:

1. **Search for the fixture.** A test-only search enumerates the pinned family `b = (0, y_b)` for
   `y_b` in `22.0 .. 51.0` step `0.5`, plus `h2 = (0, y_b + 6)` to keep `b` itself legal, with
   `A = (-100, 0)`, `B = (100, 0)`, radius `8.0` for every disc, no pins and no enclosures, one edge `A -> B`,
   generation `1L`. For each candidate it records, for each of the six ring-0 targets and each of the six
   targets in rings 1–4, which of A1/A2/A3 rejects it.
2. **If at least one `y_b` exists whose six ring-0 targets are all rejected by A2 or A3 and whose ring-`k`
   target for some `k >= 1` is accepted by full verification**, that `y_b` is pinned in this section with the
   exact rejection table, the accepted ring, the expected `verifications`, `verificationWork` and the accepted
   coordinates, and the test asserts all of them.
3. **If no such `y_b` exists**, the test instead asserts that fact explicitly — that every `y_b` in the family
   admits acceptance at ring 0 — and records the A2/A3 ring-0-only veto as a **search limit, not a defect**.
   Test 7 (A1) then carries Goal 4 on its own, and the acceptance evidence must say so in those words.

Rule 3 is not a fallback that hides a gap: it is the same standard this design holds everywhere else — do not
claim a geometry that has not been exhibited. The specification must not ship a test that silently passes when
no fixture was found.

The two existing source-backed veto witnesses stay mandatory regression inputs, and they are **not** substituted
for the staged fixture:

- `vetoesTheUnsafeDirectTargetThatAddsANodeGapAndAcceptsTheOtherSide`: `A=(-100,0)`, `B=(100,0)`, `n=(0,5)`,
  `b=(0,30)`, one edge `A -> B`, radius `8.0`, no pins or enclosures, generation `1L`. Initial C2 key
  `edgeNodePair(A-B,n)`. Target `(0,11)` adds `nodePair(n,b)` because the distance to `b` becomes `19`, so A2
  rejects it; target `(0,-11)` is accepted. Expected `rounds=1`, `verifications=2`, accepted `n=(0,-11)`,
  empty residual, `verificationWork=30`. With the staged search this fixture still accepts at ring 0, so its
  numbers are unchanged and it is a Goal-1 assertion.
- `vetoesTheUnsafeSiblingTargetWithA3Subset`: nodes `a=(0,0)`, `c=(-53,0)`, `p=(5,100)`, `q=(5,-100)`,
  radius `8.0`, sibling enclosures `A` and `C` under root `R`, one edge `p -> q`, generation `1L`. The nearest
  direct target `(-6,0)` introduces `hullPair(HA,HC)`; the opposite direct target `(16,0)` is safe. The full
  hull recomputation must equal the incremental one. This fixture also accepts at ring 0 and keeps its numbers.

### 4.3 Design test 9: Goal-1 preservation fixtures

Test names are the existing names unless explicitly stated. All expected coordinates below are exact.

#### E1: nine independent obstructions

Fixture factory: `nineObstructionsFixture()`.

- Generation `1L`; no enclosures; no pins; 27 radius-8 nodes and 9 edges.
- For `i=0..8`, nodes are `A_i=(-100,150*i)`, `B_i=(100,150*i)`, `N_i=(0,150*i+5)`, and edge `A_i -> B_i`.
- Initial conflict keys in canonical order are exactly `edgeNodePair(A_i-B_i,N_i)` for `i=0..8`; `conflictsDetected=9`, `crossingsDetected=0`, `edgeOverNodeDetected=9`; node gaps and hull pairs are empty.
- Accepted repairs are `N_i: (0,150*i+5) -> (0,150*i+11)` for all nine `i`; each is `TRANSLATION`; repairs `0..7` have `batchIndex=0`, repair `8` has `batchIndex=1`.
- Expected diagnostics: `rounds=2`, `verifications=2`, `verificationWork=1_755`, `repairsApplied=9`, `edgeOverNodeResolved=9`, empty residual, all budget flags false.
- Each routed edge is `STRAIGHT` with samples `(-92,150*i)` and `(92,150*i)`, length `184.0`; route counters are `9/0/0/0` for straight/bow/dogleg/fallback.

This is asserted by `repairsNineIndependentObstructionsInBoundedBatches`. [Design §§4 Goal 1, 6.2, 9 test 9; 2026-10-16 spec §6.1]

#### E2: opposite-side A2 repair

Fixture factory: `oppositeSideFixture()`.

- Generation `1L`; nodes `A=(-100,0)`, `B=(100,0)`, `N=(0,5)`, `O=(0,30)`, all radius `8.0`; no enclosures or pins; edge `A -> B`.
- Initial C2 conflict is exactly `edgeNodePair(A-B,N)`; initial node gaps and hull pairs are empty.
- Target `(0,11)` is only `19` from `O` and fails A2 because the required node-centre separation is `22`; target `(0,-11)` is `41` from `O` and is accepted.
- Expected repair: `N: (0,5) -> (0,-11)`, one `TRANSLATION`, `batchIndex=0`.
- Expected diagnostics: `rounds=1`, `verifications=2`, `verificationWork=30 = 5 * C(4,2)`, one resolved C2, empty residual, zero crossing counts, all budget flags false.
- Expected path: `STRAIGHT`, samples `(-92,0)` and `(92,0)`, length `184.0`; no bow, dogleg, or fallback.

This is asserted by `repairsOnTheOppositeSideWhenTheNearestSideBreaksTheGap`. [Design §§4 Goal 1, 6.2, 9 test 9; 2026-10-16 spec §6.3]

#### E3: blocked column and dormant pin

Fixture factory: `blockedColumnFixture()`.

- Generation `1L`; nodes `A=(-100,0)`, `B=(100,0)`, `N=(0,0)`, `O1=(0,-22)`, `O2=(0,-44)`, `O3=(0,-66)`, `O4=(0,22)`, `O5=(0,44)`, `O6=(0,66)`; all radius `8.0`; no enclosures; edge `A -> B`; no active pins for the installing case.
- Initial conflict is exactly `edgeNodePair(A-B,N)`; column gaps are exactly `22`, so the initial node-gap set is empty.
- Direct targets `(0,11)` and `(0,-11)` fail A2 against a column neighbour. The first feasible candidate is diagonal `(-25,-11)`, movement `sqrt(746)=27.313000567...`, below the inclusive `64.0` excursion.
- Expected repair: `N: (0,0) -> (-25,-11)`, one `TRANSLATION`, `batchIndex=0`.
- Expected diagnostics: `rounds=1`, `verifications=3`, `verificationWork=252 = 7 * C(9,2)`, one detected and resolved C2, empty residual, zero crossing counts, all budget flags false.
- Expected path: `STRAIGHT`, samples `(-92,0)` and `(92,0)`, length `184.0`; the complete final-path oracle must pass for the published geometry.
- With `pinnedAtZero(N)`, the same fixture must perform no verification: `rounds=0`, `verifications=0`, no repairs, `edgeOverNodeDetected=1`, `edgeOverNodeResolved=0`, exact residual `edgeNodePair(A-B,N)`, unchanged positions and anchors, all budget flags false.
- With a dormant pin record at `(0,0)`, the record remains present but excludes no candidate; the installing result remains exactly `N=(-25,-11)`, `rounds=1`, `verifications=3`, `verificationWork=252`, empty residual.

These are asserted by `repairsTheBlockedColumnWithTheFirstFeasibleDiagonal`, `keepsThePinnedColumnObstructionAsAnExactResidual`, and `repairsTheColumnWithADormantPin`. [Design §§4 Goal 1, 6.2, 8, 9 test 9; 2026-10-16 spec §§3.2, 6.4, 8.4]

#### Prominence fixture

Factory: `prominenceFixture()`.

- Generation `1L`; no enclosures or pins; nodes `A=(-100,0)`, `B=(100,0)`, `N=(0,5)`, `T1=(-100,100)`, `T2=(100,100)`.
- Edges are undirected `A -> B`, plus arrow-at-target connectors `N -> T1` and `N -> T2`; `N` has two visible outgoing targets, scale `1.2`, and radius `9.6`; all other radii are `8.0`.
- Initial conflict is exactly `edgeNodePair(A-B,N)`; initial node-gap and hull residuals are empty.
- Expected repair is `N: (0,5) -> (0,12.6)`, because `rho=9.6+3.0=12.6`; `rounds=1`, `verifications=1`, one `TRANSLATION`, `batchIndex=0`, empty residual, `edgeOverNodeDetected=1`, `edgeOverNodeResolved=1`, no exhaustion/truncation.
- Expected paths are three `STRAIGHT` paths, no bow/dogleg/fallback; the central path samples `(-92,0)` and `(92,0)`, length `184.0`, and `toChord-radius=3.0` within `1e-9`.

This is asserted by `clearsTheActualProminentNodeDisc`. [Design §§4 Goal 1, 6.2, 9 test 9; 2026-10-16 spec §6.1]

#### Round, excursion, and acceptance-prefix witnesses

- `stopsAtFourAcceptedRoundsWithoutClaimingExhaustion` uses `crowdedRowsFixture(33)`: generation `1L`, no enclosures/pins, rows `A_i=(-100,150*i)`, `B_i=(100,150*i)`, `N_i=(0,150*i+5)`, radius `8.0`, one edge per row. Exactly 33 initial C2 keys. Eight families are accepted per round for four rounds; `rounds=4`, `verifications=4`, `repairs=32`, no exhaustion/truncation, residual exactly `edgeNodePair(A_9-B_9,N_9)`, and every repaired node is `(0,150*i+11)` except `N_9=(0,1355)`. The last complete residual is published and the terminal rebuild runs. [Design §§4 Goal 1, 9 test 9]
- `acceptsTheC2TargetAtExactlyTheMaximumExcursion` uses `excursionBoundaryFixture`: `A=(-100,0)`, `B=(100,0)`, `N=(0,1)`, one edge, no enclosures/pins, endpoint radii `8.0`, supplied `N` radius `62.0`. The same-side direct target moves exactly `64.0` to `N=(0,65)` and is accepted; `rounds=1`, `verifications=1`, one `TRANSLATION`, `from=(0,1)`, `to=(0,65)`, empty residual, central straight samples `(-92,0)`, `(92,0)`, length `184.0`, `toChord=65`, `toChord-radius=3.0`. `MAX_EDGE_NODE_EXCURSION` must remain inclusive. [Design §§6.2, 9 test 9; 2026-10-16 spec §§3.2, 6.1]
- `rejectsEveryC2TargetBeyondTheMaximumExcursionWithoutClamping` uses `overExcursionFixture` with the same endpoints and edge but `N=(0,0)` and supplied radius `62.0`. Both direct targets move `65.0`; diagonal movement is `hypot(65,133)=148.03...`; every candidate is rejected without clamping. Expected `rounds=0`, `verifications=0`, no repair, exact residual, unchanged `N=(0,0)`, and all budget flags false. [Design §§6.2, 8; 2026-10-16 spec §3.2]
- `rejectsTheZeroMovementTargetButAcceptsTheOppositeSide` uses source positions `A=(-100,0)`, `B=(100,0)`, `N=(0,-11)`, supplied boundary geometry centre `N=(0,0)`, radius `8.0`, one edge, no enclosures/pins. The target on the current side is zero movement and is discarded without verification; the opposite direct target is accepted. Expected `rounds=1`, `verifications=1`, repair `(0,-11)->(0,11)`, empty residual. [Design §§6.2, 8]

### 4.4 Controls whose counter values are intentionally re-pinned

#### No-candidate control

Factory: `fullyBlockedFixture()`.

- Generation `1L`; no enclosures or pins; all radius `8.0`.
- Nodes in exact order: `A=(-100,0)`, `B=(100,0)`, `N=(0,0)`, `U=(0,22)`, `D=(0,-22)`, `UL=(-25,25)`, `UR=(25,25)`, `DL=(-25,-25)`, `DR=(25,-25)`.
- Edge list: only `A -> B`.
- Initial conflict: exactly `edgeNodePair(A-B,N)`; node-gap and hull residuals empty; `crossingsDetected=0`, `edgeOverNodeDetected=1`.
- The six ring-0 alternatives all add a node gap: direct targets are `11` from a blocker and diagonal targets are `14` from a blocker. Stage 0 rejects all six; stages 1-4 reject all remaining six-slot families.
- Required post-change diagnostics: `verifications=30`, `verificationWork=2_160`, `rounds=0`, no repairs, `edgeOverNodeResolved=0`, residual exactly the initial one-key list, all budget flags false, unchanged positions and anchors.
- The path remains present. With the unchanged geometry, the route is `DOGLEG` (`edgesDogleg=1`, other shape counters `0`), no route budget stop, no path-crossing residual, and measured minimum same-geometry clearance `23.590001215504564`.

The old expectation `6 / 432` is superseded by `30 / 2_160`. The increase is the measured cost of expanded search, not an improvement. [Design §§6.2, 9, 11; 2026-10-16 spec §7.1]

#### Duplicate-alternative control

Factory: `duplicateChordFixture()`.

- Generation `1L`; no enclosures; all radius `8.0`.
- Nodes in exact order: `A=(-120,0)`, `B=(120,0)`, `C=(-80,0)`, `E=(80,0)`, `N=(0,0)`, `U=(0,22)`, `D=(0,-22)`, `UL=(-25,25)`, `UR=(25,25)`, `DL=(-25,-25)`, `DR=(25,-25)`.
- Edges: `A -> B` and `C -> E`.
- Active pins: `A`, `B`, `C`, and `E`; `N` and all blockers are unpinned.
- Initial conflict order is exactly `edgeNodePair(A-B,C)`, `edgeNodePair(A-B,E)`, `edgeNodePair(A-B,N)`, `edgeNodePair(C-E,N)`. Node-gap and hull residuals are empty.
- The two unpinned N families are identical and deduplicate to six candidates; the four endpoint families emit nothing because their nodes are pinned. All six ring-0 alternatives are rejected, and stages 1-4 run without duplicating the family.
- Required post-change diagnostics: `verifications=30`, `verificationWork=3_300`, `rounds=0`, no repairs, `conflictsDetected=4`, `edgeOverNodeDetected=4`, `edgeOverNodeResolved=0`, residual exactly the four initial keys, all budget flags false, unchanged positions and anchors.
- Both routes remain published and the route residual is empty; the sum of straight/bow/dogleg/fallback classifications is `2`, with no route budget stop.

The old expectation `6 / 660` is superseded by `30 / 3_300`. The increase is the measured cost of expanded search, not an improvement. [Design §§6.2, 9, 11; 2026-10-16 spec §7.1]

### 4.5 Exhaustion and partial-publication fixtures

Test name: `stopsStage1AtTheRemainingVerificationAllowance`.

Use `fullyBlockedFixture()` exactly as in §4.4, but seed `verifications=57` at the first visibility query of the initial detection with the existing `seedCountersAtDetection(correction, fixture, 1, 0L, 0L, 0L)`-style helper extended to the verification counter. Stage 0 must submit all six ring-0 alternatives, bringing the count to `63`; the first stage-1 attempt consumes the final allowance and stops at `64`. Required result: `verificationBudgetExhausted=true`, `verifications=64`, `rounds=0`, `detectionTruncated=false`, no repairs, complete stage-0 residual `edgeNodePair(A-B,N)`, unchanged positions and anchors, and no retry of a singleton after the stopping attempt. [Design §9 test 10; 2026-10-16 spec §5]

Test name: `publishesNeitherPartialRebuildRecordWhenTheTerminalScanStops`.

Use `preExistingSiblingResidualFixture(0.0,30.0,100.0)` exactly:

- Generation `10L`; nodes `a=(0,0)`, `c=(30,0)`, `d=(100,0)`, `p=(5,100)`, `q=(5,-100)`, all radius `8.0`.
- Three sibling enclosures `A`, `C`, and `D`, each containing its named node, with no common parent; one edge `p -> q`; no pins.
- The supplied boundary record has residual `hullPair(HA,HC)`, zero node residuals, and an accepted candidate target for `a` at `(-6,0)`; the safe target is `(16,0)`.
- The terminal rebuild costs `34` work units: one hull-pair comparison plus ten node-pair evaluations. Seed `verificationWork` at `65_536`, `65_535`, and `65_525` at detection 3. Each stops the rebuild before both records complete, leaves the complete edge residual empty, leaves `edgeOverNodeResolved=1`, keeps the original boundary diagnostics object and original hull residual, and publishes the accepted node position but neither partial rebuilt record. At `65_524`, the rebuild completes with `verificationWork=65_535` and a new boundary record.

A partial detection never enters candidate selection or accepted-position/geometry publication. A partial candidate detection never replaces the last complete residual. A partial hull or node rebuild never replaces the boundary or node-residual record. [Design §§6.2, 8; 2026-10-16 spec §§4.1-5.3]

### 4.6 Numerical eligibility and canonical-basis fixtures

The following source-backed controls remain exact:

- `rejectsIneligibleC2TargetsReachedByHypotScalarAndTargetOverflow` uses the three finite-input cases: endpoints `(-7e307,-7e307)` and `(7e307,7e307)` with `N=(0,0)`, radius `8.0` for `hypot` overflow; endpoints `(-100,-100)` and `(100,100)`, published `N=(-1.5e308,1.5e308)`, supplied geometry centre `(0,0)`, radius `8.0` for scalar projection overflow; and endpoints `(-100,1e308)` and `(100,1e308)`, published `N=(0,1.7e308)`, supplied geometry radius `8e307` for target overflow. Each has exactly one detected C2 key, zero verifications, zero repairs, unchanged finite positions, residual retained, and no verification-budget flag. `indexBudgetExhausted` is true only for the `hypot` case. [Design §8]
- `usesTheCanonicalHorizontalNormalForEitherEndpointOrder` uses `A=(-100,0)`, `B=(100,0)`, `N=(0,5)`, one edge, radius `8.0`, and all endpoint/geometry permutations. Each result accepts `N=(0,11)` at verification `1`, with no residual; raw zero bits for the x coordinate are positive zero. [Design §6.2; 2026-10-16 spec §3.1]
- `keepsTheFiniteC2FamilyWithinItsStatedBounds` must prove no family emits more than six candidates and no candidate has a target equal to its source centre. [Design §6.2]
- `resetsEveryCounterBetweenSeparateApplyCallsOnOneCorrectionInstance` must continue to clear all counters and flags. The first `crowdedNineObstructionsFixture` call stops at `verificationWork=65_536` and `verifications=1`; a subsequent `nineObstructionsFixture` call equals a fresh correction instance in diagnostics, positions, and displacements. [Design §§6.2, 8; 2026-10-16 spec §5]
- Extreme-coordinate crossing input uses nodes `(-1e300,-1e300)`, `(1e300,1e300)`, `(-1e300,1e300)`, `(1e300,-1e300)` and edges `{0,1}`, `{2,3}`. Expected `indexBudgetExhausted=true`, `detectionTruncated=false`, `verifications=2`, `rounds=1`, `crossingsResolved=1`, empty residual, and `verificationBudgetExhausted=false`. [2026-10-16 spec §§3.2, 5]

## 5. Frozen evidence and reported-workspace fixtures

### 5.1 Settled-reference ledger

The resource `freeplane_plugin_graph/src/test/resources/performance/settled-reference.csv` is immutable for this task. Its SHA-256 is `e9e988b7967ea6bb4f503dfb4c5af7d3e74a3328e28e0987e71ff0f0b8ac0578`. It has 26 lines: the header and 25 node rows, no enclosure rows, no pin rows.

The exact node ids are `n:m:00000000-0000-0000-0000-000000000001|p:m00-n0001` through `...|p:m00-n0025`. Coordinates are:

| ids | coordinates |
|---|---|
| `n0001..n0004` | `(-90,0)`, `(90,0)`, `(0,-25)`, `(0,25)` |
| `n0005..n0009` | `(500,0)`, `(700,0)`, `(900,0)`, `(1100,0)`, `(1300,0)` |
| `n0010..n0014` | `(1300,200)`, `(1100,200)`, `(900,200)`, `(700,200)`, `(500,200)` |
| `n0015..n0019` | `(500,400)`, `(700,400)`, `(900,400)`, `(1100,400)`, `(1300,400)` |
| `n0020..n0024` | `(1300,600)`, `(1100,600)`, `(900,600)`, `(700,600)`, `(500,600)` |
| `n0025` | `(500,800)` |

The compiler selects the first 25 canonical identities from `GeneratedWorkspace.forScenario("reference-2000-5000")`, creates edges `n0001-n0002`, `n0003-n0004`, and consecutive edges `n0005-n0006` through `n0024-n0025`, uses native connectors with occurrences `0..21`, no relationships, no pins, and no enclosures, then serializes the exact 26-line ledger. No force relaxation runs during generation or measurement. [Design §§4 Goal 5, 9 tests 4 and 11; 2026-10-12 spec §§6.10, 14.1]

`SettledReferenceFixtureShould.theCommittedLedgerIsTheDeterministicCompilationOfTheRecalibratedProjection` must continue to verify the byte identity and digest. The router result must remain `21/1/0/0` and every defining/sample list must compare exactly. [Design §§4 Goal 5, 9 tests 4 and 11]

### 5.2 Performance stage pins

Retain the existing `PerformanceMeasurements.Stage` order with `EDGE_CONFLICT("edge-conflict")` and `EDGE_ROUTING("edge-routing")` immediately after `HULL` and before `PLACEMENT`. The wire list is exactly:

```text
snapshot, projection, diff, mutation, force, correction, separation, hull,
edge-conflict, edge-routing, placement, full-worker, edt-swap, repaint,
accepted-batch-first-frame
```

On `reference-2000-5000` only, strict p95 thresholds are `edge-conflict <= 25_000_000L` and `edge-routing <= 10_000_000L`. All other scenarios use `DIAGNOSTIC_ONLY_THRESHOLD`. `PerformanceTripwiresShould.exposeTheCanonicalStageOrderAndThresholds` is the machine check. [2026-10-12 spec §7.1]

The settled A7a contract retains `TWO_MAP_FIRST_IDLE_STEP = 3103`, `INITIAL_SPACING_FIRST_IDLE_LIMIT = 6000`, strict force `50_000_000L`, strict full worker `100_000_000L`, strict first-frame p95 `150_000_000L`, and the settled-reference byte/digest checks. The raw A7b contract uses the repository-backed `RAW_REFERENCE_MAX_FRAME_NANOS = 5_000_000_000L`; `rejectsAFrameWhoseRoutingExceedsTheCompleteFrameBound` must use an actual `EdgeRouter.route(...)` call before advancing its delay clock. [Design §9 test 6; 2026-10-12 spec §§7.1, 14.1; 2026-10-16 spec §1]

### 5.3 Reported workspace replay

The acceptance replay is `doc/edge-node-crossing/probe/RealWorkspaceCrossingProbe` against the three recorded maps. The projection input is generation `1L`, workspace id `UUID.nameUUIDFromBytes("real-workspace-probe")`, three available maps, no relationships, no pins, and seven projected enclosures.

| Map | Source nodes | Projected nodes | Connectors | Graph groups |
|---|---:|---:|---:|---:|
| `Axiomatic_Set_Theory.mm` | 7455 | 171 | 1445 | 10 |
| `Point-set_Topology.mm` | 9025 | 247 | 1229 | 11 |
| `Real_Anaylsis.mm` | 9834 | 150 | 1474 | 7 |

The projected graph has 28 nodes, 12 edges, 7 enclosures, and 0 pins. All 28 projected nodes are graph-group discs. The exact settled node ids, centres, and radii are:

| node id | centre | radius |
|---|---|---:|
| `ID_1133378501` | `(104.46420667902956,-224.98453805640872)` | `9.6` |
| `ID_822182441` | `(85.44637197392952,-171.213919652232)` | `8.0` |
| `ID_130337169` | `(38.686598760115714,-190.87219907996854)` | `8.0` |
| `ID_1901523076` | `(62.1007476684317,-237.31087846818235)` | `8.0` |
| `ID_1387156674` | `(80.82661318591157,-248.85825741845753)` | `8.0` |
| `ID_297617399` | `(-48.564075160622956,-226.99190477383434)` | `8.0` |
| `ID_215702338` | `(-69.314297361256,-175.6251405199732)` | `8.0` |
| `ID_1887656421` | `(-104.03334197218959,-185.80824711206662)` | `8.0` |
| `ID_1222855984` | `(-86.91931900152834,-241.9541706754423)` | `8.0` |
| `ID_1732260180` | `(-63.74403616437911,-244.72090126759775)` | `8.0` |
| `ID_8832923` | `(72.14806255041611,2.287330526987308)` | `8.0` |
| `ID_379163670` | `(61.411732222969285,37.962643425551825)` | `8.0` |
| `ID_1007737944` | `(28.979107583160594,65.76607944656138)` | `8.0` |
| `ID_1121122434` | `(-9.106924386746634,70.781811456558)` | `8.0` |
| `ID_845954544` | `(-48.043796232606226,52.21036969085537)` | `8.0` |
| `ID_626846666` | `(-67.10495944993325,21.938948894707615)` | `8.0` |
| `ID_801357044` | `(-67.72638078532272,-18.86339907897916)` | `8.0` |
| `ID_675241169` | `(-45.25948235692915,-53.73579569443794)` | `8.0` |
| `ID_192579155` | `(-12.66973172580646,-69.18415891974112)` | `8.0` |
| `ID_1955681225` | `(30.451499009403825,-64.22102140846964)` | `8.0` |
| `ID_43431859` | `(61.10465422634267,-37.66520173144638)` | `8.0` |
| `ID_295118693` | `(57.23077385749022,239.58716715471076)` | `8.0` |
| `ID_1721568649` | `(47.50276098490488,274.28645323541446)` | `8.0` |
| `ID_309040602` | `(-41.72104675149096,233.4548674053434)` | `10.53594000115385` |
| `ID_1165913806` | `(-40.763714615787414,271.0586070339917)` | `8.0` |
| `ID_1763143138` | `(-39.998245070825966,199.91460123363362)` | `9.6` |
| `ID_1437543342` | `(-12.872196010357943,204.99102482375477)` | `8.0` |
| `ID_792798100` | `(-0.1460685129971556,187.04539363059476)` | `8.0` |

The 12 edges, in probe order, are:

```text
ID_1133378501 -> ID_1387156674
ID_1133378501 -> ID_1901523076
ID_1222855984 -> ID_297617399
ID_1887656421 -> ID_215702338
ID_192579155 -> ID_675241169
ID_626846666 -> ID_845954544
ID_1165913806 -> ID_309040602
ID_1437543342 -> ID_1763143138
ID_1437543342 -> ID_309040602
ID_1721568649 -> ID_295118693
ID_1763143138 -> ID_309040602
ID_1763143138 -> ID_792798100
```

The recorded settle has `steps=178`, `idle=true`, and one residual violation. The correction accepts exactly one `TRANSLATION` repair of `ID_1732260180`, `batchIndex=0`, movement `(1.177,-3.016)`, magnitude `3.238` to the probe's full precision result; correction diagnostics are `conflictsDetected=1`, `crossingsDetected=0`, `edgeOverNodeDetected=1`, `crossingsResolved=0`, `edgeOverNodeResolved=1`, `residual=0`, `rounds=1`, `verifications=1`, `repairs=1`, `verificationWork=1207`, `indexInsertedItems=300`, `indexPairChecks=84`, `subdivisions=0`, and all three stop flags false. The exact accepted centre is not an independent fixture value: the probe output records the movement rounded to three decimals, while the implementation must retain the production result at full precision. [Design §§4 Goals 1, 2, 5, 9 test 12]

The post-relocation route has `12 straight / 0 bow / 0 dogleg / 0 fallback`, no route stop, no routing detection truncation, `routeObstacleChecks=8`, `routePairChecks=0`, and `routeIndexItems=2156`. The reported-path audit has zero of 12 edges violating node clearance. [Design §§4 Goals 2, 3, 9 test 12]

## 6. Error handling, stop flags, and publication

### 6.1 Candidate-generation errors

Non-finite geometry, a zero-length chord, or a non-positive radius emits no C2 candidates. A pinned disc emits no C2 candidates and remains an edge-node residual. An unavailable trimmed attachment returns `null` and is skipped. Existing malformed-input exceptions remain unchanged. [Design §8]

A candidate rejected at generation is not a verification and cannot change candidate ordering, selection, positions, geometry, repairs, or residual publication. [Design §§6.2, 8]

### 6.2 Allowance stops

The following stop paths are distinct and must retain their existing flags and last-complete-state rules:

- **Initial or terminal detection truncation:** set `detectionTruncated=true`; do not enter selection with an incomplete conflict set. The existing detected-key stop may publish its accumulated residual lower bound as a diagnostic residual, but it must not publish a partial candidate position, partial geometry, or partial boundary rebuild. [Design §§6.2, 8; 2026-10-16 spec §5]
- **Candidate detection truncation during `verify`:** reject the entire attempt, set the existing stop state, and retain the last complete published residual and positions. Do not install the candidate. [Design §§6.2, 8]
- **Verification-count exhaustion:** the rejected attempt is not retried; `verificationBudgetExhausted=true` is reconciled with the existing flags; the last complete residual and positions remain published. [Design §§6.2, 8; 2026-10-16 spec §5]
- **Verification-work exhaustion:** reject the current attempt outright. Do not retry its singleton. Set `verificationBudgetExhausted=true`, retain the last complete residual, and never publish a partial hull/node rebuild. `stopsOnTheVerificationWorkCapWithoutADetectionStop` must observe exactly one verification when seeded at `65_536`. [Design §§6.2, 8]
- **Index or pair-check exhaustion:** preserve `indexBudgetExhausted` and the existing complete/lower-bound detection publication contract. No incomplete detection may influence ordering or accepted geometry. [Design §8; 2026-10-12 spec §§3.2, 4.3]
- **Four-round attempt cap:** stop without setting an exhaustion flag solely because `rounds == MAX_RELOCATION_ROUNDS`; the four-round fixture must retain `verificationBudgetExhausted=false`. [Design §§6.2, 9 test 9]
- **Maximum verification count after an accepted attempt:** retain the accepted positions and repair, reconcile the count flag, and do not claim verification exhaustion merely because the accepted attempt reached the count cap. [Design §§6.2, 9 test 9]

### 6.3 Partial scans cannot drive ordering or publication

A partial scan never supplies a conflict set to candidate selection, never determines the canonical order of a later attempt, never installs positions or geometry, and never replaces a complete boundary or node-residual record. A partial detection may appear only through the existing explicitly named residual lower-bound diagnostic path; that diagnostic publication is not a partial accepted state. This rule applies equally to stage 0 and stage 1. [Design §§6.2, 8; 2026-10-16 spec §§4.1-5.3]

Acceptance is atomic: positions, geometry, complete conflict detection, repair record, and counters for the accepted attempt are installed together. A rejected attempt changes none of them. [Design §§6.2, 8; 2026-10-16 spec §2]

## 7. Determinism and retained budget contracts

For identical `(projection, positions, geometry, visibleEndpoints)`, and for projection-list permutations that preserve the same logical items, correction and routing must produce identical canonical paths and diagnostics. Canonical edge order, canonical conflict-key order, fixed candidate order, fixed ladder order, and the existing sampling formula are all part of the deterministic contract. [2026-10-12 spec §6.10; Design §§4, 6.1-6.2]

`generateCandidates` remains bounded by eight nonempty candidate families per round; each family has at most six ring-local candidates and each ring-local pool is bounded accordingly. Unselected conflicts remain in the residual. Stage 1 cannot increase the family-selection bound because it expands only the already-selected families. [Design §6.2; 2026-10-16 spec §4.1]

`boundsTheCountedRelocationWorkOnADenseWideSpanFrame` retains all four bounds using `referenceScaleFan(500)`: index items `<= EDGE_CONFLICT_MAX_INDEX_ITEMS_PER_FRAME`, index pair checks `<= EDGE_CONFLICT_MAX_PAIR_CHECKS_PER_FRAME`, verification work `<= EDGE_CONFLICT_MAX_VERIFICATION_WORK_PER_FRAME`, and verifications `<= MAX_VERIFICATIONS_PER_FRAME`. The shared-leaf and wide-node-field diagnostics remain retained: `sharedLeafFan(513)` preserves its lower bound and `wideNodeFieldWithOneCrossing(512)` reports `crossingsDetected=2080`, `indexSubdivisions=4`, `indexBudgetExhausted=true`, and `indexPairChecks >= 2080`. [Design §§6.2, 8; 2026-10-16 spec §§5.1-5.3]

`reportedConvexQuad` remains two `STRAIGHT` fallbacks with `edgesStraight=0`, `edgesBow=0`, `edgesDogleg=0`, `edgesFallback=2`, one path crossing detected, and one crossing residual. `cellCornerFan` retains 15 path crossings. `closeParallelPaths` retains a `BOW` for the 2.9-unit neighbouring-cell separation. [Design §4 Goal 2; 2026-10-12 spec §§6.3, 6.10]

## 8. Supersession ledger

### 8.1 2026-10-12 graph-edge-crossing-avoidance specification

The following clauses are amended by this document:

- **§1.4, Live rung list derived from `ROUTE_LENGTH_CAP`:** retain the ten curved candidates and straight entry, retain the `A=0.5` order and ratios, and append the control-position group after the dogleg group. The new group is not a generalized midpoint item.
- **§6.1-§6.3, processing order, candidate ladder, and caps:** retain `ROUTE_LENGTH_CAP`, `MAX_BEND`, route allowances, obstacle model, sample-length acceptance, canonical edge order, and fallback behavior; amend only the rung set by appending §2.2's group and its ratio table.
- **§6.10, determinism:** retain unchanged, with the additional control-position order included in the fixed ladder order.
- **§14.1, A7a settled reference:** retain the recalibrated 25-node ledger, digest, zero exhaustion, non-trivial workload, crossing, and one accepted non-straight rung; add the exact `21/1/0/0` path classification pin.
- **§14.2, A7b raw reference:** retain bounded work and flag visibility, but the active frame bound is the repository-backed five-second value in §5.2 of this document.

The following clauses are retained without behavioral change: §3.1 anchor and attachment construction, §3.2 sign-only C1 detection and exponent normalization, §4.3 counted pair budgets, §5.1-§5.8 atomic correction/publication and affected-hull recomputation, §6.4 full clearance, and §7.1 stage/wire/threshold ordering. [Design §11]

### 8.2 2026-10-16 edge-node-clearance specification

The following clauses are amended by this document:

- **§3.2, Targets and bounds:** replace the single ring with the staged ring expansion in §3.1; retain basis construction, signed-zero normalization, finite checks, no invented direction, ring-0 `rho`, and inclusive `MAX_EDGE_NODE_EXCURSION`.
- **§3.3, Total order and deduplication:** retain the comparator and `CandidateIdentity`; scope deduplication to each ring and add the stage/ring boundary exactly as in §3.1.
- **§4.1, Selection:** retain eight-family selection, conflict-key ordering, incomplete-detection exclusion, and six-per-family generation; stage 1 expands only stage-0-selected families and does not regenerate C1 alternatives.
- **§4.2, Pool lifecycle:** retain greedy disjoint batches, singleton retries, verification limits, and round limits; add the failed-round stage boundary and return to stage 0 after every acceptance.
- **§7.1, Controls:** replace `6 / 432` with `30 / 2_160` for `fullyBlockedFixture`, and `6 / 660` with `30 / 3_300` for `duplicateChordFixture`; retain both residuals and describe the extra work only as measured expansion cost.

The following clauses are retained without behavioral change: §1 authority and amendment scope, §2 rejection/acceptance/publication records, §3.1 basis formulas, §5.1 counted verification work, §5.2-§5.3 stop and publication behavior, §6.1-§6.4 E1/E2/E3/prominence fixtures, §8.4 installed E3 evidence (`verifications=3`, `verificationWork=252`), the relationship fixture, A1-A4, C1 geometry, pin semantics, and the counted-budget framework. [Design §11]

## 9. Test-to-clause traceability

| Normative clause | Required asserting test or fixture |
|---|---|
| Goal 1, no failed round | `repairsNineIndependentObstructionsInBoundedBatches`; E2; E3; prominence; `acceptsTheC2TargetAtExactlyTheMaximumExcursion` |
| Goal 1, failed round may gain repair | `expandsTheA1RejectedFamilyToTheFourthRing`; `expandsTheA2RejectedFamilyWithoutReselectingIt`; `stopsStage1AtTheRemainingVerificationAllowance` |
| Goal 2 per-edge preservation | `preservesTheSettledReferenceAndNamedLadderFixtures`; `routesTheFirstEdgeAroundALaterChordsCrossing`; `acceptsTheSameSideDoglegWhenStraightAndBowsAreBlocked`; `waivesTheDistanceRequirementForATightFan`; `exposeStraightFallbackForAStraightOnlyScaffold` |
| Control-position constants and order | `computesTheControlPositionRatioTable`; `routesTheMeasuredOffMidpointObstacleWithAControlPositionBow`; `rejectsMovingControlPositionGroupAheadOfMidpointBows`; `keepsTheDoglegLadderAheadOfTheControlPositionGroup` |
| Existing `analyticRatio` untouched | `preservesTheSettledReferenceAndNamedLadderFixtures`; `rejectsTheDeadDoglegRungAndFallsBack` |
| Full `clears(...)` gate | `usesTheFullClearPredicateForControlPositionCandidates`; `routesTheFirstEdgeAroundALaterChordsCrossing`; `keepsClearOfAParallelPathInTheNeighbouringCell` |
| Genuine max-bend skip | `skipsControlPositionCandidatesThatExceedMaxBend` |
| Ring constants and stage boundary | `expandsTheA1RejectedFamilyToTheFourthRing`; `expandsTheA2RejectedFamilyWithoutReselectingIt`; `stopsStage1AtTheRemainingVerificationAllowance` |
| 192 versus 24 proposal bound | `keepsTheFiniteC2FamilyWithinItsStatedBounds`; `boundsTheCountedRelocationWorkOnADenseWideSpanFrame` |
| Per-ring deduplication | `deduplicatesIdenticalAlternativesFromOverlappingChords`; `duplicateChordFixture` |
| C1 generated once and not reselected | `expandsTheA1RejectedFamilyToTheFourthRing`; `expandsTheA2RejectedFamilyWithoutReselectingIt` |
| Return to stage 0 after acceptance | E1; `stopsAtFourAcceptedRoundsWithoutClaimingExhaustion`; `repairsTheBlockedColumnWithTheFirstFeasibleDiagonal` |
| Inclusive excursion | `acceptsTheC2TargetAtExactlyTheMaximumExcursion`; `rejectsEveryC2TargetBeyondTheMaximumExcursionWithoutClamping` |
| A1-A4 subset verification | E2; E3; `vetoesTheUnsafeDirectTargetThatAddsANodeGapAndAcceptsTheOtherSide`; `vetoesTheUnsafeSiblingTargetWithA3Subset` |
| Pin and dormant-pin semantics | `keepsThePinnedColumnObstructionAsAnExactResidual`; `repairsTheColumnWithADormantPin` |
| Counter re-pins | `fullyBlockedFixture`; `duplicateChordFixture`; `repairsTheBlockedColumnWithTheFirstFeasibleDiagonal` |
| Verification-work and no singleton retry | `stopsOnTheVerificationWorkCapWithoutADetectionStop`; `stopsStage1AtTheRemainingVerificationAllowance` |
| Partial detection/rebuild publication | `rejectsTheSecondRoundCandidateWithoutLosingTheLastCompleteResidual`; `publishesNeitherPartialRebuildRecordWhenTheTerminalScanStops`; `publishesTheLowerBoundResidualFromThePostRoundAndTerminalDetections` |
| Four-round cap is not exhaustion | `stopsAtFourAcceptedRoundsWithoutClaimingExhaustion` |
| Numerical ineligibility | `rejectsIneligibleC2TargetsReachedByHypotScalarAndTargetOverflow`; zero-length and zero-movement controls |
| Determinism | `isDeterministicAcrossRunsAndPermutedEdgeOrder`; `theCommittedLedgerIsTheDeterministicCompilationOfTheRecalibratedProjection` |
| Performance stage order and thresholds | `PerformanceTripwiresShould.exposeTheCanonicalStageOrderAndThresholds`; `referenceFixtureStaysInsideTheStrictWorkerAndFirstFrameGates`; `rejectsAFrameWhoseRoutingExceedsTheCompleteFrameBound` |
| Frozen evidence | settled-reference digest test; `TWO_MAP_FIRST_IDLE_STEP`; `INITIAL_SPACING_FIRST_IDLE_LIMIT`; `stopsAtFourAcceptedRoundsWithoutClaimingExhaustion` |
| Actual user workspace | `RealWorkspaceCrossingProbe` replay and zero-clearance-violation audit |

## 10. Open items for the reviewer

1. **A2/A3 staged fixture is a construct-or-declare obligation, not a missing value.** §4.2 now specifies the
   search family, the recordable outcome and the explicit "search limit" record when no fixture exists. No
   reviewer-supplied coordinates are required. This item is closed.
2. **A1 test 7 residual wording.** Resolved in the design by amendment `c6bfd3a0c6`: the accepted move removes
   the only conflict, so the final complete conflict set is empty. §4.1 asserts an empty final set. Closed.
3. **A7b historical value.** The superseded `3_000_000_000L` in `2026-10-12-graph-edge-crossing-avoidance-spec.md`
   §14.2 Revision 8 is not normative. The active contract is `5_000_000_000L` from commit `8d11991533` pinned by
   `GraphWorkspacePerformanceDiagnostic.RAW_REFERENCE_MAX_FRAME_NANOS`. Closed.
4. **Counter re-pin interpretation.** §4.4 records `30 / 2_160` and `30 / 3_300` as measured expansion cost with
   unchanged residuals. Reviewer confirmation is requested that the acceptance record must use that wording
   wherever those control results are reported. **Still open — needs a decision, not a fix.**
