package org.freeplane.plugin.graph.layout.graphstream;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.layout.LayoutCalibration;
import org.freeplane.plugin.graph.layout.LayoutEngine;
import org.freeplane.plugin.graph.layout.LayoutFrame;
import org.freeplane.plugin.graph.layout.LayoutRequest;
import org.freeplane.plugin.graph.layout.NodeSeparationProjection;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;
import org.graphstream.graph.implementations.MultiGraph;

final class GraphStreamLayoutEngine implements LayoutEngine {
    static final double NODE_RADIUS = 8.0;
    static final double GROUP_SPACING = 100.0;
    static final double SUB_GROUP_SPACING = 60.0;

    private final LayoutCalibration calibration;
    private final LinkedHashMap<String, ParticleState> particles = new LinkedHashMap<String, ParticleState>();
    private final Set<String> graphEdgeIds = new LinkedHashSet<String>();

    private MultiGraph graph;
    private TypedSpringBox springBox;
    private LayoutRequest lastRequest;
    private long lastSynchronizedProjectionGeneration = -1L;
    private WorkspaceId graphWorkspace;
    private long nextStepIndex;
    private Thread ownerThread;
    private boolean closed;

    GraphStreamLayoutEngine(final LayoutCalibration calibration) {
        this.calibration = Objects.requireNonNull(calibration, "calibration");
    }

    @Override
    public LayoutFrame apply(final LayoutRequest request) {
        checkOpen();
        checkOwnerThread();
        final LayoutRequest accepted = Objects.requireNonNull(request, "request");
        if (graph == null || !accepted.workspace().equals(graphWorkspace)) {
            disposeGraph();
            initializeGraph(accepted.workspace());
            nextStepIndex = 0;
        }
        if (lastRequest != null && accepted.workspace().equals(graphWorkspace)
                && accepted.diff().isEmpty()
                && accepted.diff().beforeGeneration() == lastSynchronizedProjectionGeneration
                && lastRequest.workspace().equals(accepted.workspace())
                && lastRequest.pins().equals(accepted.pins())) {
            lastRequest = accepted;
            lastSynchronizedProjectionGeneration = accepted.projection().generation();
            return frame(nextStepIndex, false);
        }
        synchronize(accepted);
        lastRequest = accepted;
        return frame(nextStepIndex, false);
    }

    @Override
    public LayoutFrame step() {
        checkOpen();
        checkOwnerThread();
        if (lastRequest == null) {
            throw new IllegalStateException("A layout request must be applied before stepping");
        }
        final long frameIndex = nextStepIndex++;
        try {
            springBox.compute();
            synchronizeGraphPositions();
            return frame(frameIndex, false);
        }
        catch (final RuntimeException exception) {
            return frame(frameIndex, true);
        }
    }

    @Override
    public void reset() {
        checkOpen();
        checkOwnerThread();
        nextStepIndex = 0;
        if (lastRequest == null) {
            return;
        }
        final LayoutRequest request = lastRequest;
        disposeGraph();
        initializeGraph(request.workspace());
        synchronize(request);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        checkOwnerThread();
        disposeGraph();
        lastRequest = null;
        closed = true;
    }

    private void synchronize(final LayoutRequest request) {
        final BoundarySizes sizes = new BoundarySizes(request.projection());
        final Seeds seeds = new Seeds(sizes);
        final Topology topology = topology(request.projection(), sizes);
        removeObsoleteParticles(topology.particles.keySet());
        final Map<SourceNodeKey, PinProjection> pinsBySource = pinsBySource(request.pins());
        final LinkedHashMap<String, ParticleState> ordered = new LinkedHashMap<String, ParticleState>();
        for (final DesiredParticle desired : topology.particles.values()) {
            ParticleState state = particles.get(desired.id);
            if (state == null) {
                state = new ParticleState(desired, seeds.positionFor(desired));
                graph.addNode(desired.id);
                springBox.setParticlePosition(desired.id, state.x, state.y);
            }
            state.radius = desired.radius;
            final PinProjection pin = pinFor(desired, pinsBySource);
            state.pinned = pin != null;
            springBox.configureParticle(desired.id, state.radius, state.pinned, desired.nodeKey == null,
                desired.parentAnchorId);
            if (desired.nodeKey == null && desired.anchorKey != null) {
                final BoundarySizes.Footprint anchorFootprint = sizes.footprint(desired.anchorKey);
                springBox.configureAnchorExtents(desired.id, anchorFootprint.width * 0.5,
                    anchorFootprint.height * 0.5);
            }
            if (pin != null) {
                state.x = pin.x();
                state.y = pin.y();
                springBox.setParticlePosition(desired.id, state.x, state.y);
            }
            ordered.put(desired.id, state);
        }
        particles.clear();
        particles.putAll(ordered);
        replaceLinks(topology.links);
        lastSynchronizedProjectionGeneration = request.projection().generation();
    }

    private void removeObsoleteParticles(final Set<String> desiredIds) {
        final List<String> obsolete = new ArrayList<String>();
        for (final String id : particles.keySet()) {
            if (!desiredIds.contains(id)) {
                obsolete.add(id);
            }
        }
        for (final String id : obsolete) {
            graph.removeNode(id);
            springBox.forgetParticle(id);
            particles.remove(id);
        }
    }

    private void replaceLinks(final List<ForceLink> desiredLinks) {
        for (final String edgeId : graphEdgeIds) {
            if (graph.getEdge(edgeId) != null) {
                graph.removeEdge(edgeId);
            }
        }
        graphEdgeIds.clear();
        springBox.clearLinks();
        int index = 0;
        for (final ForceLink link : desiredLinks) {
            if (!particles.containsKey(link.firstId) || !particles.containsKey(link.secondId)
                    || link.firstId.equals(link.secondId)) {
                continue;
            }
            final String edgeId = "layout-" + index++;
            graph.addEdge(edgeId, link.firstId, link.secondId, false);
            graphEdgeIds.add(edgeId);
            springBox.registerLink(edgeId, link);
        }
    }

    private Topology topology(final GraphProjection projection, final BoundarySizes sizes) {
        final LinkedHashMap<String, DesiredParticle> desired = new LinkedHashMap<String, DesiredParticle>();
        final Map<ProjectedNodeKey, String> nodeIds = new LinkedHashMap<ProjectedNodeKey, String>();
        for (final org.freeplane.plugin.graph.projection.ProjectedNode node : projection.nodes()) {
            final double scale = projection.prominence().containsKey(node.key())
                ? projection.prominence().get(node.key()).scale() : 1.0;
            final DesiredParticle particle = DesiredParticle.node(node.key(), NODE_RADIUS * scale);
            desired.put(particle.id, particle);
            nodeIds.put(node.key(), particle.id);
        }
        final Map<EnclosureHullKey, String> anchorIds = new LinkedHashMap<EnclosureHullKey, String>();
        final Map<EnclosureKey, String> enclosureEndpoints = new LinkedHashMap<EnclosureKey, String>();
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            final DesiredParticle particle = DesiredParticle.anchor(enclosure.hullKey(),
                sizes.boundaryRadius(enclosure.hullKey()));
            desired.put(particle.id, particle);
            anchorIds.put(enclosure.hullKey(), particle.id);
            for (final EnclosureKey endpoint : enclosure.endpointKeys()) {
                enclosureEndpoints.put(endpoint, particle.id);
            }
        }
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            final DesiredParticle particle = desired.get(anchorIds.get(enclosure.hullKey()));
            particle.parentAnchorId = anchorIds.get(enclosure.parentHull().orElse(null));
        }

        final List<ForceLink> result = new ArrayList<ForceLink>();
        final Set<String> hierarchyPairs = new LinkedHashSet<String>();
        final Map<EnclosureHullKey, Integer> depths = enclosureDepths(projection);
        final Map<String, Double> radiusById = new LinkedHashMap<String, Double>();
        for (final org.freeplane.plugin.graph.projection.ProjectedNode node : projection.nodes()) {
            radiusById.put(nodeIds.get(node.key()),
                Double.valueOf(sizes.discRadius(node.key())));
        }
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            radiusById.put(anchorIds.get(enclosure.hullKey()),
                Double.valueOf(sizes.contentRingRadius(enclosure.hullKey())));
        }
        for (final ProjectedEdge edge : projection.edges()) {
            final String first = endpointId(edge.first(), nodeIds, enclosureEndpoints);
            final String second = endpointId(edge.second(), nodeIds, enclosureEndpoints);
            if (first != null && second != null) {
                result.add(new ForceLink(first, second, ForceKind.RELATIONSHIP,
                    !edge.first().mapReferenceId().equals(edge.second().mapReferenceId()),
                    radiusOf(first, radiusById) + radiusOf(second, radiusById)
                        + NodeSeparationProjection.MIN_GAP));
            }
        }
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            final String anchor = anchorIds.get(enclosure.hullKey());
            for (final ProjectedNodeKey child : enclosure.directNodes()) {
                final String node = nodeIds.get(child);
                if (node != null) {
                    result.add(new ForceLink(anchor, node, ForceKind.CONTAINMENT, false,
                        sizes.contentRingRadius(enclosure.hullKey())));
                }
            }
            if (enclosure.parentHull().isPresent()) {
                addSkeletonLinks(result, hierarchyPairs, sizes, anchorIds, enclosure.parentHull().get());
            }
        }
        addTopLevelLinks(result, hierarchyPairs, sizes, anchorIds);
        return new Topology(desired, result);
    }

    private static Map<EnclosureHullKey, Integer> enclosureDepths(final GraphProjection projection) {
        final Map<EnclosureHullKey, ProjectedEnclosure> byHull =
            new LinkedHashMap<EnclosureHullKey, ProjectedEnclosure>();
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            byHull.put(enclosure.hullKey(), enclosure);
        }
        final Map<EnclosureHullKey, Integer> depths = new LinkedHashMap<EnclosureHullKey, Integer>();
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            depthOf(enclosure, byHull, depths);
        }
        return depths;
    }

    private static int depthOf(final ProjectedEnclosure enclosure,
            final Map<EnclosureHullKey, ProjectedEnclosure> byHull,
            final Map<EnclosureHullKey, Integer> depths) {
        final Integer cached = depths.get(enclosure.hullKey());
        if (cached != null) {
            return cached.intValue();
        }
        final int value = enclosure.parentHull().isPresent()
            ? depthOf(byHull.get(enclosure.parentHull().get()), byHull, depths) + 1 : 0;
        depths.put(enclosure.hullKey(), Integer.valueOf(value));
        return value;
    }

    private static double hierarchyRestLength(final int childDepth) {
        return childDepth <= 1 ? GROUP_SPACING : SUB_GROUP_SPACING;
    }

    private static void addSkeletonLinks(final List<ForceLink> links, final Set<String> pairs,
            final BoundarySizes sizes, final Map<EnclosureHullKey, String> anchorIds,
            final EnclosureHullKey parentKey) {
        final String parentId = anchorIds.get(parentKey);
        boolean firstChild = true;
        for (final ClusterPlacement placement : sizes.placements(parentKey)) {
            if (placement.item.anchorKey == null) {
                continue;
            }
            final String childId = anchorIds.get(placement.item.anchorKey);
            if (childId == null) {
                continue;
            }
            final double childX = placement.x + placement.item.anchorX;
            final double childY = placement.y + placement.item.anchorY;
            boolean linked = false;
            for (final ClusterItem contact : placement.contacts) {
                if (contact.anchorKey == null) {
                    addHierarchyLink(links, pairs, parentId, childId, Math.hypot(childX, childY));
                    linked = true;
                    continue;
                }
                final ClusterPlacement other = sizes.placementOf(parentKey, contact.anchorKey);
                if (other == null) {
                    continue;
                }
                final String otherId = anchorIds.get(contact.anchorKey);
                if (otherId == null) {
                    continue;
                }
                final double otherX = other.x + other.item.anchorX;
                final double otherY = other.y + other.item.anchorY;
                addHierarchyLink(links, pairs, childId, otherId,
                    Math.hypot(childX - otherX, childY - otherY));
                linked = true;
            }
            if (!linked || firstChild) {
                addHierarchyLink(links, pairs, parentId, childId, Math.hypot(childX, childY));
            }
            firstChild = false;
        }
    }

    private static void addTopLevelLinks(final List<ForceLink> links, final Set<String> pairs,
            final BoundarySizes sizes, final Map<EnclosureHullKey, String> anchorIds) {
        for (final ClusterPlacement placement : sizes.topLevelPlacements()) {
            if (placement.item.anchorKey == null) {
                continue;
            }
            final String id = anchorIds.get(placement.item.anchorKey);
            if (id == null) {
                continue;
            }
            final double anchorX = placement.x + placement.item.anchorX;
            final double anchorY = placement.y + placement.item.anchorY;
            for (final ClusterItem contact : placement.contacts) {
                final ClusterPlacement other = sizes.topLevelPlacementOf(contact.anchorKey);
                if (other == null) {
                    continue;
                }
                final String otherId = anchorIds.get(contact.anchorKey);
                if (otherId == null) {
                    continue;
                }
                final double otherX = other.x + other.item.anchorX;
                final double otherY = other.y + other.item.anchorY;
                addHierarchyLink(links, pairs, id, otherId,
                    Math.hypot(anchorX - otherX, anchorY - otherY));
            }
        }
    }

    static final class ClusterItem {
        final EnclosureHullKey anchorKey;
        final double width;
        final double height;
        final double anchorX;
        final double anchorY;
        final int order;

        ClusterItem(final EnclosureHullKey anchorKey, final double width, final double height,
                final double anchorX, final double anchorY, final int order) {
            this.anchorKey = anchorKey;
            this.width = width;
            this.height = height;
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.order = order;
        }
    }

    static final class ClusterPlacement {
        final ClusterItem item;
        final double x;
        final double y;
        final List<ClusterItem> contacts = new ArrayList<ClusterItem>();

        ClusterPlacement(final ClusterItem item, final double x, final double y) {
            this.item = item;
            this.x = x;
            this.y = y;
        }
    }

    static List<ClusterPlacement> packItems(final List<ClusterItem> items, final double gap) {
        final List<ClusterItem> ordered = new ArrayList<ClusterItem>(items);
        ordered.sort((first, second) -> {
            if ((first.anchorKey == null) != (second.anchorKey == null)) {
                return first.anchorKey == null ? -1 : 1;
            }
            final int byArea = Double.compare(second.width * second.height, first.width * first.height);
            if (byArea != 0) {
                return byArea;
            }
            return Integer.compare(first.order, second.order);
        });
        final List<ClusterPlacement> placed = new ArrayList<ClusterPlacement>();
        for (final ClusterItem item : ordered) {
            final List<Double> xs = new ArrayList<Double>();
            final List<Double> ys = new ArrayList<Double>();
            if (placed.isEmpty()) {
                xs.add(Double.valueOf(0.0));
                ys.add(Double.valueOf(0.0));
            }
            else {
                double minX = Double.POSITIVE_INFINITY;
                double maxX = Double.NEGATIVE_INFINITY;
                double minY = Double.POSITIVE_INFINITY;
                double maxY = Double.NEGATIVE_INFINITY;
                for (final ClusterPlacement other : placed) {
                    xs.add(Double.valueOf(other.x - (other.item.width + item.width) * 0.5 - gap));
                    xs.add(Double.valueOf(other.x + (other.item.width + item.width) * 0.5 + gap));
                    ys.add(Double.valueOf(other.y - (other.item.height + item.height) * 0.5 - gap));
                    ys.add(Double.valueOf(other.y + (other.item.height + item.height) * 0.5 + gap));
                    minX = Math.min(minX, other.x - other.item.width * 0.5);
                    maxX = Math.max(maxX, other.x + other.item.width * 0.5);
                    minY = Math.min(minY, other.y - other.item.height * 0.5);
                    maxY = Math.max(maxY, other.y + other.item.height * 0.5);
                }
                xs.add(Double.valueOf(minX - item.width * 0.5 - gap));
                xs.add(Double.valueOf(maxX + item.width * 0.5 + gap));
                ys.add(Double.valueOf(minY - item.height * 0.5 - gap));
                ys.add(Double.valueOf(maxY + item.height * 0.5 + gap));
                xs.add(Double.valueOf(minX + item.width * 0.5));
                xs.add(Double.valueOf(maxX - item.width * 0.5));
                ys.add(Double.valueOf(minY + item.height * 0.5));
                ys.add(Double.valueOf(maxY - item.height * 0.5));
            }
            dedupeSorted(xs);
            dedupeSorted(ys);
            double bestX = 0.0;
            double bestY = 0.0;
            double bestArea = Double.POSITIVE_INFINITY;
            double bestRadius = Double.POSITIVE_INFINITY;
            int bestIndex = Integer.MAX_VALUE;
            boolean found = false;
            int index = 0;
            for (final Double xValue : xs) {
                for (final Double yValue : ys) {
                    index++;
                    final double x = xValue.doubleValue();
                    final double y = yValue.doubleValue();
                    if (!clears(x, y, item, placed, gap)) {
                        continue;
                    }
                    final double area = bboxArea(placed, x, y, item);
                    final double radius = Math.hypot(x, y);
                    final boolean better = !found
                        || area < bestArea - 1e-9
                        || (Math.abs(area - bestArea) <= 1e-9
                            && (radius < bestRadius - 1e-9
                                || (Math.abs(radius - bestRadius) <= 1e-9 && index < bestIndex)));
                    if (better) {
                        found = true;
                        bestX = x;
                        bestY = y;
                        bestArea = area;
                        bestRadius = radius;
                        bestIndex = index;
                    }
                }
            }
            if (!found) {
                throw new IllegalStateException("Cluster packing found no free slot");
            }
            final ClusterPlacement placement = new ClusterPlacement(item, bestX, bestY);
            for (final ClusterPlacement other : placed) {
                recordContacts(placement, other, gap);
            }
            placed.add(placement);
        }
        return placed;
    }

    private static void dedupeSorted(final List<Double> values) {
        Collections.sort(values);
        for (int index = values.size() - 1; index > 0; index--) {
            if (Math.abs(values.get(index).doubleValue() - values.get(index - 1).doubleValue()) <= 1e-9) {
                values.remove(index);
            }
        }
    }

    private static double bboxArea(final List<ClusterPlacement> placed, final double x, final double y,
            final ClusterItem item) {
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
        return (maxX - minX) * (maxY - minY);
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

    private static void recordContacts(final ClusterPlacement current, final ClusterPlacement other,
            final double gap) {
        final double dx = Math.abs(current.x - other.x)
            - (current.item.width + other.item.width) * 0.5;
        final double dy = Math.abs(current.y - other.y)
            - (current.item.height + other.item.height) * 0.5;
        if ((dx <= gap + 0.75 && dy < -0.75) || (dy <= gap + 0.75 && dx < -0.75)) {
            current.contacts.add(other.item);
            other.contacts.add(current.item);
        }
    }

    private static double radiusOf(final String particleId, final Map<String, Double> radiusById) {
        final Double radius = radiusById.get(particleId);
        return radius == null ? 0.0 : radius.doubleValue();
    }

    private static void addHierarchyLink(final List<ForceLink> links, final Set<String> pairs,
            final String parent, final String child, final double restLength) {
        if (parent == null || child == null || parent.equals(child)) {
            return;
        }
        final String pair = parent.compareTo(child) < 0 ? parent + '\u0000' + child : child + '\u0000' + parent;
        if (pairs.add(pair)) {
            links.add(new ForceLink(parent, child, ForceKind.HIERARCHY, false, restLength));
        }
    }

    private static String endpointId(final ProjectedEndpointKey endpoint,
            final Map<ProjectedNodeKey, String> nodeIds, final Map<EnclosureKey, String> enclosureEndpoints) {
        if (endpoint.isNode()) {
            return nodeIds.get(endpoint.node().get());
        }
        return enclosureEndpoints.get(endpoint.enclosure().get());
    }

    private static Map<SourceNodeKey, PinProjection> pinsBySource(final List<PinProjection> pins) {
        final Map<SourceNodeKey, PinProjection> result = new LinkedHashMap<SourceNodeKey, PinProjection>();
        for (final PinProjection pin : pins) {
            if (pin.active()) {
                result.put(SourceNodeKey.persisted(pin.source()), pin);
            }
        }
        return result;
    }

    private static PinProjection pinFor(final DesiredParticle desired,
            final Map<SourceNodeKey, PinProjection> pinsBySource) {
        if (desired.nodeKey != null) {
            return pinsBySource.get(desired.nodeKey.source());
        }
        return null;
    }

    private void synchronizeGraphPositions() {
        for (final ParticleState state : particles.values()) {
            final LayoutPoint position = springBox.positionOf(state.id);
            if (!Double.isFinite(position.x()) || !Double.isFinite(position.y())) {
                throw new IllegalStateException("Layout force produced non-finite coordinates");
            }
            state.x = position.x();
            state.y = position.y();
        }
    }

    private LayoutFrame frame(final long index, final boolean failed) {
        final Map<ProjectedNodeKey, LayoutPoint> nodes = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        final Map<EnclosureHullKey, LayoutPoint> anchors = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        for (final ParticleState state : particles.values()) {
            if (!Double.isFinite(state.x) || !Double.isFinite(state.y)) {
                throw new IllegalStateException("Layout snapshot contains non-finite coordinates");
            }
            if (state.nodeKey != null) {
                nodes.put(state.nodeKey, LayoutPoint.of(state.x, state.y));
            }
            else {
                anchors.put(state.anchorKey, LayoutPoint.of(state.x, state.y));
            }
        }
        return LayoutFrame.of(index, LayoutPositions.of(nodes, anchors), failed);
    }

    private void initializeGraph(final WorkspaceId workspace) {
        graphWorkspace = workspace;
        springBox = new TypedSpringBox(calibration, new Random(lower64(sha256(workspaceBytes(workspace)))));
        springBox.setQuality(0.10);
        graph = new MultiGraph("graph-workspace-layout");
        graph.addSink(springBox);
    }

    private void disposeGraph() {
        if (graph != null) {
            graph.removeSink(springBox);
            graph.clear();
        }
        if (springBox != null) {
            springBox.clear();
        }
        graph = null;
        springBox = null;
        graphWorkspace = null;
        particles.clear();
        graphEdgeIds.clear();
        lastSynchronizedProjectionGeneration = -1L;
    }

    private static byte[] workspaceBytes(final WorkspaceId workspace) {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(0x57);
        writeUuid(output, workspace.value());
        return output.toByteArray();
    }

    private static byte[] encodeNode(final ProjectedNodeKey key) {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(0x4e);
        writeSource(output, key.source());
        return output.toByteArray();
    }

    private static byte[] encodeAnchor(final EnclosureHullKey key) {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(0x41);
        writeInt(output, key.endpointKeys().size());
        for (final EnclosureKey endpoint : key.endpointKeys()) {
            writeSource(output, endpoint.source());
        }
        return output.toByteArray();
    }

    private static void writeSource(final ByteArrayOutputStream output, final SourceNodeKey source) {
        writeUuid(output, source.mapReferenceId().value());
        if (source.persistent()) {
            output.write(1);
            writeUtf8(output, source.persistedReference().get().nodeId().value());
        }
        else {
            output.write(0);
            writeInt(output, source.structuralPath().size());
            for (final Integer index : source.structuralPath()) {
                writeInt(output, index.intValue());
            }
        }
    }

    private static void writeUuid(final ByteArrayOutputStream output, final UUID value) {
        writeLong(output, value.getMostSignificantBits());
        writeLong(output, value.getLeastSignificantBits());
    }

    private static void writeUtf8(final ByteArrayOutputStream output, final String value) {
        final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeBytes(output, bytes);
    }

    private static void writeBytes(final ByteArrayOutputStream output, final byte[] bytes) {
        writeInt(output, bytes.length);
        output.write(bytes, 0, bytes.length);
    }

    private static void writeInt(final ByteArrayOutputStream output, final int value) {
        output.write(value >>> 24 & 0xff);
        output.write(value >>> 16 & 0xff);
        output.write(value >>> 8 & 0xff);
        output.write(value & 0xff);
    }

    private static void writeLong(final ByteArrayOutputStream output, final long value) {
        output.write((int) (value >>> 56) & 0xff);
        output.write((int) (value >>> 48) & 0xff);
        output.write((int) (value >>> 40) & 0xff);
        output.write((int) (value >>> 32) & 0xff);
        output.write((int) (value >>> 24) & 0xff);
        output.write((int) (value >>> 16) & 0xff);
        output.write((int) (value >>> 8) & 0xff);
        output.write((int) value & 0xff);
    }

    private static byte[] sha256(final byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        }
        catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static long lower64(final byte[] value) {
        long result = 0L;
        for (int index = value.length - 8; index < value.length; index++) {
            result = result << 8 | value[index] & 0xffL;
        }
        return result;
    }

    private static String identifier(final String prefix, final byte[] identity) {
        final char[] hex = "0123456789abcdef".toCharArray();
        final byte[] digest = sha256(identity);
        final StringBuilder result = new StringBuilder(prefix.length() + digest.length * 2);
        result.append(prefix);
        for (final byte value : digest) {
            final int unsigned = value & 0xff;
            result.append(hex[unsigned >>> 4]);
            result.append(hex[unsigned & 0x0f]);
        }
        return result.toString();
    }

    private void checkOpen() {
        if (closed) {
            throw new IllegalStateException("Layout engine is closed");
        }
    }

    private void checkOwnerThread() {
        final Thread current = Thread.currentThread();
        if (ownerThread == null) {
            ownerThread = current;
        }
        else if (ownerThread != current) {
            throw new IllegalStateException("GraphStream layout calls must use one owner thread");
        }
    }

    static final class BoundarySizes {
        static final double MAX_RENDERED_NODE_RADIUS = 14.0;
        static final double CHAR_WIDTH_UPPER_BOUND = 16.0;
        static final double CHAR_HEIGHT_UPPER_BOUND = 24.0;
        static final double BOUNDARY_PADDING = 8.0;
        static final double SIBLING_GAP = 8.0;
        static final double NODE_SETTLE_MARGIN =
            Double.parseDouble(System.getProperty("proto.margin", "12.0"));
        static final double FRAME_CLEARANCE = 16.0;

        private final Map<ProjectedNodeKey, ProjectedNode> nodesByKey =
            new LinkedHashMap<ProjectedNodeKey, ProjectedNode>();
        private final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull =
            new LinkedHashMap<EnclosureHullKey, ProjectedEnclosure>();
        private final Map<EnclosureHullKey, Size> sizes = new LinkedHashMap<EnclosureHullKey, Size>();
        private final Map<ProjectedNodeKey, Double> discRadii = new LinkedHashMap<ProjectedNodeKey, Double>();

        double discRadius(final ProjectedNodeKey key) {
            final Double radius = discRadii.get(key);
            return radius == null ? 0.0 : radius.doubleValue();
        }

        double contentRingRadius(final EnclosureHullKey key) {
            final ProjectedEnclosure enclosure = enclosuresByHull.get(key);
            final List<ProjectedNodeKey> directNodes = enclosure.directNodes();
            final int count = directNodes.size();
            if (count <= 1) {
                return 0.0;
            }
            double maxRadius = 0.0;
            for (final ProjectedNodeKey nodeKey : directNodes) {
                maxRadius = Math.max(maxRadius, discRadius(nodeKey));
            }
            return (2.0 * maxRadius + NodeSeparationProjection.MIN_GAP)
                / (2.0 * Math.sin(Math.PI / count));
        }

        BoundarySizes(final GraphProjection projection) {
            for (final ProjectedNode node : projection.nodes()) {
                nodesByKey.put(node.key(), node);
                final double scale = projection.prominence().containsKey(node.key())
                    ? projection.prominence().get(node.key()).scale() : 1.0;
                discRadii.put(node.key(), Double.valueOf(NODE_RADIUS * scale));
            }
            for (final ProjectedEnclosure enclosure : projection.enclosures()) {
                enclosuresByHull.put(enclosure.hullKey(), enclosure);
            }
        }

        double boundaryRadius(final EnclosureHullKey key) {
            final Footprint footprint = footprint(key);
            return 0.5 * Math.hypot(footprint.width, footprint.height);
        }

        static final class Footprint {
            final double width;
            final double height;
            final double anchorX;
            final double anchorY;

            Footprint(final double width, final double height, final double anchorX,
                    final double anchorY) {
                this.width = width;
                this.height = height;
                this.anchorX = anchorX;
                this.anchorY = anchorY;
            }
        }

        private final Map<EnclosureHullKey, Footprint> footprints =
            new LinkedHashMap<EnclosureHullKey, Footprint>();
        private final Map<EnclosureHullKey, List<ClusterPlacement>> packedPlacements =
            new LinkedHashMap<EnclosureHullKey, List<ClusterPlacement>>();
        private List<ClusterPlacement> topLevelPlacements;

        Footprint footprint(final EnclosureHullKey key) {
            final Footprint cached = footprints.get(key);
            if (cached != null) {
                return cached;
            }
            final ProjectedEnclosure enclosure = enclosuresByHull.get(key);
            final Footprint result;
            if (enclosure.directNodes().isEmpty() && enclosure.directEnclosures().isEmpty()) {
                double width = 2.0 * BOUNDARY_PADDING;
                double height = CHAR_HEIGHT_UPPER_BOUND + 2.0 * BOUNDARY_PADDING;
                for (final SafeNodeLabel label : enclosure.labels()) {
                    width = Math.max(width,
                        label.displayText().length() * CHAR_WIDTH_UPPER_BOUND + 2.0 * BOUNDARY_PADDING);
                }
                result = new Footprint(width, height, 0.0, 0.0);
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
                final double centreX = (minX + maxX) * 0.5;
                final double centreY = (minY + maxY) * 0.5;
                result = new Footprint(maxX - minX + 2.0 * FRAME_CLEARANCE,
                    maxY - minY + 2.0 * FRAME_CLEARANCE, -centreX, -centreY);
            }
            footprints.put(key, result);
            return result;
        }

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
                items.add(new ClusterItem(null, 2.0 * half, 2.0 * half, 0.0, 0.0, 0));
            }
            int order = 0;
            for (final EnclosureHullKey child : enclosure.directEnclosures()) {
                final Footprint childFootprint = footprint(child);
                items.add(new ClusterItem(child, childFootprint.width, childFootprint.height,
                    childFootprint.anchorX, childFootprint.anchorY, order++));
            }
            final List<ClusterPlacement> result = items.isEmpty()
                ? Collections.<ClusterPlacement>emptyList() : packItems(items, SIBLING_GAP);
            packedPlacements.put(key, result);
            return result;
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
                    final Footprint size = footprint(enclosure.hullKey());
                    items.add(new ClusterItem(enclosure.hullKey(), size.width, size.height,
                        size.anchorX, size.anchorY, order++));
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

        double directNodeRingRadius(final EnclosureHullKey key) {
            final ProjectedEnclosure enclosure = enclosuresByHull.get(key);
            final List<ProjectedNodeKey> directNodes = enclosure.directNodes();
            final int count = directNodes.size();
            if (count <= 1) {
                return 0.0;
            }
            double maxWidth = 0.0;
            double maxHeight = 0.0;
            for (final ProjectedNodeKey nodeKey : directNodes) {
                final ProjectedNode node = nodesByKey.get(nodeKey);
                final double discRadius = MAX_RENDERED_NODE_RADIUS;
                final double width = Math.max(2.0 * discRadius,
                    node.label().displayText().length() * CHAR_WIDTH_UPPER_BOUND + 2.0 * BOUNDARY_PADDING);
                final double height = Math.max(2.0 * discRadius,
                    CHAR_HEIGHT_UPPER_BOUND + 2.0 * BOUNDARY_PADDING);
                maxWidth = Math.max(maxWidth, width);
                maxHeight = Math.max(maxHeight, height);
            }
            return Math.hypot(maxWidth + SIBLING_GAP, maxHeight + SIBLING_GAP)
                / (2.0 * Math.sin(Math.PI / count));
        }

        double directNodeReach(final EnclosureHullKey key) {
            final ProjectedEnclosure enclosure = enclosuresByHull.get(key);
            final List<ProjectedNodeKey> directNodes = enclosure.directNodes();
            if (directNodes.isEmpty()) {
                return 0.0;
            }
            double maxNodeRadius = 0.0;
            for (final ProjectedNodeKey nodeKey : directNodes) {
                final ProjectedNode node = nodesByKey.get(nodeKey);
                final double discRadius = MAX_RENDERED_NODE_RADIUS;
                final double width = Math.max(2.0 * discRadius,
                    node.label().displayText().length() * CHAR_WIDTH_UPPER_BOUND + 2.0 * BOUNDARY_PADDING);
                final double height = Math.max(2.0 * discRadius,
                    CHAR_HEIGHT_UPPER_BOUND + 2.0 * BOUNDARY_PADDING);
                maxNodeRadius = Math.max(maxNodeRadius, 0.5 * Math.hypot(width, height));
            }
            final double radius = directNodeRingRadius(key);
            return radius + maxNodeRadius;
        }

        double childBoundaryReach(final EnclosureHullKey key) {
            final ProjectedEnclosure enclosure = enclosuresByHull.get(key);
            final List<EnclosureHullKey> children = enclosure.directEnclosures();
            if (children.isEmpty()) {
                return 0.0;
            }
            double reach = 0.0;
            final double radius = ringRadius(key);
            for (final EnclosureHullKey child : children) {
                reach = Math.max(reach, radius + reachOf(child));
            }
            return reach;
        }

        double ringRadius(final EnclosureHullKey key) {
            final ProjectedEnclosure enclosure = enclosuresByHull.get(key);
            final List<EnclosureHullKey> children = enclosure.directEnclosures();
            final int count = children.size();
            if (count <= 1) {
                return 0.0;
            }
            double maxWidth = 0.0;
            double maxHeight = 0.0;
            for (final EnclosureHullKey child : children) {
                final Size size = sizeOf(child);
                maxWidth = Math.max(maxWidth, size.width);
                maxHeight = Math.max(maxHeight, size.height);
            }
            return Math.hypot(maxWidth + SIBLING_GAP, maxHeight + SIBLING_GAP)
                / (2.0 * Math.sin(Math.PI / count));
        }

        ProjectedEnclosure enclosure(final EnclosureHullKey key) {
            return enclosuresByHull.get(key);
        }

        List<ProjectedEnclosure> enclosures() {
            return new ArrayList<ProjectedEnclosure>(enclosuresByHull.values());
        }

        Size sizeOf(final EnclosureHullKey key) {
            final Footprint footprint = footprint(key);
            return new Size(footprint.width, footprint.height);
        }

        private double reachOf(final EnclosureHullKey key) {
            final ProjectedEnclosure enclosure = enclosuresByHull.get(key);
            if (enclosure.directNodes().isEmpty() && enclosure.directEnclosures().isEmpty()) {
                return 0.5 * Math.hypot(sizeOf(key).width, sizeOf(key).height);
            }
            return Math.max(directNodeReach(key), childBoundaryReach(key));
        }

        private static final class Size {
            private final double width;
            private final double height;

            Size(final double width, final double height) {
                this.width = width;
                this.height = height;
            }
        }
    }

    private static final class Seeds {
        private final BoundarySizes sizes;
        private final Map<EnclosureHullKey, Position> centers = new LinkedHashMap<EnclosureHullKey, Position>();
        private final Map<ProjectedNodeKey, Position> nodePositions = new LinkedHashMap<ProjectedNodeKey, Position>();
        private final Map<ProjectedNodeKey, EnclosureHullKey> parentEnclosureOfNode =
            new LinkedHashMap<ProjectedNodeKey, EnclosureHullKey>();

        Seeds(final BoundarySizes sizes) {
            this.sizes = sizes;
            for (final ProjectedEnclosure enclosure : sizes.enclosures()) {
                for (final ProjectedNodeKey nodeKey : enclosure.directNodes()) {
                    parentEnclosureOfNode.put(nodeKey, enclosure.hullKey());
                }
            }
        }

        Position positionFor(final DesiredParticle particle) {
            if (particle.nodeKey != null) {
                return nodePosition(particle.nodeKey);
            }
            return center(particle.anchorKey);
        }

        private Position nodePosition(final ProjectedNodeKey nodeKey) {
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

        private Position center(final EnclosureHullKey key) {
            final Position cached = centers.get(key);
            if (cached != null) {
                return cached;
            }
            final Position center;
            if (isTopLevel(key)) {
                final ClusterPlacement placement = sizes.topLevelPlacementOf(key);
                center = placement == null ? new Position(0.0, 0.0)
                    : new Position(placement.x + placement.item.anchorX,
                        placement.y + placement.item.anchorY);
            }
            else {
                final ProjectedEnclosure enclosure = sizes.enclosure(key);
                final EnclosureHullKey parentKey = enclosure.parentHull().get();
                final Position parentCenter = center(parentKey);
                final ClusterPlacement placement = sizes.placementOf(parentKey, key);
                center = placement == null ? parentCenter
                    : new Position(parentCenter.x + placement.x + placement.item.anchorX,
                        parentCenter.y + placement.y + placement.item.anchorY);
            }
            centers.put(key, center);
            return center;
        }

        private boolean isTopLevel(final EnclosureHullKey key) {
            final ProjectedEnclosure enclosure = sizes.enclosure(key);
            return enclosure == null || enclosure.mapRoot() || !enclosure.parentHull().isPresent();
        }

        private Position topRingPosition(final EnclosureHullKey key) {
            final List<EnclosureHullKey> roots = new ArrayList<EnclosureHullKey>();
            for (final ProjectedEnclosure enclosure : sizes.enclosures()) {
                if (enclosure.mapRoot()) {
                    roots.add(enclosure.hullKey());
                }
            }
            final int count = roots.size();
            if (count <= 1) {
                return new Position(0.0, 0.0);
            }
            double maxWidth = 0.0;
            double maxHeight = 0.0;
            for (final EnclosureHullKey root : roots) {
                maxWidth = Math.max(maxWidth, sizes.sizeOf(root).width);
                maxHeight = Math.max(maxHeight, sizes.sizeOf(root).height);
            }
            final double radius = Math.hypot(maxWidth + BoundarySizes.SIBLING_GAP,
                maxHeight + BoundarySizes.SIBLING_GAP) / (2.0 * Math.sin(Math.PI / count));
            final int index = Math.max(0, roots.indexOf(key));
            final double angle = 2.0 * Math.PI * index / count;
            return new Position(radius * Math.cos(angle), radius * Math.sin(angle));
        }
    }

    static final class ParticleState {
        final String id;
        final MapReferenceId mapReferenceId;
        final ProjectedNodeKey nodeKey;
        final EnclosureHullKey anchorKey;
        double radius;
        double x;
        double y;
        boolean pinned;

        ParticleState(final DesiredParticle desired, final Position position) {
            id = desired.id;
            mapReferenceId = desired.mapReferenceId;
            nodeKey = desired.nodeKey;
            anchorKey = desired.anchorKey;
            radius = desired.radius;
            x = position.x;
            y = position.y;
        }
    }

    static final class ForceLink {
        final String firstId;
        final String secondId;
        final ForceKind kind;
        final boolean crossMap;
        final double restLength;

        ForceLink(final String firstId, final String secondId, final ForceKind kind, final boolean crossMap,
                final double restLength) {
            this.firstId = firstId;
            this.secondId = secondId;
            this.kind = kind;
            this.crossMap = crossMap;
            this.restLength = restLength;
        }
    }

    enum ForceKind {
        RELATIONSHIP,
        CONTAINMENT,
        HIERARCHY
    }

    private static final class DesiredParticle {
        private final String id;
        private final MapReferenceId mapReferenceId;
        private final ProjectedNodeKey nodeKey;
        private final EnclosureHullKey anchorKey;
        private final double radius;
        private String parentAnchorId;

        private DesiredParticle(final String id, final MapReferenceId mapReferenceId,
                final ProjectedNodeKey nodeKey, final EnclosureHullKey anchorKey, final double radius) {
            this.id = id;
            this.mapReferenceId = mapReferenceId;
            this.nodeKey = nodeKey;
            this.anchorKey = anchorKey;
            this.radius = radius;
        }

        static DesiredParticle node(final ProjectedNodeKey key, final double radius) {
            return new DesiredParticle(identifier("node-", encodeNode(key)), key.mapReferenceId(), key, null,
                radius);
        }

        static DesiredParticle anchor(final EnclosureHullKey key, final double radius) {
            return new DesiredParticle(identifier("anchor-", encodeAnchor(key)), key.mapReferenceId(), null, key,
                radius);
        }
    }

    private static final class Topology {
        private final LinkedHashMap<String, DesiredParticle> particles;
        private final List<ForceLink> links;

        Topology(final LinkedHashMap<String, DesiredParticle> particles, final List<ForceLink> links) {
            this.particles = particles;
            this.links = links;
        }
    }

    private static final class Position {
        private final double x;
        private final double y;

        Position(final double x, final double y) {
            this.x = x;
            this.y = y;
        }
    }
}
