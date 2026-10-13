# Prototype evidence — cluster packing initialization (design v7, scope A)

Reproduces the measurements in
`docs/superpowers/specs/2026-10-13-graph-cluster-initial-proximity-design.md` sections 2, 7 and 11.
`superseded-v2/` keeps the review-2 lattice prototype, the review-3 v4 overlay, and the interim
v5 (no centre lines) and v6 (packing skeleton) overlays for history.

## Contents

- `overlay/` — option-A overlay sources for `GraphStreamLayoutEngine.java`, `TypedNodeParticle.java`,
  `TypedSpringBox.java`: anchor-centred per-axis footprints, production centre-line packing with contact
  metadata, hierarchy links with packed rest lengths, offset-aware box separation. No skeleton, no
  displacement caps. Prototype switches: `-Dproto.containment`, `-Dproto.margin`, `-Dproto.boundary`,
  and the hierarchy multiplier via `-Dproto.packingMultiplier`.
- `ClusterProbe.java` — baseline probe (fixtures A/B against production classes; uses `sizeOf`).
- `ContactProbe.java` — design probe: fixtures A–H, per-side containment, contact types, convex-hull
  distances, all-pairs crossings, 1000-step post-endpoint stability window, and the 200-child timing.
  Requires the overlay.
- `probe-baseline.txt` (ClusterProbe, production classes), `probe-final.txt` (ContactProbe, option A),
  `probe-cap-sweep.txt`, `probe-containment-sweep.txt` (historical).

## Environment and classpath

```
JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu
REPO=/data/home/guest/Development/freeplane
GC=$(find /home/henry/.gradle/caches/modules-2/files-2.1/org.graphstream -name "*.jar" | tr '\n' ':')
CP="$REPO/freeplane_plugin_graph/build/classes/java/main:$REPO/freeplane_plugin_graph/build/classes/java/test:$REPO/freeplane/build/classes/java/main:$GC"
```

Build the module once (`gradle :freeplane_plugin_graph:compileTestJava`); the probes use the test fixture
`ReferenceRepulsionFixture` from the module test classes.

## Apply the overlay (throwaway, outside the worktree)

```
D=/tmp/cluster-optionA-proto
mkdir -p $D/src/org/freeplane/plugin/graph/layout/graphstream $D/classes $D/probe-classes
S=$REPO/freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/graphstream
cp $S/GraphStreamLayoutEngine.java $S/TypedNodeParticle.java $S/TypedSpringBox.java \
   $D/src/org/freeplane/plugin/graph/layout/graphstream/
cp overlay/*.java $D/src/org/freeplane/plugin/graph/layout/graphstream/     # overwrite with the overlay
$JAVA_HOME/bin/javac -source 8 -target 8 -nowarn -cp "$CP" -d $D/classes \
   $D/src/org/freeplane/plugin/graph/layout/graphstream/*.java
$JAVA_HOME/bin/javac -source 8 -target 8 -nowarn -cp "$D/classes:$CP" -d $D/probe-classes ContactProbe.java
```

## Run

```
$JAVA_HOME/bin/java -Djava.awt.headless=true -Dproto.boundary=box -Dproto.packingMultiplier=0.3 \
   -Dproto.containment=1.0 -Dproto.margin=8 -cp "$D/classes:$D/probe-classes:$CP" \
   org.freeplane.plugin.graph.layout.ContactProbe

# baseline A/B (production classes; compile ClusterProbe against $CP first)
$JAVA_HOME/bin/java -Djava.awt.headless=true -cp "baseline-probe-classes:$CP" \
   org.freeplane.plugin.graph.layout.ClusterProbe
```

## Sanity checks (option A)

- Initial frame is the exact deterministic packing (gutter 8, no overlap) — asserted by the plan's
  initial-frame test.
- Idle 22–785 steps on A–H; all-pairs non-ancestor crossings 0 at the documented endpoint.
- Settled pairs drift (documented scope): window maxima A 4.6, B 14.6, C 12.1, D 78.7, E 2.1, F 27.2,
  G 9.9, H 2.1; painted gaps 0.0–37.5; containment overflow 0–14.0.
- 200-child fixture: submit (apply + packing) 0.52 s, 10 steps 0.84 s.
- Baseline: A `Axioms`↔`Definitions` 682.8, B `Map a`↔`Map b` 2704.4.
