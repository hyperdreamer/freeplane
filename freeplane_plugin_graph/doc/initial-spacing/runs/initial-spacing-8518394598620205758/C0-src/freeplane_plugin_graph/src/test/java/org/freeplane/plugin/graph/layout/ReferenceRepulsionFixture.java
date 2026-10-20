package org.freeplane.plugin.graph.layout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
}
