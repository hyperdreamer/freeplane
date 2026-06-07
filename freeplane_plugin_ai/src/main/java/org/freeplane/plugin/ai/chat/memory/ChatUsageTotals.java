package org.freeplane.plugin.ai.chat.memory;

public class ChatUsageTotals {
    private final long inputTokenCount;
    private final long outputTokenCount;
    private final boolean visible;
    private final String label;
    private final String inputLabel;
    private final String outputLabel;

    ChatUsageTotals(long inputTokenCount, long outputTokenCount) {
        this(inputTokenCount, outputTokenCount, true, null, null, null);
    }

    private ChatUsageTotals(long inputTokenCount, long outputTokenCount, boolean visible, String label,
                            String inputLabel, String outputLabel) {
        this.inputTokenCount = inputTokenCount;
        this.outputTokenCount = outputTokenCount;
        this.visible = visible;
        this.label = label;
        this.inputLabel = inputLabel;
        this.outputLabel = outputLabel;
    }

    public long getInputTokenCount() {
        return inputTokenCount;
    }

    public long getOutputTokenCount() {
        return outputTokenCount;
    }

    public boolean isVisible() {
        return visible;
    }

    public String getLabel() {
        return label;
    }

    public String formatStatusLine() {
        if (!visible) {
            return "";
        }
        String prefix = label == null || label.trim().isEmpty()
            ? ""
            : label.trim() + ": ";
        String safeInputLabel = inputLabel == null || inputLabel.trim().isEmpty()
            ? "input"
            : inputLabel.trim();
        String safeOutputLabel = outputLabel == null || outputLabel.trim().isEmpty()
            ? "output"
            : outputLabel.trim();
        return prefix + safeInputLabel + " " + inputTokenCount + ", " + safeOutputLabel + " " + outputTokenCount;
    }

    ChatUsageTotals withLabel(String label) {
        if (!visible) {
            return this;
        }
        return new ChatUsageTotals(inputTokenCount, outputTokenCount, true, label, inputLabel, outputLabel);
    }

    ChatUsageTotals withInputOutputLabels(String inputLabel, String outputLabel) {
        if (!visible) {
            return this;
        }
        return new ChatUsageTotals(inputTokenCount, outputTokenCount, true, label, inputLabel, outputLabel);
    }

    static ChatUsageTotals hidden() {
        return new ChatUsageTotals(0L, 0L, false, null, null, null);
    }

    static ChatUsageTotals estimated(long inputTokenCount, long outputTokenCount) {
        return new ChatUsageTotals(inputTokenCount, outputTokenCount, true, null, null, null);
    }
}
