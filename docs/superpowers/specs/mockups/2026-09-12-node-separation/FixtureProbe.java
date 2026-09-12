import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * FixtureProbe — the self-checking reference oracle for the pinned fixture tables of
 * docs/superpowers/specs/2026-09-12-graph-node-separation-spec.md.
 *
 * This file is NOT production code and is not compiled into the plugin. It is a self-contained
 * design-time oracle that implements the pinned placement rules of the specification:
 *
 *   - the pinned total order (forced group first, then non-forced node labels, both by
 *     descending disc radius with ties in node order);
 *   - the forced full-text-only restriction (rungs 1-2) and the O4 base slot, including its
 *     contribution to the obstacle set;
 *   - the eight-rung degradation ladder with the fixed slot order;
 *   - the slot caps (200/130/150 px) and the slot anchor geometry (SLOT_GAP 6, DISPLACED 30);
 *   - the truncation rule of specification step 7;
 *   - the per-zoom stand-in rectangle hullLabelRect(scene, z, 0, 0);
 *   - the pinned enclosure rule of specification section 2.8.
 *
 * It prints the pinned sections 5.4-5.10 tables (including the section 5.8 enclosure fixture)
 * so that every value in them can be reproduced from a committed artifact.
 *
 * The probe is self-checking: every pinned expected value is compared against the computed
 * value through the check helpers below; every mismatch is printed to stderr, and the process
 * exits with a non-zero status when any check fails. The final line reports the number of
 * checks performed and the number of failures.
 *
 * The committed mockup generator NodeSeparationMockups.java is a design-time reference that
 * implements the pre-O4 rules; its stdout is not an oracle for these tables, and the pinned
 * tables supersede both the generator and design section 6's long rows.
 *
 * Run: java -Djava.awt.headless=true FixtureProbe.java
 */
public final class FixtureProbe {

    // ---------------------------------------------------------------- pinned constants (C1-C17)

    static final FontRenderContext FRC = new FontRenderContext(null, true, true);
    static final Font FULL = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    static final Font DENSE = new Font(Font.SANS_SERIF, Font.PLAIN, 9);
    static final Font EMPH = new Font(Font.SANS_SERIF, Font.BOLD, 15);
    static final Font SMALL = new Font(Font.SANS_SERIF, Font.PLAIN, 10);
    static final double SLOT_GAP = 6.0;          // C8
    static final double DISPLACED_OFFSET = 30.0; // C9
    static final double ARC_GAP = 1.0;           // C17
    static final double EXTERNAL_GAP = 4.0;      // C17
    static final int SUBTLE_EXTERNAL_CANDIDATE_BUDGET = 8; // C17
    static final double HULL_HALF = 50.0;        // section 5.8 square hull
    static final String ELL = "\u2026";
    static final String STAND_IN_TEXT = "Basic Definitions and Theorems";

    enum Slot {
        ABOVE, BELOW, RIGHT, LEFT, ABOVE_RIGHT, ABOVE_LEFT, BELOW_RIGHT, BELOW_LEFT,
        ABOVE_FAR, BELOW_FAR, RIGHT_FAR, LEFT_FAR
    }

    static final Slot[] NEAR_SLOTS = {Slot.ABOVE, Slot.BELOW, Slot.RIGHT, Slot.LEFT,
        Slot.ABOVE_RIGHT, Slot.ABOVE_LEFT, Slot.BELOW_RIGHT, Slot.BELOW_LEFT};
    static final Slot[] FAR_SLOTS = {Slot.ABOVE_FAR, Slot.BELOW_FAR, Slot.RIGHT_FAR, Slot.LEFT_FAR};

    /** Ladder rungs 1-8: {font, truncating, far?} per specification 2.7 step 5. */
    static final Object[][] RUNGS = {
        {FULL, Boolean.FALSE, Boolean.FALSE}, {FULL, Boolean.FALSE, Boolean.TRUE},
        {DENSE, Boolean.FALSE, Boolean.FALSE}, {DENSE, Boolean.FALSE, Boolean.TRUE},
        {FULL, Boolean.TRUE, Boolean.FALSE}, {FULL, Boolean.TRUE, Boolean.TRUE},
        {DENSE, Boolean.TRUE, Boolean.FALSE}, {DENSE, Boolean.TRUE, Boolean.TRUE}};

    // ---------------------------------------------------------------- measurement helpers

    static double w(String text, Font font) {
        return font.getStringBounds(text, FRC).getWidth();
    }

    static double h(Font font) {
        return font.getLineMetrics("Ag", FRC).getHeight();
    }

    static double slotMaxWidth(Slot slot) {
        switch (slot) {
            case ABOVE: case BELOW: case ABOVE_FAR: case BELOW_FAR:
                return 200.0;
            case RIGHT: case LEFT: case RIGHT_FAR: case LEFT_FAR:
                return 130.0;
            default:
                return 150.0;
        }
    }

    static double[] slotAnchor(Slot slot, double cx, double cy, double r, double w, double h) {
        switch (slot) {
            case ABOVE:       return new double[]{cx, cy - r - SLOT_GAP - h / 2};
            case BELOW:       return new double[]{cx, cy + r + SLOT_GAP + h / 2};
            case RIGHT:       return new double[]{cx + r + SLOT_GAP + w / 2, cy};
            case LEFT:        return new double[]{cx - r - SLOT_GAP - w / 2, cy};
            case ABOVE_FAR:   return new double[]{cx, cy - r - DISPLACED_OFFSET - h / 2};
            case BELOW_FAR:   return new double[]{cx, cy + r + DISPLACED_OFFSET + h / 2};
            case RIGHT_FAR:   return new double[]{cx + r + DISPLACED_OFFSET + w / 2, cy};
            case LEFT_FAR:    return new double[]{cx - r - DISPLACED_OFFSET - w / 2, cy};
            case ABOVE_RIGHT: return new double[]{cx + r + SLOT_GAP + w / 2, cy - r - SLOT_GAP - h / 2};
            case ABOVE_LEFT:  return new double[]{cx - r - SLOT_GAP - w / 2, cy - r - SLOT_GAP - h / 2};
            case BELOW_RIGHT: return new double[]{cx + r + SLOT_GAP + w / 2, cy + r + SLOT_GAP + h / 2};
            default:          return new double[]{cx - r - SLOT_GAP - w / 2, cy + r + SLOT_GAP + h / 2};
        }
    }

    /** Closed-form leader length of specification 2.7 for this slot and box. */
    static double leaderForm(Slot slot, double r, double w, double h) {
        switch (slot) {
            case ABOVE: case BELOW: return r + SLOT_GAP + h / 2;
            case ABOVE_FAR: case BELOW_FAR: return r + DISPLACED_OFFSET + h / 2;
            case RIGHT: case LEFT: return r + SLOT_GAP + w / 2;
            case RIGHT_FAR: case LEFT_FAR: return r + DISPLACED_OFFSET + w / 2;
            default: return Math.hypot(r + SLOT_GAP + w / 2, r + SLOT_GAP + h / 2);
        }
    }

    /** Closed-form farthest-corner support of specification 2.7 for this slot and box. */
    static double supportForm(Slot slot, double r, double w, double h) {
        switch (slot) {
            case ABOVE: case BELOW: return Math.hypot(w / 2, r + SLOT_GAP + h);
            case ABOVE_FAR: case BELOW_FAR: return Math.hypot(w / 2, r + DISPLACED_OFFSET + h);
            case RIGHT: case LEFT: return Math.hypot(r + SLOT_GAP + w, h / 2);
            case RIGHT_FAR: case LEFT_FAR: return Math.hypot(r + DISPLACED_OFFSET + w, h / 2);
            default: return Math.hypot(r + SLOT_GAP + w, r + SLOT_GAP + h);
        }
    }

    static String truncateTo(String text, Font font, double limit) {
        if (w(text, font) <= limit) return text;
        for (int cut = text.length() - 1; cut > 2; cut--) {
            String candidate = text.substring(0, cut).stripTrailing() + ELL;
            if (w(candidate, font) <= limit) return candidate;
        }
        return text.substring(0, Math.min(3, text.length())) + ELL;
    }

    static Rectangle2D rect(double x, double y, double w, double h) {
        return new Rectangle2D.Double(x - w / 2, y - h / 2, w, h);
    }

    static Rectangle2D area(double width, double height) {
        return new Rectangle2D.Double(-width / 2.0, -height / 2.0, width, height);
    }

    static boolean intersectsAny(List<Rectangle2D> obstacles, Rectangle2D rect) {
        for (Rectangle2D other : obstacles) if (other.intersects(rect)) return true;
        return false;
    }

    // ---------------------------------------------------------------- fixture scenes

    static final class Node {
        final String name;
        final double r;
        final boolean selected;
        final double x, y;
        Node(String name, double r, boolean selected, double x, double y) {
            this.name = name; this.r = r; this.selected = selected; this.x = x; this.y = y;
        }
    }

    static final class Scene {
        final List<Node> nodes;
        Scene(List<Node> nodes) { this.nodes = nodes; }
    }

    static Scene dense() {
        String[] names = {"Theorem", "Axiom of Choice", "Replacement Scheme", "Extensionality",
                          "Pairing", "Union", "Power Set", "Infinity", "Separation",
                          "Foundation / Regularity", "Comprehension", "Well-Ordering"};
        List<Node> nodes = new ArrayList<Node>();
        for (int i = 0; i < names.length; i++)
            nodes.add(new Node(names[i], (i % 3 == 0) ? 14.0 : 8.0, i == 1,
                (i % 4 - 1.5) * 34, (i / 4 - 1) * 34));
        return new Scene(nodes);
    }

    static Scene longScene() {
        String[] names = {
            "Well-Ordering Theorem of Choice and Regularity",
            "Axiom Schema of Replacement and Comprehension",
            "Transfinite Induction over Ordinal Numbers",
            "Cardinal Arithmetic under the Continuum Hypothesis",
            "Ultrafilter Lemma and Boolean Prime Ideal Theorem",
            "Kuratowski Zorn Lemma for Partially Ordered Sets"};
        List<Node> nodes = new ArrayList<Node>();
        for (int i = 0; i < names.length; i++)
            nodes.add(new Node(names[i], 8.0, i == 0, (i % 3 - 1) * 22, (i / 3) * 22));
        return new Scene(nodes);
    }

    static Rectangle2D discRect(Node node, double zoom) {
        double cx = node.x * zoom, cy = node.y * zoom, r = Math.max(2.0, node.r * zoom);
        return new Rectangle2D.Double(cx - r, cy - r, 2 * r, 2 * r);
    }

    /** Per-zoom stand-in: hullLabelRect(scene, z, 0, 0) in world-centred coordinates (spec 5.4). */
    static Rectangle2D standIn(Scene scene, double zoom) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        for (Node node : scene.nodes) {
            minX = Math.min(minX, node.x * zoom - node.r * zoom);
            minY = Math.min(minY, node.y * zoom - node.r * zoom);
        }
        double w = w(STAND_IN_TEXT, SMALL);
        double h = h(SMALL);
        return new Rectangle2D.Double(minX - 11.0, minY - 18.0 - h, w, h);
    }

    // ---------------------------------------------------------------- placement rule (2.7)

    static final class Prev {
        final Slot slot;
        final String text;
        final Font font;
        final double w, h;
        Prev(Slot slot, String text, Font font, double w, double h) {
            this.slot = slot; this.text = text; this.font = font; this.w = w; this.h = h;
        }
    }

    static final class P {
        final Node node;
        final String text;
        final Font font;
        final double x, y, w, h;
        final Slot slot;
        final boolean baseSlot;
        final boolean fullTextSlotWasFree;
        P(Node node, String text, Font font, double x, double y, double w, double h, Slot slot,
          boolean baseSlot, boolean fullTextSlotWasFree) {
            this.node = node; this.text = text; this.font = font;
            this.x = x; this.y = y; this.w = w; this.h = h; this.slot = slot;
            this.baseSlot = baseSlot; this.fullTextSlotWasFree = fullTextSlotWasFree;
        }
        Rectangle2D rect() { return new Rectangle2D.Double(x - w / 2, y - h / 2, w, h); }
        boolean truncated() { return !text.equals(node.name); }
    }

    static Map<String, Prev> prevOf(List<P> placed) {
        Map<String, Prev> previous = new LinkedHashMap<String, Prev>();
        for (P p : placed) previous.put(p.node.name, new Prev(p.slot, p.text, p.font, p.w, p.h));
        return previous;
    }

    /**
     * Pinned placement of section 2.7. {@code standIn} and {@code seedObstacles} are extra
     * obstacles (the generator-derived stand-in and the enclosure rectangle of the test seam).
     * {@code previous} is the retained placement; {@code hiddenOut} collects HOVER_ONLY labels.
     */
    static List<P> place(List<Node> nodes, double zoom, Rectangle2D placementArea,
            Rectangle2D standIn, List<Rectangle2D> seedObstacles, Map<String, Prev> previous,
            Set<String> forcedNames, List<String> hiddenOut) {
        List<P> out = new ArrayList<P>();
        List<Rectangle2D> taken = new ArrayList<Rectangle2D>();
        for (Node node : nodes) taken.add(discRect(node, zoom));
        if (standIn != null) taken.add(standIn);
        if (seedObstacles != null) taken.addAll(seedObstacles);

        List<Node> order = new ArrayList<Node>(nodes);
        Collections.sort(order, new Comparator<Node>() {
            public int compare(Node a, Node b) {
                int fa = (a.selected || forcedNames.contains(a.name)) ? 0 : 1;
                int fb = (b.selected || forcedNames.contains(b.name)) ? 0 : 1;
                if (fa != fb) return fa - fb;
                return Double.compare(b.r, a.r);
            }
        });

        for (Node node : order) {
            boolean forced = node.selected || forcedNames.contains(node.name);
            double cx = node.x * zoom, cy = node.y * zoom, r = Math.max(2.0, node.r * zoom);

            // I4 retention for node labels; enclosure labels are re-placed every pass.
            if (previous != null) {
                Prev p0 = previous.get(node.name);
                if (p0 != null && p0.slot != null) {
                    double[] a = slotAnchor(p0.slot, cx, cy, r, p0.w, p0.h);
                    Rectangle2D keptRect = rect(a[0], a[1], p0.w, p0.h);
                    if (placementArea.contains(keptRect) && !intersectsAny(taken, keptRect)) {
                        taken.add(keptRect);
                        out.add(new P(node, p0.text, p0.font, a[0], a[1], p0.w, p0.h, p0.slot,
                            false, false));
                        continue;
                    }
                }
            }

            boolean fullFree = false;
            P created = null;
            if (forced) {
                // Forced labels use only rungs 1-2 (full text, full font, near then far).
                for (Slot[] rung : new Slot[][]{NEAR_SLOTS, FAR_SLOTS}) {
                    for (Slot slot : rung) {
                        double fw = w(node.name, FULL);
                        if (fw > slotMaxWidth(slot)) continue;   // fit condition
                        double hh = h(FULL);
                        double[] a = slotAnchor(slot, cx, cy, r, fw, hh);
                        Rectangle2D candidate = rect(a[0], a[1], fw, hh);
                        if (!placementArea.contains(candidate)) continue;
                        if (intersectsAny(taken, candidate)) continue;
                        fullFree = true;
                        taken.add(candidate);
                        created = new P(node, node.name, FULL, a[0], a[1], fw, hh, slot,
                            false, true);
                        break;
                    }
                    if (created != null) break;
                }
                if (created == null) {
                    // O4 base slot: ABOVE at SLOT_GAP, unchecked, contributes to the obstacle set.
                    double fw = w(node.name, FULL), hh = h(FULL);
                    double[] a = slotAnchor(Slot.ABOVE, cx, cy, r, fw, hh);
                    Rectangle2D candidate = rect(a[0], a[1], fw, hh);
                    taken.add(candidate);
                    created = new P(node, node.name, FULL, a[0], a[1], fw, hh, Slot.ABOVE,
                        true, false);
                }
            } else {
                for (Object[] rung : RUNGS) {
                    Font font = (Font) rung[0];
                    boolean truncating = ((Boolean) rung[1]).booleanValue();
                    Slot[] slots = ((Boolean) rung[2]).booleanValue() ? FAR_SLOTS : NEAR_SLOTS;
                    for (Slot slot : slots) {
                        double limit = slotMaxWidth(slot);
                        if (!truncating && w(node.name, font) > limit) continue; // fit condition
                        String text = truncating ? truncateTo(node.name, font, limit) : node.name;
                        double ww = w(text, font), hh = h(font);
                        double[] a = slotAnchor(slot, cx, cy, r, ww, hh);
                        Rectangle2D candidate = rect(a[0], a[1], ww, hh);
                        if (!placementArea.contains(candidate)) continue;
                        boolean clash = intersectsAny(taken, candidate);
                        if (!truncating && !clash) fullFree = true;
                        if (clash) continue;
                        taken.add(candidate);
                        created = new P(node, text, font, a[0], a[1], ww, hh, slot, false, fullFree);
                        break;
                    }
                    if (created != null) break;
                }
            }
            if (created == null) {
                hiddenOut.add(node.name);
                continue;
            }
            out.add(created);
        }
        return out;
    }

    // ---------------------------------------------------------------- self-check harness

    static int checks = 0;
    static int failures = 0;

    static void fail(String what, Object expected, Object actual) {
        failures++;
        System.err.println("CHECK FAILED: " + what + ": expected <" + expected + "> but was <" + actual + ">");
    }

    static void check(String what, int expected, int actual) {
        checks++;
        if (expected != actual) fail(what, expected, actual);
    }

    static void check(String what, boolean expected, boolean actual) {
        checks++;
        if (expected != actual) fail(what, expected, actual);
    }

    static void check(String what, String expected, String actual) {
        checks++;
        if (!expected.equals(actual)) fail(what, expected, actual);
    }

    static void checkClose(String what, double expected, double actual, double tolerance) {
        checks++;
        if (!(Math.abs(expected - actual) <= tolerance)) fail(what, f6(expected), f6(actual));
    }

    static void check6(String what, double expected, double actual) {
        checkClose(what, expected, actual, 1e-6);
    }

    static void check4(String what, double expected, double actual) {
        checkClose(what, expected, actual, 1e-4);
    }

    static void check3(String what, double expected, double actual) {
        checkClose(what, expected, actual, 1e-3);
    }

    static void checkList(String what, List<String> expected, List<String> actual) {
        checks++;
        if (!expected.equals(actual)) fail(what, expected, actual);
    }

    static void checkLabel(String what, List<P> placed, String name, Slot slot, int font,
            String text, double x, double y, double w, double h) {
        P p = find(placed, name);
        check(what + " " + name + " present", true, p != null);
        if (p == null) return;
        check(what + " " + name + " slot", slot.name(), p.slot.name());
        check(what + " " + name + " font", font, p.font.getSize());
        check(what + " " + name + " text", text, p.text);
        check6(what + " " + name + " x", x, p.x);
        check6(what + " " + name + " y", y, p.y);
        check6(what + " " + name + " w", w, p.w);
        check6(what + " " + name + " h", h, p.h);
    }

    static void checkHistogram(String what, List<P> placed, List<String> hidden,
            int full, int dense, int truncated, int hoverOnly) {
        int f = 0, d = 0, t = 0;
        for (P p : placed) {
            if (p.truncated()) t++;
            else if (p.font == DENSE) d++;
            else f++;
        }
        check(what + " full", full, f);
        check(what + " dense", dense, d);
        check(what + " truncated", truncated, t);
        check(what + " hover-only", hoverOnly, hidden.size());
    }

    static void checkKept(String what, List<P> base, List<P> after, String name) {
        P p = find(base, name), q = find(after, name);
        check(what + " " + name + " present", true, q != null);
        if (p == null || q == null) return;
        check(what + " " + name + " slot", p.slot.name(), q.slot.name());
        check(what + " " + name + " text", p.text, q.text);
        check(what + " " + name + " font", p.font.getSize(), q.font.getSize());
        check(what + " " + name + " anchor bit-identical", true, p.x == q.x && p.y == q.y);
    }

    // ---------------------------------------------------------------- print helpers

    static String f6(double v) { return String.format(Locale.ROOT, "%.6f", v); }
    static String f4(double v) { return String.format(Locale.ROOT, "%.4f", v); }
    static String pt(double x, double y) { return "(" + f6(x) + "," + f6(y) + ")"; }
    static String rectString(Rectangle2D r) {
        if (r == null) return "none";
        return "(" + f6(r.getX()) + "," + f6(r.getY()) + "," + f6(r.getWidth()) + "," + f6(r.getHeight()) + ")";
    }
    static Set<String> noneForced() { return Collections.<String>emptySet(); }
    static P find(List<P> placed, String name) {
        for (P p : placed) if (p.node.name.equals(name)) return p;
        return null;
    }
    static List<String> names(List<P> placed) {
        List<String> out = new ArrayList<String>();
        for (P p : placed) out.add(p.node.name);
        return out;
    }

    static void dumpPlacement(String tag, List<P> placed, double zoom) {
        System.out.println(tag + " (" + placed.size() + " labels):");
        int index = 1;
        for (P p : placed) {
            System.out.printf(Locale.ROOT,
                "  %2d %-52s %-18s %2d %-42s a=(%.6f,%.6f) w=%.6f h=%.6f%s%n",
                index++, p.node.name, p.slot, p.font.getSize(), p.text,
                p.x, p.y, p.w, p.h, p.baseSlot ? " [O4-BASE]" : "");
        }
    }

    static void printHistogram(String tag, List<P> placed, List<String> hidden) {
        int full = 0, dense = 0, truncated = 0, base = 0;
        for (P p : placed) {
            if (p.truncated()) truncated++;
            else if (p.font == DENSE) dense++;
            else full++;
            if (p.baseSlot) base++;
        }
        System.out.println(tag + ": full " + full + " (base-slot " + base + ") | dense " + dense
            + " | truncated " + truncated + " | hover-only " + hidden.size() + " " + hidden);
    }

    static int labelLabelCollisions(List<P> placed) {
        int n = 0;
        for (int i = 0; i < placed.size(); i++)
            for (int j = i + 1; j < placed.size(); j++)
                if (placed.get(i).rect().intersects(placed.get(j).rect())) n++;
        return n;
    }

    static int labelDiscCollisions(List<P> placed, Scene scene, double zoom) {
        return labelDiscCollisions(placed, scene.nodes, zoom);
    }

    static int labelDiscCollisions(List<P> placed, List<Node> nodes, double zoom) {
        int n = 0;
        for (P p : placed)
            for (Node node : nodes)
                if (p.rect().intersects(discRect(node, zoom))) { n++; break; }
        return n;
    }

    static double leader(P p, double zoom) {
        return Math.hypot(p.x - p.node.x * zoom, p.y - p.node.y * zoom);
    }

    static double maxLeader(List<P> placed, double zoom) {
        double max = 0;
        for (P p : placed) max = Math.max(max, leader(p, zoom));
        return max;
    }

    static double meanLeader(List<P> placed, double zoom) {
        double total = 0;
        for (P p : placed) total += leader(p, zoom);
        return placed.isEmpty() ? 0 : total / placed.size();
    }

    /** Distance from the disc centre to the placed rectangle's farthest corner. */
    static double support(P p, double zoom) {
        Rectangle2D r = p.rect();
        double cx = p.node.x * zoom, cy = p.node.y * zoom;
        double max = 0;
        max = Math.max(max, Math.hypot(r.getMinX() - cx, r.getMinY() - cy));
        max = Math.max(max, Math.hypot(r.getMinX() - cx, r.getMaxY() - cy));
        max = Math.max(max, Math.hypot(r.getMaxX() - cx, r.getMinY() - cy));
        max = Math.max(max, Math.hypot(r.getMaxX() - cx, r.getMaxY() - cy));
        return max;
    }

    static double maxSupport(List<P> placed, double zoom) {
        double max = 0;
        for (P p : placed) max = Math.max(max, support(p, zoom));
        return max;
    }

    static double orient(double ax, double ay, double bx, double by, double cx, double cy) {
        return (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
    }

    static boolean crosses(double[] a, double[] b) {
        return orient(a[0], a[1], a[2], a[3], b[0], b[1]) * orient(a[0], a[1], a[2], a[3], b[2], b[3]) < 0
            && orient(b[0], b[1], b[2], b[3], a[0], a[1]) * orient(b[0], b[1], b[2], b[3], a[2], a[3]) < 0;
    }

    /**
     * The generator's leader-crossings metric (NodeSeparationMockups.leaderCrossings): the
     * number of proper crossings between disc-trimmed leader segments, taking only labels whose
     * slot is not ABOVE or BELOW (the directly above/below slots).
     */
    static int leaderCrossings(List<P> placed, double zoom) {
        List<double[]> segments = new ArrayList<double[]>();
        for (P p : placed) {
            if (p.slot == Slot.ABOVE || p.slot == Slot.BELOW) continue;
            double cx = p.node.x * zoom, cy = p.node.y * zoom, r = p.node.r * zoom;
            double dx = p.x - cx, dy = p.y - cy, d = Math.max(1e-6, Math.hypot(dx, dy));
            segments.add(new double[]{cx + dx / d * r, cy + dy / d * r, p.x, p.y});
        }
        int n = 0;
        for (int i = 0; i < segments.size(); i++)
            for (int j = i + 1; j < segments.size(); j++)
                if (crosses(segments.get(i), segments.get(j))) n++;
        return n;
    }

    /** Euclidean minimum separation between two axis-aligned rectangles (0 when they overlap). */
    static double minRectGap(List<P> placed, boolean includeBaseSlot) {
        double best = Double.MAX_VALUE;
        for (int i = 0; i < placed.size(); i++) {
            if (!includeBaseSlot && placed.get(i).baseSlot) continue;
            for (int j = i + 1; j < placed.size(); j++) {
                if (!includeBaseSlot && placed.get(j).baseSlot) continue;
                Rectangle2D a = placed.get(i).rect(), b = placed.get(j).rect();
                double dx = Math.max(0, Math.max(a.getMinX() - b.getMaxX(), b.getMinX() - a.getMaxX()));
                double dy = Math.max(0, Math.max(a.getMinY() - b.getMaxY(), b.getMinY() - a.getMaxY()));
                best = Math.min(best, Math.hypot(dx, dy));
            }
        }
        return best;
    }

    static boolean samePlacement(List<P> a, List<P> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            P x = a.get(i), y = b.get(i);
            if (!x.node.name.equals(y.node.name) || x.slot != y.slot || !x.text.equals(y.text)
                || x.font.getSize() != y.font.getSize() || x.x != y.x || x.y != y.y
                || x.w != y.w || x.h != y.h) return false;
        }
        return true;
    }

    static boolean preserved(List<P> base, List<P> after) {
        if (base.size() != after.size()) return false;
        for (P p : base) {
            P q = find(after, p.node.name);
            if (q == null || q.slot != p.slot || !q.text.equals(p.text)
                || q.font.getSize() != p.font.getSize()) return false;
        }
        return true;
    }

    static boolean othersIdentical(List<P> base, List<P> after, String skip) {
        for (P p : base) {
            if (p.node.name.equals(skip)) continue;
            P q = find(after, p.node.name);
            if (q == null || q.slot != p.slot || !q.text.equals(p.text)
                || q.font.getSize() != p.font.getSize() || q.x != p.x || q.y != p.y
                || q.w != p.w || q.h != p.h) return false;
        }
        return true;
    }

    /**
     * The RenderingLevel.OVER_TARGET filter of specification 2.7 step 11: the required emphatic
     * enclosure label (when present and emphatic) plus the forced node labels.
     */
    static List<String> overTargetLabels(String enclosureText, boolean enclosureRequired,
            List<P> placed, Set<String> forced) {
        List<String> out = new ArrayList<String>();
        if (enclosureRequired && enclosureText != null) out.add(enclosureText);
        for (P p : placed) if (p.node.selected || forced.contains(p.node.name)) out.add(p.node.name);
        return out;
    }

    // ---------------------------------------------------------------- painted-ink measurement (5.7)

    static boolean[] labelMask(P p, int width, int height) {
        double ax = p.x + width / 2.0, ay = p.y + height / 2.0;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(p.font);
        g.setColor(Color.BLACK);
        double logicalWidth = p.font.getStringBounds(p.text, FRC).getWidth();
        FontMetrics fm = g.getFontMetrics(p.font);
        double baseline = ay + (fm.getAscent() - fm.getDescent()) / 2.0;
        g.drawString(p.text, (float) (ax - logicalWidth / 2), (float) baseline);
        g.dispose();
        boolean[] mask = new boolean[width * height];
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                if ((image.getRGB(x, y) >>> 24) != 0) mask[y * width + x] = true;
        return mask;
    }

    static boolean[] discMask(double cx, double cy, double r, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
        g.dispose();
        boolean[] mask = new boolean[width * height];
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                if ((image.getRGB(x, y) >>> 24) != 0) mask[y * width + x] = true;
        return mask;
    }

    /** Per-side overhang {left, right, top, bottom} of the ink past the logical rectangle. */
    static double[] overhang(P p, boolean[] mask, int width, int height) {
        int minX = Integer.MAX_VALUE, maxX = -1, minY = Integer.MAX_VALUE, maxY = -1;
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                if (mask[y * width + x]) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
        double rectMinX = p.x - p.w / 2 + width / 2.0, rectMaxX = p.x + p.w / 2 + width / 2.0;
        double rectMinY = p.y - p.h / 2 + height / 2.0, rectMaxY = p.y + p.h / 2 + height / 2.0;
        return new double[]{rectMinX - minX, maxX + 1 - rectMaxX, rectMinY - minY, maxY + 1 - rectMaxY};
    }

    static void printInk(String tag, Scene scene, double zoom, Rectangle2D standIn,
            int expPlaced, String expForcedSlot, double expForcedX, double expForcedY,
            boolean expForcedBase, double expOverhang, double expMinGap, double expMinGapNoBase,
            int expInkPairs, int expInkDisc, int expRectDisc, List<String> expHidden) {
        int width = 1128, height = 364;
        List<String> hidden = new ArrayList<String>();
        List<P> placed = place(scene.nodes, zoom, area(width, height), standIn, null, null,
            noneForced(), hidden);

        List<boolean[]> masks = new ArrayList<boolean[]>();
        double maxOverhang = 0;
        String maxWho = "";
        P forced = null;
        for (P p : placed) {
            boolean[] mask = labelMask(p, width, height);
            masks.add(mask);
            double[] o = overhang(p, mask, width, height);
            double m = Math.max(Math.max(o[0], o[1]), Math.max(o[2], o[3]));
            if (m > maxOverhang) {
                maxOverhang = m;
                maxWho = p.node.name + " L=" + f6(o[0]) + " R=" + f6(o[1])
                    + " T=" + f6(o[2]) + " B=" + f6(o[3]) + (p.baseSlot ? " [O4 base]" : "");
            }
            if (p.node.selected) forced = p;
        }

        int inkPairs = 0;
        for (int i = 0; i < masks.size(); i++)
            for (int j = i + 1; j < masks.size(); j++) {
                boolean hit = false;
                boolean[] a = masks.get(i), b = masks.get(j);
                for (int k = 0; k < width * height; k++)
                    if (a[k] && b[k]) { hit = true; break; }
                if (hit) inkPairs++;
            }

        List<boolean[]> discs = new ArrayList<boolean[]>();
        for (Node node : scene.nodes)
            discs.add(discMask(node.x * zoom + width / 2.0, node.y * zoom + height / 2.0,
                Math.max(2.0, node.r * zoom), width, height));
        int inkDisc = 0;
        List<String> inkDiscWho = new ArrayList<String>();
        for (int i = 0; i < masks.size(); i++) {
            boolean hit = false;
            for (int j = 0; j < discs.size(); j++) {
                boolean[] a = masks.get(i), b = discs.get(j);
                for (int k = 0; k < width * height; k++)
                    if (a[k] && b[k]) { hit = true; break; }
                if (hit) { inkDiscWho.add(placed.get(i).node.name + "/" + scene.nodes.get(j).name); break; }
            }
            if (hit) inkDisc++;
        }

        int rectDisc = 0;
        List<String> rectDiscWho = new ArrayList<String>();
        for (P p : placed)
            for (Node node : scene.nodes)
                if (p.rect().intersects(discRect(node, zoom))) {
                    rectDisc++;
                    rectDiscWho.add(p.node.name);
                    break;
                }

        System.out.println("5.7 " + tag + " stand-in=" + rectString(standIn) + " placed=" + placed.size()
            + " forced=" + (forced == null ? "none"
                : forced.slot + " a=" + pt(forced.x, forced.y) + (forced.baseSlot ? " [O4]" : ""))
            + " maxOverhang=" + f4(maxOverhang) + " (" + maxWho + ")"
            + " minGap=" + f6(minRectGap(placed, true)) + " minGapNoBase=" + f6(minRectGap(placed, false))
            + " inkPairs=" + inkPairs + " inkDisc=" + inkDisc
            + (inkDiscWho.isEmpty() ? "" : " " + inkDiscWho)
            + " rectDisc=" + rectDisc + (rectDiscWho.isEmpty() ? "" : " " + rectDiscWho)
            + " hidden=" + hidden);

        check("5.7 " + tag + " placed", expPlaced, placed.size());
        check("5.7 " + tag + " forced present", true, forced != null);
        if (forced != null) {
            check("5.7 " + tag + " forced slot", expForcedSlot, forced.slot.name());
            check6("5.7 " + tag + " forced x", expForcedX, forced.x);
            check6("5.7 " + tag + " forced y", expForcedY, forced.y);
            check("5.7 " + tag + " forced base", expForcedBase, forced.baseSlot);
        }
        check4("5.7 " + tag + " max overhang", expOverhang, maxOverhang);
        check6("5.7 " + tag + " min gap", expMinGap, minRectGap(placed, true));
        check6("5.7 " + tag + " min gap no base", expMinGapNoBase, minRectGap(placed, false));
        check("5.7 " + tag + " ink pairs", expInkPairs, inkPairs);
        check("5.7 " + tag + " ink/disc", expInkDisc, inkDisc);
        check("5.7 " + tag + " rect/disc", expRectDisc, rectDisc);
        checkList("5.7 " + tag + " hidden", expHidden, hidden);
    }

    // ---------------------------------------------------------------- enclosure rule (5.8, 5.9 cell)

    static final double[][] HULL_POLYGON = {{-50, -50}, {50, -50}, {50, 50}, {-50, 50}};
    static final String[] EDGE_NAMES = {"bottom", "right", "top", "left"};

    static final class Encl {
        final String mode;
        final double x, y, w, h;
        final String edge;
        final int lane;
        final Double leaderX, leaderY;
        final boolean emphaticAtAnchor;
        final int totalCandidates;
        final int[] perEdge;
        Encl(String mode, double x, double y, double w, double h, String edge, int lane,
             Double leaderX, Double leaderY, boolean emphaticAtAnchor, int totalCandidates,
             int[] perEdge) {
            this.mode = mode; this.x = x; this.y = y; this.w = w; this.h = h;
            this.edge = edge; this.lane = lane; this.leaderX = leaderX; this.leaderY = leaderY;
            this.emphaticAtAnchor = emphaticAtAnchor; this.totalCandidates = totalCandidates;
            this.perEdge = perEdge;
        }
        Rectangle2D rect() { return FixtureProbe.rect(x, y, w, h); }
        boolean hasLeader() { return leaderX != null; }
        boolean placedLabel() {
            return mode.equals("INTERIOR") || mode.equals("ARC") || mode.equals("EXTERNAL");
        }
        String leaderString() {
            return hasLeader() ? pt(leaderX.doubleValue(), leaderY.doubleValue()) : "none";
        }
    }

    static boolean hullContains(double x, double y) {
        return x >= -HULL_HALF && x <= HULL_HALF && y >= -HULL_HALF && y <= HULL_HALF;
    }

    static boolean rectInHull(double x, double y, double w, double h) {
        return hullContains(x - w / 2, y - h / 2) && hullContains(x - w / 2, y + h / 2)
            && hullContains(x + w / 2, y - h / 2) && hullContains(x + w / 2, y + h / 2);
    }

    static double[] nearestBoundary(double x, double y) {
        if (x >= -HULL_HALF && x <= HULL_HALF && y >= -HULL_HALF && y <= HULL_HALF) {
            double left = x + HULL_HALF, right = HULL_HALF - x;
            double bottom = y + HULL_HALF, top = HULL_HALF - y;
            double min = Math.min(Math.min(left, right), Math.min(bottom, top));
            if (min == left) return new double[]{-HULL_HALF, y};
            if (min == right) return new double[]{HULL_HALF, y};
            if (min == bottom) return new double[]{x, -HULL_HALF};
            return new double[]{x, HULL_HALF};
        }
        return new double[]{Math.max(-HULL_HALF, Math.min(HULL_HALF, x)),
                            Math.max(-HULL_HALF, Math.min(HULL_HALF, y))};
    }

    static double maxHullSupport(double ux, double uy) {
        double max = -Double.MAX_VALUE;
        for (double[] v : HULL_POLYGON) max = Math.max(max, ux * v[0] + uy * v[1]);
        return max;
    }

    static double[] projection(Rectangle2D r, double ux, double uy) {
        double[][] corners = {{r.getMinX(), r.getMinY()}, {r.getMinX(), r.getMaxY()},
            {r.getMaxX(), r.getMinY()}, {r.getMaxX(), r.getMaxY()}};
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (double[] c : corners) {
            double v = ux * c[0] + uy * c[1];
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        return new double[]{min, max};
    }

    /** Population of an edge per specification 2.8 Tier B (occupied-rectangle overlap count). */
    static int edgePopulation(double[] start, double[] end, double[] outward,
            List<Rectangle2D> obstacles, double depth) {
        double tx = end[0] - start[0], ty = end[1] - start[1];
        double length = Math.hypot(tx, ty);
        tx /= length; ty /= length;
        double minT = Math.min(tx * start[0] + ty * start[1], tx * end[0] + ty * end[1]);
        double maxT = Math.max(tx * start[0] + ty * start[1], tx * end[0] + ty * end[1]);
        double support = maxHullSupport(outward[0], outward[1]);
        int n = 0;
        for (Rectangle2D obstacle : obstacles) {
            double[] onTangent = projection(obstacle, tx, ty);
            double[] onNormal = projection(obstacle, outward[0], outward[1]);
            if (onTangent[0] <= maxT && onTangent[1] >= minT
                && onNormal[0] <= support + depth && onNormal[1] >= support - depth) n++;
        }
        return n;
    }

    static double areaSupport(Rectangle2D area, double nx, double ny) {
        return Math.max(
            Math.max(nx * area.getMinX() + ny * area.getMinY(), nx * area.getMinX() + ny * area.getMaxY()),
            Math.max(nx * area.getMaxX() + ny * area.getMinY(), nx * area.getMaxX() + ny * area.getMaxY()));
    }

    /**
     * Pinned enclosure rule of specification 2.8: interior, arc, external (lane search with the
     * outward-side abandonment rule and the subtle candidate budget), terminal rules.
     */
    static Encl enclosureRule(String text, Font font, Rectangle2D placementArea,
            List<Rectangle2D> obstacles, boolean emphatic) {
        double ww = w(text, font), hh = h(font);

        // Tier A - INTERIOR at hull.labelAnchor() = (0,0).
        Rectangle2D interior = rect(0, 0, ww, hh);
        if (placementArea.contains(interior) && rectInHull(0, 0, ww, hh)
            && !intersectsAny(obstacles, interior))
            return new Encl("INTERIOR", 0, 0, ww, hh, null, -1, null, null, false, 0, new int[4]);

        // Tier B - ARC: edges in canonical order, sorted by (population asc, length desc, index asc).
        double[][] starts = {{-HULL_HALF, -HULL_HALF}, {HULL_HALF, -HULL_HALF},
            {HULL_HALF, HULL_HALF}, {-HULL_HALF, HULL_HALF}};
        double[][] ends = {{HULL_HALF, -HULL_HALF}, {HULL_HALF, HULL_HALF},
            {-HULL_HALF, HULL_HALF}, {-HULL_HALF, -HULL_HALF}};
        double[][] outward = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        final int[] population = new int[4];
        final double[] length = new double[4];
        for (int i = 0; i < 4; i++) {
            population[i] = edgePopulation(starts[i], ends[i], outward[i], obstacles, hh + ARC_GAP);
            length[i] = Math.hypot(ends[i][0] - starts[i][0], ends[i][1] - starts[i][1]);
        }
        Integer[] edgeOrder = {0, 1, 2, 3};
        Arrays.sort(edgeOrder, new Comparator<Integer>() {
            public int compare(Integer a, Integer b) {
                if (population[a] != population[b]) return population[a] - population[b];
                if (length[a] != length[b]) return Double.compare(length[b], length[a]);
                return a - b;
            }
        });
        for (int k = 0; k < 4; k++) {
            int i = edgeOrder[k];
            double mx = (starts[i][0] + ends[i][0]) / 2.0, my = (starts[i][1] + ends[i][1]) / 2.0;
            double ax = mx - outward[i][0] * (hh / 2 + ARC_GAP);
            double ay = my - outward[i][1] * (hh / 2 + ARC_GAP);
            Rectangle2D candidate = rect(ax, ay, ww, hh);
            if (placementArea.contains(candidate) && rectInHull(ax, ay, ww, hh)
                && !intersectsAny(obstacles, candidate))
                return new Encl("ARC", ax, ay, ww, hh, EDGE_NAMES[i], -1, null, null, false, 0, new int[4]);
        }

        // Tier C - EXTERNAL: canonical edge order, lanes outward, abandonment + subtle budget.
        int total = 0;
        int[] perEdge = new int[4];
        candidates:
        for (int i = 0; i < 4; i++) {
            double onx = outward[i][0], ony = outward[i][1];
            double areaSupport = areaSupport(placementArea, onx, ony);
            double halfNormal = (onx != 0) ? ww / 2 : hh / 2;
            double mx = (starts[i][0] + ends[i][0]) / 2.0, my = (starts[i][1] + ends[i][1]) / 2.0;
            for (int lane = 0; ; lane++) {
                total++;
                perEdge[i]++;
                double distance = hh / 2 + EXTERNAL_GAP + lane * (hh + EXTERNAL_GAP);
                double ax = mx + onx * distance, ay = my + ony * distance;
                Rectangle2D candidate = rect(ax, ay, ww, hh);
                if (!hullContains(ax, ay) && placementArea.contains(candidate)
                    && !intersectsAny(obstacles, candidate)) {
                    double[] leader = nearestBoundary(ax, ay);
                    return new Encl("EXTERNAL", ax, ay, ww, hh, EDGE_NAMES[i], lane,
                        Double.valueOf(leader[0]), Double.valueOf(leader[1]), false, total, perEdge);
                }
                if (!emphatic && total >= SUBTLE_EXTERNAL_CANDIDATE_BUDGET) break candidates;
                double innerEdge = onx * ax + ony * ay - halfNormal;
                if (innerEdge > areaSupport) break;
            }
        }

        // Terminal rules.
        if (emphatic)
            return new Encl("EMPHATIC_AT_ANCHOR", 0, 0, ww, hh, null, -1, null, null, true,
                total, perEdge);
        return new Encl("HOVER_ONLY", 0, 0, ww, hh, null, -1, null, null, false, total, perEdge);
    }

    // ---------------------------------------------------------------- fixture sections

    static void section54(Scene dense) {
        System.out.println();
        System.out.println("== 5.4 Dense 12-node fixture (1128x364, zoom 1, per-zoom stand-in) ==");
        Rectangle2D standIn = standIn(dense, 1.0);
        System.out.println("stand-in hullLabelRect(dense,1,0,0)=" + rectString(standIn));
        check6("5.4 stand-in x", -76.0, standIn.getX());
        check6("5.4 stand-in y", -79.619987, standIn.getY());
        check6("5.4 stand-in w", 148.289871, standIn.getWidth());
        check6("5.4 stand-in h", 13.619987, standIn.getHeight());
        List<String> hidden = new ArrayList<String>();
        List<P> placed = place(dense.nodes, 1.0, area(1128, 364), standIn, null, null,
            noneForced(), hidden);
        dumpPlacement("placement order", placed, 1.0);
        printHistogram("histogram", placed, hidden);
        System.out.println("label/label collisions=" + labelLabelCollisions(placed)
            + " label/disc collisions=" + labelDiscCollisions(placed, dense, 1.0));
        System.out.println("mean leader=" + f6(meanLeader(placed, 1.0))
            + " max leader=" + f6(maxLeader(placed, 1.0))
            + " leader crossings=" + leaderCrossings(placed, 1.0));

        checkList("5.4 placement order", Arrays.asList("Axiom of Choice", "Theorem",
            "Extensionality", "Power Set", "Foundation / Regularity", "Replacement Scheme",
            "Pairing", "Infinity", "Separation", "Comprehension", "Well-Ordering"), names(placed));
        checkLabel("5.4", placed, "Axiom of Choice", Slot.ABOVE, 12, "Axiom of Choice",
            -17.0, -56.172057, 91.104675, 16.344114);
        checkLabel("5.4", placed, "Theorem", Slot.LEFT, 12, "Theorem",
            -96.530182, -34.0, 51.060364, 16.344114);
        checkLabel("5.4", placed, "Extensionality", Slot.RIGHT, 12, "Extensionality",
            110.216270, -34.0, 78.432541, 16.344114);
        checkLabel("5.4", placed, "Power Set", Slot.RIGHT_FAR, 12, "Power Set",
            89.242210, 0.0, 56.484421, 16.344114);
        checkLabel("5.4", placed, "Foundation / Regularity", Slot.BELOW, 12, "Foundation / Regularity",
            -17.0, 62.172057, 132.600922, 16.344114);
        checkLabel("5.4", placed, "Replacement Scheme", Slot.ABOVE_RIGHT, 12, "Replacement Scheme",
            91.672424, -56.172057, 121.344849, 16.344114);
        checkLabel("5.4", placed, "Pairing", Slot.LEFT, 12, "Pairing",
            -84.968140, 0.0, 39.936279, 16.344114);
        checkLabel("5.4", placed, "Infinity", Slot.BELOW_RIGHT, 12, "Infinity",
            84.836136, 22.172057, 39.672272, 16.344114);
        checkLabel("5.4", placed, "Separation", Slot.LEFT, 12, "Separation",
            -95.630211, 34.0, 61.260422, 16.344114);
        checkLabel("5.4", placed, "Comprehension", Slot.BELOW_FAR, 12, "Comprehension",
            17.0, 80.172057, 90.288651, 16.344114);
        checkLabel("5.4", placed, "Well-Ordering", Slot.BELOW_RIGHT, 12, "Well-Ordering",
            104.654289, 56.172057, 79.308578, 16.344114);
        checkHistogram("5.4 1128x364", placed, hidden, 11, 0, 0, 1);
        checkList("5.4 1128x364 hidden", Arrays.asList("Union"), hidden);
        check("5.4 label/label collisions", 0, labelLabelCollisions(placed));
        check("5.4 label/disc collisions", 0, labelDiscCollisions(placed, dense, 1.0));
        check6("5.4 mean leader", 48.046026, meanLeader(placed, 1.0));
        check6("5.4 max leader", 77.894615, maxLeader(placed, 1.0));
        check("5.4 leader crossings", 0, leaderCrossings(placed, 1.0));

        for (int[] viewport : new int[][]{{420, 240}, {280, 170}, {200, 130}}) {
            List<String> h = new ArrayList<String>();
            List<P> p = place(dense.nodes, 1.0, area(viewport[0], viewport[1]), standIn,
                null, null, noneForced(), h);
            printHistogram("histogram " + viewport[0] + "x" + viewport[1] + " stand-in", p, h);
            String key = "5.4 " + viewport[0] + "x" + viewport[1];
            if (viewport[0] == 420) {
                checkHistogram(key, p, h, 11, 0, 0, 1);
                checkList(key + " hidden", Arrays.asList("Union"), h);
            } else if (viewport[0] == 280) {
                checkHistogram(key, p, h, 7, 4, 0, 1);
                checkList(key + " hidden", Arrays.asList("Union"), h);
            } else {
                checkHistogram(key, p, h, 2, 4, 0, 6);
                checkList(key + " hidden", Arrays.asList("Extensionality", "Power Set",
                    "Foundation / Regularity", "Replacement Scheme", "Union", "Well-Ordering"), h);
            }
        }
    }

    static void section55(Scene longScene) {
        System.out.println();
        System.out.println("== 5.5 Long-label truncation fixture (zoom 1) ==");
        System.out.println("12 pt widths:");
        double[] expectedWidths = {274.130005, 292.586090, 246.722, 299.606, 295.478, 283.034};
        for (int i = 0; i < longScene.nodes.size(); i++) {
            Node node = longScene.nodes.get(i);
            System.out.println("  " + node.name + " -> " + f6(w(node.name, FULL)));
            check3("5.5 width " + node.name, expectedWidths[i], w(node.name, FULL));
        }
        String axiomText = "Axiom Schema of Replacement a" + ELL;
        String transfiniteDense = "Transfinite Induction over Ordinal Numbers";
        String transfiniteText = "Transfinite Induction" + ELL;
        String cardinalText = "Cardinal Arithmetic u" + ELL;
        String ultrafilterText = "Ultrafilter Lemma and Boolean Pr" + ELL;
        String kuratowskiText = "Kuratowski Zorn Lem" + ELL;
        for (String mode : new String[]{"none", "stand-in"}) {
            Rectangle2D standIn = mode.equals("stand-in") ? standIn(longScene, 1.0) : null;
            if (standIn != null) {
                check6("5.5 stand-in x", -41.0, standIn.getX());
                check6("5.5 stand-in y", -39.619987, standIn.getY());
                check6("5.5 stand-in w", 148.289871, standIn.getWidth());
                check6("5.5 stand-in h", 13.619987, standIn.getHeight());
            }
            List<String> hidden = new ArrayList<String>();
            List<P> placed = place(longScene.nodes, 1.0, area(1128, 364), standIn, null, null,
                noneForced(), hidden);
            dumpPlacement("placement 1128x364 " + mode, placed, 1.0);
            printHistogram("histogram 1128x364 " + mode, placed, hidden);
            System.out.println("max leader 1128x364 " + mode + "=" + f6(maxLeader(placed, 1.0)));
            String key = "5.5 1128x364 " + mode;
            checkList(key + " order", Arrays.asList(
                "Well-Ordering Theorem of Choice and Regularity",
                "Axiom Schema of Replacement and Comprehension",
                "Transfinite Induction over Ordinal Numbers",
                "Cardinal Arithmetic under the Continuum Hypothesis",
                "Ultrafilter Lemma and Boolean Prime Ideal Theorem",
                "Kuratowski Zorn Lemma for Partially Ordered Sets"), names(placed));
            checkLabel(key, placed, "Well-Ordering Theorem of Choice and Regularity", Slot.ABOVE, 12,
                "Well-Ordering Theorem of Choice and Regularity", -22.0, -22.172057, 274.130005, 16.344114);
            boolean withStandIn = mode.equals("stand-in");
            checkLabel(key, placed, "Axiom Schema of Replacement and Comprehension",
                withStandIn ? Slot.BELOW_FAR : Slot.ABOVE_FAR, 12, axiomText,
                0.0, withStandIn ? 46.172057 : -46.172057, 193.873367, 16.344114);
            checkLabel(key, placed, "Transfinite Induction over Ordinal Numbers",
                withStandIn ? Slot.RIGHT : Slot.BELOW_FAR, withStandIn ? 12 : 9,
                withStandIn ? transfiniteText : transfiniteDense,
                withStandIn ? 99.558441 : 22.0, withStandIn ? 0.0 : 44.129043,
                withStandIn ? 127.116882 : 185.041336, withStandIn ? 16.344114 : 12.258085);
            checkLabel(key, placed, "Cardinal Arithmetic under the Continuum Hypothesis", Slot.LEFT, 12,
                cardinalText, -100.392456, 22.0, 128.784912, 16.344114);
            checkLabel(key, placed, "Ultrafilter Lemma and Boolean Prime Ideal Theorem", Slot.BELOW_FAR, 12,
                ultrafilterText, 0.0, 68.172057, 198.541412, 16.344114);
            checkLabel(key, placed, "Kuratowski Zorn Lemma for Partially Ordered Sets", Slot.RIGHT, 12,
                kuratowskiText, 100.656464, 22.0, 129.312927, 16.344114);
            if (withStandIn)
                checkHistogram(key, placed, hidden, 1, 0, 5, 0);
            else
                checkHistogram(key, placed, hidden, 1, 1, 4, 0);
            checkList(key + " hidden", Collections.<String>emptyList(), hidden);
            check6(key + " max leader", 78.656464, maxLeader(placed, 1.0));
        }
        for (int[] viewport : new int[][]{{500, 300}, {200, 130}}) {
            for (String mode : new String[]{"none", "stand-in"}) {
                Rectangle2D standIn = mode.equals("stand-in") ? standIn(longScene, 1.0) : null;
                List<String> hidden = new ArrayList<String>();
                List<P> placed = place(longScene.nodes, 1.0, area(viewport[0], viewport[1]),
                    standIn, null, null, noneForced(), hidden);
                printHistogram("histogram " + viewport[0] + "x" + viewport[1] + " " + mode,
                    placed, hidden);
                System.out.println("max leader " + viewport[0] + "x" + viewport[1] + " " + mode
                    + "=" + f6(maxLeader(placed, 1.0)));
                String key = "5.5 " + viewport[0] + "x" + viewport[1] + " " + mode;
                boolean withStandIn = mode.equals("stand-in");
                if (viewport[0] == 500) {
                    if (withStandIn) checkHistogram(key, placed, hidden, 1, 0, 5, 0);
                    else checkHistogram(key, placed, hidden, 1, 1, 4, 0);
                    check6(key + " max leader", 78.656464, maxLeader(placed, 1.0));
                } else {
                    if (withStandIn) {
                        checkHistogram(key, placed, hidden, 1, 0, 1, 4);
                        checkList(key + " hidden", Arrays.asList(
                            "Transfinite Induction over Ordinal Numbers",
                            "Cardinal Arithmetic under the Continuum Hypothesis",
                            "Ultrafilter Lemma and Boolean Prime Ideal Theorem",
                            "Kuratowski Zorn Lemma for Partially Ordered Sets"), hidden);
                    } else {
                        checkHistogram(key, placed, hidden, 1, 0, 2, 3);
                        checkList(key + " hidden", Arrays.asList(
                            "Transfinite Induction over Ordinal Numbers",
                            "Cardinal Arithmetic under the Continuum Hypothesis",
                            "Kuratowski Zorn Lemma for Partially Ordered Sets"), hidden);
                    }
                    check6(key + " max leader", 46.172057, maxLeader(placed, 1.0));
                }
            }
        }
    }

    static void section56(Scene dense) {
        System.out.println();
        System.out.println("== 5.6 Stickiness and invalidation ==");
        // Pan case.
        List<P> baseline1128 = place(dense.nodes, 1.0, area(1128, 364), standIn(dense, 1.0),
            null, null, noneForced(), new ArrayList<String>());
        Rectangle2D panned = new Rectangle2D.Double(-564 + 1, -182, 1128, 364);
        List<P> pannedResult = place(dense.nodes, 1.0, panned, standIn(dense, 1.0),
            null, prevOf(baseline1128), noneForced(), new ArrayList<String>());
        System.out.println("pan +1px: labels=" + pannedResult.size()
            + " all slot/text/font preserved=" + preserved(baseline1128, pannedResult));
        List<P> pannedAgain = place(dense.nodes, 1.0, panned, standIn(dense, 1.0),
            null, prevOf(pannedResult), noneForced(), new ArrayList<String>());
        System.out.println("pan fixed point=" + samePlacement(pannedResult, pannedAgain));
        check("5.6 pan labels", 11, pannedResult.size());
        check("5.6 pan preserved", true, preserved(baseline1128, pannedResult));
        check("5.6 pan fixed point", true, samePlacement(pannedResult, pannedAgain));

        // One-pixel retention case (200x130 with stand-in).
        Rectangle2D v200 = area(200, 130);
        List<P> base200 = place(dense.nodes, 1.0, v200, standIn(dense, 1.0), null, null,
            noneForced(), new ArrayList<String>());
        dumpPlacement("baseline 200x130", base200, 1.0);
        checkLabel("5.6 baseline 200x130", base200, "Axiom of Choice", Slot.ABOVE, 12, "Axiom of Choice",
            -17.0, -56.172057, 91.104675, 16.344114);
        checkLabel("5.6 baseline 200x130", base200, "Theorem", Slot.BELOW_FAR, 9, "Theorem",
            -51.0, 16.129043, 38.295258, 12.258085);
        checkLabel("5.6 baseline 200x130", base200, "Pairing", Slot.LEFT, 9, "Pairing",
            -79.976112, 0.0, 29.952225, 12.258085);
        checkLabel("5.6 baseline 200x130", base200, "Infinity", Slot.RIGHT, 9, "Infinity",
            79.877113, 0.0, 29.754227, 12.258085);
        checkLabel("5.6 baseline 200x130", base200, "Separation", Slot.BELOW, 12, "Separation",
            -51.0, 56.172057, 61.260422, 16.344114);
        checkLabel("5.6 baseline 200x130", base200, "Comprehension", Slot.BELOW, 9, "Comprehension",
            17.0, 54.129043, 67.716476, 12.258085);
        List<Node> moved = new ArrayList<Node>();
        for (Node node : dense.nodes)
            moved.add(new Node(node.name, node.r, node.selected,
                node.name.equals("Infinity") ? 51 : node.x,
                node.name.equals("Infinity") ? -1 : node.y));
        List<P> retained = place(moved, 1.0, v200, standIn(dense, 1.0), null, prevOf(base200),
            noneForced(), new ArrayList<String>());
        dumpPlacement("after Infinity -> (51,-1), previous=baseline", retained, 1.0);
        P infinity = find(retained, "Infinity");
        System.out.println("retention Infinity slot=" + infinity.slot + " a=" + pt(infinity.x, infinity.y)
            + " others bit-identical=" + othersIdentical(base200, retained, "Infinity"));
        List<P> retainedAgain = place(moved, 1.0, v200, standIn(dense, 1.0), null,
            prevOf(retained), noneForced(), new ArrayList<String>());
        System.out.println("retention fixed point=" + samePlacement(retained, retainedAgain));
        checkLabel("5.6 retention", retained, "Infinity", Slot.RIGHT, 9, "Infinity",
            79.877113, -1.0, 29.754227, 12.258085);
        check("5.6 retention others bit-identical", true, othersIdentical(base200, retained, "Infinity"));
        check("5.6 retention fixed point", true, samePlacement(retained, retainedAgain));
        System.out.println("retention collisions ll=" + labelLabelCollisions(retained)
            + " ld=" + labelDiscCollisions(retained, moved, 1.0));
        check("5.6 retention collisions ll", 0, labelLabelCollisions(retained));
        check("5.6 retention collisions ld", 0, labelDiscCollisions(retained, moved, 1.0));

        // Hover-only -> forced (section 5.10).
        Set<String> forcedNames = new LinkedHashSet<String>();
        forcedNames.add("Well-Ordering");
        List<String> forcedHidden = new ArrayList<String>();
        List<P> forcedResult = place(dense.nodes, 1.0, v200, standIn(dense, 1.0), null,
            prevOf(base200), forcedNames, forcedHidden);
        dumpPlacement("hover-only -> forced Well-Ordering (previous=baseline 200x130)", forcedResult, 1.0);
        check("5.10 hover-forced placed", 6, forcedResult.size());
        checkLabel("5.10 hover-forced", forcedResult, "Well-Ordering", Slot.BELOW, 12, "Well-Ordering",
            51.0, 56.172057, 79.308578, 16.344114);
        checkList("5.10 hover-forced hidden", Arrays.asList("Extensionality", "Power Set",
            "Foundation / Regularity", "Replacement Scheme", "Union", "Comprehension"),
            forcedHidden);
        checkKept("5.10 hover-forced kept", base200, forcedResult, "Axiom of Choice");
        checkKept("5.10 hover-forced kept", base200, forcedResult, "Theorem");
        checkKept("5.10 hover-forced kept", base200, forcedResult, "Pairing");
        checkKept("5.10 hover-forced kept", base200, forcedResult, "Infinity");
        checkKept("5.10 hover-forced kept", base200, forcedResult, "Separation");
        System.out.println("hover-forced collisions ll=" + labelLabelCollisions(forcedResult)
            + " ld=" + labelDiscCollisions(forcedResult, dense, 1.0));
        check("5.10 hover-forced collisions ll", 0, labelLabelCollisions(forcedResult));
        check("5.10 hover-forced collisions ld", 0, labelDiscCollisions(forcedResult, dense, 1.0));
        List<P> forcedAgain = place(dense.nodes, 1.0, v200, standIn(dense, 1.0), null,
            prevOf(forcedResult), forcedNames, new ArrayList<String>());
        check("5.10 hover-forced fixed point", true, samePlacement(forcedResult, forcedAgain));

        // Invalidation case.
        List<Node> two = new ArrayList<Node>();
        two.add(new Node("Alpha", 8, true, 0, 0));
        two.add(new Node("Beta", 8, false, -40, 0));
        Rectangle2D v400 = area(400, 300);
        List<P> invalidationBase = place(two, 1.0, v400, null, null, null, noneForced(),
            new ArrayList<String>());
        dumpPlacement("invalidation baseline", invalidationBase, 1.0);
        checkLabel("5.6 invalidation baseline", invalidationBase, "Alpha", Slot.ABOVE, 12, "Alpha",
            0.0, -22.172057, 32.292221, 16.344114);
        checkLabel("5.6 invalidation baseline", invalidationBase, "Beta", Slot.ABOVE, 12, "Beta",
            -40.0, -22.172057, 25.632172, 16.344114);
        List<Node> twoMoved = new ArrayList<Node>();
        twoMoved.add(two.get(0));
        twoMoved.add(new Node("Beta", 8, false, 0, -35));
        List<P> invalidated = place(twoMoved, 1.0, v400, null, null, prevOf(invalidationBase),
            noneForced(), new ArrayList<String>());
        dumpPlacement("invalidation after Beta -> (0,-35), previous=baseline", invalidated, 1.0);
        P alpha = find(invalidated, "Alpha");
        P beta = find(invalidated, "Beta");
        System.out.println("invalidation Alpha slot=" + alpha.slot + " (a=" + pt(alpha.x, alpha.y)
            + ") Beta slot=" + beta.slot + " a=" + pt(beta.x, beta.y));
        checkLabel("5.6 invalidation after", invalidated, "Alpha", Slot.BELOW, 12, "Alpha",
            0.0, 22.172057, 32.292221, 16.344114);
        checkLabel("5.6 invalidation after", invalidated, "Beta", Slot.ABOVE, 12, "Beta",
            0.0, -57.172057, 25.632172, 16.344114);
        System.out.println("invalidation collisions ll=" + labelLabelCollisions(invalidated)
            + " ld=" + labelDiscCollisions(invalidated, twoMoved, 1.0));
        check("5.6 invalidation collisions ll", 0, labelLabelCollisions(invalidated));
        check("5.6 invalidation collisions ld", 0, labelDiscCollisions(invalidated, twoMoved, 1.0));
        List<P> invalidatedAgain = place(twoMoved, 1.0, v400, null, null, prevOf(invalidated),
            noneForced(), new ArrayList<String>());
        System.out.println("invalidation fixed point=" + samePlacement(invalidated, invalidatedAgain));
        check("5.6 invalidation fixed point", true, samePlacement(invalidated, invalidatedAgain));
    }

    static void section57(Scene dense, Scene longScene) {
        System.out.println();
        System.out.println("== 5.7 Painted-ink zoom matrix ==");
        printInk("dense z=0.25", dense, 0.25, standIn(dense, 0.25),
            6, "BELOW_FAR", -4.25, 31.672057, false, 0.4958, 2.155886, 2.155886, 0, 0, 0,
            Arrays.asList("Power Set", "Replacement Scheme", "Union", "Separation",
                "Comprehension", "Well-Ordering"));
        printInk("dense z=1.00", dense, 1.00, standIn(dense, 1.00),
            11, "ABOVE", -17.0, -56.172057, false, 0.5156, 1.655886, 1.655886, 0, 0, 0,
            Arrays.asList("Union"));
        printInk("dense z=2.00", dense, 2.00, standIn(dense, 2.00),
            11, "ABOVE", -34.0, -98.172057, true, 0.7578, 19.921654, 19.921654, 0, 0, 1,
            Arrays.asList("Replacement Scheme"));
        printInk("long z=1 none", longScene, 1.00, null,
            6, "ABOVE", -22.0, -22.172057, true, 0.4793, 7.655886, 7.827943, 0, 0, 0,
            Collections.<String>emptyList());
        printInk("long z=1 stand-in", longScene, 1.00, standIn(longScene, 1.00),
            6, "ABOVE", -22.0, -22.172057, true, 0.2151, 5.655886, 5.655886, 0, 0, 0,
            Collections.<String>emptyList());
    }

    static void section58() {
        System.out.println();
        System.out.println("== 5.8 Enclosure-label fixture (square hull half-extent 50) ==");
        List<Rectangle2D> discAtOrigin = new ArrayList<Rectangle2D>();
        discAtOrigin.add(new Rectangle2D.Double(-8, -8, 16, 16));
        Encl interior = enclosureRule("Axioms", EMPH, area(1128, 364),
            new ArrayList<Rectangle2D>(), true);
        System.out.println("interior: mode=" + interior.mode + " a=" + pt(interior.x, interior.y)
            + " w=" + f6(interior.w) + " h=" + f6(interior.h)
            + " emphaticAtAnchor=" + interior.emphaticAtAnchor + " leader=" + interior.leaderString());
        check("5.8 interior mode", "INTERIOR", interior.mode);
        check6("5.8 interior x", 0.0, interior.x);
        check6("5.8 interior y", 0.0, interior.y);
        check6("5.8 interior w", 55.065384, interior.w);
        check6("5.8 interior h", 20.430143, interior.h);
        check("5.8 interior emphaticAtAnchor", false, interior.emphaticAtAnchor);
        check("5.8 interior leader empty", false, interior.hasLeader());

        Encl arc = enclosureRule("Axioms", EMPH, area(1128, 364), discAtOrigin, true);
        System.out.println("arc: mode=" + arc.mode + " edge=" + arc.edge + " a=" + pt(arc.x, arc.y)
            + " w=" + f6(arc.w) + " h=" + f6(arc.h) + " leader=" + arc.leaderString());
        check("5.8 arc mode", "ARC", arc.mode);
        check("5.8 arc edge", "bottom", arc.edge);
        check6("5.8 arc x", 0.0, arc.x);
        checkClose("5.8 arc y exact", -38.78492832183838, arc.y, 1e-9);
        check6("5.8 arc w", 55.065384, arc.w);
        check6("5.8 arc h", 20.430143, arc.h);
        check("5.8 arc leader empty", false, arc.hasLeader());

        Encl external = enclosureRule(STAND_IN_TEXT, EMPH, area(320, 320),
            new ArrayList<Rectangle2D>(), true);
        System.out.println("external: mode=" + external.mode + " edge=" + external.edge
            + " lane=" + external.lane + " a=" + pt(external.x, external.y)
            + " leader=" + external.leaderString());
        check("5.8 external mode", "EXTERNAL", external.mode);
        check("5.8 external edge", "bottom", external.edge);
        check("5.8 external lane", 0, external.lane);
        check6("5.8 external x", 0.0, external.x);
        check6("5.8 external y", -64.215072, external.y);
        check("5.8 external leader", true, external.hasLeader());
        if (external.hasLeader()) {
            check6("5.8 external leader x", 0.0, external.leaderX.doubleValue());
            check6("5.8 external leader y", -50.0, external.leaderY.doubleValue());
        }

        Encl emphaticTerminal = enclosureRule(STAND_IN_TEXT, EMPH,
            area(120, 120), new ArrayList<Rectangle2D>(), true);
        System.out.println("emphatic terminal: mode=" + emphaticTerminal.mode + " a="
            + pt(emphaticTerminal.x, emphaticTerminal.y) + " totalCandidates="
            + emphaticTerminal.totalCandidates + " perEdge="
            + Arrays.toString(emphaticTerminal.perEdge) + " emphaticAtAnchor="
            + emphaticTerminal.emphaticAtAnchor);
        check("5.8 emphatic terminal mode", "EMPHATIC_AT_ANCHOR", emphaticTerminal.mode);
        check6("5.8 emphatic terminal x", 0.0, emphaticTerminal.x);
        check6("5.8 emphatic terminal y", 0.0, emphaticTerminal.y);
        check("5.8 emphatic terminal candidates", 16, emphaticTerminal.totalCandidates);
        checkList("5.8 emphatic terminal perEdge",
            Arrays.asList("2", "6", "2", "6"), perEdgeList(emphaticTerminal.perEdge));
        check("5.8 emphatic terminal emphaticAtAnchor", true, emphaticTerminal.emphaticAtAnchor);

        Encl subtleTerminal = enclosureRule(STAND_IN_TEXT, FULL,
            area(120, 120), new ArrayList<Rectangle2D>(), false);
        System.out.println("subtle terminal: mode=" + subtleTerminal.mode + " a="
            + pt(subtleTerminal.x, subtleTerminal.y) + " totalCandidates="
            + subtleTerminal.totalCandidates + " perEdge="
            + Arrays.toString(subtleTerminal.perEdge));
        check("5.8 subtle terminal mode", "HOVER_ONLY", subtleTerminal.mode);
        check6("5.8 subtle terminal x", 0.0, subtleTerminal.x);
        check6("5.8 subtle terminal y", 0.0, subtleTerminal.y);
        check("5.8 subtle terminal candidates", 8, subtleTerminal.totalCandidates);
        checkList("5.8 subtle terminal perEdge",
            Arrays.asList("2", "6", "0", "0"), perEdgeList(subtleTerminal.perEdge));
    }

    static List<String> perEdgeList(int[] perEdge) {
        List<String> out = new ArrayList<String>();
        for (int n : perEdge) out.add(Integer.toString(n));
        return out;
    }

    static void section59(Scene dense) {
        System.out.println();
        System.out.println("== 5.9 Zoom x rendering-level matrix (dense 12, no stand-in) ==");
        double[] zooms = {0.25, 0.5, 1.0, 2.0};
        String[] forcedSlots = {"ABOVE", "ABOVE", "ABOVE", "ABOVE_FAR"};
        double[][] forcedAnchors = {{-4.25, -24.672057}, {-8.5, -35.172057},
            {-17.0, -56.172057}, {-34.0, -122.172057}};
        int[] placedCounts = {8, 10, 11, 11};
        int[] fullCounts = {7, 10, 11, 11};
        int[] denseCounts = {1, 0, 0, 0};
        List<List<String>> hoverNames = Arrays.asList(
            Arrays.asList("Pairing", "Union", "Infinity", "Well-Ordering"),
            Arrays.asList("Union", "Well-Ordering"),
            Arrays.asList("Union"),
            Arrays.asList("Replacement Scheme"));
        for (int i = 0; i < zooms.length; i++) {
            double zoom = zooms[i];
            List<String> hidden = new ArrayList<String>();
            List<P> placed = place(dense.nodes, zoom, area(1128, 364), null, null, null,
                noneForced(), hidden);
            int full = 0, denseFont = 0, truncated = 0;
            for (P p : placed) {
                if (p.truncated()) truncated++;
                else if (p.font == DENSE) denseFont++;
                else full++;
            }
            P forced = find(placed, "Axiom of Choice");
            System.out.println("5.9 z=" + zoom + " FULL/DENSE placed=" + placed.size()
                + " (full " + full + ", dense " + denseFont + ", truncated " + truncated
                + ") hover-only=" + hidden + " forced=" + forced.slot + " a=" + pt(forced.x, forced.y));
            List<String> overTarget = overTargetLabels(null, false, placed, noneForced());
            System.out.println("5.9 z=" + zoom + " OVER_TARGET placed=" + overTarget.size()
                + " labels=" + overTarget);
            System.out.println("5.9 z=" + zoom + " label/label collisions="
                + labelLabelCollisions(placed) + " label/disc collisions="
                + labelDiscCollisions(placed, dense, zoom));
            String key = "5.9 z=" + zoom;
            check(key + " FULL/DENSE placed", placedCounts[i], placed.size());
            check(key + " full", fullCounts[i], full);
            check(key + " dense", denseCounts[i], denseFont);
            check(key + " truncated", 0, truncated);
            checkList(key + " hover-only", hoverNames.get(i), hidden);
            check("5.9 z=" + zoom + " forced present", true, forced != null);
            if (forced != null) {
                check(key + " forced slot", forcedSlots[i], forced.slot.name());
                check6(key + " forced x", forcedAnchors[i][0], forced.x);
                check6(key + " forced y", forcedAnchors[i][1], forced.y);
            }
            checkList(key + " OVER_TARGET", Arrays.asList("Axiom of Choice"), overTarget);
            check(key + " collisions ll", 0, labelLabelCollisions(placed));
            check(key + " collisions ld", 0, labelDiscCollisions(placed, dense, zoom));
        }

        // Emphatic-enclosure cell of section 5.9.
        List<Node> one = new ArrayList<Node>();
        one.add(new Node("Theorem", 8, false, 0, -80));
        List<Rectangle2D> discs = new ArrayList<Rectangle2D>();
        discs.add(discRect(one.get(0), 1.0));
        Encl emphatic = enclosureRule("Axioms", EMPH, area(1128, 364), discs, true);
        List<Rectangle2D> seedEmphatic = new ArrayList<Rectangle2D>();
        seedEmphatic.add(emphatic.rect());
        List<P> emphaticNode = place(one, 1.0, area(1128, 364), null, seedEmphatic, null,
            noneForced(), new ArrayList<String>());
        List<String> emphaticOverTarget = overTargetLabels("Axioms", true, emphaticNode, noneForced());
        int emphaticFullDense = (emphatic.placedLabel() ? 1 : 0) + emphaticNode.size();
        System.out.println("5.9 emphatic cell: enclosure=" + emphatic.mode + " a="
            + pt(emphatic.x, emphatic.y) + " " + f6(emphatic.w) + "x" + f6(emphatic.h)
            + "; node=" + emphaticNode.get(0).slot + " a=" + pt(emphaticNode.get(0).x, emphaticNode.get(0).y)
            + " " + f6(emphaticNode.get(0).w) + "x" + f6(emphaticNode.get(0).h)
            + "; FULL/DENSE labels=" + emphaticFullDense
            + "; OVER_TARGET labels=" + emphaticOverTarget);
        check("5.9 emphatic cell enclosure mode", "INTERIOR", emphatic.mode);
        check6("5.9 emphatic cell enclosure x", 0.0, emphatic.x);
        check6("5.9 emphatic cell enclosure y", 0.0, emphatic.y);
        check6("5.9 emphatic cell enclosure w", 55.065384, emphatic.w);
        check6("5.9 emphatic cell enclosure h", 20.430143, emphatic.h);
        check("5.9 emphatic cell enclosure emphaticAtAnchor", false, emphatic.emphaticAtAnchor);
        check("5.9 emphatic cell enclosure leader empty", false, emphatic.hasLeader());
        checkLabel("5.9 emphatic cell", emphaticNode, "Theorem", Slot.ABOVE, 12, "Theorem",
            0.0, -102.172057, 51.060364, 16.344114);
        check("5.9 emphatic cell FULL/DENSE", 2, emphaticFullDense);
        checkList("5.9 emphatic cell OVER_TARGET", Arrays.asList("Axioms"), emphaticOverTarget);
        check("5.9 emphatic cell disjoint", false,
            emphatic.rect().intersects(emphaticNode.get(0).rect()));

        Encl subtle = enclosureRule("Axioms", FULL, area(1128, 364), discs, false);
        List<Rectangle2D> seedSubtle = new ArrayList<Rectangle2D>();
        seedSubtle.add(subtle.rect());
        List<P> subtleNode = place(one, 1.0, area(1128, 364), null, seedSubtle, null,
            noneForced(), new ArrayList<String>());
        List<String> subtleOverTarget = overTargetLabels("Axioms", false, subtleNode, noneForced());
        int subtleFullDense = (subtle.placedLabel() ? 1 : 0) + subtleNode.size();
        System.out.println("5.9 subtle cell: enclosure=" + subtle.mode + " a="
            + pt(subtle.x, subtle.y) + " " + f6(subtle.w) + "x" + f6(subtle.h)
            + "; node=" + subtleNode.get(0).slot + " a=" + pt(subtleNode.get(0).x, subtleNode.get(0).y)
            + " " + f6(subtleNode.get(0).w) + "x" + f6(subtleNode.get(0).h)
            + "; FULL/DENSE labels=" + subtleFullDense
            + "; OVER_TARGET labels=" + subtleOverTarget);
        check("5.9 subtle cell enclosure mode", "INTERIOR", subtle.mode);
        check6("5.9 subtle cell enclosure x", 0.0, subtle.x);
        check6("5.9 subtle cell enclosure y", 0.0, subtle.y);
        check6("5.9 subtle cell enclosure w", 41.340302, subtle.w);
        check6("5.9 subtle cell enclosure h", 16.344114, subtle.h);
        checkLabel("5.9 subtle cell", subtleNode, "Theorem", Slot.ABOVE, 12, "Theorem",
            0.0, -102.172057, 51.060364, 16.344114);
        check("5.9 subtle cell FULL/DENSE", 2, subtleFullDense);
        checkList("5.9 subtle cell OVER_TARGET", Collections.<String>emptyList(), subtleOverTarget);
        check("5.9 subtle cell disjoint", false,
            subtle.rect().intersects(subtleNode.get(0).rect()));
    }

    static void section510(Scene longScene) {
        System.out.println();
        System.out.println("== 5.10 fullTextSlotWasFree for every truncated label (pinned false) ==");
        int truncatedSeen = 0;
        for (int[] viewport : new int[][]{{1128, 364}, {500, 300}, {200, 130}}) {
            for (String mode : new String[]{"none", "stand-in"}) {
                Rectangle2D standIn = mode.equals("stand-in") ? standIn(longScene, 1.0) : null;
                List<String> hidden = new ArrayList<String>();
                List<P> placed = place(longScene.nodes, 1.0, area(viewport[0], viewport[1]),
                    standIn, null, null, noneForced(), hidden);
                for (P p : placed)
                    if (p.truncated()) {
                        truncatedSeen++;
                        System.out.println("5.10 " + viewport[0] + "x" + viewport[1] + " " + mode
                            + " [" + p.text + "] fullTextSlotWasFree=" + p.fullTextSlotWasFree);
                        check("5.10 " + viewport[0] + "x" + viewport[1] + " " + mode + " " + p.text
                            + " fullTextSlotWasFree", false, p.fullTextSlotWasFree);
                    }
            }
        }
        System.out.println("5.10 truncated labels seen=" + truncatedSeen);
        check("5.10 truncated labels seen", 21, truncatedSeen);
    }

    static void section510Bounds(Scene dense, Scene longScene) {
        System.out.println();
        System.out.println("== 5.10 per-slot leader/support maxima (per-zoom stand-in) ==");
        Scene[] scenes = {dense, dense, longScene, longScene};
        double[] zooms = {1.0, 2.0, 1.0, 2.0};
        String[] tags = {"dense z=1.0", "dense z=2.0", "long z=1.0", "long z=2.0"};
        int[] expectedCounts = {11, 11, 6, 5};
        double[] expectedMaxLeaders = {77.894615, 73.611911, 78.656464, 86.656464};
        double[] expectedMaxSupports = {138.704698, 118.655013, 143.545734, 151.533443};
        for (int i = 0; i < scenes.length; i++) {
            Scene scene = scenes[i];
            double zoom = zooms[i];
            List<String> hidden = new ArrayList<String>();
            List<P> placed = place(scene.nodes, zoom, area(1128, 364), standIn(scene, zoom),
                null, null, noneForced(), hidden);
            double maxLeader = maxLeader(placed, zoom);
            double maxSupport = maxSupport(placed, zoom);
            System.out.println("5.10 " + tags[i] + " labels=" + placed.size()
                + " maxLeader=" + f6(maxLeader) + " maxSupport=" + f6(maxSupport)
                + " leaderCrossings=" + leaderCrossings(placed, zoom));
            check("5.10 " + tags[i] + " labels", expectedCounts[i], placed.size());
            check6("5.10 " + tags[i] + " max leader", expectedMaxLeaders[i], maxLeader);
            check6("5.10 " + tags[i] + " max support", expectedMaxSupports[i], maxSupport);
            check("5.10 " + tags[i] + " leader crossings", 0, leaderCrossings(placed, zoom));
            for (P p : placed) {
                double r = Math.max(2.0, p.node.r * zoom);
                check6("5.10 " + tags[i] + " " + p.node.name + " leader form",
                    leaderForm(p.slot, r, p.w, p.h), leader(p, zoom));
                check6("5.10 " + tags[i] + " " + p.node.name + " support form",
                    supportForm(p.slot, r, p.w, p.h), support(p, zoom));
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("FixtureProbe: self-checking reference oracle for the node-separation specification fixtures");
        System.out.println("(not production code; java -Djava.awt.headless=true FixtureProbe.java)");
        System.out.println();
        System.out.println("font metrics: h(12)=" + f6(h(FULL)) + " h(9)=" + f6(h(DENSE))
            + " h(10)=" + f6(h(SMALL)) + " h(15 bold)=" + f6(h(EMPH)));
        System.out.println("widths: w(\"Axiom of Choice\",12)=" + f6(w("Axiom of Choice", FULL))
            + " w(\"Axioms\",15b)=" + f6(w("Axioms", EMPH))
            + " w(\"Axioms\",12)=" + f6(w("Axioms", FULL))
            + " w(\"Basic Definitions and Theorems\",15b)=" + f6(w(STAND_IN_TEXT, EMPH))
            + " w(\"Basic Definitions and Theorems\",10)=" + f6(w(STAND_IN_TEXT, SMALL)));
        check6("font h(12)", 16.344114, h(FULL));
        check6("font h(9)", 12.258085, h(DENSE));
        check6("font h(10)", 13.619987, h(SMALL));
        check6("font h(15 bold)", 20.430143, h(EMPH));
        check6("width Axiom of Choice 12", 91.104675, w("Axiom of Choice", FULL));
        check6("width Axioms 15 bold", 55.065384, w("Axioms", EMPH));
        check6("width Axioms 12", 41.340302, w("Axioms", FULL));
        check6("width Basic Definitions 15 bold", 235.291611, w(STAND_IN_TEXT, EMPH));
        check6("width Basic Definitions 10", 148.289871, w(STAND_IN_TEXT, SMALL));

        Scene dense = dense();
        Scene longScene = longScene();
        section54(dense);
        section55(longScene);
        section56(dense);
        section57(dense, longScene);
        section58();
        section59(dense);
        section510(longScene);
        section510Bounds(dense, longScene);
        System.out.println();
        System.out.println("checks=" + checks + " failures=" + failures);
        if (failures > 0) {
            System.out.println("ORACLE FAILED: " + failures + " of " + checks + " checks failed");
            System.exit(1);
        }
        System.out.println("all checks passed");
    }
}
