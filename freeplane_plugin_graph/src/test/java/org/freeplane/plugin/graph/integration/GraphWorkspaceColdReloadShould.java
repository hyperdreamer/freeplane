package org.freeplane.plugin.graph.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import javax.swing.Timer;

import org.freeplane.core.undo.IActor;
import org.freeplane.core.undo.IUndoHandler;
import org.freeplane.core.ui.menubuilders.generic.Entry;
import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.util.Compat;
import org.freeplane.features.link.NodeLinks;
import org.freeplane.features.map.IMapLifeCycleListener;
import org.freeplane.features.map.IMapSelection;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.mindmapmode.MMapController;
import org.freeplane.features.map.mindmapmode.MMapModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.features.ui.IMapViewManager;
import org.freeplane.features.ui.ViewController;
import org.freeplane.features.url.MapVersionInterpreter;
import org.freeplane.features.url.mindmapmode.MFileManager;
import org.freeplane.features.url.mindmapmode.MapLoader;
import org.freeplane.main.application.ApplicationResourceController;
import org.freeplane.main.application.CommandLineParser;
import org.freeplane.main.headlessmode.FreeplaneHeadlessStarter;
import org.freeplane.main.headlessmode.HeadlessUIController;
import org.freeplane.plugin.graph.control.DefaultGraphWorkspaceController;
import org.freeplane.plugin.graph.control.GraphWorkspaceHandle;
import org.freeplane.plugin.graph.control.GraphWorkspaceView;
import org.freeplane.plugin.graph.control.GraphWorkspaceViewBinding;
import org.freeplane.plugin.graph.control.GraphWorkspaceViewFactory;
import org.freeplane.plugin.graph.control.WorkspaceCloseController;
import org.freeplane.plugin.graph.group.GraphGroupController;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.NodeProminence;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.RelationshipResolution;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.command.GraphCommands;
import org.freeplane.plugin.graph.workspace.GraphCommandResult;
import org.freeplane.plugin.graph.workspace.ListenerRegistration;
import org.freeplane.plugin.graph.workspace.io.WorkspaceMigrationRegistry;
import org.freeplane.plugin.graph.workspace.io.WorkspaceXmlCodec;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.freeplane.plugin.graph.workspace.model.RelationshipDirection;
import org.freeplane.plugin.graph.workspace.model.Viewport;
import org.freeplane.plugin.graph.workspace.model.WorkspaceDocument;
import org.freeplane.features.mapio.mindmapmode.MMapIO;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class GraphWorkspaceColdReloadShould {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void coldReloadsProductionWorkspaceStateAndProjection() throws Exception {
        final GraphWorkspaceIntegrationSupport.FreeplaneScope freeplane =
            new GraphWorkspaceIntegrationSupport.FreeplaneScope();
        DefaultGraphWorkspaceController controller = null;
        GraphWorkspaceHandle handle = null;
        MapModel sourceMap = null;
        try {
            final ExecutorService scopeExecutor = freeplane.controller().getMainThreadExecutorService();
            final GraphWorkspaceIntegrationSupport.FreeplaneScope nestedScope =
                new GraphWorkspaceIntegrationSupport.FreeplaneScope();
            try {
                assertThat(nestedScope.controller()).isSameAs(freeplane.controller());
                assertThat(nestedScope.controller().getMainThreadExecutorService())
                    .as("each acceptance scope has its own main-thread executor")
                    .isNotSameAs(scopeExecutor);
            }
            finally {
                nestedScope.close();
            }
            assertThat(freeplane.controller().getMainThreadExecutorService()).isSameAs(scopeExecutor);

            final Path sourceMapFile = temporaryFolder.getRoot().toPath().resolve("source.mm");
            GraphWorkspaceIntegrationSupport.copyFixture(sourceMapFile);
            freeplane.installGraphGroups();
            sourceMap = freeplane.loadWithView(sourceMapFile);
            assertThat(sourceMap.getExtension(IUndoHandler.class)).as("editor map undo handler").isNotNull();
            final List<NodeModel> actorNodes = freeplane.applyRandomActors(sourceMap, 0x41BA7CL, 5);
            freeplane.applyRandomGroups(actorNodes, 0x41BA7CL);

            final GraphWorkspaceIntegrationSupport.RecordingViewFactory views =
                new GraphWorkspaceIntegrationSupport.RecordingViewFactory();
            controller = new DefaultGraphWorkspaceController(freeplane.modeController(), views);
            final Path workspaceFile = temporaryFolder.getRoot().toPath().resolve("graph.fpg");
            handle = controller.open(workspaceFile);
            final MapReferenceId mapId = MapReferenceId.of(UUID.nameUUIDFromBytes(
                sourceMapFile.toAbsolutePath().toString().getBytes(StandardCharsets.UTF_8)));
            final GraphCommandResult addMap = handle.execute(GraphCommands.addMap(mapId, sourceMapFile.toUri()));
            assertThat(addMap.status()).isEqualTo(GraphCommandResult.Status.APPLIED);
            GraphWorkspaceIntegrationSupport.awaitProjection(handle, 1);

            final List<NodeModel> connectorTargets = new ArrayList<NodeModel>();
            connectorTargets.add(sourceMap.getRootNode());
            connectorTargets.addAll(actorNodes);
            final List<int[]> connectorPairs = new ArrayList<int[]>();
            connectorPairs.add(new int[] { 0, 1 });
            connectorPairs.add(new int[] { 1, 2 });
            connectorPairs.add(new int[] { 2, 3 });
            connectorPairs.add(new int[] { 3, 4 });
            connectorPairs.add(new int[] { 4, 5 });
            final Random connectorRandom = new Random(0xC0FFEE);
            Collections.shuffle(connectorPairs, connectorRandom);
            final RelationshipDirection[] directions = RelationshipDirection.values();
            for (int index = 0; index < 3; index++) {
                final int[] pair = connectorPairs.get(index);
                final NodeModel source = connectorTargets.get(pair[0]);
                final NodeModel target = connectorTargets.get(pair[1]);
                final SourceNodeKey sourceKey = persisted(mapId, source);
                final SourceNodeKey targetKey = persisted(mapId, target);
                final RelationshipDirection direction = directions[connectorRandom.nextInt(directions.length)];
                final GraphCommandResult connector = handle.execute(GraphCommands.connect(sourceKey, targetKey,
                    direction));
                assertThat(connector.status()).as("connector %s", index)
                    .isEqualTo(GraphCommandResult.Status.APPLIED);
            }
            GraphWorkspaceIntegrationSupport.awaitProjection(handle, 1);

            final IUndoHandler mapUndo = sourceMap.getExtension(IUndoHandler.class);
            assertThat(mapUndo.canUndo()).as("map-owned history exists before the workspace history probe")
                .isTrue();
            final GraphCommandResult undoWorkspace = handle.execute(GraphCommands.undoWorkspace());
            assertThat(undoWorkspace.status()).isEqualTo(GraphCommandResult.Status.APPLIED);
            assertThat(mapUndo.canUndo()).as("workspace undo must not consume map-owned undo").isTrue();
            final GraphCommandResult redoWorkspace = handle.execute(GraphCommands.redoWorkspace());
            assertThat(redoWorkspace.status()).isEqualTo(GraphCommandResult.Status.APPLIED);
            GraphWorkspaceIntegrationSupport.awaitProjection(handle, 1);

            final GraphCommandResult undoSourceMap = handle.execute(GraphCommands.undoSourceMap());
            assertThat(undoSourceMap.status()).as("map-owned undo is routed to Freeplane").isEqualTo(
                GraphCommandResult.Status.APPLIED);
            GraphWorkspaceIntegrationSupport.awaitProjection(handle, 1);
            freeplane.redoMapAction(mapUndo);
            GraphWorkspaceIntegrationSupport.awaitProjectionWithEdges(handle, 1, 3);

            freeplane.markMapDirtyWithoutChangingContent(sourceMap);
            assertThat(freeplane.saveMapAsUserAction(sourceMap)).isTrue();
            assertThat(sourceMap.isSaved()).isTrue();
            final byte[] sourceBytesAfterExplicitSave = Files.readAllBytes(sourceMapFile);
            assertThat(new String(sourceBytesAfterExplicitSave, StandardCharsets.UTF_8)).contains("arrowlink");
            final FileTime sourceTimeAfterExplicitSave = Files.getLastModifiedTime(sourceMapFile);
            final WorkspaceXmlCodec codec = productionCodec();
            final WorkspaceDocument persistedWorkspace = codec.read(workspaceFile);
            final GraphWorkspaceIntegrationSupport.ProjectionState expectedProjection =
                GraphWorkspaceIntegrationSupport.ProjectionState.of(handle.currentProjection());
            final List<String> expectedRows = GraphWorkspaceIntegrationSupport.rows(views.latestBinding());
            final Viewport expectedViewport = views.latestBinding().currentViewport();

            final GraphCommandResult save = handle.execute(GraphCommands.save());
            assertThat(save.status()).isEqualTo(GraphCommandResult.Status.APPLIED);
            assertThat(codec.read(workspaceFile)).isEqualTo(persistedWorkspace);

            // Keep the in-memory map dirty without changing its persisted bytes. A workspace save must not save it.
            freeplane.markMapDirtyWithoutChangingContent(sourceMap);
            assertThat(sourceMap.isSaved()).isFalse();
            handle.close();
            handle = null;
            assertThat(sourceMap.isSaved()).as("workspace close must not invoke the map writer").isFalse();
            assertThat(Files.readAllBytes(sourceMapFile)).containsExactly(sourceBytesAfterExplicitSave);
            assertThat(Files.getLastModifiedTime(sourceMapFile)).isEqualTo(sourceTimeAfterExplicitSave);

            freeplane.closeMap(sourceMap);
            sourceMap = null;
            handle = controller.open(workspaceFile);
            GraphWorkspaceIntegrationSupport.awaitProjectionWithEdges(handle, 1, 3);
            final GraphWorkspaceIntegrationSupport.RecordingViewFactory reopenedViews = views;
            assertThat(codec.read(workspaceFile)).isEqualTo(persistedWorkspace);
            assertThat(GraphWorkspaceIntegrationSupport.connectorCount(freeplane.mapAt(sourceMapFile)))
                .as("cold-loaded native connectors").isEqualTo(3);
            assertThat(GraphWorkspaceIntegrationSupport.rows(reopenedViews.latestBinding()))
                .containsExactlyElementsOf(expectedRows);
            assertThat(reopenedViews.latestBinding().currentViewport()).isEqualTo(expectedViewport);
            final GraphWorkspaceIntegrationSupport.ProjectionState reopenedProjection =
                GraphWorkspaceIntegrationSupport.ProjectionState.of(handle.currentProjection());
            assertThat(reopenedProjection.nodes).as("projected nodes").containsExactlyElementsOf(expectedProjection.nodes);
            assertThat(reopenedProjection.enclosures).as("projected enclosures")
                .containsExactlyElementsOf(expectedProjection.enclosures);
            assertThat(reopenedProjection.edges).as("projected edges").containsExactlyElementsOf(expectedProjection.edges);
            assertThat(reopenedProjection.resolutions).as("relationship resolutions")
                .containsExactlyElementsOf(expectedProjection.resolutions);
            assertThat(reopenedProjection.pins).as("pins").containsExactlyElementsOf(expectedProjection.pins);
            assertThat(reopenedProjection.prominence).as("node prominence").isEqualTo(expectedProjection.prominence);
        }
        finally {
            if (handle != null) {
                try {
                    handle.close();
                }
                catch (RuntimeException ignored) {
                    // The controller shutdown below performs deterministic discard cleanup for a failed close.
                }
            }
            if (controller != null) {
                try {
                    controller.shutdown();
                }
                catch (RuntimeException ignored) {
                    // Preserve the original assertion failure while still releasing the headless Freeplane scope.
                }
            }
            if (sourceMap != null) {
                freeplane.closeMap(sourceMap);
            }
            freeplane.close();
        }
    }

    private static SourceNodeKey persisted(final MapReferenceId mapId, final NodeModel node) {
        return SourceNodeKey.persisted(org.freeplane.plugin.graph.workspace.model.NodeReference.of(mapId,
            PersistedNodeId.of(node.getID())));
    }

    private static WorkspaceXmlCodec productionCodec() {
        return new WorkspaceXmlCodec(new WorkspaceMigrationRegistry(Collections.emptyList()));
    }
}

final class GraphWorkspaceIntegrationSupport {
    private static final String[] GRAPH_THREAD_PREFIXES = {
        "freeplane-graph-workspace-store",
        "freeplane-graph-map-lease-check",
        "freeplane-graph-map-lease-deferred-drain",
        "freeplane-graph-projection-batcher",
        "freeplane-graph-layout-worker-",
        "freeplane-graph-layout-lifecycle-",
        "freeplane-graph-update-coordinator-shutdown",
        "freeplane-graph-workspace-shutdown",
        "freeplane-graph-workspace-close",
        "freeplane-graph-workspace-open-rollback",
        "freeplane-graph-workspace-session-shutdown-wait",
        "freeplane-graph-workspace-shutdown-wait"
    };

    private GraphWorkspaceIntegrationSupport() {
    }

    static void copyFixture(final Path target) throws Exception {
        try (InputStream input = GraphWorkspaceColdReloadShould.class.getResourceAsStream(
                "/maps/graph-projection.mm")) {
            if (input == null) {
                throw new IOException("Missing graph projection fixture");
            }
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static void awaitProjection(final GraphWorkspaceHandle handle, final int minimumNodes) throws Exception {
        awaitCondition(() -> handle.currentProjection().projectedNodeCount() >= minimumNodes, 15000L,
            "production graph projection did not become available");
    }

    static void awaitProjectionWithEdges(final GraphWorkspaceHandle handle, final int minimumNodes,
            final int minimumEdges) throws Exception {
        awaitCondition(() -> handle.currentProjection().projectedNodeCount() >= minimumNodes
            && handle.currentProjection().projectedEdgeCount() >= minimumEdges, 15000L,
            "production graph projection did not publish the expected native edges");
    }

    static void awaitCondition(final BooleanSupplier condition, final long timeoutMillis, final String failureMessage)
            throws Exception {
        final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(20L);
        }
        assertThat(condition.getAsBoolean()).as(failureMessage).isTrue();
    }

    static void deleteRecursively(final Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> children = Files.newDirectoryStream(path)) {
                for (Path child : children) {
                    deleteRecursively(child);
                }
            }
        }
        Files.deleteIfExists(path);
    }

    static Path blockWorkspaceTarget(final Path workspace) throws IOException {
        final Path preserved = workspace.resolveSibling(workspace.getFileName().toString() + ".preserved");
        Files.move(workspace, preserved, StandardCopyOption.REPLACE_EXISTING);
        Files.createDirectory(workspace);
        Files.write(workspace.resolve("do-not-replace"), new byte[] { 7, 3, 1 });
        return preserved;
    }

    static void restoreWorkspaceTarget(final Path workspace, final Path preserved) throws IOException {
        deleteRecursively(workspace);
        Files.move(preserved, workspace, StandardCopyOption.REPLACE_EXISTING);
    }

    static ResourceBaseline baseline(final FreeplaneScope freeplane, final Path root) throws Exception {
        return new ResourceBaseline(freeplane.mapLifecycleListenerCount(), freeplane.mapChangeListenerCount(),
            freeplane.viewCount(), freeplane.timerCount(), graphThreadProfile(), temporaryArtifacts(root));
    }

    static void awaitBaseline(final FreeplaneScope freeplane, final Path root, final ResourceBaseline expected)
            throws Exception {
        awaitCondition(() -> expected.matches(freeplane, root), 15000L,
            "graph workspace resources did not return to baseline");
        assertThat(freeplane.mapLifecycleListenerCount()).isEqualTo(expected.mapLifecycleListeners);
        assertThat(freeplane.mapChangeListenerCount()).isEqualTo(expected.mapChangeListeners);
        assertThat(freeplane.viewCount()).isEqualTo(expected.views);
        assertThat(freeplane.timerCount()).isEqualTo(expected.timers);
        assertThat(graphThreadProfile()).isEqualTo(expected.threads);
        assertThat(temporaryArtifacts(root)).isEqualTo(expected.temporaryArtifacts);
    }

    static Map<String, Integer> graphThreadProfile() {
        final Map<String, Integer> profile = new LinkedHashMap<String, Integer>();
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            final String name = thread.getName();
            final String prefix = graphThreadPrefix(name);
            if (prefix != null) {
                final Integer count = profile.get(prefix);
                profile.put(prefix, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
            }
        }
        return profile;
    }

    static Set<String> temporaryArtifacts(final Path root) throws IOException {
        final Set<String> result = new java.util.TreeSet<String>();
        if (!Files.exists(root)) {
            return result;
        }
        Files.walk(root).forEach(path -> {
            if (Files.isRegularFile(path) || Files.isDirectory(path)) {
                final String name = path.getFileName().toString();
                if (name.contains(".tmp") || name.startsWith(".freeplane-") || name.endsWith(".preserved")
                        || name.endsWith(".lock") || name.endsWith(".~lock~") || name.equals("do-not-replace")) {
                    result.add(root.relativize(path).toString());
                }
            }
        });
        return result;
    }

    static String graphThreadPrefix(final String name) {
        for (String prefix : GRAPH_THREAD_PREFIXES) {
            if (name.startsWith(prefix)) {
                return prefix;
            }
        }
        return null;
    }

    static int connectorCount(final MapModel map) {
        return map == null ? 0 : connectorCount(map.getRootNode());
    }

    private static int connectorCount(final NodeModel node) {
        if (node == null) {
            return 0;
        }
        int count = NodeLinks.getLinks(node).size();
        for (NodeModel child : node.getChildren()) {
            count += connectorCount(child);
        }
        return count;
    }

    static List<String> rows(final GraphWorkspaceViewBinding binding) {
        final List<String> rows = new ArrayList<String>();
        for (GraphWorkspaceViewBinding.MapRegistration row : binding.currentMapRows()) {
            rows.add(row.mapReferenceId().value().toString() + "|" + row.displayName() + "|"
                + row.availability().name());
        }
        return rows;
    }

    static final class ProjectionState {
        final List<ProjectedNode> nodes;
        final List<ProjectedEnclosure> enclosures;
        final List<ProjectedEdge> edges;
        final List<RelationshipResolution> resolutions;
        final List<PinProjection> pins;
        final Map<ProjectedNodeKey, NodeProminence> prominence;

        private ProjectionState(final GraphProjection projection) {
            nodes = projection.nodes();
            enclosures = projection.enclosures();
            edges = projection.edges();
            resolutions = projection.relationshipResolutions();
            pins = projection.pins();
            prominence = projection.prominence();
        }

        static ProjectionState of(final GraphProjection projection) {
            return new ProjectionState(projection);
        }

        @Override
        public boolean equals(final Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ProjectionState)) {
                return false;
            }
            final ProjectionState that = (ProjectionState) other;
            return nodes.equals(that.nodes) && enclosures.equals(that.enclosures) && edges.equals(that.edges)
                && resolutions.equals(that.resolutions) && pins.equals(that.pins)
                && prominence.equals(that.prominence);
        }

        @Override
        public int hashCode() {
            return Objects.hash(nodes, enclosures, edges, resolutions, pins, prominence);
        }
    }

    static final class RecordingViewFactory implements GraphWorkspaceViewFactory {
        private final List<GraphWorkspaceViewBinding> bindings = new ArrayList<GraphWorkspaceViewBinding>();
        private final List<WorkspaceCloseController> closeControllers = new ArrayList<WorkspaceCloseController>();
        private int activeViews;
        private int showCalls;
        private int closeCalls;

        @Override
        public synchronized GraphWorkspaceView create(final org.freeplane.plugin.graph.control.GraphWorkspaceHandle handle,
                final GraphWorkspaceViewBinding binding, final WorkspaceCloseController close) {
            bindings.add(binding);
            closeControllers.add(close);
            return new GraphWorkspaceView() {
                @Override
                public void show() {
                    synchronized (RecordingViewFactory.this) {
                        activeViews++;
                        showCalls++;
                    }
                }

                @Override
                public void focus() {
                }

                @Override
                public void close() {
                    synchronized (RecordingViewFactory.this) {
                        activeViews--;
                        closeCalls++;
                    }
                }
            };
        }

        synchronized GraphWorkspaceViewBinding latestBinding() {
            if (bindings.isEmpty()) {
                throw new AssertionError("No graph workspace view was created");
            }
            return bindings.get(bindings.size() - 1);
        }

        synchronized WorkspaceCloseController latestCloseController() {
            if (closeControllers.isEmpty()) {
                throw new AssertionError("No graph workspace close controller was created");
            }
            return closeControllers.get(closeControllers.size() - 1);
        }

        synchronized int activeViews() {
            return activeViews;
        }

        synchronized int showCalls() {
            return showCalls;
        }

        synchronized int closeCalls() {
            return closeCalls;
        }
    }

    static final class ResourceBaseline {
        final int mapLifecycleListeners;
        private final int mapChangeListeners;
        private final int views;
        private final int timers;
        private final Map<String, Integer> threads;
        private final Set<String> temporaryArtifacts;

        private ResourceBaseline(final int mapLifecycleListeners, final int mapChangeListeners, final int views,
                final int timers, final Map<String, Integer> threads, final Set<String> temporaryArtifacts) {
            this.mapLifecycleListeners = mapLifecycleListeners;
            this.mapChangeListeners = mapChangeListeners;
            this.views = views;
            this.timers = timers;
            this.threads = threads;
            this.temporaryArtifacts = temporaryArtifacts;
        }

        private boolean matches(final FreeplaneScope freeplane, final Path root) {
            try {
                return mapLifecycleListeners == freeplane.mapLifecycleListenerCount()
                    && mapChangeListeners == freeplane.mapChangeListenerCount()
                    && views == freeplane.viewCount() && timers == freeplane.timerCount()
                    && threads.equals(graphThreadProfile())
                    && temporaryArtifacts.equals(temporaryArtifacts(root));
            }
            catch (Exception failure) {
                return false;
            }
        }
    }

    private static final class ActionRegistrationController extends Controller {
        ActionRegistrationController(final Controller source) {
            super(source.getResourceController());
        }

        @Override
        public void addAction(final AFreeplaneAction action) {
        }
    }

    private static final class ScopeHeadlessUIController extends HeadlessUIController {
        private final ExecutorService executorService;
        private volatile Thread dispatchThread;

        ScopeHeadlessUIController(final Controller controller, final IMapViewManager mapViewManager) {
            super(new ActionRegistrationController(controller), mapViewManager, "");
            executorService = Executors.newSingleThreadExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(final Runnable runnable) {
                    final Thread thread = Executors.defaultThreadFactory().newThread(runnable);
                    dispatchThread = thread;
                    return thread;
                }
            });
        }

        @Override
        public boolean isDispatchThread() {
            return Thread.currentThread() == dispatchThread;
        }

        @Override
        public ExecutorService getMainThreadExecutorService() {
            return executorService;
        }

        @Override
        public void invokeLater(final Runnable runnable) {
            executorService.execute(runnable);
        }

        @Override
        public void invokeAndWait(final Runnable runnable)
                throws InterruptedException, InvocationTargetException {
            try {
                if (isDispatchThread()) {
                    runnable.run();
                }
                else if (!executorService.isShutdown()) {
                    executorService.submit(runnable).get();
                }
            }
            catch (ExecutionException failure) {
                throw new InvocationTargetException(failure);
            }
        }

        void shutdownExecutor() {
            executorService.shutdown();
        }
    }

    static final class FreeplaneScope implements AutoCloseable {
        private final Controller previousController;
        private final ViewController previousViewController;
        private final IMapViewManager previousMapViewManager;
        private final MapVersionInterpreter[] previousInterpreters;
        private final HeadlessResourceFiles resources;
        private final boolean ownsStarter;
        private final ScopeHeadlessUIController scopeViewController;
        private final Entry previousMenuStructure;
        private final Controller controller;
        private final ModeController modeController;
        private final MMapController mapController;
        private final Set<MapModel> openedMaps = Collections.newSetFromMap(
            new IdentityHashMap<MapModel, Boolean>());
        private final Set<MapModel> observedMaps = Collections.newSetFromMap(
            new IdentityHashMap<MapModel, Boolean>());
        private final IMapLifeCycleListener observer = new IMapLifeCycleListener() {
            @Override
            public void onCreate(final MapModel map) {
                observedMaps.add(map);
            }

            @Override
            public void onRemove(final MapModel map) {
                observedMaps.remove(map);
            }
        };
        private boolean observing;
        private GraphGroupController graphGroups;
        private boolean closed;

        FreeplaneScope() throws Exception {
            previousController = Controller.getCurrentController();
            previousInterpreters = mapVersionInterpreters();
            final ModeController existing = previousController == null ? null
                : previousController.getModeController(MModeController.MODENAME);
            if (existing != null && existing.getMapController() instanceof MMapController) {
                resources = null;
                ownsStarter = false;
                controller = previousController;
                modeController = existing;
                previousViewController = controller.getViewController();
                previousMapViewManager = controller.getMapViewManager();
            }
            else {
                resources = new HeadlessResourceFiles();
                final FreeplaneHeadlessStarter starter = new FreeplaneHeadlessStarter(CommandLineParser.parse());
                controller = starter.createController();
                starter.createModeControllers(controller);
                starter.createFrame();
                ownsStarter = true;
                modeController = controller.getModeController(MModeController.MODENAME);
                previousViewController = null;
                previousMapViewManager = null;
            }
            controller.setMapViewManager(selectableMapViewManager(controller.getMapViewManager()));
            scopeViewController = new ScopeHeadlessUIController(controller, controller.getMapViewManager());
            controller.setViewController(scopeViewController);
            mapController = (MMapController) modeController.getMapController();
            MapVersionInterpreter.addMapVersionInterpreter(new MapVersionInterpreter("GRAPH_WORKSPACE_INTEGRATION",
                19, "freeplane 1.12.0", false, false, "Freeplane", "https://www.freeplane.org", null, null));
            previousMenuStructure = replaceMenuStructure(modeController, new Entry());
        }

        ModeController modeController() {
            return modeController;
        }

        Controller controller() {
            return controller;
        }

        void installGraphGroups() {
            graphGroups = new GraphGroupController(modeController);
        }

        MapModel loadWithView(final Path source) throws Exception {
            final MapModel map = new MapLoader(modeController).load(source.toUri().toURL()).withView().getMap();
            if (map == null) {
                throw new IOException("MapLoader returned no map for " + source);
            }
            assertThat(controller.getMapViewManager().getMaps().values()).contains(map);
            // The proxy-backed headless view supplies a real current-map selection to Freeplane undo actors.
            openedMaps.add(map);
            return map;
        }

        void observeMaps() {
            if (observing) {
                return;
            }
            observing = true;
            mapController.addMapLifeCycleListener(observer);
            observedMaps.addAll(new ArrayList<MapModel>(controller.getMapViewManager().getMaps().values()));
        }

        MapModel mapAt(final Path source) throws Exception {
            return mapController.getMap(source.toUri().toURL());
        }

        void closeMap(final MapModel map) {
            if (map == null) {
                return;
            }
            mapController.closeWithoutSaving(map);
            openedMaps.remove(map);
            observedMaps.remove(map);
        }

        void closeMapAt(final Path source) throws Exception {
            closeMap(mapAt(source));
        }

        boolean saveMapAsUserAction(final MapModel map) {
            return MFileManager.getController(modeController).save(map);
        }

        void redoMapAction(final IUndoHandler undo) {
            undo.redo();
        }

        void markMapDirtyWithoutChangingContent(final MapModel map) {
            mapController.mapSaved(map, false);
        }

        List<NodeModel> applyRandomActors(final MapModel map, final long seed, final int count) {
            final Random random = new Random(seed);
            final List<NodeModel> nodes = new ArrayList<NodeModel>();
            final NodeModel root = map.getRootNode();
            for (int index = 0; index < count; index++) {
                final int nodeIndex = index;
                final NodeModel node = mapController.addNewNode(root, root.getChildCount(), value -> {
                    value.setText("actor-" + nodeIndex + "-" + random.nextInt(1000));
                    value.setID("ID_ACTOR_" + nodeIndex);
                });
                if (node == null) {
                    throw new AssertionError("Freeplane did not create actor node " + index);
                }
                nodes.add(node);
            }
            for (final NodeModel node : nodes) {
                final String before = node.getText();
                final String after = before + "-edited";
                modeController.execute(new IActor() {
                    @Override
                    public void act() {
                        node.setText(after);
                        mapController.nodeChanged(node);
                    }

                    @Override
                    public String getDescription() {
                        return "graph-workspace-integration-text-edit";
                    }

                    @Override
                    public void undo() {
                        node.setText(before);
                        mapController.nodeChanged(node);
                    }
                }, map);
            }
            return nodes;
        }

        void applyRandomGroups(final List<NodeModel> nodes, final long seed) {
            if (graphGroups == null) {
                throw new IllegalStateException("Graph groups are not installed");
            }
            final Random random = new Random(seed ^ 0x47524F5550L);
            final List<NodeModel> selected = new ArrayList<NodeModel>();
            for (NodeModel node : nodes) {
                if (random.nextBoolean()) {
                    selected.add(node);
                }
            }
            if (selected.isEmpty()) {
                selected.add(nodes.get(0));
            }
            graphGroups.setMarked(selected, true);
        }

        int mapLifecycleListenerCount() {
            return mapController.getMapLifeCycleListeners().size();
        }

        int mapChangeListenerCount() throws Exception {
            int result = 0;
            for (MapModel map : new ArrayList<MapModel>(observedMaps)) {
                result += mapChangeListeners(map);
            }
            return result;
        }

        int viewCount() {
            return controller.getMapViewManager().getMaps().size();
        }

        int timerCount() {
            int result = 0;
            for (MapModel map : new ArrayList<MapModel>(observedMaps)) {
                if (map instanceof MMapModel) {
                    final Timer timer = ((MMapModel) map).getTimerForAutomaticSaving();
                    if (timer != null && timer.isRunning()) {
                        result++;
                    }
                }
            }
            return result;
        }

        private static int mapChangeListeners(final MapModel map) throws Exception {
            final Field field = MapModel.class.getDeclaredField("listeners");
            field.setAccessible(true);
            return ((List<?>) field.get(map)).size();
        }

        @Override
        public void close() throws Exception {
            if (closed) {
                return;
            }
            closed = true;
            RuntimeException failure = null;
            try {
                for (MapModel map : new ArrayList<MapModel>(openedMaps)) {
                    try {
                        closeMap(map);
                    }
                    catch (RuntimeException exception) {
                        failure = recordFailure(failure, exception);
                    }
                }
                try {
                    closeAllLoadedMaps();
                }
                catch (RuntimeException exception) {
                    failure = recordFailure(failure, exception);
                }
                if (observing) {
                    mapController.removeMapLifeCycleListener(observer);
                    observing = false;
                }
                if (graphGroups != null) {
                    graphGroups.close();
                    graphGroups = null;
                }
            }
            finally {
                try {
                    if (previousViewController != null) {
                        controller.setViewController(previousViewController);
                    }
                    if (previousMapViewManager != null) {
                        controller.setMapViewManager(previousMapViewManager);
                    }
                    if (!ownsStarter) {
                        replaceMenuStructure(modeController, previousMenuStructure);
                    }
                    if (ownsStarter) {
                        clearMapIoSingleton();
                    }
                    restoreMapVersionInterpreters(previousInterpreters);
                    if (resources != null) {
                        resources.close();
                    }
                    Controller.setCurrentController(previousController);
                }
                finally {
                    scopeViewController.shutdownExecutor();
                }
            }
            if (failure != null) {
                throw failure;
            }
        }

        private void closeAllLoadedMaps() {
            try {
                final Field loadedMaps = MMapController.class.getDeclaredField("loadedMaps");
                loadedMaps.setAccessible(true);
                final Object value = loadedMaps.get(mapController);
                if (value instanceof Map<?, ?>) {
                    for (Object map : new ArrayList<Object>(((Map<?, ?>) value).keySet())) {
                        if (map instanceof MapModel) {
                            closeMap((MapModel) map);
                        }
                    }
                }
                controller.getMapViewManager().closeWithoutSaving();
            }
            catch (Exception failure) {
                throw new IllegalStateException("Unable to close loaded Freeplane maps", failure);
            }
        }

        private static IMapViewManager selectableMapViewManager(final IMapViewManager delegate) {
            final IMapSelection selection = new IMapSelection() {
                private NodeModel selected() {
                    final MapModel map = delegate.getMap();
                    return map == null ? null : map.getRootNode();
                }

                @Override
                public void moveNodeTo(final NodeModel node, final NodePosition position) {
                }

                @Override
                public void slowlyMoveNodeTo(final NodeModel node, final NodePosition position) {
                }

                @Override
                public NodeModel getSelected() {
                    return selected();
                }

                @Override
                public NodeModel getSelectionRoot() {
                    return selected();
                }

                @Override
                public NodeModel getSearchRoot() {
                    return selected();
                }

                @Override
                public NodeModel getEffectiveSearchRoot() {
                    return selected();
                }

                @Override
                public Set<NodeModel> getSelection() {
                    final NodeModel node = selected();
                    return node == null ? Collections.<NodeModel>emptySet() : Collections.singleton(node);
                }

                @Override
                public List<String> getOrderedSelectionIds() {
                    final NodeModel node = selected();
                    return node == null || node.getID() == null ? Collections.<String>emptyList()
                        : Collections.singletonList(node.getID());
                }

                @Override
                public List<NodeModel> getOrderedSelection() {
                    final NodeModel node = selected();
                    return node == null ? Collections.<NodeModel>emptyList() : Collections.singletonList(node);
                }

                @Override
                public List<NodeModel> getSortedSelection(final boolean differentSubtrees) {
                    return getOrderedSelection();
                }

                @Override
                public boolean isSelected(final NodeModel node) {
                    return selected() == node;
                }

                @Override
                public void preserveRootNodeLocationOnScreen() {
                }

                @Override
                public void preserveSelectedNodeLocationOnScreen() {
                }

                @Override
                public void preserveNodeLocationOnScreen(final NodeModel model) {
                }

                @Override
                public void preserveNodeLocationOnScreen(final NodeModel node, final float horizontalPoint,
                        final float verticalPoint) {
                }

                @Override
                public void makeTheSelected(final NodeModel node) {
                }

                @Override
                public void makeTheSearchRoot(final NodeModel node) {
                }

                @Override
                public void scrollNodeToVisible(final NodeModel node) {
                }

                @Override
                public void scrollNodeToCenter(final NodeModel node, final boolean slow) {
                }

                @Override
                public void scrollNodeToCenter(final NodeModel node) {
                }

                @Override
                public void scrollNodeTreeToVisible(final NodeModel node, final boolean slow) {
                }

                @Override
                public void scrollNodeTreeToVisible(final NodeModel node) {
                }

                @Override
                public void selectAsTheOnlyOneSelected(final NodeModel node) {
                }

                @Override
                public void selectBranch(final NodeModel node, final boolean extend) {
                }

                @Override
                public void selectContinuous(final NodeModel node) {
                }

                @Override
                public void selectRoot() {
                }

                @Override
                public int size() {
                    return selected() == null ? 0 : 1;
                }

                @Override
                public void toggleSelected(final NodeModel node) {
                }

                @Override
                public void replaceSelection(final NodeModel[] nodes) {
                }

                @Override
                public org.freeplane.features.filter.Filter getFilter() {
                    return null;
                }

                @Override
                public void setFilter(final org.freeplane.features.filter.Filter filter) {
                }

                @Override
                public boolean isFolded(final NodeModel node) {
                    return node != null && node.isFolded();
                }

                @Override
                public boolean isVisible(final NodeModel node) {
                    return true;
                }
            };
            return (IMapViewManager) Proxy.newProxyInstance(IMapViewManager.class.getClassLoader(),
                new Class<?>[] { IMapViewManager.class }, new InvocationHandler() {
                    @Override
                    public Object invoke(final Object proxy, final Method method, final Object[] arguments)
                            throws Throwable {
                        if ("getMapSelection".equals(method.getName())) {
                            return selection;
                        }
                        try {
                            return method.invoke(delegate, arguments);
                        }
                        catch (java.lang.reflect.InvocationTargetException failure) {
                            throw failure.getCause();
                        }
                    }
                });
        }

        private static Entry replaceMenuStructure(final ModeController modeController, final Entry menuRoot)
                throws Exception {
            final Object inputFactory = modeController.getUserInputListenerFactory();
            final Field menuStructure = inputFactory.getClass().getDeclaredField("genericMenuStructure");
            menuStructure.setAccessible(true);
            final Entry previousMenuStructure = (Entry) menuStructure.get(inputFactory);
            menuStructure.set(inputFactory, menuRoot);
            return previousMenuStructure;
        }
        private static RuntimeException recordFailure(final RuntimeException prior, final RuntimeException next) {
            if (prior == null) {
                return next;
            }
            prior.addSuppressed(next);
            return prior;
        }

        private static MapVersionInterpreter[] mapVersionInterpreters() throws Exception {
            final Field values = MapVersionInterpreter.class.getDeclaredField("values");
            values.setAccessible(true);
            return (MapVersionInterpreter[]) values.get(null);
        }

        private static void restoreMapVersionInterpreters(final MapVersionInterpreter[] values) throws Exception {
            final Field field = MapVersionInterpreter.class.getDeclaredField("values");
            field.setAccessible(true);
            field.set(null, values);
        }

        private static void clearMapIoSingleton() throws Exception {
            final Field instance = MMapIO.class.getDeclaredField("INSTANCE");
            instance.setAccessible(true);
            instance.set(null, null);
        }
    }

    static final class HeadlessResourceFiles implements AutoCloseable {
        private final String previousGlobalResourceDirectory;
        private final String previousResourceBaseDirectory;
        private final String previousInstallationBaseDirectory;
        private final Path testResourceDirectory;
        private final Path properties;
        private final byte[] previousProperties;
        private final Path versionProperties;
        private final byte[] previousVersionProperties;
        private final Path mapVersions;
        private final byte[] previousMapVersions;
        private final Path preferences;
        private final byte[] previousPreferences;

        HeadlessResourceFiles() throws Exception {
            final URL fixtureUrl = GraphWorkspaceColdReloadShould.class.getResource("/maps/graph-projection.mm");
            if (fixtureUrl == null) {
                throw new IOException("Missing graph workspace fixture");
            }
            testResourceDirectory = Paths.get(fixtureUrl.toURI()).getParent().getParent();
            final Path projectDirectory = testResourceDirectory.getParent().getParent().getParent().getParent();
            final Path viewerResources = projectDirectory.resolve("freeplane/build/resources/viewer");
            final Path viewerProperties = viewerResources.resolve("freeplane.properties");
            final Path viewerVersionProperties = viewerResources.resolve("version.properties");
            final Path externalPreferences = projectDirectory.resolve("freeplane/src/external/resources/xml/preferences.xml");
            final Path editorMapVersions = projectDirectory.resolve("freeplane/src/editor/resources/xml/mapVersions.xml");
            if (!Files.isRegularFile(viewerProperties) || !Files.isRegularFile(viewerVersionProperties)
                    || !Files.isRegularFile(externalPreferences) || !Files.isRegularFile(editorMapVersions)) {
                throw new IOException("Missing Freeplane headless test resources");
            }
            properties = testResourceDirectory.resolve("freeplane.properties");
            previousProperties = copyWithBackup(viewerProperties, properties);
            versionProperties = testResourceDirectory.resolve("version.properties");
            previousVersionProperties = copyWithBackup(viewerVersionProperties, versionProperties);
            mapVersions = testResourceDirectory.resolve("xml/mapVersions.xml");
            previousMapVersions = copyWithBackup(editorMapVersions, mapVersions);
            preferences = testResourceDirectory.resolve("xml/preferences.xml");
            previousPreferences = copyWithBackup(externalPreferences, preferences);

            previousGlobalResourceDirectory = System.getProperty("org.freeplane.globalresourcedir");
            previousResourceBaseDirectory = ApplicationResourceController.RESOURCE_BASE_DIRECTORY;
            previousInstallationBaseDirectory = ApplicationResourceController.INSTALLATION_BASE_DIRECTORY;
            System.setProperty("org.freeplane.globalresourcedir", viewerResources.toString());
            ApplicationResourceController.RESOURCE_BASE_DIRECTORY = viewerResources.toString();
            ApplicationResourceController.INSTALLATION_BASE_DIRECTORY = viewerResources.getParent().toString();
            Compat.setIsApplet(false);
        }

        private static byte[] copyWithBackup(final Path source, final Path target) throws IOException {
            final byte[] previous = Files.exists(target) ? Files.readAllBytes(target) : null;
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            return previous;
        }

        @Override
        public void close() throws IOException {
            restore(properties, previousProperties);
            restore(versionProperties, previousVersionProperties);
            restore(mapVersions, previousMapVersions);
            restore(preferences, previousPreferences);
            if (previousGlobalResourceDirectory == null) {
                System.clearProperty("org.freeplane.globalresourcedir");
            }
            else {
                System.setProperty("org.freeplane.globalresourcedir", previousGlobalResourceDirectory);
            }
            ApplicationResourceController.RESOURCE_BASE_DIRECTORY = previousResourceBaseDirectory;
            ApplicationResourceController.INSTALLATION_BASE_DIRECTORY = previousInstallationBaseDirectory;
        }

        private static void restore(final Path target, final byte[] previous) throws IOException {
            if (previous == null) {
                Files.deleteIfExists(target);
            }
            else {
                Files.createDirectories(target.getParent());
                Files.write(target, previous);
            }
        }
    }
}
