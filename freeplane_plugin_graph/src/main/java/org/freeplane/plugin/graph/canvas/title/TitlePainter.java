package org.freeplane.plugin.graph.canvas.title;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.util.Objects;

/**
 * Paints a resolved row. Stateless, and measures nothing: every font, offset and icon it needs is carried by the
 * row. The base transform is applied exactly once.
 */
public final class TitlePainter {
    private TitlePainter() {
    }

    public static void paint(final Graphics2D graphics, final TitleRow row, final double anchorX,
            final double anchorY, final double zoom, final Color colour, final AffineTransform baseTransform) {
        validate(graphics, row, anchorX, anchorY, zoom, colour, baseTransform);
        final double left = anchorX - row.width() * 0.5;
        final float baselineOffset = ((float) row.ascent() - (float) row.descent()) * 0.5f;
        final double baseline = anchorY + baselineOffset;
        for (final RunRow run : row.runs()) {
            if (run instanceof PlainRunRow) {
                final PlainRunRow plain = (PlainRunRow) run;
                graphics.setColor(colour);
                graphics.setFont(plain.worldFont());
                graphics.drawString(plain.paintText(), (float) (left + plain.x()), (float) baseline);
                continue;
            }
            final MathRunRow math = (MathRunRow) run;
            final Point2D componentTop = iconTopComponent(math, left + math.x(), baseline, zoom,
                graphics.getTransform(), baseTransform);
            final Graphics2D copy = (Graphics2D) graphics.create();
            try {
                copy.setTransform(baseTransform);
                copy.translate(componentTop.getX(), componentTop.getY());
                math.icon().setForeground(colour);
                math.icon().paintIcon(null, copy, 0, 0);
            }
            finally {
                copy.dispose();
            }
        }
    }

    /**
     * The component-space top-left at which the icon must be placed so that its own (base-transform-scaled) ascent
     * lands on the shared baseline.
     */
    static Point2D iconTopComponent(final MathRunRow run, final double runLeft, final double baseline,
            final double zoom, final AffineTransform currentTransform, final AffineTransform baseTransform) {
        Objects.requireNonNull(currentTransform, "currentTransform");
        final Point2D deviceBaseline = currentTransform.transform(new Point2D.Double(runLeft, baseline), null);
        final double verticalScale = baseTransform.deltaTransform(new Point2D.Double(0.0, 1.0), null).getY();
        final Point2D deviceTop = new Point2D.Double(deviceBaseline.getX(),
            deviceBaseline.getY() - run.ascent() * zoom * verticalScale);
        try {
            return baseTransform.inverseTransform(deviceTop, null);
        }
        catch (final NoninvertibleTransformException notInvertible) {
            throw new IllegalStateException("base transform must be invertible", notInvertible);
        }
    }

    private static void validate(final Graphics2D graphics, final TitleRow row, final double anchorX,
            final double anchorY, final double zoom, final Color colour, final AffineTransform baseTransform) {
        Objects.requireNonNull(graphics, "graphics");
        Objects.requireNonNull(row, "row");
        Objects.requireNonNull(colour, "colour");
        Objects.requireNonNull(baseTransform, "baseTransform");
        if (!Double.isFinite(anchorX) || !Double.isFinite(anchorY)) {
            throw new IllegalArgumentException("anchor must be finite");
        }
        if (!Double.isFinite(zoom) || !(zoom > 0.0)) {
            throw new IllegalArgumentException("zoom must be finite and positive");
        }
        final double determinant = baseTransform.getDeterminant();
        if (!Double.isFinite(determinant) || determinant == 0.0) {
            throw new IllegalArgumentException("base transform must be invertible");
        }
    }
}
