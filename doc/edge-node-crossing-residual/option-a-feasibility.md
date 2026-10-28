# Option A feasibility: the engine can be seeded without restructuring

Task identifier: `2026-10-26-edge-node-crossing-residual`
Defect: `graph-layout-non-convergence-29-discs`
Decision under test: option A — feed accepted corrections back into the `LayoutEngine`.

## Question

Does `GraphStreamLayoutEngine` admit a seed-position capability, or does option A require restructuring it?

## Answer: it admits one. No restructuring needed.

Three facts, each verified in source.

1. **The per-particle position is directly settable.** `GraphStreamLayoutEngine.synchronize` already writes positions
   through `springBox.setParticlePosition(desired.id, state.x, state.y)` — at particle creation
   (`GraphStreamLayoutEngine.java:140-143`) and at pin application (`:156-160`). A correction seed is the same call
   with a different source of coordinates.

2. **Particle state already carries position.** Each particle keeps a `ParticleState` with `x`, `y`, `radius` and
   `pinned`, held in `particles`, keyed by the topology id. There is no missing state to add.

3. **Only *new* particles are seeded today.** `synchronize` guards its seeding with `if (state == null)`
   (`GraphStreamLayoutEngine.java:139`). Existing particles keep their previous `state.x/state.y`. That is precisely the
   divergence: an accepted correction never reaches this branch, because the particle already exists.

So the seam is a single new entry point, not a redesign. The natural shape:

```java
// new, on LayoutEngine; implemented by GraphStreamLayoutEngine
void seedPositions(Map<ProjectedNodeKey, LayoutPoint> accepted);
```

and in `LayoutWorker.accept`, after `final LayoutPositions corrected = edge.positions();`, pass the corrected node
centres to it before the next `step()`.

Two details the implementation must resolve, both already visible in the code:

- The engine keys particles by topology id, not by `ProjectedNodeKey`. The mapping is built inside `topology(...)`
  from `DesiredParticle.nodeKey`; a seeding method must resolve the same mapping rather than assume key equality.
- Seeding must not fight pins. `synchronize` applies pin coordinates *after* the default seed; a seeding method must
  skip any particle whose `state.pinned` is true, or the pin contract (E9) is silently overridden.

## What this does and does not establish

**Established:** option A is implementable without restructuring the engine, and the exact call site and guard
condition are identified.

**Not established:** whether feeding corrections back actually converges. That is the experiment's second half and it
requires a real code change plus a run against the 29-disc reproduction. It must not be assumed: the whole point of
choosing A first was that its outcome decides whether option C is mandatory.

## The experiment, stated so it can be executed without further design

1. Add the seeding entry point and the `LayoutWorker.accept` call, honouring the pin guard above.
2. Run the archived probe against the unmodified maps (29 discs) and record `steps` to `idle=true`, or prove
   non-convergence with a bounded frame count and a residual trajectory.
3. Run the same probe against the marker-removed copy (28 discs) and against the delivery base, as controls.
4. Decision rule, fixed in advance: convergence means A is the fix; non-convergence means the force equilibrium itself
   prefers a crossing arrangement, which makes option C mandatory and retires A.

Do not tune thresholds, relax the idle predicate, or widen the frame bound to manufacture a pass. The acceptance
question is unchanged: *with `ID_1271861869` marked as a group, does the workspace reach `idle=true` within a bounded
frame count, and does the settled frame have every published path clear of every non-incident disc?*
