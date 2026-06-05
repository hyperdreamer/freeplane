package org.freeplane.plugin.ai.chat.ui;

import java.net.URL;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import org.freeplane.core.util.Hyperlink;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.link.LinkController;

class ChatHistoryHyperlinkHandler {

    interface LinkControllerAdapter {
        Hyperlink createHyperlink(String uriText) throws Exception;
        void loadHyperlink(Hyperlink hyperlink);
    }

    private static final LinkControllerAdapter DEFAULT_LINK_CONTROLLER = new LinkControllerAdapter() {
        @Override
        public Hyperlink createHyperlink(String uriText) throws Exception {
            return LinkController.createHyperlink(uriText);
        }

        @Override
        public void loadHyperlink(Hyperlink hyperlink) {
            LinkController.getController().loadHyperlink( hyperlink);
        }
    };

    static LinkControllerAdapter defaultLinkControllerAdapter() {
        return DEFAULT_LINK_CONTROLLER;
    }

    private final LinkControllerAdapter linkController;

    ChatHistoryHyperlinkHandler(LinkControllerAdapter linkController) {
        this.linkController = linkController;
    }

    HyperlinkListener createListener() {
        return new HyperlinkListener() {
            @Override
            public void hyperlinkUpdate(HyperlinkEvent event) {
                if (event.getEventType() != HyperlinkEvent.EventType.ACTIVATED) {
                    return;
                }
                String uriText = uriText(event);
                if (uriText == null || uriText.trim().isEmpty()) {
                    return;
                }
                try {
                    Hyperlink hyperlink = linkController.createHyperlink(uriText);
                    linkController.loadHyperlink(hyperlink);
                } catch (Exception ignored) {
                	LogUtils.warn(ignored);
                }
            }
        };
    }

    private String uriText(HyperlinkEvent event) {
        URL url = event.getURL();
        if (url != null) {
            return url.toExternalForm();
        }
        return event.getDescription();
    }
}
