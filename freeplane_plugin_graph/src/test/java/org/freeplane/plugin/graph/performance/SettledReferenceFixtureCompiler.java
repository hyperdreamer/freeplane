package org.freeplane.plugin.graph.performance;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.freeplane.plugin.graph.projection.CanonicalKeys;
import org.freeplane.plugin.graph.projection.EdgeContributor;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEdgeKey;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.RelationshipResolution;
import org.freeplane.plugin.graph.projection.input.ConnectorDescriptor;
import org.freeplane.plugin.graph.projection.input.ConnectorSnapshot;

/**
 * Offline deterministic generator for the committed {@code settled-reference} ledger
 * (spec Revision 9 §15). Measurements only parse the committed bytes and verify their digest.
 *
 * <p>The first 25 canonical reference node identities carry a synthetic projection: two
 * crossing chords beside a separate serpentine grid path. The grid adds ordinary index and
 * routing work without extra conflicts; the crossing pair exercises the non-straight ladder.
 * Fixed coordinates keep the complete initial detection within the repairable bound of eight
 * with zero Revision 7 exhaustion. No force relaxation runs during generation or measurement.</p>
 */
public final class SettledReferenceFixtureCompiler {
    static final int FIXTURE_NODE_LIMIT = 25;
    static final int CROSSING_NODE_COUNT = 4;
    static final int LATTICE_COLUMNS = 5;
    static final double LATTICE_SPACING = 200.0;
    static final double LATTICE_ORIGIN_X = 500.0;
    static final String RESOURCE_PATH = "freeplane_plugin_graph/src/test/resources/performance/"
        + "settled-reference.csv";

    private SettledReferenceFixtureCompiler() {
    }

    public static void main(final String[] args) throws Exception {
        final Path resource = repositoryResource();
        Files.createDirectories(resource.getParent());
        final GraphProjection reference = referenceProjection();
        final byte[] ledger = compile(reference);
        Files.write(resource, ledger);
        final GraphProjection fixture = fixtureProjection(reference);
        System.out.println("settled-reference nodes=" + fixture.nodes().size()
            + " enclosures=" + fixture.enclosures().size()
            + " edges=" + fixture.edges().size()
            + " bytes=" + ledger.length + " sha256=" + SettledReferenceFixture.sha256(ledger));
    }

    static GraphProjection referenceProjection() {
        return GeneratedWorkspace.forScenario("reference-2000-5000").projection();
    }

    static GraphProjection fixtureProjection(final GraphProjection reference) {
        final List<ProjectedNode> ordered = new ArrayList<ProjectedNode>(reference.nodes());
        Collections.sort(ordered, new Comparator<ProjectedNode>() {
            @Override
            public int compare(final ProjectedNode first, final ProjectedNode second) {
                return CanonicalKeys.node(first.key()).compareTo(CanonicalKeys.node(second.key()));
            }
        });
        final List<ProjectedNode> nodes = new ArrayList<ProjectedNode>(
            ordered.subList(0, FIXTURE_NODE_LIMIT));
        final List<ProjectedEdge> edges = new ArrayList<ProjectedEdge>();
        edges.add(edge(nodes.get(0), nodes.get(1), 0));
        edges.add(edge(nodes.get(2), nodes.get(3), 1));
        for (int index = CROSSING_NODE_COUNT; index < nodes.size() - 1; index++) {
            edges.add(edge(nodes.get(index), nodes.get(index + 1), index));
        }
        return GraphProjection.projected(reference.generation(), nodes,
            Collections.<ProjectedEnclosure>emptyList(), edges,
            Collections.<RelationshipResolution>emptyList(), Collections.<PinProjection>emptyList());
    }

    private static ProjectedEdge edge(final ProjectedNode first, final ProjectedNode second,
            final int occurrence) {
        final ProjectedEndpointKey source = ProjectedEndpointKey.ofNode(first.key());
        final ProjectedEndpointKey target = ProjectedEndpointKey.ofNode(second.key());
        final ConnectorDescriptor descriptor = ConnectorDescriptor.of(first.source(),
            second.source().persistedReference().get(), false, false, "", "", "");
        return ProjectedEdge.of(ProjectedEdgeKey.of(source, target), Collections.singletonList(
            EdgeContributor.nativeConnector(ConnectorSnapshot.of(occurrence, descriptor), source, target)));
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

    static byte[] compile(final GraphProjection reference) {
        final List<ProjectedNode> nodes = fixtureProjection(reference).nodes();
        final double[][] crossing = {{-90.0, 0.0}, {90.0, 0.0}, {0.0, -25.0}, {0.0, 25.0}};
        final StringBuilder csv = new StringBuilder("kind,key,x,y\n");
        for (int index = 0; index < nodes.size(); index++) {
            final double x;
            final double y;
            if (index < CROSSING_NODE_COUNT) {
                x = crossing[index][0];
                y = crossing[index][1];
            }
            else {
                final int gridIndex = index - CROSSING_NODE_COUNT;
                final int row = gridIndex / LATTICE_COLUMNS;
                final int column = gridIndex % LATTICE_COLUMNS;
                x = LATTICE_ORIGIN_X + ((row & 1) == 0 ? column : LATTICE_COLUMNS - 1 - column)
                    * LATTICE_SPACING;
                y = row * LATTICE_SPACING;
            }
            csv.append("node,").append(CanonicalKeys.node(nodes.get(index).key())).append(',')
                .append(String.format(Locale.ROOT, "%.17g", x)).append(',')
                .append(String.format(Locale.ROOT, "%.17g", y)).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }
}
