# Graph Workspace Node Repulsion & Layout Settling — Design

- Date: 2026-09-11
- Status: Revised after frontier design review (attempt 1)
- Ticket: none — Task Identifier: `2026-09-11-graph-node-repulsion`
- Scope: `freeplane_plugin_graph` (feature branch `feature/graph-workspace`)

## 1. Background

The Graph Workspace lays out projected graph nodes and boundary anchors with a
custom force model built on GraphStream's `SpringBox` (gs-core 1.3). A layout run
is driven by `LayoutSettleLoop`, which steps the worker as long as frames keep
moving, publishes canvas states, and terminates only when `PerceptualIdlePolicy`
reports idle: **8 consecutive frames** with RMS displacement ≤ 0.05 **and** max
displacement ≤ 0.10, measured over the corrected node and anchor positions; the
consecutive counter resets whenever the node/anchor key set changes. All forces
are position-only: each step adds the computed displacement directly to the
particle position (`pos += disp`), with no damping or velocity state.

## 2. Symptoms

Reported: "The repulsion system between nodes doesn't work properly … the nodes
keep moving and won't stabilize when unpinned."

Observed in the running instance (built from worktree HEAD `c9170a4494`,
byte-identical plugin classes):

- The two `freeplane-graph-layout-*` threads plus the EDT burn ~85–99 % of one
  CPU core continuously: the settle loop never terminates.
- The whole graph slides steadily across the canvas (19–66 units per 300 frames
  for the user's graph, ~2 node widths per 5 s), i.e. the layout never reaches
  `OperationalStatus.IDLE`.

## 3. Diagnosis & Evidence

Headless reproduction with the production classes and a projection mirroring the
user's `math.fpg` workspace: one map, enclosures `root(SUPPRESSED)`,
`ZFC(EMPHATIC)`, `Axioms(SUBTLE)` with nodes "Fundation / Regularity",
"Replacement Scheme", "Axiom of Choice", and `Basic Definitions and Theorems
(SUBTLE)` with node "Theorem"; **no relationships, no pins** — exactly the saved
workspace (aside from its stored pin, which is exercised separately).

| Experiment | Residual motion after 1500+ steps |
|---|---|
| Production `LayoutWorker` + current settings | stalls at rms 0.184 / max 0.241; **never idle in 4000 frames** |
| Exact N² repulsion (bypass Barnes–Hut) | identical stall → not an approximation artifact |
| Repulsion off (`K2 = 0`) | converges to rms 0.0012 |
| `K2` = 0.024 / 0.5 / 1 / 2 / 4 / 8 / 16 / 32 | rms 0.001 / 0.019 / 0.037 / 0.060 / 0.088 / 0.129 / **0.184** / 0.255 |
| Half step size (`force = 0.5`) | stalls at ~0.09 — amplitude scales with the step, never decays |
| Pin → unpin (user's exact scenario) | never idle in either state |

The residual motion is a **rigid translation of the whole graph at constant
velocity** (all particles move by nearly the same vector every frame), not a
local jitter. A rigid translation can only be sustained by a **permanent net
force on the system**. Every force term is pairwise and symmetric *except one*:

`TypedNodeParticle.scaleRepulsion` multiplies a particle's **total** native
repulsion (`disp_after − before` of the native pass) by its **own**
`separationRadius / 8`. Hull anchors have `separationRadius` of order 10³
(≈ 180× for the validated fixture's root and `ZFC` hulls) while nodes are 8–14
(factor 1.0–1.75, from `NodeProminence.MAX_SCALE`). For an anchor–node pair the
two sides of the repulsion are no longer equal and opposite, so the system has a
constant net force and drifts forever. The amplitude scales linearly with `K2`
and with the step size, which is why this is a persistent drift and not slow
convergence.

Secondary geometric defect found in the same force model: boundary repulsion
(`TypedSpringBox.addBoundaryRepulsion`) is applied to **all** anchor pairs,
including nested parent/child/grandchild hulls that are supposed to overlap. The
nested fixture's anchors settle ~3800 units apart; ancestor exclusion reduces
the maximum pairwise anchor spread to ~711 (the sibling contact distance
`593.9 + 117.5 + 8 = 719.3`).

## 4. Goals

1. Per-frame displacement of the unpinned layout falls below the idle thresholds
   so `LayoutSettleLoop` terminates, freezes the canvas, and the CPU returns to
   idle. This is "perceptually at rest", not a proof of physical convergence —
   the accepted prominence asymmetry (below) can leave a sub-threshold residual.
2. Nested boundaries stay nested: parent/child/grandchild hull anchors are not
   repelled apart by the boundary repulsion.
3. The fix is minimal and local: idle thresholds, `K2`, typed attraction,
   cross-map budget, pins, projection and geometry code stay unchanged.

## 5. Non-Goals

- Not changing the cross-map displacement budget (≤ 0.005) that currently
  replaces repulsion for cross-map-linked particles (separate defect, no
  relationship present in the user's workspace).
- Not retuning `K2`, `ATTRACTION_FACTOR`, rest lengths, or the idle thresholds.
- Not adding damping/inertia: the root cause is the asymmetric repulsion, not
  the integrator. (See §7.)
- Not making node prominence repulsion fully pairwise-symmetric (bounded
  asymmetry 1.0–1.75; empirically settles — see §10). Documented as an accepted
  residual in §12.

## 6. Design

Three localized changes, all in the package
`org.freeplane.plugin.graph.layout.graphstream`.

### 6.1 Change 1 — anchors no longer scale native repulsion
`TypedNodeParticle.scaleRepulsion` currently scales the accumulated native
repulsion by `separationRadius / baseSeparationRadius` for every particle. New
behavior: **anchor particles (hull centers) skip the scaling**; node particles
keep it (their scale range is bounded, 1.0–1.75, from `NodeProminence`).

- `TypedSpringBox` gains `boolean isAnchorParticle(String id)` (reads the
  existing `anchorFlags` map).
- `scaleRepulsion` scales only when `!typedBox.isAnchorParticle(id)`.

Rationale: anchor–anchor separation is already provided by the **symmetric**
`addBoundaryRepulsion` (it uses the sum of both radii). Scaling the native
repulsion by a hull radius of order 10³ was both redundant and the source of the
net force.

### 6.2 Change 2 — explicit anchor ancestry
`GraphStreamLayoutEngine` carries each anchor's **parent anchor id** into the
particle configuration instead of leaving the box to infer relationships:

- `DesiredParticle` gains a nullable `parentAnchorId`.
- `topology()` builds anchor particles in two phases: first create all anchor
  `DesiredParticle`s and fill the `anchorIds` map; then set
  `parentAnchorId = anchorIds.get(enclosure.parentHull().orElse(null))`.
- `synchronize()` passes the parent id:
  `springBox.configureParticle(id, radius, pinned, anchor, parentAnchorId)`.
- `configureParticle` is already invoked for every particle on every accepted
  request, so a reparented boundary refreshes its parent id without extra
  invalidation logic.

### 6.3 Change 3 — boundary repulsion excludes ancestor pairs
`TypedSpringBox`:

- `configureParticle(..., String parentAnchorId)` records
  `parentOf[id] = parentAnchorId`.
- `forgetParticle` removes the entry.
- `addBoundaryRepulsion` skips any pair where one anchor is an ancestor of the
  other, covering parent–child **and** grandparent–grandchild pairs:
  `isAncestorPair(a, b)` walks `parentOf` from `a` and from `b` with
  `while (current != null) { if (current.equals(other)) return true; current =
  parentOf.get(current); }`, so an absent key or a null parent terminates the
  walk. The skip is applied **before** the zero-distance fallback, so a
  parent/child pair seeded at the same point (single-child boundary,
  `Seeds.center` with `ringRadius = 0`) is excluded rather than resolved by the
  fallback direction.
- Sibling boundaries, different-map roots, and cross-map anchors still repel —
  separation contracts are unchanged. Direct-parent-only exclusion is
  insufficient (measured: spread 2025 with direct-parent-only vs 711 with full
  ancestry).

### 6.4 Unchanged behavior
- `K2 = 16`, `force = 1`, `ATTRACTION_FACTOR`, `BOUNDARY_REPULSION_FACTOR`,
  rest lengths, `PerceptualIdlePolicy.spikeDefaults()` thresholds.
- Typed attraction, pin freezing, seeding, Barnes–Hut repulsion
  (quality/view zone), `MapTierCorrection`, hull geometry, projection.

One coupled effect must be stated: `scaleRepulsion` also captures
`rawBudgetedRepulsion` for cross-map-linked particles, so removing anchor scaling
shrinks that term to the anchor's true native repulsion magnitude (previously
inflated ~180×). The ≤ 0.005 budget cap and its
`capAggregateCrossMapFanOutDisplacementOncePerParticle` contract are preserved;
only the composition of the capped sum changes (cross-map attraction dominates
it in practice).

## 7. Alternatives considered (all measured)

| Approach | Result |
|---|---|
| **Symmetry + nesting exclusion (chosen)** | settles: 457 frames (user graph), 292 after unpin, 419 for a 2-map/24-node/4-cross-map-edge graph; geometry nests; 50 layout + 38 settle-loop tests pass |
| Damping (EMA α = 0.5 / 0.8 / 0.9) | no effect on a steady drift; a DC-gain mistake even amplifies it |
| Recenter layout each frame (remove rigid drift) | settles unpinned (462) but **never settles with a pin** (rms 0.113) and masks the defect |
| Lower `K2` below idle thresholds | amplitude ∝ `K2`, so it only hides the drift; headroom shrinks as graphs grow |
| Fully symmetric pairwise radius weighting for all particles | correct but larger change; deferred per scope |

## 8. Error Handling

No new failure modes. The changes adjust force magnitudes, pair filtering, and
one configuration parameter.

- A missing, absent, or unresolvable parent hull yields a null parent id
  (anchors without a resolvable parent, not only map roots, are null) and
  produces no exclusion; ancestry walks terminate on a null/absent entry.
- Non-finite position guards, coverage validation, and the failed-frame recovery
  path in `LayoutWorker`/`LayoutSettleLoop` are untouched.
- `parentOf` entries are removed in `forgetParticle`, keeping the map bounded by
  the live particle set.

## 9. Test Strategy

All new tests are JUnit 4 `*Should` tests. All fixtures are **exact copies of the
validated projection** (shared test helper: one map, 4 enclosures — suppressed
root "Axiomatic Set Theory", emphatic "ZFC", subtle "Axioms" and "Basic
Definitions and Theorems" — and the 4 nodes "Fundation / Regularity",
"Replacement Scheme", "Axiom of Choice", "Theorem"), because `BoundarySizes`
depends on label texts and on the suppressed map root, and all measured bounds
below were obtained with that fixture. Size formulas used in assertions are
test-local copies, as the existing suites already do.

1. `TypedForcesShould.settleRelationshipFreeNestedBoundaryProjectionToIdle` —
   the exact fixture above (no edges, no pins), driven by `LayoutWorker` with
   `PerceptualIdlePolicy.spikeDefaults()`; assert `frame.idle().idle()` becomes
   true within 2000 steps (measured 457).
2. `TypedForcesShould.nativeRepulsionDisplacementsSumToZeroWithoutDrift` —
   drive the raw `LayoutEngine` (not `LayoutWorker`, which applies
   `MapTierCorrection`) on the same fixture, compute
   `Σ (position_after − position_before)` over all particles between `apply()`
   and `step()`, and assert the vector norm is ≤ 1e-9. The fixture has ≤ 10
   particles (one Barnes–Hut leaf cell → exact pairwise pass), no pins (pins
   freeze one side of every incident force), no edges (all prominence scales are
   exactly 1.0, so no node scaling asymmetry), and no cross-map links; a comment
   records that prominence asymmetry, pins, cross-map budgeting and
   Barnes–Hut aggregation are deliberately outside this assertion.
3. `BoundarySeparationShould.nestedAncestorsSatisfyTheNestingInvariantAfterSettling`
   — after settling the fixture, for every ancestor–descendant anchor pair
   assert `distance(parent, child) + boundaryRadius(child) ≤ boundaryRadius(parent)`
   with a small tolerance, using test-local `BoundarySizes` formulas (for the
   validated fixture the parent radius is ≈ 1441.7 and parent–child distances
   are the hierarchy rest lengths, ~60/100). This encodes Goal 2 directly
   instead of a loose spread bound.
4. `BoundarySeparationShould.siblingAnchorsRemainSeparatedAfterSettling` — after
   settling, assert every sibling-anchor pair still satisfies the existing
   non-overlap contract, and the maximum pairwise anchor spread is ≤ 1000
   (measured 711 = sibling contact distance), so a sibling regression is caught
   separately from the nesting invariant.
5. `BoundarySeparationShould.pinnedFixtureSettlesWithPinPositionUnchanged` —
   same fixture with one node pinned; assert idle within 10000 steps (measured
   2471; the measured first-idle rms 0.0495 sits close to the 0.05 threshold, so
   the bound is deliberately far above the measurement), the pinned node's
   coordinates are exactly the pin values, and **after** first idle continue ~100
   frames asserting the measurement stays under both thresholds and the pin
   position is unchanged (guards against spurious short dips and threshold
   hovering).
6. `BoundarySeparationShould.unpinTransitionSettlesAfterFormerPinReleased` — the
   reported scenario: submit with an active pin, step 300–1500 frames, submit a
   new `LayoutRequest` with empty pins on the same worker, then assert idle
   within 2000 steps (measured 292) and that the formerly pinned node position
   is no longer frozen at the pin coordinates.
7. `BoundarySeparationShould.reparentedBoundaryRefreshesAncestorExclusion` —
   settle the fixture, apply a projection that reparents (or flattens) one
   boundary, and assert the reparented anchor now repels its former sibling
   (behaviour follows the new ancestry; stale exclusions are not retained).
8. `BoundarySeparationShould.grandchildAnchorsAreExcludedFromBoundaryRepulsion`
   — explicit assertion that a grandparent–grandchild pair is not pushed apart
   (the measured decision point: direct-parent-only exclusion leaves spread
   2025).
9. `BoundarySeparationShould.coincidentParentChildAnchorsStayExcluded` — fixture
   with a single-child boundary whose parent and child anchors seed at the same
   point; assert the exclusion applies before the zero-distance fallback and the
   settling result stays finite and idle.
10. `TypedForcesShould.twoMapWorkspaceSettlesToIdle` — the §10 two-map fixture
    (24 nodes, 8 anchors, 4 cross-map edges) asserts idle within 2000 steps
    (measured 419), locking the multi-map / `MapTierCorrection` coexistence.
11. Existing suites must stay green: `TypedForcesShould`,
    `BoundarySeparationShould`, `GraphStreamBoundaryShould`, `LayoutWorkerShould`,
    `PerceptualIdlePolicyShould`, `MapTierCorrectionShould`,
    `LayoutSettleLoopShould` (88 tests currently passing).

## 10. Validation Evidence (headless, production classes + patched copies)

All numbers below come from driving the real `LayoutWorker`/engine with the
worktree's compiled classes; patched candidates were compiled into a shadowing
directory so the target worktree was never modified.

- User graph (no relationships): baseline never idle; fix idle at step 457
  (rms 0.0456), converges monotonically to rms 0.0018 by step 2500.
- Pin → unpin: baseline never idle in either state; fix idle 292 steps after
  unpin.
- Pinned while settling: baseline never idle; fix idle at step 2471 (rms
  0.0495).
- Larger graph (2 maps, 24 nodes, 8 anchors, 4 cross-map edges): baseline never
  idle (rms ~0.24); fix idle at step 419 (rms 0.0019).
- Anchor spread for the nested fixture: 3781 → 711 (nesting restored).
- Existing tests: 50 layout-package tests and 38 `LayoutSettleLoopShould` tests
  pass with the fix enabled.

## 11. Rollout & Verification

1. `gradle :freeplane_plugin_graph:test` — full plugin suite green.
2. Manual: rebuild `BIN`, open `math.fpg`, unpin a node, observe the layout
   freeze; verify CPU returns to idle.
3. Manual spacing check: confirm sibling boundaries and multiple map roots are
   still visually separated after the change — removing the inflated anchor
   repulsion means anchors now separate only by contact-time boundary repulsion
   plus hierarchy springs, so sibling clusters may sit closer than before. If
   crowding is observed, raise `BOUNDARY_REPULSION_FACTOR` (or a minimum
   separation) in a follow-up; non-overlap must hold meanwhile.
4. Commit on `feature/graph-workspace` via the PM delivery gate.

## 12. Known Residuals (accepted, out of scope)

- Node prominence repulsion remains one-sided (factor 1.0–1.75); some graphs may
  settle with a small sub-threshold residual drift rather than a mathematically
  static configuration. Idle still fires, so the loop terminates.
- Cross-map-linked particles still have their repulsion replaced by the
  ≤ 0.005 per-particle budget (separate defect, no relationship in the reported
  workspace).
