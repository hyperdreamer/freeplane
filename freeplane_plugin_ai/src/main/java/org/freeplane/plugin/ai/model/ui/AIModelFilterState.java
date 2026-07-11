package org.freeplane.plugin.ai.model.ui;

public class AIModelFilterState {
    private static final AIModelFilterState SHARED_INSTANCE = new AIModelFilterState();

    private String filterText = "";

    public static AIModelFilterState shared() {
        return SHARED_INSTANCE;
    }

    public String getFilterText() {
        return filterText;
    }

    public void setFilterText(String filterText) {
        this.filterText = filterText == null ? "" : filterText;
    }
}
