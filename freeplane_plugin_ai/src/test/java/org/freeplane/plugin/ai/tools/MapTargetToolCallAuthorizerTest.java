package org.freeplane.plugin.ai.tools;

import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MapTargetToolCallAuthorizerTest {
    private final MapTargetToolCallAuthorizer uut = new MapTargetToolCallAuthorizer();

    @Test
    public void rejectsDocumentationMapForEditingTool() {
        assertThatThrownBy(() -> uut.assertAuthorized(
            "createNodes",
            AvailableMaps.INTERNAL_API_DOCUMENTATION_MAP_IDENTIFIER.toString()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(MapTargetToolCallAuthorizer.DOCUMENTATION_MAP_EDITING_MESSAGE);
    }

    @Test
    public void rejectsDocumentationMapForFormulaTool() {
        assertThatThrownBy(() -> uut.assertAuthorized(
            "previewFormulaUpdates",
            AvailableMaps.INTERNAL_API_DOCUMENTATION_MAP_IDENTIFIER.toString()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(MapTargetToolCallAuthorizer.DOCUMENTATION_MAP_SCRIPTING_MESSAGE);
    }

    @Test
    public void allowsOrdinaryMapForEditingTool() {
        assertThatCode(() -> uut.assertAuthorized(
            "createNodes",
            "ordinary-map"))
            .doesNotThrowAnyException();
    }

    @Test
    public void allowsOrdinaryMapForFormulaTool() {
        assertThatCode(() -> uut.assertAuthorized(
            "previewFormulaUpdates",
            "ordinary-map"))
            .doesNotThrowAnyException();
    }
}
