package org.freeplane.plugin.graph.window;

import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.util.Objects;

import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.Timer;

final class PresentationHint {
    @FunctionalInterface
    interface TimerFactory {
        Timer create(int delayMillis, Runnable expiry);
    }

    private final JLayeredPane host;
    private final JPanel panel = new JPanel() {
        @Override
        public boolean contains(int x, int y) {
            return false;
        }
    };
    private final ComponentListener hostResizeListener = new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent event) {
            if (!disposed) {
                placeAtTopCenter();
            }
        }
    };
    private final Timer timer;
    private boolean disposed;

    PresentationHint(JLayeredPane host, String text) {
        this(host, text, 3000, (delay, expiry) -> {
            Timer timer = new Timer(delay, event -> expiry.run());
            timer.setRepeats(false);
            return timer;
        });
    }

    PresentationHint(JLayeredPane host, String text, int delayMillis, TimerFactory timerFactory) {
        this.host = Objects.requireNonNull(host, "host");
        JLabel label = new JLabel(Objects.requireNonNull(text, "text"));
        label.setFocusable(false);
        panel.setFocusable(false);
        panel.add(label);
        panel.setVisible(false);
        timer = Objects.requireNonNull(Objects.requireNonNull(timerFactory, "timerFactory")
            .create(delayMillis, () -> {
                if (!disposed) {
                    hide();
                }
            }), "timer");
        host.add(panel, JLayeredPane.POPUP_LAYER);
        host.addComponentListener(hostResizeListener);
    }

    void show() {
        if (disposed) {
            return;
        }
        placeAtTopCenter();
        panel.setVisible(true);
        timer.restart();
    }

    void hide() {
        panel.setVisible(false);
        timer.stop();
    }

    void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        // Remove the overlay even if the timer's stop hook fails during close.
        try {
            timer.stop();
        }
        finally {
            host.removeComponentListener(hostResizeListener);
            host.remove(panel);
            host.revalidate();
            host.repaint();
        }
    }

    private void placeAtTopCenter() {
        Dimension size = panel.getPreferredSize();
        panel.setBounds((host.getWidth() - size.width) / 2, 12, size.width, size.height);
    }
}
