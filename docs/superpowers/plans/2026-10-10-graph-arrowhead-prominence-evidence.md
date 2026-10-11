# Graph Workspace Arrowhead Prominence Evidence

## Screenshot

- Deliverable: `docs/superpowers/specs/images/2026-10-10-graph-arrowhead-prominence-graph-workspace.png`.
- Shows: the running application's Graph Workspace with the seeded two-node map
  `arrowhead evidence` and one directed native connector (`STARTARROW="NONE"`,
  `ENDARROW="DEFAULT"`); the target-end arrowhead is the filled triangle produced by
  `L = min(max(12.5, 8.68 x zoom), a x zoom)` at the workspace zoom of 1.0, which is the
  12.5 px floor and visibly longer than the removed 8 px head. The rendered triangle in
  this capture spans about 12 px from base (x = 713) to tip (x = 724) with a base height
  of 9-10 px, consistent with the 12.5 px floor; the previous `8.0 / zoom` literal would
  have drawn an 8 px head.
- Source revision: `cb1d4cd67e77bfdb977308fd1b7740fe2f080270` in the lane worktree, built with
  `JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane_plugin_graph:graphSmokeInstallation --console=plain --offline`.
- Run environment: Xvfb `1600x1000x24` on display `:98`, isolated `HOME` and
  `XDG_CONFIG_HOME`, seeded `graph_workspace_recent_workspaces=/tmp/fp-arrowhead-evidence/maps/arrowheads.fpg`.
- Capture: `import -window "$(xdotool search --name 'Graph Workspace' | head -1)"` from the lane
  worktree after the Graph Workspace window settled.

## Seed fixture

The transient map `/tmp/fp-arrowhead-evidence/maps/arrowheads.mm` and workspace
`/tmp/fp-arrowhead-evidence/maps/arrowheads.fpg` were seeded with the details below, which the
actual workspace pipeline and layout require; the workspace was opened through the first
`View > Open Graph Workspace` entry after starting the launcher from the lane worktree.

- The config was seeded under `XDG_CONFIG_HOME/freeplane/1.12.x` (also mirrored to `1.13.x`).
  The launcher appends `Compat.CURRENT_VERSION_DIR` (`/1.12.x`) to `XDG_CONFIG_HOME/freeplane`,
  so the `1.12.x` directory is the one the running application reads.
- The map declares `version="freeplane 1.12.15"`. The built application reports
  `freeplane_version = 1.13.4; freeplane_xml_version = freeplane 1.12.15`; declaring the map
  as `freeplane 1.13.4` selects the `FREEPLANE_NEWER` dialect and raises the
  "created with some later version" warning dialog before the workspace opens.
- Both `source` and `target` carry `<graph_group version="1"/>`. The projection projects only
  graph-group-marked nodes as vertices; plain leaves are not drawn.
- `<pin>` records place the vertices at `(-80, 0)` and `(80, 0)`. The unpinned layout settles
  the two vertices close together (about 6 px of attachment space at zoom 1.0), which caps
  `effectiveArrowHeadLengthPx` below the 12.5 px floor. The pins keep the attachment distance
  far above the floor so the rendered head demonstrates `max(12.5, 8.68 x 1.0)`.

The seeded workspace file and map under `/tmp/fp-arrowhead-evidence` are transient and are not
committed.
