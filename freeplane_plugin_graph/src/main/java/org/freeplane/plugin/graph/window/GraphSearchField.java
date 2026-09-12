package org.freeplane.plugin.graph.window;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;

import javax.swing.Icon;
import javax.swing.JTextField;

final class GraphSearchField extends JTextField {
    private static final int GAP = 6;
    private static final int TRAILING_INSET = 6;

    private final Insets baseMargin;
    private String prompt;
    private Icon promptIcon;

    GraphSearchField() {
        baseMargin = getMargin();
    }

    void setPrompt(final String prompt) {
        this.prompt = prompt;
        repaint();
    }

    void setPromptIcon(final Icon icon) {
        promptIcon = icon;
        final Insets base = baseMargin != null ? baseMargin : new Insets(0, 0, 0, 0);
        if (icon != null) {
            setMargin(new Insets(base.top, icon.getIconWidth() + GAP, base.bottom, TRAILING_INSET));
        }
        else {
            setMargin(baseMargin);
        }
        repaint();
    }

    private Insets currentMargin() {
        final Insets margin = getMargin();
        return margin != null ? margin : baseMargin;
    }

    @Override
    protected void paintComponent(final Graphics graphics) {
        super.paintComponent(graphics);
        final Icon icon = promptIcon;
        if (icon != null) {
            final Insets margin = currentMargin();
            final int marginLeft = margin == null ? 0 : margin.left;
            final int glyphX = getInsets().left - marginLeft;
            icon.paintIcon(this, graphics, Math.max(0, glyphX),
                (getHeight() - icon.getIconHeight()) / 2);
        }
        if (!getText().isEmpty() || isFocusOwner()) {
            return;
        }
        if (prompt != null) {
            graphics.setColor(getDisabledTextColor());
            graphics.drawString(prompt, getInsets().left,
                getInsets().top + getFontMetrics(getFont()).getAscent());
        }
    }

    @Override
    public Dimension getPreferredSize() {
        final int promptWidth = prompt == null ? 0 : getFontMetrics(getFont()).stringWidth(prompt);
        final int width = Math.max(160, getInsets().left + promptWidth + getInsets().right);
        final Insets margin = currentMargin() == null ? new Insets(0, 0, 0, 0) : currentMargin();
        final int iconHeight = promptIcon == null ? 0 : promptIcon.getIconHeight();
        final int height = Math.max(26, iconHeight + margin.top + margin.bottom);
        return new Dimension(width, height);
    }
}
