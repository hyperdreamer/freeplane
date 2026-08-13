# Node Prominence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for readability; controller state is canonical.

- **Task Identifier:** 2026-08-10-graph-workspace
- **Original backlog task:** Task 17, Compute node prominence from visible outgoing reach
- **Base commit:** `713cc2c8dfba2b37cc834d1000028767a31c4729`

**Goal:** Compute a deterministic, immutable prominence value for every projected graph node from its distinct visible outgoing targets, including the approved visible group-boundary deduplication rule.

**Architecture:** `NodeProminence` is a pure value object. `ProminenceCalculator` consumes already-consolidated projected nodes, enclosures, and edges only; it never reads Freeplane models, resolves IDs, traverses descendants, or invokes content conversion. `GraphProjection` publishes the ordered immutable result, and `ProjectionEngine` populates it after edge consolidation.

## Global Constraints

- Follow `AGENTS.md`: Java source and target compatibility are 8, UTF-8 encoding, four-space indentation, JUnit 4/AssertJ/Mockito tests, and builds use `gradle`, not Maven or the Gradle wrapper.
- Use `/home/henry/.sdkman/candidates/java/21.0.8-zulu` for every Gradle and JDK command; set `JAVA_HOME` and prepend its `bin` directory to `PATH`. Verify that exact path before implementation.
- Work only in the isolated Task 17 worktree and keep the implementation allowlist exactly five paths: `NodeProminence.java`, `ProminenceCalculator.java`, `GraphProjection.java`, `ProjectionEngine.java`, and `ProminenceCalculatorShould.java`.
- Do not modify `freeplane_api`, build files, existing projection value types, translations, resources, prior-task files, or any file outside the five-file allowlist.
- Keep all graph projection values immutable and deterministic. Do not expose mutable collections; use ordered immutable maps/lists and do not depend on `HashMap` or `HashSet` iteration for published order.
- The published prominence map iterates in **projected-node order**: walk the supplied `nodes` list once and build an insertion-ordered immutable map. `ProjectedNodeKey` is deliberately not `Comparable`; do not add `Comparable` to it, do not sort by `toString`, and do not introduce a comparator. This matches the existing determinism rule that map nodes use structural traversal order.
- Projection/layout packages must not depend on mutable Freeplane model types. This task consumes only `ProjectedNode`, `ProjectedEnclosure`, `ProjectedEdge`, and their immutable keys.
- Endpoint identity remains exact for relationship creation, inspection, and deletion. Prominence is a visual metric only and dedupes enclosure endpoints by visible `EnclosureHullKey`, not by addressable `EnclosureKey`.
- A directed contributor increments only its source; a bidirectional contributor increments both endpoints; an undirected contributor increments neither. Duplicate or parallel contributors to one projected target count once, and contributors whose endpoints resolve to the same projected endpoint count zero.
- An active Graph Group root is a projected graph node and receives prominence normally. An ancestor enclosure is a visible group boundary and is a target for counting but never receives a prominence entry of its own. Hidden descendants are never inspected or redistributed.
- The scale is exactly `min(1.75, 1 + 0.20 * log2(max(1, d)))`, where `d` is a nonnegative integer. It must be finite, monotonic, and capped at `1.75`; `d = 0` and `d = 1` both produce `1.00`.
- Use test-driven development: write the behavioral test first, run it and observe the expected red failure, then implement the minimum production behavior and rerun green.
- After green, run the named boundary-fold mutant: temporarily dedupe enclosure targets by `EnclosureKey` instead of `EnclosureHullKey`, prove `twoCollapsedAncestorsInOneVisibleBoundaryCountOnce` fails, immediately restore the exact production SHA-256, verify no mutant diff remains, and rerun green.
- Before staging, assert the index is empty. Stage exactly the five allowlist paths and compare `git diff --cached --name-only` to that allowlist. Commit with `2026-08-10-graph-workspace: Compute node prominence`.

## Task 1: Compute node prominence from visible outgoing reach

**Implementer tier:** Advanced

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/NodeProminence.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/ProminenceCalculator.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/GraphProjection.java:1-end`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/ProjectionEngine.java:1-end`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/ProminenceCalculatorShould.java`

**Interfaces:**
```java
public final class NodeProminence {
    public static NodeProminence of(int visibleOutgoingTargets);
    public int visibleOutgoingTargets();
    public double scale();
}
public final class ProminenceCalculator {
    public static Map<ProjectedNodeKey, NodeProminence> calculate(
        List<ProjectedNode> nodes, List<ProjectedEnclosure> enclosures, List<ProjectedEdge> edges);
}
```

`GraphProjection` gains `prominence()` as an ordered immutable `Map<ProjectedNodeKey, NodeProminence>` covering every projected node. Existing `structure()` and `resolved()` projections have an empty prominence map; the fully projected result is populated after edge consolidation. Preserve value equality/hash behavior and all existing constructor invariants.

For each canonical `ProjectedEdge`, an arrow at the far end is outgoing from the near endpoint: `arrowAtSecond()` makes `first()` reach `second()`, and `arrowAtFirst()` makes `second()` reach `first()`. Ignore an outgoing source if it is an enclosure, because enclosures do not receive prominence. For a node target, dedupe by `ProjectedNodeKey`. For an enclosure target, map its addressable `EnclosureKey` through the matching `ProjectedEnclosure.endpointKeys()` to that enclosure's `hullKey()` and dedupe by `EnclosureHullKey`. Two addressable ancestors sharing one visible boundary therefore count as one target. Missing or non-visible endpoint mappings must not expose or inspect hidden descendants; follow the existing immutable projection invariants rather than introducing a raw-node lookup.

The calculator's returned map must contain every input projected node, including nodes with zero outgoing targets, in projected-node order taken from the supplied `nodes` list, independent of the order of the `edges` and `enclosures` arguments. It must return an unmodifiable map. `NodeProminence.of` rejects negative counts and computes the exact capped logarithmic scale without non-finite output.

Two branches need explicit behavior. A contributor whose endpoints resolve to one projected endpoint cannot reach this calculator: `ProjectionEngine.projectEdges` already skips it and `ProjectedEdgeKey` rejects identical endpoints, so assert that rejection at the key level rather than building an impossible edge fixture. An outgoing enclosure target whose `EnclosureKey` appears in no supplied `ProjectedEnclosure` is not visible, so it contributes zero and is skipped without throwing and without any descendant or raw-node lookup.

- [ ] **Step 1: Tests** Write failing tests for directed source-only counts; bidirectional counts on both endpoints; undirected contributors counting zero; `ProjectedEdgeKey` rejecting identical endpoints, which is why a self-resolving contributor never reaches the calculator; duplicate contributors counting once; two collapsed ancestors in one visible boundary counting once while both remain addressable; an enclosure target absent from the supplied enclosures contributing zero without throwing; active Graph Group root nodes counting normally; enclosure-only endpoints absent from the prominence map; every zero-degree node present; exact scale at `d = 0, 1, 2, 4, 8, 13, 14+`; monotonic finite capped output; unmodifiable result; and projected-node ordering that is unchanged when the `edges` and `enclosures` arguments are reordered.
- [ ] **Step 2: Red** Run only `*ProminenceCalculatorShould` with `--rerun-tasks` and confirm failure is caused by the missing production implementation/API, not malformed test setup.
- [ ] **Step 3: Implement** Add the pure `O(V + E)` calculation over consolidated edges, wire `GraphProjection` and `ProjectionEngine`, and preserve all existing projection tests.
- [ ] **Step 4: Boundary-fold mutant** Record production/test hashes; change only enclosure deduplication from `EnclosureHullKey` to `EnclosureKey`; run the named collapsed-ancestor test and confirm red; restore the exact production hash and rerun the complete focused class green.
- [ ] **Step 5: Verify and commit** Run the focused class, the projection module check, `verifyGraphBundle`, Java 8 bytecode inspection for changed production classes, `git diff --check`, and exact five-file scope validation. Stage and commit the exact allowlist.

**Expected commit:**
```bash
git commit -m "2026-08-10-graph-workspace: Compute node prominence"
```
