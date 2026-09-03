# Separate Maps into Active and Inactive Categories Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Partition the Graph Workspace map list panel into distinct Active and Inactive categories, dynamically swapping action button labels and enablement to support deactivating, reactivating, and permanently deleting maps with cascaded relationship and pin purges.

**Architecture:** WorkspaceCommands introduces `deleteMap` to purge a map reference along with its incident relationships and pins atomically while preserving undo/redo compensation. GraphCommands and GraphCommandRouter route `reactivateMap` and `deleteMap` (enforcing deactivation prior to permanent deletion). The Swing MapListPanel is reorganized into a single scrollable container hosting two vertical JLists (Active and Inactive) with mutual exclusion and a 2x2 action button grid. GraphWorkspaceWindow wires selection updates to dynamic menu enablement and maintains partition classification across active and read-only sessions.

**Tech Stack:** Java 8 language target (JDK 21.0.8-zulu runtime), Swing, Gradle multi-project build, JUnit 4, AssertJ, Mockito.

## Global Constraints

- Use `gradle` (never `gradlew`); run module tests with `JAVA_HOME=~/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test`.
- Java runtime: `~/.sdkman/candidates/java/21.0.8-zulu`.
- Java 8 language target; 4-space indentation; UTF-8 source encoding; no new runtime dependencies.
- Do NOT modify `freeplane_api` or core classes under `freeplane/src/main/java`; all implementation changes reside in `freeplane_plugin_graph` and viewer resources (`Resources_en.properties`).
- Translation keys must match specification verbatim; file encoding for `Resources_en.properties` is ISO-8859-1 with ASCII characters.
- Commit message prefix for every commit: `2026-08-30-active-inactive-maps: <summary>`.
- Never put a plain `##` heading inside a task body; use `###` for subheadings.

## Task 1: Workspace Command deleteMap and Atomic Cascade Purge

**Implementer tier:** Standard

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/WorkspaceCommands.java:30-40,65-75,150-180`
- Modify: `freeplane/src/viewer/resources/translations/Resources_en.properties:860-880`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/WorkspaceCommandsShould.java:50-100`

**Interfaces:**

- Consumes: `WorkspaceDocument`, `MapReferenceId`, `MapReference`, `GraphRelationshipRecord`, `PinRecord`, `WorkspaceTransition`, `WorkspaceCommand`.
- Produces: `WorkspaceCommands.deleteMap(final MapReferenceId id): WorkspaceCommand` — purges target map, purges all incident relationships (source or target match), purges all associated pins atomically, and emits `graph_workspace.map.deleted`.

- [ ] **Step 1: Write the failing unit tests in WorkspaceCommandsShould**

Add tests to `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/WorkspaceCommandsShould.java` verifying `deleteMap` and `reactivateMap`:

```java
    @Test
    public void deleteMapPurgesMapAndCascadesRelationshipsAndPinsBothSourceAndTarget() {
        MapReferenceId deletedId = MAP_TWO;
        WorkspaceDocument before = document(
            Arrays.asList(
                map(MAP_ONE, 1, "maps/one.mm", true, "#4E79A7", noUnknownXml()),
                map(MAP_TWO, 2, "maps/two.mm", false, "#59A14F", noUnknownXml()),
                map(MAP_THREE, 3, "maps/three.mm", true, "#EDC948", noUnknownXml())),
            Arrays.asList(
                relationship(RELATIONSHIP_ONE, 1, MAP_ONE, "n1", MAP_TWO, "n2", RelationshipDirection.FORWARD),
                relationship(RELATIONSHIP_TWO, 2, MAP_TWO, "n2", MAP_THREE, "n3", RelationshipDirection.FORWARD),
                relationship(RELATIONSHIP_THREE, 3, MAP_ONE, "n1", MAP_THREE, "n3", RelationshipDirection.FORWARD)),
            Arrays.asList(
                pin(MAP_ONE, "n1", 10.0, 20.0),
                pin(MAP_TWO, "n2", 30.0, 40.0),
                pin(MAP_THREE, "n3", 50.0, 60.0)));

        WorkspaceTransition transition = WorkspaceCommands.deleteMap(deletedId).apply(before);

        assertThat(transition.status()).isEqualTo(WorkspaceTransition.Status.APPLIED);
        assertThat(transition.messageKey()).isEqualTo("graph_workspace.map.deleted");
        assertThat(transition.messageArguments()).containsExactly(deletedId);

        WorkspaceDocument after = transition.after();
        assertThat(after.maps()).extracting(MapReference::id).containsExactly(MAP_ONE, MAP_THREE);
        assertThat(after.relationships()).extracting(GraphRelationshipRecord::id).containsExactly(RELATIONSHIP_THREE);
        assertThat(after.pins()).extracting(pin -> pin.node().mapReferenceId()).containsExactly(MAP_ONE, MAP_THREE);
    }

    @Test
    public void deleteMapRejectsUnknownMapId() {
        WorkspaceDocument before = document(
            Collections.singletonList(map(MAP_ONE, 1, "maps/one.mm", true, "#4E79A7", noUnknownXml())),
            noRelationships(),
            noPins());

        WorkspaceTransition transition = WorkspaceCommands.deleteMap(MAP_EIGHT).apply(before);

        assertThat(transition.status()).isEqualTo(WorkspaceTransition.Status.REJECTED);
        assertThat(transition.messageKey()).isEqualTo("graph_workspace.map.not_found");
        assertThat(transition.messageArguments()).containsExactly(MAP_EIGHT);
        assertThat(transition.after()).isSameAs(before);
    }

    @Test
    public void deleteMapIsUndoableAndRedoableWithAllCascadedEntities() {
        WorkspaceDocument initial = document(
            Arrays.asList(
                map(MAP_ONE, 1, "maps/one.mm", true, "#4E79A7", noUnknownXml()),
                map(MAP_TWO, 2, "maps/two.mm", false, "#59A14F", noUnknownXml())),
            Collections.singletonList(
                relationship(RELATIONSHIP_ONE, 1, MAP_ONE, "n1", MAP_TWO, "n2", RelationshipDirection.FORWARD)),
            Collections.singletonList(
                pin(MAP_TWO, "n2", 30.0, 40.0)));

        WorkspaceHistory history = new WorkspaceHistory(10);
        WorkspaceTransition deleteTransition = WorkspaceCommands.deleteMap(MAP_TWO).apply(initial);
        history.record(deleteTransition, initial);

        assertThat(deleteTransition.after().maps()).extracting(MapReference::id).containsExactly(MAP_ONE);
        assertThat(deleteTransition.after().relationships()).isEmpty();
        assertThat(deleteTransition.after().pins()).isEmpty();

        WorkspaceTransition undoTransition = history.undo(deleteTransition.after());
        assertThat(undoTransition.status()).isEqualTo(WorkspaceTransition.Status.APPLIED);
        WorkspaceDocument restored = undoTransition.after();
        assertThat(restored.maps()).extracting(MapReference::id).containsExactly(MAP_ONE, MAP_TWO);
        assertThat(restored.relationships()).extracting(GraphRelationshipRecord::id).containsExactly(RELATIONSHIP_ONE);
        assertThat(restored.pins()).extracting(pin -> pin.node().mapReferenceId()).containsExactly(MAP_TWO);

        WorkspaceTransition redoTransition = history.redo(restored);
        assertThat(redoTransition.status()).isEqualTo(WorkspaceTransition.Status.APPLIED);
        assertThat(redoTransition.after().maps()).extracting(MapReference::id).containsExactly(MAP_ONE);
        assertThat(redoTransition.after().relationships()).isEmpty();
        assertThat(redoTransition.after().pins()).isEmpty();
    }

    @Test
    public void reactivateMapRestoresActiveStatusAndPreservesRelationships() {
        WorkspaceDocument before = document(
            Collections.singletonList(map(MAP_TWO, 2, "maps/two.mm", false, "#59A14F", noUnknownXml())),
            Collections.singletonList(
                relationship(RELATIONSHIP_ONE, 1, MAP_ONE, "n1", MAP_TWO, "n2", RelationshipDirection.FORWARD)),
            noPins());

        WorkspaceTransition transition = WorkspaceCommands.reactivateMap(MAP_TWO).apply(before);

        assertThat(transition.status()).isEqualTo(WorkspaceTransition.Status.APPLIED);
        assertThat(transition.messageKey()).isEqualTo("graph_workspace.map.reactivated");
        assertThat(transition.after().maps().get(0).active()).isTrue();
        assertThat(transition.after().relationships()).hasSize(1);
    }
```

- [ ] **Step 2: Run tests to confirm failure**

Run:
```bash
JAVA_HOME=~/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.workspace.WorkspaceCommandsShould"
```
Expected: Compilation failure due to missing `WorkspaceCommands.deleteMap`.

- [ ] **Step 3: Implement WorkspaceCommands.deleteMap and add translation key**

In `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/WorkspaceCommands.java`:
1. Add constant:
```java
    private static final String MAP_DELETED = "graph_workspace.map.deleted";
```
2. Add public static factory:
```java
    public static WorkspaceCommand deleteMap(final MapReferenceId id) {
        return new DeleteMapCommand(Objects.requireNonNull(id, "id"));
    }
```
3. Add `DeleteMapCommand` inner class:
```java
    private static final class DeleteMapCommand implements WorkspaceCommand {
        private final MapReferenceId id;

        private DeleteMapCommand(final MapReferenceId id) {
            this.id = id;
        }

        @Override
        public WorkspaceTransition apply(final WorkspaceDocument before) {
            Objects.requireNonNull(before, "before");
            final MapReference existing = mapById(before, id);
            if (existing == null) {
                return WorkspaceTransition.rejected(before, MAP_NOT_FOUND, id);
            }
            final List<MapReference> maps = new ArrayList<MapReference>();
            for (final MapReference map : before.maps()) {
                if (!map.id().equals(id)) {
                    maps.add(map);
                }
            }
            final List<GraphRelationshipRecord> relationships = new ArrayList<GraphRelationshipRecord>();
            for (final GraphRelationshipRecord relationship : before.relationships()) {
                final boolean touchesSource = relationship.source().mapReferenceId().equals(id);
                final boolean touchesTarget = relationship.target().mapReferenceId().equals(id);
                if (!touchesSource && !touchesTarget) {
                    relationships.add(relationship);
                }
            }
            final List<PinRecord> pins = new ArrayList<PinRecord>();
            for (final PinRecord pin : before.pins()) {
                if (!pin.node().mapReferenceId().equals(id)) {
                    pins.add(pin);
                }
            }
            return WorkspaceTransition.applied(
                before.toBuilder()
                    .maps(maps)
                    .relationships(relationships)
                    .pins(pins)
                    .build(),
                MAP_DELETED,
                id);
        }
    }
```

In `freeplane/src/viewer/resources/translations/Resources_en.properties`, add:
```properties
graph_workspace.map.deleted=Map deleted: {0}
```

- [ ] **Step 4: Run tests to confirm pass**

Run:
```bash
JAVA_HOME=~/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.workspace.WorkspaceCommandsShould"
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/WorkspaceCommands.java \
        freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/workspace/WorkspaceCommandsShould.java \
        freeplane/src/viewer/resources/translations/Resources_en.properties
git commit -m "2026-08-30-active-inactive-maps: Implement WorkspaceCommands.deleteMap with cascaded entity purge"
```

## Task 2: GraphCommands and GraphCommandRouter for ReactivateMap and DeleteMap

**Implementer tier:** Standard

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/command/GraphCommands.java:40-60,180-220`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/command/GraphCommandRouter.java:25-35,60-75,170-200`
- Modify: `freeplane/src/viewer/resources/translations/Resources_en.properties:860-880`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/command/GraphCommandRouterShould.java:180-220`

**Interfaces:**

- Consumes: `WorkspaceCommands.reactivateMap(id)`, `WorkspaceCommands.deleteMap(id)`, `GraphWorkspaceStore`, `MapReferenceId`.
- Produces: `GraphCommands.reactivateMap(MapReferenceId)`, `GraphCommands.deleteMap(MapReferenceId)`, router execution handling deactivation guard before permanent deletion.

- [ ] **Step 1: Write failing tests in GraphCommandRouterShould**

Add tests to `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/command/GraphCommandRouterShould.java`:

```java
    @Test
    public void routesReactivateMapToWorkspaceCommand() {
        MapReferenceId mapId = MapReferenceId.of("00000000-0000-0000-0000-000000000002");
        WorkspaceDocument doc = document(
            Arrays.asList(
                map(MAP_ONE, 1, "maps/one.mm", true, "#4E79A7", noUnknownXml()),
                map(mapId, 2, "maps/two.mm", false, "#59A14F", noUnknownXml())),
            noRelationships(), noPins());
        GraphWorkspaceStore store = store(doc);
        GraphCommandRouter router = router(store);

        GraphCommandResult result = router.execute(GraphCommands.reactivateMap(mapId));

        assertThat(result.status()).isEqualTo(GraphCommandResult.Status.APPLIED);
        assertThat(store.currentDocument().maps().get(1).active()).isTrue();
    }

    @Test
    public void routesDeleteMapForInactiveMap() {
        MapReferenceId mapId = MapReferenceId.of("00000000-0000-0000-0000-000000000002");
        WorkspaceDocument doc = document(
            Arrays.asList(
                map(MAP_ONE, 1, "maps/one.mm", true, "#4E79A7", noUnknownXml()),
                map(mapId, 2, "maps/two.mm", false, "#59A14F", noUnknownXml())),
            noRelationships(), noPins());
        GraphWorkspaceStore store = store(doc);
        GraphCommandRouter router = router(store);

        GraphCommandResult result = router.execute(GraphCommands.deleteMap(mapId));

        assertThat(result.status()).isEqualTo(GraphCommandResult.Status.APPLIED);
        assertThat(store.currentDocument().maps()).extracting(MapReference::id).containsExactly(MAP_ONE);
    }

    @Test
    public void rejectsDeleteMapForActiveMap() {
        MapReferenceId mapId = MapReferenceId.of("00000000-0000-0000-0000-000000000002");
        WorkspaceDocument doc = document(
            Arrays.asList(
                map(MAP_ONE, 1, "maps/one.mm", true, "#4E79A7", noUnknownXml()),
                map(mapId, 2, "maps/two.mm", true, "#59A14F", noUnknownXml())),
            noRelationships(), noPins());
        GraphWorkspaceStore store = store(doc);
        GraphCommandRouter router = router(store);

        GraphCommandResult result = router.execute(GraphCommands.deleteMap(mapId));

        assertThat(result.status()).isEqualTo(GraphCommandResult.Status.REJECTED);
        assertThat(result.messageKey()).isEqualTo("graph_workspace.map.delete_active");
        assertThat(result.messageArguments()).containsExactly(mapId);
        assertThat(store.currentDocument().maps()).hasSize(2);
    }
```

- [ ] **Step 2: Run tests to confirm failure**

Run:
```bash
JAVA_HOME=~/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.command.GraphCommandRouterShould"
```
Expected: Compilation failure due to missing `GraphCommands.reactivateMap` and `GraphCommands.deleteMap`.

- [ ] **Step 3: Implement GraphCommands and GraphCommandRouter routing**

In `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/command/GraphCommands.java`:
1. Add factory methods:
```java
    public static ReactivateMap reactivateMap(final MapReferenceId mapReferenceId) {
        return new ReactivateMap(mapReferenceId);
    }

    public static DeleteMap deleteMap(final MapReferenceId mapReferenceId) {
        return new DeleteMap(mapReferenceId);
    }
```
2. Add command classes:
```java
    public static final class ReactivateMap implements GraphCommand {
        private final MapReferenceId mapReferenceId;

        private ReactivateMap(final MapReferenceId mapReferenceId) {
            this.mapReferenceId = Objects.requireNonNull(mapReferenceId, "mapReferenceId");
        }

        public MapReferenceId mapReferenceId() {
            return mapReferenceId;
        }
    }

    public static final class DeleteMap implements GraphCommand {
        private final MapReferenceId mapReferenceId;

        private DeleteMap(final MapReferenceId mapReferenceId) {
            this.mapReferenceId = Objects.requireNonNull(mapReferenceId, "mapReferenceId");
        }

        public MapReferenceId mapReferenceId() {
            return mapReferenceId;
        }
    }
```

In `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/command/GraphCommandRouter.java`:
1. Add constant:
```java
    private static final String MAP_DELETE_ACTIVE = "graph_workspace.map.delete_active";
```
2. In `execute(final GraphCommand command)`, add branches:
```java
        if (value instanceof GraphCommands.ReactivateMap) {
            return executeReactivateMap((GraphCommands.ReactivateMap) value);
        }
        if (value instanceof GraphCommands.DeleteMap) {
            return executeDeleteMap((GraphCommands.DeleteMap) value);
        }
```
3. Add helper methods:
```java
    private GraphCommandResult executeReactivateMap(final GraphCommands.ReactivateMap command) {
        return executeWorkspace(WorkspaceCommands.reactivateMap(command.mapReferenceId()));
    }

    private GraphCommandResult executeDeleteMap(final GraphCommands.DeleteMap command) {
        final MapReference map = store.currentDocument().map(command.mapReferenceId()).orElse(null);
        if (map != null && map.active()) {
            return GraphCommandResult.rejected(store.currentDocument(), MAP_DELETE_ACTIVE, command.mapReferenceId());
        }
        return executeWorkspace(WorkspaceCommands.deleteMap(command.mapReferenceId()));
    }
```

In `freeplane/src/viewer/resources/translations/Resources_en.properties`, add:
```properties
graph_workspace.map.delete_active=Active map cannot be deleted directly: {0}
```

- [ ] **Step 4: Run tests to confirm pass**

Run:
```bash
JAVA_HOME=~/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.command.GraphCommandRouterShould"
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/command/GraphCommands.java \
        freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/command/GraphCommandRouter.java \
        freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/command/GraphCommandRouterShould.java \
        freeplane/src/viewer/resources/translations/Resources_en.properties
git commit -m "2026-08-30-active-inactive-maps: Add ReactivateMap and DeleteMap to GraphCommands and router"
```

## Task 3: MapPartition Model, MapRow Partition Field, and Action Localization Keys

**Implementer tier:** Fast

**Files:**

- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/MapPartition.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/MapListPanel.java:45-95`
- Modify: `freeplane/src/viewer/resources/translations/Resources_en.properties:780-820`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/MapRowShould.java`

**Interfaces:**

- Consumes: `MapReferenceId`, `RowState`.
- Produces: `MapPartition` (`ACTIVE`, `INACTIVE`), `MapRow.partition(): MapPartition`, overloaded factory methods `MapRow.of(...)`, and complete localization property keys.

- [ ] **Step 1: Write failing tests for MapRow in MapRowShould**

Create `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/MapRowShould.java`:

```java
package org.freeplane.plugin.graph.window;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.junit.Test;

public class MapRowShould {
    private static final MapReferenceId MAP_ID = MapReferenceId.of("00000000-0000-0000-0000-000000000001");

    @Test
    public void constructWithExplicitPartition() {
        MapListPanel.MapRow row = MapListPanel.MapRow.of(
            MAP_ID, "My Map", MapListPanel.RowState.ACTIVE, MapPartition.ACTIVE, 5, true);

        assertThat(row.mapReferenceId()).isEqualTo(MAP_ID);
        assertThat(row.displayName()).isEqualTo("My Map");
        assertThat(row.state()).isEqualTo(MapListPanel.RowState.ACTIVE);
        assertThat(row.partition()).isEqualTo(MapPartition.ACTIVE);
        assertThat(row.projectedNodeCount()).isEqualTo(5);
        assertThat(row.selected()).isTrue();
    }

    @Test
    public void constructUsingBackwardCompatibleOverloadDerivingPartitionFromState() {
        MapListPanel.MapRow activeRow = MapListPanel.MapRow.of(
            MAP_ID, "Active Map", MapListPanel.RowState.ACTIVE, 3, false);
        MapListPanel.MapRow inactiveRow = MapListPanel.MapRow.of(
            MAP_ID, "Inactive Map", MapListPanel.RowState.INACTIVE, 0, false);
        MapListPanel.MapRow missingRow = MapListPanel.MapRow.of(
            MAP_ID, "Missing Map", MapListPanel.RowState.MISSING, 0, false);

        assertThat(activeRow.partition()).isEqualTo(MapPartition.ACTIVE);
        assertThat(inactiveRow.partition()).isEqualTo(MapPartition.INACTIVE);
        assertThat(missingRow.partition()).isEqualTo(MapPartition.ACTIVE);
    }

    @Test
    public void preservePartitionWhenUpdatingSelected() {
        MapListPanel.MapRow row = MapListPanel.MapRow.of(
            MAP_ID, "My Map", MapListPanel.RowState.READ_ONLY, MapPartition.INACTIVE, 0, false);
        MapListPanel.MapRow updated = row.withSelected(true);

        assertThat(updated.selected()).isTrue();
        assertThat(updated.partition()).isEqualTo(MapPartition.INACTIVE);
        assertThat(updated.state()).isEqualTo(MapListPanel.RowState.READ_ONLY);
    }
}
```

- [ ] **Step 2: Run tests to confirm failure**

Run:
```bash
JAVA_HOME=~/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.window.MapRowShould"
```
Expected: Compilation failure due to missing `MapPartition` and `partition()` method.

- [ ] **Step 3: Implement MapPartition, update MapRow, and add translation keys**

1. Create `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/MapPartition.java`:
```java
package org.freeplane.plugin.graph.window;

public enum MapPartition {
    ACTIVE,
    INACTIVE
}
```

2. Modify `MapRow` in `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/MapListPanel.java`:
```java
    static final class MapRow {
        private final MapReferenceId mapReferenceId;
        private final String displayName;
        private final RowState state;
        private final MapPartition partition;
        private final int projectedNodeCount;
        private final boolean selected;

        private MapRow(final MapReferenceId mapReferenceId, final String displayName, final RowState state,
                final MapPartition partition, final int projectedNodeCount, final boolean selected) {
            this.mapReferenceId = Objects.requireNonNull(mapReferenceId, "mapReferenceId");
            this.displayName = requireText(displayName, "displayName");
            this.state = Objects.requireNonNull(state, "state");
            this.partition = Objects.requireNonNull(partition, "partition");
            if (projectedNodeCount < 0) {
                throw new IllegalArgumentException("Projected node count must be nonnegative");
            }
            this.projectedNodeCount = projectedNodeCount;
            this.selected = selected;
        }

        static MapRow of(final MapReferenceId mapReferenceId, final String displayName, final RowState state,
                final MapPartition partition, final int projectedNodeCount, final boolean selected) {
            return new MapRow(mapReferenceId, displayName, state, partition, projectedNodeCount, selected);
        }

        static MapRow of(final MapReferenceId mapReferenceId, final String displayName, final RowState state,
                final int projectedNodeCount, final boolean selected) {
            return of(mapReferenceId, displayName, state,
                state == RowState.INACTIVE ? MapPartition.INACTIVE : MapPartition.ACTIVE,
                projectedNodeCount, selected);
        }

        MapReferenceId mapReferenceId() {
            return mapReferenceId;
        }

        String displayName() {
            return displayName;
        }

        RowState state() {
            return state;
        }

        MapPartition partition() {
            return partition;
        }

        int projectedNodeCount() {
            return projectedNodeCount;
        }

        boolean selected() {
            return selected;
        }

        MapRow withSelected(final boolean value) {
            return new MapRow(mapReferenceId, displayName, state, partition, projectedNodeCount, value);
        }

        private static String requireText(final String value, final String name) {
            Objects.requireNonNull(value, name);
            if (value.trim().isEmpty()) {
                throw new IllegalArgumentException(name + " must not be empty");
            }
            return value;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
```

3. Add i18n keys to `freeplane/src/viewer/resources/translations/Resources_en.properties`:
```properties
graph_workspace.action.deactivate_map=Deactivate Map
graph_workspace.action.reactivate_map=Reactivate Map
graph_workspace.action.delete_map=Delete Map
graph_workspace.map_list.active_heading=ACTIVE ({0})
graph_workspace.map_list.inactive_heading=INACTIVE ({0})
graph_workspace.dialog.delete_map.title=Delete Map
graph_workspace.dialog.delete_map.message=Are you sure you want to permanently delete "{0}" and all associated relationships and pins?
```

- [ ] **Step 4: Run tests to confirm pass**

Run:
```bash
JAVA_HOME=~/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.window.MapRowShould"
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/MapPartition.java \
        freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/MapListPanel.java \
        freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/MapRowShould.java \
        freeplane/src/viewer/resources/translations/Resources_en.properties
git commit -m "2026-08-30-active-inactive-maps: Introduce MapPartition and update MapRow"
```

## Task 4: MapListPanel Component Refactoring with Partitioned Lists, 2x2 Grid, and Testable Delete Confirmation

**Implementer tier:** Advanced

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/MapListPanel.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/MapListPanelShould.java`

**Interfaces:**

- Consumes: `GraphWorkspaceHandle`, `GraphCommands`, `MapRow`, `MapPartition`, `RowState`.
- Produces: `MapListPanel` with two partitioned sections (`activeList`, `inactiveList`), mutual selection exclusion, pending selection preservation on state changes, 2x2 button grid, `DeleteConfirmationPrompt` injection, trigger actions (`deactivateSelected`, `reactivateSelected`, `deleteSelected`), and `addSelectionListener`.

- [ ] **Step 1: Write failing tests in MapListPanelShould**

Create `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/MapListPanelShould.java`:

```java
package org.freeplane.plugin.graph.window;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import org.freeplane.plugin.graph.command.GraphCommand;
import org.freeplane.plugin.graph.command.GraphCommands;
import org.freeplane.plugin.graph.control.GraphWorkspaceHandle;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class MapListPanelShould {
    private static final MapReferenceId ACTIVE_MAP = MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    private static final MapReferenceId INACTIVE_MAP = MapReferenceId.of("00000000-0000-0000-0000-000000000002");
    private static final MapReferenceId MISSING_MAP = MapReferenceId.of("00000000-0000-0000-0000-000000000003");

    @Test
    public void partitionActiveAndInactiveRowsIntoSeparateLists() {
        GraphWorkspaceHandle handle = mock(GraphWorkspaceHandle.class);
        MapListPanel panel = new MapListPanel(handle, () -> Paths.get("/tmp/map.mm"), (parent, name) -> true);

        panel.setRows(Arrays.asList(
            MapListPanel.MapRow.of(ACTIVE_MAP, "Active Map", MapListPanel.RowState.ACTIVE, MapPartition.ACTIVE, 5, false),
            MapListPanel.MapRow.of(INACTIVE_MAP, "Inactive Map", MapListPanel.RowState.INACTIVE, MapPartition.INACTIVE, 0, false)));

        assertThat(panel.activeList().getModel().getSize()).isEqualTo(1);
        assertThat(panel.activeList().getModel().getElementAt(0).mapReferenceId()).isEqualTo(ACTIVE_MAP);
        assertThat(panel.inactiveList().getModel().getSize()).isEqualTo(1);
        assertThat(panel.inactiveList().getModel().getElementAt(0).mapReferenceId()).isEqualTo(INACTIVE_MAP);
    }

    @Test
    public void maintainMutuallyExclusiveSelectionBetweenLists() {
        GraphWorkspaceHandle handle = mock(GraphWorkspaceHandle.class);
        MapListPanel panel = new MapListPanel(handle, () -> Paths.get("/tmp/map.mm"), (parent, name) -> true);

        panel.setRows(Arrays.asList(
            MapListPanel.MapRow.of(ACTIVE_MAP, "Active Map", MapListPanel.RowState.ACTIVE, MapPartition.ACTIVE, 5, false),
            MapListPanel.MapRow.of(INACTIVE_MAP, "Inactive Map", MapListPanel.RowState.INACTIVE, MapPartition.INACTIVE, 0, false)));

        panel.activeList().setSelectedIndex(0);
        assertThat(panel.selectedRow().mapReferenceId()).isEqualTo(ACTIVE_MAP);
        assertThat(panel.inactiveList().getSelectedIndex()).isEqualTo(-1);

        panel.inactiveList().setSelectedIndex(0);
        assertThat(panel.selectedRow().mapReferenceId()).isEqualTo(INACTIVE_MAP);
        assertThat(panel.activeList().getSelectedIndex()).isEqualTo(-1);
    }

    @Test
    public void swapButtonLabelsAndEnablementBasedOnSelectionPartition() {
        GraphWorkspaceHandle handle = mock(GraphWorkspaceHandle.class);
        MapListPanel panel = new MapListPanel(handle, () -> Paths.get("/tmp/map.mm"), (parent, name) -> true);

        panel.setRows(Arrays.asList(
            MapListPanel.MapRow.of(ACTIVE_MAP, "Active Map", MapListPanel.RowState.ACTIVE, MapPartition.ACTIVE, 5, false),
            MapListPanel.MapRow.of(INACTIVE_MAP, "Inactive Map", MapListPanel.RowState.INACTIVE, MapPartition.INACTIVE, 0, false)));

        // No selection
        assertThat(panel.addButton().getText()).isEqualTo("Add Map");
        assertThat(panel.addButton().isEnabled()).isTrue();
        assertThat(panel.removeButton().getText()).isEqualTo("Deactivate Map");
        assertThat(panel.removeButton().isEnabled()).isFalse();

        // Active selection
        panel.selectMap(ACTIVE_MAP);
        assertThat(panel.addButton().getText()).isEqualTo("Add Map");
        assertThat(panel.addButton().isEnabled()).isTrue();
        assertThat(panel.removeButton().getText()).isEqualTo("Deactivate Map");
        assertThat(panel.removeButton().isEnabled()).isTrue();

        // Inactive selection
        panel.selectMap(INACTIVE_MAP);
        assertThat(panel.addButton().getText()).isEqualTo("Reactivate Map");
        assertThat(panel.addButton().isEnabled()).isTrue();
        assertThat(panel.removeButton().getText()).isEqualTo("Delete Map");
        assertThat(panel.removeButton().isEnabled()).isTrue();
    }

    @Test
    public void dispatchDeactivateAndPreserveSelectionInInactiveListOnNextUpdate() {
        GraphWorkspaceHandle handle = mock(GraphWorkspaceHandle.class);
        MapListPanel panel = new MapListPanel(handle, () -> Paths.get("/tmp/map.mm"), (parent, name) -> true);

        panel.setRows(Arrays.asList(
            MapListPanel.MapRow.of(ACTIVE_MAP, "Active Map", MapListPanel.RowState.ACTIVE, MapPartition.ACTIVE, 5, false)));
        panel.selectMap(ACTIVE_MAP);

        panel.removeButton().doClick();

        ArgumentCaptor<GraphCommand> commandCaptor = ArgumentCaptor.forClass(GraphCommand.class);
        verify(handle).execute(commandCaptor.capture());
        assertThat(commandCaptor.getValue()).isInstanceOf(GraphCommands.RemoveMap.class);

        // State updates with map now inactive
        panel.setRows(Arrays.asList(
            MapListPanel.MapRow.of(ACTIVE_MAP, "Active Map", MapListPanel.RowState.INACTIVE, MapPartition.INACTIVE, 0, false)));

        assertThat(panel.selectedRow()).isNotNull();
        assertThat(panel.selectedRow().mapReferenceId()).isEqualTo(ACTIVE_MAP);
        assertThat(panel.selectedRow().partition()).isEqualTo(MapPartition.INACTIVE);
        assertThat(panel.addButton().getText()).isEqualTo("Reactivate Map");
    }

    @Test
    public void dispatchReactivateAndPreserveSelectionInActiveListOnNextUpdate() {
        GraphWorkspaceHandle handle = mock(GraphWorkspaceHandle.class);
        MapListPanel panel = new MapListPanel(handle, () -> Paths.get("/tmp/map.mm"), (parent, name) -> true);

        panel.setRows(Arrays.asList(
            MapListPanel.MapRow.of(INACTIVE_MAP, "Inactive Map", MapListPanel.RowState.INACTIVE, MapPartition.INACTIVE, 0, false)));
        panel.selectMap(INACTIVE_MAP);

        panel.addButton().doClick(); // Displays "Reactivate Map"

        ArgumentCaptor<GraphCommand> commandCaptor = ArgumentCaptor.forClass(GraphCommand.class);
        verify(handle).execute(commandCaptor.capture());
        assertThat(commandCaptor.getValue()).isInstanceOf(GraphCommands.ReactivateMap.class);

        // State updates with map now active
        panel.setRows(Arrays.asList(
            MapListPanel.MapRow.of(INACTIVE_MAP, "Inactive Map", MapListPanel.RowState.ACTIVE, MapPartition.ACTIVE, 4, false)));

        assertThat(panel.selectedRow()).isNotNull();
        assertThat(panel.selectedRow().mapReferenceId()).isEqualTo(INACTIVE_MAP);
        assertThat(panel.selectedRow().partition()).isEqualTo(MapPartition.ACTIVE);
        assertThat(panel.removeButton().getText()).isEqualTo("Deactivate Map");
    }

    @Test
    public void promptConfirmationBeforeDeleteAndClearSelectionOnDelete() {
        GraphWorkspaceHandle handle = mock(GraphWorkspaceHandle.class);
        AtomicBoolean promptCalled = new AtomicBoolean(false);
        MapListPanel panel = new MapListPanel(handle, () -> Paths.get("/tmp/map.mm"), (parent, name) -> {
            promptCalled.set(true);
            return false; // User cancels
        });

        panel.setRows(Arrays.asList(
            MapListPanel.MapRow.of(INACTIVE_MAP, "Inactive Map", MapListPanel.RowState.INACTIVE, MapPartition.INACTIVE, 0, false)));
        panel.selectMap(INACTIVE_MAP);

        panel.removeButton().doClick(); // Displays "Delete Map"
        assertThat(promptCalled.get()).isTrue();
        verify(handle, never()).execute(org.mockito.ArgumentMatchers.any());

        // Now test when confirmed
        MapListPanel panelConfirmed = new MapListPanel(handle, () -> Paths.get("/tmp/map.mm"), (parent, name) -> true);
        panelConfirmed.setRows(Arrays.asList(
            MapListPanel.MapRow.of(INACTIVE_MAP, "Inactive Map", MapListPanel.RowState.INACTIVE, MapPartition.INACTIVE, 0, false)));
        panelConfirmed.selectMap(INACTIVE_MAP);

        panelConfirmed.removeButton().doClick();
        ArgumentCaptor<GraphCommand> commandCaptor = ArgumentCaptor.forClass(GraphCommand.class);
        verify(handle).execute(commandCaptor.capture());
        assertThat(commandCaptor.getValue()).isInstanceOf(GraphCommands.DeleteMap.class);

        // When updated after deletion, selection is cleared
        panelConfirmed.setRows(Arrays.asList());
        assertThat(panelConfirmed.selectedRow()).isNull();
    }
}
```

- [ ] **Step 2: Run tests to confirm failure**

Run:
```bash
JAVA_HOME=~/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.window.MapListPanelShould"
```
Expected: Compilation failure due to missing constructors, methods, and component structure.

- [ ] **Step 3: Implement MapListPanel refactoring**

Refactor `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/MapListPanel.java` to:
1. Define `DeleteConfirmationPrompt`:
```java
    @FunctionalInterface
    interface DeleteConfirmationPrompt {
        boolean confirmDelete(Component parent, String mapDisplayName);
    }
```
2. Build `ScrollableListContainer`:
```java
    private static final class ScrollableListContainer extends JPanel implements javax.swing.Scrollable {
        private static final long serialVersionUID = 1L;

        private ScrollableListContainer() {
            setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS));
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(final java.awt.Rectangle visibleRect, final int orientation, final int direction) {
            return ROW_HEIGHT / 2;
        }

        @Override
        public int getScrollableBlockIncrement(final java.awt.Rectangle visibleRect, final int orientation, final int direction) {
            return ROW_HEIGHT * 2;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
```
3. Use two lists and models:
```java
    private final DefaultListModel<MapRow> activeModel = new DefaultListModel<MapRow>();
    private final JList<MapRow> activeList = new JList<MapRow>(activeModel);
    private final DefaultListModel<MapRow> inactiveModel = new DefaultListModel<MapRow>();
    private final JList<MapRow> inactiveList = new JList<MapRow>(inactiveModel);
    private final JLabel activeHeader = new JLabel();
    private final JLabel inactiveHeader = new JLabel();
    private final JButton actionButton1 = button("graph_workspace.action.add_map", "add-map");
    private final JButton actionButton2 = button("graph_workspace.action.deactivate_map", "remove-map");
    private final JButton retryButton = button("graph_workspace.action.retry_map", "retry-map");
    private final JButton locateButton = button("graph_workspace.action.locate_map", "locate-map");
    private final DeleteConfirmationPrompt deletePrompt;
    private final List<java.util.function.Consumer<MapRow>> selectionListeners =
        new ArrayList<java.util.function.Consumer<MapRow>>();
    private MapReferenceId pendingSelectionId;
```
4. Configure constructors:
```java
    MapListPanel(final GraphWorkspaceHandle handle, final Supplier<Path> pathChooser) {
        this(handle, pathChooser, defaultDeleteConfirmationPrompt());
    }

    MapListPanel(final GraphWorkspaceHandle handle, final Supplier<Path> pathChooser,
            final DeleteConfirmationPrompt deletePrompt) {
        this.handle = Objects.requireNonNull(handle, "handle");
        this.pathChooser = Objects.requireNonNull(pathChooser, "pathChooser");
        this.deletePrompt = Objects.requireNonNull(deletePrompt, "deletePrompt");
        setName("graph-workspace-map-list");
        setLayout(new BorderLayout(0, 4));
        setBorder(new EmptyBorder(6, 6, 6, 6));
        setPreferredSize(new Dimension(PANEL_WIDTH, 0));
        setMinimumSize(new Dimension(PANEL_WIDTH, 0));

        final JLabel heading = new JLabel(TextUtils.getText("graph_workspace.map_list.heading"));
        heading.setName("graph-workspace-map-list-heading");
        heading.setBorder(new EmptyBorder(0, 2, 2, 2));
        add(heading, BorderLayout.NORTH);

        activeHeader.setName("graph-workspace-active-header");
        activeHeader.setBorder(new EmptyBorder(4, 2, 2, 2));
        activeHeader.setFont(activeHeader.getFont().deriveFont(Font.BOLD, 10f));
        activeHeader.setForeground(Color.GRAY);

        activeList.setName("graph-workspace-active-map-list");
        activeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        activeList.setFixedCellHeight(ROW_HEIGHT);
        activeList.setVisibleRowCount(0);
        activeList.setCellRenderer(new RowRenderer());
        activeList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !updatingSelection) {
                updatingSelection = true;
                try {
                    inactiveList.clearSelection();
                    synchronizeSelection();
                }
                finally {
                    updatingSelection = false;
                }
            }
        });

        final JPanel activeSection = new JPanel(new BorderLayout());
        activeSection.add(activeHeader, BorderLayout.NORTH);
        activeSection.add(activeList, BorderLayout.CENTER);

        inactiveHeader.setName("graph-workspace-inactive-header");
        inactiveHeader.setBorder(new EmptyBorder(8, 2, 2, 2));
        inactiveHeader.setFont(inactiveHeader.getFont().deriveFont(Font.BOLD, 10f));
        inactiveHeader.setForeground(Color.GRAY);

        inactiveList.setName("graph-workspace-inactive-map-list");
        inactiveList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        inactiveList.setFixedCellHeight(ROW_HEIGHT);
        inactiveList.setVisibleRowCount(0);
        inactiveList.setCellRenderer(new RowRenderer());
        inactiveList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !updatingSelection) {
                updatingSelection = true;
                try {
                    activeList.clearSelection();
                    synchronizeSelection();
                }
                finally {
                    updatingSelection = false;
                }
            }
        });

        final JPanel inactiveSection = new JPanel(new BorderLayout());
        inactiveSection.add(inactiveHeader, BorderLayout.NORTH);
        inactiveSection.add(inactiveList, BorderLayout.CENTER);

        final ScrollableListContainer listContainer = new ScrollableListContainer();
        listContainer.add(activeSection);
        listContainer.add(inactiveSection);

        final JScrollPane scrollPane = new JScrollPane(listContainer);
        scrollPane.setName("graph-workspace-map-list-scroll");
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);

        final JPanel actions = new JPanel(new GridLayout(2, 2, 4, 4));
        actions.setName("graph-workspace-map-list-actions");
        actions.setBorder(new EmptyBorder(2, 0, 0, 0));
        actions.add(actionButton1);
        actions.add(actionButton2);
        actions.add(retryButton);
        actions.add(locateButton);
        add(actions, BorderLayout.SOUTH);

        actionButton1.addActionListener(event -> handleActionButton1());
        actionButton2.addActionListener(event -> handleActionButton2());
        retryButton.addActionListener(event -> retrySelected());
        locateButton.addActionListener(event -> locateSelected());
        updateButtons();
    }

    private static DeleteConfirmationPrompt defaultDeleteConfirmationPrompt() {
        return (parent, mapDisplayName) -> {
            if (GraphicsEnvironment.isHeadless()) {
                return true;
            }
            final String title = TextUtils.getText("graph_workspace.dialog.delete_map.title");
            final String message = TextUtils.format("graph_workspace.dialog.delete_map.message", mapDisplayName);
            return javax.swing.JOptionPane.showConfirmDialog(parent, message, title,
                javax.swing.JOptionPane.YES_NO_OPTION) == javax.swing.JOptionPane.YES_OPTION;
        };
    }
```
5. Implement `setRows`, selection persistence, and action triggers:
```java
    void setRows(final List<MapRow> values) {
        Objects.requireNonNull(values, "rows");
        final List<MapRow> copy = new ArrayList<MapRow>(values.size());
        for (final MapRow row : values) {
            copy.add(Objects.requireNonNull(row, "row"));
        }
        rows = Collections.unmodifiableList(copy);
        updatingSelection = true;
        try {
            activeModel.clear();
            inactiveModel.clear();
            int selectedActiveIndex = -1;
            int selectedInactiveIndex = -1;

            final MapReferenceId targetSelection = pendingSelectionId != null
                ? pendingSelectionId : (selectedRow() != null ? selectedRow().mapReferenceId() : null);

            for (final MapRow row : rows) {
                if (row.partition() == MapPartition.ACTIVE) {
                    activeModel.addElement(row);
                    if (targetSelection != null && targetSelection.equals(row.mapReferenceId())) {
                        selectedActiveIndex = activeModel.size() - 1;
                    }
                    else if (row.selected() && selectedActiveIndex < 0 && targetSelection == null) {
                        selectedActiveIndex = activeModel.size() - 1;
                    }
                }
                else {
                    inactiveModel.addElement(row);
                    if (targetSelection != null && targetSelection.equals(row.mapReferenceId())) {
                        selectedInactiveIndex = inactiveModel.size() - 1;
                    }
                    else if (row.selected() && selectedInactiveIndex < 0 && targetSelection == null) {
                        selectedInactiveIndex = inactiveModel.size() - 1;
                    }
                }
            }

            activeHeader.setText(TextUtils.format("graph_workspace.map_list.active_heading", activeModel.size()));
            inactiveHeader.setText(TextUtils.format("graph_workspace.map_list.inactive_heading", inactiveModel.size()));

            activeList.setPreferredSize(new Dimension(PANEL_WIDTH, activeModel.size() * ROW_HEIGHT));
            inactiveList.setPreferredSize(new Dimension(PANEL_WIDTH, inactiveModel.size() * ROW_HEIGHT));

            if (selectedActiveIndex >= 0) {
                activeList.setSelectedIndex(selectedActiveIndex);
                inactiveList.clearSelection();
            }
            else if (selectedInactiveIndex >= 0) {
                inactiveList.setSelectedIndex(selectedInactiveIndex);
                activeList.clearSelection();
            }
            else {
                activeList.clearSelection();
                inactiveList.clearSelection();
            }
            pendingSelectionId = null;
        }
        finally {
            updatingSelection = false;
        }
        synchronizeSelection();
    }

    void addSelectionListener(final java.util.function.Consumer<MapRow> listener) {
        selectionListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    void selectMap(final MapReferenceId mapReferenceId) {
        Objects.requireNonNull(mapReferenceId, "mapReferenceId");
        updatingSelection = true;
        try {
            for (int i = 0; i < activeModel.size(); i++) {
                if (mapReferenceId.equals(activeModel.get(i).mapReferenceId())) {
                    activeList.setSelectedIndex(i);
                    inactiveList.clearSelection();
                    return;
                }
            }
            for (int i = 0; i < inactiveModel.size(); i++) {
                if (mapReferenceId.equals(inactiveModel.get(i).mapReferenceId())) {
                    inactiveList.setSelectedIndex(i);
                    activeList.clearSelection();
                    return;
                }
            }
            activeList.clearSelection();
            inactiveList.clearSelection();
        }
        finally {
            updatingSelection = false;
            synchronizeSelection();
        }
    }

    MapRow selectedRow() {
        if (activeList.getSelectedValue() != null) {
            return activeList.getSelectedValue();
        }
        return inactiveList.getSelectedValue();
    }

    JList<MapRow> activeList() {
        return activeList;
    }

    JList<MapRow> inactiveList() {
        return inactiveList;
    }

    JList<MapRow> rowList() {
        return activeList;
    }

    JButton addButton() {
        return actionButton1;
    }

    JButton removeButton() {
        return actionButton2;
    }

    JButton retryButton() {
        return retryButton;
    }

    JButton locateButton() {
        return locateButton;
    }

    void addMapFromChooser() {
        final Path path = pathChooser.get();
        if (path == null || readOnly) {
            return;
        }
        execute(GraphCommands.addMap(MapReferenceId.of(UUID.randomUUID()), path.toUri()));
    }

    void deactivateSelected() {
        final MapRow row = selectedRow();
        if (row != null && !readOnly && row.partition() == MapPartition.ACTIVE) {
            pendingSelectionId = row.mapReferenceId();
            execute(GraphCommands.removeMap(row.mapReferenceId()));
        }
    }

    void reactivateSelected() {
        final MapRow row = selectedRow();
        if (row != null && !readOnly && row.partition() == MapPartition.INACTIVE) {
            pendingSelectionId = row.mapReferenceId();
            execute(GraphCommands.reactivateMap(row.mapReferenceId()));
        }
    }

    void deleteSelected() {
        final MapRow row = selectedRow();
        if (row != null && !readOnly && row.partition() == MapPartition.INACTIVE) {
            if (deletePrompt.confirmDelete(this, row.displayName())) {
                pendingSelectionId = null;
                execute(GraphCommands.deleteMap(row.mapReferenceId()));
            }
        }
    }

    void retrySelected() {
        final MapRow row = selectedRow();
        if (row != null && !readOnly && row.partition() == MapPartition.ACTIVE && row.state() == RowState.RETRYABLE) {
            execute(GraphCommands.retryMap(row.mapReferenceId()));
        }
    }

    void locateSelected() {
        final MapRow row = selectedRow();
        final Path path = pathChooser.get();
        if (row != null && path != null && !readOnly) {
            execute(GraphCommands.locateMap(row.mapReferenceId(), path.toUri()));
        }
    }

    private void handleActionButton1() {
        final MapRow row = selectedRow();
        if (row != null && row.partition() == MapPartition.INACTIVE) {
            reactivateSelected();
        }
        else {
            addMapFromChooser();
        }
    }

    private void handleActionButton2() {
        final MapRow row = selectedRow();
        if (row != null && row.partition() == MapPartition.INACTIVE) {
            deleteSelected();
        }
        else {
            deactivateSelected();
        }
    }

    private void synchronizeSelection() {
        final MapRow selected = selectedRow();
        final List<MapRow> next = new ArrayList<MapRow>(rows.size());
        for (final MapRow row : rows) {
            next.add(row.withSelected(selected != null && row.mapReferenceId().equals(selected.mapReferenceId())));
        }
        rows = Collections.unmodifiableList(next);
        updateButtons();
        for (final java.util.function.Consumer<MapRow> listener : selectionListeners) {
            listener.accept(selected);
        }
    }

    private void updateButtons() {
        final MapRow selected = selectedRow();
        if (selected == null || selected.partition() == MapPartition.ACTIVE) {
            actionButton1.setText(TextUtils.getText("graph_workspace.action.add_map"));
            actionButton1.setEnabled(!readOnly);
            actionButton2.setText(TextUtils.getText("graph_workspace.action.deactivate_map"));
            actionButton2.setEnabled(!readOnly && selected != null && selected.state() != RowState.READ_ONLY);
            retryButton.setEnabled(!readOnly && selected != null && selected.state() == RowState.RETRYABLE);
        }
        else {
            actionButton1.setText(TextUtils.getText("graph_workspace.action.reactivate_map"));
            actionButton1.setEnabled(!readOnly && selected.state() != RowState.READ_ONLY);
            actionButton2.setText(TextUtils.getText("graph_workspace.action.delete_map"));
            actionButton2.setEnabled(!readOnly && selected.state() != RowState.READ_ONLY);
            retryButton.setEnabled(false);
        }
        locateButton.setEnabled(!readOnly && selected != null
            && (selected.state() == RowState.MISSING || selected.state() == RowState.RETRYABLE));
    }
```

- [ ] **Step 4: Run tests to confirm pass**

Run:
```bash
JAVA_HOME=~/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.window.MapListPanelShould"
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/MapListPanel.java \
        freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/MapListPanelShould.java
git commit -m "2026-08-30-active-inactive-maps: Refactor MapListPanel for active/inactive partitions and 2x2 action grid"
```

## Task 5: GraphWorkspaceWindow Integration, Menu Enablement, and End-to-End Verification

**Implementer tier:** Standard

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java:990-1060,1110-1130`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java:500-580`

**Interfaces:**

- Consumes: `MapListPanel`, `MapPartition`, `RowState`, `MapAvailability`.
- Produces: `GraphWorkspaceWindow` with updated map partitioning logic in `updateMapRows`, 6 maps menu items (`Add`, `Deactivate`, `Reactivate`, `Delete`, `Retry`, `Locate`), selection listener wiring, and menu enablement adhering to the partition matrix.

- [ ] **Step 1: Write failing window model tests in GraphWorkspaceWindowModelShould**

Update `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java` to test menu item states for active vs. inactive partitions:

```java
    @Test
    public void updatesMapsMenuEnablementForActiveAndInactivePartitions() {
        MapReferenceId activeId = id(101L);
        MapReferenceId inactiveId = id(102L);
        List<GraphWorkspaceViewBinding.MapRegistration> registrations = Arrays.asList(
            registration(activeId, "Active", MapAvailability.AVAILABLE),
            registration(inactiveId, "Inactive", MapAvailability.INACTIVE));
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), registrations, false);
        GraphWorkspaceWindowModel model = fixture.model();

        // Select Active map
        model.mapList().selectMap(activeId);
        assertThat(model.menuItem("graph_workspace.action.add_map").isEnabled()).isTrue();
        assertThat(model.menuItem("graph_workspace.action.deactivate_map").isEnabled()).isTrue();
        assertThat(model.menuItem("graph_workspace.action.reactivate_map").isEnabled()).isFalse();
        assertThat(model.menuItem("graph_workspace.action.delete_map").isEnabled()).isFalse();

        // Select Inactive map
        model.mapList().selectMap(inactiveId);
        assertThat(model.menuItem("graph_workspace.action.add_map").isEnabled()).isTrue();
        assertThat(model.menuItem("graph_workspace.action.deactivate_map").isEnabled()).isFalse();
        assertThat(model.menuItem("graph_workspace.action.reactivate_map").isEnabled()).isTrue();
        assertThat(model.menuItem("graph_workspace.action.delete_map").isEnabled()).isTrue();

        model.close();
    }

    @Test
    public void preservesInactivePartitionInReadOnlyMode() {
        MapReferenceId inactiveId = id(103L);
        List<GraphWorkspaceViewBinding.MapRegistration> registrations = Collections.singletonList(
            registration(inactiveId, "Inactive", MapAvailability.INACTIVE));
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            emptyState(), registrations, true);
        GraphWorkspaceWindowModel model = fixture.model();

        MapListPanel.MapRow row = model.mapList().rows().get(0);
        assertThat(row.partition()).isEqualTo(MapPartition.INACTIVE);
        assertThat(row.state()).isEqualTo(MapListPanel.RowState.READ_ONLY);
        assertThat(model.mapList().addButton().isEnabled()).isFalse();
        assertThat(model.mapList().removeButton().isEnabled()).isFalse();

        model.close();
    }
```

- [ ] **Step 2: Run tests to confirm failure**

Run:
```bash
JAVA_HOME=~/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould"
```
Expected: Tests fail because menu items and partitioning are not yet wired.

- [ ] **Step 3: Update GraphWorkspaceWindow**

In `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java`:
1. Add menu item fields:
```java
    private JMenuItem mapsAddMenuItem;
    private JMenuItem mapsDeactivateMenuItem;
    private JMenuItem mapsReactivateMenuItem;
    private JMenuItem mapsDeleteMenuItem;
    private JMenuItem mapsRetryMenuItem;
    private JMenuItem mapsLocateMenuItem;
```
2. In the constructor, connect selection listener:
```java
    mapList.addSelectionListener(row -> updateMenuEnablement());
```
3. In `createMenuBar()`:
```java
        final JMenu maps = menu("graph_workspace.menu.maps", "graph-workspace-maps-menu");
        mapsAddMenuItem = item("graph_workspace.action.add_map", "add-map",
            event -> mapList.addMapFromChooser());
        maps.add(mapsAddMenuItem);
        mapsDeactivateMenuItem = item("graph_workspace.action.deactivate_map", "deactivate-map",
            event -> mapList.deactivateSelected());
        maps.add(mapsDeactivateMenuItem);
        mapsReactivateMenuItem = item("graph_workspace.action.reactivate_map", "reactivate-map",
            event -> mapList.reactivateSelected());
        maps.add(mapsReactivateMenuItem);
        mapsDeleteMenuItem = item("graph_workspace.action.delete_map", "delete-map",
            event -> mapList.deleteSelected());
        maps.add(mapsDeleteMenuItem);
        mapsRetryMenuItem = item("graph_workspace.action.retry_map", "retry-map",
            event -> mapList.retrySelected());
        maps.add(mapsRetryMenuItem);
        mapsLocateMenuItem = item("graph_workspace.action.locate_map", "locate-map",
            event -> mapList.locateSelected());
        maps.add(mapsLocateMenuItem);
```
4. In `updateMenuEnablement()`:
```java
        final MapListPanel.MapRow selectedMap = mapList.selectedRow();
        mapsAddMenuItem.setEnabled(!readOnly);
        mapsDeactivateMenuItem.setEnabled(!readOnly && selectedMap != null
            && selectedMap.partition() == MapPartition.ACTIVE
            && selectedMap.state() != MapListPanel.RowState.READ_ONLY);
        mapsReactivateMenuItem.setEnabled(!readOnly && selectedMap != null
            && selectedMap.partition() == MapPartition.INACTIVE
            && selectedMap.state() != MapListPanel.RowState.READ_ONLY);
        mapsDeleteMenuItem.setEnabled(!readOnly && selectedMap != null
            && selectedMap.partition() == MapPartition.INACTIVE
            && selectedMap.state() != MapListPanel.RowState.READ_ONLY);
        mapsRetryMenuItem.setEnabled(!readOnly && selectedMap != null
            && selectedMap.partition() == MapPartition.ACTIVE
            && selectedMap.state() == MapListPanel.RowState.RETRYABLE);
        mapsLocateMenuItem.setEnabled(!readOnly && selectedMap != null
            && (selectedMap.state() == MapListPanel.RowState.MISSING
                || selectedMap.state() == MapListPanel.RowState.RETRYABLE));
```
5. In `updateMapRows(final CanvasState state)`:
```java
        final List<MapListPanel.MapRow> rows = new ArrayList<MapListPanel.MapRow>(accumulators.size());
        for (final RowAccumulator accumulator : accumulators.values()) {
            final boolean isSelected = selectedMapId != null && selectedMapId.equals(accumulator.mapReferenceId);
            final MapPartition partition = accumulator.availability == MapAvailability.INACTIVE
                ? MapPartition.INACTIVE : MapPartition.ACTIVE;
            final MapListPanel.RowState rowState = readOnly ? MapListPanel.RowState.READ_ONLY
                : rowStateFor(accumulator.availability);
            rows.add(MapListPanel.MapRow.of(accumulator.mapReferenceId, accumulator.displayName,
                rowState, partition, accumulator.projectedNodeCount, isSelected));
        }
        mapList.setRows(rows);
        updateMenuEnablement();
```

- [ ] **Step 4: Run module tests to confirm pass**

Run:
```bash
JAVA_HOME=~/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:test
```
Expected: All tests pass.

- [ ] **Step 5: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java \
        freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java
git commit -m "2026-08-30-active-inactive-maps: Wire GraphWorkspaceWindow maps menu and partition updates"
```
