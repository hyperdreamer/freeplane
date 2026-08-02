package org.freeplane.plugin.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.junit.Test;
import org.mockito.MockedStatic;

public class FormulaUtilsTest {

    @Test
    public void scriptOfReplacesLeadingFormulaMarkerWithSpaceWithoutChangingLength() {
        String formulaText = "=line1\nline2";

        String script = FormulaUtils.scriptOf(formulaText);

        assertThat(script).isEqualTo(" line1\nline2");
        assertThat(script).hasSize(formulaText.length());
    }

    @Test
    public void nodeScriptUsesReplacedMarkerForFormulaIdentity() {
        MapModel map = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel node = new NodeModel("node", map);
        NodeScript nodeScript = new NodeScript(node, " node.text");

        assertThat(nodeScript.scriptIsContainedIn("=node.text")).isTrue();
    }

    @Test
    public void evalIfScriptPreservesFormulaResultAfterSourceLayoutChange() {
        ensureScriptClasspath();
        ResourceController resourceController = mock(ResourceController.class);
        Controller currentController = mock(Controller.class);
        when(currentController.getResourceController()).thenReturn(resourceController);
        when(resourceController.getBooleanProperty("formula_disable_caching")).thenReturn(false);
        when(resourceController.getBooleanProperty(FormulaUtils.FORMULA_BLOCK_MODE_CONTROLLER_EXECUTE, true))
            .thenReturn(false);
        when(resourceController.getBooleanProperty(ScriptingPermissions.RESOURCES_EXECUTE_SCRIPTS_WITHOUT_READ_RESTRICTION))
            .thenReturn(false);
        when(resourceController.getIntProperty("compiled_script_cache_size", 200)).thenReturn(8);

        try (MockedStatic<Controller> controller = mockStatic(Controller.class);
             MockedStatic<ResourceController> resource = mockStatic(ResourceController.class)) {
            controller.when(Controller::getCurrentController).thenReturn(currentController);
            resource.when(ResourceController::getResourceController).thenReturn(resourceController);

            MapModel map = new MapModel((source, targetMap, withChildren) -> null, null, null);
            NodeModel node = new NodeModel("node", map);

            assertThat(FormulaUtils.evalIfScript(node, "=21*2")).isEqualTo(42);
        }
    }

    private void ensureScriptClasspath() {
        if (ScriptResources.getClasspath() == null) {
            ScriptResources.setClasspath(Collections.<String>emptyList());
        }
    }
}
