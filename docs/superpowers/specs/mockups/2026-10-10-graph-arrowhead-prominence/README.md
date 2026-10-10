# Directional arrowhead prominence mockups

Renders used to choose the arrowhead treatment. `ArrowheadMockups.java` mirrors
`GraphPainter.paintArrow` geometry, `GraphTheme` light and dark colours, the `1.4 x zoom` edge and
node-ring strokes, and the real paint order (edges and arrowheads first, node fill and ring painted
over them afterwards). The head length follows the design rule `max(12.5, 8.68 x zoom)` screen px,
which is 13.9 px at the rendered zoom of 1.6.

Regenerate:

```
java ArrowheadMockups.java png
```

Chosen treatment and sizes are written to `png/`; rejected alternatives are written to
`png/rejected/` so they cannot be mistaken for the decision.

| File | Shows |
| --- | --- |
| `png/before-detail-*.png` | Today: 8 px head, base 7 px (4x magnified) |
| `png/bigger-detail-*.png` | Chosen treatment B at zoom 1.6: 13.9 px head, 11.7 px base, edge colour, no gap |
| `png/zoom-b-uniform-*.png` | Chosen sizing rule `max(12.5, 8.68 x zoom)` at 0.6x (12.5 px), 1.6x (13.9 px) and 3x (26.0 px) |

Rejected, under `png/rejected/`: a canvas-colour halo around the head (paints a notch through
enclosure fills and border strokes, and loses most of its contrast in the dim state), an emphasis
ink for the head, a stealth concave head, and the constant-screen-size rule that does not grow with
zoom.
