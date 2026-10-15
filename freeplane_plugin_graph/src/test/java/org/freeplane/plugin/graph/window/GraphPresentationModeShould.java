package org.freeplane.plugin.graph.window;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.junit.Test;

public class GraphPresentationModeShould {
    @Test
    public void frameStateDefensivelyCopiesBoundsAndPreservesExtendedState() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Rectangle original = new Rectangle(11, 22, 900, 700);
            ScreenFillSurface.FrameState saved =
                new ScreenFillSurface.FrameState(original, Frame.MAXIMIZED_BOTH);
            original.x = 999;
            saved.bounds().width = 1;
            assertThat(saved.bounds()).isEqualTo(new Rectangle(11, 22, 900, 700));
            assertThat(saved.extendedState()).isEqualTo(Frame.MAXIMIZED_BOTH);
        });
    }

    @Test
    public void hintShowRestartsInjectedTimerAndExpiryHidesPanel() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            HintFixture fixture = new HintFixture();
            JPanel panel = fixture.panel();
            assertThat(panel.isVisible()).isFalse();
            fixture.hint.show();
            fixture.hint.show();
            assertThat(fixture.timer.restarts).isEqualTo(2);
            assertThat(fixture.delay).isEqualTo(3000);
            assertThat(panel.isVisible()).isTrue();
            assertThat(panel.getX()).isEqualTo((1000 - panel.getWidth()) / 2);
            assertThat(JLayeredPane.getLayer(panel)).isGreaterThan(JLayeredPane.DEFAULT_LAYER);
            fixture.host.setSize(1200, 700);
            for (ComponentListener listener : fixture.host.getComponentListeners()) {
                listener.componentResized(new ComponentEvent(fixture.host, ComponentEvent.COMPONENT_RESIZED));
            }
            assertThat(panel.getX()).isEqualTo((1200 - panel.getWidth()) / 2);
            fixture.expiry.get().run();
            assertThat(panel.isVisible()).isFalse();
            fixture.hint.dispose();
        });
    }

    @Test
    public void hintHideStopsTimerAndDisposeMakesExpiryInert() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            HintFixture fixture = new HintFixture();
            fixture.hint.show();
            fixture.hint.hide();
            assertThat(fixture.panel().isVisible()).isFalse();
            assertThat(fixture.timer.stops).isEqualTo(1);
            Runnable lateExpiry = fixture.expiry.get();
            fixture.hint.dispose();
            fixture.hint.dispose();
            int stops = fixture.timer.stops;
            lateExpiry.run();
            fixture.hint.show();
            assertThat(fixture.host.getComponentCount()).isZero();
            assertThat(fixture.host.getComponentListeners()).isEmpty();
            assertThat(fixture.timer.stops).isEqualTo(stops);
            assertThat(fixture.timer.restarts).isEqualTo(1);
        });
    }

    @Test
    public void hintNeverAcceptsFocusOrInterceptsMouse() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            HintFixture fixture = new HintFixture();
            fixture.hint.show();
            JPanel panel = fixture.panel();
            JLabel label = (JLabel) panel.getComponent(0);
            assertThat(panel.isFocusable()).isFalse();
            assertThat(label.isFocusable()).isFalse();
            assertThat(label.getText()).isEqualTo("Press Esc to exit full screen");
            assertThat(panel.contains(panel.getWidth() / 2, panel.getHeight() / 2)).isFalse();
            fixture.hint.dispose();
        });
    }

    private static final class HintFixture {
        final JLayeredPane host = new JLayeredPane();
        final AtomicReference<Runnable> expiry = new AtomicReference<Runnable>();
        final RecordingTimer timer = new RecordingTimer();
        final PresentationHint hint;
        int delay;

        HintFixture() {
            host.setSize(1000, 700);
            hint = new PresentationHint(host, "Press Esc to exit full screen", 3000, (millis, callback) -> {
                delay = millis;
                expiry.set(callback);
                return timer;
            });
        }

        JPanel panel() {
            assertThat(host.getComponents()).hasSize(1);
            return (JPanel) host.getComponent(0);
        }
    }

    private static final class RecordingTimer extends Timer {
        int restarts;
        int stops;

        RecordingTimer() {
            super(3000, null);
        }

        @Override
        public void restart() {
            restarts++;
        }

        @Override
        public void stop() {
            stops++;
        }
    }
}
