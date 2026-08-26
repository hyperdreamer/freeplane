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
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;
import org.graphstream.graph.implementations.MultiGraph;

final class GraphStreamLayoutEngine implements LayoutEngine {
    private static final double NODE_RADIUS = 8.0;
    private static final double ANCHOR_RADIUS = 8.0;
    private static final double INITIAL_POSITION_SPREAD = 50.0;

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
        final Topology topology = topology(request.projection());
        removeObsoleteParticles(topology.particles.keySet());
        final Map<ProjectedNodeKey, PinProjection> activePins = activePins(request.pins());
        final LinkedHashMap<String, ParticleState> ordered = new LinkedHashMap<String, ParticleState>();
        for (final DesiredParticle desired : topology.particles.values()) {
            ParticleState state = particles.get(desired.id);
            if (state == null) {
                state = new ParticleState(desired, initialPosition(request.workspace(), desired,
                    INITIAL_POSITION_SPREAD));
                graph.addNode(desired.id);
                springBox.setParticlePosition(desired.id, state.x, state.y);
            }
            state.radius = desired.radius;
            final PinProjection pin = desired.nodeKey == null ? null : activePins.get(desired.nodeKey);
            state.pinned = pin != null;
            springBox.configureParticle(desired.id, state.radius, state.pinned);
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

    private Topology topology(final GraphProjection projection) {
        final LinkedHashMap<String, DesiredParticle> desired = new LinkedHashMap<String, DesiredParticle>();
        final Map<ProjectedNodeKey, String> nodeIds = new LinkedHashMap<ProjectedNodeKey, String>();
        for (final ProjectedNode node : projection.nodes()) {
            final DesiredParticle particle = DesiredParticle.node(node.key(), NODE_RADIUS
                * projection.prominence().get(node.key()).scale());
            desired.put(particle.id, particle);
            nodeIds.put(node.key(), particle.id);
        }
        final Map<EnclosureHullKey, String> anchorIds = new LinkedHashMap<EnclosureHullKey, String>();
        final Map<EnclosureKey, String> enclosureEndpoints = new LinkedHashMap<EnclosureKey, String>();
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            final DesiredParticle particle = DesiredParticle.anchor(enclosure.hullKey(), ANCHOR_RADIUS);
            desired.put(particle.id, particle);
            anchorIds.put(enclosure.hullKey(), particle.id);
            for (final EnclosureKey endpoint : enclosure.endpointKeys()) {
                enclosureEndpoints.put(endpoint, particle.id);
            }
        }

        final List<ForceLink> result = new ArrayList<ForceLink>();
        final Set<String> hierarchyPairs = new LinkedHashSet<String>();
        for (final ProjectedEdge edge : projection.edges()) {
            final String first = endpointId(edge.first(), nodeIds, enclosureEndpoints);
            final String second = endpointId(edge.second(), nodeIds, enclosureEndpoints);
            if (first != null && second != null) {
                result.add(new ForceLink(first, second, ForceKind.RELATIONSHIP,
                    !edge.first().mapReferenceId().equals(edge.second().mapReferenceId())));
            }
        }
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            final String anchor = anchorIds.get(enclosure.hullKey());
            for (final ProjectedNodeKey child : enclosure.directNodes()) {
                final String node = nodeIds.get(child);
                if (node != null) {
                    result.add(new ForceLink(anchor, node, ForceKind.CONTAINMENT, false));
                }
            }
            if (enclosure.parentHull().isPresent()) {
                addHierarchyLink(result, hierarchyPairs, anchorIds.get(enclosure.parentHull().get()), anchor);
            }
            for (final EnclosureHullKey child : enclosure.directEnclosures()) {
                addHierarchyLink(result, hierarchyPairs, anchor, anchorIds.get(child));
            }
        }
        return new Topology(desired, result);
    }

    private static void addHierarchyLink(final List<ForceLink> links, final Set<String> pairs,
            final String parent, final String child) {
        if (parent == null || child == null || parent.equals(child)) {
            return;
        }
        final String pair = parent.compareTo(child) < 0 ? parent + '\u0000' + child : child + '\u0000' + parent;
        if (pairs.add(pair)) {
            links.add(new ForceLink(parent, child, ForceKind.HIERARCHY, false));
        }
    }

    private static String endpointId(final ProjectedEndpointKey endpoint,
            final Map<ProjectedNodeKey, String> nodeIds, final Map<EnclosureKey, String> enclosureEndpoints) {
        if (endpoint.isNode()) {
            return nodeIds.get(endpoint.node().get());
        }
        return enclosureEndpoints.get(endpoint.enclosure().get());
    }

    private static Map<ProjectedNodeKey, PinProjection> activePins(final List<PinProjection> pins) {
        final Map<ProjectedNodeKey, PinProjection> result = new LinkedHashMap<ProjectedNodeKey, PinProjection>();
        for (final PinProjection pin : pins) {
            if (pin.active()) {
                result.put(pin.projectedNode().get(), pin);
            }
        }
        return result;
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
            final LayoutPoint point = LayoutPoint.of(state.x, state.y);
            if (state.nodeKey != null) {
                nodes.put(state.nodeKey, point);
            }
            else {
                anchors.put(state.anchorKey, point);
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

    private static Position initialPosition(final WorkspaceId workspace, final DesiredParticle particle,
            final double spread) {
        final Random random = new Random(lower64(sha256(seedBytes(workspace, particle.identity))));
        return new Position(random.nextDouble() * spread - spread * 0.5,
            random.nextDouble() * spread - spread * 0.5);
    }

    private static byte[] seedBytes(final WorkspaceId workspace, final byte[] identity) {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(0x57);
        writeUuid(output, workspace.value());
        writeBytes(output, identity);
        return output.toByteArray();
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

        ForceLink(final String firstId, final String secondId, final ForceKind kind, final boolean crossMap) {
            this.firstId = firstId;
            this.secondId = secondId;
            this.kind = kind;
            this.crossMap = crossMap;
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
        private final byte[] identity;

        private DesiredParticle(final String id, final MapReferenceId mapReferenceId,
                final ProjectedNodeKey nodeKey, final EnclosureHullKey anchorKey, final double radius,
                final byte[] identity) {
            this.id = id;
            this.mapReferenceId = mapReferenceId;
            this.nodeKey = nodeKey;
            this.anchorKey = anchorKey;
            this.radius = radius;
            this.identity = identity;
        }

        static DesiredParticle node(final ProjectedNodeKey key, final double radius) {
            final byte[] identity = encodeNode(key);
            return new DesiredParticle(identifier("node-", identity), key.mapReferenceId(), key, null, radius,
                identity);
        }

        static DesiredParticle anchor(final EnclosureHullKey key, final double radius) {
            final byte[] identity = encodeAnchor(key);
            return new DesiredParticle(identifier("anchor-", identity), key.mapReferenceId(), null, key, radius,
                identity);
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
