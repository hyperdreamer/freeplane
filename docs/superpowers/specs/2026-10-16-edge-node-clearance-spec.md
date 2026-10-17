# Edge–node clearance repair — technical specification

Task identifier: `2026-10-16-edge-node-clearance`

Status: **review draft, awaiting technical-specification sign-off**. This document specifies future implementation; neither implementation nor technical-specification approval is recorded here.

## 1. Authority, baseline and amendment scope

The user approved option A in [the design](2026-10-16-edge-node-clearance-design.md): move an obstructing unpinned node locally so simple repaired edges remain straight. The independent design review passed. This is a focused amendment to [the preceding crossing-avoidance specification](2026-10-12-graph-edge-crossing-avoidance-spec.md), evaluated against unchanged production baseline `0734fb23db6873f4ec64c3e3e29fc8c3e17edc7a` and docs-only design commit `d4af56401df280ae7bb9f36bd56e63dd70ca2dac`.

This amendment supersedes:

- The preceding spec §15 Revision 9 requirement to suppress every complete initial in-index frame with more than eight conflicts, the early-return branch in `EdgeConflictCorrection.relocate`, and `EdgeConflictCorrectionShould.suppressesAFrameWithMoreThanTheRepairableConflictBound`. Remove the suppression constant `EDGE_CONFLICT_MAX_REPAIRABLE_CONFLICTS_PER_FRAME` and its diagnostic interpretation; do not retain a compatibility branch.
- The preceding spec §5.2 C2 single signed-normal candidate and `addEdgeOverNodeCandidate` implementation. Replace them with the finite family in §3.
- Candidate generation/selection and rejected-pool traversal only as specified in §§3–4. C1 crossing geometry, A1–A4 verification, detection identities, counter units, publication and routing otherwise retain their current contracts.

The active preceding-spec reconciliation for A7b is **5_000_000_000 ns**, introduced by `8d11991533b9be9414fec0fae6729805ee3ba9fe` and pinned in `GraphWorkspacePerformanceDiagnostic.RAW_REFERENCE_MAX_FRAME_NANOS`. Older three-second mentions in that specification are superseded. No counted cap, timing threshold, fixture, relaxation engine, routing rung, pin, node gap or enclosure rule changes in this task. No automatic hiding of unresolved edges is added.

Implementation is concentrated in `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/EdgeConflictCorrection.java`, with behavioral tests under the same module. Reuse the existing `apply`, `Candidate`, `Evaluation`, `verify`, counted detection, `BoundaryInvariants`, `NodeSeparationProjection` and result/diagnostic interfaces. The `LayoutWorker` → `LayoutSettleLoop` → `EdgeRouter` → `CanvasState` sequence already carries corrected positions; this amendment requires tests of that sequence, not a new publication architecture. No new production public API or core-module modification is expected. The installed-application acceptance harness in §8.4 adds three module-local test sources and test-only Gradle tasks; its agent and replay binding are confined to the disposable evidence launch, never packaged in the graph bundle or distribution.

## 2. Retained invariants and interfaces

The inputs and output of `EdgeConflictCorrection.apply(projection, positions, metrics, pins, visibleEndpoints, boundaryResult)` remain unchanged. Initial detection uses `boundaryResult.geometry()`, including its actual `NodeGeometry.radius()` values. Do not replace that geometry with synthetic radius-eight discs or an initial fresh geometry computation. Production-consistent regression fixtures must compute their boundary geometry with `GraphGeometryEngine.computeHulls`; the old general-purpose test helper installs radius 14 by default and must not be used unchanged for the radius-eight fixtures below.

Every candidate is an immutable proposed update of existing node centers. Its `fromCentres` come from the current accepted positions and its `toCentres` contain exactly the moved nodes. Anchors retain the input map's keys and coordinate values. Active pins, as identified by `PinProjection.active()`, exclude every candidate moving that node, including C1 swaps/mirrors and every C2 alternative. Dormant pins do not exclude a node. Nodes, edges, enclosure membership, visibility and prominence are immutable for this call.

Verification retains the existing order and complete scope:

1. Build candidate positions, charge the affected-hull work, and call the existing `recomputeHulls` with `BoundaryInvariants.changedHulls`. Recompute affected ancestors as currently implemented.
2. Detect **all** C1/C2 conflicts in the candidate projection using the shared frame allowance. A1 requires a complete `C_out` with `C_out ⊂ C_in`: identity-set inclusion and strict size decrease. `C_in` is the complete current frame detection, never the selected families alone.
3. Run the counted current and candidate node-pair scans. A2 requires `gapPairs_out ⊆ gapPairs_in`, preserving the existing prominence-scaled `r_i + r_j + NodeSeparationProjection.MIN_GAP`, where `MIN_GAP = 6.0`. A pre-existing violation may remain; an equal-count replacement by a different violating pair is forbidden.
4. Run the counted current and candidate `BoundaryInvariants.detectPairKeys` scans. A3 requires `hullPairs_out ⊆ hullPairs_in`, including sibling crossing/containment and ancestor escape identities. Use the current accepted geometry as the per-attempt baseline, not just the initial count or the boundary diagnostics' count.
5. Keep the A4 enclosure-path precondition/assertion. It does not substitute for the A3 containment veto.

A rejection changes no current position, geometry, conflict set or repair record. An acceptance installs the verified positions, geometry and complete detection together and records one existing `Repair` per candidate, with its exact kind and moved-node maps. All candidates in an accepted attempt share its zero-based `batchIndex`. C2 alternatives continue to use `RepairKind.TRANSLATION`.

## 3. Finite C2 candidate family

### 3.1 Eligibility and local basis

Use the existing complete C2 `Conflict`'s attachment chord endpoints `a`, `b` and obstructing `Disc`. Endpoint nodes remain excluded by detection. Require an existing, unpinned obstructing node position `p`; finite chord endpoints and center; and the disc's finite positive actual drawn radius `r`. Compute the following in binary64, with explicit finite checks before constructing a `LayoutPoint`:

```text
d = b - a
L = hypot(d.x, d.y)
rawNormal = (-d.y / L, d.x / L)
n = lexicographically smaller of rawNormal and -rawNormal
u = (-n.y, n.x)
s = (p.x - a.x) * n.x + (p.y - a.y) * n.y
rho = r + EDGE_NODE_CLEARANCE          // EDGE_NODE_CLEARANCE = 3.0
h = 2*r + MIN_GAP + EDGE_NODE_CLEARANCE // 2*r + 9.0
```

For the normal comparison, normalize any signed zero to `+0.0` first, then compare x and y with `Double.compare`; normalize zero components again after choosing `n` and `u`. Consequently a horizontal chord has `n = (0,-1)` and `u = (1,0)`, independently of endpoint traversal direction. This canonicalization applies to the new C2 family; C1 mirror formulas retain their current behavior.

If subtraction, `L`, a basis component, `s`, `rho` or `h` is non-finite, or `L <= 0`, emit no C2 candidates for this conflict. Missing centers/radii and degenerate chords likewise emit no candidate. Keep the detected conflict as a residual if no other repair removes it. Do not invent a direction or use a fallback radius. This numerical ineligibility is not budget exhaustion. Existing malformed-input exceptions remain unchanged.

### 3.2 Targets and bounds

For each `sigma` in `{+1,-1}` and `t` in `{0,-h,+h}`, construct:

```text
normalShift = sigma*rho - s
q(sigma,t) = p + normalShift*n + t*u
movement = hypot(q.x - p.x, q.y - p.y)
```

This yields two direct perpendicular targets (`t == 0`) and four diagonal targets. It preserves the center's along-line coordinate before adding `t`, and sets signed distance to the infinite chord line to `sigma*(r+3)`. It does not extrapolate the attachment chord or claim that algebra alone proves clearance against other geometry. A1–A4 must verify each proposed move.

Add package-private selection pins in `EdgeConflictCorrection`:

| Pin | Value | Meaning |
| --- | ---: | --- |
| `MAX_CANDIDATE_FAMILIES_PER_ROUND` | `8` | Nonempty, distinct-conflict families retained at one accepted-position snapshot |
| `MAX_CANDIDATES_PER_BATCH` | `8` | Proposed disjoint candidates in one verification attempt |
| `MAX_EDGE_NODE_EXCURSION` | `64.0` | World-unit C2 displacement from the current accepted center per candidate |

Discard a target before point construction if any computed component/displacement is non-finite, if movement is zero, or if movement exceeds `64.0`. Normalize target zeros to `+0.0`. Do not clamp or enlarge an over-limit target. At most six C2 candidates exist per family. The offset `h` provides a local route around a neighboring disc column: at radius eight it is 25, just beyond the 22-unit required node-center separation. These bounds are candidate-selection limits, not increases to retained counted-work budgets. A node moved in successive accepted C2 rounds can move at most 256 units in total path length over the retained four rounds; C1 geometry is not newly constrained by the C2 excursion pin.

### 3.3 Total order and deduplication

Each C2 family's total order is the ascending tuple:

```text
(stage, movement, sideRank, alongRank, candidateIdentity)
stage = 0 for t=0, otherwise 1
sideRank = 0 for sigma=+1, 1 for sigma=-1
alongRank = 0 for t=0 or t=-h, 1 for t=+h
```

Compare finite movement with `Double.compare`. **Both direct targets precede every diagonal**, then prefer smaller displacement within a stage. The side and along ranks resolve exact ties. Do not use an epsilon comparator, map insertion order, or unordered-set iteration to resolve ties. For the symmetric radius-eight column, the order is `(0,-11)`, `(0,11)`, `(-25,-11)`, `(25,-11)`, `(-25,11)`, `(25,11)`.

The private candidate identity is `(RepairKind, sorted moved-node keys, corresponding from/to x/y values)`, with node keys ordered by `CanonicalKeys.node` and coordinates represented by `Double.doubleToLongBits` after normalizing zero. For its final ordering tie-breaker, compare `RepairKind.name()` with `String.compareTo`, then compare the node-entry sequences lexicographically: canonical node string, from-x bits, from-y bits, to-x bits, to-y bits. Compare each bit field with `Long.compareUnsigned`; if one sequence is a prefix, the shorter sequence comes first. Equality requires all these fields to match. The identity excludes the generating conflict key so duplicate proposed updates from different conflicts collapse. Keep the first candidate in the specified traversal order; do not round coordinates to merge nearby but different moves. Within one immutable current state, retained candidate identities are unique. A private family/entry wrapper may hold the owner conflict key, rank and existing `Candidate`; it need not change `Candidate`'s externally observable contract.

## 4. Bounded family selection, batching and retry

### 4.1 Selection from complete detection

At the beginning of each accepted-position snapshot, visit the complete conflict list in ascending identity-key order (`CanonicalKeys.edgePair` for C1, `CanonicalKeys.edgeNodePair` for C2). For C1, retain its existing two adjacent swaps followed by four mirrors, with the existing canonical corner cycle, pin filtering and swap enclosure-path eligibility. For C2, use §3. Sort a family's C2 alternatives before deduplication; retain C1's existing generation order.

Deduplicate proposed updates against earlier retained entries and skip empty families. Select the first **eight nonempty families**, then stop generating families. Flatten those selected families in conflict-key order, preserving each family's order. At most 48 candidate entries enter the pool: both C1 and C2 have at most six. If fewer than eight eligible families exist, retain all of them. This is a candidate-generation bound only: the input detection and every verification still cover the entire projection.

The same selection policy applies to a complete initial detection regardless of conflict count and regardless of whether the index required complete direct enumeration. Incomplete initial detection never enters selection. There is no special `conflictsDetected > 8` branch. Unselected conflicts remain in `C_in` and the reported residual. Selection does not create a truncation/exhaustion flag or imply that the unselected conflicts were examined for repairability. Eight blocked families can prevent later families from being searched in this call; no global fairness or universal progress claim is made.

### 4.2 Pool lifecycle and attempts

Use the following schedule over the retained pool; all verifications use the unchanged accepted state until one succeeds:

```text
pool = ordered selected entries for current complete C_in
while pool nonempty and frame allowances permit:
    batch = first-fit greedy scan of pool, up to 8 entries
            add only entries whose moved-node set is disjoint
            from all entries already in this batch
    remove precisely the selected entries from pool
    attempts = [batch]
    if batch.size > 1:
        append each selected candidate as a singleton, in batch order
    verify attempts sequentially, checking frame limits before each
    on ACCEPT:
        install the verified state; record repairs; increment rounds
        discard the old pool and every remaining old-state attempt
        continue the existing post-round detection/next-round schedule
    on ordinary REJECT:
        continue the remaining attempts, then greedy selection
        over the reduced pool
    on verification/detection/work STOP:
        abandon this pool and stop relocation for this frame
if no attempt accepted from the selected pool:
    stop relocation; do not regenerate the same pool or widen selection
```

Alternatives sharing a node never enter a batch together. Skipping such an entry while assembling a batch does **not** remove it from the pool. Removing an attempted preferred move does not remove its untried opposite/diagonal alternatives. Thus, after all selected entries in a rejected batch also fail singly, the next greedy scan advances to the remaining alternatives instead of selecting the rejected preferred move again.

The actual baseline uses a rejected batch followed by singleton retries; it has **no recursive bisection**. Preserve that schedule explicitly. Do not add bisection, split halves or change singleton order under the name of retry. A one-entry batch is evaluated once, without a duplicate singleton retry. A candidate in a multi-entry batch may be evaluated once in that batch and once singly, because a sibling's effect can cause batch rejection. The reported C1 quad must still reject its two-swap batch and accept its first singleton in exactly two verifications. Mixed C1/C2 batches retain individual `RepairKind` records.

An ordinary rejected alternative is consumed only for this current-state pool. Following an accepted move, regenerate from the new complete detected conflicts; previously rejected moves may be different or feasible in that new state. Counters are never reset there. Accepted attempts, not rejected alternatives, increment `rounds`; the four-round limit is unchanged.

## 5. Shared accounting, stops and publication

### 5.1 Retained caps

| Frame allowance | Retained cap | Charged work |
| --- | ---: | --- |
| Correction index items | `262_144` | Each primitive-cell write or outermost direct examination, including subdivision recollects |
| Correction pair checks | `131_072` | Each counted crossing/segment-distance predicate |
| Detected-key units | `4_096` | Each distinct-key insertion in one detection's accumulator; the same key found in another detection charges another unit to the shared frame total |
| Verification work | `65_536` | Both A2 node-pair scans, both A3 hull-pair scans, affected-hull recomputation charges, and terminal boundary/node rebuilding |
| Verifications | `64` | Each actual batch or singleton evaluation, including rejected attempts |
| Accepted rounds | `4` | Each accepted attempt |
| Router index / obstacle / pair work | `262_144 / 131_072 / 131_072` | Existing router units and stop behavior |

Reset correction counters once on entry to `apply`, including when a correction instance is reused. Initial detection, all candidates/retries, post-round detection, terminal detection and terminal rebuild share these totals. Alternative enumeration/sorting has the finite bounds in §§3–4 and adds no unbounded geometric precheck, node-pair scan or separate counter reset. Candidate eligibility must not perform an uncounted A2/A3 scan to avoid charging failed alternatives. Keep the verifier's affected-hull charge and existing counted scan APIs rather than invoking unbounded diagnostic helpers in production.

Whole-frame verification remains expensive: with 257 positioned nodes the two A2 scans cost `2*C(257,2) = 65_792` units before hull/rebuild work. Selecting eight candidates does not avoid that cost. Only small in-budget fixtures below require progress. The limits are not a universal elapsed-time proof; §8 retains separate timing gates.

### 5.2 Phase-specific outcomes

| Phase/outcome | Positions and residual | Flags and further work |
| --- | --- | --- |
| Initial detection stops on its counted allowance | Input positions; ascending distinct keys accumulated by that detection are a lower-bound residual; zero resolved counts | `detectionTruncated=true`; zero rounds/verifications/repairs; no terminal rebuild |
| Complete detection uses index direct-enumeration fallback | Complete detected set | Preserve `indexBudgetExhausted`'s existing meaning; selection can proceed. This flag alone does not truncate detection |
| Candidate detection or A1–A4 work stops | Reject the candidate; retain last accepted positions and their **last complete** `C_in` as exact residual | `verificationBudgetExhausted=true`, published `detectionTruncated=false`; no more retries or relocation detections |
| Ordinary rejection / pool has no accepted alternative | Current accepted positions and complete residual | No exhaustion flag merely because candidates were vetoed or families were bounded |
| Post-round or terminal published-position detection stops | Last accepted positions; ascending accumulated keys from this published-position detection are a lower-bound residual | `detectionTruncated=true`; skip terminal rebuild; never substitute a candidate's partial set |
| Attempt limit reached | Last accepted positions and complete retained residual | Keep the existing transition rule: rejected cap attempt with remaining conflicts sets `verificationBudgetExhausted`; accepted cap attempt sets it if residual conflicts remain, but reaching attempt 64 while clearing all conflicts alone does not set it |
| Accepted-round limit reached | Last accepted positions; retain existing terminal detection schedule | Four rounds alone is not an exhaustion flag; unsolved identities remain visible |
| Terminal boundary or node rebuild stops | Keep accepted positions and complete edge residual; retain this frame's boundary-stage diagnostics and node residual, publishing neither partial rebuilt record | `verificationBudgetExhausted=true`, `detectionTruncated=false`; all consumed terminal units included in the total |

Preserve the existing post-round/terminal detection schedule in `relocate` and preceding spec §13.2; do not remove scans to fund more alternatives. For example, an accepted move below the round/attempt limits is followed by the current post-round detection; an accepted, non-stopped frame also receives the existing terminal detection. Retain all applicable cap flags at a stop, with `indexBudgetExhausted` reserved for direct enumeration rather than repurposed as a generic truncation flag. No incomplete candidate detection can produce an accepted move or resolved count.

### 5.3 Publication and unresolved geometry

After an acceptance, the current geometry is the verifier's recomputation from exactly the accepted positions. Perform the existing counted terminal `describeCounted` and node-residual rebuild when eligible under §5.2, publishing rebuilt boundary/node diagnostics only if both scans complete. `LayoutWorker` continues merging accepted displacements and decorating the frame. `LayoutSettleLoop.handleFrame` computes hulls from that frame's positions, routes those positions/geometry, and publishes their immutable `CanvasState`. Do not route pre-repair positions or reuse paths from another frame.

Keep the current `EdgeRouter` straight fallback when pins, constraints, budgets or geometry outside the finite search prevent a repair or route. Fallbacks remain present, classified and observable. Correction residuals must not be erased merely because a router bow avoids a conflict; a clear path does not prove local node repair. Conversely, an unresolved fallback can still intersect a node. This amendment guarantees clearance and straight paths for its repairable fixtures, not every possible graph.

## 6. Executable regression fixtures and final-path oracle

### 6.1 Common construction and assertions

Use JUnit 4 and the existing projection/native-connector helpers. All identities below are persisted node IDs in map `00000000-0000-0000-0000-000000000001`; use generation `1`. Labels equal their IDs. Unless overridden: no pins/enclosures, empty anchor map, every node visible, and one native connector per listed edge with neither arrowhead, source/target IDs as listed. Construct geometry through the production `GraphGeometryEngine` and assert actual radii are 8.0; undirected connectors do not increase outgoing prominence. Use `AwtGeometryTextMetrics` with Dialog/plain/12 and an antialiased fractional `FontRenderContext`, as in the existing tests. The initial boundary scaffold must carry the actual geometry and truthful empty gap/hull residuals for these unconstrained fixtures.

For E1–E3, the prominence fixture, and the E3 publication witness require:

- Exact expected visible edge-key coverage in `EdgePaths.byEdge`; a missing or unresolved path fails before clearance checks. All samples and state positions must be finite; each path has at least two samples. Check initial conflicts/gaps to prove the obstruction exists and unrelated constraints are initially satisfied.
- The accepted movements, counts and straight classification below, plus an empty final correction residual. All untouched node centers and all anchor coordinate values remain equal to input; gaps/hull-pair identities introduce no new pair. Inspect `repairsApplied`/`appliedDisplacements`, not just a moved mock state.
- Correction `detectionTruncated`, `verificationBudgetExhausted`, `indexBudgetExhausted` and router `routeBudgetExhausted`, `detectionTruncated` are all false; all counters remain within §5.1. Route classification sums to the number of expected visible edges.
- **Final-path oracle:** for each present expected edge, every consecutive segment `(v_i,v_(i+1))` of its actual `EdgePath.samples()` is checked against every unrelated node disc in the **same rebuilt/published geometry**. Exclude only the edge's two attached node endpoints, not nearby nodes, pinned nodes, or nodes in the same enclosure. Include paths classified as fallback when a control produces them. Compute the Euclidean point-to-closed-segment distance with a clamped projection (zero-length segment uses point distance). Require `distance - actualRadius >= 3.0 - 1e-9`. The `1e-9` world-unit tolerance matches the existing measurement epsilon; do not use bow sagitta, screen pixels, bounding boxes, endpoints alone or a physical-radius-only test as clearance tolerance.

Small-fixture arithmetic permits an independent ordinary point/segment calculation in test code; do not call the correction's conflict predicate as the sole final-path oracle. Assert expected straight paths use `Shape.STRAIGHT`, two samples, and the current geometry's facing rim attachments. Their 184-unit length for the radius-eight horizontal edges is a useful additional check. A straight **fallback** has the same shape, so require `edgesFallback == 0` and `edgesStraight` counts for repairable cases.

### 6.2 E1 — nine independent obstructions

For `i=0..8`, define:

| ID | Center |
| --- | --- |
| `Ai` | `(-100,150*i)` |
| `Bi` | `(100,150*i)` |
| `Ni` | `(0,150*i+5)` |

There are 27 nodes and exactly nine edges `Ai → Bi` (arrows off). Initial detection has exactly nine C2 identities `edgeNodePair(Ai–Bi,Ni)`, zero C1 crossings and zero node-gap/hull violations. Ascending canonical conflict ordering selects indices 0 through 7 first, then index 8 from the new complete residual.

Required result: every `Ni` moves to `(0,150*i+11)`; all `Ai/Bi` stay fixed. Nine `TRANSLATION` repair records report these moves, eight with batch index 0 and one with batch index 1. `rounds=2`, `verifications=2`, `edgeOverNodeDetected=9`, `edgeOverNodeResolved=9`, both crossing counts zero and `conflictsResidual` empty. All nine paths are successful straights from `(-92,150*i)` to `(92,150*i)`: `edgesStraight=9`, other route classifications zero. The counted verification total is `5*C(27,2)=1_755` (two current/candidate A2 pairs of scans plus one terminal scan; no hull work). This exact total also proves that accepting the first eight does not reset the frame counter before the ninth.

Baseline fails because suppression performs zero verifications and leaves nine residuals/nine bows. Zero final intersections alone is not a regression assertion.

### 6.3 E2 — preferred side blocked, opposite side clear

IDs and centers: `A=(-100,0)`, `B=(100,0)`, `N=(0,5)`, `O=(0,30)`; edge `A → B`. Initial C2 is exactly `edgeNodePair(A–B,N)`, with zero gaps/hull violations. The nearest direct target `(0,11)` is only 19 units from O and must fail A2's required 22; the opposite direct target `(0,-11)` is 41 units from O and is feasible.

Required result: only N moves, to `(0,-11)`; `rounds=1`, `verifications=2`, one translation at batch index 0, detected/resolved C2 both 1, empty residual and zero crossing counts. Verification work is `5*C(4,2)=30`. The one successful straight path has samples `(-92,0)` and `(92,0)`, with no bow/dogleg/fallback. This requires the opposite **direct** target before diagonals. Baseline leaves N unchanged and uses a bow.

### 6.4 E3 — seven-node column and actual publication

IDs and centers: `A=(-100,0)`, `B=(100,0)`, `N=(0,0)`, `O1=(0,-22)`, `O2=(0,-44)`, `O3=(0,-66)`, `O4=(0,22)`, `O5=(0,44)`, `O6=(0,66)`; edge `A → B`. There are nine nodes total; seven form the column. Initial C2 is exactly `edgeNodePair(A–B,N)` and gaps are zero, including column neighbors exactly 22 apart.

Here `r=8`, `rho=11`, `h=25`. Both direct targets `(0,±11)` are 11 from a column neighbor and fail A2. The first diagonal `(-25,-11)` has displacement `sqrt(25²+11²)=sqrt(746)=27.313000567... < 64`. Its nearest remaining column node is O1 at `(0,-22)`, also `sqrt(746)>22` away. Its distance to the horizontal edge is 11, giving precisely three units beyond the drawn radius. Endpoints and all other column nodes are farther away. Thus it introduces no gap or C2 conflict.

Required correction: only N moves to `(-25,-11)`; `rounds=1`, `verifications=3`, one translation at batch index 0, detected/resolved C2 both 1, empty residual and zero crossing counts. Verification work is `7*C(9,2)=252`. Require the successful straight `(-92,0)–(92,0)` and the full final-path oracle. Baseline fails with unchanged N, one fallback and a path through N.

Add `LayoutSettleLoopShould.publishesAStraightClearPathAfterRepairingTheBlockedColumn`. Drive the **real `LayoutWorker`**, created with its existing `Supplier<LayoutEngine>` constructor and a deterministic replay engine returning the exact raw E3 positions. Adapt that worker to the existing package-private `LayoutSettleLoop.FrameStepper` interface, delegating submit/step/pause/restart/lastValidFrame/close; implement adapter reset by closing and recreating the worker with the same replay factory. Construct the loop through its existing injectable constructor with `ImmediateEdt`. Capture its first actual consumer-published state in a `CompletableFuture<CanvasState>` and perform assertions on the test thread after a five-second bounded wait; do not rely on assertions thrown inside a callback that the loop can catch. A stepper containing a manually pre-corrected frame is insufficient. Close the loop/worker in `finally` through the existing completion/cleanup helpers, and check that publication is not FAILED. Inspect the first state before later replay steps can replace its repair diagnostics. Assert generation/projection, the movement and diagnostics above, geometry node centers equal to `state.layout().positions()`, expected path coverage, straight classification and exhaustive same-state clearance. `withStatus` must continue retaining the same paths. This is the required end-to-end publication witness without a new production API.

## 7. Controls, preservation and traceability

### 7.1 Required controls

**Pins:** rerun E3 with an active pin on N at `(0,0)`. Require unchanged node/anchor positions, zero verifications/rounds/repairs, exact one-C2 residual, no false resolved count and no correction exhaustion flag. Route it and require the expected edge present; preserve its unresolved fallback/intersection as observable evidence, rather than demanding impossible clearance. Rerun with the same pin dormant and require E3's normal repair. Also retain existing C1 active-pin exclusion tests.

**No candidate clears:** use A/B/N as in E3, plus `U=(0,22)`, `D=(0,-22)`, `UL=(-25,25)`, `UR=(25,25)`, `DL=(-25,-25)`, `DR=(25,-25)`, with the sole edge A–B. All radii are eight and initial gaps are zero. Each direct target is 11 from U or D; each diagonal is 14 from the corresponding diagonal blocker. All six fail A2. Require `rounds=0`, `verifications=6`, no repairs, unchanged positions, verification work `6*2*C(9,2)=432`, exact one-C2 residual and no correction budget flags. The routed edge must remain present, with its actual classification and minimum segment/disc distance recorded; there is no guaranteed straight/clear output for this control. This distinguishes finite-search failure from budget exhaustion.

**Collection order:** repeat E1–E3 and the no-candidate control with node/edge lists, positions/geometry maps and visible-endpoint insertion order reversed, and with fixed-seed shuffles. Preserve identities/coordinates/contributor directions. Compare accepted positions, repairs including from/to maps and batch indices, diagnostics/counters, route classifications and actual sample coordinates; exclude elapsed timings. Also retain the reported-quad and mixed-kind permutation tests.

**Duplicate alternatives:** in `EdgeConflictCorrectionShould.deduplicatesIdenticalAlternativesFromOverlappingChords`, extend the no-candidate control with endpoints `A=(-120,0)`, `B=(120,0)`, `C=(-80,0)`, `E=(80,0)` and edges A–B and C–E; retain its N/U/D/UL/UR/DL/DR coordinates. Pin all four endpoints actively and leave the seven control nodes unpinned. The eleven-node fixture has no initial gaps and exactly four C2 identities: A–B with C, E and N, and C–E with N. Both chords generate the same six N targets; all six fail A2. Require unchanged positions, that exact residual, zero rounds/repairs/resolved counts, six verifications, `verificationWork=6*2*C(11,2)=660`, and no correction budget flags. Counting twelve attempts would expose failure to deduplicate across generating conflict keys. This checks duplicate traversal through observable work without private candidate-list assertions.

**Prominence:** use A/B as in E2, `N=(0,5)`, `T1=(-100,100)`, `T2=(100,100)` and edges A–B (undirected), N→T1 and N→T2 (arrow at target only). Production prominence must report two outgoing targets and actual N radius `8*1.2=9.6`; the other radii are eight. Initial sole obstruction is N on A–B. Require N's accepted direct target `(0,12.6)` within `1e-9`, one accepted round/verification, unchanged other centers, empty residual, A–B successfully straight, all three expected paths present and the final-path oracle using radius 9.6. This must fail a hardcoded radius-eight displacement, which leaves a new clearance violation even if physical discs no longer intersect.

**Sibling hull veto with a safe alternative:** construct nodes `p=(5,100)`, `q=(5,-100)`, `a=(0,0)`, `c=(-53,0)` with sole undirected edge p–q. Enclosures `HA` and `HC` contain a and c; common parent `HR` contains those two children. All tiers are enforced (`SUBTLE` children, `EMPHATIC` parent), no suppressed/ambiguous hulls; anchors are all `(0,0)`. Use production geometry/radii. Initial sibling hulls do not overlap; each singleton hull has axial extent `8+16=24`. The preferred a target `(-6,0)` clears the edge and gaps but brings sibling centers to separation 47, introducing `hullPair(HA,HC)`, so A3 must veto it. The opposite direct target `(16,0)` introduces no hull pair and must be accepted in two verifications. Assert the bad-target geometry's new pair independently using `BoundaryInvariants`, final a `(16,0)`, unchanged anchors, no new sibling/ancestor keys and empty C2 residual. Removing the A3 veto would choose the wrong first target. Update the old zero-round sibling-veto test around this falsifiable behavior.

**Ancestor containment:** retain and extend the existing `BoundaryInvariantsShould` ancestor coverage and `BoundarySeparationCorrectionShould.ancestorEscapePairKeysReportEscapingChildren`. In `BoundaryInvariantsShould.detectsAncestorEscapeInTheCandidatePairSet`, use enforced parent/child hulls HR/HC, with HR's square `[-10,10]×[-10,10]` and HC's initial square `[-1,1]×[-1,1]`; the candidate HC square is `[19,21]×[-1,1]`, with HR unchanged. `detectPairKeys` must change from empty to exactly `pair(HR,HC)` and `detect` must classify `ANCESTOR_ESCAPE`. This identity is forbidden by A3's same subset comparison as siblings. Additionally run E3 with N as the only direct node of HC and HC the only child of HR, fixed anchors `(0,0)`, and production recomputation: the accepted diagonal must rebuild both hulls, retain containment and add no ancestor identity. Check this variant at the correction/recomputation layer; its edge can still meet an unrelated enclosure hull, so any routed result follows the existing hull-obstacle policy. The straight-path outcome in §6.4 applies to E3's unenclosed fixture. Generated consistent parent hulls expand around their children; do not invent an impossible natural escape in that valid fixture or weaken ancestor checking to get a test to pass. Detector-level escape coverage and correction-level ancestor rebuilding are separate checks.

**Counters/stops:** retain the existing item-, pair-, detected-volume-, verification-detection-, verification-work- and terminal-rebuild-exhaustion probes, all cap reconciliation probes, and counter reset between separate `apply` calls. Add `EdgeConflictCorrectionShould.keepsTheCompleteCrowdedResidualWhenGlobalVerificationCannotFinish`: E1 plus 230 isolated nodes `Xi=(10000+100*i,10000)`, `i=0..229`, no new edges/enclosures/pins. Initial complete C2 count stays nine at 257 nodes. Its first eight-node batch would remove eight conflicts, but its two full A2 scans cannot fit 65,536 units. Require no accepted movement, one verification, exact initial residual, `verificationWork=65_536`, `verificationBudgetExhausted=true`, `detectionTruncated=false`, and no retry/reset. Compare residual with unbounded test-only detection on the published positions. Reaching a selection limit must not masquerade as incomplete detection.

**Mixed batch/retry:** retain the reported quad's exact two-attempt trace and mixed SWAP/TRANSLATION repair records. Add a combined fixture consisting of that quad and E2 translated by `(1000,1000)`, with the quad's C/D endpoints pinned as in the existing mixed-kind test. Require the rejected mixed batch to yield a legal singleton, then allow the remaining C2 opposite-side repair in the new state; verify complete global conflict/gap/hull subsets after each accepted round and the final exact movements of E2. A rejected sibling must not permanently discard the blocked node's untried alternatives. Limit assertions to the prescribed ordering and accepted records, not private candidate-array layouts.

### 7.2 Existing expectations affected by the broader search

Delete/replace the suppression test with E1's bounded progress and the 257-node budget-stop control. Replace normal-only C2 test naming/assertions with nearest-direct/opposite/diagonal behavior. Existing tests such as `vetoesACandidateThatWouldBreakMinGap`, `vetoesACandidateThatAddsASiblingHullViolation` and `rejectsACandidateThatReplacesAPreExistingHullResidualWithAnEqualCountPair` must not keep a blanket `rounds=0, verifications=1` merely because their original single candidate failed. Retain the unsafe proposal as an independently measured counterexample, assert that it is not accepted, and assert the safe alternative selected by the new order, or use the fully blocked control when testing total rejection.

For the existing equal-count hull replacement fixture `(aX,cX,dX)=(0,45,-53)` with vertical edge x=5 and actual radii eight, a's preferred `(-6,0)` replaces the initial A–C hull pair with A–D and must be rejected despite equal counts; `(16,0)` retains A–C and is safe. Pin that accepted opposite target and retained pair to keep the identity-subset test meaningful. Preserve the separate pre-existing-same-pair acceptance test. Mixed-kind fixtures with synthetic radius 14 can now choose the nearer opposite direct target (e.g. N at x=1005 beside x=1000 moves to x=1017, rather than x=983); calculate expected moves from the fixture's actual initial geometry or rebuild the fixture consistently, without changing its SWAP/TRANSLATION coverage. Intentionally poisoned-geometry tests must continue proving that initial detection reuses boundary terminal geometry.

### 7.3 Requirement-to-test map

New method names below are implementation acceptance targets, not claims that these tests exist or have run.

| Requirement | Concrete test/check |
| --- | --- |
| Bounded progress beyond eight; shared round counters | `EdgeConflictCorrectionShould.repairsNineIndependentObstructionsInBoundedBatches` (E1) |
| Both direct sides; rejected preferred is consumed | `EdgeConflictCorrectionShould.repairsOnTheOppositeSideWhenTheNearestSideBreaksTheGap` (E2) |
| Diagonal after both direct vetoes | `EdgeConflictCorrectionShould.repairsTheBlockedColumnWithTheFirstFeasibleDiagonal` (E3) |
| Actual publication uses repaired frame, geometry and paths | `LayoutSettleLoopShould.publishesAStraightClearPathAfterRepairingTheBlockedColumn` |
| Active/dormant pins and finite exhausted family | `EdgeConflictCorrectionShould.keepsThePinnedColumnObstructionAsAnExactResidual`; `repairsTheColumnWithADormantPin`; `keepsAResidualWhenEveryLocalAlternativeBreaksTheGap` |
| Stable ordering, deduplication, finite/degenerate target handling | Extend `isDeterministicAcrossRunsAndPermutedInputs`, `detectConflictsIsAscendingDeduplicatedAndDeterministic`, existing extreme-span tests; `deduplicatesIdenticalAlternativesFromOverlappingChords`; no acceptance/exception from an ineligible C2 target |
| Actual prominence radii | `EdgeConflictCorrectionShould.clearsTheActualProminentNodeDisc` |
| Global pair identities, sibling and ancestor invariants | Updated sibling/equal-count tests; `BoundaryInvariantsShould.detectsAncestorEscapeInTheCandidatePairSet`; `EdgeConflictCorrectionShould.rebuildsAncestorsAfterTheColumnDiagonal` |
| C1 and mixed rejection/retry retained | `rejectsTheTwoAdjacentSwapBatchAndRetriesSingles`, `retriesASingleCandidateAfterARejectedBatch`, `recordsOneRepairPerCandidateForAnAcceptedMixedKindBatch`; new combined quad/E2 control |
| Global verification can stop despite small selection | `keepsTheCompleteCrowdedResidualWhenGlobalVerificationCannotFinish`, retained exhaustion and terminal rebuild tests |
| Paths present, exhaustive same-geometry clearance, fallback observable | Shared final-path assertion invoked by E1–E3, prominence and publication tests; retained `EdgeRouterShould` attachment/fallback/budget/permutation tests |
| Current performance gates and installed plugin | §8.2 commands, pinned fixture digest and §8.3 thresholds |
| Actual installed-window E3 replay; screenshot and measurements from the same displayed state | §8.4 `edgeNodeClearanceApplicationEvidence`, its test-only agent/helper, frame checks and external evidence artifacts |

## 8. Verification commands, performance pins and evidence

### 8.1 Baseline evidence and limitations

The supplied refreshed evidence reports 38 `EdgeConflictCorrectionShould` and 23 `EdgeRouterShould` tests passing with no failures/errors/skips on unchanged code. `EdgeClearanceProbe.java` SHA-256 is `be6bf460d67ac4b8bd9d25c561b73ff9c65100e1643c77f624267a85367f252f`; refreshed `clearance-probe-2.log` digest is `ee2820ad37489bbc49707f333623811ddce21d6f9beb94430e722f5c587c2cb5`. Use `investigation-evidence-2.json`; the earlier manifest mislabeled its probe hash.

The parent subsequently recorded `performance-baseline-evidence.json`: the two targeted A7 tests finished on 2026-10-16 at 05:31:54 UTC with exit 0, two tests and zero failures/errors/skips, against the unchanged code at the docs-only design HEAD. Its log digest is `9b03d4081f3c29932d475e27b2e1db57a3b53c39767fee64149b310334348dad`. An earlier attempt inherited an inaccessible X11 display; the successful rerun explicitly forced headless AWT through an external Gradle init script. These are supplied baseline results, not new executions by the drafter. The new acceptance tests, module suite, implementation performance, smoke checks and real-app evidence are not yet run. The user's original screenshot map has not been replayed.

### 8.2 Commands after implementation

Run from the supplied integration worktree using Java 21.0.8-zulu and `gradle`, with the execution environment's normal escalation when needed. Do not use the wrapper or Maven. Set these shell variables (the existing external init script sets `java.awt.headless=true` on Gradle `Test` JVMs, unlike merely setting the Gradle daemon property):

```bash
export JAVA_HOME=/data/home/henry-arch/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"
EDGE_CLEARANCE_REPORTS=/data/home/henry-arch/.local/state/pi/project-manager/runs/0233ff88d3cc54bbd61550c61fa917d6e2a5717f8a03be4e9aa63dcd47dd1cfa/pm-run-20261016-045155-9d674096/reports
```

First run the new regressions against unchanged production to observe failures for the intended missing behaviors, then the same commands after implementation. Preserve logs; do not count a missing test selection or an environmental failure as a red-phase proof.

```bash
gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.layout.EdgeConflictCorrectionShould' --tests 'org.freeplane.plugin.graph.layout.BoundaryInvariantsShould' --tests 'org.freeplane.plugin.graph.control.LayoutSettleLoopShould' --tests 'org.freeplane.plugin.graph.geometry.EdgeRouterShould' -I "$EDGE_CLEARANCE_REPORTS/performance-headless.init.gradle" -PTestLoggingFull --console=plain --rerun-tasks

gradle :freeplane_plugin_graph:test -I "$EDGE_CLEARANCE_REPORTS/performance-headless.init.gradle" -PTestLoggingFull --console=plain --rerun-tasks

gradle :freeplane_plugin_graph:test --tests 'org.freeplane.plugin.graph.performance.PerformanceTripwiresShould.referenceFixtureStaysInsideTheStrictWorkerAndFirstFrameGates' --tests 'org.freeplane.plugin.graph.performance.PerformanceTripwiresShould.referenceFixtureStaysBoundedAndFlagVisible' --tests 'org.freeplane.plugin.graph.performance.PerformanceTripwiresShould.rejectsAFrameWhoseRoutingExceedsTheCompleteFrameBound' -I "$EDGE_CLEARANCE_REPORTS/performance-headless.init.gradle" -PTestLoggingFull --console=plain --rerun-tasks

gradle :freeplane_plugin_graph:graphPerformanceDiagnostic -PgraphStrictPerformance --console=plain --rerun-tasks

gradle :freeplane_plugin_graph:verifyGraphBundle :freeplane_plugin_graph:graphOsgiSmoke --console=plain

xvfb-run -a -s '-screen 0 1600x1000x24' gradle :freeplane_plugin_graph:freeplaneLaunchSmoke --console=plain
```

`graphPerformanceDiagnostic` and `graphOsgiSmoke` already force headless AWT. The launch smoke needs a reachable X display; use Xvfb as above when no desktop is available, and run it without an inherited JVM option forcing headless mode. Archive exact cwd, source OID/diff, Java version, command, exit code, JUnit XML and performance output. Report environment failures separately, reproduce against the baseline when attribution is uncertain, and do not relax gates to make a run pass.

### 8.3 Performance acceptance

Preserve `src/test/resources/performance/settled-reference.csv` byte-for-byte, SHA-256 `e9e988b7967ea6bb4f503dfb4c5af7d3e74a3328e28e0987e71ff0f0b8ac0578`, its loader/compiler inputs and its 25-node/22-edge nonvacuous workload. It retains one initial crossing, one accepted bow, 21 straights, no dogleg/fallback and no routed residual crossing. All nodes remain pinned to the recorded ledger. The existing `conflictsDetected <= 8` fixture assertion remains a workload property, no longer a global repair-eligibility rule.

Keep 400 warmups / 300 measured samples; edge-conflict p95 ≤25 ms, edge-routing p95 ≤10 ms, full-worker p95/p99 ≤100 ms, accepted-first-frame p95 ≤150 ms and p99 ≤300 ms. Every measured sample must satisfy the existing zero-exhaustion/no-direct-fallback rules; preserve meaningful crossing and bow/dogleg assertions and zero discard counts. A7b retains eight raw reference frames, all counted caps/flags, and the inclusive worker-submit + geometry + routing bound of **5 seconds per complete frame**. The routing-delay test must still reject an injected delay of `RAW_REFERENCE_MAX_FRAME_NANOS + 1`.

### 8.4 Real application evidence

This mandatory gate exercises the actual installed application window with E3's exact raw positions, N unpinned, and real downstream correction and routing. Its test-only replay binding is specified below. It is separate from the ordinary controller/acquisition path, which remains covered by the normal smoke and integration tests. No schematic, mockup, manually relocated node, pre-corrected frame, or standalone `graphUiEvidence` painter establishes this gate. No implementation success is claimed by this draft.

#### 8.4.1 Future files, tasks and launch

Add only these harness sources, relative to `freeplane_plugin_graph/src/test/java/`:

| Source | Responsibility |
| --- | --- |
| `org/freeplane/plugin/graph/smoke/EdgeNodeClearanceApplicationEvidence.java` | JDK-only outer launcher: isolated profile/fixtures, process management, deadlines and artifact/result validation |
| `org/freeplane/plugin/graph/smoke/EdgeNodeClearanceEvidenceAgent.java` | JDK-only `premain(String, Instrumentation)`: readiness/action invocation, installed-loader discovery, helper definition and failure/result transport |
| `org/freeplane/plugin/graph/control/EdgeNodeClearanceReplay.java` | Typed helper and nested classes: exact E3 fixture, replay engine/real-worker adapter, settle-loop/window binding, independent oracle, paint/capture and cleanup |

Extend this module's `build.gradle` with `edgeNodeClearanceEvidenceAgent` (`Jar`) and `edgeNodeClearanceApplicationEvidence` (`JavaExec`). The Jar depends on `testClasses`, has manifest `Premain-Class: org.freeplane.plugin.graph.smoke.EdgeNodeClearanceEvidenceAgent`, and includes only that agent's class files and `EdgeNodeClearanceReplay` plus its nested class files. It contains no production classes, test frameworks, fixture/resource shadows or distribution content. Do not copy it into `BIN`, the graph bundle or OSGi's bundle class path. The JavaExec depends on the Jar and `graphSmokeInstallation`, uses the test runtime classpath only for its outer launcher, passes the absolute agent-Jar path and required `-PedgeClearanceEvidenceDir` directory as arguments, and propagates nonzero results. None of these new tasks exists or has run at drafting time.

After §8.2's checks pass, run from the same worktree with its Java 21 settings:

```bash
xvfb-run -a -s '-screen 0 1600x1000x24' gradle :freeplane_plugin_graph:edgeNodeClearanceApplicationEvidence -PedgeClearanceEvidenceDir="$EDGE_CLEARANCE_REPORTS/installed-e3" --console=plain
```

The outer launcher creates a fresh attempt subdirectory beneath that external directory, with isolated `home`, `config` and fixture files. Never reuse an earlier PASS artifact. It records the actual source OID, working diff digest, Java version, command, and installed graph-bundle/agent hashes. Use `graphSmokeInstallation` to refresh the installed bundles before this launch; do not infer the build revision from `BIN/gitinfo.txt`. On a never-built worktree ensure `BIN` exists and installation actually populated the required core and graph bundles.

Create a compatible `.fpg` and `.mm` fixture using the existing XML formats. Give the workspace/map/nodes stable identities, use the same identities in the typed E3 replay projection, and record the full fixture coordinates and files' hashes. Mark the nine leaf nodes with `<graph_group version="1"/>`; the workspace contains just the A–B relationship, no pins/enclosures, and a viewport showing the fixture. Seed `graph_workspace_recent_workspaces` with the absolute `.fpg` path in the isolated profile. Set `ui_elements_follow_selected_map=false`, `selected_map_follows_active_window=false`, `create_new_map_if_no_maps_are_loaded=false`, `always_load_last_maps=false`, `load_last_maps=false` and `load_last_map=false`. Opening this workspace establishes the real window and presentation; the exact raw positions are supplied by the replay worker below, not by an assumption about GraphStream's initial layout.

Launch the actual `BIN/freeplane.sh` with its `BIN` working directory, the isolated `HOME`/`XDG_CONFIG_HOME`, inherited controlled `DISPLAY`, this Java 21, and `JAVA_OPTS` containing `-Dorg.freeplane.nosplash=true`, `-Dorg.freeplane.main.application.FreeplaneSecurityManager.disable=true` and `-javaagent:<absolute-agent-jar>=<absolute-attempt-directory>`. The existing SecurityManager-disable switch applies only to this disposable evidence process; it permits the external agent's lookup/class definition, private-member access and capture work without changing production policy or normal launches. Pass these as process arguments/environment values with correct quoting; strip inherited options forcing headless AWT. Do not use `-XQuitAction`. Reuse the existing launch smoke's isolation/owned-process patterns, not its immediate-quit invocation. Normal application launches have no agent and retain their existing behavior.

#### 8.4.2 Loading and the existing window

`premain` starts a bounded coordinator thread and returns immediately so startup can proceed. The agent never links against graph/core classes or loads the typed helper through the system classloader. Use `Instrumentation.getAllLoadedClasses()` to locate the application's already loaded `org.freeplane.features.mode.Controller`; on the EDT, reflectively obtain `getCurrentController()`, `getModeController()` and `getAction("OpenGraphWorkspaceAction")`. Once nonnull, require the effective disable property to be `true` and `System.getSecurityManager()` to be null, recording both values, then invoke that action's `actionPerformed(ActionEvent)` exactly once on the EDT. Recheck and record this prerequisite after window discovery immediately before helper definition; fail if startup configuration overrides the flag or installs a manager. A null-manager check during `premain` alone is insufficient because normal startup temporarily removes and later restores the manager before installing the graph action. The seeded recent workspace avoids a chooser. Do not substitute a mock action/controller or repeatedly invoke it while waiting.

On the EDT inspect `Frame.getFrames()` for the showing frame with exact class name `org.freeplane.plugin.graph.window.GraphWorkspaceWindow` and name `graph-workspace-window`. Its actual classloader is authoritative. Through that loader obtain `org.freeplane.plugin.graph.control.LayoutSettleLoop`; require its loader and the window loader to match. The agent reads its own Jar entries as raw bytes, obtains a private lookup for that installed control class using `MethodHandles.privateLookupIn`, and defines `EdgeNodeClearanceReplay` and every nested class into the same control package/loader using `Lookup.defineClass`. Invoke these Java 9+ APIs reflectively so the test sources retain the Java 8 compilation target; this evidence runner explicitly requires Java 21. Define all helper dependencies before invoking its JDK-signature entry point, `run(Object window, Path attemptDirectory)`. Never resolve the helper with the system loader, add duplicate production plugin classes to the application classpath, transform production bytecode or replace final production fields. A duplicate/wrong loader, inaccessible seam or missing class is a reported failure, not a fallback to a standalone window.

Record the installed window/control/worker/router loaders and their bundle/code-source identities in the numeric artifact, and verify helper production references resolve to the installed bundle's classes. Reflective access here is confined to the disposable evidence process, with the verified absent SecurityManager and existing unnamed-module classes. The real launcher otherwise installs a SecurityManager whose external-code permissions do not allow the required private lookup/access and class definition; unnamed modules alone do not satisfy that policy. No production export, public API, core code or mutable production factory is added.

#### 8.4.3 Replay worker and actual view binding

The helper obtains the real window's private `model`, then its `canvasRegistration`, `canvas`, `binding` and `currentState` through reflection. Close the original canvas registration on the EDT. From the coordinator thread wait for an EDT fence after unregistering, draining pre-existing queued deliveries before attaching the replay listener. Keep the existing window, model, canvas, presentation and painter; do not replace them with mocks. The isolated controller may continue doing ordinary background work, but its unregistered canvas listener cannot overwrite the replay view; any later observed overwrite fails the state checks below.

Build §6.4's exact E3 `GraphProjection`, centers and empty active-pin set using the fixture's workspace/map/node identities. Create the **real installed `LayoutWorker`** with its existing `Supplier<LayoutEngine>` constructor, a deterministic raw-position replay engine and the normal idle policy. Implement the same `FrameStepper` adapter as §6.4, delegating to that worker; reset closes/recreates the worker with the same raw replay factory. Construct the installed `LayoutSettleLoop` via its package-private injectable constructor, real Swing-backed `EdtExecutor`, and normal geometry/metrics collaborators. Submit an `AcceptedBatch` for that workspace with a recorded generation and this exact projection. The raw engine supplies the unchanged column; correction, hull construction and edge routing occur through the installed worker and settle loop. Never move N in the harness or fabricate a corrected `LayoutFrame`/`CanvasState`.

In the loop's **first actual `CanvasStateListener` callback**, on the EDT, call that same loop's `pause()` synchronously before returning, retain the exact state reference, and invoke the real model's `acceptCanvasState(CanvasState)` with that state. `pause()` sets the paused flag during publication; `finishPublication` must therefore schedule no next step. Keep the loop paused until capture and cleanup. Verify non-FAILED status, the expected generation/projection, E3 movement `N=(-25,-11)`, first-frame repair diagnostics/work counts, and §6.1's independent exhaustive final-path oracle. Capture callback exceptions into an exceptional future/result consumed off the EDT; the loop catches listener exceptions, so an assertion thrown only inside the callback is insufficient. A callback result alone is not painting evidence.

#### 8.4.4 Paint, screen capture and retained-frame checks

Use timed EDT requests from the coordinator thread; never block the EDT waiting on worker completion or `Robot.waitForIdle`. After first publication, drain queued EDT work, validate/layout the actual showing window and call the real canvas's `fitGraph()`. Wait through `runWhenViewportSettled` so deferred scroll clamps/fit work finish. Require the fixture's whole drawing to be within the visible graph region. On the EDT require **both** the model's `currentState` and the real canvas's package-private `canvasState()` (read reflectively) to be the retained published object. Record its generation and a deterministic digest of sorted node centers/radii, edge keys, classifications and every path sample, using exact double-bit encodings. No generation-only equality is sufficient.

Paint the actual visible window region synchronously on the EDT using the root pane/canvas `paintImmediately`, drain pending Swing repaint work and call `Toolkit.sync()`. Record window and visible-canvas screen bounds, viewport/zoom, and the drawing's screen bounds. From a separate capture thread use a real `Robot`, `waitForIdle()` and `createScreenCapture(windowBounds)` on the controlled display. Bound that capture thread by the attempt deadline; an unresponsive Robot never leaves the outer launcher waiting indefinitely. Repeat timed EDT state-reference, generation, digest, bounds and viewport checks immediately after capture. Fail and discard the attempted PASS screenshot if any differs. Recompute/check the oracle on the retained same-state data; the screenshot and all numbers must refer to this first repaired frame. Do not call a painter into a `BufferedImage` as the screenshot source. Inspect the captured PNG to confirm the complete straight edge and displaced N are visible and unobscured; image capture succeeding by itself is insufficient.

#### 8.4.5 Artifacts, deadlines and cleanup

Write external per-attempt `app.log`, the raw fixture files, `e3-window.png`, `e3-state.json` and `result.json`. The state JSON records fixture seed/identities/raw centers and pins, workspace/generation, effective disable-property and absent-SecurityManager checks at action/helper readiness, installed class/bundle identities, retained-reference checks before/after capture, exact corrected centers and actual radii, complete expected/actual edge coverage, path samples/classifications, correction/router counters and flags, independent per-segment/per-disc minimum clearance, digest, window/visible-canvas/drawing bounds, viewport/zoom and screenshot SHA-256. Include oracle tolerance and verify all §6.4 success assertions; in particular `rounds=1`, `verifications=3`, `verificationWork=252`, no fallback/exhaustion, and minimum clearance at least `3.0-1e-9`. The result JSON binds the state and image hashes to the command/build/attempt and reports stage, PASS/FAIL, error details and cleanup outcome. Atomically publish a PASS result only after successful capture checks and cleanup; missing, stale, malformed, mismatched-hash or FAIL artifacts cause the JavaExec to exit nonzero.

Set an overall 180-second evidence deadline from app-process launch, excluding the preceding Gradle build, with startup/action readiness at most 60 seconds, window discovery at most 30, first publication at most five, and all other waits bounded by the remaining deadline. Reserve a separate ten-second cleanup allowance. Timed EDT requests must use posted tasks/futures with bounded waits rather than unbounded `invokeAndWait`; capture failure is transported even when the application swallows a listener exception. Timeouts, missing states/coverage, bad clearance, changed frames/viewports, wrong loaders, agent/reflection errors or uncaught asynchronous errors fail the attempt and retain diagnostic logs. These harness deadlines do not alter A7 timings.

In `finally`, call the replay loop's existing `close()` off the EDT/lifecycle thread, on a harness cleanup executor, and await that submitted task with the remaining cleanup deadline. This return establishes the loop's private physical-close completion and its adapter's real-worker close; there is no new/public close-future API. The harness's timed wait surrounds the potentially blocking close call. Close the actual graph window via its existing `close()` on the EDT. Reflectively obtain the model's `applicationController`, unwrap the installed `GraphModeExtension.ForwardingGraphWorkspaceController`'s `delegate`, require the real `DefaultGraphWorkspaceController`, and call its existing `shutdown()` off the EDT on the bounded cleanup executor. Retain the underlying task failures and verify window disposal. The agent can request `System.exit(0)` only after successful harness cleanup/result staging; its outer launcher then checks actual child exit and atomically finalizes the PASS artifact. Record launched process handles/PIDs and a unique attempt environment marker. If graceful cleanup fails, terminate only that attempt's application descendants using those recorded identities, wait within the cleanup allowance, and report FAIL; a remaining owned process also fails. The `xvfb-run` wrapper owns its fresh display and removes it on exit. Never kill or modify the user's application/profile/display. Preserve the ordinary native connector, arrowhead, pin and enclosure smoke checks in addition to this replay evidence.

