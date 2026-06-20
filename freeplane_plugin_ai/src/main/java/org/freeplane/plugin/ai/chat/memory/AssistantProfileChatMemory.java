package org.freeplane.plugin.ai.chat.memory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.memory.ChatMemoryService;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import org.freeplane.plugin.ai.chat.history.ChatTranscriptEntry;
import org.freeplane.plugin.ai.tools.MessageBuilder;
import org.freeplane.plugin.ai.tools.utilities.ToolCaller;

import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

public class AssistantProfileChatMemory implements ChatMemory {

    private final Object id;
    private final Function<Object, Integer> maxTokensProvider;
    private final ChatTokenEstimator tokenEstimator;
    private final ChatMemoryViewState viewState;
    private final ChatTurnTracker turnTracker;
    private final ToolWindowSelector toolWindowSelector;
    private final ChatMessageFilter chatMessageFilter;
    private final PanelProjector panelProjector;
    private final ModelProjector modelProjector;
    private final TranscriptProjector transcriptProjector;
    private ProfileInstructionFactory profileInstructionFactory;
    private GeneralSystemMessage generalSystemMessage;
    private final List<ChatMessage> conversationMessages = new ArrayList<>();
    private final List<Integer> turnEndIndexes = new ArrayList<>();
    private ChatMessageFilter.FilteringComputation cachedFilteringComputation;
    private boolean derivedFilteringDirty = true;

    private AssistantProfileChatMemory(Builder builder) {
        this.id = ensureNotNull(builder.id, "id");
        this.maxTokensProvider = ensureNotNull(builder.maxTokensProvider, "maxTokensProvider");
        this.tokenEstimator = new ChatTokenEstimator(builder.tokenEstimatorModelNameProvider);
        this.viewState = new ChatMemoryViewState();
        this.turnTracker = new ChatTurnTracker();
        this.toolWindowSelector = new ToolWindowSelector();
        this.chatMessageFilter = new ChatMessageFilter(turnTracker, toolWindowSelector, this::estimateTokenCount);
        this.panelProjector = new PanelProjector();
        this.modelProjector = new ModelProjector();
        this.transcriptProjector = new TranscriptProjector();
        this.profileInstructionFactory = resolveProfileInstructionFactory(builder.profileInstructionFactory);
        ensureGreaterThanZero(this.maxTokensProvider.apply(this.id), "maxTokens");
    }

    @Override
    public Object id() {
        return id;
    }

    @Override
    public void add(ChatMessage message) {
        if (message == null) {
            return;
        }
        discardRedoBranchIfNeeded();
        if (message instanceof TranscriptHiddenSystemMessage) {
            viewState.restoredTranscriptSession(true);
            invalidateDerivedFiltering();
            return;
        }
        if (message instanceof RemovedForSpaceSystemMessage) {
            markContextWindowStart();
            return;
        }
        if (message instanceof AssistantProfileSwitchMessage) {
            addConversationMessage(message);
            addConversationMessage(new InstructionAckMessage());
            rebuildTurnBoundaries();
            invalidateDerivedFiltering();
            return;
        }
        if (message instanceof InstructionAckMessage) {
            return;
        }
        if (message instanceof SystemMessage) {
            setGeneralSystemMessage(toGeneralSystemMessage((SystemMessage) message));
            invalidateDerivedFiltering();
            return;
        }
        if (message instanceof UserMessage
            && !(message instanceof AutomaticCodeStatusMessage)
            && AutomaticCodeStatusMessage.isAutomaticCodeStatusText(((UserMessage) message).singleText())) {
            message = new AutomaticCodeStatusMessage(((UserMessage) message).singleText());
        }
        addConversationMessage(message);
        rebuildTurnBoundaries();
        invalidateDerivedFiltering();
    }

    @Override
    public List<ChatMessage> messages() {
        return modelProjector.buildMessages(
            generalSystemMessage,
            filteredChatMessages(),
            buildLatestProfileInstruction(activeConversationEndIndex()));
    }

    @Override
    public void clear() {
        generalSystemMessage = null;
        conversationMessages.clear();
        viewState.clear();
        turnEndIndexes.clear();
        cachedFilteringComputation = null;
        derivedFilteringDirty = true;
    }

    public boolean canUndo() {
        return viewState.activeConversationTurnCount() > firstActiveTurnIndex();
    }

    public int conversationMessageCount() {
        return conversationMessages.size();
    }

    public void truncateConversationMessagesTo(int size) {
        int targetSize = Math.max(0, Math.min(size, conversationMessages.size()));
        while (conversationMessages.size() > targetSize) {
            removeConversationMessage(conversationMessages.size() - 1);
        }
        viewState.chatWindowStartIndex(Math.min(viewState.chatWindowStartIndex(), targetSize));
        rebuildTurnBoundaries();
        invalidateDerivedFiltering();
    }

    public boolean canRedo() {
        return turnTracker.canRedo(turnEndIndexes, viewState);
    }

    public String undo() {
        if (!canUndo()) {
            return "";
        }
        int turnIndex = viewState.activeConversationTurnCount() - 1;
        int from = turnIndex == 0 ? 0 : turnEndIndexes.get(turnIndex - 1);
        from = Math.max(from, viewState.chatWindowStartIndex());
        int to = turnEndIndexes.get(turnIndex);
        viewState.activeConversationTurnCount(turnIndex);
        rebalanceActiveWindowForCurrentTurnRange();
        invalidateDerivedFiltering();
        return findUserMessageInRange(from, to);
    }

    public void redo() {
        if (!canRedo()) {
            return;
        }
        viewState.activeConversationTurnCount(viewState.activeConversationTurnCount() + 1);
        rebalanceActiveWindowForCurrentTurnRange();
        invalidateDerivedFiltering();
    }

    public void initializeUndoRedoFromMessages() {
        rebuildTurnBoundaries();
        invalidateDerivedFiltering();
    }

    public void expandWindowAfterTranscriptRestoreIfUnderutilized() {
        rebuildTurnBoundaries();
        int endIndex = activeConversationEndIndex();
        if (endIndex <= 0) {
            return;
        }
        int maxTokens = maxTokensProvider.apply(id);
        ensureGreaterThanZero(maxTokens, "maxTokens");
        int startIndex = Math.min(viewState.chatWindowStartIndex(), endIndex);
        long activeTokens = estimateTotalTokensForRange(startIndex, endIndex);
        if (activeTokens >= maxTokens) {
            return;
        }
        int selectedStart = startIndex;
        while (true) {
            int previousTurnStart = turnTracker.previousTurnStartFor(turnEndIndexes, selectedStart);
            if (previousTurnStart < 0) {
                break;
            }
            long expandedTokens = estimateTotalTokensForRange(previousTurnStart, endIndex);
            if (expandedTokens > maxTokens) {
                break;
            }
            selectedStart = previousTurnStart;
            if (expandedTokens >= maxTokens) {
                break;
            }
        }
        viewState.chatWindowStartIndex(selectedStart);
        invalidateDerivedFiltering();
    }

    public void refreshCompactionForCurrentMaxTokens() {
        rebuildTurnBoundaries();
        rebalanceActiveWindowForCurrentTurnRange();
        invalidateDerivedFiltering();
    }

    public boolean evictOldestTurn() {
        rebuildTurnBoundaries();
        if (!canAdvanceWindowByTurnWithMinimumRetention(1)) {
            return false;
        }
        if (!advanceWindowByOneTurn()) {
            return false;
        }
        invalidateDerivedFiltering();
        return true;
    }

    public List<ChatTranscriptEntry> transcriptEntriesForPersistence() {
        return transcriptProjector.buildTranscriptEntries(generalSystemMessage, filteredChatMessages());
    }

    public String capturedSystemMessage() {
        return generalSystemMessage == null ? null : generalSystemMessage.text();
    }

    public AssistantProfileSwitchMessage latestProfileSwitchMessage() {
        int index = findLatestProfileSwitchIndex(activeConversationEndIndex());
        if (index < 0 || index >= conversationMessages.size()) {
            return null;
        }
        ChatMessage message = conversationMessages.get(index);
        return message instanceof AssistantProfileSwitchMessage
            ? (AssistantProfileSwitchMessage) message
            : null;
    }

    public List<ChatMemoryRenderEntry> activeConversationRenderEntries() {
        return buildRenderEntries();
    }

    public List<ChatMemoryRenderEntry> panelConversationRenderEntries() {
        return buildRenderEntries();
    }

    FilteredChatMessages filteredChatMessages() {
        return filteringComputation().filteredMessages();
    }

    public void markContextWindowStart() {
        viewState.chatWindowStartIndex(Math.max(viewState.chatWindowStartIndex(), conversationMessages.size()));
        invalidateDerivedFiltering();
    }

    public void addToolCallSummary(String summaryText, ToolCaller toolCaller) {
        if (summaryText == null || summaryText.trim().isEmpty()) {
            return;
        }
        conversationMessages.add(new ToolCallSummaryMessage(summaryText, toolCaller));
        rebuildTurnBoundaries();
        invalidateDerivedFiltering();
    }

    ChatUsageTotals estimateTokenUsageForActiveWindow() {
        return estimateTokenUsageForFilteredMessages(filteredChatMessages());
    }

    ChatUsageTotals estimateTokenUsageForFullConversation() {
        return estimateTokenUsageForRange(0, conversationMessages.size());
    }

    public boolean onResponseTokenUsage(TokenUsage ignoredUsage) {
        return evictIfNeededAfterResponse();
    }

    public void setProfileInstructionFactory(ProfileInstructionFactory profileInstructionFactory) {
        this.profileInstructionFactory = resolveProfileInstructionFactory(profileInstructionFactory);
    }

    private List<ChatMemoryRenderEntry> buildRenderEntries() {
        return panelProjector.buildRenderEntries(generalSystemMessage, filteredChatMessages());
    }

    private boolean evictIfNeededAfterResponse() {
        rebuildTurnBoundaries();
        int maxTokens = maxTokensProvider.apply(id);
        ensureGreaterThanZero(maxTokens, "maxTokens");
        ChatMessageFilter.FilteringComputation previousFiltering = cachedFilteringComputation;
        ChatMessageFilter.FilteringComputation currentFiltering = filteringComputation();
        if (currentFiltering.currentWindowTokenCount() < maxTokens) {
            return false;
        }
        int resetTargetTokens = maxTokens / 4;
        int minimumTurnBlocksToKeep = minimumTurnBlocksToKeep(maxTokens);
        int previousStart = viewState.chatWindowStartIndex();
        boolean changed = previousFiltering == null
            ? !currentFiltering.hiddenGroups().isEmpty()
            : !previousFiltering.hiddenGroups().equals(currentFiltering.hiddenGroups());
        while (currentFiltering.filteredTokenCount() > resetTargetTokens) {
            if (!canAdvanceWindowByTurnWithMinimumRetention(minimumTurnBlocksToKeep)) {
                break;
            }
            if (!advanceWindowByOneTurn()) {
                break;
            }
            changed = true;
            currentFiltering = filteringComputation();
        }
        return changed || previousStart != viewState.chatWindowStartIndex();
    }

    private int activeConversationEndIndex() {
        return turnTracker.activeConversationEndIndex(turnEndIndexes, viewState, conversationMessages.size());
    }

    private int conversationEndIndexForCurrentTurnRange() {
        return turnTracker.conversationEndIndexForCurrentTurnRange(turnEndIndexes, viewState, conversationMessages.size());
    }

    private GeneralSystemMessage toGeneralSystemMessage(SystemMessage message) {
        if (message instanceof GeneralSystemMessage) {
            return (GeneralSystemMessage) message;
        }
        return new GeneralSystemMessage(message.text());
    }

    private void rebuildTurnBoundaries() {
        turnEndIndexes.clear();
        turnEndIndexes.addAll(turnTracker.rebuildTurnEndIndexes(conversationMessages));
        viewState.activeConversationTurnCount(turnEndIndexes.size());
        int endIndex = activeConversationEndIndex();
        if (viewState.chatWindowStartIndex() > endIndex) {
            viewState.chatWindowStartIndex(endIndex);
        }
    }

    private void discardRedoBranchIfNeeded() {
        if (!canRedo()) {
            return;
        }
        int keepSize = viewState.activeConversationTurnCount() == 0
            ? 0
            : turnEndIndexes.get(viewState.activeConversationTurnCount() - 1);
        while (conversationMessages.size() > keepSize) {
            removeConversationMessage(conversationMessages.size() - 1);
        }
        while (turnEndIndexes.size() > viewState.activeConversationTurnCount()) {
            turnEndIndexes.remove(turnEndIndexes.size() - 1);
        }
        viewState.chatWindowStartIndex(Math.min(viewState.chatWindowStartIndex(), keepSize));
        invalidateDerivedFiltering();
    }

    private int firstActiveTurnIndex() {
        return turnTracker.firstActiveTurnIndex(
            turnEndIndexes,
            viewState.chatWindowStartIndex(),
            conversationMessages.size());
    }

    private String findUserMessageInRange(int from, int to) {
        int safeFrom = Math.max(0, from);
        int safeTo = Math.min(to, conversationMessages.size());
        for (int index = safeTo - 1; index >= safeFrom; index--) {
            ChatMessage message = conversationMessages.get(index);
            if (message instanceof UserMessage) {
                String text = ((UserMessage) message).singleText();
                if (text != null && !text.startsWith(MessageBuilder.CONTROL_INSTRUCTION_PREFIX)) {
                    return text;
                }
            }
        }
        return "";
    }

    private void setGeneralSystemMessage(GeneralSystemMessage message) {
        generalSystemMessage = message;
    }

    private void addConversationMessage(ChatMessage message) {
        conversationMessages.add(message);
    }

    private ChatMessage removeConversationMessage(int index) {
        return conversationMessages.remove(index);
    }

    private boolean advanceWindowByOneTurn() {
        rebuildTurnBoundaries();
        int endIndex = activeConversationEndIndex();
        int startIndex = Math.min(viewState.chatWindowStartIndex(), endIndex);
        int nextTurnEnd = turnTracker.findNextTurnEndAfter(turnEndIndexes, startIndex);
        if (nextTurnEnd <= startIndex) {
            return false;
        }
        viewState.chatWindowStartIndex(nextTurnEnd);
        invalidateDerivedFiltering();
        return true;
    }

    private void rebalanceActiveWindowForCurrentTurnRange() {
        int maxTokens = maxTokensProvider.apply(id);
        ensureGreaterThanZero(maxTokens, "maxTokens");
        int endIndex = conversationEndIndexForCurrentTurnRange();
        if (endIndex <= 0 || viewState.activeConversationTurnCount() <= 0) {
            viewState.chatWindowStartIndex(0);
            return;
        }
        int selectedStart = turnTracker.turnStartIndex(turnEndIndexes, viewState.activeConversationTurnCount() - 1);
        for (int turnIndex = viewState.activeConversationTurnCount() - 2; turnIndex >= 0; turnIndex--) {
            int candidateStart = turnTracker.turnStartIndex(turnEndIndexes, turnIndex);
            if (estimateTotalTokensForRange(candidateStart, endIndex) <= maxTokens) {
                selectedStart = candidateStart;
                continue;
            }
            break;
        }
        viewState.chatWindowStartIndex(selectedStart);
    }

    private boolean canAdvanceWindowByTurnWithMinimumRetention(int minimumTurnBlocksToKeep) {
        return activeTurnRanges().size() > minimumTurnBlocksToKeep;
    }

    private UserMessage buildLatestProfileInstruction(int endIndex) {
        return buildProfileInstructionForIndex(findLatestProfileSwitchIndex(endIndex));
    }

    private int findLatestProfileSwitchIndex(int endIndex) {
        for (int index = Math.min(endIndex, conversationMessages.size()) - 1; index >= 0; index--) {
            if (conversationMessages.get(index) instanceof AssistantProfileSwitchMessage) {
                return index;
            }
        }
        return -1;
    }

    private UserMessage buildProfileInstructionForIndex(int messageIndex) {
        if (messageIndex < 0 || messageIndex >= conversationMessages.size()) {
            return null;
        }
        ChatMessage message = conversationMessages.get(messageIndex);
        if (!(message instanceof AssistantProfileSwitchMessage)) {
            return null;
        }
        AssistantProfileInstructionMessage profileInstruction =
            profileInstructionFactory.buildFor((AssistantProfileSwitchMessage) message);
        if (profileInstruction == null) {
            return null;
        }
        return MessageBuilder.buildSystemInstructionUserMessage(profileInstruction.singleText());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static AssistantProfileChatMemory withMaxTokens(int maxTokens) {
        return builder().maxTokens(maxTokens).build();
    }

    public static class Builder {

        private Object id = ChatMemoryService.DEFAULT;
        private Function<Object, Integer> maxTokensProvider;
        private Supplier<String> tokenEstimatorModelNameProvider = () -> null;
        private ProfileInstructionFactory profileInstructionFactory;

        public Builder id(Object id) {
            this.id = id;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokensProvider = ignored -> maxTokens;
            return this;
        }

        public Builder dynamicMaxTokens(Function<Object, Integer> maxTokensProvider) {
            this.maxTokensProvider = maxTokensProvider;
            return this;
        }

        public Builder tokenEstimatorModelNameProvider(Supplier<String> tokenEstimatorModelNameProvider) {
            this.tokenEstimatorModelNameProvider = tokenEstimatorModelNameProvider;
            return this;
        }

        public Builder profileInstructionFactory(ProfileInstructionFactory profileInstructionFactory) {
            this.profileInstructionFactory = profileInstructionFactory;
            return this;
        }

        public AssistantProfileChatMemory build() {
            return new AssistantProfileChatMemory(this);
        }
    }

    public interface ProfileInstructionFactory {
        AssistantProfileInstructionMessage buildFor(AssistantProfileSwitchMessage profileSwitchMessage);
    }

    private ProfileInstructionFactory resolveProfileInstructionFactory(ProfileInstructionFactory profileInstructionFactory) {
        if (profileInstructionFactory != null) {
            return profileInstructionFactory;
        }
        return profileSwitchMessage -> {
            if (profileSwitchMessage == null) {
                return null;
            }
            return new AssistantProfileInstructionMessage(
                profileSwitchMessage.getProfileId(),
                profileSwitchMessage.getProfileName(),
                profileSwitchMessage.getProfileMessage());
        };
    }

    private ChatUsageTotals estimateTokenUsageForRange(int startIndex, int endIndex) {
        long inputTokens = 0L;
        long outputTokens = 0L;
        int safeStart = Math.max(0, startIndex);
        int safeEnd = Math.min(endIndex, conversationMessages.size());
        for (int index = safeStart; index < safeEnd; index++) {
            ChatMessage message = conversationMessages.get(index);
            if (!isCompactionCountedMessage(message)) {
                continue;
            }
            int tokenCount = tokenEstimator.estimateTokenCountInMessage(message);
            if (message instanceof AiMessage) {
                outputTokens += tokenCount;
            }
            else {
                inputTokens += tokenCount;
            }
        }
        return ChatUsageTotals.estimated(inputTokens, outputTokens);
    }

    private ChatUsageTotals estimateTokenUsageForFilteredMessages(FilteredChatMessages filteredMessages) {
        if (filteredMessages == null) {
            return ChatUsageTotals.estimated(0L, 0L);
        }
        long inputTokens = 0L;
        long outputTokens = 0L;
        for (ChatMessage message : filteredMessages.messages()) {
            if (!isCompactionCountedMessage(message)) {
                continue;
            }
            int tokenCount = tokenEstimator.estimateTokenCountInMessage(message);
            if (message instanceof AiMessage) {
                outputTokens += tokenCount;
            }
            else {
                inputTokens += tokenCount;
            }
        }
        return ChatUsageTotals.estimated(inputTokens, outputTokens);
    }

    private int estimateTokenCount(ChatMessage message) {
        return tokenEstimator.estimateTokenCountInMessage(message);
    }

    private long estimateTotalTokensForRange(int startIndex, int endIndex) {
        ChatUsageTotals totals = estimateTokenUsageForRange(startIndex, endIndex);
        return totals.getInputTokenCount() + totals.getOutputTokenCount();
    }

    private int minimumTurnBlocksToKeep(int maxTokens) {
        List<ActiveTurnRange> ranges = activeTurnRanges();
        if (ranges.size() <= 1) {
            return 1;
        }
        ActiveTurnRange secondLast = ranges.get(ranges.size() - 2);
        ActiveTurnRange last = ranges.get(ranges.size() - 1);
        long twoTurnTokenCount = estimateTotalTokensForRange(secondLast.startIndex(), last.endExclusive());
        return twoTurnTokenCount <= maxTokens ? 2 : 1;
    }

    private List<ActiveTurnRange> activeTurnRanges() {
        int endIndex = activeConversationEndIndex();
        return turnTracker.activeTurnRanges(turnEndIndexes, viewState, endIndex);
    }

    private boolean isCompactionCountedMessage(ChatMessage message) {
        if (message == null) {
            return false;
        }
        if (message instanceof AssistantProfileSwitchMessage
            || message instanceof InstructionAckMessage
            || message instanceof TranscriptHiddenSystemMessage
            || message instanceof RemovedForSpaceSystemMessage
            || message instanceof ToolCallSummaryMessage
            || message instanceof GeneralSystemMessage) {
            return false;
        }
        if (message instanceof SystemMessage) {
            return false;
        }
        if (message instanceof UserMessage) {
            String text = ((UserMessage) message).singleText();
            return text == null || !text.startsWith(MessageBuilder.CONTROL_INSTRUCTION_PREFIX);
        }
        return message instanceof AiMessage || message instanceof ToolExecutionResultMessage;
    }

    private ChatMessageFilter.FilteringComputation filteringComputation() {
        if (!derivedFilteringDirty && cachedFilteringComputation != null) {
            return cachedFilteringComputation;
        }
        cachedFilteringComputation = chatMessageFilter.computeFiltering(
            conversationMessages,
            viewState,
            turnEndIndexes,
            maxTokensProvider.apply(id));
        derivedFilteringDirty = false;
        return cachedFilteringComputation;
    }

    private void invalidateDerivedFiltering() {
        derivedFilteringDirty = true;
    }

    private static class ChatTokenEstimator {
        private static final String FALLBACK_MODEL_NAME = "gpt-4o-mini";

        private final Supplier<String> modelNameProvider;
        private OpenAiTokenCountEstimator estimator;
        private String activeModelName;

        private ChatTokenEstimator(Supplier<String> modelNameProvider) {
            this.modelNameProvider = modelNameProvider == null ? () -> null : modelNameProvider;
        }

        int estimateTokenCountInMessage(ChatMessage message) {
            OpenAiTokenCountEstimator activeEstimator = estimator();
            try {
                return activeEstimator.estimateTokenCountInMessage(message);
            }
            catch (RuntimeException error) {
                return 0;
            }
        }

        private OpenAiTokenCountEstimator estimator() {
            String modelName = normalizeModelName(modelNameProvider.get());
            if (estimator == null || !modelName.equals(activeModelName)) {
                estimator = buildEstimator(modelName);
                activeModelName = modelName;
            }
            return estimator;
        }

        private OpenAiTokenCountEstimator buildEstimator(String modelName) {
            try {
                return new OpenAiTokenCountEstimator(modelName);
            }
            catch (IllegalArgumentException error) {
                return new OpenAiTokenCountEstimator(FALLBACK_MODEL_NAME);
            }
        }

        private String normalizeModelName(String modelName) {
            if (modelName == null || modelName.trim().isEmpty()) {
                return FALLBACK_MODEL_NAME;
            }
            String normalized = modelName.trim();
            int slashIndex = normalized.lastIndexOf('/');
            if (slashIndex >= 0 && slashIndex < normalized.length() - 1) {
                normalized = normalized.substring(slashIndex + 1);
            }
            return normalized;
        }
    }
}
