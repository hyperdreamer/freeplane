import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Design mockups for the graph label / leader-line clearance change.
 *
 * PM run pm-run-20260912-212024-c7dfa6db, topic graph-label-leader-clearance.
 * Every number below is mirrored from production code, not hand-placed:
 *
 *   SLOT_GAP            = 6.0    ScreenLabelPlacement.SLOT_GAP
 *   DISPLACED_OFFSET    = 30.0   ScreenLabelPlacement.DISPLACED_OFFSET
 *   slotAnchor(...)              ScreenLabelPlacement.slotAnchor
 *   leader start        = disc-rim point on the centre -> anchor direction
 *                                ScreenLabelPlacement.leaderStart
 *   leader end (TODAY)  = the label anchor, i.e. the label rectangle centre
 *                                GraphPainter.paintLabels -> Line2D(leaderStart, anchorX/Y)
 *   paint order         = leader first, then the name on top
 *                                GraphPainter.paintLabels (stroke -> drawString)
 *   label rectangle     = font.getStringBounds(text, SCREEN_FRC), 12 pt
 *                                ScreenLabelPlacement.screenBounds
 *
 * Panels: (1) today's defect, (2) option A, (3) option B, (4) option C.
 * Each panel draws the identical fixture at production scale plus two 2x
 * magnified junctions: an adjacent RIGHT slot and a displaced RIGHT_FAR slot.
 *
 * The clipped leader ends are the only geometry this mockup invents; they are
 * computed by clipToRect() (first boundary crossing of the rim -> centre segment
 * with the label rectangle). Option A clips against the rectangle inflated by
 * OPTION_A_GAP on all four sides, so the leader's Euclidean clearance from the
 * name's box is OPTION_A_GAP on a face entry and up to OPTION_A_GAP*sqrt(2) at a
 * corner; option B clips against the plain rectangle.
 */
public final class LabelLeaderClearanceMockups {

    static final double SLOT_GAP = 6.0;
    static final double DISPLACED_OFFSET = 30.0;
    static final double OPTION_A_GAP = 3.0;

    static final Font FULL = new Font(Font.DIALOG, Font.PLAIN, 12);
    static final Font TITLE = new Font(Font.DIALOG, Font.BOLD, 17);
    static final Font CAPTION = new Font(Font.DIALOG, Font.BOLD, 13);
    static final Font TINY = new Font(Font.DIALOG, Font.PLAIN, 11);

    static final Color INK = new Color(0x1F, 0x24, 0x2B);
    static final Color PAPER = new Color(0xFB, 0xFC, 0xFE);
    static final Color HULL_FILL = new Color(0xDD, 0xE7, 0xF3);
    static final Color HULL_LINE = new Color(0x5B, 0x86, 0xB5);
    static final Color DISC_FILL = new Color(0xEA, 0xF2, 0xFA);
    static final Color DISC_STROKE = new Color(0x2F, 0x5C, 0x8A);
    static final Color EDGE = new Color(0x9A, 0xA4, 0xB0);
    static final Color BAD = new Color(0xB3, 0x2B, 0x2B);
    static final Color MUTED = new Color(0x6B, 0x74, 0x80);
    static final Color FRAME = new Color(0xC3, 0xCB, 0xD5);

    static final FontRenderContext FRC = new FontRenderContext(null, true, true);

    enum Slot {
        ABOVE, BELOW, RIGHT, LEFT, ABOVE_RIGHT, ABOVE_LEFT, BELOW_RIGHT, BELOW_LEFT,
        ABOVE_FAR, BELOW_FAR, RIGHT_FAR, LEFT_FAR
    }

    enum Mode { TODAY, A, B, C }

    static final class Node {
        final double x;
        final double y;
        final double r;
        final String text;
        final Slot slot;
        final boolean plus;

        Node(final double x, final double y, final double r, final String text, final Slot slot,
                final boolean plus) {
            this.x = x;
            this.y = y;
            this.r = r;
            this.text = text;
            this.slot = slot;
            this.plus = plus;
        }
    }

    /** Fixture: RIGHT, ABOVE_RIGHT and RIGHT_FAR node labels inside one hull. */
    static final Node[] FIXTURE = {
        new Node(150.0, 150.0, 14.0, "Foundation / Regularity", Slot.RIGHT, false),
        new Node(150.0, 250.0, 8.0, "Theorem", Slot.ABOVE_RIGHT, false),
        new Node(390.0, 120.0, 14.0, "Axiom of Choice", Slot.RIGHT, true),
        new Node(390.0, 250.0, 8.0, "Replacement Scheme", Slot.RIGHT_FAR, false)
    };

    static final double OUTER_HULL_X0 = 40.0;
    static final double OUTER_HULL_X1 = 560.0;
    static final double OUTER_HULL_Y0 = 40.0;
    static final double OUTER_HULL_Y1 = 330.0;

    static final double SCENE_W = 620.0;
    static final double SCENE_H = 360.0;
    static final double PANEL_W = 644.0;
    static final double PANEL_H = 596.0;
    static final double COL_GAP = 20.0;
    static final double OUTER = 24.0;
    static final double HEAD = 78.0;

    static final double SCENE_Y = 56.0;
    static final double INSET_Y = SCENE_Y + SCENE_H + 34.0;
    static final double INSET_1_X = 12.0;
    static final double INSET_1_W = 264.0;
    static final double INSET_2_X = 12.0 + INSET_1_W + 12.0;
    static final double INSET_2_W = 330.0;
    static final double INSET_H = 120.0;
    static final double INSET_ZOOM = 2.0;
    static final double INSET_1_SRC_X = 372.0;
    static final double INSET_1_SRC_Y = 90.0;
    static final double INSET_2_SRC_X = 390.0;
    static final double INSET_2_SRC_Y = 222.0;

    public static void main(final String[] args) throws Exception {
        final Mode[] modes = { Mode.TODAY, Mode.A, Mode.B, Mode.C };
        final int width = (int) (2 * OUTER + 2 * PANEL_W + COL_GAP);
        final int height = (int) (OUTER + HEAD + 2 * PANEL_H + COL_GAP + OUTER);
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);

        g.setColor(INK);
        g.setFont(TITLE);
        g.drawString("Graph name vs. its leader line \u2014 clearance options", (float) OUTER, 34f);
        g.setFont(TINY);
        g.setColor(MUTED);
        g.drawString("Identical fixture in all four panels, drawn at production scale with the "
            + "shipped constants (SLOT_GAP 6, DISPLACED_OFFSET 30, 12 pt label font). "
            + "The leader is painted under the name, exactly as GraphPainter does.",
            (float) OUTER, 50f);
        g.drawString("A clips the leader on the name's box inflated by 3 px (clearance 3 px on a face "
            + "entry, up to 4.24 px at a corner). B clips on the bare box. C drops the leader for "
            + "adjacent slots and trims the displaced and enclosure leaders like A.",
            (float) OUTER, 66f);

        for (int i = 0; i < modes.length; i++) {
            final int col = i % 2;
            final int row = i / 2;
            final Graphics2D panel = (Graphics2D) g.create();
            panel.translate(OUTER + col * (PANEL_W + COL_GAP),
                OUTER + HEAD + row * (PANEL_H + COL_GAP));
            drawPanel(panel, modes[i]);
            panel.dispose();
        }

        g.setColor(MUTED);
        g.setFont(TINY);
        g.drawString("Leader start and label rectangles use the production formulas "
            + "(ScreenLabelPlacement.slotAnchor / leaderStart); today's leader end is the rectangle "
            + "centre, which is why the stroke runs through the name.",
            (float) OUTER, (float) (height - 8));
        g.dispose();

        final File out = new File(args.length > 0 ? args[0]
            : "docs/superpowers/specs/mockups/2026-09-12-label-leader-clearance/"
                + "label-leader-clearance.png");
        out.getParentFile().mkdirs();
        ImageIO.write(image, "png", out);
        System.out.println("wrote " + out.getAbsolutePath() + " (" + width + "x" + height + ")");
    }

    static void drawPanel(final Graphics2D g, final Mode mode) {
        g.setColor(PAPER);
        g.fillRect(0, 0, (int) PANEL_W, (int) PANEL_H);
        g.setColor(FRAME);
        g.drawRect(0, 0, (int) PANEL_W - 1, (int) PANEL_H - 1);

        final String[] captions = caption(mode);
        g.setFont(CAPTION);
        g.setColor(mode == Mode.TODAY ? BAD : INK);
        g.drawString(captions[0], 12f, 24f);
        g.setFont(TINY);
        g.setColor(MUTED);
        g.drawString(captions[1], 12f, 40f);

        final Graphics2D scene = (Graphics2D) g.create();
        scene.translate(12.0, SCENE_Y);
        drawScene(scene, mode);
        scene.dispose();

        g.setFont(TINY);
        g.setColor(MUTED);
        g.drawString("2x detail of the two junctions", 12f, (float) (INSET_Y - 8.0));

        drawInset(g, mode, FIXTURE[2], INSET_1_X, INSET_1_SRC_X, INSET_1_SRC_Y, INSET_1_W);
        drawInset(g, mode, FIXTURE[3], INSET_2_X, INSET_2_SRC_X, INSET_2_SRC_Y, INSET_2_W);

        g.setFont(TINY);
        g.setColor(MUTED);
        g.drawString("adjacent (RIGHT) \u2014 6 px box gap", (float) INSET_1_X,
            (float) (INSET_Y + INSET_H + 14.0));
        g.drawString("displaced (RIGHT_FAR) \u2014 30 px offset", (float) INSET_2_X,
            (float) (INSET_Y + INSET_H + 14.0));
    }

    static void drawInset(final Graphics2D g, final Mode mode, final Node node, final double x,
            final double srcX, final double srcY, final double w) {
        final Graphics2D inset = (Graphics2D) g.create();
        inset.translate(x, INSET_Y);
        inset.clip(new Rectangle2D.Double(0, 0, w, INSET_H));
        inset.setColor(Color.WHITE);
        inset.fillRect(0, 0, (int) w, (int) INSET_H);
        inset.scale(INSET_ZOOM, INSET_ZOOM);
        inset.translate(-srcX, -srcY);
        drawNodeLabel(inset, node, mode);
        inset.dispose();
        g.setColor(FRAME);
        g.drawRect((int) x, (int) INSET_Y, (int) w, (int) INSET_H);
    }

    static String[] caption(final Mode mode) {
        switch (mode) {
            case TODAY:
                return new String[] {
                    "Today \u2014 the leader ends at the name's centre point",
                    "stroke painted first, name painted on top: it stays visible in the letter gaps "
                        + "(red = the part that crosses the name)" };
            case A:
                return new String[] {
                    "Option A \u2014 stop 3 px clear of the name's box",
                    "leader keeps its disc-rim start and ends on the box inflated by 3 px; "
                        + "every slot keeps its leader" };
            case B:
                return new String[] {
                    "Option B \u2014 stop exactly on the name's box edge",
                    "no gap: the leader touches the box outline but never enters it; "
                        + "every slot keeps its leader" };
            default:
                return new String[] {
                    "Option C \u2014 no leader when the box is adjacent (6 px)",
                    "uninformative near-slot leaders are removed; displaced (30 px) and enclosure "
                        + "leaders are kept and trimmed like A" };
        }
    }

    // ---------------------------------------------------------------- scene

    static void drawScene(final Graphics2D g, final Mode mode) {
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, (int) SCENE_W, (int) SCENE_H);
        fillHull(g, hexagon(OUTER_HULL_X0, OUTER_HULL_Y0, OUTER_HULL_X1, OUTER_HULL_Y1, 70.0));
        fillHull(g, hexagon(70.0, 108.0, 250.0, 330.0, 40.0));

        // enclosure label, EXTERNAL to the outer hull: keeps a leader in every option
        drawEnclosureLabel(g, "ZFC", OUTER_HULL_X1, (OUTER_HULL_Y0 + OUTER_HULL_Y1) / 2.0, mode);
        // enclosure label above the inner hull: ABOVE has never had a leader
        drawPlainLabel(g, "Basic Definitions and Theorems", 160.0,
            108.0 - 4.0 - screenBounds("Basic Definitions and Theorems", FULL).getHeight() / 2.0);

        for (final Node node : FIXTURE) {
            drawNodeLabel(g, node, mode);
        }
    }

    static void fillHull(final Graphics2D g, final Path2D hull) {
        g.setColor(HULL_FILL);
        g.fill(hull);
        g.setColor(HULL_LINE);
        g.setStroke(new BasicStroke(1.2f));
        g.draw(hull);
        g.setStroke(new BasicStroke(1f));
    }

    /** Elongated hexagon with points at the left and right extremes, as in the app's hulls. */
    static Path2D hexagon(final double x0, final double y0, final double x1, final double y1,
            final double cut) {
        final double ym = (y0 + y1) / 2.0;
        final Path2D p = new Path2D.Double();
        p.moveTo(x0, ym);
        p.lineTo(x0 + cut, y0);
        p.lineTo(x1 - cut, y0);
        p.lineTo(x1, ym);
        p.lineTo(x1 - cut, y1);
        p.lineTo(x0 + cut, y1);
        p.closePath();
        return p;
    }

    static void drawEnclosureLabel(final Graphics2D g, final String text, final double hullRightX,
            final double y, final Mode mode) {
        final Rectangle2D size = screenBounds(text, FULL);
        final double anchorX = hullRightX + 4.0 + size.getWidth() / 2.0;
        final Rectangle2D rect = new Rectangle2D.Double(anchorX - size.getWidth() / 2.0,
            y - size.getHeight() / 2.0, size.getWidth(), size.getHeight());
        final double[] end = leaderEnd(mode, hullRightX, y, anchorX, y, rect);
        if (end != null) {
            if (mode == Mode.TODAY) {
                drawLeader(g, hullRightX, y, new double[] { anchorX, y }, EDGE);
                drawLeader(g, end[0], end[1], new double[] { anchorX, y }, BAD);
            } else {
                drawLeader(g, hullRightX, y, end, EDGE);
            }
        }
        drawPlainLabel(g, text, anchorX, y);
        if (mode == Mode.TODAY) {
            drawDashedRect(g, rect);
        }
    }

    static void drawNodeLabel(final Graphics2D g, final Node node, final Mode mode) {
        final Rectangle2D size = screenBounds(node.text, FULL);
        final double w = size.getWidth();
        final double h = size.getHeight();
        final double[] anchor = slotAnchor(node.slot, node.x, node.y, node.r, w, h);
        final Rectangle2D rect = new Rectangle2D.Double(anchor[0] - w / 2.0, anchor[1] - h / 2.0,
            w, h);
        final double[] start = leaderStart(node.slot, node.x, node.y, node.r, anchor[0], anchor[1]);
        final double[] clip = start == null ? null : clipToRect(start, anchor, rect);

        if (mode == Mode.TODAY) {
            if (start != null) {
                drawLeader(g, start[0], start[1], anchor, EDGE);
                drawLeader(g, clip[0], clip[1], anchor, BAD);
            }
        } else if (start != null && !(mode == Mode.C && isNear(node.slot))) {
            final double[] end = leaderEnd(mode, start[0], start[1], anchor[0], anchor[1], rect);
            if (end != null) {
                drawLeader(g, start[0], start[1], end, EDGE);
            }
        }

        drawDisc(g, node);
        drawPlainLabel(g, node.text, anchor[0], anchor[1]);

        if (mode == Mode.TODAY) {
            drawDashedRect(g, rect);
        }
    }

    static void drawDashedRect(final Graphics2D g, final Rectangle2D rect) {
        g.setColor(BAD);
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
            new float[] { 3f, 3f }, 0f));
        g.draw(rect);
        g.setStroke(new BasicStroke(1f));
    }

    /** Option C: no leader for near slots other than the already-suppressed ABOVE/BELOW. */
    static boolean isNear(final Slot slot) {
        return slot != Slot.ABOVE && slot != Slot.BELOW
            && slot != Slot.ABOVE_FAR && slot != Slot.BELOW_FAR
            && slot != Slot.RIGHT_FAR && slot != Slot.LEFT_FAR;
    }

    static double[] leaderEnd(final Mode mode, final double startX, final double startY,
            final double anchorX, final double anchorY, final Rectangle2D rect) {
        if (mode == Mode.TODAY) {
            return new double[] { anchorX, anchorY };
        }
        final double[] clip = clipToRect(new double[] { startX, startY },
            new double[] { anchorX, anchorY }, mode == Mode.A ? inflate(rect, OPTION_A_GAP) : rect);
        return clip;
    }

    /** Rectangle inflated by gap on all four sides; the leader stops on its boundary. */
    static Rectangle2D inflate(final Rectangle2D rect, final double gap) {
        return new Rectangle2D.Double(rect.getMinX() - gap, rect.getMinY() - gap,
            rect.getWidth() + 2.0 * gap, rect.getHeight() + 2.0 * gap);
    }

    /**
     * First boundary crossing of the segment start -> insidePoint with the rectangle, i.e. the
     * point where a leader aimed at the label centre enters the label box.
     */
    static double[] clipToRect(final double[] start, final double[] inside, final Rectangle2D rect) {
        final double dx = inside[0] - start[0];
        final double dy = inside[1] - start[1];
        double tEnter = 0.0;
        if (dx > 0) {
            tEnter = Math.max(tEnter, (rect.getMinX() - start[0]) / dx);
        } else if (dx < 0) {
            tEnter = Math.max(tEnter, (rect.getMaxX() - start[0]) / dx);
        }
        if (dy > 0) {
            tEnter = Math.max(tEnter, (rect.getMinY() - start[1]) / dy);
        } else if (dy < 0) {
            tEnter = Math.max(tEnter, (rect.getMaxY() - start[1]) / dy);
        }
        if (tEnter <= 0.0 || tEnter >= 1.0) {
            return null;
        }
        return new double[] { start[0] + tEnter * dx, start[1] + tEnter * dy };
    }

    static void drawLeader(final Graphics2D g, final double x1, final double y1, final double[] end,
            final Color color) {
        g.setColor(color);
        g.setStroke(new BasicStroke(1.4f));
        g.draw(new Line2D.Double(x1, y1, end[0], end[1]));
        g.setStroke(new BasicStroke(1f));
    }

    static void drawDisc(final Graphics2D g, final Node node) {
        final double diameter = node.r * 2.0;
        final Ellipse2D.Double circle = new Ellipse2D.Double(node.x - node.r, node.y - node.r,
            diameter, diameter);
        g.setColor(DISC_FILL);
        g.fill(circle);
        g.setColor(DISC_STROKE);
        g.setStroke(new BasicStroke(1.4f));
        g.draw(circle);
        if (node.plus) {
            final double arm = node.r * 0.55;
            g.draw(new Line2D.Double(node.x - arm, node.y, node.x + arm, node.y));
            g.draw(new Line2D.Double(node.x, node.y - arm, node.x, node.y + arm));
        }
        g.setStroke(new BasicStroke(1f));
    }

    static void drawPlainLabel(final Graphics2D g, final String text, final double anchorX,
            final double anchorY) {
        g.setFont(FULL);
        g.setColor(INK);
        final Rectangle2D bounds = FULL.getStringBounds(text, FRC);
        final LineMetrics line = FULL.getLineMetrics(text, FRC);
        final float baseline = (line.getAscent() - line.getDescent()) * 0.5f;
        g.drawString(text, (float) (anchorX - bounds.getWidth() * 0.5),
            (float) (anchorY + baseline));
    }

    // -------------------------------------------------- production formulas

    static double[] slotAnchor(final Slot slot, final double centerX, final double centerY,
            final double radius, final double width, final double height) {
        switch (slot) {
            case ABOVE:
                return new double[] { centerX, centerY - radius - SLOT_GAP - height / 2 };
            case BELOW:
                return new double[] { centerX, centerY + radius + SLOT_GAP + height / 2 };
            case RIGHT:
                return new double[] { centerX + radius + SLOT_GAP + width / 2, centerY };
            case LEFT:
                return new double[] { centerX - radius - SLOT_GAP - width / 2, centerY };
            case ABOVE_FAR:
                return new double[] { centerX, centerY - radius - DISPLACED_OFFSET - height / 2 };
            case BELOW_FAR:
                return new double[] { centerX, centerY + radius + DISPLACED_OFFSET + height / 2 };
            case RIGHT_FAR:
                return new double[] { centerX + radius + DISPLACED_OFFSET + width / 2, centerY };
            case LEFT_FAR:
                return new double[] { centerX - radius - DISPLACED_OFFSET - width / 2, centerY };
            case ABOVE_RIGHT:
                return new double[] { centerX + radius + SLOT_GAP + width / 2,
                    centerY - radius - SLOT_GAP - height / 2 };
            case ABOVE_LEFT:
                return new double[] { centerX - radius - SLOT_GAP - width / 2,
                    centerY - radius - SLOT_GAP - height / 2 };
            case BELOW_RIGHT:
                return new double[] { centerX + radius + SLOT_GAP + width / 2,
                    centerY + radius + SLOT_GAP + height / 2 };
            default:
                return new double[] { centerX - radius - SLOT_GAP - width / 2,
                    centerY + radius + SLOT_GAP + height / 2 };
        }
    }

    static double[] leaderStart(final Slot slot, final double centerX, final double centerY,
            final double radius, final double anchorX, final double anchorY) {
        if (slot == Slot.ABOVE || slot == Slot.BELOW) {
            return null;
        }
        final double dx = anchorX - centerX;
        final double dy = anchorY - centerY;
        final double distance = Math.max(1e-6, Math.hypot(dx, dy));
        return new double[] { centerX + dx / distance * radius, centerY + dy / distance * radius };
    }

    static Rectangle2D screenBounds(final String text, final Font font) {
        final Rectangle2D bounds = font.getStringBounds(text, FRC);
        return new Rectangle2D.Double(0, 0, bounds.getWidth(), bounds.getHeight());
    }
}
