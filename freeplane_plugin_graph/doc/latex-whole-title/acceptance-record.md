# Graph Workspace Acceptance Capture

## Build Identity

- Build revision from `git rev-parse HEAD` before the evidence commit: `ce40c3035f045bbe57390078d8d31ff435962b80`
- Distribution command: `gradle dist` with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk`; exit status 0 and final output `BUILD SUCCESSFUL`.
- Graph plugin JAR: `BIN/plugins/org.freeplane.plugin.graph/lib/plugin-1.13.4.jar`
- JAR SHA-256: `6b53d8941dc88b679ef6c32f3299d67a5e1dec23487aaea5060e9661526846e3`
- Build note: Gradle emitted Javadoc errors from `:freeplane_plugin_script:javadoc` but continued through `:dist` and returned `BUILD SUCCESSFUL`.

## Runtime Environment

- Host: `Linux 6.18.60-1-lts`, x86_64
- JDK: OpenJDK `21.0.13.0.3` (Arch Linux build `21.0.13.0.3+1`)
- Gradle: `9.8.2`, launcher JVM `21.0.13.0.3`
- Sans-serif font resolution: Noto Sans Regular (`NotoSans-Regular.ttf`); graph label font: `Font.SANS_SERIF`, plain, 12 pt
- X server: `Xvfb :98 -screen 0 1600x1000x24 -nolisten tcp`; display size `1600x1000`, root depth 24
- Captured window: exact title `Graph Workspace`; geometry `1600x1000`
- Environment passed to Freeplane:
  - `DISPLAY=:98`
  - `HOME=/tmp/fp-latex-whole-title-acceptance/home`
  - `XDG_CONFIG_HOME=/tmp/fp-latex-whole-title-acceptance/config`
  - `JAVA_HOME=/usr/lib/jvm/java-21-openjdk`
  - `PATH=/usr/lib/jvm/java-21-openjdk/bin:$PATH`
  - `JAVA_OPTS=-Dorg.freeplane.nosplash=true`
  - `FP_LATEX_ACCEPTANCE=20261105-graph-latex-whole-title-prefix`
- Launcher: this worktree's `BIN/freeplane.sh`

## Map and Visible Nodes

- Map: `/home/henry/Documents/999.notebooks/01.math/00.foundation.of.math/02.set_theory/mindmap/Axiomatic_Set_Theory.mm`
- Map SHA-256: `840358a03666d02f445e3aeb5ba84d9852e913318a3bf6c1776f50593aaff08c`
- The workspace file was `/tmp/fp-latex-whole-title-acceptance/acceptance-empty.fpg`, created as an empty workspace before adding the active map.
- The workspace contains the active map, 11 projected nodes, and 4 edges. The status bar reads `Layout: Idle`.
- Graph Workspace does not paint persisted node IDs as text. The following source-map IDs are represented by visible node labels in the capture:
  - `ID_215702338`: `\latex Theorem: $ON$ is well-ordered by $\in$`; shown as a typeset math label (`Theorem: ON is well-ordered by ∈`). Literal `$ON$ is well-ordered by $\in$` delimiters are not displayed.
  - `ID_297617399`: `\latex Theorem: Equivalence of $\leqslant$ and $\subseteq$`; its visible label begins `Theorem: Equivalenc...` and is placement-truncated in this overview.
  - `ID_1222855984`: `\latex Theorem: $\bigcup\alpha\leqslant\alpha$`; its formula label is visible in the left enclosure.
  - `ID_1887656421`: plain control `Theorem: initial segement`, visible below the formula labels.

## Evidence

- PNG: `freeplane_plugin_graph/doc/latex-whole-title/acceptance-graph-workspace.png`
- Capture method: `xwd` of the window whose `xdotool getwindowname` output was exactly `Graph Workspace`, converted from XWD to PNG with `ffmpeg`.
- PNG dimensions: `1600x1000`
- PNG SHA-256: `16c9c75bac5cace6e62bc5e0dd99089ccccb1dcd2d54afb0fc4eaf20ded25a7f`
- Command record: `freeplane_plugin_graph/doc/latex-whole-title/capture-acceptance.sh`
