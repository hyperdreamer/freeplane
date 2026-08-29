package org.freeplane.plugin.graph.window;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;

import org.freeplane.core.resources.IFreeplanePropertyListener;
import org.freeplane.plugin.graph.group.GraphGroupColors;
import org.junit.Test;

public class GraphWorkspaceWindowColorListenerShould {
    @Test
    public void repaintsWhenTheGroupColorPreferenceChanges() {
        final AtomicBoolean repainted = new AtomicBoolean(false);
        IFreeplanePropertyListener listener = GraphWorkspaceWindow.repaintOnColorChange(new Runnable() {
            @Override
            public void run() {
                repainted.set(true);
            }
        });

        listener.propertyChanged(GraphGroupColors.COLOR_PROPERTY_KEY, "#112233", "#df625d");

        assertThat(repainted.get()).isTrue();
    }

    @Test
    public void ignoresUnrelatedPropertyChanges() {
        final AtomicBoolean repainted = new AtomicBoolean(false);
        IFreeplanePropertyListener listener = GraphWorkspaceWindow.repaintOnColorChange(new Runnable() {
            @Override
            public void run() {
                repainted.set(true);
            }
        });

        listener.propertyChanged("some_other_property", "x", "y");

        assertThat(repainted.get()).isFalse();
    }
}
