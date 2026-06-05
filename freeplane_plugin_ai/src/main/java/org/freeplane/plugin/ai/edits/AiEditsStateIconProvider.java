package org.freeplane.plugin.ai.edits;

import java.util.Objects;
import java.util.function.Supplier;
import org.freeplane.features.icon.IStateIconProvider;
import org.freeplane.features.icon.UIIcon;
import org.freeplane.features.icon.factory.IconStoreFactory;
import org.freeplane.features.map.NodeModel;

public class AiEditsStateIconProvider implements IStateIconProvider {
    private final AiEditsSettings aiEditsSettings;
    private final AiEditsStateIconDecision stateIconDecision;
    private final Supplier<UIIcon> iconSupplier;

    public AiEditsStateIconProvider(AiEditsSettings aiEditsSettings) {
        this(aiEditsSettings, new AiEditsStateIconDecision(), () -> IconStoreFactory.ICON_STORE.getUIIcon("ai.svg"));
    }

    AiEditsStateIconProvider(AiEditsSettings aiEditsSettings, AiEditsStateIconDecision stateIconDecision,
                             Supplier<UIIcon> iconSupplier) {
        this.aiEditsSettings = Objects.requireNonNull(aiEditsSettings, "aiEditsSettings");
        this.stateIconDecision = Objects.requireNonNull(stateIconDecision, "stateIconDecision");
        this.iconSupplier = Objects.requireNonNull(iconSupplier, "iconSupplier");
    }

    @Override
    public UIIcon getStateIcon(NodeModel node) {
        boolean isStateIconVisible = aiEditsSettings.isStateIconVisible();
        boolean hasAiEditsMarker = node.getExtension(AIEdits.class) != null;
        if (!stateIconDecision.shouldShowIcon(isStateIconVisible, hasAiEditsMarker)) {
            return null;
        }
        return iconSupplier.get();
    }

    @Override
    public boolean mustIncludeInIconRegistry() {
        return true;
    }
}
