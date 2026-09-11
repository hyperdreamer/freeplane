package org.freeplane.plugin.graph.layout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.RelationshipResolution;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;

final class ReferenceRepulsionFixture {
    static final WorkspaceId WORKSPACE = WorkspaceId.of("00000000-0000-0000-0000-0000000000aa");
    static final MapReferenceId MAP = MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    static final double CHAR_WIDTH_UPPER_BOUND = 16.0;
    static final double CHAR_HEIGHT_UPPER_BOUND = 24.0;
    static final double BOUNDARY_PADDING = 8.0;
    static final double SIBLING_GAP = 8.0;
    static final double FRAME_CLEARANCE = 16.0;
    static final double MAX_RENDERED_NODE_RADIUS = 14.0;

    private static final String NODE_REGULARITY = "n1";
    private static final String NODE_REPLACEMENT = "n2";
    private static final String NODE_CHOICE = "n3";
    private static final String NODE_THEOREM = "n4";
    private static final String HULL_ROOT = "root";
    private static final String HULL_ZFC = "zfc";
    private static final String HULL_AXIOMS = "axioms";
    private static final String HULL_DEFINITIONS = "defs";

    private ReferenceRepulsionFixture() {
    }

    static ProjectedNodeKey regularityNode() {
        return nodeKey(NODE_REGULARITY);
    }

    static ProjectedNodeKey replacementNode() {
        return nodeKey(NODE_REPLACEMENT);
    }

    static ProjectedNodeKey choiceNode() {
        return nodeKey(NODE_CHOICE);
    }

    static ProjectedNodeKey theoremNode() {
        return nodeKey(NODE_THEOREM);
    }

    static EnclosureHullKey rootHull() {
        return hull(HULL_ROOT);
    }

    static EnclosureHullKey zfcHull() {
        return hull(HULL_ZFC);
    }

    static EnclosureHullKey axiomsHull() {
        return hull(HULL_AXIOMS);
    }

    static EnclosureHullKey definitionsHull() {
        return hull(HULL_DEFINITIONS);
    }

    static GraphProjection referenceProjection(long generation) {
        List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        enclosures.add(enclosure(HULL_ROOT, "Axiomatic Set Theory", Optional.<EnclosureHullKey>empty(),
            Collections.<ProjectedNodeKey>emptyList(), Collections.singletonList(zfcHull()), true,
            BoundaryTier.SUPPRESSED));
        enclosures.add(enclosure(HULL_ZFC, "ZFC", Optional.of(rootHull()),
            Collections.<ProjectedNodeKey>emptyList(), Arrays.asList(axiomsHull(), definitionsHull()), false,
            BoundaryTier.EMPHATIC));
        enclosures.add(enclosure(HULL_AXIOMS, "Axioms", Optional.of(zfcHull()),
            Arrays.asList(regularityNode(), replacementNode(), choiceNode()),
            Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.SUBTLE));
        enclosures.add(enclosure(HULL_DEFINITIONS, "Basic Definitions and Theorems", Optional.of(zfcHull()),
            Collections.singletonList(theoremNode()), Collections.<EnclosureHullKey>emptyList(), false,
            BoundaryTier.SUBTLE));
        return projection(generation, referenceNodes(), enclosures);
    }

    static GraphProjection reparentedProjection(long generation) {
        List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        enclosures.add(enclosure(HULL_ROOT, "Axiomatic Set Theory", Optional.<EnclosureHullKey>empty(),
            Collections.<ProjectedNodeKey>emptyList(), Collections.singletonList(zfcHull()), true,
            BoundaryTier.SUPPRESSED));
        enclosures.add(enclosure(HULL_ZFC, "ZFC", Optional.of(rootHull()),
            Collections.<ProjectedNodeKey>emptyList(), Collections.singletonList(axiomsHull()), false,
            BoundaryTier.EMPHATIC));
        enclosures.add(enclosure(HULL_AXIOMS, "Axioms", Optional.of(zfcHull()),
            Arrays.asList(regularityNode(), replacementNode(), choiceNode()),
            Collections.singletonList(definitionsHull()), false, BoundaryTier.SUBTLE));
        enclosures.add(enclosure(HULL_DEFINITIONS, "Basic Definitions and Theorems", Optional.of(axiomsHull()),
            Collections.singletonList(theoremNode()), Collections.<EnclosureHullKey>emptyList(), false,
            BoundaryTier.SUBTLE));
        return projection(generation, referenceNodes(), enclosures);
    }

    static double boundaryRadius(GraphProjection projection, EnclosureHullKey hull) {
        return new Sizes(projection).boundaryRadius(hull);
    }

    private static List<ProjectedNode> referenceNodes() {
        return Arrays.asList(node(NODE_REGULARITY, "Fundation / Regularity"),
            node(NODE_REPLACEMENT, "Replacement Scheme"), node(NODE_CHOICE, "Axiom of Choice"),
            node(NODE_THEOREM, "Theorem"));
    }

    private static ProjectedNode node(String id, String label) {
        return ProjectedNode.of(nodeKey(id), SafeNodeLabel.of(label, label), "M", true);
    }

    private static ProjectedEnclosure enclosure(String hullId, String label,
            Optional<EnclosureHullKey> parent, List<ProjectedNodeKey> directNodes,
            List<EnclosureHullKey> directEnclosures, boolean mapRoot, BoundaryTier tier) {
        EnclosureHullKey hull = hull(hullId);
        return ProjectedEnclosure.of(hull, Collections.singletonList(
            EnclosureKey.of(SourceNodeKey.persisted(NodeReference.of(MAP, PersistedNodeId.of(hullId))))),
            Collections.singletonList(SafeNodeLabel.of(label, label)), "M", parent, directNodes,
            directEnclosures, mapRoot, tier);
    }

    private static GraphProjection projection(long generation, List<ProjectedNode> nodes,
            List<ProjectedEnclosure> enclosures) {
        return GraphProjection.projected(generation, nodes, enclosures,
            Collections.<ProjectedEdge>emptyList(), Collections.<RelationshipResolution>emptyList(),
            Collections.<PinProjection>emptyList());
    }

    private static ProjectedNodeKey nodeKey(String id) {
        return ProjectedNodeKey.of(SourceNodeKey.persisted(NodeReference.of(MAP, PersistedNodeId.of(id))));
    }

    private static EnclosureHullKey hull(String id) {
        return EnclosureHullKey.of(Collections.singletonList(
            EnclosureKey.of(SourceNodeKey.persisted(NodeReference.of(MAP, PersistedNodeId.of(id))))));
    }

    private static final class Sizes {
        private final Map<ProjectedNodeKey, ProjectedNode> nodesByKey =
            new LinkedHashMap<ProjectedNodeKey, ProjectedNode>();
        private final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull =
            new LinkedHashMap<EnclosureHullKey, ProjectedEnclosure>();
        private final Map<EnclosureHullKey, double[]> sizes =
            new LinkedHashMap<EnclosureHullKey, double[]>();

        Sizes(GraphProjection projection) {
            for (ProjectedNode node : projection.nodes()) {
                nodesByKey.put(node.key(), node);
            }
            for (ProjectedEnclosure enclosure : projection.enclosures()) {
                enclosuresByHull.put(enclosure.hullKey(), enclosure);
            }
        }

        double boundaryRadius(EnclosureHullKey hull) {
            double[] size = sizeOf(hull);
            return 0.5 * Math.hypot(size[0], size[1]);
        }

        private double directNodeRingRadius(EnclosureHullKey hull) {
            ProjectedEnclosure enclosure = enclosuresByHull.get(hull);
            List<ProjectedNodeKey> directNodes = enclosure.directNodes();
            int count = directNodes.size();
            if (count <= 1) {
                return 0.0;
            }
            double maxWidth = 0.0;
            double maxHeight = 0.0;
            for (ProjectedNodeKey nodeKey : directNodes) {
                double[] box = nodeBox(nodesByKey.get(nodeKey));
                maxWidth = Math.max(maxWidth, box[0]);
                maxHeight = Math.max(maxHeight, box[1]);
            }
            return Math.hypot(maxWidth + SIBLING_GAP, maxHeight + SIBLING_GAP)
                / (2.0 * Math.sin(Math.PI / count));
        }

        private double directNodeReach(EnclosureHullKey hull) {
            ProjectedEnclosure enclosure = enclosuresByHull.get(hull);
            List<ProjectedNodeKey> directNodes = enclosure.directNodes();
            if (directNodes.isEmpty()) {
                return 0.0;
            }
            double maxNodeRadius = 0.0;
            for (ProjectedNodeKey nodeKey : directNodes) {
                double[] box = nodeBox(nodesByKey.get(nodeKey));
                maxNodeRadius = Math.max(maxNodeRadius, 0.5 * Math.hypot(box[0], box[1]));
            }
            return directNodeRingRadius(hull) + maxNodeRadius;
        }

        private double ringRadius(EnclosureHullKey hull) {
            ProjectedEnclosure enclosure = enclosuresByHull.get(hull);
            List<EnclosureHullKey> children = enclosure.directEnclosures();
            int count = children.size();
            if (count <= 1) {
                return 0.0;
            }
            double maxWidth = 0.0;
            double maxHeight = 0.0;
            for (EnclosureHullKey child : children) {
                double[] size = sizeOf(child);
                maxWidth = Math.max(maxWidth, size[0]);
                maxHeight = Math.max(maxHeight, size[1]);
            }
            return Math.hypot(maxWidth + SIBLING_GAP, maxHeight + SIBLING_GAP)
                / (2.0 * Math.sin(Math.PI / count));
        }

        private double childBoundaryReach(EnclosureHullKey hull) {
            ProjectedEnclosure enclosure = enclosuresByHull.get(hull);
            List<EnclosureHullKey> children = enclosure.directEnclosures();
            if (children.isEmpty()) {
                return 0.0;
            }
            double reach = 0.0;
            double radius = ringRadius(hull);
            for (EnclosureHullKey child : children) {
                reach = Math.max(reach, radius + reachOf(child));
            }
            return reach;
        }

        private double reachOf(EnclosureHullKey hull) {
            ProjectedEnclosure enclosure = enclosuresByHull.get(hull);
            if (enclosure.directNodes().isEmpty() && enclosure.directEnclosures().isEmpty()) {
                double[] size = sizeOf(hull);
                return 0.5 * Math.hypot(size[0], size[1]);
            }
            return Math.max(directNodeReach(hull), childBoundaryReach(hull));
        }

        private double[] sizeOf(EnclosureHullKey hull) {
            double[] cached = sizes.get(hull);
            if (cached != null) {
                return cached;
            }
            ProjectedEnclosure enclosure = enclosuresByHull.get(hull);
            double directReach = directNodeReach(hull);
            double childReach = childBoundaryReach(hull);
            double contentReach = Math.max(directReach, childReach);
            double[] size;
            if (contentReach == 0.0 && enclosure.directNodes().isEmpty()
                    && enclosure.directEnclosures().isEmpty()) {
                double width = 2.0 * BOUNDARY_PADDING;
                double height = CHAR_HEIGHT_UPPER_BOUND + 2.0 * BOUNDARY_PADDING;
                for (SafeNodeLabel label : enclosure.labels()) {
                    width = Math.max(width,
                        label.displayText().length() * CHAR_WIDTH_UPPER_BOUND + 2.0 * BOUNDARY_PADDING);
                }
                size = new double[] {width, height};
            }
            else {
                double side = 2.0 * (contentReach + FRAME_CLEARANCE);
                size = new double[] {side, side};
            }
            sizes.put(hull, size);
            return size;
        }

        private static double[] nodeBox(ProjectedNode node) {
            double width = Math.max(2.0 * MAX_RENDERED_NODE_RADIUS,
                node.label().displayText().length() * CHAR_WIDTH_UPPER_BOUND + 2.0 * BOUNDARY_PADDING);
            double height = Math.max(2.0 * MAX_RENDERED_NODE_RADIUS,
                CHAR_HEIGHT_UPPER_BOUND + 2.0 * BOUNDARY_PADDING);
            return new double[] {width, height};
        }
    }
}
