# Graph Workspace Task 39 Dispatch-Mismatch Recovery Design

## Context

The original Task 39 SDD run at `.superpowers/sdd/batch-j-task-39` is terminal in `DISPATCH_MISMATCH_BLOCKED`. The child received 5,384 prompt bytes instead of the persisted 5,791-byte renderer output, so its `DONE` report cannot be admitted.

The child nevertheless produced source commit `0ec5e71b9d585ca6e5ecacb836ab4cdcde562c43`, a direct child of `834d381f724c8606034a6bc5c878bb91d105cb63`. It changes only `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceModelAcceptanceShould.java`, whose SHA-256 is `da75e13932703d7e302c5ce10379096ddbade991165c6c8a4b0baddd5a79c287`.

## Recovery

A distinct successor run audits that immutable commit without source changes and without citing the blocked report or transcript. The audit independently checks every named model acceptance scenario, focused and full graph-plugin test results, safe projection and identity boundaries, and the recorded strict performance prerequisite.

A fresh task reviewer then reviews the exact implementation range. Only an admitted finding may open a normal fix round, and any fix is confined to the one Task 39 test file. No production, build, translation, resource, shared-fixture, or compatibility change is authorized.

## Dispatch Transport

The tracked child API accepts only literal prompt text. The successor therefore persists the complete renderer-produced role envelope under the run root, then persists and dispatches a short ASCII pointer naming that envelope. Before spawn, a candidate pointer file must compare byte-for-byte with the persisted pointer. After completion, the raw first user message extracted from the child session must compare byte-for-byte with the pointer before its report can be admitted.

## Completion

Completion requires a fresh read-only audit, independent task review, and Frontier final review of `834d381f724c8606034a6bc5c878bb91d105cb63..HEAD`, with no unresolved load-bearing findings, exact source scope, clean Git state, and the original terminal run preserved.
