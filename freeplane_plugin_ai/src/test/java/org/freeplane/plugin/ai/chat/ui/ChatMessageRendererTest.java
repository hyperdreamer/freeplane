package org.freeplane.plugin.ai.chat.ui;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ChatMessageRendererTest {

    @Test
    public void renderFailureMessageLinkifiesUrlWithoutJsonTail() {
        ChatMessageRenderer uut = new ChatMessageRenderer();
        String message = "Request failed: {\"error\":{\"message\":\"No endpoints found, visit: "
            + "https://openrouter.ai/docs/guides/routing/provider-selection\",\"code\":404}}";

        String rendered = uut.renderFailureMessage(message);

        assertThat(rendered).contains(
            "<a href=\"https://openrouter.ai/docs/guides/routing/provider-selection\">"
                + "https://openrouter.ai/docs/guides/routing/provider-selection</a>");
        assertThat(rendered).contains("&quot;code&quot;:404}}");
        assertThat(rendered).doesNotContain(
            "provider-selection&quot;,&quot;code&quot;:404");
    }

    @Test
    public void renderFailureMessageEscapesHtmlAndPreservesLineBreaks() {
        ChatMessageRenderer uut = new ChatMessageRenderer();
        String message = "<tag>\nhttps://example.com/path";

        String rendered = uut.renderFailureMessage(message);

        assertThat(rendered).contains("&lt;tag&gt;<br>");
        assertThat(rendered).contains("<a href=\"https://example.com/path\">https://example.com/path</a>");
    }
}
