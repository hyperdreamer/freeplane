# 015 plan — F11 restore position race

Source record: `ai-specs/tasks/done/015-f11-restore-position-race.md`
Base commit: `bd2be219d8`

## Global Constraints

- One production method may change: `GraphWorkspaceWindow.rawRestoreWindowed`. Nothing else in
  `freeplane_plugin_graph/src/main/java/**` may differ from `bd2be219d8`. No test, build or fixture file
  changes.
- No assertion is relaxed and no wait is widened. The test that reported the defect keeps asserting the
  exact saved bounds; the repair is in the product, not in the assertion.
- No instrumentation is delivered: every probe used for the diagnosis is reverted before commit and the
  tree verified byte-identical apart from the one method.
- Java 8 source level, 4-space indent, UTF-8. `JAVA_HOME=~/.sdkman/candidates/java/21.0.8-zulu`.
  Use `gradle`, never `gradlew` or Maven.
- The suite is run through `freeplane_plugin_graph/scripts/run-suite-on-xvfb.sh`. Every verification step
  records its exact command and output.

## Task 1: Restate the saved bounds once the restored peer is mapped

**Implementer tier:** Advanced

Files: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindow.java`

- After `setVisible(true); validate();` in `rawRestoreWindowed`, re-apply `previous.bounds()` when
  `getBounds()` does not already equal it, then `validate()`.
- Comment the reason with the measured evidence: the window manager applies the un-maximize
  asynchronously and re-places a frame that was disposed and remapped while that request was in flight,
  keeping the requested size but choosing the origin; a bare-frame measurement showed 1 loss in 60 round
  trips without the guard and 0 in 60 with it.
- Do not touch `rawEnterFullScreen`, the `Compat.isWindowsOS()` branch, or any other platform path.

Verification: compiles.

## Task 2: Confirm the fix is a no-op when the window manager behaves

**Implementer tier:** Standard

Files: none.

- Run `GraphWorkspaceWindowModelShould` (118 display tests, including every bounds, work-area coverage,
  chrome and round-trip assertion) three times through the suite script. Each run must report 118 tests,
  0 failures, 0 skipped — the same result as before the change, which is what "no behavioural change in
  the normal case" means for this method.

Verification: three green runs with the test count and skip count recorded.

## Task 3: Full-suite regression and scope proof

**Implementer tier:** Standard

Files: none.

- Run the full module suite through the suite script and record tests, failures, skips and duration.
- Prove the delivered diff is one production method: `git diff --stat` and
  `git diff --exit-code bd2be219d8` outside `GraphWorkspaceWindow.java`.
- State the honest limit explicitly: a green suite is a regression result, not proof the ≈1-in-5 flake is
  gone, and the mutation that would prove the new branch is exercised cannot be built because the suite
  cannot force the window manager to misplace a frame.

Verification: the record's §9 contains every command with its output.
