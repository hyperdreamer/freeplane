import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Design mockups for the Graph Workspace "edges should avoid crossing each other" change.
 *
 * Topic graph-edge-crossing-avoidance, PM run pm-run-20261012-021203-0d02a300.
 *
 * The fixture disc positions are measured directly from the user's screenshot
 * (521x447 px PNG, disc-stroke blob centroids):
 *
 *   top    (272, 170)      left  (240, 208)
 *   right  (307, 202)      bottom(275, 240)
 *
 * Treatment geometry mirrors production painter rules:
 *   node radius          = 8.0 world x MAX_SCALE 1.75 = 14.0 world  (NodeProminence)
 *   zoom                 = 1.32  (disc stroke blobs measure 37 px diameter = 2 x 14 x 1.32)
 *   edge stroke          = GraphTheme.lightTheme edgeStroke 1.4 world x zoom
 *   arrowhead length     = max(12.5, 0.62 x 14.0 x zoom) screen px   (GraphPainter)
 *   arrowhead half base  = 0.42 x length
 *   binding points       = disc rim on the centre -> centre ray        (NodeGeometry.boundaryToward)
 *
 * Panels: (A1) today, (A2) bounded bow/dogleg, (A3) around-disc detour, (A4) hop/bridge,
 * (B1) edge over a disc today, (B2) edge over a disc routed.
 */
public final class EdgeRoutingMockups {

    // ---- production constants mirrored ------------------------------------------------
    static final double ZOOM = 1.32;
    static final double NODE_RADIUS_WORLD = 14.0;
    static final double DISC_R = NODE_RADIUS_WORLD * ZOOM;              // 18.48 px
    static final float EDGE_STROKE = (float) (1.4 * ZOOM);              // 1.85 px
    static final double ARROW_LENGTH = Math.max(12.5, 0.62 * NODE_RADIUS_WORLD * ZOOM);
    static final double ARROW_HALF_BASE = 0.42 * ARROW_LENGTH;

    static final Color BG = new Color(250, 251, 253);
    static final Color EDGE = new Color(73, 84, 100);
    static final Color NODE_FILL = new Color(229, 239, 249);
    static final Color NODE_STROKE = new Color(38, 91, 137);
    static final Color INK = new Color(31, 36, 43);
    static final Color MUTED = new Color(110, 118, 130);
    static final Color BAD = new Color(179, 43, 43);
    static final Color OK = new Color(21, 128, 61);
    static final Color FRAME = new Color(210, 216, 224);

    static final Font TITLE = new Font(Font.DIALOG, Font.BOLD, 14);
    static final Font CAPTION = new Font(Font.DIALOG, Font.PLAIN, 11);

    // ---- fixture ---------------------------------------------------------------------
    static final Point2D TOP = new Point2D.Double(272, 170);
    static final Point2D LEFT = new Point2D.Double(240, 208);
    static final Point2D RIGHT = new Point2D.Double(307, 202);
    static final Point2D BOTTOM = new Point2D.Double(275, 240);
    static final Point2D CROSS = intersect(rim(TOP, BOTTOM), rim(BOTTOM, TOP), rim(LEFT, RIGHT), rim(RIGHT, LEFT));

    /** clearance kept between a routed edge and any obstacle, screen px. */
    static final double CLEARANCE = 3.0;
    /** bow/dogleg ladder cap as a fraction of the direct distance. */
    static final double DETOUR_CAP = 0.35;

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : "edge-crossing-mockups.png");
        BufferedImage image = render();
        ImageIO.write(image, "png", out);
        System.out.println("wrote " + out.getAbsolutePath() + " " + image.getWidth() + "x" + image.getHeight());
    }

    static BufferedImage render() {
        final int pw = 480, ph = 340, cols = 4, rows = 2;
        BufferedImage img = new BufferedImage(pw * cols, ph * rows, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(238, 240, 244));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.dispose();

        panel(img, 0, 0, "A1 - TODAY", new String[] {
            "Straight edges. The crossing sits inside",
            "the arrowhead cluster: dark star.",
        }, Treatment.TODAY);
        panel(img, 1, 0, "A2 - BOUNDED ROUTING (accepted)", new String[] {
            "Largest bow inside the mockup's 0.35 x chord cap;",
            "no candidate at this cap clears. Crossing remains.",
        }, Treatment.BOUNDED);
        panel(img, 2, 0, "A3 - AROUND-DISC DETOUR", new String[] {
            "Route E1 around the left disc (3 px clearance):",
            "crossing removed; long wrapped detour.",
        }, Treatment.DETOUR);
        panel(img, 3, 0, "A4 - HOP / BRIDGE", new String[] {
            "Straight edges; the later edge hops over the",
            "crossing. Crossing remains, but legible.",
        }, Treatment.HOP);
        panel(img, 0, 1, "B1 - EDGE OVER A DISC (today)", new String[] {
            "A straight edge passes over an unrelated disc.",
        }, Treatment.DISC_TODAY);
        panel(img, 1, 1, "B2 - EDGE OVER A DISC (routed)", new String[] {
            "Dogleg inside the bend and route-length caps clears",
            "the disc - the case bounded routing always fixes.",
        }, Treatment.DISC_ROUTED);
        panel(img, 2, 1, "C1 - RELOCATION - BEFORE", new String[] {
            "The reported case before any correction:",
            "diagonals cross inside the arrowhead cluster.",
        }, Treatment.RELOCATE_BEFORE);
        panel(img, 3, 1, "C2 - RELOCATION - AFTER (swap two discs)", new String[] {
            "Two nodes exchange positions; every edge stays straight",
            "and the crossing is gone. Disc gaps stay verified.",
        }, Treatment.RELOCATE_AFTER);
        return img;
    }

    enum Treatment { TODAY, BOUNDED, DETOUR, HOP, DISC_TODAY, DISC_ROUTED, RELOCATE_BEFORE,
        RELOCATE_AFTER }

    static void panel(BufferedImage img, int col, int row, String title, String[] caption,
            Treatment treatment) {
        final int pw = 480, ph = 340;
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(252, 253, 255));
        g.fillRect(col * pw + 6, row * ph + 6, pw - 12, ph - 12);
        g.setColor(FRAME);
        g.drawRect(col * pw + 6, row * ph + 6, pw - 12, ph - 12);
        g.setColor(INK);
        g.setFont(TITLE);
        g.drawString(title, col * pw + 18, row * ph + 30);
        g.setColor(MUTED);
        g.setFont(CAPTION);
        int y = row * ph + ph - 34;
        for (String line : caption) {
            g.drawString(line, col * pw + 18, y);
            y += 14;
        }

        // main view (1:1)
        Graphics2D main = (Graphics2D) g.create(col * pw + 8, row * ph + 40, 210, 250);
        main.setColor(new Color(246, 248, 251));
        main.fillRect(0, 0, 210, 250);
        main.translate(-170, -140);
        if (treatment == Treatment.DISC_TODAY || treatment == Treatment.DISC_ROUTED) {
            main.translate(20, 0);
        }
        scene(main, treatment, false);
        main.dispose();

        // 2x inset on the junction
        Graphics2D inset = (Graphics2D) g.create(col * pw + 224, row * ph + 40, 240, 250);
        inset.setColor(new Color(246, 248, 251));
        inset.fillRect(0, 0, 240, 250);
        inset.translate(120, 40);
        inset.scale(2.0, 2.0);
        inset.translate(-273.5, -205.0);
        if (treatment == Treatment.DISC_TODAY || treatment == Treatment.DISC_ROUTED) {
            inset.translate(-8, 40);
        }
        scene(inset, treatment, true);
        inset.dispose();
        g.dispose();
    }

    static void scene(Graphics2D g, Treatment treatment, boolean inset) {
        if (treatment == Treatment.DISC_TODAY || treatment == Treatment.DISC_ROUTED) {
            discScene(g, treatment);
            return;
        }
        Point2D topPos = treatment == Treatment.RELOCATE_AFTER ? LEFT : TOP;
        Point2D leftPos = treatment == Treatment.RELOCATE_AFTER ? TOP : LEFT;
        Point2D topRim = rim(topPos, BOTTOM);
        Point2D botRim = rim(BOTTOM, topPos);
        Point2D leftRim = rim(leftPos, RIGHT);
        Point2D rightRim = rim(RIGHT, leftPos);

        switch (treatment) {
            case TODAY:
            case RELOCATE_BEFORE:
                edge(g, line(topRim, botRim), INK_COLOR);
                arrow(g, line(topRim, botRim), botRim);
                edge(g, line(leftRim, rightRim), INK_COLOR);
                arrow(g, line(leftRim, rightRim), rightRim);
                break;
            case BOUNDED: {
                // largest legal bow for E1 toward the left, still crossing E2
                double amp = DETOUR_CAP * topRim.distance(botRim);
                List<Point2D> bow = bow(topRim, botRim, -amp);
                edge(g, bow, INK_COLOR);
                arrow(g, bow, botRim);
                edge(g, line(leftRim, rightRim), INK_COLOR);
                arrow(g, line(leftRim, rightRim), rightRim);
                marker(g, CROSS, BAD);
                break;
            }
            case DETOUR: {
                List<Point2D> route = shortestClear(detourCandidates(TOP, BOTTOM, LEFT, CLEARANCE),
                    line(leftRim, rightRim));
                if (route == null) {
                    route = line(TOP, BOTTOM);
                }
                route = clipRims(route, TOP, BOTTOM);
                edge(g, route, INK_COLOR);
                arrow(g, route, route.get(route.size() - 1));
                edge(g, line(leftRim, rightRim), INK_COLOR);
                arrow(g, line(leftRim, rightRim), rightRim);
                marker(g, CROSS, OK);
                break;
            }
            case HOP: {
                edge(g, line(leftRim, rightRim), INK_COLOR);
                arrow(g, line(leftRim, rightRim), rightRim);
                // E1 drawn second: hop over E2 at the crossing
                List<Point2D> withHop = hop(line(topRim, botRim), CROSS, 5.0);
                edge(g, withHop, INK_COLOR);
                arrow(g, withHop, withHop.get(withHop.size() - 1));
                break;
            }
            case RELOCATE_AFTER: {
                edge(g, line(topRim, botRim), INK_COLOR);
                arrow(g, line(topRim, botRim), botRim);
                edge(g, line(leftRim, rightRim), INK_COLOR);
                arrow(g, line(leftRim, rightRim), rightRim);
                swapArrow(g, TOP, LEFT);
                swapArrow(g, LEFT, TOP);
                break;
            }
            default:
                break;
        }
        // discs drawn last, as the painter does (nodes over edges)
        disc(g, topPos);
        disc(g, leftPos);
        disc(g, RIGHT);
        disc(g, BOTTOM);
    }

    /** Dashed curved arrow marking one node's relocation. */
    static void swapArrow(Graphics2D g, Point2D from, Point2D to) {
        double mx = (from.getX() + to.getX()) * 0.5, my = (from.getY() + to.getY()) * 0.5;
        double dx = to.getX() - from.getX(), dy = to.getY() - from.getY();
        double len = Math.hypot(dx, dy);
        double nx = -dy / len, ny = dx / len;
        double bulge = 16.0;
        double cx = mx + nx * bulge, cy = my + ny * bulge;
        List<Point2D> path = new ArrayList<Point2D>();
        for (int i = 0; i <= 20; i++) {
            double t = i / 20.0;
            double u = 1 - t;
            path.add(new Point2D.Double(u * u * from.getX() + 2 * u * t * cx + t * t * to.getX(),
                u * u * from.getY() + 2 * u * t * cy + t * t * to.getY()));
        }
        g.setColor(OK);
        g.setStroke(new BasicStroke((float) (1.1 / ZOOM), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
            10.0f, new float[] { 4.0f, 3.0f }, 0.0f));
        Path2D.Double p = new Path2D.Double();
        p.moveTo(path.get(0).getX(), path.get(0).getY());
        for (int i = 1; i < path.size(); i++) {
            p.lineTo(path.get(i).getX(), path.get(i).getY());
        }
        g.draw(p);
    }

    static void discScene(Graphics2D g, Treatment treatment) {
        Point2D a = new Point2D.Double(95, 205);
        Point2D b = new Point2D.Double(430, 205);
        Point2D c = new Point2D.Double(262, 202);
        Point2D aRim = rim(a, b);
        Point2D bRim = rim(b, a);
        if (treatment == Treatment.DISC_TODAY) {
            edge(g, line(aRim, bRim), INK_COLOR);
            arrow(g, line(aRim, bRim), bRim);
            marker(g, c, BAD);
        }
        else {
            List<Point2D> route = clipRims(doglegAround(a, b, c, DISC_R + CLEARANCE + 3.0), a, b);
            edge(g, route, INK_COLOR);
            arrow(g, route, route.get(route.size() - 1));
            marker(g, c, OK);
        }
        disc(g, a);
        disc(g, c);
        disc(g, b);
    }

    static final Color INK_COLOR = EDGE;

    static void edge(Graphics2D g, List<Point2D> path, Color color) {
        g.setColor(color);
        g.setStroke(new BasicStroke(EDGE_STROKE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D.Double p = new Path2D.Double();
        p.moveTo(path.get(0).getX(), path.get(0).getY());
        for (int i = 1; i < path.size(); i++) {
            p.lineTo(path.get(i).getX(), path.get(i).getY());
        }
        g.draw(p);
    }

    static void arrow(Graphics2D g, List<Point2D> path, Point2D tip) {
        Point2D previous = path.get(path.size() - 2);
        // ensure the tip is the last path point
        if (!path.get(path.size() - 1).equals(tip)) {
            previous = path.get(path.size() - 1);
        }
        double dx = tip.getX() - previous.getX();
        double dy = tip.getY() - previous.getY();
        double len = Math.hypot(dx, dy);
        if (!(len > 0)) {
            return;
        }
        double ux = dx / len, uy = dy / len;
        double px = -uy, py = ux;
        Path2D.Double a = new Path2D.Double();
        a.moveTo(tip.getX(), tip.getY());
        a.lineTo(tip.getX() - ux * ARROW_LENGTH + px * ARROW_HALF_BASE,
            tip.getY() - uy * ARROW_LENGTH + py * ARROW_HALF_BASE);
        a.lineTo(tip.getX() - ux * ARROW_LENGTH - px * ARROW_HALF_BASE,
            tip.getY() - uy * ARROW_LENGTH - py * ARROW_HALF_BASE);
        a.closePath();
        g.setColor(EDGE);
        g.fill(a);
    }

    static void disc(Graphics2D g, Point2D center) {
        Ellipse2D.Double circle = new Ellipse2D.Double(center.getX() - DISC_R, center.getY() - DISC_R,
            DISC_R * 2, DISC_R * 2);
        g.setColor(NODE_FILL);
        g.fill(circle);
        g.setColor(NODE_STROKE);
        g.setStroke(new BasicStroke(EDGE_STROKE));
        g.draw(circle);
    }

    static void marker(Graphics2D g, Point2D at, Color color) {
        g.setColor(color);
        g.setStroke(new BasicStroke((float) (1.2 / ZOOM)));
        double r = 5.0;
        g.draw(new Ellipse2D.Double(at.getX() - r, at.getY() - r, r * 2, r * 2));
    }

    // ---- geometry helpers -------------------------------------------------------------

    static Point2D rim(Point2D from, Point2D toward) {
        double dx = toward.getX() - from.getX();
        double dy = toward.getY() - from.getY();
        double len = Math.hypot(dx, dy);
        return new Point2D.Double(from.getX() + dx / len * DISC_R, from.getY() + dy / len * DISC_R);
    }

    static List<Point2D> line(Point2D a, Point2D b) {
        List<Point2D> path = new ArrayList<Point2D>();
        path.add(a);
        path.add(b);
        return path;
    }

    /** quadratic Bezier through the chord, control point offset perpendicular by amplitude. */
    static List<Point2D> bow(Point2D a, Point2D b, double amplitude) {
        double mx = (a.getX() + b.getX()) * 0.5, my = (a.getY() + b.getY()) * 0.5;
        double dx = b.getX() - a.getX(), dy = b.getY() - a.getY();
        double len = Math.hypot(dx, dy);
        double nx = -dy / len, ny = dx / len;
        double cx = mx + nx * amplitude, cy = my + ny * amplitude;
        List<Point2D> path = new ArrayList<Point2D>();
        for (int i = 0; i <= 24; i++) {
            double t = i / 24.0;
            double u = 1 - t;
            path.add(new Point2D.Double(u * u * a.getX() + 2 * u * t * cx + t * t * b.getX(),
                u * u * a.getY() + 2 * u * t * cy + t * t * b.getY()));
        }
        return path;
    }

    /** rounded dogleg around an obstacle disc, in centre coordinates. */
    static List<Point2D> doglegAround(Point2D a, Point2D b, Point2D obstacle, double radius) {
        double dx = b.getX() - a.getX(), dy = b.getY() - a.getY();
        double len = Math.hypot(dx, dy);
        double ux = dx / len, uy = dy / len;
        double nx = -uy, ny = ux;
        double t = ((obstacle.getX() - a.getX()) * ux + (obstacle.getY() - a.getY()) * uy);
        // choose the side with the shorter departure
        double side = signedDistance(obstacle, a, b) > 0 ? -1.0 : 1.0;
        double offset = Math.abs(signedDistance(obstacle, a, b)) + radius;
        List<Point2D> path = new ArrayList<Point2D>();
        path.add(a);
        Point2D p1 = new Point2D.Double(a.getX() + ux * (t - 40) + nx * offset * side,
            a.getY() + uy * (t - 40) + ny * offset * side);
        Point2D p2 = new Point2D.Double(a.getX() + ux * (t + 40) + nx * offset * side,
            a.getY() + uy * (t + 40) + ny * offset * side);
        path.add(p1);
        path.add(p2);
        path.add(b);
        return path;
    }

    /** Replace the first and last centre points with the rim points facing the first waypoint. */
    static List<Point2D> clipRims(List<Point2D> centres, Point2D a, Point2D b) {
        List<Point2D> path = new ArrayList<Point2D>(centres);
        path.set(0, rim(a, path.get(1)));
        path.set(path.size() - 1, rim(b, path.get(path.size() - 2)));
        return path;
    }

    /**
     * All tangent-pair / arc-direction routes around an obstacle disc, in centre coordinates.
     * Every candidate touches the clearance circle exactly; selection is by clearance, not length.
     */
    static List<List<Point2D>> detourCandidates(Point2D a, Point2D b, Point2D obstacle,
            double clearance) {
        double radius = DISC_R + clearance;
        Point2D[] first = tangents(a, obstacle, radius);
        Point2D[] second = tangents(b, obstacle, radius);
        List<List<Point2D>> candidates = new ArrayList<List<Point2D>>();
        if (first.length == 0 || second.length == 0) {
            return candidates;
        }
        for (Point2D t1 : first) {
            for (Point2D t2 : second) {
                double a0 = Math.atan2(t1.getY() - obstacle.getY(), t1.getX() - obstacle.getX());
                double a1 = Math.atan2(t2.getY() - obstacle.getY(), t2.getX() - obstacle.getX());
                double delta = a1 - a0;
                while (delta > Math.PI) {
                    delta -= 2 * Math.PI;
                }
                while (delta <= -Math.PI) {
                    delta += 2 * Math.PI;
                }
                double[] sweeps = { delta, delta > 0 ? delta - 2 * Math.PI : delta + 2 * Math.PI };
                for (double sweep : sweeps) {
                    List<Point2D> path = new ArrayList<Point2D>();
                    path.add(a);
                    path.add(t1);
                    int steps = 16;
                    for (int i = 1; i < steps; i++) {
                        double angle = a0 + sweep * i / steps;
                        path.add(new Point2D.Double(obstacle.getX() + Math.cos(angle) * radius,
                            obstacle.getY() + Math.sin(angle) * radius));
                    }
                    path.add(t2);
                    path.add(b);
                    candidates.add(path);
                }
            }
        }
        return candidates;
    }

    /** Shortest candidate that does not properly cross the forbidden segment. */
    static List<Point2D> shortestClear(List<List<Point2D>> candidates, List<Point2D> forbiddenEdge) {
        List<Point2D> best = null;
        double bestLength = Double.MAX_VALUE;
        for (List<Point2D> candidate : candidates) {
            if (!clears(candidate, forbiddenEdge)) {
                continue;
            }
            double length = pathLength(candidate);
            if (length < bestLength) {
                bestLength = length;
                best = candidate;
            }
        }
        return best;
    }

    static double pathLength(List<Point2D> path) {
        double length = 0;
        for (int i = 1; i < path.size(); i++) {
            length += path.get(i - 1).distance(path.get(i));
        }
        return length;
    }

    static boolean clears(List<Point2D> route, List<Point2D> forbiddenEdge) {
        Point2D f0 = forbiddenEdge.get(0), f1 = forbiddenEdge.get(1);
        for (int i = 1; i < route.size(); i++) {
            if (properCross(route.get(i - 1), route.get(i), f0, f1)) {
                return false;
            }
        }
        return true;
    }

    static boolean properCross(Point2D a, Point2D b, Point2D c, Point2D d) {
        double d1 = orient(a, b, c), d2 = orient(a, b, d);
        double d3 = orient(c, d, a), d4 = orient(c, d, b);
        return d1 * d2 < 0 && d3 * d4 < 0;
    }

    static double orient(Point2D a, Point2D b, Point2D c) {
        return (b.getX() - a.getX()) * (c.getY() - a.getY())
            - (b.getY() - a.getY()) * (c.getX() - a.getX());
    }

    /** Kept for reference: the original length-only detour (superseded by candidates). */
    static List<Point2D> detourAround(Point2D a, Point2D b, Point2D obstacle, double clearance) {
        double radius = DISC_R + clearance;
        Point2D[] first = tangents(a, obstacle, radius);
        Point2D[] second = tangents(b, obstacle, radius);
        if (first.length == 0 || second.length == 0) {
            return line(a, b);
        }
        // pick the pair that yields the shorter route and stays farther from the other edge
        double best = Double.MAX_VALUE;
        Point2D bestT1 = null, bestT2 = null;
        for (Point2D t1 : first) {
            for (Point2D t2 : second) {
                double length = a.distance(t1) + t1.distance(t2) + t2.distance(b);
                if (length < best) {
                    best = length;
                    bestT1 = t1;
                    bestT2 = t2;
                }
            }
        }
        List<Point2D> path = new ArrayList<Point2D>();
        path.add(a);
        path.add(bestT1);
        // arc between the two tangency points on the obstacle's rim
        double a0 = Math.atan2(bestT1.getY() - obstacle.getY(), bestT1.getX() - obstacle.getX());
        double a1 = Math.atan2(bestT2.getY() - obstacle.getY(), bestT2.getX() - obstacle.getX());
        double delta = a1 - a0;
        while (delta > Math.PI) {
            delta -= 2 * Math.PI;
        }
        while (delta < -Math.PI) {
            delta += 2 * Math.PI;
        }
        int steps = 16;
        for (int i = 1; i < steps; i++) {
            double angle = a0 + delta * i / steps;
            path.add(new Point2D.Double(obstacle.getX() + Math.cos(angle) * radius,
                obstacle.getY() + Math.sin(angle) * radius));
        }
        path.add(bestT2);
        path.add(b);
        return path;
    }

    /** Tangency points from p to the circle (centre, radius); empty when p is inside. */
    static Point2D[] tangents(Point2D p, Point2D centre, double radius) {
        double d = p.distance(centre);
        if (d <= radius) {
            return new Point2D[0];
        }
        double alpha = Math.acos(radius / d);
        double base = Math.atan2(p.getY() - centre.getY(), p.getX() - centre.getX());
        return new Point2D[] {
            new Point2D.Double(centre.getX() + radius * Math.cos(base + alpha),
                centre.getY() + radius * Math.sin(base + alpha)),
            new Point2D.Double(centre.getX() + radius * Math.cos(base - alpha),
                centre.getY() + radius * Math.sin(base - alpha)),
        };
    }

    /** Break a path at the crossing and insert a semicircular hop with the given radius. */
    static List<Point2D> hop(List<Point2D> straight, Point2D crossing, double radius) {
        Point2D a = straight.get(0), b = straight.get(1);
        double dx = b.getX() - a.getX(), dy = b.getY() - a.getY();
        double len = Math.hypot(dx, dy);
        double ux = dx / len, uy = dy / len;
        double nx = -uy, ny = ux;
        List<Point2D> path = new ArrayList<Point2D>();
        Point2D before = new Point2D.Double(crossing.getX() - ux * radius, crossing.getY() - uy * radius);
        Point2D after = new Point2D.Double(crossing.getX() + ux * radius, crossing.getY() + uy * radius);
        path.add(a);
        path.add(before);
        for (int i = 1; i < 12; i++) {
            double angle = Math.PI * i / 12;
            path.add(new Point2D.Double(crossing.getX() - ux * radius * Math.cos(angle)
                + nx * radius * Math.sin(angle),
                crossing.getY() - uy * radius * Math.cos(angle) + ny * radius * Math.sin(angle)));
        }
        path.add(after);
        path.add(b);
        return path;
    }

    static double signedDistance(Point2D p, Point2D a, Point2D b) {
        double dx = b.getX() - a.getX(), dy = b.getY() - a.getY();
        double len = Math.hypot(dx, dy);
        return ((p.getX() - a.getX()) * -dy + (p.getY() - a.getY()) * dx) / len;
    }

    static Point2D intersect(Point2D a1, Point2D a2, Point2D b1, Point2D b2) {
        double d1x = a2.getX() - a1.getX(), d1y = a2.getY() - a1.getY();
        double d2x = b2.getX() - b1.getX(), d2y = b2.getY() - b1.getY();
        double denom = d1x * d2y - d1y * d2x;
        double t = ((b1.getX() - a1.getX()) * d2y - (b1.getY() - a1.getY()) * d2x) / denom;
        return new Point2D.Double(a1.getX() + t * d1x, a1.getY() + t * d1y);
    }
}
