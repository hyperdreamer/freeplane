package org.freeplane.plugin.ai.tools;

import java.util.Collections;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.tools.create.CreateNodesRequest;
import org.freeplane.plugin.ai.tools.create.CreateNodesResponse;
import org.freeplane.plugin.ai.tools.create.CreateNodesTool;
import org.freeplane.plugin.ai.tools.formula.FormulaUpdatePreviewRequest;
import org.freeplane.plugin.ai.tools.formula.FormulaUpdatePreviewResponse;
import org.freeplane.plugin.ai.tools.formula.FormulaUpdateTool;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AIToolSetTest {
    @Test
    public void createNodesDelegatesDocumentationMapAuthorization() {
        MapTargetToolCallAuthorizer mapTargetToolCallAuthorizer = mock(MapTargetToolCallAuthorizer.class);
        CreateNodesTool createNodesTool = mock(CreateNodesTool.class);
        FormulaUpdateTool formulaUpdateTool = mock(FormulaUpdateTool.class);
        AIToolSet uut = new AIToolSet(mapTargetToolCallAuthorizer, createNodesTool, formulaUpdateTool);
        CreateNodesRequest request = new CreateNodesRequest(
            AvailableMaps.INTERNAL_API_DOCUMENTATION_MAP_IDENTIFIER.toString(),
            null,
            null,
            Collections.emptyList());
        when(createNodesTool.createNodes(request)).thenReturn(new CreateNodesResponse(
            request.getMapIdentifier(),
            null,
            Collections.emptyList()));

        uut.createNodes(request);

        verify(mapTargetToolCallAuthorizer).assertAuthorized("createNodes", request.getMapIdentifier());
        verify(createNodesTool).createNodes(request);
    }

    @Test
    public void previewFormulaUpdatesDelegatesDocumentationMapAuthorization() {
        MapTargetToolCallAuthorizer mapTargetToolCallAuthorizer = mock(MapTargetToolCallAuthorizer.class);
        CreateNodesTool createNodesTool = mock(CreateNodesTool.class);
        FormulaUpdateTool formulaUpdateTool = mock(FormulaUpdateTool.class);
        AIToolSet uut = new AIToolSet(mapTargetToolCallAuthorizer, createNodesTool, formulaUpdateTool);
        FormulaUpdatePreviewRequest request = new FormulaUpdatePreviewRequest(
            AvailableMaps.INTERNAL_API_DOCUMENTATION_MAP_IDENTIFIER.toString(),
            null,
            Collections.emptyList());
        when(formulaUpdateTool.previewFormulaUpdates(request)).thenReturn(new FormulaUpdatePreviewResponse(
            request.getMapIdentifier(),
            "preview-id",
            Collections.emptyList()));

        uut.previewFormulaUpdates(request);

        verify(mapTargetToolCallAuthorizer).assertAuthorized("previewFormulaUpdates", request.getMapIdentifier());
        verify(formulaUpdateTool).previewFormulaUpdates(request);
    }
}
