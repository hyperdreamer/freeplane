package org.freeplane.plugin.ai.tools.content;

import java.util.List;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.styles.StyleNameMappingHelper;

public class NodeStyleContentReader {
    public List<String> readActiveStyles(NodeModel nodeModel) {
        if (nodeModel == null) {
            return null;
        }
        List<String> activeStyles = StyleNameMappingHelper.readActiveStyleNames(nodeModel);
        return activeStyles.isEmpty() ? null : activeStyles;
    }

    public String readMainStyle(NodeModel nodeModel) {
        if (nodeModel == null) {
            return null;
        }
        return StyleNameMappingHelper.readMainStyleName(nodeModel);
    }
}
