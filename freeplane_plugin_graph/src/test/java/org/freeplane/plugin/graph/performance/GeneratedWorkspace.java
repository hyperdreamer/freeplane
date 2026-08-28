package org.freeplane.plugin.graph.performance;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.xml.namespace.QName;

import org.freeplane.plugin.graph.projection.EdgeContributor;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectionEngine;
import org.freeplane.plugin.graph.projection.input.ConnectorDescriptor;
import org.freeplane.plugin.graph.projection.input.ConnectorSnapshot;
import org.freeplane.plugin.graph.projection.input.MapAvailability;
import org.freeplane.plugin.graph.projection.input.MapSnapshot;
import org.freeplane.plugin.graph.projection.input.NodeSnapshot;
import org.freeplane.plugin.graph.projection.input.ProjectionInput;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.io.WorkspaceMigrationRegistry;
import org.freeplane.plugin.graph.workspace.io.WorkspaceXmlCodec;
import org.freeplane.plugin.graph.workspace.model.DisplaySettings;
import org.freeplane.plugin.graph.workspace.model.GraphRelationshipRecord;
import org.freeplane.plugin.graph.workspace.model.MapReference;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.freeplane.plugin.graph.workspace.model.PinRecord;
import org.freeplane.plugin.graph.workspace.model.RelationshipDirection;
import org.freeplane.plugin.graph.workspace.model.RelationshipId;
import org.freeplane.plugin.graph.workspace.model.UnknownXml;
import org.freeplane.plugin.graph.workspace.model.Viewport;
import org.freeplane.plugin.graph.workspace.model.WorkspaceDocument;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;

public final class GeneratedWorkspace {
    public static final long SEED = 20260810L;
    public static final long FIXED_SEED = SEED;
    private static final List<UnknownXml> NO_UNKNOWN_XML = Collections.emptyList();
    private static final QName GENERATED_LABELS_ELEMENT = new QName("", "generated-visible-leaf-labels");
    private static final QName GENERATED_LABEL_ELEMENT = new QName("", "label");
    private static final QName MAP_ATTRIBUTE = new QName("", "map");
    private static final QName NODE_ATTRIBUTE = new QName("", "node");
    private static final QName FULL_ATTRIBUTE = new QName("", "full");
    private static final QName DISPLAY_ATTRIBUTE = new QName("", "display");
    private static final List<Integer> SKEWED_FINAL_MAP_EMPTY_ENCLOSURE_BUCKETS =
        Collections.unmodifiableList(Arrays.asList(Integer.valueOf(22), Integer.valueOf(23)));
    private static final String[] MAP_COLORS = {
        "#4E79A7", "#F28E2B", "#59A14F", "#E15759",
        "#76B7B2", "#B07AA1", "#EDC948", "#9C755F"
    };

    public enum Scenario {
        TWO_MAP("two-map", 2, new int[] {60, 60}, new int[] {20, 20},
            new int[] {60, 60}, 20, 30),
        THREE_MAP_CLUSTERED("three-map-clustered", 3, new int[] {60, 60, 60},
            new int[] {20, 20, 20}, new int[] {60, 60, 60}, 20, 30),
        REFERENCE_2000_5000("reference-2000-5000", 20, repeat(20, 100), repeat(20, 60),
            repeat(20, 175), 400, 300),
        SKEWED_REFERENCE("skewed-reference", 20,
            new int[] {1600, 21, 21, 21, 21, 21, 21, 21, 21, 21,
                21, 21, 21, 21, 21, 21, 21, 21, 21, 22},
            new int[] {960, 12, 12, 12, 12, 12, 12, 12, 12, 12,
                12, 12, 12, 12, 12, 12, 12, 12, 12, 24},
            new int[] {2800, 37, 37, 37, 37, 37, 37, 37, 37, 37,
                37, 37, 37, 37, 37, 37, 37, 37, 37, 34}, 20, 30),
        ONE_PINNED_MAP("one-pinned-map", 3, new int[] {60, 60, 60},
            new int[] {20, 20, 20}, new int[] {60, 60, 60}, 20, 30),
        TWO_PINNED_MAPS("two-pinned-maps", 3, new int[] {60, 60, 60},
            new int[] {20, 20, 20}, new int[] {60, 60, 60}, 20, 30);

        private final String wireName;
        private final int mapCount;
        private final int[] nodesByMap;
        private final int[] enclosuresByMap;
        private final int[] nativeByMap;
        private final int warmups;
        private final int measured;

        Scenario(final String wireName, final int mapCount, final int[] nodesByMap,
                final int[] enclosuresByMap, final int[] nativeByMap, final int warmups,
                final int measured) {
            this.wireName = wireName;
            this.mapCount = mapCount;
            this.nodesByMap = nodesByMap;
            this.enclosuresByMap = enclosuresByMap;
            this.nativeByMap = nativeByMap;
            this.warmups = warmups;
            this.measured = measured;
        }

        public String wireName() {
            return wireName;
        }

        public String scenarioName() {
            return wireName;
        }

        public int mapCount() {
            return mapCount;
        }

        public int warmupCount() {
            return warmups;
        }

        public int measuredCount() {
            return measured;
        }

        public int[] nodesByMap() {
            return nodesByMap.clone();
        }

        public int[] enclosuresByMap() {
            return enclosuresByMap.clone();
        }

        public int[] nativeContributorsByMap() {
            return nativeByMap.clone();
        }

        @Override
        public String toString() {
            return wireName;
        }

        public static Scenario fromWireName(final String value) {
            for (final Scenario scenario : values()) {
                if (scenario.wireName.equals(value)) {
                    return scenario;
                }
            }
            throw new IllegalArgumentException("Unknown generated workspace scenario: " + value);
        }
    }

    private final Scenario scenario;
    private final WorkspaceDocument document;
    private final List<MapSnapshot> snapshots;
    private final Map<MapReferenceId, MapAvailability> availability;
    private final ProjectionInput input;
    private final GraphProjection projection;
    private final Counts counts;

    private GeneratedWorkspace(final Scenario scenario) {
        this.scenario = scenario;
        final BuildResult built = build(scenario);
        document = built.document;
        snapshots = built.snapshots;
        availability = built.availability;
        input = ProjectionInput.of(1L, document, snapshots, availability);
        projection = new ProjectionEngine().project(input);
        counts = validate(scenario, document, snapshots, projection);
    }

    public static GeneratedWorkspace forScenario(final Scenario scenario) {
        if (scenario == null) {
            throw new NullPointerException("scenario");
        }
        return new GeneratedWorkspace(scenario);
    }

    public static GeneratedWorkspace create(final Scenario scenario) {
        return forScenario(scenario);
    }

    public static GeneratedWorkspace forScenario(final String scenario) {
        return forScenario(Scenario.fromWireName(scenario));
    }

    public static GeneratedWorkspace create(final String scenario) {
        return forScenario(scenario);
    }

    public static List<Scenario> scenarios() {
        return Collections.unmodifiableList(Arrays.asList(Scenario.values()));
    }

    public static List<GeneratedWorkspace> all() {
        final List<GeneratedWorkspace> result = new ArrayList<GeneratedWorkspace>();
        for (final Scenario scenario : Scenario.values()) {
            result.add(forScenario(scenario));
        }
        return Collections.unmodifiableList(result);
    }

    public Scenario scenario() {
        return scenario;
    }

    public Scenario variant() {
        return scenario;
    }

    public String scenarioName() {
        return scenario.wireName;
    }

    public String name() {
        return scenario.wireName;
    }

    public WorkspaceDocument document() {
        return document;
    }

    public WorkspaceDocument workspace() {
        return document;
    }

    public List<MapSnapshot> snapshots() {
        return snapshots;
    }

    public List<MapSnapshot> mapSnapshots() {
        return snapshots;
    }

    public Map<MapReferenceId, MapAvailability> availability() {
        return availability;
    }

    public ProjectionInput input() {
        return input;
    }

    public ProjectionInput projectionInput() {
        return input;
    }

    public GraphProjection projection() {
        return projection;
    }

    public GraphProjection project() {
        return projection;
    }

    public Counts counts() {
        return counts;
    }

    public int mapCount() { return counts.mapCount(); }
    public int nodeCount() { return counts.nodeCount(); }
    public int enclosureCount() { return counts.enclosureCount(); }
    public int nativeContributorCount() { return counts.nativeContributorCount(); }
    public int relationshipCount() { return counts.relationshipCount(); }
    public int crossMapContributorCount() { return counts.crossMapContributorCount(); }
    public int containmentLinkCount() { return counts.containmentLinkCount(); }
    public int hierarchyLinkCount() { return counts.hierarchyLinkCount(); }
    public int particleCount() { return counts.particleCount(); }
    public int springCount() { return counts.springCount(); }
    public int warmupCount() { return scenario.warmupCount(); }
    public int measuredCount() { return scenario.measuredCount(); }

    public void writeFixture(final Path outputDirectory) throws IOException {
        final String fileName = fixtureFileName(scenario);
        if (fileName == null) {
            throw new IllegalArgumentException("Scenario has no required fixture: " + scenario.wireName);
        }
        writeDocument(document, outputDirectory.resolve(fileName));
    }

    public static void writeFixtures(final Path outputDirectory) throws IOException {
        if (outputDirectory == null) {
            throw new NullPointerException("outputDirectory");
        }
        Files.createDirectories(outputDirectory);
        for (final Scenario scenario : Scenario.values()) {
            final String fileName = fixtureFileName(scenario);
            if (fileName != null) {
                forScenario(scenario).writeFixture(outputDirectory);
            }
        }
        final Set<String> actual = new LinkedHashSet<String>();
        try (java.nio.file.DirectoryStream<Path> files = Files.newDirectoryStream(outputDirectory)) {
            for (final Path file : files) {
                if (Files.isRegularFile(file)) {
                    actual.add(file.getFileName().toString());
                }
            }
        }
        final Set<String> expected = new LinkedHashSet<String>(Arrays.asList(
            "two-map.fpg", "three-map.fpg", "reference-2000-5000.fpg"));
        if (!actual.equals(expected)) {
            throw new IllegalStateException("Unexpected generated fixture names: " + actual);
        }
    }

    private static void writeDocument(final WorkspaceDocument document, final Path location) throws IOException {
        final WorkspaceXmlCodec codec = codec();
        final byte[] first = codec.write(document, location);
        Files.write(location, first);
        if (!document.equals(codec.read(location))) {
            throw new IllegalStateException("Workspace XML round trip changed " + location.getFileName());
        }
        final byte[] second = codec.write(document, location);
        if (!Arrays.equals(first, second)) {
            throw new IllegalStateException("Workspace XML serialization is not deterministic for "
                + location.getFileName());
        }
    }

    private static String fixtureFileName(final Scenario scenario) {
        switch (scenario) {
        case TWO_MAP:
            return "two-map.fpg";
        case THREE_MAP_CLUSTERED:
            return "three-map.fpg";
        case REFERENCE_2000_5000:
            return "reference-2000-5000.fpg";
        default:
            return null;
        }
    }

    private static WorkspaceXmlCodec codec() {
        return new WorkspaceXmlCodec(new WorkspaceMigrationRegistry(
            Collections.<org.freeplane.plugin.graph.workspace.io.WorkspaceMigration>emptyList()));
    }

    private static BuildResult build(final Scenario scenario) {
        final int mapCount = scenario.mapCount;
        final List<MapReference> registrations = new ArrayList<MapReference>(mapCount);
        final List<MapSnapshot> snapshots = new ArrayList<MapSnapshot>(mapCount);
        final Map<MapReferenceId, MapAvailability> availability =
            new LinkedHashMap<MapReferenceId, MapAvailability>();
        final List<GraphRelationshipRecord> relationships = new ArrayList<GraphRelationshipRecord>();
        final List<PinRecord> pins = pinsFor(scenario);

        final List<MapReferenceId> mapIds = new ArrayList<MapReferenceId>(mapCount);
        final List<List<NodeReference>> referencesByMap = new ArrayList<List<NodeReference>>(mapCount);
        for (int mapIndex = 0; mapIndex < mapCount; mapIndex++) {
            final MapReferenceId mapId = mapId(mapIndex);
            mapIds.add(mapId);
            registrations.add(MapReference.of(mapId, mapIndex + 1L,
                URI.create(String.format(Locale.ROOT, "maps/m%02d.mm", mapIndex)), true,
                MAP_COLORS[mapIndex % MAP_COLORS.length], NO_UNKNOWN_XML));
            final List<NodeReference> references = nodeReferences(mapId, mapIndex,
                scenario.nodesByMap[mapIndex]);
            referencesByMap.add(references);
            snapshots.add(buildSnapshot(scenario, mapIndex, mapId, references,
                scenario.enclosuresByMap[mapIndex], scenario.nativeByMap[mapIndex]));
            availability.put(mapId, MapAvailability.AVAILABLE);
        }

        final List<PairAllocation> allocations = crossAllocations(scenario, mapCount);
        int relationshipIndex = 0;
        final Map<String, Set<String>> usedCrossPairs = new HashMap<String, Set<String>>();
        for (final PairAllocation allocation : allocations) {
            final int firstMap = allocation.firstMap;
            final int secondMap = allocation.secondMap;
            final List<NodeReference> firstNodes = referencesByMap.get(firstMap);
            final List<NodeReference> secondNodes = referencesByMap.get(secondMap);
            final String pairName = firstMap + ":" + secondMap;
            Set<String> used = usedCrossPairs.get(pairName);
            if (used == null) {
                used = new HashSet<String>();
                usedCrossPairs.put(pairName, used);
            }
            final long ordinal = allocation.ordinal;
            int firstNode = floorMod(SEED + ordinal * 37L + firstMap * 101L, firstNodes.size());
            int secondNode = floorMod(SEED + ordinal * 53L + secondMap * 151L + 7L,
                secondNodes.size());
            String endpointPair = firstNode + ":" + secondNode;
            long probe = 0L;
            while (!used.add(endpointPair)) {
                probe++;
                firstNode = (firstNode + 1) % firstNodes.size();
                secondNode = (secondNode + (int) (probe % secondNodes.size()) + 1) % secondNodes.size();
                endpointPair = firstNode + ":" + secondNode;
                if (probe > (long) firstNodes.size() * (long) secondNodes.size()) {
                    throw new IllegalStateException("Unable to allocate a unique cross-map endpoint pair");
                }
            }
            final RelationshipDirection direction = (relationshipIndex & 1) == 0
                ? RelationshipDirection.FORWARD : RelationshipDirection.BIDIRECTIONAL;
            final String relationshipNamespace = scenario.wireName + ":relationship:" + relationshipIndex;
            final RelationshipId id = RelationshipId.of(UUID.nameUUIDFromBytes(
                relationshipNamespace.getBytes(StandardCharsets.UTF_8)));
            relationships.add(GraphRelationshipRecord.of(id, relationshipIndex + 1L,
                firstNodes.get(firstNode), secondNodes.get(secondNode), direction, NO_UNKNOWN_XML));
            relationshipIndex++;
        }

        final WorkspaceId workspaceId = WorkspaceId.of(UUID.nameUUIDFromBytes(
            ("freeplane-graph-performance:workspace:" + scenario.wireName)
                .getBytes(StandardCharsets.UTF_8)));
        final WorkspaceDocument document = WorkspaceDocument.createVersion1(workspaceId).toBuilder()
            .maps(registrations)
            .relationships(relationships)
            .pins(pins)
            .viewport(Viewport.of(0.0, 0.0, 1.0, NO_UNKNOWN_XML))
            .displaySettings(DisplaySettings.of(true, DisplaySettings.CanvasTheme.LIGHT, true, false,
                NO_UNKNOWN_XML))
            .unknownXml(generatedLabelXml(snapshots))
            .build();
        return new BuildResult(document, snapshots, availability);
    }

    private static List<UnknownXml> generatedLabelXml(final List<MapSnapshot> snapshots) {
        final List<UnknownXml.Content> labels = new ArrayList<UnknownXml.Content>();
        for (final MapSnapshot snapshot : snapshots) {
            appendVisibleLeafLabels(snapshot.root(), labels);
        }
        return Collections.singletonList(UnknownXml.element(UnknownXml.Owner.WORKSPACE, 5,
            GENERATED_LABELS_ELEMENT, Collections.<QName, String>emptyMap(), labels));
    }

    private static void appendVisibleLeafLabels(final NodeSnapshot node,
            final List<UnknownXml.Content> labels) {
        if (node.structuralLeaf()) {
            if (node.excluded()) {
                return;
            }
            if (!node.key().persistent()) {
                throw new IllegalStateException("Generated visible leaf must have a persisted key");
            }
            final NodeReference reference = node.key().persistedReference().get();
            final Map<QName, String> attributes = new LinkedHashMap<QName, String>();
            attributes.put(MAP_ATTRIBUTE, reference.mapReferenceId().toString());
            attributes.put(NODE_ATTRIBUTE, reference.nodeId().value());
            attributes.put(FULL_ATTRIBUTE, node.label().fullText());
            attributes.put(DISPLAY_ATTRIBUTE, node.label().displayText());
            labels.add(UnknownXml.Content.element(GENERATED_LABEL_ELEMENT, attributes,
                Collections.<UnknownXml.Content>emptyList()));
            return;
        }
        for (final NodeSnapshot child : node.children()) {
            appendVisibleLeafLabels(child, labels);
        }
    }

    private static MapSnapshot buildSnapshot(final Scenario scenario, final int mapIndex,
            final MapReferenceId mapId, final List<NodeReference> references, final int enclosureCount,
            final int nativeCount) {
        final List<List<NodeSnapshot>> leavesByEnclosure = new ArrayList<List<NodeSnapshot>>(enclosureCount);
        final int[] directLeafCounts = new int[enclosureCount];
        for (int index = 0; index < enclosureCount; index++) {
            leavesByEnclosure.add(new ArrayList<NodeSnapshot>());
        }
        for (int nodeIndex = 0; nodeIndex < references.size(); nodeIndex++) {
            final NodeReference reference = references.get(nodeIndex);
            final NodeSnapshot leaf = NodeSnapshot.of(SourceNodeKey.persisted(reference),
                SafeNodeLabel.of("node-full-" + reference.nodeId().value(),
                    "node-" + reference.nodeId().value()), true, true, false,
                Collections.<NodeSnapshot>emptyList());
            final int enclosureIndex = nodeIndex < enclosureCount ? nodeIndex : nodeIndex % enclosureCount;
            leavesByEnclosure.get(enclosureIndex).add(leaf);
            directLeafCounts[enclosureIndex]++;
        }
        assertDirectLeafAllocationContract(scenario, mapIndex, directLeafCounts);

        final List<NodeSnapshot> rootChildren = new ArrayList<NodeSnapshot>();
        final List<NodeSnapshot> rootLeaves = leavesByEnclosure.get(0);
        rootChildren.addAll(rootLeaves);
        final SourceNodeKey rootKey = SourceNodeKey.transientPath(mapId,
            Collections.singletonList(Integer.valueOf(0)));
        for (int enclosureIndex = 1; enclosureIndex < enclosureCount; enclosureIndex++) {
            final SourceNodeKey enclosureKey = SourceNodeKey.transientPath(mapId,
                Collections.singletonList(Integer.valueOf(enclosureIndex)));
            final List<NodeSnapshot> children = leavesByEnclosure.get(enclosureIndex);
            rootChildren.add(NodeSnapshot.of(enclosureKey,
                SafeNodeLabel.of(String.format(Locale.ROOT, "m%02d-e%04d", mapIndex, enclosureIndex),
                    String.format(Locale.ROOT, "m%02d-e%04d", mapIndex, enclosureIndex)),
                false, true, false, children));
        }
        if (rootChildren.isEmpty()) {
            throw new IllegalStateException("Generated map root must have children");
        }
        final NodeSnapshot root = NodeSnapshot.of(rootKey,
            SafeNodeLabel.of(String.format(Locale.ROOT, "m%02d-root", mapIndex),
                String.format(Locale.ROOT, "m%02d-root", mapIndex)),
            false, false, false, rootChildren);

        final List<ConnectorSnapshot> connectors = new ArrayList<ConnectorSnapshot>(nativeCount);
        final Map<Integer, Integer> occurrences = new HashMap<Integer, Integer>();
        int emitted = 0;
        for (int first = 0; first < references.size() && emitted < nativeCount; first++) {
            for (int second = first + 1; second < references.size() && emitted < nativeCount; second++) {
                final int occurrence = occurrences.containsKey(first)
                    ? occurrences.get(first).intValue() : 0;
                occurrences.put(first, Integer.valueOf(occurrence + 1));
                final SourceNodeKey source = SourceNodeKey.persisted(references.get(first));
                final ConnectorDescriptor descriptor = ConnectorDescriptor.of(source, references.get(second),
                    false, true, "", "", "");
                connectors.add(ConnectorSnapshot.of(occurrence, descriptor));
                emitted++;
            }
        }
        if (emitted != nativeCount) {
            throw new IllegalStateException("Unable to allocate native connectors for map " + mapIndex);
        }
        final Set<PersistedNodeId> attached = new LinkedHashSet<PersistedNodeId>();
        for (final NodeReference reference : references) {
            attached.add(reference.nodeId());
        }
        return MapSnapshot.of(mapId, mapIndex + 1, String.format(Locale.ROOT, "m%02d", mapIndex), root,
            attached, false).withConnectors(connectors);
    }

    static void assertDirectLeafAllocationContract(final Scenario scenario, final int mapIndex,
            final int[] directLeafCounts) {
        if (scenario == null) {
            throw new NullPointerException("scenario");
        }
        if (mapIndex < 0 || mapIndex >= scenario.mapCount) {
            throw new IllegalArgumentException("Unknown map index for " + scenario.wireName + ": " + mapIndex);
        }
        if (directLeafCounts == null) {
            throw new NullPointerException("directLeafCounts");
        }
        if (directLeafCounts.length != scenario.enclosuresByMap[mapIndex]) {
            throw new IllegalArgumentException("Direct-leaf bucket count does not match " + scenario.wireName
                + " map " + mapIndex);
        }
        final List<Integer> emptyBuckets = new ArrayList<Integer>();
        for (int bucket = 0; bucket < directLeafCounts.length; bucket++) {
            if (directLeafCounts[bucket] < 0) {
                throw new IllegalArgumentException("Direct-leaf count must not be negative");
            }
            if (directLeafCounts[bucket] == 0) {
                emptyBuckets.add(Integer.valueOf(bucket));
            }
        }
        if (scenario == Scenario.SKEWED_REFERENCE && mapIndex == scenario.mapCount - 1) {
            assertSkewedFinalMapEmptyEnclosureContract(emptyBuckets);
            return;
        }
        if (!emptyBuckets.isEmpty()) {
            throw new IllegalStateException("Every enclosure bucket must own a direct leaf for "
                + scenario.wireName + " map " + mapIndex + ": " + emptyBuckets);
        }
    }

    private static void assertSkewedFinalMapEmptyEnclosureContract(final List<Integer> emptyBuckets) {
        if (!SKEWED_FINAL_MAP_EMPTY_ENCLOSURE_BUCKETS.equals(emptyBuckets)) {
            throw new IllegalStateException("Skewed final map must leave exactly enclosure buckets "
                + SKEWED_FINAL_MAP_EMPTY_ENCLOSURE_BUCKETS + " empty: " + emptyBuckets);
        }
    }

    private static List<NodeReference> nodeReferences(final MapReferenceId mapId, final int mapIndex,
            final int nodeCount) {
        final List<NodeReference> result = new ArrayList<NodeReference>(nodeCount);
        for (int nodeIndex = 0; nodeIndex < nodeCount; nodeIndex++) {
            final String id = String.format(Locale.ROOT, "m%02d-n%04d", mapIndex, nodeIndex + 1);
            result.add(NodeReference.of(mapId, PersistedNodeId.of(id)));
        }
        return result;
    }

    private static List<PinRecord> pinsFor(final Scenario scenario) {
        final List<PinRecord> result = new ArrayList<PinRecord>();
        if (scenario == Scenario.ONE_PINNED_MAP || scenario == Scenario.TWO_PINNED_MAPS) {
            result.add(PinRecord.of(NodeReference.of(mapId(0), PersistedNodeId.of("m00-n0001")),
                0.0, 0.0, NO_UNKNOWN_XML));
        }
        if (scenario == Scenario.TWO_PINNED_MAPS) {
            result.add(PinRecord.of(NodeReference.of(mapId(1), PersistedNodeId.of("m01-n0001")),
                0.0, 0.0, NO_UNKNOWN_XML));
        }
        return result;
    }

    private static List<PairAllocation> crossAllocations(final Scenario scenario, final int mapCount) {
        final List<PairAllocation> result = new ArrayList<PairAllocation>();
        switch (scenario) {
        case TWO_MAP:
            appendPair(result, 0, 1, 120);
            break;
        case THREE_MAP_CLUSTERED:
            appendPair(result, 0, 1, 150);
            appendPair(result, 1, 2, 30);
            break;
        case ONE_PINNED_MAP:
        case TWO_PINNED_MAPS:
            appendPair(result, 0, 1, 120);
            appendPair(result, 1, 2, 60);
            break;
        case SKEWED_REFERENCE:
            for (int index = 0; index < 1500; index++) {
                final int second = 1 + (index % (mapCount - 1));
                result.add(new PairAllocation(0, second, index / (mapCount - 1)));
            }
            break;
        case REFERENCE_2000_5000:
            final List<int[]> pairs = new ArrayList<int[]>();
            for (int first = 0; first < mapCount; first++) {
                for (int second = first + 1; second < mapCount; second++) {
                    pairs.add(new int[] {first, second});
                }
            }
            for (int index = 0; index < 1500; index++) {
                final int[] pair = pairs.get(index % pairs.size());
                result.add(new PairAllocation(pair[0], pair[1], index / pairs.size()));
            }
            break;
        default:
            throw new IllegalArgumentException("Unhandled scenario " + scenario);
        }
        return result;
    }

    private static void appendPair(final List<PairAllocation> result, final int first, final int second,
            final int count) {
        for (int index = 0; index < count; index++) {
            result.add(new PairAllocation(first, second, index));
        }
    }

    private static Counts validate(final Scenario scenario, final WorkspaceDocument document,
            final List<MapSnapshot> snapshots, final GraphProjection projection) {
        final int mapCount = document.maps().size();
        int nativeCount = 0;
        for (final MapSnapshot snapshot : snapshots) {
            nativeCount += snapshot.connectors().size();
            if (snapshot.connectors().size() != scenario.nativeByMap[snapshot.workspaceOrder() - 1]) {
                throw new IllegalStateException("Native connector allocation mismatch for "
                    + snapshot.mapReferenceId());
            }
        }
        final int relationshipCount = document.relationships().size();
        final int nodeCount = projection.nodes().size();
        final int enclosureCount = projection.enclosures().size();
        final int[] actualNodesByMap = new int[mapCount];
        final int[] actualEnclosuresByMap = new int[mapCount];
        final Map<MapReferenceId, Integer> mapIndexes = new HashMap<MapReferenceId, Integer>();
        for (int mapIndex = 0; mapIndex < document.maps().size(); mapIndex++) {
            final MapReferenceId mapId = document.maps().get(mapIndex).id();
            if (mapIndexes.put(mapId, Integer.valueOf(mapIndex)) != null) {
                throw new IllegalStateException("Generated workspace map IDs must be unique");
            }
        }
        for (final org.freeplane.plugin.graph.projection.ProjectedNode node : projection.nodes()) {
            final Integer mapIndex = mapIndexes.get(node.mapReferenceId());
            if (mapIndex == null) {
                throw new IllegalStateException("Projected node belongs to an unknown map: " + node);
            }
            actualNodesByMap[mapIndex.intValue()]++;
        }
        for (final org.freeplane.plugin.graph.projection.ProjectedEnclosure enclosure : projection.enclosures()) {
            final Integer mapIndex = mapIndexes.get(enclosure.mapReferenceId());
            if (mapIndex == null) {
                throw new IllegalStateException("Projected enclosure belongs to an unknown map: " + enclosure);
            }
            actualEnclosuresByMap[mapIndex.intValue()]++;
        }
        int containment = 0;
        int hierarchy = 0;
        int contributors = 0;
        int nativeContributors = 0;
        int relationshipContributors = 0;
        for (final org.freeplane.plugin.graph.projection.ProjectedEnclosure enclosure : projection.enclosures()) {
            containment += enclosure.directNodes().size();
            hierarchy += enclosure.directEnclosures().size();
        }
        for (final ProjectedEdge edge : projection.edges()) {
            contributors += edge.contributors().size();
            for (final EdgeContributor contributor : edge.contributors()) {
                if (contributor.connectorDescriptor().isPresent()) {
                    nativeContributors++;
                }
                if (contributor.graphRelationship().isPresent()) {
                    relationshipContributors++;
                }
            }
        }
        final int expectedNodes = 0;
        final int expectedEnclosures = expectedBoundaries(scenario, snapshots);
        final int expectedNative = sum(scenario.nativeByMap);
        final int expectedContainment = 0;
        final int expectedHierarchy = expectedEnclosures - mapCount;
        if (nodeCount != expectedNodes || enclosureCount != expectedEnclosures
                || nativeCount != expectedNative || nativeContributors != expectedNative
                || relationshipContributors != relationshipCount || contributors != expectedNative + relationshipCount
                || projection.edges().size() != expectedNative + relationshipCount
                || containment != expectedContainment || hierarchy != expectedHierarchy) {
            throw new IllegalStateException("Generated projection counts do not match scenario "
                + scenario.wireName + ": nodes=" + nodeCount + ", enclosures=" + enclosureCount
                + ", edges=" + projection.edges().size() + ", contributors=" + contributors
                + ", native=" + nativeContributors + ", relationships=" + relationshipContributors
                + ", containment=" + containment + ", hierarchy=" + hierarchy);
        }
        for (int mapIndex = 0; mapIndex < mapCount; mapIndex++) {
            final int expectedBoundaries = expectedBoundariesFor(scenario, snapshots, mapIndex);
            if (actualNodesByMap[mapIndex] != 0 || actualEnclosuresByMap[mapIndex] != expectedBoundaries) {
                throw new IllegalStateException("Generated projection boundary allocation does not match "
                    + scenario.wireName + " map " + mapIndex + ": nodes=" + actualNodesByMap[mapIndex]
                    + ", enclosures=" + actualEnclosuresByMap[mapIndex] + ", expected boundaries="
                    + expectedBoundaries);
            }
        }
        if (scenario == Scenario.SKEWED_REFERENCE) {
            assertSkewedBoundaryAllocationContract(scenario, actualNodesByMap, actualEnclosuresByMap);
        }

        for (final org.freeplane.plugin.graph.projection.RelationshipResolution resolution
                : projection.relationshipResolutions()) {
            if (resolution.status() != org.freeplane.plugin.graph.projection.RelationshipStatus.ACTIVE) {
                throw new IllegalStateException("Generated relationship did not resolve: " + resolution);
            }
        }
        final int particles = nodeCount + enclosureCount;
        final int springs = projection.edges().size() + containment + hierarchy;
        return new Counts(mapCount, nodeCount, enclosureCount, projection.edges().size(), contributors,
            nativeContributors, relationshipContributors, relationshipCount, containment, hierarchy,
            particles, springs);
    }

    private static int expectedBoundaries(final Scenario scenario, final List<MapSnapshot> snapshots) {
        int total = 0;
        for (int mapIndex = 0; mapIndex < snapshots.size(); mapIndex++) {
            total += expectedBoundariesFor(scenario, snapshots, mapIndex);
        }
        return total;
    }

    public static int expectedBoundaryCount(final Scenario scenario, final List<MapSnapshot> snapshots) {
        return expectedBoundaries(scenario, snapshots);
    }

    private static int expectedBoundariesFor(final Scenario scenario, final List<MapSnapshot> snapshots,
            final int mapIndex) {
        int chainedContainers = 0;
        for (final NodeSnapshot child : snapshots.get(mapIndex).root().children()) {
            if (child.excluded() || child.structuralLeaf()) {
                continue;
            }
            int visibleChildren = 0;
            for (final NodeSnapshot grandchild : child.children()) {
                if (!grandchild.excluded()) {
                    visibleChildren++;
                }
            }
            if (visibleChildren == 1) {
                chainedContainers++;
            }
        }
        return scenario.nodesByMap[mapIndex] + scenario.enclosuresByMap[mapIndex] - chainedContainers;
    }

    static void assertSkewedMapAllocationContract(final Scenario scenario, final int[] actualNodesByMap,
            final int[] actualEnclosuresByMap) {
        if (scenario == null) {
            throw new IllegalArgumentException("scenario");
        }
        if (actualNodesByMap == null) {
            throw new IllegalArgumentException("actualNodesByMap");
        }
        if (actualEnclosuresByMap == null) {
            throw new IllegalArgumentException("actualEnclosuresByMap");
        }
        if (scenario != Scenario.SKEWED_REFERENCE) {
            throw new IllegalArgumentException("Skewed map allocation applies only to SKEWED_REFERENCE");
        }
        if (actualNodesByMap.length != scenario.mapCount
                || actualEnclosuresByMap.length != scenario.mapCount) {
            throw new IllegalArgumentException("Skewed map allocation must contain exactly "
                + scenario.mapCount + " map buckets");
        }
        final int totalNodes = sum(actualNodesByMap);
        final int totalEnclosures = sum(actualEnclosuresByMap);
        if (totalNodes != 2000 || totalEnclosures != 1200
                || actualNodesByMap[0] != 1600 || actualEnclosuresByMap[0] != 960
                || actualNodesByMap[0] * 5 != totalNodes * 4
                || actualEnclosuresByMap[0] * 5 != totalEnclosures * 4) {
            throw new IllegalStateException("Skewed map allocation does not match the reference contract: nodes="
                + Arrays.toString(actualNodesByMap) + ", enclosures="
                + Arrays.toString(actualEnclosuresByMap));
        }
    }

    static void assertSkewedBoundaryAllocationContract(final Scenario scenario, final int[] actualNodesByMap,
            final int[] actualEnclosuresByMap) {
        if (scenario == null) {
            throw new IllegalArgumentException("scenario");
        }
        if (actualNodesByMap == null) {
            throw new IllegalArgumentException("actualNodesByMap");
        }
        if (actualEnclosuresByMap == null) {
            throw new IllegalArgumentException("actualEnclosuresByMap");
        }
        if (scenario != Scenario.SKEWED_REFERENCE) {
            throw new IllegalArgumentException("Skewed boundary allocation applies only to SKEWED_REFERENCE");
        }
        if (actualNodesByMap.length != scenario.mapCount
                || actualEnclosuresByMap.length != scenario.mapCount) {
            throw new IllegalArgumentException("Skewed boundary allocation must contain exactly "
                + scenario.mapCount + " map buckets");
        }
        final int totalNodes = sum(actualNodesByMap);
        final int totalEnclosures = sum(actualEnclosuresByMap);
        if (totalNodes != 0 || totalEnclosures != 2805
                || actualNodesByMap[0] != 0 || actualEnclosuresByMap[0] != 2240) {
            throw new IllegalStateException("Skewed boundary allocation does not match the reference contract:"
                + " nodes=" + Arrays.toString(actualNodesByMap) + ", enclosures="
                + Arrays.toString(actualEnclosuresByMap));
        }
    }

    private static int sum(final int[] values) {
        int result = 0;
        for (final int value : values) {
            result += value;
        }
        return result;
    }

    private static int floorMod(final long value, final int modulus) {
        final long remainder = value % modulus;
        return (int) (remainder < 0L ? remainder + modulus : remainder);
    }

    private static MapReferenceId mapId(final int mapIndex) {
        return MapReferenceId.of(String.format(Locale.ROOT,
            "00000000-0000-0000-0000-%012d", mapIndex + 1));
    }

    private static int[] repeat(final int count, final int value) {
        final int[] result = new int[count];
        Arrays.fill(result, value);
        return result;
    }

    private static final class BuildResult {
        private final WorkspaceDocument document;
        private final List<MapSnapshot> snapshots;
        private final Map<MapReferenceId, MapAvailability> availability;

        private BuildResult(final WorkspaceDocument document, final List<MapSnapshot> snapshots,
                final Map<MapReferenceId, MapAvailability> availability) {
            this.document = document;
            this.snapshots = Collections.unmodifiableList(new ArrayList<MapSnapshot>(snapshots));
            this.availability = Collections.unmodifiableMap(new LinkedHashMap<MapReferenceId, MapAvailability>(
                availability));
        }
    }

    private static final class PairAllocation {
        private final int firstMap;
        private final int secondMap;
        private final long ordinal;

        private PairAllocation(final int firstMap, final int secondMap, final long ordinal) {
            this.firstMap = firstMap;
            this.secondMap = secondMap;
            this.ordinal = ordinal;
        }
    }

    public static final class Counts {
        private final int mapCount;
        private final int nodeCount;
        private final int enclosureCount;
        private final int edgeCount;
        private final int contributorCount;
        private final int nativeContributorCount;
        private final int crossMapContributorCount;
        private final int relationshipCount;
        private final int containmentLinkCount;
        private final int hierarchyLinkCount;
        private final int particleCount;
        private final int springCount;

        private Counts(final int mapCount, final int nodeCount, final int enclosureCount, final int edgeCount,
                final int contributorCount, final int nativeContributorCount,
                final int crossMapContributorCount, final int relationshipCount,
                final int containmentLinkCount, final int hierarchyLinkCount, final int particleCount,
                final int springCount) {
            this.mapCount = mapCount;
            this.nodeCount = nodeCount;
            this.enclosureCount = enclosureCount;
            this.edgeCount = edgeCount;
            this.contributorCount = contributorCount;
            this.nativeContributorCount = nativeContributorCount;
            this.crossMapContributorCount = crossMapContributorCount;
            this.relationshipCount = relationshipCount;
            this.containmentLinkCount = containmentLinkCount;
            this.hierarchyLinkCount = hierarchyLinkCount;
            this.particleCount = particleCount;
            this.springCount = springCount;
        }

        public int mapCount() { return mapCount; }
        public int nodeCount() { return nodeCount; }
        public int enclosureCount() { return enclosureCount; }
        public int edgeCount() { return edgeCount; }
        public int projectedEdgeCount() { return edgeCount; }
        public int contributorCount() { return contributorCount; }
        public int nativeContributorCount() { return nativeContributorCount; }
        public int crossMapContributorCount() { return crossMapContributorCount; }
        public int relationshipCount() { return relationshipCount; }
        public int containmentLinkCount() { return containmentLinkCount; }
        public int hierarchyLinkCount() { return hierarchyLinkCount; }
        public int particleCount() { return particleCount; }
        public int springCount() { return springCount; }

        @Override
        public String toString() {
            return "Counts{" + "maps=" + mapCount + ", nodes=" + nodeCount
                + ", enclosures=" + enclosureCount + ", edges=" + edgeCount
                + ", contributors=" + contributorCount + ", particles=" + particleCount
                + ", springs=" + springCount + '}';
        }
    }
}
