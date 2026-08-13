# Graph Workspace Node Prominence Design

- **Task Identifier:** 2026-08-10-graph-workspace
- **Amends:** `docs/superpowers/specs/2026-08-10-graph-workspace-design.md`
- **Scope:** visual prominence of projected graph nodes based on visible outgoing reach
- **Status:** approved in design discussion; implementation plan and code changes are separate follow-up work

## Goal

Make a node with many visible outgoing relationships visually recognizable as a source or foundation, without allowing a dense hub to dominate the canvas and without exposing hidden graph content.

This amendment changes projected node size and the geometry derived from it. It does not change projection membership, relationship persistence, endpoint resolution, direction semantics, labels, enclosure tiers, or the confidentiality boundary defined by the base specification.

## 1. Prominence Metric

For each visible projected graph node, `d` is the number of distinct **visible prominence targets** it reaches through outgoing relationships.

### Visible prominence target

A prominence target is what the user can actually see and click, not an internal identity:

- an outgoing endpoint that projects to a graph node is identified by its `ProjectedNodeKey`;
- an outgoing endpoint that projects to an ancestor enclosure is identified by its **visible boundary** (`EnclosureHullKey`), not by the addressable ancestor (`EnclosureKey`).

Because a maximal unary ancestor chain collapses into one visible boundary, several separately addressable ancestors can share one prominence target. Reaching two collapsed ancestors inside one visible boundary is `d = 1`. Prominence is a visual metric: two canvases that look identical must produce identical node sizes, so counting hidden addressable identities is explicitly rejected. Relationship creation, inspection, and deletion continue to use exact endpoint identity and are unaffected.

### Direction rules

- A directed contributor `A -> B` increments `A` once for target `B`.
- A bidirectional contributor `A <-> B` increments both `A` and `B` once.
- An undirected contributor increments neither endpoint. The base specification also calls these nondirectional contributors; both names mean the same thing.
- Duplicate or parallel contributors reaching the same target count once, matching the existing rule that they paint one line.
- A contributor whose endpoints resolve to the same projected endpoint contributes nothing, consistent with the existing omission rule for self-resolving contributors.
- A visible group boundary counts once, regardless of how many descendants or collapsed ancestors it represents.
- A target that leaves the current projection through filtering, collapsing, hiding, or map removal contributes zero until it becomes visible again.

Descendants hidden inside a group boundary are never inspected, counted, or given a share of the relationship. Unreachable and hidden content must not influence `d`.

The metric is derived from the graph currently visible to the user, so a projection change can resize the affected source. Unaffected nodes never resize because an unrelated node changed.

### Target examples

| Relationship | Prominence contribution |
|---|---|
| `A -> B` | `A + 1` |
| `A <-> B` | `A + 1`, `B + 1` |
| undirected `A - B` | none |
| two `A -> B` contributors | `A + 1` total |
| `A -> A` | none |
| `A ->` visible group boundary | `A + 1` |
| `A ->` two collapsed ancestors sharing one visible boundary | `A + 1` |
| `A ->` hidden descendants behind a boundary | never counted separately |

### Terminology

The user-facing term for an ancestor enclosure boundary is **group boundary**. `EnclosureKey`, `EnclosureHullKey`, and *ancestor enclosure* remain the internal implementation terms.

## 2. Scaling And Geometry

Use one absolute, deterministic, capped logarithmic scale:

```text
scale(d) = min(1.75, 1 + 0.20 * log2(max(1, d)))
```

| Distinct visible outgoing targets | Scale |
|---:|---:|
| 0–1 | `1.00x` |
| 2 | `1.20x` |
| 4 | `1.40x` |
| 8 | `1.60x` |
| 13 | `1.7401x` |
| 14 or more | `1.75x` maximum |

The scale multiplies the projected node's own base shape extent in world space, so prominence is preserved across zoom and is not a screen-space decoration. Values are finite, monotonic, and capped.

Derived geometry consumes the scaled extent:

- node shape bounds and the node's effective collision extent;
- layout separation around that node;
- the fitted hull extent of the enclosure that directly contains it;
- relationship attachment points, so lines terminate on the updated shape;
- hit-testing and pointer-target bounds;
- accessible bounds and virtual-child geometry.

Padding values are unchanged. Hull and label padding stay constant; only the enclosed extent grows.

The scale does **not** change:

- label text, label font size, or label suppression thresholds;
- edge stroke thickness or multiplicity cues;
- enclosure stroke tiers, map colors, or the two visual tiers;
- group boundary size directly;
- projected node or edge counts;
- persistence formats or source-map data.

An active Graph Group root is projected as an ordinary graph node and is enlarged by its own outgoing reach like any other node. Only enclosure boundaries are exempt.

Prominence applies at every adaptive rendering tier, including above the engineering target, because it is geometry rather than optional detail.

### Boundary propagation

A group boundary is never enlarged by its own relationship count. It grows only because the child geometry it is fitted around grew:

1. apply the node's prominence scale to its own shape;
2. refit the directly containing boundary around its child node shapes and child hulls with unchanged clearance;
3. recompute relationship attachment points and hit bounds from the resulting geometry.

The directly containing boundary always encloses its enlarged direct children, because hulls are fitted around actual child shapes. Containment by **parent** boundaries remains best effort: the base specification rejects strict geometric containment at every depth, and residual internal overlap stays acceptable and is not an error. Enlargement does not change that contract.

If an outgoing relationship targets a group boundary, it still counts as one visible target and attaches to the refitted boundary.

### Interaction with pins and map separation

Enlargement can bring a node closer to a neighbor that cannot move. Existing rules win:

- collision spacing around an enlarged node is best effort, exactly like existing internal layout;
- a pinned node is never moved to make room for an enlarged neighbor, and a node is never shrunk below its computed scale to avoid contact;
- residual visual overlap involving a pinned node is accepted silently. It is normal internal geometry, not a conflict, so it does not use the map-tier overlap report or the Unpin offer, which stay reserved for map-level hull overlap;
- prominence never overrides the hard map-level separation tier or the per-particle displacement cap.

When `d` changes, the next immutable projection and geometry generation carries the new scale. Unaffected node positions and pins are retained under the existing stable-key and pin rules, and a prominence-only change must not reset the layout.

### Accessibility

Size alone must not be the only carrier of prominence, following the base specification's rule that meaning is never conveyed by one visual channel.

The node's accessible description states the **count** of distinct visible outgoing targets, after its label and owning map name, and only when that count is nonzero. Zero-reach nodes stay silent, so the common case adds no verbosity to keyboard traversal.

The scale factor is deliberately **not** announced. It is a rendering artifact of the curve, it is lossy above the cap because every node from 14 targets upward shares `1.75x`, and placing it in accessible text would make a later change to the curve silently change what users hear. Node extent already reaches assistive technology through normal accessible bounds. The same count is available in hover and inspection text.

This adds one requirement to the planned keyboard and accessibility work, whose virtual children already carry label, owning map, state, and available actions.

### Rejected alternatives

- **Percentile-relative sizing** was rejected because adding or removing an unrelated node would resize existing nodes and make size semantically unstable.
- **Fixed size tiers** were rejected because threshold crossings jump visibly and discard information between thresholds.
- **Counting addressable ancestors instead of visible boundaries** was rejected because identical-looking canvases would render different node sizes.

### Accepted limitation

An ancestor enclosure that is itself a relationship source receives no prominence, because boundaries are sized by fitting rather than by reach. Prominence describes projected graph nodes only. This is a deliberate limitation, not an oversight.

## 3. Data Flow And Component Responsibilities

Prominence is a pure function of the immutable `GraphProjection`. It is computed from already-consolidated projected edges, directional coverage, and projected endpoint identity. It must not traverse Freeplane models again, inspect hidden descendants, resolve map IDs, or invoke any content transformer.

Because geometry is computed from layout output while layout needs node extent, prominence must not sit between them. It is published once by projection and consumed independently:

1. projection consolidation determines visible targets, visible boundary identity, and directional coverage;
2. a pure calculation derives one count and one scale per visible projected node, keyed by `ProjectedNodeKey`, and publishes it as part of the immutable projection;
3. the layout adapter reads that size hint for separation behavior, expressed entirely behind `LayoutEngine` with no engine-specific type leaking out;
4. the geometry engine applies the scale to node shapes and refits hulls from the scaled shapes;
5. edge geometry attaches lines to the resulting node or boundary shape;
6. canvas painting, hit testing, search, keyboard traversal, and accessibility consume that one published geometry and never recompute size independently.

The calculation is `O(V + E)` over the projected graph, adds no particles or springs, and therefore does not invalidate the recorded GraphStream spike. It uses deterministic ordered collections and is a derived view property, never a new source of truth.

## 4. Testing And Performance Gates

Deterministic tests must cover:

- `A -> B` counting only `A`, and `A <-> B` counting both endpoints;
- undirected contributors and self-resolving contributors contributing zero;
- duplicate and parallel contributors to one target counting once;
- a visible group boundary counting once without inspecting descendants;
- two collapsed ancestors sharing one visible boundary counting once, while both remain independently addressable for relationship creation and deletion;
- an active Graph Group root being enlarged by its own outgoing reach;
- filtering, collapsing, hiding, and map removal resizing only affected sources;
- exact scale values at `d = 0, 1, 2, 4, 8, 13, 14+`, with monotonicity, finite output, and the `1.75x` cap;
- world-space scaling that survives zoom and applies at every rendering tier;
- propagation into node bounds, collision extent, hull extent, edge attachment, hit testing, and accessible bounds;
- an enlarged child remaining inside its directly containing refitted boundary with unchanged padding;
- no pinned node moving and no node shrinking because a neighbor grew, with residual overlap accepted and no map-tier conflict reported;
- unaffected node positions and pins staying stable across a prominence-only update;
- the nonzero target count appearing in the accessible description, the zero case staying silent, and the scale factor never appearing in accessible text;
- identical output for identical snapshots regardless of collection iteration order.

The existing engineering workload of 2,000 projected nodes and 5,000 projected edges must include prominence calculation, geometry recomputation, hit-index updates, and the first rendered frame, measured against the existing batch-to-first-frame budget. No separate pass may push the pipeline past that budget.

Test graphs must include a dense outgoing fan that reaches the cap, a mixed directed/bidirectional/undirected graph, duplicate contributors, a unary chain collapsed into one boundary, a target represented by a group boundary, and an update that removes that boundary. The dense fan must produce finite geometry with no boundary clipping of direct children.

## 5. Implementation Boundaries And Sequencing

This amendment is consumed by the already-planned projection, geometry, layout, canvas, interaction, and accessibility tasks. The implementation plan must assign responsibility without creating a parallel sizing system:

- projection owns visible target identity, directional coverage, and the published count and scale;
- geometry owns scale application to node shapes and hull refitting;
- layout consumes the size hint and preserves stable positions and pins;
- canvas, hit testing, search, keyboard traversal, and accessibility consume the one published geometry;
- no component may infer prominence from raw Freeplane links.

The implementation remains subject to the base specification's confidentiality requirements: unreachable nodes and hidden descendants must not affect labels, counts, geometry, accessible output, or inspection text.
