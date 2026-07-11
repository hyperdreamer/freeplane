package org.freeplane.plugin.ai.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AIModelDiscoveryResult {
    private final boolean successful;
    private final List<DiscoveredAIModel> models;

    private AIModelDiscoveryResult(boolean successful, List<DiscoveredAIModel> models) {
        this.successful = successful;
        this.models = Collections.unmodifiableList(new ArrayList<>(models));
    }

    public static AIModelDiscoveryResult success(List<DiscoveredAIModel> models) {
        return new AIModelDiscoveryResult(true, models);
    }

    public static AIModelDiscoveryResult failed() {
        return new AIModelDiscoveryResult(false, Collections.<DiscoveredAIModel>emptyList());
    }

    public boolean isSuccessful() {
        return successful;
    }

    public List<DiscoveredAIModel> getModels() {
        return models;
    }
}
