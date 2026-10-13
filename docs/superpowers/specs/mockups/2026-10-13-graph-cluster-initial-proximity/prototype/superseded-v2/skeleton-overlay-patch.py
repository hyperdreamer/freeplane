#!/usr/bin/env python3
"""Patch the three graphstream sources in /tmp/cluster-skeleton-proto/src to prototype
the design's tight footprints + V1 packing + packing skeleton + box separation."""
import os
import sys

BASE = "/tmp/cluster-skeleton-proto/src/org/freeplane/plugin/graph/layout/graphstream"


def patch(path, replacements):
    with open(path) as f:
        src = f.read()
    for old, new in replacements:
        if old not in src:
            print("MISSING in %s:\n%s" % (path, old[:200]))
            sys.exit(1)
        src = src.replace(old, new, 1)
    with open(path, "w") as f:
        f.write(src)
    print("patched", path)


# ---------------------------------------------------------------- engine
ENGINE = os.path.join(BASE, "GraphStreamLayoutEngine.java")
engine_old_imports = "import java.util.Collections;\n"
engine_new_imports = "import java.util.Collections;\nimport java.util.Comparator;\n"

engine_old_configure = """            springBox.configureParticle(desired.id, state.radius, state.pinned, desired.nodeKey == null,
                desired.parentAnchorId);
"""
engine_new_configure = engine_old_configure + """            if (desired.nodeKey == null && desired.anchorKey != null) {
                final BoundarySizes.Size anchorSize = sizes.sizeOf(desired.anchorKey);
                springBox.configureAnchorExtents(desired.id, anchorSize.width * 0.5, anchorSize.height * 0.5);
            }
"""

engine_old_hierarchy_block = """            if (enclosure.parentHull().isPresent()) {
                addHierarchyLink(result, hierarchyPairs, anchorIds.get(enclosure.parentHull().get()), anchor,
                    hierarchyRestLength(depths.get(enclosure.hullKey()).intValue()));
            }
            for (final EnclosureHullKey child : enclosure.directEnclosures()) {
                addHierarchyLink(result, hierarchyPairs, anchor, anchorIds.get(child),
                    hierarchyRestLength(depths.get(child).intValue()));
            }
        }
        return new Topology(desired, result);"""
engine_new_hierarchy_block = """            if (enclosure.parentHull().isPresent()) {
                addSkeletonLink(result, hierarchyPairs, sizes, anchorIds, enclosure.hullKey(),
                    enclosure.parentHull().get());
            }
        }
        addTopLevelChain(result, hierarchyPairs, sizes, anchorIds, projection);
        return new Topology(desired, result);"""

engine_old_rest = """    private static double hierarchyRestLength(final int childDepth) {
        return childDepth <= 1 ? GROUP_SPACING : SUB_GROUP_SPACING;
    }
"""
engine_new_rest = engine_old_rest + """
    private static void addSkeletonLink(final List<ForceLink> links, final Set<String> pairs,
            final BoundarySizes sizes, final Map<EnclosureHullKey, String> anchorIds,
            final EnclosureHullKey child, final EnclosureHullKey parent) {
        final List<ClusterPlacement> placements = sizes.placements(parent);
        int index = -1;
        for (int candidate = 0; candidate < placements.size(); candidate++) {
            if (child.equals(placements.get(candidate).item.anchorKey)) {
                index = candidate;
                break;
            }
        }
        if (index < 0) {
            return;
        }
        final ClusterPlacement placement = placements.get(index);
        final ClusterPlacement predecessor = index > 0 ? placements.get(index - 1) : null;
        final String predecessorId = predecessor == null || predecessor.item.anchorKey == null
            ? anchorIds.get(parent) : anchorIds.get(predecessor.item.anchorKey);
        final double rest = predecessor == null
            ? Math.hypot(placement.x, placement.y)
            : Math.hypot(placement.x - predecessor.x, placement.y - predecessor.y);
        addHierarchyLink(links, pairs, predecessorId, anchorIds.get(child), rest);
    }

    private static void addTopLevelChain(final List<ForceLink> links, final Set<String> pairs,
            final BoundarySizes sizes, final Map<EnclosureHullKey, String> anchorIds,
            final GraphProjection projection) {
        final List<ClusterPlacement> placements = sizes.topLevelPlacements();
        for (int index = 1; index < placements.size(); index++) {
            final ClusterPlacement previous = placements.get(index - 1);
            final ClusterPlacement current = placements.get(index);
            final double rest = Math.hypot(current.x - previous.x, current.y - previous.y);
            addHierarchyLink(links, pairs, anchorIds.get(previous.item.anchorKey),
                anchorIds.get(current.item.anchorKey), rest);
        }
    }

    static final class ClusterItem {
        final EnclosureHullKey anchorKey;
        final double width;
        final double height;
        final int order;

        ClusterItem(final EnclosureHullKey anchorKey, final double width, final double height,
                final int order) {
            this.anchorKey = anchorKey;
            this.width = width;
            this.height = height;
            this.order = order;
        }
    }

    static final class ClusterPlacement {
        final ClusterItem item;
        final double x;
        final double y;

        ClusterPlacement(final ClusterItem item, final double x, final double y) {
            this.item = item;
            this.x = x;
            this.y = y;
        }
    }

    static List<ClusterPlacement> packItems(final List<ClusterItem> items, final double gap) {
        final List<ClusterItem> ordered = new ArrayList<ClusterItem>(items);
        Collections.sort(ordered, new Comparator<ClusterItem>() {
            @Override
            public int compare(final ClusterItem first, final ClusterItem second) {
                if ((first.anchorKey == null) != (second.anchorKey == null)) {
                    return first.anchorKey == null ? -1 : 1;
                }
                final int byArea = Double.compare(second.width * second.height,
                    first.width * first.height);
                if (byArea != 0) {
                    return byArea;
                }
                return Integer.compare(first.order, second.order);
            }
        });
        final List<ClusterPlacement> placed = new ArrayList<ClusterPlacement>();
        for (final ClusterItem item : ordered) {
            double bestX = 0.0;
            double bestY = 0.0;
            double bestArea = Double.POSITIVE_INFINITY;
            double bestRadius = Double.POSITIVE_INFINITY;
            int bestAngle = Integer.MAX_VALUE;
            boolean found = false;
            final double stepRadius = 4.0;
            final int angleStep = 2;
            for (double radius = 0.0; radius <= 4000.0; radius += stepRadius) {
                final int angleCount = radius == 0.0 ? 1 : 360 / angleStep;
                for (int angleIndex = 0; angleIndex < angleCount; angleIndex++) {
                    final double angle = 2.0 * Math.PI * angleIndex * angleStep / 360.0;
                    final double x = radius * Math.cos(angle);
                    final double y = radius * Math.sin(angle);
                    if (!clears(x, y, item, placed, gap)) {
                        continue;
                    }
                    double minX = x - item.width * 0.5;
                    double maxX = x + item.width * 0.5;
                    double minY = y - item.height * 0.5;
                    double maxY = y + item.height * 0.5;
                    for (final ClusterPlacement other : placed) {
                        minX = Math.min(minX, other.x - other.item.width * 0.5);
                        maxX = Math.max(maxX, other.x + other.item.width * 0.5);
                        minY = Math.min(minY, other.y - other.item.height * 0.5);
                        maxY = Math.max(maxY, other.y + other.item.height * 0.5);
                    }
                    final double area = (maxX - minX) * (maxY - minY);
                    final boolean better = !found
                        || area < bestArea - 1e-9
                        || (Math.abs(area - bestArea) <= 1e-9
                            && (radius < bestRadius - 1e-9
                                || (Math.abs(radius - bestRadius) <= 1e-9 && angleIndex < bestAngle)));
                    if (better) {
                        found = true;
                        bestX = x;
                        bestY = y;
                        bestArea = area;
                        bestRadius = radius;
                        bestAngle = angleIndex;
                    }
                }
            }
            if (!found) {
                throw new IllegalStateException("Cluster packing found no free slot");
            }
            placed.add(new ClusterPlacement(item, bestX, bestY));
        }
        if (placed.isEmpty()) {
            return placed;
        }
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (final ClusterPlacement placement : placed) {
            minX = Math.min(minX, placement.x - placement.item.width * 0.5);
            maxX = Math.max(maxX, placement.x + placement.item.width * 0.5);
            minY = Math.min(minY, placement.y - placement.item.height * 0.5);
            maxY = Math.max(maxY, placement.y + placement.item.height * 0.5);
        }
        final double offsetX = (minX + maxX) * 0.5;
        final double offsetY = (minY + maxY) * 0.5;
        final List<ClusterPlacement> centred = new ArrayList<ClusterPlacement>(placed.size());
        for (final ClusterPlacement placement : placed) {
            centred.add(new ClusterPlacement(placement.item, placement.x - offsetX,
                placement.y - offsetY));
        }
        return centred;
    }

    private static boolean clears(final double x, final double y, final ClusterItem item,
            final List<ClusterPlacement> placed, final double gap) {
        for (final ClusterPlacement other : placed) {
            final double dx = Math.abs(x - other.x);
            final double dy = Math.abs(y - other.y);
            if (dx < (item.width + other.item.width) * 0.5 + gap
                    && dy < (item.height + other.item.height) * 0.5 + gap) {
                return false;
            }
        }
        return true;
    }
"""

engine_old_margin_const = """        static final double SIBLING_GAP = 8.0;"""
engine_new_margin_const = """        static final double SIBLING_GAP = 8.0;
        static final double NODE_SETTLE_MARGIN = 12.0;"""

engine_old_boundary_radius = """        double boundaryRadius(final EnclosureHullKey key) {
            final Size size = sizeOf(key);
            return 0.5 * Math.hypot(size.width, size.height);
        }
"""
engine_new_boundary_radius = engine_old_boundary_radius + """
        private final Map<EnclosureHullKey, List<ClusterPlacement>> packedPlacements =
            new LinkedHashMap<EnclosureHullKey, List<ClusterPlacement>>();
        private List<ClusterPlacement> topLevelPlacements;

        List<ClusterPlacement> placements(final EnclosureHullKey key) {
            final List<ClusterPlacement> cached = packedPlacements.get(key);
            if (cached != null) {
                return cached;
            }
            final ProjectedEnclosure enclosure = enclosuresByHull.get(key);
            final List<ClusterItem> items = new ArrayList<ClusterItem>();
            if (!enclosure.directNodes().isEmpty()) {
                final double ring = contentRingRadius(key);
                double maxDisc = 0.0;
                for (final ProjectedNodeKey nodeKey : enclosure.directNodes()) {
                    maxDisc = Math.max(maxDisc, discRadius(nodeKey));
                }
                final double half = ring + maxDisc + NODE_SETTLE_MARGIN;
                items.add(new ClusterItem(null, 2.0 * half, 2.0 * half, 0));
            }
            int order = 0;
            for (final EnclosureHullKey child : enclosure.directEnclosures()) {
                final Size childSize = sizeOf(child);
                items.add(new ClusterItem(child, childSize.width, childSize.height, order++));
            }
            final List<ClusterPlacement> result = items.isEmpty()
                ? Collections.<ClusterPlacement>emptyList() : packItems(items, SIBLING_GAP);
            packedPlacements.put(key, result);
            return result;
        }

        ClusterPlacement contentPlacement(final EnclosureHullKey key) {
            for (final ClusterPlacement placement : placements(key)) {
                if (placement.item.anchorKey == null) {
                    return placement;
                }
            }
            return null;
        }

        ClusterPlacement placementOf(final EnclosureHullKey parentKey, final EnclosureHullKey childKey) {
            for (final ClusterPlacement placement : placements(parentKey)) {
                if (childKey.equals(placement.item.anchorKey)) {
                    return placement;
                }
            }
            return null;
        }

        List<ClusterPlacement> topLevelPlacements() {
            if (topLevelPlacements != null) {
                return topLevelPlacements;
            }
            final List<ClusterItem> items = new ArrayList<ClusterItem>();
            int order = 0;
            for (final ProjectedEnclosure enclosure : enclosuresByHull.values()) {
                if (enclosure.mapRoot() || !enclosure.parentHull().isPresent()) {
                    final Size size = sizeOf(enclosure.hullKey());
                    items.add(new ClusterItem(enclosure.hullKey(), size.width, size.height, order++));
                }
            }
            topLevelPlacements = packItems(items, SIBLING_GAP);
            return topLevelPlacements;
        }

        ClusterPlacement topLevelPlacementOf(final EnclosureHullKey key) {
            for (final ClusterPlacement placement : topLevelPlacements()) {
                if (key.equals(placement.item.anchorKey)) {
                    return placement;
                }
            }
            return null;
        }
"""

engine_old_sizeof = """        Size sizeOf(final EnclosureHullKey key) {
            final Size cached = sizes.get(key);
            if (cached != null) {
                return cached;
            }
            final ProjectedEnclosure enclosure = enclosuresByHull.get(key);
            final Size size;
            final double directReach = directNodeReach(key);
            final double childReach = childBoundaryReach(key);
            final double contentReach = Math.max(directReach, childReach);
            if (contentReach == 0.0 && enclosure.directNodes().isEmpty() && enclosure.directEnclosures().isEmpty()) {
                double width = 2.0 * BOUNDARY_PADDING;
                double height = CHAR_HEIGHT_UPPER_BOUND + 2.0 * BOUNDARY_PADDING;
                for (final SafeNodeLabel label : enclosure.labels()) {
                    width = Math.max(width,
                        label.displayText().length() * CHAR_WIDTH_UPPER_BOUND + 2.0 * BOUNDARY_PADDING);
                }
                size = new Size(width, height);
            }
            else {
                size = new Size(2.0 * (contentReach + FRAME_CLEARANCE), 2.0 * (contentReach + FRAME_CLEARANCE));
            }
            sizes.put(key, size);
            return size;
        }
"""
engine_new_sizeof = """        Size sizeOf(final EnclosureHullKey key) {
            final Size cached = sizes.get(key);
            if (cached != null) {
                return cached;
            }
            final ProjectedEnclosure enclosure = enclosuresByHull.get(key);
            final Size size;
            if (enclosure.directNodes().isEmpty() && enclosure.directEnclosures().isEmpty()) {
                double width = 2.0 * BOUNDARY_PADDING;
                double height = CHAR_HEIGHT_UPPER_BOUND + 2.0 * BOUNDARY_PADDING;
                for (final SafeNodeLabel label : enclosure.labels()) {
                    width = Math.max(width,
                        label.displayText().length() * CHAR_WIDTH_UPPER_BOUND + 2.0 * BOUNDARY_PADDING);
                }
                size = new Size(width, height);
            }
            else {
                final List<ClusterPlacement> placements = placements(key);
                double minX = Double.POSITIVE_INFINITY;
                double maxX = Double.NEGATIVE_INFINITY;
                double minY = Double.POSITIVE_INFINITY;
                double maxY = Double.NEGATIVE_INFINITY;
                for (final ClusterPlacement placement : placements) {
                    minX = Math.min(minX, placement.x - placement.item.width * 0.5);
                    maxX = Math.max(maxX, placement.x + placement.item.width * 0.5);
                    minY = Math.min(minY, placement.y - placement.item.height * 0.5);
                    maxY = Math.max(maxY, placement.y + placement.item.height * 0.5);
                }
                size = new Size(maxX - minX + 2.0 * FRAME_CLEARANCE,
                    maxY - minY + 2.0 * FRAME_CLEARANCE);
            }
            sizes.put(key, size);
            return size;
        }
"""

engine_old_center = """        private Position center(final EnclosureHullKey key) {
            final Position cached = centers.get(key);
            if (cached != null) {
                return cached;
            }
            final Position center;
            if (isTopLevel(key)) {
                center = topRingPosition(key);
            }
            else {
                final ProjectedEnclosure enclosure = sizes.enclosure(key);
                final EnclosureHullKey parentKey = enclosure.parentHull().get();
                final Position parentCenter = center(parentKey);
                final int index = sizes.enclosure(parentKey).directEnclosures().indexOf(key);
                final int count = sizes.enclosure(parentKey).directEnclosures().size();
                final double radius = sizes.ringRadius(parentKey);
                final double angle = 2.0 * Math.PI * Math.max(0, index) / Math.max(1, count);
                center = new Position(parentCenter.x + radius * Math.cos(angle),
                    parentCenter.y + radius * Math.sin(angle));
            }
            centers.put(key, center);
            return center;
        }
"""
engine_new_center = """        private Position center(final EnclosureHullKey key) {
            final Position cached = centers.get(key);
            if (cached != null) {
                return cached;
            }
            final Position center;
            if (isTopLevel(key)) {
                final ClusterPlacement placement = sizes.topLevelPlacementOf(key);
                center = placement == null ? new Position(0.0, 0.0)
                    : new Position(placement.x, placement.y);
            }
            else {
                final ProjectedEnclosure enclosure = sizes.enclosure(key);
                final EnclosureHullKey parentKey = enclosure.parentHull().get();
                final Position parentCenter = center(parentKey);
                final ClusterPlacement placement = sizes.placementOf(parentKey, key);
                center = placement == null ? parentCenter
                    : new Position(parentCenter.x + placement.x, parentCenter.y + placement.y);
            }
            centers.put(key, center);
            return center;
        }
"""

engine_old_node_position = """        private Position nodePosition(final ProjectedNodeKey nodeKey) {
            final Position cached = nodePositions.get(nodeKey);
            if (cached != null) {
                return cached;
            }
            final EnclosureHullKey parentKey = parentEnclosureOfNode.get(nodeKey);
            final Position center = parentKey != null ? center(parentKey) : new Position(0.0, 0.0);
            final Position pos;
            if (parentKey != null) {
                final ProjectedEnclosure parent = sizes.enclosure(parentKey);
                final int index = parent.directNodes().indexOf(nodeKey);
                final int count = parent.directNodes().size();
                final double radius = sizes.contentRingRadius(parentKey);
                final double angle = 2.0 * Math.PI * Math.max(0, index) / Math.max(1, count);
                pos = new Position(center.x + radius * Math.cos(angle), center.y + radius * Math.sin(angle));
            }
            else {
                pos = center;
            }
            nodePositions.put(nodeKey, pos);
            return pos;
        }
"""
engine_new_node_position = """        private Position nodePosition(final ProjectedNodeKey nodeKey) {
            final Position cached = nodePositions.get(nodeKey);
            if (cached != null) {
                return cached;
            }
            final EnclosureHullKey parentKey = parentEnclosureOfNode.get(nodeKey);
            final Position pos;
            if (parentKey != null) {
                final Position parentCenter = center(parentKey);
                final ClusterPlacement content = sizes.contentPlacement(parentKey);
                final double centerX = parentCenter.x + (content == null ? 0.0 : content.x);
                final double centerY = parentCenter.y + (content == null ? 0.0 : content.y);
                final ProjectedEnclosure parent = sizes.enclosure(parentKey);
                final int index = parent.directNodes().indexOf(nodeKey);
                final int count = parent.directNodes().size();
                final double radius = sizes.contentRingRadius(parentKey);
                final double angle = 2.0 * Math.PI * Math.max(0, index) / Math.max(1, count);
                pos = new Position(centerX + radius * Math.cos(angle), centerY + radius * Math.sin(angle));
            }
            else {
                pos = new Position(0.0, 0.0);
            }
            nodePositions.put(nodeKey, pos);
            return pos;
        }
"""

patch(ENGINE, [
    (engine_old_imports, engine_new_imports),
    (engine_old_configure, engine_new_configure),
    (engine_old_hierarchy_block, engine_new_hierarchy_block),
    (engine_old_rest, engine_new_rest),
    (engine_old_margin_const, engine_new_margin_const),
    (engine_old_boundary_radius, engine_new_boundary_radius),
    (engine_old_sizeof, engine_new_sizeof),
    (engine_old_center, engine_new_center),
    (engine_old_node_position, engine_new_node_position),
])

# ------------------------------------------------------------ particle
PARTICLE = os.path.join(BASE, "TypedNodeParticle.java")
particle_old = """    double boundaryRadius() {
        return separationRadius;
    }
"""
particle_new = particle_old + """
    private double separationHalfWidth = -1.0;
    private double separationHalfHeight = -1.0;

    void configureExtents(final double halfWidth, final double halfHeight) {
        separationHalfWidth = halfWidth;
        separationHalfHeight = halfHeight;
    }

    double separationHalfWidth() {
        return separationHalfWidth;
    }

    double separationHalfHeight() {
        return separationHalfHeight;
    }
"""
patch(PARTICLE, [(particle_old, particle_new)])

# -------------------------------------------------------------- springbox
BOX = os.path.join(BASE, "TypedSpringBox.java")
box_old_configure = """    void configureParticle(final String id, final double radius, final boolean pinned, final boolean anchor,
            final String parentAnchorId) {
        parentOf.put(id, parentAnchorId);
        anchorFlags.put(id, Boolean.valueOf(anchor));
        final TypedNodeParticle particle = typedParticles.get(id);
        if (particle != null) {
            particle.configure(radius, pinned);
            freezeNode(id, pinned);
        }
    }
"""
box_new_configure = box_old_configure + """
    void configureAnchorExtents(final String id, final double halfWidth, final double halfHeight) {
        final TypedNodeParticle particle = typedParticles.get(id);
        if (particle != null) {
            particle.configureExtents(halfWidth, halfHeight);
        }
    }
"""

box_old_anchor_check = """        final Boolean isAnchor = anchorFlags.get(particle.getId().toString());
        if (isAnchor == null || !isAnchor.booleanValue()) {
            return;
        }
"""
box_new_anchor_check = box_old_anchor_check + """        if ("none".equals(System.getProperty("proto.boundary", "box"))) {
            return;
        }
"""

box_old_circle = """            final Point3 own = particle.getPosition();
            final Point3 position = other.getPosition();
            double dx = own.x - position.x;
            double dy = own.y - position.y;
"""
box_new_circle = """            final Point3 own = particle.getPosition();
            final Point3 position = other.getPosition();
            if ("box".equals(System.getProperty("proto.boundary", "box"))
                    && particle.separationHalfWidth() > 0.0 && other.separationHalfWidth() > 0.0) {
                final double gapX = Math.abs(own.x - position.x)
                    - (particle.separationHalfWidth() + other.separationHalfWidth()
                        + GraphStreamLayoutEngine.BoundarySizes.SIBLING_GAP);
                final double gapY = Math.abs(own.y - position.y)
                    - (particle.separationHalfHeight() + other.separationHalfHeight()
                        + GraphStreamLayoutEngine.BoundarySizes.SIBLING_GAP);
                if (gapX >= 0.0 || gapY >= 0.0) {
                    continue;
                }
                if (-gapX <= -gapY) {
                    displacement.set(0, displacement.at(0) + (own.x >= position.x ? 1.0 : -1.0)
                        * (-gapX) * BOUNDARY_REPULSION_FACTOR);
                }
                else {
                    displacement.set(1, displacement.at(1) + (own.y >= position.y ? 1.0 : -1.0)
                        * (-gapY) * BOUNDARY_REPULSION_FACTOR);
                }
                continue;
            }
            double dx = own.x - position.x;
            double dy = own.y - position.y;
"""

box_old_multiplier = """        if (kind == GraphStreamLayoutEngine.ForceKind.HIERARCHY) {
            return calibration.hierarchy();
        }"""
box_new_multiplier = """        if (kind == GraphStreamLayoutEngine.ForceKind.HIERARCHY) {
            return Double.parseDouble(System.getProperty("proto.packingMultiplier",
                Double.toString(calibration.hierarchy())));
        }"""

patch(BOX, [
    (box_old_configure, box_new_configure),
    (box_old_anchor_check, box_new_anchor_check),
    (box_old_circle, box_new_circle),
    (box_old_multiplier, box_new_multiplier),
])

print("all patches applied")
