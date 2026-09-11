# Graph Workspace Toolbar Icons & Pin Toggle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for readability; controller state is canonical.

**Goal:** Replace exactly four Graph Workspace toolbar text buttons (`Undo Workspace Change`, `Redo Workspace Change`, `Zoom Out`, `Zoom In`) with scalable preference-sized SVG icon buttons, replace the separate `Pin Node`/`Unpin Node` buttons with one context-sensitive pin toggle whose label always reflects the selected node's pinned state, and turn canvas right-click into a symmetric pin toggle at the hit node's geometry centre.

**Architecture:** Five production changes confined to `freeplane_plugin_graph`: (1) a shared `PinProjection.isPinned` query; (2) an `iconButton` helper plus a single pin toggle in `WorkspaceToolbar`; (3) pin-state publication and one `togglePinSelectedNode` action in `GraphWorkspaceWindowModel`; (4) a symmetric node branch in `GraphInteractionController.handleContext` plus deletion of the two duplicated private `isPinned` helpers; (5) the `AccessibleGraphCanvas` call site moving to the shared query. Thirteen new tests plus the C1/C2/C3 existing-test edits, the C4-C7 contract guards, and the regenerated headless UI evidence discharge design requirements R1-R17.

**Tech Stack:** Java 8 (class major version 52), Swing (`ResourceController.getOptionalIcon`, `IconFactory`/`SVGIconCreator`, `toolbar_icon_height` preference), JUnit 4 with AssertJ and Mockito (`mock`, `verify`, `ArgumentCaptor`, `mockStatic`), Gradle plugin project `freeplane_plugin_graph`, OSGi plugin `freeplane_plugin_graph`.

Requirement coverage (design R1-R17): Task 1 implements R16's shared query (spec T11); Task 2 implements R1-R6 and R17 (spec T1-T3; C4 and C7 must stay green); Task 3 implements R7-R11 and R17 (spec T4-T8; C1-C3 edited); Task 4 implements R10 and R15 (spec T9, T10); Task 5 implements R12-R14 and R16's canvas call sites (spec T12, T13; C5 and C6 must stay green); Task 6 discharges R1 and R4's evidence gate plus V1-V5 (the spec §4.5 harness, V2 counts, V3 evidence, V4 manual acceptance, V5 scope).

Spec-section coverage: P1 -> Task 1; P2a -> Task 2; P2b, P2c, P3 -> Task 3; P4, P5 -> Task 5; P6 (§2.6 summary) -> the Interfaces and step edits of Tasks 1, 2, 3 and 5, with Task 6 V5 confirming the file allowlist. E1 -> Task 2 (T2); E2, E2b, E4, E5, E6, E7, E8 -> Task 3 (E4, E7 and E8 are code-reviewed; E3 is code-reviewed only and must not be unit-tested or removed); E9 -> Task 5 (T13). T1-T3 -> Task 2; T4-T8 -> Task 3; T9, T10 -> Task 4; T11 -> Task 1; T12, T13 -> Task 5; spec §4.5 evidence gate -> Task 6. C1-C3 -> Task 3; C4 and C7 -> Task 2 plus the Task 6 full suite; C5 and C6 -> Task 5 plus the Task 6 full suite. V1, V2, V3, V4, V5 -> Task 6.

## Global Constraints

- Java 8 language level (class major version 52); do not use APIs newer than Java 8; run Gradle with `JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu`.
- Use the repository `gradle` binary (never `gradlew` or Maven), from the repository root; add `-PTestLoggingFull` for verbose failures.
- The only SVG resource paths are `/images/undo.svg?useAccentColor=true`, `/images/redo.svg?useAccentColor=true`, `/images/ZoomIn24.svg?useAccentColor=true`, `/images/ZoomOut24.svg?useAccentColor=true`; no pixel size may be passed by the toolbar.
- The four tooltip/accessible-name keys are exactly `graph_workspace.action.undo_workspace`, `graph_workspace.action.redo_workspace`, `graph_workspace.action.zoom_out`, `graph_workspace.action.zoom_in`; the retained toggle label keys are `graph_workspace.action.pin` and `graph_workspace.action.unpin`; add no new translation keys and change no `Resources_*.properties` file.
- Production files allowed to change, and no others: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/PinProjection.java`, `.../window/WorkspaceToolbar.java`, `.../window/GraphWorkspaceWindow.java`, `.../canvas/GraphInteractionController.java`, `.../canvas/AccessibleGraphCanvas.java`.
- Test files allowed to change, and no others: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java`, `.../canvas/GraphInteractionControllerShould.java`, the new `.../projection/PinProjectionShould.java`, `.../smoke/GraphWorkspaceUiEvidence.java`, plus the regenerated `docs/superpowers/specs/images/2026-08-10-graph-workspace-implemented.png`; `freeplane_plugin_graph/build.gradle` and every `Resources_*.properties` file must stay byte-identical.
- Expected suite counts at revision `50e87ba14a` (V2): `GraphWorkspaceWindowModelShould` 52 to 62 (T1-T10), `GraphInteractionControllerShould` 23 to 25 (T12, T13), new `PinProjectionShould` 1 (T11), `UndoRoutingShould` 3 unchanged (C4), and every other existing suite unchanged and green.
- Evidence expectation (V3): `gradle :freeplane_plugin_graph:graphUiEvidence` exits successfully, prints `Graph UI evidence: EDT shell interactions, desktop workspace, and marker paints passed`, regenerates `docs/superpowers/specs/images/2026-08-10-graph-workspace-implemented.png` with the icon-only toolbar and a single pin button, and passes the in-harness pin-state assertions.
- Evidence and tests run headless (`java.awt.headless=true`); never assert on real icon pixels or real `ResourceController`/`IconFactory` behavior; stubbed lookups are the gate for icon rendering.
- Accepted residual: `WorkspaceToolbar.PREFERRED_SIZE` keeps the row at 42 px, so raising `toolbar_icon_height` above roughly 28-30 pt clips trailing controls; do not add a clamp or a derived row height.
- Existing test methods are never deleted or weakened except the exact C1/C2/C3 edits named in Task 3; C4, C5, C6 and C7 stay byte-identical and green.

## Task 1: Add the shared pinned-state query

**Implementer tier:** Standard
**Lane:** graph-toolbar-icons

**Files:**
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/PinProjection.java:48-52`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/PinProjectionShould.java`

**Interfaces:**
- Consumes: `PinProjection.active(PinRecord record, ProjectedNodeKey node)`, `PinProjection.dormant(PinRecord record)`, `PinProjection.active()`, `PinProjection.projectedNode()` (unchanged, same package); `GraphProjection.projected(long generation, List<ProjectedNode> nodes, List<ProjectedEnclosure> enclosures, List<ProjectedEdge> edges, List<RelationshipResolution> relationshipResolutions, List<PinProjection> pins)`; `ProjectedNode.of(ProjectedNodeKey key, SafeNodeLabel label, String mapName, boolean graphGroup)`; `ProjectedNodeKey.of(SourceNodeKey source)`; `SourceNodeKey.persisted(NodeReference reference)`; `SafeNodeLabel.of(String displayText, String fullText)`; `NodeReference.of(MapReferenceId map, PersistedNodeId node)`; `PersistedNodeId.of(String value)`; `MapReferenceId.of(String canonicalUuid)`; `PinRecord.of(NodeReference node, double x, double y, List<UnknownXml> attributes)`.
- Produces: `public static boolean PinProjection.isPinned(GraphProjection projection, ProjectedNodeKey node)` and the new test `PinProjectionShould.resolvesPinnedStateFromActiveProjectionPins()`.

- [ ] **Step 1: Write the failing test**

Create `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/PinProjectionShould.java` with exactly this content:

```java
package org.freeplane.plugin.graph.projection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;

import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.freeplane.plugin.graph.workspace.model.PinRecord;
import org.junit.Test;

public class PinProjectionShould {
    @Test
    public void resolvesPinnedStateFromActiveProjectionPins() {
        final MapReferenceId map = MapReferenceId.of("00000000-0000-0000-0000-000000000001");
        final NodeReference firstReference = NodeReference.of(map, PersistedNodeId.of("first"));
        final NodeReference secondReference = NodeReference.of(map, PersistedNodeId.of("second"));
        final NodeReference thirdReference = NodeReference.of(map, PersistedNodeId.of("third"));
        final ProjectedNodeKey first = ProjectedNodeKey.of(SourceNodeKey.persisted(firstReference));
        final ProjectedNodeKey second = ProjectedNodeKey.of(SourceNodeKey.persisted(secondReference));
        final ProjectedNodeKey third = ProjectedNodeKey.of(SourceNodeKey.persisted(thirdReference));
        final GraphProjection projection = GraphProjection.projected(1L,
            Arrays.asList(
                ProjectedNode.of(first, SafeNodeLabel.of("First", "First"), "Map", false),
                ProjectedNode.of(second, SafeNodeLabel.of("Second", "Second"), "Map", false),
                ProjectedNode.of(third, SafeNodeLabel.of("Third", "Third"), "Map", false)),
            Collections.<ProjectedEnclosure>emptyList(), Collections.<ProjectedEdge>emptyList(),
            Collections.<RelationshipResolution>emptyList(),
            Arrays.asList(
                PinProjection.active(PinRecord.of(firstReference, 1.0, 2.0, Collections.emptyList()), first),
                PinProjection.dormant(PinRecord.of(secondReference, 3.0, 4.0, Collections.emptyList()))));

        assertThat(PinProjection.isPinned(projection, first)).isTrue();
        assertThat(PinProjection.isPinned(projection, second)).isFalse();
        assertThat(PinProjection.isPinned(projection, third)).isFalse();
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run:

```bash
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.projection.PinProjectionShould" -PTestLoggingFull
```

Expected: FAIL, compilation error `cannot find symbol: method isPinned(GraphProjection,ProjectedNodeKey)`, because `PinProjection.isPinned` does not exist yet.

- [ ] **Step 3: Write the minimal implementation**

In `PinProjection.java`, insert this method directly between `public boolean dormant()` (current lines 48-50) and `public double x()` (current line 52):

```java
    public static boolean isPinned(final GraphProjection projection, final ProjectedNodeKey node) {
        for (final PinProjection pin : projection.pins()) {
            if (pin.active() && pin.projectedNode().isPresent()
                    && node.equals(pin.projectedNode().get())) {
                return true;
            }
        }
        return false;
    }
```

This is the exact semantics of the two private copies being deleted in Task 5, including the redundant `isPresent()` check that mirrors `active()`. No import is needed: `GraphProjection`, `ProjectedNodeKey` and `PinProjection` are all in `org.freeplane.plugin.graph.projection`. Add no null checks; callers pass non-null state as today.

- [ ] **Step 4: Run the test and confirm it passes**

Run:

```bash
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.projection.PinProjectionShould" -PTestLoggingFull
```

Expected: PASS, 1 test (`resolvesPinnedStateFromActiveProjectionPins`).

- [ ] **Step 5: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/projection/PinProjection.java \
    freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/projection/PinProjectionShould.java
git commit -m "Add shared pinned-state query to PinProjection [2026-09-11-graph-toolbar-icons]"
```

## Task 2: Render four toolbar controls as scalable icons

**Implementer tier:** Standard
**Lane:** graph-toolbar-icons

**Files:**
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceToolbar.java:18-29`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceToolbar.java:50-59`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceToolbar.java:360-372`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java:1-70`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java:218-220`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java:1712-1800`

**Interfaces:**
- Consumes: `ResourceController.getOptionalIcon(String iconKey)` returning `javax.swing.Icon` (nullable, not `getIcon`); `TextUtils.getText(String key)`; the existing private `WorkspaceToolbar.configure(AbstractButton button, String name)`; the existing `WorkspaceToolbar.undoButton()`, `redoButton()`, `zoomInButton()`, `zoomOutButton()` accessors returning `JButton`; existing test helpers `fixture(Viewport viewport, CanvasState state, List<GraphWorkspaceViewBinding.MapRegistration> registrations, boolean readOnly)`, `nodeState(MapReferenceId mapId, LayoutPoint center)`, `registration(MapReferenceId id, String name, MapAvailability availability)`, `emptyUnknownXml()`; `GraphWorkspaceHandle.execute(GraphCommand command)`; the existing `EdtResources` static-mock block at lines 1770-1799 and the existing `Fixture` at lines 1715-1768.
- Produces: `private static JButton WorkspaceToolbar.iconButton(String textKey, String name, String iconPath)`; four icon-backed `JButton` fields; test-side `private static Icon GraphWorkspaceWindowModelShould.icon(int width, int height)`, `private Fixture Fixture.stubIcon(String path, Icon icon)`, `private ResourceController Fixture.resourceController()`, `private EdtResources(Map<String, Icon> iconStubs)` with the retained no-arg overload `private EdtResources()`, and `private ResourceController EdtResources.controller()`. New tests: `rendersFourIconOnlyControlsThroughTheScalableIconLookup` (T1), `keepsTextFallbackWhenTheIconLookupReturnsNoIcon` (T2), `keepsUndoRedoEnablementRulesAndZoomButtonsEnabledWithoutHistoryAndInReadOnlySessions` (T3).

- [ ] **Step 1: Write the failing tests and the fixture plumbing**

In `GraphWorkspaceWindowModelShould.java`, add `import javax.swing.Icon;` to the `javax.swing.*` import block (after `import javax.swing.JViewport;`, current line 44).

Insert these three test methods immediately after the closing brace of `composesAHeadlessModelessWorkspaceWithStablePanelsAndApprovedControls` (current line 218) and before `public void growsTheScrollableSurfaceForVisibleWorldGeometry()` (current line 221):

```java
    @Test
    public void rendersFourIconOnlyControlsThroughTheScalableIconLookup() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        Icon undoIcon = icon(24, 16);
        Icon redoIcon = icon(16, 16);
        Icon zoomInIcon = icon(20, 16);
        Icon zoomOutIcon = icon(28, 16);
        fixture.stubIcon("/images/undo.svg?useAccentColor=true", undoIcon);
        fixture.stubIcon("/images/redo.svg?useAccentColor=true", redoIcon);
        fixture.stubIcon("/images/ZoomIn24.svg?useAccentColor=true", zoomInIcon);
        fixture.stubIcon("/images/ZoomOut24.svg?useAccentColor=true", zoomOutIcon);
        GraphWorkspaceWindowModel model = fixture.model();

        assertThat(model.toolbar().undoButton().getIcon()).isSameAs(undoIcon);
        assertThat(model.toolbar().undoButton().getText()).isNull();
        assertThat(model.toolbar().undoButton().getToolTipText())
            .isEqualTo("graph_workspace.action.undo_workspace");
        assertThat(model.toolbar().undoButton().getAccessibleContext().getAccessibleName())
            .isEqualTo("graph_workspace.action.undo_workspace");
        assertThat(model.toolbar().undoButton().getName()).isEqualTo("graph-workspace-undo");

        assertThat(model.toolbar().redoButton().getIcon()).isSameAs(redoIcon);
        assertThat(model.toolbar().redoButton().getText()).isNull();
        assertThat(model.toolbar().redoButton().getToolTipText())
            .isEqualTo("graph_workspace.action.redo_workspace");
        assertThat(model.toolbar().redoButton().getAccessibleContext().getAccessibleName())
            .isEqualTo("graph_workspace.action.redo_workspace");
        assertThat(model.toolbar().redoButton().getName()).isEqualTo("graph-workspace-redo");

        assertThat(model.toolbar().zoomInButton().getIcon()).isSameAs(zoomInIcon);
        assertThat(model.toolbar().zoomInButton().getText()).isNull();
        assertThat(model.toolbar().zoomInButton().getToolTipText())
            .isEqualTo("graph_workspace.action.zoom_in");
        assertThat(model.toolbar().zoomInButton().getAccessibleContext().getAccessibleName())
            .isEqualTo("graph_workspace.action.zoom_in");
        assertThat(model.toolbar().zoomInButton().getName()).isEqualTo("graph-workspace-zoom-in");

        assertThat(model.toolbar().zoomOutButton().getIcon()).isSameAs(zoomOutIcon);
        assertThat(model.toolbar().zoomOutButton().getText()).isNull();
        assertThat(model.toolbar().zoomOutButton().getToolTipText())
            .isEqualTo("graph_workspace.action.zoom_out");
        assertThat(model.toolbar().zoomOutButton().getAccessibleContext().getAccessibleName())
            .isEqualTo("graph_workspace.action.zoom_out");
        assertThat(model.toolbar().zoomOutButton().getName()).isEqualTo("graph-workspace-zoom-out");

        assertThat(model.toolbar().undoButton().getPreferredSize().width
            - model.toolbar().redoButton().getPreferredSize().width).isEqualTo(8);
        assertThat(model.toolbar().zoomOutButton().getPreferredSize().width
            - model.toolbar().zoomInButton().getPreferredSize().width).isEqualTo(8);

        verify(fixture.resourceController()).getOptionalIcon("/images/undo.svg?useAccentColor=true");
        verify(fixture.resourceController()).getOptionalIcon("/images/redo.svg?useAccentColor=true");
        verify(fixture.resourceController()).getOptionalIcon("/images/ZoomIn24.svg?useAccentColor=true");
        verify(fixture.resourceController()).getOptionalIcon("/images/ZoomOut24.svg?useAccentColor=true");
        model.close();
    }

    @Test
    public void keepsTextFallbackWhenTheIconLookupReturnsNoIcon() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();

        assertThat(model.toolbar().undoButton().getIcon()).isNull();
        assertThat(model.toolbar().undoButton().getText())
            .isEqualTo("graph_workspace.action.undo_workspace");
        assertThat(model.toolbar().undoButton().getToolTipText()).isNull();
        assertThat(model.toolbar().undoButton().getName()).isEqualTo("graph-workspace-undo");
        assertThat(model.toolbar().redoButton().getIcon()).isNull();
        assertThat(model.toolbar().redoButton().getText())
            .isEqualTo("graph_workspace.action.redo_workspace");
        assertThat(model.toolbar().redoButton().getToolTipText()).isNull();
        assertThat(model.toolbar().redoButton().getName()).isEqualTo("graph-workspace-redo");
        assertThat(model.toolbar().zoomInButton().getIcon()).isNull();
        assertThat(model.toolbar().zoomInButton().getText())
            .isEqualTo("graph_workspace.action.zoom_in");
        assertThat(model.toolbar().zoomInButton().getToolTipText()).isNull();
        assertThat(model.toolbar().zoomInButton().getName()).isEqualTo("graph-workspace-zoom-in");
        assertThat(model.toolbar().zoomOutButton().getIcon()).isNull();
        assertThat(model.toolbar().zoomOutButton().getText())
            .isEqualTo("graph_workspace.action.zoom_out");
        assertThat(model.toolbar().zoomOutButton().getToolTipText()).isNull();
        assertThat(model.toolbar().zoomOutButton().getName()).isEqualTo("graph-workspace-zoom-out");

        org.mockito.Mockito.clearInvocations(fixture.handle);
        model.toolbar().zoomInButton().doClick();
        verify(fixture.handle, org.mockito.Mockito.times(1)).execute(any(GraphCommand.class));
        model.close();
    }

    @Test
    public void keepsUndoRedoEnablementRulesAndZoomButtonsEnabledWithoutHistoryAndInReadOnlySessions() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false,
            WorkspaceSessionStatus.empty());
        GraphWorkspaceWindowModel model = fixture.model();

        assertThat(model.toolbar().undoButton().isEnabled()).isFalse();
        assertThat(model.toolbar().redoButton().isEnabled()).isFalse();
        assertThat(model.toolbar().zoomInButton().isEnabled()).isTrue();
        assertThat(model.toolbar().zoomOutButton().isEnabled()).isTrue();
        org.mockito.Mockito.clearInvocations(fixture.handle);
        model.toolbar().zoomInButton().doClick();
        verify(fixture.handle, org.mockito.Mockito.times(1)).execute(any(GraphCommand.class));
        model.close();

        Fixture readOnlyFixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), true,
            WorkspaceSessionStatus.empty());
        GraphWorkspaceWindowModel readOnlyModel = readOnlyFixture.model();

        assertThat(readOnlyModel.toolbar().undoButton().isEnabled()).isFalse();
        assertThat(readOnlyModel.toolbar().redoButton().isEnabled()).isFalse();
        assertThat(readOnlyModel.toolbar().zoomInButton().isEnabled()).isTrue();
        assertThat(readOnlyModel.toolbar().zoomOutButton().isEnabled()).isTrue();
        org.mockito.Mockito.clearInvocations(readOnlyFixture.handle);
        readOnlyModel.toolbar().zoomInButton().doClick();
        verify(readOnlyFixture.handle, org.mockito.Mockito.never()).execute(any(GraphCommand.class));
        readOnlyModel.close();
    }
```

Replace the `EdtResources` class (current lines 1770-1799) with this version; the existing `TextUtils` static-mock stubbing and `closeOnEdt()` body stay byte-identical:

```java
    private static final class EdtResources {
        private final MockedStatic<TextUtils> textUtils;
        private final MockedStatic<ResourceController> resourceController;
        private final ResourceController controller;
        private boolean closed;

        private EdtResources() {
            this(Collections.<String, Icon>emptyMap());
        }

        private EdtResources(final Map<String, Icon> iconStubs) {
            controller = mock(ResourceController.class);
            resourceController = org.mockito.Mockito.mockStatic(ResourceController.class);
            resourceController.when(ResourceController::getResourceController).thenReturn(controller);
            when(controller.getOptionalIcon(any(String.class)))
                .thenAnswer(invocation -> iconStubs.get(invocation.getArgument(0)));
            textUtils = org.mockito.Mockito.mockStatic(TextUtils.class);
            textUtils.when(() -> TextUtils.getText(any(String.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
            textUtils.when(() -> TextUtils.getText(any(String.class), any(String.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
            textUtils.when(() -> TextUtils.getRawText(any(String.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
            textUtils.when(() -> TextUtils.getRawText(any(String.class), any(String.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
            textUtils.when(() -> TextUtils.format(any(String.class), any(Object[].class)))
                .thenAnswer(invocation -> formattedText(invocation));
        }

        private ResourceController controller() {
            return controller;
        }

        private void closeOnEdt() {
            if (!closed) {
                closed = true;
                textUtils.close();
                resourceController.close();
            }
        }
    }
```

In `Fixture` (current lines 1715-1768): add these two fields after `private final RecentWorkspaceList recentWorkspaces;`, add these two methods directly after the constructor, and change `modelWithoutLayout` as shown:

```java
        private final Map<String, Icon> iconStubs = new LinkedHashMap<String, Icon>();
        private EdtResources resources;

        private Fixture stubIcon(final String path, final Icon icon) {
            iconStubs.put(path, icon);
            return this;
        }

        private ResourceController resourceController() {
            return resources.controller();
        }
```

```java
        private GraphWorkspaceWindowModel modelWithoutLayout(final Consumer<String> commandMessageSink) {
            final GraphWorkspaceWindowModel[] result = new GraphWorkspaceWindowModel[1];
            final EdtResources[] edtResources = new EdtResources[1];
            GraphWorkspaceWindow.runOnEdt(new Runnable() {
                @Override
                public void run() {
                    edtResources[0] = new EdtResources(iconStubs);
                    result[0] = new GraphWorkspaceWindowModel(handle, binding, applicationController,
                        () -> OPEN_PATH, closeController, () -> { }, () -> { }, () -> { }, commandMessageSink,
                        recentWorkspaces);
                }
            });
            resources = edtResources[0];
            RESOURCES.add(edtResources[0]);
            return result[0];
        }
```

The stub map must be populated before `fixture.model()` is called; the answer reads the live map during toolbar construction on the EDT. Insert the `icon(int, int)` helper immediately before `private static final class Fixture {` (current line 1715):

```java
    private static Icon icon(final int width, final int height) {
        final Icon icon = mock(Icon.class);
        when(icon.getIconWidth()).thenReturn(Integer.valueOf(width));
        when(icon.getIconHeight()).thenReturn(Integer.valueOf(height));
        return icon;
    }
```

- [ ] **Step 2: Run the new tests and confirm the red**

Run:

```bash
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould.rendersFourIconOnlyControlsThroughTheScalableIconLookup" --tests "org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould.keepsTextFallbackWhenTheIconLookupReturnsNoIcon" --tests "org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould.keepsUndoRedoEnablementRulesAndZoomButtonsEnabledWithoutHistoryAndInReadOnlySessions" -PTestLoggingFull
```

Expected: `rendersFourIconOnlyControlsThroughTheScalableIconLookup` FAILS at its first assertion — `expected same instance ... but was null`, because the four buttons are still plain text buttons and no icon is ever set. `keepsTextFallbackWhenTheIconLookupReturnsNoIcon` and `keepsUndoRedoEnablementRulesAndZoomButtonsEnabledWithoutHistoryAndInReadOnlySessions` are expected to PASS before the change: they pin behavior that must not change (R5 and R6). If either of those two fails, stop and report instead of continuing.

- [ ] **Step 3: Write the minimal implementation**

In `WorkspaceToolbar.java`, add `import javax.swing.Icon;` after `import javax.swing.DefaultListCellRenderer;` (current line 19), and add `import org.freeplane.core.resources.ResourceController;` before `import org.freeplane.core.util.TextUtils;` (current line 28).

Replace the two field initializers:

```java
    private final JButton undoButton = button("graph_workspace.action.undo_workspace", "undo");
    private final JButton redoButton = button("graph_workspace.action.redo_workspace", "redo");
```

with:

```java
    private final JButton undoButton = iconButton("graph_workspace.action.undo_workspace", "undo",
        "/images/undo.svg?useAccentColor=true");
    private final JButton redoButton = iconButton("graph_workspace.action.redo_workspace", "redo",
        "/images/redo.svg?useAccentColor=true");
```

Replace the two field initializers:

```java
    private final JButton zoomInButton = button("graph_workspace.action.zoom_in", "zoom-in");
    private final JButton zoomOutButton = button("graph_workspace.action.zoom_out", "zoom-out");
```

with:

```java
    private final JButton zoomInButton = iconButton("graph_workspace.action.zoom_in", "zoom-in",
        "/images/ZoomIn24.svg?useAccentColor=true");
    private final JButton zoomOutButton = iconButton("graph_workspace.action.zoom_out", "zoom-out",
        "/images/ZoomOut24.svg?useAccentColor=true");
```

Insert this helper directly after `button(...)` (current lines 362-366) and before `toggleButton(...)`:

```java
    private static JButton iconButton(final String textKey, final String name,
            final String iconPath) {
        final JButton button = new JButton(TextUtils.getText(textKey));
        final Icon icon = ResourceController.getResourceController().getOptionalIcon(iconPath);
        if (icon != null) {
            button.setIcon(icon);
            button.setText(null);
            button.setToolTipText(TextUtils.getText(textKey));
            button.getAccessibleContext().setAccessibleName(TextUtils.getText(textKey));
        }
        configure(button, name);
        return button;
    }
```

The four paths are exact and must not be altered. No `setPreferredSize` is applied; the button derives its preferred size from the resolved icon. `configure(button, name)` is reused unchanged, so `getName()` keeps `graph-workspace-undo`, `graph-workspace-redo`, `graph-workspace-zoom-in`, `graph-workspace-zoom-out`. Button fields, accessors, listeners (constructor lines 138-145) and `approvedControlNames` are untouched.

- [ ] **Step 4: Run the tests and confirm they pass**

Run:

```bash
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould" --tests "org.freeplane.plugin.graph.window.UndoRoutingShould" -PTestLoggingFull
```

Expected: PASS. `GraphWorkspaceWindowModelShould` reports 55 tests (52 existing plus T1-T3); `UndoRoutingShould` reports its 3 tests unchanged because its mocked `ResourceController` returns `null` from the unstubbed `getOptionalIcon`, so the text fallback keeps its lines 120-121 assertions green (C4). This is the guard that no test installs a global icon stub.

- [ ] **Step 5: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceToolbar.java \
    freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java
git commit -m "Render toolbar undo, redo, and zoom controls as scalable icons [2026-09-11-graph-toolbar-icons]"
```

## Task 3: Replace the two pin buttons with one context-sensitive toggle

**Implementer tier:** Advanced
**Lane:** graph-toolbar-icons

**Files:**
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceToolbar.java:50-80`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceToolbar.java:120-150`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceToolbar.java:225-290`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceToolbar.java:348-360`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java:70-79`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java:466-470`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java:628-640`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java:1280-1312`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java:198-200`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java:638-652`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java:1427-1455`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java:1474-1475`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java:1505-1558`

**Interfaces:**
- Consumes: `boolean PinProjection.isPinned(GraphProjection projection, ProjectedNodeKey node)` from Task 1; `WorkspaceToolbar.pinButton()` returning `JButton`, `WorkspaceToolbar.setPinAction(Runnable)`, `WorkspaceToolbar.setHistoryAvailability(boolean undoAvailable, boolean redoAvailable)`, `WorkspaceToolbar.updateReadOnlyControls()`; `TextUtils.getText(String key)`; the Task 2 test fixture (`Fixture.stubIcon`, `fixture(...)`, `persistedNodeState(MapReferenceId, LayoutPoint)`, `boundaryWithNodeState()`, `nodeState(MapReferenceId, LayoutPoint)`, `registration(...)`, `emptyUnknownXml()`); `GraphIntent.ChangeSelection(Optional<ProjectedEndpointKey> selection)`, `GraphIntent.Pin(ProjectedNodeKey node, double worldX, double worldY)`, `GraphIntent.Unpin(ProjectedNodeKey node)`; `GraphCommands.Pin` accessors `node()`, `x()`, `y()` and `GraphCommands.Unpin.node()`; `NodeReference.of(MapReferenceId, PersistedNodeId)`, `PersistedNodeId.of(String)`, `SourceNodeKey.persisted(NodeReference)`, `ProjectedNodeKey.of(SourceNodeKey)`, `ProjectedEndpointKey.ofNode(ProjectedNodeKey)`, `ProjectedEndpointKey.ofEnclosure(EnclosureKey)`; `PinRecord.of(NodeReference, double, double, List<UnknownXml>)`, `PinProjection.active(PinRecord, ProjectedNodeKey)`, `GraphProjection.projected(long, List<ProjectedNode>, List<ProjectedEnclosure>, List<ProjectedEdge>, List<RelationshipResolution>, List<PinProjection>)`, `GraphGeometry.of(Map, Map)`, `LayoutPositions.of(Map, Map)`, `LayoutFrame.of(long, LayoutPositions, boolean)`, `CanvasState.of(long, GraphProjection, LayoutFrame, GraphGeometry, OperationalStatus)`.
- Produces: `void WorkspaceToolbar.setPinState(boolean enabled, boolean pinned)`; the removals listed in P2b/P2c (`unpinButton` field/accessor, `unpinAction`, `setUnpinAction`, `add(unpinButton)`, `unpinButton.addActionListener(...)`, the `"unpin"` entry in `approvedControlNames`, the `unpinButton.setEnabled(...)` line); `private void GraphWorkspaceWindowModel.togglePinSelectedNode()`; the `toolbar.setPinState(nodeSelected, pinned)` call inside `updateStatusBar()`; test helpers `persistedPinnedNodeState(MapReferenceId mapId, LayoutPoint center, double pinX, double pinY)`, `persistedNodeStateWithoutGeometry(MapReferenceId mapId)`, `persistedPinnedNodeStateWithoutGeometry(MapReferenceId mapId)`; tests T4-T8.

- [ ] **Step 1: Write the failing tests and the C1-C3 edits**

In `GraphWorkspaceWindowModelShould.java`, add these imports: `java.lang.reflect.Method` (after `import java.lang.reflect.Constructor;`), `org.freeplane.plugin.graph.projection.PinProjection` (after `import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;`), and `org.freeplane.plugin.graph.workspace.model.PinRecord` (after `import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;`).

Insert these five test methods immediately after the closing brace of `boundarySelectionDoesNotPinOrSelectAMapRow` (current line 1472) and before `public void connectIntentRequiresBothNodeEndpoints()` (current line 1475):

```java
    @Test
    public void tracksPinToggleLabelAndEnablementAcrossSelectionAndPinnedStates() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            persistedNodeState(ACTIVE_ID, LayoutPoint.of(2.0, -3.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        ProjectedNodeKey nodeKey = ProjectedNodeKey.of(SourceNodeKey.persisted(
            NodeReference.of(ACTIVE_ID, PersistedNodeId.of("selected"))));
        ProjectedEndpointKey nodeEndpoint = ProjectedEndpointKey.ofNode(nodeKey);

        assertThat(model.toolbar().pinButton().getText()).isEqualTo("graph_workspace.action.pin");
        assertThat(model.toolbar().pinButton().isEnabled()).isFalse();
        model.acceptIntent(new GraphIntent.ChangeSelection(Optional.of(nodeEndpoint)));
        assertThat(model.toolbar().pinButton().getText()).isEqualTo("graph_workspace.action.pin");
        assertThat(model.toolbar().pinButton().isEnabled()).isTrue();
        model.acceptCanvasState(persistedPinnedNodeState(ACTIVE_ID, LayoutPoint.of(2.0, -3.0), 2.0, -3.0));
        assertThat(model.toolbar().pinButton().getText()).isEqualTo("graph_workspace.action.unpin");
        assertThat(model.toolbar().pinButton().isEnabled()).isTrue();
        model.acceptIntent(new GraphIntent.ChangeSelection(Optional.<ProjectedEndpointKey>empty()));
        assertThat(model.toolbar().pinButton().getText()).isEqualTo("graph_workspace.action.pin");
        assertThat(model.toolbar().pinButton().isEnabled()).isFalse();
        model.close();

        Fixture boundaryFixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            boundaryWithNodeState(),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel boundaryModel = boundaryFixture.model();
        EnclosureKey boundary = EnclosureKey.of(SourceNodeKey.transientPath(ACTIVE_ID,
            Collections.singletonList(Integer.valueOf(2))));
        ProjectedEndpointKey boundaryEndpoint = ProjectedEndpointKey.ofEnclosure(boundary);

        boundaryModel.acceptIntent(new GraphIntent.ChangeSelection(Optional.of(boundaryEndpoint)));
        assertThat(boundaryModel.toolbar().pinButton().getText()).isEqualTo("graph_workspace.action.pin");
        assertThat(boundaryModel.toolbar().pinButton().isEnabled()).isFalse();
        boundaryModel.close();
    }

    @Test
    public void keepsPinLabelTruthfulWhenReadOnlyDisablesTheToggle() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            persistedPinnedNodeState(ACTIVE_ID, LayoutPoint.of(2.0, -3.0), 2.0, -3.0),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), true);
        GraphWorkspaceWindowModel model = fixture.model();
        ProjectedNodeKey nodeKey = ProjectedNodeKey.of(SourceNodeKey.persisted(
            NodeReference.of(ACTIVE_ID, PersistedNodeId.of("selected"))));
        ProjectedEndpointKey nodeEndpoint = ProjectedEndpointKey.ofNode(nodeKey);

        model.acceptIntent(new GraphIntent.ChangeSelection(Optional.of(nodeEndpoint)));
        assertThat(model.toolbar().pinButton().getText()).isEqualTo("graph_workspace.action.unpin");
        assertThat(model.toolbar().pinButton().isEnabled()).isFalse();
        model.acceptCanvasState(persistedNodeState(ACTIVE_ID, LayoutPoint.of(2.0, -3.0)));
        assertThat(model.toolbar().pinButton().getText()).isEqualTo("graph_workspace.action.pin");
        assertThat(model.toolbar().pinButton().isEnabled()).isFalse();
        model.close();
    }

    @Test
    public void removesTheStandaloneUnpinControlFromToolbarAndApprovedNames() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            nodeState(ACTIVE_ID, LayoutPoint.of(0.0, 0.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();

        assertThat(model.toolbar().approvedControlNames()).doesNotContain("unpin").contains("pin");
        for (Component component : model.toolbar().getComponents()) {
            assertThat(component.getName()).isNotEqualTo("graph-workspace-unpin");
        }
        model.close();
    }

    @Test
    public void pinsTheSelectedNodeAtItsGeometryCentreAndUnpinsWhenPinned() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            persistedNodeState(ACTIVE_ID, LayoutPoint.of(2.0, -3.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        ProjectedNodeKey nodeKey = ProjectedNodeKey.of(SourceNodeKey.persisted(
            NodeReference.of(ACTIVE_ID, PersistedNodeId.of("selected"))));
        ProjectedEndpointKey nodeEndpoint = ProjectedEndpointKey.ofNode(nodeKey);
        model.acceptIntent(new GraphIntent.ChangeSelection(Optional.of(nodeEndpoint)));

        model.toolbar().pinButton().doClick();
        ArgumentCaptor<GraphCommand> commands = ArgumentCaptor.forClass(GraphCommand.class);
        verify(fixture.handle).execute(commands.capture());
        assertThat(commands.getValue()).isInstanceOf(GraphCommands.Pin.class);
        GraphCommands.Pin pin = (GraphCommands.Pin) commands.getValue();
        assertThat(pin.node()).isEqualTo(NodeReference.of(ACTIVE_ID, PersistedNodeId.of("selected")));
        assertThat(pin.x()).isEqualTo(2.0);
        assertThat(pin.y()).isEqualTo(-3.0);

        org.mockito.Mockito.clearInvocations(fixture.handle);
        model.acceptCanvasState(persistedPinnedNodeState(ACTIVE_ID, LayoutPoint.of(2.0, -3.0), 2.0, -3.0));
        assertThat(model.toolbar().pinButton().getText()).isEqualTo("graph_workspace.action.unpin");
        model.toolbar().pinButton().doClick();
        ArgumentCaptor<GraphCommand> unpinCommands = ArgumentCaptor.forClass(GraphCommand.class);
        verify(fixture.handle).execute(unpinCommands.capture());
        assertThat(unpinCommands.getValue()).isInstanceOf(GraphCommands.Unpin.class);
        assertThat(((GraphCommands.Unpin) unpinCommands.getValue()).node())
            .isEqualTo(NodeReference.of(ACTIVE_ID, PersistedNodeId.of("selected")));
        model.close();
    }

    @Test
    public void doesNotCommandPinWithoutGeometryOrSelection() throws Exception {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            persistedNodeStateWithoutGeometry(ACTIVE_ID),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        ProjectedNodeKey nodeKey = ProjectedNodeKey.of(SourceNodeKey.persisted(
            NodeReference.of(ACTIVE_ID, PersistedNodeId.of("selected"))));
        ProjectedEndpointKey nodeEndpoint = ProjectedEndpointKey.ofNode(nodeKey);
        model.acceptIntent(new GraphIntent.ChangeSelection(Optional.of(nodeEndpoint)));

        assertThat(model.toolbar().pinButton().isEnabled()).isTrue();
        model.toolbar().pinButton().doClick();
        verify(fixture.handle, never()).execute(any(GraphCommand.class));

        model.acceptCanvasState(persistedPinnedNodeStateWithoutGeometry(ACTIVE_ID));
        assertThat(model.toolbar().pinButton().getText()).isEqualTo("graph_workspace.action.unpin");
        model.toolbar().pinButton().doClick();
        ArgumentCaptor<GraphCommand> commands = ArgumentCaptor.forClass(GraphCommand.class);
        verify(fixture.handle).execute(commands.capture());
        assertThat(commands.getValue()).isInstanceOf(GraphCommands.Unpin.class);
        org.mockito.Mockito.clearInvocations(fixture.handle);

        model.acceptIntent(new GraphIntent.ChangeSelection(Optional.<ProjectedEndpointKey>empty()));
        Method method = GraphWorkspaceWindowModel.class.getDeclaredMethod("togglePinSelectedNode");
        method.setAccessible(true);
        method.invoke(model);
        verify(fixture.handle, never()).execute(any(GraphCommand.class));
        model.close();
    }
```

Insert these three state builders immediately after `persistedNodeState(MapReferenceId mapId, LayoutPoint center)` (current lines 1505-1517) and before `boundaryWithNodeState()` (current line 1519):

```java
    private static CanvasState persistedPinnedNodeState(MapReferenceId mapId, LayoutPoint center,
            double pinX, double pinY) {
        SourceNodeKey source = SourceNodeKey.persisted(
            NodeReference.of(mapId, PersistedNodeId.of("selected")));
        ProjectedNodeKey key = ProjectedNodeKey.of(source);
        ProjectedNode node = ProjectedNode.of(key, SafeNodeLabel.of("Selected", "Selected"), "Map", true);
        GraphProjection projection = GraphProjection.projected(0L, Collections.singletonList(node),
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
            Collections.singletonList(PinProjection.active(
                PinRecord.of(NodeReference.of(mapId, PersistedNodeId.of("selected")), pinX, pinY,
                    Collections.emptyList()), key)));
        GraphGeometry geometry = GraphGeometry.of(Collections.singletonMap(key, NodeGeometry.of(center, 10.0)),
            Collections.emptyMap());
        LayoutFrame layout = LayoutFrame.of(0L, LayoutPositions.of(
            Collections.singletonMap(key, center), Collections.emptyMap()), false);
        return CanvasState.of(0L, projection, layout, geometry, OperationalStatus.IDLE);
    }

    private static CanvasState persistedNodeStateWithoutGeometry(MapReferenceId mapId) {
        SourceNodeKey source = SourceNodeKey.persisted(
            NodeReference.of(mapId, PersistedNodeId.of("selected")));
        ProjectedNodeKey key = ProjectedNodeKey.of(source);
        ProjectedNode node = ProjectedNode.of(key, SafeNodeLabel.of("Selected", "Selected"), "Map", true);
        GraphProjection projection = GraphProjection.structure(0L, Collections.singletonList(node),
            Collections.emptyList());
        return CanvasState.of(0L, projection,
            LayoutFrame.of(0L, LayoutPositions.of(Collections.emptyMap(), Collections.emptyMap()), false),
            GraphGeometry.of(Collections.emptyMap(), Collections.emptyMap()), OperationalStatus.IDLE);
    }

    private static CanvasState persistedPinnedNodeStateWithoutGeometry(MapReferenceId mapId) {
        SourceNodeKey source = SourceNodeKey.persisted(
            NodeReference.of(mapId, PersistedNodeId.of("selected")));
        ProjectedNodeKey key = ProjectedNodeKey.of(source);
        ProjectedNode node = ProjectedNode.of(key, SafeNodeLabel.of("Selected", "Selected"), "Map", true);
        GraphProjection projection = GraphProjection.projected(0L, Collections.singletonList(node),
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
            Collections.singletonList(PinProjection.active(
                PinRecord.of(NodeReference.of(mapId, PersistedNodeId.of("selected")), 2.0, -3.0,
                    Collections.emptyList()), key)));
        return CanvasState.of(0L, projection,
            LayoutFrame.of(0L, LayoutPositions.of(Collections.emptyMap(), Collections.emptyMap()), false),
            GraphGeometry.of(Collections.emptyMap(), Collections.emptyMap()), OperationalStatus.IDLE);
    }
```

Apply the three C edits:

C1: replace the `approvedControlNames` assertion (current lines 198-200):

```java
        assertThat(model.toolbar().approvedControlNames()).contains(
            "open", "save", "add-map", "remove-map", "select", "connect", "direction", "search",
            "settings", "zoom-in", "zoom-out", "fit-graph", "reset-zoom", "pin", "unpin");
```

with:

```java
        assertThat(model.toolbar().approvedControlNames()).contains(
            "open", "save", "add-map", "remove-map", "select", "connect", "direction", "search",
            "settings", "zoom-in", "zoom-out", "fit-graph", "reset-zoom", "pin").doesNotContain("unpin");
```

C2: replace the three lines in `disablesMutatingControlsForReadOnlySessions` (current lines 644-646):

```java
        assertThat(model.toolbar().saveButton().isEnabled()).isFalse();
        assertThat(model.toolbar().pinButton().isEnabled()).isFalse();
        assertThat(model.toolbar().unpinButton().isEnabled()).isFalse();
```

with:

```java
        assertThat(model.toolbar().saveButton().isEnabled()).isFalse();
        assertThat(model.toolbar().pinButton().isEnabled()).isFalse();
        assertThat(model.toolbar().pinButton().getText()).isEqualTo("graph_workspace.action.pin");
```

C3: change the method signature of `selectsTheSelectedNodeMapRowAndPinsTheSelectedNode` (current line 1427) to `public void selectsTheSelectedNodeMapRowAndPinsTheSelectedNode() throws Exception {` and replace its last four lines (current lines 1449-1452):

```java
        model.acceptIntent(new GraphIntent.ChangeSelection(Optional.<ProjectedEndpointKey>empty()));
        assertThat(model.mapList().rows().get(0).selected()).isFalse();
        model.toolbar().unpinButton().doClick();
        verify(fixture.handle, org.mockito.Mockito.times(1)).execute(any(GraphCommand.class));
```

with:

```java
        model.acceptIntent(new GraphIntent.ChangeSelection(Optional.<ProjectedEndpointKey>empty()));
        assertThat(model.mapList().rows().get(0).selected()).isFalse();
        Method togglePin = GraphWorkspaceWindowModel.class.getDeclaredMethod("togglePinSelectedNode");
        togglePin.setAccessible(true);
        togglePin.invoke(model);
        verify(fixture.handle, org.mockito.Mockito.times(1)).execute(any(GraphCommand.class));
```

The `pinButton().doClick()` at line 1469 inside `boundarySelectionDoesNotPinOrSelectAMapRow` stays as is; with the toggle implemented it becomes a disabled-button click and remains green vacuously, while T4's enclosure step carries the real enclosure assertion.

- [ ] **Step 2: Run the new tests and confirm the red**

Run:

```bash
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould.tracksPinToggleLabelAndEnablementAcrossSelectionAndPinnedStates" --tests "org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould.keepsPinLabelTruthfulWhenReadOnlyDisablesTheToggle" --tests "org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould.removesTheStandaloneUnpinControlFromToolbarAndApprovedNames" --tests "org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould.pinsTheSelectedNodeAtItsGeometryCentreAndUnpinsWhenPinned" --tests "org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould.doesNotCommandPinWithoutGeometryOrSelection" --tests "org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould.composesAHeadlessModelessWorkspaceWithStablePanelsAndApprovedControls" --tests "org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould.selectsTheSelectedNodeMapRowAndPinsTheSelectedNode" -PTestLoggingFull
```

Expected: FAIL. `tracksPinToggleLabelAndEnablementAcrossSelectionAndPinnedStates` fails after the pinned canvas state is published because the old `pinButton` text never changes from `graph_workspace.action.pin`; `keepsPinLabelTruthfulWhenReadOnlyDisablesTheToggle` fails at the same point; `removesTheStandaloneUnpinControlFromToolbarAndApprovedNames` fails `doesNotContain("unpin")`; `pinsTheSelectedNodeAtItsGeometryCentreAndUnpinsWhenPinned` fails on the second click because the old button always pins; `doesNotCommandPinWithoutGeometryOrSelection` fails the pinned-variant `Unpin` verification; the edited `composesAHeadlessModelessWorkspaceWithStablePanelsAndApprovedControls` fails `doesNotContain("unpin")`; the edited `selectsTheSelectedNodeMapRowAndPinsTheSelectedNode` fails with `NoSuchMethodException: togglePinSelectedNode`. `disablesMutatingControlsForReadOnlySessions` is expected to PASS unchanged.

- [ ] **Step 3: Write the minimal implementation**

In `WorkspaceToolbar.java`:

Remove the `unpinButton` field (current line 63) and the `unpinAction` field (current line 74). After `private boolean workspaceRedoAvailable;` (current line 78) add:

```java
    private boolean pinEnabled;
    private boolean pinPinned;
```

In `approvedControlNames` (current lines 64-67) replace the tail `"zoom-in", "zoom-out", "fit-graph", "reset-zoom", "pin", "unpin")));` with `"zoom-in", "zoom-out", "fit-graph", "reset-zoom", "pin")));`.

In the constructor remove `add(unpinButton);` (current line 133) and `unpinButton.addActionListener(event -> unpinAction.run());` (current line 149). The retained wiring is `pinButton.addActionListener(event -> pinAction.run());` (current line 148).

Remove the accessor `JButton unpinButton()` (current lines 229-231) and the setter `void setUnpinAction(...)` (current lines 269-271). `setPinAction` (current lines 265-267) is retained.

Directly after `setHistoryAvailability` (current lines 282-287) add:

```java
    void setPinState(final boolean enabled, final boolean pinned) {
        pinEnabled = enabled;
        pinPinned = pinned;
        updateReadOnlyControls();
    }
```

Replace the whole `updateReadOnlyControls()` method (current lines 350-360), deleting the `unpinButton.setEnabled(!readOnly);` line, with:

```java
    private void updateReadOnlyControls() {
        saveButton.setEnabled(!readOnly);
        saveAsButton.setEnabled(!readOnly);
        undoButton.setEnabled(!readOnly && workspaceUndoAvailable);
        redoButton.setEnabled(!readOnly && workspaceRedoAvailable);
        connectButton.setEnabled(!readOnly);
        settingsButton.setEnabled(!readOnly);
        pinButton.setEnabled(!readOnly && pinEnabled);
        pinButton.setText(TextUtils.getText(pinPinned
            ? "graph_workspace.action.unpin" : "graph_workspace.action.pin"));
        directionComboBox.setEnabled(!readOnly);
    }
```

The truth table (R7/R11) is exactly: `readOnly=true` any `pinEnabled` `pinPinned=false` -> label `Pin Node`, disabled; `readOnly=true` any `pinEnabled` `pinPinned=true` -> label `Unpin Node`, disabled; `readOnly=false` `pinEnabled=false` `pinPinned=false` -> `Pin Node`, disabled; `readOnly=false` `pinEnabled=true` `pinPinned=false` -> `Pin Node`, enabled; `readOnly=false` `pinEnabled=true` `pinPinned=true` -> `Unpin Node`, enabled. The `pinEnabled=false, pinPinned=true` cell cannot occur and is deliberately omitted; no test may assert it. The `graph_workspace.action.unpin` key stays in `freeplane/src/viewer/resources/translations/Resources_en.properties` (line 812) and no translation key is added.

In `GraphWorkspaceWindow.java`:

Add `import org.freeplane.plugin.graph.projection.PinProjection;` between `GraphProjection` (line 73) and `ProjectedEdge` (line 74).

Replace the wiring (current lines 468-469):

```java
        toolbar.setPinAction(this::pinSelectedNode);
        toolbar.setUnpinAction(this::unpinSelectedNode);
```

with:

```java
        toolbar.setPinAction(this::togglePinSelectedNode);
```

In `updateStatusBar()` (current line 628), insert immediately after the `toolbar.setHistoryAvailability(...)` statement (current lines 631-632) and before `undoWorkspaceAction.setEnabled(...)`:

```java
        final boolean nodeSelected = selectedNode != null && currentState != null;
        final boolean pinned = nodeSelected && PinProjection.isPinned(currentState.projection(), selectedNode);
        toolbar.setPinState(nodeSelected, pinned);
```

`updateStatusBar()` already runs on every `ChangeSelection` intent (line 1242), on every accepted canvas state (`acceptCanvasState`, line 967) and on every session-status update (`acceptSessionStatus`, line 622). The short-circuit `nodeSelected &&` covers `currentState == null` without a separate branch (E7); the button is then disabled and labelled `Pin Node`.

Replace `pinSelectedNode()` (current lines 1282-1291) and `unpinSelectedNode()` (current lines 1293-1297) with one method in the same location:

```java
    private void togglePinSelectedNode() {
        if (selectedNode == null || currentState == null) {
            return;
        }
        if (PinProjection.isPinned(currentState.projection(), selectedNode)) {
            executeUnpin(selectedNode);
            return;
        }
        final NodeGeometry geometry = currentState.geometry().nodes().get(selectedNode);
        if (geometry != null) {
            executePin(selectedNode, geometry.center().x(), geometry.center().y());
        }
    }
```

The `isPinned` test precedes the geometry lookup, so a pinned selection still unpins when geometry is absent (E2b). The persisted-reference guard moves entirely into `executePin`/`executeUnpin` (current lines 1299-1311), which stay byte-identical: both read `node.source().persistedReference().orElse(null)` and no-op when the reference is absent (E4) or when `readOnly` is true (R15). The `NodeReference` import (line 85) and `NodeGeometry` import (line 69) remain used.

- [ ] **Step 4: Run the tests and confirm they pass**

Run:

```bash
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould" --tests "org.freeplane.plugin.graph.window.UndoRoutingShould" -PTestLoggingFull
```

Expected: PASS. `GraphWorkspaceWindowModelShould` reports 60 tests (55 after Task 2 plus T4-T8); `UndoRoutingShould` reports 3 unchanged. All five old `unpinButton` references are gone: `unpinButton` field removed, accessor removed, setter removed, `add` call removed, listener removed, `approvedControlNames` no longer contains `unpin`, and `updateReadOnlyControls` no longer touches the removed button. No compatibility shim or deprecated member is kept.

- [ ] **Step 5: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/WorkspaceToolbar.java \
    freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java \
    freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java
git commit -m "Replace the two pin buttons with one context-sensitive toggle [2026-09-11-graph-toolbar-icons]"
```

## Task 4: Prove pin-toggle refresh paths and read-only rejection

**Implementer tier:** Standard
**Lane:** graph-toolbar-icons

**Files:**
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java:1-70`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java:1474-1475`

**Interfaces:**
- Consumes: the Task 3 production behavior (`toolbar.setPinState(nodeSelected, pinned)` in `updateStatusBar`, `togglePinSelectedNode`, and the unchanged `executePin`/`executeUnpin` read-only guards); `WorkspaceSessionStatus.of(boolean workspaceDirty, boolean workspaceUndoAvailable, boolean workspaceRedoAvailable, boolean saveFailed, Set<MapReferenceId> dirtySourceMaps, Optional<MapUndoTarget> sourceMapUndoTarget)`; `WorkspaceSessionStatusListener.onWorkspaceSessionStatus(WorkspaceSessionStatus status)`; `GraphWorkspaceViewBinding.addSessionStatusListener(WorkspaceSessionStatusListener listener)`; `GraphIntent.Unpin(ProjectedNodeKey node)`, `GraphIntent.UnpinAll()`, `GraphIntent.Pin(ProjectedNodeKey node, double worldX, double worldY)`; `GraphCommands.Unpin.node()`, `GraphCommands.UnpinAll`; the Task 3 test helpers `persistedPinnedNodeState(MapReferenceId, LayoutPoint, double, double)`, `persistedNodeState(MapReferenceId, LayoutPoint)`, `fixture(...)`, `fixture.binding`, `fixture.handle`.
- Produces: tests `refreshesPinToggleAfterPostCommandCanvasStatePublication` (T9) and `rejectsPinAndUnpinIntentsInReadOnlySessions` (T10).

- [ ] **Step 1: Write the two tests**

In `GraphWorkspaceWindowModelShould.java`, add `import org.freeplane.plugin.graph.control.WorkspaceSessionStatusListener;` after `import org.freeplane.plugin.graph.control.WorkspaceSessionStatus;`.

Insert these two test methods immediately after the closing brace of `doesNotCommandPinWithoutGeometryOrSelection` and before `public void connectIntentRequiresBothNodeEndpoints()`:

```java
    @Test
    public void refreshesPinToggleAfterPostCommandCanvasStatePublication() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            persistedNodeState(ACTIVE_ID, LayoutPoint.of(2.0, -3.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), false);
        GraphWorkspaceWindowModel model = fixture.model();
        ProjectedNodeKey nodeKey = ProjectedNodeKey.of(SourceNodeKey.persisted(
            NodeReference.of(ACTIVE_ID, PersistedNodeId.of("selected"))));
        ProjectedEndpointKey nodeEndpoint = ProjectedEndpointKey.ofNode(nodeKey);
        model.acceptIntent(new GraphIntent.ChangeSelection(Optional.of(nodeEndpoint)));
        assertThat(model.toolbar().pinButton().getText()).isEqualTo("graph_workspace.action.pin");

        model.acceptCanvasState(persistedPinnedNodeState(ACTIVE_ID, LayoutPoint.of(2.0, -3.0), 2.0, -3.0));
        assertThat(model.toolbar().pinButton().getText()).isEqualTo("graph_workspace.action.unpin");

        org.mockito.Mockito.clearInvocations(fixture.handle);
        ArgumentCaptor<GraphCommand> commands = ArgumentCaptor.forClass(GraphCommand.class);
        model.acceptIntent(new GraphIntent.Unpin(nodeKey));
        verify(fixture.handle, org.mockito.Mockito.times(1)).execute(commands.capture());
        assertThat(commands.getValue()).isInstanceOf(GraphCommands.Unpin.class);
        model.acceptCanvasState(persistedNodeState(ACTIVE_ID, LayoutPoint.of(2.0, -3.0)));
        assertThat(model.toolbar().pinButton().getText()).isEqualTo("graph_workspace.action.pin");

        model.acceptCanvasState(persistedPinnedNodeState(ACTIVE_ID, LayoutPoint.of(2.0, -3.0), 2.0, -3.0));
        assertThat(model.toolbar().pinButton().getText()).isEqualTo("graph_workspace.action.unpin");
        org.mockito.Mockito.clearInvocations(fixture.handle);
        ArgumentCaptor<GraphCommand> allCommands = ArgumentCaptor.forClass(GraphCommand.class);
        model.acceptIntent(new GraphIntent.UnpinAll());
        verify(fixture.handle, org.mockito.Mockito.times(1)).execute(allCommands.capture());
        assertThat(allCommands.getValue()).isInstanceOf(GraphCommands.UnpinAll.class);
        model.acceptCanvasState(persistedNodeState(ACTIVE_ID, LayoutPoint.of(2.0, -3.0)));
        assertThat(model.toolbar().pinButton().getText()).isEqualTo("graph_workspace.action.pin");

        model.acceptCanvasState(persistedPinnedNodeState(ACTIVE_ID, LayoutPoint.of(2.0, -3.0), 2.0, -3.0));
        assertThat(model.toolbar().pinButton().getText()).isEqualTo("graph_workspace.action.unpin");
        ArgumentCaptor<WorkspaceSessionStatusListener> statusListener =
            ArgumentCaptor.forClass(WorkspaceSessionStatusListener.class);
        verify(fixture.binding).addSessionStatusListener(statusListener.capture());
        statusListener.getValue().onWorkspaceSessionStatus(
            WorkspaceSessionStatus.of(true, true, false, false, Collections.<MapReferenceId>emptySet(),
                Optional.<org.freeplane.plugin.graph.command.MapUndoTarget>empty()));
        assertThat(model.toolbar().pinButton().getText()).isEqualTo("graph_workspace.action.unpin");
        assertThat(model.toolbar().pinButton().isEnabled()).isTrue();
        model.close();
    }

    @Test
    public void rejectsPinAndUnpinIntentsInReadOnlySessions() {
        Fixture fixture = fixture(Viewport.of(0.0, 0.0, 1.0, emptyUnknownXml()),
            persistedNodeState(ACTIVE_ID, LayoutPoint.of(2.0, -3.0)),
            Collections.singletonList(registration(ACTIVE_ID, "Active", MapAvailability.AVAILABLE)), true);
        GraphWorkspaceWindowModel model = fixture.model();
        ProjectedNodeKey nodeKey = ProjectedNodeKey.of(SourceNodeKey.persisted(
            NodeReference.of(ACTIVE_ID, PersistedNodeId.of("selected"))));

        model.acceptIntent(new GraphIntent.Pin(nodeKey, 5.0, 6.0));
        model.acceptIntent(new GraphIntent.Unpin(nodeKey));
        verify(fixture.handle, never()).execute(any(GraphCommand.class));
        assertThat(model.toolbar().pinButton().isEnabled()).isFalse();
        model.close();
    }
```

Both tests use `persistedNodeState`/`persistedPinnedNodeState`, whose keys carry `persistedReference()`; with the transient `nodeState` fixture `executePin`/`executeUnpin` would silently no-op and T10 would pass even if the `!readOnly` guard were deleted. Every intent-only path publishes the post-command canvas state before its label assertion, because the intents only call `handle.execute(...)` and do not mutate `currentState`.

- [ ] **Step 2: Prove T9 fails when the refresh publication is removed**

This test is an acceptance test for the Task 3 wiring, so obtain red evidence deterministically. In `GraphWorkspaceWindow.updateStatusBar()`, temporarily remove the single line:

```java
        toolbar.setPinState(nodeSelected, pinned);
```

Then run:

```bash
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould.refreshesPinToggleAfterPostCommandCanvasStatePublication" -PTestLoggingFull
```

Expected: FAIL on the first pinned-state label assertion (the label stays `graph_workspace.action.pin` because nothing calls `setPinState`). Restore the line exactly and verify the production file is byte-identical to its committed state:

```bash
git diff --quiet -- freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java
```

Expected: exit status `0` (no output, no diff). Do not commit the probe state.

- [ ] **Step 3: Prove T10 fails when the read-only guards are removed**

In `GraphWorkspaceWindow.executePin` and `GraphWorkspaceWindow.executeUnpin`, temporarily replace each guard `if (reference != null && !readOnly) {` with `if (reference != null) {`, then run:

```bash
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould.rejectsPinAndUnpinIntentsInReadOnlySessions" -PTestLoggingFull
```

Expected: FAIL on `verify(fixture.handle, never()).execute(any(GraphCommand.class))` because the read-only session now executes the `Pin` command. Restore both guards exactly and verify:

```bash
git diff --quiet -- freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java
```

Expected: exit status `0` (no output, no diff). Do not commit the probe state.

- [ ] **Step 4: Run the two tests with the guards in place and confirm they pass**

Run:

```bash
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould.refreshesPinToggleAfterPostCommandCanvasStatePublication" --tests "org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould.rejectsPinAndUnpinIntentsInReadOnlySessions" -PTestLoggingFull
```

Expected: PASS, 2 tests. `GraphWorkspaceWindowModelShould` now reports 62 tests.

- [ ] **Step 5: Commit**

```bash
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java
git commit -m "Prove pin toggle refresh paths and read-only rejection [2026-09-11-graph-toolbar-icons]"
```

## Task 5: Toggle pin from canvas right-click and share the canvas pinned lookup

**Implementer tier:** Advanced
**Lane:** graph-toolbar-icons

**Files:**
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java:20-23`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java:441-446`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java:494-513`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java:570-581`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvas.java:39`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvas.java:476`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvas.java:518-526`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphInteractionControllerShould.java:401-403`

**Interfaces:**
- Consumes: `boolean PinProjection.isPinned(GraphProjection projection, ProjectedNodeKey node)` from Task 1; `CanvasState.projection()`, `CanvasState.geometry()`; `GraphProjection.pins()`; `GraphGeometry.nodes()` returning `Map<ProjectedNodeKey, NodeGeometry>`; `NodeGeometry.center()` returning `LayoutPoint`; `LayoutPoint.x()`, `LayoutPoint.y()`; `GraphHitIndex.endpointAt(LayoutPoint point)` and `edgeAt(LayoutPoint point, double tolerance)`; `GraphIntent.Pin(ProjectedNodeKey node, double worldX, double worldY)`, `GraphIntent.Unpin(ProjectedNodeKey node)`, `GraphIntent.InspectEdge(ProjectedEdgeKey edge)`, `GraphIntent.ChangeSelection(Optional<ProjectedEndpointKey> selection)`; the existing test fixture `Fixture.create()` with `fixture.canvas()`, `fixture.firstNodeKey`, `fixture.secondNodeKey`, `fixture.edgeKey`, `RecordingListener.last()`, `RecordingListener.intents`, `dispatch(GraphCanvas canvas, AWTEvent event)`, `context(GraphCanvas canvas, double worldX, double worldY)`, `click(GraphCanvas canvas, int id, double worldX, double worldY, int count, int button)`; `GraphCanvas.canvasState()`, `GraphCanvas.worldAt(Point)`.
- Produces: the symmetric `handleContext` node branch (R12-R14); `beginSelect` calling `PinProjection.isPinned(state.projection(), value.node().get())`; removal of the two private `isPinned(CanvasState, ProjectedNodeKey)` helpers and of the `ProjectedNodeKey` import in `AccessibleGraphCanvas`; the `NodeGeometry` import in `GraphInteractionController`; tests `togglesPinOnTheNodeUnderTheContextClickAtItsCentre` (T12) and `dispatchesAtMostOneContextActionPerGesture` (T13).

- [ ] **Step 1: Write the two failing tests**

Insert these two test methods immediately after the closing brace of `translateHoverPinAndContextActions` (current line 401) and before `public void cancelConnectionPreviewBeforeClearingSelection()` (current line 404):

```java
    @Test
    public void togglesPinOnTheNodeUnderTheContextClickAtItsCentre() {
        final Fixture fixture = Fixture.create();
        final GraphCanvas canvas = fixture.canvas();
        final RecordingListener listener = new RecordingListener();
        final GraphInteractionController controller = new GraphInteractionController(listener);
        controller.install(canvas);

        dispatch(canvas, context(canvas, -30.0, 0.0));
        assertThat(listener.last()).isEqualTo(new GraphIntent.Pin(fixture.firstNodeKey, -40.0, 0.0));
        for (GraphIntent intent : listener.intents) {
            assertThat(intent).isNotInstanceOf(GraphIntent.ChangeSelection.class);
        }

        dispatch(canvas, context(canvas, 35.0, 0.0));
        assertThat(listener.last()).isEqualTo(new GraphIntent.Unpin(fixture.secondNodeKey));

        dispatch(canvas, context(canvas, 0.0, 0.0));
        assertThat(listener.last()).isEqualTo(new GraphIntent.InspectEdge(fixture.edgeKey));
        controller.uninstall();
    }

    @Test
    public void dispatchesAtMostOneContextActionPerGesture() {
        final Fixture fixture = Fixture.create();
        final GraphCanvas canvas = fixture.canvas();
        final RecordingListener listener = new RecordingListener();
        final GraphInteractionController controller = new GraphInteractionController(listener);
        controller.install(canvas);

        dispatch(canvas, click(canvas, MouseEvent.MOUSE_PRESSED, -30.0, 0.0, 1, MouseEvent.BUTTON3));
        dispatch(canvas, click(canvas, MouseEvent.MOUSE_RELEASED, -30.0, 0.0, 1, MouseEvent.BUTTON3));
        dispatch(canvas, click(canvas, MouseEvent.MOUSE_CLICKED, -30.0, 0.0, 1, MouseEvent.BUTTON3));

        assertThat(listener.intents).hasSize(1);
        assertThat(listener.last()).isEqualTo(new GraphIntent.Pin(fixture.firstNodeKey, -40.0, 0.0));
        controller.uninstall();
    }
```

`Fixture.create()` already supplies the differential state: `first` is an unpinned persisted node at centre `(-40.0, 0.0)` with radius `20.0`; `second` is a pinned persisted node at centre `(40.0, 0.0)` with radius `8.0`; `edgeKey` connects them; `context(...)` dispatches `MOUSE_PRESSED` with `BUTTON3`. The point `(-30.0, 0.0)` is an interior point of the first node, 10 units from its centre, so it distinguishes the node centre from the cursor position. No fixture change is needed.

- [ ] **Step 2: Run the new tests and confirm the red**

Run:

```bash
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.canvas.GraphInteractionControllerShould.togglesPinOnTheNodeUnderTheContextClickAtItsCentre" --tests "org.freeplane.plugin.graph.canvas.GraphInteractionControllerShould.dispatchesAtMostOneContextActionPerGesture" -PTestLoggingFull
```

Expected: FAIL. `togglesPinOnTheNodeUnderTheContextClickAtItsCentre` fails its first assertion (the old code falls through to edge inspection at `(-30.0, 0.0)`, so `listener.last()` is `InspectEdge(edgeKey)`, not `Pin(firstNodeKey, -40.0, 0.0)`). `dispatchesAtMostOneContextActionPerGesture` fails the intent assertion for the same reason while already emitting exactly one intent. `translateHoverPinAndContextActions` (C5) must stay green throughout: right-click on the pinned `second` node still emits `Unpin(secondNodeKey)` and right-click at `(0.0, 0.0)` still hits the edge and emits `InspectEdge(edgeKey)`.

- [ ] **Step 3: Write the minimal implementation**

In `GraphInteractionController.java`, add `import org.freeplane.plugin.graph.geometry.NodeGeometry;` after `import org.freeplane.plugin.graph.geometry.LayoutPoint;` (current line 22).

In `beginSelect(...)` (current line 441) replace the pinned lookup (current line 445):

```java
        final boolean pinCandidate = value != null && value.isNode()
            && !isPinned(state, value.node().get());
```

with:

```java
        final boolean pinCandidate = value != null && value.isNode()
            && !PinProjection.isPinned(state.projection(), value.node().get());
```

Replace `handleContext(...)` (current lines 494-513) with:

```java
    private void handleContext(final MouseEvent event) {
        final CanvasState state = canvas.canvasState();
        if (state == null) {
            return;
        }
        final LayoutPoint world = canvas.worldAt(event.getPoint());
        final Optional<ProjectedEndpointKey> endpoint = canvas.hitIndex().endpointAt(world);
        if (endpoint.isPresent() && endpoint.get().isNode()) {
            final ProjectedNodeKey node = endpoint.get().node().get();
            if (PinProjection.isPinned(state.projection(), node)) {
                emit(new GraphIntent.Unpin(node));
            }
            else {
                final NodeGeometry geometry = state.geometry().nodes().get(node);
                if (geometry != null) {
                    emit(new GraphIntent.Pin(node, geometry.center().x(), geometry.center().y()));
                }
            }
            return;
        }
        final double zoom = canvas.viewport().zoom();
        final double tolerance = Double.isFinite(zoom) && zoom > 0.0
            ? EDGE_TOLERANCE_PIXELS / zoom : EDGE_TOLERANCE_PIXELS;
        final Optional<ProjectedEdgeKey> edge = canvas.hitIndex().edgeAt(world, tolerance);
        if (edge.isPresent()) {
            emit(new GraphIntent.InspectEdge(edge.get()));
        }
    }
```

Semantics: a right-click that hits a node toggles it (pinned -> `GraphIntent.Unpin(node)`; not pinned -> `GraphIntent.Pin(node, geometry.center().x(), geometry.center().y())`, anchored at the node geometry centre and never at the cursor world point). No `GraphIntent.ChangeSelection` is emitted anywhere in `handleContext`, so the selection is unchanged (R13). `contextGestureDispatched` / `contextHandled` (lines 204-265) keep at most one `handleContext` action per gesture (R14), and the node branch returns early, so a node hit no longer falls through to edge inspection. The `geometry != null` check is defensive only: `GraphHitIndex.from` indexes node endpoints only when `state.geometry().nodes().get(node.key())` is non-null (`GraphHitIndex.java:61-63`), so the branch is unreachable in practice; it is code-reviewed, **must not** be unit-tested, and **must not** be removed. A right-click that hits only an enclosure, or nothing, keeps the existing fall-through to `edgeAt`/`InspectEdge` (E5). Read-only enforcement stays in `GraphWorkspaceWindow.executePin`/`executeUnpin`; this controller continues to emit intents regardless of read-only mode, exactly as before (R15).

Delete the now-unused private helper `isPinned(CanvasState, ProjectedNodeKey)` (current lines 572-580). The `PinProjection` import (line 27) and `ProjectedNodeKey` import (line 26) remain used.

In `AccessibleGraphCanvas.java`, in the endpoint accessible state computation replace the pinned lookup (current line 476):

```java
                        isSelected(state), isPinned(state, node.key()), visibleOutgoingTargets,
```

with:

```java
                        isSelected(state), PinProjection.isPinned(state.projection(), node.key()), visibleOutgoingTargets,
```

Delete the now-unused private static helper `isPinned(CanvasState, ProjectedNodeKey)` (current lines 518-526) and the now-unused `ProjectedNodeKey` import (line 39). The `PinProjection` import (line 34) remains used; `CanvasState` remains used by `isSelected` and the surrounding methods. This deletion is verified at compile time by the single shared call site; the existing accessibility tests in the Task 6 full suite keep the endpoint state green.

- [ ] **Step 4: Run the tests and confirm they pass**

Run:

```bash
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.canvas.GraphInteractionControllerShould" --tests "org.freeplane.plugin.graph.canvas.AccessibleGraphCanvasShould" --tests "org.freeplane.plugin.graph.integration.GraphWorkspaceCommandAcceptanceShould" -PTestLoggingFull
```

Expected: PASS. `GraphInteractionControllerShould` reports 25 tests (23 existing plus T12 and T13) with C5 `translateHoverPinAndContextActions` unchanged and green. `AccessibleGraphCanvasShould` stays green with the shared lookup. `GraphWorkspaceCommandAcceptanceShould` reports its existing tests unchanged: `scenario14SupportsPanZoomFitResetSearchHoverSelectOpenAndInspect` (C6) right-clicks world `(0,0)` and still emits `InspectEdge`, because its fixture projects enclosures only and no nodes.

- [ ] **Step 5: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphInteractionController.java \
    freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/AccessibleGraphCanvas.java \
    freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphInteractionControllerShould.java
git commit -m "Toggle pin from canvas right-click and share the canvas pinned lookup [2026-09-11-graph-toolbar-icons]"
```

## Task 6: Regenerate the UI evidence and run the full verification contract

**Implementer tier:** Advanced
**Lane:** graph-toolbar-icons

**Files:**
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/smoke/GraphWorkspaceUiEvidence.java:46-72`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/smoke/GraphWorkspaceUiEvidence.java:88-104`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/smoke/GraphWorkspaceUiEvidence.java:206-231`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/smoke/GraphWorkspaceUiEvidence.java:290-300`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/smoke/GraphWorkspaceUiEvidence.java:564-605`
- Regenerated evidence: `docs/superpowers/specs/images/2026-08-10-graph-workspace-implemented.png`

**Interfaces:**
- Consumes: `ResourceController.getOptionalIcon(String iconKey)`; `GraphIntent.ChangeSelection(Optional<ProjectedEndpointKey> selection)`; `PinProjection.active(PinRecord record, ProjectedNodeKey node)`; `PinRecord.of(NodeReference node, double x, double y, List<UnknownXml> attributes)`; `ProjectedNode.of(ProjectedNodeKey, SafeNodeLabel, String, boolean)`; `ProjectedNodeKey.of(SourceNodeKey)`; `SourceNodeKey.persisted(NodeReference)`; `NodeReference.of(MapReferenceId, PersistedNodeId)`; `PersistedNodeId.of(String)`; `GraphProjection.structure(long, List<ProjectedNode>, List<ProjectedEnclosure>)` and `GraphProjection.projected(long, List<ProjectedNode>, List<ProjectedEnclosure>, List<ProjectedEdge>, List<RelationshipResolution>, List<PinProjection>)`; `ProjectedEndpointKey.ofNode(ProjectedNodeKey)`; the private harness helpers `compactText(String)`, `findNamed(Container, String)`, `requireComponent(JComponent, String)`; the reflective model API `acceptCanvasState(CanvasState)`, `acceptIntent(GraphIntent)`, `setReadOnly(boolean)`, `completeInitialLayout()`, `close()`, `menuBar()`, `content()`.
- Produces: harness methods `private void verifyPinToggleStates(CanvasState pinnedState)` and `private static void assertPinState(AbstractButton button, String expected, boolean enabled, String description)`; harness state builders `twoMapNodes()`, `twoMapState()` (persisted keys), `pinnedTwoMapState()`, `unpinnedState()`, `twoMapCanvasState(GraphProjection)`; the `ModelAccess.findMethod` fallbacks for `GraphIntent` and `boolean`; the regenerated evidence PNG; V1-V5 evidence.

- [ ] **Step 1: Apply the harness changes**

In the `GraphWorkspaceUiEvidence.java` import block add: `java.util.Optional`, `javax.swing.Icon`, `org.freeplane.plugin.graph.canvas.GraphIntent`, `org.freeplane.plugin.graph.projection.PinProjection`, `org.freeplane.plugin.graph.projection.ProjectedEdge`, `org.freeplane.plugin.graph.projection.ProjectedEnclosure`, `org.freeplane.plugin.graph.projection.ProjectedEndpointKey`, `org.freeplane.plugin.graph.projection.RelationshipResolution`, `org.freeplane.plugin.graph.workspace.model.NodeReference`, `org.freeplane.plugin.graph.workspace.model.PersistedNodeId`, `org.freeplane.plugin.graph.workspace.model.PinRecord`.

Inside the try-with-resources block of `main`, replace:

```java
                    resourceController.when(ResourceController::getResourceController)
                        .thenReturn(mock(ResourceController.class));
                    final EvidenceImages images = new EvidenceImages(desktop, marker);
```

with:

```java
                    final ResourceController controller = mock(ResourceController.class);
                    resourceController.when(ResourceController::getResourceController).thenReturn(controller);
                    final Icon placeholder = mock(Icon.class);
                    when(placeholder.getIconWidth()).thenReturn(Integer.valueOf(16));
                    when(placeholder.getIconHeight()).thenReturn(Integer.valueOf(16));
                    for (final String path : new String[] {
                            "/images/undo.svg?useAccentColor=true",
                            "/images/redo.svg?useAccentColor=true",
                            "/images/ZoomIn24.svg?useAccentColor=true",
                            "/images/ZoomOut24.svg?useAccentColor=true" }) {
                        when(controller.getOptionalIcon(path)).thenReturn(placeholder);
                    }
                    final EvidenceImages images = new EvidenceImages(desktop, marker);
```

Immediately after the `FIRST_MAP` / `SECOND_MAP` constants, add:

```java
    private static final NodeReference FIRST_REFERENCE = NodeReference.of(FIRST_MAP, PersistedNodeId.of("first"));
    private static final NodeReference SECOND_REFERENCE = NodeReference.of(SECOND_MAP, PersistedNodeId.of("second"));
    private static final ProjectedNodeKey FIRST_KEY = ProjectedNodeKey.of(
        SourceNodeKey.persisted(FIRST_REFERENCE));
    private static final ProjectedNodeKey SECOND_KEY = ProjectedNodeKey.of(
        SourceNodeKey.persisted(SECOND_REFERENCE));
```

Replace the whole `twoMapState()` method (current lines 206-231) with:

```java
    private static List<ProjectedNode> twoMapNodes() {
        final ProjectedNode first = ProjectedNode.of(FIRST_KEY, SafeNodeLabel.of("Alpha", "Alpha"),
            "Alpha map", false);
        final ProjectedNode second = ProjectedNode.of(SECOND_KEY, SafeNodeLabel.of("Beta", "Beta"),
            "Beta map", false);
        return Arrays.asList(first, second);
    }

    private static CanvasState twoMapState() {
        final List<ProjectedNode> nodes = twoMapNodes();
        final GraphProjection projection = GraphProjection.structure(7L, nodes, Collections.emptyList());
        return twoMapCanvasState(projection);
    }

    private static CanvasState pinnedTwoMapState() {
        final List<ProjectedNode> nodes = twoMapNodes();
        final GraphProjection projection = GraphProjection.projected(7L, nodes,
            Collections.<ProjectedEnclosure>emptyList(), Collections.<ProjectedEdge>emptyList(),
            Collections.<RelationshipResolution>emptyList(),
            Collections.singletonList(PinProjection.active(PinRecord.of(SECOND_REFERENCE, 110.0, 32.0,
                Collections.emptyList()), SECOND_KEY)));
        return twoMapCanvasState(projection);
    }

    private static CanvasState unpinnedState() {
        return twoMapState();
    }

    private static CanvasState twoMapCanvasState(final GraphProjection projection) {
        final LayoutPoint firstPoint = LayoutPoint.of(-110.0, -32.0);
        final LayoutPoint secondPoint = LayoutPoint.of(110.0, 32.0);
        final java.util.Map<ProjectedNodeKey, NodeGeometry> geometry =
            new java.util.LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
        geometry.put(FIRST_KEY, NodeGeometry.of(firstPoint, 28.0));
        geometry.put(SECOND_KEY, NodeGeometry.of(secondPoint, 28.0));
        final java.util.Map<ProjectedNodeKey, LayoutPoint> positions =
            new java.util.LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        positions.put(FIRST_KEY, firstPoint);
        positions.put(SECOND_KEY, secondPoint);
        return CanvasState.of(7L, projection,
            LayoutFrame.of(0L, LayoutPositions.of(positions, Collections.emptyMap()), false),
            GraphGeometry.of(geometry, Collections.emptyMap()), OperationalStatus.IDLE);
    }
```

In `capture()` (current lines 290-300) insert the new gate directly after `dispatchInteractions();` and before `paintAndVerify(desktop, root);`:

```java
            verifyPinToggleStates(pinnedTwoMapState());
```

Add these two methods inside `EvidenceImages`, for example directly after `dispatchInteractions()`:

```java
        private void verifyPinToggleStates(final CanvasState pinnedState) {
            final JComponent pin = findNamed(root, "graph-workspace-pin");
            requireComponent(pin, "pin toggle");
            final ProjectedEndpointKey firstEndpoint =
                ProjectedEndpointKey.ofNode(pinnedState.projection().nodes().get(0).key());
            final ProjectedEndpointKey secondEndpoint =
                ProjectedEndpointKey.ofNode(pinnedState.projection().nodes().get(1).key());
            modelAccess.invoke("acceptCanvasState", pinnedState);
            modelAccess.invoke("acceptIntent", new GraphIntent.ChangeSelection(Optional.of(secondEndpoint)));
            assertPinState((AbstractButton) pin, "unpin", true, "pinned selection");
            modelAccess.invoke("setReadOnly", Boolean.TRUE);
            assertPinState((AbstractButton) pin, "unpin", false, "read-only pinned selection");
            modelAccess.invoke("acceptCanvasState", unpinnedState());
            assertPinState((AbstractButton) pin, "pin", false, "read-only unpinned selection");
            modelAccess.invoke("setReadOnly", Boolean.FALSE);
            assertPinState((AbstractButton) pin, "pin", true, "unpinned selection");
        }

        private static void assertPinState(final AbstractButton button, final String expected,
                final boolean enabled, final String description) {
            final String actual = compactText(button.getText());
            if (!expected.equals(actual) || button.isEnabled() != enabled) {
                throw new AssertionError("Pin toggle " + description + " expected " + expected + "/"
                    + enabled + " but was " + actual + "/" + button.isEnabled());
            }
        }
```

Extend `ModelAccess.findMethod` (current lines 564-605) by adding, after the existing `CanvasState.class` fallback block and before the final `throw new IllegalStateException(exception);`:

```java
                if (parameterTypes.length == 1 && GraphIntent.class.isAssignableFrom(parameterTypes[0])) {
                    try {
                        return type.getDeclaredMethod(name, GraphIntent.class);
                    }
                    catch (final NoSuchMethodException ignored) {
                        throw new IllegalStateException(exception);
                    }
                }
                if (parameterTypes.length == 1 && parameterTypes[0] == Boolean.class) {
                    try {
                        return type.getDeclaredMethod(name, boolean.class);
                    }
                    catch (final NoSuchMethodException ignored) {
                        throw new IllegalStateException(exception);
                    }
                }
```

`R1`'s real-glyph rendering is deliberately not gated here: the placeholder stub only proves icon-only geometry and layout/no-overlap; T1 gates the icon pipeline. `build.gradle` and translation files must not change.

- [ ] **Step 2: Prove the harness pin gate fails against a broken refresh**

Temporarily remove the single line `toolbar.setPinState(nodeSelected, pinned);` from `GraphWorkspaceWindow.updateStatusBar()`, then run:

```bash
gradle :freeplane_plugin_graph:graphUiEvidence
```

Expected: FAIL with an `AssertionError` from `assertPinState` (for example `Pin toggle pinned selection expected unpin/true but was pin/false`), proving the harness gate is falsifiable. Restore the line exactly and verify:

```bash
git diff --quiet -- freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java
```

Expected: exit status `0` (no output, no diff). Do not commit the probe state.

- [ ] **Step 3: Run the evidence harness and confirm it passes**

Run:

```bash
gradle :freeplane_plugin_graph:graphUiEvidence
```

Expected: BUILD SUCCESSFUL with output `Graph UI evidence: EDT shell interactions, desktop workspace, and marker paints passed`; `docs/superpowers/specs/images/2026-08-10-graph-workspace-implemented.png` is regenerated in place with the icon-only toolbar (four icon slots), one pin button, and no overlapping or clipped siblings. The task keeps the existing two `args`/`outputs.files` (`freeplane_plugin_graph/build.gradle:151-165`) and overwrites only the first image.

- [ ] **Step 4: Run the full plugin suite and confirm the V1/V2 counts**

Run:

```bash
gradle :freeplane_plugin_graph:test -PTestLoggingFull
```

Expected: BUILD SUCCESSFUL. The run includes every existing plugin test green plus the 13 new tests. Expected counts at revision `50e87ba14a`: `GraphWorkspaceWindowModelShould` 62 (52 existing plus T1-T10), `GraphInteractionControllerShould` 25 (23 existing plus T12, T13), `PinProjectionShould` 1 (T11), `UndoRoutingShould` 3 unchanged (C4), and the other existing suites including `GraphWorkspaceCommandAcceptanceShould` (C6) and `GraphPluginIntegrationShould` unchanged and green. Confirm the counts in the generated test report; if a count differs, stop and report the mismatch instead of adjusting assertions.

- [ ] **Step 5: Run the V5 scope check and record V4 manual acceptance**

Run:

```bash
git status --porcelain
git diff --name-only
git diff --cached --name-only
git diff --exit-code -- freeplane_plugin_graph/build.gradle
```

Expected: across the whole plan, the only changed main-source files are `PinProjection.java`, `WorkspaceToolbar.java`, `GraphWorkspaceWindow.java`, `GraphInteractionController.java` and `AccessibleGraphCanvas.java`; the only changed test files are `GraphWorkspaceWindowModelShould.java`, `GraphInteractionControllerShould.java`, the new `PinProjectionShould.java` and `GraphWorkspaceUiEvidence.java`; the regenerated `docs/superpowers/specs/images/2026-08-10-graph-workspace-implemented.png` is changed; `freeplane_plugin_graph/build.gradle` and every `Resources_*.properties` file are byte-identical to revision `50e87ba14a`.

V4 is manual acceptance and is not part of the automated gate: after a full build run `BIN/freeplane.sh`, open a workspace, and confirm (a) the four tooltips show the full names, (b) icons stay crisp under HiDPI scaling and legible in the dark theme, (c) `Pin Node`/`Unpin Node` track selection, drag-pin, right-click toggle, undo/redo, and `Unpin All`, and (d) a `toolbar_icon_height` increase plus restart reproduces the accepted clipping boundary of spec §5.1. Record the observation in the task report; do not change layout code to satisfy it.

- [ ] **Step 6: Commit**

```bash
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/smoke/GraphWorkspaceUiEvidence.java \
    docs/superpowers/specs/images/2026-08-10-graph-workspace-implemented.png
git commit -m "Regenerate the Graph Workspace UI evidence with icon toolbar and pin states [2026-09-11-graph-toolbar-icons]"
```
