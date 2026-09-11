# Graph Workspace Node Repulsion & Layout Settling — Design

- Date: 2026-09-11
- Status: Revised after frontier design review rounds 1–3
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

Headless reproduction with the production classes and the **reference fixture**
(used by every measurement and test below): one map, 4 enclosures —
`"Axiomatic Set Theory"` (map root, `SUPPRESSED`), `"ZFC"` (`EMPHATIC`),
`"Axioms"` (`SUBTLE`, nodes `"Fundation / Regularity"`, `"Replacement Scheme"`,
`"Axiom of Choice"`), `"Basic Definitions and Theorems"` (`SUBTLE`, node
`"Theorem"`) — with **no relationship edges and no pins**, mirroring the user's
saved `math.fpg` workspace. `BoundarySizes` depends on these label texts, so the
exact strings are part of the fixture contract.

| Experiment | Residual motion after 1500+ steps |
|---|---|
| Production `LayoutWorker` + current settings | stalls at rms 0.184 / max 0.241; **never idle in 4000 frames** |
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
(≈ 180× for the reference fixture's root and `ZFC` hulls, both ≈ 1441.7) while
nodes are 8–14 (factor 1.0–1.75, from `NodeProminence.MAX_SCALE`). For an
anchor–node pair the two sides of the repulsion are no longer equal and opposite,
so the system has a constant net force and drifts forever. The amplitude scales
linearly with `K2` and with the step size, which is why this is a persistent
drift and not slow convergence.

Measured net momentum (Σ of per-particle position deltas over one step) on the
reference fixture: **0.1725** on the first step (both before and after any fix —
see §9 test 2), **6.55** on the second step unfixed, **9.9e-14** fixed.

Secondary geometric defect found in the same force model: boundary repulsion
(`TypedSpringBox.addBoundaryRepulsion`) is applied to **all** anchor pairs,
including nested parent/child/grandchild hulls that are supposed to overlap.
Measured anchor distances on the reference fixture after exactly 1500 frames:

| Pair | Unfixed | With ancestor exclusion |
|---|---|---|
| root–ZFC (direct parent/child) | 2810.58 | 100.23 |
| ZFC–Axioms (direct parent/child) | 1984.77 | 355.37 |
| ZFC–Definitions (direct parent/child) | 1522.81 | 355.11 |
| root–Axioms (grandparent/grandchild) | 2043.08 | 455.60 |
| Axioms–Definitions (siblings) | 3485.41 | 710.48 (contact ≈ 719.3) |

## 4. Goals

1. **Reported class fixed:** for the unpinned, relationship-free workspace class
   the reported bug belongs to (and for moderate multi-map workspaces), per-frame
   displacement falls below the idle thresholds so `LayoutSettleLoop` terminates,
   freezes the canvas, and the CPU returns to idle. This is "perceptually at
   rest", not a proof of physical convergence; §12 records the measured
   residuals that are deliberately left out of scope.
2. **Nested boundaries stay nested:** direct parent/child hull anchors are not
   repelled apart by the boundary repulsion.
3. **Minimal and local:** idle thresholds, `K2`, typed attraction, cross-map
   budget, pins, projection and geometry code stay unchanged.

## 5. Non-Goals

- Not changing the cross-map displacement budget (≤ 0.005) that currently
  replaces repulsion for cross-map-linked particles (separate defect).
- Not retuning `K2`, `ATTRACTION_FACTOR`, rest lengths, or the idle thresholds.
- Not adding damping/inertia: the root cause is the asymmetric repulsion, not
  the integrator. (See §7.)
- Not removing the node-prominence repulsion weighting: the approved prominence
  design makes layout consume prominence as a per-particle separation size hint,
  and its asynchrony is bounded (1.0–1.75, §12). Anchors have no such contract,
  and their weighting (≈ 180×) is the defect.
- Not addressing Barnes–Hut cell-transition re-excitation in larger graphs
  (separately diagnosed, measured, and recorded in §12).

## 6. Design

Three localized changes, all in the package
`org.freeplane.plugin.graph.layout.graphstream`.

### 6.1 Change 1 — anchors no longer scale native repulsion
`TypedNodeParticle.scaleRepulsion` currently scales the accumulated native
repulsion by `separationRadius / baseSeparationRadius` for every particle. New
behavior: **anchor particles (hull centers) skip the scaling**; node particles
keep it (bounded 1.0–1.75, and consumed by layout as the prominence separation
size hint).

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
  walk. The skip is applied **before** the zero-distance fallback (a
  code-impact note, not independently observable: the fallback only mutates
  local `dx/dy/distance`). A parent/child pair seeded at the same point
  (single-child boundary, `Seeds.center` with `ringRadius = 0`) is therefore
  excluded rather than resolved by the fallback direction.
- Sibling boundaries, different-map roots, and cross-map anchors still repel —
  separation contracts are unchanged. Direct-parent-only exclusion is
  insufficient (measured spread 2025 with direct-parent-only vs 710.5 with full
  ancestry).

### 6.4 Unchanged behavior
- `K2 = 16`, `force = 1`, `ATTRACTION_FACTOR`, `BOUNDARY_REPULSION_FACTOR`,
  rest lengths, `PerceptualIdlePolicy.spikeDefaults()` thresholds.
- Typed attraction, pin freezing, seeding, Barnes–Hut repulsion
  (quality/view zone), `MapTierCorrection`, hull geometry, projection.

One coupled effect must be stated: `scaleRepulsion` also captures
`rawBudgetedRepulsion` for cross-map-linked particles, so removing anchor scaling
shrinks that term to the anchor's true native repulsion magnitude (previously
inflated ≈ 180×). The ≤ 0.005 budget cap and its
`capAggregateCrossMapFanOutDisplacementOncePerParticle` contract are preserved;
only the composition of the capped sum changes (cross-map attraction dominates
it in practice).

## 7. Alternatives considered (all measured)

| Approach | Result |
|---|---|
| **Symmetry for anchors + nesting exclusion (chosen)** | idle 457 (reference fixture), 292 after unpin, 419 (2-map/24-node); geometry nests; 50 layout + 38 settle-loop tests pass |
| Symmetry for **all** particles (`noRadiusScale`) | same results on the reference and 2-map fixtures; drops the documented prominence layout contract; **does not** fix the Barnes–Hut re-excitation (§12) |
| Damping (EMA α = 0.5 / 0.8 / 0.9) | no effect on a steady drift; a DC-gain mistake even amplifies it |
| Recenter layout each frame (remove rigid drift) | settles unpinned (462) but **never settles with a pin** (rms 0.113) and masks the defect |
| Lower `K2` below idle thresholds | amplitude ∝ `K2`, so it only hides the drift; headroom shrinks as graphs grow |
| Disable Barnes–Hut (`viewZone = -1`) | keeps the crowded 17-particle fixture under both thresholds from step 162 (persistent rms 0.0110) with the chosen fix, but changes the performance profile for large projections — out of scope (§12) |

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

All new tests are JUnit 4 `*Should` tests. Unless stated otherwise they use the
**reference fixture of §3** (exact labels, fixed workspace id
`00000000-0000-0000-0000-0000000000aa`), shared through a test helper, because
all measured bounds below belong to exactly that fixture. Tests 2, 3, 4 and 9
are only valid on the relationship-free fixture (all node prominence scales are
exactly 1.0); do not reuse them on fixtures with relationship edges.

1. `TypedForcesShould.settleRelationshipFreeNestedBoundaryProjectionToIdle` —
   reference fixture, no pins, driven by `LayoutWorker` with
   `PerceptualIdlePolicy.spikeDefaults()`; assert `frame.idle().idle()` becomes
   true within 2000 steps (measured 457).
2. `TypedForcesShould.nativeRepulsionDisplacementsSumToZeroWithoutDrift` — drive
   the raw `LayoutEngine` (not `LayoutWorker`, which applies
   `MapTierCorrection`) on the reference fixture and compare the **second frame
   against the first**: `apply(); before = step(); after = step();` then assert
   `‖Σ (position_after − position_before)‖ ≤ 1e-9`. The first interval must not be
   used: `NodeParticle.move` clamps each particle's displacement independently
   to `box.area/2`, which is ≈ 1.414 during seeding, so the first-step sum is
   0.1725 even on fully symmetric code. Measured for the specified interval:
   fixed 9.9e-14, unfixed 6.55 (the next interval is 1.0e-13 fixed / 0.54
   unfixed, §10).
   The fixture has ≤ 10 particles (one Barnes–Hut leaf cell → exact pairwise
   pass), no pins (pins freeze one side of every incident force), no
   relationship edges (all prominence scales exactly 1.0) and no cross-map links;
   hierarchy and containment springs exist but are pairwise symmetric, so they
   are compatible with the assertion. A comment records that pins, cross-map
   budgeting and Barnes–Hut aggregation are deliberately outside this assertion.
3. `BoundarySeparationShould.directParentChildAnchorsSatisfyTheProximityInvariantAfterSettling`
   — step exactly 1500 frames (mirroring the existing `settle(...)` helper
   convention in `BoundarySeparationShould`) and then, for every **direct**
   hierarchy pair, assert `distance(parent, child) ≤ boundaryRadius(parent) − FRAME_CLEARANCE`
   using test-local `BoundarySizes` formulas. Measured after exactly 1500
   steps (fixed): root–ZFC 100.23, ZFC–Axioms 355.37, ZFC–Definitions 355.11;
   bound for root/ZFC is `1441.73 − 16 = 1425.73`. Unfixed: root–ZFC 2810.58 →
   fails. Full
   containment (`d + r_child ≤ r_parent`) is deliberately **not** asserted: for
   wrapper hulls such as the suppressed root, parent and child radius are equal
   by construction (both 1441.73), and the approved prominence design rejects
   strict geometric containment at every depth. Only direct pairs are asserted;
   no guaranteed containment exists for deeper ancestors at settle time (the
   grandparent case is covered by test 8 instead).
4. `BoundarySeparationShould.siblingAnchorsRemainSeparatedAfterSettling` —
   step exactly 1500 frames, then assert every sibling pair still satisfies the
   existing non-overlap contract and the maximum pairwise anchor
   spread is ≤ 1000. Measured: fixed Axioms–Definitions 710.48 (contact
   593.87 + 117.45 + 8 = 719.32), unfixed 3485.41 → fails.
5. `BoundarySeparationShould.pinnedFixtureSettlesWithPinPositionUnchanged` —
   reference fixture with node `"Replacement Scheme"` pinned at `(1059, -145)`;
   assert idle within 10000 steps (measured 2471 at rms 0.049654, i.e. within
   1 % of the 0.05 threshold, hence the deliberately large bound), the pinned
   node's coordinates exactly equal the pin values, and after first idle the
   layout is stepped ~100 further frames asserting the measurement stays under
   `rms ≤ 0.0505` and `max ≤ 0.10` (the small RMS tolerance accommodates the
   measured 0.049654 margin) with the pin position unchanged.
6. `BoundarySeparationShould.unpinTransitionSettlesAfterFormerPinReleased` — the
   reported scenario: submit the reference fixture with `"Replacement Scheme"`
   pinned at `(1059, -145)`, step exactly 1500 frames, submit a new
   `LayoutRequest` with empty pins on the same worker, then assert idle within
   2000 steps (measured 292) and that the formerly pinned node is no longer held
   at the pin: its settled position must differ from `(1059, -145)` by more than
   10 units (measured 122.04).
7. `BoundarySeparationShould.reparentedBoundaryRefreshesAncestorExclusion` —
   step exactly 1500 frames, then reparent `"Basic Definitions and
   Theorems"` from `"ZFC"` to `"Axioms"`, building the request with
   `ProjectionDiff.between(originalProjection, reparentedProjection)` and a
   distinct generation (the honest request description: the fast path is only
   taken for an empty diff whose `beforeGeneration()` matches the last
   synchronized generation, so a real reparent always re-synchronizes; the
   assertion fails if `configureParticle` does not overwrite `parentOf`), then
   step exactly 1500 further frames and assert
   `distance("Axioms", "Basic Definitions and Theorems") ≤
   boundaryRadius("Axioms") − FRAME_CLEARANCE` (bound ≈ 577.9). With the
   refreshed exclusion the pair is a hierarchy pair (rest length 60; measured
   61.8); with stale sibling exclusion it stays near the contact distance
   (measured 699.9) and fails.
8. `BoundarySeparationShould.grandchildAnchorsAreExcludedFromBoundaryRepulsion`
   — step exactly 1500 frames, then assert
   `distance(root, "Axioms") < 1000`. Measured after exactly 1500 steps:
   fixed 455.60; ancestor exclusion removed 2043.08 (direct-parent-only ≈ 2025)
   → fails.
9. `BoundarySeparationShould.coincidentParentChildAnchorsStayExcluded` — the
   single-child boundary case, in which `Seeds.center` gives `ringRadius = 0`
   and the parent/child anchors seed at the same point (this is already the
   root–ZFC configuration of the reference fixture); step exactly 1500 frames
   and assert the same proximity invariant as test 3 (fixed 100.23, unfixed
   2810.58 → fails).
   The exclusion-before-fallback ordering itself is a code-impact note, not an
   observable assertion.
10. `TypedForcesShould.twoMapWorkspaceSettlesToIdle` — the explicitly defined
    two-map fixture: workspace id `00000000-0000-0000-0000-0000000000aa`; map A
    `…0001` with root `"Axiomatic Set Theory"` (`mapRoot`, `SUPPRESSED`) and
    sub-boundaries `"A-sub 0".."A-sub 2"` (`SUBTLE`), each holding 4 nodes
    `a{s}_{n}` labelled `"Boundary {s} node {n}"`; map B `…0002` with root
    `"Topology"` (`mapRoot`, `EMPHATIC`) and sub-boundaries `"B-sub 0".."B-sub 2"`
    (`SUBTLE`), each holding 4 nodes `b{s}_{n}` labelled `"Boundary {s} node {n}"`;
    4 cross-map `GraphRelationshipRecord`s (ids
    `20000000-0000-0000-0000-00000000000{i+1}`, sequence `i+1`, `FORWARD`) from
    `a0_i` to `b0_i` for `i = 0..3`, projected through
    `EdgeContributor.graphRelationship`. 24 nodes, 8 anchors. Assert idle within
    2000 steps (first idle measured at step 419 with rms 0.0468 / max 0.0648;
    converged rms 0.0018 at step 4000) and that the measurement stays under both
    thresholds for the following 100 frames; baseline never idles (rms ≈ 0.24).
11. Existing suites must stay green: `TypedForcesShould`,
    `BoundarySeparationShould`, `GraphStreamBoundaryShould`, `LayoutWorkerShould`,
    `PerceptualIdlePolicyShould`, `MapTierCorrectionShould`,
    `LayoutSettleLoopShould` (88 tests currently passing).

## 10. Validation Evidence (headless, production classes + patched copies)

All numbers come from driving the real `LayoutWorker`/engine with the worktree's
compiled classes; patched candidates were compiled into a shadowing directory so
the target worktree was never modified.

- Reference fixture: baseline never idle; fix first idle at step 457 (rms
  0.0472 / max 0.0939), converging monotonically to rms 0.0018 by step 2500.
- Pin → unpin: baseline never idle in either state; fix idle 292 steps after
  unpin, formerly pinned node 122.04 units from the pin.
- Pinned while settling: baseline never idle; fix idle at step 2471 (rms
  0.049654).
- Two-map fixture (§9 test 10): baseline never idle (rms ~0.24); fix first idle
  at step 419 (rms 0.0468 / max 0.0648), converged rms 0.0018 (0.001751) at step
  4000 and stays idle.
- Momentum: step 1 = 0.1725 (clamp artifact, before and after the fix), step 2
  = 6.55 unfixed vs 9.9e-14 fixed, step 3 = 0.54 unfixed vs 1.0e-13 fixed.
- Settled anchor distances and net momentum: see §3 tables.
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

## 12. Known Residuals (measured, out of scope)

- **Node prominence repulsion weighting** remains one-sided (factor 1.0–1.75)
  because layout consumes prominence as a separation size hint. Its asymmetry is
  an order of magnitude smaller than the anchor defect and does not by itself
  prevent idle on the reference or two-map fixtures.
- **Barnes–Hut aggregation residual in larger graphs.** `SpringBox.setQuality(0.10)`
  sets `viewZone = 2` for every graph, so `repulsionNLogN` always runs and the
  N²/N·log·N branch is not selected by particle count; count only decides whether
  the n-tree root stays a single leaf (`nodesPerCell = 10`), where the recursion
  degenerates to the exact pairwise pass. Beyond that, aggregated barycenter
  terms introduce force discontinuities when cells subdivide. Measured on a
  17-particle crowded same-map-relationship fixture (hub with 14 outgoing
  targets, scale 1.75, single boundary): baseline never idle (rms 0.31); with
  this fix it idles once at step 183 but does not remain under both thresholds
  (max ≈ 0.156); with Barnes–Hut disabled (`viewZone = -1`) the same fix stays
  under both thresholds from step 162 at a persistent rms ≈ 0.0110, and only
  removing the retained node-prominence asymmetry as well (`noRadiusScale`, out
  of scope) reaches rms 0.0000. The residual is therefore a combined effect of
  n-tree aggregation discontinuities and the retained node-prominence weighting,
  not the anchor asymmetry this design removes. The reported relationship-free
  workspace and the 24-node (32-particle) two-map fixture are unaffected (both
  stay idle).
  Addressing it would change the large-projection performance profile and is a
  separate task.
- **Cross-map-linked particles** still have their repulsion replaced by the
  ≤ 0.005 per-particle budget (separate defect).
