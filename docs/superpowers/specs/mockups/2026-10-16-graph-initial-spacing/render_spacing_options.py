#!/usr/bin/env python3
"""Render a fixed-zoom design comparison; this is not an application capture."""
from pathlib import Path
import json
import math
from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parent
S = 2
W, H = 1584, 790
canvas = Image.new('RGB', (W * S, H * S), '#f5f7fb')
draw = ImageDraw.Draw(canvas)
FONT = '/usr/share/fonts/TTF/DejaVuSans.ttf'

def text(x, y, value, size=18, color='#42536b', anchor=None):
    draw.text((x*S, y*S), value, font=ImageFont.truetype(FONT, size*S), fill=color, anchor=anchor)

def box(bounds, fill, outline=None, width=1, radius=12):
    draw.rounded_rectangle(tuple(v*S for v in bounds), radius=radius*S, fill=fill, outline=outline, width=width*S)

def line(points, color, width=1):
    draw.line([(x*S,y*S) for x,y in points], fill=color, width=width*S)

text(40, 26, 'Graph View: choose the amount of extra space', 30, '#182a45')
text(40, 72, 'Same eight nodes, same node size, same zoom. Schematic of initial placement.', 19)

scale, radius, n = 2.0, 8.0, 8
variants = [('Current', 6, 'Reference', '#8090a8'),
            ('1  Moderate', 24, 'Recommended', '#1d7663'),
            ('2  Roomier', 40, 'More spread', '#5264aa')]
measurements = []
for i, (title, gap, tag, accent) in enumerate(variants):
    x0 = 24 + i * 520
    box((x0, 122, x0+504, 690), '#ffffff', '#cbd4e0', 1)
    text(x0+24, 143, title, 25, '#182a45')
    text(x0+24, 183, tag, 17, accent)
    cx, cy = x0+252, 418
    ring = (2*radius+gap)/(2*math.sin(math.pi/n))
    positions = [(cx + scale*ring*math.cos(-math.pi/2+2*math.pi*k/n),
                  cy + scale*ring*math.sin(-math.pi/2+2*math.pi*k/n)) for k in range(n)]
    # A faint circular envelope indicates grouping only, not exact Freeplane hull geometry.
    envelope = scale*(ring+radius+16)
    draw.ellipse(((cx-envelope)*S, (cy-envelope)*S, (cx+envelope)*S, (cy+envelope)*S),
                 fill='#f0f5fc', outline='#c2d0e5', width=S)
    for k in range(n):
        line((positions[k], positions[(k+1)%n]), '#a8b6ca', 2)
    for k, (x,y) in enumerate(positions):
        r=radius*scale
        draw.ellipse(((x-r)*S,(y-r)*S,(x+r)*S,(y+r)*S), fill='#326aa5', outline='#24507e', width=S)
        text(x, y, str(k+1), 15, '#ffffff', 'mm')
    # A separate two-node ruler makes the gap comparable without variable ring angles.
    y=620
    middle=x0+252
    d=(2*radius+gap)*scale
    p,q=middle-d/2,middle+d/2
    r=radius*scale
    for x in (p,q):
        draw.ellipse(((x-r)*S,(y-r)*S,(x+r)*S,(y+r)*S), fill='#326aa5')
    left,right=p+r,q-r
    line(((left,y),(right,y)), accent, 2)
    line(((left,y-6),(left,y+6)), accent, 2)
    line(((right,y-6),(right,y+6)), accent, 2)
    text(middle, 661, f'{gap}-unit gap  |  {16+gap}-unit centre spacing', 17, '#42536b', 'mm')
    measured_gap=math.dist(positions[0],positions[1])/scale-2*radius
    assert math.isclose(measured_gap, gap, abs_tol=1e-10)
    measurements.append({'variant':title,'preferred_gap':gap,'node_radius':radius,
                         'node_count':n,'ring_radius':ring,'center_to_center':2*radius+gap,
                         'measured_adjacent_edge_gap':measured_gap,'pixels_per_layout_unit':scale})

text(40, 719, 'Both proposed choices use the wider spacing for initial placement and spring targets.', 19, '#243951')
text(40, 751, 'Final positions depend on connections and pins. This illustration is not a settled-layout measurement.', 17)
canvas.resize((W,H), Image.Resampling.LANCZOS).save(OUT/'spacing-options.png')
(OUT/'spacing-options.measurements.json').write_text(json.dumps(measurements,indent=2)+'\n')
print('Rendered:', OUT/'spacing-options.png')
print('Verified the illustrated adjacent gaps:', [m['preferred_gap'] for m in measurements])
