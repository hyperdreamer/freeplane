#!/usr/bin/env python3
"""Schematic mockups comparing initial cluster-packing options for the Graph Workspace.

Level 1: separate maps seeded into one workspace.
Level 2: sibling clusters seeded inside one map.

Panels share one scale per row. Footprint boxes are conservative layout-estimate
rectangles (schematic, not pixel-accurate renderings of real maps).

Packing rule used by every variant except the "current" reference (production v7):
  place clusters in the variant's order; the first sits at the origin; each next
  cluster takes the candidate position (edge-aligned candidates, assembly-box edges,
  and centre lines) that minimizes the resulting bounding-box area, ties broken by
  smaller distance from the origin, then by candidate enumeration order. The
  assembly is not re-centred; the drawing only translates it into the panel for
  display. Gutter between footprints is SIBLING_GAP = 8 units.
"""
import math
import os

from PIL import Image, ImageDraw, ImageFont

GAP = 8.0          # SIBLING_GAP: minimum gutter between footprints
HULL_CLEAR = 16.0  # parent hull clearance, used only for the dashed parent outline

FONT_REG = "/usr/share/fonts/TTF/DejaVuSans.ttf"
FONT_BOLD = "/usr/share/fonts/TTF/DejaVuSans-Bold.ttf"


def font(size, bold=False):
    path = FONT_BOLD if bold else FONT_REG
    if os.path.exists(path):
        return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def clear(rect, placed, gap=GAP):
    cx, cy, w, h = rect
    for px, py, pw, ph, _ in placed:
        if abs(cx - px) < (w + pw) / 2.0 + gap and abs(cy - py) < (h + ph) / 2.0 + gap:
            return False
    return True


def bbox_area_with(placed, cx, cy, w, h):
    min_x = min([px - pw / 2.0 for px, py, pw, ph, _ in placed] + [cx - w / 2.0])
    max_x = max([px + pw / 2.0 for px, py, pw, ph, _ in placed] + [cx + w / 2.0])
    min_y = min([py - ph / 2.0 for px, py, pw, ph, _ in placed] + [cy - h / 2.0])
    max_y = max([py + ph / 2.0 for px, py, pw, ph, _ in placed] + [cy + h / 2.0])
    return (max_x - min_x) * (max_y - min_y)


def production_candidates(item_w, item_h, placed, gap=GAP):
    if not placed:
        return [0.0], [0.0]
    xs = []
    ys = []
    min_x = min(p['x'] - p['w'] / 2.0 for p in placed)
    max_x = max(p['x'] + p['w'] / 2.0 for p in placed)
    min_y = min(p['y'] - p['h'] / 2.0 for p in placed)
    max_y = max(p['y'] + p['h'] / 2.0 for p in placed)
    for p in placed:
        xs += [p['x'] - (p['w'] + item_w) / 2.0 - gap,
               p['x'] + (p['w'] + item_w) / 2.0 + gap, p['x']]
        ys += [p['y'] - (p['h'] + item_h) / 2.0 - gap,
               p['y'] + (p['h'] + item_h) / 2.0 + gap, p['y']]
    xs += [min_x - item_w / 2.0 - gap, max_x + item_w / 2.0 + gap,
           min_x + item_w / 2.0, max_x - item_w / 2.0]
    ys += [min_y - item_h / 2.0 - gap, max_y + item_h / 2.0 + gap,
           min_y + item_h / 2.0, max_y - item_h / 2.0]
    return sorted(set(round(v, 6) for v in xs)), sorted(set(round(v, 6) for v in ys))


def bbox_area(placed, x, y, item_w, item_h):
    min_x = min([p['x'] - p['w'] / 2.0 for p in placed] + [x - item_w / 2.0])
    max_x = max([p['x'] + p['w'] / 2.0 for p in placed] + [x + item_w / 2.0])
    min_y = min([p['y'] - p['h'] / 2.0 for p in placed] + [y - item_h / 2.0])
    max_y = max([p['y'] + p['h'] / 2.0 for p in placed] + [y + item_h / 2.0])
    return (max_x - min_x) * (max_y - min_y)


def clears(x, y, item_w, item_h, placed, gap=GAP):
    for p in placed:
        dx = abs(x - p['x']) - (item_w + p['w']) / 2.0
        dy = abs(y - p['y']) - (item_h + p['h']) / 2.0
        if dx < gap and dy < gap:
            return False
    return True


def pack(clusters):
    """Production v7: edge-aligned candidates (+ assembly-box edges and centre lines),
    minimum resulting bounding-box area, ties by origin distance then enumeration order."""
    ordered = [dict(label=label, w=w, h=h, order=index)
               for index, (label, w, h) in enumerate(clusters)]
    ordered.sort(key=lambda item: (-(item['w'] * item['h']), item['order']))
    placed = []
    for item in ordered:
        xs, ys = production_candidates(item['w'], item['h'], placed)
        best = None
        for xi, x in enumerate(xs):
            for yi, y in enumerate(ys):
                if not clears(x, y, item['w'], item['h'], placed):
                    continue
                key = (round(bbox_area(placed, x, y, item['w'], item['h']), 6),
                       round(math.hypot(x, y), 6), xi * len(ys) + yi)
                if best is None or key < best[0]:
                    best = (key, x, y)
        if best is None:
            raise RuntimeError('no free slot')
        placed.append(dict(label=item['label'], w=item['w'], h=item['h'], x=best[1], y=best[2]))
    return [(p['x'], p['y'], p['w'], p['h'], p['label']) for p in placed]


def current_ring(clusters):
    n = len(clusters)
    out = []
    if n <= 1:
        for label, w, h in clusters:
            out.append((0.0, 0.0, w, h, label))
        return out
    max_w = max(c[1] for c in clusters)
    max_h = max(c[2] for c in clusters)
    radius = math.hypot(max_w + GAP, max_h + GAP) / (2.0 * math.sin(math.pi / n))
    for i, (label, w, h) in enumerate(clusters):
        angle = 2.0 * math.pi * i / n
        out.append((radius * math.cos(angle), radius * math.sin(angle), w, h, label))
    return out


def bbox(placed):
    min_x = min(cx - w / 2.0 for cx, cy, w, h, _ in placed)
    max_x = max(cx + w / 2.0 for cx, cy, w, h, _ in placed)
    min_y = min(cy - h / 2.0 for cx, cy, w, h, _ in placed)
    max_y = max(cy + h / 2.0 for cx, cy, w, h, _ in placed)
    return min_x, min_y, max_x - min_x, max_y - min_y


def footprint_area(placed):
    _, _, w, h = bbox(placed)
    return w * h


def auto_pack(clusters):
    candidates = [
        ("largest-first", sorted(clusters, key=lambda c: (-(c[1] * c[2]), c[0]))),
        ("workspace-order", list(clusters)),
        ("reverse-order", list(reversed(clusters))),
    ]
    best = None
    best_name = None
    for name, order in candidates:
        placed = pack(order)
        area = footprint_area(placed)
        if best is None or area < footprint_area(best) - 1e-9:
            best, best_name = placed, name
    return best, best_name


def min_pair_gap(placed):
    gaps = []
    for i in range(len(placed)):
        for j in range(i + 1, len(placed)):
            ax, ay, aw, ah, _ = placed[i]
            bx, by, bw, bh, _ = placed[j]
            if abs(ax - bx) >= (aw + bw) / 2.0 - 1e-9:
                gaps.append(abs(ax - bx) - (aw + bw) / 2.0)
            else:
                gaps.append(abs(ay - by) - (ah + bh) / 2.0)
    return min(gaps) if gaps else 0.0


WORKSPACE = [
    ("Measure Theory", 120.0, 70.0),
    ("Real Analysis", 320.0, 240.0),
    ("Axiomatic Set Theory", 380.0, 260.0),
    ("Point-Set Topology", 300.0, 200.0),
]

SIBLINGS = [
    ("Fundamentals", 240.0, 120.0),
    ("ZFC", 260.0, 130.0),
]

COL_W = 530
PANEL_H = 470
MARGIN = 30
HEADER_H = 116
ROW_LABEL_H = 34
COLORS = [
    ("#cfe2f3", "#5b7c9d"),
    ("#d9ead3", "#6a9a5b"),
    ("#fce5cd", "#b98a4a"),
    ("#ead1dc", "#a05f84"),
]


def draw_parent_hull(draw, panel, placed, scale, label):
    px, py, pw, ph = panel
    bx, by, bw, bh = bbox(placed)
    origin_x = px + (pw - bw * scale) / 2.0 - bx * scale
    origin_y = py + (ph - bh * scale) / 2.0 - by * scale
    x0 = origin_x + (bx - HULL_CLEAR) * scale
    y0 = origin_y + (by - HULL_CLEAR) * scale
    x1 = origin_x + (bx + bw + HULL_CLEAR) * scale
    y1 = origin_y + (by + bh + HULL_CLEAR) * scale
    draw.rounded_rectangle((x0, y0, x1, y1), radius=14, outline="#4a6d99", width=2)
    f = font(12, bold=True)
    tw = draw.textlength(label, font=f)
    ty = y0 - 15 if y0 - 15 > py else y0 + 3
    draw.rectangle((x0 + (x1 - x0 - tw) / 2 - 4, ty - 1, x0 + (x1 - x0 + tw) / 2 + 4, ty + 15),
                   fill="#ffffff")
    draw.text((x0 + (x1 - x0 - tw) / 2, ty), label, font=f, fill="#2b4a73")
    return (x1 - x0) / scale


def draw_arrangement(draw, panel, placed, scale):
    px, py, pw, ph = panel
    bx, by, bw, bh = bbox(placed)
    origin_x = px + (pw - bw * scale) / 2.0 - bx * scale
    origin_y = py + (ph - bh * scale) / 2.0 - by * scale
    for i, (cx, cy, w, h, label) in enumerate(placed):
        fill, edge = COLORS[i % len(COLORS)]
        x0, y0 = origin_x + (cx - w / 2.0) * scale, origin_y + (cy - h / 2.0) * scale
        x1, y1 = origin_x + (cx + w / 2.0) * scale, origin_y + (cy + h / 2.0) * scale
        draw.rounded_rectangle((x0, y0, x1, y1), radius=9, fill=fill, outline=edge, width=2)
        dot_r = max(1.5, min(3.5, (y1 - y0) * 0.035))
        cols = max(2, int((x1 - x0) / 26))
        rows = max(1, int((y1 - y0) / 26))
        for r in range(rows):
            for c in range(cols):
                dx = x0 + (c + 0.5) * (x1 - x0) / cols
                dy = y0 + (r + 0.5) * (y1 - y0) / rows
                draw.ellipse((dx - dot_r, dy - dot_r, dx + dot_r, dy + dot_r), fill=edge)
        f = font(11, bold=True)
        tw = draw.textlength(label, font=f)
        draw.rectangle((x0 + 2, y0 + 2, min(x1 - 2, x0 + tw + 10), y0 + 18), fill="#ffffff")
        draw.text((x0 + 6, y0 + 4), label, font=f, fill="#222222")


def draw_panel(draw, x, y, title, placed, row_scale, note, hull_label=None):
    draw.rectangle((x, y, x + COL_W, y + PANEL_H), outline="#bbbbbb", width=1)
    f = font(13, bold=True)
    tw = draw.textlength(title, font=f)
    draw.text((x + (COL_W - tw) / 2, y + 8), title, font=f, fill="#111111")
    panel = (x + 12, y + 34, COL_W - 24, PANEL_H - 80)
    hull_w = None
    if hull_label:
        hull_w = draw_parent_hull(draw, panel, placed, row_scale, hull_label)
    draw_arrangement(draw, panel, placed, row_scale)
    bx, by, bw, bh = bbox(placed)
    lines = ["footprint %d x %d units (area %d)" % (round(bw), round(bh), round(bw * bh))]
    if len(placed) > 1:
        lines.append("smallest gap between clusters: %d units" % round(min_pair_gap(placed)))
    if hull_w is not None:
        lines.append("parent hull width: %d units" % round(hull_w))
    ty = y + PANEL_H - 14 - 14 * len(lines)
    for line in lines:
        draw.text((x + 10, ty), line, font=font(11), fill="#333333")
        ty += 14
    draw.text((x + 10, y + PANEL_H - 16), note, font=font(11, bold=True), fill="#8a2b2b")


def layout_row(clusters):
    largest = pack(sorted(clusters, key=lambda c: (-(c[1] * c[2]), c[0])))
    order_first = pack(list(clusters))
    current = current_ring(clusters)
    return [
        ("Current: ring seeding", current, "hollow center + largest-size rule"),
        ("V1 Snowball - largest-first", largest, "recommended: most compact tested order"),
        ("V2 Snowball - workspace order", order_first, "predictable: first map anchors the packing"),
    ]


def compose(level_title, clusters, hull_label, filename):
    arrangements = layout_row(clusters)
    inner_w = COL_W - 24
    inner_h = PANEL_H - 80
    scale = None
    for _, placed, _ in arrangements:
        bx, by, bw, bh = bbox(placed)
        need = min((inner_w - 40) / max(bw, 1.0), (inner_h - 40) / max(bh, 1.0))
        scale = need if scale is None else min(scale, need)

    ncols = len(arrangements)
    width = MARGIN * 2 + COL_W * ncols + 12 * (ncols - 1)
    height = 16 + HEADER_H + ROW_LABEL_H + PANEL_H + 24
    img = Image.new("RGB", (width, height), "white")
    draw = ImageDraw.Draw(img)

    draw.text((MARGIN, 14), "Graph Workspace - initial cluster packing options (schematic)",
              font=font(20, bold=True), fill="#111111")
    draw.text((MARGIN, 44),
              "A cluster = a boundary footprint (rounded box; conservative layout estimate). "
              "Gutter between footprints = %d units. Panels share one scale." % GAP,
              font=font(12), fill="#444444")
    draw.text((MARGIN, 64),
              "Packing rule: clusters are placed in the variant's order; each next cluster takes the "
              "position that minimizes the resulting footprint; the panel drawing only translates it.",
              font=font(12), fill="#444444")

    y = 16 + HEADER_H
    draw.text((MARGIN, y), level_title, font=font(15, bold=True), fill="#2b4a73")
    y += ROW_LABEL_H
    for i, (title, placed, note) in enumerate(arrangements):
        x = MARGIN + i * (COL_W + 12)
        draw_panel(draw, x, y, title, placed, scale, note, hull_label)

    img.save(filename)
    print("wrote", filename)


def main():
    out_dir = os.path.dirname(os.path.abspath(__file__))
    compose("Level 1 - separate maps in one workspace (4 maps, heterogeneous footprints)",
            WORKSPACE, None,
            os.path.join(out_dir, "cluster-packing-workspace.png"))
    compose("Level 2 - sibling clusters inside one map (parent: Axiomatic Set Theory)",
            SIBLINGS, "Axiomatic Set Theory",
            os.path.join(out_dir, "cluster-packing-siblings.png"))


if __name__ == "__main__":
    main()
