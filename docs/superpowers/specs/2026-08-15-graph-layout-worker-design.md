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

`LayoutRequest` carries the workspace ID (used for diagnostic labelling and as the primary seed namespace), current projection, stable-key diff, and pins. `GraphStreamLayoutEngine` retains particles for unaffected keys across requests, removes obsolete graph elements, and deterministically seeds new nodes and enclosure anchors from their stable keys. The seed for a projected node key is derived by XOR-ing `workspaceId.value().getLeastSignificantBits()` with the SHA-256 of the key's canonical string form (`key.toString()`), taking the lower 64 bits as a `long` and using it to seed a `java.util.Random`. The same derivation applies to enclosure hull keys. Transient structural-path keys produce a canonical form from their map ID and path indices. This algorithm is stable across JVM restarts because all inputs are string-representable values with no reliance on `Object.hashCode()`. Active pins set an exact particle position and freeze it; dormant pins do not create a particle or force. Pin coordinates (`PinProjection.x()` and `.y()`) are stored and interpreted in GraphStream physics layout space — the same coordinate space in which particle positions are reported in `LayoutPositions`. No coordinate-space conversion is applied.

The solver represents:

- a particle for every visible projected node;
- an invisible particle for every enclosure hull anchor;
- relationship springs for projected edges;
- weak containment springs from direct nodes to their enclosing anchor;
- weak hierarchy springs between nested enclosure anchors.

`TypedSpringBox` implements the required force classes without using `layout.weight` as stiffness. Instead, each force type computes a displacement vector and scales it by the calibration multiplier before adding it to the particle's cumulative step contribution; the multiplier is therefore dimensionless relative to a unit base force. The calibration starts with containment `0.15`, hierarchy `0.30`, and same-map relationship attraction `1.0`; the ordering invariant `containment < hierarchy < sameMap` must hold. `LayoutCalibration.spikeDefaults()` returns these three values. Cross-map relationship displacement is accumulated once per particle per step and clamped by vector magnitude to the fixed `0.005` cap. Prominence is used only as a particle-size separation hint: each visible node particle receives a repulsion radius of `physicsBaseRadius * prominence.scale()`, implemented in `TypedNodeParticle`, where `physicsBaseRadius` is a physics-domain constant independent of the geometry domain's `BASE_RADIUS = 8.0`. The `spikeDefaults()` value for `physicsBaseRadius` is `8.0` (matching geometry) but it is a separate named constant in the layout package, not shared with `GraphGeometryEngine`. Enclosure anchor particles use a fixed radius equal to `physicsBaseRadius` (scale 1.0). Enlarged node particles push neighbours further away without receiving extra spring force. Prominence never changes a pin, exceeds the aggregate cap, or shrinks a node to avoid contact.

Every public value is immutable and GraphStream-free. `LayoutFrame` contains its monotonically increasing step index, immutable `LayoutPositions`, and a failure flag. `apply`, `step`, `reset`, and `close` are the only public engine operations. `reset()` discards all particles, springs, and internal solver state, reconstructs the full graph from the most recently accepted `LayoutRequest` using fresh deterministic seeds, and resets the step index to zero; it does not change the accepted request.

## Task 21: Serialized Worker and Correction

`LayoutWorker` owns one single-thread executor. All calls to its engine occur on that executor, so GraphStream mutation and `compute()` cannot overlap. `submit` applies the request; `step` advances the same accepted request. `pause()` suspends stepping while leaving engine state intact. `restart()` resumes a paused worker without touching engine state; if the engine is in a failed state, `restart()` also creates a fresh engine instance so the next `submit()` can proceed cleanly. The worker retains the last valid frame and returns it with `failed=true` if the engine raises an exception. `close()` is idempotent, rejects later work, and leaves no live worker thread.

`MapTierCorrection` is a pure operation over a projection, immutable positions, and hull geometry. It considers only map-root hull pairs, iterated in the order that map-root `ProjectedEnclosure` entries appear in `GraphProjection.enclosures()`, which is the stable projection output order. Each pair `(A, B)` is visited once with `A` as the earlier enclosure; pairs are not revisited with roles reversed. A map is rigid if any active pin belongs to it. For an overlap:

- when both maps are movable, split `T = HullIntersection.minimumSeparatingTranslation(hullA, hullB)` uniformly: map A's particles translate by `+T/2` and map B's particles translate by `-T/2`, where `T` is the vector that would move A clear of B if applied entirely to A;
- when exactly one map is rigid, give the complete translation to the movable map;
- - when both are rigid, preserve every position and emit an immutable `LayoutConflict` with public API `MapReferenceId firstMap()`, `MapReferenceId secondMap()`, and `List<PinProjection> blockingPins()` listing only the active pins that make each map rigid.

The correction translates all node particles and enclosure anchors belonging to a moved map by the same vector. It never translates only a root anchor or a subset of a pinned map. It is intentionally a transformation of each returned immutable frame: the Task 20 `LayoutEngine` boundary has no position-writeback operation, so Task 21 does not add one or leak GraphStream types. Because the engine has no writeback path, it always steps from its own internal uncorrected positions; correction is recomputed from scratch on every published frame, continuously nudging maps toward separation without disturbing the physics solver's internal energy bookkeeping. The later settle loop recomputes geometry from each corrected frame before label placement and publication. On the first frame of a settle loop, no prior hull geometry is available; the caller passes `GraphGeometry.of(Collections.emptyMap(), Collections.emptyMap())`, which produces no correction and no conflicts. Task 23 must initialise its retained geometry reference to this empty value before entering the loop body.

## Idle Policy

Idle status derives from observed displacement rather than GraphStream stabilization, which becomes invalid after map correction. `PerceptualIdlePolicy` compares matching node and anchor positions in consecutive frames. A topology mismatch — defined as the node key set of `after.nodes()` differing from `before.nodes()`, or the anchor key set of `after.anchors()` differing from `before.anchors()`, or both — resets the stable-frame streak to zero and is never idle; both key sets must match for displacement to be measured. An empty projection (zero nodes and zero anchors in both frames) is treated as immediately idle: `consecutiveStableFrames` is set to the required threshold and `idle()` returns true.

RMS and maximum displacement are computed over all positions in matching `LayoutPositions` frames — both node particles and enclosure anchor particles. The provisional production default is eight consecutive stable frames, RMS displacement at most `0.02`, and maximum displacement at most `0.05`. These defaults are returned by `PerceptualIdlePolicy.spikeDefaults()`, a static factory method, so that Task 38 calibration changes touch only that one class. `LayoutWorker` constructs its policy by calling `PerceptualIdlePolicy.spikeDefaults()`. Values are injectable in tests by passing a custom `PerceptualIdlePolicy` to the worker constructor. They are supported by the accepted spike's final observation (RMS `0.014392`, maximum `0.044531`) after its stricter provisional RMS `0.01` failed to settle. Task 38 owns final calibration; no later caller should depend on these values as a permanent product contract.

## Error and Lifecycle Semantics

- Layout failures preserve the last valid positions and conflicts, never call Swing, and are observable through a failed frame.
- `lastValidFrame()` returns the most recent non-failed frame, or an empty failed frame with step index 0 if no frame has been successfully published; Task 23 uses it to retain valid positions when the worker fails mid-settle.
- A paused worker preserves its last valid frame and does not advance physics until restarted.
- `step()` and `submit()` return `CompletionStage<LayoutFrame>`. When `step()` is called before any successful `submit()`, or while the worker is paused, it returns an already-completed `CompletionStage` wrapping the last valid frame without advancing physics; if no frame exists it returns an already-completed stage wrapping the same empty failed frame as `lastValidFrame()`.
- `close()` is idempotent, rejects later work, and leaves no live worker thread. Any in-flight `CompletionStage` not yet resolved at close time is completed exceptionally with `CancellationException` before the executor shuts down.
- Empty projections and dormant pins are valid inputs.
- All frame maps, conflict lists, and idle measurements are immutable snapshots suitable for off-EDT publication.

## Verification

Task 20 tests will enforce the GraphStream boundary, absence of `LayoutRunner`, fixed quality, deterministic seeds, particle/anchor/spring topology, ordered strengths, aggregate fan-out cap, dynamic add/remove cleanup, prominence-aware separation, and immobility of pinned neighbours.

Task 21 tests will enforce executor serialization, pin/dormant/unpin behavior, whole-map uniform correction, one- and two-rigid-map conflicts, worker failure and restart, repeated close, and injected idle measurements/default construction. Focused Gradle tests run red before implementation and green after each task. Each task is committed separately using its exact task allowlist.

## Deferred Work

Task 19 owns label placement. Task 22 owns projection batching. Task 23 composes worker frames, correction, final hull computation, label placement, stale-generation rejection, and EDT publication. Task 38 calibrates force and idle constants against production diagnostics.
