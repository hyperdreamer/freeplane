package org.freeplane.features.icon.mindmapmode;

import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.swing.ComboBoxEditor;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import org.freeplane.core.ui.components.TagIcon;
import org.freeplane.features.icon.Tag;

class TagSelector {
    private static final JPanel TRANSPARENT_RENDERER = new JPanel();
    static {
        TRANSPARENT_RENDERER.setOpaque(false);
    }

    private final JComboBox<Tag> comboBox = new TagComboBox();
    private final Supplier<Stream<Tag>> tagSupplier;
    private final Supplier<Font> fontSupplier;
    private final Timer filterTimer;
    private boolean filterIsRunning;

    TagSelector(Supplier<Stream<Tag>> tagSupplier, Supplier<Font> fontSupplier) {
        this.tagSupplier = tagSupplier;
        this.fontSupplier = fontSupplier;
        filterTimer = new Timer(200, event -> updateListItems());
        filterTimer.setRepeats(false);
        configureComboBox();
        installListeners();
    }

    JComboBox<Tag> getComboBox() {
        return comboBox;
    }

    boolean isFilterRunning() {
        return filterIsRunning;
    }

    private void configureComboBox() {
        comboBox.setEditable(true);
        comboBox.setRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                if (index == -1) {
                    return TRANSPARENT_RENDERER;
                }
                Object displayedValue = value instanceof Tag
                    ? new TagIcon((Tag) value, fontSupplier.get())
                    : value;
                return super.getListCellRendererComponent(
                    list, displayedValue, index, isSelected, cellHasFocus);
            }
        });
        JTextField editorComponent = editorComponent();
        editorComponent.putClientProperty("JTextField.selectAllOnFocusPolicy", "never");
    }

    private void installListeners() {
        DocumentListener documentListener = new DocumentListener() {
            @Override
            public void removeUpdate(DocumentEvent event) {
                updateList();
            }

            @Override
            public void insertUpdate(DocumentEvent event) {
                updateList();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                updateList();
            }

            private void updateList() {
                if (!filterIsRunning) {
                    resetSelectedItem();
                    filterTimer.restart();
                }
            }
        };
        comboBox.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent event) {
                resetSelectedItem();
                updateListItems();
                editorComponent().getDocument().addDocumentListener(documentListener);
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent event) {
                editorComponent().getDocument().removeDocumentListener(documentListener);
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent event) {
            }
        });
        comboBox.getEditor().getEditorComponent().addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent event) {
                if (!comboBox.isPopupVisible()) {
                    EventQueue.invokeLater(() -> {
                        if (comboBox.isShowing()) {
                            comboBox.showPopup();
                        }
                    });
                }
            }
        });
    }

    private void resetSelectedItem() {
        if (filterIsRunning) {
            return;
        }
        filterIsRunning = true;
        try {
            comboBox.setSelectedItem(null);
        }
        finally {
            filterIsRunning = false;
        }
    }

    private void updateListItems() {
        if (filterIsRunning) {
            return;
        }
        filterIsRunning = true;
        try {
            DefaultComboBoxModel<Tag> model = model();
            model.removeAllElements();
            String text = editorComponent().getText();
            String normalizedText = text.toLowerCase(Locale.ROOT);
            Stream<Tag> tags = tagSupplier.get();
            Stream<Tag> displayedTags = text.isEmpty()
                ? tags
                : tags.filter(tag -> tag.getContent().toLowerCase(Locale.ROOT).contains(normalizedText));
            AtomicInteger exactMatchIndex = new AtomicInteger(-1);
            displayedTags.forEach(tag -> {
                model.addElement(tag);
                if (exactMatchIndex.get() == -1
                    && tag.getContent().equalsIgnoreCase(text)) {
                    exactMatchIndex.set(model.getSize() - 1);
                }
            });
            if (exactMatchIndex.get() >= 0) {
                comboBox.setSelectedIndex(exactMatchIndex.get());
            }
        }
        finally {
            filterIsRunning = false;
        }
    }

    private JTextField editorComponent() {
        return (JTextField) comboBox.getEditor().getEditorComponent();
    }

    @SuppressWarnings("unchecked")
    private DefaultComboBoxModel<Tag> model() {
        return (DefaultComboBoxModel<Tag>) comboBox.getModel();
    }

    private class TagComboBox extends JComboBox<Tag> {
        private static final long serialVersionUID = 1L;

        TagComboBox() {
            super(new DefaultComboBoxModel<Tag>());
        }

        @Override
        public void configureEditor(ComboBoxEditor editor, Object item) {
            if (!filterIsRunning) {
                super.configureEditor(editor, item);
            }
        }
    }
}
