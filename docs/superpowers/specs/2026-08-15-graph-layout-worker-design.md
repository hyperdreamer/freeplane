# Graph Layout and Worker Design

**Status:** Approved 2026-08-15

## Scope

This document narrows the existing Graph Workspace design for implementation Tasks 20 and 21. It adds the private GraphStream physics adapter, immutable layout frames, a serialized worker, map-tier correction, and perceptual idle measurement. Label placement, live batching, canvas publication, and performance calibration remain in their assigned later tasks.

## Chosen Approach

Use GraphStream 1.3 only behind the graph plugin's layout boundary. A public, GraphStream-free `LayoutEngine` exposes immutable `LayoutFrame` values. `GraphStreamLayoutFactory` is the sole public construction seam; its implementation classes remain package-private in `layout.graphstream`.

The implementation uses a `MultiGraph`, not a simple graph, so distinct projected edges with the same endpoints remain valid. The layout sink is attached before nodes and edges are populated, ensuring every node and enclosure anchor obtains a particle. No `LayoutRunner` is created.

The alternatives were rejected for the following reasons:

- Exposing GraphStream graph, particle, or spring types would couple projection, control, and canvas code to an implementation dependency.
- A `LayoutRunner` would retain the documented release race and violate single-worker ownership.
- Delaying automatic idle behavior until calibration would leave the Task 21 worker incomplete and prevent the later settling loop from terminating naturally.

## Task 20: Private Physics Adapter

`LayoutRequest` carries the workspace, current projection, stable-key diff, and pins. `GraphStreamLayoutEngine` retains particles for unaffected keys across requests, removes obsolete graph elements, and deterministically seeds new nodes and enclosure anchors from their stable keys. Active pins set an exact particle position and freeze it; dormant pins do not create a particle or force.

The solver represents:

- a particle for every visible projected node;
- an invisible particle for every enclosure hull anchor;
- relationship springs for projected edges;
- weak containment springs from direct nodes to their enclosing anchor;
- weak hierarchy springs between nested enclosure anchors.

`TypedSpringBox` implements the required force classes without using `layout.weight` as stiffness. The calibration starts with containment `0.15` and hierarchy `0.30`; both remain below same-map relationship attraction. Cross-map relationship displacement is accumulated once per particle per step and clamped by vector magnitude to the fixed `0.005` cap. Prominence is used only as a particle-size separation hint. It never changes a pin, exceeds the aggregate cap, or shrinks a node to avoid contact.

Every public value is immutable and GraphStream-free. `LayoutFrame` contains its monotonically increasing step index, immutable `LayoutPositions`, and a failure flag. `apply`, `step`, `reset`, and `close` are the only public engine operations.

## Task 21: Serialized Worker and Correction

`LayoutWorker` owns one single-thread executor. All calls to its engine occur on that executor, so GraphStream mutation and `compute()` cannot overlap. `submit` applies the request; `step` advances the same accepted request. The worker retains the last valid frame and returns it with `failed=true` if the engine raises an exception. `restart` discards the failed engine state and permits a subsequent accepted request to create a clean engine. `close` is idempotent, rejects later work, and leaves no live worker thread.

`MapTierCorrection` is a pure operation over a projection, immutable positions, and hull geometry. It considers only map-root hull pairs in deterministic order. A map is rigid if any active pin belongs to it. For an overlap:

- when both maps are movable, split the minimum separating translation uniformly between their complete map particle sets;
- when exactly one map is rigid, give the complete translation to the movable map;
- when both are rigid, preserve every position and emit an immutable `LayoutConflict` identifying the blocked map pair and active blocking pins.

The correction translates all node particles and enclosure anchors belonging to a moved map by the same vector. It never translates only a root anchor or a subset of a pinned map. It is intentionally a transformation of each returned immutable frame: the Task 20 `LayoutEngine` boundary has no position-writeback operation, so Task 21 does not add one or leak GraphStream types. The later settle loop recomputes geometry from each corrected frame before label placement and publication.

## Idle Policy

Idle status derives from observed displacement rather than GraphStream stabilization, which becomes invalid after map correction. `PerceptualIdlePolicy` compares matching node and anchor positions in consecutive frames. A topology mismatch resets the stable-frame streak and is never idle.

The provisional production default is eight consecutive frames with RMS displacement at most `0.02` and maximum displacement at most `0.05`. These values are deliberately isolated behind policy construction and injectable in worker tests. They are supported by the accepted spike's final observation (RMS `0.014392`, maximum `0.044531`) after its stricter provisional RMS `0.01` failed to settle. Task 38 owns final calibration; no later caller should depend on these values as a permanent product contract.

## Error and Lifecycle Semantics

- Layout failures preserve the last valid positions and conflicts, never call Swing, and are observable through a failed frame.
- A paused worker preserves its last valid frame and does not advance physics until restarted.
- Empty projections and dormant pins are valid inputs.
- All frame maps, conflict lists, and idle measurements are immutable snapshots suitable for off-EDT publication.

## Verification

Task 20 tests will enforce the GraphStream boundary, absence of `LayoutRunner`, fixed quality, deterministic seeds, particle/anchor/spring topology, ordered strengths, aggregate fan-out cap, dynamic add/remove cleanup, prominence-aware separation, and immobility of pinned neighbours.

Task 21 tests will enforce executor serialization, pin/dormant/unpin behavior, whole-map uniform correction, one- and two-rigid-map conflicts, worker failure and restart, repeated close, and injected idle measurements/default construction. Focused Gradle tests run red before implementation and green after each task. Each task is committed separately using its exact task allowlist.

## Deferred Work

Task 19 owns label placement. Task 22 owns projection batching. Task 23 composes worker frames, correction, final hull computation, label placement, stale-generation rejection, and EDT publication. Task 38 calibrates force and idle constants against production diagnostics.
