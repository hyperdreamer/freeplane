# Edge-crossing avoidance mockups

- PM run: `pm-run-20261012-021203-0d02a300`
- Topic: `graph-edge-crossing-avoidance`
- Generator: `EdgeRoutingMockups.java` (standalone; run with a JDK, no repository dependencies)
- Output: `edge-crossing-mockups.png`

Fixtures and constants mirror production: disc centres are measured from the reported screenshot
(521x447 px: top 272,170 · left 240,208 · right 307,202 · bottom 275,240), disc radius = 8.0 world x
prominence 1.75 x zoom 1.32 = 18.48 px, edge stroke = 1.4 world x zoom, arrowhead length =
max(12.5, 0.62 x 14.0 x zoom) screen px, arrowhead half base = 0.42 x length.

Panels: A1 today · A2 bounded bow at the mockup's 0.35 x chord cap · A3 around-disc wrap (rejected) ·
A4 hop/bridge (rejected) · B1 edge over a disc today · B2 routed dogleg · C1 reported case before
relocation · C2 after the adjacent-pair position swap (accepted primary mechanism).

The mockup is an illustration of the treatments, not a proof of the algorithm; the design ladder
(±0.15/±0.30/±0.50 bows and doglegs with a route-length cap) is pinned by the design document.

Note: panel C2's swap exchanges two node centres; the design's A2 acceptance rule re-verifies disc
gaps against NodeSeparationProjection.MIN_GAP, it does not promise the gaps are unchanged.
