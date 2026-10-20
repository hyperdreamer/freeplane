# Graph View — wider initial and preferred node spacing

- Date: 2026-10-16
- Task identifier: `graph-initial-spacing`
- PM run: `pm-run-20261016-125451-a5048653`
- Delivery target: `plugin/graph-workspace`
- Base: `0734fb23db6873f4ec64c3e3e29fc8c3e17edc7a`
- Status: user-approved design, option 1 — Moderate (24-unit preferred gap); independent design review PASS, round 2. Amended at epoch 2 (2026-10-17): the preferred gap applies to enclosure content rings only; relationship rest lengths retain the collision clearance. Rationale and measured receipts in the amendment note below.

## User decision and intended result

The user reports that nodes initially cluster too closely together. They selected
**B: widen initial placement and the settling layout's preferred spacing**. The
layout should open with more space between nodes and continue aiming for that
wider spacing as it settles.

This extends the scope beyond the 2026-10-13 cluster-proximity design's
initialization-only decision for this new node-spacing change. That earlier
work's compact enclosure packing remains the basis of the layout.

## Visual options

![Fixed-zoom comparison of current, moderate, and roomier node spacing](mockups/2026-10-16-graph-initial-spacing/spacing-options.png)

| Variant | Preferred edge gap | Centre distance for two radius-8 nodes | Trade-off |
| --- | --- | --- | --- |
| Current reference | 6 units | 22 units | Crowded starting point |
| **1 — Moderate (recommended)** | **24 units** | **40 units** | Clear increase with a smaller workspace footprint than option 2 |
| 2 — Roomier | 40 units | 56 units | More breathing room and a larger workspace footprint |

Both proposals implement scope B. The illustration uses the existing ring formula
for eight equal radius-8 nodes at the same zoom and draws an indicative group
envelope. It is a design schematic, not an application screenshot or a measured
settled result. The separate two-node rulers show each gap directly.

The user selected **option 1 — Moderate** after choosing scope B. The approved
preferred gap is **24.0 layout units**. Option 2 remains in this comparison only
as an unselected design alternative; it is not a runtime mode.

## Approaches considered

1. **One preferred node gap shared by layout targets (recommended).** Introduce a
   layout-owned constant of 24 units and use it in the existing content
   ring formula. Existing footprints and hierarchy distances derive from those
   values. This gives one parameter with a narrow change surface and retains the
   current layout algorithms. The epoch 2 amendment confines it to the content ring
   because the relationship rest-length formula is coupled to a frozen cross-map
   motion budget; see the amendment below.
2. **Multiply all preferred distances.** A common multiplier also widens the
   graph, but scales distances due to large node radii and enclosure size as well
   as the empty space. Its effect varies more with prominence and nesting.
3. **Add an adjustable density preference.** This permits personal tuning but
   adds UI, persistence, and live-update behavior to a request for a better
   default. A fixed, reviewed default addresses the present request directly.

## Architecture and data flow

Keep the change within `GraphStreamLayoutEngine`'s existing parameterized
geometry, plus the one authorized tolerance correction in
`ClusterPacking.isClear`. The approved constant is `PREFERRED_NODE_GAP = 24.0`.

For an enclosure with at least two direct nodes, retain the existing ring formula:

```
R = (2 * maximumRenderedDiscRadius + PREFERRED_NODE_GAP)
    / (2 * sin(pi / directNodeCount))
```

For zero or one direct node, retain a zero ring radius. With mixed prominence,
the maximum rendered radius continues to give conservative spacing. The same
computed ring radius feeds all existing consumers:

- `Seeds.nodePosition` for new node positions;
- `BoundarySizes.placements` for the content footprint and recursive enclosure
  packing;
- containment spring rest lengths so their targets match the wider ring;
- enclosure endpoint radii when relationship rest lengths are computed.

Relationship springs use `firstEndpointRadius + secondEndpointRadius +
NodeSeparationProjection.MIN_GAP`; their preferred distance is deliberately
unchanged by this feature. `NodeSeparationProjection.MIN_GAP = 6.0` remains the hard
collision clearance and keeps its other production use, so the engine keeps that
import. There is one active preferred-gap parameter and no legacy preference
fallback.

### Epoch 2 amendment: why relationship rests are excluded

Widening relationship rest lengths was measured to break convergence. The
cross-map rest length for two radius-8 nodes would grow from `8+8+6 = 22` to
`8+8+24 = 40`, an 18-unit separation. `TypedSpringBox.CROSS_MAP_DISPLACEMENT_LIMIT`
caps cross-map travel at 0.005 units per affected particle per engine step, so
traversing 18 units needs at least 3600 steps, while
`TypedForcesShould.twoMapWorkspaceSettlesToIdle` requires a non-null idle frame
within 2000 steps. The fixture therefore never reaches idle: 20 000 steps produced
no run of idle frames at all (limit cycle, not slow convergence).

The failure is not specific to 24: gaps of 12, 16 and 20 fail identically, and
isolated mutants show the relationship rest is the dominant trigger while the ring
alone still converges (3455 steps). No tested cross-map cap satisfies both the
frozen cap and the observation bound.

Content rings are not subject to that cap, so the preferred gap is confined to
them. This keeps wide settled clusters — the visible spacing the request is about —
without changing a safety limit. The 2000-step bound was still too tight for the
widened ring on that one cross-map fixture, so it is raised to a measured 6000 as a
test observation bound only; the production idle policy is untouched.

Two further allowances were approved from measurement: a `CANDIDATE_EPSILON`
tolerance in `ClusterPacking.isClear`, which corrects a pre-existing one-ulp
rejection of valid geometry that larger packed items newly expose, and a
residual-tolerant assertion for one `ClusterProximityShould` fixture whose
last-ulp premise the widened ring removes.

## User-visible behavior and scope

Fresh Graph View layouts and **Reset Layout** seed the wider node rings and
targets. Larger content footprints flow into compact enclosure packing, allowing
groups to accommodate their expanded contents. Keep the existing 8-unit packing
gutter between enclosure footprints.

Healthy **Restart Layout** resumes retained particle coordinates with the wider
targets. Failed-layout recovery keeps its existing worker-recreation behavior.
Ordinary projection synchronization preserves retained positions, seeds added
particles using the current deterministic scheme, and rebuilds affected targets
without forcing retained nodes onto a new ring. Active pins retain their exact
coordinates throughout; a pair of pins can force closer spacing than the
preference. Exact fresh-ring gaps are not promised for incremental additions or
healthy restart.

This change adds no setting, menu item, serialization field, or migration. Node
radii, label placement, zoom policy, projection semantics, and the force model
remain governed by their existing mechanisms.

## Settling contract and limitations

The wider value remains in the force targets on every synchronization and reset;
it is not discarded after initial seeding. The final distance is an equilibrium
among containment, relationships, repulsion, hull corrections, and pins. The
preferred gap is not a guarantee that every settled pair has exactly 24 units of
clearance, and the schematic must not be presented as a settled measurement.

The settled acceptance frame is the **first non-failed corrected `LayoutWorker`
frame whose `idle().idle()` is true**, under the unchanged production idle policy
(eight consecutive frames within its RMS and maximum motion thresholds). It
must be reached within **2000 worker steps and 30 seconds per fixture**. Failure,
timeout, or no idle blocks acceptance; the last budget frame can only be reported
as diagnostic evidence. Do not substitute the older cluster probe's endpoint of
first idle plus 200 additional steps.

Compare baseline and candidate on three required fixed unpinned cases: direct
nodes without relationships, a same-map relationship case, and a cross-map
relationship case. Each case has at least one enclosure with two or more direct
nodes. Use identical workspace IDs, projection order, calibration, prominence,
metrics, and zoom in the two versions. Freeze the exact fixtures and baseline
provenance in the technical specification before implementation.

For every node in each qualifying enclosure, compute its nearest-neighbor
rendered disc-edge gap within that enclosure:

```
nearestGap(i) = min(distance(i,j) - renderedRadius(i) - renderedRadius(j))
               over the other direct nodes j in the same enclosure
```

At first idle, the median of those per-node gaps in **every qualifying enclosure
of every required fixture must increase by at least 6.0 layout units**, with a
numerical tolerance of 1e-6. At the fixed acceptance zoom of 1.0 this is a
6-pixel visible-improvement margin. Report each enclosure's minimum as well as
its median; retain the existing collision, residual, pin, and painted-crossing
safety assertions. Measure relationship-endpoint gaps separately and record that
their preferred rest stays at the unchanged collision-based value. Enclosure/anchor
distances alone do not satisfy the node-spacing gate.

Record the next 100 worker frames' node gaps and idle/motion diagnostics as a
separate post-idle experiment. Those frames cannot replace a failed first-idle
comparison or be described as the application's stopped frame. Real-application
fixed-zoom evidence must identify the actual first-IDLE state. If a required case
cannot satisfy the gate with the approved parameter change, revisit the design
before claiming the user's intended outcome has been achieved.

## Error handling and edge cases

The new default is a finite positive internal constant, so it introduces no new
input parsing or failure path. Existing validation and force-failure handling
remain in force. Cover empty, singleton, two-node, mixed-prominence, nested, and
multi-map projections; connected and unconnected nodes; pins; restart; and
retained particles after a topology update. Wider geometry must remain finite
and deterministic.

## Verification strategy

1. Pin the preferred gap with independent expectations for the initial two-node
   and multi-node distances; include mixed prominence and singleton behavior.
2. Verify that containment spring rest lengths use the selected preference,
   including node and enclosure relationship endpoints, and that relationship
   springs keep the unchanged collision-based rest. A change confined to initial
   seeding must fail these tests.
3. Verify that expanded content footprints participate in sibling and top-level
   packing without overlap, while the packing gutter remains 8 units.
4. Execute the first-idle gate above for the required relationship-free,
   same-map, and cross-map fixtures. Assert the declared 6-unit per-enclosure
   median improvement, bounded convergence, and existing safety invariants;
   report relationship-endpoint gaps and the separate 100-frame diagnostic
   window. Do not infer settled behavior from target lengths alone.
5. Re-run the affected layout, boundary, separation, pin, determinism, and canvas
   tests. Preserve existing displacement and safety assertions unless evidence
   requires a separately reviewed change.
6. Capture real-application initial and settled views at the same zoom for the
   delivered change, including a nested/multi-map example and pin behavior.
7. Run the required plugin and project checks before final audit and delivery.

Baseline evidence, before any application changes: the original run of
`BoundaryFootprintShould` and `ClusterPackingShould` had 25 passes and one timeout
at the existing 5-second await. The isolated rerun reproduced it and logged
`Authorization required, but no authorization protocol specified`. Investigation
found that the test JVM inherited an unauthorized X11 display; font measurement
runs on the layout worker and AWT initialization errors can leave the submitted
future incomplete.

The same original two-class command, with only
`JAVA_TOOL_OPTIONS=-Djava.awt.headless=true` added, passed **all 26 tests**, with
200-enclosure submission at **0.565 seconds** against the unchanged 3-second
assertion. The isolated headless test also passed. No production code, test code,
or deadline changed. The baseline is clear in headless mode. Preserve that test
environment for non-UI checks; actual UI evidence requires a valid display.
Original and diagnostic logs are retained in the external PM run's `reports/`:
`baseline-spacing.log`, `baseline-packing-isolated.log`,
`baseline-packing-headless.log`, and `baseline-spacing-headless.log`.

## Acceptance criteria

- The approved visual default is option 1, with a 24-unit preferred edge gap.
- On a fully fresh or reset engine seed frame, adjacent unpinned equal-radius
  direct nodes in every enclosure with at least two direct nodes have a 24-unit
  disc-edge gap within numerical tolerance. Mixed prominence uses the maximum
  rendered radius conservatively; empty and singleton behavior is unchanged.
  Incremental updates and healthy restart verify retained-coordinate continuity
  and updated targets instead of exact fresh-ring gaps.
- Containment target lengths use the selected wider spacing throughout settling and
  after reset. Relationship links keep the unchanged collision-based rest
  `firstEndpointModelRadius + secondEndpointModelRadius +
  NodeSeparationProjection.MIN_GAP`; enclosure endpoint radii may still change
  through the wider content ring.
- The three required unpinned cases pass the first-idle gate: idle within 2000
  steps and 30 seconds, at least 6 units of median nearest-neighbor gap
  improvement in every qualifying enclosure, and existing safety assertions.
  The separate 100-frame post-idle window is reported honestly.
- Compact enclosure packing accommodates the wider contents; safety, pins,
  retained-particle continuity, and finite geometry checks pass.
- Required validation passes in the documented headless environment, including
  the existing 3-second packing assertion; no failed gate is waived or weakened.

---

## Appendix: PM base-drift verification (2026-10-20)

This appendix records read-only verification performed before implementation. It does
not change any approved decision above; the approved body is byte-identical to the
exported design.

### Provenance

| Artifact | Source | SHA-256 |
| --- | --- | --- |
| Approved design (body above) | `/home/henry/Ramdisk/graph-initial-spacing-design/graph-initial-spacing-design.md` | `a3660e516b6d63e3e80764a3b8472a6c8e1f4de854b07fbc151c05b9eedc181e` |
| Superseded epoch-1 design | same export directory | `1b2acc8986582d12826c707342ce6f943e7c9802b5bb506d1940c2113343dad4` |
| `spacing-options.png` | same export directory | `f4cd83f9a61cecf257060480d0a344e01b80fde3767ba06ab211911aa7b0b5db` |
| `spacing-options.measurements.json` | same export directory | `e9e34824be2fa4a1b42e031a5d7f34508b5d57bb9d45dadeb9b3ce49c648615b` |

The mockup generator `render_spacing_options.py` is **not** part of the export; only
the rendered figure and its measurements are. The superseded epoch-1 copy is retained
for comparison under `mockups/2026-10-16-graph-initial-spacing/` and is not an input.

### Repository drift since the design was written

The design declares base `0734fb23db6873f4ec64c3e3e29fc8c3e17edc7a`. The delivery
target now stands at `cb8782fb3742f378b2907198fd2343292415da95`, 16 commits later;
the intervening work is the `2026-10-16-edge-node-clearance` feature. Every code
anchor named by the design was re-read at `cb8782fb` and still matches:

| Design claim | Verified location at `cb8782fb` |
| --- | --- |
| Content ring formula `R = (2·maxR + gap) / (2·sin(π/n))` | `GraphStreamLayoutEngine.java:511-512` (`BoundarySizes.contentRingRadius`) |
| Zero ring radius for `count <= 1` | `GraphStreamLayoutEngine.java:503-506` |
| Ring radius feeds seeding | `GraphStreamLayoutEngine.java:689-690` (`Seeds.nodePosition`) |
| Ring radius feeds containment rest lengths | `GraphStreamLayoutEngine.java:252-253` |
| Ring radius feeds enclosure endpoint radii | `GraphStreamLayoutEngine.java:235-236` |
| Relationship rest = `r₁ + r₂ + MIN_GAP` | `GraphStreamLayoutEngine.java:239-242` |
| `NodeSeparationProjection.MIN_GAP = 6.0` | `NodeSeparationProjection.java:22` (also used at `EdgeConflictCorrection.java:576`, `NodeSeparationProjection.java:78,191`) |
| Packing gutter `SIBLING_GAP = 8.0` | `GraphStreamLayoutEngine.java:483` |
| `ClusterPacking.isClear` exact `gap` comparison (no epsilon) | `ClusterPacking.java:141-151` |
| `TypedSpringBox.CROSS_MAP_DISPLACEMENT_LIMIT = 0.005` | `TypedSpringBox.java:17` (enforced at `TypedNodeParticle.java:100`) |
| `twoMapWorkspaceSettlesToIdle` exists | `TypedForcesShould.java:391` |

### Finding D-1 (must be resolved in the specification)

`TypedForcesShould.twoMapWorkspaceSettlesToIdle` no longer only asserts that a
first-idle frame exists within 2000 steps. It additionally asserts an **exact** step:

```
private static final int TWO_MAP_FIRST_IDLE_STEP = 756;   // line 50
...
assertThat(firstIdleStep).isEqualTo(TWO_MAP_FIRST_IDLE_STEP);   // line 408
```

This predates the design base, so it is not new drift, but the design's epoch-2
amendment only authorizes raising "the 2000-step bound" to a measured 6000. An exact
step equality is a stricter gate than that bound: once the content ring widens, the
first-idle step for this cross-map fixture may change and the equality would fail
independently of the bound.

The specification must therefore choose explicitly between (a) re-measuring and
re-pinning `TWO_MAP_FIRST_IDLE_STEP`, or (b) replacing the equality with a bounded
window. Whichever is chosen must be justified by measurement, reviewed as a
separately visible change, and must not silently weaken the existing convergence
intent. This is a specification decision, not a licence to relax the assertion during
implementation.

### Note on the baseline environment

The design's baseline evidence relies on
`JAVA_TOOL_OPTIONS=-Djava.awt.headless=true` for non-UI checks; the original
non-headless run timed out on an unauthorized X11 display. The specification must pin
that environment for every automated layout gate and keep real-UI evidence separate.

---

## Epoch-2 amendment (2026-10-20): acceptance restated on the seed frame

Decided by the user after independent design review returned REQUEST_CHANGES and a
36-configuration measurement campaign. Approved parameter change is unchanged:
`PREFERRED_NODE_GAP = 24.0` applied to enclosure content rings only. This amendment
supersedes the settled-spacing acceptance gate and resolves the review findings; it does
not supersede the user's visual choice.

### E2-A1 — Why the settled gate is withdrawn

The previous gate required the first-idle nearest-neighbour median to improve by at least
6.0 units in **every** qualifying enclosure of every required fixture. Measurement at the
delivery base shows that is unsatisfiable by ring-only widening, at any ring value.

| Required case | C0 | ring 24 | ring 30 | ring 40 | ring 56 |
| --- | --- | --- | --- | --- | --- |
| Relationship-free | 6.000 | +20.23 | +25.60 | +35.00 | +50.54 |
| Cross-map, enclosures meeting +6.0 | — | 2/6 | 4/6 | 6/6 | 6/6 |
| Same-map native connector | 6.000 | +0.55 | +0.59 | +0.61 | +0.62 |
| First-idle step, cross-map | 756 | 3455 | 3765 | 698 | 2430 |
| Post-idle max displacement, cross-map | 29.69 | 0.99 | 0.93 | 0.97 | 107.95 |

The cause is structural, not numerical: a pair chained by a relationship settles at its
rest length `r₁ + r₂ + MIN_GAP`, which this feature deliberately leaves unchanged, so no
content-ring value can move it. In the same-map case a 32-unit ring increase (24 → 56)
moves the settled median by 0.07 units. An enclosure can also be compressed below its own
ring by *external* connector tension even when none of its own nearest pairs carry an edge:
cross-map `B-sub 0` settles at exactly `MIN_GAP` under ring 24 and ring 30.

Excluding connector-endpoint enclosures does not repair the gate: cross-map `A-sub 2`
(+4.124) and `B-sub 2` (+5.201) contain no connector endpoints and still miss +6.0.
Lowering the threshold would make the claim vacuous. The gate therefore moves to the frame
that the reported problem is actually about.

### E2-A2 — The acceptance gate (primary, seed frame)

On a **fresh or reset seed frame** — the first non-failed published `LayoutWorker` frame
after `submit(...)`, before any settling steps — and for **every qualifying enclosure of
every required fixture**:

```
nearestGap(i) = min over direct nodes j in the same enclosure of
                distance(i, j) - renderedRadius(i) - renderedRadius(j)
```

- qualifying enclosure: an enclosure with at least two direct nodes whose positions are
  present in the frame; relationship and native-connector endpoints are included;
- `renderedRadius` is the production disc radius `NODE_RADIUS * prominenceScale`, 8.0 when
  no prominence entry is present; mixed prominence therefore uses each node's own radius;
- median is the middle value for odd counts and the arithmetic mean of the two middle
  values for even counts;
- gate: the enclosure median must increase by **at least 6.0 layout units** relative to the
  frozen baseline, compared with tolerance 1e-6;
- frozen baseline is C0: ring 6.0 (`MIN_GAP`), `MIN_GAP` 6.0, no `CANDIDATE_EPSILON`, at
  delivery base `cb8782fb`;
- additionally, an enclosure whose seed positions are the unmodified ring (no packing
  displacement of its centre and no pin) must have adjacent equal-radius direct nodes at a
  **24.0 ± 1e-6** gap. The specification must identify, per fixture, which enclosures are
  unmodified rings and which are displaced by packing.

Measured at ring 24, this gate passes with margin: cross-map enclosure seed medians are
17.147, 17.147, 24.000, 24.000, 24.000, 20.574 against a 6.000 baseline (+11.15 to
+18.00); the relationship-free case is 24.000 (+18.00). The same-map seed frame must be
frozen and confirmed by the specification before it is gated; if it does not clear +6.0 the
case is reported, never waived.

### E2-A3 — Settled behaviour is reported, not gated

The first-idle frame remains the observation point, under the unchanged production idle
policy. For each required fixture the specification must record, against the frozen C0
baseline: per-enclosure minimum and median nearest-neighbour gap, the first-idle step, and
the 100-frame post-idle window with rms and maximum motion. Explicitly:

- no +6.0 settled threshold is asserted anywhere, and no document may claim universal
  settled-spacing improvement;
- an enclosure whose settled median equals `MIN_GAP` within 1e-9 is labelled
  **connector-bound** and reported as expected not to improve;
- the settled result is described as an equilibrium among containment, relationships,
  repulsion, hull corrections and pins, in which the ring is only one contributor.

### E2-A4 — One authoritative convergence limit table

This table supersedes the 2000-step and 30-second statements at lines 105, 145 and 244,
which contradicted each other.

| Fixture | First idle within | Wall bound | Measured at ring 24 |
| --- | --- | --- | --- |
| Relationship-free | 6000 steps | 600 s | 42 steps |
| Same-map native connector | 6000 steps | 600 s | 58 steps |
| Cross-map | 6000 steps | 600 s | 3455 steps / 213 s |

6000 steps and 600 s are **test observation bounds**, sized from measurement. They are not
production policy: the eight-consecutive-frame idle policy, its RMS and maximum motion
thresholds, and all safety limits are unchanged. Failure, timeout or no idle still blocks
acceptance, and a budget-exhausted frame may only be reported as diagnostic evidence.

The pre-existing exact pin in `TypedForcesShould.twoMapWorkspaceSettlesToIdle`
(`TWO_MAP_FIRST_IDLE_STEP = 756`, asserted with `isEqualTo`) cannot survive this change: the
same fixture idles at 3455 steps at ring 24. The specification must either re-pin it to a
measured value or replace the equality with a bounded window, justified by measurement and
reviewed as a separately visible change. Relaxing it silently during implementation is
forbidden.

### E2-A5 — The same-map case is a native-connector case

`GraphRelationshipRecord` rejects same-map endpoints
(`source.mapReferenceId().equals(target.mapReferenceId())` throws "Relationships must
connect distinct nodes across maps"). "Same-map relationship" is therefore not
representable and the phrase is removed from this design. The third required case becomes a
**single-map native-connector case**, built with `EdgeContributor.nativeConnector` and
`ConnectorDescriptor` as `EdgeRouterFixture` does.

It is a genuinely distinct force regime: same-map edges are not marked cross-map, so they
bypass `CROSS_MAP_DISPLACEMENT_LIMIT`. Measured topology audit — cross-map fixture 4
cross-map links over 8 of 32 particles; same-map fixture 0 cross-map links over 0 of 32
particles.

The fixture must be proven to reach idle at C0 before it is used as a gate. A 24-node
shape (2 roots × 3 sub-enclosures × 4 nodes) does **not** settle at C0 (no idle within 6000
steps, final-100 maximum displacement 142.8) and must not be used; the divergence is a
property of that fixture shape, not of spacing. A 4-node single-sub-enclosure shape with
two or four native connectors does settle at C0 (steps 84 and 58) and is the verified
starting point.

### E2-A6 — Resolution of the review findings

1. **`ClusterProximityShould` (review major 3).** The earlier "last-ulp roundoff" rationale
   is withdrawn as inaccurate. On the delivery base, fixture D fails under the wider ring
   because two nodes move about 11 units (`y≈0` to `y=11`) in the corrected worker frame,
   not because of roundoff; `LayoutWorker.accept` now applies edge-node correction after
   boundary correction. The specification must state the contract for the corrected frame
   and assert independently checked repair expectations and invariants — node residual, hull,
   pin, finite coordinates, repair budget — rather than adopting a tolerance that would
   swallow an 11-unit move. `CANDIDATE_EPSILON` in `ClusterPacking.isClear` remains, justified
   solely by packing geometry and kept separate from this.
2. **Cross-map displacement argument (review major 4).** The "18 units at 0.005 per step
   therefore at least 3600 steps" argument is withdrawn as unsound.
   `TypedNodeParticle.move` clamps the combined native-repulsion / cross-map-attraction
   component and then restores ordinary displacement; containment, hierarchy, boundary
   repulsion and worker corrections are uncapped, and a separation increase need not be paid
   by either endpoint alone. The relationship-rest exclusion is retained as an **empirical**
   decision supported by pinned receipts: ring 24 with `MIN_GAP` 24 never idles within 6000
   steps, while ring 24 alone idles at 3455; ring 40 with `MIN_GAP` 24 converges at 698, so
   the failure is the combination rather than `MIN_GAP` alone.
3. **Relationship preferred distance (review minor 5).** The statement that relationship
   preferred distances are unchanged is true only of the additive `MIN_GAP` term. Because
   enclosure endpoint radii come from `contentRingRadius`, total rest lengths for
   node–enclosure and enclosure–enclosure relationships **do** change. The complete consumer
   list is: `Seeds.nodePosition`; content footprints and sibling plus top-level packing;
   containment rest lengths; enclosure endpoint radii inside relationship rest lengths;
   hierarchy rest lengths derived from placements; anchor extents and anchor repulsion
   (`TypedSpringBox` 185/188); and worker geometry and correction. Same-map and cross-map
   endpoint types must be tested separately.
4. **Fixtures and metric conventions (review minors 6 and 7).** The specification must freeze
   each required fixture: generator, node/enclosure/edge counts and ordering, nesting,
   endpoint and contributor types, workspace ids, labels and label metrics, calibration,
   prominence, and baseline provenance (C0 at `cb8782fb`). Metric conventions are fixed in
   E2-A2 above. Zoom 1.0 maps one layout unit to one logical component pixel; physical pixel
   size depends on display scaling, so UI receipts must state logical pixels and the display
   scale factor separately, and must identify the actual first-IDLE state.

### E2-A7 — Unchanged

`PREFERRED_NODE_GAP = 24.0`; ring-only application; `NodeSeparationProjection.MIN_GAP` stays
6.0 as the hard collision clearance with its other production uses; the 8-unit packing gutter;
node radii, label placement, zoom policy, projection semantics and the force model; no
setting, menu item, serialization field or migration.
