# Graph Workspace Node Repulsion & Layout Settling — Design

- Date: 2026-09-11
- Status: Draft for frontier design review
- Ticket: none — Task Identifier: `2026-09-11-graph-node-repulsion`
- Scope: `freeplane_plugin_graph` (feature branch `feature/graph-workspace`)

## 1. Background

The Graph Workspace lays out projected graph nodes and boundary anchors with a
custom force model built on GraphStream's `SpringBox` (gs-core 1.3). A layout run
is driven by `LayoutSettleLoop`, which steps the worker as long as frames keep
moving, publishes canvas states, and terminates only when
`PerceptualIdlePolicy` reports idle (8 consecutive frames with RMS displacement
≤ 0.05 **and** max displacement ≤ 0.10). All forces are position-only: each step
adds the computed displacement directly to the particle position
(`pos += disp`), with no damping or velocity state.

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
user's `math.fpg` workspace (`root(SUPPRESSED) → ZFC(EMPHATIC) →
{Axioms(SUBTLE): 3 nodes, Definitions(SUBTLE): 1 node}`, no relationships —
exactly the saved workspace):

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
repulsion by its **own** `separationRadius / 8`. Hull anchors have
`separationRadius ≈ 1000` (scale factor ≈ 125) while nodes are 8–14 (factor
1.0–1.75). For an anchor–node pair the two sides of the repulsion are no longer
equal and opposite, so the system has a constant net force and drifts forever.
The amplitude scales linearly with `K2` and with the step size, which is why
this is a persistent limit cycle and not slow convergence.

Secondary geometric defect found in the same force model: boundary repulsion
(`TypedSpringBox.addBoundaryRepulsion`) is applied to **all** anchor pairs,
including nested parent/child/grandchild hulls that are supposed to overlap. The
nested fixture's anchors settle ~3800 units apart; ancestor exclusion reduces
that to ~711.

## 4. Goals

1. Unpinned nodes come to rest: the layout converges to a static configuration
   and `LayoutSettleLoop` terminates (`OperationalStatus.IDLE`), so the canvas
   stops repainting and the CPU returns to idle.
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
  asymmetry 1.0–1.75; empirically settles — see §10).

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
repulsion by a hull radius of ~1000 was both redundant and the source of the
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

### 6.3 Change 3 — boundary repulsion excludes ancestor pairs
`TypedSpringBox`:

- `configureParticle(..., String parentAnchorId)` records
  `parentOf[id] = parentAnchorId` (null for nodes and map roots).
- `forgetParticle` removes the entry.
- `addBoundaryRepulsion` skips any pair where one anchor is an ancestor of the
  other (walk the `parentOf` chain from each side), which covers
  parent–child **and** grandparent–grandchild pairs. Direct-parent-only
  exclusion is insufficient (measured: spread 2025 with direct-parent-only vs
  711 with full ancestry).
- Sibling boundaries, different-map roots, and cross-map anchors still repel —
  separation contracts are unchanged.

### 6.4 Unchanged behavior
- `K2 = 16`, `force = 1`, `ATTRACTION_FACTOR`, `BOUNDARY_REPULSION_FACTOR`,
  rest lengths, `PerceptualIdlePolicy.spikeDefaults()` thresholds.
- Typed attraction, cross-map budget logic, pin freezing, seeding, Barnes–Hut
  repulsion (quality/view zone), `MapTierCorrection`, hull geometry.

## 7. Alternatives considered (all measured)

| Approach | Result |
|---|---|
| **Symmetry + nesting exclusion (chosen)** | settles: 457 frames (user graph), 292 after unpin, 419 for a 2-map/24-node/4-cross-map-edge graph; geometry nests; 50 layout + 38 settle-loop tests pass |
| Damping (EMA α = 0.5 / 0.8 / 0.9) | no effect on a steady drift; a DC-gain mistake even amplifies it |
| Recenter layout each frame (remove rigid drift) | settles unpinned (462) but **never settles with a pin** (rms 0.113) and masks the defect |
| Lower `K2` below idle thresholds | amplitude ∝ `K2`, so it only hides the drift; headroom shrinks as graphs grow |
| Fully symmetric pairwise radius weighting for all particles | correct but larger change; deferred per scope |

## 8. Error Handling

No new failure modes. The changes only adjust force magnitudes and pair
filtering; non-finite position guards, coverage validation, and the
failed-frame recovery path in `LayoutWorker`/`LayoutSettleLoop` are untouched.
A missing parent hull (e.g. malformed projection) yields a null parent id and
simply produces no exclusion.

## 9. Test Strategy

New regression tests (JUnit 4, `*Should` naming):

1. `TypedForcesShould` — `settleRelationshipFreeNestedBoundaryProjectionToIdle`:
   build the math-notebook-shaped projection (4 nodes, 3 nested boundaries, no
   edges, no pins) and assert `LayoutWorker` (spike idle policy) reaches
   `idle() == true` within a generous deterministic bound (measured 457 steps;
   assert ≤ 2000).
2. `TypedForcesShould` — `nativeRepulsionDisplacementsSumToZeroWithoutDrift`:
   after one step, the sum of per-particle displacements (rigid momentum) is
   ~0 within tolerance — the direct regression test for the asymmetry defect.
3. `BoundarySeparationShould` — `nestedAncestorsRemainBoundedAfterSettling`:
   after settling the nested fixture, parent–child anchor distances stay below
   the parent hull extent (measured spread 711; assert ≤ 1500).
4. `BoundarySeparationShould` — settle assertion for the pinned fixture: with a
   node pinned at fixed coordinates, the rest reach idle within a generous
   bound (measured 2471; assert ≤ 5000) and the pinned node's position is
   exactly unchanged (contract already covered by
   `pinnedNodesKeepTheirForcedPositions`).
5. Existing suites must stay green: `TypedForcesShould`,
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
   freeze; verify CPU returns to idle; verify boundaries stay nested.
3. Commit on `feature/graph-workspace` via the PM delivery gate.
