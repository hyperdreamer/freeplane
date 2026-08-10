# Domain Glossary

## Graph View

**Graph Workspace** - A saved collection of mind maps viewed together, plus relationships whose endpoints belong to different mind maps. Removing a mind map from a workspace does not delete or modify that mind map. Relationships involving an absent map remain dormant and reactivate if the same map is added again.

**Map connector** - A relationship between two nodes in the same mind map. The mind map owns this relationship.

**Graph relationship** - A relationship between nodes in different mind maps. The Graph Workspace owns this relationship.

**Unresolved graph relationship** - A dormant graph relationship whose map or node endpoint is unavailable. It becomes active again when both original endpoints resolve and is removed only by an explicit purge.

**Graph group** - A marked mind-map node whose complete subtree is treated as one graph node. The marked node is the group root and remains the stored endpoint of relationships involving the group. If its marker is removed, those relationships attach to the root's ancestor enclosure rather than being redirected to descendants. If graph groups are nested, only the outermost marked ancestor is active; inner markers remain and become active when no marked ancestor contains them.

**Structural leaf** - A mind-map node with no children in the map model. Folding, filtering, view roots, and editor visibility do not change whether a node is a structural leaf.

**Projected graph node** - A visible graph vertex representing either a structural leaf in an added mind map or an active graph group.

**Projected endpoint** - The visible attachment for one end of a relationship: a projected graph node for a leaf or active graph group, or the boundary of an ancestor enclosure for an ungrouped non-leaf node.

**Projected edge** - The single visible connection between two distinct projected endpoints. It can be derived from one or more map connectors or graph relationships. Connections that project to the same endpoint pair are consolidated; connections whose endpoints project to the same projected endpoint are omitted. Its arrowheads are the union of the directed contributors in each direction; nondirectional contributors add no arrowheads.

**Ancestor enclosure** - A labeled closed boundary that represents an ungrouped non-leaf mind-map node and contains its projected descendant nodes or nested ancestor enclosures. A relationship involving that ancestor attaches to the enclosure boundary. A consecutive chain of ancestors with only one projected child is represented by one boundary containing all chain labels in hierarchy order; branching ancestors retain separate boundaries.

**Map enclosure** - The topmost visible boundary of one added mind map. It is drawn in the emphatic tier so map identity outranks internal depth, and it keeps that tier when it collapses into a combined unary chain. With two or more added maps this is the map-root boundary; with exactly one added map the map-root boundary is suppressed and its first-level children form the emphatic tier instead. Every boundary nested inside an emphatic one is subtle, at any depth.
