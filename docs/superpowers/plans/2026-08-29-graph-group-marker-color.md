# Graph Group Marker Color Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users change the graph group marker color from one toolbar button
next to the "Mark graph group" button; a single application-wide preference
drives both the mind-map marker and the Graph Workspace group boundary color.

**Architecture:** One `ResourceController` property (`graph_group_color`,
default `#DF625D`) read through a small plugin-side accessor
(`GraphGroupColors.currentColor()` with a coral fallback). A new
`GraphGroupColorAction` writes the property; existing painters
(`GraphGroupMarkerPainter`, `GraphPainter`) read it at paint time; property
listeners repaint open `MapView`s (`GraphModeExtension`) and open
Graph Workspace canvases (`GraphWorkspaceWindow`).

**Tech Stack:** Java 8, Swing, JUnit 4, AssertJ, Mockito (incl. `mockStatic`),
Gradle multi-project build, Freeplane `ResourceController` preferences.

## Global Constraints

- Property key is exactly `graph_group_color`; default color is exactly
  `#DF625D` (RGB 0xDF, 0x62, 0x5D) — copy verbatim, do not approximate.
- Action key is exactly `GraphGroupColorAction`; translation keys are
  `GraphGroupColorAction.text`, `GraphGroupColorAction.tooltip`, and
  `choose_graph_group_color`.
- Do not modify `freeplane_api`; all changes live in `freeplane_plugin_graph`
  plus the established plugin resource files under `freeplane/` (menu XML,
  viewer `freeplane.properties`, viewer `Resources_en.properties`).
- Do NOT add a Preferences dialog entry (`preferences.xml` stays untouched).
- `Resources_en.properties` stays ISO-8859-1 ASCII; all added text is plain
  ASCII.
- Every task's requirements implicitly include this section.

## Task 1: Color accessor + mind-map marker painter

**Implementer tier:** Standard

**Files:**

- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/group/GraphGroupColors.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/group/GraphGroupMarkerPainter.java:18-89`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/group/GraphGroupColorsShould.java`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/group/GraphGroupMarkerPainterShould.java:63-160,476-489`

**Interfaces:**

- Consumes: nothing; first task. Uses existing `ResourceController`,
  `ColorUtils`, `NodeView` APIs.
- Produces: `GraphGroupColors.COLOR_PROPERTY_KEY` (`String`),
  `GraphGroupColors.DEFAULT_COLOR` (`java.awt.Color`), and
  `GraphGroupColors.currentColor(): java.awt.Color` — never null; falls back
  to `DEFAULT_COLOR` when the property is missing or invalid.

- [ ] **Step 1: Write the failing test for the accessor**

Create `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/group/GraphGroupColorsShould.java`:

```java
package org.freeplane.plugin.graph.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

import java.awt.Color;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.main.application.ApplicationResourceController;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

public class GraphGroupColorsShould {
    private MockedStatic<ResourceController> resourceController;

    @Before
    public void setUp() {
        resourceController = mockStatic(ResourceController.class);
    }

    @After
    public void tearDown() {
        resourceController.close();
    }

    @Test
    public void returnsTheDefaultCoralWhenThePropertyIsMissing() {
        ApplicationResourceController resources = mock(ApplicationResourceController.class);
        resourceController.when(ResourceController::getResourceController).thenReturn(resources);

        assertThat(GraphGroupColors.currentColor()).isEqualTo(GraphGroupColors.DEFAULT_COLOR);
    }

    @Test
    public void returnsTheDefaultCoralWhenThePropertyIsInvalid() {
        ApplicationResourceController resources = mock(ApplicationResourceController.class,
            CALLS_REAL_METHODS);
        when(resources.getProperty(anyString())).thenReturn("");
        resourceController.when(ResourceController::getResourceController).thenReturn(resources);

        assertThat(GraphGroupColors.currentColor()).isEqualTo(GraphGroupColors.DEFAULT_COLOR);
    }

    @Test
    public void returnsTheConfiguredColorWhenThePropertyIsSet() {
        ApplicationResourceController resources = mock(ApplicationResourceController.class,
            CALLS_REAL_METHODS);
        when(resources.getProperty(GraphGroupColors.COLOR_PROPERTY_KEY)).thenReturn("#112233");
        resourceController.when(ResourceController::getResourceController).thenReturn(resources);

        Color color = GraphGroupColors.currentColor();

        assertThat(color.getRed()).isEqualTo(0x11);
        assertThat(color.getGreen()).isEqualTo(0x22);
        assertThat(color.getBlue()).isEqualTo(0x33);
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run:

```bash
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.8-zulu
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.group.GraphGroupColorsShould"
```

Expected: FAIL (compilation), `GraphGroupColors` cannot be resolved.

- [ ] **Step 3: Write the minimal implementation**

Create `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/group/GraphGroupColors.java`:

```java
package org.freeplane.plugin.graph.group;

import java.awt.Color;

import org.freeplane.core.resources.ResourceController;

public final class GraphGroupColors {
    public static final String COLOR_PROPERTY_KEY = "graph_group_color";
    public static final Color DEFAULT_COLOR = new Color(0xDF, 0x62, 0x5D);

    private GraphGroupColors() {
    }

    public static Color currentColor() {
        try {
            final Color color = ResourceController.getResourceController().getColorProperty(COLOR_PROPERTY_KEY);
            return color != null ? color : DEFAULT_COLOR;
        }
        catch (final RuntimeException exception) {
            return DEFAULT_COLOR;
        }
    }
}
```

Note: `getColorProperty` on a missing key returns `""` and
`ColorUtils.stringToColor("")` throws `NumberFormatException`; when no
`Controller` exists (unit tests), `getResourceController()` may NPE — both are
`RuntimeException`, so the catch covers them.

- [ ] **Step 4: Run the test and confirm it passes**

Run:

```bash
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.8-zulu
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.group.GraphGroupColorsShould"
```

Expected: PASS, 3 tests.

- [ ] **Step 5: Write the failing test for the painter using a configured color**

In `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/group/GraphGroupMarkerPainterShould.java`:

1. Replace the `setUp()` local variable with a field. Change:

```java
    @Before
    public void setUp() {
        ApplicationResourceController resources = mock(ApplicationResourceController.class);
        when(resources.getProperty(anyString())).thenReturn("");
```

to:

```java
    private ApplicationResourceController resources;

    @Before
    public void setUp() {
        resources = mock(ApplicationResourceController.class);
        when(resources.getProperty(anyString())).thenReturn("");
```

2. Add a new test after `rendersNestedMarkersWithLowerAlphaAndAStableDashedStroke` (use the same node-view fixture style as the existing tests):

```java
    @Test
    public void paintsTheConfiguredColorInsteadOfTheDefaultCoral() {
        Color configured = new Color(0x22, 0x55, 0xAA);
        when(resources.getColorProperty(GraphGroupColors.COLOR_PROPERTY_KEY)).thenReturn(configured);
        MapModel map = mapWithRoot();
        NodeModel marked = new NodeModel("marked", map);
        map.getRootNode().insert(marked);
        marked.addExtension(new GraphGroupModel());
        GraphGroupMarkerPainter painter = new GraphGroupMarkerPainter();

        BufferedImage image = image();
        paint(painter, nodeView(marked, new Point[] {
            new Point(20, 20), new Point(20, 45), new Point(115, 45), new Point(115, 20)
        }, null), image);

        assertThat(maxAlphaWithColor(image, configured)).isEqualTo(255);
        assertThat(maxAlphaWithColor(image, GraphGroupColors.DEFAULT_COLOR)).isEqualTo(0);
    }
```

3. Generalize the color helpers. Replace lines 476-489 with:

```java
    private static boolean containsOpaqueCoral(BufferedImage image) {
        return maxAlphaWithCoral(image) == 255;
    }

    private static int maxAlphaWithCoral(BufferedImage image) {
        return maxAlphaWithColor(image, CORAL);
    }

    private static int maxAlphaWithColor(BufferedImage image, Color color) {
        int maxAlpha = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color pixelColor = new Color(image.getRGB(x, y), true);
                if (pixelColor.getRed() == color.getRed() && pixelColor.getGreen() == color.getGreen()
                        && pixelColor.getBlue() == color.getBlue()) {
                    maxAlpha = Math.max(maxAlpha, pixelColor.getAlpha());
                }
            }
        }
        return maxAlpha;
    }
```

- [ ] **Step 6: Run the new painter test and confirm it fails**

Run:

```bash
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.8-zulu
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.group.GraphGroupMarkerPainterShould"
```

Expected: FAIL — `paintsTheConfiguredColorInsteadOfTheDefaultCoral` fails
because the painter still paints fixed coral (max alpha for `configured` is 0).

- [ ] **Step 7: Make the painter read the accessor**

In `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/group/GraphGroupMarkerPainter.java`:

1. Delete the constant `CORAL` (line 18):

```java
    private static final Color CORAL = new Color(0xDF, 0x62, 0x5D);
```

2. Replace the body of `paint` (current lines 38-52) with:

```java
        final boolean inactive = hasMarkedAncestor(node);
        final RoundRectangle2D envelope = envelope(nodeView, coordinates);
        final Color markerColor = GraphGroupColors.currentColor();
        graphics.setColor(withAlpha(markerColor, inactive ? INACTIVE_FILL_ALPHA : ACTIVE_FILL_ALPHA));
        graphics.fill(envelope);
        graphics.setColor(inactive ? withAlpha(markerColor, INACTIVE_STROKE_ALPHA) : markerColor);
        graphics.setStroke(stroke(nodeView, inactive));
        graphics.draw(envelope);
```

3. Replace the `coral(int)` helper (current lines 88-89) with:

```java
    private Color withAlpha(final Color color, final int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }
```

- [ ] **Step 8: Run the painter tests and confirm they pass**

Run:

```bash
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.8-zulu
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.group.GraphGroupMarkerPainterShould"
```

Expected: PASS, all tests (the existing coral tests keep passing because the
mocked `getColorProperty` returns null → `currentColor()` returns
`DEFAULT_COLOR`, which equals the old `CORAL`).

- [ ] **Step 9: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/group/GraphGroupColors.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/group/GraphGroupMarkerPainter.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/group/GraphGroupColorsShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/group/GraphGroupMarkerPainterShould.java
git commit -m "2026-08-29-graph-group-marker-color: Read marker color from graph_group_color preference"
```

## Task 2: Graph Workspace group boundary color

**Implementer tier:** Standard

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java:35,90-121`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java:12-130`

**Interfaces:**

- Consumes: `GraphGroupColors.currentColor(): java.awt.Color` and
  `GraphGroupColors.COLOR_PROPERTY_KEY` from Task 1, never null.
- Produces: group boundary hulls painted with `GraphGroupColors.currentColor()`
  (replacing the hardcoded `GROUP_BOUNDARY_COLOR`).

- [ ] **Step 1: Make the canvas paint test's color lookups deterministic**

In `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java`:

1. Add imports:

```java
import org.freeplane.core.resources.ResourceController;
import org.freeplane.main.application.ApplicationResourceController;
import org.freeplane.plugin.graph.group.GraphGroupColors;
import org.junit.After;
import org.junit.Before;
import org.mockito.MockedStatic;
```

(keep existing imports; add the above next to them)

2. Add fields and `setUp`/`tearDown` right before the first `@Test`:

```java
    private ApplicationResourceController resources;
    private MockedStatic<ResourceController> resourceController;

    @Before
    public void setUp() {
        resourceController = org.mockito.Mockito.mockStatic(ResourceController.class);
        resources = mock(ApplicationResourceController.class);
        resourceController.when(ResourceController::getResourceController).thenReturn(resources);
    }

    @After
    public void tearDown() {
        resourceController.close();
    }
```

(make sure `mock` and `when` are statically imported — add
`import static org.mockito.Mockito.mock;` and
`import static org.mockito.Mockito.when;` if the file does not already have
them)

- [ ] **Step 2: Run the canvas paint tests and confirm they still pass**

Run:

```bash
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.8-zulu
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould"
```

Expected: PASS (the mock's unstubbed `getColorProperty` returns null →
`currentColor()` returns `GraphGroupColors.DEFAULT_COLOR`, still coral).

- [ ] **Step 3: Write the failing test for a configured boundary color**

Add this test after `paintBoundaryShapesOnlyAndNeverNodeCircles`:

```java
    @Test
    public void paintsGroupBoundariesInTheConfiguredColor() {
        Color configured = new Color(0x22, 0x55, 0xAA);
        when(resources.getColorProperty(GraphGroupColors.COLOR_PROPERTY_KEY)).thenReturn(configured);
        EnclosureKey boundaryKey = EnclosureKey.of(source(FIRST_MAP, "boundary-only"));
        EnclosureHullKey boundaryHull = EnclosureHullKey.of(Collections.singletonList(boundaryKey));
        ProjectedEnclosure boundary = ProjectedEnclosure.of(boundaryHull,
            Collections.singletonList(boundaryKey),
            Collections.singletonList(SafeNodeLabel.of("Boundary only", "Boundary only")), "Map",
            Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(),
            Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.SUBTLE);
        ProjectedNode retained = node(FIRST_MAP, "retained", LayoutPoint.of(-45.0, 0.0));
        Map<ProjectedNodeKey, NodeGeometry> nodeGeometry =
            new LinkedHashMap<ProjectedNodeKey, NodeGeometry>();
        nodeGeometry.put(retained.key(), NodeGeometry.of(LayoutPoint.of(-45.0, 0.0), 8.0));
        Map<EnclosureHullKey, HullGeometry> hulls = new LinkedHashMap<EnclosureHullKey, HullGeometry>();
        hulls.put(boundaryHull, rectangle(-50.0, -20.0, 50.0, 20.0, LayoutPoint.of(0.0, 0.0)));
        GraphProjection projection = GraphProjection.projected(1L, Collections.singletonList(retained),
            Collections.singletonList(boundary), Collections.<ProjectedEdge>emptyList(),
            Collections.<RelationshipResolution>emptyList(), Collections.<PinProjection>emptyList());
        LayoutFrame layout = LayoutFrame.of(1L, LayoutPositions.of(
            Collections.singletonMap(retained.key(), LayoutPoint.of(-45.0, 0.0)),
            Collections.singletonMap(boundaryHull, LayoutPoint.of(0.0, 0.0))), false);
        CanvasState state = CanvasState.of(1L, projection, layout,
            GraphGeometry.of(nodeGeometry, hulls), OperationalStatus.IDLE);
        GraphTheme theme = lightTheme();
        BufferedImage image = paint(state, GraphPaintState.empty(), theme, RenderingLevel.FULL);

        assertThat(image.getRGB(120, 70)).isEqualTo(configured.getRGB());
        assertThat(colorPixelsIn(image, new Color(0xDF, 0x62, 0x5D), 0, 0, SIZE.width, SIZE.height))
            .as("default coral must not appear").isZero();
    }
```

- [ ] **Step 4: Run the new canvas test and confirm it fails**

Run:

```bash
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.8-zulu
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould"
```

Expected: FAIL — `paintsGroupBoundariesInTheConfiguredColor` fails because
`GraphPainter` still uses its hardcoded constant.

- [ ] **Step 5: Make the graph painter read the accessor**

In `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java`:

1. Delete the constant (line 35):

```java
    private static final Color GROUP_BOUNDARY_COLOR = new Color(0xDF, 0x62, 0x5D);
```

2. Add the import:

```java
import org.freeplane.plugin.graph.group.GraphGroupColors;
```

3. In `paintHulls` (line 90 onward), right after `final boolean dim = ...;` add:

```java
            final Color groupBoundaryColor = GraphGroupColors.currentColor();
```

4. Replace both group-boundary color usages (lines 109 and 116):

```java
                graphics.setColor(groupBoundaryColor);
```

(remove the old comments "Group boundaries use the fixed coral marker color."
in both branches)

- [ ] **Step 6: Run the canvas tests and confirm they pass**

Run:

```bash
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.8-zulu
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.canvas.GraphCanvasPaintShould"
```

Expected: PASS, all tests.

- [ ] **Step 7: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/canvas/GraphPainter.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/canvas/GraphCanvasPaintShould.java
git commit -m "2026-08-29-graph-group-marker-color: Paint graph workspace boundaries from the preference"
```

## Task 3: Color action, registration, and UI resources

**Implementer tier:** Advanced

**Files:**

- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/group/GraphGroupColorAction.java`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/group/GraphGroupController.java:24-58`
- Modify: `freeplane/src/external/resources/xml/mindmapmodemenu.xml:62`
- Modify: `freeplane/src/viewer/resources/freeplane.properties:25`
- Modify: `freeplane/src/viewer/resources/translations/Resources_en.properties:290,958-959`
- Extend: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/group/GraphGroupActionShould.java:357-377`
- Extend: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphPluginIntegrationShould.java:153-172`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/group/GraphGroupColorActionShould.java`

**Interfaces:**

- Consumes: `GraphGroupColors.currentColor()`, `GraphGroupColors.DEFAULT_COLOR`,
  `GraphGroupColors.COLOR_PROPERTY_KEY` from Task 1.
- Produces: `GraphGroupColorAction` (an `AFreeplaneAction` with key
  `GraphGroupColorAction`) registered by `GraphGroupController`; toolbar entry
  in `mindmapmodemenu.xml` directly after `GraphGroupAction`; icon and
  translation resources.

- [ ] **Step 1: Write the failing action test**

Create `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/group/GraphGroupColorActionShould.java`:

```java
package org.freeplane.plugin.graph.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.event.ActionEvent;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.ColorTracker;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.map.IMapSelection;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

public class GraphGroupColorActionShould {
    private ResourceController resources;
    private MockedStatic<ResourceController> resourceController;
    private MockedStatic<Controller> controllers;
    private MockedStatic<ColorTracker> colorTrackers;
    private MockedStatic<TextUtils> textUtils;

    @Before
    public void setUp() {
        resources = mock(ResourceController.class);
        resourceController = org.mockito.Mockito.mockStatic(ResourceController.class);
        resourceController.when(ResourceController::getResourceController).thenReturn(resources);
        textUtils = org.mockito.Mockito.mockStatic(TextUtils.class);
        textUtils.when(() -> TextUtils.getRawText(any(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        textUtils.when(() -> TextUtils.getRawText(any(String.class), any(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(1));
        textUtils.when(() -> TextUtils.getText(any(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        Controller controller = mock(Controller.class);
        IMapSelection selection = mock(IMapSelection.class);
        when(selection.getSelected()).thenReturn(mock(NodeModel.class));
        when(controller.getSelection()).thenReturn(selection);
        controllers = org.mockito.Mockito.mockStatic(Controller.class);
        controllers.when(Controller::getCurrentController).thenReturn(controller);
        colorTrackers = org.mockito.Mockito.mockStatic(ColorTracker.class);
    }

    @After
    public void tearDown() {
        colorTrackers.close();
        controllers.close();
        textUtils.close();
        resourceController.close();
    }

    @Test
    public void writesTheChosenColorToThePreference() {
        colorTrackers.when(() -> ColorTracker.showCommonJColorChooserDialog(any(NodeModel.class),
            anyString(), any(), any())).thenReturn(new Color(0x11, 0x22, 0x33));

        new GraphGroupColorAction().actionPerformed(new ActionEvent(this, 1, "color"));

        verify(resources).setProperty(GraphGroupColors.COLOR_PROPERTY_KEY, "#112233");
    }

    @Test
    public void writesNothingWhenTheChooserIsCancelled() {
        colorTrackers.when(() -> ColorTracker.showCommonJColorChooserDialog(any(NodeModel.class),
            anyString(), any(), any())).thenReturn(null);

        new GraphGroupColorAction().actionPerformed(new ActionEvent(this, 1, "color"));

        verify(resources, never()).setProperty(anyString(), anyString());
    }

    @Test
    public void seedsTheChooserWithTheCurrentColorAndTheCoralResetColor() {
        Color current = new Color(0x33, 0x44, 0x55);
        when(resources.getColorProperty(GraphGroupColors.COLOR_PROPERTY_KEY)).thenReturn(current);
        colorTrackers.when(() -> ColorTracker.showCommonJColorChooserDialog(any(NodeModel.class),
            anyString(), any(), any())).thenReturn(null);

        new GraphGroupColorAction().actionPerformed(new ActionEvent(this, 1, "color"));

        ArgumentCaptor<Color> initial = ArgumentCaptor.forClass(Color.class);
        ArgumentCaptor<Color> reset = ArgumentCaptor.forClass(Color.class);
        colorTrackers.verify(() -> ColorTracker.showCommonJColorChooserDialog(any(NodeModel.class),
            eq("choose_graph_group_color"), initial.capture(), reset.capture()));
        assertThat(initial.getValue()).isEqualTo(current);
        assertThat(reset.getValue()).isEqualTo(GraphGroupColors.DEFAULT_COLOR);
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run:

```bash
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.8-zulu
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.group.GraphGroupColorActionShould"
```

Expected: FAIL (compilation), `GraphGroupColorAction` cannot be resolved.

- [ ] **Step 3: Write the action**

Create `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/group/GraphGroupColorAction.java`:

```java
package org.freeplane.plugin.graph.group;

import java.awt.Color;
import java.awt.event.ActionEvent;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.ui.ColorTracker;
import org.freeplane.core.util.ColorUtils;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;

final class GraphGroupColorAction extends AFreeplaneAction {
    static final String KEY = "GraphGroupColorAction";
    private static final long serialVersionUID = 1L;

    GraphGroupColorAction() {
        super(KEY);
    }

    @Override
    public void actionPerformed(final ActionEvent event) {
        final Controller controller = Controller.getCurrentController();
        if (controller == null) {
            return;
        }
        final NodeModel selected = controller.getSelection().getSelected();
        final Color actionColor = ColorTracker.showCommonJColorChooserDialog(selected,
            TextUtils.getText("choose_graph_group_color"), GraphGroupColors.currentColor(),
            GraphGroupColors.DEFAULT_COLOR);
        if (actionColor != null) {
            ResourceController.getResourceController().setProperty(GraphGroupColors.COLOR_PROPERTY_KEY,
                ColorUtils.colorToString(actionColor));
        }
    }
}
```

- [ ] **Step 4: Run the test and confirm it passes**

Run:

```bash
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.8-zulu
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.group.GraphGroupColorActionShould"
```

Expected: PASS, 3 tests.

- [ ] **Step 5: Register the action in the controller**

In `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/group/GraphGroupController.java`:

1. Add a field after `private GraphGroupAction graphGroupAction;` (line 24):

```java
    private GraphGroupColorAction graphGroupColorAction;
```

2. In the constructor, after `modeController.addAction(graphGroupAction);` (line 43) add:

```java
        graphGroupColorAction = new GraphGroupColorAction();
        modeController.addAction(graphGroupColorAction);
```

3. In `close()`, after `modeController.removeAction(graphGroupAction.getKey());` add:

```java
        modeController.removeAction(graphGroupColorAction.getKey());
        graphGroupColorAction = null;
```

- [ ] **Step 6: Extend the registration symmetry test**

In `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/group/GraphGroupActionShould.java`,
in `installsAndClosesAllGraphGroupRegistrationsSymmetrically`, after the
existing `verify(modeController).addAction(any(GraphGroupAction.class));` line
add:

```java
        verify(modeController).addAction(any(GraphGroupColorAction.class));
        verify(modeController, times(1)).removeAction("GraphGroupColorAction");
```

- [ ] **Step 7: Add the toolbar entry**

In `freeplane/src/external/resources/xml/mindmapmodemenu.xml`, after line 62
(`<Entry name="GraphGroupAction" />`) insert:

```xml
			<Entry name="GraphGroupColorAction" />
```

- [ ] **Step 8: Add the icon and translations**

1. In `freeplane/src/viewer/resources/freeplane.properties`, after line 25
   (`GraphGroupAction.icon=/images/GraphGroup.svg`) insert:

```
GraphGroupColorAction.icon=/images/Colors24.svg?useAccentColor\=true
```

2. In `freeplane/src/viewer/resources/translations/Resources_en.properties`
   (a) after line 959 (`GraphGroupAction.tooltip=...`) insert:

```
GraphGroupColorAction.text=Graph group marker color
GraphGroupColorAction.tooltip=Change the color of graph group markers
```

   (b) after line 290 (`choose_cloud_color=Choose Cloud Color:`) insert:

```
choose_graph_group_color=Choose Graph Group Marker Color:
```

- [ ] **Step 9: Extend the integration test**

In `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphPluginIntegrationShould.java`,
in `placesAndDescribesBothGraphActionsWithTheirOwnIcons`, after the existing
`int graphGroup = menu.indexOf(...)` line add:

```java
        int colorAction = menu.indexOf("<Entry name=\"GraphGroupColorAction\" />");
        assertThat(colorAction).isGreaterThan(graphGroup);
        assertThat(menu.substring(graphGroup, colorAction))
            .isEqualTo("<Entry name=\"GraphGroupAction\" />\n\t\t\t");
```

and after the existing `GraphGroupAction.tooltip` assertion add:

```java
        assertThat(translations.getProperty("GraphGroupColorAction.text")).isNotBlank();
        assertThat(translations.getProperty("GraphGroupColorAction.tooltip")).isNotBlank();
        assertThat(viewerProperties)
            .contains("GraphGroupColorAction.icon=/images/Colors24.svg?useAccentColor\\=true");
```

- [ ] **Step 10: Run the affected tests**

Run:

```bash
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.8-zulu
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.group.GraphGroupColorActionShould" \
  --tests "org.freeplane.plugin.graph.group.GraphGroupActionShould" \
  --tests "org.freeplane.plugin.graph.window.GraphPluginIntegrationShould"
```

Expected: PASS.

- [ ] **Step 11: Format the translation file**

Run:

```bash
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.8-zulu
gradle format_translation
git status --short
```

Check `git status`: only the intended files are modified; if
`format_translation` touched unrelated translation files, revert them with
`git checkout -- <file>` (the English file's three added lines are already
ASCII so they must survive untouched).

- [ ] **Step 12: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/group/GraphGroupColorAction.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/group/GraphGroupController.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/group/GraphGroupColorActionShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/group/GraphGroupActionShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphPluginIntegrationShould.java \
  freeplane/src/external/resources/xml/mindmapmodemenu.xml \
  freeplane/src/viewer/resources/freeplane.properties \
  freeplane/src/viewer/resources/translations/Resources_en.properties
git commit -m "2026-08-29-graph-group-marker-color: Add group marker color action and toolbar button"
```

## Task 4: Repaint listeners

**Implementer tier:** Advanced

**Files:**

- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/GraphModeExtension.java:26-113`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java:96-135,212-220`
- Extend: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/group/GraphGroupActionShould.java:357-377`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowColorListenerShould.java`

**Interfaces:**

- Consumes: `GraphGroupColors.COLOR_PROPERTY_KEY` from Task 1.
- Produces: on `graph_group_color` property change, all open `MapView`s
  repaint (`GraphModeExtension` listener) and each Graph Workspace canvas
  repaints (`GraphWorkspaceWindow` listener); both listeners are removed
  symmetrically on close.

- [ ] **Step 1: Write the failing test for the window listener factory**

Create `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowColorListenerShould.java`:

```java
package org.freeplane.plugin.graph.window;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;

import org.freeplane.core.resources.IFreeplanePropertyListener;
import org.freeplane.plugin.graph.group.GraphGroupColors;
import org.junit.Test;

public class GraphWorkspaceWindowColorListenerShould {
    @Test
    public void repaintsWhenTheGroupColorPreferenceChanges() {
        final AtomicBoolean repainted = new AtomicBoolean(false);
        IFreeplanePropertyListener listener = GraphWorkspaceWindow.repaintOnColorChange(new Runnable() {
            @Override
            public void run() {
                repainted.set(true);
            }
        });

        listener.propertyChanged(GraphGroupColors.COLOR_PROPERTY_KEY, "#112233", "#df625d");

        assertThat(repainted.get()).isTrue();
    }

    @Test
    public void ignoresUnrelatedPropertyChanges() {
        final AtomicBoolean repainted = new AtomicBoolean(false);
        IFreeplanePropertyListener listener = GraphWorkspaceWindow.repaintOnColorChange(new Runnable() {
            @Override
            public void run() {
                repainted.set(true);
            }
        });

        listener.propertyChanged("some_other_property", "x", "y");

        assertThat(repainted.get()).isFalse();
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run:

```bash
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.8-zulu
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.window.GraphWorkspaceWindowColorListenerShould"
```

Expected: FAIL (compilation), `repaintOnColorChange` cannot be resolved.

- [ ] **Step 3: Add the window listener factory and wiring**

In `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java`:

1. Add imports:

```java
import org.freeplane.core.resources.IFreeplanePropertyListener;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.plugin.graph.group.GraphGroupColors;
```

2. Add a static factory method (place it right after the closing brace of the
   second constructor, before `showWindowOnEdt`):

```java
    static IFreeplanePropertyListener repaintOnColorChange(final Runnable repaint) {
        return new IFreeplanePropertyListener() {
            @Override
            public void propertyChanged(final String propertyName, final String newValue,
                    final String oldValue) {
                if (GraphGroupColors.COLOR_PROPERTY_KEY.equals(propertyName)) {
                    repaint.run();
                }
            }
        };
    }
```

3. Add a field next to `private final GraphCanvas canvas;` (line 264):

```java
    private final IFreeplanePropertyListener colorChangeListener;
```

4. In the main constructor, after the `addWindowListener(new WindowAdapter() { ... });`
   block (before `pack()`), add:

```java
        colorChangeListener = repaintOnColorChange(new Runnable() {
            @Override
            public void run() {
                canvas().repaint();
            }
        });
        ResourceController.getResourceController().addPropertyChangeListener(colorChangeListener);
```

5. In `closeOnEdt()` (line 212), between `closed = true;` and `model.close();`
   add:

```java
        ResourceController.getResourceController().removePropertyChangeListener(colorChangeListener);
```

- [ ] **Step 4: Run the window listener test and confirm it passes**

Run:

```bash
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.8-zulu
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.window.GraphWorkspaceWindowColorListenerShould"
```

Expected: PASS, 2 tests.

- [ ] **Step 5: Write the failing test for the mode extension listener**

In `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/group/GraphGroupActionShould.java`,
add this test right after `installsAndClosesAllGraphGroupRegistrationsSymmetrically`:

```java
    @Test
    public void repaintsAllOpenMapViewsWhenTheColorPreferenceChanges() {
        ApplicationResourceController applicationResources = mock(ApplicationResourceController.class);
        resourceController.when(ResourceController::getResourceController).thenReturn(applicationResources);
        GraphModeExtension extension = new GraphModeExtension();
        extension.installExtension(modeController, null);
        ArgumentCaptor<IFreeplanePropertyListener> captor =
            ArgumentCaptor.forClass(IFreeplanePropertyListener.class);
        verify(applicationResources).addPropertyChangeListener(captor.capture());

        MapView mapView = mock(MapView.class);
        IMapViewManager mapViewManager = mock(IMapViewManager.class);
        when(mapViewManager.getMapViews()).thenReturn(Collections.singletonList(mapView));
        Controller controller = mock(Controller.class);
        when(controller.getMapViewManager()).thenReturn(mapViewManager);
        try (MockedStatic<Controller> controllers = org.mockito.Mockito.mockStatic(Controller.class)) {
            controllers.when(Controller::getCurrentController).thenReturn(controller);
            captor.getValue().propertyChanged(GraphGroupColors.COLOR_PROPERTY_KEY, "#112233", "#df625d");
        }

        verify(mapView).repaint();
    }
```

Add these imports to the test file if absent:

```java
import org.freeplane.core.resources.IFreeplanePropertyListener;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.ui.IMapViewManager;
import org.freeplane.view.swing.map.MapView;
```

- [ ] **Step 6: Run the test and confirm it fails**

Run:

```bash
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.8-zulu
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.group.GraphGroupActionShould"
```

Expected: FAIL — `verify(applicationResources).addPropertyChangeListener(...)`
fails because `GraphModeExtension` does not register a listener yet.

- [ ] **Step 7: Implement the mode extension listener**

In `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/GraphModeExtension.java`:

1. Add imports:

```java
import java.awt.Component;

import org.freeplane.core.resources.IFreeplanePropertyListener;
import org.freeplane.core.util.ColorUtils;
import org.freeplane.features.mode.Controller;
import org.freeplane.plugin.graph.group.GraphGroupColors;
```

2. Add a field next to `private GraphGroupMarkerPainter graphGroupMarkerPainter;`:

```java
    private IFreeplanePropertyListener graphColorChangeListener;
```

3. In `installExtension`, after `modeController.addAction(openGraphWorkspaceAction);`
   add:

```java
        resourceController.setDefaultProperty(GraphGroupColors.COLOR_PROPERTY_KEY,
            ColorUtils.colorToString(GraphGroupColors.DEFAULT_COLOR));
        graphColorChangeListener = new IFreeplanePropertyListener() {
            @Override
            public void propertyChanged(final String propertyName, final String newValue,
                    final String oldValue) {
                if (GraphGroupColors.COLOR_PROPERTY_KEY.equals(propertyName)) {
                    repaintMapViews();
                }
            }
        };
        resourceController.addPropertyChangeListener(graphColorChangeListener);
```

4. Add the private helper method (after `close()`):

```java
    private static void repaintMapViews() {
        final Controller controller = Controller.getCurrentController();
        if (controller == null) {
            return;
        }
        for (final Component mapView : controller.getMapViewManager().getMapViews()) {
            mapView.repaint();
        }
    }
```

5. In `close()`, in the innermost `finally` block, immediately before
   `if (graphGroupController != null) { graphGroupController.close(); }` add:

```java
                    if (graphColorChangeListener != null) {
                        ResourceController.getResourceController()
                            .removePropertyChangeListener(graphColorChangeListener);
                    }
```

6. In the final `finally` block, next to the other null-outs, add:

```java
                graphColorChangeListener = null;
```

- [ ] **Step 8: Run the test and confirm it passes**

Run:

```bash
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.8-zulu
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.group.GraphGroupActionShould"
```

Expected: PASS, all tests.

- [ ] **Step 9: Verify listener symmetry + net effect**

In `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/group/GraphGroupActionShould.java`,
in `installsAndClosesAllGraphGroupRegistrationsSymmetrically`, add after the
existing `verify(modeController, times(1)).removeAction("GraphGroupColorAction");`
line:

```java
        verify(applicationResources).addPropertyChangeListener(any(IFreeplanePropertyListener.class));
        verify(applicationResources).removePropertyChangeListener(any(IFreeplanePropertyListener.class));
```

Run:

```bash
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.8-zulu
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.group.GraphGroupActionShould"
```

Expected: PASS.

- [ ] **Step 10: Full module test run**

Run:

```bash
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.8-zulu
gradle :freeplane_plugin_graph:test
```

Expected: PASS (note: `AccessibleGraphCanvasShould` may need a display; if it
fails with `HeadlessException` in this environment, run with
`xvfb-run -a gradle :freeplane_plugin_graph:test` or note it as a pre-existing
environment failure only if it also fails on the baseline commit).

- [ ] **Step 11: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/GraphModeExtension.java \
  freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/group/GraphGroupActionShould.java \
  freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowColorListenerShould.java
git commit -m "2026-08-29-graph-group-marker-color: Repaint maps and graph canvases on color preference change"
```
