package org.freeplane.plugin.ai.chat.session;

import java.util.Objects;
import java.util.UUID;

public final class LiveChatSessionId {
    private final String value;

    private LiveChatSessionId(String value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    public static LiveChatSessionId create() {
        return new LiveChatSessionId(UUID.randomUUID().toString());
    }

    String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LiveChatSessionId)) {
            return false;
        }
        LiveChatSessionId that = (LiveChatSessionId) other;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
