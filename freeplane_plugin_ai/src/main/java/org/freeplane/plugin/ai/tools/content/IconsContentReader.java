package org.freeplane.plugin.ai.tools.content;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.freeplane.features.icon.IconController;
import org.freeplane.features.icon.NamedIcon;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.styles.LogicalStyleController.StyleOption;

public class IconsContentReader {
    private final IconController iconController;
    private final IconDescriptionResolver iconDescriptionResolver;

    public IconsContentReader(IconDescriptionResolver iconDescriptionResolver, IconController iconController) {
        this.iconDescriptionResolver = Objects.requireNonNull(iconDescriptionResolver, "iconDescriptionResolver");
        this.iconController = Objects.requireNonNull(iconController, "iconController");
    }

    public IconsContent readIconsContent(NodeModel nodeModel, NodeContentPreset preset) {
        if (nodeModel == null || preset == NodeContentPreset.BRIEF) {
            return null;
        }
        return buildIconsContent(nodeModel);
    }

    public IconsContent readIconsContent(NodeModel nodeModel, IconsContentRequest request) {
        if (nodeModel == null || request == null || !request.includesIcons()) {
            return null;
        }
        return buildIconsContent(nodeModel);
    }

    public List<String> collectSearchTerms(NodeModel nodeModel, IconsContentRequest request) {
        if (nodeModel == null || request == null || !request.includesIcons()) {
            return Collections.emptyList();
        }
        List<NamedIcon> icons = new ArrayList<>(iconController.getIcons(nodeModel, StyleOption.FOR_UNSELECTED_NODE));
        if (icons.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> terms = new ArrayList<>(icons.size() * 2);
        for (NamedIcon icon : icons) {
            String description = iconDescriptionResolver.resolveDescription(icon);
            if (description != null && !description.isEmpty()) {
                terms.add(description);
            }
        }
        return terms;
    }

    public boolean matches(NodeModel nodeModel, IconsContentRequest request, NodeContentValueMatcher valueMatcher) {
        if (nodeModel == null || request == null || !request.includesIcons() || valueMatcher == null) {
            return false;
        }
        List<String> terms = collectSearchTerms(nodeModel, request);
        for (String term : terms) {
            if (valueMatcher.matchesValue(term)) {
                return true;
            }
        }
        return false;
    }

    private IconsContent buildIconsContent(NodeModel nodeModel) {
        List<NamedIcon> icons = new ArrayList<>(iconController.getIcons(nodeModel, StyleOption.FOR_UNSELECTED_NODE));
        if (icons.isEmpty()) {
            return null;
        }
        List<String> descriptions = new ArrayList<>(icons.size());
        for (NamedIcon icon : icons) {
            descriptions.add(iconDescriptionResolver.resolveDescription(icon));
        }
        return new IconsContent(descriptions);
    }
}
