import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Design mockups for the Graph Workspace node-separation change.
 *
 * Constants and rules mirror production code:
 *   - node radius = NODE_RADIUS(8) * prominence scale, capped at 14   (GraphStreamLayoutEngine.NODE_RADIUS, NodeProminence.MAX_SCALE)
 *   - today's spring rest length = 24 for relationship and containment links (TypedSpringBox.REST_LENGTH)
 *   - today's node labels: fixed slot above the disc, full text, no avoidance,
 *     painted at font size/zoom so screen size is constant (GraphPainter.paintLabels)
 *
 * Proposed panels are produced by the proposed algorithms, not hand-placed:
 *   - hard invariant: centerDistance >= r_i + r_j + MIN_GAP via pairwise projection
 *   - labels: screen-space candidate slots, priority order (selection first), leader lines,
 *     ladder full@12 -> full@9 -> truncated@12 -> truncated@9 -> hover-only
 */
public final class NodeSeparationMockups {

    static final double NODE_RADIUS = 8.0;
    static final double MAX_SCALE = 1.75;
    static final double REST_LENGTH = 24.0;
    static final double MIN_GAP = 6.0;

    static final Font FULL = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    static final Font DENSE_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 9);
    static final Font TITLE = new Font(Font.SANS_SERIF, Font.BOLD, 13);
    static final Font SMALL = new Font(Font.SANS_SERIF, Font.PLAIN, 10);

    static final Color INK = new Color(0x1F, 0x24, 0x2B);
    static final Color HULL = new Color(0x3B, 0x6F, 0xA8);
    static final Color DISC_FILL = new Color(0xEA, 0xF2, 0xFA);
    static final Color DISC_STROKE = new Color(0x2F, 0x5C, 0x8A);
    static final Color MUTED = new Color(0x6B, 0x74, 0x80);
    static final Color PAPER = new Color(0xFB, 0xFC, 0xFE);
    static final Color EDGE = new Color(0x9A, 0xA4, 0xB0);
    static final Color OK = new Color(0x1B, 0x7F, 0x4B);
    static final Color BAD = new Color(0xB3, 0x2B, 0x2B);
    static final Color WARN = new Color(0xA9, 0x62, 0x00);

    static final FontRenderContext FRC = new FontRenderContext(null, true, true);

    /** (b) displacement-first: keep the full name and push the label out before shortening it. */
    static final Object[][] LADDER_DISPLACEMENT_FIRST = {
        {FULL, Boolean.FALSE, Boolean.FALSE}, {FULL, Boolean.FALSE, Boolean.TRUE},
        {DENSE_FONT, Boolean.FALSE, Boolean.FALSE}, {DENSE_FONT, Boolean.FALSE, Boolean.TRUE},
        {FULL, Boolean.TRUE, Boolean.FALSE}, {FULL, Boolean.TRUE, Boolean.TRUE},
        {DENSE_FONT, Boolean.TRUE, Boolean.FALSE}, {DENSE_FONT, Boolean.TRUE, Boolean.TRUE}};

    /** (a) truncation-first: shorten the text before allowing a displaced slot. */
    static final Object[][] LADDER_TRUNCATION_FIRST = {
        {FULL, Boolean.FALSE, Boolean.FALSE}, {DENSE_FONT, Boolean.FALSE, Boolean.FALSE},
        {FULL, Boolean.TRUE, Boolean.FALSE}, {DENSE_FONT, Boolean.TRUE, Boolean.FALSE},
        {FULL, Boolean.FALSE, Boolean.TRUE}, {DENSE_FONT, Boolean.FALSE, Boolean.TRUE},
        {FULL, Boolean.TRUE, Boolean.TRUE}, {DENSE_FONT, Boolean.TRUE, Boolean.TRUE}};

    static Object[][] LADDER = LADDER_DISPLACEMENT_FIRST;

    /** Number of leader lines that cross each other: the visual cost of long displacement. */
    static int leaderCrossings(List<Placed> labels, double zoom, double ox, double oy) {
        List<double[]> segments = new ArrayList<>();
        for (Placed label : labels) {
            if (label.slot == Slot.ABOVE || label.slot == Slot.BELOW) continue;
            double cx = label.node.x * zoom + ox, cy = label.node.y * zoom + oy, r = label.node.r * zoom;
            double dx = label.x - cx, dy = label.y - cy, d = Math.max(1e-6, Math.hypot(dx, dy));
            segments.add(new double[]{cx + dx / d * r, cy + dy / d * r, label.x, label.y});
        }
        int n = 0;
        for (int i = 0; i < segments.size(); i++)
            for (int j = i + 1; j < segments.size(); j++)
                if (crosses(segments.get(i), segments.get(j))) n++;
        return n;
    }

    static boolean crosses(double[] a, double[] b) {
        return orient(a[0], a[1], a[2], a[3], b[0], b[1]) * orient(a[0], a[1], a[2], a[3], b[2], b[3]) < 0
            && orient(b[0], b[1], b[2], b[3], a[0], a[1]) * orient(b[0], b[1], b[2], b[3], a[2], a[3]) < 0;
    }

    static double orient(double ax, double ay, double bx, double by, double cx, double cy) {
        return (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
    }

    /** Mean screen distance from a node to its own label. */
    static double meanLeaderLength(List<Placed> labels, double zoom, double ox, double oy) {
        double total = 0; int n = 0;
        for (Placed label : labels) {
            double cx = label.node.x * zoom + ox, cy = label.node.y * zoom + oy;
            total += Math.hypot(label.x - cx, label.y - cy); n++;
        }
        return n == 0 ? 0 : total / n;
    }

    static double textWidth(String text, Font font) {
        return font.getStringBounds(text, FRC).getWidth();
    }

    static double textHeight(Font font) {
        return font.getLineMetrics("Ag", FRC).getHeight();
    }

    static final class Node {
        final String name;
        final double r;
        final boolean selected;
        double x, y;
        Node(String name, double r, boolean selected, double x, double y) {
            this.name = name; this.r = r; this.selected = selected; this.x = x; this.y = y;
        }
    }

    static final class Scene {
        final List<Node> nodes;
        final int[][] edges;
        Scene(List<Node> nodes, int[][] edges) { this.nodes = nodes; this.edges = edges; }
    }

    // ------------------------------------------------------------ proposed projection

    static int countDiscOverlaps(List<Node> nodes) {
        int n = 0;
        for (int i = 0; i < nodes.size(); i++)
            for (int j = i + 1; j < nodes.size(); j++) {
                Node a = nodes.get(i), b = nodes.get(j);
                if (Math.hypot(b.x - a.x, b.y - a.y) < a.r + b.r - 1e-9) n++;
            }
        return n;
    }

    static void separate(List<Node> nodes, int iterations) {
        for (int it = 0; it < iterations; it++) {
            for (int i = 0; i < nodes.size(); i++)
                for (int j = i + 1; j < nodes.size(); j++) {
                    Node a = nodes.get(i), b = nodes.get(j);
                    double dx = b.x - a.x, dy = b.y - a.y;
                    double d = Math.hypot(dx, dy);
                    if (d == 0.0) { dx = 1.0; dy = 0.0; d = 1.0; }
                    double need = a.r + b.r + MIN_GAP;
                    if (d >= need) continue;
                    double push = (need - d) / 2.0, ux = dx / d, uy = dy / d;
                    a.x -= ux * push; a.y -= uy * push;
                    b.x += ux * push; b.y += uy * push;
                }
        }
    }

    // ------------------------------------------------------------ proposed label placement

    enum Slot { ABOVE, BELOW, RIGHT, LEFT, ABOVE_RIGHT, ABOVE_LEFT, BELOW_RIGHT, BELOW_LEFT,
                ABOVE_FAR, BELOW_FAR, RIGHT_FAR, LEFT_FAR }

    static final Slot[] NEAR_SLOTS = {Slot.ABOVE, Slot.BELOW, Slot.RIGHT, Slot.LEFT,
        Slot.ABOVE_RIGHT, Slot.ABOVE_LEFT, Slot.BELOW_RIGHT, Slot.BELOW_LEFT};
    static final Slot[] FAR_SLOTS = {Slot.ABOVE_FAR, Slot.BELOW_FAR, Slot.RIGHT_FAR, Slot.LEFT_FAR};

    static final class Placed {
        final Node node; final String text; final Font font;
        final double x, y, w, h; final Slot slot;
        Placed(Node node, String text, Font font, double x, double y, double w, double h, Slot slot) {
            this.node = node; this.text = text; this.font = font;
            this.x = x; this.y = y; this.w = w; this.h = h; this.slot = slot;
        }
        Rectangle2D rect() { return new Rectangle2D.Double(x - w / 2, y - h / 2, w, h); }
        boolean truncated() { return !text.equals(node.name); }
    }

    static double slotMaxWidth(Slot slot) {
        switch (slot) {
            case ABOVE: case BELOW: case ABOVE_FAR: case BELOW_FAR: return 200.0;
            case RIGHT: case LEFT: case RIGHT_FAR: case LEFT_FAR: return 130.0;
            default: return 150.0;
        }
    }

    static double[] slotAnchor(Slot slot, double cx, double cy, double r, double w, double h, double gap) {
        switch (slot) {
            case ABOVE:       return new double[]{cx, cy - r - gap - h / 2};
            case BELOW:       return new double[]{cx, cy + r + gap + h / 2};
            case RIGHT:       return new double[]{cx + r + gap + w / 2, cy};
            case LEFT:        return new double[]{cx - r - gap - w / 2, cy};
            case ABOVE_FAR:   return new double[]{cx, cy - r - 30.0 - h / 2};
            case BELOW_FAR:   return new double[]{cx, cy + r + 30.0 + h / 2};
            case RIGHT_FAR:   return new double[]{cx + r + 30.0 + w / 2, cy};
            case LEFT_FAR:    return new double[]{cx - r - 30.0 - w / 2, cy};
            case ABOVE_RIGHT: return new double[]{cx + r + gap + w / 2, cy - r - gap - h / 2};
            case ABOVE_LEFT:  return new double[]{cx - r - gap - w / 2, cy - r - gap - h / 2};
            case BELOW_RIGHT: return new double[]{cx + r + gap + w / 2, cy + r + gap + h / 2};
            default:          return new double[]{cx - r - gap - w / 2, cy + r + gap + h / 2};
        }
    }

    static String truncateTo(String text, Font font, double limit) {
        if (textWidth(text, font) <= limit) return text;
        for (int cut = text.length() - 1; cut > 2; cut--) {
            String candidate = text.substring(0, cut).stripTrailing() + "\u2026";
            if (textWidth(candidate, font) <= limit) return candidate;
        }
        return text.substring(0, Math.min(3, text.length())) + "\u2026";
    }

    static List<Placed> currentLabels(List<Node> nodes, double zoom, double ox, double oy) {
        List<Placed> out = new ArrayList<>();
        for (Node node : nodes) {
            double cx = node.x * zoom + ox, cy = node.y * zoom + oy, r = node.r * zoom;
            double w = textWidth(node.name, FULL), h = textHeight(FULL);
            out.add(new Placed(node, node.name, FULL, cx, cy - r - 8.0 - h / 2, w, h, Slot.ABOVE));
        }
        return out;
    }

    static List<Placed> proposedLabels(List<Node> nodes, double zoom, double ox, double oy,
                                       Rectangle2D viewport, List<Rectangle2D> discRects, List<Node> hiddenOut) {
        return proposedLabels(nodes, zoom, ox, oy, viewport, discRects, hiddenOut, null);
    }

    /** obstacles = disc rects plus any already-placed enclosure label, so both label kinds share one obstacle set. */
    static List<Placed> proposedLabels(List<Node> nodes, double zoom, double ox, double oy,
                                       Rectangle2D viewport, List<Rectangle2D> discRects, List<Node> hiddenOut,
                                       Rectangle2D enclosureLabel) {
        List<Placed> out = new ArrayList<>();
        List<Rectangle2D> taken = new ArrayList<>(discRects);
        if (enclosureLabel != null) taken.add(enclosureLabel);
        List<Node> order = new ArrayList<>(nodes);
        order.sort(Comparator.comparingDouble((Node n) -> n.selected ? 0.0 : 1.0).thenComparingDouble(n -> -n.r));

        for (Node node : order) {
            double cx = node.x * zoom + ox, cy = node.y * zoom + oy, r = Math.max(2.0, node.r * zoom);
            boolean placed = false;
            Object[][] ladder = LADDER;
            for (int step = 0; step < ladder.length && !placed; step++) {
                Font font = (Font) ladder[step][0];
                boolean truncating = (Boolean) ladder[step][1];
                Slot[] candidates = (Boolean) ladder[step][2] ? FAR_SLOTS : NEAR_SLOTS;
                for (Slot slot : candidates) {
                    double limit = slotMaxWidth(slot);
                    if (!truncating && textWidth(node.name, font) > limit) continue;
                    String text = truncating ? truncateTo(node.name, font, limit) : node.name;
                    double w = textWidth(text, font), h = textHeight(font);
                    double[] a = slotAnchor(slot, cx, cy, r, w, h, 6.0);
                    Rectangle2D rect = new Rectangle2D.Double(a[0] - w / 2, a[1] - h / 2, w, h);
                    if (!viewport.contains(rect)) continue;
                    boolean clash = false;
                    for (Rectangle2D other : taken) if (other.intersects(rect)) { clash = true; break; }
                    if (clash) continue;
                    taken.add(rect);
                    out.add(new Placed(node, text, font, a[0], a[1], w, h, slot));
                    placed = true;
                    break;
                }
            }
            if (!placed && node.selected) {
                double w = textWidth(node.name, FULL), h = textHeight(FULL);
                double[] a = slotAnchor(Slot.ABOVE, cx, cy, r, w, h, 5.0);
                taken.add(new Rectangle2D.Double(a[0] - w / 2, a[1] - h / 2, w, h));
                out.add(new Placed(node, node.name, FULL, a[0], a[1], w, h, Slot.ABOVE));
                placed = true;
            }
            if (!placed) hiddenOut.add(node);
        }
        return out;
    }

    static int labelCollisions(List<Placed> labels) {
        int n = 0;
        for (int i = 0; i < labels.size(); i++)
            for (int j = i + 1; j < labels.size(); j++)
                if (labels.get(i).rect().intersects(labels.get(j).rect())) n++;
        return n;
    }

    static int labelDiscCollisions(List<Placed> labels, List<Node> nodes, double zoom, double ox, double oy) {
        int n = 0;
        for (Placed label : labels)
            for (Node node : nodes) {
                if (label.node == node) continue;
                double cx = node.x * zoom + ox, cy = node.y * zoom + oy, r = node.r * zoom;
                if (label.rect().intersects(new Rectangle2D.Double(cx - r, cy - r, 2 * r, 2 * r))) n++;
            }
        return n;
    }

    static String histogram(List<Placed> placed, List<Node> hidden) {
        int full = 0, dense = 0, trunc = 0;
        for (Placed p : placed) {
            if (p.truncated()) trunc++;
            else if (p.font == DENSE_FONT) dense++;
            else full++;
        }
        return "full " + full + " | dense font " + dense + " | truncated " + trunc + " | hover-only " + hidden.size();
    }

    static List<Rectangle2D> discRects(List<Node> nodes, double zoom, double ox, double oy) {
        List<Rectangle2D> out = new ArrayList<>();
        for (Node node : nodes) {
            double cx = node.x * zoom + ox, cy = node.y * zoom + oy, r = Math.max(2.0, node.r * zoom);
            out.add(new Rectangle2D.Double(cx - r, cy - r, 2 * r, 2 * r));
        }
        return out;
    }

    // ------------------------------------------------------------ drawing

    static Rectangle2D hullLabelRect(Scene scene, double zoom, double ox, double oy) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        for (Node node : scene.nodes) {
            double cx = node.x * zoom + ox, cy = node.y * zoom + oy, r = node.r * zoom;
            minX = Math.min(minX, cx - r);
            minY = Math.min(minY, cy - r);
        }
        minX -= 13.0; minY -= 13.0;
        double w = textWidth("Basic Definitions and Theorems", SMALL);
        double h = textHeight(SMALL);
        return new Rectangle2D.Double(minX + 2, minY - 5 - h, w, h);
    }

    static void drawScene(Graphics2D g, Scene scene, List<Placed> labels, List<Node> hidden, String caption,
                          String note, double zoom, double ox, double oy, Rectangle2D viewport,
                          boolean hull, Color captionColor) {
        List<Node> nodes = scene.nodes;
        g.setColor(PAPER);
        g.fill(viewport);
        g.setColor(new Color(0xD5, 0xDC, 0xE4));
        g.setStroke(new BasicStroke(1f));
        g.draw(viewport);

        if (hull) {
            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            for (Node node : nodes) {
                double cx = node.x * zoom + ox, cy = node.y * zoom + oy, r = node.r * zoom;
                minX = Math.min(minX, cx - r); maxX = Math.max(maxX, cx + r);
                minY = Math.min(minY, cy - r); maxY = Math.max(maxY, cy + r);
            }
            double pad = 13.0;
            minX -= pad; minY -= pad; maxX += pad; maxY += pad;
            double cut = Math.min(maxX - minX, maxY - minY) * 0.16;
            int[] xs = {(int) (minX + cut), (int) (maxX - cut), (int) maxX, (int) maxX,
                        (int) (maxX - cut), (int) (minX + cut), (int) minX, (int) minX};
            int[] ys = {(int) minY, (int) minY, (int) (minY + cut), (int) (maxY - cut),
                        (int) maxY, (int) maxY, (int) (maxY - cut), (int) (minY + cut)};
            Polygon oct = new Polygon(xs, ys, 8);
            g.setColor(new Color(0xE8, 0xEF, 0xF7));
            g.fill(oct);
            g.setColor(HULL);
            g.setStroke(new BasicStroke(1.6f));
            g.draw(oct);
            g.setFont(SMALL);
            g.setColor(new Color(0x2A, 0x4E, 0x76));
            g.drawString("Basic Definitions and Theorems", (int) minX + 2, (int) minY - 5);
        }

        g.setStroke(new BasicStroke(1.1f));
        g.setColor(EDGE);
        for (int[] edge : scene.edges) {
            Node a = nodes.get(edge[0]), b = nodes.get(edge[1]);
            g.drawLine((int) (a.x * zoom + ox), (int) (a.y * zoom + oy), (int) (b.x * zoom + ox), (int) (b.y * zoom + oy));
        }

        for (Placed label : labels) {
            if (label.slot == Slot.ABOVE || label.slot == Slot.BELOW) continue;
            double cx = label.node.x * zoom + ox, cy = label.node.y * zoom + oy, r = label.node.r * zoom;
            double dx = label.x - cx, dy = label.y - cy, d = Math.max(1e-6, Math.hypot(dx, dy));
            g.setColor(new Color(0xB0, 0xB8, 0xC2));
            g.setStroke(new BasicStroke(0.8f));
            g.drawLine((int) (cx + dx / d * r), (int) (cy + dy / d * r), (int) label.x, (int) label.y);
        }

        for (Node node : nodes) {
            double cx = node.x * zoom + ox, cy = node.y * zoom + oy, r = Math.max(2.0, node.r * zoom);
            g.setColor(DISC_FILL);
            g.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
            g.setColor(node.selected ? WARN : DISC_STROKE);
            g.setStroke(new BasicStroke(node.selected ? 2.2f : 1.4f));
            g.draw(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
        }

        for (Node node : hidden) {
            double cx = node.x * zoom + ox, cy = node.y * zoom + oy, r = Math.max(2.0, node.r * zoom);
            g.setColor(new Color(0x8A, 0x93, 0x9E));
            g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10f, new float[]{2f, 2f}, 0f));
            g.draw(new Ellipse2D.Double(cx - r - 3, cy - r - 3, 2 * r + 6, 2 * r + 6));
        }

        for (Placed label : labels) {
            g.setFont(label.font);
            g.setColor(INK);
            FontMetrics fm = g.getFontMetrics(label.font);
            float baseline = (float) (label.y + (fm.getAscent() - fm.getDescent()) / 2.0);
            g.drawString(label.text, (float) (label.x - label.w / 2), baseline);
        }

        g.setFont(TITLE);
        g.setColor(captionColor);
        g.drawString(caption, (int) viewport.getX() + 10, (int) viewport.getY() + 20);
        g.setFont(SMALL);
        g.setColor(MUTED);
        String[] parts = note.split("\n");
        for (int i = 0; i < parts.length; i++) {
            g.drawString(parts[i], (int) viewport.getX() + 10,
                (int) (viewport.getY() + viewport.getHeight() - 8 - (parts.length - 1 - i) * 13));
        }
    }

    static BufferedImage blank(int w, int h) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return image;
    }

    static Graphics2D graphics(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }

    static void save(BufferedImage image, File dir, String name) throws Exception {
        ImageIO.write(image, "png", new File(dir, name));
        System.out.println("  wrote " + name);
    }

    // ------------------------------------------------------------ scenarios

    /** Two connected prominent nodes: the tightest case today's constants allow. */
    static Scene connectedPair() {
        List<Node> nodes = new ArrayList<>();
        nodes.add(new Node("Theorem", NODE_RADIUS * MAX_SCALE, false, 0, 0));
        nodes.add(new Node("Axiom of Choice", NODE_RADIUS * MAX_SCALE, false, REST_LENGTH, 0));
        return new Scene(nodes, new int[][]{{0, 1}});
    }

    /** Sparse: three nodes at containment-ring distances, wide names, one selected. */
    static Scene sparse() {
        List<Node> nodes = new ArrayList<>();
        nodes.add(new Node("Theorem", NODE_RADIUS * MAX_SCALE, false, -46, 0));
        nodes.add(new Node("Foundation / Regularity", NODE_RADIUS, true, 0, 0));
        nodes.add(new Node("Axiom of Choice", NODE_RADIUS * MAX_SCALE, false, 60, 10));
        return new Scene(nodes, new int[][]{{0, 2}});
    }

    /** Dense: 12 nodes at exactly the invariant floor (spacing = 14 + 14 + MIN_GAP). */
    static Scene dense() {
        String[] names = {"Theorem", "Axiom of Choice", "Replacement Scheme", "Extensionality", "Pairing",
                          "Union", "Power Set", "Infinity", "Separation", "Foundation / Regularity",
                          "Comprehension", "Well-Ordering"};
        List<Node> nodes = new ArrayList<>();
        double spacing = NODE_RADIUS * MAX_SCALE * 2 + MIN_GAP;   // 34
        int columns = 4;
        for (int i = 0; i < names.length; i++) {
            double r = (i % 3 == 0) ? NODE_RADIUS * MAX_SCALE : NODE_RADIUS;
            nodes.add(new Node(names[i], r, i == 1,
                (i % columns - 1.5) * spacing, (i / columns - 1.0) * spacing));
        }
        return new Scene(nodes, new int[][]{{0, 5}, {1, 6}, {2, 7}});
    }

    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : ".");
        dir.mkdirs();

        // ---- 01: the hard invariant on discs -------------------------------------------------
        {
            Scene today = connectedPair();
            Scene fixed = connectedPair();
            separate(fixed.nodes, 400);
            double dToday = Math.hypot(today.nodes.get(1).x - today.nodes.get(0).x,
                today.nodes.get(1).y - today.nodes.get(0).y);
            double dFixed = Math.hypot(fixed.nodes.get(1).x - fixed.nodes.get(0).x,
                fixed.nodes.get(1).y - fixed.nodes.get(0).y);
            double need = today.nodes.get(0).r + today.nodes.get(1).r;
            BufferedImage image = blank(940, 330);
            Graphics2D g = graphics(image);
            Rectangle2D left = new Rectangle2D.Double(16, 16, 440, 296);
            Rectangle2D right = new Rectangle2D.Double(484, 16, 440, 296);

            List<Node> hiddenL = new ArrayList<>();
            List<Rectangle2D> discsL = discRects(today.nodes, 1.0, left.getCenterX() - REST_LENGTH / 2, left.getCenterY() + 8);
            drawScene(g, today, currentLabels(today.nodes, 1.0, left.getCenterX() - REST_LENGTH / 2, left.getCenterY() + 8),
                hiddenL, "TODAY: rest length 24, radii 14 + 14", String.format(Locale.ROOT,
                "centres %.0f apart, need %.0f: discs overlap by %.0f units\n"
                + "no non-overlap rule exists in the layout at all", dToday, need, need - dToday),
                1.0, left.getCenterX() - REST_LENGTH / 2, left.getCenterY() + 8, left, false, BAD);

            List<Node> hiddenR = new ArrayList<>();
            double oxR = right.getCenterX() - dFixed / 2;
            List<Rectangle2D> discsR = discRects(fixed.nodes, 1.0, oxR, right.getCenterY() + 8);
            List<Placed> labelsR = proposedLabels(fixed.nodes, 1.0, oxR, right.getCenterY() + 8,
                new Rectangle2D.Double(right.getX() + 4, right.getY() + 4, right.getWidth() - 8, right.getHeight() - 40),
                discsR, hiddenR);
            drawScene(g, fixed, labelsR, hiddenR, "PROPOSED: centre distance >= r1 + r2 + gap", String.format(Locale.ROOT,
                "centres %.0f apart, need %.0f: 0 overlaps, invariant holds\n"
                + "enforced as a projection on published positions, not stronger repulsion", dFixed, need),
                1.0, oxR, right.getCenterY() + 8, right, false, OK);
            g.dispose();
            save(image, dir, "01-disc-invariant.png");
            System.out.println("01 disc overlaps: today " + countDiscOverlaps(today.nodes)
                + " -> proposed " + countDiscOverlaps(fixed.nodes)
                + " | label collisions today " + labelCollisions(currentLabels(today.nodes, 1.0, 0, 0))
                + " -> proposed " + labelCollisions(labelsR));
        }

        // ---- 02: sparse ----------------------------------------------------------------------
        {
            Scene scene = sparse();
            separate(scene.nodes, 400);
            BufferedImage image = blank(1180, 440);
            Graphics2D g = graphics(image);
            Rectangle2D left = new Rectangle2D.Double(16, 16, 566, 400);
            Rectangle2D right = new Rectangle2D.Double(598, 16, 566, 400);
            Rectangle2D viewL = new Rectangle2D.Double(left.getX() + 4, left.getY() + 40, left.getWidth() - 8, left.getHeight() - 76);
            Rectangle2D viewR = new Rectangle2D.Double(right.getX() + 4, right.getY() + 40, right.getWidth() - 8, right.getHeight() - 76);

            double ox = left.getCenterX(), oy = left.getCenterY() + 16;
            List<Placed> current = currentLabels(scene.nodes, 1.0, ox, oy);
            drawScene(g, scene, current, new ArrayList<Node>(), "TODAY: full text, fixed slot above each disc",
                "label/label collisions: " + labelCollisions(current)
                + "   label/disc collisions: " + labelDiscCollisions(current, scene.nodes, 1.0, ox, oy) + "\n"
                + "the wide names are drawn on top of each other even though the discs are 46 and 60 units apart", 1.0, ox, oy, left, true, BAD);

            double ox2 = right.getCenterX(), oy2 = right.getCenterY() + 16;
            List<Node> hidden = new ArrayList<>();
            List<Placed> proposed = proposedLabels(scene.nodes, 1.0, ox2, oy2, viewR,
                discRects(scene.nodes, 1.0, ox2, oy2), hidden,
                viewR.contains(hullLabelRect(scene, 1.0, ox2, oy2)) ? hullLabelRect(scene, 1.0, ox2, oy2) : null);
            drawScene(g, scene, proposed, hidden, "PROPOSED: screen-space slots, priority, leader lines",
                histogram(proposed, hidden) + "\nlabel/label collisions: " + labelCollisions(proposed)
                + "   label/disc collisions: " + labelDiscCollisions(proposed, scene.nodes, 1.0, ox2, oy2),
                1.0, ox2, oy2, right, true, OK);
            g.dispose();
            save(image, dir, "02-sparse-current-vs-proposed.png");
            System.out.println("02 sparse: label collisions " + labelCollisions(current)
                + " -> " + labelCollisions(proposed)
                + " | label/disc " + labelDiscCollisions(current, scene.nodes, 1.0, ox, oy)
                + " -> " + labelDiscCollisions(proposed, scene.nodes, 1.0, ox2, oy2)
                + " | " + histogram(proposed, hidden));
        }

        // ---- 03: dense -----------------------------------------------------------------------
        {
            Scene scene = dense();
            int before = countDiscOverlaps(scene.nodes);
            separate(scene.nodes, 400);
            BufferedImage image = blank(1180, 480);
            Graphics2D g = graphics(image);
            Rectangle2D left = new Rectangle2D.Double(16, 16, 566, 440);
            Rectangle2D right = new Rectangle2D.Double(598, 16, 566, 440);
            Rectangle2D viewR = new Rectangle2D.Double(right.getX() + 4, right.getY() + 40, right.getWidth() - 8, right.getHeight() - 76);

            double ox = left.getCenterX(), oy = left.getCenterY();
            List<Placed> current = currentLabels(scene.nodes, 1.0, ox, oy);
            drawScene(g, scene, current, new ArrayList<Node>(), "TODAY: 12 nodes at the minimum legal spacing",
                "label/label collisions: " + labelCollisions(current) + " of " + (current.size() * (current.size() - 1) / 2)
                + " pairs\nlabel/disc collisions: " + labelDiscCollisions(current, scene.nodes, 1.0, ox, oy)
                + "   - text fails long before the discs touch", 1.0, ox, oy, left, true, BAD);

            double ox2 = right.getCenterX(), oy2 = right.getCenterY();
            List<Node> hidden = new ArrayList<>();
            List<Placed> proposed = proposedLabels(scene.nodes, 1.0, ox2, oy2, viewR,
                discRects(scene.nodes, 1.0, ox2, oy2), hidden, hullLabelRect(scene, 1.0, ox2, oy2));
            drawScene(g, scene, proposed, hidden, "PROPOSED: ladder degrades instead of piling up",
                histogram(proposed, hidden) + "\nlabel/label collisions: " + labelCollisions(proposed)
                + "   label/disc collisions: " + labelDiscCollisions(proposed, scene.nodes, 1.0, ox2, oy2)
                + "   (dashed ring = hover-only)", 1.0, ox2, oy2, right, true, OK);
            g.dispose();
            save(image, dir, "03-dense-current-vs-proposed.png");
            System.out.println("03 dense: disc overlaps " + before + " -> " + countDiscOverlaps(scene.nodes)
                + " | label collisions " + labelCollisions(current) + " -> " + labelCollisions(proposed)
                + " | label/disc " + labelDiscCollisions(current, scene.nodes, 1.0, ox, oy)
                + " -> " + labelDiscCollisions(proposed, scene.nodes, 1.0, ox2, oy2)
                + " | " + histogram(proposed, hidden));

            // ---- 04: zoom --------------------------------------------------------------------
            BufferedImage zoom = blank(1180, 480);
            Graphics2D zg = graphics(zoom);
            Rectangle2D zl = new Rectangle2D.Double(16, 16, 566, 440);
            Rectangle2D zr = new Rectangle2D.Double(598, 16, 566, 440);
            Rectangle2D viewL = new Rectangle2D.Double(zl.getX() + 4, zl.getY() + 40, zl.getWidth() - 8, zl.getHeight() - 76);
            Rectangle2D zoomViewR = new Rectangle2D.Double(zr.getX() + 4, zr.getY() + 40, zr.getWidth() - 8, zr.getHeight() - 76);
            List<Node> hidden100 = new ArrayList<>();
            List<Placed> at100 = proposedLabels(scene.nodes, 1.0, zl.getCenterX(), zl.getCenterY(), viewL,
                discRects(scene.nodes, 1.0, zl.getCenterX(), zl.getCenterY()), hidden100,
                hullLabelRect(scene, 1.0, zl.getCenterX(), zl.getCenterY()));
            drawScene(zg, scene, at100, hidden100, "100% zoom - proposed rules",
                histogram(at100, hidden100) + "\ntext is drawn at constant screen size: font 12/z world units",
                1.0, zl.getCenterX(), zl.getCenterY(), zl, true, OK);
            List<Node> hidden50 = new ArrayList<>();
            List<Placed> at50 = proposedLabels(scene.nodes, 0.5, zr.getCenterX(), zr.getCenterY(), zoomViewR,
                discRects(scene.nodes, 0.5, zr.getCenterX(), zr.getCenterY()), hidden50,
                hullLabelRect(scene, 0.5, zr.getCenterX(), zr.getCenterY()));
            drawScene(zg, scene, at50, hidden50, "50% zoom - same world positions, proposed rules",
                histogram(at50, hidden50) + "\nplacement is recomputed in screen space, so it stays correct",
                0.5, zr.getCenterX(), zr.getCenterY(), zr, true, WARN);
            zg.dispose();
            save(zoom, dir, "04-zoom-100-vs-50.png");
            System.out.println("04 zoom: placed " + at100.size() + " at 100%, " + at50.size() + " at 50%"
                + " | hidden " + hidden100.size() + " -> " + hidden50.size());
        }
        // ---- 05: the ladder under far zoom ------------------------------------------------
        {
            Scene scene = dense();
            separate(scene.nodes, 400);
            BufferedImage image = blank(1180, 480);
            Graphics2D g = graphics(image);
            Rectangle2D left = new Rectangle2D.Double(16, 16, 566, 440);
            Rectangle2D right = new Rectangle2D.Double(598, 16, 566, 440);
            Rectangle2D viewL = new Rectangle2D.Double(left.getX() + 4, left.getY() + 40, left.getWidth() - 8, left.getHeight() - 76);
            Rectangle2D viewR = new Rectangle2D.Double(right.getX() + 120, right.getY() + 100, right.getWidth() - 240, right.getHeight() - 200);

            double zoom = 1.00;
            double ox = left.getCenterX(), oy = left.getCenterY();
            List<Placed> current = currentLabels(scene.nodes, zoom, ox, oy);
            drawScene(g, scene, current, new ArrayList<Node>(), "TODAY: cramped window at 100% zoom",
                "text is still 12 px on screen while the discs shrink to 3-6 px\nlabel/label collisions: "
                + labelCollisions(current) + "   label/disc collisions: "
                + labelDiscCollisions(current, scene.nodes, zoom, ox, oy), zoom, ox, oy, left, true, BAD);

            double ox2 = right.getCenterX(), oy2 = right.getCenterY();
            List<Node> hidden = new ArrayList<>();
            List<Placed> proposed = proposedLabels(scene.nodes, zoom, ox2, oy2, viewR,
                discRects(scene.nodes, zoom, ox2, oy2), hidden, hullLabelRect(scene, zoom, ox2, oy2));
            drawScene(g, scene, proposed, hidden, "PROPOSED: cramped window, ladder engages",
                histogram(proposed, hidden) + "\nlabel/label collisions: " + labelCollisions(proposed)
                + "   label/disc collisions: " + labelDiscCollisions(proposed, scene.nodes, zoom, ox2, oy2),
                zoom, ox2, oy2, right, true, WARN);
            g.dispose();
            save(image, dir, "05-far-zoom-ladder.png");
            System.out.println("05 far zoom: label collisions " + labelCollisions(current) + " -> " + labelCollisions(proposed)
                + " | label/disc " + labelDiscCollisions(current, scene.nodes, zoom, ox, oy)
                + " -> " + labelDiscCollisions(proposed, scene.nodes, zoom, ox2, oy2)
                + " | " + histogram(proposed, hidden));
        }
        // ---- 06: (a) truncation-first vs (b) displacement-first ---------------------------
        {
            Scene scene = dense();
            separate(scene.nodes, 400);
            BufferedImage image = blank(1180, 480);
            Graphics2D g = graphics(image);
            Rectangle2D left = new Rectangle2D.Double(16, 16, 566, 440);
            Rectangle2D right = new Rectangle2D.Double(598, 16, 566, 440);
            Rectangle2D view = new Rectangle2D.Double(left.getX() + 132, left.getY() + 96, left.getWidth() - 264, left.getHeight() - 192);
            Rectangle2D viewR = new Rectangle2D.Double(right.getX() + 132, right.getY() + 96, right.getWidth() - 264, right.getHeight() - 192);
            g.setColor(new Color(0x99, 0x9F, 0xA8));
            g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{4f, 3f}, 0f));
            g.draw(view);
            g.draw(viewR);

            double ox = left.getCenterX(), oy = left.getCenterY();
            LADDER = LADDER_TRUNCATION_FIRST;
            List<Node> hiddenA = new ArrayList<>();
            List<Placed> variantA = proposedLabels(scene.nodes, 1.0, ox, oy, view,
                discRects(scene.nodes, 1.0, ox, oy), hiddenA, hullLabelRect(scene, 1.0, ox, oy));
            drawScene(g, scene, variantA, hiddenA, "(a) shorten first: cramped window",
                histogram(variantA, hiddenA) + "\nlabel collisions " + labelCollisions(variantA)
                + "   leader crossings " + leaderCrossings(variantA, 1.0, ox, oy)
                + "   mean label offset " + String.format(Locale.ROOT, "%.0f px", meanLeaderLength(variantA, 1.0, ox, oy)),
                1.0, ox, oy, left, true, BAD);

            double ox2 = right.getCenterX(), oy2 = right.getCenterY();
            LADDER = LADDER_DISPLACEMENT_FIRST;
            List<Node> hiddenB = new ArrayList<>();
            List<Placed> variantB = proposedLabels(scene.nodes, 1.0, ox2, oy2, viewR,
                discRects(scene.nodes, 1.0, ox2, oy2), hiddenB, hullLabelRect(scene, 1.0, ox2, oy2));
            drawScene(g, scene, variantB, hiddenB, "(b) push out first: cramped window",
                histogram(variantB, hiddenB) + "\nlabel collisions " + labelCollisions(variantB)
                + "   leader crossings " + leaderCrossings(variantB, 1.0, ox2, oy2)
                + "   mean label offset " + String.format(Locale.ROOT, "%.0f px", meanLeaderLength(variantB, 1.0, ox2, oy2)),
                1.0, ox2, oy2, right, true, OK);
            g.dispose();
            save(image, dir, "06-ladder-truncate-vs-displace.png");
            System.out.println("06 ladder: (a) " + histogram(variantA, hiddenA)
                + " crossings " + leaderCrossings(variantA, 1.0, ox, oy)
                + " meanOffset " + String.format(Locale.ROOT, "%.0f", meanLeaderLength(variantA, 1.0, ox, oy))
                + " | (b) " + histogram(variantB, hiddenB)
                + " crossings " + leaderCrossings(variantB, 1.0, ox2, oy2)
                + " meanOffset " + String.format(Locale.ROOT, "%.0f", meanLeaderLength(variantB, 1.0, ox2, oy2)));
            LADDER = LADDER_DISPLACEMENT_FIRST;
        }
        // ---- truncation reachability probe -------------------------------------------------
        {
            Scene scene = dense();
            separate(scene.nodes, 400);
            for (double[] viewport : new double[][]{{1128, 364}, {700, 300}, {420, 240}, {280, 170}, {200, 130}}) {
                for (Object[][] ladder : new Object[][][]{LADDER_TRUNCATION_FIRST, LADDER_DISPLACEMENT_FIRST}) {
                    LADDER = ladder;
                    Rectangle2D view = new Rectangle2D.Double(-viewport[0] / 2, -viewport[1] / 2, viewport[0], viewport[1]);
                    List<Node> hidden = new ArrayList<>();
                    List<Placed> placed = proposedLabels(scene.nodes, 1.0, 0, 0, view,
                        discRects(scene.nodes, 1.0, 0, 0), hidden, null);
                    int salvageable = 0;
                    List<Rectangle2D> finalTaken = discRects(scene.nodes, 1.0, 0, 0);
                    for (Placed p : placed) finalTaken.add(p.rect());
                    for (Node node : hidden) {
                        boolean found = false;
                        for (Slot slot : Slot.values()) {
                            for (Font font : new Font[]{FULL, DENSE_FONT}) {
                                String text = truncateTo(node.name, font, Math.min(60.0, slotMaxWidth(slot)));
                                double w = textWidth(text, font), h = textHeight(font);
                                double[] a = slotAnchor(slot, node.x, node.y, node.r, w, h, 6.0);
                                Rectangle2D rect = new Rectangle2D.Double(a[0] - w / 2, a[1] - h / 2, w, h);
                                if (!view.contains(rect)) continue;
                                boolean clash = false;
                                for (Rectangle2D other : finalTaken) {
                                    if (other.getCenterX() == node.x && other.getCenterY() == node.y) continue;
                                    if (other.intersects(rect)) { clash = true; break; }
                                }
                                if (!clash) { found = true; break; }
                            }
                            if (found) break;
                        }
                        if (found) salvageable++;
                    }
                    System.out.println(String.format(Locale.ROOT,
                        "probe %4.0fx%4.0f %-18s -> %s | hidden %d, salvageable by a truncated label in a FREE slot: %d",
                        viewport[0], viewport[1],
                        ladder == LADDER_TRUNCATION_FIRST ? "truncation-first" : "displacement-first",
                        histogram(placed, hidden), hidden.size(), salvageable));
                }
            }
            LADDER = LADDER_DISPLACEMENT_FIRST;
        }
        System.out.println("done");
    }
}
