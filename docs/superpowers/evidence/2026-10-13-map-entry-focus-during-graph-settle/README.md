# Map-Entry Focus Mid-Settle Acceptance Evidence

- Source revision: 14ba5c65f955db99cd01778b9613b1ff8c44409c
- Build: gradle :freeplane_plugin_graph:graphSmokeInstallation --console=plain --offline
- Launch: cd BIN && DISPLAY=:99 HOME=/tmp/fp-focus-evidence/home XDG_CONFIG_HOME=/tmp/fp-focus-evidence/config JAVA_HOME=/home/henry/.sdkman/candidates/java/21.0.8-zulu JAVA_OPTS=-Dorg.freeplane.nosplash=true ./freeplane.sh
- Run environment: Xvfb 1600x1000x24 on display :99; isolated HOME and XDG_CONFIG_HOME; workspace seed /tmp/fp-focus-evidence/maps/focus.fpg with two active maps (alpha, beta) and one inactive map (gamma)
- Environment seeding: each map carries 20 graph-group-marked children (enlarged from the 5-child seed, as Step 7 permits) and the workspace carries 20 cross-map relationships between alpha and beta so the layout keeps publishing moving frames while it settles.
- Settle trigger: the inactive gamma map was reactivated from the map list immediately before the sequence (spec section 8.2 allows triggering a fresh settle by "adding a map, or changing a map's availability"); the prescribed Restart Layout click was also performed on the live settle just before C1, and the map list was fitted into the canvas with the toolbar Fit Graph action so the canvas crop shows nodes.

## Capture log (name, absolute timestamp, display, window rectangle)

C1 2026-10-13T20:59:11+08:00 display=:99 rect=WINDOW=2097591 X=0 Y=0 WIDTH=1280 HEIGHT=800 SCREEN=0 
C2 2026-10-13T20:59:12+08:00 display=:99 rect=WINDOW=2097591 X=0 Y=0 WIDTH=1280 HEIGHT=800 SCREEN=0 
C3 2026-10-13T20:59:12+08:00 display=:99 rect=WINDOW=2097591 X=0 Y=0 WIDTH=1280 HEIGHT=800 SCREEN=0 
C4 2026-10-13T20:59:13+08:00 display=:99 rect=WINDOW=2097591 X=0 Y=0 WIDTH=1280 HEIGHT=800 SCREEN=0 
C5 2026-10-13T21:00:18+08:00 display=:99 rect=WINDOW=2097591 X=0 Y=0 WIDTH=1280 HEIGHT=800 SCREEN=0 

## Observed layout-status text

C1: Settling
C2: Settling
C3: Settling
C4: Settling
C5: Idle
Note: C3 and C5 show the beta active row highlighted with Add Map and Deactivate Map enabled.
Retry Map and Locate Map are correctly disabled for an available active row; Delete Map applies
only to inactive rows.

## Canvas witness checks (nonzero pixel count proves frames still moving)

- Measurement parameters: CANVAS_CROP=760x690+273+70; ROW_X=150, ROW_Y=207; RESTART_X=1149, RESTART_Y=786
- C2 vs C1: AE=8313.74 (0.0158538)
- C3 vs C2: AE=6838.86 (0.0130413)
- C4 vs C3: AE=7398.05 (0.0141076)

## Result

The beta row click landed while the status bar still showed Settling; the canvas changed across C1-C4; after settle (C5, Idle) the same row is still highlighted with its action buttons enabled.
