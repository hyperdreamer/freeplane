# Graph Workspace Boundary Separation Design

- **Date:** 2026-09-12
- **Status:** Revision 8 — after design review attempts 1–7; **zero blockers at attempt 8**
- **Scope:** Enforce non-overlap of non-nested boundary hulls (and verify ancestor containment) on every published layout frame, with pin-aware exact correction and explicit conflicts.
- **PM run:** `pm-run-20260912-211532-f0569035` (topic `graph-boundary-separation`)
- **Integration worktree:** `…/worktrees/0233ff88…/pm-run-20260912-211532-f0569035/integration`
- **UI impact:** none — no mockups required.

---

## 1. Background & Problem Statement

### 1.1 The rule

In Graph Workspace, boundary hulls (enclosures) reflect mind-map branch containment:

- A **child** boundary must be **contained** inside its parent boundary (ancestor pairs overlap by definition).
- **Non-nested** boundaries (siblings, cousins, cross-map pairs) must **never** have intersecting interiors. Touching is allowed; one non-nested boundary containing another is also a violation.

This is the boundary counterpart of the already-hard "nodes never overlap" invariant.

### 1.2 What production enforces today

1. **Force-side prevention only, approximate.** `TypedSpringBox.addBoundaryRepulsion` repels every non-ancestor anchor pair using static bounding-circle radii computed from label text estimates (`boundaryRadius = 0.5·hypot(width, height)`), penetration = `r₁ + r₂ + SIBLING_GAP(8)`, force = `0.5·penetration`.
2. **Exact correction only for cross-map roots.** `MapTierCorrection` runs `HullIntersection.minimumSeparatingTranslation` on the *computed* hulls of map-root pairs only, translating whole maps rigidly (all nodes and anchors) with per-map pin rigidity and `LayoutConflict` records.
3. **The exact predicate is production-dead.** `HullIntersection.siblingOverlap` (SAT test; containment and contact count as *not* overlap) is called only from tests.
4. **No post-geometry check.** `LayoutWorker.accept` computes the real hulls and `LayoutSettleLoop` recomputes them for publication; nothing verifies or repairs sibling intersections on the geometry that is painted.

A force equilibrium cannot guarantee a geometric invariant. The result is a persistent, user-visible crossing.

### 1.3 Reproduced defect (real data)

Probe: `/tmp/pm-probe/RealPipelineOverlapProbe.java`, compiled outside the worktree against the
`:freeplane_plugin_graph` test classpath. It parses the real `math.fpg` with the production
`WorkspaceXmlCodec`, loads the real `Axiomatic_Set_Theory.mm` through `MapSnapshotFactory` +
headless Freeplane, marks the five real `graph_group` nodes, projects with the production
`ProjectionEngine`, settles the production layout engine for 1500 steps with the real pins, and
computes hulls with the production metrics.

Settled state (world coordinates):

| Node | Boundary | Position | Inflated radius | Pin |
| --- | --- | --- | --- | --- |
| `Fundation / Regularity` (`ID_1133378501`) | Axioms | (-173.3, 1.8) | ~9.6 | — |
| `Replacement Scheme` (`ID_822182441`) | Axioms | (248.6, -59.3) | ~8.0 | — |
| `Axiom of Choice` (`ID_130337169`) | Axioms | (-24.8, -34.9) | ~8.0 | pinned |
| `Theorem` (`ID_1901523076`) | Basic Definitions and Theorems | (-209.3, 9.8) | ~8.0 | pinned |
| `Theorem` (`ID_1387156674`) | Basic Definitions and Theorems | (-241.8, 14.4) | ~8.0 | — |

Hull bounds: Axioms x∈[-198.9, 272.6], y∈[-83.3, 27.4]; Basic Definitions x∈[-265.8, -185.3], y∈[-14.2, 38.4].

```
hull(Axioms) <-> hull(Basic Definitions and Theorems)
  relation=SIBLING  siblingOverlap=true  aInB=false  bInA=false  mst=(-13.5, 0.0)   <<< SIBLING OVERLAP
```

All other pairs are ancestor pairs and properly contained. **The two sibling hulls cross by ~13.5 world units and stay crossed.**

Which edges cross (verified against `GraphGeometryEngine`):

- Axioms' facing edge (min x = -198.9) is defined by the **unpinned** `Regularity`: `-173.3 − 9.6 − HULL_CLEARANCE(16) = -198.9`.
- Basic Definitions' facing edge (max x = -185.3) is defined by the **pinned** `Theorem`: `-209.3 + 8 + 16 = -185.3`.
- Required clearance between the two node sets is ≈ 49.6 units (25.6 + 24.0); actual node distance is ~36.

Mechanism: node-level separation is satisfied, but hull clearance is not part of any contract, the anchor-based force uses circular estimates unrelated to the facing hull edges, and no post-geometry check exists.

Full evidence: `$STATE_ROOT/reports/boundary-overlap-diagnosis.md`.

### 1.4 Consequence

A hard, user-visible invariant is violated persistently, and any future change to labels, pins, prominence, or layouts can produce further crossings with no detection. The invariant must be enforced on the geometry that is published.

---

## 2. Goals & Non-Goals

### 2.1 Goals

1. After layout correction, **every published non-failed frame** satisfies the boundary invariants in §3, or every unresolved violation pair is covered by an explicit per-pair conflict record (I6).
2. Reuse and generalize the existing exact machinery (`HullIntersection` MST, pin rigidity, conflict records) instead of adding a parallel path.
3. Keep the hard node non-overlap invariant, the node-separation projection, and its residual reporting intact.
4. Preserve deterministic settles and compactness; no global clearance inflation.
5. Preserve the established cross-map semantics: whole maps move rigidly as units when they contain no pin; a map containing any active pin is rigid, so pins never move.
6. Make the correction measurable, so force-side clearance can be added later only on evidence (§7).
7. Remove the cross-map-only special case: one correction component.

### 2.2 Non-Goals

- Changing hull generation or hull shape (the octagonal `HullGeometry` stays).
- Changing label placement or the label leader/clearance work.
- Changing the force model in this iteration (deferred; see §7).
- UI changes of any kind.
- Changing behaviour for suppressed, failed, or fallback frames beyond scoping the invariants to published non-failed frames.

---

## 3. Invariants

Scoped to published, non-failed frames, and to enclosures whose `boundaryTier != SUPPRESSED` (suppressed roots are not painted or hit-tested — `GraphPainter`, `GraphCanvas`, `GraphSearchModel`):

- **I1 — Non-nested separation.** For every pair of enforced enclosures `(A, B)` where neither is an ancestor of the other, `interior(hull(A)) ∩ interior(hull(B)) = ∅` up to the numerical tolerance of §4.2: strict overlaps whose minimum separating translation is zero (shallow crossings within `HullIntersection`'s `1e-9` epsilon) are contact. Boundary contact is allowed: the correction removes violations but does **not** mandate the force-side `SIBLING_GAP(8)`.
- **I2 — Ancestor containment.** For every enforced child `A` with parent `P`, every vertex of `hull(A)` lies inside `hull(P)`. `GraphGeometryEngine.computeHull` builds parent supports from direct child hull vertices plus `HULL_CLEARANCE`, so this holds structurally for computed hulls; it is a **verification** invariant, never a displacement target (§4.3b.9).
- **I3 — Pins.** Every active pinned node's corrected position equals its stored pin position.
- **I4 — Node separation.** The node-separation projection's guarantees hold on the published frame, and its residual is reported on the frame exactly as before.
- **I5 — Determinism.** Identical inputs produce identical corrected positions, conflict lists, round counts, and diagnostic values.
- **I6 — Explicit residuals and bijection.** `hullResidualViolations` counts every detected violation of every kind (non-nested and ancestor). Every residual pair has exactly one matching conflict record naming exactly that pair, and no conflict record exists for a pair that is not a residual. `boundaryCovered()` is computed by the component from the final residual pair set (§4.6). A coverage failure is not publishable: the component signals failure and `LayoutWorker` fails the frame closed **and marks the engine failed** (§4.5).

---

## 4. Architecture

### 4.1 Pipeline position and ownership

`BoundarySeparationCorrection` replaces `MapTierCorrection` in `LayoutWorker.accept` and **owns the whole correction, including every node-separation pass**, so the published positions are exactly the positions whose residuals are reported:

```
raw engine frame
  → BoundarySeparationCorrection.apply(projection, raw.positions, metrics, pins)
        loop: node-separation projection
              → compute hulls (metrics)
              → detect violations
              → deterministic pin-aware displacement plan (or per-pair conflicts)
        terminal: the last loop iteration's separated positions + final detection
  → LayoutFrame.of(..., result.nodeResidualViolations)          [existing contract preserved]
  → LayoutFrame.boundaryDiagnostics(result.diagnostics)          [extended]
  → publish
```

- `LayoutWorker.accept` no longer calls `NodeSeparationProjection` itself; the component returns `nodeResidualViolations` in its result, and the worker propagates it into the existing `LayoutFrame.of(..., residual)` slot (I4).
- The component returns per-stage timings (`separation`, `hull`, `plan`, `apply`) so the performance diagnostic's stage sampling remains intact (§4.7).
- **Failure channel:** the component guards every MST call and wraps any `RuntimeException` it raises — including `IllegalArgumentException` from non-finite translation products — into `BoundarySeparationException` (a **`RuntimeException`**, chaining the original exception as its cause). `LayoutWorker` already catches `RuntimeException` in its submit and step paths (`LayoutWorker.java:253-255` and `:272-274`), marking the engine failed and returning a failed frame, so there is exactly one fail-closed channel and it rebuilds the engine via `restart()`.
- `LayoutSettleLoop` consumes worker frames and recomputes hulls from `frame.positions()` for `CanvasState`, so the publication path inherits the invariant with no second correction path.
- The fallback path (`LayoutWorker.failedFrame`, `LayoutSettleLoop.fail`) is explicitly outside the invariant; failed frames copy the retained `BoundarySeparationDiagnostics` unchanged.

### 4.2 Violation detection

- Enforced enclosures only: skip `BoundaryTier.SUPPRESSED` (matches the previous `MapTierCorrection` skip and the painter).
- Build the enclosure parent map once per frame.
- **Non-nested candidate pairs** are found with a bounding-box sweep (sort by `minX`, sweep): O(H log H + K) instead of an O(H²) scan, for the large-graph budget (§6.4). Duplicate or missing hulls are skipped defensively.
- **Tolerance facts (stated exactly):**
  - `HullIntersection.siblingOverlap` uses an **exact** interval comparison (no epsilon): any positive interior overlap is a crossing.
  - `minimumSeparatingTranslation` returns `(0,0)` when any axis overlap is `≤ epsilon`, and after unscaling that epsilon is effectively an **absolute `1e-9` world units** (the `scalb` factor cancels), not a scale-relative window. Caveat: for hull extents below ~`2^-523`, the `scalb` unscaling itself overflows and every overlap returns `(0,0)`; production hulls are far above that (see the next bullet), so the tolerance claim is bounded to `computeHull`-produced hulls.
  - Production hull support widths are far above that epsilon in every one of the 8 normal directions (non-empty hulls enclose node discs plus clearance — width ≥ ~48; empty hulls are label octagons — width ≥ ~11.3). A zero-MST strict overlap can therefore only be a *shallow crossing*; **containment can never be hidden by the tolerance rule** (attempt-5 verification).
- **Non-nested predicate and violation definition:**
  - `strictlyOverlapping = siblingOverlap(A,B) || containsInclusive(A,B) || containsInclusive(B,A)`, where `containsInclusive` requires every vertex of one hull to be contained (inclusive `HullGeometry.contains`) in the other. This catches crossings, inner containment, and coincident/equal hulls; bare contact without containment is not `strictlyOverlapping`.
  - Compute `t = minimumSeparatingTranslation(A,B)` for every strictly overlapping pair and split three ways:
    1. `t == 0` (overlap within the `1e-9` epsilon) → **contact by tolerance**: not a violation, not a residual, no conflict. `I1`'s "up to the numerical tolerance" is true as written and sub-epsilon rounding cannot fail acceptance or trigger §7 escalation.
    2. `t` non-zero and all components finite → **violation**.
    3. an MST call failure (reachable on extreme coordinates): `minimumSeparatingTranslation` cannot *return* a non-finite `t` because `LayoutPoint.of` rejects non-finite values (`LayoutPoint.java:9-12`) and throws; the component wraps that `RuntimeException` into `BoundarySeparationException` so the fail-closed channel is reachable, uniform, and testable (§4.1). A large crossing is never silently dropped, and `NUMERICAL_TOLERANCE` is not a coverage reason.
- **Ancestor containment** is tested for every enforced child/parent pair and is **not** gated on bounding-box overlap. A child vertex outside the parent hull is an `ANCESTOR_ESCAPE` violation.
- **Determinism / canonical key.** `NodeReference` and `SourceNodeKey` are not `Comparable` and `SourceNodeKey.toString()` omits the persisted id, so the key is built explicitly and collision-free:
  - `escape(s)` = `%`→`%25`, `|`→`%7C`, `:`→`%3A`, `,`→`%2C`;
  - persisted endpoint: `m:<mapId>|p:<escape(nodeId)>`; transient endpoint: `m:<mapId>|t:<escape(i.j.k… structural indices)>`;
  - hull key: the endpoint encodings sorted lexicographically and joined with `,`.
  Pairs are ordered by descending penetration (the MST magnitude `|t|` for every kind, including containment findings), then by the ascending canonical key pair `"A|B"`.
- Violations are processed together in one deterministic round: displacements are accumulated per movable node/anchor (§4.3b.6) and applied once, then hulls are recomputed next round. Ordering therefore affects only conflict recording and diagnostics, never the accumulated result.

### 4.3 Displacement policy

Two policies, chosen by pair kind, because maps and boundaries are different units:

#### (a) Cross-map pairs — rigid map translation (existing semantics preserved)

When the two hulls belong to different maps (root or non-root; enforcement is generalized from roots-only to every enforced cross-map pair), keep the current `MapTierCorrection` behaviour: a map is **rigid iff it contains any active pin**; non-rigid maps are translated wholesale by the MST delta (or take the full delta when the other map is rigid); both rigid → conflict with reason `IMMOVABLE_SIDES` (§4.4). A rigid delta applies to **every node and every anchor of the moved map**, preserving map-unit semantics. Because rigidity follows active pins, whole-map translation never moves a pinned node. Cross-map numeric expectations are re-derived from node-based fixtures rather than the old hand-fed square hulls (§4.7).

#### (b) Same-map pairs — facing-edge, pin-aware node displacement (new)

1. **MST orientation.** `minimumSeparatingTranslation(A, B)` returns the translation **applied to the second hull** (`HullIntersectionShould.returnsTheExactMinimumTranslationAppliedToTheSecondHull`). With finite non-zero `t` and direction `d = t/|t|`: the first hull separates by `−t`, the second by `+t`. Signs are bound to the canonical pair order.
2. **Cap band.** For hull `A` the facing direction is `u = d`; for hull `B` it is `u = −d`. Define `capSet(hull, u, band)` recursively:
   - hull support `S = max(max over direct nodes of (center·u + radius), max over direct child hull vertices of vertex·u) + HULL_CLEARANCE` (empty enclosures: anchor-based support including `BOUNDARY_PADDING` and label bounds, no clearance, per `GraphGeometryEngine`);
   - a direct node is in the cap set iff `center·u + radius + HULL_CLEARANCE ≥ S − band − ε`;
   - a child hull whose vertex support `+ HULL_CLEARANCE ≥ S − band − ε` is in the cap set and contributes its own recursive `capSet(child, u, band)`;
   - an empty contributing hull contributes its **anchor**.
   The maximum-support contributor is always in the set, and the recursion reproduces `computeHull`'s support exactly. ε is a **support-comparison tolerance separate from the MST epsilon**: scale-relative (`1e-9 ×` the largest absolute support magnitude in the comparison), never an absolute world-unit window.
3. **Per-candidate capability (fixes attempt-5 I-1).** A pinned contributor caps a move only when the applied magnitude exceeds its depth below the edge; therefore capability is evaluated **per candidate distribution**, in this deterministic preference order:
   A pinned contributor at depth `δ = S − inflatedSupport` caps a move only when the applied magnitude `a > δ`; a move with `a = δ` ends exactly at the pin (contact, allowed) and `a < δ` stops short of it. Validity for an applied magnitude `a` on a side is therefore: **no pinned contributor in the full band has depth strictly less than `a`**, and the side has at least one unpinned contributor/anchor in `capSet(hull, u, a)`. Ties (`δ == a`) are allowed and the pinned node itself is never displaced (§4.3b.5). Candidates, in deterministic preference order:
   1. **both-sides half** — first `−t/2`, second `+t/2`, each valid at `a = |t|/2`.
   2. **first-only full** — first `−t`, valid at `a = |t|`; second does not move.
   3. **second-only full** — second `+t`, valid at `a = |t|`; first does not move.
   4. **first-side complementary split** — first moves `a_A = d_A`, the smallest pinned depth on the first side (with `0 < d_A < |t|`; valid by the tie rule); second moves `a_B = |t| − d_A`, valid iff no second-side pin has depth strictly less than `a_B`.
   5. **second-side complementary split** — symmetric with `d_B` on the second hull.
   6. otherwise **conflict** (`IMMOVABLE_SIDES`) for this exact pair, positions unchanged.
   The preference is deterministic (the canonical pair order defines "first"). The attempt-5 case (`|t| = 10`, pins at depth 6 on both sides) resolves by candidate 1; the mixed case (`|t| = 10`, a pin at depth 3 on the first side and a pin at depth 9 on the second side) resolves by candidate 4 (`3 + 7`), because a tie move is allowed.
4. **Selection uses the chosen candidate's band.** For each moving side, the displacement set is `capSet(hull, u, a)` with the candidate's applied magnitude `a` (`|t|/2`, `|t|`, or a complementary split band) — pinned-free except for ties, which are removed from the set by the unpinned-only filter in the next item. Any contributor below `S − a − ε` cannot cap the edge after the move, so the facing support moves by exactly `a`.
5. **Application.** The displacement set contains **only unpinned** contributors (nodes) and empty-enclosure anchors; pinned nodes are never displaced (I3), including tied pinned contributors at depth exactly equal to `a`. All selected contributors receive the same vector rigidly, preserving their internal separations.
6. **Accumulation across pairs.** Violations are planned in deterministic order and displacements accumulate per key; cap sets are computed against the raw geometry of the round, so a node displaced for one pair can receive additional displacement from another pair. The bounded loop re-detects and re-corrects all of that.
7. **Convergence, stated precisely.** For a pair planned in isolation, any valid candidate resolves it in **one displacement round**: the relative displacement is the full `t`, and the chosen candidate's band guarantees no pinned contributor caps it. Interruptions come from two directions, both handled by the loop rather than by the claim: (a) per-key accumulation can add another pair's vector to the same contributor, and (b) **the mandatory node-separation pass before the next detection can move nodes again**. The published frame's cleanliness is therefore guaranteed by the bounded loop's terminal coverage, not by the one-round claim. The reproduced case has no such interaction (the Axioms facing node moves away from the pinned `Theorem`) and is expected to publish at `rounds == 1`; the general gate is `rounds ≤ 2` (§7.1).
8. **Anchor consistency.** Same-map only:
   - displacement case: `delta(anchor(e)) = mean of the deltas applied to moved nodes in subtree(e)`; an enclosure with no moved node keeps its anchor;
   - empty-enclosure contributor: its anchor receives the displacement assigned to that empty hull;
   - a later node-separation pass moves nodes only (existing `NodeSeparationProjection` behaviour) and does not update anchors; anchors follow correction displacements exactly as the old per-map `applyDeltas` did.
   Cross-map rigid deltas are handled by §4.3a and are not subject to this mean rule.
9. **Ancestor findings are never displaced.** `ANCESTOR_ESCAPE` is a verification finding only: since `computeHull` structurally guarantees I2, a detected escape records a conflict and leaves positions unchanged rather than applying a separating vector.

### 4.4 Pins and conflicts

- Pinned nodes are never moved (I3), by either policy.
- A conflict records: the unordered hull pair (canonical order), the violation kind (`SIBLING_CROSSING`, `SIBLING_CONTAINMENT`, `ANCESTOR_ESCAPE`), a coverage **reason**, and the blocking pins ordered deterministically by `(mapId, nodeId)` — same-map pairs list all active pins whose nodes lie in either hull's subtree; cross-map pairs list all active pins of either involved map (matching today's `MapTierCorrection.blockingPins`). The pin list is intentionally conservative.
- The conflict type is generalized from `LayoutConflict` (which rejects same-map pairs, `LayoutConflict.java:20-22`) to `BoundaryConflict` over hull keys; map references are derived from the hull keys. Cross-map conflicts keep their blocking-pin meaning.
- **Reasons and precedence** (evaluated per final residual pair):
  1. `STRUCTURAL_ESCAPE` — ancestor findings;
  2. `IMMOVABLE_SIDES` — a non-nested pair for which no displacement policy yields a move: §4.3a both-rigid, or §4.3b.3 with no valid candidate;
  3. `ROUND_LIMIT` — a non-nested pair with a valid candidate that remained after the bound.
- **I6 enforcement and bijection:** the returned conflict list is built **solely from the final detected violation set** after the terminal separation and detection, deduplicated by canonical pair, with exactly one record per residual pair and no record for a pair that is not residual. Capability and reasons are **recomputed on that final set**, so pairs that appear only in the final detection get correct reasons. No concatenation of intermediate plans.

### 4.5 Loop, terminal guarantee, and enforcement

```
rounds = 0
for iteration in 1..MAX_ITERATIONS:               # MAX_ITERATIONS = 4; rounds counts applies only
    positions  = nodeSeparation(positions)        # I4, owned here, pins excluded
    hulls      = computeHulls(projection, positions, metrics)
    violations = detectViolations(hulls)          # §4.2: strict overlap, zero-MST = contact,
                                                  #        MST-call failure wrapped as BoundarySeparationException
    if violations is empty:
        return verified(positions, rounds)                 # residual 0
    plan = planDisplacements(violations, pins)             # §4.3b.3 candidates, per-pair conflicts
    if plan.displacements is empty:
        return covered(positions, conflictsFor(violations))
    positions  = apply(positions, plan.displacements)      # accumulated once
    rounds++
# bound reached: one final separated + measured state
positions  = nodeSeparation(positions)
hulls      = computeHulls(projection, positions, metrics)
violations = detectViolations(hulls)
if violations is empty:
    return verified(positions, rounds)
return covered(positions, conflictsFor(violations, boundExhausted=true))
```

- **`rounds`** = the number of **displacement-applying** rounds. Empty projection → `0`; the reproduced case → `1`; the bound → `4`.
- The returned `positions` are always post-node-separation and are exactly the positions on which the returned residual was detected.
- `verified` means `hullResidualViolations == 0`. `covered` means the I6 bijection holds on the final residual pair set.
- **Fail-closed enforcement:** `LayoutWorker.accept` asserts `verified || covered`; if that ever fails it throws/returns through the failure channel (`failedEngine = true`, failed frame copying the retained diagnostics). `BoundarySeparationException` from the component follows the same path. Recovery rebuilds the engine and component state.
- The result carries `nodeResidualViolations` from the final separation pass; `LayoutWorker.accept` propagates it to `LayoutFrame.of(..., residual)` so `verified()`/residual consumers are unchanged.

### 4.6 Diagnostics

- Keep `LayoutFrame.verified()` unchanged: today it means "kinematics verified" (`residualViolations >= 0`) and is test-visible.
- Add a small immutable `BoundarySeparationDiagnostics` value on the frame:
  - `conflicts` (`BoundaryConflict`);
  - `residualHullPairs` — the canonical key pairs of the final residual violations, so coverage can be evaluated externally and `boundaryCovered()` is computed from this exact set (attempt-5 M-3);
  - `hullViolationsDetected` — violations detected on the **first detection pass of the frame** (after the first node-separation pass), not on the untouched raw engine frame;
  - `hullResidualViolations` — all detected violations (every kind) after the terminal pass;
  - `rounds` — displacement-applying rounds;
  - `displacementRms`, `displacementMax` — rms/max over the union of moved node/anchor keys of the applied displacement vectors (keys absent in a frame count as zero);
  - `appliedDisplacements` — the per-frame map from a canonical key string (`n:<canonical node key>` for nodes, `a:<canonical hull key>` for anchors) to the applied displacement vector, exposed so `LayoutWorker` can compute consecutive-frame deltas;
  - `deltaRms`, `deltaMax` — **worker-side decoration** (the component cannot see the previous frame): rms/max of `D_f − D_{f−1}` over the union of the two frames' moved keys (missing keys zero); `LayoutWorker` tracks the previous displacement field, augments the frame diagnostics, and **resets the field on `LayoutWorker.restart()` and whenever the settle loop replaces the worker on reset (`LayoutSettleLoop`)**;
  - `boundaryVerified()` — `hullResidualViolations == 0`;
  - `boundaryCovered()` — every pair in `residualHullPairs` has exactly one matching conflict and no conflict exists outside that set.
- Node `residualViolations` stays a separate, existing contract on the frame, fed from the component result.
- Diagnostics are test-visible and drive §7; nothing new is painted.

### 4.7 Class changes and call-site migration

- **Replace** `MapTierCorrection` with `BoundarySeparationCorrection`; no delegating wrapper.
  API: `apply(GraphProjection projection, LayoutPositions positions, GeometryTextMetrics metrics, List<PinProjection> pins)` returning `{ positions, diagnostics, nodeResidualViolations, appliedDisplacements, timings }`; throws `BoundarySeparationException` when an MST call fails or an internal invariant is violated (§4.1). The worker decorates the frame with the `deltaRms`/`deltaMax` fields computed from consecutive `appliedDisplacements` maps (§4.6).
- **Delete** `MapTierCorrection.java`; migrate `MapTierCorrectionShould` to `BoundarySeparationCorrectionShould`.
- **Replace** `LayoutConflict` with `BoundaryConflict` (same-map allowed; hull-key based; coverage reason).
- **Update production call sites:** `LayoutWorker` (field, `accept` residual propagation, failure channel, `failedFrame` retained diagnostics), `LayoutSettleLoop` (conflict consumption at ~:760), `LayoutFrame`.
- **Update tests deliberately** (inventory; line references verified):
  - `LayoutWorkerShould`, `LayoutSettleLoopShould`, `GraphUpdateCoordinatorShould`, `GraphWorkspaceCommandAcceptanceShould`;
  - `GraphWorkspacePerformanceDiagnostic` (direct `MapTierCorrection` usage at ~:100/~:360 replaced; `TWO_PINNED_MAPS` at ~:533-553 asserts exactly one conflict with two specific pins — re-derived or shown to still yield exactly one violating cross-map pair under the preserved rigidity rule);
  - the acceptance test asserting a single blocking pin (`GraphWorkspaceCommandAcceptanceShould`, ~:420-424) — re-derived under the generalized pin rule (same-map subtree pins, cross-map all-map pins) or shown unchanged.
- **Performance diagnostic stages:** the component returns per-frame `separation`, `hull`, `plan`, `apply` timings aggregated over the loop iterations; the diagnostic maps `SEPARATION = separation`, `HULL = hull`, `CORRECTION = plan + apply`, so every `PerformanceMeasurements.Stage` keeps its full warmup/measured sample count (`validateScenarioLifecycle`). The aggregation-per-frame semantic change is deliberate and recorded; the diagnostic also reads `hullViolationsDetected`, `hullResidualViolations`, `residualHullPairs`, and `rounds`.
- **Cross-map pin rule unchanged:** a map is rigid iff it contains any active pin; cross-map numeric expectations are re-derived from node-based constructions.
- **Public layout surface:** add `BoundarySeparationCorrection`, its result/diagnostics types, `BoundaryConflict`, and `BoundarySeparationException` to `GraphStreamBoundaryShould.publicLayoutTypes()` (`GraphStreamBoundaryShould.java:84-88`), the GraphStream-freedom signature gate.
- All touched types live in the plugin-internal `org.freeplane.plugin.graph.layout` package; no `freeplane_api`, OSGi bundle export/import, persistence, or schema changes.

---

## 5. Error Handling

| Case | Behavior |
| --- | --- |
| Empty projection / no enforced enclosures | **Hull** no-op (`rounds = 0`, `hullResidualViolations = 0`); node separation still runs and its residual is reported. |
| Suppressed enclosures | Excluded from I1/I2 enforcement (matches the painter); never corrected. |
| Missing or duplicate hull | Pair skipped defensively; never throws on a valid projection. |
| Coincident/equal non-nested hulls | Detected by the inclusive-containment clause; separated by the finite non-zero MST. |
| Strict overlap with zero MST | Contact by numerical tolerance: not a violation, not a residual, no conflict. |
| MST-call failure on extreme coordinates (`LayoutPoint` rejects non-finite products, so the call throws instead of returning) | The component wraps it as `BoundarySeparationException`; `LayoutWorker` marks the engine failed and returns a failed frame; never silently dropped. |
| Cap set empty for a valid candidate | Impossible by construction (the maximum-support contributor is always selected); if it ever occurs, the component throws `BoundarySeparationException` (fail closed). |
| No valid candidate distribution | Per-pair conflict (`IMMOVABLE_SIDES`); positions unchanged; exactly one conflict; never silent. |
| Pinned contributor strictly inside a candidate's applied band (depth `< a`) | That candidate is invalid; a less aggressive candidate may still be valid (per-candidate capability, §4.3b.3); a tie (`depth == a`) is valid, and the pinned node is never displaced. |
| Cross-map both-rigid maps | Per-pair conflict (`IMMOVABLE_SIDES`); positions unchanged; never silent. |
| Round bound reached with a valid candidate | Residual pairs receive `ROUND_LIMIT` conflicts built from the final violation set with reasons recomputed there; §7 escalates if this occurs on the corpus. |
| Coverage assertion fails in `LayoutWorker` | Fail closed (engine failed + failed frame carrying retained diagnostics); never publish an uncovered residual. |
| Ancestor escape detected | Conflict (`STRUCTURAL_ESCAPE`) only; no separating displacement. |
| Empty-enclosure overlap | Anchor-level displacement. |
| Failed engine / fallback frame | Out of invariant scope; retained diagnostics copied unchanged. |

---

## 6. Test Strategy

### 6.1 Unit — `BoundarySeparationCorrectionShould`

- **Falsifiable real-case regression.** A frozen fixture embedding the recorded real projection: 5 nodes with the real labels and real hull keys (real node ids), both pins, the recorded settled positions, and the **same prominence inputs the real projection has** so the computed inflated radii match the recording (Regularity ≈9.6; Replacement/Choice/Theorems ≈8.0; the plan re-runs the probe to print the real prominence map and asserts the radii). Metrics: `new AwtGeometryTextMetrics(new Font("Dialog", Font.PLAIN, 12), new FontRenderContext(null, true, true))`; include the root and ZFC enclosures for the ancestor-containment assertion. The test first asserts the pre-correction violation (`siblingOverlap == true`, `mst ≈ (−13.5, 0)`) and then that the correction separates the pair in **one displacement round** (`rounds == 1`, canonical pair order), leaves both pins exact, and preserves ancestor containment.
- **Predicate completeness.** Coincident and equal non-nested hulls are violations; sibling containment is a violation; bare contact and zero-MST shallow overlaps are contact (no violation, no residual, no conflict); an MST call failure on extreme coordinates (the geometry layer throws `IllegalArgumentException`) is wrapped as `BoundarySeparationException` and takes the fail-closed path (unit-level test with an extreme-coordinate hull pair).
- **Per-candidate capability.** The attempt-5 falsification case (`|t| = 10`, unpinned max contributor plus a pin at depth 6 on both sides) resolves via the both-sides half candidate; the mixed case (`|t| = 10`, a pin at depth 3 on the first side and a pin at depth 9 on the second side) resolves via the first-side complementary split (`3 + 7`, a tie move followed by a strict-clear move); a tie (`depth == applied magnitude`) is valid and the tied pin is not displaced; pinned max contributor forces a one-side full candidate; pinned contributors strictly inside every candidate band → `IMMOVABLE_SIDES`; applied-band selection is pinned-free; nested child-hull cap recursion; empty-enclosure anchors (including `BOUNDARY_PADDING`).
- **Pin policy.** Pins byte-identical (assert every pinned node's coordinates), across all candidate paths and cross-map rigidity.
- **Conflict identity.** On the bound path, exactly one record per residual pair, no stale pairs, reasons recomputed on the final set with the §4.4 precedence; `residualHullPairs` matches the conflict pairs exactly both ways.
- **Same-map conflicts** are representable; **suppressed roots** are not corrected; **empty enclosure** overlap resolves by anchor displacement.
- **Cross-map rigidity** preserved: a map containing a pin anywhere is rigid (pin not moved); whole-map node+anchor deltas; both-rigid conflicts.
- **Determinism.** Repeated runs produce identical positions, conflicts, rounds, and diagnostics; the canonical key is collision-free across persisted and transient endpoint forms.
- **Fast path.** A valid configuration returns positions unchanged with `rounds == 0`.

### 6.2 Pipeline

- A `GraphStreamLayoutFactory` fixture that **provably crosses at settle before the fix**, constructed so a green post-fix state exists: at least one side's facing edge must be defined by an **unpinned** node, and the fixture must admit a specific valid candidate per §4.3b.3 (no pinned contributor with depth strictly less than the candidate's applied magnitude, and at least one unpinned contributor in that band) — an unpinned facing edge alone is not sufficient because a deeper pin can invalidate the applied band. The plan must demonstrate red-before/green-after through the real worker.
- A settle-sequence test publishing frames through `LayoutWorker` to idle, recording §4.6 diagnostics per frame.

### 6.3 Invariants and evidence

- Shared helper `assertBoundaryInvariants`: recomputes all enforced pairs with the **same predicate and tolerance** used by detection and coverage, asserts I1 + I2, asserts I3 for pins, asserts I4 via the frame's node residual, and asserts the I6 bijection both directions from `residualHullPairs` and `conflicts`.
- Numeric equilibrium expectations live in `BoundarySeparationShould` and the repulsion fixtures and must keep their values; `GraphStreamBoundaryShould` is the GraphStream-freedom signature gate (including the new public layout types in `publicLayoutTypes()`), not a numeric fixture.
- Manual acceptance: after a full build, open `math.fpg` in `BIN/freeplane.sh`, inspect the `ZFC → Basic Definitions and Theorems` region, and confirm the sibling hulls no longer cross and the layout reaches idle. Automated tests never read the user's Dropbox paths; all fixtures embed their data.

### 6.4 Performance

- Detection uses the bounding-box sweep (§4.2). `capSet` recursion is bounded by the cap subtree size; candidate evaluation needs at most four distinct hull/band cap-set computations per pair per round (two half-band sets for candidate 1 and two full-band sets for candidates 2–3, reused by the complementary splits); a per-hull full-band cache filtered by band reduces this to two full-band runs plus filtering. The five candidate distributions are evaluated in the deterministic preference order of §4.3b.3. The plan documents the measured cost on the large generated scenario (`REFERENCE_2000_5000`: 2000 nodes / 1200 enclosures, `GeneratedWorkspace`) against the strict `FULL_WORKER` gate (`PerformanceMeasurements.STRICT_FULL_WORKER_NANOS = 100_000_000L`; the normal threshold is 500 ms elsewhere in that class), and records it.
- If the budget is exceeded, the fallback is affected-only hull recomputation with an explicit interface (recompute the moved hulls and their ancestors); the plan must state which interface was chosen and show the measurement.

---

## 7. Measurement Hooks & Clearance Escalation Criterion

All quantities are exposed by `BoundarySeparationDiagnostics` (§4.6), measurable from the frame stream:

1. `rounds ≤ 2` on every corpus frame, except frames covered by `ROUND_LIMIT` conflicts (which must not occur in the corpus). The reproduced case is `rounds == 1`.
2. **Terminal frame cleanliness:** on the idle terminal frame of every settle sequence, `hullResidualViolations == 0`, or every residual pair is conflict-covered with reason `IMMOVABLE_SIDES` in the deliberate pin fixtures. An `ANCESTOR_ESCAPE`/`STRUCTURAL_ESCAPE` residual is a hard **verification defect** against §8.3 (it means `computeHull`'s containment guarantee was violated), not a force-clearance escalation trigger. Zero-MST overlaps are not violations and do not appear. `hullViolationsDetected > 0` on the first detection pass of raw-equilibrium frames is *expected* for force-equilibrium crossings and is not an escalation trigger by itself.
3. **Correction stability, not correction size:** the settle sequence must reach idle under `PerceptualIdlePolicy` within the existing step budgets. The policy constants are `SPIKE_RMS = 0.05` / `SPIKE_MAX = 0.10` (`PerceptualIdlePolicy`); the boundary test's pinned slack is `0.0505` / `0.10` (`BoundarySeparationShould`). The published-frame idle measurement must satisfy the test-pinned slack, and the stability deltas `deltaRms`/`deltaMax` must not exceed the policy constants for longer than the idle policy's consecutive-frame window (8). A stable recurring correction has zero delta and is acceptable.
4. No `ROUND_LIMIT` conflict occurs on the reproduced case or any existing fixture; `verified || covered` never fails; `BoundarySeparationException` never occurs on the corpus.
5. **Cross-map compactness metric (recorded, not gated):** worst accumulated map displacement per frame, so overshoot from generalizing whole-map MST deltas to all cross-map pairs is visible.

**Escalate to targeted force-side clearance only if any of 1–4 fails.** The follow-up adds `HULL_CLEARANCE` only to the non-ancestor boundary repulsion effective radius and re-runs the same measurements. Until then, no force-model change is made. Measurements and the decision are recorded in the plan evidence.

---

## 8. Acceptance Criteria

1. The reproduced case settles with **zero non-ancestor violations** in one displacement round, and both pins are at their stored coordinates; the two sibling hulls no longer cross.
2. Every published non-failed frame satisfies the I6 bijection (`verified || covered`, exactly one conflict per residual pair, none outside); `verified()` and the node residual keep their existing meaning; a coverage failure fails the frame and the engine closed.
3. Ancestor containment is verified across the existing suite and new fixtures.
4. `:freeplane_plugin_graph:test` passes, including `BoundarySeparationCorrectionShould`, the migrated diagnostics/conflict expectations, the `publicLayoutTypes()` update, and determinism tests.
5. Performance tripwires pass with recorded correction overhead against the strict `FULL_WORKER` gate; the settle sequence reaches idle with stable correction deltas; the cross-map compactness metric is recorded.
6. Manual acceptance on `math.fpg` confirms the crossing is gone and the layout reaches idle.
7. `MapTierCorrection` and its cross-map-only special casing no longer exist; one component handles cross-map rigid map translation and same-map facing-edge displacement.

---

## 9. Rejected Alternatives & Risks

| Alternative | Why rejected |
| --- | --- |
| Force-clearance only (Option B) | Cannot guarantee a geometric invariant. Retained as an evidence-gated escalation (§7). |
| Painter-level clamping/clipping | Lies to hit-testing, label placement, and edge attachment. |
| Territory-based layout rewrite | Disproportionate; violates minimal-change. |
| Edge relevance by "within epsilon of maximum support" (revision 1) | Unimplementable: supports include `radius + HULL_CLEARANCE ≥ 24`, and the second hull's facing edge is its minimum support along `d`. |
| Exact-edge-only selection | Leaves stacked unpinned contributors to cap the hull after a partial move. |
| Capability evaluated at a fixed magnitude | Circular (revision 4) or over-conservative (revision 5); replaced by per-candidate capability with the deterministic preference both-half, first-full, second-full, first/second complementary splits, conflict (revision 8 sharpens validity to depth-strict with ties allowed). |
| Treating non-finite MST as tolerance | Silently drops large crossings and defeats the fail-closed guard; non-finite now throws `BoundarySeparationException`. |
| Excluding coincident/equal hulls from the predicate | Publishes intersecting non-nested hulls silently. |
| Publishing a "residual defect" for round-limit residuals | Violates I6. |
| Separating translation for ancestor escapes | Pushes the child further out; verification-only. |
| Whole-hull rigid translation for same-map pairs | Cannot move hulls whose facing edges are pinned; cross-map pairs keep whole-map rigid translation. |

Risks and mitigations:

- **Correction fights the forces:** `deltaRms`/`deltaMax` measure exactly this; §7 escalates on instability.
- **Node separation re-creates crossings after a displacement:** the bounded loop re-detects and re-corrects; §4.3b.7 states the interaction explicitly.
- **Accumulated displacements:** bounded loop + per-pair `ROUND_LIMIT` coverage, with §7 escalation.
- **Diagnostic contract changes:** `verified()` and node residual unchanged; new boundary diagnostics; full call-site inventory in §4.7.
- **Performance on large graphs:** bbox sweep, bounded cap recursion, five candidate distributions over at most four distinct cap-set computations, strict 100 ms gate, measured overhead, affected-only recomputation fallback.
- **Degenerate/duplicate hulls:** tolerance-consistent predicate, non-finite failure channel, defensive skips.

---

## 10. Design-Review Disposition

### 10.1 Attempt 1 (3 critical, 12 important, 7 minor) — resolved in revision 2

Edge-rule implementability, sign binding and convergence, ancestor displacement, stale residual, silent residuals, suppressed enclosures, empty enclosures, bbox-gated ancestor test, migration inventory, measurability, ordering, API/metrics, blocking pins/same-map conflicts, red/green fixture, helper strength, and M1–M7.

### 10.2 Attempt 2 (2 critical, 6 important, 7 minor) — resolved in revision 3

Pinned co-contributor; covered terminal state; cross-map pin rule; unsatisfiable §7 criteria; epsilon guard; convergence statement; performance stages; cross-map anchors; M1–M7.

### 10.3 Attempt 3 (2 critical, 4 important, 5 minor) — resolved in revision 4

Coincident/equal hulls; zero-MST NaN path; stale bound-path conflicts; `rounds` definition; stacked contributors; node residual propagation; M1–M5.

### 10.4 Attempt 4 (2 critical, 2 important, 9 minor) — resolved in revision 5

| Finding | Resolution |
| --- | --- |
| C‑1 circular cap-band capability | Full-band capability before distribution; applied-band selection. |
| C‑2 detection/MST tolerance inconsistency | Violation requires finite non-zero MST; zero-MST overlaps are contact. |
| I‑1 node separation after displacement | Interaction stated in §4.3b.7. |
| I‑2 bound-path reasons | Reasons recomputed on the final set with precedence. |
| M‑1…M‑9 | bijection, pin ordering, finiteness guard (revised in revision 6), `separationPasses` removal, thresholds, empty projection, scenario scale, stage mapping, compactness metric. |

### 10.5 Attempt 5 (1 critical, 1 important, 5 minor) — resolved in revision 6

| Finding | Resolution |
| --- | --- |
| C‑1 non-finite MST silently treated as tolerance; guard unreachable | §4.2 splits zero (contact by tolerance) from non-finite (throw `BoundarySeparationException`); §4.1 defines the failure channel through `LayoutWorker`'s existing `RuntimeException` catch; §4.4 drops `NUMERICAL_TOLERANCE` as a coverage reason; §5/§7/§8 updated. |
| I‑1 full-band pin rule over-conservative with false rationale | §4.3b.3: per-candidate capability with the deterministic preference both-half → first-full → second-full → conflict; a pin caps only moves larger than its depth; §6.1 adds the falsification case. |
| M‑1 §8.1 wording not tolerance-consistent | §8.1 now says "zero non-ancestor **violations**" and defers to the shared predicate. |
| M‑2 one-round claim ignored accumulation | §4.3b.6/7: accumulation and node separation are both named as interactions; the claim is scoped to a pair planned in isolation. |
| M‑3 coverage not evaluable from diagnostics; §4.3i contradicted final-set rule | §4.6 exposes `residualHullPairs`; `boundaryCovered()` is computed from that set; §4.3b.9 defers conflict recording to the final set (§4.4). |
| M‑4 migration expectations vague | §4.7 names the specific performance-diagnostic (`~:533-553`, one conflict/two pins) and acceptance-test (`~:420-424`, single pin) expectations to re-derive or show unchanged. |
| M‑5 `NUMERICAL_TOLERANCE` dead vocabulary | Removed from the reason list; the failure channel is specified. |

### 10.6 Attempt 6 (0 critical, 2 important, 6 minor) — resolved in revision 7

| Finding | Resolution |
| --- | --- |
| I‑1 non-finite failure channel unreachable | §4.1/§4.2/§5/§6.1: `LayoutPoint` enforces finiteness, so the MST call throws instead of returning; the component wraps MST-call `RuntimeException`s as `BoundarySeparationException`, keeping one fail-closed channel and a testable contract. |
| I‑2 missing cross-map reason | §4.4: `IMMOVABLE_SIDES` covers both policies (cross-map both-rigid and same-map no-valid-candidate); §5/§7.2 wording aligned. |
| M‑1 cross-map pin list | §4.4: same-map = subtree pins, cross-map = all active pins of either involved map. |
| M‑2 candidates not exhaustive | §4.3b.3 candidates 4–5 (first/second complementary splits by nearest pin depth). |
| M‑3 cap-set cost | §6.4: four distinct sets worst case, with the caching note. |
| M‑4 delta plumbing | §4.6/§4.7: delta fields are worker-side decoration over the component's per-frame displacement. |
| M‑5 fixture criterion | §6.2: require a specific valid candidate, not merely an unpinned facing edge. |
| M‑6 stale references | cross-references and cited line numbers corrected throughout. |

### 10.7 Attempt 7 (1 critical, 0 important, 5 minor) — resolved in revision 8

| Finding | Resolution |
| --- | --- |
| C‑1 complementary-split candidates dead (a pin at depth equal to the band is a legal tie, not a cap) | §4.3b.3 validity restated as depth-strict (`δ < a` invalid, `δ == a` valid tie); candidates 1–5 share the rule; §4.3b.4/§4.3b.5 list the split bands and the unpinned-only filter; §5/§6.1/§6.2 updated; the mixed `3 + 7` example is now realizable. |
| M‑1 stale candidate enumeration in §9 | Five distributions named; “three candidate evaluations” replaced by four distinct cap-set computations. |
| M‑2 cross-map reason not named in §4.3a/§5 | §4.3a names `IMMOVABLE_SIDES`; §5 gains a cross-map both-rigid row. |
| M‑3 exception base class unstated | §4.1 states `BoundarySeparationException extends RuntimeException` and requires cause chaining. |
| M‑4 per-key displacement field not exposed | §4.6/§4.7 expose `appliedDisplacements`; the worker computes deltas from consecutive maps. |
| M‑5 `,` not escaped in the canonical key | §4.2 escapes `,` → `%2C`. |

---

## 11. Reproduction Evidence Appendix

- Real-pipeline probe: `/tmp/pm-probe/RealPipelineOverlapProbe.java` (outside the worktree; compiled against `:freeplane_plugin_graph` test classpath).
- Full diagnosis report: `$STATE_ROOT/reports/boundary-overlap-diagnosis.md`.
- Frozen fixture data for §6.1 comes from the probe's recorded positions and hull keys; the plan must re-run the probe and embed full-precision doubles.
