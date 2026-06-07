package org.freeplane.plugin.ai.tools.create;

import java.util.Objects;
import org.freeplane.core.resources.ResourceController;

public class CreateNodesPreferences {
    static final String AI_UNFOLDS_PARENTS_ON_CREATE_PROPERTY = "ai_unfolds_parents_on_create";

    private final ResourceController resourceController;

    public CreateNodesPreferences() {
        this(ResourceController.getResourceController());
    }

    CreateNodesPreferences(ResourceController resourceController) {
        this.resourceController = Objects.requireNonNull(resourceController, "resourceController");
    }

    public boolean unfoldsParentsOnCreate() {
        return resourceController.getBooleanProperty(AI_UNFOLDS_PARENTS_ON_CREATE_PROPERTY);
    }
}
