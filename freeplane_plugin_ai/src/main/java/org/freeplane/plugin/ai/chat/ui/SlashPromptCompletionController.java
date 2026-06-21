package org.freeplane.plugin.ai.chat.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import javax.swing.text.Utilities;
import org.freeplane.plugin.ai.prompt.AiPrompt;

class SlashPromptCompletionController {
    private final JTextArea inputArea;
    private final Supplier<List<AiPrompt>> promptSupplier;
    private final PromptReferenceResolver promptReferenceResolver;
    private final Runnable promptReferenceChangeListener;
    private final JPopupMenu popup;
    private final JList<AiPrompt> candidateList;
    private final DefaultListModel<AiPrompt> candidateListModel;
    private final Highlighter.HighlightPainter underlinePainter;
    private Object underlineTag;
    private Character promptControlTypedCharacterToConsume;
    private String lastText = "";
    private String popupSuppressedText;

    SlashPromptCompletionController(JTextArea inputArea,
                                    Supplier<List<AiPrompt>> promptSupplier,
                                    PromptReferenceResolver promptReferenceResolver) {
        this(inputArea, promptSupplier, promptReferenceResolver, () -> {
        });
    }

    SlashPromptCompletionController(JTextArea inputArea,
                                    Supplier<List<AiPrompt>> promptSupplier,
                                    PromptReferenceResolver promptReferenceResolver,
                                    Runnable promptReferenceChangeListener) {
        this.inputArea = Objects.requireNonNull(inputArea, "inputArea");
        this.promptSupplier = Objects.requireNonNull(promptSupplier, "promptSupplier");
        this.promptReferenceResolver = Objects.requireNonNull(promptReferenceResolver, "promptReferenceResolver");
        this.promptReferenceChangeListener = promptReferenceChangeListener == null
            ? () -> {
            }
            : promptReferenceChangeListener;
        this.candidateListModel = new DefaultListModel<AiPrompt>();
        this.candidateList = new JList<AiPrompt>(candidateListModel);
        this.popup = new JPopupMenu();
        this.underlinePainter = new PromptReferenceUnderlinePainter();
        configurePopup();
    }

    void install() {
        lastText = inputArea.getText();
        inputArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                handleDocumentChange(event);
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                handleDocumentChange(event);
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                handleDocumentChange(event);
            }
        });
        inputArea.addCaretListener(new CaretListener() {
            @Override
            public void caretUpdate(CaretEvent event) {
                refresh();
            }
        });
        installKeyInterceptor();
    }

    void refreshFromPromptListChange() {
        lastText = inputArea.getText();
        refresh();
        promptReferenceChangeListener.run();
    }

    void refresh() {
        List<AiPrompt> prompts = promptSupplier.get();
        PromptReferenceMatch match = promptReferenceResolver.resolveLeadingReference(inputArea.getText(), prompts);
        updateUnderline(match);
        updatePopup(prompts);
    }

    void closePopup() {
        popup.setVisible(false);
    }

    int underlineCount() {
        return underlineTag == null ? 0 : 1;
    }

    int candidateCount() {
        return candidateListModel.size();
    }

    int selectedCandidateIndex() {
        return candidateList.getSelectedIndex();
    }

    String selectedCandidateName() {
        AiPrompt selectedPrompt = candidateList.getSelectedValue();
        return selectedPrompt == null ? null : selectedPrompt.getName();
    }

    private void configurePopup() {
        popup.setFocusable(false);
        popup.setRequestFocusEnabled(false);
        candidateList.setFocusable(false);
        candidateList.setRequestFocusEnabled(false);
        candidateList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        candidateList.setVisibleRowCount(8);
        candidateList.setCellRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getListCellRendererComponent(JList<?> list,
                                                          Object value,
                                                          int index,
                                                          boolean isSelected,
                                                          boolean cellHasFocus) {
                Component component = super.getListCellRendererComponent(
                    list,
                    value instanceof AiPrompt ? ((AiPrompt) value).getName() : value,
                    index,
                    isSelected,
                    cellHasFocus);
                return component;
            }
        });
        candidateList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    acceptSelectedCandidate();
                }
            }
        });
        JPanel panel = new JPanel(new BorderLayout());
        panel.setFocusable(false);
        panel.setRequestFocusEnabled(false);
        JScrollPane scrollPane = new JScrollPane(candidateList);
        scrollPane.setFocusable(false);
        scrollPane.setRequestFocusEnabled(false);
        panel.add(scrollPane, BorderLayout.CENTER);
        popup.add(panel);
    }

    private void installKeyInterceptor() {
        inputArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                interceptKeyPressed(event);
            }

            @Override
            public void keyTyped(KeyEvent event) {
                interceptKeyTyped(event);
            }
        });
    }

    boolean interceptKeyPressed(KeyEvent event) {
        promptControlTypedCharacterToConsume = null;
        if (!popup.isVisible()) {
            return false;
        }
        return handleVisiblePopupKeyPressed(event);
    }

    boolean handleVisiblePopupKeyPressed(KeyEvent event) {
        if (event.getModifiersEx() != 0) {
            return false;
        }
        switch (event.getKeyCode()) {
            case KeyEvent.VK_UP:
                moveSelection(-1);
                event.consume();
                return true;
            case KeyEvent.VK_DOWN:
                moveSelection(1);
                event.consume();
                return true;
            case KeyEvent.VK_TAB:
                acceptSelectedCandidate();
                promptControlTypedCharacterToConsume = Character.valueOf('\t');
                event.consume();
                return true;
            case KeyEvent.VK_ENTER:
                acceptSelectedCandidate();
                promptControlTypedCharacterToConsume = Character.valueOf('\n');
                event.consume();
                return true;
            case KeyEvent.VK_ESCAPE:
                closePopup();
                event.consume();
                return true;
            default:
                return false;
        }
    }

    boolean interceptKeyTyped(KeyEvent event) {
        Character characterToConsume = promptControlTypedCharacterToConsume;
        if (characterToConsume == null) {
            return false;
        }
        promptControlTypedCharacterToConsume = null;
        if (event.getKeyChar() != characterToConsume.charValue()) {
            return false;
        }
        event.consume();
        return true;
    }

    private void handleDocumentChange(DocumentEvent event) {
        String newText = inputArea.getText();
        if (!newText.equals(popupSuppressedText)) {
            popupSuppressedText = null;
        }
        List<AiPrompt> prompts = promptSupplier.get();
        boolean relevant = promptReferenceResolver.isPromptRelevantChange(
            lastText,
            newText,
            event.getOffset(),
            prompts);
        lastText = newText;
        if (relevant) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    refresh();
                    promptReferenceChangeListener.run();
                }
            });
        }
    }

    private void updatePopup(List<AiPrompt> prompts) {
        List<AiPrompt> candidates = promptReferenceResolver.completionCandidates(
            inputArea.getText(),
            inputArea.getCaretPosition(),
            prompts);
        if (inputArea.getText().equals(popupSuppressedText)) {
            closePopup();
            return;
        }
        candidateListModel.clear();
        for (AiPrompt candidate : candidates) {
            candidateListModel.addElement(candidate);
        }
        if (candidateListModel.isEmpty()) {
            closePopup();
            return;
        }
        candidateList.setSelectedIndex(0);
        if (inputArea.hasFocus() && inputArea.isShowing()) {
            showPopupAtCaret();
        }
    }

    private void showPopupAtCaret() {
        try {
            Rectangle2D caretRectangle = inputArea.modelToView2D(inputArea.getCaretPosition());
            if (caretRectangle == null) {
                return;
            }
            Rectangle popupBounds = popupBoundsForCaret(caretRectangle);
            if (!popup.isVisible()) {
                popup.show(inputArea, popupBounds.x, popupBounds.y);
            }
            else {
                popup.setLocation(inputArea.getLocationOnScreen().x + popupBounds.x,
                    inputArea.getLocationOnScreen().y + popupBounds.y);
            }
        }
        catch (BadLocationException ignored) {
            closePopup();
        }
    }

    Rectangle popupBoundsForCaret(Rectangle2D caretRectangle) {
        Dimension preferredSize = popup.getPreferredSize();
        int caretX = (int) Math.round(caretRectangle.getX());
        int maximumX = Math.max(0, inputArea.getWidth() - preferredSize.width);
        int x = Math.max(0, Math.min(caretX, maximumX));
        return new Rectangle(x, -preferredSize.height, preferredSize.width, preferredSize.height);
    }

    private void updateUnderline(PromptReferenceMatch match) {
        Highlighter highlighter = inputArea.getHighlighter();
        if (underlineTag != null) {
            highlighter.removeHighlight(underlineTag);
            underlineTag = null;
        }
        if (match == null || match.getReferenceEndOffset() <= match.getReferenceStartOffset()) {
            return;
        }
        try {
            underlineTag = highlighter.addHighlight(
                match.getReferenceStartOffset(),
                match.getReferenceEndOffset(),
                underlinePainter);
        }
        catch (BadLocationException ignored) {
            underlineTag = null;
        }
    }

    void acceptSelectedCandidate() {
        AiPrompt selectedPrompt = candidateList.getSelectedValue();
        if (selectedPrompt == null) {
            return;
        }
        String promptName = selectedPrompt.getName() == null ? "" : selectedPrompt.getName().trim();
        if (promptName.isEmpty()) {
            return;
        }
        int caret = Math.max(1, Math.min(inputArea.getCaretPosition(), inputArea.getText().length()));
        String suffix = inputArea.getText().substring(caret);
        String insertion = "/" + promptName;
        if (suffix.isEmpty() || !Character.isWhitespace(suffix.charAt(0))) {
            insertion += " ";
        }
        inputArea.replaceRange(insertion, 0, caret);
        popupSuppressedText = inputArea.getText();
        inputArea.setCaretPosition(insertion.length());
        closePopup();
        refresh();
    }

    private void moveSelection(int delta) {
        int size = candidateListModel.size();
        if (size == 0) {
            return;
        }
        int selectedIndex = candidateList.getSelectedIndex();
        int newIndex = selectedIndex < 0 ? 0 : Math.max(0, Math.min(size - 1, selectedIndex + delta));
        candidateList.setSelectedIndex(newIndex);
        candidateList.ensureIndexIsVisible(newIndex);
    }

    private static class PromptReferenceUnderlinePainter implements Highlighter.HighlightPainter {
        @Override
        public void paint(Graphics graphics, int startOffset, int endOffset, Shape bounds, JTextComponent component) {
            if (graphics == null || component == null || startOffset >= endOffset) {
                return;
            }
            graphics.setColor(component.getForeground());
            int currentOffset = startOffset;
            while (currentOffset < endOffset) {
                try {
                    int rowEnd = Utilities.getRowEnd(component, currentOffset);
                    if (rowEnd < 0) {
                        return;
                    }
                    int segmentEnd = Math.min(endOffset, rowEnd + 1);
                    Rectangle2D startRectangle = component.modelToView2D(currentOffset);
                    Rectangle2D endRectangle = component.modelToView2D(segmentEnd);
                    if (startRectangle == null || endRectangle == null) {
                        return;
                    }
                    Rectangle allocation = bounds instanceof Rectangle ? (Rectangle) bounds : bounds.getBounds();
                    int lineY = (int) Math.round(startRectangle.getY() + startRectangle.getHeight() - 2);
                    int lineStartX = (int) Math.round(startRectangle.getX());
                    int lineEndX = segmentEnd >= endOffset
                        ? (int) Math.round(endRectangle.getX())
                        : allocation.x + allocation.width;
                    if (lineEndX <= lineStartX) {
                        lineEndX = lineStartX + Math.max(1, (int) Math.round(startRectangle.getWidth()));
                    }
                    graphics.drawLine(lineStartX, lineY, lineEndX, lineY);
                    currentOffset = segmentEnd;
                }
                catch (BadLocationException ignored) {
                    return;
                }
            }
        }
    }
}
