# Directional arrowhead prominence mockups

Renders used to choose the arrowhead treatment. `ArrowheadMockups.java` mirrors
`GraphPainter.paintArrow` geometry, `GraphTheme` light and dark colours, and the real paint order
(edges and arrowheads first, node fill and ring painted over them afterwards). The edge line and the
node ring are stroked at `1.4 x zoom` px, as the real painter does inside its world transform; the
head is a constant-screen-size triangle, as the design specifies.

Regenerate:

```
java ArrowheadMockups.java png
```

Chosen treatment and sizes are written to `png/`; rejected alternatives are written to
`png/rejected/` so they cannot be mistaken for the decision.

| File | Shows |
| --- | --- |
| `before-detail-*.png` | Today: 8 px head, base 7 px (4x magnified) |
| `bigger-detail-*.png` | Chosen treatment B: 12.5 px head, base 10.5 px, edge colour, no gap |
| `zoom-b-uniform-*.png` | Chosen sizing rule `max(12.5, 8.68 x zoom)` at 0.6x, 1.6x, 3x |

Rejected, under `png/rejected/`: a canvas-colour halo around the head (paints a notch through
enclosure fills and border strokes, and loses most of its contrast in the dim state), an emphasis
ink for the head, a stealth concave head, and the constant-screen-size rule that does not grow with
zoom.
