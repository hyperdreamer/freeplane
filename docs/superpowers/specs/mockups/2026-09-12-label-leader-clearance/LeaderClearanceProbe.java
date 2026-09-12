import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Design-time measurement probe for the label / leader-line clearance change.
 *
 * Questions answered with real rasterization (no hand numbers):
 *
 *  Q1  How far does the painted leader ink extend beyond its geometric endpoint?
 *      The painter reuses theme.edgeStroke() = BasicStroke(1.4, CAP_ROUND, JOIN_ROUND)
 *      (GraphTheme.java:59) and draws the leader with it (GraphPainter.paintLabels).
 *  Q2  Is the leader stroke zoom-compensated?  No: it is applied under the world
 *      transform (GraphPainter.paint -> copy.transform(worldTransform) -> scale(zoom)),
 *      so its painted screen width is 1.4 * zoom, unlike the label font, which is
 *      pre-divided by zoom two lines above.
 *  Q3  For a screen-space clearance GAP in {1, 2, 3} px, does any leader ink land
 *      inside the label rectangle at zoom 0.25 / 1 / 2 / 4, with the stroke as shipped
 *      and with a zoom-compensated stroke?
 *
 * Usage: java LeaderClearanceProbe
 */
public final class LeaderClearanceProbe {

    static final float STROKE_WIDTH = 1.4f;
    static final int WIDTH = 520;
    static final int HEIGHT = 260;

    /** screen-space fixture: leader ends at END_X, label rectangle starts GAP px to the right. */
    static final double END_X = 200.0;
    static final double CENTER_Y = 130.0;
    static final double LABEL_WIDTH = 90.0;
    static final double LABEL_HEIGHT = 14.0;

    public static void main(final String[] args) throws Exception {
        System.out.println("stroke = BasicStroke(" + STROKE_WIDTH
            + ", CAP_ROUND, JOIN_ROUND), GraphTheme.java:59");
        System.out.println();
        System.out.printf("%6s %10s %10s %10s %12s %10s %10s%n", "zoom", "strokeMode", "paintedPx",
            "overhang", "minInkDistance", "insideRect", "expected");
        for (final double zoom : new double[] { 0.25, 1.0, 2.0, 4.0 }) {
            for (final boolean compensated : new boolean[] { false, true }) {
                for (final double gap : new double[] { 0.0, 1.0, 2.0, 3.0, 4.0 }) {
                    final Result result = measure(zoom, compensated, gap);
                    System.out.printf("%6.2f %10s %10.3f %10.3f %12.3f %10d %10.1f%n", zoom,
                        compensated ? "1.4/zoom" : "1.4", result.paintedWidth, result.overhang,
                        result.minInkDistance, result.inkInsideRect, gap);
                }
            }
        }
        System.out.println();
        System.out.println("glyph ink overhang beyond the logical 12 pt box, per side, "
            + "production centring (GraphPainter.drawCentered)");
        System.out.printf("%52s %9s %9s %9s %9s%n", "text", "left", "right", "top", "bottom");
        for (final String text : FIXTURE_TEXTS) {
            final double[] overhang = glyphOverhang(text);
            System.out.printf("%52s %9.4f %9.4f %9.4f %9.4f%n", text, overhang[0], overhang[1],
                overhang[2], overhang[3]);
        }
    }

    static final String[] FIXTURE_TEXTS = {
        "Axiom of Choice", "Theorem", "Power Set", "Replacement Scheme",
        "Foundation / Regularity", "Extensionality", "ZFC", "Axioms",
        "Basic Definitions and Theorems",
        "Well-Ordering Theorem of Choice and Regularity", "Group",
        "Transfinite Induction over Ordinal Numbers", "Zermelo"
    };

    /** {left, right, top, bottom} ink overhang beyond the logical box, in px at 1:1. */
    static double[] glyphOverhang(final String text) {
        final Font font = new Font(Font.DIALOG, Font.PLAIN, 12);
        final Rectangle2D size = font.getStringBounds(text, new FontRenderContext(null, true, true));
        final double anchorX = 200.0;
        final double anchorY = 100.0;
        final Rectangle2D rect = new Rectangle2D.Double(anchorX - size.getWidth() / 2.0,
            anchorY - size.getHeight() / 2.0, size.getWidth(), size.getHeight());
        final BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(font);
        g.setColor(Color.BLACK);
        final LineMetrics line = font.getLineMetrics(text, g.getFontRenderContext());
        final float baseline = (line.getAscent() - line.getDescent()) * 0.5f;
        g.drawString(text, (float) (anchorX - size.getWidth() / 2.0), (float) (anchorY + baseline));
        g.dispose();
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if ((image.getRGB(x, y) >>> 24) == 0) {
                    continue;
                }
                minX = Math.min(minX, x + 0.5);
                maxX = Math.max(maxX, x + 0.5);
                minY = Math.min(minY, y + 0.5);
                maxY = Math.max(maxY, y + 0.5);
            }
        }
        return new double[] { rect.getMinX() - minX, maxX - rect.getMaxX(),
            rect.getMinY() - minY, maxY - rect.getMaxY() };
    }

    static final class Result {
        final double paintedWidth;
        final double overhang;
        final double minInkDistance;
        final int inkInsideRect;

        Result(final double paintedWidth, final double overhang, final double minInkDistance,
                final int inkInsideRect) {
            this.paintedWidth = paintedWidth;
            this.overhang = overhang;
            this.minInkDistance = minInkDistance;
            this.inkInsideRect = inkInsideRect;
        }
    }

    static Result measure(final double zoom, final boolean compensated, final double gap) {
        final BufferedImage image = render(zoom, compensated, gap);
        final Rectangle2D rect = labelRect(gap);
        double maxX = Double.NEGATIVE_INFINITY;
        double minDistance = Double.POSITIVE_INFINITY;
        int painted = 0;
        int inside = 0;
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if ((image.getRGB(x, y) >>> 24) == 0) {
                    continue;
                }
                painted++;
                maxX = Math.max(maxX, x + 0.5);
                minDistance = Math.min(minDistance, distanceToRect(rect, x + 0.5, y + 0.5));
                if (rect.contains(x + 0.5, y + 0.5)) {
                    inside++;
                }
            }
        }
        final double thickness = maxThickness(image);
        return new Result(thickness, maxX - END_X, minDistance, inside);
    }

    /** painted vertical thickness of the horizontal stroke, in device px. */
    static double maxThickness(final BufferedImage image) {
        int best = 0;
        for (int x = 0; x < WIDTH; x++) {
            int count = 0;
            for (int y = 0; y < HEIGHT; y++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    count++;
                }
            }
            best = Math.max(best, count);
        }
        return best;
    }

    static double distanceToRect(final Rectangle2D rect, final double x, final double y) {
        final double dx = Math.max(0.0, Math.max(rect.getMinX() - x, x - rect.getMaxX()));
        final double dy = Math.max(0.0, Math.max(rect.getMinY() - y, y - rect.getMaxY()));
        return Math.hypot(dx, dy);
    }

    static Rectangle2D labelRect(final double gap) {
        return new Rectangle2D.Double(END_X + gap, CENTER_Y - LABEL_HEIGHT / 2.0, LABEL_WIDTH,
            LABEL_HEIGHT);
    }

    /** Reproduces GraphPainter.paintLabels' leader drawing under the world transform. */
    static BufferedImage render(final double zoom, final boolean compensated, final double gap) {
        final BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        g.setColor(Color.BLACK);
        final AffineTransform world = new AffineTransform();
        world.translate(WIDTH * 0.5, HEIGHT * 0.5);
        world.scale(zoom, zoom);
        g.transform(world);
        g.setStroke(new BasicStroke((float) (compensated ? STROKE_WIDTH / zoom : STROKE_WIDTH),
            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(-100.0 / zoom, 0.0, (END_X - WIDTH * 0.5) / zoom, 0.0));
        g.dispose();
        return image;
    }
}
