package org.freeplane.plugin.graph.layout.graphstream;

import java.lang.reflect.Method;
import java.util.*;
import org.freeplane.plugin.graph.layout.*;
import org.freeplane.plugin.graph.projection.*;
import org.freeplane.plugin.graph.projection.input.*;
import org.freeplane.plugin.graph.workspace.model.*;

final class InitialSpacingFixtures {
    static final WorkspaceId WORKSPACE = WorkspaceId.of("00000000-0000-0000-0000-0000000000aa");
    static final MapReferenceId A = MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    static final MapReferenceId B = MapReferenceId.of("00000000-0000-0000-0000-000000000002");

    static GraphProjection reference(long generation) throws Exception {
        return invoke("org.freeplane.plugin.graph.layout.ReferenceRepulsionFixture", "referenceProjection", generation);
    }

    static GraphProjection reparented(long generation) throws Exception {
        return invoke("org.freeplane.plugin.graph.layout.ReferenceRepulsionFixture", "reparentedProjection", generation);
    }

    static GraphProjection cross(long generation) throws Exception {
        return invoke("org.freeplane.plugin.graph.layout.TypedForcesShould", "twoMapProjection", generation);
    }

    private static GraphProjection invoke(String owner, String name, long generation) throws Exception {
        Method method = Class.forName(owner).getDeclaredMethod(name, long.class);
        method.setAccessible(true);
        return (GraphProjection) method.invoke(null, generation);
    }

    static SourceNodeKey source(MapReferenceId map, String id) {
        return SourceNodeKey.persisted(NodeReference.of(map, PersistedNodeId.of(id)));
    }

    static ProjectedNodeKey key(MapReferenceId map, String id) {
        return ProjectedNodeKey.of(source(map, id));
    }

    static EnclosureHullKey hull(MapReferenceId map, String id) {
        return EnclosureHullKey.of(Collections.singletonList(EnclosureKey.of(source(map, id))));
    }

    static ProjectedEnclosure enclosure(MapReferenceId map, String id, String label,
            Optional<EnclosureHullKey> parent, List<ProjectedNodeKey> direct,
            List<EnclosureHullKey> children, boolean root, BoundaryTier tier) {
        return ProjectedEnclosure.of(hull(map, id), Collections.singletonList(EnclosureKey.of(source(map, id))),
            Collections.singletonList(SafeNodeLabel.of(label, label)), "Map", parent, direct, children, root, tier);
    }

    static GraphProjection projection(long generation, List<ProjectedNode> nodes,
            List<ProjectedEnclosure> enclosures, List<ProjectedEdge> edges, List<PinProjection> pins) {
        return GraphProjection.projected(generation, nodes, enclosures, edges,
            Collections.<RelationshipResolution>emptyList(), pins);
    }

    static GraphProjection single(int count, long generation, boolean mixed, List<PinProjection> pins) {
        List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        List<ProjectedNodeKey> keys = new ArrayList<ProjectedNodeKey>();
        for (int i = 0; i < count; i++) {
            ProjectedNodeKey key = key(A, "n" + i);
            keys.add(key);
            nodes.add(ProjectedNode.of(key, SafeNodeLabel.of("Node " + i, "Node " + i), "Map", true));
        }
        List<ProjectedEnclosure> enclosures = Arrays.asList(
            enclosure(A, "sub", "Sub", Optional.of(hull(A, "root")), keys,
                Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.SUBTLE),
            enclosure(A, "root", "Root", Optional.<EnclosureHullKey>empty(),
                Collections.<ProjectedNodeKey>emptyList(), Collections.singletonList(hull(A, "sub")),
                true, BoundaryTier.EMPHATIC));
        List<ProjectedEdge> edges = new ArrayList<ProjectedEdge>();
        if (count >= 4) {
            if (mixed) {
                edges.add(nativeEdge(0, endpoint(keys.get(2)), endpoint(keys.get(0)), true));
                edges.add(nativeEdge(1, endpoint(keys.get(2)), endpoint(keys.get(1)), true));
            }
            else {
                edges.add(nativeEdge(0, endpoint(keys.get(0)), endpoint(keys.get(1)), false));
                edges.add(nativeEdge(0, endpoint(keys.get(2)), endpoint(keys.get(3)), false));
            }
        }
        return projection(generation, nodes, enclosures, edges, pins);
    }

    static GraphProjection root(int count, long generation) {
        GraphProjection p = single(count, generation, false, Collections.<PinProjection>emptyList());
        return projection(generation, p.nodes(), Collections.singletonList(enclosure(A, "root", "Root",
            Optional.<EnclosureHullKey>empty(), p.enclosures().get(0).directNodes(),
            Collections.<EnclosureHullKey>emptyList(), true, BoundaryTier.EMPHATIC)), p.edges(), p.pins());
    }

    static GraphProjection named(String name) throws Exception {
        if (name.equals("two")) return root(2, 1L);
        if (name.equals("reference")) return reference(1L);
        if (name.equals("S1")) return single(4, 1L, false, Collections.<PinProjection>emptyList());
        if (name.equals("cross")) return cross(1L);
        if (name.equals("mixed")) return single(4, 1L, true, Collections.<PinProjection>emptyList());
        String[] parts = name.split("-");
        if (parts.length == 3 && (parts[0].equals("matrix") || parts[0].equals("endpoints"))) {
            return matrix(parts[1].equals("cross"), parts[2], false);
        }
        throw new IllegalArgumentException("Unknown fixture " + name);
    }

    static ProjectedEndpointKey endpoint(ProjectedNodeKey key) {
        return ProjectedEndpointKey.ofNode(key);
    }

    static SourceNodeKey source(ProjectedEndpointKey endpoint) {
        return endpoint.isNode() ? endpoint.node().get().source() : endpoint.enclosure().get().source();
    }

    static ProjectedEdge nativeEdge(int occurrence, ProjectedEndpointKey first,
            ProjectedEndpointKey second, boolean targetArrow) {
        ConnectorDescriptor descriptor = ConnectorDescriptor.of(source(first),
            source(second).persistedReference().get(), false, targetArrow, "source", "middle", "target");
        return ProjectedEdge.of(ProjectedEdgeKey.of(first, second), Collections.singletonList(
            EdgeContributor.nativeConnector(ConnectorSnapshot.of(occurrence, descriptor), first, second)));
    }

    static GraphProjection matrix(boolean crossMap, String pair, boolean reverse) {
        MapReferenceId secondMap = crossMap ? B : A;
        List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        for (int side = 0; side < 2; side++) {
            MapReferenceId map = side == 0 ? A : secondMap;
            String prefix = side == 0 ? "left" : "right";
            List<ProjectedNodeKey> direct = new ArrayList<ProjectedNodeKey>();
            for (int i = 0; i < 4; i++) {
                ProjectedNodeKey key = key(map, prefix + i);
                direct.add(key);
                nodes.add(ProjectedNode.of(key, SafeNodeLabel.of("Node " + i, "Node " + i), "Map", true));
            }
            enclosures.add(enclosure(map, prefix, prefix, Optional.<EnclosureHullKey>empty(), direct,
                Collections.<EnclosureHullKey>emptyList(), true, BoundaryTier.SUBTLE));
        }
        ProjectedEndpointKey first = pair.equals("ee")
            ? ProjectedEndpointKey.ofEnclosure(EnclosureKey.of(source(A, "left"))) : endpoint(key(A, "left0"));
        ProjectedEndpointKey second = pair.equals("nn") ? endpoint(key(secondMap, "right0"))
            : ProjectedEndpointKey.ofEnclosure(EnclosureKey.of(source(secondMap, "right")));
        if (reverse) {
            ProjectedEndpointKey saved = first;
            first = second;
            second = saved;
        }
        ProjectedEdge edge;
        if (crossMap) {
            GraphRelationshipRecord record = GraphRelationshipRecord.of(
                RelationshipId.of("30000000-0000-0000-0000-000000000001"), 1L,
                source(first).persistedReference().get(), source(second).persistedReference().get(),
                RelationshipDirection.FORWARD, Collections.<UnknownXml>emptyList());
            edge = ProjectedEdge.of(ProjectedEdgeKey.of(first, second), Collections.singletonList(
                EdgeContributor.graphRelationship(record, first, second)));
        }
        else edge = nativeEdge(0, first, second, false);
        return projection(1L, nodes, enclosures, Collections.singletonList(edge), Collections.<PinProjection>emptyList());
    }

    static PinProjection pin(ProjectedNodeKey key, double x, double y) {
        return PinProjection.active(PinRecord.of(key.source().persistedReference().get(), x, y,
            Collections.<UnknownXml>emptyList()), key);
    }

    static LayoutRequest request(GraphProjection before, GraphProjection after) {
        return LayoutRequest.of(WORKSPACE, after, ProjectionDiff.between(before, after), after.pins());
    }
}
