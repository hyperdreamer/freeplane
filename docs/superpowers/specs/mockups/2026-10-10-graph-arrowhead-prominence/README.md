# Directional arrowhead prominence mockups

Renders used to choose the arrowhead treatment. `ArrowheadMockups.java` mirrors
`GraphPainter.paintArrow` geometry, the `GraphTheme` light and dark colours, and the real paint
order (edges and arrowheads first, node fill and ring painted over them afterwards), at zoom 1.6
with a 14 world-unit node radius.

Regenerate:

```
java ArrowheadMockups.java png
```

| File | Shows |
| --- | --- |
| `before-detail-*.png` | Today: 8 px head, base 7 px, edge colour, no separation (4x magnified) |
| `halo-detail-*.png` | Chosen: 12.5 px head, base 10.5 px, canvas-colour halo (4x magnified) |
| `zoom-uniform-*.png` | Chosen sizing rule `max(12.5, 8.68 x zoom)` at 0.6x, 1.6x, 3x |
| `zoom-constant-3p0.png` | Rejected constant-screen-size rule at 3x |
