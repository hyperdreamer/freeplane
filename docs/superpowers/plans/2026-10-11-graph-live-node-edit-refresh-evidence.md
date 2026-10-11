# Graph Live Node Edit Refresh Verification Evidence

- Date: 2026-10-11
- Topic slug: graph-live-node-edit-refresh
- Source revision: 4a02056afb6f9ecf1136a7abc6459d235bcf1cda
- Plan: docs/superpowers/plans/2026-10-11-graph-live-node-edit-refresh.md
- Contract: docs/superpowers/specs/2026-10-11-graph-live-node-edit-refresh-spec.md

**Full module gate**

gradle :freeplane_plugin_graph:test -PTestLoggingFull: BUILD SUCCESSFUL.

**Falsifiability probe**

With mapController.addNodeChangeListener(nodeChangeListener) replaced by a
"// FALSIFIABILITY PROBE:" comment in the MapLeaseManager constructor, the command
gradle :freeplane_plugin_graph:test --tests "org.freeplane.plugin.graph.integration.GraphWorkspaceLiveNodeRefreshShould.nodeTextEditRefreshesTheLiveProjection" -PTestLoggingFull
fails at the awaiting assertion with the message "edited label did not reach the live projection".
The registration line was restored with git checkout -- and both git status --porcelain and
git diff were empty; the same test then passed.

**Core gate**

Every commit carrying the plan identifier touches only freeplane_plugin_graph test and adapter
paths; no path under freeplane/ or freeplane_api/ was modified.
