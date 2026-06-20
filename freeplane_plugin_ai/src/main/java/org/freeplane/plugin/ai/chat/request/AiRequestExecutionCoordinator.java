package org.freeplane.plugin.ai.chat.request;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import org.freeplane.api.ai.AiRequestHandle;
import org.freeplane.api.ai.AiRequestMode;
import org.freeplane.api.ai.AiRequestResult;
import org.freeplane.api.ai.AiRequestStatus;
import org.freeplane.plugin.ai.chat.ui.AIChatPanel;

public class AiRequestExecutionCoordinator {
    private static final ExecutorService ADD_TO_CHAT_EXECUTOR = Executors.newSingleThreadExecutor(
        new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "freeplane-ai-add-to-chat-dispatch");
                thread.setDaemon(true);
                return thread;
            }
        });

    private final AIChatPanel aiChatPanel;
    private final AddToChatDispatchJobFactory addToChatDispatchJobFactory;
    private final AiRequestTimeoutControllerFactory timeoutControllerFactory;
    private final ExecutorService addToChatExecutor;

    public AiRequestExecutionCoordinator(AIChatPanel aiChatPanel,
                                  AddToChatDispatchJobFactory addToChatDispatchJobFactory,
                                  AiRequestTimeoutControllerFactory timeoutControllerFactory) {
        this(aiChatPanel, addToChatDispatchJobFactory, timeoutControllerFactory, ADD_TO_CHAT_EXECUTOR);
    }

    public AiRequestExecutionCoordinator(AIChatPanel aiChatPanel,
                                  AddToChatDispatchJobFactory addToChatDispatchJobFactory,
                                  AiRequestTimeoutControllerFactory timeoutControllerFactory,
                                  ExecutorService addToChatExecutor) {
        this.aiChatPanel = Objects.requireNonNull(aiChatPanel, "aiChatPanel");
        this.addToChatDispatchJobFactory = Objects.requireNonNull(addToChatDispatchJobFactory,
            "addToChatDispatchJobFactory");
        this.timeoutControllerFactory = Objects.requireNonNull(timeoutControllerFactory,
            "timeoutControllerFactory");
        this.addToChatExecutor = Objects.requireNonNull(addToChatExecutor, "addToChatExecutor");
    }

    public AiRequestHandle askAi(ResolvedAiRequest request, AiRequestHandleImpl handle) {
        if (handle.isDone()) {
            return handle;
        }
        if (handle.isCancelled()) {
            aiChatPanel.completeCancelledRequest(handle);
            return handle;
        }
        AiRequestMode mode = request.getMode();
        switch (mode) {
            case SHOW_IN_NEW_CHAT:
                aiChatPanel.startShownAiRequest(request, handle, timeoutControllerFactory.create(request, handle));
                return handle;
            case ADD_TO_CHAT:
                submitAddToChat(request, handle);
                return handle;
            case HIDDEN_WITH_CANCEL_DIALOG:
                aiChatPanel.startHiddenAiRequest(request, handle, true,
                    timeoutControllerFactory.create(request, handle));
                return handle;
            case HIDDEN:
            default:
                aiChatPanel.startHiddenAiRequest(request, handle, false,
                    timeoutControllerFactory.create(request, handle));
                return handle;
        }
    }

    private void submitAddToChat(ResolvedAiRequest request, AiRequestHandleImpl handle) {
        AddToChatDispatchJob job = addToChatDispatchJobFactory.create(
            request,
            handle,
            timeoutControllerFactory.create(request, handle));
        handle.setCancelAction(job::cancel);
        if (handle.isDone()) {
            return;
        }
        try {
            addToChatExecutor.submit(job);
        } catch (RejectedExecutionException rejectedExecutionException) {
            handle.complete(new AiRequestResult(
                AiRequestStatus.FAILED,
                null,
                AiRequestStatusMapper.detailMessage(rejectedExecutionException)));
        }
    }
}
