package org.freeplane.plugin.graph.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.freeplane.plugin.graph.projection.CanonicalKeys;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.junit.Test;

public class SettledReferenceFixtureShould {
    @Test
    public void theCommittedLedgerIsTheDeterministicCompilationOfTheReferenceProjection() throws Exception {
        GeneratedWorkspace workspace = GeneratedWorkspace.forScenario("reference-2000-5000");
        byte[] expected = SettledReferenceFixtureCompiler.compile(workspace.projection());
        if ("1".equals(System.getenv("GRAPH_REGENERATE_SETTLED_FIXTURE"))) {
            Path resource = repositoryFile(
                "freeplane_plugin_graph/src/test/resources/performance/settled-reference.csv");
            Files.createDirectories(resource.getParent());
            Files.write(resource, expected);
        }
        byte[] committed = SettledReferenceFixture.readCommittedLedger();
        assertThat(committed).isEqualTo(expected);
        assertThat(SettledReferenceFixture.sha256(committed))
            .isEqualTo(SettledReferenceFixture.SETTLED_FIXTURE_SHA256);
    }

    @Test
    public void theCommittedLedgerCoversTheReferenceProjectionExactly() {
        SettledReferenceFixture fixture = SettledReferenceFixture.load();
        assertThat(fixture.positions().nodes()).hasSize(25);
        assertThat(fixture.positions().anchors()).isEmpty();
        assertThat(fixture.positions().nodes().keySet()).containsExactlyInAnyOrderElementsOf(
            nodeKeys(fixture.projection()));
        assertThat(fixture.pins()).hasSize(25);
    }

    private static Path repositoryFile(final String relativePath) {
        Path directory = Paths.get("").toAbsolutePath();
        while (directory != null) {
            Path candidate = directory.resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("Cannot locate " + relativePath);
    }

    private static List<ProjectedNodeKey> nodeKeys(final GraphProjection projection) {
        final List<ProjectedNodeKey> keys = new ArrayList<ProjectedNodeKey>();
        for (final ProjectedNode node : projection.nodes()) {
            keys.add(node.key());
        }
        Collections.sort(keys, new Comparator<ProjectedNodeKey>() {
            @Override
            public int compare(final ProjectedNodeKey first, final ProjectedNodeKey second) {
                return CanonicalKeys.node(first).compareTo(CanonicalKeys.node(second));
            }
        });
        return keys;
    }
}
