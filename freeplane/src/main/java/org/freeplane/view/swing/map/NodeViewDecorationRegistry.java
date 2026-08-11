package org.freeplane.view.swing.map;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.freeplane.core.extension.IExtension;
import org.freeplane.features.mode.ModeController;

public final class NodeViewDecorationRegistry implements IExtension {
    private final List<NodeViewDecorationPainter> painters = new ArrayList<NodeViewDecorationPainter>();

    public static NodeViewDecorationRegistry of(final ModeController modeController) {
        Objects.requireNonNull(modeController, "modeController");
        synchronized (modeController) {
            NodeViewDecorationRegistry registry = modeController.getExtension(NodeViewDecorationRegistry.class);
            if (registry == null) {
                registry = new NodeViewDecorationRegistry();
                modeController.addExtension(NodeViewDecorationRegistry.class, registry);
            }
            return registry;
        }
    }

    public synchronized void add(final NodeViewDecorationPainter painter) {
        Objects.requireNonNull(painter, "painter");
        for (NodeViewDecorationPainter registered : painters) {
            if (registered == painter) {
                return;
            }
        }
        painters.add(painter);
    }

    public synchronized void remove(final NodeViewDecorationPainter painter) {
        for (int index = 0; index < painters.size(); index++) {
            if (painters.get(index) == painter) {
                painters.remove(index);
                return;
            }
        }
    }

    public synchronized boolean isEmpty() {
        return painters.isEmpty();
    }

    void paint(final NodeView nodeView, final Graphics2D graphics) {
        final List<NodeViewDecorationPainter> snapshot;
        synchronized (this) {
            if (painters.isEmpty()) {
                return;
            }
            snapshot = new ArrayList<NodeViewDecorationPainter>(painters);
        }
        for (NodeViewDecorationPainter painter : snapshot) {
            final Graphics2D painterGraphics = (Graphics2D) graphics.create();
            try {
                painter.paint(nodeView, painterGraphics);
            }
            finally {
                painterGraphics.dispose();
            }
        }
    }
}
