# Graph Workspace Task 40 Pointer-Recovery Design

## Context

Task 40 has two terminal SDD runs. `.superpowers/sdd/batch-j-task-40` is blocked because the implementation child received a manually shortened prompt. `.superpowers/sdd/batch-j-task-40-recovery` is blocked because its read-only audit child again received manually transcribed full-role bytes. Neither report or transcript is admissible.

The implementation commit remains `b88081f4d15b36593048cdb5e6e297fc35dc9199`, parent `834d381f724c8606034a6bc5c878bb91d105cb63`, changing only `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceCommandAcceptanceShould.java`. Its SHA-256 remains `c1fb2e2fd402be89b90f48e4a87d2cc6bde87a3b1b6cc457392cdc3c7d9cd3f7`.

## Transport

V2 persists the full renderer-produced role envelope under a distinct run root. The actual dispatch prompt is a short two-line ASCII pointer naming that envelope. A candidate copy must match the stored pointer by `cmp` and SHA-256 before spawn. After completion, the raw first user message is extracted from the child JSONL and compared byte-for-byte with the pointer before any report admission.

## Recovery

A fresh read-only audit independently verifies the immutable commit, scenario coverage, focused/full tests, and a disposable confidentiality mutant. The two blocked reports are never cited. A fresh task review evaluates the exact source range, and only an admitted load-bearing finding may authorize a fix to the one test file.

Completion requires the read-only audit, task review, and Frontier final review of `834d381f724c8606034a6bc5c878bb91d105cb63..HEAD`, with exact source scope and clean Git state.
