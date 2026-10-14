package org.freeplane.plugin.graph.performance;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.freeplane.plugin.graph.projection.CanonicalKeys;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;

/**
 * Offline deterministic generator for the committed {@code settled-reference} ledger
 * (spec Revision 8 §14.1). It is never called by a measurement: the ledger is committed
 * as a resource, and the tests only parse it and verify its digest.
 *
 * <p>The fixture is calibrated as the deterministic reduction of the
 * {@code reference-2000-5000} projection that stays inside every Revision 7 budget with
 * zero exhaustion while carrying a non-trivial workload. The reduction keeps the first
 * {@link #FIXTURE_NODE_LIMIT} nodes in ascending {@link CanonicalKeys#node} order, every
 * enclosure whose direct nodes and direct child enclosures all survive, and every edge
 * whose endpoints survive. The measured calibration record is in the task report.</p>
 */
public final class SettledReferenceFixtureCompiler {
    static final int SETTLED_RELAXATION_ITERATIONS = 400;
    static final double SETTLED_SCALE = 4.1;
    static final int LATTICE_COLUMNS = 50;
    static final double LATTICE_SPACING = 100.0;
    static final String RESOURCE_PATH = "freeplane_plugin_graph/src/test/resources/performance/"
        + "settled-reference.csv";

    /**
     * Chosen by the Step 6 calibration (25 nodes, scale 4.1): the fastest in-budget
     * non-trivial reduction (at least one detected crossing and one accepted bow) that
     * stays inside the §7.1 strict thresholds on the reference machine. The reduction
     * drops the highest-index nodes in ascending {@link CanonicalKeys#node} order.
     */
    static final int FIXTURE_NODE_LIMIT = 25;

    private SettledReferenceFixtureCompiler() {
    }

    public static void main(final String[] args) throws Exception {
        final Path resource = repositoryResource();
        Files.createDirectories(resource.getParent());
        final GraphProjection projection = referenceProjection();
        final byte[] ledger = compile(projection);
        Files.write(resource, ledger);
        final GraphProjection reduced = reducedReferenceProjection();
        System.out.println("settled-reference nodes=" + reduced.nodes().size()
            + " enclosures=" + reduced.enclosures().size()
            + " edges=" + reduced.edges().size()
            + " bytes=" + ledger.length + " sha256=" + SettledReferenceFixture.sha256(ledger));
    }

    static GraphProjection referenceProjection() {
        return GeneratedWorkspace.forScenario("reference-2000-5000").projection();
    }

    static GraphProjection reducedReferenceProjection() {
        return reduce(referenceProjection(), FIXTURE_NODE_LIMIT);
    }

    /**
     * Drops the highest-index nodes (ascending {@link CanonicalKeys#node} order), every
     * enclosure left without a surviving node anywhere below it and every edge with a
     * dropped endpoint. The relative order of the surviving values is preserved.
     */
    static GraphProjection reduce(final GraphProjection projection, final int nodeLimit) {
        final List<ProjectedNode> ordered = new ArrayList<ProjectedNode>(projection.nodes());
        Collections.sort(ordered, new Comparator<ProjectedNode>() {
            @Override
            public int compare(final ProjectedNode first, final ProjectedNode second) {
                return CanonicalKeys.node(first.key()).compareTo(CanonicalKeys.node(second.key()));
            }
        });
        final int keptCount = Math.min(Math.max(nodeLimit, 0), ordered.size());
        final List<ProjectedNode> nodes = new ArrayList<ProjectedNode>(ordered.subList(0, keptCount));
        final Set<ProjectedNodeKey> keptNodes = new LinkedHashSet<ProjectedNodeKey>();
        for (final ProjectedNode node : nodes) {
            keptNodes.add(node.key());
        }
        final Set<EnclosureHullKey> keptHulls = new LinkedHashSet<EnclosureHullKey>();
        final Set<EnclosureKey> keptEnclosureKeys = new LinkedHashSet<EnclosureKey>();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (final ProjectedEnclosure enclosure : projection.enclosures()) {
                if (keptHulls.contains(enclosure.hullKey())) {
                    continue;
                }
                boolean complete = true;
                for (final ProjectedNodeKey key : enclosure.directNodes()) {
                    if (!keptNodes.contains(key)) {
                        complete = false;
                        break;
                    }
                }
                if (complete) {
                    for (final EnclosureHullKey child : enclosure.directEnclosures()) {
                        if (!keptHulls.contains(child)) {
                            complete = false;
                            break;
                        }
                    }
                }
                if (complete) {
                    keptHulls.add(enclosure.hullKey());
                    keptEnclosureKeys.addAll(enclosure.endpointKeys());
                    changed = true;
                }
            }
        }
        final List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            if (keptHulls.contains(enclosure.hullKey())) {
                enclosures.add(enclosure);
            }
        }
        final List<ProjectedEdge> edges = new ArrayList<ProjectedEdge>();
        for (final ProjectedEdge edge : projection.edges()) {
            if (kept(edge.first(), keptNodes, keptEnclosureKeys)
                    && kept(edge.second(), keptNodes, keptEnclosureKeys)) {
                edges.add(edge);
            }
        }
        return GraphProjection.projected(projection.generation(), nodes, enclosures, edges,
            Collections.<org.freeplane.plugin.graph.projection.RelationshipResolution>emptyList(),
            Collections.<org.freeplane.plugin.graph.projection.PinProjection>emptyList());
    }

    private static boolean kept(final org.freeplane.plugin.graph.projection.ProjectedEndpointKey endpoint,
            final Set<ProjectedNodeKey> keptNodes, final Set<EnclosureKey> keptEnclosureKeys) {
        if (endpoint.isNode()) {
            return keptNodes.contains(endpoint.node().get());
        }
        return keptEnclosureKeys.contains(endpoint.enclosure().get());
    }

    private static Path repositoryResource() {
        Path directory = Paths.get("").toAbsolutePath();
        while (directory != null) {
            final Path candidate = directory.resolve(RESOURCE_PATH);
            if (Files.isRegularFile(candidate) || Files.isDirectory(directory.resolve(
                    "freeplane_plugin_graph/src/test/resources"))) {
                return candidate;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("Cannot locate the repository root for " + RESOURCE_PATH);
    }

    static byte[] compile(final GraphProjection projection) {
        return compileReduced(reduce(projection, FIXTURE_NODE_LIMIT));
    }

    private static byte[] compileReduced(final GraphProjection projection) {
        final double scale = SETTLED_SCALE;
        final int iterations = SETTLED_RELAXATION_ITERATIONS;        final List<ProjectedNode> nodes = new ArrayList<ProjectedNode>(projection.nodes());
        Collections.sort(nodes, new Comparator<ProjectedNode>() {
            @Override
            public int compare(final ProjectedNode first, final ProjectedNode second) {
                return CanonicalKeys.node(first.key()).compareTo(CanonicalKeys.node(second.key()));
            }
        });
        final int count = nodes.size();
        final Map<ProjectedNodeKey, Integer> indexByKey = new LinkedHashMap<ProjectedNodeKey, Integer>();
        for (int index = 0; index < count; index++) {
            indexByKey.put(nodes.get(index).key(), Integer.valueOf(index));
        }
        final double[] x = new double[count];
        final double[] y = new double[count];
        for (int index = 0; index < count; index++) {
            x[index] = (index % LATTICE_COLUMNS) * LATTICE_SPACING;
            y[index] = (index / LATTICE_COLUMNS) * LATTICE_SPACING;
        }
        final List<int[]> edges = new ArrayList<int[]>();
        for (final ProjectedEdge edge : projection.edges()) {
            if (!edge.first().isNode() || !edge.second().isNode()) {
                continue;
            }
            final Integer first = indexByKey.get(edge.first().node().get());
            final Integer second = indexByKey.get(edge.second().node().get());
            if (first != null && second != null && first.intValue() != second.intValue()) {
                edges.add(new int[] { first.intValue(), second.intValue() });
            }
        }
        final double area = (LATTICE_COLUMNS * LATTICE_SPACING)
            * ((count / LATTICE_COLUMNS + 1) * LATTICE_SPACING);
        final double k = Math.sqrt(area / count);
        final double[] displacementX = new double[count];
        final double[] displacementY = new double[count];
        for (int pass = 0; pass < iterations; pass++) {
            final double temperature = 8.0 * LATTICE_SPACING
                * (1.0 - (double) pass / iterations);
            Arrays.fill(displacementX, 0.0);
            Arrays.fill(displacementY, 0.0);
            final double bucketSize = 2.0 * k;
            final Map<Long, List<Integer>> buckets = new HashMap<Long, List<Integer>>();
            for (int index = 0; index < count; index++) {
                final long bucketKey = (((long) Math.floor(x[index] / bucketSize)) << 32)
                    | ((long) Math.floor(y[index] / bucketSize) & 0xffffffffL);
                List<Integer> bucket = buckets.get(Long.valueOf(bucketKey));
                if (bucket == null) {
                    bucket = new ArrayList<Integer>();
                    buckets.put(Long.valueOf(bucketKey), bucket);
                }
                bucket.add(Integer.valueOf(index));
            }
            for (int index = 0; index < count; index++) {
                final int cellX = (int) Math.floor(x[index] / bucketSize);
                final int cellY = (int) Math.floor(y[index] / bucketSize);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        final List<Integer> bucket = buckets.get(Long.valueOf(
                            (((long) (cellX + dx)) << 32) | ((cellY + dy) & 0xffffffffL)));
                        if (bucket == null) {
                            continue;
                        }
                        for (final Integer other : bucket) {
                            final int peer = other.intValue();
                            if (peer == index) {
                                continue;
                            }
                            double deltaX = x[index] - x[peer];
                            double deltaY = y[index] - y[peer];
                            double distance = Math.hypot(deltaX, deltaY);
                            if (distance < 1e-9) {
                                deltaX = ((index & 1) == 0 ? 1.0 : -1.0) * 0.01;
                                deltaY = 0.01;
                                distance = Math.hypot(deltaX, deltaY);
                            }
                            final double force = (k * k) / distance / 2.0;
                            displacementX[index] += deltaX / distance * force;
                            displacementY[index] += deltaY / distance * force;
                        }
                    }
                }
            }
            for (final int[] edge : edges) {
                final int first = edge[0];
                final int second = edge[1];
                final double deltaX = x[first] - x[second];
                final double deltaY = y[first] - y[second];
                final double distance = Math.max(1e-9, Math.hypot(deltaX, deltaY));
                final double force = (distance * distance) / k / 2.0;
                displacementX[first] -= deltaX / distance * force;
                displacementY[first] -= deltaY / distance * force;
                displacementX[second] += deltaX / distance * force;
                displacementY[second] += deltaY / distance * force;
            }
            for (int index = 0; index < count; index++) {
                final double length = Math.hypot(displacementX[index], displacementY[index]);
                if (length < 1e-12) {
                    continue;
                }
                final double limited = Math.min(length, temperature) / length;
                x[index] += displacementX[index] * limited;
                y[index] += displacementY[index] * limited;
            }
        }
        final StringBuilder csv = new StringBuilder("kind,key,x,y\n");
        for (int index = 0; index < count; index++) {
            csv.append("node,").append(CanonicalKeys.node(nodes.get(index).key())).append(',')
                .append(String.format(Locale.ROOT, "%.17g", x[index] * scale)).append(',')
                .append(String.format(Locale.ROOT, "%.17g", y[index] * scale)).append('\n');
        }
        final List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>(projection.enclosures());
        Collections.sort(enclosures, new Comparator<ProjectedEnclosure>() {
            @Override
            public int compare(final ProjectedEnclosure first, final ProjectedEnclosure second) {
                return CanonicalKeys.hull(first.hullKey()).compareTo(CanonicalKeys.hull(second.hullKey()));
            }
        });
        for (final ProjectedEnclosure enclosure : enclosures) {
            double sumX = 0.0;
            double sumY = 0.0;
            int direct = 0;
            for (final ProjectedNodeKey key : enclosure.directNodes()) {
                final Integer index = indexByKey.get(key);
                if (index != null) {
                    sumX += x[index.intValue()] * scale;
                    sumY += y[index.intValue()] * scale;
                    direct++;
                }
            }
            csv.append("anchor,").append(CanonicalKeys.hull(enclosure.hullKey())).append(',')
                .append(String.format(Locale.ROOT, "%.17g", direct == 0 ? 0.0 : sumX / direct)).append(',')
                .append(String.format(Locale.ROOT, "%.17g", direct == 0 ? 0.0 : sumY / direct)).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }
}
