# Graph Workspace Task 40 Dispatch-Mismatch Recovery Design

## Context

The original Task 40 SDD run is terminal at `.superpowers/sdd/batch-j-task-40` in `DISPATCH_MISMATCH_BLOCKED`. The child completed source commit `b88081f4d15b36593048cdb5e6e297fc35dc9199`, but its initial prompt was not byte-identical to the persisted `renderedPrompt`, so neither its status nor report can be admitted.

The source commit remains valid verification input. It is a direct child of `834d381f724c8606034a6bc5c878bb91d105cb63`, changes only `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceCommandAcceptanceShould.java`, and that file has SHA-256 `c1fb2e2fd402be89b90f48e4a87d2cc6bde87a3b1b6cc457392cdc3c7d9cd3f7`.

## Recovery

A distinct successor SDD run will audit the immutable implementation range without using the blocked child report or transcript as evidence. The audit implementer must independently inspect every Task 40 scenario, run the focused and full graph-plugin suites, and replay the confidentiality mutant in a disposable archive.

The normal independent task reviewer then reviews the exact implementation range. The audit role may not edit source. If the reviewer opens a load-bearing finding, a normal SDD fix round may modify only the one Task 40 test file and must produce a new commit. No production file, build file, translation, fixture, or compatibility path may change.

## Admission

Every successor dispatch uses controller-rendered prompt bytes persisted before spawn. The completed child's initial user message must match those bytes before its report can be admitted. The original terminal run root remains byte-preserved diagnostic history.

Completion requires the read-only audit, independent task review, and Frontier final review to approve the branch from `834d381f724c8606034a6bc5c878bb91d105cb63` through successor `HEAD`, with zero unresolved load-bearing findings and a clean worktree.
