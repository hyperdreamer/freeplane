package org.freeplane.plugin.ai.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.Objects;
import java.util.function.Supplier;
import org.freeplane.features.map.IMapSelection;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.text.TextController;
import org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevel;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.text.NodeTextPreviewFormatter;
import org.freeplane.plugin.ai.tools.selection.SelectionIdentifiersBuilder;
import org.freeplane.plugin.ai.tools.selection.SelectionIdentifiersResponse;

public class AiPromptRequestComposer {
    private final Supplier<SelectionIdentifiersResponse> selectionIdentifiersSupplier;
    private final ObjectMapper objectMapper;

    public AiPromptRequestComposer(AvailableMaps availableMaps, TextController textController) {
        this(createSelectionIdentifiersSupplier(availableMaps, textController), new ObjectMapper());
    }

    AiPromptRequestComposer(Supplier<SelectionIdentifiersResponse> selectionIdentifiersSupplier,
                            ObjectMapper objectMapper) {
        this.selectionIdentifiersSupplier = Objects.requireNonNull(selectionIdentifiersSupplier,
            "selectionIdentifiersSupplier");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public String compose(AiPrompt prompt) {
        return compose(prompt == null ? null : prompt.getPrompt(), ToolAvailabilityLevel.EDITING);
    }

    public String compose(AiPrompt prompt, ToolAvailabilityLevel toolAvailability) {
        return compose(prompt == null ? null : prompt.getPrompt(), toolAvailability, null);
    }

    public String compose(String promptText,
                          ToolAvailabilityLevel toolAvailability,
                          SelectionIdentifiersResponse selectionIdentifiersOverride) {
        SelectionIdentifiersResponse response = selectionIdentifiersOverride != null
            ? selectionIdentifiersOverride
            : selectionIdentifiersSupplier.get();
        SelectionIdentifiersResponse safeResponse = response == null
            ? new SelectionIdentifiersResponse(null, null, null, Collections.emptyList(), 0, 0)
            : response;
        try {
            String json = objectMapper.writeValueAsString(safeResponse);
            String safePromptText = promptText == null ? "" : promptText;
            if (!toolAvailability.includesTools()) {
                return safePromptText;
            }
            return "Selected map and node identifiers:\n"
                + json
                + "\n\n"
                + safePromptText;
        } catch (Exception error) {
            throw new IllegalStateException("Failed to compose AI prompt request.", error);
        }
    }

    String compose(String promptText, ToolAvailabilityLevel toolAvailability) {
        return compose(promptText, toolAvailability, null);
    }

    private static Supplier<SelectionIdentifiersResponse> createSelectionIdentifiersSupplier(
            AvailableMaps availableMaps, TextController textController) {
        final AvailableMaps safeAvailableMaps = Objects.requireNonNull(availableMaps, "availableMaps");
        final SelectionIdentifiersBuilder selectionIdentifiersBuilder =
            new SelectionIdentifiersBuilder(new NodeTextPreviewFormatter(
                Objects.requireNonNull(textController, "textController")));
        return new Supplier<SelectionIdentifiersResponse>() {
            @Override
            public SelectionIdentifiersResponse get() {
                String mapIdentifier = safeAvailableMaps.getCurrentMapIdentifier() == null
                    ? null
                    : safeAvailableMaps.getCurrentMapIdentifier().toString();
                MapModel mapModel = safeAvailableMaps.getCurrentMapModel();
                IMapSelection selection = Controller.getCurrentController() == null
                    ? null
                    : Controller.getCurrentController().getSelection();
                return selectionIdentifiersBuilder.buildSelectionIdentifiersResponse(
                    mapIdentifier, mapModel, selection, null);
            }
        };
    }
}
