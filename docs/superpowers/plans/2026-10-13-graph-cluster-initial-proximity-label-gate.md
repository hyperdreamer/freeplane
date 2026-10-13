# Graph Workspace — Label readability gate (baseline vs post-change)

> Evidence note for spec section 8.4 item 5 and section 8.5 of
> `docs/superpowers/specs/2026-10-13-graph-cluster-initial-proximity-spec.md`.
> This is an explicit **user-review gate with no numeric pass/fail threshold**.
> The numbers below are measurements, not assertions.

## 1. Configurations and commands

Both runs use the same A-H fixture set, the same settlement endpoint (first idle frame,
else the 2000th step, then 200 extra steps), the same headless geometry text metrics
(`AwtGeometryTextMetrics(new Font("Dialog", Font.PLAIN, 12), new FontRenderContext(null, true, true))`),
the same placement area (`1440x900`), and `RenderingLevel.FULL`. The viewport centre is the
settled world bounding-box centre of the fixture, so the two configurations are compared at
the same nominal zoom.

- **Baseline**: scratch worktree `/tmp/graph-label-baseline` at the pre-feature commit
  `9b8259bcd1cb2c8d37121e63bcac0107386fed0d` (created with
  `git worktree add --detach /tmp/graph-label-baseline 9b8259bcd1cb2c8d37121e63bcac0107386fed0d`).
- **Post-change**: the lane worktree at `139e031f30cccda74390c01791768a204b409653` (merge base,
  feature integrated).

Probe build and run (the probe is in package `org.freeplane.plugin.graph.canvas` so it calls the
package-private `ScreenLabelPlacement.place(request, previous, fonts, seedObstacles)` overload;
its source is committed at
`docs/superpowers/specs/mockups/2026-10-13-graph-cluster-initial-proximity/label-gate/probe-source/LabelGateProbe.java`):

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
REPO="$(git rev-parse --show-toplevel)"
BASELINE=/tmp/graph-label-baseline
GC=$(find "$HOME/.gradle/caches/modules-2/files-2.1/org.graphstream" -name "*.jar" | tr '\n' ':')
gradle -p "$REPO" :freeplane_plugin_graph:compileTestJava
gradle -p "$BASELINE" :freeplane_plugin_graph:compileTestJava
CP_POST="$REPO/freeplane_plugin_graph/build/classes/java/main:$REPO/freeplane_plugin_graph/build/classes/java/test:$REPO/freeplane/build/classes/java/main:$GC"
CP_BASE="$BASELINE/freeplane_plugin_graph/build/classes/java/main:$BASELINE/freeplane_plugin_graph/build/classes/java/test:$BASELINE/freeplane/build/classes/java/main:$GC"
mkdir -p /tmp/graph-label-probe/classes
"$JAVA_HOME/bin/javac" -source 8 -target 8 -nowarn -cp "$CP_POST" -d /tmp/graph-label-probe/classes \
  /tmp/graph-label-probe/src/org/freeplane/plugin/graph/canvas/LabelGateProbe.java
"$JAVA_HOME/bin/java" -Djava.awt.headless=true -cp "/tmp/graph-label-probe/classes:$CP_BASE" \
  org.freeplane.plugin.graph.canvas.LabelGateProbe | tee /tmp/graph-label-baseline.txt
"$JAVA_HOME/bin/java" -Djava.awt.headless=true -cp "/tmp/graph-label-probe/classes:$CP_POST" \
  org.freeplane.plugin.graph.canvas.LabelGateProbe | tee /tmp/graph-label-post.txt
```

All eight fixtures reached idle within the 2000-step budget in both configurations, so every row
is measured at the first-idle endpoint plus the mandatory 200 extra steps
(the documented 2000th-step fallback was not needed).

## 2. Baseline metrics (pre-feature `9b8259bcd1`)

### Baseline (pre-feature `9b8259bcd1`)

| Fixture | Zoom | Labels | Overlapping pairs | Max leader | Mean leader | Leader / footprint |
|---|---|---|---|---|---|---|
| A | 1.0 | 7 | 0 | 3.0 | 3.0 | 0.19 |
| A | 4.0 | 7 | 0 | 0.0 | 0.0 | 0.00 |
| B | 1.0 | 12 | 8 | 648.1 | 648.0 | 6.34 |
| B | 4.0 | 12 | 3 | 4727.7 | 4703.4 | 46.05 |
| C | 1.0 | 12 | 4 | 403.8 | 272.9 | 2.66 |
| C | 4.0 | 12 | 0 | 3775.1 | 3091.0 | 29.19 |
| D | 1.0 | 12 | 9 | 403.8 | 391.4 | 3.62 |
| D | 4.0 | 12 | 3 | 3701.8 | 3701.7 | 34.19 |
| E | 1.0 | 6 | 0 | 27.0 | 17.3 | 0.95 |
| E | 4.0 | 6 | 0 | 3.0 | 3.0 | 0.19 |
| F | 1.0 | 22 | 12 | 1369.1 | 1249.3 | 12.09 |
| F | 4.0 | 22 | 4 | 6841.4 | 6709.5 | 65.22 |
| G | 1.0 | 13 | 4 | 0.0 | 0.0 | 0.00 |
| G | 4.0 | 13 | 1 | 0.0 | 0.0 | 0.00 |
| H | 1.0 | 11 | 7 | 392.7 | 392.7 | 4.91 |
| H | 4.0 | 11 | 6 | 3666.3 | 3666.3 | 45.83 |

## 3. Post-change metrics (feature branch at `139e031f30`)

### Post-change (feature integrated at `139e031f30`)

| Fixture | Zoom | Labels | Overlapping pairs | Max leader | Mean leader | Leader / footprint |
|---|---|---|---|---|---|---|
| A | 1.0 | 7 | 0 | 37.1 | 14.4 | 0.38 |
| A | 4.0 | 7 | 0 | 0.0 | 0.0 | 0.00 |
| B | 1.0 | 12 | 2 | 37.1 | 19.5 | 0.98 |
| B | 4.0 | 12 | 0 | 27.0 | 15.0 | 0.94 |
| C | 1.0 | 12 | 1 | 37.1 | 14.4 | 0.67 |
| C | 4.0 | 12 | 0 | 3.0 | 3.0 | 0.19 |
| D | 1.0 | 12 | 7 | 36.2 | 19.2 | 1.20 |
| D | 4.0 | 12 | 0 | 58.6 | 58.6 | 3.66 |
| E | 1.0 | 6 | 0 | 36.8 | 30.7 | 1.92 |
| E | 4.0 | 6 | 0 | 3.0 | 3.0 | 0.19 |
| F | 1.0 | 22 | 12 | 57.4 | 44.4 | 1.20 |
| F | 4.0 | 22 | 0 | 3.0 | 3.0 | 0.19 |
| G | 1.0 | 13 | 3 | 37.1 | 30.9 | 1.70 |
| G | 4.0 | 13 | 0 | 57.7 | 57.7 | 3.61 |
| H | 1.0 | 11 | 4 | 37.3 | 26.4 | 1.39 |
| H | 4.0 | 11 | 0 | 0.0 | 0.0 | 0.00 |

## 4. Delta (post − baseline)

### Delta (post minus baseline)

| Fixture | Zoom | Δ overlapping pairs | Δ max leader | Δ mean leader | Δ leader / footprint |
|---|---|---|---|---|---|
| A | 1.0 | +0 | +34.1 | +11.4 | +0.19 |
| A | 4.0 | +0 | +0.0 | +0.0 | +0.00 |
| B | 1.0 | -6 | -611.0 | -628.5 | -5.36 |
| B | 4.0 | -3 | -4700.7 | -4688.4 | -45.11 |
| C | 1.0 | -3 | -366.7 | -258.5 | -1.99 |
| C | 4.0 | +0 | -3772.1 | -3088.0 | -29.00 |
| D | 1.0 | -2 | -367.6 | -372.2 | -2.42 |
| D | 4.0 | -3 | -3643.2 | -3643.1 | -30.53 |
| E | 1.0 | +0 | +9.8 | +13.4 | +0.97 |
| E | 4.0 | +0 | +0.0 | +0.0 | +0.00 |
| F | 1.0 | +0 | -1311.7 | -1204.9 | -10.89 |
| F | 4.0 | -4 | -6838.4 | -6706.5 | -65.03 |
| G | 1.0 | -1 | +37.1 | +30.9 | +1.70 |
| G | 4.0 | -1 | +57.7 | +57.7 | +3.61 |
| H | 1.0 | -3 | -355.4 | -366.3 | -3.52 |
| H | 4.0 | -6 | -3666.3 | -3666.3 | -45.83 |

## 5. Reading the delta

- **Leader lengths collapse by one to two orders of magnitude.** The largest leader in the whole
  baseline table is **6841.4 px**; the largest post-change leader is **58.6 px**.
  Mean leader length falls from hundreds-to-thousands of pixels (baseline B/C/D/F/H at 4.0 zoom:
  3091.0–6709.5 px) to single-digit or tens of pixels (post-change B/C/D/E/F/G/H at 4.0 zoom:
  3.0–58.6 px). The `leaderLengthOverFootprint` ratio (leader length divided by the smaller side of
  the label's node-disc or hull footprint) falls from up to **65.22** to at most **3.66**.
- **Overlapping label pairs at reading zoom (4.0) drop to zero on every fixture**:
  baseline B 3, D 3, F 4, G 1, H 6 (and A/C/E 0); post-change all eight fixtures are 0.
- **Overlapping label pairs at fit-all (1.0)** are unchanged or better: B 8→2, C 4→1, D 9→7,
  G 4→3, H 7→4, A/E 0→0; F is unchanged at 12. Total overlapping pairs across all fixture/zoom
  rows fall from 61 to 29. Crowding does **not** materially exceed the baseline in any
  row.
- The visual contrast is direct: at fit-all the baseline spreads the three maps across the canvas
  with long leaders, and at reading zoom most of the workspace is off-screen; post-change the maps
  are packed in the viewport centre at fit-all and a single map fills the reading-zoom view with
  labels at their nodes.

**Conclusion for the gate.** No row worsens on overlapping pairs: fit-all totals fall from 44 to 29
with no row increase, and reading-zoom overlap pairs fall from 17 to 0. Four rows show localized
leader increases from near-zero baselines (A zoom 1.0 +34.1 px/+0.19; E zoom 1.0 +9.8 px/+0.97;
G zoom 1.0 +37.1 px/+1.70; G zoom 4.0 +57.7 px/+3.61), while the global maximum leader falls from
6841.4 px to 58.6 px and the maximum leader/footprint ratio falls from 65.22 to 3.66. Crowding on
leaders therefore does not exceed the recorded baseline overall, so **decision 3 (no world-space
label allowance) is not re-opened** by this evidence. If a future change makes crowding exceed this
baseline, decision 3 is re-opened with the user and any mitigation must be screen-space, never a
world-space label reservation.

## 6. Real-application screenshots (virtual X display)

Captured from the real Freeplane application (`BIN/freeplane.sh` after
`:freeplane_plugin_graph:graphSmokeInstallation`, isolated `HOME`/`XDG_CONFIG_HOME`, Xvfb `:97`
at `1600x1000x24`; the graph workspace was opened through View → Open Graph Workspace and the
workspace loaded three maps: fixture-B-style `map-a.mm` (6 marked nodes) and `map-b.mm` (2 marked
nodes) plus fixture-G-style `map-g.mm` (four sibling groups, 8 marked nodes). Both captures are
cropped to the `1280x800` Graph Workspace window.

| Configuration | State | Path |
|---|---|---|
| Baseline `9b8259bcd1` | fit-all | `docs/superpowers/specs/mockups/2026-10-13-graph-cluster-initial-proximity/label-gate/baseline/fit-all.png` |
| Baseline `9b8259bcd1` | reading zoom | `docs/superpowers/specs/mockups/2026-10-13-graph-cluster-initial-proximity/label-gate/baseline/reading-zoom.png` |
| Post-change `139e031f30` | fit-all | `docs/superpowers/specs/mockups/2026-10-13-graph-cluster-initial-proximity/label-gate/post/fit-all.png` |
| Post-change `139e031f30` | reading zoom | `docs/superpowers/specs/mockups/2026-10-13-graph-cluster-initial-proximity/label-gate/post/reading-zoom.png` |

The screenshots match the headless direction: the baseline places `Map a`, `Map b` and the four
`Map g` groups far apart with long leaders at fit-all and pushes `Map a` off-screen at reading
zoom, while the post-change application packs the maps tightly at fit-all and shows the labels at
their nodes when zoomed in.

## 7. Provenance

- Raw metric files: `label-gate/baseline/metrics.txt`, `label-gate/post/metrics.txt` (verbatim
  probe stdout).
- 200-child timing, full-suite acceptance and the spec 8.4 items 1–4 evidence are recorded by
  T009 in the T009 task evidence file.
- No production or test code was changed for this gate; the probe source is committed under
  `label-gate/probe-source/` only for reproducibility and is not added to any Gradle source set.
