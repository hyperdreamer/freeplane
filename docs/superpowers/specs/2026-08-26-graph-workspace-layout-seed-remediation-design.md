# Graph Workspace Layout Seed Remediation Design

- Date: 2026-08-26
- Status: Approved for implementation
- Scope: Remediate seed-sensitive GraphStream force tests while retaining the 50.0-unit initial spread

## Context

The Graph Workspace layout currently initializes every new particle with a deterministic, identity-derived position inside a 50.0 world-unit square. This spread is intentional: it prevents small workspaces from beginning as a visually collapsed pile and gives the scrollable canvas meaningful world extents.

The first implementation of that spread exposed three existing `TypedForcesShould` assumptions. Those tests compare one solver tick from the former 0.002-unit seed square. At that scale, particles begin nearly coincident, so repulsion, prominence scaling, and hierarchy behavior dominate the comparison. At a 50.0-unit seed, absolute starting distances and Barnes-Hut range behavior can dominate instead. The failures are therefore fixture coupling to an incidental seed envelope, not evidence that the production force equations must change.

The prior SDD run that recorded this blocker is terminal and remains immutable. This design is implemented in a separate run and worktree; its reports and state are not used as correctness evidence.

## Design

### Production seed

Keep one production initialization policy:

```java
private static final double INITIAL_POSITION_SPREAD = 50.0;
```

`initialPositionSpread(...)` returns that constant for every projection size. Preserve the existing SHA-256 identity encoding, deterministic random derivation, particle ordering, topology, pin handling, force quality, prominence radius, reset behavior, and map/layout behavior. No test-only branch, new production hook, force constant, or solver compensation is introduced.

### Deterministic force fixtures

The force tests continue to exercise the real `GraphStreamLayoutEngine` through `LayoutEngine`, `LayoutRequest`, and `PinProjection`.

For a test that needs a particle at a force-relevant coordinate, the fixture first applies the same projection with active pins at explicit coordinates, then applies the same projection with only the pins that should remain active. The pin change prevents the layout engine's identical-request fast path from coalescing the setup, while the public synchronization path positions the particles and configures their frozen state. The following solver step then measures the unpinned particle under the arranged geometry. The fixture must not access package-private GraphStream particle objects or add a production test seam.

The unpinned-particle test arranges two particles at `(0.0, 0.0)` and `(20.0, 0.0)`, retains the second pin, releases the first, and verifies that the first particle moves after one step.

The prominence test arranges the source and pinned neighbor at `(24.0, 0.0)` and `(0.0, 0.0)`. In the high-prominence case it also pins the two outgoing targets at `(100.0, 100.0)` and `(100.0, -100.0)`. It then compares the source-to-neighbor distance after one step. This preserves the existing assertion that the larger prominence-scaled source produces greater separation while removing random initial coordinates from the comparison.

The hierarchy test uses identical pinned direct-node coordinates for a nested-enclosure projection and a peer-enclosure projection. It captures each scenario's anchor distance before and after one step and compares the distance deltas. A hierarchy link is an attraction spring, so its invariant is that the nested case's distance change is smaller than the peer case's distance change. Comparing deltas cancels the common seed-dependent absolute anchor separation and tests the link's incremental effect. The test name and assertion must describe this attraction behavior accurately.

### Initial-spread regression

Retain the regression that applies a small projection without stepping and asserts that the greatest pairwise distance among distinct projected node positions is greater than `1.0`. Keep the equal-request determinism and different-workspace seed tests unchanged.

## Testing

Implementation follows TDD in the task child:

1. Run the focused `TypedForcesShould` suite against the pre-change implementation and observe the new spread regression fail for the old 0.002 envelope.
2. Add the deterministic force fixtures and run the focused suite, confirming the three old one-step assertions fail before the production spread change and then pass with the complete remediation.
3. Run a falsifiability mutation probe in a disposable copy: restore the old size-dependent spread and require the initial-spread regression to fail; remove each fixture arrangement or restore the old absolute hierarchy assertion and require the corresponding focused test to fail. The active worktree must remain unchanged by probes.
4. Run the focused layout boundary suite and the complete `:freeplane_plugin_graph:test` suite with `JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu`.
5. Run `git diff --check` and verify that only the two allowlisted source/test files changed.

The expected verification must show zero failed tests. Existing unrelated lifecycle observations remain outside this remediation's acceptance claim and must be reported if encountered.

## Scope

Only these two paths may change:

- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream/GraphStreamLayoutEngine.java`
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/TypedForcesShould.java`

No changes are made to `TypedNodeParticle`, `TypedSpringBox`, public layout interfaces, projection code, canvas code, persistence, or SDD artifacts in the deliverable branch. The final result must retain all prior Graph Workspace commits through `40c619ecdf` and add one focused remediation commit.
