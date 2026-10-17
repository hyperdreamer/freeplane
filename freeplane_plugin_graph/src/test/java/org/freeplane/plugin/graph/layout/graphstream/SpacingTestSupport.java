package org.freeplane.plugin.graph.layout.graphstream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import java.lang.reflect.Field;
import java.util.*;
import org.freeplane.plugin.graph.geometry.*;
import org.freeplane.plugin.graph.layout.*;
import org.freeplane.plugin.graph.projection.*;
import org.freeplane.plugin.graph.projection.input.*;
import org.freeplane.plugin.graph.workspace.model.*;

public final class SpacingTestSupport {
    public static final MapReferenceId A = MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    public static final MapReferenceId B = MapReferenceId.of("00000000-0000-0000-0000-000000000002");
    public static final WorkspaceId WORKSPACE = WorkspaceId.of("00000000-0000-0000-0000-000000001604");
    private SpacingTestSupport() { }
    public static Object read(Object object, String name) {
        try {
            Field field = object.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(object);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(name, failure); }
    }
    public static void write(Object object, String name, Object value) {
        try {
            Field field = object.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(object, value);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(name, failure); }
    }
    public static NodeReference ref(MapReferenceId map, String id) {
        return NodeReference.of(map, PersistedNodeId.of(id));
    }
    public static ProjectedNodeKey node(MapReferenceId map, String id) {
        return ProjectedNodeKey.of(SourceNodeKey.persisted(ref(map, id)));
    }
    public static EnclosureHullKey hull(MapReferenceId map, String id) {
        return EnclosureHullKey.of(Collections.singletonList(
            EnclosureKey.of(SourceNodeKey.persisted(ref(map, id)))));
    }
    public static ProjectedEnclosure enclosure(MapReferenceId map, String id,
            EnclosureHullKey parent, List<ProjectedNodeKey> nodes, List<EnclosureHullKey> children) {
        EnclosureHullKey key = hull(map, id);
        return ProjectedEnclosure.of(key, key.endpointKeys(), Collections.singletonList(SafeNodeLabel.of(id, id)),
            map.equals(A) ? "Map A" : "Map B", Optional.ofNullable(parent), nodes, children,
            parent == null, parent == null ? BoundaryTier.EMPHATIC : BoundaryTier.SUBTLE);
    }
    public static GraphProjection projection(long generation, List<ProjectedNode> nodes,
            List<ProjectedEnclosure> hulls, List<ProjectedEdge> edges, List<PinProjection> pins) {
        return GraphProjection.projected(generation, nodes, hulls, edges,
            Collections.<RelationshipResolution>emptyList(), pins);
    }
    public static GraphProjection empty(long generation) {
        return projection(generation, Collections.<ProjectedNode>emptyList(),
            Collections.<ProjectedEnclosure>emptyList(), Collections.<ProjectedEdge>emptyList(),
            Collections.<PinProjection>emptyList());
    }
    public static GraphProjection ring(long generation, int count, boolean mixed) {
        List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        List<ProjectedNodeKey> direct = new ArrayList<ProjectedNodeKey>();
        for (int i = 0; i < count; i++) {
            String id = mixed ? "m" + i : "u" + i;
            ProjectedNodeKey key = node(A, id);
            direct.add(key);
            nodes.add(ProjectedNode.of(key, SafeNodeLabel.of(id, id), "Map A", true));
        }
        List<ProjectedEdge> edges = new ArrayList<ProjectedEdge>();
        if (mixed) {
            edges.add(edge(ProjectedEndpointKey.ofNode(direct.get(0)), ProjectedEndpointKey.ofNode(direct.get(1)), true, 16041));
            edges.add(edge(ProjectedEndpointKey.ofNode(direct.get(0)), ProjectedEndpointKey.ofNode(direct.get(2)), true, 16042));
        }
        return projection(generation, nodes, Collections.singletonList(enclosure(A,
            mixed ? "mixed-root" : "ring-root", null, direct, Collections.<EnclosureHullKey>emptyList())),
            edges, Collections.<PinProjection>emptyList());
    }
    public static SourceNodeKey source(ProjectedEndpointKey endpoint) {
        return endpoint.isNode() ? endpoint.node().get().source() : endpoint.enclosure().get().source();
    }
    public static ProjectedEdge edge(ProjectedEndpointKey from, ProjectedEndpointKey to,
            boolean forward, int sequence) {
        EdgeContributor contributor;
        if (from.mapReferenceId().equals(to.mapReferenceId())) {
            ConnectorDescriptor descriptor = ConnectorDescriptor.of(source(from),
                source(to).persistedReference().get(), false, forward, "", "", "");
            contributor = EdgeContributor.nativeConnector(ConnectorSnapshot.of(0, descriptor), from, to);
        } else {
            GraphRelationshipRecord record = GraphRelationshipRecord.of(RelationshipId.of(String.format(Locale.ROOT,
                "00000000-0000-0000-0000-%012d", sequence)), sequence - 16040L,
                source(from).persistedReference().get(), source(to).persistedReference().get(),
                forward ? RelationshipDirection.FORWARD : RelationshipDirection.UNDIRECTED,
                Collections.<UnknownXml>emptyList());
            contributor = EdgeContributor.graphRelationship(record, from, to);
        }
        return ProjectedEdge.of(ProjectedEdgeKey.of(from, to), Collections.singletonList(contributor));
    }
    public static GraphProjection targets(boolean crossMap) {
        MapReferenceId rightMap = crossMap ? B : A;
        EnclosureHullKey root = hull(A, "target-root"), left = hull(A, "left"), right = hull(rightMap, "right");
        List<ProjectedNodeKey> keys = Arrays.asList(node(A, "p0"), node(A, "p1"),
            node(rightMap, "q0"), node(rightMap, "q1"));
        List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        for (ProjectedNodeKey key : keys) {
            String id = key.source().persistedReference().get().nodeId().value();
            nodes.add(ProjectedNode.of(key, SafeNodeLabel.of(id, id), key.mapReferenceId().equals(A) ? "Map A" : "Map B", true));
        }
        List<ProjectedEnclosure> hulls = new ArrayList<ProjectedEnclosure>();
        if (!crossMap) hulls.add(enclosure(A, "target-root", null, Collections.<ProjectedNodeKey>emptyList(), Arrays.asList(left, right)));
        hulls.add(enclosure(A, "left", crossMap ? null : root, keys.subList(0, 2), Collections.<EnclosureHullKey>emptyList()));
        hulls.add(enclosure(rightMap, "right", crossMap ? null : root, keys.subList(2, 4), Collections.<EnclosureHullKey>emptyList()));
        List<ProjectedEdge> edges = Arrays.asList(
            edge(ProjectedEndpointKey.ofNode(keys.get(0)), ProjectedEndpointKey.ofNode(keys.get(2)), false, 16041),
            edge(ProjectedEndpointKey.ofNode(keys.get(1)), ProjectedEndpointKey.ofEnclosure(right.endpointKeys().get(0)), false, 16042),
            edge(ProjectedEndpointKey.ofEnclosure(left.endpointKeys().get(0)), ProjectedEndpointKey.ofEnclosure(right.endpointKeys().get(0)), false, 16043));
        return projection(1, nodes, hulls, edges, Collections.<PinProjection>emptyList());
    }
    public static PinProjection pin(ProjectedNodeKey node, double x, double y) {
        return PinProjection.active(PinRecord.of(node.source().persistedReference().get(), x, y,
            Collections.<UnknownXml>emptyList()), node);
    }
    public static LayoutRequest request(WorkspaceId workspace, GraphProjection before,
            GraphProjection after, List<PinProjection> pins) {
        return LayoutRequest.of(workspace, after, ProjectionDiff.between(before, after), pins);
    }
    public static final class Link {
        public final Object first, second;
        public final String kind;
        public final boolean crossMap;
        public final double restLength;
        Link(Object first, Object second, String kind, boolean crossMap, double rest) {
            this.first = first; this.second = second; this.kind = kind;
            this.crossMap = crossMap; this.restLength = rest;
        }
    }
    public static final class Snapshot {
        public final LayoutPositions positions;
        public final Map<Object, Double> radii;
        public final List<Link> links;
        Snapshot(LayoutPositions positions, Map<Object, Double> radii, List<Link> links) {
            this.positions = positions;
            this.radii = Collections.unmodifiableMap(new LinkedHashMap<Object, Double>(radii));
            this.links = Collections.unmodifiableList(new ArrayList<Link>(links));
        }
    }
    @SuppressWarnings("unchecked")
    public static Snapshot inspect(LayoutEngine engine) {
        Map<String, GraphStreamLayoutEngine.ParticleState> particles =
            (Map<String, GraphStreamLayoutEngine.ParticleState>) read(engine, "particles");
        Map<ProjectedNodeKey, LayoutPoint> nodes = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        Map<EnclosureHullKey, LayoutPoint> anchors = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        Map<Object, Double> radii = new LinkedHashMap<Object, Double>();
        Map<String, Object> keys = new LinkedHashMap<String, Object>();
        for (GraphStreamLayoutEngine.ParticleState particle : particles.values()) {
            Object key = particle.nodeKey != null ? particle.nodeKey : particle.anchorKey;
            assertThat(key).as("particle semantic key").isNotNull();
            keys.put(particle.id, key); radii.put(key, particle.radius);
            LayoutPoint position = LayoutPoint.of(particle.x, particle.y);
            if (particle.nodeKey != null) nodes.put(particle.nodeKey, position);
            else anchors.put(particle.anchorKey, position);
        }
        TypedSpringBox box = (TypedSpringBox) read(engine, "springBox");
        Map<String, GraphStreamLayoutEngine.ForceLink> registered =
            (Map<String, GraphStreamLayoutEngine.ForceLink>) read(box, "typedLinks");
        List<Link> links = new ArrayList<Link>();
        for (GraphStreamLayoutEngine.ForceLink link : registered.values()) {
            assertThat(keys).containsKeys(link.firstId, link.secondId);
            assertThat(link.firstId).isNotEqualTo(link.secondId);
            links.add(new Link(keys.get(link.firstId), keys.get(link.secondId), link.kind.name(), link.crossMap, link.restLength));
        }
        return new Snapshot(LayoutPositions.of(nodes, anchors), radii, links);
    }
    public static void requireTargets(Snapshot snapshot, double containment, boolean cross, double... relationship) {
        List<Double> actual = new ArrayList<Double>();
        int contained = 0;
        for (Link link : snapshot.links) {
            if (link.kind.equals("RELATIONSHIP")) { assertThat(link.crossMap).isEqualTo(cross); actual.add(link.restLength); }
            if (link.kind.equals("CONTAINMENT")) { assertThat(link.restLength).isCloseTo(containment, offset(1e-9)); contained++; }
        }
        Collections.sort(actual);
        double[] expected = relationship.clone(); Arrays.sort(expected);
        assertThat(actual).hasSize(expected.length);
        for (int i = 0; i < expected.length; i++) assertThat(actual.get(i)).isCloseTo(expected[i], offset(1e-9));
        assertThat(contained).isEqualTo(snapshot.positions.nodes().size());
    }
}
