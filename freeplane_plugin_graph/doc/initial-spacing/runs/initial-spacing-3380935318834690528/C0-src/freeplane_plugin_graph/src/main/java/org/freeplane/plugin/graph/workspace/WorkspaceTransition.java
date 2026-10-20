package org.freeplane.plugin.graph.workspace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.freeplane.plugin.graph.workspace.model.WorkspaceDocument;

public final class WorkspaceTransition {
    public enum Status {
        APPLIED,
        NO_OP,
        REJECTED
    }

    private final Status status;
    private final WorkspaceDocument after;
    private final String messageKey;
    private final List<Object> messageArguments;

    private WorkspaceTransition(final Status status, final WorkspaceDocument after, final String messageKey,
            final Object... messageArguments) {
        this.status = Objects.requireNonNull(status, "status");
        this.after = Objects.requireNonNull(after, "after");
        this.messageKey = Objects.requireNonNull(messageKey, "messageKey");
        this.messageArguments = copyArguments(messageArguments);
    }

    public static WorkspaceTransition applied(final WorkspaceDocument after, final String key,
            final Object... args) {
        return new WorkspaceTransition(Status.APPLIED, after, key, args);
    }

    public static WorkspaceTransition noOp(final WorkspaceDocument same, final String key,
            final Object... args) {
        return new WorkspaceTransition(Status.NO_OP, same, key, args);
    }

    public static WorkspaceTransition rejected(final WorkspaceDocument same, final String key,
            final Object... args) {
        return new WorkspaceTransition(Status.REJECTED, same, key, args);
    }

    public Status status() {
        return status;
    }

    public WorkspaceDocument after() {
        return after;
    }

    public String messageKey() {
        return messageKey;
    }

    public List<Object> messageArguments() {
        return messageArguments;
    }

    private static List<Object> copyArguments(final Object[] arguments) {
        Objects.requireNonNull(arguments, "messageArguments");
        final List<Object> copy = new ArrayList<Object>(arguments.length);
        for (final Object argument : arguments) {
            copy.add(argument);
        }
        return Collections.unmodifiableList(copy);
    }
}
