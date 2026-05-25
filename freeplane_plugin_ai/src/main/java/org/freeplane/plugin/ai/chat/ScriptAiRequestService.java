package org.freeplane.plugin.ai.chat;

import java.util.Objects;
import javax.swing.SwingUtilities;
import org.freeplane.api.ai.AiRequest;
import org.freeplane.api.ai.AiRequestCallback;
import org.freeplane.api.ai.AiRequestHandle;
import org.freeplane.api.ai.AiRequestService;
import org.freeplane.features.mode.Controller;

public class ScriptAiRequestService implements AiRequestService {
    interface UiDispatcher {
        void dispatch(Runnable runnable);
    }

    interface RequestStarter {
        void start(AiRequest request, AiRequestHandleImpl handle);
    }

    private final UiDispatcher uiDispatcher;
    private final RequestStarter requestStarter;

    public ScriptAiRequestService(AIChatPanel aiChatPanel) {
        this(new RequestStarter() {
            @Override
            public void start(AiRequest request, AiRequestHandleImpl handle) {
                aiChatPanel.askAi(request, handle);
            }
        }, ScriptAiRequestService::dispatchOnUiThread);
    }

    ScriptAiRequestService(RequestStarter requestStarter,
                           UiDispatcher uiDispatcher) {
        this.requestStarter = Objects.requireNonNull(requestStarter, "requestStarter");
        this.uiDispatcher = Objects.requireNonNull(uiDispatcher, "uiDispatcher");
    }

    @Override
    public AiRequestHandle askAi(AiRequest request, AiRequestCallback callback) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(callback, "callback");
        AiRequestHandleImpl handle = new AiRequestHandleImpl(uiDispatcher::dispatch, callback);
        uiDispatcher.dispatch(() -> requestStarter.start(request, handle));
        return handle;
    }

    private static void dispatchOnUiThread(Runnable runnable) {
        Controller controller = Controller.getCurrentController();
        if (controller != null && controller.getMainThreadExecutorService() != null) {
            controller.getMainThreadExecutorService().execute(runnable);
            return;
        }
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        }
        else {
            SwingUtilities.invokeLater(runnable);
        }
    }
}
