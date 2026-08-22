# Graph Workspace Task 41 Full-Suite Recovery Design

## Finding

The Task 41 cold-reload acceptance passes in isolation and in the focused pair, but fails reproducibly in the full graph-plugin suite during `FreeplaneScope.loadWithView`. The failure is a headless `UserInputListenerFactory.ActionEnabler` null `Entry` caused by shared controller/menu state: `FreeplaneScope` reuses an existing global controller when possible, yet installs the empty reflective menu root only when it creates a new starter.

The implementation commit and two acceptance source files are otherwise unchanged and clean. Current HEAD is `577074cf9f68e8fd59e61ff0a3e0e8452e51552c`; source hashes are `GraphWorkspaceColdReloadShould.java` `ebf21666566ed9fbc660582b4e1fdbce3ba7b7f2f78c39775934331dc8d02320` and `GraphWorkspaceLifecycleShould.java` `e2a1f5ddfa5ab3e2cc1517567b3322e833c6a00646b5e88f168ac0a33df420d5`.

## Recovery

Use the existing Task 41 worktree and branch. A capable implementer makes the smallest acceptance-fixture-only correction that gives every `FreeplaneScope` a deterministic valid menu root without modifying production code or unrelated tests. It must preserve and restore shared controller state where necessary, retain production `MapLoader.withView()` behavior, and prove the historical full-suite failure is red under a disposable archived mutant that removes the correction. Run focused and full graph-plugin tests serially after Task 40 verification is complete. A Frontier task review and mandatory Frontier final review inspect the final range.

The one non-reproducible MapLeaseManager timing concern and fixed-sleep observation remain disclosed; they are not silently discarded. Any repeatable full-suite failure remains blocking.
