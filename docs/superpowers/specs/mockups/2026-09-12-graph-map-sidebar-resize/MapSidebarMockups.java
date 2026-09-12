import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * Design mockups for the Graph Workspace Maps-sidebar resize / hide change.
 *
 * Sizes and rules mirror production code:
 *   - MapListPanel.PANEL_WIDTH = 264 (today's fixed sidebar width, kept as the default)
 *   - WorkspaceSettingsPanel.PANEL_WIDTH = 244 (right Display panel, unchanged)
 *   - MapListPanel.ROW_HEIGHT = 52
 *   - WorkspaceToolbar row height = 42
 *   - proposed: MIN_WIDTH = 180, MAX = 50% of the graph area, RAIL_WIDTH = 26
 *
 * The mockup is drawn with plain Swing painting; it is an inspection artefact only and is
 * never hand-edited.
 *
 * Usage: java MapSidebarMockups <output-directory>
 */
public final class MapSidebarMockups {

    static final int SIDEBAR_DEFAULT = 264;
    static final int SIDEBAR_MIN = 180;
    static final int RAIL_WIDTH = 26;
    static final int SETTINGS_WIDTH = 244;
    static final int TOOLBAR_HEIGHT = 42;
    static final int MENU_HEIGHT = 28;
    static final int STATUS_HEIGHT = 30;
    static final int DIVIDER = 6;
    static final int ROW_HEIGHT = 52;

    static final Font TITLE = new Font(Font.SANS_SERIF, Font.BOLD, 14);
    static final Font CAPTION = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    static final Font BODY = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    static final Font SMALL = new Font(Font.SANS_SERIF, Font.PLAIN, 10);
    static final Font TINY = new Font(Font.SANS_SERIF, Font.PLAIN, 9);
    static final Font BOLD_SMALL = new Font(Font.SANS_SERIF, Font.BOLD, 10);
    static final Font BOLD_TINY = new Font(Font.SANS_SERIF, Font.BOLD, 9);

    static final Color WINDOW = new Color(0xF2, 0xF3, 0xF5);
    static final Color TOOLBAR = new Color(0xE9, 0xEC, 0xF1);
    static final Color SIDEBAR = new Color(0xEE, 0xF1, 0xF6);
    static final Color SETTINGS = new Color(0xEE, 0xF1, 0xF6);
    static final Color CANVAS = new Color(0xFA, 0xFB, 0xFC);
    static final Color RAIL = new Color(0xE3, 0xE7, 0xEE);
    static final Color EDGE = new Color(0xC6, 0xCC, 0xD7);
    static final Color INK = new Color(0x1F, 0x24, 0x2B);
    static final Color MUTED = new Color(0x6B, 0x74, 0x80);
    static final Color CHIP = new Color(0xDF, 0xE4, 0xEC);
    static final Color ACCENT = new Color(0x4E, 0x79, 0xA7);
    static final Color MARK = new Color(0xC2, 0x3B, 0x3B);
    static final Color BLOB_FILL = new Color(0xEC, 0xF2, 0xFA);
    static final Color BLOB_EDGE = new Color(0xE0, 0xA0, 0x4A);

    public static void main(final String[] args) throws Exception {
        final File out = new File(args.length > 0 ? args[0] : ".");
        out.mkdirs();
        write(out, "01-expanded-collapse-control.png", expandedPanel());
        write(out, "02-collapsed-rail.png", collapsedPanel());
        write(out, "03-resize-bounds.png", boundsPanel());
        write(out, "2026-09-12-graph-map-sidebar-resize-mockup.png", combinedMockup());
        System.out.println("mockups written to " + out.getAbsolutePath());
    }

    private static void write(final File dir, final String name, final BufferedImage image) throws Exception {
        ImageIO.write(image, "png", new File(dir, name));
    }

    // ---------------------------------------------------------------- panels

    private static BufferedImage expandedPanel() {
        final BufferedImage image = new BufferedImage(1160, 700, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = graphics(image);
        title(g, 24, 26, "Expanded \u2014 collapse control in the Maps heading");
        drawWindow(g, 20, 44, 1120, 620, false, SIDEBAR_DEFAULT, true);
        final int headingX = 20 + sidebarRightOffset(SIDEBAR_DEFAULT) - 28;
        final int headingY = 44 + MENU_HEIGHT + TOOLBAR_HEIGHT + 4;
        connector(g, headingX + 10, headingY + 18, 320, 476);
        callout(g, 320, 476, 660, 112,
            "Chevron \u25C0 button (tooltip \"Hide maps sidebar\") sits at the trailing",
            "edge of the Maps heading; icon-only, same accent-coloured SVG pipeline",
            "as the toolbar icons. The checkable View \u2192 Maps sidebar item mirrors",
            "this state (checked = expanded).");
        return image;
    }

    private static BufferedImage collapsedPanel() {
        final BufferedImage image = new BufferedImage(1160, 700, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = graphics(image);
        title(g, 24, 26, "Collapsed \u2014 rail with restore control, label and active-map count");
        drawWindow(g, 20, 44, 1120, 620, true, SIDEBAR_DEFAULT, true);
        final int railChevronX = 20 + RAIL_WIDTH / 2;
        final int railChevronY = 44 + MENU_HEIGHT + TOOLBAR_HEIGHT + 18;
        connector(g, railChevronX, railChevronY, 90, 476);
        callout(g, 90, 476, 660, 128,
            "Collapsed state keeps a " + RAIL_WIDTH + " px rail: chevron \u25B6 (tooltip \"Show maps sidebar\"),",
            "vertical MAPS label, and the count of ACTIVE-partition maps (hidden at zero,",
            "updated on every row refresh). The divider is locked while collapsed, so the",
            "rail width is stable. Every Maps menu action stays available.");
        return image;
    }

    private static BufferedImage boundsPanel() {
        final BufferedImage image = new BufferedImage(1160, 700, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = graphics(image);
        title(g, 24, 26, "Resize bounds and the drag affordance");
        final int top = 78;
        final int graphWidth = 900;
        final int[] widths = { SIDEBAR_MIN, SIDEBAR_DEFAULT, graphWidth / 2 };
        final String[] labels = {
            "min " + SIDEBAR_MIN + " px",
            "default " + SIDEBAR_DEFAULT + " px (double-click resets here)",
            "max 50% of the graph area"
        };
        for (int i = 0; i < widths.length; i++) {
            final int y = top + i * 190;
            final int width = widths[i];
            g.setColor(SIDEBAR);
            g.fillRect(140, y, width, 150);
            g.setColor(EDGE);
            g.drawRect(140, y, width, 150);
            g.setColor(MUTED);
            g.setFont(BOLD_TINY);
            g.drawString("MAPS", 150, y + 18);
            for (int row = 0; row < 2; row++) {
                g.setColor(CHIP);
                g.fillRect(150, y + 26 + row * 22, Math.max(20, width - 20), 16);
            }
            g.setColor(CANVAS);
            g.fillRect(140 + width, y, 180, 150);
            g.setColor(new Color(0xE6, 0xE9, 0xEE));
            g.fillRect(140 + width, y, DIVIDER, 150);
            g.setColor(EDGE);
            g.drawRect(140 + width + DIVIDER, y, 180 - DIVIDER, 150);
            g.setColor(MUTED);
            g.setFont(SMALL);
            g.drawString("canvas", 140 + width + 16, y + 82);
            g.setColor(INK);
            g.setFont(BOLD_SMALL);
            g.drawString(labels[i], 140, y - 6);
            g.setColor(MARK);
            g.drawLine(140 + width + DIVIDER / 2, y - 2, 140 + width + DIVIDER / 2, y + 152);
        }
        g.setColor(MUTED);
        g.setFont(CAPTION);
        g.drawString("One workspace commit per drag gesture (on release); keyboard divider moves coalesce.", 140, 660);
        return image;
    }

    private static BufferedImage combinedMockup() {
        final BufferedImage image = new BufferedImage(1200, 1460, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = graphics(image);
        title(g, 24, 30, "Graph Workspace \u2014 Maps sidebar: adjustable width and hideable");
        drawWindow(g, 20, 56, 1160, 640, false, SIDEBAR_DEFAULT, true);
        caption(g, 20, 720, "Expanded: " + SIDEBAR_DEFAULT + " px sidebar, collapse chevron \u25C0 in the Maps heading, drag divider on the right edge.");
        drawWindow(g, 20, 770, 1160, 640, true, SIDEBAR_DEFAULT, true);
        caption(g, 20, 1434, "Collapsed: " + RAIL_WIDTH + " px rail with restore chevron \u25B6, vertical MAPS label and active-map count; divider locked.");
        return image;
    }

    // ---------------------------------------------------------------- drawing

    private static Graphics2D graphics(final BufferedImage image) {
        final Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        return g;
    }

    private static void title(final Graphics2D g, final int x, final int y, final String text) {
        g.setColor(INK);
        g.setFont(TITLE);
        g.drawString(text, x, y);
    }

    private static void caption(final Graphics2D g, final int x, final int y, final String text) {
        g.setColor(MUTED);
        g.setFont(CAPTION);
        g.drawString(text, x, y);
    }

    private static int sidebarRightOffset(final int sidebarWidth) {
        return sidebarWidth + DIVIDER;
    }

    private static void drawWindow(final Graphics2D g, final int x, final int y, final int width, final int height,
            final boolean collapsed, final int expandedWidth, final boolean highlightControls) {
        g.setColor(WINDOW);
        g.fillRect(x, y, width, height);
        g.setColor(EDGE);
        g.drawRect(x, y, width, height);

        // menu bar
        g.setColor(Color.WHITE);
        g.fillRect(x + 1, y + 1, width - 1, MENU_HEIGHT - 1);
        g.setColor(EDGE);
        g.drawLine(x + 1, y + MENU_HEIGHT, x + width - 1, y + MENU_HEIGHT);
        g.setColor(INK);
        g.setFont(BODY);
        int menuX = x + 16;
        for (final String item : Arrays.asList("File", "Edit", "View", "Maps")) {
            g.drawString(item, menuX, y + 19);
            menuX += g.getFontMetrics().stringWidth(item) + 22;
        }

        // toolbar
        final int toolbarY = y + MENU_HEIGHT;
        g.setColor(TOOLBAR);
        g.fillRect(x + 1, toolbarY, width - 1, TOOLBAR_HEIGHT);
        g.setColor(EDGE);
        g.drawLine(x + 1, toolbarY + TOOLBAR_HEIGHT, x + width - 1, toolbarY + TOOLBAR_HEIGHT);
        int buttonX = x + 10;
        buttonX = toolbarButton(g, buttonX, toolbarY + 8, "Open", 52);
        buttonX = toolbarButton(g, buttonX, toolbarY + 8, "Save", 52);
        buttonX = toolbarButton(g, buttonX, toolbarY + 8, "\u21B6", 30);
        buttonX = toolbarButton(g, buttonX, toolbarY + 8, "\u21B7", 30);
        buttonX = toolbarButton(g, buttonX, toolbarY + 8, "Select", 58);
        buttonX = toolbarButton(g, buttonX, toolbarY + 8, "Connect", 70);
        buttonX = toolbarButton(g, buttonX, toolbarY + 8, "Forward \u25BE", 92);
        toolbarButton(g, buttonX, toolbarY + 8, "Search nodes and maps", 230);

        // content
        final int contentY = toolbarY + TOOLBAR_HEIGHT;
        final int contentHeight = height - MENU_HEIGHT - TOOLBAR_HEIGHT - STATUS_HEIGHT;
        final int graphAreaWidth = width - 2;
        final int graphAreaX = x + 1;
        final int canvasX;
        if (collapsed) {
            drawRail(g, graphAreaX, contentY, contentHeight);
            canvasX = graphAreaX + RAIL_WIDTH;
        }
        else {
            drawSidebar(g, graphAreaX, contentY, expandedWidth, contentHeight, highlightControls);
            g.setColor(new Color(0xE6, 0xE9, 0xEE));
            g.fillRect(graphAreaX + expandedWidth, contentY, DIVIDER, contentHeight);
            g.setColor(EDGE);
            g.drawLine(graphAreaX + expandedWidth + DIVIDER / 2, contentY,
                graphAreaX + expandedWidth + DIVIDER / 2, contentY + contentHeight);
            canvasX = graphAreaX + expandedWidth + DIVIDER;
        }
        final int settingsX = graphAreaX + graphAreaWidth - SETTINGS_WIDTH;
        drawCanvas(g, canvasX, contentY, settingsX - canvasX, contentHeight);
        drawSettings(g, settingsX, contentY, SETTINGS_WIDTH, contentHeight);

        // status bar
        final int statusY = contentY + contentHeight;
        g.setColor(Color.WHITE);
        g.fillRect(x + 1, statusY, width - 1, STATUS_HEIGHT - 1);
        g.setColor(EDGE);
        g.drawLine(x + 1, statusY, x + width - 1, statusY);
        g.setColor(MUTED);
        g.setFont(SMALL);
        g.drawString("Maps: Axiomatic_Set_Theory.mm Available. Point-set Topology.mm Inactive  "
            + "Nodes 5 / Edges 2   Selected: none   Layout: Idle   Recoverable 0 / Missing 0",
            x + 12, statusY + 19);
    }

    private static int toolbarButton(final Graphics2D g, final int x, final int y, final String text,
            final int width) {
        g.setColor(Color.WHITE);
        g.fillRect(x, y, width, 26);
        g.setColor(EDGE);
        g.drawRect(x, y, width, 26);
        g.setColor(INK);
        g.setFont(SMALL);
        final FontMetrics metrics = g.getFontMetrics();
        g.drawString(text, x + 6, y + 17);
        return x + width + 6;
    }

    private static void drawSidebar(final Graphics2D g, final int x, final int y, final int width,
            final int height, final boolean highlightControls) {
        g.setColor(SIDEBAR);
        g.fillRect(x, y, width, height);
        g.setColor(INK);
        g.setFont(BODY.deriveFont(Font.BOLD, 12f));
        g.drawString("Maps", x + 10, y + 22);

        final int buttonSize = 18;
        final int buttonX = x + width - buttonSize - 8;
        final int buttonY = y + 7;
        g.setColor(Color.WHITE);
        g.fillRoundRect(buttonX, buttonY, buttonSize, buttonSize, 4, 4);
        g.setColor(EDGE);
        g.drawRoundRect(buttonX, buttonY, buttonSize, buttonSize, 4, 4);
        g.setColor(INK);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        g.drawString("\u25C0", buttonX + 5, buttonY + 13);
        if (highlightControls) {
            g.setColor(MARK);
            g.drawRoundRect(buttonX - 3, buttonY - 3, buttonSize + 6, buttonSize + 6, 6, 6);
        }

        g.setColor(MUTED);
        g.setFont(BOLD_TINY);
        g.drawString("ACTIVE (1)", x + 10, y + 46);
        drawRow(g, x + 6, y + 52, width - 12, "Axiomatic_Set_...", "Active", "5 nodes", ACCENT);

        g.setColor(MUTED);
        g.setFont(BOLD_TINY);
        g.drawString("INACTIVE (1)", x + 10, y + 118);
        drawRow(g, x + 6, y + 124, width - 12, "Point-set_Top...", "Inactive", "0 nodes",
            new Color(0x9B, 0x9B, 0x9B));

        g.setColor(CHIP);
        g.fillRect(x, y + height - 62, width, 62);
        final int gridX = x + 6;
        final int gridY = y + height - 58;
        final int cell = (width - 16) / 2;
        actionButton(g, gridX, gridY, cell, "Add Map");
        actionButton(g, gridX + cell + 4, gridY, cell, "Deactiva\u2026");
        actionButton(g, gridX, gridY + 26, cell, "Retry Map");
        actionButton(g, gridX + cell + 4, gridY + 26, cell, "Locate M\u2026");
    }

    private static void drawRow(final Graphics2D g, final int x, final int y, final int width, final String name,
            final String status, final String count, final Color chipColor) {
        g.setColor(Color.WHITE);
        g.fillRect(x, y, width, ROW_HEIGHT - 4);
        g.setColor(EDGE);
        g.drawRect(x, y, width, ROW_HEIGHT - 4);
        g.setColor(chipColor);
        g.fillRect(x + 6, y + 8, 5, ROW_HEIGHT - 20);
        g.setColor(INK);
        g.setFont(SMALL);
        g.drawString(name, x + 18, y + 20);
        g.setColor(MUTED);
        g.setFont(TINY);
        g.drawString(status, x + 18, y + 36);
        g.setColor(MUTED);
        g.setFont(BOLD_TINY);
        final FontMetrics metrics = g.getFontMetrics();
        g.drawString(count, x + width - metrics.stringWidth(count) - 8, y + 20);
    }

    private static void actionButton(final Graphics2D g, final int x, final int y, final int width,
            final String text) {
        g.setColor(Color.WHITE);
        g.fillRect(x, y, width, 22);
        g.setColor(EDGE);
        g.drawRect(x, y, width, 22);
        g.setColor(INK);
        g.setFont(SMALL);
        g.drawString(text, x + 6, y + 15);
    }

    private static void drawRail(final Graphics2D g, final int x, final int y, final int height) {
        g.setColor(RAIL);
        g.fillRect(x, y, RAIL_WIDTH, height);
        g.setColor(EDGE);
        g.drawRect(x, y, RAIL_WIDTH, height);

        final int buttonSize = 18;
        final int buttonX = x + (RAIL_WIDTH - buttonSize) / 2;
        final int buttonY = y + 8;
        g.setColor(Color.WHITE);
        g.fillRoundRect(buttonX, buttonY, buttonSize, buttonSize, 4, 4);
        g.setColor(EDGE);
        g.drawRoundRect(buttonX, buttonY, buttonSize, buttonSize, 4, 4);
        g.setColor(INK);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        g.drawString("\u25B6", buttonX + 5, buttonY + 13);

        final int badgeY = buttonY + buttonSize + 8;
        g.setColor(ACCENT);
        g.fillRoundRect(x + 5, badgeY, RAIL_WIDTH - 10, 16, 8, 8);
        g.setColor(Color.WHITE);
        g.setFont(BOLD_TINY);
        final String count = "3";
        g.drawString(count, x + (RAIL_WIDTH - g.getFontMetrics().stringWidth(count)) / 2, badgeY + 12);

        final AffineTransformState state = new AffineTransformState(g);
        g.translate(x + RAIL_WIDTH - 7, badgeY + 34);
        g.rotate(Math.toRadians(90));
        g.setColor(MUTED);
        g.setFont(BOLD_TINY);
        g.drawString("MAPS", 0, 0);
        state.restore(g);
    }

    /** Small helper so the rail can rotate its label without leaking the transform. */
    private static final class AffineTransformState {
        private final java.awt.geom.AffineTransform original;

        private AffineTransformState(final Graphics2D g) {
            original = g.getTransform();
        }

        private void restore(final Graphics2D g) {
            g.setTransform(original);
        }
    }

    private static void drawCanvas(final Graphics2D g, final int x, final int y, final int width,
            final int height) {
        g.setColor(CANVAS);
        g.fillRect(x, y, width, height);
        final int blobWidth = Math.min(width - 60, 560);
        final int blobHeight = 150;
        final int blobX = x + (width - blobWidth) / 2;
        final int blobY = y + height / 2 - blobHeight / 2;
        final Path2D shape = new Path2D.Double();
        shape.moveTo(blobX + 40, blobY);
        shape.lineTo(blobX + blobWidth - 40, blobY);
        shape.lineTo(blobX + blobWidth, blobY + blobHeight / 2);
        shape.lineTo(blobX + blobWidth - 40, blobY + blobHeight);
        shape.lineTo(blobX + 40, blobY + blobHeight);
        shape.lineTo(blobX, blobY + blobHeight / 2);
        shape.closePath();
        g.setColor(BLOB_FILL);
        g.fill(shape);
        g.setColor(BLOB_EDGE);
        g.setStroke(new BasicStroke(2.5f));
        g.draw(shape);
        g.setStroke(new BasicStroke(1f));
        g.setColor(INK);
        g.setFont(SMALL);
        g.drawString("ZFC - Axiomatic_Set_Theory", blobX + 120, blobY + 60);
        g.setColor(MUTED);
        g.setFont(TINY);
        g.drawString("Basic Definitions and Theorems", blobX + 40, blobY + 110);
        g.drawString("Axiom of Choice", blobX + 300, blobY + 40);
        g.drawString("Replacement Scheme", blobX + 420, blobY + 40);
        g.setColor(new Color(0x9A, 0xA4, 0xB0));
        for (final int[] point : new int[][] {
            { blobX + 150, blobY + 90 }, { blobX + 220, blobY + 100 }, { blobX + 310, blobY + 78 } }) {
            g.setColor(new Color(0xEA, 0xF2, 0xFA));
            g.fillOval(point[0], point[1], 14, 14);
            g.setColor(new Color(0x2F, 0x5C, 0x8A));
            g.drawOval(point[0], point[1], 14, 14);
        }
        g.setColor(CHIP);
        g.fillRect(x, y, width, 26);
        g.setColor(EDGE);
        g.drawRect(x, y, width, 26);
        g.setColor(MUTED);
        g.setFont(SMALL);
        g.drawString("canvas", x + 10, y + 18);
    }

    private static void drawSettings(final Graphics2D g, final int x, final int y, final int width,
            final int height) {
        g.setColor(SETTINGS);
        g.fillRect(x, y, width, height);
        g.setColor(EDGE);
        g.drawRect(x, y, width, height);
        g.setColor(INK);
        g.setFont(BODY.deriveFont(Font.BOLD, 12f));
        g.drawString("Display", x + 12, y + 26);
        g.setFont(BODY);
        checkbox(g, x + 12, y + 52, "Show arrowheads", true);
        g.setColor(INK);
        g.drawString("Canvas theme", x + 12, y + 150);
        g.setColor(Color.WHITE);
        g.fillRect(x + 12, y + 160, width - 24, 24);
        g.setColor(EDGE);
        g.drawRect(x + 12, y + 160, width - 24, 24);
        g.setColor(INK);
        g.setFont(SMALL);
        g.drawString("Follow Freeplane \u25BE", x + 18, y + 176);
        checkbox(g, x + 12, y + 300, "Remember viewport", true);
        checkbox(g, x + 12, y + 470, "Dim unrelated nodes", true);
    }

    private static void checkbox(final Graphics2D g, final int x, final int y, final String label,
            final boolean checked) {
        g.setColor(Color.WHITE);
        g.fillRect(x, y - 12, 14, 14);
        g.setColor(EDGE);
        g.drawRect(x, y - 12, 14, 14);
        if (checked) {
            g.setColor(INK);
            g.setStroke(new BasicStroke(2f));
            g.drawLine(x + 3, y - 6, x + 6, y - 3);
            g.drawLine(x + 6, y - 3, x + 11, y - 9);
            g.setStroke(new BasicStroke(1f));
        }
        g.setColor(INK);
        g.setFont(BODY);
        g.drawString(label, x + 22, y);
    }

    private static void connector(final Graphics2D g, final int fromX, final int fromY, final int toX,
            final int toY) {
        g.setColor(MARK);
        g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[] { 5f, 4f }, 0f));
        g.drawLine(fromX, fromY, toX, toY);
        g.setStroke(new BasicStroke(1f));
        g.fillOval(fromX - 3, fromY - 3, 6, 6);
    }

    private static void callout(final Graphics2D g, final int x, final int y, final int width, final int height,
            final String... lines) {
        g.setColor(new Color(0xFF, 0xF7, 0xE3));
        g.fill(new RoundRectangle2D.Double(x, y, width, height, 10, 10));
        g.setColor(new Color(0xE0, 0xA0, 0x4A));
        g.draw(new RoundRectangle2D.Double(x, y, width, height, 10, 10));
        g.setColor(INK);
        g.setFont(SMALL);
        int lineY = y + 20;
        for (final String line : lines) {
            g.drawString(line, x + 12, lineY);
            lineY += 17;
        }
    }
}
