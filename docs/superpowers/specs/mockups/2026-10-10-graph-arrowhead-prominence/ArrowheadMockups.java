import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Renders candidate directional-arrowhead treatments using the exact geometry and colors of
 * GraphPainter.paintArrow / GraphTheme (light + dark), including the real paint order
 * (edges and arrowheads first, node fill + ring painted over them afterwards).
 *
 * Screen-space scene: node radius 14 world units at zoom 1.6 => 22.4 px, matching the
 * reported screenshot; arrowheads are drawn at a constant screen size (length / zoom).
 */
public final class ArrowheadMockups {

    // GraphTheme.lightTheme
    static final Color L_BG = new Color(250, 251, 253);
    static final Color L_EDGE = new Color(73, 84, 100);
    static final Color L_NODE_FILL = new Color(229, 239, 249);
    static final Color L_NODE_STROKE = new Color(38, 91, 137);
    static final Color L_EMPHASIS = new Color(45, 54, 68);
    // GraphTheme.darkTheme
    static final Color D_BG = new Color(31, 35, 42);
    static final Color D_EDGE = new Color(190, 202, 217);
    static final Color D_NODE_FILL = new Color(57, 76, 98);
    static final Color D_NODE_STROKE = new Color(142, 194, 239);
    static final Color D_EMPHASIS = new Color(232, 240, 250);

    static final double ZOOM = 1.6;
    static final double NODE_RADIUS_WORLD = 14.0;
    static final float EDGE_STROKE = 1.4f;
    static final float HALO_STROKE = 2.2f;

    static final int W = 440;
    static final int H = 260;
    static final int SCALE = 2;

    enum Shape { TRIANGLE, STEALTH }

    record Variant(String id, String label, double length, double halfWidth, Shape shape,
                   boolean halo, boolean emphasis, String note) {
    }

    static final Variant[] VARIANTS = {
        new Variant("before", "A. Current", 8.0, 3.5, Shape.TRIANGLE, false, false,
            "length 8 px, base 7 px, edge colour, no separation"),
        new Variant("bigger", "B. Larger only", 12.5, 5.25, Shape.TRIANGLE, false, false,
            "length 12.5 px, base 10.5 px, same edge colour"),
        new Variant("halo", "C. Larger + background halo", 12.5, 5.25, Shape.TRIANGLE, true, false,
            "larger head outlined in the canvas colour so it never merges with the line"),
        new Variant("halo-emphasis", "D. Larger + halo + emphasis colour", 13.0, 5.5, Shape.TRIANGLE, true, true,
            "larger head, canvas-colour halo, head filled in a stronger ink than the edge line"),
        new Variant("stealth", "E. Larger stealth head + halo", 14.0, 6.0, Shape.STEALTH, true, false,
            "concave back edge, reads as a directional pointer rather than a wedge"),
    };

    // magnified detail window around both arrow tips
    static final double CROP_X = 286;
    static final double CROP_Y = 84;
    static final double CROP = 72;
    static final int DETAIL_SCALE = 4;

    public static void main(final String[] args) throws Exception {
        final File out = new File(args[0]);
        out.mkdirs();
        for (final Variant variant : VARIANTS) {
            write(render(variant, false), new File(out, variant.id() + "-light.png"));
            write(render(variant, true), new File(out, variant.id() + "-dark.png"));
            write(renderDetail(variant, false), new File(out, variant.id() + "-detail-light.png"));
            write(renderDetail(variant, true), new File(out, variant.id() + "-detail-dark.png"));
        }
        // zoom ladder for the baseline and for variant C, to reason about scale behaviour
        for (final double zoom : new double[] { 0.6, 1.6 }) {
            write(renderScale(VARIANTS[0], false, zoom), new File(out, "scale-before-" + tag(zoom) + ".png"));
            write(renderScale(VARIANTS[2], false, zoom), new File(out, "scale-halo-" + tag(zoom) + ".png"));
        }
        renderZoomComparison(out);
        System.out.println("rendered into " + out);
    }

    static String tag(final double zoom) {
        return String.valueOf(zoom).replace('.', 'p');
    }

    /** Head length in screen px per sizing mode. */
    static double headLengthPx(final double zoom, final String mode) {
        final double constant = 12.5;
        if ("hybrid".equals(mode)) {
            return Math.max(constant, 0.62 * NODE_RADIUS_WORLD * zoom);
        }
        if ("uniform".equals(mode)) {
            return Math.max(constant, UNIFORM_WORLD_LENGTH * zoom);
        }
        return constant;
    }

    /** World length at which a head matches 0.62 x a maximum-prominence node radius. */
    static final double UNIFORM_WORLD_LENGTH = 0.62 * NODE_RADIUS_WORLD;

    static void renderZoomComparison(final File out) throws Exception {
        for (final double zoom : new double[] { 0.6, 1.6, 3.0 }) {
            for (final String mode : new String[] { "constant", "uniform", "hybrid" }) {
                final double length = headLengthPx(zoom, mode);
                final Variant variant = new Variant("zoom-" + mode, "", length,
                    length * 0.42, Shape.TRIANGLE, true, false, "");
                final BufferedImage image = new BufferedImage(ZW * ZS, ZH * ZS, BufferedImage.TYPE_INT_RGB);
                final Graphics2D g = image.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                g.scale(ZS, ZS);
                paintZoomPanel(g, zoom, variant);
                g.dispose();
                write(image, new File(out, "zoom-" + mode + "-" + tag(zoom) + ".png"));
            }
        }
    }

    static final int ZW = 300;
    static final int ZH = 220;
    static final int ZS = 2;

    /** One node with a horizontal incoming edge at the given zoom, in screen pixels. */
    static void paintZoomPanel(final Graphics2D g, final double zoom, final Variant variant) {
        g.setColor(L_BG);
        g.fillRect(0, 0, ZW, ZH);
        g.setColor(L_EDGE);
        g.setStroke(new BasicStroke(EDGE_STROKE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        final double radius = NODE_RADIUS_WORLD * zoom;
        final double cx = 205;
        final double cy = 110;
        final double tipX = cx - radius;
        g.draw(new Line2D.Double(12, cy, tipX, cy));
        paintArrow(g, tipX, cy, 12, cy, L_EDGE, L_BG, variant);
        g.setColor(L_NODE_FILL);
        g.fill(new Ellipse2D.Double(cx - radius, cy - radius, radius * 2.0, radius * 2.0));
        g.setColor(L_NODE_STROKE);
        g.setStroke(new BasicStroke(EDGE_STROKE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Ellipse2D.Double(cx - radius, cy - radius, radius * 2.0, radius * 2.0));
    }

    static void write(final BufferedImage image, final File file) throws Exception {
        ImageIO.write(image, "png", file);
    }

    static BufferedImage render(final Variant variant, final boolean dark) {
        return renderScale(variant, dark, ZOOM);
    }

    static BufferedImage renderDetail(final Variant variant, final boolean dark) {
        final int size = (int) Math.round(CROP * DETAIL_SCALE);
        final BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.translate(-CROP_X * DETAIL_SCALE, -CROP_Y * DETAIL_SCALE);
            g.scale(DETAIL_SCALE, DETAIL_SCALE);
            paintScene(g, variant, dark, ZOOM);
        }
        finally {
            g.dispose();
        }
        return image;
    }

    static BufferedImage renderScale(final Variant variant, final boolean dark, final double zoom) {
        final BufferedImage image = new BufferedImage(W * SCALE, H * SCALE, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.scale(SCALE, SCALE);
            paintScene(g, variant, dark, zoom);
        }
        finally {
            g.dispose();
        }
        return image;
    }

    static void paintScene(final Graphics2D g, final Variant variant, final boolean dark, final double zoom) {
        final Color background = dark ? D_BG : L_BG;
        final Color edgeColor = dark ? D_EDGE : L_EDGE;
        final Color nodeFill = dark ? D_NODE_FILL : L_NODE_FILL;
        final Color nodeStroke = dark ? D_NODE_STROKE : L_NODE_STROKE;
        final Color headColor = variant.emphasis() ? (dark ? D_EMPHASIS : L_EMPHASIS) : edgeColor;

        g.setColor(background);
        g.fillRect(0, 0, W, H);

        final double radius = NODE_RADIUS_WORLD * zoom;
        final double cx = 330;
        final double cy = 118;
        final double[][] sources = { { 40, 118 }, { 26, 246 } };

        // edges first (GraphPainter paints hulls -> edges -> nodes)
        g.setColor(edgeColor);
        g.setStroke(new BasicStroke(EDGE_STROKE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (final double[] source : sources) {
            final double[] tip = attachment(source[0], source[1], cx, cy, radius);
            g.draw(new Line2D.Double(source[0], source[1], tip[0], tip[1]));
        }
        // arrowheads, tips on the node boundary
        for (final double[] source : sources) {
            final double[] tip = attachment(source[0], source[1], cx, cy, radius);
            paintArrow(g, tip[0], tip[1], source[0], source[1], headColor, background, variant);
        }
        // nodes afterwards, exactly like the real paint order
        g.setColor(nodeFill);
        g.fill(new Ellipse2D.Double(cx - radius, cy - radius, radius * 2.0, radius * 2.0));
        g.setColor(nodeStroke);
        g.setStroke(new BasicStroke(EDGE_STROKE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Ellipse2D.Double(cx - radius, cy - radius, radius * 2.0, radius * 2.0));
    }

    static double[] attachment(final double fromX, final double fromY, final double cx, final double cy,
            final double radius) {
        final double dx = cx - fromX;
        final double dy = cy - fromY;
        final double length = Math.sqrt(dx * dx + dy * dy);
        return new double[] { cx - dx / length * radius, cy - dy / length * radius };
    }

    /** Mirrors GraphPainter.paintArrow, plus optional halo and stealth back edge. */
    static void paintArrow(final Graphics2D g, final double tipX, final double tipY, final double fromX,
            final double fromY, final Color color, final Color background, final Variant variant) {
        final double dx = tipX - fromX;
        final double dy = tipY - fromY;
        final double length = Math.sqrt(dx * dx + dy * dy);
        if (!(length > 0.0)) {
            return;
        }
        final double ux = dx / length;
        final double uy = dy / length;
        final double px = -uy;
        final double py = ux;
        final double arrowLength = variant.length();
        final double arrowWidth = variant.halfWidth();
        final Path2D.Double arrow = new Path2D.Double();
        if (variant.shape() == Shape.STEALTH) {
            final double notchX = tipX - ux * arrowLength * 0.62;
            final double notchY = tipY - uy * arrowLength * 0.62;
            arrow.moveTo(tipX, tipY);
            arrow.lineTo(tipX - ux * arrowLength + px * arrowWidth, tipY - uy * arrowLength + py * arrowWidth);
            arrow.lineTo(notchX, notchY);
            arrow.lineTo(tipX - ux * arrowLength - px * arrowWidth, tipY - uy * arrowLength - py * arrowWidth);
        }
        else {
            arrow.moveTo(tipX, tipY);
            arrow.lineTo(tipX - ux * arrowLength + px * arrowWidth, tipY - uy * arrowLength + py * arrowWidth);
            arrow.lineTo(tipX - ux * arrowLength - px * arrowWidth, tipY - uy * arrowLength - py * arrowWidth);
        }
        arrow.closePath();
        if (variant.halo()) {
            g.setColor(background);
            g.setStroke(new BasicStroke(HALO_STROKE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(arrow);
        }
        g.setColor(color);
        g.fill(arrow);
    }
}
