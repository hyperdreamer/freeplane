# Graph Workspace Node Prominence Design

- **Task Identifier:** 2026-08-10-graph-workspace
- **Amends:** `docs/superpowers/specs/2026-08-10-graph-workspace-design.md`
- **Scope:** visual prominence of projected graph nodes based on visible outgoing reach
- **Status:** approved in design discussion; implementation plan and code changes are separate follow-up work

## Goal

Make a node with many visible outgoing relationships visually recognizable as a source or foundation, without allowing a dense hub to dominate the canvas or exposing hidden graph content.

This amendment changes node geometry only. It does not change projection membership, relationship persistence, endpoint resolution, relationship direction semantics, labels, or the confidentiality boundary defined by the base specification.

## 1. Prominence Metric

For each currently visible projected graph node, compute `d` as the number of distinct visible outgoing projected endpoints.

Direction rules:

- A directed contributor `A -> B` increments `A` once for endpoint `B`.
- A bidirectional contributor `A <-> B` increments both `A` and `B` once.
- An undirected contributor `A - B` increments neither endpoint.
- Duplicate or parallel contributors to the same source/target endpoint pair count once for prominence.
- Self-relationships contribute zero.
- A visible group boundary is one endpoint, regardless of how many descendants it contains.
- If an endpoint disappears from the current projection through filtering, collapsing, hiding, or map removal, it contributes zero until it becomes visible again.

The calculation uses projected endpoint identity, not raw node IDs and not descendants hidden inside a group boundary. A relationship is never redistributed to those descendants. A visible group boundary is therefore treated as one distinct target when a line visibly terminates at that boundary.

The metric is based on the graph currently visible to the user. A projection update may consequently resize an affected source when a map, endpoint, filter, collapse state, or group marker changes. Unaffected nodes do not resize merely because an unrelated node changes.

### Endpoint examples

| Relationship | Prominence contribution |
|---|---|
| `A -> B` | `A + 1` |
| `A <-> B` | `A + 1`, `B + 1` |
| `A - B` | none |
| two `A -> B` contributors | `A + 1` total |
| `A -> A` | none |
| `A ->` visible group boundary | `A + 1` |
| `A ->` hidden descendants behind that boundary | never counted separately |

## 2. Scaling And Geometry

Use one absolute, deterministic, capped logarithmic scale:

```text
scale(d) = min(1.75, 1 + 0.20 * log2(max(1, d)))
```

The resulting values include:

| Distinct visible outgoing endpoints | Scale |
|---:|---:|
| 0–1 | `1.00x` |
| 2 | `1.20x` |
| 4 | `1.40x` |
| 8 | `1.60x` |
| 14 or more | `1.75x` maximum |

The multiplier is applied to a projected node's base geometry before downstream geometry is computed. It affects:

- the node shape bounds and collision radius;
- layout separation involving that node;
- hull padding and containment around the enlarged node;
- relationship attachment points, so arrows terminate on the updated node boundary;
- hit-testing and pointer-target bounds;
- accessibility bounds and virtual-child geometry.

The multiplier does **not** change:

- label text or label font size;
- edge stroke thickness or multiplicity cues;
- enclosure-boundary size directly;
- projected node or edge counts;
- persistence formats or source-map data.

### Boundary propagation

A group boundary is not enlarged directly by the prominence metric. It is recomputed from its child geometry after child node scaling:

1. scale the projected node's own shape;
2. recompute the immediate group boundary with its normal clearance;
3. propagate the resulting extent through any containing parent boundaries;
4. recompute edge attachment points and hit bounds from the new geometry.

A boundary must never clip an enlarged child. Existing label-placement padding caps remain in force: labels cannot inflate a boundary without limit, but child-shape containment takes precedence. If an outgoing edge targets a group boundary, it still counts as one visible endpoint and attaches to the updated boundary.

When `d` changes, the next immutable projection/geometry generation carries the new scale. Unaffected node positions and pins are retained under the existing stable-key and pin rules. A prominence-only update must not reset the whole layout.

### Rejected alternatives

- **Percentile-relative sizing** was rejected because adding or removing an unrelated node would resize existing nodes and make size semantically unstable.
- **Fixed size tiers** were rejected because threshold crossings create abrupt jumps and discard information between thresholds.

## 3. Data Flow And Component Responsibilities

The prominence count is derived from the already-built projected edges and projected endpoints. It must not perform a second traversal of Freeplane models, inspect hidden descendants, resolve map IDs, or invoke any content transformer.

The intended flow is:

1. projection consolidation determines visible projected endpoints and directional coverage;
2. a pure prominence calculation derives one count and scale per visible projected node;
3. layout receives the scaled node geometry/radius for separation and collision behavior;
4. hull geometry recomputes group boundaries from scaled child shapes;
5. edge geometry attaches lines to the resulting node or group-boundary shape;
6. canvas hit testing and accessibility consume the same immutable geometry rather than recomputing size independently.

The calculation is `O(V + E)` in the projected graph and uses deterministic ordered collections. It is a derived view property, never a new source of truth.

## 4. Testing And Performance Gates

Deterministic tests must cover:

- `A -> B` counting only `A`;
- `A <-> B` counting both endpoints;
- undirected relationships and self-relationships contributing zero;
- duplicate and parallel contributors to one projected endpoint counting once;
- a visible group boundary counting once without inspecting or counting descendants;
- filtering, collapsing, hiding, and map removal resizing only affected sources;
- exact scale values at `d = 0, 1, 2, 4, 8, 13, 14+`;
- monotonicity, finite output, and the `1.75x` cap;
- scale propagation to node geometry, collision bounds, hulls, edge attachments, hit testing, and accessibility bounds;
- enlarged children remaining inside their recomputed boundaries and parent boundaries;
- unaffected node positions and pins remaining stable across a prominence-only update;
- deterministic output for identical snapshots regardless of collection iteration order.

The existing engineering workload of 2,000 projected nodes and 5,000 projected edges must include prominence calculation, geometry recomputation, hit-index updates, and the first rendered frame. No separate expensive pass may cause the graph to exceed the existing interaction budget.

Tests must include a dense outgoing fan, a mixed directed/bidirectional/undirected graph, duplicate contributors, a target represented by a group boundary, and an update that removes that boundary. The dense fan must reach the cap without producing non-finite geometry or clipping.

## 5. Implementation Boundaries And Sequencing

This amendment is consumed by the existing planned projection, geometry, layout, canvas, interaction, and accessibility work. The implementation plan must assign responsibilities without introducing a parallel sizing system:

- projection owns visible endpoint identity and directional coverage;
- geometry owns scale application and boundary propagation;
- layout consumes the resulting geometry/radius and preserves stable positions;
- canvas, hit testing, and accessibility consume the same published immutable geometry;
- no component may infer prominence from raw Freeplane links independently.

The implementation remains subject to the base specification's confidentiality requirements: unreachable nodes and hidden descendants must not affect labels, counts, geometry, or accessibility output.
