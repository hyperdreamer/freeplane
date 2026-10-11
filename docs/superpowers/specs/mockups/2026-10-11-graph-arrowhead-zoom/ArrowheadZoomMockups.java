import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Renders the arrowhead sizing rules at the zoom levels of the reported workspaces,
 * using the exact triangle geometry of GraphPainter.paintArrow, the light-canvas palette
 * (edgeColor 73,84,100, nodeFill 229,239,249, nodeStroke 38,91,137 over the reported
 * background 238,238,238), and the real paint order (edges and heads first, node fill and
 * ring afterwards).
 *
 * Every panel contains the same world-space scene (a hub node of radius 14 world units
 * with six long incoming edges), so panels differ only by zoom and by the sizing rule.
 * Edges are long enough that the cap stays unbound and each edge carries one head, so the
 * renderer exercises the rule length only, not the cap or the bidirectional split.
 * Panels are magnified uniformly by the final image scale, so head : node ratios are exact.
 *
 *   current  max(12.5, 8.68 x z) px                 (shipped)
 *   A        8.68 x z px                            (0.62 x max node radius, world constant)
 *   B        12.5 x z px                            (today's 1x proportion, world constant)
 *
 * Regenerate: java ArrowheadZoomMockups.java png
 */
public final class ArrowheadZoomMockups {

    static final Color BG = new Color(238, 238, 238);
    static final Color EDGE = new Color(73, 84, 100);
    static final Color NODE_FILL = new Color(229, 239, 249);
    static final Color NODE_STROKE = new Color(38, 91, 137);
    static final Color TEXT = new Color(40, 40, 40);

    // GraphGeometryEngine.BASE_RADIUS 8.0 x NodeProminence.MAX_SCALE 1.75 = 14.0 world.
    static final double MAX_NODE_RADIUS_WORLD = 14.0;
    static final double EDGE_STROKE_WORLD = 1.4;
    static final double GROWTH_WORLD_LENGTH = 0.62 * MAX_NODE_RADIUS_WORLD; // 8.68
    static final double FLOOR_PX = 12.5;
    static final double HALF_BASE_RATIO = 0.42;

    static final double[] ZOOMS = { 0.1485, 0.378, 0.75, 1.0, 2.798 };
    static final String[] MODES = { "current", "A", "B" };

    static final int PANEL_W = 168;
    static final int PANEL_H = 150;
    static final int GAP = 10;
    static final int LABEL_H = 26;
    static final int GUTTER = 132;
    static final int TITLE_H = 30;

    public static void main(final String[] args) throws Exception {
        final File out = new File(args.length > 0 ? args[0] : "png");
        final File rejected = new File(out, "rejected");
        out.mkdirs();
        rejected.mkdirs();
        write(renderAll(), new File(out, "zoom-rule-comparison.png"));
        write(renderRow("B"), new File(out, "chosen-rule-b.png"));
        write(renderRow("current"), new File(out, "before-shipped-rule.png"));
        write(renderRow("A"), new File(rejected, "rule-a-smaller-heads.png"));
        System.out.println("rendered into " + out.getAbsolutePath());
    }

    /** Head length in screen px for a long edge (cap unbound). */
    static double headLengthPx(final double zoom, final String mode) {
        if ("current".equals(mode)) {
            return Math.max(FLOOR_PX, GROWTH_WORLD_LENGTH * zoom);
        }
        if ("A".equals(mode)) {
            return GROWTH_WORLD_LENGTH * zoom;
        }
        if ("B".equals(mode)) {
            return FLOOR_PX * zoom;
        }
        throw new IllegalArgumentException(mode);
    }

    static String modeLabel(final String mode) {
        if ("current".equals(mode)) {
            return "shipped | max(12.5, 8.68 x z) px";
        }
        if ("A".equals(mode)) {
            return "A | 0.62 x max node radius | 8.68 x z px";
        }
        return "B (chosen) | today's 1x proportion | 12.5 x z px";
    }

    static BufferedImage renderAll() {
        final int width = GUTTER + GAP + ZOOMS.length * (PANEL_W + GAP);
        final int height = TITLE_H + 3 * (PANEL_H + LABEL_H);
        final BufferedImage image = new BufferedImage(width * 2, height * 2, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.scale(2.0, 2.0);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.setColor(TEXT);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            g.drawString("Arrowhead size at workspace zooms (identical world scene per panel)",
                GAP, 20);
            int y = TITLE_H;
            for (final String mode : MODES) {
                paintRow(g, mode, y);
                y += PANEL_H + LABEL_H;
            }
        }
        finally {
            g.dispose();
        }
        return image;
    }

    static BufferedImage renderRow(final String mode) {
        final int width = GUTTER + GAP + ZOOMS.length * (PANEL_W + GAP);
        final int height = TITLE_H + PANEL_H + LABEL_H;
        final BufferedImage image = new BufferedImage(width * 3, height * 3, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.scale(3.0, 3.0);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.setColor(TEXT);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            g.drawString(modeLabel(mode), GAP, 18);
            paintRow(g, mode, TITLE_H);
        }
        finally {
            g.dispose();
        }
        return image;
    }

    static void paintRow(final Graphics2D g, final String mode, final int top) {
        g.setColor(TEXT);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        int line = 0;
        for (final String part : modeLabel(mode).split(" \\| ")) {
            g.drawString(part, GAP, top + 18 + line * 14);
            line++;
        }
        int x = GUTTER;
        for (final double zoom : ZOOMS) {
            paintPanel(g, x, top, zoom, mode);
            x += PANEL_W + GAP;
        }
    }

    static void paintPanel(final Graphics2D g, final int left, final int top, final double zoom,
            final String mode) {
        g.setColor(BG);
        g.fillRect(left, top, PANEL_W, PANEL_H);
        final java.awt.Shape oldClip = g.getClip();
        g.clipRect(left, top, PANEL_W, PANEL_H);

        final double cx = left + PANEL_W * 0.5;
        final double cy = top + PANEL_H * 0.55;
        final double radius = MAX_NODE_RADIUS_WORLD * zoom;
        final double reach = 4000.0; // world units, so every edge enters the panel; cap stays unbound

        final float stroke = (float) Math.max(0.1, EDGE_STROKE_WORLD * zoom);
        g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 6; i++) {
            final double angle = Math.PI * 2.0 * i / 6.0 + 0.35;
            final double fromX = cx + Math.cos(angle) * reach * zoom;
            final double fromY = cy + Math.sin(angle) * reach * zoom;
            final double dx = cx - fromX;
            final double dy = cy - fromY;
            final double length = Math.hypot(dx, dy);
            final double tipX = cx - dx / length * radius;
            final double tipY = cy - dy / length * radius;
            g.setColor(EDGE);
            g.draw(new Line2D.Double(fromX, fromY, tipX, tipY));
            paintHead(g, tipX, tipY, fromX, fromY, zoom, mode);
        }
        g.setColor(NODE_FILL);
        g.fill(new Ellipse2D.Double(cx - radius, cy - radius, radius * 2.0, radius * 2.0));
        g.setColor(NODE_STROKE);
        g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Ellipse2D.Double(cx - radius, cy - radius, radius * 2.0, radius * 2.0));

        g.setColor(new Color(255, 255, 255, 220));
        g.fillRect(left + 2, top + 2, 96, 58);
        g.setColor(TEXT);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        g.drawString(String.format("z=%.4f", zoom), left + 6, top + 14);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
        g.drawString(String.format("head %.2f px", headLengthPx(zoom, mode)), left + 6, top + 26);
        g.drawString(String.format("node d %.1f px", radius * 2.0), left + 6, top + 37);
        g.drawString(String.format("ratio %.2f", headLengthPx(zoom, mode) / (radius * 2.0)),
            left + 6, top + 48);
        g.setClip(oldClip);
    }

    /** Mirrors GraphPainter.paintArrow for a one-headed long edge, in panel (screen) px. */
    static void paintHead(final Graphics2D g, final double tipX, final double tipY, final double fromX,
            final double fromY, final double zoom, final String mode) {
        final double dx = tipX - fromX;
        final double dy = tipY - fromY;
        final double length = Math.hypot(dx, dy);
        if (!(length > 0.0)) {
            return;
        }
        final double ux = dx / length;
        final double uy = dy / length;
        // The panel is drawn in screen px, so the rule's screen length and half-base are used as-is.
        final double arrowLength = headLengthPx(zoom, mode);
        final double arrowWidth = HALF_BASE_RATIO * arrowLength;
        final Path2D.Double arrow = new Path2D.Double();
        arrow.moveTo(tipX, tipY);
        arrow.lineTo(tipX - ux * arrowLength - uy * arrowWidth, tipY - uy * arrowLength + ux * arrowWidth);
        arrow.lineTo(tipX - ux * arrowLength + uy * arrowWidth, tipY - uy * arrowLength - ux * arrowWidth);
        arrow.closePath();
        g.setColor(EDGE);
        g.fill(arrow);
    }

    static void write(final BufferedImage image, final File file) throws Exception {
        ImageIO.write(image, "png", file);
    }

    private ArrowheadZoomMockups() {
    }
}
