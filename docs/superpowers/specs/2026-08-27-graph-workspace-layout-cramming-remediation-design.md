# Graph Workspace Layout Cramming Remediation Design

- Date: 2026-08-27
- Status: Approved for implementation
- Scope: Fix the Graph Workspace force-layout collapse in which freshly seeded particles teleport onto their neighbours at link insertion, keeping deterministic seeded positions and adding step-level spread regressions.

## Context

The Graph Workspace layout engine seeds every particle (visible nodes and enclosure anchors) at a deterministic, identity-derived position inside a 50.0 world-unit square, then adds all relationship, containment, and hierarchy links. GraphStream's `SpringBox.chooseNodePosition(NodeParticle, NodeParticle)` runs on every edge insertion after both endpoints have already registered the new edge, and its rule teleports any degree-1 endpoint onto an already-connected (degree >= 2) endpoint plus a small random offset. Because every node and anchor is degree-1 at the moment its first edge is inserted, nearly all particles collapse onto the first connected particle.

`apply()` still reports the original seed coordinates (`ParticleState.x/y`), so the collapse is invisible on the first published frame and only appears on the first `step()`, after which the whole graph renders as one pile. Recovery is slow: cross-map-linked particles additionally route their entire repulsion vector through the aggregate 0.005 world-unit cross-map displacement cap.

Evidence (deterministic probe replaying the production edge order on seeded particles): in the baseline three-node fixture, the root anchor teleports onto node a-one, the child anchor onto the root anchor, node a-two onto the child anchor, and the other-map root anchor onto b-one; node a-two moves about 36.7 world units between the apply frame and the first step frame. The existing `smallWorkspaceInitialPositionsAreNotCollapsedIntoTheOrigin` regression asserts only on the apply frame and therefore cannot see the collapse; the UI evidence screenshots hand-write positions and never run the layout engine.

## Design

### Production change

Override `chooseNodePosition(NodeParticle first, NodeParticle second)` in `TypedSpringBox` as a no-op:

```java
@Override
protected void chooseNodePosition(final NodeParticle first, final NodeParticle second) {
    // Keep the engine's deterministic seeded positions. The default
    // implementation teleports a degree-1 endpoint onto its already-connected
    // neighbour at edge insertion, collapsing freshly seeded particles into a pile.
}
```

The engine already positions every particle deterministically before adding links, so the library's incremental placement heuristic is unwanted and is the sole cause of the collapse. The override preserves the deterministic seeds, keeps the force equations, pin handling, solver quality, and public layout API unchanged, and is safe for pins because GraphStream's default already returns early for frozen particles and the override changes no other path.

### Regressions

Add two `TypedForcesShould` regressions that assert the solver's published positions stay close to the previously published frame (per-particle movement below 1.0 world unit), using a new `greatestMovementBetween(LayoutFrame, LayoutFrame)` helper that covers both nodes and anchors:

1. `firstStepDoesNotTeleportSeededParticlesOntoTheirNeighbours` — apply `baseline(1)`, step once, and assert every node and anchor moved less than 1.0 world unit between the two frames. Fails on the current implementation (observed movement ~36.7 units) and passes with the override (observed movement ~0.001 units).
2. `aTopologyChangeDoesNotTeleportRetainedParticles` — apply `baseline(1)`, step, apply `expanded(2)`, step, and assert every retained node and anchor moved less than 1.0 world unit between the two step frames. This pins the re-collapse that would otherwise occur whenever a projection change removes and re-adds all links.

Both tests use only the public `LayoutEngine` surface and the existing deterministic fixtures, so they are stable and seed-independent in the sense that the asserted invariant (published frames agree with the solver) holds for any seed envelope.

## Testing

1. Add the two regressions and run the focused `TypedForcesShould` suite; both must fail against the current implementation (movement ~36.7 world units) while the existing 13 tests stay green.
2. Add the `chooseNodePosition` override and rerun the focused suite; all 15 tests must pass.
3. Falsifiability probe in a disposable manner: record the production file SHA-256, restore the original file (removing the override), prove both regressions fail, restore the exact override bytes, verify the SHA-256 matches, and rerun the focused suite green. The active worktree must be unchanged by the probe.
4. Run the complete `:freeplane_plugin_graph:test` suite with `JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu`.
5. Run `git diff --check` and verify that only the two allowlisted paths changed.

The expected verification must show zero failed tests. Existing unrelated lifecycle observations remain outside this remediation's acceptance claim and must be reported if encountered.

## Scope

Only these two paths may change:

- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/TypedSpringBox.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java`

No changes are made to `TypedNodeParticle`, `GraphStreamLayoutEngine`, public layout interfaces, projection code, canvas code, persistence, or dependencies in the deliverable branch. The final result must retain all prior Graph Workspace commits and add one focused remediation commit.
