# Arrowhead zoom proportionality mockups

Renders used to choose the arrowhead sizing rule for the reported low-zoom clutter.
`ArrowheadZoomMockups.java` mirrors `GraphPainter.paintArrow` triangle geometry and half-base
ratio 0.42 and the real paint order (edges and heads first, node fill and ring afterwards), in
the light-canvas palette (`edgeColor` 73,84,100, `nodeFill` 229,239,249, `nodeStroke` 38,91,137)
over the reported background (238,238,238). Every panel shows the same world scene, a hub node of
radius 14 world units with six long incoming edges; every edge is long enough that the cap stays
unbound and carries one head, so the renderer exercises the requested length rule only, not the
cap or the bidirectional split. Panels are magnified uniformly by the final image scale, so
head : node ratios are exact.

The zoom levels are the ones recorded for the reporter's workspace file `math.fpg` and its
Dropbox sync-conflict siblings (0.1485, 0.378, 0.75, 2.798), plus 1.0. The reported view is the
0.378 state, where the shipped rule draws 12.5 px heads against 6.0-10.6 px node discs.

Regenerate:

```
java ArrowheadZoomMockups.java png
```

| File | Shows |
| --- | --- |
| `png/before-shipped-rule.png` | Shipped rule `max(12.5, 8.68 x z)` at 0.1485/0.378/0.75/1/2.798: ratio to the largest node diameter 3.01/1.18/0.60/0.45/0.31 |
| `png/chosen-rule-b.png` | Chosen rule `12.5 x z`: constant 0.45 ratio to the largest node, 12.5 px at 1x, 4.73 px at 0.378 |
| `png/zoom-rule-comparison.png` | All three rules above, identical scene per panel |

Rejected alternatives are written under `png/rejected/` so they cannot be mistaken for the
decision.

| File | Shows |
| --- | --- |
| `png/rejected/rule-a-smaller-heads.png` | `8.68 x z` (0.62 x max node radius, the shipped growth term): constant 0.31 ratio, but heads shrink to 8.68 px at 1x, below the size approved in the 2026-10-10 design |

A third rejected option, a small screen-space floor (for example 4 px on top of either
proportional rule), restores node-sized heads at the 0.1485 overview zoom and was dropped
for that reason.
