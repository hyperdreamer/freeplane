# Graph Workspace Task 40 Blocked-Fixer Recovery Design

## Recovery Boundary

The pointer-recovery run `.superpowers/sdd/batch-j-task-40-pointer-recovery` is terminal at `TASK_BLOCKED` revision 13. Its Frontier review established three immutable Important findings (F-1, F-2, F-3), and its round-1 fixer stopped without a report or commit after a focused Gradle run failed while Mockito attempted agent attachment with `java.io.IOException: No space left on device`.

The source candidate is intentionally preserved, not approved: recovery `HEAD` before the candidate is `d9858e1ae80eafbd44bff37271ba2a42ea02bd4c`; exactly one allowlisted file is dirty, 372 insertions and 26 deletions, whitespace-clean, current SHA-256 `292fda8226b29a045a09de98097ba9ecdb79a408f88bece03f95cd95d4b30463`.

## Successor Boundary

A fresh successor SDD run starts from the preserved candidate after a documented preflight conflict approval. It carries the three findings, the prior attempted correction context, and the environmental failure. The fresh capable implementer may inspect, retain, replace, or complete only the dirty acceptance test. It must produce a new commit; no prior commit is amended and no production/build/dependency/resource/other-test file may change.

The successor must use `/data/home/guest/.tmp/freeplane-graph-batch-j-task-40` as both `TMPDIR` and Java `java.io.tmpdir` for every Gradle/JUnit invocation. This avoids the full `/tmp` filesystem without deleting unrelated temporary files. It must remove only its own logs and temporary probe residue.

## Required Evidence

The child must reproduce each F-1/F-2/F-3 gap with a focused red assertion before its correction, then run the focused acceptance class and the full graph-plugin suite serially. Native scenarios must cross a real `FreeplaneMapCommandExecutor` and production-shaped native map/lease/undo/link path; scenario 16 must use an initially null target ID and a normal save/ID-assignment step; scenario 22 must make the first validation pass valid and the second immediate pass invalid for both handler paths.

A fresh task review follows the new commit. Any unresolved Important finding opens the normal bounded fix/re-review loop. The original blocked run and child transcript/report absence remain preserved and are not approval evidence.
