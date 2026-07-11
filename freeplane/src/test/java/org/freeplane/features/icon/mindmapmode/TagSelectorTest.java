package org.freeplane.features.icon.mindmapmode;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Font;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.swing.JComboBox;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import org.freeplane.features.icon.Tag;
import org.junit.Test;

public class TagSelectorTest {
    @Test
    public void editableTagComboBox_preservesFilteringBehavior() throws Exception {
        Tag alpha = new Tag("Alpha");
        Tag beta = new Tag("Beta");
        TagSelector uut = new TagSelector(
            () -> Arrays.asList(alpha, beta).stream(),
            () -> new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        JComboBox<Tag> comboBox = uut.getComboBox();
        openPopup(comboBox);

        assertThat(displayedTags(comboBox)).containsExactly(alpha, beta);

        setEditorText(comboBox, "ALP");
        awaitFilter();
        assertThat(displayedTags(comboBox)).containsExactly(alpha);

        setEditorText(comboBox, "beta");
        awaitFilter();
        assertThat(displayedTags(comboBox)).containsExactly(beta);
        assertThat(comboBox.getSelectedItem()).isEqualTo(beta);

        setEditorText(comboBox, "new tag");
        awaitFilter();
        assertThat(displayedTags(comboBox)).isEmpty();
        assertThat(editor(comboBox).getText()).isEqualTo("new tag");

        setEditorText(comboBox, "");
        awaitFilter();
        assertThat(displayedTags(comboBox)).containsExactly(alpha, beta);
    }

    private void openPopup(JComboBox<Tag> comboBox) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PopupMenuEvent event = new PopupMenuEvent(comboBox);
            for (PopupMenuListener listener : comboBox.getPopupMenuListeners()) {
                listener.popupMenuWillBecomeVisible(event);
            }
        });
    }

    private void setEditorText(JComboBox<Tag> comboBox, String text) throws Exception {
        SwingUtilities.invokeAndWait(() -> editor(comboBox).setText(text));
    }

    private void awaitFilter() throws Exception {
        Thread.sleep(300);
        SwingUtilities.invokeAndWait(() -> { });
    }

    private JTextField editor(JComboBox<Tag> comboBox) {
        return (JTextField) comboBox.getEditor().getEditorComponent();
    }

    private List<Tag> displayedTags(JComboBox<Tag> comboBox) {
        return IntStream.range(0, comboBox.getItemCount())
            .mapToObj(comboBox::getItemAt)
            .collect(Collectors.toList());
    }
}
