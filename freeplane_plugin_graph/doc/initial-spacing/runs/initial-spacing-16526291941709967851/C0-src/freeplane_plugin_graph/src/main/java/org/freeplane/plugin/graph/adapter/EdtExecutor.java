package org.freeplane.plugin.graph.adapter;

import java.util.concurrent.Callable;

public interface EdtExecutor {
    <T> T call(Callable<T> task);
    void execute(Runnable task);
    boolean isEdt();
}
