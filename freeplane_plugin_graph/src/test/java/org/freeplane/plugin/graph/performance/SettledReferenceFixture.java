package org.freeplane.plugin.graph.performance;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.layout.LayoutEngine;
import org.freeplane.plugin.graph.layout.LayoutFrame;
import org.freeplane.plugin.graph.layout.LayoutRequest;
import org.freeplane.plugin.graph.layout.LayoutWorker;
import org.freeplane.plugin.graph.layout.PerceptualIdlePolicy;
import org.freeplane.plugin.graph.projection.CanonicalKeys;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.ProjectionDiff;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PinRecord;
import org.freeplane.plugin.graph.workspace.model.UnknownXml;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;

/**
 * Committed deterministic settled-position ledger (spec Revision 9 §15). The ledger is
 * static resource data; {@link #load()} only parses bytes, verifies the SHA-256 pin and
 * maps each row to a {@link ProjectedNodeKey}/{@link EnclosureHullKey}. No layout engine
 * and no relaxation run at test time, so the "settled" frame is a fixed point by
 * construction: the replay worker returns exactly these positions and the fixture request
 * pins every node.
 */
public final class SettledReferenceFixture {
    /** Re-pinned by the Revision 9 B-prime calibration; the ledger is byte-stable. */
    public static final String SETTLED_FIXTURE_SHA256 =
        "e9e988b7967ea6bb4f503dfb4c5af7d3e74a3328e28e0987e71ff0f0b8ac0578";

    private static final String RESOURCE = "/performance/settled-reference.csv";
    private static final String CSV_HEADER = "kind,key,x,y";

    private final WorkspaceId workspaceId;
    private final GraphProjection projection;
    private final LayoutPositions positions;
    private final List<PinProjection> pins;

    private SettledReferenceFixture(final WorkspaceId workspaceId, final GraphProjection projection,
            final LayoutPositions positions, final List<PinProjection> pins) {
        this.workspaceId = workspaceId;
        this.projection = projection;
        this.positions = positions;
        this.pins = Collections.unmodifiableList(new ArrayList<PinProjection>(pins));
    }

    static byte[] readCommittedLedger() {
        try (InputStream input = SettledReferenceFixture.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing committed settled-reference ledger " + RESOURCE);
            }
            final ByteArrayOutputStream output = new ByteArrayOutputStream();
            final byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
        catch (final IOException failure) {
            throw new IllegalStateException("Cannot read the committed settled-reference ledger", failure);
        }
    }

    static String sha256(final byte[] bytes) {
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            final StringBuilder result = new StringBuilder(digest.length * 2);
            for (final byte value : digest) {
                final String hex = Integer.toHexString(value & 0xff);
                if (hex.length() == 1) {
                    result.append('0');
                }
                result.append(hex);
            }
            return result.toString();
        }
        catch (final NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    public static SettledReferenceFixture load() {
        return Holder.INSTANCE;
    }

    private static SettledReferenceFixture loadVerified() {
        final byte[] ledger = readCommittedLedger();
        final String actual = sha256(ledger);
        if (!SETTLED_FIXTURE_SHA256.equals(actual)) {
            throw new IllegalStateException("Committed settled-reference ledger digest changed: " + actual);
        }
        final GeneratedWorkspace workspace = GeneratedWorkspace.forScenario("reference-2000-5000");
        final GraphProjection projection = SettledReferenceFixtureCompiler.fixtureProjection(
            workspace.projection());
        return parse(workspace.document().id(), projection, ledger);
    }

    public GraphProjection projection() {
        return projection;
    }

    public LayoutPositions positions() {
        return positions;
    }

    public List<PinProjection> pins() {
        return pins;
    }

    public LayoutRequest request() {
        return LayoutRequest.of(workspaceId, projection,
            ProjectionDiff.between(projection, projection), pins);
    }

    public LayoutFrame replayFrame() {
        return LayoutFrame.of(0L, positions, false, 0);
    }

    public LayoutWorker newReplayWorker() {
        return new LayoutWorker(new Supplier<LayoutEngine>() {
            @Override
            public LayoutEngine get() {
                return new LedgerReplayEngine(replayFrame());
            }
        }, PerceptualIdlePolicy.spikeDefaults());
    }

    private static SettledReferenceFixture parse(final WorkspaceId workspaceId,
            final GraphProjection projection, final byte[] ledger) {
        final Map<String, ProjectedNodeKey> nodesByKey = new LinkedHashMap<String, ProjectedNodeKey>();
        for (final org.freeplane.plugin.graph.projection.ProjectedNode node : projection.nodes()) {
            nodesByKey.put(CanonicalKeys.node(node.key()), node.key());
        }
        final Map<String, EnclosureHullKey> anchorsByKey = new LinkedHashMap<String, EnclosureHullKey>();
        for (final org.freeplane.plugin.graph.projection.ProjectedEnclosure enclosure : projection.enclosures()) {
            anchorsByKey.put(CanonicalKeys.hull(enclosure.hullKey()), enclosure.hullKey());
        }
        final Map<ProjectedNodeKey, LayoutPoint> nodes =
            new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        final Map<EnclosureHullKey, LayoutPoint> anchors =
            new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        final List<PinProjection> pins = new ArrayList<PinProjection>();
        final String text = new String(ledger, StandardCharsets.UTF_8);
        final String[] lines = text.split("\n", -1);
        if (lines.length == 0 || !CSV_HEADER.equals(lines[0])) {
            throw new IllegalStateException("Unexpected settled-reference header: "
                + (lines.length == 0 ? "<empty>" : lines[0]));
        }
        for (int index = 1; index < lines.length; index++) {
            final String line = lines[index];
            if (line.isEmpty() && index == lines.length - 1) {
                continue;
            }
            final String[] parts = line.split(",", -1);
            if (parts.length != 4) {
                throw new IllegalStateException("Malformed settled-reference row: " + line);
            }
            final double x = Double.parseDouble(parts[2]);
            final double y = Double.parseDouble(parts[3]);
            if (!Double.isFinite(x) || !Double.isFinite(y)) {
                throw new IllegalStateException("Non-finite settled-reference row: " + line);
            }
            final LayoutPoint point = LayoutPoint.of(x, y);
            if ("node".equals(parts[0])) {
                final ProjectedNodeKey key = nodesByKey.remove(parts[1]);
                if (key == null) {
                    throw new IllegalStateException("Unknown settled-reference node key: " + parts[1]);
                }
                nodes.put(key, point);
                final NodeReference reference = key.source().persistedReference().get();
                pins.add(PinProjection.active(PinRecord.of(reference, x, y,
                    Collections.<UnknownXml>emptyList()), key));
            }
            else if ("anchor".equals(parts[0])) {
                final EnclosureHullKey key = anchorsByKey.remove(parts[1]);
                if (key == null) {
                    throw new IllegalStateException("Unknown settled-reference anchor key: " + parts[1]);
                }
                anchors.put(key, point);
            }
            else {
                throw new IllegalStateException("Unknown settled-reference row kind: " + parts[0]);
            }
        }
        if (!nodesByKey.isEmpty() || !anchorsByKey.isEmpty()
                || nodes.size() != projection.nodes().size()
                || anchors.size() != projection.enclosures().size()) {
            throw new IllegalStateException("Settled-reference ledger does not cover the projection exactly");
        }
        return new SettledReferenceFixture(workspaceId, projection, LayoutPositions.of(nodes, anchors), pins);
    }

    private static final class LedgerReplayEngine implements LayoutEngine {
        private final LayoutFrame frame;

        private LedgerReplayEngine(final LayoutFrame frame) {
            this.frame = frame;
        }

        @Override
        public LayoutFrame apply(final LayoutRequest request) {
            return frame;
        }

        @Override
        public LayoutFrame step() {
            return frame;
        }

        @Override
        public void reset() {
        }

        @Override
        public void close() {
        }
    }

    private static final class Holder {
        private static final SettledReferenceFixture INSTANCE = loadVerified();
    }
}
