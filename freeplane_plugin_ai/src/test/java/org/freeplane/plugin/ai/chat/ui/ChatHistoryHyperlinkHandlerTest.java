package org.freeplane.plugin.ai.chat.ui;

import java.net.URL;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkEvent.EventType;
import org.freeplane.core.util.Hyperlink;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class ChatHistoryHyperlinkHandlerTest {

    @Test
    public void activatedEventLoadsUriFromUrlWithoutAnchorNode() throws Exception {
        ChatHistoryHyperlinkHandler.LinkControllerAdapter linkController = mock(ChatHistoryHyperlinkHandler.LinkControllerAdapter.class);
        Hyperlink hyperlink = new Hyperlink(new URL("https://openrouter.ai").toURI());
        when(linkController.createHyperlink("https://openrouter.ai")).thenReturn(hyperlink);
        ChatHistoryHyperlinkHandler uut = new ChatHistoryHyperlinkHandler(linkController);

        uut.createListener().hyperlinkUpdate(
            new HyperlinkEvent(this, EventType.ACTIVATED, new URL("https://openrouter.ai")));

        verify(linkController).createHyperlink("https://openrouter.ai");
        verify(linkController).loadHyperlink(hyperlink);
    }

    @Test
    public void nonActivatedEventDoesNotLoadUri() throws Exception {
        ChatHistoryHyperlinkHandler.LinkControllerAdapter linkController = mock(ChatHistoryHyperlinkHandler.LinkControllerAdapter.class);
        ChatHistoryHyperlinkHandler uut = new ChatHistoryHyperlinkHandler(linkController);

        uut.createListener().hyperlinkUpdate(
            new HyperlinkEvent(this, EventType.ENTERED, new URL("https://openrouter.ai")));

        verifyNoInteractions(linkController);
    }

    @Test
    public void malformedDescriptionDoesNotLoadUri() throws Exception {
        ChatHistoryHyperlinkHandler.LinkControllerAdapter linkController = mock(ChatHistoryHyperlinkHandler.LinkControllerAdapter.class);
        when(linkController.createHyperlink("%%%")).thenThrow(new IllegalArgumentException("bad"));
        ChatHistoryHyperlinkHandler uut = new ChatHistoryHyperlinkHandler(linkController);

        uut.createListener().hyperlinkUpdate(
            new HyperlinkEvent(this, EventType.ACTIVATED, null, "%%%"));

        verify(linkController).createHyperlink("%%%");
        verify(linkController, never()).loadHyperlink(any());
    }

    @Test
    public void emptyDescriptionDoesNotLoadUri() throws Exception {
        ChatHistoryHyperlinkHandler.LinkControllerAdapter linkController = mock(ChatHistoryHyperlinkHandler.LinkControllerAdapter.class);
        ChatHistoryHyperlinkHandler uut = new ChatHistoryHyperlinkHandler(linkController);

        uut.createListener().hyperlinkUpdate(
            new HyperlinkEvent(this, EventType.ACTIVATED, null, " "));

        verifyNoInteractions(linkController);
    }
}
