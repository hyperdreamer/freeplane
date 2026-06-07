package org.freeplane.plugin.ai.tools.edit;

import java.util.Objects;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.note.mindmapmode.MNoteController;

public class NoteContentWriteControllerAdapter implements NoteContentWriteController {
    private final MNoteController noteController;

    public NoteContentWriteControllerAdapter(MNoteController noteController) {
        this.noteController = Objects.requireNonNull(noteController, "noteController");
    }

    @Override
    public void setNoteText(NodeModel nodeModel, String value) {
        noteController.setNoteText(nodeModel, value);
    }
}
