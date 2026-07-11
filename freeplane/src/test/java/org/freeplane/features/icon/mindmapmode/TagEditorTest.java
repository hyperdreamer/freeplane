package org.freeplane.features.icon.mindmapmode;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JComboBox;

import org.freeplane.features.icon.Tag;
import org.junit.Test;

public class TagEditorTest {
    @Test
    public void tagSelector_preservesNewTagCommitBehavior() {
        JComboBox<Tag> comboBox = new JComboBox<Tag>();
        comboBox.setEditable(true);
        AtomicReference<String> createdSpec = new AtomicReference<String>();
        TagEditor.TagCellEditor uut = new TagEditor.TagCellEditor(
            comboBox,
            spec -> {
                createdSpec.set(spec);
                return new Tag(spec);
            },
            () -> false);
        Tag existingTag = new Tag("Existing");
        comboBox.addItem(existingTag);
        comboBox.setSelectedItem(existingTag);

        assertThat(uut.getCellEditorValue()).isEqualTo(existingTag);
        assertThat(createdSpec.get()).isNull();

        comboBox.setSelectedItem("New tag");

        Tag createdTag = (Tag) uut.getCellEditorValue();
        assertThat(createdTag.getContent()).isEqualTo("New tag");
        assertThat(createdSpec).hasValue("New tag");
    }
}
