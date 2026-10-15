package org.freeplane.plugin.graph.window;

import javax.swing.event.ChangeEvent;

import org.freeplane.plugin.graph.canvas.GraphViewport;

interface PresentationViewport {
    void capture();
    void compensate();
    void classify(ChangeEvent event);
    void canvasPublication();
    void toolbarPublication(GraphViewport value);
    void disarm();
    void windowClosed();
}
